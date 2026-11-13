# Exception Handler Refactor Plan

> Nguồn: đánh giá xử lý lỗi hệ thống (2026-08-22) — đọc `common/exception/*`,
> `common/response/*`, `common/security/{JwtAuthEntryPoint,JwtAccessDeniedHandler,
> JwtAuthenticationFilter,RateLimitFilter}.java`, khảo sát toàn bộ ~380 throw site +
> 48 catch block trong `src/main`. Đây là **kế hoạch**, chưa thực hiện — không có dòng
> `src/main` nào bị đổi bởi tài liệu này.
>
> **Không phải một phase nghiệp vụ `P*`/`F*`/`D*`/`C2-*`** theo quy ước `CLAUDE.md` — đây là
> nợ kỹ thuật xuyên-module về hạ tầng lỗi. Khi bắt đầu thực hiện, tạo mục riêng trong
> `NEXT_PHASE_PLAN.md` trỏ về đúng hạng mục (`EH-n`) đang làm, theo `dev-workflow.md §6.6`.

---

## ✅ TRẠNG THÁI THỰC HIỆN *(cập nhật 2026-08-23)*

| ID | Trạng thái | Ghi chú |
|---|---|---|
| EH-1 | ✅ **Xong** | 3 filter tự bắt `RuntimeException` + `ApiErrorController` + `/error` permitAll |
| EH-2 | ✅ **Xong** | Tách **3** mã (A+B+C) trên **59/96** site — user chốt qua `AskUserQuestion`, làm một lượt |
| EH-3 | ✅ **Xong** | `DataIntegrityErrorMapper`; fallback đổi 409 → **422** |
| EH-4 | ✅ **Xong** | `MrpRunStateRecorder` (`REQUIRES_NEW`) + sanitize message |
| EH-5 | ✅ **Xong** | Xoá **4** mã, **giữ 5** kèm javadoc nêu lý do (xem §4 EH-5) |
| EH-6 | ✅ **Xong** | Viết lại `error-handling.md` §5.2 + §5.3, thêm §5.4.1/§5.4.2 |
| EH-7 | ⏸️ **Hoãn có chủ đích** | Đúng theo khuyến nghị của chính plan này (P4). Xem §4 EH-7 |
| EH-8 | ⚠️ **Khảo sát lại — tiền đề của plan SAI, chưa thực hiện** | Xem §4 EH-8 |

**Nghiệm thu:** `mvn -o clean verify` — baseline **1155 case unit / 147 class + 140 case IT / 20 class IT**
→ sau toàn bộ EH: **1181 case unit / 152 class + 142 case IT / 21 class IT**. `failures = 0, errors = 0` ở cả hai mốc.
Nghiệm thu mutation: 5 mutation, 5/5 đụng `src/main`, xem §9.5.


## 0. Kết luận đánh giá (tóm tắt, để tham chiếu khi thực hiện)

| Điểm mạnh đã xác nhận | Điểm yếu đã xác nhận |
|---|---|
| Một envelope `ApiResponse` cho mọi response, kể cả tầng Security | `OPERATION_NOT_ALLOWED` là sọt rác — 96/380 throw site dùng chung 1 mã |
| 14 handler phủ họ lỗi client phổ biến, mỗi lần thêm đều do bug thật | Lỗi thoát khỏi filter chain (Redis down, RuntimeException lạ) không có envelope |
| 0 `printStackTrace`, catch-all không lộ nội bộ | `DataIntegrityViolationException` map bằng `contains()` hardcode |
| Module mới tái dùng mã cũ — 4 module gần nhất (`costing`/`uom`/`workcenter`/`shift`) thêm 0 mã mới | `MrpRunService.fail` lưu `e.getMessage()` thô ra response + rủi ro rollback-only |
| Interface `ErrorCode` cho phép domain lỗi khác biệt tách riêng (`ImportErrorCode`) mà không phá kiến trúc chung | 9 mã lỗi khai báo nhưng chưa bao giờ ném (code speculative) |
| | Tên `ExceptionFactory.businessRule()` ngụ ý 422 nhưng dùng 15 lần cho mã 409 |
| | `ApiResponse.multiErrors(code, List<String>)` làm mất cấu trúc field-level |
| | `error-handling.md §5.2` vẽ cây exception 12 lớp không tồn tại trong code |

## 1. Mục tiêu

1. Cho FE khả năng rẽ nhánh theo `code` ở những chỗ hiện phải parse `message` tiếng Anh.
2. Đảm bảo **mọi** lỗi thoát ra HTTP — kể cả lỗi trong filter chain, trước
   `DispatcherServlet` — đều đi qua envelope `ApiResponse`, không rơi về `/error` mặc định
   của Spring Boot.
3. Xoá các điểm hardcode dễ vỡ khi thêm constraint/migration mới (`contains()` trong
   `handleDataIntegrity`).
4. Đóng khoảng lệch giữa `error-handling.md` và code thật (tài liệu đang mô tả một hệ thống
   không tồn tại).
5. Không đổi hành vi ở bất kỳ chỗ nào **không** nằm trong phạm vi các hạng mục dưới đây —
   đúng tinh thần "Surgical Changes" của `CLAUDE.md`.

## 2. Nguyên tắc bắt buộc khi thực hiện (kế thừa từ `.claude/rules/*`)

- **`coding-rules.md §11.4`**: tách `OPERATION_NOT_ALLOWED` đổi `code` trên wire cho các
  endpoint đang chạy ⇒ là breaking change. Phải: (a) baseline `mvn -o clean verify` trước khi
  bắt đầu, (b) ghi bảng "Breaking Changes" theo đúng format các phase cũ trong `CLAUDE.md`
  (VD `§0.15`), (c) **sửa** test cũ theo `R10`, không xoá, (d) số case cuối ≥ baseline.
- **`best-practices.md R10`**: không xoá test để build xanh.
- **`coding-rules.md §11.5`**: không thêm mã lỗi/abstraction cho tình huống chưa ai ném tới
  (áp dụng khi cân nhắc có tách quá nhiều mã nhỏ từ `OPERATION_NOT_ALLOWED` hay không).
- **`dev-workflow.md §6.5`**: sau mỗi hạng mục, cập nhật `error-handling.md` (mã lỗi, handler
  chain) + `CLAUDE.md` (nếu ảnh hưởng bất biến/phase state) trong cùng lượt, không hoãn.
- **Breaking change trên wire cần phối hợp FE** — theo tiền lệ `§0.42`/`§0.44`: đổi wire chỉ
  triển khai sau khi xác nhận FE đọc được / đã chuẩn bị, giống cách các phase trước dùng
  `AskUserQuestion` để chốt phạm vi trước khi code.

## 3. Danh sách hạng mục

| ID | Hạng mục | Ưu tiên | Breaking change (wire) | Đụng `src/main` |
|---|---|---|---|---|
| EH-1 | Safety net cho lỗi thoát khỏi filter chain | P1 | Không (chỉ hình dạng lỗi hiếm gặp trước đây vốn đã sai) | Có |
| EH-2 | Khảo sát + tách `OPERATION_NOT_ALLOWED` thành các mã cụ thể | P1 | **Có** | Có |
| EH-3 | Bảng ánh xạ `DataIntegrityViolationException` → `ErrorCode` | P2 | Nhẹ (chỉ các constraint chưa từng khớp `contains()` cũ) | Có |
| EH-4 | Sửa `MrpRunService.fail` — sanitize message + ghi nhận an toàn | P2 | Không | Có |
| EH-5 | Dọn 9 mã lỗi chưa từng ném (`ITEM_ALREADY_ISSUED`, `PASSWORD_TOO_WEAK`, …) | P3 | Không | Có (xoá) |
| EH-6 | `error-handling.md §5.2` — vẽ lại cây exception theo code thật | P3 | Không (chỉ tài liệu) | Không |
| EH-7 | *(tuỳ chọn, có thể hoãn vô thời hạn)* Đổi tên `ExceptionFactory.businessRule` | P4 | Không | Có (161 call site, cơ học) |
| EH-8 | *(tuỳ chọn)* `ApiResponse.multiErrors(code, List<String>)` giữ cấu trúc thay vì join chuỗi | P4 | Có, nhưng 0 call site hiện tại trong `src/main` | Có |

## 4. Chi tiết từng hạng mục

### EH-1 — Safety net cho lỗi thoát khỏi filter chain (P1)

**Vấn đề.** `JwtAuthenticationFilter:97` chỉ bắt `AppException`. Redis lỗi trong
`assertNotBlacklisted`, hay bất kỳ `RuntimeException` không lường trước nào trong
`RateLimitFilter`/`UserRateLimitFilter`, bay thẳng ra ngoài chain — không tới
`GlobalExceptionHandler` — rơi vào trang `/error` mặc định của Spring Boot
(`{timestamp,status,error,path}`), một hình dạng JSON FE chưa từng thấy.

**Việc cần làm:**
1. `JwtAuthenticationFilter.doFilterInternal`: thêm `catch (Exception ex)` **sau** catch
   `AppException` hiện có, ghi log `ERROR` kèm stack trace đầy đủ, trả
   `ApiResponse.error(BusinessErrorCode.INTERNAL_SERVER_ERROR)` qua cùng cơ chế
   `writeAuthError`/`objectMapper.writeValue` đã có trong file. Không được để lộ
   `ex.getMessage()` ra response (khác nhánh `AppException`, exception ở đây chưa được kiểm
   soát).
2. Rà tương tự cho `RateLimitFilter` và `UserRateLimitFilter` — cả hai đều thao tác Redis,
   cùng loại rủi ro.
3. Thêm một `ErrorController` fallback (`@Controller` implements `ErrorController`,
   map `/error`) trả đúng envelope, làm lưới an toàn **cuối cùng** cho bất kỳ đường thoát
   nào khác chưa lường tới (kể cả ngoài filter chain, ví dụ lỗi từ container trước khi vào
   Spring MVC).
4. Test: `@WebMvcTest` giả lập filter ném `RuntimeException` không phải `AppException`,
   assert response vẫn đúng envelope + status 500, `code = INTERNAL_SERVER_ERROR`, và
   **không** chứa message thô của exception gốc.

**Không đụng gì khác** — không đổi hành vi của `AppException` path đang chạy đúng.

---

### EH-2 — Tách `OPERATION_NOT_ALLOWED` (P1, việc lớn nhất)

**Vấn đề.** 96 throw site dùng chung một mã 422, gộp nhiều lý do khác hẳn nhau: master data
`INACTIVE`, sai `ItemType`, tài nguyên khác company/plant, chứng từ chưa có dòng nào,
`dueDate < orderDate`, dữ liệu đầu vào sai... FE không rẽ nhánh được, phải parse `message`.

**Bước 1 — Khảo sát (bắt buộc trước khi đổi bất kỳ code nào).**
Liệt kê đầy đủ 96 throw site (`grep -rn "OPERATION_NOT_ALLOWED" src/main`), phân loại theo
module + lý do nghiệp vụ. Sản phẩm của bước này là một bảng trong chính file plan này (mục
"8 — Kết quả khảo sát", cập nhật khi khảo sát xong), **không** đoán trước nội dung bảng đó.

Bốn nhóm ứng viên nêu ra lúc đánh giá (cần khảo sát xác nhận lại, không phải quyết định
cuối):
- Master data không hoạt động (`INACTIVE`) khi thao tác.
- Tham chiếu xuyên company/plant/warehouse không hợp lệ.
- Chứng từ/tài nguyên cha chưa có dòng con nào (BOM/Routing chưa có line).
- Input sai theo nghĩa hẹp (loại `SupplySuggestionType` sai, ngày tháng vô lý,
  nhiều supplier trong 1 PR...).

**Bước 2 — Quyết định ngưỡng tách.** Không tách 1-1 (96 mã là phản tác dụng, vi phạm
`coding-rules.md §11.5` theo hướng ngược — quá nhiều mã không ai phân biệt nổi). Ngưỡng đề
xuất: chỉ tách nhóm có ≥ 8-10 throw site VÀ có giá trị rẽ nhánh thật cho FE (ví dụ: FE cần xử
lý khác nhau giữa "cha INACTIVE" và "chứng từ rỗng"). Nhóm còn lại **giữ nguyên**
`OPERATION_NOT_ALLOWED` làm mã catch-all cho input sai chung chung — đây vẫn là hành vi hợp
lệ theo bảng 3 tiêu chí đã có ở `error-handling.md §5.3`, không phải lỗi.

**Bước 3 — Quyết định cần chốt với user trước khi code** (dùng `AskUserQuestion` khi tới lúc
thực hiện, không chốt sẵn trong plan này):
- Danh sách mã mới cuối cùng (dựa trên khảo sát thật, không phải 4 nhóm ứng viên).
- Có làm cùng lúc cho toàn bộ 96 site, hay chia theo module (mỗi module một lượt, dễ review
  + dễ rollback hơn)?
- Phối hợp FE theo mức nào — cần release đồng thời, hay adapter FE đọc được cả `code` cũ lẫn
  mới trong một cửa sổ chuyển tiếp (đúng tiền lệ `§0.42`)?

**Bước 4 — Thực hiện** (chỉ sau khi bước 2-3 chốt):
1. Thêm constant mới vào `BusinessErrorCode` (hoặc `ValidationErrorCode` tuỳ bản chất) theo
   đúng HTTP status hiện tại của `OPERATION_NOT_ALLOWED` (422) trừ khi khảo sát cho thấy một
   nhóm thật ra nên là status khác (nếu vậy đó là tín hiệu nó thuộc `STATE_CONFLICT`/khác,
   không phải nợ này).
2. Đổi throw site theo từng nhóm đã chốt, **giữ nguyên message hiện có** (không đổi hành vi
   ngoài mã).
3. Test: `R10` — sửa assertion `code` ở test cũ đang pin `OPERATION_NOT_ALLOWED` cho những
   throw site vừa đổi mã, thêm test mới nếu nhóm đó trước đây chưa có test khoá `code`
   (nhiều throw site trong 96 cái này hiện chỉ được test phủ qua `isInstanceOf`, không phải
   `ErrorCode` — theo rule `R1`, phải nâng cấp).
4. Cập nhật `error-handling.md §5.3` (bảng mã lỗi) + ghi "Breaking Changes" đầy đủ theo
   format các phase cũ.

**Rủi ro chính:** đây là hạng mục duy nhất thực sự phá client đang đọc `code ==
"OPERATION_NOT_ALLOWED"` để rẽ nhánh (nếu FE có làm vậy) — cần xác nhận với FE trước khi
merge, không suy đoán.

---

### EH-3 — Bảng ánh xạ `DataIntegrityViolationException` (P2)

**Vấn đề.** `GlobalExceptionHandler.handleDataIntegrity:230` chỉ có 1 case đặc biệt
(`uk_inventory_lots_item_code`/`chk_inventory_lots_code_trimmed` → `LOT_CODE_ALREADY_EXISTS`),
mọi constraint khác (FK, NOT NULL, CHECK khác) đều rơi vào `RESOURCE_ALREADY_EXISTS` — sai
ngữ nghĩa cho phần lớn trường hợp không phải "đã tồn tại".

**Việc cần làm:**
1. Thêm một `Map<String constraintName, ErrorCode>` (hằng số, khởi tạo tĩnh) trong
   `GlobalExceptionHandler` hoặc một class hỗ trợ riêng
   (`DataIntegrityErrorMapper`), thay cho chuỗi `if (detail.contains(...))`.
2. Constraint không khớp map ⇒ fallback **`BUSINESS_RULE_VIOLATION`** (422) thay vì
   `RESOURCE_ALREADY_EXISTS` (409) — đúng hơn về ngữ nghĩa cho FK/CHECK violation không phải
   trùng khoá. Giữ `RESOURCE_ALREADY_EXISTS` chỉ cho constraint tên chứa `uk_`/`uq_` (unique).
3. Rà migration hiện có (`grep -rn "ADD CONSTRAINT" src/main/resources/db/migration`) để
   liệt kê các unique constraint quan trọng khác đáng có message riêng (theo đúng khuôn
   `LOT_CODE_ALREADY_EXISTS`) — không bắt buộc thêm hết, chỉ những chỗ tái phạm/khách hàng đã
   từng vấp (đối chiếu `CLAUDE.md §0.40` — `uk_sales_order_lines_order_line_no` là ví dụ cụ
   thể từng gây nhầm lẫn).
4. Test: mỗi entry trong map cần 1 case `GlobalExceptionHandlerTest` mô phỏng đúng detail
   string, cộng 1 case fallback (constraint lạ) để khoá hành vi mặc định mới.

**Breaking change nhẹ:** chỉ ảnh hưởng những chỗ trước đây vô tình nhận `RESOURCE_ALREADY_EXISTS`
sai — đây là sửa sai, giống tiền lệ các bugfix trong `CLAUDE.md`.

---

### EH-4 — Sửa `MrpRunService.fail` (P2)

**Vấn đề (`module/planning/service/MrpRunService.java:148`):**
```java
} catch (RuntimeException e) {
    persistedRun.fail(Instant.now(), e.getMessage());   // → MrpRunResponse.errorMessage, ra thẳng client
```
Hai lỗi: (a) message nội bộ (có thể là exception message của Hibernate/SQL) đi thẳng ra
response — vi phạm nguyên tắc "không lộ chi tiết nội bộ" duy nhất bị vòng qua trong repo;
(b) nếu `RuntimeException` gốc là lỗi từ tầng DB, transaction hiện tại có thể đã
rollback-only, khiến `mrpRunRepository.save(persistedRun)` ở dòng `:152` nổ lúc commit —
client nhận 500 và **không có** row `FAILED` nào được ghi (cùng loại lỗi persist-trong-
transaction-sắp-rollback đã ghi ở `§0.23`, bài học `WorkOrderBlockRecorder`).

**Việc cần làm:**
1. Sanitize message trước khi lưu: chỉ giữ message nếu nó đến từ `AppException` (đã là lỗi
   nghiệp vụ có chủ đích); với `RuntimeException` khác, lưu một message cố định
   ("MRP calculation failed — see server logs") + log đầy đủ `ex` ở mức `ERROR`.
2. Ghi nhận trạng thái `FAILED` qua một bean `@Transactional(propagation = REQUIRES_NEW)`
   riêng — đúng pattern `WorkOrderBlockRecorder` đã có sẵn trong `module/workorder` — để
   `fail()` commit được độc lập với transaction chính đang lỗi, thay vì gọi `save()` chay
   trong cùng transaction rồi hy vọng nó chưa rollback-only.
3. Test: 1 case mô phỏng `calculate()` ném `RuntimeException` không phải `AppException`,
   assert `errorMessage` trên response là message cố định (không phải message gốc), assert
   row `FAILED` **thực sự persist** (đọc lại từ repository, không chỉ mock trả về) — cần
   `*IT` vì đây đúng loại lỗi mock repository không bắt được (`R7`).

**Không breaking change trên wire** — hình dạng response không đổi, chỉ nội dung
`errorMessage` (vốn không ai dựa vào nội dung cụ thể để rẽ nhánh, chỉ để hiển thị).

---

### EH-5 — Dọn mã lỗi chưa từng ném (P3)

**Danh sách xác nhận qua grep (0 throw site):**
`BusinessErrorCode.ITEM_ALREADY_ISSUED`, `LOT_WAREHOUSE_CONFLICT`, `RECEIPT_STATE_CONFLICT`,
`MRP_CALCULATION_ERROR`, `PRODUCTION_ORDER_CLOSED`, `BOM_REQUIREMENT_EXCEEDED`,
`EXTERNAL_SERVICE_ERROR`; `ValidationErrorCode.PASSWORD_TOO_WEAK`;
`AuthErrorCode.SESSION_CONFLICT`.

**Việc cần làm:**
1. Xác nhận lại bằng grep tại thời điểm thực hiện (danh sách có thể đã đổi nếu EH-4 dùng
   `MRP_CALCULATION_ERROR` — nếu vậy, gạch nó khỏi danh sách xoá, nó chuyển thành "đã dùng").
2. Với mỗi mã: hoặc (a) xoá khỏi enum nếu chắc chắn không nhánh nào sắp cần, hoặc (b) giữ
   lại kèm comment nêu rõ nhánh dự kiến sẽ ném nó (nếu đã có kế hoạch cụ thể, không phải
   speculative) — mặc định nghiêng về (a) theo `coding-rules.md §11.5`.
3. Đặc biệt: `SESSION_CONFLICT` có `SessionConflictException` khai trong
   `error-handling.md §5.2` nhưng không có class Java tương ứng — xử lý cùng lúc với EH-6.
4. Kiểm tra không có tài liệu FE nào (`docs/api-guide-for-frontend.md`,
   `FE_SingleTask_Response.md` các bản cũ) đang hứa các mã này — nếu có, giữ lại và ghi rõ lý
   do thay vì xoá.

**Không breaking change** — xoá constant chưa từng xuất hiện trên wire không ảnh hưởng client
thật.

> ✅ **Kết quả 2026-08-23.** Grep lại tại thời điểm thực hiện: **cả 9 mã vẫn 0 throw site**, và `EH-4`
> **không** dùng `MRP_CALCULATION_ERROR` (nó ghi nhận `FAILED` chứ không ném), nên mã đó vẫn nằm trong
> diện xoá.
>
> | Quyết định | Mã |
> |---|---|
> | **Xoá** (4) | `BusinessErrorCode.MRP_CALCULATION_ERROR`, `BusinessErrorCode.EXTERNAL_SERVICE_ERROR`, `ValidationErrorCode.PASSWORD_TOO_WEAK`, `AuthErrorCode.SESSION_CONFLICT` |
> | **Giữ + javadoc nêu lý do** (5) | `ITEM_ALREADY_ISSUED`, `PRODUCTION_ORDER_CLOSED` — đã hứa với FE ở `docs/api-guide-for-frontend.md` (bước 4 của chính mục này) · `LOT_WAREHOUSE_CONFLICT`, `RECEIPT_STATE_CONFLICT`, `BOM_REQUIREMENT_EXCEEDED` — nằm trong hợp đồng lỗi phase BE-2/BE-4 của `BE_SYSTEM_ISSUES_RESOLUTION_PLAN_2026-08-19.md`, tức có kế hoạch cụ thể (nhánh (b)) |
>
> Dòng trong `docs/api-guide-for-frontend.md` liệt kê `ITEM_ALREADY_ISSUED` / `PRODUCTION_ORDER_CLOSED`
> **với ô "Khi nào" bỏ trống** đã được điền: nay ghi rõ *"đã đặt chỗ, backend chưa từng trả"* — giữ mã
> mà không nói rõ nó chưa bao giờ xảy ra thì FE vẫn có thể viết một nhánh xử lý chết.

---

### EH-6 — Đồng bộ `error-handling.md §5.2` với code thật (P3)

**Vấn đề.** Tài liệu vẽ cây 12 lớp exception (`ResourceNotFoundException`,
`BusinessRuleException`, `TokenExpiredException`, `SessionConflictException`...) — code thực
tế chỉ có `AppException` (phẳng, mang `ErrorCode` + `HttpStatus`) và `MultiErrorException`.
Thiết kế thật **đơn giản hơn và tốt hơn** cái được vẽ, nhưng bất kỳ ai đọc tài liệu trước code
sẽ đi tìm nhầm class.

**Việc cần làm:** viết lại §5.2 mô tả đúng 2 lớp thật (`AppException`, `MultiErrorException`)
+ cơ chế `ErrorCode` interface + 3 enum triển khai, thay cho sơ đồ phân cấp giả. Thuần tài
liệu, không đụng code, làm được độc lập bất cứ lúc nào (không phụ thuộc EH-1..EH-5).

---

### EH-7 — *(P4, tuỳ chọn, có thể hoãn)* Đổi tên `ExceptionFactory.businessRule`

`businessRule()` được gọi 161 lần, trong đó **15 lần** dùng mã HTTP 409 (`STATE_CONFLICT`,
`INSUFFICIENT_STOCK`, `CONCURRENT_MODIFICATION`...) dù tên ngụ ý 422. Hành vi đúng (status lấy
từ `ErrorCode`, không từ tên method) — đây thuần là nhiễu đặt tên.

**Đánh giá chi phí/lợi ích:** đổi tên đụng 161 call site cơ học (mechanical rename, không đổi
logic) — rủi ro thấp nhưng diff lớn không tương xứng giá trị (chỉ là đọc code dễ hơn chút).
**Đề xuất: hoãn vô thời hạn**, chỉ làm nếu nhân tiện có PR lớn khác đã chạm nhiều throw site
tương tự (ví dụ làm chung với EH-2). Nếu làm: đổi tên thành `of(ErrorCode, String)`, giữ
`businessRule` như alias `@Deprecated` một thời gian rồi xoá, hoặc sed 1 lượt + review diff
theo module.

> ⏸️ **Kết luận 2026-08-23: HOÃN, đúng theo khuyến nghị ở trên.** EH-2 có chạm 59 throw site nên điều
> kiện "nhân tiện" đã xuất hiện, nhưng chúng chỉ đổi **đối số** (`BusinessErrorCode.X`), không đổi
> **tên method** — gộp thêm 161 call site rename vào cùng một lượt sẽ chôn một breaking change wire
> thật (EH-2) dưới một diff cơ học lớn gấp ba, khiến chính phần cần review kỹ nhất trở nên khó thấy.
> Nhiễu đặt tên vẫn còn; nay đã được ghi thẳng vào `error-handling.md §5.2` hệ quả #2 ("tên method
> không quyết định HTTP status"), nên người đọc code không bị dẫn sai nữa dù chưa đổi tên.

---

### EH-8 — *(P4, tuỳ chọn)* `ApiResponse.multiErrors(code, List<String>)`

**Xác nhận qua grep: 0 call site thật trong `src/main`** — chỉ xuất hiện trong javadoc ví dụ
của `ExceptionFactory`/`MultiErrorException`. Đây là code chưa từng được dùng
(`coding-rules.md §11.5`).

**Đề xuất:** không "sửa cho có cấu trúc" — vì không ai dùng, việc đúng theo nguyên tắc
"Surgical Changes" là **xoá** overload này (và `MultiErrorException` constructor tương ứng
nếu cũng không còn ai gọi) thay vì đầu tư sửa một API chết. Xác nhận lại lúc thực hiện bằng
grep; nếu có call site phát sinh giữa lúc viết plan và lúc thực hiện, đổi hướng sang giữ cấu
trúc field thay vì join chuỗi.

> ⚠️ **Khảo sát lại 2026-08-23 — tiền đề "0 call site" của mục này SAI.** Grep thật cho ra:
>
> | Mắt xích | Call site trong `src/main` |
> |---|---|
> | `ApiResponse.multiErrors(code, List<String>)` | **1** — `GlobalExceptionHandler:67`, nhánh `else` của `hasFieldErrors()` |
> | `ExceptionFactory.withErrors(code, List<String>)` | **0** (ngoài javadoc của chính nó) |
> | `new MultiErrorException(code, List<String>)` | **0** |
>
> Tức là: overload **có** người gọi, nhưng **không ai tạo ra** một `MultiErrorException` kiểu list
> ⇒ nhánh `GlobalExceptionHandler:67` hiện **không thể chạy tới**. Cả chuỗi chết từ đầu chứ không
> phải chỉ chết ở một đầu như plan mô tả.
>
> **Chưa thực hiện — cần quyết định**, vì hai đường plan vạch ra đều không còn đúng:
> - *"Xoá"* nay phải xoá **bốn** mắt xích (factory method, constructor, overload, nhánh handler) và
>   thu `MultiErrorException` về chỉ còn field-errors. Là dọn dẹp thật, ~30 dòng, **không** đổi hành
>   vi vì nhánh đó không chạy tới được.
> - *"Giữ cấu trúc thay vì join chuỗi"* nghĩa là thiết kế một hình dạng wire mới cho một nhánh chưa
>   ai chạm — đúng loại code speculative `coding-rules.md §11.5` cấm.
>
> Không tự chọn hộ: cả hai đều là quyết định, không phải việc cơ học.

## 5. Thứ tự thực hiện đề xuất

```
EH-1 (an toàn, không breaking) ──┐
EH-3 (an toàn, breaking nhẹ)     ├─→ độc lập nhau, làm song song hoặc theo thứ tự bất kỳ
EH-4 (an toàn, không breaking)   │
EH-6 (tài liệu thuần)           ─┘

EH-5 sau EH-4 (để không xoá nhầm mã EH-4 vừa dùng)

EH-2 làm SAU CÙNG, riêng một đợt — việc lớn nhất, breaking change thật,
cần chốt phạm vi + phối hợp FE bằng AskUserQuestion trước khi code

EH-7, EH-8: tuỳ chọn, không lịch cụ thể
```

Lý do tách EH-2 ra cuối: nó là hạng mục duy nhất đổi hành vi quan sát được từ phía client
đang chạy đúng. Làm nó sau khi các hạng mục an toàn đã xong giúp: (a) EH-1 đã có sẵn lưới an
toàn nếu quá trình đổi mã ở đâu đó tạo ra exception lạ, (b) baseline test đã ổn định qua các
đợt nhỏ trước, dễ cô lập nếu EH-2 làm hỏng gì.

## 6. Definition of Done (áp dụng từng hạng mục, theo `coding-rules.md §11.7`)

- Compile sạch, `mvn -o clean verify` xanh (`failures = 0, errors = 0`).
- Không sửa migration cũ (EH-3 nếu cần constraint mới phải là migration mới).
- Test cũ bị ảnh hưởng đã **sửa** theo `R10`, không xoá; case cuối ≥ baseline.
- `error-handling.md` + `CLAUDE.md` (nếu đụng bất biến/phase state) cập nhật trong cùng
  lượt — theo `dev-workflow.md §6.5`.
- Với EH-2: có mục "Breaking Changes" liệt kê từng endpoint đổi `code`, theo đúng format các
  phase trước trong `CLAUDE.md` (bảng `# | Endpoint | Điều kiện | Cũ | Mới`).
- Commit theo `dev-workflow.md §6.2`/`§6.6`, mỗi hạng mục một commit (hoặc nhóm các hạng mục
  an toàn EH-1/EH-3/EH-4/EH-6 vào 1-2 commit nếu làm cùng lượt).

## 7. Rủi ro & rollback

| Hạng mục | Rủi ro chính | Giảm thiểu |
|---|---|---|
| EH-1 | Che giấu lỗi thật nếu log không đủ chi tiết | Log `ERROR` đầy đủ stack trace trước khi trả response rút gọn |
| EH-2 | Phá FE nếu đang rẽ nhánh theo `OPERATION_NOT_ALLOWED` | Xác nhận với FE trước, cân nhắc cửa sổ chuyển tiếp như `§0.42` |
| EH-3 | Fallback mới (`BUSINESS_RULE_VIOLATION` thay vì `RESOURCE_ALREADY_EXISTS`) đổi status 409→422 cho các constraint lạ chưa từng gặp | Rà migration trước, liệt kê hết constraint quan trọng vào map thay vì dựa fallback |
| EH-4 | `REQUIRES_NEW` bean mới có thể lộ thêm race condition nếu không cẩn thận | Copy đúng pattern `WorkOrderBlockRecorder` đã kiểm chứng, không tự thiết kế lại |
| EH-5 | Xoá nhầm mã một tài liệu ngoài đang hứa | Grep cả `docs/` trước khi xoá, không chỉ `src/main` |

## 8. Kết quả khảo sát EH-2 *(hoàn thành 2026-08-23)*

`grep -rn "OPERATION_NOT_ALLOWED" src/main` → **96 throw site** (không kể khai báo enum), 15 module.
Phân loại theo **lý do nghiệp vụ**, không theo module:

| Nhóm | Số site | Nội dung | Đủ ngưỡng tách? |
|---|---|---|---|
| **A — Master data `INACTIVE`** | **23** | Công ty/nhà máy/kho/vật tư/nhà cung cấp/role/permission/scope/work center/work calendar/import profile đang `INACTIVE` mà bị dùng, hoặc kích hoạt con khi cha `INACTIVE` | ✅ |
| **B — Sai phạm vi sở hữu** | **32** | "must belong to" / "does not belong to" — item khác company, warehouse khác plant, lot khác item, serial khác item, routing operation trỏ work center khác plant, scope chứa plant/warehouse không thuộc company | ✅ |
| **C — Chứng từ chưa có dòng con** | **4** | BOM activate không có line · Routing activate không có operation · Import run không có dòng hợp lệ · Access scope non-global chưa có resource | ❌ (dưới ngưỡng) |
| **D — Input sai, không đồng nhất** | **37** | Sai `ItemType`, lot/serial tracking không khớp, sai `SupplySuggestionType`, ngày tháng vô lý, break ngoài ca, nhiều supplier trong 1 PR, role hệ thống không deactivate được… | ❌ (không có trục chung để FE rẽ nhánh) |

Nhóm A và B qua ngưỡng ở **cả hai** điều kiện của Bước 2: đủ số lượng **và** có giá trị rẽ nhánh thật
cho FE — "cha đang ngừng hoạt động" là việc người dùng đi kích hoạt lại, "chọn nhầm phạm vi" là việc
người dùng đổi lựa chọn trên form. Hôm nay hai tình huống đó trả **cùng một `code`**, nên FE chỉ còn
cách đọc `message` tiếng Anh.

Nhóm C và D **giữ nguyên `OPERATION_NOT_ALLOWED`** — đúng vai catch-all "input sai chung chung" mà
`error-handling.md §5.3` đã định nghĩa, không phải nợ.

### 8.1 Hai phát hiện phụ (ngoài phạm vi EH-2, ghi lại để không mất)

1. 🔴 **`ImportRunService.invalidState` trả 422 nhưng đọc *status chứng từ*** — 3 call site
   (`validated`/`applied`/`cancelled`, `module/dataimport/service/ImportRunService.java:366`). Theo
   đúng ba tiêu chí ở `error-handling.md §5.3` đây phải là `STATE_CONFLICT` (**409**). Cùng loại nợ
   `#26` mà `D11` đã đóng cho `planning` — `dataimport` ra đời sau nên chưa từng được rà. **Không sửa
   trong EH-2** (EH-2 tách mã trong cùng status 422; đây là đổi status, một quyết định riêng).
2. **`WorkOrderService.ensureDoesNotExceed` (`:543`) là dead code** — private, **0 call site**. Nó là
   site duy nhất trong 96 site dùng message mặc định của enum. Chỉ ghi nhận, không xoá (dead code
   ngoài phạm vi task, `CLAUDE.md` mục 3).

## 9. Breaking Changes & nghiệm thu *(2026-08-23)*

### 9.1 Breaking change trên wire

🔴 **`code` đổi, HTTP status KHÔNG đổi** ở cả ba mục dưới đây. Client rẽ nhánh theo `status()` không
bị ảnh hưởng; client rẽ nhánh theo `code` — đúng thứ `best-practices.md A7` yêu cầu — thì có.

#### (a) EH-2 — 59 throw site rời khỏi `OPERATION_NOT_ALLOWED`

Các throw site này nằm trong **lookup/validate service dùng chung**, nên chúng không thuộc về một
endpoint mà bị *mọi* endpoint đi qua chúng thừa hưởng. Liệt kê theo nơi ném, không theo endpoint —
liệt kê theo endpoint sẽ vừa dài vừa sai (bỏ sót đường gọi gián tiếp):

| # | Nơi ném | Điều kiện | Cũ | Mới |
|---|---|---|---|---|
| 1 | `OrganizationLookupService.getActiveCompany/Plant/Warehouse` | công ty / nhà máy / kho `INACTIVE` | `OPERATION_NOT_ALLOWED` 422 | `RESOURCE_INACTIVE` 422 |
| 2 | `OrganizationLookupService.getActiveWarehouseInPlant` | kho không thuộc nhà máy | 422 | `RESOURCE_SCOPE_MISMATCH` 422 |
| 3 | `OrganizationService.createPlant/createWarehouse/activatePlant/activateWarehouse` | cha `INACTIVE` | 422 | `RESOURCE_INACTIVE` 422 |
| 4 | `ItemLookupService.getActiveItem` · `ItemService.create/update/activate` | vật tư hoặc công ty cha `INACTIVE` | 422 | `RESOURCE_INACTIVE` 422 |
| 5 | `ItemLookupService` (lot ≠ item) · `InventoryMovementService` (item ≠ warehouse company, lot ≠ item, serial ≠ item) | khác chủ sở hữu | 422 | `RESOURCE_SCOPE_MISMATCH` 422 |
| 6 | `ItemWarehouseSettingService.upsert` | kho `INACTIVE` / item ≠ kho company | 422 | `RESOURCE_INACTIVE` · `RESOURCE_SCOPE_MISMATCH` |
| 7 | `SupplierService.findActiveSupplier` / preferred supplier · `SupplierService` (item ≠ supplier) | `INACTIVE` / khác chủ | 422 | `RESOURCE_INACTIVE` · `RESOURCE_SCOPE_MISMATCH` |
| 8 | `WorkCenterLookupService` · `WorkCalendarLookupService` | work center / work calendar `INACTIVE` | 422 | `RESOURCE_INACTIVE` 422 |
| 9 | `WorkCenterService` · `WorkCalendarService` | work calendar / shift khác nhà máy | 422 | `RESOURCE_SCOPE_MISMATCH` 422 |
| 10 | `RoutingService.create` | operation trỏ work center khác nhà máy · item khác công ty | 422 | `RESOURCE_SCOPE_MISMATCH` 422 |
| 11 | `BomService` · `ItemStandardCostService` · `PlanningService` · `PlanningDemandService` · `SalesOrderService` · `PurchaseOrderService` · `PurchaseRequisitionService` · `WorkOrderService` | "must belong to" | 422 | `RESOURCE_SCOPE_MISMATCH` 422 |
| 12 | `BomService.activateBom` · `RoutingService.activate` · `ImportRunService.apply` · `AccessControlService.assignRole` | chứng từ chưa có dòng con | 422 | `DOCUMENT_HAS_NO_LINES` 422 |
| 13 | `AccessControlService` | role / permission / access scope `INACTIVE`; scope chứa plant/warehouse không dùng được | 422 | `RESOURCE_INACTIVE` · `RESOURCE_SCOPE_MISMATCH` |
| 14 | `BomService.createBom` · `ImportRunService` | công ty cha / import profile `INACTIVE` | 422 | `RESOURCE_INACTIVE` 422 |

**37 site còn lại giữ nguyên `OPERATION_NOT_ALLOWED`** — có chủ đích, xem `§8` nhóm C/D.

#### (b) EH-3 — ràng buộc CSDL không phải trùng khoá

| # | Điều kiện | Cũ | Mới |
|---|---|---|---|
| 1 | Vi phạm khoá ngoại / `CHECK` / `NOT NULL` (mọi endpoint ghi) | `RESOURCE_ALREADY_EXISTS` **409** | `BUSINESS_RULE_VIOLATION` **422** |

Là **sửa sai**: trả 409 "đã tồn tại" cho một khoá ngoại sai khiến client tưởng trùng dữ liệu và thử
lại một việc không bao giờ thành công. Vi phạm `uk_*` vẫn giữ nguyên 409.

#### (c) EH-1 — lỗi thoát khỏi filter chain

| # | Điều kiện | Cũ | Mới |
|---|---|---|---|
| 1 | Redis chết / exception lạ trong 3 filter bảo mật | body `/error` mặc định `{timestamp,status,error,path}` | envelope `{code,result,message}` + `INTERNAL_SERVER_ERROR` |

Status vẫn **500**; chỉ hình dạng body đổi — và hình dạng cũ chưa bao giờ là hợp đồng, nó là thứ lọt ra.

### 9.2 Không phải breaking change

- **EH-4** — `MrpRunResponse` không đổi hình dạng; chỉ nội dung `errorMessage` của lượt chạy hỏng đổi
  từ message nội bộ sang một câu cố định (khi lỗi **không** phải `AppException`).
- **EH-5** — 4 hằng bị xoá chưa bao giờ xuất hiện trên wire.
- **EH-6** — thuần tài liệu.

### 9.3 Java positional / constructor

| Thay đổi | Ảnh hưởng |
|---|---|
| `MrpRunService` constructor **+1** tham số (`MrpRunStateRecorder`, cuối) | `MrpRunServiceTest`, `MrpPlanningMethodSecurityTest` đã **sửa** theo `R10` |
| `AccessScopeInvariantSecurityTest.assertOperationNotAllowed` → `assertRejectedWith(ErrorCode, …)` | 10 call site trong chính class đó; nay mỗi site pin mã của riêng nó (mạnh hơn trước) |

### 9.4 Nghiệm thu

| Mốc | Unit | IT |
|---|---|---|
| Baseline (trước EH) | 1155 case / 147 class | 140 case / 20 class |
| Sau EH-1/3/4/5/6 | 1176 case / 152 class | 142 case / 21 class |
| Sau EH-2 + 4 case do mutation #5 phát hiện (cuối) | **1181 case / 152 class** | **142 case / 21 class** |

`failures = 0, errors = 0` ở mọi mốc, đo bằng `mvn -o clean verify` thật với Docker.

**Class test mới:** `ApiErrorControllerTest`, `DataIntegrityErrorMapperTest`,
`RateLimitFilterErrorEnvelopeTest` (unit) · `MrpRunStateRecorderIT` (**class IT thứ 21**).

🔴 **Số liệu đáng nhớ nhất của lượt này: đổi 59 throw site nhưng chỉ 23 assertion đỏ.** Tức
**hơn một nửa** số throw site được sửa **không có test nào pin `code` của nó** — chúng chỉ được phủ
qua `isInstanceOf(AppException.class)` hoặc qua mock stub tự nhất quán. Đúng loại lỗ hổng rule `R1`
được viết ra để chặn, và là lý do phải đọc kỹ *test nào đỏ* thay vì chỉ chờ build xanh.

### 9.5 Nghiệm thu mutation (5 mutation, **5/5 đụng `src/main`**, đã revert hết)

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Bỏ nhánh `catch (RuntimeException)` khỏi `JwtAuthenticationFilter` (EH-1) | **1** — `unexpectedRuntimeException_answersTheStandardEnvelopeWithoutLeakingTheCause` | Exception bay khỏi chain đúng như trước khi vá; test bắt bằng chính exception thoát ra, không phải bằng status |
| 2 | Bỏ tra `KNOWN` trong `DataIntegrityErrorMapper`, để prefix rule quyết định (EH-3) | **3** — 2 case mapper + `GlobalExceptionHandlerTest.mappedConstraint_keepsItsSpecificErrorCode` | 🔴 **Cùng HTTP status ở cả hai phía** (`LOT_CODE_ALREADY_EXISTS` và `RESOURCE_ALREADY_EXISTS` đều **409**) ⇒ chỉ assert bám `$.code` mới bắt được, đúng cảnh báo `CLAUDE.md §0.15` |
| 3 | `clientSafeFailureMessage` trả thẳng `e.getMessage()` (EH-4) | **1** — `run_unexpectedFailure_recordsAFixedMessageInsteadOfTheRawCause` | HTTP **vẫn 201**, mã lỗi không đổi, chỉ nội dung một field đổi — không assertion nào khác thấy được |
| 4 | `MrpRunStateRecorder` đổi `REQUIRES_NEW` → `REQUIRED` (EH-4) | **2/2** `MrpRunStateRecorderIT` | Toàn bộ **1177 case unit vẫn xanh** cùng lượt chạy ⇒ minh hoạ trực tiếp `R7`: mock repository báo `save()` thành công cho một hàng mà rollback sẽ vứt đi |
| 5 | Đổi `RESOURCE_INACTIVE` → `RESOURCE_SCOPE_MISMATCH` ở `OrganizationLookupService.getActiveCompany` (EH-2) | **lần 1: 0 (!)** → sau khi bổ sung test: **1** | 🔴 **Kết quả giá trị nhất.** Xem dưới |

🔴 **Mutation #5 lần chạy đầu KHÔNG làm đỏ một case nào** — và đó là phát hiện thật, không phải trục
trặc. `OrganizationLookupService.getActive{Company,Plant,Warehouse}` là **bộ validate được tái dùng
nhiều nhất repo** (mọi module chạm master data đều đi qua nó), nhưng `OrganizationLookupServiceTest`
chỉ phủ `resolveScope` — **không case nào pin `code` của ba nhánh `INACTIVE`**. Các test tưởng là phủ
chúng (`OrganizationServiceTest.createPlant_underInactiveCompany_fails`…) thật ra kiểm nhánh **riêng**
trong `OrganizationService`, không đi qua lookup service. Hai mã đều **422** nên cả assertion status
lẫn test tầng HTTP đều không thấy gì.

⇒ Đã bổ sung **4 case** (`getActiveCompany` / `getActivePlant` / `getActiveWarehouse` cả hai vế
warehouse-và-plant-cha / `getActiveWarehouseInPlant`). Chạy lại mutation #5: **1 case đỏ**.

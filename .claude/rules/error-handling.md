# Error Handling

> Tách từ `CLAUDE.md` §5 (2026-07-25). Áp dụng cho **mọi** controller/service trong repo — luôn load cùng mọi session.

> **Chuẩn**: Tập trung, nhất quán, production-grade.

## 5.1 Unified Response Contract (TẤT CẢ response)

> **Quy tắc**: Mọi response – **success lẫn error** – đều dùng **cùng một envelope** `ApiResponse`.
> Frontend chỉ cần xử lý 1 contract duy nhất: kiểm tra `code`, đọc `result` hoặc `message`.

```json
{
  "code":    "SUCCESS",              // ErrorCode string – luôn có mặt
  "result":  { ... },               // payload (null khi error)
  "message": "Mô tả ngắn gọn",      // human-readable – luôn có mặt
  "errors":  [ ... ]                // chỉ có khi lỗi validation theo field
}
```

| Field | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `code` | `string` | ✅ | **Tên constant** của `ErrorCode` (vd `INVALID_CREDENTIALS`, `INSUFFICIENT_AVAILABLE_STOCK`). Frontend code theo field này |
| `result` | `T \| null` | — | Data payload khi success; `null` khi error |
| `message` | `string` | ✅ | Thông báo ngắn gọn cho user / log |
| `errors` | `[{field, message}] \| absent` | — | Lỗi theo từng field. `@JsonInclude(NON_NULL)` nên **biến mất** khi không có |

> **`code` là tên enum, KHÔNG phải mã ngắn.** Trước `F1` (2026-07-26) `ErrorCode.code()` trả
> `"BIZ_010"` / `"VAL_001"` / `"AUTH_030"` — lệch với chính tài liệu này. `F1` đã sửa `code()` trả
> đúng tên constant như §5.3 mô tả. Vì rule `R8` cấm assert magic string, toàn bộ test hiện có
> vẫn xanh sau thay đổi này.

> **`errors` là mảng object, không phải map.** Mỗi field có thể xuất hiện **nhiều lần** (nhiều
> constraint cùng vi phạm) — không gộp, không "giữ lỗi đầu tiên".

> **Hai thứ tên giống nhau, mục đích khác hẳn — đừng gộp** (làm rõ ở `F5`):
>
> | | Header `X-Trace-Id` | Field `traceId` trên chứng từ |
> |---|---|---|
> | Mục đích | correlate **log** của một request | **truy xuất nguồn gốc** nghiệp vụ |
> | Vòng đời | sống bằng log retention | sống bằng vòng đời chứng từ |
> | Ở đâu | response header, MDC | cột DB + trong `result` của response |
> | Ai đọc | dev khi debug | auditor / QA khi truy vết lô hàng |
>
> Giá trị được lấy từ cùng một nguồn (`TraceIdFilter` → MDC → `TraceIdProvider`), nhưng khi đã
> persist thì nó là **dữ liệu nghiệp vụ**, không phải debug info. `F5` persist `traceId` trên
> `material_issues`, `production_executions`, `production_receipts`, `stock_movements`, cộng
> `production_receipts.source_wip_trace_ids` nối WIP → Receipt. Thông tin debug khác của request
> (`path`) vẫn **không** xuất hiện trong body.

#### Ví Dụ

```json
// Success – GET single
{ "code": "SUCCESS", "result": { "id": "abc", "status": "RELEASED" }, "message": "OK" }

// Success – danh sách phân trang
{
  "code": "SUCCESS",
  "result": {
    "content": [...], "page": 0, "size": 20, "totalElements": 150,
    "totalPages": 8, "first": true, "last": false
  },
  "message": "OK"
}

// Success – không có content (DELETE)
{ "code": "SUCCESS", "result": null, "message": "Deleted successfully" }

// Error – validation (message = tóm tắt phẳng; errors = cấu trúc để bind vào form)
{
  "code": "VALIDATION_ERROR", "result": null,
  "message": "quantity: must be greater than 0; name: must not be blank",
  "errors": [
    { "field": "quantity", "message": "must be greater than 0" },
    { "field": "name",     "message": "must not be blank" }
  ]
}

// Error – business rule
{ "code": "INSUFFICIENT_AVAILABLE_STOCK", "result": null, "message": "Not enough stock for material MAT-001" }

// Error – rate limit
{ "code": "RATE_LIMIT_EXCEEDED", "result": null, "message": "Too many requests. Retry after 45s." }

// Error – idempotency: cùng key nhưng payload khác (F1)
{ "code": "IDEMPOTENCY_CONFLICT", "result": null, "message": "Idempotency-Key was already used with a different payload" }
```

## 5.2 Phân Cấp Exception *(viết lại 2026-08-23, `EH-6` — bản cũ mô tả một hệ thống không tồn tại)*

> 🔴 **Trước `EH-6` mục này vẽ một cây 12 lớp** (`ResourceNotFoundException`, `BusinessRuleException`,
> `TokenExpiredException`, `SessionConflictException`…). **Không lớp nào trong số đó tồn tại trong
> `src/main`.** Ai đọc tài liệu trước code sẽ đi tìm nhầm class, và tệ hơn là tưởng phải *thêm* một
> lớp exception mới mỗi khi thêm một loại lỗi. Thiết kế thật **đơn giản hơn** bản vẽ đó, không phải
> phức tạp hơn.

Repo chỉ có **hai** lớp exception, cộng một `interface` mang dữ liệu lỗi:

```
RuntimeException
└── AppException                 – mang ErrorCode + HttpStatus + message (ghi đè được)
    └── MultiErrorException      – thêm field errors (Map field→message) hoặc list message
```

```
interface ErrorCode { String code(); String message(); HttpStatus status(); }
├── AuthErrorCode        (enum)  – credentials, token, session, recovery, authorization
├── BusinessErrorCode    (enum)  – vi phạm quy tắc nghiệp vụ + hạ tầng (rate limit, internal)
├── ValidationErrorCode  (enum)  – input sai, resource not found / already exists
└── ImportErrorCode      (enum, module/dataimport) – lỗi cấp DÒNG của file import, KHÔNG phải
                                  lỗi HTTP; nó không đi qua GlobalExceptionHandler
```

**Hệ quả cần nhớ:**

1. **Loại lỗi mới ⇒ thêm một hằng vào enum, KHÔNG thêm class.** Status và message mặc định nằm trên
   chính hằng đó, nên `AppException` không cần biết gì về domain.
2. **`ExceptionFactory` là đường tạo exception duy nhất** (`notFound`, `alreadyExists`,
   `unauthorized`, `forbidden`, `businessRule`, `custom`, `withErrors`). Mọi factory method đều trả về
   cùng một `AppException`; **tên method không quyết định HTTP status** — status luôn lấy từ
   `ErrorCode`. Vì vậy `businessRule(STATE_CONFLICT, …)` trả **409**, không phải 422 (xem `EH-7`).
3. **Interface `ErrorCode` là điểm mở rộng.** Một domain có vốn từ lỗi riêng khai enum của chính nó
   thay vì nhồi thêm hằng vào `BusinessErrorCode` — `ImportErrorCode` là tiền lệ.
4. `AppException` có một constructor nhận `HttpStatus` tường minh để ghi đè status của `ErrorCode`;
   hiện **không call site nào dùng** — đừng đọc nó như cơ chế đang chạy.


## 5.3 ErrorCode Enum

> 🔴 **Không có "danh sách mã lỗi" trong tài liệu này nữa** *(sửa 2026-08-23, `EH-6`)*. Bản cũ chép
> một danh sách "thiết kế gốc" rồi tự ghi chú là nó không khớp code — một danh sách vừa lỗi thời vừa
> tự nhận là lỗi thời thì không ai dùng được. **Nguồn duy nhất là ba enum**:
> `AuthErrorCode`, `BusinessErrorCode`, `ValidationErrorCode` (`common/exception/`). Đọc thẳng ở đó.
>
> `EH-5` (2026-08-23) đã **xoá 4 hằng chưa từng có throw site** — `MRP_CALCULATION_ERROR`,
> `EXTERNAL_SERVICE_ERROR`, `PASSWORD_TOO_WEAK`, `SESSION_CONFLICT` — nên bản danh sách cũ ở đây còn
> hứa những mã không tồn tại. **5 hằng chưa dùng khác cố ý giữ lại**, mỗi cái kèm javadoc nêu lý do:
> `ITEM_ALREADY_ISSUED` / `PRODUCTION_ORDER_CLOSED` (đã hứa với FE ở `docs/api-guide-for-frontend.md`)
> và `LOT_WAREHOUSE_CONFLICT` / `RECEIPT_STATE_CONFLICT` / `BOM_REQUIREMENT_EXCEEDED` (nằm trong hợp
> đồng lỗi của phase BE-2/BE-4, `BE_SYSTEM_ISSUES_RESOLUTION_PLAN_2026-08-19.md`). Đừng xoá chúng vì
> "grep ra 0 kết quả" — đó chính là câu hỏi `EH-5` đã trả lời.

**Tên constant ≠ chuỗi `code()` trả ra wire** ở vài chỗ, vì lý do lịch sử. Bảng ánh xạ thực tế sau `F1`:

| Enum constant (dùng trong code/test) | `code()` trả ra wire | HTTP |
|---|---|---|
| `AuthErrorCode.ACCESS_DENIED` | `PERMISSION_DENIED` | 403 |
| `ValidationErrorCode.INVALID_INPUT` | `VALIDATION_ERROR` | 400 |
| `ValidationErrorCode.RESOURCE_NOT_FOUND` | `ENTITY_NOT_FOUND` | 404 |
| `BusinessErrorCode.INSUFFICIENT_STOCK` | `INSUFFICIENT_AVAILABLE_STOCK` | **409** *(F5 đổi từ 422)* |
| *(còn lại)* | trùng tên constant | — |

**Constant thêm ở `F1`** (theo `OmniPlant_MVP_Production_Backend_Handoff.docx` §8.2):
`STATE_CONFLICT` (409), `IDEMPOTENCY_CONFLICT` (409), `CONCURRENT_MODIFICATION` (409),
`RESERVATION_EXCEEDED` (409), `PLANNED_QUANTITY_EXCEEDED` (409), `LOT_NOT_ELIGIBLE` (409),
`LOT_REQUIRED` (400), `APPROVAL_REASON_REQUIRED` (400).

**Constant thêm ở `D8a`:** `TOKEN_REUSE_DETECTED` (401) — refresh token đã bị rotate away mà quay
lại. Cùng **401** với `REFRESH_TOKEN_EXPIRED` nên `code` là thứ **duy nhất** phân biệt "đăng nhập
lại đi" với "phiên của bạn vừa bị thu hồi vì phát hiện replay". Bất biến `B80`.

**Constant thêm ở `D8b`:** `SESSION_ABSOLUTE_TIMEOUT` (401) — phiên sống quá 30 ngày kể từ **login**,
bị thu hồi dù vẫn đang hoạt động. Đây là mã **thứ ba** cùng **401** trên `POST /auth/refresh`:

| `code` | Nghĩa | FE nên nói gì với user |
|---|---|---|
| `REFRESH_TOKEN_EXPIRED` | Token hỏng/hết hạn tự nhiên | "Đăng nhập lại" |
| `TOKEN_REUSE_DETECTED` | Replay ⇒ **mọi phiên đã bị thu hồi** | Cảnh báo bảo mật rõ ràng |
| `SESSION_ABSOLUTE_TIMEOUT` | Hết hạn theo chính sách 30 ngày | "Phiên đã hết hạn, đăng nhập lại" — **không** phải cảnh báo bảo mật |

⇒ HTTP status **không** phân biệt được ba trường hợp này. Client **phải** rẽ theo `code` (`A7`).
Bất biến `B81`.

**Constant thêm ở `D8c`:** `AuthErrorCode.RESET_TOKEN_INVALID` (401) trên `POST /auth/reset-password`
— **chỉ một** mã cho cả "token chưa từng tồn tại" lẫn "token đã hết hạn/đã dùng" (Redis TTL không
phân biệt được hai trường hợp, đúng cách `REFRESH_TOKEN_EXPIRED` đã xử lý cho refresh token). Danh
sách thiết kế gốc (bỏ ở `EH-6`) từng liệt kê cả `RESET_TOKEN_INVALID` **và** `RESET_TOKEN_EXPIRED` — chỉ vế đầu
được implement; `RESET_TOKEN_EXPIRED` **cố ý không thêm** vì không có nhánh nào thật sự ném nó
(`coding-rules.md §11.5`). Bất biến `B101`.

**Constant thêm ở P0 (2026-08-06, bugfix):** `AuthErrorCode.AUTHENTICATION_REQUIRED` (401) — trả bởi
`JwtAuthEntryPoint` khi request tới một endpoint cần auth mà **không** có `Authorization` header nào
(hoặc không đúng prefix `Bearer`) — nói cách khác, "chưa gửi credential gì cả". Trước đây chỗ này
hardcode `TOKEN_MALFORMED`, sai vì mã đó nên dành cho "đã gửi token nhưng token hỏng" (nhánh đó được
xử lý riêng, sớm hơn, ngay trong `JwtAuthenticationFilter`, không bao giờ chạm tới
`JwtAuthEntryPoint`). Hai mã giờ tách biệt đúng nghĩa:

| `code` | Nghĩa | Ai ném |
|---|---|---|
| `AUTHENTICATION_REQUIRED` | Không gửi credential gì | `JwtAuthEntryPoint` |
| `TOKEN_MALFORMED` | Có gửi, nhưng token hỏng/rác/sai chữ ký | `JwtAuthenticationFilter` (qua `JwtTokenProvider`) |

Chi tiết + bối cảnh phát hiện (hai lỗi P0 FE báo): `common/security/CLAUDE.md §4.20`.

**Constant thêm ở `F5`:** `MISSING_BOM` (409) — anh em của `MISSING_ROUTING`. Trước `F5`,
`BomLookupService.getActiveBom` ném `RESOURCE_NOT_FOUND` (404) trong khi routing tương ứng trả 409,
dù spec §8.1 coi hai lỗi cùng loại (nợ #14).

> **Nợ #9 đã trả ở `F5`.** `INSUFFICIENT_STOCK` nay là **409**, và mọi lỗi *sai trạng thái* của
> work order / material issue / reservation / WIP đã chuyển `OPERATION_NOT_ALLOWED` →
> `STATE_CONFLICT` (409). Lỗi *lot sai trạng thái* dùng `LOT_NOT_ELIGIBLE` (409) thay vì
> `OPERATION_NOT_ALLOWED`.
>
> `OPERATION_NOT_ALLOWED` (422) **vẫn còn dùng có chủ đích** cho **validate master data**, không phải
> state machine: item/warehouse `INACTIVE`, item sai `ItemType`, item và warehouse khác company,
> lot không thuộc item. Đó là "dữ liệu đầu vào sai", không phải "trạng thái chứng từ không cho phép" —
> đừng đổi tiếp cho "đồng bộ".
>
> **[`D7`, 2026-07-30] Nợ #9 đã trả hết.** 4 module còn lại đã được rà từng chỗ một (44 throw site):
> `bom` sửa **1**, `purchasing` sửa **8**, `organization` và `sales` sửa **0** (vốn đã đúng tiêu chí).
> Bảng liệt kê từng chỗ ở `FRONTEND_ALIGNMENT_ROADMAP.md §6.1.6`. Ba tiêu chí rút ra, dùng cho mọi
> module về sau:
>
> | Loại kiểm tra | Code | HTTP |
> |---|---|---|
> | Đọc **status** của chứng từ (`isDraft()`, `isApproved()`, `canReceive()`, `status != X`) | `STATE_CONFLICT` | 409 |
> | Số lượng vượt **trần của chứng từ** (nhận vượt số đặt, duyệt vượt số xin, good vượt plan) | `PLANNED_QUANTITY_EXCEEDED` | 409 |
> | Master data đang **`INACTIVE`** (công ty/nhà máy/kho/vật tư/nhà cung cấp/role/scope/work center/work calendar), **hoặc** kích hoạt con khi cha `INACTIVE` | **`RESOURCE_INACTIVE`** | **422** |
> | Hai bản ghi **khác chủ sở hữu** — "must belong to" / "does not belong to" (item khác company, warehouse khác plant, lot/serial khác item, routing operation trỏ work center khác plant) | **`RESOURCE_SCOPE_MISMATCH`** | **422** |
> | Chứng từ **chưa có dòng con nào** (BOM/routing chưa có line, import run không có dòng hợp lệ, scope non-global chưa có resource) | **`DOCUMENT_HAS_NO_LINES`** | **422** |
> | Còn lại: dữ liệu đầu vào sai (sai `ItemType`, lot/serial tracking không khớp, sai `SupplySuggestionType`, `dueDate` < `orderDate`, nhiều supplier trong 1 PR…) | `OPERATION_NOT_ALLOWED` | **422** |
>
> **[`EH-2`, 2026-08-23] Ba hàng đầu của bảng 422 tách ra từ `OPERATION_NOT_ALLOWED`.** Trước đó **96
> throw site** dùng chung một mã duy nhất, nên FE muốn phân biệt "công ty cha đang ngừng hoạt động" với
> "bạn chọn nhầm kho" chỉ còn cách parse `message` tiếng Anh — vi phạm chính `A7`. Khảo sát đầy đủ 96 site
> và lý do chỉ tách ba nhóm (không tách 1-1): `ExceptionHandlerRefactorPlan.md §8`.
>
> 🔴 **`OPERATION_NOT_ALLOWED` vẫn là câu trả lời đúng cho 37 site còn lại** — nó không bị "thay thế",
> chỉ thu hẹp lại đúng vai catch-all. Đừng tiện tay đổi nốt.
>
> Ranh giới hay bị nhầm nhất: **"chứng từ chưa có dòng nào" là 422, không phải 409.** Tiền lệ gốc là
> `RoutingService.activate` (`F4`) — nó đã tách đúng hai nhánh này từ trước `D7`.
>
> **[`D11`, 2026-07-31] `planning` đã được rà nốt.** `D7` tuyên bố nợ #9 "đóng hết" nhưng phạm vi rà
> của nó là 4 module `bom`/`purchasing`/`organization`/`sales` — `planning` **chưa bao giờ** nằm
> trong đó (nợ #26). 3 chỗ đọc status chứng từ nay là `STATE_CONFLICT` (409):
> `PlanningDemandService.cancel`, `SupplySuggestionService.convertToWorkOrder`,
> `SupplySuggestionService.ensureDraft`. Chi tiết + danh sách chỗ **cố ý giữ 422**: bất biến `B74`
> ở `module/planning/CLAUDE.md`.
>
> 🔴 **Bài học rút ra khi rà module tiếp theo:** dấu hiệu mạnh nhất **không phải** là bảng tiêu chí ở
> trên, mà là **hai đường vào cùng một điều kiện nghiệp vụ trả 2 status khác nhau**. Ở nợ #26,
> `convert-to-purchase-requisition` (409 từ `D7`) và `convert-to-work-order` (422) đọc **cùng** một
> `!suggestion.isApproved()`. Khi rà, hãy tìm những cặp như vậy trước — chúng là bằng chứng tự thân,
> không cần viện tới tài liệu.

## 5.4 Global Exception Handler (`@RestControllerAdvice`)

Xử lý theo thứ tự ưu tiên (thêm `@Order(HIGHEST_PRECEDENCE)`):

| Exception | HTTP | ErrorCode | `errors[]`? |
|---|---|---|---|
| `MultiErrorException` | theo `httpStatus` | theo `errorCode` | ✅ khi có field errors |
| `AppException` (mọi lỗi nghiệp vụ) | theo `httpStatus` | theo `errorCode` | — |
| `MethodArgumentNotValidException` (`@Valid` body) | 400 | `VALIDATION_ERROR` | ✅ |
| `ConstraintViolationException` (`@Validated` path/query) | 400 | `VALIDATION_ERROR` | ✅ |
| `MethodArgumentTypeMismatchException` (vd UUID sai ở path) | 400 | `VALIDATION_ERROR` | ✅ |
| `MissingServletRequestParameterException` (thiếu `@RequestParam` bắt buộc) | 400 | `VALIDATION_ERROR` | ✅ |
| `HttpMessageNotReadableException` (JSON hỏng/thiếu body) | 400 | `VALIDATION_ERROR` | — |
| `HttpRequestMethodNotSupportedException` | 405 | `VALIDATION_ERROR` | — |
| `NoResourceFoundException` / `NoHandlerFoundException` (không có endpoint ở path đó) | 404 | `ENTITY_NOT_FOUND` | — |
| `AccessDeniedException` (Spring) | 403 | `PERMISSION_DENIED` | — |
| `ObjectOptimisticLockingFailureException` (`@Version`) | 409 | `CONCURRENT_MODIFICATION` | — |
| `DataIntegrityViolationException` | **409 hoặc 422 — tuỳ constraint** | `RESOURCE_ALREADY_EXISTS` · `BUSINESS_RULE_VIOLATION` · `LOT_CODE_ALREADY_EXISTS` | — |
| `Exception` (catch-all) | 500 | `INTERNAL_SERVER_ERROR` | — |

> 5 handler ở giữa bảng được thêm ở `F1`. Trước đó chúng **không** được handle nên rơi xuống
> catch-all và trả **500** — một UUID sai định dạng ở path cũng thành lỗi server.
>
> 🔴 **[2026-08-04] `MissingServletRequestParameterException` bị sót trong lần vá đó** — phát hiện qua
> log thật: `GET /inventory/movements` thiếu `warehouseId` trả **500 `INTERNAL_SERVER_ERROR`**. Thiếu
> param bắt buộc là **input sai của client** (400), không phải server hỏng; trả 500 khiến FE không
> phân biệt được "tôi gọi sai" với "backend chết" và làm alert 5xx nổ oan. Nay trả 400
> `VALIDATION_ERROR` + `errors[{field: "<tên param>"}]`, có test
> (`GlobalExceptionHandlerTest.missingRequiredQueryParam_returns400NamingTheParameter`).
> 🔴 **[2026-08-12] `NoResourceFoundException` bị sót theo đúng cùng một cách** — phát hiện khi FE probe
> `GET /access/roles/{roleId}/permissions` (endpoint lúc đó chưa tồn tại) và nhận **500
> `INTERNAL_SERVER_ERROR`**, trace `bbad9f8c37824349`. Spring 6.1 đẩy request không khớp handler nào
> sang `ResourceHttpRequestHandler`, chỗ này ném `NoResourceFoundException`; không handler nào bắt nên
> nó rơi xuống catch-all. Hệ quả giống hệt bài học 2026-08-04: URL gõ sai hoặc endpoint chưa làm trông
> như **backend sập**, và alert 5xx nổ oan. Nay trả 404 `ENTITY_NOT_FOUND`, có test
> (`GlobalExceptionHandlerTest.unmappedPath_returns404NotAnInternalServerError`) **cùng** một test canh
> ranh giới ngược lại (`wrongVerbOnExistingPath_stays405`) — path **có** tồn tại mà sai verb vẫn phải là
> **405** của handler 8, không bị nhánh 404 mới nuốt mất. `NoHandlerFoundException` gộp chung
> `@ExceptionHandler` để câu trả lời không phụ thuộc `spring.mvc.throw-exception-if-no-handler-found`.
>
> **`MissingRequestHeaderException` cố ý KHÔNG thêm handler:** mọi `@RequestHeader` trong repo đều
> `required = false` (`Idempotency-Key`, `X-Plant-Id`) nên exception đó **không có đường ném** —
> thêm handler cho nó là code speculative (`coding-rules.md §11.5`). Khi nào có header bắt buộc đầu
> tiên thì **đó** mới là lúc thêm, cùng test.

> **Catch-all không bao giờ expose stack trace ra client.** Log đầy đủ phía server.

### 5.4.1 Phân loại `DataIntegrityViolationException` *(`EH-3`, 2026-08-23)*

Trước `EH-3`, handler này đọc message driver bằng một chuỗi `detail.contains(...)` hardcode cho **một**
constraint của lot, và trả `RESOURCE_ALREADY_EXISTS` (409) cho **mọi** vi phạm còn lại. Sai ngữ nghĩa
với phần lớn: FK / `NOT NULL` / `CHECK` **không** phải "đã tồn tại", và client nhận 409 không có cách
nào phân biệt trùng khoá thật với payload sai — tức bị bảo hãy thử lại một việc không bao giờ thành công.

`DataIntegrityErrorMapper` phân loại theo **tên constraint** đọc từ message (`... constraint "tên"`):

| Thứ tự | Quy tắc | Kết quả |
|---|---|---|
| 1 | Có trong bảng `KNOWN` | mã + message riêng của nó |
| 2 | Tên bắt đầu `uk_` (quy ước duy nhất của schema này cho unique constraint/index) | `RESOURCE_ALREADY_EXISTS` (409) |
| 3 | Còn lại (`chk_`, `fk_`, `NOT NULL` không có tên) | `BUSINESS_RULE_VIOLATION` (**422**) |
| 4 | Không có tên constraint nhưng message chứa `duplicate key` | `RESOURCE_ALREADY_EXISTS` (409) |

🔴 **Tên constraint KHÔNG bao giờ trả ra response** — nó là chi tiết schema, chỉ ghi log (cùng lý do
catch-all không echo message exception). Thêm entry vào `KNOWN` khi một constraint **thật sự** từng làm
ai đó mất công chẩn đoán, không phải vì nó tồn tại: ~170 constraint mà mỗi cái một mã thì client không
phân biệt nổi (`coding-rules.md §11.5`).

### 5.4.2 Lỗi thoát khỏi filter chain *(`EH-1`, 2026-08-23)*

`@RestControllerAdvice` **chỉ** phủ exception phát sinh trong `DispatcherServlet`. Filter chạy **trước**
nó, nên một `RuntimeException` trong `JwtAuthenticationFilter` / `RateLimitFilter` / `UserRateLimitFilter`
(thực tế hay gặp nhất: Redis không kết nối được) thoát khỏi chain, **không** tới `GlobalExceptionHandler`,
và rơi vào trang `/error` mặc định của Spring Boot — body `{timestamp,status,error,path}`, một hình dạng
FE chưa từng được dạy đọc. Ba lớp bảo vệ nay:

1. **Mỗi filter tự bắt `RuntimeException`**, log `ERROR` kèm stack trace, trả envelope
   `INTERNAL_SERVER_ERROR`. **Không** echo `ex.getMessage()` — khác nhánh `AppException`, exception ở
   đây chưa được kiểm duyệt cho client đọc.
2. **`ApiErrorController`** (`common/exception/`) implement `ErrorController`, thay
   `BasicErrorController` của Boot, để **mọi** error dispatch cấp container cũng ra envelope chuẩn.
3. **`/error` là `permitAll`** trong `SecurityConfig`: Boot đăng ký security chain cho cả dispatcher
   type `ERROR`, nên thiếu dòng này một request chưa auth mà lỗi thật là 500 sẽ bị trả lời lại thành
   401 và mất status thật. `ApiErrorController` không tiết lộ gì về sự cố nên mở nó không tốn gì.

## 5.5 Auth Entry Points (Spring Security Layer)

Khi Spring Security chặn request (chưa vào controller):
- **401** (`AuthenticationEntryPoint`): Trả `ApiResponse.error(resolveAuthErrorCode, message)` — phân biệt `TOKEN_EXPIRED`, `TOKEN_REVOKED`, `TOKEN_MALFORMED`
- **403** (`AccessDeniedHandler`): Trả `ApiResponse.error(ACCESS_DENIED, "Insufficient permissions")`

## 5.6 Response Headers Chuẩn

| Header | Mô tả |
|---|---|
| `X-Trace-Id` | UUID 16 ký tự – correlate logs với request |
| `X-RateLimit-Limit` | Giới hạn request trong window |
| `X-RateLimit-Remaining` | Số request còn lại |
| `X-RateLimit-Rule` | Rule đang áp dụng |
| `Retry-After` | Giây cần chờ (chỉ khi 429) |
| `X-Session-Warning` | Chỉ có khi kick session cũ |

### 5.6.1 Request header `X-Plant-Id` — phạm vi áp dụng *(chốt ở `D1`, 2026-07-28)*

`X-Plant-Id` là header **request** (không phải response), do `F1` thêm, và là **cross-check chứ
không phải nguồn `plantId`** (quyết định `F1-D6`). Nó **chỉ** áp dụng cho endpoint **nêu plant tường
minh** — `/plants/{plantId}/…` hoặc `plantId` trong query/body:

| Nhóm endpoint | Có cross-check? | Vì sao |
|---|---|---|
| Nêu `plantId` tường minh (`/plants/{plantId}/work-orders`, `/planning-runs`, `/planning-demands`, `/sales-orders`) | ✅ Có | Có **hai** nguồn plant (header và request) ⇒ có cái để đối chiếu; lệch nhau ⇒ 409 `STATE_CONFLICT` |
| Định danh bằng aggregate (`/work-orders/{id}/…`, `/material-issues`, `/production-receipts/…`) | ❌ **Không, có chủ đích** | Chính `workOrderId` **là** scope — không có giá trị thứ hai để header đối chiếu, và `@PreAuthorize` đã chặn work order thuộc plant khác. Thêm check ở đây là thêm code không kiểm được gì |

⇒ Đừng "wire cho đồng bộ": endpoint định danh bằng aggregate **không** nhận header này.

> **[`F7`, 2026-07-31] Ranh giới là theo ENDPOINT, không theo controller.** Trước `F7`, mục này ghi
> "`ManufacturingExecutionController` cố ý không nhận header" — đúng lúc đó, vì **mọi** endpoint của
> nó định danh bằng aggregate. `F7` thêm `GET /production-executions/candidates?plantId=…` (spec §5.1),
> endpoint đầu tiên của controller đó nêu plant tường minh ⇒ nó **có** cross-check, đúng theo hàng 1
> của bảng trên. Các endpoint còn lại của controller vẫn không. Khi thêm endpoint mới, hỏi *"endpoint
> này có hai nguồn plant không?"*, đừng hỏi *"controller này có nhận header không?"*.
>
> 🔴 **[`F8`, 2026-07-31] Phase thứ BA chạm đúng cái bẫy đó.** `F8` thêm 4 endpoint vào cùng
> controller, và chúng chia **cả hai** phía của ranh giới:
>
> | Endpoint `F8` | Cross-check? | Vì sao |
> |---|---|---|
> | `GET /production-receipts/candidates?plantId=` | ✅ có | nêu plant tường minh |
> | `GET /production-receipts?plantId=&status=` | ✅ có | nêu plant tường minh |
> | `GET /material-issues?plantId=&workOrderId=` | ✅ có | nêu plant tường minh (`workOrderId` chỉ là filter) |
> | `GET /production-executions?workOrderId=` | ❌ **cố ý không** | định danh bằng aggregate — **một** nguồn plant duy nhất, không có gì để đối chiếu |
>
> ⇒ `ManufacturingExecutionController` nay chứa **cả hai loại cùng lúc**. Câu hỏi vẫn là *"endpoint
> này có hai nguồn plant không?"*. **Cả hai phía đều được pin bằng test** trong
> `ManufacturingExecutionControllerTest`: 3 endpoint đầu trả **409** khi header lệch, endpoint thứ tư
> trả **200** và bỏ qua header — comment không đỏ được, test thì có.

## 5.7 Request Tracing

Mỗi request được gán `traceId` (UUID 16 ký tự):
- Ưu tiên lấy từ header `X-Trace-Id` nếu có (distributed tracing từ upstream)
- Nếu không → tự sinh mới
- Lưu vào MDC → xuất hiện trong mọi dòng log → dễ trace khi debug
- Trả lại client qua header `X-Trace-Id`

**Logback pattern**: `%d{ISO8601} [%X{traceId}] [%X{userId}] [%X{clientIp}] %-5level %logger{36} - %msg%n`

## 5.8 Controller Patterns

| Operation | HTTP | Status trả về | ApiResponse |
|---|---|---|---|
| GET single | `GET /{id}` | 200 | `ok(data)` |
| GET list phân trang | `GET /` | 200 | `ok(PageResult.from(page))` |
| POST create | `POST /` | 201 | `created(data)` |
| PATCH partial update | `PATCH /{id}` | 200 | `ok(data)` |
| PUT full replace | `PUT /{id}` | 200 | `ok(data)` |
| DELETE | `DELETE /{id}` | 200 | `noContent("Deleted")` |
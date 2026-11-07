# Kế Hoạch Chi Tiết `P7` → `P11` — Đóng Phần Còn Thiếu So Với Lý Thuyết

> **Nguồn:** `MANUFACTURING_GAP_ROADMAP.md §2.0` (ma trận đối chiếu PDF ↔ code, 2026-09-25) + `§3`
> (phác thảo `P7`–`P11`) + `§2.0.1` (ba khoảng trống lớn nhất).
>
> **File này là gì:** thiết kế **thi công được** cho 5 phase còn lại — schema, service, API, bất biến,
> chuẩn bảo mật, chuẩn nhất quán dữ liệu, và **bộ test nghiệm thu chuẩn nghiệp vụ** cho từng phase.
> Roadmap trả lời *"còn thiếu gì"*; file này trả lời *"làm thế nào, và làm sao biết là đúng"*.
>
> **File này KHÔNG phải** `NEXT_PHASE_PLAN.md`. Theo `dev-workflow.md §6.5`, `NEXT_PHASE_PLAN.md` luôn
> chứa **đúng một** phase đang chạy; khi bắt đầu một phase ở đây thì copy mục của nó sang đó, không
> chạy song song hai nguồn sự thật.
>
> 🔴 **Đọc `§0` trước khi đọc bất cứ mục phase nào.** `§0` ghi **10 chỗ phác thảo trong roadmap không
> khớp code thật** — đã kiểm trực tiếp trên entity/migration/service ngày 2026-09-25. Ba trong số đó
> làm đổi hẳn thiết kế của `P7`; một chỗ làm `P10` **không chạy được** nếu làm theo đúng câu chữ
> roadmap.

---

## 0. Đối Chiếu Lại Phác Thảo Roadmap Với Code Thật

> Cách kiểm: đọc entity + migration + service thật, không đọc tài liệu. Mỗi hàng ghi **bằng chứng** để
> người sau kiểm lại được mà không phải tin file này.

| # | Phác thảo trong roadmap | Sự thật đo được | Hệ quả |
|---|---|---|---|
| **F1** | `P7`: *"`work_order_operation_id` **đã có sẵn** trên `wip_transactions`; chỉ cần dùng nó làm chiều gom nhóm"* | Cột **có**, nhưng **chỉ** `WipTransactionService.recordProductionExecution` truyền operation. `recordMaterialIssued` / `recordOutputReceipted` / `recordStart` **hardcode `null`** (`WipTransactionService.java:102-130`) | Gom giá trị **vật tư** theo công đoạn **không làm được** bằng cách "chỉ dùng cột có sẵn" |
| **F2** | `P7`: *"ghi một `wip_transactions` **âm**"* | `chk_wip_transactions_quantity CHECK (quantity IS NULL OR quantity > 0)` (`V16:80`) | Dòng âm bị **DB từ chối**. Phải mang dấu bằng `direction`, đúng khuôn `stock_movements`, không bằng số âm |
| **F3** | `P7`: ghi tiền *"cùng lúc với `quantity` ở 2 hook đã có"* | Tiền tích luỹ **theo từng component line** (`MaterialIssueService:271,449`) nhưng dòng WIP ghi **một dòng cho cả chứng từ** với `totalIssued` (`:293,457`) — tổng của **nhiều item khác nhau** | Hai độ mịn khác nhau. Một cột `amount` trên dòng đó chỉ là tổng, không chia được theo item |
| **F4** | — | `work_order_cost_accumulators` có `CHECK (… >= 0)` ×3 (`V50:49-51`); javadoc service ghi *"never subtract, never reset"* | Giải phóng WIP **không được** trừ accumulator. Nó là *"đã tiêu bao nhiêu"*, không phải *"còn lại bao nhiêu"* |
| **F5** | Roadmap không nhắc | `WorkOrderCostVarianceResponse` **đã có** `authoritative` + `authorityStatus`, hiện hardcode `false` / `"DEFERRED_NON_AUTHORITATIVE"` (`WorkOrderVarianceService:115-116`), và OpenAPI của `GET /v1/work-orders/{id}/variance` **đã hứa với FE**: *"costVariance is retained for compatibility but authoritative=false while detailed costing is deferred"* | `P7` là thứ **đúng để** lật cờ đó. Hợp đồng wire **có sẵn**, không phải field mới — xem `§7.6` |
| **F6** | `P9` / `module/purchasing/CLAUDE.md:29`: goods receipt item serial-tracked *"nổ `SERIAL_REQUIRED` (400)"* | Đường thật ném **`OPERATION_NOT_ALLOWED` (422)** — `requireSerialCode` (`InventoryMovementService:540-546`) hoặc `ensureSerialQuantityIsOne` (`:533`). `SERIAL_REQUIRED` có **đúng một** throw site, ở `ProductionReceiptService:535` | Test `P9` assert `SERIAL_REQUIRED` ở đường PO sẽ pin một mã **chưa bao giờ** được ném ở đó — xem `§9.5` |
| **F7** | `P10`: *"Tái dùng `CapacityBoardService.dayCapacityMinutes`/`utilizationPercent`"* | `getBoard` bị `@PreAuthorize(PERM_CAPACITY_READ)` và **khoá vào `WorkOrderOperation`**: `LOAD_STATUSES` chỉ gồm `RELEASED/IN_PROGRESS/COMPLETED/CLOSED`, và **chỉ `release()` mới ghi lịch**. Một **đề xuất MRP chưa có work order**, nên **không có dòng operation nào** để đưa vào `buildDayContext` | Gọi `getBoard` từ `MrpRunService` sẽ **403** với planner chỉ có `PERM_MRP_RUN` (cross-bean call **đi qua** proxy nên `@PreAuthorize` có hiệu lực). Phải dùng phần **static** + builder mới keyed theo work center — xem `§10.3` |
| **F8** | `P10` ngầm giả định biết số phút của đề xuất | `RoutingLookupService.findActiveRoutingSummaries` trả **`record RoutingSummary(routingId, code, version)`** — **không** operation, **không** phút | Cần lookup batch mới trả phút theo work center. Không có nó thì `P10` không có tử số |
| **F9** | `P11`: *"`PlanningDemandStatus.CONSUMED` … rất có thể được thêm cho đúng cơ chế này"* | **Không phải.** Javadoc `PlanningDemandService:164-165` định nghĩa `CONSUMED` = *"already snapshotted into an MRP run"* — "đã vào một lượt chạy", **khác hẳn** "forecast bị đơn hàng thật tiêu trừ" | Dùng lại `CONSUMED` là nhồi **hai nghĩa** vào một giá trị enum ⇒ không phân biệt được "đã vào run" với "đã bị đơn hàng ăn" — xem `§11.4` |
| **F10** | — | `supply_suggestions.message_codes VARCHAR(200)`; 7 mã hiện có join hết = **138 ký tự** | `P10` thêm `CAPACITY_OVERLOAD` (17+1) ⇒ 156, **vẫn vừa**. Mã thứ 9 phải đo lại: quá 200 là **INSERT chết**, làm sập cả lượt MRP |

### 0.1 Ba hệ quả thiết kế lớn nhất từ bảng trên

1. 🔴 **`P7` không được nhồi tiền vào `wip_transactions`** (`F1`+`F2`+`F3`). Thay bằng một **ledger giá
   trị riêng** `wip_cost_entries`, mang dấu bằng `direction` (đúng khuôn `stock_movements` đã có trong
   repo), nối về dòng số lượng bằng `source_wip_transaction_id`. Lý do đầy đủ: `§7.2`.

2. 🔴 **Giá trị vật tư KHÔNG thể quy về công đoạn với mô hình hiện tại.** Đã kiểm: `bom_lines` **không
   có** cột operation, `work_order_component_lines` cũng không, `MaterialIssueLineRequest` cũng không —
   chỉ có `sourceExecutionId`, mà `MaterialIssueService:497` giới hạn nó cho **rework** execution.
   ⇒ `P7` trả lời được *"công đoạn Hàn đang giữ bao nhiêu tiền **công + overhead**"* chính xác, còn
   **vật tư chỉ quy được về cấp work order**. Phải **nói thẳng điều đó trong response**, không để người
   đọc tưởng một con số một phần là con số đầy đủ. Xem `§7.3` + nợ mở `§12.1`.

3. 🔴 **`P10` phải tự dựng ngữ cảnh năng lực cho *đề xuất*, không tái dùng đường của *work order đã
   release*** (`F7`+`F8`). Phần tái dùng được là **công thức** (`static`), không phải điểm vào.

---

## 1. Thứ Tự Thi Công & Phụ Thuộc

```text
P8 (Operation status)  ──►  P7 (WIP Valuation + Variance snapshot)
                              ▲
P3 ✅, P4 ✅ ─────────────────┘
P5 ✅ ──────────────────────►  P9 (Genealogy + serial cho Goods Receipt)   [song song P7 được]
P4 ✅ ──────────────────────►  P10 (CRP đóng vòng)
                               P11 (MPS) — độc lập, làm cuối
```

| Thứ tự | Phase | Vì sao ở đây | Migration dự kiến | Permission |
|---|---|---|---|---|
| 1 | **`P8`** | Rẻ nhất; là nợ của `P4`; là chiều gom nhóm mà `P7` cần | `V96` | **không mới** (tái dùng) |
| 2 | **`P7`** | Khoảng trống lý thuyết lớn nhất. Sau `P8` để giá trị có chỗ neo | `V97` | **không mới** (tái dùng `PERM_COSTING_READ`) |
| 3 | **`P9`** | Thu hồi có mục tiêu; **không đụng `P7`** ⇒ song song được | `V98` | **không mới** |
| 4 | **`P10`** | Tinh chỉnh vòng hoạch định | 🔴 **không cần migration** (đã kiểm: `message_codes` **không có** `CHECK`, và `chk_supply_suggestions_exception_state` của `V34` **đã** cho `WARNING`) ⇒ thuần Java | **không mới** |
| 5 | **`P11`** | Chỉ cần khi khách sản xuất make-to-stock | `V99` + `V100` | **`PERM_MPS_READ`/`_MANAGE`** |

> ⚠️ **Số migration ở trên là dự kiến** — đánh số lại 2026-10-27 (bản gốc dự kiến `V69`–`V73`, đã bị `G2`–`G10` dùng hết). Đánh số lại lần nữa 2026-10-28: `CF-4` dùng `V93`, `CF-5` giữ `V94` ⇒ P7–P11 bắt đầu từ **`V95`**. Đánh số lại 2026-10-29: `CF-9` dùng `V95` ⇒ P7–P11 bắt đầu từ **`V96`**. Mở mỗi phase phải
> `ls src/main/resources/db/migration/ | sort -V | tail -3` lấy số thật — làm `P9` song song `P7` thì hai
> phase **giành cùng một số** và Flyway chặn. Chốt số ngay khi mở phase, ghi vào `NEXT_PHASE_PLAN.md`.

> **Bất biến mới:** cuối cùng hiện tại là **`B123`** (track `AR-*`). Cấp số từ **`B124`** theo thứ tự
> thi công. Mỗi bất biến **phải** có cột "test bảo vệ" như mọi hàng trong `module/*/CLAUDE.md` — bất
> biến không có test là văn xuôi.

> **Chỉ `P11` kích hoạt `coding-rules.md C10`** (permission mới ⇒ seed migration + cập nhật
> `docs/roles-and-permissions.md`). Bốn phase kia **cố ý** không thêm permission — ghi rõ trong bản ghi
> phase để người đọc sau không tưởng là bị quên (đúng cách `F8`/`F9`/`F10`/`D8b` đã ghi).

---

## 2. Chuẩn Chống Rò Rỉ Thông Tin — Áp Cho MỌI Phase

> Yêu cầu của user: *"đảm bảo cách thiết kế không gây rò rỉ thông tin"*. Đây là checklist **bắt buộc**.
> Mỗi phase có mục "Bảo mật & chống rò rỉ" riêng, chiếu về đây.

| # | Quy tắc | Vì sao, và cái bẫy cụ thể trong repo này |
|---|---|---|
| **L1** | **Tiền chỉ đi cùng `PERM_COSTING_*`.** Mọi response mang `amount`/`unitCost`/variance tiền phải gác bằng `PERM_COSTING_READ` hoặc `PERM_WORK_ORDER_VARIANCE_READ` (cả hai **ADMIN+MANAGER**, không OPERATOR — `V51`, `V17`) | Đây là lý do `P7` **không** thêm permission mới: tái dùng `PERM_COSTING_READ` làm tiền **tự động** nằm ngoài tầm OPERATOR. Ngược lại `PERM_INVENTORY_READ` **có** cấp cho OPERATOR ⇒ 🔴 **node genealogy của `P9` tuyệt đối không mang field tiền** |
| **L2** | **Authorize ở điểm vào KHÔNG đủ cho truy vấn đệ quy.** Phải **ghim company của node gốc** và lọc **mọi cấp** theo nó | `P9` đi lot → work order → lot NVL. Guard chỉ kiểm lot gốc. Không ghim company thì một mắt xích dữ liệu bẩn là một đường lộ dữ liệu công ty khác |
| **L3** | **Không phân biệt "không tồn tại" với "không có quyền".** Cả hai **403** | `InventoryPermissionGuard.hasLotCompanyAccess` đã đúng: `findById(...).map(...).orElse(false)` ⇒ lot lạ và lot không quyền **cùng** cho `false`. Là tính chất chống liệt kê — phải **có test giữ**, đừng "cải thiện" thành 404 cho dễ debug |
| **L4** | **Cắt biên thì báo là đã cắt, không báo cắt cái gì.** Trả `truncated` / `boundaryOmittedCount`, **không** trả id/mã của thứ bị loại | Nói "đã loại 3 lô" là tiết lộ **sự tồn tại** của 3 lô ngoài phạm vi; nói "có cắt" là thông tin người dùng cần để biết dữ liệu chưa đầy đủ |
| **L5** | **Cảnh báo, không phải dữ liệu nền.** Khi tính năng chỉ cần *tín hiệu*, trả mã tín hiệu, đừng trả số liệu sinh ra nó | `P10`: đề xuất MRP mang `CAPACITY_OVERLOAD`, **không** mang `utilizationPercent`/`capacityMinutes`. Muốn số thì mở Capacity Board bằng `PERM_CAPACITY_READ`. Nếu không, `PERM_MRP_RUN` thành cửa sau đọc năng lực nhà máy |
| **L6** | **Không echo message nội bộ / tên constraint / stack trace** | Đã là luật repo (`error-handling.md §5.4.1`, `EH-4`). Nhắc lại vì `P7`/`P11` có ràng buộc CSDL mới ⇒ vi phạm phải đi qua `DataIntegrityErrorMapper`, không tự bắt rồi kể lể |
| **L7** | **Endpoint mới không được bỏ qua `@PreAuthorize` do self-invocation** | Bẫy đã gây lỗ hổng thật ở `F8` (`CLAUDE.md §0.19` #3) và suýt tái diễn ở `AR-*` (`§0.49` #2). Endpoint alias **uỷ quyền từ controller**, không gọi `this.method()` |
| **L8** | **Audit mọi hành động ghi mới** (`coding-rules.md §11.7`) | `P8` start/complete, `P7` snapshot variance, `P11` publish MPS đều là hành động có người chịu trách nhiệm ⇒ `AuditAction` **riêng**, không gộp vào action cũ cho tiện |

---

## 3. Chuẩn Nhất Quán Dữ Liệu — Áp Cho MỌI Phase

> Yêu cầu của user: *"dữ liệu phải nhất quán"*. Bảng này là các bẫy **đã trả giá** trong repo, viết lại
> thành luật cho 5 phase tới.

| # | Quy tắc | Tiền lệ đã trả giá |
|---|---|---|
| **C-1** | **Bảo đảm thật nằm ở DB, không ở nhánh `if` trong service.** Mỗi bất biến số học/duy nhất có `CHECK`/`UNIQUE` tương ứng | `§0.44` mutation #4: xoá `UNIQUE` khỏi `V58` ⇒ chỉ `FlywayMigrationIT` đỏ, service test vẫn xanh |
| **C-2** | **Không `UNIQUE` trên tập cột có `NULL`.** Postgres coi hai `NULL` là khác nhau ⇒ ràng buộc im lặng vô hiệu cho đúng những dòng cần nó nhất | Nợ #10 / `D6` #2 — lý do idempotency **không** scope theo `created_by`. `P7` vì thế **không** đặt unique lên `(reference_type, reference_id, work_order_operation_id, item_id)` |
| **C-3** | **Không backfill dữ liệu chưa ai từng chụp.** `NULL` = *"trước phase này không ai theo dõi"*, khác `0` = *"đo được và bằng không"* | `B77` (`V40`), `V67` (không backfill `plant_id`), `V65`. `P8 §8.3` áp đúng luật này cho `status` |
| **C-4** | **Số sinh ở `@PrePersist`, và công thức Java khớp từng ký tự với backfill SQL** | `B68` (`§0.12` #5), `B79` (`F10` mutation #4) |
| **C-5** | **Hàng phải sống sót transaction đang hỏng ⇒ ghi ở `REQUIRES_NEW` qua bean RIÊNG**, và **commit trước** khi phần dễ đổ vỡ chạy. `saveAndFlush` **không** đủ: hàng đã flush mà chưa commit là **vô hình** với `REQUIRES_NEW` | `B120` (`AR-*`), `EH-4` (`MrpRunStateRecorder`) |
| **C-6** | **Đọc–sửa–ghi trên hàng dùng chung phải dựa `@Version`**, trả `CONCURRENT_MODIFICATION` (409), không ghi đè im lặng | `§0.40` bug #2: `lines` là collection inverse nên thay dòng **không** bump version ⇒ hai request đồng thời cùng qua check. `P11 §11.4` đúng hình dạng đó |
| **C-7** | **Một con số, một công thức.** Hai endpoint báo hai số cho cùng câu hỏi tệ hơn thiếu endpoint | Lý do `CapacityBoardService.dayCapacityMinutes` là `public static` (`C2-8`). Áp cho `P10` (board ↔ MRP) và `P7` (live ↔ snapshot) |
| **C-8** | **1 aggregate query mỗi cấp, không loop theo dòng** (`C14`); đệ quy có **cycle guard** + **trần độ sâu** | `CostingService` đã có cycle guard. `P9` đệ quy nhiều cấp ⇒ bắt buộc cả hai, cộng trần để payload không vô hạn |
| **C-9** | **Thêm path thứ hai vào `@EntityGraph` đã có collection ⇒ đi HẾT association** hỏi path đó có giải ra `List` không | `MultipleBagFetchException` nổ **ba lần**: `§0.27`, `§0.29`, `§0.45` (lần ba bag cách một association) |
| **C-10** | **`String` nullable vào `concat`/`like` ⇒ `cast(:x as string)`; `Instant` chỉ xuất hiện ở vế `IS NULL` ⇒ `cast(:x as timestamp)`** | `lower(bytea)` `§0.24` (2 chỗ) + `§0.36` #4. Cả hai **chỉ** `*IT` bắt được |
| **C-11** | **Thêm giá trị vào enum đang có ⇒ chạy đủ checklist `coding-rules.md §11.3`** | `P6` tìm ra bug thật nhờ checklist này: `canReserve()` là **danh sách phủ định** nên `CLOSED` không tự bị loại (`§0.34` #1). `P8`/`P10`/`P11` đều thêm enum |

---

## 4. Chuẩn Kiểm Thử — Áp Cho MỌI Phase

> Yêu cầu của user: *"mỗi phase phải viết test để kiểm tra đã đúng với chuẩn nghiệp vụ của doanh nghiệp
> chưa"*. Repo đã có `R1`–`R10` (`best-practices.md §8.6.1`); mục này nói **tầng nào phải có gì**, và
> thêm một tầng repo chưa gọi tên: **test chuẩn nghiệp vụ**.

### 4.1 Năm tầng bắt buộc

| Tầng | Chứng minh điều gì | Bẫy nếu thiếu |
|---|---|---|
| **1. Domain unit** | State machine + công thức, trên entity thuần | Thiếu ⇒ công thức chỉ được kiểm gián tiếp qua mock |
| **2. Service unit** | Gate nghiệp vụ, **mã lỗi** (`R1`), **side-effect** (`R4`), nhánh bị chặn **không rò xuống tầng dưới** (`R5`) | Assert `isInstanceOf(AppException)` là lỗ hổng `EH-2` đã đo: đổi 59 throw site mà chỉ 23 assertion đỏ |
| **3. Method-security** | deny **+** allow **+** `verify(guard)` khoá đúng chuỗi `PERM_*` (`R2`) | Chỉ có deny thì sai tên permission vẫn xanh (mock trả `false` mặc định) |
| **4. Controller (`@WebMvcTest`)** | Envelope `{code,result,message}`, 400 type-mismatch, 404, **cả** status **và** `code` | `§0.48`: `jsonPath(...).value("X")` **cũng xanh** cho object node ⇒ assert **kiểu** trước |
| **5. `*IT` (Testcontainers)** | Thứ **chỉ** Postgres thật trả lời: JPQL, `CHECK`/`UNIQUE`, `@EntityGraph`, `@Version`, commit/rollback | Bài học `R7` đã lặp **bảy lần** (`§0.24`, `§0.27`, `§0.28`, `§0.40`, `§0.43`, `§0.45`, `§0.49`) |

> ⚠️ **Fixture `*IT` phải điền cả collection lồng nhau, không chỉ entity gốc.** Lỗi
> `MultipleBagFetchException` của `§0.45` sống sót lâu **không** vì thiếu `*IT` mà vì mọi `*IT` có `Shift`
> nhưng **không ca nào có break** ⇒ bag thứ hai chưa từng bị chạm.

### 4.2 Tầng thứ sáu — test chuẩn nghiệp vụ (business-standard scenario)

Không kiểm một method; kiểm **một câu hỏi của doanh nghiệp** có trả lời được không, bằng **con số
nghiệp vụ thật** (`R6`, không `isNotNull()`).

| Phase | Câu hỏi nghiệp vụ (nguồn PDF) | Hình thức |
|---|---|---|
| `P8` | *"Work order này đang ở công đoạn nào?"* (§10.6.5) | `*IT`: 3 operation, start op 20 ⇒ đọc lại thấy `10=COMPLETED, 20=IN_PROGRESS, 30=PENDING` |
| `P7` | *"Đang có bao nhiêu tiền trong xưởng, ở đâu?"* (§10.6.5) + *"nhập kho rồi WIP còn bao nhiêu?"* (§10.6.4-3) | `*IT`: xuất vật tư → báo sản lượng → nhập kho → assert `wipBalance` **bằng đúng** số tính tay từ standard cost |
| `P9` | *"Lô NVL này lỗi — những lô thành phẩm nào dính?"* (Rule 4) | `*IT`: 1 lô NVL → 2 WO → 2 lô FG; `where-used` liệt kê **đủ 2**; truy ngược từ 1 lô FG về **đúng** lô NVL |
| `P10` | *"Kế hoạch MRP vừa đề xuất có khả thi về năng lực?"* (§3.4) | Dữ liệu vượt năng lực ⇒ đề xuất mang `CAPACITY_OVERLOAD`, **và** verdict khớp Capacity Board (`C-7`) |
| `P11` | *"Đơn hàng thật có bị cộng dồn hai lần với forecast?"* (§10.6.2-1) | forecast 100, SO thật 60 ⇒ MRP thấy **100**, không phải 160 |

### 4.3 Nghiệm thu mutation — điều kiện đóng phase

Mỗi phase: **≥ 3 mutation**, **≥ 2 đụng `src/main`** (hoặc migration); mỗi mutation làm đỏ **đúng** tập
case dự đoán trước, rồi **revert**. Ba luật đã trả giá:

- 🔴 **Hai mã lỗi trong một mutation phải CÙNG HTTP status** — đổi 409↔422 là bài dễ, xanh cũng không
  nói gì (`§0.15`).
- 🔴 **Case đỏ vì `UnnecessaryStubbing` KHÔNG tính là kill** (`§0.22` #1, `§0.41` #2, `§0.44` #2).
- ⚠️ Mutation cũng để **bác bỏ khẳng định của chính mình**: `§0.45` mutation #2 chứng minh một dòng
  "phòng thủ" trong bản nháp là **thừa** (0 case đỏ) ⇒ đã gỡ. Một câu khẳng định trong javadoc phải qua
  mutation như một assertion.

### 4.4 Baseline

Baseline hiện tại: **1225 case unit / 158 class + 154 case IT / 22 class IT**, `failures = 0`,
`errors = 0` (`mvn -o clean verify` thật với Docker). Mỗi phase: số case **≥ baseline**; test cũ **sửa**
theo `R10` — không xoá, không nới assertion. Đo bằng dòng tổng `Tests run:`, **không** đếm file XML
trong `target/surefire-reports/` (bẫy `CLAUDE.md §0.1`).

---

# 5. `P8` — Shop Floor Control Cấp Công Đoạn

> **Đóng:** PDF §3.1 (Shop Floor Control), §10.6.5 (*"sản phẩm đang nằm ở công đoạn nào"*).
> **Là nợ của `P4`:** thiết kế `P4` ghi nguyên văn *"mỗi dòng status `PENDING/IN_PROGRESS/DONE`"* nhưng
> `work_order_operations` **không có cột `status`** — đã kiểm entity. Đây là lần thứ tư repo mắc lỗi
> "tuyên bố đóng rộng hơn phạm vi đã rà".

### 5.1 Hiện trạng đã kiểm

| Có | Thiếu |
|---|---|
| `work_order_operations`: `sequence`, `name`, `work_center_code`, `work_center_id` (FK, `V48`), `setup_minutes`, `run_minutes_per_unit`, `planned_start_at`/`planned_end_at`, `schedule_adjustment_reason` | **Không có `status`**, không có `started_at`/`completed_at` |
| `production_executions`: good/scrap/rework **theo từng operation**, `actual_started_at`/`actual_ended_at`, `operator_user_id` | Không có đường nói "công đoạn này đang chạy" khi **chưa** có sản lượng |

⇒ Dữ liệu *"đã xảy ra gì"* đủ; thiếu *"đang ở trạng thái gì"*.

### 5.2 Quyết định phải chốt với user trước khi code (`AskUserQuestion`)

Roadmap ghi rõ: *"endpoint start/complete, **hoặc** suy trạng thái tự động từ execution — chốt một
trong hai, đừng làm cả hai (hai nguồn sự thật cho cùng một trạng thái)"*.

| | **Phương án A — cột `status` + endpoint tường minh** ⭐ khuyến nghị | **Phương án B — suy ra ở read model, không cột** |
|---|---|---|
| Ghi | `POST /v1/work-orders/{id}/operations/{opId}/start` + `/complete`. **Chỉ** hai endpoint này ghi `status`. Báo sản lượng **không** đổi status | Không ghi gì |
| Đọc | Đọc cột | `PENDING` nếu chưa execution nào; `IN_PROGRESS` nếu có execution và Σgood < planned; `COMPLETED` nếu Σgood ≥ planned |
| Trả lời được *"công đoạn đang set-up, chưa ra sản phẩm"*? | ✅ có | ❌ **không** — đúng câu PDF hỏi |
| Rủi ro lệch dữ liệu | Có: operator quên bấm ⇒ status lệch thực tế | Không có (dẫn xuất) |
| Migration | Cần | Không |

**Khuyến nghị A**, vì câu hỏi PDF đặt ra (*"công đoạn 20 đang chạy, công đoạn 30 chưa bắt đầu"*) chính
là câu B **không** trả lời được. Rủi ro "quên bấm" là rủi ro vận hành, không phải lỗi thiết kế, và
`P7 §7.3` cũng cần một mốc thời gian bắt đầu công đoạn để phân bổ giá trị.

**Quyết định thứ hai cần chốt:** `start` một công đoạn khi công đoạn trước **chưa** `COMPLETED` —
**chặn** hay **cho phép**? Khuyến nghị **cho phép** (không chặn theo `sequence`), nhất quán với triết lý
tư vấn của `B90` (`ScheduleAdjustmentService` chỉ gắn cờ `sequenceConflict`, **không bao giờ** tự dời
hay chặn). Gối đầu công đoạn là chuyện bình thường ở xưởng thật; chặn cứng sẽ tạo ra một luồng mà
người dùng phải lách.

### 5.3 Schema — `V96__add_work_order_operation_status.sql`

```sql
ALTER TABLE work_order_operations
    ADD COLUMN status       VARCHAR(20),          -- nullable, KHÔNG backfill (C-3)
    ADD COLUMN started_at   TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE work_order_operations
    ADD CONSTRAINT chk_work_order_operations_status
        CHECK (status IS NULL OR status IN ('PENDING','IN_PROGRESS','COMPLETED')),
    -- Mốc thời gian chỉ tồn tại khi trạng thái tương ứng đã đạt tới (C-1).
    ADD CONSTRAINT chk_work_order_operations_started_at
        CHECK (started_at IS NULL OR status IN ('IN_PROGRESS','COMPLETED')),
    ADD CONSTRAINT chk_work_order_operations_completed_at
        CHECK (completed_at IS NULL OR status = 'COMPLETED'),
    ADD CONSTRAINT chk_work_order_operations_time_order
        CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at);

CREATE INDEX idx_work_order_operations_status ON work_order_operations(status);
```

🔴 **`status` nullable, KHÔNG `NOT NULL DEFAULT 'PENDING'`, và KHÔNG backfill** — đây là quyết định
thiết kế, không phải tiết kiệm công:

- Gán `PENDING` cho operation của một work order đã `CLOSED` năm ngoái là **nói dối**: công đoạn đó đã
  làm xong, chỉ là chưa ai theo dõi trạng thái.
- Gán `COMPLETED` cũng là **bịa**: không có bằng chứng nào nói nó xong (`actual_good` là số cấp work
  order, không cấp công đoạn).
- `NULL` = *"trước `P8` không ai theo dõi"* — câu trả lời đúng duy nhất. Đúng tiền lệ `B77` (`V40`),
  `V67` (không backfill `plant_id`), `V65`.
- API trả `NULL` thành **`UNTRACKED`**, không thành `PENDING` (`§5.5`). `start` trên một operation
  `NULL` **được phép** (coi như `PENDING`), để work order cũ vẫn dùng được tính năng mới.

Operation mới (sinh ở `WorkOrderService.snapshotRouting`) đặt `PENDING` **tường minh trong Java** —
không dựa `DEFAULT` của cột, để hai đường (Java mới / dòng lịch sử) phân biệt được nhau.

### 5.4 Domain & service

`WorkOrderOperation` thêm:

```java
public boolean canStart()     // status == null || PENDING
public boolean canComplete()  // status == IN_PROGRESS
public void start(Instant at)
public void complete(Instant at)
public WorkOrderOperationStatus trackedStatus()  // null-safe, dùng cho read model
```

`WorkOrderOperationStatusService` (`module/workorder/service/execution/`) — hai method:

| Method | Gate | Lỗi |
|---|---|---|
| `start(workOrderId, operationId)` | Work order phải `canExecute()` (tái dùng `WorkOrderExecutionSupport.ensureExecutable` — tự loại `CLOSED`/`CANCELLED`/`DRAFT`, `§0.34` #2); operation phải thuộc **đúng** work order đó (tái dùng khuôn `WipTransactionService.resolveOperation`); `canStart()` | `STATE_CONFLICT` (409) khi sai trạng thái · `RESOURCE_NOT_FOUND` (404) khi operation không thuộc work order |
| `complete(workOrderId, operationId)` | như trên + `canComplete()` | như trên |

- ⚠️ **Không** ép "đã có sản lượng mới cho complete" — một công đoạn có thể là kiểm tra/vận chuyển, không
  sinh good quantity. Ép sẽ khoá những routing hợp lệ.
- ⚠️ **Không** cho `COMPLETED → IN_PROGRESS` (reopen). Rework được báo bằng **số lượng**
  (`REWORK_REPORTED`), không bằng cách mở lại công đoạn — nhất quán với `CLOSED` của `B100`.
- `AuditAction` mới: `WORK_ORDER_OPERATION_STARTED`, `WORK_ORDER_OPERATION_COMPLETED` (`L8`).

### 5.5 API

| Endpoint | Permission (tái dùng) | Trả |
|---|---|---|
| `POST /v1/work-orders/{workOrderId}/operations/{operationId}/start` | `PERM_PRODUCTION_EXECUTION_MANAGE` | `WorkOrderOperationResponse` |
| `POST /v1/work-orders/{workOrderId}/operations/{operationId}/complete` | `PERM_PRODUCTION_EXECUTION_MANAGE` | `WorkOrderOperationResponse` |
| `GET /v1/work-orders/{workOrderId}` *(đã có)* | không đổi | `operations[]` thêm `status`/`startedAt`/`completedAt` |

- **Không permission mới**: start/complete là hành động của cùng người báo sản lượng.
- `status` trên wire: `PENDING` / `IN_PROGRESS` / `COMPLETED` / **`UNTRACKED`** (khi cột `NULL`).
  `UNTRACKED` là **giá trị wire**, không phải giá trị enum trong DB — nếu đưa vào DB thì `CHECK` ở `§5.3`
  phải đổi và dòng lịch sử lại bị backfill, đúng thứ `C-3` cấm.
- Additive ⇒ **không breaking change wire**. Positional Java: `WorkOrderOperationResponse` +3 component.

### 5.6 Bảo mật & chống rò rỉ

| Quy tắc | Áp dụng ở `P8` |
|---|---|
| `L1` | Response operation **không** mang tiền ⇒ không vướng |
| `L3` | Operation của work order khác ⇒ **404 `RESOURCE_NOT_FOUND`** (không phải 403) — đúng khuôn `resolveOperation` hiện có; và vì `@PreAuthorize` đã gác theo `workOrderId` **trước**, người gọi phải có quyền trên work order đó rồi, nên 404 ở đây không tiết lộ gì về work order khác |
| `L7` | Hai endpoint gọi service qua controller, **không** self-invocation |
| `L8` | 2 `AuditAction` mới |

### 5.7 Nhất quán dữ liệu

| Quy tắc | Áp dụng |
|---|---|
| `C-1` | 4 `CHECK` ở `§5.3` — mốc thời gian không tồn tại được nếu trạng thái chưa tới |
| `C-3` | `status` nullable, không backfill (`§5.3`) |
| `C-6` | `WorkOrderOperation` extends `BaseEntity` ⇒ có `@Version`. Hai request `start` đồng thời: một thắng, một nhận **409 `CONCURRENT_MODIFICATION`** — **không** ghi đè im lặng |
| `C-11` | `WorkOrderOperationStatus` là enum **mới** (không thêm giá trị vào enum cũ) ⇒ checklist §11.3 chỉ áp phần `CHECK constraint`. Vẫn phải `grep -rn "WorkOrderOperation" src/main` để rà `CapacityBoardService`/`ScheduleAdjustmentService` — 🔴 **kiểm xem Capacity Board có nên loại operation `COMPLETED` khỏi load hay không** (khuyến nghị: **không loại**, cùng lý do `LOAD_STATUSES` giữ `COMPLETED`/`CLOSED` — lịch đã chạy vẫn là load lịch sử thật) |

### 5.8 Bất biến mới

| # | Nội dung | Test bảo vệ |
|---|---|---|
| **`B124`** | Trạng thái công đoạn có **đúng một** người ghi: `WorkOrderOperationStatusService.start`/`complete`. Báo sản lượng (`ProductionExecutionService`) **không bao giờ** đổi `status`. `NULL` = "trước `P8`, không theo dõi", ra wire là `UNTRACKED`, **không** là `PENDING`; `start` trên `NULL` được phép. Không reopen `COMPLETED`. Không chặn theo `sequence` (tư vấn, đúng triết lý `B90`) | `WorkOrderOperationTest` (bảng chuyển trạng thái), `WorkOrderOperationStatusServiceTest`, `ProductionExecutionServiceTest.report_doesNotTouchOperationStatus`, `FlywayMigrationIT.migrate_v95_leavesHistoricalOperationsUntracked` |

### 5.9 Test plan

| Tầng | Case |
|---|---|
| **Domain** | `canStart` từ `null`/`PENDING` ✅, từ `IN_PROGRESS`/`COMPLETED` ❌ · `canComplete` chỉ từ `IN_PROGRESS` · `complete` set `completed_at` ≥ `started_at` |
| **Service** | Gate work order: `DRAFT`/`BLOCKED`/`CLOSED`/`CANCELLED` ⇒ `STATE_CONFLICT` + `verifyNoInteractions(repository)` (`R5`) · operation của WO khác ⇒ 404 · start hai lần ⇒ `STATE_CONFLICT` · **out-of-sequence start ⇒ 200** (pin quyết định `§5.2`) · audit action đúng |
| **Method-security** | deny + allow, `verify(workOrderPermissionGuard).hasWorkOrderAccess(any(), eq("PERM_PRODUCTION_EXECUTION_MANAGE"), eq(id))` (`R2`) |
| **Controller** | 200 envelope · 409 `code = STATE_CONFLICT` · 404 · 400 UUID sai |
| **`*IT`** | `FlywayMigrationIT.migrate_v95_*`: 3 `CHECK` (chèn `started_at` khi `status = 'PENDING'` ⇒ **DB từ chối**), và dòng lịch sử **vẫn `NULL`** sau migrate · `WorkOrderOperationRepositoryIT`: filter theo `status` trong JPQL |
| **Chuẩn nghiệp vụ** | `*IT`: work order 3 công đoạn → complete op 10 → start op 20 → `GET /work-orders/{id}` trả **đúng** `[COMPLETED, IN_PROGRESS, PENDING]` |

**Mutation dự kiến (4, ≥2 đụng `src/main`):**

| # | Mutation | Case đỏ kỳ vọng | Chứng minh |
|---|---|---|---|
| 1 | `canStart()` cho luôn `COMPLETED` (**`src/main`**) | `WorkOrderOperationTest` + `...startTwice_*` | Không reopen |
| 2 | `ensureExecutable` → không gate gì (**`src/main`**) | 4 case status work order | `CLOSED` không lọt qua bằng danh sách phủ định (bài học `§0.34` #1) |
| 3 | `V96` đổi `status` thành `NOT NULL DEFAULT 'PENDING'` (**migration**) | `migrate_v95_leavesHistoricalOperationsUntracked` | Quyết định "không backfill" là **test-backed** |
| 4 | `ProductionExecutionService.reportNew` set `status = IN_PROGRESS` (**`src/main`**) | `report_doesNotTouchOperationStatus` | Một nguồn sự thật duy nhất (`B124`) |

### 5.10 Definition of Done

Trả lời được *"work order này đang ở công đoạn nào"* qua `GET /v1/work-orders/{id}` · state machine có
test **cả nhánh vào lẫn nhánh ra** · dòng lịch sử vẫn `UNTRACKED` · `mvn -o clean verify` PASS, case
≥ baseline · nghiệm thu mutation 4/4 như bảng trên · cập nhật `module/workorder/CLAUDE.md` (`B124`),
`CLAUDE.md §0.x`, `MANUFACTURING_GAP_ROADMAP.md §2.1`, `docs/system-flow.md`.

**Breaking change wire:** không (thuần additive). **Positional Java:** `WorkOrderOperationResponse` +3.

---

# 6. `P7` — WIP Valuation, Cost Flow & Variance Snapshot

> **Đóng:** PDF §10.6.4-3 (*chuyển **giá trị** WIP → thành phẩm*), §10.6.5 (*giá trị WIP theo từng giai
> đoạn*), §10.6.7 (bảng `ProductionVariance`). **Khoảng trống lý thuyết lớn nhất còn lại.**
>
> **Không trùng `P3`:** `P3` trả lời *"lệnh này tốn bao nhiêu"*; `P7` trả lời *"đang có bao nhiêu tiền
> nằm trong xưởng, ở đâu, và khi nhập kho thì nó đi đâu"*.

### 6.1 Hiện trạng đã kiểm (xem `§0` để biết bằng chứng)

| Sự thật | Hệ quả |
|---|---|
| `work_order_cost_accumulators`: `UNIQUE (work_order_id)` + `CHECK >= 0` ×3, service *"never subtract"* | Một dòng **tổng** / work order; **chỉ tăng**. Không phải chỗ để trừ khi nhập kho (`F4`) |
| `wip_transactions`: **không cột tiền**; `CHECK quantity > 0`; `work_order_operation_id` **chỉ** được điền bởi execution | Không phải ledger giá trị, và không nhận được dòng âm (`F1`,`F2`) |
| `MaterialIssueService`: tiền theo **line**, dòng WIP theo **chứng từ** | Hai độ mịn (`F3`) |
| `ProductionReceiptService.approve`: sinh `RECEIVE` movement + lot, **không** bút toán giảm WIP | Giá trị vào WIP rồi **ở lại vĩnh viễn** |
| Không có `production_variances` | Sửa standard cost hôm nay **đổi variance của lệnh đã đóng năm ngoái** |
| `bom_lines` / `work_order_component_lines` / `MaterialIssueLineRequest`: **không** có tham chiếu operation | Vật tư **không quy được về công đoạn** (`§0.1` #2) |

### 6.2 Quyết định #1 — nơi chứa giá trị: bảng ledger RIÊNG

> Roadmap gợi ý *"thêm cột tiền vào `wip_transactions`"*. **Khuyến nghị: không.** Ba lý do đo được, cộng
> một lý do thiết kế:

1. `CHECK (quantity > 0)` chặn dòng giải phóng (`F2`) ⇒ phải mang dấu ở chỗ khác.
2. Dòng `MATERIAL_ISSUED` là **tổng của nhiều item** (`F3`) ⇒ một cột `amount` ở đó không bao giờ chia
   được theo item, mà vật tư là thành phần chi phí lớn nhất.
3. `work_order_operation_id` trên dòng đó **luôn `NULL`** (`F1`) ⇒ chiều công đoạn không có sẵn như
   roadmap tưởng.
4. Ledger số lượng và ledger giá trị có **vòng đời khác nhau**: một lần ghi nhận số lượng có thể sinh
   nhiều bút toán giá trị (material + labor + overhead). Nhồi chung tạo ra dòng mà `quantity` và
   `amount` nói về hai chuyện khác nhau.

**`wip_cost_entries`** — append-only, mang dấu bằng `direction`, đúng khuôn `stock_movements` (ledger
append-only đã có trong repo):

```sql
CREATE TABLE wip_cost_entries (
    wip_cost_entry_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id            UUID          NOT NULL REFERENCES work_orders(work_order_id),
    -- NULL = không quy được về công đoạn nào. Vật tư LUÔN NULL với mô hình hiện tại (§0.1 #2),
    -- trừ dòng rework có source_execution_id. KHÔNG bịa ra một operation để cột trông đầy.
    work_order_operation_id  UUID          REFERENCES work_order_operations(work_order_operation_id),
    cost_component           VARCHAR(20)   NOT NULL,   -- MATERIAL | LABOR | OVERHEAD
    direction                VARCHAR(3)    NOT NULL,   -- IN | OUT
    amount                   NUMERIC(19,6) NOT NULL,   -- luôn > 0; dấu nằm ở direction
    -- Truy vết: amount = quantity x unit_cost. Giữ cả ba để kiểm lại được mà không phải tra lịch sử
    -- standard cost (thứ repo này cố ý không lưu theo thời gian — quyết định P3).
    quantity                 NUMERIC(19,6),
    unit_cost                NUMERIC(19,6),
    item_id                  UUID          REFERENCES items(item_id),   -- chỉ MATERIAL
    reference_type           VARCHAR(80)   NOT NULL,   -- MATERIAL_ISSUE | PRODUCTION_EXECUTION | PRODUCTION_RECEIPT
    reference_id             VARCHAR(120)  NOT NULL,
    source_wip_transaction_id UUID         REFERENCES wip_transactions(wip_transaction_id),
    occurred_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID, version BIGINT DEFAULT 0,
    CONSTRAINT chk_wip_cost_entries_component CHECK (cost_component IN ('MATERIAL','LABOR','OVERHEAD')),
    CONSTRAINT chk_wip_cost_entries_direction CHECK (direction IN ('IN','OUT')),
    CONSTRAINT chk_wip_cost_entries_amount    CHECK (amount > 0),
    CONSTRAINT chk_wip_cost_entries_material_item
        CHECK (cost_component <> 'MATERIAL' OR item_id IS NOT NULL)
);
CREATE INDEX idx_wip_cost_entries_work_order ON wip_cost_entries(work_order_id);
CREATE INDEX idx_wip_cost_entries_operation  ON wip_cost_entries(work_order_operation_id);
CREATE INDEX idx_wip_cost_entries_reference  ON wip_cost_entries(reference_type, reference_id);
```

- 🔴 **Không** `UNIQUE` trên tập cột có `NULL` (`C-2`): `(reference_type, reference_id, cost_component,
  work_order_operation_id, item_id)` chứa hai cột nullable ⇒ Postgres coi hai `NULL` là khác nhau, ràng
  buộc **im lặng vô hiệu** đúng những dòng cần nó nhất. Chống trùng đã có ở **cấp chứng từ**:
  `material_issues` / `production_executions` / `production_receipts` đều có `idempotency_key` +
  `UNIQUE` (`A2`) ⇒ một chứng từ không thể post hai lần, nên bút toán của nó không thể sinh hai lần.
  Ghi điều này vào javadoc, đừng để người sau "thêm unique cho chắc".
- **Không** xoá/sửa `work_order_cost_accumulators` (`F4`, roadmap cũng cảnh báo): nó là nguồn của
  `costVariance.actual*` (`B94`) và `UNIQUE` là thứ giữ `upsert` đúng. Ledger mới **cộng thêm** chiều,
  không thay nó. Hai nguồn phải khớp nhau — có test (`§6.9`).

### 6.3 Quyết định #2 — chiều công đoạn đi được tới đâu (nói thật, không nói quá)

| Thành phần | Quy về công đoạn? | Vì sao |
|---|---|---|
| **LABOR** | ✅ có | `ProductionExecution.operation` đã có |
| **OVERHEAD** | ✅ có | như trên |
| **MATERIAL** | ❌ **không** (trừ rework) | `bom_lines` **không có** cột operation; `work_order_component_lines` cũng không; `MaterialIssueLineRequest` chỉ có `sourceExecutionId` **giới hạn cho rework** (`MaterialIssueService:497`) |

⇒ `GET /v1/work-orders/{id}/wip-valuation` trả **hai khối tách bạch**, không trộn:

```json
{
  "workOrderId": "...", "workOrderNo": "WO-1001", "status": "IN_PROGRESS",
  "wipBalance":        { "material": 500.00, "labor": 120.00, "overhead": 40.00, "total": 660.00 },
  "byOperation": [
    { "sequence": 10, "name": "Cắt",  "workCenterCode": "WC-CUT",
      "labor": 70.00, "overhead": 25.00, "total": 95.00 }
  ],
  "unattributed":     { "material": 500.00, "labor": 50.00, "overhead": 15.00, "total": 565.00 },
  "materialAttributableToOperations": false,
  "note": "MATERIAL_NOT_OPERATION_ATTRIBUTABLE"
}
```

🔴 `materialAttributableToOperations: false` + mã `note` là **phần của thiết kế**, không phải chú thích:
không có nó, `byOperation[].total` đọc như "toàn bộ tiền ở công đoạn này" trong khi nó chỉ là công +
overhead. Một con số một phần bị đọc như con số đầy đủ là sai tệ hơn không có số. Bù lại `unattributed`
làm đẳng thức **luôn đóng**: `Σ byOperation + unattributed = wipBalance` (có test, `§6.9`).

### 6.4 Quyết định #3 — phương pháp định giá lúc nhập kho (phải chốt với user)

Roadmap ghi rõ đây là chỗ *"phải chốt"*: *"standard cost × số lượng, **hay** actual WIP chia đều"*.

| | **(a) Standard cost × số lượng nhập** ⭐ khuyến nghị | **(b) Actual WIP ÷ good quantity** |
|---|---|---|
| Bút toán `OUT` | `standardUnitCost(FG) × receiptedQty` | `wipBalance ÷ goodQty × receiptedQty` |
| WIP còn lại sau khi đóng lệnh | **= variance** (chênh giữa tiêu thật và định mức) | ≈ 0 — variance **biến mất** vào giá trị thành phẩm |
| Phù hợp repo? | ✅ toàn bộ `P3` là standard costing; `ItemStandardCostLookupService.findStandardUnitCost` đã có và đã **fully-loaded** (`§0.31` #2) | ❌ cần một tầng actual-costing (FIFO/bình quân) **chưa tồn tại** — đúng lý do `P3` bỏ Material Price Variance |
| Ai đọc được variance? | Người xem báo cáo variance | **Không ai** — nó nằm im trong giá trị tồn kho |

**Khuyến nghị (a).** Nó làm cả hệ thống tự nhất quán: dư WIP sau `CLOSED` **chính là** variance, và đó
là con số `production_variances` chốt lại.

⚠️ **Hệ quả bắt buộc chấp nhận của (a): `wipBalance` có thể ÂM.** Sản xuất hiệu quả hơn định mức (tiêu
ít hơn, xuất kho theo standard) ⇒ `OUT > IN`. Vì vậy:
- **KHÔNG** thêm `CHECK` kiểu "WIP ≥ 0", **KHÔNG** kẹp `max(0, …)` ở mapper. Số âm là **credit
  variance** có ý nghĩa; kẹp nó là che một sự thật nghiệp vụ (cùng lý do `§0.43` #5 từ chối clamp
  availability).
- Có test riêng cho nhánh âm (`§6.9`) để người sau không "sửa cho đẹp".

### 6.5 Hook ghi bút toán

| Sự kiện | Ledger giá trị ghi gì | Ghi ở đâu |
|---|---|---|
| `MaterialIssueService.postNew` / `postFlat` — **mỗi component line** | `IN` / `MATERIAL` / `amount = qty × findStandardUnitCost(component)` / `item_id` = component / `operation = null` (hoặc operation của `sourceExecution` nếu là rework) | Ngay cạnh `costAccumulatorService.accumulateMaterialCost` (`:271`, `:449`) — **cùng transaction**, cùng vòng lặp, để hai nguồn không lệch nhau |
| `ProductionExecutionService.reportNew` | `IN` / `LABOR` + `IN` / `OVERHEAD`, `amount = goodQty × rate`, `operation` = operation của execution | Cạnh `accumulateLaborOverheadCost` |
| `ProductionReceiptService.approve` | `OUT` / **3 dòng** (MATERIAL/LABOR/OVERHEAD) theo tỷ lệ standard breakdown của FG × `receiptedQty`; `operation = null`; `source_wip_transaction_id` = dòng `OUTPUT_RECEIPTED` vừa ghi | Ngay sau `wipTransactionService.recordOutputReceipted` |

- 🔴 Dòng `OUT` bám **`OUTPUT_RECEIPTED`**, tuyệt đối không `OUTPUT_COMPLETED` — cái đầu là "đã nhập
  kho", cái sau là "xưởng làm ra" (`CLAUDE.md §0.5`). Bám sai là giải phóng giá trị cho hàng chưa vào kho.
- Tách `OUT` thành 3 dòng theo component (thay vì một dòng tổng) để `wipBalance` vẫn chia được theo
  material/labor/overhead sau khi nhập kho — nếu gộp, ba con số đầu ra sẽ không trừ được.
- QC `REJECTED` trên output **không lot** hiện rút hàng bằng `ADJUST_OUT` (`§0.13`). 🔴 **Chốt với
  user:** giá trị của hàng bị loại có ghi `OUT` (đưa ra khỏi WIP thành chi phí phế) hay giữ trong WIP?
  Khuyến nghị **giữ trong WIP** ở `P7` và ghi thành nợ mở — tách chi phí phế là một chủ đề riêng
  (scrap costing), không nằm trong ba ô PDF mà `P7` đóng.

### 6.6 `production_variances` — chốt variance lúc `CLOSED`

```sql
CREATE TABLE production_variances (
    production_variance_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id          UUID NOT NULL REFERENCES work_orders(work_order_id),
    -- Định mức tại thời điểm chốt. Lưu lại vì item_standard_costs KHÔNG có history (quyết định P3):
    -- không đóng băng thì sửa standard cost hôm nay làm đổi variance của lệnh đã đóng năm ngoái.
    standard_material_cost NUMERIC(19,6) NOT NULL,
    standard_labor_cost    NUMERIC(19,6) NOT NULL,
    standard_overhead_cost NUMERIC(19,6) NOT NULL,
    actual_material_cost   NUMERIC(19,6) NOT NULL,
    actual_labor_cost      NUMERIC(19,6) NOT NULL,
    actual_overhead_cost   NUMERIC(19,6) NOT NULL,
    planned_quantity       NUMERIC(19,6) NOT NULL,
    actual_good_quantity   NUMERIC(19,6) NOT NULL,
    scrap_quantity         NUMERIC(19,6) NOT NULL,
    rework_quantity        NUMERIC(19,6) NOT NULL,
    residual_wip_amount    NUMERIC(19,6) NOT NULL,   -- CÓ THỂ ÂM (§6.4)
    closed_at              TIMESTAMPTZ   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID, version BIGINT DEFAULT 0,
    CONSTRAINT uk_production_variances_work_order UNIQUE (work_order_id)
);
```

- **Ghi đúng một lần**, trong `WorkOrderService.close()` (`WorkOrderService.java:350`), **trước**
  `workOrder.close(...)` — cùng transaction, nên đóng lệnh và chốt variance **commit cùng nhau hoặc
  rollback cùng nhau** (không có trạng thái "đã đóng mà không có snapshot").
- 🔴 **Không** dùng `REQUIRES_NEW` ở đây, khác `MrpRunStateRecorder` (`C-5`): ở đó cần hàng sống sót
  **một transaction đang hỏng**; ở đây thì ngược lại — nếu snapshot lỗi thì việc đóng lệnh **phải**
  rollback theo, vì một lệnh `CLOSED` không có snapshot là đúng thứ bảng này sinh ra để chống.
- **Không** trigger append-only kiểu `V68`: bảng này **dẫn xuất được** từ hai ledger (khác audit log —
  bản duy nhất). `UNIQUE` + service ghi-một-lần + javadoc là đủ và **tương xứng**.
- `AuditAction.PRODUCTION_VARIANCE_SNAPSHOTTED` (`L8`).

### 6.7 Read API & lật cờ `authoritative` (đóng hợp đồng `F5`)

| Endpoint | Permission | Ghi chú |
|---|---|---|
| `GET /v1/work-orders/{id}/wip-valuation` | **`PERM_COSTING_READ`** (tái dùng) | Payload `§6.3` |
| `GET /v1/plants/{plantId}/wip-valuation` *(tuỳ chọn)* | `PERM_COSTING_READ` | Tổng theo work center/work order. Chỉ làm nếu user cần báo cáo cấp nhà máy — nếu không thì là code speculative (`§11.5`) |
| `GET /v1/work-orders/{id}/variance` *(đã có)* | không đổi | **Lật cờ**: work order `CLOSED` ⇒ đọc `production_variances`, trả `authoritative = true`, `authorityStatus = "SNAPSHOT_AT_CLOSE"`. Chưa `CLOSED` ⇒ vẫn tính live, giữ `false` / `"DEFERRED_NON_AUTHORITATIVE"` |

🔴 **Không permission mới** — tiền phải ở sau `PERM_COSTING_*` (`L1`), và `PERM_COSTING_READ` đã là
ADMIN+MANAGER (`V51`) nên OPERATOR **tự động** không thấy tiền WIP. Đây là lý do chọn tái dùng thay vì
tạo `PERM_WIP_VALUATION_READ`.

⚠️ `authorityStatus` là **string trên wire**, và FE đang đọc nó. Thêm giá trị mới ⇒ cập nhật
`docs/api-guide-for-frontend.md` **trong cùng commit** (`CLAUDE.md` "BẢN ĐỒ TÀI LIỆU" 🔴).

### 6.8 Bất biến mới

| # | Nội dung | Test bảo vệ |
|---|---|---|
| **`B125`** | Giá trị WIP nằm ở `wip_cost_entries`, **không** ở `wip_transactions`. Dấu mang bởi `direction` (`amount` luôn > 0). `Σ IN − Σ OUT` = tồn giá trị WIP, **có thể âm** (credit variance) — không `CHECK ≥ 0`, không kẹp ở mapper. Chống trùng dựa vào `idempotency_key` **cấp chứng từ**, **không** unique trên tập cột nullable (`C-2`) | `WipCostLedgerServiceTest`, `WipValuationServiceTest.negativeBalance_isReportedNotClamped`, `WipCostEntryRepositoryIT` |
| **`B126`** | MATERIAL **không** quy về công đoạn (mô hình hiện tại không có link BOM↔operation); LABOR/OVERHEAD thì có. Response **phải** mang `materialAttributableToOperations = false` + khối `unattributed`, và đẳng thức `Σ byOperation + unattributed = wipBalance` **luôn đóng** | `WipValuationServiceTest.byOperationPlusUnattributed_equalsTheBalance`, `...materialNeverCarriesAnOperation` |
| **`B127`** | Giải phóng WIP bám **`OUTPUT_RECEIPTED`** (đã nhập kho), **không** `OUTPUT_COMPLETED`; định giá bằng **standard cost của FG × số lượng nhập** (`§6.4` (a)). `work_order_cost_accumulators` **không bao giờ** bị trừ (`F4`) | `ProductionReceiptServiceTest.approve_releasesWipAtStandardCost`, `...accumulatorIsNeverDecremented` |
| **`B128`** | `production_variances` ghi **đúng một lần**, trong **cùng transaction** với `close()`; snapshot **tự mang** định mức tại thời điểm chốt. `CLOSED` ⇒ `GET .../variance` đọc snapshot + `authoritative = true`; chưa `CLOSED` ⇒ live + `false` | `WorkOrderServiceTest.close_snapshotsVarianceInTheSameTransaction`, `WorkOrderVarianceServiceTest.closedWorkOrder_readsTheSnapshotAndIsAuthoritative`, `ProductionVarianceRepositoryIT` |

### 6.9 Test plan

| Tầng | Case |
|---|---|
| **Domain/unit công thức** | `amount = qty × unitCost` đúng scale · `Σ IN − Σ OUT` với dữ liệu hỗn hợp · nhánh **âm** |
| **Service** | 3 hook ghi đúng `cost_component`/`direction`/`operation` (`R4`, assert **đối số**) · MATERIAL luôn `operation == null` · `approve` ghi 3 dòng `OUT` bám `OUTPUT_RECEIPTED` · `close()` ghi snapshot rồi mới `close` · close hai lần ⇒ `STATE_CONFLICT` + **không** snapshot thứ hai (`R5`) · accumulator **không** bị trừ |
| **Đối chiếu hai nguồn (`C-7`)** | `Σ IN` của ledger **bằng** `work_order_cost_accumulators` sau cùng một chuỗi nghiệp vụ — khoá chặt hai nguồn không trôi lệch |
| **Method-security** | `wip-valuation` deny+allow, `verify(guard)` pin **`PERM_COSTING_READ`** · 🔴 case riêng: principal có `PERM_WORK_ORDER_READ` nhưng **không** `PERM_COSTING_READ` ⇒ **403** (đúng `L1`) |
| **Controller** | envelope · 404 · 400 · `authoritative` là **boolean** (`isBoolean()`, bài học `§0.48`) |
| **`*IT`** | `WipCostEntryRepositoryIT`: aggregate group-by operation trên Postgres thật (`R7`) + 4 `CHECK` · `ProductionVarianceRepositoryIT`: `UNIQUE` chặn snapshot thứ hai · `FlywayMigrationIT.migrate_v96_*` |
| **Chuẩn nghiệp vụ** | `*IT` end-to-end: xuất 2 component → báo sản lượng → nhập kho một phần → assert `wipBalance` **bằng đúng** số tính tay; đóng lệnh → `production_variances` có **đúng** `residual_wip_amount`; sửa `item_standard_costs` **sau khi** đóng → variance của lệnh đã đóng **không đổi** (🔴 đây là bài kiểm chứng chính của cả phase) |

**Mutation dự kiến (5, ≥3 đụng `src/main`):**

| # | Mutation | Case đỏ kỳ vọng | Chứng minh |
|---|---|---|---|
| 1 | `OUT` bám `OUTPUT_COMPLETED` thay `OUTPUT_RECEIPTED` (**`src/main`**) | `approve_releasesWipAtStandardCost` + case nghiệp vụ | `B127`, bẫy roadmap đã cảnh báo |
| 2 | Kẹp `wipBalance` về `max(0, …)` (**`src/main`**) | `negativeBalance_isReportedNotClamped` | `B125` — credit variance không bị che |
| 3 | `close()` không ghi snapshot (**`src/main`**) | `close_snapshotsVariance*` + `closedWorkOrder_readsTheSnapshot*` | `B128` |
| 4 | Snapshot đọc `item_standard_costs` **lúc đọc** thay vì lưu định mức (**`src/main`**) | case "sửa standard cost sau khi đóng" | 🔴 mutation **giá trị nhất**: bản nháp rất dễ mắc, và **chỉ** case nghiệp vụ bắt được |
| 5 | Bỏ `chk_wip_cost_entries_material_item` khỏi `V97` (**migration**) | `FlywayMigrationIT.migrate_v96_*` | `C-1` — bảo đảm ở DB, không ở `if` |

### 6.10 Definition of Done

Tồn giá trị WIP đọc được **theo work order** và **theo công đoạn** (với giới hạn `B126` ghi rõ trên
response) · nhập kho thành phẩm làm **giảm đúng** giá trị WIP · work order `CLOSED` có dòng
`production_variances` bất biến và `GET .../variance` trả `authoritative = true` · `Σ IN` khớp
accumulator · `mvn -o clean verify` PASS, case ≥ baseline · mutation 5/5 · cập nhật
`module/workorder/CLAUDE.md` (`B125`–`B128`), `module/costing/CLAUDE.md`,
**`docs/api-guide-for-frontend.md`** (`authorityStatus` mới + endpoint mới), `CLAUDE.md §0.x`,
`MANUFACTURING_GAP_ROADMAP.md §2.0`+`§2.1`.

**Breaking change wire:** không (endpoint mới + 2 field đổi **giá trị** chứ không đổi kiểu; `authoritative`
chuyển `false → true` cho work order `CLOSED` là **đúng** thứ hợp đồng cũ hứa sẽ xảy ra).
**Positional Java:** `WorkOrderVarianceService` +1..2 constructor param; `ProductionReceiptService`,
`MaterialIssueService`, `ProductionExecutionService`, `WorkOrderService` mỗi cái +1.

---

# 7. `P9` — Traceability Toàn Chuỗi (Genealogy)

> **Đóng:** PDF §3.5, Rule 4 (*"bắt buộc Lot/Batch/Serial + traceability"*).
> **Hệ quả nghiệp vụ đang thiếu:** một lô NVL bị phát hiện lỗi mà hệ thống **không liệt kê được** những
> lô thành phẩm đã dùng nó ⇒ không thu hồi có mục tiêu được.

### 7.1 Hiện trạng đã kiểm

**Dữ liệu đủ; đường đọc không có.** Đã xác nhận trên entity:
`material_issue_lines` có `lot_id` **và** `serial_id` (+ `source_execution_id` cho rework);
`production_receipt_lines` có `lot_id` **và** `serial_id`; `stock_movements` có `lot_id`; `work_order_id`
nối hai đầu. **Không service/endpoint nào đi theo đường đó.**

⚠️ `grep genealogy|traceability` chỉ ra **`traceId`** — id correlate **log của request**, hoàn toàn
không liên quan (`error-handling.md §5.1` có bảng phân biệt hai thứ cùng tên). Đừng đọc nhầm.

### 7.2 Hai chiều truy vết

| Chiều | Endpoint | Đường đi |
|---|---|---|
| **Truy ngược** (lô FG → NVL) | `GET /v1/inventory/lots/{lotId}/genealogy` | `production_receipt_lines(lot)` → `work_order` → `material_issue_lines(work_order)` → lô/serial NVL → **đệ quy** nếu NVL đó cũng do sản xuất ra |
| **Truy xuôi** (thu hồi) | `GET /v1/inventory/lots/{lotId}/where-used` | `material_issue_lines(lot)` → `work_order` → `production_receipt_lines(work_order)` → lô FG → **đệ quy** xuống nếu lô FG đó lại bị tiêu ở lệnh khác |

### 7.3 Thuật toán — BFS theo cấp, 1 aggregate query mỗi cấp

```
frontier = { lotId gốc }
visited  = LinkedHashSet { lotId gốc }        // cycle guard, như CostingService
depth    = 0
while frontier không rỗng và depth < MAX_DEPTH:
    workOrderIds = <1 query> lookup.findWorkOrderIdsFor(frontier)        // lọc theo companyId đã ghim
    nextLots     = <1 query> lookup.findLotIdsFor(workOrderIds)          // lọc theo companyId đã ghim
    nextLots -= visited
    ghi node + edge của cấp này
    frontier = nextLots; visited += nextLots; depth++
if frontier không rỗng:  truncated = true      // hết trần, còn dữ liệu
```

- **2 query / cấp**, không query theo dòng (`C-8`/`C14`).
- **Cycle guard bắt buộc**: lô A → WO1 → lô B → WO2 → lô A là **có thể** trong dữ liệu thật (rework,
  tái chế). Không guard ⇒ vòng lặp vô hạn. `CostingService` đã có tiền lệ `LinkedHashSet<UUID>`.
- **Trần độ sâu** (`MAX_DEPTH`, đề xuất **10**, hằng số `@ConfigurationProperties`-able chỉ khi user
  yêu cầu — mặc định là `static final`, `§11.5`): chặn payload vô hạn **và** là một biện pháp chống rò
  rỉ (`L4`).
- Cross-module theo `C7`: `module/inventory` gọi **lookup service mới** trong `module/workorder`, không
  chạm repository. Tiền lệ đúng: `LotQcOriginLookupService` (`C2-2`, cùng hướng `inventory → workorder`).

`LotGenealogyLookupService` (`module/workorder/service/query/`) — 4 method batch:

```java
Map<UUID, Set<UUID>> findProducingWorkOrderIdsByLotIds(UUID companyId, Collection<UUID> lotIds);
Map<UUID, Set<UUID>> findConsumedLotIdsByWorkOrderIds(UUID companyId, Collection<UUID> workOrderIds);
Map<UUID, Set<UUID>> findConsumingWorkOrderIdsByLotIds(UUID companyId, Collection<UUID> lotIds);
Map<UUID, Set<UUID>> findProducedLotIdsByWorkOrderIds(UUID companyId, Collection<UUID> workOrderIds);
```

🔴 **`companyId` là tham số BẮT BUỘC của cả 4**, không phải tuỳ chọn — xem `§7.4`.

### 7.4 Bảo mật & chống rò rỉ — phần quan trọng nhất của `P9`

> Đây là endpoint **rủi ro rò rỉ cao nhất** trong cả 5 phase: nó **cố tình** đi ra khỏi bản ghi mà
> người dùng yêu cầu.

| Quy tắc | Thiết kế cụ thể |
|---|---|
| **Điểm vào** | `@PreAuthorize("@inventoryPermissionGuard.hasLotCompanyAccess(authentication, 'PERM_INVENTORY_READ', #lotId)")` — **tái dùng**, không permission mới |
| **`L2` — ghim company** | Lấy `companyId` từ lot gốc (`lot.getItem().getCompany()`), truyền vào **mọi** query của **mọi** cấp. Mỗi JPQL có mệnh đề `company_id = :companyId` tường minh. 🔴 **Không** dựa vào "BOM/WO vốn cùng company" — đó là một giả định, và `L2` tồn tại vì một giả định không phải một biện pháp |
| **`L1` — không tiền** | Node genealogy mang: lot code, item code/name, quantity, `receivedAt`, work order no, warehouse code. **Không** `unitCost`, **không** `amount`, **không** variance. Lý do cứng, đã kiểm: `PERM_INVENTORY_READ` **có** cấp cho OPERATOR (`V41:56`; `V9` chỉ tạo permission và cấp cho ADMIN), còn `PERM_COSTING_READ` thì **không** (`V51`, ADMIN+MANAGER) — nhồi tiền vào đây là cấp cho OPERATOR một đường đọc giá thành |
| **`L3` — không liệt kê được** | Lot không tồn tại **và** lot công ty khác đều ⇒ guard `false` ⇒ **403**. Đã đúng sẵn trong `hasLotCompanyAccess` (`orElse(false)`); `P9` **thêm test giữ** tính chất này, vì nó dễ bị "cải thiện" thành 404 cho dễ debug |
| **`L4` — cắt thì báo có cắt** | `truncated: true` + `boundaryOmittedCount: N`. **Không** trả id/mã của node bị loại. Áp cho **cả hai** nguyên nhân cắt: hết `MAX_DEPTH`, và node rơi ngoài company |
| **`L8`** | Read endpoint ⇒ không cần audit action mới (repo không audit read; `AuditAction` chỉ cho hành động ghi) |

### 7.5 Nối serial vào Goods Receipt — nợ còn lại của `P5`

🔴 **Sửa `F6` trước khi viết test.** `module/purchasing/CLAUDE.md:29` ghi đường này *"nổ `SERIAL_REQUIRED`
(400)"* — **sai**: thực tế là `OPERATION_NOT_ALLOWED` (422) từ `requireSerialCode`
(`InventoryMovementService:540`). Hai việc phải làm, đúng thứ tự:

1. **Sửa tài liệu** cho khớp hiện trạng (một dòng ở `module/purchasing/CLAUDE.md`) — nếu không, test mới
   sẽ pin một mã chưa bao giờ được ném ở đó.
2. **Rồi mới** implement, và **lúc đó** `SERIAL_REQUIRED` (400) trở thành mã **thật** của đường PO cho
   trường hợp "thiếu/không đủ serial", tức tài liệu thành đúng vì code đổi, không vì ai sửa chữ.

Thiết kế:

| | Nội dung |
|---|---|
| Request | `GoodsReceiptLineRequest` + `List<String> serialNumbers` (**additive**, nullable) |
| Validate | Item `serialTracked` ⇒ `serialNumbers.size()` **phải bằng** `receivedQuantity` (số nguyên) ⇒ lệch thì **`SERIAL_REQUIRED` (400)**. Item **không** serial-tracked mà gửi list ⇒ `OPERATION_NOT_ALLOWED` (422), đúng khuôn `ensureNoSerialProvided` |
| Thực thi | `receivedQuantity` = N ⇒ **N lần** `movementService.receive(...)`, mỗi lần `quantity = 1` + một serial (`B97`). Child idempotency key `<parent>:L<n>:S<m>` — mở rộng khuôn `<parent>:L<n>` đã có, và `:qc-reject:L<n>` của `§0.13` |
| Nhất quán | 🔴 N lần receive phải **cùng một transaction** với goods receipt: nhận 3/5 serial rồi lỗi ⇒ **rollback cả phiếu**, không để phiếu nhận một phần với số lượng không khớp `stock_balances` |
| Giới hạn | Serial ⇒ `receivedQuantity` phải là **số nguyên** (0.5 đơn vị serial không có nghĩa) ⇒ thêm check, `OPERATION_NOT_ALLOWED` |

### 7.6 Bất biến mới

| # | Nội dung | Test bảo vệ |
|---|---|---|
| **`B129`** | Genealogy đi qua `LotGenealogyLookupService` (`C7`), **2 aggregate query mỗi cấp**, cycle guard + `MAX_DEPTH`. **`companyId` của lot gốc được ghim và áp cho mọi cấp**; node ngoài company bị loại và **chỉ** báo bằng `boundaryOmittedCount`, không nêu danh. Node **không bao giờ** mang field tiền | `LotGenealogyServiceTest.cycle_terminates`, `...pinsTheEntryCompanyOnEveryLevel`, `...nodeCarriesNoCostField`, `LotGenealogyLookupServiceIT` |
| **`B130`** | Lot không tồn tại và lot ngoài quyền **cùng** trả 403 (không liệt kê được) | `InventoryLotMethodSecurityTest.genealogy_unknownLot_isIndistinguishableFromForbidden` |
| **`B131`** | Goods receipt item serial-tracked: `serialNumbers.size() == receivedQuantity` (số nguyên), N movement `quantity = 1`, child key `<parent>:L<n>:S<m>`, **cùng một transaction** — nhận một phần rồi lỗi ⇒ rollback cả phiếu | `GoodsReceiptServiceTest.serialTracked_*` (4 nhánh), `GoodsReceiptRepositoryIT.partialSerialFailure_rollsBackTheWholeReceipt` |

### 7.7 Test plan

| Tầng | Case |
|---|---|
| **Service** | Cycle A→B→A **kết thúc** · `MAX_DEPTH` ⇒ `truncated = true` · node ngoài company bị loại và **đếm** vào `boundaryOmittedCount` (không lộ id) · số query **đúng 2/cấp** (`verify(lookup, times(...))`, `C15`) · node DTO **không có** field tiền (assert trên **record components**, không chỉ trên một payload mẫu) |
| **Method-security** | deny+allow `verify(guard)` pin `PERM_INVENTORY_READ` · lot lạ ⇒ 403 (không 404) |
| **Controller** | envelope · 403 · 400 UUID sai · lot không có genealogy ⇒ **200 + cây một node**, không 404 (một lô mua ngoài là dữ liệu hợp lệ, không phải lỗi) |
| **`*IT`** | 🔴 Bắt buộc (roadmap ghi rõ, `R7`): JPQL nhiều cấp trên Postgres thật. Fixture **có chiều sâu ≥ 2** và **có nhánh rẽ** (1 lô NVL → 2 WO → 2 lô FG) — fixture phẳng không chạm được đường đệ quy (bài học `§0.45`: dữ liệu test quá sạch) · **case cross-company**: chèn dữ liệu công ty B rồi chạy từ lot công ty A, assert B **không** xuất hiện |
| **Chuẩn nghiệp vụ** | `*IT`: từ **một** lô NVL, `where-used` liệt kê **đủ 2** lô FG; từ **một** lô FG, `genealogy` trả **đúng** lô NVL cấp 1 và cấp 2; goods receipt nhận được item serial-tracked |

**Mutation dự kiến (4, ≥3 đụng `src/main`):**

| # | Mutation | Case đỏ kỳ vọng | Chứng minh |
|---|---|---|---|
| 1 | Bỏ `company_id = :companyId` khỏi **một** query cấp sâu (**`src/main`**) | case cross-company IT | 🔴 mutation **quan trọng nhất**: chứng minh `L2` được ép ở **mọi** cấp, không chỉ cấp đầu |
| 2 | Bỏ cycle guard (**`src/main`**) | `cycle_terminates` (treo/`StackOverflow`) | `C-8` |
| 3 | `hasLotCompanyAccess` đổi `orElse(false)` → ném 404 khi lot lạ (**`src/main`**) | `..._isIndistinguishableFromForbidden` | `L3` là **test-backed**, không phải may mắn |
| 4 | Goods receipt: chấp nhận `serialNumbers.size() < receivedQuantity` (**`src/main`**) | `serialTracked_shortList_*` | `B131` |

### 7.8 Definition of Done

Từ một lô FG liệt kê đủ lô NVL cấp 1 **và** cấp sâu hơn · từ một lô NVL liệt kê đủ lô FG dính · goods
receipt nhận được item serial-tracked · `*IT` trên Postgres thật, **có** case cross-company ·
`mvn -o clean verify` PASS, case ≥ baseline · mutation 4/4 · cập nhật
`module/inventory/CLAUDE.md` (`B129`–`B130`), `module/workorder/CLAUDE.md` (entry point mới),
**`module/purchasing/CLAUDE.md`** (`B131` + **sửa `F6`**), `docs/api-guide-for-frontend.md`.

**Breaking change wire:** không (2 endpoint mới + 1 field additive trên goods receipt line).

---

# 8. `P10` — CRP Đóng Vòng Vào MRP Run (MRP II Closed-Loop)

> **Đóng:** PDF §3.1 (MRP I/II, Capacity Planning), §3.4 (MRP + CRP).
> **Khoảng trống:** CRP tĩnh **có** nhưng là một **read riêng**; một lượt MRP **không bao giờ** nói cho
> người lập kế hoạch biết kế hoạch nó vừa đề xuất là bất khả thi về năng lực.

### 8.1 Hiện trạng đã kiểm

| Có | Vấn đề với việc tái dùng |
|---|---|
| `CapacityBoardService.getBoard` + `ScheduleAdjustmentService` (`C2-8`) | `getBoard` bị `@PreAuthorize(PERM_CAPACITY_READ)` và **keyed vào `WorkOrderOperation`** (`F7`) |
| `dayCapacityMinutes`, `utilizationPercent`, `attributedDay`, `key` — **`public static`**, không guard | ✅ **đây là phần tái dùng được** |
| `buildDayContext(plantId, from, to, List<WorkOrderOperation>)` — `public`, **không** `@PreAuthorize` | Nhận `List<WorkOrderOperation>` chỉ để biết **work center nào** cần nạp calendar ⇒ cần overload keyed theo work center |
| `workOrderOperationRepository.aggregateExistingLoad(plantId, from, to, LOAD_STATUSES)` | ✅ đúng **mẫu số** cần dùng |
| `RoutingLookupService.findActiveRoutingSummaries` | ❌ trả `(routingId, code, version)` — **không** phút (`F8`) |

🔴 **Vì sao không gọi `getBoard`:** một đề xuất MRP **chưa có** work order ⇒ **không có** dòng
`WorkOrderOperation` nào. Và cross-bean call **đi qua** proxy Spring ⇒ `@PreAuthorize` **có** hiệu lực ⇒
planner chỉ có `PERM_MRP_RUN` sẽ nhận **403 giữa lượt chạy MRP**.

### 8.2 Quyết định phải chốt với user

**`WARNING` hay `BLOCKED`?** Khuyến nghị **`WARNING`** (roadmap cũng khuyến nghị vậy), vì:
- `BLOCKED` hiện mang nghĩa **"thiếu master data"** (`MISSING_BOM`/`MISSING_ROUTING`) và `isBlocked()`
  **chặn convert** (`SupplySuggestionService`). Vượt năng lực là *"đủ dữ liệu nhưng xưởng không kham
  nổi"* — khác hẳn về bản chất.
- Chặn convert sẽ để planner **không có đường thoát**: không có cơ chế override, và cách duy nhất là
  sửa work calendar — một việc thuộc người khác.
- ⚠️ Nếu user chọn `BLOCKED`: phải thiết kế thêm đường override (permission hoặc lý do bắt buộc), nếu
  không sẽ tạo ra một trạng thái người dùng phải lách bằng cách sửa dữ liệu master.

### 8.3 Thiết kế

**(1) Lookup phút — đóng `F8`.** `RoutingLookupService` thêm **một** query batch:

```java
/** Một query cho cả lượt chạy (C14), không phải một query mỗi item. */
Map<UUID, List<RoutingOperationLoad>> findActiveRoutingLoadByItemIds(UUID companyId, Collection<UUID> itemIds);
public record RoutingOperationLoad(UUID workCenterId, BigDecimal setupMinutes, BigDecimal runMinutesPerUnit) {}
```

**(2) Ngữ cảnh năng lực cho *đề xuất* — đóng `F7`.** `CapacityBoardService` thêm **overload không
guard**, cùng thân với cái đang có:

```java
public DayContext buildDayContextForWorkCenters(UUID plantId, LocalDate from, LocalDate to,
                                                Collection<WorkCenter> workCenters);
```

Cái hiện tại (`List<WorkOrderOperation>`) chỉ dùng operation để **suy ra tập work center** ⇒ tách phần
đó ra, hai điểm vào **cùng một** thân ⇒ `C-7` (một con số, một công thức) được giữ **bằng cấu trúc**,
không bằng kỷ luật.

**(3) Đánh giá, trong `module/planning`.** `CapacityFeasibilityService`:

```
cho mỗi đề xuất MAKE (không BLOCKED):
    load  = Σ over operations( setup + run × suggestedQuantity )      // theo work center
    day   = suggestedOrderDate  (ngày local theo Plant.timezone)
    cap   = CapacityBoardService.dayCapacityMinutes(context, wc, day)
    if cap != null && existingLoad(wc, day) + load > cap:
        gắn PlanningMessageCode.CAPACITY_OVERLOAD, exceptionState = max(hiện tại, WARNING)
```

- 🔴 `cap == null` (work center **không** có work calendar) ⇒ **không kết luận gì**. `null` nghĩa là
  "không biết", **không** phải "không có năng lực" — đúng quy ước `C2-8` (`dayCapacityMinutes` trả `null`
  và `overload` khi đó luôn `false`). Kết luận quá tải từ `null` là bịa.
- **Ngày quy thuộc** phải dùng **cùng** phép quy đổi timezone với `attributedDay` (`Plant.timezone`),
  nếu không board và MRP lệch ngày nhau ở múi giờ âm (`C2-8` đã có `WorkOrderOperationRepositoryIT` canh
  đúng chuyện này).
- `exceptionState` chỉ được **nâng**, không hạ: một đề xuất đang `BLOCKED` vì thiếu BOM **không** được
  hạ xuống `WARNING` vì lý do năng lực.
- `PlanningMessageCode.CAPACITY_OVERLOAD` mới — `F10`: `message_codes` còn đủ chỗ (138 → 156 / 200).
  **Có test đo độ dài** khi gắn đủ mã, để mã thứ 9 không làm `INSERT` chết im lặng.
- Chạy **trong** transaction của lượt run, **trước** `persistSuggestions` ⇒ run là một snapshot nhất
  quán (`C-5`: không cần `REQUIRES_NEW` ở đây — nếu đánh giá lỗi thì cả lượt run nên lỗi, khác
  `MrpRunStateRecorder`).

### 8.4 Bảo mật & chống rò rỉ

| Quy tắc | Thiết kế |
|---|---|
| **`L5`** 🔴 | Đề xuất mang **chỉ** `CAPACITY_OVERLOAD` — **không** `utilizationPercent`, **không** `capacityMinutes`, **không** `loadMinutes`. Muốn số thì mở Capacity Board với `PERM_CAPACITY_READ`. Nếu không, `PERM_MRP_RUN` thành **cửa sau đọc năng lực nhà máy** |
| **`L7`** | Gọi **phần static + overload không guard**, **không** gọi `getBoard` (`F7`) ⇒ tránh cả 403 oan **và** tránh cám dỗ bỏ `@PreAuthorize` khỏi `getBoard` (thứ sẽ mở board cho mọi người) |
| Permission | **Không mới.** `POST /v1/planning-runs` vẫn `PERM_MRP_RUN`; Capacity Board vẫn `PERM_CAPACITY_READ` |

### 8.5 Bất biến mới

| # | Nội dung | Test bảo vệ |
|---|---|---|
| **`B132`** | Lượt MRP đánh giá năng lực **trong cùng transaction**, **trước** khi persist đề xuất; vượt năng lực ⇒ `CAPACITY_OVERLOAD` + `exceptionState` nâng lên **`WARNING`** (không `BLOCKED` — vẫn convert được). `exceptionState` chỉ **nâng**, không hạ. `dayCapacityMinutes == null` ⇒ **không** kết luận quá tải | `CapacityFeasibilityServiceTest` (4 nhánh), `MrpRunServiceTest.run_overloadedProposal_carriesTheWarning` |
| **`B133`** | Verdict quá tải của MRP và của Capacity Board đến từ **một** công thức (`CapacityBoardService` static + overload dùng chung thân). Đề xuất **không bao giờ** mang số liệu năng lực thô (`L5`) | `CapacityFeasibilityServiceTest.verdictMatchesTheCapacityBoard`, `SupplySuggestionResponseTest.carriesNoCapacityFigures` |

### 8.6 Test plan

| Tầng | Case |
|---|---|
| **Service** | Đủ năng lực ⇒ `READY`, **không** message · vượt ⇒ `WARNING` + `CAPACITY_OVERLOAD` · đang `BLOCKED` (thiếu BOM) + vượt năng lực ⇒ **vẫn `BLOCKED`** (không hạ) · `cap == null` ⇒ **không** message · số query: **1** lookup routing cho cả lượt (`C15`) |
| **Đối chiếu (`C-7`)** | Cùng dữ liệu ⇒ verdict của `CapacityFeasibilityService` **bằng** cờ `overload` của `CapacityBoardService` |
| **Chống rò rỉ** | `SupplySuggestionResponse` **không có** component nào tên `utilization*`/`capacity*`/`loadMinutes` (assert trên **record components**) |
| **Controller** | `GET /v1/supply-suggestions` trả `messages[]` chứa `CAPACITY_OVERLOAD`; không có field số năng lực |
| **`*IT`** | JPQL load mới trên Postgres thật (`R7`) · **độ dài `message_codes`**: đề xuất gắn đủ mã ⇒ INSERT **không** vượt 200 (`F10`) · quy đổi timezone khớp board (mượn khuôn `WorkOrderOperationRepositoryIT`, dùng `America/New_York` tháng 1 để ngày UTC ≠ ngày local) |
| **Chuẩn nghiệp vụ** | Dữ liệu vượt năng lực thật (1 work center, calendar 1 ca, 2 đề xuất lớn cùng ngày) ⇒ đề xuất mang cảnh báo, **và** Capacity Board báo `overload = true` cho **cùng** (work center, ngày) |

**Mutation dự kiến (4, ≥3 đụng `src/main`):**

| # | Mutation | Case đỏ kỳ vọng | Chứng minh |
|---|---|---|---|
| 1 | `cap == null` ⇒ coi như `0` (**`src/main`**) | `..._nullCapacity_doesNotConclude` | "Không biết" ≠ "không có năng lực" |
| 2 | `exceptionState` được **ghi đè** thành `WARNING` (**`src/main`**) | `..._blockedStaysBlocked` | Không hạ cấp `BLOCKED` |
| 3 | Đánh giá **sau** `persistSuggestions` (**`src/main`**) | `run_overloadedProposal_carriesTheWarning` | Đề xuất persist **không** mang cảnh báo ⇒ tính năng thành no-op mà HTTP vẫn 201 |
| 4 | Ngày quy thuộc dùng UTC thay `Plant.timezone` (**`src/main`**) | case timezone IT | Board và MRP không lệch ngày |

### 8.7 Definition of Done

Lượt MRP trên dữ liệu vượt năng lực trả đề xuất **có cảnh báo**; verdict **khớp** Capacity Board; đề xuất
**không** mang số liệu năng lực thô; `mvn -o clean verify` PASS, case ≥ baseline; mutation 4/4; cập nhật
`module/planning/CLAUDE.md` (`B132`–`B133`), `module/workorder/CLAUDE.md` (overload mới),
`module/routing/CLAUDE.md` (lookup mới), `docs/api-guide-for-frontend.md` (mã message mới).

**Breaking change wire:** không (một giá trị mới trong `messages[]` — mảng vốn đã mở).
**Positional Java:** `MrpRunService` / `MrpCalculationService` +1 constructor param.

---

# 9. `P11` — MPS (Master Production Schedule)

> **Đóng:** PDF §10.6.2-1 (*"Tạo Work Order từ MRP/**MPS**"*).
> **Vì sao xếp cuối:** nhu cầu nhập tay (`MANUAL`) đã phủ phần lớn tác dụng thực tế của MPS trong sản
> xuất theo đơn hàng ⇒ đây là **tinh chỉnh**, không phải lỗ hổng chặn nghiệp vụ. Làm khi khách thật sự
> sản xuất theo kế hoạch tồn kho (make-to-stock).

### 9.1 Hiện trạng đã kiểm

`PlanningDemandType.FORECAST` **tồn tại nhưng chỉ là một giá trị enum**: không engine dự báo, không kế
hoạch theo kỳ đứng sau. MRP chạy từ `planning_demands` (Sales Order đã confirm, hoặc `MANUAL`).

### 9.2 Phạm vi — cố ý hẹp

**Làm:** kế hoạch sản xuất theo kỳ cho thành phẩm (item × kỳ × số lượng), publish ⇒ sinh
`PlanningDemand` type `FORECAST`, **và** quy tắc khử trùng với nhu cầu Sales Order thật cùng kỳ.

**Không làm (nói rõ để lần đối chiếu sau không mở lại):** engine **dự báo** (forecasting: hồi quy, mùa
vụ…). MPS là *kế hoạch do người lập*, dự báo là *số do thuật toán sinh*. PDF §10.6.2-1 chỉ yêu cầu MPS
làm **nguồn** cho MRP. Thêm forecasting là một dự án riêng (`§11.5`).

### 9.3 Schema — `V99`

```sql
-- Phẳng, KHÔNG header/line: một header chỉ để nhóm các dòng của cùng một kỳ chưa mua thêm được gì
-- cho tới khi có nhu cầu version hoá kế hoạch (§11.5 — không abstraction cho một use case).
CREATE TABLE mps_entries (
    mps_entry_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       UUID          NOT NULL REFERENCES companies(company_id),
    plant_id         UUID          NOT NULL REFERENCES plants(plant_id),
    item_id          UUID          NOT NULL REFERENCES items(item_id),
    period_start     DATE          NOT NULL,
    period_end       DATE          NOT NULL,
    planned_quantity NUMERIC(19,6) NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    planning_demand_id UUID        REFERENCES planning_demands(planning_demand_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID, version BIGINT DEFAULT 0,
    CONSTRAINT uk_mps_entries_plant_item_period UNIQUE (plant_id, item_id, period_start),
    CONSTRAINT chk_mps_entries_status   CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED')),
    CONSTRAINT chk_mps_entries_quantity CHECK (planned_quantity > 0),
    CONSTRAINT chk_mps_entries_period   CHECK (period_end >= period_start)
);

-- Forecast consumption (§9.4). NOT NULL DEFAULT 0 an toàn ở đây vì "chưa bị tiêu" ĐÚNG là 0
-- cho mọi dòng lịch sử — khác §8.3 nơi 'PENDING' là một khẳng định không ai đo.
ALTER TABLE planning_demands
    ADD COLUMN consumed_quantity NUMERIC(19,6) NOT NULL DEFAULT 0;
ALTER TABLE planning_demands
    ADD CONSTRAINT chk_planning_demands_consumed
        CHECK (consumed_quantity >= 0 AND consumed_quantity <= quantity);
```

- `UNIQUE (plant_id, item_id, period_start)` ⇒ **không thể** có hai kế hoạch cho cùng item cùng kỳ ⇒
  nguồn nhu cầu forecast không thể nhân đôi ngay ở tầng DB (`C-1`).
- `CHECK consumed <= quantity` là **thứ chặn tiêu quá** — không phải nhánh `if` trong service (`C-1`).

### 9.4 🔴 Forecast consumption — phần khó nhất, và chỗ `F9` bắt buộc phải đọc

**Vấn đề nghiệp vụ:** forecast 100 cho tháng 3; khách đặt thật 60 trong tháng 3. MRP **không được** thấy
160 (đếm hai lần) — chuẩn APICS là đơn hàng thật **tiêu** forecast cùng kỳ: MRP thấy
`60 (thật) + max(100 − 60, 0) = 100`.

**`F9` — KHÔNG dùng lại `PlanningDemandStatus.CONSUMED`.** Nó **đã có nghĩa khác**, ghi trong javadoc
`PlanningDemandService:164-165`: *"already snapshotted into an MRP run"*. Nhồi nghĩa thứ hai vào ⇒ không
phân biệt được "đã vào lượt chạy" với "đã bị đơn hàng ăn", và `findOpenByReference` (đang loại `CONSUMED`)
sẽ âm thầm loại sai nhóm.

**Thiết kế: cột `consumed_quantity`, không phải một giá trị status.** Vì tiêu trừ **thường là một phần**
(forecast 100, đơn 60 ⇒ còn 40) — một status chỉ biểu diễn được "hết" hoặc "chưa", và sẽ mất 40.

| Sự kiện | Hành vi |
|---|---|
| `SalesOrderService.confirm` | Sau khi sinh demand `SALES_ORDER`, tiêu forecast **cùng (plant, item, kỳ)**: `consumed += min(soQty, forecastOpen)` |
| `SalesOrderService.cancel` | **Nhả** phần đã tiêu tương ứng: `consumed -= …`. 🔴 Thiếu bước này thì huỷ đơn làm forecast **biến mất vĩnh viễn** ⇒ MRP thiếu hàng |
| MRP đọc | Nhu cầu forecast hiệu dụng = `quantity − consumed_quantity` |

**Nhất quán (`C-6`) — đây là đọc–sửa–ghi trên hàng dùng chung:**
- Tiêu trừ nằm **trong cùng transaction** với `confirm` ⇒ hoặc cả hai xảy ra, hoặc không gì cả.
- `PlanningDemand` extends `BaseEntity` ⇒ có `@Version`. Hai đơn hàng confirm đồng thời cùng tiêu một
  dòng forecast: một thắng, một nhận **409 `CONCURRENT_MODIFICATION`** (client retry) — **không** ghi đè
  im lặng. Đây đúng hình dạng bug `§0.40` #2, nên **phải có test đồng thời** (`*IT`), không chỉ test
  đơn luồng.
- `CHECK consumed <= quantity` là lưới cuối: dù logic sai, DB **không** cho tiêu quá.

### 9.5 API & permission

| Endpoint | Permission |
|---|---|
| `POST /v1/plants/{plantId}/mps-entries` · `GET` (list, filter kỳ/item) · `PATCH /v1/mps-entries/{id}` · `POST .../publish` · `POST .../cancel` | **`PERM_MPS_MANAGE`** (ghi) / **`PERM_MPS_READ`** (đọc) |

🔴 **Phase duy nhất có permission mới** ⇒ `coding-rules.md C10` kích hoạt: `V100` seed + **bắt buộc** cập
nhật `docs/roles-and-permissions.md`. Cấp cho **ADMIN + MANAGER**, **không** OPERATOR (kế hoạch sản xuất
là quyết định quản lý, đúng khuôn `V51` costing). Test ma trận grant ở `FlywayMigrationIT.migrate_v99_*`
— 🔴 `PermissionCatalogTest` **không** chứng minh được permission được cấp cho role nào (bài học lặp lại
`C2-5`/`C2-3`/`C2-6`/`C2-7`).

### 9.6 Bất biến mới

| # | Nội dung | Test bảo vệ |
|---|---|---|
| **`B134`** | Một dòng MPS `PUBLISHED` sinh **đúng một** `PlanningDemand` type `FORECAST` (`planning_demand_id` nối 1-1); `UNIQUE (plant, item, period_start)` chặn nhân đôi ở DB; `cancel` huỷ demand `OPEN` tương ứng | `MpsServiceTest.publish_*`, `FlywayMigrationIT.migrate_v98_*` |
| **`B135`** | Forecast consumption dùng **`consumed_quantity`**, **không** dùng `PlanningDemandStatus.CONSUMED` (giá trị đó đã có nghĩa "đã vào một lượt MRP" — `F9`). SO confirm **tiêu**, SO cancel **nhả**; tiêu trừ ở **cùng transaction** với confirm, xung đột đồng thời ⇒ **409**, `CHECK` chặn tiêu quá. Nhu cầu forecast hiệu dụng = `quantity − consumed_quantity` | `SalesOrderServiceTest.confirm_consumesForecast`, `.cancel_releasesForecast`, `PlanningDemandRepositoryIT.concurrentConsumption_*`, `MrpCalculationServiceTest.forecastAndRealOrder_areNotCountedTwice` |

### 9.7 Test plan

| Tầng | Case |
|---|---|
| **Service** | publish sinh demand · publish hai lần ⇒ `STATE_CONFLICT` + **không** demand thứ hai (`R5`) · cancel huỷ demand · tiêu trừ một phần (100/60 ⇒ consumed 60) · tiêu trừ vượt (100/150 ⇒ consumed **100**, không 150) · cancel nhả đúng phần · item **không** phải thành phẩm ⇒ `OPERATION_NOT_ALLOWED` |
| **Method-security** | deny+allow, `verify(guard)` pin `PERM_MPS_READ`/`_MANAGE` |
| **Controller** | envelope · 409 · 400 ngày sai định dạng · 404 |
| **`*IT`** | `FlywayMigrationIT.migrate_v98_*` (`UNIQUE` + 3 `CHECK`, gồm `consumed <= quantity`) · `migrate_v99_*` (ma trận grant: MANAGER **có**, OPERATOR **không**) · 🔴 `PlanningDemandRepositoryIT`: **hai transaction đồng thời** cùng tiêu một forecast ⇒ một nhận optimistic lock failure (`C-6`; `@Version` **chỉ** Postgres thật kiểm được) |
| **Chuẩn nghiệp vụ** | forecast 100 + SO thật 60 cùng kỳ ⇒ lượt MRP thấy gross **100**, không 160 · huỷ SO đó ⇒ lượt MRP sau thấy lại **100** (forecast được nhả, không mất) |

**Mutation dự kiến (4, ≥3 đụng `src/main`):**

| # | Mutation | Case đỏ kỳ vọng | Chứng minh |
|---|---|---|---|
| 1 | `cancel` **không** nhả consumption (**`src/main`**) | case nghiệp vụ thứ hai | Forecast không mất vĩnh viễn khi huỷ đơn |
| 2 | Tiêu trừ bằng `min` → dùng thẳng `soQty` (**`src/main`**) | `confirm_consumesForecast` (vượt) + `FlywayMigrationIT` (`CHECK`) | Hai tầng cùng chặn (`C-1`) |
| 3 | MRP đọc `quantity` thay `quantity − consumed` (**`src/main`**) | `forecastAndRealOrder_areNotCountedTwice` | Đúng lỗi "đếm hai lần" mà `P11` sinh ra để chống |
| 4 | Bỏ `UNIQUE (plant, item, period_start)` khỏi `V99` (**migration**) | `migrate_v98_*` | `C-1` |

### 9.8 Definition of Done

MPS sinh demand; MRP nhận; nhu cầu SO thật **tiêu trừ** forecast cùng kỳ thay vì cộng dồn; huỷ SO **nhả**
lại; xung đột đồng thời trả 409 (có `*IT` chứng minh); `mvn -o clean verify` PASS, case ≥ baseline;
mutation 4/4; cập nhật `module/planning/CLAUDE.md` (`B134`–`B135`), `module/sales/CLAUDE.md` (hook mới),
**`docs/roles-and-permissions.md`** (`C10`), `docs/api-guide-for-frontend.md`, `docs/system-flow.md`.

**Breaking change wire:** không (module mới + endpoint mới). **Positional Java:** `SalesOrderService` +1
constructor param.

---

## 10. Nợ Mở Có Chủ Đích Sau 5 Phase

> Ghi ra để lần đối chiếu PDF sau **không** mở lại chúng như "khoảng trống mới phát hiện" — đúng cách
> `MANUFACTURING_GAP_ROADMAP.md §4` đã làm.

| # | Nợ | Vì sao để lại |
|---|---|---|
| **10.1** | **Vật tư không quy được về công đoạn** (`§0.1` #2, `B126`). Đóng nó cần `bom_lines.operation_sequence` (+ snapshot lên `work_order_component_lines`) và một field operation trên `MaterialIssueLineRequest` | Đổi **mô hình BOM** — phạm vi riêng, và PDF §10.6.5 chỉ yêu cầu "giá trị WIP theo giai đoạn", mà `P7` trả lời được cho công + overhead, đồng thời **khai báo rõ** phần chưa trả lời được |
| **10.2** | **Chi phí phế (scrap costing)**: giá trị hàng bị QC loại hiện **giữ trong WIP** (`§6.5`) | Tách chi phí phế là chủ đề riêng, không nằm trong ba ô PDF mà `P7` đóng |
| **10.3** | **Material Price Variance** | Đã quyết định loại ở `P3` và lý do **vẫn đúng**: `stock_movements` không có cột giá; cần tầng actual-costing (FIFO/bình quân) chưa tồn tại. `P7` **không** mở lại |
| **10.4** | **Process Costing** | `MANUFACTURING_GAP_ROADMAP.md §4`: repo là discrete/assembly, Job Costing là mô hình **đúng**. Thêm process costing là dựng mô hình chi phí thứ hai không ai dùng |
| **10.5** | **Forecasting engine** (`§9.2`) | MPS là kế hoạch do người lập; dự báo là số do thuật toán sinh. PDF chỉ yêu cầu MPS làm nguồn cho MRP |
| **10.6** | **Serial trong genealogy**: `P9` thiết kế cây theo **lot**; serial xuất hiện như node nhưng **không** có endpoint `/serials/{id}/genealogy` riêng | Thêm khi có yêu cầu thật; dữ liệu đã đủ để thêm sau mà không đổi schema |
| **10.7** | **Toàn bộ `P-Deferred`** (MES/ISA-95, OEE, WIP real-time, CRP động) | Chờ tầng OT. Không thay đổi |

---

## 11. Checklist Mở & Đóng Một Phase

**Khi mở:**
1. Đọc `§0` (10 phát hiện) + mục phase + `§2`/`§3`/`§4`.
2. `ls src/main/resources/db/migration/ | sort -V | tail -3` — chốt số migration **thật**.
3. Chốt các quyết định "phải chốt với user" của phase qua `AskUserQuestion` — **trước** khi viết code
   (`P8 §5.2`, `P7 §6.2`/`§6.4`/`§6.5`, `P10 §8.2`).
4. `mvn -o clean test` lấy **baseline** (`coding-rules.md §11.4`).
5. Copy mục phase sang `NEXT_PHASE_PLAN.md` làm phase đang chạy.

**Khi đóng:**
1. `mvn -o clean verify` **thật với Docker** — case ≥ baseline, `failures = 0`, `errors = 0`.
   ⚠️ Docker Desktop tắt giữa chừng ⇒ **mọi** class IT lỗi như nhau; đó không phải kết quả (`§0.41`/`§0.44`).
2. Nghiệm thu mutation theo bảng của phase; **revert** hết.
3. Smoke test HTTP thật nếu phase đổi wire. ⚠️ Hai bẫy đã trả giá hai lần: `pkill -f spring-boot:run`
   **không** giết tiến trình con `java` (A/B sẽ đo hai lần trên **cùng một** build — `§0.42a`, `§0.48`);
   và Git Bash ánh xạ `/tmp` khác python trên Windows ⇒ truyền dữ liệu qua **stdin**, không qua file tạm.
4. Cập nhật tài liệu theo `dev-workflow.md §6.5`: `CLAUDE.md §0.x` + §0.1, `module/*/CLAUDE.md` (bất
   biến + **test bảo vệ**), `MANUFACTURING_GAP_ROADMAP.md §2.0`+`§2.1`,
   `docs/api-guide-for-frontend.md` nếu đổi wire, `docs/roles-and-permissions.md` nếu có permission mới.
5. 🔴 **Tick phase thì ghi "đã làm tới đâu", KHÔNG ghi "xong"/"hết"/"toàn diện"** — repo đã mắc lỗi
   tuyên bố quá phạm vi **bốn lần** (`CLAUDE.md §0.20` + `P8` chính là nợ của `P4` bị tick sớm).
6. Commit theo `dev-workflow.md §6.6` (kiểm chứng → tài liệu → commit; `git status` trước `add`; message
   có mã phase; **không** `push` nếu user không yêu cầu).

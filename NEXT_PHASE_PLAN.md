# Next Phase Plan — Roadmap toàn bộ phase còn lại

> Phase trước: **`P3` – Costing Engine** ✅ **HOÀN THÀNH 2026-08-05.** Bản ghi đầy đủ: `CLAUDE.md
> §0.31`.
>
> **821 case unit + 88 case IT / 13 class IT · failures = 0, errors = 0** (đo bằng `mvn -o clean
> verify` thật với Docker) · migration mới nhất `V51`.

---

## Vì sao file này có hình dạng khác các đợt trước

Từ trước tới nay file này chỉ giữ **một** phase đang chạy tại một thời điểm (`.claude/rules/dev-
workflow.md §6.6`). Bản này (2026-08-05) là ngoại lệ có chủ đích: viết sẵn roadmap cho **toàn bộ**
công việc còn lại của backend — cả track `C2-*` (Capstone 2) lẫn track `P*` (nghiệp vụ gốc) và nợ kỹ
thuật còn mở — để không phải lật `CLAUDE.md`/`FRONTEND_ALIGNMENT_ROADMAP.md`/
`MANUFACTURING_GAP_ROADMAP.md` mỗi lần muốn biết còn gì chưa làm. **Quy tắc "một phase đang chạy"
vẫn áp dụng khi thực thi** — mục "Trạng thái" ngay dưới đây luôn chỉ đúng một phase là "ĐANG CHẠY",
phần còn lại là "CHỜ".

**Phạm vi:** mọi phase chưa xong và chưa bị loại có chủ đích. Loại trừ tường minh: nhóm `P-Deferred`
(MES/ISA-95, OEE, real-time WIP, dynamic CRP — chờ lớp OT, ngoài phạm vi dự án) và nợ nhỏ đã bị từ
chối có chủ đích (`D`, `E`, `I` ở `FRONTEND_ALIGNMENT_ROADMAP.md §7.1` — xem Phụ lục cuối file).

## Trạng thái

| | |
|---|---|
| **Đang chạy** | *(không có — `P3` vừa xong. Cập nhật dòng này thành tên phase khi bắt tay vào code phase kế tiếp)* |
| **Ứng viên kế tiếp** | Concurrent refresh-token race (mở rộng `D8`) — xem §3 |
| **Bị chặn** | `C2-1`, `C2-2` (chờ FE), `D8c` (chờ quyết định hạ tầng email) |

---

## Thứ tự đề xuất

| # | Phase | Trạng thái | Vì sao xếp ở đây |
|---|---|---|---|
| 1 | ~~`C2-8` — CRP tĩnh + Capacity Board + Schedule Adjustment~~ | ✅ **Đã xong (2026-08-05)** | Đóng nốt `P4`. Bản ghi: `CLAUDE.md §0.30` |
| 2 | ~~`P3` — Costing Engine~~ | ✅ **Đã xong (2026-08-05)** | Bản ghi: `CLAUDE.md §0.31` |
| 3 | Concurrent refresh-token race (mở rộng `D8`) | Không bị chặn | FE coi đây là điều kiện nghiệm thu |
| 4 | `P5` — Serial Number Tracking | Không bị chặn | Đổi `MaterialIssueLineRequest` — nên làm sau khi `P3`/`C2-8` đã ổn định để tránh đổi DTO cùng lúc nhiều phase |
| 5 | `P6` phần còn lại — WO Close/Reconcile | Không bị chặn | Nhỏ, đóng nốt track `P6` |
| 6 | `C2-1` — Audit Logs read API | 🔴 Bị chặn — chờ FE câu 1 | Giữ vị trí trong roadmap để không quên |
| 7 | `C2-2` — Inventory Lot lifecycle API | 🔴 Bị chặn — chờ FE câu 2 | Tương tự |
| 8 | `D8c` — Forgot-password / Account Recovery | 🔴 Bị chặn — chờ quyết định hạ tầng email | Thiết kế đã có sẵn, chỉ chờ chốt `spring-boot-starter-mail` hay dịch vụ ngoài |

> `C2-8`..`8` là thứ tự **đề xuất**, không phải bắt buộc — xác nhận lại với user trước khi bắt đầu
> từng phase, vì độ ưu tiên nghiệp vụ có thể đổi giữa chừng.

---

## 1. `C2-8` — CRP tĩnh + Capacity Board + Schedule Adjustment ✅ ĐÃ XONG (2026-08-05)

Đóng nốt `P4`. Bản ghi đầy đủ (thiết kế, quyết định đã chốt, bất biến B89-B90, breaking changes):
`CLAUDE.md §0.30`. Ba quyết định chốt với user trước khi viết kế hoạch: lịch operation sinh ở
`WorkOrderService.release()` (không phải lúc tạo/`plan()`); nợ B
(`predecessorOperationIds`/sequence-dependency) tách `C2-8b`, không làm ở đây; `schedule-adjustments`
không bao giờ tự dời operation khác, chỉ trả cờ tư vấn. Migration `V48`+`V49`.

---

## 2. `P3` — Costing Engine ✅ ĐÃ XONG (2026-08-05)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P3). Bản ghi đầy đủ (thiết kế, quyết định đã chốt,
bất biến B91-B92, breaking changes): `CLAUDE.md §0.31`. Hai quyết định chốt với user trước khi viết
kế hoạch chi tiết: `laborCost`/`overheadCost` nhập tay theo rate cố định trên `ItemStandardCost`
(không tính từ `WorkOrderOperation.runMinutesPerUnit`); **bỏ Material Price Variance**, chỉ làm
Material Usage Variance (không có cột giá trên `stock_movements`, dữ liệu giá duy nhất
— `PurchaseOrderLine.unitPrice` — tách rời khỏi ledger xuất kho). Module mới `module/costing`.
Migration `V50`+`V51`.

---

## 3. Concurrent refresh-token race (mở rộng `D8`)

Nguồn: `common/security/CLAUDE.md §4.12` — giới hạn đã biết, chấp nhận ở `D8a`: hai request refresh
đồng thời dùng cùng một token hợp lệ không giải được bằng thứ tự thao tác, cần lock/CAS. FE coi đây
là điều kiện nghiệm thu (`FRONTEND_ALIGNMENT_ROADMAP.md §8.0` mục 1).

### Quyết định phải chốt với user TRƯỚC khi viết kế hoạch chi tiết

Có cần "grace window" (token cũ vẫn dùng được N giây sau khi rotate, để tha thứ double-submit do
network retry) hay khoá cứng theo `tokenId` trong lúc rotate là đủ? Đây là quyết định bảo mật, không
tự chọn.

### Thiết kế phác thảo

Redis lock (Lua script atomic, đúng pattern đã dùng cho brute-force counter —
`.claude/rules/architecture-decisions.md`) khoá theo `tokenId` trong lúc rotate; request đến sau
trong lúc khoá còn giữ phải chờ ngắn rồi đọc lại token mới thay vì bị báo `TOKEN_REUSE_DETECTED` oan.
**Không đụng** logic RTR (`B80`) hay absolute timeout (`B81`) đã có — đây là lớp bổ sung, không thay
thế.

### Test bắt buộc

Test mô phỏng hai thread/coroutine gọi `refresh` đồng thời cùng token — xác nhận đúng một request
thắng, request kia nhận lại token mới thay vì bị force-logout.

---

## 4. `P5` — Serial Number Tracking

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P5). Độc lập, nhưng đổi DTO đang chạy nên xếp sau khi
`P3`/`C2-8` ổn định.

### Quyết định phải chốt với user TRƯỚC khi viết kế hoạch chi tiết

Item vừa `lotTracked` vừa `serialTracked` có được phép cùng lúc không, hay hai cờ loại trừ nhau?

### Thiết kế phác thảo (đối chiếu code thật)

- `SerialNumber` entity song song `InventoryLot` (đã đọc `InventoryLot.java` làm mẫu):
  `serialId`, `item`, `serialCode` (unique), `status` (AVAILABLE/ISSUED/SOLD...).
- FK nullable `serialId` trên `stock_movements`/`material_issue_lines`/`production_receipt_lines` —
  đúng pattern cột `lot_id` đã có.
- `Item.serialTracked` (boolean) song song `Item.lotTracked` đã có.

### 🔴 Breaking change thật — ghi rõ khi viết kế hoạch chi tiết

`MaterialIssueLineRequest` hiện nhận `quantity` (BigDecimal) đơn lẻ (đã đọc `MaterialIssueLine.java`
xác nhận shape hiện tại). Với item serial-tracked, request phải đổi sang nhận **danh sách
`serialId`** — đây là thay đổi DTO đang chạy production, phải có mục Breaking Changes tường minh và
sửa (không xoá) test cũ theo `R10`.

### Test bắt buộc

Test receipt sinh đúng N `SerialNumber` cho N unit nhận vào, test issue theo danh sách serial, test
item vừa lot vừa serial theo quyết định đã chốt ở trên.

---

## 5. `P6` phần còn lại — WO Close/Reconcile

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P6). Nhỏ, đóng nốt track `P6` (Sales Order + Fulfillment
đã xong ở `F3`/`F6`).

### Quyết định phải chốt với user TRƯỚC khi viết kế hoạch chi tiết

`CLOSED` có khoá hoàn toàn (không receipt/adjust thêm được) hay vẫn cho phép một số thao tác đọc/
điều chỉnh nhẹ?

### Thiết kế phác thảo (đối chiếu code thật)

`WorkOrderStatus` hiện tại (đã đọc enum, xác nhận): `DRAFT, PLANNED, BLOCKED, RELEASED, IN_PROGRESS,
COMPLETED, CANCELLED` — **chưa có `CLOSED`**. Thêm `CLOSED` là breaking change ngầm theo đúng
checklist `.claude/rules/coding-rules.md §11.3`: rà **mọi** switch/so sánh status hiện có (đặc biệt
`WorkOrderService`, `WorkOrderSupplyService` — nơi `COMPLETED` đang được coi là trạng thái cuối), sửa
CHECK constraint, xét cả nhánh vào lẫn ra của status mới. `CLOSED` khác `COMPLETED` (tự động theo số
lượng) ở chỗ cần **bước reconcile do manager thực hiện tường minh**.

Endpoint: `POST /work-orders/{workOrderId}/close` — chỉ cho phép từ `COMPLETED`, permission tái dùng
`PERM_WORK_ORDER_MANAGE` hay permission mới (`PERM_WORK_ORDER_CLOSE`?) tuỳ mức độ nhạy cảm nghiệp vụ
đã chốt.

### Test bắt buộc

Test cả nhánh vào (`COMPLETED → CLOSED`) và nhánh ra (không được vào từ status khác), test các thao
tác bị chặn sau `CLOSED` theo đúng phạm vi đã chốt.

---

## 6. `C2-1` — Audit Logs read API 🔴 BỊ CHẶN

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.3`.

**Câu hỏi chặn** (verbatim, `docs/capstone2-api-gap-response.md §5` câu 1): màn hình Audit dùng được
khi `changes[]` rỗng, hay phải chờ diff capture thật? Nếu FE cần diff thật thì phải mở rộng
`AuditableAspect` để ghi field-level change (`AuditLogChange` đã tồn tại từ `V6` nhưng **0 call site
ghi vào nó**) — phạm vi khác hẳn việc chỉ thêm read API. **Không code phase này tới khi có câu trả
lời.**

### Bẫy đã biết (ghi sẵn để không quên khi tới lượt làm)

`audit_logs` **không có cột `plant_id`** mà filter theo spec đòi hỏi. Thêm cột ⇒ mọi dòng lịch sử
`NULL` (audit append-only, không backfill được) ⇒ filter chỉ đúng cho dòng mới, phải ghi tường minh
`NULL = "trước C2-1"` — đúng tiền lệ `cancel_reason` của `F7`.

### Khung endpoint đã biết (chưa code)

`GET /audit-logs?entityType=&entityId=&action=&actorUserId=&plantId=&traceId=&from=&to=&page=&size=`,
`GET /audit-logs/{auditLogId}`.

---

## 7. `C2-2` — Inventory Lot lifecycle API 🔴 BỊ CHẶN

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.2`.

**Câu hỏi chặn** (câu 2, `docs/capstone2-api-gap-response.md §5`): màn hình Inventory Lots có dẫn
user sang QC disposition khi lot `HOLD` chờ QC không? Nếu `POST /inventory/lots/{id}/status` cho tự
do `HOLD → AVAILABLE` thì lot của production receipt ra `AVAILABLE` **không qua QC** ⇒
`fulfilledQuantity` của SO line không bao giờ tăng ⇒ đơn treo `IN_PROGRESS` vĩnh viễn, **không lỗi
nào báo** — dựng lại đúng nợ #17 mà `D5` đã trả (`B62`). **Không code phase này tới khi có câu trả
lời.**

### Bẫy đã biết (ghi sẵn để không quên khi tới lượt làm)

`InventoryLot` **không có** warehouse, **không có** manufacture date (đã đọc entity xác nhận —
chỉ có `lotId`, `item`, `lotCode`, `status`, `receivedAt`, `expiresAt`). `warehouseId` bắt buộc phải
resolve qua `stock_balances` (tái dùng `StockBalanceRepository.aggregate*`, rule C14), **không**
thêm cột `warehouseId` lên `InventoryLot`. `manufactureDate` map từ `receivedAt` (alias, đúng pattern
`bomCapturedAt` của `F8`) — đừng thêm cột cho giống tài liệu literal.

### Khung endpoint đã biết (chưa code)

`GET /inventory/lots?warehouseId=&itemId=&status=&search=&expiryFrom=&expiryTo=&page=&size=`,
`GET /inventory/lots/{lotId}`, `POST /inventory/lots/{lotId}/status`.

---

## 8. `D8c` — Forgot-password / Account Recovery 🔴 BỊ CHẶN

Thiết kế đã có sẵn đầy đủ ở `common/security/CLAUDE.md §4.14` — chỉ tóm tắt lại, **không thiết kế
lại từ đầu** khi tới lượt làm:

- `POST /api/v1/auth/forgot-password { email }` — luôn trả 200 generic message (chống account
  enumeration theo §4.13), sinh token UUID lưu Redis `auth:reset:{token}` → `{userId}` TTL 15 phút,
  gửi email chứa link reset.
- `POST /api/v1/auth/reset-password { token, newPassword }` — `RESET_TOKEN_INVALID` (401) nếu không
  tìm thấy, validate password policy, đặt mật khẩu mới, xoá token ngay (single-use), xoá toàn bộ
  refresh token của user (force logout), audit `PASSWORD_RESET`.
- Flow liên quan nhưng tách biệt: Admin Manual Unlock (`PATCH /api/v1/admin/users/{userId}/unlock`)
  — cùng section, cũng chưa code.

**Câu hỏi chặn:** `pom.xml` **chưa có** `spring-boot-starter-mail`. Dùng SMTP thật, dịch vụ ngoài
(SES/SendGrid), hay mock/log ra console cho môi trường dev? Quyết định hạ tầng phải chốt trước khi
viết dòng code đầu tiên. **Không code phase này tới khi có quyết định.**

---

## Phụ lục — nợ nhỏ không thành phase riêng (đọc để biết, không phải action item)

| Nợ | Trạng thái | Ghi chú |
|---|---|---|
| `C` (idempotency `created_by` scope, `D6`) | Mở, chưa lên lịch | Cần backfill cột `created_by` (nullable) trước khi scope theo nó — việc lớn hơn một phase nhỏ, gộp vào bất kỳ phase nào đụng `stock_movements` nếu tiện |
| `D` (17 DTO còn `itemCode`/`lotCode` cũ) | **Từ chối có chủ đích** (`F7`) | Không đổi — 17 DTO này ngoài luồng spec |
| `E` (`sourceBomCode` + BOM `outputQuantity`) | **Loại khỏi phạm vi có chủ đích** (`F10`, user xác nhận 2026-08-01) | Đổi công thức nổ BOM, ảnh hưởng số MRP của mọi WO — rủi ro khác hẳn, chỉ làm nếu có yêu cầu mới rõ ràng |
| `I` (`childBom` trên snapshot component line) | **Từ chối có chủ đích** (thiết kế từ `P1`, bất biến `B12`) | WO snapshot direct-only; FE gọi endpoint BOM tree sẵn có thay vì nest vào snapshot |
| Sweep 500 toàn endpoint | Một phần đã làm (`§0.24`), chưa rà hết | Không phải phase riêng — làm dần khi chạm tới từng module |
| Commit OpenAPI snapshot vào repo | Process/hành chính, không phải code | Câu hỏi 5 cho FE, `docs/capstone2-api-gap-response.md §5` |

---

## Checklist tổng — tick khi xong, cập nhật cùng lúc với `CLAUDE.md §0.1`

- [x] **`C2-8`** — CRP tĩnh + Capacity Board + Schedule Adjustment ✅ **2026-08-05**
  - [x] Quyết định thời điểm sinh lịch — chốt: lúc `release()`
  - [x] Quyết định phạm vi nợ B (`predecessorOperationIds`) — chốt: tách `C2-8b`
  - [x] Migration (`V48`+`V49`) + scheduling trong `WorkOrderService.release()`
  - [x] Endpoint `GET .../capacity-board` + `POST .../schedule-adjustments`
  - [x] Test (unit + method-security + controller + `WorkOrderOperationRepositoryIT` cho JPQL
        timezone-bucketing) — 791 case unit + 87 case IT / 13 class IT
  - [x] Docs cập nhật (`CLAUDE.md` §0.30, `module/workorder`+`workcenter`+`shift` CLAUDE.md,
        `FRONTEND_ALIGNMENT_ROADMAP.md`, `roles-and-permissions.md`, `api-guide-for-frontend.md`,
        `BACKEND_CAPSTONE2_API_GAPS.md` checklist)
- [x] **`P3`** — Costing Engine ✅ **2026-08-05**
  - [x] Quyết định labor/overhead cost: nhập tay theo rate cố định — chốt với user
  - [x] Quyết định bỏ Material Price Variance, chỉ làm Usage Variance — chốt với user
  - [x] `ItemStandardCost` entity + migration (`V50`)
  - [x] `CostingService` (BOM cost roll-up nhiều cấp, đệ quy giống `MrpCalculationService`)
  - [x] `WorkOrderCostAccumulator` + wiring vào `MaterialIssueService`/`ProductionExecutionService`
  - [x] Variance tiền trên `WorkOrderVarianceResponse` (`usageVarianceCost` + `costVariance`)
  - [x] Endpoint CRUD `ItemStandardCost` + permission mới (`PERM_COSTING_READ`/`_MANAGE`, `V51`)
  - [x] Test (821 case unit + 88 case IT / 13 class IT) + docs + smoke test HTTP thật
- [ ] **Concurrent refresh-token race**
  - [ ] Quyết định có grace window hay khoá cứng — đã chốt với user
  - [ ] Lock/CAS qua Redis Lua script khi rotate
  - [ ] Test đồng thời (2 request refresh cùng token)
  - [ ] Docs cập nhật (`common/security/CLAUDE.md`, `error-handling.md` nếu có mã lỗi mới)
- [ ] **`P5`** — Serial Number Tracking
  - [ ] Quyết định `lotTracked`+`serialTracked` có loại trừ nhau — đã chốt với user
  - [ ] `SerialNumber` entity + migration + FK trên 3 bảng (`stock_movements`,
        `material_issue_lines`, `production_receipt_lines`)
  - [ ] `Item.serialTracked` flag
  - [ ] Đổi `MaterialIssueLineRequest` (quantity → serial list khi serial-tracked) — **breaking
        change, ghi rõ mục riêng**
  - [ ] Test (receipt sinh N serial, issue theo serial list) + docs
- [ ] **`P6`** — WO Close/Reconcile
  - [ ] Quyết định phạm vi khoá của `CLOSED` — đã chốt với user
  - [ ] `WorkOrderStatus.CLOSED` + rà switch/so sánh status hiện có (checklist `coding-rules.md §11.3`)
  - [ ] Endpoint `POST /work-orders/{id}/close`
  - [ ] Test (nhánh vào + nhánh ra) + docs
- [ ] **`C2-1`** — Audit Logs read API *(🔴 chờ FE trả lời câu 1 — không code trước khi có câu trả lời)*
- [ ] **`C2-2`** — Inventory Lot lifecycle API *(🔴 chờ FE trả lời câu 2 — không code trước khi có câu trả lời)*
- [ ] **`D8c`** — Forgot-password / Account Recovery *(🔴 chờ quyết định hạ tầng email — không code trước khi có quyết định)*

---

## Quy trình khi bắt đầu một phase trong roadmap này

1. Cập nhật dòng "Đang chạy" ở mục **Trạng thái** đầu file.
2. Đo lại baseline thật (`mvn -o clean verify`) — **đừng chép số ở đầu file này**, số liệu đổi theo
   từng phase.
3. Nếu phase có mục "Quyết định phải chốt với user" — hỏi và ghi lại quyết định vào phần thiết kế của
   phase đó trước khi viết migration/entity.
4. Viết kế hoạch chi tiết hơn cho riêng phase đó (theo đúng khuôn `.claude/rules/dev-workflow.md`),
   có thể thay thế phần tóm tắt ở trên bằng bản đầy đủ khi thực thi.
5. Sau khi xong: tick đủ các dòng con trong Checklist tổng, cập nhật `CLAUDE.md §0.1`/§0.2/bản ghi
   phase mới, dời "Phase trước" trong file này, và **không xoá** các phase còn CHỜ khác — chỉ tick
   phase vừa xong.

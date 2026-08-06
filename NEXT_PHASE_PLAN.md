# Next Phase Plan — Roadmap toàn bộ phase còn lại

> Phase trước: **`P6` — WO Close/Reconcile** ✅ **HOÀN THÀNH 2026-08-06.** Bản ghi đầy đủ:
> `CLAUDE.md §0.34`. Đóng nốt track `P*` — chỉ còn `P-Deferred` (chờ tầng OT, ngoài phạm vi).
>
> **864 case unit + 89 case IT / 13 class IT · failures = 0, errors = 0** (đo bằng `mvn -o clean
> verify` thật với Docker) · migration mới nhất `V53`.

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
| **Đang chạy** | *(không có — `P6` vừa xong, đóng nốt track `P*`)* |
| **Ứng viên kế tiếp** | *(không có — mọi ứng viên còn lại đều bị chặn, xem hàng dưới)* |
| **Bị chặn** | `C2-1`, `C2-2` (chờ FE), `D8c` (chờ quyết định hạ tầng email) |

---

## Thứ tự đề xuất

| # | Phase | Trạng thái | Vì sao xếp ở đây |
|---|---|---|---|
| 1 | ~~`C2-8` — CRP tĩnh + Capacity Board + Schedule Adjustment~~ | ✅ **Đã xong (2026-08-05)** | Đóng nốt `P4`. Bản ghi: `CLAUDE.md §0.30` |
| 2 | ~~`P3` — Costing Engine~~ | ✅ **Đã xong (2026-08-05)** | Bản ghi: `CLAUDE.md §0.31` |
| 3 | ~~Concurrent refresh-token race (mở rộng `D8`)~~ | ✅ **Đã xong (2026-08-05)** | Bản ghi: `CLAUDE.md §0.32` |
| 4 | ~~`P5` — Serial Number Tracking~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.33` |
| 5 | ~~`P6` — WO Close/Reconcile~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.34` |
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

## 3. Concurrent refresh-token race (mở rộng `D8`) ✅ ĐÃ XONG (2026-08-05)

Đóng giới hạn "race double-submit" mà `D8a` chấp nhận có chủ đích (`common/security/CLAUDE.md
§4.12`). Bản ghi đầy đủ (thiết kế thật, bất biến `B95`, breaking changes): `CLAUDE.md §0.32`.
Quyết định chốt với user trước khi viết kế hoạch chi tiết: **hard lock, không grace window** — token
cũ không bao giờ được chấp nhận lại làm credential lần hai. Thiết kế thật lệch bản phác thảo gốc ở
đúng một chỗ: khoá dùng `SET NX PX` (`opsForValue().setIfAbsent`, atomic một lệnh) thay vì Lua script
— đọc lại `TokenStoreService.incrementFailCount` (bộ đếm brute-force) mới phát hiện nó **chưa từng**
dùng Lua như tài liệu cũ ghi (đã sửa cùng phase, xem `CLAUDE.md §0.32` hệ quả #3). Không migration.

---

## 4. `P5` — Serial Number Tracking ✅ ĐÃ XONG (2026-08-06)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P5). Bản ghi đầy đủ (thiết kế, quyết định đã chốt, bất
biến B96-B99, breaking changes): `CLAUDE.md §0.33`. Hai quyết định chốt với user trước khi viết kế
hoạch chi tiết: `lotTracked`/`serialTracked` **loại trừ nhau**; serial-tracked output **không** có
HOLD chờ QC (mirror non-lot-tracked của `D5`, tránh phải thêm `serial_id` vào `stock_balances` +
viết lại `StockBalanceRepository.aggregate*`). Thiết kế thật lệch bản phác thảo gốc ở đúng một chỗ,
theo hướng **tốt hơn dự đoán**: mỗi movement chạm serial luôn `quantity = 1` nên
`MaterialIssueLineRequest`/`ProductionReceiptPostRequest` chỉ cần thêm field optional
(`serialId`/`serialNumber`) — **không phải breaking change** như roadmap gốc dự đoán (`List<serialId>`
thay `quantity`). Migration `V52`.

---

## 5. `P6` — WO Close/Reconcile ✅ ĐÃ XONG (2026-08-06)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P6). Đóng nốt track `P6` (Sales Order + Fulfillment đã
xong ở `F3`/`F6`) — và đóng nốt **toàn bộ** track `P*` (chỉ còn `P-Deferred`, chờ tầng OT, ngoài phạm
vi). Bản ghi đầy đủ (thiết kế, bất biến B100, breaking changes): `CLAUDE.md §0.34`. Quyết định chốt
với user trước khi viết kế hoạch chi tiết: `CLOSED` **khoá hoàn toàn** — không carve-out đọc/ghi nào
sau khi đóng (phương án đơn giản hơn trong hai phương án được hỏi). `close()` cũng là bước "Reconcile"
tường minh: giải phóng mọi reservation `ACTIVE` còn sót về lại tồn khả dụng trước khi khoá vĩnh viễn
— tái dùng đúng `materialReservationService.cancelActiveReservations(...)` mà `cancel()` đã có sẵn.
Tái dùng `PERM_WORK_ORDER_MANAGE`, không permission mới. Migration `V53`. Phase phát hiện và sửa một
bug thật: `canReserve()` là danh sách phủ định, thiếu loại trừ `CLOSED` tường minh.

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
- [x] **Concurrent refresh-token race** ✅ **2026-08-05**
  - [x] Quyết định có grace window hay khoá cứng — chốt: khoá cứng, không grace window
  - [x] Advisory lock (`SET NX PX`, không Lua) + breadcrumb kết quả rotate qua `TokenStoreService`
  - [x] Test đồng thời (mô phỏng race, breadcrumb absent/present, lock not-acquired)
  - [x] Docs cập nhật (`common/security/CLAUDE.md` §4.8+§4.12+§4.12a mới, `module/auth/CLAUDE.md`
        `B95`, `architecture-decisions.md`, `FRONTEND_ALIGNMENT_ROADMAP.md §8.0`)
- [x] **`P5`** — Serial Number Tracking ✅ **2026-08-06**
  - [x] Quyết định `lotTracked`+`serialTracked` loại trừ nhau — chốt với user
  - [x] Quyết định serial-tracked output không HOLD chờ QC — chốt với user
  - [x] `SerialNumber` entity + migration (`V52`) + FK trên 3 bảng (`stock_movements`,
        `material_issue_lines`, `production_receipt_lines`)
  - [x] `Item.serialTracked` flag + validate loại trừ (service + CHECK constraint)
  - [x] Nối vào `InventoryMovementService` (receive/issue/adjust) + `MaterialIssueService` +
        `ProductionReceiptService` (post/approve/qcDisposition) — **additive, không breaking**
  - [x] Test (850 case unit + 89 case IT / 13 class IT) + docs
- [x] **`P6`** — WO Close/Reconcile ✅ **2026-08-06**
  - [x] Quyết định phạm vi khoá của `CLOSED` — chốt với user: khoá hoàn toàn
  - [x] `WorkOrderStatus.CLOSED` + rà switch/so sánh status hiện có (checklist `coding-rules.md §11.3`)
        — phát hiện + sửa bug thật: `canReserve()` là danh sách phủ định, thiếu loại trừ `CLOSED`
  - [x] `close()` reconcile: giải phóng reservation `ACTIVE` còn sót (`materialReservationService
        .cancelActiveReservations`), tái dùng `PERM_WORK_ORDER_MANAGE`, không permission mới
  - [x] Endpoint `POST /work-orders/{id}/close` — migration `V53`
  - [x] Test (nhánh vào + nhánh ra + regression mỗi gate ghi) — 864 case unit + 89 case IT / 13 class IT + docs
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

# Next Phase Plan — Roadmap toàn bộ phase còn lại

> Cập nhật ad-hoc mới nhất: **Track `AR-*` — refactor toàn bộ hạ tầng Audit** hoàn thành
> **2026-09-02** — bản ghi đầy đủ `CLAUDE.md §0.49`, kế hoạch + log triển khai + danh sách "cố ý chưa
> làm" ở `AuditRefactorPlan.md §13`, chi tiết kỹ thuật `common/audit/CLAUDE.md` (viết lại hoàn toàn).
> Migration **`V67`** (outbox + `audit_log_entities` + 10 cột mới, thuần additive, **không backfill**)
> + **`V68`** (trigger append-only). **Không permission mới, không breaking change wire.**
> Phạm vi và failure policy (**`FAIL_OPEN` toàn hệ thống**) do user chốt qua `AskUserQuestion`.
> 🔴 **Ba defect gốc, cả ba im lặng — không log, không lỗi, không test nào đỏ:** mọi event auth bị
> vứt bỏ (`AuthService` không có `@Transactional`, mà `AFTER_COMMIT` listener cần một transaction để
> giao event) · event `FAILURE` mất khi rollback · một `X-Trace-Id` dài làm hỏng insert **sau khi**
> business đã commit, tức client tự tắt được audit trail của chính mình. Sửa bằng transactional
> outbox (`B119`–`B123`). **1225 case unit / 158 class + 154 case IT / 22 class IT · failures = 0,
> errors = 0** (`mvn -o clean verify` thật với Docker); `AuditPipelineIT` (class IT thứ 22) chứng
> minh commit/rollback/idempotency/append-only trên Postgres thật.
>
> Trước đó: **Sửa contract giá trị của Audit Log** hoàn thành **2026-08-25** — bản ghi
> đầy đủ `CLAUDE.md §0.48`, trả lời FE `FE_SingleTask_Response.md`, nguồn
> `BACKEND_AUDIT_LOG_VALUE_CONTRACT_2026-08-25.md`. **Không migration, không permission mới, không
> endpoint mới.** `changes[].oldValue`/`newValue` của `GET /v1/audit-logs/{id}` trở lại **`string` |
> `null`** đúng như OpenAPI công bố — hồi quy từ commit `caps2 done` (đổi DTO sang `JsonNode` cùng
> lượt bật field-level diff) khiến một snapshot có cấu trúc lọt ra dạng object và làm sập màn hình
> Audit của FE. Bất biến **`B118`** (`common/audit`). **Breaking change wire: có, hẹp** — là khôi phục
> contract cũ. **1184 case unit / 152 class + 142 case IT / 21 class IT · failures = 0, errors = 0**
> (`mvn -o clean verify` thật với Docker) · nghiệm thu mutation 3/3 đụng `src/main` · smoke test A/B
> qua HTTP thật trên cùng một bản ghi audit.
>
> Trước đó: **Track `EH-*` — refactor hạ tầng xử lý lỗi** hoàn thành **6/8 hạng mục**
> 2026-08-23 — bản ghi đầy đủ `CLAUDE.md §0.47`, kế hoạch + khảo sát + breaking changes
> `ExceptionHandlerRefactorPlan.md`. **Không migration, không permission mới, không endpoint mới.**
> `EH-1` (lưới an toàn filter chain + `ApiErrorController`) · `EH-2` (tách `OPERATION_NOT_ALLOWED`
> thành 3 mã trên 59/96 throw site — **breaking change wire**, đổi `code`, giữ nguyên HTTP 422) ·
> `EH-3` (`DataIntegrityErrorMapper`, FK/`CHECK`/`NOT NULL` 409 → **422**) · `EH-4`
> (`MrpRunStateRecorder`, `REQUIRES_NEW` + sanitize `errorMessage`) · `EH-5` (xoá 4 mã chỉ khai báo,
> giữ 5 kèm lý do) · `EH-6` (tài liệu). `EH-7`/`EH-8` **hoãn có chủ đích**, lý do ở §4 của plan.
> **1181 case unit / 152 class + 142 case IT / 21 class IT · failures = 0, errors = 0**
> (`mvn -o clean verify` thật với Docker) · nghiệm thu mutation 5/5 đụng `src/main`.
>
> Trước đó: **Trả lời `live-data-audit.md` của FE** hoàn thành 2026-08-14 — bản ghi
> đầy đủ `CLAUDE.md §0.44`, hướng dẫn FE `FE_SingleTask_Response.md`. Migration **`V58`**
> (`mrp_runs` + idempotency). Hai thay đổi: available của lot `HOLD`/`REJECTED` nay là `0` trên
> `/inventory/balances` + `/inventory/lots*` (`B116`), và `POST /planning-runs` nhận
> `Idempotency-Key` tuỳ chọn (`B117`). Mục thứ ba FE báo (convert suggestion "không atomic")
> **không phải defect** — đã bác bỏ bằng dữ liệu thật, không sửa dòng nào.
> **1025 case unit + 120 case IT / 17 class IT · failures = 0, errors = 0**.
>
> Trước đó: **FE handoff Inventory Dashboard API** hoàn thành 2026-08-14 — bản ghi
> đầy đủ `CLAUDE.md §0.43`, hướng dẫn FE `FE_SingleTask_Response.md`. Không migration, không permission
> mới, không endpoint mới; `GET /reports/inventory-dashboard` bổ sung nhãn hiển thị + `generatedAt` +
> `lowStockLimit`/`movementLimit`. **1009 case unit + 119 case IT / 17 class IT · failures = 0,
> errors = 0** (`mvn -o clean verify` thật với Docker). Bất biến `B114`, `B115`.
>
> Trước đó: **FE-4 5C Item Master permission contract** hoàn thành 2026-08-09.
> Migration `V57__separate_item_master_permissions.sql`; unit suite `977/977` xanh. Flyway IT đã được
> bổ sung nhưng chưa chạy lại trong phiên này vì Docker engine không hoạt động.
>
> Phase trước: **`C2-2` — Inventory Lot lifecycle API** ✅ **HOÀN THÀNH 2026-08-06.** Bản ghi đầy đủ:
> `CLAUDE.md §0.37`. `GET /inventory/lots` + `GET /inventory/lots/{lotId}` +
> `POST /inventory/lots/{lotId}/status`. HOLD-escape gate qua `LotQcOriginLookupService` (cross-module
> lookup mới, `inventory → workorder`). Không migration.
>
> **925 case unit + 105 case IT / 14 class IT · failures = 0, errors = 0** (đo bằng `mvn -o clean
> verify` thật với Docker) · migration mới nhất vẫn `V55` (`C2-2` không migration).

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
| **Đang chạy** | *(không có — `C2-2` vừa xong, toàn bộ track `C2-*`/`P*`/`D8` đã đóng)* |
| **Ứng viên kế tiếp** | *(chưa chốt — xem Phụ lục cho nợ nhỏ còn mở, hoặc hỏi user)* |
| **Bị chặn** | *(không có)* |

---

## Thứ tự đề xuất

| # | Phase | Trạng thái | Vì sao xếp ở đây |
|---|---|---|---|
| 1 | ~~`C2-8` — CRP tĩnh + Capacity Board + Schedule Adjustment~~ | ✅ **Đã xong (2026-08-05)** | Đóng nốt `P4`. Bản ghi: `CLAUDE.md §0.30` |
| 2 | ~~`P3` — Costing Engine~~ | ✅ **Đã xong (2026-08-05)** | Bản ghi: `CLAUDE.md §0.31` |
| 3 | ~~Concurrent refresh-token race (mở rộng `D8`)~~ | ✅ **Đã xong (2026-08-05)** | Bản ghi: `CLAUDE.md §0.32` |
| 4 | ~~`P5` — Serial Number Tracking~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.33` |
| 5 | ~~`P6` — WO Close/Reconcile~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.34` |
| 6 | ~~`C2-1` — Audit Logs read API~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.36` |
| 7 | ~~`C2-2` — Inventory Lot lifecycle API~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.37` |
| 8 | ~~`D8c` — Forgot-password / Account Recovery~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.35` |

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

## 6. `C2-1` — Audit Logs read API ✅ ĐÃ XONG (2026-08-06)

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.3`. FE xác nhận (`docs/capstone2-api-gap-response.md §5`
câu 1, 2026-08-06): màn hình Audit dùng được với `changes[]` rỗng — đợt 1 không cần chờ diff capture
thật (đợt 2, `AuditableAspect` field-level, vẫn chưa xếp lịch). `PERM_AUDIT_READ` **ADMIN-only**
(chốt với user qua `AskUserQuestion`). Cột `plant_id` thêm vào `audit_logs` (`V54`) nhưng **chỉ dừng
ở schema** — không wiring populate real-time, mọi dòng (cũ lẫn mới) vẫn `NULL` cho tới phase riêng.
2 endpoint: `GET /audit-logs` (list, không `changes[]`), `GET /audit-logs/{id}` (detail, `changes[]`
join thật vào `AuditLogChangeRepository`, luôn rỗng hôm nay vì chưa ai ghi vào bảng đó — không phải
hardcode `[]`). Phát hiện + sửa biến thể mới của lỗi `lower(bytea)` (`CLAUDE.md §0.24`): tham số
`Instant` chỉ xuất hiện ở vế `IS NULL` khiến Postgres không suy được type — sửa bằng
`cast(:from as timestamp) IS NULL`. Migration `V54`+`V55`. Bản ghi đầy đủ: `CLAUDE.md §0.36`,
`common/audit/CLAUDE.md §9.12`.

---

## 7. `C2-2` — Inventory Lot lifecycle API ✅ ĐÃ XONG (2026-08-06)

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.2`. FE xác nhận (`docs/capstone2-api-gap-response.md §5`
câu 2, 2026-08-06): màn hình Inventory Lots bắt buộc dẫn user sang QC disposition khi lot `HOLD` chờ
QC — giữ nguyên thiết kế đã mô tả. 3 endpoint: `GET /inventory/lots`, `GET /inventory/lots/{lotId}`,
`POST /inventory/lots/{lotId}/status`. **Không migration.**

Quyết định chốt với user (`AskUserQuestion`): HOLD-escape gate dùng **cross-module lookup thật**
(`LotQcOriginLookupService` mới trong `module/workorder`, kiểm `ProductionReceiptLine` tồn tại **và**
`QualityDisposition` chưa tồn tại), không phải heuristic cùng-module suy từ `referenceType` — heuristic
có lỗ hổng thật với lot đã QC rồi bị đưa lại `HOLD` thủ công. Hướng phụ thuộc mới `inventory →
workorder`, hợp lệ theo rule C7. `InventoryMovementService.changeLotStatus` (đã có từ `F2`) không đổi
gì — gate nằm ở tầng gọi mới, luồng QC disposition hiện có không bị ảnh hưởng. Bản ghi đầy đủ (bất
biến `B102`-`B106`, breaking changes): `CLAUDE.md §0.37`.

---

## 8. `D8c` — Forgot-password / Account Recovery ✅ ĐÃ XONG (2026-08-06)

Trả nốt 3/3 nợ #6 (`D8a` RTR + `D8b` absolute session timeout đã trả 2/3 trước đó). Thiết kế đã có
sẵn ở `common/security/CLAUDE.md §4.14` — phase này triển khai đúng thiết kế, không thiết kế lại.
Bản ghi đầy đủ (bất biến `B101`, breaking changes): `CLAUDE.md §0.35`. Quyết định hạ tầng chốt với
user trước khi viết kế hoạch chi tiết: gửi email bằng **mock/log console** — không thêm
`spring-boot-starter-mail`, không SMTP/SES/SendGrid nào được tích hợp, không interface cho một
implementation duy nhất (`coding-rules.md §11.5`). 3 endpoint mới: `POST /auth/forgot-password`,
`POST /auth/reset-password`, `PATCH /admin/users/{id}/unlock` (controller `/admin` đầu tiên trong
repo). Không migration.

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
- [x] **`D8c`** — Forgot-password / Account Recovery ✅ **2026-08-06**
  - [x] Quyết định hạ tầng gửi email — chốt với user: mock/log console, không `spring-boot-starter-mail`
  - [x] `PasswordResetTokenService` (Redis `auth:reset:{token}` + reverse index, TTL 15m, single-use)
  - [x] `AuthService.forgotPassword`/`resetPassword`/`adminUnlockAccount` — nợ #6 trả đủ 3/3
  - [x] Endpoint `POST /auth/forgot-password`, `POST /auth/reset-password`, `PATCH /admin/users/{id}/unlock`
  - [x] Test (885 case unit + 89 case IT / 13 class IT) + docs
- [x] **`C2-1`** — Audit Logs read API ✅ **2026-08-06**
  - [x] Quyết định phạm vi permission — chốt với user: `PERM_AUDIT_READ` ADMIN-only
  - [x] Quyết định phạm vi cột `plant_id` — chốt: schema-only, không wiring populate real-time
  - [x] `AuditLogQueryService` (tách khỏi `AuditLogService`) + `AuditLogRepository.search`
  - [x] Endpoint `GET /audit-logs` + `GET /audit-logs/{id}` — migration `V54`+`V55`
  - [x] Phát hiện + sửa biến thể mới của lỗi `lower(bytea)` (`Instant` bare `IS NULL`)
  - [x] Test (898 case unit + 98 case IT / 14 class IT) + docs
- [x] **`C2-2`** — Inventory Lot lifecycle API ✅ **2026-08-06**
  - [x] Quyết định cơ chế HOLD-escape gate — chốt với user: cross-module lookup thật, không heuristic
  - [x] `LotQcOriginLookupService` (mới, `module/workorder`) — entry point cross-module `inventory → workorder`
  - [x] `InventoryLotService` (list/get/changeStatus) — reuse `InventoryMovementService.changeLotStatus`
  - [x] Endpoint `GET /inventory/lots` + `GET /inventory/lots/{lotId}` + `POST .../status` — không migration
  - [x] Test (925 case unit + 105 case IT / 14 class IT) + docs

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

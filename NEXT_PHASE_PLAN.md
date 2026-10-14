# Next Phase Plan — Roadmap toàn bộ phase còn lại

> **`G2` — FE handoff Giai đoạn 2** (`FE_HANDOFF_GD_02_2026-10-12.md`): ✅ **BE xong + nghiệm thu
> 2026-10-14** — `G2-1`..`G2-5` hoàn tất, file trả lời **`BE_RESPONSE_GD_02_2026-10-14_v2.md`** (thay v1,
> **user tự gửi qua Zalo**). Lỗi `leadTimeDays: 1.5` bị cắt thành `1` **đã sửa cho toàn hệ thống** (§9.6).
> Phase chỉ đóng khi **FE** báo A01–A22 đạt. **1325 case unit / 162 class + 184 case IT / 27 class IT ·
> failures = 0, errors = 0** (`mvn -o clean verify` thật với Docker). Bản ghi `CLAUDE.md §0.50`, chi tiết **§9**.
>
> Trước đó: **Track `AR-*` — refactor toàn bộ hạ tầng Audit** hoàn thành
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
| **Đang chạy** | **`G3` — FE handoff Giai đoạn 3: Nhập Excel dữ liệu nền** (`FE_HANDOFF_GD_03_2026-10-12.md`, P1) — ✅ **BE xong 2026-10-14** (D1–D8 chốt theo khuyến nghị), chờ **FE** nghiệm thu A01–A26 — xem **§10**, bản ghi `CLAUDE.md §0.51` |
| **Chờ FE** | `G2` — ✅ BE xong 2026-10-14, chờ FE nghiệm thu (§9.6) — xem **§9** |
| **Ứng viên kế tiếp** | `G1` — FE handoff Giai đoạn 1 (`BE_HANDOFF_GD_01_2026-10-09.md`, Auth/tài khoản/quyền) — **chưa lập kế hoạch**. `G4` — FE handoff Giai đoạn 4 (tồn kho/lô/chuyển kho) — **chưa lập kế hoạch** |
| **Bị chặn** | *(không có — D1–D7 đã chốt 2026-10-13)*. ⚠️ `MANUFACTURING_PHASE_PLAN_P7_P11.md` dự kiến migration `V69`→`V73`, nhưng `G2-1` đã dùng `V69`/`V70` ⇒ phải đánh số lại trước khi làm `P7` |

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
| 9 | **`G2` — FE handoff Giai đoạn 2 (tổ chức + danh mục nền)** | ✅ **BE xong (2026-10-14)** — chờ FE nghiệm thu | Nguồn `FE_HANDOFF_GD_02_2026-10-12.md`, P1. Chi tiết **§9**, bản ghi `CLAUDE.md §0.50` |
| 10 | **`G3` — FE handoff Giai đoạn 3 (nhập Excel dữ liệu nền)** | ✅ **BE xong — chờ FE nghiệm thu** | Nguồn `FE_HANDOFF_GD_03_2026-10-12.md`, P1. Phụ thuộc `G2` (Customer/Supplier master) — đã xong. Chi tiết **§10** |

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

## 9. `G2` — FE handoff Giai đoạn 2: Tổ chức và danh mục nền ✅ BE XONG (2026-10-14) — chờ FE nghiệm thu

Nguồn: `FE_HANDOFF_GD_02_2026-10-12.md` (v1, P1, PO chốt FE ngày 12/10/2026). FE đã chốt mock; BE phải
(1) **trả lời** 16 câu §5 của họ, (2) **đối chiếu** 11 kết quả R1–R11 với API hiện có, (3) **chỉ code
phần thiếu**. Đầu ra gửi FE theo `BETaskDeliveryRules.md`: file `BE_RESPONSE_GD_02_<ngày gửi>.md`.

> 🔴 **Đọc như bảng câu hỏi, không như danh sách việc** (`BETaskDeliveryRules.md §5` lỗi #1, đã mắc
> thật ở `§0.41`/`§0.43`). Trong 16 câu §5, phần lớn trả lời được bằng hành vi hiện có; chỉ những mục
> đánh dấu **CODE** ở §9.1 mới là việc phải viết. FE cũng ghi rõ: *"Không coi bảng này là xác nhận rằng
> API hiện có đang sai."*

### 9.1 Đối chiếu yêu cầu FE ↔ code thật *(rà ngày 2026-10-13, bằng đọc source — chưa gọi live)*

Phạm vi đã rà: `SupplierController/Service/Repository`, `ItemWarehouseSettingService/Request/Response`,
`OrganizationController` + `OrganizationService.deactivatePlant/activateWarehouse/resolveTimezone`,
`ItemController` + `ItemService.create/update/listItems`, `UomController`, `UomLookupService`,
`PageableFactory`, `V13`/`V20`/`V21`/`V66` + unique constraint của `plants`/`warehouses`/`items`/`uoms`,
`application.yml` (`spring.jackson`). **Chưa rà:** `AccessControl`/scope `GLOBAL` (thuộc `G1`), FE
adapter (không có repo FE).

| # | FE yêu cầu | Hiện trạng code | Loại |
|---|---|---|---|
| R1 | Supplier: list/create/detail/update/activate/deactivate | **Đã có** nhưng **lệch hình dạng**: path `/v1/suppliers?companyId=` (không phải `/companies/{C}/suppliers`), query `keyword` (không phải `search`), deactivate là `PATCH .../deactivate`, **không có activate**, thiếu `contactName`/`notes`, thừa `taxCode`/`companyCode` | **CODE** (§9.2 D1) |
| R1 | Customer master | **Không tồn tại.** `sales_orders.customer_name` là text tự do | **CODE** — module mới |
| R2 | code trim/uppercase, unique theo Company + loại danh mục | Supplier: `UNIQUE (company_id, code)` (`V20`) + service uppercase. Item/Plant/Warehouse: `@Pattern("^[A-Z0-9._-]+$")` chạy **trước** normalize ⇒ `" partner-01 "` bị **400**, không được chuẩn hoá | Trả lời (FE đã tự normalize) |
| R3 | Update không đổi code/parent/type/tracking; unit theo Q6 | `ItemUpdateRequest` chỉ có `name`+`unit` ⇒ code/type/tracking **không đổi được**, nhưng field lạ bị **bỏ qua im lặng** (`fail-on-unknown-properties: false`, `§0.42`) chứ không bị từ chối. **`unit` sửa được** qua PATCH | Trả lời + **CODE** (Q6, D5) |
| R4 | Không tạo/kích hoạt Warehouse dưới Plant INACTIVE; không ghi settings cho master INACTIVE | Đã có (`activateWarehouse`, `B108`; settings dùng `getActiveItem` + `findActiveWarehouse`). ⚠️ Settings **không** kiểm Plant của kho có ACTIVE không | Trả lời + **CODE nhỏ** |
| R5 | GET settings đúng Plant đã authorize | 🔴 **Lỗ hổng thật:** `GET /v1/inventory/item-warehouse-settings` **không** có filter plant; không truyền `warehouseId` thì guard là `hasPermission` (chỉ đọc scope `GLOBAL`) và trả settings của **mọi công ty** — đúng cái bẫy đã ghi ở `CLAUDE.md §0.46` (PF-03). `X-Plant-Id` FE gửi bị bỏ qua | **CODE** |
| R6 | safety/ROP decimal ≥ 0, leadTime nguyên ≥ 0 | Đã có (`@PositiveOrZero`, `@Min(0)`, `Integer`). ⚠️ Wire là **JSON number**, không phải "decimal string" như FE ghi | Trả lời (Q12) |
| R7 | Không 2 mapping ACTIVE cùng pair; ≤ 1 default ACTIVE / Item+Plant+mục đích | Đã có ở DB: `uk_item_wh_settings_active` (partial, `V13`) + trigger `trg_item_warehouse_default_role` (`V66`) | Trả lời |
| R8 | Thay default có xác nhận, nguyên tử, serialize đồng thời | 🔴 **Ngược thiết kế FE:** `upsert` **luôn tự chuyển** cờ từ kho cũ sang kho mới, **không hỏi** (`releaseDefaultRoleFromOtherWarehouses`). Nguyên tử trong 1 transaction: có. Serialize đồng thời: **không** — hai request cùng đọc rồi cùng flush, trigger chặn một bên nhưng mã lỗi trả ra chưa ai kiểm | **CODE** (D2) + `*IT` |
| R9 | Ngừng dùng là logical; reference mới loại INACTIVE | Đã có cho mọi master. ⚠️ `ItemWarehouseSetting.deactivate()` **không xoá cờ** default ⇒ row INACTIVE giữ `defaultSupply=true` | **CODE nhỏ** (cần cho Q7) |
| R10 | Search/filter trước pagination, tie-breaker ổn định | 🔴 `GET /companies/{C}/plants` và `/plants/{P}/warehouses` **không có** `search`/`status` — FE adapter gửi, Spring **bỏ qua im lặng** ⇒ live trả danh sách không lọc. Item list chỉ có `search`. Phần lớn list dùng `PageableFactory.of(4 tham số)` — **không** tie-breaker | **CODE** |
| R11 | Sai quyền/scope bị từ chối trên server | Đã có `@PreAuthorize` mọi method. Ngoại lệ chính là R5 | Trả lời + R5 |
| §4 | `INVALID_TIMEZONE` | 🔴 **Bug tiềm ẩn:** `resolveTimezone` chỉ `trim()`, **không** `ZoneId.of` ⇒ timezone rác được lưu, rồi nổ **500** về sau ở `WorkCalendarLookupService.computeEndInstant` lúc release WO (`C2-8`) | **CODE** |
| A08 | UOM INACTIVE không dùng tạo Item mới | 🔴 **Chưa nối:** `UomLookupService` có **0 caller ngoài module `uom`**; `Item.unit` là `String` tự do, không kiểm với bảng `uoms` (nợ ghi từ `C2-3`) | **CODE** (D5) |
| Q4 | Chặn deactivate Plant còn Warehouse ACTIVE | `deactivatePlant` **không kiểm gì** | **CODE** (D4) |
| Q10 | Replay `Idempotency-Key` cho mutation master | **Không** controller master nào đọc header | Trả lời (D6) |

### 9.2 Quyết định phải chốt với user trước khi code ✅ *(chốt 2026-10-13: user trả lời "làm" ⇒ cả 7 theo cột "Khuyến nghị BE")*

Hai chi tiết con chốt khi giao việc: D4 dùng **mã mới** `ACTIVE_CHILDREN_EXIST` (422); timezone sai dùng
**`VALIDATION_ERROR` + `errors[{field:"timezone"}]`** (mã đã có, tương đương `INVALID_TIMEZONE` của FE — không thêm mã).

| # | Quyết định | Khuyến nghị BE | Lý do | Chặn |
|---|---|---|---|---|
| **D1** | Supplier: đổi sang hình dạng FE đề xuất, hay **thêm song song**? | **Thêm song song (additive)**: thêm `GET/POST /v1/companies/{C}/suppliers` (query `search`) + `POST /v1/suppliers/{id}/activate` + `POST /v1/suppliers/{id}/deactivate`; **giữ** path cũ (`/v1/suppliers?companyId=`, `PATCH .../deactivate`) | Path cũ đang dùng cho luồng mua hàng (`§0.46` seed gọi nó). Đổi tại chỗ = breaking không cần thiết. Customer dùng **đúng** hình dạng mới ngay từ đầu | `G2-2` |
| **D2** | `replaceDefaults` trên `PUT` settings | `Boolean replaceDefaults` **nullable**: `null` ⇒ **giữ hành vi hiện tại** (tự chuyển cờ), `false` ⇒ `409 DEFAULT_CONFLICT` nếu kho khác giữ cờ, `true` ⇒ chuyển cờ. Thêm lock bi quan theo `(item, plant)` để serialize | FE đề xuất mặc định `false` ⇒ mọi client hiện tại (adapter cũ, script seed `D26-`/`VIETBIKE`) đang thành công sẽ **bắt đầu nhận 409** — breaking. Nullable vừa cho FE đúng hành vi mới, vừa không phá ai | `G2-3` |
| **D3** | PATCH đối tác: `omitted = giữ, null = xoá` (Q3)? | **Không theo FE** — giữ quy ước toàn repo: `null`/omitted = giữ, **chuỗi rỗng `""` = xoá** | Java `record` không phân biệt được omitted với `null` nếu không thêm `JsonNullable`/`Map` — một cơ chế mới chỉ cho 2 endpoint. Tiền lệ: `CLAUDE.md §0.29` hệ quả #4. Trả lời Q3 bằng phương án khác + ví dụ JSON | `G2-1`, `G2-2` (PATCH) |
| **D4** | Deactivate Plant còn Warehouse ACTIVE (Q4) | **Chặn**, `422` mã mới `ACTIVE_CHILDREN_EXIST` (hoặc tái dùng `OPERATION_NOT_ALLOWED` nếu user không muốn mã mới) | Không cascade ngoài ý định người dùng — FE cũng nghiêng về chặn. ⚠️ Rà script seed có deactivate plant không trước khi bật | `G2-4` |
| **D5** | `Item.unit`: bất biến qua PATCH (Q6) + kiểm với UOM master (A08) | **Có cả hai**: create ⇒ `unit` phải là UOM **ACTIVE** (`RESOURCE_INACTIVE`/`ENTITY_NOT_FOUND`); PATCH ⇒ `unit` khác giá trị cũ thì `422`, giống thì chấp nhận (adapter cũ vẫn gửi `unit` không đổi). Giữ `unit` trong `ItemUpdateRequest` để không breaking | 🔴 **Rủi ro:** item tạo với unit **không có** trong bảng `uoms` (dữ liệu demo, script seed) sẽ hết tạo được. Phải rà `scripts/demo*/catalogue.sh` tạo UOM trước item chưa | `G2-4` |
| **D6** | `Idempotency-Key` cho mutation master (Q10) | **Không làm ở phase này** — trả lời: header bị bỏ qua; retry create được chặn bởi unique code ⇒ `409 RESOURCE_ALREADY_EXISTS` (FE hiểu là "đã tạo"), retry PATCH/activate là idempotent tự nhiên | `A2` giới hạn idempotency cho POST nhạy cảm ghi tồn kho/chứng từ. Làm cho ~20 endpoint master là phase riêng, chưa ai cần ngoài nghiệm thu A21 | Không (chỉ trả lời) |
| **D7** | Permission settings: FE ghi `PERM_ITEM_*`, code dùng `PERM_INVENTORY_*` | **Giữ `PERM_INVENTORY_*`**, trả lời FE mapping đúng | Đổi permission = đổi cấu hình role của mọi môi trường. Customer: thêm `PERM_CUSTOMER_READ/_MANAGE` theo đúng ma trận grant hiện có của `PERM_SUPPLIER_*` (Q2) | `G2-1` (perm Customer), `G2-0` |

### 9.3 Gói công việc *(thứ tự thực thi; mỗi gói là một commit theo `dev-workflow.md §6.6`)*

#### `G2-0` — Baseline + trả lời §5 cho FE *(không code `src/main`, không bị chặn)*

1. `mvn -o clean verify` với Docker ⇒ ghi baseline thật (số cuối cùng đã ghi là **1225 unit / 158
   class + 154 IT / 22 class**, `§0.49` — **đo lại, không chép**).
2. Chạy backend + gọi live từng endpoint hiện có ở FE §3.3 trên DB dùng-một-lần (chuỗi C1→P1→W1/W2→
   KG→I1) để có **request/response thật** cho file trả lời (`B2`: cấm mẫu viết tay).
3. Viết `BE_RESPONSE_GD_02_<ngày>.md` §0–§8: trả lời Q1–Q16 (đã làm / sẽ làm sau quyết định §9.2 /
   khác đề xuất + ví dụ JSON), bảng mapping mã lỗi FE §4 ↔ mã thật (Q13):

   | FE đề xuất | Mã thật | HTTP thật |
   |---|---|---|
   | `DUPLICATE_CODE` | `RESOURCE_ALREADY_EXISTS` | 409 |
   | `INACTIVE_PARENT` / `INACTIVE_REFERENCE` | `RESOURCE_INACTIVE` | **422**, không phải 409 |
   | `NOT_FOUND` | `ENTITY_NOT_FOUND` | 404 |
   | `PLANT_CONTEXT_MISMATCH` | `STATE_CONFLICT` | **409**, không phải 400 (`error-handling.md §5.6.1`) |
   | `IMMUTABLE_FIELD` | *(không có — field lạ bị bỏ qua, response trả giá trị cũ)* | 200 |
   | `INTERNAL_ERROR` | `INTERNAL_SERVER_ERROR` | 500 |
   | `INVALID_TIMEZONE` · `ACTIVE_CHILDREN` · `DEFAULT_CONFLICT` | chưa có ⇒ theo `G2-3`/`G2-4` | — |

   Kèm trả lời Q12 (decimal là **JSON number**, scale theo DB `NUMERIC(19,6)`), Q14 (unique: Plant/Item/
   Supplier theo Company, Warehouse theo Plant, UOM toàn hệ thống — **đúng như FE đề xuất**, đã có ở
   DB), Q15 (chờ `G1`).
4. Gửi **sau** khi user chốt §9.2 — câu trả lời phụ thuộc D1–D7.

#### `G2-1` — Customer master *(mới hoàn toàn; chỉ chặn bởi D3 cho PATCH)*

- **Vị trí:** `module/sales/` (entity `Customer` + repository + service + controller + lookup service),
  đối xứng với `Supplier` nằm trong `module/purchasing`. **Không** nối `SalesOrder.customerName` →
  `customer_id` (FE ghi bán hàng đầy đủ là ngoài phạm vi).
- **Migration `V69`:** bảng `customers` (`customer_id` UUID, `company_id` FK, `code` 100, `name` 255,
  `contact_name` 255, `email` 255, `phone` 255, `address` 500, `notes` 500, `status` + `CHECK`, audit
  field, `version`), `UNIQUE (company_id, code)`, index `(company_id, status)`. Cùng migration: thêm
  `contact_name`/`notes` vào `suppliers` (cho `G2-2`).
- **Migration `V70`:** seed `PERM_CUSTOMER_READ`/`_MANAGE`, grant **đúng ma trận** `PERM_SUPPLIER_*`
  đang có trong DB (đọc từ `role_permissions` thật, không đoán từ `V21`); ghi `docs/roles-and-permissions.md` (`C10`).
- **6 endpoint** đúng FE §3.4: `GET/POST /v1/companies/{C}/customers`, `GET/PATCH /v1/customers/{id}`,
  `POST /v1/customers/{id}/activate|deactivate`. Guard: `hasResourceAccess(..., 'COMPANY', ...)` +
  guard record cho path `{id}` (mẫu `purchasingPermissionGuard.hasSupplierAccess`). Sort mặc định `code
  asc` **có tie-breaker** id.
- `AuditAction`: `CUSTOMER_CREATED/UPDATED/ACTIVATED/DEACTIVATED`.
- **Test:** service (uniqueness, normalize, lifecycle idempotent, Supplier + Customer cùng code hợp lệ),
  method-security (deny + allow + `verify` chuỗi `PERM_*`), controller (envelope, 201, 409, 400 email),
  `FlywayMigrationIT.migrate_v70_*` (ma trận grant **dương + cấm**), `CustomerRepositoryIT` nếu `search`
  dùng `concat`/`like` trên tham số nullable (bẫy `lower(bytea)`, `§0.24`).

#### `G2-2` — Căn chỉnh Supplier *(chặn bởi D1, D3)*

- Thêm `contactName`/`notes` vào request/response (additive). Giữ `taxCode`.
- Theo D1: thêm path company-scoped + `activate` + `POST deactivate`; path cũ giữ nguyên.
  `Supplier.activate()` chặn khi Company `INACTIVE` (đúng `B108`).
- Test sửa theo `R10` cho record positional (`SupplierResponse`, `SupplierCreateRequest`).

#### `G2-3` — Item–Warehouse settings *(chặn bởi D2)*

1. **R5 (lỗ hổng scope):** thêm query `plantId` vào `GET`; guard `hasResourceAccess(..., 'PLANT',
   plantId)`; repository lọc `warehouse.plant`. Cross-check `X-Plant-Id` qua `PlantContextResolver` cho
   `GET` (có `plantId`) và `PUT` (đối chiếu plant của `warehouseId`) — theo `§5.6.1`.
   ⚠️ Giữ nhánh `warehouseId` hiện có. Bỏ hẳn nhánh "không filter + `hasPermission`" là **breaking** ⇒ ghi
   rõ, hỏi user nếu phải bỏ.
2. **R8/D2:** `replaceDefaults` nullable + `BusinessErrorCode.DEFAULT_CONFLICT` (409) + lock bi quan
   (`@Lock(PESSIMISTIC_WRITE)` trên query lấy settings của `(item, plant)`) **trước** khi đọc peer.
3. **Q7:** upsert tìm cả row `INACTIVE` của cùng pair ⇒ **kích hoạt lại** (giữ `settingId`) thay vì
   insert row mới.
4. **R9:** `deactivate()` xoá cả hai cờ default (bắt buộc cho bước 3 — nếu không, kích hoạt lại một row
   mang cờ cũ sẽ đụng trigger `V66`).
5. **R4:** từ chối khi Plant của kho `INACTIVE` (`RESOURCE_INACTIVE`).
6. **Test:** unit cho cả 3 nhánh `replaceDefaults`; **`ItemWarehouseSettingConcurrencyIT`** (2 thread,
   cùng item+plant, cả hai `replaceDefaults=true` ⇒ cuối cùng đúng 1 default, không 500) — mock **không**
   kiểm được lock/trigger (`R7`); IT cho filter `plantId` (đúng bẫy `§0.46` PF-03: fixture **phải có 2
   công ty**).

#### `G2-4` — Organization + Item: filter, lifecycle, validation *(chặn bởi D4, D5)*

1. **R10:** `search` (code/name) + `status` cho list plants/warehouses; `status`/`type`/`lotTracked`
   cho list item (Q9 — tên tham số chốt `type`, khớp field response). Mọi list trong phạm vi dùng
   `PageableFactory.of(..., tieBreaker)`. JPQL nullable ⇒ `cast(... as string)` + `*IT` (bẫy `§0.24`,
   enum `null` cũng cần kiểm).
2. **Timezone:** `resolveTimezone` validate bằng `ZoneId.of` ⇒ `400` mã mới `INVALID_TIMEZONE` (hoặc
   `VALIDATION_ERROR` + `errors[{field:"timezone"}]` — chốt cùng D4). **Không** sửa dữ liệu cũ đã lưu.
3. **D4:** `deactivatePlant` chặn khi còn Warehouse `ACTIVE`.
4. **D5:** `ItemService.create` resolve `unit` qua `UomLookupService` (ACTIVE); `update` từ chối đổi
   `unit`. Lần đầu nối `items.unit` với UOM master — **không** đổi cột (vẫn là code string).
5. **Test:** service + controller cho từng nhánh mới; `OrganizationRepositoryIT`/`ItemRepositoryIT` cho
   filter; regression: chạy lại `scripts/demo-capstone2/seed.sh` trên DB trắng (D5 có thể phá seed).

#### `G2-5` — Nghiệm thu + bàn giao

- `mvn -o clean verify` thật với Docker; số case **≥ baseline G2-0** (`R10`).
- Nghiệm thu mutation, ưu tiên các chỗ mock không thấy: bỏ lock bi quan (IT concurrency phải đỏ), bỏ
  filter `plantId` trong JPQL, đổi `DEFAULT_CONFLICT` sang mã khác **cùng 409**, bỏ vế `ACTIVE` khi
  resolve UOM.
- Smoke test HTTP thật cho A01–A22 phần thuộc BE (A18 thuộc `G1`; A21 trả lời theo D6) — ghi request/
  response/`X-Trace-Id`/giờ đo.
- Cập nhật `BE_RESPONSE_GD_02_*.md` (§7 bằng chứng + mục **chưa nghiệm thu**), `CLAUDE.md §0`
  (bản ghi mới), `module/sales|purchasing|inventory|organization/CLAUDE.md` (bất biến mới — tiếp số từ
  `B123`), `docs/api-guide-for-frontend.md`, `docs/roles-and-permissions.md`.

### 9.4 Breaking change dự kiến *(tuỳ quyết định §9.2)*

| Thay đổi | Breaking? | Phụ thuộc |
|---|---|---|
| Customer + `PERM_CUSTOMER_*` + path Supplier mới + `contactName`/`notes` | **Không** (additive) | D1 |
| `replaceDefaults` nullable | **Không** nếu theo khuyến nghị D2; **có** nếu mặc định `false` như FE đề xuất | D2 |
| `GET settings` thêm `plantId` | Không (tuỳ chọn); **có** nếu bỏ nhánh không-filter | §9.3 G2-3 #1 |
| Deactivate Plant còn kho ACTIVE ⇒ 422 | **Có** (request trước đây 200) | D4 |
| Item create với unit không có/INACTIVE trong UOM ⇒ lỗi; PATCH đổi unit ⇒ 422 | **Có** | D5 |
| Timezone rác ⇒ 400 | **Có** (sửa sai: trước đây lưu rồi 500 về sau) | — |
| Filter `search`/`status` trên list org | Nới lỏng (trước đây bị bỏ qua) | — |

### 9.6 Kết quả thực thi *(2026-10-14)*

Làm đúng §9.3; breaking change thực tế khớp §9.4 (6 mục "chặn mới", chi tiết `BE_RESPONSE_GD_02_2026-10-14.md §3`).
Bản ghi đầy đủ: `CLAUDE.md §0.50`.

| Còn mở | Trạng thái | Cần gì |
|---|---|---|
| ~~`leadTimeDays: 1.5` ⇒ lưu `1`~~ (FE A10) | ✅ **Đã sửa cho toàn hệ thống** (user chốt): `accept-float-as-int: false` + handler trả `errors[{field}]`. `5.0` cũng bị từ chối. Breaking mục 7 trong `_v2` | — (đã gửi trong `BE_RESPONSE_GD_02_2026-10-14_v2.md`) |
| FE nghiệm thu A01–A22 | Chờ FE | FE gửi kết quả; chưa đạt ⇒ trả lời bằng `_v2`, không mở file mới |
| A04/A14 live, A17 bằng tài khoản giới hạn, A19 (125 UOM), Q5, Q16 live | Chưa đo — ghi ở `BE_RESPONSE_GD_02_2026-10-14.md §7.3` | Xem file đó |

### 9.5 Ngoài phạm vi *(theo FE §1.3 + quyết định BE)*

CRUD Company; Import Excel; PO/GRN, giá, công nợ; nối `SalesOrder` với `Customer`; serial tracking;
UOM quy đổi; tự chọn kho khi thiếu mapping; `Idempotency-Key` cho master (D6); mọi thay đổi Auth/scope
`GLOBAL` (thuộc `G1`); đổi định dạng decimal sang string toàn hệ thống (Q12 — tác động mọi endpoint,
`BETaskDeliveryRules.md §5` lỗi #7).

---

## 10. `G3` — FE handoff Giai đoạn 3: Nhập Excel dữ liệu nền ✅ BE XONG (2026-10-14) — chờ FE nghiệm thu

Nguồn: `FE_HANDOFF_GD_03_2026-10-12.md` (v1, P1). FE/mock đã xong; BE phải (1) trả lời Q1–Q14 §5, (2) đối chiếu R01–R09 với API có sẵn, (3) chỉ code phần thiếu, (4) trả `BE_RESPONSE_GD_03_<ngày>.md` theo `BETaskDeliveryRules.md`. Mọi path/DTO/mã lỗi trong file FE là **[Đề xuất BE]**, không phải API đã có; FE chưa gọi live lần nào (§2: không có bằng chứng lỗi).

> 🔴 **Bảng câu hỏi, không phải danh sách việc** (`BETaskDeliveryRules.md §5` lỗi #1). Và lần này còn một điều nặng hơn: **repo đã có module nhập dữ liệu** (`module/dataimport`, `V59`/`V60`/`V64`, `/v1/import-runs`) mà file FE **không biết tới** — nó được dựng cho một hợp đồng khác. Quyết định lớn nhất của phase là reuse hay dựng song song (D1).

### 10.1 Đối chiếu yêu cầu FE ↔ code thật *(rà 2026-10-14 bằng đọc source, chưa gọi live)*

Phạm vi đã rà: `ImportRunController`, `ImportRunService`, `ImportRowApplyService`, `ImportRun`, `ImportTargetType`, `ImportTargetRegistry`, `pom.xml` (POI 5.5.1), `application.yml` (`multipart`, `data-import.max-rows`), `V60`. **Chưa rà:** `SpreadsheetReader`/`SpreadsheetTemplateWriter` (guard ZIP, kiểu ô), `ItemImportTargetHandler`, `ImportProfile`/`ColumnMapping`, `DataImportPermissionGuard`, `V59`/`V64` chi tiết — phải đọc ở `G3-0`.

| Chủ đề | FE yêu cầu | Hiện trạng | Lệch? |
|---|---|---|---|
| Luồng | `POST batches` (upload + validate một phát) → `POST .../commit` | `POST /v1/import-runs` (chỉ stage, **không** validate) → `POST .../validate` (cần **profile** ánh xạ cột) → `POST .../apply` | 🔴 Có — FE dùng **mẫu cố định** `omniplant-import-v1`, không có profile/mapping cột |
| Atomicity | **Tất cả hoặc không** (R03, A15, A18) | `apply` lặp từng dòng, mỗi dòng `REQUIRES_NEW`, có trạng thái `PARTIALLY_APPLIED`; javadoc ghi đó là "import contract" | 🔴 **Xung đột ngữ nghĩa trực diện** |
| Đối tượng | `items` + `suppliers` + `customers` | Chỉ `ImportTargetType.ITEM` | 🔴 Thiếu 2 handler (Supplier/Customer master đã có từ `G2`) |
| Quyền | READ+MANAGE **của chính loại** (`PERM_ITEM_*`/`SUPPLIER_*`/`CUSTOMER_*`), không permission import riêng | `PERM_DATA_IMPORT_READ/EXECUTE` (`V60`), một cặp cho mọi target | 🔴 Có — mô hình quyền khác hẳn |
| Giới hạn | 5 MiB, **500** dòng | `max-file-size` 10MB, `max-rows` **5000** | 🟡 Cấu hình |
| Retry | `Idempotency-Key` trên cả upload lẫn commit; commit lại trả receipt cũ | Chỉ `apply`, key tuỳ chọn, replay trả run cũ (đã đúng tinh thần) | 🟡 Thiếu ở upload |
| History | list/detail theo **owner** + quyền loại | `list` theo công ty (`PERM_DATA_IMPORT_READ`), **không** lọc owner | 🟡 Lệch (Q13) |
| Mẫu `.xlsx` | metadata `_OmniPlant` veryHidden, VI/EN, tải mẫu do **FE** tạo | Có `SpreadsheetTemplateWriter` (chưa rõ mẫu), ánh xạ theo tên cột người dùng chọn | 🟡 Cần đọc |
| Item type/tracking | enum + `NONE\|LOT` | `ItemService.createItem` có `serialTracked`, `type` | Trả lời |
| Trùng mã | unique `(company, loại, code)` kể cả INACTIVE | Đã có ở DB cho `items`/`suppliers`/`customers` (`G2`) | Trả lời (Q8) |
| `unit` | UOM ACTIVE thật, không tin sheet tham chiếu | `ItemService.create` đã resolve UOM ACTIVE từ `G2` (D5) | Trả lời |

### 10.2 Quyết định đã chốt với user *(2026-10-14, `AskUserQuestion` — cả 8 theo cột "Khuyến nghị BE"; D4/D6 không chặn nên theo đề xuất FE)*

| # | Quyết định | Khuyến nghị BE | Lý do | Chặn |
|---|---|---|---|---|
| **D1** | Reuse `module/dataimport` hay dựng module batch mới song song? | **Mở rộng `module/dataimport`**: thêm đường "mẫu cố định" cạnh đường profile (path mới `/v1/data-import/batches`, **giữ** `/v1/import-runs`); thêm target `SUPPLIER`/`CUSTOMER` vào `ImportTargetType` + 2 `ImportTargetHandler` | Tái dùng `ImportRun`/`ImportRow`, `SpreadsheetReader`, registry, audit. Dựng bảng `data_import_batches` riêng = hai hệ nhập cho cùng việc. Rủi ro: hai luồng cùng tồn tại — chấp nhận, đường cũ không đổi | `G3-1` |
| **D2** | Atomic all-or-nothing (FE) vs per-row partial (đang có) | **Atomic cho đường mới, giữ per-row cho đường cũ.** Commit mới chạy **một** transaction (500 dòng ⇒ nhỏ), bất kỳ dòng nào lỗi ⇒ rollback cả lô + trả `409 IMPORT_REVALIDATION_FAILED` | FE dựa vào atomic (A15, A18) và loại partial khỏi phạm vi. Đường cũ có sẵn khách (seed/script) — đổi ngữ nghĩa tại chỗ là breaking. Cần trạng thái `READY`/`INVALID`/`COMMITTED` riêng với enum cũ | `G3-1` |
| **D3** | Mô hình quyền | **Theo FE**: đường mới cần READ+MANAGE **của đúng loại**; kiểm trong service với `hasResourceAccess` đúng scope (Item: PLANT/COMPANY/GLOBAL; đối tác: COMPANY/GLOBAL). **Không** dùng `PERM_DATA_IMPORT_*` cho đường mới | FE đã thiết kế và chặn ghép chéo loại. `PERM_DATA_IMPORT_*` giữ nguyên cho đường cũ. ⚠️ cần đọc `DataImportPermissionGuard` xem có tái dùng được không | `G3-1` |
| **D4** | Path: `/v1/data-import/batches` (FE) | **Theo FE** | Không xung đột `/v1/import-runs`. Context-path `/api` đã ở ngoài | — |
| **D5** | Giới hạn 500 dòng / 5 MiB | **Theo FE** cho đường mới (cấu hình riêng `max-rows-fixed-template`, **không** hạ ngưỡng 5000 của đường cũ). Thêm giới hạn giải nén thực tế/entry trong parser | A04 đòi 500 dòng hợp lệ nhận, 501 bị từ chối. Q11 đòi BE công bố ngưỡng server cụ thể | `G3-1` |
| **D6** | Mẫu `.xlsx`: ai là nguồn sự thật | **BE chỉ đọc** theo `omniplant-import-v1` (sheet metadata `_OmniPlant` `A1/B1/C1`), **không** thêm endpoint tải mẫu (FE đã tự tạo, §3.2) | Đúng đề xuất FE "bản đầu không yêu cầu endpoint download riêng" | — |
| **D7** | History/detail: lọc theo **owner** (FE Q13) hay công ty | **Owner + quyền hiện tại của loại**, ID trái owner/scope ⇒ `404` (không lộ tồn tại) | Khớp UI FE; đường cũ lọc theo công ty — khác biệt có chủ đích, ghi rõ trong response | `G3-1` |
| **D8** | Cắt số lẻ & kiểu ô (Q10): server strict hơn FE (boolean/ô công thức) | **Strict**: formula ⇒ `FORMULA`, số ở cột mã/điện thoại ⇒ `TEXT_REQUIRED`, ngày/lỗi ⇒ `CELL_TYPE`; rich text ghép chữ; boolean ⇒ `CELL_TYPE` | FE đồng ý đồng bộ strict khi làm adapter. ⚠️ liên quan nợ `leadTimeDays` ở `G2` (§9.6): cùng loại lỗi "ép kiểu im lặng" | `G3-1` |

### 10.2a Kết quả *(2026-10-14)*

- `G3-0`/`G3-1`/`G3-2` xong; chi tiết + bất biến `B142`–`B148`: `CLAUDE.md §0.51`, `module/dataimport/CLAUDE.md`.
- **Khác kế hoạch (có chủ đích):** batch không `READY` ⇒ `STATE_CONFLICT`, `expectedVersion` lệch ⇒ `CONCURRENT_MODIFICATION`
  (mã sẵn có theo `error-handling.md §5.3`, không thêm `IMPORT_BATCH_NOT_READY`/`_VERSION_CONFLICT`); `X-Plant-Id` lệch ⇒ 409
  `STATE_CONFLICT` (quy ước sẵn có, không phải `400 PLANT_CONTEXT_MISMATCH`); plant khác công ty ⇒ 422 `RESOURCE_SCOPE_MISMATCH`.
- **Hai lỗi phát sinh, đã sửa trong phase:** `MaxUploadSizeExceededException` → 500 (nay 413); `committedAt`/`createdAt` lệch µs giữa
  response đầu và lần đọc lại (smoke lượt 1 bắt được).
- **Chưa nghiệm thu:** A21 nhánh "thu hồi assignment **sau** COMMITTED" chỉ đo bằng method-security test (smoke dùng user không quyền
  từ đầu); A26 "CPU/timeout" không có timeout riêng — chặn bằng ngưỡng kích thước; bộ mẫu `.xlsx` **do FE tạo** chưa từng được BE đọc thử
  (smoke dùng file tự dựng theo §3.2).

### 10.3 Gói công việc *(thứ tự thực thi; mỗi gói một commit theo `dev-workflow.md §6.6`)*

#### `G3-0` — Đọc nốt + baseline *(không code `src/main`)*

1. Đọc những file còn thiếu ở §10.1 (đặc biệt `SpreadsheetReader` — kiểu ô/ZIP guard; `ItemImportTargetHandler`; `DataImportPermissionGuard`; `V59`/`V64`). Cập nhật bảng §10.1 nếu có gì đổi.
2. `mvn -o clean verify` với Docker ⇒ baseline thật (số cuối đã ghi: **1320 unit / 162 class + 184 IT / 27 class**, `§0.50` — **đo lại**).
3. Chạy `/v1/import-runs` live (upload → validate → apply) trên DB dùng-một-lần để có request/response **thật** cho file trả lời (`B2`).
4. Chốt D1–D8 với user.

#### `G3-1` — Đường "mẫu cố định" atomic *(chặn bởi D1–D3, D5, D7, D8)*

- **Migration `V71`** *(V69/V70 đã dùng ở `G2`; ⚠️ `MANUFACTURING_PHASE_PLAN_P7_P11.md` phải đánh số lại)*: thêm vào `import_runs` các cột cho batch (`kind`, `locale`, `template_version`, `owner_user_id`, `version`, `created_ids` jsonb) + mở rộng `CHECK` status/`target_type`; **không backfill**, dòng cũ giữ nguyên. Thêm `idempotency_key` cho upload (unique `(owner_user_id, key)`).
- `ImportTargetType` + `SUPPLIER`, `CUSTOMER`; 2 handler gọi đúng service tạo master của `G2` (không INSERT thẳng — bài học `§0.45` #4).
- `SpreadsheetReader` đọc mẫu cố định + guard giải nén/entry/macro/external link.
- Service: `validate` (một phát, ghi batch `INVALID`/`READY`, **0 master**), `commit` (một transaction, revalidate UOM/mã/quyền, `expectedVersion`, retry COMMITTED trả receipt cũ **sau** authorization), `list`/`detail` theo owner.
- Mã lỗi theo FE §4 ↔ mã thật (đối chiếu `error-handling.md`; mã nghiệp vụ import là enum riêng `ImportErrorCode` đã có — **chốt mapping** ở `G3-0`).
- **Test:** unit (từng issue code, atomic, retry), method-security (deny+allow+`verify` chuỗi `PERM_*` cho **cả ba loại**, ghép chéo bị từ chối), controller, **`DataImportBatchIT`** (rollback thật khi dòng cuối lỗi; hai commit song song cùng mã ⇒ một lô thắng; 0 master khi validate — mock **không** kiểm được, `R7`; fixture **hai công ty**, bẫy `§0.46`), `FlywayMigrationIT.migrate_v71_*`.

#### `G3-2` — Nghiệm thu + bàn giao

- `mvn -o clean verify` thật; số case **≥ baseline** (`R10`).
- Nghiệm thu mutation, ưu tiên chỗ mock không thấy: đổi transaction commit về per-row (IT atomic phải đỏ), bỏ revalidate UOM ở commit, bỏ lọc owner ở list, đổi `IMPORT_REVALIDATION_FAILED` sang mã khác **cùng 409**.
- Smoke HTTP thật cho A01–A26 phần BE; **A21 (thu hồi quyền)** cần tài khoản giới hạn — ghi rõ nếu chưa đo.
- Docs: `BE_RESPONSE_GD_03_*.md` (Q1–Q14 + §7 bằng chứng + mục **chưa nghiệm thu**), `CLAUDE.md §0`, `module/dataimport/CLAUDE.md` (**chưa tồn tại — tạo**; bất biến tiếp từ `B141`), `docs/api-guide-for-frontend.md`, `docs/roles-and-permissions.md` nếu đổi quyền.

### 10.4 Breaking change dự kiến

| Thay đổi | Breaking? |
|---|---|
| Path `/v1/data-import/batches*` mới, `SUPPLIER`/`CUSTOMER` target mới, cột `import_runs` mới | **Không** (additive) nếu theo D1/D2 |
| Atomic thay per-row | **Có** nếu áp lên `/v1/import-runs` — **tránh** bằng D2 (chỉ đường mới) |
| Giới hạn 500 dòng | Chỉ đường mới |

### 10.5 Ngoài phạm vi *(theo FE §1.2)*

`.csv`/`.xls`/`.xlsm`, upsert/xoá qua file, partial import, import Company/Plant/Warehouse/UOM, tồn đầu kỳ/BOM/routing, giá/công nợ, tài khoản đăng nhập, job queue/`PROCESSING` (Q14), endpoint tải mẫu (D6), xem batch người khác của admin (Q13), thay Auth/email giai đoạn 1.

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
- [ ] **`G2`** — FE handoff Giai đoạn 2: tổ chức + danh mục nền *(BE xong 2026-10-14; mở cho tới khi FE nghiệm thu đạt)*
  - [x] Chốt 7 quyết định §9.2 (D1–D7) với user — 2026-10-13, theo khuyến nghị
  - [x] Baseline đo thật 2026-10-13: **1225 unit + 154 IT, failures = 0, errors = 0** (`mvn -o clean verify`, Docker)
  - [x] `G2-1` Customer master (`V69`+`V70`, 6 endpoint, `PERM_CUSTOMER_*`) — `B136`
  - [x] `G2-2` Supplier: `contactName`/`notes` + path company-scoped + activate — `B141`
  - [x] `G2-3` Settings: filter `plantId` (lỗ hổng R5), `replaceDefaults`, lock, kích hoạt lại pair cũ — `B137`, `B138`
  - [x] `G2-4` Org/Item: `search`/`status`/`type` filter, timezone, Plant có kho ACTIVE, unit ↔ UOM — `B139`, `B140`
  - [x] `G2-5` `mvn -o clean verify` (**1320 unit + 184 IT**, 0/0) + mutation 3/3 + smoke 59 lượt gọi + docs
  - [x] `G2-0` `BE_RESPONSE_GD_02_2026-10-14.md` trả lời R1–R11 + Q1–Q16, mẫu JSON copy từ lượt gọi thật
  - [x] `leadTimeDays` số lẻ (§9.6) — user chốt sửa **toàn hệ thống**; 1325 unit + 184 IT, mutation 2/2, smoke live; `BE_RESPONSE_GD_02_2026-10-14_v2.md`
  - [ ] FE báo A01–A22 đạt
- [x] **`G3`** — FE handoff Giai đoạn 3: nhập Excel dữ liệu nền *(BE xong 2026-10-14, §10 — chờ FE nghiệm thu)*
  - [x] `G3-0` đọc nốt file §10.1 + baseline đo thật (1325 + 184, khớp) — *live `/v1/import-runs` không chạy: đường cũ không đổi, smoke chỉ kiểm nó không thấy batch mới*
  - [ ] Chốt 8 quyết định §10.2 (D1–D8) với user
  - [x] `G3-1` đường mẫu cố định atomic (`V71`, target `SUPPLIER`/`CUSTOMER`, validate/commit/list/detail theo owner)
  - [x] `G3-2` `mvn -o clean verify` (1394 + 192) + mutation 10/10 + smoke 2 lượt × 78 gọi + `BE_RESPONSE_GD_03_2026-10-14.md` + docs
  - [ ] FE nghiệm thu

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

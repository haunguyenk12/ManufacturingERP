# Manufacturing Gap Roadmap – Kế Hoạch Bù Đắp Khoảng Trống So Với Lý Thuyết

> **Bối cảnh:** File này tổng hợp các phần **còn thiếu** của hệ thống khi đối chiếu với:
> 1. Tài liệu lý thuyết *"Phân tích chuyên sâu Manufacturing ERP vs Commercial ERP"*
> 2. Sơ đồ nghiệp vụ mục tiêu `business_flow.jpg`
>
> Sau đó chia thành các **phase độc lập, kiểm soát được**, sắp theo dependency + giá trị.
>
> **Ràng buộc nền tảng:** Hệ thống **CHƯA có tầng OT** (không kết nối trực tiếp máy móc / PLC /
> sensor). Vì vậy roadmap này **chỉ gồm các hạng mục làm được thuần ở tầng ERP**. Các hạng mục
> phụ thuộc OT được tách riêng ở [Mục Deferred](#phase-deferred--phụ-thuộc-ot-chưa-làm) và
> **không nằm trong kế hoạch triển khai hiện tại**.

---

## 1. Nguyên Tắc Chung

- **Tái sử dụng hạ tầng sẵn có:** ưu tiên bám theo pattern đã có (BOM explosion, snapshot line,
  Material Issue/Receipt, Variance service, dynamic RBAC permission) thay vì phát minh mới.
- **Mỗi phase phải build + test PASS độc lập**, không để hệ thống ở trạng thái nửa vời.
- **Không đụng OT:** mọi dữ liệu real-time từ máy móc đều nằm ngoài phạm vi.
- Migration Flyway tiếp nối số hiện tại (mới nhất là `V24` – do `P1` tạo), phase mới bắt đầu từ `V25`.

---

## 2. Bảng Tổng Quan Các Phase

| ✔ | Phase | Tên | Nhóm giá trị | Phụ thuộc | Effort | Cần OT? |
|---|---|---|---|---|---|---|
| **[x]** | **P1** | Approval Workflow & Business Gates | Khớp `business_flow` (4 chốt duyệt) | — | Trung bình | Không |
| **[x]** | **P2** | Quality Control (QC) Module | Khớp `business_flow` (QC + HOLD) | P1 ✅ | Trung bình | Không |
| **[x]** | **P3** | Costing Engine | Khoảng trống lý thuyết lớn nhất | — | Cao | Không |
| **[x]** | **P4** | Routing + Work Center + CRP tĩnh + Labor Time | Master Data + Capacity Check | — | Cao | Không |
| **[x]** | **P5** | Serial Number Tracking | Traceability cấp đơn vị | — | Trung bình–Cao | Không |
| **[~]** | **P6** | Sales Order & Fulfillment + WO Close | Đóng vòng end-to-end | P2 ✅ | Cao | Không |
> `P6`: Sales Order (`F3`) + Fulfillment allocation (`F6`) ✅ **xong 2026-07-28**. Còn lại **WO Close/reconcile** (status `CLOSED` do manager) — chưa xếp lịch.
| **[ ]** | **P-Deferred** | MES/ISA-95, OEE, WIP real-time, CRP động | Industry 4.0 | Tầng OT | — | **Có** |

### 2.1 Bảng Theo Dõi Tiến Độ  *(cập nhật: 2026-07-28)*

> **Quy tắc cập nhật:** phase chỉ được tick `[x]` khi **toàn bộ** Definition of Done của phase đó
> đã đạt và `mvn -o test` PASS. Khi tick xong một phase ⇒ ghi prompt của phase kế tiếp vào
> `NEXT_PHASE_PLAN.md` (file đó luôn chỉ chứa **đúng một** phase đang chạy).

> ⚠️ **Cập nhật 2026-07-26 — track `P*` đang được thực thi qua track `F*`.** Sau khi đối chiếu với
> `OmniPlant_MVP_Production_Backend_Handoff.docx` (đặc tả FE↔BE), thứ tự phase được sắp lại theo
> dependency của spec: `F2`≈`P2` (QC), `F4`≈`P4` (Routing, chỉ phần spec cần), `F3`+`F6`≈`P6`
> (Sales Order tách làm demand và fulfillment). `P3` (Costing) và `P5` (Serial) **không** nằm trong
> MVP của spec. Nguồn sự thật về phase đang chạy: `CLAUDE.md §0.1` + `NEXT_PHASE_PLAN.md`.

**Track nghiệp vụ (`P*` – file này):**

- [x] **P1 – Approval Workflow & Business Gates** — hoàn thành 2026-07-24
  - [x] Gate 1a: release yêu cầu reservation phủ 100%, thiếu ⇒ WO `BLOCKED` + 422
  - [x] Gate 1b: over-issue cần `PERM_MATERIAL_ISSUE_OVERRIDE` + `overrideReason`
  - [x] Gate 1c: receipt `post` → `PENDING_APPROVAL` → `approve` → `POSTED` + lot `HOLD`
  - [x] Migration `V23__create_work_order_approval_gates.sql`, `V24__seed_approval_gate_permissions.sql`
  - [x] Docs cập nhật: `architecture.md`, `system-flow.md`, `roles-and-permissions.md`
  - Chi tiết + 2 giới hạn đã biết: `CLAUDE.md §0.3`
- [x] **P2 – Quality Control (QC) Module** — hoàn thành 2026-07-26 (thực thi dưới tên `F2`)
  - [x] Vòng đời receipt `DRAFT` → `PENDING_APPROVAL` → `APPROVED` (+ `submit` endpoint); `POSTED` đổi tên thành `APPROVED`
  - [x] `POST /production-receipts/{id}/qc-disposition` → lot `HOLD` sang `AVAILABLE`/`REJECTED`, reason bắt buộc
  - [x] Entity `QualityDisposition` (per-lot) + summary `qcResult`/`qcReason`/`qcAt`/`qcBy` trên receipt
  - [x] `MovementType.LOT_STATUS_CHANGE` + `MovementDirection.NONE` (ledger truy xuất, không đổi balance)
  - [x] Permission `PERM_QUALITY_DISPOSITION` (`V27`), chỉ ADMIN/MANAGER — separation of duties
  - [x] Migration `V26__create_quality_control.sql`, `V27__seed_quality_permissions.sql`
  - [x] Docs cập nhật: `CLAUDE.md §0.6`, `roles-and-permissions.md`, `system-flow.md`, `module/{workorder,inventory}/CLAUDE.md`
  - [x] **[`D5`, 2026-07-30]** QC mở cho output **không** lot-tracked: điều kiện rẽ nhánh là cấp dòng
    (`line.lot != null`), phán quyết ghi trên receipt, `REJECTED` rút hàng bằng `ADJUST_OUT`. Trả nợ
    #17 — trước đó thành phẩm không lot-tracked không bao giờ lên `FULFILLED`. Không migration.
    Chi tiết: `CLAUDE.md §0.13`
  - Chi tiết + giới hạn còn lại: `CLAUDE.md §0.6` và nợ #11/#12 ở `§0.4`
  - *Khác thiết kế gốc:* entity tên `QualityDisposition` (không phải `QualityInspection`), permission
    tên `PERM_QUALITY_DISPOSITION` (không phải `PERM_QUALITY_INSPECT`) — theo đúng đặc tả FE §6.2.
- [x] **P3 – Costing Engine** — ✅ **xong 2026-08-05**. Module mới `module/costing`: `ItemStandardCost`
  (upsert, company-scoped) + `CostingService` (BOM cost roll-up đệ quy, cùng khuôn
  `MrpCalculationService`) + `WorkOrderCostAccumulator` (2 hook: `MaterialIssueService`,
  `ProductionExecutionService`). `GET /work-orders/{id}/variance` mở rộng `usageVarianceCost` +
  `costVariance`. **Chỉ Material Usage Variance** — không làm Price Variance (không có cột giá trên
  `stock_movements`). `laborCost`/`overheadCost` nhập tay theo rate cố định, **không** tính từ
  `WorkOrderOperation.runMinutesPerUnit`. Permission `PERM_COSTING_READ`/`_MANAGE` (ADMIN+MANAGER
  only). Bản ghi đầy đủ: `CLAUDE.md §0.31`.
- [x] **P4 – Routing + Work Center + CRP tĩnh** — ✅ **đóng nốt 2026-08-05**. Routing master data xong 2026-07-27 (`F4`); Work Center/Shift/Calendar/CRP thực thi dưới mã `C2-6`/`C2-7`/`C2-8` (track Capstone 2, xem `FRONTEND_ALIGNMENT_ROADMAP.md §8`)
  - [x] Module `module/routing`: `RoutingHeader` + `RoutingOperation`, 1 routing `ACTIVE` / `(company, item)`
  - [x] Snapshot bất biến lên `work_orders` (`source_routing_id/code/version` + `routing_captured_at`)
  - [x] `MISSING_ROUTING` (409) chặn convert proposal MAKE thiếu routing `ACTIVE`
  - [x] `PERM_ROUTING_READ` / `PERM_ROUTING_MANAGE` (`V31`), docs cập nhật
  - [x] `WorkCenter` entity + CRUD/lifecycle, `RoutingOperation.workCenterCode` (string) → FK — **xong 2026-08-05 dưới tên `C2-6`** (per-plant, `capacityUnitType`+`capacityUnits` thay `capacityHoursPerDay`, xem `module/workcenter/CLAUDE.md`)
  - [x] `Shift` + `WorkCalendar` entity + CRUD/lifecycle + `WorkCenter.workCalendarId` FK — **xong 2026-08-05 dưới tên `C2-7`** (net working window nội bộ, chưa endpoint public — xem `module/shift/CLAUDE.md`)
  - [x] `work_order_operations` snapshot + `stage_code` → FK — **xong 2026-07-27 dưới tên `F5-A`** (bảng snapshot copy, bất biến B56; `wip_transactions.work_order_operation_id` thêm cạnh `stage_code`, không bỏ cột cũ vì WO không routing vẫn cần free text)
  - [x] CRP tĩnh (`CapacityBoardService` + `ScheduleAdjustmentService`) + Capacity Board — **xong 2026-08-05 dưới mã `C2-8`** (lịch operation sinh ở `WorkOrderService.release()`, infinite-capacity; Labor **cost** theo operation time vẫn thuộc `P3`, xem `CLAUDE.md §0.30`)
  - [x] Routing `ACTIVE` được kiểm ngay ở tầng MRP: proposal MAKE thiếu routing ⇒ `exceptionState = BLOCKED`
        + message `MISSING_ROUTING`, không đợi đến lúc convert — **xong 2026-07-28 dưới tên `F5-B`**
  - *Khác thiết kế gốc:* field entity là `routingVersion` (không phải `revision`) vì `BaseEntity` đã
    chiếm `version`; operations nhập inline khi create thay vì endpoint line riêng như BOM.
- [x] **P5 – Serial Number Tracking** — hoàn thành 2026-08-06
  - [x] `SerialNumber` entity (mirror `InventoryLot`, mọi movement chạm serial luôn `quantity = 1`) +
        `Item.serialTracked` loại trừ `lotTracked` (service + CHECK constraint, chốt với user)
  - [x] Nối vào `InventoryMovementService` (receive/issue/adjust), `MaterialIssueService`,
        `ProductionReceiptService` (post/approve/qcDisposition) — migration `V52`
  - [x] Quyết định chốt với user: serial-tracked output **không** HOLD chờ QC (mirror non-lot-tracked
        của `D5`, tránh phải viết lại `StockBalanceRepository.aggregate*`)
  - [ ] Goods Receipt (`module/purchasing`) — **chưa nối**, nợ có chủ đích, xem `module/purchasing/CLAUDE.md`
  - Chi tiết + bất biến B96-B99: `CLAUDE.md §0.33`
- [ ] **P6 – Sales Order & Fulfillment + WO Close** — *nửa đầu xong 2026-07-26 (`F3`), nửa sau xong 2026-07-28 (`F6`); chỉ còn WO Close*
  - [x] **[F6]** `WorkOrderDemandAllocation` (`V35`) — nối WO ↔ SO line lúc convert proposal MAKE level-0
  - [x] **[F6]** Fulfillment chỉ chạy khi QC `AVAILABLE` commit (spec §7.1); `REJECTED`/`approve` không chạm
  - [x] **[F6]** Roll-up `IN_PRODUCTION` → `PARTIALLY_FULFILLED` → `FULFILLED`; `WorkOrderResponse.allocations[]`
  - [ ] **WO Close/reconcile** (node 25): status `CLOSED` + bước đối chiếu tường minh do manager — **chưa làm**
  - [x] Module `module/sales`: `SalesOrder` + `SalesOrderLine`, 6 trạng thái, `lineNo` do server cấp
  - [x] `confirm` ⇒ sinh independent demand (`PlanningDemand`, `demandType = SALES_ORDER`); `cancel` ⇒ huỷ demand `OPEN`
  - [x] `GET /sales-orders/planning-demands` — 4 điều kiện eligibility spec §2.1 trong 1 aggregate query
  - [x] Permission `PERM_SALES_ORDER_READ` / `PERM_SALES_ORDER_MANAGE` (`V29`), OPERATOR chỉ READ
  - [x] Migration `V28__create_sales_order.sql`, `V29__seed_sales_permissions.sql`
  - [x] Docs: `CLAUDE.md §0.7`, `module/sales/CLAUDE.md` (B43-B47), `roles-and-permissions.md`, `system-flow.md`
  - [ ] **Nửa sau (`F6`)**: fulfillment allocation (`fulfilledQuantity` tăng khi QC `AVAILABLE`), WO Close
- [ ] **P-Deferred** — chờ tầng OT, không lên lịch

**Track kiểm thử (`T*` – chi tiết ở `TEST_IMPROVEMENT_PLAN.md`):**

- [x] **T0 – JaCoCo & coverage baseline** (line 62.3% / branch 48.9%)
- [x] **T1 – Method-security hardening** — hoàn thành (218 case)
- [x] **T2 – Controller / GlobalExceptionHandler contract** — hoàn thành 2026-07-25 (281 case; 3/18 controller đại diện, xem `TEST_IMPROVEMENT_PLAN.md §0`)
- [x] **T3 – Auth core** (`refresh`/`logout`, `TokenStoreService`, `JwtTokenProvider`) — hoàn thành 2026-07-25 (257 case)
- [x] **T4 – Testcontainers + JPQL RBAC + migration** — hoàn thành 2026-07-25
- [x] **T5 – Dọn over-mocking, tên test sai** — hoàn thành 2026-07-25 (281 case)

> **Vì sao `P2` bị hoãn:** `P2` thêm `PERM_QUALITY_*` vào đúng vùng mà `T1` đang vá.
> Làm `P2` trước ⇒ permission mới cũng bị "test" bằng pattern vô hiệu. Xong `T1` rồi mới mở `P2`.

> **Thứ tự khuyến nghị:** P1 → P2 trước (rẻ, khớp trực tiếp sơ đồ mới nhất, mở khóa QC).
> P3–P6 **có thể hoán đổi thứ tự theo ưu tiên nghiệp vụ** — nếu ưu tiên báo cáo giá thành thì
> kéo **P3 (Costing) lên làm trước**, vì nó standalone và là điểm phân biệt cốt lõi
> Manufacturing vs Commercial ERP.

### Sơ đồ phụ thuộc

```text
P1 (Gates/Approval) ──► P2 (QC) ──► P6 (Sales Order & Close)
P3 (Costing)        ── độc lập
P4 (Routing/CRP)    ── độc lập  (unlock: stage_code có nghĩa, Labor cost cho P3)
P5 (Serial)         ── độc lập
```

---

## 3. Chi Tiết Từng Phase

### P1 — Approval Workflow & Business Gates  ✅ **ĐÃ HOÀN THÀNH (2026-07-24)**

> Kết quả thực tế: 3 gate hoạt động, migration `V23`+`V24`, `WorkOrderStatus` có thêm `BLOCKED`,
> `ProductionReceiptStatus` có thêm `PENDING_APPROVAL`/`REJECTED`.
> **Khác thiết kế ban đầu:** không thêm status `PLANNED` (trùng nghĩa `DRAFT`) và chọn
> **phương án nhẹ** cho gate 1b (permission override, không thêm `PENDING_APPROVAL` cho material issue).
> Hai giới hạn đã biết ảnh hưởng trực tiếp `P2`: xem `CLAUDE.md §0.3`.

**Mục tiêu:** Bổ sung 3 chốt kiểm soát trong `business_flow.jpg` mà code hiện tại chưa có.
Hiện hệ thống **chưa có bất kỳ approval-workflow nào**, nên phase này cũng đặt nền cho cơ chế
duyệt dùng lại về sau.

| # | Gate (theo `business_flow`) | Hiện trạng | Việc cần làm |
|---|---|---|---|
| 1a | **Reserve đủ 100% mới cho Release** (node 12) | `release()` chỉ check component lines không rỗng | Thêm status `PLANNED`/`BLOCKED` vào `WorkOrderStatus`; `WorkOrderService.release()` tính tỷ lệ reserved theo từng component line, chưa đủ → chặn / giữ `BLOCKED` |
| 1b | **Xuất vượt BOM → duyệt Material Variance** (node 15) | `support.ensureDoesNotExceed()` ném exception, chặn cứng | **Phương án nhẹ (khuyến nghị):** thêm permission `PERM_MATERIAL_ISSUE_OVERRIDE`; nếu có quyền + có `overrideReason` thì cho vượt, ghi audit mức WARNING. **Phương án đầy đủ (tùy chọn):** thêm status `PENDING_APPROVAL` + endpoint `approve` |
| 1c | **Duyệt Production Receipt → output vào HOLD** (node 22) | Receipt post thẳng `POSTED`, lot ra `AVAILABLE` ngay | Thêm bước duyệt; khi duyệt, output lot tạo ở `LotStatus.HOLD` thay vì `AVAILABLE` (mở khóa cho P2 QC) |

**Thay đổi chính:**
- `WorkOrderStatus`: thêm `PLANNED`, `BLOCKED` (+ cập nhật `chk_work_orders_status`).
- `MaterialIssueStatus` / `ProductionReceiptStatus`: cân nhắc `PENDING_APPROVAL` (nếu chọn phương án đầy đủ).
- `InventoryMovementService.resolveReceiveLot()`: cho phép chỉ định lot status khởi tạo (`HOLD` cho output sản xuất).
- Seed permission mới cho các quyền override/approve.

**Definition of Done:** 3 gate hoạt động; test cho từng nhánh (đủ/thiếu reservation, vượt/không vượt BOM, duyệt/từ chối receipt); build + test PASS.

**Effort:** Trung bình. **OT:** Không.

---

### P2 — Quality Control (QC) Module

**Mục tiêu:** Thêm bước QC sau Production Receipt như node 23 trong `business_flow`:
output `HOLD` → (Accept) `AVAILABLE` / (Reject) `REJECTED`.

**Thuận lợi:** `LotStatus` **đã có sẵn** đủ giá trị (`AVAILABLE`, `HOLD`, `REJECTED`, `EXPIRED`) —
chỉ thiếu workflow điều khiển transition.

**Thay đổi chính:**
- Entity mới `QualityInspection`: `inspectionId`, tham chiếu `production_receipt_line` / `lot`,
  `result` (ACCEPTED/REJECTED), `inspectedBy`, `note`, `inspectedAt`.
- Migration `V2x__create_quality_control.sql`.
- `QualityControlService`: endpoint `accept` → lot sang `AVAILABLE`; `reject` → lot sang `REJECTED`
  (không cho bán/không cộng available).
- Permission `PERM_QUALITY_INSPECT`.

**Phụ thuộc:** P1 (1c) — output phải nằm ở `HOLD` trước thì QC mới có gì để duyệt.

**Definition of Done:** post receipt → lot HOLD → QC accept/reject chuyển đúng trạng thái, phản ánh đúng available quantity; test đủ 2 nhánh; build + test PASS.

**Effort:** Trung bình. **OT:** Không.

---

### P3 — Costing Engine ⭐ (khoảng trống lý thuyết lớn nhất)

> ✅ **Đã xong (2026-08-05).** Phần dưới đây là **thiết kế phác thảo gốc**, giữ lại làm lịch sử —
> triển khai thật lệch vài chỗ, đã chốt với user trước khi viết kế hoạch chi tiết:
> - `ItemStandardCost` **không** có `effectiveDate`/version-theo-thời-gian — upsert một dòng/item
>   (không có "cost tại một thời điểm trong quá khứ" nào cần dùng lúc này, thêm sẽ là speculative).
>   Company-scoped, **không** per-plant như bản nháp gợi ý.
> - **Bỏ hẳn Material Price Variance**, chỉ làm Material Usage Variance — `stock_movements` không có
>   cột giá, và giá duy nhất trong hệ thống (`PurchaseOrderLine.unitPrice`) tách rời khỏi ledger xuất
>   kho.
> - `WorkOrderCostAccumulator` cộng dồn material cost ở `MaterialIssueService.postNew` (đúng như bản
>   nháp) nhưng cộng dồn labor/overhead ở `ProductionExecutionService.reportNew` (báo sản lượng),
>   **không phải** lúc `ProductionReceiptService` hoàn tất — khớp với đảo ngược ngữ nghĩa `F5`
>   (`CLAUDE.md §0.5`): chính execution làm WO tiến triển, không phải receipt.
>
> Bản ghi đầy đủ (thiết kế thật, bất biến B91-B94, breaking changes): `CLAUDE.md §0.31`.

**Mục tiêu:** Trả lời được câu hỏi *"sản xuất lô này tốn bao nhiêu tiền"* — hiện tại `Item` không
có field chi phí nào, `WorkOrderVarianceService` chỉ so sánh **số lượng**, chưa có tiền.

**Thay đổi chính:**
1. **Entity `ItemStandardCost`** (tách khỏi `Item` vì cost theo company/plant + có hiệu lực theo
   thời gian): `materialCost`, `laborCost`, `overheadCost`, `effectiveDate`, `version`.
2. **BOM cost roll-up** — `CostingService.calculateStandardCost(item)` đệ quy y hệt cách
   `MrpCalculationService` bung BOM:
   `cost(FG) = Σ(component.standardCost × quantityPer × (1 + scrapRate)) + labor + overhead của chính FG`.
3. **Actual costing** — `WorkOrderCostAccumulator` (bảng mới, 1-1 với `WorkOrder`): mỗi lần
   `MaterialIssueService` post → cộng dồn `issuedQuantity × standardCost`. Khi
   `ProductionReceiptService` hoàn tất → chốt actual cost / đơn vị.
4. **Variance theo tiền** — mở rộng `WorkOrderVarianceService` (đang có variance số lượng) để thêm
   Material Price Variance & Material Usage Variance, tái dùng `issuedQuantity`/`requiredQuantity`
   nhân với cost.

**Ghi chú:** `laborCost`/`overheadCost` giai đoạn này có thể là **rate phẳng khai báo tay** trên
`ItemStandardCost`; khi có P4 (Routing) thì có thể tính labor chính xác hơn theo operation time —
nhưng **P3 không phụ thuộc P4**, làm trước được.

**Definition of Done:** roll-up standard cost cho BOM đa cấp đúng; WO tích lũy actual cost; báo cáo
variance có cột tiền tệ; test tính toán chi phí + variance; build + test PASS.

**Effort:** Cao. **OT:** Không. Tái dùng nhiều nhất hạ tầng (BOM explosion + Variance service).

---

### P4 — Routing + Work Center + CRP Tĩnh + Labor Time

> ✅ **Đã xong hết (2026-08-05, dưới mã `C2-6`/`C2-7`/`C2-8`).** Phần dưới đây là **thiết kế phác thảo
> gốc**, giữ lại làm lịch sử — triển khai thật lệch vài chỗ: `WorkCenter.capacityHoursPerDay` →
> `capacityUnitType`+`capacityUnits`; `CapacityCalculationService` chạy trong `MrpRunService` → tách
> thành `CapacityBoardService` (read riêng, không đụng MRP) + `ScheduleAdjustmentService`; đánh dấu
> `OVER_CAPACITY` ở MRP → cờ `overload` trên Capacity Board (một read độc lập, không phải nhánh của
> MRP run). Bản ghi thật: `CLAUDE.md §0.28`-`§0.30`, `FRONTEND_ALIGNMENT_ROADMAP.md §8.6`-`§8.7b`.

**Mục tiêu:** Bổ sung Master Data còn thiếu (`Routing`, `Work Center` — node 2) và Capacity Check
tĩnh (node 5/6/7), làm cho `wip_transactions.stage_code` thực sự có nghĩa.

**Thay đổi chính:**
- **Master data mới** (theo pattern `BomHeader`/`BomLine`):
  - `WorkCenter`: `workCenterId`, `plant`, `code`, `name`, `capacityHoursPerDay`, `status`.
  - `Routing`: giống `BomHeader` — `item`, `revision`, `status` (ACTIVE/DRAFT).
  - `RoutingOperation`: `routing`, `operationNo`, `workCenter`, `standardSetupTime`,
    `standardRunTimePerUnit`, `sequence`.
- **Snapshot khi release:** `WorkOrder.release()` snapshot thêm `WorkOrderOperationLine` (giống
  `snapshotComponentLines()` snapshot từ `BomLine`), mỗi dòng status `PENDING/IN_PROGRESS/DONE`.
- **`stage_code` → FK** trỏ tới `WorkOrderOperationLine` thay vì `VARCHAR` tự do.
- **Labor time** (node 19): ghi nhận thời gian thực hiện theo operation (nhập tay, không cần OT).
- **CRP tĩnh** — `CapacityCalculationService` chạy song song `MrpCalculationService` trong
  `MrpRunService`: `Σ(standardRunTimePerUnit × plannedQuantity)` theo `WorkCenter` theo ngày/tuần,
  so với `capacityHoursPerDay × số ngày làm việc` → đánh dấu `OVER_CAPACITY` (giống cách
  `MrpRequirementStatus` đánh dấu COVERED/thiếu).

**Lưu ý phân biệt với OT:** đây là **CRP tĩnh** — chỉ so *kế hoạch* với *công suất khai báo sẵn*,
không đọc trạng thái máy real-time. CRP động (theo tải máy thực) thuộc phần Deferred.

**Definition of Done:** tạo được Routing/WorkCenter; WO snapshot operation lines; CRP report cảnh
báo over-capacity; test roll-up giờ tải; build + test PASS.

**Effort:** Cao. **OT:** Không (chỉ CRP tĩnh).

---

### P5 — Serial Number Tracking

> ✅ **Đã xong (2026-08-06).** Phần dưới đây là **thiết kế phác thảo gốc**, giữ lại làm lịch sử —
> triển khai thật lệch một chỗ quan trọng, đã chốt với user trước khi viết kế hoạch chi tiết:
> `MaterialIssueLineRequest` **không** đổi sang nhận danh sách `serialId` như phác thảo dưới đây dự
> đoán — mỗi movement chạm serial luôn `quantity = 1` nên chỉ cần thêm field optional `serialId`, và
> N đơn vị dùng N dòng trong `lines[]` đã có sẵn (**không phải breaking change**). Quyết định thứ hai
> chốt với user: serial-tracked output **không** có HOLD chờ QC (mirror non-lot-tracked của `D5`),
> tránh phải thêm `serial_id` vào `stock_balances` + viết lại `StockBalanceRepository.aggregate*`.
> Chi tiết đầy đủ + bất biến B96-B99: `CLAUDE.md §0.33`.

**Mục tiêu:** Truy vết cấp **từng đơn vị** (Rule 4 – Serial), bổ sung bên cạnh Lot đã có.

**Thay đổi chính:**
- Entity mới `SerialNumber` (song song `InventoryLot`): `serialId`, `item`, `serialCode` (unique),
  `status` (AVAILABLE/ISSUED/SOLD…).
- Thêm `serialId` (nullable FK) vào `stock_movements`, `material_issue_lines`,
  `production_receipt_lines` — đúng pattern `lot_id`.
- Thêm cờ `Item.serialTracked` song song `lotTracked`.

**Điểm khó (không chỉ là thêm cột):** Lot theo lô (nhiều đơn vị / 1 mã), Serial theo từng đơn vị
(1 sản phẩm = 1 mã). Khi `ProductionReceipt` nhận 100 sản phẩm serial-tracked → phải sinh **100
record Serial**. `MaterialIssueLineRequest` hiện nhận 1 `quantity` (BigDecimal) — với serial phải
đổi sang nhận **danh sách `serialId`**, là thay đổi API/DTO đáng kể.

**Definition of Done:** item serial-tracked; receipt sinh serial theo đơn vị; issue theo danh sách
serial; test; build + test PASS.

**Effort:** Trung bình–Cao. **OT:** Không.

---

### P6 — Sales Order & Fulfillment + WO Close/Reconcile

**Mục tiêu:** Đóng vòng end-to-end như đầu và cuối `business_flow` (node 3, 10, 24, 25). Hiện chỉ
có `PlanningDemand` với enum type `SALES_ORDER` — chưa có entity SO thật.

**Thay đổi chính:**
- Module `sales` mới: `Customer`, `SalesOrder`, `SalesOrderLine` (item, quantity, dueDate, fulfilledQuantity).
- **SO → demand:** SO sinh `PlanningDemand` (type `SALES_ORDER`) làm đầu vào MRP.
- **SO ↔ WO allocation** (node 10): liên kết nhu cầu SO với Work Order phục vụ nó.
- **Fulfillment update** (node 24): khi output QC-accepted (`AVAILABLE`) → cập nhật
  `fulfilledQuantity` của SO line.
- **WO Close/reconcile** (node 25): thêm bước đối chiếu tường minh + status `CLOSED` (hiện chỉ có
  auto-`COMPLETED` theo số lượng, không có bước close do manager).

**Phụ thuộc:** P2 (QC) — fulfillment chỉ tính output đã QC-accepted.

**Definition of Done:** tạo SO → MRP nhận demand → WO phục vụ SO → output accepted cập nhật
fulfillment → WO close khi reconcile đủ; test luồng; build + test PASS.

**Effort:** Cao. **OT:** Không.

---

## 4. Ghi Chú: Hạng Mục KHÔNG Khuyến Nghị

- **Ép Lot/Serial "bắt buộc toàn hệ thống":** cơ chế lot đã có sẵn ở mức "tùy chọn theo Item"
  (`Item.lotTracked`). Ép `true` toàn bộ chỉ đổi 1 nhánh validation, nhưng **không nên làm** — thiết
  kế tùy chọn phản ánh đúng thực tế hơn (không ai lot-track từng con ốc vít). Giữ nguyên.

---

## Phase Deferred — Phụ Thuộc OT (CHƯA làm)

> Các hạng mục dưới đây **bị chặn vì hệ thống chưa có tầng OT**. Có code cũng vô dụng khi chưa có
> nguồn dữ liệu real-time từ máy móc để nuôi. **Không nằm trong kế hoạch triển khai hiện tại** —
> chỉ ghi lại để không bỏ sót khi có OT.

| Hạng mục | Vì sao phải chờ OT |
|---|---|
| **MES Integration (ISA-95)** | Bản chất MES là cầu nối ERP ↔ máy móc; không có OT thì không có gì để đọc/ghi real-time |
| **OEE** (Availability × Performance × Quality) | Cần downtime, tốc độ máy, tỷ lệ lỗi real-time — chỉ máy biết |
| **WIP tracking real-time theo công đoạn** | Cần tín hiệu máy báo "đang chạy công đoạn nào" thay vì nhập tay |
| **CRP động** (điều độ theo tải máy thực) | Cần biết máy rảnh/bận theo thời gian thực |

---

## 5. Tóm Tắt Ưu Tiên

> Trạng thái hiện tại: **P1 xong** → đang chèn `T0+T1` (test hardening) trước khi mở `P2`.
> Xem bảng theo dõi ở §2.1.

1. ~~**Làm ngay (rẻ, khớp `business_flow` mới nhất):** P1 → P2.~~ → P1 ✅, P2 chờ `T1` xong.
2. **Giá trị lý thuyết cao nhất, standalone:** P3 (Costing) — cân nhắc kéo lên làm sớm nếu ưu tiên báo cáo giá thành.
3. **Mở rộng năng lực sản xuất:** P4 (Routing/CRP tĩnh), P5 (Serial).
4. **Đóng vòng end-to-end:** P6 (Sales Order + Close).
5. **Chờ OT:** toàn bộ Phase Deferred.

# module/costing — Business Rule Invariants

> Thêm ở `P3` (2026-08-05, `NEXT_PHASE_PLAN.md` §2 → `MANUFACTURING_GAP_ROADMAP.md §3`). Chỉ nạp
> khi agent làm việc trong `module/costing/**` hoặc sửa `WorkOrderCostAccumulator`/
> `WorkOrderVarianceService` liên quan tới cost.

Standard costing (`ItemStandardCost`) + BOM cost roll-up (`CostingService`) + actual cost accumulation
trên work order (`WorkOrderCostAccumulator`, nằm trong `module/workorder` — xem lý do ở mục "Vị trí
code" cuối file). Bảng `item_standard_costs` + `work_order_cost_accumulators`, migration `V50`
(schema) + `V51` (seed permission).

**Hai quyết định chốt với user trước khi viết kế hoạch:** (1) `laborCost`/`overheadCost` nhập tay
theo rate cố định trên `ItemStandardCost` — **không** tính từ `WorkOrderOperation.runMinutesPerUnit`
× rate/phút (đơn giản hơn, không khoá costing vào dữ liệu routing/work-center). (2) **Không làm
Material Price Variance** — `stock_movements` không có cột giá, và dữ liệu giá duy nhất trong hệ
thống (`PurchaseOrderLine.unitPrice`) tách rời khỏi ledger xuất kho; tính "giá thực trả cho vật tư đã
xuất cho WO này" đòi một lớp actual-costing (FIFO/weighted-average) chưa tồn tại và không ai yêu cầu.
Phase này chỉ làm **Material Usage Variance** (chênh lệch số lượng × standard cost, ra `$`).

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B91 | `CostingService.calculateStandardCost(companyId, itemId)` đệ quy theo BOM `ACTIVE`, cùng khuôn `MrpCalculationService.expandChildren`: có BOM ⇒ `materialCost = Σ(component.totalCost × quantityPer × (1 + scrapRate))`; không có BOM ⇒ `materialCost` = field riêng của item. `laborCost`/`overheadCost` **luôn** đọc từ `ItemStandardCost` của **chính item đó** — không bao giờ roll-up từ BOM, kể cả khi item có BOM (quyết định #1 ở trên: rate thủ công là flat, không đệ quy) | `CostingServiceTest.calculateStandardCost_manufacturedItem_rollsUpMaterialFromComponents`, `.calculateStandardCost_manufacturedItem_ignoresOwnMaterialCostField` |
| B92 | Thiếu `ItemStandardCost` (chưa ai nhập) ⇒ mọi field mặc định `ZERO`, **không** throw. Cùng hình dạng "thiếu config trả về mặc định" đã có ở `PlanningSettingSource.SYSTEM_DEFAULT` (`module/planning`) — một item chưa được định giá không được làm sập roll-up của cha nó | `CostingServiceTest.calculateStandardCost_noStandardCostRow_defaultsToZero`, `WorkOrderCostAccumulatorServiceTest.accumulateMaterialCost_noStandardCostConfigured_contributesZero` |

## Quyết định thiết kế cần nhớ

### 1. `ItemStandardCostLookupService` — entry point duy nhất cho `workorder` (rule C7)

`workorder` **không** được đụng `ItemStandardCostRepository`/`CostingService` trực tiếp. Ba
method của lookup service, mỗi cái phục vụ đúng một việc — **đừng gộp lại**:

| Method | Dùng ở đâu | Vì sao khác nhau |
|---|---|---|
| `findStandardUnitCost(companyId, itemId)` | `WorkOrderCostAccumulatorService.accumulateMaterialCost` (component bị issue), `WorkOrderVarianceService.toVarianceLine` (`usageVarianceCost`) | Cost **fully-loaded** (material+labor+overhead, roll-up qua `CostingService`) của **một đơn vị component** — nếu component tự nó là hàng lắp ráp, cost này đã gồm cả labor/overhead của chính nó |
| `findLaborOverheadCost(itemId)` | `WorkOrderCostAccumulatorService.accumulateLaborOverheadCost` (product item của chính WO) | Rate **thẳng** (không roll-up) từ `ItemStandardCost` của item — quyết định #1: labor/overhead không bao giờ đệ quy |
| `findStandardCostBreakdown(companyId, itemId)` | `WorkOrderVarianceService.toCostVariance` (baseline `standardMaterialCost`/`standardLaborCost`/`standardOverheadCost`) | Cần **tách** 3 thành phần để hiển thị, không chỉ tổng |

### 2. `WorkOrderCostAccumulator` cố ý nằm trong `module/workorder`, không phải `module/costing`

Cùng lý do `ProductionExecution`/`QualityDisposition` nằm trong `workorder`: nó thuộc aggregate
Work Order (một dòng/WO, sống chết theo WO), không phải master data của `costing`. `costing` chỉ
export `ItemStandardCostLookupService` để `workorder` tự cộng dồn.

### 3. Hai hook tích luỹ — nơi và điều kiện chặn double-count

- **Material**: `MaterialIssueService.postNew`, bên trong `if (movementResult.created())` — **cùng
  điều kiện** bảo vệ `componentLine.addIssuedQuantity`/`workOrder.markInProgress` khỏi replay theo
  `Idempotency-Key`. Không thêm guard riêng: guard đã có sẵn đúng chỗ.
- **Labor/overhead**: `ProductionExecutionService.reportNew`, sau `workOrder.reportProduction(...)`,
  chỉ khi `good > ZERO` — scrap/rework-only report (`good = 0`) không tạo dòng
  `WorkOrderCostAccumulator` nào cả, không phải tạo dòng rồi cộng 0. Idempotency của report đã được
  `report()` xử lý **một tầng trên** `reportNew` (replay trả response cũ, `reportNew` không chạy lại)
  nên hook không cần guard riêng.

### 4. `findOrCreate` — không gọi `save()` thừa sau khi mutate

`WorkOrderCostAccumulatorService.findOrCreate` trả về entity **đã managed** trong transaction của
caller — `addMaterialCost`/`addLaborAndOverheadCost` mutate qua setter, JPA dirty-checking tự flush
lúc commit. **Không** gọi `accumulatorRepository.save(...)` lần hai sau đó (cùng cách
`WorkOrderComponentLine.addIssuedQuantity` không cần save lại ở `MaterialIssueService`).

### 5. Permission: `hasResourceAccess(..., 'COMPANY', companyId)`, KHÔNG phải `hasPermission` như UOM

🔴 **Cố ý khác `module/uom`.** `UomService` dùng `PermissionGuard.hasPermission(auth, code)` —
cơ chế chỉ đọc assignment `scope_type = GLOBAL`, và trong dữ liệu hiện tại **chỉ `admin`** có
assignment đó (xem `module/uom/CLAUDE.md` "Phát hiện khi kiểm chứng qua HTTP thật" — `manager.a`/
`operator.a` bị 403 trên mọi endpoint UOM dù đã được cấp quyền). `ItemStandardCostService` tránh
đúng cái bẫy đó bằng cách dùng `hasResourceAccess(auth, code, 'COMPANY', #companyId)` — cùng cơ chế
`BomService.createBom`/`RoutingService.create` đã dùng, xác nhận qua smoke test HTTP thật: `admin`
(scope `GLOBAL_ALL`) tạo/đọc được `ItemStandardCost` bình thường, và cơ chế này đọc scope `COMPANY`
nên một tài khoản `MANAGER` với assignment scope `COMPANY` (khác `PLANT` như seed `manager.a` của
`C2-5`) sẽ dùng được — không lặp lại giới hạn của UOM.

### 6. `PERM_COSTING_READ`/`_MANAGE`: ADMIN+MANAGER, KHÔNG có OPERATOR — khác mọi permission `C2-*` gần đây

Xác nhận bằng cách đọc `V17__seed_manufacturing_execution_permissions.sql`:
`PERM_WORK_ORDER_VARIANCE_READ` (permission mà cost figures của phase này gắn thêm vào, qua
`GET /work-orders/{id}/variance`) đã luôn ADMIN+MANAGER only từ trước — không phải 1 trong 12
permission mà `V41`/`C2-5` phải sửa vì lỡ chỉ cấp cho `ADMIN`. Cost data được coi là nhạy cảm, khác
Work Center/Shift (`OPERATOR` cần đọc để vận hành hàng ngày). `FlywayMigrationIT.migrate_v51_*` là
test **cấm** thuần (OPERATOR không có **cả hai** quyền) — `PermissionCatalogTest` chỉ chứng minh
permission tồn tại, không chứng minh được cấp cho role nào (bài học lặp lại từ `C2-5`/`C2-3`/`C2-6`).

### 7. `ItemStandardCostRepository.search` không có `*IT` — có chủ đích

Query chỉ so `company_id`/`item_id` bằng `=`/`is null`, không có `concat`/`like` trên `String`
nullable nên không dính bẫy "lower(bytea)" (`CLAUDE.md §0.24`). Cùng lý do `WorkCenterRepository`
không có `*IT` (`C2-6`) — mock repository là đủ, rule R7 không áp dụng.

### 8. `totalStandardCost` trên `ItemStandardCostResponse` tính lúc đọc, không lưu

`CostingMapper.toResponse` gọi `CostingService.calculateStandardCost` mỗi lần map — không cache,
không lưu cột. Sửa một BOM line thì mọi `GET .../standard-cost` của item cha (và mọi item cha của
item đó) phản ánh ngay, không cần "chạy lại costing". Đánh đổi: mỗi request `list`/`get` tốn thêm
truy vấn đệ quy — chấp nhận được vì đây là read hiếm (master data, không phải hot path).

## Vị trí code

`ItemStandardCost`/`CostingService`/`ItemStandardCostLookupService`/`ItemStandardCostService`/
`ItemStandardCostController` nằm trong `module/costing` — module mới, focused, cùng khuôn `uom`/
`workcenter`/`shift`. `WorkOrderCostAccumulator` + `WorkOrderCostAccumulatorService` nằm trong
`module/workorder/domain` + `module/workorder/service/execution` (xem mục 2).

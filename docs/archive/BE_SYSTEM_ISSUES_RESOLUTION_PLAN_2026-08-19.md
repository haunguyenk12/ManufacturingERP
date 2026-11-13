# OmniPlant Backend — Kế hoạch contract và nghiệp vụ cho 12 vấn đề hệ thống

> Ngày lập: 2026-08-19  
> Trạng thái: Approved for planning — Product Owner đã chốt các quyết định ngày 2026-08-19  
> Đối tượng: Backend team, Product Owner và Frontend integration owner  
> Baseline: Capstone 2 production loop; Purchasing, detailed costing và MES vẫn deferred

## 1. Mục tiêu

Cung cấp contract và transaction semantics ổn định để Frontend xử lý 12 vấn đề mà không tự tính MRP,
tự hợp nhất lot hoặc mô phỏng approval. Ưu tiên cao nhất là ngăn trạng thái dữ liệu không thể phục hồi:
Receipt đã post HOLD nhưng QC thất bại vì lot duplicate, MRP netting sai kho và Over-BOM post stock trước
khi được phê duyệt. Serial Tracking được defer khỏi đợt này.

Tài liệu FE liên quan: `plans/FE_SYSTEM_ISSUES_RESOLUTION_PLAN_2026-08-19.md`.

## 2. Phạm vi Backend theo mã vấn đề

| ID | Backend deliverable |
|---|---|
| `ISS-01` | Sales Order list search contract và index/query behavior |
| `ISS-02` | Không bắt buộc đổi Inventory nếu Item search + `itemId` đáp ứng; xác nhận performance |
| `ISS-03` | Không đổi numeric precision/serialization chỉ để phục vụ display |
| `ISS-04` | Xác nhận reservation/BOM quantity fields; bổ sung Over-BOM approval lifecycle nếu thiếu |
| `ISS-05` | Không xóa `sourceWipTraceIds`; đây là genealogy field |
| `ISS-06` | Thiết kế MRP Plant-scoped, per-requirement warehouse resolution |
| `ISS-07` | Công bố netting formula, eligible lots, exception state và BUY semantics |
| `ISS-08` | Lot uniqueness policy, validation timing và atomic Approval/QC behavior |
| `ISS-09` | Aggregate balance/lot response đủ để không gây lặp khó hiểu và có received/source time |
| `ISS-10` | Deferred; không thêm `SERIAL_TRACKED` hoặc serial API trong đợt này |
| `ISS-11` | Công bố nguồn variance; không tuyên bố Cost authoritative khi costing deferred |
| `ISS-12` | Execution disposition invariants, rework linkage và material consumption flow |

## 3. Quy ước contract chung

1. Giữ standard API root `/api/v1/**`; Sales Order tiếp tục special root `/api/sales-orders/v1/**` nếu
   controller hiện tại không được Product Owner yêu cầu đổi.
2. UUID cho entity IDs; quantity/cost/rate dùng decimal precision hiện tại, không gửi display-ready string.
3. Response dùng envelope chuẩn; page có `content/page/size/totalElements/totalPages`.
4. Mutation có `Idempotency-Key`; same key + same payload replay cùng result, same key + different payload lỗi.
5. Stable error code, field errors và `X-Trace-Id` cho mọi validation/lifecycle conflict.
6. Command phải kiểm tra permission, Plant scope, current state và optimistic version tại server.
7. Mọi stock posting và lifecycle transition liên quan phải nằm trong transaction atomic.
8. OpenAPI và API guide phải cập nhật trong cùng deploy với implementation.

## 4. Product decisions đã chốt

| Decision | Quyết định có hiệu lực |
|---|---|
| `DEC-01` | Một combined search cho `orderNo`/`customerName` |
| `DEC-02` | Balance chính aggregate theo `Item + Warehouse`; lot ở detail |
| `DEC-03` | Run Warehouse là kho đáp ứng/nhận FG; component resolve kho riêng theo Plant policy |
| `DEC-04` | BUY read-only; không có Approve/Reject hoặc stock side effect |
| `DEC-05` | Lot unique theo `Company + Item`; duplicate chặn trước Approval; partial reuse explicit |
| `DEC-06` | Defer Serial Tracking; scope chỉ `NON_TRACKED`/`LOT_TRACKED` |
| `DEC-07` | UI summary chỉ giữ Output và WIP; trace còn lại giữ cho audit nếu đã có |
| `DEC-08` | Rework trên cùng WO; extra material dùng reason `REWORK` và Over-BOM flow |
| `DEC-09` | Operator request, Manager approve mới post; reject bắt buộc reason |

## 5. Kế hoạch triển khai Backend

### Phase BE-0 — Contract baseline và dữ liệu acceptance

1. Chụp Runtime OpenAPI hiện tại; liệt kê endpoint/entity/table bị ảnh hưởng.
2. Tạo fixtures tối thiểu:
   - Sales Orders có orderNo/customerName tương tự để test search;
   - BOM ba cấp `FG → SF → RM` với `WH-FG`, `WH-SF`, `WH-RM`;
   - nhiều lot AVAILABLE/HOLD/REJECTED/EXPIRED cho cùng Item;
   - Receipt partial và duplicate lot;
   - Execution có Good/Scrap/Rework và rework material.
3. Viết contract decision record cho `DEC-01` đến `DEC-09`.
4. Xác định migration backward compatibility và deployment order.

### Phase BE-1 — Search và read models (`ISS-01`, `ISS-02`, `ISS-09`)

#### BE-1A — Sales Order search

Nếu `DEC-01` chọn combined search:

```http
GET /api/sales-orders/v1?companyId=&plantId=&status=&search=&page=&size=
```

Semantics đề xuất:

- trim search; empty tương đương không filter;
- case-insensitive contains trên `orderNo` và `customerName`;
- vẫn áp Company/Plant scope trước search;
- deterministic sort và pagination;
- không hydrate detail theo từng row nếu có thể tránh N+1.

Nếu chọn hai field, dùng `orderNo` và `customerName` với semantics được OpenAPI mô tả rõ.

#### BE-1B — Stock Item search

1. Xác nhận Item list `search` hỗ trợ code/name, pagination và index phù hợp.
2. Giữ Balance filter `warehouseId + itemId`; không cần thêm free-text nếu FE dùng Item autocomplete.
3. Kiểm thử Plant/company isolation khi Item được tìm ở Company khác.

#### BE-1C — Inventory balance/lot read model

Theo `DEC-02`, ưu tiên endpoint balance chính trả một row cho `Item + Warehouse`:

```text
onHandQuantity
reservedQuantity
availableQuantity
qualityHoldQuantity
rejectedQuantity (nếu business cần hiển thị riêng)
expiredQuantity (nếu business cần hiển thị riêng)
lotCount
updatedAt
```

Lot list/detail tiếp tục trả per-lot balances và bổ sung/chuẩn hóa:

```text
lotId, lotCode, itemId, warehouseId, status
onHand, reserved, available
receivedAt/sourceAt, manufactureDate, expiresAt
sourceReferenceType, sourceReferenceId
```

Không để FE suy ra lot status từ `availableQuantity > 0` trong contract dài hạn.

### Phase BE-2 — Material Issue và Over-BOM (`ISS-04`, một phần `ISS-12`)

#### BE-2A — Quantity invariants

Response reservation/component phải đủ các field authoritative:

```text
requiredQuantity
reservedQuantity
issuedQuantity
remainingQuantity
reservation.reservedQuantity
reservation.issuedQuantity
reservation.remainingQuantity
```

Normal Issue invariant:

```text
quantity > 0
quantity <= reservation.remainingQuantity
quantity <= eligible available/reserved stock
sum normal issued <= component required quantity
```

Server phải recheck trong transaction; FE quick-fill chỉ là convenience, không phải security boundary.

#### BE-2B — Over-BOM state machine

Nếu `DEC-09` chọn request/approve, đề xuất:

```text
DRAFT/PENDING_APPROVAL → APPROVED → POSTED
                       ↘ REJECTED
```

- Operator request chứa WO, component, warehouse, lot khi Item yêu cầu, extra quantity, reason và source type
  (`REWORK`, `PROCESS_LOSS`, `DAMAGE`, `SETUP_LOSS`, `OTHER`).
- Tạo request không thay đổi On Hand/Reserved/Available/WIP.
- Manager Approve atomically recheck stock + WO state rồi post Material Issue/WIP.
- Reject bắt buộc reason, không post stock.
- Mọi transition audit actor/time/version/idempotency.
- Không cho sửa BOM snapshot của WO để che giấu variance.

Stable errors tối thiểu:

```text
RESERVATION_EXCEEDED
BOM_REQUIREMENT_EXCEEDED
OVER_BOM_APPROVAL_REQUIRED
INSUFFICIENT_AVAILABLE_STOCK
LOT_NOT_ELIGIBLE
WORK_ORDER_STATE_CONFLICT
CONCURRENT_MODIFICATION
```

### Phase BE-3 — MRP multi-warehouse (`ISS-06`, `ISS-07`)

#### BE-3A — Tách demand warehouse khỏi requirement warehouse

Planning Run request theo `DEC-03` nên diễn đạt rõ:

```text
companyId
plantId
demandWarehouseId hoặc fulfillmentWarehouseId
horizonStartDate
horizonEndDate
demandLineIds
```

Không dùng một `warehouseId` mơ hồ cho cả netting tree và Work Order output.

Mỗi requirement/suggestion phải persist warehouse riêng:

```text
MrpRequirementLine.warehouseId
SupplySuggestion.warehouseId
SupplySuggestion.outputWarehouseId (MAKE)
SupplySuggestion.receivingWarehouseId (BUY, nếu giữ field này)
```

#### BE-3B — Warehouse resolution precedence

Thứ tự resolution đã chốt:

1. Sales Order line fulfillment warehouse nếu có.
2. Planning Run demand warehouse cho top-level finished good.
3. BOM line explicit source warehouse nếu domain bổ sung override.
4. Nếu Plant chỉ có đúng một Warehouse ACTIVE, dùng Warehouse đó cho mọi Item và cả vai trò supply/output.
5. Item-Plant planning policy/default Item-Warehouse cho component.
6. Warehouse Type fallback theo Item type nếu chỉ có đúng một Warehouse ACTIVE phù hợp:
   `RAW_MATERIAL/CONSUMABLE → RAW_MATERIAL`, `WIP → WIP`, `FINISHED_GOOD → FINISHED_GOODS`.
7. Không có kho phù hợp → `BLOCKED/MISSING_WAREHOUSE_POLICY`; có nhiều kho phù hợp nhưng không có default
   → `BLOCKED/AMBIGUOUS_WAREHOUSE_POLICY`. Không được chọn bản ghi đầu tiên.

Item-Warehouse hiện có safety stock/reorder point/lead time nhưng chưa đủ để chọn một Warehouse khi Item
có nhiều setting. Contract khuyến nghị bổ sung `isDefaultSupply` và `isDefaultOutput` trên setting; cùng
một Warehouse có thể giữ một hoặc cả hai vai trò. Backend enforce tối đa một default trong phạm vi
`Plant + Item + role`, và yêu cầu có default khi nhiều Warehouse cùng đủ điều kiện. Item vẫn thuộc Company;
mapping Item-Warehouse được cấu hình theo Plant nên cùng một Item có thể dùng kho khác nhau tại hai Plant.

#### BE-3C — Netting per requirement warehouse

Với từng requirement:

```text
eligibleOnHand = tổng lot AVAILABLE đúng Item + resolved Warehouse
projectedAvailable = eligibleOnHand + confirmedScheduledReceipts - priorAllocations
netRequirement = max(0, grossRequirement + safetyStock - projectedAvailable)
```

- HOLD/REJECTED/EXPIRED không eligible.
- Planned BUY deferred không được tính scheduled receipt.
- Stock ở kho khác không được cộng; nếu có transfer planning, trả alternate availability/transfer proposal
  riêng và chỉ tính khi transfer receipt được confirm.
- Persist input quantities và resolution source để Planning Run immutable/auditable.

#### BE-3D — Suggestion states

Mã exception cần ổn định và đủ biểu đạt:

```text
READY
WARNING
BLOCKED

MATERIAL_SHORTAGE
MISSING_BOM
MISSING_ROUTING
MISSING_WAREHOUSE_POLICY
AMBIGUOUS_WAREHOUSE_POLICY
SYSTEM_FALLBACK_USED
PURCHASING_DEFERRED
```

MAKE conversion lấy output warehouse từ suggestion và tạo tối đa một Work Order idempotently.
BUY behavior thực hiện đúng `DEC-04`; approve BUY không tạo PO/scheduled receipt/stock side effect.

#### BE-3E — Acceptance scenario bắt buộc

```text
SO: 10 FG, demand warehouse WH-FG
WH-FG: 2 FG AVAILABLE
FG BOM cần 1 SF; WH-SF có 3 SF AVAILABLE
5 SF còn phải MAKE; mỗi SF cần 4 RM
WH-RM có 12 RM AVAILABLE
```

Kỳ vọng bỏ qua safety stock để minh họa:

```text
FG net MAKE = 8 tại WH-FG
SF net MAKE = 5 tại WH-SF
RM net BUY  = 8 tại WH-RM
```

Thay đổi WH-RM rồi tạo run mới chỉ đổi result liên quan; run cũ không mutate.

### Phase BE-4 — Output lot integrity (`ISS-08`)

#### BE-4A — Chính sách identity/uniqueness

Triển khai đúng `DEC-05`; constraint database phải khớp service validation. Nếu chọn unique theo
`Company + Item + normalized lotCode`, công bố normalization (trim, case sensitivity) và hành vi với
partial receipt/reuse lot.

#### BE-4B — Validation timing

1. Create/Submit Receipt validate format và duplicate/reuse eligibility sớm để UX tốt.
2. Approval recheck authoritative dưới transaction/lock để chống race.
3. Approval atomically tạo/reuse lot, post movement, tăng On Hand, tăng Quality Hold và link WIP genealogy.
4. Nếu lot step thất bại, toàn bộ Approval rollback; Receipt không được chuyển APPROVED và stock không đổi.
5. QC chỉ disposition lot/output hold đã tồn tại; không tạo lot mới và không đổi lot code.
6. QC AVAILABLE chuyển hold → available; QC REJECTED giữ on-hand nhưng không available.

Stable errors đề xuất:

```text
LOT_CODE_ALREADY_EXISTS
LOT_REUSE_NOT_ALLOWED
LOT_ITEM_MISMATCH
LOT_WAREHOUSE_CONFLICT
OUTPUT_LOT_NOT_POSTED
RECEIPT_STATE_CONFLICT
```

#### BE-4C — Data repair

Trước deploy, audit Receipt APPROVED/QC_PENDING không có output lot hoặc có duplicate conflict. Chuẩn bị
script/report idempotent; không sửa tay lot code/movement đã post mà không có audit/reversal policy.

### Phase BE-5 — Deferred Serial Tracking (`ISS-10`)

Không triển khai data model, migration hoặc API serial trong đợt hiện tại. Contract authoritative chỉ hỗ trợ
`NON_TRACKED | LOT_TRACKED`. Khi mở lại, phải lập kế hoạch end-to-end bao phủ Item, Receipt, QC, Inventory,
Reservation, Issue, Transfer và genealogy; không chỉ thêm `serialNumbers[]` vào Production Receipt.

### Phase BE-6 — Scrap, Rework và variance (`ISS-11`, `ISS-12`)

#### BE-6A — Execution disposition semantics

Document và enforce:

- Good làm tăng `actualGoodQuantity` và available-to-receipt.
- Scrap là loss cuối cùng, không thể đồng thời là Rework cho cùng unit.
- Rework là WIP disposition chờ xử lý lại; không tự tăng Good.
- Lần rework hoàn thành tạo Execution mới và nên link `sourceReworkExecutionId`/transaction nếu MVP cho phép.

Nếu operation input quantity có thể xác định, enforce:

```text
good + scrap + rework <= eligible input/WIP quantity của operation
```

Nếu hiện chưa có input ledger đủ chính xác, Backend phải công bố giới hạn và không để FE tuyên bố validation
mà server không bảo đảm.

#### BE-6B — Rework material

Theo `DEC-08` MVP:

- Material Issue/Over-BOM request có `reasonCode=REWORK`;
- optional source Execution/Rework transaction ID;
- extra consumption đi vào material variance và audit;
- không tự động trừ vật tư chỉ vì người dùng nhập `reworkQuantity`.

Nếu Product Owner chọn Rework Order, cần plan riêng cho demand, BOM/routing snapshot, WIP transfer,
reservation, issue, execution và receipt; không nhét vào mutation Execution hiện tại.

#### BE-6C — Variance authority

Backend công bố nguồn từng field:

```text
output: Work Order planned vs Execution actual Good
wip: Execution Scrap/Rework
time: Routing snapshot vs sum/union actual execution duration theo rule rõ ràng
material: BOM snapshot vs posted Material Issues
cost: valuation + labor + overhead authoritative, hiện deferred
```

Cost có thể giữ trong DTO để compatibility nhưng phải đánh dấu unavailable/non-authoritative hoặc trả null
theo contract; không trả `0` khiến FE hiểu là chi phí thực bằng 0.

### Phase BE-7 — Verification, OpenAPI và rollout

1. Unit tests cho normalization/unique constraint, warehouse resolver, netting và disposition invariants.
2. Repository/integration tests cho concurrent Approval duplicate lot, idempotency replay và rollback.
3. API tests cho search pagination, Plant isolation, error codes và optimistic locking.
4. End-to-end fixtures:
   - SO search;
   - three-warehouse MRP;
   - normal/Over-BOM issue approval;
   - Receipt duplicate negative case và HOLD → QC AVAILABLE;
   - rework → extra issue → subsequent good output.
5. Migration dry run và rollback/recovery documentation.
6. Publish Runtime OpenAPI, API guide, permission matrix và sample payload cùng deploy.
7. Deploy Backend trước contract-dependent FE; giữ compatibility window hoặc version field rõ ràng.

## 6. Backend acceptance criteria theo vấn đề

| ID | Điều kiện chấp nhận Backend |
|---|---|
| `ISS-01` | Search đúng scope, deterministic pagination, không N+1 nghiêm trọng |
| `ISS-02` | Item search code/name và balance by itemId đáp ứng dữ liệu lớn |
| `ISS-03` | Numeric precision giữ nguyên; không sửa BE chỉ để trim display zero |
| `ISS-04` | Server enforce reservation/BOM bounds; Over-BOM approval không post sớm |
| `ISS-05` | Genealogy field vẫn tồn tại và ổn định |
| `ISS-06` | Requirement/suggestion resolve và persist đúng warehouse từng cấp |
| `ISS-07` | Formula, eligible stock, state/message và BUY side effect được document/test |
| `ISS-08` | Không thể có Receipt APPROVED với lot creation conflict; QC chỉ disposition |
| `ISS-09` | Aggregate và lot detail đủ field/status/time, tổng quantity nhất quán |
| `ISS-10` | Được ghi rõ deferred; Runtime OpenAPI hiện tại không quảng bá hỗ trợ Serial Tracking |
| `ISS-11` | Mỗi variance field có nguồn; Cost không giả authoritative |
| `ISS-12` | Rework không tự thành Good; extra material có reference/audit/approval |

## 7. Rủi ro và giảm thiểu

- **MRP double counting:** persist resolved warehouse và allocations trong immutable run; không aggregate mọi kho.
- **Race duplicate lot:** database constraint + transaction/lock + idempotency, không chỉ pre-check.
- **Breaking API:** deploy compatibility/migration theo phase và contract tests với FE.
- **Serial scope phình lớn:** đã defer toàn bộ; không làm serial chỉ ở Receipt.
- **Rework double count:** dùng explicit disposition/linkage; một unit không đồng thời Scrap và Rework.
- **Cost gây hiểu nhầm:** null/unavailable cho dữ liệu chưa có valuation authority.
- **Shared server mutation:** acceptance writes chỉ trên local/dedicated Backend hoặc có authorization rõ ràng.

## 8. Thứ tự ưu tiên đề xuất

1. `P0`: `ISS-08` lot transaction integrity, `ISS-06` MRP warehouse semantics, `ISS-04` Over-BOM posting gate.
2. `P1`: `ISS-01` search, `ISS-09` inventory read model, `ISS-12` rework semantics.
3. `P2`: variance contract cleanup và các read-model enrichment còn lại.
4. Deferred: `ISS-10` Serial Tracking end-to-end.

## 9. Bàn giao Backend

Mỗi phase bàn giao đồng thời code, migration, automated tests, Runtime OpenAPI, sample request/response,
stable error codes, permission/state matrix và acceptance evidence. FE chỉ bắt đầu phase phụ thuộc sau khi
contract tương ứng được deploy hoặc có mock contract được Backend team ký xác nhận.

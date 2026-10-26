# module/sales — Business Rule Invariants

> Thêm ở `F3` (2026-07-26). Chỉ nạp khi agent làm việc trong `module/sales/**`.

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B43 | Sales Order chỉ tạo independent demand khi **`confirm`** — mỗi line đúng **1** `PlanningDemand` (`demandType = SALES_ORDER`, `referenceType = SALES_ORDER_LINE`, `referenceId = salesOrderLineId`). `DRAFT` không sinh demand | `SalesOrderServiceTest.confirm_generatesOnePlanningDemandPerLine`, `.create_assignsSequentialLineNumbersAndStartsAtDraftWithZeroFulfilled` |
| B44 | Demand line **eligible** cho planning run ⇔ đủ **cả 4** điều kiện spec §2.1: order status ∈ {`CONFIRMED`,`IN_PRODUCTION`,`PARTIALLY_FULFILLED`} · cùng `plantId` · `dueDate ≤ horizonEnd` (biên **inclusive**) · `openQuantity = orderedQuantity − fulfilledQuantity > 0`. Cả 4 nằm trong **một** JPQL, không lọc ở Java | `SalesOrderLineRepositoryIT` (5 case, mỗi điều kiện loại 1 case) |
| B45 | `fulfilledQuantity` **chỉ** tăng khi QC disposition = `AVAILABLE` commit thành công (spec §7.1) — **[F6]** đã implement, xem `module/workorder/CLAUDE.md` B62. Vượt `orderedQuantity` bị **cắt** (`SalesOrderLine.addFulfilled`), không ném lỗi: hàng dư là tồn kho tự do. CHECK `fulfilled_quantity <= ordered_quantity` ở `V28` là chốt chặn cuối | `SalesOrderFulfillmentServiceTest.applyFulfillment_moreThanOrdered_isClippedAtOrderedQuantity`, `SalesOrderServiceTest.create_...` (assert `fulfilledQuantity = 0`), CHECK constraint `chk_sales_order_lines_fulfilled` |
| B46 | Huỷ Sales Order chỉ được từ `DRAFT` hoặc `CONFIRMED`; từ `IN_PRODUCTION` trở đi ⇒ `STATE_CONFLICT` (409). Huỷ đơn `CONFIRMED` **phải** huỷ luôn demand `OPEN` của nó, nhưng **không** đụng demand đã `CONSUMED` (MRP run là snapshot immutable) | `SalesOrderServiceTest.cancel_confirmedOrder_cancelsTheDemandItGenerated`, `.cancel_orderAlreadyInProduction_throwsStateConflict`, `.cancel_draftOrder_hasNoDemandToCancel` |
| B47 | `lineNo` do **server** cấp (1..N theo thứ tự request), client không gửi ⇒ không thể phá UNIQUE `(sales_order_id, line_no)` | `SalesOrderServiceTest.create_assignsSequentialLineNumbersAndStartsAtDraftWithZeroFulfilled` |
| B66 | **[F6]** Roll-up trạng thái đọc **mọi** line của đơn, không chỉ line đang được fulfill: `FULFILLED` ⇔ mọi line `fulfilled >= ordered`; `PARTIALLY_FULFILLED` ⇔ có line `fulfilled > 0` nhưng chưa đủ. `IN_PRODUCTION` chỉ vào được **từ `CONFIRMED`** — đơn đã có hàng giao không được lùi về "mới bắt đầu sản xuất" | `SalesOrderFulfillmentServiceTest.applyFulfillment_onlyOneLineOfTwoCovered_doesNotReportTheOrderComplete`, `.applyFulfillment_everyLineCovered_marksTheOrderFulfilled`, `.markInProduction_orderAlreadyPartiallyFulfilled_keepsItsStatus` |
| B87 | **[C2-4]** `PATCH /sales-orders/{id}` chỉ áp dụng `DRAFT` (không thì `STATE_CONFLICT` 409); `expectedVersion` phải khớp `SalesOrder.version` **trước khi** mutate bất cứ field nào, lệch ⇒ `CONCURRENT_MODIFICATION` (409); nếu `lines == null` giữ nguyên dòng cũ, nếu không thì xoá hết + build lại `lineNo` 1..N; validate `dueDate ≥ orderDate` chạy lại cho **toàn bộ** dòng liên quan (dòng mới nếu `lines` được thay, dòng cũ nếu chỉ đổi `orderDate`) trước khi ghi gì | `SalesOrderServiceTest.update_validRequest_replacesHeaderAndLines`, `.update_nullLines_keepsExistingLines`, `.update_nonDraftOrder_throwsStateConflict`, `.update_staleExpectedVersion_throwsConcurrentModificationBeforeSaving`, `.update_orderDateMovesPastAnExistingLineDueDate_throwsAndSavesNothing` |
| B111 | **[FE contract fix, 2026-08-06]** `SalesOrderResponse` nay mang `version` — đóng đúng lỗ hổng mà `B87` để hở: FE **bắt buộc** gửi `expectedVersion` trên `PATCH` nhưng trước đây không có cách nào lấy giá trị hợp lệ (không `version`, không ETag). 🔴 **Bug thật phát hiện lúc smoke test thủ công, không phải giả định:** `update`/`confirm`/`cancel` đều gọi `salesOrderRepository.save(order)` rồi map response **ngay trong cùng transaction** — Hibernate chỉ bump field `@Version` trong bộ nhớ **lúc flush** (mặc định là lúc commit, tức **sau** khi response đã build xong), nên response trả `version` **cũ hơn** con số vừa thật sự persist. Client tin theo response rồi gửi `expectedVersion` đó ở lần `PATCH` kế tiếp sẽ luôn lệch 1, y hệt bị người khác vừa sửa đè — `CONCURRENT_MODIFICATION` giả. Sửa bằng `saveAndFlush` thay cho `save` ở cả ba method (`update`/`confirm`/`cancel`) — ép flush ngay trong transaction, entity trả về mang đúng `version` mới. `create` **không cần sửa**: entity mới `version` khởi tạo `0`/`null`, `INSERT` không bump field đó nên không có gì để đọc lệch | `SalesOrderServiceTest.update_persistsThroughSaveAndFlush_soTheResponseVersionCanBeTrustedAsTheNextExpectedVersion` (pin đúng phương thức gọi, không chỉ giá trị trả về — mock `returnFirstArgument()` không tự tái hiện được độ trễ thật của Hibernate); xác nhận qua smoke test HTTP thật: `PATCH` liên tiếp 2 lần dùng `version` của response trước làm `expectedVersion` — cả hai đều `200`, `version` tăng đúng `0→1→2` |

| B112 | **[FE defect report, 2026-08-10]** `PATCH /sales-orders/{id}` với `lines[]` (full replace) phải **thật sự chạy được**, và mỗi lần chạy được phải bump `version` **đúng một lần**. Hai lỗi thật, cả hai chỉ lộ ra khi chạy trên Postgres thật — mock repository và toàn bộ 977 case unit đều xanh: 🔴 **(1)** `order.getLines().clear()` + add lại `lineNo` 1..N trong **cùng một flush** ⇒ Hibernate xếp `INSERT` con **trước** `DELETE` orphan-removal ⇒ đụng `uk_sales_order_lines_order_line_no` ⇒ **toàn bộ** PATCH chết với `RESOURCE_ALREADY_EXISTS` ("Data constraint violation"). Sửa bằng `salesOrderRepository.flush()` **giữa** clear và add — ép thứ tự tường minh thay vì tin vào action ordering của Hibernate. 🔴 **(2)** `lines` là collection **inverse** (`mappedBy`) ⇒ thay dòng **không** làm dirty row header ⇒ không có `UPDATE sales_orders` ⇒ PATCH **chỉ có `lines`** trả 200 nhưng `version` **đứng yên**. Đó là phá đúng thứ `expectedVersion` sinh ra để bảo vệ: hai request thay dòng đồng thời cùng đọc version *n*, cùng qua check, request thứ hai thắng **im lặng** (lost update). Sửa bằng `order.setUpdatedAt(Instant.now())` trong `replaceLines` — làm dirty aggregate để đúng **một** `UPDATE` (một lần bump) được phát; giá trị bị `@LastModifiedDate` ghi đè lúc flush, thứ cần là **tính dirty**, không phải giá trị. ⚠️ **Đừng dùng `LockModeType.OPTIMISTIC_FORCE_INCREMENT`** cho việc này: khi header **cũng** đổi, entity đã dirty sẵn ⇒ bump **hai** lần | `SalesOrderUpdateLinesIT` (4 case, Postgres thật — **class IT thứ 15**): `.update_replacesLinesReusingLineNumberOne_withoutViolatingTheLineNoUniqueConstraint`, `.update_growingFromTwoLinesToThree_numbersThemOneToThree`, `.update_replacingOnlyTheLines_stillBumpsTheVersionExactlyOnce`, `.update_dueDateBeforeOrderDate_leavesTheExistingLinesInPlace`; unit: `SalesOrderServiceTest.update_replacingLines_flushesTheOrphanDeletesBeforeBuildingTheReplacements` (`InOrder`, khoá thứ tự flush → build), `.update_replacementLineFromAnotherCompany_flushesTheDeletesButNeverCommits`, `.update_nullLines_keepsExistingLines` (+`never().flush()`) |

---

## Quyết định thiết kế cần nhớ

### 1. Hướng phụ thuộc cross-module là `sales → planning`, không có `SalesOrderLookupService`

`NEXT_PHASE_PLAN.md` (bản `F3`) dự kiến "cross-module phải đi qua lookup service của `sales`" —
tức hướng `planning → sales`. Thực tế khi implement thì **không có ai gọi vào `sales`**:

- Endpoint `GET /sales-orders/planning-demands` nằm trong chính `module/sales` (spec §2.2 đặt path
  dưới `/sales-orders`), nên nó đọc dữ liệu của chính mình.
- Chiều thực sự phát sinh là `sales → planning` (confirm ⇒ tạo `PlanningDemand`).

⇒ Tạo `SalesOrderLookupService` lúc này là abstraction không người dùng (vi phạm `coding-rules.md
§11.5`). Rule `C7` vẫn được giữ đúng theo chiều thật: `SalesOrderService` gọi
**`PlanningDemandService`**, tuyệt đối không chạm `PlanningDemandRepository`.

Hai entry point dành riêng cho việc này nằm ở `PlanningDemandService`:

| Method | Vì sao **không** có `@PreAuthorize` |
|---|---|
| `createFromSalesOrderLine(company, plant, item, qty, dueDate, salesOrderLineId)` | Caller (`SalesOrderService.confirm`) đã được kiểm `PERM_SALES_ORDER_MANAGE` trên **cùng plant**. Bắt user sales phải có thêm `PERM_PLANNING_DEMAND_MANAGE` là sai nghiệp vụ. Cùng pattern với `PurchaseOrderService.createFromRequisition` |
| `cancelOpenDemandsForSalesOrderLines(lineIds)` | Như trên, caller là `SalesOrderService.cancel` |

Hai method này nhận **tham số nguyên thuỷ**, không nhận entity của `sales` ⇒ `module/planning`
không compile-depend ngược lại `module/sales`.

### 2. Lệch tên trường có chủ đích: `orderNo` vs `salesOrderCode`

| Nơi | Tên | Nguồn |
|---|---|---|
| Entity + `SalesOrderResponse` (CRUD) | `orderNo` | `NEXT_PHASE_PLAN.md §1` (bản `F3`) |
| `PlanningDemandLineResponse` (màn hình Planning) | `salesOrderCode` | Spec §2.4 bảng "Demand" |

Cùng một cột `sales_orders.order_no`. Không tự ý thống nhất một bên — cả hai đều là contract đã
chốt với FE. Nếu `F5`/`F6` đổi, phải đổi **có chủ đích** kèm ghi Breaking Change.

### 3. ✅ `SalesOrderStatus` — cả 6 giá trị đã có đường vào (`F6`, 2026-07-28)

`IN_PRODUCTION` / `PARTIALLY_FULFILLED` / `FULFILLED` trước `F6` chỉ được khai báo (và có trong CHECK
của `V28`) vì query eligibility ở `B44` phải gọi tên chúng. `F6` đã nối đường vào — xem `B66` và
`docs/system-flow.md` mục "Sales Order". Không có giá trị enum **mới** nào được thêm, nên
`coding-rules.md §11.3` không áp dụng đầy đủ; nhánh ra của `cancel` vẫn giữ nguyên `B46`
(`IN_PRODUCTION` trở đi **không** huỷ được — đã có Work Order và có thể đã xuất vật tư).

### 3b. `F6` gọi vào `sales` qua `SalesOrderFulfillmentService`, không phải `SalesOrderLookupService`

`NEXT_PHASE_PLAN.md` (bản `F6`) dự kiến tên `SalesOrderLookupService`. Service này **ghi** (tăng
`fulfilledQuantity`, roll-up status) chứ không chỉ đọc, nên đặt tên "lookup" sẽ là service duy nhất
trong repo có tên nói sai việc nó làm. Ba entry point, đều **không** `@PreAuthorize` cùng lý do với
`PlanningDemandService.createFromSalesOrderLine` (mục 1 ở trên):

| Method | Dùng ở đâu |
|---|---|
| `findAllocationTargets(lineIds)` | `WorkOrderDemandAllocationService` — 1 query cho cả trang WO (`C14`) |
| `markInProduction(lineIds)` | lúc convert proposal MAKE |
| `applyFulfillment(qtyByLineId)` | lúc QC `AVAILABLE` commit |

Chiều ngược lại (`workorder → sales`) cố tình **không** dùng association có kiểu — xem
`module/workorder/CLAUDE.md` mục Fulfillment Allocation.

### 4. Lỗi sai trạng thái dùng `STATE_CONFLICT` (409), không phải `OPERATION_NOT_ALLOWED` (422)

Theo tiền lệ `F2`. Lưu ý phân biệt trong chính service này: lỗi **dữ liệu không hợp lệ** (line
`dueDate` trước `orderDate`, item khác company, plant khác company) vẫn dùng
`OPERATION_NOT_ALLOWED` (422) — đó là validation, không phải xung đột trạng thái.

---

## Bất Biến Customer Master (`G2-1`, 2026-10-14)

> Số `B136`+ (không phải `B124`): `MANUFACTURING_PHASE_PLAN_P7_P11.md` đã giữ chỗ `B124`–`B135` cho P7–P11 dù chưa implement — dùng chung dãy số sẽ trùng.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B136 | Customer là master data **company-scoped**, đối xứng `Supplier` (`module/purchasing`). `code` **trim + uppercase rồi mới** kiểm `^[A-Z0-9._-]+$` và độ dài 1..100 (sai ⇒ `VALIDATION_ERROR` + `errors[{field:"code"}]`) — khác `@Pattern` của Item/Plant/Warehouse vốn từ chối `" partner-01 "`. Unique `(company_id, code)` **bất kể status**: customer `INACTIVE` vẫn giữ mã của nó. Supplier và Customer **được trùng mã** (hai danh mục khác nhau). `activate` idempotent và **bị chặn khi company `INACTIVE`** (`RESOURCE_INACTIVE` 422, cùng luật `B108`); `deactivate` luôn được. PATCH: `null`/omitted = giữ, **chuỗi rỗng = xoá** field tuỳ chọn (`NEXT_PHASE_PLAN.md §9.2 D3`, không theo đề xuất "null = xoá" của FE); `name` không xoá được. **Không** nối `SalesOrder.customerName` → `customer_id` (ngoài phạm vi G2). Permission `PERM_CUSTOMER_READ/_MANAGE` (`V70`) **sao chép grant từ `PERM_SUPPLIER_*` trong DB lúc migrate**, không viết lại danh sách role | `CustomerServiceTest` (16), `CustomerMethodSecurityTest` (10), `SalesPermissionGuardTest` (4), `CustomerControllerTest` (9), **`CustomerRepositoryIT`** (6 — mutation bỏ `cast(:search as string)` ⇒ 2 case đỏ với đúng `lower(bytea)`), `FlywayMigrationIT.migrate_v69_*`/`migrate_v70_*` |

---

## Bất Biến Đơn Bán Hàng Nối Khách + Giữ Tồn Cho Đơn (`G6a`, 2026-10-17)

> Nguồn: `FE_HANDOFF_GD_06_2026-10-14.md`; quyết định D0–D4 ở `NEXT_PHASE_PLAN.md §13.2`. Trả lời FE: `BE_RESPONSE_GD_06_2026-10-17.md`.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B171 | **Đường nháp mới (`POST /drafts`, `PATCH /{id}/draft`) cùng tồn tại với đường cũ (`POST /v1`, `PATCH /{id}`)** (D2). Nháp mới: `orderNo` = `SO-%06d` từ `sales_order_number_seq` (V74), bỏ qua giá trị đã bị mã cũ chiếm; `customerId` bắt buộc, khách **ACTIVE cùng Công ty** (`RESOURCE_INACTIVE`/`RESOURCE_SCOPE_MISMATCH` 422); `customerCode`/`customerName` **chụp** lúc lưu (FE Q18). Kiểm **trước mọi lookup** lỗi ngày `lines[i].dueDate` (`VALIDATION_ERROR` + field), rồi vật tư ACTIVE, cùng Công ty, loại `FINISHED_GOOD`/`WIP`, UOM (nếu có trong danh mục) ACTIVE. `PATCH /{id}/draft` là **thay toàn bộ** — field vắng = xoá (khác quy ước "null = giữ" của `PATCH /{id}`). Xác nhận đơn **có `customerId`** kiểm lại khách + vật tư; đơn cũ không đổi hành vi. `expectedVersion` tuỳ chọn ở confirm/cancel (null = đường cũ) | `SalesOrderDraftServiceTest` (11), `SalesOrderReservationIT.draftsGetDistinctServerNumbers_*` |
| B172 | **Sáu số tiến độ** (D3): `heldStock` = Σ ACTIVE remaining giữ tồn của dòng; `workOrderCovered` = Σ (allocated − fulfilled) của allocation có WO **không** `CANCELLED`/`CLOSED` (gồm `DRAFT`, FE Q2); `qcAccepted` = `fulfilledQuantity`; `produced`/`delivered` **luôn `null` + `UNKNOWN`** — không có vết sản lượng theo dòng đơn, chưa có chứng từ giao. `uncovered = max(0, ordered − qcAccepted − held − workOrderCovered)` — ba số trừ **không chồng nhau**. 🔴 **[`G10b`, D8] nay là `max(0, ordered − max(qcAccepted, delivered) − held − workOrderCovered)`**; `delivered` = Σ phiếu giao `POSTED` (`null` + `UNKNOWN` khi baseline không biết, `B240`). Trừ thẳng `delivered` đếm đôi với `qcAccepted` (đặt 10, QC 6, WO đóng hụt, giao 6 ⇒ ra 0, thật còn 4); không trừ gì thì giao từ phần giữ làm `uncovered` **tăng** đúng lượng vừa giao. Detail lấy bằng **3 batch query** cho cả đơn (`SalesOrderDetailAssembler`, C14). `openQuantity` **giữ nghĩa cũ** (FE Q27) | `SalesOrderServiceTest`, `SalesOrderReservationServiceTest.reserve_moreThanTheUncoveredQuantity_*` (10 − 2 − 3 − 4 = 1) |
| B173 | **Giữ tồn cho đơn**: chỉ khi đơn `CONFIRMED`/`IN_PRODUCTION`/`PARTIALLY_FULFILLED` (`STATE_CONFLICT`); dòng thuộc đơn (`404`); kho ACTIVE cùng nhà máy; lượng ≤ `uncovered` (`PLANNED_QUANTITY_EXCEEDED` 409) và ≤ tồn tự do (`INSUFFICIENT_AVAILABLE_STOCK`). Mọi lệnh giữ/bỏ giữ kiểm `expectedVersion` rồi **bump version đơn** (`setUpdatedAt` + `saveAndFlush`, cùng bẫy `B112` #2 — dòng giữ nằm ở bảng con nên đơn không tự dirty) ⇒ gửi lại lệnh đã chạy ⇒ `409 CONCURRENT_MODIFICATION`, hai lệnh tranh cùng đơn không cùng commit. Quyền: `PERM_SALES_ORDER_MANAGE` (FE Q4), không phải quyền giữ vật tư của WO | `SalesOrderReservationServiceTest` (5), `SalesOrderReservationMethodSecurityTest` (4), **`SalesOrderReservationIT`** (mutation bỏ bump ⇒ đỏ) |
| B174 | **Huỷ đơn `CONFIRMED` trả hết tồn đang giữ của chính đơn** (`SalesOrderStockReservationService.cancelActiveForSalesOrder`, status `CANCELLED`) trong cùng transaction, kể cả qua đường cũ không body. Body `{expectedVersion?, reason}` tuỳ chọn; gửi body thì `reason` bắt buộc và lưu `cancel_reason`. Chặn khi có lệnh SX là luật `B46` có sẵn (WO allocation ⇒ `IN_PRODUCTION`), không cascade (FE Q24) | `SalesOrderDraftServiceTest.cancel_*`, `SalesOrderReservationIT.cancellingTheOrder_*` (mutation bỏ dòng trả tồn ⇒ đỏ) |

**Quyết định cần nhớ:**

1. 🔴 **Hướng phụ thuộc mới `sales → workorder`** (qua `SalesOrderStockReservationService` + `SalesOrderCoverageLookupService`, đều không `@PreAuthorize`). Mục 1 ở trên ("`sales` không biết gì về work order") **không còn đúng** — đã lỗi thời từ G6a. Chiều `workorder → sales` (`SalesOrderFulfillmentService`) vẫn giữ; không vòng bean vì cả hai service phía workorder chỉ phụ thuộc repository.
2. `customer_id` là **UUID phẳng**, không `@ManyToOne`, không backfill (G2 §9.5). Đơn cũ `customerId = null` ⇒ confirm không kiểm khách.
3. Idempotency-Key **không** áp cho đơn bán — replay safety là `expectedVersion`. Tạo nháp gửi lại hai lần tạo **hai** nháp (đã ghi cho FE).

### G6b — entry point cho hoạch định

`SalesOrderPlanningSourceService.findSources(lineIds)` (không `@PreAuthorize`, 3 batch query): mỗi dòng đơn kèm Công ty/nhà máy, đơn còn
hoạch định được, held, WO coverage, `uncovered` — **cùng** công thức B172 (`SalesOrderMapper.uncovered`). Caller: run thương mại, tính
phiên bản nguồn, chuyển đổi (cap phần gắn đơn).

## Bất Biến Nền Thương Mại Của Đơn Bán (`G10a`, 2026-10-25)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B238 | **Giá bán: `NULL` ≠ 0** (FE R02). `sales_order_lines.unit_price` VND, 0..999 999 999, ≤ 6 số lẻ (CHECK `V86` + `@Digits(9,6)`/`@DecimalMax` ở request). Đơn `DRAFT` đổi giá qua `PATCH /{id}/draft` (tuỳ chọn mỗi dòng); đổi giá ⇒ `pricing_revision` +1 (so theo **vị trí dòng** — id dòng nháp đổi mỗi lần lưu). Đơn đã xác nhận: `POST …/commercial-prices` (`PERM_SALES_PRICE_MANAGE`) **chỉ** điền giá còn thiếu, tất cả hoặc không; giá đã biết **kể cả 0** ⇒ `409 PRICE_LOCKED` field `prices[i].unitPrice`; trùng dòng / dòng lạ ⇒ `400` field; `DRAFT`/`CANCELLED` ⇒ `STATE_CONFLICT`. Mỗi giá điền là một dòng `sales_order_price_events` (không sửa/xoá) + `pricing_revision` +1 + version đơn +1 (`setUpdatedAt`, bẫy `B112`). `Idempotency-Key` **bắt buộc**, replay **trước** version (bảng `commercial_idempotency_records`, `UNIQUE (key, command)`, không TTL) | `SalesOrderPricingServiceTest` (8), `SalesOrderDraftServiceTest.updateDraft_movesThePricingVersionOnlyWhenAPriceChanges`, `SalesOrderCommercialShapeTest.pricesChange_*`, `FlywayMigrationIT.migrate_v86_*` |
| B239 | **Giá chỉ hiện cho người được thấy tiền** (D6). `SalesPriceVisibility.canSeePrices(plant)` = `PERM_SALES_PRICE_MANAGE` hoặc `PERM_SALES_INVOICE_READ/_MANAGE/_POST` trên nhà máy, tính **một lần mỗi response**. Không được ⇒ `commercial` (dòng và đơn) **vắng mặt** trong JSON — `@JsonInclude(NON_NULL)` trên chính component, vì `default-property-inclusion: always` sẽ in `null`; được nhưng chưa có giá ⇒ `commercial.unitPrice: null` + `priceKnown: false`. Hình dạng cũ (`toResponse`) và danh sách **không bao giờ** mang tiền. Lệch D6 có chủ đích: một object con tuỳ chọn thay cho hai record — cùng hiệu ứng trên wire, ít code hơn | `SalesOrderCommercialShapeTest` (JSON thô, 7) — mutation bỏ `NON_NULL` ⇒ 2 đỏ; thêm `PERM_SALES_ORDER_READ` vào danh sách ⇒ đỏ |
| B240 | **Xác nhận đơn đòi khách master** (D4): `customerId == null` ⇒ `422 OPERATION_NOT_ALLOWED` field `customerId`, không demand nào được tạo. Đường tạo cũ `POST /v1` nhận `customerId` **tuỳ chọn** (additive) — có thì tên master thay tên gõ tay. **Baseline giao hàng** (D3): `delivery_baseline_known` = `FALSE` cho mọi đơn đã qua `DRAFT` khi `V86` chạy (không suy từ `fulfilled`), `TRUE` cho đơn nháp và mọi đơn mới; response mang `deliveryBaselineKnown` + `deliveryBaselineUnknownReason = LEGACY_ORDER`. Không có đường đặt lại thành `TRUE` | `SalesOrderServiceTest.confirm_withoutAMasterCustomer_*`, `SalesOrderDraftServiceTest.legacyCreate_withACustomerId_*`, `SalesOrderCommercialShapeTest.detail_ofALegacyOrder_*`, `FlywayMigrationIT.migrate_v86_*`, `ProductionFlowE2EIT` (tạo khách master) |

### G10b — giao hàng

Phiếu giao là module riêng `module/delivery` (bất biến `B241`–`B245` ở `module/delivery/CLAUDE.md`). Phía sales: `SalesOrderCommercialLookupService` (đọc đơn Optional cho guard, batch đơn, khoá dòng đơn native rồi `refresh`, `touch`); `SalesOrderService.cancel` chặn đơn còn phiếu giao `DRAFT`/`POSTED` (`STATE_CONFLICT`); detail + `planning-demands` đọc `delivered` qua `DeliveryLookupService` (một batch, C14). Hướng phụ thuộc hai chiều `sales ↔ delivery` chỉ qua lookup service không `@PreAuthorize`. `SalesOrderPricingService` đặt `updatedAt` **trước** lần flush đầu (smoke `G10b` tìm ra version +2; `pricing_revision` vốn đã làm header dirty nên dòng đó chủ yếu là chặn đúng vị trí, mutation dời nó ra sau `record` ⇒ `DeliveryPostingIT.setPrices_movesTheOrderVersionByExactlyOne` đỏ).

### G6c — người gọi mới của `createDraft`

`dataimport.SalesOrderImportHandler` tạo đơn nhập từ Excel qua **đúng** `SalesOrderService.createDraft` (một lần mỗi nhóm, trong transaction
của batch) — mọi luật, mã `SO-…` và audit của G6a áp nguyên. Hướng phụ thuộc mới `dataimport → sales` (cùng kiểu `dataimport → bom`).
Bất biến: `module/dataimport/CLAUDE.md` `B184`–`B186`.

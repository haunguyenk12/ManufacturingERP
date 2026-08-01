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

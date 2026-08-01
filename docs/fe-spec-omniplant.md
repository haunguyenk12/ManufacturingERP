# Spec tích hợp FE↔BE — OmniPlant MVP Production Flow

> ⚠️ **File này là BẢN TRÍCH XUẤT TỰ ĐỘNG, không phải bản gốc.**
>
> | | |
> |---|---|
> | **Nguồn** | `OmniPlant_MVP_Production_Backend_Handoff.docx` (thư mục gốc repo) |
> | **Ngày trích** | 2026-07-31 (phase `F7`) |
> | **Cách trích** | `.docx` là file **zip**; nội dung nằm ở `word/document.xml`. Trích bằng `python zipfile` + strip tag XML + unescape HTML entity |
>
> **Vì sao cần file này.** Bản `.docx` là binary và **chưa từng được commit** (`git log --all -- OmniPlant_*.docx`
> trả rỗng) ⇒ mọi agent trước `F7` **không đọc được spec**, chỉ đọc lại tham chiếu `spec §N` rải trong
> markdown và javadoc. Hệ quả trực tiếp: **§9 chưa từng được nhắc tới một lần nào trong toàn repo**, và
> 5 gap chỉ lộ ra khi `F7` mở file gốc lần đầu. Xem `CLAUDE.md §0.18`.
>
> **Giới hạn của bản trích.** Bảng trong Word bị làm phẳng thành từng dòng liên tiếp (mỗi ô một dòng),
> nên các bảng "Trường cần hiển thị" đọc hơi rời rạc — thứ tự ô vẫn đúng theo hàng. Hình ảnh/sơ đồ
> (nếu có) **không** được trích. Khi một chi tiết quan trọng có vẻ mơ hồ, mở lại `.docx` gốc để đối chiếu,
> **đừng** suy đoán từ bản này.
>
> **Không sửa file này bằng tay.** Nó phải luôn phản ánh đúng `.docx`. Spec đổi ⇒ trích lại.

---

ĐẶC TẢ TÍCH HỢP FRONTEND - BACKEND
OmniPlant MVP Production Flow
Flow 5 phase, API contract, business rules và danh sách trường cần hiển thị
Thuộc tính
Giá trị
Phạm vi
Sales Order demand → MRP → Work Order → Issue → WIP → Receipt → QC → Inventory
Đối tượng sử dụng
Backend developer, Frontend developer, QA, Product Owner
Trạng thái tài liệu
Backend handoff specification cho MVP
Nguồn đối chiếu
Frontend implementation và business rules hiện tại
Ngày cập nhật
25/07/2026
Mục tiêu bàn giao: Backend có thể triển khai API mà không cần suy đoán trường dữ liệu, điều kiện chuyển trạng thái hoặc tác động tồn kho. Tên endpoint trong tài liệu là đề xuất; contract dữ liệu và quy tắc nghiệp vụ là phần cần giữ ổn định.
Cách đọc tài liệu
Mỗi phase mô tả luồng người dùng, xử lý backend, endpoint đề xuất và trường UI cần hiển thị.
Các field name dùng camelCase để khớp frontend TypeScript hiện tại.
Mọi mutation phải có Idempotency-Key và được xử lý theo transaction.
Các bảng “Trường UI/API” là minimum contract; backend có thể bổ sung field nhưng không nên đổi nghĩa.

## 1. Phạm vi và luồng end-to-end

Sales Order được CONFIRMED và tạo independent demand theo từng dòng.
Planning Run chọn demand, explode BOM, net tồn kho và tạo proposal MAKE/BUY.
Proposal MAKE ở READY được convert thành Work Order trạng thái PLANNED, đồng thời đóng băng BOM/Routing snapshot.
Work Order reserve nguyên vật liệu AVAILABLE; chỉ khi readiness đạt 100% mới được RELEASED.
Material Issue trừ on-hand và reserved, tạo stock movement + WIP material transaction, Work Order chuyển IN_PROGRESS.
Production Execution ghi nhận good/scrap/rework và thời gian thực tế; cumulative good không vượt planned.
Production Receipt được tạo DRAFT, submit để PENDING_APPROVAL, Manager approve để nhập thành phẩm vào HOLD.
QC disposition chuyển output lot từ HOLD sang AVAILABLE hoặc REJECTED.
Chỉ quantity AVAILABLE mới làm tăng available stock và fulfillment của Sales Order.

### 1.1 Vai trò và quyền

Nghiệp vụ
Permission code
Vai trò MVP
Chạy MRP
PLANNING_RUN
Planner/Admin
Xem/Tạo Work Order
WORK_ORDER_VIEW / WORK_ORDER_CREATE
Planner/Admin
Reserve/Release Work Order
WORK_ORDER_RELEASE
Planner/Manager
Post Material Issue
MATERIAL_ISSUE_CREATE
Operator/Warehouse
Duyệt material variance
MATERIAL_VARIANCE_APPROVE
Manager
Ghi nhận Production Execution
PRODUCTION_EXECUTE
Operator
Tạo/Submit Production Receipt
PRODUCTION_RECEIPT_CREATE
Operator
Approve/Reject Receipt
PRODUCTION_RECEIPT_APPROVE
Manager
QC disposition
QC_DISPOSITION
Manager; tách riêng để mở rộng QC role
Xem trace tồn kho/lot
STOCK_VIEW
Manager/Warehouse

### 1.2 Quy ước tích hợp chung

Hạng mục
Backend phải hỗ trợ
Base path
/api/v1
Authorization
Bearer JWT; backend vẫn phải enforce RBAC, không tin việc frontend ẩn nút
Plant scope
Header X-Plant-Id cho mọi API scoped theo nhà máy
Idempotency
Header Idempotency-Key: UUID v4 cho mọi POST/PUT/PATCH mutation
Success envelope
{ code, message, result }
Paginated list
result: { content, page, size, totalElements, totalPages }
Date/time
ISO-8601; ngày dùng YYYY-MM-DD, thời điểm dùng UTC offset hoặc Z
Quantity
Decimal; không dùng floating point nhị phân cho lưu trữ/cộng dồn
Optimistic locking
Khuyến nghị version/updatedAt để chặn ghi đè trạng thái cũ
Traceability
Mọi mutation production/stock trả traceId dùng chung cho audit và movement
Nguyên tắc lỗi: Error response cần có code máy đọc được, message cụ thể cho người dùng và errors theo field nếu là validation. Frontend không xóa dữ liệu form khi lỗi.

## 2. Phase 1 — Material Planning (MRP)

Mục tiêu: gom demand từ các Sales Order line hợp lệ, explode BOM nhiều cấp, loại trừ lot HOLD/REJECTED, net tồn kho và sinh proposal MAKE/BUY.

### 2.1 Điều kiện và xử lý backend

Chỉ Sales Order line thuộc đơn CONFIRMED/IN_PRODUCTION/PARTIALLY_FULFILLED, cùng plant và dueDate ≤ horizonEnd được chọn.
Open demand không được âm và phải trừ phần đã fulfilled/allocated phù hợp với quy ước allocation.
Dependent MRP: projectedAvailable = eligibleOnHand + scheduledReceipts - priorAllocations.
netRequirement = max(0, grossRequirement + safetyStock - projectedAvailable).
Lot HOLD và REJECTED không được tính vào eligibleOnHand.
BOM phải ACTIVE; Routing phải ACTIVE cho proposal MAKE. Thiếu master data tạo BLOCKED + message code.
Planning Run là immutable snapshot. Chạy lại tạo run mới, không sửa run cũ.

### 2.2 Endpoint đề xuất

Method
Endpoint
Mục đích
Permission
GET
/sales-orders/planning-demands?horizonEnd=…
Danh sách demand line có thể chọn
PLANNING_RUN
POST
/planning-runs
Chạy MRP và lưu snapshot
PLANNING_RUN
GET
/planning-runs?plantId=…
Lịch sử Planning Run
PLANNING_RUN
GET
/planning-runs/{id}
Chi tiết run + requirements + proposals
PLANNING_RUN
POST
/planning-runs/{id}/proposals/{proposalId}/work-order
Convert MAKE proposal
WORK_ORDER_CREATE

### 2.3 Request chạy MRP

Field
Kiểu
Bắt buộc
Ý nghĩa/validation
plantId
number
Có
Phải khớp X-Plant-Id
warehouseId
number
Có
Kho net tồn và cấp phát
horizonStart
date
Có
Không sau horizonEnd
horizonEnd
date
Có
Giới hạn due date
demandLineIds
number[]
Có
Ít nhất 1; tất cả line phải còn eligible

### 2.4 Trường cần hiển thị — màn hình Planning

Khu vực
Field/API key
Hiển thị/ý nghĩa
Bộ lọc đầu vào
plantId, warehouseId, horizonStart, horizonEnd
Plant, kho và khoảng planning
Demand
salesOrderCode, salesOrderLineId
Nguồn demand
Demand
itemSku, itemName, uom, quantity, dueDate
Thành phẩm, open quantity và hạn
Run header
code, status, createdAt
Mã MRP, COMPLETED, thời điểm chạy
Summary
demandLines, grossDemand, requirementLines
Tổng số dòng và nhu cầu
Summary
shortageLines, plannedWorkOrders, plannedPurchaseRecommendations, blockedProposals
Kết quả tổng hợp
Requirement
itemSku, itemName, depth, parentItemIds
Cây BOM/nguồn cha
Requirement
grossRequirement, availableQuantity, reservedQuantity
Nhu cầu và tồn
Requirement
safetyStock, projectedAvailable, netRequirement
Kết quả netting
Requirement
excludedLotCount, settingSource, status
Lot bị loại, nguồn setting, AVAILABLE/SHORTAGE
Proposal
proposalId, supplyType, quantity, uom
MAKE/BUY và lượng đề xuất
Proposal
requiredDate, suggestedReleaseDate
Ngày cần và ngày release gợi ý
Proposal
sourceBomCode, sourceRoutingCode, sourceRoutingVersion
Master data nguồn
Proposal
exceptionState, messages
READY/WARNING/BLOCKED và reason code
Proposal
convertedWorkOrderId
Link Work Order sau convert; ngăn convert lặp

## 3. Phase 2 — Work Order, Reservation và Release

Work Order được tạo từ MAKE proposal, giữ demand lineage và snapshot bất biến. Trạng thái đúng ngay sau Phase 1 là PLANNED.

### 3.1 State transition

Từ
Action
Điều kiện
Đến
—
Create from proposal
Proposal MAKE + READY + chưa convert
PLANNED
PLANNED/BLOCKED
Reserve
Reserve từ lot AVAILABLE
PLANNED hoặc BLOCKED
PLANNED
Release
Tất cả component reserved 100%
RELEASED
RELEASED
First Material Issue
Issue hợp lệ
IN_PROGRESS
IN_PROGRESS
Cumulative good = planned
Execution hợp lệ
COMPLETED
DRAFT/PLANNED/RELEASED
Cancel
Theo policy và chưa có posting không thể đảo
CANCELLED

### 3.2 Endpoint đề xuất

Method
Endpoint
Ghi chú
GET
/work-orders?plantId=…&status=…&search=…
List + filter/pagination
GET
/work-orders/{id}
Detail đầy đủ cho release/issue/receipt
POST
/work-orders/{id}/reserve
Tự động reserve FEFO hoặc policy backend
POST
/work-orders/{id}/release
Chỉ khi readiness.canRelease=true
POST
/work-orders/{id}/cancel
Nếu MVP mở action cancel; cần reason

### 3.3 Trường cần hiển thị — Work Order

Khu vực
Field/API key
Hiển thị/ý nghĩa
Header
code, status
Mã WO và badge trạng thái
Output
outputItemSku, outputItemName, outputUom
Thành phẩm
Plan
plannedQuantity, plannedStartDate, dueDate, targetWarehouseId
Kế hoạch sản xuất
Planning lineage
planningRunId, planningRunCode, planningProposalId
Nguồn tạo WO
Demand allocation
salesOrderCode, salesOrderLineId, allocatedQuantity, fulfilledQuantity, uom, dueDate
Phân bổ theo SO line
BOM snapshot
sourceBomId, sourceBomCode, outputQuantity, capturedAt, lines[]
Snapshot bất biến
BOM line
itemId, quantity, scrapRate, childBom
Component và BOM con
Routing snapshot
sourceRoutingId, sourceRoutingCode, sourceRoutingVersion, capturedAt
Routing bất biến
Operation
sequence, name, workCenterId, setupMinutes, runMinutesPerUnit, predecessorOperationIds
Công đoạn snapshot
Requirement
itemSku, itemName, requiredQuantity, reservedQuantity, issuedQuantity, uom
Tình trạng component
Reservation
id, warehouseName, lotNumber, reservedQuantity, issuedQuantity, status
Nguồn lot/kho đã giữ
Readiness
canRelease, reservedPercent, shortageLineCount
Điều kiện Release
Execution
actualGoodQuantity, actualScrapQuantity, actualReworkQuantity
Cộng dồn WIP
Receipt
approvedReceiptQuantity, availableReceiptQuantity
Đã approve và đã QC available
Audit
releasedAt, executionStartedAt, executionCompletedAt, createdAt, updatedAt
Mốc thời gian

### 3.4 Quy tắc reservation

Reservation chỉ làm reserved tăng và available giảm; không làm onHand giảm.
Chỉ lot AVAILABLE, đúng item/warehouse/plant và còn available mới được reserve.
Một component có thể được reserve từ nhiều lot.
Backend phải lock/serialize stock rows để tránh hai Work Order reserve cùng quantity.
Release phải kiểm tra lại readiness trong transaction, không dựa vào trạng thái nút ở frontend.

## 4. Phase 3 — Material Issue

Material Issue là posting tồn kho thực sự: trừ on-hand và reserved, đồng thời tạo WIP material transaction và trace audit.

### 4.1 Request và endpoint

Method
Endpoint
Request body
GET
/material-issues?plantId=…&workOrderId=…
List/history
POST
/material-issues
{ workOrderId, reservationId, quantity }
Field
Kiểu
Bắt buộc
Validation backend
workOrderId
number
Có
WO phải RELEASED hoặc IN_PROGRESS
reservationId
number
Có
Reservation ACTIVE và thuộc WO
quantity
decimal
Có
> 0 và ≤ reservedQuantity - issuedQuantity
actor
JWT principal
Có
Không nhận quyền tin cậy từ body
Idempotency-Key
header
Có
Retry không tạo issue/movement lần hai

### 4.2 Trường cần hiển thị

Khu vực
Field/API key
Hiển thị/ý nghĩa
WO selector
workOrderId, workOrderCode, status
Chỉ RELEASED/IN_PROGRESS
Reservation selector
reservationId, itemSku, itemName
Component đang mở
Reservation selector
warehouseName, lotNumber
Nguồn xuất chính xác
Limit
reservedQuantity, issuedQuantity, remainingIssueQuantity
Max quantity cho input
Input
quantity
Số lượng issue
History
code, status, workOrderCode
Mã MI và POSTED
History
itemSku, itemName, quantity, uom
Vật tư đã xuất
History
warehouseName, lotNumber
Trace kho/lot
History
createdBy, occurredAt, traceId
Người thao tác và audit
Link
stockMovementId
Đi đến Stock Movement tương ứng

### 4.3 Transaction bắt buộc

Kiểm tra quyền, plant, trạng thái WO và reservation.
Lock reservation + stock position.
Kiểm tra remaining reserved quantity.
Tạo MaterialIssue POSTED và một traceId.
Trừ stock.onHandQuantity và stock.reservedQuantity cùng quantity.
Tăng reservation.issuedQuantity; đổi ISSUED khi đã issue hết.
Tăng componentRequirement.issuedQuantity.
Tạo StockMovement loại ISSUE và WipMaterialTransaction cùng traceId.
Đổi WO RELEASED → IN_PROGRESS nếu đây là issue đầu tiên; commit một lần.

## 5. Phase 4 — Production Execution / WIP

Không tích hợp MES trong MVP. Operator nhập thủ công good, scrap, rework và actual time; backend lưu từng posting và tính summary cộng dồn.

### 5.1 Endpoint và request

Method
Endpoint
Mục đích
GET
/production-executions/candidates?plantId=…
WO RELEASED/IN_PROGRESS đủ điều kiện
GET
/production-executions?workOrderId=…
Lịch sử posting
POST
/production-executions
Post production result
Field
Kiểu
Bắt buộc
Validation
workOrderId
number
Có
WO RELEASED hoặc IN_PROGRESS
goodQuantity
decimal
Có
≥ 0; cumulative good ≤ planned
scrapQuantity
decimal
Có
≥ 0
reworkQuantity
decimal
Có
≥ 0
actualStartedAt
datetime
Có
Không sau actualEndedAt
actualEndedAt
datetime
Có
Không trước actualStartedAt
notes
string
Không
Giới hạn độ dài; sanitize khi render
operatorUserId/Username
JWT principal
Có
Backend lấy từ token
Idempotency-Key
header
Có
Chống post trùng
Điều kiện tối thiểu: goodQuantity + scrapQuantity + reworkQuantity phải > 0. Backend không được để cumulativeGood vượt plannedQuantity.

### 5.2 Trường cần hiển thị

Khu vực
Field/API key
Hiển thị/ý nghĩa
WO selector
workOrderId, workOrderCode, status
WO đang sản xuất
Context
outputItemSku, outputItemName, uom, plannedQuantity
Thành phẩm/kế hoạch
Input
goodQuantity, scrapQuantity, reworkQuantity
Kết quả lần post
Input
actualStartedAt, actualEndedAt, notes
Thời gian và ghi chú
Summary
goodQuantity, scrapQuantity, reworkQuantity
Số cộng dồn
Summary
remainingGoodQuantity, completionPercent
Còn lại và % hoàn thành
History
code, status, occurredAt
Mã WIP và POSTED
History
operatorUsername, traceId
Người nhập và audit

### 5.3 Tác động Work Order

Lần post đầu tiên đặt executionStartedAt nếu chưa có.
actualGoodQuantity, actualScrapQuantity, actualReworkQuantity được cộng dồn atomically.
Khi cumulative good = plannedQuantity, Work Order chuyển COMPLETED và đặt executionCompletedAt.
Nếu cumulative good < plannedQuantity, Work Order vẫn IN_PROGRESS; vẫn được phép tạo partial Production Receipt cho phần good chưa receipt.

## 6. Phase 5 — Production Receipt, Approval và QC

Production Receipt tách ba mốc: tạo chứng từ, duyệt nhập kho HOLD và QC disposition. Chỉ AVAILABLE mới được dùng cho sản xuất tiếp theo hoặc fulfillment.

### 6.1 State transition

Từ
Action
Điều kiện
Đến / tác động
—
Create
quantity ≤ availableToReceipt; lot bắt buộc nếu LOT_TRACKED
DRAFT; chưa stock
DRAFT
Submit
Dữ liệu hợp lệ
PENDING_APPROVAL; chưa stock
PENDING_APPROVAL
Approve
Manager permission
APPROVED; lot HOLD; RECEIVE stock
PENDING_APPROVAL
Reject
Có reason
REJECTED; không stock
APPROVED + HOLD
QC Available
Có reason
Lot AVAILABLE; tăng available; fulfill demand
APPROVED + HOLD
QC Rejected
Có reason
Lot REJECTED; không tăng available/fulfillment

### 6.2 Endpoint đề xuất

Method
Endpoint
Permission
GET
/production-receipts/candidates?plantId=…
PRODUCTION_RECEIPT_CREATE
GET
/production-receipts?plantId=…&status=…
Một trong các quyền receipt/QC
POST
/production-receipts
PRODUCTION_RECEIPT_CREATE
POST
/production-receipts/{id}/submit
PRODUCTION_RECEIPT_CREATE
POST
/production-receipts/{id}/approve
PRODUCTION_RECEIPT_APPROVE
POST
/production-receipts/{id}/reject
PRODUCTION_RECEIPT_APPROVE
POST
/production-receipts/{id}/qc-disposition
QC_DISPOSITION

### 6.3 Request tạo Receipt

Field
Kiểu
Bắt buộc
Validation
workOrderId
number
Có
Có good WIP chưa receipt
quantity
decimal
Có
> 0 và ≤ availableToReceipt
destinationWarehouseId
number
Có
Cùng plant và active
lotNumber
string
Có điều kiện
Bắt buộc khi outputTrackingMethod=LOT_TRACKED; unique theo policy
notes
string
Không
Ghi chú operator
actor
JWT principal
Có
Backend lấy từ token
Idempotency-Key
header
Có
Chống tạo receipt lặp

### 6.4 Trường cần hiển thị

Khu vực
Field/API key
Hiển thị/ý nghĩa
Candidate
workOrderId, workOrderCode, status
WO có good WIP
Candidate
outputItemSku, outputItemName, uom
Thành phẩm
Candidate
actualGoodQuantity, receiptedQuantity, availableToReceipt
Giới hạn receipt
Candidate
outputTrackingMethod
NON_TRACKED/LOT_TRACKED
Form
quantity, destinationWarehouseId, lotNumber, notes
Dữ liệu tạo receipt
Receipt
code, status, workOrderCode
Mã chứng từ và trạng thái
Receipt
outputItemSku, outputItemName, quantity, uom
Thành phẩm nhập
Receipt
destinationWarehouseName, lotNumber
Kho đích/lot
Trace
traceId, sourceWipTraceIds
Liên kết WIP → Receipt
Output lot
outputLotId, outputLotStatus
HOLD/AVAILABLE/REJECTED
Decision
rejectionReason, qcReason
Lý do reject/QC
Actors
createdByUsername, approvedByUsername, qcByUsername
Người thao tác
Timestamps
createdAt, submittedAt, approvedAt, rejectedAt, qcAt
Audit timeline

## 7. Tác động tồn kho, lot và Sales Order fulfillment

Sự kiện
On-hand
Reserved
Available
Lot
SO fulfilled
Reserve component
Không đổi
Tăng
Giảm
AVAILABLE
Không đổi
Material Issue
Giảm
Giảm
Không giảm lần hai
Nguồn lot
Không đổi
Receipt DRAFT/Submitted
Không đổi
Không đổi
Không đổi
Chưa tạo
Không đổi
Receipt Approved
Tăng
Không đổi
Không tăng
HOLD
Không đổi
QC Available
Không đổi
Không đổi
Tăng
AVAILABLE
Tăng
QC Rejected
Không đổi
Không đổi
Không tăng
REJECTED
Không đổi

### 7.1 Phân bổ fulfillment

Fulfillment chỉ chạy khi QC_AVAILABLE commit thành công.
Phân bổ quantity AVAILABLE vào WorkOrderDemandAllocation theo thứ tự dueDate/line đã được backend thống nhất.
fulfilledQuantity của allocation và SalesOrderLine không vượt allocatedQuantity/orderedQuantity.
Sales Order status: IN_PRODUCTION khi có allocation/WO; PARTIALLY_FULFILLED khi fulfilled > 0 nhưng chưa đủ; FULFILLED khi tất cả line đủ.
Một Receipt có thể fulfill nhiều Sales Order line; một line có thể được fulfill bởi nhiều Receipt.

### 7.2 Traceability tối thiểu

Entity/event
Dữ liệu bắt buộc
Material Issue
traceId, workOrderId, reservationId, stockMovementId, lotId, actor, occurredAt
WIP Execution
traceId, workOrderId, quantities, actual times, operator, occurredAt
Production Receipt
traceId, sourceWipTraceIds, workOrderId, outputLotId, actors/timestamps
Stock Movement
traceId, type ISSUE/RECEIVE/LOT_STATUS_CHANGE, item, warehouse, lot, quantity
Audit Event
eventType, entityType, entityId, workOrderId, plantId, warehouseId/lotId, actorUserId, reason

## 8. Status, reason code và badge UI

Nhóm
Giá trị
Màu UI
Tốt/hoàn tất
AVAILABLE, ACTIVE, APPROVED, COMPLETED, FULFILLED
Xanh lá
Đang xử lý
CONFIRMED, PLANNED, RELEASED, IN_PROGRESS, PARTIALLY_FULFILLED
Xanh dương
Cảnh báo/chờ
HOLD, BLOCKED, PENDING_APPROVAL, QC_PENDING
Vàng/cam
Lỗi/từ chối
SHORTAGE, REJECTED, CANCELLED
Đỏ
Trung tính
DRAFT, INACTIVE, CLOSED
Xám

### 8.1 Planning message code

Code
Khi nào trả
UI cần hiển thị
MATERIAL_SHORTAGE
netRequirement > 0
Shortage và lượng thiếu
MISSING_BOM
Không có ACTIVE BOM cho MAKE item
BLOCKED; không cho tạo WO
MISSING_ROUTING
Không có ACTIVE Routing
BLOCKED; không cho tạo WO
PURCHASING_DEFERRED
Proposal BUY nhưng Purchasing ngoài MVP
Cảnh báo/read-only
SYSTEM_FALLBACK_USED
Không có Item-Warehouse setting
Thông báo settingSource fallback

### 8.2 Error code backend nên chuẩn hóa

Code đề xuất
HTTP
Tình huống
VALIDATION_ERROR
400
Sai field; trả errors theo field
PERMISSION_DENIED
403
Không đủ permission
ENTITY_NOT_FOUND
404
Không tìm thấy hoặc khác plant
STATE_CONFLICT
409
Action không hợp lệ với trạng thái hiện tại
IDEMPOTENCY_CONFLICT
409
Cùng key nhưng payload khác
INSUFFICIENT_AVAILABLE_STOCK
409
Không đủ tồn AVAILABLE để reserve/issue
RESERVATION_EXCEEDED
409
Issue vượt remaining reservation
PLANNED_QUANTITY_EXCEEDED
409
Cumulative good hoặc receipt vượt giới hạn
LOT_REQUIRED
400
Item LOT_TRACKED nhưng thiếu lotNumber
LOT_NOT_ELIGIBLE
409
Lot HOLD/REJECTED được dùng sai nghiệp vụ
APPROVAL_REASON_REQUIRED
400
Reject/QC thiếu reason
CONCURRENT_MODIFICATION
409
Version/stock row đã thay đổi
Error envelope đề xuất: { "code": "VALIDATION_ERROR", "message": "Quantity exceeds remaining reservation", "errors": [{ "field": "quantity", "message": "Maximum allowed is 5 PCS" }] }

## 9. API response mẫu


### 9.1 Success object

JSON: { "code": "SUCCESS", "message": "Operation completed successfully", "result": { "id": 1, "code": "WO-20260725-0001", "status": "PLANNED" } }

### 9.2 Paginated list

JSON: { "code": "SUCCESS", "message": "Success", "result": { "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 } }

### 9.3 Response sau Material Issue

Nhánh result
Field tối thiểu
materialIssue
id, code, status, workOrderId/code, item, reservationId, warehouse, lot, quantity, traceId, stockMovementId, actor, occurredAt
workOrder
id, status, componentRequirements, materialReservations, executionStartedAt
stockPosition
itemId, warehouseId, lotId, onHandQuantity, reservedQuantity, availableQuantity

### 9.4 Response sau QC disposition

Nhánh result
Field tối thiểu
receipt
id, code, status, outputLotId, outputLotStatus, qcReason, qcByUsername, qcAt, traceId
inventoryLot
id, lotNumber, itemId, warehouseId, quantity, status
stockPosition
onHandQuantity, reservedQuantity, availableQuantity
workOrder
approvedReceiptQuantity, availableReceiptQuantity, demandAllocations
salesOrders
line fulfilledQuantity, order status, linked receipt document
stockMovement
id, type=LOT_STATUS_CHANGE, traceId, occurredAt
Backend có thể trả entity chính rồi để frontend invalidate/refetch các query liên quan. Tuy nhiên mọi entity liên quan phải được commit atomically trước khi trả SUCCESS.

## 10. Transaction, concurrency và idempotency


### 10.1 Mutation cần transaction nguyên tử

Mutation
Các bản ghi phải commit cùng nhau
Convert proposal
Proposal convertedWorkOrderId + WorkOrder + allocations + snapshots
Reserve WO
Reservations + stock reserved/available + readiness + audit
Release WO
Status/releasedAt + audit; kiểm tra lại readiness dưới lock
Material Issue
Issue + reservation + stock + movement + WIP material + WO + audit
Execution
Execution record + cumulative WO + status/timestamps + audit
Approve Receipt
Receipt + HOLD lot + RECEIVE movement + stock on-hand + audit
QC disposition
Receipt/lot + LOT_STATUS_CHANGE + available stock + allocation/SO fulfillment + audit

### 10.2 Idempotency behavior

Unique scope khuyến nghị: (authenticatedUserId, operation, idempotencyKey).
Lưu request hash và response thành công.
Retry cùng key + cùng payload trả lại response cũ, không post lần hai.
Cùng key + payload khác trả IDEMPOTENCY_CONFLICT.
Không sinh key mới khi frontend retry do network timeout.

### 10.3 Concurrency

Stock reserve/issue: row lock hoặc atomic conditional update theo available/reserved.
Cumulative good/receipt: atomic update với điều kiện tổng mới không vượt planned/good WIP.
State command: kiểm tra current status trong cùng transaction.
Mọi query và mutation phải kiểm tra plant ownership ở backend.

## 11. Checklist backend Definition of Done

Hạng mục
Tiêu chí hoàn thành
Contract
Tất cả response theo envelope; list có pagination metadata.
Security
JWT, X-Plant-Id và permission được enforce phía backend.
Idempotency
Mọi mutation production hỗ trợ Idempotency-Key.
MRP
Chỉ demand hợp lệ; exclude HOLD/REJECTED; run immutable.
Snapshot
Work Order lưu BOM/Routing snapshot bất biến.
Reservation
Không trừ on-hand; chống oversubscription.
Release
Chỉ cho phép khi reserved 100%.
Issue
Không vượt reservation; stock/WIP/movement cùng traceId.
Execution
Không nhận all-zero; cumulative good không vượt planned.
Receipt
Hỗ trợ partial; tổng receipt không vượt good WIP.
Approval
Approve tạo HOLD; Reject bắt buộc reason và không post stock.
QC
AVAILABLE mới tăng available và fulfillment; REJECTED không tăng.
Audit
Actor/time/reason/traceId có ở mọi action trọng yếu.
Error
Code ổn định, message cụ thể, field errors; không trả lỗi chung chung.
Tests
Có integration test cho happy path, validation, retry và concurrent mutation.

### 11.1 Kịch bản acceptance end-to-end

Confirm Sales Order có FG demand.
Chạy Planning Run và nhận MAKE proposal READY.
Convert proposal thành Work Order PLANNED; kiểm tra allocation + snapshots.
Reserve đủ component; kiểm tra on-hand không đổi và reserved tăng.
Release Work Order.
Post Material Issue; kiểm tra stock ISSUE và WO IN_PROGRESS.
Post good/scrap/rework; kiểm tra cumulative WIP.
Tạo partial Receipt DRAFT rồi Submit; tồn kho chưa đổi.
Approve; kiểm tra output lot HOLD và on-hand tăng, available chưa tăng.
QC_AVAILABLE; kiểm tra available tăng, Stock Movement cùng trace và SO fulfillment tăng.
Retry từng mutation với cùng Idempotency-Key; không có chứng từ/tồn kho trùng.
Ngoài MVP: MES integration, full Purchasing, delivery/invoice/payment, detailed costing/OEE và advanced variance analytics chưa thuộc contract này.

# Next Phase Plan – Reporting & Operational Hardening v1

File này mô tả phase tiếp theo sau khi `Purchasing v1` đã hoàn thành.
Mọi coding agent phải đọc `AGENTS.md` trước, sau đó đọc file này trước khi sửa code.

---

## 1. Trạng Thái Hệ Thống Hiện Tại

Hệ thống đã có đầy đủ vòng nghiệp vụ lõi:

```text
MRP → PURCHASE_REQUISITION suggestion
   → Purchase Requisition (tạo từ MRP hoặc thủ công)
   → Purchase Order → Goods Receipt → Inventory RECEIVE

MRP → WORK_ORDER suggestion
   → Work Order → Material Reservation → Material Issue
   → WIP Transaction → Production Receipt → Inventory RECEIVE

Dashboard tồn kho + Low-stock alert (cơ bản)
```

Build: **PASS** | Tests: **148/148 PASS** | Modules: auth, user, organization,
inventory, bom, workorder, planning, purchasing, reporting (sơ sài)

---

## 2. Mục Tiêu Phase Này

Phase này có hai nhóm mục tiêu song song:

### Nhóm A – Purchasing Hardening & Refactoring
Sửa các gaps phát hiện sau khi hoàn thành Purchasing v1:
- Bổ sung test coverage còn thiếu (~50% test plan chưa làm)
- Tách `GoodsReceiptController` thành controller riêng
- Implement `Cancel Goods Receipt` với reversal movement

### Nhóm B – Reporting v1 (Operational Reports)
Mở rộng module reporting để đáp ứng nhu cầu vận hành thực tế:
- Work Order Execution Report
- Purchasing & Goods Receipt Report
- MRP Shortage Summary Report
- Production Variance Report
- Inventory Movement History Report

---

## 3. Phạm Vi Chi Tiết

### 3.1 Purchasing Hardening

#### 3.1.1 Tách GoodsReceiptController

Tạo file mới `GoodsReceiptController` và chuyển 3 endpoint sau từ `PurchaseOrderController`:

```text
POST /api/v1/purchase-orders/{purchaseOrderId}/goods-receipts
GET  /api/v1/purchase-orders/{purchaseOrderId}/goods-receipts
GET  /api/v1/goods-receipts/{goodsReceiptId}
```

Không thay đổi URL. Không phá API hiện có.

#### 3.1.2 Cancel Goods Receipt

Thêm:
- `POST /api/v1/goods-receipts/{goodsReceiptId}/cancel`
- Không xóa movement cũ; tạo reversal movement (`MovementType.REVERSAL`) thông qua `InventoryMovementService`
- Trừ `purchase_order_lines.received_quantity`
- Refresh PO status: `RECEIVED` → `PARTIALLY_RECEIVED` / `SENT` nếu cần
- Chỉ `POSTED` goods receipt mới được cancel
- Ghi audit `GOODS_RECEIPT_CANCELLED`

Migration mới `V22__add_goods_receipt_cancel.sql`:
```sql
ALTER TABLE goods_receipts
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancel_note  TEXT;
```

#### 3.1.3 Bổ Sung Test Purchasing

Thêm test cases còn thiếu:

**SupplierServiceTest:**
- Reject duplicate supplier code trong cùng company

**PurchaseRequisitionServiceTest:**
- Reject convert suggestion nếu status != APPROVED
- Reject convert suggestion type = WORK_ORDER
- Không convert lại suggestion đã CONVERTED
- Convert APPROVED PURCHASE_REQUISITION suggestion thành công

**PurchaseOrderServiceTest:**
- Reject tạo PO với inactive supplier
- Reject convert PR có lines với supplier khác nhau nếu request không chỉ rõ

**GoodsReceiptServiceTest:**
- Idempotency: cùng idempotency key không tạo 2 receipt
- Nhận một phần → PO status = PARTIALLY_RECEIVED
- Nhận đủ → PO status = RECEIVED
- Cancel receipt thành công (sau PROMPT 2)
- Không cancel receipt đã CANCELLED

**PurchasingMethodSecurityTest:**
- Thiếu PERM_GOODS_RECEIPT_POST → 403
- Thiếu PERM_PURCHASE_ORDER_MANAGE → 403 khi cancel PO
- Thiếu PERM_SUPPLIER_MANAGE → 403 khi create supplier

---

### 3.2 Reporting v1

Module `reporting` chỉ có controller và service.
Report queries thông qua repository của module nguồn.

#### 3.2.1 Work Order Execution Report

```text
GET /api/v1/reports/work-orders
    ?companyId=&plantId=&status=&fromDate=&toDate=&page=&size=

GET /api/v1/reports/work-orders/{workOrderId}/variance
```

Response tóm tắt: workOrderId, workOrderNo, status, plannedQuantity,
producedQuantity, scrapQuantity, duration, varianceStatus.

Variance per WO: itemId, itemCode, itemName, plannedQuantity, issuedQuantity,
variance, variancePercent.

Permission: `PERM_WORK_ORDER_READ`

#### 3.2.2 Purchasing & Goods Receipt Report

```text
GET /api/v1/reports/purchase-orders
    ?companyId=&plantId=&supplierId=&status=&fromDate=&toDate=&page=&size=

GET /api/v1/reports/purchase-orders/{purchaseOrderId}/goods-receipts-summary
```

Response tóm tắt: purchaseOrderNo, supplierName, status,
totalOrderedQuantity, totalReceivedQuantity, completionPercent.

Permission: `PERM_PURCHASE_ORDER_READ`

#### 3.2.3 MRP Shortage Summary Report

```text
GET /api/v1/reports/mrp-shortage
    ?mrpRunId=&companyId=&plantId=&page=&size=
```

Response per line: itemId, itemCode, itemName, requiredQuantity,
availableQuantity, shortageQuantity, suggestionType, suggestionStatus,
preferredSupplierName (nullable), leadTimeDays (nullable).

Permission: `PERM_PLANNING_READ`

#### 3.2.4 Production Variance Report

```text
GET /api/v1/reports/production-variance
    ?companyId=&plantId=&fromDate=&toDate=&status=&page=&size=
```

Permission: `PERM_WORK_ORDER_READ`

#### 3.2.5 Inventory Movement History Report

```text
GET /api/v1/reports/stock-movements
    ?warehouseId=&itemId=&movementType=&fromDate=&toDate=&page=&size=
```

Permission: `PERM_INVENTORY_READ`

---

### 3.3 Migration

```text
V22__add_goods_receipt_cancel.sql
```

Không cần migration cho reporting.

---

## 4. Module Design

### Purchasing bổ sung

```text
module/purchasing/controller/GoodsReceiptController.java     [NEW - tách từ PurchaseOrderController]
module/purchasing/service/GoodsReceiptService.java           [MODIFY - thêm cancel()]
module/purchasing/dto/GoodsReceiptCancelRequest.java         [NEW]
```

### Reporting mở rộng

```text
module/reporting/controller/WorkOrderReportController.java      [NEW]
module/reporting/controller/PurchasingReportController.java     [NEW]
module/reporting/controller/MrpReportController.java            [NEW]
module/reporting/controller/InventoryReportController.java      [MODIFY - thêm stock movement]
module/reporting/service/WorkOrderReportService.java            [NEW]
module/reporting/service/PurchasingReportService.java           [NEW]
module/reporting/service/MrpReportService.java                  [NEW]
module/reporting/dto/*ReportResponse.java                       [NEW]
```

---

## 5. Integration

- `WorkOrderReportService` đọc từ WorkOrderRepository, MaterialIssueRepository, ProductionReceiptRepository
- `PurchasingReportService` đọc từ PurchaseOrderRepository, GoodsReceiptRepository
- `MrpReportService` đọc từ MrpRequirementLineRepository, SupplySuggestionRepository, ItemSupplierRepository
- Cancel GR gọi `InventoryMovementService.reverseReceive(...)` (thêm method nếu chưa có)

### InventoryMovementService.reverseReceive(...)

Nếu chưa có, thêm method:
```java
InventoryMovementResult reverseReceive(
    UUID itemId,
    UUID warehouseId,
    UUID lotId,
    BigDecimal quantity,
    String referenceType,
    String referenceId,
    String idempotencyKey
);
```
Tạo movement với `MovementType.REVERSAL`, direction `OUT`, trừ `stock_balances`.

---

## 6. RBAC / Permission

Reuse permission hiện có:
- `PERM_WORK_ORDER_READ` → Work Order reports
- `PERM_PURCHASE_ORDER_READ` → Purchasing reports
- `PERM_PLANNING_READ` → MRP Shortage report
- `PERM_INVENTORY_READ` → Stock movement history
- `PERM_PURCHASE_ORDER_MANAGE` → Cancel Goods Receipt

---

## 7. Audit

Thêm `GOODS_RECEIPT_CANCELLED` vào `AuditAction` enum.

---

## 8. N+1 / Performance

- Tất cả report endpoint bắt buộc pagination.
- Không join-fetch collection cùng pageable.
- MRP shortage: dùng JOIN aggregate, không query từng item.
- WO variance: dùng JOIN FETCH, không N+1 per component.
- Stock movement history: dùng index hiện có (warehouseId, itemId, createdAt).

---

## 9. Test Plan

### Purchasing Hardening
- Cancel POSTED receipt → status CANCELLED, reversal movement created, PO qty giảm
- PO status hồi về PARTIALLY_RECEIVED hoặc SENT sau cancel
- Cancel CANCELLED receipt → reject
- Test idempotency, partial receive, full receive
- Security tests cho từng permission

### Reporting
- WO report list với filter → đúng danh sách, pagination đúng
- WO variance per component → đúng planned vs actual
- PO report với completionPercent đúng
- MRP shortage với preferredSupplier nullable
- Stock movement history với filter movementType, date range

---

## 10. Definition Of Done

Phase này hoàn thành khi:

- [ ] Code compile
- [ ] 148 test cũ vẫn pass
- [ ] Test mới (Purchasing hardening) pass
- [ ] Test mới (Reporting) pass
- [ ] Migration V22 đúng thứ tự, không sửa migration cũ
- [ ] `GoodsReceiptController` tách thành file riêng, URL không đổi
- [ ] Cancel GR tạo reversal movement đúng
- [ ] PO status cập nhật đúng sau cancel receipt
- [ ] Tất cả report endpoint có pagination
- [ ] Không có N+1 rõ ràng ở report endpoint
- [ ] Có permission guard cho tất cả report endpoint
- [ ] Không expose entity trực tiếp, dùng DTO

---

## 11. Không Làm Trong Phase Này

- Sales Order
- Quality Control / Inspection
- Costing / Cost Roll
- Supplier Contract / Pricing nâng cao
- Invoice / Payment / Accounting
- MPS Calendar đầy đủ
- MES Integration / OEE
- Background async job
- Transactional Outbox pattern
- RTR token reuse detection
- Password reset flow

---

## 12. Prompt Chi Tiết Để Thực Hiện

Dưới đây là các prompt chi tiết để coding agent thực hiện từng phần.
Thứ tự gợi ý: 1 → 2 → 3 → 4, 5, 6, 7 (song song) → 8.

---

### PROMPT 1 – Tách GoodsReceiptController

```
Context: Manufacturing ERP. Đọc AGENTS.md và NEXT_PHASE_PLAN.md trước khi làm.

Yêu cầu:
Tách goods receipt endpoints khỏi PurchaseOrderController thành GoodsReceiptController riêng.

Việc cần làm:
1. Tạo:
   src/main/java/com/erp/manufacturing/module/purchasing/controller/GoodsReceiptController.java

2. Chuyển 3 method sau từ PurchaseOrderController:
   - POST /api/v1/purchase-orders/{purchaseOrderId}/goods-receipts
   - GET  /api/v1/purchase-orders/{purchaseOrderId}/goods-receipts
   - GET  /api/v1/goods-receipts/{goodsReceiptId}

3. Giữ nguyên URL. Xóa 3 method đó khỏi PurchaseOrderController.
   PurchaseOrderController không còn inject GoodsReceiptService nữa.

Ràng buộc:
- Không thay đổi logic service/repository.
- Không thay đổi URL.
- 148 test hiện tại vẫn pass sau tách.
- Dùng cùng pattern controller hiện có (@RestController, @RequiredArgsConstructor, @Tag).
```

---

### PROMPT 2 – Cancel Goods Receipt

```
Context: Manufacturing ERP. Đọc AGENTS.md và NEXT_PHASE_PLAN.md trước khi làm.

Yêu cầu:
Implement Cancel Goods Receipt với reversal inventory movement.

Việc cần làm:

1. Migration V22__add_goods_receipt_cancel.sql:
   ALTER TABLE goods_receipts
       ADD COLUMN cancelled_at TIMESTAMPTZ,
       ADD COLUMN cancel_note  TEXT;

2. Thêm AuditAction.GOODS_RECEIPT_CANCELLED vào AuditAction.java nếu chưa có.

3. Nếu InventoryMovementService chưa có method reverse, thêm:
   InventoryMovementResult reverseReceive(
       UUID itemId, UUID warehouseId, UUID lotId,
       BigDecimal quantity,
       String referenceType, String referenceId, String idempotencyKey);
   Logic: tạo movement MovementType.REVERSAL, direction OUT, trừ stock_balances.

4. Thêm cancel() vào GoodsReceiptService:
   - @Transactional
   - @PreAuthorize: PERM_PURCHASE_ORDER_MANAGE
   - @Auditable: GOODS_RECEIPT_CANCELLED
   - Input: goodsReceiptId, cancelNote
   - Logic:
     a. Load receipt với lines + purchase order.
     b. Nếu status != POSTED → throw OPERATION_NOT_ALLOWED.
     c. Với từng receipt line:
        - Gọi inventoryMovementService.reverseReceive(...)
          với idempotency key = goodsReceiptId + ":cancel:L" + lineIndex
        - Trừ purchaseOrderLine.receivedQuantity đi receivedQuantity của line.
     d. Set receipt: status=CANCELLED, cancelledAt=now(), cancelNote.
     e. Refresh PO status:
        - Tổng receivedQty tất cả PO lines = 0 → PO status = SENT
        - Còn thiếu → PARTIALLY_RECEIVED
        - Đủ hết → RECEIVED
     f. Save receipt và PO.

5. Endpoint: POST /api/v1/goods-receipts/{goodsReceiptId}/cancel
   Body: { "cancelNote": "..." } (optional)
   Thêm vào GoodsReceiptController.

6. Thêm GoodsReceiptCancelRequest record (cancelNote String nullable).

7. Test:
   - Cancel POSTED → CANCELLED, reversal movement, PO qty giảm đúng.
   - PO từ RECEIVED → PARTIALLY_RECEIVED sau cancel một phần.
   - PO từ PARTIALLY_RECEIVED → SENT sau cancel toàn bộ.
   - Cancel CANCELLED → throw OPERATION_NOT_ALLOWED.
   - Missing permission → 403.

Ràng buộc:
- Không sửa stock_movements cũ. Chỉ append reversal.
- Trong cùng @Transactional.
```

---

### PROMPT 3 – Bổ Sung Test Purchasing

```
Context: Manufacturing ERP. Đọc AGENTS.md và NEXT_PHASE_PLAN.md trước khi làm.

Yêu cầu:
Bổ sung unit test cho module purchasing theo section 3.1.3 trong NEXT_PHASE_PLAN.md.
Không sửa test hiện có. Chỉ thêm test method mới.

File cần bổ sung:

1. SupplierServiceTest.java – thêm:
   - testCreate_duplicateCode_sameCompany_shouldThrow()

2. SupplierServiceTest.java hoặc tạo ItemSupplierServiceTest.java – thêm:
   - testAddItemSupplier_preferredConflict_shouldThrow()
     (item đã có preferred=true ACTIVE → thêm preferred=true khác → BusinessRuleException)
   - testAddItemSupplier_negativeLeadTime_shouldThrow()

3. PurchaseRequisitionServiceTest.java – thêm:
   - testConvertSuggestion_statusNotApproved_shouldThrow()
   - testConvertSuggestion_typeWorkOrder_shouldThrow()
   - testConvertSuggestion_alreadyConverted_shouldThrow()
   - testConvertApprovedSuggestion_success_suggestionStatusConverted()

4. PurchaseOrderServiceTest.java – thêm:
   - testCreate_inactiveSupplier_shouldThrow()
   - testConvertFromPR_multipleSuppliers_noOverride_shouldThrow()

5. GoodsReceiptServiceTest.java – thêm:
   - testPost_idempotency_sameKeyTwice_returnsExisting()
   - testPost_partialReceive_POBecomePartiallyReceived()
   - testPost_fullReceive_POBecomeReceived()
   - testCancel_posted_shouldCancelAndCreateReversal() [chạy sau PROMPT 2]
   - testCancel_alreadyCancelled_shouldThrow() [chạy sau PROMPT 2]

6. PurchasingMethodSecurityTest.java – thêm:
   - testPostGoodsReceipt_missingPermission_shouldReturn403()
   - testCancelPO_missingPermission_shouldReturn403()
   - testCreateSupplier_missingPermission_shouldReturn403()

Ràng buộc:
- Dùng Mockito @ExtendWith(MockitoExtension.class).
- Không sửa production code.
- Đặt tên theo convention: testScenario_condition_expectedOutcome().
```

---

### PROMPT 4 – Work Order Execution Report

```
Context: Manufacturing ERP. Đọc AGENTS.md và NEXT_PHASE_PLAN.md trước khi làm.

Yêu cầu:
Implement Work Order Execution Report trong module reporting.

Việc cần làm:

1. Tạo DTO records:
   - WorkOrderReportResponse:
     workOrderId, workOrderNo, finishedItemId, finishedItemCode, finishedItemName,
     status, plannedQuantity, producedQuantity, scrapQuantity,
     materialIssuedCount, productionReceiptCount,
     createdAt, completedAt
   - WorkOrderVarianceItemResponse:
     itemId, itemCode, itemName,
     plannedQuantity, issuedQuantity, variance, variancePercent
   - WorkOrderVarianceReportResponse:
     workOrderId, workOrderNo, status, plannedQuantity, producedQuantity,
     components: List<WorkOrderVarianceItemResponse>

2. Tạo WorkOrderReportService:
   listWorkOrders(UUID companyId, UUID plantId, WorkOrderStatus status,
                  LocalDate fromDate, LocalDate toDate, Pageable pageable)
     → PageResult<WorkOrderReportResponse>
     Query: filter theo plant, status, createdAt range. Không join-fetch collection.

   getVarianceReport(UUID workOrderId)
     → WorkOrderVarianceReportResponse
     Load WO + component lines + issued qty per item.
     JOIN FETCH hoặc @EntityGraph. Không N+1.

3. Tạo WorkOrderReportController:
   - GET /api/v1/reports/work-orders
     @PreAuthorize: PERM_WORK_ORDER_READ
   - GET /api/v1/reports/work-orders/{workOrderId}/variance
     @PreAuthorize: PERM_WORK_ORDER_READ

4. Test:
   - listWorkOrders filter status đúng.
   - getVarianceReport: variance = plannedQty - issuedQty per component.
   - Pagination đúng.

Ràng buộc:
- Không expose entity. Dùng DTO record.
- Không sửa WorkOrderService.
- Nếu cần thêm query vào repository, chỉ thêm method mới.
- variancePercent: xử lý plannedQty = 0 → trả 0 hoặc null.
```

---

### PROMPT 5 – Purchasing & Goods Receipt Report

```
Context: Manufacturing ERP. Đọc AGENTS.md và NEXT_PHASE_PLAN.md trước khi làm.

Yêu cầu:
Implement Purchasing Report trong module reporting.

Việc cần làm:

1. Tạo DTO records:
   - PurchaseOrderReportResponse:
     purchaseOrderId, purchaseOrderNo, supplierId, supplierName, status,
     totalLines, totalOrderedQuantity, totalReceivedQuantity, completionPercent,
     orderDate, expectedDate, firstReceiptPostedAt, lastReceiptPostedAt
   - GoodsReceiptSummaryResponse:
     goodsReceiptId, receiptNo, status, postedAt, cancelledAt,
     totalLines, totalReceivedQuantity, createdBy

2. Tạo PurchasingReportService:
   listPurchaseOrders(UUID companyId, UUID plantId, UUID supplierId,
                      PurchaseOrderStatus status,
                      LocalDate fromDate, LocalDate toDate, Pageable pageable)
     → PageResult<PurchaseOrderReportResponse>
     Aggregate JPQL: tính totalOrderedQty, totalReceivedQty từ PO lines.
     completionPercent = totalReceivedQty / totalOrderedQty * 100 (edge: totalOrdered=0 → 0).

   listGoodsReceiptsByPurchaseOrder(UUID purchaseOrderId)
     → List<GoodsReceiptSummaryResponse>

3. Tạo PurchasingReportController:
   - GET /api/v1/reports/purchase-orders  @PreAuthorize: PERM_PURCHASE_ORDER_READ
   - GET /api/v1/reports/purchase-orders/{purchaseOrderId}/goods-receipts-summary
     @PreAuthorize: PERM_PURCHASE_ORDER_READ

4. Test:
   - listPurchaseOrders với filter → đúng.
   - completionPercent tính đúng (kể cả edge 0).
   - listGoodsReceiptsByPurchaseOrder đúng.

Ràng buộc:
- Không sửa PurchaseOrderService, GoodsReceiptService.
- Không expose entity.
```

---

### PROMPT 6 – MRP Shortage Summary Report

```
Context: Manufacturing ERP. Đọc AGENTS.md và NEXT_PHASE_PLAN.md trước khi làm.

Yêu cầu:
Implement MRP Shortage Summary Report trong module reporting.

Việc cần làm:

1. Tạo DTO record MrpShortageReportResponse:
   mrpRunId, itemId, itemCode, itemName, itemType,
   requiredQuantity, availableQuantity, shortageQuantity,
   suggestionType, suggestionStatus, neededByDate,
   preferredSupplierId (nullable), preferredSupplierName (nullable),
   leadTimeDays (nullable)

2. Tạo MrpReportService:
   getShortageReport(UUID mrpRunId, UUID companyId, UUID plantId, Pageable pageable)
     → PageResult<MrpShortageReportResponse>
     Query:
     - Load MrpRequirementLine theo mrpRunId có shortage (requiredQty > availableQty).
     - LEFT JOIN SupplySuggestion để lấy type và status.
     - LEFT JOIN ItemSupplier (preferred=true, ACTIVE) để lấy supplierName, leadTimeDays.
     - Dùng JPQL native query hoặc DTO projection. Không load entity rồi map vòng lặp.

3. Tạo MrpReportController:
   - GET /api/v1/reports/mrp-shortage
     Params: mrpRunId (required), companyId, plantId, page, size
     @PreAuthorize: PERM_PLANNING_READ

4. Test:
   - Shortage list đúng theo mrpRunId.
   - preferredSupplier nullable không lỗi.
   - shortageQuantity = requiredQty - availableQty đúng.

Ràng buộc:
- Không sửa MrpRunService, SupplySuggestionService.
- Query phải là 1 query hoặc batch, không N+1 per item.
```

---

### PROMPT 7 – Inventory Movement History Report

```
Context: Manufacturing ERP. Đọc AGENTS.md và NEXT_PHASE_PLAN.md trước khi làm.

Yêu cầu:
Thêm inventory movement history report vào module reporting.

Việc cần làm:

1. Tạo DTO record StockMovementReportResponse:
   movementId, movementType, direction,
   itemId, itemCode, itemName,
   warehouseId, warehouseName,
   lotId (nullable), lotCode (nullable),
   quantity, referenceType, referenceId,
   note, createdAt, createdBy

2. Kiểm tra StockMovementRepository. Nếu chưa có search method, thêm:
   Page<StockMovement> search(UUID warehouseId, UUID itemId,
                               MovementType movementType,
                               Instant fromDate, Instant toDate,
                               Pageable pageable);
   Dùng @Query JPQL. Lưu ý: không join-fetch collection cùng pageable.

3. Thêm vào InventoryReportController (file hiện có):
   GET /api/v1/reports/stock-movements
     Params: warehouseId, itemId, movementType, fromDate, toDate, page, size
     @PreAuthorize: PERM_INVENTORY_READ

4. Test:
   - Filter warehouseId → đúng danh sách.
   - Filter movementType = RECEIVE → chỉ trả RECEIVE.
   - Filter date range → đúng.
   - Pagination đúng.

Ràng buộc:
- Không sửa InventoryMovementService.
- Dùng index hiện có (warehouseId, itemId, createdAt).
- Response là DTO record, không expose StockMovement entity.
```

---

### PROMPT 8 – Production Variance Report Tổng Hợp

```
Context: Manufacturing ERP. Đọc AGENTS.md và NEXT_PHASE_PLAN.md trước khi làm.
Thực hiện sau khi PROMPT 4 (Work Order Report) đã xong.

Yêu cầu:
Implement Production Variance Report tổng hợp nhiều Work Order (list summary, không detail component).

Việc cần làm:

1. Tạo DTO record ProductionVarianceReportResponse:
   workOrderId, workOrderNo,
   finishedItemId, finishedItemCode, finishedItemName,
   plannedQuantity, actualProducedQuantity,
   totalPlannedComponentQty, totalActualIssuedQty,
   overallVarianceQty, overallVariancePercent,
   workOrderStatus, completedAt

2. Thêm listProductionVariance() vào WorkOrderReportService:
   Filter: companyId, plantId, fromDate (completedAt range), toDate, status, page, size
   Aggregate query: tính totalActualIssuedQty per WO từ material_issues.
   Không N+1.

3. Thêm endpoint vào WorkOrderReportController:
   GET /api/v1/reports/production-variance
   Params: companyId, plantId, fromDate, toDate, status, page, size
   @PreAuthorize: PERM_WORK_ORDER_READ

4. Test:
   - List với filter date range → đúng.
   - overallVariancePercent tính đúng (edge: plannedQty = 0 → 0).
   - Pagination đúng.

Ràng buộc:
- Không sửa WorkOrderService.
- Aggregate query, không load từng entity.
```

---

## 13. Thứ Tự Thực Hiện Gợi Ý

```text
PROMPT 1 (Tách controller)        → Không dependency, làm trước để clean code
PROMPT 2 (Cancel GR)              → Phụ thuộc PROMPT 1
PROMPT 3 (Test Purchasing)        → Song song với PROMPT 2, test cancel sau khi PROMPT 2 xong
PROMPT 4 (WO Report)              → Độc lập, song song với 5/6/7
PROMPT 5 (Purchasing Report)      → Độc lập, song song
PROMPT 6 (MRP Shortage Report)    → Độc lập, song song
PROMPT 7 (Stock Movement Report)  → Độc lập, song song
PROMPT 8 (Variance Report)        → Sau PROMPT 4 (dùng lại WorkOrderReportService)
```

Sau khi hoàn thành tất cả: chạy `mvn test` để verify toàn bộ pass.

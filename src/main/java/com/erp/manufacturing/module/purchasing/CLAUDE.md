# module/purchasing — Business Rule Invariants

> Tách từ `CLAUDE.md` §10.5 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/purchasing/**`.

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B27 | **[D7 – sửa mã lỗi]** Không nhận vượt `orderedQuantity − receivedQuantity`; vượt → **`PLANNED_QUANTITY_EXCEEDED` (409)** **trước khi** đụng tồn kho. Trước `D7` là `OPERATION_NOT_ALLOWED` (422) — lệch với `ProductionReceiptService`/`ProductionExecutionService` vốn dùng đúng mã đó cho cùng loại lỗi "vượt trần của chứng từ" (nợ #9) | `GoodsReceiptServiceTest.postOverRemainingQuantity_failsBeforeInventoryReceive`, `GoodsReceiptControllerTest.post_overRemainingOrderedQuantity_returns409PlannedQuantityExceeded` |
| B28 | Nhận đủ → PO `RECEIVED`; nhận một phần → `PARTIALLY_RECEIVED`; cancel receipt → PO quay lại status trước đó | `GoodsReceiptServiceTest` |
| B29 | Mỗi item chỉ có **1 preferred supplier ACTIVE** | `SupplierServiceTest.addItemSupplier_secondPreferredActiveFails` |
| B72 | **[D7]** Mọi lỗi *sai trạng thái chứng từ* trong module này dùng **`STATE_CONFLICT` (409)**: PO không `SENT`/`PARTIALLY_RECEIVED` không nhận hàng · receipt không `POSTED` không cancel được · PO/PR không `DRAFT` không sửa được · PR không `APPROVED` không convert được · suggestion không `APPROVED` không convert được. `OPERATION_NOT_ALLOWED` (422) **vẫn giữ** cho validate master data (supplier `INACTIVE`, plant/warehouse/item/supplier khác company, itemSupplier không thuộc item, trùng preferred supplier) và cho dữ liệu đầu vào sai (PR nhiều supplier, suggestion sai `SupplySuggestionType`) — xem `.claude/rules/error-handling.md §5.3`. **Đừng** đổi nốt 422 cho "đồng bộ" | `GoodsReceiptControllerTest`, `PurchaseOrderControllerTest.cancel_alreadySentOrder_returns409StateConflict`, `PurchaseRequisitionControllerTest` (3 case) |

## Entry point cho module khác (`D4`, 2026-07-30)

`PurchaseOrderSupplyService` (`service/query/`) là **cách duy nhất** module khác đọc open purchase
order supply — `planning` gọi nó để cộng vào `scheduledReceipts` của MRP (`C7`; bất biến **B67** ở
`module/planning/CLAUDE.md`). Nó cố ý sao đúng khuôn `WorkOrderSupplyService`: cùng chữ ký, cùng
hành vi "đầu vào rỗng ⇒ `Map.of()` không chạm repository", **1 aggregate query** (`C14`).

Định nghĩa "open" nằm ở `OPEN_SUPPLY_STATUSES = {SENT, PARTIALLY_RECEIVED}`. Đổi tập này là **đổi số
MRP** trên toàn hệ thống — không phải tinh chỉnh nội bộ purchasing.

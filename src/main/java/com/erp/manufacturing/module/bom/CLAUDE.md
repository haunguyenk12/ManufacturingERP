# module/bom — Business Rule Invariants

> Tách từ `CLAUDE.md` §10.2 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/bom/**`.

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B7 | Mỗi product trong cùng company chỉ có **1 BOM `ACTIVE`** tại một thời điểm; activate revision mới → revision cũ tự động `INACTIVE` | `BomServiceTest.activateBom_success_deactivatesPreviousActiveRevision` |
| B8 | **Circular reference bị chặn trước khi activate** (A→B→A) | `BomServiceTest.activateBom_circularReference_fails` + `PlanningServiceTest.estimateProduction_circularActiveBom_failsDefensively` |
| B9 | Parent item chỉ được là `WIP` hoặc `FINISHED_GOOD`; component **không** được là `SERVICE` | `BomServiceTest.createBom_parentWrongType_fails`, `addLine_serviceComponent_fails` |
| B10 | `quantity_per > 0` và `0 ≤ scrap_rate < 1` | `BomServiceTest.addLine_invalidQuantityOrScrapRate_fails` |
| B11 | **[D7 – sửa mã lỗi]** Chỉ BOM `DRAFT` mới được sửa line; sai trạng thái → **`STATE_CONFLICT` (409)**. Trước `D7` là `OPERATION_NOT_ALLOWED` (422), lệch với `RoutingService.activate` vốn đã dùng 409 cho đúng loại kiểm tra này (nợ #9) | `BomServiceTest.updateLine_nonDraftBom_fails`, `BomControllerTest.updateLine_nonDraftBom_returns409StateConflict` |
| B73 | **[D7]** Trong module này chỉ `ensureDraft` là 409. Bốn chỗ còn lại **cố ý giữ `OPERATION_NOT_ALLOWED` (422)** vì là *validate master data / nội dung chứng từ*, không phải trạng thái: parent item sai `ItemType` (B9) · component là `SERVICE` (B9) · item khác company · company `INACTIVE` · activate BOM **không có line** (cùng tiêu chí với `RoutingService`: nội dung thiếu ≠ sai trạng thái). Xem `.claude/rules/error-handling.md §5.3` | `BomControllerTest.addLine_serviceComponent_returns422OperationNotAllowed`, `BomServiceTest.createBom_parentWrongType_fails` |

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

## G5 — Lượng đầu ra, phiên bản, lưu nháp một lần, sao chép (2026-10-16, `V73`)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B155 | **`quantity_per` vẫn là nguồn** (D1). `output_quantity` (mặc định 1, BOM cũ = 1) chỉ để lưu/hiển thị form theo lô: `componentQuantity = quantityPer × outputQuantity` tính lúc đọc. Lưu nháp tính `quantityPer = componentQuantity / outputQuantity` **chính xác**; thương cần > 6 số lẻ ⇒ `400` trên `lines[i].componentQuantity`, **không** làm tròn (FE Q2). MRP, snapshot WO, costing không đổi một chữ số | `BomDraftServiceTest.saveDraft_create_storesQuantityPerAndReturnsTheBatchFigureTyped`, `.saveDraft_quotientNeedingMoreThanSixDecimals_*`, `BomDraftIT` (migration ở `FlywayMigrationIT.migrate_v73_*`) |
| B156 | **Khoá họ sản phẩm** (`ItemLookupService.lockItem`, `SELECT … FOR UPDATE` row `items`) **trước** khi đọc các version: tạo (cấp `versionNumber` = max+1), sao chép, activate. Activate đọc `parentItemId` bằng query riêng, khoá, **rồi** mới load BOM — nên request sau thấy kết quả request trước. `uk_bom_headers_active_parent` một mình chỉ chặn hai ACTIVE; request thua **chết** thay vì nhường | `BomDraftIT.concurrentActivationOfTwoVersions_bothSucceed_andExactlyOneIsActive` (mutation bỏ khoá ⇒ đỏ) |
| B157 | Lưu lại nháp: dòng **giữ id theo thành phần**; xoá dòng bỏ ⇒ flush ⇒ "đỗ" dòng giữ lại ở `lineNo` âm ⇒ flush ⇒ ghi số cuối (`uk_bom_lines_bom_line_no` kiểm từng dòng, §0.40 #1). Lines là collection inverse ⇒ chạm `updatedAt` của header để `version` tăng **đúng một** (§0.40 #2). `bomId` của Công ty khác ⇒ 404 | `BomDraftServiceTest.saveDraft_replace_*`, `BomDraftIT.saveDraft_replaceSwappingLineNumbers_keepsTheLineIds` |
| B158 | Activate kiểm thêm (G5): `effectiveFrom` tương lai ⇒ **409 `EFFECTIVE_DATE_FUTURE`**; sản phẩm hoặc thành phần đã ngừng ⇒ **422 `RESOURCE_INACTIVE`**; `expectedVersion` (tuỳ chọn) lệch ⇒ 409. Đường mới (lưu nháp, nhập Excel) từ chối thành phần là **FINISHED_GOOD** (422); API dòng cũ giữ luật B9 | `BomDraftServiceTest.activate_*`, `.saveDraft_finishedGoodComponent_isRefused` |

`bomCode` duy nhất trong Công ty **không phân biệt hoa thường** (`uk_bom_headers_company_bom_code` trên `UPPER`), mỗi version một
mã. BOM tạo qua API cũ (không có mã) nhận `mã sản phẩm-revision`, trùng thì thêm `-V<n>` — cùng quy tắc backfill `V73`.

# module/inventory — Business Rule Invariants

> Tách từ `CLAUDE.md` §10.1 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/inventory/**`.

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B1 | `stock_movements` là **append-only ledger** — không sửa/xoá lịch sử. Sửa sai bằng adjustment/reversal movement mới | `GoodsReceiptServiceTest.cancelPosted_*` |
| B2 | `available = quantity − reserved_quantity`. **Tồn đã reserve không được issue thường** | `InventoryMovementServiceTest.issue_reservedStockIsNotAvailableForUnreservedIssue` |
| B3 | Chỉ lot `AVAILABLE` được reserve/issue. Lot `HOLD` / `REJECTED` / `EXPIRED` bị từ chối | `InventoryMovementServiceTest.issue_holdLot_failsBeforeStockMutation` |
| B4 | Item và Warehouse phải **cùng company** — không cross-company leak | `InventoryMovementServiceTest.receive_itemAndWarehouseInDifferentCompanies_fails` |
| B5 | POST tạo movement phải **idempotent** theo `Idempotency-Key`: gửi trùng → trả kết quả cũ, **không** cộng tồn lần 2. Phạm vi của "trùng" là **`(idempotency_key, movement_type)`** (`D6`/`V37`), **không** phải key một mình — cùng key ở nghiệp vụ khác là 2 chứng từ độc lập | `InventoryMovementServiceTest.receive_duplicateIdempotencyKey_*`, `GoodsReceiptServiceTest.postDuplicateIdempotency_*` |
| B6 | Stock update và document nghiệp vụ **cùng một transaction** | — (cần IT ở `T4`) |

## Nợ Kỹ Thuật Đã Biết

| # | Nợ | Phát hiện | Trạng thái |
|---|---|---|---|
| 1 | `StockBalanceRepository.aggregateAvailableQuantities` / `aggregatePlanningQuantities` / `aggregateAvailableQuantitiesByWarehouse` đều lọc bằng `(b.lot is null or b.lot.status = :availableStatus)`. Dereferencing `b.lot.status` như path expression khiến Hibernate sinh **INNER JOIN** tới `inventory_lots` — dòng `StockBalance` có `lot_id IS NULL` (item không lot-tracked) **không bao giờ** khớp JOIN đó nên bị loại khỏi kết quả, bất kể nhánh `is null` trong JPQL. Vi phạm ý định nghiệp vụ của **B3**. Ảnh hưởng: `InventoryAvailabilityService` và MRP planning tính thiếu tồn kho cho mọi item không lot-tracked | `StockBalanceRepositoryIT` (T4.4) | ✅ **ĐÃ SỬA (`F1.6`, 2026-07-26)** — cả 3 query dùng `left join b.lot l` tường minh + `(l is null or l.status = :availableStatus)`. `StockBalanceRepositoryIT` nay assert hành vi **đúng** (12.000000 và 30/7/23) và trở thành regression guard: quay lại implicit join ⇒ 2 test đỏ |

## Bất Biến Bổ Sung (F1)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B5b | Replay `Idempotency-Key` với **payload khác** phải trả `IDEMPOTENCY_CONFLICT` (409), không được im lặng trả document cũ. Fingerprint là SHA-256 của payload JSON, lưu ở cột `payload_hash` (V25). **Sau `D6`:** chỉ so payload **trong cùng scope `(key, movement_type)`** — cùng key + khác movement type nay là 2 chứng từ độc lập nên **không** có gì để so với nhau. Đây **không** phải B5b yếu đi: bên trong một nghiệp vụ, bảo vệ vẫn nguyên vẹn (`V37` vẫn `UNIQUE` trên cặp đó) | `InventoryMovementServiceTest.receive_sameIdempotencyKeyDifferentPayload_throwsIdempotencyConflictAndDoesNotTouchStock` |
| B5c | `payload_hash` **NULL** ⇒ không verify được ⇒ vẫn replay bình thường. Bảo vệ document ghi trước V25 và movement sinh từ child key (`:L<n>`) khỏi đột ngột lỗi. **`D6` không đổi bất biến này** (ghi tường minh để phase sau khỏi phải suy luận) | `InventoryMovementServiceTest.receive_legacyMovementWithoutPayloadHash_stillReplaysInsteadOfConflicting` |

## Bất Biến Bổ Sung (F2)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B42 | `MovementType.LOT_STATUS_CHANGE` là dòng ledger **thuần truy xuất**: đổi `lot.status`, **không** chạm `stock_balances`. Vì vậy `direction = NONE` (giá trị mới ở `V26`) — cộng dồn ledger theo direction vẫn khớp balance | `ProductionReceiptServiceTest.qcDisposition_available_*` (assert `newStatus` + không gọi `receive`) |

> **`MovementDirection` giờ có 3 giá trị** (`IN`/`OUT`/`NONE`). Mọi so sánh direction mới viết phải
> xét nhánh `NONE`; hiện chưa có `switch` nào trên enum này.

## Bất Biến Bổ Sung (D6)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B69 | **Scope của replay = scope của constraint.** `StockMovementRepository.findByIdempotencyKeyAndMovementType` và `uk_stock_movements_idempotency_key` (`V37`, `UNIQUE (idempotency_key, movement_type)`) phải **luôn khớp nhau**. Query hẹp hơn constraint ⇒ replay trả về dòng **không xác định trước** trong nhiều dòng cùng key (tệ hơn bug gốc, vì mất tính deterministic); query rộng hơn ⇒ DB từ chối một chứng từ hợp lệ. Đổi một bên thì **bắt buộc** đổi bên kia trong cùng commit | `InventoryMovementServiceTest.issue_sameKeyAlreadyUsedByAReceive_createsAnIndependentIssueMovement` (tầng query) + `FlywayMigrationIT.migrate_v37_allowsOneIdempotencyKeyPerMovementTypeInsteadOfPerTable` (tầng DB) — **cần cả hai**, mock repository không kiểm được constraint |
| B70 | `issue` và `issueReserved` **cùng** ghi `MovementType.ISSUE` ⇒ **dùng chung một scope replay**, có chủ đích: cả hai đều là "xuất kho". **Không** bịa thêm movement type để tách 2 entry point — đó là chi tiết implementation, không phải phân loại nghiệp vụ của ledger | `InventoryMovementServiceTest.issueReserved_replaysMovementCreatedByPlainIssue_becauseBothAreIssueScope` |
| B71 | `adjust` phải quyết `ADJUST_IN`/`ADJUST_OUT` **trước** khi tra replay (type là phần của scope). Hệ quả: delta = 0 / null nổ `NEGATIVE_QUANTITY` **trước** `RESOURCE_NOT_FOUND` của item/warehouse — thứ tự validate này là **có chủ đích**, không phải sơ suất | `InventoryMovementServiceTest.adjust_zeroDelta_failsBeforeAnyLookupIncludingTheReplayLookup`, `...adjust_sameKeyAlreadyUsedInbound_createsAnIndependentOutboundMovement` |

> **`material_issues` / `production_receipts` / `production_executions` vẫn `UNIQUE (idempotency_key)`
> toàn bảng** và vẫn dùng `findByIdempotencyKey` không scope. `D6` **cố ý không** đụng: mỗi bảng đó chỉ
> có một loại nghiệp vụ ghi vào, nên không dùng chung key space như ledger — chưa có triệu chứng nào.
> `goods_receipts` đã scope theo `(purchase_order_id, idempotency_key)` từ `V20`.

> **Nợ đã trả kèm ở `V26`:** `chk_stock_movements_type` (V8) chưa bao giờ liệt kê `REVERSAL`, dù
> `reverseReceive()` đã sinh loại movement này từ lúc có tính năng cancel goods receipt ⇒ mọi lần
> cancel sẽ bị DB từ chối. `V26` vá luôn vì phải `DROP/ADD` đúng constraint đó.

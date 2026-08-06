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

## Bất Biến Serial Tracking (P5, 2026-08-06)

> Thiết kế đầy đủ + quyết định đã chốt với user: `CLAUDE.md §0.33`. `SerialNumber` mirror
> `InventoryLot` nhưng luôn đại diện **đúng 1 đơn vị vật lý** — không phải bucket số lượng tuỳ ý.
> Mọi command (`receive`/`issue`/`adjust`) nhận thêm `serialId`/`serialCode` cạnh `lotId`/`lotCode`.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B96 | `Item.lotTracked` và `Item.serialTracked` **loại trừ nhau** — chốt với user (AskUserQuestion, không phải suy luận). Validate ở **cả hai tầng**: `ItemService.createItem` (422 `OPERATION_NOT_ALLOWED`, trước khi save) **và** CHECK `chk_items_tracking_exclusive` (`V52`, defense-in-depth cho dòng ghi ngoài service, vd migration/script). Immutable sau khi tạo — giống hệt `lotTracked`, `ItemUpdateRequest` không đụng tới | `ItemServiceTest.createItem_bothLotAndSerialTracked_throwsOperationNotAllowedBeforeSaving`, `FlywayMigrationIT.migrate_v52_rejectsAnItemThatIsBothLotAndSerialTracked` |
| B97 | Mọi `StockMovement` chạm một serial luôn có `quantity = 1` — `ensureSerialQuantityIsOne` chặn ở cả `receive`/`issue`/`adjust`, **trước** khi mutate balance. `receive` **luôn tạo serial mới** (không có khái niệm "nhận vào serial đã tồn tại" như lot) — `serialCode` trùng với item ném `RESOURCE_ALREADY_EXISTS` (409), không im lặng ghi đè. `issue` đòi `serialId` cụ thể (không FEFO tự động cho serial), verify `canIssue()` (`status == AVAILABLE`) rồi flip `ISSUED` — đây là **trạng thái cuối**, không có đường quay lại `AVAILABLE` ở MVP | `InventoryMovementServiceTest.receive_serialTrackedItem_createsSerialAndIncreasesBalance`, `.receive_serialTrackedItem_quantityOtherThanOne_throwsBeforeAnyWrite`, `.receive_serialTrackedItem_duplicateSerialCode_throwsResourceAlreadyExists`, `.issue_serialTrackedItem_flipsSerialToIssuedAndDecreasesBalance`, `.issue_serialNotAvailable_failsBeforeStockMutation`, `.issue_serialTrackedItemWithoutSerialId_throwsBeforeStockMutation` |

**Quyết định cần nhớ khi mở rộng serial tracking:**

1. 🔴 **`stock_balances` KHÔNG có cột `serial_id`.** Cân nhắc rõ với user: thêm cột đó (mirror `lot_id`)
   sẽ cho phép serial-tracked output mở ở `HOLD` chờ QC giống lot (xem `module/workorder/CLAUDE.md`
   mục Serial QC) nhưng phải viết lại **mọi** `StockBalanceRepository.aggregate*` JPQL — bị đánh giá là
   rủi ro/quy mô lớn nhất của cả phase. Quyết định: **không làm**, item serial-tracked dùng chung bucket
   "không lot" với item không tracked — `findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull` không đổi.
2. **`MaterialReservation` KHÔNG có `serial_id`.** Reservation vẫn thuần theo số lượng trên bucket
   chung; serial cụ thể chỉ được chọn **lúc issue** (`MaterialIssueLineRequest.serialId`) — giống mô
   hình "reserve số lượng, pick đơn vị cụ thể lúc xuất kho" thực tế.
3. **Không có FEFO tự động cho serial.** `MaterialReservationService.reserveAutomatically` không đổi —
   với component serial-tracked, việc chọn serial luôn là tường minh từ người dùng qua `serialId` trên
   dòng issue, không có "auto-pick oldest serial".
4. **Goods Receipt (`module/purchasing`) chưa nối** — xem `module/purchasing/CLAUDE.md`.

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

# module/inventory — Business Rule Invariants

> Tách từ `CLAUDE.md` §10.1 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/inventory/**`.

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B1 | `stock_movements` là **append-only ledger** — không sửa/xoá lịch sử. Sửa sai bằng adjustment/reversal movement mới | `GoodsReceiptServiceTest.cancelPosted_*` |
| B2 | `available = quantity − reserved_quantity − quality_hold_quantity`. **Tồn đã reserve hoặc đang chờ QC không được issue thường**. `quality_hold_quantity` là carrier HOLD cho output không lot/serial, không được giả làm reservation | `InventoryMovementServiceTest.issue_reservedStockIsNotAvailableForUnreservedIssue`, `.receive_nonTrackedItemWithHoldStatus_increasesOnHandAndQualityHoldButNotAvailable`, `.releaseQualityHold_nonTrackedStock_makesItAvailableWithoutChangingOnHand`, `StockBalanceRepositoryIT` |
| B3 | Chỉ lot `AVAILABLE` được reserve/issue. Lot `HOLD` / `REJECTED` / `EXPIRED` bị từ chối | `InventoryMovementServiceTest.issue_holdLot_failsBeforeStockMutation` |
| B4 | Item và Warehouse phải **cùng company** — không cross-company leak | `InventoryMovementServiceTest.receive_itemAndWarehouseInDifferentCompanies_fails` |
| B5 | POST tạo movement phải **idempotent** theo `Idempotency-Key`: gửi trùng → trả kết quả cũ, **không** cộng tồn lần 2. Phạm vi của "trùng" là **`(idempotency_key, movement_type)`** (`D6`/`V37`), **không** phải key một mình — cùng key ở nghiệp vụ khác là 2 chứng từ độc lập | `InventoryMovementServiceTest.receive_duplicateIdempotencyKey_*`, `GoodsReceiptServiceTest.postDuplicateIdempotency_*` |
| B6 | Stock update và document nghiệp vụ **cùng một transaction** | — (cần IT ở `T4`) |

## Nợ Kỹ Thuật Đã Biết

| # | Nợ | Phát hiện | Trạng thái |
|---|---|---|---|
| 1 | `StockBalanceRepository.aggregateAvailableQuantities` / `aggregatePlanningQuantities` / `aggregateAvailableQuantitiesByWarehouse` đều lọc bằng `(b.lot is null or b.lot.status = :availableStatus)`. Dereferencing `b.lot.status` như path expression khiến Hibernate sinh **INNER JOIN** tới `inventory_lots` — dòng `StockBalance` có `lot_id IS NULL` (item không lot-tracked) **không bao giờ** khớp JOIN đó nên bị loại khỏi kết quả, bất kể nhánh `is null` trong JPQL. Vi phạm ý định nghiệp vụ của **B3**. Ảnh hưởng: `InventoryAvailabilityService` và MRP planning tính thiếu tồn kho cho mọi item không lot-tracked | `StockBalanceRepositoryIT` (T4.4) | ✅ **ĐÃ SỬA (`F1.6`, cập nhật `FE4-5B3`)** — cả 3 query dùng `left join b.lot l` và nay trừ cả `quality_hold_quantity`. Test assert 9 available và planning 30/7/17, bảo vệ đồng thời non-lot join lẫn QC hold |

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

## Bất Biến Lot Lifecycle API (C2-2, 2026-08-06)

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.2`. **Không migration** — module mới chỉ là read/write API
trên dữ liệu `InventoryLot`/`StockBalance`/`StockMovement` đã tồn tại. 3 endpoint mới:
`GET /inventory/lots`, `GET /inventory/lots/{lotId}`, `POST /inventory/lots/{lotId}/status`
(`module/inventory/controller/InventoryLotController.java`).

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B102 | 🔴 **`POST /inventory/lots/{lotId}/status` không cho lot `HOLD` sinh từ Production Receipt chưa QC tự do thoát `HOLD`** — FE xác nhận (`docs/capstone2-api-gap-response.md §5` câu 2, 2026-08-06). Tín hiệu đúng **không** nằm trong `module/inventory` một mình: heuristic cùng-module (suy nguồn gốc từ `referenceType` của `RECEIVE` movement sớm nhất) có lỗ hổng thật — lot đã QC hợp lệ một lần (thoát `HOLD`) rồi bị thủ công đưa lại `HOLD` qua **chính** endpoint này sẽ bị chặn vĩnh viễn, vì heuristic không biết QC đã từng xảy ra. Đóng bằng lookup cross-module thật (xem B103) | `InventoryLotServiceTest.changeStatus_holdEscapeBlockedWhenQcRequired`, `.changeStatus_holdEscapeAllowedWhenQcNotRequired`, `.changeStatus_intoHold_neverConsultsQcLookup` |
| B103 | Gate ở B102 gọi `LotQcOriginLookupService.requiresQcDispositionBeforeRelease(lotId)` (`module/workorder/service/query/`, entry point cross-module theo `C7`) — **chỉ** đúng khi lot có dòng `ProductionReceiptLine` tham chiếu **và** chưa từng có `QualityDisposition`. Gate **chỉ** được gọi khi `current == HOLD && target != HOLD` — mọi chuyển **vào** `HOLD`, hoặc chuyển trạng thái của lot chưa từng qua sản xuất, không consult lookup này | `LotQcOriginLookupServiceTest` (4 case, cả 2 boolean) |
| B104 | `InventoryMovementService.changeLotStatus` (đã có từ `F2`) **không đổi một dòng nào** — gate B102 nằm ở tầng gọi (`InventoryLotService`, method mới), gọi **trước khi** delegate xuống. Nhờ vậy luồng QC disposition hiện có (`ProductionReceiptService.dispositionLots`, cũng là một cách hợp lệ để thoát `HOLD`) hoàn toàn không bị ảnh hưởng | Không có test regression nào đỏ trong `ProductionReceiptServiceTest`/`ProductionFlowE2EIT` sau phase này — bằng chứng bằng cách không đổi |
| B105 | Một lot có thể tồn tại ở **nhiều warehouse** (`uk_stock_balances_item_warehouse_lot` unique theo `(item, warehouse, lot)`, không phải `(item, lot)`). `GET /inventory/lots` bắt buộc `warehouseId`. `GET /inventory/lots/{lotId}` nhận `warehouseId` tuỳ chọn: có param thì authorize theo `WAREHOUSE` và chỉ trả đúng một balance của kho đó; không có param thì chỉ company/global/admin được xem toàn bộ `balances[]`. Không được cho phép theo một warehouse rồi trả balance của warehouse khác | `InventoryLotServiceTest.get_withWarehouse_returnsOnlyThatWarehouseData`, `.get_returnsRealBalancesAcrossWarehouses`, `InventoryLotMethodSecurityTest.get_withWarehouse_*` |
| B106 | `sourceMovementType`/`sourceReferenceType`/`sourceReferenceId`/`sourceAt` trên response lot lấy từ `RECEIVE` `StockMovement` **sớm nhất**. Với detail có `warehouseId`, source cũng phải thuộc đúng warehouse đó; chế độ company-wide và list giữ lookup theo lot hiện có. Đây chỉ phục vụ hiển thị nguồn gốc cho FE, không dùng để quyết định gate B102 | `InventoryLotServiceTest.list_mapsRowsAndResolvesOrigin`, `.get_withWarehouse_returnsOnlyThatWarehouseData` |
| B109 | **[FE contract fix, 2026-08-06; permission superseded by B110/V57]** `POST /items/{id}/activate` idempotent. **Chặn nếu company cha đang `INACTIVE`** (`OPERATION_NOT_ALLOWED`, 422). Từ V57 endpoint này dùng `PERM_ITEM_MANAGE`, không còn dùng `PERM_INVENTORY_MANAGE` | `ItemServiceTest.activateItem_*`, `ItemMethodSecurityTest`, `ItemControllerTest.activate_*` |
| B110 | **[FE-4 5C, 2026-08-09]** Item Master có contract riêng `PERM_ITEM_READ/MANAGE`. Item là master data dùng chung trong Company: assignment ở một Plant cho phép truy cập Item của Company cha nhưng không Company khác. `PERM_INVENTORY_*` chỉ còn gác stock/lot/movement/item-warehouse setting. V57 sao chép grant cũ để giữ custom role | `PermissionGuardTest.plantScopedItemPermissionAllowsSharedItemMasterOnlyInOwningCompany`, `InventoryPermissionGuardTest`, `ItemMethodSecurityTest`, `PermissionCatalogTest`, `FlywayMigrationIT.migrate_v57_*` |

**Quyết định cần nhớ:**

1. 🔴 **Hướng phụ thuộc mới `inventory → workorder`** (qua `LotQcOriginLookupService`, một lookup
   service — không phải repository, đúng rule `C7`). Đây là hướng **ngược** với phần lớn quan hệ
   hiện có trong repo (`workorder` thường gọi **vào** `inventory`, ví dụ `MaterialIssueService` →
   `InventoryMovementService`) — hợp lệ vì `C7` không cấm hướng, chỉ cấm gọi thẳng repository của
   module khác. Cùng tiền lệ `planning → purchasing` (`D4`) và `planning → routing` (`F5-B`): mỗi
   hướng mới đều đi qua đúng một lookup service hẹp, không mở rộng bề mặt hơn cần thiết.
2. **`InventoryLot`/`manufactureDate`/`warehouseId` không có cột mới** — `manufactureDate` trên
   response là alias của `receivedAt` (đúng pattern `bomCapturedAt` của `F8`); `warehouseId` luôn
   resolve qua `StockBalance` (B105), không thêm cột lên `InventoryLot`.
3. `POST /inventory/lots/{lotId}/status` chỉ nhận target ∈ {`AVAILABLE`, `HOLD`, `REJECTED`} —
   `EXPIRED` bị từ chối (`OPERATION_NOT_ALLOWED`, 422) vì chưa có luồng chuyển-tay-sang-`EXPIRED`
   nào đã xác lập trong repo (`coding-rules.md §11.5`, tránh code speculative).
4. Permission tái dùng nguyên vẹn: `PERM_INVENTORY_READ` (list và detail có `warehouseId` kiểm tra
   theo warehouse; detail không có param qua `InventoryPermissionGuard.hasLotCompanyAccess`),
   `PERM_INVENTORY_MOVE` (changeStatus — cùng quyền gác `receive`/`issue`/`adjust`). **Không**
   permission mới ⇒ không migration seed, không đụng `docs/roles-and-permissions.md`.
5. `@Auditable(action = AuditAction.INVENTORY_LOT_STATUS_CHANGED, ...)` là action **mới**, tách khỏi
   `QC_DISPOSITION_RECORDED` — hai hành động khác nhau dù cùng đổi `lot.status`: một cái đi qua QC,
   một cái là thao tác kho thủ công.

## Bất Biến Inventory Dashboard (2026-08-14)

Nguồn: `BACKEND_HANDOFF_DASHBOARD_API_REQUIREMENTS.md` (FE). **Không migration**, không permission
mới, không endpoint mới — `GET /reports/inventory-dashboard` và `GET /reports/low-stock` đã có từ
trước, phase này bổ sung field hiển thị + hợp đồng thứ tự/limit. Bản ghi: `CLAUDE.md §0.43`.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B114 | Một dòng cảnh báo là một cặp **`(item, warehouse)`** — ngưỡng cấu hình theo kho, nên cùng item ở hai kho là hai dòng độc lập với hai status độc lập, và ba count trạng thái là **loại trừ nhau**. `shortageQuantity = max(0, max(safetyStock, reorderPoint) − available)`: phải tính theo **cả hai** ngưỡng, vì `LOW_STOCK` theo định nghĩa là dải **giữa** hai ngưỡng ⇒ công thức chỉ-reorder-point sẽ báo `0` cho mọi dòng `LOW_STOCK` (nghiệm thu mutation: đổi về reorder-point-only ⇒ đúng 1 case đỏ). `available = onHand − reserved − qualityHold` với `onHand` **đã loại** lot không `AVAILABLE`; không clamp `max(0, …)` vì hai CHECK constraint (`V16`, `V56`) đã ép `reserved + qualityHold <= quantity` — clamp ở đây chỉ che được vi phạm, không sửa được gì | `InventoryAlertServiceTest.listAlerts_lineCarriesTheUnitTheAvailabilityTermsAndTheShortage`, `.listAlerts_okLine_reportsNoShortage`, `.listAlerts_sameItemInTwoWarehouses_isEvaluatedIndependently`, `StockBalanceRepositoryIT.aggregateStockQuantitiesByWarehouse_splitsPerWarehouse_andKeepsTheTermsBehindAvailability` |
| B115 | Dashboard là **một** aggregate read: mọi nhãn (item, warehouse, uom, username) resolve server-side, tối đa **2 query** cho khối movement bất kể `movementLimit` (trang ledger + **một** batch username, rule C14). 🔴 `findRecentByWarehouseIds` phải giữ `left join fetch m.lot` — inner join làm **mọi** movement của item không lot-tracked biến mất khỏi feed (nghiệm thu mutation: đổi thành inner ⇒ **3/3** case IT đỏ, trong khi unit test dùng mock vẫn xanh). Movement không có `created_by` phải trả `actorUsername = null`, **không** được tra map bằng khoá `null` (`Map.of().get(null)` ném NPE — mutation bỏ guard ⇒ 1 case đỏ) | `InventoryAlertServiceTest.getDashboard_recentMovementsCarryLabelsAndResolveActorsInOneBatch`, `StockMovementRepositoryIT` (3 case) |

**Quyết định cần nhớ:**

1. `uomCode` = `Item.unit` (text tự do). `items` **vẫn chưa** có FK sang `uoms` (`C2-3` cố ý dừng ở
   đó) — dashboard chỉ hiển thị lại thứ item master đang có, không tự chuẩn hoá.
2. `recentMovements[]` dùng DTO **riêng** (`DashboardRecentMovementResponse`), không mở rộng
   `StockMovementResponse` của `GET /inventory/movements` — hai endpoint trả lời hai câu hỏi khác
   nhau, và chỉ dashboard mới đáng trả giá join nhãn (trang cố định ≤ 20 dòng).
3. `referenceNo` (số chứng từ nghiệp vụ) **cố ý chưa làm**: `referenceType` là text tự do do service
   ghi (`WORK_ORDER`, `GOODS_RECEIPT`, …) nên resolve nó cần một lookup cross-module **cho mỗi loại**.
   FE xếp nó vào nhóm "nên có", không phải tối thiểu.
4. `lowStockLimit`/`movementLimit` **kẹp** về `[1, 20]` thay vì trả 400 — theo đúng tiền lệ
   `PageableFactory` (`A4`) của repo, không phải bỏ sót validate.
5. `getStockQuantitiesByWarehouse` **thay** `getAvailableQuantitiesByWarehouse` (không phải thêm
   mới): consumer duy nhất của nó cần cả ba số hạng, giữ hai method là hai query cho cùng dữ liệu.

## Bất Biến Available Theo Lot Status (2026-08-14)

Nguồn: `live-data-audit.md` (FE) — lot `HOLD` hiển thị `available = 2` trên `GET /inventory/lots` và
`GET /inventory/balances`. **Không migration**, không permission mới, không endpoint mới. Bản ghi:
`CLAUDE.md §0.44`.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B116 | `availableQuantity` **trên response** phải bằng `0` khi `lot.status != AVAILABLE` (B3 cấm issue/reserve lot đó), trong khi `onHandQuantity`/`quantity` **giữ nguyên số thật** — hàng có tồn tại, chỉ là chưa dùng được; giấu nó đi là phá màn hình kiểm kê. 🔴 **Sửa ở `InventoryMapper.issuableQuantity`, KHÔNG sửa `StockBalance.availableQuantity()`**: domain method là phép tính thuần theo dòng và có **3 gate ghi tồn kho** gọi nó **sau khi** đã tự validate lot status (`InventoryMovementService`, `WorkOrderExecutionSupport`, `MaterialReservationService`) — cho nó tự đọc `lot.status` sẽ làm đường QC `adjust` rút hàng khỏi lot `REJECTED` (`§0.13`) nổ `INSUFFICIENT_STOCK`. Lượng đang giữ **không** đổ vào `qualityHoldQuantity`: cột đó là carrier QC cho hàng **không** có lot (B2/V56); lý do của lot nằm ở `lot.status`, vốn đã có trong mọi response | `InventoryMapperTest` (9 case, cả 3 method mapper × AVAILABLE/HOLD/REJECTED/EXPIRED/không-lot), `InventoryLotServiceTest.list_lotOnHold_reportsZeroAvailableWithoutHidingOnHand` |

**Quyết định cần nhớ:**

1. 🔴 **Triệu chứng gốc là hai con số mâu thuẫn trong cùng một hệ thống**, không phải "thiếu field":
   `StockBalanceRepository.aggregate*` (MRP + dashboard) **luôn** lọc lot ≠ `AVAILABLE`, còn read model
   theo dòng thì không ⇒ cùng một lô hàng, dashboard nói `0`, danh sách lot nói `2`. Khi thêm bất kỳ
   read model tồn kho nào về sau, câu hỏi phải là *"số này có khớp aggregate không?"*.
2. **`EXPIRED` cũng bằng `0`** dù chưa có luồng nào tự chuyển lot sang `EXPIRED` — cùng một điều kiện
   `!= AVAILABLE`, không liệt kê status theo tên. 🔴 Nhưng lưu ý: loại trừ dựa trên **status**, không
   dựa trên `expiresAt < now` — lot quá hạn mà vẫn `AVAILABLE` **vẫn tính là available**. Đó là hiện
   trạng thật của repo (không có job hết hạn lot), đã nói rõ với FE.

---

## Bất Biến Item–Warehouse Setting + Item Unit (`G2-3`/`G2-4`, 2026-10-14)

> Số `B136`+ (không phải `B124`): `MANUFACTURING_PHASE_PLAN_P7_P11.md` đã giữ chỗ `B124`–`B135` cho P7–P11 dù chưa implement — dùng chung dãy số sẽ trùng.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B137 | `upsert` **khoá row `items` (`PESSIMISTIC_WRITE`, `ItemRepository.lockByItemId`) TRƯỚC khi đọc** các kho đang giữ vai trò default của cùng item/plant. 🔴 **Đo được, không phải giả định:** bỏ khoá ⇒ hai request đồng thời đều thành công và plant còn **hai** default supply — trigger `trg_item_warehouse_default_role` (`V66`) **không** chặn được vì ở `READ COMMITTED` nó không thấy row chưa commit của transaction kia. Trigger chưa bao giờ là lớp bảo vệ trước truy cập đồng thời. `replaceDefaults` (nullable): `null` ⇒ chuyển cờ im lặng (hành vi cũ, mọi client hiện tại dựa vào); `false` ⇒ `DEFAULT_CONFLICT` (409) **và không ghi gì**; `true` ⇒ chuyển cờ. Quyết định chạy **trước** mọi mutation; chỉ vai trò được yêu cầu bị gỡ khỏi peer | **`ItemWarehouseSettingConcurrencyIT`** (2 case × 5 vòng — mutation bỏ khoá ⇒ **cả 2 đỏ ngay vòng 0**: "expected 1 but was 2" và "0 conflict"), `ItemWarehouseSettingServiceTest.upsert_replaceDefaults*`, `.upsert_locksTheItemBeforeReadingThePeers` (`InOrder`) |
| B138 | **Một row cho mỗi cặp (item, warehouse):** upsert dùng row `ACTIVE` nếu có, nếu không thì **kích hoạt lại row `INACTIVE` cập nhật gần nhất** (giữ `settingId`), chỉ insert khi cặp chưa từng tồn tại. `deactivate()` **xoá cả hai cờ default** — row `INACTIVE` mà còn cờ sẽ mang nó quay lại trigger `V66` lúc được kích hoạt lại. Kho thuộc **plant `INACTIVE`** bị từ chối (`RESOURCE_INACTIVE`). `GET` nhận `plantId` (guard `PLANT`, cross-check `X-Plant-Id`) — 🔴 đóng lỗ hổng: không truyền `warehouseId` thì trước đây trả settings của **mọi công ty** (bẫy PF-03 `§0.46`). `PUT`/`DELETE` nhận `X-Plant-Id` tuỳ chọn, so với plant của kho **trước** mọi ghi (lệch ⇒ `STATE_CONFLICT` 409, sai định dạng ⇒ 400). Nhánh không-scope cũ (GLOBAL) **giữ nguyên** để không breaking | **`ItemWarehouseSettingRepositoryIT`** (7, fixture **hai công ty** — mutation vô hiệu filter `plantId` ⇒ 2 case đỏ), `ItemWarehouseSettingServiceTest.upsert_pairWithOnlyAnInactiveRow_*`, `.deactivate_clearsBothDefaultFlags`, `.upsert_plantHeader*`, `.deactivate_plantHeader*`, `ItemWarehouseSettingMethodSecurityTest.list_byPlant_*`, `ItemWarehouseSettingControllerTest` (+7) |
| B139 | **`items.unit` lần đầu được nối với UOM master** (nợ từ `C2-3`): `createItem` đòi unit là UOM **tồn tại** (`RESOURCE_NOT_FOUND` 404) và **`ACTIVE`** (`RESOURCE_INACTIVE` 422). Cột vẫn là code string, **không FK**. `updateItem`: unit **bất biến** — khác giá trị cũ ⇒ `OPERATION_NOT_ALLOWED` (422); giống (không phân biệt hoa thường/khoảng trắng) ⇒ chấp nhận **không tra UOM**, vì item cũ có thể mang unit chưa từng có trong `uoms`. `ItemUpdateRequest.unit` **giữ** để adapter cũ gửi lại không vỡ. Importer (`ItemImportTargetHandler`) tra UOM cho **cả file trong 1 query** và báo lỗi theo dòng (`REFERENCE_NOT_FOUND`/`RULE_VIOLATION`), để `ItemService` không ném giữa vòng apply. List item thêm `status`/`type`/`lotTracked` (lọc trong SQL trước phân trang); `searchByCodeOrName` + `findByCompanyCompanyId` cũ **đã xoá** vì mồ côi | `ItemServiceTest.createItem_unitUnknown*`, `.createItem_inactiveUom_*`, `.updateItem_changingTheUnit_*`, `.updateItem_sameUnitInAnotherCase_*` (`verifyNoInteractions(uomLookupService)`), `ItemImportTargetHandlerTest.validateBatch_unknownAndInactiveUnits_*`, **`ItemRepositoryIT`** (7, mọi nhánh `null` gồm `Boolean`) |

---

## Bất Biến Tồn Kho / Lô / Chuyển Kho (`G4`, 2026-10-15)

> Nguồn: `FE_HANDOFF_GD_04_2026-10-12.md`. Quyết định user (2026-10-15): **không** làm định giá tồn kho
> (D1); chặn lô quá hạn **chỉ ở luồng kho thủ công** (issue/transfer/count/status→AVAILABLE), **không**
> đụng reservation FEFO / material issue của WO / MRP; transfer **từ chối dòng trùng** (D8); thêm
> `NO_CHANGE` + `RESERVED_STOCK` (409); aggregate thêm `heldLotQuantity`, lô quá hạn vào `expiredQuantity`.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B149 | **Đổi trạng thái lô là global** (một row `inventory_lots`) ⇒ `@PreAuthorize` đòi `PERM_INVENTORY_MOVE` ở kho trong body **và ở mọi kho còn tồn của lô** (`InventoryPermissionGuard.hasLotPositionsAccess`, `StockBalanceRepository.findWarehouseIdsHoldingLot`, `quantity > 0`). 🔴 Trước G4 chỉ kiểm **một** kho ⇒ người có quyền ở kho A đổi được chất lượng hàng nằm ở kho B ngoài scope. Thiếu ở bất kỳ kho nào ⇒ **403** chung, không nói kho nào (lệch `NEXT_PHASE_PLAN.md §11.2` D5 ghi 404: lô vẫn đọc được ở kho A nên 404 là sai nghĩa). Ba lần từ chối, **trước mọi ghi**: cùng trạng thái ⇒ `NO_CHANGE`; `expiresAt <= now` mà đích `AVAILABLE` ⇒ `LOT_NOT_ELIGIBLE` (đích HOLD/REJECTED vẫn được); còn `reservedQuantity > 0` ở bất kỳ vị thế nào ⇒ `RESERVED_STOCK`. 🔴 **Replay cùng key bỏ qua ba check mới** — nếu không, retry của request đã thành công sẽ ăn `NO_CHANGE` (R9) | `InventoryPermissionGuardTest.hasLotPositionsAccess_*` (3), `InventoryLotMethodSecurityTest.changeStatus_*`, `InventoryLotServiceTest.changeStatus_sameStatus_*`, `.changeStatus_expiredLotIntoAvailable_*`, `.changeStatus_expiredLotIntoRejected_isAllowed`, `.changeStatus_lotWithActiveReservation_*`, `.changeStatus_replayOfASucceededRequest_*`, `StockBalanceRepositoryIT.findWarehouseIdsHoldingLot_*` (fixture lô ở **hai plant**) |
| B150 | **Chuyển kho là MỘT transaction cho cả phiếu**, mọi check chạy **trước** ghi đầu tiên (`C9`): hai kho khác nhau (`VALIDATION_ERROR` trên `toWarehouseId`), cùng plant (`RESOURCE_SCOPE_MISMATCH`), kho/plant/item `ACTIVE` (`RESOURCE_INACTIVE`), 1–100 dòng, **không trùng (item, lot)** (`VALIDATION_ERROR` trên `lines[i]`, D8), item lô bắt buộc `lotId` (`LOT_REQUIRED` trên `lines[i].lotId`), lô `AVAILABLE` và chưa quá hạn (`LOT_NOT_ELIGIBLE`), serial ⇒ từ chối. Chỉ chuyển **free stock** (`quantity − reserved − qualityHold`); thiếu ⇒ `INSUFFICIENT_AVAILABLE_STOCK` kèm `errors[lines[i].quantity]` cho **mọi** dòng thiếu. Mỗi dòng = cặp `TRANSFER_OUT`/`TRANSFER_IN`, `reference_type = STOCK_TRANSFER`, `reference_id = transferId`, key con `<key>:L<n>`. Phiếu luôn `POSTED` (không cột status, cùng lối F10); `code = TR-<8 hex>` ở `@PrePersist` (`B68`). `X-Plant-Id` cross-check trên create + list (endpoint nêu plant), **không** trên detail | `StockTransferServiceTest` (14), **`StockTransferIT.transfer_failingOnTheLastLineAfterTheFirstWasWritten_leavesNothingBehind`** (mutation bỏ `@Transactional` ⇒ 3/4 case đỏ), `StockTransferControllerTest` (8), `FlywayMigrationIT.migrate_v72_*` |
| B151 | **Khoá vị thế nguồn bằng MỘT `SELECT … FOR UPDATE` theo thứ tự `balanceId`** (`lockPositionsOfItems`) trước khi đọc free stock ⇒ hai phiếu tranh nhau phần free cuối: một thắng, phiếu kia chờ rồi nhận `INSUFFICIENT_AVAILABLE_STOCK` theo dòng — không âm, không deadlock. 🔴 **Không join fetch** (Postgres từ chối `FOR UPDATE` trên phía nullable của outer join). Không có lock thì `@Version` vẫn chặn âm, nhưng kẻ thua nhận `CONCURRENT_MODIFICATION` chung chung thay vì dòng nào thiếu | **`StockTransferIT.concurrentTransfers_*`** (5 vòng — mutation bỏ `@Lock` ⇒ đỏ: nhận `ObjectOptimisticLockingFailureException` thay vì `AppException`) |
| B152 | **Lô đi theo hàng, không sao chép**: một row `inventory_lots` dùng chung cho mọi kho ⇒ mã/ngày nhận/hạn dùng giữ nguyên ở kho đích. Detail lô theo kho đích báo nguồn là `TRANSFER_IN` đầu tiên khi kho đó không có `RECEIVE` (`InventoryLotService.findOriginMovement(lot, warehouseId)`). `postedAt` cắt về **µs** trước khi lưu — response tạo phiếu, retry và đọc lại khớp từng micro giây (cùng lỗi §0.51 #3; đo A/B qua HTTP: trước sửa `…134628100Z` vs `…134628Z`) | `StockTransferIT.transfer_commitsBothSidesOfEveryLine_*` (expiry + so `postedAt` với DB), `InventoryLotServiceTest.get_withWarehouse_lotArrivedByTransfer_*` |
| B153 | **Kiểm đếm tuyệt đối** (`POST /v1/inventory/counts`): khoá **đúng một** vị thế (`lockPositionWithLot`/`WithoutLot`), so `expectedVersion` với `version` của vị thế ⇒ lệch `CONCURRENT_MODIFICATION`; `delta = counted − quantity` tính **dưới khoá**, không nhận từ client; `0` ⇒ `NO_CHANGE`; `counted < reserved` ⇒ `RESERVED_STOCK`; `counted < reserved + qualityHold` ⇒ `STATE_CONFLICT`. Ghi **một** `ADJUST_IN`/`ADJUST_OUT` (`reference_type = STOCK_COUNT`), **không** `MovementType` mới; `POST /adjust` giữ nghĩa delta. Item lô đếm theo lô (`LOT_REQUIRED`), lô phải `AVAILABLE` + chưa quá hạn; serial ⇒ từ chối; vị thế chưa tồn tại ⇒ 404. Replay tra **cả hai** loại adjust vì dấu chỉ biết sau khoá; fingerprint là command count ⇒ key từng dùng cho `/adjust` ⇒ `IDEMPOTENCY_CONFLICT` | `InventoryMovementServiceTest.count_*` (7), `InventoryControllerTest.count_*` (3), `StockBalanceRepositoryIT.lockPosition_*` |
| B154 | **Hết hạn theo đồng hồ server, chỉ ở luồng kho thủ công** (quyết định user): `issueManual` (dùng bởi `POST /v1/inventory/issue`), transfer, count, status→`AVAILABLE` từ chối lô `expiresAt <= now`. `issue()` (đường WO/material issue), `issueReserved`, FEFO reservation, aggregate của MRP/dashboard **không đổi**. Read model khớp gate: `InventoryMapper.issuableQuantity` trả `0` cho lô quá hạn; aggregate `/balances/aggregate` đưa lô `AVAILABLE` quá hạn sang `expiredQuantity` và thêm `heldLotQuantity` (lô `HOLD`) — bốn bucket available/heldLot/rejected/expired **không chồng nhau**, `qualityHoldQuantity` vẫn chỉ là carrier hàng không-lô (B116). `expiresAt` chỉ nhận khi receipt **tạo lô mới** (`@Future`; lô có sẵn ⇒ `OPERATION_NOT_ALLOWED`), field `@JsonInclude(NON_NULL)` trong command để fingerprint cũ vẫn khớp. Mọi quantity trên request inventory `@Digits(13, 6)` — số lẻ thứ 7 (kể cả số 0 thừa) ⇒ 400 thay vì Postgres làm tròn im lặng | `InventoryMovementServiceTest.issueManual_expiredLot_*`, `.issue_workOrderPath_stillIssuesFromAnExpiredAvailableLot`, `.receive_newLotWithExpiry_*`, `.receive_existingLotWithExpiry_*`, `.receiveCommand_nullExpiry_isLeftOutOfThePayloadHash`, `InventoryMapperTest.availableLotPastItsExpiry_*`, **`StockBalanceRepositoryIT.aggregateBalances_availableLotPastItsExpiry_isExpiredNotAvailable`** (mutation bỏ điều kiện hết hạn ⇒ đỏ), `InventoryControllerTest.receive_quantityWithSevenDecimals_*` |

**Giới hạn đã biết (không sửa trong G4):** `createdAt`/`updatedAt` do JPA auditing ghi có độ phân giải
100 ns trên Windows trong response vừa tạo nhưng µs khi đọc lại — áp cho **mọi** movement/balance (receive,
issue, adjust, count). Cắt µs trong service là **no-op** vì `@CreatedDate` ghi đè lúc persist (đã thử,
gỡ). Sửa đúng chỗ là `DateTimeProvider` của auditing — thay đổi toàn hệ thống, cần quyết định riêng.
Lot code vẫn **phân biệt hoa/thường** (`L1` ≠ `l1`), FE A05 kỳ vọng ngược lại — chưa đổi.

## G6a — Giữ tồn cho đơn bán (2026-10-17)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B177 | `StockBalanceRepository.findReservableBalancesFefo` (chỉ dùng bởi giữ tồn đơn bán, `module/workorder`): lô `AVAILABLE` **và** `expiresAt` null hoặc > `now`, `quantity − reserved − qualityHold > 0`, thứ tự FEFO (`expiresAt` nulls last, `receivedAt`, `balanceId` tie-break). **Không** thay `findIssuableBalancesFefo` của WO (B154: WO vẫn không xét hạn). Lượng đơn giữ cộng vào `reserved_quantity` như WO ⇒ `availableQuantity()` và mọi aggregate tự trừ; `RESERVED_STOCK` khi đổi trạng thái lô nay gồm cả dòng giữ của đơn bán | `SalesOrderReservationIT.reserveWithoutALot_*` (mutation bỏ điều kiện hạn ⇒ đỏ), `SalesOrderStockReservationServiceTest` |

## G7b — Hàng mua nhận vào vùng chờ kiểm (2026-10-18)

Không đổi một dòng nào trong `module/inventory` ngoài hai lookup (`ItemLookupService.findLotByCode`, `lotCodeExistsIgnoreCase`). Ghi lại vì
**ngữ nghĩa số liệu đổi**: `module/purchasing` nay gọi `InventoryMovementService.receive(cmd, key, LotStatus.HOLD, allowImplicitLotReuse)` cho
**mọi** lần nhận mua (B195 ở `module/purchasing/CLAUDE.md`). Hệ quả cho người đọc code inventory:

1. `stock_balances.qualityHoldQuantity` không còn chỉ là carrier QC **sản xuất** (`B2`/`V56`) — hàng mua không theo lô cũng nằm ở đó cho tới
   QC mua (`G7c`). Mọi số khả dụng (MRP, dashboard, gate xuất) tự trừ đúng, không cần sửa.
2. Đảo nhập một phiếu mua không theo lô đã nhận vào hold phải `releaseQualityHold` **trước** `reverseReceive` — nếu không CHECK
   `reserved + qualityHold <= quantity` từ chối. Phía purchasing tự làm (B196); đừng "đơn giản hoá" bằng cách cho `reverseReceive` tự gỡ hold.

## G7c — Tách lô cho QC mua, chặn đường vòng qua QC (2026-10-18)

Migration `V78`. Phía purchasing ghi bất biến chính (`B199`–`B204` ở `module/purchasing/CLAUDE.md`); ở đây chỉ phần inventory sở hữu.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B205 | **`InventoryMovementService.splitLot`** chuyển một lượng từ lô cha sang lô con **mới** cùng vật tư–kho, trong một transaction: kiểm mã lô con chưa có (không phân biệt hoa thường ⇒ `LOT_CODE_ALREADY_EXISTS`), `quantity − reserved` của lô cha đủ (thiếu ⇒ `INSUFFICIENT_AVAILABLE_STOCK`; `qualityHold` không áp cho dòng có lô), lô con kế thừa `receivedAt`/`expiresAt`, mang `parent_lot_id` + nguồn. Hai dòng sổ cái `LOT_SPLIT_OUT` (lô cha, `OUT`) + `LOT_SPLIT_IN` (lô con, `IN`) **cùng** `Idempotency-Key` — hợp lệ vì `uk_stock_movements` là `(key, movement_type)` (`B69`); replay tra theo `LOT_SPLIT_IN`. Tồn thực tế của vật tư–kho **không đổi** | `InventoryMovementServiceTest.splitLot_*`, `PurchaseQualityIT.trackedDecision_*` |
| B206 | **`parentLotId`** trên `GET /v1/inventory/lots` + `/{lotId}` (additive, cuối record); nguồn gốc của lô con là dòng `LOT_SPLIT_IN` khi lô không có `RECEIVE` nào. **`InventoryLotService.changeStatus` có thêm hai cổng** (R13): `HOLD` → khác khi `PurchaseQcLookupService.isAwaitingPurchaseQc` (hướng phụ thuộc `inventory → purchasing`, cùng khuôn `inventory → workorder` của `C2-2`), và lô `REJECTED` có `sourceReferenceType = PURCHASE_QC_DECISION` ⇒ cả hai `LOT_NOT_ELIGIBLE` | `InventoryLotServiceTest.changeStatus_holdEscapeBlockedWhilePurchaseQcPending`, `.changeStatus_purchaseQcRejectedChild_cannotBeReleased` |

## CF-1 — Chặn điều chỉnh lô thuộc nguồn QC (2026-10-27)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B263 | **`POST /v1/inventory/adjust` từ chối lô QC còn sở hữu** (`LotQcOwnershipGuard`, `409 STATE_CONFLICT`): lô còn phần chờ QC mua (`PurchaseQcLookupService.isAwaitingPurchaseQc`), lô còn phần chờ QC sản xuất (`LotQcOriginLookupService`), lô con `REJECTED` có nguồn `PURCHASE_QC_DECISION`/`PRODUCTION_QC_DECISION`. **Hẹp có chủ đích**: lô `HOLD` đặt tay không thuộc phiếu nào và lô con đạt vẫn điều chỉnh được; hàng không lô không bị chặn (`ADJUST_OUT` đã bị chặn bởi `available = quantity − reserved − qualityHold`). Guard chạy qua overload `InventoryMovementService.adjust(command, key, lotGuard)` — **sau** replay (replay trả chứng từ cũ không hỏi guard), sau khi resolve lô, **trước** khi đổi balance; nằm ngoài `InventoryMovementService` để service đó không phụ thuộc purchasing/workorder. Đường nội bộ (`ProductionReceiptService` QC loại hàng không lô) vẫn gọi overload 2 tham số. Chưa có workflow "sửa số lượng nguồn" — adjust/count đều bị chặn, chưa có đường thay thế | `LotQcOwnershipGuardTest` (8), `InventoryMovementServiceTest.adjust_lotGuardRefusesTheResolvedLot_*`, `.adjust_replayOfAnExistingKey_*WithoutConsultingTheLotGuard`, **`PurchaseQualityIT.adjust_lotsPurchaseQcStillOwns_areRefused_acceptedChildAdjusts`**, `.adjust_lotHeldWithoutAnyReceipt_staysAdjustable` (mutation bỏ guard / mở rộng sang mọi lô ≠ AVAILABLE ⇒ đỏ) |

## CF-2 — Hàng hết hạn bị loại khỏi hoạch định và luồng lệnh SX (2026-10-27)

> PO chốt 2026-10-27: **đảo** quyết định 2026-10-15 của `B154` ("hết hạn chỉ chặn luồng kho thủ công"). `B154` vẫn đúng cho phần read model của aggregate/mapper; phần "`issue()`/FEFO/MRP không đổi" **không còn đúng**.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B264 | **Lô `AVAILABLE` có `expiresAt <= now` (đồng hồ server) không còn là tồn khả dụng ở đâu cả**: `aggregateAvailableQuantities`, `aggregateStockQuantitiesByWarehouse`, `aggregatePlanningQuantities` (onHand/reserved/available đều loại lô đó, như lô `HOLD`), và `findIssuableBalancesFefo` (giữ chỗ tự động của WO) đều thêm `(l.expiresAt is null or l.expiresAt > :now)`. `aggregateExcludedLotCounts` đếm cả lô hết hạn còn tồn. `InventoryAvailabilityService.getPlanningQuantities` dùng **một** lần đọc đồng hồ cho cả hai query. Đường xuất WO dùng `issueManual`/`issueReservedManual` (xem `module/workorder` B265); `issue()`/`issueReserved()` không xét hạn chỉ còn cho luồng nội bộ đã phán quyết hàng (QC). Replay `Idempotency-Key` vẫn trả lời **trước** kiểm hạn ⇒ phiếu đã post trước khi lô hết hạn retry được | `StockBalanceRepositoryIT.*_cf2_*` (biên `expiresAt = now` loại, `now+1s` vào, không hạn vào, HOLD loại, 2 kho/2 nhà máy), `.feFixture_expiredLotA_freshLotB_*` (onHand 6 / available 6 / excluded 1 / FEFO chỉ lô B), `InventoryAvailabilityServiceTest.getPlanningQuantities_passesTheSameCurrentInstantToBothPlanningQueries`, `InventoryMovementServiceTest.issueManual_replayOfAnIssuePostedBeforeTheLotExpired_*` |

## CF-3 — Tách phần chờ / phần loại QC mua trong vùng chờ của hàng không lô (2026-10-27)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B266 | **`GET /v1/inventory/balances/aggregate` giải thích `qualityHoldQuantity`** bằng 4 field additive: `pendingPurchaseQcQuantity` = Σ(nhận − đạt − loại), `rejectedPurchaseQcQuantity` = Σ loại của dòng phiếu nhận **không lô**, phiếu `POSTED` + `quality_held`, đúng (vật tư, kho); `unclassifiedQualityHoldQuantity = qualityHold − pending − rejected` **không clamp** (âm = lỗi dữ liệu, để lộ ra); `qualityBreakdownKnown = unclassified == 0`. `qualityHoldQuantity` **giữ nghĩa cũ**. Phần chờ QC **sản xuất** không lô (G9c) rơi vào `unclassified` (PO chốt, không có field `…ProductionQc…`). Một query tổng hợp mỗi trang (`GoodsReceiptLineRepository.sumNonLotQualityHold` qua `PurchaseQcLookupService.nonLotQualityHoldByItem`, C14) — số không phụ thuộc phân trang hàng đợi QC. Lô không nằm trong các field này: trạng thái QC của lô nằm trên lô (`heldLotQuantity`/`rejectedQuantity`) | `PurchaseQualityIT.sumNonLotQualityHold_*` (QC nhiều lượt, nhiều phiếu, 2 kho/2 nhà máy; loại nháp/huỷ/`quality_held = false`/dòng lô), `.aggregateBalance_breaksTheQualityHoldDownIntoPendingAndRejectedPurchaseQc` (25 → 10/5 → 0/5), `InventoryServiceTest` (một lookup mỗi trang, không clamp), `PurchaseQcLookupServiceTest`, `InventoryControllerTest.aggregate_exposesThePurchaseQcBreakdownOfTheQualityHold` |
| B271 | **Mã lô duy nhất theo vật tư, không phân biệt hoa thường — bảo đảm ở DB** (`V95`, `uk_inventory_lots_item_code_ci` trên `(item_id, lower(lot_code))`, CF-9). Mọi đường tạo lô đã so khớp không phân biệt hoa thường ở tầng ứng dụng, nhưng hai request đồng thời `LOT-X` / `lot-x` cùng qua bước kiểm đó; trước `V95` DB chỉ có `uk_inventory_lots_item_code` (phân biệt hoa thường, giữ nguyên) nên cả hai lô cùng tồn tại. Kẻ thua nay chết ở index lúc flush/commit ⇒ `DataIntegrityErrorMapper` ⇒ `409 LOT_CODE_ALREADY_EXISTS`, rollback sạch (phiếu vẫn `DRAFT`, PO không tăng nhận, không biến động). Không cần `btrim` vì `chk_inventory_lots_code_trimmed` (V66). Cùng mã ở vật tư khác vẫn hợp lệ. DB có sẵn cặp chỉ khác hoa thường ⇒ `V95` thất bại có chủ đích (gộp/đổi tên lô là quyết định của người) — DB dev đo 2026-10-29: 0 cặp | `GoodsReceiptLotCodeConcurrencyIT` (2 PO, 5 vòng, CountDownLatch), `FlywayMigrationIT.migrate_v95_*` (2), `DataIntegrityErrorMapperTest.caseInsensitiveLotCodeIndex_isLotCodeAlreadyExists` (mutation bỏ index / bỏ entry mapper ⇒ đỏ) |

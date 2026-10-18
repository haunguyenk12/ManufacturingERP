# module/purchasing — Business Rule Invariants

> Tách từ `CLAUDE.md` §10.5 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/purchasing/**`.

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B27 | **[D7 – sửa mã lỗi]** Không nhận vượt `orderedQuantity − receivedQuantity`; vượt → **`PLANNED_QUANTITY_EXCEEDED` (409)** **trước khi** đụng tồn kho. Trước `D7` là `OPERATION_NOT_ALLOWED` (422) — lệch với `ProductionReceiptService`/`ProductionExecutionService` vốn dùng đúng mã đó cho cùng loại lỗi "vượt trần của chứng từ" (nợ #9) | `GoodsReceiptServiceTest.postOverRemainingQuantity_failsBeforeInventoryReceive`, `GoodsReceiptControllerTest.post_overRemainingOrderedQuantity_returns409PlannedQuantityExceeded` |
| B28 | **[G7a — đổi tên]** Nhận đủ → PO `FULLY_RECEIVED` (trước: `RECEIVED`); nhận một phần → `PARTIALLY_RECEIVED`; cancel receipt → PO quay lại theo lượng đã nhận, xuống tới `CONFIRMED` (trước: `SENT`) — một logic duy nhất `PurchaseOrder.refreshReceiptStatus()`. Cancel receipt của PO `CLOSED` ⇒ `STATE_CONFLICT`, không đảo gì (B187) | `GoodsReceiptServiceTest` |
| B29 | Mỗi item chỉ có **1 preferred supplier ACTIVE** | `SupplierServiceTest.addItemSupplier_secondPreferredActiveFails` |
| B72 | **[D7]** Mọi lỗi *sai trạng thái chứng từ* trong module này dùng **`STATE_CONFLICT` (409)**: PO không `CONFIRMED`/`PARTIALLY_RECEIVED` không nhận hàng · receipt không `POSTED` không cancel được · PO/PR không `DRAFT` không sửa được · PR không `APPROVED` không convert được · suggestion không `APPROVED` không convert được. `OPERATION_NOT_ALLOWED` (422) **vẫn giữ** cho validate master data (supplier `INACTIVE`, plant/warehouse/item/supplier khác company, itemSupplier không thuộc item, trùng preferred supplier) và cho dữ liệu đầu vào sai (PR nhiều supplier, suggestion sai `SupplySuggestionType`) — xem `.claude/rules/error-handling.md §5.3`. **Đừng** đổi nốt 422 cho "đồng bộ" | `GoodsReceiptControllerTest`, `PurchaseOrderControllerTest.cancel_alreadySentOrder_returns409StateConflict`, `PurchaseRequisitionControllerTest` (3 case) |

## Entry point cho module khác (`D4`, 2026-07-30)

`PurchaseOrderSupplyService` (`service/query/`) là **cách duy nhất** module khác đọc open purchase
order supply — `planning` gọi nó để cộng vào `scheduledReceipts` của MRP (`C7`; bất biến **B67** ở
`module/planning/CLAUDE.md`). Nó cố ý sao đúng khuôn `WorkOrderSupplyService`: cùng chữ ký, cùng
hành vi "đầu vào rỗng ⇒ `Map.of()` không chạm repository", **1 aggregate query** (`C14`).

Định nghĩa "open" nằm ở `OPEN_SUPPLY_STATUSES = {CONFIRMED, PARTIALLY_RECEIVED}` (G7a đổi tên `SENT`), lượng mở =
`ordered − received − closed`. Đổi tập này là **đổi số MRP** trên toàn hệ thống — không phải tinh chỉnh nội bộ purchasing.

## Nợ đã biết: Goods Receipt chưa hỗ trợ item serial-tracked (`P5`, 2026-08-06)

`P5` (Serial Number Tracking) chỉ nối serial vào **Material Issue** và **Production Receipt**
(`module/workorder`) — cố ý **không** đụng `GoodsReceiptService`/`GoodsReceiptLineRequest`. Nhận hàng
PO của một item `serialTracked = true` qua goods receipt hiện nay sẽ nổ `SERIAL_REQUIRED` (400) từ
`InventoryMovementService.receive`, đúng cách `LOT_REQUIRED` đã hoạt động cho item lot-tracked chưa
từng được nối — đây là lỗi **thấy được**, không phải silently sai. Muốn nhận nguyên liệu serial-tracked
qua PO thì `GoodsReceiptLineRequest`/`GoodsReceiptLine` cần thêm `serialNumber` (mirror `lotCode`),
và vì mỗi goods receipt line có thể có `receivedQuantity` > 1 trong khi serial luôn = 1 đơn vị/lần
receive, endpoint sẽ cần chấp nhận **danh sách** serial number thay vì một chuỗi đơn — khác hẳn shape
`lotCode` hiện tại. Xem `module/inventory/CLAUDE.md` (mục Serial Tracking) + `module/workorder/CLAUDE.md`
cho phần đã làm.

---

## Bất Biến Supplier — căn chỉnh FE giai đoạn 2 (`G2-2`, 2026-10-14)

> Số `B136`+ (không phải `B124`): `MANUFACTURING_PHASE_PLAN_P7_P11.md` đã giữ chỗ `B124`–`B135` cho P7–P11 dù chưa implement — dùng chung dãy số sẽ trùng.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B141 | Đường company-scoped FE §3.4 (`GET/POST /v1/companies/{companyId}/suppliers`, `POST /v1/suppliers/{id}/activate`, `POST /v1/suppliers/{id}/deactivate`) được **thêm song song**, không thay đường cũ (`/v1/suppliers?companyId=&keyword=`, `PATCH .../deactivate`) — purchasing và seed script vẫn gọi đường cũ (`§9.2 D1`). `createForCompany` mang **`@PreAuthorize`/`@Auditable` riêng** và chỉ chia sẻ routine **private** `createInCompany` với `create` — gọi method public kia qua `this` là bypass proxy, mất cả guard lẫn audit (bẫy `§0.19` hệ quả #3). `activate` idempotent, **chặn khi company `INACTIVE`** (`B108`), audit `SUPPLIER_ACTIVATED`. `contactName` (255) / `notes` (500) — cột từ `V69` — PATCH cùng luật `B136` (`null` giữ, rỗng xoá). `taxCode`/`companyCode` giữ nguyên trên response | `SupplierServiceTest` (+7: `createForCompany_*`, `update_nullKeepsContactAndNotes_blankClearsThem`, `activate_*`), `PurchasingMethodSecurityTest` (+4, deny+allow+verify chuỗi `PERM_SUPPLIER_MANAGE`), `SupplierControllerTest` (+5) |

## G6b — Đơn mua nháp từ chuyển đổi hoạch định (2026-10-18)

`PurchaseOrderService.createFromPlanning(...)`: PO `DRAFT`, số `PO-MRP-xxxxxxxx` do server sinh (thử lại khi trùng), không giá, ngày đặt
do caller truyền (hôm nay theo múi giờ nhà máy), `expectedDate` đầu phiếu = max(ngày đặt, ngày cần sớm nhất), mỗi dòng giữ ngày cần riêng.
Supplier/kho/item kiểm y như `create`. 🔴 **[G7a] Nay CÓ `@PreAuthorize` `PERM_PURCHASE_ORDER_MANAGE` trên plant** (FE §1.4: chuyển BUY cần
cả quyền mua) — trước G7a không có, vì GĐ6 hoãn quyền PO sang GĐ7. Caller duy nhất `PlanningConversionService` gọi qua proxy nên guard chạy;
bị từ chối ⇒ cả lượt chuyển đổi rollback (B192). Có `@Auditable(PURCHASE_ORDER_CREATED)`. Không gọi từ controller.

---

## G7a — Vòng đời đơn mua hàng (2026-10-18)

Nguồn: `FE_HANDOFF_GD_07_2026-10-14.md`, kế hoạch `NEXT_PHASE_PLAN.md §14` (D0 tách `G7a`/`G7b`/`G7c`, D1–D12 theo khuyến nghị). Migration
**`V76`**. Trả lời FE: `BE_RESPONSE_GD_07_2026-10-18.md`.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B187 | **Trạng thái PO** = `DRAFT`/`CONFIRMED`/`PARTIALLY_RECEIVED`/`FULLY_RECEIVED`/`CLOSED`/`CANCELLED` (D1; `V76` đổi tên dữ liệu cũ tại chỗ — **gỡ CHECK trước rồi mới UPDATE**, ngược lại thì nâng cấp chết trên mọi DB có PO `SENT`; DB trống không lộ, chỉ `FlywayMigrationIT.migrate_v76_*` seed ở V75 mới bắt được). **Đóng** (`POST /close`, body `{expectedVersion, reason}` bắt buộc) chỉ từ `CONFIRMED`/`PARTIALLY_RECEIVED` còn lượng mở: mọi dòng `closedQuantity += open`, received **không đổi**, `previous_status` ghi lại để history không phải đoán. `CHECK received + closed <= ordered` ở DB. **Huỷ**: không body = luật cũ (chỉ `DRAFT`); có body `{expectedVersion, reason}` = `DRAFT`/`CONFIRMED` (hai trạng thái này đã hàm ý chưa nhận — vế kiểm lượng thứ hai bị mutation M11 chứng minh thừa nên đã gỡ). Phiếu nhận của PO `CLOSED` không đảo được | `PurchaseOrderServiceTest` (`close_*`, `cancel_*`), `GoodsReceiptServiceTest.cancelPostedReceiptOfClosedOrder_*`, `PurchaseOrderRepositoryIT.receivedPlusClosedAboveOrdered_*`, `FlywayMigrationIT.migrate_v76_*` |
| B188 | **Đường nháp mới** `POST /v1/purchase-orders/drafts` **song song** đường cũ (D2): mã `PO-%06d` từ `purchase_order_number_seq`, bỏ qua giá trị mã cũ đã chiếm; ngày dòng kiểm **trước mọi lookup**; loại vật tư `RAW_MATERIAL`/`CONSUMABLE`/`WIP`, không theo sê-ri (FE Q6 — hệ thống không có `MRO`/`SEMI_FINISHED`) ⇒ `OPERATION_NOT_ALLOWED` theo field; dòng gán `VND`; `expectedDate` đầu phiếu = ngày dòng sớm nhất. **`PATCH /{id}`** chỉ `DRAFT`, `expectedVersion` bắt buộc, thay **toàn bộ** dòng với **id dòng ổn định** (có `id` = giữ + cập nhật, không `id` = thêm, vắng = xoá); dòng giữ nguyên vật tư **không** bị kiểm lại loại (nháp PR/MRP cũ vẫn sửa được giá/ngày). Version **luôn** tăng (`setUpdatedAt` — dòng là collection inverse, bẫy `B112`). **Xác nhận** `POST /confirm` body `{expectedVersion}` kiểm lại công ty/nhà máy/kho/NCC/vật tư ACTIVE + đủ giá (`0` hợp lệ, `null` ⇒ `VALIDATION_ERROR` field `lines[i].unitPrice`); `/send` cũ vẫn chạy, không kiểm giá | `PurchaseOrderServiceTest` (`createDraft_*`, `updateDraft_*`, `confirm_*`), `PurchaseOrderControllerTest` (G7a), `PurchaseOrderRepositoryIT.nextOrderNumber_*` |
| B189 | **PO sinh từ hoạch định khoá nguồn** (FE Q7, D12): `sourceLocked` = còn ít nhất một dòng `supply_suggestion_conversions` **chưa giải phóng** trỏ tới PO. Khi khoá, `PATCH` không đổi được `warehouseId`, `lines[i].itemId`, `lines[i].orderedQuantity`, tập dòng ⇒ **`SOURCE_LOCKED` (409)** kèm field; NCC/giá/ngày/text vẫn sửa được. `sources[]` đọc từ ledger GĐ6 qua `PurchaseOrderPlanningLinkService` (planning, rule C7) — **không** thêm cột nguồn lên dòng PO | `PurchaseOrderServiceTest.updateDraft_sourceLocked_*`, `get_mapsSourcesAndSourceLocked`, `get_onlyReleasedSources_isNotLocked` |
| B190 | **Huỷ PO trả quota hoạch định đúng một lần** (FE Q12): mọi dòng ledger PO còn giữ được đánh dấu `released_at`/`release_reason` (**không xoá**), mỗi lượt liên quan tăng `conversionVersion` (khoá theo thứ tự id cố định). Áp cho **cả** huỷ không body. Gọi lại tìm không còn dòng giữ ⇒ không đổi gì. **Đóng KHÔNG trả quota** (FE Q13). Phía planning: tổng đã chuyển chỉ cộng dòng chưa giải phóng; đề xuất `CONVERTED` có dòng ledger (kể cả đã giải phóng) tính theo ledger, chỉ đề xuất `CONVERTED` **không** dòng ledger nào mới coi là chuyển hết (đường one-shot cũ) | `PurchaseOrderServiceTest.cancel_*`, `close_partiallyReceived_*` (`never()`), `PurchaseOrderPlanningLinkServiceTest`, `MrpPlanningMapperConvertedQuantityTest`, `PlanningConversionRepositoryIT.ledgerSum_*`/`holdingTargets_*` |
| B191 | **`Idempotency-Key` cho lệnh PO** (tạo nháp, `PATCH`, confirm, cancel, close) — header **tuỳ chọn**, lưu ở bảng `purchasing_idempotency_records` `UNIQUE (idempotency_key, command)`, **không TTL**. Dấu vân tay = id PO + body (tạo nháp: body). Replay ⇒ trả **trạng thái hiện tại** của PO, không chạy lại; khác payload ⇒ `IDEMPOTENCY_CONFLICT`. Key được ghi **sau** kiểm version/trạng thái, trong cùng transaction (lỗi ⇒ key được trả lại), `saveAndFlush` để request song song cùng key dừng ở unique index | `PurchaseOrderServiceTest` (`createDraft_replay*`, `cancel_replay_*`), `PurchaseOrderControllerTest` |
| B192 | **Thứ tự dòng PO ổn định**: `PurchasingMapper.LINE_ORDER` = `createdAt` rồi id **dạng chuỗi** (không `UUID.compareTo` — so long có dấu). Dòng lưu cùng flush có `created_at` bằng nhau sau khi Postgres làm tròn µs ⇒ không tie-break thì hai lần đọc đảo thứ tự (smoke G7a tìm ra) và chỉ số `lines[i]` của lỗi confirm chỉ sai dòng. Confirm đánh chỉ số theo **cùng** comparator. Chuyển BUY cần `PERM_PURCHASE_ORDER_MANAGE` (xem mục G6b) | `PurchaseOrderServiceTest.linesWithTheSameCreatedAt_*`, `PurchaseOrderMethodSecurityTest.createFromPlanning_*` |

**Ghi nhớ khi code tiếp (G7b/G7c):**
1. Chặn huỷ/đóng khi còn phiếu nhận `DRAFT` (FE Q11) **chưa có** — phiếu nhận `DRAFT` ra đời ở `G7b`, thêm kiểm ở cùng lượt đó.
2. `remainingQuantity` trên dòng nay = `openQuantity` (trừ cả `closed`) — B27 (nhận vượt) dùng nó nên PO đóng không nhận thêm được dù trạng thái đã chặn trước.
3. `history[]` dựng từ cột (`created*`/`confirmed*`/`cancelled*`/`closed*` + `previous_status`), không có bảng sự kiện — không có bước do phiếu nhận.
4. List tính `sourceLocked` bằng **một** truy vấn cho cả trang (C14); `currencyCode` trên list luôn `null` (không tải dòng).

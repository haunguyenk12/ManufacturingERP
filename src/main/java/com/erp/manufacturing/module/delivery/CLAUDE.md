# Module `delivery` — Phiếu giao hàng (GĐ10, `G10b`)

> Thêm 2026-10-25 (`G10b`). Nguồn: `FE_HANDOFF_GD_10_2026-10-15.md` R03–R09, R19–R28; kế hoạch `NEXT_PHASE_PLAN.md §17`;
> bản ghi `CLAUDE.md §0.68`; trả lời FE `BE_RESPONSE_GD_10_2026-10-25.md`. Migration `V87`.

## 1. Vị trí và hướng phụ thuộc

- Package riêng (`D1`), **không** nằm trong `sales`: phiếu giao là chứng từ kho của một đơn bán, có vòng đời riêng
  `DRAFT → POSTED | CANCELLED`, quyền riêng (`PERM_DELIVERY_READ/_MANAGE/_POST`).
- `delivery → sales` qua `SalesOrderCommercialLookupService` (đọc đơn, khoá dòng đơn, `touch`) và
  `SalesOrderStockReservationService` (phần giữ của chính đơn). `delivery → inventory` qua `InventoryMovementService`
  (`lockPositions`, `issueReservedManual`, `issueManual`). `delivery → organization` để gắn nhãn kho/nhà máy.
- `sales → delivery` chỉ qua `DeliveryLookupService` (không `@PreAuthorize`): tổng đã giao theo dòng đơn, "đơn còn phiếu giao
  mở không". Không vòng bean — `DeliveryLookupService` chỉ phụ thuộc repository.
- Lệnh trả `CommercialCommandResult` và dùng `commercial_idempotency_records` của `G10a` (`D9`).

## 2. Bất biến

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B241 | **Hạn mức giao dẫn xuất từ sổ, không cột đếm.** Mỗi dòng đơn: `còn nhận = đặt − Σ dòng phiếu POSTED − Σ dòng phiếu DRAFT khác`. Phiếu đang sửa **không** tính trùng phần của chính nó (`editingDeliveryId`, `sumDraftBySalesOrderLineIds(lineIds, exclude)`). Vượt ⇒ `409 PLANNED_QUANTITY_EXCEEDED` field `lines[i].quantity`. Nháp **giữ** hạn mức nhưng **không** đụng tồn | `DeliverySupportTest`, `DeliveryPostingIT.quota_excludesTheEditedDraft_andCountsPostedAndOtherDrafts` |
| B242 | **Nguồn = hàng tự do + phần giữ của chính dòng đơn** (`D2`: không có sổ quyền hưởng). `sourceKey` = base64url(`warehouseId|lotId`) do server phát; khoá lạ ⇒ `400`. Chỉ lô `AVAILABLE` chưa hết hạn (00:00 ngày hết hạn theo giờ nhà máy) hoặc hàng không theo lô, trong kho của nhà máy đơn. Đủ tồn: `Σ max(0, yêu cầu − phần giữ của chính dòng tại vị trí) ≤ tự do`. Phần giữ của **đơn khác** không bao giờ dùng được | `DeliverySupportTest` (lô `HOLD`, phần giữ của chính đơn), `DeliveryPostingIT.post_neverTakesAnotherOrdersHold`, `.post_ofALotThatExpiredSinceTheDraft_*` |
| B243 | **Ghi sổ nguyên tử, thứ tự khoá cố định.** Khoá dòng đơn (native `FOR UPDATE` theo id, rồi `refresh` — khuôn `B194`) → `refresh` phiếu → version → kiểm lại baseline/khách/hạn mức/tồn → `lockPositions` (thứ tự ổn định item → kho → lô) → mỗi dòng: `ISSUE` từ phần giữ của chính dòng trước (`DN:<id>:L<n>:R`, tiêu thụ reservation cũ nhất trước) rồi phần còn lại từ tự do (`:F`); **cả hai** từ chối lô hết hạn. Phiếu, tồn, reservation, `delivered`, version đơn (+1) cùng commit hoặc cùng huỷ | `DeliveryPostingIT.post_takesTheOrdersOwnHoldFirstThenFreeStock_*`, `.concurrentPosts_*` (cùng đơn), `.concurrentPostsOfTwoOrders_*` (hai đơn, cùng vị trí — chứng minh khoá vị trí; mutation bỏ `lockPositions` ⇒ đỏ) |
| B244 | **Mọi lệnh bump version đúng +1.** Dòng phiếu là bảng con ⇒ đặt `updatedAt` của header **trước** flush đầu tiên (bulk `deleteByDeliveryId` có `flushAutomatically`); đặt sau thì version +2 (bẫy `B212`, smoke `G10b` tìm ra). `POSTED`/`CANCELLED` là cuối. Sửa/huỷ nháp chỉ người tạo (`403`); ghi sổ cần `PERM_DELIVERY_POST`, người tạo ghi sổ được phiếu của mình. Replay `Idempotency-Key` **trước** version | `DeliveryPostingIT.update_thatChangesTheNoteToo_movesTheVersionByExactlyOne`, `.lifecycle_onlyTheAuthorChangesADraft_andPostedIsFinal` |
| B245 | **Chặn theo đơn:** đơn phải `CONFIRMED`/`IN_PRODUCTION`/`PARTIALLY_FULFILLED`/`FULFILLED`; baseline không biết (`B240`) hoặc không có khách master ⇒ `409 SOURCE_UNKNOWN`; khách ngừng ⇒ `422 RESOURCE_INACTIVE`. `documentDate` ≤ hôm nay theo giờ nhà máy và ≥ ngày đơn. Huỷ đơn còn phiếu `DRAFT`/`POSTED` ⇒ `409 STATE_CONFLICT` (`SalesOrderService.cancel`) | `DeliverySupportTest`, `SalesOrderServiceTest.cancel_withADeliveryBehindTheOrder_*` |

## 3. Đọc

- `GET /v1/deliveries` bắt buộc `plantId` (cross-check `X-Plant-Id`); quyền bất kỳ `PERM_DELIVERY_READ/_MANAGE/_POST`.
  Nhãn đơn/kho/lô/người dùng nạp theo batch cho cả trang (C14); `allowedActions` tính quyền **một lần mỗi nhà máy**.
- `GET /v1/deliveries/related?plantId&lotId` — phiếu `POSTED` đã lấy từ lô. Theo phiếu nhập/WO: `G10d`.
- `delivery-candidates` / `delivery-sources` treo dưới `/sales-orders/v1/{id}`; guard đọc đơn qua `findOrder` (Optional) — guard
  không được ném qua proxy `@Transactional` (rollback-only).
- **Không** có giá trị tiền / giá vốn: `valuationStatus = "PENDING_COSTING"` (`D7`).

## 4. Bẫy đã gặp

1. 🔴 Version +2 khi sửa nháp có đổi ghi chú (B244) — unit + IT xanh, chỉ smoke HTTP thấy; IT đã thêm.
2. Hai phiếu **cùng** đơn đã bị khoá dòng đơn tuần tự hoá, nên ca đó không chứng minh được khoá vị trí — cần ca **hai đơn**.
3. Không có sổ "quyền hưởng" (`D2`): lô do WO của đơn A nhập về vẫn là hàng tự do cho đơn B nếu A không giữ chỗ.

# Module `billing` — Hoá đơn bán hàng (GĐ10, `G10c`)

> Thêm 2026-10-26 (`G10c`). Nguồn: `FE_HANDOFF_GD_10_2026-10-15.md` R11–R15, R27, Q7, Q8, Q15, Q22, A14–A23; kế hoạch
> `NEXT_PHASE_PLAN.md §17`; bản ghi `CLAUDE.md §0.69`; trả lời FE `BE_RESPONSE_GD_10_2026-10-26_v2.md`. Migration `V88`.
> Thanh toán (`G10d`) sẽ thêm vào cùng module.

## 1. Vị trí và hướng phụ thuộc

- Package riêng (`D1`). Hoá đơn **không bao giờ** đụng tồn kho và **không** đổi version đơn bán (không đổi field nào của đơn).
- `billing → delivery` qua `DeliveryLookupService.findLines`/`findPostedLines`; `billing → sales` qua
  `SalesOrderCommercialLookupService` (đọc đơn, khoá dòng đơn, khách). Không có chiều ngược lại (chưa — `G10d` đưa tổng tiền lên đơn).
- Lệnh trả `CommercialCommandResult` (`type = INVOICE`), idempotency trên `commercial_idempotency_records` (`INVOICE_*`).
- Quyền `PERM_SALES_INVOICE_READ/_MANAGE/_POST` (seed ở `V86`); người có quyền hoá đơn cũng thấy giá đơn bán (`B239`).

## 2. Bất biến

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B246 | **Nguồn và hạn mức theo dòng phiếu giao.** Mỗi dòng hoá đơn trỏ một dòng **khác nhau** của một phiếu giao `POSTED` **cùng đơn** (trùng / lạ / đơn khác ⇒ `400` field `lines[i].deliveryLineId`; phiếu giao chưa `POSTED` ⇒ `409 STATE_CONFLICT`). `còn lập = giao − Σ hoá đơn POSTED − Σ hoá đơn DRAFT khác` (hoá đơn đang sửa loại theo id); vượt ⇒ `409 PLANNED_QUANTITY_EXCEEDED` field `lines[i].quantity`. Dẫn xuất từ sổ dòng hoá đơn, không cột đếm. Nháp giữ hạn mức, xem trước thì không | `SalesInvoiceIT.quota_*`, `.onlyTheAuthorChangesADraft_andSourcesArePostedLinesOfThisOrder` |
| B247 | **Tiền: đồng nguyên, HALF_UP cộng dồn theo dòng đơn** (FE R14, Q7). `amount = round((billedBefore + qty) × price) − billedAmountBefore`; nền = hoá đơn `POSTED` của dòng đơn; các dòng cùng dòng đơn trong một hoá đơn đi theo thứ tự gửi, mỗi dòng dời nền. Ba lần 1/3 ở giá 1 ⇒ 0, 1, 0. Giá lấy từ dòng đơn (đã khoá), không bao giờ từ client. Tổng ≤ `9007199254740991` | `InvoicePricingTest` (4), `SalesInvoiceIT.partialInvoicesOfAThird_*` |
| B248 | **`billingSnapshotVersion` + `PRICE_STALE`** (FE Q22, A22, A23). Snapshot mờ = hash(`pricingVersion` + nền của mọi dòng đơn hoá đơn chạm). Lưu (tạo/sửa) đòi đúng snapshot server tính **lúc này** — khác ⇒ `409 PRICE_STALE` field `billingSnapshotVersion`, không lưu. Ghi sổ tính lại dòng đã lưu dưới khoá — snapshot khác snapshot đã lưu **hoặc** số tiền khác số đã lưu ⇒ `409 PRICE_STALE`, không ghi sổ. 🔴 Nền dời mà số tiền không đổi (lượng nguyên × giá nguyên) **vẫn** stale — nếu không, dòng hoá đơn mang `billedQuantityBefore` sai | `SalesInvoiceIT.twoDraftsOnTheSameBase_*`, `.aMovedBaseIsStale_evenWhenTheAmountsWouldNotChange` (mutation bỏ so snapshot chỉ ca này đỏ), `.missingPriceIsPriceUnknown_staleSnapshotIsRefused_andATamperedAmountNeverPosts` |
| B249 | **Thiếu giá ⇒ `409 PRICE_UNKNOWN`** field `lines[i].deliveryLineId` ở xem trước/lưu/ghi sổ — không bao giờ tính 0 (FE R13, A19). Giá 0 đã biết ⇒ hoá đơn 0 đồng hợp lệ (trạng thái thanh toán `NO_AMOUNT_DUE` ở `G10d`) | `SalesInvoiceIT.missingPrice*` |
| B250 | **Vòng đời và đồng thời.** Mọi lệnh khoá dòng đơn trước (cùng khoá với phiếu giao) ⇒ hai nháp tranh cùng lượng giao xếp hàng. Replay `Idempotency-Key` **trước** version. Sửa/huỷ nháp chỉ người tạo (`403`), lý do huỷ 5–2000; ghi sổ cần `PERM_SALES_INVOICE_POST`, người tạo ghi sổ được. `POSTED`/`CANCELLED` là cuối. Sửa nháp dirty header **trước** bulk delete dòng ⇒ version đúng +1 (`B212`). Ngày: ≤ hôm nay theo giờ nhà máy, ≥ ngày phiếu giao nguồn muộn nhất, hạn ≥ ngày (CHECK `V88` cho cặp sau) | `SalesInvoiceIT.concurrentCreates_*`, `.quota_*` (version +1), `.onlyTheAuthor*`, `.database_*`, `SalesInvoiceMethodSecurityTest` |

## 3. Đọc

- `GET /v1/sales-invoices` bắt buộc `plantId` (cross-check `X-Plant-Id`); `status`, `salesOrderId`, `customerId`, `fromDate`/`toDate`,
  `search` (mã hoá đơn, tên khách, số đơn). Lọc `paymentState`: `G10d`.
- `GET /sales-orders/v1/{id}/invoice-candidates?editingInvoiceId` — dòng phiếu giao `POSTED` của đơn, phiếu cũ nhất trước, kèm đã lập / nháp này /
  nháp khác / còn lập, giá dòng đơn.
- `POST /v1/sales-invoices/preview` — cần `MANAGE`, không lưu không giữ; trả `billingSnapshotVersion` để gửi lại khi lưu.
- Lịch sử dẫn xuất từ cột ai/khi/lý do trên hoá đơn (`CREATED`/`POSTED`/`CANCELLED`). `paid`/`outstanding`/`paymentState`: `G10d`.

## 4. Bẫy đã gặp

1. 🔴 Mutation "ghi sổ bỏ so snapshot" **sống sót** ở lượt đầu: ca A22 dùng lượng lẻ nên số tiền cũng đổi và bước so số tiền bắt thay.
   Cần ca lượng nguyên — nền dời, số tiền y nguyên — mới chứng minh được bước so snapshot.

# Module `billing` — Hoá đơn bán hàng và thu tiền khách hàng (GĐ10, `G10c` + `G10d`)

> Thêm 2026-10-26 (`G10c`), mở rộng 2026-10-27 (`G10d`). Nguồn: `FE_HANDOFF_GD_10_2026-10-15.md` R11–R18, R24, R27, Q7, Q8, Q15,
> Q22, Q26–Q30, A14–A33, A37; kế hoạch `NEXT_PHASE_PLAN.md §17`; bản ghi `CLAUDE.md §0.69` (hoá đơn), `§0.70` (thanh toán); trả lời FE
> `BE_RESPONSE_GD_10_2026-10-26_v2.md`, `BE_RESPONSE_GD_10_2026-10-27_v3.md`. Migration `V88`, `V89`.

## 1. Vị trí và hướng phụ thuộc

- Package riêng (`D1`). Hoá đơn và thanh toán **không bao giờ** đụng tồn kho và **không** đổi version đơn bán (không đổi field nào của đơn).
- `billing → delivery` qua `DeliveryLookupService.findLines`/`findPostedLines`/`sumDeliveredBySalesOrderLineIds`/`sumDraftBySalesOrderLineIds`;
  `billing → sales` qua `SalesOrderCommercialLookupService` (đọc đơn, khoá dòng đơn, khách) và `SalesPriceVisibility`. Không có chiều ngược
  lại: tiến độ đơn (`fulfillment-summary`) sống **ở đây**, không phải trong `module/sales`.
- Lệnh trả `CommercialCommandResult` (`type = INVOICE` / `PAYMENT`), idempotency trên `commercial_idempotency_records` (`INVOICE_*`, `PAYMENT_*`).
- Quyền `PERM_SALES_INVOICE_*` và `PERM_CUSTOMER_PAYMENT_*` × READ/MANAGE/POST (seed ở `V86`); người có quyền hoá đơn cũng thấy giá đơn bán (`B239`).

## 2. Bất biến

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B246 | **Nguồn và hạn mức theo dòng phiếu giao.** Mỗi dòng hoá đơn trỏ một dòng **khác nhau** của một phiếu giao `POSTED` **cùng đơn** (trùng / lạ / đơn khác ⇒ `400` field `lines[i].deliveryLineId`; phiếu giao chưa `POSTED` ⇒ `409 STATE_CONFLICT`). `còn lập = giao − Σ hoá đơn POSTED − Σ hoá đơn DRAFT khác` (hoá đơn đang sửa loại theo id); vượt ⇒ `409 PLANNED_QUANTITY_EXCEEDED` field `lines[i].quantity`. Dẫn xuất từ sổ dòng hoá đơn, không cột đếm. Nháp giữ hạn mức, xem trước thì không | `SalesInvoiceIT.quota_*`, `.onlyTheAuthorChangesADraft_andSourcesArePostedLinesOfThisOrder` |
| B247 | **Tiền: đồng nguyên, HALF_UP cộng dồn theo dòng đơn** (FE R14, Q7). `amount = round((billedBefore + qty) × price) − billedAmountBefore`; nền = hoá đơn `POSTED` của dòng đơn; các dòng cùng dòng đơn trong một hoá đơn đi theo thứ tự gửi, mỗi dòng dời nền. Ba lần 1/3 ở giá 1 ⇒ 0, 1, 0. Giá lấy từ dòng đơn (đã khoá), không bao giờ từ client. Tổng ≤ `9007199254740991` | `InvoicePricingTest` (4), `SalesInvoiceIT.partialInvoicesOfAThird_*` |
| B248 | **`billingSnapshotVersion` + `PRICE_STALE`** (FE Q22, A22, A23). Snapshot mờ = hash(`pricingVersion` + nền của mọi dòng đơn hoá đơn chạm). Lưu (tạo/sửa) đòi đúng snapshot server tính **lúc này** — khác ⇒ `409 PRICE_STALE` field `billingSnapshotVersion`, không lưu. Ghi sổ tính lại dòng đã lưu dưới khoá — snapshot khác snapshot đã lưu **hoặc** số tiền khác số đã lưu ⇒ `409 PRICE_STALE`, không ghi sổ. 🔴 Nền dời mà số tiền không đổi (lượng nguyên × giá nguyên) **vẫn** stale — nếu không, dòng hoá đơn mang `billedQuantityBefore` sai | `SalesInvoiceIT.twoDraftsOnTheSameBase_*`, `.aMovedBaseIsStale_evenWhenTheAmountsWouldNotChange` (mutation bỏ so snapshot chỉ ca này đỏ), `.missingPriceIsPriceUnknown_staleSnapshotIsRefused_andATamperedAmountNeverPosts` |
| B249 | **Thiếu giá ⇒ `409 PRICE_UNKNOWN`** field `lines[i].deliveryLineId` ở xem trước/lưu/ghi sổ — không bao giờ tính 0 (FE R13, A19). Giá 0 đã biết ⇒ hoá đơn 0 đồng hợp lệ, trạng thái thanh toán `NO_AMOUNT_DUE` | `SalesInvoiceIT.missingPrice*`, `PaymentStateTest.aZeroInvoiceHasNoAmountDue` |
| B250 | **Vòng đời và đồng thời (hoá đơn).** Mọi lệnh khoá dòng đơn trước (cùng khoá với phiếu giao) ⇒ hai nháp tranh cùng lượng giao xếp hàng. Replay `Idempotency-Key` **trước** version. Sửa/huỷ nháp chỉ người tạo (`403`), lý do huỷ 5–2000; ghi sổ cần `PERM_SALES_INVOICE_POST`, người tạo ghi sổ được. `POSTED`/`CANCELLED` là cuối. Sửa nháp dirty header **trước** bulk delete dòng ⇒ version đúng +1 (`B212`). Ngày: ≤ hôm nay theo giờ nhà máy, ≥ ngày phiếu giao nguồn muộn nhất, hạn ≥ ngày (CHECK `V88` cho cặp sau) | `SalesInvoiceIT.concurrentCreates_*`, `.quota_*` (version +1), `.onlyTheAuthor*`, `.database_*`, `SalesInvoiceMethodSecurityTest` |
| B251 | **Phân bổ thanh toán: trần `amount − P − Cp` theo hoá đơn** (FE R17, A27, A31). `P` = Σ phân bổ của thanh toán `POSTED`, `Cp` = Σ phân bổ của thanh toán `DRAFT` **khác** (thanh toán đang sửa loại theo id); vượt ⇒ `409 PLANNED_QUANTITY_EXCEEDED` field `allocations[i].amount`. Dẫn xuất từ `customer_payment_allocations`, **không** cột "đã thu" trên hoá đơn. Nháp giữ phần của nó; huỷ nháp trả lại một lần | `CustomerPaymentIT.allocation_isOutstandingMinusOtherDrafts_*` (mutation bỏ Cp / bỏ P / tính phần của chính nó đều đỏ) |
| B252 | **Một thanh toán = một khách, một nhà máy, nhiều hoá đơn `POSTED`** (FE R16, A28, A29). Header `amount` = **đúng** Σ phân bổ (`400` field `amount`); mỗi phân bổ một hoá đơn **khác nhau** của **cùng khách** và cùng nhà máy (`400` field `allocations[i].invoiceId`); hoá đơn chưa `POSTED` ⇒ `409 STATE_CONFLICT`; `expectedInvoiceVersion` lệch ⇒ `409 CONCURRENT_MODIFICATION` (kiểm khi lưu, không khi ghi sổ); ngày ≤ hôm nay theo giờ nhà máy và ≥ ngày hoá đơn muộn nhất. Tiền đồng nguyên > 0, ≤ `9007199254740991`. Không có tiền ứng / chưa phân bổ | `CustomerPaymentIT.anAllocationMustBeAPostedInvoiceOfThisCustomer_*`, `.onePaymentPaysTwoInvoices_*`, `CustomerPaymentControllerTest` |
| B253 | **Ghi sổ kiểm lại dưới khoá hoá đơn và dời version mỗi hoá đơn đúng +1** (FE R18, A24–A26, A37). Mọi lệnh thanh toán khoá **hàng hoá đơn** theo id tăng dần (native `FOR UPDATE`, rồi `refresh` — khuôn `B194`), rồi mới tính `P`/`Cp`. Ghi sổ tính lại trần trên số tiền hoá đơn **hiện tại** và đặt `updatedAt` của từng hoá đơn được trả **trước** flush ⇒ version +1 đúng một lần. Ghi sổ cần `PERM_CUSTOMER_PAYMENT_POST` (không bao hàm MANAGE), ai có quyền đó ghi sổ được kể cả người tạo; sửa/huỷ chỉ người tạo; `POSTED`/`CANCELLED` là cuối (Q33) | `CustomerPaymentIT.postPaysTheInvoice_movesItsVersionByOne_*`, `.postReChecksTheOutstanding_underTheInvoiceLock`, `.concurrentCreates_onTheSameOutstanding_*` (mutation bỏ khoá ⇒ đỏ), `CustomerPaymentMethodSecurityTest` |
| B254 | **`paymentState` dẫn xuất, không lưu** (FE §3.2, Q8). `amount = 0` ⇒ `NO_AMOUNT_DUE`; `P = 0` ⇒ `UNPAID`; `P ≥ amount` ⇒ `PAID`; còn lại `PARTIALLY_PAID`. Chỉ có trên hoá đơn `POSTED` (`null` trên `DRAFT`/`CANCELLED`); `paidAmount`/`outstandingAmount` cùng điều kiện. Bộ lọc danh sách `paymentState` là **cùng** công thức viết bằng JPQL (chỉ thanh toán `POSTED`, chỉ hoá đơn `POSTED`) — đổi một nơi phải đổi cả hai | `PaymentStateTest`, `CustomerPaymentIT.paymentStateFilter_readsPostedPaymentsOnly` |
| B255 | **Tiến độ đơn và quyền tiền** (FE R24, Q26, Q28). `GET /sales-orders/v1/{id}/fulfillment-summary` cần quyền đọc đơn; lượng (`qcAccepted`/`delivered`/còn giao/nháp giao/`invoiced`) cho mọi người đọc đơn, `null` khi baseline không biết; giá dòng chỉ với quyền giá/hoá đơn (`SalesPriceVisibility`); khoản phải thu (`invoiced`/`paid`/`outstanding`) chỉ với quyền **hoá đơn** trên nhà máy. Thiếu quyền ⇒ object `commercial` **vắng mặt** (không `null`, `@JsonInclude(NON_NULL)` trên component — khuôn `B239`). Thanh toán đọc được bằng bất kỳ `PERM_CUSTOMER_PAYMENT_*`; quyền hoá đơn **không** mở thanh toán; `GET /v1/sales-invoices/{id}/payments` đòi **cả hai** | `FulfillmentSummaryServiceTest` (JSON thô), `CustomerPaymentMethodSecurityTest.invoicePayments_needInvoiceAndPaymentAuthority`, `.realGuard_*` |

## 3. Đọc

- `GET /v1/sales-invoices` bắt buộc `plantId` (cross-check `X-Plant-Id`); `status`, `salesOrderId`, `customerId`, `fromDate`/`toDate`,
  `search` (mã hoá đơn, tên khách, số đơn), `paymentState` (`B254`; chỉ trả hoá đơn `POSTED`).
- `GET /sales-orders/v1/{id}/invoice-candidates?editingInvoiceId` — dòng phiếu giao `POSTED` của đơn, phiếu cũ nhất trước, kèm đã lập / nháp này /
  nháp khác / còn lập, giá dòng đơn.
- `POST /v1/sales-invoices/preview` — cần `MANAGE`, không lưu không giữ; trả `billingSnapshotVersion` để gửi lại khi lưu.
- Lịch sử hoá đơn và thanh toán dẫn xuất từ cột ai/khi/lý do (`CREATED`/`POSTED`/`CANCELLED`) — không bảng sự kiện riêng.
- `GET /v1/customer-payments?plantId` (+ `status`, `method`, `customerId`, `invoiceId`, `fromDate`/`toDate`, `search` mã/tên khách/tham chiếu),
  `GET …/{id}`, `GET …/{id}/history`.
- `GET /v1/customer-payments/invoice-candidates?plantId&customerId&editingPaymentId` — hoá đơn `POSTED` của khách còn phải thu (hoặc đang có phần
  của nháp này), cũ nhất trước, kèm `paid`/`outstanding`/`ownDraftClaim`/`otherDraftClaim`/`availableToAllocate`. **Không** trả giá dòng đơn (Q30).
- `GET /v1/sales-invoices/{id}/payments` — mọi thanh toán từng phân bổ vào hoá đơn (mọi trạng thái), mỗi dòng chỉ phần đã phân bổ cho hoá đơn đó.

## 4. Bẫy đã gặp

1. 🔴 Mutation "ghi sổ bỏ so snapshot" **sống sót** ở lượt đầu: ca A22 dùng lượng lẻ nên số tiền cũng đổi và bước so số tiền bắt thay.
   Cần ca lượng nguyên — nền dời, số tiền y nguyên — mới chứng minh được bước so snapshot.
2. 🔴 **Sửa thanh toán chỉ đổi phân bổ** (cùng header) vẫn phải dời version: phân bổ là bảng riêng, nên `setUpdatedAt` **trước** bulk delete
   (`B212`). Ca IT đầu tiên đổi cả `amount` nên header tự dirty — mutation bỏ `setUpdatedAt` sẽ sống sót; đã thêm ca chuyển 500 000 sang hoá đơn
   khác với header y nguyên.
3. **Trạng thái đơn không đi theo tiền hay giao hàng.** Giao đủ và thu đủ, đơn vẫn `CONFIRMED` (trạng thái đơn do QC phủ — `B66`); FE đọc
   `deliveryProgress` + `commercial.outstandingAmount`, không đọc `status`.

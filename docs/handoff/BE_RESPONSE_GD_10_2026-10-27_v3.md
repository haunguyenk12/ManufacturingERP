# Giai đoạn 10 — BE trả lời FE (phần 4/4): thu tiền khách hàng, tiến độ đơn, liên kết ngược

**Bản:** v3 · **Ngày:** 27/10/2026 · **Trả lời:** `FE_HANDOFF_GD_10_2026-10-15.md` (v1). **Bản trước:** `BE_RESPONSE_GD_10_2026-10-25.md`
(phần 1–2: giá trên đơn, khách bắt buộc, phiếu giao hàng) và `BE_RESPONSE_GD_10_2026-10-26_v2.md` (phần 3: hoá đơn) — cả hai vẫn đúng, bản này
chỉ thêm. **Với bản này, toàn bộ Giai đoạn 10 đã có ở BE.**

## §0 — Kết luận

BE đã có **thu tiền khách hàng**: một phiếu thu cho một khách, phân bổ vào một hoặc nhiều hoá đơn đã ghi sổ; nháp giữ phần đã phân bổ, ghi sổ
thì hoá đơn được trả tiền. Hoá đơn nay có **đã thu / còn phải thu / trạng thái thanh toán** và lọc được theo trạng thái thanh toán. Thêm màn hình
**tiến độ đơn bán** (QC, giao, lập hoá đơn, thu tiền — mỗi số một sự thật riêng) và **"phiếu giao liên quan"** theo phiếu nhập hoặc lệnh sản xuất.
Không đụng tồn kho. Đã gọi thật trên môi trường dev.

| Nhóm | Kết quả |
|---|---|
| R16 | **Làm** — một phiếu thu = một khách, một nhà máy, nhiều hoá đơn `POSTED`; tổng phiếu phải đúng bằng tổng phân bổ |
| R17, Q30 | **Làm** — mỗi hoá đơn chỉ phân bổ được `số tiền − đã thu − phần nháp khác đang giữ`; ứng viên trả đúng số đó, không trả giá dòng đơn |
| R18 | **Làm** — ghi sổ kiểm lại dưới khoá; hoá đơn được trả tăng `version` đúng 1 |
| Q8, §3.2 | **Làm** — `paymentState`: `UNPAID` / `PARTIALLY_PAID` / `PAID` / `NO_AMOUNT_DUE` (hoá đơn 0 đồng) |
| R24 | **Làm** — `fulfillment-summary`; tiền chỉ hiện với người có quyền |
| Q38 | **Làm** — `related` theo lô **hoặc** phiếu nhập **hoặc** lệnh sản xuất |
| Q33 | Phiếu thu `POSTED` là cuối — không sửa, không huỷ, không đảo |
| Lệch contract | **7 chỗ**, §2.3 |

## §1 — Vì sao các quyết định này

1. **"Đã thu" luôn được cộng lại từ các phân bổ đã ghi sổ**, không có cột đếm nào trên hoá đơn. Phiếu nháp không làm hoá đơn "đã thu", nhưng nó
   **giữ chỗ**: người khác không phân bổ được phần đó cho tới khi nháp được ghi sổ hoặc huỷ. Khi sửa một nháp, phần của chính nó không bị tính trùng.
2. **Hai nháp tranh cùng một hoá đơn xếp hàng phía server** — người sau thấy phần người trước vừa giữ. Đo bằng hai giao dịch thật song song: luôn
   một thành công, một nhận `PLANNED_QUANTITY_EXCEEDED`.
3. **Ghi sổ phiếu thu tăng `version` của từng hoá đơn được trả.** Vì vậy một nháp lưu trên `version` cũ của hoá đơn sẽ bị từ chối ở lần lưu kế tiếp —
   hãy đọc lại ứng viên. Lúc ghi sổ, server không so `version` hoá đơn mà tính lại số còn phải thu ngay lúc đó.
4. **Tiền chỉ cho người có quyền tiền.** Ai đọc được đơn thì thấy lượng; giá dòng đơn chỉ với quyền giá/hoá đơn; khoản phải thu của đơn chỉ với
   quyền **hoá đơn**. Không có quyền thì object `commercial` **vắng mặt** khỏi JSON (không phải `null`). Quyền hoá đơn **không** cho đọc phiếu thu, và
   ngược lại.
5. **Trạng thái đơn không đi theo giao hàng hay tiền.** Giao đủ, thu đủ, đơn vẫn có thể `CONFIRMED` — trạng thái đơn do QC phủ. Hiển thị tiến độ
   bằng `deliveryProgress` và `commercial.outstandingAmount`, không bằng `status`.

## §2 — Thay đổi contract

### 2.1 Endpoint mới

| Endpoint | Quyền | Ghi chú |
|---|---|---|
| `GET /api/v1/customer-payments?plantId=…` + `status`, `method`, `customerId`, `invoiceId`, `fromDate`, `toDate`, `search`, `page`, `size` | bất kỳ `PERM_CUSTOMER_PAYMENT_READ/MANAGE/POST` | `plantId` **bắt buộc**; `search` khớp mã phiếu, tên khách, tham chiếu; `invoiceId` = phiếu có phân bổ vào hoá đơn đó; sắp `documentDate` giảm dần |
| `GET /api/v1/customer-payments/{id}` | như trên | chi tiết + phân bổ + `allowedActions` |
| `GET /api/v1/customer-payments/{id}/history` | như trên | `CREATED`/`POSTED`/`CANCELLED`, có người + lúc + lý do |
| `GET /api/v1/customer-payments/invoice-candidates?plantId=…&customerId=…&editingPaymentId=` | như trên | hoá đơn `POSTED` của khách còn phải thu, cũ nhất trước |
| `POST /api/v1/customer-payments` | `PERM_CUSTOMER_PAYMENT_MANAGE` trên nhà máy của **mọi** hoá đơn được phân bổ | **201**; tạo `DRAFT` |
| `PATCH /api/v1/customer-payments/{id}` | `PERM_CUSTOMER_PAYMENT_MANAGE` + **người tạo** | thay toàn bộ ngày, phương thức, tham chiếu, ghi chú, số tiền, phân bổ; khách không đổi |
| `POST /api/v1/customer-payments/{id}/post` | `PERM_CUSTOMER_PAYMENT_POST` | ghi sổ; người có quyền POST ghi được cả phiếu mình tạo. POST **không** bao hàm MANAGE |
| `POST /api/v1/customer-payments/{id}/cancel` | `PERM_CUSTOMER_PAYMENT_MANAGE` + **người tạo** | chỉ `DRAFT`; lý do 5–2000 ký tự |
| `GET /api/v1/sales-invoices/{id}/payments` | quyền đọc hoá đơn **và** quyền đọc phiếu thu | mọi phiếu thu từng phân bổ vào hoá đơn (mọi trạng thái), mỗi dòng chỉ phần đã phân bổ cho hoá đơn này |
| `GET /api/sales-orders/v1/{id}/fulfillment-summary` | quyền đọc đơn bán | tiến độ đơn; tiền theo quyền (§1.4) |

Lệnh ghi phiếu thu (tạo/sửa/ghi sổ/huỷ) **bắt buộc** `Idempotency-Key` và trả `{documentId, type: "PAYMENT", version}` — đọc lại chi tiết sau đó.

**Body tạo:** `customerId`, `documentDate`, `method` (`CASH` / `TRANSFER` / `OTHER`), `reference?` (≤ 200), `note?` (≤ 2000), `amount`,
`allocations[1..100]` mỗi dòng `{invoiceId, expectedInvoiceVersion, amount}`. **Sửa:** `expectedVersion` + các trường trên trừ `customerId`.
**Ghi sổ:** `{expectedVersion}`. **Huỷ:** `{expectedVersion, reason}`.

**Luật:**

- Tiền là **số nguyên đồng > 0**, ≤ 9007199254740991; `amount` của phiếu phải **đúng bằng** tổng phân bổ.
- Mỗi phân bổ một hoá đơn **khác nhau**, `POSTED`, **cùng khách**, cùng nhà máy; `expectedInvoiceVersion` là `version` hoá đơn bạn vừa đọc.
- Mỗi hoá đơn: `số phân bổ ≤ số tiền − đã thu − phần nháp khác đang giữ` (`availableToAllocate` của ứng viên).
- Ngày phiếu ≤ hôm nay (giờ nhà máy) và ≥ ngày hoá đơn muộn nhất được phân bổ. Khách phải còn hoạt động.
- Ghi sổ kiểm lại tất cả dưới khoá; **không** biến động kho, **không** đổi `version` đơn bán, tăng `version` mỗi hoá đơn được trả đúng 1.
- `POSTED` và `CANCELLED` là cuối. Không có tiền ứng / chưa phân bổ, không hoàn tiền.

### 2.2 Thay đổi trên endpoint có sẵn (thêm, không bỏ gì)

| Endpoint | Thêm |
|---|---|
| `GET /api/v1/sales-invoices/{id}`, danh sách hoá đơn | `paidAmount`, `outstandingAmount`, `paymentState` — có giá trị khi hoá đơn `POSTED`, `null` khi `DRAFT`/`CANCELLED` |
| `GET /api/v1/sales-invoices?…&paymentState=` | lọc theo trạng thái thanh toán; chỉ trả hoá đơn `POSTED` |
| `GET /api/v1/deliveries/related?plantId=…` | ngoài `lotId`, nay nhận `productionReceiptId` **hoặc** `workOrderId` — **đúng một** trong ba. Phiếu nhập/lệnh SX được hiểu là các lô nó đưa vào kho: lô đầu ra của phiếu nhập đã duyệt + lô con QC chấp nhận |

### 2.3 Lệch so với đề xuất FE

| # | FE đề xuất | BE làm | Vì sao |
|---|---|---|---|
| 1 | `PAYMENT_EXCEEDED`, `ALLOCATION_MISMATCH` | `PLANNED_QUANTITY_EXCEEDED` field `allocations[i].amount`; `VALIDATION_ERROR` field `amount` (luôn 400) | dùng mã sẵn có — bảng §5 |
| 2 | Tiền là chuỗi số | **số JSON nguyên** ≤ 9007199254740991 | như các phần trước |
| 3 | Ghi sổ phiếu thu gửi `sourceSnapshotVersion` | chỉ `{expectedVersion}`; số còn phải thu tính lại ngay lúc ghi sổ, dưới khoá | `version` hoá đơn đã kiểm lúc lưu; lúc ghi sổ số dư hiện tại mới là thứ quyết định |
| 4 | `relatedPayments[]` nằm trong chi tiết hoá đơn | endpoint riêng `GET …/sales-invoices/{id}/payments` (phân trang) | chi tiết hoá đơn không phải gánh thêm quyền phiếu thu; người chỉ có quyền hoá đơn vẫn mở được chi tiết |
| 5 | `fulfillment-summary` kèm danh sách chứng từ liên quan + `capabilities`; `deliveryProgress`/`invoicedQuantity` trên danh sách/chi tiết đơn | summary chỉ có số tiến độ + tiền; chứng từ liên quan đọc bằng `GET /v1/deliveries?plantId&salesOrderId`, `GET /v1/sales-invoices?plantId&salesOrderId`, `GET /v1/customer-payments?plantId&invoiceId`; danh sách/chi tiết đơn không đổi | các danh sách đó đã phân trang và đã gác quyền riêng; nhân đôi chúng trong summary là thêm một chỗ phải giữ quyền |
| 6 | `related` nhận `receiptId` | tên tham số là `productionReceiptId` | tránh nhầm với phiếu nhận hàng mua |
| 7 | `related` "không giả rỗng khi không truy được nguồn" | phiếu nhập/WO **không đưa lô nào vào kho** (hàng không theo lô, hoặc chưa duyệt) ⇒ **trang rỗng** — không phân biệt được với "không có phiếu giao nào" | chỉ lô nối phiếu giao với nguồn; chưa có cờ "không truy được". Ghi ở §8 |

## §3 — Breaking change

**Không có.** Endpoint và field mới; `related` không có nguồn nào vẫn trả `400 VALIDATION_ERROR` field `lotId` như trước.

## §4 — Ví dụ copy từ lần gọi thật (27/10/2026, dev, công ty thử `GD10D-100050`)

Đơn `SO-000015`: 10 xe giá 100 000. Đã giao 8 + 2, lập `INV-000015` (8 xe, 800 000) và `INV-000016` (2 xe, 200 000), đều đã ghi sổ.

### 4.1 Ứng viên phân bổ

```http
GET /api/v1/customer-payments/invoice-candidates?plantId=ac28ca7c-4516-49e9-ab37-d49d45118e25&customerId=342c471b-8fee-463b-bc01-941e6d5b48a6
200  X-Trace-Id: ac45bec5f4014a0c
```
```json
{"code":"SUCCESS","result":{"content":[{"invoiceId":"be99feff-c92c-41ec-8a05-492ea8ffa09d","code":"INV-000015","documentDate":"2026-10-27","dueDate":"2026-11-26","salesOrderId":"318db35f-f5ff-438b-8c19-e00deb659494","orderNo":"SO-000015","version":1,"currency":"VND","amount":800000,"paidAmount":0,"outstandingAmount":800000,"ownDraftClaim":0,"otherDraftClaim":0,"availableToAllocate":800000},{"invoiceId":"cfa04c8f-3c4a-47a4-a71e-0017860ae4cb","code":"INV-000016","documentDate":"2026-10-27","dueDate":"2026-11-26","salesOrderId":"318db35f-f5ff-438b-8c19-e00deb659494","orderNo":"SO-000015","version":1,"currency":"VND","amount":200000,"paidAmount":0,"outstandingAmount":200000,"ownDraftClaim":0,"otherDraftClaim":0,"availableToAllocate":200000}],"page":0,"size":20,"totalElements":2,"totalPages":1,"first":true,"last":true},"message":"OK"}
```

### 4.2 Tạo nháp, sửa, ghi sổ

```http
POST /api/v1/customer-payments
Idempotency-Key: <uuid>
201  X-Trace-Id: c591da6d9c134781
```
```json
{"customerId": "342c471b-8fee-463b-bc01-941e6d5b48a6", "documentDate": "2026-10-27", "method": "TRANSFER", "reference": "UNC-100050", "amount": 250000, "allocations": [{"invoiceId": "be99feff-c92c-41ec-8a05-492ea8ffa09d", "expectedInvoiceVersion": 1, "amount": 250000}]}
```
```json
{"code":"SUCCESS","result":{"documentId":"693884ab-4c82-430b-b984-c4002179502e","type":"PAYMENT","version":0},"message":"Created successfully"}
```

Sửa thành 300 000 (`PATCH …/693884ab-…`, `X-Trace-Id: 2949aae0ab9441d5`) → `{"documentId":"693884ab-4c82-430b-b984-c4002179502e","type":"PAYMENT","version":1}`.

```http
POST /api/v1/customer-payments/693884ab-4c82-430b-b984-c4002179502e/post
Idempotency-Key: <uuid>
200  X-Trace-Id: cebc564369304576
```
```json
{"expectedVersion": 1}
```
```json
{"code":"SUCCESS","result":{"documentId":"693884ab-4c82-430b-b984-c4002179502e","type":"PAYMENT","version":2},"message":"OK"}
```

Hoá đơn sau đó (`GET /api/v1/sales-invoices/be99feff-…`, `X-Trace-Id: 00caca9d378a4c51`, rút gọn):

```json
{"invoiceId":"be99feff-c92c-41ec-8a05-492ea8ffa09d","code":"INV-000015","type":"INVOICE","status":"POSTED","version":2,"currency":"VND","amount":800000,"paidAmount":300000,"outstandingAmount":500000,"paymentState":"PARTIALLY_PAID","allowedActions":[]}
```

`version` hoá đơn 1 → 2. Gửi lại cùng `Idempotency-Key` cho ghi sổ trả lại đúng `version: 2`, hoá đơn không tăng lần nữa.

### 4.3 Một phiếu cho hai hoá đơn

```json
{"customerId": "342c471b-8fee-463b-bc01-941e6d5b48a6", "documentDate": "2026-10-27", "method": "TRANSFER", "reference": "UNC-100050", "amount": 700000, "allocations": [{"invoiceId": "be99feff-c92c-41ec-8a05-492ea8ffa09d", "expectedInvoiceVersion": 2, "amount": 500000}, {"invoiceId": "cfa04c8f-3c4a-47a4-a71e-0017860ae4cb", "expectedInvoiceVersion": 1, "amount": 200000}]}
```

`201 X-Trace-Id: ac3d3f2b590848b7` → ghi sổ → cả hai hoá đơn `PAID`, mỗi hoá đơn `version` +1; ứng viên trả rỗng.

### 4.4 Phiếu thu của một hoá đơn

```http
GET /api/v1/sales-invoices/be99feff-c92c-41ec-8a05-492ea8ffa09d/payments
200  X-Trace-Id: 9dcf61527b1d4ef6
```
```json
{"code":"SUCCESS","result":{"content":[{"paymentId":"9edfa760-b9f7-4c34-bcc1-13eaf07f0947","code":"PAY-000003","status":"POSTED","documentDate":"2026-10-27","method":"TRANSFER","allocatedAmount":500000,"postedAt":"2026-10-27T03:00:54.403215Z"},{"paymentId":"79981c75-9083-4168-93e8-a7ce31ee7b01","code":"PAY-000002","status":"CANCELLED","documentDate":"2026-10-27","method":"TRANSFER","allocatedAmount":400000,"postedAt":null},{"paymentId":"693884ab-4c82-430b-b984-c4002179502e","code":"PAY-000001","status":"POSTED","documentDate":"2026-10-27","method":"TRANSFER","allocatedAmount":300000,"postedAt":"2026-10-27T03:00:53.847140Z"}],"page":0,"size":20,"totalElements":3,"totalPages":1,"first":true,"last":true},"message":"OK"}
```

Phiếu `CANCELLED` vẫn hiện để có lịch sử; chỉ phiếu `POSTED` được tính vào `paidAmount`.

### 4.5 Tiến độ đơn — người có quyền hoá đơn và người chỉ đọc đơn

```http
GET /api/sales-orders/v1/318db35f-f5ff-438b-8c19-e00deb659494/fulfillment-summary
200  X-Trace-Id: 152093ff03994baf   (admin)
```
```json
{"code":"SUCCESS","result":{"salesOrderId":"318db35f-f5ff-438b-8c19-e00deb659494","orderNo":"SO-000015","status":"CONFIRMED","version":3,"deliveryBaselineKnown":true,"deliveryBaselineUnknownReason":null,"deliveryProgress":"DELIVERED","lines":[{"salesOrderLineId":"5435777c-c673-4e4f-8b29-de236500f3b2","lineNo":1,"itemId":"b4fbff56-56b2-4634-8138-7d4a6e6a1e67","itemSku":"BIKE-100050","itemName":"Bike","uom":"EA","orderedQuantity":10.000000,"qcAcceptedQuantity":0.000000,"deliveredQuantity":10.000000,"remainingToDeliverQuantity":0.000000,"draftDeliveryClaim":0,"invoicedQuantity":10.000000,"commercial":{"unitPrice":100000.000000,"invoicedAmount":1000000}}],"commercial":{"currency":"VND","invoicedAmount":1000000,"paidAmount":1000000,"outstandingAmount":0}},"message":"OK"}
```

Cùng request với người chỉ có `PERM_SALES_ORDER_READ` (`X-Trace-Id: 10e3b15d2acd4992`) — **không** có khoá `commercial` nào:

```json
{"code":"SUCCESS","result":{"salesOrderId":"318db35f-f5ff-438b-8c19-e00deb659494","orderNo":"SO-000015","status":"CONFIRMED","version":3,"deliveryBaselineKnown":true,"deliveryBaselineUnknownReason":null,"deliveryProgress":"DELIVERED","lines":[{"salesOrderLineId":"5435777c-c673-4e4f-8b29-de236500f3b2","lineNo":1,"itemId":"b4fbff56-56b2-4634-8138-7d4a6e6a1e67","itemSku":"BIKE-100050","itemName":"Bike","uom":"EA","orderedQuantity":10.000000,"qcAcceptedQuantity":0.000000,"deliveredQuantity":10.000000,"remainingToDeliverQuantity":0.000000,"draftDeliveryClaim":0,"invoicedQuantity":10.000000}]},"message":"OK"}
```

`deliveryProgress`: `NOT_DELIVERED` / `PARTIALLY_DELIVERED` / `DELIVERED`, `null` khi đơn xác nhận trước khi có giao hàng (`deliveryBaselineKnown = false`,
các số giao/lập cũng `null`). Ở đây `qcAcceptedQuantity` = 0 vì hàng giao từ tồn sẵn có, không qua sản xuất — đúng là hai sự thật khác nhau.

### 4.6 Lỗi

Tổng phiếu khác tổng phân bổ (`X-Trace-Id: 3a570a2b12fb4ffa`):
```json
{"code":"VALIDATION_ERROR","message":"amount: must equal the sum of the allocations (300000)","errors":[{"field":"amount","message":"must equal the sum of the allocations (300000)"}]}
```
Vượt phần còn phân bổ được (`X-Trace-Id: 642d546336a24b1b`):
```json
{"code":"PLANNED_QUANTITY_EXCEEDED","message":"allocations[0].amount: invoice INV-000015 can still be allocated 800000 (outstanding minus other draft payments)","errors":[{"field":"allocations[0].amount","message":"invoice INV-000015 can still be allocated 800000 (outstanding minus other draft payments)"}]}
```
`version` hoá đơn cũ (`X-Trace-Id: fe74d040744d41ae`):
```json
{"code":"CONCURRENT_MODIFICATION","message":"allocations[0].expectedInvoiceVersion: invoice INV-000015 is at version 1; reload it","errors":[{"field":"allocations[0].expectedInvoiceVersion","message":"invoice INV-000015 is at version 1; reload it"}]}
```

## §5 — Envelope và mã lỗi

| Tình huống | HTTP | `code` | `errors[].field` |
|---|---|---|---|
| Tiền ≤ 0 / có số lẻ / quá trần, phương thức lạ, > 100 phân bổ, lý do huỷ < 5 ký tự | 400 | `VALIDATION_ERROR` | `amount`, `allocations[i].amount`, `method`, `reason` |
| Tổng phiếu ≠ tổng phân bổ | 400 | `VALIDATION_ERROR` | `amount` |
| Hoá đơn lạ / của khách khác / nhà máy khác / trùng | 400 | `VALIDATION_ERROR` | `allocations[i].invoiceId` |
| Ngày sau hôm nay / trước ngày hoá đơn | 400 | `VALIDATION_ERROR` | `documentDate` |
| `related` không có hoặc có hơn một nguồn | 400 | `VALIDATION_ERROR` | `lotId` |
| Thiếu `Idempotency-Key` | 400 | `MISSING_REQUIRED_FIELD` | — |
| Không có quyền / không phải người tạo / id không tồn tại | 403 | `PERMISSION_DENIED` | — |
| Hoá đơn chưa ghi sổ; phiếu thu không phải `DRAFT` | 409 | `STATE_CONFLICT` | `allocations[i].invoiceId` khi do hoá đơn |
| Vượt phần còn phân bổ được (lúc lưu hoặc lúc ghi sổ) | 409 | `PLANNED_QUANTITY_EXCEEDED` | `allocations[i].amount` |
| `version` hoá đơn lệch | 409 | `CONCURRENT_MODIFICATION` | `allocations[i].expectedInvoiceVersion` |
| `expectedVersion` của phiếu lệch | 409 | `CONCURRENT_MODIFICATION` | — |
| Cùng key, khác body | 409 | `IDEMPOTENCY_CONFLICT` | — |
| `X-Plant-Id` lệch `plantId` | 409 | `STATE_CONFLICT` | — |
| Khách ngừng hoạt động | 422 | `RESOURCE_INACTIVE` | — |

**Khi nhận `CONCURRENT_MODIFICATION` ở `expectedInvoiceVersion`** (thường do người khác vừa ghi sổ một phiếu thu cho hoá đơn đó): đọc lại ứng viên,
cho người dùng xem số còn phân bổ được, rồi lưu lại với `version` mới.

## §6 — Quyền và giới hạn

- Quyền mới: không (đã seed ở phần 1). `PERM_CUSTOMER_PAYMENT_*` READ/MANAGE/POST — đọc cần một trong ba; POST không bao hàm MANAGE.
- Quyền hoá đơn **không** cho đọc phiếu thu; quyền phiếu thu **không** cho đọc hoá đơn. `GET …/sales-invoices/{id}/payments` cần cả hai.
- Ứng viên phân bổ không trả giá dòng đơn (Q30).
- Không có tiền ứng / chưa phân bổ, hoàn tiền, đảo phiếu thu đã ghi sổ, báo cáo tuổi nợ — ngoài phạm vi GĐ10.

## §7 — Bằng chứng

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — 2085 unit + 312 IT, 0 failures, 0 errors |
| HTTP thật | dev, Postgres + Redis Docker, 27/10/2026 ~10:00 (+07:00), công ty thử `GD10D-100050`: **44/44** kiểm tra đạt (tiến độ trước giao; hoá đơn `UNPAID`; ứng viên; tổng lệch; vượt phần còn; `version` hoá đơn cũ; ngày mai; nửa đồng; phương thức lạ; tạo + replay + cùng key khác body; chi tiết; ứng viên khi sửa; sửa `version` +1; ghi sổ ⇒ `PARTIALLY_PAID` + hoá đơn `version` +1; replay ghi sổ; ghi sổ lại / huỷ phiếu đã ghi sổ 409; `version` hoá đơn cũ sau ghi sổ 409; nháp thứ hai vượt phần còn; huỷ lý do ngắn 400; huỷ trả phần giữ; một phiếu hai hoá đơn ⇒ cả hai `PAID`; hết ứng viên; phiếu thu của hoá đơn; lọc `paymentState` ×3; lọc phiếu theo hoá đơn; tìm theo mã; lịch sử; `X-Plant-Id` lệch; tiến độ sau thu; `related` không/hai nguồn 400; WO không sinh lô ⇒ rỗng; người chỉ đọc đơn không thấy `commercial`; quyền hoá đơn không đọc được phiếu thu; quyền hoá đơn thấy khoản phải thu; quyền phiếu thu không đọc được phiếu thu của hoá đơn; quyền phiếu thu đọc phiếu; MANAGE không ghi sổ được) |
| Tranh chấp | Postgres thật: hai nháp 700 000 cùng một hoá đơn 1 000 000 — luôn một thắng, một nhận `PLANNED_QUANTITY_EXCEEDED` |

**Chưa nghiệm thu:** chưa chạy trên môi trường FE; `related` theo phiếu nhập/WO có lô thật chỉ đo bằng test tự động (smoke chỉ đo WO không sinh lô).

## §8 — Chưa làm và việc FE cần làm

| Mục | Vì sao | Khi nào |
|---|---|---|
| Seed demo `scripts/demo-capstone2` gửi `customerId` khi tạo đơn | đơn tạo không có khách master không xác nhận được (phần 1) | ngay sau bản này |
| `docs/api-guide-for-frontend.md` cập nhật GĐ10 | gom cả 4 phần | ngay sau bản này |
| Cờ "không truy được nguồn" cho `related` theo phiếu nhập/WO không có lô | §2.3 #7 | khi FE cần — cho biết |
| Tiền ứng, hoàn tiền, đảo phiếu thu, tuổi nợ, giá vốn | ngoài phạm vi GĐ10 | — |

**Bắt buộc:** gửi `expectedInvoiceVersion` lấy từ **ứng viên vừa đọc** · gửi `editingPaymentId` khi đọc ứng viên lúc sửa nháp · `amount` của phiếu =
tổng phân bổ · gửi `Idempotency-Key` cho tạo/sửa/ghi sổ/huỷ · dùng `version` trả về cho lệnh tiếp · coi `commercial` vắng mặt là "không có quyền".
**Nên làm:** hiển thị `availableToAllocate` thay vì tự trừ; hiển thị tiến độ đơn bằng `deliveryProgress` + `outstandingAmount`, không bằng `status`.

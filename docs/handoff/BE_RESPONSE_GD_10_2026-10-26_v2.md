# Giai đoạn 10 — BE trả lời FE (phần 3/4): hoá đơn bán hàng

**Bản:** v2 · **Ngày:** 26/10/2026 · **Trả lời:** `FE_HANDOFF_GD_10_2026-10-15.md` (v1). **Bản trước:** `BE_RESPONSE_GD_10_2026-10-25.md`
(phần 1–2: giá trên đơn, khách bắt buộc, phiếu giao hàng) — vẫn đúng, bản này chỉ thêm. Thanh toán và màn hình tổng hợp (phần 4) gửi ở `_v3`.

## §0 — Kết luận

BE đã có **hoá đơn bán hàng nội bộ** cho một đơn bán, gộp nhiều phiếu giao đã ghi sổ: ứng viên, xem trước (không giữ gì), nháp giữ
hạn mức theo dòng phiếu giao, ghi sổ thành khoản phải thu, huỷ nháp, lịch sử. Tiền tính bằng **đồng nguyên, làm tròn HALF_UP cộng dồn
theo dòng đơn** — ba hoá đơn 1/3 ở giá 1 đồng ra 0, 1, 0. Nền tính tiền đổi giữa lúc xem trước và lúc lưu/ghi sổ ⇒ `409 PRICE_STALE`,
server **không bao giờ** tự đổi số tiền trong lần xác nhận cũ. Hoá đơn **không** đụng tồn kho. Đã gọi thật trên môi trường dev.

| Nhóm | Kết quả |
|---|---|
| R11, R12, Q15 | **Làm** — một hoá đơn một đơn, nhiều phiếu giao `POSTED`; nháp giữ phần chưa lập theo dòng phiếu giao, sửa loại phần của chính nó, huỷ trả lại |
| R13, Q8 | **Làm** — giá lấy từ dòng đơn (đã khoá); thiếu giá ⇒ `PRICE_UNKNOWN`, không bao giờ tính 0; giá 0 ⇒ hoá đơn 0 đồng hợp lệ |
| R14, Q7, Q22 | **Làm** — HALF_UP cộng dồn theo dòng đơn; `billingSnapshotVersion` + `PRICE_STALE` |
| R15 | **Làm** — ghi sổ nguyên tử, không biến động kho, không đổi version đơn |
| R27 | **Làm** — ngày hoá đơn ≤ hôm nay (giờ nhà máy), ≥ ngày phiếu giao nguồn muộn nhất; hạn ≥ ngày |
| `paidAmount` / `outstandingAmount` / `paymentState`, `GET …/{id}/payments`, lọc `paymentState` | Chưa — phần 4 (`_v3`) |
| Lệch contract | **6 chỗ**, §2.3 |

## §1 — Vì sao các quyết định này

1. **Làm tròn trên lượng cộng dồn, không trên từng hoá đơn.** `số tiền dòng = round((đã lập trước + lượng này) × giá) − tiền đã lập
   trước`. Nhờ vậy lập nhiều hoá đơn từng phần luôn cộng lại đúng bằng một hoá đơn cho tổng — không lệch 1 đồng nào. "Đã lập trước" là
   các hoá đơn **đã ghi sổ** của cùng dòng đơn; nháp không tính (nháp chỉ là xem trước).
2. **Nhiều dòng cùng dòng đơn trong một hoá đơn được tính theo thứ tự bạn gửi** — dòng sau đứng trên nền đã dời của dòng trước. Ví dụ
   3 + 1 + 4 ở 100 000 ra 300 000 / 100 000 / 400 000 (§4.2).
3. **`billingSnapshotVersion` là mã mờ**, đại diện cho phiên bản giá của đơn và nền đã lập của mọi dòng đơn hoá đơn chạm tới. Lưu
   nháp phải gửi đúng mã vừa xem trước cho **chính những dòng đó**; ghi sổ so mã đã lưu với nền hiện tại. 🔴 Nền dời mà số tiền tình cờ
   không đổi (lượng nguyên × giá nguyên) **vẫn** là `PRICE_STALE` — nếu không, các dòng hoá đơn sẽ lưu "đã lập trước" sai.
4. **Hoá đơn không đổi version đơn bán.** Nó không sửa trường nào của đơn. Thứ chặn hai người cùng lập trên một lượng giao là khoá của
   đơn bán phía server — hai lệnh xếp hàng, người sau thấy phần người trước đã giữ.

## §2 — Thay đổi contract

### 2.1 Endpoint mới

| Endpoint | Quyền | Ghi chú |
|---|---|---|
| `GET /api/v1/sales-invoices?plantId=…` + `status`, `salesOrderId`, `customerId`, `fromDate`, `toDate`, `search`, `page`, `size` | bất kỳ `PERM_SALES_INVOICE_READ/MANAGE/POST` | `plantId` **bắt buộc**; `search` khớp mã hoá đơn, tên khách, số đơn; sắp `documentDate` giảm dần |
| `GET /api/v1/sales-invoices/{id}` | như trên | chi tiết + dòng + nguồn + `allowedActions` |
| `GET /api/v1/sales-invoices/{id}/history` | như trên | `CREATED`/`POSTED`/`CANCELLED`, có người + lúc + lý do |
| `GET /api/sales-orders/v1/{id}/invoice-candidates?editingInvoiceId=` | như trên | dòng phiếu giao `POSTED` của đơn + đã lập + nháp này + nháp khác + còn lập + giá |
| `POST /api/v1/sales-invoices/preview` | `PERM_SALES_INVOICE_MANAGE` | **200**; không lưu, không giữ hạn mức |
| `POST /api/v1/sales-invoices` | `PERM_SALES_INVOICE_MANAGE` | **201**; tạo `DRAFT` (giữ hạn mức, chưa phải khoản phải thu) |
| `PATCH /api/v1/sales-invoices/{id}` | `PERM_SALES_INVOICE_MANAGE` + **người tạo** | thay toàn bộ ngày, hạn, ghi chú, dòng |
| `POST /api/v1/sales-invoices/{id}/post` | `PERM_SALES_INVOICE_POST` | ghi sổ — người có quyền POST ghi được cả hoá đơn mình tạo |
| `POST /api/v1/sales-invoices/{id}/cancel` | `PERM_SALES_INVOICE_MANAGE` + **người tạo** | chỉ `DRAFT`; lý do 5–2000 ký tự |

Lệnh ghi (tạo/sửa/ghi sổ/huỷ) **bắt buộc** `Idempotency-Key` và trả `{documentId, type: "INVOICE", version}` — đọc lại chi tiết sau
đó. Xem trước không cần key.

**Body xem trước:** `salesOrderId`, `expectedOrderVersion`, `editingInvoiceId?` (đang sửa nháp nào), `lines[1..100]` mỗi dòng
`{deliveryLineId, quantity}` (số dương, ≤ 6 số lẻ, ≤ 999999999). **Tạo:** `salesOrderId`, `expectedOrderVersion`,
`billingSnapshotVersion` (từ xem trước), `documentDate`, `dueDate` (bắt buộc), `note?` (≤ 2000), `lines`. **Sửa:** `expectedVersion`,
`billingSnapshotVersion`, `documentDate`, `dueDate`, `note?`, `lines`. **Ghi sổ:** `{expectedVersion}`. **Huỷ:** `{expectedVersion, reason}`.

**Luật:**

- Mỗi dòng là một dòng **khác nhau** của một phiếu giao **đã ghi sổ** của **cùng đơn**. Giá, vật tư, khách lấy từ đơn — client không
  gửi.
- Hạn mức mỗi dòng phiếu giao: `đã giao − đã lập (hoá đơn POSTED) − nháp khác`; hoá đơn đang sửa không tính trùng phần của chính nó.
- Đơn phải `CONFIRMED`/`IN_PRODUCTION`/`PARTIALLY_FULFILLED`/`FULFILLED`, khách master còn hoạt động.
- Mọi thứ được kiểm lại khi ghi sổ, dưới khoá. Ghi sổ **không** sinh biến động kho và **không** đổi version đơn bán.
- `POSTED` và `CANCELLED` là cuối — không sửa, không huỷ, không ghi sổ lại. Tổng tiền ≤ 9007199254740991.

### 2.2 Hình dạng

`amount` (header, dòng, `billedAmountBefore`) là **số JSON nguyên**, đơn vị đồng, `currency` luôn `"VND"`. `unitPrice` là giá dòng đơn
(≤ 6 số lẻ). Mọi trường tiền **luôn có** trên hoá đơn — chỉ người có quyền hoá đơn mới đọc được hoá đơn. Lượng (`quantity`…) giữ quy
ước hiện có: lệnh vừa ghi lặp lại cách viết của request (`3`), đọc lại từ DB ra 6 số lẻ (`3.000000`).

### 2.3 Lệch so với đề xuất FE

| # | FE đề xuất | BE làm | Vì sao |
|---|---|---|---|
| 1 | Danh sách lấy nhà máy từ `X-Plant-Id` | `plantId` trong query **bắt buộc**; `X-Plant-Id` chỉ để đối chiếu (lệch ⇒ 409) | quy ước chung, như phiếu giao |
| 2 | Ghi sổ gửi `sourceSnapshotVersion` | chỉ `{expectedVersion}`; server so nền hiện tại với `billingSnapshotVersion` **đã lưu** trên hoá đơn. Gửi thêm trường đó thì bị bỏ qua | nền đã nằm trên hoá đơn; một token thứ hai không chặn thêm được gì |
| 3 | Tiền là chuỗi số | **số JSON nguyên** ≤ 9007199254740991 | một kiểu số cho cả hệ thống (như bản trước §2.3 #3) |
| 4 | `INVOICE_EXCEEDED`, `MONEY_INVALID`, `DATE_INVALID`, `SOURCE_CHANGED` | `PLANNED_QUANTITY_EXCEEDED`, `VALIDATION_ERROR` (field), `VALIDATION_ERROR` (field), `STATE_CONFLICT` / `VALIDATION_ERROR` | dùng mã sẵn có — bảng §5 |
| 5 | Ghi sổ hoá đơn tăng version đơn bán | **không** | hoá đơn không đổi trường nào của đơn; `expectedOrderVersion` cũ vẫn dùng được sau khi người khác lập hoá đơn |
| 6 | Nền đổi nhưng tiền không đổi thì cho qua? (không nêu) | vẫn `PRICE_STALE` | xem §1.3 |

## §3 — Breaking change

**Không có.** Toàn bộ là endpoint mới; không đổi field hay hành vi nào đã có.

## §4 — Ví dụ copy từ lần gọi thật (26/10/2026, dev, công ty thử `GD10C-213414`)

Đơn `SO-000014`: dòng 1 đặt 11 giá 100 000, dòng 2 đặt 1 giá 1, dòng 3 đặt 2 **chưa có giá**. Đã giao: `DN-000011` 3, `DN-000012` 1,
`DN-000013` 4 (dòng 1) + 1 (dòng 2) + 2 (dòng 3).

### 4.1 Ứng viên (rút gọn 2/5 dòng)

```http
GET /api/sales-orders/v1/09efa9a7-632f-4c1b-9140-e8cfc8d232f1/invoice-candidates
200  X-Trace-Id: 437774c3404041c8
```
```json
{"code":"SUCCESS","result":{"salesOrderId":"09efa9a7-632f-4c1b-9140-e8cfc8d232f1","orderNo":"SO-000014","status":"CONFIRMED","version":4,"customerId":"5ec9609a-df51-4e7d-a22a-a59cb83c3e6f","customerName":"Khach GD10C","currency":"VND","pricingVersion":"PRICE-0","invoiceable":true,"blockedReason":null,"lines":{"content":[{"deliveryLineId":"dcb63933-4b85-42fc-8078-51aeb8cf59a1","deliveryId":"eb68a62d-c0f6-42de-b7d8-1b02840f7655","deliveryCode":"DN-000011","deliveryDate":"2026-10-26","salesOrderLineId":"915f9f32-550b-450b-b61e-37da960a4a88","salesOrderLineNo":1,"itemId":"ca5a2871-1fbb-4caa-93e7-914d88b07f6f","itemSku":"BIKE-213414","itemName":"Bike","uom":"EA","deliveredQuantity":3.000000,"postedInvoicedQuantity":0,"ownDraftClaim":0,"otherDraftClaim":0,"unclaimedQuantity":3.000000,"unitPrice":100000.000000,"priceKnown":true},{"deliveryLineId":"c95bdb6f-ecd4-445e-b472-9d3d2ae7798b","deliveryId":"f4d57af8-b271-493e-bf3f-8ca4af7653b4","deliveryCode":"DN-000012","deliveryDate":"2026-10-26","salesOrderLineId":"915f9f32-550b-450b-b61e-37da960a4a88","salesOrderLineNo":1,"itemId":"ca5a2871-1fbb-4caa-93e7-914d88b07f6f","itemSku":"BIKE-213414","itemName":"Bike","uom":"EA","deliveredQuantity":1.000000,"postedInvoicedQuantity":0,"ownDraftClaim":0,"otherDraftClaim":0,"unclaimedQuantity":1.000000,"unitPrice":100000.000000,"priceKnown":true}, "…"]}}}
```

### 4.2 Xem trước 3 + 1 + 4

```http
POST /api/v1/sales-invoices/preview
200  X-Trace-Id: 9503761a888f4dc6
```
```json
{"salesOrderId": "09efa9a7-632f-4c1b-9140-e8cfc8d232f1", "expectedOrderVersion": 4, "lines": [{"deliveryLineId": "dcb63933-4b85-42fc-8078-51aeb8cf59a1", "quantity": 3}, {"deliveryLineId": "c95bdb6f-ecd4-445e-b472-9d3d2ae7798b", "quantity": 1}, {"deliveryLineId": "5dd49f7a-18d0-4f8b-96d9-6a36d4310ca1", "quantity": 4}]}
```
```json
{"code":"SUCCESS","result":{"salesOrderId":"09efa9a7-632f-4c1b-9140-e8cfc8d232f1","currency":"VND","pricingVersion":"PRICE-0","billingSnapshotVersion":"BILL-5f71b1831040c6e4","amount":800000,"lines":[{"deliveryLineId":"dcb63933-4b85-42fc-8078-51aeb8cf59a1","salesOrderLineId":"915f9f32-550b-450b-b61e-37da960a4a88","quantity":3,"unitPrice":100000.000000,"amount":300000,"billedQuantityBefore":0,"billedAmountBefore":0},{"deliveryLineId":"c95bdb6f-ecd4-445e-b472-9d3d2ae7798b","salesOrderLineId":"915f9f32-550b-450b-b61e-37da960a4a88","quantity":1,"unitPrice":100000.000000,"amount":100000,"billedQuantityBefore":3,"billedAmountBefore":300000},{"deliveryLineId":"5dd49f7a-18d0-4f8b-96d9-6a36d4310ca1","salesOrderLineId":"915f9f32-550b-450b-b61e-37da960a4a88","quantity":4,"unitPrice":100000.000000,"amount":400000,"billedQuantityBefore":4,"billedAmountBefore":400000}]},"message":"OK"}
```

### 4.3 Lưu nháp, đọc lại, ghi sổ

```http
POST /api/v1/sales-invoices
Idempotency-Key: <uuid>
201  X-Trace-Id: 5dd2155db1294bbd
```
```json
{"salesOrderId": "09efa9a7-632f-4c1b-9140-e8cfc8d232f1", "expectedOrderVersion": 4, "billingSnapshotVersion": "BILL-5f71b1831040c6e4", "documentDate": "2026-10-26", "dueDate": "2026-11-10", "note": "Smoke", "lines": [{"deliveryLineId": "dcb63933-4b85-42fc-8078-51aeb8cf59a1", "quantity": 3}, {"deliveryLineId": "c95bdb6f-ecd4-445e-b472-9d3d2ae7798b", "quantity": 1}, {"deliveryLineId": "5dd49f7a-18d0-4f8b-96d9-6a36d4310ca1", "quantity": 4}]}
```
```json
{"code":"SUCCESS","result":{"documentId":"92420da6-d3a1-47ce-8dc7-2268dc1662e1","type":"INVOICE","version":0},"message":"Created successfully"}
```

`GET /api/v1/sales-invoices/92420da6-d3a1-47ce-8dc7-2268dc1662e1` → 200 `X-Trace-Id: b0a6b3837e4f436c` (rút gọn còn dòng đầu):

```json
{"code":"SUCCESS","result":{"invoiceId":"92420da6-d3a1-47ce-8dc7-2268dc1662e1","code":"INV-000007","type":"INVOICE","status":"DRAFT","version":0,"companyId":"dbd8812b-9731-4ba6-99b0-7c85e2b8a2b5","plantId":"13ebfac3-2839-4dfb-b7e6-e7eaa53f74eb","salesOrderId":"09efa9a7-632f-4c1b-9140-e8cfc8d232f1","orderNo":"SO-000014","customerId":"5ec9609a-df51-4e7d-a22a-a59cb83c3e6f","customerName":"Khach GD10C","documentDate":"2026-10-26","dueDate":"2026-11-10","note":"Smoke","currency":"VND","amount":800000,"billingSnapshotVersion":"BILL-5f71b1831040c6e4","lines":[{"invoiceLineId":"49d3ce1c-362b-4ff5-867e-3903de270fff","lineNo":1,"deliveryLineId":"dcb63933-4b85-42fc-8078-51aeb8cf59a1","deliveryId":"eb68a62d-c0f6-42de-b7d8-1b02840f7655","deliveryCode":"DN-000011","salesOrderLineId":"915f9f32-550b-450b-b61e-37da960a4a88","salesOrderLineNo":1,"itemId":"ca5a2871-1fbb-4caa-93e7-914d88b07f6f","itemSku":"BIKE-213414","itemName":"Bike","uom":"EA","quantity":3.000000,"unitPrice":100000.000000,"amount":300000,"billedQuantityBefore":0.000000,"billedAmountBefore":0}, "…"],"createdBy":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","createdByUsername":"admin","createdAt":"2026-10-26T14:34:15.521560Z","postedBy":null,"postedByUsername":null,"postedAt":null,"cancelledBy":null,"cancelledByUsername":null,"cancelledAt":null,"cancelReason":null,"allowedActions":["EDIT","CANCEL","POST"]},"message":"OK"}
```

Sau một lần sửa (version 1), ghi sổ:

```http
POST /api/v1/sales-invoices/92420da6-d3a1-47ce-8dc7-2268dc1662e1/post
Idempotency-Key: <uuid>
200  X-Trace-Id: ba8257915b7d4ffe
```
```json
{"expectedVersion": 1}
```
```json
{"code":"SUCCESS","result":{"documentId":"92420da6-d3a1-47ce-8dc7-2268dc1662e1","type":"INVOICE","version":2},"message":"OK"}
```

Tồn kho thành phẩm trước/sau ghi sổ: 19 / 19.

### 4.4 Ba hoá đơn 1/3 ở giá 1 đồng (A21)

Mỗi lần: xem trước → lưu → ghi sổ, lượng `0.333333`, `0.333333`, `0.333334` trên dòng 2. Số tiền ba hoá đơn: **0, 1, 0**.

### 4.5 Lỗi

Mã xem trước cũ (`X-Trace-Id: 18b8cefaf37b4ee3`):
```json
{"code":"PRICE_STALE","message":"billingSnapshotVersion: the billing base is now BILL-525cc1a87ff62eb5; preview again and confirm the new amounts","errors":[{"field":"billingSnapshotVersion","message":"the billing base is now BILL-525cc1a87ff62eb5; preview again and confirm the new amounts"}]}
```
Ghi sổ nháp B sau khi nháp A cùng nền đã ghi sổ (A22, `X-Trace-Id: 0fb5518470264031`):
```json
{"code":"PRICE_STALE","message":"The billing base changed since this invoice was saved; preview it, save it again and confirm"}
```
Dòng đơn chưa có giá (`X-Trace-Id: 8d0e2f5d21f749df`):
```json
{"code":"PRICE_UNKNOWN","message":"lines[0].deliveryLineId: sales order line 3 has no price","errors":[{"field":"lines[0].deliveryLineId","message":"sales order line 3 has no price"}]}
```

## §5 — Envelope và mã lỗi

| Tình huống | HTTP | `code` | `errors[].field` |
|---|---|---|---|
| Lượng ≤ 0 / > 6 số lẻ / > 999999999, thiếu `dueDate`, > 100 dòng, lý do huỷ < 5 ký tự | 400 | `VALIDATION_ERROR` | đúng path, vd `lines[0].quantity`, `reason` |
| Dòng phiếu giao lạ / của đơn khác / gửi trùng | 400 | `VALIDATION_ERROR` | `lines[i].deliveryLineId` |
| Ngày sau hôm nay / trước ngày phiếu giao nguồn / hạn trước ngày | 400 | `VALIDATION_ERROR` | `documentDate` / `dueDate` |
| Thiếu `Idempotency-Key` | 400 | `MISSING_REQUIRED_FIELD` | — |
| Không có quyền / không phải người tạo / id không tồn tại | 403 | `PERMISSION_DENIED` | — |
| Phiếu giao nguồn chưa ghi sổ; hoá đơn không phải `DRAFT`; đơn sai trạng thái | 409 | `STATE_CONFLICT` | `lines[i].deliveryLineId` khi do nguồn |
| Vượt phần còn lập | 409 | `PLANNED_QUANTITY_EXCEEDED` | `lines[i].quantity` |
| Dòng đơn chưa có giá | 409 | `PRICE_UNKNOWN` | `lines[i].deliveryLineId` |
| Nền đã đổi (lưu: mã xem trước cũ; ghi sổ: nền đổi từ lúc lưu, hoặc số tiền đã lưu không khớp) | 409 | `PRICE_STALE` | `billingSnapshotVersion` khi lưu; không field khi ghi sổ |
| `expectedVersion` / `expectedOrderVersion` lệch | 409 | `CONCURRENT_MODIFICATION` | — |
| Cùng key, khác body | 409 | `IDEMPOTENCY_CONFLICT` | — |
| Khách đơn ngừng hoạt động | 422 | `RESOURCE_INACTIVE` | — |
| Đơn không có khách master | 409 | `SOURCE_UNKNOWN` | — |

**Khi nhận `PRICE_STALE`:** giữ dòng người dùng đã nhập, gọi lại xem trước (kèm `editingInvoiceId` nếu đang sửa), cho người dùng xem
số tiền mới, rồi lưu với `billingSnapshotVersion` mới. Hoá đơn đang ở `DRAFT` phải **lưu lại** (PATCH) trước khi ghi sổ.

## §6 — Quyền và giới hạn

- Quyền mới: không (10 quyền đã seed ở phần 1). `PERM_SALES_INVOICE_*` READ/MANAGE/POST — đọc cần một trong ba; POST không bao hàm MANAGE.
- Người có quyền hoá đơn **thấy giá đơn bán** (`commercial` trên chi tiết đơn). Người chỉ có quyền giao hàng **không** đọc được hoá đơn
  hay ứng viên hoá đơn (403).
- Hoá đơn 0 đồng (giá 0) được lập và ghi sổ; trạng thái thanh toán `NO_AMOUNT_DUE` có ở phần 4.

## §7 — Bằng chứng

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — 2068 unit + 303 IT, 0 failures, 0 errors |
| HTTP thật | dev, Postgres + Redis Docker, 26/10/2026 ~21:34 (+07:00), công ty thử `GD10C-213414`: **31/31** kiểm tra đạt (ứng viên, xem trước, lưu, replay, vượt hạn mức, mã cũ, thiếu giá, ngày, số lẻ thứ 7, sửa version +1, ghi sổ không đổi tồn, replay/ghi sổ lại, A21 0/1/0, A22, số tiền bị sửa tay trong DB, huỷ trả hạn mức, `X-Plant-Id` lệch, danh sách/tìm, lịch sử, quyền giao hàng 403, quyền hoá đơn thấy giá, không phải người tạo 403, không có POST 403) |
| Tranh chấp | Postgres thật: hai nháp cùng lượng giao 5, mỗi nháp 3 — luôn một thắng, một nhận `PLANNED_QUANTITY_EXCEEDED` |

**Chưa nghiệm thu:** chưa chạy trên môi trường FE; hoá đơn nhiều dòng đơn khác giá trong cùng một lần chỉ đo bằng test tự động.

## §8 — Chưa làm và việc FE cần làm

| Mục FE xin | Vì sao chưa | Khi nào |
|---|---|---|
| `paidAmount` / `outstandingAmount` / `paymentState`, lọc `paymentState`, `GET …/{id}/payments` | phần 4 | `_v3` |
| Thanh toán (R16–R18), tổng tiền trên đơn, liên kết ngược (R24) | phần 4 | `_v3` |
| `sourceSnapshotVersion` khi ghi sổ, `REQUEST_IN_PROGRESS`, TTL idempotency | §2.3 #2; như bản trước | không làm |

**Bắt buộc:** luôn lấy `billingSnapshotVersion` từ **lần xem trước cuối cùng** cho đúng các dòng sắp lưu · gửi `editingInvoiceId` khi xem
trước lúc sửa nháp · xử lý `PRICE_STALE` như §5 · gửi `Idempotency-Key` cho tạo/sửa/ghi sổ/huỷ · dùng `version` trả về cho lệnh tiếp.
**Nên làm:** hiển thị `unclaimedQuantity` từ ứng viên thay vì tự trừ; giữ thứ tự dòng khi gửi lại (thứ tự quyết định cách chia đồng lẻ).

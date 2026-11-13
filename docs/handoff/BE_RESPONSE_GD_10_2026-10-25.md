# Giai đoạn 10 — BE trả lời FE (phần 1–2/4): giá bán trên đơn, khách bắt buộc, giao hàng

**Bản:** v1 · **Ngày:** 25/10/2026 · **Trả lời:** `FE_HANDOFF_GD_10_2026-10-15.md` (v1).
**Phạm vi bản này:** phần **1** (nền đơn bán: giá, khách master, baseline, quyền) và phần **2** (phiếu giao hàng). Hoá đơn
(phần 3) và thanh toán + màn hình tổng hợp (phần 4) gửi ở `_v2`, `_v3`. Câu hỏi §5 thuộc hai phần sau được trả lời ở bản đó.

## §0 — Kết luận

BE đã có **giá bán trên đơn bán** (ẩn hẳn với người không có quyền tiền), **bắt buộc khách master khi xác nhận đơn**, và
**phiếu giao hàng đầy đủ** (ứng viên, nguồn tồn, nháp giữ hạn mức, ghi sổ xuất kho nguyên tử — trừ phần giữ của chính đơn trước,
huỷ nháp, lịch sử, liên quan theo lô). Nhu cầu hoạch định của đơn **giảm theo lượng đã giao**. Đã gọi thật trên môi trường dev.

| Nhóm | Kết quả |
|---|---|
| R01, R02, Q4, Q5, Q6 | **Làm** — giá `unitPrice` (thiếu ≠ 0), bổ sung giá thiếu, giá đã biết khoá, khách bắt buộc khi xác nhận, đơn cũ `deliveryBaselineKnown = false` |
| R03–R09, R19–R23, R27, R28 | **Làm** — 4 GET + `POST`/`PATCH`/`post`/`cancel` phiếu giao, ứng viên, nguồn |
| R25, Q32 | **Làm, khác công thức FE** — xem §1 |
| R26, Q37 | **Làm** — huỷ đơn bị chặn khi còn phiếu giao nháp/đã ghi sổ |
| R10, Q31 | **Không có giá vốn** — `valuationStatus = "PENDING_COSTING"`, không trường giá trị nào; không lấy giá bán thay |
| R11–R18, R24, R29 (hoá đơn/thanh toán) | Chưa — phần 3, 4 |
| Lệch contract | **5 chỗ**, §2 |

## §1 — Vì sao các quyết định này

1. **Thiếu giá ≠ giá 0.** Một dòng đơn chưa có giá có `unitPrice: null`; `0` là giá đã biết. Khi đơn còn `DRAFT`, giá đổi cùng
   nháp; khi đã xác nhận, chỉ **bổ sung** được dòng còn thiếu, và giá đã biết — kể cả 0 — bị khoá.
2. **Xác nhận đơn cần khách master.** Hoá đơn và thanh toán gắn khách master; tên gõ tay không khoá được.
3. **Đơn đã xác nhận trước khi có giao hàng không giao được.** Không có gì ghi lại hàng đã rời kho cho các đơn đó trước nay; coi
   là 0 là đoán. Đơn mới (và đơn còn nháp) luôn có baseline biết.
4. **Nguồn giao = hàng tự do + phần giữ của chính đơn.** Hệ thống **không có** sổ "lô này thuộc đơn nào" ngoài phần giữ chỗ
   (giữ hàng ở GĐ6). Vì vậy: không lấy được phần giữ của đơn khác (A09); ca A12 theo nghĩa "WO earmark cho đơn khác" **không xảy ra
   được** vì không có earmark để vi phạm — thứ chặn duy nhất là phần giữ của đơn khác; NON_TRACKED (A13) dùng chính phần giữ làm
   bằng chứng nguồn.
5. **Nhu cầu hoạch định:** BE dùng `max(0, đặt − max(QC đạt, đã giao) − đang giữ − WO còn phủ)`, **không** trừ thẳng "đã giao".
   Lý do đo được trên dữ liệu: trừ thẳng làm hàng QC đạt rồi đem giao bị trừ **hai lần** (đặt 10, QC đạt 6, WO đóng hụt, giao 6 ⇒
   công thức trừ thẳng ra 0, thực tế còn thiếu 4). Lấy `max` thì: giao từ phần giữ hay từ hàng tự do không để lượng đó bị hoạch
   định lại; hàng đã QC đạt rồi giao chỉ tính một lần. Run đã lưu không đổi.

## §2 — Thay đổi contract

### 2.1 Đơn bán (root `/api/sales-orders/v1` giữ nguyên — Q2)

| | Trước | Sau |
|---|---|---|
| `POST /api/sales-orders/v1/drafts`, `PATCH …/{id}/draft` — mỗi dòng | `itemId`, `orderedQuantity`, `dueDate` | thêm **`unitPrice`** tuỳ chọn: số 0..999999999, ≤ 6 số lẻ. Vắng = chưa có giá |
| `POST /api/sales-orders/v1` (tạo cũ) | không có khách master | thêm **`customerId`** tuỳ chọn; có thì tên khách lấy từ master |
| `POST …/{id}/confirm` | đơn chỉ có tên khách xác nhận được | đơn **không có `customerId`** ⇒ **422** `OPERATION_NOT_ALLOWED` field `customerId` |
| `POST …/{id}/cancel` | — | còn phiếu giao `DRAFT`/`POSTED` ⇒ **409** `STATE_CONFLICT` |
| `GET …/{id}` (chi tiết) | — | thêm `deliveryBaselineKnown` (bool), `deliveryBaselineUnknownReason` (`"LEGACY_ORDER"` hoặc `null`), và — **chỉ với người được thấy tiền** — object `commercial` `{currency:"VND", pricingVersion, missingPriceLineCount}` |
| `GET …/{id}` — mỗi dòng | `deliveredQuantity` luôn `null` | `deliveredQuantity` = tổng phiếu giao đã ghi sổ (`null` khi baseline không biết); `metricsEvidence.delivered` = `KNOWN`/`UNKNOWN`; **chỉ với người được thấy tiền**: `commercial` `{unitPrice, currency, priceKnown}` |
| `GET …/{id}` — `uncoveredQuantity` của dòng | `đặt − QC đạt − giữ − WO phủ` | `đặt − max(QC đạt, đã giao) − giữ − WO phủ` (§1.5) |
| `GET …/planning-demands` — `uncoveredQuantity` | như trên | cùng công thức mới |
| **mới** `POST …/{id}/commercial-prices` | — | bổ sung giá thiếu cho đơn đã xác nhận (§4.1) |

**Ai thấy giá (R28):** người có `PERM_SALES_PRICE_MANAGE` hoặc một trong `PERM_SALES_INVOICE_READ/MANAGE/POST` trên nhà máy của đơn.
Người khác (ví dụ thủ kho chỉ có quyền đọc đơn) nhận **cùng response nhưng không có key `commercial` nào** — vắng mặt, không phải
`null`. Danh sách đơn (`GET /api/sales-orders/v1`) **không bao giờ** có tiền.

### 2.2 Phiếu giao hàng (mới)

| Endpoint | Quyền | Ghi chú |
|---|---|---|
| `GET /api/v1/deliveries?plantId=…` + `status`, `salesOrderId`, `customerId`, `warehouseId`, `fromDate`, `toDate`, `search`, `page`, `size` | bất kỳ `PERM_DELIVERY_READ/MANAGE/POST` | `plantId` **bắt buộc** (lệch #1); `search` khớp mã phiếu, tên khách, số đơn, mã vật tư |
| `GET /api/v1/deliveries/{id}` | như trên | chi tiết + dòng + nguồn + movement + `allowedActions` |
| `GET /api/v1/deliveries/{id}/history` | như trên | `CREATED`/`POSTED`/`CANCELLED`, có người + lúc + lý do |
| `GET /api/v1/deliveries/related?plantId=…&lotId=…` | như trên | phiếu `POSTED` đã lấy từ lô đó. Theo phiếu nhập/WO: phần 4 |
| `GET /api/sales-orders/v1/{id}/delivery-candidates?editingDeliveryId=` | như trên | dòng đơn + đã giao + nháp khác + còn nhận được |
| `GET /api/sales-orders/v1/{id}/lines/{lineId}/delivery-sources?editingDeliveryId=&warehouseId=` | như trên | vị trí tồn: tự do + phần giữ của chính dòng; `sourceKey` |
| `POST /api/v1/deliveries` | `PERM_DELIVERY_MANAGE` | **201**; tạo `DRAFT` (chỉ giữ hạn mức, không đụng tồn) |
| `PATCH /api/v1/deliveries/{id}` | `PERM_DELIVERY_MANAGE` + **người tạo** | thay toàn bộ ngày, ghi chú, dòng |
| `POST /api/v1/deliveries/{id}/post` | `PERM_DELIVERY_POST` | ghi sổ — người có quyền POST ghi được cả phiếu mình tạo (Q28) |
| `POST /api/v1/deliveries/{id}/cancel` | `PERM_DELIVERY_MANAGE` + **người tạo** | chỉ `DRAFT`; lý do 5–2000 ký tự |

Mọi lệnh **bắt buộc** `Idempotency-Key` và trả `{documentId, type, version}` (Q25) — FE đọc lại chi tiết sau đó.

**Body tạo:** `salesOrderId`, `expectedOrderVersion`, `documentDate` (`YYYY-MM-DD`), `note?` (≤ 2000), `lines[1..100]` mỗi dòng
`{salesOrderLineId, sourceKey, quantity}` (số dương, ≤ 6 số lẻ, ≤ 999999999). **Sửa:** `expectedVersion`, `documentDate`, `note?`,
`lines`. **Ghi sổ:** `{expectedVersion}`. **Huỷ:** `{expectedVersion, reason}`.

**Luật:**

- Đơn phải `CONFIRMED`/`IN_PRODUCTION`/`PARTIALLY_FULFILLED`/`FULFILLED`, có baseline và khách master còn hoạt động.
- Hạn mức mỗi dòng đơn: `đặt − đã giao − nháp khác`; phiếu đang sửa **không** tính trùng phần của chính nó.
- `documentDate` ≤ hôm nay theo múi giờ nhà máy và ≥ ngày đơn.
- Nguồn chỉ là lô `AVAILABLE` chưa hết hạn (hoặc hàng không theo lô) trong kho của nhà máy đơn. Tồn được kiểm khi lưu nháp và
  **kiểm lại khi ghi sổ** dưới khoá.
- Ghi sổ: mỗi dòng xuất **phần giữ của chính dòng đơn tại đúng vị trí đó trước**, phần còn lại từ hàng tự do — tối đa hai movement
  `ISSUE` (`reservedMovementId`, `freeMovementId`). Phiếu, tồn, phần giữ, lượng đã giao, version đơn (+1) cùng commit hoặc cùng huỷ.
- `POSTED` và `CANCELLED` là cuối — không sửa, không huỷ, không ghi sổ lại (Q33). `POSTED` nghĩa là "đã xuất kho", không phải
  khách đã nhận (Q39). Đủ hàng không tự đóng đơn (Q40).

### 2.3 Lệch so với đề xuất FE

| # | FE đề xuất | BE làm | Vì sao |
|---|---|---|---|
| 1 | Danh sách lấy nhà máy từ `X-Plant-Id` | `plantId` trong query **bắt buộc**; `X-Plant-Id` chỉ dùng để đối chiếu (lệch ⇒ 409) | quy ước chung của hệ thống |
| 2 | ID ngoài phạm vi ⇒ 404 (Q35) | **403** `PERMISSION_DENIED` | guard tra bản ghi để biết nhà máy rồi từ chối trước — hành vi toàn hệ thống |
| 3 | Số lượng/tiền là chuỗi số | **số JSON** (như mọi lượng khác); tiền sẽ là số nguyên ≤ 9007199254740991 | một kiểu số cho cả hệ thống; `10^18` không biểu diễn được an toàn trong JS |
| 4 | `sourceSnapshotVersion` bắt buộc khi lưu/ghi sổ | **không dùng** — server kiểm lại mọi thứ dưới khoá khi ghi sổ; `version` phiếu + `expectedOrderVersion` đủ chặn ghi đè | token chụp nguồn không chặn thêm được gì mà khoá chưa chặn |
| 5 | Mã lỗi `DELIVERY_EXCEEDED`, `INSUFFICIENT`, `STALE_VERSION`, `NOT_FOUND`, `INVALID_STATE` | `PLANNED_QUANTITY_EXCEEDED`, `INSUFFICIENT_AVAILABLE_STOCK`, `CONCURRENT_MODIFICATION`, `PERMISSION_DENIED`/`ENTITY_NOT_FOUND`, `STATE_CONFLICT` | dùng mã sẵn có của hệ thống — bảng §5 |

Hạn lô dạng ngày (Q19): giữ như GĐ9 — lô hết hiệu lực **từ 00:00 ngày hết hạn** theo giờ nhà máy. Hai handoff GĐ9/GĐ10 nghiêng hai
hướng; đổi bây giờ sẽ làm các lô đã tạo hết hạn khác đi. Nhà máy thiếu múi giờ (Q20): không thể xảy ra — múi giờ là bắt buộc từ GĐ2.

## §3 — Breaking change

| # | Thay đổi | Ai bị ảnh hưởng |
|---|---|---|
| 1 | Xác nhận đơn không có `customerId` ⇒ **422** field `customerId` | client tạo đơn bằng `POST /api/sales-orders/v1` mà không gửi `customerId` |
| 2 | Đơn đã xác nhận trước 25/10/2026 có `deliveryBaselineKnown: false` và **không giao được** (`409 SOURCE_UNKNOWN`) | dữ liệu demo/thử hiện có — tạo đơn mới để thử GĐ10 |
| 3 | Huỷ đơn còn phiếu giao nháp/đã ghi sổ ⇒ **409** `STATE_CONFLICT` | chỉ khi có phiếu giao |
| 4 | `deliveredQuantity` của dòng đơn từ luôn `null` sang **số thật** (0 nếu chưa giao) khi baseline biết | màn hình đơn bán |
| 5 | `uncoveredQuantity` (đơn và `planning-demands`) giảm theo lượng đã giao | chỉ khi có phiếu giao `POSTED` |
| 6 | Field `commercial` **vắng mặt** với người không được thấy tiền | FE nếu giả định field luôn có |

## §4 — Ví dụ copy từ lần gọi thật (25/10/2026, dev)

### 4.1 Bổ sung giá thiếu

```http
POST /api/sales-orders/v1/cabbc107-5d02-4c3b-b6fe-78adc41a8779/commercial-prices
Idempotency-Key: 141b162f-7e39-4dd2-bd65-c8c53d0efda3
```
```json
{"expectedVersion": 1, "prices": [{"salesOrderLineId": "6f704246-3c95-4942-b4e4-85db6d3319e2", "unitPrice": 0}]}
```
HTTP 200, `X-Trace-Id: a5cf9736c10145e1`:
```json
{"code":"SUCCESS","result":{"documentId":"cabbc107-5d02-4c3b-b6fe-78adc41a8779","type":"SALES_PRICE","version":2,"pricingVersion":"PRICE-1"},"message":"OK"}
```
Gửi lại giá khác cho chính dòng đó (`unitPrice: 5`) — HTTP 409, `X-Trace-Id: fbcff9e0aa054bbb`:
```json
{"code":"PRICE_LOCKED","message":"prices[0].unitPrice: the line already has a price; a known price is locked","errors":[{"field":"prices[0].unitPrice","message":"the line already has a price; a known price is locked"}]}
```

### 4.2 Nguồn của một dòng đơn (dòng đặt 8, đơn này đang giữ 3 ở lô L1)

`GET /api/sales-orders/v1/cabbc107-5d02-4c3b-b6fe-78adc41a8779/lines/48a32138-8ec1-4da3-bfdf-211f0fb827d2/delivery-sources` — HTTP 200,
`X-Trace-Id: bb3c1b1d214943a2`:
```json
{"code":"SUCCESS","result":{"content":[{"sourceKey":"MWNjM2NkYzEtYmNmNi00NzM4LWJiYWYtNzg4Mjc5MTliY2ZlfDVlOTJjZWNhLTUyNWUtNDRiMC1iNjRhLWVlZGRkYjk2ZDQ4ZA","warehouseId":"1cc3cdc1-bcf6-4738-bbaf-78827919bcfe","warehouseCode":"FG-171137","warehouseName":"FG","itemId":"a9173858-d651-4fe3-94bc-b572ec3177fb","uom":"EA","lotId":"5e92ceca-525e-44b0-b64a-eedddb96d48d","lotCode":"L1-171137","expiresAt":null,"freeQuantity":7.000000,"ownReservedQuantity":3.000000,"availableForLine":8.000000},{"sourceKey":"MWNjM2NkYzEtYmNmNi00NzM4LWJiYWYtNzg4Mjc5MTliY2ZlfDRkMTYxN2I0LTY3YmYtNDQwYS04ZWZlLTZjODkxZjNmOWM1NQ","warehouseId":"1cc3cdc1-bcf6-4738-bbaf-78827919bcfe","warehouseCode":"FG-171137","warehouseName":"FG","itemId":"a9173858-d651-4fe3-94bc-b572ec3177fb","uom":"EA","lotId":"4d1617b4-67bf-440a-8efe-6c891f3f9c55","lotCode":"L2-171137","expiresAt":null,"freeQuantity":4.000000,"ownReservedQuantity":0,"availableForLine":4.000000}],"page":0,"size":20,"totalElements":2,"totalPages":1,"first":true,"last":true},"message":"OK"}
```

### 4.3 Tạo nháp, vượt hạn mức, ghi sổ

```http
POST /api/v1/deliveries
Idempotency-Key: 97a365fd-bc72-4d51-b939-d625eafb7d34
```
```json
{"salesOrderId": "cabbc107-5d02-4c3b-b6fe-78adc41a8779", "expectedOrderVersion": 3, "documentDate": "2026-10-25", "note": "Giao lan 1", "lines": [{"salesOrderLineId": "48a32138-8ec1-4da3-bfdf-211f0fb827d2", "sourceKey": "MWNjM2NkYzEtYmNmNi00NzM4LWJiYWYtNzg4Mjc5MTliY2ZlfDVlOTJjZWNhLTUyNWUtNDRiMC1iNjRhLWVlZGRkYjk2ZDQ4ZA", "quantity": 5}]}
```
HTTP 201, `X-Trace-Id: 25a7b43feb6943ce`:
```json
{"code":"SUCCESS","result":{"documentId":"ec945cb2-cce9-4b5d-becb-e10950f8f98f","type":"DELIVERY","version":0},"message":"Created successfully"}
```
Nháp thứ hai lấy 4 trên phần còn 3 — HTTP 409, `X-Trace-Id: 5147c496da45415a`:
```json
{"code":"PLANNED_QUANTITY_EXCEEDED","message":"lines[0].quantity: the order line can still be delivered 3 (ordered minus delivered minus other draft deliveries)","errors":[{"field":"lines[0].quantity","message":"the order line can still be delivered 3 (ordered minus delivered minus other draft deliveries)"}]}
```
Ghi sổ (`POST /api/v1/deliveries/ec945cb2-cce9-4b5d-becb-e10950f8f98f/post`, `Idempotency-Key: 017c49ca-22be-429d-8fb7-f120527b86b1`,
body `{"expectedVersion": 2}`) — HTTP 200, `X-Trace-Id: d3f63f26b1d943c9`:
```json
{"code":"SUCCESS","result":{"documentId":"ec945cb2-cce9-4b5d-becb-e10950f8f98f","type":"DELIVERY","version":3},"message":"OK"}
```
Chi tiết sau ghi sổ (`GET /api/v1/deliveries/ec945cb2-cce9-4b5d-becb-e10950f8f98f`, `X-Trace-Id: b2762acfa9634e77`), trích dòng:
```json
{"deliveryLineId":"ea48a592-5e08-4782-872c-b357e5214956","lineNo":1,"salesOrderLineId":"48a32138-8ec1-4da3-bfdf-211f0fb827d2","salesOrderLineNo":1,"itemId":"a9173858-d651-4fe3-94bc-b572ec3177fb","itemCode":"BIKE-171137","itemName":"Bike","uom":"EA","warehouseId":"1cc3cdc1-bcf6-4738-bbaf-78827919bcfe","warehouseCode":"FG-171137","lotId":"5e92ceca-525e-44b0-b64a-eedddb96d48d","lotCode":"L1-171137","sourceKey":"MWNjM2NkYzEtYmNmNi00NzM4LWJiYWYtNzg4Mjc5MTliY2ZlfDVlOTJjZWNhLTUyNWUtNDRiMC1iNjRhLWVlZGRkYjk2ZDQ4ZA","quantity":5.000000,"reservedQuantity":3.000000,"freeQuantity":2.000000,"reservedMovementId":"15623369-3a34-47e5-882d-6e79a51e6f5b","freeMovementId":"255d61e0-3625-4939-a104-8490bad42631"}
```
và header của chính response đó: `"code":"DN-000004"`, `"status":"POSTED"`, `"version":3`, `"totalQuantity":5.000000`,
`"valuationStatus":"PENDING_COSTING"`, `"postedAt":"2026-10-25T10:11:43.159674Z"`. Phiếu nháp do thủ kho tạo trả
`"allowedActions":["EDIT","CANCEL","POST"]`; phiếu đã ghi sổ trả `[]`.

### 4.4 Đơn cũ

`POST /api/v1/deliveries` cho đơn `D26-SO-1002` (xác nhận trước 25/10) — HTTP 409, `X-Trace-Id: 57163c8ecb594c48`:
```json
{"code":"SOURCE_UNKNOWN","message":"This order has no known delivery baseline (BASELINE_UNKNOWN); it cannot be delivered"}
```

## §5 — Envelope và mã lỗi

Mọi response dùng `{code, result, message, errors?}`; `code` là tên hằng, FE rẽ nhánh theo nó; `errors` chỉ có khi lỗi theo field;
danh sách là `{content, page, size, totalElements, totalPages, first, last}`. Lỗi không có `result`.

| Tình huống | HTTP | `code` | `errors[].field` |
|---|---|---|---|
| Body sai (số 0/âm, 7 số lẻ, > 100 dòng, thiếu field, lý do < 5 ký tự) | 400 | `VALIDATION_ERROR` | đúng path, ví dụ `lines[0].quantity`, `reason` |
| Thiếu `Idempotency-Key` | 400 | `MISSING_REQUIRED_FIELD` | — |
| `sourceKey` không do server phát | 400 | `VALIDATION_ERROR` | `lines[i].sourceKey` |
| Ngày sau hôm nay / trước ngày đơn | 400 | `VALIDATION_ERROR` | `documentDate` |
| Giá âm / > 999999999 / 7 số lẻ | 400 | `VALIDATION_ERROR` | `prices[i].unitPrice` |
| Không đủ quyền, ID ngoài phạm vi, không phải người tạo | 403 | `PERMISSION_DENIED` | — |
| `X-Plant-Id` lệch `plantId` | 409 | `STATE_CONFLICT` | — |
| Phiếu không `DRAFT`, đơn sai trạng thái, huỷ đơn còn phiếu giao | 409 | `STATE_CONFLICT` | — |
| `version`/`expectedOrderVersion` cũ | 409 | `CONCURRENT_MODIFICATION` | — |
| Đơn cũ / không có khách master | 409 | `SOURCE_UNKNOWN` | — |
| Vượt `đặt − đã giao − nháp khác` | 409 | `PLANNED_QUANTITY_EXCEEDED` | `lines[i].quantity` |
| Không đủ tồn tự do + phần giữ của chính đơn | 409 | `INSUFFICIENT_AVAILABLE_STOCK` | `lines[i].quantity` |
| Lô `HOLD`/`REJECTED`/hết hạn | 409 | `LOT_NOT_ELIGIBLE` | `lines[i].sourceKey` |
| Giá đã biết khi bổ sung | 409 | `PRICE_LOCKED` | `prices[i].unitPrice` |
| Cùng key, body/đích khác | 409 | `IDEMPOTENCY_CONFLICT` | — |
| Xác nhận đơn thiếu khách master | 422 | `OPERATION_NOT_ALLOWED` | `customerId` |
| Khách / kho / vật tư ngừng hoạt động | 422 | `RESOURCE_INACTIVE` | — |

**Idempotency (Q23, Q24):** khoá theo `(key, lệnh)` + so dấu vân tay body và đích; **không hết hạn** (mạnh hơn 24 giờ FE xin). Gửi
lại cùng key + body trả **trạng thái hiện tại** của chứng từ, không chạy lần hai; quyền được kiểm lại trước khi trả. Không có
trạng thái "đang xử lý": hai request cùng key cùng lúc ⇒ một chạy, một nhận 409. Không phạm vi theo người: cùng key giữa hai người
là xung đột thật.

## §6 — Quyền và giới hạn

10 quyền mới, cấp sẵn cho **ADMIN** và **MANAGER** (OPERATOR không được cấp ngầm): `PERM_DELIVERY_READ/MANAGE/POST`,
`PERM_SALES_INVOICE_READ/MANAGE/POST`, `PERM_CUSTOMER_PAYMENT_READ/MANAGE/POST`, `PERM_SALES_PRICE_MANAGE`. Phạm vi theo nhà máy.

- Đọc giao hàng = bất kỳ quyền giao hàng nào; `POST` không ngầm cho `MANAGE` (Q26). Sửa/huỷ nháp chỉ người tạo (Q27).
- Người chỉ có quyền giao hàng không thấy giá bán ở bất kỳ đâu (phiếu giao không có trường tiền nào).
- Hàng **theo sê-ri** chưa giao được (`422 OPERATION_NOT_ALLOWED`).
- `/related` chỉ theo lô trong bản này.

## §7 — Bằng chứng

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — 2053 unit + 294 IT, 0 failures, 0 errors, chạy lại sau bản sửa §7.1 |
| HTTP thật | dev, Postgres + Redis Docker, 25/10/2026 ~17:11 (+07:00), công ty thử `GD10-171137`: **39/39** kiểm tra đạt (giá, ẩn giá với thủ kho, đơn cũ, ứng viên, nguồn, nháp, hạn mức, sửa 5→4→5, nguồn giả, ngày tương lai, ghi sổ hai chân, replay, ghi sổ lại/huỷ phiếu đã ghi sổ, nhu cầu hoạch định 8 − 5 = 3, huỷ nháp trả hạn mức một lần, huỷ đơn bị chặn, `X-Plant-Id` lệch, danh sách/tìm, liên quan, lịch sử, lô `HOLD`, thủ kho tạo + ghi sổ phiếu của mình) |
| Tranh chấp | Postgres thật: hai phiếu cùng đơn và hai phiếu của **hai đơn** cùng vị trí, 6 tồn, mỗi phiếu 5 — luôn một thắng, một nhận `INSUFFICIENT_AVAILABLE_STOCK`, tồn còn 1 |

### 7.1 Lỗi do chính lượt smoke tìm ra, đã sửa trước khi gửi

Lượt smoke đầu tiên cho thấy `version` nhảy **+2** ở hai lệnh: bổ sung giá, và sửa nháp phiếu giao **khi đổi ghi chú**. Đã sửa;
lượt smoke thứ hai đạt 39/39. FE luôn dùng `version` trả về để gửi lệnh tiếp — không tự cộng.

**Chưa nghiệm thu:** chưa chạy trên môi trường FE; hàng không theo lô chỉ đo bằng test tự động (smoke dùng lô); `/related` theo phiếu
nhập/WO và tổng tiền trên đơn — phần 4.

## §8 — Chưa làm và việc FE cần làm

| Mục FE xin | Vì sao chưa | Khi nào |
|---|---|---|
| Hoá đơn (R11–R15, Q7, Q8, Q15, Q22) | phần 3 | `_v2` |
| Thanh toán, tiến độ đơn, liên kết ngược phiếu nhập/WO (R16–R18, R24) | phần 4 | `_v3` |
| Giá vốn / giá trị xuất kho (R10) | không có lớp giá vốn — để GĐ11 | GĐ11 |
| Sổ "quyền hưởng theo nguồn" (A12, Q13) | xem §1.4 | không làm |
| `sourceSnapshotVersion`, trạng thái `REQUEST_IN_PROGRESS`, TTL idempotency | §2.3 #4, §5 | không làm |

**Bắt buộc:** gửi `customerId` khi tạo đơn (hoặc gắn qua `PATCH …/draft`) trước khi xác nhận · đọc `deliveryBaselineKnown` để khoá nút
giao hàng · luôn gửi `Idempotency-Key` cho lệnh GĐ10 · lấy `sourceKey` từ `delivery-sources`, không tự ghép · coi field `commercial`
vắng mặt là "không được thấy" · dùng `version` từ response.
**Nên làm:** hiển thị `availableForLine` thay vì tự cộng `free + own` · làm mới nguồn sau mỗi lần ghi sổ.

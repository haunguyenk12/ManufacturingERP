# BE trả lời — Giai đoạn 7: Đơn mua hàng, nhận hàng và QC

**Trả lời cho:** `FE_HANDOFF_GD_07_2026-10-14.md` (v1, P1) · **Ngày gửi:** 19/10/2026 · **Bản:** v3 — **phần QC mua hàng (`G7c`)**, bổ sung v1 + v2
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. Bản v1 (đơn mua hàng) và v2 (phiếu nhận) vẫn đúng nguyên văn; bản này thêm
phần 3 và là phần **cuối** của Giai đoạn 7 phía BE. Chưa phải "FE nghiệm thu đạt": task chỉ đóng khi FE chạy A01–A38 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Hàng mua đang chờ kiểm nay được QC theo lượng, nhiều lần trên một dòng phiếu: phần đạt sang lô con `AVAILABLE` (hàng không theo lô thì rời
vùng chờ kiểm), phần loại sang lô con `REJECTED` (hàng không theo lô thì ở lại vùng chờ kiểm), phần chưa QC ở lại lô gốc `HOLD` — tồn thực tế và
đơn mua hàng không đổi, và không còn đường nào đưa hàng ra khỏi `HOLD` mà không qua QC.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R10 | `accepted + rejected > 0` và `≤ pending`, loại phải có lý do; QC nhiều lần; version phiếu kiểm dưới khoá | 🟡 **Chưa có, đã làm** | 0 + 0 ⇒ `400` field `acceptedQuantity`; loại thiếu lý do ⇒ field `reason`; vượt ⇒ `409 PLANNED_QUANTITY_EXCEEDED`; hai quyết định cùng version ⇒ `200` + `409 CONCURRENT_MODIFICATION` (§4.2–§4.6) |
| R11 | QC theo lô tạo lô con có lô cha, hạn dùng, nguồn; hàng không theo lô truy được phần đạt/loại; pending giảm đúng | 🟡 **Chưa có, đã làm** | Lô 4: đạt 2.5 ⇒ `LOT-…-A1`, loại 0.5 ⇒ `LOT-…-R1`, cả hai mang `parentLotId` + hạn của lô gốc, lô gốc còn 1 ở `HOLD` (§4.3) |
| R12 | QC không đổi tồn thực tế / PO đã nhận; nhận đủ không tự QC xong; loại không mở lại PO | 🟡 **Đã làm** | Tồn thực tế vật tư theo lô 4 → 4 sau QC; hàng không theo lô 25 → 25 (chờ kiểm 25 → 5) (§4.3, §4.4) |
| R13 | Hàng `HOLD`/`REJECTED` không bị đổi trạng thái đi vòng QC | 🟡 **Đã làm phần trạng thái lô** | `POST /v1/inventory/lots/{id}/status` trên lô đang chờ QC ⇒ `409 LOT_NOT_ELIGIBLE`; trên lô con bị loại ⇒ `409 LOT_NOT_ELIGIBLE` (§4.7). Giới hạn ở §6 |
| R15 | Hàng đợi QC có tên/mã/đơn vị/nguồn/số liệu, không phải gọi danh mục từng dòng | 🟡 **Đã làm** | `GET /v1/purchase-qc` (§4.1) |
| R16 | QC không lộ giá | ✅ **Đã làm** | Response hàng đợi + quyết định + lịch sử **không** có field giá nào |
| R17 (phần QC) | Gửi lại cùng key ⇒ cùng kết quả; khác payload ⇒ xung đột | 🟡 **Đã làm** | Cùng key ⇒ cùng `decisionId`; đổi payload ⇒ `409 IDEMPOTENCY_CONFLICT` (§4.5) |
| R18 | Lịch sử quyết định bất biến: ai, khi nào, lý do, lô, movement | 🟡 **Đã làm** | `GET /v1/goods-receipts/{id}/quality-decisions` (§4.8) |
| R20 (phần QC) | Không breaking change cho QC sản xuất | ✅ **Không đụng** | QC sản xuất (`/production-receipts/{id}/qc-disposition`) giữ nguyên; chặn phiếu nhận đã có QC khỏi đảo nhập là chặn mới (§3) |
| R19 (giá trị tồn) | ⏳ **Hoãn có chủ đích** (như v1) | v1 §8 |

### Trả lời Q (những câu còn lại)

| Q | Trả lời |
|---|---|
| Q1 (phần QC) | **Có, đúng tên FE:** `PERM_PURCHASE_QC_READ` (hàng đợi), `PERM_PURCHASE_QC_MANAGE` (ghi quyết định). Lịch sử quyết định của một phiếu mở bằng `PERM_PURCHASE_QC_READ` **hoặc** `PERM_GOODS_RECEIPT_READ` **hoặc** `PERM_PURCHASE_ORDER_READ` |
| Q16 | **Có.** Mỗi phần đạt/loại của hàng theo lô thành một lô con mới có `parentLotId`; phần chưa QC ở lại lô gốc `HOLD`, lô gốc **không** đổi trạng thái — kể cả khi đạt toàn bộ (lô gốc về 0, vẫn `HOLD`). Mã lô con do server sinh: `<mã gốc>-A1`, `-A2`… cho phần đạt, `-R1`… cho phần loại, lấy hậu tố đầu tiên chưa có (không phân biệt hoa thường), cắt cho vừa 120 ký tự |
| Q18, Q19 | **Hoãn** cùng giá trị tồn (v1 §8). Không trường giá trị nào trong phần QC |
| Q24 (phần QC) | **Có.** Hai quyền QC chỉ cấp cho `ADMIN` + `MANAGER`; `OPERATOR` **không** |
| Q27 | **Có.** Cùng một người được nhận hàng và QC nếu có cả hai quyền; không ép tách nhiệm vụ |
| Q30 | **Có.** Đơn mua hàng không có khái niệm "dành riêng" (earmark), nên toàn bộ lượng còn mở (`ordered − received − closed`) của PO `CONFIRMED`/`PARTIALLY_RECEIVED` là nguồn cung tự do cho MRP. ⚠️ Hệ quả đáng biết: hàng **đã nhận nhưng còn chờ kiểm** không còn là lượng mở của PO và cũng chưa khả dụng, nên MRP không thấy nó ở đâu cả cho tới khi QC đạt |

---

## §1 — Nguyên nhân thật

Không có lỗi FE báo — đây là phần **chưa có**. Ba điều đáng nói, theo thứ tự ảnh hưởng tới FE:

1. **QC là cách duy nhất đưa hàng mua ra khỏi vùng chờ kiểm.** Lệnh đổi trạng thái lô chung nay từ chối lô còn chờ QC mua và lô con bị QC loại.
   Phiếu nhận đã có ít nhất một quyết định QC **không đảo nhập được nữa** — hàng đã rời chỗ phiếu đặt nó.
2. **Hàng không theo lô bị loại vẫn nằm trong số "chờ kiểm"** (`qualityHoldQuantity`), thuộc về dòng phiếu (`qualityBucketId`). Hệ thống chưa có
   ô "đã loại" riêng cho hàng không theo lô — màn hình tồn sẽ thấy phần loại cộng chung với phần đang chờ. Hàng đợi QC thì tách rõ.
3. **Nghiệm thu qua HTTP thật tìm ra một lỗi mà test tự động không thấy:** trong response của lệnh QC, `childLots[].parentLotId` là `null` dù CSDL
   và `GET /v1/inventory/lots` đều đúng (lô con vừa tạo trong cùng lệnh chưa có giá trị đó). Đã sửa và thêm test khoá lại; §4.3 là response sau sửa.

---

## §2 — Thay đổi contract

### 2.1 Endpoint

| Method | Path | Mới / đổi | Quyền |
|---|---|---|---|
| GET | `/api/v1/purchase-qc?companyId&plantId&warehouseId?&search?&receivedDateFrom?&receivedDateTo?&page&size` | **Mới** — hàng đợi QC. `X-Plant-Id` phải khớp `plantId` | `PERM_PURCHASE_QC_READ` (nhà máy) |
| POST | `/api/v1/goods-receipts/{id}/lines/{lineId}/qc` | **Mới** — ghi một quyết định. Header `Idempotency-Key` tuỳ chọn | `PERM_PURCHASE_QC_MANAGE` |
| GET | `/api/v1/goods-receipts/{id}/quality-decisions?page&size` | **Mới** — lịch sử, mới nhất trước | `PERM_PURCHASE_QC_READ` **hoặc** `PERM_GOODS_RECEIPT_READ` **hoặc** `PERM_PURCHASE_ORDER_READ` |

Request QC: `{expectedVersion, acceptedQuantity, rejectedQuantity, reason?}` — `expectedVersion` là `version` **của phiếu nhận** (hàng đợi trả
sẵn ở `goodsReceiptVersion`); hai lượng ≥ 0, tối đa 13 chữ số nguyên + 6 chữ số lẻ; `reason` ≤ 500, bắt buộc khi `rejectedQuantity > 0`.

Response QC: `{decision, receiptVersion, receivedQuantity, acceptedQuantity, rejectedQuantity, pendingQuantity}` — `receiptVersion` là version
**mới** của phiếu, dùng cho quyết định tiếp theo; bốn số lượng là số **tổng** của dòng sau quyết định.

### 2.2 Một quyết định (`decision`, cũng là một dòng lịch sử)

`decisionId`, `goodsReceiptId`, `goodsReceiptCode`, `goodsReceiptLineId`, `itemId`, `itemCode`, `itemName`, `uomCode`, `trackingPolicy`,
`acceptedQuantity`, `rejectedQuantity`, `reason`, `decidedBy`, `decidedByUsername`, `decidedAt`, `qualityBucketId` (= id dòng phiếu khi hàng
không theo lô, `null` khi theo lô), `childLots[]{lotId, lotCode, parentLotId, status, quantity, expiresAt, movementId}`, `movementIds[]`.

### 2.3 Một dòng hàng đợi

Mã/version/ngày nhận/`postedAt` của phiếu, mã PO, mã + tên NCC, mã + tên kho, `goodsReceiptLineId`, vật tư (`itemCode`, `itemName`, `uomCode`,
`trackingPolicy`), lô (`lotId`, `lotCode`, `expiresOn`, `lotExpiresAt`), `receivedQuantity`, `acceptedQuantity`, `rejectedQuantity`,
`pendingQuantity`, `qualityBucketId`. Chỉ dòng phiếu `POSTED` đã nhận vào vùng chờ kiểm và còn `pendingQuantity > 0`; sắp theo ngày nhận, giờ
post. `search` tìm trên mã phiếu, mã PO, mã/tên NCC, mã/tên vật tư, mã lô.

### 2.4 Field mới ở chỗ khác — additive

- Dòng phiếu nhận (`GET /v1/goods-receipts/{id}` và mọi response phiếu nhận): `acceptedQuantity`, `rejectedQuantity`, `pendingQuantity`,
  `qualityBucketId`. `pendingQuantity` chỉ khác 0 khi phiếu đã post vào vùng chờ kiểm.
- Lô (`GET /v1/inventory/lots`, `GET /v1/inventory/lots/{id}`): `parentLotId` (`null` với lô không tách từ lô khác).

---

## §3 — Breaking change

**Wire: CÓ, hẹp.**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /v1/inventory/lots/{id}/status` | lô `HOLD` còn dòng phiếu nhận chờ QC mua, đổi sang trạng thái khác `HOLD` | `200` | `409 LOT_NOT_ELIGIBLE` | **Bắt buộc mới** | dẫn người dùng sang QC mua |
| Như trên | lô con bị QC mua loại | `200` | `409 LOT_NOT_ELIGIBLE` | **Bắt buộc mới** | không cho đổi trạng thái lô đó |
| `POST /v1/goods-receipts/{id}/cancel` | phiếu đã post có ít nhất một quyết định QC | `200` (đảo nhập) | `409 STATE_CONFLICT` | **Bắt buộc mới** | ẩn nút huỷ khi `acceptedQuantity`/`rejectedQuantity` của dòng nào > 0 |
| Tồn khả dụng | sau QC đạt | — | tăng đúng lượng đạt | **Đổi số** (chủ đích) | — |

Không bỏ field nào. Không đổi giá trị mặc định của query param nào. QC sản xuất không đổi.

**Thứ tự deploy:** BE trước. Không có migration dữ liệu cũ nào cần đối soát: phiếu nhận trước v2 không vào vùng chờ kiểm nên không có gì để QC.

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Môi trường dev, cổng 8090, 19/10/2026 khoảng 13:22 (giờ VN), fixture công ty `GD7C-132237`: một PO 40 kg vật tư không theo lô + 10 kg vật tư theo
lô, một phiếu nhận `GR-000015` đã post (25 kg không theo lô, 4 kg vào lô `LOT-132237`). Response dài rút gọn bằng `…` (chỉ cắt, không viết lại).

### 4.1 Hàng đợi — `GET /api/v1/purchase-qc?companyId=ac958362-…&plantId=9fc7488e-…` · `X-Trace-Id: 5bef984f248e4aa2` · **200**

```json
{"code":"SUCCESS","result":{"content":[{"goodsReceiptId":"72192fba-38b9-4f1b-8529-6f6badff1239","goodsReceiptCode":"GR-000015","goodsReceiptVersion":1,"receivedDate":"2026-10-19","postedAt":"2026-10-19T06:22:39.670444Z","purchaseOrderId":"7102e2fa-6d6c-4e62-b195-43c869fa2103","purchaseOrderCode":"PO-000015","supplierId":"0097e3a8-d005-4510-b223-08f5a892eabe","supplierCode":"SUP-132237","supplierName":"NCC Kim loai","warehouseId":"1ec10140-d822-4db1-9b4c-5bbdfd669e6c","warehouseCode":"RM-132237","warehouseName":"Kho nguyen lieu","goodsReceiptLineId":"6f810e1b-916f-4641-b6de-1eacf2900f2b","itemId":"3f026a91-70ee-428b-9d5a-52f78a559d77","itemCode":"RML-132237","itemName":"RML","uomCode":"K132237","trackingPolicy":"LOT_TRACKED","lotId":"0ca60fc3-1915-4087-809d-4b51a228fdae","lotCode":"LOT-132237","expiresOn":"2026-12-18","lotExpiresAt":"2026-12-17T17:00:00Z","receivedQuantity":4.000000,"acceptedQuantity":0.000000,"rejectedQuantity":0.000000,"pendingQuantity":4.000000,"qualityBucketId":null},{…,"goodsReceiptLineId":"ea2a7b8d-ac18-4079-840d-3c2cc7f6d5ee",…,"itemCode":"RM1-132237",…,"trackingPolicy":"NON_TRACKED","lotId":null,"lotCode":null,"expiresOn":null,"lotExpiresAt":null,"receivedQuantity":25.000000,"acceptedQuantity":0.000000,"rejectedQuantity":0.000000,"pendingQuantity":25.000000,"qualityBucketId":"ea2a7b8d-ac18-4079-840d-3c2cc7f6d5ee"}],"page":0,"size":20,"totalElements":2,"totalPages":1,"first":true,"last":true},"message":"OK"}
```

`search=lot-132237` ⇒ 1 dòng (`X-Trace-Id: f8746d8107d841d4`). `X-Plant-Id` khác `plantId` ⇒ `409 STATE_CONFLICT` (`80953ff965fa4f6b`).

### 4.2 Lỗi nhập — `POST /api/v1/goods-receipts/72192fba-…/lines/6f810e1b-…/qc`

Body `{"expectedVersion": 1, "acceptedQuantity": 0, "rejectedQuantity": 0}` · `X-Trace-Id: 869b38724b8c4bfc` · **400**
```json
{"code":"VALIDATION_ERROR","message":"acceptedQuantity: accepted and rejected quantities cannot both be 0","errors":[{"field":"acceptedQuantity","message":"accepted and rejected quantities cannot both be 0"}]}
```
- `{"expectedVersion": 1, "acceptedQuantity": 1, "rejectedQuantity": 1}` (thiếu lý do) ⇒ **400**, `errors[0].field = "reason"` (`7963c907e5a04606`)
- `"acceptedQuantity": 0.1234567` ⇒ **400**, field `acceptedQuantity` (`0661301e0d354965`)
- `{"expectedVersion": 8, …}` ⇒ **409 `CONCURRENT_MODIFICATION`** (`4fbb02fc4c84486b`)
- Đạt 4 + loại 0.000001 trên dòng còn chờ 4 ⇒ **409** (`7613791f7c934dd7`):
```json
{"code":"PLANNED_QUANTITY_EXCEEDED","message":"acceptedQuantity: accepted + rejected exceeds what is still pending on the line: 4","errors":[{"field":"acceptedQuantity","message":"accepted + rejected exceeds what is still pending on the line: 4"}]}
```

### 4.3 QC hàng theo lô — đạt 2.5, loại 0.5 · header `Idempotency-Key: g7c-8a400ba511da47db` · `X-Trace-Id: 36c4ba7311b34dca` · **200**

Body `{"expectedVersion": 1, "acceptedQuantity": "2.5", "rejectedQuantity": "0.5", "reason": "0.5 kg ri set"}`
```json
{"code":"SUCCESS","result":{"decision":{"decisionId":"31ac0a40-4ef3-4ad4-8a04-fbc541d47925","goodsReceiptId":"72192fba-38b9-4f1b-8529-6f6badff1239","goodsReceiptCode":"GR-000015","goodsReceiptLineId":"6f810e1b-916f-4641-b6de-1eacf2900f2b","itemId":"3f026a91-70ee-428b-9d5a-52f78a559d77","itemCode":"RML-132237","itemName":"RML","uomCode":"K132237","trackingPolicy":"LOT_TRACKED","acceptedQuantity":2.5,"rejectedQuantity":0.5,"reason":"0.5 kg ri set","decidedBy":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","decidedByUsername":"admin","decidedAt":"2026-10-19T06:22:40.178622Z","qualityBucketId":null,"childLots":[{"lotId":"94937902-fcab-469f-9248-75ef0720c11d","lotCode":"LOT-132237-A1","parentLotId":"0ca60fc3-1915-4087-809d-4b51a228fdae","status":"AVAILABLE","quantity":2.5,"expiresAt":"2026-12-17T17:00:00Z","movementId":"a20d6409-7b13-4a26-8ae9-fa4bf5839773"},{"lotId":"6e3bb756-ba70-4366-8aae-2e257280dc2e","lotCode":"LOT-132237-R1","parentLotId":"0ca60fc3-1915-4087-809d-4b51a228fdae","status":"REJECTED","quantity":0.5,"expiresAt":"2026-12-17T17:00:00Z","movementId":"78702d5c-afa7-4943-866e-8b383f29140b"}],"movementIds":["a20d6409-7b13-4a26-8ae9-fa4bf5839773","78702d5c-afa7-4943-866e-8b383f29140b"]},"receiptVersion":2,"receivedQuantity":4.000000,"acceptedQuantity":2.500000,"rejectedQuantity":0.500000,"pendingQuantity":1.000000},"message":"OK"}
```

Đối chiếu CSDL ngay sau lệnh: `LOT-132237 HOLD 1` · `LOT-132237-A1 AVAILABLE 2.5` · `LOT-132237-R1 REJECTED 0.5`, cả ba hạn
`2026-12-17 17:00 UTC`; tổng tồn thực tế vật tư vẫn **4**; 4 dòng sổ kho tách lô (một cặp ra/vào cho mỗi lô con).
`GET /api/v1/inventory/lots?warehouseId=…&itemId=…` (`dc8aa02aec7d46e3`): khả dụng lô gốc 0, `-A1` 2.5, `-R1` 0; hai lô con có `parentLotId`.

### 4.4 QC hàng không theo lô — đạt 20, loại 5 · `X-Trace-Id: 9e6e7812cd764fbd` · **200**

Body `{"expectedVersion": 2, "acceptedQuantity": 20, "rejectedQuantity": 5, "reason": "5 kg sai quy cach"}`
```json
{"code":"SUCCESS","result":{"decision":{"decisionId":"0a7cb083-abd6-4947-b4e4-ba5ff9a666a6",…,"trackingPolicy":"NON_TRACKED","acceptedQuantity":20,"rejectedQuantity":5,"reason":"5 kg sai quy cach",…,"qualityBucketId":"ea2a7b8d-ac18-4079-840d-3c2cc7f6d5ee","childLots":[],"movementIds":[]},"receiptVersion":3,"receivedQuantity":25.000000,"acceptedQuantity":20.000000,"rejectedQuantity":5.000000,"pendingQuantity":0.000000},"message":"OK"}
```

CSDL: tồn thực tế 25, chờ kiểm **5** (trước: 25) ⇒ khả dụng 20. Không dòng sổ kho nào — tồn thực tế không đổi.

### 4.5 Gửi lại

- Cùng key, cùng body ⇒ **200**, cùng `decisionId` `31ac0a40-…` (`c5e2477e248c4c35`)
- Cùng key, đổi `acceptedQuantity` thành `"2.4"` ⇒ **409 `IDEMPOTENCY_CONFLICT`** (`3e58d40b59a045f2`)

### 4.6 Hai quyết định song song trên cùng version

Dòng theo lô còn chờ 1; hai lệnh cùng lúc, mỗi lệnh `{"expectedVersion": 3, "acceptedQuantity": "0.6", "rejectedQuantity": 0}`:
một **200** (`ee62f08885fa4101`), một **409** (`51061404325c465e`):
```json
{"code":"CONCURRENT_MODIFICATION","message":"Goods receipt version is 4, not 3; reload it"}
```
CSDL: đã chấp nhận trên dòng = 3.1 (2.5 + 0.6), chỉ một lần. Lô con của lệnh thắng là `LOT-132237-A2`.

### 4.7 Không đi vòng QC

- `POST /api/v1/inventory/lots/0ca60fc3-…/status` `{"warehouseId": "1ec10140-…", "newStatus": "AVAILABLE", "reason": "bypass QC"}` (lô gốc còn chờ
  QC) · `X-Trace-Id: 97ccc6b2b1c8434f` · **409**
  ```json
  {"code":"LOT_NOT_ELIGIBLE","message":"Lot is on hold from a goods receipt and must be released via purchase QC"}
  ```
- Cùng lệnh trên lô con `LOT-132237-R1` ⇒ **409 `LOT_NOT_ELIGIBLE`** (`78ec645464c34b08`)
- `POST /api/v1/goods-receipts/72192fba-…/cancel` `{"cancelNote": "dao"}` · `X-Trace-Id: 48faad58ac7a4bf0` · **409**
  ```json
  {"code":"STATE_CONFLICT","message":"Goods receipt has purchase QC decisions and cannot be reversed"}
  ```
- Lô hết hạn (hạn bị đặt về hôm qua bằng SQL — API không cho nhập hạn quá khứ): đạt 2 ⇒ **409** (`9c13c2cef8e94ccd`)
  ```json
  {"code":"LOT_NOT_ELIGIBLE","message":"acceptedQuantity: lot EXP-132237 expired at 2026-10-18T06:22:41.578390Z; it can only be rejected","errors":[{"field":"acceptedQuantity","message":"lot EXP-132237 expired at 2026-10-18T06:22:41.578390Z; it can only be rejected"}]}
  ```
  loại 2 có lý do ⇒ **200**, lô con `EXP-132237-R1` `REJECTED` (`cc51ffc8c183473a`)

### 4.8 Lịch sử — `GET /api/v1/goods-receipts/72192fba-…/quality-decisions` · `X-Trace-Id: af930ddcf45c4fe3` · **200**

3 quyết định, mới nhất trước, đều `decidedByUsername: "admin"`. Dòng đầu (quyết định thắng ở §4.6):
```json
{"decisionId":"0bc41035-c3dc-4c4e-9522-848efab57951",…,"acceptedQuantity":0.600000,"rejectedQuantity":0.000000,"reason":null,"decidedBy":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","decidedByUsername":"admin","decidedAt":"2026-10-19T06:22:40.994310Z","qualityBucketId":null,"childLots":[{"lotId":"23ad1d0c-f64c-4576-bfb0-45e130c7a006","lotCode":"LOT-132237-A2","parentLotId":"0ca60fc3-1915-4087-809d-4b51a228fdae","status":"AVAILABLE","quantity":0.600000,"expiresAt":"2026-12-17T17:00:00Z","movementId":"0ac398af-9436-4cbb-8098-6ad5a6934804"}],"movementIds":["0ac398af-9436-4cbb-8098-6ad5a6934804"]}
```

Hàng đợi sau tất cả (`8d45e8183c254bab`): còn **một** dòng — `LOT-132237`, `pendingQuantity: 0.400000`, `goodsReceiptVersion: 4`.

---

## §5 — Envelope và hợp đồng lỗi

Envelope như v1 §5. Bảng bổ sung cho QC mua (không mã lỗi mới nào):

| Tình huống | HTTP | `code` | `errors[]`? |
|---|---|---|---|
| Thiếu field, lượng âm, chữ số lẻ thứ 7, `reason` > 500 | 400 | `VALIDATION_ERROR` | có |
| Đạt + loại đều 0 | 400 | `VALIDATION_ERROR` | có — `acceptedQuantity` |
| Loại > 0 mà không có lý do | 400 | `VALIDATION_ERROR` | có — `reason` |
| Đạt + loại vượt lượng còn chờ (kể cả dòng đã QC xong) | 409 | `PLANNED_QUANTITY_EXCEEDED` | có — `acceptedQuantity` |
| Đạt trên lô đã hết hạn | 409 | `LOT_NOT_ELIGIBLE` | có — `acceptedQuantity` |
| `expectedVersion` lệch version phiếu (kể cả bên thua khi hai người QC cùng lúc) | 409 | `CONCURRENT_MODIFICATION` | không |
| Phiếu chưa post, đã huỷ, hoặc nhận trước khi có vùng chờ kiểm | 409 | `STATE_CONFLICT` | không |
| Đổi trạng thái lô đang chờ QC mua / lô con bị QC loại | 409 | `LOT_NOT_ELIGIBLE` | không |
| Huỷ (đảo nhập) phiếu đã có quyết định QC | 409 | `STATE_CONFLICT` | không |
| Cùng `Idempotency-Key`, payload khác | 409 | `IDEMPOTENCY_CONFLICT` | không |
| `X-Plant-Id` lệch `plantId` (hàng đợi) | 409 | `STATE_CONFLICT` | không |
| Dòng không thuộc phiếu | 404 | `ENTITY_NOT_FOUND` | không |
| Thiếu quyền, hoặc id phiếu không tồn tại | 403 | `PERMISSION_DENIED` | không |

Ánh xạ mã FE đề xuất ⇒ mã thật: `QC_QUANTITY_EXCEEDED` ⇒ `PLANNED_QUANTITY_EXCEEDED` + field; `QC_STATE_INVALID` ⇒ `STATE_CONFLICT`;
`PURCHASE_QC_REQUIRED` ⇒ `LOT_NOT_ELIGIBLE`; `LOT_EXPIRED` ⇒ `LOT_NOT_ELIGIBLE` field `acceptedQuantity`; `REASON_REQUIRED` ⇒ `VALIDATION_ERROR`
field `reason`; `INVALID_QUANTITY` ⇒ `VALIDATION_ERROR` + field; `STALE_VERSION` ⇒ `CONCURRENT_MODIFICATION`.

---

## §6 — Quyền, phạm vi và giới hạn đã biết

| Endpoint | Quyền | Phạm vi |
|---|---|---|
| Hàng đợi | `PERM_PURCHASE_QC_READ` | nhà máy |
| Ghi quyết định | `PERM_PURCHASE_QC_MANAGE` | kho của phiếu |
| Lịch sử | `PERM_PURCHASE_QC_READ` **hoặc** `PERM_GOODS_RECEIPT_READ` **hoặc** `PERM_PURCHASE_ORDER_READ` | kho của phiếu |

Cấp sẵn: `ADMIN`, `MANAGER` có cả hai quyền QC. `OPERATOR` **không**.

**Giới hạn đã biết:**
1. **Hàng không theo lô bị loại nằm chung số "chờ kiểm" trên màn hình tồn** (§1 mục 2). Hàng đợi QC và `qualityBucketId` tách được; số tồn thì chưa.
2. **Điều chỉnh tồn thủ công (`POST /v1/inventory/adjust`) trên lô `HOLD` chưa bị chặn** — có từ trước GĐ7, ngoài phạm vi phần này. Đổi trạng
   thái lô thì đã chặn.
3. **Hàng đã nhận nhưng chờ kiểm không xuất hiện trong MRP** — không còn là lượng mở của PO, chưa khả dụng (xem Q30).
4. **Số trong response vừa ghi lặp lại cách viết của request** (`"acceptedQuantity":2.5`), đọc lại có 6 chữ số lẻ (`2.500000`) — như GĐ2.
5. **Id phiếu không tồn tại trả 403**, không phải 404 (như v1 Q26).
6. **Không huỷ / sửa được một quyết định QC** — bảng quyết định chỉ thêm. Sửa sai bằng một quyết định khác trên phần còn chờ; phần đã quyết thì
   không đổi được qua API (FE §1.2 đặt đảo ngược QC ngoài phạm vi).

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

`mvn -o clean verify` (Postgres thật qua Testcontainers): **1749 case unit + 253 case tích hợp, `failures = 0`, `errors = 0`** (trước phần này:
1721 + 249). Nghiệm thu đột biến (sửa cố ý một chỗ code, test phải đỏ): **17/17 bị bắt**, gồm: bỏ kiểm version, bỏ tăng version phiếu, nới trần
lượng chờ, cho đạt lô hết hạn, gỡ chờ kiểm cả phần loại, lô con loại thành `AVAILABLE`, bỏ đọc lại phiếu dưới khoá, bỏ hai cổng chặn đổi trạng
thái lô, cho đảo nhập phiếu đã QC, hàng đợi giữ dòng đã xong, tách lô lấy cả phần đã giữ chỗ, sai tên quyền, và lỗi `parentLotId` ở §1 mục 3.
Hai trong số đó **lần đầu không bị bắt** và đã được bổ sung test trước khi gửi bản này.

### 7.2 Kiểm chứng qua HTTP thật

Môi trường: backend dev, Postgres + Redis thật, cổng 8090, 19/10/2026. **Hai lượt**: lượt đầu tìm ra lỗi `parentLotId` (§1 mục 3), lượt cuối
chạy trên code cuối cùng — công ty `GD7C-132237`, **23 lượt gọi kiểm tra, 0 lượt sai kỳ vọng**. Mỗi bước có `X-Trace-Id` ở §4.

### 7.3 Chưa nghiệm thu

| Mục | Vì sao | Cần gì |
|---|---|---|
| Tài khoản chỉ có `PERM_PURCHASE_QC_*` (không quyền phiếu nhận/PO) gọi live | Đã kiểm bằng test phân quyền tự động; chưa dựng tài khoản như vậy | tài khoản thử với đúng tập quyền |
| Tìm kiếm hàng đợi theo NCC/vật tư/khoảng ngày trên dữ liệu lớn | Chỉ đo tìm theo mã lô live | dữ liệu nhiều phiếu |
| Script dữ liệu demo (`D26-…`) chạy lại sau GĐ7 | Chưa chạy lại; hàng mua của script sẽ nằm ở chờ kiểm cho tới khi QC | chạy lại script + bổ sung bước QC |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm:**

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Ô "đã loại" riêng cho hàng không theo lô trong số tồn | Cần thêm một cột và đổi mọi tổng tồn — rộng hơn phạm vi QC | quyết định riêng |
| Chặn điều chỉnh tồn thủ công trên lô `HOLD` | Đường có từ trước GĐ7, đổi nó ảnh hưởng nghiệp vụ kho ngoài mua hàng | quyết định riêng |
| Huỷ / đảo ngược quyết định QC | FE §1.2 ngoài phạm vi | phase riêng |
| R19 giá trị tồn | Hoãn như v1 | phase riêng |

**Việc FE cần làm tiếp:**

Bắt buộc:
- [ ] Màn hình QC đọc `GET /api/v1/purchase-qc`, gửi `goodsReceiptVersion` làm `expectedVersion`, sau mỗi quyết định dùng `receiptVersion` trả về
- [ ] Hiển thị `errors[].field` cho `acceptedQuantity` / `reason`; xử lý `409 CONCURRENT_MODIFICATION` bằng tải lại hàng đợi, **không** tự thử lại
- [ ] Xử lý `409 LOT_NOT_ELIGIBLE` ở màn hình đổi trạng thái lô (dẫn sang QC) và ở QC (lô hết hạn chỉ loại được)
- [ ] Ẩn / chặn huỷ phiếu nhận đã có quyết định QC (`409 STATE_CONFLICT`)

Nên làm:
- [ ] Gửi `Idempotency-Key` (UUID mới mỗi lần bấm, giữ nguyên khi retry) trên lệnh QC
- [ ] Dùng `childLots[]` + `parentLotId` để vẽ lineage lô
- [ ] Chạy A13–A19, A20 (phần đổi trạng thái lô; điều chỉnh tồn thủ công xem §6 mục 2), A23, A25–A27 (phần QC), A29, A30, A35 (hàng đợi),
      A37, A38 trên dev và báo kết quả cùng phần phiếu nhận (v2 §8). Phần "value" của A13/A14 không áp dụng (giá trị tồn hoãn); "rejected5" của
      A14 với hàng không theo lô đọc ở hàng đợi/`rejectedQuantity` của dòng phiếu, không ở số tồn (§6 mục 1)

# BE trả lời — Giai đoạn 7: Đơn mua hàng, nhận hàng và QC

**Trả lời cho:** `FE_HANDOFF_GD_07_2026-10-14.md` (v1, P1) · **Ngày gửi:** 18/10/2026 · **Bản:** v2 — **phần phiếu nhận hàng (`G7b`)**, bổ sung bản v1
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. Bản v1 (đơn mua hàng) vẫn đúng nguyên văn; bản này chỉ thêm phần 2. Phần 3
(QC mua hàng, lô con) sẽ đến trong `_v3` — **chưa có trong bản này**, nên hàng nhận về **chưa dùng được** cho tới khi có QC. Chưa phải "FE nghiệm
thu đạt": task chỉ đóng khi FE chạy A01–A38 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Phiếu nhận hàng nay có nháp do server cấp mã (sửa được, không đụng tồn kho), post kiểm lại lượng còn mở dưới khoá đơn mua hàng, và mọi lần nhận
mua — kể cả đường cũ — đi vào vùng chờ kiểm (lô mới `HOLD`, hàng không theo lô vào `qualityHoldQuantity`), nên tồn khả dụng không tăng cho tới QC.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R06 | Phiếu nhận nháp sửa được, không đụng tồn kho, id dòng ổn định | 🟡 **Chưa có, đã làm** | `POST /v1/goods-receipts` ⇒ `DRAFT`, 0 dòng tồn kho; `PATCH` giữ id dòng (§4.2, §4.3) |
| R07 | Post nhận từng phần, nhiều dòng cùng dòng PO để tách lô, tổng không vượt lượng mở | 🟡 **Chưa có, đã làm** | 20 + 5 trên cùng dòng PO được nhận; 30 + 11 > 40 ⇒ `409` chỉ đúng `lines[1]` (§4.2) |
| R08 | Hai người post cùng lúc trên cùng lượng mở: một thắng, một bị từ chối rõ ràng | 🟡 **Chưa có, đã làm** | Hai lệnh post song song 10 + 10 trên lượng mở 14 ⇒ `200` + `409 PLANNED_QUANTITY_EXCEEDED`, đã nhận đúng 36 (§4.5) |
| R09 | Nhận vào vùng chờ kiểm, chưa dùng được trước QC | 🟡 **Chưa có, đã làm** | Sau post: tồn 26 / chờ kiểm 26 / khả dụng 0; lô `HOLD` (§4.4) |
| R10 | Danh sách phiếu nhận toàn nhà máy, tìm/lọc/khoảng ngày | 🟡 **Chưa có, đã làm** | `GET /v1/goods-receipts` (§4.6) |
| R11 | Danh sách PO đang nhận được (ứng viên) với lượng còn mở | 🟡 **Chưa có, đã làm** | `GET /v1/goods-receipts/purchase-order-candidates` (§4.1) |
| R12 | Huỷ phiếu nháp không đụng tồn; phiếu đã post bất biến | 🟡 **Huỷ nháp đã làm; phiếu đã post: giữ đường đảo nhập cũ** | Theo quyết định D3 của PO: `POSTED` vẫn huỷ được bằng đảo nhập (`REVERSAL`), chỉ với quyền mua hàng (§4.7, §3) |
| R17 (phần phiếu nhận) | Gửi lại cùng key trả cùng kết quả; version cũ không ghi gì | 🟡 **Đã làm** | Gửi lại cùng key ⇒ cùng phiếu `GR-000010`; `expectedVersion` cũ ⇒ `409 CONCURRENT_MODIFICATION` (§4.2, §4.3) |
| R13, R16, R18, R20 (QC, lô con, chặn đi vòng QC) | ⏳ **Phần 3 — chưa có trong bản này** | `_v3` |
| R19 (giá trị tồn) | ⏳ **Hoãn có chủ đích** (như v1) | v1 §8 |

### Trả lời Q (những câu phần 2 trả lời được)

| Q | Trả lời |
|---|---|
| Q1 | **Có.** Phần 2 tạo hai quyền với **đúng tên FE đề xuất**: `PERM_GOODS_RECEIPT_READ`, `PERM_GOODS_RECEIPT_MANAGE`, cấp sẵn cho `ADMIN` + `MANAGER`. Post dùng `PERM_GOODS_RECEIPT_POST` (đã có từ trước, cùng hai vai trò đó) |
| Q8 | **Có.** Post đưa thẳng vào vùng chờ kiểm, không có bước duyệt riêng — quyền post (`PERM_GOODS_RECEIPT_POST`) tách khỏi quyền lập nháp |
| Q9 | **Có.** Nháp **không** giữ chỗ lượng mở. Lúc lưu nháp server vẫn kiểm sớm (để báo lỗi ngay), nhưng lúc post kiểm lại **tất cả** dưới khoá đơn mua hàng — hai nháp cùng lượng mở thì nháp post sau bị từ chối |
| Q10 | **Có.** Nhiều dòng phiếu trỏ cùng một dòng PO được; trần là **tổng** các dòng so với lượng còn mở của dòng PO. Lỗi trỏ đúng dòng làm vượt (`lines[i].receivedQuantity`) |
| Q11 | **Có (nay áp dụng được).** PO còn phiếu nhận `DRAFT` ⇒ huỷ hoặc đóng PO trả `409 PENDING_GOODS_RECEIPT`, không đổi gì (§4.8) |
| Q14 | **Có một phần.** Chi tiết phiếu mở được bằng `PERM_GOODS_RECEIPT_READ` **hoặc** `PERM_PURCHASE_ORDER_READ`; danh sách toàn nhà máy và ứng viên PO cần `PERM_GOODS_RECEIPT_READ`. Ứng viên trả sẵn thông tin NCC/kho/vật tư cần cho form, không phải gọi danh mục. Chi tiết PO **chưa** nhúng danh sách phiếu nhận — FE lọc `GET /v1/goods-receipts?purchaseOrderId=…` |
| Q15 | **Có.** Ứng viên và dòng phiếu nhận trả `unitPrice` + `currencyCode` của dòng PO cho người có `PERM_GOODS_RECEIPT_READ` |
| Q17 | **Có.** `expiresOn` tuỳ chọn cho vật tư theo lô (hệ thống chưa có cờ "bắt buộc hạn dùng" trên vật tư). Nếu gửi thì phải **sau hôm nay** theo múi giờ nhà máy. Vật tư không theo lô gửi `lotCode`/`expiresOn` ⇒ `400` đúng field |
| Q20 | **Có.** "Hôm nay" (chặn `receivedDate` tương lai, chặn hạn dùng quá khứ) do server tính theo múi giờ của **nhà máy** của PO, không theo trình duyệt |
| Q25 | **Không như đề xuất:** server chỉ đòi **quyền hành động** (MANAGE / POST), không đòi kèm READ. FE vẫn có thể đòi cả hai ở UI; server không chặn thêm |
| Q28 | **Có.** Mã lô mới phải **chưa tồn tại** cho vật tư đó, so **không phân biệt hoa thường** (`lot-…` trùng `LOT-…` ⇒ `409 LOT_CODE_ALREADY_EXISTS`, §4.9). Đường nháp **không** cho nhận vào lô có sẵn. Giới hạn: kiểm này nằm ở tầng ứng dụng, xem §6 |
| Q29 | **Có.** `expiresOn` = ngày bắt đầu hết hiệu lực lúc **00:00 giờ nhà máy**: `2026-12-17` ở `Asia/Ho_Chi_Minh` ⇒ lô hết hạn lúc `2026-12-16T17:00:00Z` (§4.4) |

Các Q còn lại thuộc QC/lô con/giá trị tồn (Q16, Q18, Q19, Q27, Q30) — trả lời ở `_v3`.

---

## §1 — Nguyên nhân thật

Không có lỗi FE báo — đây là phần **chưa có**. Ba điều đáng nói, theo thứ tự ảnh hưởng tới FE:

1. **Mọi lần nhận mua nay vào vùng chờ kiểm — kể cả đường cũ `POST /v1/purchase-orders/{id}/goods-receipts`.** Trước đây hàng nhận về dùng được
   ngay. Nay lô mới tạo ở `HOLD`; hàng không theo lô cộng vào `qualityHoldQuantity`. Tồn khả dụng, số hoạch định và dashboard **không tăng** cho
   tới khi có QC (phần 3). Một quy tắc cho cả hai đường, không có hai con số.
2. **Trong lúc nghiệm thu qua HTTP thật tìm ra một lỗi mà test tự động không thấy:** hai lệnh post song song trên cùng lượng mở — bên thua nhận
   `409 CONCURRENT_MODIFICATION` ("tải lại rồi thử lại") thay vì `409 PLANNED_QUANTITY_EXCEEDED` ("không còn đủ lượng mở"). Số tồn vẫn đúng (chỉ
   một bên được ghi), nhưng mã sai thì FE hướng người dùng thử lại một việc không bao giờ thành công. Đã sửa; nay bên thua nhận đúng mã kèm dòng.
3. **Cũng tìm ra khi nghiệm thu:** huỷ phiếu đã post (đảo nhập) trả `version` **cũ hơn** giá trị thật vừa lưu — dùng nó làm `expectedVersion` lần
   sau sẽ luôn bị `409` giả. Đã sửa (đo lại: huỷ ⇒ `version` 2 → 3).

---

## §2 — Thay đổi contract

### 2.1 Endpoint

| Method | Path | Mới / đổi | Quyền |
|---|---|---|---|
| GET | `/api/v1/goods-receipts/purchase-order-candidates?companyId&plantId&search?&page&size` | **Mới** | `PERM_GOODS_RECEIPT_READ` (nhà máy) |
| POST | `/api/v1/goods-receipts` | **Mới** — tạo nháp. Header `Idempotency-Key` tuỳ chọn | `PERM_GOODS_RECEIPT_MANAGE` |
| PATCH | `/api/v1/goods-receipts/{id}` | **Mới** — sửa nháp, thay toàn bộ dòng | `PERM_GOODS_RECEIPT_MANAGE` |
| POST | `/api/v1/goods-receipts/{id}/post` | **Mới** — body `{expectedVersion}`, `Idempotency-Key` tuỳ chọn | `PERM_GOODS_RECEIPT_POST` |
| POST | `/api/v1/goods-receipts/{id}/cancel` | **Đổi** — body tuỳ chọn `{cancelNote?, expectedVersion?}`; `DRAFT` ⇒ huỷ không đụng tồn, `POSTED` ⇒ đảo nhập như trước | nháp: `PERM_GOODS_RECEIPT_MANAGE` **hoặc** `PERM_PURCHASE_ORDER_MANAGE`; đã post: **chỉ** `PERM_PURCHASE_ORDER_MANAGE` |
| GET | `/api/v1/goods-receipts?companyId&plantId&purchaseOrderId?&warehouseId?&status?&search?&receivedDateFrom?&receivedDateTo?&page&size&sortBy&sortDir` | **Mới** — danh sách toàn nhà máy. `X-Plant-Id` phải khớp `plantId` | `PERM_GOODS_RECEIPT_READ` |
| GET | `/api/v1/goods-receipts/{id}` | **Đổi quyền** — nay mở bằng `GOODS_RECEIPT_READ` **hoặc** `PURCHASE_ORDER_READ` | |
| POST | `/api/v1/purchase-orders/{id}/goods-receipts` | Giữ nguyên request; **hàng nay vào vùng chờ kiểm** (§3) | `PERM_PURCHASE_ORDER_MANAGE` (như trước) |

Request tạo nháp: `{purchaseOrderId, receivedDate, reference?, notes?, lines[1..500]{purchaseOrderLineId, receivedQuantity, lotCode?, expiresOn?}}`.
Sửa nháp: thêm `expectedVersion` bắt buộc, và `lines[i].id` để giữ dòng cũ (không có `id` = dòng mới, vắng = xoá).
`receivedQuantity` > 0, tối đa 13 chữ số nguyên + 6 chữ số lẻ. `reference` ≤ 200, `notes` ≤ 2000, `lotCode` ≤ 120.

### 2.2 Field mới trên phiếu nhận — additive, field cũ giữ nguyên

`code` (= `receiptNo`), `version`, `receivedDate`, `reference`, `companyId`, `plantId`, `purchaseOrderCode`, `supplierId`, `supplierCode`,
`supplierName`, `warehouseName`, `postedBy`, `cancelledBy`, `purchaseOrderStatus`, `purchaseOrderVersion`. `status` thêm giá trị **`DRAFT`**.
`postedAt` = `null` khi nháp. `notes` của request lưu ở field `note` cũ (chuỗi rỗng ⇒ `null`).

### 2.3 Field mới trên dòng phiếu nhận

`trackingPolicy` (`NON_TRACKED` / `LOT_TRACKED`), `uomCode`, `unitPrice`, `currencyCode` (của dòng PO), `expiresOn`, `lotStatus` (sau post),
`movementIds[]` (sau post). `lotId`/`stockMovementId` = `null` khi nháp.

### 2.4 Ứng viên PO

PO `CONFIRMED`/`PARTIALLY_RECEIVED` còn ít nhất một dòng mở; **chỉ** trả dòng còn mở. Mỗi dòng: `orderedQuantity`, `receivedQuantity`,
`closedQuantity`, `openQuantity`, `trackingPolicy`, `uomCode`, `unitPrice`, `currencyCode`, `expectedDate`.

---

## §3 — Breaking change

**Wire: CÓ.**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /v1/purchase-orders/{id}/goods-receipts` (đường cũ) | mọi lần nhận | tồn **khả dụng** tăng ngay; lô mới `AVAILABLE` | lô mới `HOLD`; hàng không theo lô vào `qualityHoldQuantity`; khả dụng **không** tăng | **Đổi số** | không coi hàng vừa nhận là dùng được; chờ QC (phần 3) |
| Như trên | nêu một lô **có sẵn** đang `AVAILABLE` | `201` (cộng vào lô) | `409 LOT_NOT_ELIGIBLE` | **Bắt buộc mới** | dùng mã lô mới, hoặc dùng đường nháp |
| Mọi số tồn khả dụng (tồn kho, hoạch định, dashboard) | sau mọi lần nhận mua mới | tăng ngay | tăng sau QC | **Đổi số** | — |
| `POST /v1/purchase-orders/{id}/cancel`, `/close` | PO còn phiếu nhận `DRAFT` | `200` | `409 PENDING_GOODS_RECEIPT` | **Bắt buộc mới** | post hoặc huỷ phiếu nháp trước |
| `POST /v1/goods-receipts/{id}/cancel` | phiếu đã post, người dùng **không** có `PERM_PURCHASE_ORDER_MANAGE` | theo quyền cũ | `403 PERMISSION_DENIED` | **Bắt buộc mới** (quyền) | không — `ADMIN`/`MANAGER` có sẵn |
| `GET` phiếu nhận | phiếu nháp | — | `postedAt: null`, `status: "DRAFT"` | **Giá trị mới** | xử lý `null` + trạng thái mới |
| `POST /v1/purchase-orders/{id}/goods-receipts` | `receivedQuantity` có 7 chữ số lẻ | được nhận | `400 VALIDATION_ERROR` field `lines[i].receivedQuantity` | **Bắt buộc mới** | tối đa 6 chữ số lẻ |
| Huỷ phiếu đã post, post đường cũ | `version` trong response | cũ hơn giá trị thật | đúng giá trị vừa lưu | **Đổi số** (sửa sai) | — |

Không bỏ field nào. Không đổi giá trị mặc định của query param nào.

**Thứ tự deploy:** BE trước. Màn hình đang đọc tồn khả dụng ngay sau khi nhận hàng sẽ thấy hàng ở "chờ kiểm" thay vì "khả dụng" — đúng ý D4.

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Môi trường dev, cổng 8090, 18/10/2026 khoảng 23:32 (giờ VN), fixture công ty `GD7B-233219`. Response dài được rút gọn bằng `…` (chỉ cắt, không
viết lại); bản đầy đủ có trong log server theo `X-Trace-Id`.

### 4.1 Ứng viên — `GET /api/v1/goods-receipts/purchase-order-candidates?companyId=…&plantId=…` · `X-Trace-Id: 066448823fcc43fb` · **200**

```json
{"code":"SUCCESS","result":{"content":[{"purchaseOrderId":"bf8e154f-d010-4b6f-a800-fd93b82b72ad","purchaseOrderCode":"PO-000013","version":1,"status":"CONFIRMED","supplierId":"17b39ae7-58f2-4573-a481-b562854d1316","supplierCode":"SUP-233219","supplierName":"NCC Kim loai","warehouseId":"31e6cba1-816b-466e-892f-5efd25579a7c","warehouseCode":"RM-233219","warehouseName":"Kho nguyen lieu","orderDate":"2026-10-18","reference":"TEST-GR","lines":[{"purchaseOrderLineId":"04868ffd-d74d-4146-8816-3a258c364df3","itemId":"70f1ce00-d02d-4c3f-9566-0e4e1737f640","itemCode":"RM1-233219","itemName":"RM1","uomCode":"K233219","trackingPolicy":"NON_TRACKED","orderedQuantity":40.000000,"receivedQuantity":0.000000,"closedQuantity":0.000000,"openQuantity":40.000000,"expectedDate":"2026-10-18","unitPrice":10.000000,"currencyCode":"VND"},{"purchaseOrderLineId":"cbc0a8b2-d780-49a8-9844-30564a704cf5","itemId":"bfa25b88-351d-4323-bc9b-384456fdb3c8","itemCode":"RML-233219","itemName":"RML","uomCode":"K233219","trackingPolicy":"LOT_TRACKED","orderedQuantity":10.000000,"receivedQuantity":0.000000,"closedQuantity":0.000000,"openQuantity":10.000000,"expectedDate":"2026-10-18","unitPrice":125.000000,"currencyCode":"VND"}]}],"page":0,"size":20,"totalElements":1,"totalPages":1,"first":true,"last":true},"message":"OK"}
```

### 4.2 Tạo nháp — `POST /api/v1/goods-receipts` · header `Idempotency-Key: g7b-7523904fa8ae4e1f` · `X-Trace-Id: f41b9cccdf36461f` · **201**

Request (hai dòng trên **cùng** dòng PO để tách, một dòng theo lô):

```json
{"purchaseOrderId": "bf8e154f-d010-4b6f-a800-fd93b82b72ad", "receivedDate": "2026-10-18", "reference": "DEL-1", "notes": "", "lines": [{"purchaseOrderLineId": "04868ffd-d74d-4146-8816-3a258c364df3", "receivedQuantity": "20"}, {"purchaseOrderLineId": "04868ffd-d74d-4146-8816-3a258c364df3", "receivedQuantity": "5"}, {"purchaseOrderLineId": "cbc0a8b2-d780-49a8-9844-30564a704cf5", "receivedQuantity": "4", "lotCode": "LOT-233219", "expiresOn": "2026-12-17"}]}
```

Response (rút gọn):

```json
{"code":"SUCCESS","result":{"goodsReceiptId":"81cce5bb-0aee-4b9b-b702-2618f8d43dee","purchaseOrderId":"bf8e154f-d010-4b6f-a800-fd93b82b72ad","warehouseId":"31e6cba1-816b-466e-892f-5efd25579a7c","warehouseCode":"RM-233219","receiptNo":"GR-000010","status":"DRAFT","postedAt":null,"idempotencyKey":null,"note":null,"cancelledAt":null,"cancelNote":null,"createdAt":"2026-10-18T16:32:21.496982900Z","updatedAt":"2026-10-18T16:32:21.496982900Z","lines":[{"goodsReceiptLineId":"af80aabd-20ef-459d-bb88-082a9a5f65dd","purchaseOrderLineId":"04868ffd-d74d-4146-8816-3a258c364df3","itemId":"70f1ce00-d02d-4c3f-9566-0e4e1737f640","itemCode":"RM1-233219","itemName":"RM1","lotId":null,"lotCode":null,"receivedQuantity":20,"stockMovementId":null,"trackingPolicy":"NON_TRACKED","uomCode":"K233219","unitPrice":10.000000,"currencyCode":"VND","expiresOn":null,"lotStatus":null,"movementIds":[]},…],…}
```

Ngay sau đó trong DB: **0 dòng tồn kho** cho hai vật tư. Gửi lại **đúng** request với **cùng** key (`X-Trace-Id: d5d7adfd56034be1`) ⇒ **201**, cùng
`goodsReceiptId` `81cce5bb-…`, cùng `GR-000010`, không tạo phiếu thứ hai.

Nhánh lỗi khi tạo nháp:

| Request (rút gọn) | HTTP | Response | `X-Trace-Id` |
|---|---|---|---|
| 30 + 11 trên dòng PO còn mở 40 | 409 | `{"code":"PLANNED_QUANTITY_EXCEEDED","message":"lines[1].receivedQuantity: exceeds what is still open on the purchase order line: 40","errors":[{"field":"lines[1].receivedQuantity","message":"exceeds what is still open on the purchase order line: 40"}]}` | `eb3e104a60184b52` |
| vật tư theo lô, không `lotCode` | 400 | `{"code":"VALIDATION_ERROR","message":"lines[0].lotCode: is required for a lot-tracked item","errors":[{"field":"lines[0].lotCode","message":"is required for a lot-tracked item"}]}` | `4792096167014277` |
| vật tư không theo lô, gửi `"lotCode": "X"` | 400 | `{"code":"VALIDATION_ERROR","message":"lines[0].lotCode: item is not lot-tracked; omit lotCode and expiresOn","errors":[{"field":"lines[0].lotCode","message":"item is not lot-tracked; omit lotCode and expiresOn"}]}` | `181f1d64c9d44b90` |
| `"receivedDate": "2026-10-19"` (ngày mai) | 400 | `{"code":"VALIDATION_ERROR","message":"receivedDate: must not be after today","errors":[{"field":"receivedDate","message":"must not be after today"}]}` | `df62824b7af44cfc` |

### 4.3 Sửa nháp — `PATCH /api/v1/goods-receipts/81cce5bb-…`

`expectedVersion` sai (`9`, thật là `0`) · `X-Trace-Id: be08e3ad9b6a4c5a` · **409**:

```json
{"code":"CONCURRENT_MODIFICATION","message":"Goods receipt version is 0, not 9; reload it"}
```

Gửi lại với `expectedVersion: 0`, ba dòng kèm `id` cũ, dòng thứ hai đổi 5 → 6 · `X-Trace-Id: ee0613feb66a4591` · **200**: `version` 0 → 1, cả ba
`goodsReceiptLineId` giữ nguyên.

### 4.4 Post — `POST /api/v1/goods-receipts/81cce5bb-…/post` · `X-Trace-Id: a4b63276eaa8430a` · **200**

Request `{"expectedVersion": 1}` (với `0` ⇒ `409 CONCURRENT_MODIFICATION` "Goods receipt version is 1, not 0; reload it", `X-Trace-Id:
111f9cf6d5584084`). Response (rút gọn):

```json
{"code":"SUCCESS","result":{"goodsReceiptId":"81cce5bb-0aee-4b9b-b702-2618f8d43dee",…,"receiptNo":"GR-000010","status":"POSTED","postedAt":"2026-10-18T16:32:22.158075Z",…,"lines":[{"goodsReceiptLineId":"af80aabd-20ef-459d-bb88-082a9a5f65dd",…,"receivedQuantity":20.000000,"stockMovementId":"c10996ce-5ce2-4811-909a-09d437a6afb5","trackingPolicy":"NON_TRACKED",…,"movementIds":["c10996ce-5ce2-4811-909a-09d437a6afb5"]},…],…}
```

Dòng theo lô sau post: `lotCode` `LOT-233219`, `lotStatus` `HOLD`. PO chuyển `PARTIALLY_RECEIVED`. Tồn kho ngay sau đó —
`GET /api/v1/inventory/balances?warehouseId=…` · `X-Trace-Id: cfc000a2a45640dd`:

```json
{"code":"SUCCESS","result":{"content":[{"balanceId":"15491259-6542-441d-8a43-4bb7fb2f1ce3","itemId":"bfa25b88-351d-4323-bc9b-384456fdb3c8","warehouseId":"31e6cba1-816b-466e-892f-5efd25579a7c","lotId":"2b65c9ba-3a7a-4f4d-b346-fb246bff5111","lotCode":"LOT-233219","quantity":4.000000,"reservedQuantity":0.000000,"qualityHoldQuantity":0.000000,"availableQuantity":0,"updatedAt":"2026-10-18T16:32:22.227476Z","version":0},{"balanceId":"51fde0a1-fcc9-45e6-9745-703cfdcf275f","itemId":"70f1ce00-d02d-4c3f-9566-0e4e1737f640","warehouseId":"31e6cba1-816b-466e-892f-5efd25579a7c","lotId":null,"lotCode":null,"quantity":26.000000,"reservedQuantity":0.000000,"qualityHoldQuantity":26.000000,"availableQuantity":0.000000,"updatedAt":"2026-10-18T16:32:22.203083Z","version":1}],"page":0,"size":20,"totalElements":2,"totalPages":1,"first":true,"last":true},"message":"OK"}
```

Hạn dùng của lô trong DB: `2026-12-16 17:00:00+00` (= 00:00 ngày 17/12 giờ VN, Q29). Đọc lại phiếu (`X-Trace-Id: dc555cc01abc4b70`): `postedAt`
giống hệt. Post lần nữa (`{"expectedVersion": 2}`, `X-Trace-Id: c079c1cb6a2c422c`) ⇒ **409**
`{"code":"STATE_CONFLICT","message":"Only DRAFT goods receipts can be posted"}`.

### 4.5 Hai lệnh post song song

Lượng mở còn lại của dòng PO là 14 (40 − 26). Hai nháp 10 + 10 được post **cùng lúc** từ hai luồng:

| Lệnh | HTTP | Response | `X-Trace-Id` |
|---|---|---|---|
| post nháp `47df779e-…` | 200 | `POSTED`, `version` 1 | `5713431cbf764a61` |
| post nháp `28141c99-…` | 409 | `{"code":"PLANNED_QUANTITY_EXCEEDED","message":"lines[0].receivedQuantity: exceeds what is still open on the purchase order line: 4","errors":[{"field":"lines[0].receivedQuantity","message":"exceeds what is still open on the purchase order line: 4"}]}` | `a30f414176634449` |

Lượng đã nhận của dòng PO sau đó: **36** — chỉ một bên được ghi. Bên nào thắng là ngẫu nhiên.

### 4.6 Danh sách — `GET /api/v1/goods-receipts?companyId=…&plantId=…&status=POSTED&search=po-000013` · `X-Trace-Id: a7e38b773ea54bf0` · **200**

`totalElements: 2`. `search` khớp mã phiếu, mã PO, mã/tên NCC, không phân biệt hoa thường.

### 4.7 Huỷ

Huỷ nháp thua ở §4.5 — `POST /api/v1/goods-receipts/28141c99-…/cancel`, body `{"cancelNote": "trung phieu", "expectedVersion": 0}` ·
`X-Trace-Id: 28ffa6fc3d1d44ce` · **200**: `status` `CANCELLED`, `version` 1, `postedAt: null`, `movementIds: []` — không dòng tồn kho nào.

Huỷ phiếu **đã post** — `POST /api/v1/goods-receipts/81cce5bb-…/cancel`, body `{"cancelNote": "dao phieu"}` · `X-Trace-Id: 386c85a621074634` ·
**200**: `status` `CANCELLED`, `version` 2 → **3**. Trước đó vật tư không theo lô có tồn 38 / chờ kiểm 38 (thêm 2 từ §4.10); sau huỷ **12 / 12** — chờ
kiểm được gỡ cùng lượng rồi mới đảo nhập. PO về `PARTIALLY_RECEIVED`.

### 4.8 Huỷ / đóng PO còn phiếu nháp

`POST /api/v1/purchase-orders/bf8e154f-…/cancel` `{"expectedVersion": 1, "reason": "x"}` · `X-Trace-Id: 2e0c8da176bb4827` · **409**;
`…/close` cùng body · `X-Trace-Id: 2d3458339f6d4130` · **409**:

```json
{"code":"PENDING_GOODS_RECEIPT","message":"Purchase order PO-000013 still has a draft goods receipt; post or cancel it first"}
```

### 4.9 Mã lô đã có, khác hoa thường — `X-Trace-Id: f3d7cd2eac964fb4` · **409**

Request dòng `{"purchaseOrderLineId": "cbc0a8b2-…", "receivedQuantity": "1", "lotCode": "lot-233219"}` (lô `LOT-233219` đã có):

```json
{"code":"LOT_CODE_ALREADY_EXISTS","message":"lines[0].lotCode: already exists for this item","errors":[{"field":"lines[0].lotCode","message":"already exists for this item"}]}
```

### 4.10 Đường cũ cũng vào vùng chờ kiểm — `POST /api/v1/purchase-orders/bf8e154f-…/goods-receipts` · `X-Trace-Id: 7c7eb92a881e489c` · **201**

Request `{"receiptNo": "GR-OLD-233219", "lines": [{"purchaseOrderLineId": "04868ffd-…", "receivedQuantity": "2"}]}` kèm `Idempotency-Key`. Response:
`status` `POSTED`, `version` 1, `code` `GR-OLD-233219`. `qualityHoldQuantity` của vật tư: **36 → 38**.

---

## §5 — Envelope và hợp đồng lỗi

Envelope như v1 §5. Bảng bổ sung cho phiếu nhận:

| Tình huống | HTTP | `code` | `errors[]`? |
|---|---|---|---|
| Thiếu field, quá 500 dòng, chữ số lẻ thứ 7, `receivedDate` tương lai, `expiresOn` không sau hôm nay, thiếu/thừa `lotCode` theo loại vật tư, trùng mã lô trong cùng phiếu, thiếu `expectedVersion` (sửa/post) | 400 | `VALIDATION_ERROR` | có |
| Tổng các dòng vượt lượng còn mở của dòng PO (lúc lưu nháp hoặc lúc post, kể cả bên thua khi post đồng thời) | 409 | `PLANNED_QUANTITY_EXCEEDED` | có — `lines[i].receivedQuantity` |
| Mã lô đã tồn tại cho vật tư (không phân biệt hoa thường) | 409 | `LOT_CODE_ALREADY_EXISTS` | có — `lines[i].lotCode` |
| Đường cũ nêu một lô có sẵn không ở `HOLD` | 409 | `LOT_NOT_ELIGIBLE` | không |
| Tạo nháp cho PO không `CONFIRMED`/`PARTIALLY_RECEIVED`; sửa/post phiếu không `DRAFT`; huỷ phiếu đã huỷ; đảo phiếu của PO `CLOSED` | 409 | `STATE_CONFLICT` | không |
| Huỷ/đóng PO còn phiếu nháp | 409 | **`PENDING_GOODS_RECEIPT`** (mới) | không |
| `expectedVersion` lệch | 409 | `CONCURRENT_MODIFICATION` | không |
| Cùng `Idempotency-Key`, payload khác | 409 | `IDEMPOTENCY_CONFLICT` | không |
| Vật tư theo số sê-ri | 400 | `VALIDATION_ERROR` | có — `lines[i].purchaseOrderLineId` |
| `X-Plant-Id` lệch `plantId` (danh sách) | 409 | `STATE_CONFLICT` | không |
| Thiếu quyền, hoặc id phiếu/PO không tồn tại | 403 | `PERMISSION_DENIED` | không |

Ánh xạ mã FE đề xuất ⇒ mã thật: `PURCHASE_ORDER_OVER_RECEIPT` ⇒ `PLANNED_QUANTITY_EXCEEDED` + field; `PENDING_GOODS_RECEIPT` ⇒ **đúng tên**;
`LOT_CODE_CONFLICT` ⇒ `LOT_CODE_ALREADY_EXISTS`; `LOT_REQUIRED` ⇒ `VALIDATION_ERROR` field `lines[i].lotCode`; `INVALID_EXPIRY` ⇒
`VALIDATION_ERROR` field `lines[i].expiresOn`; `RECEIPT_NOT_DRAFT` ⇒ `STATE_CONFLICT`. `QC_*`, `PURCHASE_QC_REQUIRED` thuộc phần 3.

---

## §6 — Quyền, phạm vi và giới hạn đã biết

| Endpoint | Quyền | Phạm vi |
|---|---|---|
| Ứng viên, danh sách toàn nhà máy | `PERM_GOODS_RECEIPT_READ` | nhà máy |
| `GET /v1/goods-receipts/{id}` | `PERM_GOODS_RECEIPT_READ` **hoặc** `PERM_PURCHASE_ORDER_READ` | kho của phiếu |
| Tạo / sửa nháp | `PERM_GOODS_RECEIPT_MANAGE` | kho của PO / phiếu |
| Post | `PERM_GOODS_RECEIPT_POST` | kho của phiếu |
| Huỷ nháp | `PERM_GOODS_RECEIPT_MANAGE` **hoặc** `PERM_PURCHASE_ORDER_MANAGE` | kho của phiếu |
| Huỷ phiếu đã post (đảo nhập) | **chỉ** `PERM_PURCHASE_ORDER_MANAGE` | kho của phiếu |

Cấp sẵn: `ADMIN`, `MANAGER` có cả bốn quyền phiếu nhận. `OPERATOR` **chưa** được cấp quyền phiếu nhận nào.

**Giới hạn đã biết:**
1. **Id phiếu/PO không tồn tại trả 403**, không phải 404 (như v1 Q26).
2. **`createdAt`/`updatedAt` ngay sau khi ghi có 7–9 chữ số lẻ**, đọc lại 6 chữ số (§4.2: `…496982900Z` lúc tạo, `…496983Z` khi đọc lại) — như v1
   §6 mục 2, so sánh theo thời gian, đừng so chuỗi. `postedAt`/`cancelledAt` đã cắt sẵn về micro-giây nên trùng khít.
3. **Số trong response vừa tạo lặp lại cách viết của request** (`"receivedQuantity":20`), đọc lại có 6 chữ số lẻ (`20.000000`) — như GĐ2.
4. **Trùng mã lô không phân biệt hoa thường được kiểm ở tầng ứng dụng** dưới khoá của **một** PO. Hai PO khác nhau cùng vật tư post **đồng thời**
   hai mã chỉ khác hoa thường thì hiếm khi cả hai lọt qua (CSDL chỉ chặn trùng **đúng** từng ký tự).
5. **`idempotencyKey` trên phiếu tạo bằng đường nháp là `null`** — key của các lệnh nháp/post/huỷ lưu ở bảng riêng; field đó chỉ có giá trị
   với phiếu tạo bằng đường cũ.
6. **Hàng nhận về chưa dùng được cho tới phần 3.** Không có đường nào trong bản này đưa hàng mua từ vùng chờ kiểm sang khả dụng.

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

`mvn -o clean verify` (Postgres thật qua Testcontainers): **1721 case unit + 249 case tích hợp, `failures = 0`, `errors = 0`** (trước phần này:
1686 + 243). Nghiệm thu đột biến (sửa cố ý một chỗ code, test phải đỏ): **18/18 bị bắt**, gồm: bỏ khoá PO khi post, nhận vào `AVAILABLE`, kiểm lượng
mở không cộng dồn các dòng, cho dùng lại mã lô, đảo nhập không gỡ chờ kiểm, và các chỗ sửa sau nghiệm thu HTTP.

### 7.2 Kiểm chứng qua HTTP thật

Môi trường: backend dev, Postgres + Redis thật (không phải container test), cổng 8090, 18/10/2026. **Bốn lượt**; ba lượt đầu tìm ra lỗi (§1 mục 2
và 3), lượt cuối chạy trên code cuối cùng: công ty `GD7B-233219`, **26 lượt gọi kiểm tra, 0 lượt sai kỳ vọng**. Mỗi bước có `X-Trace-Id` ở §4.

### 7.3 Chưa nghiệm thu

| Mục | Vì sao | Cần gì |
|---|---|---|
| QC mua hàng, lô con, hàng chờ kiểm thành khả dụng | Phần 3, chưa làm | `_v3` |
| Tài khoản chỉ có `PERM_GOODS_RECEIPT_MANAGE` (không có quyền PO) gọi live | Đã kiểm bằng test phân quyền tự động; chưa dựng tài khoản như vậy | tài khoản thử với đúng tập quyền |
| Hai lệnh **huỷ** phiếu đã post gửi song song | Có khoá lạc quan (`version`), chưa đo live | — |
| Script dữ liệu demo (`D26-…`) chạy lại sau thay đổi "vào vùng chờ kiểm" | Chưa chạy lại; hàng mua của script sẽ nằm ở chờ kiểm | chạy lại khi có QC (phần 3) |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm:**

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| R13, R16, R18, R20 (QC theo lượng, lô con, chặn đổi trạng thái lô đi vòng QC) | Phần 3 của Giai đoạn 7 | đang làm tiếp, gửi `_v3` |
| Phiếu đã post **bất biến** (FE §1.3 mục 8) | PO chọn D3: **giữ** đường đảo nhập cũ cho phiếu đã post, chỉ cho người có quyền mua hàng | quyết định khác của PO |
| Chi tiết PO nhúng danh sách phiếu nhận | Dùng `GET /v1/goods-receipts?purchaseOrderId=…` | nếu FE cần gộp một request, nêu lại |
| Cờ "bắt buộc hạn dùng" trên vật tư | Danh mục vật tư chưa có trường này; hạn dùng đang tuỳ chọn | quyết định riêng về danh mục |
| R19 giá trị tồn | Hoãn như v1 | phase riêng |

**Việc FE cần làm tiếp:**

Bắt buộc:
- [ ] Tạo phiếu nhận qua `POST /api/v1/goods-receipts` (nháp) rồi `POST /{id}/post` với `expectedVersion`; không còn tự đặt mã phiếu
- [ ] Hiển thị hàng vừa nhận ở **chờ kiểm** (`qualityHoldQuantity` / lô `HOLD`), không phải khả dụng
- [ ] Xử lý `409 PLANNED_QUANTITY_EXCEEDED` theo `errors[].field` (dòng nào vượt) — **không** tự thử lại
- [ ] Xử lý `409 PENDING_GOODS_RECEIPT` khi huỷ/đóng PO
- [ ] Xử lý `status: "DRAFT"` và `postedAt: null`

Nên làm:
- [ ] Lấy danh sách PO nhận được từ `/purchase-order-candidates` thay vì lọc danh sách PO ở client
- [ ] Gửi `Idempotency-Key` (UUID mới mỗi lần bấm, giữ nguyên khi retry) trên tạo nháp/post/huỷ
- [ ] Chạy A07–A12, A22, A24–A26, A35, A37 (phần phiếu nhận) trên dev và báo kết quả. Phần "value" của A07 không áp dụng — chưa có trường
      giá trị tồn (v1 §8); A13–A20 chờ `_v3`

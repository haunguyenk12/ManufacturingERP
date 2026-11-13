# BE trả lời — Hợp đồng số thập phân trên wire (phạm vi, số lẻ, dạng gửi/nhận)

**Trả lời cho:** `FE_QUESTION_DECIMAL_WIRE_PRECISION_2026-10-20.md` (v1) · **Ngày gửi:** 28/10/2026 · **Bản:** v1
**Mức:** P2 · **Breaking:** KHÔNG (chỉ trả lời, không đổi code) · **Thứ tự deploy:** không ràng buộc

---

## §0 — Kết luận trong một câu, và bảng phân loại

Response giữ số thập phân dạng **JSON number** với đủ chữ số; request nhận cả số lẫn chuỗi số cho field thập phân; phạm vi chuẩn là 13 chữ số nguyên + 6 số lẻ, trừ các nhóm hẹp hơn liệt kê ở §2 — và có một nhóm field chưa chặn chữ số lẻ thứ 7 (§2.3).

| # | FE hỏi | Trả lời | Bằng chứng |
|---|---|---|---|
| Q1 | Response thập phân giữ JSON number? | **Có.** Không đổi kiểu | §4.1: `9999999999999.999999` ra nguyên văn, không mũ, không chuỗi |
| Q2 | FE dùng parser giữ nguyên token số có được coi là đủ? | **Có** — đó là hướng đúng; BE không đổi kiểu wire | §4.1: số trên wire đã đủ chữ số, chỉ `JSON.parse` thường làm mất |
| Q3 | Request có nhận chuỗi số cho field thập phân? | **Có** (đo thật). Field số nguyên: nhận `"2"`, từ chối `"1.5"` | §4.2 |
| Q4 | Phạm vi / số lẻ chính thức theo nhóm? | **Có**, bảng §2 | dựng từ ràng buộc validate + kiểu cột DB, không viết từ trí nhớ |

## §1 — Nguyên nhân thật

Không có lỗi. Câu hỏi đến từ việc `JSON.parse` của JavaScript làm tròn số 19 chữ số (`9999999999999.999999` thành `10000000000000`). Wire đúng; phía đọc cần parser giữ nguyên token.

## §2 — Thay đổi contract: không đổi — đây là bảng phạm vi hiện hành

### §2.1 Phạm vi theo nhóm

| Nhóm | Ví dụ field | Request chấp nhận | Lưu ở DB | Ghi chú |
|---|---|---|---|---|
| Số lượng chung | `quantity`, `plannedQuantity`, `orderedQuantity` (đường nháp), `receivedQuantity`, `componentQuantity`, `outputQuantity`, `quantityDelta` | tối đa 13 chữ số nguyên + 6 số lẻ (`9999999999999.999999`) | 19 chữ số, 6 số lẻ | quá 6 số lẻ ⇒ 400 `VALIDATION_ERROR` có `errors[{field}]` |
| Số lượng thương mại + giá bán + đơn giá giờ | `lines[].quantity` của phiếu giao / hoá đơn, `unitPrice` dòng đơn bán, `laborRate`/`overheadRate` | `0`…`999999999`, tối đa 6 số lẻ | 19, 6 | vượt ⇒ 400 có field |
| Tỉ lệ hao hụt BOM | `scrapRate` | `0` ≤ x < `1`, tối đa 6 số lẻ (`0.051234` = 5.1234%) | 9, 6 | |
| Tiền thu (đồng nguyên) | `amount` của phiếu thu và phân bổ | số nguyên `1`…`9007199254740991` | 19, 0 | số lẻ ⇒ 400 có field |
| Tiền tính phía server | hoá đơn, giá thành | chỉ đọc | 19, 0 (hoá đơn); 19, 6 / 25, 12 (giá thành nội bộ) | response có thể có tới 6 số lẻ |
| Số nguyên | `version`, `expectedVersion`, `leadTimeDays`, `lineNo`, `capacityUnits`, số đếm | số nguyên | integer / bigint | `5.0` và `1.5` ⇒ 400 "must be a whole number" |

### §2.2 Dạng số trên response

- Luôn là JSON number, đủ chữ số, không dạng mũ (§4.1).
- **Số 0 thừa không cố định:** cùng một giá trị có thể ra `100` (lặp lại đúng cách client đã gửi, ngay trong response của lệnh ghi) hoặc `100.000000` (đọc lại từ DB). FE so sánh theo giá trị, đừng so chuỗi.

### §2.3 🔴 Nhóm field chưa chặn chữ số lẻ thứ 7

Các field dưới đây **chưa** có ràng buộc số lẻ ở bước validate. Gửi 7 số lẻ ⇒ **200**, DB làm tròn về 6 (HALF_UP), nhưng **response của chính lệnh đó lặp lại số chưa làm tròn**; đọc lại mới thấy số đã lưu (§4.3). Gửi quá 13 chữ số nguyên ⇒ **422 `BUSINESS_RULE_VIOLATION`, không có `errors[]`** (DB từ chối), không phải 400 có field.

| Màn hình | Field |
|---|---|
| Giá thành chuẩn | `materialCost`, `laborCost`, `overheadCost` |
| Thiết lập kho cho vật tư | `safetyStock`, `reorderPoint` |
| Nhu cầu thủ công / ước tính sản xuất | `requiredQuantity`, `targetQuantity` |
| Nhà cung cấp – vật tư | `minimumOrderQuantity`, `unitPrice` |
| Đề nghị mua | `requestedQuantity`, `approvedQuantity` |
| Đơn mua (đường tạo cũ `POST /api/v1/purchase-orders`) | `lines[].orderedQuantity`, `lines[].unitPrice` — đường nháp đã chặn |
| Quy trình | `setupMinutes`, `runMinutesPerUnit` |
| Đơn bán (đường tạo cũ `POST /api/sales-orders/v1`) | `lines[].orderedQuantity` — đường nháp đã chặn |
| Lệnh SX (các đường cũ) | `POST /api/v1/material-issues` (phẳng), `/wip-transactions`, `component-issues`, `output-completions`: `quantity` |

Các đường FE đang dùng cho đơn mua (`POST /api/v1/purchase-orders/drafts`), phiếu nhận, đơn bán (nháp), BOM, lệnh SX đều **đã** chặn — không thuộc nhóm này.

Đề xuất: FE tự giới hạn 6 số lẻ và 13 chữ số nguyên cho các field trên. Bổ sung ràng buộc phía BE là thay đổi riêng (sẽ thành 400 có field thay vì làm tròn) — cần PO/FE đồng ý vì là chặn mới.

## §3 — Breaking change

**`Wire: KHÔNG có`** — bản trả lời này không đổi code.

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Đo trên `PUT /api/v1/inventory/item-warehouse-settings` (field `safetyStock` thập phân, `leadTimeDays` số nguyên). Mỗi dòng: giá trị gửi → HTTP → giá trị trên response → `X-Trace-Id`.

### §4.1 Bốn mẫu biên — response nguyên văn

| Gửi `safetyStock` | HTTP | Response (nguyên văn) | Trace |
|---|---|---|---|
| `0` | 200 | `"safetyStock":0` | `72c0836b0e4549a6` |
| `0.000001` | 200 | `"safetyStock":0.000001` | `ced7b472c2474efa` |
| `999999999.999999` | 200 | `"safetyStock":999999999.999999` | `61e33e37987446f9` |
| `9999999999999.999999` (body viết tay, không qua bộ chuyển số) | 200 | `"safetyStock":9999999999999.999999` | `f5548f12b7634753` |

```http
PUT /api/v1/inventory/item-warehouse-settings
Content-Type: application/json

{"itemId":"7c1b4dfb-5ac3-4d9f-a917-87fd1050797a","warehouseId":"05a7d7ab-8699-4f08-aca7-1e39b91e9589","reorderPoint":0,"leadTimeDays":0,"safetyStock":9999999999999.999999}
```

HTTP **200** · `X-Trace-Id: f5548f12b7634753`

```json
{"code":"SUCCESS","result":{"settingId":"e7474d6c-cb43-4f0a-ae1c-d2057b8cdffe","itemId":"7c1b4dfb-5ac3-4d9f-a917-87fd1050797a","itemCode":"LOT-170603","itemName":"LOT","warehouseId":"05a7d7ab-8699-4f08-aca7-1e39b91e9589","warehouseCode":"RM-170603","warehouseName":"RM","safetyStock":9999999999999.999999,"reorderPoint":0,"leadTimeDays":0,"defaultSupply":false,"defaultOutput":false,"status":"ACTIVE","createdAt":"2026-10-28T10:06:06.383343Z","updatedAt":"2026-10-28T10:06:06.453059Z"},"message":"OK"}
```

### §4.2 Chuỗi số trong request

| Gửi | HTTP | Kết quả | Trace |
|---|---|---|---|
| `"safetyStock":"1.5"` (chuỗi, field thập phân) | 200 | nhận, `"safetyStock":1.5` | `a4e7b3dc58344b66` |
| `"leadTimeDays":"2"` (chuỗi, field số nguyên) | 200 | nhận, `"leadTimeDays":2` | `f21e90e469294212` |
| `"leadTimeDays":"1.5"` (chuỗi) | 400 | `VALIDATION_ERROR`, field `leadTimeDays`, "must be a whole number" | `c0fc5e3f1c2d4db0` |
| `"leadTimeDays":1.5` (số) | 400 | như trên | `752830ab26d44bd9` |

```http
PUT /api/v1/inventory/item-warehouse-settings
Content-Type: application/json

{"itemId": "7c1b4dfb-5ac3-4d9f-a917-87fd1050797a", "warehouseId": "05a7d7ab-8699-4f08-aca7-1e39b91e9589", "reorderPoint": 0, "leadTimeDays": 0, "safetyStock": "1.5"}
```

HTTP **200** · `X-Trace-Id: a4e7b3dc58344b66`

```json
{"code":"SUCCESS","result":{"settingId":"e7474d6c-cb43-4f0a-ae1c-d2057b8cdffe","itemId":"7c1b4dfb-5ac3-4d9f-a917-87fd1050797a","itemCode":"LOT-170603","itemName":"LOT","warehouseId":"05a7d7ab-8699-4f08-aca7-1e39b91e9589","warehouseCode":"RM-170603","warehouseName":"RM","safetyStock":1.5,"reorderPoint":0,"leadTimeDays":0,"defaultSupply":false,"defaultOutput":false,"status":"ACTIVE","createdAt":"2026-10-28T10:06:06.383343Z","updatedAt":"2026-10-28T10:06:06.497973Z"},"message":"OK"}
```

```http
PUT /api/v1/inventory/item-warehouse-settings
Content-Type: application/json

{"itemId": "7c1b4dfb-5ac3-4d9f-a917-87fd1050797a", "warehouseId": "05a7d7ab-8699-4f08-aca7-1e39b91e9589", "reorderPoint": 0, "leadTimeDays": "1.5", "safetyStock": 0}
```

HTTP **400** · `X-Trace-Id: c0fc5e3f1c2d4db0`

```json
{"code":"VALIDATION_ERROR","message":"leadTimeDays: must be a whole number","errors":[{"field":"leadTimeDays","message":"must be a whole number"}]}
```

### §4.3 Field chưa chặn số lẻ thứ 7 (§2.3)

Gửi `safetyStock: 1.1234567` ⇒ 200, response lặp lại `1.1234567` (`e0a146ea8a544a81`), **giá trị đã lưu là `1.123457`** (đọc trực tiếp DB ngay sau đó). Gửi `12345678901234` (14 chữ số nguyên) ⇒ 422:

```http
PUT /api/v1/inventory/item-warehouse-settings
Content-Type: application/json

{"itemId": "7c1b4dfb-5ac3-4d9f-a917-87fd1050797a", "warehouseId": "05a7d7ab-8699-4f08-aca7-1e39b91e9589", "reorderPoint": 0, "leadTimeDays": 0, "safetyStock": 12345678901234}
```

HTTP **422** · `X-Trace-Id: 36d6afffdd5f40b2`

```json
{"code":"BUSINESS_RULE_VIOLATION","message":"Request violates a data constraint"}
```

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — dùng cùng một envelope:

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "quantity", "message": "must be greater than 0" } ] }
```

| Tình huống | HTTP | `code` | Có `errors[]`? |
|---|---|---|---|
| Quá số lẻ / quá phạm vi ở field có ràng buộc (§2.1) | 400 | `VALIDATION_ERROR` | có |
| Số lẻ / `5.0` / chuỗi `"1.5"` ở field số nguyên | 400 | `VALIDATION_ERROR` ("must be a whole number") | có |
| Quá 13 chữ số nguyên ở field thuộc §2.3 | 422 | `BUSINESS_RULE_VIOLATION` | **không** |

## §6 — Quyền, phạm vi và giới hạn đã biết

Không liên quan quyền. Giới hạn đã biết: §2.3.

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | Thêm 5 ca vào bộ kiểm cấu hình JSON thật của app (dựng đúng cấu hình đang chạy): 4 mẫu biên + `100.000000` ra đủ chữ số dạng number; `1.1234567` đọc vào giữ đủ 7 số lẻ (việc chặn là của validate); chuỗi `"1.5"` vào field thập phân được nhận; chuỗi `"1.5"` vào field số nguyên bị từ chối. Toàn bộ `mvn -o test` của bộ này xanh |
| HTTP thật | §4, 10 lần gọi |
| Môi trường | profile dev, jar cổng 8090, Postgres + Redis docker; đo 28/10/2026 lúc 17:06 (giờ VN) |

**Chưa nghiệm thu:** không đo từng field của §2.1 qua HTTP — bảng dựng từ ràng buộc trong code và kiểu cột DB; §4 đo đại diện trên một endpoint.

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

| Mục | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Chặn 7 số lẻ cho các field §2.3 | Là chặn mới, câu hỏi này chỉ xin xác nhận | FE/PO đồng ý |
| Đổi thập phân sang chuỗi trên wire | FE Q1/Q2 chọn giữ number | — |

**FE cần làm tiếp**
- [ ] (bắt buộc) Đọc response bằng parser giữ nguyên token số trước khi đưa vào Decimal.
- [ ] (nên làm) Giới hạn 6 số lẻ / 13 chữ số nguyên ở form cho các field §2.3.

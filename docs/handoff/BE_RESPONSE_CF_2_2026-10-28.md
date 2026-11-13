# BE trả lời — Lô hết hạn bị loại khỏi hoạch định, giữ chỗ, cấp vật tư và readiness

**Trả lời cho:** `FE_REQUEST_EXPIRED_STOCK_GUARDS_2026-10-20.md` (v1) · **Ngày gửi:** 28/10/2026 · **Bản:** v1
**Mức:** P1 · **Breaking:** CÓ (số MRP đổi + chặn mới, §3) · **Thứ tự deploy:** không ràng buộc

---

## §0 — Kết luận trong một câu, và bảng phân loại

Lô `AVAILABLE` có `expiresAt <= giờ server` nay không còn được MRP tính là tồn dùng được, không giữ chỗ / xuất / duyệt xuất được, và phần đã giữ trên lô đó không còn được coi là đủ để phát hành lệnh.

| # | FE nêu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | MRP không tính lô hết hạn là tồn dùng được | 🟡 **Thiếu thật, đã làm** | A/B §1: cùng dữ liệu, bản cũ `COVERED`, bản mới `SHORTAGE` net 10 |
| R02 | Giữ chỗ tự động / tay không chọn lô hết hạn | 🟡 **Thiếu thật, đã làm** | §4.1: tự động chỉ lấy lô còn hạn (6); tay trên lô hết hạn ⇒ 409 `LOT_NOT_ELIGIBLE` |
| R03 | Xuất và duyệt xuất kiểm hạn lúc ghi | 🟡 **Thiếu thật, đã làm** | Lô hết hạn trong lúc phiếu chờ duyệt ⇒ duyệt 409 `LOT_NOT_ELIGIBLE`, phiếu vẫn chờ, không trừ kho (test tự động) |
| R04 | Readiness/release không coi phần giữ trên lô hết hạn là đủ | 🟡 **Thiếu thật, đã làm** | §4.2: release ⇒ 409, lệnh `BLOCKED`; readiness có trường mới `ineligibleReservedQuantity` + `blocker` |
| R05 | Kiểm quyền trước replay; gửi lại cùng key sau khi lô hết hạn chỉ trả phiếu cũ | ✅ **Đã đúng từ trước, không sửa** | Phiếu cấp tìm theo `Idempotency-Key` và trả lại **trước** mọi bước kiểm hạn; quyền (`@PreAuthorize`) chạy trước cả bước đó |

## §1 — Nguyên nhân thật

Từ GĐ4 (15/10), hạn dùng chỉ được kiểm ở màn hình kho thủ công (`POST /api/v1/inventory/issue`, đếm vào `expiredQuantity` của tổng hợp tồn). MRP, giữ chỗ cho lệnh SX và cấp vật tư vẫn coi lô `AVAILABLE` quá hạn là hàng dùng được — một hệ thống, hai con số. PO duyệt đổi lại ngày 27/10. Dữ liệu cũ không bị sửa: giữ chỗ đã có trên lô vừa hết hạn **không** bị tự huỷ (Q3).

**A/B trên cùng dữ liệu, cùng lúc** (lô A 10 KG hết hạn, lô B 6 KG còn hạn đang giữ cho một lệnh SX, nhu cầu 10):

| | Bản trước CF-2 | Bản này |
|---|---|---|
| Lượt chạy | `RUN-B282CF4E` (`X-Trace-Id 6cbe2471bc29415e`) | `RUN-710A6592` (`2a8ea49d659e4368`) |
| Khả dụng của vật tư | 10 (lô A hết hạn vẫn tính) | 0 |
| Net / trạng thái | 0 / `COVERED` | 10 / `SHORTAGE` |
| `excludedLotCount` | 0 | 1 |
| Số đề xuất (cả lượt) | 1 | 2 |

Field mốc chứng minh hai lượt chạy trên hai bản khác nhau: response dòng nhu cầu của bản cũ **không có** `pendingQcQuantity`, bản này có.

## §2 — Thay đổi contract: bảng trước / sau

| Field / Endpoint | Trước | Sau | Nullable? | Phá client đang chạy? |
|---|---|---|---|---|
| Số MRP (`POST /api/v1/planning-runs`, `/planning-runs/commercial`) | lô hết hạn tính vào khả dụng | không tính; `excludedLotCount` đếm cả lô hết hạn | — | Số đổi (xem A/B) |
| `POST /api/v1/work-orders/{id}/reserve` | có thể chọn lô hết hạn | bỏ qua lô hết hạn (FEFO) | — | Lượng giữ có thể ít hơn |
| `POST /api/v1/work-orders/{id}/material-reservations` lô hết hạn | 201 | 409 `LOT_NOT_ELIGIBLE` | — | Có |
| Cấp vật tư (`POST …/material-issues`, `/approve`, `POST /api/v1/material-issues`) lô hết hạn | 200/201 | 409 `LOT_NOT_ELIGIBLE` | — | Có |
| Readiness `lines[].ineligibleReservedQuantity` | — | số, KG theo đơn vị vật tư | không (0 khi không có) | Không |
| Readiness `lines[].blocker` | — | chuỗi `RESERVED_LOT_INELIGIBLE` khi có phần giữ không dùng được | có (`null`) | Không |
| Readiness `reservedPercent` | tính mọi phần giữ | chỉ phần giữ còn dùng được | — | Số đổi |

**Field mới (5 cột):**

| Tên | Kiểu | Nullable | Nguồn giá trị | Ví dụ thật |
|---|---|---|---|---|
| `ineligibleReservedQuantity` | number | không | Σ phần còn lại của giữ chỗ `ACTIVE` của dòng trên lô không `AVAILABLE` hoặc `expiresAt <= giờ server` | `0` (§4.2: phần giữ còn hiệu lực 6/10) |
| `blocker` | string | có | `RESERVED_LOT_INELIGIBLE` khi `ineligibleReservedQuantity > 0`, ngược lại `null` | `null` |

`reservedQuantity` **giữ nghĩa cũ** (mọi phần giữ còn lại, bằng con số trên danh sách lệnh SX). Phần còn thiếu để phát hành = `cần − (reservedQuantity − ineligibleReservedQuantity)`.

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /api/v1/planning-runs`, `/planning-runs/commercial` | kho có lô hết hạn | lô hết hạn là khả dụng | không tính | Đổi số | Không sửa code; số đề xuất mua có thể tăng |
| `POST /api/v1/work-orders/{id}/material-reservations` | chọn lô hết hạn | 201 | 409 `LOT_NOT_ELIGIBLE` | Chặn mới | Hiển thị lỗi theo `code` |
| Cấp/duyệt cấp vật tư | dòng trên lô hết hạn | 200/201 | 409 `LOT_NOT_ELIGIBLE` | Chặn mới | Hiển thị lỗi; phiếu chờ vẫn chờ |
| `POST /api/v1/work-orders/{id}/release` | phần giữ nằm trên lô hết hạn | có thể phát hành | 409 `STATE_CONFLICT`, lệnh `BLOCKED` | Chặn mới | Đọc readiness, giải phóng giữ chỗ (`POST /api/v1/work-orders/{id}/release-reservations`) rồi giữ lại |
| `GET /api/v1/work-orders/{id}/material-readiness` | — | `reservedPercent` tính mọi phần giữ | chỉ phần dùng được | Đổi số | Không |

Không đổi mặc định query param, không đổi thứ tự sắp xếp, không bỏ field nào.

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Fixture: vật tư theo lô, lô A 10 KG (hết hạn), lô B 6 KG (còn hạn); lệnh SX cần 10.

### §4.1 Giữ chỗ tự động bỏ qua lô hết hạn; giữ tay trên lô hết hạn bị chặn

```http
POST /api/v1/work-orders/b231aa11-4ae0-4d60-943c-98775554649c/reserve
Content-Type: application/json

{"expectedWorkOrderVersion": 0}
```

HTTP **201** · `X-Trace-Id: e8d56d14e0b24b44`

```json
{"code":"SUCCESS","result":[{"reservationId":"5ed0c005-3a58-4f87-8bc1-af32d1cad304","workOrderId":"b231aa11-4ae0-4d60-943c-98775554649c","componentLineId":"39e99164-3ccd-452a-80f3-bbae6bddefa9","itemId":"7134e305-89b3-4cfc-ae83-806d63bdb327","itemSku":"EXP-170603","warehouseId":"05a7d7ab-8699-4f08-aca7-1e39b91e9589","warehouseCode":"RM-170603","lotId":"81afa87c-cc7d-4900-b1a0-81f7f1821c00","lotNumber":"EXB-170603","quantity":6.000000,"consumedQuantity":0,"remainingQuantity":6.000000,"status":"ACTIVE","createdAt":"2026-10-28T10:06:05.464323100Z","updatedAt":"2026-10-28T10:06:05.464323100Z","workOrderVersion":1}],"message":"Created successfully"}
```

```http
POST /api/v1/work-orders/b231aa11-4ae0-4d60-943c-98775554649c/material-reservations
Content-Type: application/json

{"componentLineId": "39e99164-3ccd-452a-80f3-bbae6bddefa9", "warehouseId": "05a7d7ab-8699-4f08-aca7-1e39b91e9589", "lotId": "a98c255d-92f1-4fc2-b2b7-bad0c7ce859d", "quantity": 1, "expectedWorkOrderVersion": 1}
```

HTTP **409** · `X-Trace-Id: 63af6d75cfb64196`

```json
{"code":"LOT_NOT_ELIGIBLE","message":"Lot expired at 2026-10-27T10:06:05.036148Z and cannot be reserved"}
```

### §4.2 Phát hành khi còn thiếu 4 ⇒ chặn; readiness sau đó

```http
POST /api/v1/work-orders/b231aa11-4ae0-4d60-943c-98775554649c/release
Content-Type: application/json

{"expectedWorkOrderVersion": 1}
```

HTTP **409** · `X-Trace-Id: d343469910264d80`

```json
{"code":"STATE_CONFLICT","message":"Cannot release: 1 component(s) not fully reserved"}
```

```http
GET /api/v1/work-orders/b231aa11-4ae0-4d60-943c-98775554649c/material-readiness
Content-Type: application/json
```

HTTP **200** · `X-Trace-Id: fdee22e3d48d44c7`

```json
{"code":"SUCCESS","result":{"workOrderId":"b231aa11-4ae0-4d60-943c-98775554649c","workOrderNo":"WO2-170603","status":"BLOCKED","ready":false,"canRelease":false,"reservedPercent":60.00,"shortageLineCount":1,"lines":[{"componentLineId":"39e99164-3ccd-452a-80f3-bbae6bddefa9","itemId":"7134e305-89b3-4cfc-ae83-806d63bdb327","itemSku":"EXP-170603","itemName":"EXP","requiredQuantity":10.000000,"issuedQuantity":0.000000,"reservedQuantity":6.000000,"shortageQuantity":4.000000,"ineligibleReservedQuantity":0,"blocker":null}]},"message":"OK"}
```

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — dùng cùng một envelope:

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "quantity", "message": "must be greater than 0" } ] }
```

`code` là tên hằng; `errors` chỉ có khi lỗi theo field; `result` vắng mặt khi lỗi.

| Tình huống | HTTP | `code` | Có `errors[]`? |
|---|---|---|---|
| Giữ chỗ / xuất / duyệt trên lô hết hạn | 409 | `LOT_NOT_ELIGIBLE` | không |
| Phát hành khi phần giữ dùng được < cần | 409 | `STATE_CONFLICT` (lệnh chuyển `BLOCKED`) | không |
| Gửi lại cùng `Idempotency-Key` + cùng body sau khi lô hết hạn | 200/201 | `SUCCESS`, trả phiếu cũ, không xuất lần hai | không |

**Trả lời Q2 (biên):** **có**, `expiresAt` **đúng bằng** giờ server là đã hết hạn (`<=`), giữ quy ước GĐ4. **Q3:** **có**, giữ chỗ đã có trên lô vừa hết hạn giữ nguyên lịch sử, không tự huỷ — readiness báo `blocker`, planner giải phóng bằng `release-reservations`. **Q1:** trước bản này chỉ màn hình kho thủ công kiểm hạn; R01–R04 làm mới trong bản này, R05 vốn đã đúng.

## §6 — Quyền, phạm vi và giới hạn đã biết

- Quyền không đổi: MRP `PERM_MRP_RUN`; giữ chỗ `PERM_MATERIAL_RESERVATION_MANAGE`; cấp vật tư `PERM_MATERIAL_ISSUE_MANAGE` / duyệt `PERM_MATERIAL_ISSUE_APPROVE`.
- "Giờ server" là đồng hồ của BE (UTC), không phải giờ trình duyệt.
- **Giới hạn:** lệnh SX đã `RELEASED` có giữ chỗ trên lô sắp hết hạn sẽ bị chặn cấp vật tư đúng lúc xưởng cần — đây là hành vi FE xin, nhưng nên báo người dùng trước.

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — **2173 unit + 325 IT**, 0 failures, 0 errors (lúc phase này đóng); bản hiện tại 2219 + 332 xanh |
| Mutation | 9/9 bị test bắt (bỏ điều kiện hạn ở từng query MRP / FEFO / giữ tay / xuất / duyệt / readiness; `>` đổi `>=` ở biên; release bỏ qua phần không dùng được) |
| HTTP thật | §1 A/B + §4: giữ tự động 6 (lô B), giữ tay lô A ⇒ 409 (`63af6d75cfb64196`), release ⇒ 409 (`d343469910264d80`), MRP bản này: khả dụng 6, net 4, `excludedLotCount` 1 (lượt chạy trước khi giữ chỗ) |
| Môi trường | profile dev, jar cổng 8090 (bản này) và 8091 (bản trước CF-2), cùng Postgres + Redis docker, DB dev có dữ liệu demo; hạn lô A đặt về quá khứ bằng một câu SQL (API cố ý không cho ghi hạn quá khứ); đo 28/10/2026 lúc 17:06–17:08 (giờ VN) |

**Chưa nghiệm thu qua HTTP:** duyệt phiếu cấp khi lô hết hạn trong lúc chờ duyệt (R03), replay sau khi hết hạn (R05), và readiness có `ineligibleReservedQuantity > 0` (giữ chỗ tạo trước rồi lô mới hết hạn — ví dụ §4.2 chỉ có ca 0). Cả ba chỉ có test tự động.

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Tự huỷ giữ chỗ trên lô vừa hết hạn | FE Q3 chọn giữ lịch sử | — |

**FE cần làm tiếp**
- [ ] (bắt buộc) Bắt 409 `LOT_NOT_ELIGIBLE` ở giữ chỗ tay và cấp/duyệt cấp vật tư.
- [ ] (bắt buộc) Readiness: đọc `ineligibleReservedQuantity` + `blocker`, gợi ý `release-reservations`.
- [ ] (nên làm) Báo người dùng số MRP có thể đổi khi kho có lô hết hạn.

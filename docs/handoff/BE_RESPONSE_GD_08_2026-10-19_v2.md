# BE trả lời — Giai đoạn 8: Lệnh sản xuất, cấp vật tư, công đoạn và dở dang

**Trả lời cho:** `FE_HANDOFF_GD_08_2026-10-14.md` (v1, P1) · **Ngày gửi:** 19/10/2026 · **Bản:** v2 — **phần 2/4: cấp vật tư (`G8b`)**
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. Bản này **bổ sung** v1 (nháp lệnh), không thay nó. Còn v3 (công đoạn) và v4
(dở dang + hoàn vật tư). Chưa phải "FE nghiệm thu đạt": task chỉ đóng khi FE chạy A01–A40 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Phiếu cấp vật tư nhiều dòng nay bắt buộc gửi version lệnh, có phiếu bổ sung tách bạch với phiếu thường, xét vượt định mức theo cả phiếu, kiểm tồn
nguồn ngay lúc tạo phiếu chờ và kiểm lại lúc duyệt, người tạo tự huỷ được phiếu chờ của mình, và có endpoint chi tiết phiếu + ứng viên nguồn cấp.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R07 | Phiếu 1–500 dòng dương, ghi tồn/lệnh/WIP/phiếu/idempotency trong một transaction; phiếu thường chỉ dùng reservation của lệnh | 🟡 **Đã làm** | Tối đa 500 dòng, 6 chữ số lẻ; dòng thường không reservation phải đúng kho của dòng vật tư ⇒ `422` (§4.2). Một transaction như trước |
| R08 | Một dòng bổ sung vượt định mức ⇒ **cả phiếu** chờ, không ảnh hưởng tồn; gross không trừ hoàn; lý do/nguồn hợp lệ; phiếu chờ không giữ tồn | 🟡 **Đã làm** | Vượt định mức xét theo vật tư trên cả phiếu (7 + 7 vượt 12 còn lại ⇒ cả phiếu chờ, §4.4); tồn RAW2 vẫn 50 (§4.5) |
| R09 | Duyệt kiểm lại version phiếu + lệnh, trạng thái, tồn hiện tại, quyền; thiếu một dòng ⇒ rollback cả phiếu; từ chối / người tạo huỷ không ảnh hưởng tồn | 🟡 **Đã làm** | Tồn hụt sau khi tạo phiếu ⇒ duyệt `409`, phiếu vẫn chờ, không dòng nào ghi (§4.7); huỷ của người tạo (§4.6) |
| §3.3 | Chi tiết một phiếu + ngữ cảnh quyết định | 🟡 **Đã làm** | `GET /api/v1/material-issues/{I}` (§4.5) |
| §3.3 | Ứng viên nguồn cấp | 🟡 **Đã làm** | `GET /api/v1/work-orders/{W}/material-issue-candidates` (§4.1) |
| R21 (phần cấp) | List có tìm kiếm + khoảng ngày | 🟡 **Đã làm** | `search` (mã phiếu / số lệnh), `fromDate`/`toDate` (§2.1) |
| R22 (phần cấp) | Version + idempotency | 🟡 **Đã làm** | Thiếu ⇒ `400` field; lệch ⇒ `409`; gửi lại cùng key sau khi đã duyệt ⇒ trả trạng thái hiện tại |
| R10–R14 | Công đoạn | ⏳ **Phần v3** | — |
| R15–R17 | Dở dang, hoàn vật tư | ⏳ **Phần v4** | — |

### Trả lời Q (những câu thuộc phần này)

| Q | Trả lời |
|---|---|
| Q1 | **Có, phần cấp vật tư:** tạo phiếu / huỷ phiếu của mình / ứng viên nguồn = `PERM_MATERIAL_ISSUE_MANAGE`; duyệt / từ chối = `PERM_MATERIAL_ISSUE_APPROVE`; tự duyệt thêm `PERM_MATERIAL_ISSUE_OVERRIDE`. Ba quyền READ/RETURN mới thuộc v4 |
| Q2 | **Một phần.** Đọc phiếu (danh sách, chi tiết) mở cho **bất kỳ** `MANAGE` / `APPROVE` / `OVERRIDE` — quản lý duyệt không cần quyền cấp vật tư mới thấy hàng đợi. Lệnh ghi chỉ kiểm quyền của chính lệnh, không đòi thêm READ |
| Q3 | **Có.** Duyệt phiếu do **chính mình** tạo cần cả `APPROVE` lẫn `OVERRIDE`; kiểm theo mã quyền trên nhà máy của lệnh, không theo tên role (§4.8) |
| Q8 | **Có.** Phiếu bổ sung lấy tồn tự do ở **mọi** kho `ACTIVE` của nhà máy. Phiếu thường không reservation thì chỉ lấy đúng kho của dòng vật tư (nếu dòng có kho — v1 D2) |
| Q9 | **Có.** Lý do giữ **theo dòng** (`reasonCode` + `overrideReason` mỗi dòng); `supplemental` và `expectedWorkOrderVersion` ở cấp phiếu. Phiếu bổ sung: mỗi dòng bắt buộc cả hai |
| Q10 | **Có.** Phiếu chờ **không** giữ tồn, nhưng nguồn tồn tự do phải đủ lúc tạo — các dòng cùng nguồn (vật tư, kho, lô) **cộng lại** trước khi so (§4.3). Duyệt kiểm lại vì tồn có thể đổi (§4.7) |
| Q11 | **Có.** "Đã cấp" để xét vượt định mức là **gross** — hoàn vật tư (v4) không trừ |
| Q33 | **Có.** Lý do (từ chối, huỷ phiếu, huỷ lệnh, lý do dòng bổ sung, lý do khi duyệt) trim rồi phải dài **5–2000** ký tự. Thiếu lý do khi bắt buộc vẫn trả `400 APPROVAL_REASON_REQUIRED` như cũ; có nhưng ngoài khoảng ⇒ `400 VALIDATION_ERROR` field `reason` |
| Q4, Q22, Q23, Q31 | Trả lời ở v3 (công đoạn / hoàn thành) |
| Q26, Q34, Q37 | Trả lời ở v4 |

---

## §1 — Nguyên nhân thật

Không có lỗi FE báo — phần này hoàn thiện luồng cấp vật tư đã có từ trước (phiếu chờ duyệt vượt định mức có từ `DEC-09`). Ba điều ảnh hưởng tới FE:

1. **`POST /work-orders/{W}/material-issues` bắt buộc `expectedWorkOrderVersion`**, và duyệt/từ chối nay bắt buộc **cả** version lệnh lẫn version
   phiếu. Body cũ `{reason}` của duyệt/từ chối nhận `400` ngay khi BE deploy (§3).
2. **Vượt định mức xét theo vật tư trên cả phiếu**, không từng dòng. Trước đây hai dòng mỗi dòng vừa đủ nhưng cộng lại vượt thì dòng thứ hai lỗi
   giữa chừng; nay cả phiếu thành phiếu chờ (phiếu bổ sung) hoặc bị từ chối với `409` theo field (phiếu thường gửi `supplemental: false`).
3. 🔴 **Nghiệm thu HTTP thật tìm ra một lỗi mà unit + IT đều xanh:** phiếu cấp **đầu tiên** của một lệnh `RELEASED` làm version lệnh tăng **2**
   (3 → 5) thay vì 1. Phiếu chuyển lệnh sang `IN_PROGRESS`, lần flush phiếu ghi luôn trạng thái đó, rồi bước "tăng version" ghi lần nữa. Không hỏng
   dữ liệu (FE dùng `workOrderVersion` trong response vẫn đúng), nhưng trái hợp đồng "mỗi lệnh tăng đúng một". Đã sửa cả đường tạo phiếu lẫn đường duyệt,
   thêm guard ở test end-to-end; lượt HTTP cuối: 3 → 4 (tạo phiếu) và 4 → 5 (duyệt phiếu chờ đầu tiên của lệnh `RELEASED`).

---

## §2 — Thay đổi contract

### 2.1 Endpoint

| Method | Path | Mới / đổi | Quyền |
|---|---|---|---|
| POST | `/api/v1/work-orders/{W}/material-issues` | **Đổi:** body thêm `expectedWorkOrderVersion` **bắt buộc**, `supplemental?`; tối đa 500 dòng; quy tắc dòng §2.3 | `PERM_MATERIAL_ISSUE_MANAGE` |
| POST | `/api/v1/work-orders/{W}/material-issues/{I}/approve` | **Đổi:** body `{expectedWorkOrderVersion, expectedIssueVersion, reason?}` — hai version **bắt buộc**; header `Idempotency-Key` tuỳ chọn | `PERM_MATERIAL_ISSUE_APPROVE` (+ `OVERRIDE` nếu tự duyệt) |
| POST | `/api/v1/work-orders/{W}/material-issues/{I}/reject` | **Đổi:** body `{expectedWorkOrderVersion, expectedIssueVersion, reason}` | `PERM_MATERIAL_ISSUE_APPROVE` |
| POST | `/api/v1/work-orders/{W}/material-issues/{I}/cancel` | **Mới** — người tạo rút phiếu chờ; body như từ chối | `PERM_MATERIAL_ISSUE_MANAGE` (chỉ người tạo) |
| GET | `/api/v1/material-issues/{I}` | **Mới** — phiếu + `decisionContext[]` | `MANAGE` **hoặc** `APPROVE` **hoặc** `OVERRIDE` |
| GET | `/api/v1/work-orders/{W}/material-issue-candidates?componentLineId=&mode=NORMAL\|SUPPLEMENTAL&page&size` | **Mới** | `PERM_MATERIAL_ISSUE_MANAGE` |
| GET | `/api/v1/material-issues?plantId&workOrderId&status&search&fromDate&toDate` | **Đổi (additive):** `search` (mã phiếu / số lệnh, không phân biệt hoa thường), `fromDate`/`toDate` `YYYY-MM-DD` theo ngày nhà máy trên `requestedAt`, gồm cả hai đầu | `MANAGE` **hoặc** `APPROVE` **hoặc** `OVERRIDE` (trước: chỉ `MANAGE`) |
| GET | `/api/v1/work-orders/{W}/material-issues?status` | **Đổi quyền (nới):** như trên | như trên |

🔴 **Không đổi:** `POST /api/v1/material-issues` (dạng phẳng, một reservation) và `POST .../component-issues` vẫn **không** đòi version — chúng vẫn
tăng version lệnh, nên lệnh khác gửi version cũ sau đó vẫn nhận `409` đúng.

### 2.2 Field mới trên response

| Field | Kiểu | Nullable? | Nguồn giá trị | Ví dụ thật |
|---|---|---|---|---|
| `MaterialIssue.version` | integer | không | version của phiếu — gửi lại làm `expectedIssueVersion` | `0` lúc tạo, `1` sau huỷ/duyệt |
| `MaterialIssue.workOrderVersion` | integer | có (`null` ở đường đọc không nạp lệnh) | version của lệnh **sau** lệnh này | `4` |
| `MaterialIssue.supplemental` | boolean | không | phiếu tạo bằng `supplemental: true` | `true` |
| `MaterialIssue.requestedByUserId` | UUID | có | người tạo phiếu | `"e9a1d42e-…"` |
| `MaterialIssue.cancellationReason` | string | có — chỉ khi người tạo huỷ | lý do huỷ đã trim | `"Found spare sheets"` |

`decisionContext[]` (chi tiết phiếu), một phần tử cho mỗi dòng vật tư của phiếu: `{componentLineId, itemId, itemCode, uom, requiredQuantity,
grossIssuedQuantity, batchRequestedQuantity, extraRequestedQuantity, currentAvailability, asOf}`. `extraRequestedQuantity` = `max(0, đã cấp + phiếu
này − định mức)`, **chỉ có** khi phiếu đang chờ (đã quyết thì `null`). `currentAvailability` = tồn hiện tại của các nguồn phiếu nêu (phần chưa cấp của
reservation, hoặc tồn tự do của (kho, lô)), mỗi nguồn đếm một lần — ảnh chụp lúc đọc, duyệt vẫn kiểm lại.

Ứng viên nguồn, mỗi dòng: `{componentLineId, lineNo, itemId, itemCode, itemName, uom, requiredQuantity, grossIssuedQuantity, sourceType, reservationId,
warehouseId, warehouseCode, lotId, lotCode, lotExpiresAt, availableQuantity, eligible, ineligibleReason, suggestedQuantity, asOf}`.
`mode=NORMAL` (mặc định): reservation `ACTIVE` của lệnh, `suggestedQuantity = min(còn lại của reservation, định mức − đã cấp)`; lô không đạt vẫn liệt kê
với `eligible: false`, `ineligibleReason` = `LOT_HOLD` / `LOT_REJECTED` / `LOT_EXPIRED`…, gợi ý `0`. `mode=SUPPLEMENTAL`: tồn tự do đạt chuẩn (lô
`AVAILABLE`, chưa hết hạn, theo FEFO) ở mọi kho của nhà máy, `suggestedQuantity: null`.

### 2.3 Quy tắc dòng của phiếu (kiểm cả phiếu trước khi ghi)

| Điều kiện | Kết quả |
|---|---|
| `supplemental: true` và dòng có `reservationId` | `400` field `lines[i].reservationId` |
| `supplemental: true` và dòng thiếu `reasonCode` / `overrideReason` | `400` field `lines[i].reasonCode` / `lines[i].overrideReason` |
| phiếu thường, dòng không reservation, kho khác kho của dòng vật tư | `422 RESOURCE_SCOPE_MISMATCH` field `lines[i].warehouseId` |
| `sourceExecutionId` mà `reasonCode` khác `REWORK` | `400` field `lines[i].reasonCode` |
| lý do dòng dưới 5 / trên 2000 ký tự | `400` field `lines[i].overrideReason` |
| vượt định mức + `supplemental: false` | `409 OVER_BOM_APPROVAL_REQUIRED` field `lines[i].quantity` (dòng đầu tiên của vật tư vượt) |
| vượt định mức + `supplemental: true` **hoặc vắng** | `201`, cả phiếu `PENDING_APPROVAL`, mọi dòng của vật tư vượt `overIssue: true` |
| phiếu chờ, nguồn tự do không đủ (cộng các dòng cùng nguồn) | `409 INSUFFICIENT_AVAILABLE_STOCK` field `lines[i].quantity` |
| trong định mức | `201`, `POSTED`, ghi tồn ngay (cả phiếu bổ sung) |

---

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /work-orders/{W}/material-issues` | không gửi `expectedWorkOrderVersion` | `201` | `400 VALIDATION_ERROR` field `expectedWorkOrderVersion` | **Bắt buộc mới** | gửi `version` của lệnh vừa đọc |
| `POST .../material-issues/{I}/approve`, `/reject` | body `{reason}` cũ | `200` | `400` field `expectedWorkOrderVersion` / `expectedIssueVersion` | **Bắt buộc mới** | gửi cả hai version |
| như trên | version lệch | `200` | `409 CONCURRENT_MODIFICATION` | **Bắt buộc mới** | đọc lại rồi gửi lại |
| `POST .../approve` | tự duyệt phiếu của mình, không có `OVERRIDE` | `200` | `403 PERMISSION_DENIED` | **Chặn mới** | ẩn nút duyệt khi người xem là người tạo và không có `OVERRIDE` |
| `POST .../reject` | lý do 1–4 ký tự | `200` | `400` field `reason` | **Chặn mới** | kiểm 5–2000 trên form |
| `POST /work-orders/{W}/material-issues` | `supplemental: false` + vượt định mức | (field chưa có) | `409 OVER_BOM_APPROVAL_REQUIRED` | **Mới** | chuyển sang phiếu bổ sung có lý do |
| như trên | hai dòng cùng vật tư, cộng lại vượt định mức | dòng thứ hai `409` giữa chừng | cả phiếu chờ duyệt (`201`) | **Đổi hành vi** | không — tự nhiên hơn |
| như trên | phiếu thường không reservation, kho khác kho của dòng | `201` | `422` field `lines[i].warehouseId` | **Chặn mới** | dùng phiếu bổ sung để lấy kho khác |
| như trên | phiếu chờ mà nguồn không đủ tồn | `201` (chờ, rồi duyệt mới hỏng) | `409` field `lines[i].quantity` | **Chặn sớm** | báo ngay trên form |
| như trên | `quantity` có chữ số lẻ thứ 7 / hơn 500 dòng | lưu / nhận | `400` | **Chặn mới** | giới hạn 6 chữ số lẻ, 500 dòng |
| `POST /work-orders/{W}/cancel` | lý do 1–4 ký tự | `200` | `400` field `reason` | **Chặn mới** | kiểm 5–2000 |
| `GET /material-issues`, `GET /work-orders/{W}/material-issues` | người chỉ có `APPROVE`/`OVERRIDE` | `403` | `200` | **Nới lỏng** | không |

Additive: 3 endpoint, 5 field phiếu, `search` + khoảng ngày trên list. **Thứ tự deploy: FE trước hoặc cùng lúc** — BE không nhận cả hai dạng body
duyệt/từ chối. FE gửi sẵn được field mới ngay: BE bản cũ bỏ qua field lạ.

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Môi trường dev, cổng 8090, 19/10/2026 ~22:09 giờ VN, lượt `220914`. Token che. Lệnh `WO-220914`: kế hoạch 10, định mức 20 kg `RM-220914`, kho của dòng
`RAW-220914` (30 kg, giữ 20), kho khác `RAW2-220914` (50 kg). Response dài được lược phần lặp, đánh dấu `…`.

### 4.1 Ứng viên nguồn

`GET /api/v1/work-orders/7bde537d-9053-410f-b2cd-a40d2185f954/material-issue-candidates` ⇒ `200`, `X-Trace-Id: 2af398be5b074027`: một dòng
`sourceType: "RESERVATION"`, `availableQuantity: 20`, `suggestedQuantity: 20`.

`…?mode=SUPPLEMENTAL&componentLineId=d814b20c-519e-4948-91f6-f78319d2240f` ⇒ `200`, `X-Trace-Id: ea377ed061694e08`:
```json
{"code":"SUCCESS","result":{"content":[{"componentLineId":"d814b20c-519e-4948-91f6-f78319d2240f","lineNo":10,"itemId":"88259902-dff3-42cf-ab5b-ba7ec09c797a","itemCode":"RM-220914","itemName":"Raw material","uom":"KG","requiredQuantity":20.000000,"grossIssuedQuantity":0.000000,"sourceType":"FREE_STOCK","reservationId":null,"warehouseId":"6b99a4d3-4850-4f29-816d-a20e005d44ae","warehouseCode":"RAW-220914","lotId":null,"lotCode":null,"lotExpiresAt":null,"availableQuantity":10.000000,"eligible":true,"ineligibleReason":null,"suggestedQuantity":null,"asOf":"2026-10-19T15:09:18.564937Z"},{"…":"…","warehouseCode":"RAW2-220914","availableQuantity":50.000000,"suggestedQuantity":null}],"page":0,"size":20,"totalElements":2,"totalPages":1,"first":true,"last":true},"message":"OK"}
```
`mode=ALL` ⇒ `400 VALIDATION_ERROR` field `mode`.

### 4.2 Phiếu thường

```http
POST /api/v1/work-orders/7bde537d-9053-410f-b2cd-a40d2185f954/material-issues
Idempotency-Key: db7651b7-50d0-46f0-b9d0-356d52af3e04
```
```json
{"expectedWorkOrderVersion": 3, "lines": [{"componentLineId": "d814b20c-519e-4948-91f6-f78319d2240f", "reservationId": "fb98dc12-bd4f-489f-a5b9-b746d91a58cd", "warehouseId": "6b99a4d3-4850-4f29-816d-a20e005d44ae", "quantity": 8}]}
```
`201`, `X-Trace-Id: f68bdd563dd648b6` — phần đuôi response (nguyên văn):
```json
{"…":"…","status":"POSTED","…":"…","version":0,"workOrderVersion":4,"supplemental":false,"requestedByUserId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","cancellationReason":null}
```
Không gửi `expectedWorkOrderVersion` ⇒ `400` field `expectedWorkOrderVersion` (`X-Trace-Id: 078bce3f540f4ed1`).

Dòng không reservation lấy từ `RAW2` (kho khác kho của dòng) ⇒ `422`, `X-Trace-Id: 5d084fcc5d1c4df4`:
```json
{"code":"RESOURCE_SCOPE_MISMATCH","message":"lines[0].warehouseId: the component is issued from warehouse RAW-220914 only; use a supplemental issue for another warehouse","errors":[{"field":"lines[0].warehouseId","message":"the component is issued from warehouse RAW-220914 only; use a supplemental issue for another warehouse"}]}
```

### 4.3 Vượt định mức với phiếu thường; nguồn không đủ

Còn 12 kg trong định mức. `{"expectedWorkOrderVersion": 4, "supplemental": false, "lines": [10 kg từ RAW, 5 kg từ RAW]}` ⇒ `409`, `X-Trace-Id: ecbe2b83ef3a41e3`:
```json
{"code":"OVER_BOM_APPROVAL_REQUIRED","message":"lines[0].quantity: goes over the BOM requirement; send it as a supplemental issue with a reason","errors":[{"field":"lines[0].quantity","message":"goes over the BOM requirement; send it as a supplemental issue with a reason"}]}
```
Phiếu bổ sung 40 + 20 kg từ `RAW2` (có 50; mỗi dòng đủ, cộng lại không đủ) ⇒ `409`, `X-Trace-Id: 80d0b195e2e74615`:
```json
{"code":"INSUFFICIENT_AVAILABLE_STOCK","message":"lines[0].quantity: the source holds less free stock than requested","errors":[{"field":"lines[0].quantity","message":"the source holds less free stock than requested"}]}
```
Phiếu bổ sung nêu `reservationId` ⇒ `400` field `lines[0].reservationId`; thiếu `reasonCode` ⇒ `400` field `lines[0].reasonCode`.

### 4.4 Phiếu bổ sung thành phiếu chờ

Người dùng `g8b.op.220914` (role OPERATOR, phạm vi nhà máy):
```json
{"expectedWorkOrderVersion": 4, "supplemental": true, "note": "Sheet damaged at cutting", "lines": [{"componentLineId": "d814b20c-519e-4948-91f6-f78319d2240f", "warehouseId": "31145b52-ef56-49c5-a980-d5ab77789e75", "quantity": 7, "reasonCode": "DAMAGE", "overrideReason": "Two sheets bent in the press"}, {"componentLineId": "d814b20c-519e-4948-91f6-f78319d2240f", "warehouseId": "31145b52-ef56-49c5-a980-d5ab77789e75", "quantity": 7, "reasonCode": "DAMAGE", "overrideReason": "Two sheets bent in the press"}]}
```
`201`, `X-Trace-Id: 2862dc9f8fc14589`: `"status":"PENDING_APPROVAL"`, `"postedAt":null`, mỗi dòng `"stockMovementId":null,"overIssue":true` — 7 và 7
đều nằm trong 12 còn lại nhưng cộng lại 14 thì vượt, nên **cả phiếu** chờ.

### 4.5 Chi tiết phiếu chờ

`GET /api/v1/material-issues/e4ba2510-efa5-42d3-bdfb-b9516b1f48ee` ⇒ `200`, `X-Trace-Id: 74146d3fb9ee4da1`, phần ngữ cảnh (nguyên văn):
```json
"decisionContext":[{"componentLineId":"d814b20c-519e-4948-91f6-f78319d2240f","itemId":"88259902-dff3-42cf-ab5b-ba7ec09c797a","itemCode":"RM-220914","uom":"KG","requiredQuantity":20.000000,"grossIssuedQuantity":8.000000,"batchRequestedQuantity":14.000000,"extraRequestedQuantity":2.000000,"currentAvailability":50.000000,"asOf":"2026-10-19T15:09:19.540965Z"}]
```
Tồn `RAW2` đọc lại vẫn `50` — phiếu chờ không giữ tồn.

### 4.6 Huỷ phiếu của người tạo

Quản trị huỷ phiếu của người khác ⇒ `403`, `X-Trace-Id: 383d8283eb4d42fd`:
```json
{"code":"PERMISSION_DENIED","message":"Only the person who made the request can cancel it; a manager rejects it instead"}
```
Người tạo, lý do `" no "` ⇒ `400`, `X-Trace-Id: bc1ecaab069e4b30`:
```json
{"code":"VALIDATION_ERROR","message":"reason: must be between 5 and 2000 characters","errors":[{"field":"reason","message":"must be between 5 and 2000 characters"}]}
```
`expectedIssueVersion` sai ⇒ `409 CONCURRENT_MODIFICATION`. Đúng:
```json
{"expectedWorkOrderVersion": 5, "expectedIssueVersion": 0, "reason": "Found spare sheets"}
```
`200`, `X-Trace-Id: 4b41bb6d06fb4448`, đuôi response:
```json
{"…":"…","status":"CANCELLED","…":"…","decidedAt":"2026-10-19T15:09:20.085918800Z","decidedBy":"e9a1d42e-68fc-464a-8586-3f969f4d6e78","rejectionReason":null,"version":1,"workOrderVersion":6,"supplemental":true,"requestedByUserId":"e9a1d42e-68fc-464a-8586-3f969f4d6e78","cancellationReason":"Found spare sheets"}
```
Gửi lại cùng `Idempotency-Key` ⇒ `200`, cùng phiếu, `version` vẫn `1`.

### 4.7 Duyệt kiểm lại tồn; từ chối

Người tạo phiếu chờ thứ hai (14 kg từ `RAW2`), rồi kho `RAW2` bị điều chỉnh −45 (còn 5). Duyệt
`{"expectedWorkOrderVersion": 7, "expectedIssueVersion": 0}` ⇒ `409`, `X-Trace-Id: fc3cc88663a04b31`:
```json
{"code":"INSUFFICIENT_AVAILABLE_STOCK","message":"Insufficient stock for this operation"}
```
Đọc lại: phiếu vẫn `PENDING_APPROVAL`, `version` không đổi, không dòng nào có `stockMovementId`. Từ chối không lý do ⇒ `400 APPROVAL_REASON_REQUIRED`;
có lý do ⇒ `200`, `"status":"REJECTED","rejectionReason":"Stock was scrapped"`.

### 4.8 Tự duyệt; duyệt đúng

Người dùng `g8b.apr.220914` có `MANAGE` + `APPROVE` nhưng không có `OVERRIDE`, tạo phiếu chờ rồi tự duyệt ⇒ `403`, `X-Trace-Id: 44c8b7368f3043f5`:
```json
{"code":"PERMISSION_DENIED","message":"Approving your own material issue request also requires PERM_MATERIAL_ISSUE_OVERRIDE"}
```
Thiếu `expectedIssueVersion` ⇒ `400` field `expectedIssueVersion`. Quản trị duyệt
`{"expectedWorkOrderVersion": 9, "expectedIssueVersion": 0, "reason": "Bent sheets confirmed"}` ⇒ `200`, `X-Trace-Id: d282a8ac48c042d4`:
`"status":"POSTED"`, mỗi dòng có `stockMovementId`; `RAW2` 50 → 36, đã cấp gross 8 → 22. Chi tiết phiếu đã duyệt: `extraRequestedQuantity: null`.

### 4.9 Danh sách

`GET /api/v1/material-issues?plantId=a60629fc-c4f2-43af-b076-b3baf7a5d5b8&search=mi-aa3b569c` ⇒ `200`, `X-Trace-Id: 3abcfe239474481a`, đúng một phiếu
`MI-AA3B569C`. `fromDate`/`toDate` = hôm nay (giờ nhà máy) ⇒ đủ 4 phiếu; `2020-01-01..2020-01-02` ⇒ rỗng; `X-Plant-Id` lệch ⇒ `409 STATE_CONFLICT`.

---

## §5 — Envelope và hợp đồng lỗi

Envelope như mọi phần trước: `{code, result, message, errors?}`; FE rẽ nhánh theo `code`; `errors` chỉ có khi lỗi theo field. **Không mã lỗi mới.**

| Tình huống | HTTP | `code` | `errors[]`? |
|---|---|---|---|
| thiếu version lệnh / phiếu | 400 | `VALIDATION_ERROR` | ✅ `expectedWorkOrderVersion` / `expectedIssueVersion` |
| version lệch | 409 | `CONCURRENT_MODIFICATION` | — |
| quy tắc dòng (§2.3) | 400 / 422 | `VALIDATION_ERROR` / `RESOURCE_SCOPE_MISMATCH` | ✅ `lines[i].…` |
| vượt định mức với `supplemental: false` | 409 | `OVER_BOM_APPROVAL_REQUIRED` | ✅ `lines[i].quantity` |
| nguồn không đủ lúc tạo phiếu chờ | 409 | `INSUFFICIENT_AVAILABLE_STOCK` | ✅ `lines[i].quantity` |
| tồn hụt lúc duyệt | 409 | `INSUFFICIENT_AVAILABLE_STOCK` | — |
| tự duyệt thiếu `OVERRIDE`; huỷ phiếu người khác | 403 | `PERMISSION_DENIED` | — |
| từ chối / huỷ thiếu lý do | 400 | `APPROVAL_REASON_REQUIRED` | — |
| lý do ngoài 5–2000 | 400 | `VALIDATION_ERROR` | ✅ `reason` |
| duyệt/từ chối/huỷ phiếu không còn chờ | 409 | `STATE_CONFLICT` | — |
| cùng `Idempotency-Key`, khác payload | 409 | `IDEMPOTENCY_CONFLICT` | — |
| `mode` sai | 400 | `VALIDATION_ERROR` | ✅ `mode` |

Mã FE đề xuất ánh xạ: `OVER_BOM_REQUIRES_SUPPLEMENTAL` → `OVER_BOM_APPROVAL_REQUIRED`; `MATERIAL_SOURCE_INVALID` → `RESOURCE_SCOPE_MISMATCH` (kho) /
`VALIDATION_ERROR` (reservation trên phiếu bổ sung); `INSUFFICIENT_STOCK` → `INSUFFICIENT_AVAILABLE_STOCK`; `SELF_APPROVAL_FORBIDDEN` →
`PERMISSION_DENIED`; `ISSUE_NOT_PENDING` → `STATE_CONFLICT`; `REASON_REQUIRED` → `APPROVAL_REASON_REQUIRED` / `VALIDATION_ERROR` field `reason`.

---

## §6 — Quyền, phạm vi và giới hạn đã biết

**Không quyền mới.** `ADMIN`/`MANAGER` có cả ba quyền cấp vật tư; `OPERATOR` có `PERM_MATERIAL_ISSUE_MANAGE` (tạo phiếu, huỷ phiếu của mình, xem
ứng viên) — không duyệt được.

Giới hạn đã biết:

1. **Vắng `supplemental` = hành vi cũ** (vượt định mức ⇒ phiếu chờ) — để client cũ không vỡ. Muốn chặn phiếu thường vượt định mức, FE gửi
   `supplemental: false` tường minh.
2. **Phiếu bổ sung trong định mức được ghi tồn ngay** (không chờ duyệt) — chỉ phần vượt định mức mới cần duyệt.
3. **Lô chỉ nêu bằng mã** (không có `lotId`) không được kiểm tồn sớm lúc tạo phiếu chờ và tính `0` trong `currentAvailability`; duyệt vẫn kiểm.
4. **Kiểm tồn sớm không giữ chỗ:** hai phiếu chờ cùng nguồn có thể cùng qua lúc tạo; người duyệt sau gặp `409` nếu tồn đã hết (Q10).
5. **Độ chính xác thời gian:** `postedAt`/`requestedAt`/`decidedAt` trong response của chính lệnh vừa ghi có thể in tới 100 ns
   (`…758516100Z`), đọc lại in µs — có từ trước, toàn hệ thống.
6. **Số trong response lệnh tạo phiếu** lặp lại cách viết của request (`"quantity":8`), đọc lại in `8.000000` — có từ trước.
7. **Ứng viên nguồn phân trang trong bộ nhớ** (một lệnh có ít reservation/tồn); `totalElements` đúng.

---

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — **1816 case unit + 258 case IT, failures = 0, errors = 0** (sau v1: 1785 + 256) |
| Kiểm tra đột biến | 20 lỗi cố ý cấy vào code (bỏ kiểm version, phiếu thường tự thành phiếu chờ, xét vượt từng dòng, bỏ kiểm tồn sớm, kiểm tồn không cộng dòng cùng nguồn, cho tự duyệt, cho người khác huỷ, gửi lại sau version, bỏ `cast` ở tìm kiếm, khoảng ngày lệch, tăng version hai lần, …) — **20/20 bị test bắt**. Hai lỗi cấy đầu tiên lọt qua (cộng dòng cùng nguồn; gửi lại lệnh duyệt) — đã thêm test rồi chạy lại |
| Nâng cấp CSDL | `V80` chạy sạch trên CSDL dev có dữ liệu demo |
| HTTP thật | dev, Postgres + Redis thật, cổng 8090, 19/10/2026 ~22:09 giờ VN, lượt `220914`: **32/32 kiểm tra đạt** (mẫu §4 lấy từ chính lượt này). Lượt `220136` trước đó tìm ra lỗi tăng version hai lần (§1 #3) |

Phạm vi HTTP đã thử: A09 (một phần — cấp 8 từ reservation), A10, A11, A12, A14, A15 (nhánh bị từ chối + người khác duyệt), A38 (gửi lại huỷ),
A40 (tìm kiếm + khoảng ngày).

**Chưa nghiệm thu:**

- A13 (phiếu chờ không reservation duyệt sau khi reservation khác đã dùng hết) — có test đơn vị, chưa chạy qua HTTP.
- A16 (nguồn `REWORK` khác lệnh) — kiểm `reasonCode` đã có test; đối chiếu nguồn của lệnh khác vốn có từ trước, chưa chạy lại qua HTTP.
- A17 (dòng sau lỗi ⇒ rollback cả phiếu) — một transaction như trước, chưa thử qua HTTP.
- A37 (hai người duyệt cùng lúc qua HTTP) — chỉ chứng minh bằng kiểm version, chưa đo đồng thời qua HTTP.

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Phần sau:** v3 công đoạn (đầu vào / chuyển giao / xử lý lại / vật tư thực dùng / hoàn thành) · v4 dở dang + hoàn vật tư + 3 quyền mới.

**FE cần làm:**

1. Gửi `expectedWorkOrderVersion` khi tạo phiếu; body duyệt/từ chối/huỷ `{expectedWorkOrderVersion, expectedIssueVersion, reason}`.
2. Form cấp: phiếu thường lấy từ ứng viên `NORMAL`; phiếu bổ sung từ ứng viên `SUPPLEMENTAL` + `supplemental: true` + lý do từng dòng.
3. Màn duyệt: dùng `GET /material-issues/{I}`; ẩn nút duyệt khi người xem là người tạo mà không có `OVERRIDE`; nút "Huỷ yêu cầu" chỉ cho người tạo.
4. Kiểm lý do 5–2000 ký tự trên mọi form lý do.
5. Chạy A09–A15, A38, A40 trên bản BE đã deploy và báo kết quả.

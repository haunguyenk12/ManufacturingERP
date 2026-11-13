# BE trả lời — Giai đoạn 9: Nhập thành phẩm và kiểm tra chất lượng

**Trả lời cho:** `FE_HANDOFF_GD_09_2026-10-15.md` (v1, P1) · **Ngày gửi:** 23/10/2026 · **Bản:** v3 — **phần 3/3: QC theo lượng nhiều lượt (`G9c`)**
**Trạng thái:** BE đã triển khai và tự nghiệm thu trên môi trường dev. Bản này **bổ sung** v1 (`BE_RESPONSE_GD_09_2026-10-21.md`) và v2 (`BE_RESPONSE_GD_09_2026-10-23_v2.md`).
**Phía BE đã đủ cả 3 phần của Giai đoạn 9.** Task chưa đóng: FE cần chạy A01–A40 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Một phiếu nhập đã duyệt nay được QC nhiều lượt theo lượng:**
- mỗi lượt ghi lượng đạt + lượng loại, là bản ghi bất biến;
- hàng theo lô: phần đạt sang lô con `AVAILABLE`, phần loại sang lô con `REJECTED`, phần còn chờ ở lại lô gốc `HOLD`;
- hàng không theo lô: chỉ phần đạt rời vùng chờ;
- tồn thực tế không đổi;
- endpoint QC nguyên phiếu cũ vẫn chạy, ghi vào **cùng** sổ.

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R09 | accepted ≥ 0, rejected ≥ 0, 0 < tổng ≤ chờ; loại cần lý do; quyết định bất biến | 🟡 **Đã làm** | Lượt `094029`: 0/0 ⇒ 400; loại không lý do ⇒ 400; 4+2 trên 5 ⇒ 409 (§4.2). Không có `PATCH`/`DELETE` quyết định |
| R10 | Hàng theo lô tạo lô con, phần chờ ở lại lô gốc; hàng không lô không có lô giả | 🟡 **Đã làm** | 3/1 ⇒ `-A1` 3 + `-R1` 1, lô gốc còn 1 `HOLD` (§4.1); không lô ⇒ `outputLots: []` (§4.4) |
| R11 | QC giữ tồn thực tế, không nhập lần hai, không mở lại trần | ✅ **Đã đúng** | Tồn thực tế 5 trước và sau; phần loại không đổi `completedQuantity` hay trần nhập |
| R12 | Không đi vòng QC bằng lệnh kho chung | 🟡 **Đã làm phần đổi trạng thái lô** · ⚠️ một lệch | Đổi tay lô gốc khi còn chờ ⇒ `409 LOT_NOT_ELIGIBLE`; lô con bị loại ⇒ `409`. Lệch: xem §6 |
| R23 | Giá trị unknown ≠ 0 | ⏳ **Hoãn, trả trạng thái** | `valuationStatus: "PENDING_COSTING"`, không có field tiền nào |
| R24 | API cũ không đổi nghĩa ngầm | 🟡 **Đã làm** | `qc-disposition` cũ quyết **phần còn chờ** một phía, cùng sổ (§4.3) |

### Trả lời Q (những câu thuộc phần này)

| Q | Trả lời |
|---|---|
| Q15 | **Có.** Mọi lượt QC trên hàng theo lô đều tạo lô con, kể cả lượt toàn đạt/toàn loại: `<lô>-A<n>` / `<lô>-R<n>`, `n` là số đầu tiên chưa có lô nào của vật tư dùng (so không phân biệt hoa thường). Lô con mang `parentLotId` và hạn của lô gốc. **Ngoại lệ:** endpoint cũ `qc-disposition` trên phiếu **chưa ai quyết** vẫn đổi trạng thái **cả lô gốc**, không tạo lô con (giữ client cũ) |
| Q16 | **Không có vùng chờ riêng từng phiếu.** Hàng không theo lô dùng vùng chờ chung của vật tư–kho; quyết định trả `qualityBucketId` = id dòng phiếu để FE biết phần đó thuộc phiếu nào. Phần đạt rời vùng chờ, phần loại ở lại — màn hình tồn sẽ cộng "chờ kiểm" với "đã loại" thành một số |
| Q17 | **Có.** Phần loại không mở lại trần nhập; `completedQuantity` không đổi |
| Q18 | **Có.** Phần loại vẫn còn trong tồn thực tế, không dùng được; BE không tự ghi giảm |
| Q19 | **Có.** Quyết định bất biến, không sửa/xoá |
| Q20 | **Có.** `qualityState` độc lập với `status`: `NOT_STARTED` / `PARTIAL` / `ACCEPTED` / `REJECTED` / `MIXED`, `null` khi chưa duyệt. Còn chờ là `PARTIAL` kể cả khi đã có phần loại |
| Q21 | **Có.** Bộ lọc `hasQcDecision=true` gồm cả phiếu kiểm dở; `pendingQc=true` là hàng đợi QC |
| Q24 | **Có.** Lô hết hạn: chỉ được loại (có lý do); đạt ⇒ `409 LOT_NOT_ELIGIBLE` field `acceptedQuantity` |
| Q26 | **Giá trị chưa tính:** `valuationStatus = "PENDING_COSTING"` trên mọi phiếu |
| Q29 | **Không cần version API riêng.** Endpoint cũ được điều phối trên cùng sổ: phiếu chưa ai quyết ⇒ như cũ; đã quyết một phần ⇒ quyết phần còn chờ; hết chờ ⇒ `409 STATE_CONFLICT`. Endpoint cũ không bao giờ ghi đè quyết định đã có |
| Q22 | Trả lời ở v1 §6: đọc bằng quyền QC chỉ thấy phiếu `APPROVED` |

---

## §1 — Nguyên nhân thật

Trước bản này, phiếu nhập chỉ chứa **một** kết quả QC (`qcResult` + `qcReason`/`qcAt`/`qcBy`). Bảng `quality_dispositions` là "một dòng cho mỗi lô", không có cột lượng. Vì vậy trạng thái "đạt 3 / loại 1 / còn chờ 1" không có chỗ lưu, và lượt QC thứ hai bị chặn. Bảng mới mang lượng từng lượt; dòng phiếu mang tổng đạt + tổng loại, CSDL ép `đạt + loại ≤ lượng nhập`.

---

## §2 — Thay đổi contract

### 2.1 Endpoint

| Method | Path | Quyền | Ghi chú |
|---|---|---|---|
| `POST` | `/v1/work-orders/{W}/production-receipts/{R}/qc-decisions` | `PERM_QUALITY_DISPOSITION` | **Mới.** `Idempotency-Key` tuỳ chọn |
| `GET` | `/v1/work-orders/{W}/production-receipts/{R}/qc-decisions` | như chi tiết phiếu | **Mới.** Mới nhất trước; `page`, `size` |
| `GET` | `/v1/production-receipts` | như v1 | **Thêm bộ lọc** `qualityState`, `pendingQc`, `hasQcDecision` |
| `POST` | `…/{R}/qc-disposition` | như cũ | Request không đổi; nghĩa: quyết toàn bộ phần còn chờ (§4.3) |

Body `POST …/qc-decisions`:

```json
{"expectedVersion": 2, "expectedWorkOrderVersion": 7, "acceptedQuantity": 3, "rejectedQuantity": 1, "reason": "One unit out of tolerance"}
```

- Cả hai lượng **bắt buộc** (0 được), tối đa 6 chữ số lẻ.
- Tổng > 0 và ≤ phần chờ.
- `reason`: bắt buộc khi có loại, 5–2000 ký tự; tuỳ chọn khi chỉ đạt.

Response: `{"receipt": <phiếu như chi tiết>, "qcDecision": <quyết định>}`. Khác ví dụ J6: BE trả phiếu đầy đủ trong `receipt` thay vì gộp phẳng các field phiếu.

### 2.2 Field mới

Trên **mọi** response phiếu nhập:

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `qualityState` | string \| null | Xem Q20 |
| `acceptedQuantity` / `rejectedQuantity` | number | Tổng các lượt |
| `pendingQcQuantity` | number | `quantity − đạt − loại` khi `APPROVED`; 0 khi chưa duyệt |
| `valuationStatus` | string | Luôn `PENDING_COSTING` |

⚠️ `qcResult` cũ nay chỉ là tóm tắt: `AVAILABLE` khi hết chờ và toàn đạt, `REJECTED` khi toàn loại, **`null` khi `PARTIAL` hoặc `MIXED`**.

Quyết định (`qcDecision`, phần tử của `GET …/qc-decisions`): `qcDecisionId`, `receiptId`, `receiptCode`, `receiptLineId`, `acceptedQuantity`, `rejectedQuantity`, `reason`, `actorId`, `actorName`, `decidedAt`, `qualityBucketId` (id dòng phiếu khi hàng không theo lô, `null` khi có lô), `outputLots[]` (`lotId`, `lotNumber`, `parentLotId`, `status`, `quantity` = lượng **quyết định** đưa vào lô, `expiresAt`, `stockMovementId`), `stockMovementIds[]`.

### 2.3 Dữ liệu cũ

Phiếu đã QC qua endpoint cũ trước bản này: BE ghi tổng lên dòng phiếu và **một** dòng quyết định, lấy từ chính các cột đã lưu (`qc_result`, `qc_reason`, `qc_at`, `qc_by`). Dòng đó nêu lô của phiếu (cả lô đã đổi trạng thái) và không có biến động tách lô. Trên DB dev: 6 phiếu đã QC ⇒ 6 dòng quyết định, tổng khớp lượng.

---

## §3 — Breaking change

| # | Ở đâu | Trước | Sau |
|---|---|---|---|
| 1 | `qcResult` trên phiếu đã có quyết định | Luôn có giá trị sau QC | `null` khi `PARTIAL`/`MIXED` — đọc `qualityState` |
| 2 | `qc-disposition` trên phiếu đã quyết một phần | Trạng thái này chưa tồn tại | Quyết phần còn chờ, tách lô con |
| 3 | Đổi trạng thái tay lô con bị QC sản xuất loại | (chưa có lô con) | `409 LOT_NOT_ELIGIBLE` |

Additive: 2 endpoint, 3 bộ lọc, 5 field.

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Lượt smoke `094029`, DB dev, cổng 8090. Phiếu `PR-1813D512` nhập 5 vào lô `GD9C-094029-01` (`HOLD`, hạn 2027-01-21 giờ VN).

### 4.1 QC 3 đạt / 1 loại

`POST …/production-receipts/bc9076fe-…/qc-decisions` · trace `79e6394c74484560`

```json
{"expectedVersion": 2, "expectedWorkOrderVersion": 7, "acceptedQuantity": 3, "rejectedQuantity": 1, "reason": "One unit out of tolerance"}
```
```json
"receipt":{… "qualityState":"PARTIAL","acceptedQuantity":3.000000,"rejectedQuantity":1.000000,"pendingQcQuantity":1.000000,"valuationStatus":"PENDING_COSTING"},
"qcDecision":{"qcDecisionId":"7fa5ac94-00c3-4bb4-ad97-4969626a0fc4","receiptCode":"PR-1813D512","acceptedQuantity":3,"rejectedQuantity":1,
  "reason":"One unit out of tolerance","actorName":"admin","decidedAt":"2026-10-23T02:40:31.817273Z","qualityBucketId":null,
  "outputLots":[
    {"lotNumber":"GD9C-094029-01-A1","parentLotId":"d2c8f361-2363-4289-860e-6d5525ce2d7f","status":"AVAILABLE","quantity":3,"expiresAt":"2027-01-20T17:00:00Z", …},
    {"lotNumber":"GD9C-094029-01-R1","parentLotId":"d2c8f361-2363-4289-860e-6d5525ce2d7f","status":"REJECTED","quantity":1,"expiresAt":"2027-01-20T17:00:00Z", …}],
  "stockMovementIds":["e63437d9-…","13ffc068-…"]}
```

Tồn sau đó: lô gốc 1, `-A1` 3 (khả dụng 3), `-R1` 1 (khả dụng 0) — tổng vẫn 5. Version lệnh sản xuất 7 → 8. Gửi lại cùng key + body ⇒ cùng `qcDecisionId`, không có lô mới; cùng key khác body ⇒ `409 IDEMPOTENCY_CONFLICT`.

### 4.2 Lỗi

| Trường hợp | Trace | Kết quả |
|---|---|---|
| 0/0 | `f9aa0472758d4a24` | `400 VALIDATION_ERROR` field `acceptedQuantity` |
| Loại 1 không lý do | `f12e9b764ecc407f` | `400 APPROVAL_REASON_REQUIRED` |
| 4 + 2 khi chờ 5 | `d7022209e1c14da4` | `409 PLANNED_QUANTITY_EXCEEDED` field `acceptedQuantity`, message `accepted + rejected exceeds what is still pending QC: 5` |
| `expectedVersion` cũ | `592595611e4c4ffc` | `409 CONCURRENT_MODIFICATION` |
| `1.0000001` | `37e2d7849c99451c` | `400` field `acceptedQuantity` |
| Đổi tay lô gốc khi còn chờ 1 | `cb924db44a1346c1` | `409 LOT_NOT_ELIGIBLE` |
| Đổi tay lô `-R1` | `80144dad711a4a8c` | `409 LOT_NOT_ELIGIBLE` |
| Đạt lô hết hạn | `1ec0fdb83fc142d4` | `409 LOT_NOT_ELIGIBLE` field `acceptedQuantity`, message `… expired at …; it can only be rejected` |

### 4.3 Endpoint cũ quyết nốt phần còn chờ

`POST …/qc-disposition` `{"result": "AVAILABLE", "reason": "The rest passed"}` · trace `9c24a58691b44afb`
⇒ `"qualityState":"MIXED","acceptedQuantity":4.000000,"rejectedQuantity":1.000000,"pendingQcQuantity":0.000000`, `qcResult: null`, thêm lô `-A2` 1.
Gọi lại ⇒ `409 STATE_CONFLICT`. `GET …/qc-decisions` ⇒ 2 dòng, mới nhất trước: 1/0 rồi 3/1.

### 4.4 Hàng không theo lô

Phiếu `PR-F58D8581` nhập 4, QC 3/1 (trace `bd3014c7e2d14351`) ⇒ `"qualityState":"MIXED"`, `"outputLots":[]`, `"qualityBucketId":"04b7d6b1-101c-4f87-82a8-899b1bf9bb44"` (id dòng phiếu). Tồn: thực tế 4 → 4, khả dụng 0 → 3, vùng chờ 4 → 1.

### 4.5 Bộ lọc

`GET /v1/production-receipts?plantId=…&workOrderId=…&qualityState=MIXED` ⇒ phiếu trên; `&hasQcDecision=true` ⇒ phiếu trên; `&pendingQc=true` ⇒ rỗng.

---

## §5 — Envelope và hợp đồng lỗi

Không mã lỗi mới. Bảng ở §4.2. `qc-disposition` khi hết chờ ⇒ `409 STATE_CONFLICT`; `qc-decisions` khi hết chờ ⇒ `409 PLANNED_QUANTITY_EXCEEDED` field `acceptedQuantity`. Hàng theo sê-ri gọi `qc-decisions` ⇒ `422 OPERATION_NOT_ALLOWED` (sê-ri giữ đường cũ, ngoài phạm vi theo §1.1 của FE).

---

## §6 — Quyền, phạm vi và giới hạn đã biết

- Ghi quyết định: `PERM_QUALITY_DISPOSITION`. Đọc lịch sử: như chi tiết phiếu.
- **Lệch 1 (có từ trước, như GĐ7):** `POST /v1/inventory/adjust` vẫn chạy trên lô `HOLD`. "Không đi vòng QC" mới đúng với **đổi trạng thái lô**, chưa đúng với điều chỉnh tồn.
- **Lệch 2:** lô gốc sau khi QC hết còn 0 và vẫn `HOLD`. Đổi trạng thái tay lô đó trả `422 NEGATIVE_QUANTITY` (trace `3f7387a01f21401d`), vì đổi trạng thái luôn ghi biến động bằng toàn bộ tồn dương của lô. Cổng QC đã mở. FE không cần thao tác gì với lô gốc rỗng.
- **Giới hạn 1:** hàng không theo lô không có vùng "đã loại" riêng (Q16).
- **Giới hạn 2:** biến động kho/WIP chưa mang `receiptId`/`qcDecisionId` riêng. Lô con mang nguồn `PRODUCTION_QC_DECISION` + id quyết định; quyết định trả `stockMovementIds`.
- **Giới hạn 3:** QC theo lượng chỉ cho phiếu một dòng (mọi phiếu từ GĐ5 về sau). Phiếu nhiều dòng rất cũ chỉ đi endpoint cũ.

---

## §7 — Bằng chứng nghiệm thu

| Hạng mục | Kết quả |
|---|---|
| `mvn -o clean verify` | **2009 unit + 283 IT, 0 lỗi** (trước bản này: 1982 + 279) |
| Migration `V85` | Chạy sạch trên DB dev có dữ liệu demo; 6/6 phiếu đã QC có dòng quyết định, tổng khớp |
| Mutation | **17/17 bị diệt** |
| Smoke HTTP thật | **24/24** lượt `094029` (lượt `094003`: 23/24, do kỳ vọng sai ở bước đổi trạng thái lô gốc rỗng — §6 lệch 2) |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

- Giá trị thành phẩm (D10, hoãn).
- Vùng "đã loại" riêng cho hàng không theo lô (Q16).
- Sửa/đảo quyết định QC (Q19).
- QC từng phần cho sê-ri.

**FE cần làm:**
1. Màn QC gọi `POST …/qc-decisions` với hai version; hàng đợi dùng `status=APPROVED&pendingQc=true`, lịch sử dùng `hasQcDecision=true`.
2. Hiển thị `qualityState` thay vì suy từ `qcResult`.
3. Hiển thị `valuationStatus = PENDING_COSTING` là "chưa tính giá", không phải 0.
4. Chạy A01–A40 và báo kết quả.

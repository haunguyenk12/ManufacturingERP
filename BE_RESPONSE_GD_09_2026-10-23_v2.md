# BE trả lời — Giai đoạn 9: Nhập thành phẩm và kiểm tra chất lượng

**Trả lời cho:** `FE_HANDOFF_GD_09_2026-10-15.md` (v1, P1) · **Ngày gửi:** 23/10/2026 · **Bản:** v2 — **phần 2/3: phân bổ theo lượt báo sản lượng + truy xuất nguồn (`G9b`)**
**Trạng thái:** BE đã triển khai và tự nghiệm thu trên môi trường dev. Bản này **bổ sung** v1 (`BE_RESPONSE_GD_09_2026-10-21.md`), không thay thế nó.
Còn lại: v3 — QC từng phần nhiều lượt. Task chưa đóng: FE cần chạy A01–A40 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Mỗi phiếu nhập nay biết nó lấy thành phẩm từ những lượt báo sản lượng nào:**
- server tự phân bổ lúc tạo phiếu và mỗi lần sửa nháp;
- chỉ lấy lượt báo của công đoạn cuối, lượt cũ nhất trước;
- không lượt nào bị giữ quá good của chính nó;
- có endpoint mới để xem nguồn của một phiếu: các lượt báo + vật tư của cả lệnh;
- phiếu tạo trước bản này báo "không rõ nguồn", BE không dựng nguồn giả.

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R05 | Nguồn không trùng, tổng = lượng phiếu, mỗi nguồn ≤ good chưa ai giữ | 🟡 **Đã làm** | Lượt `084754`: tạo 4 ⇒ `3 + 1` từ hai lượt; tạo 2 ⇒ `2` từ phần còn lại của lượt sau; sửa phiếu đầu xuống 3 ⇒ chỉ còn `3` từ lượt đầu (§4.1–§4.3) |
| R13 | Nguồn: lượt báo thật + vật tư cấp lệnh; thiếu bằng chứng thì giữ unknown | 🟡 **Đã làm** | `GET …/production-receipts/{R}/source` (§2.1, §4.5) |
| R22 | Phiếu cũ giữ nguyên, thiếu bằng chứng nguồn thì chỉ đọc / unknown | 🟡 **Đã làm phần nguồn** | `outputEvidenceKnown: false`, `contributions: null` (§4.4). Không backfill |
| R05 (kiểm lại lúc gửi/duyệt) | Nguồn kiểm lại ở submit/approve | ✅ **Không cần — nguồn không thể cũ đi** | Xem Q6 và §1 |

### Trả lời Q (những câu thuộc phần này)

| Q | Trả lời |
|---|---|
| Q6 | **Có.** BE tự phân bổ, client **không** gửi nguồn. Thứ tự: lượt cũ nhất trước theo `actualEndedAt`, trùng thì theo id — ổn định giữa các lần gọi. Phân bổ được lưu (bảng mới), không tính lại lúc đọc. Lúc gửi/duyệt BE **không** phân bổ lại và không có mã lỗi "nguồn đã đổi" — lý do ở §1 |
| Q27 | **Có.** Vật tư trả ở cấp lệnh: `materialAttribution = WORK_ORDER_LEVEL`. BE **không** gán vật tư cho từng đơn vị thành phẩm. Lệnh làm trước khi có theo dõi công đoạn (GĐ8) trả `UNKNOWN` |
| Q29 | **Lượt xử lý lại (retry) của công đoạn cuối là nguồn bình thường** — good của nó là good cuối (GĐ8 B219). Lượt của các công đoạn trước **không bao giờ** là nguồn |
| Q30 | **Có.** Phiếu cũ: `outputEvidenceKnown = false`, `outputUnknownReason` có chữ, `contributions = null`. BE không tạo dòng phân bổ giả. Phần lượng của phiếu cũ vẫn được tính trừ vào các lượt cũ nhất trước, nên phiếu mới không lấy trùng. Một **nháp** cũ được sửa sẽ có phân bổ từ lần sửa đó |
| Q15–Q22, Q24 | Trả lời ở v3 (QC từng phần) |

---

## §1 — Nguyên nhân thật

1. **Trước bản này không có gì nối phiếu nhập với lượt báo cụ thể.** Chỉ có trần tổng của lệnh. `sourceWipTraceIds` là một chuỗi mã truy vết ghi lúc duyệt, không có lượng của từng lượt.
2. **Vì sao không kiểm lại nguồn lúc gửi/duyệt** (lệch kế hoạch ban đầu của BE, có chủ đích):
   - lượt báo sản lượng không có đường sửa hay đảo;
   - mọi lệnh trên phiếu nhập đều tăng version của lệnh sản xuất ⇒ hai lần phân bổ cùng lúc không thể cùng thành công (một bên nhận `409 CONCURRENT_MODIFICATION`);
   - phần lượng tính cho phiếu cũ chỉ có thể **giảm** (khi bị huỷ/từ chối), không bao giờ tăng.

   Vì vậy phân bổ đã lưu không thể trở nên sai. Thêm bước kiểm lại là thêm code cho tình huống không xảy ra được.

---

## §2 — Thay đổi contract

### 2.1 Endpoint

| Method | Path | Ghi chú |
|---|---|---|
| `GET` | `/v1/work-orders/{W}/production-receipts/{R}/source` | **Mới.** Nguồn của một phiếu. Quyền như chi tiết phiếu (v1 §6) |

Các endpoint tạo / sửa / gửi / rút / huỷ / duyệt / từ chối: **không đổi request**.

### 2.2 Field mới trên response

Trên **mọi** response phiếu nhập (tạo, sửa, chi tiết, danh sách, các lệnh):

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `outputEvidenceKnown` | boolean | `true` nếu phiếu có phân bổ đã lưu |
| `outputUnknownReason` | string \| null | Có chữ khi `outputEvidenceKnown = false` |
| `contributions` | array \| null | `null` khi không rõ nguồn — **khác** mảng rỗng |
| `contributions[].executionId` | uuid | Lượt báo sản lượng |
| `contributions[].executionCode` | string | Mã `PE-…` của lượt |
| `contributions[].operationId` | uuid \| null | Công đoạn của lượt; `null` nếu lệnh không có công đoạn |
| `contributions[].operationSequence` | number \| null | Thứ tự công đoạn |
| `contributions[].quantity` | number | Lượng phiếu lấy từ lượt này |

Response của `…/source`: `receiptId`, `receiptCode`, `workOrderId`, `workOrderCode`, `outputEvidenceKnown`, `outputUnknownReason`, `contributions` (như trên), `materialAttribution` (`WORK_ORDER_LEVEL` | `UNKNOWN`), `materials[]`. Mỗi dòng `materials[]` có cùng hình dạng với phần vật tư của `GET /v1/work-orders/{W}/wip` (GĐ8 v4): một dòng cấp vật tư kèm `issuedQuantity` / `consumedQuantity` / `returnedQuantity` / `unusedQuantity`.

### 2.3 Dữ liệu cũ

Không backfill. Phiếu tạo trước bản này không có dòng phân bổ ⇒ `outputEvidenceKnown: false`. Số lượng, trạng thái, lịch sử của phiếu cũ không đổi.

---

## §3 — Breaking change

**Không có trên wire.** Chỉ thêm field và một endpoint. Request không đổi.

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Lượt smoke `084754`, DB dev, cổng 8090. Lệnh `WO-084754` có 2 công đoạn: Cut (10) → Weld (20). Báo Cut good 10. Báo Weld hai lượt: good **3** (kết thúc sớm hơn, `PE-3A5275A3`) rồi good **5** (`PE-F0F86C31`). Good cuối = 8. Các số dưới đây chép nguyên chuỗi từ response.

### 4.1 Tạo 4 — lấy từ lượt cũ trước, không lấy lượt Cut

`POST /api/v1/work-orders/68dbbda0-…/production-receipts` · trace `a167356937a24370`

```json
{"destinationWarehouseId": "02509744-be93-4bdc-a58b-48ec01244edc", "quantity": 4, "receivedOn": "2026-10-23", "expectedWorkOrderVersion": 10}
```
```json
"claimedQuantity":4.000000,"unclaimedReceiptQuantity":4.000000,
"outputEvidenceKnown":true,"outputUnknownReason":null,
"contributions":[
  {"executionId":"3a5275a3-dd3d-49a8-aad6-5535c870ead4","executionCode":"PE-3A5275A3","operationId":"5efde262-dc0b-4d9f-a264-886b58bd92de","operationSequence":20,"quantity":3.000000},
  {"executionId":"f0f86c31-f399-432e-8942-64e0aebc1cbd","executionCode":"PE-F0F86C31","operationId":"5efde262-dc0b-4d9f-a264-886b58bd92de","operationSequence":20,"quantity":1.000000}]
```

### 4.2 Phiếu thứ hai lấy phần còn lại

Tạo 2 · trace `c84a81731a5a4160` ⇒ `"contributions":[{"executionId":"f0f86c31-…","executionCode":"PE-F0F86C31",…,"quantity":2}]`

⚠️ Số `2` ở đây không có `.000000`: response của lệnh vừa ghi trả lượng đúng như đã tính, còn đọc lại từ CSDL thì có 6 chữ số lẻ. Cùng hiện tượng đã báo ở GĐ2 — so sánh theo giá trị số, đừng so chuỗi.

### 4.3 Sửa phiếu đầu xuống 3 — trả lại phần của chính nó rồi phân bổ lại

`PATCH …/production-receipts/aa714067-…` · trace `0139143386b3482e`

```json
{"expectedVersion": 0, "expectedWorkOrderVersion": 12, "quantity": 3, "receivedOn": "2026-10-23"}
```
```json
"version":1,"workOrderVersion":13,"claimedQuantity":5.000000,"unclaimedReceiptQuantity":3.000000,
"contributions":[{"executionId":"3a5275a3-dd3d-49a8-aad6-5535c870ead4","executionCode":"PE-3A5275A3",…,"quantity":3.000000}]
```

Phiếu thứ hai không bị đụng: đọc lại vẫn `[PE-F0F86C31 2]`. Tạo tiếp 4 khi còn 3 chưa giữ ⇒ `409 PLANNED_QUANTITY_EXCEEDED` field `quantity` (trace `0479f2c970c94d63`, message `exceeds the 3.000000 of final good, up to the plan, that no receipt has claimed yet`).

### 4.4 Phiếu cũ

Để có một phiếu "cũ" trên DB dev, BE xoá dòng phân bổ của phiếu `PR-2A3090C7` bằng SQL. Đây chỉ là dựng dữ liệu thử, API không có đường làm việc này. Sau đó:

`GET …/production-receipts/aa714067-…` · trace `e9d6e726706f48e9`
```json
"outputEvidenceKnown":false,
"outputUnknownReason":"The receipt predates the contribution ledger; the reports it drew from were not recorded",
"contributions":null
```

Tạo phiếu mới 2 (trace `a7210a1b23b54d32`) ⇒ `[PE-F0F86C31 2.000000]`. Lượng 3 của phiếu cũ được tính trừ vào lượt cũ nhất (`PE-3A5275A3`), nên phiếu mới không lấy lại lượt đó.

### 4.5 Nguồn của một phiếu đã duyệt

`GET …/production-receipts/5d6ed930-…/source` · trace `eb81beea34184d91`
```json
{"receiptId":"5d6ed930-61ff-4f9d-8f2f-19e8b182341b","receiptCode":"PR-C088D172","workOrderId":"68dbbda0-…","workOrderCode":"WO-084754",
 "outputEvidenceKnown":true,"outputUnknownReason":null,
 "contributions":[{"executionId":"f0f86c31-…","executionCode":"PE-F0F86C31",…,"quantity":3.000000}],
 "materialAttribution":"WORK_ORDER_LEVEL",
 "materials":[{"issueCode":"MI-5231299C","itemSku":"RM-084754","uom":"KG","warehouseCode":"RAW-084754", … }]}
```

Nguồn của phiếu cũ (trace `4ba121ec1d5f49ea`): `outputEvidenceKnown: false`, `contributions: null`, phần `materials` vẫn đầy đủ.

---

## §5 — Envelope và hợp đồng lỗi

Không mã lỗi mới.

| Tình huống | Mã |
|---|---|
| Tạo/sửa vượt phần chưa ai giữ | `409 PLANNED_QUANTITY_EXCEEDED` field `quantity` (như v1) |
| Hai lệnh cùng lúc trên một lệnh sản xuất | `409 CONCURRENT_MODIFICATION` (như v1) |
| `…/source` với id phiếu không thuộc lệnh / không tồn tại | `404 ENTITY_NOT_FOUND` |
| `…/source` chỉ có quyền đọc QC, phiếu chưa `APPROVED` | `403 PERMISSION_DENIED` |

---

## §6 — Quyền, phạm vi và giới hạn đã biết

- `…/source` cần một trong: `PERM_PRODUCTION_RECEIPT_READ`, `_MANAGE`, `_APPROVE`, `PERM_PRODUCTION_QC_READ`, `PERM_QUALITY_DISPOSITION`. Chỉ có quyền QC ⇒ chỉ đọc được phiếu `APPROVED`.
- **Giới hạn 1:** phiếu bị huỷ/từ chối vẫn trả `contributions` (lịch sử của nó), nhưng phần đó **không** còn bị tính là đang giữ.
- **Giới hạn 2:** vật tư chỉ ở cấp lệnh. BE không chia vật tư theo từng phiếu hay từng đơn vị.
- **Giới hạn 3:** số lẻ không cố định trên response vừa ghi (§4.2).

---

## §7 — Bằng chứng nghiệm thu

| Hạng mục | Kết quả |
|---|---|
| `mvn -o clean verify` | **1982 unit + 279 IT, 0 lỗi** (trước bản này: 1968 + 274) |
| Migration `V84` | Chạy sạch trên DB dev có dữ liệu demo |
| Mutation | **14/14 bị diệt** (một con sống lần đầu ⇒ thêm test phiếu cũ đọc chi tiết rồi chạy lại) |
| Smoke HTTP thật | **16/16** lượt `084754` (lượt `084742` dừng ở lỗi in ký tự của chính script, không phải backend) |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

- QC từng phần nhiều lượt ⇒ v3.
- Không chia vật tư cho từng phiếu/đơn vị (Q27).
- Không backfill phân bổ cho phiếu cũ (Q30).

**FE cần làm:**
1. Đọc `outputEvidenceKnown`. Khi `false`, hiển thị "không rõ nguồn", không hiển thị 0.
2. Màn hình truy xuất gọi `…/source`.
3. Không gửi nguồn trong request.

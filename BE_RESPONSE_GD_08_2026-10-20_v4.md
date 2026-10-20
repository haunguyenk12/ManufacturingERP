# BE trả lời — Giai đoạn 8: Lệnh sản xuất, cấp vật tư, công đoạn và dở dang

**Trả lời cho:** `FE_HANDOFF_GD_08_2026-10-14.md` (v1, P1) · **Ngày gửi:** 20/10/2026 · **Bản:** v4 — **phần 4/4: dở dang + hoàn vật tư (`G8d`)**
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. Bản này **bổ sung** v1 (nháp lệnh), v2 (cấp vật tư), v3 (công đoạn), không thay
chúng. Với bản này cả 4 phần của GĐ8 đã có ở BE. Chưa phải "FE nghiệm thu đạt": task chỉ đóng khi FE chạy A01–A40 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Vật tư đã cấp mà chưa dùng nay hoàn được về đúng kho và lô nó được cấp ra, có trần theo từng dòng cấp; màn hình dở dang đọc trực tiếp từ sổ cấp,
dùng, hoàn, công đoạn và phiếu nhập — chỉ số lượng, chưa có giá trị.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R15 | Dở dang tách vật tư / công đoạn / làm lại / chờ nhập kho, có đơn vị, phân biệt biết / chưa biết | 🟡 **Đã làm** | `GET /v1/work-orders/{W}/wip` (§4.4); lệnh cũ trả `null`, không trả 0 (§4.6) |
| R16 | Hoàn nguyên nguồn, trần phần chưa dùng, kiểm nguồn lúc hoàn, nguyên tử, gửi lại một phiếu | 🟡 **Đã làm** | 0.5 + 0.5 cùng dòng ⇒ một dòng hoàn 1, tồn +1, version +1; gửi lại ⇒ cùng phiếu (§4.1) |
| R17 | Hoàn không giảm "đã cấp", không khôi phục giữ tồn, không đi vòng QC | 🟡 **Đã làm** | "Đã cấp" vẫn 20 sau khi hoàn; lô đang `HOLD` ⇒ `409 LOT_NOT_ELIGIBLE` (§4.1, §4.3) |
| R13 (bổ sung) | Trần vật tư dùng = cấp − đã dùng − **đã hoàn** | 🟡 **Đã làm** | Sau khi hoàn, dùng 2 khi chỉ còn 1 ⇒ `409` field `consumption[0].quantity` (§4.2) |
| R19 (phần giá) | Giá trị theo quyền variance | ⏸ **Chưa làm (D10)** | Không trường giá trị nào trên dở dang / phiếu hoàn |
| R18 | Nguồn cung lệnh cho MRP theo sản lượng thực | ⏸ **Hoãn (D11, đã chốt)** | Nguồn cung vẫn theo kế hoạch |

### Trả lời Q (những câu thuộc phần này)

| Q | Trả lời |
|---|---|
| Q1 | **Có**, đúng ba quyền mới: `PERM_WIP_READ`, `PERM_MATERIAL_RETURN_READ`, `PERM_MATERIAL_RETURN_MANAGE` |
| Q2 | **Một phần.** Tạo phiếu hoàn và xem ứng viên hoàn cần `PERM_MATERIAL_RETURN_MANAGE` (không đòi thêm `_READ`); đọc phiếu hoàn nhận `_READ` **hoặc** `_MANAGE` |
| Q11 | **Có.** Hoàn không đổi "đã cấp" (`issuedQuantity`) của dòng vật tư — xét vượt định mức vẫn theo tổng đã cấp (§4.1) |
| Q13 | **Có.** `CONSUMED` = giữ tồn đã cấp hết; phần chưa cấp vẫn `ACTIVE`. Hoàn không đưa giữ tồn trở lại |
| Q19 | **Có.** `PERM_WIP_READ` đọc được vật tư (cấp / dùng / hoàn theo dòng cấp), công đoạn, làm lại chờ, thành phẩm chờ nhập — không mở thao tác nào |
| Q22 | **Có.** Lệnh đã báo sản lượng trước `G8c`: đã dùng / chưa dùng / có thể hoàn = `null`, hoàn ⇒ `409 PRODUCTION_INPUT_UNKNOWN` (§4.6) |
| Q23 | **Có.** Hoàn được khi lệnh `IN_PROGRESS` **hoặc** `COMPLETED` (đã kiểm sau `complete-production`) |
| Q26, Q35, Q37 | **Chưa áp dụng.** BE chưa có giá trị cấp / dùng / hoàn / dở dang (D10) — không trả trường giá trị nào, kể cả `null`. Coi như "không có quyền xem giá" |
| Q30 | **Không đổi.** Endpoint định danh bằng id (`/material-returns/{R}`, `/work-orders/{W}/…`) trả **`403`** cả khi id không tồn tại lẫn khi thuộc nhà máy khác — không lộ bản ghi, nhưng không phải `404` như FE đề xuất. Đây là hành vi chung của mọi endpoint id trong BE |
| Q34 | **Có.** `V82` chỉ cấp ba quyền cho ADMIN + MANAGER; OPERATOR và vai trò tự tạo không được cấp ngầm |
| Q36 | **Có.** Phiếu hoàn `POSTED` khi ghi, không `PATCH`/`DELETE` |

---

## §1 — Nguyên nhân thật

Không có lỗi FE báo — đây là luồng mới. Ba điều ảnh hưởng tới FE:

1. **Trần vật tư dùng khi báo sản lượng đổi** (thêm `− đã hoàn`). Trước đây chưa có hoàn nên số không đổi với mọi dữ liệu hiện có.
2. **Hoàn trả hàng về đúng kho + lô của dòng cấp**, bằng một biến động nhập kho (`RECEIVE`) có `referenceType = "MATERIAL_RETURN"` và
   `referenceId` = id phiếu hoàn. Không có loại biến động mới, không có cột liên kết mới trên biến động kho — liên kết nằm trên dòng hoàn
   (`stockMovementId`) và dòng cấp (`stockMovementId` có sẵn).
3. **Chưa có giá trị.** Mọi công thức giá trị ở §3.1 của FE (giá cấp, giá dùng, giá hoàn, phần dư lần hoàn cuối) chưa làm vì BE chưa có lớp giá tồn
   (D10, đã chốt hoãn).

Nghiệm thu HTTP và mutation lần này **không** tìm ra lỗi nào của BE. Hai mutation lần đầu "bị giết" vì lý do sai (truy vấn không khởi động được)
— đã viết lại cho đúng nghĩa và chạy lại, cả hai vẫn bị test bắt.

---

## §2 — Thay đổi contract

### 2.1 Endpoint

| Method | Path | Mới / đổi | Quyền |
|---|---|---|---|
| GET | `/api/v1/plants/{P}/wip?status&workOrderId&search&fromDate&toDate&page&size&sortBy&sortDir` | **Mới** | `PERM_WIP_READ` |
| GET | `/api/v1/work-orders/{W}/wip?section=MATERIALS\|OPERATIONS\|REWORK\|AWAITING_RECEIPT` | **Mới** | `PERM_WIP_READ` |
| GET | `/api/v1/work-orders/{W}/material-return-candidates?page&size` | **Mới** | `PERM_MATERIAL_RETURN_MANAGE` |
| POST | `/api/v1/work-orders/{W}/material-returns` | **Mới** — body `{expectedWorkOrderVersion, reason, lines[1..500]{sourceIssueLineId, quantity}}`, `Idempotency-Key` **bắt buộc** | `PERM_MATERIAL_RETURN_MANAGE` |
| GET | `/api/v1/material-returns?plantId&workOrderId&search&fromDate&toDate&page&size&sortBy&sortDir` | **Mới** | `PERM_MATERIAL_RETURN_READ` **hoặc** `_MANAGE` |
| GET | `/api/v1/material-returns/{R}` | **Mới** | `PERM_MATERIAL_RETURN_READ` **hoặc** `_MANAGE` |
| POST | `/api/v1/work-orders/{W}/production-executions` | **Đổi (trần):** `consumption[]` nay so `cấp − đã dùng − đã hoàn` | không đổi |

Danh sách dở dang: không `status` ⇒ `RELEASED`, `IN_PROGRESS`, `COMPLETED`. `search` khớp số lệnh hoặc mã sản phẩm; `fromDate`/`toDate` là ngày nhà máy trên
`createdAt` của lệnh, gồm cả hai đầu. Danh sách phiếu hoàn: `search` khớp mã phiếu (`MR-…`) hoặc số lệnh, không phân biệt hoa thường; ngày nhà máy trên
`postedAt`. Cả hai danh sách cross-check `X-Plant-Id`. Không có `status` trên danh sách phiếu hoàn — mọi phiếu đều `POSTED`.

### 2.2 Field mới trên response

**Dòng vật tư (dở dang `materials[]` và ứng viên hoàn)** — một phần tử cho mỗi dòng cấp `POSTED` của lệnh:

| Field | Kiểu | Nullable? | Nguồn | Ví dụ thật |
|---|---|---|---|---|
| `issueLineId`, `issueId`, `issueCode`, `postedAt`, `componentLineId` | UUID / text / ISO | không | dòng cấp | `"MI-3812B43B"` |
| `itemId`, `itemSku`, `itemName`, `uom`, `warehouseId`, `warehouseCode` | UUID / text | không | dòng cấp | `"RM-162845"`, `"KG"` |
| `lotId`, `lotNumber` | UUID / text | **có** — `null` khi vật tư không theo lô | dòng cấp | `null` |
| `issuedQuantity` | decimal | không | lượng dòng cấp | `20.000000` |
| `consumptionKnown` | boolean | không | `false` với lệnh báo trước `G8c` | `true` |
| `consumedQuantity` | decimal | **có** — `null` khi `consumptionKnown = false` | tổng `consumption[]` của dòng | `18.000000` |
| `returnedQuantity` | decimal | không | tổng đã hoàn của dòng | `1.000000` |
| `unusedQuantity` | decimal | **có** (như trên) | `cấp − dùng − hoàn` | `1.000000` |
| `returnableQuantity` | decimal | **có** (như trên) | = `unusedQuantity` khi nguồn nhận lại được, `0` khi không | `1.000000` |
| `eligible`, `ineligibleReason` | boolean / text | lý do `null` khi đạt | trạng thái nguồn lúc đọc: `ITEM_INACTIVE`, `WAREHOUSE_INACTIVE`, `LOT_HOLD`, `LOT_REJECTED`, `LOT_EXPIRED`, `SERIAL_TRACKED` | `true`, `null` |

**Dở dang một lệnh:** `workOrderId`, `workOrderNo`, `status`, `version`, `inputKnown`, `consumptionKnown`, `section`, `materials[]`,
`operations[]` (đúng hình dạng `GET …/operations` của v3), `rework[]`, `output`. Phần không hỏi qua `section` là `null`; phần đã hỏi mà trống là `[]`.
`rework[]` — lượt còn làm lại chờ: `{productionExecutionId, code, workOrderOperationId, operationSequence, operationName, reworkQuantity,
retryUsedQuantity, pendingReworkQuantity, actualEndedAt}`. `output`: `{uom, plannedQuantity, finalGoodQuantity, scrapQuantity, historicalReworkQuantity,
approvedReceiptQuantity, openReceiptQuantity, availableToReceipt, terminalShortfallQuantity}` — `availableToReceipt = good cuối − đã nhập kho`, phiếu
nhập nháp/chờ duyệt báo riêng ở `openReceiptQuantity` (không trừ, như trần phiếu nhập hiện có); `finalGoodQuantity`/`availableToReceipt` `null` với lệnh cũ.

**Dòng danh sách dở dang:** `workOrderId, workOrderNo, status, version, productItemId, productItemSku, productItemName, uom, plannedQuantity, inputKnown,
finalGoodQuantity, scrapQuantity, historicalReworkQuantity, pendingReworkQuantity, approvedReceiptQuantity, availableToReceipt, terminalShortfallQuantity,
createdAt`. Không có tổng vật tư ở dòng này (các vật tư khác đơn vị) — đọc theo từng lệnh.

**Phiếu hoàn:** `materialReturnId, code, workOrderId, workOrderCode, status (POSTED), reason, postedAt, postedByUserId, postedByUsername, traceId,
workOrderVersion, lines[]`; dòng: `materialReturnLineId, lineNo, sourceIssueLineId, sourceIssueId, sourceIssueCode, componentLineId, itemId, itemSku,
uom, warehouseId, warehouseCode, lotId?, lotNumber?, quantity, stockMovementId`.

### 2.3 Quy tắc hoàn (kiểm cả phiếu trước khi ghi)

| Điều kiện | Kết quả |
|---|---|
| thiếu `Idempotency-Key` | `400 MISSING_REQUIRED_FIELD` |
| thiếu `expectedWorkOrderVersion` / `lines` rỗng / hơn 500 dòng / chữ số lẻ thứ 7 | `400 VALIDATION_ERROR` theo field |
| version lệch | `409 CONCURRENT_MODIFICATION` |
| lệnh không `IN_PROGRESS`/`COMPLETED` | `409 STATE_CONFLICT` |
| lệnh báo sản lượng trước `G8c` | `409 PRODUCTION_INPUT_UNKNOWN` |
| thiếu lý do / lý do < 5 hoặc > 2000 ký tự | `400 APPROVAL_REASON_REQUIRED` / `400 VALIDATION_ERROR`, field `reason` |
| dòng cấp không phải dòng `POSTED` của chính lệnh | `422 RESOURCE_SCOPE_MISMATCH` field `lines[i].sourceIssueLineId` |
| vật tư hoặc kho nguồn ngừng hoạt động | `422 RESOURCE_INACTIVE` field `lines[i].sourceIssueLineId` |
| lô nguồn `HOLD`/`REJECTED`/hết hạn | `409 LOT_NOT_ELIGIBLE` field `lines[i].sourceIssueLineId` |
| dòng cấp theo số sê-ri | `422 OPERATION_NOT_ALLOWED` field `lines[i].sourceIssueLineId` |
| tổng các mục cùng dòng > `cấp − dùng − hoàn` | `409 PLANNED_QUANTITY_EXCEEDED` field `lines[i].quantity` (`i` = mục đầu tiên của dòng đó) |
| cùng key, cùng body | `201`, đúng phiếu cũ, không biến động thứ hai |
| cùng key, khác body | `409 IDEMPOTENCY_CONFLICT` |

---

## §3 — Breaking change

**`Wire: CÓ, hẹp`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /work-orders/{W}/production-executions` | `consumption[]` vào phần vật tư đã hoàn | (chưa có hoàn) | `409 PLANNED_QUANTITY_EXCEEDED` field `consumption[i].quantity` | **Chặn mới** | dùng `unusedQuantity` của dở dang làm trần |

Còn lại thuần additive: 6 endpoint, 3 quyền. **Thứ tự deploy:** không phụ thuộc — FE bản cũ không gọi endpoint mới.

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Môi trường dev, cổng 8090, 20/10/2026 ~16:28 giờ VN, lượt `162845`. Token che. Lệnh `WO-162845`: kế hoạch 10 `EA`, quy trình `Cut` → `Weld`, cấp 20 kg
`RM-162845` từ kho `RAW-162845` (vật tư không theo lô). `Cut`: đầu vào 10, lượt 8/1/1 dùng 16 kg, xử lý lại 1 dùng 2 kg ⇒ chưa dùng 2 kg. Response dài
được lược, đánh dấu `…`.

### 4.1 Hoàn vật tư

Hoàn 3 khi chỉ còn 2 chưa dùng (`X-Trace-Id: 730bd0aee30140fc`):

```http
POST /api/v1/work-orders/{W}/material-returns
{"reason":"Unused material back to stock","lines":[{"sourceIssueLineId":"9dc3254b-4cc7-46b7-8126-1344f645208f","quantity":3}],
 "expectedWorkOrderVersion":7}
→ 409 {"code":"PLANNED_QUANTITY_EXCEEDED","message":"lines[0].quantity: only 2.000000 of this issue line is unused",
  "errors":[{"field":"lines[0].quantity","message":"only 2.000000 of this issue line is unused"}]}
```

Hai mục 0.5 cùng dòng (`X-Trace-Id: 266431fe2a2b4056`):

```http
POST /api/v1/work-orders/{W}/material-returns
Idempotency-Key: …
{"expectedWorkOrderVersion":7,"reason":"Unused offcut back to stock",
 "lines":[{"sourceIssueLineId":"9dc3254b-…","quantity":0.5},{"sourceIssueLineId":"9dc3254b-…","quantity":0.5}]}
→ 201 {"code":"SUCCESS","result":{"materialReturnId":"89dd7262-4dc7-43ae-909e-3322a115bbcf","code":"MR-89DD7262",
  "workOrderId":"2763cbda-…","workOrderCode":"WO-162845","status":"POSTED","reason":"Unused offcut back to stock",
  "postedAt":"2026-10-20T09:28:48.428433Z","postedByUserId":"23fce5b6-…","postedByUsername":"admin","traceId":"266431fe2a2b4056",
  "workOrderVersion":8,"lines":[{"materialReturnLineId":"c47f31ad-…","lineNo":1,"sourceIssueLineId":"9dc3254b-…",
  "sourceIssueId":"3812b43b-…","sourceIssueCode":"MI-3812B43B","componentLineId":"62883835-…","itemId":"720b48cf-…",
  "itemSku":"RM-162845","uom":"KG","warehouseId":"b8c9066e-…","warehouseCode":"RAW-162845","lotId":null,"lotNumber":null,
  "quantity":1.0,"stockMovementId":"d1a49a28-17c8-4329-8f11-8a1fa787711b"}]},"message":"Created successfully"}
```

Tồn kg của kho `RAW` trước / sau: `60` / `61`. "Đã cấp" của dòng vật tư (`GET /work-orders/{W}` → `componentLines[0].issuedQuantity`) vẫn `20`. Gửi
lại cùng key + body ⇒ `201`, cùng `materialReturnId`, tồn vẫn `61`. Cùng key, body khác ⇒ `409 IDEMPOTENCY_CONFLICT` (`dbe7a7c8fc234f17`). Thiếu
`Idempotency-Key` ⇒ `400 MISSING_REQUIRED_FIELD`; thiếu lý do ⇒ `400 APPROVAL_REASON_REQUIRED` field `reason`; lý do 3 ký tự ⇒ `400` field `reason`;
thiếu version ⇒ `400`; version cũ ⇒ `409 CONCURRENT_MODIFICATION`; `1.0000001` ⇒ `400` field `lines[0].quantity`. Lệnh `RELEASED` chưa cấp gì ⇒
`409 STATE_CONFLICT`; dòng cấp của lệnh khác ⇒ `422 RESOURCE_SCOPE_MISMATCH` field `lines[0].sourceIssueLineId`.

Sau `complete-production` (lệnh `COMPLETED`, hụt 1), hoàn nốt 1 kg còn lại ⇒ `201`, version +1; ứng viên hoàn sau đó rỗng.

### 4.2 Vật tư dùng sau khi hoàn (`X-Trace-Id: 7f98a113bc9843f3`)

20 cấp, 18 dùng, 1 hoàn ⇒ còn 1. Báo sản lượng `Weld` dùng 2:

```http
→ 409 {"code":"PLANNED_QUANTITY_EXCEEDED","message":"consumption[0].quantity: only 1.000000 of this issue line is left unconsumed",
  "errors":[{"field":"consumption[0].quantity","message":"only 1.000000 of this issue line is left unconsumed"}]}
```

### 4.3 Nguồn không nhận lại được

Lô `LOT-162845` (vật tư theo lô `RM2-162845`) đổi sang `HOLD` sau khi cấp. Ứng viên hoàn: `unusedQuantity 5`, `returnableQuantity 0`,
`ineligibleReason "LOT_HOLD"`. Hoàn 1 (`X-Trace-Id: d80d6f70692549e1`):

```http
→ 409 {"code":"LOT_NOT_ELIGIBLE","message":"lines[0].sourceIssueLineId: the source cannot take material back now: LOT_HOLD",
  "errors":[{"field":"lines[0].sourceIssueLineId","message":"the source cannot take material back now: LOT_HOLD"}]}
```

Lô trở lại `AVAILABLE` ⇒ hoàn 2 ⇒ `201`, `lines[0].lotId` = lô đó, tồn của lô `5` → `7`. Kho nguồn ngừng hoạt động (`X-Trace-Id: 921c139da178470b`) ⇒
`422 RESOURCE_INACTIVE` cùng field.

### 4.4 Dở dang một lệnh (`X-Trace-Id: 79a09f1a3e2f4f17`)

Sau khi hoàn 1 và kết thúc cả hai công đoạn (good cuối 9):

```http
GET /api/v1/work-orders/{W}/wip
→ 200 {"code":"SUCCESS","result":{"workOrderId":"2763cbda-…","workOrderNo":"WO-162845","status":"IN_PROGRESS","version":12,
  "inputKnown":true,"consumptionKnown":true,"section":null,
  "materials":[{"issueLineId":"9dc3254b-…","issueId":"3812b43b-…","issueCode":"MI-3812B43B","postedAt":"2026-10-20T09:28:47.449620Z",
    "componentLineId":"62883835-…","itemId":"720b48cf-…","itemSku":"RM-162845","itemName":"Raw material","uom":"KG",
    "warehouseId":"b8c9066e-…","warehouseCode":"RAW-162845","lotId":null,"lotNumber":null,"issuedQuantity":20.000000,
    "consumptionKnown":true,"consumedQuantity":18.000000,"returnedQuantity":1.000000,"unusedQuantity":1.000000,
    "returnableQuantity":1.000000,"eligible":true,"ineligibleReason":null}],
  "operations":[{…,"name":"Cut","status":"COMPLETED","inputQuantity":10.000000,"goodQuantity":9.000000,"scrapQuantity":1.000000,
    "historicalReworkQuantity":1.000000,"pendingReworkQuantity":0.000000,"transferredQuantity":9.000000,…},
    {…,"name":"Weld","status":"COMPLETED","inputQuantity":9.000000,"goodQuantity":9.000000,…}],
  "rework":[],
  "output":{"uom":"EA","plannedQuantity":10.000000,"finalGoodQuantity":9.000000,"scrapQuantity":1.000000,
    "historicalReworkQuantity":1.000000,"approvedReceiptQuantity":0.000000,"openReceiptQuantity":0,"availableToReceipt":9.000000,…}}}
```

Trước lượt xử lý lại, `GET …/wip?section=REWORK` trả một lượt `PE-DBBF7913` với `pendingReworkQuantity 1`, và `materials`/`output` là `null`.

### 4.5 Danh sách

`GET /api/v1/plants/{P}/wip?search=WO-162845` ⇒ một dòng `status COMPLETED`, `finalGoodQuantity 9`, `terminalShortfallQuantity 1`,
`pendingReworkQuantity 0`. `GET /api/v1/material-returns?plantId={P}&search=mr-89dd7262&fromDate=2026-10-20&toDate=2026-10-20` ⇒ `totalElements 1`.
`X-Plant-Id` khác `plantId` ⇒ `409 STATE_CONFLICT` (`1096c2024d154f27`).

### 4.6 Lệnh báo sản lượng trước `G8c` (`X-Trace-Id: 3bffb442259c464d`)

```http
GET /api/v1/work-orders/{W4}/wip
→ 200 {…,"inputKnown":false,"consumptionKnown":false,"materials":[{…,"issuedQuantity":20.000000,"consumptionKnown":false,
  "consumedQuantity":null,"returnedQuantity":0,"unusedQuantity":null,"returnableQuantity":null,…}],…,
  "output":{…,"finalGoodQuantity":null,…,"availableToReceipt":null,"terminalShortfallQuantity":null}}
```

Hoàn ⇒ `409 PRODUCTION_INPUT_UNKNOWN` (`629c70fa09a64c43`). *(Lệnh này được đánh dấu bằng một câu SQL trên dev để giả lập lệnh cũ.)*

### 4.7 Quyền

Người chỉ có `PERM_WIP_READ` + `PERM_MATERIAL_RETURN_READ` trên nhà máy: dở dang `200`, chi tiết phiếu hoàn `200`, tạo phiếu hoàn `403`, ứng viên hoàn `403`.

---

## §5 — Envelope và hợp đồng lỗi

Envelope không đổi `{code, result, message, errors?}`. **Không** mã lỗi mới. Mã FE đề xuất ánh xạ vào mã sẵn có: `MATERIAL_RETURN_EXCEEDED` →
`PLANNED_QUANTITY_EXCEEDED` field `lines[i].quantity`; `MATERIAL_RETURN_SOURCE_INELIGIBLE` → `LOT_NOT_ELIGIBLE` (lô) hoặc `RESOURCE_INACTIVE` (kho /
vật tư); `MATERIAL_CONSUMPTION_UNKNOWN` → `PRODUCTION_INPUT_UNKNOWN`; `REASON_REQUIRED` → `APPROVAL_REASON_REQUIRED` field `reason`.

---

## §6 — Quyền, phạm vi và giới hạn đã biết

- `PERM_WIP_READ` chỉ đọc; không tự cấp quyền thao tác nào. Chi tiết dở dang trả toàn bộ dòng cấp của lệnh — **không** cắt theo quyền cấp vật tư.
- **Không có `HISTORY`** trong dở dang (`section=HISTORY` ⇒ `400`). Lịch sử đọc từ danh sách cấp vật tư, báo sản lượng, phiếu nhập, phiếu hoàn.
- **Không có `capabilities`** (D12). FE suy nút từ trạng thái + số (`returnableQuantity > 0`, quyền của người dùng); server kiểm lại khi gọi.
- **Variance và chi phí vật tư thực tế vẫn theo lượng đã cấp** — vật tư đã hoàn vẫn bị tính là đã dùng ở `GET /work-orders/{W}/variance`. Đổi là đổi số
  variance, chưa làm.
- Response tạo phiếu hoàn in `quantity` theo cách cộng các mục (`1.0`); đọc lại thì `1.000000` — cùng hiện tượng response "vừa tạo" đã ghi ở v1.
- Hoàn vật tư theo số sê-ri chưa hỗ trợ (sê-ri ngoài phạm vi GĐ8).
- Endpoint theo id trả `403` cho id không tồn tại (Q30).

---

## §7 — Bằng chứng nghiệm thu

| Hạng mục | Kết quả |
|---|---|
| `mvn -o clean verify` | **1913 unit / 203 class + 269 IT / 38 class**, 0 lỗi (trước bản này 1871 + 263) |
| Migration `V82` | chạy sạch trên DB dev có dữ liệu demo |
| Nghiệm thu mutation | 27/27 bị test bắt (2 lần đầu bị giết vì lý do sai — đã viết lại đúng nghĩa và chạy lại) |
| Smoke HTTP thật | lượt `162845`: **33/33** kiểm tra đạt, ngay lần chạy đầu |
| Test end-to-end | luồng 11 bước nay hoàn 4 kg qua HTTP: tồn +4, version +1, gửi lại không biến động thứ hai, dở dang báo 20/1/4/15 |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm:** giá trị cấp / dùng / hoàn / dở dang (D10); nguồn cung lệnh cho MRP theo sản lượng thực (D11); `capabilities` (D12); `HISTORY` của dở
dang; variance trừ phần đã hoàn.

**FE làm tiếp:**
1. Màn hình dở dang: gọi `GET /work-orders/{W}/wip` (theo `section` khi chỉ mở một tab); hiện "không xác định" khi `consumptionKnown = false`.
2. Form hoàn: lấy dòng từ `material-return-candidates`, trần = `returnableQuantity`; gửi `expectedWorkOrderVersion`, lý do 5–2000, `Idempotency-Key`.
3. Bỏ cột giá trị khỏi màn hình dở dang / phiếu hoàn cho tới khi BE có giá.
4. Chạy lại A01–A40 trên bản deploy này (phần mới: A29–A33, A39) và báo kết quả.

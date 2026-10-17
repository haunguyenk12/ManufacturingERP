# BE trả lời — Giai đoạn 6: Đơn bán hàng và hoạch định vật tư

**Trả lời cho:** `FE_HANDOFF_GD_06_2026-10-14.md` (v1, P1) · **Ngày gửi:** 17/10/2026 · **Bản:** v1 — **phần đơn bán hàng (`G6a`)**
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev phần đơn bán hàng. Phần **hoạch định + chuyển đổi** sẽ gửi
ở `_v2`, phần **nhập đơn Excel** ở `_v3` — cùng task này. Chưa phải "FE nghiệm thu đạt": task chỉ đóng khi FE chạy A01–A38
trên adapter của mình và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Đơn bán hàng nay nối vào danh mục khách hàng, server tự cấp mã đơn, lưu nháp cả đơn một lần, mỗi dòng có 6 con số tiến độ
(số không có bằng chứng trả `null` + `UNKNOWN`, không bao giờ đoán `0`), và giữ được tồn kho cho từng dòng đơn — dùng chung
đúng một cột "đã giữ" với lệnh sản xuất, nên mọi con số tồn khả dụng trong hệ thống tự thấy phần đơn bán đang giữ.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | Đơn lưu `customerId` của khách ACTIVE cùng Công ty; tên/mã khách là snapshot | 🟡 **Thiếu thật, đã làm** | Trước đây `customerName` là chữ tự do, không nối danh mục (cố ý hoãn ở GĐ2). Nay `POST /drafts` bắt buộc `customerId`, chụp `customerCode` + `customerName` |
| R02 | Lưu cả đơn 1–500 dòng một lần, chỉ DRAFT, `expectedVersion`, số tối đa 6 lẻ, ngày cần ≥ ngày đặt | 🟡 **Một phần có sẵn, đã bổ sung** | Lưu một lần + `expectedVersion` có từ 08/2026. Mới: đường `/drafts` + `/{id}/draft`, 1–500 dòng, 6 số lẻ (`400`), lỗi ngày gắn đúng field `lines[i].dueDate`, chỉ bán thành phẩm/bán thành phẩm, xác nhận kiểm lại khách + vật tư |
| R03 | Tìm theo mã/khách/tham chiếu, lọc trạng thái + khoảng ngày, tóm tắt dòng không cần gọi chi tiết | 🟡 **Thiếu thật, đã làm** | Trước đây chỉ tìm theo mã + tên khách. Nay thêm mã khách, tham chiếu khách, `orderDateFrom/To`, `dueDateFrom/To` (theo **ngày cần sớm nhất**), `earliestDueDate`/`latestDueDate`/`lineCount` trên mỗi dòng danh sách |
| R04 | Chi tiết tách đặt / giữ / lệnh SX phủ / sản xuất / QC / giao, có bằng chứng | 🟡 **Thiếu thật, đã làm** | Trước đây chỉ có `orderedQuantity` + `fulfilledQuantity`. Xem §2 công thức |
| R05 | Giữ tồn ≤ phần chưa phủ và ≤ tồn tự do, đúng lô/kho, gắn đơn/dòng; tồn thực tế không đổi | 🔴 **Chưa có gì, đã làm** | Trước đây giữ tồn **chỉ** thuộc lệnh sản xuất. Đo thật: đặt 100, giữ 30 ⇒ tồn thực tế 30 / đã giữ 30 / khả dụng 0, dòng còn 70 |
| R06 | Bỏ giữ chỉ phần của chính đơn; huỷ đơn có lý do, giải phóng giữ tồn trong cùng transaction | 🟡 **Thiếu thật, đã làm** | Bỏ giữ của đơn khác ⇒ `404`. Huỷ đơn nhận `reason` (lưu lại) và trả hết tồn đang giữ |
| R07 | Nhu cầu hoạch định chỉ đơn đã xác nhận còn chưa phủ; tách mã dòng đơn khỏi mã nhu cầu | 🟡 **Có sẵn, bổ sung** | Danh sách cũ giữ nguyên; thêm `heldStockQuantity`, `workOrderCoveredQuantity`, `uncoveredQuantity` và tham số `uncoveredOnly=true` |
| R08–R15 | Hoạch định, chuyển đổi, đơn mua nháp | ⏳ **Ở `_v2`** | — |
| R16–R17 | Nhập đơn Excel | ⏳ **Ở `_v3`** | — |
| R18 | Mỗi lệnh kiểm quyền/phạm vi/trạng thái, nhất quán, không vượt số lượng khi tranh chấp | ✅ **Đã làm cho phần đơn bán** | Giữ tồn kiểm `expectedVersion` rồi tăng version đơn ⇒ hai lệnh giữ tranh nhau cùng đơn không cùng commit được (đo bằng test DB thật) |

### Trả lời Q1–Q28 (phần thuộc đơn bán hàng)

| Q | Trả lời |
|---|---|
| Q1 | **Có.** `POST /api/sales-orders/v1/drafts` cấp `SO-000001`, `SO-000002`… từ một sequence của DB; mã duy nhất trong Công ty. Mã cũ do client gửi (đường `POST /v1`) vẫn chạy, và số nào đã bị mã cũ chiếm thì server bỏ qua, không cấp trùng |
| Q2 | **Có.** Phủ bởi lệnh SX tính cả lệnh `DRAFT`; không tính lệnh `CANCELLED`/`CLOSED`. Chỉ tính phần chưa qua QC (phần đã qua QC nằm ở `qcAcceptedQuantity`, không đếm hai lần) |
| Q3 | **Có.** `dueDateFrom/To` lọc theo `earliestDueDate`; `latestDueDate` trả riêng |
| Q4 | **Có.** Giữ/bỏ giữ cần `PERM_SALES_ORDER_MANAGE` trên đơn; xem cần `PERM_SALES_ORDER_READ`. Không dùng quyền giữ vật tư của lệnh SX |
| Q6 | Phần đơn bán: **có** — `allocation-candidates` chỉ cần quyền đọc đơn, trả đúng vị trí tồn của vật tư dòng đó trong nhà máy của đơn (không mở toàn bộ tồn kho). Selector khách/vật tư dùng API danh mục hiện có (cần quyền đọc danh mục tương ứng) |
| Q15 | Giới hạn thật là `NUMERIC(19,6)`: tối đa **13 chữ số nguyên + 6 số lẻ**. Guard `999999999` của FE nằm gọn trong đó, giữ được |
| Q18 | **Có.** `customerCode`/`customerName` chụp lúc lưu nháp; đổi tên khách sau đó không sửa đơn cũ. Lưu nháp lại thì lấy tên hiện tại |
| Q24 | **Có, chặn — không cascade.** Ngay khi có lệnh SX gắn vào dòng đơn, đơn chuyển `IN_PRODUCTION` và **không huỷ được** (`409 STATE_CONFLICT`, luật có từ 07/2026). FE đề xuất mã `DOWNSTREAM_DOCUMENT_EXISTS`; BE dùng `STATE_CONFLICT` (§5) |
| Q25 | **Có.** Bỏ trống `lotId` với vật tư theo lô ⇒ server phân bổ **FEFO** (hết hạn sớm trước) trên các lô đủ điều kiện của kho đó, mỗi lô một dòng giữ — response luôn ghi rõ lô. Thiếu tổng ⇒ không giữ gì (`409`) |
| Q26 | **Có.** `fulfilledQuantity` = lượng QC chấp nhận (`AVAILABLE`) gắn cho dòng đơn. **Không** phải đã giao — hệ thống chưa có chứng từ giao hàng nào |
| Q27 | **Có.** `openQuantity` giữ nguyên nghĩa (`ordered − fulfilled`); số mới là `uncoveredQuantity` |
| Q2x khác | Q5, Q7–Q14, Q16, Q17, Q21, Q23, Q28: ở `_v2`. Q19, Q20, Q22: ở `_v3` |

---

## §1 — Nguyên nhân thật

1. **Giữ tồn chỉ thuộc lệnh sản xuất.** Bảng giữ tồn bắt buộc có lệnh SX + dòng linh kiện, nên đơn bán không có chỗ nào để giữ.
   BE **mở rộng đúng bảng đó** (một dòng giữ thuộc **đúng một** chủ: dòng linh kiện lệnh SX *hoặc* dòng đơn bán — DB chặn cả hai
   hoặc không cái nào). Lượng giữ vẫn cộng vào **một** cột "đã giữ" của vị trí tồn, nên MRP, dashboard, xuất kho, chuyển kho,
   kiểm đếm, đổi trạng thái lô đều tự thấy phần đơn bán đang giữ — không có "hai con số" cho cùng một kho.
2. **Một chỗ suýt lệch thật, đã sửa:** danh sách "đang giữ" theo kho (`GET /api/v1/inventory/reservations`) trước đây nối cứng
   vào lệnh SX ⇒ phần đơn bán giữ sẽ **biến mất** khỏi đúng màn hình trả lời "vì sao tồn này không tự do". Nay trả cả hai, kèm
   `ownerType` + mã đơn.
3. **Đơn chưa nối khách hàng** vì GĐ2 cố ý hoãn (so tên khách tự do với danh mục là đoán). Đơn cũ giữ `customerId = null`
   ("tạo trước khi nối danh mục") — BE **không** tự gán bằng cách so tên.

---

## §2 — Thay đổi contract

### Endpoint (tiền tố `/api`)

| Method + path | Body / query | Kết quả | Quyền |
|---|---|---|---|
| `POST /sales-orders/v1/drafts` | `companyId, plantId, customerId, customerReference?, orderDate, note?, lines[1..500]{itemId, orderedQuantity, dueDate}` | **201** đơn `DRAFT`, `orderNo` do server cấp | `PERM_SALES_ORDER_MANAGE` nhà máy; `X-Plant-Id` đối chiếu |
| `PATCH /sales-orders/v1/{id}/draft` | `expectedVersion, customerId, customerReference?, orderDate, note?, lines[1..500]` — **thay toàn bộ** (field vắng = xoá, khác `PATCH /{id}` cũ) | 200; mã dòng được cấp lại | MANAGE |
| `POST /sales-orders/v1/{id}/confirm` | body **tuỳ chọn** `{expectedVersion}` | 200 | MANAGE |
| `POST /sales-orders/v1/{id}/cancel` | body **tuỳ chọn** `{expectedVersion?, reason}` — gửi body thì `reason` bắt buộc | 200, trả hết tồn đang giữ | MANAGE |
| `GET /sales-orders/v1` | thêm `search` (mã đơn, tên/mã khách, tham chiếu), `orderDateFrom/To`, `dueDateFrom/To` | trang 20; mỗi dòng có `earliestDueDate`, `latestDueDate`, `lineCount` | `PERM_SALES_ORDER_READ` |
| `GET /sales-orders/v1/{id}` | — | đơn + mọi dòng có 6 số + `linkedDocuments[]` | READ |
| `GET /sales-orders/v1/{id}/reservations` | `lineId?, status?, page, size` | trang các dòng giữ của đơn | READ |
| `GET /sales-orders/v1/{id}/lines/{lineId}/allocation-candidates` | `warehouseId?` (không có = mọi kho của nhà máy), `page, size` | `uncoveredQuantity` + trang vị trí tồn giữ được (FEFO) | READ |
| `POST /sales-orders/v1/{id}/reservations` | `expectedVersion, salesOrderLineId, warehouseId, lotId?, quantity` | **201** `{reservations[], salesOrder}` — đơn kèm `version` mới + số dòng đã tính lại | MANAGE |
| `POST /sales-orders/v1/{id}/reservations/{reservationId}/release` | `expectedVersion, reason?` | 200 `{reservations[], salesOrder}` | MANAGE |
| `GET /sales-orders/v1/planning-demands` | thêm `uncoveredOnly` (mặc định `false` = như cũ) | thêm `heldStockQuantity`, `workOrderCoveredQuantity`, `uncoveredQuantity` | `PERM_MRP_RUN` (như cũ) |

Đường cũ `POST /v1` (mã đơn + tên khách do client gửi) và `PATCH /{id}` **giữ nguyên**.

### Field mới

| Ở đâu | Field | Ghi chú |
|---|---|---|
| Đơn | `customerId`, `customerCode`, `customerReference` | `null` trên đơn tạo trước GĐ6 hoặc qua đường cũ |
| Đơn | `cancelReason` | `null` khi huỷ không gửi body |
| Đơn | `earliestDueDate`, `latestDueDate`, `lineCount` | có trên danh sách lẫn chi tiết |
| Đơn | `linkedDocuments[]` `{type, documentId, documentCode, status, salesOrderLineId, itemId, uom, quantityForThisOrder, qcAcceptedQuantity}` | hiện chỉ `WORK_ORDER`; `quantityForThisOrder` là **phần của lệnh SX dành cho dòng đơn này**, không phải toàn bộ lệnh |
| Dòng đơn | `heldStockQuantity`, `workOrderCoveredQuantity`, `producedQuantity`, `qcAcceptedQuantity`, `deliveredQuantity`, `uncoveredQuantity`, `metricsEvidence` | có ở chi tiết và mọi response ghi; `null` trên đường cũ `POST /v1`, `PATCH /{id}` |
| Kho — `GET /v1/inventory/reservations` | `ownerType` (`WORK_ORDER`/`SALES_ORDER`), `salesOrderId`, `salesOrderNo`, `salesOrderLineId`, `salesOrderLineNo` | dòng của đơn bán có mọi field lệnh SX = `null` |

### Công thức

```
heldStockQuantity        = Σ (giữ − đã dùng) các dòng giữ ACTIVE của dòng đơn
workOrderCoveredQuantity = Σ (phân bổ − đã QC) của lệnh SX gắn dòng đơn, lệnh không CANCELLED/CLOSED (gồm DRAFT)
qcAcceptedQuantity       = fulfilledQuantity (QC chấp nhận)
producedQuantity         = null, metricsEvidence.produced  = "UNKNOWN"   — không có vết sản lượng theo từng dòng đơn
deliveredQuantity        = null, metricsEvidence.delivered = "UNKNOWN"   — hệ thống chưa có chứng từ giao hàng
uncoveredQuantity        = max(0, ordered − qcAccepted − heldStock − workOrderCovered)
```

Ba số bị trừ **không chồng nhau**: phủ lệnh SX chỉ tính phần chưa qua QC, và lệnh giữ tồn không được vượt `uncoveredQuantity`
tại thời điểm giữ. Khác công thức mock của FE ở một chỗ: FE trừ `delivered`; BE trừ `qcAccepted` (cùng nghĩa với `openQuantity`
cũ mà MRP vẫn dùng), vì `delivered` chưa có nguồn.

---

## §3 — Breaking change

**`Wire: CÓ`** — hẹp, đều là "chặn mới" hoặc "số liệu đổi đúng ý định":

| # | Thay đổi | Trước | Sau |
|---|---|---|---|
| 1 | Tồn khả dụng (mọi màn hình, MRP, dashboard) khi có đơn bán giữ tồn | không đổi | **giảm đúng lượng đơn đang giữ** — đó là mục đích của giữ tồn |
| 2 | Đổi trạng thái lô đang được **đơn bán** giữ | (không thể xảy ra) | `409 RESERVED_STOCK` — phải bỏ giữ trước, như với lệnh SX (G4) |
| 3 | Huỷ đơn `CONFIRMED` đang giữ tồn qua đường cũ (không body) | — | vẫn `200`, và **trả hết tồn đang giữ** |
| 4 | `GET /v1/inventory/reservations` | chỉ dòng lệnh SX | **có thêm dòng đơn bán**; các field lệnh SX của dòng đó là `null` |

Không bỏ field nào, không đổi kiểu, không đổi mặc định query param. Mọi field mới là additive.

---

## §4 — Cách dùng (copy từ lượt gọi thật `142411`, 17/10/2026 ~14:24 giờ VN)

### 4.1 Tạo nháp (A01) · `X-Trace-Id: ea807bfbc5c842e8`

```
POST /api/sales-orders/v1/drafts
{
  "companyId": "c7f74c0b-2fbb-44ba-8b27-d3e6376bd1f8",
  "plantId": "de1d49b6-1cf1-43c4-bd53-2c11ec31c4ae",
  "customerId": "68280d96-8532-40d5-aa20-b1865972e2ab",
  "customerReference": "PO-DEMO-A",
  "orderDate": "2026-10-14",
  "lines": [
    {"itemId": "2ab50ce7-02a1-48b7-8ba7-4ca038448705", "orderedQuantity": "0.1", "dueDate": "2026-10-28"},
    {"itemId": "2ab50ce7-02a1-48b7-8ba7-4ca038448705", "orderedQuantity": "0.2", "dueDate": "2026-10-29"}
  ]
}
```

```
HTTP 201
{
  "code": "SUCCESS",
  "result": {
    "salesOrderId": "3b64997d-6bb1-467d-98e1-de7c8bd2f1b4",
    "orderNo": "SO-000003",
    "customerName": "Khách hàng A",
    "orderDate": "2026-10-14",
    "status": "DRAFT",
    "version": 0,
    "lines": [
      {
        "salesOrderLineId": "b7aad266-e8a2-4407-b9dc-3005492e8ca0",
        "lineNo": 1,
        "uom": "P142411",
        "orderedQuantity": 0.1,
        "fulfilledQuantity": 0,
        "openQuantity": 0.1,
        "dueDate": "2026-10-28",
        "heldStockQuantity": 0,
        "workOrderCoveredQuantity": 0,
        "producedQuantity": null,
        "qcAcceptedQuantity": 0,
        "deliveredQuantity": null,
        "uncoveredQuantity": 0.1,
        "metricsEvidence": {"heldStock": "KNOWN", "workOrderCovered": "KNOWN", "produced": "UNKNOWN",
                            "qcAccepted": "KNOWN", "delivered": "UNKNOWN"}
      },
      "…"
    ],
    "customerId": "68280d96-8532-40d5-aa20-b1865972e2ab",
    "customerCode": "K-142411",
    "customerReference": "PO-DEMO-A",
    "cancelReason": null,
    "earliestDueDate": "2026-10-28",
    "latestDueDate": "2026-10-29",
    "lineCount": 2,
    "linkedDocuments": []
  },
  "message": "Created successfully"
}
```

(Rút gọn: bỏ `companyId/Code`, `plantId/Code`, `createdAt/updatedAt`, `itemId/itemSku/itemName` cho dễ đọc; response thật có đủ.)
Số trên response ghi đúng như request gửi (`0.1`); đọc lại thì DB trả đủ 6 số lẻ (`0.100000`) — cùng hiện tượng đã ghi ở GĐ2.

### 4.2 Giữ tồn (A07) · `X-Trace-Id: b23576f764694a82`

Sau khi sửa nháp thành 100 cái và xác nhận (`version` = 2):

```
POST /api/sales-orders/v1/3b64997d-6bb1-467d-98e1-de7c8bd2f1b4/reservations
{"expectedVersion": 2, "salesOrderLineId": "19385842-c1df-4b21-89a7-5f2d524f5248",
 "warehouseId": "d620beed-5b0d-46c1-bec4-eb042de09806", "quantity": "30"}
```

```
HTTP 201
{
  "code": "SUCCESS",
  "result": {
    "reservations": [
      {"reservationId": "062a0267-836f-404f-843a-b93c5fa06ee5", "salesOrderLineId": "19385842-c1df-4b21-89a7-5f2d524f5248",
       "warehouseCode": "FG-142411", "lotId": null, "lotCode": null, "lotExpiresAt": null,
       "quantity": 30, "consumedQuantity": 0, "remainingHeldQuantity": 30, "status": "ACTIVE"}
    ],
    "salesOrder": {"orderNo": "SO-000003", "status": "CONFIRMED", "version": 3, "lines": ["…"]}
  }
}
```

Đọc lại ngay sau đó:
- `GET /api/sales-orders/v1/3b64997d-…` (A13, `e462d80d8ad94132`): `orderedQuantity 100.000000`, `heldStockQuantity 30.000000`,
  `workOrderCoveredQuantity 0`, `producedQuantity null`, `qcAcceptedQuantity 0.000000`, `deliveredQuantity null`,
  `uncoveredQuantity 70.000000`.
- `GET /api/v1/inventory/balances?warehouseId=…&itemId=…` (A07b, `bd4493039bc24b0c`): `quantity 30.000000`,
  `reservedQuantity 30.000000`, `availableQuantity 0.000000` — tồn thực tế không đổi.
- `GET /api/v1/inventory/reservations?warehouseId=…` (A07w, `9fb6aadd1b3448f8`): `ownerType "SALES_ORDER"`,
  `salesOrderNo "SO-000003"`, `workOrderId null`.
- Gửi lại đúng request trên (cùng `expectedVersion: 2`) ⇒ `409 CONCURRENT_MODIFICATION` (A07s, `bcf1f7258d244dec`) — không giữ lần hai.

### 4.3 Giữ không chỉ định lô (A08) · `X-Trace-Id: cfa68a6ae4f745eb`

Vật tư theo lô, kho có lô EARLY 4 (hết hạn 2030), LATE 20 (2031), HELD 50 (`HOLD`), EXP 50 (đã quá hạn). Giữ 7, không gửi `lotId`
⇒ hai dòng giữ: **EARLY 4 + LATE 3**; lô HOLD và lô quá hạn không bị đụng. Chỉ định lô HELD ⇒ `409 LOT_NOT_ELIGIBLE` "Lot cannot be
held in status HOLD" (`211272cd5cf141f6`); chỉ định lô EXP ⇒ `409 LOT_NOT_ELIGIBLE` "Lot has expired" (`54835f4874334412`).

### 4.4 Huỷ đơn có lý do (A11) · `X-Trace-Id: b7ea332cdeb04c52`

```
POST /api/sales-orders/v1/38f29ee2-b163-48a2-a82d-f0578d9824b4/cancel
{"expectedVersion": 3, "reason": "Khách rút đơn"}
```

⇒ `200`, `status "CANCELLED"`, `cancelReason "Khách rút đơn"`. DB sau đó: dòng giữ của đơn `RELEASED, CANCELLED` (một dòng đã bỏ giữ
trước đó, một dòng do huỷ đơn), tổng `reserved_quantity` của vật tư về `0.000000`.

---

## §5 — Envelope và hợp đồng lỗi

Envelope như mọi giai đoạn trước. Lỗi của phần này:

| Tình huống | HTTP | `code` | Ghi chú |
|---|---|---|---|
| Thiếu quyền | 403 | `PERMISSION_DENIED` | |
| Đơn / dòng đơn / dòng giữ không tồn tại hoặc thuộc đơn khác | 404 | `ENTITY_NOT_FOUND` | gồm bỏ giữ của đơn khác (A09) |
| Khách / vật tư / đơn vị tính ngừng hoạt động | 422 | `RESOURCE_INACTIVE` | FE đề xuất `CUSTOMER_NOT_AVAILABLE`/`ITEM_NOT_AVAILABLE` (400) |
| Khách/vật tư/kho khác Công ty hoặc nhà máy | 422 | `RESOURCE_SCOPE_MISMATCH` | FE đề xuất `INVALID_WAREHOUSE` (400) |
| Vật tư không phải thành phẩm/bán thành phẩm | 422 | `OPERATION_NOT_ALLOWED` | |
| Số ≤ 0, quá 6 số lẻ, ngày cần < ngày đặt, thiếu `reason` khi huỷ có body | 400 | `VALIDATION_ERROR` + `errors[{field}]` | field ổn định: `lines[i].orderedQuantity`, `lines[i].dueDate`, `reason` — FE đề xuất `INVALID_QUANTITY`/`INVALID_DATE` |
| Sai trạng thái (sửa đơn đã xác nhận, giữ tồn cho DRAFT, huỷ đơn đang sản xuất) | 409 | `STATE_CONFLICT` | FE đề xuất `INVALID_STATE` / `DOWNSTREAM_DOCUMENT_EXISTS` |
| `expectedVersion` lệch | 409 | `CONCURRENT_MODIFICATION` | FE đề xuất `VERSION_CONFLICT` |
| Giữ vượt tồn tự do | 409 | `INSUFFICIENT_AVAILABLE_STOCK` | trùng đề xuất FE |
| Giữ vượt phần chưa phủ của dòng | 409 | `PLANNED_QUANTITY_EXCEEDED` | FE đề xuất `DEMAND_QUANTITY_EXCEEDED` |
| Lô HOLD/REJECTED/quá hạn | 409 | `LOT_NOT_ELIGIBLE` | trùng đề xuất FE |
| Đổi trạng thái lô đang bị giữ | 409 | `RESERVED_STOCK` | có từ G4, nay gồm cả giữ của đơn bán |

Không thêm mã lỗi mới ở phần này — mọi tình huống đều có mã sẵn có cùng nghĩa.

---

## §6 — Quyền, phạm vi và giới hạn đã biết

- Không thêm quyền mới. Xem: `PERM_SALES_ORDER_READ`; ghi (lưu nháp, xác nhận, huỷ, giữ/bỏ giữ): `PERM_SALES_ORDER_MANAGE` —
  kiểm theo nhà máy của đơn. Bỏ giữ của đơn khác ⇒ `404`, không lộ đơn đó có tồn tại hay không.
- `X-Plant-Id` đối chiếu ở `POST /drafts` và `GET /v1` (endpoint nêu `plantId`); endpoint theo `{id}` thì không (nhà máy lấy từ đơn).

**Giới hạn đã biết:**
- **Idempotency-Key không áp cho đơn bán.** Chống làm hai lần dựa vào `expectedVersion`: gửi lại một lệnh đã chạy ⇒ `409
  CONCURRENT_MODIFICATION`, không trả lại kết quả cũ. Tạo nháp mới gửi lại lần hai **tạo thêm một nháp** (mã mới) — FE không nên
  tự retry lệnh tạo nháp khi chưa biết kết quả; tải danh sách trước.
- **Vật tư theo số sê-ri không giữ tồn cho đơn được** (`422`) — ngoài phạm vi GĐ6.
- **Đổi trạng thái lô khi lô đang bị giữ bị chặn** (`RESERVED_STOCK`), nên kịch bản A10 của FE ("lô chuyển HOLD trước khi bỏ
  giữ") không xảy ra được: phải bỏ giữ trước. Bỏ giữ không bao giờ đổi trạng thái lô.
- **Giữ tồn chưa bao giờ "tiêu" vào giao hàng** — chưa có chứng từ giao (GĐ10). Đơn đã giao xong vẫn cần bỏ giữ tay hoặc huỷ.
- `workOrderCoveredQuantity` có thể in `0` (không kèm số lẻ) khi dòng không có lệnh SX nào, các số khác in `0.000000`. Cùng giá trị.

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

`mvn -o clean verify` (unit + integration trên PostgreSQL thật qua Testcontainers): **1583 case unit + 224 case integration,
failures = 0, errors = 0**. Trước phần này: 1548 + 215. Không xoá test cũ nào; test cũ đổi theo chữ ký mới được **sửa**.

Integration đáng chú ý (DB thật, commit thật):
- Giữ không chỉ định lô: FEFO qua hai lô hợp lệ, **bỏ qua** lô HOLD và lô quá hạn; gửi lại cùng `expectedVersion` ⇒ `409`, tổng
  đã giữ không đổi.
- Giữ vượt phần chưa phủ ⇒ `409`, không có dòng giữ nào được ghi.
- Huỷ đơn ⇒ mọi dòng giữ `CANCELLED`, `reserved_quantity` về 0, lý do được lưu.
- Danh sách lọc với **mọi** tham số rỗng và với đủ tham số (bẫy kiểu tham số của Postgres đã gặp hai lần trong repo).
- Danh sách "đang giữ" theo kho trả cả dòng lệnh SX lẫn dòng đơn bán; DB từ chối dòng giữ có hai chủ hoặc không chủ nào.

**Kiểm chứng "test có bắt được lỗi thật không":** cố ý phá 7 chỗ (nối cứng lệnh SX ở danh sách theo kho, bỏ điều kiện ACTIVE khi
cộng lượng giữ, bỏ tăng version đơn khi giữ, bỏ điều kiện hết hạn khi chọn lô, đổi mã "vượt phần chưa phủ" sang một mã 409 khác,
bỏ ép kiểu tham số ngày, bỏ trả tồn khi huỷ đơn) — **7/7 lần test đỏ đúng chỗ**; đã khôi phục.

### 7.2 Kiểm chứng qua HTTP thật

- **Môi trường:** backend local (profile dev), PostgreSQL + Redis thật qua Docker, tài khoản quản trị. Migration mới chạy được
  trên DB dev có sẵn dữ liệu demo.
- **Lượt `142411`** (17/10, ~14:24): 55 lượt gọi (kể cả dựng dữ liệu), **mọi HTTP status đúng kỳ vọng**. Công ty thử `GD6-142411`.

| Ca FE | Kết quả | `X-Trace-Id` |
|---|---|---|
| A01 nháp 2 dòng 0,1 / 0,2 | 201, `SO-000003`, `version 0`, UOM từ vật tư | `ea807bfbc5c842e8` |
| A02 khách ngừng / vật tư nguyên liệu | 422 `RESOURCE_INACTIVE` / 422 `OPERATION_NOT_ALLOWED` | `362ff7107e424d2b`, `760fde3f876249d8` |
| A03 7 số lẻ / ngày cần < ngày đặt / số 0 | 400 ×3, field `lines[0].orderedQuantity` / `lines[0].dueDate` | `a4f8672452f341cc`, `02510b2a02c64b01`, `dfa74c21fb21486a` |
| A04 sửa nháp đúng version / gửi lại | 200 / 409 `CONCURRENT_MODIFICATION` | `ef35613790604d84`, `17386ca845334d0a` |
| A05 xác nhận; sửa sau xác nhận; xác nhận lần hai | 200 / 409 / 409 `STATE_CONFLICT` | `b7ef0ae391d14e6a`, `d3f25564568c4261`, `9bd06caa848c4cb6` |
| A06 tìm tham chiếu + ngày cần; không lọc; `X-Plant-Id` sai | 200 (1 đơn) / 200 / 409 | `47479c4ae0464bd9`, `99691d531c4e4c43`, `5615e2d92a9b4d2c` |
| A07 giữ 30 trên 100; gửi lại; giữ thêm khi hết tồn | 201 / 409 / 409 `INSUFFICIENT_AVAILABLE_STOCK` | `b23576f764694a82`, `bcf1f7258d244dec`, `d2d7a7d74b434117` |
| A08 lô HOLD / lô quá hạn / FEFO 7 | 409 / 409 `LOT_NOT_ELIGIBLE` / 201 (EARLY 4 + LATE 3) | `211272cd5cf141f6`, `54835f4874334412`, `cfa68a6ae4f745eb` |
| A09 bỏ giữ của đơn khác | 404 | `66e879211f764527` |
| A10 đổi trạng thái lô đang giữ / bỏ giữ | 409 `RESERVED_STOCK` / 200 | `dcecb50304e44df7`, `ef59da5fcaa54a82` |
| A11 huỷ có lý do; huỷ lần hai; body thiếu lý do | 200 / 409 / 400 | `b7ea332cdeb04c52`, `abc391adfa834057`, `8fefd49be72a4ef0` |
| A13 6 số tiến độ | `produced`/`delivered` `null` + `UNKNOWN`, chưa phủ 70 | `e462d80d8ad94132` |
| A14 nhu cầu chưa phủ | `quantity 100`, `heldStock 30`, `uncovered 70` | `dad7d50ad6064472` |
| Huỷ đường cũ (không body) khi đang giữ | 200, dòng giữ `CANCELLED` | `bd9c554a187c49f3` |

### 7.3 Chưa nghiệm thu

| Ca | Vì sao | Cần gì |
|---|---|---|
| A04 hai lệnh sửa **đồng thời qua HTTP** | Chỉ gửi tuần tự (lệnh thứ hai dùng version cũ) | Bắn song song |
| A12 huỷ khi đã có lệnh SX | Luật có từ 07/2026 (đơn `IN_PRODUCTION` không huỷ được), không dựng lệnh SX trong lượt này | Lệnh SX gắn đơn (có ở `_v2`) |
| A13 legacy có sản lượng/QC | Lượt live chưa có QC gắn đơn | — |
| A37 tài khoản thiếu quyền | Lượt live chỉ dùng tài khoản quản trị; quyền có test tự động deny + allow | Tài khoản thiếu từng quyền |
| A38 lỗi giữa chừng khi giữ/huỷ | Chỉ test tự động (giữ thiếu tồn ⇒ không ghi gì) | — |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm ở bản này:** hoạch định thương mại, chuyển đổi một phần, đơn mua nháp (⇒ `_v2`); nhập đơn Excel (⇒ `_v3`);
Idempotency-Key cho đơn bán (lý do ở §6); giữ tồn theo sê-ri; số dạng chuỗi trên wire (toàn hệ thống dùng JSON number).

**Việc FE cần làm tiếp:**

Bắt buộc:
- [ ] Tạo nháp mới qua `POST /drafts` (không gửi `orderNo`, gửi `customerId`); sửa nháp qua `PATCH /{id}/draft` với **đủ** field
      (field vắng = xoá).
- [ ] Giữ `version` của mọi response để gửi `expectedVersion` cho lệnh kế tiếp (xác nhận, huỷ, giữ, bỏ giữ).
- [ ] Đọc 6 số từ dòng đơn; `null` + `UNKNOWN` hiển thị "chưa có bằng chứng", **không** hiển thị 0.
- [ ] Giữ tồn: lấy vị trí từ `allocation-candidates`, gửi `lotId` hoặc bỏ trống để FEFO; đọc `salesOrder` trong response thay vì
      tải lại.
- [ ] Bắt `PLANNED_QUANTITY_EXCEEDED` (vượt phần chưa phủ), `INSUFFICIENT_AVAILABLE_STOCK`, `LOT_NOT_ELIGIBLE`, `RESERVED_STOCK`.
- [ ] Màn hình kho: dòng `ownerType = "SALES_ORDER"` dẫn link sang đơn bán, không sang lệnh SX.

Nên làm:
- [ ] Danh sách đơn dùng `earliestDueDate`/`lineCount` thay vì gọi chi tiết từng dòng.
- [ ] Chạy A01–A14 trên môi trường FE và báo kết quả theo mã ca.

**Thứ tự deploy:** không ràng buộc.

---

**Tin nhắn kèm (Zalo):**

```
[BE→FE] Giai đoạn 6 (phần 1/3: đơn bán hàng) — nối khách hàng, mã đơn server cấp, 6 số tiến độ, giữ tồn cho đơn
Mức: P1 · Breaking: CÓ (tồn khả dụng giảm theo lượng đơn đang giữ)
Cần FE làm: tạo/sửa nháp qua /drafts, giữ version cho lệnh kế tiếp, đọc 6 số + UNKNOWN, giữ/bỏ giữ tồn
File: BE_RESPONSE_GD_06_2026-10-17.md (phần hoạch định gửi ở _v2, nhập Excel ở _v3)
Thứ tự deploy: không ràng buộc
```

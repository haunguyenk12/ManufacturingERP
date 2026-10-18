# BE trả lời — Giai đoạn 6: Đơn bán hàng và hoạch định vật tư

**Trả lời cho:** `FE_HANDOFF_GD_06_2026-10-14.md` (v1, P1) · **Ngày gửi:** 18/10/2026 · **Bản:** v3 — **phần nhập đơn bán hàng Excel (`G6c`)**
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. Bản này **bổ sung** `BE_RESPONSE_GD_06_2026-10-17.md` (v1, đơn bán hàng)
và `_v2` (hoạch định + chuyển đổi) — không thay hai bản đó. Đây là bản **cuối** của Giai đoạn 6. Chưa phải "FE nghiệm thu đạt": task chỉ đóng
khi FE chạy A01–A38 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Nhập đơn bán hàng từ Excel chạy trên đường batch dùng chung của Giai đoạn 3 với `kind=so`: tải lên chỉ kiểm tra (0 đơn), xác nhận tạo
một đơn `DRAFT` cho mỗi mã nhóm trong một transaction — một lỗi bất kỳ thì không đơn nào được tạo.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R16 | Server kiểm lại XLSX, nhất quán trong nhóm, ≤ 5 MiB / 500 dòng, từ chối công thức; commit tạo mới DRAFT trong một transaction hoặc 0 | 🟡 **Chưa có, đã làm** | Trước đây **không có** đường nhập đơn bán nào. Nay `kind=so` (§2). Nhóm cuối lỗi ở DB ⇒ 0 đơn, batch giữ `READY` (đo bằng test tích hợp) |
| R17 | Lịch sử/chi tiết theo người tải + Công ty + nhà máy; retry trả cùng mã đơn; nguồn đổi làm cả commit thất bại | ✅ **Có sẵn (hạ tầng GĐ3), áp cho `kind=so`** | Commit lại ⇒ cùng `createdIds` + cùng `committedAt` (§4.1). Khách ngừng sau `READY` ⇒ `409 IMPORT_REVALIDATION_FAILED`, 0 đơn (§4.3) |
| A33 | 3 dòng / nhóm A 2 + nhóm B 1 ⇒ đúng 2 đơn DRAFT, retry cùng id | ✅ **Đã đo live** | `SO-000006` (2 dòng) + `SO-000007` (1 dòng), commit lại 2 lần vẫn 2 đơn |
| A34 | Nhóm A có số lượng 0, nhóm B hợp lệ ⇒ INVALID, 0 đơn, không tạo riêng B | ✅ **Đã đo live** | batch `INVALID`, commit `409 STATE_CONFLICT`, số đơn trong DB không đổi |
| A35 | Sai mẫu/header/công thức/> 5 MiB/501 dòng ⇒ mã cụ thể, không tạo đơn | ✅ **Đã đo live** (trừ sai tiêu đề — cùng bộ đọc GĐ3) | `400 TEMPLATE` / ô `FORMULA` / `413 FILE_SIZE` / `400 ROW_LIMIT` (§5) |
| A36 | `READY` rồi khách/vật tư ngừng; nhóm cuối lỗi khi ghi ⇒ rollback cả lô | ✅ **Đã đo** — live phần khách ngừng, test tích hợp phần nhóm cuối lỗi | §4.3, §7.1 |

### Trả lời Q (phần nhập Excel)

| Q | Trả lời |
|---|---|
| Q19 | **Có.** Ô chữ phải đúng `yyyy-MM-dd` (ISO) — `20/10/2026`, `2026-02-30` hay năm có dấu `+12026-…` đều bị từ chối, không đoán. Ô **ngày thật** của Excel được đọc theo **hệ ngày của chính workbook** (1900 hoặc 1904), không có múi giờ nào chen vào — đã đo với workbook 1904 (§7.2). Ô ngày **có giờ** và ô **số trơn** (serial không định dạng ngày) ⇒ lỗi dòng `CELL_TYPE` |
| Q20 | **Có, giữ chính sách GĐ3:** batch, dòng, lỗi, kết quả lưu **vô thời hạn**; **file gốc không lưu** (chỉ tên, kích thước, mã băm SHA-256). Chưa có chính sách xoá — cần PO chốt thời hạn nếu muốn |
| Q22 | **Không làm như đề xuất.** Xem lịch sử/chi tiết vẫn cần **cả** `PERM_SALES_ORDER_READ` **và** `_MANAGE` trên nhà máy, giống mọi loại batch khác (một luật cho mọi loại). Route mock của FE hiện cũng đòi cả cặp nên không phải đổi gì |

---

## §1 — Nguyên nhân thật

Không có lỗi nào bị phát hiện — đây là phần **chưa có**: trước bản này BE không có cách nào tạo đơn bán từ file. Ba điều đáng nói khi làm:

1. **Mã nhóm không phải mã đơn, cũng không phải khoá chống trùng.** Mã đơn do server cấp (`SO-…`, như tạo nháp ở v1). Tải **cùng một file**
   hai lần rồi xác nhận cả hai là **hai bộ nháp**. Chống bấm trùng là việc của `Idempotency-Key` trên lệnh xác nhận (hoặc gửi lại chính batch
   đã xác nhận — server trả lại kết quả cũ).
2. **Mỗi đơn đi qua đúng đường tạo nháp của v1** — cùng luật khách/vật tư/ngày, cùng mã server cấp, cùng nhật ký kiểm toán
   `SALES_ORDER_CREATED` cho **từng** đơn. Không có đường ghi tắt.
3. Trong lúc nghiệm thu, một kiểm tra định dạng ngày ban đầu **không được test nào chứng minh** (mọi ca sai đều bị bước kiểm thứ hai chặn sẵn).
   Nó có giá trị thật ở năm 5 chữ số có dấu, nên đã thêm đúng ca đó.

---

## §2 — Thay đổi contract

### Endpoint (tiền tố `/api`) — **dùng đường batch chung, không mở `/sales-orders/v1/imports/*`**

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Tải + kiểm tra | `POST /v1/data-import/batches` multipart: `file`, `kind=so`, `companyId`, **`plantId`** (bắt buộc) · header `Idempotency-Key` tuỳ chọn · `X-Plant-Id` (nếu gửi phải khớp `plantId`) | **201** batch `READY`/`INVALID`, **không tạo đơn nào** |
| Xác nhận | `POST /v1/data-import/batches/{id}/commit` body `{"expectedVersion": <version>}` · `Idempotency-Key` tuỳ chọn | **200** batch `COMMITTED` + `createdIds` (một id **mỗi đơn**) |
| Lịch sử | `GET /v1/data-import/batches?companyId=&plantId=&kind=so&status=&search=&page=&size=` | Chỉ batch của **chính** người gọi, **đúng** nhà máy |
| Chi tiết | `GET /v1/data-import/batches/{id}` | Mọi dòng + lỗi + `createdIds`. Batch của người khác ⇒ **404** |

Đường FE đề xuất (`/sales-orders/v1/imports/template|validate|errors|commit`) **không có**: tải mẫu và tải file lỗi dạng XLSX không có ở
server — FE tự sinh mẫu và tự xuất lỗi từ `rows[].issues[]`, đúng như đã làm với nhập danh mục (GĐ3) và BOM (GĐ5).

### Mẫu `omniplant-so-v1`

- Sheet dữ liệu tên **`Sales orders`** (cả hai ngôn ngữ). Sheet `_meta`: ô A1 `version` / B1 `omniplant-so-v1`, ô A2 `locale` / B2 `en` hoặc `vi`.
  Sheet hướng dẫn/tham chiếu khác được bỏ qua.
- Hàng 1 là tiêu đề, đúng thứ tự, không thừa cột có tiêu đề:

| Cột | English | Tiếng Việt | Luật |
|---|---|---|---|
| A | Group code | Mã nhóm đơn | bắt buộc, ≤ 100, `A-Z 0-9 . _ -` (tự viết hoa) |
| B | Customer code | Mã khách hàng | bắt buộc, khách `ACTIVE` cùng Công ty; **cùng nhóm cùng giá trị** |
| C | Order date | Ngày đặt | bắt buộc, ngày (Q19); **cùng nhóm cùng giá trị** |
| D | Customer reference | Tham chiếu khách hàng | tuỳ chọn, ≤ 255; **cùng nhóm cùng giá trị** (trống khác có giá trị) |
| E | Item code | Mã sản phẩm | bắt buộc, vật tư `ACTIVE` loại `FINISHED_GOOD`/`WIP`, đơn vị không ngừng |
| F | Quantity | Số lượng | > 0, ≤ 6 số lẻ, không dấu, không mũ; ô số đọc theo **giá trị**, không theo định dạng hiển thị |
| G | Due date | Ngày cần | bắt buộc, ngày, ≥ ngày đặt; mỗi dòng có thể khác |

- Cùng một sản phẩm xuất hiện hai lần trong một nhóm là **hợp lệ** (hai dòng đơn, ví dụ hai ngày cần).
- Một dòng lỗi ⇒ **mọi dòng còn lại của nhóm đó** nhận lỗi `RULE_VIOLATION` ở cột A; dòng lỗi giữ nguyên lỗi gốc của nó. Batch có bất kỳ
  dòng lỗi nào ⇒ `INVALID`, xác nhận bị chặn — **không** có chuyện tạo riêng nhóm hợp lệ.

### Field mới

| Field | Ở đâu | Ý nghĩa |
|---|---|---|
| `kind = "so"` | batch | loại batch mới |
| `salesOrderCount` | batch (chi tiết + kết quả upload/commit) | số mã nhóm = **số đơn sẽ tạo**; `null` với loại khác. Có ngay từ lúc `READY` để FE hiện "sẽ tạo 2 đơn" |

`createdCount` vẫn đếm **dòng** đã áp dụng (3 ở ví dụ), còn `createdIds` là **một id mỗi đơn** (2) — giống BOM. Dùng `createdIds` để mở đơn.

---

## §3 — Breaking change

**Không có.** Chỉ thêm `kind=so` và field `salesOrderCount` (các loại batch khác nhận `null`). Không đổi endpoint, mã lỗi hay field cũ nào.

---

## §4 — Cách dùng (copy từ lượt gọi thật `120521`, 18/10/2026 ~12:05 giờ VN)

### 4.1 Xác nhận 3 dòng / 2 nhóm (A33) · `X-Trace-Id: 14824bb1f70340c4`

Tải lên trước đó (`ca21787e589e47f3`) trả `201`, `status: "READY"`, `version: 0`, `salesOrderCount: 2`, đếm DB: **0 đơn**. Dòng 3 cột G là ô
ngày thật của Excel, dòng 2 gõ mã chữ thường (`ka-120521`, `fg-120521`).

```http
POST /api/v1/data-import/batches/98227458-47d1-4fb7-8272-d1bcee1a226d/commit
Idempotency-Key: g6c-xxxxxxxxxxxx
Content-Type: application/json

{"expectedVersion": 0}
```

```json
{
  "code": "SUCCESS",
  "result": {
    "id": "98227458-47d1-4fb7-8272-d1bcee1a226d",
    "plantId": "fa860730-33d2-4152-8fe9-cb839bd8c118",
    "kind": "so",
    "locale": "en",
    "templateVersion": "omniplant-so-v1",
    "status": "COMMITTED",
    "version": 1,
    "committedAt": "2026-10-18T05:05:24.574907Z",
    "rowCount": 3,
    "validRowCount": 3,
    "errorRowCount": 0,
    "createdCount": 3,
    "rows": [
      { "line": 2, "values": { "groupCode": "A", "customerCode": "KA-120521", "orderDate": "2026-10-20", "customerReference": "PO-77", "itemCode": "FG-120521", "quantity": "2000.000001", "dueDate": "2026-10-30" }, "issues": [] },
      { "line": 3, "values": { "groupCode": "A", "customerCode": "KA-120521", "orderDate": "2026-10-20", "customerReference": "PO-77", "itemCode": "WIP-120521", "quantity": "5", "dueDate": "2026-11-05" }, "issues": [] },
      { "line": 4, "values": { "groupCode": "B", "customerCode": "KB-120521", "orderDate": "2026-10-21", "customerReference": "", "itemCode": "FG-120521", "quantity": "1", "dueDate": "2026-10-21" }, "issues": [] }
    ],
    "createdIds": [
      "0ff957e7-269d-4c9e-a73d-9ea27e048860",
      "b05b381c-e188-4e50-ac46-8f463e89f72b"
    ],
    "bomCount": null,
    "salesOrderCount": 2
  },
  "message": "OK"
}
```

*(Rút gọn `actorId`, `companyId`, `fileName`, `fileSize`, `createdAt`. Thứ tự key trong `values` không cố định — đọc theo tên.)*

- `values` là **chuỗi** (đã chuẩn hoá: viết hoa mã, ngày ISO) — `quantity` là chuỗi số, không phải JSON number.
- Gửi lại đúng lệnh (`1fa305ab376d46d6`) và đọc lại (`7ab6808233ba43d8`): cùng `createdIds`, cùng `committedAt` `2026-10-18T05:05:24.574907Z`;
  đếm DB vẫn **2 đơn**.
- `GET /api/sales-orders/v1/0ff957e7-…` (`1869788118244968`): `orderNo: "SO-000006"`, `status: "DRAFT"`, khách `KA-120521`,
  `customerReference: "PO-77"`, 2 dòng — `orderedQuantity: 2000.000001` (ngày cần `2026-10-30`) và `orderedQuantity: 5.000000` (`2026-11-05`).
  Đơn thứ hai `SO-000007`, khách `KB-120521`, `customerReference: null`, 1 dòng `1.000000`.

### 4.2 Một nhóm lỗi khoá cả lô (A34) · `X-Trace-Id: 7db53f9c9cc24b90`

Nhóm A dòng 2 số lượng `0`; nhóm A dòng 3 và nhóm B hợp lệ. `201`, `status: "INVALID"`, `errorRowCount: 2`:

```json
"rows": [
  { "line": 2, "issues": [ { "field": "quantity", "column": "F", "code": "INVALID_FORMAT", "message": "Column F must be a number greater than 0 with at most 6 decimals" } ] },
  { "line": 3, "issues": [ { "field": "groupCode", "column": "A", "code": "RULE_VIOLATION", "message": "Order group A has errors on rows [2]; the whole order is rejected" } ] },
  { "line": 4, "issues": [] }
]
```

Xác nhận (`3c89fbbfbd804b47`) ⇒ `409 STATE_CONFLICT` "Import batch in status INVALID cannot be committed; fix the file and upload it again";
số đơn trong DB không đổi.

### 4.3 Khách ngừng sau khi `READY` (A36) · `X-Trace-Id: ae8ee56b9bbf4bed`

Tải lên nhóm của khách `KB-120521` (`READY`), ngừng khách đó, rồi xác nhận ⇒ `409 IMPORT_REVALIDATION_FAILED`, `result` là batch đã chuyển
`INVALID` với lỗi mới ở dòng 2: `{field: "customerCode", column: "B", code: "RULE_VIOLATION", message: "Customer is inactive"}`. 0 đơn được tạo.

---

## §5 — Envelope và hợp đồng lỗi

Không có mã lỗi mới. Ánh xạ bảng mã FE đề xuất:

| FE đề xuất | BE trả | HTTP | Đo live |
|---|---|---|---|
| `INVALID_FILE` | `FILE_FORMAT` | 400 | (GĐ3) |
| `FILE_TOO_LARGE` | `FILE_SIZE` — "File exceeds 5242880 bytes" | **413** | `b1d83d6d99e642c2` |
| `INVALID_TEMPLATE` | `TEMPLATE` — sai phiên bản / thiếu sheet `Sales orders` (đo live); sai tiêu đề / thừa cột (cùng bộ đọc của GĐ3, chưa đo live riêng cho `so`) | 400 | `e69371c987554ac7`, `4d344c17f69d4512` |
| `IMPORT_ROW_LIMIT_EXCEEDED` | `ROW_LIMIT` — "File has more than 500 data rows" | 400 | `d4e1af8a41554193` |
| `IMPORT_BATCH_INVALID` | `STATE_CONFLICT` | 409 | `3c89fbbfbd804b47` |
| `IMPORT_SOURCE_STALE` | `IMPORT_REVALIDATION_FAILED` **kèm `result` = batch** | 409 | `ae8ee56b9bbf4bed` |
| `IMPORT_BATCH_EXPIRED` | **không có** — batch không hết hạn | — | — |
| `IMPORT_GROUP_INVALID` (lỗi dòng) | `RULE_VIOLATION` ở field `groupCode` | (trong batch 201) | `7db53f9c9cc24b90` |
| `INVALID_QUANTITY` (lỗi dòng) | `INVALID_FORMAT` ở field `quantity` | (trong batch 201) | `7db53f9c9cc24b90` |

Lỗi dòng khác gặp trong lượt đo: `FORMULA` (ô có công thức, `d0faa96cff534385`), `INVALID_FORMAT` ở `orderDate`/`dueDate` ("must be a date
written yyyy-MM-dd"), `NOT_ALLOWED_VALUE` ở `itemCode` (vật tư không phải thành phẩm/bán thành phẩm), `RULE_VIOLATION` ở `dueDate` ("Must not
be before the order date") (`61ae325b402f4e56`), `GROUP_MISMATCH` ở `customerCode`/`orderDate`/`customerReference` (`488d220908d14e0f`),
`REFERENCE_NOT_FOUND` (mã không có), `CELL_TYPE` (ô ngày có giờ, ô số trơn ở cột ngày). Lỗi dòng **không** là lỗi HTTP: chúng nằm trong batch
`INVALID` của response `201`.

---

## §6 — Quyền, phạm vi và giới hạn đã biết

- Quyền: `PERM_SALES_ORDER_READ` **và** `PERM_SALES_ORDER_MANAGE` trên **nhà máy** (gán theo nhà máy, theo Công ty phủ nhà máy đó, hoặc
  toàn cục) — cho cả tải, xác nhận, lịch sử, chi tiết (Q22). Mỗi đơn khi tạo còn đi qua kiểm quyền tạo nháp của chính nó.
- Thiếu `plantId` ⇒ `400` nêu field `plantId` ("is required when kind is so") — cả tải lên lẫn lịch sử (`f911624cb9304362`, `d6efeb0637a646b3`).
  `X-Plant-Id` lệch ⇒ `409 STATE_CONFLICT` (`e3f9e3c0aea1427e`). Lịch sử của nhà máy khác ⇒ trang rỗng (`457bee2187024511`).
- Batch của người khác ⇒ `404`, không bao giờ `403`.
- Mỗi lần xác nhận tạo tối đa 500 dòng; 500 dòng thời gian tương đương nhập danh mục GĐ3 (≈ 3 giây đo ở GĐ3 — **chưa** đo riêng cho đơn bán).
- Đơn nhập chỉ là `DRAFT`: không tự xác nhận, không giữ tồn, không sinh nhu cầu hoạch định cho tới khi xác nhận đơn.

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

`mvn -o clean verify` — **1634 case unit / 187 class + 233 case IT / 34 class IT, failures = 0, errors = 0** (trước phần này: 1621 + 230).
Mới: `SalesOrderImportHandlerTest` (12 — đọc mẫu, ngày ISO/ô ngày thật/workbook 1904/ô có giờ/ô số trơn, luật nhóm, luật danh mục, tạo một
nháp mỗi nhóm); 3 ca tích hợp trên Postgres thật: **nhóm cuối lỗi khi ghi ⇒ 0 đơn, batch giữ `READY`, xác nhận lại tạo đúng 2 đơn**; một nhóm
lỗi ⇒ `INVALID`, 0 đơn; khách ngừng sau `READY` ⇒ `409`, 0 đơn.

Nghiệm thu bằng cách cố ý làm hỏng code (9 lần, 9/9 bị test bắt): bỏ lọc nhà máy ở lịch sử; đọc ngày theo hệ 1900 cố định; bỏ "loại cả nhóm";
bỏ tham chiếu khỏi luật nhóm; bỏ kiểm đơn vị ngừng; đếm `salesOrderCount` sai field; nhận ô ngày có giờ; bỏ kiểm định dạng ngày; tra khách bằng
tập rỗng.

### 7.2 Kiểm chứng qua HTTP thật

Backend thật (profile dev, Postgres + Redis thật), lượt **`120521`**: 33 lượt gọi ngoài đăng nhập (10 dựng dữ liệu + 23 kiểm), **mọi** status đúng kỳ vọng — A33/A34/A35/A36
như §4–§5; workbook **1904** với ô ngày thật `2026-10-20` ⇒ đọc đúng `2026-10-20` (`0d8ae3a142bc4243`). Fixture: công ty `GD6C-120521` (hai nhà
máy, ba vật tư, hai khách).

### 7.3 Chưa nghiệm thu

- Đo thời gian commit 500 dòng **đơn bán** (mỗi nhóm một lần tạo nháp — nhiều nhóm nhỏ sẽ chậm hơn ít nhóm lớn).
- Quyền với tài khoản chỉ có READ hoặc chỉ có MANAGE trên nhà máy — đã có test phân quyền chung của đường batch (GĐ3), chưa đo live riêng cho `so`.

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

| Việc | Vì sao | Ai chốt |
|---|---|---|
| Tải mẫu / tải file lỗi dạng XLSX từ server | Không có ở mọi loại batch; FE tự sinh | — |
| Xem lịch sử chỉ với quyền READ (Q22) | Giữ một luật cho mọi loại batch | PO nếu muốn đổi cho mọi loại |
| Chính sách xoá batch cũ (Q20) | Chưa có quyết định | PO chốt thời hạn |
| Nhập để **sửa/ghi đè** đơn có sẵn | FE §1.2 ngoài phạm vi | — |

**FE cần làm:** dùng `kind=so` + `plantId` trên đường batch chung; hiện `salesOrderCount` ở bước xem trước; sau xác nhận mở đơn bằng
`createdIds`; đọc lỗi ở `rows[].issues[]` (lỗi nhóm nằm ở field `groupCode`); ghi ngày trong file là `yyyy-MM-dd` hoặc ô ngày thật; chạy
A33–A36 và báo kết quả cùng A01–A32, A37–A38 của v1/v2.

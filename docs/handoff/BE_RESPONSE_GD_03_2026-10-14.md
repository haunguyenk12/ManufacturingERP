# BE trả lời — Giai đoạn 3: Nhập Excel dữ liệu nền

**Trả lời cho:** `FE_HANDOFF_GD_03_2026-10-12.md` (v1, P1) · **Ngày:** 14/10/2026 · **Bản:** v1
**Trạng thái:** BE đã làm xong và đo trên môi trường dev thật. Task chỉ đóng khi FE nghiệm thu A01–A26 trên adapter của FE.

---

## §0 — Kết luận

API nhập Excel cho **Vật tư / Nhà cung cấp / Khách hàng** đã có: kiểm tra file (không tạo dữ liệu) → xác nhận nhập
**nguyên lô trong một transaction** → lịch sử/chi tiết theo **chủ batch**. Path, DTO và mẫu `omniplant-import-v1` theo
đúng đề xuất của FE; **có 5 chỗ BE chọn khác** (bảng §2.3), mỗi chỗ có lý do.

| # | FE yêu cầu (§3.1) | Kết luận | Bằng chứng (lượt đo 14/10/2026 20:10 giờ VN) |
|---|---|---|---|
| R01 | Server đọc file gốc, kiểm mẫu/loại/ngôn ngữ/giới hạn | 🟡 **Thiếu thật, đã làm** | CSV, sai loại, sai header, thiếu metadata, 501 dòng, 5 MiB+1, macro, external link — đều bị từ chối, không tạo batch (§4.6) |
| R02 | Kiểm tra không tạo master; lưu batch INVALID/READY | 🟡 **Đã làm** | Khách hàng trong công ty: **0 → 0** sau kiểm tra |
| R03 | Commit lấy dữ liệu server đã lưu, kiểm lại, tạo nguyên lô | 🟡 **Đã làm** | 2 → tạo đúng 2; 500 dòng → tạo đúng **500** (≈ 2,98 s) |
| R04 | Retry cùng batch trả cùng `createdIds`/`committedAt` | 🟡 **Đã làm** + 🔴 **một lỗi BE tự phát hiện và đã sửa** | Lượt đo đầu: `committedAt` của lần commit và lần retry **lệch nhau ở chữ số thứ 7** — đã sửa; lượt đo lại: **khớp** (§1) |
| R05 | Hai batch cùng mã commit đồng thời ⇒ một lô thắng, lô kia rollback toàn bộ | 🟡 **Đã làm** | Test tự động trên PostgreSQL thật, 3 vòng: luôn đúng 1 lô thắng, lô thua **không** tạo cả dòng mã riêng của nó |
| R06 | History tìm/lọc/phân trang, chỉ batch của owner | 🟡 **Đã làm** | §4.4 |
| R07 | Detail sau reload; ID trái owner không lộ | 🟡 **Đã làm** | Người dùng khác mở batch ⇒ **404** |
| R08 | Bản ghi nhập xuất hiện ở API danh mục hiện có | ✅ **Có sẵn** | Vật tư nhập xong đọc được bằng `GET /api/v1/items/{id}` |
| R09 | Audit batch | 🟡 **Đã làm** | Mỗi lần kiểm tra và mỗi lần commit ghi một sự kiện audit; mỗi master tạo ra vẫn có audit tạo riêng như tạo bằng tay |

---

## §1 — Nguyên nhân / phạm vi ảnh hưởng

- Đây là tính năng **mới** cho FE, không phải sửa lỗi. Hệ thống trước đây đã có một API nhập Excel khác
  (`/api/v1/import-runs`, ánh xạ cột tự do, ghi **từng dòng**, có trạng thái "nhập một phần"). API đó **giữ nguyên**,
  không đổi gì; API mới cho FE là `/api/v1/data-import/batches` và **không nhìn thấy** batch của API cũ (và ngược lại).
- 🔴 **Lỗi BE tìm ra khi đo thật, đã sửa trước khi gửi file này:** thời điểm `committedAt` (và `createdAt` ngay sau
  upload) trong **response đầu tiên** dài hơn giá trị lưu trong DB một chữ số (`…612675100Z` so với `…612675Z`), nên
  một lần retry/reload sẽ thấy giá trị "khác". Nay response đầu tiên trả đúng giá trị đã lưu. Không ảnh hưởng dữ liệu.
- 🔴 **Một lỗi cùng họ, có từ trước, đã sửa luôn:** upload file **lớn hơn 10 MB** vào **bất kỳ** endpoint upload nào
  trước đây trả **500** (trông như server sập); nay trả **413 `FILE_SIZE`**.

---

## §2 — Contract

Mọi path đầy đủ, bắt đầu bằng `/api`. Mọi request cần `Authorization: Bearer <token>`.

### 2.1 Endpoint

| Method + path | Request | Thành công | Quyền |
|---|---|---|---|
| `POST /api/v1/data-import/batches` | `multipart/form-data`: `file`, `kind` (`items`\|`suppliers`\|`customers`), `companyId`, `plantId` (**bắt buộc** với `items`; với đối tác **bị bỏ qua** nếu gửi). Header tuỳ chọn: `Idempotency-Key`, `X-Plant-Id` | **201**, Batch (`READY` **hoặc** `INVALID`) | §6 |
| `POST /api/v1/data-import/batches/{id}/commit` | JSON `{"expectedVersion": <số nguyên ≥ 0>}`. Header tuỳ chọn `Idempotency-Key` | **200**, Batch `COMMITTED` | §6 |
| `GET /api/v1/data-import/batches` | query `companyId` (bắt buộc), `plantId?`, `kind?`, `status?` (`READY`\|`INVALID`\|`COMMITTED`), `search?` (tên file, không phân biệt hoa thường), `page=0`, `size=20` (1–100) | **200**, trang BatchSummary, **mới nhất trước** (hoà thì theo id) | §6 |
| `GET /api/v1/data-import/batches/{id}` | — | **200**, Batch đầy đủ | §6 |

### 2.2 DTO Batch / BatchSummary *(BatchSummary = Batch trừ `rows` và `createdIds`)*

| Field | Kiểu | Nullable | Nguồn giá trị | Ví dụ thật |
|---|---|---|---|---|
| `id` | UUID | Không | server sinh | `21c4de42-285e-41a9-b6e6-1f97a36cfac2` |
| `actorId` | UUID | Không | người gọi (từ token), **không** nhận từ request | `23fce5b6-…` |
| `companyId` | UUID | Không | request, đã kiểm tồn tại + đang hoạt động | |
| `plantId` | UUID | **Có** — `null` với đối tác | request (`items`) | |
| `kind` | `items`\|`suppliers`\|`customers` | Không | request | `items` |
| `locale` | `vi`\|`en` | Không | ô `C1` của sheet `_OmniPlant` | `vi` |
| `templateVersion` | string | Không | ô `A1` | `omniplant-import-v1` |
| `fileName` / `fileSize` | string / số nguyên byte | Không | tên file (đã bỏ đường dẫn) / số byte thật | `items-vi.xlsx` / `5514` |
| `status` | `READY`\|`INVALID`\|`COMMITTED` | Không | | |
| `version` | số nguyên | Không | 🔴 **bắt đầu từ `0`** sau upload, **+1** mỗi lần đổi trạng thái | `0` → `1` sau commit |
| `createdAt` / `committedAt` | ISO-8601 UTC | `committedAt` **có**, `null` trước commit | đồng hồ server, độ chính xác micro-giây | `2026-10-14T13:10:04.472194Z` |
| `rowCount` / `validRowCount` / `errorRowCount` | số nguyên ≥ 0 | Không | số **dòng có dữ liệu**; `errorRowCount` đếm dòng, không đếm lỗi | `6 / 1 / 5` |
| `createdCount` | số nguyên ≥ 0 | Không | `0` trước commit; `= rowCount` sau commit | |
| `rows[]` | mảng | Không (chỉ Batch) | mọi dòng có dữ liệu, theo thứ tự file | |
| `rows[].line` | số nguyên ≥ 2 | Không | **số dòng Excel gốc**, không đánh lại sau dòng trống | `8`, `10` |
| `rows[].values` | object string | Không | text đã trim; `code`/`unit` viết hoa; `type`/`tracking` **đổi sang giá trị chuẩn** khi hợp lệ; ô trống/ô bị từ chối = `""`. ⚠️ **thứ tự key không cố định** | `{"type":"RAW_MATERIAL","tracking":"LOT",…}` |
| `rows[].issues[]` | mảng `{field, column, code, message}` | Không, `[]` khi hợp lệ | `field` = tên trường FE, `column` = chữ cột Excel `A`–`G`, `code` = mã máy (§5.2), `message` = tiếng Anh | |
| `createdIds` | mảng UUID | Không (chỉ Batch) | `[]` trước commit; sau commit **đúng thứ tự `rows`** | |

Trường của `values`: vật tư `code, name, type, unit, tracking`; đối tác `code, name, contact, email, phone, address, notes`.
Khi tạo master: `tracking = LOT` ⇒ `lotTracked = true`; `serialTracked` luôn `false`; ô đối tác trống ⇒ **`null`** trong master.

### 2.3 Năm chỗ BE chọn khác đề xuất FE

| Đề xuất FE | BE làm | Lý do |
|---|---|---|
| `version` bắt đầu từ 1 | bắt đầu từ **0** | Đây là bộ đếm khoá lạc quan dùng chung toàn hệ thống (Đơn bán hàng cũng bắt đầu từ 0). FE chỉ cần gửi lại **đúng số đã nhận** |
| Commit batch không READY ⇒ `IMPORT_BATCH_NOT_READY` | **`STATE_CONFLICT`** (409) | Mã chung của hệ thống cho "sai trạng thái"; trên endpoint commit nó chỉ có một nghĩa: batch đang `INVALID` |
| `expectedVersion` lệch ⇒ `IMPORT_BATCH_VERSION_CONFLICT` | **`CONCURRENT_MODIFICATION`** (409) | Mã chung đã dùng cho `expectedVersion` ở mọi nơi khác |
| Header `X-Plant-Id` lệch ⇒ `400 PLANT_CONTEXT_MISMATCH` | **`409 STATE_CONFLICT`** | Quy ước có sẵn của mọi endpoint nhận `X-Plant-Id`. Header **sai định dạng** ⇒ `400 FIELD_FORMAT_INVALID` |
| Nhà máy không thuộc công ty ⇒ 400 | **`422 RESOURCE_SCOPE_MISMATCH`** | Quy ước có sẵn: hai bản ghi khác chủ là 422 |

Mã lỗi **theo dòng** (§5.2) dùng bộ mã BE đã có, cộng 3 mã mới đúng tên FE — bảng đối chiếu ở §5.2.

---

## §3 — Breaking change

**Wire: KHÔNG có** với client đang chạy.

- Bốn endpoint `/api/v1/data-import/batches*` là **mới**.
- API nhập cũ `/api/v1/import-runs*` không đổi request/response; danh sách đích nhập (`GET /api/v1/import-targets`)
  nay có thêm `SUPPLIER`, `CUSTOMER` — **thêm**, không bỏ gì.
- Upload > 10 MB vào endpoint upload bất kỳ: **500 → 413 `FILE_SIZE`** — loại **nới lỏng/sửa sai**, client đúng không phải sửa.
- Không đổi giá trị mặc định query param, không đổi thứ tự sắp xếp nào có sẵn, không bỏ field nào.

---

## §4 — Ví dụ copy từ lần gọi thật

Lượt đo 14/10/2026 20:10 (giờ VN), fixture hậu tố `201002`. `Authorization` đã che. Response giữ nguyên văn (chỉ xuống dòng).

### 4.1 Kiểm tra file vật tư (VI) — 201 READY

```http
POST /api/v1/data-import/batches
Authorization: Bearer <da-che>
X-Plant-Id: 9e42aecf-8f3b-4e68-a8bc-f0334ad1d209
Content-Type: multipart/form-data

file: items-vi.xlsx (5514 byte) — Dữ liệu dòng 2: "GD3-RM-201002" | "Nguyên liệu minh họa" | "Nguyên vật liệu" | "g3a201002" | "Có"
kind: items
companyId: d40a23fd-03d5-46bc-b93d-9d95b6c5b71e
plantId: 9e42aecf-8f3b-4e68-a8bc-f0334ad1d209
```

`HTTP 201` · `X-Trace-Id: d31dfe2cab33491a`

```json
{
  "code": "SUCCESS",
  "result": {
    "id": "21c4de42-285e-41a9-b6e6-1f97a36cfac2",
    "actorId": "23fce5b6-dd3b-4d30-a8b8-c33962a38a82",
    "companyId": "d40a23fd-03d5-46bc-b93d-9d95b6c5b71e",
    "plantId": "9e42aecf-8f3b-4e68-a8bc-f0334ad1d209",
    "kind": "items",
    "locale": "vi",
    "templateVersion": "omniplant-import-v1",
    "fileName": "items-vi.xlsx",
    "fileSize": 5514,
    "status": "READY",
    "version": 0,
    "createdAt": "2026-10-14T13:10:04.391806Z",
    "committedAt": null,
    "rowCount": 1,
    "validRowCount": 1,
    "errorRowCount": 0,
    "createdCount": 0,
    "rows": [
      {
        "line": 2,
        "values": {
          "code": "GD3-RM-201002",
          "name": "Nguyên liệu minh họa",
          "type": "RAW_MATERIAL",
          "unit": "G3A201002",
          "tracking": "LOT"
        },
        "issues": []
      }
    ],
    "createdIds": []
  },
  "message": "Created successfully"
}
```

### 4.2 Xác nhận nhập — 200 COMMITTED

```http
POST /api/v1/data-import/batches/21c4de42-285e-41a9-b6e6-1f97a36cfac2/commit
Authorization: Bearer <da-che>
Content-Type: application/json

{"expectedVersion": 0}
```

`HTTP 200` · `X-Trace-Id: 9789ed25bfb94f14`

```json
{
  "code": "SUCCESS",
  "result": {
    "id": "21c4de42-285e-41a9-b6e6-1f97a36cfac2",
    "actorId": "23fce5b6-dd3b-4d30-a8b8-c33962a38a82",
    "companyId": "d40a23fd-03d5-46bc-b93d-9d95b6c5b71e",
    "plantId": "9e42aecf-8f3b-4e68-a8bc-f0334ad1d209",
    "kind": "items",
    "locale": "vi",
    "templateVersion": "omniplant-import-v1",
    "fileName": "items-vi.xlsx",
    "fileSize": 5514,
    "status": "COMMITTED",
    "version": 1,
    "createdAt": "2026-10-14T13:10:04.391806Z",
    "committedAt": "2026-10-14T13:10:04.472194Z",
    "rowCount": 1,
    "validRowCount": 1,
    "errorRowCount": 0,
    "createdCount": 1,
    "rows": [
      {
        "line": 2,
        "values": {
          "code": "GD3-RM-201002",
          "name": "Nguyên liệu minh họa",
          "type": "RAW_MATERIAL",
          "unit": "G3A201002",
          "tracking": "LOT"
        },
        "issues": []
      }
    ],
    "createdIds": [
      "dc319b3f-ce3d-4b5e-9e04-d87c7ed6a0c1"
    ]
  },
  "message": "OK"
}
```

**Retry sau khi mất response** (cùng batch khách hàng `33a29365-…`, cùng `Idempotency-Key: CM-201002-1`, cùng
`{"expectedVersion": 0}` dù batch đã ở `version` 1) ⇒ `HTTP 200` · `X-Trace-Id: 809447b46e86409c`, trả lại **đúng**
`createdIds` `["7cee7f51-33ca-43fd-b5b4-e6d05d4fce76","99f82138-0494-4f09-baca-c7cfd0570f15"]` và
`committedAt` `2026-10-14T13:10:03.771356Z` như lần đầu; số khách hàng **giữ nguyên 2**. Commit lại với **key mới** cũng trả receipt
đó (200), không tạo thêm.

### 4.3 File có dòng lỗi — vẫn là 201, batch INVALID

File `loi.xlsx` (khách hàng, EN): dòng 2–7 trống; dòng 8 mã và điện thoại nhập dạng **số**; dòng 10 tên là **công thức**, người
liên hệ là **ngày**, email là **TRUE/FALSE**; dòng 10 và 11 mã `abc-201002`/`ABC-201002`; dòng 11 tên 256 ký tự, email sai;
dòng 12 mã đã tồn tại; dòng 13 mã có khoảng trắng, tên rỗng; dòng 14 hợp lệ.

`HTTP 201` · `X-Trace-Id: ba6dff9524be40a0` (trích — mọi field khác như 4.1, `status` `INVALID`, `rowCount` 6, `validRowCount` 1, `errorRowCount` 5):

```json
"rows": [
  {"line": 8, "values": {"code": "", "name": "Numeric code", "contact": "", "email": "", "phone": "", "address": "", "notes": ""},
   "issues": [
     {"field": "code", "column": "A", "code": "TEXT_REQUIRED", "message": "Cell is a number; format the column as Text so leading zeros are kept"},
     {"field": "phone", "column": "E", "code": "TEXT_REQUIRED", "message": "Cell is a number; format the column as Text so leading zeros are kept"}]},
  {"line": 10, "values": {"code": "ABC-201002", "name": "", "contact": "", "email": "", "phone": "", "address": "", "notes": ""},
   "issues": [
     {"field": "name", "column": "B", "code": "FORMULA", "message": "Cell contains a formula; enter the value itself"},
     {"field": "contact", "column": "C", "code": "CELL_TYPE", "message": "Cell is a date; this column expects text"},
     {"field": "email", "column": "D", "code": "CELL_TYPE", "message": "Cell type BOOLEAN is not accepted; enter plain text"},
     {"field": "code", "column": "A", "code": "DUPLICATE_IN_FILE", "message": "Code 'ABC-201002' appears on several rows of this file: [10, 11]"}]},
  {"line": 11, "values": {"code": "ABC-201002", "name": "xxxx…(256 ký tự)", "contact": "", "email": "not-an-email", "phone": "", "address": "", "notes": ""},
   "issues": [
     {"field": "name", "column": "B", "code": "TOO_LONG", "message": "Column B allows at most 255 characters, got 256"},
     {"field": "email", "column": "D", "code": "INVALID_FORMAT", "message": "Email is not a valid address"},
     {"field": "code", "column": "A", "code": "DUPLICATE_IN_FILE", "message": "Code 'ABC-201002' appears on several rows of this file: [10, 11]"}]},
  {"line": 12, "values": {"code": "KH-201002-1", "name": "Existing code", "contact": "", "email": "", "phone": "", "address": "", "notes": ""},
   "issues": [{"field": "code", "column": "A", "code": "ALREADY_EXISTS", "message": "Customer code 'KH-201002-1' already exists in this company"}]},
  {"line": 13, "values": {"code": "BAD CODE!", "name": "", "contact": "", "email": "", "phone": "", "address": "", "notes": ""},
   "issues": [
     {"field": "code", "column": "A", "code": "INVALID_FORMAT", "message": "Code may only contain A-Z, 0-9, dot, underscore or hyphen"},
     {"field": "name", "column": "B", "code": "REQUIRED_MISSING", "message": "Column B is required"}]},
  {"line": 14, "values": {"code": "KH-201002-14", "name": "Valid row", "contact": "", "email": "", "phone": "", "address": "", "notes": ""},
   "issues": []}
]
```

Commit batch này ⇒ `HTTP 409` · `X-Trace-Id: b43b46fa110c4c0e`:

```json
{
  "code": "STATE_CONFLICT",
  "message": "Import batch in status INVALID cannot be committed; fix the file and upload it again"
}
```

Vật tư: đơn vị không tồn tại ⇒ `unit`/`D`/`REFERENCE_NOT_FOUND`; loại `SERVICE` ⇒ `type`/`C`/`NOT_ALLOWED_VALUE`; theo dõi lô `Maybe` ⇒
`tracking`/`E`/`NOT_ALLOWED_VALUE` (`X-Trace-Id: b3984672e9c94a65`).

### 4.4 Dữ liệu đổi sau khi kiểm tra — 409 kèm batch INVALID

Đơn vị `G3B201002` bị ngừng **sau** khi batch `READY`, rồi commit với `{"expectedVersion": 0}`:

`HTTP 409` · `X-Trace-Id: f733a1cf235e4252`

```json
{
  "code": "IMPORT_REVALIDATION_FAILED",
  "result": {
    "id": "e1987011-c006-4a11-a8ad-e0b667db8b2e",
    "actorId": "23fce5b6-dd3b-4d30-a8b8-c33962a38a82",
    "companyId": "d40a23fd-03d5-46bc-b93d-9d95b6c5b71e",
    "plantId": "9e42aecf-8f3b-4e68-a8bc-f0334ad1d209",
    "kind": "items",
    "locale": "en",
    "templateVersion": "omniplant-import-v1",
    "fileName": "stale.xlsx",
    "fileSize": 5436,
    "status": "INVALID",
    "version": 1,
    "createdAt": "2026-10-14T13:10:04.961391Z",
    "committedAt": null,
    "rowCount": 1,
    "validRowCount": 0,
    "errorRowCount": 1,
    "createdCount": 0,
    "rows": [
      {
        "line": 2,
        "values": {
          "code": "GD3-ST-201002",
          "name": "Stale",
          "type": "CONSUMABLE",
          "unit": "G3B201002",
          "tracking": "NONE"
        },
        "issues": [
          {
            "field": "unit",
            "column": "D",
            "code": "RULE_VIOLATION",
            "message": "Unit of measure 'G3B201002' is inactive"
          }
        ]
      }
    ],
    "createdIds": []
  },
  "message": "Import batch is no longer valid; nothing was created"
}
```

Tương tự khi mã bị tạo bằng màn hình danh mục sau khi batch `READY` (`X-Trace-Id: c6c045bec43040f0`): 409, dòng 3
`code`/`A`/`ALREADY_EXISTS`, dòng 2 vẫn hợp lệ nhưng **cũng không được tạo** — số khách hàng **3 → 3**.

### 4.5 Lịch sử

```http
GET /api/v1/data-import/batches?companyId=d40a23fd-03d5-46bc-b93d-9d95b6c5b71e&kind=customers&status=COMMITTED&page=0&size=2
```

`HTTP 200` · `X-Trace-Id: 2d96ab8edb9b4007`

```json
{
  "code": "SUCCESS",
  "result": {
    "content": [
      {
        "id": "7f7f2335-f0fa-4207-b464-0846a6e69203",
        "actorId": "23fce5b6-dd3b-4d30-a8b8-c33962a38a82",
        "companyId": "d40a23fd-03d5-46bc-b93d-9d95b6c5b71e",
        "plantId": null,
        "kind": "customers",
        "locale": "en",
        "templateVersion": "omniplant-import-v1",
        "fileName": "500.xlsx",
        "fileSize": 12338,
        "status": "COMMITTED",
        "version": 1,
        "createdAt": "2026-10-14T13:10:06.086545Z",
        "committedAt": "2026-10-14T13:10:09.191302Z",
        "rowCount": 500,
        "validRowCount": 500,
        "errorRowCount": 0,
        "createdCount": 500
      },
      {
        "id": "33a29365-4743-463d-99da-d64f836ba3d3",
        "actorId": "23fce5b6-dd3b-4d30-a8b8-c33962a38a82",
        "companyId": "d40a23fd-03d5-46bc-b93d-9d95b6c5b71e",
        "plantId": null,
        "kind": "customers",
        "locale": "en",
        "templateVersion": "omniplant-import-v1",
        "fileName": "customers-en.xlsx",
        "fileSize": 5522,
        "status": "COMMITTED",
        "version": 1,
        "createdAt": "2026-10-14T13:10:03.622892Z",
        "committedAt": "2026-10-14T13:10:03.771356Z",
        "rowCount": 2,
        "validRowCount": 2,
        "errorRowCount": 0,
        "createdCount": 2
      }
    ],
    "page": 0,
    "size": 2,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "message": "OK"
}
```

### 4.6 Lỗi cấp file (không tạo batch) — mỗi dòng là một lần gọi thật

| Tình huống | HTTP | Body | `X-Trace-Id` |
|---|---|---|---|
| File `data.csv` | 400 | `{"code":"FILE_FORMAT","message":"Only .xlsx workbooks are accepted (not .xls, .xlsm or .csv)"}` | `ddf0565daf8f4ce4` |
| File khách hàng gửi với `kind=suppliers` | 400 | `{"code":"TEMPLATE","message":"Template is for 'customers', not 'suppliers'"}` | `cf92583474344473` |
| Mẫu trống (style tới dòng 501) | 400 | `{"code":"EMPTY_FILE","message":"File contains no data rows"}` | `ce620b1da4ee4af6` |
| 501 dòng | 400 | `{"code":"ROW_LIMIT","message":"File has more than 500 data rows"}` | `1bf7257e0f164182` |
| 5 242 881 byte | 413 | `{"code":"FILE_SIZE","message":"File exceeds 5242880 bytes"}` | `67ddeb29337c4dfe` |
| 11 MB | 413 | `{"code":"FILE_SIZE","message":"Uploaded file exceeds the server upload limit"}` | `8c5c8dfc9396484c` |
| `.xlsx` có `xl/vbaProject.bin` | 400 | `{"code":"FILE_FORMAT","message":"Workbooks with macros, ActiveX or external links are not accepted"}` | `beb3b54802cd4a05` |
| `kind=items` thiếu `plantId` | 400 | `{"code":"VALIDATION_ERROR","message":"plantId: is required when kind is items","errors":[{"field":"plantId","message":"is required when kind is items"}]}` | `7f055ee2c5ff4c05` |
| `kind=boms` | 400 | `{"code":"VALIDATION_ERROR","message":"kind: must be one of [items, suppliers, customers]","errors":[{"field":"kind","message":"must be one of [items, suppliers, customers]"}]}` | `468c76b3c0974a51` |
| `X-Plant-Id` khác `plantId` | 409 | `{"code":"STATE_CONFLICT","message":"X-Plant-Id does not match the plant referenced by this request"}` | `8be85b12427640b6` |
| Nhà máy của công ty khác | 422 | `{"code":"RESOURCE_SCOPE_MISMATCH","message":"Plant does not belong to company"}` | `4a85d3d1d95a4168` |
| Cùng `Idempotency-Key` upload, file khác | 409 | `{"code":"IDEMPOTENCY_CONFLICT","message":"Idempotency-Key was already used with a different payload"}` | `68af8b80e9bf4532` |

---

## §5 — Envelope và hợp đồng lỗi

Mọi response dùng cùng một envelope:

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "quantity", "message": "must be greater than 0" } ] }
```

- `code` là tên hằng — FE rẽ nhánh theo `code`, **không** theo `message` (tiếng Anh, có thể đổi).
- `result` thường **vắng mặt** khi lỗi. 🔴 **Ngoại lệ duy nhất:** `409 IMPORT_REVALIDATION_FAILED` mang `result` = batch vừa chuyển `INVALID`.
- `errors[]` chỉ có khi lỗi theo field của **request** (không phải lỗi theo dòng Excel — những lỗi đó nằm trong `rows[].issues`).

### 5.1 Lỗi HTTP

| Tình huống | HTTP | `code` | `errors[]`? |
|---|---|---|---|
| Không phải `.xlsx`, `.xlsm`, file hỏng, có macro / ActiveX / external link | 400 | `FILE_FORMAT` | — |
| > 5 MiB, > 10 MB (giới hạn upload chung), hoặc giải nén > 25 MiB | **413** | `FILE_SIZE` | — |
| Sai mẫu: thiếu `_OmniPlant`, sai phiên bản / loại / ngôn ngữ, sai header, có cột thứ 6 (vật tư) / thứ 8 (đối tác) | 400 | `TEMPLATE` | — |
| Không có dòng dữ liệu / > 500 dòng | 400 | `EMPTY_FILE` / `ROW_LIMIT` | — |
| `kind` sai, thiếu `plantId` cho vật tư, `status` lọc sai, thiếu `expectedVersion` | 400 | `VALIDATION_ERROR` | ✅ |
| `X-Plant-Id` sai định dạng | 400 | `FIELD_FORMAT_INVALID` | — |
| Thiếu cặp quyền | 403 | `PERMISSION_DENIED` | — |
| Batch không tồn tại **hoặc của người khác** | 404 | `ENTITY_NOT_FOUND` | — |
| Commit batch `INVALID` | 409 | `STATE_CONFLICT` | — |
| `expectedVersion` lệch (batch chưa commit) | 409 | `CONCURRENT_MODIFICATION` | — |
| Dữ liệu đổi sau khi kiểm tra / thua cuộc đua mã | 409 | `IMPORT_REVALIDATION_FAILED` + **`result`** | — |
| `Idempotency-Key` đã dùng với file/batch/version khác | 409 | `IDEMPOTENCY_CONFLICT` | — |
| `X-Plant-Id` khác `plantId` | 409 | `STATE_CONFLICT` | — |
| Nhà máy không thuộc công ty | 422 | `RESOURCE_SCOPE_MISMATCH` | — |
| Công ty / nhà máy đang ngừng | 422 | `RESOURCE_INACTIVE` | — |
| Lỗi DB/server | 500 | `INTERNAL_SERVER_ERROR` | — |

🔴 `STATE_CONFLICT`, `CONCURRENT_MODIFICATION`, `IMPORT_REVALIDATION_FAILED`, `IDEMPOTENCY_CONFLICT` **cùng 409** — phải rẽ theo `code`.

### 5.2 Mã lỗi theo dòng (`rows[].issues[].code`) — đối chiếu với mã FE

| Mã FE đề xuất | Mã BE trả | `field` |
|---|---|---|
| `CODE_FORMAT` | `INVALID_FORMAT` (mã sai) · `REQUIRED_MISSING` (mã rỗng) | `code` |
| `REQUIRED_NAME` | `REQUIRED_MISSING` | `name` (cũng dùng cho `type`, `unit`, `tracking` rỗng) |
| `TOO_LONG` | `TOO_LONG` | mọi field có giới hạn |
| `ENUM` | `NOT_ALLOWED_VALUE` | `type`, `tracking` |
| `INVALID_EMAIL` | `INVALID_FORMAT` | `email` |
| `DUPLICATE_FILE` | `DUPLICATE_IN_FILE` — gắn cho **mọi** dòng trùng, không chỉ dòng sau | `code` |
| `DUPLICATE_CODE` | `ALREADY_EXISTS` | `code` |
| `INACTIVE_REFERENCE` | `REFERENCE_NOT_FOUND` (đơn vị không tồn tại) · `RULE_VIOLATION` (đơn vị đang ngừng) | `unit` |
| `FORMULA` · `TEXT_REQUIRED` · `CELL_TYPE` | **giữ nguyên tên** | ô tương ứng |

Mỗi field báo **tối đa một lỗi**; một dòng có thể có nhiều lỗi ở nhiều field.

---

## §6 — Quyền, phạm vi và giới hạn đã biết

| Loại | Cần **cả hai** quyền | Phạm vi được chấp nhận |
|---|---|---|
| `items` | `PERM_ITEM_READ` + `PERM_ITEM_MANAGE` | gán ở **nhà máy** được chọn, hoặc **công ty** chứa nhà máy đó, hoặc toàn hệ thống (Q2: **Có**) |
| `suppliers` | `PERM_SUPPLIER_READ` + `PERM_SUPPLIER_MANAGE` | gán ở công ty, hoặc toàn hệ thống |
| `customers` | `PERM_CUSTOMER_READ` + `PERM_CUSTOMER_MANAGE` | gán ở công ty, hoặc toàn hệ thống |

- **Không** có permission nhập riêng. READ của loại này + MANAGE của loại khác ⇒ **403** (đã kiểm).
- Role có sẵn: **ADMIN + MANAGER** có đủ ba cặp; **OPERATOR** chỉ có `PERM_ITEM_READ` ⇒ không nhập được loại nào.
- Quyền được kiểm **ở mỗi request**, kể cả khi chỉ đọc lại receipt của batch đã commit: mất quyền ⇒ 403.
- Lịch sử/chi tiết **chỉ của chính người gọi**; quản trị viên cũng chỉ thấy batch của mình (Q13: **Có**).
- `kind=items` khi xem lịch sử **bắt buộc** `plantId` (400 nếu thiếu); không truyền `kind` + không có `plantId` ⇒ chỉ trả đối tác.
- 🔴 **Giới hạn đã biết:**
  1. Điện thoại **nhà cung cấp** tối đa **60** ký tự (khách hàng 255) — mẫu FE ghi 255 cho cả hai. Vượt ⇒ `TOO_LONG` ở cột `E`.
  2. Không có timeout riêng cho việc đọc file: thời gian bị chặn bởi ngưỡng 5 MiB / 64 phần trong archive / 25 MiB giải nén / 10 000 dòng có style.
  3. Commit 500 dòng mất khoảng **3 giây** trên máy dev (đo thật: 2 981 ms). FE nên khoá nút và hiện trạng thái chờ.
  4. Thứ tự key trong `rows[].values` **không cố định** giữa các response (đọc theo tên field, đừng theo thứ tự).

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

`mvn -o clean verify` với Docker (PostgreSQL thật trong container): **1394 test đơn vị + 192 test tích hợp, failures = 0,
errors = 0** (trước task: 1325 + 184). Riêng phần nhập Excel: 69 test đơn vị mới + 8 test tích hợp mới, gồm:

- Rollback thật khi dòng **cuối** lỗi: 2 dòng đầu đã ghi bị huỷ, batch vẫn `READY`, sửa xong commit lại tạo **đúng 3**.
- Hai batch có chung một mã commit **đồng thời** (3 vòng): luôn đúng 1 lô thắng, lô thua về `INVALID` và **không** tạo dòng riêng của nó.
- Cùng một batch commit đồng thời hai lần: một receipt, không nhân đôi.
- Receipt đọc lại có `committedAt` và `createdIds` **trùng khớp** với response commit đầu tiên.

**Kiểm chứng "test có bắt được lỗi thật không":** cố ý phá 10 chỗ trong code (bỏ cơ chế bắt lỗi trùng mã ở dòng cuối,
bỏ lọc theo chủ batch, làm lịch sử mất batch đối tác, đổi mã 409 sang mã 409 khác, bỏ kiểm quyền cho chủ batch, chỉ gắn lỗi
trùng cho dòng sau, bỏ kiểm tra lại trước khi ghi, nhận ô số ở cột mã, và hai bản sửa thời gian ở §1) — **10/10 lần test đỏ đúng chỗ**;
đã khôi phục trước lượt chạy cuối.

### 7.2 Kiểm chứng qua HTTP thật

- **Môi trường:** backend local (profile dev), cổng 8081, PostgreSQL + Redis thật qua Docker. Tài khoản quản trị + một tài khoản
  không có quyền tạo riêng cho lượt đo. File `.xlsx` do BE **tự dựng theo đúng §3.2** của tài liệu FE (sheet `Dữ liệu`/`Data`,
  `_OmniPlant` ẩn `veryHidden`).
- **Lượt 1** (20:05, hậu tố `200509`): 78 lượt gọi, mọi HTTP status đúng kỳ vọng — **nhưng** phát hiện lỗi `committedAt` ở §1.
- **Lượt 2** (20:10, hậu tố `201002`), bản đã sửa: **78/78 đúng kỳ vọng**. Chứng minh đúng bản build: cùng phép đo
  "`committedAt` của commit so với retry" cho kết quả **khác** ở lượt 1 và **khớp** ở lượt 2.
- **Dữ liệu dọn:** công ty, nhà máy, đơn vị đo và tài khoản thử đã **ngừng hoạt động** cuối lượt; khách hàng/nhà cung cấp/vật tư
  thử nằm trong hai công ty đã ngừng `GD3-200509`, `GD3-201002`.

| Ca FE | Kết quả lượt 2 | `X-Trace-Id` |
|---|---|---|
| A01 ba loại × VI/EN | khách hàng EN 0 → **0** sau kiểm tra → **2** sau commit; vật tư VI 0 → 1, `lotTracked` đúng; nhà cung cấp VI tạo được | `acd852c52c8146a2`, `543085b760c747bb`, `d31dfe2cab33491a`, `9789ed25bfb94f14`, `a9cf9c13a3ea4df9` |
| A02 mẫu trống | `400 EMPTY_FILE` | `ce620b1da4ee4af6` |
| A03 csv / xlsm / sai loại / sai header / thiếu metadata | 400 `FILE_FORMAT` / `FILE_FORMAT` / `TEMPLATE` / `TEMPLATE` / `TEMPLATE` | §4.6 |
| A04 5 MiB+1 / 11 MB / 501 dòng / **500 dòng** | 413 / 413 / 400 / **201 rồi commit tạo đúng 500** | `67ddeb29337c4dfe`, `8c5c8dfc9396484c`, `1bf7257e0f164182`, `4f6f9cb267d8411e` |
| A05 dòng trống xen giữa | lỗi báo đúng **dòng 8, 10…** | `ba6dff9524be40a0` |
| A06 mã/điện thoại dạng số | `TEXT_REQUIRED` cột A và E; dạng Text giữ `0000123456` | `ba6dff9524be40a0`, `acd852c52c8146a2` |
| A07 công thức / ngày / boolean | `FORMULA` / `CELL_TYPE` / `CELL_TYPE` | `ba6dff9524be40a0` |
| A08 mã sai, tên rỗng/quá dài, email sai | đúng field + mã, dòng hợp lệ còn lại **không** được tạo | `ba6dff9524be40a0` |
| A09 `abc` / `ABC` | **cả hai** dòng `DUPLICATE_IN_FILE` | `ba6dff9524be40a0` |
| A10 mã đã có | `ALREADY_EXISTS`, bản ghi cũ không bị sửa | `ba6dff9524be40a0` |
| A11 nhà cung cấp trùng mã khách hàng | **cho phép** (201, commit được) | `a9cf9c13a3ea4df9`, `beb82d3e81ca48a8` |
| A12 đơn vị không có / `SERVICE` / theo dõi lô lạ | `REFERENCE_NOT_FOUND` / `NOT_ALLOWED_VALUE` ×2 | `b3984672e9c94a65` |
| A13 ngừng đơn vị sau READY | `409 IMPORT_REVALIDATION_FAILED` + batch `INVALID`, 0 vật tư mới | `f733a1cf235e4252` |
| A14 tạo mã qua màn hình danh mục sau READY | 409, khách hàng **3 → 3** | `c6c045bec43040f0` |
| A16 commit lại bằng key mới | 200, cùng receipt, không tạo thêm | `873b6548dc334955` |
| A17 retry / reload | `createdIds` + `committedAt` **khớp** | `809447b46e86409c` |
| A19 tài khoản không quyền nhập | 403 | `1e1bd466df63400c` |
| A20 tài khoản khác mở / commit / xem lịch sử | 404 / 404 / 403 | `58a9de167683459a`, `88b9d6932ad54da7`, `08c8b49f25b64e8f` |
| A22 commit batch INVALID | `409 STATE_CONFLICT` | `b43b46fa110c4c0e` |
| A23 lịch sử lọc + trang | §4.5; `kind=items` thiếu nhà máy 400; `status=APPLIED` 400 | `2d96ab8edb9b4007` |
| A24 nhà máy lệch / khác công ty / key khác version | 409 / 422 / `409 IDEMPOTENCY_CONFLICT` | `8be85b12427640b6`, `4a85d3d1d95a4168`, `49fb5df2f3f642bc` |
| A25 upload cùng key cùng file / khác file | **cùng batch id** / `409 IDEMPOTENCY_CONFLICT` | `68af8b80e9bf4532` |
| A26 macro / external link | 400 `FILE_FORMAT` | `beb3b54802cd4a05`, `da46fb098e894252` |
| API nhập cũ mở batch mới | 404 — hai API không thấy nhau | `b8dd1dd2d60e4924` |

### 7.3 Chưa nghiệm thu

| Mục | Vì sao | Cần gì để đo |
|---|---|---|
| **File `.xlsx` do chính FE sinh ra** | BE không có repo FE; mọi file đo đều do BE dựng theo §3.2 | FE gửi kèm 3 file mẫu trống + 3 file minh họa (VI/EN) để BE chạy lại cùng bộ đo |
| A15 qua HTTP | Cuộc đua hai commit chỉ đo bằng test tích hợp (PostgreSQL thật), không qua HTTP | Có thể đo khi FE nghiệm thu bằng hai tab |
| A18 qua HTTP | "Lỗi DB ở dòng cuối" chỉ gây được trong test (giả lập lỗi), không gây được an toàn trên server đang chạy | — |
| A21 "thu hồi quyền **sau** khi đã commit" | Đo bằng test bảo mật (bị chặn 403 trước khi đọc receipt); smoke HTTP chỉ dùng tài khoản không quyền từ đầu | Tài khoản có quyền rồi bị thu hồi giữa chừng |
| Tài khoản phạm vi **nhà máy** nhập vật tư | Đo bằng test bảo mật; lượt HTTP dùng tài khoản quản trị | Tài khoản MANAGER gán phạm vi nhà máy |

---

## §8 — Câu trả lời Q1–Q14, việc chưa làm, việc FE cần làm

### 8.1 Q1–Q14

| # | Câu hỏi | Trả lời |
|---|---|---|
| Q1 | Atomic create-only, không partial/upsert? | **Có.** Mã đã tồn tại (kể cả đã ngừng) là lỗi dòng, không bao giờ sửa/kích hoạt lại bản ghi cũ |
| Q2 | Vật tư: quyền công ty phủ nhà máy? | **Có** — §6 |
| Q3 | Bốn path + quy ước `X-Plant-Id`? | **Có**, path đúng như FE đề xuất. `X-Plant-Id` chỉ đối chiếu ở upload/list vật tư khi có `plantId`; lệch ⇒ **409 `STATE_CONFLICT`** (khác đề xuất 400, xem §2.3) |
| Q4 | Server parse file gốc, không nhận rows từ client? | **Có.** Commit chỉ nhận `expectedVersion` |
| Q5 | Giữ metadata/header/mapping VI/EN §3.2? | **Có**, đúng từng chữ. Nhãn loại/theo dõi lô so **không phân biệt hoa thường**, chỉ theo ngôn ngữ của file (+ giá trị chuẩn `RAW_MATERIAL`…, `NONE`/`LOT`) |
| Q6 | Dòng lỗi ⇒ 201 INVALID; commit stale ⇒ 409 kèm batch? | **Có** — §4.3, §4.4 |
| Q7 | Retry COMMITTED trả receipt trước khi kiểm version, sau khi kiểm quyền? | **Có.** Thứ tự: quyền → key → receipt → trạng thái → version |
| Q8 | Mã duy nhất theo công ty + loại, gồm cả đã ngừng, không theo nhà máy? | **Có** (đã đúng từ giai đoạn 2) |
| Q9 | Ô đối tác trống ⇒ `null` trong master? | **Có.** `values` trong preview vẫn là `""` |
| Q10 | Server strict với boolean/kiểu ô lạ? | **Có** — boolean/ngày/lỗi ⇒ `CELL_TYPE`; ô số ở **cột mã và điện thoại** ⇒ `TEXT_REQUIRED`; ô số ở cột khác đọc theo giá trị **hiển thị**; rich text ghép thành text |
| Q11 | Ngưỡng server? | 5 242 880 byte/file (đường nhập này), 10 MB giới hạn upload chung, ≤ 500 dòng có dữ liệu, ≤ 64 phần trong archive, ≤ 25 MiB **giải nén thật**, ≤ 10 000 dòng có style; từ chối macro, ActiveX, external link; không chạy công thức, không tải liên kết |
| Q12 | Lưu trữ batch/receipt? | Batch, dòng, lỗi, receipt lưu **vô thời hạn** cùng chính sách lịch sử master; **file gốc không lưu** (chỉ tên, kích thước, mã băm SHA-256). Chưa có chính sách xoá — cần PO chốt nếu muốn có |
| Q13 | Chỉ owner xem, chưa có admin xem của người khác? | **Có** |
| Q14 | Đồng bộ 500 dòng, chưa cần hàng đợi? | **Có.** Đo thật 500 dòng commit ≈ 3 giây |

### 8.2 Chưa làm (có chủ đích)

| Mục | Vì sao | Điều kiện để làm |
|---|---|---|
| Endpoint tải mẫu / báo cáo lỗi | FE đã tự tạo (§3.2 của FE ghi bản đầu không cần) | FE yêu cầu |
| Màn hình quản trị xem batch của người khác | Ngoài phạm vi (Q13) | PO chốt |
| Chính sách xoá batch cũ | Chưa có quyết định (Q12) | PO chốt thời hạn |
| Mã riêng cho "đơn vị đang ngừng" (`INACTIVE_REFERENCE`) | Dùng `RULE_VIOLATION` + `field=unit` đã đủ phân biệt; thêm mã sẽ đổi cả API nhập cũ | FE thấy không đủ |

### 8.3 Việc FE cần làm tiếp

**Bắt buộc:**

- [ ] Map `version` **bắt đầu từ 0**; gửi lại đúng `version` đã nhận làm `expectedVersion`.
- [ ] Rẽ nhánh theo bảng §5.1 — đặc biệt bốn mã **cùng 409**; `IMPORT_REVALIDATION_FAILED` lấy batch mới từ `result`.
- [ ] Map mã lỗi dòng theo bảng §5.2 (`REQUIRED_MISSING`, `NOT_ALLOWED_VALUE`, `DUPLICATE_IN_FILE`, `ALREADY_EXISTS`, …).
- [ ] Vật tư: luôn gửi `plantId`; đối tác: không cần.
- [ ] Giới hạn điện thoại nhà cung cấp 60 ký tự (cập nhật kiểm tra phía FE + câu hướng dẫn trong mẫu).
- [ ] Đồng bộ strict theo Q10: ô boolean/ngày nay là lỗi.

**Nên làm:**

- [ ] Gửi `Idempotency-Key` ở cả upload và commit; khi mất response, gọi lại **cùng key** hoặc mở chi tiết batch, không upload lại.
- [ ] Gửi BE bộ file mẫu thật của FE (6 file) để đo lại mục đầu tiên của §7.3.

**Thứ tự deploy:** không ràng buộc (BE không đổi gì của API đang dùng).

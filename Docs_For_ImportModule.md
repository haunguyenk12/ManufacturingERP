# Hướng dẫn tích hợp Import Module cho Frontend

## 1. Mục đích tài liệu

Tài liệu này mô tả cách Frontend tích hợp chức năng import master data từ file Excel, bao gồm:

- Import đang dùng cho module nào.
- Luồng sử dụng trên giao diện.
- Danh sách API và payload.
- Cách hiển thị dữ liệu trước và sau mapping.
- Trạng thái của một lần import và từng dòng dữ liệu.
- Các lỗi FE cần hiển thị cho người dùng.

Import được thiết kế theo nguyên tắc:

> Upload và Validate không tạo master data. Dữ liệu thật chỉ được tạo khi người dùng xác nhận Apply.

Backend không sửa file Excel gốc. Backend đọc dữ liệu vào vùng tạm, lưu riêng dữ liệu gốc và dữ liệu sau mapping.

---

## 2. Import hiện dùng cho module nào?

Phiên bản hiện tại chỉ hỗ trợ:

| Nội dung | Giá trị |
|---|---|
| Module nghiệp vụ | Inventory – Danh mục vật tư/hàng hóa |
| Import target | `ITEM` |
| Master data được tạo | Item |
| Backend handler | `ItemImportTargetHandler` |

Các trường Item được hỗ trợ:

| Trường đích | Ý nghĩa trên FE | Kiểu | Bắt buộc | Giới hạn/Giá trị |
|---|---|---|---:|---|
| `code` | Mã vật tư | String | Có | Tối đa 100 ký tự; chỉ `A-Z`, `0-9`, `.`, `_`, `-` |
| `name` | Tên hàng | String | Có | Tối đa 255 ký tự |
| `type` | Loại vật tư | Enum | Có | Xem danh sách bên dưới |
| `unit` | Đơn vị tính | String | Có | Tối đa 30 ký tự |
| `lotTracked` | Theo dõi lô | Boolean | Không | `true` hoặc `false` sau mapping |
| `serialTracked` | Theo dõi serial | Boolean | Không | `true` hoặc `false` sau mapping |

Giá trị `type` hợp lệ:

| Giá trị backend | Nhãn nên hiển thị trên FE |
|---|---|
| `RAW_MATERIAL` | Nguyên vật liệu |
| `WIP` | Bán thành phẩm |
| `FINISHED_GOOD` | Thành phẩm |
| `CONSUMABLE` | Vật tư tiêu hao |
| `SERVICE` | Dịch vụ |

Chưa hỗ trợ import Supplier, Warehouse, BOM, Routing, Work Center hoặc các loại master data khác.

---

## 3. Base URL, xác thực và phân quyền

### 3.1 Base URL mặc định

```text
http://localhost:8080/api
```

Swagger UI:

```text
http://localhost:8080/api/swagger-ui.html
```

### 3.2 Xác thực

Các API import yêu cầu JWT access token:

```http
Authorization: Bearer <ACCESS_TOKEN>
```

API đăng nhập:

```http
POST /api/auth/v1/login
Content-Type: application/json
```

```json
{
  "username": "<USERNAME>",
  "password": "<PASSWORD>",
  "deviceId": "import-module-web"
}
```

Access token nằm tại:

```text
result.accessToken
```

### 3.3 Quyền cần có

| Quyền | Mục đích |
|---|---|
| `PERM_DATA_IMPORT_READ` | Xem profile, import run và dữ liệu từng dòng |
| `PERM_DATA_IMPORT_EXECUTE` | Tạo profile, upload, validate, apply và cancel |
| `PERM_ITEM_MANAGE` | Tạo Item khi Apply |

Mặc định role `ADMIN` và `MANAGER` được cấp quyền import.

---

## 4. Luồng giao diện đề xuất

```text
1. Chọn công ty và loại dữ liệu ITEM
              ↓
2. Chọn hoặc tạo Mapping Profile
              ↓
3. Upload file Excel
              ↓
4. Validate: backend mapping + kiểm tra
              ↓
5. FE hiển thị dữ liệu xem trước
              ↓
6. Người dùng xác nhận Apply hoặc Cancel
              ↓
7. FE hiển thị kết quả import
```

### Nguyên tắc quan trọng

- FE không tự thực hiện transform dữ liệu.
- Backend thực hiện mapping, transform và validation.
- FE chỉ cấu hình profile và hiển thị kết quả backend trả về.
- Trước bước Apply, không có Item nào được tạo.
- FE phải hiển thị rõ số dòng hợp lệ và số dòng lỗi trước khi cho phép Apply.
- Apply chỉ xử lý những dòng có trạng thái `VALID`.

---

## 5. Cấu trúc file Excel

Backend chỉ đọc sheet được khai báo trong Mapping Profile. Ví dụ profile sử dụng:

```text
sheetName = Dữ liệu
headerRowIndex = 0
firstDataRowIndex = 1
```

Chỉ số dòng là **0-based**:

- `headerRowIndex = 0`: dòng đầu tiên của sheet là header.
- `firstDataRowIndex = 1`: dữ liệu bắt đầu từ dòng thứ hai của sheet.

Ví dụ header:

| Mã vật tư | Tên hàng | Loại | Đơn vị tính | Theo dõi lô | Theo dõi serial |
|---|---|---|---|---|---|

File có thể có thêm các cột không map. Backend vẫn lưu dữ liệu gốc của các cột đó và trả chúng trong `unmappedHeaders`, nhưng không đưa chúng vào Item.

Nếu workbook có các sheet như `Cấu hình mapping`, `Ma trận kịch bản` hoặc `Đọc trước khi test`, backend không tự đọc cấu hình từ những sheet này. Chúng chỉ là nội dung hướng dẫn. Mapping thực tế phải được tạo qua Import Profile API.

Giới hạn mặc định:

| Giới hạn | Giá trị |
|---|---:|
| Số dòng import tối đa | 5.000 dòng |
| Kích thước file tối đa | 10 MB |
| Kích thước multipart request tối đa | 12 MB |
| Loại file | Chỉ `.xlsx` |

MIME type bắt buộc của file:

```text
application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

Formula cell không được phép xuất hiện trong file import.

---

## 6. API Import Target

### 6.1 Danh sách loại dữ liệu có thể import

```http
GET /api/v1/import-targets?companyId=<COMPANY_ID>
Authorization: Bearer <ACCESS_TOKEN>
```

### 6.2 Lấy contract trường của ITEM

```http
GET /api/v1/import-targets/ITEM/fields?companyId=<COMPANY_ID>
Authorization: Bearer <ACCESS_TOKEN>
```

Response rút gọn:

```json
{
  "code": "SUCCESS",
  "result": {
    "type": "ITEM",
    "label": "Item master",
    "fields": [
      {
        "name": "code",
        "label": "Item code",
        "type": "STRING",
        "required": true,
        "maxLength": 100,
        "allowedValues": [],
        "example": "VT-001"
      }
    ]
  },
  "message": "OK"
}
```

FE nên sử dụng API này làm nguồn sự thật để dựng UI mapping, không hard-code danh sách trường nếu có thể.

### 6.3 Tải template chuẩn

```http
GET /api/v1/import-targets/ITEM/template?companyId=<COMPANY_ID>
Authorization: Bearer <ACCESS_TOKEN>
```

Response là file `.xlsx`.

---

## 7. API Mapping Profile

Mapping Profile định nghĩa:

- Sheet cần đọc.
- Vị trí header và dòng dữ liệu đầu tiên.
- Cột Excel nào map vào trường Item nào.
- Các transform được chạy theo thứ tự.
- Giá trị mặc định cho ô trống.

### 7.1 Tạo profile

```http
POST /api/v1/import-profiles
Authorization: Bearer <ACCESS_TOKEN>
Content-Type: application/json
```

Payload mẫu cho file tiếng Việt:

```json
{
  "code": "ITEM_MAPPING_VI_V1",
  "name": "Mapping vật tư tiếng Việt",
  "targetType": "ITEM",
  "companyId": "<COMPANY_ID>",
  "sheetName": "Dữ liệu",
  "headerRowIndex": 0,
  "firstDataRowIndex": 1,
  "mappings": [
    {
      "sourceHeader": "Mã vật tư",
      "targetField": "code",
      "transforms": ["STRIP_NBSP", "TRIM", "NFC", "UPPER"],
      "defaultValue": null
    },
    {
      "sourceHeader": "Tên hàng",
      "targetField": "name",
      "transforms": ["STRIP_NBSP", "TRIM", "COLLAPSE_SPACES", "NFC"],
      "defaultValue": null
    },
    {
      "sourceHeader": "Loại",
      "targetField": "type",
      "transforms": [
        "STRIP_NBSP",
        "TRIM",
        "NFC",
        "VALUE_DICT:Nguyên vật liệu=RAW_MATERIAL;Bán thành phẩm=WIP;Thành phẩm=FINISHED_GOOD;Vật tư tiêu hao=CONSUMABLE;Dịch vụ=SERVICE",
        "UPPER"
      ],
      "defaultValue": null
    },
    {
      "sourceHeader": "Đơn vị tính",
      "targetField": "unit",
      "transforms": ["STRIP_NBSP", "TRIM", "COLLAPSE_SPACES", "NFC", "UPPER"],
      "defaultValue": null
    },
    {
      "sourceHeader": "Theo dõi lô",
      "targetField": "lotTracked",
      "transforms": ["STRIP_NBSP", "TRIM", "NFC", "BOOLEAN_VN"],
      "defaultValue": "false"
    },
    {
      "sourceHeader": "Theo dõi serial",
      "targetField": "serialTracked",
      "transforms": ["STRIP_NBSP", "TRIM", "NFC", "BOOLEAN_VN"],
      "defaultValue": "false"
    }
  ]
}
```

Response HTTP `201 Created`. FE cần lưu:

```text
result.profileId
```

### 7.2 Danh sách profile

```http
GET /api/v1/import-profiles?companyId=<COMPANY_ID>&targetType=ITEM&status=ACTIVE&page=0&size=20
Authorization: Bearer <ACCESS_TOKEN>
```

### 7.3 Chi tiết profile

```http
GET /api/v1/import-profiles/<PROFILE_ID>
Authorization: Bearer <ACCESS_TOKEN>
```

### 7.4 Cập nhật profile

```http
PATCH /api/v1/import-profiles/<PROFILE_ID>
Authorization: Bearer <ACCESS_TOKEN>
Content-Type: application/json
```

Payload cập nhật thay thế toàn bộ phần có thể chỉnh sửa:

```json
{
  "name": "Mapping vật tư tiếng Việt phiên bản 2",
  "sheetName": "Dữ liệu",
  "headerRowIndex": 0,
  "firstDataRowIndex": 1,
  "mappings": [
    {
      "sourceHeader": "Mã vật tư",
      "targetField": "code",
      "transforms": ["TRIM", "UPPER"],
      "defaultValue": null
    },
    {
      "sourceHeader": "Tên hàng",
      "targetField": "name",
      "transforms": ["TRIM", "COLLAPSE_SPACES", "NFC"],
      "defaultValue": null
    },
    {
      "sourceHeader": "Loại",
      "targetField": "type",
      "transforms": [
        "TRIM",
        "VALUE_DICT:Nguyên vật liệu=RAW_MATERIAL;Bán thành phẩm=WIP;Thành phẩm=FINISHED_GOOD;Vật tư tiêu hao=CONSUMABLE;Dịch vụ=SERVICE",
        "UPPER"
      ],
      "defaultValue": null
    },
    {
      "sourceHeader": "Đơn vị tính",
      "targetField": "unit",
      "transforms": ["TRIM", "UPPER"],
      "defaultValue": null
    },
    {
      "sourceHeader": "Theo dõi lô",
      "targetField": "lotTracked",
      "transforms": ["TRIM", "BOOLEAN_VN"],
      "defaultValue": "false"
    },
    {
      "sourceHeader": "Theo dõi serial",
      "targetField": "serialTracked",
      "transforms": ["TRIM", "BOOLEAN_VN"],
      "defaultValue": "false"
    }
  ],
  "status": "ACTIVE"
}
```

Lưu ý: `mappings` trong PATCH là danh sách thay thế hoàn toàn, không phải cập nhật từng phần. Payload thực tế vẫn phải map đủ các trường bắt buộc `code`, `name`, `type`, `unit`.

Trạng thái profile:

```text
ACTIVE
INACTIVE
```

---

## 8. Transform được hỗ trợ

Transform được backend chạy từ trái sang phải.

| Transform | Ý nghĩa |
|---|---|
| `TRIM` | Xóa khoảng trắng đầu và cuối |
| `STRIP_NBSP` | Chuyển khoảng trắng ẩn U+00A0/U+202F thành khoảng trắng thường |
| `COLLAPSE_SPACES` | Gộp nhiều khoảng trắng, tab hoặc xuống dòng |
| `NFC` | Chuẩn hóa Unicode tiếng Việt |
| `UPPER` | Chuyển thành chữ hoa |
| `LOWER` | Chuyển thành chữ thường |
| `PAD_LEFT_ZERO:6` | Thêm số 0 bên trái cho đủ độ dài 6 |
| `BOOLEAN_VN` | Chuyển Có/Không, Đúng/Sai, X, 1/0, Yes/No thành boolean |
| `VALUE_DICT:a=A;b=B` | Chuyển giá trị theo từ điển |
| `DECIMAL_COMMA` | Chuyển số dạng `1.234,56` thành `1234.56` |
| `DECIMAL_DOT` | Chuyển số dạng `1,234.56` thành `1234.56` |
| `EXCEL_DATE` | Chuyển Excel date serial thành ngày ISO |
| `DATE_FORMAT:dd/MM/yyyy` | Chuyển ngày theo format đã khai báo thành ISO |

Không nên dùng `PAD_LEFT_ZERO` trên toàn bộ cột bắt buộc nếu cột có thể trống, vì chuỗi trống có thể bị biến thành chuỗi số 0 và che mất lỗi thiếu dữ liệu.

---

## 9. API Upload Import Run

```http
POST /api/v1/import-runs
Authorization: Bearer <ACCESS_TOKEN>
Content-Type: multipart/form-data
```

Multipart fields:

| Key | Kiểu | Bắt buộc | Nội dung |
|---|---|---:|---|
| `file` | File | Có | File `.xlsx` |
| `targetType` | Text | Có | `ITEM` |
| `companyId` | UUID | Có | Công ty nhận master data |
| `profileId` | UUID | Không | Profile dự kiến sử dụng |
| `plantId` | UUID | Không | Scope nhà máy nếu cần |
| `warehouseId` | UUID | Không | Scope kho; cần `plantId` nếu có |

Ví dụ curl:

```bash
curl --request POST "http://localhost:8080/api/v1/import-runs" \
  --header "Authorization: Bearer <ACCESS_TOKEN>" \
  --form "file=@items.xlsx;type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
  --form "targetType=ITEM" \
  --form "companyId=<COMPANY_ID>" \
  --form "profileId=<PROFILE_ID>"
```

Response HTTP `201 Created`:

```json
{
  "code": "SUCCESS",
  "result": {
    "importRunId": "<RUN_ID>",
    "code": "IMP-XXXXXXXX",
    "targetType": "ITEM",
    "profileId": "<PROFILE_ID>",
    "profileCode": "ITEM_MAPPING_VI_V1",
    "companyId": "<COMPANY_ID>",
    "originalFilename": "items.xlsx",
    "fileSizeBytes": 80730,
    "fileSha256": "<SHA256>",
    "detectedHeaders": [
      "Mã vật tư",
      "Tên hàng",
      "Loại",
      "Đơn vị tính",
      "Theo dõi lô",
      "Theo dõi serial"
    ],
    "unmappedHeaders": [],
    "status": "PARSED",
    "totalRows": 1500,
    "validRows": 0,
    "errorRows": 0,
    "appliedRows": 0,
    "failedRows": 0
  },
  "message": "Created successfully"
}
```

Upload chỉ đọc và lưu staging data. Upload không mapping và không tạo Item.

FE cần lưu:

```text
result.importRunId
```

---

## 10. API Validate và Mapping

```http
POST /api/v1/import-runs/<RUN_ID>/validate
Authorization: Bearer <ACCESS_TOKEN>
Content-Type: application/json
```

Payload:

```json
{
  "profileId": "<PROFILE_ID>"
}
```

Backend sẽ:

1. Lấy từng dòng dữ liệu gốc trong staging.
2. Map `sourceHeader` sang `targetField`.
3. Chạy transform theo thứ tự.
4. Áp dụng `defaultValue` nếu kết quả rỗng.
5. Kiểm tra kiểu dữ liệu và trường bắt buộc.
6. Kiểm tra mã trùng trong file và mã đã tồn tại.
7. Lưu `mappedValues`, `errors` và trạng thái từng dòng.

Response:

```json
{
  "code": "SUCCESS",
  "result": {
    "importRunId": "<RUN_ID>",
    "profileId": "<PROFILE_ID>",
    "status": "VALIDATED",
    "totalRows": 1500,
    "validRows": 1200,
    "errorRows": 300,
    "appliedRows": 0,
    "failedRows": 0
  },
  "message": "OK"
}
```

Các con số trên chỉ là ví dụ. Điều kiện đúng là:

```text
validRows + errorRows = totalRows
```

Một run ở trạng thái `PARSED` hoặc `VALIDATED` có thể Validate lại bằng profile khác mà không cần upload lại file.

---

## 11. API Preview dữ liệu từng dòng

### 11.1 Xem tất cả

```http
GET /api/v1/import-runs/<RUN_ID>/rows?page=0&size=100&sortBy=rowNumber&sortDir=asc
Authorization: Bearer <ACCESS_TOKEN>
```

### 11.2 Chỉ xem dòng lỗi

```http
GET /api/v1/import-runs/<RUN_ID>/rows?status=ERROR&page=0&size=100
Authorization: Bearer <ACCESS_TOKEN>
```

### 11.3 Chỉ xem dòng hợp lệ

```http
GET /api/v1/import-runs/<RUN_ID>/rows?status=VALID&page=0&size=100
Authorization: Bearer <ACCESS_TOKEN>
```

Kích thước trang tối đa là 100 dòng.

Response một dòng:

```json
{
  "importRowId": "<ROW_ID>",
  "rowNumber": 2,
  "status": "VALID",
  "rawCells": {
    "Mã vật tư": " vt-001 ",
    "Tên hàng": "Thép  tấm 2 mm",
    "Loại": "Nguyên vật liệu",
    "Đơn vị tính": "kg",
    "Theo dõi lô": "Có",
    "Theo dõi serial": "Không"
  },
  "mappedValues": {
    "code": "VT-001",
    "name": "Thép tấm 2 mm",
    "type": "RAW_MATERIAL",
    "unit": "KG",
    "lotTracked": "true",
    "serialTracked": "false"
  },
  "errors": [],
  "createdEntityId": null
}
```

### Cách FE nên hiển thị Preview

FE cần đặt `rawCells` và `mappedValues` cạnh nhau:

| Trường | Dữ liệu từ Excel | Dữ liệu sẽ lưu |
|---|---|---|
| Mã vật tư | ` vt-001 ` | `VT-001` |
| Tên hàng | `Thép  tấm 2 mm` | `Thép tấm 2 mm` |
| Loại | `Nguyên vật liệu` | `Nguyên vật liệu` |
| Đơn vị | `kg` | `KG` |

FE nên đổi các giá trị kỹ thuật thành nhãn thân thiện:

- `RAW_MATERIAL` → `Nguyên vật liệu`.
- `true` → `Có`.
- `false` → `Không`.

Không sửa `mappedValues` trên FE. Dữ liệu backend trả về là dữ liệu dự kiến được dùng khi Apply.

---

## 12. API lấy tổng quan Import Run

```http
GET /api/v1/import-runs/<RUN_ID>
Authorization: Bearer <ACCESS_TOKEN>
```

Các trường FE cần quan tâm:

| Trường | Ý nghĩa |
|---|---|
| `status` | Trạng thái toàn bộ lần import |
| `totalRows` | Tổng số dòng |
| `validRows` | Số dòng có thể Apply |
| `errorRows` | Số dòng lỗi |
| `appliedRows` | Số dòng tạo Item thành công |
| `failedRows` | Số dòng lỗi tại bước Apply |
| `detectedHeaders` | Header đọc được từ Excel |
| `unmappedHeaders` | Header không được profile sử dụng |
| `errorMessage` | Lỗi cấp run nếu có |

Danh sách run của công ty:

```http
GET /api/v1/import-runs?companyId=<COMPANY_ID>&targetType=ITEM&page=0&size=20&sortBy=createdAt&sortDir=desc
Authorization: Bearer <ACCESS_TOKEN>
```

Có thể lọc thêm theo `status`.

---

## 13. API Apply

Chỉ gọi API này sau khi người dùng đã xem Preview và xác nhận.

```http
POST /api/v1/import-runs/<RUN_ID>/apply
Authorization: Bearer <ACCESS_TOKEN>
Idempotency-Key: <UNIQUE_KEY>
```

Không có request body.

FE nên tạo một UUID mới làm `Idempotency-Key` cho một hành động Apply. Khi retry cùng hành động do timeout hoặc mất mạng, FE phải gửi lại đúng key cũ.

Ví dụ:

```http
Idempotency-Key: 7b3d7469-99b9-43ae-a3dc-81222a75a7d9
```

Backend chỉ Apply dòng `VALID`. Mỗi dòng được xử lý trong transaction riêng.

Kết quả có thể là:

| Run status | Ý nghĩa |
|---|---|
| `APPLIED` | Tất cả dòng hợp lệ đã tạo thành công |
| `PARTIALLY_APPLIED` | Một số dòng thành công, một số thất bại |
| `FAILED` | Không có dòng nào tạo thành công hoặc toàn bộ dòng Apply bị lỗi |

Dòng thành công có:

```text
status = APPLIED
createdEntityId = ID của Item vừa tạo
```

Dòng thất bại tại Apply có:

```text
status = FAILED
errors[].code = APPLY_FAILED
```

FE phải hiển thị rõ rằng nếu run có cả dòng `VALID` và `ERROR`, Apply chỉ tạo những dòng `VALID` và bỏ qua các dòng `ERROR`.

---

## 14. API Cancel

```http
POST /api/v1/import-runs/<RUN_ID>/cancel
Authorization: Bearer <ACCESS_TOKEN>
```

Không có request body.

Cancel chỉ dùng khi run chưa ở trạng thái cuối. Sau Cancel:

```text
status = CANCELLED
```

Cancel không xóa file gốc của người dùng và không hoàn tác những Item đã được Apply trước đó.

---

## 15. Trạng thái Import Run

| Trạng thái | Ý nghĩa | Hành động FE |
|---|---|---|
| `PARSING` | Backend đang đọc file | Hiển thị loading |
| `PARSED` | Đã đọc file, chưa Validate | Cho phép chọn profile và Validate |
| `VALIDATING` | Backend đang mapping và kiểm tra | Hiển thị loading |
| `VALIDATED` | Đã có kết quả Preview | Cho xem dòng và xác nhận Apply |
| `APPLYING` | Backend đang tạo Item | Khóa nút Apply |
| `APPLIED` | Hoàn thành toàn bộ | Hiển thị kết quả thành công |
| `PARTIALLY_APPLIED` | Hoàn thành một phần | Hiển thị cả thành công và thất bại |
| `FAILED` | Apply thất bại | Hiển thị lỗi |
| `CANCELLED` | Người dùng đã hủy | Chỉ cho xem lịch sử |

---

## 16. Trạng thái từng dòng

| Trạng thái | Ý nghĩa |
|---|---|
| `PENDING` | Đã đọc từ Excel, chưa Validate |
| `VALID` | Hợp lệ, có thể Apply |
| `ERROR` | Lỗi validation, không được Apply |
| `APPLIED` | Đã tạo Item thành công |
| `FAILED` | Hợp lệ khi Validate nhưng lỗi tại Apply |
| `SKIPPED` | Bị bỏ qua theo xử lý nghiệp vụ |

---

## 17. Mã lỗi từng dòng

| Error code | Ý nghĩa | Cách hiển thị đề xuất |
|---|---|---|
| `REQUIRED_MISSING` | Thiếu trường bắt buộc | “Thiếu dữ liệu bắt buộc” |
| `TOO_LONG` | Vượt độ dài cho phép | Hiển thị giới hạn và độ dài hiện tại |
| `NOT_ALLOWED_VALUE` | Giá trị enum không hợp lệ | Hiển thị danh sách giá trị được phép |
| `INVALID_FORMAT` | Sai định dạng | Hiển thị giá trị gốc và trường bị lỗi |
| `DUPLICATE_IN_FILE` | Trùng mã trong cùng file | Hiển thị mã bị trùng |
| `ALREADY_EXISTS` | Mã đã tồn tại trong hệ thống | Đề nghị bỏ dòng hoặc thay mã |
| `REFERENCE_NOT_FOUND` | Không tìm thấy dữ liệu tham chiếu | Hiển thị mã không tìm thấy |
| `RULE_VIOLATION` | Vi phạm quy tắc liên trường | Ví dụ cùng theo dõi lô và serial |
| `APPLY_FAILED` | Thất bại khi tạo dữ liệu thật | Cho phép xem chi tiết lỗi |

Ví dụ lỗi trong response:

```json
{
  "sourceHeader": "Mã vật tư",
  "targetField": "code",
  "code": "DUPLICATE_IN_FILE",
  "message": "Item code 'VT-001' appears more than once in this file"
}
```

FE nên điều khiển UI theo `errors[].code`, không phân tích nội dung tiếng Anh trong `message`.

---

## 18. Response envelope chung

API thành công:

```json
{
  "code": "SUCCESS",
  "result": {},
  "message": "OK"
}
```

API thất bại:

```json
{
  "code": "INVALID_INPUT",
  "result": null,
  "message": "Nội dung lỗi"
}
```

Lỗi nhiều trường có thể có thêm:

```json
{
  "errors": [
    {
      "field": "companyId",
      "message": "must not be null"
    }
  ]
}
```

Lưu ý: một lần Validate có nhiều dòng sai vẫn trả HTTP `200`. Đây không phải lỗi của API; lỗi từng dòng nằm trong `result` của API Preview.

---

## 19. Phân trang

Các API danh sách trả về:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 1500,
  "totalPages": 75,
  "first": true,
  "last": false
}
```

Quy ước:

- `page` bắt đầu từ `0`.
- `size` tối đa `100`.
- FE không nên tải toàn bộ 1.500 dòng trong một request.

---

## 20. Checklist triển khai FE

### Màn hình Upload

- Chọn company.
- Chọn target `ITEM`.
- Chọn Mapping Profile.
- Chọn file `.xlsx`.
- Kiểm tra extension và kích thước trước khi gửi.
- Không tự đặt `Content-Type` cho toàn request multipart; để browser sinh boundary.
- Đảm bảo MIME của file là MIME `.xlsx`.

### Màn hình Mapping/Profile

- Lấy target fields từ backend.
- Cho chọn source header và target field.
- Không cho map một target field nhiều lần.
- Đánh dấu các trường bắt buộc.
- Hiển thị thứ tự transform.
- Lưu profile qua API trước khi Validate.

### Màn hình Preview

- Hiển thị tổng dòng, dòng hợp lệ và dòng lỗi.
- Hiển thị `rawCells` cạnh `mappedValues`.
- Lọc theo `VALID` và `ERROR`.
- Hiển thị `unmappedHeaders`.
- Cho phép Validate lại bằng profile khác.
- Không tự sửa `mappedValues`.

### Màn hình xác nhận

- Hiển thị chính xác số dòng sẽ được Apply.
- Cảnh báo các dòng `ERROR` sẽ bị bỏ qua.
- Yêu cầu xác nhận rõ ràng.
- Tạo và lưu `Idempotency-Key` trước khi gọi Apply.
- Khóa nút trong lúc `APPLYING`.

### Màn hình kết quả

- Hiển thị `appliedRows` và `failedRows`.
- Cho xem dòng `APPLIED` và `FAILED`.
- Hiển thị `createdEntityId` nếu cần điều hướng tới Item vừa tạo.

---

## 21. File test hiện có

File kiểm thử trong workspace:

```text
test-data/master-data-item-mapping-worst-case-1500-vi.xlsx
```

File gồm 1.500 dòng dữ liệu cho target `ITEM`. Sheet được import là `Dữ liệu`.

Các sheet còn lại chỉ là hướng dẫn kiểm thử, không được backend tự động dùng làm Mapping Profile.

File có cả dòng hợp lệ và dòng cố ý sai. Không nên Apply vào công ty có dữ liệu sản xuất. Hãy dùng company test hoặc chỉ dừng ở bước Validate/Preview.

---

## 22. Tóm tắt contract FE cần nhớ

```text
Target hiện tại: ITEM

Upload:
POST /api/v1/import-runs
→ PARSED, chưa tạo Item

Validate:
POST /api/v1/import-runs/{runId}/validate
→ VALIDATED, có rawCells + mappedValues + errors

Preview:
GET /api/v1/import-runs/{runId}/rows
→ FE hiển thị dữ liệu gốc và dữ liệu dự kiến

Apply:
POST /api/v1/import-runs/{runId}/apply
→ Chỉ lúc này backend mới tạo Item
```

---
paths:
  - "src/**"
---

# Error Handling

> Bản đầy đủ kèm lịch sử từng sự cố: `docs/archive/rules-2026-11-12/error-handling.md`.
> Lịch sử mã lỗi theo phase: `docs/error-codes-history.md`.

## 5.1 Response contract

Mọi response, thành công lẫn lỗi, dùng một envelope `ApiResponse`:

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "quantity", "message": "must be greater than 0" } ] }
```

| Field | Ghi chú |
|---|---|
| `code` | `"SUCCESS"` hoặc **tên hằng** của `ErrorCode` (vd `STATE_CONFLICT`). Client rẽ nhánh theo field này |
| `result` | Payload khi thành công; `null` khi lỗi. Danh sách phân trang: `{content, page, size, totalElements, totalPages, first, last}` |
| `message` | Luôn có, để hiển thị hoặc log |
| `errors` | Mảng `{field, message}`, chỉ có khi lỗi validation theo field (`@JsonInclude(NON_NULL)`). Một field có thể xuất hiện nhiều lần; không gộp |

- **Ngoại lệ duy nhất của "`result` null khi lỗi"**: `ResultCarryingException`, chỉ dùng khi chính thất
  bại đã đổi resource và client cần trạng thái mới (hiện chỉ `409 IMPORT_REVALIDATION_FAILED`).
- **`X-Trace-Id` (header) khác `traceId` (field trên chứng từ).** Header để correlate log. Field là dữ
  liệu nghiệp vụ truy xuất nguồn gốc, được persist trên `material_issues`, `production_executions`,
  `production_receipts`, `stock_movements`. Không gộp hai thứ; `path` không bao giờ nằm trong body.

## 5.2 Exception

```
RuntimeException
└── AppException            – ErrorCode + HttpStatus + message
    └── MultiErrorException – thêm errors theo field

interface ErrorCode { code(); message(); status(); }
├── AuthErrorCode · BusinessErrorCode · ValidationErrorCode   (common/exception)
└── ImportErrorCode (module/dataimport) – lỗi cấp DÒNG của file import, không đi qua handler HTTP
```

1. Loại lỗi mới ⇒ thêm **một hằng vào enum**, không thêm class exception.
2. Tạo exception qua `ExceptionFactory` (`notFound`, `alreadyExists`, `unauthorized`, `forbidden`,
   `businessRule`, `custom`, `withErrors`). Tên method không quyết định HTTP status; status lấy từ `ErrorCode`.
3. Domain có vốn từ lỗi riêng thì khai enum riêng implement `ErrorCode` (tiền lệ: `ImportErrorCode`).

## 5.3 Mã lỗi

Nguồn duy nhất là ba enum trong `common/exception/`; không chép danh sách vào tài liệu.

Năm hằng chưa có throw site được **cố ý giữ** (đã hứa với FE hoặc nằm trong hợp đồng lỗi):
`ITEM_ALREADY_ISSUED`, `PRODUCTION_ORDER_CLOSED`, `LOT_WAREHOUSE_CONFLICT`, `RECEIPT_STATE_CONFLICT`,
`BOM_REQUIREMENT_EXCEEDED`. Đừng xoá vì "grep ra 0 kết quả".

Tên hằng khác chuỗi trên wire ở bốn chỗ:

| Hằng | `code()` trên wire | HTTP |
|---|---|---|
| `AuthErrorCode.ACCESS_DENIED` | `PERMISSION_DENIED` | 403 |
| `ValidationErrorCode.INVALID_INPUT` | `VALIDATION_ERROR` | 400 |
| `ValidationErrorCode.RESOURCE_NOT_FOUND` | `ENTITY_NOT_FOUND` | 404 |
| `BusinessErrorCode.INSUFFICIENT_STOCK` | `INSUFFICIENT_AVAILABLE_STOCK` | 409 |

**Chọn mã nào — 409 hay 422:**

| Loại kiểm tra | Mã | HTTP |
|---|---|---|
| Đọc **status** của chứng từ (`isDraft()`, `canReceive()`, `status != X`) | `STATE_CONFLICT` | 409 |
| Số lượng vượt **trần của chứng từ** (nhận vượt đặt, good vượt plan) | `PLANNED_QUANTITY_EXCEEDED` | 409 |
| Lot sai trạng thái | `LOT_NOT_ELIGIBLE` | 409 |
| Master data đang `INACTIVE`, hoặc kích hoạt con khi cha `INACTIVE` | `RESOURCE_INACTIVE` | 422 |
| Hai bản ghi khác chủ sở hữu ("must belong to") | `RESOURCE_SCOPE_MISMATCH` | 422 |
| Chứng từ chưa có dòng con nào | `DOCUMENT_HAS_NO_LINES` | 422 |
| Dữ liệu đầu vào sai còn lại (sai `ItemType`, tracking không khớp, `dueDate` < `orderDate`…) | `OPERATION_NOT_ALLOWED` | 422 |

- `OPERATION_NOT_ALLOWED` vẫn là câu trả lời đúng cho phần catch-all; đừng đổi tiếp "cho đồng bộ".
- Dấu hiệu sai mạnh nhất: **hai đường vào cùng một điều kiện nghiệp vụ trả hai status khác nhau**.
- `POST /auth/v1/refresh` có ba mã cùng 401 (`REFRESH_TOKEN_EXPIRED`, `TOKEN_REUSE_DETECTED`,
  `SESSION_ABSOLUTE_TIMEOUT`); client phải rẽ theo `code`.
- FE đề xuất mã mới mà đã có mã tương đương ⇒ dùng mã sẵn có (`STALE_VERSION` → `CONCURRENT_MODIFICATION`).
  Chỉ thêm mã mới khi **cách xử lý của client khác** (vd `PRICE_STALE`, `SOURCE_LOCKED`).

## 5.4 `GlobalExceptionHandler`

| Exception | HTTP | Mã | `errors[]` |
|---|---|---|---|
| `MultiErrorException` / `AppException` | theo `ErrorCode` | theo `ErrorCode` | khi có field errors |
| `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 | `VALIDATION_ERROR` | ✅ |
| `MethodArgumentTypeMismatchException` (UUID/enum sai ở path, query) | 400 | `VALIDATION_ERROR` | ✅ |
| `MissingServletRequestParameterException` | 400 | `VALIDATION_ERROR` | ✅ tên param |
| `HttpMessageNotReadableException` | 400 | `VALIDATION_ERROR` | ✅ chỉ khi sai kiểu ở một field |
| `HttpRequestMethodNotSupportedException` | 405 | `VALIDATION_ERROR` | — |
| `NoResourceFoundException` / `NoHandlerFoundException` | 404 | `ENTITY_NOT_FOUND` | — |
| `MaxUploadSizeExceededException` | 413 | `FILE_SIZE` | — |
| `AccessDeniedException` | 403 | `PERMISSION_DENIED` | — |
| `ObjectOptimisticLockingFailureException` | 409 | `CONCURRENT_MODIFICATION` | — |
| `DataIntegrityViolationException` | 409 hoặc 422 | xem dưới | — |
| `Exception` | 500 | `INTERNAL_SERVER_ERROR` | — |

- Input sai của client không bao giờ được rơi xuống catch-all thành 500. Exception framework mới xuất
  hiện ⇒ thêm handler kèm test trong `GlobalExceptionHandlerTest`.
- Không thêm handler cho exception chưa có đường ném (vd `MissingRequestHeaderException`: mọi
  `@RequestHeader` hiện đều `required = false`).
- `accept-float-as-int: false`: `1.5` và cả `5.0` gửi vào field số nguyên trả 400 kèm tên field,
  không cắt im lặng. Không echo giá trị bị từ chối.
- Catch-all không trả stack trace hay `ex.getMessage()` ra client.

**`DataIntegrityViolationException`** phân loại theo tên constraint (`DataIntegrityErrorMapper`):
có trong bảng `KNOWN` ⇒ mã riêng; tên bắt đầu `uk_` hoặc message chứa `duplicate key` ⇒
`RESOURCE_ALREADY_EXISTS` (409); còn lại (`chk_`, `fk_`, `NOT NULL`) ⇒ `BUSINESS_RULE_VIOLATION` (422).
Tên constraint không bao giờ trả ra response. Chỉ thêm vào `KNOWN` khi constraint đó thật sự từng gây khó chẩn đoán.

**Lỗi trong filter chain** không tới `@RestControllerAdvice`. Ba lớp bảo vệ: mỗi filter tự bắt
`RuntimeException` và trả envelope `INTERNAL_SERVER_ERROR`; `ApiErrorController` thay
`BasicErrorController`; `/error` là `permitAll` trong `SecurityConfig`.

## 5.5 Spring Security

- 401 (`AuthenticationEntryPoint`): `TOKEN_EXPIRED`, `TOKEN_REVOKED`, `TOKEN_MALFORMED`.
- 403 (`AccessDeniedHandler`): `PERMISSION_DENIED`.

## 5.6 Header

Response: `X-Trace-Id` · `X-RateLimit-Limit` / `-Remaining` / `-Rule` · `Retry-After` (khi 429) ·
`X-Session-Warning: previous-session-terminated` (khi login thu hồi phiên cũ).

Request `X-Plant-Id` là **cross-check, không phải nguồn `plantId`**. Hỏi theo từng endpoint: *endpoint
này có hai nguồn plant không?*

| Endpoint | Cross-check |
|---|---|
| Nêu plant tường minh (`/plants/{plantId}/…`, `plantId` trong query/body) | ✅ lệch ⇒ 409 `STATE_CONFLICT` |
| Định danh bằng aggregate (`/work-orders/{id}/…`, `?workOrderId=`) | ❌ cố ý không; `@PreAuthorize` đã chặn plant khác |

Một controller có thể chứa cả hai loại (`ManufacturingExecutionController`); cả hai phía đều có test pin.

## 5.7 Tracing

`traceId` 16 ký tự: lấy từ `X-Trace-Id` nếu có, không thì tự sinh; đưa vào MDC và trả lại qua header.
Log pattern: `%d{ISO8601} [%X{traceId}] [%X{userId}] [%X{clientIp}] %-5level [%thread] %logger{36} - %msg%n`

## 5.8 Controller

| Thao tác | Status | `ApiResponse` |
|---|---|---|
| GET một / danh sách | 200 | `ok(data)` / `ok(PageResult)` |
| POST tạo | 201 | `created(data)` |
| PATCH / PUT | 200 | `ok(data)` |
| DELETE | 200 | `noContent("Deleted")` |

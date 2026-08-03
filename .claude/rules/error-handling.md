# Error Handling

> Tách từ `CLAUDE.md` §5 (2026-07-25). Áp dụng cho **mọi** controller/service trong repo — luôn load cùng mọi session.

> **Chuẩn**: Tập trung, nhất quán, production-grade.

## 5.1 Unified Response Contract (TẤT CẢ response)

> **Quy tắc**: Mọi response – **success lẫn error** – đều dùng **cùng một envelope** `ApiResponse`.
> Frontend chỉ cần xử lý 1 contract duy nhất: kiểm tra `code`, đọc `result` hoặc `message`.

```json
{
  "code":    "SUCCESS",              // ErrorCode string – luôn có mặt
  "result":  { ... },               // payload (null khi error)
  "message": "Mô tả ngắn gọn",      // human-readable – luôn có mặt
  "errors":  [ ... ]                // chỉ có khi lỗi validation theo field
}
```

| Field | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `code` | `string` | ✅ | **Tên constant** của `ErrorCode` (vd `INVALID_CREDENTIALS`, `INSUFFICIENT_AVAILABLE_STOCK`). Frontend code theo field này |
| `result` | `T \| null` | — | Data payload khi success; `null` khi error |
| `message` | `string` | ✅ | Thông báo ngắn gọn cho user / log |
| `errors` | `[{field, message}] \| absent` | — | Lỗi theo từng field. `@JsonInclude(NON_NULL)` nên **biến mất** khi không có |

> **`code` là tên enum, KHÔNG phải mã ngắn.** Trước `F1` (2026-07-26) `ErrorCode.code()` trả
> `"BIZ_010"` / `"VAL_001"` / `"AUTH_030"` — lệch với chính tài liệu này. `F1` đã sửa `code()` trả
> đúng tên constant như §5.3 mô tả. Vì rule `R8` cấm assert magic string, toàn bộ test hiện có
> vẫn xanh sau thay đổi này.

> **`errors` là mảng object, không phải map.** Mỗi field có thể xuất hiện **nhiều lần** (nhiều
> constraint cùng vi phạm) — không gộp, không "giữ lỗi đầu tiên".

> **Hai thứ tên giống nhau, mục đích khác hẳn — đừng gộp** (làm rõ ở `F5`):
>
> | | Header `X-Trace-Id` | Field `traceId` trên chứng từ |
> |---|---|---|
> | Mục đích | correlate **log** của một request | **truy xuất nguồn gốc** nghiệp vụ |
> | Vòng đời | sống bằng log retention | sống bằng vòng đời chứng từ |
> | Ở đâu | response header, MDC | cột DB + trong `result` của response |
> | Ai đọc | dev khi debug | auditor / QA khi truy vết lô hàng |
>
> Giá trị được lấy từ cùng một nguồn (`TraceIdFilter` → MDC → `TraceIdProvider`), nhưng khi đã
> persist thì nó là **dữ liệu nghiệp vụ**, không phải debug info. `F5` persist `traceId` trên
> `material_issues`, `production_executions`, `production_receipts`, `stock_movements`, cộng
> `production_receipts.source_wip_trace_ids` nối WIP → Receipt. Thông tin debug khác của request
> (`path`) vẫn **không** xuất hiện trong body.

#### Ví Dụ

```json
// Success – GET single
{ "code": "SUCCESS", "result": { "id": "abc", "status": "RELEASED" }, "message": "OK" }

// Success – danh sách phân trang
{
  "code": "SUCCESS",
  "result": {
    "content": [...], "page": 0, "size": 20, "totalElements": 150,
    "totalPages": 8, "first": true, "last": false
  },
  "message": "OK"
}

// Success – không có content (DELETE)
{ "code": "SUCCESS", "result": null, "message": "Deleted successfully" }

// Error – validation (message = tóm tắt phẳng; errors = cấu trúc để bind vào form)
{
  "code": "VALIDATION_ERROR", "result": null,
  "message": "quantity: must be greater than 0; name: must not be blank",
  "errors": [
    { "field": "quantity", "message": "must be greater than 0" },
    { "field": "name",     "message": "must not be blank" }
  ]
}

// Error – business rule
{ "code": "INSUFFICIENT_AVAILABLE_STOCK", "result": null, "message": "Not enough stock for material MAT-001" }

// Error – rate limit
{ "code": "RATE_LIMIT_EXCEEDED", "result": null, "message": "Too many requests. Retry after 45s." }

// Error – idempotency: cùng key nhưng payload khác (F1)
{ "code": "IDEMPOTENCY_CONFLICT", "result": null, "message": "Idempotency-Key was already used with a different payload" }
```

## 5.2 Phân Cấp Exception

```
RuntimeException
└── BaseBusinessException (abstract – chứa ErrorCode + HttpStatus)
    ├── ResourceNotFoundException       → 404
    ├── ResourceAlreadyExistsException  → 409
    ├── BusinessRuleException           → 422
    ├── AuthException (abstract)
    │   ├── TokenExpiredException       → 401
    │   ├── TokenRevokedException       → 401
    │   ├── TokenMalformedException     → 401
    │   ├── RefreshTokenExpiredException→ 401
    │   ├── InvalidCredentialsException → 401
    │   └── AccountLockedException      → 403
    ├── AccessDeniedException           → 403
    ├── SessionConflictException        → 409
    ├── SessionAbsoluteTimeoutException → 401
    ├── TokenReuseDetectedException     → 401
    ├── ResetTokenInvalidException      → 401
    ├── ValidationException             → 400
    ├── ExternalServiceException        → 502
    └── RateLimitExceededException      → 429
```

## 5.3 ErrorCode Enum

```
SUCCESS,
// Auth – Token
TOKEN_EXPIRED, TOKEN_REVOKED, TOKEN_MALFORMED,
REFRESH_TOKEN_EXPIRED, TOKEN_REUSE_DETECTED,
// Auth – Credentials & Session
INVALID_CREDENTIALS, ACCOUNT_LOCKED, SESSION_CONFLICT,
SESSION_ABSOLUTE_TIMEOUT, SESSION_TERMINATED,
// Auth – Recovery
RESET_TOKEN_INVALID, RESET_TOKEN_EXPIRED,
// Resource
RESOURCE_NOT_FOUND, RESOURCE_ALREADY_EXISTS,
// Business
BUSINESS_RULE_VIOLATION, INSUFFICIENT_STOCK, BOM_CIRCULAR_REFERENCE, MRP_CALCULATION_ERROR,
// System
VALIDATION_FAILED, RATE_LIMIT_EXCEEDED, EXTERNAL_SERVICE_ERROR,
INTERNAL_SERVER_ERROR, ACCESS_DENIED
```

> ⚠️ **Danh sách trên là thiết kế gốc, KHÔNG khớp 1-1 với code.** Tên constant trong code và chuỗi
> `code()` trả về wire lệch nhau ở vài chỗ vì lý do lịch sử. Bảng ánh xạ thực tế sau `F1`:

| Enum constant (dùng trong code/test) | `code()` trả ra wire | HTTP |
|---|---|---|
| `AuthErrorCode.ACCESS_DENIED` | `PERMISSION_DENIED` | 403 |
| `ValidationErrorCode.INVALID_INPUT` | `VALIDATION_ERROR` | 400 |
| `ValidationErrorCode.RESOURCE_NOT_FOUND` | `ENTITY_NOT_FOUND` | 404 |
| `BusinessErrorCode.INSUFFICIENT_STOCK` | `INSUFFICIENT_AVAILABLE_STOCK` | **409** *(F5 đổi từ 422)* |
| *(còn lại)* | trùng tên constant | — |

**Constant thêm ở `F1`** (theo `OmniPlant_MVP_Production_Backend_Handoff.docx` §8.2):
`STATE_CONFLICT` (409), `IDEMPOTENCY_CONFLICT` (409), `CONCURRENT_MODIFICATION` (409),
`RESERVATION_EXCEEDED` (409), `PLANNED_QUANTITY_EXCEEDED` (409), `LOT_NOT_ELIGIBLE` (409),
`LOT_REQUIRED` (400), `APPROVAL_REASON_REQUIRED` (400).

**Constant thêm ở `D8a`:** `TOKEN_REUSE_DETECTED` (401) — refresh token đã bị rotate away mà quay
lại. Cùng **401** với `REFRESH_TOKEN_EXPIRED` nên `code` là thứ **duy nhất** phân biệt "đăng nhập
lại đi" với "phiên của bạn vừa bị thu hồi vì phát hiện replay". Bất biến `B80`.

**Constant thêm ở `D8b`:** `SESSION_ABSOLUTE_TIMEOUT` (401) — phiên sống quá 30 ngày kể từ **login**,
bị thu hồi dù vẫn đang hoạt động. Đây là mã **thứ ba** cùng **401** trên `POST /auth/refresh`:

| `code` | Nghĩa | FE nên nói gì với user |
|---|---|---|
| `REFRESH_TOKEN_EXPIRED` | Token hỏng/hết hạn tự nhiên | "Đăng nhập lại" |
| `TOKEN_REUSE_DETECTED` | Replay ⇒ **mọi phiên đã bị thu hồi** | Cảnh báo bảo mật rõ ràng |
| `SESSION_ABSOLUTE_TIMEOUT` | Hết hạn theo chính sách 30 ngày | "Phiên đã hết hạn, đăng nhập lại" — **không** phải cảnh báo bảo mật |

⇒ HTTP status **không** phân biệt được ba trường hợp này. Client **phải** rẽ theo `code` (`A7`).
Bất biến `B81`.

**Constant thêm ở `F5`:** `MISSING_BOM` (409) — anh em của `MISSING_ROUTING`. Trước `F5`,
`BomLookupService.getActiveBom` ném `RESOURCE_NOT_FOUND` (404) trong khi routing tương ứng trả 409,
dù spec §8.1 coi hai lỗi cùng loại (nợ #14).

> **Nợ #9 đã trả ở `F5`.** `INSUFFICIENT_STOCK` nay là **409**, và mọi lỗi *sai trạng thái* của
> work order / material issue / reservation / WIP đã chuyển `OPERATION_NOT_ALLOWED` →
> `STATE_CONFLICT` (409). Lỗi *lot sai trạng thái* dùng `LOT_NOT_ELIGIBLE` (409) thay vì
> `OPERATION_NOT_ALLOWED`.
>
> `OPERATION_NOT_ALLOWED` (422) **vẫn còn dùng có chủ đích** cho **validate master data**, không phải
> state machine: item/warehouse `INACTIVE`, item sai `ItemType`, item và warehouse khác company,
> lot không thuộc item. Đó là "dữ liệu đầu vào sai", không phải "trạng thái chứng từ không cho phép" —
> đừng đổi tiếp cho "đồng bộ".
>
> **[`D7`, 2026-07-30] Nợ #9 đã trả hết.** 4 module còn lại đã được rà từng chỗ một (44 throw site):
> `bom` sửa **1**, `purchasing` sửa **8**, `organization` và `sales` sửa **0** (vốn đã đúng tiêu chí).
> Bảng liệt kê từng chỗ ở `FRONTEND_ALIGNMENT_ROADMAP.md §6.1.6`. Ba tiêu chí rút ra, dùng cho mọi
> module về sau:
>
> | Loại kiểm tra | Code | HTTP |
> |---|---|---|
> | Đọc **status** của chứng từ (`isDraft()`, `isApproved()`, `canReceive()`, `status != X`) | `STATE_CONFLICT` | 409 |
> | Số lượng vượt **trần của chứng từ** (nhận vượt số đặt, duyệt vượt số xin, good vượt plan) | `PLANNED_QUANTITY_EXCEEDED` | 409 |
> | Master data (`INACTIVE`, sai `ItemType`, khác company/plant), **nội dung chứng từ thiếu** (BOM/routing không có line), dữ liệu đầu vào sai (nhiều supplier trong 1 PR, sai `SupplySuggestionType`, `dueDate` < `orderDate`) | `OPERATION_NOT_ALLOWED` | **422** |
>
> Ranh giới hay bị nhầm nhất: **"chứng từ chưa có dòng nào" là 422, không phải 409.** Tiền lệ gốc là
> `RoutingService.activate` (`F4`) — nó đã tách đúng hai nhánh này từ trước `D7`.
>
> **[`D11`, 2026-07-31] `planning` đã được rà nốt.** `D7` tuyên bố nợ #9 "đóng hết" nhưng phạm vi rà
> của nó là 4 module `bom`/`purchasing`/`organization`/`sales` — `planning` **chưa bao giờ** nằm
> trong đó (nợ #26). 3 chỗ đọc status chứng từ nay là `STATE_CONFLICT` (409):
> `PlanningDemandService.cancel`, `SupplySuggestionService.convertToWorkOrder`,
> `SupplySuggestionService.ensureDraft`. Chi tiết + danh sách chỗ **cố ý giữ 422**: bất biến `B74`
> ở `module/planning/CLAUDE.md`.
>
> 🔴 **Bài học rút ra khi rà module tiếp theo:** dấu hiệu mạnh nhất **không phải** là bảng tiêu chí ở
> trên, mà là **hai đường vào cùng một điều kiện nghiệp vụ trả 2 status khác nhau**. Ở nợ #26,
> `convert-to-purchase-requisition` (409 từ `D7`) và `convert-to-work-order` (422) đọc **cùng** một
> `!suggestion.isApproved()`. Khi rà, hãy tìm những cặp như vậy trước — chúng là bằng chứng tự thân,
> không cần viện tới tài liệu.

## 5.4 Global Exception Handler (`@RestControllerAdvice`)

Xử lý theo thứ tự ưu tiên (thêm `@Order(HIGHEST_PRECEDENCE)`):

| Exception | HTTP | ErrorCode | `errors[]`? |
|---|---|---|---|
| `MultiErrorException` | theo `httpStatus` | theo `errorCode` | ✅ khi có field errors |
| `AppException` (mọi lỗi nghiệp vụ) | theo `httpStatus` | theo `errorCode` | — |
| `MethodArgumentNotValidException` (`@Valid` body) | 400 | `VALIDATION_ERROR` | ✅ |
| `ConstraintViolationException` (`@Validated` path/query) | 400 | `VALIDATION_ERROR` | ✅ |
| `MethodArgumentTypeMismatchException` (vd UUID sai ở path) | 400 | `VALIDATION_ERROR` | ✅ |
| `HttpMessageNotReadableException` (JSON hỏng/thiếu body) | 400 | `VALIDATION_ERROR` | — |
| `HttpRequestMethodNotSupportedException` | 405 | `VALIDATION_ERROR` | — |
| `AccessDeniedException` (Spring) | 403 | `PERMISSION_DENIED` | — |
| `ObjectOptimisticLockingFailureException` (`@Version`) | 409 | `CONCURRENT_MODIFICATION` | — |
| `DataIntegrityViolationException` | 409 | `RESOURCE_ALREADY_EXISTS` | — |
| `Exception` (catch-all) | 500 | `INTERNAL_SERVER_ERROR` | — |

> 5 handler ở giữa bảng được thêm ở `F1`. Trước đó chúng **không** được handle nên rơi xuống
> catch-all và trả **500** — một UUID sai định dạng ở path cũng thành lỗi server.

> **Catch-all không bao giờ expose stack trace ra client.** Log đầy đủ phía server.

## 5.5 Auth Entry Points (Spring Security Layer)

Khi Spring Security chặn request (chưa vào controller):
- **401** (`AuthenticationEntryPoint`): Trả `ApiResponse.error(resolveAuthErrorCode, message)` — phân biệt `TOKEN_EXPIRED`, `TOKEN_REVOKED`, `TOKEN_MALFORMED`
- **403** (`AccessDeniedHandler`): Trả `ApiResponse.error(ACCESS_DENIED, "Insufficient permissions")`

## 5.6 Response Headers Chuẩn

| Header | Mô tả |
|---|---|
| `X-Trace-Id` | UUID 16 ký tự – correlate logs với request |
| `X-RateLimit-Limit` | Giới hạn request trong window |
| `X-RateLimit-Remaining` | Số request còn lại |
| `X-RateLimit-Rule` | Rule đang áp dụng |
| `Retry-After` | Giây cần chờ (chỉ khi 429) |
| `X-Session-Warning` | Chỉ có khi kick session cũ |

### 5.6.1 Request header `X-Plant-Id` — phạm vi áp dụng *(chốt ở `D1`, 2026-07-28)*

`X-Plant-Id` là header **request** (không phải response), do `F1` thêm, và là **cross-check chứ
không phải nguồn `plantId`** (quyết định `F1-D6`). Nó **chỉ** áp dụng cho endpoint **nêu plant tường
minh** — `/plants/{plantId}/…` hoặc `plantId` trong query/body:

| Nhóm endpoint | Có cross-check? | Vì sao |
|---|---|---|
| Nêu `plantId` tường minh (`/plants/{plantId}/work-orders`, `/planning-runs`, `/planning-demands`, `/sales-orders`) | ✅ Có | Có **hai** nguồn plant (header và request) ⇒ có cái để đối chiếu; lệch nhau ⇒ 409 `STATE_CONFLICT` |
| Định danh bằng aggregate (`/work-orders/{id}/…`, `/material-issues`, `/production-receipts/…`) | ❌ **Không, có chủ đích** | Chính `workOrderId` **là** scope — không có giá trị thứ hai để header đối chiếu, và `@PreAuthorize` đã chặn work order thuộc plant khác. Thêm check ở đây là thêm code không kiểm được gì |

⇒ Đừng "wire cho đồng bộ": endpoint định danh bằng aggregate **không** nhận header này.

> **[`F7`, 2026-07-31] Ranh giới là theo ENDPOINT, không theo controller.** Trước `F7`, mục này ghi
> "`ManufacturingExecutionController` cố ý không nhận header" — đúng lúc đó, vì **mọi** endpoint của
> nó định danh bằng aggregate. `F7` thêm `GET /production-executions/candidates?plantId=…` (spec §5.1),
> endpoint đầu tiên của controller đó nêu plant tường minh ⇒ nó **có** cross-check, đúng theo hàng 1
> của bảng trên. Các endpoint còn lại của controller vẫn không. Khi thêm endpoint mới, hỏi *"endpoint
> này có hai nguồn plant không?"*, đừng hỏi *"controller này có nhận header không?"*.
>
> 🔴 **[`F8`, 2026-07-31] Phase thứ BA chạm đúng cái bẫy đó.** `F8` thêm 4 endpoint vào cùng
> controller, và chúng chia **cả hai** phía của ranh giới:
>
> | Endpoint `F8` | Cross-check? | Vì sao |
> |---|---|---|
> | `GET /production-receipts/candidates?plantId=` | ✅ có | nêu plant tường minh |
> | `GET /production-receipts?plantId=&status=` | ✅ có | nêu plant tường minh |
> | `GET /material-issues?plantId=&workOrderId=` | ✅ có | nêu plant tường minh (`workOrderId` chỉ là filter) |
> | `GET /production-executions?workOrderId=` | ❌ **cố ý không** | định danh bằng aggregate — **một** nguồn plant duy nhất, không có gì để đối chiếu |
>
> ⇒ `ManufacturingExecutionController` nay chứa **cả hai loại cùng lúc**. Câu hỏi vẫn là *"endpoint
> này có hai nguồn plant không?"*. **Cả hai phía đều được pin bằng test** trong
> `ManufacturingExecutionControllerTest`: 3 endpoint đầu trả **409** khi header lệch, endpoint thứ tư
> trả **200** và bỏ qua header — comment không đỏ được, test thì có.

## 5.7 Request Tracing

Mỗi request được gán `traceId` (UUID 16 ký tự):
- Ưu tiên lấy từ header `X-Trace-Id` nếu có (distributed tracing từ upstream)
- Nếu không → tự sinh mới
- Lưu vào MDC → xuất hiện trong mọi dòng log → dễ trace khi debug
- Trả lại client qua header `X-Trace-Id`

**Logback pattern**: `%d{ISO8601} [%X{traceId}] [%X{userId}] [%X{clientIp}] %-5level %logger{36} - %msg%n`

## 5.8 Controller Patterns

| Operation | HTTP | Status trả về | ApiResponse |
|---|---|---|---|
| GET single | `GET /{id}` | 200 | `ok(data)` |
| GET list phân trang | `GET /` | 200 | `ok(PageResult.from(page))` |
| POST create | `POST /` | 201 | `created(data)` |
| PATCH partial update | `PATCH /{id}` | 200 | `ok(data)` |
| PUT full replace | `PUT /{id}` | 200 | `ok(data)` |
| DELETE | `DELETE /{id}` | 200 | `noContent("Deleted")` |
# API Guide cho Frontend — Manufacturing ERP

> **Audit update ngày 2026-09-02 (track `AR-*`):** response audit thêm 11 field và endpoint list thêm
> 6 filter — **thuần additive, không xoá/đổi tên field nào**. Đáng chú ý: `entityId` **quay lại**
> response, `plantId`/`companyId`/`warehouseId` bắt đầu có giá trị thật (audit lịch sử vẫn `null`, cố
> ý không backfill), và detail có thêm `entities[]` + `metadata`. Bảng đầy đủ:
> `docs/fe-guide-audit-logs-and-inventory-lots.md §1.5`.
>
> **Audit update ngày 2026-08-17:** `GET /audit-logs/{auditLogId}` nay trả field-level diff thật trong
> `changes[]` cho các audit mới. Các đoạn lịch sử nói mảng này luôn rỗng đã được thay thế bởi hành vi mới này.
>
> 🔴 **Sửa ngày 2026-08-25:** `changes[].oldValue`/`newValue` là **`string` hoặc `null`, luôn luôn** —
> đúng như OpenAPI công bố. Từ 2026-08-17 tới 2026-08-25 hai field này mang kiểu JSON gốc (object /
> array / number / boolean tuỳ dòng), khiến một snapshot có cấu trúc lọt ra dưới dạng object và làm
> sập màn hình Audit của FE. Object/array nay được serialize thành **chuỗi JSON compact** (một
> `JSON.parse` là lấy lại cấu trúc); số/boolean thành dạng chuỗi (`"10"`, `"true"`); chuỗi giữ nguyên
> **không kèm dấu nháy JSON**; `null` vẫn là `null`.

> **Thông báo thay đổi URL ngày 2026-08-17:** `/api` hiện là context path toàn ứng dụng và `/v1`
> nằm ở method mapping. Năm nhóm `auth`, `admin/users`, `users`, `access`, `sales-orders` đã đổi vị trí
> `/v1`; xem bảng migration và cấu hình Axios tại
> [`fe-api-v1-migration-guide.md`](./fe-api-v1-migration-guide.md). Các URL cũ của năm nhóm này xuất
> hiện bên dưới chỉ còn giá trị lịch sử và không còn là route đang chạy.

> **Nguồn của tài liệu này:** đọc trực tiếp từ **code đang chạy** (controller + DTO + enum + error code),
> rồi đối chiếu ngược với `docs/fe-spec-omniplant.md`. Chỗ nào backend **lệch** so với spec FE đều
> được ghi rõ ở [§9](#9-những-chỗ-lệch-so-với-spec-fe). Toàn bộ luồng ở [§4](#4-luồng-sản-xuất-đầu-cuối)
> đã được kiểm chứng tự động bằng `ProductionFlowE2EIT` (chạy thật trên Postgres, không mock).
>
> Cập nhật: 2026-08-05 · Tương ứng code sau phase `P3` · Migration mới nhất `V51`

---

## Mục lục

1. [Chạy backend lên](#1-chạy-backend-lên)
2. [Quy ước chung — đọc trước khi code](#2-quy-ước-chung--đọc-trước-khi-code)
3. [Đăng nhập & giữ phiên](#3-đăng-nhập--giữ-phiên)
4. [Luồng sản xuất đầu cuối](#4-luồng-sản-xuất-đầu-cuối) ← phần chính
5. [Tham chiếu endpoint theo màn hình](#5-tham-chiếu-endpoint-theo-màn-hình)
6. [Tham chiếu DTO](#6-tham-chiếu-dto)
7. [Enum](#7-enum)
8. [Mã lỗi](#8-mã-lỗi)
9. [Những chỗ lệch so với spec FE](#9-những-chỗ-lệch-so-với-spec-fe)
10. [Checklist wiring](#10-checklist-wiring)

---

## 1. Chạy backend lên

```bash
docker compose up -d          # Postgres (cổng 5434) + Redis (cổng 6379)
mvn spring-boot:run           # API tại http://localhost:8080
```

| | |
|---|---|
| **Base URL** | `http://localhost:8080` |
| **API prefix** | `/api/v1` — **trừ 5 nhóm** đặt `/v1` sau tên nhóm: `/api/auth/v1`, `/api/admin/users/v1`, `/api/users/v1`, `/api/access/v1`, `/api/sales-orders/v1` (xem [`fe-api-v1-migration-guide.md`](./fe-api-v1-migration-guide.md)) |
| **Swagger UI** | `http://localhost:8080/api/swagger-ui.html` (chỉ khi bật `SPRINGDOC_SWAGGER_UI_ENABLED`) |
| **OpenAPI JSON** | `http://localhost:8080/api/v3/api-docs` (chỉ khi bật `SPRINGDOC_API_DOCS_ENABLED`) |
| **Health check** | `http://localhost:8081/actuator/health` (cổng quản trị riêng, `MANAGEMENT_SERVER_PORT`) |

> Swagger UI là **nguồn chính xác nhất tại thời điểm chạy** — tài liệu này giải thích *cách dùng* và
> *cạm bẫy*, Swagger cho bạn schema sinh tự động từ code. Dùng cả hai.

Endpoint **không cần token**: `/api/auth/v1/{login, refresh, forgot-password, reset-password,
password-challenges/**}`, `/actuator/health`, `/actuator/info`, `/api/v3/api-docs/**`,
`/api/swagger-ui/**`. Mọi endpoint khác — kể cả `/api/auth/v1/{me, logout, logout-all,
password/change}` — đều cần `Authorization: Bearer`.

### 1.1 CORS *(thêm ở `C2-5`, 2026-08-04)*

Trước `C2-5` backend **không có một dòng cấu hình CORS nào** ⇒ mọi request từ browser ở origin khác
đều bị chặn, bất kể API đúng hay sai. Nay:

| | |
|---|---|
| Origin được phép (default dev) | `http://localhost:3000`, `http://localhost:5173` |
| Đổi origin | biến môi trường `CORS_ALLOWED_ORIGINS` (phân tách bằng dấu phẩy) |
| Header được gửi lên | `Authorization`, `Content-Type`, `Idempotency-Key`, `X-Plant-Id`, `X-Trace-Id` |
| Header JS đọc được | `X-Trace-Id`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Rule`, `Retry-After` |
| Credentials | `Access-Control-Allow-Credentials: true` |

> 🔴 **`"*"` không dùng được và backend sẽ từ chối khởi động nếu cấu hình như vậy** — API này gửi
> `Authorization` mọi request, CORS spec cấm wildcard origin trên credentialed request. Origin của bạn
> phải được liệt kê tường minh; gửi cho backend origin dev/demo để thêm vào biến môi trường.
>
> Preflight `OPTIONS` **không** cần token (nó không mang `Authorization` được) — nếu bạn thấy 401 ở
> preflight thì đó là bug backend, báo ngay.

### 1.2 Account dev để test phân quyền *(thêm ở `C2-5`)*

Chạy `src/main/resources/db/dev-seed.sql` (hướng dẫn ở đầu file đó) để có 1 company + **2 plant** +
6 warehouse + 3 account. Mật khẩu phải được cấp dưới dạng BCrypt qua biến
`demo_password_hash` khi chạy seed; repository không còn chứa mật khẩu dùng chung.

| Username | Role | Scope | Dùng để |
|---|---|---|---|
| `manager.a` | `MANAGER` | PLANT-A | Luồng quản lý: tạo/release WO, BOM, duyệt |
| `operator.a` | `OPERATOR` | PLANT-A | Luồng thực thi: xuất vật tư, báo sản lượng |
| `manager.b` | `MANAGER` | PLANT-B | **Cặp với `manager.a` để chứng minh isolation** |
| `admin` | `ADMIN` | GLOBAL | Có sẵn từ trước, thấy mọi plant |

Hai kiểm chứng đã chạy thật, FE nên thấy đúng như vậy:

| Request | Kết quả |
|---|---|
| `manager.a` → `GET /plants/{PLANT-A}/work-orders` | **200** |
| `manager.a` → `GET /plants/{PLANT-B}/work-orders` | **403 `PERMISSION_DENIED`** (không phải mảng rỗng) |
| `operator.a` → `POST /plants/{PLANT-A}/work-orders` | **403** — tạo WO là quyền của `MANAGER` |

> 🔴 **Sai plant thì là 403, KHÔNG phải danh sách rỗng.** Nếu bạn thấy mảng rỗng thay vì 403, nghĩa là
> bộ lọc scope đã nới sai — báo backend, đừng xử lý như "plant không có dữ liệu".
>
> ⚠️ **`@Valid` chạy TRƯỚC kiểm quyền.** Body sai định dạng sẽ trả **400 `VALIDATION_ERROR`** kể cả khi
> user không có quyền — đừng kết luận "tôi có quyền" từ việc không thấy 403.

---

## 2. Quy ước chung — đọc trước khi code

### 2.1 Envelope — mọi response đều cùng một hình dạng

Thành công lẫn lỗi **đều** dùng chung envelope này:

```jsonc
{
  "code":    "SUCCESS",        // luôn có
  "result":  { ... },          // payload
  "message": "OK",             // luôn có
  "errors":  [ ... ]           // chỉ có khi lỗi validation theo field
}
```

🔴 **Cạm bẫy số 1 — `result` và `errors` BIẾN MẤT khỏi JSON khi null**, không phải `"result": null`.
Backend dùng `@JsonInclude(NON_NULL)` trên cả envelope.

```jsonc
// DELETE thành công — KHÔNG có key "result"
{ "code": "SUCCESS", "message": "Deleted successfully" }

// Lỗi — KHÔNG có key "result"
{ "code": "STATE_CONFLICT", "message": "Work order is not in a releasable state" }
```

⇒ Đừng viết `if (res.result === null)`. Dùng `if (!('result' in res))` hoặc `res.result?.…`.

**Luôn phân nhánh theo `code`, không parse `message`.** `message` là chuỗi cho người đọc, có thể đổi
bất cứ lúc nào; `code` là hợp đồng.

### 2.2 Phân trang

Mọi endpoint list đều nhận 4 query param và trả `PageResult` trong `result`:

| Param | Mặc định | Ghi chú |
|---|---|---|
| `page` | `0` | 0-based |
| `size` | `20` | 🔴 **trần cứng 100** — gửi `size=500` server tự cắt còn 100, không báo lỗi. `size<1` → 20 |
| `sortBy` | tuỳ endpoint | tên field entity |
| `sortDir` | `asc`/`desc` tuỳ endpoint | |

```jsonc
{
  "code": "SUCCESS",
  "result": {
    "content": [ /* … */ ],
    "page": 0, "size": 20,
    "totalElements": 150, "totalPages": 8,
    "first": true, "last": false
  },
  "message": "OK"
}
```

> **Ngoại lệ không phân trang** (trả mảng trần trong `result`): `GET /sales-orders/planning-demands`,
> `GET /work-orders/{id}/material-readiness`, `GET /reports/low-stock`, `POST /work-orders/{id}/reserve`.

### 2.3 Header

| Header | Chiều | Bắt buộc? | Ý nghĩa |
|---|---|---|---|
| `Authorization: Bearer <accessToken>` | request | ✅ (trừ `/auth/**`) | |
| `Idempotency-Key: <chuỗi duy nhất>` | request | Khuyến nghị mạnh | Xem §2.4 |
| `X-Plant-Id: <uuid>` | request | Không | Xem §2.5 |
| `X-Trace-Id` | response | — | ID để đối chiếu log khi báo lỗi cho backend |
| `X-RateLimit-*`, `Retry-After` | response | — | Rate limit |

### 2.4 `Idempotency-Key` — bắt buộc cho mọi POST đụng tồn kho

Áp dụng cho: material issue, production execution, production receipt, goods receipt, inventory
receive/issue/adjust.

- **Gửi lại cùng key + cùng payload** → trả lại **đúng chứng từ cũ**, không tạo bản ghi thứ hai.
  Đây là lưới an toàn khi mạng chập chờn / user double-click.
- **Gửi lại cùng key + payload KHÁC** → `409 IDEMPOTENCY_CONFLICT`.
  ⇒ Sinh key mới mỗi khi user sửa form; **đừng** giữ nguyên key cũ với dữ liệu mới.
- Gợi ý sinh key: `<screen>-<workOrderId>-<uuid client sinh>`.

### 2.5 `X-Plant-Id` — chỉ dùng cho endpoint có `plantId` tường minh

Header này là **cross-check**, không phải nguồn dữ liệu. Nếu gửi và **lệch** với `plantId` trong
path/query/body → `409 STATE_CONFLICT` (chặn trước khi vào service). Không gửi thì bỏ qua.

| Loại endpoint | Có kiểm? |
|---|---|
| Có `plantId` trong path/query/body (`/plants/{plantId}/work-orders`, `/planning-runs`, `/material-issues?plantId=`, `/production-receipts/candidates?plantId=`, `/sales-orders`) | ✅ Có |
| Định danh bằng aggregate (`/work-orders/{id}/…`, `/production-executions?workOrderId=`) | ❌ Không — `workOrderId` **chính là** scope |

Sai định dạng UUID trong header → `400 FIELD_FORMAT_INVALID` (không phải 409).

### 2.6 Kiểu dữ liệu

| Kiểu | Định dạng | Ví dụ |
|---|---|---|
| ID | **UUID chuỗi** (spec FE ghi `number` — sai, đã chốt dùng UUID) | `"3f2a9c01-…"` |
| Số lượng | `NUMERIC(19,6)` — **gửi/nhận dạng chuỗi hoặc số, luôn parse bằng decimal library** | `"10.000000"` |
| Ngày | `LocalDate` ISO | `"2026-08-15"` |
| Thời điểm | `Instant` ISO-8601 UTC | `"2026-08-03T08:00:00Z"` |

🔴 **Đừng dùng `parseFloat`/`Number` cho quantity.** Backend dùng `BigDecimal` scale 6; JS float sẽ
làm tròn sai ở nghiệp vụ tồn kho. Dùng `decimal.js` / `big.js`.

> **[2026-08-12] Hai dòng ngày/giờ ở trên nay đúng với code.** Từ trước tới ngày này chúng là *lời
> hứa*, không phải sự thật: một `@Bean ObjectMapper` trong `RedisConfig` đè ObjectMapper của Spring
> Boot nên `spring.jackson.*` bị vô hiệu im lặng ⇒ `LocalDate` đi ra dạng **mảng** `[2026,8,15]` và
> `Instant` dạng **số epoch**. Bean đó đã bị xoá; wire format nay là ISO ở **mọi** endpoint. Client nào
> đang tự parse mảng số/epoch thì nay nhận chuỗi ISO — FE đã xác nhận adapter của họ chấp nhận cả hai
> nên đây không phải breaking change trong thực tế.
>
> **Field `null` KHÔNG đổi** — vẫn xuất hiện đầy đủ trong payload (`"cancelReason": null`), không bị
> lược bỏ. Chỉ envelope là đã lược từ trước (`result`/`errors` biến mất khi null, §1.1). Guard:
> `JsonWireFormatTest`.

---

## 3. Đăng nhập & giữ phiên

### `POST /api/auth/v1/login`

```jsonc
// Request
{ "username": "admin", "password": "…", "deviceId": "web-chrome-01" }  // deviceId tuỳ chọn
```
```jsonc
// Response 200
{
  "code": "SUCCESS",
  "result": {
    "accessToken": "eyJhbGciOi…",   // JWT, TTL 15 phút
    "refreshToken": "b3f1…",        // UUID opaque, TTL 7 ngày
    "tokenId": "9c2e…",             // 🔴 PHẢI lưu — refresh/logout đều cần
    "expiresIn": 900,               // giây
    "deviceId": "web-chrome-01",
    "sessionKicked": false
  },
  "message": "OK"
}
```

🔴 **Lưu cả 3**: `accessToken`, `refreshToken`, **`tokenId`**. Rất dễ quên `tokenId` — thiếu nó thì
không refresh được.

⚠️ **Hệ thống hiện đang enforce single-session**: đăng nhập mới sẽ **huỷ toàn bộ phiên cũ** của user
đó (mọi thiết bị). Nếu FE mở 2 tab với 2 lần login riêng, tab cũ sẽ hỏng ở lần refresh kế tiếp.

### `POST /api/auth/v1/refresh`

Gọi khi access token sắp/đã hết hạn (bắt `401 TOKEN_EXPIRED`).

```jsonc
{ "refreshToken": "b3f1…", "tokenId": "9c2e…", "deviceId": "web-chrome-01" }
```

🔴 **[Sửa 2026-08-06] Endpoint này KHÔNG cần header `Authorization` — đừng gửi access token, kể cả
token cũ/hỏng/hết hạn.** Trước bản sửa này, backend có bug: nếu bạn gắn một access token rỗng/hỏng
vào header (kể cả khi endpoint là `permitAll`), request sẽ bị chặn bằng `401 TOKEN_MALFORMED` **trước
khi** backend kịp đọc `refreshToken` trong body — đúng lúc access token hỏng lại là lúc bạn cần gọi
endpoint này nhất. Nếu HTTP client/interceptor của bạn **tự động** gắn `Authorization: Bearer <token
đang lưu>` vào mọi request, hãy loại trừ `/auth/refresh` khỏi interceptor đó (hoặc đảm bảo nó không
gắn header rỗng/`"null"` khi chưa có token) — dù backend giờ đã bỏ qua header này trên endpoint này
hoàn toàn, không còn cách nào để nó chặn request nữa.

Trả về **cặp token hoàn toàn mới** (rotation) — `tokenId` cũng đổi. **Ghi đè cả 3 giá trị đã lưu.**

| Lỗi | Nghĩa | FE làm gì |
|---|---|---|
| `401 REFRESH_TOKEN_EXPIRED` | Refresh token hết hạn / không hợp lệ | Xoá storage, về màn login |
| `401 TOKEN_REUSE_DETECTED` | Token **đã bị rotate** mà vẫn được gửi lại (trong vòng 60s) ⇒ hệ thống coi là bị đánh cắp và **đã thu hồi toàn bộ phiên của user trên mọi thiết bị** | Xoá storage, về màn login, hiện thông báo bảo mật rõ ràng (**không** gộp chung message với dòng trên) |
| `401 SESSION_ABSOLUTE_TIMEOUT` | Phiên đã sống **quá 30 ngày kể từ lúc login** và bị thu hồi theo chính sách, dù user vẫn đang dùng đều. **Không** phải sự cố bảo mật | Xoá storage, về màn login với message trung tính kiểu "Phiên đã hết hạn, vui lòng đăng nhập lại" — **đừng** dùng lại message cảnh báo của dòng trên |

🔴 **Cả ba lỗi trên đều là HTTP 401 — status không phân biệt được chúng.** Rẽ nhánh theo `code`,
đừng theo status. Đây chính là lý do envelope luôn có `code` (§5.1).

⏱️ **30 ngày là mốc tuyệt đối, refresh không kéo dài nó.** Mốc đếm từ lần `login` gần nhất và được
mang nguyên qua mọi lần rotate — user dùng liên tục vẫn bị buộc đăng nhập lại đúng hạn. Timeout chỉ
được kiểm **khi gọi refresh**, nên access token đang cầm vẫn dùng được tới khi hết TTL của nó.

🔴 **Đừng gửi 2 request refresh song song với cùng một `tokenId`.** Request thứ hai sẽ rơi đúng vào
`TOKEN_REUSE_DETECTED` và user bị đăng xuất mọi thiết bị **oan**. FE phải serialize refresh: khi có
nhiều request 401 cùng lúc, chỉ **một** cái được gọi refresh, các cái còn lại **chờ** kết quả rồi
retry — đừng để mỗi request tự gọi refresh riêng.

### `POST /api/auth/v1/logout` — body `{refreshToken, tokenId}` · `POST /api/auth/v1/logout-all` — không body

### `GET /api/auth/v1/me` — profile, permissions, plant & access scope sau login

Endpoint duy nhất trong `AuthController` **không** permit-all — cần `Authorization: Bearer` hợp lệ,
không cần permission `PERM_*` cụ thể (tự xem thông tin của chính mình).

```jsonc
// Response 200
{
  "code": "SUCCESS",
  "result": {
    "userId": "d22df979-…", "username": "admin", "email": "admin@erp.local", "status": "ACTIVE",
    "roles": ["ADMIN"],
    "permissions": ["PERM_WORK_ORDER_MANAGE", "PERM_MRP_RUN", "…"],   // union phẳng, giống claim JWT
    "scopes": [
      {
        "scopeType": "PLANT", "companyId": "…", "companyCode": "CO-01",
        "plantId": "…", "plantCode": "PL-HN",
        "permissions": ["PERM_WORK_ORDER_MANAGE", "…"]   // permission riêng theo scope này
      }
    ],
    "defaultPlantId": "…"   // plant đầu tiên theo (companyCode, plantCode), hoặc null
  },
  "message": "OK"
}
```

Không token → `401`. Danh sách quyền cũng nằm sẵn trong JWT (claim `roles`, union `ROLE_*` + `PERM_*`)
nếu FE muốn ẩn/hiện nút ngay mà không đợi round-trip — nhưng chỉ `/auth/me` mới cho biết `userId`,
`email`, và permission **theo từng company/plant** (`scopes[]`).

Chi tiết đầy đủ (code mẫu TypeScript, cách chọn plant từ `scopes[]`, và bằng chứng chạy thật):
**[`fe-session-bootstrap.md`](./fe-session-bootstrap.md)**.

### `POST /api/auth/v1/forgot-password` / `POST /api/auth/v1/reset-password` (`D8c`)

```jsonc
// POST /auth/forgot-password { "email": "user@erp.local" }
// Response 200 — LUÔN cùng shape này, dù email có tồn tại hay không (chống dò email)
{ "code": "SUCCESS", "result": null, "message": "If this email exists, a reset link has been sent" }
```
🔴 **Đừng suy luận từ response** rằng email có tồn tại hay không — server cố ý trả **y hệt** dù có
hay không tìm thấy tài khoản. Reset link/token hiện tại chỉ được **log ra server console** (chưa nối
SMTP/dịch vụ email thật — quyết định hạ tầng của `D8c`), nên trong môi trường dev/test phải lấy token
từ log backend, không phải từ hộp thư thật.

```jsonc
// POST /auth/reset-password { "token": "…", "newPassword": "…" }
// Response 200 khi hợp lệ
{ "code": "SUCCESS", "result": null, "message": "Password reset successfully" }
// 401 khi token sai/hết hạn/đã dùng
{ "code": "RESET_TOKEN_INVALID", "result": null, "message": "Reset token is invalid or has expired" }
```
Token chỉ dùng được **một lần**, sống **15 phút**, và một request forgot-password mới sẽ **vô hiệu**
token cũ (không tích luỹ nhiều token sống cùng lúc). Reset thành công đăng xuất **mọi thiết bị** —
FE phải điều hướng về màn login sau khi reset, không chỉ đóng modal.

### `PATCH /api/admin/users/v1/{userId}/unlock` (`D8c`) — không body, role `ADMIN`

Xoá bộ đếm brute-force trong Redis và đưa tài khoản về `ACTIVE`. `200 { result: null }` khi thành
công; `403` nếu người gọi không có role `ADMIN`. 🔴 **[2026-11-06]** Tài khoản `PENDING_SETUP` (xem mục dưới) **giữ
nguyên** `PENDING_SETUP` — mở khoá không phải đường vòng qua bước đặt mật khẩu.

### Tài khoản mới: đăng nhập lần đầu bằng mã email, và đổi mật khẩu *(2026-11-06, `V96`)*

> Đường dẫn thật là `/api/auth/v1/...` và `/api/admin/users/v1/...` (nhóm này đặt `v1` sau tiền tố tài nguyên). Hợp đồng đầy đủ + câu trả lời cho
> 7 câu hỏi của FE: `BE_RESPONSE_ACCOUNT_PASSWORD_FLOWS_2026-11-06.md`.

**Admin tạo tài khoản** — `POST /api/admin/users/v1/accounts/commands`, body `{kind:"CREATE", username, email, password}` (password là mật khẩu **ban đầu**),
quyền `PERM_ACCESS_MANAGE` ở phạm vi GLOBAL. `201` trả tài khoản với `status:"PENDING_SETUP"`. Chỉ `kind:"CREATE"` được hỗ trợ (kind khác ⇒ `400`
field `kind`). `POST /api/users/v1` cũ **không đổi** (vẫn tạo `ACTIVE`).

**Quản lý tài khoản `/api/users/v1/**`** *(2026-11-12)* — danh sách, tạo, sửa, vô hiệu hoá, gán/gỡ role nay yêu cầu quyền
`PERM_ACCESS_MANAGE` (trước là role `ADMIN`; tài khoản `ADMIN` vẫn qua). `GET /api/users/v1/{userId}` còn cho phép **chính chủ** đọc
tài khoản của mình — trước đó nhánh này luôn trả `403` do lỗi so sánh kiểu. `PATCH /api/users/v1/{userId}` **không** có nhánh chính chủ:
người dùng đổi mật khẩu qua `POST /api/auth/v1/password/change`.

**Đăng nhập lần đầu** (4 bước, FE ở nguyên `/login`):

| # | Gọi | Kết quả |
|---|---|---|
| 1 | `POST /api/auth/v1/login` `{username, password: <mật khẩu ban đầu>}` | `200`, `result = {kind:"PASSWORD_SETUP", email, challenge:{id, expiresAt, resendAt}}` — **không có token**; mã 6 số đã được gửi tới email. Sai mật khẩu ⇒ `401 INVALID_CREDENTIALS`, không gửi mã. **Rẽ nhánh theo `result.kind`**: nhánh token không có field này |
| 2 | `POST /api/auth/v1/password-challenges/verify` `{challengeId, code}` | `result = {setupToken}` |
| 3 | `POST /api/auth/v1/password-challenges/complete` `{setupToken, password}` | `200`, **không có `result`**. Tài khoản thành `ACTIVE`, mọi phiên cũ bị thu hồi, **không** tự đăng nhập |
| 4 | `POST /api/auth/v1/login` với mật khẩu **mới** | token như thường |

Gửi lại mã: `POST /api/auth/v1/password-challenges` `{identifier, purpose:"SETUP", previousId:<challenge.id đang có>}` ⇒ challenge mới, mã cũ chết.
Trước `resendAt` ⇒ `429 RESEND_TOO_SOON` + header `Retry-After` (giây). Mã sống 10 phút; token setup sống 10 phút sau verify.

**Quên mật khẩu bằng mã** — `POST /api/auth/v1/password-challenges` `{identifier, purpose:"RESET"}` (identifier = email hoặc username) ⇒ luôn `200` cùng hình dạng
dù tài khoản có tồn tại hay không (không tiết lộ); rồi verify → complete như trên. Tài khoản chưa setup **không** dùng được RESET.
Luồng cũ `forgot-password`/`reset-password` (link) vẫn còn, không đổi.

**Đổi mật khẩu khi đã đăng nhập** — `POST /api/auth/v1/password/change` (Bearer) `{currentPassword, password}` ⇒ `200`, không `result`. Thu hồi **mọi** phiên kể cả phiên
đang dùng ⇒ về `/login`. Mật khẩu hiện tại sai ⇒ **`422`** `CURRENT_PASSWORD_INVALID` (cố ý không phải 401: client sẽ hiểu 401 là hết phiên).

**Policy mật khẩu mới** (complete, change, tạo tài khoản): ≥ 8 ký tự, có chữ hoa, chữ thường, số, ký tự đặc biệt, ≤ 72 byte, và khác mật khẩu hiện tại ⇒
`422 PASSWORD_POLICY` + `errors[{field:"password", message}]`. Token **chưa bị tiêu** khi policy sai nên cùng token thử lại được.

**Idempotency:** `Idempotency-Key` được nhận nhưng bỏ qua; mỗi bước tự replay-safe (xem §4 của file trả lời). Riêng `change` không replay được — sau timeout hãy thử
đăng nhập bằng mật khẩu mới.

### Gợi ý interceptor

```ts
// 1. Gắn token
config.headers.Authorization = `Bearer ${accessToken}`;

// 2. Bắt 401 → refresh 1 lần → retry; refresh lỗi → về login
// 🔴 Chống refresh song song: nếu 3 request cùng 401 một lúc, chỉ được gọi refresh MỘT lần
//    rồi cho 2 request kia chờ kết quả. Gọi refresh 3 lần song song sẽ làm rotation đá nhau.
```

---

## 4. Luồng sản xuất đầu cuối

Đây là chuỗi **đã được kiểm chứng chạy đúng** bởi `ProductionFlowE2EIT`. Làm theo đúng thứ tự này thì
màn hình sẽ hiển thị đủ dữ liệu.

```
Sales Order ──confirm──► Planning Demand
                              │
                         MRP Run ──► Supply Suggestion (MAKE/BUY)
                              │            │ approve → convert
                              │            ▼
                         Work Order ──reserve──► release ──issue──► production execution
                                                                          │
                                                          production receipt (DRAFT
                                                          → PENDING_APPROVAL → APPROVED)
                                                                          │
                                                                    QC disposition
                                                                          │
                                                            lot AVAILABLE + SO fulfilled
```

### Bước 0 — Lấy master data cho dropdown

| Cần | Endpoint |
|---|---|
| Company | `GET /api/v1/companies` |
| Plant | `GET /api/v1/companies/{companyId}/plants` |
| Warehouse | `GET /api/v1/plants/{plantId}/warehouses` |
| Item | `GET /api/v1/companies/{companyId}/items` |

Item Master dùng `PERM_ITEM_READ` cho list/detail và `PERM_ITEM_MANAGE` cho create/update/activate/
deactivate (`V57`). User scope Plant được truy cập Item của Company cha; Company khác vẫn trả `403`.
Không gate Item bằng `PERM_INVENTORY_*`.

`ItemResponse.lotTracked` quyết định màn hình receipt có **bắt buộc** nhập `lotNumber` hay không.
Tương tự, `ItemResponse.serialTracked` (P5, 2026-08-06) quyết định màn hình có **bắt buộc** nhập
`serialNumber` hay không — **hai cờ này loại trừ nhau**, một item chỉ có thể là lot-tracked, serial-
tracked, hoặc không tracking gì, không bao giờ cả hai cùng lúc.

---

### Bước 1 — Tạo & xác nhận Sales Order

**`POST /api/sales-orders/v1`** → `201`

```jsonc
{
  "companyId": "…", "plantId": "…",
  "orderNo": "SO-2026-001",
  "customerName": "Công ty ABC",
  "orderDate": "2026-08-01",
  "lines": [
    { "itemId": "…", "orderedQuantity": 10, "dueDate": "2026-08-15" }
  ]
}
```

> `lineNo` do **server** cấp (1..N theo thứ tự mảng) — FE không gửi.
> Đơn tạo ra ở trạng thái `DRAFT`, **chưa** sinh nhu cầu cho MRP.

**`POST /api/sales-orders/v1/{salesOrderId}/confirm`** → `200`, status → `CONFIRMED`.
Đây mới là lúc mỗi dòng đơn sinh **một** planning demand.

> Huỷ đơn: `POST /{id}/cancel` — chỉ được từ `DRAFT`/`CONFIRMED`. Từ `IN_PRODUCTION` trở đi →
> `409 STATE_CONFLICT` (đã có work order, có thể đã xuất vật tư).

---

### Bước 2 — Màn hình Planning: chọn demand rồi chạy MRP

**`GET /api/sales-orders/v1/planning-demands?plantId={…}&horizonEnd=2026-09-30`** → mảng trần

```jsonc
{
  "code": "SUCCESS",
  "result": [{
    "salesOrderId": "…", "salesOrderCode": "SO-2026-001",
    "salesOrderLineId": "…",
    "planningDemandId": "…",          // 🔴 ĐÂY là id để gửi vào demandLineIds
    "lineNo": 1,
    "itemId": "…", "itemSku": "FG-100", "itemName": "Widget", "uom": "EA",
    "quantity": "10.000000", "dueDate": "2026-08-15"
  }],
  "message": "OK"
}
```

🔴 **Gửi `planningDemandId`, KHÔNG phải `salesOrderLineId`** vào `demandLineIds` ở bước sau. Hai
field này nằm cạnh nhau và rất dễ nhầm.

**`POST /api/v1/planning-runs`** → `201`

```jsonc
{
  "companyId": "…", "plantId": "…", "warehouseId": "…",
  "horizonStartDate": "2026-08-01",
  "horizonEndDate": "2026-09-30",
  "demandLineIds": ["…"]     // tuỳ chọn: bỏ trống ⇒ tự quét mọi demand trong horizon
}
```

Response `MrpRunResponse` chứa sẵn header cho màn hình: `code` (vd `RUN-A1B2C3D4`), `status`,
`grossDemandQuantity`, và 4 ô summary `shortageLines` / `plannedWorkOrders` /
`plannedPurchaseRecommendations` / `blockedProposals`.

> 🔴 **[2026-08-14] Endpoint này nay có `Idempotency-Key` — TRƯỚC ĐÓ HEADER BỊ BỎ QUA HOÀN TOÀN.**
> Gửi cùng một key với **cùng** payload ⇒ trả lại **đúng run đã tạo lần đầu** (không chạy MRP lần
> hai). Gửi cùng key với payload **khác** ⇒ `409 IDEMPOTENCY_CONFLICT`. **Không** gửi header ⇒ hành vi
> y như cũ: mỗi lần gọi là một run mới.
>
> Vì sao quan trọng hơn vẻ ngoài: mỗi run sinh **một bộ suggestion riêng** cho cùng nhu cầu. Ba run
> trùng ⇒ ba bộ proposal song song, và convert proposal của run này rồi mở màn hình của run kia sẽ
> trông như "convert bị mất". Retry mù (transport retry) trên endpoint này là cách dễ nhất tự tạo ra
> tình huống đó — hãy gắn `Idempotency-Key` cho mọi lần chạy MRP từ script seed hoặc từ nút bấm có
> thể double-click.
>
> Hai điều cần biết:
> 1. **Run kết thúc `FAILED` vẫn giữ key của nó.** Muốn chạy lại sau khi thất bại thì **đổi key mới** —
>    gửi lại key cũ chỉ trả về đúng cái run `FAILED` đó.
> 2. **Hai request cùng key chạy song song**: request thứ hai trả `409 RESOURCE_ALREADY_EXISTS` (không
>    phải `IDEMPOTENCY_CONFLICT`) và trả **ngay**, không chờ run thứ nhất tính xong.

**`GET /api/v1/planning-runs/{runId}/requirements`** (mặc định `size=50`, sort `requirementLevel asc`)

Bảng netting. Đẳng thức FE có thể tự kiểm:
```
netRequiredQuantity = max(0, grossRequiredQuantity + safetyStockQuantity − projectedAvailableQuantity)
```
🔴 **Dùng `projectedAvailableQuantity` từ API, đừng tự tính `availableQuantity + openSupplyQuantity`.**
Khi một item xuất hiện trên nhiều dòng, hai công thức ra kết quả khác nhau và cách tự tính là **sai**
(nó không trừ phần coverage đã bị các dòng trước dùng).
`projectedAvailableQuantity` = `null` nghĩa là run chạy **trước** `V40` → hiển thị `—`.

**`GET /api/v1/planning-runs/{runId}/suggestions`** — bảng đề xuất

| Field | Ý nghĩa hiển thị |
|---|---|
| `supplyType` | `MAKE` (→ work order) / `BUY` (→ purchase requisition) |
| `exceptionState` | `READY` (xanh) · `WARNING` (vàng, vẫn convert được) · `BLOCKED` (đỏ, **không** convert được) |
| `messages[]` | `MATERIAL_SHORTAGE`, `MISSING_BOM`, `MISSING_ROUTING`, `SYSTEM_FALLBACK_USED` |
| `sourceRoutingCode` / `sourceRoutingVersion` | Routing đóng băng lúc chạy MRP; `null` với `BUY` và với `MAKE` bị `BLOCKED` |
| `convertedWorkOrderId` | ≠ null ⇒ đã convert rồi, **disable nút convert** |

---

### Bước 3 — Duyệt & chuyển đề xuất thành Work Order

```
POST /api/v1/supply-suggestions/{suggestionId}/approve    body: {"decisionNote": "…"}  (tuỳ chọn)
POST /api/v1/supply-suggestions/{suggestionId}/convert-to-work-order
     body: {"outputWarehouseId": "…", "workOrderNo": "…", "plannedStartAt": "…", "plannedEndAt": "…"}  (tất cả tuỳ chọn)
```
Response là `SupplySuggestionResponse` — lấy **`convertedWorkOrderId`** để điều hướng sang màn WO.

Với `BUY`: `POST /api/v1/supply-suggestions/{suggestionId}/convert-to-purchase-requisition`.

| Lỗi | Nghĩa |
|---|---|
| `409 STATE_CONFLICT` | Suggestion chưa `APPROVED`, hoặc đã `CONVERTED` |
| `409 MISSING_BOM` / `409 MISSING_ROUTING` | Suggestion đang `BLOCKED` — phải sửa master data trước |
| `422 OPERATION_NOT_ALLOWED` | Gọi convert-to-work-order trên đề xuất `BUY` (sai loại) |

---

### Bước 4 — Giữ vật tư (reserve)

**`POST /api/v1/work-orders/{workOrderId}/reserve`** body `{"expectedWorkOrderVersion": <version>}` → `201`, trả **mảng**
reservation (FEFO tự động), mỗi phần tử có `workOrderVersion` = version của lệnh **sau** lệnh này.

> 🔴 **G8a (2026-10-19): mọi lệnh trên work order bắt buộc gửi version vừa đọc** — PATCH (`expectedVersion`), plan/release/cancel/
> reserve/release-reservations (`expectedWorkOrderVersion` trong body), tạo reservation (body), xoá reservation (query
> `?expectedWorkOrderVersion=`). Thiếu ⇒ `400 VALIDATION_ERROR` theo field; lệch ⇒ `409 CONCURRENT_MODIFICATION` (đọc lại lệnh).
> Header `Idempotency-Key` tuỳ chọn: gửi lại cùng key + cùng body ⇒ trạng thái hiện tại, không chạy lại; khác body ⇒ `409 IDEMPOTENCY_CONFLICT`.
>
> **Kho cho từng vật tư:** dòng vật tư có `warehouseId` (chọn qua `componentWarehouses: [{itemId, warehouseId}]` lúc tạo/sửa/plan) chỉ giữ
> tồn ở kho đó; giữ tay từ kho khác ⇒ `422 RESOURCE_SCOPE_MISMATCH` field `warehouseId`. `warehouseId: null` = mọi kho của nhà máy (hành vi cũ).

🔴 **Thành công một phần là bình thường, không phải lỗi.** Component nào không còn tồn sẽ bị **bỏ qua
im lặng**. Sau khi gọi, **luôn** gọi tiếp:

**`GET /api/v1/work-orders/{workOrderId}/material-readiness`** (không phân trang)

```jsonc
{
  "workOrderId": "…", "workOrderNo": "WO-001", "status": "PLANNED",
  "ready": true, "canRelease": true,
  "reservedPercent": "100.00", "shortageLineCount": 0,
  "lines": [{
    "componentLineId": "…", "itemId": "…", "itemSku": "RM-01", "itemName": "Bolt",
    "requiredQuantity": "20.000000", "issuedQuantity": "0.000000",
    "reservedQuantity": "20.000000", "shortageQuantity": "0.000000"
  }]
}
```
Dùng `canRelease` để bật/tắt nút Release, `lines[].shortageQuantity` để hiện thiếu bao nhiêu.

> Muốn chỉ định kho/lot cụ thể: `POST /work-orders/{id}/material-reservations`
> body `{componentLineId, warehouseId, lotId?, quantity, expectedWorkOrderVersion}`.
> Huỷ giữ: `DELETE /work-orders/{id}/material-reservations/{reservationId}?expectedWorkOrderVersion=`.
> Huỷ **mọi** phần chưa cấp: `POST /work-orders/{id}/release-reservations` body `{expectedWorkOrderVersion}` (không biến động kho).

---

### Bước 5 — Release

**`POST /api/v1/work-orders/{workOrderId}/release`** body `{"expectedWorkOrderVersion": <version>}` → `200`, status → `RELEASED`,
lịch công đoạn được chốt: `scheduleRevision` (1, 2, … — `null` khi chưa chốt), `calendarRevision`, `scheduleTimeZone`, `scheduleCapturedAt`;
mỗi công đoạn có `workMinutes` (phút làm việc). Xem trước đúng lịch đó **trước** release: `POST /work-orders/{id}/schedule-preview`
(chỉ đọc, body tuỳ chọn `{expectedVersion?, plannedStartAt?, plannedQuantity?}`).

🔴 Nếu reservation **chưa phủ 100%**: trả `409` **và** work order bị đẩy sang trạng thái `BLOCKED`
(được lưu thật vào DB, không rollback). FE phải refetch WO để hiển thị `BLOCKED` + `blockReason`.
Từ `BLOCKED` vẫn **reserve tiếp được** để thoát ra — đó là đường đi đúng.

---

### Bước 6 — Xuất vật tư

**`POST /api/v1/material-issues`** (dạng phẳng, 1 dòng — khớp spec §4.1) → `201`

```jsonc
// Header: Idempotency-Key: ISSUE-<workOrderId>-<uuid>
{ "workOrderId": "…", "reservationId": "…", "quantity": 20, "reason": "…" }
```

> Cần xuất nhiều dòng cùng lúc, hoặc xuất **vượt định mức**: dùng
> `POST /api/v1/work-orders/{workOrderId}/material-issues` với `lines[]` và `overrideReason`.
> 🔴 Component **serial-tracked**: endpoint phẳng (`quantity` bất kỳ) **không dùng được** — mỗi dòng
> serial-tracked phải là `quantity: 1` kèm `serialId` cụ thể, nên phải đi qua `lines[]` với **N dòng**
> cho N đơn vị (mỗi dòng gọi `movementService.issue` một serial riêng).

#### 🔴 G8b (2026-10-19) — phiếu cấp nhiều dòng: version, phiếu bổ sung, huỷ của người tạo

- `POST /work-orders/{W}/material-issues` **bắt buộc** `expectedWorkOrderVersion` (thiếu ⇒ `400` field; lệch ⇒ `409 CONCURRENT_MODIFICATION`).
  Body thêm `supplemental` (tuỳ chọn): `false` + vượt định mức ⇒ **`409 OVER_BOM_APPROVAL_REQUIRED`** field `lines[i].quantity` (không tự thành
  phiếu chờ); `true` ⇒ phiếu bổ sung — không nêu `reservationId`, mỗi dòng cần `reasonCode` + `overrideReason` (5–2000 ký tự), lấy tồn tự do ở
  **mọi** kho `ACTIVE` của nhà máy; vượt định mức thì **cả phiếu** chờ duyệt. Vắng `supplemental` = hành vi cũ (vượt ⇒ chờ duyệt).
- Vượt định mức xét **theo dòng vật tư trên cả phiếu** (đã cấp + mọi dòng của phiếu), không từng dòng.
- Dòng thường không reservation phải lấy đúng kho của dòng vật tư (nếu dòng có kho) ⇒ `422 RESOURCE_SCOPE_MISMATCH` field `lines[i].warehouseId`.
- Phiếu chờ không giữ tồn nhưng nguồn phải đủ tồn tự do lúc tạo ⇒ `409 INSUFFICIENT_AVAILABLE_STOCK` field `lines[i].quantity`.
- Duyệt / từ chối / huỷ: body `{expectedWorkOrderVersion, expectedIssueVersion, reason?}` — **cả hai** version bắt buộc; `Idempotency-Key` tuỳ chọn.
  Từ chối và huỷ cần `reason` 5–2000 ký tự. **Tự duyệt** phiếu của mình cần thêm `PERM_MATERIAL_ISSUE_OVERRIDE` (thiếu ⇒ `403`).
- **Mới:** `POST /work-orders/{W}/material-issues/{I}/cancel` — chỉ người tạo, `PENDING_APPROVAL → CANCELLED`, không biến động kho (người khác ⇒ `403`).
- **Mới:** `GET /material-issues/{I}` → `{issue, decisionContext[]}` (required / gross / batch / `extraRequestedQuantity` chỉ khi chờ duyệt / tồn hiện tại).
- **Mới:** `GET /work-orders/{W}/material-issue-candidates?componentLineId=&mode=NORMAL|SUPPLEMENTAL` — nguồn cấp + số gợi ý.
- `MaterialIssueResponse` thêm `version`, `workOrderVersion`, `supplemental`, `requestedByUserId`, `cancellationReason`.
- Đọc phiếu (list, chi tiết) mở cho `MANAGE` **hoặc** `APPROVE` **hoặc** `OVERRIDE`. List nhà máy thêm `search` + `fromDate`/`toDate` (ngày nhà máy trên `requestedAt`).

#### 🔴 Xuất vượt định mức (Over-BOM) nay là hai bước — `DEC-09`

Trước đây một dòng vượt định mức kèm `overrideReason` **ghi tồn kho ngay**. Nay **không**:

| Bước | Gọi gì | Ai | Kết quả |
|---|---|---|---|
| 1. Đề nghị | `POST /work-orders/{id}/material-issues` với dòng vượt định mức | Operator (`PERM_MATERIAL_ISSUE_MANAGE`) | `201` + `status: "PENDING_APPROVAL"` — **tồn kho không đổi một chữ số nào**, `issuedQuantity` của component **không** tăng |
| 2a. Duyệt | `POST /work-orders/{id}/material-issues/{issueId}/approve` | Manager (`PERM_MATERIAL_ISSUE_APPROVE`) | `200` + `status: "POSTED"` — **đây** mới là lúc movement được ghi, `issuedQuantity` tăng, chi phí được cộng |
| 2b. Từ chối | `POST /work-orders/{id}/material-issues/{issueId}/reject` với `{ "reason": "…" }` | Manager (`PERM_MATERIAL_ISSUE_APPROVE`) | `200` + `status: "REJECTED"` — tồn kho không đổi |

Dòng **trong** định mức vẫn `POSTED` ngay ở bước 1 như cũ — hành vi không đổi.

**Hàng đợi duyệt là filter phía server** (thêm 2026-08-21), không phải lọc trong trình duyệt:

```http
GET /api/v1/material-issues?plantId={…}&status=PENDING_APPROVAL     # cả nhà máy
GET /api/v1/work-orders/{workOrderId}/material-issues?status=PENDING_APPROVAL   # một lệnh sản xuất
```

`status` nhận `PENDING_APPROVAL` · `POSTED` · `REJECTED` · `CANCELLED`; vắng mặt = không lọc. Giá trị
lạ trả `400 VALIDATION_ERROR`.

🔴 **Sắp xếp mặc định đổi từ `postedAt` sang `requestedAt`.** Chứng từ chờ duyệt **chưa có** `postedAt`
(nó `null` tới lúc duyệt), nên sắp xếp hàng đợi theo cột đó không sắp xếp được gì. `requestedAt` có
trên mọi chứng từ.

**`lines[].lotNumber` luôn có mã lô, kể cả khi chứng từ còn chờ duyệt** — backend resolve sẵn bằng
**một** query cho cả trang, FE không cần gọi `GET /inventory/lots/{lotId}` cho từng dòng. Khi dòng
mang cả `lotId` lẫn `lotNumber` mà hai thứ lệch nhau, giá trị trả về là mã của lô ứng với **`lotId`**
— đúng lô mà bước duyệt sẽ xuất kho.

Mỗi dòng nhận thêm 3 field tuỳ chọn phục vụ truy vết rework (`DEC-08`):
`reasonCode` ∈ `REWORK` · `PROCESS_LOSS` · `DAMAGE` · `SETUP_LOSS` · `OTHER`, `reason` (mô tả tự do),
`sourceExecutionId` (phải trỏ tới một Production Execution **có `reworkQuantity > 0` của chính WO đó**,
nếu không → `409 STATE_CONFLICT`).

Xem lịch sử: `GET /api/v1/material-issues?plantId={…}&workOrderId={…}` (workOrderId tuỳ chọn).
`MaterialIssueResponse.code` là số chứng từ dạng `MI-3F2A9C01`.

| Lỗi | Nghĩa |
|---|---|
| `409 RESERVATION_EXCEEDED` | Xuất quá phần còn lại của reservation |
| `409 INSUFFICIENT_AVAILABLE_STOCK` | Kho không đủ (kiểm lại **lúc duyệt**, không chỉ lúc đề nghị) |
| `409 STATE_CONFLICT` | WO không ở `RELEASED`/`IN_PROGRESS`, hoặc chứng từ đã được quyết định rồi |
| `409 OVER_BOM_APPROVAL_REQUIRED` | Nhiều dòng cùng một component cộng lại vượt định mức giữa chừng — gửi lại thành một đề nghị Over-BOM |
| `400 APPROVAL_REASON_REQUIRED` | `reject`/`cancel` thiếu `reason` |
| `400 VALIDATION_ERROR` field `reason` | lý do ngắn hơn 5 hoặc dài hơn 2000 ký tự (G8b) |
| `403 PERMISSION_DENIED` | tự duyệt không có `OVERRIDE`; huỷ phiếu của người khác (G8b) |

---

### Bước 7 — Báo sản lượng (Production Execution)

🔴 **Đây — không phải receipt — mới là thứ làm work order tiến triển và hoàn thành.**

**`GET /api/v1/production-executions/candidates?plantId={…}`** — danh sách WO được phép báo sản lượng.
Chỉ gồm WO `RELEASED`/`IN_PROGRESS` **và** chưa đạt sản lượng kế hoạch.

**`POST /api/v1/work-orders/{workOrderId}/production-executions`** → `201`

```jsonc
// Header: Idempotency-Key: EXEC-<workOrderId>-<uuid>
{
  "goodQuantity": 10,
  "scrapQuantity": 0,
  "reworkQuantity": 0,
  "actualStartedAt": "2026-08-03T08:00:00Z",   // 🔴 BẮT BUỘC
  "actualEndedAt":   "2026-08-03T16:00:00Z",   // 🔴 BẮT BUỘC
  "workOrderOperationId": null,                 // tuỳ chọn, nếu WO có routing
  "notes": "…"
}
```

Response trả sẵn khối tổng để cập nhật màn hình ngay, khỏi refetch:
`workOrderActualGoodQuantity`, `workOrderAvailableToReceipt`, `workOrderRemainingGoodQuantity`,
`workOrderCompletionPercent`, `workOrderStatus`. Bản thân chứng từ có `code` (`PE-…`) và
`status` (**luôn** `"POSTED"`).

| Lỗi | Nghĩa |
|---|---|
| `400 VALIDATION_ERROR` | Thiếu `actualStartedAt`/`actualEndedAt` |
| `409 PLANNED_QUANTITY_EXCEEDED` | Cộng dồn `goodQuantity` vượt `plannedQuantity` |

> Khi cộng dồn good = planned, WO tự chuyển `COMPLETED`. **Nhưng hàng vẫn chưa vào kho** — vẫn phải
> làm tiếp bước 8. `COMPLETED` **không** chặn receipt.

#### 🔴 G8c (2026-10-20) — công đoạn: đầu vào, chuyển giao, retry, vật tư thực dùng, hoàn thành có hụt

WO có quy trình (routing) nay đi **từng công đoạn, tuần tự**:

| Bước | Gọi gì | Ghi chú |
|---|---|---|
| Đọc công đoạn | `GET /work-orders/{W}/operations` | Mỗi công đoạn: `status` (`PENDING`/`READY`/`IN_PROGRESS`/`COMPLETED`), `inputQuantity`, `freshProcessedQuantity`, `freshRemainingQuantity`, `goodQuantity`, `scrapQuantity`, `historicalReworkQuantity`, `pendingReworkQuantity`, `transferredQuantity`/`transferableQuantity` (`null` ở công đoạn cuối), `workOrderVersion` |
| Xác nhận đầu vào | `POST /work-orders/{W}/operations/{O}/input` `{expectedWorkOrderVersion, quantity}` · `Idempotency-Key?` | Công đoạn đầu: cần ≥ 1 phiếu cấp `POSTED` (`409 MATERIAL_ISSUE_REQUIRED`), tổng ≤ kế hoạch. Công đoạn sau: chỉ khi công đoạn trước đã kết thúc (`409 OPERATION_SEQUENCE_CONFLICT`), tối đa `transferableQuantity` của công đoạn trước. Vượt ⇒ `409 PLANNED_QUANTITY_EXCEEDED` field `quantity` |
| Báo sản lượng | `POST /work-orders/{W}/production-executions` | **Bắt buộc** `expectedWorkOrderVersion`; WO có công đoạn **bắt buộc** `workOrderOperationId`. Lượt mới: `good+scrap+rework ≤ freshRemainingQuantity`. Lượt xử lý lại: `sourceExecutionId` (cùng WO + công đoạn, khác ⇒ `422 RESOURCE_SCOPE_MISMATCH`), tối đa `pendingReworkQuantity` của lượt nguồn. `consumption[] {issueLineId, quantity}` (≤ 500, có thể rỗng): dòng cấp `POSTED` của chính WO, mục cùng dòng **cộng lại** rồi so `đã cấp − đã dùng` (vượt ⇒ `409` field `consumption[i].quantity`) — **không** biến động kho |
| Kết thúc công đoạn | `POST /work-orders/{W}/operations/{O}/finish` `{expectedWorkOrderVersion}` | Cần `freshRemaining = 0`, `pendingRework = 0`; công đoạn đầu: đầu vào **đúng bằng** kế hoạch (thiếu thì ghi phế); công đoạn sau: mọi good của công đoạn trước đã chuyển vào. Công đoạn trước kết thúc với good 0 ⇒ công đoạn sau kết thúc được với 0 |
| Hoàn thành thiếu | `POST /work-orders/{W}/complete-production` `{expectedWorkOrderVersion, reason?}` | WO `RELEASED`/`IN_PROGRESS`, mọi công đoạn đã kết thúc, không phiếu chờ. Hụt > 0 cần `reason` 5–2000 **và** `PERM_MATERIAL_ISSUE_APPROVE` (thiếu ⇒ `403`). Không nhập kho / đóng / biến động |

- 🔴 **`actualGoodQuantity` của WO = good của công đoạn CUỐI** (trước G8c: cộng mọi lần báo). Good công đoạn trước là dở dang. Phế/làm lại
  vẫn là tổng mọi công đoạn. Đạt kế hoạch ở công đoạn cuối vẫn tự `COMPLETED`.
- `actualEndedAt` được lệch tối đa **60 giây** sau giờ server; xa hơn ⇒ `400` field `actualEndedAt`.
- Response báo sản lượng thêm `sourceExecutionId`, `processedQuantity`, `retryUsedQuantity`, `pendingReworkQuantity`, `consumption[]`,
  `durationMinutes`, `workOrderVersion`. `WorkOrderResponse` thêm `inputKnown`, `consumptionKnown`, `finalGoodQuantity`,
  `historicalReworkQuantity`, `terminalShortfallQuantity`, `shortfallReason`.
- WO đã báo sản lượng **trước** khi deploy G8c: `inputKnown = false`, đầu vào / cap / `finalGoodQuantity` = `null` (không đoán); đầu vào,
  xử lý lại, vật tư dùng ⇒ `409 PRODUCTION_INPUT_UNKNOWN`; báo sản lượng giữ luật cũ.
- WO **không** có quy trình: không cần đầu vào, `workOrderOperationId` để trống, trần vẫn là kế hoạch.

#### G8d (2026-10-20) — dở dang và hoàn vật tư

| Việc | Gọi gì | Ghi chú |
|---|---|---|
| Dở dang theo nhà máy | `GET /plants/{P}/wip?status&workOrderId&search&fromDate&toDate&page&size` | Không `status` ⇒ `RELEASED`/`IN_PROGRESS`/`COMPLETED`. Mỗi dòng: thành phẩm (`finalGoodQuantity`, `availableToReceipt`, `terminalShortfallQuantity`), phế, làm lại (lịch sử + chờ). `X-Plant-Id` cross-check |
| Dở dang một lệnh | `GET /work-orders/{W}/wip?section=MATERIALS\|OPERATIONS\|REWORK\|AWAITING_RECEIPT` | Bỏ `section` ⇒ mọi phần; phần không hỏi ⇒ `null`. `materials[]` theo **dòng cấp**: `issuedQuantity`, `consumedQuantity`, `returnedQuantity`, `unusedQuantity = cấp − dùng − hoàn`, `returnableQuantity` (= chưa dùng nếu nguồn nhận lại được, `0` nếu không — `ineligibleReason`). `HISTORY` **không** có (⇒ `400`) |
| Ứng viên hoàn | `GET /work-orders/{W}/material-return-candidates?page&size` | Dòng cấp còn chưa dùng, cùng hình dạng `materials[]` |
| Hoàn vật tư | `POST /work-orders/{W}/material-returns` `{expectedWorkOrderVersion, reason, lines[1..500]{sourceIssueLineId, quantity}}` · `Idempotency-Key` **bắt buộc** | WO `IN_PROGRESS`/`COMPLETED`. Hàng về **đúng** kho + lô của dòng cấp (biến động `RECEIVE`, `referenceType = MATERIAL_RETURN`). Mục cùng dòng cộng lại rồi so `cấp − dùng − hoàn` (vượt ⇒ `409 PLANNED_QUANTITY_EXCEEDED` field `lines[i].quantity`). Lô `HOLD`/`REJECTED`/hết hạn ⇒ `409 LOT_NOT_ELIGIBLE`; kho/vật tư ngừng ⇒ `422 RESOURCE_INACTIVE`; dòng cấp của lệnh khác ⇒ `422 RESOURCE_SCOPE_MISMATCH`; WO báo trước G8c ⇒ `409 PRODUCTION_INPUT_UNKNOWN`. Không giảm "đã cấp" của dòng vật tư, không khôi phục giữ tồn |
| Danh sách / chi tiết phiếu hoàn | `GET /material-returns?plantId&workOrderId&search&fromDate&toDate` · `GET /material-returns/{R}` | Mã `MR-…`; ngày theo giờ nhà máy trên `postedAt` |

- Sau khi hoàn, vật tư đã hoàn **không** còn dùng được: trần `consumption[]` của báo sản lượng nay là `cấp − dùng − hoàn`.
- Không trường giá trị nào (giá cấp/dùng/hoàn/dở dang) — chưa làm (quyết định D10).

---

### Bước 8 — Nhập kho thành phẩm (Production Receipt, 3 bước)

**`GET /api/v1/production-receipts/candidates?plantId={…}`** — WO còn hàng chưa nhập kho.
Bao gồm cả `COMPLETED` (khác với candidates ở bước 7). `availableToReceipt` đã **trừ** các receipt
đang mở, dùng trực tiếp làm max của ô nhập.

```
① POST /api/v1/work-orders/{id}/production-receipts              → DRAFT              (chưa đụng tồn kho)
② POST /api/v1/work-orders/{id}/production-receipts/{rid}/submit → PENDING_APPROVAL   (chưa đụng tồn kho)
③ POST /api/v1/work-orders/{id}/production-receipts/{rid}/approve→ APPROVED           (✅ tồn kho tăng, lot = HOLD)
```

Body bước ① (**phẳng, không phải `lines[]`**):
```jsonc
// Header: Idempotency-Key: RECEIPT-<workOrderId>-<uuid>
{
  "destinationWarehouseId": "…",
  "lotNumber": "LOT-20260803-01",   // 🔴 BẮT BUỘC nếu item lotTracked = true
  "serialNumber": "SN-00042",       // 🔴 BẮT BUỘC nếu item serialTracked = true; quantity PHẢI = 1
  "quantity": 10,
  "note": "…",
  "expectedWorkOrderVersion": 4,     // 🔴 G9a: BẮT BUỘC — version lệnh vừa đọc
  "receivedOn": "2026-10-21",        // 🔴 G9a: BẮT BUỘC — ngày nhận, ≤ hôm nay của nhà máy
  "expiresOn": "2027-01-19"          // G9a: tuỳ chọn, chỉ hàng theo lô; lô hết hạn 00:00 ngày đó (giờ nhà máy)
}
```

Từ chối: `POST .../{rid}/reject` body `{"expectedVersion", "expectedWorkOrderVersion", "reason"}` (lý do 5–2000 ký tự).

#### G9a (2026-10-21) — vòng đời đầy đủ, version, hạn mức nhập

- **`destinationWarehouseId` phải là kho đầu ra của lệnh** (có trên ứng viên: `outputWarehouseId`), khác ⇒ `422 RESOURCE_SCOPE_MISMATCH`.
- **Mọi lệnh sau khi tạo** gửi `{"expectedVersion": <version phiếu>, "expectedWorkOrderVersion": <version lệnh>}`:
  `submit`, `approve` (bắt buộc body), `reject`/`withdraw`/`cancel` (+ `reason`), `PATCH` (+ field sửa). Thiếu ⇒ `400` theo field, lệch ⇒
  `409 CONCURRENT_MODIFICATION`. Mỗi lệnh làm version **lệnh sản xuất** tăng đúng 1 — dùng `workOrderVersion` của response cho lệnh kế tiếp.
- **Mới:** `GET .../production-receipts/{rid}` (chi tiết), `GET .../{rid}/events` (lịch sử), `PATCH .../{rid}` (sửa nháp), `POST .../{rid}/withdraw`
  (`PENDING_APPROVAL → DRAFT`), `POST .../{rid}/cancel` (`DRAFT`/`PENDING_APPROVAL → CANCELLED`). Sửa/gửi/rút/huỷ **chỉ người tạo** (người khác ⇒ `403`).
- **Hạn mức trên mọi response:** `actualFinalGoodQuantity` (G), `receiptableGoodCap` (C = min(kế hoạch, G)), `approvedReceiptQuantity` (A),
  `claimedQuantity` (D: nháp + chờ duyệt), `awaitingReceiptQuantity` (W = C − A), `unclaimedReceiptQuantity` (U = C − A − D = max của ô nhập).
  Nút: `allowedActions`.
- **Bộ lọc list:** `workOrderId`, `destinationWarehouseId`, `receivedFrom`/`receivedTo` (ngày nhà máy, gồm hai đầu), `search` (mã phiếu, mã lệnh,
  SKU/tên, số lô). Ứng viên: `workOrderId`, `search`.
- **Quyền đọc:** `PERM_PRODUCTION_RECEIPT_READ` (mới) / `_MANAGE` / `_APPROVE` đọc mọi phiếu; `PERM_PRODUCTION_QC_READ` (mới) /
  `PERM_QUALITY_DISPOSITION` chỉ đọc phiếu `APPROVED`.
- Mã lô trùng một lô đã có (**không phân biệt hoa thường**) ⇒ `409 LOT_CODE_ALREADY_EXISTS` field `lotNumber`. Số lẻ thứ 7 ⇒ `400`.

#### G9b (2026-10-23) — phiếu lấy từ lượt báo nào, và nguồn của phiếu

- **Server tự phân bổ** — không gửi gì thêm. Lúc tạo phiếu và mỗi lần sửa nháp, BE lấy lượng từ các lượt báo good của **công đoạn cuối**,
  lượt cũ nhất trước, không lượt nào bị giữ quá good của chính nó.
- **Mọi response phiếu nhập** có thêm `outputEvidenceKnown` (boolean), `outputUnknownReason` (chuỗi | `null`), `contributions[]`
  (`executionId`, `executionCode`, `operationId`, `operationSequence`, `quantity`). Phiếu tạo trước bản này: `outputEvidenceKnown = false`,
  `contributions = null` — hiển thị "không rõ nguồn", **đừng** hiểu là 0.
- **Mới:** `GET /v1/work-orders/{W}/production-receipts/{R}/source` — `contributions[]` như trên + `materialAttribution`
  (`WORK_ORDER_LEVEL` | `UNKNOWN`) + `materials[]` (vật tư của cả lệnh: cấp / dùng / hoàn / còn lại theo dòng cấp, cùng hình dạng phần vật tư
  của `GET /v1/work-orders/{W}/wip`). Quyền như chi tiết phiếu.

#### G9c (2026-10-23) — QC theo lượng, nhiều lượt

- **Mới:** `POST /v1/work-orders/{W}/production-receipts/{R}/qc-decisions` (`Idempotency-Key` tuỳ chọn) body
  `{"expectedVersion", "expectedWorkOrderVersion", "acceptedQuantity", "rejectedQuantity", "reason"}` — cả hai lượng bắt buộc (0 được),
  tổng > 0 và ≤ phần chờ (`409 PLANNED_QUANTITY_EXCEEDED` field `acceptedQuantity`); có loại ⇒ lý do 5–2000 (thiếu ⇒
  `400 APPROVAL_REASON_REQUIRED`). Response `{"receipt": <phiếu như chi tiết>, "qcDecision": {…, "outputLots": [...]}}`.
- **Hàng theo lô:** phần đạt sang lô con `<lô>-A<n>` (`AVAILABLE`), phần loại sang `<lô>-R<n>` (`REJECTED`), phần chờ ở lại lô gốc `HOLD`.
  Lô hết hạn chỉ được **loại** (đạt ⇒ `409 LOT_NOT_ELIGIBLE` field `acceptedQuantity`). **Hàng không theo lô:** phần đạt rời vùng chờ, phần loại
  ở lại vùng chờ (màn hình tồn cộng chung "chờ kiểm" + "đã loại").
- **Mới:** `GET …/{R}/qc-decisions` — lịch sử quyết định, mới nhất trước, bất biến.
- **Mọi response phiếu nhập** thêm `qualityState` (`NOT_STARTED`/`PARTIAL`/`ACCEPTED`/`REJECTED`/`MIXED`, `null` khi chưa duyệt),
  `acceptedQuantity`/`rejectedQuantity`/`pendingQcQuantity`, `valuationStatus = "PENDING_COSTING"` (chưa có giá trị — đừng hiển thị 0).
  `qcResult` chỉ còn là tóm tắt: có giá trị khi hết chờ và toàn đạt/toàn loại, `null` khi `PARTIAL`/`MIXED`.
- **`qc-disposition` cũ vẫn chạy:** quyết toàn bộ **phần còn chờ** một phía, ghi vào cùng sổ. Hết chờ ⇒ `409 STATE_CONFLICT`.
- **Bộ lọc list:** `qualityState`, `pendingQc` (đã duyệt + còn chờ — hàng đợi QC), `hasQcDecision` (đã có quyết định — lịch sử, gồm cả phiếu kiểm dở).

🔴 **Sau `approve`, hàng đã vào kho nhưng CHƯA dùng được**: lot ở trạng thái `HOLD`, `onHand` tăng
nhưng `available` **vẫn = 0**. Màn hình tồn kho phải hiển thị đúng điều này.

🔴 **Item serial-tracked là NGOẠI LỆ của quy tắc trên (P5, 2026-08-06)**: hàng khả dụng **ngay** sau
`approve`, không có bước `HOLD` chờ QC (quyết định có chủ đích, đánh đổi lấy việc không phải viết lại
toàn bộ tầng aggregate tồn kho). QC vẫn chạy được ở bước 9 nhưng chỉ ghi nhận verdict; `REJECTED` sẽ
rút hàng ra khỏi tồn khả dụng thay vì đóng một lot lại. Vì mỗi đơn vị serial là 1 unit, nhận N đơn vị
= gửi **N request** riêng (không có `serialNumbers[]`).

| Lỗi | Nghĩa |
|---|---|
| `400 LOT_REQUIRED` | Item lot-tracked mà không gửi `lotNumber` |
| `400 SERIAL_REQUIRED` | Item serial-tracked mà không gửi `serialNumber` |
| `409 PLANNED_QUANTITY_EXCEEDED` | Nhập vượt `availableToReceipt` / `unclaimedReceiptQuantity` (đã trừ receipt đang mở); duyệt mà `A + q > C` |
| `409 LOT_NOT_ELIGIBLE` field `expiresOn` | G9a: hạn lô đã qua kể từ lúc lưu nháp (gửi/duyệt) |
| `422 RESOURCE_SCOPE_MISMATCH` | G9a: kho nhập không phải kho đầu ra của lệnh |
| `409 STATE_CONFLICT` | Sai trạng thái receipt (vd approve một receipt đang `DRAFT`) |

---

### Bước 9 — QC disposition (mắt xích cuối)

**`POST /api/v1/work-orders/{id}/production-receipts/{rid}/qc-disposition`** → `200`

```jsonc
{ "result": "AVAILABLE", "reason": "Đạt kiểm tra ngoại quan" }   // hoặc "REJECTED"
```

| Kết quả | Tác động |
|---|---|
| `AVAILABLE` | Lot `HOLD` → `AVAILABLE`; với `NON_TRACKED`, giải phóng `qualityHoldQuantity`. Hàng dùng được và **Sales Order được fulfill** |
| `REJECTED` | Lot → `REJECTED`; với `NON_TRACKED`, giữ nguyên on-hand và `qualityHoldQuantity`. **Không** fulfill |

- Receipt **vẫn giữ** `status = "APPROVED"` sau QC; kết quả nằm ở `qcResult`/`qcReason`/`qcAt`.
- Với `NON_TRACKED`, `outputLotStatus = null` là đúng vì không có lot. FE hiển thị chờ QC khi
  `status = "APPROVED" && qcResult == null`; tồn bị giữ được công bố ở
  `GET /inventory/balances` qua `qualityHoldQuantity` và đã bị trừ khỏi `availableQuantity`.
- Chỉ QC được **một lần** — gọi lần hai → `409 STATE_CONFLICT`.
- `reason` **bắt buộc** cho cả hai kết quả.

---

### Bước 10 — Hiển thị kết quả

| Muốn xem | Endpoint |
|---|---|
| WO đầy đủ (kèm `componentLines`, `operations`, `allocations`) | `GET /api/v1/work-orders/{id}` |
| Tồn kho | `GET /api/v1/inventory/balances?warehouseId={…}&itemId={…}` (`warehouseId` **bắt buộc**, `itemId` tuỳ chọn) |
| Đơn hàng đã giao tới đâu | `GET /api/sales-orders/v1/{id}` → `lines[].fulfilledQuantity` |
| Chênh lệch kế hoạch/thực tế | `GET /api/v1/work-orders/{id}/variance` |

> ✅ **[`C2-4`, 2026-08-05] `variance` nay trả đủ 4 khối:** `materialLines` + `outputVariance` +
> `wipSummary` (scrap/rework) + **`timeVariance`** (`plannedMinutes`/`actualMinutes`/`varianceMinutes`).
> `plannedMinutes` = `Σ (setupMinutes + runMinutesPerUnit × plannedQuantity)` trên mọi operation của
> routing snapshot; `actualMinutes` = `Σ` thời lượng mọi production execution **đã kết thúc**
> (`actualEndedAt` khác `null`) — execution đang chạy dở không tính vào tổng.

> ✅ **[`P3`, 2026-08-05] `variance` nay trả thêm khối thứ 5, `costVariance`, và mỗi dòng
> `materialLines[]` có thêm `usageVarianceCost`.** Cần `PERM_COSTING_READ` **hoặc**
> `PERM_WORK_ORDER_VARIANCE_READ` — thực tế cả hai đều ADMIN+MANAGER only nên OPERATOR không thấy
> field tiền, chỉ thấy field số lượng như trước. Chỉ làm **Material Usage Variance**, **không** có
> Material Price Variance (không có dữ liệu giá trên ledger xuất kho để tính).
>
> ```jsonc
> {
>   "materialLines": [{
>     "componentItemCode": "RM-001", "plannedQuantity": 6, "actualIssuedQuantity": 4,
>     "varianceQuantity": -2, "status": "UNDER_ISSUED",
>     "usageVarianceCost": -10.0   // varianceQuantity × standard unit cost của component (rolled-up)
>   }],
>   "costVariance": {
>     "standardMaterialCost": 30.0, "standardLaborCost": 9.0, "standardOverheadCost": 3.0,
>     "standardTotalCost": 42.0,     // CostingService roll-up của product item × plannedQuantity
>     "actualMaterialCost": 20.0, "actualLaborCost": 6.0, "actualOverheadCost": 2.0,
>     "actualTotalCost": 28.0,       // tích luỹ từ MaterialIssue + ProductionExecution thực tế
>     "totalCostVariance": -14.0     // actualTotalCost − standardTotalCost
>   }
> }
> ```
>
> `actual*` = 0 với WO chưa từng issue vật tư/report sản lượng — không phải lỗi.

---

## 5. Tham chiếu endpoint theo màn hình

> **Đường dẫn trong mục này viết tắt.** Mặc định thêm `/api/v1` phía trước (`/work-orders/{id}` →
> `/api/v1/work-orders/{id}`). Riêng 5 nhóm `/auth`, `/admin/users`, `/users`, `/access`, `/sales-orders` thì `/v1` đứng
> **sau** tên nhóm: `/auth/login` → `/api/auth/v1/login`, `/sales-orders/{id}` → `/api/sales-orders/v1/{id}`.

### Auth
| Việc | Endpoint | Quyền |
|---|---|---|
| Login | `POST /auth/login` | permitAll |
| Refresh token | `POST /auth/refresh` | permitAll |
| Logout / logout tất cả thiết bị | `POST /auth/logout` · `/auth/logout-all` | đã đăng nhập |
| Profile + permissions + scope hiện tại | `GET /auth/me` | đã đăng nhập (không cần `PERM_*` cụ thể) |
| Quên mật khẩu (`D8c`) | `POST /auth/forgot-password` — body `{email}` | permitAll |
| Đặt lại mật khẩu (`D8c`) | `POST /auth/reset-password` — body `{token, newPassword}` | permitAll |
| Admin mở khoá tài khoản (`D8c`) | `PATCH /admin/users/{userId}/unlock` — không body | role `ADMIN` |

### Planning
| Việc | Endpoint | Quyền |
|---|---|---|
| Danh sách demand chọn được | `GET /sales-orders/planning-demands?plantId=&horizonEnd=` | `PERM_MRP_RUN` |
| Chạy MRP | `POST /planning-runs` | `PERM_MRP_RUN` |
| Lịch sử run | `GET /planning-runs?companyId=&plantId=&warehouseId=&status=` | `PERM_MRP_READ` |
| Chi tiết run | `GET /planning-runs/{runId}` | `PERM_MRP_READ` |
| Bảng netting | `GET /planning-runs/{runId}/requirements` | `PERM_MRP_READ` |
| Bảng đề xuất | `GET /planning-runs/{runId}/suggestions` | `PERM_MRP_READ` |
| Duyệt / từ chối | `POST /supply-suggestions/{id}/approve` · `/reject` | `PERM_SUPPLY_SUGGESTION_MANAGE` |
| → Work Order | `POST /supply-suggestions/{id}/convert-to-work-order` | `PERM_SUPPLY_SUGGESTION_MANAGE` |
| → Purchase Requisition | `POST /supply-suggestions/{id}/convert-to-purchase-requisition` | `PERM_PURCHASE_REQUISITION_MANAGE` |

### Work Order
| Việc | Endpoint | Quyền |
|---|---|---|
| Danh sách (lọc `status`, `productItemId`, `search`, `fromDate`/`toDate` theo ngày nhà máy trên `createdAt`) | `GET /plants/{plantId}/work-orders` | `PERM_WORK_ORDER_READ` |
| Chi tiết | `GET /work-orders/{id}` | `PERM_WORK_ORDER_READ` |
| Tạo thủ công | `POST /plants/{plantId}/work-orders` | `PERM_WORK_ORDER_MANAGE` |
| Sửa (chỉ khi DRAFT, **bắt buộc** `expectedVersion`; lệnh MAKE khoá `plannedQuantity`/`outputWarehouseId` ⇒ `409 SOURCE_LOCKED`) | `PATCH /work-orders/{id}` | `PERM_WORK_ORDER_MANAGE` |
| Xem trước lịch (chỉ đọc) | `POST /work-orders/{id}/schedule-preview` | `PERM_WORK_ORDER_READ` |
| Lên lịch (`{expectedWorkOrderVersion, componentWarehouses?}`) | `POST /work-orders/{id}/plan` | `PERM_WORK_ORDER_MANAGE` |
| Release (`{expectedWorkOrderVersion}`) | `POST /work-orders/{id}/release` | `PERM_WORK_ORDER_MANAGE` |
| Huỷ (**bắt buộc** `{reason, expectedWorkOrderVersion}`; còn phiếu cấp chờ duyệt ⇒ `409`; lệnh MAKE trả hạn ngạch chuyển đổi một lần) | `POST /work-orders/{id}/cancel` | `PERM_WORK_ORDER_MANAGE` |
| Giải phóng mọi phần chưa cấp (`{expectedWorkOrderVersion}`) | `POST /work-orders/{id}/release-reservations` | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Đóng/chốt sổ (chỉ từ `COMPLETED`, **không** body) | `POST /work-orders/{id}/close` | `PERM_WORK_ORDER_MANAGE` |
| Sẵn sàng vật tư | `GET /work-orders/{id}/material-readiness` | `PERM_WORK_ORDER_READ` |
| Variance | `GET /work-orders/{id}/variance` | `PERM_WORK_ORDER_VARIANCE_READ` |

> 🔴 **`close` khoá vĩnh viễn (P6).** Một work order `CLOSED` không còn nhận reserve/issue/receipt/
> adjust nào nữa — không có ngoại lệ đọc/ghi nào khác. `close` cũng giải phóng mọi reservation
> `ACTIVE` còn sót (component bị đặt trước nhưng chưa issue) về lại tồn khả dụng — đây là bước
> "Reconcile" trước khi khoá. `WorkOrderResponse.closedAt` (`null` cho tới khi `CLOSED`) cho biết khi
> nào việc này xảy ra.

`search` là free-text trên **số WO + SKU thành phẩm**.

### Reservation / Issue / Execution / Receipt
| Việc | Endpoint | Quyền |
|---|---|---|
| Auto reserve (FEFO) | `POST /work-orders/{id}/reserve` | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Reserve thủ công | `POST /work-orders/{id}/material-reservations` | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Danh sách reservation | `GET /work-orders/{id}/material-reservations` | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Huỷ reservation | `DELETE /work-orders/{id}/material-reservations/{rid}` | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Xuất 1 dòng | `POST /material-issues` | `PERM_MATERIAL_ISSUE_MANAGE` |
| Xuất nhiều dòng / đề nghị Over-BOM | `POST /work-orders/{id}/material-issues` | `PERM_MATERIAL_ISSUE_MANAGE` |
| Duyệt / từ chối đề nghị Over-BOM | `POST /work-orders/{id}/material-issues/{issueId}/approve\|reject` | **`PERM_MATERIAL_ISSUE_APPROVE`** (ADMIN + MANAGER) |
| Huỷ đề nghị của chính mình (G8b) | `POST /work-orders/{id}/material-issues/{issueId}/cancel` | `PERM_MATERIAL_ISSUE_MANAGE` (chỉ người tạo) |
| Lịch sử xuất (plant) / chi tiết phiếu | `GET /material-issues?plantId=&workOrderId=&search=&fromDate=&toDate=` · `GET /material-issues/{issueId}` | `MANAGE` **hoặc** `APPROVE` **hoặc** `OVERRIDE` (G8b) |
| Ứng viên nguồn cấp (G8b) | `GET /work-orders/{id}/material-issue-candidates` | `PERM_MATERIAL_ISSUE_MANAGE` |
| Candidate báo sản lượng | `GET /production-executions/candidates?plantId=` | `PERM_PRODUCTION_EXECUTION_READ` |
| Báo sản lượng | `POST /work-orders/{id}/production-executions` | `PERM_PRODUCTION_EXECUTION_MANAGE` |
| Công đoạn: đọc (G8c) | `GET /work-orders/{id}/operations` | `PERM_PRODUCTION_EXECUTION_READ` **hoặc** `_MANAGE` |
| Công đoạn: đầu vào / kết thúc (G8c) | `POST /work-orders/{id}/operations/{opId}/input` · `/finish` | `PERM_PRODUCTION_EXECUTION_MANAGE` |
| Hoàn thành sản xuất (G8c) | `POST /work-orders/{id}/complete-production` | `PERM_WORK_ORDER_MANAGE` (+ `PERM_MATERIAL_ISSUE_APPROVE` khi hụt) |
| Dở dang (G8d) | `GET /plants/{plantId}/wip` · `GET /work-orders/{id}/wip` | `PERM_WIP_READ` |
| Hoàn vật tư: tạo / ứng viên (G8d) | `POST /work-orders/{id}/material-returns` · `GET /work-orders/{id}/material-return-candidates` | `PERM_MATERIAL_RETURN_MANAGE` |
| Hoàn vật tư: đọc (G8d) | `GET /material-returns` · `GET /material-returns/{id}` | `PERM_MATERIAL_RETURN_READ` **hoặc** `_MANAGE` |
| Lịch sử báo sản lượng | `GET /production-executions?workOrderId=` | `PERM_PRODUCTION_EXECUTION_READ` |
| Candidate nhập kho | `GET /production-receipts/candidates?plantId=` | `PERM_PRODUCTION_RECEIPT_READ` / `_MANAGE` / `_APPROVE` |
| Tạo/sửa/submit/rút/huỷ receipt (G9a) | `POST /work-orders/{id}/production-receipts` · `PATCH …/{rid}` · `POST …/{rid}/submit` · `/withdraw` · `/cancel` | `PERM_PRODUCTION_RECEIPT_MANAGE` (sửa/gửi/rút/huỷ: chỉ người tạo) |
| Chi tiết / lịch sử receipt (G9a) | `GET …/{rid}` · `GET …/{rid}/events` | như danh sách |
| Nguồn của receipt (G9b) | `GET …/{rid}/source` | như danh sách |
| QC theo lượng (G9c) | `POST …/{rid}/qc-decisions` | `PERM_QUALITY_DISPOSITION` |
| Lịch sử QC (G9c) | `GET …/{rid}/qc-decisions` | như danh sách |
| Duyệt/từ chối receipt | `POST …/{rid}/approve` · `/reject` | `PERM_PRODUCTION_RECEIPT_APPROVE` |
| QC | `POST …/{rid}/qc-disposition` | `PERM_QUALITY_DISPOSITION` |
| Danh sách receipt (plant) | `GET /production-receipts?plantId=&status=&workOrderId=&destinationWarehouseId=&receivedFrom=&receivedTo=&search=` | `PERM_PRODUCTION_RECEIPT_READ` / `_MANAGE` / `_APPROVE`; `PERM_PRODUCTION_QC_READ` / `PERM_QUALITY_DISPOSITION` chỉ thấy `APPROVED` |

> Nhận `403 PERMISSION_DENIED` nghĩa là **role của user đang đăng nhập** thiếu quyền đó, không phải
> API sai. Vai trò: `ADMIN` (cấu hình hệ thống) · `MANAGER` (duyệt, lập kế hoạch) · `OPERATOR` (thực
> thi: xuất vật tư, báo sản lượng, tạo receipt — **không** duyệt receipt, **không** QC).

### Sales Order · Inventory · Master data
| Việc | Endpoint |
|---|---|
| Danh sách đơn | `GET /sales-orders?companyId=&plantId=&status=` |
| Tạo / xác nhận / huỷ | `POST /sales-orders` · `/{id}/confirm` · `/{id}/cancel` |
| Sửa (chỉ khi `DRAFT`) | `PATCH /sales-orders/{id}` — full-replace `lines[]`, `expectedVersion` bắt buộc (`C2-4`) |
| Plant / Warehouse / Item: activate | `POST /plants/{id}/activate` · `POST /warehouses/{id}/activate` · `POST /items/{id}/activate` *(mới, 2026-08-06)* |

> 🔴 **[2026-08-06] `SalesOrderResponse` giờ mang `version`.** Trước đây `PATCH /sales-orders/{id}`
> đòi `expectedVersion` bắt buộc nhưng không endpoint nào trả `version` lại cho FE — không có cách
> hợp lệ để lấy giá trị phải gửi. Nay **mọi** response Sales Order (`create`, `get`, `list`, `update`,
> `confirm`, `cancel`) đều có field `version` (số nguyên, tăng dần từ `0`). Luồng đúng: lấy `version`
> từ response gần nhất (`GET` hoặc bất kỳ action nào) → gửi lại nguyên giá trị đó làm `expectedVersion`
> trên `PATCH` tiếp theo → response của `PATCH` trả `version` **mới** (đã +1) → dùng cho lần sau. Lệch
> `expectedVersion` (đơn đã bị người khác sửa) trả `409 CONCURRENT_MODIFICATION`.
>
> 🔴 **[Sửa 2026-08-10] `PATCH /sales-orders/{id}` kèm `lines[]` trước đây LUÔN trả
> `RESOURCE_ALREADY_EXISTS` ("Data constraint violation") — nay chạy đúng.** Hai điều FE cần biết:
> (1) Full replace là **thay toàn bộ**: mọi dòng cũ bị xoá, dòng mới nhận `salesOrderLineId` **mới** và
> `lineNo` đánh lại `1..N` theo đúng thứ tự phần tử trong `lines[]` — **đừng** cache
> `salesOrderLineId` qua một lần `PATCH`. FE **không** gửi `lineNo` hay id dòng (server cấp, `B47`).
> (2) `version` nay bump **đúng một lần** cho **mọi** `PATCH` thành công, kể cả `PATCH` chỉ có `lines[]`
> (trước đây trường hợp đó không bump — hai người sửa dòng đồng thời sẽ ghi đè nhau im lặng).
> `Idempotency-Key` **không** có tác dụng trên endpoint này (chỉ POST ghi tồn kho dùng nó); chống áp
> dụng hai lần đã có sẵn nhờ `expectedVersion` — gửi lại **cùng** payload lần hai trả
> `409 CONCURRENT_MODIFICATION`, không phải 200 với kết quả cũ.

| Tồn kho | `GET /inventory/balances?warehouseId=` (bắt buộc) `&itemId=` (tuỳ chọn) |
| Lịch sử movement | `GET /inventory/movements?warehouseId=` (**bắt buộc**) `&itemId=&lotId=` (tuỳ chọn) |

> 🔴 **[2026-08-14 — ĐỔI HÀNH VI] Dòng của lot `HOLD`/`REJECTED`/`EXPIRED` nay trả
> `availableQuantity = 0`.** Áp dụng cho `GET /inventory/balances`, `GET /inventory/lots` và
> `GET /inventory/lots/{lotId}` (mảng `balances[]`). Trước đó ba endpoint này báo số dương cho hàng mà
> `B3` cấm xuất/giữ chỗ — tức FE hiển thị hàng dùng được trong khi MRP và dashboard (đọc qua aggregate
> có lọc lot status) báo `0` cho **đúng lô hàng đó**. Đây là **sửa sai**, nhưng nếu FE đang hiển thị
> con số cũ thì màn hình sẽ đổi.
>
> - `quantity`/`onHandQuantity` **giữ nguyên số thật** — hàng vẫn tồn tại vật lý, chỉ là chưa dùng
>   được. Đừng suy ra "hết hàng"; lý do nằm ở `status` của lot.
> - `qualityHoldQuantity` **không** đổi: nó là số hàng chờ QC của tồn **không** lot-tracked. Với lot
>   thì lý do giữ hàng đọc ở `lot.status`, không phải ở cột này. Nói cách khác, đẳng thức
>   `available = quantity − reserved − qualityHold` chỉ đúng cho dòng có lot `AVAILABLE` hoặc dòng
>   không lot; dòng lot bị giữ thì `available = 0` bất kể ba số kia.
> - Loại trừ dựa trên **`lot.status`**, **không** dựa trên `expiresAt`: một lot đã quá hạn nhưng vẫn
>   `AVAILABLE` thì **vẫn tính là available**. Hệ thống chưa có job tự đẩy lot quá hạn sang `EXPIRED` —
>   muốn chặn theo ngày thì đó là yêu cầu riêng.
| Nhập/xuất/điều chỉnh thủ công | `POST /inventory/receive` · `/issue` · `/adjust` |
| Dashboard tồn kho | `GET /reports/inventory-dashboard?scopeType=&scopeId=` (**cả hai bắt buộc**) `&lowStockLimit=&movementLimit=` (tuỳ chọn) |
| Cảnh báo tồn thấp | `GET /reports/low-stock?scopeType=&scopeId=` (**cả hai bắt buộc**) `&status=` (tuỳ chọn) |

> 🔴 **[2026-08-14] Dashboard là MỘT request duy nhất — không phải gọi thêm Item/Warehouse/User để
> lấy nhãn.** `scopeType` ∈ `COMPANY | PLANT | WAREHOUSE`, `scopeId` là id tương ứng; quyền là
> `PERM_INVENTORY_READ` **trên đúng scope đó** (scope ngoài phạm vi assignment ⇒ `403
> PERMISSION_DENIED`, scope không tồn tại ⇒ `404 ENTITY_NOT_FOUND`). Những gì response trả:
>
> - `generatedAt` — thời điểm server tính snapshot (ISO-8601 UTC). Không cache, mọi con số đọc live.
> - **Đếm theo dòng `(item, warehouse)`**, không theo item: ngưỡng cấu hình theo kho nên cùng một item
>   ở hai kho là **hai** dòng cảnh báo độc lập. `okCount + lowStockCount + reorderNeededCount` = số
>   setting `ACTIVE` trong scope. `totalItemCount` = số item **có setting `ACTIVE`** trong scope (không
>   phải số item của Company). `totalWarehouseCount` **có tính** kho `INACTIVE` — hàng trong kho đã
>   ngừng hoạt động vẫn là hàng có thật.
> - Mỗi dòng cảnh báo có `uomCode`, `onHandQuantity`, `reservedQuantity`, `qualityHoldQuantity`,
>   `availableQuantity`, `shortageQuantity`. Đẳng thức: `available = onHand − reserved − qualityHold`,
>   trong đó `onHand` **đã loại** lot không `AVAILABLE` (`HOLD`/`REJECTED`/`EXPIRED` không đóng góp
>   một đơn vị nào, kể cả vào `onHand`). Không bao giờ âm — DB có CHECK constraint ép
>   `reserved + qualityHold <= quantity`.
> - `shortageQuantity = max(0, max(safetyStock, reorderPoint) − available)` — số cần bổ sung để dòng
>   về `OK`. **Không** phải chỉ khoảng cách tới reorder point: trong repo này `safetyStock ≥
>   reorderPoint` là cấu hình thường gặp, dùng reorder point một mình thì mọi dòng `LOW_STOCK` báo 0.
> - Trạng thái loại trừ nhau: `available <= reorderPoint` ⇒ `REORDER_NEEDED`; ngược lại
>   `available < safetyStock` ⇒ `LOW_STOCK`; còn lại `OK`. `REORDER_NEEDED` nặng hơn `LOW_STOCK`.
> - `topLowStockLines` sắp xếp **nặng trước**: `REORDER_NEEDED` → shortage giảm dần → `itemCode` →
>   `warehouseCode`; **không** chứa dòng `OK`. `recentMovements` sắp xếp `createdAt desc`, hoà thì
>   `movementId desc` (thứ tự ổn định giữa hai lần gọi).
> - `lowStockLimit` / `movementLimit`: mặc định **10**, kẹp về khoảng `[1, 20]` (giá trị ngoài khoảng
>   **không** trả lỗi, chỉ bị kẹp — giống quy tắc `size` của phân trang).
> - `recentMovements[]` là DTO riêng của dashboard: có `itemCode`/`itemName`/`uomCode`/
>   `warehouseCode`/`warehouseName`/`actorUserId`/`actorUsername`, **không** có `idempotencyKey`.
>   `GET /inventory/movements` giữ nguyên hình dạng cũ, không đổi.
> - `referenceType`/`referenceId` là **id chứng từ nguồn, không phải số chứng từ nghiệp vụ** — chưa có
>   `referenceNo`. `uomCode` lấy từ `items.unit` (text tự do, chưa nối FK sang UOM master).
>
> `GET /reports/low-stock` dùng **cùng** hình dạng dòng và **cùng** thứ tự sắp xếp, chỉ khác là giữ cả
> dòng `OK` và trả **mảng trần** (không phân trang — một dòng cho mỗi ngưỡng đã cấu hình).
| UOM (đơn vị tính, **global**) | `GET /uoms` · `POST /uoms` · `GET/PATCH /uoms/{id}` · `POST /uoms/{id}/activate` · `POST /uoms/{id}/deactivate` |

> 🔴 **UOM không có `companyId`/`plantId`** — danh mục chung cho toàn hệ thống, không lọc theo company.
> `code` **không sửa được sau khi tạo** — `PATCH /uoms/{id}` chỉ nhận `name`/`description`, gửi `code`
> lên sẽ bị bỏ qua (không phải lỗi, server không đọc field đó). Trùng `code` trả `409
> RESOURCE_ALREADY_EXISTS`. Item vẫn chưa gắn `uomId` (`items.unit` còn là text tự do) — UOM hiện là
> danh mục độc lập, chưa dùng để validate item.

| BOM | `GET /companies/{companyId}/boms` · `GET /boms/{bomId}/tree` · `GET /items/{itemId}/active-bom` |
| BOM activate / **deactivate** | `POST /boms/{bomId}/activate` · **`DELETE /boms/{bomId}`** ⇐ đây **là** deactivate |
| Routing | `GET /companies/{companyId}/routings` · `GET /routings/{routingId}` |
| Routing activate / **deactivate** | `POST /routings/{routingId}/activate` · **`DELETE /routings/{routingId}`** ⇐ đây **là** deactivate |

> 🔴 **`DELETE` ở đây là deactivate, KHÔNG phải xoá.** Rule `C6` cấm hard-delete chứng từ nghiệp vụ,
> nên phần lớn endpoint deactivate của repo dùng verb `DELETE` và trả `200` + `{"result": null}` —
> **không** có `POST /…/deactivate` cho `companies`, `plants`, `warehouses`, `items`, `bom-lines`,
> `material-reservations`, BOM, Routing. Deactivate BOM/Routing **không** đổi snapshot của work
> order đã tạo (bất biến `B12`/`B49`); nhưng khi item không còn routing `ACTIVE` thì convert proposal
> MAKE sẽ bị từ chối bằng `409 MISSING_ROUTING`.
>
> 🔴 **[2026-08-06] `plants`, `warehouses`, `items` giờ là NGOẠI LỆ có `POST .../activate` — nhưng
> vẫn KHÔNG có `POST .../deactivate`, chỉ `DELETE`.** Trước đây ba resource này chỉ đi được một
> chiều (`ACTIVE` → `DELETE` → `INACTIVE`, không có đường quay lại); FE báo thiếu và backend đã thêm
> `POST /plants/{id}/activate`, `POST /warehouses/{id}/activate`, `POST /items/{id}/activate` (không
> phải `companies` — chưa ai yêu cầu, vẫn chỉ có `DELETE`). Cả ba **idempotent** (gọi trên record đã
> `ACTIVE` là no-op `200`) và **chặn nếu "cha" đang `INACTIVE`**: activate Warehouse khi Plant của nó
> `INACTIVE` ⇒ `422 OPERATION_NOT_ALLOWED`; activate Plant hoặc Item khi Company `INACTIVE` ⇒ cùng mã
> lỗi. FE nên vô hiệu hoá nút "Activate" của Warehouse/Plant/Item khi cha đang hiển thị `INACTIVE`,
> thay vì chỉ phát hiện qua lỗi `422` sau khi bấm. Verb vẫn **lệch cặp** có chủ đích (`activate` =
> `POST`, `deactivate` = `DELETE`) — đây là asymmetry thật của API, không phải tài liệu thiếu.

### Work Center *(`C2-6`, 2026-08-05)*

| Việc | Endpoint |
|---|---|
| Tạo / danh sách theo plant | `POST /plants/{plantId}/work-centers` · `GET /plants/{plantId}/work-centers?status=` |
| Xem / sửa | `GET /work-centers/{id}` · `PATCH /work-centers/{id}` (chỉ `name`/`description`/`capacityUnitType`/`capacityUnits`) |
| Activate / deactivate / **delete** | `POST /work-centers/{id}/activate` · `POST /work-centers/{id}/deactivate` · **`DELETE /work-centers/{id}`** ⇐ gọi **cùng** hành vi với `deactivate` |

> 🔴 **Work Center là ngoại lệ duy nhất có CẢ BA verb** (`POST .../activate`, `POST .../deactivate`
> **và** `DELETE` cùng nghĩa deactivate) — khác Routing/BOM/UOM ở trên chỉ có `DELETE` (không có
> `POST .../deactivate`) và Access Control chỉ có `POST .../deactivate` (không có `DELETE`). Ba pattern
> khác nhau tồn tại song song trong cùng API vì mỗi nhóm đi theo đúng verb FE liệt kê tường minh lúc
> đặc tả — **đừng "đồng bộ hoá" chúng lại với nhau**.
>
> 🔴 **Breaking change trên Routing:** `RoutingOperationRequest.workCenterCode` (text tự do) đã đổi
> thành `workCenterId` (UUID, phải trỏ một Work Center có thật, cùng plant với mọi operation khác
> trong cùng routing — trộn Work Center của hai plant khác nhau trả `422 OPERATION_NOT_ALLOWED`).
> Client cũ gửi text tự do cho field này **sẽ hỏng** — phải tạo Work Center trước rồi lấy
> `workCenterId`. `RoutingOperationResponse` vẫn trả **cả** `workCenterId` **và** `workCenterCode`
> (resolve qua join, không phải cột riêng) để FE hiện được cả id lẫn tên hiển thị mà không cần gọi
> thêm API. Work Center per-plant, **không** có `companyId` — khác UOM (global).
>
> **[`C2-7`]** `WorkCenterCreateRequest`/`UpdateRequest`/`Response` thêm field tuỳ chọn
> `workCalendarId` (UUID, additive) — gán lịch làm việc cho work center (xem mục Shift/Work Calendar
> ngay dưới). Phải cùng plant với work center, nếu không trả `422 OPERATION_NOT_ALLOWED`. `null` ở
> `PATCH` nghĩa là "giữ nguyên" — **không có cách gỡ** một calendar đã gán qua endpoint này.

### Shift & Work Calendar *(`C2-7`, 2026-08-05)*

| Việc | Endpoint |
|---|---|
| Shift: tạo / danh sách theo plant | `POST /plants/{plantId}/shifts` · `GET /plants/{plantId}/shifts?status=` |
| Shift: xem / sửa | `GET /shifts/{id}` · `PATCH /shifts/{id}` (chỉ `name`/`startTime`/`endTime`/`breaks[]`) |
| Shift: activate / deactivate / **delete** | `POST /shifts/{id}/activate` · `POST /shifts/{id}/deactivate` · **`DELETE /shifts/{id}`** ⇐ cùng hành vi với `deactivate` |
| Work Calendar: tạo / danh sách theo plant | `POST /plants/{plantId}/work-calendars` · `GET /plants/{plantId}/work-calendars?status=` |
| Work Calendar: xem / sửa | `GET /work-calendars/{id}` · `PATCH /work-calendars/{id}` (chỉ `name`/`effectiveFrom`/`effectiveTo`/`weeklyShifts[]`/`exceptions[]`) |
| Work Calendar: activate / deactivate / **delete** | `POST /work-calendars/{id}/activate` · `POST /work-calendars/{id}/deactivate` · **`DELETE /work-calendars/{id}`** ⇐ cùng hành vi với `deactivate` |

> **Shift là MỘT khoảng liên tục**, không phải danh sách nhiều ca con: `startTime`/`endTime` kiểu
> `TIME` (`"HH:mm:ss"`, không có ngày). `endTime` nhỏ hơn `startTime` nghĩa là **ca qua đêm** (vd
> `22:00:00`-`06:00:00`), **không phải** lỗi input. `breaks[]` (mỗi phần tử `{startTime, endTime}`)
> là các khoảng nghỉ nằm **trong** ca đó; gửi khoảng nghỉ nằm ngoài ca (kể cả ca qua đêm) trả
> `422 OPERATION_NOT_ALLOWED`.
>
> **`weeklyShifts[]`** của Work Calendar là mảng `{weekday, shiftId}` (`weekday` ∈
> `MONDAY`..`SUNDAY`) — **một weekday có thể gán nhiều shift** (vd ca ngày + ca đêm cùng chạy thứ
> Hai). `shiftId` phải cùng plant với work calendar, nếu không trả `422 OPERATION_NOT_ALLOWED`.
>
> **`exceptions[]`** là mảng `{exceptionDate, reason}` — mỗi phần tử đánh dấu **một ngày cụ thể**
> thành ngày nghỉ (`NON_WORKING`), ghi đè lịch tuần mặc định. **Chỉ có một chiều** — không có kiểu
> "làm bù"/ngày đặc biệt khác. Trùng `exceptionDate` trong cùng request trả
> `422 BUSINESS_RULE_VIOLATION`.
>
> 🔴 **`breaks[]`/`weeklyShifts[]`/`exceptions[]` trên `PATCH` theo đúng quy ước full-replace-khi-có-mặt
> đã dùng ở Work Center/Routing:** không gửi field (`null`/vắng mặt trong JSON) = giữ nguyên danh sách
> hiện có; gửi `[]` = xoá sạch danh sách đó. Gửi một mảng có phần tử = **thay thế toàn bộ**, không phải
> "thêm vào".
>
> Chưa có API public tính "giờ làm thực" (net working window, đã trừ break/ngày nghỉ) — nội bộ dùng
> cho `C2-8` (Capacity Board — nay **đã** dùng, xem mục ngay dưới).

### Capacity Board (CRP tĩnh) *(`C2-8`, 2026-08-05)*

| Việc | Endpoint |
|---|---|
| Load/capacity theo horizon | `GET /plants/{plantId}/capacity-board?from=&to=&workCenterId=&status=&page=&size=&sortBy=&sortDir=` (`from`/`to` bắt buộc, `ISO-8601` date) |
| Điều chỉnh lịch một operation | `POST /work-orders/{workOrderId}/operations/{operationId}/schedule-adjustments` |

> **Lịch (`plannedStartAt`/`plannedEndAt` của mỗi operation) sinh MỘT LẦN, lúc `POST
> /work-orders/{id}/release`** — không phải lúc tạo hay `plan()` work order. WO chưa release thì
> operation của nó **không xuất hiện** trên Capacity Board (đúng thiết kế, không phải bug/thiếu dữ
> liệu).
>
> **Mỗi dòng của Capacity Board là một operation**, kèm ngữ cảnh capacity/load/utilization của
> Work Center nó chạy trên, cho đúng ngày operation đó bắt đầu: `dayCapacityMinutes`
> (`null` = Work Center chưa gắn Work Calendar, **không phải** `0`), `dayExistingLoadMinutes`,
> `utilizationPercent` (`null` khi capacity không xác định được), `overload` (boolean),
> `calendarExceptionApplies` (ngày đó là exception `NON_WORKING` của Work Calendar).
> `dayExistingLoadMinutes`/`overload` luôn tính trên **mọi** operation `RELEASED`/`IN_PROGRESS`/
> `COMPLETED`/`CLOSED` (`P6`, 2026-08-06 — đóng WO vẫn giữ nguyên lịch sử load, không bị trừ khỏi số
> utilization của ngày đã báo cáo) của Work Center đó, **bất kể** filter `status` đang lọc dòng nào —
> filter chỉ ảnh hưởng dòng hiển thị, không ảnh hưởng số utilization.
>
> 🔴 **Đây là lịch "infinite capacity"** — hệ thống không tự phát hiện và ngăn hai Work Order cùng
> chiếm một Work Center cùng lúc; nó chỉ **báo cáo** khi việc đó đã xảy ra (`overload = true`). Việc
> giải quyết xung đột là của con người, qua `schedule-adjustments`.
>
> **`schedule-adjustments` request:** `{plannedStartAt, plannedEndAt, reason, expectedVersion}` (cả
> 4 field bắt buộc; `expectedVersion` là optimistic-lock — lệch trả `409 CONCURRENT_MODIFICATION`).
> `plannedEndAt <= plannedStartAt` trả `400`. Operation chưa có Work Center hoặc chưa từng được
> `release()` trả `422 OPERATION_NOT_ALLOWED`.
>
> 🔴 **Backend KHÔNG BAO GIỜ tự dời operation khác và KHÔNG chặn cứng khi lịch mới đụng operation
> liền kề hoặc vượt capacity.** Response luôn thành công (nếu qua được 3 check cứng ở trên) kèm 3 cờ
> tư vấn: `sequenceConflict` (đè lên cửa sổ của operation liền trước/sau), `calendarConflict` (0 phút
> làm việc ngày đó), `capacityOverload` — FE tự quyết định hiển thị cảnh báo, người dùng tự xử lý
> xung đột bằng tay (gọi lại `schedule-adjustments` cho operation khác nếu cần).
>
> Chưa làm: validate thứ tự phụ thuộc operation (`predecessorOperationIds`) — nợ tách riêng, xem
> `NEXT_PHASE_PLAN.md`.

### Costing *(`P3`, 2026-08-05)*

| Việc | Endpoint |
|---|---|
| Tạo / sửa standard cost của một item | `PUT /companies/{companyId}/items/{itemId}/standard-cost` — body `{materialCost, laborCost, overheadCost}` (cả 3 bắt buộc, `≥ 0`), 200 (upsert, không phải 201 kể cả lần đầu) |
| Xem standard cost của một item | `GET /companies/{companyId}/items/{itemId}/standard-cost` — 404 nếu chưa từng `PUT` |
| Danh sách standard cost | `GET /companies/{companyId}/items/standard-costs?itemId=&page=&size=&sortBy=&sortDir=` (`itemId` tuỳ chọn để lọc) |

> **`PERM_COSTING_READ`/`PERM_COSTING_MANAGE` — ADMIN+MANAGER only, OPERATOR không có cả hai.** Khác
> mọi permission thêm gần đây (Work Center/Shift/Capacity cho OPERATOR đọc) — chi phí bị coi là dữ
> liệu nhạy cảm.
>
> **`totalStandardCost` trong response tính lại mỗi lần đọc, không lưu.** Với item có BOM `ACTIVE`:
> `materialCost` = Σ(`totalStandardCost` của mỗi component × `quantityPer` × (1 + `scrapRate`)) —
> field `materialCost` riêng của chính item đó (nhập qua `PUT`) bị **bỏ qua**, chỉ dùng cho item
> không có BOM (nguyên liệu mua ngoài). `laborCost`/`overheadCost` **luôn** là giá trị đã `PUT` cho
> item đó — không bao giờ cộng dồn từ BOM. Sửa một BOM line thì `GET .../standard-cost` của mọi item
> cha (và cha của cha) phản ánh ngay lần đọc kế tiếp, không cần thao tác "tính lại".
>
> **Không có Material Price Variance** — chỉ Material Usage Variance, xem field `usageVarianceCost`
> ở mục Bước 10 (`GET /work-orders/{id}/variance`) phía trên.

### Access Control — Role / Scope lifecycle + Assignments *(`C2-4`, 2026-08-05)*

| Việc | Endpoint |
|---|---|
| Role: xem / sửa `name`+`description` | `GET/PATCH /access/roles/{roleId}` |
| Role: activate / deactivate | `POST /access/roles/{roleId}/activate` · `/deactivate` |
| Scope: xem / sửa `name`+`description` | `GET/PATCH /access/scopes/{scopeId}` |
| Scope: activate / deactivate | `POST /access/scopes/{scopeId}/activate` · `/deactivate` |
| Tra cứu assignment | `GET /access/assignments?userId=&roleId=&scopeId=&page=&size=` (cả 3 filter tuỳ chọn) |
| **Role: đọc permission đang có** | **`GET /access/roles/{roleId}/permissions?page=&size=`** *(2026-08-12)* |
| **Scope: đọc resource đang có** | **`GET /access/scopes/{scopeId}/resources?page=&size=`** *(2026-08-12)* |

> **[2026-08-12] Hai endpoint đọc membership ở trên là nguồn authoritative duy nhất** cho màn hình
> phân quyền — `RoleResponse` **không** nhúng `permissions[]` và `AccessScopeResponse` **không** nhúng
> `resources[]` (giữ nguyên, không breaking change). Dựng checkbox bằng cách so
> `GET /access/permissions` (catalog) với `GET /access/roles/{roleId}/permissions` (đang có), **không**
> suy từ role code hay dữ liệu hardcode.
> `roleId`/`scopeId` không tồn tại trả **404 `ENTITY_NOT_FOUND`**, không phải page rỗng — page rỗng
> nghĩa là "role có thật, chưa được cấp quyền nào", hai chuyện khác nhau.
> Danh sách trả về **gồm cả permission đã `INACTIVE`** nếu role vẫn đang giữ: đó là grant có thật,
> ẩn đi sẽ khiến lần save kế tiếp âm thầm gỡ mất nó. Rẽ nhánh hiển thị theo field `status`.

> 🔴 **Role/Scope dùng verb `POST .../activate`+`.../deactivate`, KHÁC với BOM/Routing/UOM ở trên**
> (`DELETE` = deactivate). Đây là 2 pattern verb khác nhau tồn tại song song trong cùng API, không
> phải lỗi — đi theo đúng 2 verb FE liệt kê tường minh cho nhóm này.
> **Role `is_system = true` (ADMIN/MANAGER/OPERATOR) không bao giờ deactivate được**, kể cả bởi
> `admin` — trả `422 OPERATION_NOT_ALLOWED`. `code`/`is_system`/`scopeType` là **immutable**, `PATCH`
> chỉ nhận `name`/`description`. Cùng permission `PERM_ACCESS_MANAGE` cho cả 9 endpoint — **không**
> có permission mới.

### Audit Logs *(`C2-1`, 2026-08-06)*

| Việc | Endpoint |
|---|---|
| Danh sách audit trail | `GET /audit-logs?actorUserId=&entityType=&entityId=&action=&outcome=&source=&companyId=&plantId=&warehouseId=&traceId=&relatedEntityType=&relatedEntityId=&from=&to=&page=&size=&sortBy=&sortDir=` (mọi filter tuỳ chọn, default sort `occurredAt,desc`). 🔴 **[2026-09-02]** `sortBy` có allowlist (`occurredAt`/`createdAt`/`action`/`username`/`outcome`) — giá trị khác trả **400** thay vì 500; `from > to` trả **400** thay vì trang rỗng |
| Chi tiết một audit log, kèm `changes[]` **và `entities[]`** | `GET /audit-logs/{auditLogId}` |

> 🔴 **`PERM_AUDIT_READ` — ADMIN only**, không cấp cho MANAGER/OPERATOR (chốt với user). Gác bằng
> `hasPermission` (kiểm tra global), không `hasResourceAccess` — audit trail không thuộc về một
> company/plant cụ thể, nên **không** có cross-check `X-Plant-Id` trên filter `plantId` (khác
> `PlanningDemandController`, nơi header có ý nghĩa vì có hai nguồn plant để đối chiếu).
>
> Response audit trả `entityName` thay cho `entityId`; `entityId` vẫn được giữ làm query parameter.
> `entityName` là snapshot tên/mã/số chứng từ tại thời điểm thao tác và có thể `null` với dữ liệu lịch
> sử trước migration `V65`. Response chi tiết trả field-level diff thật trong `changes[]` cho audit mới.
>
> 🔴 **`changes[].oldValue`/`newValue` luôn là `string` hoặc `null`** *(chốt 2026-08-25)*. Cột lưu là
> `jsonb` nên một snapshot có thể là chữ, số, boolean, object hay array — nhưng ra tới wire thì mọi
> hình dạng đó đều đã thành chuỗi: object/array là **JSON compact** (dùng `JSON.parse` nếu cần cấu
> trúc), số/boolean là dạng chuỗi, chữ **không** kèm dấu nháy JSON, `null` giữ nguyên `null`. Đừng
> render thẳng giá trị vào React mà không kiểm kiểu nếu client còn phải chạy với backend cũ hơn
> 2026-08-25.
>
> **`plantId` trên mọi dòng — kể cả dòng mới tạo hôm nay — vẫn là `null`.** Cột thêm ở `V54` nhưng chỉ
> dừng ở mức schema; chưa có code nào populate nó lúc ghi audit log (việc đó chạm `RequestContext`/
> `AuditableAspect` — phạm vi khác, chưa xếp lịch). Lọc theo `plantId` hôm nay sẽ luôn trả **rỗng**,
> không phải bug.
>
> **`action` là enum `AuditAction`** (danh sách đầy đủ: xem `@Tag "Audit Logs"` trong Swagger) — gửi
> giá trị không hợp lệ trả `400 VALIDATION_ERROR`, không phải mảng rỗng.

### Inventory Lots *(`C2-2`, 2026-08-06)*

| Việc | Endpoint |
|---|---|
| Danh sách lot trong một kho | `GET /inventory/lots?warehouseId=&itemId=&status=&search=&expiryFrom=&expiryTo=&page=&size=&sortBy=&sortDir=` (`warehouseId` **bắt buộc**, mọi filter khác tuỳ chọn) |
| Chi tiết một lot trong kho đang xem | `GET /inventory/lots/{lotId}?warehouseId=` (`warehouseId` nên luôn gửi từ màn list) |
| Đổi trạng thái lot (`AVAILABLE`/`HOLD`/`REJECTED`) | `POST /inventory/lots/{lotId}/status` — body `{warehouseId, newStatus, reason, referenceType?, referenceId?}`, header `Idempotency-Key` **bắt buộc** (thiếu ⇒ `400 MISSING_REQUIRED_FIELD`; dòng này trước 2026-10-15 ghi "tuỳ chọn" — sai, chưa bao giờ tuỳ chọn) |

> 🔴 **Một lot có thể có hàng ở nhiều kho** — bảng `stock_balances` unique theo `(item, warehouse,
> lot)`, không phải `(item, lot)`. Vì vậy list bắt buộc `warehouseId` (giống `/inventory/balances`),
> detail có `warehouseId` chỉ trả balance và nguồn RECEIVE của đúng kho được phép xem. Chỉ
> company/global/admin mới được bỏ param để nhận toàn bộ `balances[]`.
>
> 🔴 **`POST .../status` không cho lot `HOLD` từ Production Receipt tự do thoát `HOLD`.** Nếu lot
> đang `HOLD` vì vừa được sản xuất ra và chưa qua QC, gọi endpoint này với `newStatus` khác `HOLD` sẽ
> trả `409 LOT_NOT_ELIGIBLE` — phải gọi
> `POST /work-orders/{id}/production-receipts/{rid}/qc-disposition` (đã có từ `F2`) thay vào đó. Lot
> đã qua QC một lần rồi (dù sau đó bị đưa lại `HOLD` thủ công qua chính endpoint này) thì **không**
> bị chặn nữa — hệ thống nhớ đúng "lot này đã từng được QC chưa", không chỉ "lot này có phải hàng sản
> xuất không".
>
> `newStatus` chỉ nhận `AVAILABLE`/`HOLD`/`REJECTED` — gửi `EXPIRED` trả `422
> OPERATION_NOT_ALLOWED` (chưa có luồng nghiệp vụ nào chuyển tay sang `EXPIRED`).
>
> 🔴 **Ngày nhận hàng authoritative là `receivedAt`** (thêm 2026-08-21). Đừng gắn nhãn "Ngày nhận" cho
> `createdAt` — `createdAt`/`updatedAt` là dấu thời gian **audit của dòng dữ liệu**, chỉ trùng
> `receivedAt` với lot được tạo bởi chính lần nhận đầu tiên của nó, và lệch đi với lot được nhập bổ
> sung về sau. `manufactureDate` là **alias cũ mang đúng cùng giá trị `receivedAt`**, giữ lại để client
> hiện tại không gãy; tên của nó sai nghĩa (hệ thống **không** lưu ngày sản xuất riêng) nên đừng hiển
> thị nó như ngày sản xuất.
> `sourceMovementType`/`sourceReferenceType`/`sourceReferenceId`/`sourceAt` cho biết lot này sinh ra
> từ đâu (vd `referenceType="WORK_ORDER"` + `referenceId` là `workOrderId` nếu sinh từ production
> receipt, `referenceType="GOODS_RECEIPT"` nếu nhận từ PO) — có thể `null` nếu không tìm thấy movement
> `RECEIVE` gốc.

### Danh mục nền — FE giai đoạn 2 *(`G2`, 2026-10-14)*

Mọi path dưới đây nằm dưới context `/api` (ví dụ `/api/v1/companies/{companyId}/customers`).

**Khách hàng (mới)** — permission `PERM_CUSTOMER_READ` / `PERM_CUSTOMER_MANAGE` (ADMIN + MANAGER, sao chép từ quyền Supplier; OPERATOR không có):

| Thao tác | Endpoint |
|---|---|
| Danh sách | `GET /v1/companies/{companyId}/customers?search=&status=&page=0&size=20&sortBy=code&sortDir=asc` |
| Tạo | `POST /v1/companies/{companyId}/customers` — body `{code, name, contactName?, email?, phone?, address?, notes?}` → **201** |
| Chi tiết / sửa | `GET /v1/customers/{customerId}` · `PATCH /v1/customers/{customerId}` |
| Kích hoạt / ngừng | `POST /v1/customers/{customerId}/activate` · `POST /v1/customers/{customerId}/deactivate` |

> `code` được **trim + viết hoa trước khi kiểm tra**: gửi `" partner-01 "` sẽ lưu `PARTNER-01`. Mã trùng trong
> cùng công ty (kể cả bản ghi đã ngừng) ⇒ `409 RESOURCE_ALREADY_EXISTS`. Supplier và Customer **được trùng mã**.
> 🔴 **PATCH:** bỏ field hoặc gửi `null` = **giữ nguyên**; gửi chuỗi rỗng `""` = **xoá** field tuỳ chọn. Đây
> **không** phải quy ước "null = xoá" mà tài liệu FE giai đoạn 2 đề xuất. `name` không xoá được (rỗng ⇒ 400).
> Kích hoạt khi công ty đang ngừng ⇒ `422 RESOURCE_INACTIVE`.

**Nhà cung cấp** — đường mới **song song**, đường cũ (`/v1/suppliers?companyId=&keyword=`, `PATCH .../deactivate`) vẫn chạy:

| Thao tác | Endpoint |
|---|---|
| Danh sách theo công ty | `GET /v1/companies/{companyId}/suppliers?search=&status=` (sort mặc định `code asc`) |
| Tạo theo công ty | `POST /v1/companies/{companyId}/suppliers` — body như Customer, cộng `taxCode?` |
| Kích hoạt / ngừng | `POST /v1/suppliers/{supplierId}/activate` · `POST /v1/suppliers/{supplierId}/deactivate` |

> Response Supplier có thêm `contactName`, `notes` (cuối object). PATCH dùng cùng quy ước rỗng-là-xoá.

**Thiết lập kho của vật tư** (`PERM_INVENTORY_READ` / `PERM_INVENTORY_MANAGE` — **không** phải `PERM_ITEM_*`):

| Thao tác | Endpoint |
|---|---|
| Danh sách theo nhà máy | `GET /v1/inventory/item-warehouse-settings?plantId=&itemId=&warehouseId=` + header `X-Plant-Id` (tuỳ chọn, phải trùng `plantId`) |
| Lưu (upsert theo cặp vật tư + kho) | `PUT /v1/inventory/item-warehouse-settings` — body thêm `replaceDefaults?` |
| Ngừng | `DELETE /v1/inventory/item-warehouse-settings/{settingId}` |

> 🔴 **`replaceDefaults`** khi kho khác của cùng vật tư/nhà máy đang giữ vai trò default bạn yêu cầu:
> **bỏ field** ⇒ chuyển cờ ngay (hành vi cũ) · **`false`** ⇒ `409 DEFAULT_CONFLICT`, **không ghi gì** — hiện
> confirmation rồi gửi lại với **`true`** ⇒ chuyển cờ. Chỉ vai trò bạn yêu cầu bị gỡ khỏi kho kia.
> Lưu lại một cặp đã ngừng sẽ **kích hoạt lại đúng bản ghi cũ** (giữ `settingId`). Ngừng một setting xoá luôn
> hai cờ default của nó. `X-Plant-Id` lệch với nhà máy của kho ⇒ `409 STATE_CONFLICT`; sai định dạng ⇒ 400.
> Kho thuộc nhà máy đang ngừng ⇒ `422 RESOURCE_INACTIVE`.

**Tổ chức & vật tư — thay đổi hành vi:**

| Endpoint | Thay đổi |
|---|---|
| `GET /v1/companies/{companyId}/plants`, `GET /v1/plants/{plantId}/warehouses` | Nay **nhận** `search` (mã/tên) và `status` — trước đây hai tham số này bị bỏ qua im lặng |
| `GET /v1/companies/{companyId}/items` | Thêm `status`, `type`, `lotTracked` (cạnh `search` đã có), lọc trước phân trang |
| `POST`/`PATCH /v1/companies/{companyId}/plants`, `/v1/plants/{plantId}` | Timezone không hợp lệ ⇒ `400 VALIDATION_ERROR`, `errors[0].field = "timezone"` |
| `DELETE /v1/plants/{plantId}` | Còn kho `ACTIVE` ⇒ **`422 ACTIVE_CHILDREN_EXIST`** — ngừng kho trước |
| `POST /v1/companies/{companyId}/items` | `unit` phải là mã UOM **tồn tại** (404 nếu không) và **đang hoạt động** (`422 RESOURCE_INACTIVE`) |
| `PATCH /v1/items/{itemId}` | `unit` **không đổi được**: gửi lại đúng giá trị cũ thì được, khác ⇒ `422 OPERATION_NOT_ALLOWED` |
| **Mọi endpoint nhận body JSON** | Số có dấu chấm gửi vào field **số nguyên** (`leadTimeDays`, `capacityUnits`, `lineNo`, `sequence`, `priority`, `headerRowIndex`…) ⇒ **`400 VALIDATION_ERROR`**, `errors[0].field` = tên field, `message` = `"must be a whole number"`. Trước đây `1.5` bị **cắt thành `1` và lưu**, trả 200. 🔴 **`5.0` cũng bị từ chối** — gửi số nguyên không có phần thập phân (`5`). Field thập phân (`safetyStock`, `quantity`, `quantityPer`…) **không** bị ảnh hưởng |


### Nhập Excel dữ liệu nền — FE giai đoạn 3 *(`G3`, 2026-10-14)*

Mẫu `omniplant-import-v1` (sheet `_OmniPlant`: `A1` = phiên bản, `B1` = `items|suppliers|customers`, `C1` = `vi|en`).
Đường cũ `/v1/import-runs*` (profile ánh xạ cột, ghi từng dòng) **không đổi** và **không thấy** các batch dưới đây.

| Thao tác | Endpoint | Ghi chú |
|---|---|---|
| Kiểm tra file | `POST /v1/data-import/batches` — multipart `file`, `kind`, `companyId`, `plantId` (bắt buộc với `items`, bị bỏ qua với đối tác) · header `Idempotency-Key?`, `X-Plant-Id?` | **201** kể cả khi có dòng lỗi (batch `INVALID`). **Không tạo master nào** |
| Xác nhận nhập | `POST /v1/data-import/batches/{id}/commit` — body `{"expectedVersion": <version>}` · `Idempotency-Key?` | Tạo **cả lô trong một transaction** hoặc không gì cả |
| Lịch sử | `GET /v1/data-import/batches?companyId=&plantId=&kind=&status=&search=&page=0&size=20` | Chỉ batch **của chính người gọi**, mới nhất trước |
| Chi tiết | `GET /v1/data-import/batches/{id}` | Đủ `rows[]` + `createdIds[]` |

Quyền: cần **cả** READ và MANAGE **của chính loại** — `PERM_ITEM_*` (nhà máy được chọn; gán COMPANY/GLOBAL phủ nhà máy cũng
được), `PERM_SUPPLIER_*` / `PERM_CUSTOMER_*` (công ty). **Không** dùng `PERM_DATA_IMPORT_*`. Batch của người khác ⇒ **404**
(không lộ tồn tại); batch của mình nhưng đã mất quyền ⇒ **403**.

> 🔴 `version` bắt đầu từ **0** sau upload (không phải 1), tăng 1 mỗi lần đổi trạng thái — gửi lại đúng số đã nhận.
> 🔴 Commit một batch **đã `COMMITTED`** luôn trả lại receipt cũ (200, cùng `createdIds`/`committedAt`), kiểm quyền trước.
> 🔴 Dữ liệu đã đổi kể từ lúc kiểm tra (mã bị chiếm, UOM ngừng) ⇒ **409 `IMPORT_REVALIDATION_FAILED` kèm `result`** = batch đã
> chuyển `INVALID` với lỗi mới — mã lỗi **duy nhất** của hệ thống có `result` khi lỗi.
> Giới hạn: 5 MiB/file (vượt ⇒ `413 FILE_SIZE`), 500 dòng có dữ liệu (vượt ⇒ `400 ROW_LIMIT`), ≤ 64 phần trong archive,
> ≤ 25 MiB sau giải nén. Mã lỗi theo dòng (`rows[].issues[].code`): `REQUIRED_MISSING`, `TOO_LONG`, `INVALID_FORMAT`,
> `NOT_ALLOWED_VALUE`, `DUPLICATE_IN_FILE`, `ALREADY_EXISTS`, `REFERENCE_NOT_FOUND`, `RULE_VIOLATION`, `FORMULA`,
> `TEXT_REQUIRED`, `CELL_TYPE`.

### Tồn kho, lô, chuyển kho — FE giai đoạn 4 *(`G4`, 2026-10-15)*

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Chuyển kho | `POST /v1/inventory/transfers` — body `{fromWarehouseId, toWarehouseId, reason, lines:[{itemId, lotId?, quantity}]}` · `Idempotency-Key` **bắt buộc** · `X-Plant-Id?` | **201**. Cả phiếu hoặc không gì; hai kho khác nhau cùng một plant `ACTIVE`; 1–100 dòng; **không** cho hai dòng cùng (item, lô) |
| Danh sách phiếu | `GET /v1/inventory/transfers?plantId=&warehouseId=&search=&page=&size=` | `plantId` **bắt buộc**; `warehouseId` = phiếu chạm kho đó ở **một trong hai** phía; `lines` = `null` ở danh sách |
| Chi tiết phiếu | `GET /v1/inventory/transfers/{id}` | Đủ `lines[]` + `outMovementId`/`inMovementId` |
| Kiểm đếm tuyệt đối | `POST /v1/inventory/counts` — body `{itemId, warehouseId, lotId?, countedQuantity, expectedVersion, reason}` · `Idempotency-Key` **bắt buộc** | `expectedVersion` = `version` của **đúng vị thế** lấy từ `GET /inventory/balances`. `POST /adjust` **giữ nguyên** nghĩa delta |
| Chi tiết movement | `GET /v1/inventory/movements/{id}` | Có `traceId`, `createdBy`, `createdByUsername` (cũng có trên list) |
| Giữ cho sản xuất | `GET /v1/inventory/reservations?warehouseId=&itemId=&lotId=&status=&page=&size=` | `status` bỏ trống = mọi trạng thái; `remainingQuantity = quantity − consumedQuantity` |
| Aggregate | `GET /v1/inventory/balances/aggregate?…&search=` | `search` theo mã/tên vật tư; thêm `heldLotQuantity` |

> 🔴 **Bốn bucket không chồng nhau trên aggregate:** `availableQuantity` (không-lô + lô `AVAILABLE` còn hạn)
> · `heldLotQuantity` (lô `HOLD`) · `rejectedQuantity` · `expiredQuantity` (lô `EXPIRED` **và** lô `AVAILABLE`
> đã quá hạn). `qualityHoldQuantity` vẫn chỉ là phần giữ QC của hàng **không** lô. Lô `AVAILABLE` quá hạn báo
> `availableQuantity = 0` trên `/balances` và `/lots`.
> 🔴 **Hết hạn chỉ chặn ở luồng kho thủ công** (`/issue`, chuyển kho, kiểm đếm, đổi trạng thái sang
> `AVAILABLE`): `409 LOT_NOT_ELIGIBLE`. Giữ/xuất vật tư cho Work Order **chưa** xét hạn dùng.
> 🔴 Đổi trạng thái lô là **toàn cục** (mọi kho có lô đó): cần quyền ở **mọi** kho còn tồn của lô, thiếu ⇒ `403`.
> Từ chối: cùng trạng thái ⇒ `409 NO_CHANGE`; còn reservation ⇒ `409 RESERVED_STOCK`; quá hạn sang `AVAILABLE` ⇒ `409 LOT_NOT_ELIGIBLE`.
> 🔴 `expiresAt` trên `POST /inventory/receive` chỉ khi receipt **tạo lô mới** và phải ở tương lai (quá khứ ⇒ `400`
> trên `expiresAt`; lô có sẵn ⇒ `422 OPERATION_NOT_ALLOWED`).
> 🔴 Mọi số lượng trên request inventory tối đa **6 số lẻ** (kể cả số 0 thừa: `1.1234560` bị từ chối) ⇒ `400` với
> `errors[].field` (vd `lines[0].quantity`). Trước đây Postgres làm tròn im lặng.
> Không có định giá tồn kho (giá bình quân, giá trị tồn, snapshot giá) — để phase sau.

### Giá thành sản xuất và đóng lệnh — FE giai đoạn 11 *(`G11`, 2026-10-27)*

Chi tiết đầy đủ (13 endpoint, công thức, Q1–Q36): `BE_RESPONSE_GD_11_2026-10-27.md`. Những điều phải nhớ khi wire:

- 🔴 **Hai hệ giá thành, khác nghĩa:** `GET /v1/work-orders/{id}/variance` (`costVariance`) = giá chuẩn **theo vật tư**, tức thời —
  **không đổi**. `GET /v1/work-orders/{id}/production-costs` = giá thành **theo trung tâm + thời gian**, cơ sở chụp lúc phát hành,
  có tính thử và chốt. Đừng ghép số của hai bên.
- **Giá vật tư = giá chuẩn của vật tư tại giây phát hành** (kho không có giá). Đặt giá chuẩn
  (`PUT /v1/companies/{C}/items/{I}/standard-cost`) và đơn giá trung tâm (`PUT /v1/plants/{P}/production-cost-rates/{W}`) **trước**
  khi phát hành — thêm sau không hồi tố; thiếu thì số liên quan là `null` kèm `unknownReasons`, không phải 0.
- **Luồng đóng lệnh mới:** đọc `GET …/cost-checklist` (lấy `sourceVersion`, `workOrderVersion`) → `POST …/cost-runs`
  `{expectedVersion, expectedSourceVersion}` → khi `readiness = READY`: `POST …/finalize-cost-and-close`
  `{expectedVersion, expectedSourceVersion, runId, expectedRunVersion: 1, reason}`. Cả ba lệnh **bắt buộc** `Idempotency-Key` và chỉ
  trả id + version — đọc lại sau đó.
- 🔴 `POST /v1/work-orders/{id}/close` trả `409 CLOSE_BLOCKED` cho lệnh phát hành sau bản này; lệnh cũ (không có cơ sở chi phí) đóng
  như trước.
- Người không có quyền xem giá thành vẫn đọc được checklist (`PERM_WORK_ORDER_CLOSE` / `_READ` / `PERM_WIP_READ`): `sourceVersion` và
  `cost` khi đó **vắng mặt**, không phải `null`.
- Tiền là số JSON scale ≤ 6 (trần `NUMERIC(19,6)`); đọc bằng decimal nếu cần chính xác trên ~10^9.

### Giao hàng, hoá đơn, thu tiền — FE giai đoạn 10 *(`G10a`–`G10d`, 2026-10-25 → 27)*

Lệnh ghi GĐ10 (phiếu giao, hoá đơn, phiếu thu, bổ sung giá) **bắt buộc** `Idempotency-Key` và trả `{documentId, type, version}` — đọc lại chi tiết
sau đó. Tiền là **số JSON nguyên đồng** ≤ 9007199254740991, `currency` luôn `"VND"`. Không đụng tồn kho trừ ghi sổ phiếu giao.

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Giá trên đơn | dòng nháp (`POST /sales-orders/v1/drafts`, `PATCH …/{id}/draft`) nhận `unitPrice?` · `POST /sales-orders/v1/{id}/commercial-prices {expectedVersion, prices[]{salesOrderLineId, unitPrice}}` · `PERM_SALES_PRICE_MANAGE` | Bổ sung giá thiếu cho đơn đã xác nhận; giá đã biết (kể cả 0) khoá ⇒ `409 PRICE_LOCKED` |
| Khách bắt buộc | `POST …/{id}/confirm` | đơn không có `customerId` ⇒ `422 OPERATION_NOT_ALLOWED` field `customerId`. Đường tạo cũ `POST /sales-orders/v1` nhận `customerId?` |
| Phiếu giao | `GET /v1/deliveries?plantId…` · `GET …/{id}` · `…/{id}/history` · `GET /sales-orders/v1/{id}/delivery-candidates` · `GET /sales-orders/v1/{id}/lines/{lineId}/delivery-sources?warehouseId` · `POST /v1/deliveries {salesOrderId, expectedOrderVersion, documentDate, note?, lines[]{salesOrderLineId, sourceKey, quantity}}` · `PATCH /{id}` · `POST /{id}/post` · `POST /{id}/cancel` | `PERM_DELIVERY_*`; `sourceKey` lấy từ `delivery-sources`, không tự ghép. Hạn mức `đặt − đã giao − nháp khác`. Ghi sổ lấy phần giữ của chính đơn trước rồi hàng tự do; lô `HOLD`/`REJECTED`/hết hạn ⇒ `409 LOT_NOT_ELIGIBLE`. Huỷ đơn còn phiếu giao ⇒ `409` |
| Phiếu giao liên quan | `GET /v1/deliveries/related?plantId` + **đúng một** `lotId` / `productionReceiptId` / `workOrderId` | phiếu nhập/WO = lô đầu ra đã duyệt + lô con QC chấp nhận; không có lô ⇒ trang rỗng |
| Hoá đơn | `GET /v1/sales-invoices?plantId…&paymentState?` · `GET …/{id}` · `…/{id}/history` · `…/{id}/payments` · `GET /sales-orders/v1/{id}/invoice-candidates` · `POST /v1/sales-invoices/preview` · `POST /v1/sales-invoices {…, billingSnapshotVersion, documentDate, dueDate, lines[]{deliveryLineId, quantity}}` · `PATCH /{id}` · `POST /{id}/post` · `POST /{id}/cancel` | `PERM_SALES_INVOICE_*`. Server tự tính tiền (HALF_UP cộng dồn theo dòng đơn); lưu bằng `billingSnapshotVersion` của lần xem trước cuối; nền đổi ⇒ `409 PRICE_STALE` (xem trước + lưu lại). Thiếu giá ⇒ `409 PRICE_UNKNOWN`. Hoá đơn `POSTED` có `paidAmount`/`outstandingAmount`/`paymentState` |
| Thu tiền | `GET /v1/customer-payments?plantId…` · `GET …/{id}` · `…/{id}/history` · `GET /v1/customer-payments/invoice-candidates?plantId&customerId&editingPaymentId?` · `POST /v1/customer-payments {customerId, documentDate, method, reference?, note?, amount, allocations[]{invoiceId, expectedInvoiceVersion, amount}}` · `PATCH /{id}` · `POST /{id}/post` · `POST /{id}/cancel` | `PERM_CUSTOMER_PAYMENT_*`. `amount` = tổng phân bổ; mỗi hoá đơn ≤ `availableToAllocate`. Ghi sổ tăng `version` mỗi hoá đơn được trả |
| Tiến độ đơn | `GET /sales-orders/v1/{id}/fulfillment-summary` | lượng cho mọi người đọc đơn; giá dòng chỉ với quyền giá/hoá đơn; khoản phải thu chỉ với quyền hoá đơn |

> 🔴 **Thiếu quyền tiền ⇒ object `commercial` vắng mặt** (không phải `null`) trên chi tiết đơn, dòng đơn và tiến độ đơn. Kiểm bằng `'commercial' in x`.
> 🔴 **Đơn xác nhận trước GĐ10** có `deliveryBaselineKnown = false` (`deliveryBaselineUnknownReason = "LEGACY_ORDER"`): không giao / lập hoá đơn
> được (`409 SOURCE_UNKNOWN`), số giao/lập là `null`.
> 🔴 Trạng thái đơn **không** đi theo giao/thu (giao đủ, thu đủ vẫn có thể `CONFIRMED`). Hiển thị bằng `deliveryProgress` + `outstandingAmount`.
> Chi tiết, ví dụ và mã lỗi đầy đủ: `BE_RESPONSE_GD_10_2026-10-25.md`, `…-26_v2.md`, `…-27_v3.md`.

### Vòng đời đơn mua hàng — FE giai đoạn 7 phần 1 *(`G7a`, 2026-10-18)*

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Tạo nháp (server cấp mã) | `POST /v1/purchase-orders/drafts` — `{companyId, plantId, supplierId, warehouseId, orderDate, reference?, notes?, lines[1..500]{itemId, orderedQuantity, unitPrice?, expectedDate}}` · `Idempotency-Key` tuỳ chọn · `X-Plant-Id` phải khớp `plantId` | **201**, mã `PO-000001`…; vật tư `RAW_MATERIAL`/`CONSUMABLE`/`WIP`, không theo sê-ri; dòng `VND`; `unitPrice` `null` = chưa có giá, `0` = giá |
| Sửa nháp | `PATCH /v1/purchase-orders/{id}` — `{expectedVersion, supplierId, warehouseId, orderDate, reference?, notes?, lines[]{id?, itemId, orderedQuantity, unitPrice?, expectedDate}}` | chỉ `DRAFT`; thay toàn bộ dòng, `id` giữ dòng cũ; text `null` = giữ, `""` = xoá. PO còn giữ quota hoạch định ⇒ `409 SOURCE_LOCKED` nếu đổi kho/vật tư/lượng/tập dòng |
| Xác nhận | `POST /v1/purchase-orders/{id}/confirm` — `{expectedVersion}` | `DRAFT → CONFIRMED`; thiếu giá ⇒ `400` field `lines[i].unitPrice`. `/send` cũ vẫn chạy (không kiểm giá) |
| Huỷ | `POST /v1/purchase-orders/{id}/cancel` — body **tuỳ chọn** `{expectedVersion, reason}` | không body: chỉ `DRAFT`. Có body: `DRAFT`/`CONFIRMED` (chưa nhận). Trả quota hoạch định một lần |
| Đóng phần còn lại | `POST /v1/purchase-orders/{id}/close` — `{expectedVersion, reason}` | `CONFIRMED`/`PARTIALLY_RECEIVED` còn lượng mở ⇒ `CLOSED`, `closedQuantity` = phần còn lại. Không trả quota |
| Danh sách | `GET /v1/purchase-orders?companyId&plantId&warehouseId?&supplierId?&status?&search?&orderDateFrom?&orderDateTo?` | `search`: mã PO, tham chiếu, mã/tên NCC; ngày bao gồm hai đầu |

Field mới trên PO: `code` (= `purchaseOrderNo`), `version`, `warehouseName`, `reference`, `currencyCode`, `sourceLocked`, `reason`,
`confirmedAt`, `cancelledAt`, `closedAt`, `createdBy`, `history[]`. Trên dòng: `uomCode`, `closedQuantity`, `openQuantity`, `sources[]`.

> 🔴 **Đổi tên trạng thái:** `SENT` → `CONFIRMED`, `RECEIVED` → `FULLY_RECEIVED`, thêm `CLOSED` (dữ liệu cũ đã đổi). `status=SENT` ⇒ 400.
> 🔴 `remainingQuantity` trên dòng nay = `openQuantity` (trừ cả phần đã đóng). Chuyển BUY từ hoạch định cần thêm `PERM_PURCHASE_ORDER_MANAGE`.
> 🔴 Phiếu nhận của PO `CLOSED` không huỷ được (`409 STATE_CONFLICT`). Id PO không tồn tại trả **403** (như mọi `/{id}`).

### QC mua hàng theo số lượng — FE giai đoạn 7 phần 3 *(`G7c`, 2026-10-18)*

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Hàng đợi QC | `GET /v1/purchase-qc?companyId&plantId&warehouseId?&search?&receivedDateFrom?&receivedDateTo?&page&size` · `X-Plant-Id` phải khớp · `PERM_PURCHASE_QC_READ` | Mỗi dòng = **một dòng phiếu** `POSTED` còn `pendingQuantity > 0`. Field: mã phiếu/PO/NCC/kho, `goodsReceiptVersion` (gửi làm `expectedVersion`), `trackingPolicy`, `lotId`/`lotCode`/`lotExpiresAt`, `received`/`accepted`/`rejected`/`pendingQuantity`, `qualityBucketId` (= id dòng phiếu khi hàng không theo lô, `null` khi theo lô). `search`: mã phiếu, mã PO, mã/tên NCC, mã/tên vật tư, mã lô |
| Ghi quyết định | `POST /v1/goods-receipts/{id}/lines/{lineId}/qc` — `{expectedVersion, acceptedQuantity, rejectedQuantity, reason?}` · `Idempotency-Key` tuỳ chọn · `PERM_PURCHASE_QC_MANAGE` | **200** `{decision, receiptVersion, receivedQuantity, acceptedQuantity, rejectedQuantity, pendingQuantity}`. Một phần được: nhiều quyết định mỗi dòng |
| Lịch sử quyết định | `GET /v1/goods-receipts/{id}/quality-decisions?page&size` | `PURCHASE_QC_READ` **hoặc** `GOODS_RECEIPT_READ` **hoặc** `PURCHASE_ORDER_READ`; mới nhất trước |

Quyết định (`decision`): `decisionId`, `acceptedQuantity`, `rejectedQuantity`, `reason`, `decidedBy`/`decidedByUsername`/`decidedAt`,
`qualityBucketId`, `childLots[]{lotId, lotCode, parentLotId, status, quantity, expiresAt, movementId}`, `movementIds[]`. **Không** có giá.

> 🔴 **Hàng theo lô:** phần chấp nhận sang lô con `AVAILABLE` (`<lô>-A1`, `-A2`…), phần loại sang lô con `REJECTED` (`<lô>-R1`…); phần còn chờ
> ở lại lô gốc `HOLD`. Lô hết hạn **chỉ được loại**. **Hàng không theo lô:** phần chấp nhận rời vùng chờ, phần loại **ở lại** `qualityHoldQuantity`.
> Tồn thực tế **không đổi** trong mọi trường hợp; PO không đổi.
> 🔴 Lỗi: cả hai bằng 0 ⇒ `400` field `acceptedQuantity`; loại mà thiếu `reason` ⇒ `400` field `reason`; vượt phần chờ ⇒ `409 PLANNED_QUANTITY_EXCEEDED`
> field `acceptedQuantity`; `expectedVersion` cũ ⇒ `409 CONCURRENT_MODIFICATION`; phiếu không `POSTED` hoặc nhận trước khi có vùng chờ ⇒ `409 STATE_CONFLICT`.
> 🔴 Lô đang chờ QC mua không đổi trạng thái được qua `POST /v1/inventory/lots/{id}/status`, và lô con bị QC loại cũng không (`409 LOT_NOT_ELIGIBLE`).
> Phiếu đã có quyết định QC **không huỷ (đảo nhập) được** (`409 STATE_CONFLICT`). Lô có thêm field `parentLotId`.

### Phiếu nhận nháp, nhận vào vùng chờ kiểm — FE giai đoạn 7 phần 2 *(`G7b`, 2026-10-18)*

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Ứng viên PO để nhận | `GET /v1/goods-receipts/purchase-order-candidates?companyId&plantId&search?` | PO `CONFIRMED`/`PARTIALLY_RECEIVED` còn dòng mở, chỉ trả **dòng mở** (`openQuantity`, `trackingPolicy`, `uomCode`) · `GOODS_RECEIPT_READ` |
| Tạo nháp | `POST /v1/goods-receipts` — `{purchaseOrderId, receivedDate, reference?, notes?, lines[1..500]{purchaseOrderLineId, receivedQuantity, lotCode?, expiresOn?}}` · `Idempotency-Key` tuỳ chọn | **201** `DRAFT`, mã `GR-000001`…, **không đụng tồn kho**. Đã kiểm sớm: cộng dồn theo dòng PO ≤ mở, `lotCode` bắt buộc/cấm theo loại, mã lô mới (không phân biệt hoa thường), `expiresOn` > hôm nay, `receivedDate` ≤ hôm nay |
| Sửa nháp | `PATCH /v1/goods-receipts/{id}` — `{expectedVersion, receivedDate, reference?, notes?, lines[]{id?, …}}` | chỉ `DRAFT`, thay toàn bộ dòng, `id` giữ dòng cũ |
| Post | `POST /v1/goods-receipts/{id}/post` — `{expectedVersion}` · quyền `PERM_GOODS_RECEIPT_POST` | `DRAFT → POSTED`; **kiểm lại tất cả dưới khoá PO** — nháp không giữ chỗ. Hai phiếu tranh cùng lượng mở: một thắng, một `409 PLANNED_QUANTITY_EXCEEDED` field `lines[i].receivedQuantity` |
| Huỷ | `POST /v1/goods-receipts/{id}/cancel` — body tuỳ chọn `{cancelNote?, expectedVersion?}` | `DRAFT` ⇒ `CANCELLED`, không tồn kho. `POSTED` ⇒ đảo nhập (`REVERSAL`) như trước, chỉ `PERM_PURCHASE_ORDER_MANAGE` |
| Danh sách toàn nhà máy | `GET /v1/goods-receipts?companyId&plantId&purchaseOrderId?&warehouseId?&status?&search?&receivedDateFrom?&receivedDateTo?` · `X-Plant-Id` phải khớp | `search`: mã phiếu, mã PO, mã/tên NCC |
| Chi tiết | `GET /v1/goods-receipts/{id}` | `GOODS_RECEIPT_READ` **hoặc** `PURCHASE_ORDER_READ` |

Field mới trên phiếu: `code`, `version`, `receivedDate`, `reference`, `companyId`, `plantId`, `purchaseOrderCode`, `supplierId/Code/Name`,
`warehouseName`, `postedBy`, `cancelledBy`, `purchaseOrderStatus`, `purchaseOrderVersion`. Trên dòng: `trackingPolicy`, `uomCode`, `unitPrice`,
`currencyCode`, `expiresOn`, `lotStatus`, `movementIds`. `postedAt` = `null` khi nháp.

> 🔴 **Mọi lần nhận mua vào vùng chờ kiểm, cả đường cũ `POST /purchase-orders/{id}/goods-receipts`:** lô mới tạo `HOLD`; hàng không theo
> lô vào `qualityHoldQuantity`. Tồn **khả dụng không tăng** cho tới QC mua (phần 3). Đường cũ nêu lô có sẵn thì lô đó phải đang `HOLD`.
> 🔴 PO còn phiếu nhận `DRAFT` ⇒ huỷ/đóng PO trả `409 PENDING_GOODS_RECEIPT`.

### Nhập đơn bán hàng Excel — FE giai đoạn 6 phần 3 *(`G6c`, 2026-10-18)*

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Tải + kiểm tra | `POST /v1/data-import/batches` multipart `file`, `kind=so`, `companyId`, **`plantId`** · `Idempotency-Key` tuỳ chọn | **201** `READY`/`INVALID`, **0 đơn**. Mẫu `omniplant-so-v1`: sheet `Sales orders`, `_meta` A1 `version`/B1 `omniplant-so-v1`, A2 `locale`/B2 `en` hoặc `vi`\|`vi`; 7 cột `Group code, Customer code, Order date, Customer reference, Item code, Quantity, Due date` (tiếng Việt: `Mã nhóm đơn, Mã khách hàng, Ngày đặt, Tham chiếu khách hàng, Mã sản phẩm, Số lượng, Ngày cần`). ≤ 5 MiB, ≤ 500 dòng |
| Xác nhận | `POST /v1/data-import/batches/{id}/commit {expectedVersion}` · `Idempotency-Key` tuỳ chọn | Tạo **N đơn `DRAFT`** (N = `salesOrderCount`) trong một transaction hoặc 0. Gửi lại ⇒ cùng `createdIds`. Dữ liệu đổi ⇒ `409 IMPORT_REVALIDATION_FAILED` kèm batch `INVALID` |
| Lịch sử / chi tiết | `GET /v1/data-import/batches?companyId=&plantId=&kind=so` · `GET /v1/data-import/batches/{id}` | Của **chính** người tải, đúng nhà máy. Lỗi theo dòng ở `rows[].issues[]` (`field`, `column`, `code`, `message`) |

> 🔴 Ngày: chữ `yyyy-MM-dd` hoặc ô ngày thật của Excel (đọc theo hệ ngày của workbook). `20/10/2026`, ô có giờ, ô số trơn ⇒ lỗi dòng.
> 🔴 Mã nhóm **không** phải mã đơn và **không** phải khoá chống trùng: tải cùng file hai lần rồi commit cả hai là hai bộ nháp.
> Đường riêng `/sales-orders/v1/imports/*` FE đề xuất **không** có — dùng đường batch chung như item/BOM.

### Hoạch định theo dòng đơn + chuyển đổi một phần — FE giai đoạn 6 phần 2 *(`G6b`, 2026-10-18)*

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Run thương mại | `POST /v1/planning-runs/commercial` — `{companyId, plantId, horizonStartDate, horizonEndDate, salesOrderLineIds[1..200], itemWarehouseMappings[]?{itemId, demandWarehouseId, outputWarehouseId?}, includeRop?}` · `Idempotency-Key` tuỳ chọn | **201**; lập kế hoạch phần **chưa phủ** của dòng đơn; kỳ ≤ 90 ngày cả hai đầu; `includeRop=true` ⇒ 422 |
| Chi tiết run | `GET /v1/planning-runs/{id}` | thêm `runKind`, `sourceVersion`, `currentSourceState` (`CURRENT`/`STALE`, chỉ ở đây), `conversionVersion`, `inputSnapshot` |
| Dòng nhu cầu | `GET /v1/planning-runs/{id}/requirements` · `GET .../requirements/{lineId}` | thêm `sourceKind`, `sourceSalesOrderLineIds[]`, `parentRequirementLineIds[]`, `calculationState`, `supplySuggestionId`, `supplyType`, `exceptionState`, `messages[]`, `convertedQuantity`, `remainingConvertibleQuantity`; chi tiết thêm `conversions[]` |
| Chuyển đổi | `POST /v1/planning-runs/{id}/conversions` — `{expectedSourceVersion, expectedConversionVersion, entries[1..100]{requirementLineId, quantity}, supplierId?}` · `Idempotency-Key` tuỳ chọn | **201** `{conversionVersion, sourceVersion, documents[], conversions[]}`. Toàn MAKE (mỗi entry một WO `DRAFT`) hoặc toàn BUY (một PO `DRAFT`, `supplierId` bắt buộc). Không cần duyệt |
| Sổ chuyển đổi | `GET /v1/planning-runs/{id}/conversions?page&size` | |

> 🔴 Số MRP đổi cho **cả** run cũ: tồn an toàn chỉ cộng một lần mỗi vật tư–kho; nhu cầu linh kiện làm tròn **lên** 6 số lẻ. Hao hụt là `× (1 + tỉ lệ)`.
> 🔴 Nguồn đổi ⇒ `409 PLANNING_SOURCE_STALE` — chạy run mới. Đọc `remainingConvertibleQuantity`, không đọc `status` của đề xuất.

### Đơn bán hàng nối khách + giữ tồn cho đơn — FE giai đoạn 6 phần 1 *(`G6a`, 2026-10-17)*

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Tạo nháp (server cấp mã) | `POST /sales-orders/v1/drafts` — `{companyId, plantId, customerId, customerReference?, orderDate, note?, lines[1..500]{itemId, orderedQuantity, dueDate}}` | **201**, `orderNo` = `SO-000001`…; khách ACTIVE cùng Công ty; chỉ `FINISHED_GOOD`/`WIP`; tối đa 6 số lẻ; lỗi ngày ở `lines[i].dueDate` |
| Sửa nháp | `PATCH /sales-orders/v1/{id}/draft` — `{expectedVersion, customerId, customerReference?, orderDate, note?, lines[]}` | **Thay toàn bộ**: field vắng = xoá (khác `PATCH /{id}` cũ) |
| Xác nhận / huỷ | `POST .../{id}/confirm` body tuỳ chọn `{expectedVersion}` · `POST .../{id}/cancel` body tuỳ chọn `{expectedVersion?, reason}` | Huỷ trả hết tồn đơn đang giữ; gửi body thì `reason` bắt buộc |
| Danh sách | `GET /sales-orders/v1?…&search=&orderDateFrom/To=&dueDateFrom/To=` | `search` gồm mã đơn, tên/mã khách, tham chiếu; khoảng ngày cần theo **ngày cần sớm nhất**; mỗi dòng có `earliestDueDate`/`latestDueDate`/`lineCount` |
| Chi tiết | `GET /sales-orders/v1/{id}` | Mỗi dòng: `heldStockQuantity`, `workOrderCoveredQuantity`, `producedQuantity` (**null**), `qcAcceptedQuantity`, `deliveredQuantity` (**null** tới `G10b`; nay là tổng phiếu giao đã ghi sổ, `null` với đơn cũ), `uncoveredQuantity`, `metricsEvidence`; `linkedDocuments[]` (lệnh SX) |
| Giữ tồn | `GET .../{id}/lines/{lineId}/allocation-candidates?warehouseId=` · `POST .../{id}/reservations {expectedVersion, salesOrderLineId, warehouseId, lotId?, quantity}` · `POST .../{id}/reservations/{rid}/release {expectedVersion, reason?}` · `GET .../{id}/reservations` | Bỏ `lotId` ⇒ FEFO; response giữ/bỏ giữ trả `{reservations[], salesOrder}` (version mới); vượt phần chưa phủ ⇒ `409 PLANNED_QUANTITY_EXCEEDED` |
| Nhu cầu hoạch định | `GET /sales-orders/v1/planning-demands?…&uncoveredOnly=true` | Thêm `heldStockQuantity`, `workOrderCoveredQuantity`, `uncoveredQuantity` |

> 🔴 `uncoveredQuantity = max(0, ordered − max(qcAccepted, delivered) − heldStock − workOrderCovered)` (`G10b`; trước đó không có `delivered`). `fulfilledQuantity` = QC chấp nhận, **không** phải đã giao.
> 🔴 Tồn khả dụng ở mọi màn hình nay trừ cả phần đơn bán đang giữ; `GET /v1/inventory/reservations` có dòng `ownerType = "SALES_ORDER"` (field lệnh SX `null`).
> Đổi trạng thái lô đang bị đơn giữ ⇒ `409 RESERVED_STOCK` — bỏ giữ trước.

### BOM, quy trình, ca/lịch/trung tâm, công suất — FE giai đoạn 5 *(`G5`, 2026-10-16)*

| Việc | Endpoint | Ghi chú |
|---|---|---|
| Lưu nháp BOM một lần | `PUT /v1/companies/{companyId}/bom-drafts` — body `{bomId?, expectedVersion?, bomCode, parentItemId, revision?, outputQuantity, effectiveFrom?, description?, lines:[{lineNo, componentItemId, componentQuantity, scrapRate}]}` | **200**. Không `bomId` ⇒ nháp version mới; có `bomId` ⇒ thay toàn bộ nháp (`expectedVersion` bắt buộc). `componentQuantity` theo **lô** `outputQuantity`; server lưu `quantityPer = componentQuantity / outputQuantity`, cần > 6 số lẻ ⇒ `400`. Giữ `lineId` theo thành phần |
| Sao chép / version / ngừng BOM | `POST /v1/boms/{id}/copy {bomCode, revision?, effectiveFrom?, description?}` (**201**) · `GET /v1/boms/{id}/versions` · `POST /v1/boms/{id}/deactivate {expectedVersion}` | |
| Activate BOM | `POST /v1/boms/{id}/activate` body `{expectedVersion}` **tuỳ chọn** | `effectiveFrom` tương lai ⇒ `409 EFFECTIVE_DATE_FUTURE`; vật tư ngừng ⇒ `422 RESOURCE_INACTIVE` |
| List BOM | `GET /v1/companies/{companyId}/boms?search=&latestOnly=` | Field mới: `bomCode`, `versionNumber`, `outputQuantity`, `outputUomCode`, `effectiveFrom`, `version`; dòng có `componentQuantity`, `componentUomCode` |
| Lưu nháp quy trình cả đồ thị | `PUT /v1/companies/{companyId}/routing-drafts` — `{routingId?, expectedVersion?, code, name?, itemId, version?, effectiveFrom?, note?, operations:[{routingOperationId?, clientKey, sequence, name, workCenterId, setupMinutes, runMinutesPerUnit>0, predecessorClientKeys[]}]}` | Công đoạn trước phải có `sequence` nhỏ hơn. Một nháp mở/sản phẩm (`409 OPEN_DRAFT_EXISTS`). Khoá concurrency là `lockVersion` (`version` là nhãn) |
| Quy trình khác | `POST /v1/routings/{id}/copy` · `GET .../versions` · `POST .../deactivate {expectedVersion}` · list `?plantId=&workCenterId=&search=` | Quy trình vẫn theo **Công ty** |
| Ca / Lịch / Trung tâm | như cũ + `?search=` trên list, `expectedVersion?` trên PATCH | Ca trả `workingMinutes`. Ngừng khi còn tham chiếu `ACTIVE` ⇒ `422 REFERENCE_IN_USE` |
| Tải theo kỳ | `GET /v1/plants/{plantId}/capacity/loads?periodStart=&periodEnd=&workCenterId=` | Tối đa 90 ngày tính cả hai đầu. `availableMinutes = phút làm việc × capacityUnits` |
| Xem trước lịch | `POST /v1/plants/{plantId}/capacity/preview {routingId, quantity, requestedStart, periodStart, periodEnd}` | **Không ghi gì**. Tuần tự theo `sequence`, `elapsed = required / capacityUnits`. Không lịch ⇒ `409 SCHEDULING_UNAVAILABLE` |
| Điều chỉnh công đoạn | `POST /v1/work-orders/{woId}/operations/{opId}/schedule-adjustments {plannedStartAt, plannedEndAt?, reason (≥5), expectedVersion}` | Chồng công đoạn trước/sau ⇒ `409 OPERATION_SEQUENCE_CONFLICT`; WO không `RELEASED`/`IN_PROGRESS` hoặc công đoạn đã báo sản lượng ⇒ `409 STATE_CONFLICT` |
| Nhập BOM Excel | `POST /v1/data-import/batches` multipart `kind=bom` | Mẫu `omniplant-bom-v1`; `bomCount`; `createdIds` = một id mỗi BOM |

> 🔴 **Bảng công suất đổi số liệu:** tải chia theo phần giờ làm việc của công đoạn rơi vào từng ngày (trước đây dồn
> vào ngày bắt đầu); công đoạn bắt đầu trước `from` nay có mặt; đuôi ca đêm tính cho ngày nó chạy. Mỗi dòng thêm
> `inPeriodMinutes`.
> 🔴 Ca là **một** khung + giờ nghỉ (không có `workingIntervals[]`, không có hiệu lực — hiệu lực ở lịch); trung tâm
> **không** có `shiftIds[]`. Bảng ánh xạ đầy đủ: `BE_RESPONSE_GD_05_2026-10-16.md §2`.

---

### 5.CF — Bổ sung theo bộ Capstone 3 follow-up (`CF-*`, 2026-10-27)

| Phase | Endpoint | Thay đổi | Lỗi mới |
|---|---|---|---|
| CF-1 | `POST /v1/inventory/adjust` | Lô còn thuộc nguồn QC bị từ chối: lô còn phần chờ QC mua, lô còn phần chờ QC sản xuất, lô con bị QC (mua hoặc sản xuất) loại. Lô `HOLD` đặt tay (không phiếu nhận) và lô con đạt vẫn điều chỉnh được. Replay cùng key trả chứng từ cũ | `409 STATE_CONFLICT` |
| CF-2 | MRP (`/planning-runs`, `/planning-runs/commercial`), `POST /work-orders/{id}/reserve`, `.../material-reservations`, `.../material-issues` + `/approve`, `/material-issues`, `GET .../material-readiness`, `POST .../release` | Lô `AVAILABLE` có `expiresAt <= giờ server` không còn là tồn khả dụng của MRP (nhu cầu mua có thể tăng; `excludedLotCount` tính cả lô hết hạn), không giữ chỗ/xuất được. Readiness thêm `ineligibleReservedQuantity` + `blocker` (`RESERVED_LOT_INELIGIBLE`), `reservedPercent` chỉ tính phần đủ điều kiện; `reservedQuantity` không đổi nghĩa. Reservation cũ trên lô hết hạn không tự huỷ — dùng `release-reservations`. Replay cùng key trả phiếu cũ | `409 LOT_NOT_ELIGIBLE` (giữ chỗ/xuất/duyệt), `409 STATE_CONFLICT` + WO `BLOCKED` (release) |
| CF-3 | `GET /v1/inventory/balances/aggregate` | Thêm `pendingPurchaseQcQuantity`, `rejectedPurchaseQcQuantity`, `unclassifiedQualityHoldQuantity` (không clamp, có thể âm khi dữ liệu lệch), `qualityBreakdownKnown`. Chỉ giải thích `qualityHoldQuantity` của hàng **không lô** do phiếu nhận mua đã ghi sổ; phần chờ QC sản xuất không lô nằm ở `unclassified`. `qualityHoldQuantity` giữ nghĩa cũ (= pending + rejected + unclassified) | — |
| CF-4 | `POST /planning-runs`, `POST /planning-runs/commercial`, `GET /planning-runs/{id}/requirements`, **mới** `GET /v1/planning-runs/{runId}/pending-qc-supplies?requirementId&warehouseId&page&size` | Dòng nhu cầu thêm `pendingQcQuantity` + `pendingQcSourceCount` (0 khi không có hàng chờ QC; `null` = run chạy trước bản này). Endpoint mới liệt kê từng dòng phiếu nhận đang chờ QC lúc run chạy (`receiptCode`, `receiptVersion`, `pendingQuantity`, `uomCode`) — là ảnh chụp, không cập nhật khi QC quyết sau. Netting không đổi. Đề xuất **mua** có hàng chờ QC: `READY` → `WARNING` + thông điệp `PENDING_QC_SUPPLY`. Quyền: `PERM_MRP_READ` trên run | — |
| CF-5 | `POST /v1/work-orders/{id}/release`, `POST /v1/work-orders/{id}/schedule-preview`, `POST .../operations/{opId}/schedule-adjustments` (khi bỏ trống `plannedEndAt`), mọi response công đoạn | Lịch chia `capacityUnits` của trung tâm, giống `POST /v1/plants/{P}/capacity/preview`: 2 tổ ⇒ nửa thời gian làm việc; công đoạn trên lịch bắt đầu ở phút làm việc đầu tiên; giờ kết thúc làm tròn lên theo giây. Công đoạn thêm `capturedCapacityUnits`, `requiredResourceMinutes`, `activeElapsedMinutes` (`null` với công đoạn release trước bản này). Lệnh đã release không đổi lịch. Bảng công suất (`capacity-board`, `capacity/loads`) không đổi | — |
| CF-9 | Mọi đường tạo lô (`/goods-receipts/{id}/post`, nhận mua cũ, `/inventory/receive`, duyệt phiếu nhập thành phẩm, QC tách lô con) | Hai request **đồng thời** tạo cùng mã lô khác hoa thường (`LOT-X` / `lot-x`) của một vật tư: trước đây cả hai thành công; nay một thắng, một bị từ chối và rollback sạch (phiếu vẫn `DRAFT`). Cùng mã ở vật tư khác vẫn được | `409 LOT_CODE_ALREADY_EXISTS` |
| CF-10 | `PUT .../items/{I}/standard-cost`, `PUT /inventory/item-warehouse-settings`, `POST /planning/demands`, `POST /planning/production-estimates`, item–supplier, đường tạo PO/PR cũ + duyệt PR, routing (`operations[]`), `POST /sales-orders/v1` + `PATCH` (`lines[]`), `POST /material-issues`, WIP, `component-issues`, `output-completions` | Quá 6 số lẻ (kể cả số 0 thừa `1.1234560`) hoặc quá 13 chữ số nguyên trên field số thập phân: trước đây `200` rồi DB làm tròn / `422` không `errors[]`; nay chặn trước khi ghi. Số dạng chuỗi JSON `"1.1234567"` cùng kết quả | `400 VALIDATION_ERROR` + `errors[{field}]` (field lồng: `lines[1].orderedQuantity`) |
| CF-11 | `POST /plants/{P}/work-orders`, `PATCH /work-orders/{id}`, chuyển đổi MAKE | `componentLines[].requiredQuantity` làm tròn `HALF_UP` 6 số lẻ ngay trong response tạo/sửa (trước đây in 12 chữ số, đọc lại mới ra 6). Giá trị lưu không đổi. MRP làm tròn lên (`CEILING`) nên có thể cao hơn dòng lệnh SX tối đa `0.000001` | — |

## 6. Tham chiếu DTO

Chỉ liệt kê DTO trên luồng chính. Schema đầy đủ: Swagger UI.

### `WorkOrderResponse` (44 field)
```
workOrderId, companyId, plantId, plantCode, workOrderNo,
productItemId, productItemCode, productItemName, outputUom,
bomId, bomRevision, bomCapturedAt,
sourceRoutingId, sourceRoutingCode, sourceRoutingVersion, routingCapturedAt,
planningRunId, planningRunCode, planningProposalId,
outputWarehouseId, outputWarehouseCode,
plannedQuantity, completedQuantity, remainingQuantity,
actualGoodQuantity, actualScrapQuantity, actualReworkQuantity, availableToReceipt,
status, plannedStartAt, plannedEndAt, releasedAt,
executionStartedAt, executionCompletedAt, completedAt,
cancelledAt, cancelReason, blockedAt, blockReason, notes, createdAt, updatedAt,
componentLines[], operations[], allocations[], closedAt
```

🔴 **Ba cột số lượng dễ nhầm nhất — hiển thị sai là sai nghiệp vụ:**

| Field | Nghĩa |
|---|---|
| `plannedQuantity` | Kế hoạch |
| `actualGoodQuantity` | **Xưởng đã làm ra** (từ production execution) |
| `completedQuantity` | **Đã nhập kho** (từ receipt approve) |
| `availableToReceipt` | `actualGood − completed` = còn chờ nhập kho |
| `remainingQuantity` | `planned − completed` |

`componentLines[]`: `componentLineId, bomLineId, lineNo, componentItemId, componentItemCode,
componentItemName, uom, quantityPer, scrapRate, requiredQuantity, reservedQuantity, issuedQuantity,
remainingQuantity`

`allocations[]`: `allocationId, salesOrderLineId, salesOrderCode, allocatedQuantity,
fulfilledQuantity, uom, dueDate`

`operations[]`: `workOrderOperationId, sourceRoutingOperationId, sequence, name, workCenterCode,
setupMinutes, runMinutesPerUnit`

### `ProductionReceiptResponse` (34 field)
```
receiptId, code, workOrderId, workOrderCode, status, idempotencyKey,
outputTrackingMethod, itemId, itemSku, itemName, uom,
destinationWarehouseId, destinationWarehouseCode, lotId, lotNumber, serialId, serialNumber,
outputLotStatus,
quantity, stockMovementId, postedAt, note,
submittedAt, approvedAt, rejectedAt, rejectReason,
qcResult, qcReason, qcAt,
createdByUsername, approvedByUsername, qcByUsername,
traceId, sourceWipTraceIds[]
```

### `ProductionExecutionResponse` (27 field)
```
productionExecutionId, code, status, workOrderId, workOrderCode,
workOrderOperationId, operationSequence, operationName, workCenterCode,
goodQuantity, scrapQuantity, reworkQuantity, actualStartedAt, actualEndedAt,
operatorUserId, operatorUsername, traceId, notes, uom,
workOrderActualGoodQuantity, workOrderActualScrapQuantity, workOrderActualReworkQuantity,
workOrderAvailableToReceipt, workOrderRemainingGoodQuantity, workOrderCompletionPercent,
workOrderStatus, createdAt
```

### `MrpRequirementLineResponse` (24 field)
```
mrpRequirementLineId, mrpRunId, parentRequirementLineId, sourceDemandId,
itemId, itemSku, itemName, uom, warehouseId, warehouseCode, requirementLevel,
grossRequiredQuantity, availableQuantity, reservedQuantity, openSupplyQuantity,
safetyStockQuantity, projectedAvailableQuantity, netRequiredQuantity,
dueDate, requirementStatus, settingSource, excludedLotCount, note, createdAt
```

### `MaterialIssueResponse`
```
issueId, code, workOrderId, workOrderCode, status, idempotencyKey, traceId,
postedAt, createdByUsername, note, lines[]
```
`lines[]`: `issueLineId, componentLineId, reservationId, itemId, itemSku, itemName, uom,
warehouseId, warehouseCode, lotId, lotNumber, serialId, serialNumber, quantity, stockMovementId,
overIssue, overrideReason`

---

## 7. Enum

| Enum | Giá trị |
|---|---|
| `WorkOrderStatus` | `DRAFT` `PLANNED` `BLOCKED` `RELEASED` `IN_PROGRESS` `COMPLETED` `CANCELLED` `CLOSED` (P6, terminal — khoá hoàn toàn, chỉ vào được từ `COMPLETED` qua `POST .../close`) |
| `ProductionReceiptStatus` | `DRAFT` `PENDING_APPROVAL` `APPROVED` `REJECTED` `CANCELLED` |
| `SalesOrderStatus` | `DRAFT` `CONFIRMED` `IN_PRODUCTION` `PARTIALLY_FULFILLED` `FULFILLED` `CANCELLED` |
| `MaterialReservationStatus` | `ACTIVE` `RELEASED` `CONSUMED` `CANCELLED` |
| `MaterialIssueStatus` | `POSTED` `CANCELLED` |
| `QualityDispositionResult` | `AVAILABLE` `REJECTED` |
| `LotStatus` | `AVAILABLE` `HOLD` `REJECTED` `EXPIRED` |
| `TrackingMethod` | `NON_TRACKED` `LOT_TRACKED` `SERIAL_TRACKED` |
| `MrpRequirementStatus` | `COVERED` `SHORTAGE` `BOM_MISSING` `INVALID` |
| `SupplySuggestionStatus` | `DRAFT` `APPROVED` `REJECTED` `CONVERTED` |
| `SupplySuggestionExceptionState` | `READY` `WARNING` `BLOCKED` |
| `PlanningMessageCode` | `MATERIAL_SHORTAGE` `MISSING_BOM` `MISSING_ROUTING` `SYSTEM_FALLBACK_USED` |
| `supplyType` (wire) | `MAKE` `BUY` |

> ⚠️ `supplyType` là giá trị **trên wire**; DB lưu `WORK_ORDER`/`PURCHASE_REQUISITION`. FE chỉ thấy
> `MAKE`/`BUY`.

---

## 8. Mã lỗi

### 400 — dữ liệu gửi lên sai
| `code` | Khi nào |
|---|---|
| `VALIDATION_ERROR` | `@Valid` fail — **có** mảng `errors[{field, message}]` để bind vào form |
| `MISSING_REQUIRED_FIELD`, `FIELD_TOO_LONG`, `FIELD_FORMAT_INVALID` | |
| `LOT_REQUIRED` | Item lot-tracked mà thiếu `lotNumber` |
| `SERIAL_REQUIRED` | Item serial-tracked mà thiếu `serialNumber`/`serialId` |
| `APPROVAL_REASON_REQUIRED` | Thiếu `reason` khi cancel WO / reject receipt / QC |
| `FILE_FORMAT` 🆕 `G3` | File nhập không phải `.xlsx` hợp lệ, hoặc chứa macro / ActiveX / liên kết ngoài |
| `TEMPLATE` 🆕 `G3` | Không đúng mẫu `omniplant-import-v1`: thiếu metadata, sai phiên bản/loại/ngôn ngữ, sai header, thừa cột |
| `EMPTY_FILE` / `ROW_LIMIT` 🆕 `G3` | Không có dòng dữ liệu / quá 500 dòng |

```jsonc
{
  "code": "VALIDATION_ERROR",
  "message": "quantity: must be greater than 0; …",
  "errors": [{ "field": "quantity", "message": "must be greater than 0" }]
}
```
> Một field có thể xuất hiện **nhiều lần** trong `errors` (nhiều constraint cùng vi phạm).

### 401 / 403
| `code` | Khi nào | FE làm gì |
|---|---|---|
| `TOKEN_EXPIRED` | Access token hết hạn | Gọi refresh, retry |
| `TOKEN_REVOKED` / `TOKEN_MALFORMED` | Token bị logout / hỏng | Về login |
| `AUTHENTICATION_REQUIRED` (`Sửa 2026-08-06`) | **Không** gửi `Authorization` header nào cả (khác `TOKEN_MALFORMED` — đó là "có gửi nhưng hỏng"). Nếu gặp mã này ở một request mà FE tin là đã gắn token, kiểm tra lại Network tab: header có thật sự tới được backend không | Về login (cùng cách xử lý `TOKEN_MALFORMED`, chỉ khác nguyên nhân để debug) |
| `REFRESH_TOKEN_EXPIRED` | Refresh token hỏng | Về login |
| `TOKEN_REUSE_DETECTED` | Refresh token đã rotate bị dùng lại ⇒ **mọi phiên đã bị thu hồi** | Về login + báo bảo mật. Xem lưu ý serialize refresh ở §`POST /auth/refresh` |
| `SESSION_ABSOLUTE_TIMEOUT` | Phiên quá **30 ngày** kể từ login ⇒ thu hồi theo chính sách, **không** phải sự cố bảo mật | Về login với message trung tính. **Đừng** gộp message với 2 dòng trên |
| `RESET_TOKEN_INVALID` (`D8c`) | Token reset-password sai/hết hạn/đã dùng — một mã dùng chung cho cả ba trường hợp | Hiện lỗi chung, cho phép request forgot-password lại |
| `PASSWORD_SETUP_REQUIRED` (403) | Gửi lại mã SETUP mà thiếu/sai `previousId` | Quay về bước login bằng mật khẩu ban đầu |
| `CODE_INVALID` (400) | Mã sai (hoặc `challengeId` không tồn tại) — mỗi lần sai tốn một lượt | Giữ mã đã nhập, báo lỗi |
| `CODE_EXPIRED` / `CODE_USED` (422) | Mã hết hạn hoặc đã bị thay / đã dùng | Cho gửi lại mã |
| `CODE_ATTEMPTS` (429) | 5 lần sai trên một challenge. Có `Retry-After` | Hiện thời gian chờ, rồi xin mã mới |
| `RESEND_TOO_SOON` (429) | Gửi lại trước `resendAt`. Có `Retry-After` | Đếm ngược tới `resendAt` |
| `PASSWORD_POLICY` (422) | Mật khẩu mới không đạt policy hoặc trùng mật khẩu hiện tại. Có `errors[]` field `password` | Giữ form, báo ở field |
| `CURRENT_PASSWORD_INVALID` (422) | Mật khẩu hiện tại sai khi đổi mật khẩu | Báo ở field `currentPassword`; **không** kết thúc phiên |
| `SETUP_TOKEN_INVALID` (422) | Setup token sai / hết hạn / đã dùng (một mã chung cho cả ba) | Xin mã mới |
| `INVALID_CREDENTIALS` | Sai user/mật khẩu | Hiện lỗi chung, **không** nói field nào sai |
| `ACCOUNT_LOCKED` (423) | 5 lần sai liên tiếp → khoá 15 phút | |
| `ACCOUNT_INACTIVE` (403) | Tài khoản bị vô hiệu | |
| `PERMISSION_DENIED` (403) | Role thiếu quyền | Ẩn nút, không retry |

### 404 · `ENTITY_NOT_FOUND`

### 413 · `FILE_SIZE` 🆕 `G3`
File nhập vượt 5 MiB (giới hạn của đường nhập mẫu cố định), vượt 10 MB (giới hạn upload chung — trước `G3` trường hợp này trả nhầm **500**), hoặc giải nén ra hơn 25 MiB.

### 409 — xung đột trạng thái (nhóm quan trọng nhất)
| `code` | Khi nào |
|---|---|
| `STATE_CONFLICT` | Thao tác không hợp lệ với trạng thái hiện tại (chiếm đa số) |
| `INSUFFICIENT_AVAILABLE_STOCK` | Không đủ tồn khả dụng |
| `PLANNED_QUANTITY_EXCEEDED` | Vượt trần chứng từ (good > planned, nhập > availableToReceipt…) |
| `RESERVATION_EXCEEDED` | Xuất quá phần còn lại của reservation |
| `LOT_NOT_ELIGIBLE` | Lot sai trạng thái (vd QC lot không ở `HOLD`) |
| `SERIAL_NOT_ELIGIBLE` | Issue một serial không ở trạng thái `AVAILABLE` (vd đã `ISSUED`/`REJECTED`) |
| `MISSING_BOM` / `MISSING_ROUTING` | Thiếu master data `ACTIVE` |
| `IDEMPOTENCY_CONFLICT` | Cùng `Idempotency-Key` nhưng payload khác |
| `NO_CHANGE` 🆕 `G4` | Kiểm đếm bằng đúng số hiện tại / đổi lô sang trạng thái nó đang có — **không ghi gì** |
| `EFFECTIVE_DATE_FUTURE` 🆕 `G5` (409) | Activate BOM/quy trình có `effectiveFrom` ở tương lai |
| `OPEN_DRAFT_EXISTS` 🆕 `G5` (409) | Sản phẩm đã có nháp quy trình — mở nháp đó |
| `REFERENCE_IN_USE` 🆕 `G5` (**422**) | Ngừng ca/lịch/trung tâm còn được bản ghi `ACTIVE` dùng |
| `OPERATION_SEQUENCE_CONFLICT` 🆕 `G5` (409) | Điều chỉnh làm công đoạn chồng công đoạn trước/sau — không ghi gì |
| `SCHEDULING_UNAVAILABLE` 🆕 `G5` (409) | Xem trước không xếp được: trung tâm không có lịch `ACTIVE` hoặc hết giờ làm trong 180 ngày sau kỳ |
| `RESERVED_STOCK` 🆕 `G4` | Đổi trạng thái lô còn reservation, hoặc đếm xuống dưới lượng đã giữ cho WO — giải phóng reservation trước |
| **`IMPORT_REVALIDATION_FAILED`** 🆕 `G3` | Commit batch nhập mà dữ liệu đã đổi kể từ lúc kiểm tra. **Có `result`** = batch đã `INVALID` kèm lỗi mới. Không tạo master nào |
| **`DEFAULT_CONFLICT`** 🆕 | Lưu thiết lập kho với `replaceDefaults=false` khi kho khác đang giữ vai trò default. Không có gì được ghi — hỏi người dùng rồi gửi lại với `replaceDefaults=true` |
| `PRICE_LOCKED` 🆕 `G10a` | Bổ sung giá cho dòng đơn đã có giá (kể cả 0) — giá đã chốt không sửa |
| `SOURCE_UNKNOWN` 🆕 `G10b` | Đơn không có baseline giao hàng (xác nhận trước GĐ10) hoặc không có khách master — không giao / lập hoá đơn được |
| `PRICE_UNKNOWN` 🆕 `G10c` | Dòng đơn chưa có giá nên không lập hoá đơn được (không bao giờ tính 0) |
| `COST_SOURCE_STALE` 🆕 `G11` | Nguồn giá thành đã đổi từ lúc đọc / lúc tính run — tính thử lại (field `expectedSourceVersion` hoặc `runId`) |
| `CLOSE_BLOCKED` 🆕 `G11` | Checklist vận hành chưa đạt (field = mã kiểm tra, message = lý do), hoặc `close` cũ trên lệnh có cơ sở chi phí |
| `COST_DATA_INCOMPLETE` 🆕 `G11` | Thiếu cơ sở chi phí / giá / đơn giá / tiêu thụ / thời gian hợp lệ — không có số bù |
| `VALUATION_UNRECONCILED` 🆕 `G11` | Kho đầu ra có hàng/biến động không phải của lệnh này, hoặc giao không khớp phần QC |
| `PRICE_STALE` 🆕 `G10c` | Nền tính tiền hoá đơn đã đổi — xem trước lại, cho người dùng xác nhận số mới, lưu lại |
| `CONCURRENT_MODIFICATION` | Hai người sửa cùng bản ghi — **refetch rồi thử lại** |
| `RESOURCE_ALREADY_EXISTS`, `USERNAME_ALREADY_EXISTS`, `EMAIL_ALREADY_EXISTS` | Trùng khoá |
| `LOT_CODE_ALREADY_EXISTS` | Mã lô đã tồn tại cho vật tư đó |
| `ITEM_ALREADY_ISSUED`, `PRODUCTION_ORDER_CLOSED` | **Đã đặt chỗ, backend chưa từng trả** — đừng viết nhánh xử lý cho tới khi có thông báo |

### 422 — dữ liệu hợp lệ nhưng vi phạm nghiệp vụ
| `code` | Khi nào |
|---|---|
| **`RESOURCE_INACTIVE`** 🆕 | Một bản ghi được tham chiếu đang `INACTIVE` (công ty/nhà máy/kho/vật tư/nhà cung cấp/role/scope/work center/work calendar), hoặc kích hoạt bản ghi con khi cha còn `INACTIVE`. **Cách xử lý khác hẳn**: kích hoạt lại bản ghi đó, không phải sửa form |
| **`RESOURCE_SCOPE_MISMATCH`** 🆕 | Hai bản ghi trong request thuộc hai chủ khác nhau — vật tư khác công ty, kho khác nhà máy, lô/sê-ri khác vật tư. Cách xử lý: đổi lựa chọn trên form |
| **`ACTIVE_CHILDREN_EXIST`** 🆕 | Ngừng một nhà máy khi nó còn kho đang hoạt động — ngừng kho trước, backend không tự cascade |
| **`DOCUMENT_HAS_NO_LINES`** 🆕 | Chứng từ chưa có dòng con nào (BOM/quy trình chưa có dòng, import run không có dòng hợp lệ, scope chưa có resource) |
| `OPERATION_NOT_ALLOWED` | **[2026-08-23] Đã thu hẹp**: nay chỉ còn là catch-all cho dữ liệu đầu vào sai (sai `ItemType`, lot/serial không khớp, `dueDate` < `orderDate`, nhiều nhà cung cấp trong 1 PR…). Ba tình huống ở trên **trước đây cũng trả mã này** — nếu bạn đang rẽ nhánh theo nó, xem lại |
| `BUSINESS_RULE_VIOLATION` | Vi phạm quy tắc nghiệp vụ chung — **[2026-08-23] nay còn gồm ràng buộc CSDL không phải trùng khoá** (khoá ngoại, `CHECK`, `NOT NULL`). Trước đó những lỗi này bị trả nhầm là `RESOURCE_ALREADY_EXISTS` (409), khiến client tưởng là trùng dữ liệu và thử lại vô ích |
| `NEGATIVE_QUANTITY`, `BOM_CIRCULAR_REFERENCE` | |

> 🔴 **Ranh giới 409 vs 422**: 409 = "đúng dữ liệu, sai **trạng thái**" (thử lại sau có thể được);
> 422 = "**dữ liệu** sai" (phải sửa input/master data). Đừng gộp hai nhóm này vào một thông báo.

### 429 · `RATE_LIMIT_EXCEEDED` — đọc header `Retry-After` (giây)

### 5xx · `INTERNAL_SERVER_ERROR`

🔴 **Cạm bẫy đã kiểm chứng trên server thật:** gõ **sai đường dẫn** cũng trả `500`, **không** phải `404`.

```
GET /api/v1/khong-ton-tai  → 500 {"code":"INTERNAL_SERVER_ERROR"}
```

Đây là lỗi backend đã biết (`NoHandlerFoundException` rơi vào catch-all). ⇒ Gặp `500`, **kiểm tra lại
URL trước** khi kết luận server hỏng; đối chiếu với `/v3/api-docs`. Khi báo lỗi thật cho backend, gửi
kèm `X-Trace-Id` từ response header.

---

## 9. Những chỗ lệch so với spec FE

Đối chiếu `docs/fe-spec-omniplant.md`. **Tất cả đều là quyết định có chủ đích** — không phải thiếu sót.

### 9.1 Khác về kiểu / tên
| Spec ghi | Thực tế | Ghi chú |
|---|---|---|
| `id: number` | **UUID chuỗi** | Quyết định 2026-07-26. FE đổi TS type sang `string` |
| Proposal lồng `/planning-runs/{id}/proposals/{pid}/…` | **phẳng** `/supply-suggestions/{id}/…` | Tên endpoint là phần *đề xuất* của spec |
| WO list `/work-orders?plantId=` | `/plants/{plantId}/work-orders` | |
| `demandLineIds` **bắt buộc** | **tuỳ chọn** | Bỏ trống ⇒ tự quét theo horizon |
| Execution "Mã WIP" | `code` prefix **`PE-`** | `wip_transactions` là bảng khác, không có code |
| Sales order `orderNo` vs `salesOrderCode` | **cả hai**, cùng một cột | CRUD dùng `orderNo`; màn Planning dùng `salesOrderCode` |

### 9.2 Field spec liệt kê nhưng backend **chưa có**
| Field | Vì sao |
|---|---|
| `sourceBomCode`, BOM `outputQuantity` (§2.4, §3.3) | `BomHeader` chưa có khái niệm "mã BOM"; `outputQuantity` sẽ đổi công thức nổ BOM |
| `predecessorOperationIds` (§3.3) | Chỉ có nghĩa khi có scheduling/CRP — chưa làm |
| `childBom` lồng trong WO snapshot (§3.3) | WO snapshot là **direct-only**; MRP nổ cấp sâu thành **work order riêng**. Cần cây BOM → gọi `GET /boms/{bomId}/tree` |
| `PURCHASING_DEFERRED` (§8.1) | Code này giả định purchasing ngoài MVP; backend có module purchasing đầy đủ |

### 9.3 DTO ngoài luồng spec vẫn dùng tên cũ
7 DTO trên luồng spec đã đổi sang `itemSku`/`lotNumber`. **17 DTO ngoài luồng** (inventory,
purchasing, BOM tree) vẫn dùng `itemCode`/`lotCode` — spec không mô tả màn hình cho chúng.
⇒ Khi map response, **kiểm tra tên field theo từng endpoint**, đừng giả định toàn hệ thống giống nhau.

### 9.4 Ngữ nghĩa quan trọng nhất — đọc kỹ
🔴 **Production **execution** (không phải receipt) mới làm work order tiến triển.**
Spec cũ có thể khiến FE nghĩ receipt là thứ hoàn thành WO. Thực tế:

| | Ai làm | Ảnh hưởng |
|---|---|---|
| `POST /production-executions` | Operator | `actualGoodQuantity` ↑, WO → `IN_PROGRESS` → `COMPLETED` |
| `POST /production-receipts/{rid}/approve` | Người duyệt | `completedQuantity` ↑, tồn kho ↑ (lot `HOLD`) |
| `POST …/qc-disposition` = `AVAILABLE` | QC | Lot dùng được, Sales Order được fulfill |

---

## 10. Checklist wiring

- [ ] Envelope: đọc `code`, **không** parse `message`; nhớ `result`/`errors` **biến mất** khi null
- [ ] Lưu đủ **3** giá trị sau login: `accessToken`, `refreshToken`, **`tokenId`**
- [ ] Interceptor 401 → refresh **một lần** (chống gọi song song) → retry; refresh lỗi → về login
- [ ] Mọi POST đụng tồn kho gửi `Idempotency-Key`; **sinh key mới** khi user sửa form
- [ ] Quantity parse bằng decimal library, **không** dùng float
- [ ] `size` tối đa 100 — phân trang tôn trọng trần này
- [ ] Gửi `planningDemandId` (không phải `salesOrderLineId`) vào `demandLineIds`
- [ ] Dùng `projectedAvailableQuantity` từ API, **không** tự cộng `available + openSupply`
- [ ] Sau `POST /reserve` **luôn** gọi `/material-readiness` — reserve thành công một phần là bình thường
- [ ] Release lỗi → refetch WO để hiển thị `BLOCKED` + `blockReason`
- [ ] `actualStartedAt`/`actualEndedAt` **bắt buộc** khi báo sản lượng
- [ ] Cancel WO **bắt buộc** `{reason}`
- [ ] Item `lotTracked` → ô `lotNumber` bắt buộc ở màn receipt
- [ ] Hiển thị đúng `onHand` vs `available` sau approve (lot `HOLD` ⇒ available vẫn 0)
- [ ] Phân biệt `actualGoodQuantity` (đã làm ra) vs `completedQuantity` (đã nhập kho)
- [ ] Disable nút convert khi `convertedWorkOrderId != null`; disable khi `exceptionState = BLOCKED`
- [ ] `403 PERMISSION_DENIED` → ẩn nút theo role, không retry
- [ ] `409 CONCURRENT_MODIFICATION` → refetch rồi thử lại
- [ ] Log `X-Trace-Id` từ response lỗi để đối chiếu với backend

---

## Phụ lục — smoke test bằng curl

```bash
BASE=http://localhost:8080/api/v1

# 1. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/v1/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<mật khẩu>"}' | jq -r '.result.accessToken')

# 2. Company → Plant → Warehouse
curl -s $BASE/companies -H "Authorization: Bearer $TOKEN" | jq '.result.content[0]'

# 3. Work order của plant
curl -s "$BASE/plants/<plantId>/work-orders?size=5" -H "Authorization: Bearer $TOKEN" | jq '.result'

# 4. Candidate báo sản lượng
curl -s "$BASE/production-executions/candidates?plantId=<plantId>" \
  -H "Authorization: Bearer $TOKEN" | jq '.result.content'
```

# API Guide cho Frontend — Manufacturing ERP

> **Nguồn của tài liệu này:** đọc trực tiếp từ **code đang chạy** (controller + DTO + enum + error code),
> rồi đối chiếu ngược với `docs/fe-spec-omniplant.md`. Chỗ nào backend **lệch** so với spec FE đều
> được ghi rõ ở [§9](#9-những-chỗ-lệch-so-với-spec-fe). Toàn bộ luồng ở [§4](#4-luồng-sản-xuất-đầu-cuối)
> đã được kiểm chứng tự động bằng `ProductionFlowE2EIT` (chạy thật trên Postgres, không mock).
>
> Cập nhật: 2026-08-01 · Tương ứng code sau phase `F10` · Migration mới nhất `V40`

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
| **API prefix** | `/api/v1` |
| **Swagger UI** | `http://localhost:8080/swagger-ui.html` |
| **OpenAPI JSON** | `http://localhost:8080/v3/api-docs` |
| **Health check** | `http://localhost:8080/actuator/health` |

> Swagger UI là **nguồn chính xác nhất tại thời điểm chạy** — tài liệu này giải thích *cách dùng* và
> *cạm bẫy*, Swagger cho bạn schema sinh tự động từ code. Dùng cả hai.

Endpoint **không cần token**: `/api/v1/auth/**`, `/actuator/health`, `/actuator/info`,
`/v3/api-docs/**`, `/swagger-ui/**`. Mọi endpoint `/api/v1/**` khác đều cần `Authorization: Bearer`.

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
6 warehouse + 3 account. **Mật khẩu của cả ba: `Admin@123`.**

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

---

## 3. Đăng nhập & giữ phiên

### `POST /api/v1/auth/login`

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

### `POST /api/v1/auth/refresh`

Gọi khi access token sắp/đã hết hạn (bắt `401 TOKEN_EXPIRED`).

```jsonc
{ "refreshToken": "b3f1…", "tokenId": "9c2e…", "deviceId": "web-chrome-01" }
```

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

### `POST /api/v1/auth/logout` — body `{refreshToken, tokenId}` · `POST /api/v1/auth/logout-all` — không body

### `GET /api/v1/auth/me` — profile, permissions, plant & access scope sau login

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

`ItemResponse.lotTracked` quyết định màn hình receipt có **bắt buộc** nhập `lotNumber` hay không.

---

### Bước 1 — Tạo & xác nhận Sales Order

**`POST /api/v1/sales-orders`** → `201`

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

**`POST /api/v1/sales-orders/{salesOrderId}/confirm`** → `200`, status → `CONFIRMED`.
Đây mới là lúc mỗi dòng đơn sinh **một** planning demand.

> Huỷ đơn: `POST /{id}/cancel` — chỉ được từ `DRAFT`/`CONFIRMED`. Từ `IN_PRODUCTION` trở đi →
> `409 STATE_CONFLICT` (đã có work order, có thể đã xuất vật tư).

---

### Bước 2 — Màn hình Planning: chọn demand rồi chạy MRP

**`GET /api/v1/sales-orders/planning-demands?plantId={…}&horizonEnd=2026-09-30`** → mảng trần

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

**`POST /api/v1/work-orders/{workOrderId}/reserve`** → `201`, trả **mảng** reservation (FEFO tự động).

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
> body `{componentLineId, warehouseId, lotId?, quantity}`.
> Huỷ giữ: `DELETE /work-orders/{id}/material-reservations/{reservationId}`.

---

### Bước 5 — Release

**`POST /api/v1/work-orders/{workOrderId}/release`** → `200`, status → `RELEASED`.

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
> Xuất vượt cần quyền `PERM_MATERIAL_ISSUE_OVERRIDE`; thiếu `overrideReason` → `422`.

Xem lịch sử: `GET /api/v1/material-issues?plantId={…}&workOrderId={…}` (workOrderId tuỳ chọn).
`MaterialIssueResponse.code` là số chứng từ dạng `MI-3F2A9C01`.

| Lỗi | Nghĩa |
|---|---|
| `409 RESERVATION_EXCEEDED` | Xuất quá phần còn lại của reservation |
| `409 INSUFFICIENT_AVAILABLE_STOCK` | Kho không đủ |
| `409 STATE_CONFLICT` | WO không ở `RELEASED`/`IN_PROGRESS` |

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
  "quantity": 10,
  "note": "…"
}
```

Từ chối: `POST .../{rid}/reject` body `{"reason": "…"}` (reason bắt buộc).

🔴 **Sau `approve`, hàng đã vào kho nhưng CHƯA dùng được**: lot ở trạng thái `HOLD`, `onHand` tăng
nhưng `available` **vẫn = 0**. Màn hình tồn kho phải hiển thị đúng điều này.

| Lỗi | Nghĩa |
|---|---|
| `400 LOT_REQUIRED` | Item lot-tracked mà không gửi `lotNumber` |
| `409 PLANNED_QUANTITY_EXCEEDED` | Nhập vượt `availableToReceipt` (đã trừ receipt đang mở) |
| `409 STATE_CONFLICT` | Sai trạng thái receipt (vd approve một receipt đang `DRAFT`) |

---

### Bước 9 — QC disposition (mắt xích cuối)

**`POST /api/v1/work-orders/{id}/production-receipts/{rid}/qc-disposition`** → `200`

```jsonc
{ "result": "AVAILABLE", "reason": "Đạt kiểm tra ngoại quan" }   // hoặc "REJECTED"
```

| Kết quả | Tác động |
|---|---|
| `AVAILABLE` | Lot `HOLD` → `AVAILABLE` ⇒ hàng dùng được ⇒ **Sales Order được fulfill** ⇒ status đơn roll-up sang `PARTIALLY_FULFILLED`/`FULFILLED` |
| `REJECTED` | Lot → `REJECTED` (hoặc rút hàng bằng `ADJUST_OUT` nếu item không lot-tracked). **Không** fulfill |

- Receipt **vẫn giữ** `status = "APPROVED"` sau QC; kết quả nằm ở `qcResult`/`qcReason`/`qcAt`.
- Chỉ QC được **một lần** — gọi lần hai → `409 STATE_CONFLICT`.
- `reason` **bắt buộc** cho cả hai kết quả.

---

### Bước 10 — Hiển thị kết quả

| Muốn xem | Endpoint |
|---|---|
| WO đầy đủ (kèm `componentLines`, `operations`, `allocations`) | `GET /api/v1/work-orders/{id}` |
| Tồn kho | `GET /api/v1/inventory/balances?warehouseId={…}&itemId={…}` (`warehouseId` **bắt buộc**, `itemId` tuỳ chọn) |
| Đơn hàng đã giao tới đâu | `GET /api/v1/sales-orders/{id}` → `lines[].fulfilledQuantity` |
| Chênh lệch kế hoạch/thực tế | `GET /api/v1/work-orders/{id}/variance` |

> ✅ **[`C2-4`, 2026-08-05] `variance` nay trả đủ 4 khối:** `materialLines` + `outputVariance` +
> `wipSummary` (scrap/rework) + **`timeVariance`** (`plannedMinutes`/`actualMinutes`/`varianceMinutes`).
> `plannedMinutes` = `Σ (setupMinutes + runMinutesPerUnit × plannedQuantity)` trên mọi operation của
> routing snapshot; `actualMinutes` = `Σ` thời lượng mọi production execution **đã kết thúc**
> (`actualEndedAt` khác `null`) — execution đang chạy dở không tính vào tổng.

---

## 5. Tham chiếu endpoint theo màn hình

### Auth
| Việc | Endpoint | Quyền |
|---|---|---|
| Login | `POST /auth/login` | permitAll |
| Refresh token | `POST /auth/refresh` | permitAll |
| Logout / logout tất cả thiết bị | `POST /auth/logout` · `/auth/logout-all` | permitAll |
| Profile + permissions + scope hiện tại | `GET /auth/me` | đã đăng nhập (không cần `PERM_*` cụ thể) |

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
| Danh sách (lọc `status`, `productItemId`, `search`) | `GET /plants/{plantId}/work-orders` | `PERM_WORK_ORDER_READ` |
| Chi tiết | `GET /work-orders/{id}` | `PERM_WORK_ORDER_READ` |
| Tạo thủ công | `POST /plants/{plantId}/work-orders` | `PERM_WORK_ORDER_MANAGE` |
| Sửa (chỉ khi DRAFT) | `PATCH /work-orders/{id}` | `PERM_WORK_ORDER_MANAGE` |
| Lên lịch | `POST /work-orders/{id}/plan` | `PERM_WORK_ORDER_MANAGE` |
| Release | `POST /work-orders/{id}/release` | `PERM_WORK_ORDER_MANAGE` |
| Huỷ (**bắt buộc** `{reason}`) | `POST /work-orders/{id}/cancel` | `PERM_WORK_ORDER_MANAGE` |
| Sẵn sàng vật tư | `GET /work-orders/{id}/material-readiness` | `PERM_WORK_ORDER_READ` |
| Variance | `GET /work-orders/{id}/variance` | `PERM_WORK_ORDER_VARIANCE_READ` |

`search` là free-text trên **số WO + SKU thành phẩm**.

### Reservation / Issue / Execution / Receipt
| Việc | Endpoint | Quyền |
|---|---|---|
| Auto reserve (FEFO) | `POST /work-orders/{id}/reserve` | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Reserve thủ công | `POST /work-orders/{id}/material-reservations` | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Danh sách reservation | `GET /work-orders/{id}/material-reservations` | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Huỷ reservation | `DELETE /work-orders/{id}/material-reservations/{rid}` | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Xuất 1 dòng | `POST /material-issues` | `PERM_MATERIAL_ISSUE_MANAGE` |
| Xuất nhiều dòng / override | `POST /work-orders/{id}/material-issues` | `PERM_MATERIAL_ISSUE_MANAGE` (+ `_OVERRIDE`) |
| Lịch sử xuất (plant) | `GET /material-issues?plantId=&workOrderId=` | `PERM_MATERIAL_ISSUE_MANAGE` |
| Candidate báo sản lượng | `GET /production-executions/candidates?plantId=` | `PERM_PRODUCTION_EXECUTION_READ` |
| Báo sản lượng | `POST /work-orders/{id}/production-executions` | `PERM_PRODUCTION_EXECUTION_MANAGE` |
| Lịch sử báo sản lượng | `GET /production-executions?workOrderId=` | `PERM_PRODUCTION_EXECUTION_READ` |
| Candidate nhập kho | `GET /production-receipts/candidates?plantId=` | `PERM_PRODUCTION_RECEIPT_MANAGE` |
| Tạo/submit receipt | `POST /work-orders/{id}/production-receipts[/{rid}/submit]` | `PERM_PRODUCTION_RECEIPT_MANAGE` |
| Duyệt/từ chối receipt | `POST …/{rid}/approve` · `/reject` | `PERM_PRODUCTION_RECEIPT_APPROVE` |
| QC | `POST …/{rid}/qc-disposition` | `PERM_QUALITY_DISPOSITION` |
| Danh sách receipt (plant) | `GET /production-receipts?plantId=&status=` | `PERM_PRODUCTION_RECEIPT_MANAGE` |

> Nhận `403 PERMISSION_DENIED` nghĩa là **role của user đang đăng nhập** thiếu quyền đó, không phải
> API sai. Vai trò: `ADMIN` (cấu hình hệ thống) · `MANAGER` (duyệt, lập kế hoạch) · `OPERATOR` (thực
> thi: xuất vật tư, báo sản lượng, tạo receipt — **không** duyệt receipt, **không** QC).

### Sales Order · Inventory · Master data
| Việc | Endpoint |
|---|---|
| Danh sách đơn | `GET /sales-orders?companyId=&plantId=&status=` |
| Tạo / xác nhận / huỷ | `POST /sales-orders` · `/{id}/confirm` · `/{id}/cancel` |
| Sửa (chỉ khi `DRAFT`) | `PATCH /sales-orders/{id}` — full-replace `lines[]`, `expectedVersion` bắt buộc (`C2-4`) |
| Tồn kho | `GET /inventory/balances?warehouseId=` (bắt buộc) `&itemId=` (tuỳ chọn) |
| Lịch sử movement | `GET /inventory/movements?warehouseId=` (**bắt buộc**) `&itemId=&lotId=` (tuỳ chọn) |
| Nhập/xuất/điều chỉnh thủ công | `POST /inventory/receive` · `/issue` · `/adjust` |
| Dashboard tồn kho | `GET /reports/inventory-dashboard` |
| Cảnh báo tồn thấp | `GET /reports/low-stock` |
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
> `COMPLETED` của Work Center đó, **bất kể** filter `status` đang lọc dòng nào — filter chỉ ảnh
> hưởng dòng hiển thị, không ảnh hưởng số utilization.
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

### Access Control — Role / Scope lifecycle + Assignments *(`C2-4`, 2026-08-05)*

| Việc | Endpoint |
|---|---|
| Role: xem / sửa `name`+`description` | `GET/PATCH /access/roles/{roleId}` |
| Role: activate / deactivate | `POST /access/roles/{roleId}/activate` · `/deactivate` |
| Scope: xem / sửa `name`+`description` | `GET/PATCH /access/scopes/{scopeId}` |
| Scope: activate / deactivate | `POST /access/scopes/{scopeId}/activate` · `/deactivate` |
| Tra cứu assignment | `GET /access/assignments?userId=&roleId=&scopeId=&page=&size=` (cả 3 filter tuỳ chọn) |

> 🔴 **Role/Scope dùng verb `POST .../activate`+`.../deactivate`, KHÁC với BOM/Routing/UOM ở trên**
> (`DELETE` = deactivate). Đây là 2 pattern verb khác nhau tồn tại song song trong cùng API, không
> phải lỗi — đi theo đúng 2 verb FE liệt kê tường minh cho nhóm này.
> **Role `is_system = true` (ADMIN/MANAGER/OPERATOR) không bao giờ deactivate được**, kể cả bởi
> `admin` — trả `422 OPERATION_NOT_ALLOWED`. `code`/`is_system`/`scopeType` là **immutable**, `PATCH`
> chỉ nhận `name`/`description`. Cùng permission `PERM_ACCESS_MANAGE` cho cả 9 endpoint — **không**
> có permission mới.

---

## 6. Tham chiếu DTO

Chỉ liệt kê DTO trên luồng chính. Schema đầy đủ: Swagger UI.

### `WorkOrderResponse` (43 field)
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
componentLines[], operations[], allocations[]
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

### `ProductionReceiptResponse` (32 field)
```
receiptId, code, workOrderId, workOrderCode, status, idempotencyKey,
outputTrackingMethod, itemId, itemSku, itemName, uom,
destinationWarehouseId, destinationWarehouseCode, lotId, lotNumber, outputLotStatus,
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
warehouseId, warehouseCode, lotId, lotNumber, quantity, stockMovementId, overIssue, overrideReason`

---

## 7. Enum

| Enum | Giá trị |
|---|---|
| `WorkOrderStatus` | `DRAFT` `PLANNED` `BLOCKED` `RELEASED` `IN_PROGRESS` `COMPLETED` `CANCELLED` |
| `ProductionReceiptStatus` | `DRAFT` `PENDING_APPROVAL` `APPROVED` `REJECTED` `CANCELLED` |
| `SalesOrderStatus` | `DRAFT` `CONFIRMED` `IN_PRODUCTION` `PARTIALLY_FULFILLED` `FULFILLED` `CANCELLED` |
| `MaterialReservationStatus` | `ACTIVE` `RELEASED` `CONSUMED` `CANCELLED` |
| `MaterialIssueStatus` | `POSTED` `CANCELLED` |
| `QualityDispositionResult` | `AVAILABLE` `REJECTED` |
| `LotStatus` | `AVAILABLE` `HOLD` `REJECTED` `EXPIRED` |
| `TrackingMethod` | `NON_TRACKED` `LOT_TRACKED` |
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
| `APPROVAL_REASON_REQUIRED` | Thiếu `reason` khi cancel WO / reject receipt / QC |

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
| `REFRESH_TOKEN_EXPIRED` | Refresh token hỏng | Về login |
| `TOKEN_REUSE_DETECTED` | Refresh token đã rotate bị dùng lại ⇒ **mọi phiên đã bị thu hồi** | Về login + báo bảo mật. Xem lưu ý serialize refresh ở §`POST /auth/refresh` |
| `SESSION_ABSOLUTE_TIMEOUT` | Phiên quá **30 ngày** kể từ login ⇒ thu hồi theo chính sách, **không** phải sự cố bảo mật | Về login với message trung tính. **Đừng** gộp message với 2 dòng trên |
| `INVALID_CREDENTIALS` | Sai user/mật khẩu | Hiện lỗi chung, **không** nói field nào sai |
| `ACCOUNT_LOCKED` (423) | 5 lần sai liên tiếp → khoá 15 phút | |
| `ACCOUNT_INACTIVE` (403) | Tài khoản bị vô hiệu | |
| `PERMISSION_DENIED` (403) | Role thiếu quyền | Ẩn nút, không retry |

### 404 · `ENTITY_NOT_FOUND`

### 409 — xung đột trạng thái (nhóm quan trọng nhất)
| `code` | Khi nào |
|---|---|
| `STATE_CONFLICT` | Thao tác không hợp lệ với trạng thái hiện tại (chiếm đa số) |
| `INSUFFICIENT_AVAILABLE_STOCK` | Không đủ tồn khả dụng |
| `PLANNED_QUANTITY_EXCEEDED` | Vượt trần chứng từ (good > planned, nhập > availableToReceipt…) |
| `RESERVATION_EXCEEDED` | Xuất quá phần còn lại của reservation |
| `LOT_NOT_ELIGIBLE` | Lot sai trạng thái (vd QC lot không ở `HOLD`) |
| `MISSING_BOM` / `MISSING_ROUTING` | Thiếu master data `ACTIVE` |
| `IDEMPOTENCY_CONFLICT` | Cùng `Idempotency-Key` nhưng payload khác |
| `CONCURRENT_MODIFICATION` | Hai người sửa cùng bản ghi — **refetch rồi thử lại** |
| `RESOURCE_ALREADY_EXISTS`, `USERNAME_ALREADY_EXISTS`, `EMAIL_ALREADY_EXISTS` | Trùng khoá |
| `ITEM_ALREADY_ISSUED`, `PRODUCTION_ORDER_CLOSED` | |

### 422 — dữ liệu hợp lệ nhưng vi phạm nghiệp vụ
| `code` | Khi nào |
|---|---|
| `OPERATION_NOT_ALLOWED` | Master data sai (item `INACTIVE`, sai `ItemType`, khác company/plant), hoặc chứng từ **chưa có dòng nào** |
| `BUSINESS_RULE_VIOLATION`, `NEGATIVE_QUANTITY`, `BOM_CIRCULAR_REFERENCE` | |

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
TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<mật khẩu>"}' | jq -r '.result.accessToken')

# 2. Company → Plant → Warehouse
curl -s $BASE/companies -H "Authorization: Bearer $TOKEN" | jq '.result.content[0]'

# 3. Work order của plant
curl -s "$BASE/plants/<plantId>/work-orders?size=5" -H "Authorization: Bearer $TOKEN" | jq '.result'

# 4. Candidate báo sản lượng
curl -s "$BASE/production-executions/candidates?plantId=<plantId>" \
  -H "Authorization: Bearer $TOKEN" | jq '.result.content'
```

# Hướng dẫn FE chuyển sang URL API có `/v1`

> Áp dụng từ ngày 2026-08-17. Tài liệu này là nguồn tham chiếu cho thay đổi URL sau khi backend đưa
> `/api` vào `server.servlet.context-path` và đưa `/v1` xuống mapping của từng API.

## 1. Quy tắc ghép URL mới

URL public được ghép theo thứ tự:

```text
http://localhost:8080 + /api + controller base path + method path
```

Ví dụ:

```text
Controller không có base path:
/api + /v1/companies
= /api/v1/companies

AuthController:
/api + /auth + /v1/login
= /api/auth/v1/login
```

Vì vậy, **không được mặc định mọi endpoint đều bắt đầu bằng `/api/v1`**. Có 5 nhóm đặt `/v1`
sau base path của controller.

## 2. Năm nhóm FE bắt buộc phải sửa

| Nhóm | URL cũ | URL mới |
|---|---|---|
| Authentication | `/api/v1/auth/**` | `/api/auth/v1/**` |
| Admin user | `/api/v1/admin/users/**` | `/api/admin/users/v1/**` |
| User management | `/api/v1/users/**` | `/api/users/v1/**` |
| Access control/RBAC | `/api/v1/access/**` | `/api/access/v1/**` |
| Sales order | `/api/v1/sales-orders/**` | `/api/sales-orders/v1/**` |

Không chạy replace toàn cục `/api/v1` thành `/api/.../v1`. Các endpoint ngoài 5 nhóm trên vẫn giữ
nguyên dạng `/api/v1/**`.

## 3. Endpoint chi tiết cần sửa

### 3.1 Authentication

| Method | URL mới |
|---|---|
| `POST` | `/api/auth/v1/login` |
| `POST` | `/api/auth/v1/refresh` |
| `POST` | `/api/auth/v1/logout` |
| `POST` | `/api/auth/v1/logout-all` |
| `GET` | `/api/auth/v1/me` |
| `POST` | `/api/auth/v1/forgot-password` |
| `POST` | `/api/auth/v1/reset-password` |

```diff
- POST /api/v1/auth/login
+ POST /api/auth/v1/login
```

### 3.2 Admin user

```diff
- PATCH /api/v1/admin/users/{userId}/unlock
+ PATCH /api/admin/users/v1/{userId}/unlock
```

### 3.3 User management

| Method | URL mới |
|---|---|
| `GET` | `/api/users/v1` |
| `POST` | `/api/users/v1` |
| `GET` | `/api/users/v1/{userId}` |
| `PATCH` | `/api/users/v1/{userId}` |
| `DELETE` | `/api/users/v1/{userId}` |
| `POST` | `/api/users/v1/{userId}/roles/{roleName}` |
| `DELETE` | `/api/users/v1/{userId}/roles/{roleName}` |

```diff
- GET /api/v1/users?size=100
+ GET /api/users/v1?size=100
```

### 3.4 Access control/RBAC

Tất cả API RBAC chuyển từ `/api/v1/access/...` sang `/api/access/v1/...`:

```text
GET,POST    /api/access/v1/roles
GET,PATCH   /api/access/v1/roles/{roleId}
POST        /api/access/v1/roles/{roleId}/activate
POST        /api/access/v1/roles/{roleId}/deactivate
GET         /api/access/v1/roles/{roleId}/permissions
POST,DELETE /api/access/v1/roles/{roleId}/permissions/{permissionId}

GET,POST    /api/access/v1/permissions

GET,POST    /api/access/v1/scopes
GET,PATCH   /api/access/v1/scopes/{scopeId}
POST        /api/access/v1/scopes/{scopeId}/activate
POST        /api/access/v1/scopes/{scopeId}/deactivate
GET,POST    /api/access/v1/scopes/{scopeId}/resources

GET,POST    /api/access/v1/assignments
DELETE      /api/access/v1/assignments/{assignmentId}
```

### 3.5 Sales order

| Method | URL mới |
|---|---|
| `GET` | `/api/sales-orders/v1` |
| `POST` | `/api/sales-orders/v1` |
| `GET` | `/api/sales-orders/v1/planning-demands` |
| `GET` | `/api/sales-orders/v1/{salesOrderId}` |
| `PATCH` | `/api/sales-orders/v1/{salesOrderId}` |
| `POST` | `/api/sales-orders/v1/{salesOrderId}/confirm` |
| `POST` | `/api/sales-orders/v1/{salesOrderId}/cancel` |

```diff
- POST /api/v1/sales-orders/{salesOrderId}/confirm
+ POST /api/sales-orders/v1/{salesOrderId}/confirm
```

## 4. Các endpoint không đổi URL public

Controller không có base path vẫn dùng `/api/v1/...`. FE không sửa những URL này.

| Nhóm | Ví dụ URL giữ nguyên |
|---|---|
| Organization | `/api/v1/companies`, `/api/v1/plants/{plantId}` |
| Inventory | `/api/v1/inventory/balances`, `/api/v1/items/{itemId}` |
| BOM/Routing | `/api/v1/boms/{bomId}`, `/api/v1/routings/{routingId}` |
| Planning | `/api/v1/planning-runs`, `/api/v1/planning/demands` |
| Purchasing | `/api/v1/purchase-orders`, `/api/v1/suppliers` |
| Work order | `/api/v1/work-orders/{workOrderId}` |
| Manufacturing execution | `/api/v1/production-executions`, `/api/v1/material-issues` |
| Reporting/Audit | `/api/v1/reports/low-stock`, `/api/v1/audit-logs` |
| Data import | `/api/v1/import-runs`, `/api/v1/import-profiles` |

## 5. Cấu hình Axios/HTTP client đề xuất

Đặt base URL ở `/api`, không đặt ở `/api/v1`, vì `/v1` không đứng cùng vị trí cho mọi controller.

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

```ts
import axios from 'axios';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
});

// Năm nhóm có controller base path
api.post('/auth/v1/login', { username, password, deviceId });
api.get('/users/v1');
api.get('/access/v1/roles');
api.get('/sales-orders/v1');

// Các controller còn lại
api.get('/v1/companies');
api.get('/v1/inventory/balances', { params: { warehouseId } });
```

Nếu HTTP client đang dùng base URL `http://localhost:8080`, giữ nguyên `/api` trong từng path:

```ts
api.post('/api/auth/v1/login', body);
api.get('/api/v1/companies');
```

Không trộn hai cách trên, nếu không request có thể thành `/api/api/...`.

## 6. Danh sách tìm/thay trong source FE

Tìm các chuỗi sau trong `src/`:

```text
/api/v1/auth
/api/v1/admin/users
/api/v1/users
/api/v1/access
/api/v1/sales-orders
```

Thay tương ứng:

```text
/api/auth/v1
/api/admin/users/v1
/api/users/v1
/api/access/v1
/api/sales-orders/v1
```

Nếu FE dùng base URL `/api`, tìm/thay phần path tương đối:

```text
/v1/auth          -> /auth/v1
/v1/admin/users   -> /admin/users/v1
/v1/users         -> /users/v1
/v1/access        -> /access/v1
/v1/sales-orders  -> /sales-orders/v1
```

## 7. Checklist kiểm tra FE

- [ ] Base URL chỉ kết thúc ở `/api`, không phải `/api/v1`.
- [ ] Login gọi `POST /api/auth/v1/login`.
- [ ] Bootstrap session gọi `GET /api/auth/v1/me`.
- [ ] Refresh/logout đã chuyển sang `/api/auth/v1/...`.
- [ ] Màn hình user gọi `/api/users/v1...`.
- [ ] Màn hình role/permission/scope gọi `/api/access/v1...`.
- [ ] Màn hình sales order gọi `/api/sales-orders/v1...`.
- [ ] Inventory, planning, work order và các module còn lại vẫn gọi `/api/v1/...`.
- [ ] Network tab không xuất hiện `/api/api`, `/v1/v1` hoặc `/api/v1/auth`.
- [ ] Khi gặp `404`, đối chiếu URL trước; `401/403` là lỗi xác thực/phân quyền, không phải lỗi routing.

## 8. URL tra cứu khi backend đang chạy

OpenAPI và Swagger đang mặc định tắt. Chỉ bật ở local hoặc môi trường acceptance được phép:

```env
SPRINGDOC_API_DOCS_ENABLED=true
SPRINGDOC_SWAGGER_UI_ENABLED=true
```

Sau khi backend khởi động thành công:

```text
OpenAPI JSON: http://localhost:8080/api/v3/api-docs
Swagger UI:   http://localhost:8080/api/swagger-ui/index.html
Health:       http://localhost:8081/actuator/health
```

Profile `prod` luôn tắt Springdoc; không coi URL OpenAPI trên production là contract được publish.

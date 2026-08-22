# Yêu cầu xác nhận API Admin RBAC giữa Frontend và Backend

**Dự án:** OmniPlant Manufacturing ERP  
**Ngày đối chiếu:** 2026-08-12  
**OpenAPI đã kiểm tra:** 3.0.1 — `Manufacturing ERP API v1.0.0`  
**Phạm vi:** Users, Roles, Permissions, Access Scopes và User Assignments trong MVP

## 1. Mục tiêu

Frontend cần Backend xác nhận các contract dưới đây trước khi tiếp tục tích hợp phần gán Permission trực tiếp cho Role và chạy acceptance toàn bộ Admin RBAC.

Mục tiêu chung:

- Dùng một response envelope thống nhất.
- Dùng UUID cho toàn bộ entity ID.
- Không suy đoán Role membership hoặc Scope resources từ code/hardcoded data.
- Xác định rõ permission guard, idempotency, error code và thời điểm quyền có hiệu lực.
- OpenAPI phải phản ánh đúng contract đang chạy thực tế.

## 2. Kết quả kiểm tra hiện tại

Các API read-only sau đã trả HTTP 200 và đúng paginated envelope:

| Chức năng | Endpoint | Trạng thái |
|---|---|---|
| Danh sách Users | `GET /api/v1/users` | Đã hoạt động |
| Danh sách Roles | `GET /api/v1/access/roles` | Đã hoạt động |
| Danh mục Permissions | `GET /api/v1/access/permissions` | Đã hoạt động, frontend chỉ đọc |
| Danh sách Access Scopes | `GET /api/v1/access/scopes` | Đã hoạt động |
| Danh sách Assignments | `GET /api/v1/access/assignments` | Đã hoạt động |
| Profile/quyền hiện tại | `GET /api/v1/auth/me` | Đã hoạt động |

Paginated response thực tế:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  }
}
```

## 3. Danh sách API Frontend cần dùng

### 3.1 Authentication và hiệu lực phân quyền

| Method | Endpoint | Mục đích | Cần Backend xác nhận |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Nhận token triplet | Contract hiện tại giữ nguyên |
| `GET` | `/api/v1/auth/me` | Nhận User, Roles, Permissions và Scopes hiệu lực | Khi grant/revoke quyền, request mới có thấy ngay không? |
| `POST` | `/api/v1/auth/refresh` | Rotate token và đọc lại `/auth/me` | Permission mới có hiệu lực sau refresh hay phải login lại? |
| `POST` | `/api/v1/auth/logout` | Thu hồi session tạm | Contract hiện tại giữ nguyên |

### 3.2 Users

| Method | Endpoint | Mục đích |
|---|---|---|
| `GET` | `/api/v1/users` | List/filter/page Users |
| `GET` | `/api/v1/users/{userId}` | User detail |
| `POST` | `/api/v1/users` | Tạo User |
| `PATCH` | `/api/v1/users/{userId}` | Sửa email/password theo contract |
| `DELETE` | `/api/v1/users/{userId}` | Deactivate User |
| `PATCH` | `/api/v1/admin/users/{userId}/unlock` | Unlock User |

Backend vui lòng xác nhận:

- Query list được hỗ trợ: `page`, `size`, `sortBy`, `sortDir` và các filter nếu có.
- `DELETE` là soft deactivate, không xóa vật lý.
- Có cho phép deactivate User đang có assignment ACTIVE không.
- Mọi mutation có hỗ trợ `Idempotency-Key` không.

### 3.3 Roles

| Method | Endpoint | Mục đích | Trạng thái |
|---|---|---|---|
| `GET` | `/api/v1/access/roles` | List Roles | Đã hoạt động |
| `GET` | `/api/v1/access/roles/{roleId}` | Role detail | Đã hoạt động nhưng chưa có Permissions |
| `POST` | `/api/v1/access/roles` | Tạo Role | Đã có |
| `PATCH` | `/api/v1/access/roles/{roleId}` | Sửa tên/mô tả | Đã có |
| `POST` | `/api/v1/access/roles/{roleId}/activate` | Kích hoạt Role | Đã có |
| `POST` | `/api/v1/access/roles/{roleId}/deactivate` | Vô hiệu Role | Đã có |
| `POST` | `/api/v1/access/roles/{roleId}/permissions/{permissionId}` | Grant Permission | Có trong OpenAPI |
| `DELETE` | `/api/v1/access/roles/{roleId}/permissions/{permissionId}` | Revoke Permission | Có trong OpenAPI |
| `GET` | `/api/v1/access/roles/{roleId}/permissions` | Đọc Permission membership | **Chưa có — cần bổ sung** |

### 3.4 Permissions

| Method | Endpoint | Mục đích | MVP frontend |
|---|---|---|---|
| `GET` | `/api/v1/access/permissions` | Danh mục Permission authoritative | Sử dụng |
| `POST` | `/api/v1/access/permissions` | Tạo Permission | Không mở trên MVP UI |

Frontend cần các field ổn định:

```json
{
  "permissionId": "uuid",
  "code": "PERM_ACCESS_MANAGE",
  "resource": "ACCESS",
  "action": "MANAGE",
  "description": "Manage access control",
  "status": "ACTIVE"
}
```

### 3.5 Access Scopes

| Method | Endpoint | Mục đích | Trạng thái |
|---|---|---|---|
| `GET` | `/api/v1/access/scopes` | List Scopes | Đã hoạt động |
| `GET` | `/api/v1/access/scopes/{scopeId}` | Scope detail | Đã có nhưng chưa trả resource membership |
| `POST` | `/api/v1/access/scopes` | Tạo Scope | Đã có |
| `PATCH` | `/api/v1/access/scopes/{scopeId}` | Sửa Scope | Đã có |
| `POST` | `/api/v1/access/scopes/{scopeId}/activate` | Kích hoạt Scope | Đã có |
| `POST` | `/api/v1/access/scopes/{scopeId}/deactivate` | Vô hiệu Scope | Đã có |
| `POST` | `/api/v1/access/scopes/{scopeId}/resources` | Thêm Scope resource | Đã có |
| `GET` | `/api/v1/access/scopes/{scopeId}/resources` hoặc resources trong Scope detail | Đọc resource membership | Cần xác nhận/bổ sung nếu MVP cho phép Scope theo resource |

Backend cần xác nhận một trong hai hướng:

1. MVP chỉ hỗ trợ `GLOBAL` Scope: frontend sẽ giữ resource picker ở trạng thái không sử dụng; hoặc
2. MVP hỗ trợ `COMPANY`/`PLANT`/`WAREHOUSE`: Backend cần API đọc authoritative resources và semantics cập nhật rõ ràng (`add`, `remove` hoặc `replace-all`).

### 3.6 User Assignments

| Method | Endpoint | Mục đích |
|---|---|---|
| `GET` | `/api/v1/access/assignments` | List/filter assignments |
| `POST` | `/api/v1/access/assignments` | Gán User + Role + Scope |
| `DELETE` | `/api/v1/access/assignments/{assignmentId}` | Revoke assignment |

Request tạo assignment frontend đang dùng:

```json
{
  "userId": "uuid",
  "roleId": "uuid",
  "scopeId": "uuid",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

Backend vui lòng xác nhận:

- Filter list: `userId`, `roleId`, `scopeId`, `page`, `size`, `sortBy`, `sortDir`.
- Duplicate ACTIVE assignment trả HTTP/code nào.
- `expiresAt` có bắt buộc UTC Instant không và boundary hết hạn là inclusive hay exclusive.
- `DELETE` chuyển status sang `REVOKED`, không xóa audit history.

## 4. Blocker bắt buộc: đọc Permission membership của Role

Frontend không thể hiển thị checkbox đã chọn hoặc tính diff grant/revoke nếu chỉ có Permission catalog toàn cục.

Đề xuất Backend bổ sung:

```http
GET /api/v1/access/roles/{roleId}/permissions?page=0&size=100
Authorization: Bearer <token>
```

Response đề xuất:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "content": [
      {
        "permissionId": "550e8400-e29b-41d4-a716-446655440000",
        "code": "PERM_INVENTORY_READ",
        "resource": "INVENTORY",
        "action": "READ",
        "description": "Read inventory",
        "status": "ACTIVE"
      }
    ],
    "page": 0,
    "size": 100,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

Phương án khác được chấp nhận: `GET /access/roles/{roleId}` trả thêm `permissions[]` với cùng Permission DTO. Backend vui lòng chọn **một** nguồn authoritative và cập nhật OpenAPI.

Kết quả probe hiện tại:

- OpenAPI không khai báo `GET /access/roles/{roleId}/permissions`.
- Role detail không có `permissions` hoặc `permissionIds`.
- GET vào đường dẫn chưa khai báo trả HTTP 500 thay vì 404/405, trace ID `bbad9f8c37824349`.

Backend vui lòng sửa endpoint không được hỗ trợ để trả 404/405 chuẩn, hoặc triển khai GET membership trả 200.

## 5. Semantics cần Backend xác nhận cho grant/revoke

Hai endpoint hiện có dùng UUID path params, không có request body và OpenAPI ghi response `ApiResponseVoid`.

Backend vui lòng điền/xác nhận bảng sau:

| Trường hợp | HTTP đề xuất | `code` đề xuất | Backend xác nhận |
|---|---:|---|---|
| Grant thành công | 200 | `SUCCESS` | ☐ |
| Grant Permission đã tồn tại | 200 idempotent hoặc 409 | `SUCCESS` hoặc `ROLE_PERMISSION_EXISTS` | ☐ |
| Revoke thành công | 200 | `SUCCESS` | ☐ |
| Revoke membership không tồn tại | 200 idempotent hoặc 404 | `SUCCESS` hoặc `ROLE_PERMISSION_NOT_FOUND` | ☐ |
| Role không tồn tại | 404 | `ROLE_NOT_FOUND` | ☐ |
| Permission không tồn tại | 404 | `PERMISSION_NOT_FOUND` | ☐ |
| Role INACTIVE | 409 hoặc 422 | Mã ổn định do Backend chọn | ☐ |
| System Role không cho sửa | 409 hoặc 403 | Mã ổn định do Backend chọn | ☐ |
| Thiếu quyền quản trị | 403 | `PERMISSION_DENIED` | ☐ |

Cần xác nhận thêm:

- Permission guard cho list/read/manage có cùng là `PERM_ACCESS_MANAGE` không.
- `Idempotency-Key` có bắt buộc và được replay ổn định cho grant/revoke không.
- Grant/revoke có cập nhật `updatedAt` hoặc audit log không.
- Permission INACTIVE có được grant không.
- Deactivate Role có làm assignment hiện tại mất hiệu lực ngay không.

## 6. Response và error envelope thống nhất

Success object:

```json
{
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "result": {}
}
```

Success void: Backend có thể bỏ `result` do NON_NULL serialization, nhưng cần áp dụng nhất quán:

```json
{
  "code": "SUCCESS",
  "message": "Success"
}
```

Error đề xuất:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "errors": [
    {
      "field": "roleId",
      "message": "Role does not exist"
    }
  ]
}
```

Yêu cầu chung:

- Mọi error response có `code` và `message` ổn định.
- Validation có `errors[]` với `field` và `message`.
- Mọi response có `X-Trace-Id`; trace ID trong body là tùy chọn nhưng phải thống nhất nếu có.
- OpenAPI khai báo các response 400, 401, 403, 404, 409, 422 và 500 thay vì chỉ response 200.
- Admin RBAC là GLOBAL/COMPANY context; không yêu cầu `X-Plant-Id` nếu endpoint không có Plant ID trong path/query/body.

## 7. Idempotency thống nhất

Frontend gửi `Idempotency-Key: <UUID v4>` cho mọi mutation.

Backend vui lòng xác nhận:

- Các endpoint create/update/status/grant/revoke/assignment/revoke assignment đều hỗ trợ header này.
- Replay cùng key + cùng payload/path trả cùng logical result và không tạo tác động lần hai.
- Cùng key + payload/path khác trả conflict với error code ổn định.
- Thời gian lưu idempotency key.
- `DELETE` và status transition có idempotent hay không.

## 8. Hiệu lực session sau thay đổi quyền

Frontend cần một contract rõ ràng sau grant/revoke Role Permission hoặc create/revoke Assignment.

Backend vui lòng chọn và xác nhận:

- ☐ `GET /auth/me` request mới phản ánh thay đổi ngay với access token hiện tại.
- ☐ Chỉ phản ánh sau `POST /auth/refresh`.
- ☐ Chỉ phản ánh sau logout/login lại.

Khuyến nghị: `/auth/me` luôn đọc trạng thái quyền hiện tại; frontend gọi lại `/auth/me` sau mutation thành công và refresh Auth Context. Backend vẫn là nguồn kiểm tra authorization cuối cùng cho mọi request.

## 9. Checklist Backend phản hồi trước khi Frontend tiếp tục

### Bắt buộc

- [ ] Chọn contract đọc Role Permission membership và cập nhật OpenAPI.
- [ ] Xác nhận grant/revoke semantics và error codes.
- [ ] Xác nhận permission guard, dự kiến `PERM_ACCESS_MANAGE`.
- [ ] Xác nhận `Idempotency-Key` cho toàn bộ Admin mutations.
- [ ] Xác nhận thời điểm quyền/assignment có hiệu lực trong `/auth/me`.
- [ ] Sửa GET membership chưa hỗ trợ không trả HTTP 500.

### Access Scope

- [ ] Xác nhận MVP chỉ GLOBAL hay có COMPANY/PLANT/WAREHOUSE resources.
- [ ] Nếu có resources, cung cấp API đọc membership và cập nhật semantics.

### Tính đồng nhất chung

- [ ] UUID, pagination, envelope và status enum đúng như tài liệu.
- [ ] Error envelope và `X-Trace-Id` thống nhất.
- [ ] OpenAPI mô tả đủ success/error responses.
- [ ] Admin RBAC endpoints không yêu cầu `X-Plant-Id` khi không Plant-scoped.

## 10. Điều kiện Frontend tiếp tục

Sau khi Backend phản hồi checklist và cập nhật OpenAPI, Frontend sẽ:

1. Cập nhật contract tests cho Role membership và grant/revoke.
2. Kết nối API hooks/adapters.
3. Hiển thị membership authoritative trong Role UI.
4. Chạy live acceptance bằng Role/User/Scope/Assignment tạm.
5. Revoke/deactivate toàn bộ dữ liệu thử và xác nhận `/auth/me`/route authorization.

Frontend sẽ không triển khai trạng thái checkbox Role Permission dựa trên suy đoán hoặc hardcoded data.

# FE Review phản hồi Backend — Admin RBAC — 2026-08-12

## 1. Kết luận

**Contract được chấp nhận và đủ để tiếp tục các session Admin RBAC.** Hai endpoint mới và fix unknown-path đã xuất hiện trên backend live tại thời điểm FE recheck, dù cuối file phản hồi Backend vẫn ghi “chưa deploy”.

Không bắt đầu implementation trong bước review này. Các chênh lệch FE được đưa vào đúng session nhỏ bên dưới.

## 2. HTTP live recheck

Kiểm tra bằng Admin session tạm, chỉ dùng GET và đã logout thành công.

| Kiểm tra | Kết quả live | Trace ID |
|---|---|---|
| `GET /access/roles/{roleId}/permissions?page=0&size=100&sortBy=code&sortDir=asc` | HTTP 200 `SUCCESS`; tổng 53; item fields đúng contract | `2eef2279994a45d4` |
| `GET /access/scopes/{scopeId}/resources?page=0&size=100` | HTTP 200 `SUCCESS`; tổng 4; item fields đúng contract | `3b6191a7a1e74ebf` |
| Unknown `GET /definitely-not-a-real-path` | HTTP 404 `ENTITY_NOT_FOUND` | `1a8b0b69c92e4edf` |

Role membership fields:

```text
permissionId, code, resource, action, description, status
```

Scope resource fields:

```text
scopeResourceId, scopeId, resourceType, resourceId
```

Kết luận: controller, security và truy vấn DB của hai GET mới đã chạy thành công qua HTTP live; ghi chú “chưa smoke test/chưa deploy” trong file Backend không còn đúng với môi trường FE vừa kiểm tra.

## 3. Contract FE chốt theo phản hồi Backend

### Role–Permission

- Nguồn membership authoritative là endpoint GET riêng, không phải Role detail.
- Endpoint phân trang; `size` tối đa 100; FE phải đọc `totalPages`.
- Page rỗng nghĩa là Role tồn tại nhưng chưa có Permission.
- Role không tồn tại trả 404 `ENTITY_NOT_FOUND`.
- Membership giữ cả Permission `INACTIVE`; FE phải hiển thị checked nhưng disabled/có nhãn ngưng dùng, không âm thầm revoke.
- Grant/revoke dùng UUID path params, không request body, trả success void.
- Grant trùng và revoke thiếu đều idempotent HTTP 200 `SUCCESS`.
- Grant Role/Permission không tồn tại: 404 `ENTITY_NOT_FOUND`.
- Grant Role hoặc Permission `INACTIVE`: 422 `OPERATION_NOT_ALLOWED`.
- System Role cho phép grant/revoke; FE phải cảnh báo mạnh khi sửa ADMIN để tránh tự khóa.
- Revoke không được dùng để kiểm tra Role/Permission tồn tại vì kể cả Role ID không tồn tại vẫn trả 200.

### Access Scope resources

- MVP hỗ trợ `GLOBAL`, `COMPANY`, `PLANT`, `WAREHOUSE`.
- Resource membership authoritative dùng GET riêng.
- Semantics hiện tại chỉ có `add + read`; không có remove hoặc replace-all.
- Thêm trùng trả 409 `RESOURCE_ALREADY_EXISTS`.
- Nếu thêm nhầm, contract hiện chỉ cho deactivate Scope hoặc revoke Assignment đang dùng Scope.

### Authorization và context

- Toàn bộ `/access/**`, `/users` và `/companies` cần GLOBAL `PERM_ACCESS_MANAGE`.
- Plant-only account nhận 403 dù có permission code; hiện chỉ Admin dùng được Admin RBAC.
- Admin RBAC không dùng `X-Plant-Id`.
- `/auth/me` và authorization request kế tiếp đọc quyền mới ngay từ DB với access token hiện tại; không cần refresh/login lại.

### Assignment

- Filter: `userId`, `roleId`, `scopeId`, `page`, `size`.
- Sort cố định `createdAt desc`; `sortBy`/`sortDir` bị bỏ qua.
- Duplicate ACTIVE: 409 `RESOURCE_ALREADY_EXISTS`.
- `expiresAt` là optional UTC Instant, phải lớn hơn hiện tại; boundary exclusive.
- Revoke giữ lịch sử và chuyển status thành `INACTIVE`, không phải `REVOKED`.

### Envelope và trace

- Success void bỏ hẳn `result`.
- `X-Trace-Id` có trên mọi response và không nằm trong body.
- OpenAPI error responses chưa đầy đủ; FE dùng HTTP + code đã xác nhận thực tế.

## 4. Trả lời Backend về định dạng ngày/giờ

Frontend hiện có compatibility normalization ở DTO boundary:

- Java `Instant`: nhận cả ISO date-time string và numeric epoch seconds/milliseconds, sau đó chuẩn hóa thành ISO string bằng `toIsoInstant`.
- `LocalDate` của Sales Order: nhận cả `YYYY-MM-DD` và tuple `[year, month, day]`, sau đó chuẩn hóa thành `YYYY-MM-DD`.
- Admin Users và Assignments đã nhận numeric Instant qua cùng normalizer; Audit và Organization cũng dùng normalizer này.

**FE đề nghị Backend chuyển wire format canonical sang ISO như tài liệu đã hứa:**

- `LocalDate` → `YYYY-MM-DD`.
- `Instant` → ISO-8601 UTC, ví dụ `2026-08-12T08:30:00Z`.

Backend có thể sửa mà không cần release đồng thời với FE vì adapter hiện chấp nhận cả legacy lẫn ISO. FE sẽ giữ compatibility parser trong giai đoạn chuyển đổi và chỉ cân nhắc bỏ legacy support sau live acceptance toàn hệ thống.

Focused verification: Instant/Admin/Sales Order API tests — 11/11 passed; TypeScript passed.

## 5. Chênh lệch trong FE phải xử lý theo session

| Gap FE hiện tại | Tác động | Session xử lý |
|---|---|---|
| Admin mutations đang gửi `Idempotency-Key`, trong khi Backend bỏ qua | Header không gây lỗi nhưng không bảo vệ retry | RBAC-03/04/06/07 theo từng mutation |
| Một generic Admin mutation retry đang tự retry mọi network error tối đa 2 lần | Create Role/Scope/Assignment/Scope resource có thể commit lần đầu rồi retry nhận 409 | Tách retry policy trong RBAC-03 trước khi live create; áp dụng cho các session sau |
| Assignment UI kiểm tra `status !== 'REVOKED'` | Backend trả `INACTIVE`, nên action Revoke có thể vẫn hiện | RBAC-07 |
| API Access Scope workspace chỉ tạo `GLOBAL`, chưa đọc/hiển thị resources | Không đáp ứng COMPANY/PLANT/WAREHOUSE contract | Tách RBAC-06A API contract và RBAC-06B UI/live acceptance |
| Role API chưa có membership/grant/revoke hooks | Chưa dùng được contract mới | RBAC-08 |
| Role UI chưa hiển thị membership/INACTIVE Permission/system ADMIN warning | Chưa thể gán quyền an toàn | RBAC-09 |
| Admin cache invalidation chưa refresh `/auth/me` sau permission/assignment changes | Sidebar/action visibility có thể giữ session cache cũ | RBAC-09/10 |
| API User UI chỉ hiện Unlock cho status `LOCKED` | Backend mô tả unlock là đường bật lại User `INACTIVE`; cần live xác nhận semantics trước khi sửa UI | RBAC-03 |

Lưu ý: câu Backend “FE không phải sửa code đang chạy” đúng theo nghĩa không có breaking change với chức năng cũ. Các thay đổi FE trong bảng vẫn bắt buộc để sử dụng hai endpoint mới và tuân thủ semantics vừa chốt.

## 6. Gate trước session tiếp theo

- Backend contract: **PASS**.
- Hai GET mới qua live DB/HTTP: **PASS**.
- Unknown path 404: **PASS**.
- Date format decision từ FE: **ISO canonical, compatibility parser tạm giữ**.
- Không còn blocker để chạy RBAC-02.

**Next:** RBAC-02 chỉ kiểm tra navigation/authorization cho Admin và Plant-only accounts; không CRUD.

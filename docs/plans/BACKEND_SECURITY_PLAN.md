# Kế hoạch khắc phục RBAC và cô lập phạm vi dữ liệu

**Ngày lập:** 2026-08-16  
**Phạm vi:** Dynamic RBAC, global admin, Company/Plant/Warehouse scope, `/auth/me`, tenant isolation và security regression gates  
**Trạng thái:** YELLOW — các lỗi RBAC/scope cốt lõi đã sửa và test tự động đã GREEN; chưa phát hành cho đến khi audit dữ liệu môi trường, HTTP cross-tenant, SAST/SCA/secret/DAST và review độc lập có bằng chứng  
**Nguồn sự thật:** Code backend, migration PostgreSQL, security tests và contract API; giao diện không phải nguồn quyết định authorization

## 1. Mục tiêu và giới hạn cam kết

Mục tiêu là đưa cơ chế xét quyền về trạng thái **fail closed**, nhất quán giữa DB, Spring Security, service guard và `/auth/me`, đồng thời ngăn mọi đường leo thang từ role có scope thành quyền global.

Không hệ thống phần mềm nào có thể được chứng minh “bảo mật tuyệt đối” chỉ bằng test. Tiêu chuẩn nghiệm thu của kế hoạch này là mức bảo đảm cao, đo được và lặp lại được:

- Mọi quyết định cấp quyền quan trọng đều có test dương và test đối nghịch.
- Mọi dữ liệu scope không hợp lệ đều bị từ chối hoặc bị bỏ qua theo chính sách fail-closed.
- Không role tùy biến hoặc role được gán ở scope hẹp nào tạo được `ROLE_ADMIN`.
- Backend luôn là nguồn authorization cuối cùng; client không thể nâng quyền bằng header, payload hoặc dữ liệu `/auth/me` đã cache.
- Mọi endpoint nghiệp vụ được phân loại permission + scope; endpoint mới chưa phân loại làm build thất bại.
- Unit, integration, migration, HTTP cross-tenant và mutation/decision-coverage gates đều xanh trước phát hành.

## 2. Các lỗi phải khắc phục

### S1 — Scoped ADMIN trở thành global admin

Hiện tại role code lấy từ mọi assignment active được chuyển thẳng thành Spring authority `ROLE_*`. Vì `PermissionGuard`, User API, Swagger và Actuator đều tin `ROLE_ADMIN`, assignment `ADMIN` ở Company/Plant có thể bật bypass toàn cục.

**Mức độ:** Critical.  
**Chính sách mới:** Chỉ global system role `ADMIN`, được xác minh từ nguồn authoritative, mới được sinh `ROLE_ADMIN`. Dynamic/scoped role không bao giờ được quyền tạo authority đặc biệt này.

### S2 — `scopeType` không ràng buộc resource membership

Hiện tại một scope có thể chứa resource không đúng loại, `GLOBAL` có thể chứa resource, và non-global scope có thể được assign khi chưa có resource.

**Mức độ:** High.  
**Chính sách mới:** Áp dụng ma trận invariant ở mục 4 tại service và kiểm tra dữ liệu DB trước khi phát hành.

### S3 — Role thuộc Company có thể gán chéo tenant

`Role.companyId` chưa được đối chiếu với các Company bao phủ bởi scope khi tạo assignment.

**Mức độ:** High.  
**Chính sách mới:** Company-owned role chỉ được gán vào scope có toàn bộ resource thuộc đúng Company đó; không được gán vào `GLOBAL` hoặc scope chứa resource của Company khác.

### S4 — `/auth/me` suy luận scope từ resource thay vì `scopeType`

Row không có resource bị coi là `GLOBAL`; row có Plant bị coi là `PLANT` dù `scopeType` là `GLOBAL` hoặc `COMPANY`.

**Mức độ:** High.  
**Chính sách mới:** Phân loại trước hết bằng `scopeType`, sau đó xác thực resource membership. Dữ liệu mâu thuẫn phải fail closed và phát audit/metric.

### S5 — Contract `WAREHOUSE` và enum `WAREHOUSE_GROUP` lệch nhau

API contract dùng `WAREHOUSE`, còn persistence enum dùng `WAREHOUSE_GROUP`.

**Mức độ:** Medium.  
**Chính sách mới:** Wire canonical là `WAREHOUSE`; request tạm nhận cả `WAREHOUSE` và `WAREHOUSE_GROUP`; DB tiếp tục lưu `WAREHOUSE_GROUP`. Response chỉ phát `WAREHOUSE`.

### S6 — Thiếu architecture và endpoint inventory gate

Test hiện tại chủ yếu kiểm tra từng service, chưa chứng minh endpoint mới luôn có permission/scope classification.

**Mức độ:** High đối với khả năng tái phát.  
**Chính sách mới:** Thêm architecture test, endpoint-security inventory và cross-tenant HTTP matrix bắt buộc.

## 3. Mô hình quyền sau sửa

### 3.1 Phân biệt role hiển thị và authority bảo mật

- `ROLE_ADMIN` là authority đặc biệt, chỉ dành cho global system administrator.
- Custom role và scoped role là dữ liệu RBAC; không được tự động trở thành authority có semantics toàn cục.
- Permission nghiệp vụ phải được quyết định bằng `PermissionGuard` trên request hiện tại và trạng thái DB hiện tại.
- Flat `roles[]`/`permissions[]` trong `/auth/me` chỉ phục vụ hiển thị. Client phải dùng `scopes[]` để quyết định khả năng hiển thị theo context; backend vẫn kiểm tra lại.
- Không thêm `hasRole(...)` hoặc `hasAuthority(...)` mới cho nghiệp vụ scoped. Mọi nghiệp vụ scoped dùng guard resolve resource/aggregate.

### 3.2 Nguồn xác minh global admin

Phương án triển khai ưu tiên:

1. Thêm repository query chỉ trả system role `ADMIN` khi:
   - role `is_system = true`, `company_id is null`, `status = ACTIVE`;
   - assignment `status = ACTIVE`, chưa hết hạn;
   - access scope `scope_type = GLOBAL`, `status = ACTIVE`;
   - scope không có resource membership.
2. `UserDetailsServiceImpl` chỉ thêm `ROLE_ADMIN` từ query này.
3. `UserPrincipal` không được chuyển dynamic role code `ADMIN` hoặc `ROLE_ADMIN` thành authority đặc biệt.
4. `PermissionGuard.isAdmin` chỉ tin principal được tạo bởi đường xác minh trên; không tin raw string từ client/JWT claim.
5. JWT không mang authority authoritative. Mỗi request tiếp tục reload user/access state từ DB; revoke phải có hiệu lực ở request kế tiếp.

Không dùng tên/code role đơn lẻ làm bằng chứng global admin.

## 4. Ma trận invariant của Access Scope

| `ScopeType` nội bộ | Wire value | Resource được phép | Số resource tối thiểu trước assign | Quy tắc bao phủ |
|---|---|---|---:|---|
| `GLOBAL` | `GLOBAL` | Không có | 0 | Toàn hệ thống |
| `COMPANY` | `COMPANY` | Chỉ `COMPANY` | 1 | Company và mọi Plant/Warehouse con |
| `PLANT` | `PLANT` | Chỉ `PLANT` | 1 | Plant và mọi Warehouse con |
| `WAREHOUSE_GROUP` | `WAREHOUSE` | Chỉ `WAREHOUSE` | 1 | Chỉ các Warehouse được liệt kê |
| `CUSTOM` | `CUSTOM` | `COMPANY`, `PLANT`, `WAREHOUSE` | 1 | Hợp của các resource explicit, có kế thừa top-down |

Các invariant bắt buộc:

- `GLOBAL` không được thêm resource.
- Non-global scope không được assign nếu membership rỗng.
- `COMPANY`, `PLANT`, `WAREHOUSE_GROUP` từ chối resource sai loại bằng HTTP 422 `OPERATION_NOT_ALLOWED`.
- `CUSTOM` cho phép resource hỗn hợp nhưng phải loại trùng và không được suy diễn thành global.
- Resource phải tồn tại và active tại lúc thêm. Việc resource bị deactivate sau đó không tự xóa lịch sử scope; service nghiệp vụ vẫn thực thi lifecycle rule của resource.
- Company-owned role chỉ được gán khi mọi resource của scope nằm trong cùng `role.companyId`.
- Global/system role chỉ được gán `GLOBAL` nếu chính sách role cho phép; riêng system `ADMIN` phải tuân thủ mục 3.2.
- Mọi validation chạy trong cùng transaction với mutation assignment/resource.
- DB migration/audit phải phát hiện dữ liệu cũ vi phạm trước khi constraint logic mới được bật.

## 5. Thiết kế thành phần sửa

### 5.1 `ReservedAuthorityPolicy`

Tạo component thuần, không phụ thuộc HTTP:

- Chuẩn hóa code bằng `Locale.ROOT`.
- Nhận diện `ADMIN` và `ROLE_ADMIN` là reserved.
- Chặn tạo company/custom role bằng reserved code.
- Không dựa vào `name`; code là identity. Dữ liệu cũ có name giống ADMIN nhưng code khác không được đặc quyền.

### 5.2 `AccessScopeInvariantValidator`

Tạo một validator dùng chung bởi `createScope`, `addScopeResource`, `assignRole` và job audit dữ liệu:

- `validateResourceType(scopeType, resourceType)`.
- `validateAssignable(scope, resources)`.
- `validateRoleOwnership(role, resources)`.
- `resolveOwningCompanyIds(resources)` theo batch, không N+1.
- Trả lỗi nghiệp vụ ổn định, không lộ sự tồn tại resource ngoài tenant cho caller không có quyền.

### 5.3 `EffectiveAccessService`

Tách logic đọc effective access khỏi DTO mapping:

- Trả cấu trúc typed gồm `scopeType`, normalized resources, permissions và validity status.
- Dùng cùng logic cho `/auth/me` và các kiểm tra consistency nội bộ.
- Row malformed bị loại khỏi effective access, ghi security audit và tăng metric; không tự nâng thành global.
- Global row hợp lệ được phân loại bằng `scopeType`, không bằng `resourceType == null`.

### 5.4 Query và repository

- Thêm query xác minh global system admin đầy đủ điều kiện.
- Giữ 5 filter bắt buộc cho assignment: assignment, expiry, role, permission, access scope.
- Batch-load scope resources và organization ownership.
- Không dùng flat permission union để quyết định resource access.
- Cân nhắc projection trả `scopeId`, `roleId`, `role.companyId`, `scopeType`, resource tuple và permission code để validation/audit có đủ identity.

### 5.5 Wire compatibility cho Warehouse

- Request nhận `WAREHOUSE` và alias legacy `WAREHOUSE_GROUP`.
- Mapper response phát `WAREHOUSE`.
- JPA tiếp tục lưu `WAREHOUSE_GROUP` bằng `EnumType.STRING`.
- OpenAPI và tài liệu chỉ quảng bá `WAREHOUSE`; alias legacy có ngày loại bỏ riêng.

## 6. Kế hoạch triển khai theo pha

### Pha 0 — Khóa test tái hiện lỗi

**Đã thực hiện ngày 2026-08-16.**

Các file security regression mới:

- `ScopedAdminEscalationSecurityTest`
- `AccessScopeInvariantSecurityTest`
- `WarehouseScopeWireContractSecurityTest`
- `PermissionGuardFailClosedSecurityTest`
- `AccessScopeAuthorizationMatrixSecurityTest`
- `AuthorizationArchitectureSecurityTest`
- `SensitiveResponseDataMinimizationSecurityTest`

Baseline trước sửa của toàn bộ 7 lớp: **47 test, 18 pass, 28 failure, 1 error, 0 skipped**. Trong đó bộ tái hiện lỗi ban đầu có 22 test (4 pass, 17 failure, 1 error); bộ nghiệm thu bổ sung có 25 test (14 pass, 11 failure, 0 error). Sau sửa đã bổ sung thêm 2 ca global-admin và đạt **49/49 pass, 0 failure, 0 error, 0 skipped**. Không test nào bị disable hoặc nới assertion để làm build xanh giả.

Các lỗi bổ sung được khóa bằng test:

- Principal giả mạo mang `ROLE_ADMIN` nhưng không có bằng chứng authoritative vẫn đang vượt guard.
- `permissionCode = null` đang ném `NullPointerException` thay vì fail closed.
- 9 cặp `ScopeType × ResourceType` không hợp lệ vẫn đang được chấp nhận.
- Architecture gate Controller → Repository và response data-minimization đã xanh, tiếp tục giữ làm regression gate.

### Pha 1 — Đóng đường scoped ADMIN

1. Thêm reserved-code policy.
2. Chặn tạo custom/company role `ADMIN` và `ROLE_ADMIN` không phân biệt hoa thường.
3. Thêm query global-system-admin authoritative.
4. Sửa `UserDetailsServiceImpl` và `UserPrincipal` để scoped dynamic roles không sinh `ROLE_ADMIN`.
5. Giữ legacy global system admin hoạt động.
6. Rà toàn bộ `hasRole('ADMIN')`, `ROLE_ADMIN` và endpoint Swagger/Actuator.
7. Thêm HTTP test chứng minh scoped admin nhận 403 ở Users, Access Control, Swagger và Actuator.

**Gate:** Toàn bộ `ScopedAdminEscalationSecurityTest` xanh; mutation bỏ một điều kiện global/system/status/expiry phải làm test đỏ.

### Pha 2 — Áp invariant scope-resource

1. Thêm `AccessScopeInvariantValidator`.
2. Gọi validator trong `addScopeResource` trước khi save.
3. Gọi validator trong `assignRole` trước khi kiểm tra duplicate/save.
4. Kiểm tra company ownership của role và toàn bộ resource.
5. Dùng batch query để tránh N+1.
6. Bổ sung API remove/replace membership nếu sản phẩm cần sửa scope nhập nhầm; trước khi có API đó, scope sai phải deactivate.

**Gate:** Các test ma trận GLOBAL/COMPANY/PLANT/WAREHOUSE/CUSTOM, empty membership và cross-company assignment xanh.

### Pha 3 — Đồng nhất `/auth/me` và authorization

1. Tạo effective-access model typed.
2. Phân loại theo `scopeType`.
3. Kiểm tra cặp `scopeType/resourceType`.
4. Fail closed cho row malformed.
5. Ghi audit/metric nhưng không trả ID nhạy cảm trong thông báo cho client.
6. So sánh `/auth/me.scopes[]` với các quyết định `PermissionGuard` bằng property/table-driven tests.

**Gate:** Không trường hợp nào `/auth/me` báo GLOBAL khi `PermissionGuard` từ chối global, hoặc ngược lại.

### Pha 4 — Chuẩn hóa contract Warehouse

1. Thêm wire adapter/creator cho `WAREHOUSE` → `WAREHOUSE_GROUP`.
2. Giữ alias request `WAREHOUSE_GROUP` trong thời gian chuyển tiếp.
3. Response/OpenAPI phát `WAREHOUSE`.
4. Cập nhật tài liệu FE và contract tests.

**Gate:** Ba test wire contract xanh; không migration DB chỉ vì đổi wire label.

### Pha 5 — Audit và làm sạch dữ liệu hiện hữu

Tạo migration/report chạy trước enforcement:

- Liệt kê company/custom role có code reserved.
- Liệt kê system ADMIN assignment không phải GLOBAL.
- Liệt kê GLOBAL scope có resource.
- Liệt kê non-global scope không có resource nhưng đang có assignment active.
- Liệt kê scopeType/resourceType mismatch.
- Liệt kê company-owned role gán chéo Company/global.
- Liệt kê assignment inactive/expired nhưng vẫn xuất hiện trong effective authorities.

Chính sách xử lý:

- Không tự động mở rộng quyền.
- Dữ liệu mơ hồ bị quarantine/deactivate, có báo cáo ID cho quản trị viên và audit trail.
- Chỉ backfill khi mapping duy nhất và chứng minh được.
- Migration phải idempotent và có integration test trên DB mới lẫn DB có dữ liệu legacy.

### Pha 6 — Architecture và endpoint-security inventory

1. Thêm test cấm Controller gọi Repository trực tiếp.
2. Sinh inventory từ `RequestMappingHandlerMapping` gồm method, path, service method, permission, resource type và classification.
3. Endpoint mới không có classification làm build fail.
4. Public endpoint phải khớp allowlist chính xác; không wildcard rộng.
5. Mọi write action phải dùng permission MANAGE/APPROVE/EXECUTE tương ứng, không chỉ READ.
6. Application service public được controller gọi phải có `@PreAuthorize` hoặc allowlist nội bộ có lý do.

### Pha 7 — Cross-tenant HTTP/E2E và release hardening

Với mỗi aggregate ID: tạo dữ liệu A và B ở hai tenant, dùng user A gọi ID của B qua GET/PATCH/DELETE/action. Kỳ vọng 403 hoặc 404 theo policy thống nhất và dữ liệu B không đổi.

Tối thiểu bao phủ:

- Company, Plant, Warehouse, Item, BOM, Routing.
- Inventory balance, lot, movement, setting.
- Sales Order và line.
- Work Order, operation, reservation, material issue, receipt, execution.
- Planning demand/run/suggestion.
- Supplier, requisition, purchase order, goods receipt.
- Shift, calendar, work center, costing, audit, data import.
- User, Role, Permission, Scope, Scope Resource, Assignment.

## 7. Ma trận test bắt buộc

### 7.1 Global admin và role escalation

| Tình huống | Kỳ vọng |
|---|---|
| Legacy/system ADMIN + GLOBAL + active | Có `ROLE_ADMIN` |
| System ADMIN + COMPANY/PLANT/WAREHOUSE | Không `ROLE_ADMIN` |
| Custom role code `ADMIN` hoặc `ROLE_ADMIN` | Bị từ chối khi tạo |
| Dynamic assignment trả code `ADMIN` | Không sinh `ROLE_ADMIN` |
| ADMIN assignment expired/inactive | Không sinh authority |
| Role/scope inactive | Không sinh authority |
| User không có global `PERM_ACCESS_MANAGE` tự assign | 403, DB không đổi |
| Scoped ADMIN gọi Users/Swagger/Actuator | 403 |

### 7.2 Scope shape

Test table-driven toàn bộ tích Descartes `ScopeType × ResourceType`:

- Chỉ các cặp trong mục 4 được chấp nhận.
- GLOBAL + bất kỳ resource nào bị từ chối.
- Non-global membership rỗng không assign được.
- CUSTOM mixed explicit resources được phép nhưng không global.
- Duplicate resource trả 409 và không tạo thêm row.
- Resource không tồn tại/inactive trả lỗi contract hiện hành.

### 7.3 Kế thừa quyền

| Assignment | Company target | Plant target cùng cây | Warehouse target cùng cây | Target ngoài cây |
|---|---:|---:|---:|---:|
| GLOBAL | allow | allow | allow | allow |
| COMPANY | allow exact | allow child | allow descendant | deny |
| PLANT | deny parent mặc định | allow exact | allow child | deny |
| WAREHOUSE | deny | deny | allow exact | deny |
| CUSTOM | theo explicit + top-down | theo explicit + top-down | theo explicit + top-down | deny |

Ngoại lệ Item Master “plant có thể dùng shared item của owning company” phải có test riêng và không được lan sang resource khác.

### 7.4 `/auth/me` consistency

- Global hợp lệ trả một entry GLOBAL với company/plant null.
- Company/Plant hợp lệ trả đúng identity và permission.
- Warehouse không được giả thành Plant/Global; nếu UI chưa điều hướng Warehouse thì contract phải mô tả rõ.
- Non-global row không resource bị bỏ qua + audit.
- Mismatch scope/resource bị bỏ qua + audit.
- Global row có resource legacy vẫn không bị hạ thành Plant; dữ liệu phải được audit để cleanup.
- Assignment revoke/expire thay đổi `/auth/me` ở request kế tiếp với access token hiện tại.
- Mỗi permission hiển thị trong scope phải cho kết quả guard tương ứng; flat union không được dùng để suy diễn global.

### 7.5 Concurrency và TOCTOU

- Scope bị deactivate đồng thời với assign: transaction cuối không được tạo assignment effective.
- Role/permission bị deactivate đồng thời với request nghiệp vụ: request kế tiếp bị từ chối.
- Hai request assign trùng: tối đa một ACTIVE row; request còn lại 409.
- Resource membership thay đổi trong lúc resolve access: quyết định phải dựa trên snapshot transaction nhất quán.

### 7.6 Input và data minimization

- Enum/code xử lý bằng `Locale.ROOT`, chống case/Unicode confusable theo policy.
- JSON thêm `system`, `status`, `companyId`, `createdBy`, `authVersion`, role hoặc scope nội bộ không được mass-assign.
- Response không chứa password hash, token, Redis key hoặc audit payload nội bộ.
- Error cross-tenant không tiết lộ resource tồn tại nếu policy yêu cầu 404 concealment.

## 8. Security quality gates

### Gate bắt buộc cho pull request

- Security regression tests active, không `@Disabled`.
- Unit suite xanh.
- Integration/Flyway suite xanh với PostgreSQL Testcontainers.
- Endpoint-security inventory không có `UNCLASSIFIED`.
- Không còn direct Controller → Repository dependency.
- Không có `hasRole/hasAuthority` mới cho scoped business operation.
- Static analysis không có Critical/High chưa triage.

### Coverage có ý nghĩa bảo mật

- `PermissionGuard`, global-admin resolver, scope validator và effective-access resolver: 100% branch/decision coverage cho các nhánh allow/deny.
- Mỗi allow case có ít nhất một deny đối nghịch.
- Mutation test bắt buộc trên các điều kiện `GLOBAL`, `system`, status, expiry, resource equality và company ownership.
- Coverage tổng thể không thay thế cross-tenant và mutation tests.

### Gate phát hành

- Tất cả test ở mục 7 xanh trên CI sạch.
- Dữ liệu production audit không còn invariant violation chưa xử lý.
- Migration đã chạy thử trên snapshot ẩn danh và có rollback/restore rehearsal.
- Scoped account bị kiểm tra thực tế qua HTTP không mở được endpoint global.
- Security logs/metrics không chứa secret hoặc PII ngoài policy.
- Docker/Testcontainers phải khả dụng; không chấp nhận release dựa chỉ trên unit tests.

## 9. Audit, metric và phản ứng sự cố

Ghi audit cho:

- Tạo role reserved bị chặn.
- Assignment ADMIN sai scope bị chặn.
- Scope/resource mismatch.
- Cross-company role assignment bị chặn.
- Malformed effective-access row bị bỏ qua.
- Grant/revoke role/permission/scope/assignment thành công.

Metric đề xuất:

- `security.rbac.denied{reason}`
- `security.rbac.scope_invariant_violation{type}`
- `security.rbac.reserved_role_attempt`
- `security.rbac.cross_tenant_attempt{resource}`

Không đưa username đầy đủ, token, password, payload hoặc raw UUID nhạy cảm vào metric label. Audit detail có kiểm soát truy cập riêng.

Nếu phát hiện `ROLE_ADMIN` đã được sinh từ scoped assignment trong môi trường thật:

1. Deactivate assignment vi phạm.
2. Tăng `authVersion`/revoke all sessions của user liên quan.
3. Rà audit log cho Users, Access Control, Swagger/Actuator và mọi mutation global.
4. Xác định dữ liệu bị đọc/sửa và phục hồi nếu cần.
5. Không xóa audit evidence.

## 10. Chiến lược rollout và rollback

1. Deploy code đọc tương thích + audit trước, chưa chặn dữ liệu legacy.
2. Chạy report invariant trên staging và snapshot production ẩn danh.
3. Cleanup/quarantine dữ liệu.
4. Bật enforcement service.
5. Chạy full HTTP cross-tenant smoke.
6. Bật DB constraint/index bổ sung nếu khả thi.

Rollback application không được khôi phục đường scoped ADMIN. Nếu cần rollback chức năng khác, giữ security hotfix và migration cleanup. Trước migration dữ liệu phải có backup/restore đã kiểm thử.

## 11. Definition of Done

- [x] Không scoped/dynamic role nào tạo `ROLE_ADMIN`.
- [x] Global system admin hợp lệ vẫn hoạt động.
- [x] Reserved role codes bị chặn ở API và service.
- [x] Ma trận scope-resource được enforce.
- [x] Non-global empty scope không assign được.
- [x] Company-owned role không gán chéo tenant/global.
- [x] `/auth/me` và `PermissionGuard` nhất quán.
- [x] Wire `WAREHOUSE` canonical, alias legacy có test.
- [ ] Dữ liệu legacy đã audit và cleanup.
- [ ] Architecture/endpoint inventory gate hoạt động.
- [ ] Mọi aggregate có cross-tenant negative test.
- [ ] Unit, integration, migration, HTTP và mutation gates xanh.
- [x] Không test security nào bị disable hoặc làm yếu assertion.
- [x] 49/49 test security mục tiêu xanh, 0 failure, 0 error, 0 skipped.
- [x] Guard từ chối an toàn với authentication/quyền/resource null, rỗng hoặc không hỗ trợ.
- [x] Response DTO không lộ password/hash/token/secret/Redis key hay `authVersion`.
- [ ] Có bằng chứng chạy CI sạch và review độc lập trước phát hành.

## 12. Bộ nghiệm thu bắt buộc sau khi sửa xong

Bộ test này phải luôn active. Kết quả RED trước khi sửa là bằng chứng test bắt được lỗi, không phải lý do để bỏ test. Sau khi hoàn tất Pha 1–7, thực hiện tuần tự các gate dưới đây trên checkout sạch và DB test độc lập.

### 12.1 Gate security mục tiêu

Chạy toàn bộ 7 lớp kiểm thử:

```powershell
mvn -o "-Dtest=ScopedAdminEscalationSecurityTest,PermissionGuardFailClosedSecurityTest,AccessScopeInvariantSecurityTest,AccessScopeAuthorizationMatrixSecurityTest,WarehouseScopeWireContractSecurityTest,AuthorizationArchitectureSecurityTest,SensitiveResponseDataMinimizationSecurityTest" test
```

Tiêu chí đạt: **49/49 pass, 0 failure, 0 error, 0 skipped**. Không được dùng `@Disabled`, assume, exclude Surefire, catch rộng hoặc nới assertion để đạt gate.

Phạm vi được khóa:

| Lớp test | Bảo đảm bắt buộc |
|---|---|
| `ScopedAdminEscalationSecurityTest` | Scoped/dynamic ADMIN không thể trở thành global admin |
| `PermissionGuardFailClosedSecurityTest` | Guard deny với principal giả mạo, input null/rỗng/không hỗ trợ; revoke có hiệu lực ở request kế tiếp |
| `AccessScopeInvariantSecurityTest` | Role ownership, empty membership, `/auth/me` và malformed legacy rows fail closed |
| `AccessScopeAuthorizationMatrixSecurityTest` | Kiểm tra đủ 15 cặp `ScopeType × ResourceType`, chỉ 6 cặp hợp lệ được allow |
| `WarehouseScopeWireContractSecurityTest` | Request alias tương thích và response canonical `WAREHOUSE` |
| `AuthorizationArchitectureSecurityTest` | Không Controller nào truy cập Repository trực tiếp để bỏ qua service guard |
| `SensitiveResponseDataMinimizationSecurityTest` | DTO xác thực/quyền không phát trường bí mật hoặc trạng thái bảo mật nội bộ |

### 12.2 Gate hồi quy toàn hệ thống

```powershell
mvn -o test
mvn -o verify
```

- `test` phải xanh toàn bộ, không chỉ 7 lớp mục tiêu.
- `verify` phải chạy migration/integration với PostgreSQL Testcontainers; môi trường thiếu Docker là **chưa đủ bằng chứng phát hành**, không được coi là pass.
- JaCoCo phải giữ 100% branch/decision coverage trên các nhánh allow/deny của guard, global-admin resolver, scope validator và effective-access resolver.
- Mutation test phải chứng minh việc bỏ bất kỳ điều kiện `GLOBAL`, `system`, active, expiry, company ownership hoặc resource equality đều làm test thất bại.

### 12.3 Gate HTTP và cô lập dữ liệu thực

Trên mỗi aggregate ở Pha 7, tạo tenant A/B và kiểm tra cùng một access token của A:

1. GET/list/search/export không trả row, count, ID, tên, trạng thái hoặc timing/error phân biệt được tài nguyên B ngoài policy.
2. PATCH/PUT/DELETE/action dùng ID của B trả 403/404 theo policy và dữ liệu B không đổi.
3. Thử giả mạo `companyId`, `plantId`, `warehouseId`, role/scope/status/system field trong path, query, body và header; backend vẫn resolve scope từ identity + DB.
4. Sau revoke/deactivate/expire, request kế tiếp với token cũ bị từ chối và `/auth/me` không còn quyền.
5. Response, error, log, metric và audit dành cho caller không chứa password/hash, access/refresh token, secret, Redis key, stack trace, SQL hoặc raw payload nhạy cảm.
6. Kiểm tra song song hai request assign/revoke và thay membership để chứng minh không có cửa sổ TOCTOU mở rộng quyền.

Mỗi ca phải lưu bằng chứng request/response đã khử dữ liệu nhạy cảm và snapshot DB trước/sau. Chỉ dùng dữ liệu tổng hợp hoặc snapshot production đã ẩn danh.

### 12.4 Gate phân tích và review độc lập

- SAST, dependency/SCA và secret scan không còn Critical/High chưa xử lý hoặc chưa có risk acceptance có thời hạn.
- DAST chạy bằng tài khoản global, Company, Plant, Warehouse và không quyền; riêng IDOR/BOLA phải bao phủ read/write/export/bulk endpoints.
- Reviewer độc lập đối chiếu endpoint inventory, policy matrix, migration report và bằng chứng CI; người viết fix không tự phê duyệt ngoại lệ bảo mật của chính mình.
- Sau khi deploy staging, chạy lại security target, full suite và HTTP cross-tenant smoke trên artifact đúng digest dự kiến phát hành.

### 12.5 Điều kiện đổi trạng thái RED → GREEN

Chỉ đổi trạng thái khi đồng thời có đủ: 49/49 security target pass; full `test` và `verify` pass; không invariant violation chưa xử lý; HTTP cross-tenant matrix pass; SAST/SCA/secret/DAST gate pass; migration và rollback rehearsal pass; bằng chứng CI + review độc lập được lưu. Bất kỳ gate nào thiếu bằng chứng đều được xem là **chưa đạt**, không phải “đạt có điều kiện”.

## 13. Biên bản thực hiện ngày 2026-08-16

Đã triển khai:

- Chỉ sinh `ROLE_ADMIN` từ legacy global system role hợp lệ hoặc dynamic assignment được query authoritative xác minh đủ system/company/GLOBAL/status/expiry/no-resource.
- Loại bỏ hoàn toàn semantics bảo mật dựa trên role `name`; code `ADMIN`/`ROLE_ADMIN` tùy biến và scoped bị chặn.
- `PermissionGuard` fail closed với authentication/principal/permission/resource không hợp lệ và không tin authority string của principal lạ.
- Enforce toàn bộ ma trận `ScopeType × ResourceType`, non-global membership, system ADMIN scope và company ownership trước khi lưu assignment.
- `/auth/me` phân loại bằng `scopeType`, bỏ row malformed và không nâng non-global rỗng thành GLOBAL.
- Wire request nhận `WAREHOUSE` + alias `WAREHOUSE_GROUP`; response chỉ phát `WAREHOUSE`.
- V63 tạo constraint `NOT VALID` chặn reserved custom ADMIN mới và view `rbac_scope_invariant_violations` để audit dữ liệu legacy mà không tự sửa/nới quyền.
- Sửa constructor binding của `JwtProperties` và cô lập Production E2E bằng profile test để full verification khởi động đúng cấu hình.

Bằng chứng local:

- Security target: **49 test, 49 pass, 0 failure, 0 error, 0 skipped**.
- Full Maven `test`: **1.112 test, 1.112 pass, 0 failure, 0 error, 0 skipped**.
- Full Maven `verify` với PostgreSQL/Redis Testcontainers và Flyway V63: **1.112 unit + 132 integration, 0 failure, 0 error, 0 skipped**.
- JPQL global-system-admin chạy trên PostgreSQL thật: `UserRoleAssignmentRepositoryIT` **14/14 pass**.
- Migration V1→V63 và idempotency checks: `FlywayMigrationIT` **17/17 pass**.

Còn thiếu trước khi đổi GREEN/phát hành:

- Chạy `SELECT * FROM rbac_scope_invariant_violations` trên staging và snapshot production đã ẩn danh; cleanup/quarantine đến khi rỗng.
- Hoàn thành HTTP cross-tenant matrix cho mọi aggregate và lưu bằng chứng request/response đã khử dữ liệu nhạy cảm.
- Chạy mutation test, SAST, dependency/SCA, secret scan và DAST/IDOR-BOLA.
- Rehearsal backup/restore/rollback, kiểm tra artifact đúng digest trên staging và review bảo mật độc lập.

Chỉ khi toàn bộ checklist trên hoàn tất mới được đổi trạng thái tài liệu từ **RED** sang **GREEN**.

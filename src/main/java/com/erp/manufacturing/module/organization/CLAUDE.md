# module/organization — RBAC Invariants

> Tách từ `CLAUDE.md` §10.6 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/organization/**`
> (`roles`, `permissions`, `access_scopes`, `user_role_assignments`, `AccessControlService`).
> Bất biến về auth/login/session nằm ở `../auth/CLAUDE.md` và `../../common/security/CLAUDE.md`.

## Entity Model (từ `AGENTS.md` §8)

Dynamic RBAC — 6 entity: `roles`, `permissions`, `role_permissions`, `access_scopes`,
`access_scope_resources`, `user_role_assignments`. Scope có thể là `COMPANY`, `PLANT`, hoặc `WAREHOUSE`.

> **Không hardcode chỉ `ADMIN`/`MANAGER`/`OPERATOR` cho nghiệp vụ mới.** Mọi permission check trong
> business logic mới phải đi qua `PERM_*` string + `AccessControlService`/`*PermissionGuard`, không
> so sánh role name trực tiếp — role/permission gán qua `role_permissions` runtime, không compile-time.

## Bất biến nghiệp vụ

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B30 | Scope kế thừa xuống: quyền ở `COMPANY` ⇒ có quyền trên mọi `PLANT`/`WAREHOUSE` con. Quyền ở `WAREHOUSE` **không** lan sang warehouse khác | `PermissionGuardTest` |
| B31 | Guard phải **fail-closed**: resource không tồn tại / thiếu context → `false`, không throw, không mặc định `true` | `BomPermissionGuardTest`, `InventoryPermissionGuardTest`, `PlanningPermissionGuardTest` |
| B32 | Assignment hết hạn / `INACTIVE` / role `INACTIVE` / org `INACTIVE` ⇒ **không** cấp quyền | `UserRoleAssignmentRepositoryIT` (T4.3) — 6 case chạy JPQL thật qua Testcontainers, nghiệm thu mutation JPQL đã xác nhận đỏ đúng case. **[2026-08-01]** `findActiveScopeResourcePermissionRowsForUser` (`GET /api/v1/auth/me`) là ứng dụng thứ 3 của cùng bất biến — lặp đủ 5 điều kiện, thêm 5 case B32-shape + 2 case riêng của LEFT JOIN (`_globalScopeWithoutResource...`, `_scopeWithMultipleResources...`) vào cùng file |
| B37 | Separation of duties: người thực hiện **không** được tự duyệt phần vượt định mức của mình → `PERM_MATERIAL_ISSUE_OVERRIDE` và `PERM_PRODUCTION_RECEIPT_APPROVE` **không** grant cho `OPERATOR` | `V24` seed + `PermissionCatalogTest` (T1.4) — **lưu ý:** catalog test chỉ kiểm 2 permission **đã được seed**, **chưa** kiểm role nào được grant (cần đọc `role_permissions` ở `T4`) |
| B80 | `AccessControlService.resolveMyScopes(userId)` (`GET /api/v1/auth/me`): `defaultPlantId` phải **deterministic** — sort `scopes[]` theo `(companyCode, plantCode)` **trước** khi chọn phần tử `PLANT` đầu tiên, không phụ thuộc thứ tự JPQL/`HashMap`/`Set` trả về | `AccessControlServiceTest.resolveMyScopes_defaultPlantId_isDeterministic_regardlessOfInputOrder` — feed cùng dữ liệu 2 thứ tự khác nhau, assert cùng kết quả. Nghiệm thu mutation: bỏ bước `sort` ⇒ case đỏ |
| B88 | **[C2-4]** Role/Scope lifecycle (`GET`/`PATCH`/`activate`/`deactivate`) gác bằng `hasPermission(auth, 'PERM_ACCESS_MANAGE')` — **không** permission mới, **không** `hasResourceAccess` (role/scope là cấu hình toàn hệ thống, không company/plant). Role `is_system = true` (ADMIN/MANAGER/OPERATOR) **không bao giờ** deactivate được, kể cả bởi `admin` ⇒ `OPERATION_NOT_ALLOWED` (422) — chặn ở `deactivate` là đủ, vì role hệ thống không có đường vào `INACTIVE` nên `activate` không cần chặn thêm. `PATCH` chỉ nhận `name`/`description`; `code`/`is_system`/`scopeType` immutable | `AccessControlServiceTest.deactivateRole_systemRole_throwsOperationNotAllowedBeforeSaving`, `.deactivateRole_customRole_succeeds`, `AccessControlMethodSecurityTest` (7 method mới: deny + verify guard) |
| B108 | **[FE contract fix, 2026-08-06]** `POST /plants/{id}/activate`, `POST /warehouses/{id}/activate` (mới) — **không** permission mới, tái dùng `PERM_ORG_MANAGE` đúng scope của `deactivate*` tương ứng. Idempotent (gọi trên record đã `ACTIVE` là no-op 200). **Chặn nếu cha đang `INACTIVE`** (`OPERATION_NOT_ALLOWED`, 422) — quyết định tường minh của user (`AskUserQuestion`), là bất biến **đầu tiên** áp cho chiều *activate*; trước đó check "cha phải `ACTIVE`" chỉ tồn tại ở chiều *create* (`createPlant`/`createWarehouse`). `Company` **cố ý không có** endpoint activate — FE chỉ hỏi về Plant/Warehouse/Item, mở rộng sang Company là ngoài phạm vi yêu cầu | `OrganizationServiceTest.activatePlant_*`, `.activateWarehouse_*` (happy path, idempotent, parent-inactive chặn trước khi save), `OrganizationMethodSecurityTest.activatePlant_*`/`.activateWarehouse_*` (deny+allow), `OrganizationControllerTest.activatePlant_*`/`.activateWarehouse_*` |

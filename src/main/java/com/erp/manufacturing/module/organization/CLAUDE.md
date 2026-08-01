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
| B32 | Assignment hết hạn / `INACTIVE` / role `INACTIVE` / org `INACTIVE` ⇒ **không** cấp quyền | `UserRoleAssignmentRepositoryIT` (T4.3) — 6 case chạy JPQL thật qua Testcontainers, nghiệm thu mutation JPQL đã xác nhận đỏ đúng case |
| B37 | Separation of duties: người thực hiện **không** được tự duyệt phần vượt định mức của mình → `PERM_MATERIAL_ISSUE_OVERRIDE` và `PERM_PRODUCTION_RECEIPT_APPROVE` **không** grant cho `OPERATOR` | `V24` seed + `PermissionCatalogTest` (T1.4) — **lưu ý:** catalog test chỉ kiểm 2 permission **đã được seed**, **chưa** kiểm role nào được grant (cần đọc `role_permissions` ở `T4`) |

# module/uom — Business Rule Invariants

> Thêm ở `C2-3` (2026-08-04, track `C2-*` — `FRONTEND_ALIGNMENT_ROADMAP.md §8`). Chỉ nạp khi agent
> làm việc trong `module/uom/**`.

Unit of measure master data (`BACKEND_CAPSTONE2_API_GAPS.md §3.1`). Bảng `uoms`, migration `V42`
(schema) + `V43` (seed permission).

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B82 | `uoms` là **global**, không có `company_id`. FE gap doc liệt kê `GET /uoms` không lọc theo company/plant — khác **mọi** master data khác trong repo (`Item`/`Supplier`/`BomHeader` đều FK `company_id`). Đảo lại quyết định này sau (nếu cần) là thêm cột nullable, rẻ hơn ngược lại | — (thiết kế schema, không có nhánh code để test) |
| B83 | `code` **immutable sau khi tạo** — không phải validate runtime, mà là `UomUpdateRequest` **không có field `code`**. Đổi ý sau, thêm field đó vào DTO là quyết định tường minh, không phải "quên chặn" | `UomServiceTest.update_hasNoCodeField` (kiểm `RecordComponent`, không phải hành vi runtime) |
| B84 | `activate`/`deactivate` **idempotent** — gọi `activate` trên UOM đã `ACTIVE` là no-op 200, không lỗi. Khác `RoutingService.activate` (deactivate kèm "revision cũ"): UOM không có khái niệm revision nên không có gì để xung đột | `UomServiceTest.activate_alreadyActive_isNoOp`, `.deactivateThenActivate_roundTrips` |
| B85 | Validate "không deactivate khi đang được tham chiếu" (spec §3.1) là **no-op đúng nghĩa hiện tại**, không phải bug: chưa bảng nào có FK tới `uoms` (`items.unit` vẫn là `String` tự do). Khi `items.uom_id` tồn tại (phase riêng, xem "KHÔNG làm gì" ở `NEXT_PHASE_PLAN.md` lịch sử `C2-3`), `UomService.deactivate` là chỗ thêm reference-count check | — (chưa có consumer nào để test được) |

## 🔴 Phát hiện khi kiểm chứng qua HTTP thật — không phải bug của module này

`PermissionGuard.hasPermission(authentication, code)` (dùng cho cả `PERM_UOM_READ`/`_MANAGE`, giống
`PERM_ORG_READ`/`_MANAGE` của `OrganizationService.listCompanies`/`createCompany`) chỉ đọc assignment
có **`AccessScope.scopeType = GLOBAL`** (`existsActivePermissionInScopeType`). Trong dữ liệu hiện tại
**chỉ `admin`** có assignment scope đó (`GLOBAL_ALL`, seed từ `V7`).

⇒ Account seed ở `C2-5` (`manager.a`, `operator.a` — scope `PLANT`) trả **403** trên mọi endpoint UOM,
**kể cả `GET /uoms` dù `V43` đã cấp `PERM_UOM_READ` cho `OPERATOR`.** Đã xác nhận **không riêng UOM**:
`manager.a` gọi `GET /companies` (`listCompanies`, cùng cơ chế) cũng 403 — đây là đặc điểm **có sẵn
toàn repo** của `hasPermission`, không phải lỗi `C2-3` gây ra.

**Hệ quả cho ai test/probe sau này:** muốn kiểm phân quyền UOM (hoặc bất kỳ permission nào gác bằng
`hasPermission` thuần, không có resource) qua HTTP thật, **phải dùng account có assignment
`scope_type = GLOBAL`** — hiện tại chỉ `admin` thoả điều kiện đó. Seed `manager.a`/`operator.a` của
`C2-5` **không dùng được** cho việc này (chúng cố ý scope `PLANT` để test isolation, đúng mục đích
khác). Đừng đọc 403 đó là "V43 seed sai" — đã kiểm bằng mutation (xem bản ghi `CLAUDE.md §0.26`) và
bằng đối chiếu với `OrganizationService` là đúng thiết kế.

**Nếu muốn `MANAGER`/`OPERATOR` scope-PLANT thật sự dùng được UOM**, đó là quyết định thiết kế khác
hẳn (đổi `hasPermission` thành cơ chế khác, hoặc thêm GLOBAL-scope assignment cho họ) — ngoài phạm vi
`C2-3`, cần hỏi lại user.

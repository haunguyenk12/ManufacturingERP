Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.



---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

do not delete 47 lines above

# Manufacturing ERP – Capstone Project
> **Role**: Backend Developer | **Stack**: Spring Boot 3.x · PostgreSQL · Redis · Java 17

---

## 0. TRẠNG THÁI DỰ ÁN  *(cập nhật: 2026-07-30)*

> **Đọc mục này TRƯỚC KHI sửa bất kỳ dòng code nào.**
> Thứ tự tài liệu bắt buộc: `CLAUDE.md` (file này) →
> `NEXT_PHASE_PLAN.md` (phase nghiệp vụ đang chạy) → `TEST_IMPROVEMENT_PLAN.md` (phase test).
> *(`AGENTS.md` đã xoá 2026-07-25 — nội dung được phân bổ vào `CLAUDE.md` và các file split, xem "BẢN ĐỒ TÀI LIỆU" ở cuối file này.)*

### 0.1 Phase Hiện Tại

| | |
|---|---|
| **Phase đang chạy** | *(không có — `C2-1` vừa xong)* |
| **Phase trước** | **`C2-1` – Audit Logs read API** ✅ **HOÀN THÀNH** (2026-08-06). `GET /audit-logs`, `GET /audit-logs/{id}` (đợt 1, `changes[]` luôn rỗng — FE đã xác nhận dùng được). `AuditLogQueryService`/`AuditLogController` mới trong `common/audit/` (tách khỏi `AuditLogService`, service đó chỉ publish). `PERM_AUDIT_READ` **ADMIN-only** (chốt với user). Migration `V54` (cột `plant_id`, chỉ dừng ở schema — **không** populate real-time, quyết định phạm vi có chủ đích) + `V55` (seed permission). 🔴 Phát hiện biến thể mới của lớp lỗi `lower(bytea)` (`§0.24`): tham số `Instant` chỉ xuất hiện ở vế `IS NULL` làm Postgres không suy được type — sửa bằng `cast(:from as timestamp) IS NULL`, cùng công thức `cast(... as string)` đã dùng cho String, lần đầu áp dụng cho `Instant`. Bản ghi: **§0.36** |
| **Phase trước đó** | **`D8c` – Forgot-password / Account Recovery** ✅ **HOÀN THÀNH** (2026-08-06). Trả nốt 3/3 nợ #6. Không migration. Bất biến `B101`. Bản ghi: **§0.35** |
| **Phase `D8b`** | **`D8b` – Absolute Session Timeout** ✅ **HOÀN THÀNH** (2026-08-03). `SESSION_ABSOLUTE_TIMEOUT` (401) sau 30 ngày kể từ **login** + force logout mọi phiên; `sessionCreatedAt` lưu ở **companion key** `auth:refresh:{userId}:{tokenId}:meta`, **carry-forward** qua mỗi lần rotate. **Không migration** (thuần Redis), wire **additive**. Bất biến **`B81`**. Trả **2/3** nợ #6 — **`D8c` vẫn mở**. Bản ghi: §0.23 |
| **Phase `D8a`** | **`D8a` – Refresh Token Reuse Detection (RTR)** ✅ HOÀN THÀNH (2026-08-03). `TOKEN_REUSE_DETECTED` (401) + force logout **cả** refresh token **lẫn** device session; thứ tự rotate lưu-mới→mark-used→xoá-cũ. Không migration, wire additive. Bất biến **`B80`**. Giới hạn "race double-submit" mà phase này chấp nhận **đã đóng**, xem §0.32. Bản ghi: §0.22 |
| **Phase kế tiếp** | `C2-2` — Inventory Lot lifecycle API, làm ngay sau `C2-1`. Track `P*` và `D8` đã đóng hết (nợ #6 trả đủ 3/3). Xem `NEXT_PHASE_PLAN.md` để biết còn gì chưa làm. |
| **Migration mới nhất** | **`V53__add_work_order_closed_status.sql`** (`P6`) — thêm `CLOSED` vào `chk_work_orders_status` + cột `work_orders.closed_at`. `D8c` **không có migration** (thuần Redis) |
| **Baseline test** | 180 case / 44 class → T0+T1: 218 → T3: 257 → T2/T4/T5: 281 case / 57 class → F1: 289 → F2: 303 → F3: 320 → F4: 347 → F5-A: 356 → F5-B: 365 → F6: 391 → D1: 396 → D9+D10: 402 → D4: 408 → D5: 412 → D6: 416 → D7: 450 → D7b: 510 → D11: 516 → F7: 521 → F8: 545 → F9: 549 → F10: 556 case unit + 59 case IT / 10 class IT → `GET /auth/me` (2026-08-01): 571 case unit + 66 case IT → `D8a` (2026-08-03): 577 case unit → `D8b` (2026-08-03): 586 case unit + 66 case IT / 10 class IT → bugfix `lower(bytea)` + missing-param 500 (2026-08-04): 587 case unit + 70 case IT / 11 class IT → `C2-5` (2026-08-04): 593 case unit + 73 case IT / 11 class IT → `C2-3` (2026-08-04): 617 case unit + 78 case IT / 12 class IT → `C2-4` (2026-08-05): 661 case unit + 78 case IT / 12 class IT → `C2-6` (2026-08-05): 690 case unit + 79 case IT / 12 class IT → `C2-7` (2026-08-05): 759 case unit + 80 case IT / 12 class IT → `C2-8` (2026-08-05): 791 case unit + 87 case IT / 13 class IT → `P3` (2026-08-05): 821 case unit + 88 case IT / 13 class IT → concurrent refresh-token race (2026-08-05): 831 case unit + 88 case IT / 13 class IT → `P5` (2026-08-06): 850 case unit + 89 case IT / 13 class IT → `P6` (2026-08-06): 864 case unit + 89 case IT / 13 class IT → `D8c` (2026-08-06): 885 case unit + 89 case IT / 13 class IT (`+21` unit: `PasswordResetTokenServiceTest` +6 (mới), `AuthServiceTest` +5, `AuthMethodSecurityTest` +2 (mới), `AuthControllerTest` +5, `AdminControllerTest` +1 (mới), `SensitiveRequestToStringTest` +2; `+0` IT — không migration, không JPQL mới) → **`C2-1` (2026-08-06): 898 case unit + 98 case IT / 14 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker (`+13` unit: `AuditLogQueryServiceTest` +5 (mới), `AuditLogMethodSecurityTest` +4 (mới), `AuditLogControllerTest` +4 (mới); `+9` IT từ `AuditLogRepositoryIT` (mới, **class IT thứ 14**) + `+1` case `FlywayMigrationIT.migrate_v55_grantsAuditReadToAdminOnly`) |
| **Coverage tool** | ✅ JaCoCo 0.8.12 — **unit một mình: line 74.3% / branch 59.5%**; **unit + IT: line 80.5% / branch 64.2%** (cả hai đo lại 2026-08-03 sau `D8b`; số unit+IT trước đó 80.1% / 63.9% là của `F10`). ⚠️ **Xu hướng đã xác nhận tám phase liên tiếp:** `D7` +34 case ⇒ +0.6 line; `D7b` +60 ⇒ +0.8; `D11` +6 ⇒ +0.0 / +0.2; `F7` +12 ⇒ +0.2 / +0.2; `F8` +40 ⇒ +0.5 / +0.4; `F9` +8 ⇒ +0.1 / +0.0; `F10` +7 unit / +2 IT ⇒ +0.0 / +0.4; **`D8b` +9 unit ⇒ +0.1 line / +0.1 branch** (unit một mình). 🔴 **Số ở hàng này là số đo sau `D8b`; `§0.24` (bugfix), `§0.25` (`C2-5`), `§0.26` (`C2-3`), `§0.27` (`C2-4`), `§0.28` (`C2-6`), `§0.29` (`C2-7`), `§0.30` (`C2-8`), `§0.31` (`P3`), `§0.32` (concurrent refresh-token race), `§0.33` (`P5`), `§0.34` (`P6`), `§0.35` (`D8c`), `§0.36` (`C2-1`) KHÔNG đo lại** — đừng đọc nó như đã tính các phần đó. **Coverage không đo được contract** — thước đo thật là nghiệm thu mutation (§0.15–§0.27). `C2-5` là ví dụ thêm: `PermissionCatalogTest` phủ 100% đường permission mà **không** thấy 12 quyền chỉ `ADMIN` có, vì "tồn tại" và "được cấp cho role" là hai sự thật khác nhau. `D8b` là ví dụ sắc nhất tới nay: mutation #1 của nó (carry-forward stamp `now`) **giữ nguyên 100% coverage, response byte-identical, mã lỗi và HTTP status không đổi** — tính năng thành no-op hoàn toàn mà mọi thước đo trừ assertion đối số đều báo xanh. Xem cảnh báo cách đo ngay dưới bảng |
| **Bảng theo dõi phase** | Nghiệp vụ `P*`: `MANUFACTURING_GAP_ROADMAP.md §2.1` · Kiểm thử `T*`: `TEST_IMPROVEMENT_PLAN.md §0` (xong hết) · Căn chỉnh FE `F*`: `FRONTEND_ALIGNMENT_ROADMAP.md §1` (lịch sử, đã đóng ở `F10`) · Trả nợ `D*`: cùng file **§6** · **Capstone 2 `C2-*` (đang chạy): cùng file §8** — bảng phase §8.1, trạng thái checklist 7/13 §8.0, bẫy từng phase §8.8 · `NEXT_PHASE_PLAN.md` (phase đang chạy) |

> **Track `F*` là gì:** `OmniPlant_MVP_Production_Backend_Handoff.docx` là đặc tả tích hợp viết
> **ngược từ frontend đã implement**, nên field name + business rule là phần cố định, tên endpoint
> là đề xuất. Đối chiếu cho thấy repo thiếu **3 module** (Sales Order, Routing, QC) và có **1 đảo
> ngược ngữ nghĩa** (xem §0.5). `F*` gồm 6 phase; `F2`/`F4`/`F6` trùng nội dung với `P2`/`P4`/`P6`
> của `MANUFACTURING_GAP_ROADMAP.md`, chỉ sắp lại thứ tự theo dependency của spec.
>
> **3 quyết định đã chốt với user (2026-07-26):**
> 1. **Giữ `UUID`** — spec ghi `id: number`, FE đổi TS type sang `string`. Không đụng schema/RBAC.
> 2. **Làm đủ 3 module thiếu**, chia phase tuần tự `F1`→`F6`.
> 3. **Sửa tại chỗ trên `/api/v1`**, không mở `/api/v2` — miễn trừ có chủ đích rule `A1`/`C-A1`
>    vì chưa có client production nào ngoài chính FE này. Mỗi phase phải ghi "Breaking Changes"
>    vào `NEXT_PHASE_PLAN.md` và **sửa** test cũ theo `R10` (không xoá).

> ⚠️ **Hai cái bẫy khi đo lại số liệu — cả hai đều từng làm sai số đã ghi (phát hiện ở `D5`):**
>
> 1. **Đếm test case:** lấy số ở dòng tổng `Tests run:` của `mvn -o verify`, **đừng** cộng file XML
>    trong `target/surefire-reports/` — thư mục đó giữ lại report của class đã **đổi tên/xoá** từ
>    những lần build cũ (`InventoryServiceTest.xml` từ `T5` làm lệch **+10 case**). Muốn đọc file thì
>    `mvn clean` trước.
> 2. **Coverage:** `pom.xml` chỉ có **một** `prepare-agent` và bind `jacoco:report` vào phase **`test`**
>    — tức report được sinh **trước khi** Failsafe chạy `*IT`. Nên:
>    - `mvn -o clean verify` ⇒ `target/site/jacoco` là **unit một mình** (70.4%).
>    - Muốn số **unit + IT** (con số của series lịch sử) phải chạy `mvn -o verify` rồi
>      **`mvn -o jacoco:report`** lần nữa — lúc đó `jacoco.exec` đã có cả hai (agent `append=true`).
>    - `mvn -o verify` **không clean** cũng ra số unit+IT nhưng vì `jacoco.exec` còn dữ liệu IT của
>      **lần chạy trước** — số đúng do ăn may, không phải do thiết kế. Đừng dựa vào nó.



> **T0+T1 đã đạt:** 22 assertion method-security giờ có `verify(<guard>)` khoá chặt chuỗi `PERM_*`
> (sai tên permission ⇒ test đỏ, đã nghiệm thu mutation trên `BomService`/`SupplierService`/`InventoryService`);
> mỗi class có ≥ 1 nhánh allow; `PermissionCatalogTest` chống lệch code↔Flyway; bất biến **B13**
> (`BLOCKED` bị chặn issue/receipt/reserve) đã có test bảo vệ.

> **T3 đã đạt:** 3 class lõi bảo mật trước đây trần trụi nay được khoá bằng test (218 → **257 case**).
> `JwtTokenProvider`: round-trip `sub`/`roles`/`jti`, `TOKEN_EXPIRED` vs `TOKEN_MALFORMED`, đường
> expired-token của logout/refresh. `TokenStoreService`: **key string + TTL** của mọi Redis key ở §4.4,
> nhánh SCAN rỗng không gọi `delete`, blacklist TTL ≤ 0 là no-op, fail-counter chỉ set expire ở lần đầu.
> `AuthService`: rotation verify **cả** xoá token cũ lẫn lưu token mới; 3 nhánh `REFRESH_TOKEN_EXPIRED`
> đều chứng minh `never().saveRefreshToken`.
> **Giới hạn:** Redis vẫn là mock ⇒ TTL/SCAN thật chưa được kiểm — thuộc `T4`.

> **T2 đã đạt:** contract `{code,result,message}` (`.claude/rules/error-handling.md §5.1`) giờ có test
> ở đúng nơi trước đây trần trụi — `GlobalExceptionHandler` (cả 6 nhánh + `X-Trace-Id`) và 3 controller
> đại diện (`AuthController`, `WorkOrderController`, `InventoryController`), kể cả Idempotency-Key
> header forward (`WorkOrderController.issueComponent`, `InventoryController.receive`).
> `@WithMockUser` được dùng lần đầu trong repo (`I9` đã xử lý).
> **Giới hạn (bản `T2`, đã thu hẹp ở `D7` — nay còn 9/20, xem §0.4 #2):** 15/18 controller còn lại vẫn chưa có test (có chủ đích, xem `NEXT_PHASE_PLAN.md` T2 §11);
> `@WebMvcTest` trong repo này auto-detect mọi bean `Filter` trên classpath dù không `@Import` tường
> minh, nên mỗi `@WebMvcTest` mới (kể cả ở `P2`) cần `@MockBean` đủ 6 bean hạ tầng bảo mật
> (`IpExtractor`, `JwtTokenProvider`, `TokenStoreService`, `UserDetailsService`, `RedisTemplate`,
> `RateLimitProperties`) để context load được — chi tiết `TEST_IMPROVEMENT_PLAN.md` mục "Cập nhật sau T2".

> **T4+T5 đã đạt:** kích hoạt `Testcontainers` lần đầu (`AbstractPostgresIntegrationTest`, Singleton
> Container pattern) + `maven-failsafe-plugin` (chạy `*IT.java` tách khỏi `mvn test`). `FlywayMigrationIT`
> xác nhận `V1..V24` chạy sạch trên DB trống. `UserRoleAssignmentRepositoryIT` (6 case) chạy thật JPQL
> RBAC — bịt điểm mù bất biến **B32** (assignment/role/permission/scope hết hạn/`INACTIVE` bị loại đúng
> ở tầng JPQL, không chỉ mock). T5 dọn 5 khoản nợ nhỏ (`WipTransactionServiceTest` dùng helper thật,
> đổi tên test tautology, đổi tên `InventoryServiceTest`→`InventoryMovementServiceTest`, bỏ magic string
> `"BIZ_100"`, rà 71 chỗ `isInstanceOf(AppException.class)` sửa 2 chỗ thiếu `ErrorCode`).
> **Phát hiện quan trọng (bug thật, chưa sửa — quyết định của user):** `StockBalanceRepositoryIT` lộ ra
> `StockBalanceRepository.aggregateAvailableQuantities`/`aggregatePlanningQuantities`/
> `aggregateAvailableQuantitiesByWarehouse` dùng path expression `b.lot.status` khiến Hibernate sinh
> INNER JOIN, loại bỏ mọi `StockBalance` không lot-tracked khỏi kết quả — vi phạm bất biến B3, ảnh hưởng
> `InventoryAvailabilityService`/MRP. Ghi thành nợ kỹ thuật ở `module/inventory/CLAUDE.md`, IT hiện pin
> lại hành vi sai này (có giải thích) chờ sửa ở commit nghiệp vụ riêng — **quan trọng cho P2** vì P2 đụng
> đúng vùng `StockBalanceRepository`.
> **Giới hạn:** JaCoCo report từ `mvn test` không bao gồm coverage của `*IT.java` (chạy qua Failsafe ở
> phase `verify` riêng biệt).

### 0.2 Module Đã Hoàn Thành

| Module | Trạng thái | Ghi chú |
|---|---|---|
| `auth` + `user` | ✅ Done | JWT HS256, refresh rotation, multi-device session, brute-force Lua. **[2026-08-01]** `GET /api/v1/auth/me` — profile + permissions + `scopes[]` theo company/plant + `defaultPlantId` (endpoint duy nhất của `AuthController` không permit-all). Không phải phase `F*`/`D*`/`P*`, không migration. Xem `module/auth/CLAUDE.md` + `module/organization/CLAUDE.md` (B80) |
| `organization` + dynamic RBAC | ✅ Done | `roles` / `permissions` / `access_scopes` / `user_role_assignments`, scope Company→Plant→Warehouse |
| `inventory` | ✅ Done | `stock_movements` append-only ledger, `stock_balances` projection, lot tracking, idempotency. **[D6]** `Idempotency-Key` scope theo `(key, movement_type)` (V37). **[P5]** Serial tracking (`SerialNumber`, mirror lot nhưng quantity luôn = 1, `Item.serialTracked` loại trừ `lotTracked`) — xem §0.33 + `module/inventory/CLAUDE.md` B96-B97. Xem §0.14 + B69-B71 |
| `bom` | ✅ Done | BOM đa cấp, circular reference check, activate/deactivate revision |
| `planning` + `mrp` | ✅ Done | MRP run, requirement explosion, supply suggestion, safety stock + lead time |
| `purchasing` | ✅ Done | Supplier, PR → PO → Goods Receipt (+ cancel/reversal) |
| `workorder` | ✅ Done | WO core, material reservation/issue, WIP, production receipt, variance |
| `workorder` – **P1 gates** | ✅ Done | 3 business gate (chi tiết §0.3) |
| `common/audit`, `common/security`, `common/response`, `common/exception` | ✅ Done | |
| `quality` (QC disposition) | ✅ Done (`F2`, `D5`) | **Không** là package riêng — `QualityDisposition` nằm trong `module/workorder` vì thuộc aggregate Production Receipt. **[D5]** QC chạy cho **cả** output không lot-tracked (phán quyết trên receipt); `quality_dispositions` vẫn chỉ ghi khi có lot. Xem `module/workorder/CLAUDE.md` B38-B41 |
| `sales` | ✅ Done (`F3`, `F6`) | Sales Order + line, confirm ⇒ independent demand cho MRP, endpoint `planning-demands`. **[F6]** fulfillment + roll-up status. Xem `module/sales/CLAUDE.md` B43-B47, B66 |
| `routing` | ✅ Done (`F4`, FK Work Center ở `C2-6`) | `RoutingHeader` + `RoutingOperation`, 1 routing `ACTIVE`/item, snapshot bất biến lên WO. **[C2-6]** `RoutingOperation.workCenterCode` → `workCenter` (FK), bất biến `B_wc2`; CRP/capacity vẫn chưa làm (`C2-8`). Xem `module/routing/CLAUDE.md` B48-B52 |
| `planning` – **F5-B** | ✅ Done | Endpoint `/planning-runs` + `/supply-suggestions`, `demandLineIds`, `supplyType` `MAKE`/`BUY`, `exceptionState` + `messages[]`, `settingSource` + `excludedLotCount` (V34). Xem §0.10 + `module/planning/CLAUDE.md` B58-B61 |
| `planning` – **D4** | ✅ Done | Open purchase order vào netting (`PurchaseOrderSupplyService`), `MrpRun.code` + 4 ô summary Run header (V36). Xem §0.12 + `module/planning/CLAUDE.md` B67-B68 |
| `workorder` – **F5-A** | ✅ Done | `ProductionExecution` + `WorkOrderOperation` (snapshot routing), status `PLANNED`, đảo ngược B16/B17, `PERM_PRODUCTION_EXECUTION_*` (V32/V33). Xem §0.9 |
| `workorder` – **F6** | ✅ Done | `WorkOrderDemandAllocation` (V35) nối WO ↔ Sales Order line; QC `AVAILABLE` ⇒ fulfillment. Xem §0.11 + `module/workorder/CLAUDE.md` B62-B65 |
| `uom` | ✅ Done (`C2-3`) | Unit of measure master data, **global** (không `company_id`). 7 endpoint, permission `PERM_UOM_READ`/`_MANAGE`. **Chưa nối** với `items.unit` (vẫn `String` tự do) — xem `module/uom/CLAUDE.md` |
| `workcenter` | ✅ Done (`C2-6`, calendar FK ở `C2-7`) | Work center master data, **per-plant**. 7 endpoint `/api/v1/plants/{plantId}/work-centers` + `/api/v1/work-centers/{id}`, permission `PERM_WORK_CENTER_READ`/`_MANAGE`. **[C2-7]** thêm FK tuỳ chọn `workCalendarId` (bất biến `B_wc4`). Xem `module/workcenter/CLAUDE.md` B_wc1-B_wc4 |
| `shift` | ✅ Done (`C2-7`) | Shift (1 interval + `breaks[]`) + Work Calendar (lịch tuần + exception `NON_WORKING`), **per-plant**. 14 endpoint `/api/v1/plants/{plantId}/shifts`+`/api/v1/shifts/{id}` và `/api/v1/plants/{plantId}/work-calendars`+`/api/v1/work-calendars/{id}`, permission `PERM_SHIFT_READ`/`_MANAGE`, `PERM_WORK_CALENDAR_READ`/`_MANAGE`. **[C2-8]** `WorkingWindowCalculator`/`WorkCalendarLookupService` nay có người gọi thật ngoài module (xem dưới). Xem `module/shift/CLAUDE.md` B_sh1-B_sh2, B_cal1-B_cal2 |
| `costing` | ✅ Done (`P3`) | `ItemStandardCost` (upsert, company-scoped, không có history/`effectiveDate`) + `CostingService` (BOM cost roll-up đệ quy) + `WorkOrderCostAccumulator` (`module/workorder`, tích luỹ material/labor/overhead thực tế qua 2 hook ở `MaterialIssueService`/`ProductionExecutionService`). 3 endpoint `/api/v1/companies/{companyId}/items/{itemId}/standard-cost` (+ list), permission `PERM_COSTING_READ`/`_MANAGE` (**ADMIN+MANAGER only**, không có OPERATOR — khác mọi permission `C2-*` gần đây). `GET /work-orders/{id}/variance` mở rộng `usageVarianceCost` + `costVariance`. **Không** làm Material Price Variance (quyết định có chủ đích — không có cột giá trên `stock_movements`). Xem `module/costing/CLAUDE.md` B91-B92, `module/workorder/CLAUDE.md` B93-B94 |
| Capacity Board (CRP tĩnh) | ✅ Done (`C2-8`) | `GET /plants/{plantId}/capacity-board` + `POST /work-orders/{id}/operations/{id}/schedule-adjustments`, permission `PERM_CAPACITY_READ`/`_MANAGE`. Lịch (`WorkOrderOperation.plannedStartAt`/`plannedEndAt`) sinh ở `WorkOrderService.release()`, "infinite capacity" (không biết WO khác đang chiếm cùng Work Center); Capacity Board là một read riêng tổng hợp load/capacity/utilization. Xem `module/workorder/CLAUDE.md` B89-B90 |

### 0.3 P1 – Approval Workflow & Business Gates (ĐÃ HOÀN THÀNH)

| Gate | Nội dung | Hệ quả cần nhớ khi code tiếp |
|---|---|---|
| **1a** | Release WO yêu cầu reservation phủ **100%**; thiếu → WO chuyển `BLOCKED` + trả **409** (`F5` đổi từ 422). `BLOCKED` **thật sự được persist** từ `D9` (trước đó bị rollback cùng exception — nợ #23) | `WorkOrderStatus` có thêm `BLOCKED`. Mọi `switch`/so sánh status phải xét nhánh này. `BLOCKED` **không** được issue / receipt / WIP, **nhưng ĐƯỢC reserve** (`D9`, nợ #22) — đó chính là trạng thái planner reserve để thoát ra |
| **1b** | Xuất vật tư vượt định mức BOM cần `PERM_MATERIAL_ISSUE_OVERRIDE` + `overrideReason` | Constraint `issued_quantity <= required_quantity` **đã bị gỡ** (giờ chỉ còn `>= 0`). Lưu vết ở cột `material_issue_lines.over_issue` + `override_reason` |
| **1c** | Production Receipt tách 2 bước: `post` → `PENDING_APPROVAL` (**không đụng tồn kho**) → `approve` → `POSTED` + movement + lot `HOLD`. ⚠️ **`F2` đã mở rộng thành 3 bước** (`DRAFT` → `PENDING_APPROVAL` → `APPROVED`) — xem §0.6 | `ProductionReceiptLine.stockMovement` giờ **nullable**. Mọi chỗ đọc `getStockMovement()` phải null-safe |

**2 giới hạn đã biết của P1 — đã được xử lý ở `F2`:**
1. Item **không** lot-tracked ⇒ không có chỗ mang trạng thái `HOLD` ⇒ output dùng được ngay.
   QC chỉ áp dụng cho output **lot-tracked**. ⇒ `F2` chặn `qc-disposition` trên receipt không
   lot-tracked bằng `STATE_CONFLICT`, và bắt buộc `lotCode` ngay từ bước `post` (`LOT_REQUIRED`).
2. Lot đã tồn tại (trùng `lotCode`) **giữ nguyên** status hiện tại — chỉ lot **mới tạo** mới nhận `HOLD`.
   Khuyến nghị nghiệp vụ: mỗi production receipt dùng một `lotCode` mới. ⇒ `F2` **chưa** ép buộc
   điều này; nếu lot tái sử dụng không ở `HOLD` thì `qc-disposition` trả `LOT_NOT_ELIGIBLE`.

### 0.6 F2 – Production Receipt Lifecycle & QC Disposition (ĐÃ HOÀN THÀNH 2026-07-26)

| Bước | Endpoint | Permission | Tác động |
|---|---|---|---|
| Create | `POST /work-orders/{id}/production-receipts` | `PERM_PRODUCTION_RECEIPT_MANAGE` | → `DRAFT`, **không** tồn kho |
| Submit | `POST .../production-receipts/{rid}/submit` | `PERM_PRODUCTION_RECEIPT_MANAGE` | → `PENDING_APPROVAL`, **không** tồn kho |
| Approve | `POST .../production-receipts/{rid}/approve` | `PERM_PRODUCTION_RECEIPT_APPROVE` | → `APPROVED` + RECEIVE movement + lot `HOLD` + cộng `completedQuantity` |
| Reject | `POST .../production-receipts/{rid}/reject` | `PERM_PRODUCTION_RECEIPT_APPROVE` | → `REJECTED`, reason bắt buộc (`APPROVAL_REASON_REQUIRED`) |
| QC | `POST .../production-receipts/{rid}/qc-disposition` | `PERM_QUALITY_DISPOSITION` | lot → `AVAILABLE`/`REJECTED` + `LOT_STATUS_CHANGE` movement. Receipt **vẫn** `APPROVED` |

**Hệ quả cần nhớ khi code tiếp:**
1. `ProductionReceiptStatus.POSTED` **không còn tồn tại** — đổi tên thành `APPROVED`, dữ liệu cũ
   được `UPDATE` trong `V26`. Mọi so sánh status phải xét cả `DRAFT`.
2. `MovementDirection` có giá trị thứ ba `NONE` (chỉ dùng cho `LOT_STATUS_CHANGE`).
3. Lỗi *sai trạng thái* của receipt nay trả `STATE_CONFLICT` (**409**), không còn
   `OPERATION_NOT_ALLOWED` (422) — trả một phần nợ #9 trong đúng phạm vi vòng đời receipt.
4. QC **không** đổi `stock_balances`; available tăng gián tiếp qua lot status.

### 0.4 Nợ Kỹ Thuật Đã Biết (không phải bug, đừng "tiện tay sửa")

| # | Nợ | Phase xử lý |
|---|---|---|
| 1 | ~~10 class `*MethodSecurityTest` chỉ test nhánh deny~~ ✅ **ĐÃ TRẢ (T1)** – mọi assertion deny có `verify(<guard>)` khoá `PERM_*`, mỗi class có nhánh allow, thêm `PermissionCatalogTest` + 6 class test cho 11 permission trước đó chưa phủ | ~~`T1`~~ |
| 2 | ~~`GlobalExceptionHandler` + controller không có test~~ ✅ **ĐÃ TRẢ HẾT (T2 → `D7` → `D7b`)** – `GlobalExceptionHandler` (cả 6 nhánh) + **20/20** controller nay khoá contract `{code,result,message}`: 3 từ `T2` (`Auth`, `WorkOrder`, `Inventory`) + 8 từ `D7` (nhóm A, có state machine) + **9 từ `D7b`** (nhóm B+C: `Item`, `ItemWarehouseSetting`, `Supplier`, `User`, `Organization`, `AccessControl`, `InventoryReport`, `Planning`, `PlanningDemand`). ⚠️ Tổng là **20**, không phải 18/19 như các bản trước ghi | ~~`D7b`~~ |
| 3 | ~~`AuthService.refresh` / `logout` / `logoutAll`, `TokenStoreService`, `JwtTokenProvider` không có test~~ ✅ **ĐÃ TRẢ (T3)** – 39 case mới. Còn lại: Redis là mock, chưa chạy thật (→ `T4`) | ~~`T3`~~ |
| 4 | ~~Logic phân quyền (hạn hiệu lực assignment, status role/org) nằm trong JPQL nhưng JPQL chưa bao giờ được chạy trong test~~ ✅ **ĐÃ TRẢ (T4)** – `UserRoleAssignmentRepositoryIT` (6 case) chạy JPQL thật qua Testcontainers, nghiệm thu mutation xác nhận | ~~`T4`~~ |
| 5 | ~~`Testcontainers` đã khai báo trong `pom.xml` nhưng chưa dùng lần nào~~ ✅ **ĐÃ TRẢ (T4)** – `AbstractPostgresIntegrationTest` + 3 `*IT.java`. `spring-security-test` ✅ đã dùng từ T2 | ~~`T4`~~ |
| 6 | 🟡 **Trả 2/3 — nợ VẪN MỞ.** RTR: ✅ **đã trả (`D8a`, 2026-08-03)**, §0.22 + `B80`. Absolute session timeout: ✅ **đã trả (`D8b`, 2026-08-03)**, §0.23 + `B81`. **Forgot-password (`D8c`): vẫn chưa có bất kỳ dòng code nào** — và đang **bị chặn** vì `pom.xml` không có `spring-boot-starter-mail` (phải chốt hạ tầng gửi email trước). 🔴 Đừng ghi "nợ #6 đã đóng" — đúng bài học §0.20 (repo đã ba lần tuyên bố đóng nợ rộng hơn phạm vi thật) | RTR: ~~`D8a`~~ · timeout: ~~`D8b`~~ · còn lại: `D8c` |
| 7 | ~~`LoginRequest` / `RefreshRequest` là **record thuần** ⇒ `toString()` tự sinh **lộ password / raw token**~~ ✅ **ĐÃ TRẢ (`D1`, 2026-07-28)** – rà ra **5** record dính (không phải 2): `LoginRequest`, `RefreshRequest`, `LogoutRequest`, `CreateUserRequest`, `UpdateUserRequest`. Cả 5 override `toString()` che secret, **giữ** `tokenId` hiện (là định danh Redis, không phải credential) và **giữ** phân biệt `null` vs `***` (thiếu password và ẩn password là hai bug khác nhau). `SensitiveRequestToStringTest` (5 case) là regression guard, đã nghiệm thu mutation | ~~Commit riêng~~ `D1` |
| 8 | ~~`StockBalanceRepository.aggregate*` dùng path expression `b.lot.status` ⇒ INNER JOIN loại bỏ mọi `StockBalance` không lot-tracked~~ ✅ **ĐÃ TRẢ (`F1.6`, 2026-07-26)** – cả 3 query dùng `left join b.lot l` tường minh; `StockBalanceRepositoryIT` nay assert hành vi đúng và là regression guard | ~~`F1`~~ |
| 9 | ~~Spec đặt `INSUFFICIENT_AVAILABLE_STOCK` / `STATE_CONFLICT` ở **409**, code trả **422**~~ ✅ **ĐÃ TRẢ HẾT (`F5-A` + `D7`)** – `F5-A`: `INSUFFICIENT_STOCK` → 409, state machine của work order / issue / reservation / WIP → `STATE_CONFLICT` (409), lot sai trạng thái → `LOT_NOT_ELIGIBLE` (409). **`D7`**: rà nốt 4 module còn lại theo đúng tiêu chí `.claude/rules/error-handling.md §5.3`, **44 throw site** phân loại từng chỗ ⇒ sửa **9** (`bom` 1, `purchasing` 8), `organization`/`sales` **0** (vốn đã đúng). `OPERATION_NOT_ALLOWED` (422) **cố ý giữ lại** cho validate master data **và** cho "chứng từ chưa có dòng nào" — bảng liệt kê từng chỗ ở `FRONTEND_ALIGNMENT_ROADMAP.md §6.1.6` | ~~`F5`~~ + ~~`D7`~~ |
| 10 | ~~`Idempotency-Key` của `stock_movements` là **UNIQUE toàn bảng**, không scope theo `(userId, operation)` như spec §10.2. Hệ quả: cùng một key gửi tới `/receive` rồi `/issue` thì lần 2 trả về movement của lần 1~~ ✅ **ĐÃ TRẢ (`D6`, 2026-07-30)** – `V37` đổi constraint sang `UNIQUE (idempotency_key, movement_type)` **và** `findByIdempotencyKey` → `findByIdempotencyKeyAndMovementType` trong **cùng commit** (đổi lệch nhau còn tệ hơn hiện trạng — xem `B69`). Scope theo `created_by` như spec §10.2 **cố ý không** làm: cột nullable + Postgres coi 2 `NULL` là khác nhau ⇒ mọi dòng lịch sử mất bảo vệ trùng lặp; cần backfill trước. `issue`/`issueReserved` cùng scope `ISSUE` có chủ đích (`B70`). Bất biến `B69`-`B71` | ~~`D6`~~ |
| 11 | ~~Spec §6.3 mô tả Production Receipt là **một dòng**, code dùng `lines[]`~~ ✅ **ĐÃ TRẢ (`F5-A`)** – request/response nay phẳng (`quantity` + `destinationWarehouseId` + `lotNumber`). Bảng `production_receipt_lines` **cố ý giữ nguyên** làm storage (mỗi receipt đúng 1 dòng) — collapse bảng là migration phá dữ liệu lịch sử mà không đổi hành vi | ~~`F5`~~ |
| 13 | ~~`POST /planning-runs` vẫn tự quét demand theo horizon, **chưa** nhận `demandLineIds` như spec §2.3~~ ✅ **ĐÃ TRẢ (`F5-B`)** – `MrpRunCreateRequest.demandLineIds` chọn đúng demand vào run; vắng mặt thì giữ hành vi quét horizon cũ (bất biến `B60`) | ~~`F5-B`~~ |
| 15 | ~~`MrpRun` chưa có `code` và 4 ô summary spec §2.4~~ ✅ **ĐÃ TRẢ (`D4`, 2026-07-30)** – `code` (`RUN-<8 hex>`, sinh ở `@PrePersist`, UNIQUE, backfill trong `V36`) + `shortageLines` / `plannedWorkOrders` / `plannedPurchaseRecommendations` / `blockedProposals`. Đếm **tại chỗ** từ `MrpCalculationResult`, không query lại; run cũ mang 0 ở 4 ô (số thật vẫn đọc được từ requirement/suggestion lines). Bất biến `B68` | ~~`D4`~~ |
| 16 | ~~`scheduledReceipts` thiếu purchase order đang mở ⇒ MRP đề xuất mua trùng thứ đã đặt hàng~~ ✅ **ĐÃ TRẢ (`D4`, 2026-07-30)** – `PurchaseOrderSupplyService` (`module/purchasing/service/query/`, entry point cross-module theo `C7`) cộng PO `SENT`+`PARTIALLY_RECEIVED` vào `openSupplyQuantity`. `DRAFT` **không** tính; dòng đã nhận đủ **không** tính (đã nằm trong on-hand). 1 query/cấp BOM (`C14`), JPQL nghiệm thu thật bằng `PurchaseOrderRepositoryIT`. Bất biến `B67` | ~~`D4`~~ |
| 17 | ~~**Thành phẩm không lot-tracked không bao giờ fulfill được Sales Order.**~~ ✅ **ĐÃ TRẢ (`D5`, 2026-07-30)** — QC disposition nay chạy cho **cả** output không lot: điều kiện rẽ nhánh là **cấp dòng** (`line.lot != null`), phán quyết ghi trên `production_receipts` (`qc_result`/`qc_reason`/`qc_at`/`qc_by`). Giữ đúng 2 điều user đã bác: **không** đường fulfill thứ hai ở `approve`, **không** ép item phải lot-tracked. Số đưa sang fulfillment là **tổng quantity dòng receipt** — nguồn cũ cộng dồn từ lot line nên ra 0 và `fulfill` no-op **im lặng** (bẫy chính của phase, đã khoá bằng nghiệm thu mutation). Không migration. Bất biến `B39` viết lại + `B40` tách đôi | ~~`D5`~~ |
| 26 | ~~**`planning` còn 3 chỗ trả 422 mà 3 tiêu chí §5.3 xếp vào 409.**~~ ✅ **ĐÃ TRẢ (`D11`, 2026-07-31)** — `D7` rà `bom`/`purchasing`/`organization`/`sales` và tuyên bố nợ #9 "đã đóng hết", nhưng **`planning` chưa bao giờ nằm trong phạm vi rà đó**. Ba chỗ đọc **status chứng từ** nay trả `STATE_CONFLICT` (409) qua `ExceptionFactory.custom`: `PlanningDemandService.cancel` (`!demand.isOpen()`), `SupplySuggestionService.convertToWorkOrder` (`!suggestion.isApproved()`), `SupplySuggestionService.ensureDraft` (`approve`/`reject`). Lập luận quyết định **không** phải tài liệu mà là mâu thuẫn nội tại: nhánh "suggestion không `APPROVED`" của `PurchaseRequisitionService` đã sang 409 từ `D7` ⇒ cùng một điều kiện nghiệp vụ trả 2 status tuỳ đường convert. 🔴 Các chỗ 422 còn lại của `planning` (`ensurePlantBelongsToCompany`, `ensureItemBelongsToCompany`, `ensureWarehouseBelongsToPlant`, `PlanningService` item khác company, **"sai `SupplySuggestionType`" ngay dưới chỗ vừa đổi**) là master data / input sai ⇒ **cố ý giữ 422, đừng đụng**. Bất biến `B74` | ~~`D11`~~ |
| 25 | ~~**Dòng receipt cuối của work order không bao giờ nhập kho được.**~~ ✅ **ĐÃ TRẢ (`D11`, 2026-07-31)** — `WorkOrder.report` gọi `complete()` ngay khi `actualGoodQuantity >= plannedQuantity` (`B53`) trong khi `canExecute()` loại `COMPLETED` (`B13`) ⇒ report đủ số kế hoạch xong thì không receipt được nữa. Sửa bằng **phương án A (nới `B13`)** theo đúng tiền lệ `D9`: thêm `WorkOrder.canReceipt()` (= `canExecute()` **+ `COMPLETED`**, vẫn loại `CANCELLED`/`BLOCKED`) + `WorkOrderExecutionSupport.ensureReceiptable`. ⚠️ **Đổi cả 3 call site của vòng đời receipt** (`postNew`, `submit`, `approve`) — kế hoạch ban đầu chỉ ghi `postNew`, nhưng thế thì draft tạo được rồi kẹt không submit/approve được, tồn kho vẫn không đổi. `issue`/`report`/WIP **giữ** `ensureExecutable`. Trần `B16` không nới. **Phương án B (hoãn `complete()`) bị loại**: WO làm xong mà chưa ai nhập kho sẽ kẹt `IN_PROGRESS` vô thời hạn + đổi nghĩa cột có dữ liệu lịch sử. Thước đo: `ProductionFlowE2EIT` đã **bỏ** seed safety stock = 2 của `D5` | ~~`D11`~~ |
| 14 | ~~`MISSING_ROUTING` trả 409 nhưng `MISSING_BOM` trả 404~~ ✅ **ĐÃ TRẢ (`F5-A`)** – thêm `BusinessErrorCode.MISSING_BOM` (409), `BomLookupService.getActiveBom` dùng nó thay `RESOURCE_NOT_FOUND` | ~~`F5`~~ |
| 12 | ~~Spec §6.4 đòi `code`, `sourceWipTraceIds`, `*Username`, rename `itemCode`→`itemSku` / `lotCode`→`lotNumber`~~ ✅ **ĐÃ TRẢ (`F5-A`)** – đủ cả; username resolve qua `UserLookupService` bằng **1 query batch** cho cả trang (rule C14) | ~~`F5`~~ |
| 18 | ~~Ranh giới áp dụng `X-Plant-Id` chưa được ghi ⇒ quyết định `F1-D6` ("các controller còn lại sẽ wire ở `F5`") gây hiểu nhầm là có code bị bỏ sót~~ ✅ **ĐÃ TRẢ (`D1`, 2026-07-28)** – **không** phải bỏ sót: header chỉ có nghĩa với endpoint nêu `plantId` tường minh; endpoint định danh bằng aggregate (`/work-orders/{id}/…`) lấy plant từ chính aggregate nên không có giá trị thứ hai để đối chiếu, và `@PreAuthorize` đã chặn. Ranh giới nay ghi ở `.claude/rules/error-handling.md §5.6.1` + javadoc `PlantContextResolver` | ~~`D1`~~ |
| 19 | ~~Kịch bản nghiệm thu end-to-end 11 bước (spec §11.1) chưa được viết thành `*IT.java`~~ ✅ **ĐÃ TRẢ (`D1`)** – `ProductionFlowE2EIT` (2 case, `@SpringBootTest` **duy nhất** được phép, chạy qua HTTP + Testcontainers). Nó **lập tức tìm ra 4 defect** mà 391 unit test không thấy: nợ #21, #22, #23, #24 | ~~`D1`~~ |
| 24 | ~~`POST /material-issues` (endpoint phẳng spec §4.1) không replay-safe~~ ✅ **ĐÃ TRẢ (`D10`, 2026-07-30)** – `postFlat` nay resolve reservation **không** kiểm `ACTIVE` (chỉ để dịch request); `postNew` vẫn kiểm nên đường tạo chứng từ không bị nới. Retry cùng `Idempotency-Key` trả lại đúng chứng từ cũ (`R9`), đã assert bằng `issueId` ở bước 11 của `ProductionFlowE2EIT` | ~~`D10`~~ |
| 20 | ~~Số liệu trong `§0.1`/`§0.4` lệch thực tế (coverage còn là số của `F4`, "18 controller")~~ ✅ **ĐÃ TRẢ (`D1`)** – đo lại và sửa. **Bài học:** coverage phải đo lại mỗi phase, không chép số phase trước | ~~`D1`~~ |
| 21 | ~~`GET /sales-orders/planning-demands` không trả `planningDemandId` mà `POST /planning-runs` cần~~ ✅ **ĐÃ TRẢ (`D10`, 2026-07-30)** – `PlanningDemandLineResponse` thêm `planningDemandId` (additive). Lấy qua **1 query batch** `PlanningDemandService.findOpenDemandIdsBySalesOrderLineIds` (rule `C7`+`C14`), **không** join JPQL xuyên module vì `referenceId` là `String`. Đóng nốt nửa còn lại của nợ #13 | ~~`D10`~~ |
| 22 | ~~Work order tạo từ MRP không bao giờ release được qua API (reserve đòi `RELEASED`, release đòi reservation 100%)~~ ✅ **ĐÃ TRẢ (`D9`, 2026-07-30)** – thêm `WorkOrder.canReserve()` (mọi status trừ `COMPLETED`/`CANCELLED`) + `WorkOrderExecutionSupport.ensureReservable`; **chỉ** 2 call site reserve đổi sang gate mới, issue/execution/receipt/WIP vẫn `canExecute()`. B13 tách đôi. Thước đo: seam `forceStatus` trong `ProductionFlowE2EIT` đã **xoá**, bước 4-5 chạy reserve → release đúng thứ tự spec §11.1 | ~~`D9`~~ |
| 23 | ~~`BLOCKED` của gate 1a không bao giờ được persist (throw trong chính transaction `REQUIRES_NEW` của nó ⇒ rollback-only)~~ ✅ **ĐÃ TRẢ (`D9`, 2026-07-30)** – tách `WorkOrderBlockRecorder` (bean riêng, `REQUIRES_NEW`, **return bình thường** nên commit được), gate gọi nó xong mới throw. Javadoc sai đã sửa. `ProductionFlowE2EIT` đọc lại từ DB để chứng minh row sống sót rollback — mock không kiểm được điều này | ~~`D9`~~ |

### 0.7 F3 – Sales Order + Planning Demand (ĐÃ HOÀN THÀNH 2026-07-26)

| Bước | Endpoint | Permission | Tác động |
|---|---|---|---|
| Create | `POST /sales-orders` | `PERM_SALES_ORDER_MANAGE` | → `DRAFT`, **chưa** sinh demand |
| Confirm | `POST /sales-orders/{id}/confirm` | `PERM_SALES_ORDER_MANAGE` | → `CONFIRMED` + **1 `PlanningDemand` / line** (`demandType = SALES_ORDER`) |
| Cancel | `POST /sales-orders/{id}/cancel` | `PERM_SALES_ORDER_MANAGE` | → `CANCELLED` + huỷ demand còn `OPEN`. Chỉ từ `DRAFT`/`CONFIRMED` |
| List / Get | `GET /sales-orders`, `GET /sales-orders/{id}` | `PERM_SALES_ORDER_READ` | — |
| Planning demand | `GET /sales-orders/planning-demands?plantId=…&horizonEnd=…` | **`PERM_MRP_RUN`** (spec §2.2 gán `PLANNING_RUN`, không phải quyền sales) | 1 aggregate query, trả open quantity |

**Hệ quả cần nhớ khi code tiếp:**
1. `PlanningDemandService` có thêm **2 entry point không `@PreAuthorize`** dành riêng cho sales
   (`createFromSalesOrderLine`, `cancelOpenDemandsForSalesOrderLines`) — caller đã được authorize
   trên cùng plant. Lý do đầy đủ ở `module/sales/CLAUDE.md`. **Không** gọi chúng từ controller.
2. Hướng phụ thuộc là `sales → planning`. **Không có** `SalesOrderLookupService` (không ai gọi vào
   `sales`) — khác với dự kiến ban đầu của kế hoạch `F3`, xem `module/sales/CLAUDE.md` mục 1.
3. `SalesOrderStatus` khai báo đủ 6 giá trị nhưng `IN_PRODUCTION`/`PARTIALLY_FULFILLED`/`FULFILLED`
   **chưa có đường vào** — `F6` mới set. `fulfilledQuantity` luôn = 0 trong `F3`.
4. Lệch tên có chủ đích: `orderNo` (CRUD) vs `salesOrderCode` (màn hình Planning) — cùng một cột.
5. Chưa nối vào `MrpRunService`: MRP vẫn chọn demand qua `PlanningDemandRepository.findOpenDemandsForRun`
   theo horizon, nên demand từ sales tự động được nhặt. Endpoint `POST /planning-runs` nhận
   `demandLineIds` như spec §2.3 mô tả thì thuộc `F5` (reshape Planning).

### 0.8 F4 – Routing Tối Thiểu + Snapshot Lên Work Order (ĐÃ HOÀN THÀNH 2026-07-27)

| Bước | Endpoint | Permission | Tác động |
|---|---|---|---|
| Create | `POST /companies/{companyId}/routings` | `PERM_ROUTING_MANAGE` | → `DRAFT` + operations inline |
| Activate | `POST /routings/{routingId}/activate` | `PERM_ROUTING_MANAGE` | → `ACTIVE` + routing `ACTIVE` cũ **của cùng item** → `INACTIVE` |
| Deactivate | `DELETE /routings/{routingId}` | `PERM_ROUTING_MANAGE` | → `INACTIVE` |
| List / Get | `GET /companies/{companyId}/routings`, `GET /routings/{routingId}` | `PERM_ROUTING_READ` | — |

**Hệ quả cần nhớ khi code tiếp:**
1. `work_orders` có thêm 4 cột **nullable**: `source_routing_id` / `source_routing_code` /
   `source_routing_version` / `routing_captured_at`. Mọi chỗ đọc phải null-safe.
2. Snapshot là **cột phẳng, không phải `@ManyToOne`** — đọc qua association sẽ trả master data
   sống và phá bất biến B49. Đừng "tiện tay" đổi thành quan hệ.
3. `WorkOrderService.createFromMrp` **không còn** gọi lồng vào `create`; cả hai gọi
   `createInternal(plantId, request, routingRequired)`. Chỉ đường MRP đòi routing `ACTIVE`
   (`MISSING_ROUTING`, 409); tạo WO thủ công vẫn chạy với snapshot rỗng.
4. `RoutingOperation.workCenterCode` là **string**, không có entity Work Center — capacity/CRP
   ngoài MVP (spec §11). `predecessorOperationIds` của spec §3.3 chưa làm.
5. **Chưa có** bảng `work_order_operations` — thuộc `F5`, thiết kế cùng Production Execution.
6. `RoutingHeader.routingVersion` (cột `routing_version`) vì `BaseEntity` đã chiếm `version` cho
   optimistic locking; DTO vẫn expose tên `version` ra wire.

### 0.9 F5-A – Production Execution + Work Order Reshape (ĐÃ HOÀN THÀNH 2026-07-27)

| Bước | Endpoint | Permission | Tác động |
|---|---|---|---|
| Schedule | `POST /work-orders/{id}/plan` | `PERM_WORK_ORDER_MANAGE` | `DRAFT` → `PLANNED`, không đụng vật tư |
| Auto reserve | `POST /work-orders/{id}/reserve` | `PERM_MATERIAL_RESERVATION_MANAGE` | Reserve FEFO mọi component thiếu; **thành công một phần là có chủ đích** |
| Issue phẳng | `POST /material-issues` | `PERM_MATERIAL_ISSUE_MANAGE` | 1 dòng theo reservation (spec §4.1); không hỗ trợ over-issue |
| **Report sản lượng** | `POST /work-orders/{id}/production-executions` | `PERM_PRODUCTION_EXECUTION_MANAGE` | **+`actualGood/Scrap/Rework`, `IN_PROGRESS`, `COMPLETED`** + WIP ledger |
| Receipt create | `POST /work-orders/{id}/production-receipts` | `PERM_PRODUCTION_RECEIPT_MANAGE` | → `DRAFT`, body **phẳng**, trần = `availableToReceipt` |
| Receipt approve | `.../{rid}/approve` | `PERM_PRODUCTION_RECEIPT_APPROVE` | movement + lot `HOLD` + `completedQuantity` + WIP `OUTPUT_RECEIPTED`. **Không** đổi status WO |

**Hệ quả cần nhớ khi code tiếp:**
1. `WorkOrderStatus` có thêm `PLANNED`. Mọi `switch`/so sánh status phải xét nhánh này — nó hành xử
   như `DRAFT` với execution nhưng **được** release và cancel.
2. `WipTransactionType` có thêm `OUTPUT_RECEIPTED`. `OUTPUT_COMPLETED` nay nghĩa là "xưởng làm ra",
   không còn là "đã nhập kho".
3. `ProductionReceiptPostRequest`/`Response` **phẳng** (`quantity`, `destinationWarehouseId`,
   `lotNumber`) — `lines[]` và `ProductionReceiptLineRequest/Response` đã xoá. Bảng
   `production_receipt_lines` vẫn còn làm storage, đúng 1 dòng/receipt.
4. `WipTransactionRequest` thêm `workOrderOperationId` ở **vị trí thứ 2** — mọi call site positional
   phải sửa.
5. `TraceIdProvider` là bean mới; `InventoryMovementService`, `MaterialIssueService`,
   `ProductionReceiptService`, `ProductionExecutionService` đều nhận nó qua constructor.
6. `UserLookupService` (module `user`) là entry point mới cho cross-module resolve username —
   **batch**, không gọi từng dòng (C14).

**Chưa làm trong `F5-A`** (đã làm ở `F5-B`, xem §0.10): `/mrp/runs` → `/planning-runs`, `demandLineIds`,
`supplyType`/`exceptionState`/`messages[]`/`convertedWorkOrderId`/`settingSource`/`excludedLotCount`.

### 0.10 F5-B – Reshape Planning (ĐÃ HOÀN THÀNH 2026-07-28)

| Endpoint cũ | Endpoint mới | Ghi chú |
|---|---|---|
| `POST /mrp/runs` | `POST /planning-runs` | body thêm `demandLineIds` (tuỳ chọn) |
| `GET /mrp/runs`, `/mrp/runs/{id}`, `/{id}/requirements`, `/{id}/suggestions` | `GET /planning-runs…` | không đổi tham số |
| `POST /mrp/suggestions/{id}/approve\|reject\|convert-to-work-order` | `POST /supply-suggestions/{id}/…` | |
| `POST /mrp/suggestions/{id}/convert-to-purchase-requisition` | `POST /supply-suggestions/{id}/…` | thuộc `PurchaseRequisitionController` |

**Hệ quả cần nhớ khi code tiếp:**
1. `SupplySuggestionResponse.suggestionType` **đã đổi tên** thành `supplyType`, giá trị wire là
   `MAKE`/`BUY`. Enum lưu DB vẫn là `WORK_ORDER`/`PURCHASE_REQUISITION` — đọc qua
   `SupplySuggestionType.supplyType()`, **đừng** đổi tên constant.
2. Suggestion nay có `exceptionState` (`READY`/`WARNING`/`BLOCKED`) + `messages[]`. Item
   manufacturable thiếu BOM/routing `ACTIVE` **vẫn sinh** proposal MAKE ở trạng thái `BLOCKED` —
   trước `F5-B` những dòng đó biến mất khỏi kết quả. Convert một suggestion `BLOCKED` trả
   `MISSING_BOM`/`MISSING_ROUTING` (409). Bất biến `B58`-`B59`.
3. `MrpRequirementLine` thêm `settingSource` + `excludedLotCount` (V34) — dữ liệu **thật**, không
   phải placeholder: nguồn là `InventoryAvailabilityService.PlanningInventoryQuantity`
   (`hasItemWarehouseSetting`, `excludedLotCount`), đo bằng aggregate query mới
   `StockBalanceRepository.aggregateExcludedLotCounts`.
4. `PlanningInventoryQuantity` là **record 8 field** (thêm 2) — mọi call site positional phải sửa.
5. Hướng phụ thuộc mới: `planning → routing` (`RoutingLookupService.findItemIdsWithActiveRouting`,
   batch theo từng cấp BOM). Rule `C7`/`C14`.
6. Class `MrpController` **đã xoá**, thay bằng `PlanningRunController`.

### 0.11 F6 – Fulfillment Allocation (ĐÃ HOÀN THÀNH 2026-07-28)

Phase **cuối** của track `F*`. Đóng mắt xích cuối: work order giờ biết nó được tạo cho đơn hàng nào,
và QC giải phóng lot là thứ biến sản lượng thành số lượng đã giao của Sales Order.

| Bước | Ở đâu | Tác động |
|---|---|---|
| Tạo allocation | `POST /supply-suggestions/{id}/convert-to-work-order` | 1 `WorkOrderDemandAllocation` khi lineage dẫn về SO line; `allocatedQuantity = min(plannedQuantity, openQuantity)`; đơn hàng → `IN_PRODUCTION` |
| Fulfill | `POST /work-orders/{id}/production-receipts/{rid}/qc-disposition` (result = `AVAILABLE`) | Phân bổ theo `dueDate` → `lineNo`; tăng `fulfilledQuantity` của allocation **và** SO line; roll-up → `PARTIALLY_FULFILLED`/`FULFILLED` |
| Đọc | `GET /work-orders`, `GET /work-orders/{id}` | `allocations[]` (`salesOrderCode`, `salesOrderLineId`, `allocatedQuantity`, `fulfilledQuantity`, `uom`, `dueDate`) |

**Hệ quả cần nhớ khi code tiếp:**
1. `WorkOrderService.createFromMrp` nay có **tham số thứ 3** `salesOrderLineId` (nullable). Chỉ
   `SupplySuggestionService` gọi nó. `WorkOrderCreateRequest` **không** đổi — allocation không phải
   thứ client tự khai.
2. `WorkOrderMapper.toResponse` nay nhận **2 tham số** (`WorkOrder`, `List<…AllocationResponse>`);
   `WorkOrderResponse` có field thứ 36 `allocations`. Mọi call site positional phải sửa.
3. `ProductionReceiptService` nhận thêm `WorkOrderDemandAllocationService` qua constructor
   (**vị trí thứ 10**, ngay sau `auditorAware`).
4. `WorkOrderDemandAllocation.salesOrderLineId` là **`UUID` phẳng**, không `@ManyToOne` — giữ
   `sales → planning → workorder → sales` không thành vòng phụ thuộc compile-time. Đọc dữ liệu SO
   line qua `SalesOrderFulfillmentService` (rule `C7`).
5. **Không** thêm permission mới: fulfillment là hệ quả của `PERM_QUALITY_DISPOSITION` (spec §7.1).
6. Chỉ suggestion **level 0** có demand `SALES_ORDER_LINE` mới sinh allocation — component thừa
   hưởng `sourceDemand` của cha nên phải bị loại (bất biến `B64`).
7. Giới hạn mới: item **không** lot-tracked không fulfill được — nợ **#17** ở §0.4.

**Breaking changes:** mục 1, 2, 3 ở trên. Test cũ đã **sửa** theo `R10`, không xoá: 365 → **391**
unit, 16 → **18** IT.

### 0.12 D4 – MRP Đúng Số: Open PO Vào Netting + Run Header (ĐÃ HOÀN THÀNH 2026-07-30)

Trả nợ **#16** (defect nghiệp vụ nặng nhất còn lại) và **#15**. Không permission mới, không endpoint mới.

| Thay đổi | Ở đâu | Tác động |
|---|---|---|
| Open PO vào `scheduledReceipts` | `PurchaseOrderSupplyService` → `MrpCalculationService.loadLevelSupplySnapshot` | `net = gross + max(safetyStock, reorderPoint) − (available + openWO + **openPO**)`. Item đã có PO mở ra số nhỏ hơn hoặc **biến mất** khỏi danh sách suggestion |
| Run header spec §2.4 | `MrpRun` + `MrpRunService` + `V36` | `code` + `shortageLines` / `plannedWorkOrders` / `plannedPurchaseRecommendations` / `blockedProposals` |

**Hệ quả cần nhớ khi code tiếp:**
1. **Kết quả MRP đã đổi** — đây là sửa sai, không phải regression. Fixture MRP nào assert số cũ phải
   tính lại theo `R10`, **không** nới assertion.
2. `MrpCalculationService` nhận `PurchaseOrderSupplyService` qua constructor ở **vị trí thứ 4**
   (trước `RoutingLookupService`) — mọi test dựng service này phải sửa.
3. `MrpRun.complete(...)` nay nhận **8 tham số** (thêm 4 số summary). Call site duy nhất: `MrpRunService`.
4. `MrpRunResponse` thêm `code` (vị trí **2**) + 4 số (sau `totalSuggestionLines`) — additive trên
   wire, **positional** trong Java.
5. `MrpRun.code` sinh ở **`@PrePersist`**, không sau `save()`: Hibernate chụp snapshot entity lúc queue
   insert nên field gán sau đó **không** vào INSERT và cột NOT NULL nổ. Đây là defect thật, do
   `ProductionFlowE2EIT` bắt được — mock repository không thấy được (bất biến `B68`).
6. Hướng phụ thuộc mới `planning → purchasing`, hợp lệ theo `C7` (query service, không chạm repository).
7. **Không** tách `openWorkOrderQuantity` / `openPurchaseQuantity` thành 2 cột — spec chỉ có một số
   hạng `scheduledReceipts`. Lý do đầy đủ: `module/planning/CLAUDE.md` mục 6.

### 0.13 D5 – QC Disposition Cho Output Không Lot-Tracked (ĐÃ HOÀN THÀNH 2026-07-30)

Trả nợ **#17** — mắt xích cuối của track `F*` bị hở. Không permission mới, không endpoint mới,
**không migration**. Toàn bộ thay đổi nằm trong `ProductionReceiptService.qcDisposition`.

| Điều kiện | Trước `D5` | Sau `D5` |
|---|---|---|
| Receipt **có** lot (`line.lot != null`) | validate mọi lot ở `HOLD` → `changeLotStatus` → ghi `quality_dispositions`/lot → fulfill nếu `AVAILABLE` | **không đổi một dòng nào** |
| Receipt **không** lot | `STATE_CONFLICT` (409) | `AVAILABLE`: chỉ ghi verdict trên receipt + fulfill · `REJECTED`: verdict + **`ADJUST_OUT`** rút hàng |

**Hệ quả cần nhớ khi code tiếp:**
1. Điều kiện rẽ nhánh là **cấp dòng** (`line.getLot() != null`), **không** phải `item.isLotTracked()`.
   Đừng đi tìm cờ `lotTracked` trong đường QC — nó không nằm ở đó.
2. `dispositionedQuantity` của đường không-lot là **tổng `quantity` mọi dòng receipt**. Nguồn cũ cộng
   dồn từ lot line ⇒ ra 0 ⇒ `fulfill(...)` **no-op im lặng, không lỗi**. Đây là bẫy chính của phase;
   đã khoá bằng nghiệm thu mutation (đổi hàm trả `ZERO` ⇒ đúng 1 case đỏ).
3. `qcDisposition` tách thành 2 private method `dispositionLots` / `dispositionWithoutLots`, cả hai
   trả về "số QC vừa phán quyết". `auditorAware.getCurrentAuditor()` nay được gọi **trước** khi
   validate lot (trước đây sau) — không đổi hành vi, chỉ đổi thứ tự.
4. **Quyết định `A2`:** receipt không lot **không** ghi dòng `quality_dispositions` (bảng đó định
   nghĩa là "một dòng cho mỗi lot"; `lot_id` vẫn `NOT NULL`, không migration). Báo cáo QC query từ
   bảng đó sẽ **thiếu** những receipt này — cảnh báo đã ghi ở `module/workorder/CLAUDE.md` + javadoc
   entity. Bảng vẫn chưa có call site đọc nào trong `src/main`.
5. **Quyết định `A`** cho `REJECTED`: rút hàng bằng `movementService.adjust(...)` delta âm
   (⇒ `ADJUST_OUT` + `direction = OUT`). `ADJUST_OUT` đã có sẵn trong enum **và** trong
   `chk_stock_movements_type` ⇒ không migration. Child idempotency key: `<parent>:qc-reject:L<n>`.
6. **Edge case đã chốt:** nếu hàng đã bị issue/bán trước khi QC kịp phán quyết, `adjust` nổ
   `INSUFFICIENT_AVAILABLE_STOCK` (409) và **cả** QC disposition rollback ⇒ receipt kẹt chờ người xử
   lý. Có chủ đích: hàng lỗi đã ra khỏi kho là sự cố nghiệp vụ thật, không che bằng cách rút thiếu.
7. `AVAILABLE` trên output không lot **không** sinh movement nào — hàng đã ở tồn tự do từ lúc
   `approve`. Đừng "sửa cho giống" nhánh lot-tracked bằng cách thêm movement.

**Breaking change:** `qc-disposition` trên receipt không lot **không còn** trả `STATE_CONFLICT` (409),
nay trả 200. Đây là sửa sai. Test cũ `qcDisposition_nonLotTrackedOutput_shouldThrowStateConflict` đã
**sửa** theo `R10` (thay bằng case chứng minh hành vi mới), không xoá.

---

### 0.14 D6 – Scope Lại `Idempotency-Key` Của `stock_movements` (ĐÃ HOÀN THÀNH 2026-07-30)

Trả nợ **#10**. Không permission mới, không endpoint mới, không đổi contract wire. Migration `V37`.

| Thay đổi | Ở đâu | Tác động |
|---|---|---|
| Constraint | `V37` | `UNIQUE (idempotency_key)` → **`UNIQUE (idempotency_key, movement_type)`** |
| Query replay | `StockMovementRepository` | `findByIdempotencyKey` → **`findByIdempotencyKeyAndMovementType`** |
| 5 call site | `InventoryMovementService` | mỗi đường truyền type tường minh qua private `findReplay(key, type)` |
| Dead code | `InventoryMovementService` | **xoá** `hasMovementForIdempotencyKey` (0 call site, quyết định của user) |

**Hệ quả cần nhớ khi code tiếp:**
1. **Constraint và query là một cặp, đổi phải cùng commit** (bất biến `B69`). Đổi constraint mà không
   đổi query thì DB cho 2 dòng cùng key khác type nhưng query trả dòng **đầu tiên nó gặp** — replay
   trả nhầm chứng từ **không xác định trước**, tệ hơn hiện trạng cũ (cũ ít nhất còn deterministic).
2. **Không** scope theo `created_by` như spec §10.2: cột nullable (`V8`) và Postgres coi 2 `NULL` là
   khác nhau ⇒ mọi dòng lịch sử `created_by IS NULL` **mất hoàn toàn** bảo vệ trùng lặp. Muốn sát
   spec phải backfill trước — việc lớn hơn nợ đang trả. Quyết định của user.
3. `adjust` nay đọc `quantityDelta` **trước** khi tra replay (phải biết `ADJUST_IN`/`ADJUST_OUT` mới
   scope được). ⇒ delta = 0/null nổ `NEGATIVE_QUANTITY` **trước** `RESOURCE_NOT_FOUND` của
   item/warehouse. Thứ tự này có chủ đích, bất biến `B71`.
4. `issue` và `issueReserved` **cùng** scope `ISSUE` — có chủ đích, đừng bịa movement type để tách
   (bất biến `B70`).
5. `material_issues` / `production_receipts` / `production_executions` **vẫn** `UNIQUE` toàn bảng và
   vẫn dùng `findByIdempotencyKey` không scope. **Cố ý ngoài phạm vi**: mỗi bảng chỉ có một loại
   nghiệp vụ ghi vào nên không dùng chung key space như ledger.
6. Mock repository **không** kiểm được constraint. Bằng chứng cần **cả hai** tầng: unit test chứng
   minh *query* đã scope, `FlywayMigrationIT` chứng minh *DB* đã cho phép cặp đó. Thiếu một nửa là
   đúng kiểu lỗ hổng của nợ #8/#23 từng lọt qua.

**Breaking change:** cùng `Idempotency-Key` + **khác** `movement_type` nay **tạo chứng từ mới** thay vì
trả chứng từ cũ. Client nào (vô tình) dựa vào hành vi cũ sẽ thấy tồn kho đổi ở lần 2 — đây là **sửa
sai**, hành vi cũ là bug. `ProductionFlowE2EIT` không đổi assertion nào (child key `:L<n>`, `:approve`,
`:qc`, `:qc-reject` không bị ảnh hưởng).

---

### 0.15 D7 – Test Tầng HTTP Nhóm A + Rà 409/422 (ĐÃ HOÀN THÀNH 2026-07-30)

Trả nợ **#9 hết** và **#2 một phần**. Không permission mới, không endpoint mới, **không migration**.

| Phần | Kết quả |
|---|---|
| `D7.2` | **8 `*ControllerTest` mới** (nhóm A) — 34 case. Nhóm B+C (9 controller) hoãn sang `D7b` theo phương án `A` §6.1 của kế hoạch |
| `D7.3` | Rà **44 throw site** `OPERATION_NOT_ALLOWED` ở 4 module, phân loại **từng chỗ**. Bảng đầy đủ: `FRONTEND_ALIGNMENT_ROADMAP.md §6.1.6` |
| `D7.4` | Sửa **9** chỗ: `bom` 1, `purchasing` 8. `organization` **0**, `sales` **0** (vốn đã đúng tiêu chí) |
| `D7.5` | Sửa số controller **19 → 20** / **16 → 17** ở 4 chỗ tài liệu |

**Ba tiêu chí phân loại rút ra (dùng cho mọi module về sau, đã ghi vào `.claude/rules/error-handling.md §5.3`):**

| Kiểm tra gì | Code | HTTP |
|---|---|---|
| Đọc **status** chứng từ (`isDraft()`, `isApproved()`, `canReceive()`, `status != X`) | `STATE_CONFLICT` | 409 |
| Số lượng vượt **trần của chứng từ** (nhận vượt số đặt, duyệt vượt số xin) | `PLANNED_QUANTITY_EXCEEDED` | 409 |
| Master data (`INACTIVE`, sai `ItemType`, khác company/plant) · **chứng từ chưa có dòng nào** · dữ liệu đầu vào sai | `OPERATION_NOT_ALLOWED` | **422** |

**Hệ quả cần nhớ khi code tiếp:**
1. 🔴 **"Chứng từ chưa có dòng nào" là 422, KHÔNG phải 409.** `RoutingService.activate` (`F4`) là tiền lệ
   gốc và đã tách đúng hai nhánh này từ trước `D7`; `BomService.activateBom` được giữ 422 theo đúng nó.
   Đây là ranh giới dễ nhầm nhất — đừng "đồng bộ" nốt.
2. `ExceptionFactory.businessRule(...)` đọc như "422" nhưng thực ra chỉ lấy status từ `ErrorCode`. Chỗ
   nào ném `STATE_CONFLICT`/`PLANNED_QUANTITY_EXCEEDED` thì dùng `custom(...)` cho khớp tiền lệ `F5`
   trong `workorder` — **không** phải vì `businessRule` sai chức năng.
3. `PLANNED_QUANTITY_EXCEEDED` nay dùng cả ở **purchasing** (nhận vượt số đặt — `B27`; duyệt vượt số
   xin), không chỉ ở `workorder`. Tên nó chung ("vượt trần kế hoạch"), đừng bịa mã mới cho từng module.
4. Bất biến mới: **`B72`** (`module/purchasing/CLAUDE.md`), **`B73`** (`module/bom/CLAUDE.md`) ghi rõ
   chỗ nào 409 và chỗ nào **cố ý giữ** 422. `B11`/`B27` được viết lại kèm mã lỗi.
5. `@JsonInclude(NON_NULL)` trên **cả record** `ApiResponse` ⇒ `result` **biến mất khỏi JSON** khi null,
   không phải `"result": null` như ví dụ ở `error-handling.md §5.1` mô tả. Test `D7` assert
   `$.result` `doesNotExist()` cho DELETE/lỗi. **Chưa sửa** — đổi hành vi này là breaking change với
   FE, ngoài phạm vi `D7`; ví dụ trong tài liệu là chỗ lệch, không phải code.
6. Mỗi `@WebMvcTest` mới vẫn cần **6 `@MockBean`** hạ tầng bảo mật, và `@Import(PlantContextResolver.class)`
   khi controller nhận `X-Plant-Id` (`R3`: nó chứa logic thật, mock nó thành no-op là xoá thứ đang test).

**Nghiệm thu mutation (3 mutation, mỗi cái đúng 1 case đỏ, đã revert):**

| Mutation | Case đỏ | Chứng minh |
|---|---|---|
| `GoodsReceiptControllerTest`: stub ném `IDEMPOTENCY_CONFLICT` thay `STATE_CONFLICT` | `cancel_receiptNotPosted_returns409StateConflict` | Assert bám `$.code`, **không** phải `status()` — cả hai đều 409 |
| `ManufacturingExecutionControllerTest`: stub ném `RESERVATION_EXCEEDED` thay `PLANNED_QUANTITY_EXCEEDED` | `reportProduction_overPlannedQuantity_...` | như trên, cả hai đều 409 |
| `GoodsReceiptService`: đổi `PLANNED_QUANTITY_EXCEEDED` về `STATE_CONFLICT` | `GoodsReceiptServiceTest.postOverRemainingQuantity_...` | Tầng service cũng khoá đúng mã, không chỉ đúng status |

> ⚠️ **Cả 3 mutation đều giữ nguyên HTTP status.** Mutation đổi status (409↔422) là bài **dễ**; nó xanh
> cũng không nói gì. Phase sau kiểm lại thì phải chọn hai mã **cùng status**.

**Breaking changes** (9 endpoint đổi HTTP status 422 → 409, FE đang code theo `code` cũ sẽ hỏng —
liệt kê từng cái, không gộp):

| # | Endpoint | Điều kiện | Cũ | Mới |
|---|---|---|---|---|
| 1 | `PATCH /boms/{id}`, `POST /boms/{id}/lines`, `PATCH /bom-lines/{id}`, `DELETE /bom-lines/{id}`, `POST /boms/{id}/activate` (5 call site của `ensureDraft`; `DELETE /boms/{id}` **không** đụng vì deactivate không gọi nó) | BOM không `DRAFT` | `OPERATION_NOT_ALLOWED` 422 | `STATE_CONFLICT` 409 |
| 2 | `POST /purchase-orders/{id}/goods-receipts` | PO không `SENT`/`PARTIALLY_RECEIVED` | 422 | `STATE_CONFLICT` 409 |
| 3 | `POST /purchase-orders/{id}/goods-receipts` | nhận vượt số còn lại (`B27`) | 422 | `PLANNED_QUANTITY_EXCEEDED` 409 |
| 4 | `POST /goods-receipts/{id}/cancel` | receipt không `POSTED` | 422 | `STATE_CONFLICT` 409 |
| 5 | `POST /purchase-orders/{id}/send`, `.../cancel` | PO không `DRAFT` | 422 | `STATE_CONFLICT` 409 |
| 6 | `POST /supply-suggestions/{id}/convert-to-purchase-requisition` | suggestion không `APPROVED` | 422 | `STATE_CONFLICT` 409 |
| 7 | `POST /purchase-requisitions/{id}/approve` | duyệt vượt số xin | 422 | `PLANNED_QUANTITY_EXCEEDED` 409 |
| 8 | `POST /purchase-requisitions/{id}/convert-to-purchase-order` | PR không `APPROVED` | 422 | `STATE_CONFLICT` 409 |
| 9 | `POST /purchase-requisitions/{id}/approve\|reject\|cancel` | PR không `DRAFT` | 422 | `STATE_CONFLICT` 409 |

6 test cũ được **sửa** theo `R10` (không xoá, không nới assertion): `BomServiceTest`,
`GoodsReceiptServiceTest` ×2, `PurchaseOrderServiceTest`, `PurchaseRequisitionServiceTest` ×2.
`GlobalExceptionHandlerTest` + 3 controller test của `T2` + `ProductionFlowE2EIT`: **0 assertion bị sửa**
(chỉ đổi **tên** một method của `InventoryControllerTest` vốn ghi "422" từ trước `F5` — `R7`).

---

### 0.16 D7b – Test Tầng HTTP Nhóm B+C (ĐÃ HOÀN THÀNH 2026-07-31)

Trả **hết** nợ **#2**. Không permission mới, không endpoint mới, **không migration**, **không breaking
change** — phase này chỉ thêm test, không sửa một dòng `src/main` nào.

| Phần | Kết quả |
|---|---|
| `D7b.1` | **6 `*ControllerTest` nhóm B** (CRUD master data): `Item` (7 case), `ItemWarehouseSetting` (5), `Supplier` (7), `User` (7), `Organization` (8), `AccessControl` (7) |
| `D7b.2` | **3 `*ControllerTest` nhóm C** (read-only): `InventoryReport` (5), `Planning` (4), `PlanningDemand` (10) |
| `D7b.3` | `A4` **được ép thật** — `PageableFactory:22` (`size < 1 ? 20 : min(size, 100)`). Không phải nợ. Nay có test HTTP ở `ItemControllerTest` (size=500 ⇒ 100) và `UserControllerTest` (size=0 ⇒ 20) |
| `D7b.4` | Tài liệu: `§0.1`, `§0.4` #2 → đã trả hết + **#26 mới**, `best-practices.md` T3/T5 → 20/20, `TEST_IMPROVEMENT_PLAN.md` I3, roadmap §6.1 + §6.1.7 |

**Hệ quả cần nhớ khi code tiếp:**
1. 🔴 **Nợ #9 chưa đóng hết như `D7` tuyên bố** — `planning` chưa bao giờ được rà. 3 chỗ đọc status
   chứng từ vẫn trả 422. Chi tiết đầy đủ + danh sách chỗ **đúng 422 đừng đụng**: nợ **#26** ở §0.4.
   `PlanningDemandControllerTest.cancel_nonOpenDemand_returns422_pendingTheStateConflictDecision`
   **pin hành vi hiện tại kèm javadoc giải thích** — cùng cách `StockBalanceRepositoryIT` từng xử lý
   nợ #8. Đừng đọc test đó rồi tưởng 422 là kết luận đã chốt.
2. **Đừng assert phân trang bằng `PageableFactory.MAX_PAGE_SIZE`/`DEFAULT_PAGE_SIZE`.** Hằng số nằm ở
   cả hai vế ⇒ đổi nó thì test **vẫn xanh** (tautology, `R6`). `D7b` assert literal `100`/`20` vì đó
   là con số `best-practices.md A4` hứa với client. Bản nháp đầu của phase này mắc đúng lỗi đó và bị
   nghiệm thu mutation bắt.
3. `PUT /inventory/item-warehouse-settings` trả **200, không 201** kể cả lần ghi đầu (§5.8: PUT là
   full replace) — `POST /planning/production-estimates` cũng **200, không 201** (không persist gì).
   Cả hai đã có test; đừng "sửa cho đúng REST".
4. `PlanningDemandController` nhận `X-Plant-Id` ở **cả** `create` (body) và `list` (query param) —
   §5.6.1 áp dụng cho query string, không chỉ body. Cả hai nhánh 409 đã có test + `verifyNoInteractions`.
5. Header `X-Plant-Id` **sai định dạng** trả `FIELD_FORMAT_INVALID` (**400**), không phải
   `STATE_CONFLICT` (409) — không parse được là input sai, khác với "parse được nhưng lệch".
6. `MethodArgumentTypeMismatchException` (§5.4, thêm ở `F1`) nay được khoá trên **controller thật**
   (`PlanningDemandControllerTest`, `OrganizationControllerTest`, `InventoryReportControllerTest` cho
   enum sai) — trước `D7b` chỉ có `GlobalExceptionHandlerTest` với controller giả.
7. `GET /reports/low-stock` trả **mảng trần**, không `PageResult` — có chủ đích (một dòng cho mỗi
   ngưỡng đã cấu hình). Đừng "thêm phân trang cho đồng bộ" mà không có yêu cầu.

**Nghiệm thu mutation (3 mutation, mỗi cái đúng 1 case đỏ / 21 case chạy, đã revert):**

| Mutation | Case đỏ | Chứng minh |
|---|---|---|
| `SupplierControllerTest`: stub ném `BUSINESS_RULE_VIOLATION` thay `OPERATION_NOT_ALLOWED` | `addItemSupplier_secondPreferred_returns422OperationNotAllowed` | Assert bám `$.code`, **không** `status()` — cả hai đều **422** |
| `UserControllerTest`: stub ném `RESOURCE_ALREADY_EXISTS` thay `USERNAME_ALREADY_EXISTS` | `create_duplicateUsername_returns409UsernameAlreadyExists` | như trên, cả hai đều **409** |
| `PageableFactory` (**production code**): `MAX_PAGE_SIZE` 100 → 500 | `ItemControllerTest.list_sizeAboveMax_isClampedToHundred` | Kết luận `D7b.3` là **test-backed**, không phải đọc source rồi tin |

> ⚠️ Hai mutation đầu giữ nguyên HTTP status (đúng cảnh báo `§0.15`). Mutation thứ ba đụng
> **`src/main`** — `D7` chỉ có 1/3 loại này, `D7b` giữ tỷ lệ đó vì phase không sửa production code.

---

### 0.17 D11 – Đóng Hai Nợ Đúng-Sai Cuối Cùng (#25, #26) (ĐÃ HOÀN THÀNH 2026-07-31)

Trả nợ **#25** và **#26**. Không permission mới, không endpoint mới, **không migration**. Phase đầu
tiên của track `D*` **sửa hành vi** thay vì thêm test ⇒ coverage và số case đều **không** phải thước đo.

| Phần | Kết quả |
|---|---|
| `D11.1` | Nợ #25: `WorkOrder.canReceipt()` (= `canExecute()` **+ `COMPLETED`**) + `WorkOrderExecutionSupport.ensureReceiptable`. **`B13` tách lần thứ ba** (lần 1 ở `D9`) |
| `D11.2` | Nợ #26: 3 chỗ `planning` đọc status chứng từ đổi `OPERATION_NOT_ALLOWED` → `STATE_CONFLICT` qua `ExceptionFactory.custom` |
| `D11.3` | 3 test cũ **sửa** theo `R10` + **6 case mới**. `PlanningDemandControllerTest` gỡ javadoc "pending", nay pin **fix** thay vì pin gap |
| `D11.4` | `ProductionFlowE2EIT` **bỏ** seed safety stock = 2 của `D5` — bằng chứng nghiệm thu thật |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Phải đổi CẢ 3 call site của vòng đời receipt, không phải một.** Kế hoạch gốc chỉ ghi
   `postNew`, nhưng `ensureExecutable` gác **ba** chỗ: `postNew` (`:382`), `submit` (`:108`),
   `approve` (`:122`). Đổi mỗi `postNew` thì tạo được `DRAFT` trên WO `COMPLETED` rồi kẹt luôn ở
   `submit` — **tồn kho vẫn không đổi, bug vẫn nguyên**, chỉ dời đi một bước và khó thấy hơn.
   `issue` / `report` / WIP **giữ** `ensureExecutable` — đụng thêm là trượt phạm vi.
2. **`COMPLETED` không còn là trạng thái cuối tuyệt đối** với receipt. Nó nghĩa là "xưởng làm xong",
   không phải "hàng đã vào kho" — `completedQuantity` là cột riêng và luôn đi sau (`§0.5`). Mọi
   `switch`/so sánh `WorkOrderStatus` đã rà (`§11.3`): không có `switch` nào; `WorkOrderSupplyService`
   (open supply) và `WorkOrderService` (cancel gate) đều **cố ý** vẫn loại `COMPLETED`, đúng như trước.
3. **Trần `B16` không nới** và là thứ **duy nhất** giữ cho WO `COMPLETED` không thành cửa mở. Đừng
   chuyển check số lượng ra sau gate status — có test riêng
   (`post_onCompletedWorkOrder_stillCannotExceedWhatWasProduced`) canh đúng chỗ đó.
4. **Phương án B của nợ #25 (hoãn `complete()`) đã bị loại có lý do**, đừng quay lại: WO làm xong mà
   chưa ai nhập kho sẽ kẹt `IN_PROGRESS` **vô thời hạn**, và nó đổi nghĩa một cột đã có dữ liệu lịch sử.
5. 🔴 **Trong `SupplySuggestionService.convertToWorkOrder` có 2 check liền nhau, giờ khác mã lỗi:**
   `!isApproved()` → **409** (status chứng từ), `!= SupplySuggestionType.WORK_ORDER` → **422** (input
   sai). Đây là chỗ dễ "đồng bộ nhầm" nhất trong repo. Bất biến `B74`.
6. `D7` tuyên bố nợ #9 "đóng hết" nhưng phạm vi rà của nó là **4 module**, `planning` không nằm trong
   đó. **Bài học:** khi một phase tuyên bố đóng nợ, ghi rõ **phạm vi đã rà**, đừng ghi "hết".

**Nghiệm thu mutation (3 mutation, đã revert):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | `canReceipt()` trả lại đúng `canExecute()` (**`src/main`**) | **4** — `post_onCompletedWorkOrder_receiptsTheOutputThatIsNotWarehousedYet`, `submit_onCompletedWorkOrder_isAllowed`, `approve_onCompletedWorkOrder_...`, `post_onCompletedWorkOrder_stillCannotExceedWhatWasProduced` | Mỗi bước vòng đời có case riêng ⇒ nếu chỉ đổi `postNew` như kế hoạch gốc thì 3 case vẫn đỏ. Đây là thứ bắt được lỗ hổng ở hệ quả #1 |
| 2 | `canReceipt()` cho luôn `CANCELLED` (**`src/main`**) | **1** — `post_onCancelledWorkOrder_shouldThrow` | Nới gate không nới quá tay |
| 3 | `ensureDraft` đổi `STATE_CONFLICT` → `IDEMPOTENCY_CONFLICT` (**cả hai đều `HttpStatus.CONFLICT`**) | **1** — `SupplySuggestionServiceTest.reject_convertedSuggestion_fails` | Assert bám `ErrorCode`, **không** phải status |

> ⚠️ Mutation #3 giữ nguyên HTTP status (đúng cảnh báo `§0.15`/`§0.16`). Khác `D7b`: **2/3** mutation
> lần này đụng `src/main` — phase sửa hành vi thì phải như vậy.

**Breaking changes** *(ghi từng cái, không gộp)*:

| # | Endpoint | Điều kiện | Cũ | Mới |
|---|---|---|---|---|
| 1 | `POST /work-orders/{id}/production-receipts` **+ `/{rid}/submit` + `/{rid}/approve`** | WO đã `COMPLETED` | `STATE_CONFLICT` 409 | **200/201** (nay hợp lệ) |
| 2 | `PATCH /planning/demands/{id}/cancel` | demand không `OPEN` | `OPERATION_NOT_ALLOWED` 422 | `STATE_CONFLICT` 409 |
| 3 | `POST /supply-suggestions/{id}/convert-to-work-order` | suggestion không `APPROVED` | 422 | `STATE_CONFLICT` 409 |
| 4 | `POST /supply-suggestions/{id}/approve\|reject` | suggestion không `DRAFT` | 422 | `STATE_CONFLICT` 409 |

> #1 là **nới lỏng** (request trước đây lỗi nay thành công) ⇒ không phá client đang chạy đúng.
> #2-#4 **đổi mã** ⇒ FE bắt theo `code` cũ sẽ hỏng. Không migration ở cả bốn.

---

### 0.18 F7 – Đối Chiếu Spec FE Toàn Diện + Đóng 5 Gap (ĐÃ HOÀN THÀNH 2026-07-31)

Phase **đầu tiên** kiểm chứng spec ở mức section/field. Migration `V38`. Bản ghi đầy đủ +
**bảng đối chiếu §1..§11**: `FRONTEND_ALIGNMENT_ROADMAP.md §3.7` và **§7**.

🔴 **Vì sao 5 gap nằm ẩn suốt 5 phase — quan trọng hơn bản thân 5 gap.** `F1`–`F6` tuyên bố đóng track
`F*` mà bằng chứng phủ spec **duy nhất** là kịch bản E2E 11 bước (§11.1). Một kịch bản happy-path
không thể phát hiện một endpoint *không tồn tại*. Cộng thêm `.docx` là binary **chưa từng được commit**
(`git log` rỗng) nên không agent nào đọc được spec gốc — mỗi phase chỉ đọc lại tham chiếu `spec §N`
rải trong markdown. Kết quả: **§9 chưa từng được nhắc tới một lần nào trong toàn repo**.
⇒ `F7` trích spec ra `docs/fe-spec-omniplant.md` và lập bảng đối chiếu §1..§11.

| # | Gap | Spec § | Nội dung |
|---|---|---|---|
| 1 | `GET /production-executions/candidates` **không tồn tại** — cả một màn hình FE không có API | §5.1 | Endpoint + query + DTO lean. Tái dùng `PERM_PRODUCTION_EXECUTION_READ` ⇒ **không** permission mới. Bất biến **`B75`** |
| 2 | Rename field mới làm 4/17 DTO ⇒ FE thấy **2 từ vựng** | §6.4 | **Phương án A** (user chốt): đổi 7 DTO trên luồng spec, giữ 17 DTO ngoài luồng |
| 3 | `cancel` không nhận `reason` | §3.2 | `V38` + `WorkOrderCancelRequest`; thiếu ⇒ `APPROVAL_REASON_REQUIRED` (400) |
| 4 | timestamps optional | §5.1 | `@NotNull`. Validate **thứ tự** vốn đã có — không đụng |
| 5 | thiếu param `search` | §3.2 | Free-text trên `workOrderNo` + product SKU |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`B75` có HAI vế.** `status ∈ {RELEASED, IN_PROGRESS}` **và** `actualGoodQuantity <
   plannedQuantity`. WO đã đạt plan không nhận report được nữa (`B54`) ⇒ đưa vào danh sách là đưa
   operator một nút bấm luôn lỗi. Lọc status không thôi vẫn "trông đúng".
2. 🔴 **Cả hai vế nằm trong JPQL ⇒ mock repository không kiểm được** (`R7`). `F7` vì thế tạo
   **`WorkOrderRepositoryIT`** (class IT thứ 7). Viết unit test với mock cho vùng này là tautology.
3. **`ManufacturingExecutionController` nay CÓ nhận `X-Plant-Id`** — đúng theo chính rule §5.6.1
   (endpoint nêu `plantId` tường minh thì cross-check), không phải phá lệ. Ranh giới là theo
   **endpoint**, không theo controller; ghi chú cũ ở `error-handling.md §5.6.1` đã sửa.
4. **Ranh giới rename là phần của quyết định, không phải chuyện dở dang.** 17 DTO ngoài luồng spec
   **cố ý giữ** `itemCode`/`lotCode`. Danh sách chính xác: roadmap §3.7 hệ quả #4.
5. ⚠️ **Rename gần như không được test bảo vệ:** đổi 6 response DTO mà chỉ **một** assertion trong
   toàn suite phát hiện. Chỗ đó nay assert cả tên mới lẫn `itemCode` `doesNotExist()`.
6. **§9 đã kiểm, code hợp lệ — đừng "sửa".** §9.3/§9.4 mô tả response ghép nhiều nhánh nhưng câu cuối
   §9 **cho phép** trả entity chính rồi FE refetch, miễn commit atomically. Ràng buộc thật là §10.1.
7. `cancel_reason` **nullable**: `NULL` = "cancel trước `F7`", **không** phải "không có lý do".

**Nghiệm thu mutation (3, đã revert):** ① bỏ vế số lượng khỏi query candidates (`src/main`) ⇒
`WorkOrderRepositoryIT.findExecutionCandidates_excludesWorkOrdersThatAlreadyReachedTheirPlan`;
② bỏ `@NotNull` khỏi `actualEndedAt` (`src/main`) ⇒ case 400; ③ `APPROVAL_REASON_REQUIRED` →
`INVALID_INPUT` (**cả hai đều `BAD_REQUEST`**) ⇒ `WorkOrderServiceTest.cancel_withoutReason_*`.
**2/3 đụng `src/main`.**

**Breaking changes:** cancel **bắt buộc** body `{reason}` · production-execution **bắt buộc**
`actualStartedAt`/`actualEndedAt` · 6 response DTO đổi `itemCode`→`itemSku` / `lotCode`→`lotNumber` ·
`MaterialIssueLineRequest.lotCode`→`lotNumber` · *(Java)* `WorkOrderResponse` thêm `cancelReason` ở
**vị trí 31**. Endpoint `candidates` và `?search=` là **additive**.

**Nợ nhỏ còn mở sau `F7`** (4 khoản, không cái nào là bug): `FRONTEND_ALIGNMENT_ROADMAP.md §7.1`.

---

### 0.19 F8 – Đóng Gap Field-Level + 4 Endpoint Spec FE (ĐÃ HOÀN THÀNH 2026-07-31)

Phase **đầu tiên** đi hết từng hàng các bảng "Trường cần hiển thị" của spec. Migration `V39`.
**Không** permission mới, **không** rename, **không** breaking change trên wire. Bản ghi đầy đủ:
`FRONTEND_ALIGNMENT_ROADMAP.md §3.8`; bảng đối chiếu đã cập nhật từng hàng: **§7**.

🔴 **Vì sao phase này tồn tại.** `F7` tuyên bố "đối chiếu spec toàn diện" và đánh ✅ cho §2.4/§3.3/
§4.2/§5.2/§6.4, nhưng phạm vi thật của nó là **grep `itemCode`/`lotCode`** — nó chưa bao giờ đi hết
bảng field. Hệ quả: `GET /production-receipts/candidates` (§6.2) **không tồn tại** — đúng cùng loại
gap #1 mà `F7` vừa đóng, nằm **ngay section kế tiếp** trong cùng file spec.
⇒ **Bài học (lần thứ hai repo học nó, sau nợ #26):** khi tuyên bố đóng một nợ, ghi rõ **phạm vi đã
rà**, đừng ghi "hết".

| Nhóm | Nội dung |
|---|---|
| **A** | 4 endpoint: `GET /production-receipts/candidates` (`B76`), `GET /production-receipts?plantId=&status=`, `GET /material-issues?plantId=&workOrderId=`, `GET /production-executions?workOrderId=` |
| **B** | ~20 field: `uom` (7 DTO), `workOrderCode` (3), `itemName` (2), `operatorUsername`/`createdByUsername` (batch), `outputLotStatus`, `workOrderRemainingGoodQuantity`/`workOrderCompletionPercent`, `bomCapturedAt`/`executionCompletedAt` (**alias, không cột mới**) |
| **C** | `V39`: `work_orders.execution_started_at` + 3 cột lineage phẳng + `mrp_runs.gross_demand_quantity` |
| **D** | 4 mục **cố ý cắt** ⇒ nợ `E`–`H` (roadmap §7.1) |
| **E** | Nợ **A** đóng: test `CONCURRENT_MODIFICATION` ở **2 tầng** |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`B76` có ba vế, và vế status NGƯỢC với `B75`.** Receipt-candidate **gồm `COMPLETED`**;
   execution-candidate loại nó. Hai query trông giống nhau, trả lời hai câu hỏi khác nhau — **đừng
   copy cái này thành cái kia**. Bỏ `COMPLETED` = dựng lại nợ #25 (`D11`) ở tầng read model.
2. 🔴 **Vế "trừ receipt đang mở" phải nằm trong JPQL.** `availableToReceipt()` **không** trừ receipt
   mở (`B16` để việc đó cho caller) ⇒ mapper một mình ra số **sai**. Query giữ gate, batch
   `sumOpenQuantityByWorkOrderIds` chỉ để hiển thị đúng số đó. 2 query/trang, 0 query/dòng.
3. 🔴 **Endpoint alias phải uỷ quyền từ CONTROLLER, không từ service.** Bản nháp đầu thêm
   `ProductionExecutionService.listFlat()` gọi `this.list(...)`: self-invocation **bypass Spring
   proxy** ⇒ `@PreAuthorize` **không chạy**, endpoint thành công khai. Lỗ hổng bảo mật thật.
4. 🔴 **`X-Plant-Id` theo ENDPOINT, không theo controller — phase thứ BA chạm bẫy này.**
   `ManufacturingExecutionController` nay chứa **cả hai loại cùng lúc**: 3 endpoint plant-scope **có**
   cross-check, endpoint thứ tư (định danh bằng aggregate) **cố ý không**. Cả hai phía đã pin bằng
   test (409 vs 200). Chi tiết: `error-handling.md §5.6.1`.
5. **`F8` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi** (`C10` không kích hoạt).
   Ghi tường minh để người đọc sau không tưởng là bị quên. `V17` seed đúng **một** permission/loại
   chứng từ, mô tả nguyên văn *"Post **and read** … documents"*, nên 4 endpoint đọc dùng `MANAGE` sẵn có.
   🔴 Hệ quả chấp nhận: user chỉ có `PERM_QUALITY_DISPOSITION` **không** mở được danh sách receipt.
   Sửa đúng chỗ là **role seed** — quyết định nới quyền, ngoài phạm vi `F8`.
6. **Nới `@EntityGraph` TRƯỚC khi thêm field** (ràng buộc thứ tự cứng duy nhất của phase).
   `ProductionExecutionRepository` chỉ fetch `operation`; mọi field mới đi qua `getWorkOrder()`. Làm
   ngược ⇒ N+1 trên endpoint phẳng, và first-level cache của endpoint **nested** (mọi dòng chung 1 WO)
   **che mất** lỗi khi test.
7. **Hai số Summary đặt trên `ProductionExecutionResponse`, KHÔNG trên `WorkOrderResponse`** — ở đó
   `workOrderRemainingGoodQuantity` (planned − **actualGood**) sẽ nằm cạnh `remainingQuantity`
   (planned − **completed**), hai con số khác hẳn.
8. **`bomCapturedAt`/`executionCompletedAt` KHÔNG có cột** — là alias của `createdAt`/`completedAt`.
   Bất đối xứng với `routing_captured_at` **có lý do** (routing tuỳ chọn, có thể vắng); đừng "thêm cột
   cho đồng bộ".
9. **`grossDemandQuantity` chỉ cộng `level == 0`** — cấp sâu hơn được dẫn xuất qua BOM, cộng hết mọi
   cấp thì cùng một nhu cầu bị đếm một lần cho mỗi cấp.

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main`**):** ① bỏ số hạng "trừ receipt đang mở"
khỏi query candidates ⇒ `WorkOrderRepositoryIT.findReceiptCandidates_excludes…ClaimedByAnOpenDraft`;
② batch nhầm `workOrder.getCreatedBy()` thay `getOperatorUserId()` ⇒
`ProductionExecutionServiceTest.list_resolvesTheOperatorUsername…` (**HTTP vẫn 200**, payload sai âm
thầm); ③ `CONCURRENT_MODIFICATION` → `STATE_CONFLICT` (**cả hai đều 409**) ⇒
`GlobalExceptionHandlerTest.optimisticLockConflict_*`; ④ `completionPercent` `HALF_UP` → `FLOOR` ⇒
`WorkOrderTest.completionPercent_roundsToTwoDecimalsHalfUp`. Mỗi mutation làm đỏ **đúng 1** case.

**Breaking changes — wire: KHÔNG có cái nào** (thuần additive, FE đang chạy không hỏng).
**Java positional: có** — 9 record thêm component; `WorkOrderService.createFromMrp` 3 → **4** tham số;
`MrpRun.complete(...)` 8 → **9**; 3 service đổi constructor;
`ManufacturingExecutionMapper.toResponse(...)` của execution/issue nhận thêm `Map<UUID,String>`.
Test cũ **sửa** theo `R10`, không xoá, không nới assertion.

> 🔴 **Đính chính (thêm ở `F9`, cùng ngày):** `F8` **để sót 2 field** và — nghiêm trọng hơn — **tuyên
> bố quá phạm vi nó thực sự đã rà**. Nó ghi "Đã đi hết bảng field" vào roadmap §7 hàng §3.3/§5.2 trong
> khi thực tế bám **danh sách 7 DTO trong kế hoạch của chính nó**, không bám bảng spec. Hai field thiếu
> (`uom` trên execution-candidate, `reservedQuantity` trên component line) đóng ở **`F9`, §0.20**.
> Đây là **lần thứ ba** repo mắc lỗi "tuyên bố đóng rộng hơn phạm vi đã rà" — xem bảng ở §0.20.

---

### 0.20 F9 – Hai Field Cuối + Sửa Tuyên Bố Nói Quá Của `F8` (ĐÃ HOÀN THÀNH 2026-07-31)

**Không migration**, không permission, không rename, không breaking change trên wire.
Bản ghi đầy đủ: `FRONTEND_ALIGNMENT_ROADMAP.md §3.9`.

🔴 **Lý do phase tồn tại quan trọng hơn 2 field nó đóng — đây là LẦN THỨ BA repo mắc cùng một lỗi:**

| Lần | Tuyên bố | Phạm vi thật | Hệ quả |
|---|---|---|---|
| 1 | `D7`: nợ #9 "**đóng hết**" | 4 module; `planning` chưa bao giờ nằm trong đó | nợ #26 → `D11` |
| 2 | `F7`: "**đối chiếu spec toàn diện**", ✅ cho 5 section | grep `itemCode`/`lotCode` | 5 gap → `F8` |
| 3 | `F8`: "**Đã đi hết bảng field**" | Bám **danh sách 7 DTO trong kế hoạch của chính nó**, không bám bảng spec | 2 field → `F9` |

⇒ **Quy tắc bắt buộc từ nay:** khi cập nhật roadmap §7, ghi **đã rà tới đâu** vào cột Ghi chú.
**Không bao giờ ghi "xong"/"hết"/"toàn diện".** Và khi kế hoạch phase liệt kê một danh sách field,
danh sách đó là **gợi ý**, bảng spec mới là **nguồn** — `F8` sai đúng ở chỗ đảo ngược hai thứ này.

| # | Field đã đóng | Spec | Ghi chú |
|---|---|---|---|
| 1 | `uom` trên `ProductionExecutionCandidateResponse` | §5.2 "Context" (ô 4/4) | **0 query** (`productItem` vốn đã `join fetch`). `F8` **đã nhìn thấy ô này và cố ý bỏ qua** |
| 2 | `reservedQuantity` trên `WorkOrderComponentLineResponse` | §3.3 "Requirement" (5/6 → 6/6) | Trước `F9` chỉ có ở `/material-readiness` ⇒ FE gọi **2 API cho một bảng** |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`reservedQuantity` phải là ĐÚNG CÙNG số của `/material-readiness`**
   (`sumActiveRemaining*` = `sum(quantity − consumedQuantity)` của reservation `ACTIVE`). Hai màn hình
   cùng tên field, khác số, là tệ hơn thiếu field. Javadoc `@link` hai chiều đã ghi: đổi thì đổi cả hai.
2. 🔴 **Batch phải NGOÀI `page.map(...)`, và CẢ HAI đường của `WorkOrderService` đều cần.**
   `toResponse(WorkOrder)` là chỗ **mọi** response 1-work-order đi qua (9 call site). Chỉ sửa `list`
   thì đường mutation trả `null` ⇒ FE thấy field lúc có lúc không.
3. **`sumActiveRemainingByWorkOrderIds` tái dùng `ComponentQuantityProjection` KHÔNG đổi**, không mang
   `workOrderId` — `componentLineId` duy nhất toàn cục.
4. **`group by` không trả hàng cho line không có reservation** ⇒ `getOrDefault(..., ZERO)`.
5. **`childBom` (§3.3) là nợ `I`** — WO snapshot direct-only theo `B12`, MRP nổ cấp sâu thành WO riêng.
   Không phải field quên (user chốt).
6. **`F9` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi.**

**Nghiệm thu mutation (3, đã revert — 3/3 đụng `src/main`):** ① bỏ `status = ACTIVE` khỏi query batch
⇒ `MaterialReservationRepositoryIT.sumActiveRemainingByWorkOrderIds_countsActiveReservationsOnly`;
② `toCandidateResponse` truyền `getName()` thay `getUnit()` ⇒ `ProductionExecutionServiceTest`
(**HTTP vẫn 200**; controller test **không** đỏ vì nó stub service — đúng phân vai); ③ chuyển batch
**vào trong** `page.map(...)` ⇒ `WorkOrderServiceTest.list_resolvesReservedQuantityInOneBatchQuery…`
(output **byte-identical**, chỉ số query đổi).

**Breaking changes — wire: KHÔNG có.** Java positional: 2 record +1 component,
`WorkOrderMapper.toResponse` +1 tham số, `WorkOrderService` constructor +1
(`MaterialReservationRepository`). Test cũ **sửa** theo `R10`.

---

### 0.21 F10 – Ba Nợ Field-Level Cuối Của Spec FE (`G`, `F`, `H`) (ĐÃ HOÀN THÀNH 2026-08-01)

Migration **`V40`**. **Không** permission mới, **không** rename, **không** breaking change trên wire.
Bản ghi đầy đủ: `FRONTEND_ALIGNMENT_ROADMAP.md §3.10`.

**Phạm vi do user chốt (2026-08-01):** đóng nợ **`G`** + **`F`** + **`H`** ở roadmap §7.1 — cả ba đều
là *"backend đã có dữ liệu, chỉ thiếu chỗ chứa"*. **Nợ `E` cố ý loại khỏi phạm vi** (`BomHeader` không
có cột `code`, và `outputQuantity` đổi **công thức nổ BOM** ⇒ đổi số MRP lẫn component line của mọi WO
— rủi ro khác hẳn).

| Nợ | Đóng bằng | Bất biến |
|---|---|---|
| **G** | `mrp_requirement_lines.projected_available_quantity` — **nullable, KHÔNG backfill**. Giá trị là `remainingCoverage` mà `MrpCalculationService:78-88` vốn **đã tính rồi vứt đi** | **`B77`** |
| **F** | `supply_suggestions.source_routing_code`/`_version` + `RoutingLookupService.findActiveRoutingSummaries` (**thay** `findItemIdsWithActiveRouting`) | **`B78`** |
| **H** | `material_issues.code` (`MI-`) + `production_executions.code` (`PE-`), backfill + `UNIQUE`, sinh ở `@PrePersist`; execution `status` = **hằng `"POSTED"`, không cột** | **`B79`** |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Nợ `G` không phải "thiếu field" mà là "derive ra SỐ SAI".** Số bị trừ (`consumedCoverageByItem`)
   chỉ sống trong **một** lần chạy calculate và chưa bao giờ persist ⇒ khi một item nằm trên **nhiều**
   requirement line, mapper cộng `available + openSupply` báo số **lớn hơn thực tế** và phá đẳng thức
   `net = max(0, gross + safety − projected)` mà FE dùng để tự kiểm.
2. 🔴 **`NULL` ≠ 0, và KHÔNG được backfill.** `NULL` = "run chạy trước `V40`". Backfill bằng
   `available + openSupply` là ghi **vĩnh viễn** đúng con số sai mà cột này sinh ra để thay thế.
3. 🔴 **`findItemIdsWithActiveRouting` bị THAY, không phải bổ sung** — `keySet()` của map mới trả lời
   luôn câu "có routing `ACTIVE` không?". Giữ cả hai = **2 query cho cùng dữ liệu** mỗi cấp BOM (`C14`).
4. 🔴 **`@PrePersist`, không gán code sau `save()`**, và **công thức Java phải khớp từng ký tự với
   backfill SQL** — lệch một ký tự thì dòng lịch sử mang mã không truy ngược về id được, và `UNIQUE`
   **không** bắt được. Đây là bài học `§0.12` #5 (`B68`) áp dụng lần thứ hai.
5. **Prefix `PE-` lệch spec có chủ đích** — §5.2 gọi "Mã WIP" nhưng `wip_transactions` là bảng ledger
   khác và không có `code`.
6. **`status` của execution KHÔNG thành cột** — không có đường huỷ/đảo nên cột một-giá-trị là
   speculative (`§11.5`), cùng lối tư duy alias `bomCapturedAt`/`executionCompletedAt` của `F8`.
7. **Số MRP không đổi một chữ số nào** — phase chỉ *lưu lại* con số netting vốn đã tính.
8. **`F10` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi** (`C10` không kích hoạt).

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Persist `available + openSupply` thay `remainingCoverage` | **1** — `MrpCalculationServiceTest.calculate_sameItemOnTwoRequirementLines_reportsTheCoverageLeftForTheSecondLineNotTheGrossAvailable` | Case đẳng thức (`.calculate_projectedAvailableIsTheFigureTheNettingUsed`) **vẫn xanh** — 1 line thì hai công thức trùng nhau ⇒ phải có **cả hai** case mới khoá được |
| 2 | Bỏ `@PrePersist` khỏi `MaterialIssue.assignCode()` | `MaterialIssueRepositoryIT` (5 case error) | `NOT NULL` nổ ngay ở INSERT — đúng failure mode javadoc mô tả; mock repository **không** thấy được |
| 3 | Bỏ vế `suggestionType == WORK_ORDER` khỏi `carriesRouting` | **1** — `.calculate_makeProposal_freezesTheActiveRoutingCodeAndVersion_buyProposalCarriesNone` | 🔴 **Lần chạy đầu XANH** ⇒ lộ ra test bản nháp assert `null` **vì lý do sai** (component không có routing để mang). Test đã sửa: stub cấp routing cho **cả** component |
| 4 | Prefix `MI-` → `MX-` **chỉ trong Java**, giữ SQL | **2** — `FlywayMigrationIT.migrate_v40_backfills…` + `MaterialIssueRepositoryIT.persist_withoutACode_…` | Canh đúng chỗ Java↔SQL lệch nhau; assert bằng **literal**, không bằng `assignCode()` (tautology `D7b.3`) |

> 🔴 **Mutation #3 là kết quả giá trị nhất của phase** — nó chứng minh vì sao "nghiệm thu mutation" khác
> "chạy test cho xanh": assertion `isNull()` xanh **không** có nghĩa là nhánh đó được khoá.

---

### 0.22 D8a – Refresh Token Reuse Detection (RTR) (ĐÃ HOÀN THÀNH 2026-08-03)

**Không migration** (thuần Redis), **không** permission mới, **không** đổi request/response DTO.
Phase đầu tiên của track `D8` — và là phase `D*` thứ hai **sửa hành vi** thay vì thêm test (sau `D11`).
Trả **1/3** nợ #6. Thiết kế gốc: `common/security/CLAUDE.md` §4.12. Bất biến: **`B80`**.

**Vấn đề đã đóng:** trước `D8a`, refresh token cũ bị đánh cắp rồi dùng lại sau khi đã rotate chỉ trả
`REFRESH_TOKEN_EXPIRED` — **giống hệt** hết hạn tự nhiên. Server không có cách nào phân biệt sự cố
bảo mật với hoạt động bình thường.

| Thay đổi | Ở đâu |
|---|---|
| `markRefreshTokenUsed` / `wasRefreshTokenUsed` + key `auth:refresh:used:{tokenId}` TTL 60s | `TokenStoreService` |
| Tách nhánh `stored == null` khỏi nhánh mismatch; nhánh reuse force-logout + audit + throw | `AuthService.refresh` |
| `TOKEN_REUSE_DETECTED` (401) | `AuthErrorCode` |
| `SUSPICIOUS_TOKEN_REUSE` | `AuditAction` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Thứ tự Redis khi rotate là lưu-mới → mark-used → xoá-cũ, và cả ba bước đều bắt buộc.**
   Marker "used" phải tồn tại **trước khi** key cũ biến mất — nếu không, một request reuse rơi đúng
   khoảng hở đó đọc thấy `stored == null` **và** `used` chưa có ⇒ bị chẩn đoán nhầm thành hết hạn
   bình thường, **bỏ lọt tín hiệu tấn công**. Và **không** được bỏ bước xoá riêng: không xoá thì token
   cũ vẫn `stored != null`, rotation coi như vô hiệu (token cũ sống tới hết TTL 7 ngày).
2. 🔴 **RTR chỉ áp dụng nhánh `stored == null`.** Nhánh `stored != null` nhưng giá trị mismatch
   **giữ** `REFRESH_TOKEN_EXPIRED` — tokenId còn sống nghĩa là nó **chưa từng** bị rotate away, đó là
   giá trị sai/bị sửa chứ không phải replay. Có test `never()` canh đúng chỗ này.
3. 🔴 **Key `used` cố ý KHÔNG có segment `{userId}`.** Nó phải nằm **ngoài** pattern SCAN
   `auth:refresh:{userId}:*` của `deleteAllUserTokens`, nếu không chính thao tác force-logout sẽ xoá
   mất cái marker chứng minh có reuse. Có test canh (`markRefreshTokenUsed_keyIsNotSweptBy…`).
4. **Force-logout gọi CẢ `deleteAllUserTokens` LẪN `deleteAllDeviceSessions`** — thiết kế §4.12 chỉ
   ghi cái đầu; `D8a` gọi cả hai cho khớp `logoutAll()`.
5. **Hai giới hạn đã biết, CHẤP NHẬN — đừng tự mở rộng phạm vi để "sửa":** (a) race 2 request refresh
   đồng thời cùng 1 token hợp lệ không giải được bằng thứ tự thao tác, cần lock/CAS mà thiết kế không
   có; (b) hệ quả là client double-submit (network retry) có thể bị force-logout **oan** — giới hạn cố
   hữu của RTR cơ bản không có grace window.
6. **`D8a` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi** (`C10` không kích hoạt).

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Bỏ nhánh `if (wasRefreshTokenUsed(...))`, luôn nhảy thẳng `REFRESH_TOKEN_EXPIRED` | `refresh_reusedToken_throwsTokenReuseDetectedAndForceLogoutAll` (assertion dòng 263) | **HTTP vẫn 401** ở cả hai mã ⇒ assert phải bám `ErrorCode`, không bám status. ⚠️ Case thứ hai (`refresh_storedTokenNull_*`) cũng đỏ nhưng **do `UnnecessaryStubbing`**, không phải kill hành vi — đừng tính là bằng chứng |
| 2 | Đổi thứ tự thành mark-used → xoá-cũ → lưu-mới | **1** — `refresh_validToken_rotatesAndReturnsNewPair` (`InOrder`) | Response **byte-identical**; chỉ assertion thứ tự bắt được. Đây là mutation giá trị nhất của phase |
| 3 | Bỏ `deleteAllDeviceSessions` khỏi nhánh reuse | **1** — `refresh_reusedToken_*` (dòng 268) | Mã lỗi **và** HTTP status đều không đổi ⇒ chỉ `verify` side-effect bắt được (`R4`) |
| 4 | Cho nhánh mismatch cũng gọi `wasRefreshTokenUsed` | **1** — `refresh_storedTokenMismatch_throwsRefreshTokenExpired` (`never()` vi phạm) | Khoá ranh giới ở hệ quả #2 |

**Breaking changes — wire: KHÔNG có** (thuần additive: 1 mã lỗi mới, DTO không đổi).
**Java positional: không có.** **Hành vi:** reuse token cũ nay **buộc đăng xuất toàn bộ thiết bị**
thay vì chỉ báo hết hạn — đó là mục đích của tính năng, không phải tác dụng phụ.

---

### 0.23 D8b – Absolute Session Timeout (ĐÃ HOÀN THÀNH 2026-08-03)

**Không migration** (thuần Redis), **không** permission mới, **không** đổi request/response DTO.
Phase thứ hai của track `D8`. Trả **2/3** nợ #6. Thiết kế: `common/security/CLAUDE.md` §4.15.
Bất biến: **`B81`**.

**Vấn đề đã đóng:** refresh token TTL 7 ngày **sliding** ⇒ user active liên tục thì phiên **không
bao giờ** kết thúc. Rủi ro `S13` đã ghi trong `best-practices.md §8.1` từ lâu, chưa có dòng code nào.

| Thay đổi | Ở đâu |
|---|---|
| `saveSessionStart` / `getSessionStart` + key `auth:refresh:{userId}:{tokenId}:meta` TTL 7d | `TokenStoreService` |
| `deleteRefreshToken` xoá **cả** key `:meta` (tránh orphan) | `TokenStoreService` |
| `login` stamp session start; `refresh` check `B81` + carry-forward | `AuthService` |
| `absoluteSessionTimeoutMs` (component thứ 4) + `app.jwt.absolute-session-timeout-ms: 2592000000` | `JwtProperties`, `application.yml`, `application-dev.yml` |
| `SESSION_ABSOLUTE_TIMEOUT` (401) · `AuditAction.SESSION_ABSOLUTE_TIMEOUT` | `AuthErrorCode`, `AuditAction` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Carry-forward khi rotate là bất biến, không phải chi tiết triển khai.** Rotate phải mang
   **nguyên** `sessionCreatedAt` cũ sang `newTokenId`. Stamp `now` ở đó ⇒ đồng hồ absolute reset mỗi
   15 phút ⇒ **tính năng thành no-op hoàn toàn**, trong khi response **byte-identical**, mã lỗi và
   HTTP status không đổi, **coverage không đổi**. Chỉ assertion đối số bắt được (mutation #1).
2. 🔴 **Vị trí check: SAU validate `stored`, TRƯỚC rotate.** *Sau* — caller không cầm token hợp lệ
   thì không được biết gì về tuổi phiên. *Trước* — phiên hết hạn tuyệt đối **không được** nhận cặp
   token mới. Đặt sau rotate thì vẫn ném đúng mã lỗi, đúng 401, chỉ khác là Redis đã có cặp mới —
   chỉ `verify(never())` bắt được (mutation #2).
3. 🔴 **Key `:meta` cố ý NẰM TRONG pattern SCAN `auth:refresh:{userId}:*`** ⇒ `deleteAllUserTokens`
   quét luôn, **không cần** sửa method đó. Đây là yêu cầu **NGƯỢC** với key `auth:refresh:used:`
   của `D8a` (cố ý nằm **ngoài**, để force-logout không xoá mất marker chứng minh reuse). Hai key,
   hai yêu cầu trái nhau, cùng namespace — mỗi cái có một test canh đúng chiều của nó, đặt cạnh nhau
   trong `TokenStoreServiceTest`. **Đừng "đồng bộ" một trong hai.**
4. **Phiên không có stamp (login trước `D8b`) là fail-open** — coi như bắt đầu **từ bây giờ**, không
   phải "đã hết hạn". Fail-closed sẽ đăng xuất **toàn bộ** user đang online ngay lúc deploy mà không
   tăng bảo mật (refresh TTL 7 ngày ⇒ trong một tuần mọi phiên sống đều có stamp). Quyết định này
   **có test + mutation riêng**, không để ngầm.
5. **Chốt companion key, KHÔNG phải JSON payload** như thiết kế gốc §4.15 mô tả (user chốt
   2026-08-03). Nhờ vậy `saveRefreshToken`/`getRefreshToken`/toàn bộ `B80` **không đổi một dòng nào**
   — `InOrder` của `D8a` vẫn đúng vì nó chỉ ràng buộc thứ tự **tương đối** giữa 3 lời gọi đã verify.
6. **Giới hạn đã biết, chấp nhận:** timeout chỉ được đánh giá **khi refresh**. Access token đang cầm
   (TTL 15 phút) vẫn dùng được tới hết hạn dù phiên vừa vượt mốc — cửa sổ tối đa bằng đúng
   access-token TTL. Đóng nó cần kiểm ở `JwtAuthenticationFilter` (mỗi request thêm một lượt đọc
   Redis), không tương xứng rủi ro.
7. **`D8b` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi** (`C10` không kích hoạt).
8. **Sửa mâu thuẫn tài liệu ↔ code về session** (user chốt): `architecture-decisions.md` +
   `common/security/CLAUDE.md §4.10` từng ghi "cho phép nhiều thiết bị đăng nhập đồng thời", nhưng
   `AuthService.login` xoá sạch mọi phiên cũ mỗi lần login ⇒ code là **single-session**. **Sửa tài
   liệu theo code, KHÔNG đổi hành vi** — bỏ 2 dòng `deleteAll*` là quyết định bảo mật riêng.

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Carry-forward stamp `Instant.now()` thay vì start cũ | **1** — `refresh_carriesTheOriginalSessionStartForwardToTheNewTokenId` | 🔴 **Giá trị nhất của phase.** Response byte-identical, mã lỗi/status/coverage đều không đổi, tính năng thành no-op. Chỉ assertion **đối số** bắt được |
| 2 | Dời check timeout xuống **sau** rotate | **1** — `refresh_sessionOlderThanAbsoluteTimeout_…` (`NeverWantedButInvoked` ở `never().saveRefreshToken`) | Mã lỗi **và** HTTP status đều không đổi ⇒ chỉ `verify` side-effect bắt được (`R4`). ⚠️ 2 case khác cũng đỏ nhưng **do mock `jwtProperties` trả 0 mặc định**, không phải kill hành vi — đừng tính là bằng chứng |
| 3 | `SESSION_ABSOLUTE_TIMEOUT` → `REFRESH_TOKEN_EXPIRED` (**cả hai đều 401**) | **1** — `refresh_sessionOlderThanAbsoluteTimeout_…` | Assert bám `ErrorCode`, **không** bám status — đúng cảnh báo §0.15 |
| 4 | Nhánh legacy dùng `Instant.EPOCH` thay `now` (fail-closed) | **1** — `refresh_sessionWithoutStartStamp_isTreatedAsStartingNow` | Quyết định fail-open là **test-backed**, không phải chỉ văn xuôi |

**Breaking changes — wire: KHÔNG có** (thuần additive: 1 mã lỗi mới trên `POST /auth/refresh`,
DTO không đổi). **Java positional: có** — `JwtProperties` 3 → **4** component; 2 test class đã **sửa**
theo `R10` (`TokenStoreServiceTest`, `JwtTokenProviderTest`). **Hành vi:** phiên quá 30 ngày buộc
login lại dù user vẫn active — đó là **mục đích** tính năng.

---

### 0.24 Bugfix Từ Log Production: `lower(bytea)` + 500 Do Thiếu Query Param (2026-08-04)

**Không** phase, **không** migration, **không** permission, **không** đổi contract. Ba lỗi do user báo
từ log thật, không phải từ đối chiếu spec — đây là **lần đầu** repo sửa bug phát hiện bằng đường đó.

| # | Lỗi | Chỗ sửa |
|---|---|---|
| 1 | `GET /plants/{plantId}/work-orders` trả **500** khi **không** có `?search=` | `WorkOrderRepository.search` — `cast(:search as string)` |
| 2 | `GET /suppliers` có **y nguyên** lỗi #1 (chưa ai gọi tới nên chưa lộ) | `SupplierRepository.search` — cùng cách |
| 3 | `GET /inventory/movements` thiếu `warehouseId` trả **500 `INTERNAL_SERVER_ERROR`** | `GlobalExceptionHandler` — handler thứ **7** cho `MissingServletRequestParameterException` ⇒ **400 `VALIDATION_ERROR`** + `errors[{field}]` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Parameter `String` nullable trong JPQL BẮT BUỘC `cast(... as string)` khi nó đi vào `concat`/
   `like`.** Bind `null` không kèm JDBC type ⇒ Postgres phải suy type cho `'%' || ? || '%'` từ một
   mình parameter, chọn overload **`bytea || bytea`**, rồi chết ở **parse time**:
   `function lower(bytea) does not exist`. Nhánh short-circuit `:search is null` **không cứu được** —
   Postgres định type toàn bộ expression **trước khi** evaluate bất cứ thứ gì. Đây là lỗi **toàn bộ
   endpoint 500**, không phải lọc sai.
2. 🔴 **`grep "concat('%'"` trước khi viết query search mới.** Hai chỗ duy nhất trong repo dùng pattern
   này thì **cả hai** đều dính — nó là lỗi copy-paste của pattern, không phải sai sót một lần.
3. 🔴 **Lỗi này mock repository không thấy được** (`R7`) và **coverage không thấy được** — dòng vẫn
   được "phủ" bởi unit test dùng mock. Chỉ chạy JPQL thật mới bắt: `WorkOrderRepositoryIT` **đã có**
   case đúng chỗ (`search_withoutATermReturnsEveryWorkOrderOfThePlant`) và **đang đỏ** suốt từ `F7` —
   nghĩa là lỗ hổng thật không phải thiếu test mà là **đọc kết quả build không kỹ** (xem đính chính ở
   hàng "Baseline test" §0.1). `SupplierRepositoryIT` (**class IT thứ 11**) là guard mới cho #2.
4. **Thiếu `@RequestParam` bắt buộc là 400, không phải 500.** `F1` thêm 5 handler vào
   `error-handling.md §5.4` để một UUID sai định dạng không thành lỗi server, nhưng
   `MissingServletRequestParameterException` **bị sót**. Trả 500 khiến FE không phân biệt được "tôi
   gọi sai" với "backend chết", và làm alert 5xx nổ oan.
5. **`MissingRequestHeaderException` cố ý KHÔNG có handler** — mọi `@RequestHeader` trong repo đều
   `required = false` (`Idempotency-Key`, `X-Plant-Id`) nên nó **không có đường ném**; thêm handler là
   code speculative (`§11.5`). Có header bắt buộc đầu tiên thì **đó** mới là lúc thêm.
6. **`docs/api-guide-for-frontend.md` dòng "Lịch sử movement" trước đây ghi trống query param** trong
   khi dòng ngay trên nó ghi rõ `balances?warehouseId=` (bắt buộc) ⇒ FE gọi thiếu là hệ quả trực tiếp
   của tài liệu, không phải FE tự sai. Đã sửa. Đúng cảnh báo 🔴 ở "BẢN ĐỒ TÀI LIỆU": file đó là thứ FE
   đang dùng để wire API.

**Nghiệm thu mutation (2, đã revert — **2/2 đụng `src/main`**):** ① bỏ `cast` khỏi
`SupplierRepository.search` ⇒ **2** case đỏ (`search_withoutAKeywordReturnsEverySupplierOfTheCompany`,
`...StillHonoursTheStatusFilter`) — 2 case có keyword vẫn xanh, đúng như dự đoán, nên **phải có case
keyword `null`** mới khoá được; ② đổi `@ExceptionHandler` sang exception khác ⇒ **1** case đỏ
(`GlobalExceptionHandlerTest.missingRequiredQueryParam_returns400NamingTheParameter`, `500 != 400`).

**Coverage: KHÔNG đo lại** — bugfix 3 dòng production code, và số coverage ở §0.1 vẫn là số đo sau
`D8b`. Đừng đọc nó như đã tính phần này.

**Breaking changes — wire: KHÔNG có.** #1/#2 là endpoint trước đây **luôn 500** nay hoạt động; #3 đổi
**500 → 400** cho request vốn đã sai (client đang chạy đúng không bị ảnh hưởng; client bắt 5xx để retry
thì nay thấy 400 — đó là **sửa sai**).

---

### 0.25 C2-5 – CORS + Dữ Liệu Test Multi-Plant + Sửa Lệch RBAC Seed↔Tài Liệu (2026-08-04)

Phase thứ hai của track **`C2-*`** (đóng gap API cho Capstone 2 theo `BACKEND_CAPSTONE2_API_GAPS.md`;
phase đầu là `C2-0` — xem `docs/capstone2-api-gap-response.md`). Migration **`V41`**.
**Không** endpoint mới, **không** đổi DTO, **không** breaking change trên wire.

| Phần | Nội dung |
|---|---|
| `C2-5a` | **CORS** — `CorsProperties` (`app.cors.*`) + `corsConfigurationSource()` trong `SecurityConfig`. Trước phase này repo có **0 dòng** cấu hình CORS |
| `C2-5b` | **`src/main/resources/db/dev-seed.sql`** — 1 company + 2 plant + 6 warehouse + 3 account (`manager.a`, `operator.a`, `manager.b`), scope theo plant. **Chạy tay, KHÔNG phải migration** |
| `C2-5c` | **`V41`** — cấp 10 permission lõi cho `MANAGER`, 6 cho `OPERATOR` theo `docs/roles-and-permissions.md` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Defect nặng nhất của phase không nằm trong kế hoạch: seed RBAC cấp 12 permission lõi cho
   riêng `ADMIN`.** Quy luật: seed **sớm** (`V7`–`V15`) chỉ ghi `WHERE r.code = 'ADMIN'`; seed **sau**
   (`V17`+) ghi `IN ('ADMIN','MANAGER','OPERATOR')`. Hệ quả đo được: login `manager.a` (scope PLANT-A)
   gọi `GET /plants/{A}/work-orders` trả **403 `PERMISSION_DENIED` ngay trên plant của chính nó** ⇒
   **mọi account không phải `admin` đều vô dụng** với các màn hình lõi. Đây là khoản nợ `F8` đã ghi và
   cố ý hoãn ("*sửa đúng chỗ là role seed — quyết định nới quyền, ngoài phạm vi F8*"), nay đóng bằng
   `V41` theo quyết định của user: **tài liệu là nguồn, code là chỗ trôi**.
2. 🔴 **`PermissionCatalogTest` không bắt được lỗi này, và đó là bài học.** Nó chỉ chứng minh mọi
   `PERM_*` trong `@PreAuthorize` **có dòng trong bảng `permissions`** — "tồn tại" và "được cấp cho
   role nào" là **hai sự thật khác nhau**, sự thật thứ hai chỉ nằm ở `role_permissions` và chỉ DB thật
   trả lời được. 3 test mới ở `FlywayMigrationIT`:
   - `migrate_v41_leavesNoCorePermissionGrantedToAdminAlone` — bất biến hình dạng *"mọi permission phải
     dùng được bởi ai đó không phải superuser, trừ khi khai báo tường minh là cấu hình hệ thống"*.
     Tập admin-only **đầy đủ** sau `V41` là **đúng 2**: `PERM_ORG_MANAGE`, `PERM_ACCESS_MANAGE`.
   - `migrate_v41_grantsManagerTheCorePermissionsTheRolesDocPromises` — checklist **dương** cho MANAGER.
     Cần cả hai: test trên nhận "MANAGER **hoặc** OPERATOR" nên một mình nó không thấy quyền rơi sai vế.
   - `migrate_v41_keepsOperatorOutOfApprovalAndConfigurationPermissions` — separation of duties viết
     dưới dạng **cấm**, phần mà checklist dương không bao giờ diễn đạt được.
3. 🔴 **`dev-seed.sql` KHÔNG được biến thành migration, và có HAI lý do — lý do thứ hai mới là lý do
   quyết định.** (a) Migration dưới `db/migration` chạy ở **mọi** môi trường, account demo có mật khẩu
   công khai không được tới đó. (b) **`spring.profiles.active` default là `dev`, và default đó áp dụng
   cả khi chạy test** ⇒ wire vào `spring.flyway.locations` của `application-dev.yml` là bơm dữ liệu demo
   vào Testcontainer của **cả 11 class IT** và âm thầm đổi thứ chúng assert.
4. 🔴 **CORS phải wire qua `http.cors(...)`, không phải `WebMvcConfigurer` riêng.** Preflight `OPTIONS`
   **không** mang `Authorization` nên không thể qua `.anyRequest().authenticated()`; chỉ đường
   `http.cors(...)` mới cho Spring Security short-circuit preflight **trước** authorization. Làm sai thì
   `curl` vẫn chạy (không phải browser, không preflight) mà **mọi** request từ browser đều chết — đúng
   loại bug sống sót qua test tay.
5. **`SecurityConfig` cần `@EnableConfigurationProperties(CorsProperties.class)`.**
   `@ConfigurationPropertiesScan` của app phủ full context, nhưng `@WebMvcTest` slice chỉ `@Import`
   `SecurityConfig` thì **không** chạy scan đó ⇒ 6 test của `SecurityFilterChainTest` +
   `AuthMeSecurityTest` chết vì thiếu bean. Sửa ở **production config** (nó là consumer), không mock ở
   từng test (`R3`: `CorsProperties.validate()` chứa logic).
6. **`"*"` bị từ chối ngay lúc khởi động**, không phải "khuyến cáo không nên": API này gửi
   `Authorization` mọi request, mà CORS spec cấm wildcard origin trên credentialed request. Ném từ
   `CorsProperties.validate()` để thông báo nêu **tên property** thay vì lỗi từ trong lòng filter.
7. **`exposed-headers` là phần của contract, không phải trang trí.** `X-Trace-Id` là thứ FE trích dẫn
   khi báo lỗi, `X-RateLimit-*` + `Retry-After` là thứ FE cần để backoff — không expose thì JS **không
   đọc được** dù server có gửi.
8. **`items.unit`, UOM, lot, audit… không đụng** — `C2-5` chỉ làm hạ tầng chạy được. Các phase còn lại
   của `C2-*`: xem plan roadmap + `docs/capstone2-api-gap-response.md` §3.

**Nghiệm thu mutation (4, đã revert — **3/4 đụng `src/main` hoặc migration**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Bỏ `.cors(...)` khỏi `SecurityConfig` (**`src/main`**) | **4/6** `CorsConfigurationTest` | 2 case sống sót đúng như dự đoán: origin lạ vẫn 403 (vì lý do khác), và test wildcard là unit thuần ⇒ **phải có case origin được whitelist** mới khoá được |
| 2 | `validate()` không còn từ chối `"*"` (**`src/main`**) | **1** — `wildcardOrigin_isRejectedWithAnExplanation` | Quyết định fail-fast là test-backed, không phải văn xuôi |
| 3 | Bỏ 1 dòng grant của MANAGER khỏi `V41` (**migration**) | **2** — checklist dương **và** test admin-only | Cặp test mạnh hơn dự tính: quyền OPERATOR **không** có mà MANAGER mất thì thành admin-only ⇒ cả hai đỏ |
| 4 | Cấp `PERM_QUALITY_DISPOSITION` cho OPERATOR trong `V41` (**migration**) | **1** — `...keepsOperatorOutOfApprovalAndConfigurationPermissions` | Separation of duties không thể chứng minh bằng danh sách dương |

**Kiểm chứng qua HTTP thật** (`mvn -o spring-boot:run`, port 8081 vì 8080 đang có instance khác):

| Probe | Kết quả |
|---|---|
| Preflight `OPTIONS` từ `http://localhost:5173` | **200** + `Allow-Origin`/`Allow-Credentials`/`Expose-Headers` đầy đủ |
| Preflight từ `http://evil.example.com` | **403**, không có `Allow-Origin` |
| `manager.a` → `GET /plants/{A}/work-orders` | **200** (trước `V41`: 403) |
| `manager.a` → `GET /plants/{B}/work-orders` | **403 `PERMISSION_DENIED`** ⇒ isolation đúng |
| `operator.a` → `POST /plants/{A}/work-orders` | **403** (thiếu `PERM_WORK_ORDER_MANAGE`) |
| `manager.a` → cùng request đó | **404 `ENTITY_NOT_FOUND`** ⇒ đã qua authz, chết ở item giả |

> ⚠️ **Probe đầu tiên của tôi cho `operator.a` trả 400 `VALIDATION_ERROR`, không phải 403** — `@Valid`
> trên body chạy **trước** method security. Muốn probe authz thì body **phải hợp lệ**, nếu không sẽ kết
> luận sai là "đã chặn".

**Breaking changes — wire: KHÔNG có.** **Hành vi:** MANAGER/OPERATOR nay **làm được** việc tài liệu đã
hứa (nới quyền có chủ đích, quyết định của user); browser từ origin ngoài danh sách nay bị chặn tường
minh thay vì "chặn vì không có cấu hình nào".

---

### 0.26 C2-3 – UOM Master (ĐÃ HOÀN THÀNH 2026-08-04)

Phase thứ ba của track `C2-*` (`FRONTEND_ALIGNMENT_ROADMAP.md §8`), theo đúng
`NEXT_PHASE_PLAN.md` đã viết cho phase này. Migration **`V42`** (schema) + **`V43`** (seed permission).
Module mới `module/uom/` — 7 endpoint `/api/v1/uoms`. **Không** breaking change wire.

| Quyết định | Nội dung |
|---|---|
| Global, không company-scope | `uoms` **không có** `company_id` — khác mọi master data khác trong repo. Theo đúng dữ kiện FE liệt kê (`GET /uoms` không lọc theo company) |
| Lifecycle | `POST .../activate` + `POST .../deactivate` (không dùng `DELETE` như BOM/Routing) — đi theo đúng 2 verb FE liệt kê tường minh vì đây là greenfield API, tránh lặp lại vòng "FE báo thiếu lần 2" của `C2-0` |
| `code` immutable | Không validate runtime — `UomUpdateRequest` **không có field `code`**, compile-time guarantee |
| Permission | `PERM_UOM_READ` (ADMIN+MANAGER+OPERATOR), `PERM_UOM_MANAGE` (ADMIN+MANAGER) — theo đúng khuôn `V31` (routing) |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Phát hiện khi kiểm chứng qua HTTP thật, không phải bug của phase này:** `PermissionGuard.hasPermission(...)`
   (cơ chế gác `PERM_UOM_*`, giống `PERM_ORG_READ`/`_MANAGE` của `OrganizationService.listCompanies`/
   `createCompany`) chỉ đọc assignment có **`scopeType = GLOBAL`**. Account seed `manager.a`/`operator.a`
   của `C2-5` chỉ có scope `PLANT` ⇒ **403 trên mọi endpoint UOM**, kể cả `GET /uoms` dù `V43` đã cấp
   `PERM_UOM_READ` cho `OPERATOR`. Đã xác nhận **không riêng UOM** — `manager.a` gọi `GET /companies`
   (cùng cơ chế) cũng 403. Đây là đặc điểm **có sẵn toàn repo**, không phải lỗi `C2-3`. Chi tiết + hệ quả
   cho việc test sau này: `module/uom/CLAUDE.md`.
2. `items.unit` **hoàn toàn không đụng** — vẫn `String` tự do, đọc ở 13 call site/5 mapper. Nối
   `items.uom_id` là phase riêng (ngoài phạm vi `C2-3`, xem "KHÔNG làm gì" đã ghi trong kế hoạch phase).
3. Validate "không deactivate khi đang tham chiếu" (spec §3.1) **cố ý là no-op hiện tại** — chưa bảng
   nào FK tới `uoms`. Đừng đọc code kiểm tra chỗ đó rồi tưởng là dead code; nó chờ `items.uom_id`.
4. `AuditAction` thêm 4 constant: `UOM_CREATED`/`UOM_UPDATED`/`UOM_ACTIVATED`/`UOM_DEACTIVATED`
   (rule "Create/update/deactivate master data" bắt buộc audit, `common/audit/CLAUDE.md`).

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main` hoặc migration**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Đổi `PERM_UOM_MANAGE` → chuỗi rác trong `@PreAuthorize` của `create` (**`src/main`**) | **2** — `UomMethodSecurityTest.create_deniedWhenUomManageMissing` + `.create_allowedWhenUomManagePresent` | Test pin đúng chuỗi permission, không phải mock trả `false` mặc định trùng hợp |
| 2 | Đổi mã lỗi trùng `code` từ `RESOURCE_ALREADY_EXISTS` sang `INVALID_INPUT` (**`src/main`**) | **1** — `UomServiceTest.create_duplicateCode_throwsBeforeSaving` | Bám `ErrorCode`, không bám status — cả hai khác status thật (409 vs 400) nên cũng bắt được qua HTTP nếu có test đó |
| 3 | Bỏ `cast(:keyword as string)` khỏi `UomRepository.search` (**`src/main`**) | **2** — `UomRepositoryIT.search_withoutAKeywordReturnsEveryUom` + `.search_withoutAKeywordStillHonoursTheStatusFilter` | 🔴 **Xác nhận unit test (mock repository) không bắt được** — chạy `UomServiceTest`+`UomMethodSecurityTest` dưới mutation này vẫn xanh 15/15. Đúng cách `lower(bytea)` đã hai lần bắt được ai đó trong ngày (`§0.24`), lần thứ ba là tự bắt trước khi ai báo |
| 4 | Bỏ grant `PERM_UOM_READ` cho `OPERATOR` khỏi `V43` (**migration**) | **1** — `FlywayMigrationIT.migrate_v43_grantsUomPermissionsToTheDocumentedRoles` | Test mới, theo đúng khuôn `migrate_v41_*` của `C2-5` — ma trận grant chỉ DB thật trả lời được |

> 🔴 Mutation #3 là kết quả giá trị nhất: **plan của chính phase này** viết "có thể không cần `*IT`" rồi
> tự ghi điều kiện dự phòng ("nếu không chắc, thêm 1 case") — chạy thử xác nhận unit test **không** bắt
> được, nên `UomRepositoryIT` (**class IT thứ 12**) được thêm đúng theo điều kiện dự phòng đó, không bỏ qua.

**Kiểm chứng qua HTTP thật** (`mvn -o spring-boot:run`, port 8081): tạo UOM (`kg` → `KG`, 201) · list
**không** `search` (200, không 500 — đúng bẫy `§0.24`) · list có `search` · trùng `code` (409) ·
`deactivate`→`activate` round-trip · `admin` tạo được (201) · `operator.a`/`manager.a` 403 (do giới hạn
`GLOBAL` scope ở mục 1, không phải do V43 sai — đã đối chiếu với `listCompanies` để xác nhận).

**Breaking changes — wire: KHÔNG có.** Toàn bộ additive: bảng mới, endpoint mới, 2 permission mới, 4
audit action mới.

---

### 0.27 C2-4 – SO PATCH · Role/Scope Lifecycle · Over-BOM Contract · Time Variance (ĐÃ HOÀN THÀNH 2026-08-05)

Phase thứ tư của track `C2-*`. **Không migration.** Bốn phần độc lập về code, gộp một phase vì đều là
"hoàn thiện contract đang có". Bất biến `B86` (`module/workorder/CLAUDE.md`), `B87`
(`module/sales/CLAUDE.md`), `B88` (`module/organization/CLAUDE.md`).

| Phần | Endpoint | Quyền |
|---|---|---|
| A | `PATCH /sales-orders/{id}` | `PERM_SALES_ORDER_MANAGE` (tái dùng guard `confirm`/`cancel`) |
| B | `GET`/`PATCH /access/roles/{id}`, `.../activate`, `.../deactivate`; cùng bộ cho `/access/scopes/{id}`; `GET /access/assignments?userId=&roleId=&scopeId=` | `PERM_ACCESS_MANAGE` (không permission mới) |
| C | *(không endpoint)* — chốt tài liệu | — |
| D | `GET /work-orders/{id}/variance` (field mới `timeVariance`) | `PERM_WORK_ORDER_VARIANCE_READ` (không đổi) |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Defect thật, không phải giả định — bắt được lúc `mvn verify`, không phải lúc viết Part D.**
   Kế hoạch gốc định nới `WorkOrderRepository.findWithDetailsByWorkOrderId` thêm `"operations"` vào
   `@EntityGraph` đã có `"componentLines"`. **Cả hai đều là `List` ("bag")** ⇒ Hibernate ném
   `MultipleBagFetchException` ngay từ câu query đầu tiên — sập **toàn bộ** endpoint variance (và kéo
   theo `ProductionFlowE2EIT`, vì nó gọi cùng entity graph), không riêng phần time. `mvn test` (mock
   repository, `@EntityGraph` không chạy) **không** thấy được; chỉ `mvn -o verify` (Testcontainers
   thật) bắt được. Sửa: `WorkOrderVarianceService` đọc operations qua **một query riêng**
   (`WorkOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc` — method đã có sẵn từ
   trước, không phải thêm mới), không đụng `@EntityGraph` hiện có. 1 query thêm, không phải 1/dòng
   (`C14`). **Bài học:** trước khi thêm bất kỳ path thứ hai vào một `@EntityGraph` đã có collection
   `List`, kiểm xem path đó cũng là `List` không.
2. `plannedMinutes = Σ(setupMinutes + runMinutesPerUnit × plannedQuantity)` trên **mọi**
   `WorkOrderOperation` snapshot; `actualMinutes = Σ Duration.between(actualStartedAt, actualEndedAt)`
   trên **mọi** `ProductionExecution`, **loại** execution còn dở dang (`actualEndedAt == null`) — thời
   gian dở dang không phải thời gian đã tiêu tốn xong.
3. `PATCH /sales-orders/{id}`: `expectedVersion` so **tường minh** với `SalesOrder.getVersion()` ở
   tầng service, **trước khi** mutate bất cứ field nào (rule `C9`) — JPA không tự phát hiện được vì
   entity vừa load lại, không có gì "cũ" để so ở đây. Validate `dueDate ≥ orderDate` chạy lại cho
   **toàn bộ** dòng liên quan (dòng mới nếu `lines` được thay, dòng cũ nếu chỉ đổi `orderDate`) —
   đổi `orderDate` một mình vẫn có thể làm dòng cũ (không đổi) trở nên invalid.
4. Role `is_system = true` (ADMIN/MANAGER/OPERATOR) **không bao giờ** deactivate được, kể cả bởi
   `admin` — chặn ở **`deactivate`** là đủ; `activate` không cần chặn thêm vì role hệ thống không có
   đường vào `INACTIVE`. `PATCH` role/scope chỉ nhận `name`/`description` — `code`/`is_system`/
   `scopeType` immutable (compile-time: field không có trong request record).
5. `UserRoleAssignmentRepository.search` lọc 3 tham số **trực tiếp trên cột `UUID` phẳng** của
   `UserRoleAssignment` (`userId`/`roleId`/`scopeId`) — **không** cần join `Role`/`AccessScope`/`User`,
   khác các query B32 ở cùng file phải join để biết permission nào được cấp. Vì vậy **không** cần
   `*IT` cho query này (không phải JPQL phức tạp, `R7` không áp dụng) — chỉ unit test verify đúng
   tham số được forward.
6. `docs/capstone2-api-gap-response.md §5` câu 4 (over-BOM) đã trả lời: phương án (1) **là** hành vi
   code hiện tại (`B15`), không code gì thêm cho Part C.
7. `C2-4` **không** thêm permission mới ⇒ `docs/roles-and-permissions.md` không thêm quyền, chỉ ghi
   chú 3 dòng ở mục ADMIN/Sales nói rõ endpoint mới tái dùng quyền có sẵn.

**Nghiệm thu mutation (4, đã revert — 4/4 đụng `src/main`):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Bỏ vế `runMinutesPerUnit × plannedQuantity` khỏi `plannedMinutesFor` | **2** — `getVariance_timeVariance_sumsOperationsAndExecutionsExcludingInProgress`, `getVariance_noExecutions_actualMinutesIsZero` | Cả hai case có `runMinutesPerUnit > 0` đều lộ ra số planned sai |
| 2 | Đảo chiều `Duration.between(actualEndedAt, actualStartedAt)` (ngược) | **1** — `getVariance_timeVariance_sumsOperationsAndExecutionsExcludingInProgress` (`50` → `-50`) | Assert đúng dấu dương của tổng, không chỉ độ lớn |
| 3 | Bỏ `if (role.isSystem())` khỏi `deactivateRole` | **1** — `deactivateRole_systemRole_throwsOperationNotAllowedBeforeSaving` | Guard bảo vệ role hệ thống là test-backed, không phải văn xuôi |
| 4 | Bỏ check `expectedVersion` khỏi `SalesOrderService.update` | **1** — `update_staleExpectedVersion_throwsConcurrentModificationBeforeSaving` | Optimistic-lock tường minh ở tầng service có test khoá, không chỉ dựa vào `@Version` của JPA |

**Nghiệm thu:** `mvn -o clean verify` — **661 case unit + 78 case IT / 12 class IT, failures = 0,
errors = 0** (baseline trước phase: 617 unit + 78 IT / 12 class — không migration nên IT không đổi số
ròng: Part D thêm 1 case `WorkOrderRepositoryIT` cho N+1 rồi phải bỏ khi đổi thiết kế ở hệ quả #1).

**Breaking changes — wire: KHÔNG có.** Toàn bộ additive: 1 endpoint `PATCH` mới, 9 endpoint Role/Scope
mới, 1 field mới (`timeVariance`) trên `WorkOrderVarianceResponse`. **Java positional:**
`WorkOrderVarianceResponse` +1 component (cuối cùng), `WorkOrderVarianceService` constructor +2 tham
số (`ProductionExecutionRepository`, `WorkOrderOperationRepository`).

---

### 0.28 C2-6 – Work Center Entity + CRUD + Routing FK (ĐÃ HOÀN THÀNH 2026-08-05)

Phase thứ năm của track `C2-*`, mở đầu cluster `P4` (Work Center/Shift/Calendar/Capacity).
Migration **`V44`** (bảng `work_centers` + cột `routing_operations.work_center_id`) + **`V45`**
(seed permission). Module mới `module/workcenter/` — 7 endpoint. **Không** đổi request/response DTO
ngoài Routing (xem Breaking changes).

| Phần | Nội dung |
|---|---|
| A | `WorkCenter` **per-plant** (không company-level, khác `UOM` global của `C2-3`) — 7 endpoint, `PERM_WORK_CENTER_READ`/`_MANAGE` theo khuôn Routing (không theo khuôn `PERM_ORG_*`) |
| B | `RoutingOperation.workCenterCode` (free text) → `RoutingOperation.workCenter` (FK `WorkCenter`, **nullable, không backfill**) |

**Ba quyết định đã chốt với user trước khi viết phase** (đầy đủ ở `NEXT_PHASE_PLAN.md` lịch sử
`C2-6` §1): Work Center per-plant; `RoutingOperation` khoá vào 1 plant qua Work Center (bất biến
`B_wc2`); chưa thêm Calendar/Shift reference (chờ `C2-7`); `DELETE` + `POST .../activate|deactivate`
cùng tồn tại, `DELETE`/`deactivate` gọi **cùng** service method.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Defect thật bắt được lúc `mvn verify`, không phải lúc viết code.** `V30` tạo
   `routing_operations.work_center_code` là `NOT NULL`. Bản nháp đầu của `V44` chỉ `ADD COLUMN
   work_center_id` mà quên `ALTER COLUMN work_center_code DROP NOT NULL` — vì entity không còn map
   cột đó nữa, **mọi** insert `RoutingOperation` mới (kể cả `ProductionFlowE2EIT` seed dữ liệu)
   chết với `null value in column "work_center_code" violates not-null constraint`. `mvn test`
   (mock repository) không thấy được; chỉ Testcontainers thật (`FlywayMigrationIT`,
   `ProductionFlowE2EIT`) bắt được — đúng kiểu lỗ hổng `§0.24`/`§0.26` đã ghi hai lần trước đó,
   nay là lần thứ ba trong repo.
2. **`FlywayMigrationIT` là nơi duy nhất kiểm được ma trận grant `V45`** — `migrate_v45_grantsWorkCenterPermissionsToTheDocumentedRoles`, cùng khuôn `migrate_v43_*`/`migrate_v41_*`. `PermissionCatalogTest` chỉ chứng minh permission tồn tại, không chứng minh được cấp cho role nào (bài học lặp lại từ `C2-5`).
3. **B_wc2 validate ở `RoutingService.create()`, KHÔNG ở `WorkCenterService`.** Resolve toàn bộ
   `workCenterId` của request trước, kiểm tập `plantId` phân biệt > 1 mới build entity (rule C9).
   Nghiệm thu mutation: bỏ dòng gọi `ensureOperationsShareOnePlant(...)` ⇒ đúng
   **1** case đỏ (`RoutingServiceTest.create_operationsAcrossTwoPlants_throwsOperationNotAllowed`),
   các case khác (cùng plant) vẫn xanh — xác nhận check không nới quá tay.
4. **`WorkOrderOperation.workCenterCode` (String) không đổi** — vẫn là snapshot bất biến
   (`B56`/`B49`). `WorkOrderService.snapshotRouting` đổi đúng 1 dòng:
   `operation.getWorkCenter().getCode()` thay vì `operation.getWorkCenterCode()` (đã xoá field đó
   khỏi `RoutingOperation`).
5. **`RoutingOperationResponse` giữ cả `workCenterId` lẫn `workCenterCode`** — code nay resolve qua
   `operation.getWorkCenter()` (join), không phải cột riêng; null-safe cho dòng lịch sử chưa có
   Work Center (`workCenter == null` ⇒ cả hai field trả `null`).
6. **Mọi test dựng `RoutingOperation`/`RoutingOperationRequest` trực tiếp phải sửa** — không chỉ
   file mới viết mà cả `RoutingServiceTest`, `RoutingControllerTest`, `RoutingMethodSecurityTest`,
   `WorkOrderServiceTest`, `ProductionFlowE2EIT` đều dùng `workCenterCode`/`WC-01` (String) trước
   phase này; tất cả **sửa** theo `R10`, không xoá.
7. **`C2-6` KHÔNG thêm `*IT` mới** (vẫn 12 class IT) — đúng nhận định trong kế hoạch: query của
   `WorkCenterRepository.search` chỉ là `=`/`is null` đơn giản, không phải `concat`/`like` phức tạp
   nên mock repository là đủ (`R7` không áp dụng).

**Nghiệm thu:** `mvn -o clean verify` — **690 case unit + 79 case IT / 12 class IT, failures = 0,
errors = 0** (baseline trước phase: 661 unit + 78 IT / 12 class — `+29` unit từ `WorkCenterServiceTest`
(9) + `WorkCenterMethodSecurityTest` (8) + `WorkCenterControllerTest` (10) + 2 case mới trong
`RoutingServiceTest`; `+1` IT từ `FlywayMigrationIT.migrate_v45_*`, không thêm class IT mới).

**Breaking changes — wire:**
- `RoutingOperationRequest.workCenterCode` (String) → `workCenterId` (UUID). Client gửi text tự do
  cho field này **sẽ hỏng** — phải tạo Work Center trước rồi lấy `workCenterId`.
- `RoutingOperationResponse` thêm `workCenterId` (additive).
- 7 endpoint Work Center: additive.

**Java positional:** `RoutingOperation` field `workCenterCode` (String) → `workCenter` (`WorkCenter`);
`RoutingService` constructor +1 tham số (`WorkCenterLookupService`).

---

### 0.29 C2-7 – Shift + Work Calendar Entity + CRUD + Work Center FK (ĐÃ HOÀN THÀNH 2026-08-05)

Phase thứ sáu của track `C2-*`, phần thứ hai của cluster `P4` (Work Center/Shift/Calendar/Capacity),
nối tiếp `C2-6`. Migration **`V46`** (schema, gộp cả 3 phần A+B+C) + **`V47`** (seed permission).
Module mới `module/shift/` — 14 endpoint. **Không** breaking change wire ngoài field additive trên
Work Center.

| Phần | Nội dung |
|---|---|
| A | `Shift` **per-plant** — 1 interval (`startTime`/`endTime`, kiểu `TIME`) + `breaks[]`. 7 endpoint `/api/v1/plants/{plantId}/shifts` + `/api/v1/shifts/{id}`, `PERM_SHIFT_READ`/`_MANAGE` theo khuôn Work Center |
| B | `WorkCalendar` **per-plant** — lịch tuần (`weekday → shift`, một weekday nhận **nhiều** shift) + `WorkCalendarException` (`NON_WORKING`, một chiều). 7 endpoint `/api/v1/plants/{plantId}/work-calendars` + `/api/v1/work-calendars/{id}`, `PERM_WORK_CALENDAR_READ`/`_MANAGE` — permission **riêng** với Shift dù cùng module |
| C | `WorkCenter.workCalendarId` (FK tuỳ chọn, nullable) + bất biến `B_wc4` (cùng plant) |
| D | `WorkingWindowCalculator` + `WorkCalendarLookupService.computeWorkingWindows` — net working window, **internal only**, không endpoint public |

**Bốn quyết định đã chốt với user trước khi viết phase** (đầy đủ ở `NEXT_PHASE_PLAN.md` lịch sử
`C2-7` §1): Shift = 1 interval + `breaks[]`, không phải nhiều "work interval" con; `WorkCalendarException`
chỉ `NON_WORKING`, không có "override ca đặc biệt"; `WorkCenter` chỉ thêm đúng 1 cột `work_calendar_id`,
không có `shiftIds[]` riêng; net working window không có endpoint public ở phase này (chờ `C2-8`).

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`WorkCalendarRepository.findWithWeeklyShiftsByWorkCalendarId` cố ý KHÔNG fetch `exceptions`
   trong cùng `@EntityGraph`.** `weeklyShifts` và `exceptions` đều là `List` (bag) trên `WorkCalendar`
   ⇒ join-fetch cả hai cùng lúc ném `MultipleBagFetchException` — **đúng cái bẫy `C2-4` đã gặp** giữa
   `operations`/`componentLines` (`§0.27` hệ quả #1), lần thứ hai trong repo. Sửa: `exceptions` lazy-load
   trong cùng transaction (`calendar.getExceptions().size()` ngay sau khi fetch) thay vì fetch cùng lúc
   — đây là **một** aggregate detail, không phải report lặp theo dòng, nên rule C14 không áp dụng.
2. 🔴 **`ShiftTimeWindow.containsInterval` dùng phép đo circular, KHÔNG có nhánh if/else riêng cho ca
   qua đêm.** `durationMinutes(start, end)` luôn trả về số phút không âm, tự "wrap" qua nửa đêm khi
   `end` đứng trước `start`. Công thức này dùng chung cho **cả hai**: (a) một break có nằm trong shift
   không (`ShiftService`), và (b) shift chiếm bao nhiêu phút trong ngày (`WorkingWindowCalculator`,
   Part D). Sửa công thức ảnh hưởng cả hai nơi cùng lúc — xem `module/shift/CLAUDE.md` mục 2.
3. **Quy ước qui-thuộc-ngày cho ca qua đêm là hợp đồng cho `C2-8`, không phải chi tiết nội bộ.** Shift
   qua đêm (`endTime < startTime`) gán cho weekday nó **bắt đầu**; interval trả về có thể kết thúc ở
   ngày dương lịch **kế tiếp**. Ghi đủ chi tiết ở `module/shift/CLAUDE.md` mục 1 vì đó là chỗ `C2-8` sẽ
   đọc lại khi cộng dồn giờ làm cho Capacity Board.
4. **`WorkCalendarUpdateRequest.effectiveTo` và `WorkCenterUpdateRequest.workCalendarId` giữ quy ước
   "`null` = không đổi" dù cả hai đều nullable-có-ý-nghĩa** (không hết hạn / không gắn calendar) — hệ
   quả chấp nhận: không có đường "xoá về trống" hai field này qua `PATCH`, để nhất quán với mọi field
   khác trong repo thay vì thêm sentinel riêng cho hai chỗ này.
5. **`Shift`/`WorkCalendar` validate lẫn nhau bằng cách gọi thẳng `ShiftRepository`, không qua lookup
   service riêng** — khác `WorkCenter`↔`Routing` (hai module khác nhau, bắt buộc rule C7). `Shift` và
   `WorkCalendar` cùng module nên không có ranh giới cross-module cần bảo vệ thêm.
6. **`B_wc2` (Routing↔WorkCenter cùng plant) và `B_cal1` (WorkCalendar↔Shift cùng plant) và `B_wc4`
   (WorkCenter↔WorkCalendar cùng plant) là ba bất biến "cùng hình dạng" ở ba cặp entity khác nhau** —
   cả ba đều validate ở tầng service, **trước khi** build entity (rule C9), cả ba đều trả 422
   `OPERATION_NOT_ALLOWED` (input sai, không phải state machine).
7. **`C2-7` KHÔNG thêm permission cho ADMIN riêng** — `V47` cấp `PERM_SHIFT_*`/`PERM_WORK_CALENDAR_*`
   cho `ADMIN`+`MANAGER` (cả hai) và chỉ 2 quyền `_READ` cho `OPERATOR`, đúng khuôn `V45`/`V43`. Test
   ma trận grant ở `FlywayMigrationIT.migrate_v47_*` (3 case, DB thật) — `PermissionCatalogTest` không
   đủ để kiểm điều này (bài học lặp lại từ `C2-5`/`C2-3`/`C2-6`).

**Nghiệm thu:** `mvn -o clean verify` — **759 case unit + 80 case IT / 12 class IT, failures = 0,
errors = 0** (baseline trước phase: 690 unit + 79 IT / 12 class — `+69` unit từ `ShiftServiceTest`(12)/
`ShiftMethodSecurityTest`(8)/`ShiftControllerTest`(9)/`WorkCalendarServiceTest`(12)/
`WorkCalendarMethodSecurityTest`(8)/`WorkCalendarControllerTest`(9)/`WorkingWindowCalculatorTest`(7) +
4 case `B_wc4` mới trong `WorkCenterServiceTest`; `+1` IT từ `FlywayMigrationIT.migrate_v47_*`, không
thêm class IT mới — `ShiftRepository.search`/`WorkCalendarRepository.search` chỉ `=`/`is null` đơn
giản nên mock repository là đủ, đúng điều kiện dự phòng đã ghi trong kế hoạch phase).

**Breaking changes — wire: KHÔNG có** (thuần additive: 14 endpoint mới, 1 field mới `workCalendarId`
trên Work Center create/update/response, tuỳ chọn).

**Java positional:** `WorkCenterService` constructor +1 tham số (`WorkCalendarLookupService`);
`WorkCenterCreateRequest`/`UpdateRequest`/`Response` +1 component (`workCalendarId`, cuối record).
Test cũ dựng các record này positional đã **sửa** theo `R10`.

---

### 0.30 C2-8 – CRP Tĩnh + Capacity Board + Schedule Adjustment (ĐÃ HOÀN THÀNH 2026-08-05)

Phase thứ bảy của track `C2-*`, đóng nốt cluster `P4` (Work Center → Shift/Work Calendar →
Capacity) mà `C2-6`/`C2-7` đã dựng. Migration **`V48`** (schema) + **`V49`** (seed permission).
**Không** module mới — mọi thứ nằm trong `module/workorder` (query/write service mới cạnh
`WorkOrderVarianceService`) cộng **một** method mới trên `module/shift`'s `WorkCalendarLookupService`
+ `WorkingWindowCalculator` (rule C7 — `workorder` gọi vào, không tự làm lại toán lịch).

**Ba quyết định đã chốt với user trước khi viết kế hoạch chi tiết** (đầy đủ ở `NEXT_PHASE_PLAN.md`
lịch sử `C2-8` §1): lịch operation sinh **ở `release()`**, không phải lúc tạo WO hay lúc `plan()`;
nợ B (`predecessorOperationIds`/sequence-dependency validation) **tách `C2-8b`**, không làm ở đây;
`schedule-adjustments` **không bao giờ tự dời** operation khác — chỉ trả cờ warning/conflict.

| Phần | Nội dung |
|---|---|
| A | `WorkOrderOperation` thêm `workCenter` (FK, nullable), `plannedStartAt`/`plannedEndAt` (`Instant`, nullable), `scheduleAdjustmentReason`. `WorkOrderService.scheduleOperations` (gọi từ `release()`) forward-schedule tuần tự theo `sequence`, đi qua lịch làm việc của Work Center (nếu có) |
| B | `GET /api/v1/plants/{plantId}/capacity-board` — `CapacityBoardService` (mới, `module/workorder/service/query`), aggregate load/capacity/utilization theo `(workCenter, ngày local theo `Plant.timezone`)` |
| C | `POST /api/v1/work-orders/{id}/operations/{id}/schedule-adjustments` — `ScheduleAdjustmentService` (mới, `module/workorder/service`), ghi đè thủ công + cờ tư vấn |
| D | Permission `PERM_CAPACITY_READ`/`_MANAGE` (`V49`) — cùng tầng Work Center/Shift: ADMIN+MANAGER cả hai, OPERATOR chỉ `_READ` (gap doc §3.6: "Manager điều chỉnh") |

**Quyết định đảo ngược tiền lệ đã ghi — flag rõ để không đọc nhầm `module/workcenter/CLAUDE.md` cũ:**

`module/workcenter/CLAUDE.md` mục 4 (viết ở `C2-6`) từng nói `WorkOrderOperation.workCenterCode`
giữ nguyên `String`, **không** đổi thành FK, vì lúc đó chưa có consumer thật. `C2-8` **là** consumer
đó — Capacity Board phải join/filter theo Work Center và đọc `capacityUnits`/`workCalendar`, việc
free-text không làm được. `C2-8` thêm cột **thứ hai**, `work_center_id` (nullable, FK), **cạnh**
`work_center_code` (giữ nguyên, vẫn là display snapshot) — dòng lịch sử (routing operation không có
Work Center, `B_wc2`) mang `NULL` và đơn giản là vô hình với Capacity Board, cùng hình dạng "không
backfill" đã có ở `B_wc2`/`B77`. `module/workcenter/CLAUDE.md` mục 4/7 đã cập nhật để trỏ ngược lại
đây.

**Hệ quả cần nhớ khi code tiếp:**

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B89 | Lịch (`plannedStartAt`/`plannedEndAt`) sinh **đúng một lần**, ở `WorkOrderService.release()`, tuần tự theo `sequence`: operation sau bắt đầu khi operation trước kết thúc (hoặc `workOrder.plannedStartAt`/`now()` cho operation đầu). Operation có Work Center gắn `workCalendar` thì gọi `WorkCalendarLookupService.computeEndInstant` (bỏ qua giờ không làm việc); không có calendar thì cộng phút liên tục. Đây là lịch **infinite-capacity** — không biết WO khác có đang chiếm cùng Work Center hay không, đó là việc của Capacity Board (read riêng), cố ý tách hai mối quan tâm theo đúng định nghĩa CRP tĩnh | `WorkOrderServiceTest.release_schedulesOperationsSequentially_continuousTimeWhenWorkCenterHasNoCalendar`, `.release_schedulesOperations_delegatesToTheWorkCenterCalendarWhenPresent`, `.release_withoutAnExplicitPlannedStartAt_anchorsTheFirstOperationAtNow` |
| B90 | `schedule-adjustments` chỉ chặn cứng khi **version lệch** (409 `CONCURRENT_MODIFICATION`) hoặc **thời gian vô nghĩa** (`plannedEndAt <= plannedStartAt`, 400). Xung đột **sequence** (đè lên cửa sổ của operation liền trước/sau), **calendar** (0 phút làm việc ngày đó), **capacity** (load vượt capacity) đều **không chặn** — ghi vào response dưới dạng cờ tư vấn (`sequenceConflict`/`calendarConflict`/`capacityOverload`) để manager tự xử lý. Backend **không bao giờ** tự dời operation khác | `ScheduleAdjustmentServiceTest.adjust_staleExpectedVersion_throwsConcurrentModificationBeforeAnyWrite`, `.adjust_endNotAfterStart_throwsInvalidInputBeforeAnyWrite`, `.adjust_cleanWindow_persistsAndReportsNoSequenceConflict`, `.adjust_windowStartingBeforePredecessorEnds_stillPersistsButFlagsSequenceConflict` |

1. 🔴 **Ranh giới local-time ↔ Instant nằm ở `Plant.timezone`, một field đã tồn tại từ lâu nhưng
   trước `C2-8` chưa từng được nối vào toán ngày tháng thật nào.** `module/shift` hoàn toàn "naive
   local time" (`LocalDateTime`, không zone) — `WorkingWindowCalculator.advance` nhận/trả
   `LocalDateTime`; `WorkCalendarLookupService.computeEndInstant` là **nơi duy nhất** chuyển đổi
   qua lại `Instant` bằng `ZoneId.of(plant.getTimezone())`. Việc gom nhóm load theo "ngày" trong
   `WorkOrderOperationRepository` (JPQL) dùng đúng cùng phép quy đổi
   (`function('timezone', plant.timezone, plannedStartAt)`, Postgres `timezone(zone, ts)` — tương
   đương `AT TIME ZONE`) để hai phía Java/SQL không lệch ngày với nhau. `WorkOrderOperationRepositoryIT`
   dựng cố ý một mốc mà ngày UTC và ngày local lệch nhau (`America/New_York`, tháng 1) để chứng minh
   chiều quy đổi đúng — đây là construct JPQL `function(...)` **đầu tiên** trong repo (chưa có tiền lệ
   native query nào trước đó), nên đừng copy công thức này sang chỗ khác mà không kiểm lại bằng `*IT`
   thật (rule R7, bài học `lower(bytea)` `§0.24`).
2. 🔴 **"Existing load" luôn tính trên `{RELEASED, IN_PROGRESS, COMPLETED}`, bất kể tham số `status`
   filter của board đang lọc gì** — filter chỉ thu hẹp **dòng hiển thị**, không thu hẹp mẫu số của
   utilization. Nếu không tách hai khái niệm này, hai lượt gọi board với filter khác nhau sẽ báo hai
   con số utilization khác nhau cho cùng một Work Center/ngày — tự mâu thuẫn.
3. **`dayCapacityMinutes`/`utilizationPercent` là `null` khi Work Center không có `workCalendar`,
   không phải `0`.** Không có calendar nghĩa là "không biết", không phải "không có sức chứa" —
   `overload` khi đó luôn `false` (không đủ dữ liệu để khẳng định quá tải).
4. **Ngày quy-thuộc của một operation là ngày `plannedStartAt` rơi vào (theo local time), toàn bộ
   thời lượng gán hết cho ngày đó** — không chia tỷ lệ theo số ngày operation trải dài qua. Đây là
   đơn giản hoá có chủ đích của "CRP tĩnh" (không phải bug), cùng quy ước "gán cho ngày ca bắt đầu"
   `module/shift/CLAUDE.md` mục 1 đã dùng cho ca qua đêm.
5. **`ScheduleAdjustmentService` tái dùng `workOrderPermissionGuard` có sẵn** (không phải component
   permission-guard riêng) — nó là guard tổng quát khoá theo chuỗi permission code truyền vào, đã
   dùng cho mọi action của `WorkOrderService`. `CapacityBoardService.getBoard` cũng tái dùng
   `permissionGuard.hasResourceAccess(..., 'PLANT', ...)` có sẵn. Không thêm bean guard mới.
6. **`CapacityBoardService.buildDayContext`/`attributedDay`/`dayCapacityMinutes`/`utilizationPercent`/
   `key` là `public`** dù chỉ có một caller khác (`ScheduleAdjustmentService`, khác package) — hai
   endpoint phải báo cùng một con số "quá tải" cho cùng một `(workCenter, ngày)`, nên chia sẻ đúng
   một implementation thay vì hai công thức có thể trôi lệch nhau.
7. **`WorkOrderOperationResponse` thêm field** (`workCenterId`, `plannedStartAt`, `plannedEndAt`,
   `version`) — additive trên wire, nhưng **positional trong Java** (record).

**Không làm gì (có chủ đích, không phải thiếu sót):**
- Nợ B (`predecessorOperationIds`, validate thứ tự operation) — chờ `C2-8b`, xem quyết định #2.
- Không có bảng lưu "kế hoạch capacity" — mọi con số tính live từ `WorkOrderOperation` đã persist,
  giống cách `MrpCalculationService`/`WorkOrderVarianceService` không lưu kết quả trung gian.
- Không nghiệm thu bằng mutation testing (khác nhiều phase `C2-*` trước) — thời lượng phase không
  cho phép; bù lại bằng bộ test rộng hơn ở mọi tầng (unit, method-security, controller, repository
  `*IT`, migration `*IT`) cộng smoke test HTTP thật qua `mvn -o spring-boot:run` trên Postgres thật
  (không phải Testcontainer) sau khi `mvn -o clean verify` xanh. Nếu phase sau muốn nghiệm thu mutation
  cho `C2-8`, B89/B90 là hai bất biến nên nhắm tới trước.

**Nghiệm thu:** `mvn -o clean verify` — **791 case unit + 87 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 759 unit + 80 IT / 12 class — xem hàng "Baseline test" §0.1 để
biết đúng test nào cộng vào đâu). Smoke test HTTP thật (`mvn -o spring-boot:run`, Postgres +
Redis qua `docker-compose up -d`, không phải Testcontainer): login `admin` → JWT mang đúng
`PERM_CAPACITY_READ`/`PERM_CAPACITY_MANAGE` (xác nhận `V49` seed đúng) → tạo company/plant/work
center qua HTTP → `GET .../capacity-board` trả `200` đúng envelope `PageResult` rỗng → `POST
.../schedule-adjustments` trên work order không tồn tại trả **403** (không phải 404) — đúng hành vi
sẵn có của `WorkOrderPermissionGuard.hasWorkOrderAccess` (aggregate không tồn tại ⇒ `orElse(false)`
⇒ từ chối trước khi service kịp trả `RESOURCE_NOT_FOUND`), nhất quán với mọi action khác của
`WorkOrderService` — không phải bug của phase này.

**Breaking changes — wire: KHÔNG có** (thuần additive: 2 endpoint mới, 4 field mới trên
`WorkOrderOperationResponse`). **Java positional:** `WorkOrderService` constructor +1 tham số
(`WorkCalendarLookupService`, trước `mapper`); `WorkOrderOperationResponse` +3 component;
`WorkOrderOperation.builder()` +4 field (không phá call site cũ, Lombok builder). Test cũ dựng
`WorkOrderService`/`WorkOrderMethodSecurityTest.Config` đã **sửa** theo `R10`.

---

### 0.31 P3 – Costing Engine (ĐÃ HOÀN THÀNH 2026-08-05)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P3, "khoảng trống lớn nhất về lý thuyết" — trước phase
này repo **không có cột chi phí nào** ở bất kỳ đâu, `Item` xác nhận không field cost, đọc trực tiếp
`WorkOrderVarianceResponse` xác nhận thuần số lượng, không có `$` nào). Module mới `module/costing`.
Migration **`V50`** (schema: `item_standard_costs` + `work_order_cost_accumulators`) + **`V51`**
(seed `PERM_COSTING_READ`/`_MANAGE`).

**Hai quyết định chốt với user trước khi viết kế hoạch chi tiết:**
1. **`laborCost`/`overheadCost` nhập tay theo rate cố định** trên `ItemStandardCost` — không tính từ
   `WorkOrderOperation.runMinutesPerUnit` × rate/phút. Đơn giản hơn, không khoá costing vào dữ liệu
   routing/work-center (`C2-6`/`C2-7`), ship được ngay.
2. **Không làm Material Price Variance, chỉ Material Usage Variance.** `stock_movements` không có cột
   giá (grep xác nhận), và dữ liệu giá duy nhất trong hệ thống (`PurchaseOrderLine.unitPrice`) tách
   rời khỏi ledger xuất kho — tính "giá thực trả cho vật tư đã xuất cho WO này" đòi một lớp
   actual-costing (FIFO/weighted-average) chưa tồn tại và không ai yêu cầu.

| Phần | Nội dung |
|---|---|
| A | `ItemStandardCost` — upsert (`PUT`, không phải full CRUD, cùng khuôn `ItemWarehouseSettingController.upsert`), company-scoped (như `BomHeader`/`RoutingHeader`, không per-plant như `WorkCenter`, không global như `Uom`). 3 endpoint: `PUT`/`GET .../items/{itemId}/standard-cost`, `GET .../items/standard-costs` (list) |
| B | `CostingService.calculateStandardCost` — đệ quy giống `MrpCalculationService.expandChildren`, cycle guard `LinkedHashSet<UUID>` defense-in-depth (B8 lẽ ra đã chặn BOM vòng lặp `ACTIVE`) |
| C | `WorkOrderCostAccumulator` (`module/workorder`) — 2 hook: `MaterialIssueService.postNew` (material, bên trong guard chống double-count có sẵn), `ProductionExecutionService.reportNew` (labor+overhead, chỉ khi `good > 0`) |
| D | `GET /work-orders/{id}/variance` mở rộng: `materialLines[].usageVarianceCost`, khối `costVariance` mới (`standard*`/`actual*`/`totalCostVariance`) |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`ItemStandardCostLookupService` có BA method, mỗi cái phục vụ đúng một việc, đừng gộp:**
   `findStandardUnitCost` (cost fully-loaded, roll-up — dùng cho component bị issue và cho
   `usageVarianceCost`), `findLaborOverheadCost` (rate thẳng, không roll-up — chỉ dùng cho product
   item của chính WO), `findStandardCostBreakdown` (tách 3 thành phần — dùng cho baseline
   `costVariance`). Chi tiết đầy đủ: `module/costing/CLAUDE.md` mục 1.
2. 🔴 **Cost của một component bị issue là cost fully-loaded của nó (`findStandardUnitCost`), KHÔNG
   phải chỉ field `materialCost` riêng của nó.** Nếu component tự nó là hàng lắp ráp (có BOM), cost
   fully-loaded đã gồm cả labor/overhead của chính nó — đây là cách absorbed-cost roll-up nhiều cấp
   hoạt động đúng. Dùng nhầm field `materialCost` thô sẽ undercount nghiêm trọng cho mọi component là
   sub-assembly (field đó thường bỏ trống/0 cho item có BOM, theo đúng thiết kế B91).
3. **`ItemStandardCostService.upsert` dùng MỘT action audit (`ITEM_STANDARD_COST_UPSERTED`), không
   tách CREATED/UPDATED.** `@Auditable` là AOP theo method, không hỗ trợ chọn action theo runtime
   state (create-vs-update) mà không tự đâm vào một trong hai vấn đề: self-invocation bypass proxy
   (tách 2 method) hoặc gọi `AuditLogService` thủ công qua `RequestContextHolder` (phá vỡ khả năng
   unit-test service bằng constructor + mock thuần — `@Auditable` là no-op an toàn ngoài Spring proxy,
   gọi thủ công thì không). Chọn phương án đơn giản nhất, đúng tinh thần `ItemWarehouseSettingService`
   (tiền lệ upsert gần nhất) vốn **không** audit gì cả.
4. **Permission dùng `hasResourceAccess(..., 'COMPANY', companyId)`, KHÔNG phải `hasPermission` như
   UOM.** Cố ý tránh giới hạn đã ghi ở `module/uom/CLAUDE.md`: `hasPermission` chỉ đọc assignment
   `scope_type = GLOBAL`, hiện chỉ `admin` có — `manager.a`/`operator.a` (seed `C2-5`, scope `PLANT`)
   sẽ bị 403 dù đã được cấp quyền. `hasResourceAccess(..., 'COMPANY', ...)` không dính bẫy đó.
5. **`PERM_COSTING_READ`/`_MANAGE`: ADMIN+MANAGER, KHÔNG có OPERATOR** — xác nhận bằng cách đọc
   `V17__seed_manufacturing_execution_permissions.sql`: `PERM_WORK_ORDER_VARIANCE_READ` (quyền mà
   cost figures của phase này gắn vào) đã luôn ADMIN+MANAGER only. Khác mọi permission `C2-*` gần đây
   (Work Center/Shift cho OPERATOR đọc). `FlywayMigrationIT.migrate_v51_*` là test **cấm** thuần —
   `PermissionCatalogTest` không chứng minh được cấp cho role nào (bài học lặp lại từ `C2-5`/`C2-3`/
   `C2-6`).
6. **`totalStandardCost` trên `ItemStandardCostResponse` tính lúc đọc (`CostingMapper.toResponse`
   gọi `CostingService`), không lưu cột, không cache.** Sửa một BOM line thì mọi `GET
   .../standard-cost` của item cha phản ánh ngay — đánh đổi là mỗi read tốn thêm truy vấn đệ quy,
   chấp nhận được vì đây không phải hot path.
7. **Không có `ItemStandardCostRepositoryIT`** — `search` chỉ so `=`/`is null` trên UUID, không có
   `concat`/`like` trên `String` nullable nên không dính bẫy "lower(bytea)" (`§0.24`). Cùng lý do
   `WorkCenterRepository.search` (`C2-6`) không có `*IT`.
8. **Không nghiệm thu bằng mutation testing** (khác nhiều phase `C2-*` gần đây) — bù lại bằng test
   rộng ở mọi tầng (unit, method-security, controller, `FlywayMigrationIT`) cộng smoke test HTTP thật
   qua `mvn -o spring-boot:run` trên Postgres/Redis thật: BOM 2 cấp (nguyên liệu 5đ/kg, lắp ráp
   labor=3+overhead=1, BOM 2kg/unit) → `standard-cost` trả `totalStandardCost=14` đúng công thức
   (2×5+3+1) → issue 4kg (kế hoạch 6kg) → `variance` trả `usageVarianceCost=-10` (-2×5) và
   `actualMaterialCost=20` (4×5) **trước khi** report sản lượng (accumulator row tồn tại độc lập với
   report) → report `good=2` → `actualLaborCost=6` (2×3), `actualOverheadCost=2` (2×1) — xác nhận cả
   hai hook chạy đúng qua ledger/DB thật, không chỉ qua mock.

**Nghiệm thu:** `mvn -o clean verify` — **821 case unit + 88 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 791 unit + 87 IT / 13 class — `+30` unit, `+1` IT, không thêm
class IT mới, xem hàng "Baseline test" §0.1 để biết đúng test nào cộng vào đâu).

**Breaking changes — wire: KHÔNG có** (thuần additive: 3 endpoint mới, `usageVarianceCost` +
`costVariance` mới trên `WorkOrderVarianceResponse`). **Java positional:**
`WorkOrderMaterialVarianceLineResponse` +1 component (cuối cùng); `WorkOrderVarianceResponse` +1
component (cuối cùng); `WorkOrderVarianceService` constructor +2 tham số
(`WorkOrderCostAccumulatorRepository`, `ItemStandardCostLookupService`); `MaterialIssueService`
constructor +1 tham số (`WorkOrderCostAccumulatorService`); `ProductionExecutionService` constructor
+1 tham số (`WorkOrderCostAccumulatorService`). Test cũ dựng các service/DTO này đã **sửa** theo
`R10`.

---

### 0.32 Concurrent Refresh-Token Race — Mở Rộng `D8` (ĐÃ HOÀN THÀNH 2026-08-05)

Đóng giới hạn "race double-submit" mà `D8a` (§0.22) chấp nhận có chủ đích. Nguồn: FE coi đây là điều
kiện nghiệm thu (`FRONTEND_ALIGNMENT_ROADMAP.md §8.0` mục 1, nay ✅). Không migration (thuần Redis),
không permission mới, không đổi `AuthResponse`.

**Vấn đề đã đóng** (trace từ code thật, `AuthService.refresh`): request A rotate xong (lưu mới →
mark used → xoá cũ) nhưng client không nhận được response → client retry với **chính** `(tokenId,
refreshToken)` cũ (chưa từng thấy cặp mới) → request B đọc `stored == null` → `wasRefreshTokenUsed`
trả **true** (A vừa mark) → B bị chẩn đoán nhầm thành kẻ trộm dùng lại token đã đánh cắp →
`TOKEN_REUSE_DETECTED` → **toàn bộ phiên hợp lệ của B bị force-logout oan**.

**Quyết định chốt với user (AskUserQuestion, không phải đoán):** hard lock, **không** grace window —
token cũ không bao giờ được chấp nhận lại làm credential lần hai; hai request race trên cùng
`tokenId` phải được nhận diện là **cùng một hành động logic**.

| Cơ chế | Ở đâu | TTL |
|---|---|---|
| Khoá tư vấn theo `tokenId` | `TokenStoreService.acquireRefreshLock` — `SET NX PX` qua `opsForValue().setIfAbsent`, **một lệnh Redis atomic**, không Lua | 2s |
| Breadcrumb kết quả rotate | `TokenStoreService.saveRotationResult`/`getRotationResult` — winner ghi ngay sau `deleteRefreshToken` hiện có | 5s |

`AuthService.refresh` gọi `acquireRefreshLock` **đầu tiên**, trước mọi validate — khoá được thì đi
tiếp ngay (đường nhanh, không đổi hiệu năng của tuyệt đại đa số request); khoá **không** được thì ngủ
một lần ~150ms rồi đi tiếp vào luồng validate y hệt cũ. Trong nhánh `stored == null` (đúng chỗ RTR
sống), **trước** `wasRefreshTokenUsed`: nếu breadcrumb tồn tại và cặp nó trỏ tới còn sống → trả về
**đúng cặp đó** (mint access token mới — vốn đã làm ở mọi lần refresh) thay vì ném
`TOKEN_REUSE_DETECTED`. Breadcrumb vắng mặt/hết hạn/cặp trỏ tới đã mất ⇒ rơi xuống
`wasRefreshTokenUsed` **y hệt trước phase này** — RTR thật (`B80`) không đổi một dòng.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Cần CẢ HAI cơ chế, không phải một.** Chỉ khoá không đủ: theo đúng trace ở trên, request A đã
   **hoàn tất hết** (kể cả xoá) trước khi B gọi `getRefreshToken` — B khoá được ngay (không có gì để
   chờ) và vẫn cần một câu trả lời cho `stored == null`; breadcrumb là thứ trả lời câu đó. Chỉ
   breadcrumb không đủ: hai request đọc `stored != null` ở CÙNG một khoảnh khắc (chưa ai kịp ghi gì)
   sẽ cả hai tự rotate độc lập, sinh 2 phiên hợp lệ từ 1 hành động; khoá cho request thứ hai một
   khoảng chờ ngắn để **thấy** kết quả của request thứ nhất thay vì tự làm lại.
2. 🔴 **Không unlock tường minh — dựa hoàn toàn vào TTL 2s.** Vùng găng (critical section) chỉ là vài
   lượt gọi Redis, hoàn tất trong vài mili-giây, nên compare-and-delete an toàn (Lua) không đáng công
   thêm cho lợi ích chỉ xuất hiện ở trường hợp đụng độ 3 request hiếm gặp.
3. **Tiện thể sửa một câu tài liệu sai đã tồn tại từ lâu, không phải bug mới.**
   `common/security/CLAUDE.md §4.8` và `.claude/rules/architecture-decisions.md` từng ghi bộ đếm
   brute-force "dùng Lua script Redis để atomic increment+expire" — đọc lại
   `TokenStoreService.incrementFailCount` thật thì đó là `opsForValue().increment` (tự atomic) rồi
   một `expire()` **riêng, không atomic**. Grep `RedisScript`/`DefaultRedisScript` toàn repo ra **0
   kết quả** — chưa từng có Lua script nào. Phase này **không** sửa hành vi đó (ngoài phạm vi, chưa ai
   yêu cầu), chỉ sửa mô tả cho khớp code — khoá mới ở đây dùng `SET NX PX` (atomic một lệnh), không
   phải Lua, nên tài liệu không còn có thể trích dẫn nhầm "đã có tiền lệ Lua" nữa.
4. **`respondWithExistingPair` KHÔNG rotate lại, KHÔNG gọi `saveRefreshToken`/`saveSessionStart`
   lần hai.** Nó chỉ đọc lại cặp mà winner đã tạo và mint một access token mới (JWT stateless, không
   lưu, mint mới ở **mọi** lần refresh vốn đã vậy — không phải cơ chế mới). `sessionCreatedAt` (B81)
   không cần xử lý gì thêm ở đường này vì winner đã carry-forward đúng nó rồi.
5. **Không audit `TOKEN_REUSE_DETECTED` cho nhánh breadcrumb-hit** — đây không phải sự cố bảo mật, chỉ
   là hệ thống nhận đúng một request trùng lặp của chính nó. Có log `DEBUG`, không thêm `AuditAction`
   mới (tránh phình enum cho một sự kiện không phải audit event).
6. **Test unit cho `AuthService.refresh` cần default `acquireRefreshLock` trả `true`** (lenient stub
   trong `@BeforeEach`) — nếu không, method-return mặc định của Mockito cho `boolean` là `false`,
   khiến **mọi** test `refresh_*` hiện có phải trả giá `Thread.sleep(150)` thật một cách vô ích. Chỉ
   test cố ý kiểm nhánh "khoá không acquire được" mới override thành `false`.

**Nghiệm thu:** `mvn -o clean verify` — **831 case unit + 88 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 821 unit + 88 IT / 13 class — `+10` unit, `+0` IT vì phase này
không đụng repository/JPQL nào, xem hàng "Baseline test" §0.1). Không smoke test HTTP thủ công — race
Redis không tái lập được đáng tin cậy qua curl tuần tự; bộ test unit mô phỏng đúng chuỗi sự kiện đã
trace ra là bằng chứng thật.

**Breaking changes — wire: KHÔNG có** (không đổi `AuthResponse`, không endpoint mới, không mã lỗi
mới — nhánh breadcrumb-hit trả **cùng hình dạng response 200** như rotate bình thường). **Java
positional: không có** — chỉ thêm method mới trên `TokenStoreService`, không đổi constructor nào.

---

### 0.33 P5 – Serial Number Tracking (ĐÃ HOÀN THÀNH 2026-08-06)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P5). Module mới nằm trong `module/inventory` (entity/
repository) + nối vào `module/workorder` (Material Issue, Production Receipt). **Không** đụng
`module/purchasing`, `module/sales`. Migration **`V52`**.

**Hai quyết định chốt với user trước khi viết kế hoạch chi tiết (cả hai qua `AskUserQuestion`):**
1. **`Item.lotTracked` và `Item.serialTracked` loại trừ nhau** — một item chỉ là lot-tracked,
   serial-tracked, hoặc không tracking gì, không bao giờ cả hai. Validate ở service **và** DB
   (`chk_items_tracking_exclusive`).
2. **Serial-tracked production output KHÔNG có HOLD chờ QC** — khác lot. Chấp nhận đánh đổi này để
   tránh phải thêm cột `serial_id` vào `stock_balances` và viết lại toàn bộ
   `StockBalanceRepository.aggregate*` (rủi ro/quy mô lớn nhất được xác định lúc research). Serial-
   tracked output vào thẳng tồn khả dụng ngay khi `approve`, giống hệt cách item không lot-tracked đã
   hoạt động từ `D5`.

**Thiết kế cốt lõi:** `SerialNumber` mirror `InventoryLot` gần như nguyên vẹn, nhưng **mọi movement
chạm một serial luôn có `quantity = 1`** — một serial là đúng một đơn vị vật lý, không phải bucket số
lượng tuỳ ý như lot. Nhờ vậy:
- Nhận/xuất N đơn vị serial-tracked = N `StockMovement` riêng (N lần gọi `receive`/`issue`), **không**
  cần schema/API mới cho "nhiều đơn vị trong một lần gọi".
- `MaterialIssueLineRequest` (+`serialId`) và `ProductionReceiptPostRequest` (+`serialNumber`) chỉ
  thêm field **optional** — hoàn toàn additive, **không phải breaking change** như bản phác thảo gốc
  của roadmap từng dự đoán (`List<serialId>` thay `quantity`). Xuất N đơn vị dùng **N dòng** trong
  mảng `lines[]` đã có sẵn của `MaterialIssuePostRequest`, không cần list field mới.
- `stock_balances`/`StockBalanceRepository` và `MaterialReservation` **không đổi** — item serial-
  tracked dùng chung "bucket không lot" với item không tracking, và reservation vẫn thuần số lượng;
  serial cụ thể chỉ được chọn ở bước issue.

**Bất biến mới:** `B96` (mutual exclusivity, `module/inventory/CLAUDE.md`), `B97` (quantity=1 +
lifecycle AVAILABLE→ISSUED, cùng file), `B98` (quyết định #2 ở trên — không HOLD,
`module/workorder/CLAUDE.md`), `B99` (quantity=1 ở tầng receipt/issue, cùng file).

**Nợ để lại có chủ đích:** Goods Receipt (`module/purchasing`) chưa nối serial — nhận nguyên liệu
serial-tracked qua PO sẽ nổ `SERIAL_REQUIRED` (lỗi thấy được, không silently sai). Ghi chú đầy đủ:
`module/purchasing/CLAUDE.md`.

**Nghiệm thu:** `mvn -o clean verify` — **850 case unit + 89 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 831 unit + 88 IT / 13 class — `+19` unit, `+1` IT, không thêm
class IT mới; chi tiết ở hàng "Baseline test" §0.1). Không nghiệm thu mutation (khác một số phase
`C2-*`/`P3` gần đây) — bù lại bằng test rộng ở service (Item/InventoryMovementService/
MaterialIssueService/ProductionReceiptService) + `FlywayMigrationIT` cho CHECK constraint.

**Breaking changes — wire: KHÔNG có** (thuần additive: `ItemCreateRequest`/`ItemResponse` +
`serialTracked`, `MaterialIssueLineRequest`/`Response` + `serialId`/`serialNumber`,
`ProductionReceiptPostRequest`/`Response` + `serialNumber`/`serialId`, `TrackingMethod` +
`SERIAL_TRACKED`, 2 mã lỗi mới `SERIAL_REQUIRED`/`SERIAL_NOT_ELIGIBLE`). **Java positional: có** —
`InventoryReceiveCommand`/`InventoryIssueCommand`/`InventoryAdjustCommand` +2 component mỗi record
(`serialId`, `serialCode`); `InventoryMovementService`/`MaterialIssueService.issueCommand` constructor/
signature đổi. Test cũ dựng các record/constructor này đã **sửa** theo `R10`.

---

### 0.34 P6 – WO Close/Reconcile (ĐÃ HOÀN THÀNH 2026-08-06)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P6). Đóng nốt track `P*` — chỉ còn `P-Deferred` (chờ
tầng OT, ngoài phạm vi dự án). Migration **`V53`**. Bất biến: **`B100`**.

**Quyết định chốt với user (`AskUserQuestion`) trước khi viết kế hoạch chi tiết:** `CLOSED` **khoá
hoàn toàn** — không carve-out đọc/ghi nào sau khi đóng, thay vì phương án "cho phép điều chỉnh nhẹ"
cũng được đưa ra cân nhắc.

**Thiết kế cốt lõi:** `WorkOrderStatus.CLOSED` chỉ vào được từ `COMPLETED`, qua hành động tường minh
`WorkOrderService.close` — **không bao giờ tự động** (khác `COMPLETED`, tự sinh khi cumulative good
chạm plan, `B53`). Đây là nửa "Reconcile" của tên phase: `close()` gọi lại **đúng**
`materialReservationService.cancelActiveReservations(workOrder)` mà `cancel()` đã dùng — giải phóng
mọi reservation `ACTIVE` còn sót (component bị over-reserve nhưng chưa bao giờ issue) về lại tồn khả
dụng, vì một khi `CLOSED` thì WO không còn cách nào khác để giải phóng nó nữa.

| Thay đổi | Ở đâu |
|---|---|
| `canClose()` (chỉ `COMPLETED`) + `close(Instant)` (set `CLOSED` + `closedAt`) | `WorkOrder` |
| `WorkOrderService.close(UUID)` — `STATE_CONFLICT` (409) nếu không `COMPLETED`, reconcile rồi khoá | `WorkOrderService` |
| `POST /work-orders/{id}/close` — không nhận body, tái dùng `PERM_WORK_ORDER_MANAGE` | `WorkOrderController` |
| `WorkOrderResponse.closedAt` (additive, field cuối cùng) | DTO/mapper |
| `WorkOrderAuditAction.WORK_ORDER_CLOSED` | `AuditAction` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Bug thật do phase này phát hiện và sửa, không phải giả định.** `canReserve()` trước phase là
   **danh sách phủ định** `status != COMPLETED && status != CANCELLED`. Một giá trị enum mới không tự
   động bị một danh sách phủ định loại trừ — nếu không sửa, một WO `CLOSED` vẫn nhận reserve mới được,
   đúng loại lỗ hổng checklist `coding-rules.md §11.3` được viết ra để bắt. Đã thêm
   `&& status != CLOSED` tường minh, có test regression riêng (`WorkOrderTest.canReserve_isFalseOnceClosed`)
   và tham số hoá vào `@EnumSource` sẵn có của `MaterialReservationServiceTest`.
2. **Mọi gate khác tự động đúng, không cần sửa.** `canExecute()`, `canReceipt()`, `canRelease()`,
   `canPlan()`, và danh sách status tường minh trong `cancel()` đều là **danh sách khẳng định**
   (positive list) — một WO đã chuyển `CLOSED` (không còn là `RELEASED`/`IN_PROGRESS`/`COMPLETED`/…)
   tự động fail mọi điều kiện đó mà không cần đụng một dòng code. Đã rà đủ theo
   `grep -rn "WorkOrderStatus\." src/main`.
3. **`CapacityBoardService.LOAD_STATUSES` (đọc, không phải gate ghi) có sửa** — thêm `CLOSED` cạnh
   `COMPLETED`: lịch của một WO đã đóng vẫn là load lịch sử **thật**, đóng WO không được âm thầm viết
   lại utilization của một ngày đã báo cáo trước đó. `CANCELLED` vẫn bị loại (lý do khác: lịch của WO
   bị huỷ chưa từng là load thật). Test literal (không tautology, không so sánh hằng số với chính nó):
   `CapacityBoardServiceTest.loadStatuses_includesClosedAndCompletedExcludesCancelled`.
4. **Không permission mới** — tái dùng `PERM_WORK_ORDER_MANAGE`, cùng quyền đã gác `cancel`/`plan`/
   `release`/`update`. Không migration seed, không đụng `docs/roles-and-permissions.md`
   (`coding-rules.md C10` chỉ bắt buộc khi có permission **mới**).
5. **`closedAt` mirror `cancelledAt`/`completedAt`** — cột nullable đơn giản, **không** có `closedBy`
   riêng: ai đóng đã được `updatedBy` (JPA auditing) ghi lại, đúng cách mọi status transition khác
   trên entity này đã làm từ trước.
6. **Endpoint không nhận body** — đóng không đối kháng như cancel (spec/quyết định của user chỉ nói
   "khoá hoàn toàn", không nói gì về lý do), nên **không** thêm `reason` bắt buộc như
   `WorkOrderCancelRequest` (`F7`). Không mở rộng phạm vi ngoài yêu cầu.

**Nghiệm thu:** `mvn -o clean verify` — **864 case unit + 89 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 850 unit + 89 IT / 13 class — `+14` unit, `+0` IT ròng vì phase
này không thêm case IT mới, chỉ bump version assertion trong `FlywayMigrationIT`; xem hàng "Baseline
test" §0.1 để biết đúng test nào cộng vào đâu). Không nghiệm thu mutation riêng — bù lại bằng test ở
mọi tầng bị ảnh hưởng: domain (`WorkOrderTest`), service (`WorkOrderServiceTest`), method-security
(`WorkOrderMethodSecurityTest`), controller (`WorkOrderControllerTest`), và **một test quy hồi cho mỗi
gate ghi** (`MaterialReservationServiceTest`, `MaterialIssueServiceTest`, `ProductionReceiptServiceTest`)
chứng minh `CLOSED` bị chặn — đúng tinh thần bug thật ở hệ quả #1: gate ghi là nơi một dòng code thiếu
mới thực sự gây hại.

**Breaking changes — wire: KHÔNG có** (thuần additive: 1 endpoint mới, 1 field mới `closedAt` trên
`WorkOrderResponse`). **Java positional:** `WorkOrderResponse` +1 component (`closedAt`, cuối cùng).
Test cũ dựng `WorkOrderResponse` theo vị trí (`WorkOrderControllerTest`, `SupplySuggestionServiceTest`)
đã **sửa** theo `R10`.

---

### 0.35 D8c – Forgot-password / Account Recovery (ĐÃ HOÀN THÀNH 2026-08-06)

Nguồn: `NEXT_PHASE_PLAN.md §8` (nợ #6, phần cuối cùng — `D8a` RTR và `D8b` absolute session timeout
đã trả 2/3 trước đó). Thiết kế đầy đủ đã có sẵn từ trước ở `common/security/CLAUDE.md §4.14` +
`module/auth/CLAUDE.md` "Trách nhiệm chính" — phase này **triển khai đúng thiết kế đã viết**, không
thiết kế lại. **Không migration** (thuần Redis, không đụng schema). Bất biến: **`B101`**.

**Quyết định hạ tầng chốt với user (`AskUserQuestion`) trước khi viết kế hoạch chi tiết:** gửi email
bằng **mock/log console** — không thêm `spring-boot-starter-mail`, không SMTP/SES/SendGrid nào được
tích hợp. `EmailNotificationService` chỉ log dòng chứa reset link, không có interface (chỉ một
implementation tồn tại — `coding-rules.md §11.5` cấm abstraction speculative cho một implementation
duy nhất; nếu sau này chốt gửi email thật thì đó mới là lúc thêm interface).

| Thay đổi | Ở đâu |
|---|---|
| `PasswordResetTokenService` (mới) — `auth:reset:{token}` → userId + reverse index `auth:reset:user:{userId}`, cả hai TTL 15m | `module/auth/service/` |
| `EmailNotificationService` (mới) — mock/log, không interface | `module/auth/service/` |
| `AuthService.forgotPassword`/`resetPassword`/`adminUnlockAccount` (mới) | `AuthService` |
| `POST /auth/forgot-password`, `POST /auth/reset-password` (permit-all, đã nằm trong matcher `/api/v1/auth/**` sẵn có) | `AuthController` |
| `PATCH /admin/users/{id}/unlock` — controller `/admin` **đầu tiên** trong repo | `AdminController` (mới) |
| `AuthErrorCode.RESET_TOKEN_INVALID` (401), `AuditAction.PASSWORD_RESET`/`ACCOUNT_UNLOCKED` | `common/exception`, `common/audit` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`forgotPassword` trả `void` và không branch theo kết quả — đây là cơ chế chống account
   enumeration thật, không phải chi tiết triển khai.** Nhánh "email không tồn tại" **không** gọi
   `PasswordResetTokenService`/`EmailNotificationService`, **không** audit gì — hai nhánh phải giống
   hệt nhau ở **mọi** collaborator, không chỉ ở response text trả về client. Controller tự soạn message
   cố định, không đọc gì từ kết quả service (vì service không trả gì để đọc).
2. **Reverse index `auth:reset:user:{userId}` là bắt buộc, không phải tối ưu.** Nếu không có nó,
   `generateToken` không biết token cũ nào (nếu có) cần xoá khi user request forgot-password lần thứ
   hai — sẽ tích luỹ nhiều token sống cùng lúc, vi phạm "không cho tích lũy" mà thiết kế gốc đòi hỏi.
3. **`RESET_TOKEN_INVALID` cố ý là MỘT mã cho cả "chưa từng tồn tại" lẫn "đã hết hạn"** — Redis TTL
   không phân biệt được hai trường hợp, đúng cách `REFRESH_TOKEN_EXPIRED` đã xử lý cho refresh token.
   Không thêm `RESET_TOKEN_EXPIRED` dù bảng draft ở `error-handling.md §5.3` từng liệt kê cả hai —
   thêm một mã không ai từng ném tới là code speculative.
4. **`resetPassword` xoá token cả hai chiều** (`invalidate(token, userId)` xoá cả `auth:reset:{token}`
   lẫn `auth:reset:user:{userId}`) rồi mới force-logout toàn bộ phiên
   (`deleteAllUserTokens`+`deleteAllDeviceSessions`, cùng cặp `logoutAll` đã dùng) — thứ tự không quan
   trọng ở đây (khác `B80`/`B81` của refresh rotation, nơi thứ tự Redis là bất biến), nhưng **cả bốn
   thao tác đều bắt buộc**, thiếu một là để lại orphan key hoặc phiên cũ còn sống.
5. **`adminUnlockAccount` dùng `@PreAuthorize("hasRole('ADMIN')")` — role-based, không phải `PERM_*`
   scope-based** như phần lớn service khác trong repo gần đây. Đây là lựa chọn nhất quán: nó khớp
   cách `UserService` (`create`/`delete`/`assignRole`) đã gác các hành động ADMIN-only từ trước, không
   phải ngoại lệ mới.
6. **`AdminController` gọi thẳng `AuthService`, không qua `UserService`** dù nó thao tác lên `User` —
   ba hành động account-recovery (forgot/reset/unlock) được nhóm cùng một service theo đúng thiết kế
   đã viết sẵn từ trước, không tách theo "ai sở hữu entity nào". Đừng "dọn cho gọn" bằng cách chuyển
   `adminUnlockAccount` sang `UserService` — đó là đổi kiến trúc đã quyết định, không phải refactor.
7. **`User.lock()`/`UserStatus.LOCKED` vẫn không có call site nào** — khoá tự động hiện tại thuần
   Redis fail-count, không đụng `users.status`. `adminUnlockAccount` gọi `user.activate()` đúng theo
   thiết kế đã chốt, và tiện thể khôi phục được user bị `UserService.delete()` đưa về `INACTIVE`.
8. **`D8c` KHÔNG thêm permission mới** ⇒ `docs/roles-and-permissions.md` KHÔNG đổi (`coding-rules.md
   C10` chỉ bắt buộc khi có permission **mới**; `hasRole('ADMIN')` không phải permission catalog).

**Nghiệm thu:** `mvn -o clean verify` — **885 case unit + 89 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 864 unit + 89 IT / 13 class — `+21` unit, `+0` IT vì phase này
không đụng schema/JPQL nào; xem hàng "Baseline test" §0.1 để biết đúng test nào cộng vào đâu). Không
nghiệm thu mutation riêng — bù lại bằng test ở mọi tầng: Redis key contract (`PasswordResetTokenServiceTest`),
service (`AuthServiceTest`, cả hai nhánh forgot-password + reset-password + unlock), method-security
(`AuthMethodSecurityTest`, deny/allow `hasRole('ADMIN')`), controller (`AuthControllerTest`,
`AdminControllerTest`), và regression guard cho secret masking (`SensitiveRequestToStringTest`).

**Breaking changes — wire: KHÔNG có** (thuần additive: 3 endpoint mới, 1 mã lỗi mới, 2 audit action
mới). **Java positional: có** — `AuthService` constructor +2 tham số (`PasswordResetTokenService`,
`EmailNotificationService`). Test cũ dựng `AuthService`/`AuthServiceTest` đã **sửa** theo `R10`.

---

### 0.36 C2-1 – Audit Logs Read API (ĐÃ HOÀN THÀNH 2026-08-06)

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.3`. FE xác nhận (`docs/capstone2-api-gap-response.md §5`
câu 1, 2026-08-06): màn hình Audit dùng được với `changes[]` rỗng — đợt 1 (event-level, đã ghi từ lâu)
là đủ, **không** cần chờ đợt 2 (field-level diff qua `AuditableAspect`, vẫn chưa xếp lịch). Phase này
chỉ là read API trên dữ liệu `audit_logs` đã tồn tại và đang được ghi. Migration **`V54`** (schema) +
**`V55`** (seed permission).

**Quyết định chốt với user (`AskUserQuestion`) trước khi viết code:** `PERM_AUDIT_READ` là
**ADMIN-only** — audit trail lộ IP/user-agent/lịch sử hành động của mọi user, cùng tầng nhạy cảm với
`PERM_ORG_MANAGE`/`PERM_ACCESS_MANAGE` (cả hai đã ADMIN-only), không phải tầng `PERM_COSTING_READ`
(ADMIN+MANAGER).

| Thay đổi | Ở đâu |
|---|---|
| `AuditLog.plantId` (mới, nullable, FK `plants`) | `common/audit/AuditLog.java`, `V54` |
| `AuditLogQueryService` (mới) — `list`/`get`, tách khỏi `AuditLogService` (service đó chỉ publish, fire-and-forget) | `common/audit/` |
| `AuditLogRepository.search(...)` — 8 filter, tất cả exact-match | `common/audit/AuditLogRepository.java` |
| `AuditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc` (mới) | `common/audit/AuditLogChangeRepository.java` |
| `GET /audit-logs` (list, không `changes[]`), `GET /audit-logs/{id}` (detail, có `changes[]`) | `common/audit/controller/AuditLogController.java` (mới) |
| `PERM_AUDIT_READ` — chỉ `ADMIN` | `V55` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Cột `plant_id` chỉ dừng ở schema — quyết định phạm vi có chủ đích, không phải thiếu sót.**
   `V54` thêm cột nhưng **không** wiring populate real-time: không đụng `RequestContext`,
   `AuditLogEvent`, `AuditableAspect`, hay bất kỳ call site `@Auditable`/`auditLogService.log*()` nào.
   Mọi dòng — **cả lịch sử lẫn dòng mới tạo hôm nay** — vẫn `plant_id = NULL` cho tới khi có phase
   riêng wiring nó. Lọc theo `plantId` hôm nay **luôn trả rỗng**. Bản 2026-08-04 của
   `docs/capstone2-api-gap-response.md` từng hứa nhẹ hơn ("dòng mới sẽ có") — đã sửa lại cho khớp thật.
2. 🔴 **`changes[]` trên `GET /audit-logs/{id}` là JOIN THẬT, không phải hardcode `[]`.** Nó gọi
   `AuditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc` — bảng `audit_log_changes` **đã tồn
   tại từ `V6`** (đính chính: `common/audit/CLAUDE.md` trước đây ghi sai là "chưa implement", xem
   §9.12 file đó) nhưng **0 call site nào ghi vào nó** (đợt 2, ngoài phạm vi). Kết quả hôm nay luôn
   rỗng, nhưng sẽ tự động có dữ liệu thật ngay khi đợt 2 triển khai — không cần sửa code ở đây.
3. **List endpoint cố ý KHÔNG kèm `changes[]` mỗi dòng** — batch-query một kết quả luôn-rỗng cho mọi
   trang là lãng phí vô nghĩa. Chỉ detail endpoint mới join.
4. 🔴 **Phát hiện biến thể MỚI của lỗi `lower(bytea)` (`§0.24`), lần đầu KHÔNG liên quan `concat`/
   `like`.** Tham số `Instant` (`:from`/`:to`) chỉ xuất hiện ở vế `IS NULL` — Hibernate sinh một `?`
   positional **riêng** cho mỗi lần xuất hiện tên tham số, nên vế đó không có ngữ cảnh type nào khác
   để Postgres suy ra ⇒ `could not determine data type of parameter`. Khác lỗi cũ (toàn bộ endpoint
   500 do `concat`), lỗi này chỉ nổ khi filter đó **được dùng** (`from`/`to` khác null). Sửa bằng
   `cast(:from as timestamp) IS NULL OR a.createdAt >= :from` — chỉ cast vế `IS NULL`, giữ nguyên vế
   so sánh (tránh đụng ngữ nghĩa `timestamptz`). Test `AuditLogRepositoryIT.search_filtersByCreatedAtRange`
   là nơi duy nhất bắt được (`AuditLogQueryServiceTest` dùng mock, không thấy). Xác nhận filter
   UUID/String cùng query (`actorUserId`, `entityType`...) **không** cần cast — đã có tiền lệ hoạt động
   đúng ở `WorkOrderRepository.productItemId`/`UomRepository.status`. Ghi chi tiết:
   `common/audit/CLAUDE.md §9.12`.
5. **Không `X-Plant-Id` cross-check trên filter `plantId`** — endpoint gác bằng permission global
   ADMIN-only, không phải plant scope; cơ chế cross-check (`error-handling.md §5.6.1`) tồn tại để giữ
   session/URL của caller plant-scoped nhất quán, không áp dụng cho một read xuyên-plant.
6. `AuditLogController` đặt trong `common/audit/controller/` (subpackage mới), không phải
   `module/audit/` riêng — `common/audit` đã là nơi chứa mọi thứ audit, thêm module riêng cho 2
   endpoint là quá tay.

**Nghiệm thu:** `mvn -o clean verify` — **898 case unit + 98 case IT / 14 class IT, failures = 0,
errors = 0** (baseline trước phase: 885 unit + 89 IT / 13 class — `+13` unit
(`AuditLogQueryServiceTest` 5 + `AuditLogMethodSecurityTest` 4 + `AuditLogControllerTest` 4), `+9` IT
từ `AuditLogRepositoryIT` (**class IT thứ 14**, mới) + `+1` case trong `FlywayMigrationIT`
(`migrate_v55_grantsAuditReadToAdminOnly`); xem hàng "Baseline test" §0.1). Không nghiệm thu mutation
riêng — bù lại bằng test ở mọi tầng: service (filter forwarding, detail 404, `changes[]` từ repo thật
không hardcode), method-security (deny+allow), controller (envelope, 400 type-mismatch, 404),
repository `*IT` (8 case, mỗi filter riêng + kết hợp), `FlywayMigrationIT` (ma trận grant ADMIN-only).

**Breaking changes — wire: KHÔNG có** (thuần additive: 2 endpoint mới, 1 permission mới, 1 cột mới
trên `audit_logs`). **Java positional: không có** — chỉ thêm field/method mới, không đổi constructor
nào có sẵn.

---

### 0.5 ✅ Đảo Ngược Ngữ Nghĩa — ĐÃ XỬ LÝ Ở `F5-A` (2026-07-27)

Đây từng là rủi ro lớn nhất của track `F*`. **Đã xong**, giữ lại bảng để agent sau hiểu vì sao code
trông như hiện nay:

| | Trước `F5` | Sau `F5-A` (hiện tại) |
|---|---|---|
| Ai làm WO tiến triển | **Receipt approve** | **`ProductionExecutionService.report`** — operator post good/scrap/rework |
| Trần của receipt (**B16**) | `plannedQuantity − completedQuantity − Σ(DRAFT + PENDING_APPROVAL)` | `actualGoodQuantity − completedQuantity − Σ(DRAFT + PENDING_APPROVAL)` |
| WO → `COMPLETED` khi | receipt approve đủ số | cumulative good = planned, **độc lập receipt** |

**Ba cột số lượng trên `work_orders`, đừng nhầm:** `plannedQuantity` (kế hoạch) ·
`actualGoodQuantity`/`actualScrapQuantity`/`actualReworkQuantity` (xưởng làm ra) ·
`completedQuantity` (**đã nhập kho** — giữ nguyên tên và ngữ nghĩa cũ, có chủ đích, để dữ liệu lịch
sử không bị diễn giải lại). Chi tiết + bất biến `B53`-`B57`: `module/workorder/CLAUDE.md`.

## 1. PROJECT OVERVIEW

### 1.1 Description
Manufacturing ERP là hệ thống hoạch định nguồn lực doanh nghiệp dành riêng cho **sản xuất** (nhà máy lắp ráp, sản xuất theo quy trình). Khác với Commercial ERP (mua-bán-phân phối), hệ thống quản lý **vòng đời sản xuất đầu cuối** — từ nguyên liệu thô đến thành phẩm.

**Mục tiêu nghiệp vụ lõi** (từ `AGENTS.md` §1, đã xoá):
- Inventory accuracy
- Multi-level BOM
- Shortage detection
- Work Order execution
- Material issue / production receipt / WIP tracking / variance

### 1.2 So Sánh với Commercial ERP

| Chiều | Manufacturing ERP | Commercial ERP |
|---|---|---|
| Sản xuất | BOM đa cấp, Work Order, Routing, MRP I/II | Không có / cơ bản |
| Tồn kho | WIP, theo dõi Batch/Lot/Serial | Chỉ kho tiêu chuẩn |
| Chi phí | Job/Process/Standard Costing + Phân tích variance | Chỉ COGS |
| Hoạch định | MRP + CRP (tự động yêu cầu vật tư/năng lực) | Dự báo cầu cơ bản |
| Truy xuất nguồn gốc | Toàn chuỗi (bắt buộc) | Tùy chọn |
| Tích hợp MES | Giao diện ERP ↔ MES 2 chiều | Không cần |

### 1.3 Lộ Trình Module

```
Phase 1 – Foundation
├── Auth & Security (JWT, RBAC, Session management)
├── User & Organization Management
└── Inventory / Warehouse (cờ IsManufacturing)

Phase 2 – Manufacturing Core
├── Bill of Materials (BOM đa cấp)
├── Work Order Management
├── Material Requirements Planning (MRP)
└── Shop Floor Control

Phase 3 – Advanced & Integration
├── MES Integration (chuẩn ISA-95)
├── Quality Control (truy xuất Batch/Lot)
├── Costing Engine (Standard/Job/Process)
└── Reporting & Analytics (OEE, Variance)
```

### 1.4 Tham Chiếu Thực Tế
- **VinFast / Foxconn**: BOM 8–10 cấp + MES thời gian thực trên dây chuyền lắp ráp
- **Masan / TH True Milk**: Sản xuất theo quy trình + kiểm soát Batch/phế liệu
- Mô hình kiến trúc: **ISA-95** (chuẩn tích hợp ERP ↔ MES)

---

## 2. TECHNOLOGY STACK

```yaml
language:       Java 17 (records, sealed classes, pattern matching)
framework:      Spring Boot 3.x
security:       Spring Security 6 + JWT (jjwt)
database:
  primary:      PostgreSQL (quan hệ, ACID)
  cache:        Redis (token store, session blacklist, rate-limit)
orm:            Spring Data JPA + Hibernate
migration:      Flyway
build:          Maven
documentation:  Springdoc OpenAPI 3 (Swagger UI)
testing:        JUnit 5 · Mockito · MockMvc · Testcontainers
monitoring:     Spring Boot Actuator + Micrometer
logging:        SLF4J + Logback (JSON có cấu trúc trong prod)
containerize:   Docker + Docker Compose
```

---

## 3. PROJECT CONVENTIONS

### 3.1 Quy Tắc Đặt Tên
- **PascalCase** → tên class (`WorkOrderService`, `BomController`)
- **camelCase** → phương thức & biến (`findByWorkOrderId`, `isExpired`)
- **ALL_CAPS** → hằng số (`DEFAULT_PAGE_SIZE`, `TOKEN_PREFIX`)
- **snake_case** → cột database, file migration Flyway

### 3.2 Cấu Trúc Package
```
com.erp.manufacturing
├── common/
│   ├── exception/          # Phân cấp exception toàn cục
│   ├── response/           # ApiResponse, PageResult wrappers
│   ├── security/           # JWT utils, filters, IpExtractor, TokenStoreService — CLAUDE.md riêng
│   ├── audit/              # BaseEntity, AuditLog entity, AuditLogService, Aspect — CLAUDE.md riêng
│   └── context/            # RequestContext (userId, ip, traceId) – ThreadLocal holder
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── AsyncConfig.java            # @EnableAsync + Executor truyền MDC
│   ├── JpaAuditingConfig.java      # @EnableJpaAuditing
│   ├── RateLimitProperties.java    # @ConfigurationProperties
│   └── OpenApiConfig.java
├── module/
│   ├── auth/               # AuthController, AuthService, PasswordResetTokenService — CLAUDE.md riêng
│   ├── user/
│   ├── organization/       # roles/permissions/access_scopes, AccessControlService — CLAUDE.md riêng
│   ├── inventory/          # CLAUDE.md riêng
│   ├── bom/                # CLAUDE.md riêng
│   ├── workorder/          # CLAUDE.md riêng
│   ├── routing/            # Routing master data + snapshot lên Work Order — CLAUDE.md riêng
│   ├── sales/              # Sales Order + independent demand cho MRP — CLAUDE.md riêng
│   ├── planning/           # MRP run, requirement explosion, supply suggestion — CLAUDE.md riêng
│   ├── purchasing/         # Supplier, PR → PO → Goods Receipt — CLAUDE.md riêng
│   └── reporting/          # Report controller theo domain (inventory/workorder/purchasing)
└── ManufacturingErpApplication.java
```

> Các thư mục ghi "CLAUDE.md riêng" có file `CLAUDE.md` lồng bên trong — Claude Code chỉ tự nạp file đó
> khi có agent thực sự đọc/sửa code trong đúng thư mục ấy. Xem bảng đầy đủ ở "Bản Đồ Tài Liệu" dưới đây.

### 3.3 Quy Ước API
- Base path: `/api/v1/`
- Auth endpoints: `/api/v1/auth/**` (permit all)
- Protected: `/api/v1/**` (yêu cầu JWT hợp lệ)
- HTTP status codes: tuân thủ chặt chẽ ngữ nghĩa REST
- Phân trang: query params `page`, `size`, `sortBy`, `sortDir`

---

## BẢN ĐỒ TÀI LIỆU

> File này đã được chia nhỏ (2026-07-25) để agent chỉ tự nạp phần liên quan đến task đang làm,
> thay vì luôn nạp toàn bộ ~1500 dòng. Cơ chế:
> - `.claude/rules/*.md` → Claude Code **luôn** tự nạp cùng `CLAUDE.md`, dùng cho rule cross-cutting (áp dụng mọi module).
> - `<package>/CLAUDE.md` (lồng trong `src/main/java/...`) → chỉ tự nạp khi agent đọc/sửa file trong đúng thư mục đó.

| Chủ đề | File | Cơ chế nạp |
|---|---|---|
| Error handling / response contract | `.claude/rules/error-handling.md` | Luôn nạp |
| Git workflow / commit convention / migration naming | `.claude/rules/dev-workflow.md` | Luôn nạp |
| Key architectural decisions | `.claude/rules/architecture-decisions.md` | Luôn nạp |
| Best practices checklist (security/API/DB/code quality/perf/test/observability) | `.claude/rules/best-practices.md` | Luôn nạp |
| Coding rules hàng ngày (N+1, breaking change, cấm) | `.claude/rules/coding-rules.md` | Luôn nạp |
| Auth/Session design (JWT, Redis key, rate limit, RTR, fingerprint) + bất biến B35-B36 | `src/main/java/com/erp/manufacturing/common/security/CLAUDE.md` | Chỉ khi chạm `common/security/**` |
| AuthService/AuthController + bất biến B33-B34 | `src/main/java/com/erp/manufacturing/module/auth/CLAUDE.md` | Chỉ khi chạm `module/auth/**` |
| Audit Log Architecture | `src/main/java/com/erp/manufacturing/common/audit/CLAUDE.md` | Chỉ khi chạm `common/audit/**` |
| Bất biến RBAC B30-B32, B37 | `src/main/java/com/erp/manufacturing/module/organization/CLAUDE.md` | Chỉ khi chạm `module/organization/**` |
| Bất biến Inventory B1-B6 | `src/main/java/com/erp/manufacturing/module/inventory/CLAUDE.md` | Chỉ khi chạm `module/inventory/**` |
| Bất biến BOM B7-B11 | `src/main/java/com/erp/manufacturing/module/bom/CLAUDE.md` | Chỉ khi chạm `module/bom/**` |
| Bất biến Work Order & Manufacturing Execution B12-B20 | `src/main/java/com/erp/manufacturing/module/workorder/CLAUDE.md` | Chỉ khi chạm `module/workorder/**` |
| Bất biến Planning/MRP B21-B26 | `src/main/java/com/erp/manufacturing/module/planning/CLAUDE.md` | Chỉ khi chạm `module/planning/**` |
| Bất biến Routing B48-B52 + quyết định `MISSING_ROUTING` / Work Center FK (`C2-6`) | `src/main/java/com/erp/manufacturing/module/routing/CLAUDE.md` | Chỉ khi chạm `module/routing/**` |
| Bất biến Sales Order B43-B47 + quyết định `sales`→`planning` | `src/main/java/com/erp/manufacturing/module/sales/CLAUDE.md` | Chỉ khi chạm `module/sales/**` |
| Bất biến Purchasing B27-B29 | `src/main/java/com/erp/manufacturing/module/purchasing/CLAUDE.md` | Chỉ khi chạm `module/purchasing/**` |
| Bất biến UOM B82-B85 + giới hạn `hasPermission`/`GLOBAL` scope (`C2-3`) | `src/main/java/com/erp/manufacturing/module/uom/CLAUDE.md` | Chỉ khi chạm `module/uom/**` |
| Bất biến Work Center B_wc1-B_wc4 (`C2-6`, `B_wc4` ở `C2-7`) | `src/main/java/com/erp/manufacturing/module/workcenter/CLAUDE.md` | Chỉ khi chạm `module/workcenter/**` |
| Bất biến Shift/Work Calendar B_sh1-B_sh2, B_cal1-B_cal2 + quy ước qui-thuộc-ngày ca qua đêm (`C2-7`) | `src/main/java/com/erp/manufacturing/module/shift/CLAUDE.md` | Chỉ khi chạm `module/shift/**` |
| Bất biến Costing B91-B92 + entry points `ItemStandardCostLookupService` (`P3`) | `src/main/java/com/erp/manufacturing/module/costing/CLAUDE.md` | Chỉ khi chạm `module/costing/**` |
| **Hướng dẫn API cho FE** (envelope, auth, luồng 10 bước, mã lỗi, chỗ lệch spec) | `docs/api-guide-for-frontend.md` | Đọc thủ công — **tài liệu đối ngoại**, viết cho team FE |
| **Session bootstrap cho FE** (decode JWT lấy permissions, workaround profile/plant/scope) | `docs/fe-session-bootstrap.md` | Đọc thủ công — **tài liệu đối ngoại**. Ghi rõ 2 khoảng trống: không có `GET /auth/me`, không có "default plant" |
| **Phản hồi gap Capstone 2** (đối chiếu `BACKEND_CAPSTONE2_API_GAPS.md` của FE với code thật) | `docs/capstone2-api-gap-response.md` | Đọc thủ công — **tài liệu đối ngoại**, thêm ở `C2-0` (2026-08-04). Chứa 3 mục FE báo thiếu mà **đã có**, 2 chỗ FE mô tả nhẹ hơn thực tế (audit diff rỗng, lot-status vs `B62`), và **5 câu hỏi đang chờ FE trả lời** — `C2-1`/`C2-2` bị chặn cho tới khi có câu 1 và 2 |

> 🔴 **`docs/api-guide-for-frontend.md` là tài liệu FE đang dùng để wire API.** Phase nào đổi
> endpoint / DTO / mã lỗi trên luồng sản xuất **phải** cập nhật file đó trong cùng commit — để nó lệch
> code chính là làm FE gắn sai. Nguồn của nó là **code thật**, không phải spec; spec gốc ở
> `docs/fe-spec-omniplant.md`, phần lệch giữa hai bên nằm ở §9 của guide.

> Nội dung không mất — chỉ đổi vị trí. Khi cần tra một bất biến `B<n>` hay một mục `4.x`/`9.x` cụ thể mà
> không chắc nằm ở file nào, grep theo mã bất biến hoặc theo tên class liên quan trong bảng trên.

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
| **Phase đang chạy** | **`F10` – 3 nợ field-level cuối của spec FE (`G`, `F`, `H`)** ✅ **HOÀN THÀNH** (2026-08-01). `projectedAvailable` (persist, **không** derive được), `sourceRoutingCode`/`Version` trên proposal, số chứng từ `MI-`/`PE-`. Migration **`V40`**, wire **additive**. Nợ `E` cố ý ngoài phạm vi. Bản ghi: §0.21 |
| **Phase trước** | **`F9` – 2 field cuối của spec FE + sửa tuyên bố nói quá của `F8`** ✅ HOÀN THÀNH (2026-07-31). `uom` trên execution-candidate, `reservedQuantity` trên component line. **Không migration.** Bản ghi: §0.20 |
| **Phase kế tiếp** | **Chưa chốt.** Track `F*` sau `F10` **không còn nợ nào thuộc loại "field FE hiển thị mà backend đã có dữ liệu"**. Nợ còn mở ở `FRONTEND_ALIGNMENT_ROADMAP.md §7.1`: **`B`** (`predecessorOperationIds` — chưa có consumer), **`C`** (`created_by` trong scope idempotency — cần backfill), **`D`** (17 DTO ngoài luồng spec — lệch tên có chủ đích), **`E`** (BOM `code` + `outputQuantity` — **khái niệm chưa tồn tại**, đụng công thức nổ BOM), **`I`** (`childBom` — WO snapshot direct-only theo `B12`). Không cái nào là bug. Ứng viên: **`D8`** (nợ #6 — RTR / absolute session timeout / forgot-password) · **`P3`** costing · **`P4`** Work Center + CRP (🔴 spec §11 **tự** xếp ngoài MVP ⇒ cần user xác nhận tường minh) · nợ `E`. **Cần user chốt.** |
| **Migration mới nhất** | `V40__add_projected_available_routing_snapshot_and_document_codes.sql` (`F10`) — `D7`, `D7b`, `D11`, `F9` **không** migration |
| **Baseline test** | 180 case / 44 class → T0+T1: 218 → T3: 257 → T2/T4/T5: 281 case / 57 class → F1: 289 → F2: 303 → F3: 320 → F4: 347 → F5-A: 356 → F5-B: 365 → F6: 391 → D1: 396 → D9+D10: 402 → D4: 408 → D5: 412 → D6: 416 → D7: 450 → D7b: 510 → D11: 516 → F7: 521 → F8: 545 → F9: 549 → **hiện tại (`F10`): 556 case unit + 59 case IT / 10 class IT**, failures = 0 |
| **Coverage tool** | ✅ JaCoCo 0.8.12 — **unit + IT: line 80.1% / branch 63.9%** · **unit một mình: line 73.7% / branch 59.2%** (đo lại 2026-08-01 sau `F10`). ⚠️ **Xu hướng đã xác nhận bảy phase liên tiếp:** `D7` +34 case ⇒ +0.6 line; `D7b` +60 ⇒ +0.8; `D11` +6 ⇒ +0.0 / +0.2; `F7` +12 ⇒ +0.2 / +0.2; `F8` +40 ⇒ +0.5 / +0.4; `F9` +8 ⇒ +0.1 / +0.0; **`F10` +7 unit / +2 IT ⇒ +0.0 line / +0.4 branch**. **Coverage không đo được contract** — thước đo thật là nghiệm thu mutation (§0.15–§0.21). `F10` là ví dụ mới nhất: cả 3 nợ nó đóng nằm ở vùng code **đã có coverage**, vì thứ thiếu là *một cột không tồn tại*; và mutation #3 của nó **xanh ở lần chạy đầu** dù case liên quan đã có sẵn và đang xanh. Xem cảnh báo cách đo ngay dưới bảng |
| **Bảng theo dõi phase** | Nghiệp vụ `P*`: `MANUFACTURING_GAP_ROADMAP.md §2.1` · Kiểm thử `T*`: `TEST_IMPROVEMENT_PLAN.md §0` (xong hết) · Căn chỉnh FE `F*`: `FRONTEND_ALIGNMENT_ROADMAP.md §1` (tổng quan + lịch sử) / `NEXT_PHASE_PLAN.md` (phase đang chạy) |

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
| `auth` + `user` | ✅ Done | JWT HS256, refresh rotation, multi-device session, brute-force Lua |
| `organization` + dynamic RBAC | ✅ Done | `roles` / `permissions` / `access_scopes` / `user_role_assignments`, scope Company→Plant→Warehouse |
| `inventory` | ✅ Done | `stock_movements` append-only ledger, `stock_balances` projection, lot tracking, idempotency. **[D6]** `Idempotency-Key` scope theo `(key, movement_type)` (V37). Xem §0.14 + `module/inventory/CLAUDE.md` B69-B71 |
| `bom` | ✅ Done | BOM đa cấp, circular reference check, activate/deactivate revision |
| `planning` + `mrp` | ✅ Done | MRP run, requirement explosion, supply suggestion, safety stock + lead time |
| `purchasing` | ✅ Done | Supplier, PR → PO → Goods Receipt (+ cancel/reversal) |
| `workorder` | ✅ Done | WO core, material reservation/issue, WIP, production receipt, variance |
| `workorder` – **P1 gates** | ✅ Done | 3 business gate (chi tiết §0.3) |
| `common/audit`, `common/security`, `common/response`, `common/exception` | ✅ Done | |
| `quality` (QC disposition) | ✅ Done (`F2`, `D5`) | **Không** là package riêng — `QualityDisposition` nằm trong `module/workorder` vì thuộc aggregate Production Receipt. **[D5]** QC chạy cho **cả** output không lot-tracked (phán quyết trên receipt); `quality_dispositions` vẫn chỉ ghi khi có lot. Xem `module/workorder/CLAUDE.md` B38-B41 |
| `sales` | ✅ Done (`F3`, `F6`) | Sales Order + line, confirm ⇒ independent demand cho MRP, endpoint `planning-demands`. **[F6]** fulfillment + roll-up status. Xem `module/sales/CLAUDE.md` B43-B47, B66 |
| `routing` | ✅ Done (`F4`) | `RoutingHeader` + `RoutingOperation`, 1 routing `ACTIVE`/item, snapshot bất biến lên WO. **Work Center vẫn là string**, chưa có CRP/capacity (`P4` phần còn lại). Xem `module/routing/CLAUDE.md` B48-B52 |
| `planning` – **F5-B** | ✅ Done | Endpoint `/planning-runs` + `/supply-suggestions`, `demandLineIds`, `supplyType` `MAKE`/`BUY`, `exceptionState` + `messages[]`, `settingSource` + `excludedLotCount` (V34). Xem §0.10 + `module/planning/CLAUDE.md` B58-B61 |
| `planning` – **D4** | ✅ Done | Open purchase order vào netting (`PurchaseOrderSupplyService`), `MrpRun.code` + 4 ô summary Run header (V36). Xem §0.12 + `module/planning/CLAUDE.md` B67-B68 |
| `workorder` – **F5-A** | ✅ Done | `ProductionExecution` + `WorkOrderOperation` (snapshot routing), status `PLANNED`, đảo ngược B16/B17, `PERM_PRODUCTION_EXECUTION_*` (V32/V33). Xem §0.9 |
| `workorder` – **F6** | ✅ Done | `WorkOrderDemandAllocation` (V35) nối WO ↔ Sales Order line; QC `AVAILABLE` ⇒ fulfillment. Xem §0.11 + `module/workorder/CLAUDE.md` B62-B65 |
| `costing` | 🔜 P3 | Chưa bắt đầu |
| work center entity / CRP | 🔜 P4 (phần còn lại) | Chưa bắt đầu |

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
| 6 | RTR (reuse detection), absolute session timeout, forgot-password: **thiết kế xong, chưa implement** | Backlog auth |
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
| Bất biến Routing B48-B52 + quyết định `MISSING_ROUTING` / Work Center là string | `src/main/java/com/erp/manufacturing/module/routing/CLAUDE.md` | Chỉ khi chạm `module/routing/**` |
| Bất biến Sales Order B43-B47 + quyết định `sales`→`planning` | `src/main/java/com/erp/manufacturing/module/sales/CLAUDE.md` | Chỉ khi chạm `module/sales/**` |
| Bất biến Purchasing B27-B29 | `src/main/java/com/erp/manufacturing/module/purchasing/CLAUDE.md` | Chỉ khi chạm `module/purchasing/**` |

> Nội dung không mất — chỉ đổi vị trí. Khi cần tra một bất biến `B<n>` hay một mục `4.x`/`9.x` cụ thể mà
> không chắc nằm ở file nào, grep theo mã bất biến hoặc theo tên class liên quan trong bảng trên.

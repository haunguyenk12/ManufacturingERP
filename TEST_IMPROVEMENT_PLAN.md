# Test Improvement Plan – Khắc Phục Khoảng Trống Kiểm Thử

> Nguồn: kết quả review toàn bộ 44 test class ngày **2026-07-24**.
> File này **song song** với `MANUFACTURING_GAP_ROADMAP.md` (track nghiệp vụ `P*`).
> Các phase `T*` dưới đây **độc lập với phase nghiệp vụ `P*`** và có thể xen kẽ.
> Mọi coding agent phải đọc `CLAUDE.md` → file này trước khi sửa test.

## 0. Theo Dõi Tiến Độ  *(verify bằng `mvn -o test` ngày 2026-07-25)*

```text
Test class unit 57 (đầu plan: 44) + IT 3 [NEW T4]   ·   Test case unit 281 (đầu plan: 180) + IT 10   ·   failures = 0
```

> **Lưu ý đếm class (T4):** `find src/test -name '*Test.java'` trả về 58, không phải 57, vì
> `common/AbstractPostgresIntegrationTest.java` (base class dùng chung cho IT, không có `@Test` nào)
> khớp glob `*Test.java` một cách tình cờ (tên đặt theo đúng thiết kế `NEXT_PHASE_PLAN.md` T4.1, không
> đổi tên để tránh trùng). Số class unit test **thật** vẫn là 57; 3 class IT mới (`*IT.java`) tách
> riêng, chạy qua `mvn verify` (Failsafe), không tính vào baseline `mvn test`.

> **Đính chính số class (2026-07-25):** con số "56 class" ghi ở phần T1 bên dưới là **sai**.
> Đếm lại bằng surefire report (`target/surefire-reports/*.xml`) và `find src/test -name '*Test.java'`:
> baseline sau T0+T1 thực tế là **51 class / 218 case**, sau T3 là **53 class / 257 case**
> (`src/test` có 54 file `.java`, trong đó `SecurityTestController.java` không phải test class; 0 `@Nested`).
> Số **case** trong toàn bộ file này vẫn đúng — chỉ số **class** bị lệch.

- [x] **T0 – Tooling & Baseline** — JaCoCo 0.8.12, baseline line 62.3% / branch 48.9% (§1.1)
- [x] **T1 – Cứu 10 class Method-Security** — hoàn thành (56 class / 218 case)
  - [x] `T1.1` verify khoá chặt chuỗi `PERM_*` cho toàn bộ assertion deny
  - [x] `T1.2` nhánh ALLOW cho mọi `*MethodSecurityTest`
  - [x] `T1.3` phủ 15 permission còn thiếu (+4 class mới)
  - [x] `T1.4` `PermissionCatalogTest` – chống lệch code ↔ Flyway seed
  - [x] `T1.5` trả nợ P1: bất biến **B13** (WO `BLOCKED` bị chặn thực thi)
- [x] **T2 – Tầng Contract: Exception Handler & Controller** — hoàn thành 2026-07-25 (57 class / 281 case)
  - [x] `T2.1` `GlobalExceptionHandlerTest` [NEW] – 6 case, phủ cả 6 nhánh exception handler
  - [x] `T2.2` `AuthControllerTest` / `WorkOrderControllerTest` / `InventoryControllerTest` [NEW] – 7 + 6 + 5 case
  - [x] `T2.3` `@WithMockUser` dùng ở `GlobalExceptionHandlerTest` (class-level) + `AuthControllerTest.logout` (`I9` đã xử lý)
  - [x] `T2.4` Idempotency-Key header forward test ở `WorkOrderControllerTest.issueComponent` + `InventoryControllerTest.receive`
  - [x] `T2.5` `X-Trace-Id` header test ở `GlobalExceptionHandlerTest`
- [x] **T3 – Auth & Security Core** — hoàn thành 2026-07-25 (53 class / 257 case)
  - [x] `T3.1`–`T3.3` `AuthServiceTest` phủ `refresh` / `logout` / `logoutAll` (6 → 15 case)
  - [x] `T3.4` `TokenStoreServiceTest` [NEW] – 17 case, mock `RedisTemplate` (D2)
  - [x] `T3.5` `JwtTokenProviderTest` [NEW] – 13 case, instance thật, không mock JJWT (D1)
  - [ ] `T3.6` log sanitization — **cố ý hoãn**: `LoginRequest`/`RefreshRequest` là record thuần,
        `toString()` tự sinh lộ password/raw token ⇒ phải sửa `src/main`, mâu thuẫn `D7`.
        Ghi thành nợ kỹ thuật `CLAUDE.md §0.4 #7`, vá ở commit nghiệp vụ riêng
- [x] **T4 – Tầng Persistence: JPQL, RBAC, Flyway** — hoàn thành 2026-07-25 (57 class unit + 3 IT / 281 case unit + 10 case IT)
  - [x] `T4.1` `AbstractPostgresIntegrationTest` [NEW] – Singleton Container pattern (`postgres:16-alpine`)
  - [x] `T4.2` `FlywayMigrationIT` [NEW] – chạy sạch `V1..V24` trên DB trống
  - [x] `T4.3` `UserRoleAssignmentRepositoryIT` [NEW] – 6 case, bịt `I6`/bất biến B32, nghiệm thu mutation JPQL đã xác nhận đỏ đúng case
  - [x] `T4.4` `StockBalanceRepositoryIT` [NEW] – phát hiện **bug thật** trong `aggregateAvailableQuantities`/`aggregatePlanningQuantities` (xem `module/inventory/CLAUDE.md` "Nợ Kỹ Thuật Đã Biết #1") — theo quyết định của user, **chỉ pin lại hành vi hiện tại + ghi nợ kỹ thuật, không tự sửa `src/main`**
  - [x] `T4.6` optimistic locking thật trên `StockBalance` (`ObjectOptimisticLockingFailureException`)
  - [x] `T4.7` `maven-failsafe-plugin` thêm vào `pom.xml`, `*IT.java` chạy tách khỏi `mvn test`
- [x] **T5 – Dọn Dẹp Chất Lượng** — hoàn thành 2026-07-25, gộp cùng T4 (`NEXT_PHASE_PLAN.md` D13)
  - [x] `T5.1` `WipTransactionServiceTest` dùng `WorkOrderExecutionSupport` thật thay vì mock
  - [x] `T5.2` `UserDetailsServiceImplTest` đổi tên method tautology + ghi rõ filtering thật ở `UserRoleAssignmentRepositoryIT`
  - [x] `T5.3` `InventoryServiceTest.java` → `InventoryMovementServiceTest.java`
  - [x] `T5.4` `SecurityFilterChainTest` dùng `BusinessErrorCode.RATE_LIMIT_EXCEEDED.code()` thay vì `"BIZ_100"`
  - [x] `T5.5` Rà 71 chỗ `isInstanceOf(AppException.class)` toàn `src/test` — phát hiện + sửa 2 chỗ thiếu assert `ErrorCode` (`WorkOrderReleaseGateTest`, `WorkOrderReadinessServiceTest`)

> Phase đang chạy luôn nằm ở `NEXT_PHASE_PLAN.md` (mỗi lần **đúng một** phase).
> Tick xong một phase ở đây ⇒ cập nhật `MANUFACTURING_GAP_ROADMAP.md §2.1` và ghi prompt phase kế tiếp.

> **Con số ở khối §0 là baseline lúc kết thúc `T5` (2026-07-25) và không được cập nhật theo từng
> phase nghiệp vụ.** Nguồn sự thật hiện tại luôn là `CLAUDE.md §0.1` — sau `D11` (2026-07-31) là
> **516 case unit / 86 class + 30 case IT / 6 class IT**, failures = 0.
>
> **Mô tả `FlywayMigrationIT` ở `T4.2` / `I7` / bảng §5 nay đã lỗi thời** (chúng ghi "1 case, `V1..V24`,
> dừng đúng version 24"): class này giờ có **3 case** và pin `V37` — `D4` thêm case backfill `V36`,
> `D6` thêm case chứng minh `V37` cho phép cùng `Idempotency-Key` ở 2 `movement_type` khác nhau. Các
> dòng đó **giữ nguyên** làm bản ghi lịch sử của `T4`, không viết đè.
>
> **`T4.4` nay đã lỗi thời:** nợ kỹ thuật #1 của `module/inventory` (implicit join `b.lot.status`)
> **đã được sửa** ở `F1.6`; `StockBalanceRepositoryIT` không còn pin hành vi sai mà là regression
> guard cho hành vi đúng. `F5-B` thêm case thứ tư vào class này
> (`aggregateExcludedLotCounts_countsOnlyNonAvailableLotsHoldingStock`).

---

## 1. Baseline Đã Đo (2026-07-24)

```text
mvn -o test  → BUILD SUCCESS (exit 0)
Test class   : 44
Test case    : 180        (failures=0, errors=0, skipped=0)
Thời gian    : ~8s  (chậm nhất: SecurityFilterChainTest 3.9s)
Coverage tool: KHÔNG CÓ (chưa cấu hình JaCoCo)
```

### 1.1 Coverage Baseline – đo bằng JaCoCo 0.8.12 (T0, 2026-07-24)

Đo ngay sau khi thêm plugin, **trước** khi sửa bất kỳ test nào của T1.

```text
Toàn dự án      line 62.3%  (3254/5224)      branch 48.9%  (697/1425)
module/**/service + **/security
                line 62.1%  (1909/3075)      branch 48.9%  (529/1081)
```

| Package | Line % |
|---|---:|
| `module.workorder.service.query` | 82.4% |
| `module.planning.service` | 80.7% |
| `module.workorder.service.execution` | 79.2% |
| `module.workorder.service` | 70.9% |
| `module.inventory.service` | 61.3% |
| `module.purchasing.service` | 57.1% |
| `module.organization.service` | 51.8% |
| `module.auth.service` | 49.5% |
| `module.bom.service` | 41.9% |
| `module.user.service` | 15.3% |

> Hai package thấp nhất (`auth.service` 49.5%, `user.service` 15.3%) chính là vùng `T3` nhắm tới.
> Report sinh tại `target/site/jacoco/index.html` (+ `jacoco.csv` để so sánh bằng script).

**Mọi phase dưới đây phải kết thúc với `test case ≥ 180` và `failures = 0`.**
Được **sửa** test cũ cho khớp nghiệp vụ mới, **không được xoá** test hay nới assertion để cho qua.

> **Cập nhật sau T0+T1 (đã hoàn thành):** 44 class / 180 case → **56 class / 218 case**, failures = 0.
> Coverage line 62.3% → **63.9%**, branch 48.9% → **49.7%**.
> `T0`, `T1.1`, `T1.2`, `T1.3`, `T1.4`, `T1.5` ✅. Nghiệm thu mutation trên 3 service đều đỏ;
> nghiệm thu ngược `PermissionCatalogTest` (inject `PERM_FAKE_XYZ`) → đỏ, liệt kê đúng tên permission.
> **Không** đụng `src/main` (trừ P1 đang dở); **không** migration mới.

#### Cập nhật sau T3 (2026-07-25)

51 class / 218 case → **53 class / 257 case** (+2 class, +39 case), failures = 0.

```text
Toàn dự án      line 63.9% → 66.7%       branch 49.7% → 51.6%
```

| Đơn vị | Line % trước | Line % sau | Branch % sau |
|---|---:|---:|---:|
| `module.auth.service` (package) | 49.5% | **97.8%** | 89.3% |
| `common.security` (package) | — | **79.1%** | 65.0% |
| `AuthService` | — | 97.8% (89/91) | 89.3% (25/28) |
| `TokenStoreService` | 0% | 90.3% (56/62) | 77.8% (14/18) |
| `JwtTokenProvider` | 0% | 90.0% (45/50) | n/a (class không có branch) |

> `module.user.service` vẫn 16.9% — `UserDetailsServiceImpl` phụ thuộc JPQL RBAC, thuộc `T4.3`.
> **Không** đụng `src/main` trong T3 (`find src/main -newermt` rỗng); migration mới nhất vẫn `V24`.

#### Cập nhật sau T2 (2026-07-25)

53 class / 257 case → **57 class / 281 case** (+4 class, +24 case), failures = 0.

```text
Toàn dự án      line 66.7% → 68.1%       branch 51.6% → 52.4%
```

| Đơn vị | Line % | Ghi chú |
|---|---:|---|
| `GlobalExceptionHandler` | 90.6% (29/32) | Cả 6 nhánh exception + `X-Trace-Id` đều có test |
| `AuthController` | 76.9% (10/13) | 7 case: login (4 nhánh) + logout + refresh + logout-all |
| `WorkOrderController` | 33.3% (4/12) | Chỉ 5 trong 9 endpoint có test đại diện (create/release/get/issueComponent) — theo phạm vi T2 (không phải toàn bộ 18 controller, `.claude/rules` §7) |
| `InventoryController` | 50.0% (4/8) | Chỉ `receive`/`issue`/`balances` có test đại diện; `adjust`/`listMovements` chưa |

> Coverage `WorkOrderController`/`InventoryController` không đạt 100% **có chủ đích** — T2 chỉ khoá
> pattern (envelope + idempotency + lỗi nghiệp vụ) trên các endpoint đại diện, không phải audit toàn
> bộ 18 controller (xem `NEXT_PHASE_PLAN.md` T2 §11 "Không Làm Trong Phase Này").
> **Không** đụng `src/main` trong T2 (`find src/main -newermt '-3 hours'` chỉ có các file `CLAUDE.md`,
> không có file `.java`); migration mới nhất vẫn `V24`.

#### Cập nhật sau T4 + T5 (2026-07-25)

57 class / 281 case (unit, không đổi — T5 chỉ sửa/đổi tên test hiện có, không thêm case mới) +
**3 class IT / 10 case IT mới** (`mvn verify`, tách khỏi `mvn test`), failures = 0.

```text
Coverage đo bằng `mvn test` (unit only — JaCoCo report bind vào phase `test`, KHÔNG bao gồm *IT.java
chạy qua Failsafe ở phase `verify`; xem giới hạn đã ghi ở NEXT_PHASE_PLAN.md §9 Prompt 6 mục 4):

Toàn dự án      line 68.1% → 69.2%       branch 52.4% → 52.7%
```

| Việc | Kết quả |
|---|---|
| `FlywayMigrationIT` | 1 case — chạy sạch `V1..V24` trên Postgres 16 container trống, dừng đúng version `24` |
| `UserRoleAssignmentRepositoryIT` | 6 case — bịt `I6`/bất biến B32; nghiệm thu mutation (đảo `>` thành `<` trên điều kiện `expiresAt`) → đúng 1/6 case đỏ, đã revert |
| `StockBalanceRepositoryIT` | 3 case — 1 case optimistic locking pass; **2 case phát hiện bug thật** trong JPQL aggregate (xem dưới) |
| `pom.xml` | Thêm `maven-failsafe-plugin` (version tự quản lý qua Spring Boot parent, không cần khai báo tường minh) |

> **Bug thật phát hiện, KHÔNG sửa trong phase này (quyết định của user khi được hỏi trực tiếp):**
> `StockBalanceRepository.aggregateAvailableQuantities`/`aggregatePlanningQuantities`/
> `aggregateAvailableQuantitiesByWarehouse` dùng path expression `b.lot.status` trong JPQL, khiến
> Hibernate sinh **INNER JOIN** tới `inventory_lots` thay vì tôn trọng nhánh `b.lot is null` — mọi
> `StockBalance` không lot-tracked (`lot_id IS NULL`) bị loại khỏi kết quả, vi phạm bất biến B3. Ghi
> đầy đủ ở `module/inventory/CLAUDE.md` "Nợ Kỹ Thuật Đã Biết #1"; `StockBalanceRepositoryIT` pin lại
> hành vi sai hiện tại (có giải thích trong Javadoc + comment), **không** tự sửa `src/main`.

**Không** đụng `src/main` ngoài `pom.xml` (D12); migration mới nhất vẫn `V24` (không phát hiện migration
lỗi ở `FlywayMigrationIT`).

---

## 2. Tổng Hợp Vấn Đề Đã Phát Hiện

| # | Vấn đề | Mức độ | Bằng chứng | Phase xử lý |
|---|---|---|---|---|
| **I1** | ✅ **ĐÃ XỬ LÝ (T1)** – 10 class `*MethodSecurityTest` không phát hiện sai tên permission | 🔴 Nghiêm trọng | Đã thêm `verify(<guard>)` khoá `PERM_*` cho 22 assertion + nhánh allow + `PermissionCatalogTest`. Mutation test lại → **đỏ** | ~~T1~~ ✅ |
| **I2** | ✅ **ĐÃ XỬ LÝ (T2)** – `GlobalExceptionHandler` không có test | 🔴 Nghiêm trọng | `GlobalExceptionHandlerTest` (6 case) khoá cả 6 nhánh handler bằng `ErrorCode.getCode()`, kèm test 500 không lộ message/stacktrace gốc | ~~T2~~ ✅ |
| **I3** | ✅ **ĐÃ XỬ LÝ HẾT (T2 → `D7` → `D7b`)** – Controller không có test nào | 🔴 Nghiêm trọng | `T2`: 3 controller đại diện (`Auth`, `WorkOrder`, `Inventory`). `D7` (2026-07-30): **8** class nhóm A = 34 case ⇒ 11/20. **`D7b` (2026-07-31)**: thêm **9** class nhóm B+C (`Item`, `ItemWarehouseSetting`, `Supplier`, `User`, `Organization`, `AccessControl`, `InventoryReport`, `Planning`, `PlanningDemand`) = **60 case** ⇒ **20/20** controller có test envelope. Nợ #2 đóng. ⚠️ Tổng là **20** controller, không phải 18/19 như các bản trước ghi | ~~T2~~ + ~~`D7`~~ + ~~`D7b`~~ ✅ |
| **I4** | ✅ **ĐÃ XỬ LÝ (T3)** – `AuthService.refresh` / `logout` / `logoutAll` không có test | 🔴 Nghiêm trọng | `AuthServiceTest` 6 → 15 case: rotation verify **cả** `deleteRefreshToken(old)` lẫn `saveRefreshToken(new)`; 3 nhánh `REFRESH_TOKEN_EXPIRED` đều `never().saveRefreshToken`; logout blacklist theo TTL còn lại | ~~T3~~ ✅ |
| **I5** | ✅ **ĐÃ XỬ LÝ (T3)** – `TokenStoreService`, `JwtTokenProvider` không có test | 🟠 Cao | `TokenStoreServiceTest` (17 case) khoá key string + TTL + nhánh SCAN rỗng + `count==1` của fail-counter; `JwtTokenProviderTest` (13 case) khoá `TOKEN_EXPIRED`/`TOKEN_MALFORMED`. Redis **thật** vẫn chưa chạy → còn ở `T4` | ~~T3~~ ✅ |
| **I6** | ✅ **ĐÃ XỬ LÝ (T4)** – Logic phân quyền nằm trong JPQL nhưng **JPQL không bao giờ được chạy** | 🔴 Nghiêm trọng | `UserRoleAssignmentRepositoryIT` (6 case) chạy JPQL thật qua Testcontainers, khoá cả 4 status (assignment/role/permission/scope) + điều kiện `expiresAt`. Nghiệm thu mutation JPQL → đỏ đúng case | ~~T4~~ ✅ |
| **I7** | ✅ **ĐÃ XỬ LÝ (T4)** – 22 Flyway migration không có test | 🟠 Cao | `FlywayMigrationIT` chạy `V1..V24` sạch trên DB trống (Postgres 16 container), dừng đúng version 24 | ~~T4~~ ✅ |
| **I8** | ✅ **ĐÃ XỬ LÝ (T4)** – Testcontainers khai báo trong `pom.xml` nhưng **0 file sử dụng** | 🟠 Cao | `AbstractPostgresIntegrationTest` (Singleton Container pattern) + 3 class `*IT.java` dùng thật, `maven-failsafe-plugin` thêm vào `pom.xml` để chạy tách khỏi `mvn test` | ~~T4~~ ✅ |
| **I9** | ✅ **ĐÃ XỬ LÝ (T2)** – `spring-security-test` khai báo nhưng **0 lần dùng `@WithMockUser`** | 🟡 Trung bình | `GlobalExceptionHandlerTest` (class-level) + `AuthControllerTest.logout_validRequest_returns200NoContent` dùng `@WithMockUser` | ~~T2~~ ✅ |
| **I10** | ✅ **ĐÃ XỬ LÝ (T0)** – Không có JaCoCo | 🟡 Trung bình | Đã thêm `jacoco-maven-plugin 0.8.12` (prepare-agent + report, không `check`). Baseline line 62.3% / branch 48.9% ghi ở §1.1 | ~~T0~~ ✅ |
| **I11** | ✅ **ĐÃ XỬ LÝ (T5)** – `WipTransactionServiceTest` mock `WorkOrderExecutionSupport` (helper chứa logic thật) | 🟡 Trung bình | Đổi sang instance thật `new WorkOrderExecutionSupport(...)` giống 3 file kia; phát hiện thêm 1 test thiếu `WorkOrderStatus.RELEASED` trên fixture (đã sửa) | ~~T5~~ ✅ |
| **I12** | ✅ **ĐÃ XỬ LÝ (T5)** – Tên test hứa nhiều hơn nội dung | 🟡 Trung bình | `UserDetailsServiceImplTest` đổi tên method + ghi rõ trong Javadoc rằng filtering thật được test ở `UserRoleAssignmentRepositoryIT` (T4.3) | ~~T5~~ ✅ |
| **I13** | ✅ **ĐÃ XỬ LÝ (T5)** – Tên class sai đối tượng test | 🟢 Thấp | Đổi tên file + class `InventoryServiceTest` → `InventoryMovementServiceTest` | ~~T5~~ ✅ |
| **I14** | ✅ **ĐÃ XỬ LÝ (T5)** – Assert magic string thay vì enum | 🟢 Thấp | `SecurityFilterChainTest` đổi sang `BusinessErrorCode.RATE_LIMIT_EXCEEDED.code()` | ~~T5~~ ✅ |
| **I15** | ✅ **ĐÃ XỬ LÝ (T1.5)** – Bất biến **B13** chưa được bảo vệ | 🟠 Cao | Đã thêm `post_onBlockedWorkOrder_shouldThrow` (issue + receipt) và `reserve_onBlockedWorkOrder_shouldThrow`, mỗi test assert `OPERATION_NOT_ALLOWED` + `verifyNoInteractions(movementService)` | ~~T1.5~~ ✅ |

### Số liệu định lượng khoảng trống

```text
@PreAuthorize trong src/main          : 121 biểu thức
Permission code phân biệt             :  32
Permission có method-security test    :  17  (53%)
Permission KHÔNG có test              :  15  → xem danh sách ở T1.3
Method-security assertion deny-only   :  21 / 22   (chỉ OrganizationMethodSecurityTest có allow path)
Method-security assertion verify guard :   0 / 22
```

---

## 3. Phân Loại Test Hiện Có (giữ / sửa / bổ sung)

| Nhóm | Số class | Đánh giá | Hành động |
|---|---|---|---|
| `*ServiceTest` nghiệp vụ (BOM, MRP, Inventory, WorkOrder, Purchasing, Planning) | 22 | ✅ **Chất lượng cao** – assert con số nghiệp vụ thật, dùng mapper/helper thật, phủ đúng bất biến rủi ro | **Giữ nguyên**, chỉ bổ sung case mới khi thêm nghiệp vụ |
| `*PermissionGuardTest` | 4 | 🟡 Đúng hướng nhưng logic thật nằm trong JPQL không được chạy | Bổ sung `@DataJpaTest` ở **T4**, giữ nguyên unit test |
| `*MethodSecurityTest` | 10 | 🔴 **Tạo cảm giác an toàn giả** – xem I1 | Sửa ở **T1** (không xoá, chi phí chạy chỉ ~3s) |
| Hạ tầng security (`IpExtractorTest`, `SecurityFilterChainTest`) | 2 | ✅ Tốt, có giá trị bảo mật đo được | Giữ nguyên, chỉ sửa magic string ở T5 |
| `AuthServiceTest` | 1 | ✅ Đã mở rộng ở **T3** – phủ đủ `login` / `refresh` / `logout` / `logoutAll` (15 case) | Giữ nguyên |
| `GlobalExceptionHandlerTest` [NEW T2] | 1 | ✅ Khoá cả 6 nhánh handler + `X-Trace-Id`, dùng controller test-only (`ExceptionTestController`) | Giữ nguyên |
| `AuthControllerTest` / `WorkOrderControllerTest` / `InventoryControllerTest` [NEW T2] | 3 | ✅ `@WebMvcTest` đại diện — khoá envelope + idempotency header forward + lỗi nghiệp vụ | Giữ nguyên (`D7` chỉ đổi **tên** 1 method của `InventoryControllerTest` vốn ghi "422" từ trước `F5`) |
| 8 `*ControllerTest` nhóm A [NEW `D7`] | 8 (34 case) | ✅ `ManufacturingExecution` / `SalesOrder` / `PurchaseOrder` / `GoodsReceipt` / `PurchaseRequisition` / `PlanningRun` / `Routing` / `Bom` — mỗi class ≥ 1 nhánh lỗi assert `$.code` bằng hằng `ErrorCode` + ≥ 1 nhánh success. Nghiệm thu mutation trên 2 class (đổi mã **cùng** HTTP status ⇒ đỏ) | Giữ nguyên; `D7b` dùng làm khuôn cho 9 class nhóm B+C |
| `AbstractPostgresIntegrationTest` [NEW T4] | 1 (base class, không có `@Test`) | ✅ Singleton Container pattern, dùng chung cho mọi `*IT.java` | Giữ nguyên |
| `FlywayMigrationIT` / `UserRoleAssignmentRepositoryIT` / `StockBalanceRepositoryIT` [NEW T4] | 3 IT | ✅ Chạy JPQL/migration thật qua Testcontainers — bịt `I6`/`I7`/`I8`. `StockBalanceRepositoryIT` phát hiện 1 bug thật (xem `module/inventory/CLAUDE.md`), pin lại hành vi hiện tại theo quyết định của user | Giữ nguyên, sửa lại 2 assertion khi bug được vá ở commit riêng |
| Tổng | **44** (baseline đầu plan) | | |

---

## 4. Các Phase

### T0 – Tooling & Baseline  ⏱️ ~30 phút  ✅ **XONG (2026-07-24)**

**Mục tiêu:** có số đo coverage để mọi phase sau chứng minh được tiến bộ.

| # | Việc |
|---|---|
| T0.1 | Thêm `jacoco-maven-plugin` vào `pom.xml` (`prepare-agent` + `report` gắn vào phase `test`) |
| T0.2 | Chạy `mvn test` → lưu lại số coverage baseline (line % + branch %) vào file này, mục 1 |
| T0.3 | Thêm `target/site/jacoco/` vào `.gitignore` nếu chưa có |

**DoD**
- [x] `mvn test` sinh được `target/site/jacoco/index.html`
- [x] Baseline line/branch coverage được ghi lại (§1.1)
- [x] Không đặt `check` rule fail build ở phase này (tránh chặn công việc nghiệp vụ đang chạy)

---

### T1 – Cứu 10 class Method-Security  ⏱️ ~1–2 giờ  ⏳ **ĐANG CHẠY** (còn `T1.4`, `T1.5`)

**Mục tiêu:** biến 22 assertion vô hiệu thành assertion thật, không viết lại từ đầu.

#### Vấn đề gốc

```java
// Hiện tại – KHÔNG phát hiện được sai tên permission
when(permissionGuard.hasResourceAccess(any(), eq("PERM_BOM_MANAGE"), eq("COMPANY"), eq(companyId)))
        .thenReturn(false);
assertThatThrownBy(() -> bomService.createBom(...)).isInstanceOf(AccessDeniedException.class);
```
Nếu code dùng permission khác, stub không khớp → mock trả default `false` → vẫn ném `AccessDeniedException` → **test vẫn xanh**.

#### Cách sửa bắt buộc — mỗi method phải có **đủ 3 thành phần**

```java
// 1) Nhánh DENY  – chứng minh có chặn và không rò rỉ xuống repository
@Test
void createBom_deniedWhenCompanyScopeMissing() {
    when(permissionGuard.hasResourceAccess(any(), eq("PERM_BOM_MANAGE"), eq("COMPANY"), eq(companyId)))
            .thenReturn(false);

    assertThatThrownBy(() -> bomService.createBom(companyId, request))
            .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(itemLookupService);
    // 2) VERIFY  ← BẮT BUỘC: khoá chặt đúng permission string + đúng scope type
    verify(permissionGuard).hasResourceAccess(any(), eq("PERM_BOM_MANAGE"), eq("COMPANY"), eq(companyId));
}

// 3) Nhánh ALLOW – chứng minh có quyền thì đi được vào business logic
@Test
void createBom_allowedWhenCompanyScopePresent() {
    when(permissionGuard.hasResourceAccess(any(), eq("PERM_BOM_MANAGE"), eq("COMPANY"), eq(companyId)))
            .thenReturn(true);
    // ...stub tối thiểu để method chạy xong...
    assertThatCode(() -> bomService.createBom(companyId, request)).doesNotThrowAnyException();
}
```

> **Quy tắc nghiệm thu T1:** sau khi sửa, đổi bất kỳ chuỗi permission nào trong `@PreAuthorize`
> tương ứng thành giá trị rác → test **phải đỏ**. Nếu vẫn xanh là chưa đạt.

#### Việc cần làm

| # | Việc | File |
|---|---|---|
| T1.1 | Thêm `verify(<guard>).hasResourceAccess/hasXxxAccess(...)` vào **toàn bộ 22 assertion deny** hiện có | 10 file `*MethodSecurityTest.java` |
| T1.2 | Thêm nhánh **allow** cho ít nhất **1 method mỗi service** (mẫu có sẵn: `OrganizationMethodSecurityTest:62`) | 9 file (Organization đã có) |
| T1.3 | Bổ sung method-security test cho **15 permission chưa được phủ**:<br>`PERM_ACCESS_MANAGE`, `PERM_ORG_MANAGE`, `PERM_GOODS_RECEIPT_POST`, `PERM_PURCHASE_ORDER_MANAGE`, `PERM_PURCHASE_ORDER_READ`, `PERM_PURCHASE_REQUISITION_MANAGE`, `PERM_PURCHASE_REQUISITION_READ`, `PERM_SUPPLIER_READ`, `PERM_SUPPLY_SUGGESTION_MANAGE`, `PERM_MRP_READ`, `PERM_PLANNING_READ`, `PERM_PLANNING_DEMAND_MANAGE`, `PERM_PLANNING_DEMAND_READ`, `PERM_MATERIAL_ISSUE_OVERRIDE`*, `PERM_INVENTORY_*` còn thiếu | các `*MethodSecurityTest` tương ứng |
| T1.4 | Viết 1 test "permission catalog" chống lệch code ↔ DB: quét mọi chuỗi `PERM_*` trong `@PreAuthorize` và assert tất cả đều tồn tại trong Flyway seed | `PermissionCatalogTest.java` [NEW] |
| T1.5 | **Bù test còn thiếu của P1**: WO ở status `BLOCKED` **không** issue / receipt / WIP được.<br>P1 test plan có yêu cầu `release_blockedWorkOrder_cannotIssueMaterial()` nhưng test này **chưa được viết** — hiện chỉ có test chứng minh WO *chuyển sang* `BLOCKED`, chưa có test chứng minh `BLOCKED` *bị chặn thực thi* (bất biến **B13** trong `src/main/java/com/erp/manufacturing/module/workorder/CLAUDE.md` §10.3) | `WorkOrderServiceTest`, `MaterialIssueServiceTest`, `ProductionReceiptServiceTest` |

> \* `PERM_MATERIAL_ISSUE_OVERRIDE` đã được test **đúng cách** trong `MaterialIssueServiceTest`
> (guard gọi programmatic, có cả nhánh `true` và `false`) → chỉ cần bổ sung vào catalog test T1.4.

**DoD**
- [x] Mọi assertion deny đều kèm `verify(<guard>)` với `eq("PERM_...")` tường minh (21/21)
- [x] Mỗi `*MethodSecurityTest` có ≥ 1 nhánh allow (10/10 class)
- [x] Mutation test thủ công trên 3 service bất kỳ → test đỏ (Bom/Supplier/Inventory)
- [x] `PermissionCatalogTest` pass; nếu thêm permission mới mà quên seed → test đỏ (đã nghiệm thu ngược)
- [x] Tổng test case tăng, không giảm (180 → 218)

---

### T2 – Tầng Contract: Exception Handler & Controller  ⏱️ ~1 ngày  ✅ **XONG (2026-07-25)**

**Mục tiêu:** khoá contract `{code, result, message}` mà frontend phụ thuộc (checklist **T5**, **T3**, **T6** của `.claude/rules/best-practices.md` §8.6).

| # | Việc | File |
|---|---|---|
| T2.1 | `GlobalExceptionHandlerTest` (`@WebMvcTest` + controller giả ném từng loại exception).<br>Phủ: `AppException` (theo `httpStatus`), `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED`, `ConstraintViolationException` → 400, `AccessDeniedException` → 403 `ACCESS_DENIED`, `DataIntegrityViolationException` → 409, `Exception` → 500.<br>**Bắt buộc assert cả 3 field** `code` / `result` / `message`, và assert response **không chứa stack trace** | `common/exception/GlobalExceptionHandlerTest.java` [NEW] |
| T2.2 | `@WebMvcTest` cho **3 controller đại diện** (không cần cả 18): `WorkOrderController`, `InventoryController`, `AuthController`.<br>Mỗi controller phủ: 200 happy path, 400 validation, 401 chưa auth, 403 thiếu quyền, đúng HTTP status theo bảng §5.8 `.claude/rules/error-handling.md` (POST→201, DELETE→200 `noContent`) | 3 file `*ControllerTest.java` [NEW] |
| T2.3 | Dùng `@WithMockUser` / `@WithMockUser(authorities = "PERM_...")` thay cho set `SecurityContextHolder` thủ công trong các test mới | — |
| T2.4 | Test `Idempotency-Key` header ở tầng controller cho ≥ 1 POST nhạy cảm (goods receipt hoặc production receipt) | `*ControllerTest.java` |
| T2.5 | Test header chuẩn: `X-Trace-Id` luôn có mặt trong response (§5.6 `.claude/rules/error-handling.md`) | `GlobalExceptionHandlerTest` |

**DoD**
- [x] Mọi test mới assert `$.code` bằng **enum**, không bằng chuỗi literal
- [x] Có test chứng minh 500 **không** lộ stack trace ra client
- [x] `@WithMockUser` được sử dụng ≥ 1 lần (kích hoạt `spring-security-test` đã khai báo)
- [x] Không thêm `@SpringBootTest` full-context (giữ test pyramid, dùng `@WebMvcTest`)

> **Kết quả T2:** `GlobalExceptionHandlerTest` (6) + `AuthControllerTest` (7) + `WorkOrderControllerTest` (6)
> + `InventoryControllerTest` (5). Tổng 257 → **281 case**, failures = 0. Coverage: xem §1.1 "Cập nhật sau T2".
> **Phát hiện quan trọng (không phải bug, ghi lại cho phase sau):** `@WebMvcTest` trong repo này
> auto-detect **mọi** bean `Filter` trên classpath (không chỉ filter được `@Import` tường minh), vì
> `ManufacturingErpApplication` nằm ở gốc `com.erp.manufacturing` nên `JwtAuthenticationFilter` /
> `RateLimitFilter` / `UserRateLimitFilter` / `TraceIdFilter` đều bị Spring dựng lên trong MỌI
> `@WebMvcTest`, dù không `@Import`. `@AutoConfigureMockMvc(addFilters = false)` chỉ tắt việc áp filter
> vào request — **không** ngăn Spring khởi tạo bean, nên vẫn cần `@MockBean` đủ
> `IpExtractor`/`JwtTokenProvider`/`TokenStoreService`/`UserDetailsService`/`RedisTemplate`/`RateLimitProperties`
> ở cả 3 `*ControllerTest` (không chỉ `GlobalExceptionHandlerTest`), nếu không context load sẽ lỗi
> `NoSuchBeanDefinitionException`. Không có base test-security-config class dùng chung (D2.8) → mỗi
> `@WebMvcTest` mới thêm sau này (kể cả ở `T4`/`P2`) cần lặp lại 6 `@MockBean` này.

---

### T3 – Auth & Security Core  ⏱️ ~1 ngày  ✅ **XONG (2026-07-25)** (trừ `T3.6`)

**Mục tiêu:** phủ phần được thiết kế kỹ nhất trong `common/security/CLAUDE.md` (§4.2, §4.7, §4.8) nhưng chưa có test nào.

| # | Việc | Trường hợp bắt buộc |
|---|---|---|
| T3.1 | Mở rộng `AuthServiceTest` cho `refresh()` | • rotation thành công → trả access + refresh **MỚI**, refresh cũ bị xoá khỏi store<br>• refresh token không tồn tại trong Redis → `REFRESH_TOKEN_EXPIRED`<br>• refresh token rỗng/null → lỗi rõ ràng<br>• device session được gia hạn TTL |
| T3.2 | `AuthServiceTest` cho `logout()` | • access token vào blacklist với TTL = thời gian còn lại<br>• refresh token của đúng `tokenId` bị xoá<br>• **không** ảnh hưởng thiết bị khác |
| T3.3 | `AuthServiceTest` cho `logoutAll()` | • xoá **toàn bộ** `auth:refresh:{userId}:*` và `auth:session:device:{userId}:*` |
| T3.4 | `TokenStoreServiceTest` | • `incrementFailCount` atomic (Lua) → key có TTL đúng<br>• `resetFailCount` xoá key<br>• `isBlacklisted` đúng cho jti đã/chưa blacklist<br>• `deleteAllUserTokens` xoá đúng pattern, không đụng user khác |
| T3.5 | `JwtTokenProviderTest` | • token sinh ra có đủ `sub`, `jti`, `roles`, `iat`, `exp`<br>• **không** chứa dữ liệu nhạy cảm (không có password/email)<br>• chữ ký sai → ném đúng exception<br>• token hết hạn → `TOKEN_EXPIRED`<br>• `jti` là duy nhất giữa 2 lần sinh |
| T3.6 | Test log sanitization (`common/security/CLAUDE.md` §4.18) | • `LoginRequest.toString()` **không** chứa password<br>• `RefreshRequest.toString()` không chứa raw token<br>⚠️ **HOÃN** – viết đúng thì test sẽ đỏ vì đây là lỗ hổng §4.18 **thật** trong `src/main` (record thuần ⇒ `toString()` tự sinh lộ dữ liệu). Sửa được đòi hỏi override `toString()` trong `src/main`, mâu thuẫn `D7` của phase test ⇒ chuyển thành nợ kỹ thuật `CLAUDE.md §0.4 #7` |

**DoD**
- [x] Mọi nhánh lỗi assert đúng `AuthErrorCode` cụ thể (không chỉ `isInstanceOf(AppException.class)`)
- [x] Có test chứng minh account enumeration prevention vẫn giữ (login sai user vs sai password → cùng code) — giữ nguyên case cũ, không nới assertion
- [x] Redis được mock ở T3, **không** cần Testcontainers (để dành T4)

> **Kết quả T3:** `JwtTokenProviderTest` (13) + `TokenStoreServiceTest` (17) + `AuthServiceTest` (6 → 15).
> Tổng 218 → **257 case**, failures = 0. Coverage: xem §1.1 "Cập nhật sau T3".
> Không sửa `src/main`, không migration mới.

---

### T4 – Tầng Persistence: JPQL, RBAC, Flyway  ⏱️ ~1.5 ngày  ✅ **XONG (2026-07-25)**

**Mục tiêu:** chạy thật các câu JPQL đang **chứa logic bảo mật** nhưng chưa bao giờ được thực thi trong test.

> **Tại sao quan trọng:** `PermissionGuardTest` stub toàn bộ `existsActivePermissionInScopeType(...)`.
> Điều kiện hết hạn (`expiresAt`), `AssignmentStatus.ACTIVE`, `RoleStatus.ACTIVE` nằm **bên trong JPQL**.
> Nếu JPQL sai điều kiện hết hạn, **role đã thu hồi vẫn còn hiệu lực trên production** và không test nào phát hiện.
>
> **Đính chính so với mô tả gốc (viết trước khi đọc code chi tiết — xem `NEXT_PHASE_PLAN.md` §2.2/§2.5):**
> `UserRoleAssignment` không có `validFrom`/`validTo`, chỉ có 1 field `expiresAt` (nullable). Cũng
> **không tồn tại** cơ chế `@Where(deleted_at IS NULL)` soft-delete trong code — T4.5 (bên dưới) vì vậy
> **bị bỏ khỏi scope**, không phải bug, chỉ là mô tả sai trong bản kế hoạch gốc.

| # | Việc | File | Kết quả |
|---|---|---|---|
| T4.1 | `AbstractPostgresIntegrationTest` — Testcontainers (`postgres:16-alpine`), **Singleton Container pattern** (start 1 lần trong `static {}`, không `@Container`/`stop()`, dùng `@DynamicPropertySource` thay vì `@ServiceConnection` để không thêm dependency mới) | `common/AbstractPostgresIntegrationTest.java` [NEW] | ✅ |
| T4.2 | `FlywayMigrationIT` (đặt tên `*IT.java`, không phải `*Test.java`, để Failsafe pick up đúng — xem T4.7) – khởi động container trống, chạy toàn bộ `V1..V24`, assert `flyway.info().current()` = version `24` và không có migration `pending`/`failed` | `common/FlywayMigrationIT.java` [NEW] | ✅ 1 case, chạy sạch |
| T4.3 | `UserRoleAssignmentRepositoryIT` (`@DataJpaTest` + Testcontainers) — **quan trọng nhất**, dùng đúng field `expiresAt` thật (không phải validFrom/validTo):<br>• `expiresAt` trong quá khứ → **không** trả<br>• `AssignmentStatus.INACTIVE` → **không** trả<br>• `RoleStatus.INACTIVE` → **không** trả<br>• `Permission.status = INACTIVE` → **không** trả<br>• `AccessScope.status = INACTIVE` → **không** trả<br>• tất cả `ACTIVE` + `expiresAt = null` → **có** trả | `organization/repository/UserRoleAssignmentRepositoryIT.java` [NEW] | ✅ 6 case; nghiệm thu mutation (đảo `>`→`<` trên `expiresAt`) → đúng 1/6 case đỏ, đã revert |
| T4.4 | `StockBalanceRepositoryIT` – kiểm chứng aggregate query đang được `InventoryAvailabilityService` mock | `inventory/repository/StockBalanceRepositoryIT.java` [NEW] | ✅ 2 case — **phát hiện bug thật** (xem dưới), pin lại hành vi hiện tại theo quyết định của user |
| ~~T4.5~~ | ~~Kiểm chứng `@Where(deleted_at IS NULL)` soft-delete~~ | — | **Bỏ khỏi scope** — cơ chế không tồn tại trong code (xem đính chính ở trên) |
| T4.6 | Optimistic locking thật: 2 bản copy cùng `@Version`, save 1 bản trước → bản kia (stale) ném `ObjectOptimisticLockingFailureException`. Entity đại diện: `StockBalance` (không phải `WorkOrder` — lý do: bị update đồng thời bởi issue/receive/reserve, kịch bản contention thực tế hơn) | `StockBalanceRepositoryIT.java` | ✅ 1 case |
| T4.7 | Thêm `maven-failsafe-plugin` vào `pom.xml` (version tự quản lý qua Spring Boot parent). `*IT.java` khớp include pattern mặc định của Failsafe, `*Test.java` bị Surefire loại trừ mặc định — không cần override include/exclude thủ công | `pom.xml` | ✅ |

**Bug thật phát hiện ở T4.4 (không sửa trong phase này — quyết định của user):**
`StockBalanceRepository.aggregateAvailableQuantities`/`aggregatePlanningQuantities`/
`aggregateAvailableQuantitiesByWarehouse` dùng path expression `b.lot.status` trong JPQL, khiến Hibernate
sinh **INNER JOIN** tới `inventory_lots` thay vì tôn trọng nhánh `b.lot is null` — mọi `StockBalance`
không lot-tracked bị loại khỏi kết quả, vi phạm bất biến B3. Ghi thành nợ kỹ thuật ở
`module/inventory/CLAUDE.md` "Nợ Kỹ Thuật Đã Biết #1"; `StockBalanceRepositoryIT` pin lại hành vi sai
hiện tại (có giải thích trong Javadoc), sẽ sửa ở commit nghiệp vụ riêng.

**DoD**
- [x] `mvn test` (unit) vẫn nhanh như cũ — IT không chạy trong `mvn test` (chỉ Surefire, Failsafe tách riêng ở `mvn verify`)
- [x] `mvn verify` chạy thêm 10 case IT với Testcontainers thật (Docker)
- [x] Migration chạy sạch trên DB trống (`FlywayMigrationIT`); DB có dữ liệu `POSTED` cũ **chưa** kiểm — thuộc P1 DoD, ngoài scope T4
- [x] 6 test chứng minh assignment/role/permission/scope hết hạn/`INACTIVE` bị loại → bịt I6, nghiệm thu mutation xác nhận
- [x] Testcontainers dependency đã khai báo trong `pom.xml` được **sử dụng thật**

> **Kết quả T4:** `AbstractPostgresIntegrationTest` [NEW] + `FlywayMigrationIT` (1) + `UserRoleAssignmentRepositoryIT` (6) + `StockBalanceRepositoryIT` (3). Tổng **10 case IT mới**, chạy qua `mvn verify` — không cộng vào tổng "281 case" của `mvn test` (Surefire vs Failsafe report tách biệt, xem `NEXT_PHASE_PLAN.md` D6). Unit test case không đổi: **281**. Coverage: xem §1.1 "Cập nhật sau T4 + T5". Sửa `src/main` duy nhất: `pom.xml` (D12).

---

### T5 – Dọn Dẹp Chất Lượng  ⏱️ ~1 giờ  ✅ **XONG (2026-07-25)** — gộp cùng T4 (`NEXT_PHASE_PLAN.md` D13)

| # | Việc | File | Kết quả |
|---|---|---|---|
| T5.1 | Bỏ `@Mock WorkOrderExecutionSupport`, dùng `new WorkOrderExecutionSupport(...)` **thật** (đồng bộ với `MaterialIssueServiceTest` / `MaterialReservationServiceTest` / `ProductionReceiptServiceTest`) | `WipTransactionServiceTest.java` | ✅ — logic thật chạy phát hiện fixture thiếu `WorkOrderStatus.RELEASED` (test trước đó xanh "giả" vì mock không kiểm tra status), đã sửa fixture |
| T5.2 | Sửa test tautology: đổi tên cho đúng nội dung + trỏ sang nơi test thật | `UserDetailsServiceImplTest.java:76` | ✅ Đổi tên method, ghi Javadoc trỏ sang `UserRoleAssignmentRepositoryIT` (T4.3) |
| T5.3 | Đổi tên `InventoryServiceTest` → `InventoryMovementServiceTest` (khớp `@DisplayName` đã đúng) | — | ✅ Đổi tên file + class; cập nhật 2 file `CLAUDE.md` (inventory, workorder) tham chiếu tên cũ |
| T5.4 | Thay magic string `"BIZ_100"` bằng `BusinessErrorCode.RATE_LIMIT_EXCEEDED.code()` | `SecurityFilterChainTest.java:210` | ✅ (method thật là `.code()`, không phải `.getCode()`) |
| T5.5 | Rà lại mọi `assertThatThrownBy` chỉ assert `isInstanceOf(AppException.class)` mà thiếu assert `ErrorCode` | toàn bộ `src/test` | ✅ Rà 71 chỗ, phát hiện + sửa 2 chỗ thiếu (`WorkOrderReleaseGateTest.ensureMaterialReady_noReservation_shouldBlockAndThrow`, `WorkOrderReadinessServiceTest.materialReadiness_unknownWorkOrder_shouldThrowNotFound`) |

**DoD**
- [x] Không còn test nào mock một helper chứa logic nghiệp vụ thật
- [x] Không còn tên test hứa nhiều hơn nội dung
- [x] Mọi assert exception đều kèm `ErrorCode` cụ thể (71/71 chỗ `isInstanceOf(AppException.class)` đã verify)

---

## 5. Thứ Tự & Ước Lượng

```text
T0 (tooling)          30 phút   → làm ngay, không chặn ai
T1 (method security)   1–2 giờ   → 🔴 ƯU TIÊN 1: giá trị/công sức cao nhất
T3 (auth core)         1 ngày    → 🔴 ƯU TIÊN 2: rủi ro bảo mật
T2 (contract layer)    1 ngày    → ƯU TIÊN 3: giá trị cho frontend + báo cáo
T4 (persistence)       1.5 ngày  → ƯU TIÊN 4: bịt điểm mù JPQL, kích hoạt Testcontainers
T5 (dọn dẹp)           1 giờ     → làm cuối, gộp vào PR bất kỳ
```

> **T1 và T3 có thể làm song song với P2 (Quality Control).**
> **T4 nên làm TRƯỚC hoặc TRONG P2**, vì P2 thêm workflow lot `HOLD → AVAILABLE/REJECTED`
> — đúng vùng mà `StockBalanceRepositoryIT` (T4.4) bảo vệ. **Đã hoàn thành 2026-07-25.**

---

## 6. Definition of Done Toàn Bộ Plan

- [x] `mvn verify` PASS; unit test 281 case (≥ 180), không xoá test cũ; + 10 case IT mới
- [x] Mutation test: đổi bất kỳ chuỗi `PERM_*` nào trong `@PreAuthorize` → có test đỏ *(T1)*; đổi điều kiện JPQL RBAC → có test đỏ *(T4.3)*
- [x] `GlobalExceptionHandler` có test cho cả 6 nhánh exception *(T2)*
- [x] `AuthService.refresh` / `logout` / `logoutAll` có test *(T3)*
- [x] Có ≥ 1 integration test chạy JPQL RBAC thật với PostgreSQL *(T4.3, 6 case)*
- [x] Flyway `V1..V24` được xác minh bằng test *(T4.2, `FlywayMigrationIT`)*
- [x] JaCoCo báo cáo được coverage; ghi lại số trước/sau vào mục 1 *(§1.1 "Cập nhật sau T4 + T5")*
- [x] `Testcontainers` và `spring-security-test` đã khai báo đều được **dùng thật** *(T4 kích hoạt Testcontainers; `spring-security-test` đã dùng từ T2)*
- [x] `.claude/rules/best-practices.md` §8.6 (Testing checklist) khớp với thực tế code — cập nhật dòng T2 (Testcontainers)

> **Toàn bộ `TEST_IMPROVEMENT_PLAN.md` đã hoàn thành sau T4 + T5 (2026-07-25).** Không còn phase `T*`
> nào đang treo. Phase kế tiếp của dự án là `P2 – Quality Control` (`MANUFACTURING_GAP_ROADMAP.md`).
> 1 nợ kỹ thuật mới phát sinh trong quá trình này (bug JPQL `StockBalanceRepository`, xem §1.1) — chưa
> sửa, chờ commit nghiệp vụ riêng; không chặn việc coi plan này là "xong".

---

## 7. Không Làm Trong Plan Này

- Không viết `@WebMvcTest` cho cả 18 controller (chỉ 3 controller đại diện — tránh test trùng lặp rẻ tiền)
- Không đặt ngưỡng JaCoCo fail build (sẽ chặn công việc nghiệp vụ đang chạy)
- Không thêm `@SpringBootTest` full context (phá test pyramid, làm suite chậm)
- Không viết test cho getter/setter/mapper thuần
- Không đưa mutation testing tool (PIT) vào build — mutation test ở đây làm **thủ công** khi nghiệm thu T1
- Không refactor code `src/main` ngoài phạm vi cần thiết để test được

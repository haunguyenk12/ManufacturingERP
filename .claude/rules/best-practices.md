# Best Practices Checklist

> Tách từ `CLAUDE.md` §8 (2026-07-25).

## 8.1 Security

| # | Practice | Trạng thái | Mô tả |
|---|---|---|---|
| S1 | **HTTPS only** | ✅ Design | Disable plain HTTP trong production |
| S2 | **Secret rotation** | ✅ Current | JWT secret qua env variable (min 256-bit); không hardcode |
| S3 | **BCrypt cost factor 12** | ✅ Current | Cân bằng security và latency |
| S4 | **Secure headers đầy đủ** | ✅ Design | CSP, `X-Frame-Options`, HSTS, `Referrer-Policy`, `Permissions-Policy` (xem `common/security/CLAUDE.md` §4.17) |
| S5 | **CORS strict** | ✅ Design | Chỉ whitelist domain cụ thể trong production |
| S6 | **Input sanitization** | ✅ Current | `@Valid` + custom validator; không raw input vào query |
| S7 | **Least Privilege** | ✅ Current | JWT chỉ chứa `sub`, `jti`, `roles` |
| S8 | **Audit logging** | ✅ Current (`audit_logs`) / 🔜 Phase 2 (`audit_log_changes`) | Current: 1 bảng `audit_logs`; Phase 2: thêm `audit_log_changes` cho chi tiết field |
| S9 | **Token jti uniqueness** | ✅ Current | Mỗi access token có `jti` UUID để blacklist chính xác |
| S10 | **Password policy** | ✅ Design | Min 8 ký tự, có chữ hoa, thường, số, đặc biệt |
| S11 | **RTR – Token Reuse Detection** | 🔜 Phase 2 | Phát hiện refresh token bị đánh cắp → force logout toàn bộ thiết bị |
| S12 | **Account Enumeration Prevention** | ✅ Current | Login/forgot-password luôn trả cùng message, dùng constant-time compare |
| S13 | **Absolute session timeout** | 🔜 Phase 2 | Bắt buộc login lại sau 30 ngày dù user vẫn active |
| S14 | **Log sanitization** | ✅ Design | Password/token không bao giờ xuất hiện trong log; `@ToString.Exclude` trên field nhạy cảm |

## 8.2 API Design

| # | Practice | Mô tả |
|---|---|---|
| A1 | **Versioning** | Luôn version: `/api/v1/`. Breaking change → `/api/v2/` |
| A2 | **Idempotency** | POST nhạy cảm (Work Order, MRP run) → `Idempotency-Key` header lưu Redis TTL 24h |
| A3 | **HTTP semantics** | GET safe+idempotent, POST create, PUT full replace, PATCH partial, DELETE remove |
| A4 | **Pagination bắt buộc** | Không trả list không giới hạn. Default `size=20`, max `size=100` |
| A5 | **No business logic in controller** | Controller: validate input → gọi service → wrap response |
| A6 | **DTO separation** | Request/Response DTO tách biệt hoàn toàn khỏi Entity |
| A7 | **Consistent error codes** | Client code theo `ErrorCode` enum, không parse message string |
| A8 | **OpenAPI documentation** | Mọi endpoint có `@Operation`, `@ApiResponse`, `@Parameter` |

## 8.3 Database

| # | Practice | Mô tả |
|---|---|---|
| D1 | **Flyway migrations** | Không sửa schema tay. Mọi thay đổi qua migration file |
| D2 | **Index chiến lược** | Index FK, cột WHERE/ORDER BY. Dùng `EXPLAIN ANALYZE` |
| D3 | **N+1 prevention** | `@EntityGraph` hoặc JPQL JOIN FETCH. Bật Hibernate statistics trong dev |
| D4 | **Optimistic locking** | `@Version` trên mọi entity có thể bị concurrent update |
| D5 | **Soft delete** | `deleted_at TIMESTAMPTZ NULL` + `@Where(clause="deleted_at IS NULL")` global |
| D6 | **Connection pool** | HikariCP: `maximum-pool-size` = CPU cores × 2 + 1 |
| D7 | **Read-only transactions** | Service method chỉ đọc → `@Transactional(readOnly = true)` |
| D8 | **DTO Projection** | Interface-based hoặc constructor projection cho query read-only nặng |

## 8.4 Code Quality

> **Lưu ý mã rule:** dùng tiền tố `CQ` (không phải `C`) để không trùng với `C1`-`C15` ở
> `.claude/rules/coding-rules.md` — hai bộ rule khác nhau, tránh nhầm khi ai đó nói "áp dụng rule C1".

| # | Practice | Mô tả |
|---|---|---|
| CQ1 | **Constructor injection** | Không dùng `@Autowired` field injection. Dùng `@RequiredArgsConstructor` |
| CQ2 | **Immutable DTOs** | Dùng Java `record` cho DTO/response khi không cần kế thừa |
| CQ3 | **No magic numbers** | Mọi constant → `final` field hoặc `@ConfigurationProperties` |
| CQ4 | **Fail fast** | Validate ở đầu method; throw exception ngay khi invalid |
| CQ5 | **@Transactional ở service** | Không đặt ở controller hay repository |
| CQ6 | **Exception wrapping** | Không để checked exception leak lên controller |
| CQ7 | **Logging discipline** | ERROR=cần wake-up, WARN=cần chú ý, INFO=business event, DEBUG=dev-only |
| CQ8 | **Avoid Optional.get()** | Luôn dùng `orElseThrow()` với message rõ ràng |

## 8.5 Performance

| # | Practice | Mô tả |
|---|---|---|
| P1 | **Redis caching** | Cache kết quả đắt (BOM tree, MRP plan). Key: `cache:{entity}:{id}` |
| P2 | **Async processing** | Tác vụ nặng (MRP, report) → `@Async` + `CompletableFuture` |
| P3 | **Lazy loading** | `FetchType.LAZY` mặc định; eager chỉ khi cần |
| P4 | **Bulk operations** | `saveAll()` / batch insert thay vì loop `save()` |
| P5 | **Actuator + Micrometer** | Monitor: GC, thread pool, DS pool, custom business metrics |
| P6 | **Response compression** | `server.compression.enabled=true` (GZIP) cho response > 1KB |

## 8.6 Testing

| # | Practice | Trạng thái | Mô tả |
|---|---|---|---|
| T1 | **Test pyramid** | ✅ Current | Unit > Integration > E2E. Hạn chế `@SpringBootTest` (chậm). **Đúng một** class được phép dùng: `ProductionFlowE2EIT` (bài nghiệm thu end-to-end spec §11.1, thêm ở `D1`) — chuỗi cắt qua 6 module cần transaction + method security thật nên `@WebMvcTest`/`@DataJpaTest` không host được. Nó là `*IT` nên chạy qua Failsafe, **không** làm chậm `mvn test`. Mọi test khác: vẫn cấm `@SpringBootTest` |
| T2 | **Testcontainers** | ✅ Current | `AbstractPostgresIntegrationTest` (Singleton Container, `postgres:16-alpine`) + **10** `*IT.java` (`FlywayMigrationIT`, `UserRoleAssignmentRepositoryIT`, `StockBalanceRepositoryIT`, `SalesOrderLineRepositoryIT`, **`ProductionFlowE2EIT`** từ `D1`, **`PurchaseOrderRepositoryIT`** từ `D4`, **`WorkOrderRepositoryIT`** từ `F7`, **`ProductionReceiptRepositoryIT`** + **`MaterialIssueRepositoryIT`** từ `F8`, **`MaterialReservationRepositoryIT`** từ `F9`), chạy qua `mvn verify` (T4). **Khi nào bắt buộc dùng:** logic nằm trong JPQL (`R7`) — `F7` phải tạo `WorkOrderRepositoryIT` vì bộ lọc `B75` sống trong query; `F8` phải tạo 2 class nữa vì `B76` và 2 query list phẳng cũng vậy; `F9` thêm 1 class vì vế `status = ACTIVE` của query reservation batch cũng chỉ sống trong JPQL. Mock repository ở những chỗ đó chỉ tạo ra tautology. **Cũng là nơi duy nhất kiểm được `@Version`** — `WorkOrderRepositoryIT.concurrentGoodQuantityUpdate_*` (spec §10.3, nợ A đóng ở `F8`) |
| T3 | **MockMvc** | ✅ Current (**đủ 20/20**) | `SecurityFilterChainTest` (controller giả) + `GlobalExceptionHandlerTest` + **20/20** controller thật: 3 từ `T2` (`AuthController`, `WorkOrderController`, `InventoryController`) + 8 từ `D7` (nhóm A, có state machine) + **9 từ `D7b`** (`ItemController`, `ItemWarehouseSettingController`, `SupplierController`, `UserController`, `OrganizationController`, `AccessControlController`, `InventoryReportController`, `PlanningController`, `PlanningDemandController`). Nợ #2 đã trả hết |
| T4 | **Test data isolation** | ✅ Current | Mỗi test tự dựng data bằng builder trong private helper; không chia sẻ state |
| T5 | **Assert response structure** | ✅ Current (**đủ 20/20**) | `GlobalExceptionHandlerTest` + **20** `*ControllerTest` (3 từ `T2`, 8 từ `D7`, 9 từ `D7b`) assert `$.code` bằng `ErrorCode.code()` ở tầng HTTP; unit test service vẫn assert `ErrorCode` riêng. `D7` thêm quy ước: controller phân trang phải assert **đủ** `page`/`size`/`totalElements`/`totalPages`/`first`/`last` — `PageResult` là phần của contract (§5.1), không phải chi tiết nội bộ. **`D7b` thêm:** giá trị phân trang mặc định/trần (`A4`: 20 / 100) phải assert bằng **literal**, không bằng `PageableFactory.DEFAULT_PAGE_SIZE`/`MAX_PAGE_SIZE` — hằng số nằm ở cả hai vế thì đổi nó test vẫn xanh (tautology, `R6`) |
| T6 | **Security test** | ✅ Current (deny+allow+verify+WithMockUser) | `*MethodSecurityTest` có đủ deny + `verify(guard)` khoá `PERM_*` + nhánh allow (T1); `PermissionCatalogTest` chống lệch code↔seed. `@WithMockUser` nay đã dùng ở `GlobalExceptionHandlerTest` + `AuthControllerTest` (T2) |

> Chi tiết từng khoảng trống + kế hoạch khắc phục: **`TEST_IMPROVEMENT_PLAN.md`**.

### 8.6.1 Rules bắt buộc khi viết test (rút ra từ review 2026-07-24)

**R1 — Assert `ErrorCode`, không chỉ assert exception type.**
```java
// ❌ SAI – không phân biệt được lỗi nào
assertThatThrownBy(...).isInstanceOf(AppException.class);

// ✅ ĐÚNG
assertThatThrownBy(...)
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                .isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));
```

**R2 — Test method-security phải có đủ DENY + ALLOW + VERIFY.**
Test chỉ có nhánh deny **không phát hiện được sai tên permission**: stub không khớp → mock trả
default `false` → vẫn ném `AccessDeniedException` → test vẫn xanh.
```java
// Nhánh DENY phải kèm verify để khoá chặt permission string
verify(permissionGuard).hasResourceAccess(any(), eq("PERM_BOM_MANAGE"), eq("COMPANY"), eq(companyId));
// và phải có thêm 1 test nhánh ALLOW cho ít nhất 1 method mỗi service
```
> **Nghiệm thu:** đổi chuỗi `PERM_*` trong `@PreAuthorize` thành giá trị rác → test **phải đỏ**.

**R3 — Không mock class chứa logic nghiệp vụ thật.**
Mapper, helper thuần (`WorkOrderExecutionSupport`, `*Mapper`) phải dùng **instance thật** (`new ...`).
Chỉ mock: repository, service của module khác, hạ tầng (Redis, JWT, clock).
Mock một validator rồi stub nó trả về giá trị hợp lệ = xoá bỏ chính thứ đang cần test.

**R4 — Verify side-effect, không chỉ verify return value.**
Với thao tác có tác dụng phụ (ghi Redis, tăng fail-count, tạo audit, gọi movement service):
```java
verify(tokenStore).resetFailCount("testuser");
verify(inventoryMovementService).receive(argThat(cmd -> ...), eq("GR-KEY:L1"));
```

**R5 — Nhánh bị chặn phải chứng minh KHÔNG rò rỉ xuống tầng dưới.**
```java
verifyNoInteractions(movementService, repository);   // hoặc verify(x, never()).save(any())
```

**R6 — Assert bằng con số nghiệp vụ thật, không phải `isNotNull()`.**
Với BOM/MRP/variance/scrap phải khoá cả công thức lẫn scale làm tròn:
```java
assertThat(response.lines().get(0).requiredQuantity()).isEqualByComparingTo("22.00");  // 10 × 2.0 ÷ (1 − 0.10)
```

**R7 — Tên test phải mô tả đúng nội dung test.**
Tên hứa `...ForInactiveOrExpiredAssignments` nhưng thân test chỉ stub rỗng rồi assert rỗng = tautology.
Nếu logic thật nằm trong JPQL → phải viết `@DataJpaTest`, không được giả vờ test bằng mock.

**R8 — Assert bằng enum/hằng, không bằng magic string.**
```java
// ❌ .andExpect(jsonPath("$.code").value("BIZ_100"))
// ✅ .andExpect(jsonPath("$.code").value(BusinessErrorCode.RATE_LIMIT_EXCEEDED.code()))
```

**R9 — Idempotency là bất biến bắt buộc test.**
Mọi POST nhạy cảm (goods receipt, material issue, production receipt, stock movement) phải có test:
gửi trùng key → trả document cũ + `verifyNoInteractions(<service ghi tồn kho>)`.

**R10 — Không xoá test cũ để cho build xanh.**
Khi nghiệp vụ đổi: **sửa** test cho khớp hành vi mới. Nếu test không còn ý nghĩa → **thay** bằng
test kiểm tra hành vi mới tương ứng. Số test case sau mỗi phase phải **≥ baseline**.

## 8.7 Observability

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    root: INFO
    com.erp.manufacturing: DEBUG   # dev only; prod → INFO
```

**Key metrics cần track**:
- `auth.login.success` / `auth.login.failure` (counter)
- `auth.session.kicked` (counter – phát hiện chia sẻ tài khoản)
- `rate.limit.exceeded` per endpoint (counter)
- `work_order.created` / `mrp.calculation.duration` (timer)
- `db.pool.active` / `redis.pool.active` (gauge)
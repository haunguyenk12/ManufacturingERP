# Manufacturing ERP – System Architecture

> **Status**: viết ở giai đoạn nền tảng (Phase 1), **đối chiếu lại toàn bộ với code ngày 2026-11-13**
> (`RA-1`). Mục nào mô tả thiết kế gốc chưa làm thì ghi rõ "chưa làm". Khi lệch, code và `CLAUDE.md` của
> từng module là nguồn đúng.
> **Stack**: Spring Boot 3.x · Java 17 · PostgreSQL · Redis · Flyway · Spring Security 6 + JWT
> **Architecture**: Modular Monolith (không tách microservice ở giai đoạn hiện tại)

---

## 1. Current Architecture

Modular monolith một tiến trình Spring Boot, gồm:

- `common/` — exception, response envelope, security (JWT, filter, token store), audit, request context,
  idempotency, web (phân trang, `X-Plant-Id`).
- `config/` — cấu hình Spring (Security, Redis, Async, Audit, CORS, OpenAPI, validator khởi động).
- `module/` — 18 module nghiệp vụ; danh sách và cổng giao tiếp ở **§3.3**.

Hạ tầng:

| Thành phần | Dùng cho |
|---|---|
| PostgreSQL 16 | Toàn bộ dữ liệu nghiệp vụ và audit; schema do Flyway quản lý (`V1`…`V96`, `ddl-auto: validate`) |
| Redis 7 | Refresh token, blacklist access token, device session, bộ đếm brute-force, rate limit, token đặt lại mật khẩu |
| SMTP (tuỳ chọn) | Mã xác thực email; chỉ gửi khi đặt `SPRING_MAIL_HOST` (QA dùng Mailpit trong `docker-compose.yml`) |

---

## 2. Request Lifecycle

Every HTTP request traverses 4 filters before reaching a controller:

```
Client Request
      │
      ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. TraceIdFilter                                                │
│    • Reads or generates 16-char traceId                        │
│    • Extracts clientIp (trusted-proxy-aware)                   │
│    • Sets MDC: traceId, clientIp                               │
│    • Sets request attrs: traceId, clientIp                     │
│    • Sets response header: X-Trace-Id                          │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. RateLimitFilter  [IP-scope only]                            │
│    • rate:blacklist:ip:{ip}   → 403 ACCESS_DENIED             │
│    • rate:whitelist:ip:{ip}   → bypass all checks             │
│    • IP-scope rules from YAML → 429 RATE_LIMIT_EXCEEDED       │
│    • Adds X-RateLimit-* headers on block                       │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. JwtAuthenticationFilter                                     │
│    Normal path:                                                 │
│      • Extract Bearer token                                    │
│      • Validate signature + expiry                             │
│      • Check auth:blacklist:{jti}                              │
│      • Set SecurityContextHolder                               │
│      • Set request attr: authenticatedUserId, authenticatedJti │
│      • Set MDC: userId                                         │
│    Public paths (BYPASS_PATHS: login, refresh, password…):    │
│      • Skipped entirely; refresh resolves the user from        │
│        tokenId, never from the access token (B107)             │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. UserRateLimitFilter  [USER-scope only]                      │
│    • Reads authenticatedUserId from request attr              │
│    • If null → no-op (unauthenticated requests only hit IP)   │
│    • USER-scope rules from YAML → 429 RATE_LIMIT_EXCEEDED     │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
                    Controller / Service
                          │
                    @Auditable / AuditRecorder
                     → INSERT audit_outbox (same transaction as the business write)
                          │
                    AuditOutboxDispatcher (@Scheduled, 1 s)
                     → audit_logs + audit_log_changes + audit_log_entities
```

**Error responses in filters** bypass `GlobalExceptionHandler` and write directly to the response stream using the same `ApiResponse` envelope (`{code, result, message}`).

---

## 3. Module Boundary Rules

### 3.1 Convention

Each module follows this package structure:

```
module/<name>/
  ├── controller/   HTTP layer only – validate → call service → wrap response
  ├── service/      Business logic, @Transactional
  ├── domain/       JPA entities, enums, domain helpers
  ├── repository/   Spring Data interfaces
  ├── dto/          Request / Response records (no entity leakage)
  └── mapper/       DTO ↔ Entity conversion
```

### 3.2 Cross-Module Rules

| Rule | Rationale |
|---|---|
| A module **must not** call another module's `repository` directly | Prevents coupling at the persistence layer. Ngoại lệ còn tồn tại: `.claude/rules/coding-rules.md` C7 |
| Cross-module calls go through the target module's **service** (application service) | Single source of business logic |
| For async or decoupled integration, use **Spring ApplicationEvent** | Avoids direct dependency between modules. *(Hiện chưa module nghiệp vụ nào dùng event; mọi tích hợp là gọi service đồng bộ.)* |
| `common/*` packages may be used by any module | They are true cross-cutting concerns |

### 3.3 Current Module Map *(2026-11-12)*

| Module | Phụ trách | Cổng cho module khác |
|---|---|---|
| `module/auth` | Đăng nhập, refresh, logout, khôi phục và đặt mật khẩu bằng mã email | `AuthService` |
| `module/user` | Tài khoản người dùng | `UserService`, `UserLookupService` |
| `module/organization` | Công ty, nhà máy, kho; role, permission, access scope, assignment | `OrganizationLookupService`, `PermissionGuard` |
| `module/inventory` | Vật tư, tồn kho, lô, serial, chuyển kho, thiết lập vật tư–kho | `ItemLookupService`, `InventoryLotLookupService`, `StockPoolLookupService`, `InventoryAvailabilityService`, `StockBalanceReservationService` (giữ/nhả tồn) |
| `module/uom` | Đơn vị tính | `UomLookupService` |
| `module/bom` | Định mức (BOM đa cấp, phiên bản) | `BomLookupService` |
| `module/routing` | Quy trình công nghệ | `RoutingLookupService` |
| `module/workcenter` | Trung tâm làm việc | `WorkCenterLookupService` |
| `module/shift` | Ca và lịch làm việc | `WorkCalendarLookupService` |
| `module/planning` | Nhu cầu, lượt chạy MRP, đề xuất cung ứng | `PlanningDemandService`, `SupplySuggestionService` (`findWithDetails`/`markConverted` cho purchasing) |
| `module/purchasing` | Nhà cung cấp, yêu cầu mua, đơn mua, nhận hàng, QC mua | `SupplierLookupService`, `PurchaseQcLookupService` |
| `module/workorder` | Lệnh sản xuất, giữ chỗ, cấp vật tư, công đoạn, phiếu nhập thành phẩm, QC, giá thành sản xuất | các `*LookupService` trong `service/query` |
| `module/costing` | Giá thành chuẩn của vật tư | `ItemStandardCostLookupService` |
| `module/sales` | Khách hàng, đơn bán hàng | `CustomerLookupService`, `SalesOrderCommercialLookupService` |
| `module/delivery` | Phiếu giao hàng | `DeliveryLookupService` |
| `module/billing` | Hoá đơn bán hàng, thu tiền | — |
| `module/dataimport` | Nhập Excel dữ liệu nền và chứng từ | — |
| `module/reporting` | Controller báo cáo theo domain | — |
| `common/audit` | Audit trail (outbox + bảng append-only) | `AuditLogService`, `@Auditable` |
| `common/security` | JWT, rate limit, token store | `TokenStoreService`, `JwtTokenProvider` |

Quy tắc gọi chéo module và danh sách ngoại lệ hiện có: `.claude/rules/coding-rules.md` C7.

---

## 4. Error Response Contract

Mọi response dùng envelope `{code, result, message, errors?}`. `code` là **tên hằng** của `ErrorCode`
(vd `STATE_CONFLICT`), không phải mã ngắn `AUTH_XXX`/`BIZ_XXX` như bản thiết kế đầu; `errors` là **mảng**
`{field, message}`. Hai lớp exception (`AppException`, `MultiErrorException`), tạo qua `ExceptionFactory`;
loại lỗi mới là một hằng mới trong enum, không phải class mới.

Chi tiết đầy đủ — bảng chọn 409/422, danh sách handler, phân loại `DataIntegrityViolationException`, lỗi
trong filter chain: `.claude/rules/error-handling.md`. Lịch sử mã lỗi: `docs/error-codes-history.md`.

---

## 5. Session & Token Design

### 5.1 Token Strategy

| Token | Storage | TTL | Purpose |
|---|---|---|---|
| Access Token (JWT, HS256) | Client only | 15 min mặc định (`JWT_ACCESS_TOKEN_EXPIRY_MS`) | Stateless API auth |
| Refresh Token (opaque UUID) | Redis | 7 days (sliding) | Issue new access tokens |
| Blacklist entry | Redis | Remaining access token TTL | Invalidate on logout |

### 5.2 Redis Key Strategy

```
# Refresh token (value = opaque UUID)
auth:refresh:{userId}:{tokenId}           TTL=7d

# Blacklist (value = "1")
auth:blacklist:{jti}                       TTL=remaining access token lifetime

# Brute-force counter (value = count string)
auth:failcount:{username}                  TTL=15m (reset on success)

# Multi-device session (value = last-seen IP)
auth:session:device:{userId}:{deviceId}   TTL=7d (sliding on refresh)
```

Ngoài các key trên, code hiện còn dùng: `auth:refresh:owner:{tokenId}` (tra userId khi refresh, `B107`),
`auth:refresh:used:{tokenId}` (phát hiện dùng lại, `B80`), `auth:refresh:{userId}:{tokenId}:meta` (mốc
bắt đầu phiên, `B81`), `auth:refresh:lock:{tokenId}` và `auth:refresh:rotated:{tokenId}` (refresh đồng thời,
`B95`), `auth:reset:{token}` + `auth:reset:user:{userId}` (đặt lại mật khẩu, `D8c`). Danh sách đầy đủ kèm TTL:
`common/security/CLAUDE.md §4.4`. Phiên tuyệt đối 30 ngày tính từ lúc đăng nhập.

### 5.3 Multi-Device Session

> **Đính chính 2026-11-12:** hạ tầng theo thiết bị vẫn còn, nhưng **chính sách là một phiên cho mỗi user**:
> `AuthService.login` xoá mọi refresh token và device session cũ mỗi lần đăng nhập. Đoạn dưới mô tả thiết kế ban đầu.

Each device is identified by a `deviceId` (client-provided or derived server-side as `sha256(userAgent:ip)[:24]`). A user can be logged in from multiple devices simultaneously. Each device session is independent with its own sliding TTL.

### 5.4 Filter Chain Security Note

`RateLimitFilter` (Order 2) runs **before** JWT authentication. USER-scope rate limiting is handled by the separate `UserRateLimitFilter` (Order 4) which runs **after** `JwtAuthenticationFilter` so `authenticatedUserId` is available.

---

## 6. Audit Log Design

Thiết kế ban đầu (event `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`) đã được thay bằng
**transactional outbox** ở track `AR-*` (2026-09-02), vì cơ chế cũ bỏ mất mọi event ghi ngoài transaction
(toàn bộ event đăng nhập) và mất event `FAILURE` khi rollback.

```
@Auditable / AuditRecorder
  ├─ SUCCESS → INSERT audit_outbox trong CÙNG transaction nghiệp vụ
  └─ FAILURE → INSERT audit_outbox ở REQUIRES_NEW (bean riêng) — sống qua rollback
        │
AuditOutboxDispatcher (@Scheduled, mặc định 1 s, lease + retry + backoff)
  └─ audit_logs (1 dòng/hành động) + audit_log_changes (thay đổi theo field) + audit_log_entities
```

- Idempotent nhờ `eventId` do producer sinh + unique index `uk_audit_logs_event_id`.
- Ba bảng audit append-only: trigger `V68` chặn `UPDATE`/`DELETE` với mọi role.
- Chính sách lỗi `FAIL_OPEN`: audit hỏng không đổi kết quả nghiệp vụ.
- Listener cũ vẫn còn trong code nhưng **mặc định tắt** (`AUDIT_LEGACY_LISTENER_ENABLED=false`).

Chi tiết: `src/main/java/com/erp/manufacturing/common/audit/CLAUDE.md`.

---

## 7. Rate Limiting Design

### 7.1 Two-Layer Architecture

| Layer | Filter | Scope | When |
|---|---|---|---|
| IP layer | `RateLimitFilter` (Order 2) | `IP` rules | Before JWT — protects unauthenticated endpoints |
| User layer | `UserRateLimitFilter` (Order 4) | `USER` rules | After JWT — uses authenticated userId |

### 7.2 Config-Driven Rules (application.yml)

Bộ đếm là **fixed window** (`INCR` trên key theo cửa sổ thời gian), không phải sliding window. Mọi `limit`
và `enabled` đọc từ biến môi trường `RATE_LIMIT_*`; giá trị dưới đây là mặc định.

```yaml
app:
  rate-limit:
    enabled: true
    rules:
      - id: global-ip
        scope: IP
        pattern: "/**"
        limit: 200
        window-seconds: 60
        action: BLOCK

      - id: auth-ip
        scope: IP
        pattern: "/auth/v1/**"
        limit: 10
        window-seconds: 60
        action: BLOCK

      - id: auth-refresh-ip
        scope: IP
        pattern: "/auth/v1/refresh"
        limit: 20
        window-seconds: 60
        action: BLOCK

      - id: global-user
        scope: USER
        pattern: "/**"
        limit: 500
        window-seconds: 60
        action: BLOCK
```

### 7.3 Runtime Override (no restart)

Admin can override rule limits at runtime via Redis:
```
rate:rule:override:{ruleId}  → hash { limit, windowSeconds }
```

### 7.4 Redis Key Pattern

```
rate:counter:{ruleId}:{identifier}:{epochWindow}   TTL=windowSeconds+10
rate:rule:override:{ruleId}                        hash, no fixed TTL
rate:whitelist:ip:{ip}                             "1", admin-set
rate:blacklist:ip:{ip}                             reason string, admin-set
```

---

## 8. Scaling Strategy

This is a **stateless monolith** designed to scale horizontally:

| Concern | Current Design | Scale-Out Path |
|---|---|---|
| App state | Stateless (STATELESS session policy) | Multiple pods share no in-memory state |
| Session / token | Redis | Redis Cluster or Redis Sentinel |
| Rate limiting | Redis atomic counter | Same Redis cluster, no code change |
| Database | PostgreSQL single | Read replicas + connection pooling (PgBouncer) |
| Long jobs (MRP, reports) | Not yet implemented | Job table + async workers (see §9) |
| JWT signing | HS256 (symmetric) | Migrate to RS256 when splitting to microservices |

**Preconditions for horizontal scaling** (already met):
- No `HttpSession` / no in-memory state
- Token blacklist + rate limit state in Redis
- All configs from environment variables
- Flyway for schema consistency across pods

---

## 9. Bounded Contexts & ERP Roadmap

### 9.1 Planned Bounded Contexts

> Bảng dưới là **kế hoạch ban đầu**, giữ để tham chiếu. Module thật hiện có ở §3.3. Chưa có: Quality
> (QC nằm trong `purchasing` và `workorder`), Reporting dạng materialized view, Integration/MES.

| Context | Owns | Public Interface |
|---|---|---|
| **Identity & Access** (current) | users, roles | AuthService, UserService |
| **Organization** | company, plant, warehouse | OrganizationService |
| **Inventory** | items, stock_movements, stock_balances | InventoryService |
| **Manufacturing Master Data** | bom_headers, bom_lines, routings | BomService, RoutingService |
| **Production Execution** | work_orders, shop_floor_events | WorkOrderService |
| **Planning** | mrp_plans, mrp_demands | MrpService |
| **Quality** | inspection_lots, defects | QualityService |
| **Costing** | cost_rates, cost_rolls, variances | CostingService |
| **Reporting** | materialized views, aggregates | ReportingService |
| **Integration/MES** | outbound events, inbound readings | MesIntegrationService |

### 9.2 Organization Scope (Architecture Decision)

> **ADR-001**: All domain entities that are plant/warehouse-scoped MUST carry an `organizationId` (or `plantId` / `warehouseId`) foreign key. No entity is "company-global" except master reference data.

Planned hierarchy:
```
Company (1)
  └── Plant / Site (N)
        └── Warehouse (N)
```

Role and permission assignment will need to be scoped to `(user, company)` or `(user, plant)` — not just global `ADMIN/MANAGER/OPERATOR`. This must be designed before Inventory or Work Order modules are added.

> **Đã làm:** công ty / nhà máy / kho có từ `V6`; phân quyền theo phạm vi qua `access_scopes` +
> `user_role_assignments` (GLOBAL / COMPANY / PLANT / WAREHOUSE), kiểm tra bằng `PermissionGuard`.
> Bất biến RBAC: `module/organization/CLAUDE.md`.

### 9.3 Inventory Ledger Design (Architecture Decision)

> **ADR-002**: Stock movements are the source of truth (append-only). Stock balance is a projection/snapshot derived from movements.

```
stock_movements  -- append-only ledger (qty, direction, reason, lot, serial)
stock_balances   -- projection: SUM(movements) by item/warehouse/lot
```

- Batch/Lot/Serial traceability is derived from movements — never calculated on-the-fly for large datasets.
- Idempotency key required on Inventory issue/receive/adjust operations.

### 9.4 Work Order & MRP (Architecture Decision)

> **ADR-003**: Heavy async operations (MRP run, cost roll) use a Job model.

```
POST /api/v1/planning-runs → create job row → return { jobId }
GET  /api/v1/jobs/{jobId}  → return { status: PENDING|RUNNING|DONE|FAILED, result }
```

> ⚠️ **ADR-003 chưa được implement.** Hiện `POST /api/v1/planning-runs` chạy **đồng bộ** và trả
> `MrpRunResponse` ngay (`MrpRunService.run`); không có bảng job, không có `/api/v1/jobs`. Đường dẫn
> ở trên đã đổi tên theo `F5-B` để không còn trỏ tới `/api/v1/mrp/**` đã bị xoá.

Idempotency key: client gửi header `Idempotency-Key`; server lưu ở cột `mrp_runs.idempotency_key` + UNIQUE
(`V58`), **không** lưu Redis. Gửi lại cùng key trả lượt chạy cũ; cùng key khác payload ⇒ `409 IDEMPOTENCY_CONFLICT`.

### 9.4.1 Manufacturing Business Gates (Phase P1 – implemented)

> **ADR-004**: Three control points sit inside the work order execution flow. Each one is a
> *business gate* — it refuses the operation rather than silently correcting it.

| Gate | Where | Rule | Failure mode |
|---|---|---|---|
| **1a – Release readiness** | `WorkOrderReleaseGate.ensureMaterialReady` | Every component must satisfy `reserved ≥ required − issued` | **`409 STATE_CONFLICT`** (`F5` moved it off 422 — debt #9), work order persisted as `BLOCKED` by `WorkOrderBlockRecorder` (`D9`) |
| **1b – Over-issue** | `MaterialIssueService.postNew` | Issuing beyond the remaining BOM requirement needs `PERM_MATERIAL_ISSUE_OVERRIDE` + `overrideReason` | `403` without the permission, **`400 VALIDATION_ERROR`** (`MISSING_REQUIRED_FIELD`) without a reason |
| **1c – Receipt approval** | `ProductionReceiptService` | `post` creates a `DRAFT` receipt (`F2` added `submit` → `PENDING_APPROVAL`); `approve` creates the stock movement | **`409 STATE_CONFLICT`** when the receipt is in the wrong status (`F2`) |

**Gate 1a runs in its own transaction.** `ensureMaterialReady` is annotated
`@Transactional(propagation = REQUIRES_NEW)` because two things must both happen: the release
must fail (so the caller's transaction rolls back and the client gets an error), *and* the
`BLOCKED` state must survive so planners can query which work orders are waiting on material.
A single transaction can only deliver one of the two.

**Gate 1c splits the receipt into two steps.** Posting a receipt does not touch inventory; only
`approve` creates the stock movement, and new output lots open in `HOLD` for QC.

> **Đã thay đổi sau P1:** vòng đời phiếu nhập được viết lại ở `G9a`–`G9c` (nháp, sửa, rút, huỷ, version,
> QC nhiều lượt theo lượng). Đường dẫn và trạng thái hiện tại: `docs/api-guide-for-frontend.md` và
> `module/workorder/service/execution/CLAUDE.md` (mục G9a–G9c). Phần endpoint chi tiết của P1 đã bỏ
> khỏi đây vì không còn đúng.

**Known limitations (P1, accepted):**
1. An item that is not lot-tracked has no lot status to carry `HOLD`. `D5` enabled QC on its receipt;
   `FE4-5B3` added `stock_balances.quality_hold_quantity` as the inventory-side carrier. Approval
   increases on-hand while available remains zero. `AVAILABLE` releases the hold and unlocks
   fulfilment; `REJECTED` leaves the goods on hand and held for traceability.
2. An already-existing lot (same `lotCode`) keeps its current status — only newly created lots
   receive `HOLD`. Business recommendation: use a fresh `lotCode` per production receipt.

### 9.5 Transactional Outbox (Phase 2+)

> **Đính chính 2026-11-12:** outbox đã triển khai cho audit từ track `AR-*` (bảng `audit_outbox`, `V67`/`V68`).
> Schema thật khác bản phác dưới đây; xem `common/audit/CLAUDE.md`.

Replace fire-and-forget Spring event audit with:
```
outbox_events (
  id          UUID PK,
  aggregate   VARCHAR,
  event_type  VARCHAR,
  payload     JSONB,
  status      VARCHAR DEFAULT 'PENDING',  -- PENDING | PROCESSED | FAILED
  retry_count INT DEFAULT 0,
  next_retry  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ
)
```
- Business transaction writes outbox row in the **same transaction** → no lost events on crash.
- Background worker polls `PENDING` rows, publishes/processes, marks `PROCESSED`.

---

## 10. Observability

### 10.1 Structured Log Fields (MDC)

| MDC Key | Set by | Available from |
|---|---|---|
| `traceId` | `TraceIdFilter` | All filters and services |
| `clientIp` | `TraceIdFilter` | All filters and services |
| `userId` | `JwtAuthenticationFilter` | Post-auth requests only |

Log pattern: `%d{ISO8601} [%X{traceId}] [%X{userId}] [%X{clientIp}] %-5level [%thread] %logger{36} - %msg%n`

### 10.2 Health & Metrics

Actuator chạy ở **cổng quản trị riêng** (`MANAGEMENT_SERVER_PORT`, mặc định 8081). Mặc định chỉ mở
`health`, `info` (`MANAGEMENT_ENDPOINTS_EXPOSURE_INCLUDE`); `show-details` mặc định `never`. Không có
registry Prometheus trong `pom.xml`. `/actuator/info` có `build.*` và `app.commit` (`APP_COMMIT`).

Metric tự định nghĩa hiện có **chỉ của audit** (`AuditMetrics`, `AuditOutboxHealth`). Bảng dưới là các
metric **dự kiến, chưa làm**:

| Metric | Type | When |
|---|---|---|
| `auth.login.success` | counter | AuthService.login success |
| `auth.login.failure` | counter | AuthService.login fail |
| `rate.limit.hit` | counter + tags(ruleId) | RateLimitFilter / UserRateLimitFilter |
| `mrp.run.duration` | timer | MrpService (Phase 2) |
| `db.pool.active` | gauge | HikariCP (auto via Actuator) |
| `redis.errors` | counter | TokenStoreService error catch |

### 10.3 Security Note

`/actuator/health` và `/actuator/info` là `permitAll` trong `SecurityConfig`; mọi `/actuator/**` khác yêu cầu
role `ADMIN`. Không mở cổng quản trị ra ngoài ở production.

---

## 11. Development Conventions

### 11.1 Package Structure Template (new modules)

```
module/<name>/
  controller/<Name>Controller.java    # @RestController, @RequestMapping
  service/<Name>Service.java          # @Service, @Transactional
  domain/<Name>.java                  # @Entity extends BaseEntity
  domain/<Name>Status.java            # enum for status fields
  repository/<Name>Repository.java    # extends JpaRepository
  dto/<Name>Request.java              # record, @Valid annotations
  dto/<Name>Response.java             # record
  mapper/<Name>Mapper.java            # @Component, toResponse(entity), viết tay (chỉ UserMapper dùng MapStruct)
```

### 11.2 ErrorCode Convention for New Modules

**Không** tạo enum `XxxErrorCode` riêng với tiền tố `INV_`/`WO_` như bản thiết kế đầu. Thêm hằng vào
`BusinessErrorCode` / `ValidationErrorCode`; chỉ tách enum riêng khi domain có vốn từ lỗi khác hẳn (tiền
lệ duy nhất: `ImportErrorCode` cho lỗi cấp dòng của file import). Xem `.claude/rules/error-handling.md §5.2`.

### 11.3 Migration Naming

`V<số kế tiếp>__<mô_tả_snake_case>.sql`; số mới nhất ở `CLAUDE.md §0.0`. Không sửa migration đã có.
Xem `.claude/rules/dev-workflow.md §6.4`.

---

## 12. Việc chưa làm *(viết lại 2026-11-12 theo code)*

Các mục bản cũ liệt kê là "chưa làm" nay **đã có**: phát hiện dùng lại refresh token, luồng đặt lại mật khẩu,
absolute session timeout, transactional outbox cho audit, tổ chức / nhà máy / kho, tồn kho, BOM, lệnh sản
xuất, MRP.

| Hạng mục | Thực tế |
|---|---|
| Log JSON có cấu trúc cho prod | Chỉ có pattern text của Logback |
| Metric nghiệp vụ tự định nghĩa | Chỉ audit có (`AuditMetrics`, `AuditOutboxHealth`); không có registry Prometheus |
| Security headers bổ sung (CSP, `Referrer-Policy`, `Permissions-Policy`) | Chỉ có header mặc định của Spring Security |
| Gắn refresh token với dấu vân tay thiết bị (thiết kế §4.19 của `common/security/CLAUDE.md`) | Chưa thấy trong `AuthService` / `TokenStoreService` |
| Email đặt lại mật khẩu bằng link (`sendPasswordResetEmail`) | Vẫn là mock ghi log; chỉ mã 6 số gửi SMTP thật |
| Cache (cây BOM, kết quả MRP) | Chưa có `@Cacheable` nào |
| Xử lý bất đồng bộ cho MRP / report | Chạy đồng bộ |
| Rate limit sliding window | Đang là fixed window |
| CI | Repo chưa có workflow nào |
| Tích hợp MES, module chất lượng độc lập, sổ giá trị tồn kho | Chưa làm (xem `MANUFACTURING_PHASE_PLAN_P7_P11.md`) |

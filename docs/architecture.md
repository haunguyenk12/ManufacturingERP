# Manufacturing ERP – System Architecture

> **Status**: Foundation Layer (Phase 1)
> **Stack**: Spring Boot 3.x · Java 17 · PostgreSQL · Redis · Flyway · Spring Security 6 + JWT
> **Architecture**: Modular Monolith (không tách microservice ở giai đoạn hiện tại)

---

## 1. Current Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       Manufacturing ERP                          │
│                                                                  │
│  com.erp.manufacturing                                           │
│  ├── common/                (cross-cutting concerns)            │
│  │   ├── exception/         ErrorCode interface + 3 enum impls  │
│  │   ├── response/          ApiResponse envelope                 │
│  │   ├── security/          JWT, filters, TokenStore            │
│  │   ├── audit/             AuditLog, AuditLogEvent, Aspect     │
│  │   └── context/           RequestContext (immutable snapshot) │
│  │                                                               │
│  ├── config/                Spring configs (Security, Redis, …) │
│  │                                                               │
│  └── module/                domain modules                       │
│      ├── auth/              login, refresh, logout               │
│      │   ├── controller/                                         │
│      │   ├── dto/                                                │
│      │   └── service/       AuthService                         │
│      └── user/              user & role management               │
│          ├── controller/                                         │
│          ├── domain/        User, Role, UserPrincipal, Enum     │
│          ├── dto/                                                │
│          ├── mapper/                                             │
│          ├── repository/                                         │
│          └── service/       UserService, UserDetailsServiceImpl │
│                                                                  │
│  Infrastructure:                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐ │
│  │  PostgreSQL  │  │    Redis     │  │     Flyway             │ │
│  │  users       │  │  refresh     │  │  V1__create_base…     │ │
│  │  roles       │  │  blacklist   │  │  V2__seed_admin…      │ │
│  │  user_roles  │  │  failcount   │  │  V5__create_audit…    │ │
│  │  audit_logs  │  │  sessions    │  │                        │ │
│  └──────────────┘  │  rate-limit  │  └───────────────────────┘ │
│                    └──────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
```

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
│    Refresh path (/api/v1/auth/refresh):                        │
│      • Extract claims even if token expired                    │
│      • Set authenticatedUserId only (not SecurityContext)      │
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
                    AuditLogService
                    (ApplicationEventPublisher → async)
                          │
                    [auditExecutor thread]
                     AuditLogListener → INSERT audit_logs
                     -- [TODO Phase 2] audit_log_changes batch insert
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
| A module **must not** call another module's `repository` directly | Prevents coupling at the persistence layer |
| Cross-module calls go through the target module's **service** (application service) | Single source of business logic |
| For async or decoupled integration, use **Spring ApplicationEvent** | Avoids direct dependency between modules |
| `common/*` packages may be used by any module | They are true cross-cutting concerns |

### 3.3 Current Module Map

| Module | Owns | Public Interface |
|---|---|---|
| `module/user` | `users`, `roles`, `user_roles` tables | `UserService`, `UserDetailsServiceImpl` |
| `module/auth` | Session logic (Redis) | `AuthService` |
| `module/sales` | `sales_orders`, `sales_order_lines` | `SalesOrderService` (consumes `PlanningDemandService`; nothing consumes `sales` yet — see `module/sales/CLAUDE.md`) |
| `common/audit` | `audit_logs` table | `AuditLogService` |
| `common/security` | JWT, rate-limit, token store | `TokenStoreService`, `JwtTokenProvider` |

---

## 4. Error Response Contract

**All responses** (success and error) use the same envelope:

```json
{
  "code":    "AUTH_001",
  "result":  null,
  "message": "Invalid username or password",
  "errors":  { "field": "message" }
}
```

| Field | Always present | Description |
|---|---|---|
| `code` | ✅ | Machine-readable code. `SUCCESS` on success, `AUTH_XXX` / `VAL_XXX` / `BIZ_XXX` on error |
| `result` | — | Data payload on success; `null` on error |
| `message` | ✅ | Human-readable text |
| `errors` | ❌ | Present only for multi-field validation failures |

### 4.1 ErrorCode Hierarchy

```
ErrorCode  (interface: code(), message(), status())
├── AuthErrorCode      AUTH_001…AUTH_030   (credentials, tokens, session, access)
├── ValidationErrorCode VAL_001…VAL_021   (input, user fields, resource existence)
└── BusinessErrorCode  BIZ_001…BIZ_100    (domain rules, inventory, BOM, rate-limit)
```

### 4.2 Exception Hierarchy

```
RuntimeException
└── AppException                    (root – holds ErrorCode + HttpStatus + message)
    └── MultiErrorException         (aggregates field-level errors for validation)
```

**Factory**: `ExceptionFactory` provides named static methods (`notFound`, `unauthorized`, `alreadyExists`, `businessRule`, `custom`, `withErrors`) so throw-sites are intent-revealing.

**GlobalExceptionHandler** handles (in order):
1. `MultiErrorException` → field-level errors JSON
2. `AppException` → single error
3. `MethodArgumentNotValidException` → Bean Validation (uses `ValidationErrorCode.INVALID_INPUT`)
4. `AccessDeniedException` (Spring Security) → `AuthErrorCode.ACCESS_DENIED`
5. `DataIntegrityViolationException` → `ValidationErrorCode.RESOURCE_ALREADY_EXISTS`
6. `Exception` (catch-all) → `BusinessErrorCode.INTERNAL_SERVER_ERROR`

---

## 5. Session & Token Design

### 5.1 Token Strategy

| Token | Storage | TTL | Purpose |
|---|---|---|---|
| Access Token (JWT, HS256) | Client only | 15 min | Stateless API auth |
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

> **Note**: The CLAUDE.md section 4.4 references `auth:refresh:used:{tokenId}` (RTR reuse detection) and `auth:reset:{token}` (password reset) as planned features. These keys are **documented in design but not yet implemented** in `TokenStoreService`. See §9 Roadmap.

### 5.3 Multi-Device Session

Each device is identified by a `deviceId` (client-provided or derived server-side as `sha256(userAgent:ip)[:24]`). A user can be logged in from multiple devices simultaneously. Each device session is independent with its own sliding TTL.

### 5.4 Filter Chain Security Note

`RateLimitFilter` (Order 2) runs **before** JWT authentication. USER-scope rate limiting is handled by the separate `UserRateLimitFilter` (Order 4) which runs **after** `JwtAuthenticationFilter` so `authenticatedUserId` is available.

---

## 6. Audit Log Design

### 6.1 Architecture

```
Business code
  └── AuditLogService.log*(...)
        └── ApplicationEventPublisher.publishEvent(AuditLogEvent)
              │
              [auditExecutor thread – MDC propagated via MdcTaskDecorator]
              │
              AuditLogListener
                @Async("auditExecutor")
                @TransactionalEventListener(AFTER_COMMIT)
                  └── auditLogRepository.save(AuditLog)
```

**Key properties**:
- Async → does not block response time
- `AFTER_COMMIT` → not written if the business transaction rolls back
- MDC (`traceId`, `userId`, `clientIp`) propagated to async thread via `MdcTaskDecorator`
- Context (`userId`, `username`, `clientIp`, `traceId`) captured as immutable `RequestContext` record **on the request thread** before crossing async boundary

### 6.2 Known Limitation (documented)

> **Risk**: If the JVM crashes after the business transaction commits but before the async audit event is processed, the audit entry is lost.

**Current mitigation**: None — acceptable for Phase 1 foundation.

**Planned mitigation** (Phase 2+): Transactional Outbox pattern — write an `outbox_events` row in the same transaction, background worker processes and deletes it. See §9 Roadmap.

### 6.3 Schema

```sql
-- Current (implemented): single-table design
audit_logs         -- 1 row per action (LOGIN, WORK_ORDER_UPDATED, etc.)

-- [TODO Phase 2] field-level change tracking
-- audit_log_changes  -- N rows per action (field_name, old_value, new_value)
--                    -- Only for CREATE/UPDATE/DELETE on business entities
--                    -- Migration: V6__create_audit_log_changes.sql
```

> **Current migration** (`V5__create_audit_logs.sql`) only creates `audit_logs`.
> `audit_log_changes` is planned for Phase 2 when the existing `@Auditable` AOP flow is extended to capture field-level changes.

---

## 7. Rate Limiting Design

### 7.1 Two-Layer Architecture

| Layer | Filter | Scope | When |
|---|---|---|---|
| IP layer | `RateLimitFilter` (Order 2) | `IP` rules | Before JWT — protects unauthenticated endpoints |
| User layer | `UserRateLimitFilter` (Order 4) | `USER` rules | After JWT — uses authenticated userId |

### 7.2 Config-Driven Rules (application.yml)

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
        pattern: "/api/v1/auth/**"
        limit: 10
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

Idempotency key required: client sends `Idempotency-Key` header → server stores result in Redis TTL 24h.

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

**Gate 1c splits the receipt into two steps.** Posting a receipt no longer touches inventory:

```
POST /work-orders/{id}/production-receipts              → PENDING_APPROVAL, no movement
POST /work-orders/{id}/production-receipts/{rid}/approve → POSTED + RECEIVE movement, new lot = HOLD
POST /work-orders/{id}/production-receipts/{rid}/reject  → REJECTED, no inventory impact
```

`POSTED` is deliberately reused as the post-approval state (rather than adding `APPROVED`):
existing `POSTED` rows already mean "written to stock", so no data backfill is needed.

Output lots created by approval open in `LotStatus.HOLD`, which is what unlocks Phase P2
(Quality Control): QC only has to add the `HOLD → AVAILABLE / REJECTED` workflow.

**Known limitations (P1, accepted):**
1. An item that is not lot-tracked has no lot status to carry `HOLD`. `D5` enabled QC on its receipt;
   `FE4-5B3` added `stock_balances.quality_hold_quantity` as the inventory-side carrier. Approval
   increases on-hand while available remains zero. `AVAILABLE` releases the hold and unlocks
   fulfilment; `REJECTED` leaves the goods on hand and held for traceability.
2. An already-existing lot (same `lotCode`) keeps its current status — only newly created lots
   receive `HOLD`. Business recommendation: use a fresh `lotCode` per production receipt.

### 9.5 Transactional Outbox (Phase 2+)

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

```yaml
management:
  endpoints.web.exposure.include: health, info, metrics, prometheus
  endpoint.health.show-details: when-authorized   # not exposed public
```

Key metrics to instrument (currently partially done via Actuator defaults):

| Metric | Type | When |
|---|---|---|
| `auth.login.success` | counter | AuthService.login success |
| `auth.login.failure` | counter | AuthService.login fail |
| `rate.limit.hit` | counter + tags(ruleId) | RateLimitFilter / UserRateLimitFilter |
| `mrp.run.duration` | timer | MrpService (Phase 2) |
| `db.pool.active` | gauge | HikariCP (auto via Actuator) |
| `redis.errors` | counter | TokenStoreService error catch |

### 10.3 Security Note

`/actuator/health` and `/actuator/info` are public (permitted in `SecurityConfig`). Detail is `when-authorized`. `/actuator/metrics` and `/actuator/prometheus` require auth — do not expose publicly without authentication in production.

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
  mapper/<Name>Mapper.java            # static toResponse(entity) method
```

### 11.2 ErrorCode Convention for New Modules

Create a new enum per domain:
```java
// e.g. module/inventory → InventoryErrorCode
public enum InventoryErrorCode implements ErrorCode {
    INSUFFICIENT_STOCK("INV_001", "Insufficient stock", HttpStatus.UNPROCESSABLE_ENTITY),
    ...
}
```

Prefix codes by domain: `INV_`, `BOM_`, `WO_`, `MRP_`, `QC_`.

### 11.3 Migration Naming

```
V1__create_base_schema.sql
V2__seed_admin_user.sql
V3__create_organization.sql     ← next: Organization scope
V4__create_inventory.sql
V5__create_audit_logs.sql       ← already exists
V6__create_bom_routing.sql
```

> **Rule**: Never modify existing migration files. New changes = new migration version.

---

## 12. What's Not Yet Implemented (Honest Gaps)

| Feature | CLAUDE.md / Design | Reality | Phase |
|---|---|---|---|
| RTR reuse detection (`auth:refresh:used:{tokenId}`) | Documented in §4.12 | Not in `TokenStoreService` | Phase 2 |
| Password reset flow | Documented in §4.14 | Not implemented | Phase 2 |
| Absolute session timeout (30d) | Documented in §4.15 | Not in `AuthService.refresh` | Phase 2 |
| Device fingerprinting | Documented in §4.19 | Not implemented | Nice-to-have |
| Transactional outbox | ADR above | Not implemented | Phase 2 |
| Organization / plant / warehouse | ADR-001 above | Not implemented | Phase 1 next |
| Inventory, BOM, Work Order, MRP | Roadmap | Not started | Phase 2+ |
| Structured JSON logging (prod) | Noted in CLAUDE.md | Logback pattern only | Phase 1 cleanup |
| Custom Micrometer metrics | Listed in §10.2 | Default Actuator only | Phase 1 cleanup |

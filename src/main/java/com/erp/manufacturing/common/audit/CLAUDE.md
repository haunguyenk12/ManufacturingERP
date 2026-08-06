# common/audit — Audit Log Architecture

> Tách từ `CLAUDE.md` §9 (2026-07-25). Chỉ nạp khi agent làm việc trong `common/audit/**`
> (`AuditLog`, `AuditLogEvent`, `AuditLogListener`, `AuditableAspect`, `AuditAction`, ...).

> **Mục tiêu**: Ghi mọi hoạt động quan trọng của người dùng không block luồng chính, decoupled với business logic, dễ query. Lưu đủ thông tin để trả lời được: **ai làm gì, lúc nào, thay đổi gì cụ thể**.

## Việc bắt buộc audit (từ `AGENTS.md` §9)

Thao tác sau phải publish `AuditLogEvent` (qua `@Auditable` hoặc gọi trực tiếp với auth events):
- Create/update/deactivate master data
- BOM create/update/activate/deactivate
- Inventory receive/issue/adjust
- Work Order create/release/cancel/issue/receipt/complete
- Permission/role/scope changes

Bulk/report/read-only operation **không** cần ghi field-level changes (`audit_log_changes`, Phase 2) — chỉ cần 1 row `audit_logs` tổng quát.

## 9.1 Tổng Quan Thiết Kế

```
Request
  │
  ├─ TraceIdFilter         → set traceId, clientIp vào MDC + attribute
  ├─ RateLimitFilter       → IP blacklist + IP-scope rate limit
  ├─ JwtAuthFilter         → set userId vào MDC + attribute
  ├─ UserRateLimitFilter   → USER-scope rate limit
  │
  ├─ Controller / Service
  │     └─ @Auditable(action, entityType)  ← AOP
  │           │
  │           └─ publish AuditLogEvent (Spring ApplicationEvent)
  │                         │
  │                   [Async thread – auditExecutor]
  │                         │
  │                   AuditLogListener
  │                     └─ INSERT audit_logs        (1 row – khái quát)
  │
  │              [TODO Phase 2]
  │                     └─ INSERT audit_log_changes (N rows – chi tiết từng field)
  │
  └─ Response

Auth events → AuditLogService.logAuth() trực tiếp (không dùng AOP)
```

**Nguyên tắc thiết kế**:
- Audit log **KHÔNG** ảnh hưởng response time (async hoàn toàn)
- Business logic **KHÔNG** biết về audit log (decoupled qua Spring Events + AOP)
- Context (`userId`, `ip`, `traceId`) truyền explicitly trong `RequestContext` — không qua ThreadLocal qua async boundary

---

## 9.2 Database Schema

> 🔴 **Sửa (`C2-1`, 2026-08-06): đoạn dưới đây từng ghi sai.** Cả hai bảng `audit_logs` **và**
> `audit_log_changes` đã tồn tại từ **`V6`** (không phải chỉ `audit_logs` từ `V5`, và không phải
> "chưa có migration" cho `audit_log_changes` như dòng cũ ghi). Cái thật sự chưa có là **code ghi vào**
> `audit_log_changes` — `AuditableAspect` chưa collect field-level diff, `0` call site insert
> (§9.6/§9.8 đúng ở điểm này). `C2-1` thêm được **phía đọc**: `GET /audit-logs/{id}` join thật vào
> bảng này qua `AuditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc` — `changes[]` genuinely
> rỗng hôm nay vì chưa ai ghi, không phải vì bảng không tồn tại hay vì hardcode `[]`.
> `AuditLogController`/`AuditLogQueryService` mới nằm ở `common/audit/controller/`,
> `common/audit/` (tách khỏi `AuditLogService` — service đó chỉ publish, không đọc).

### 9.2.1 Bảng Hiện Tại – `audit_logs`

#### Bảng 1: `audit_logs` (Khái quát – 1 row / hành động)

```sql
-- V5__create_audit_logs.sql
CREATE TABLE audit_logs (
    audit_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,                    -- null nếu chưa auth (login attempt)
    username    VARCHAR(100),            -- snapshot tại thời điểm thao tác
    action      VARCHAR(100) NOT NULL,   -- AuditAction enum: LOGIN, WORK_ORDER_CREATED...
    entity_type VARCHAR(100),            -- tên entity: WorkOrder, BomHeader (null với auth)
    entity_id   VARCHAR(255),            -- PK của entity bị tác động
    description TEXT,                    -- mô tả bổ sung (optional)
    status      VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',  -- SUCCESS | FAILURE
    client_ip   VARCHAR(45),
    user_agent  VARCHAR(512),
    trace_id    VARCHAR(32),             -- correlate với log file
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
    -- KHÔNG có updated_at, deleted_at: audit log là IMMUTABLE
);

CREATE INDEX idx_audit_user_id    ON audit_logs(user_id);
CREATE INDEX idx_audit_action     ON audit_logs(action);
CREATE INDEX idx_audit_entity     ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_trace_id   ON audit_logs(trace_id);
CREATE INDEX idx_audit_user_time  ON audit_logs(user_id, created_at DESC);
```

#### Bảng `audit_log_changes` (Chi tiết field – **bảng đã có từ `V6`, code ghi vẫn chưa có**)

> 🔴 Sketch dưới đây là **bản phác thảo gốc, lệch schema thật** — giữ lại chỉ để thấy ý định ban đầu.
> Bảng thật (`V6`, xem `AuditLogChange.java`) dùng `change_type` **enum** (`CREATE`/`UPDATE`/`DELETE`,
> không phải `value_type VARCHAR` tự do) và `old_value`/`new_value` kiểu `jsonb` (không phải `TEXT`).
> Việc còn thiếu là **ghi dữ liệu vào bảng này**, không phải tạo bảng.

```sql
-- Bản phác thảo gốc (lệch thật, xem cảnh báo ở trên) — schema thật nằm trong V6, không sửa ở đây (C5)
CREATE TABLE audit_log_changes (
    change_id   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id    UUID        NOT NULL REFERENCES audit_logs(audit_id) ON DELETE CASCADE,
    field_name  VARCHAR(100) NOT NULL,   -- tên field bị thay đổi, e.g. "status", "quantity"
    old_value   TEXT,                   -- giá trị cũ (null nếu là thao tác CREATE)
    new_value   TEXT,                   -- giá trị mới (null nếu là thao tác DELETE)
    value_type  VARCHAR(50)             -- kiểu dữ liệu: STRING, NUMBER, BOOLEAN, JSON
);

CREATE INDEX idx_changes_audit_id  ON audit_log_changes(audit_id);
CREATE INDEX idx_changes_field     ON audit_log_changes(field_name);
-- Không cần index created_at vì luôn query qua audit_id
```

#### Quan Hệ & Khi Nào Dùng Bảng Nào

| Hành động | `audit_logs` | `audit_log_changes` |
|---|---|---|
| LOGIN, LOGOUT, SESSION_KICKED | ✅ 1 row | ❌ Không có (không có entity thay đổi) |
| WORK_ORDER_CREATED | ✅ 1 row | ✅ Tất cả field của entity mới (old=null) [TODO Phase 2] |
| WORK_ORDER_UPDATED | ✅ 1 row | ✅ Chỉ các field **thực sự thay đổi** (old ≠ new) [TODO Phase 2] |
| WORK_ORDER_DELETED | ✅ 1 row | ✅ Tất cả field của entity cũ (new=null) [TODO Phase 2] |
| MRP_RUN (bulk) | ✅ 1 row | ❌ Quá nhiều; ghi summary vào `description` |
| INVENTORY_ADJUSTED | ✅ 1 row | ✅ `quantity`: old=50, new=45 [TODO Phase 2] |

> **Quy tắc**: Chỉ ghi `audit_log_changes` khi action là **CREATE / UPDATE / DELETE** trên business entity. Auth events và bulk operations không cần chi tiết field.

#### Ví Dụ Query Hữu Ích

```sql
-- Xem lịch sử thay đổi của 1 Work Order
SELECT al.created_at, al.username, al.action,
       alc.field_name, alc.old_value, alc.new_value
FROM audit_logs al
LEFT JOIN audit_log_changes alc ON al.audit_id = alc.audit_id
WHERE al.entity_type = 'WorkOrder' AND al.entity_id = '{id}'
ORDER BY al.created_at DESC;

-- Tìm ai đã thay đổi field "status" trong 7 ngày qua
SELECT al.username, al.created_at, alc.old_value, alc.new_value
FROM audit_log_changes alc
JOIN audit_logs al ON alc.audit_id = al.audit_id
WHERE alc.field_name = 'status'
  AND al.created_at >= NOW() - INTERVAL '7 days';
```

---

## 9.3 BaseEntity – JPA Auditing

Mọi entity kế thừa `BaseEntity` để nhận:
- `createdAt`, `updatedAt` → tự động qua `@CreatedDate`, `@LastModifiedDate`
- `createdBy`, `updatedBy` (UUID) → tự động qua `@CreatedBy`, `@LastModifiedBy` từ `SecurityAuditorAware`
- `version` (Long) → Optimistic locking qua `@Version`

## 9.4 RequestContext – Truyền Context qua Async

`RequestContext` là record bất biến chứa snapshot thông tin request tại thời điểm gọi:
- `userId`, `username`, `clientIp`, `userAgent`, `traceId`
- Được tạo từ `SecurityContextHolder` + MDC **trên request thread**
- Truyền explicitly vào `AuditLogEvent` → không bị mất khi qua async thread

## 9.5 AuditLogEvent & AuditLogListener

**Current behavior (implemented)**:
- **`AuditLogEvent`**: record chứa `RequestContext`, `action`, `entityType`, `entityId`, `description`, `status`
- **`AuditLogListener`**: lắng nghe event, chạy trên `auditExecutor`
  1. INSERT vào `audit_logs` → lấy `audit_id`
- Dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` → chỉ ghi sau transaction chính commit

**Phase 2 planned behavior**:
- Thêm **`FieldChange`** record chứa `fieldName`, `oldValue`, `newValue`, `valueType`
- Mở rộng `AuditLogEvent` để chứa `List<FieldChange>`
- Sau khi INSERT `audit_logs`, batch INSERT field changes vào `audit_log_changes`

## 9.6 [TODO Phase 2] So Sánh Change Detection

**Cách lấy `fieldChanges` trong `@Auditable` AOP**:

| Cách | Ưu điểm | Nhược điểm |
|---|---|---|
| So sánh entity trước/sau trong service thủ công | Kiểm soát hoàn toàn | Phải code thêm ở mọi service |
| Hibernate `@EntityListeners` PreUpdate + PostUpdate | Tự động, không cần sửa service | Khó lấy context (userId, traceId) |
| **AOP bắt argument request + response, so sánh** | Cân bằng giữa tự động và kiểm soát | SpEL phức tạp hơn |

> **Phase 2 direction**: Service method `update` nhận `OldSnapshot` và `NewSnapshot` rồi truyền vào `AuditLogEvent`. `@Auditable` AOP sẽ collect field changes thông qua SpEL expression.

## 9.7 AuditAction Enum (Tập Trung)

```
// Auth
LOGIN, LOGOUT, LOGOUT_ALL, TOKEN_REFRESHED,
SESSION_KICKED, LOGIN_FAILED, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED,
SUSPICIOUS_TOKEN_REUSE, SESSION_ABSOLUTE_TIMEOUT, PASSWORD_RESET, PASSWORD_CHANGED,

// User management
USER_CREATED, USER_UPDATED, USER_DELETED,
ROLE_ASSIGNED, ROLE_REVOKED,

// Manufacturing
BOM_CREATED, BOM_UPDATED, BOM_DELETED,
WORK_ORDER_CREATED, WORK_ORDER_UPDATED, WORK_ORDER_RELEASED, WORK_ORDER_COMPLETED,
MRP_RUN, INVENTORY_ADJUSTED,

// System / Admin
RATE_LIMIT_EXCEEDED, CONFIG_CHANGED
```

## 9.8 @Auditable AOP – Tự Động Audit CRUD

Gắn annotation `@Auditable` lên service method → tự động publish audit event.

- `action`: `AuditAction` enum value
- `entityType`: tên entity (e.g. `"WorkOrder"`)
- `entityIdExpression`: SpEL lấy entityId từ return value hoặc param

> **Current limitation**: `@Auditable` hiện chỉ ghi entity-level event vào `audit_logs`; chưa có `captureChanges` và chưa collect `FieldChange`. Field-level audit thuộc Phase 2.

> **Lưu ý**: Auth events không dùng AOP (SecurityContext chưa set lúc login). Auth events gọi `auditLogService.logAuth()` trực tiếp.

## 9.9 MDC Propagation sang Async Thread

`@Async` tạo thread mới → MDC bị mất theo mặc định.

**Giải pháp**: `MdcTaskDecorator` copy MDC map từ request thread sang async thread.

Cấu hình `AsyncConfig`:
- `auditExecutor`: corePool=2, maxPool=5, queue=500, prefix=`audit-`, decorator=`MdcTaskDecorator`
- `RejectedExecutionHandler`: `CallerRunsPolicy`

## 9.10 Filter Execution Order

```
TraceIdFilter (Order 1)        → MDC: traceId, clientIp; attr: traceId, clientIp
RateLimitFilter (Order 2)      → IP blacklist + IP-scope rate limit
JwtAuthFilter (Order 3)        → MDC: userId; attr: authenticatedUserId
UserRateLimitFilter (Order 4)  → USER-scope rate limit
Controller / Service
  └─ @Auditable AOP → publish AuditLogEvent (entity-level)
[Async thread] AuditLogListener
  ├─ INSERT audit_logs
  └─ [TODO Phase 2] BATCH INSERT audit_log_changes
```

## 9.11 Tóm Tắt Toàn Bộ Cải Tiến

| Thay đổi | Trạng thái | Lý do |
|---|---|---|
| Bảng `audit_logs` | ✅ Current | Ghi mọi action (LOGIN, LOGOUT, entity events) |
| `GET /audit-logs`, `GET /audit-logs/{id}` (đọc) | ✅ Current (`C2-1`) | `AuditLogQueryService` + `AuditLogController` (`common/audit/`), `PERM_AUDIT_READ` ADMIN-only. `plant_id` filter thêm ở `V54` (nullable, không backfill, không populate real-time — xem javadoc `AuditLog.plantId`) |
| `audit_log_changes` (bảng + đọc) | ✅ Current (`V6` + `C2-1`) | Bảng đã tồn tại từ `V6`; `GET /audit-logs/{id}.changes[]` join thật vào nó từ `C2-1`. Rỗng hôm nay vì chưa ai ghi |
| `audit_log_changes` (**ghi** — field-level diff capture) | 🔜 Phase 2 | Mở rộng `@Auditable`/`AuditableAspect` để collect `FieldChange` — chưa xếp lịch, ngoài phạm vi `C2-1` |
| `JwtAuthFilter` set `authenticatedUserId` vào MDC + attr | ✅ Current | `RequestContext.capture()` cần userId |
| `TraceIdFilter` set `clientIp` vào attr | ✅ Current | Không cần inject `HttpServletRequest` khắp nơi |
| `BaseEntity` có `updatedBy` | ✅ Current | JPA Auditing tự điền người sửa cuối |
| `MdcTaskDecorator` trong AsyncConfig | ✅ Current | TraceId + userId không mất khi log async |
| `AuditAction` enum tập trung | ✅ Current | Tên event nhất quán, dễ query/filter |
| `@TransactionalEventListener(AFTER_COMMIT)` | ✅ Current | Không ghi audit khi transaction rollback |
| Tách `UserRateLimitFilter` (Order 4) | ✅ Current | USER-scope rate limit chạy sau JWT auth |
| RTR – key `auth:refresh:used:{tokenId}` | ✅ Current (`D8a`) | Phát hiện token bị đánh cắp và reuse → audit `SUSPICIOUS_TOKEN_REUSE` (status `FAILURE`, qua `logAuthFailure`) |
| `auth:reset:{token}` trong Redis | ✅ Current (`D8c`) | Forgot password flow single-use, TTL 15m, + reverse index `auth:reset:user:{userId}` để request mới invalidate token cũ. Audit `PASSWORD_RESET`/`ACCOUNT_UNLOCKED` |
| Absolute session timeout 30 ngày | ✅ Current (`D8b`) | Ngăn session sống mãi dù user vẫn active → audit `SESSION_ABSOLUTE_TIMEOUT` (status `FAILURE`, qua `logAuthFailure` — request refresh đó **đã thất bại**, dù nguyên nhân là chính sách chứ không phải tấn công) |

## 9.12 C2-1 — Audit Logs Read API (2026-08-06)

`BACKEND_CAPSTONE2_API_GAPS.md §3.3`, đợt 1 (đọc dữ liệu event đang có — FE xác nhận `changes[]` rỗng
dùng được, không cần chờ diff capture, xem `docs/capstone2-api-gap-response.md §5` câu 1). Migration
`V54` (cột `plant_id`) + `V55` (seed `PERM_AUDIT_READ`, ADMIN-only — chốt với user).

**Kiến trúc:** `AuditLogQueryService` (mới) tách khỏi `AuditLogService` — service cũ tài liệu rõ là
facade **chỉ publish**, không đọc; giữ tách đúng như `TokenStoreService`/`AuthService` đã tách
read/write hạ tầng khỏi service nghiệp vụ. `AuditLogController` nằm ở `common/audit/controller/` —
`common/*` trước đó chưa có controller nào (auth controllers nằm ở `module/auth`), nhưng audit không
thuộc về module nghiệp vụ nào, nên đặt cạnh phần còn lại của package `common/audit` là ít xáo trộn
nhất, không phải dựng `module/audit/` mới cho một cặp endpoint.

🔴 **Bug thật phát hiện khi viết `AuditLogRepositoryIT`, biến thể MỚI của lớp lỗi `lower(bytea)`
(`CLAUDE.md §0.24`, đã bắt được 3 lần trước ở `concat`/`like`).** Lần này **không** liên quan
`concat`/`like` — filter `from`/`to` là so sánh thuần `>=`/`<=`. Một tham số `Instant` **chỉ** xuất
hiện ở vế `:from IS NULL` (không có occurrence nào khác cho Postgres suy type) làm nổ
`could not determine data type of parameter` ngay ở bước Parse, **trước khi** Hibernate kịp gán type
qua `setObject`. Cùng gốc rễ (Postgres không tự suy được type cho placeholder đứng một mình), khác
biểu hiện (không cần `concat`/`like` để kích hoạt — bất kỳ tham số nào CHỈ xuất hiện trong `IS NULL`
đều có nguy cơ, tuỳ driver/version). Sửa bằng đúng công thức đã có: `cast(:from as timestamp) IS NULL`
— chỉ cast vế `IS NULL`, **không** cast vế so sánh `a.createdAt >= :from` (giữ nguyên semantics
`timestamptz`). Các filter UUID/String khác trong cùng query **không** cần cast — đã có tiền lệ y hệt
(`WorkOrderRepository.productItemId`, `UomRepository`'s `status`) chạy đúng ở dạng bare `IS NULL`
nhiều năm nay, nên đây không phải quy tắc "luôn cast mọi filter" mà là "cast khi tham số không có
occurrence nào khác cho Postgres bám vào" — với `Instant`/timestamp so sánh bằng `>=`/`<=` (không phải
`=`), rủi ro này lộ ra rõ hơn. Test bảo vệ: `AuditLogRepositoryIT.search_filtersByCreatedAtRange`
(mock repository — `AuditLogQueryServiceTest` — **không** bắt được lỗi này, đúng bài học `R7`).

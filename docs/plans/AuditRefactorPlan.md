# Audit Refactor Plan

> Ngày lập: 2026-08-22  
> Phạm vi: `common/audit/**`, request/security context, async configuration, các service đang phát
> audit event, schema `audit_logs`/`audit_log_changes`, Audit Read API, tài liệu và test liên quan.  
> Đây là **kế hoạch**, chưa triển khai thay đổi trong `src/main`.

## 0. Kết luận và định hướng

Audit hiện tại có nền tảng tốt cho CRUD activity log: `AuditAction` tập trung, `@Auditable`, event
async, bảng summary/detail, API đọc ADMIN-only và độ phủ rộng ở các module. Tuy nhiên thiết kế hiện
tại chưa đủ tin cậy cho production audit trail vì:

1. Auth event phát ngoài transaction và failure event thuộc transaction rollback có thể không tới
   `@TransactionalEventListener(AFTER_COMMIT)`.
2. `X-Trace-Id`/`User-Agent` dài hơn schema có thể làm audit insert thất bại sau khi nghiệp vụ đã
   commit.
3. Annotation chỉ lấy ID từ return value; method `void`, multi-entity command, nested aggregate và
   background job khó mô tả chính xác.
4. `plantId` chưa được populate; API không trả `entityId`; một số event RBAC/BOM không xác định đủ
   đối tượng bị tác động.
5. Queue in-memory không có retry/outbox/dead-letter; lỗi bị swallow; “append-only” chưa được DB
   cưỡng chế.
6. Field diff dựa trên giao giữa JPA entity trước và response DTO sau nên không đáng tin với
   collection/nested aggregate.
7. Chưa có retention/partition, tamper evidence, metrics/alert và integration test cho toàn pipeline.
8. Tài liệu FE/audit đang chứa nội dung cũ mâu thuẫn với implementation mới.

**Định hướng:** giữ `@Auditable` làm đường nhanh cho CRUD đơn giản, nhưng đưa việc ghi nhận về một
`AuditRecorder` có model giàu ngữ nghĩa và persistence qua **transactional outbox**. Các nghiệp vụ
phức tạp dùng descriptor/enricher riêng thay vì tiếp tục nhồi thêm heuristic vào aspect.

## 1. Mục tiêu

1. Không mất success audit sau khi business transaction đã commit.
2. Ghi được failure/attempt kể cả khi business transaction rollback hoặc không tồn tại.
3. Không cho input do client kiểm soát làm audit insert thất bại.
4. Trả lời chính xác: **ai**, **làm gì**, **với đối tượng nào**, **ở scope nào**, **khi nào**, **kết
   quả gì**, **thay đổi gì**, **từ nguồn nào**.
5. Hỗ trợ HTTP request, auth flow, scheduled job, batch và message consumer mà không phụ thuộc
   `RequestContextHolder`.
6. CRUD mới vẫn chỉ cần annotation; command phức tạp có extension point rõ ràng.
7. Audit persistence có retry, idempotency, quan sát được và phục hồi được sau restart.
8. App runtime không thể update/delete audit rows; retention chỉ chạy qua maintenance path được kiểm
   soát.
9. Giữ dữ liệu lịch sử và duy trì tương thích API trong giai đoạn chuyển đổi.

## 2. Ngoài phạm vi

- Không backfill `entityName` hoặc `plantId` bằng tên/scope hiện tại nếu không thể chứng minh đó là
  snapshot đúng tại thời điểm lịch sử.
- Không audit mọi GET/read operation. Chỉ audit read nhạy cảm khi có yêu cầu riêng (export dữ liệu,
  đọc secret/configuration, v.v.).
- Không lưu nguyên request/response body.
- Không biến audit thành application log hoặc distributed tracing replacement.
- Không mở quyền Audit Read cho MANAGER/OPERATOR trong refactor này.
- Không thay đổi nghiệp vụ của các module ngoài metadata/audit hook cần thiết.

## 3. Bất biến bắt buộc

1. Một business transaction rollback **không** được sinh `SUCCESS` audit.
2. Một command thất bại đã đi qua service boundary phải có `FAILURE` audit nếu policy yêu cầu.
3. Một mutation commit thành công sinh đúng **một** logical audit event; retry dispatcher không tạo
   duplicate.
4. Audit failure không được thay đổi business response theo policy mặc định `FAIL_OPEN`; các action
   bảo mật/RBAC có thể cấu hình `FAIL_CLOSED` sau khi có quyết định sản phẩm rõ ràng.
5. Không lưu password, raw token, authorization header, credential, secret, hash hoặc exception
   message chưa sanitize.
6. Mọi string trước khi persist phải được normalize/cap theo schema; không dựa vào DB exception để
   truncate.
7. Historical row không được “làm giàu” bằng dữ liệu hiện tại nếu việc đó làm sai snapshot lịch sử.
8. App DB role chỉ có `INSERT/SELECT` trên bảng audit và quyền cần thiết trên outbox; không có
   `UPDATE/DELETE` trên immutable audit rows.
9. Mọi breaking/additive API change phải có contract test và handoff cho FE trong cùng PR.
10. Không xoá test cũ để làm build xanh; test cũ phải được nâng cấp hoặc thay thế bằng test mạnh hơn.

## 4. Kiến trúc đích

### 4.1 Luồng success trong business transaction

```text
HTTP / Job / Message
        |
        v
Business service (@Transactional)
        |
        +-- @Auditable / AuditRecorder.recordSuccess(draft)
        |         |
        |         +-- INSERT audit_outbox (cùng business transaction)
        |
        +-- COMMIT business data + outbox atomically
                              |
                              v
                    AuditOutboxDispatcher
                    claim + retry + idempotency
                              |
                              +-- INSERT audit_logs
                              +-- INSERT audit_log_changes
                              +-- INSERT audit_log_entities
                              +-- mark outbox PROCESSED
```

Nếu business transaction rollback, outbox row cũng rollback nên không thể có false `SUCCESS`.

### 4.2 Luồng failure và non-transactional/auth

```text
Command fails / auth outcome known / filter event
        |
        v
AuditRecorder.recordFailure(...) hoặc recordStandalone(...)
        |
        +-- REQUIRES_NEW: INSERT audit_outbox
        |
        v
Dispatcher -> immutable audit tables
```

Failure writer phải là bean riêng để Spring proxy áp dụng `REQUIRES_NEW`; không self-invocation.
Description phải dùng `reasonCode` + message đã sanitize, không ghi thẳng `Throwable.getMessage()`.

### 4.3 Model sự kiện chuẩn

Tạo model bất biến, dùng builder/factory thay vì constructor dài:

```java
AuditRecordDraft.builder()
    .eventId(eventId)
    .occurredAt(clock.instant())
    .action(AuditAction.PERMISSION_GRANTED)
    .outcome(AuditOutcome.SUCCESS)
    .actor(actorSnapshot)
    .primaryEntity(AuditEntityRef.of("Role", roleId, roleName))
    .relatedEntity(AuditEntityRef.of("Permission", permissionId, permissionCode))
    .scope(AuditScope.of(companyId, plantId, warehouseId))
    .source(AuditSource.HTTP)
    .request(requestSnapshot)
    .reasonCode("PERMISSION_GRANTED")
    .changes(changes)
    .metadata(allowlistedMetadata)
    .build();
```

Các type tối thiểu:

- `AuditOutcome`: `SUCCESS`, `FAILURE`.
- `AuditSource`: `HTTP`, `AUTH`, `SCHEDULED_JOB`, `MESSAGE`, `BATCH`, `SYSTEM`.
- `AuditActorSnapshot`: `userId`, `username`, tùy chọn `actorType` (`USER`/`SYSTEM`/`SERVICE`).
- `AuditEntityRef`: `relation`, `entityType`, `entityId`, `entityName`.
- `AuditScope`: `companyId`, `plantId`, `warehouseId`.
- `AuditRequestSnapshot`: `traceId`, `clientIp`, `userAgent`, `httpMethod`, `requestPath`.
- `AuditFieldChange`: giữ JSON value nhưng thêm policy/size validation tập trung.
- `metadata`: JSON object allowlist, có giới hạn depth/field count/byte size; không dùng làm nơi đổ
  tùy ý request body.

### 4.4 Annotation và extension point

Annotation phục vụ 80% CRUD đơn giản:

```java
@Auditable(
    action = AuditAction.WORK_ORDER_UPDATED,
    entityType = "WorkOrder",
    entityId = "#workOrderId",
    entityName = "#result.workOrderNo",
    plantId = "#result.plantId",
    changeMode = AuditChangeMode.AUTO
)
```

Yêu cầu expression context:

- Truy cập được named arguments (`#workOrderId`, `#request`), `#result`, `#exception` và actor/scope
  context.
- Parse/cache expression theo method; không parse lại mỗi request.
- Expression invalid phải fail test/startup nếu có thể, không chỉ log debug rồi trả null.
- Cho phép fallback rõ ràng, ví dụ ID từ result nếu có, nếu không lấy từ argument.

Không cố biểu diễn mọi multi-entity command bằng annotation. Tạo extension point:

```java
interface AuditDescriptorProvider {
    boolean supports(Method method, AuditAction action);
    AuditRecordDraft describe(AuditInvocation invocation);
}

interface AuditChangeProvider {
    boolean supports(AuditAction action, String entityType);
    List<AuditFieldChange> capture(AuditInvocation invocation);
}
```

- `DefaultCrudAuditDescriptorProvider`: annotation + SpEL.
- `JpaScalarChangeProvider`: scalar/singular association cho CRUD đơn giản.
- Provider riêng cho `BomLine`, role-permission, scope-resource, inventory movement và aggregate có
  collection.
- Service vẫn có thể gọi `AuditRecorder` trực tiếp cho event auth/system/bulk.

### 4.5 Transaction/AOP ordering

- Cấu hình order tường minh để transaction interceptor mở transaction trước, audit aspect chạy bên
  trong và insert outbox trước commit.
- Viết integration test kiểm tra advisor order thực tế; không dựa vào registration order mặc định.
- Aspect chỉ phát `SUCCESS` sau khi method hoàn tất; failure đi qua recorder `REQUIRES_NEW`.
- Method không có transaction dùng standalone path rõ ràng; không dựa vào
  `fallbackExecution=true` như một cơ chế ngầm.

## 5. Thay đổi schema dự kiến

### 5.1 Migration additive đầu tiên

Tạo `audit_outbox`:

```text
outbox_id UUID PK
event_id UUID UNIQUE NOT NULL
payload JSONB NOT NULL
status VARCHAR(20) NOT NULL       -- PENDING/PROCESSING/PROCESSED/FAILED
attempt_count INT NOT NULL
next_attempt_at TIMESTAMPTZ
last_error_code VARCHAR(100)      -- không lưu raw stack trace/message nhạy cảm
occurred_at TIMESTAMPTZ NOT NULL
created_at TIMESTAMPTZ NOT NULL
processed_at TIMESTAMPTZ
locked_at TIMESTAMPTZ
locked_by VARCHAR(100)
```

Index ít nhất: `(status, next_attempt_at, created_at)` và unique `event_id`.

Thêm additive vào `audit_logs`:

- `event_id UUID` + unique index để dispatcher idempotent.
- `company_id UUID`, dùng `plant_id` hiện có, thêm `warehouse_id UUID` nếu cần query scope.
- `outcome VARCHAR(20)` hoặc giữ wire field `status` nhưng map nội bộ sang enum. Chọn một nguồn
  canonical; không duy trì hai field có thể lệch nhau.
- `source VARCHAR(30)`.
- `reason_code VARCHAR(100)`.
- `http_method VARCHAR(10)`, `request_path VARCHAR(512)`.
- `occurred_at TIMESTAMPTZ`; giữ `created_at` là thời điểm persist để đo dispatcher lag.
- `payload_hash VARCHAR(...)` khi triển khai tamper evidence.

Tạo `audit_log_entities` cho primary/related targets:

```text
audit_entity_id UUID PK
audit_id UUID NOT NULL FK audit_logs
relation VARCHAR(20) NOT NULL     -- PRIMARY/RELATED
entity_type VARCHAR(100) NOT NULL
entity_id VARCHAR(255)
entity_name VARCHAR(255)
created_at TIMESTAMPTZ NOT NULL
```

Giữ `audit_logs.entity_type/entity_id/entity_name` trong cửa sổ tương thích; dispatcher dual-populate
primary entity vào cả cột cũ và bảng mới. Chỉ cân nhắc bỏ cột cũ ở major API/schema phase sau.

### 5.2 Constraint và giới hạn

- Đồng bộ `trace_id` với filter: chọn một giới hạn duy nhất, đề xuất 64; migration mở cột lên 64.
- `user_agent`: cap/truncate tại boundary 512; cân nhắc tăng schema lên 1024 nếu có nhu cầu thật.
- Thêm CHECK cho `outcome`, `source`, outbox `status` và `attempt_count >= 0`.
- Thêm max payload ở application và cảnh báo DB-level phù hợp.
- `event_id` bắt buộc cho row mới sau khi rollout ổn định; historical rows được phép null.
- Không backfill snapshot không đáng tin cậy.

### 5.3 Append-only và retention

- Tạo DB role/permission migration: runtime app role được `SELECT/INSERT`, không được
  `UPDATE/DELETE` trên `audit_logs`, `audit_log_changes`, `audit_log_entities`.
- Trigger bảo vệ `UPDATE/DELETE` là lớp thứ hai; chỉ maintenance role được bypass.
- Repository write-side không public `JpaRepository` đầy đủ cho business package. Tách interface
  append-only (`append`, query methods), không expose `delete*`/`save` update ngoài adapter.
- Retention không dùng row-by-row delete từ app. Partition theo `occurred_at`/`created_at` theo tháng,
  archive rồi detach/drop bằng maintenance job có manifest và audit riêng.
- Chính sách retention phải cấu hình theo môi trường, ví dụ 12/24/84 tháng; giá trị production cần
  được owner pháp lý/nghiệp vụ xác nhận trước khi bật purge.

### 5.4 Tamper evidence

Thực hiện sau khi append-only đã có:

1. Canonicalize payload và tính SHA-256/HMAC khi materialize audit row.
2. Tạo daily/partition Merkle root hoặc signed manifest.
3. Lưu anchor ngoài cùng database/application credential boundary (object storage immutable hoặc
   security-controlled store).
4. Scheduled verifier kiểm tra hash/root, phát metric và alert khi lệch.

Hash nằm cùng DB mà không có external anchor **không** đủ chống DBA-level tampering; không tuyên bố
“tamper-proof” nếu chưa có anchor độc lập.

## 6. Work breakdown theo PR

### AR-0 — Baseline và executable probes (P0)

**Mục tiêu:** khóa hành vi hiện tại và chứng minh các failure mode trước khi sửa.

Việc cần làm:

1. Ghi baseline số test toàn repo và riêng Audit.
2. Thêm integration test cho:
   - transactional success + commit;
   - transactional failure + rollback;
   - non-transactional auth success/failure;
   - long `X-Trace-Id` và `User-Agent`;
   - process/listener failure không làm business response đổi.
3. Test phải đi xuyên `ApplicationEventPublisher`/AOP/proxy/transaction/async/Postgres, không gọi
   trực tiếp `listener.handle()`.
4. Dùng Awaitility hoặc polling có timeout; không dùng sleep cố định.
5. Ghi nhận kết quả thực tế vào phần “Implementation log” cuối tài liệu khi bắt đầu triển khai.

**Acceptance:** test tái hiện được defect hiện tại; không sửa production code trong PR probe nếu đội
muốn review nguyên nhân riêng.

### AR-1 — Input hardening và safe serialization (P0)

1. Chuẩn hóa `AuditInputSanitizer` tại một boundary duy nhất.
2. `traceId`: filter/schema/model cùng max length; reject hoặc generate ID mới nếu upstream ID sai.
3. `User-Agent`, username, entity type/id/name, reason code, request path và description đều có cap.
4. Không cắt JSON giữa byte/Unicode; field change/metadata vượt quota phải:
   - lưu marker `TRUNCATED`/hash theo policy;
   - tăng metric;
   - không làm mất toàn bộ parent audit event.
5. Thay sensitive-name heuristic đơn thuần bằng:
   - annotation/config denylist (`@AuditSensitive` hoặc Jackson mix-in);
   - denylist fragment hiện có làm safety net;
   - allowlist metadata cho event thủ công.
6. Không lưu raw exception message; map sang `reasonCode` + public/sanitized description.

**Acceptance:** header/input ở giới hạn HTTP server vẫn không thể làm mất parent audit row; test có
Unicode và payload lớn.

### AR-2 — Transaction lifecycle + outbox foundation (P0)

1. Tạo migration `audit_outbox` và entity/repository riêng.
2. Tạo `AuditRecorder`, `AuditOutboxWriter` và standalone `REQUIRES_NEW` writer bean.
3. Cấu hình AOP/transaction order tường minh.
4. Chuyển success path sang insert outbox trong business transaction.
5. Chuyển failure/auth path sang standalone outbox transaction.
6. Tạo dispatcher:
   - claim theo batch bằng `FOR UPDATE SKIP LOCKED` hoặc cơ chế tương đương;
   - exponential backoff có cap;
   - idempotent qua `event_id` unique;
   - lease timeout để reclaim row kẹt `PROCESSING` sau crash;
   - trạng thái `FAILED` sau max attempts nhưng không tự xóa.
7. `auditExecutor` chỉ dùng cho dispatcher; không giữ `CallerRunsPolicy` trên request path. Khi executor
   saturated, outbox vẫn còn trong DB để xử lý sau.
8. Feature flags chuyển đổi:
   - `audit.outbox.producer-enabled`;
   - `audit.outbox.dispatcher-enabled`;
   - `audit.legacy-listener-enabled`.
9. Không bật legacy listener và outbox producer cùng lúc nếu chưa có dedupe bằng cùng `event_id`.

**Acceptance:** commit có đúng một success row; rollback không có success row nhưng có failure row;
restart giữa commit và dispatch không mất event; dispatcher retry không duplicate.

### AR-3 — Event model, context và source abstraction (P1)

1. Thêm các type tại mục 4.3 và builder/factory validation.
2. Thay `RequestContext` bằng `AuditContextProvider`:
   - HTTP provider đọc SecurityContext/request attributes;
   - system/job provider nhận actor/scope explicit;
   - message provider nhận correlation ID/producer identity explicit.
3. Không gọi `RequestContextHolder.currentRequestAttributes()` bắt buộc trong aspect.
4. Thêm `Clock` injection để test timestamp ổn định.
5. Context phải immutable trước khi đi qua async boundary.
6. Thêm `companyId`/`plantId`/`warehouseId` theo thứ tự ưu tiên rõ ràng:
   - explicit expression/descriptor;
   - entity snapshot/provider;
   - authenticated scope/header chỉ dùng khi đã cross-check permission;
   - không đoán nếu không đủ dữ liệu.

**Acceptance:** cùng recorder dùng được từ HTTP, auth, scheduled test và message test mà không cần
mock servlet request.

### AR-4 — Annotation v2 và change-provider SPI (P1)

1. Mở expression context cho arguments/result/exception.
2. Cache và validate SpEL; expression lỗi làm test/startup fail với message chỉ rõ method.
3. Thêm `AuditChangeMode`: `NONE`, `AUTO`, `CUSTOM`.
4. `AUTO` chỉ dùng cho scalar/simple association; không tuyên bố hỗ trợ collection aggregate.
5. Tạo provider riêng và test cho:
   - role-permission grant/revoke;
   - access-scope resource/assignment;
   - BOM header/line add/update/delete;
   - inventory movement/lot status;
   - work-order transition và material/receipt relationships.
6. No-op command không được ghi như mutation mới:
   - grant đã tồn tại/revoke không tồn tại phải trả outcome/metadata `NO_OP` nếu sản phẩm cần theo dõi;
   - hoặc không phát mutation audit; quyết định phải nhất quán và có test.
7. Không suy `CREATE/UPDATE/DELETE` chỉ từ hậu tố action. Annotation/provider phải truyền
   `AuditChangeType`/operation semantic explicit.

**Acceptance:** void method lấy ID từ argument; multi-entity event xác định đủ primary/related
targets; collection change có custom diff đúng.

### AR-5 — Di trú call site theo mức rủi ro (P1)

Thực hiện theo nhóm, mỗi nhóm là một PR nhỏ có service + test + docs:

1. **Auth/security:** login, login failed, lock/unlock, refresh, suspicious reuse, timeout, logout,
   password reset/change, session kick; quyết định rate-limit audit có dedupe/sampling để tránh DB
   amplification khi bị tấn công.
2. **RBAC/organization:** role, permission grant/revoke, scope/resource, user-role-scope assignment,
   company/plant/warehouse. Mọi grant/revoke phải có cả hai entity refs.
3. **BOM/routing/master data:** sửa exact ID và provider cho nested line.
4. **Inventory/purchasing:** movement, lot, receipt, PO/PR và references.
5. **Work order/execution/QC:** work order, reservation, issue, receipt, WIP, scrap/rework.
6. **Planning/import/costing:** bulk summary, source/job context, không tạo field diff khổng lồ.
7. Rà 131 `AuditAction`:
   - action có call site thật;
   - action intentionally unused có lý do;
   - action stale được deprecate nhưng không rename historical string tùy tiện.
8. Thêm coverage manifest/test cho danh sách command bắt buộc audit; không chỉ kiểm enum có được
   reference ở đâu đó.

**Acceptance cho từng nhóm:** actor, action, outcome, primary entity, related entity cần thiết, scope,
trace/source và change summary đều có assertion trên row PostgreSQL thật.

### AR-6 — Read API v2 và FE compatibility (P1/P2)

1. Additive fields cho list/detail:
   - `entityId` trở lại response;
   - `outcome`, `source`, `reasonCode`, `occurredAt`;
   - scope IDs;
   - detail có `entities[]`, `changes[]`, metadata đã lọc.
2. Giữ `status`/`createdAt` trong cửa sổ compatibility nếu FE hiện dùng; document field canonical và
   deprecation date trước khi xóa.
3. Filter thêm `outcome`, related entity, source, company/warehouse; `plantId` phải hoạt động thật.
4. Validate `from <= to`, giới hạn khoảng thời gian mặc định/max nếu cần.
5. Sort allowlist; invalid sort trả 400 thay vì lỗi repository.
6. Pagination có tie-breaker `auditId` để ổn định khi timestamp trùng nhau.
7. Giữ ADMIN-only method security; test deny/allow cho mọi endpoint mới.
8. Nếu thêm export, dùng streaming/bounded range, permission riêng và bản thân export phải được
   audit; không tải toàn bộ bảng vào memory.
9. Đánh giá masking IP/User-Agent trên list và chỉ trả đầy đủ ở detail nếu privacy policy yêu cầu.

**Breaking change:** thêm `entityId` và field mới là additive; đổi/xóa `status` hoặc ý nghĩa timestamp
là breaking và phải triển khai theo cửa sổ tương thích với FE.

### AR-7 — Append-only, tamper evidence và access control DB (P1/P2)

1. Thực hiện mục 5.3; xác minh bằng integration test dưới đúng runtime DB role.
2. Không cho controller/service có delete/update audit endpoint.
3. Thêm tamper hash + external anchor theo mục 5.4 nếu Audit được dùng cho compliance/security
   investigation; nếu chưa triển khai, tài liệu phải gọi đúng là “append-only activity trail”, không
   gọi “tamper-proof”.
4. Audit mọi maintenance/archive operation bằng credential/manifest riêng.
5. Có restore/verification drill cho archive.

### AR-8 — Retention, partition và hiệu năng (P2)

1. Đo volume event/ngày, average payload và growth của indexes trước khi chọn partition size.
2. Partition theo tháng nếu volume production chứng minh cần; migration phải có kế hoạch chuyển dữ
   liệu hiện tại không downtime hoặc maintenance window rõ ràng.
3. Retention job archive → verify checksum/count → detach/drop; không purge trực tiếp.
4. Index theo query thật, tối thiểu đánh giá:
   - `(occurred_at DESC, audit_id)`;
   - `(user_id, occurred_at DESC)`;
   - `(plant_id, occurred_at DESC)`;
   - `(action, occurred_at DESC)`;
   - entity relation `(entity_type, entity_id, audit_id)`.
5. Benchmark dispatcher batch size và DB batching cho changes/entities.
6. Ngân sách ban đầu để đo, không coi là cam kết trước benchmark:
   - success path chỉ thêm một outbox insert trong transaction;
   - dispatcher lag p95 dưới 60 giây ở tải danh định;
   - không có event `FAILED` không được alert;
   - business latency regression phải được ghi lại và phê duyệt nếu vượt ngưỡng của hệ thống.

### AR-9 — Observability và vận hành (P1/P2)

Metrics tối thiểu:

- `audit_outbox_pending_total`/gauge pending hiện tại.
- `audit_outbox_oldest_pending_age_seconds`.
- `audit_dispatch_success_total`, `audit_dispatch_failure_total`.
- `audit_dispatch_retry_total`, `audit_dead_letter_total`.
- `audit_payload_truncated_total`, `audit_sensitive_field_dropped_total`.
- `audit_record_failure_total` theo action/source nhưng không label bằng user/entity ID.
- Executor queue/active threads nếu vẫn có executor riêng.

Alert:

- Oldest pending vượt SLA.
- Có dead-letter/FAILED row.
- Failure rate tăng đột biến.
- Tamper verifier lệch hash/root.
- Outbox/table/index growth vượt capacity plan.

Log phải có `eventId`, `action`, `traceId` và error category; không log payload/old-new values mặc định.
Thêm runbook replay/reclaim/dead-letter và cách xác minh không duplicate.

### AR-10 — Tài liệu và cleanup (P2/P3)

Cập nhật trong cùng phase code tương ứng:

- `src/main/java/com/erp/manufacturing/common/audit/CLAUDE.md`.
- `docs/architecture.md` và system flow liên quan.
- `docs/fe-guide-audit-logs-and-inventory-lots.md`.
- `docs/api-guide-for-frontend.md`/OpenAPI examples.
- Root `CLAUDE.md`/`NEXT_PHASE_PLAN.md` nếu quy trình repo yêu cầu.

Xóa hoặc sửa mọi ghi chú cũ như “changes[] luôn rỗng”, “plantId filter là no-op”, “async hoàn toàn
không ảnh hưởng request” khi chúng không còn đúng. Sau cửa sổ ổn định:

- Xóa legacy listener/event path.
- Xóa feature flag chuyển đổi không còn cần.
- Deprecate constructor/model cũ.
- Chỉ xóa compatibility field ở một breaking-change phase đã phối hợp FE.

## 7. Test strategy đầy đủ

### 7.1 Unit test

- Sanitizer/length/Unicode/JSON quota.
- Sensitive field policy.
- SpEL argument/result/fallback và invalid expression.
- Default/custom change providers.
- Event builder invariant và canonical serialization/hash.
- Retry/backoff/lease state machine.

### 7.2 PostgreSQL integration test

- Business commit/rollback và outbox atomicity.
- Standalone failure/auth writer `REQUIRES_NEW`.
- Dispatcher claim cạnh tranh với nhiều worker.
- Crash/reclaim `PROCESSING` lease.
- Unique `event_id` chống duplicate.
- Parent + changes + entities commit nguyên tử.
- Query/filter/index behavior với data thật.
- Runtime DB role không update/delete được audit row.
- Migration từ schema hiện tại với historical nulls.

### 7.3 Web/security contract test

- ADMIN allow; MANAGER/OPERATOR deny.
- Additive response fields và compatibility fields.
- Invalid action/sort/date range/UUID trả đúng envelope 400.
- `entityId`, related entities và plant filter hoạt động.
- Header dài không làm mất audit.

### 7.4 End-to-end scenario bắt buộc

1. Login success và login failed.
2. Suspicious refresh-token reuse.
3. Role grant/revoke: thấy role + permission.
4. BOM line update/delete: đúng line ID và diff.
5. Work order transition rollback và commit.
6. Inventory adjustment có plant/warehouse và quantity change.
7. Bulk MRP/import: một summary event, không bùng nổ field changes.
8. Restart app sau business commit nhưng trước dispatch: event vẫn xuất hiện sau restart.
9. Dispatcher DB outage rồi phục hồi: retry thành công, không duplicate.
10. Attempt sửa/xóa audit row bằng app credential bị DB từ chối.

### 7.5 Quality gates

- `mvn clean verify` với Docker/Testcontainers hoạt động.
- Không merge AR-2 trở đi nếu pipeline integration test bị skip trong CI.
- Số test cuối không thấp hơn baseline nếu không có biên bản thay thế test tương đương/mạnh hơn.
- Migration test chạy từ empty DB và từ snapshot version ngay trước migration Audit mới.
- Load test dispatcher/outbox trước khi bật production.

## 8. Rollout và rollback

### 8.1 Thứ tự deploy an toàn

1. Deploy additive migrations (`audit_outbox`, cột/bảng/index mới), chưa đổi producer.
2. Deploy dispatcher ở trạng thái tắt; chạy migration/permission smoke test.
3. Bật dispatcher; outbox đang rỗng nên không đổi hành vi.
4. Deploy producer/recorder mới theo feature flag cho environment test.
5. Chạy shadow verification/count theo action và business command; tránh dual-write duplicate bằng
   `event_id` hoặc chỉ bật một producer.
6. Bật lần lượt auth/RBAC rồi các module khác.
7. Quan sát pending age/failure/dead-letter tối thiểu một cửa sổ tải thực tế.
8. Tắt legacy listener.
9. Sau một release ổn định mới bật DB append-only restrictions, retention và cleanup legacy.

### 8.2 Rollback

- Migration additive không rollback bằng cách drop dữ liệu trong incident.
- Tắt producer mới và bật lại legacy listener chỉ khi compatibility path còn tồn tại và đã xác minh
  không dual-write.
- Dispatcher có thể dừng mà không mất outbox; khi bật lại tiếp tục xử lý.
- Không xóa outbox `FAILED`; sửa code/config rồi replay theo event ID.
- Nếu API v2 gặp vấn đề, giữ response compatibility fields và feature-flag field/filter mới nếu cần;
  không sửa historical rows.

## 9. Ma trận truy vết defect → hạng mục sửa

| Defect hiện tại | Hạng mục xử lý | Acceptance chính |
|---|---|---|
| Auth/non-transaction event bị bỏ qua | AR-0, AR-2, AR-5 | Login success/failure có row thật |
| Failure event mất khi rollback | AR-0, AR-2 | Có FAILURE, không có false SUCCESS |
| AOP/transaction order ngầm | AR-0, AR-2 | Advisor-order integration test |
| Trace ID/User-Agent làm audit insert lỗi | AR-1 | Input dài không làm mất audit |
| Method `void` không lấy được entity ID | AR-4, AR-5 | ID lấy từ argument |
| Grant/revoke không biết permission nào | AR-3, AR-4, AR-5 | Role + Permission entity refs |
| BOM line ID/diff sai hoặc rỗng | AR-4, AR-5 | Custom provider exact line diff |
| `plantId` luôn null | AR-3, AR-5, AR-6 | Plant filter trả đúng rows |
| API không trả `entityId` | AR-6 | List/detail có stable ID |
| Queue memory, không retry/restart recovery | AR-2, AR-9 | Outbox replay, no duplicate |
| CallerRunsPolicy block request | AR-2 | Request path không chạy dispatcher task |
| Append-only chỉ ở ORM | AR-7 | App role update/delete bị từ chối |
| Không tamper evidence | AR-7 | Verifier + external anchor nếu bật compliance |
| Diff heuristic theo action suffix | AR-4 | Change semantic explicit |
| Snapshot JPA vs DTO không hợp collection | AR-4, AR-5 | Provider riêng cho aggregate |
| Phụ thuộc servlet request | AR-3 | Job/message test không cần request |
| Không retention/partition | AR-8 | Policy + archive/partition test |
| Thiếu metrics/alert/runbook | AR-9 | Dashboard/alert/runbook có smoke test |
| Tài liệu mâu thuẫn implementation | AR-10 | Docs/API examples khớp contract test |
| Test mock không bắt pipeline defect | AR-0, AR-2, AR-7 | PostgreSQL end-to-end suite bắt buộc |

## 10. Thứ tự ưu tiên và dependency

```text
AR-0 Baseline/probes
  |
  +--> AR-1 Input hardening
  |
  +--> AR-2 Transaction + outbox
          |
          +--> AR-3 Event/context model
                  |
                  +--> AR-4 Annotation/provider SPI
                          |
                          +--> AR-5 Call-site migration
                                  |
                                  +--> AR-6 Read API/FE
                                  +--> AR-7 Append-only/tamper
                                  +--> AR-8 Retention/performance
                                  +--> AR-9 Observability
                                          |
                                          +--> AR-10 Docs/cleanup
```

P0 bắt buộc trước khi coi Audit đáng tin: **AR-0 → AR-1 → AR-2**.  
P1 để đạt traceability production: **AR-3 → AR-7 + AR-9**.  
P2/P3 cho vận hành dài hạn và cleanup: **AR-8 → AR-10**.

## 11. Quyết định cần chốt trước khi triển khai

1. **Failure policy:** toàn hệ thống `FAIL_OPEN`, hay RBAC/security actions `FAIL_CLOSED` khi không ghi
   được outbox?
2. **Retention production:** số tháng giữ online, số năm archive, ai được duyệt purge?
3. **Compliance level:** chỉ append-only bằng DB permission/trigger, hay cần external tamper anchor?
4. **API compatibility:** FE có thể nhận additive `entityId/entities[]` ngay không; cửa sổ giữ
   `status`/`createdAt` bao lâu?
5. **No-op semantics:** không ghi event, hay ghi `SUCCESS` với metadata `noOp=true`?
6. **Rate-limit audit:** threshold/dedupe/sampling nào để không biến request flood thành DB write
   amplification?
7. **Sensitive data:** IP/User-Agent retention và masking policy theo môi trường/pháp lý.

Các quyết định này không chặn AR-0/AR-1. Phải chốt trước khi bật AR-2/AR-5/AR-7 tương ứng ở production.

## 12. Definition of Done toàn chương trình

- Mọi scenario mục 7.4 pass trên PostgreSQL thật trong CI.
- Không còn direct use của legacy `AuditLogEvent`/listener ngoài compatibility adapter.
- Auth success/failure, transactional success/rollback và background event đều được chứng minh bằng
  row thật, không chỉ verify mock.
- Client-controlled headers/payload không thể làm mất parent audit event.
- Mọi action quan trọng có actor, outcome, primary entity, scope và related entity cần thiết.
- `plantId` filter hoạt động; detail trả stable `entityId`.
- Crash/retry không mất và không duplicate event.
- App credential không sửa/xóa được immutable audit tables.
- Pending/dead-letter/tamper/size metrics có alert và runbook.
- Retention/archive policy được phê duyệt và test restore/verify.
- Tài liệu package, architecture, FE guide và OpenAPI không còn mâu thuẫn.
- Legacy path/flags chỉ được xóa sau ít nhất một release ổn định và có rollback record.

## 13. Implementation log

| Ngày | Hạng mục | Test result | Migration | Feature flag | Ghi chú/rollback |
|---|---|---|---|---|---|
| 2026-09-02 | **Baseline (AR-0.1)** | `mvn -o clean verify` — **1184 unit / 152 class + 142 IT / 21 class**, failures = 0, errors = 0 | — | — | Số đo thật, khớp `CLAUDE.md §0.1` |
| 2026-09-02 | **AR-0 → AR-10** (một lượt) | `mvn -o clean verify` — **1225 unit / 158 class + 154 IT / 22 class**, failures = 0, errors = 0 | `V67`, `V68` (cả hai additive) | `app.audit.outbox.{producer,dispatcher,legacy-listener}-enabled`, `app.audit.retention.enabled` | Rollback: tắt `producer-enabled` + bật `legacy-listener-enabled` (hai cờ loại trừ nhau, `AuditOutboxProperties` ném lỗi nếu bật cả hai). Dispatcher dừng được mà **không mất gì** — outbox nằm trong DB |

### 13.1 Đã làm gì, ở đâu

| Hạng mục | Trạng thái | Hiện thực |
|---|---|---|
| `AR-0` Baseline + probe | ✅ | Baseline đo thật; `AuditPipelineIT` (**class IT thứ 22**, 10 case) tái hiện + khoá cả 3 defect gốc trên Postgres thật, đi qua transaction/commit/rollback thật |
| `AR-1` Input hardening | ✅ | `AuditInputSanitizer` (boundary duy nhất, áp ở `AuditRecorder` cho cả draft), `@AuditSensitive`, `AuditMetrics`; `trace_id` nới 32→64 ở `V67` |
| `AR-2` Transaction + outbox | ✅ | `audit_outbox` (`V67`), `AuditRecorder`, `AuditOutboxWriter` (`REQUIRED`), `AuditStandaloneOutboxWriter` (`REQUIRES_NEW`, **bean riêng**), `AuditOutboxProcessor` (claim `FOR UPDATE SKIP LOCKED`, backoff, lease, dead-letter), `AuditOutboxDispatcher` (`@Scheduled`), `AuditLogMaterializer` (idempotent qua `event_id`). Thứ tự AOP↔transaction ghim tường minh |
| `AR-3` Event model + context | ✅ | `model/` (`AuditRecordDraft` + builder, `AuditOutcome`, `AuditSource`, `AuditActorSnapshot`, `AuditEntityRef`, `AuditScope`, `AuditRequestSnapshot`, `AuditChangeMode`, `AuditOperation`); `context/` (`AuditContextProvider` + HTTP/explicit/resolver). `Clock` injectable |
| `AR-4` Annotation v2 + SPI | ✅ | `@Auditable` thêm `entityId`/`entityName`/`companyId`/`plantId`/`warehouseId`/`changeMode`/`operation`/`reasonCode`; `AuditExpressionEvaluator` (cache + fail-loud khi parse lỗi); `AuditDescriptorProvider`/`AuditChangeProvider` + `capturePreState` |
| `AR-5` Di trú call site | 🟡 **phần lớn** | 84 annotation nạp scope (`plantId`/`companyId`/`warehouseId`) từ chính response; 6 call site sửa entity id hỏng; provider riêng cho RBAC + BOM line. **Chưa làm:** rà toàn bộ 131 `AuditAction` để deprecate action stale, và coverage manifest cho danh sách command bắt buộc audit — xem §13.2 |
| `AR-6` Read API v2 | ✅ | 11 field mới (thuần additive, `entityId` được **khôi phục**), 6 filter mới, sort allowlist, `from<=to`, tie-breaker `auditId`, `entities[]`. `status`/`createdAt` giữ trong cửa sổ tương thích. **Chưa làm:** export endpoint, masking IP/User-Agent (cả hai chờ quyết định §11.7) |
| `AR-7` Append-only | 🟡 | Trigger `V68` (áp cho **mọi** role, kể cả superuser) + `AppendOnlyRepository` (bỏ `JpaRepository`). REVOKE role ghi thành lệnh mẫu trong `V68` — không portable trong migration. **Chưa làm:** tamper hash **anchor ngoài DB** — cột `payload_hash` đã ghi, verifier + anchor chờ quyết định §11.3. ⇒ **Gọi đúng là "append-only activity trail", KHÔNG gọi "tamper-proof"** |
| `AR-8` Retention/hiệu năng | 🟡 | `AuditRetentionService` (batch, qua GUC `audit.maintenance`, tự audit chính nó) — **mặc định TẮT** (§11.2 chưa có người duyệt). Index theo §8.4 đã thêm ở `V67`. **Chưa làm:** partition theo tháng — plan cho phép "nếu volume production chứng minh cần", chưa có số đo nào |
| `AR-9` Observability | ✅ | 3 gauge + 6 counter + `AuditOutboxHealth`. Log có `eventId`/`action`/`traceId` + error category, **không** log payload/old-new value |
| `AR-10` Tài liệu | ✅ | `common/audit/CLAUDE.md` viết lại hoàn toàn; root `CLAUDE.md §0.49`; `best-practices.md`; `architecture-decisions.md`; FE guide + api guide |

### 13.2 Cố ý CHƯA làm (không phải bỏ sót)

1. **Tamper anchor ngoài DB** (`AR-7.3`). Hash đã tính và lưu, nhưng hash nằm **cùng** database mà nó
   bảo vệ thì không chống được tampering ở mức DBA. Chờ quyết định §11.3 về compliance level.
   Tài liệu **không** được dùng chữ "tamper-proof" cho tới lúc đó.
2. **Partition theo tháng** (`AR-8.2`). Chuyển một bảng đã có dữ liệu sang partitioned là rewrite +
   maintenance window. Xoá theo lô là bước đầu trung thực; partition chờ số đo volume.
3. **Bật retention ở production** (`§11.2`). Cần chủ sở hữu pháp lý chốt số tháng và duyệt purge.
4. **Export endpoint + masking IP/User-Agent** (`AR-6.8/6.9`). Chờ §11.7 (privacy policy).
5. **Rà 131 `AuditAction`** (`AR-5.7`) và **coverage manifest** (`AR-5.8`).
6. **Xoá legacy path** (`AR-10`). `AuditLogListener` + `AuditLogEvent` + `AuditLogService` giữ làm
   adapter tương thích và cần gạt rollback; theo §8.1 chỉ xoá **sau ít nhất một release ổn định**.

### 13.3 Phát hiện phụ trong lúc làm (ghi lại để không mất)

1. 🔴 **`AuthService` không có `@Transactional` ở bất kỳ đâu** — đó là lý do gốc khiến *toàn bộ* event
   auth bị vứt bỏ, không phải một lỗi cấu hình listener. Đã xác nhận bằng cách đọc code, và
   `AuditPipelineIT.authEventWithNoTransaction_isStillRecorded` khoá lại.
2. 🔴 **`BomService.updateLine` ghi `entity_id` của BOM HEADER dưới `entityType = "BomLine"`** — id đó
   trỏ tới một dòng **có thật nhưng khác**, tệ hơn `null` vì nó chỉ người điều tra đi sai một cách
   tự tin.
3. 🔴 **Bẫy self-invocation suýt tái diễn**: bản nháp `AuditOutboxDispatcher` gọi `claimBatch()` /
   `deliver()` trên chính nó ⇒ `REQUIRES_NEW` bị bypass. Đã tách `AuditOutboxProcessor`.
4. Trigger `FOR EACH ROW` **không** chặn `TRUNCATE` — phần đó do REVOKE gánh, đã ghi trong `V68`.

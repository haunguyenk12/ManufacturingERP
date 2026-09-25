# common/audit — Audit Log Architecture

> Tách từ `CLAUDE.md` §9 (2026-07-25). Chỉ nạp khi agent làm việc trong `common/audit/**`.

> 🔴 **VIẾT LẠI 2026-09-02 (track `AR-*`, `AuditRefactorPlan.md`).** Toàn bộ mô tả cũ về
> "publish Spring event → `@TransactionalEventListener(AFTER_COMMIT)` → `auditExecutor` ghi DB"
> **không còn là đường chạy chính**. Đường đó vẫn còn trong code (`AuditLogListener`) nhưng
> **mặc định TẮT**, chỉ để làm cần gạt rollback. Nếu bạn đọc một tài liệu nào khác trong repo còn mô
> tả pipeline cũ như hiện trạng, tài liệu đó lỗi thời — nguồn đúng là file này.

---

## 9.0 Vì sao phải refactor — 3 defect gốc

Đọc mục này trước khi sửa bất cứ thứ gì trong package. Cả ba đều **im lặng**: không log, không lỗi,
không test nào đỏ.

| # | Defect | Hệ quả đo được |
|---|---|---|
| 1 | `@TransactionalEventListener(AFTER_COMMIT)` **chỉ** giao event khi thread publish đang ở trong một transaction. `AuthService` **không có `@Transactional`** ở bất kỳ đâu | **Mọi** event auth — `LOGIN`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `SUSPICIOUS_TOKEN_REUSE`, `SESSION_ABSOLUTE_TIMEOUT`, `PASSWORD_RESET` — bị **vứt bỏ hoàn toàn**. Nửa quan trọng nhất về bảo mật của audit trail chưa bao giờ tồn tại |
| 2 | Giữa business `COMMIT` và `INSERT` của listener là một khoảng **không có bảo đảm nào**: crash, executor từ chối, hoặc chính `INSERT` lỗi | Thay đổi nghiệp vụ còn, bản ghi ai làm thì mất. Và **event `FAILURE` trong transaction rollback không bao giờ được ghi** — rollback mang theo luôn bằng chứng |
| 3 | `X-Trace-Id` (filter nhận tới 64 ký tự) đổ vào cột `VARCHAR(32)`; `User-Agent` (client tự đặt, dài tuỳ ý) đổ vào `VARCHAR(512)` | **Client tự tắt được audit trail của chính mình bằng một header.** Insert chết **sau khi** business đã commit |

**Định hướng đã chọn:** giữ `@Auditable` làm đường nhanh cho CRUD, nhưng đưa việc ghi về một
`AuditRecorder` với model giàu ngữ nghĩa, persist qua **transactional outbox**.

---

## 9.1 Pipeline hiện tại

```
Request / Job / Message
   │
   ├─ AuditContextResolver          → ai đang thao tác, qua transport nào (KHÔNG bắt buộc có servlet request)
   │
   ├─ Business service (@Transactional)
   │     └─ @Auditable  →  AuditableAspect  →  AuditRecorder
   │            │
   │            ├─ recordSuccess  → AuditOutboxWriter          (REQUIRED  → nhập vào transaction nghiệp vụ)
   │            └─ recordFailure  → AuditStandaloneOutboxWriter (REQUIRES_NEW → sống sót qua rollback)
   │                                     │
   │                                     └─ INSERT audit_outbox
   │
   ├─ COMMIT: dữ liệu nghiệp vụ + outbox row cùng lúc, hoặc không cái nào
   │
   └─ AuditOutboxDispatcher (@Scheduled, 1s)
         └─ AuditOutboxProcessor  (claim FOR UPDATE SKIP LOCKED → deliver → mark)
               └─ AuditLogMaterializer
                     ├─ INSERT audit_logs           (1 row)
                     ├─ INSERT audit_log_entities   (N row: 1 PRIMARY + n RELATED)
                     └─ INSERT audit_log_changes    (N row: field-level diff)
```

**Bất biến `B119` — outbox row nằm TRONG transaction nghiệp vụ.** Đây là toàn bộ lý do outbox tồn
tại: business commit ⇒ event chắc chắn còn; business rollback ⇒ **không thể** có `SUCCESS` giả.
`AuditOutboxWriter` dùng `Propagation.REQUIRED` — đổi nó thành `REQUIRES_NEW` là phá bất biến này mà
mọi test đơn vị vẫn xanh.

**Bất biến `B120` — event `FAILURE` phải ghi ở `REQUIRES_NEW`, và writer phải là BEAN RIÊNG.**
`AuditStandaloneOutboxWriter` tách khỏi `AuditOutboxWriter` không phải cho gọn: Spring áp
`@Transactional` qua proxy, nên gọi từ bean này sang method `REQUIRES_NEW` của **chính nó** sẽ
**bypass proxy** ⇒ annotation bị bỏ qua im lặng ⇒ mọi failure audit lại biến mất khi rollback. Đây
đúng cái bẫy đã một lần tạo lỗ hổng bảo mật thật trong repo (`CLAUDE.md §0.19` hệ quả #3).

**Bất biến `B121` — `eventId` do PRODUCER sinh và không bao giờ sinh lại.** Nó đi cùng payload,
được ghi lên `audit_logs.event_id`, và unique index ở đó là thứ biến "retry sau crash" thành
"không trùng". Sinh lại `eventId` lúc retry là biến lưới an toàn thành máy tạo bản sao.

---

## 9.2 Thứ tự AOP ↔ transaction (đừng đụng nếu chưa đọc)

`AuditableAspect` **phải** chạy **bên trong** transaction interceptor. Cả hai mặc định là
`Ordered.LOWEST_PRECEDENCE`, và **thứ tự giữa hai advisor bằng nhau là không xác định** — tức tính
nguyên tử của outbox insert sẽ phụ thuộc thứ tự đăng ký bean. Vì vậy cả hai được ghim tường minh:

| Thành phần | Order | Ghi ở đâu |
|---|---|---|
| Transaction advisor | `LOWEST_PRECEDENCE - 100` | `AuditConfig` — `@EnableTransactionManagement(order = …)` |
| `AuditableAspect` | `LOWEST_PRECEDENCE - 50` | `@Order(AuditableAspect.AUDIT_ASPECT_ORDER)` |

⚠️ Khai `@EnableTransactionManagement` ở `AuditConfig` làm Spring Boot **nhường** auto-config
transaction của nó. Giá trị vẫn thấp hơn hẳn method-security interceptor của Spring Security, nên
`@PreAuthorize` vẫn chạy **ngoài** cả hai — không đổi thứ tự authorization.

---

## 9.3 Schema

| Bảng | Migration | Vai trò | Mutable? |
|---|---|---|---|
| `audit_logs` | `V5` + `V54` + `V65` + **`V67`** | 1 row / event | ❌ trigger chặn UPDATE/DELETE (`V68`) |
| `audit_log_changes` | `V6` | field-level diff | ❌ như trên |
| `audit_log_entities` | **`V67`** | mọi đối tượng event chạm tới | ❌ như trên |
| `audit_outbox` | **`V67`** | hàng đợi công việc | ✅ **có chủ đích** — dispatcher phải chuyển trạng thái được |

**Cột thêm ở `V67`** (tất cả nullable, **không backfill**): `event_id`, `company_id`, `warehouse_id`,
`source`, `reason_code`, `http_method`, `request_path`, `occurred_at`, `metadata`, `payload_hash`.
`trace_id` nới `VARCHAR(32)` → `VARCHAR(64)`.

🔴 **Không backfill là quyết định, không phải lười.** Suy `plant_id` của một dòng lịch sử từ dữ liệu
hôm nay là **làm giả snapshot**. `NULL` ở đó nghĩa là "không ai chụp lại lúc đó", và đó là câu trả
lời đúng duy nhất.

🔴 **`occurred_at` vs `created_at` là hai thứ khác nhau, đừng gộp.** `occurred_at` = lúc hành động
xảy ra; `created_at` = lúc dòng được vật chất hoá. Giữ cả hai là thứ làm cho **độ trễ dispatcher đo
được** thay vì vô hình. Mọi query lọc/sắp theo thời gian dùng `COALESCE(occurred_at, created_at)` —
dòng trước `V67` không có `occurred_at`, lọc theo một mình cột mới sẽ làm **toàn bộ lịch sử biến
mất** khỏi kết quả.

### `audit_log_entities` — vì sao cần

Một cặp `entity_type`/`entity_id` **không mô tả nổi** một lệnh nối hai đối tượng.
`PERMISSION_GRANTED` trước đây ghi role và **bỏ mất permission** ⇒ audit trail nói được "role có thay
đổi" nhưng không nói được **được cấp quyền gì** — đúng câu hỏi duy nhất cần trả lời.
Nay: đúng **1 `PRIMARY`** + n `RELATED`. Cột cũ trên `audit_logs` **vẫn được ghi song song** cho
primary target suốt cửa sổ tương thích.

---

## 9.4 Append-only (`V68`) — bất biến `B122`

Trước `V68`, "append-only" chỉ là annotation `@Immutable` + quy ước đặt tên. `@Immutable` chặn
**app này** phát UPDATE; nó không chặn psql, không chặn migration, và không chặn chính
`JpaRepository` mà audit đang expose (`save`/`delete` được kế thừa, public).

Hai lớp, **cần cả hai**:

1. **Trigger `audit_reject_mutation()`** — áp cho **mọi** role, kể cả superuser. Đây là lớp duy nhất
   test tự động chứng minh được (Testcontainers chạy bằng superuser, nên control dựa trên GRANT sẽ
   "pass" vì lý do sai).
2. **`AppendOnlyRepository`** — repository audit **không còn** extends `JpaRepository`; không có
   method nào update/delete để ai đó lỡ tay gọi.
3. Lớp bổ sung theo môi trường: `REVOKE UPDATE, DELETE, TRUNCATE …` (mẫu lệnh ghi trong `V68`).
   🔴 Trigger `FOR EACH ROW` **không** chặn `TRUNCATE` — đó chính là phần REVOKE phải gánh.

**Đường thoát duy nhất:** GUC `audit.maintenance = 'on'`, đặt bằng `SET LOCAL` (phạm vi transaction).
Chỉ `AuditRetentionService` đặt nó.

---

## 9.5 `@Auditable` v2 và SPI

```java
@Auditable(action = AuditAction.WORK_ORDER_RELEASED,
           entityType = "WorkOrder",
           entityId = "#workOrderId",          // thấy được ARGUMENT — chạy được cả với method void
           entityName = "#result?.workOrderNo()",
           plantId = "#result?.plantId()",
           changeMode = AuditChangeMode.NONE,
           operation = AuditOperation.UPDATE)
public void release(UUID workOrderId) { ... }
```

| Thuộc tính | Ghi chú |
|---|---|
| `entityIdExpression` | **Legacy.** Root object là **return value**, không thấy argument ⇒ mọi method `void` ghi entity id `null`. ~120 call site còn dùng; **đừng thêm mới** |
| `entityId` | v2, SpEL trên context đầy đủ: `#argName`, `#args[0]`, `#result`, `#exception` |
| `companyId`/`plantId`/`warehouseId` | Thứ nạp `audit_logs.plant_id` — cột đã tồn tại từ `V54` và **`NULL` trên mọi dòng** vì chưa từng có writer nào set |
| `changeMode` | `NONE` / `AUTO` (chỉ scalar) / `CUSTOM` (provider) |
| `operation` | `CREATE`/`UPDATE`/`DELETE` **tường minh**; `INFERRED` giữ heuristic cũ |

🔴 **Heuristic cũ đoán CREATE/UPDATE/DELETE từ HẬU TỐ TÊN ENUM** (`_CREATED` → CREATE, `_DELETED` →
DELETE, còn lại → UPDATE). Nó sai cho **mọi** action không theo quy ước: `BOM_ACTIVATED`,
`WORK_ORDER_RELEASED`, `PERMISSION_GRANTED`, `ROLE_REVOKED`. Một lệnh thu hồi bị ghi thành UPDATE
không phải chuyện đặt tên — đó là khác biệt giữa "grant này bị gỡ" và "có gì đó về role thay đổi".

### Hai SPI

| Interface | Dùng khi |
|---|---|
| `AuditDescriptorProvider` | Lệnh chạm **nhiều** entity mà annotation không diễn đạt nổi |
| `AuditChangeProvider` | Diff thật nằm trong **collection con** (BOM line, role permission, scope resource) |

`capturePreState(...)` chạy **trước** khi method thực thi. Cần vì có trạng thái không thể dựng lại
sau: cấp một quyền role **đã có** và cấp quyền role **chưa có** kết thúc giống hệt nhau (quyền có
mặt, method trả `void`).

**Provider hiện có:**
- `RbacAuditDescriptorProvider` (`module/organization`) — grant/revoke + assignment; ghi đủ role +
  permission + user + scope; đánh dấu `metadata.noOp` cho lệnh không đổi gì.
- `BomLineAuditProvider` (`module/bom`) — vừa descriptor vừa change provider.
  🔴 Class implement **cả hai** SPI **bắt buộc override `order()`**: Java từ chối kế thừa hai default
  method cùng chữ ký.

---

## 9.6 Quyết định no-op (§11.5) — `metadata.noOp`

Cấp một quyền role đã giữ, hoặc thu hồi quyền role không có, được ghi là **`SUCCESS` kèm
`metadata = {"noOp": true}`**, **không** bị bỏ qua.

Lý do: một quản trị viên **đã thật sự thử** đổi đặc quyền, và bỏ im lặng sẽ để lại lỗ hổng đúng chỗ
ai đó đang dò xem mình làm được gì. Cờ `noOp` giữ người đọc khỏi nhầm nó với một thay đổi thật.

---

## 9.7 Sanitize — bất biến `B123`

`AuditInputSanitizer` là **boundary duy nhất**. Áp ở `AuditRecorder` cho **cả draft**, không ở từng
producer — để producer mới không thể quên.

| Quy tắc | Vì sao |
|---|---|
| Cắt trên **ranh giới code point** | Cắt giữa surrogate pair ⇒ Postgres từ chối `invalid byte sequence for encoding "UTF8"` — đổi lỗi insert này lấy lỗi insert khác |
| JSON quá cỡ **THAY**, không cắt | Nửa document JSON không phải JSON, mà cột là `jsonb`. Marker giữ lại "field này có đổi" + `sha256` để đối chiếu với bản sao ngoài |
| Metadata quá quota → **bỏ cả cụm** | Nửa metadata đọc y hệt metadata đầy đủ và sẽ đánh lừa người điều tra |
| **Không bao giờ** lưu `Throwable.getMessage()` thô | Message tự do thường trích dẫn chính dòng dữ liệu gây lỗi. Dùng `reasonCode` = `ErrorCode.code()` |
| Field nhạy cảm bị **bỏ**, có đếm metric | `@AuditSensitive` là khai báo có thẩm quyền; denylist theo tên chỉ là lưới đỡ |

---

## 9.8 Read API (`GET /v1/audit-logs`)

`PERM_AUDIT_READ`, **ADMIN-only**, kiểm bằng `hasPermission` (global) chứ không `hasResourceAccess` —
audit trail không thuộc sở hữu của company/plant nào, nên **không** có cross-check `X-Plant-Id`.

**Thêm ở AR-6 (thuần additive):** `entityId` (đã bị bỏ khi thêm `entityName` — client thấy được
*loại* đối tượng nhưng không có khoá nào để link/lọc), `outcome`, `reasonCode`, `source`,
`httpMethod`, `requestPath`, `companyId`, `warehouseId`, `occurredAt`, `metadata`, `entities[]`;
filter `outcome`/`source`/`companyId`/`warehouseId`/`relatedEntityType`/`relatedEntityId`.
`status` và `createdAt` **giữ nguyên** trong cửa sổ tương thích.

🔴 **Hai chi tiết dễ bỏ sót:**
1. **Sort allowlist.** Property lạ tới Hibernate thành path không hợp lệ ⇒ **500** từ trong lòng
   repository, tức gõ sai query string trông như backend hỏng. Nay trả 400.
2. **Tie-breaker `auditId`.** Một request sinh nhiều event trong cùng mili-giây; không có khoá phụ
   duy nhất thì phân trang có thể lặp một event và **không bao giờ hiện** một event khác — đúng kiểu
   làm người điều tra kết luận "thiếu dữ liệu".

`changes[].oldValue`/`newValue` **luôn là `String` hoặc `null`** (`B118`, xem §9.12) — đừng "khôi
phục" `JsonNode`.

---

## 9.9 Vận hành

| Metric | Ý nghĩa |
|---|---|
| `audit_outbox_pending_total` | độ sâu hàng đợi |
| `audit_outbox_oldest_pending_age_seconds` | 🔴 **cái để alert.** Độ sâu một mình mơ hồ: hàng đợi lớn nhưng chạy nhanh là khoẻ, một dòng kẹt một tiếng thì không |
| `audit_dead_letter_total` | mỗi giá trị khác 0 là **một lỗ thủng** trong trail |
| `audit_payload_truncated_total`, `audit_sensitive_field_dropped_total`, `audit_record_failure_total` | đếm những gì bị bỏ |

`AuditOutboxHealth` báo `DOWN` khi có dead letter (bằng chứng **đã** mất), `UP` + `backlogWithinSlo=false`
khi chỉ chậm (mọi event rồi cũng sẽ được ghi).

**Runbook:** dispatcher chết ⇒ **không mất gì**, outbox nằm trong DB, bật lại là chạy tiếp.
Dead letter ⇒ sửa nguyên nhân rồi `AuditOutboxProcessor.replayDeadLetters(n)`.
🔴 **Không bao giờ xoá dòng `FAILED`** — nó là tín hiệu duy nhất cho biết trail có lỗ.

**Retention (`AuditRetentionService`) mặc định TẮT.** Giữ trail online bao lâu là quyết định pháp
lý/nghiệp vụ có người duyệt (`AuditRefactorPlan §11.2`), không phải default do file config tự đặt.
Bật sẵn nghĩa là lần deploy đầu tiên âm thầm huỷ hồ sơ theo một con số do code bịa ra.

---

## 9.10 Cờ rollout & rollback

| Cờ | Mặc định | |
|---|---|---|
| `app.audit.outbox.producer-enabled` | `true` | ghi vào outbox |
| `app.audit.outbox.dispatcher-enabled` | `true` | rút outbox ra bảng audit |
| `app.audit.outbox.legacy-listener-enabled` | `false` | bật lại `AuditLogListener` cũ |

🔴 **Producer và legacy listener LOẠI TRỪ NHAU** — `AuditOutboxProperties` **ném lỗi lúc khởi động**
nếu bật cả hai. Hai đường sẽ ghi cùng một event logic, và **chỉ** đường outbox mang `event_id` có thể
khử trùng lặp.

---

## 9.11 Việc bắt buộc audit

Không đổi: create/update/deactivate master data · BOM create/update/activate/deactivate · inventory
receive/issue/adjust · work order create/release/cancel/issue/receipt/complete · thay đổi
permission/role/scope. Bulk/report/read-only chỉ cần 1 row `audit_logs`, **không** sinh field diff
khổng lồ.

---

## 9.12 Contract giá trị của `changes[]` (`B118`, 2026-08-25)

Cột lưu là `jsonb` nên snapshot có thể là text/số/boolean/object/array, **nhưng kiểu trên wire luôn
là `String` hoặc `null`**. Giữa 2026-08-17 và 2026-08-25 DTO mang `JsonNode`, làm một snapshot Work
Order có cấu trúc lọt sang client dưới dạng object và **làm sập** màn hình Audit của FE; springdoc
cũng công bố schema sai (`{"type":"object"}`). `AuditLogQueryService.textValue` chuẩn hoá: container →
JSON compact, scalar → dạng chuỗi trần (text **mất** dấu nháy JSON), `json null`/SQL `NULL` → `null`,
text không parse được → giữ nguyên. Nguồn: `BACKEND_AUDIT_LOG_VALUE_CONTRACT_2026-08-25.md`.

---

## 9.13 Bẫy đã trả giá — đọc trước khi sửa

1. **`@DataJpaTest` bọc mỗi test trong transaction rollback.** Test outbox phải
   `@Transactional(propagation = NOT_SUPPORTED)` + `TransactionTemplate` tường minh, nếu không mọi
   assertion đều vô nghĩa: thứ đang kiểm chính là cái gì sống sót qua commit thật và cái gì không.
2. **`@WebMvcTest`/unit test với mock repository KHÔNG bao giờ bắt được** các bất biến ở đây: commit/
   rollback, unique index, trigger, độ rộng cột. Đó là lý do `AuditPipelineIT` tồn tại (rule `R7`).
3. **`Map.of()` ném NPE khi `get(null)`** — batch resolve username phải guard `null` actor.
4. **`AuditOutboxProcessor` phải tách khỏi `AuditOutboxDispatcher`.** Scheduler gọi method
   `REQUIRES_NEW` trên chính nó ⇒ bypass proxy ⇒ lease không commit ⇒ hai worker xử lý cùng một dòng.
5. **Đừng khai `@Bean ObjectMapper`** ở bất kỳ config nào (bài học nợ #27, `CLAUDE.md §0.42`).

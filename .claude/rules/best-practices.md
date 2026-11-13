# Best Practices Checklist

> Bản đầy đủ kèm lịch sử từng sự cố: `docs/archive/rules-2026-11-12/best-practices.md`.
> Quy tắc viết test (T1–T6, R1–R10) nằm ở `.claude/rules/testing.md`, chỉ nạp khi đụng `src/test/**`.
> Trạng thái: ✅ đang áp dụng trong code · ◻ chưa làm (đừng giả định là có).

## 8.1 Security

| # | Practice | TT | Điều cần nhớ |
|---|---|---|---|
| S1 | HTTPS only | ◻ | Việc của hạ tầng triển khai; app không tự ép |
| S2 | Secret qua biến môi trường | ✅ | JWT secret ≥ 256 bit, không hardcode |
| S3 | BCrypt cost 12 | ✅ | `SecurityConfig` |
| S4 | Security headers | ◻ | Chỉ có header mặc định của Spring Security (`X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control`; HSTS khi chạy HTTPS). CSP, `Referrer-Policy`, `Permissions-Policy` ở `common/security/CLAUDE.md §4.17` là thiết kế, `SecurityConfig` chưa cấu hình |
| S5 | CORS strict | ✅ | `app.cors.*` → `CorsProperties`; wire qua `http.cors(...)` trong `SecurityConfig` (không phải `WebMvcConfigurer`, vì preflight không mang token). Origin `"*"` bị từ chối lúc khởi động |
| S6 | Input validation | ✅ | `@Valid` + validator; không ghép raw input vào query |
| S7 | Least privilege trong JWT | ✅ | Chỉ `sub`, `jti`, roles/permissions |
| S8 | Audit logging | ✅ | Transactional outbox, 3 bảng append-only, trigger `V68`. Gọi là "append-only activity trail", **không** gọi "tamper-proof". Chi tiết: `common/audit/CLAUDE.md` |
| S9 | `jti` duy nhất mỗi access token | ✅ | Để blacklist chính xác |
| S10 | Password policy | ✅ | `common/security/PasswordPolicy` (≥ 8, hoa/thường/số/đặc biệt, ≤ 72 byte). Chỉ các đường mới áp dụng (`B279`) |
| S11 | Refresh token reuse detection | ✅ | `B80` |
| S12 | Chống dò tài khoản | ✅ | Login và forgot-password trả cùng response cho mọi nhánh (`B33`, `B101`, `B281`) |
| S13 | Absolute session timeout 30 ngày | ✅ | Đánh giá khi refresh (`B81`) |
| S14 | Log sanitization | ✅ | Password/token không vào log; record request tự viết `toString()` che secret |
| S15 | Audit input hardening | ✅ | `AuditInputSanitizer` là ranh giới duy nhất. Không lưu `Throwable.getMessage()` thô, dùng `reasonCode = ErrorCode.code()`; field nhạy cảm khai `@AuditSensitive` |

## 8.2 API Design

| # | Practice | Điều cần nhớ |
|---|---|---|
| A1 | Versioning | `/api/v1/<resource>`; 5 nhóm ngoại lệ đặt `/v1` sau tên nhóm (`CLAUDE.md §3.3`). Breaking change ⇒ `v2` |
| A2 | Idempotency | POST nhạy cảm nhận `Idempotency-Key`, **lưu ở cột trên chính document row + UNIQUE** (không Redis, không TTL): `stock_movements`, `goods_receipts`, `material_issues`, `production_receipts`, `production_executions`, `mrp_runs`. Replay ⇒ trả document cũ; replay khác payload ⇒ `409 IDEMPOTENCY_CONFLICT` (`IdempotencySupport.ensureSamePayload`) |
| A3 | HTTP semantics | GET an toàn, POST tạo, PUT thay toàn bộ, PATCH một phần |
| A4 | Phân trang bắt buộc | Mặc định `size=20`, trần `100` (`PageableFactory`) |
| A5 | Controller không chứa nghiệp vụ | Validate → gọi service → bọc `ApiResponse` |
| A6 | DTO tách khỏi entity | Request và response riêng |
| A7 | Mã lỗi ổn định | Client rẽ nhánh theo `code`, không parse `message` |
| A8 | OpenAPI | Mỗi endpoint có `@Operation` |

## 8.3 Database

| # | Practice | Điều cần nhớ |
|---|---|---|
| D1 | Flyway | Mọi thay đổi schema là migration mới; không sửa migration cũ |
| D2 | Index | FK, cột lọc/sắp xếp chính |
| D3 | Chống N+1 | `@EntityGraph` / `JOIN FETCH`; dev bật Hibernate statistics |
| D4 | Optimistic locking | `@Version` trên entity sửa kiểu đọc–sửa–ghi |
| D5 | Soft delete | Đổi `status`, không hard-delete. Repo **không** có `deleted_at`/`@Where` |
| D6 | Connection pool | HikariCP, cỡ qua `DB_POOL_MAX_SIZE` (mặc định 10) |
| D7 | Read-only transaction | `@Transactional(readOnly = true)` cho method chỉ đọc |
| D8 | DTO projection | Cho query đọc nặng |

## 8.4 Code Quality

> Tiền tố `CQ` để không trùng `C1`–`C15` ở `coding-rules.md`.

| # | Practice |
|---|---|
| CQ1 | Constructor injection (`@RequiredArgsConstructor`); không `@Autowired` trên field |
| CQ2 | DTO là `record` |
| CQ3 | Không magic number: hằng `final` hoặc `@ConfigurationProperties` |
| CQ4 | Fail fast: validate đầu method |
| CQ5 | `@Transactional` ở service |
| CQ6 | Không để checked exception lọt lên controller |
| CQ7 | Log: ERROR = cần xử lý ngay, WARN = cần chú ý, INFO = sự kiện nghiệp vụ, DEBUG = chỉ dev |
| CQ8 | `orElseThrow()` có message; không `Optional.get()` |

## 8.5 Performance

| # | Practice | TT | Ghi chú |
|---|---|---|---|
| P1 | Cache kết quả đắt (cây BOM, MRP) | ◻ | Repo chưa có `@Cacheable` nào. Redis chỉ dùng cho token, session, rate limit |
| P2 | Xử lý bất đồng bộ | ◻ | MRP và report chạy đồng bộ. `@Async` chỉ có ở email (`mailExecutor`) và listener audit cũ (`auditExecutor`) |
| P3 | `FetchType.LAZY` mặc định | ✅ | Ngoại lệ duy nhất: `User.roles` là `EAGER` (cần khi dựng principal) |
| P4 | Thao tác theo lô | ✅ | `saveAll()`; `hibernate.jdbc.batch_size = 25` |
| P5 | Actuator + Micrometer | ✅ | Cổng quản trị riêng (`MANAGEMENT_SERVER_PORT`, mặc định 8081) |
| P6 | Nén response | ✅ | GZIP cho response > 1 KB |

## 8.7 Observability

- Actuator chạy ở cổng quản trị riêng; mặc định chỉ mở `health`, `info` (`MANAGEMENT_ENDPOINTS_EXPOSURE_INCLUDE`). Không có registry Prometheus trong `pom.xml`.
- Metric tự định nghĩa hiện có: chỉ của audit (`AuditMetrics`, `AuditOutboxHealth`).
- ◻ Chưa có: `auth.login.success/failure`, `rate.limit.exceeded`, `work_order.created`, `mrp.calculation.duration`.
- Log pattern kèm `traceId`, `userId`, `clientIp` (MDC). Log level lấy từ `LOG_LEVEL_*`; profile dev không ghi đè.

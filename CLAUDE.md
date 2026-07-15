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

## 1. PROJECT OVERVIEW

### 1.1 Description
Manufacturing ERP là hệ thống hoạch định nguồn lực doanh nghiệp dành riêng cho **sản xuất** (nhà máy lắp ráp, sản xuất theo quy trình). Khác với Commercial ERP (mua-bán-phân phối), hệ thống quản lý **vòng đời sản xuất đầu cuối** — từ nguyên liệu thô đến thành phẩm.

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
│   ├── security/           # JWT utils, filters, IpExtractor, TokenStoreService
│   ├── audit/              # BaseEntity, AuditLog entity, AuditLogService, Aspect
│   └── context/            # RequestContext (userId, ip, traceId) – ThreadLocal holder
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── AsyncConfig.java            # @EnableAsync + Executor truyền MDC
│   ├── JpaAuditingConfig.java      # @EnableJpaAuditing
│   ├── RateLimitProperties.java    # @ConfigurationProperties
│   └── OpenApiConfig.java
├── module/
│   ├── auth/               # AuthController, AuthService, IpSessionService
│   ├── user/
│   ├── inventory/
│   ├── bom/
│   ├── workorder/
│   └── mrp/
└── ManufacturingErpApplication.java
```

### 3.3 Quy Ước API
- Base path: `/api/v1/`
- Auth endpoints: `/api/v1/auth/**` (permit all)
- Protected: `/api/v1/**` (yêu cầu JWT hợp lệ)
- HTTP status codes: tuân thủ chặt chẽ ngữ nghĩa REST
- Phân trang: query params `page`, `size`, `sortBy`, `sortDir`

---

## 4. AUTHENTICATION & SESSION MANAGEMENT

> **Chuẩn**: Triển khai cấp senior với xử lý session hết hạn, token rotation, blacklist Redis.

### 4.1 Tổng Quan Kiến Trúc

```
Client
  │
  ├─[POST /auth/login]──────────────────────────────────────────────►
  │                                                                   AuthService
  │◄─────────── { accessToken (15m), refreshToken (7d) } ────────────
  │                   (refreshToken lưu trong Redis)
  │
  ├─[GET /api/v1/**] → BearerToken → JwtAuthFilter ─── validate ─── SecurityContext
  │
  ├─[POST /auth/refresh] → refreshToken → xoay vòng → accessToken mới + refreshToken mới
  │
  └─[POST /auth/logout] → blacklist accessToken trong Redis (TTL = thời gian còn lại)
```

### 4.2 Chiến Lược Token

| Token | Lưu trữ | TTL | Mục đích |
|---|---|---|---|
| Access Token (JWT) | Client (Memory/Header) | 15 phút | Xác thực API stateless |
| Refresh Token (opaque UUID) | Redis (server-side) | 7 ngày | Cấp access token mới |
| Blacklist | Redis SET | Đến hết hạn access token | Vô hiệu hóa token đã logout |

**Tại sao không lưu Access Token trong Redis?** → Giữ xác thực access token stateless (không cần Redis round-trip mỗi request). Chỉ blacklist khi logout hoặc bắt buộc vô hiệu hóa.

### 4.3 Schema Database

```sql
-- V1__create_users_roles.sql
users (user_id UUID PK, username, email, password BCrypt,
       status ACTIVE|INACTIVE|LOCKED,
       created_at, updated_at, created_by, updated_by, version BIGINT)

roles (role_id UUID PK, name UNIQUE, description)

user_roles (user_id FK, role_id FK, PRIMARY KEY composite)

INDEX: idx_users_username, idx_users_email
```

### 4.4 Redis Key Design

```
# Refresh Token store (value = opaque UUID refresh token)
auth:refresh:{userId}:{tokenId}                →  {refreshToken}  TTL=7d

# Access Token Blacklist (sau logout/bắt buộc vô hiệu hóa)
auth:blacklist:{jti}                           →  "1"  TTL=<thời gian còn lại của access token>

# Bộ đếm đăng nhập thất bại (brute-force protection)
auth:failcount:{username}                      →  {count}  TTL=15m

# Multi-device session (mỗi device có entry riêng)
auth:session:device:{userId}:{deviceId}        →  {clientIp}  TTL=7d (sliding)

# ── Planned (chưa implement) ────────────────────────────────────
# RTR – token đã dùng (phát hiện reuse)
auth:refresh:used:{tokenId}                    →  "1"  TTL=60s  [TODO Phase 2]

# Forgot password token (single-use)
auth:reset:{token}                             →  {userId}  TTL=15m  [TODO Phase 2]
```

### 4.5 Cấu Trúc JWT Claims

```json
{
  "sub":   "user-uuid",
  "jti":   "unique-token-id-uuid",
  "roles": ["ROLE_MANAGER", "ROLE_OPERATOR"],
  "iat":   1714000000,
  "exp":   1714000900
}
```

> JWT chỉ chứa `sub`, `jti`, `roles` – **không chứa dữ liệu nhạy cảm**.

### 4.6 Các Class Chính & Trách Nhiệm

#### `JwtTokenProvider`
- Tạo access token (JWT có ký) và refresh token (UUID ngẫu nhiên, lưu Redis)
- Validate token: chữ ký + hạn + kiểm tra blacklist
- Trích xuất claims và `jti`

#### `JwtAuthenticationFilter`
- Trích xuất Bearer token từ header `Authorization`
- Validate → set `SecurityContextHolder` + `req.attribute("authenticatedUserId")` + MDC(`userId`)
- Token hết hạn → `TokenExpiredException`; token bị revoke → `TokenRevokedException`

#### `TokenStoreService`
- Lưu/lấy/xóa refresh token trong Redis
- Xóa toàn bộ token theo userId (force logout tất cả thiết bị)
- Blacklist access token với TTL còn lại
- Kiểm tra blacklist (`isBlacklisted`)

#### `AuthService`
- `login` → validate credentials (constant-time) → cấp cặp token (có kiểm tra IP session)
- `refresh` → validate refresh token → RTR check → xoay vòng atomic → trả token mới
- `logout` → blacklist access token + xóa refresh token
- `logoutAllDevices` → xóa toàn bộ refresh token của user
- `forgotPassword(email)` → sinh reset token, gửi email (cùng response dù email tồn tại hay không)
- `resetPassword(token, newPassword)` → validate reset token → đặt mật khẩu mới → xóa token
- `adminUnlockAccount(userId)` → xóa `auth:failcount:{username}` trong Redis, set status = ACTIVE

#### `PasswordResetTokenService`
- Sinh secure random token (UUID v4), lưu Redis với TTL 15 phút
- Token chỉ dùng được 1 lần (xóa ngay khi đã dùng)
- Nếu request reset mới → overwrite token cũ (không cho tích lũy)

### 4.7 Luồng Xử Lý Session Hết Hạn

```
Kịch bản: Access token hết hạn (401)
─────────────────────────────────────
Client nhận 401 Unauthorized
  │
  ├─ Client gửi POST /api/v1/auth/refresh { refreshToken }
  │
  ├─ Server validate refresh token (basic rotation – current):
  │   ├─ Tồn tại trong Redis? → tiếp tục
  │   ├─ KHÔNG TÌM THẤY → REFRESH_TOKEN_EXPIRED
  │   └─ [TODO Phase 2] RTR reuse detection (xem 4.12):
  │         → kiểm tra auth:refresh:used:{tokenId}
  │           ├─ Key "used" tồn tại → TOKEN_REUSE_DETECTED (force logout all)
  │           └─ Key hết hạn → REFRESH_TOKEN_EXPIRED (bình thường)
  │
  ├─ [TODO Phase 2] Absolute timeout check (xem 4.15):
  │   sessionAge > 30 ngày → 401 SESSION_ABSOLUTE_TIMEOUT
  │
  └─ Nếu hợp lệ → Tạo accessToken MỚI + refreshToken MỚI (xoay vòng)
       ├─ Lưu refresh token MỚI vào Redis TRƯỚC
       ├─ [TODO Phase 2] Đánh dấu token CŨ là USED: SET auth:refresh:used:{oldTokenId} "1" TTL=60s
       └─ Trả về { accessToken, refreshToken, expiresIn }

Kịch bản: Force logout / hoạt động đáng ngờ
─────────────────────────────────────────────
Admin gọi deleteAllUserTokens(userId)
  → Toàn bộ refresh token bị xóa khỏi Redis
  → Lần refresh tiếp theo → 401 SESSION_TERMINATED
  → Client redirect về login
```

### 4.8 Bảo Vệ Brute-Force

**Cơ chế**: Redis counter theo username với TTL trượt.
- Sau **5 lần thất bại liên tiếp** → trả về `AccountLockedException` (khoá 15 phút)
- Đăng nhập thành công → reset counter
- **Quan trọng**: Dùng **Lua script Redis** để đảm bảo `increment + expire` atomic (tránh race condition khi nhiều request đồng thời)

### 4.9 Security Config

Thứ tự filter chain (quan trọng — được đăng ký trong `SecurityConfig`):

| Order | Filter | Nhiệm vụ |
|---|---|---|
| 1 | `TraceIdFilter` | Set `traceId`, `clientIp` vào MDC + request attr + response header |
| 2 | `RateLimitFilter` | IP blacklist (403) + IP whitelist bypass + IP-scope rate limit |
| 3 | `JwtAuthenticationFilter` | Validate JWT → set SecurityContext + `authenticatedUserId` vào MDC + attr |
| 4 | `UserRateLimitFilter` | USER-scope rate limit (chỉ chạy sau khi có `authenticatedUserId`) |

> **Lý do tách 2 filter rate limit**: IP-scope phải chạy trước JWT để bảo vệ unauthenticated endpoint.
> USER-scope phải chạy sau JWT vì cần `authenticatedUserId`. Gộp vào 1 filter sẽ khiến USER-scope
> không bao giờ thực thi (không có userId lúc chạy IP layer).

Permit: `/api/v1/auth/**`, `/actuator/health`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`

### 4.10 Multi-Device Session

> **Thiết kế**: Mỗi thiết bị (device) có session độc lập. User có thể đăng nhập từ nhiều thiết bị cùng lúc.

**deviceId resolution**:
- Client cung cấp `deviceId` (stable identifier, max 128 chars)
- Nếu không có → server tự sinh: `sha256(userAgent + ":" + ip)[:24]` với prefix `auto-`

```
Luồng khi login:
  1. AuthService.login(LoginRequest, clientIp)
  2. Resolve deviceId từ request
  3. SET auth:session:device:{userId}:{deviceId} = clientIp  TTL=7d
  4. Phát hành cặp token mới
  5. Khi refresh: extend TTL device session
  6. Khi logout: xóa refresh token của tokenId đó
  7. Khi logout-all: xóa tất cả auth:refresh:{userId}:* và auth:session:device:{userId}:*
```

> **Khác với single-IP enforcement**: Thiết kế hiện tại cho phép multi-device.
> Single-IP enforcement đã được loại bỏ vì không phù hợp với ERP nội bộ
> nơi user có thể làm việc trên nhiều máy tính.

### 4.11 Rate Limiting – Config-driven & Extensible

> **Design goal**: Rules định nghĩa trong `application.yml`, **override tại runtime qua Redis** mà không restart. Thêm rule mới = thêm vài dòng YAML.

#### Config Schema (`application.yml`)

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

      - id: auth-refresh-ip
        scope: IP
        pattern: "/api/v1/auth/refresh"
        limit: 20
        window-seconds: 60
        action: BLOCK

      - id: global-user
        scope: USER
        pattern: "/**"
        limit: 500
        window-seconds: 60
        action: BLOCK

      - id: mrp-user              # Tác vụ nặng – giới hạn chặt hơn
        scope: USER
        pattern: "/api/v1/mrp/**"
        limit: 5
        window-seconds: 60
        action: BLOCK
```

#### Scope & Action
- **Scope**: `IP` | `USER` | `ENDPOINT`
- **Action**: `BLOCK` (trả 429) | `LOG_ONLY` (chỉ ghi log, không block)

#### Redis Key Design – Runtime Override

```
# Counter (sliding window)
rate:counter:{ruleId}:{identifier}:{epochWindow}  →  {count}  TTL=windowSeconds+buffer

# Override runtime – admin thay đổi limit mà không restart
rate:rule:override:{ruleId}  →  hash { limit, windowSeconds }  TTL tuỳ ý

# Whitelist IP – bỏ qua rate limit
rate:whitelist:ip:{ip}  →  "1"  TTL tuỳ ý

# Blacklist IP – block hoàn toàn (403, không phải 429)
rate:blacklist:ip:{ip}  →  "lý do"  TTL tuỳ ý
```

#### Response Headers

| Header | Mô tả |
|---|---|
| `X-RateLimit-Limit` | Giới hạn request trong window |
| `X-RateLimit-Remaining` | Số request còn lại |
| `X-RateLimit-Rule` | Rule ID đang áp dụng |
| `Retry-After` | Giây cần chờ (chỉ khi 429) |

### 4.12 Refresh Token Reuse Detection (RTR) [🔜 Phase 2 – chưa implement]

> **Trạng thái**: Thiết kế đã có, chưa có code trong `AuthService.refresh`. Sẽ implement trong Phase 2.

**Mục tiêu**: Phát hiện kịp thời khi refresh token bị đánh cắp và dùng lại.

**Vấn đề với rotation đơn thuần**: Sau khi rotate, nếu token cũ bị dùng lại, server chỉ trả `REFRESH_TOKEN_EXPIRED` — không phân biệt được "hết hạn tự nhiên" với "token bị đánh cắp".

**Giải pháp RTR (planned)**:
```
Khi rotate thành công:
  → SET auth:refresh:used:{oldTokenId} "1" EX 60
  → Lưu new token, xóa old token

Khi validate refresh token và KHÔNG TÌM THẤY trong store:
  → Kiểm tra auth:refresh:used:{tokenId}
  → Nếu tồn tại (dưới 60s) → TOKEN_REUSE_DETECTED
       ├─ deleteAllUserTokens(userId)  → force logout toàn bộ thiết bị
       ├─ Ghi audit: SUSPICIOUS_TOKEN_REUSE
       └─ 401 { code: "TOKEN_REUSE_DETECTED", message: "Suspicious activity detected. Please login again." }
  → Nếu key "used" đã expire → REFRESH_TOKEN_EXPIRED (hết hạn bình thường)
```

> **Lưu ý**: TTL của key `used` (60s) đủ để phát hiện reuse kịp thời nhưng không giữ Redis lâu.

---

### 4.13 Account Enumeration Prevention

> **Mục tiêu**: Kẻ tấn công không thể dò được username hợp lệ bằng cách quan sát sự khác biệt trong response.

**Quy tắc bắt buộc**:
- `POST /auth/login`: Dù username không tồn tại hay sai mật khẩu → luôn trả cùng HTTP 401 + cùng message: `"Invalid credentials"`
- Không được trả `"User not found"` hay `"Wrong password"` — phân biệt hai lỗi này = lộ thông tin
- `POST /auth/forgot-password`: Dù email tồn tại hay không → luôn trả HTTP 200 + `"If this email exists, a reset link has been sent"`
- Khi username không tồn tại trong login → vẫn phải thực hiện `BCrypt.matches()` với dummy hash để **thời gian phản hồi tương đương** (tránh timing attack)

---

### 4.14 Account Recovery Flow [🔜 Phase 2 – chưa implement]

> **Trạng thái**: Thiết kế bên dưới là planned. Hiện tại chưa có endpoint `/auth/forgot-password` hay `/auth/reset-password` trong code.

#### Forgot Password
```
1. POST /api/v1/auth/forgot-password { email }
   → Tìm user theo email (không báo kết quả)
   → Nếu tồn tại: sinh token UUID, lưu auth:reset:{token} → { userId } TTL=15m
   → Gửi email chứa link: https://app.com/reset-password?token={token}
   → Luôn trả: 200 { code: SUCCESS, message: "If this email exists, a reset link has been sent" }

2. POST /api/v1/auth/reset-password { token, newPassword }
   → Lấy userId từ Redis bằng token
   → Nếu không tìm thấy → 401 RESET_TOKEN_INVALID (hết hạn hoặc đã dùng)
   → Validate password policy
   → Đặt mật khẩu mới (BCrypt)
   → XÓA token ngay (single-use)
   → Xóa toàn bộ refresh token của user (force logout)
   → Ghi audit: PASSWORD_RESET
```

#### Admin Manual Unlock
```
PATCH /api/v1/admin/users/{userId}/unlock
  → Yêu cầu role ADMIN
  → Xóa auth:failcount:{username} trong Redis
  → Cập nhật users.status = ACTIVE
  → Ghi audit: ACCOUNT_UNLOCKED
```

> **Lưu ý**: Tự động unlock qua TTL Redis vẫn giữ (sau 15 phút). Admin unlock là cơ chế bổ sung.

---

### 4.15 Absolute Session Timeout [🔜 Phase 2 – chưa implement]

> **Trạng thái**: Thiết kế đã có, chưa có code trong `AuthService.refresh`. Sẽ implement trong Phase 2.

> **Vấn đề**: Refresh token TTL sliding 7 ngày — user active liên tục sẽ **không bao giờ bị force logout**. Với ERP có dữ liệu nhạy cảm, đây là rủi ro bảo mật.

**Giải pháp (planned)**: Lưu `sessionCreatedAt` vào payload refresh token trong Redis.

```
Refresh token payload trong Redis:
  {
    userId,
    tokenId,
    sessionCreatedAt,   ← timestamp khi user login lần đầu
    userAgentHash       ← (optional) fingerprint
  }

Khi validate refresh:
  → Tính sessionAge = now - sessionCreatedAt
  → Nếu sessionAge > 30 ngày → SESSION_ABSOLUTE_TIMEOUT
       → Force logout (xóa toàn bộ token)
       → 401 { code: "SESSION_ABSOLUTE_TIMEOUT", message: "Session expired. Please login again." }
  → Nếu < 30 ngày → tiếp tục (sliding TTL vẫn áp dụng)
```

| Timeout | Loại | Hành vi |
|---|---|---|
| 7 ngày | Sliding (inactive timeout) | Reset mỗi khi refresh token |
| 30 ngày | Absolute (session timeout) | Bắt buộc login lại dù có active |

---

### 4.16 JWT Signing Algorithm

| | HS256 (HMAC-SHA256) | RS256 (RSA-SHA256) |
|---|---|---|
| Khóa | Symmetric (1 secret key) | Asymmetric (private ký, public verify) |
| Microservice | ❌ Phải share secret | ✅ Chỉ distribute public key |
| Performance | ✅ Nhanh hơn | Chậm hơn (RSA operation) |
| Phù hợp | Monolith / single service | Microservices |

> **Quyết định cho Capstone**: Dùng **HS256** (monolith). Secret key lưu trong env variable `JWT_SECRET` (min 256-bit). Document rõ: nếu scale lên microservice → phải migrate sang RS256 với JWK endpoint.

---

### 4.17 Security Headers Đầy Đủ

Cấu hình qua Spring Security `HeadersConfigurer` + custom filter:

| Header | Giá trị khuyến nghị | Mục đích |
|---|---|---|
| `X-Content-Type-Options` | `nosniff` | Chặn MIME sniffing |
| `X-Frame-Options` | `DENY` | Chặn clickjacking |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Bắt buộc HTTPS |
| `Content-Security-Policy` | `default-src 'self'` | Chặn XSS, inline script |
| `Referrer-Policy` | `no-referrer` | Không leak URL qua Referer |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` | Tắt API browser không cần |
| `Cache-Control` | `no-store` (chỉ auth endpoints) | Ngăn browser cache response nhạy cảm |
| `X-XSS-Protection` | `0` | Tắt XSS filter cũ (deprecated, CSP thay thế) |

---

### 4.18 Log Sanitization

> **Mục tiêu**: Token và password không bao giờ xuất hiện trong log file.

**Quy tắc bắt buộc**:
- DTO Request/Response có field nhạy cảm phải có `@ToString.Exclude` (Lombok) → không log vô tình qua `log.debug("Request: {}", req)`
- Password field trong `LoginRequest`, `RegisterRequest` phải annotate `@JsonProperty(access = WRITE_ONLY)` + `@ToString.Exclude`
- Audit log chỉ lưu `jti` của access token, **không bao giờ lưu raw token**
- Custom Logback `PatternLayoutEncoder` mask pattern Bearer token trong log: `Bearer [REDACTED]`
- Trong `GlobalExceptionHandler`, không log exception nếu chứa sensitive data trong message (ví dụ: `InvalidCredentialsException` chỉ log ở WARN level, không log stack trace)

**Mapping field nhạy cảm cần che**:
```
LoginRequest.password       → NEVER log
RegisterRequest.password    → NEVER log
RefreshRequest.refreshToken → log chỉ jti (extracted)
JWT raw string              → log chỉ sub + jti
Reset password token        → NEVER log
```

---

### 4.19 Device Fingerprinting cho Refresh Token

> **Mục tiêu**: Gắn chặt (bind) Refresh Token với môi trường thiết bị đã đăng nhập ban đầu. Ngay cả khi kẻ tấn công lấy được chuỗi Refresh Token, hắn cũng không thể dùng nó từ một thiết bị/mạng khác mà không bị phát hiện.

#### Vấn đề: Tại Sao Refresh Token Stand-alone Không Đủ?

Refresh Token hiện tại chỉ là chuỗi UUID ngẫu nhiên được lưu trong Redis. Server chỉ kiểm tra: "Chuỗi này có tồn tại trong Redis không?". Nếu có → cấp token mới, không hỏi thêm gì.

```
Kịch bản tấn công (Refresh Token Theft):
  1. Hacker đánh cắp được refresh token của user A (qua XSS, malware,
     hoặc leak từ backup Redis)
  2. Hacker dùng token đó từ máy tính của hắn (IP khác, trình duyệt khác)
  3. Server chỉ thấy: "UUID này khớp Redis" → cấp Access Token mới
  4. Hacker có quyền truy cập hệ thống như user A
  → Hệ thống KHÔNG phát hiện được điều bất thường
```

#### Giải Pháp: Fingerprint = Hash(User-Agent + IP Subnet)

**Bước 1 – Khi đăng nhập (tạo fingerprint):**

Server trích xuất 2 thông tin từ request:
- **User-Agent**: chuỗi định danh trình duyệt/OS của client (ví dụ: `Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0`)
- **IP Subnet /24**: thay vì lấy IP chính xác (`14.248.160.15`), lấy dải mạng (`14.248.160.0/24`)

> **Tại sao dùng Subnet /24 thay vì IP chính xác?**
> - User dùng điện thoại 4G → IP thay đổi liên tục dù vẫn cùng nhà mạng
> - User dùng WiFi công ty → DHCP có thể đổi IP mỗi ngày
> - Lấy IP chính xác → false positive rất cao → user xuyên bị bắt login lại
> - Subnet /24 giữ được "vùng lân cận mạng" mà không quá cứng nhắc

Sau đó hash cả hai lại:
```
Fingerprint = SHA-256( UserAgent + "|" + IPSubnet )
            = "a3f8c2d1..."  (chuỗi 64 ký tự)
```

Lưu fingerprint vào payload của Refresh Token trong Redis:
```
Redis key: auth:refresh:{userId}:{tokenId}
Payload:   {
             userId:           "uuid-user",
             tokenId:          "uuid-token",
             sessionCreatedAt: 1714000000,
             fingerprint:      "a3f8c2d1..."   ← thêm mới
           }
```

**Bước 2 – Khi refresh token (kiểm tra fingerprint):**

```
Client gửi POST /auth/refresh { refreshToken }
  │
  ├─ Server tính fingerprint từ request hiện tại:
  │     fingerprintNow = SHA-256( currentUserAgent + "|" + currentIPSubnet )
  │
  ├─ Lấy fingerprintStored từ Redis payload
  │
  ├─ So sánh:
  │   fingerprintNow == fingerprintStored ?
  │     │
  │     ├─ KHỚP → Tiếp tục xoay vòng token bình thường ✅
  │     │
  │     └─ KHÔNG KHỚP → Cảnh báo Mismatch ⚠️
  │           ├─ Mức nhẹ (WARN):  User-Agent thay đổi (cập nhật Chrome)
  │           │     → Log cảnh báo, vẫn cho phép refresh
  │           │     → Cập nhật fingerprint mới vào Redis
  │           │
  │           └─ Mức nghiêm trọng (SUSPICIOUS): IP Subnet thay đổi hoàn toàn
  │                 → Ghi audit: SUSPICIOUS_FINGERPRINT_MISMATCH
  │                 → Xóa refresh token này khỏi Redis (vô hiệu hóa)
  │                 → 401 { code: "DEVICE_MISMATCH_DETECTED" }
  │                 → (Tùy chọn) Gửi email cảnh báo cho user
```

#### Phân Loại Mismatch

| Trường hợp | Nguyên nhân phổ biến | Xử lý |
|---|---|---|
| User-Agent thay đổi nhỏ | Cập nhật Chrome/Firefox version | WARN + cập nhật fingerprint |
| IP Subnet thay đổi nhỏ | Đổi WiFi trong cùng tòa nhà | WARN + cập nhật fingerprint |
| User-Agent thay đổi hoàn toàn | Đổi hẳn trình duyệt / thiết bị | SUSPICIOUS → xóa token |
| IP Subnet thay đổi hoàn toàn | Đổi mạng khác vùng địa lý | SUSPICIOUS → xóa token |
| Cả hai thay đổi cùng lúc | Kẻ tấn công từ máy khác | CRITICAL → force logout all devices |

#### Redis Key bổ sung
```
# Flag thiết bị đáng ngờ (throttle request fingerprint check)
auth:device:suspicious:{userId}  →  {count}  TTL=1h
```

#### Trade-off Cần Cân Nhắc

| Khía cạnh | Mô tả |
|---|---|
| **False Positive** | User dùng VPN sẽ bị mismatch → cân nhắc xử lý ở mức WARN thay vì block ngay |
| **Corporate Proxy** | Nhiều user trong công ty có thể cùng IP subnet → fingerprint giả trùng nhau |
| **Mobile User** | Đổi 3G/4G/WiFi liên tục → nên dùng chế độ WARN, không block cứng |
| **Incognito Mode** | User-Agent giống browser thường → không gây vấn đề |

> **Quyết định cho Capstone**: Tính năng này ở mức **Nice-to-have**, không bắt buộc implement trong Phase 1. Tuy nhiên thiết kế sẵn trong Redis payload (`fingerprint` field) để sau này có thể bật lên mà không cần thay đổi schema. Phù hợp để trình bày trong báo cáo như một tính năng bảo mật nâng cao có tư duy sâu.

---

## 5. ERROR HANDLING

> **Chuẩn**: Tập trung, nhất quán, production-grade.

### 5.1 Unified Response Contract (TẤT CẢ response)

> **Quy tắc**: Mọi response – **success lẫn error** – đều dùng **cùng một envelope** `ApiResponse`.
> Frontend chỉ cần xử lý 1 contract duy nhất: kiểm tra `code`, đọc `result` hoặc `message`.

```json
{
  "code":    "SUCCESS",              // ErrorCode string – luôn có mặt
  "result":  { ... },               // payload (null khi error)
  "message": "Mô tả ngắn gọn"       // human-readable – luôn có mặt
}
```

| Field | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `code` | `string` | ✅ | `ErrorCode` enum name. Frontend code theo field này |
| `result` | `T \| null` | — | Data payload khi success; `null` khi error |
| `message` | `string` | ✅ | Thông báo ngắn gọn cho user / log |

> **Thông tin debug** (`traceId`, `path`) chỉ xuất hiện qua **response header** `X-Trace-Id`, không trong body.

#### Ví Dụ

```json
// Success – GET single
{ "code": "SUCCESS", "result": { "id": "abc", "status": "RELEASED" }, "message": "OK" }

// Success – danh sách phân trang
{
  "code": "SUCCESS",
  "result": {
    "content": [...], "page": 0, "size": 20, "totalElements": 150,
    "totalPages": 8, "first": true, "last": false
  },
  "message": "OK"
}

// Success – không có content (DELETE)
{ "code": "SUCCESS", "result": null, "message": "Deleted successfully" }

// Error – validation
{ "code": "VALIDATION_FAILED", "result": null, "message": "quantity: must be greater than 0; name: must not be blank" }

// Error – business rule
{ "code": "INSUFFICIENT_STOCK", "result": null, "message": "Not enough stock for material MAT-001" }

// Error – rate limit
{ "code": "RATE_LIMIT_EXCEEDED", "result": null, "message": "Too many requests. Retry after 45s." }
```

### 5.2 Phân Cấp Exception

```
RuntimeException
└── BaseBusinessException (abstract – chứa ErrorCode + HttpStatus)
    ├── ResourceNotFoundException       → 404
    ├── ResourceAlreadyExistsException  → 409
    ├── BusinessRuleException           → 422
    ├── AuthException (abstract)
    │   ├── TokenExpiredException       → 401
    │   ├── TokenRevokedException       → 401
    │   ├── TokenMalformedException     → 401
    │   ├── RefreshTokenExpiredException→ 401
    │   ├── InvalidCredentialsException → 401
    │   └── AccountLockedException      → 403
    ├── AccessDeniedException           → 403
    ├── SessionConflictException        → 409
    ├── SessionAbsoluteTimeoutException → 401
    ├── TokenReuseDetectedException     → 401
    ├── ResetTokenInvalidException      → 401
    ├── ValidationException             → 400
    ├── ExternalServiceException        → 502
    └── RateLimitExceededException      → 429
```

### 5.3 ErrorCode Enum

```
SUCCESS,
// Auth – Token
TOKEN_EXPIRED, TOKEN_REVOKED, TOKEN_MALFORMED,
REFRESH_TOKEN_EXPIRED, TOKEN_REUSE_DETECTED,
// Auth – Credentials & Session
INVALID_CREDENTIALS, ACCOUNT_LOCKED, SESSION_CONFLICT,
SESSION_ABSOLUTE_TIMEOUT, SESSION_TERMINATED,
// Auth – Recovery
RESET_TOKEN_INVALID, RESET_TOKEN_EXPIRED,
// Resource
RESOURCE_NOT_FOUND, RESOURCE_ALREADY_EXISTS,
// Business
BUSINESS_RULE_VIOLATION, INSUFFICIENT_STOCK, BOM_CIRCULAR_REFERENCE, MRP_CALCULATION_ERROR,
// System
VALIDATION_FAILED, RATE_LIMIT_EXCEEDED, EXTERNAL_SERVICE_ERROR,
INTERNAL_SERVER_ERROR, ACCESS_DENIED
```

### 5.4 Global Exception Handler (`@RestControllerAdvice`)

Xử lý theo thứ tự ưu tiên (thêm `@Order(HIGHEST_PRECEDENCE)`):

| Exception | HTTP | ErrorCode |
|---|---|---|
| `BaseBusinessException` | theo `httpStatus` | theo `errorCode` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| `ConstraintViolationException` | 400 | `VALIDATION_FAILED` |
| `AccessDeniedException` (Spring) | 403 | `ACCESS_DENIED` |
| `DataIntegrityViolationException` | 409 | `RESOURCE_ALREADY_EXISTS` |
| `Exception` (catch-all) | 500 | `INTERNAL_SERVER_ERROR` |

> **Catch-all không bao giờ expose stack trace ra client.** Log đầy đủ phía server.

### 5.5 Auth Entry Points (Spring Security Layer)

Khi Spring Security chặn request (chưa vào controller):
- **401** (`AuthenticationEntryPoint`): Trả `ApiResponse.error(resolveAuthErrorCode, message)` — phân biệt `TOKEN_EXPIRED`, `TOKEN_REVOKED`, `TOKEN_MALFORMED`
- **403** (`AccessDeniedHandler`): Trả `ApiResponse.error(ACCESS_DENIED, "Insufficient permissions")`

### 5.6 Response Headers Chuẩn

| Header | Mô tả |
|---|---|
| `X-Trace-Id` | UUID 16 ký tự – correlate logs với request |
| `X-RateLimit-Limit` | Giới hạn request trong window |
| `X-RateLimit-Remaining` | Số request còn lại |
| `X-RateLimit-Rule` | Rule đang áp dụng |
| `Retry-After` | Giây cần chờ (chỉ khi 429) |
| `X-Session-Warning` | Chỉ có khi kick session cũ |

### 5.7 Request Tracing

Mỗi request được gán `traceId` (UUID 16 ký tự):
- Ưu tiên lấy từ header `X-Trace-Id` nếu có (distributed tracing từ upstream)
- Nếu không → tự sinh mới
- Lưu vào MDC → xuất hiện trong mọi dòng log → dễ trace khi debug
- Trả lại client qua header `X-Trace-Id`

**Logback pattern**: `%d{ISO8601} [%X{traceId}] [%X{userId}] [%X{clientIp}] %-5level %logger{36} - %msg%n`

### 5.8 Controller Patterns

| Operation | HTTP | Status trả về | ApiResponse |
|---|---|---|---|
| GET single | `GET /{id}` | 200 | `ok(data)` |
| GET list phân trang | `GET /` | 200 | `ok(PageResult.from(page))` |
| POST create | `POST /` | 201 | `created(data)` |
| PATCH partial update | `PATCH /{id}` | 200 | `ok(data)` |
| PUT full replace | `PUT /{id}` | 200 | `ok(data)` |
| DELETE | `DELETE /{id}` | 200 | `noContent("Deleted")` |

---

## 6. DEVELOPMENT WORKFLOW

### 6.1 Git Branch Strategy
```
main          → production-ready
develop       → integration branch
feature/*     → tính năng mới (merge vào develop qua PR)
hotfix/*      → fix khẩn cấp (merge vào main + develop)
```

### 6.2 Commit Convention (Conventional Commits)
```
feat(auth): implement JWT refresh token rotation
fix(inventory): correct WIP balance calculation
chore(db): add V3__idx_workorder_status migration
```

### 6.3 Environment Profiles
```yaml
spring.profiles.active: ${APP_ENV:dev}

# application-dev.yml   → dev local, bảo mật lỏng, PG local
# application-test.yml  → Testcontainers, Redis in-memory
# application-prod.yml  → cấu hình strict, secrets qua env variables
```

### 6.4 Database Migration Naming
```
V1__create_users_roles.sql
V2__create_inventory_tables.sql
V3__create_bom_workorder.sql
V4__add_idx_performance.sql
V5__create_audit_logs.sql
```

---

## 7. KEY ARCHITECTURAL DECISIONS

| Quyết Định | Lựa Chọn | Lý Do |
|---|---|---|
| Token strategy | Stateless JWT + Redis blacklist | Scalable; logout vẫn được vô hiệu hóa |
| Refresh token storage | Redis (không phải DB) | Latency thấp; tự hết hạn qua TTL |
| Token rotation khi refresh | Có | Ngăn refresh token reuse attacks |
| Single active session | IP-bound via Redis, atomic Lua | Ngăn đăng nhập đồng thời; kick session cũ |
| Rate limiting | Redis sliding window per-IP + per-user | Không phụ thuộc ngoài; overhead dưới 1ms |
| Response contract | `{code, result, message}` thống nhất | Frontend 1 contract duy nhất; OpenAPI dễ spec |
| Brute-force counter | Redis Lua script (atomic) | Tránh race condition khi concurrent attack |
| Session | Hoàn toàn stateless (STATELESS policy) | Horizontal scalability, microservice-ready |
| ORM | JPA + Hibernate (không dùng native SQL mặc định) | Portability + type safety; raw SQL chỉ cho reports |
| Soft delete | `deleted_at TIMESTAMPTZ NULL` + `@Where` filter | Audit trail + khôi phục dữ liệu |
| Optimistic locking | `@Version` trên mọi entity có thể bị cập nhật đồng thời | Ngăn xung đột cập nhật concurrent |
| Audit log | Async Spring Events + `@TransactionalEventListener` | Không block response; không ghi khi transaction rollback |

---

## 8. BEST PRACTICES CHECKLIST

### 8.1 Security

| # | Practice | Trạng thái | Mô tả |
|---|---|---|---|
| S1 | **HTTPS only** | ✅ Design | Disable plain HTTP trong production |
| S2 | **Secret rotation** | ✅ Current | JWT secret qua env variable (min 256-bit); không hardcode |
| S3 | **BCrypt cost factor 12** | ✅ Current | Cân bằng security và latency |
| S4 | **Secure headers đầy đủ** | ✅ Design | CSP, `X-Frame-Options`, HSTS, `Referrer-Policy`, `Permissions-Policy` (xem 4.17) |
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

### 8.2 API Design

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

### 8.3 Database

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

### 8.4 Code Quality

| # | Practice | Mô tả |
|---|---|---|
| C1 | **Constructor injection** | Không dùng `@Autowired` field injection. Dùng `@RequiredArgsConstructor` |
| C2 | **Immutable DTOs** | Dùng Java `record` cho DTO/response khi không cần kế thừa |
| C3 | **No magic numbers** | Mọi constant → `final` field hoặc `@ConfigurationProperties` |
| C4 | **Fail fast** | Validate ở đầu method; throw exception ngay khi invalid |
| C5 | **@Transactional ở service** | Không đặt ở controller hay repository |
| C6 | **Exception wrapping** | Không để checked exception leak lên controller |
| C7 | **Logging discipline** | ERROR=cần wake-up, WARN=cần chú ý, INFO=business event, DEBUG=dev-only |
| C8 | **Avoid Optional.get()** | Luôn dùng `orElseThrow()` với message rõ ràng |

### 8.5 Performance

| # | Practice | Mô tả |
|---|---|---|
| P1 | **Redis caching** | Cache kết quả đắt (BOM tree, MRP plan). Key: `cache:{entity}:{id}` |
| P2 | **Async processing** | Tác vụ nặng (MRP, report) → `@Async` + `CompletableFuture` |
| P3 | **Lazy loading** | `FetchType.LAZY` mặc định; eager chỉ khi cần |
| P4 | **Bulk operations** | `saveAll()` / batch insert thay vì loop `save()` |
| P5 | **Actuator + Micrometer** | Monitor: GC, thread pool, DS pool, custom business metrics |
| P6 | **Response compression** | `server.compression.enabled=true` (GZIP) cho response > 1KB |

### 8.6 Testing

| # | Practice | Mô tả |
|---|---|---|
| T1 | **Test pyramid** | Unit > Integration > E2E. Hạn chế `@SpringBootTest` (chậm) |
| T2 | **Testcontainers** | Test với PostgreSQL/Redis thật trong integration test |
| T3 | **MockMvc** | Test full request/response cycle kể cả filter chain |
| T4 | **Test data isolation** | Mỗi test tự tạo/xóa data. Dùng `@Transactional` rollback |
| T5 | **Assert response structure** | Kiểm tra `code` trong mọi test – không chỉ HTTP status |
| T6 | **Security test** | Test `@WithMockUser`, test 401/403 scenarios |

### 8.7 Observability

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

---

## 9. AUDIT LOG ARCHITECTURE

> **Mục tiêu**: Ghi mọi hoạt động quan trọng của người dùng không block luồng chính, decoupled với business logic, dễ query. Lưu đủ thông tin để trả lời được: **ai làm gì, lúc nào, thay đổi gì cụ thể**.

### 9.1 Tổng Quan Thiết Kế

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

### 9.2 Database Schema

> **Current (implemented)**: chỉ có bảng `audit_logs`.
> **Planned Phase 2**: thêm bảng `audit_log_changes` cho chi tiết field-level changes.
> Migration hiện tại: `V5__create_audit_logs.sql` chỉ tạo `audit_logs`.

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

#### [🔜 Phase 2] Bảng `audit_log_changes` (Chi tiết field – chưa implement)

> **Chưa có migration** cho bảng này. Sẽ thêm trong Phase 2 khi implement AOP `@Auditable`.

```sql
-- [TODO Phase 2] V6__create_audit_log_changes.sql
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

### 9.3 BaseEntity – JPA Auditing

Mọi entity kế thừa `BaseEntity` để nhận:
- `createdAt`, `updatedAt` → tự động qua `@CreatedDate`, `@LastModifiedDate`
- `createdBy`, `updatedBy` (UUID) → tự động qua `@CreatedBy`, `@LastModifiedBy` từ `SecurityAuditorAware`
- `version` (Long) → Optimistic locking qua `@Version`

### 9.4 RequestContext – Truyền Context qua Async

`RequestContext` là record bất biến chứa snapshot thông tin request tại thời điểm gọi:
- `userId`, `username`, `clientIp`, `userAgent`, `traceId`
- Được tạo từ `SecurityContextHolder` + MDC **trên request thread**
- Truyền explicitly vào `AuditLogEvent` → không bị mất khi qua async thread

### 9.5 AuditLogEvent & AuditLogListener

**Current behavior (implemented)**:
- **`AuditLogEvent`**: record chứa `RequestContext`, `action`, `entityType`, `entityId`, `description`, `status`
- **`AuditLogListener`**: lắng nghe event, chạy trên `auditExecutor`
  1. INSERT vào `audit_logs` → lấy `audit_id`
- Dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` → chỉ ghi sau transaction chính commit

**Phase 2 planned behavior**:
- Thêm **`FieldChange`** record chứa `fieldName`, `oldValue`, `newValue`, `valueType`
- Mở rộng `AuditLogEvent` để chứa `List<FieldChange>`
- Sau khi INSERT `audit_logs`, batch INSERT field changes vào `audit_log_changes`

### 9.6 [TODO Phase 2] So Sánh Change Detection

**Cách lấy `fieldChanges` trong `@Auditable` AOP**:

| Cách | Ưu điểm | Nhược điểm |
|---|---|---|
| So sánh entity trước/sau trong service thủ công | Kiểm soát hoàn toàn | Phải code thêm ở mọi service |
| Hibernate `@EntityListeners` PreUpdate + PostUpdate | Tự động, không cần sửa service | Khó lấy context (userId, traceId) |
| **AOP bắt argument request + response, so sánh** | Cân bằng giữa tự động và kiểm soát | SpEL phức tạp hơn |

> **Phase 2 direction**: Service method `update` nhận `OldSnapshot` và `NewSnapshot` rồi truyền vào `AuditLogEvent`. `@Auditable` AOP sẽ collect field changes thông qua SpEL expression.

### 9.7 AuditAction Enum (Tập Trung)

```
// Auth
LOGIN, LOGOUT, LOGOUT_ALL, TOKEN_REFRESHED,
SESSION_KICKED, LOGIN_FAILED, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED,
SUSPICIOUS_TOKEN_REUSE, PASSWORD_RESET, PASSWORD_CHANGED,

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

### 9.8 @Auditable AOP – Tự Động Audit CRUD

Gắn annotation `@Auditable` lên service method → tự động publish audit event.

- `action`: `AuditAction` enum value
- `entityType`: tên entity (e.g. `"WorkOrder"`)
- `entityIdExpression`: SpEL lấy entityId từ return value hoặc param

> **Current limitation**: `@Auditable` hiện chỉ ghi entity-level event vào `audit_logs`; chưa có `captureChanges` và chưa collect `FieldChange`. Field-level audit thuộc Phase 2.

> **Lưu ý**: Auth events không dùng AOP (SecurityContext chưa set lúc login). Auth events gọi `auditLogService.logAuth()` trực tiếp.

### 9.9 MDC Propagation sang Async Thread

`@Async` tạo thread mới → MDC bị mất theo mặc định.

**Giải pháp**: `MdcTaskDecorator` copy MDC map từ request thread sang async thread.

Cấu hình `AsyncConfig`:
- `auditExecutor`: corePool=2, maxPool=5, queue=500, prefix=`audit-`, decorator=`MdcTaskDecorator`
- `RejectedExecutionHandler`: `CallerRunsPolicy`

### 9.10 Filter Execution Order

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

### 9.11 Tóm Tắt Toàn Bộ Cải Tiến

| Thay đổi | Trạng thái | Lý do |
|---|---|---|
| Bảng `audit_logs` | ✅ Current | Ghi mọi action (LOGIN, LOGOUT, entity events) |
| `audit_log_changes` (chi tiết field) | 🔜 Phase 2 | Tách khái quát và chi tiết; dùng khi mở rộng `@Auditable` để collect `FieldChange` |
| `JwtAuthFilter` set `authenticatedUserId` vào MDC + attr | ✅ Current | `RequestContext.capture()` cần userId |
| `TraceIdFilter` set `clientIp` vào attr | ✅ Current | Không cần inject `HttpServletRequest` khắp nơi |
| `BaseEntity` có `updatedBy` | ✅ Current | JPA Auditing tự điền người sửa cuối |
| `MdcTaskDecorator` trong AsyncConfig | ✅ Current | TraceId + userId không mất khi log async |
| `AuditAction` enum tập trung | ✅ Current | Tên event nhất quán, dễ query/filter |
| `@TransactionalEventListener(AFTER_COMMIT)` | ✅ Current | Không ghi audit khi transaction rollback |
| Tách `UserRateLimitFilter` (Order 4) | ✅ Current | USER-scope rate limit chạy sau JWT auth |
| RTR – key `auth:refresh:used:{tokenId}` | 🔜 Phase 2 | Phát hiện token bị đánh cắp và reuse |
| `auth:reset:{token}` trong Redis | 🔜 Phase 2 | Forgot password flow single-use, TTL 15m |
| Absolute session timeout 30 ngày | 🔜 Phase 2 | Ngăn session sống mãi dù user vẫn active |

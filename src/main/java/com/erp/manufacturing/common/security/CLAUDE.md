# common/security — Auth & Session Design

> Tách từ `CLAUDE.md` §4 (2026-07-25). Chỉ nạp khi agent làm việc trong `common/security/**`
> (JWT, filter chain, rate limiting, IP extraction). Phần trách nhiệm riêng của `AuthService`/`AuthController`
> nằm ở `module/auth/CLAUDE.md`.

> **Chuẩn**: Triển khai cấp senior với xử lý session hết hạn, token rotation, blacklist Redis.

## 4.1 Tổng Quan Kiến Trúc

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

## 4.2 Chiến Lược Token

| Token | Lưu trữ | TTL | Mục đích |
|---|---|---|---|
| Access Token (JWT) | Client (Memory/Header) | 15 phút | Xác thực API stateless |
| Refresh Token (opaque UUID) | Redis (server-side) | 7 ngày | Cấp access token mới |
| Blacklist | Redis SET | Đến hết hạn access token | Vô hiệu hóa token đã logout |

**Tại sao không lưu Access Token trong Redis?** → Giữ xác thực access token stateless (không cần Redis round-trip mỗi request). Chỉ blacklist khi logout hoặc bắt buộc vô hiệu hóa.

## 4.3 Schema Database

```sql
-- V1__create_users_roles.sql
users (user_id UUID PK, username, email, password BCrypt,
       status ACTIVE|INACTIVE|LOCKED,
       created_at, updated_at, created_by, updated_by, version BIGINT)

roles (role_id UUID PK, name UNIQUE, description)

user_roles (user_id FK, role_id FK, PRIMARY KEY composite)

INDEX: idx_users_username, idx_users_email
```

## 4.4 Redis Key Design

```
# Refresh Token store (value = opaque UUID refresh token)
auth:refresh:{userId}:{tokenId}                →  {refreshToken}  TTL=7d

# Reverse lookup tokenId → userId (P0 auth fix, 2026-08-06) — written by the same
# saveRefreshToken() call as the key above. Lets AuthService.refresh() identify the caller from
# tokenId ALONE, without needing to parse an access token out of the Authorization header — see §4.20.
# Not explicitly deleted on logout/rotation; self-expires via the same TTL, same as :used/:rotated.
auth:refresh:owner:{tokenId}                   →  {userId}        TTL=7d

# Access Token Blacklist (sau logout/bắt buộc vô hiệu hóa)
auth:blacklist:{jti}                           →  "1"  TTL=<thời gian còn lại của access token>

# Bộ đếm đăng nhập thất bại (brute-force protection)
auth:failcount:{username}                      →  {count}  TTL=15m

# Multi-device session (mỗi device có entry riêng)
auth:session:device:{userId}:{deviceId}        →  {clientIp}  TTL=7d (sliding)

# RTR – token đã dùng (phát hiện reuse)  ✅ D8a
# Key theo tokenId TOÀN CỤC, không có segment {userId} — cố ý: nó phải nằm NGOÀI pattern
# SCAN "auth:refresh:{userId}:*" của deleteAllUserTokens, nếu không force-logout sẽ xoá
# đúng cái marker chứng minh có reuse.
auth:refresh:used:{tokenId}                    →  "1"  TTL=60s

# Concurrent refresh race — advisory lock + rotation-result breadcrumb  ✅ (đóng nợ #6 phần race)
# Xem §4.12a. Lock: SET NX PX, không unlock tường minh (TTL tự dọn). Breadcrumb: winner ghi ngay
# sau khi rotate xong, để một request trùng lặp đang race trên CÙNG tokenId đọc lại token mới thay
# vì bị chẩn đoán nhầm thành TOKEN_REUSE_DETECTED.
auth:refresh:lock:{tokenId}                    →  "1"  TTL=2s
auth:refresh:rotated:{tokenId}                 →  {newTokenId}  TTL=5s

# Absolute session timeout – mốc bắt đầu phiên (epochMilli)  ✅ D8b
# NGƯỢC với key ngay trên: cố ý DÙNG CHUNG prefix "auth:refresh:{userId}:" để nó NẰM TRONG
# pattern SCAN của deleteAllUserTokens ⇒ force-logout dọn luôn, không cần sửa method đó.
# Ghi ở login, carry-forward nguyên giá trị qua mỗi lần rotate (B81).
auth:refresh:{userId}:{tokenId}:meta           →  {sessionCreatedAt}  TTL=7d

# Forgot password token (single-use) — đã có từ D8c (PasswordResetTokenService)
auth:reset:{token}                             →  {userId}  TTL=15m
auth:reset:user:{userId}                       →  {token}   TTL=15m  (index ngược: token mới thay token cũ)
```

## 4.5 Cấu Trúc JWT Claims

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

## 4.6 Các Class Chính & Trách Nhiệm (phần máy JWT/Redis)

> `AuthService` và `PasswordResetTokenService` (điều phối nghiệp vụ login/refresh/logout) nằm ở
> `module/auth/CLAUDE.md` — file này chỉ mô tả hạ tầng JWT/Redis mà `AuthService` gọi xuống.

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

## 4.7 Luồng Xử Lý Session Hết Hạn

```
Kịch bản: Access token hết hạn (401)
─────────────────────────────────────
Client nhận 401 Unauthorized
  │
  ├─ Client gửi POST /api/v1/auth/refresh { refreshToken, tokenId } — KHÔNG cần Authorization header
  │
  ├─ Server resolve userId từ tokenId (✅ P0, xem §4.20) — auth:refresh:owner:{tokenId}, không đọc JWT
  │
  ├─ Server validate refresh token (rotation + RTR – current):
  │   ├─ Tồn tại trong Redis nhưng giá trị SAI → REFRESH_TOKEN_EXPIRED (không phải RTR, xem 4.12)
  │   ├─ Tồn tại và khớp → tiếp tục
  │   └─ KHÔNG TÌM THẤY → RTR reuse detection (✅ D8a, xem 4.12):
  │         → kiểm tra auth:refresh:used:{tokenId}
  │           ├─ Key "used" tồn tại → TOKEN_REUSE_DETECTED (force logout all)
  │           └─ Key hết hạn → REFRESH_TOKEN_EXPIRED (bình thường)
  │
  ├─ Absolute timeout check (✅ D8b, xem 4.15) — SAU validate, TRƯỚC rotate (B81):
  │   ├─ Không có auth:refresh:{userId}:{tokenId}:meta → phiên trước D8b, coi như bắt đầu BÂY GIỜ
  │   └─ sessionAge ≥ 30 ngày → force logout all → 401 SESSION_ABSOLUTE_TIMEOUT
  │
  └─ Nếu hợp lệ → Tạo accessToken MỚI + refreshToken MỚI (xoay vòng)
       ├─ 1. Lưu refresh token MỚI vào Redis
       ├─ 2. Carry-forward sessionCreatedAt CŨ sang tokenId MỚI  (✅ D8b — KHÔNG stamp now)
       ├─ 3. Đánh dấu token CŨ là USED: SET auth:refresh:used:{oldTokenId} "1" TTL=60s  (✅ D8a)
       ├─ 4. XOÁ token CŨ (cùng key :meta của nó)  ← thứ tự 1→3→4 là bắt buộc, xem bất biến B80
       └─ Trả về { accessToken, refreshToken, expiresIn }

Kịch bản: Force logout / hoạt động đáng ngờ
─────────────────────────────────────────────
Admin gọi deleteAllUserTokens(userId)
  → Toàn bộ refresh token bị xóa khỏi Redis
  → Lần refresh tiếp theo → 401 SESSION_TERMINATED
  → Client redirect về login
```

## 4.8 Bảo Vệ Brute-Force

**Cơ chế**: Redis counter theo username với TTL trượt.
- Sau **5 lần thất bại liên tiếp** → trả về `AccountLockedException` (khoá 15 phút)
- Đăng nhập thành công → reset counter

> 🔴 **Sửa (concurrent refresh-token race phase, đọc lại code thật):** mục này từng ghi "Dùng Lua
> script Redis để đảm bảo increment + expire atomic" — **không khớp** `TokenStoreService.
> incrementFailCount`: đó là `opsForValue().increment(key)` (tự atomic, lệnh Redis đơn) rồi một
> `expire()` **riêng, không atomic**, chỉ gọi khi `count == 1`. Grep toàn repo `RedisScript`/
> `DefaultRedisScript` ra **0 kết quả** — chưa từng có Lua script nào trong codebase này. Đây là nợ
> nhỏ có sẵn (cửa sổ hẹp: crash đúng giữa `increment` và `expire` để lại key không TTL), **không** sửa
> ở đây (ngoài phạm vi, chưa ai yêu cầu) — chỉ sửa mô tả cho khớp thực tế. Khoá đồng thời mới ở
> §4.12a dùng `SET NX PX` (`setIfAbsent` với TTL, cũng một lệnh atomic), **không** phải Lua.

## 4.9 Security Config

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

> 🔴 **[P0, 2026-08-06] `permitAll` ở `SecurityConfig` là điều kiện cần nhưng không đủ.**
> `JwtAuthenticationFilter` chạy *trước* tầng authorization, và trước fix này, nó cố validate bất kỳ
> giá trị Bearer nào tìm thấy trên **mọi** path — kể cả path `permitAll` — rồi chặn cứng bằng 401 nếu
> thất bại, bỏ qua hoàn toàn quyết định của `authorizeHttpRequests`. Một header `Authorization`
> rác/rỗng/cũ (vd HTTP interceptor luôn gắn bất cứ gì đang có trong storage, kể cả rỗng) vì vậy có
> thể chặn một endpoint `permitAll`. `login`/`refresh`/`forgot-password`/`reset-password` nay nằm
> trong tập `BYPASS_PATHS` tường minh, filter xử lý y hệt "không có token" bất kể header chứa gì —
> xem §4.20. `logout`/`logout-all` **cố ý không** nằm trong tập đó (§4.20 giải thích lý do).

> ⚠️ **[2026-08-01] Ngoại lệ:** `/api/v1/auth/me` **không** permit-all — matcher cụ thể hơn đứng
> **trước** wildcard `/api/v1/auth/**` (`authorizeHttpRequests` khớp theo thứ tự khai báo, match đầu
> tiên thắng): `.requestMatchers("/api/v1/auth/me").authenticated()` rồi mới tới
> `.requestMatchers("/api/v1/auth/**").permitAll()`. 4 endpoint còn lại (`login`/`refresh`/`logout`/
> `logout-all`) vẫn permit-all. Regression guard: `AuthMeSecurityTest` (filter chain thật, không mock).

## 4.10 Device Session — hạ tầng multi-device, chính sách **single-session**

> 🔴 **[`D8b`, 2026-08-03] Sửa mô tả sai đã tồn tại từ lâu.** Mục này (và
> `.claude/rules/architecture-decisions.md`) từng ghi *"User có thể đăng nhập từ nhiều thiết bị cùng
> lúc"*. **Code không làm thế**: `AuthService.login` gọi `deleteAllUserTokens` +
> `deleteAllDeviceSessions` **mỗi lần login** ⇒ đăng nhập máy mới **thu hồi** mọi phiên cũ.
>
> Cả hai thứ đều đúng, chỉ là hai tầng khác nhau — đừng nhầm:
>
> | | Hạ tầng Redis | Chính sách `login()` |
> |---|---|---|
> | Khả năng | key **per-device** (`auth:session:device:{userId}:{deviceId}`), TTL độc lập ⇒ **chịu được** nhiều phiên song song | **single-session**: mỗi login xoá sạch phiên cũ |
> | Đổi chính sách | không cần đổi | bỏ 2 dòng `deleteAll*` ở `AuthService.java` |
>
> ⇒ Muốn thật sự cho multi-device thì đó là **quyết định bảo mật** (bỏ 2 dòng đó), không phải việc
> dọn tài liệu. `D8b` **cố ý không** đụng hành vi — chỉ sửa mô tả cho khớp code.
> Javadoc `AuthService` vốn đã ghi đúng ("Enforced single-session per user") từ trước.

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

## 4.11 Rate Limiting – Config-driven & Extensible

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

      - id: planning-run-user     # Tác vụ nặng – giới hạn chặt hơn
        scope: USER
        pattern: "/api/v1/planning-runs/**"
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

## 4.12 Refresh Token Reuse Detection (RTR) [✅ `D8a`, 2026-08-03]

Refresh token đã bị xoay mà quay lại ⇒ `401 TOKEN_REUSE_DETECTED` và thu hồi **mọi** refresh token lẫn
device session của user. Chỉ áp dụng nhánh `stored == null`; thứ tự xoay bắt buộc: lưu mới → đánh dấu đã
dùng (`auth:refresh:used:{tokenId}`, cố ý nằm **ngoài** pattern quét force-logout) → xoá cũ. Bất biến `B80`.

> Bản đầy đủ (nguyên văn, kèm lịch sử và lý do thiết kế): `docs/security-design-notes.md`.

## 4.12a Concurrent Refresh-Token Race — ĐÃ ĐÓNG (mở rộng `D8`, sau `D8b`)

Hai request refresh trùng nhau trên cùng `tokenId`: khoá tư vấn `SET NX PX` (2 s) + breadcrumb
`auth:refresh:rotated:{tokenId}` (5 s) để request đến sau nhận lại đúng cặp token vừa sinh, không bị chẩn
đoán nhầm thành reuse. Không có grace window: token cũ vẫn chết ngay. Bất biến `B95`.

> Bản đầy đủ (nguyên văn, kèm lịch sử và lý do thiết kế): `docs/security-design-notes.md`.

## 4.13 Account Enumeration Prevention

> **Mục tiêu**: Kẻ tấn công không thể dò được username hợp lệ bằng cách quan sát sự khác biệt trong response.

**Quy tắc bắt buộc**:
- `POST /auth/login`: Dù username không tồn tại hay sai mật khẩu → luôn trả cùng HTTP 401 + cùng message: `"Invalid credentials"`
- Không được trả `"User not found"` hay `"Wrong password"` — phân biệt hai lỗi này = lộ thông tin
- `POST /auth/forgot-password`: Dù email tồn tại hay không → luôn trả HTTP 200 + `"If this email exists, a reset link has been sent"`
- Khi username không tồn tại trong login → vẫn phải thực hiện `BCrypt.matches()` với dummy hash để **thời gian phản hồi tương đương** (tránh timing attack)

---

## 4.14 Account Recovery Flow [✅ `D8c`, 2026-08-06]

`forgot-password` trả cùng một response dù email có tồn tại hay không, và nhánh không tồn tại không chạm
collaborator nào. Token đặt lại một lần, TTL 15 phút, có index ngược `auth:reset:user:{userId}` để token mới
thay token cũ; đặt lại xong thì thu hồi mọi phiên. Bất biến `B101`. Luồng mã 6 số qua email (`AP-1`) nằm ở
`module/auth/CLAUDE.md` (`B275`–`B282`).

> Bản đầy đủ (nguyên văn, kèm lịch sử và lý do thiết kế): `docs/security-design-notes.md`.

## 4.15 Absolute Session Timeout [✅ `D8b`, 2026-08-03]

Phiên sống quá 30 ngày tính từ **login** ⇒ `401 SESSION_ABSOLUTE_TIMEOUT` + force logout. Mốc bắt đầu lưu ở
companion key `auth:refresh:{userId}:{tokenId}:meta` (cố ý **nằm trong** pattern quét force-logout), được
chuyển sang `tokenId` mới mỗi lần xoay, không đóng dấu lại `now`. Phiên cũ không có mốc ⇒ fail-open.
Bất biến `B81`.

> Bản đầy đủ (nguyên văn, kèm lịch sử và lý do thiết kế): `docs/security-design-notes.md`.

## 4.16 JWT Signing Algorithm

| | HS256 (HMAC-SHA256) | RS256 (RSA-SHA256) |
|---|---|---|
| Khóa | Symmetric (1 secret key) | Asymmetric (private ký, public verify) |
| Microservice | ❌ Phải share secret | ✅ Chỉ distribute public key |
| Performance | ✅ Nhanh hơn | Chậm hơn (RSA operation) |
| Phù hợp | Monolith / single service | Microservices |

> **Quyết định cho Capstone**: Dùng **HS256** (monolith). Secret key lưu trong env variable `JWT_SECRET` (min 256-bit). Document rõ: nếu scale lên microservice → phải migrate sang RS256 với JWK endpoint.

---

## 4.17 Security Headers Đầy Đủ [◻ THIẾT KẾ — chưa cấu hình]

**Chưa cấu hình.** `SecurityConfig` không có khối `headers(...)`; hiện chỉ có header mặc định của Spring
Security. Bảng giá trị khuyến nghị (CSP, `Referrer-Policy`, `Permissions-Policy`, HSTS) nằm trong file thiết kế.

> Bản đầy đủ (nguyên văn, kèm lịch sử và lý do thiết kế): `docs/security-design-notes.md`.

## 4.18 Log Sanitization

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

## 4.19 Device Fingerprinting cho Refresh Token [◻ THIẾT KẾ — chưa triển khai]

**Chưa triển khai.** Thiết kế ràng buộc refresh token với dấu vân tay thiết bị; `deviceId` hiện chỉ dùng để
đặt tên device session.

> Bản đầy đủ (nguyên văn, kèm lịch sử và lý do thiết kế): `docs/security-design-notes.md`.

## 4.20 P0 — `/auth/refresh` Decoupled From The Access Token (2026-08-06)

`/auth/v1/refresh` không còn phụ thuộc access token: `userId` lấy qua `auth:refresh:owner:{tokenId}`, endpoint
nằm trong `JwtAuthenticationFilter.BYPASS_PATHS` nên header `Authorization` thiếu hay hỏng không chặn được
refresh. Bất biến `B107`.

> Bản đầy đủ (nguyên văn, kèm lịch sử và lý do thiết kế): `docs/security-design-notes.md`.

## Bất biến nghiệp vụ (tách từ `CLAUDE.md` §10.6)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B35 | Filter order: IP rate-limit **trước** JWT, USER rate-limit **sau** JWT. Request chưa auth → **401 chứ không phải 429** | `SecurityFilterChainTest` |
| B36 | `X-Forwarded-For` chỉ được tin khi remote addr là **trusted proxy**; ngược lại dùng `remoteAddr` | `IpExtractorTest.spoofedXff_onDirectRequest_isIgnored` |
| B37 | **[P0, 2026-08-06]** `JwtAuthenticationFilter.BYPASS_PATHS` (`login`/`refresh`/`forgot-password`/`reset-password`) không bao giờ được validate token, bất kể header chứa gì — không header, header rỗng, header rác đều xử lý y hệt "không có token". `logout`/`logout-all` **không** nằm trong tập này (§4.20) | `JwtAuthenticationFilterTest` (bypass paths ignore garbage/empty tokens; logout paths still validate) |

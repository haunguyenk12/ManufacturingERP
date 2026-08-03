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

# Absolute session timeout – mốc bắt đầu phiên (epochMilli)  ✅ D8b
# NGƯỢC với key ngay trên: cố ý DÙNG CHUNG prefix "auth:refresh:{userId}:" để nó NẰM TRONG
# pattern SCAN của deleteAllUserTokens ⇒ force-logout dọn luôn, không cần sửa method đó.
# Ghi ở login, carry-forward nguyên giá trị qua mỗi lần rotate (B81).
auth:refresh:{userId}:{tokenId}:meta           →  {sessionCreatedAt}  TTL=7d

# ── Planned (chưa implement) ────────────────────────────────────
# Forgot password token (single-use)
auth:reset:{token}                             →  {userId}  TTL=15m  [TODO D8c]
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
  ├─ Client gửi POST /api/v1/auth/refresh { refreshToken }
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
- **Quan trọng**: Dùng **Lua script Redis** để đảm bảo `increment + expire` atomic (tránh race condition khi nhiều request đồng thời)

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

> **Trạng thái**: đã implement trong `AuthService.refresh` + `TokenStoreService.markRefreshTokenUsed`
> / `wasRefreshTokenUsed`. Bất biến **`B80`** ở `module/auth/CLAUDE.md` — đọc nó trước khi sửa
> `refresh()`, đặc biệt phần thứ tự thao tác Redis và ranh giới "chỉ nhánh `stored == null`".

**Mục tiêu**: Phát hiện kịp thời khi refresh token bị đánh cắp và dùng lại.

**Vấn đề với rotation đơn thuần**: Sau khi rotate, nếu token cũ bị dùng lại, server chỉ trả `REFRESH_TOKEN_EXPIRED` — không phân biệt được "hết hạn tự nhiên" với "token bị đánh cắp".

**Giải pháp RTR (đã implement)**:
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

> 🔴 **Hai điều `D8a` chốt khác thiết kế gốc ở trên — đừng "sửa cho giống tài liệu":**
> 1. **Force-logout gọi CẢ `deleteAllUserTokens` LẪN `deleteAllDeviceSessions`** (thiết kế gốc chỉ
>    ghi cái đầu). Lý do: khớp `logoutAll()`, và device-session cũng là dữ liệu phiên — bỏ lại thì
>    "force logout toàn bộ thiết bị" không đúng nghĩa đen của chính nó.
> 2. **Thứ tự rotate là lưu-mới → mark-used → xoá-cũ**, không phải "mark rồi lưu/xoá" như đoạn
>    pseudo-code trên gợi ý. Marker phải tồn tại **trước khi** key cũ biến mất.
>
> **Giới hạn đã biết, chấp nhận cho `D8a`** (đừng tự mở rộng phạm vi để "sửa"):
> - **Race 2 request refresh đồng thời cùng 1 token hợp lệ** không giải được bằng thứ tự thao tác —
>   cần lock/CAS, thiết kế này không có. Hệ quả: client double-submit (network retry) có thể bị
>   force-logout **oan**. Đây là giới hạn cố hữu của RTR cơ bản không có grace window.
> - RTR **chỉ** áp dụng nhánh `stored == null`. Nhánh `stored != null` nhưng giá trị mismatch giữ
>   `REFRESH_TOKEN_EXPIRED` — tokenId còn sống nghĩa là nó chưa từng bị rotate away.

---

## 4.13 Account Enumeration Prevention

> **Mục tiêu**: Kẻ tấn công không thể dò được username hợp lệ bằng cách quan sát sự khác biệt trong response.

**Quy tắc bắt buộc**:
- `POST /auth/login`: Dù username không tồn tại hay sai mật khẩu → luôn trả cùng HTTP 401 + cùng message: `"Invalid credentials"`
- Không được trả `"User not found"` hay `"Wrong password"` — phân biệt hai lỗi này = lộ thông tin
- `POST /auth/forgot-password`: Dù email tồn tại hay không → luôn trả HTTP 200 + `"If this email exists, a reset link has been sent"`
- Khi username không tồn tại trong login → vẫn phải thực hiện `BCrypt.matches()` với dummy hash để **thời gian phản hồi tương đương** (tránh timing attack)

---

## 4.14 Account Recovery Flow [🔜 Phase 2 – chưa implement]

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

## 4.15 Absolute Session Timeout [✅ `D8b`, 2026-08-03]

> **Trạng thái**: đã implement trong `AuthService.refresh` + `TokenStoreService.saveSessionStart` /
> `getSessionStart`. Bất biến **`B81`** ở `module/auth/CLAUDE.md` — đọc nó trước khi sửa `refresh()`.

> **Vấn đề**: Refresh token TTL sliding 7 ngày — user active liên tục sẽ **không bao giờ bị force logout**. Với ERP có dữ liệu nhạy cảm, đây là rủi ro bảo mật.

**Giải pháp (đã implement)**: lưu `sessionCreatedAt` ở **companion key**, ghi lúc login và
**carry-forward** qua mỗi lần rotate.

```
auth:refresh:{userId}:{tokenId}:meta  →  {sessionCreatedAt epochMilli}  TTL=7d

Khi validate refresh (SAU khi token đã được chứng minh hợp lệ, TRƯỚC khi rotate — B81):
  → Đọc sessionCreatedAt của tokenId hiện tại
  → Không có key  → phiên tạo trước D8b ⇒ coi như bắt đầu BÂY GIỜ (fail-open, xem dưới)
  → sessionAge ≥ app.jwt.absolute-session-timeout-ms (30 ngày) → SESSION_ABSOLUTE_TIMEOUT
       → deleteAllUserTokens + deleteAllDeviceSessions  → audit SESSION_ABSOLUTE_TIMEOUT
       → 401 { code: "SESSION_ABSOLUTE_TIMEOUT", message: "Session expired. Please login again." }
  → Còn hạn → rotate, và mang NGUYÊN sessionCreatedAt cũ sang tokenId mới
```

| Timeout | Loại | Hành vi |
|---|---|---|
| 7 ngày | Sliding (inactive timeout) | Reset mỗi khi refresh token |
| 30 ngày | Absolute (session timeout) | Bắt buộc login lại dù có active |

> 🔴 **Ba điều `D8b` chốt KHÁC thiết kế gốc ở trên — đừng "sửa cho giống tài liệu cũ":**
>
> 1. **Companion key, KHÔNG phải JSON payload.** Bản thiết kế gốc ghi *"lưu `sessionCreatedAt` vào
>    payload refresh token"*, tức đổi value của `auth:refresh:{userId}:{tokenId}` thành JSON. User
>    chốt **không** làm vậy (2026-08-03): phương án đó đụng **mọi** đường đọc/ghi refresh token, kể
>    cả nhánh RTR vừa làm ở `D8a`. Companion key giữ `saveRefreshToken`/`getRefreshToken`/`B80`
>    nguyên vẹn. `userAgentHash` trong payload gốc thuộc §4.19, vẫn chưa làm.
> 2. **Key `:meta` cố ý NẰM TRONG pattern SCAN** `auth:refresh:{userId}:*` — ngược hẳn với key
>    `auth:refresh:used:{tokenId}` của `D8a`. Hai key cạnh nhau, hai yêu cầu trái nhau: cái này
>    *phải* bị force-logout quét, cái kia *không được*. Mỗi cái có một test canh đúng chiều của nó
>    (`sessionStartKey_isSweptByDeleteAllUserTokens` vs `markRefreshTokenUsed_keyIsNotSweptBy…`).
> 3. **Ngưỡng là `≥`, không phải `>`** như pseudo-code gốc — khác biệt chỉ ở đúng mili-giây thứ
>    2_592_000_000, ghi ra để người đọc sau không tưởng là lệch.
>
> **Quyết định fail-open (phiên không có stamp), có chủ đích:** phiên login **trước** khi `D8b`
> deploy không có key `:meta`. Chúng được coi như **bắt đầu từ bây giờ**, không phải "đã hết hạn".
> Fail-closed sẽ **đăng xuất toàn bộ user đang online ngay lúc deploy** mà không tăng bảo mật —
> refresh TTL là 7 ngày nên trong vòng một tuần mọi phiên còn sống đều đã có stamp. Có test riêng
> (`refresh_sessionWithoutStartStamp_isTreatedAsStartingNow`) + mutation #4 canh quyết định này.
>
> **Giới hạn đã biết, chấp nhận:** timeout chỉ được đánh giá **khi refresh**. Access token đang cầm
> (TTL 15 phút) vẫn dùng được tới lúc hết hạn dù phiên vừa vượt mốc 30 ngày — cửa sổ tối đa bằng
> đúng access-token TTL. Đóng nó cần kiểm ở `JwtAuthenticationFilter` (mỗi request một lượt đọc
> Redis), không tương xứng với rủi ro.

---

## 4.16 JWT Signing Algorithm

| | HS256 (HMAC-SHA256) | RS256 (RSA-SHA256) |
|---|---|---|
| Khóa | Symmetric (1 secret key) | Asymmetric (private ký, public verify) |
| Microservice | ❌ Phải share secret | ✅ Chỉ distribute public key |
| Performance | ✅ Nhanh hơn | Chậm hơn (RSA operation) |
| Phù hợp | Monolith / single service | Microservices |

> **Quyết định cho Capstone**: Dùng **HS256** (monolith). Secret key lưu trong env variable `JWT_SECRET` (min 256-bit). Document rõ: nếu scale lên microservice → phải migrate sang RS256 với JWK endpoint.

---

## 4.17 Security Headers Đầy Đủ

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

## 4.19 Device Fingerprinting cho Refresh Token

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

## Bất biến nghiệp vụ (tách từ `CLAUDE.md` §10.6)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B35 | Filter order: IP rate-limit **trước** JWT, USER rate-limit **sau** JWT. Request chưa auth → **401 chứ không phải 429** | `SecurityFilterChainTest` |
| B36 | `X-Forwarded-For` chỉ được tin khi remote addr là **trusted proxy**; ngược lại dùng `remoteAddr` | `IpExtractorTest.spoofedXff_onDirectRequest_isIgnored` |

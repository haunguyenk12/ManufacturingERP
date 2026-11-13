# Ghi chú thiết kế bảo mật — bản đầy đủ

> Chuyển **nguyên văn** từ `src/main/java/com/erp/manufacturing/common/security/CLAUDE.md` ngày 2026-11-13
> (`RA-1`, mục E6) để file đó, vốn được nạp mỗi khi đụng `common/security/**`, chỉ còn tóm tắt.
> Mục 4.17 và 4.19 là **thiết kế chưa triển khai**.

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
> **Giới hạn đã biết ở `D8a`, ✅ ĐÃ ĐÓNG** (concurrent refresh-token race phase, xem §4.12a):
> - ~~Race 2 request refresh đồng thời cùng 1 token hợp lệ không giải được bằng thứ tự thao tác —
>   cần lock/CAS, thiết kế này không có. Hệ quả: client double-submit (network retry) có thể bị
>   force-logout oan.~~ Đóng bằng advisory lock + rotation-result breadcrumb, **không** có grace
>   window (quyết định của user) — token cũ vẫn chết ngay lập tức, chỉ có request trùng lặp được dẫn
>   tới đúng cặp token đã sinh ra thay vì bị chẩn đoán nhầm.
> - RTR **chỉ** áp dụng nhánh `stored == null`. Nhánh `stored != null` nhưng giá trị mismatch giữ
>   `REFRESH_TOKEN_EXPIRED` — tokenId còn sống nghĩa là nó chưa từng bị rotate away. **Không đổi.**

---

## 4.12a Concurrent Refresh-Token Race — ĐÃ ĐÓNG (mở rộng `D8`, sau `D8b`)

> **Trạng thái**: implement trong `AuthService.refresh` + `TokenStoreService.acquireRefreshLock` /
> `saveRotationResult` / `getRotationResult`. Không migration, không permission mới, không đổi
> `AuthResponse`.

**Vấn đề đã đóng** (trace đầy đủ từ code thật): request A rotate xong (lưu mới → mark used → xoá cũ)
nhưng client không nhận được response (mất kết nối, timeout) → client retry với **chính** `(tokenId,
refreshToken)` cũ vì chưa từng thấy cặp mới → request B đọc `stored == null` (A đã xoá) → kiểm
`wasRefreshTokenUsed` → **true** (A vừa mark) → B bị chẩn đoán nhầm thành kẻ trộm dùng lại token đã
đánh cắp → `TOKEN_REUSE_DETECTED` → **toàn bộ phiên hợp lệ của B bị force-logout oan**.

**Quyết định đã chốt với user (AskUserQuestion, không grace window):** token cũ **không bao giờ**
được chấp nhận lại làm credential lần thứ hai. Hai request race trên cùng `tokenId` phải được nhận
diện là **cùng một hành động logic**, không phải hai credential độc lập.

**Giải pháp — hai cơ chế nhỏ, bổ sung cho nhau:**

1. **Khoá tư vấn theo `tokenId`** (`auth:refresh:lock:{tokenId}`, TTL 2s, `SET NX PX` qua
   `opsForValue().setIfAbsent` — atomic bằng **một lệnh Redis**, không cần Lua). Gọi **đầu tiên**,
   trước mọi validate. Khoá được ⇒ đi tiếp ngay (đường nhanh, tuyệt đại đa số request). Khoá **không**
   được (ai đó đang rotate cùng `tokenId` ngay lúc này) ⇒ ngủ một lần ~150ms rồi đi tiếp vào luồng
   validate **y hệt cũ** — không có gì bị bỏ qua, chỉ trì hoãn.
2. **Breadcrumb kết quả rotate** (`auth:refresh:rotated:{oldTokenId}` → `newTokenId`, TTL 5s). Winner
   ghi ngay sau bước `deleteRefreshToken` hiện có (không đổi thứ tự B80). Trong nhánh `stored == null`
   (đúng chỗ RTR sống), **trước khi** kiểm `wasRefreshTokenUsed`: nếu breadcrumb tồn tại và cặp
   `newTokenId` nó trỏ tới vẫn còn (`getRefreshToken` khác `null`) → trả về **đúng cặp đó** (mint access
   token mới — vốn đã làm ở mọi lần refresh, không phải cơ chế mới), **không** rotate thêm lần nào,
   **không** audit `TOKEN_REUSE_DETECTED`. Breadcrumb vắng mặt hoặc cặp nó trỏ tới cũng đã biến mất ⇒
   rơi xuống `wasRefreshTokenUsed` **y hệt trước phase này** — replay thật (đến sau khi breadcrumb 5s
   hết hạn nhưng còn trong cửa sổ RTR 60s) vẫn bị bắt đúng như cũ.

🔴 **Vì sao cần CẢ HAI, không phải một:**
- **Chỉ khoá không đủ** — theo đúng trace ở trên, request A đã **hoàn tất hết** (kể cả xoá) trước khi
  B gọi `getRefreshToken`; B khoá được ngay (không có gì để chờ) và vẫn cần một câu trả lời cho
  `stored == null`. Breadcrumb là thứ trả lời câu đó.
- **Chỉ breadcrumb không đủ** — hai request đọc `stored != null` ở CÙNG một khoảnh khắc (chưa ai kịp
  ghi gì) sẽ **cả hai** tự rotate độc lập, sinh 2 phiên hợp lệ từ 1 hành động (không phải bug đã báo,
  nhưng lãng phí và ghi đè breadcrumb kiểu "ai ghi sau thắng"). Khoá cho request thứ hai một khoảng
  chờ ngắn để **thấy** kết quả của request thứ nhất thay vì tự làm lại.

**Bất biến mới** (xem `module/auth/CLAUDE.md`): `B95`.

**Giới hạn đã biết, chấp nhận:** khoá không có unlock tường minh — dựa hoàn toàn vào TTL 2s để tự
dọn. Trường hợp 3 request đụng độ thật sự cùng lúc, request thứ ba có thể chờ tới hết TTL dù winner
đã xong từ lâu — chấp nhận được vì cực hiếm và cửa sổ tối đa chỉ 2s.

---

## 4.14 Account Recovery Flow [✅ `D8c`, 2026-08-06]

> **Trạng thái**: đã implement trong `AuthService.forgotPassword`/`resetPassword`/`adminUnlockAccount`
> + `PasswordResetTokenService` (module `auth`). Bất biến **`B101`** ở `module/auth/CLAUDE.md`.

**Quyết định hạ tầng chốt với user (2026-08-06):** gửi email bằng **mock/log console** — không có
dependency `spring-boot-starter-mail`, không SMTP/SES/SendGrid nào được tích hợp. `EmailNotification
Service` chỉ log dòng chứa reset link ở mức INFO; không có interface (chỉ một implementation tồn tại,
`coding-rules.md §11.5` cấm abstraction speculative). Nếu sau này quyết định gửi email thật, đó mới là
lúc thêm interface + implementation thứ hai — không làm trước.

#### Forgot Password
```
1. POST /api/v1/auth/forgot-password { email }
   → Tìm user theo email (không báo kết quả)
   → Nếu tồn tại: sinh token UUID, lưu auth:reset:{token} → userId TTL=15m
     (đồng thời auth:reset:user:{userId} → token, TTL=15m — reverse index để request mới
     invalidate token cũ, không cho tích lũy)
   → "Gửi" email (mock/log console — xem quyết định ở trên)
   → Luôn trả: 200 { code: SUCCESS, message: "If this email exists, a reset link has been sent" }
```
🔴 **`forgotPassword` trả `void` có chủ đích** — controller tự soạn message cố định, không branch theo
kết quả service. Nhánh "không tìm thấy email" **không** audit, **không** gọi `PasswordResetTokenService`/
`EmailNotificationService` — hai nhánh phải giống hệt nhau ở mọi collaborator, đó mới là phòng chống
account enumeration thật, không chỉ là "cùng câu chữ response".

```
2. POST /api/v1/auth/reset-password { token, newPassword }
   → Lấy userId từ Redis bằng token
   → Nếu không tìm thấy → 401 RESET_TOKEN_INVALID (hết hạn hoặc đã dùng — Redis TTL không phân biệt
     được hai trường hợp, giống REFRESH_TOKEN_EXPIRED cho refresh token)
   → Validate password policy (@Size(min=8), giống CreateUserRequest)
   → Đặt mật khẩu mới (BCrypt)
   → XÓA token ngay (single-use, cả 2 key thuận/nghịch)
   → Xóa toàn bộ refresh token + device session của user (force logout mọi phiên)
   → Ghi audit: PASSWORD_RESET
```

#### Admin Manual Unlock
```
PATCH /api/v1/admin/users/{userId}/unlock
  → Yêu cầu role ADMIN (@PreAuthorize("hasRole('ADMIN')"); UserService từ 2026-11-12 dùng PERM_ACCESS_MANAGE)
  → Xóa auth:failcount:{username} trong Redis
  → Cập nhật users.status = ACTIVE (User.activate(), có sẵn từ trước)
  → Ghi audit: ACCOUNT_UNLOCKED
```
`AdminController` (`/api/v1/admin/users`) là controller `/admin` đầu tiên trong repo — gọi thẳng
`AuthService.adminUnlockAccount`, không đi qua `UserService` dù nó thao tác lên `User`: ba hành động
account-recovery (forgot/reset/unlock) được nhóm cùng một service theo đúng thiết kế đã viết sẵn ở
`module/auth/CLAUDE.md`, thay vì tách theo "ai sở hữu entity nào".

> **Lưu ý**: Tự động unlock qua TTL Redis vẫn giữ (sau 15 phút). Admin unlock là cơ chế bổ sung.
> `User.lock()`/`UserStatus.LOCKED` tồn tại từ trước nhưng **không có call site nào** — khoá tự động
> hiện tại thuần Redis fail-count, không đụng `users.status`. `adminUnlockAccount` gọi `activate()`
> đúng theo thiết kế đã chốt; nó cũng hữu ích để khôi phục một user bị `UserService.delete()` đưa về
> `INACTIVE`, không chỉ cho trường hợp `LOCKED`.

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

## 4.17 Security Headers Đầy Đủ [◻ THIẾT KẾ — chưa cấu hình]

> Kiểm tra 2026-11-12: `SecurityConfig` không có khối `headers(...)` nào, nên hiện chỉ có header mặc định của
> Spring Security. Bảng dưới là mục tiêu, không phải hiện trạng.

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

## 4.19 Device Fingerprinting cho Refresh Token [◻ THIẾT KẾ — chưa triển khai]

> Kiểm tra 2026-11-12: `AuthService` và `TokenStoreService` không có code fingerprint nào. `deviceId` chỉ dùng
> để đặt tên device session, không ràng buộc refresh token.

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

## 4.20 P0 — `/auth/refresh` Decoupled From The Access Token (2026-08-06)

> **Bối cảnh**: FE báo hai lỗi P0 chặn regression suite: (1) `/auth/refresh` trả `401 TOKEN_MALFORMED`
> khi không gửi access token, dù endpoint là `permitAll`; (2) access token hợp lệ gọi `/auth/me` được
> nhưng cùng token gọi Company/UOM/Work Order/Sales Order lại trả `401 TOKEN_MALFORMED`.

**Lỗi #1 là bug thật, nặng hơn báo cáo cho thấy.** `/auth/refresh` chỉ *permitAll trên danh nghĩa*:
`AuthService.refresh` đọc `authenticatedUserId` từ request attribute — attribute đó **chỉ**
`JwtAuthenticationFilter` set được, và chỉ set được khi parse **thành công** một access token từ
header `Authorization`. Header **thiếu hẳn** → trước đây trả `401 REFRESH_TOKEN_EXPIRED` (sai nhưng
không đúng triệu chứng báo cáo). Header **có nhưng rác/rỗng** (`"Bearer "`, `"Bearer null"` — mẫu hình
phổ biến khi HTTP interceptor luôn gắn bất cứ gì đang có trong storage, kể cả rỗng) → filter ném
`401 TOKEN_MALFORMED` và chặn request **trước khi** chạm tới validate refresh token thật. Dù kiểu nào,
refresh **không** dùng được chỉ với `{refreshToken, tokenId}` như tài liệu ngầm hứa — trong khi đó
chính là con đường phục hồi khi access token không dùng được.

**Lỗi #2 không có đường code nào tái hiện được từ backend.** `JwtTokenProvider.validateAndExtractClaims`
là hàm thuần trên chuỗi token, không tham số path nào. `handleNormalPath` giống hệt cho `/auth/me` và
Company/UOM/WorkOrder/SalesOrder. Chỉ có **một** `SecurityFilterChain`. Đã kiểm `app.cors.allowed-
headers` — `Authorization` **có** trong danh sách, loại trừ khả năng CORS chặn header. Cách duy nhất
tái hiện "cùng token, endpoint này được endpoint kia không" là header **không tới được** backend ở
những cuộc gọi đó — điều này đi qua `JwtAuthEntryPoint`, nơi **trước đây hardcode `TOKEN_MALFORMED`
cho mọi request chưa xác thực**, bất kể là "không gửi gì" hay "gửi token hỏng" — đây chính là lý do
lỗi #2 trông giống lỗi JWT thay vì "cuộc gọi này không gửi header".

**Hai fix (không fix được lỗi #2 tận gốc — đó rất có thể là phía FE, xem bên dưới):**

1. **`JwtAuthenticationFilter.BYPASS_PATHS`** — `login`/`refresh`/`forgot-password`/`reset-password`
   được filter xử lý y hệt "không có token" bất kể header chứa gì. **`logout`/`logout-all` cố ý
   KHÔNG có trong danh sách này** — khác 4 endpoint trên, `AuthService.logout`/`logoutAllDevices` vẫn
   đọc `authenticatedUserId` (và `logout` đọc lại header thô để lấy `jti`) để biết revoke cái gì, và
   **im lặng no-op** khi thiếu (`if (username == null) return;`). Bypass hai endpoint này sẽ biến
   logout thành không-revoke-gì-cả mỗi khi có token hỏng đính kèm — đổi bug hiện tại lấy một lỗ hổng
   bảo mật âm thầm còn tệ hơn.
2. **`TokenStoreService.saveRefreshToken` ghi thêm `auth:refresh:owner:{tokenId}` → `userId`**
   (§4.4) — mọi caller (login, mọi lần rotate) tự động có key này, không cần sửa call site nào khác.
   `AuthService.refresh` giờ resolve `userId` qua `tokenStore.getTokenOwner(request.tokenId())` thay
   vì đọc request attribute — **không** còn phụ thuộc header `Authorization` chút nào.
   `B80`/`B81`/`B95` (RTR, absolute timeout, concurrent-race) **không đổi** — cả ba đều chạy **sau**
   khi `principal` đã resolve xong, không quan tâm nó resolve bằng cách nào.
3. **`JwtAuthEntryPoint`** dùng `AuthErrorCode.AUTHENTICATION_REQUIRED` thay vì `TOKEN_MALFORMED`.
   An toàn và chính xác: đã xác nhận `JwtAuthEntryPoint` **chỉ** có thể bị gọi khi `extractToken()`
   trả `null` (không có header, hoặc không có prefix `Bearer`) — bất kỳ token **được gửi** nào, hỏng
   hay không, đều bị `JwtAuthenticationFilter` bắt và trả lời trực tiếp trước khi tới tầng
   authorization của Spring Security, nên entry point này không bao giờ thấy trường hợp đó. Không cần
   inspect `authException` — chỉ còn đúng một nguyên nhân khi đã vào tới đây.

**`JwtTokenProvider.extractClaimsFromExpired` đã xoá** — orphan sau khi bỏ cơ chế "refresh path cho
phép token hết hạn" (không còn cần thiết: refresh không đọc access token nữa). `extractJtiUnchecked`
(dùng cho logout) **không đổi** — khác hàm, khác mục đích.

**Chưa fix được (đã ghi rõ, không giấu):** nguyên nhân thật của lỗi #2 (vì sao header không tới được
backend ở một số cuộc gọi) — rất có thể là phía client, không phải backend defect. Sau fix #3, response
FE nhận được ở những cuộc gọi lỗi sẽ ghi `AUTHENTICATION_REQUIRED` thay vì `TOKEN_MALFORMED`, giúp lộ rõ
nguyên nhân thật qua Network tab (so sánh header `Authorization` thực tế gửi đi giữa một cuộc gọi
`/auth/me` thành công và một cuộc gọi Company/UOM/WorkOrder/SalesOrder thất bại, cùng token).

---


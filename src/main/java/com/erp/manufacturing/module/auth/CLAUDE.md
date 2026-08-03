# module/auth — AuthService & AuthController

> Tách từ `CLAUDE.md` §4.6 + §10.6 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/auth/**`.
> Phần thiết kế đầy đủ JWT/Redis/rate-limit/RTR/fingerprint nằm ở
> `../../common/security/CLAUDE.md` — đọc file đó nếu cần hiểu cơ chế mà `AuthService` gọi xuống.

## Trách nhiệm chính

#### `AuthService`
- `login` → validate credentials (constant-time) → cấp cặp token (có kiểm tra IP session)
- `refresh` → validate refresh token → RTR check → xoay vòng atomic → trả token mới
- `logout` → blacklist access token + xóa refresh token
- `logoutAllDevices` → xóa toàn bộ refresh token của user
- `forgotPassword(email)` → sinh reset token, gửi email (cùng response dù email tồn tại hay không)
- `resetPassword(token, newPassword)` → validate reset token → đặt mật khẩu mới → xóa token
- `adminUnlockAccount(userId)` → xóa `auth:failcount:{username}` trong Redis, set status = ACTIVE
- `me(httpRequest)` → `GET /api/v1/auth/me` (2026-08-01). Tách `roles`/`permissions` trực tiếp từ
  `principal.getAuthorities()` (không query lại — phải khớp JWT hiện có), gọi
  `AccessControlService.resolveMyScopes(userId)` (module `organization`, rule `C7`) để có
  `scopes[]`/`defaultPlantId`. **Không** `@PreAuthorize` cụ thể — chỉ cần đã đăng nhập (`SecurityConfig`
  matcher `/api/v1/auth/me` là `authenticated()`, ngoại lệ duy nhất trong 5 endpoint của controller này,
  4 endpoint còn lại permit-all)

#### `PasswordResetTokenService`
- Sinh secure random token (UUID v4), lưu Redis với TTL 15 phút
- Token chỉ dùng được 1 lần (xóa ngay khi đã dùng)
- Nếu request reset mới → overwrite token cũ (không cho tích lũy)

## Bất biến nghiệp vụ (tách từ `CLAUDE.md` §10.6)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B33 | Account enumeration prevention: sai username và sai password trả **cùng** `INVALID_CREDENTIALS` | `AuthServiceTest.login_failsOnUserNotFound` |
| B34 | Brute-force: đạt ngưỡng fail → khoá, và **không** truy vấn user nữa (short-circuit) | `AuthServiceTest.login_blockedAfterMaxFailedAttempts` |
| B80 | **RTR (`D8a`)**: refresh token đã bị rotate away mà quay lại ⇒ `TOKEN_REUSE_DETECTED` (401) + xoá **cả** refresh token **lẫn** device session của user. Chỉ áp dụng nhánh `stored == null`. Thứ tự rotate bắt buộc: **lưu-mới → mark-used → xoá-cũ** | `AuthServiceTest.refresh_reusedToken_throwsTokenReuseDetectedAndForceLogoutAll` · `.refresh_validToken_rotatesAndReturnsNewPair` (`InOrder`) · `.refresh_storedTokenMismatch_throwsRefreshTokenExpired` |
| B81 | **Absolute session timeout (`D8b`)**: phiên sống quá `app.jwt.absolute-session-timeout-ms` (30 ngày, đếm từ **login**) ⇒ `SESSION_ABSOLUTE_TIMEOUT` (401) + force logout **cả** refresh token **lẫn** device session. Ba vế bắt buộc: ① check nằm **sau** validate `stored`, **trước** rotate · ② rotate **carry-forward** `sessionCreatedAt` cũ sang tokenId mới, **không** stamp `now` · ③ phiên không có stamp (trước `D8b`) **fail-open**, coi như bắt đầu từ bây giờ | `AuthServiceTest.refresh_sessionOlderThanAbsoluteTimeout_throwsAndForceLogoutAll` · `.refresh_carriesTheOriginalSessionStartForwardToTheNewTokenId` · `.refresh_sessionWithoutStartStamp_isTreatedAsStartingNow` · `.refresh_sessionJustUnderAbsoluteTimeout_rotatesNormally` · `.login_recordsTheSessionStart` |

> **[`D1`, 2026-07-28] Nợ #7 đã trả.** `LoginRequest`, `RefreshRequest`, `LogoutRequest` (và
> `CreateUserRequest`/`UpdateUserRequest` ở module `user`) đều override `toString()` che secret —
> record không dùng được `@ToString.Exclude` nên phải viết tay. Hai điều **đừng** "tiện tay sửa":
> `tokenId` **cố ý hiện** (định danh để tra Redis, không phải credential — che nó làm log mất giá trị
> debug mà không tăng bảo mật), và mask **phân biệt `null` vs `***`** (thiếu password và ẩn password
> dẫn tới hai hướng điều tra khác nhau). Regression guard: `SensitiveRequestToStringTest`.
> Thêm field secret mới vào các record này ⇒ **phải** cập nhật `toString()` + test.

> **[`D8a`, 2026-08-03] RTR đã implement.** `refresh()` phân biệt "hết hạn bình thường" với "token bị
> đánh cắp và dùng lại". Cơ chế + 2 giới hạn đã biết (race double-submit, chỉ nhánh `stored == null`):
> `common/security/CLAUDE.md` §4.12.

> **[`D8b`, 2026-08-03] Absolute session timeout đã implement — nợ #6 nay trả 2/3.**
> 🔴 **`D8c` (forgot-password) vẫn chưa có dòng code nào** — đừng đọc `B80`+`B81` rồi tưởng cả nợ #6
> đã đóng. Ba method liệt kê ở mục "Trách nhiệm chính" bên trên (`forgotPassword`, `resetPassword`,
> `adminUnlockAccount`) vẫn là **thiết kế, chưa tồn tại trong code**.
>
> Ba điều `D8b` chốt, **đừng "sửa cho gọn"**:
> 1. **`sessionCreatedAt` lưu ở companion key** `auth:refresh:{userId}:{tokenId}:meta`, **không** đổi
>    value refresh token thành JSON payload như thiết kế gốc §4.15 mô tả. Nhờ vậy
>    `saveRefreshToken`/`getRefreshToken` và toàn bộ nhánh RTR (`B80`) không đổi một dòng nào.
> 2. **Key `:meta` cố ý NẰM TRONG** pattern SCAN `auth:refresh:{userId}:*` (force-logout quét luôn) —
>    **ngược** với key `auth:refresh:used:{tokenId}` của `D8a` vốn cố ý nằm **ngoài**. Hai key, hai
>    yêu cầu trái nhau, cùng namespace; mỗi cái có một test canh đúng chiều của nó.
> 3. **Fail-open cho phiên không có stamp** là quyết định, không phải sơ suất: fail-closed sẽ đăng
>    xuất mọi user đang online ngay lúc deploy mà không tăng bảo mật (refresh TTL 7 ngày ⇒ trong một
>    tuần mọi phiên sống đều có stamp).

# module/auth — AuthService & AuthController

> Tách từ `CLAUDE.md` §4.6 + §10.6 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/auth/**`.
> Phần thiết kế đầy đủ JWT/Redis/rate-limit/RTR/fingerprint nằm ở
> `../../common/security/CLAUDE.md` — đọc file đó nếu cần hiểu cơ chế mà `AuthService` gọi xuống.

## Trách nhiệm chính

#### `AuthService`
- `login` → validate credentials (constant-time) → cấp cặp token (có kiểm tra IP session)
- `refresh` → resolve userId từ `tokenId` (không đọc access token — P0, `B107`) → validate refresh token → RTR check → xoay vòng atomic → trả token mới
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
| B95 | **Concurrent refresh race (mở rộng `D8`)**: request đến sau, race trên **cùng** `tokenId` đã bị request khác rotate xong trong vài giây gần nhất ⇒ nhận lại **đúng** cặp token mà request thắng vừa sinh ra, **không** bị chẩn đoán thành `TOKEN_REUSE_DETECTED`, **không** rotate lần hai. Cơ chế: khoá tư vấn `acquireRefreshLock` (`SET NX PX`, TTL 2s) trước validate + breadcrumb `saveRotationResult`/`getRotationResult` (TTL 5s) đọc **trước** `wasRefreshTokenUsed` trong nhánh `stored == null`. **Không grace window** — token cũ vẫn chết ngay, chỉ request trùng lặp được dẫn tới cặp đã tồn tại. Breadcrumb hết hạn hoặc cặp nó trỏ tới đã mất ⇒ rơi xuống `wasRefreshTokenUsed` y hệt trước phase này, RTR thật (`B80`) không đổi | `AuthServiceTest.refresh_concurrentDuplicate_absorbsRotationResultInsteadOfThrowingReuseDetected` · `.refresh_concurrentDuplicate_extendsDeviceSession` · `.refresh_rotationResultTargetGone_fallsThroughToReuseCheck` · `.refresh_lockNotAcquired_stillDetectsGenuineReuseWhenNoBreadcrumbExists` · `.refresh_validToken_rotatesAndReturnsNewPair` (`saveRotationResult` verify) |
| B101 | **Account Recovery (`D8c`)**: `forgotPassword` trả **`void`** và không branch theo kết quả — nhánh "email không tồn tại" **không** gọi `PasswordResetTokenService`/`EmailNotificationService`, **không** audit gì, giữ hai nhánh giống hệt nhau ở mọi collaborator (account enumeration prevention thật, không chỉ cùng response text). `resetPassword` xoá token **cả hai chiều** (`auth:reset:{token}` + `auth:reset:user:{userId}`) rồi force-logout toàn bộ phiên (`deleteAllUserTokens` + `deleteAllDeviceSessions`) trước khi audit `PASSWORD_RESET`. `adminUnlockAccount` là `@PreAuthorize("hasRole('ADMIN')")` — **role-based**, không phải `PERM_*` scope-based như phần lớn service khác trong repo, cùng kiểu `UserService` đã dùng | `PasswordResetTokenServiceTest` (round-trip + invalidate-on-regenerate) · `AuthServiceTest.forgotPassword_existingEmail_generatesTokenAndSendsEmail` · `.forgotPassword_unknownEmail_doesNothingObservable` · `.resetPassword_validToken_updatesPasswordAndForcesLogoutEverywhere` · `.resetPassword_invalidToken_throwsResetTokenInvalidBeforeTouchingAnything` · `.adminUnlockAccount_resetsStatusAndFailCount` · `AuthMethodSecurityTest` (deny/allow `hasRole('ADMIN')`) |
| B107 | **[P0, 2026-08-06]** `refresh()` resolve `userId` qua `tokenStore.getTokenOwner(request.tokenId())` (Redis `auth:refresh:owner:{tokenId}`, ghi kèm mỗi lần `saveRefreshToken`) — **không** đọc request attribute `authenticatedUserId` nữa, nên endpoint không còn phụ thuộc header `Authorization` chút nào (trước đây header thiếu/hỏng chặn cứng cả request, xem `common/security/CLAUDE.md §4.20`). `B80`/`B81`/`B95` không đổi — cả ba chạy **sau** khi `principal` đã resolve | `AuthServiceTest.refresh_neverReadsAuthorizationHeader_identityComesFromTokenIdAlone` · `.refresh_unknownTokenOwner_throwsRefreshTokenExpired` · `.refresh_ownerUserDeleted_throwsRefreshTokenExpired` · `TokenStoreServiceTest.saveRefreshToken_alsoWritesTheReverseOwnerLookup` · `.getTokenOwner_roundTripsTheUserId` |
| B275 | **Tài khoản `PENDING_SETUP` không bao giờ nhận token (2026-11-06)**: login đúng mật khẩu ban đầu trả `PasswordSetupResponse{kind:"PASSWORD_SETUP"}` — không `accessToken`/`refreshToken`/`tokenId`, không `saveRefreshToken`/`saveDeviceSession`/`deleteAllUserTokens`; sai mật khẩu vẫn là `INVALID_CREDENTIALS` và **không** gửi mã. Nhánh nằm trong `!principal.isEnabled()` nên chỉ chạm tới **sau** khi mật khẩu khớp. `adminUnlockAccount` **không** đổi `PENDING_SETUP` thành `ACTIVE` (trước đó `activate()` vô điều kiện = đường vòng qua bước đặt mật khẩu) | `AuthServiceTest.login_pendingSetup_returnsChallengeWithoutIssuingTokens` · `.login_pendingSetup_wrongPassword_sendsNothing` · `.adminUnlockAccount_pendingSetup_isNotActivated` · `PasswordFlowControllerTest.login_pendingSetup_returnsChallengeAndNoTokens` |
| B276 | **Mã và token không bao giờ được lưu**: `password_challenges.code_hash` = HMAC-SHA256 (khoá = JWT secret, tách miền bằng nhãn `code`) gắn với `challengeId`; `setupToken` = `challengeId.HMAC(setup-token, challengeId, code_hash)` tính lại mỗi lần — dump DB không dùng được. Mã chỉ tới `EmailNotificationService.sendPasswordCode`; log chỉ ghi `challengeId`/`userId`/`purpose`/`expiresAt` | `PasswordFlowServiceTest.beginSetup_sendsSixDigitCodeAndStoresOnlyItsHash` · `.complete_badTokens_areRefused` |
| B277 | **Đổi trạng thái challenge chỉ qua UPDATE có điều kiện**; số dòng bị ảnh hưởng (không phải đọc trước) quyết định ai thắng: `markVerified` (PENDING, chưa hết hạn, `attempts < 5`), `markCompleted` (chỉ VERIFIED), `supersede` (không đụng COMPLETED). Không `save()` đè `status`/`attempts`. **Phát mã mới luôn `supersede` mã `PENDING` trước của cùng user + mục đích** — cả login lặp sau cooldown, gửi lại SETUP và RESET đều đi qua `reissue()`; trước 2026-11-07 đường login bị sót nên hai mã cùng sống | `PasswordChallengeRepositoryIT` (6 ca, Postgres thật) · `PasswordFlowServiceTest.beginSetup_repeatAfterCooldown_supersedesPreviousCode` · `.resend_afterCooldown_supersedesPreviousCode` |
| B278 | **Mã sai tốn một lượt dù request thất bại** (`@Transactional(noRollbackFor = AppException.class)` trên `verify`); lượt thứ 5 ⇒ `429 CODE_ATTEMPTS` + `Retry-After`, và mã **đúng** sau đó cũng bị từ chối. Số lượt đếm từ giá trị đọc **trước** khi tăng (không từ entity sau UPDATE) | `PasswordFlowServiceTest.verify_wrongCode_countsAnAttempt` · `.verify_fiveWrongCodes_lockTheChallenge` |
| B279 | **Policy kiểm TRƯỚC khi tiêu token**: mật khẩu yếu hoặc trùng mật khẩu hiện tại ⇒ `422 PASSWORD_POLICY`, token còn dùng được. Chính sách ở `common/security/PasswordPolicy` (≥ 8, hoa/thường/số/đặc biệt, ≤ 72 byte). **Chỉ** các đường mới (`complete`, `change`, `accounts/commands`) áp dụng; `POST /users/v1` và `reset-password` cũ không đổi | `PasswordFlowServiceTest.complete_weakPassword_leavesTokenUnspent` · `.complete_sameAsInitialPassword_isRejected` · `PasswordPolicyTest` |
| B280 | **`complete` và `change` thu hồi MỌI phiên và không cấp phiên mới**: `revokeAllSessions()` (`authVersion`+1) + `deleteAllUserTokens` + `deleteAllDeviceSessions`. `complete` đầu tiên chuyển `PENDING_SETUP` → `ACTIVE`. `change` dùng chung bộ đếm sai mật khẩu với login (5 lần ⇒ `ACCOUNT_LOCKED`) và trả **422** `CURRENT_PASSWORD_INVALID` (401 sẽ làm client kết thúc phiên) | `PasswordFlowServiceTest.complete_firstSetup_activatesAndEndsSessions` · `.change_success_endsAllSessions` · `.change_wrongCurrentPassword_isCounted` · `.change_afterFiveFailures_isLocked` |
| B281 | **RESET công khai trả lời giống hệt cho định danh lạ**: không có tài khoản ACTIVE ứng với `identifier` (kể cả `PENDING_SETUP`/`LOCKED`/`INACTIVE`) ⇒ một hàng "decoy" (`user_id` NULL, id suy ra từ HMAC của định danh nên lặp lại trong 30 giây trả cùng id) không gửi email và thất bại ở `verify` như một mã sai (`CODE_INVALID`, rồi `CODE_ATTEMPTS`). Dọn: hàng hết hạn > 1 ngày bị xoá ở lần phát mã kế tiếp | `PasswordFlowServiceTest.reset_unknownIdentifier_getsDecoyThatFailsLikeAWrongCode` · `.reset_pendingSetupAccount_getsDecoy` · `.reset_activeAccount_sendsCode` |
| B282 | **Endpoint công khai phải khai ở HAI nơi**: `SecurityConfig` (`permitAll`) **và** `JwtAuthenticationFilter.BYPASS_PATHS` — thiếu vế sau thì client luôn gắn token cũ/hỏng bị `401` trên endpoint `permitAll`. `/auth/v1/password/change` ngược lại **không** nằm trong cả hai (cần phiên). Idempotency của nhóm này là **trạng thái từng bước**, không phải bảng `Idempotency-Key` (`PasswordFlowService` javadoc) | `PasswordFlowControllerTest` (wire) · `FlywayMigrationIT` (V96) |

> **[`D1`, 2026-07-28] Nợ #7 đã trả.** `LoginRequest`, `RefreshRequest`, `LogoutRequest` (và
> `CreateUserRequest`/`UpdateUserRequest` ở module `user`) đều override `toString()` che secret —
> record không dùng được `@ToString.Exclude` nên phải viết tay. Hai điều **đừng** "tiện tay sửa":
> `tokenId` **cố ý hiện** (định danh để tra Redis, không phải credential — che nó làm log mất giá trị
> debug mà không tăng bảo mật), và mask **phân biệt `null` vs `***`** (thiếu password và ẩn password
> dẫn tới hai hướng điều tra khác nhau). Regression guard: `SensitiveRequestToStringTest`.
> Thêm field secret mới vào các record này ⇒ **phải** cập nhật `toString()` + test.

> **[`D8a`, 2026-08-03] RTR đã implement.** `refresh()` phân biệt "hết hạn bình thường" với "token bị
> đánh cắp và dùng lại". Cơ chế + phạm vi (chỉ nhánh `stored == null`): `common/security/CLAUDE.md`
> §4.12. Giới hạn "race double-submit" của `D8a` **đã đóng** ở phase concurrent-refresh-token-race
> sau `D8b` — xem `B95` + `common/security/CLAUDE.md §4.12a`.

> **[`D8b`, 2026-08-03] Absolute session timeout đã implement — nợ #6 khi đó mới trả 2/3.**
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

> **[P0, 2026-08-06] `refresh()` không còn phụ thuộc access token.** FE báo `/auth/refresh` trả
> `401 TOKEN_MALFORMED` dù endpoint `permitAll` — hoá ra endpoint chỉ *permitAll trên danh nghĩa*: nó
> đọc `authenticatedUserId` từ request attribute, attribute đó chỉ set được khi
> `JwtAuthenticationFilter` parse **thành công** access token từ header. Header thiếu/rác chặn cứng
> request trước khi chạm refresh token thật — đúng lúc access token hỏng là lúc client cần refresh
> nhất. Đã sửa bằng reverse lookup `tokenId → userId` (`B107`, `common/security/CLAUDE.md §4.20`) —
> refresh giờ dùng đúng `{refreshToken, tokenId, deviceId}` như tài liệu đã hứa từ đầu.

> **[`D8c`, 2026-08-06] Account recovery đã implement — nợ #6 nay trả đủ 3/3.** `forgotPassword`,
> `resetPassword`, `adminUnlockAccount` (+ `PasswordResetTokenService`, `EmailNotificationService`)
> không còn là thiết kế — xem bất biến `B101` ở bảng trên và `common/security/CLAUDE.md §4.14`.
> Quyết định hạ tầng chốt với user (D8c): gửi email bằng **mock/log console**, không
> `spring-boot-starter-mail`, không interface cho một implementation duy nhất
> (`coding-rules.md §11.5`).
> 🔴 **[2026-11-06] Quyết định đó đã được đảo một phần** để phục vụ luồng mã email (`B275`–`B282`): FE cần hộp thư
> QA thật. `spring-boot-starter-mail` nay có trong `pom.xml`; `EmailNotificationService` gửi SMTP **chỉ khi**
> `SPRING_MAIL_HOST` được đặt, ngược lại vẫn chỉ log (mã không bao giờ vào log). `sendPasswordResetEmail` (link reset
> của D8c) **vẫn là mock** — chưa ai yêu cầu gửi thật. Vẫn không có interface.
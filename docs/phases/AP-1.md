### AP-1 – Luồng tài khoản và mật khẩu: đăng nhập lần đầu bằng mã email, đổi mật khẩu (2026-11-06)

Nguồn: `FE_REQUEST_ACCOUNT_PASSWORD_FLOWS_2026-11-06.md` (P1, FE bị chặn bật `NEXT_PUBLIC_ACCOUNT_API_ENABLED`). Trả lời FE:
`BE_RESPONSE_ACCOUNT_PASSWORD_FLOWS_2026-11-06.md`. Migration **`V96`** (`password_challenges`). Bất biến **`B275`–`B282`** (`module/auth/CLAUDE.md`).
Yêu cầu của FE là **hợp đồng đề xuất**, không phải lỗi: BE giữ nguyên path/DTO FE đã dựng adapter, chỉ chốt mã lỗi.

#### Cái gì được thêm

| | |
|---|---|
| Trạng thái | `UserStatus.PENDING_SETUP`. `users.status` là `VARCHAR(20)` không CHECK ⇒ không cần backfill |
| Bảng | `password_challenges` (`V96`): mỗi dòng một mã email. **Không lưu mã**; `code_hash` = HMAC bind `challengeId`. `user_id` NULL = hàng decoy |
| Service | `PasswordFlowService` (`request` · `beginSetup` · `verify` · `complete` · `change`), `PasswordPolicy` (`common/security`), `UserService.createPendingSetup` |
| Endpoint | `POST /auth/v1/password-challenges` · `/verify` · `/complete` (công khai) · `POST /auth/v1/password/change` (Bearer) · `POST /admin/users/v1/accounts/commands` (`kind:"CREATE"`, `PERM_ACCESS_MANAGE` GLOBAL) |
| Login | `AuthService.login` trả `LoginResult` = `AuthResponse` hoặc `PasswordSetupResponse{kind:"PASSWORD_SETUP"}` |
| Lỗi | 8 hằng `AuthErrorCode` + `RetryAfterException` (header `Retry-After` cho `CODE_ATTEMPTS`/`RESEND_TOO_SOON`) |
| Email | `spring-boot-starter-mail`; `EmailNotificationService.sendPasswordCode` (`@Async`) gửi SMTP **chỉ khi** `SPRING_MAIL_HOST` được đặt, ngược lại chỉ log. `docker-compose.yml` thêm service `mailpit` (profile `mail`) |

#### Quyết định cần PO biết

1. **Đảo một phần quyết định `D8c` "không `spring-boot-starter-mail`".** FE cần hộp thư QA thật (#7). Mặc định vẫn như cũ: không đặt `SPRING_MAIL_HOST` ⇒ không gửi gì, mã không vào log.
   `sendPasswordResetEmail` (link reset cũ) vẫn là mock.
2. **Không có bảng `Idempotency-Key`.** Header được nhận và bỏ qua; mỗi bước tự replay-safe theo trạng thái (cooldown 30 giây trả lại challenge đang sống · verify cùng mã trả cùng token · complete
   lặp với cùng mật khẩu là no-op · tạo tài khoản trùng hệt lệnh cũ trả lại tài khoản). `change` không replay được (nó thu hồi phiên mang nó). Lý do: lưu `setupToken` để replay verify nghĩa là
   lưu một bearer credential; token được tính lại từ HMAC nên không cần lưu.
3. **`POST /users/v1` cũ không đổi** (vẫn `ACTIVE`): seed, `qa-bootstrap.sh`, `qa-accounts.sh`, demo đều dựa vào đó. Tài khoản `PENDING_SETUP` chỉ đi qua `accounts/commands`.
4. **RESET công khai coi `PENDING_SETUP`/`LOCKED`/`INACTIVE` như định danh lạ** (decoy, không email). Chọn thận trọng: mật khẩu ban đầu phải được chứng minh trước.
5. **Sửa một lỗ có sẵn:** `AuthService.adminUnlockAccount` gọi `activate()` cho mọi trạng thái ⇒ nếu có `PENDING_SETUP` thì unlock là đường vòng qua bước đặt mật khẩu. Nay giữ nguyên `PENDING_SETUP`.

#### Chưa làm (cố ý, ngoài yêu cầu FE lần này)

`accounts/commands` chỉ có `CREATE` (không `UPDATE`/`STATUS`/`RESEND`/`GRANT`/`REVOKE`/`ROLE`); không có `GET /admin/users/v1/accounts*` (list/detail/references); không có hộp thư QA (hạ tầng).
`MANUFACTURING_PHASE_PLAN_P7_P11.md` ghi "đánh số lại từ `V96`" — migration kế tiếp của plan đó nay là **`V97`**.

#### Bẫy đã gặp

- Endpoint công khai phải khai **cả** `SecurityConfig` **và** `JwtAuthenticationFilter.BYPASS_PATHS`; thiếu vế sau thì client gắn token cũ nhận 401. `AuthMeSecurityTest.passwordChallenges_arePermitAll_andIgnoreAStaleToken`
  canh vế này (đã thử đột biến: bỏ 3 entry ⇒ đỏ).
- Đếm lượt thử sai phải lấy giá trị **trước** `UPDATE ... attempts+1`. Bản đầu cộng `+1` lên entity đã bị test fake sửa tại chỗ ⇒ khoá sớm một lượt; đã đổi sang biến cục bộ (`PasswordFlowServiceTest.verify_fiveWrongCodes_lockTheChallenge`).
- `@Transactional` mặc định rollback khi ném `AppException` ⇒ lượt sai không được ghi. `verify` và `change` dùng `noRollbackFor = AppException.class` (`B278`).

#### Nghiệm thu

Test mới: `PasswordFlowServiceTest` (24) · `PasswordFlowControllerTest` (10) · `PasswordPolicyTest` · `UserServiceCreatePendingSetupTest` (7) · `PasswordChallengeRepositoryIT` (6, Postgres thật) ·
+3 ca `AuthServiceTest`, +3 `AdminControllerTest`, +2 `AuthMeSecurityTest`. `FlywayMigrationIT` đổi pin phiên bản `95` → `96`.
Test cũ phải sửa vì chữ ký đổi (không nới assertion): `AuthServiceTest` ép kiểu `(AuthResponse)` ở 3 chỗ gọi `login`; `AuthServiceTest`/`AuthMethodSecurityTest`/`AuthControllerTest`/`AuthMeSecurityTest` thêm `PasswordFlowService`; `AdminControllerTest` thêm `UserService`.
`mvn -o clean verify`: **2378 unit + 366 IT**, 0 failures/errors (baseline ghi gần nhất 2317 + 345; con số giữa đó chưa được ghi, nên không so được từng bước).
**Smoke với thư thật (2026-11-07)** trên một stack cô lập (Postgres/Redis/Mailpit riêng, backend `:18080` chạy jar build từ code này, **không** đụng DB dev):
21/21 ca kiểm tra đạt, và mọi phép đếm thư khớp (0 thư khi tạo tài khoản, khi sai mật khẩu, khi RESET định danh lạ; đúng 1 thư cho mỗi lần phát mã) — tạo tài khoản (policy, replay, trùng email) · login sai/đúng mật khẩu ban đầu · thư mã 6 số tới Mailpit · gửi lại sớm ⇒ `429` + `Retry-After` ·
verify sai/đúng · complete trùng mật khẩu ban đầu ⇒ `422`, rồi thành công · replay complete · mật khẩu ban đầu hết dùng · login mật khẩu mới + `/me` `ACTIVE` ·
change sai ⇒ `422`, đúng ⇒ phiên cũ `401 TOKEN_REVOKED` · RESET định danh lạ ⇒ `200`, **0 thư** · RESET tài khoản thật ⇒ 1 thư. Script: `smoke.sh` (scratchpad phiên, không commit).
**Chưa có smoke trên môi trường QA tích hợp** của FE (build chưa deploy ở đó).

**Smoke tìm ra hai lỗi mà test tự động không bắt được, đã sửa:**
1. **Login lặp sau cooldown để mã cũ sống.** `beginSetup` phát challenge mới nhưng không `supersede` challenge `PENDING` trước ⇒ hai mã cùng dùng được (resend và RESET thì đã đúng).
   Gộp vào `reissue()` dùng chung. Test `beginSetup_repeatAfterCooldown_supersedesPreviousCode` (đột biến về code cũ ⇒ đỏ); smoke lại: mã cũ ⇒ `CODE_EXPIRED`.
2. **Mỗi thư trễ 5–15 giây.** JavaMail tra ngược DNS tên máy hai lần mỗi lần gửi: cho lệnh `HELO` (8,3 giây đo được trên máy dev IP `26.154.206.236`) và cho
   `Message-ID` khi thiếu `mail.from` (5,2 giây). SMTP thô tới Mailpit chỉ 0,16 giây. Thêm `spring.mail.properties.mail.smtp.localhost` (`MAIL_SMTP_LOCALHOST`, mặc định `localhost`)
   và `mail.from` (= `MAIL_FROM`). Đo lại: **~0,5 giây**. Unit test không thấy lỗi này vì nó nằm trong DNS của máy, không trong code.

**Còn để ngỏ (không sửa, ngoài phạm vi):** `@Async` của `sendPasswordCode` chạy trên `SimpleAsyncTaskExecutor` (tạo thread mới mỗi lần, không truyền MDC ⇒ dòng log gửi thư thiếu
`traceId`). Nguyên nhân có sẵn: `AsyncConfig` chỉ khai bean `auditExecutor`, nên Spring Boot không tạo executor mặc định. Đối chiếu dòng gửi thư với yêu cầu bằng email người nhận,
hoặc bằng `challengeId` ở dòng "Password code issued" ngay trước.

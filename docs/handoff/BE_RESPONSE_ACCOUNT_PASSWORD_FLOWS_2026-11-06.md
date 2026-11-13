# BE trả lời: luồng tài khoản và mật khẩu (đăng nhập lần đầu, đổi mật khẩu)

**BE_RESPONSE_ACCOUNT_PASSWORD_FLOWS_2026-11-06 · v1 · 06/11/2026**
Trả lời `FE_REQUEST_ACCOUNT_PASSWORD_FLOWS_2026-11-06`. Migration mới: **V96**. Thay đổi wire: có, xem §5.

## §0 — Kết luận

- **Đã cài đặt đủ 6 điều kiện đầu ra (#1–#6) theo đúng path và DTO FE đề xuất ở §3.** Test tự động (unit, contract MockMvc, IT trên Postgres thật) đã chạy. Ngày 07/11 BE đã chạy hết luồng trên một môi trường cô lập có hộp thư Mailpit thật (tạo tài khoản → login → thư mã → verify → complete → login lại → đổi mật khẩu → RESET): 21/21 ca kiểm tra đạt, mọi phép đếm thư khớp. **Chưa** có smoke trên môi trường QA tích hợp của FE vì build này chưa được triển khai ở đó.
- **Đăng nhập lặp sau 30 giây thay mã cũ** (sửa 07/11): mã cũ trả `CODE_EXPIRED`, chỉ mã trong thư mới nhất dùng được — giống gửi lại mã.
- **#7 (hộp thư QA) BE chưa cung cấp được.** Code gửi email đã có, nhưng máy chủ thư và hộp thư QA là hạ tầng chưa ai dựng — xem §3 câu 5. Khi chưa cấu hình, mã **không** được gửi và log nói rõ điều đó; FE chưa nghiệm thu được ca email thật.
- **BE không công bố được host/build/tài khoản thử** (câu 6): build này chưa deploy. Sau khi deploy, nhận diện bản bằng `max(flyway_schema_history.version)` = **96**.

## §1 — Hợp đồng (path sau `/api`)

Mọi response dùng envelope `{code, result, message, errors?}`. Thời gian là ISO-8601 UTC (`Z`).

| Việc | Request | `result` |
|---|---|---|
| Admin tạo tài khoản | `POST /admin/users/v1/accounts/commands` `{kind:"CREATE", username, email, password}` | **201** `{userId, username, email, status:"PENDING_SETUP", roles, createdAt, updatedAt}` |
| Đăng nhập | `POST /auth/v1/login` `{username, password}` | tài khoản bình thường: như cũ (token). `PENDING_SETUP`: `{kind:"PASSWORD_SETUP", email, challenge:{id, expiresAt, resendAt}}` — **không có** `accessToken`/`refreshToken`/`tokenId` |
| Gửi lại mã (SETUP) / yêu cầu mã (RESET) | `POST /auth/v1/password-challenges` `{identifier, purpose:"SETUP"\|"RESET", previousId?}` | `{id, expiresAt, resendAt}` |
| Đổi mã lấy token | `POST /auth/v1/password-challenges/verify` `{challengeId, code}` | `{setupToken}` |
| Đặt mật khẩu mới | `POST /auth/v1/password-challenges/complete` `{setupToken, password}` | không có `result` (field vắng mặt, không phải `null`) |
| Đổi mật khẩu khi đã đăng nhập | `POST /auth/v1/password/change` (Bearer) `{currentPassword, password}` | không có `result` |

Quyền tạo tài khoản: `PERM_ACCESS_MANAGE` ở phạm vi **GLOBAL** (không dựa vào tên vai trò). `POST /users/v1` cũ **không đổi** — vẫn tạo tài khoản `ACTIVE` (seed và script demo dựa vào đó).

## §2 — Mã lỗi (đã chốt, FE map theo `code`)

| Tình huống | HTTP | `code` |
|---|---|---|
| Sai mật khẩu ban đầu (không gửi mã) | 401 | `INVALID_CREDENTIALS` |
| Gửi lại SETUP mà không có `previousId` hợp lệ của đúng tài khoản | 403 | `PASSWORD_SETUP_REQUIRED` |
| Mã sai (kể cả mã không đủ 6 chữ số, hoặc `challengeId` không tồn tại) | 400 | `CODE_INVALID` |
| Mã hết hạn / đã bị mã mới thay thế | 422 | `CODE_EXPIRED` |
| Mã đã dùng (challenge đã hoàn tất, hoặc replay với mã khác) | 422 | `CODE_USED` |
| 5 lần sai liên tiếp trên một challenge | 429 + `Retry-After` | `CODE_ATTEMPTS` |
| Gửi lại trước `resendAt` | 429 + `Retry-After` | `RESEND_TOO_SOON` |
| Mật khẩu mới không đạt policy, hoặc trùng mật khẩu hiện tại | 422 + `errors[{field:"password"}]` | `PASSWORD_POLICY` |
| Mật khẩu hiện tại sai (đổi mật khẩu) | **422** | `CURRENT_PASSWORD_INVALID` |
| Setup token sai / hết hạn / đã dùng | 422 | `SETUP_TOKEN_INVALID` |
| Email hoặc username đã có (tạo tài khoản) | 409 | `EMAIL_ALREADY_EXISTS` / `USERNAME_ALREADY_EXISTS` |

- `CURRENT_PASSWORD_INVALID` và `SETUP_TOKEN_INVALID` là **422, không phải 401**: 401 trên lời gọi có phiên thường khiến client kết thúc phiên.
- **Không có** `SETUP_TOKEN_EXPIRED`: token hết hạn, đã dùng và chưa từng tồn tại là cùng một `SETUP_TOKEN_INVALID`.
- Account `PENDING_SETUP` **không có token nào** nên không thể gọi API ERP; mã `PASSWORD_SETUP_REQUIRED` trên API ERP không phát sinh.
- Policy mật khẩu: ≥ 8 ký tự, có chữ hoa, chữ thường, số, ký tự đặc biệt, ≤ 72 byte (BCrypt). Mọi quy tắc vi phạm được liệt kê trong một `message` ở field `password`.

## §3 — Trả lời §5 của yêu cầu

1. **Giữ path/DTO ở §3?** Có, giữ nguyên.
2. **`username` = email chuẩn hoá?** BE **không ép**: nhận `username` đúng như FE gửi (cho phép `a-z A-Z 0-9 . _ @ + -`, 3–100 ký tự) và chuẩn hoá `email` về chữ thường. FE gửi `username = email` thì được; tài khoản cũ giữ nguyên.
3. **Login `PENDING_SETUP` có tự tạo/gửi challenge?** Có, sau khi mật khẩu ban đầu đúng. Đăng nhập lặp trong 30 giây (trước `resendAt`) nhận lại **cùng challenge**, không gửi thêm email.
4. **Mã sáu chữ số, `expiresAt`, `resendAt` UTC?** Có. Mã sống **10 phút**, gửi lại sau **30 giây**, token setup sống **10 phút** sau khi verify.
5. **Mailbox QA?** **Chưa có.** BE đã thêm khả năng gửi SMTP, bật bằng biến môi trường `SPRING_MAIL_HOST` (+ `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, và `MAIL_SMTP_AUTH`/`MAIL_SMTP_STARTTLS` cho SMTP thật như Gmail). Thư thường tới trong khoảng 0,5 giây. Để có hộp thư cô lập cho QA cần dựng một máy chủ thư thử (đề xuất Mailpit, đã thêm vào `docker-compose.yml` dưới profile `mail`: SMTP `1025`, giao diện `8025`) và quyết định **ai** được đọc. Việc đó thuộc hạ tầng/PO, BE chưa làm. Log chỉ ghi `challengeId`, `userId`, `purpose`, `expiresAt` (không ghi mã) để đối chiếu với hộp thư.
6. **Host/build/tài khoản thử?** Chưa deploy. Tài khoản thử tạo bằng `qa_admin` qua `POST /admin/users/v1/accounts/commands`.
7. **Thu hồi phiên?** **Toàn bộ phiên cũ**, cả khi complete lẫn change: `authVersion` tăng (mọi access token cũ chết ngay), xoá refresh token và phiên thiết bị. Sau complete/change người dùng phải đăng nhập lại; không có phiên mới nào được cấp.

## §4 — Idempotency

BE **không** có bảng `Idempotency-Key` cho các lời gọi này. Header được chấp nhận và bỏ qua; replay an toàn đến từ chính trạng thái của từng bước:

| Bước | Lặp lại cùng ý định |
|---|---|
| Tạo tài khoản | Cùng username + email + mật khẩu ban đầu khi tài khoản còn `PENDING_SETUP` ⇒ trả lại tài khoản đó (201), không phải 409. Khác một trong ba ⇒ vẫn 409 |
| Yêu cầu/gửi lại mã | Trong 30 giây ⇒ trả lại challenge đang sống, không gửi email thứ hai |
| Verify | Cùng mã khi token chưa dùng ⇒ **cùng** `setupToken`. Mã khác ⇒ `CODE_USED` |
| Complete | Sau thành công, lặp lại với **cùng mật khẩu** ⇒ thành công, không đổi gì; mật khẩu khác ⇒ `SETUP_TOKEN_INVALID` |
| Change | **Không replay được**: lời gọi đầu đã thu hồi chính phiên mang nó, nên lặp lại nhận 401 (token đã bị thu hồi). Sau timeout, FE nên thử đăng nhập bằng mật khẩu **mới** để biết lời gọi đầu đã thành công hay chưa |

Không có `409 IDEMPOTENCY_CONFLICT` cho nhóm này.

## §5 — Thay đổi hành vi (đọc trước khi bật cờ)

1. **`POST /auth/v1/login`** có thêm một nhánh `result.kind = "PASSWORD_SETUP"`. FE chỉ cần rẽ theo `kind`; nhánh token không có field `kind`.
2. **`PATCH /admin/users/v1/{id}/unlock`** không còn kích hoạt tài khoản `PENDING_SETUP` (trước đó `unlock` gọi `activate()` cho mọi trạng thái, tức là có thể bỏ qua bước đặt mật khẩu). Tài khoản `PENDING_SETUP` giữ nguyên trạng thái.
3. **`UserStatus`** có thêm giá trị `PENDING_SETUP`; cột `users.status` không có CHECK nên không cần backfill.
4. **RESET công khai không áp dụng cho tài khoản `PENDING_SETUP`/`LOCKED`/`INACTIVE`**: các tài khoản này nhận phản hồi giả giống hệt tài khoản không tồn tại và không có email nào được gửi. Tài khoản chưa setup phải chứng minh mật khẩu ban đầu qua login trước.
5. **Chưa có**: RESEND do admin kích hoạt, `STATUS`/`UPDATE`/`GRANT`/`REVOKE`/`ROLE` trong `accounts/commands` (yêu cầu này chỉ cần `CREATE`; endpoint từ chối `kind` khác bằng `400 VALIDATION_ERROR` field `kind`), danh sách/chi tiết tài khoản `/admin/users/v1/accounts*`.
6. `POST /auth/v1/forgot-password` + `/reset-password` (link trong email) **không đổi**; hai luồng RESET tồn tại song song. Luồng RESET bằng mã dùng `/password-challenges`.

## §6 — Rate limit

Dùng quy tắc `auth-ip` có sẵn: 10 yêu cầu/phút/IP cho mọi `/auth/v1/**`. Một lượt setup đầy đủ (login, verify, complete, login lại) là 4 yêu cầu; gửi lại mã tính thêm.

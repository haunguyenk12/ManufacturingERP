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

#### `PasswordResetTokenService`
- Sinh secure random token (UUID v4), lưu Redis với TTL 15 phút
- Token chỉ dùng được 1 lần (xóa ngay khi đã dùng)
- Nếu request reset mới → overwrite token cũ (không cho tích lũy)

## Bất biến nghiệp vụ (tách từ `CLAUDE.md` §10.6)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B33 | Account enumeration prevention: sai username và sai password trả **cùng** `INVALID_CREDENTIALS` | `AuthServiceTest.login_failsOnUserNotFound` |
| B34 | Brute-force: đạt ngưỡng fail → khoá, và **không** truy vấn user nữa (short-circuit) | `AuthServiceTest.login_blockedAfterMaxFailedAttempts` |

> **[`D1`, 2026-07-28] Nợ #7 đã trả.** `LoginRequest`, `RefreshRequest`, `LogoutRequest` (và
> `CreateUserRequest`/`UpdateUserRequest` ở module `user`) đều override `toString()` che secret —
> record không dùng được `@ToString.Exclude` nên phải viết tay. Hai điều **đừng** "tiện tay sửa":
> `tokenId` **cố ý hiện** (định danh để tra Redis, không phải credential — che nó làm log mất giá trị
> debug mà không tăng bảo mật), và mask **phân biệt `null` vs `***`** (thiếu password và ẩn password
> dẫn tới hai hướng điều tra khác nhau). Regression guard: `SensitiveRequestToStringTest`.
> Thêm field secret mới vào các record này ⇒ **phải** cập nhật `toString()` + test.

# FE_REQUEST_ACCOUNT_PASSWORD_FLOWS_2026-11-06

## §0 — Kết luận trong 3 dòng

- **Vấn đề:** BE QA hiện chưa công bố hợp đồng cho đăng nhập lần đầu bằng mã email và đổi mật khẩu bằng mật khẩu hiện tại.
- **Ảnh hưởng:** FE đã có màn hình/adapter nhưng phải giữ `NEXT_PUBLIC_ACCOUNT_API_ENABLED=false`; không thể nghiệm thu hai luồng tài khoản thật hoặc bật tính năng cho người dùng.
- **Mức ưu tiên:** **P1** — hai luồng tài khoản không dùng được trên môi trường tích hợp; đăng nhập cũ và các nghiệp vụ khác vẫn hoạt động.

## §1 — Phạm vi

**Trong phạm vi:** Admin tạo tài khoản với email và mật khẩu ban đầu; login lần đầu trả nhánh setup; gửi/xác minh mã email, đặt mật khẩu mới, đăng nhập lại; đổi mật khẩu bằng mật khẩu hiện tại khi đã đăng nhập; hộp thư QA và cấu hình gửi email phục vụ nghiệm thu.

**Ngoài phạm vi:** đăng ký công khai, social login, thay đổi luồng refresh/logout hiện có, quyền ERP nghiệp vụ, reset mật khẩu bằng email (đã có UI nhưng cần xác nhận contract riêng trước nghiệm thu), gửi email production hoặc công bố secret qua FE.

**Môi trường:** FE Next.js local `http://localhost:3000`, API mode, commit nền quan sát `29cd364` và thay đổi làm việc chưa commit. BE QA gần nhất được hướng dẫn là `http://10.60.243.54:8080`; cần BE xác nhận host/build hiện hành. FE gọi qua proxy cùng origin `/api/backend`; các path dưới đây là path BE sau `/api`. Account API flag đang tắt. **Đã kiểm chứng trong FE:** UI/adapter và test mock offline cho setup, mã sai và lỗi password policy. **Theo báo cáo live QA trước:** OpenAPI hiện hành chưa có account challenge/current-password-change. Lượt lập tài liệu này không gọi lại BE và không khẳng định trạng thái deploy mới.

## §2 — Bằng chứng và điều kiện tái hiện

Đây là **yêu cầu triển khai contract**, không phải defect từ một request bị BE trả sai. Không có request/response thật, `X-Trace-Id`, record ID hoặc mã email thật từ cùng một lượt chạy để báo lỗi; **không có**. FE chưa bật flag và chưa gửi các command đề xuất đến BE QA. Không được coi các ví dụ ở §3 là response thực tế.

Đã kiểm chứng ở FE: `LoginForm` nhận kết quả `PASSWORD_SETUP` và không tạo ERP session; `PasswordFlow` xử lý SETUP/RESET/CHANGE, giữ input khi lỗi, giới hạn gửi lại theo `resendAt`; adapter đã khai báo các path ở §3. Khi flag tắt trên BE hiện tại, FE báo tính năng mật khẩu chưa sẵn sàng thay vì gọi endpoint chưa hỗ trợ.

Mong đợi: BE cung cấp hợp đồng và build QA có thể nghiệm thu bằng một tài khoản thử mới và mailbox QA.

Thực tế: FE chưa có endpoint/live mailbox đã được xác nhận để chạy ca này; trạng thái nghiệm thu là **BLOCKED**.

## §3 — Đầu ra mong đợi, kiểm được

**Đề xuất contract FE đang dùng; BE cần triển khai hoặc trả mapping tương đương để FE sửa adapter trước khi bật flag.** Mọi response dùng envelope `{code,message,result,errors?}`. ID là UUID string, thời gian ISO-8601 UTC. Không trả mật khẩu, hash, mã email hoặc setup token trong account list/detail/audit.

| # | Điều kiện đúng/sai để nghiệm thu | Endpoint đề xuất | Chặn FE live? |
| --- | --- | --- | --- |
| 1 | Admin có quyền tạo tài khoản bằng email và mật khẩu ban đầu; account ở `PENDING_SETUP`, không có ERP session trước setup. Email trùng bị từ chối. | `POST /admin/users/v1/accounts/commands` với `{kind:"CREATE",username,email,password}` và `Idempotency-Key` | Có |
| 2 | Login bằng mật khẩu ban đầu đúng trả `result:{kind:"PASSWORD_SETUP",email,challenge:{id,expiresAt,resendAt}}`, không trả access/refresh token; mật khẩu sai không gửi mã. | `POST /auth/v1/login` với `{username,password}` | Có |
| 3 | Mã chỉ gửi tới email đã đăng ký. Gửi lại thay mã cũ và trả deadline mới; không tiết lộ account ở luồng RESET công khai. | `POST /auth/v1/password-challenges` với `{identifier,purpose:"SETUP"|"RESET",previousId?}` và `Idempotency-Key` | Có |
| 4 | Mã sáu chữ số hợp lệ, chưa hết hạn và chưa dùng được đổi lấy token hạn chế chỉ dùng đặt mật khẩu; mã sai/hết hạn/đã dùng không cấp token. | `POST /auth/v1/password-challenges/verify` với `{challengeId,code}` và `Idempotency-Key` → `result:{setupToken}` | Có |
| 5 | Complete kiểm password policy, yêu cầu mật khẩu mới khác mật khẩu ban đầu, tiêu thụ token một lần, chuyển account sang ACTIVE, thu hồi phiên cũ; user phải login lại. | `POST /auth/v1/password-challenges/complete` với `{setupToken,password}` và `Idempotency-Key` → `result:null` hoặc result omitted | Có |
| 6 | User đã login chỉ đổi được mật khẩu khi currentPassword đúng; thành công thu hồi phiên cũ và bắt login lại. | `POST /auth/v1/password/change` với Bearer, `{currentPassword,password}` và `Idempotency-Key` → `result:null` hoặc result omitted | Có |
| 7 | Hộp thư QA nhận được mã trong thời hạn challenge, người kiểm có quyền đọc, có cách đối chiếu challenge/user/time mà không chia sẻ secret trong log. | Cấu hình mail QA của BE; không có endpoint FE riêng | Có |

Các endpoint POST trả một envelope đơn, không phân trang. `Idempotency-Key` áp dụng cho các command ghi để replay cùng intent không tạo nhiều challenge hoặc đổi mật khẩu nhiều lần. Login không cấp token ERP cho account `PENDING_SETUP`; mọi ERP API trực tiếp phải từ chối account này.

## §4 — Hợp đồng lỗi mong đợi

Các HTTP/code dưới đây là **đề xuất**, chưa phải kết quả BE. BE cần chốt code chính xác để FE map theo `code`, không dựa vào HTTP đơn lẻ. Error envelope giữ `message`, `errors` theo field khi phù hợp, không chứa secret.

| Tình huống | HTTP đề xuất | `code` đề xuất | FE xử lý |
| --- | --- | --- | --- |
| Login sai mật khẩu ban đầu | 401 | `INVALID_CREDENTIALS` | Giữ username, báo sai thông tin; không mở setup |
| Account chưa setup gọi ERP API | 403 | `PASSWORD_SETUP_REQUIRED` | Không mở ERP; đưa về login/setup |
| Mã sai | 400 | `CODE_INVALID` | Giữ mã đã nhập, báo lỗi cụ thể |
| Mã hết hạn hoặc đã dùng | 400/422 | `CODE_EXPIRED` / `CODE_USED` | Cho bắt đầu/gửi lại theo deadline |
| Quá số lần thử/gửi | 429 | `CODE_ATTEMPTS` hoặc mã rate-limit thống nhất | Hiện thời gian chờ từ `Retry-After` |
| Mật khẩu mới không đạt policy hoặc trùng cũ | 422 | `PASSWORD_POLICY` | Giữ form, hiển thị lỗi; không tiêu thụ token nếu chưa hoàn tất |
| Mật khẩu hiện tại sai | 401/422 | `CURRENT_PASSWORD_INVALID` | Giữ form, báo đúng trường |
| Token setup không hợp lệ/hết hạn | 401/422 | `SETUP_TOKEN_INVALID` / `SETUP_TOKEN_EXPIRED` | Yêu cầu xin mã mới |

## §5 — Chỉ cần BE xác nhận, không cần code cho các câu hỏi này

1. BE có giữ đúng các path/DTO ở §3 không? — FE nghiêng về **giữ** để dùng adapter sẵn có — **CHẶN** bật flag.
2. `username` của account mới có bằng email chuẩn hóa không? — FE nghiêng về **có**, giữ username cũ cho tài khoản cũ — **CHẶN** form tạo account mới.
3. Login `PENDING_SETUP` có tự tạo/gửi challenge sau khi xác minh mật khẩu ban đầu không? — FE nghiêng về **có**, để vào ngay bước nhập mã tại login — **CHẶN** setup UI live.
4. Challenge có mã đúng sáu chữ số, `expiresAt` và `resendAt` theo UTC không? — FE nghiêng về **có**, đúng validation/UI hiện tại — **CHẶN** kiểm live.
5. Mailbox QA nào và ai có quyền đọc mã? — FE nghiêng về hộp thư thử biệt lập có thể truy cập trong buổi QA — **CHẶN** nghiệm thu email thật. Hiện chưa được cung cấp.
6. BE sẽ cung cấp host/build/version và tài khoản thử mới nào để nghiệm thu? — FE nghiêng về môi trường QA riêng, account chưa setup và không dùng chung người khác — **CHẶN** nghiệm thu live.
7. Sau change/complete thành công, BE thu hồi toàn bộ phiên hay chỉ phiên hiện tại? — FE nghiêng về **toàn bộ phiên cũ** để không còn token hợp lệ — **CHẶN** kiểm bảo mật phiên.

## §6 — FE đã tự xử lý

- Đã có form setup tại `/login`, quên mật khẩu và đổi mật khẩu; kiểm hai mật khẩu khớp, giữ input khi lỗi, hiển thị thời gian hết hạn/gửi lại, và không tự login sau complete.
- Adapter đã gửi idempotency key cho challenge/verify/complete/change, dùng envelope và nhận `result:null` hoặc omitted cho complete/change.
- `NEXT_PUBLIC_ACCOUNT_API_ENABLED=false` trên BE hiện tại; không fallback sang mock khi API lỗi. Chỉ bật sau khi BE xác nhận contract và FE kiểm live.
- Mock và component tests chỉ chứng minh FE behavior, **không** chứng minh mã email thật, server policy hoặc session revocation.
- Sau phản hồi BE, FE sẽ map đúng mã lỗi/DTO đã chốt, bật flag trong môi trường QA riêng và chạy lại ca live. Mã lỗi ở §4 chưa được FE coi là contract đã có.

## §7 — Tham chiếu

- `BE_HANDOFF_GD_01_2026-10-09.md` — luồng nghiệp vụ đã bàn giao, có bản contract đề xuất cũ.
- `CAPSTONE3_GD1_BE_CONTRACT_DRAFT_2026-10-08.md` — DTO và mã lỗi tham khảo; tài liệu này chốt lại phần cần BE xác nhận theo trạng thái hiện tại.
- `OMNIPLANT_FE_MANUAL_QA_2026-11-06.md` — ca N06 và điều kiện nghiệm thu thủ công.

**Checklist giao BE:** §0–§7 đầy đủ; phạm vi và ngoài phạm vi rõ; đề xuất tách khỏi đã kiểm chứng; không trình bày ví dụ như API thật; câu hỏi chỉ xác nhận ở §5; mã lỗi có `code`; chưa có bằng chứng live được nói rõ; không chứa secret. Tài liệu được lưu cục bộ, **chưa gửi** ra ngoài.

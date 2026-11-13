# Hướng dẫn test luồng tài khoản và mật khẩu trên môi trường có hộp thư thật

**BE_GUIDE_ACCOUNT_PASSWORD_TEST_ENV_2026-11-07 · v2 · 07/11/2026**
Đi kèm `BE_RESPONSE_ACCOUNT_PASSWORD_FLOWS_2026-11-06.md` (hợp đồng API, mã lỗi). File này chỉ nói **kết nối vào đâu và test thế nào**.

## §0 — Tóm tắt

- BE đã dựng sẵn một môi trường test trên máy BE, FE kết nối qua **Radmin VPN** vào IP **`26.154.206.236`**:
  - **API:** `http://26.154.206.236:18080/api`
  - **Hộp thư (Mailpit):** `http://26.154.206.236:58025`
- Mailpit là một máy chủ thư giả: nhận thư như SMTP thật nhưng **không gửi ra ngoài**. Mã 6 số được đọc ở giao diện web hoặc API của nó.
- Phía FE chỉ cần trỏ API vào địa chỉ trên và bật `NEXT_PUBLIC_ACCOUNT_API_ENABLED=true`. Adapter giữ nguyên theo hợp đồng đã gửi.
- BE đã chạy hết luồng trên môi trường này ngày 07/11: 21/21 ca đạt, thư tới trong khoảng 0,5 giây.

## §1 — Địa chỉ kết nối (qua Radmin)

| Thành phần | Địa chỉ FE dùng | Ghi chú |
|---|---|---|
| **Backend API** | **`http://26.154.206.236:18080/api`** | Bản có luồng mã email (migration `V96`). Khác cổng `8080` của môi trường dev/demo thường dùng |
| **Hộp thư Mailpit (giao diện web)** | **`http://26.154.206.236:58025`** | Xem mọi thư backend đã gửi. Không có mật khẩu: ai trong mạng Radmin cũng đọc được mọi mã |
| **Mailpit API** (cho test tự động) | `http://26.154.206.236:58025/api/v1` | Xem §6 |
| **Tài khoản quản trị** | `admin` / `ApqAdmin!Local2026` | Chỉ tồn tại trong môi trường test này, không phải `qa_admin` của môi trường QA chung |

Môi trường này **riêng biệt hoàn toàn** với backend `:8080` (dev, công ty `D26-`/`DEMO-`/`QA-GD12`): DB, Redis và tài khoản khác nhau. Ở đây **không có** dữ liệu nghiệp vụ (công ty, nhà máy, vật tư...), chỉ dùng để test tài khoản và mật khẩu.

Kiểm tra kết nối nhanh từ máy FE:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://26.154.206.236:58025/                       # mong đợi 200
curl -s -X POST http://26.154.206.236:18080/api/auth/v1/login \
     -H "Content-Type: application/json" -d '{"username":"admin","password":"ApqAdmin!Local2026"}'   # mong đợi "code":"SUCCESS"
```

Không kết nối được thì kiểm tra trước: Radmin VPN của cả hai máy đang bật và cùng mạng, và báo BE xem backend còn chạy không (§9).

## §2 — Cấu hình phía FE

| | Giá trị |
|---|---|
| API base (đích của proxy `/api/backend`) | `http://26.154.206.236:18080/api` |
| Origin của FE | `http://localhost:3000` — đã được BE cho phép trong CORS. Chạy FE ở origin khác thì báo BE thêm vào |
| Cờ tính năng | `NEXT_PUBLIC_ACCOUNT_API_ENABLED=true` |

Đường dẫn thật đặt `v1` **sau** tiền tố tài nguyên: `/api/auth/v1/...`, `/api/admin/users/v1/...`.

## §3 — Kịch bản test chính (đăng nhập lần đầu)

Mỗi lượt dùng một **username + email mới** (vd `qa.user01@omniplant.vn`, `qa.user02@...`), vì một tài khoản chỉ đi qua bước setup được một lần. Email không cần có thật: mọi thư đều về Mailpit.

| # | Thao tác trên FE | API | Kỳ vọng |
|---|---|---|---|
| 1 | Đăng nhập `admin`, tạo tài khoản với email + mật khẩu ban đầu (vd `Init1al!Pass`) | `POST /admin/users/v1/accounts/commands` `{kind:"CREATE", username, email, password}` | `201`, `result.status = "PENDING_SETUP"`, không có field mật khẩu nào. Mailpit **không** có thư mới |
| 2 | Đăng xuất admin, đăng nhập bằng tài khoản mới + mật khẩu ban đầu | `POST /auth/v1/login` | `200`, `result.kind = "PASSWORD_SETUP"`, có `email` và `challenge{id, expiresAt, resendAt}`, **không có** `accessToken`. FE ở lại `/login` |
| 3 | Mở **`http://26.154.206.236:58025`**, mở thư mới nhất gửi tới email đó | — | Tiêu đề `Your OmniPlant account setup code`, người gửi `no-reply@omniplant.local`, nội dung có mã 6 số |
| 4 | Nhập mã, mật khẩu mới (vd `Str0ng!Passw0rd`) và xác nhận, bấm **Tạo mật khẩu mới** | `POST /auth/v1/password-challenges/verify` rồi `.../complete` | verify `200` trả `setupToken`; complete `200`, **không có** `result`. FE không tự đăng nhập |
| 5 | Đăng nhập bằng mật khẩu **mới** | `POST /auth/v1/login` rồi `GET /auth/v1/me` | Có token; `/me` trả `status = "ACTIVE"` |
| 6 | Thử đăng nhập lại bằng mật khẩu **ban đầu** | `POST /auth/v1/login` | `401 INVALID_CREDENTIALS` |

**Đổi mật khẩu** (sau bước 5): vào `/account/password`, nhập mật khẩu hiện tại và mật khẩu mới ⇒ `200`. Mọi phiên bị thu hồi, kể cả phiên đang dùng: lời gọi kế tiếp bằng token cũ nhận `401 TOKEN_REVOKED`, FE phải đưa về `/login`.

**Quên mật khẩu bằng mã** (tài khoản đã `ACTIVE`): `/forgot-password` ⇒ `POST /auth/v1/password-challenges` `{identifier, purpose:"RESET"}` ⇒ thư `Your OmniPlant password reset code` trong Mailpit ⇒ verify ⇒ complete như bước 4.

## §4 — Ca lỗi nên thử

| Cách gây ra | Kỳ vọng |
|---|---|
| Bước 1 với mật khẩu yếu (`weak`) | `422 PASSWORD_POLICY`, `errors[0].field = "password"` |
| Bước 1 lặp lại **y hệt** (cùng username, email, mật khẩu) | `201`, trả lại đúng tài khoản cũ (không phải lỗi) |
| Bước 1 với email đã dùng nhưng username khác | `409 EMAIL_ALREADY_EXISTS` |
| Bước 2 với mật khẩu sai | `401 INVALID_CREDENTIALS`, Mailpit **không** có thư |
| Bước 2 lặp lại trong vòng 30 giây | Cùng `challenge.id`, **không** có thư thứ hai |
| Bấm "gửi lại mã" trước `resendAt` | `429 RESEND_TOO_SOON`, header `Retry-After` (giây) |
| Gửi lại hoặc đăng nhập lại **sau** `resendAt` rồi nhập mã của thư **cũ** | `422 CODE_EXPIRED`; chỉ mã trong thư mới nhất dùng được |
| Nhập sai mã | `400 CODE_INVALID`. Sai 5 lần ⇒ `429 CODE_ATTEMPTS` + `Retry-After`; sau đó cả mã đúng cũng bị từ chối, phải xin mã mới |
| Đợi quá 10 phút rồi mới nhập mã | `422 CODE_EXPIRED` |
| Bước 4 với mật khẩu mới trùng mật khẩu ban đầu, hoặc yếu | `422 PASSWORD_POLICY`; token **chưa bị tiêu**, sửa mật khẩu rồi bấm lại được ngay |
| Bước 4 bấm lại sau khi đã thành công (cùng mật khẩu) | `200`, không đổi gì |
| Đổi mật khẩu với mật khẩu hiện tại sai | **`422`** `CURRENT_PASSWORD_INVALID` — không phải 401, FE **không** được kết thúc phiên |
| Quên mật khẩu với email không tồn tại | `200` giống hệt email thật, Mailpit **không** có thư |
| Quên mật khẩu với tài khoản còn `PENDING_SETUP` | `200`, **không** có thư: tài khoản chưa setup phải đi qua bước đăng nhập lần đầu |

Bảng mã lỗi đầy đủ: §2 của `BE_RESPONSE_ACCOUNT_PASSWORD_FLOWS_2026-11-06.md`.

## §5 — Policy mật khẩu (để chọn dữ liệu test)

Ít nhất 8 ký tự, có chữ hoa, chữ thường, chữ số và ký tự đặc biệt, tối đa 72 byte, và khác mật khẩu hiện tại. Áp cho mật khẩu ban đầu khi tạo tài khoản, mật khẩu mới khi complete và khi đổi mật khẩu.
Mẫu đạt: `Init1al!Pass`, `Str0ng!Passw0rd`, `An0ther!Pass9`.

## §6 — Lấy mã tự động (cho test e2e)

Mailpit có API JSON, nên test tự động không cần mở giao diện:

```bash
MAIL=http://26.154.206.236:58025/api/v1
curl -s -X DELETE "$MAIL/messages"     # xoá hết thư (ảnh hưởng mọi người đang test cùng lúc, xem §7.5)
curl -s "$MAIL/messages"               # danh sách, MỚI NHẤT ĐỨNG ĐẦU: messages[i].ID, .To[].Address, .Subject
curl -s "$MAIL/message/<ID>"           # chi tiết: field "Text" chứa mã 6 số
```

Thư được gửi **bất đồng bộ** sau khi API trả về, nên đừng đọc hộp thư ngay lập tức: lặp lại vài lần, mỗi lần cách khoảng 250 ms, tối đa vài giây. Lọc theo `To[].Address` để không lấy nhầm thư của lượt khác, rồi lấy mã bằng biểu thức `\b\d{6}\b` trên field `Text`.

## §7 — Những chỗ dễ vấp

1. **Sai mật khẩu 5 lần trên cùng một username ⇒ `423 ACCOUNT_LOCKED` trong 15 phút**, kể cả khi sau đó nhập đúng. Đây là chống dò mật khẩu, áp cho cả mật khẩu ban đầu và `admin`. Khi test ca "sai mật khẩu", dùng tài khoản riêng chứ đừng dùng `admin`; nếu `admin` bị khoá, báo BE mở khoá.
2. **Rate limit:** môi trường này cho 100 yêu cầu `/auth/v1/**` mỗi phút trên một IP (môi trường thật là 10). Mọi người qua Radmin từ cùng một máy FE dùng chung hạn mức đó. Vượt ⇒ `429 RATE_LIMIT_EXCEEDED`, và các bước sau trông như lỗi khác.
3. **Một tài khoản chỉ có một phiên.** Đăng nhập cùng tài khoản ở tab thứ hai sẽ đá phiên ở tab đầu. Dùng tài khoản khác nhau cho hai luồng chạy song song.
4. **Thời hạn:** mã sống 10 phút; gửi lại sau 30 giây; token setup sống 10 phút sau khi verify.
5. **Mailpit dùng chung.** Mọi người trong nhóm thấy cùng một hộp thư; `DELETE /messages` xoá thư của cả người khác. Lấy mã theo "thư mới nhất" mà không lọc theo người nhận rất dễ lấy nhầm mã.
6. **`Idempotency-Key`** được nhận nhưng không bắt buộc; gửi hay không đều cho cùng kết quả.

## §8 — Chuyển sang gửi thư thật

Không đổi code, chỉ đổi biến môi trường của backend. Ví dụ với Gmail (tài khoản cần bật xác minh 2 bước và tạo **App password**):

```
SPRING_MAIL_HOST=smtp.gmail.com  MAIL_PORT=587
MAIL_USERNAME=<gmail>  MAIL_PASSWORD=<app password>  MAIL_FROM=<cùng gmail>
MAIL_SMTP_AUTH=true  MAIL_SMTP_STARTTLS=true
```

Phía FE không đổi gì. Khác biệt duy nhất người dùng thấy: thư tới hộp thư thật (lần đầu có thể rơi vào mục Spam).

## §9 — Giới hạn của môi trường này

- Môi trường chạy **trên máy BE**: chỉ dùng được khi máy BE bật, Radmin của BE bật và BE đang chạy backend `:18080`. Không vào được thì báo BE trước khi kết luận là lỗi.
- Đây là môi trường để test luồng tài khoản, **chưa** phải môi trường QA tích hợp. Kết quả trên môi trường QA chung sẽ có khi build được triển khai ở đó.
- Chưa có: danh sách/chi tiết tài khoản cho màn hình quản trị, và các lệnh `UPDATE`/`STATUS`/`RESEND`/`GRANT`/`REVOKE`/`ROLE` trong `accounts/commands` (chỉ có `CREATE`).

## Phụ lục — Cách BE dựng lại môi trường

Dành cho BE, khi máy khởi động lại hoặc cần dựng mới. Cần Docker, Java 17+ và jar build từ repo (`mvn -DskipTests package`). Chạy trong Git Bash.

```bash
# Hạ tầng (lần đầu). Lần sau chỉ cần: docker start apq-pg apq-redis apq-mailpit
docker run -d --name apq-pg      -e POSTGRES_DB=apq -e POSTGRES_USER=apq -e POSTGRES_PASSWORD=apqpass -p 127.0.0.1:55432:5432 postgres:16-alpine
docker run -d --name apq-redis   -p 127.0.0.1:16379:6379 redis:7-alpine redis-server --requirepass apqredis
# Giao diện Mailpit mở trên IP Radmin để FE đọc thư; cổng SMTP chỉ mở nội bộ cho backend.
docker run -d --name apq-mailpit -p 127.0.0.1:51025:1025 -p 127.0.0.1:58025:8025 -p 26.154.206.236:58025:8025 axllent/mailpit:v1.21

# Backend: chạy từ thư mục KHÔNG chứa file .env của dev. Spring Boot nghe trên mọi card mạng, kể cả Radmin.
export SERVER_PORT=18080 APP_ENV=dev \
  DB_URL=jdbc:postgresql://127.0.0.1:55432/apq DB_USERNAME=apq DB_PASSWORD=apqpass \
  REDIS_HOST=127.0.0.1 REDIS_PORT=16379 REDIS_PASSWORD=apqredis \
  JWT_SECRET=apq-local-test-secret-apq-local-test-secret-0123456789 \
  JWT_ISSUER=manufacturing-erp JWT_AUDIENCE=manufacturing-erp-clients \
  SECURITY_TRUSTED_PROXIES=127.0.0.1 CORS_ALLOWED_ORIGINS=http://localhost:3000 \
  SPRING_MAIL_HOST=127.0.0.1 MAIL_PORT=51025 MAIL_FROM=no-reply@omniplant.local \
  RATE_LIMIT_AUTH_IP_LIMIT=100
# Chỉ lần chạy ĐẦU TIÊN trên DB mới (tạo admin); bỏ ở các lần sau, backend từ chối khởi động nếu admin đã có mà cờ vẫn bật:
#   export APP_BOOTSTRAP_ADMIN_ENABLED=true APP_BOOTSTRAP_ADMIN_USERNAME=admin APP_BOOTSTRAP_ADMIN_EMAIL=admin@apq.local APP_BOOTSTRAP_ADMIN_PASSWORD='ApqAdmin!Local2026'
java -jar /đường/dẫn/tới/manufacturing-0.0.1-SNAPSHOT.jar
```

- Sẵn sàng khi log có `Started ManufacturingErpApplication`.
- Thêm `MANAGEMENT_SERVER_PORT=18081` vào các biến ở trên nếu máy đang chạy backend dev: cổng quản trị mặc định `8081` sẽ bị chiếm và backend test không khởi động được.
- **Lỗi `bind: An attempt was made to access a socket in a way forbidden by its access permissions` khi bật container**: cổng nằm trong dải Windows giữ chỗ (Hyper-V/WinNAT). Dải này đổi sau mỗi lần khởi động lại Docker hoặc máy, nên một cổng chạy được hôm nay có thể hỏng hôm sau. Xem dải bị giữ bằng `netsh interface ipv4 show excludedportrange protocol=tcp`; chọn lại cổng ngoài các dải đó (cổng thấp như `16379` thường an toàn) rồi tạo lại container với `-p` mới và đổi biến tương ứng của backend.
- Tường lửa Windows hiện đang **tắt** cho profile Public (card Radmin thuộc profile này) nên FE vào được. Bật lại tường lửa thì phải mở cổng `18080` và `58025` cho mạng Radmin.
- Mở khoá một username bị `423` (vd `admin`) mà không chờ 15 phút: `docker exec apq-redis redis-cli -a apqredis --no-auth-warning DEL auth:failcount:admin` (thay `admin` bằng username bị khoá).
- Dọn hết: Ctrl+C backend, rồi `docker rm -f apq-pg apq-redis apq-mailpit`.

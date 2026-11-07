# BE trả lời: tài khoản và dữ liệu nền nghiệm thu live OmniPlant

**BE_RESPONSE_LIVE_QA_BOOTSTRAP_2026-11-04 · v1 · 04/11/2026**
Trả lời `FE_REQUEST_LIVE_QA_BOOTSTRAP_2026-11-04`. Wire: **KHÔNG có** thay đổi API (chỉ tạo dữ liệu).

## §0 — Kết luận

- **B01–B04 đã xong** trên `http://26.154.206.236:8080` (OpenAPI `v1.0.0`, 346 thao tác — khớp số FE đếm).
- Admin test là `qa_admin`, **đủ quyền tự dựng** mọi thứ còn lại ở §3 của yêu cầu: người dùng, vai trò, phạm vi, phân công, công ty, nhà máy, kho, vật tư.
- **Mật khẩu `qa_admin` BE gửi riêng**, không để trong file này.

## §1 — Bàn giao B01–B04

| Mã | Kết quả |
|---|---|
| B01 | `qa_admin` (userId `0a577def-72f4-46e0-995a-1870c08a9e8b`), email `qa_admin@omniplant.vn`, vai trò **ADMIN**, phạm vi **GLOBAL_ALL**, không hết hạn. `POST /api/auth/v1/login` trả HTTP 200 `SUCCESS`; `GET /api/auth/v1/me` trả `username=qa_admin`, 80 quyền hiệu lực, `scopes=[GLOBAL]`. |
| B02 | Danh mục quyền/vai trò đã có (ADMIN, MANAGER, OPERATOR). Đã gọi bằng token `qa_admin`, **cả 8 API đều HTTP 200**: `/api/v1/companies`, `/api/v1/companies/{id}/plants`, `/api/v1/plants/{id}`, `/api/users/v1`, `/api/access/v1/permissions`, `/roles`, `/scopes`, `/assignments`. |
| B03 | Admin test **được** tạo công ty, nhà máy, kho, tài khoản và phân quyền theo nhà máy (không bị giới hạn). BE đã tạo sẵn phần tổ chức bên dưới để FE khỏi mất bước. |
| B04 | Origin, phiên bản và mã/UUID thật ở bảng dưới. |

**Tổ chức đã tạo** (múi giờ `Asia/Ho_Chi_Minh`, trạng thái hoạt động):

| Loại | Mã | UUID |
|---|---|---|
| Công ty | `QA-GD12` | `06c38ca4-8f7e-40de-bcbc-4377e414a116` |
| Nhà máy | `QA-A` | `7580bd12-bcb5-4145-b4af-2e29c9a8c658` |
| Nhà máy | `QA-B` | `ce5816ab-d110-4ded-9c53-bdaef543c4fd` |
| Kho (A, `RAW_MATERIAL`) | `QA-A-RM` | `cfba1499-2b34-4e59-b8bc-9992b1ae571c` |
| Kho (A, `FINISHED_GOODS`) | `QA-A-FG` | `b46f0b93-27e4-464d-b216-d403e6bce89e` |
| Kho (A, `FINISHED_GOODS`, kho thứ hai để chuyển kho) | `QA-A-FG2` | `06c93bcd-2413-4079-bef7-6d56ce23adc8` |
| Kho (B, `RAW_MATERIAL`, kho đối chứng) | `QA-B-RM` | `49135193-cf83-4afa-8100-bbc3ff78be5e` |

Đơn vị tính (toàn cục, không thuộc công ty): `KG` (đã có sẵn) và `PCS` (vừa tạo).

**Phiên bản:** mốc triển khai commit `fba3288`, schema Flyway `V95`.

## §2 — BE cố ý CHƯA tạo

Để FE kiểm được chính luồng tạo/phân quyền, BE **không** tạo: `qa_manager_a`, `qa_operator_a`, `qa_commercial_a`, `qa_readonly_a`, `qa_operator_b`, phạm vi theo nhà máy, nhà cung cấp, khách hàng, vật tư, BOM, quy trình, lịch, trung tâm làm việc, giá chuẩn, đơn giá giờ. Tất cả do FE dựng bằng `qa_admin`. Cần BE dựng thay mục nào, báo lại.

## §3 — Trả lời §5

1. **Origin có được ghi không?** Có, nhưng **đây không phải môi trường tách riêng**: là máy dev của BE, cùng một cơ sở dữ liệu với hai công ty khác (`D26-OMNIPLANT` — dữ liệu mẫu chụp hình; `DEMO-OMNIPLANT` — trình diễn trực tiếp). FE chỉ ghi vào công ty `QA-GD12` và các tài khoản/phạm vi do FE tạo; **không sửa dữ liệu của hai công ty kia**. Muốn môi trường tách hẳn (ca đồng thời, duyệt, chốt chạy nhiều lượt) thì báo BE dựng instance/DB riêng.
2. **Tạo công ty và hai Plant?** Được. BE đã tạo sẵn `QA-GD12` / `QA-A` / `QA-B`; FE tạo thêm công ty/nhà máy khác vẫn được.
3. **Tạo tài khoản, phân quyền theo Plant?** Được. Lưu ý: tạo người dùng mới thì hệ thống tự gán sẵn vai trò mặc định `OPERATOR` (thấy ở `qa_admin`: `roles=[OPERATOR, ADMIN]`). Với tài khoản chỉ-đọc/hạn chế, FE cần kiểm xem vai trò mặc định này có làm lệch ma trận quyền không. Phạm vi `PLANT` chỉ nhận tài nguyên kiểu `PLANT` (kho nằm trong nhà máy được phủ tự động qua quan hệ cha–con).
4. **Thiết lập mật khẩu lần đầu bằng mã email?** **Chưa.** Hiện có `POST /api/auth/v1/forgot-password` và `/reset-password` (đặt lại mật khẩu), nhưng không có luồng "mã email lần đầu". Mật khẩu ban đầu do Admin đặt lúc `POST /api/users/v1`.
5. **Hộp thư test?** **Không có.** Dịch vụ email hiện chỉ ghi liên kết đặt lại mật khẩu vào log máy chủ, không gửi thư thật. Ca cần nhận email chưa chạy được; các ca nghiệp vụ không bị ảnh hưởng.

## §4 — Hợp đồng lỗi (đã gọi thật, qua `26.154.206.236:8080`)

| Ca | Kết quả đo |
|---|---|
| `POST /api/users/v1` không token | 401 `AUTHENTICATION_REQUIRED` |
| Token sai định dạng (`Bearer abc`) | 401 `TOKEN_MALFORMED` |
| Có phiên nhưng thiếu quyền (tài khoản `quanly.demo` của công ty khác gọi `GET /api/v1/plants/{QA-A}` và `GET /api/users/v1`) | 403 `PERMISSION_DENIED`, message `Insufficient permissions` |
| `POST /api/auth/v1/login` body `{}` | 400 `VALIDATION_ERROR`, `errors[]` có `username`, `password` |
| `POST /api/users/v1` body `{}` bằng `qa_admin` | 400 `VALIDATION_ERROR`, `errors[]` có `email`, `password`, `username` |

**Chưa đo:** 403 cho đúng ma trận vai trò của FE (`qa_manager_a` truy cập Plant B, `qa_operator_b` dùng ID của Plant A…) — chưa có các tài khoản đó, sẽ đo được khi FE tạo chúng. Dòng 403 ở trên chỉ chứng minh mã/message, không chứng minh ma trận QA.

## §5 — Lưu ý khi chạy

- **Một người dùng chỉ có một phiên:** đăng nhập `qa_admin` ở nơi thứ hai sẽ thu hồi phiên cũ của nó. Hai luồng FE chạy song song nên dùng hai tài khoản khác nhau.
- **Phân công vai trò có thể có `expiresAt`:** hết hạn là mất quyền. Phân công của `qa_admin` không đặt hạn.
- Sao chép `scripts/demo-capstone2/qa-bootstrap.sh` không cần thiết cho FE; chạy lại script là idempotent (không tạo trùng).

# Yêu cầu BE chuẩn bị tài khoản và dữ liệu nghiệm thu live OmniPlant

**FE_REQUEST_LIVE_QA_BOOTSTRAP_2026-11-04 · v1 · 04/11/2026**

## §0 — Kết luận

- **Vấn đề** BE đã truy cập được nhưng hiện chưa có tài khoản test để FE đăng nhập và dựng dữ liệu.
- **Ảnh hưởng** Chặn nghiệm thu có xác thực trên môi trường test, gồm quyền theo nhà máy và các luồng mua, kho, sản xuất, giao hàng, công nợ và đóng lệnh.
- **Mức ưu tiên P1** Các ca cần đăng nhập đang chờ. Kiểm thử trước đăng nhập và hồi quy FE đã chạy được.

## §1 — Phạm vi

- **BE cần thực hiện** Khởi tạo một Admin test, bảo đảm danh mục quyền và vai trò quản trị đã được khởi tạo, cấp phạm vi cho phép dựng dữ liệu test. Nếu môi trường giới hạn quyền tạo tổ chức hoặc tài khoản phụ, cung cấp phần nền tương ứng ở §3.
- **BE chỉ cần xác nhận** Phạm vi được phép ghi, quyền tạo hai nhà máy test, dữ liệu nền có sẵn và khả năng kiểm email. Các câu hỏi tách riêng ở §5.
- **FE sẽ thực hiện** Xác minh hồ sơ đăng nhập, tạo các vai trò và tài khoản phụ, dựng dữ liệu, chạy các ca thành công và ca lỗi trên dữ liệu test. FE dùng API hiện hành để tạo chứng từ và kiểm tra kết quả theo từng bước.
- **Trong phạm vi** Đăng nhập, người dùng, vai trò, phạm vi truy cập, công ty và nhà máy. Các API mốc gồm `POST /api/auth/v1/login`, `GET /api/auth/v1/me`, `POST /api/users/v1`, `POST /api/v1/companies`, `POST /api/v1/companies/{companyId}/plants` và nhóm `/api/access/v1`.
- **Ngoài phạm vi** Đổi hợp đồng API, phát triển đăng ký công khai, đổi luồng mã email, seed dữ liệu production, sửa chứng từ hiện có, và nghiệm thu kết quả khi chưa chạy FE có xác thực.
- **Môi trường đã dùng** BE `http://26.154.206.236:8080`, OpenAPI `v1.0.0`. FE local ở chế độ API, mốc kiểm thử phiên 339 ngày 04/11/2026. BE xác nhận trước khi dùng môi trường này để ghi dữ liệu test.

## §2 — Bằng chứng

**Đã kiểm chứng.** OpenAPI live có 346 thao tác. Hợp đồng mô tả `POST /api/users/v1` chỉ dành cho Admin. 145 API đọc nghiệp vụ đã thử đều trả HTTP 401 khi thiếu token. Yêu cầu tạo người dùng dưới đây xác nhận điều kiện cần có phiên trước khi FE tự tạo tài khoản.

- Môi trường BE `http://26.154.206.236:8080`. Kết quả được lưu khoảng **18:54 ICT ngày 04/11/2026**. Thời điểm này lấy theo mốc lưu log FE.
- ID bản ghi **không có**. Yêu cầu tạo bị chặn trước khi có tài khoản mới.
- Request thật, không có header `Authorization`.

```http
POST /api/users/v1 HTTP/1.1
Host: 26.154.206.236:8080
Content-Type: application/json

{}
```

```http
HTTP/1.1 401
X-Trace-Id: d19037664e6642a5

{"code":"AUTHENTICATION_REQUIRED","message":"Authentication is required to access this resource"}
```

**Mong đợi.** Yêu cầu tạo người dùng thiếu phiên bị từ chối với HTTP 401 và `AUTHENTICATION_REQUIRED`.  
**Thực tế.** BE trả đúng HTTP 401 và mã lỗi trên.

**Thông tin từ PO.** Hiện chưa có tài khoản hoặc dữ liệu dành cho lượt test này. FE chưa kiểm tra được tình trạng dữ liệu bên trong BE vì chưa có phiên.

**Đề xuất.** Các mã và số liệu ở §3 dùng để dựng bộ test mới. Chúng là giá trị đề xuất, chưa phải bản ghi hoặc kết quả live.

## §3 — Đầu ra mong đợi

### Gói tối thiểu BE chuẩn bị

| Mã | Yêu cầu kiểm được | Kiểm qua | Chặn nghiệm thu live |
|---|---|---|---|
| B01 | Một Admin test đã kích hoạt, đăng nhập được bằng luồng hiện hành. BE trao thông tin đăng nhập qua kênh riêng. | `POST /api/auth/v1/login` trả HTTP 200 và `SUCCESS`. `GET /api/auth/v1/me` trả đúng hồ sơ Admin. | Có |
| B02 | Danh mục quyền và vai trò quản trị đã được khởi tạo. Admin test có quyền hiệu lực để tạo người dùng, quản lý vai trò, phạm vi, phân công và tạo dữ liệu cần cho lượt QA trong vùng test. | Đối chiếu hồ sơ và quyền hiệu lực. Kiểm các API `/api/users/v1` và `/api/access/v1/{permissions,roles,scopes,assignments}` theo quyền tương ứng. | Có, hoặc dùng gói thay thế bên dưới |
| B03 | BE xác nhận một môi trường được phép ghi và chỉ rõ Admin có được tạo công ty, hai nhà máy và kho test hay không. Nếu quyền tạo tổ chức bị giới hạn, BE cấp một công ty test và hai Plant test đã kích hoạt, kèm mã/UUID và phạm vi được cấp. | `GET /api/v1/companies`, `GET /api/v1/companies/{companyId}/plants` và `GET /api/v1/plants/{plantId}` trả đúng các bản ghi được bàn giao. | Có |
| B04 | BE bàn giao origin, phiên bản hoặc mốc triển khai, tên đăng nhập test và phạm vi dữ liệu được ghi. Nếu đã tạo công ty/Plant/kho, bàn giao mã và UUID thật. | FE đối chiếu OpenAPI live, đăng nhập và kiểm đọc lại bản ghi. | Có |

Nếu BE cấp được Admin có quyền phù hợp trong môi trường test, **BE chỉ cần tạo tài khoản B01 và phần nền B02–B04**. FE tự dựng các mục phía dưới. Nếu môi trường giới hạn quyền, BE chỉ cần tạo trước các mục bị giới hạn. Mỗi mục bàn giao kèm mã và phạm vi quyền hiệu lực.

### Tài khoản theo vai trò để chạy test

Các tên đăng nhập sau là mã gợi ý. FE tạo và cấp phạm vi sau khi có Admin. BE tạo thay những tài khoản mà Admin test không được phép tạo hoặc phân quyền.

| Tài khoản | Phạm vi | Quyền và tình huống cần kiểm |
|---|---|---|
| `qa_admin` | Quản trị vùng test | Tạo người dùng, quyền, phạm vi, tổ chức và dữ liệu test. BE khởi tạo tài khoản này trước. |
| `qa_manager_a` | Plant A | Thực hiện các bước duyệt, QC, xem chi phí và chốt lệnh trong phạm vi được cấp. Kiểm truy cập trái Plant B. |
| `qa_operator_a` | Plant A | Nhập liệu vận hành, cấp và hoàn vật tư, báo sản lượng, lập phiếu nhập. Kiểm từ chối hành động duyệt và đọc chi phí khi thiếu quyền. |
| `qa_commercial_a` | Plant A | Lập đơn bán, giao hàng, hóa đơn và phiếu thu theo quyền được cấp. Kiểm riêng quyền xem tiền, ghi sổ và phân bổ. |
| `qa_readonly_a` | Plant A | Xem danh sách và chi tiết được cấp. Kiểm thao tác ghi bị từ chối và trường chi phí được ẩn. |
| `qa_operator_b` | Plant B | Có thể vận hành tại Plant B. Kiểm từ chối khi dùng liên kết hoặc ID chứng từ của Plant A. |

### Dữ liệu nền nếu BE phải tạo thay

| Nhóm | Dữ liệu tối thiểu cần bàn giao | FE dùng để kiểm |
|---|---|---|
| Tổ chức | Một công ty test, hai Plant test, múi giờ `Asia/Ho_Chi_Minh`. Mã gợi ý `QA-GD12`, `QA-A`, `QA-B`. | Cách ly dữ liệu, chuyển nhà máy, kiểm quyền theo ID. |
| Kho | Plant A có kho nguyên liệu và kho thành phẩm. Thêm kho thứ hai có thể chuyển hàng qua lại nếu hai kho trên không phù hợp. Plant B có ít nhất một kho đối chứng. Các kho thuộc đúng Plant và đang hoạt động. | Nhập, xuất, giữ hàng, chuyển kho, đối chiếu theo Plant. |
| Danh mục | Đơn vị KG và PCS, một nhà cung cấp và một khách hàng hoạt động trong công ty test. Vật tư test có nguyên liệu, bán thành phẩm và thành phẩm, gồm cả loại theo lô và không theo lô. | Nhập dữ liệu, mua, BOM nhiều cấp, QC, giao và hóa đơn. |
| Thiết kế sản xuất và chi phí | Một BOM một cấp, một BOM nhiều cấp, một quy trình, lịch và trung tâm làm việc hoạt động tại Plant A. Giá chuẩn và đơn giá giờ có thể khai báo cho dữ liệu test. | Hoạch định BUY/MAKE, chạy sản xuất, kiểm hao hụt, giá thành và quyền xem tiền. |

FE sẽ tạo **chứng từ** mua, nhập, QC, giữ kho, lệnh sản xuất, giao hàng, hóa đơn và phiếu thu qua các thao tác nghiệp vụ. Dữ liệu ban đầu cần cho các ca sau là đề xuất để FE dựng hoặc đối chiếu khi BE phải tạo thay.

| Bộ | Dữ liệu đề xuất và điểm kiểm |
|---|---|
| Giá thành chuẩn | Lệnh 10 PCS, định mức 2 KG/PCS, giá chuẩn 1.000 VND/KG, 60 phút công đoạn, đơn giá nhân công 6.000 và chi phí chung 3.000 VND/giờ. Kho thành phẩm bắt đầu trống. Mục tiêu 29.000 VND khi đúng dữ liệu chuẩn, đơn giá thành phẩm 2.900 VND/PCS. |
| Hao hụt và nhiều cấp | Bộ riêng có hao hụt 10% và BOM nhiều cấp. Mốc đơn giản 10 PCS × 2 KG × 1,1 = 22 KG. Thêm các số lẻ để kiểm MRP làm tròn lên 6 chữ số và WO làm tròn HALF_UP. |
| Kho và QC | Tạo lô khả dụng, lô chờ QC, lô bị loại và lô hết hạn bằng luồng phù hợp. Tạo thêm hàng không theo lô. Đối chiếu lượng khả dụng, giữ hàng và các yêu cầu bị từ chối. |
| Giao và công nợ | Đơn 10 PCS × 100.000 VND, giao 4 + 6, tổng hóa đơn 1.000.000 VND, thu 400.000 + 600.000 VND. Dùng dữ liệu riêng để kiểm chặn giao quá hạn mức, hóa đơn cũ và thu vượt dư nợ. |

## §4 — Hợp đồng lỗi mong đợi

| Ca | HTTP | Mã lỗi | Xử lý trên FE |
|---|---:|---|---|
| Tạo tài khoản hoặc đọc nghiệp vụ khi thiếu phiên | 401 | `AUTHENTICATION_REQUIRED` | Yêu cầu đăng nhập. |
| Gửi access token sai định dạng | 401 | `TOKEN_MALFORMED` | Yêu cầu đăng nhập lại khi phiên không còn hợp lệ. |
| Đăng nhập thiếu tên hoặc mật khẩu | 400 | `VALIDATION_ERROR` | Hiển thị lỗi tại trường và giữ phần đã nhập. |
| Có phiên nhưng thiếu quyền đối với API quản trị hoặc Plant | 403 | `PERMISSION_DENIED` | Hiện trạng thái không có quyền và giữ phiên đăng nhập. Mã này là mốc đối chiếu từ lần nghiệm thu RBAC trước, chờ chạy lại trên môi trường hiện tại. |

Envelope đang dùng có `code` và `message`, kèm `result` khi thành công và `errors` theo trường nếu có.

## §5 — Cần BE xác nhận

**Các mục dưới đây chỉ cần trả lời, không cần code.**

1. Origin hiện tại có phải môi trường được phép ghi dữ liệu test không? — FE nghiêng về môi trường test tách riêng để chạy các ca duyệt, chốt và đồng thời — **Chặn nghiệm thu có ghi**.
2. Admin test có được tạo công ty và hai Plant test không? — FE nghiêng về tự tạo để tái hiện setup; nếu quyền tổ chức bị giới hạn thì dùng hai Plant BE cấp — **Chặn ca cách ly Plant nếu chưa có dữ liệu thay thế**.
3. Admin test có được tạo tài khoản và phân quyền theo Plant trong vùng test không? — FE nghiêng về tự tạo theo ma trận — **Chặn nghiệm thu quyền nếu BE chưa cấp tài khoản thay thế**.
4. Bản BE hiện tại đã hỗ trợ luồng thiết lập mật khẩu lần đầu bằng mã email chưa? — FE dùng đăng nhập hiện hành để mở nghiệm thu nghiệp vụ — **Chỉ chặn ca mã email**.
5. BE có hộp thư test nhận được email xác minh hoặc đặt lại mật khẩu không? — FE nghiêng về hộp thư test riêng — **Chỉ chặn các ca cần nhận email**.

## §6 — FE đã tự xử lý

- FE API local đã chạy qua proxy cùng nguồn. Đường đọc OpenAPI hoạt động và các API bảo vệ trả 401 khi thiếu token.
- Trình duyệt chuyển về màn hình đăng nhập khi mở thẳng trang đóng lệnh mà chưa có phiên. Form trống khóa nút đăng nhập.
- Toàn bộ 860 ca hồi quy FE đã đạt. Nghiệm thu tài khoản, phạm vi và chứng từ trên BE sẽ chạy lại khi đủ gói B01–B04.

## §7 — Tham chiếu

- `BE_OPENAPI_2026-10-28.json` — hợp đồng endpoint và DTO được dùng khi tích hợp FE.
- `BE_REPORT_CF_SUPPLEMENT_COMPLETED_2026-11-02.md` — bằng chứng BE về các ca hao hụt, nhận hàng, QC và đóng lệnh đã kiểm.

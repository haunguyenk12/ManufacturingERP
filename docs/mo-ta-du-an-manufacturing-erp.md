# MÔ TẢ DỰ ÁN

# GIẢI PHÁP SỐ HÓA QUẢN LÝ KHO VÀ HOẠCH ĐỊNH LINH KIỆN SẢN XUẤT - MANUFACTURING ERP

## I. Bối cảnh và tầm nhìn chiến lược

Trong doanh nghiệp sản xuất, năng lực cạnh tranh không chỉ đến từ máy móc, nhân công hay quy mô nhà xưởng, mà còn đến từ khả năng kiểm soát vật tư và ra quyết định sản xuất dựa trên dữ liệu chính xác.

Một kế hoạch sản xuất có thể bị chậm chỉ vì thiếu một linh kiện nhỏ. Một đơn hàng có thể bị trễ vì tồn kho trên hệ thống không phản ánh đúng thực tế. Một bộ phận mua hàng có thể đặt dư vật tư vì không biết chính xác nhu cầu sản xuất trong các tuần tới. Những vấn đề này thường không nằm ở năng lực sản xuất, mà nằm ở việc dữ liệu kho, định mức sản phẩm và kế hoạch sản xuất chưa được kết nối thành một luồng quản trị thống nhất.

Manufacturing ERP được định hướng là nền tảng số hóa lõi cho doanh nghiệp sản xuất lắp ráp, tập trung vào ba câu hỏi quản trị quan trọng:

- Doanh nghiệp hiện đang có những vật tư, linh kiện nào trong kho?
- Với tồn kho hiện tại, có thể sản xuất tối đa bao nhiêu sản phẩm?
- Nếu muốn sản xuất một số lượng mục tiêu, đang thiếu linh kiện nào và thiếu bao nhiêu?

Tầm nhìn của dự án là biến dữ liệu tồn kho và cấu trúc sản phẩm thành năng lực ra quyết định sản xuất. Hệ thống không chỉ ghi nhận nhập - xuất kho, mà còn giúp quản lý hiểu được tồn kho đó có ý nghĩa gì đối với kế hoạch sản xuất thực tế.

Chuỗi giá trị cốt lõi của dự án:

**Inventory Accuracy -> BOM Visibility -> Shortage Detection -> Production Decision -> Business Efficiency**

## II. Mục tiêu dự án

Dự án Manufacturing ERP hướng đến các mục tiêu chính sau:

- Chuẩn hóa nghiệp vụ nhập kho, xuất kho và điều chỉnh tồn kho.
- Quản lý tồn kho theo vật tư, kho, lô hàng và trạng thái sử dụng.
- Xây dựng danh mục vật tư, linh kiện, bán thành phẩm và thành phẩm.
- Quản lý BOM đa cấp để mô tả cấu trúc linh kiện cần thiết cho từng sản phẩm.
- Tính toán số lượng sản phẩm có thể sản xuất từ tồn kho hiện tại.
- Tính toán danh sách linh kiện thiếu khi có mục tiêu sản xuất cụ thể.
- Hỗ trợ quản lý kho và quản lý sản xuất ra quyết định dựa trên dữ liệu.
- Tạo nền tảng mở rộng sang lệnh sản xuất, MRP, mua hàng, chất lượng và tính giá thành.

## III. Tổng quan giải pháp

Manufacturing ERP là hệ thống quản trị sản xuất dạng modular monolith, được phát triển trên nền tảng Spring Boot, PostgreSQL, Redis và Flyway. Giai đoạn hiện tại của dự án đã có lớp nền tảng gồm xác thực, phân quyền, quản lý người dùng, bảo mật API và audit log. Các module sản xuất sẽ được xây dựng tiếp trên nền này.

Trong phiên bản MVP, hệ thống tập trung vào quản lý kho và hoạch định linh kiện cho sản xuất lắp ráp. Người dùng chính là quản lý kho, quản lý sản xuất và nhân viên vận hành kho.

Giải pháp bao gồm các năng lực chính:

- Ghi nhận toàn bộ biến động kho bằng stock movement.
- Duy trì tồn kho hiện tại bằng stock balance.
- Quản lý vật tư và sản phẩm theo mã chuẩn.
- Quản lý BOM đa cấp, có tính hao hụt theo từng dòng BOM.
- Bung BOM để xác định tổng nhu cầu linh kiện.
- So sánh nhu cầu với tồn kho khả dụng.
- Trả về báo cáo khả năng sản xuất và báo cáo thiếu hụt.

Nguyên tắc thiết kế quan trọng:

- Stock movement là nguồn dữ liệu gốc, stock balance là dữ liệu tổng hợp.
- Không sửa lịch sử nhập - xuất kho; sai lệch được xử lý bằng nghiệp vụ điều chỉnh.
- BOM phải kiểm tra vòng lặp để tránh cấu trúc sản phẩm sai.
- Chỉ lô hàng ở trạng thái sử dụng được mới được đưa vào tính toán khả năng sản xuất.
- Thiết kế API và dữ liệu phải đủ rõ để mở rộng sang work order và MRP.

## IV. Cấu trúc hệ thống và module chức năng

### 4.1. Module 1 - Nền tảng hệ thống, xác thực và phân quyền

#### Mục tiêu

Đảm bảo hệ thống có nền bảo mật, tài khoản người dùng và audit log đủ tốt trước khi triển khai nghiệp vụ sản xuất.

#### Chức năng chính

- Đăng nhập, đăng xuất, refresh token.
- Xác thực API bằng JWT.
- Quản lý người dùng và vai trò.
- Phân quyền theo nhóm người dùng: ADMIN, MANAGER, OPERATOR.
- Rate limit để bảo vệ API.
- Audit log cho các thao tác quan trọng.

#### Giá trị mang lại

- Tạo nền tảng an toàn cho dữ liệu sản xuất.
- Cho phép kiểm soát ai được xem, ai được thao tác.
- Có lịch sử truy vết cho các thay đổi quan trọng.

### 4.2. Module 2 - Tổ chức, nhà máy và kho

#### Mục tiêu

Mô hình hóa cấu trúc vận hành của doanh nghiệp để mọi dữ liệu kho và sản xuất đều gắn với phạm vi quản lý rõ ràng.

#### Chức năng chính

- Quản lý công ty, nhà máy hoặc site sản xuất.
- Quản lý kho vật tư, kho bán thành phẩm, kho thành phẩm.
- Gắn tồn kho và giao dịch kho với từng warehouse.
- Chuẩn bị nền tảng cho phân quyền theo nhà máy hoặc kho.

#### Giá trị mang lại

- Dữ liệu không bị lẫn giữa nhiều kho hoặc nhiều địa điểm.
- Phù hợp với doanh nghiệp có nhiều xưởng, nhiều kho hoặc nhiều line sản xuất.
- Là nền để mở rộng sang chuyển kho và kế hoạch sản xuất theo từng nhà máy.

### 4.3. Module 3 - Danh mục vật tư và quản lý tồn kho

#### Mục tiêu

Chuẩn hóa dữ liệu vật tư và kiểm soát tồn kho thực tế phục vụ sản xuất.

#### Chức năng chính

- Quản lý item master: linh kiện, nguyên vật liệu, bán thành phẩm, thành phẩm.
- Quản lý đơn vị tính, mã vật tư, tên vật tư và trạng thái sử dụng.
- Nhập kho vật tư theo kho và lô.
- Xuất kho vật tư theo nghiệp vụ sản xuất hoặc điều chỉnh.
- Điều chỉnh tồn kho khi kiểm kê hoặc phát hiện sai lệch.
- Theo dõi tồn kho theo vật tư, kho, lô và trạng thái lô.
- Ghi nhận lý do nhập, xuất, điều chỉnh.

#### Giá trị mang lại

- Biết chính xác số lượng vật tư có thể sử dụng.
- Giảm sai lệch giữa dữ liệu hệ thống và thực tế kho.
- Có lịch sử đầy đủ cho mọi biến động tồn kho.
- Là dữ liệu đầu vào bắt buộc cho ước lượng sản xuất.

### 4.4. Module 4 - Quản lý BOM đa cấp

#### Mục tiêu

Mô tả chính xác một sản phẩm được cấu thành từ những linh kiện và bán thành phẩm nào.

#### Chức năng chính

- Tạo BOM cho thành phẩm hoặc bán thành phẩm.
- Khai báo danh sách linh kiện con trong từng BOM.
- Khai báo số lượng linh kiện cần cho một đơn vị sản phẩm.
- Khai báo tỷ lệ hao hụt theo từng dòng BOM.
- Hỗ trợ BOM đa cấp cho mô hình có bán thành phẩm.
- Kiểm tra vòng lặp BOM, ví dụ A chứa B nhưng B lại chứa A.
- Quản lý trạng thái BOM: draft, active, inactive.
- Chuẩn bị khả năng mở rộng sang phiên bản BOM.

#### Giá trị mang lại

- Quản lý nhìn được cấu trúc sản phẩm một cách rõ ràng.
- Giảm phụ thuộc vào file Excel rời rạc hoặc kinh nghiệm cá nhân.
- Tạo cơ sở để tính nhu cầu linh kiện chính xác hơn.
- Là nền tảng để mở rộng sang lệnh sản xuất và tính giá thành.

### 4.5. Module 5 - Ước lượng khả năng sản xuất và thiếu hụt linh kiện

#### Mục tiêu

Chuyển dữ liệu kho và BOM thành thông tin ra quyết định cho quản lý sản xuất.

#### Chức năng chính

- Tính số lượng sản phẩm tối đa có thể sản xuất từ tồn kho hiện tại.
- Nhập số lượng sản phẩm mục tiêu và tính danh sách linh kiện thiếu.
- Bung BOM đa cấp để xác định nhu cầu linh kiện cuối cùng.
- Tính hao hụt theo tỷ lệ khai báo trên BOM.
- So sánh nhu cầu với tồn kho khả dụng.
- Hiển thị số lượng cần, số lượng đang có, số lượng thiếu.
- Loại trừ lô hàng đang bị hold, rejected hoặc không được phép sử dụng.

#### Công thức cơ bản

Nhu cầu linh kiện:

```text
required_quantity = target_product_quantity * bom_quantity * (1 + scrap_rate)
```

Khả năng sản xuất tối đa:

```text
max_buildable_quantity = min(available_component_quantity / required_component_quantity_per_product)
```

#### Giá trị mang lại

- Quản lý biết ngay có thể nhận hoặc thực hiện kế hoạch sản xuất nào.
- Phát hiện sớm vật tư thiếu trước khi ra lệnh sản xuất.
- Giảm tình trạng sản xuất dở dang do thiếu linh kiện giữa chừng.
- Hỗ trợ bộ phận mua hàng chuẩn bị vật tư đúng nhu cầu.

### 4.6. Module 6 - Báo cáo tồn kho và cảnh báo quản trị

#### Mục tiêu

Cung cấp góc nhìn quản trị để theo dõi sức khỏe tồn kho và rủi ro thiếu vật tư.

#### Chức năng chính

- Báo cáo tồn kho hiện tại theo vật tư, kho và lô.
- Báo cáo lịch sử nhập - xuất - điều chỉnh.
- Báo cáo linh kiện thiếu theo sản phẩm hoặc kế hoạch sản xuất.
- Cảnh báo vật tư dưới mức tồn kho an toàn.
- Cảnh báo vật tư sắp cần đặt hàng lại.
- Dashboard cho quản lý kho và quản lý sản xuất.

#### Giá trị mang lại

- Dữ liệu dễ đọc cho cấp quản lý.
- Giúp phát hiện rủi ro thiếu hàng trước khi ảnh hưởng sản xuất.
- Tạo cơ sở cho họp kế hoạch sản xuất và mua hàng.

### 4.7. Module 7 - Mở rộng sản xuất, MRP và mua hàng

#### Mục tiêu

Chuẩn bị lộ trình để hệ thống đi từ quản lý kho và BOM sang quản trị sản xuất đầy đủ.

#### Chức năng mở rộng

- Tạo và quản lý work order.
- Xuất vật tư theo lệnh sản xuất.
- Nhập kho thành phẩm sau sản xuất.
- MRP để tính nhu cầu vật tư theo nhiều kế hoạch sản xuất.
- Đề xuất mua hàng dựa trên thiếu hụt, tồn kho an toàn và lead time.
- Quản lý chất lượng theo lô.
- Tính giá thành sản xuất theo BOM, vật tư và chi phí công đoạn.

#### Giá trị mang lại

- Kết nối kho, sản xuất, mua hàng và quản trị chi phí.
- Tạo nền tảng ERP sản xuất hoàn chỉnh hơn sau MVP.
- Giảm phụ thuộc vào quy trình thủ công và file Excel.

## V. Luồng nghiệp vụ tổng thể

### 5.1. Luồng nhập kho

Nhân viên kho tiếp nhận vật tư, chọn mã vật tư, kho, lô hàng, số lượng và lý do nhập. Hệ thống tạo stock movement loại nhập kho và cập nhật stock balance tương ứng.

### 5.2. Luồng xuất kho

Nhân viên kho xuất vật tư theo yêu cầu sản xuất hoặc lý do nghiệp vụ khác. Hệ thống kiểm tra tồn kho khả dụng, tạo stock movement loại xuất kho và giảm stock balance.

### 5.3. Luồng điều chỉnh tồn kho

Khi kiểm kê phát hiện sai lệch, người có quyền thực hiện điều chỉnh tồn kho. Hệ thống không sửa lịch sử cũ mà tạo giao dịch điều chỉnh để đảm bảo truy vết.

### 5.4. Luồng khai báo BOM

Quản lý sản xuất khai báo thành phẩm, các linh kiện con, số lượng định mức và tỷ lệ hao hụt. Với bán thành phẩm, hệ thống tiếp tục cho phép khai báo BOM con.

### 5.5. Luồng ước lượng sản xuất

Quản lý chọn sản phẩm cần sản xuất. Hệ thống bung BOM, đọc tồn kho khả dụng và trả về hai kết quả:

- Số lượng tối đa có thể sản xuất từ tồn kho hiện tại.
- Nếu nhập số lượng mục tiêu, danh sách linh kiện thiếu và số lượng thiếu.

### 5.6. Luồng ra quyết định

Từ báo cáo thiếu hụt, quản lý có thể quyết định:

- Điều chỉnh kế hoạch sản xuất.
- Ưu tiên sản phẩm khác phù hợp tồn kho hiện tại.
- Yêu cầu bộ phận mua hàng nhập thêm linh kiện.
- Chuẩn bị mở rộng sang tạo đề xuất mua hàng tự động ở giai đoạn sau.

## VI. Ví dụ nghiệp vụ minh họa

Doanh nghiệp sản xuất sản phẩm `FG-100 - Bộ điều khiển A`.

BOM của `FG-100`:

- `PCB-01`: 1 cái, hao hụt 2%.
- `CASE-01`: 1 cái, hao hụt 1%.
- `MODULE-B`: 2 cái, hao hụt 3%.

BOM của bán thành phẩm `MODULE-B`:

- `IC-01`: 1 cái, hao hụt 2%.
- `RES-10K`: 4 cái, hao hụt 1%.

Khi quản lý nhập mục tiêu sản xuất 100 sản phẩm `FG-100`, hệ thống sẽ:

- Bung BOM đa cấp từ `FG-100` xuống `MODULE-B`, `IC-01`, `RES-10K`.
- Tính tổng nhu cầu từng linh kiện sau hao hụt.
- So sánh với tồn kho khả dụng theo kho và lô.
- Trả về vật tư đủ, vật tư thiếu và số lượng thiếu.

Kết quả giúp quản lý biết kế hoạch 100 sản phẩm có khả thi hay không trước khi ra lệnh sản xuất.

## VII. Kiến trúc hệ thống và công nghệ

| Thành phần | Định hướng kỹ thuật |
|---|---|
| Backend | Java 17, Spring Boot 3.x |
| Kiến trúc | Modular monolith |
| Database | PostgreSQL |
| Cache / session / rate limit | Redis |
| Migration | Flyway |
| Security | Spring Security, JWT, RBAC |
| API document | OpenAPI / Swagger UI |
| Audit | Audit log bất đồng bộ, mở rộng sang field-level audit |
| Container | Docker, Docker Compose |
| Testing | JUnit 5, Mockito, MockMvc, Testcontainers |

Định hướng kiến trúc:

- Mỗi module có controller, service, domain, repository, dto và mapper riêng.
- Module không gọi trực tiếp repository của module khác.
- Giao tiếp liên module đi qua service hoặc event.
- Các nghiệp vụ nặng như MRP sẽ chạy theo mô hình job bất đồng bộ.
- API dùng response envelope thống nhất để dev frontend và backend dễ tích hợp.

## VIII. Bảo mật và phân quyền

Hệ thống cần bảo vệ dữ liệu kho và sản xuất vì đây là dữ liệu vận hành quan trọng của doanh nghiệp.

Các nguyên tắc bảo mật:

- Người dùng phải đăng nhập trước khi thao tác nghiệp vụ.
- Access token có thời hạn ngắn, refresh token được quản lý qua Redis.
- Phân quyền theo vai trò: admin, quản lý, nhân viên vận hành.
- Các thao tác quan trọng phải ghi audit log.
- API có rate limit để hạn chế tấn công hoặc lạm dụng.
- Dữ liệu lỗi trả về theo chuẩn thống nhất, tránh lộ thông tin nội bộ.

Phân quyền tham khảo:

| Vai trò | Quyền chính |
|---|---|
| ADMIN | Quản trị hệ thống, người dùng, cấu hình nền tảng |
| MANAGER | Quản lý vật tư, BOM, xem báo cáo, chạy ước lượng sản xuất |
| OPERATOR | Thực hiện nhập kho, xuất kho, điều chỉnh theo quyền được cấp |

## IX. Lộ trình triển khai đề xuất

| Giai đoạn | Nội dung chính | Kết quả mong đợi |
|---|---|---|
| Phase 1 | Organization, warehouse, item master, inventory movement, stock balance | Có thể quản lý nhập - xuất - điều chỉnh và xem tồn kho |
| Phase 2 | BOM đa cấp, hao hụt, kiểm tra vòng lặp BOM | Có thể mô tả cấu trúc sản phẩm và định mức linh kiện |
| Phase 3 | Ước lượng sản xuất, báo cáo thiếu hụt | Trả lời được sản xuất được bao nhiêu và thiếu gì |
| Phase 4 | Dashboard tồn kho, cảnh báo tồn kho an toàn, reorder point | Quản lý nhìn được rủi ro thiếu vật tư sớm hơn |
| Phase 5 | Work order, MRP, đề xuất mua hàng, chất lượng, costing | Mở rộng thành ERP sản xuất hoàn chỉnh hơn |

## X. Yếu tố nghiệp vụ nên bổ sung

Bên cạnh hai ý tưởng ban đầu là quản lý xuất nhập kho và tính ước lượng linh kiện, dự án nên bổ sung các yếu tố nghiệp vụ sau để phù hợp thực tế doanh nghiệp sản xuất:

### 10.1. Tồn kho an toàn

Mỗi vật tư nên có mức tồn kho an toàn. Khi tồn kho khả dụng thấp hơn ngưỡng này, hệ thống cảnh báo để quản lý chủ động mua thêm trước khi thiếu hàng.

### 10.2. Lead time mua hàng

Mỗi vật tư hoặc nhà cung cấp nên có thời gian mua hàng dự kiến. Khi thiếu linh kiện, hệ thống không chỉ báo thiếu bao nhiêu mà còn có thể ước lượng khi nào sản xuất được nếu đặt mua ngay.

### 10.3. Trạng thái lô hàng

Không phải mọi lô hàng trong kho đều được phép sử dụng. Một lô có thể đang chờ kiểm tra chất lượng, bị giữ lại hoặc bị loại. Vì vậy tồn kho khả dụng phải loại trừ các lô không đạt trạng thái sử dụng.

### 10.4. Phiên bản BOM

Trong thực tế, cùng một sản phẩm có thể thay đổi linh kiện hoặc định mức theo thời gian. Quản lý phiên bản BOM giúp hệ thống biết kế hoạch sản xuất đang dùng công thức nào.

### 10.5. Truy xuất lịch sử

Khi tồn kho sai lệch hoặc thiếu vật tư, quản lý cần biết lịch sử nhập, xuất, điều chỉnh, người thao tác và thời điểm thao tác. Audit log và stock movement là cơ sở để truy xuất trách nhiệm.

## XI. Tác động chiến lược

| Yếu tố | Kết quả trực tiếp | Tác động cuối cùng |
|---|---|---|
| Time | Giảm thời gian kiểm tra vật tư trước sản xuất | Ra quyết định kế hoạch nhanh hơn |
| Efficiency | Giảm sản xuất dở dang do thiếu linh kiện | Tăng hiệu quả vận hành |
| Cost | Giảm đặt dư, giảm tồn kho chết, giảm sai lệch kho | Kiểm soát chi phí tốt hơn |
| Competitiveness | Phản hồi đơn hàng và kế hoạch sản xuất chính xác hơn | Nâng cao năng lực giao hàng |
| Revenue | Sản xuất ổn định hơn, ít gián đoạn hơn | Hỗ trợ tăng trưởng doanh thu bền vững |

## XII. Tiêu chí thành công của MVP

MVP được xem là đạt yêu cầu khi hệ thống có thể:

- Tạo và quản lý danh mục vật tư, thành phẩm, bán thành phẩm.
- Ghi nhận nhập kho, xuất kho và điều chỉnh tồn kho.
- Xem tồn kho hiện tại theo vật tư, kho và lô.
- Tạo BOM đa cấp cho sản phẩm.
- Tính đúng nhu cầu linh kiện có xét hao hụt.
- Tính được số lượng sản phẩm tối đa có thể sản xuất.
- Tính được danh sách linh kiện thiếu cho số lượng sản xuất mục tiêu.
- Không cho phép BOM vòng lặp.
- Không dùng lô hàng không khả dụng trong phép tính sản xuất.
- Có audit log cho các thao tác quan trọng.

## XIII. Thông điệp chốt

Manufacturing ERP không chỉ là phần mềm ghi nhận nhập kho và xuất kho. Đây là nền tảng giúp doanh nghiệp sản xuất nhìn thấy mối liên hệ giữa tồn kho, định mức linh kiện và khả năng thực hiện kế hoạch sản xuất.

Khi dữ liệu kho được chuẩn hóa, BOM được quản lý rõ ràng và thiếu hụt linh kiện được phát hiện sớm, doanh nghiệp có thể ra quyết định nhanh hơn, giảm gián đoạn sản xuất và từng bước xây dựng hệ thống ERP sản xuất hoàn chỉnh.

# Giải Thích Report.pdf — OmniPlant Manufacturing ERP (Capstone 2)

> Tài liệu này giải thích lại bằng tiếng Việt nội dung của `Report.pdf` (báo cáo Capstone 2 đã nộp,
> 72 trang, nhóm 3 người: Trần Quốc An, Nguyễn Phúc Hậu, Đặng Cao Cương, GVHD Thầy Hà Minh Ngọc).
> Đi theo đúng cấu trúc 5 chương của báo cáo, mỗi phần vừa dịch ý chính vừa giải thích **vì sao** báo
> cáo viết như vậy — để đọc xong hiểu được logic của toàn bộ báo cáo, không chỉ nhớ chữ.

---

## Mục lục

- [Tóm tắt (Abstract)](#tóm-tắt-abstract)
- [Chương 1 — Giới thiệu](#chương-1--giới-thiệu)
- [Chương 2 — Công trình liên quan](#chương-2--công-trình-liên-quan)
- [Chương 3 — Kiến trúc, phân tích, thiết kế và triển khai](#chương-3--kiến-trúc-phân-tích-thiết-kế-và-triển-khai)
- [Chương 4 — Kết quả và thảo luận](#chương-4--kết-quả-và-thảo-luận)
- [Chương 5 — Kết luận và hướng phát triển](#chương-5--kết-luận-và-hướng-phát-triển)
- [Phụ lục của báo cáo](#phụ-lục-của-báo-cáo)
- [Đọc nhanh: 10 điều cần nhớ nếu chỉ có 5 phút](#đọc-nhanh-10-điều-cần-nhớ-nếu-chỉ-có-5-phút)

---

## Tóm tắt (Abstract)

**Report nói gì:** OmniPlant là hệ ERP sản xuất trên nền web, nhắm tới doanh nghiệp sản xuất lắp ráp
quy mô vừa và nhỏ. Capstone 2 biến nền tảng phân tích + prototype của Capstone 1 thành **một vòng lặp
sản xuất chạy được trên API thật**, nối liền nhu cầu khách hàng, hoạch định vật tư, thực thi sản
xuất, phán quyết chất lượng, tồn kho và truy xuất nguồn gốc.

Luồng đã triển khai: bắt đầu từ một **Sales Order đã xác nhận** → tạo một **Planning Run bất biến**
(Material Requirements Planning) → nổ **BOM đa cấp đang active** → trừ tồn kho hợp lệ và tồn an toàn
→ sinh đề xuất **MAKE** (tự sản xuất) và **BUY** (mua ngoài, hoãn xử lý) → chuyển đề xuất đã duyệt
thành **Work Order** → giữ chỗ và xuất vật tư → ghi nhận kết quả sản xuất dở dang (WIP) thủ công →
duyệt phiếu nhập kho sản xuất → giải phóng thành phẩm đã chấp nhận qua cổng chất lượng **HOLD →
AVAILABLE**.

**Giải thích:** Đoạn tóm tắt này chính là "một câu" mà cả báo cáo xoay quanh — nếu chỉ nhớ một đoạn
duy nhất của Report thì nên nhớ đúng chuỗi 8 bước này (Sales Order → Planning Run → BOM explosion →
netting → suggestion → Work Order → reservation/issue → WIP → receipt → QC). Đây cũng chính là "vòng
sản xuất xương sống" được dùng lại xuyên suốt các phần sau của báo cáo.

**Công nghệ:** Next.js + TypeScript (frontend) kết hợp Spring Boot modular-monolith (backend),
PostgreSQL, Redis, JWT, và RBAC động (dynamic role-based access control).

**Trọng tâm của Capstone 2** là tính đúng đắn vận hành (operational correctness): snapshot bất biến
của BOM/Routing, phạm vi theo Plant/Warehouse, phả hệ lot/serial, loại trừ hàng HOLD/REJECTED khỏi
hoạch định, lệnh an toàn khi retry (idempotent), yêu cầu xác nhận cho hành động nhạy cảm, và giao
diện song ngữ cho desktop/tablet.

**Những gì cố ý KHÔNG làm trong Capstone 2** (ghi rõ ngay từ Abstract, không phải "quên"): Purchasing
đầy đủ, giao hàng và hoá đơn, costing chi tiết, tích hợp OEE/MES/PLC — tất cả hoãn sang Capstone 3.
Tính năng "Standalone Production Estimation" (một bộ tính thử độc lập, kế thừa từ Capstone 1) **không**
được đưa vào phạm vi MVP của Capstone 2, vì Planning Run đã đảm nhiệm đúng vai trò MRP nghiệp vụ chính
thức rồi.

---

## Chương 1 — Giới thiệu

### 1.1. Bối cảnh dự án

Manufacturing ERP quản lý **toàn bộ vòng đời sản xuất** — từ nguyên liệu thô tới thành phẩm — khác với
ERP thương mại (mua bán, phân phối) chỉ quan tâm tới luồng hàng hoá mua vào bán ra. Capstone 1 đã dựng
xong khái niệm OmniPlant, phân tích nghiệp vụ, hướng thiết kế database, mô hình vai trò, và giao diện
kiểu doanh nghiệp. **Capstone 2 đi xa hơn một prototype**: triển khai "xương sống sản xuất" như một
luồng ERP tích hợp thật. Nhu cầu khách hàng được ghi nhận thành Sales Order, dịch thành Planning Run
bất biến, chuyển thành Work Order có thể truy vết, và thực thi qua các giao dịch tồn kho/chất lượng
có kiểm soát.

**Một điểm rất quan trọng được nhấn mạnh ngay từ đầu:** hệ thống **cố ý tránh giả vờ có hành vi MES**
(Manufacturing Execution System) — sản lượng thực tế, số lượng tốt, phế phẩm, hàng làm lại, và thời
gian đều được **người dùng có thẩm quyền nhập tay**, không có cảm biến/máy nào tự động báo cáo. Đây
không phải thiếu sót mà là ranh giới trung thực của phạm vi dự án.

Giao diện được thiết kế cho desktop và tablet, dùng bởi admin, quản lý sản xuất, nhân viên kho, và
công nhân xưởng. Phong cách hình ảnh lấy cảm hứng từ **ERPNext**: danh sách compact, bộ lọc rõ ràng,
form ổn định, badge trạng thái, hộp thoại xác nhận — ưu tiên hơn hẳn so với layout mang tính quảng
cáo/tiêu dùng.

### 1.2. Phát biểu bài toán

Report liệt kê **5 khoảng trống vận hành** thường gặp ở doanh nghiệp sản xuất nhỏ:

1. **Nhu cầu và sản xuất rời nhau** — đơn hàng khách đã chốt nhưng không nhất quán trở thành yêu cầu
   vật tư và chứng từ sản xuất thực thi được.
2. **Tồn kho không đáng tin cậy** — tồn kho vật lý (on-hand) thường bị nhầm với tồn thực sự khả dụng
   sau khi trừ phần đã giữ chỗ (reservation) và tính đến trạng thái chất lượng.
3. **Trôi phiên bản (version drift)** — một Work Order có thể trở nên không nhất quán nếu BOM hoặc
   Routing gốc thay đổi sau khi đã lập kế hoạch, trừ khi phiên bản được chọn tại thời điểm đó được
   **chụp ảnh (snapshot)** lại.
4. **Truy xuất nguồn gốc yếu** — việc xuất vật tư, xác nhận WIP, phiếu nhập kho, và phả hệ lot khó
   đối chiếu ngược lại với nhau khi mỗi bước được ghi riêng lẻ, không liên kết.
5. **Retry và phê duyệt không an toàn** — lỗi mạng, gửi trùng, và hành động thay đổi tồn kho không
   được xác nhận có thể tạo ra bản ghi trùng lặp hoặc không nhất quán.

**Giải thích:** đây chính là 5 "lý do tồn tại" của mọi tính năng phức tạp mà Chương 3 sẽ mô tả sau
này — mỗi cơ chế kỹ thuật (snapshot, idempotency, availability tách reserved/quality-hold, …) trong
Chương 3 đều là câu trả lời trực tiếp cho đúng một trong 5 khoảng trống này.

### 1.3. Mục tiêu dự án

Report liệt kê 7 mục tiêu, có thể gom lại thành 4 nhóm:

- **Vòng lặp end-to-end**: từ Sales Order đã xác nhận tới tồn kho thành phẩm đã qua QC.
- **MRP có thẩm quyền (authoritative)**: Planning Run bất biến, nổ BOM đa cấp, netting tồn an toàn,
  đề xuất MAKE/BUY tường minh.
- **Toàn vẹn tồn kho**: tách bạch on-hand / reserved / available; loại trừ lot HOLD và REJECTED; dùng
  bản ghi movement dạng append-only (chỉ thêm, không sửa/xoá).
- **Bảo toàn phả hệ sản xuất**: liên kết Sales Order line ↔ Work Order ↔ snapshot BOM/Routing ↔ lot
  đã xuất ↔ xác nhận WIP ↔ Production Receipt ↔ lot đầu ra.
- **Kiểm soát vai trò và phạm vi**: permission động + quyền truy cập theo Plant/Warehouse ở mọi tầng
  (điều hướng, route, hành động, dữ liệu).
- **Vận hành chịu lỗi (resilient)**: idempotency key, envelope API ổn định, lỗi tường minh, form giữ
  lại dữ liệu người dùng đã nhập sau khi lỗi.
- **Kiểm chứng triển khai**: phủ các luật lõi qua regression tự động, kiểm tra hợp đồng API, và
  nghiệm thu desktop/tablet.

### 1.4. Phạm vi dự án

#### 1.4.1. Trong phạm vi Capstone 2

| Khu vực | Ranh giới đã triển khai |
|---|---|
| Nền tảng & quản trị | Xác thực, token phiên xoay vòng, user, role, permission, access scope, chọn Plant, xem audit |
| Nền tảng sản xuất | UOM, Item Master, cấu hình Item-Warehouse, BOM đa cấp, tồn kho, lot, stock movement |
| Nhu cầu & hoạch định | Sales Order, planning demand đã xác nhận, Planning Run bất biến, nổ BOM, netting tồn an toàn, báo cáo ngoại lệ, đề xuất MAKE/BUY |
| Chứng từ sản xuất | Tạo/chuyển đổi Work Order, phân bổ nhu cầu, snapshot BOM/Routing, đánh giá sẵn sàng vật tư, release, huỷ |
| Thực thi | Reservation, Material Issue, đường biên xuất vượt định mức (over-BOM), xác nhận thủ công good/scrap/rework/time, truy vết WIP |
| Nhập kho & chất lượng | Production Receipt từng phần, duyệt/từ chối của Manager, output ở HOLD, QC disposition thành AVAILABLE hoặc REJECTED |
| Báo cáo & UX | Inventory Dashboard, cảnh báo tồn thấp, movement, giao diện EN/VI, trạng thái loading/empty/error, desktop/tablet |

#### 1.4.2. Ngoài phạm vi và bị hoãn

- **Purchasing đầy đủ**: vòng đời Supplier, Purchase Requisition, Purchase Order, Goods Receipt và
  quy trình phê duyệt thương mại — hoãn sang Capstone 3. Đề xuất BUY **vẫn hiển thị nhưng chỉ đọc**
  ở MVP thu hẹp.
- **Sales fulfillment**: đơn giao hàng, hoá đơn, thanh toán, hạn mức tín dụng khách hàng — hoãn.
- **MES và telemetry**: tích hợp PLC/IoT, tín hiệu máy tự động, dispatching, downtime và OEE — hoãn;
  Capstone 2 dùng xác nhận thủ công qua ERP.
- **Costing chi tiết**: nhân công, chi phí chung, định giá WIP, so sánh chi phí chuẩn-thực tế, và
  phân tích lợi nhuận — hoãn.
- **Lập lịch nâng cao**: cảnh báo công suất và bối cảnh Routing được giữ lại, nhưng tối ưu lập lịch
  hữu hạn tự động (finite-schedule) không nằm trong MVP thu hẹp.
- **Production Estimation độc lập**: bộ tính thử-đúng-sai kế thừa từ trước không nằm trong xương
  sống sản xuất Capstone 2 vì Planning Run có thẩm quyền chính thức đã bao phủ đúng mục đích MRP
  vận hành rồi.

### 1.5. Đóng góp của Capstone 2

Report nhấn mạnh hai đóng góp chính:

1. **Không phải một tập hợp màn hình rời rạc, mà là một chuỗi chuyển trạng thái có kiểm soát**
   (controlled state transition) xuyên suốt các chứng từ nghiệp vụ. Mỗi lần chuyển trạng thái có
   **chủ sở hữu** (ai được làm), **quyền** (permission nào), **luật kiểm tra** (validation rule),
   **tác động tồn kho** (inventory effect), và **quan hệ truy vết** (trace relationship) rõ ràng.
   Nhờ thiết kế này, cùng một luồng có thể được minh hoạ từ nhu cầu tới sản lượng đã duyệt mà không
   cần bịa dữ liệu MES hay bỏ qua sổ cái tồn kho.
2. **Tách bạch master data khỏi bằng chứng thực thi.** Phiên bản BOM và Routing đang active được
   **chọn** tại thời điểm lập kế hoạch, còn Work Order nhận **snapshot bất biến**. Nhờ vậy một chứng
   từ vẫn audit lại được ngay cả khi master data kỹ thuật sau đó thay đổi.

### 1.6. Cấu trúc báo cáo

Report gồm 5 chương: Chương 1 giới thiệu bài toán/mục tiêu/phạm vi; Chương 2 so sánh với các ERP sản
xuất đã có; Chương 3 trình bày kiến trúc, tác nhân, module, luồng nghiệp vụ, luật, API, bảo mật, truy
vết, vận hành hệ thống; Chương 4 trình bày kết quả triển khai, bằng chứng giao diện API-backed, kết
quả kiểm thử, giới hạn, và thảo luận kỹ thuật; Chương 5 kết luận phase Capstone 2 và định nghĩa lộ
trình Capstone 3.

---

## Chương 2 — Công trình liên quan

### 2.1. Tổng quan các hệ thống hiện có

Report tham chiếu 3 nền tảng ERP thương mại/mã nguồn mở làm chuẩn tham chiếu:

- **SAP S/4HANA**: hỗ trợ MRP quy mô lớn, production planning, và lập lịch chi tiết.
- **Odoo**: cung cấp Bill of Materials, manufacturing order, work order, work center, và thực thi
  hướng shop-floor.
- **ERPNext**: cung cấp production plan, Work Order, Stock Entry, Job Card, nổ BOM, và tích hợp tồn
  kho trong một bộ mã nguồn mở tương đối dễ tiếp cận.

Ba hệ thống này xác nhận **khuôn mẫu chuẩn của ngành sản xuất** mà OmniPlant chủ đích đi theo: master
data kỹ thuật đi trước; nhu cầu đã xác nhận được chuyển thành cung ứng đã lập kế hoạch; Work Order
định nghĩa sản xuất nội bộ; tiêu thụ vật tư và sản lượng đầu ra cập nhật tồn kho qua chứng từ có kiểm
soát. OmniPlant **cố ý** đi theo các khái niệm dễ nhận diện này để người vận hành không phải học một
mô hình khác lạ.

### 2.2. Phân tích so sánh

| Hệ thống | Điểm mạnh liên quan | Cân nhắc cho Capstone 2 |
|---|---|---|
| SAP S/4HANA | MRP quy mô enterprise, PP/DS, costing, tích hợp toàn cầu | Chi phí triển khai và độ phức tạp quá cao cho một capstone SME thu hẹp |
| Odoo Manufacturing | Manufacturing order cấu hình được, work center, tích hợp shop-floor và tồn kho | Bộ ứng dụng và bề mặt cấu hình quá rộng so với luồng nghiệp vụ mục tiêu |
| ERPNext | BOM dễ tiếp cận, Production Plan, Work Order, luồng hướng tồn kho | Vẫn là một mô hình ERP tổng quát hơn, cần điều chỉnh |
| **OmniPlant** | Vòng lặp sản xuất tập trung từ nhu cầu tới QC, RBAC động, phả hệ lot, ranh giới thủ công-không-MES rõ ràng | Phạm vi hẹp hơn; purchasing, tài chính, MES và costing chi tiết bị hoãn |

### 2.3. Khoảng trống đã xác định & vị trí thiết kế

**Ý quan trọng nhất của chương này:** khoảng trống mà OmniPlant giải quyết **không phải** là "chưa có
phần mềm ERP đủ trưởng thành" — mà là **độ khó khi áp dụng một nền tảng tổng quát, cồng kềnh cho một
đội sản xuất nhỏ cần một vòng lặp sản xuất minh bạch, chứng minh được, và truy vết được.** Vì vậy
OmniPlant chọn một tập nhỏ hơn các khái niệm và làm cho luật của chúng **tường minh**: hàng nào đủ
điều kiện, khi nào một Work Order được phép release, ai duyệt một phiếu nhập kho, chất lượng ảnh
hưởng thế nào tới hàng khả dụng, và làm sao đi từ đơn hàng khách tới một lô thành phẩm cụ thể.

Report cũng phân biệt rõ **hoạch định vận hành** (operational planning) với **ước tính tuỳ ý**
(ad-hoc estimation): ERPNext và các bộ tương đương cho thấy production planning bắt đầu từ nhu cầu và
tạo ra chứng từ sản xuất thực thi được. Capstone 2 áp dụng mô hình vận hành này qua Planning Run bất
biến. Một bộ tính thử độc lập (what-if estimator) vẫn có thể hữu ích cho thuyết trình hoặc báo giá
trong tương lai, nhưng **không được phép thay thế bằng chứng MRP** trong phạm vi đã triển khai.

---

## Chương 3 — Kiến trúc, phân tích, thiết kế và triển khai

Đây là chương dài nhất và quan trọng nhất của báo cáo — mô tả toàn bộ thiết kế kỹ thuật.

### 3.2. Kiến trúc hệ thống

**Kiểu kiến trúc (3.2.1):** OmniPlant dùng **modular-monolith** — một backend deploy duy nhất nhưng
chia rõ theo module chức năng. Cách này giữ deploy và quản lý transaction đơn giản hơn kiến trúc
microservice phân tán, trong khi vẫn giữ được ranh giới domain.

Frontend chịu trách nhiệm: tương tác người dùng, validate phía client, điều hướng nhận biết quyền,
quản lý query state, đa ngôn ngữ, và trình bày responsive. Backend là **nguồn thẩm quyền** cho: xác
thực, phân quyền, chuyển trạng thái nghiệp vụ, tác động tồn kho, tính toán hoạch định vật tư, luật
phê duyệt, và tính nhất quán database.

PostgreSQL lưu dữ liệu giao dịch và master data; Redis hỗ trợ phiên và quản lý token. Giao tiếp
FE↔BE qua REST API dưới namespace `/api/v1`.

**Điểm nhấn quan trọng:** dù frontend chặn hành động không hợp lệ khi có thể, **mọi luật quan trọng
đều được backend kiểm lại** — bao gồm quyền truy cập Plant/Warehouse, điều kiện sẵn sàng release Work
Order, tính khả dụng vật tư, quyền phê duyệt, phán quyết chất lượng, và giao dịch tồn kho idempotent.

**Technology Stack (3.2.2):**

| Lớp | Công nghệ và trách nhiệm |
|---|---|
| Frontend | Next.js App Router, React, TypeScript nghiêm ngặt |
| UI/styling | Tailwind CSS, Shadcn UI tuỳ biến, Lucide icons |
| Client state | React Query cho server state, Context API cho auth và Plant đang chọn |
| i18n | next-intl, namespace English và Vietnamese |
| Backend | Java 17, Spring Boot, Spring Security, JPA/Hibernate |
| Persistence | PostgreSQL + Flyway migration, Redis hỗ trợ phiên |
| Integration | REST `/api/v1`, UUID identifier, response envelope chuẩn |

### 3.3. Phân tích chức năng & mô hình tác nhân

**Tác nhân hệ thống (3.3.1):** 3 tác nhân người dùng chính, phân theo trách nhiệm và thẩm quyền, không
chỉ theo màn hình họ nhìn thấy:

| Tác nhân | Trách nhiệm chính |
|---|---|
| **Administrator** | Duy trì user, role, permission, access scope, cơ cấu tổ chức, master data cấp hệ thống, xem audit |
| **Manager** | Sales Order, hoạch định vật tư, quyết định cung ứng, Work Order, chứng từ mua hàng, phê duyệt sản xuất, phán quyết chất lượng, giám sát vận hành |
| **Operator** | Hoạt động kho và sản xuất được phân quyền: reservation, xuất vật tư, báo cáo sản xuất, nhận hàng, nộp Production Receipt |
| **System** (không phải tác nhân con người) | Xác thực, phân quyền, tính MRP, nổ BOM, cập nhật tồn kho, chuyển trạng thái, tính variance, ghi audit log |

**Use case model (3.3.2):** sơ đồ use case mô tả **ranh giới chức năng**, không phải thứ tự thực
thi. Nhiều use case dùng chung giữa các actor nhưng bị kiểm soát bởi permission khác nhau — ví dụ cả
Manager lẫn Operator đều xem được Work Order, nhưng chỉ Manager được uỷ quyền mới tạo/release được.
Operator tạo và nộp được Production Receipt, nhưng **không** tự duyệt được phiếu của chính mình và
không thực hiện phán quyết chất lượng cuối cùng — đây là cơ chế **separation of duties** ngăn cùng
một người vừa thực thi vừa phê duyệt một giao dịch nhạy cảm về tính toàn vẹn.

### 3.4. Thiết kế quy trình nghiệp vụ end-to-end

Hành vi vận hành của OmniPlant là **document-driven** (dẫn dắt bằng chứng từ) — một user không thể đi
thẳng từ nhu cầu tới tồn kho đầu ra mà bỏ qua các chứng từ hoạch định, sản xuất, phê duyệt, kiểm soát
chất lượng bắt buộc. Luồng được chia theo 4 "làn bơi" (swimlane): Administrator, Manager, Operator,
System.

#### 3.4.1. Thiết lập hệ thống & kiểm soát truy cập

Quy trình bắt đầu bằng xác thực và phân quyền — System validate thông tin đăng nhập và nạp role/
permission/access scope tương ứng; xác thực lỗi thì request bị từ chối trước khi vào luồng nghiệp vụ.

Trước khi hoạt động vận hành bắt đầu, Administrator cấu hình Company, Plant, Warehouse, user, role,
permission, access scope. Master data nền tảng (UOM, Item, BOM, supplier, Routing, Work Center, ca
làm việc, lịch làm việc) phải sẵn sàng theo permission của module liên quan.

System validate các bất biến master data quan trọng — ví dụ ngăn cấu trúc BOM vòng lặp, yêu cầu BOM
active để nổ vật tư, và đảm bảo master data sản xuất thuộc đúng ngữ cảnh Company/Plant.

#### 3.4.2. Sales & Material Planning

Manager tạo và xác nhận Sales Order, hoặc ghi nhận demand thủ công/dự báo. **Xác nhận** biến yêu cầu
thương mại thành planning demand đủ điều kiện. Sales Order còn ở `DRAFT` **không** tham gia MRP.

Manager chọn demand đủ điều kiện và khởi động MRP run. System tạo snapshot bất biến của demand đã
chọn, nổ BOM đa cấp đang active, đánh giá tồn kho hợp lệ và cung ứng đã lập lịch, tính net requirement:

```
netRequirement = max(0, grossRequirement + stockTarget - projectedAvailable)
```

Nếu đủ cung ứng, requirement được đánh dấu covered. Ngược lại, System tạo supply suggestion —
khuyến nghị mua vật tư hoặc tự sản xuất nội bộ. Manager xem lại suggestion, exception state của nó,
và bất kỳ thông tin BOM/Routing bị thiếu trước khi duyệt, từ chối, hoặc chuyển đổi nó.

#### 3.4.3. Luồng mua hàng (Purchasing)

Với một yêu cầu BUY, một supply suggestion đã duyệt có thể chuyển thành Purchase Requisition. Manager
xem lại và duyệt requisition trước khi chuyển thành Purchase Order và gửi cho supplier đã chọn.

Khi supplier giao hàng, Operator ghi Goods Receipt. System validate dòng Purchase Order, Warehouse,
số lượng nhận, thông tin lot, và idempotency key. Một receipt hợp lệ tạo movement tồn kho RECEIVE,
tăng tồn nguyên liệu, và cập nhật số lượng đã nhận/trạng thái của Purchase Order.

> **Lưu ý quan trọng khi đọc phần này:** đây là mô tả **thiết kế/luồng nghiệp vụ đã cài trong
> backend**, nhưng theo §1.4.2, purchasing đầy đủ **bị hoãn ở tầng giao diện MVP** của Capstone 2 —
> đề xuất BUY chỉ hiển thị, chưa thao tác được qua UI trong phạm vi demo Capstone 2.

#### 3.4.4. Luồng thực thi sản xuất

Với một yêu cầu MAKE, một suggestion đã duyệt được chuyển thành Work Order. Work Order lưu sản phẩm
đã chọn, số lượng kế hoạch, Warehouse đầu ra, bằng chứng BOM, bằng chứng Routing, và các phân bổ nhu
cầu (demand allocation).

Vật tư được **giữ chỗ (reserve)** trước khi release. Reservation giảm số lượng khả dụng nhưng **không**
đổi tồn kho vật lý. Nếu reservation không phủ đủ mọi yêu cầu component, System giữ Work Order ở
trạng thái **BLOCKED** và báo cáo số lượng còn thiếu. Sau khi đủ vật tư được giữ chỗ, Manager release
Work Order.

Operator nộp Material Issue cho các component reserved đủ điều kiện. System tạo movement tồn kho
ISSUE và giảm tồn tương ứng. Xuất vượt định mức BOM yêu cầu lý do tường minh và quyền override.

Trong quá trình sản xuất, Operator ghi nhận số lượng tốt, phế phẩm, hàng làm lại. Các giao dịch này
cập nhật số lượng thực tế của Work Order và bảo toàn bằng chứng WIP. **OmniPlant ghi nhận các kết quả
này thủ công và không tuyên bố tích hợp MES hay dữ liệu máy tự động.**

#### 3.4.5. Nhập kho, chất lượng, và cập nhật tồn kho

Sau khi sản lượng được báo cáo, Operator tạo và nộp Production Receipt. Nộp chuyển receipt sang
`PENDING_APPROVAL` mà **chưa** đổi tồn kho ngay.

Một Manager được uỷ quyền có thể duyệt hoặc từ chối receipt. Từ chối yêu cầu lý do và không có tác
động tồn kho. Duyệt tạo movement RECEIVE thành phẩm và đặt sản lượng nhận được vào trạng thái
**HOLD**.

Phán quyết chất lượng (QC disposition) được thực hiện **tách biệt** khỏi duyệt receipt. Kết quả
**AVAILABLE** giải phóng sản lượng cho hoạch định và fulfillment Sales Order. Kết quả **REJECTED**
giữ số lượng đó không khả dụng cho hoạt động vận hành. System sau đó cập nhật số dư tồn kho,
fulfillment Sales Order, dashboard vận hành, thông tin variance, và audit log.

**Giải thích tổng quát mục 3.4:** đây chính là 8 bước "vòng sản xuất xương sống" đã dùng trong
`Outline.md`/`Outline_For_Slide.md`, chỉ khác là ở đây được viết dưới dạng đoạn văn theo 4 làn bơi
thay vì sơ đồ ngang.

### 3.5. Thiết kế database

**Nguyên tắc mô hình hoá dữ liệu (3.5.1):** database dùng UUID làm khoá chính, và khoá ngoại tường
minh cho các quan hệ vật lý. Bảng giao dịch giữ timestamp, định danh actor, và field version khi cần
cho khả năng audit và optimistic concurrency (khoá lạc quan khi có tranh chấp cập nhật đồng thời).

Mô hình phân tách rõ ba loại dữ liệu: **master data tái sử dụng**, **chứng từ nghiệp vụ có kiểm soát
vòng đời**, và **bằng chứng thực thi/ledger append-only**. Một số field, như `reference_id`, **cố ý
đa hình** (polymorphic) nên không có khoá ngoại vật lý — chúng được ghi chú là quan hệ logic thay vì
được trình bày như quan hệ được database ép buộc.

Mỗi ERD đọc được **độc lập**. Một bảng được đánh dấu là "external reference" chỉ chứa khoá quan hệ mà
sơ đồ module đó cần; định nghĩa đầy đủ của nó nằm ở module sở hữu bảng.

Database được chia thành **6 module chức năng chính**:

#### 3.5.2. System Administration and Integration ERD

Định nghĩa nền tảng tổ chức và phân quyền: user, Company, Plant, Warehouse, role, permission, access
scope, assignment, audit log, và bản ghi data-import.

Một Company chứa nhiều Plant, mỗi Plant chứa nhiều Warehouse. Role kết nối với permission qua
`role_permissions`. User nhận thẩm quyền vận hành qua `user_role_assignments`, kết nối user, role, và
access scope.

Cấu trúc audit tách sự kiện audit chính khỏi các thay đổi cấp field của nó. Data import thuộc module
này vì nó hỗ trợ tích hợp có kiểm soát chứ không đại diện cho một quy trình sản xuất độc lập.

#### 3.5.3. Product and Production Master Data ERD

Chứa định nghĩa tái sử dụng cần thiết trước khi hoạch định hoặc sản xuất bắt đầu: unit of measure,
Item, Bill of Materials, Routing, Routing operation, Work Center, ca làm việc, lịch làm việc, và
standard cost.

Một Item thuộc về một Company và có thể được dùng làm parent hoặc component của BOM. BOM header định
danh Item cha được sản xuất và revision, trong khi BOM line định nghĩa Item component và số lượng yêu
cầu.

Một Routing thuộc về một Item và chứa các Routing operation có thứ tự. Operation có thể tham chiếu
Work Center, và Work Center có thể dùng lịch làm việc gồm ca hàng tuần và ngoại lệ theo ngày cụ thể.
Những quan hệ này cung cấp cấu trúc sản xuất sau này được **snapshot** vào Work Order.

#### 3.5.4. Sales and Planning ERD

Kết nối nhu cầu khách hàng với yêu cầu vật tư: Sales Order, Sales Order line, planning demand, MRP
run, run-demand snapshot, requirement line, và supply suggestion.

Một Sales Order chứa một hoặc nhiều Sales Order line. Line đã xác nhận sinh ra planning demand,
trong khi một MRP run chụp một snapshot demand bất biến qua `mrp_run_demands`.

Requirement line có thể tạo thành **cây phân cấp** thông qua `parent_requirement_line_id`, đại diện
cho việc nổ BOM đa cấp. Một requirement thiếu hụt tạo ra supply suggestion khuyến nghị hoặc mua hoặc
tự sản xuất nội bộ.

#### 3.5.5. Purchasing ERD

Đại diện cho nguồn cung vật tư bên ngoài: supplier, dữ liệu Item riêng theo supplier, Purchase
Requisition, Purchase Order, Goods Receipt, và các dòng tương ứng.

`item_suppliers` thiết lập quan hệ nguồn cung giữa Item và Supplier, gồm lead time, số lượng đặt tối
thiểu, giá, tiền tệ, và trạng thái supplier ưu tiên.

Một Purchase Requisition chứa các dòng Item được yêu cầu và có thể được chuyển thành Purchase Order.
Dòng Purchase Order giữ lại dòng requisition nguồn nếu có. Dòng Goods Receipt tham chiếu dòng Purchase
Order và movement tồn kho được tạo bởi việc ghi nhận receipt.

#### 3.5.6. Inventory and Warehouse ERD

Quản lý chính sách theo dõi item, cấu hình hoạch định, số dư tồn kho, lot, số serial, và stock
movement dạng append-only.

Tồn kho được định danh bởi Item, Warehouse, và thông tin lot hoặc serial tuỳ chọn.
`stock_balances` cung cấp số lượng on-hand, reserved, và quality-hold được tối ưu cho truy vấn.
`stock_movements` bảo toàn lịch sử bất biến của các loại giao dịch receive, issue, adjustment,
reversal, và chuyển trạng thái lot.

Cấu hình Item-Warehouse lưu tồn an toàn (safety stock), điểm đặt hàng lại (reorder point), và cấu
hình lead time được dùng bởi hoạch định và cảnh báo vận hành. Trạng thái lot và serial quyết định
một số lượng có đủ điều kiện để reserve, issue, MRP, hay fulfillment hay không.

#### 3.5.7. Manufacturing Execution and Quality ERD

Ghi lại vòng đời sản xuất nội bộ từ khi tạo Work Order qua tiêu thụ vật tư, báo cáo WIP, nhận thành
phẩm, và phán quyết chất lượng.

Một Work Order chứa snapshot component và operation bất biến. Material reservation kết nối yêu cầu
component với tồn kho Warehouse/lot đủ điều kiện. Material Issue tiêu thụ các reservation đó và kết
nối mỗi dòng issue với movement tồn kho của nó. WIP transaction và Production Execution bảo toàn
bằng chứng sản xuất thực tế.

Dòng Production Receipt kết nối Item đầu ra, Warehouse, lot hoặc serial, và stock movement. Quality
disposition được lưu **riêng** để việc duyệt receipt và chấp nhận chất lượng cuối cùng là hai quyết
định tách biệt.

#### 3.5.8. Quan hệ dữ liệu xuyên module

Report nhấn mạnh 6 module tạo thành **một mô hình sản xuất kết nối duy nhất**, không phải 6 database
cô lập:

| Chuỗi quan hệ | Mục đích |
|---|---|
| Company → Plant → Warehouse | Thiết lập quyền sở hữu tổ chức và phạm vi phân quyền |
| Item → BOM / Routing / Inventory / Purchasing | Kết nối định nghĩa sản phẩm với hoạch định, nguồn cung, tồn kho |
| Sales Order Line → Planning Demand → MRP Run | Bảo toàn nguồn gốc của nhu cầu đi vào hoạch định vật tư |
| MRP Requirement → Supply Suggestion | Ghi lại phân tích thiếu hụt và phản ứng khuyến nghị BUY hoặc MAKE |
| Purchase Order → Goods Receipt → Stock Movement | Kết nối giao hàng của supplier với sổ cái tồn kho |
| Work Order → Material Issue → Stock Movement | Kết nối tiêu thụ sản xuất với tồn kho |
| Work Order → Production Receipt → Quality Disposition | Kết nối đầu ra sản xuất với phê duyệt và trạng thái QC |
| Work Order Allocation → Sales Order Line | Kết nối đầu ra đã qua QC ngược lại với nhu cầu khách hàng |

### 3.6. Luật nghiệp vụ và kiểm soát trạng thái

Report tổng hợp các luật validate cốt lõi thành một bảng:

| Luật | Hợp đồng được thực thi |
|---|---|
| Số lượng | Số lượng giao dịch, Sales Order, sản xuất phải lớn hơn 0 |
| Điều kiện lot | Lot `HOLD`, `REJECTED`, và hết hạn bị loại khỏi tính khả dụng, reservation, MRP, và fulfillment |
| Nhu cầu | Chỉ demand đã xác nhận và còn mở tham gia hoạch định và phân bổ |
| Snapshot | Work Order giữ bằng chứng BOM và Routing bất biến |
| Release | Mọi yêu cầu component phải được reserve đầy đủ trước khi release Work Order |
| Issue | Chỉ Material Issue đã post mới giảm tồn kho; issue vượt BOM yêu cầu lý do và quyền hạn |
| Receipt | Nộp không có tác động tồn kho; chỉ duyệt mới tạo tồn kho thành phẩm |
| Chất lượng | Đầu ra đã duyệt vẫn ở HOLD tới khi có phán quyết; chỉ đầu ra AVAILABLE mới thoả mãn nhu cầu |
| An toàn thay đổi | Lệnh nhạy cảm về toàn vẹn yêu cầu xác nhận và tái sử dụng idempotency-key ổn định khi retry |

### 3.7. API, bảo mật, và khả năng chịu lỗi giao dịch

Mọi response backend dùng một **envelope chuẩn** chứa mã phản hồi (response code), thông điệp
(message), và kết quả tuỳ chọn (result). Endpoint phân trang trả thêm thông tin page, size, tổng số
phần tử, tổng số trang.

Xác thực dùng JWT access token cùng refresh token xoay vòng. Sau khi xác thực, client nạp hồ sơ user
có thẩm quyền, permission, role assignment, và access scope. Frontend dùng thông tin này để kiểm soát
điều hướng và hiển thị hành động, trong khi backend thực hiện kiểm tra phân quyền cuối cùng cho mọi
thao tác được bảo vệ.

Phân quyền kết hợp permission với **phạm vi tổ chức**. Một user có thể có quyền quản lý Work Order
nhưng bị giới hạn trong một Plant cụ thể. Request ngoài phạm vi được gán bị từ chối kể cả khi
permission tổng quát có mặt.

Request thay đổi trạng thái dùng idempotency key ổn định. Retry cùng một hành động logic tái sử dụng
key gốc của nó, ngăn chặn movement tồn kho, MRP run, Material Issue, Goods Receipt, hoặc Production
Receipt trùng lặp trong lúc lỗi mạng.

### 3.8. Thiết kế tương tác Frontend

Frontend được thiết kế chủ yếu cho vận hành desktop và tablet. Danh sách dày đặc, bộ lọc, chỉ báo
trạng thái, và form theo một mô hình tương tác ERP nhất quán.

Trạng thái loading dùng Skeleton placeholder. Trạng thái empty giải thích **vì sao** không có bản ghi
và chỉ hiển thị hành động tạo mới khi user có quyền. Lỗi validate và API **giữ lại giá trị đã nhập**
để user có thể sửa hoặc thử lại mà không phải nhập lại toàn bộ form.

Thay đổi trạng thái quan trọng yêu cầu **xác nhận tường minh** — bao gồm xác nhận Sales Order, kích
hoạt BOM và Routing, release Work Order, nộp Material Issue, duyệt/từ chối receipt, và phán quyết
chất lượng.

Toàn bộ nội dung hiển thị cho user được duy trì bằng namespace dịch **English và Vietnamese**. Nhãn
trạng thái và thông điệp validate dùng thuật ngữ nhất quán xuyên suốt màn hình danh sách, chi tiết, và
giao dịch.

### 3.9. Vận hành hệ thống và triển khai

Môi trường local chuẩn gồm PostgreSQL, Redis, backend Spring Boot, và frontend Next.js. Migration
PostgreSQL được quản lý qua Flyway. Backend chạy ở port 8080, frontend ở port 3000.

Report kết bảng checklist khởi động và xác minh vận hành gồm 7 bước: khởi động hạ tầng → khởi động
API → khởi động frontend → xác thực → chọn Plant → thực thi luồng nghiệp vụ → xác minh kết quả (xem
chi tiết ở Phụ lục A.2 bên dưới, đây chính là checklist demo).

### 3.10. Tóm tắt chương

Chương này trình bày thiết kế kiến trúc, chức năng, quy trình, và dữ liệu của OmniPlant. Sơ đồ use
case định nghĩa trách nhiệm của Administrator, Manager, và Operator, còn sơ đồ swimlane mô tả trình tự
thực thi và hành vi System tự động.

6 ERD minh hoạ cách quản trị, master data, sales và planning, purchasing, tồn kho, và thực thi sản
xuất được tách thành các module dễ đọc trong khi vẫn kết nối với nhau qua các quan hệ tường minh.
Cùng nhau, các thiết kế này thiết lập khả năng truy vết từ quyền truy cập tổ chức và nhu cầu khách
hàng tới hoạch định vật tư, mua hàng hoặc sản xuất, ghi nhận tồn kho, và đầu ra đã kiểm soát chất
lượng.

---

## Chương 4 — Kết quả và thảo luận

### 4.1. Kết quả triển khai

Capstone 2 đã hoàn thành vòng lặp sản xuất ERP thu hẹp từ nhu cầu Sales Order đã xác nhận tới tồn kho
đã được QC chấp nhận (AVAILABLE). Hệ thống **không còn là prototype độ trung thực cao** nữa — các
route MVP chính dùng API backend thật, định danh UUID, adapter an toàn số thập phân, React Query hook
nhận biết quyền, và invalidation có tài liệu xuyên module.

| Khu vực | Hành vi đã triển khai | Trạng thái |
|---|---|---|
| Planning | Nhu cầu đã xác nhận → run bất biến → requirement/netting → đề xuất MAKE/BUY | Hoàn chỉnh, tích hợp API |
| Work Order | Chuyển đổi/draft thủ công, plan, snapshot, sẵn sàng, reservation, release, huỷ/đóng | Hoàn chỉnh, tích hợp API |
| Material Issue | Xuất theo reservation và tồn đủ điều kiện, movement tồn kho, truy vết và đường biên variance | Hoàn chỉnh, ghi nhận trực tiếp |
| Thực thi thủ công | Good/scrap/rework, thời gian thực, ghi chú và lịch sử WIP | Hoàn chỉnh, tích hợp API |
| Production Receipt | Receipt từng phần, nộp, duyệt/từ chối, HOLD và phán quyết QC | Hoàn chỉnh, tích hợp API |
| Tồn kho/Dashboard | Số dư, lot, movement, loại trừ tính khả dụng, cảnh báo, hoạt động gần đây | Hoàn chỉnh, ghi nhận trực tiếp |
| Admin/RBAC | User, role, permission, scope và assignment | Hoàn chỉnh cho mô hình 3 tác nhân hiện tại |

### 4.2. Bằng chứng giao diện API-backed

Chương này trình bày **27 screenshot** minh hoạ các màn hình chính, mỗi screenshot kèm một đoạn mô tả
ngắn về mục đích vận hành và bằng chứng thấy được trên dữ liệu demo. Danh sách 27 màn hình (theo đúng
thứ tự report):

1. Dashboard Overview — tổng quan vận hành + cảnh báo, có backend API thật.
2. Item Master — loại item, UOM, chính sách tracking.
3. Stock Balance — on-hand / reserved / available tách bạch.
4. Inventory Lots — trạng thái AVAILABLE/HOLD/REJECTED.
5. Stock Movement History — loại giao dịch + tham chiếu truy vết.
6. BOM List — vòng đời active/draft.
7. Active BOM Detail — revision, output quantity, dòng component.
8. Multi-Level BOM Tree — cây BOM nhiều cấp, số lượng lồng nhau.
9. Sales Order List — trạng thái vòng đời + fulfillment.
10. Sales Order Detail — phân bổ dòng + số lượng fulfillment.
11. Material Planning Workspace — nhu cầu đã xác nhận + lịch sử Planning Run.
12. Planning Run Detail — thiếu hụt + quyết định cung ứng MAKE/BUY.
13. Work Order List — trạng thái vòng đời sản xuất.
14. Work Order Detail — sẵn sàng reservation đầy đủ.
15. Material Issue Workspace — component + lịch sử issue.
16. Production Execution Workspace — số lượng good/scrap/rework.
17. Production Receipt List — trạng thái duyệt + vòng đời QC.
18. Inventory Lots sau QC — kết quả AVAILABLE/HOLD/REJECTED.
19. Login/Session Initialization — khởi tạo phiên + Plant scope.
20. User Management — trạng thái vòng đời tài khoản.
21. Role Management — nhóm trách nhiệm active.
22. Permission Catalog — mã resource/action.
23. Access Scope Management — phạm vi theo cấp tổ chức.
24. User Assignment — liên kết User–Role–Scope.
25. Organization Structure — Company/Plant/Warehouse.
26. UOM Management — mã đo lường tái sử dụng.
27. Audit Logs — actor/entity/action/timestamp.

**Giải thích:** phần này thuần tuý là bằng chứng hình ảnh (screenshot), không có nội dung kỹ thuật
mới — mỗi mục chỉ 2-3 câu mô tả. Nếu cần dùng lại cho slide, nên nhóm 27 màn hình này theo đúng 5
nhóm điều hướng ở Phụ lục A.1 của report (Overview / Admin / Demand & Planning / Production /
Manufacturing Foundation) thay vì liệt kê tuần tự.

### 4.3. Kết quả nghiệm thu và validate

Việc triển khai được kiểm chứng qua test theo domain có mục tiêu, test hợp đồng API, test trình bày
component, test permission/navigation, và nghiệm thu trình duyệt trực tiếp. Kết quả cổng repository
đầy đủ gần nhất trước report: **59 file Vitest với 265 test pass**, TypeScript nghiêm ngặt, tính ngang
bằng bản dịch EN/VI, kiểm tra bao phủ API, production build, ESLint, và validate diff. Nghiệm thu
Scope 4 trước đó phủ riêng: hoạch định, phân bổ, reservation, issue, WIP, receipt từng phần, từ chối,
QC, tác động tồn kho, và huỷ.

| Cổng | Kết quả ghi nhận |
|---|---|
| Regression tự động | 59 file Vitest / 265 test pass ở cổng tích hợp hoàn tất |
| Type safety | TypeScript nghiêm ngặt và validate schema/adapter lúc chạy |
| Bao phủ API | Guard ngăn route MVP âm thầm quay lại chỉ dùng mock runtime |
| Internationalization | Tính ngang bằng key English/Vietnamese |
| Responsive QA | Kiểm tra desktop và tablet, không tràn ngang cấp document |
| RBAC | Permission/hành động Admin, Manager, Operator đã kiểm; trạng thái không quyền có regression |
| Tác động tồn kho | Reservation, issue, receipt HOLD, QC AVAILABLE/REJECTED và invalidation Dashboard đã kiểm |

### 4.4. Kết quả nghiệm thu vận hành

Bộ dữ liệu (fixture) trình bày minh hoạ một Planning Run hoàn chỉnh chứa **2 demand, 8 requirement
line, 6 thiếu hụt, 3 đề xuất MAKE và 3 khuyến nghị BUY**. Work Order được chạy qua các trạng thái
`PLANNED`, `RELEASED`, `BLOCKED`, `IN_PROGRESS`, và `COMPLETED`. Bằng chứng thực thi ghi nhận số
lượng good, scrap, và rework, còn Production Receipt phủ các kết quả `DRAFT`, `PENDING_APPROVAL`,
`APPROVED/HOLD`, QC `AVAILABLE`, và QC `REJECTED`.

Việc trình bày cũng cho thấy **fulfillment từng phần của Sales Order** sau đầu ra QC `AVAILABLE`, xác
nhận rằng thành phẩm được chấp nhận chảy ngược trở lại trạng thái nhu cầu nghiệp vụ chứ không chỉ
xuất hiện trên một màn hình tồn kho cô lập.

### 4.5. Thảo luận kỹ thuật

#### 4.5.1. Planning Run so với Production Estimation độc lập

Cả hai chức năng đều dùng nổ BOM và so sánh tồn kho, nhưng mục đích nghiệp vụ khác nhau. Bộ tính thử
độc lập trả lời một câu hỏi tuỳ ý về một sản phẩm và số lượng mục tiêu. Planning Run bắt đầu từ nhu
cầu đã xác nhận, tạo snapshot bất biến kiểm chứng được, áp dụng netting tồn an toàn, tạo đề xuất cung
ứng, và tiếp tục thành Work Order. Vì lý do này, **Planning Run là bằng chứng có thẩm quyền của
Capstone 2**, còn Production Estimation nằm ngoài phạm vi thu hẹp.

#### 4.5.2. Snapshot bất biến và Idempotency

Bản ghi sản xuất phải vẫn giải thích được sau khi master data kỹ thuật thay đổi. Việc snapshot nội
dung BOM và Routing vào Work Order cung cấp sự ổn định đó. Idempotency giải quyết một **chiều thời
gian khác**: nó ngăn một lần gửi mạng lặp lại tạo ra một chứng từ logic thứ hai. Kết hợp lại, hai cơ
chế này bảo vệ cả **ý nghĩa lịch sử** lẫn **tính duy nhất của giao dịch**.

#### 4.5.3. Ngữ nghĩa tính khả dụng và chất lượng

Số lượng on-hand một mình là không đủ cho hoạch định. OmniPlant tách reservation và trạng thái chất
lượng ra khỏi nhau để chỉ tồn đủ điều kiện mới trở thành cung ứng đã lập kế hoạch. **Cùng một luật
được áp dụng nhất quán** xuyên suốt cảnh báo Dashboard, MRP, chọn lựa reservation/issue, và
fulfillment Sales Order. Tính nhất quán này **quan trọng hơn bất kỳ một màn hình đơn lẻ nào**, vì một
sự sai lệch sẽ tạo ra "sẵn sàng sản xuất giả" (false production readiness).

#### 4.5.4. Thực thi ERP không có MES

Capstone 2 ghi nhận sản xuất thực tế thủ công thay vì mô phỏng tích hợp máy móc. Giao diện thu thập
số lượng, thời gian, actor, và ghi chú, trong khi mô hình dữ liệu bảo toàn một **đường biên nguồn thực
thi có thể mở rộng** (extensible execution-source boundary). Cách tiếp cận này mang lại một quy trình
ERP dùng được ngay, đồng thời tránh những tuyên bố gây hiểu nhầm về telemetry thời gian thực, OEE, hay
dispatching shop-floor tự động.

### 4.6. Giới hạn đã biết

Report liệt kê 6 giới hạn — nên đọc kỹ vì đây chính là cơ sở của "Roadmap Capstone 3" ở Chương 5:

- Thực thi phía BUY đầy đủ bị hoãn; một đề xuất BUY chưa tạo được scheduled receipt cam kết ở MVP
  thu hẹp.
- Capacity chỉ mang tính tư vấn (advisory) và được giữ lại như một master/scheduling seam hỗ trợ; tối
  ưu hữu hạn (finite optimization) tự động chưa được triển khai.
- Mô hình 3 tác nhân hiện tại gán việc duyệt Production Receipt và phán quyết QC cho Manager qua hai
  permission riêng biệt; một role QC chuyên biệt có thể được thêm sau này.
- Chi phí sản xuất chi tiết, định giá nhân công/chi phí chung, OEE, và lợi nhuận chưa được tính toán.
- Dữ liệu MES, PLC, và IoT chưa được tích hợp; mọi xác nhận thực thi đều thủ công.
- Kiểm toán trên fixture trình bày đã ghi nhận các trường hợp biên về idempotency/atomicity và tính
  khả dụng để tiếp tục củng cố backend; các hợp đồng nghiệp vụ vẫn được kiểm thử và giám sát tường
  minh.

---

## Chương 5 — Kết luận và hướng phát triển

### 5.1. Kết luận

Capstone 2 biến OmniPlant từ một dự án phân tích-và-prototype thành một ứng dụng Manufacturing ERP
tích hợp có **xương sống vận hành mạch lạc**. Hệ thống nắm bắt nhu cầu khách hàng, thực hiện hoạch
định vật tư, tạo và kiểm soát Work Order, bảo toàn khả năng truy xuất tồn kho và lot, ghi nhận thực
thi sản xuất thủ công, phê duyệt đầu ra, và ép buộc một cổng chất lượng trước khi thành phẩm trở nên
khả dụng.

Việc triển khai chứng minh rằng một ERP hướng tới SME thu hẹp có thể duy trì các cơ chế bảo vệ cấp
enterprise mà không cần tiếp nhận toàn bộ độ phức tạp của một bộ thương mại lớn. Permission động,
phạm vi theo Plant, snapshot bất biến, movement tồn kho append-only, lệnh idempotent, trạng thái tường
minh, và không gian làm việc danh sách/form responsive cung cấp một nền tảng có kỷ luật cho việc dùng
vận hành thật và mở rộng sau này.

**Kết quả quan trọng nhất của dự án là tính liên tục của bằng chứng** (continuity of evidence). Một
người review có thể điều hướng từ một dòng Sales Order, qua một Planning Run bất biến và Work Order,
tới lô vật tư đã xuất, xác nhận WIP, Production Receipt, kết quả QC, và tồn kho đầu ra. Tính liên tục
này là thành tựu định nghĩa của phạm vi Capstone 2.

### 5.2. Hướng phát triển

Report liệt kê 7 hướng phát triển tương lai:

- **Purchasing**: triển khai supplier, Purchase Request/Requisition, Purchase Order, Goods Receipt,
  quy trình phê duyệt và kiểm soát chất lượng đầu vào; chỉ receipt đã xác nhận mới nên trở thành cung
  ứng đã lập lịch.
- **Sales fulfillment**: thêm phân bổ giao hàng, xuất kho bán hàng, hoá đơn, thanh toán, và kiểm soát
  hạn mức tín dụng khách hàng.
- **Costing sản xuất**: thêm định mức nhân công, chi phí chung, định giá WIP, phân tích biến động chi
  phí (cost variance), và lợi nhuận.
- **Tích hợp MES**: kết nối message chuẩn ISA-95, telemetry PLC/IoT, sự kiện thực thi tự động,
  downtime và OEE trong khi vẫn giữ lại phương án dự phòng thủ công.
- **Lập lịch nâng cao**: thêm tối ưu công suất hữu hạn (finite-capacity), dispatching, tái lập lịch,
  và so sánh kịch bản ngoài phạm vi cảnh báo quá tải advisory hiện tại.
- **Quản lý chất lượng**: thêm role QC chuyên biệt, kế hoạch kiểm tra, đo lường, non-conformance,
  quarantine, và hành động khắc phục.
- **Phân tích what-if tuỳ chọn**: chỉ chuyển Production Estimation độc lập lên backend nếu Product
  Owner hoặc nhu cầu trình bày biện minh cho một bộ tính toán không-thực-thi riêng biệt.

### 5.3. Trình tự triển khai Capstone 3 được đề xuất

| Ưu tiên | Mở rộng | Lý do |
|---|---|---|
| 1 | Purchasing và scheduled receipt | Khép vòng đề xuất BUY và cải thiện độ thực tế của MRP |
| 2 | Giao hàng và hoá đơn | Khép vòng fulfillment Sales Order vượt ra ngoài hoàn thành sản xuất |
| 3 | Costing và variance | Dùng bằng chứng số lượng/thời gian đã thu thập sẵn trong Capstone 2 |
| 4 | Quản lý chất lượng chuyên biệt | Mở rộng cổng HOLD/AVAILABLE/REJECTED hiện tại thành kiểm tra đo lường được |
| 5 | MES/OEE và lập lịch hữu hạn | Xây trên nền chứng từ ERP đã ổn định sau khi nền tảng giao dịch được chứng minh |

### 5.4. Tham chiếu trạng thái vận hành

Bảng chuyển trạng thái chính của các chứng từ (đây là bảng đáng nhớ nhất để trả lời câu hỏi "trạng
thái của X đi như thế nào"):

| Chứng từ | Chuyển trạng thái chính |
|---|---|
| Sales Order | `DRAFT → CONFIRMED → IN_PRODUCTION → PARTIALLY_FULFILLED → FULFILLED → CLOSED` |
| Planning Run | `PENDING/RUNNING → COMPLETED` hoặc `FAILED`; kết quả bất biến |
| Supply Suggestion | `DRAFT → APPROVED/REJECTED → CONVERTED` (cho MAKE đủ điều kiện) |
| Work Order | `DRAFT → PLANNED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED`; nhánh phụ `BLOCKED`/`CANCELLED` |
| Production Receipt | `DRAFT → PENDING_APPROVAL → APPROVED/HOLD → AVAILABLE` hoặc `REJECTED` |
| Inventory Lot | `AVAILABLE`, `HOLD`, hoặc `REJECTED`; chỉ `AVAILABLE` là cung ứng đủ điều kiện |

---

## Phụ lục của báo cáo

### References (Tài liệu tham khảo)

Report trích dẫn 9 nguồn: tài liệu SAP S/4HANA Production Planning/MRP; tài liệu Manufacturing Order/
Work Order của Odoo; tài liệu Manufacturing/Production Plan/Work Order của ERPNext (Frappe); Spring
Boot Reference Documentation; PostgreSQL Documentation; Redis Documentation; Next.js App Router
Documentation; TanStack Query Documentation; và tài liệu nội bộ của nhóm (Frontend System Context,
Business Flow, API Guide, Capstone 2 Progress Records).

### A.1. Danh mục màn hình MVP thu hẹp

| Nhóm điều hướng | Không gian làm việc MVP hiển thị |
|---|---|
| Overview | Dashboard |
| Admin | Users; Roles; Permissions; Access Scopes; User Assignments |
| Demand and planning | Sales Orders; Material Planning; Planning Run Detail |
| Production | Work Orders; Material Issues; Production Execution; Production Receipts |
| Manufacturing foundation | Item Master; UOM; Stock; Inventory Lots; Stock Movements; BOM |

### A.2. Checklist nghiệm thu end-to-end (11 bước)

Đây là kịch bản demo chính thức mà report dùng để nghiệm thu — nên dùng lại **đúng nguyên** cho buổi
bảo vệ (đã rút gọn thành 6 bước trong `Outline_For_Slide.md`, nhưng bản đầy đủ 11 bước nằm ở đây để
đối chiếu khi cần):

1. Đăng nhập với vai trò Manager và chọn một Plant được uỷ quyền.
2. Xác minh Item active, UOM, Warehouse, BOM, và dữ liệu tồn kho đủ điều kiện.
3. Tạo và xác nhận một Sales Order với số lượng dương và ngày đến hạn.
4. Chạy Material Planning và kiểm tra requirement, thông điệp thiếu hụt, và đề xuất MAKE/BUY.
5. Duyệt và chuyển đổi một đề xuất MAKE đủ điều kiện thành Work Order.
6. Reserve 100% component yêu cầu và release Work Order.
7. Nộp Material Issue dùng tồn kho lot/serial đủ điều kiện và xác nhận movement/truy vết.
8. Ghi nhận thực thi thủ công: good, scrap, rework, và thời gian thực tế.
9. Tạo và nộp Production Receipt; duyệt nó vào trạng thái HOLD.
10. Thực hiện phán quyết QC và xác minh hành vi đầu ra AVAILABLE hoặc REJECTED.
11. Xác minh fulfillment Sales Order, inventory lot, stock movement, Dashboard, và các liên kết phả
    hệ.

---

## Đọc nhanh: 10 điều cần nhớ nếu chỉ có 5 phút

1. **OmniPlant** = Manufacturing ERP cho SME, Capstone 2 biến prototype thành **vòng lặp sản xuất
   chạy trên API thật** — từ Sales Order tới tồn kho đã qua QC.
2. **5 khoảng trống bài toán**: nhu cầu-sản xuất rời nhau, tồn kho không đáng tin, trôi phiên bản,
   truy xuất yếu, retry/duyệt không an toàn.
3. **Vòng xương sống 8 bước**: Sales Order → Planning Run (MRP) → Supply Suggestion (MAKE/BUY) →
   Work Order → Material Issue → Production Execution (thủ công) → Production Receipt (HOLD) → QC
   (AVAILABLE/REJECTED) → khép vòng vào Sales Order fulfillment.
4. **Công thức netting MRP**: `netRequirement = max(0, grossRequirement + stockTarget − projectedAvailable)`.
5. **Kiến trúc**: Next.js/TypeScript (FE) + Spring Boot modular-monolith (BE) + PostgreSQL + Redis,
   REST `/api/v1`, backend là **nguồn thẩm quyền cuối cùng**.
6. **6 module database**: System Administration, Product/Production Master Data, Sales & Planning,
   Purchasing, Inventory & Warehouse, Manufacturing Execution & Quality.
7. **3 tác nhân**: Administrator (quản trị/RBAC), Manager (hoạch định/phê duyệt/QC), Operator
   (thực thi kho/xưởng) — có separation of duties.
8. **Đóng góp chính**: chuỗi state transition có kiểm soát (không phải nhiều màn hình rời rạc) +
   tách bạch master data khỏi bằng chứng thực thi (snapshot bất biến).
9. **Ngoài phạm vi Capstone 2**: Purchasing đầy đủ trên UI, giao hàng/hoá đơn, MES/PLC/IoT, costing
   chi tiết, lập lịch hữu hạn tự động, Production Estimation độc lập.
10. **Nghiệm thu**: 59 file Vitest / 265 test pass, RBAC đã kiểm đủ 3 role, fixture demo có 2 demand/
    8 requirement/6 thiếu hụt/3 MAKE/3 BUY, Work Order và Production Receipt chạy qua đủ trạng thái
    chính, Sales Order chuyển `PARTIALLY_FULFILLED` sau QC AVAILABLE.

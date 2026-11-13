# Giai đoạn 2 — Bàn giao Backend: Tổ chức và danh mục nền

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock đã chốt; cần API danh mục Nhà cung cấp/Khách hàng và đối chiếu các API tổ chức, đơn vị, vật tư, thiết lập kho với hành vi dưới đây.
- Ảnh hưởng: các màn hình quản trị danh mục và dữ liệu tham chiếu cho giao dịch; hiện dùng demo/mock, chưa nghiệm thu tích hợp giai đoạn 2.
- Mức ưu tiên: P1 — hai danh mục mới chưa dùng được trong chế độ API; các phần đã có adapter vẫn được giữ, FE/mock không bị chặn.

**Bản:** v1 · **Ngày dự kiến gửi:** 12/10/2026 · **PO chốt FE:** 12/10/2026, yêu cầu “chốt giai đoạn 2, tạo tài liệu”.
**FE:** mốc triển khai Session 290, bàn giao Session 293; **BE owner:** chưa chỉ định cá nhân.
**Trạng thái:** FE/mock được PO chốt, tài liệu chuẩn bị gửi; contract và nghiệm thu BE chưa chốt.
Nếu gửi ngày khác, đổi ngày tên file trước lần gửi đầu; bản sửa sau khi gửi thêm `_v2`, `_v3`.

Nhãn dùng trong tài liệu: **[Đã kiểm chứng FE]** là hành vi/source/test FE; **[Đề xuất BE]** là
hợp đồng cần thống nhất; **[Chưa kiểm chứng live]** là chưa gọi/nghiệm thu BE trong lần bàn giao này.
Chốt FE không tự biến các đề xuất thành API đã được BE duyệt.

## §1 — Phạm vi

### 1.1 Màn hình mới

| Màn hình | Thao tác và thông tin người dùng thấy |
|---|---|
| Nhà cung cấp | Danh sách mã/tên/trạng thái; tìm kiếm/lọc/phân trang; tạo/sửa trong dialog; xác nhận ngừng/kích hoạt |
| Khách hàng | Các thao tác như Nhà cung cấp; danh mục riêng, được phép trùng mã với Nhà cung cấp |

Hai form gồm mã, tên, người liên hệ, email, điện thoại, địa chỉ, ghi chú. Chỉ mã/tên bắt buộc.
Form 640px, căn giữa; đây là danh mục đối tác kinh doanh, không tạo tài khoản đăng nhập.

### 1.2 Màn hình đã sửa

| Màn hình | Kết quả FE/mock đã chốt |
|---|---|
| Cơ cấu tổ chức | Công ty chỉ xem; tab Nhà máy/Kho; tìm mã/tên, lọc trạng thái/nhà máy; create/edit/status và kiểm tra parent; dialog 560px |
| Đơn vị tính | Danh sách, tìm kiếm, trạng thái; create/edit/status; dialog 480px |
| Danh mục vật tư | Lọc loại/tracking/trạng thái; create/edit dialog 640px; mã mở chi tiết gồm Thông tin và Thiết lập kho |
| Thiết lập kho của vật tư | Chọn kho thuộc nhà máy đang chọn; tồn an toàn, điểm đặt lại, thời gian cung ứng; hai mục đích kho mặc định; confirmation thay thế/ngừng mapping |
| Sidebar | Bổ sung các mục danh mục; kế thừa giao diện đã chốt giai đoạn 1, không tạo quyền từ tên vai trò |

UI dùng tên nghiệp vụ tiếng Việt/Anh; mã `PERM_*` chỉ thuộc contract/authorization.
Short API forms hiện có cũng được đổi sang dialog. Workspace live tổ chức/vật tư còn dùng bố cục
cũ ở một số phần; không coi mock UI là bằng chứng rằng mọi bố cục live đã nghiệm thu.

### 1.3 Trong và ngoài phạm vi

**Trong phạm vi:** Company chỉ đọc; Plant → Warehouse; UOM; Item; Supplier/Customer cơ bản;
ACTIVE/INACTIVE, code uniqueness, references, search/pagination, bất biến, warehouse settings/defaults,
permission/scope phía BE. BE rà endpoint đã có trước khi bổ sung, không viết lại API đang đáp ứng.

**Ngoài phạm vi:** CRUD Company/đa công ty trên UI; Import Excel (giai đoạn 3); PO/GRN, bán hàng đầy đủ,
giá/công nợ/thanh toán; tồn đầu kỳ; BOM/routing; serial tracking; bổ sung nghiệp vụ SERVICE;
đổi UOM có quy đổi; tự động chọn kho khi thiếu mapping; thay đổi luồng Auth/email giai đoạn 1.
Adapter Item cũ có enum SERVICE để tương thích, nhưng không yêu cầu BE mở rộng SERVICE ở task này.

**Môi trường:** FE demo/mock, Next.js production build cục bộ; dữ liệu master lưu trong trình duyệt,
không phải DB BE/đa người dùng. BE đang làm giai đoạn 1 theo PO. Không chuyển API flag trong bàn giao.
Nguồn adapter được đối chiếu ngày 12/10/2026; phiên bản BE đang deploy chưa xác minh trong phiên này.

### 1.4 Luồng để BE tái hiện

1. Người có quyền xem mở danh mục, tìm theo mã/tên, lọc và chuyển trang; người có quyền quản lý mới tạo/sửa/status.
2. Tạo Nhà máy trong công ty → tạo Kho dưới nhà máy hoạt động → tạo Đơn vị tính → tạo Vật tư dùng đơn vị đó.
3. Chọn nhà máy P1, mở vật tư I1 → Thiết lập kho. Chỉ xem/chọn kho của P1 theo scope được cấp.
4. Nhập tồn an toàn và điểm đặt lại không âm, thời gian cung ứng nguyên không âm; lưu mapping I1 + W1.
5. Đặt W1 làm mặc định cấp nguyên liệu; đặt W2 làm mặc định nhận thành phẩm. Hai mục đích độc lập.
6. Chuyển mặc định cấp sang W2: khi phát hiện W1 đang là mặc định, FE yêu cầu xác nhận; hủy giữ nguyên,
   xác nhận mới gửi ý định thay thế. BE thay cờ của cùng mục đích trong một transaction.
7. Tạo/sửa Nhà cung cấp và Khách hàng; ngừng dùng sau xác nhận; xem lại record cũ được phép.
8. Reload đọc lại dữ liệu server. Đổi nhà máy đóng form/chi tiết đang mở, không gửi mapping sang nhà máy khác.

### 1.5 Quyền và phạm vi

**[Đã kiểm chứng FE]** Các nhóm quyền/scope hiện được kiểm tra như bảng. **[Đề xuất BE]** BE áp dụng
kiểm tra tương đương cho từng request, kể cả truy cập ID trực tiếp; tên role không thay cho permission.

| Dữ liệu | Quyền xem | Quyền quản lý | Phạm vi FE hiện tại |
|---|---|---|---|
| Company/Plant/Warehouse | PERM_ORG_READ | PERM_ORG_MANAGE | GLOBAL cho workspace tổ chức; Warehouse vẫn kiểm tra đúng Plant |
| UOM | PERM_UOM_READ | PERM_UOM_MANAGE | GLOBAL |
| Item / warehouse settings | PERM_ITEM_READ | PERM_ITEM_MANAGE | PLANT phù hợp hoặc GLOBAL; Item catalog thuộc Company, mapping thuộc Plant |
| Supplier | PERM_SUPPLIER_READ | PERM_SUPPLIER_MANAGE | COMPANY phù hợp hoặc GLOBAL; **mã quyền đề xuất** |
| Customer | PERM_CUSTOMER_READ | PERM_CUSTOMER_MANAGE | COMPANY phù hợp hoặc GLOBAL; **mã quyền đề xuất** |

MANAGE không tự thay cho READ trong shared mock workspace. Cấp role quản lý danh mục cần cả hai
quyền nếu muốn xem/lưu. Quyền mới trong role ADMIN/MANAGER của demo là seed mock, không phải
yêu cầu gán tự động cho mọi người dùng BE. Phạm vi GLOBAL quản trị từ giai đoạn 1 vẫn chờ chốt chung.

## §2 — Bằng chứng tái hiện

**Không có báo lỗi BE trong tài liệu này.** Không có request/response thật, HTTP, X-Trace-Id hoặc
record ID từ BE cho giai đoạn 2 trong phiên bàn giao. Không khẳng định đã chạy bản BE mới nhất.
Các JSON và bộ dữ liệu ở §3 là **ví dụ đề xuất**, không phải log từ server.

**[Đã kiểm chứng FE]** Bằng chứng Session 290, 12/10/2026:

- Full regression: 88 test files, 388 tests đạt; log bắt đầu 19:56:28 theo đồng hồ test.
- Build production/TypeScript và đối chiếu bản dịch đạt. Scoped ESLint không có lỗi; có bốn warning
  (ba unused symbols cũ, một quy ước type import trong test).
- Nghiệp vụ mock có test: persistence, code trùng, namespaces đối tác, quyền/scope, parent inactive,
  bất biến, reference mới, lượng thập phân, mapping trùng/sai Plant và thay mặc định có xác nhận.
- Browser: desktop 1280×800, tablet 834×1112; tổ chức, UOM, hai tab vật tư, form kho/đối tác và nhãn VI.
  Không gửi mutation tài khoản/quyền/master qua browser; mutation master được kiểm bằng test.
- Sau full regression chỉ sửa nhãn bộ lọc Nhà máy; scoped lint và build cuối đạt.

Mong đợi: FE/mock chạy các luồng đã chốt, lỗi giữ input, không thay mặc định khi chưa xác nhận.
Thực tế: các kiểm tra FE nêu trên đạt; chưa có bằng chứng live, concurrent DB hoặc email/Auth BE.

Khi nghiệm thu live, FE phải bổ sung từng request/response thật, response trace ID, môi trường/giờ,
ID thực từ cùng lượt thử và version BE. Không dùng UUID minh họa dưới đây làm ID “đã thử”.

## §3 — Đầu ra mong đợi — Cần BE triển khai phần thiếu

### 3.1 Danh sách kết quả kiểm được

| # | Kết quả mong đợi | API liên quan | Chặn FE? |
|---|---|---|---|
| R1 | Có list/create/detail/update/activate/deactivate Supplier và Customer, lưu DB và đọc lại sau reload | API đề xuất ở 3.4 | Chặn tích hợp hai màn hình mới; không chặn mock |
| R2 | Code được trim/uppercase; trùng trong cùng danh mục/phạm vi đã thống nhất bị từ chối; Supplier và Customer cùng code vẫn hợp lệ | Create master | Chặn nghiệm thu uniqueness |
| R3 | Update không đổi code/parent; Item không đổi type/tracking; thay unit theo quyết định Q6 | PATCH master | Chặn nghiệm thu bất biến |
| R4 | Không tạo/kích hoạt Warehouse dưới Plant INACTIVE; không ghi settings cho Item/Warehouse/Plant INACTIVE | Warehouse/settings | Chặn nghiệm thu lifecycle |
| R5 | GET settings trả đúng Item, Warehouse, Plant đã authorize; không lấy kho thuộc Plant khác | GET settings | Chặn tích hợp mapping |
| R6 | Safety Stock/ROP là decimal không âm, leadTimeDays là số nguyên không âm | PUT settings | Chặn nghiệm thu validation |
| R7 | Không có hai mapping ACTIVE cùng Item+Warehouse; tối đa một default ACTIVE mỗi Item+Plant+mục đích | PUT settings | Chặn nghiệm thu default |
| R8 | Thay mặc định được xác nhận cập nhật nguyên tử; thất bại rollback cả cờ cũ/mới; giữ nguyên mục đích không thay | PUT settings, extension đề xuất | Chặn nghiệm thu concurrent/atomicity |
| R9 | Ngừng dùng là logical lifecycle, không xóa lịch sử; reference tạo mới loại INACTIVE; xem record cũ vẫn được authorize | Lifecycle/GET reference | Chặn nghiệm thu references |
| R10 | Search/filter được áp dụng trước pagination; totals đúng; không cắt reference ở 100/200 dòng đầu | List/reference | Chặn nghiệm thu danh mục lớn |
| R11 | Sai quyền/scope bị từ chối trên server, không đọc/ghi record công ty/nhà máy khác bằng ID | Tất cả endpoint | Chặn nghiệm thu security |

R2–R11 trên API hiện có: BE **đối chiếu**, chỉ code phần thiếu sau các quyết định §5.
Không coi bảng này là xác nhận rằng API hiện có đang sai.

### 3.2 Quy ước HTTP/DTO

**[Đã kiểm chứng FE adapter]** API base mặc định có context `/api`, controllers master dùng `/v1`.
Tất cả path dưới đây ghi đầy đủ `/api/v1/...`; nếu gateway/base URL khác, BE cung cấp URL triển khai.

- `Authorization: Bearer <token đã che>` cho request có auth. Không có token thật trong ví dụ.
- Request Warehouse/settings có `X-Plant-Id` đúng Plant của resource; path/body/header không được mâu thuẫn.
  PATCH/DELETE/activate Plant hiện cũng gửi Plant ID tương ứng. Không dùng header làm căn cứ authorize duy nhất.
- FE client thêm `Idempotency-Key` UUID cho POST/PUT/PATCH/DELETE. Khả năng BE replay/retention chờ Q10.
- IDs trong live DTO là UUID string; numeric ID và `legacy-*` của mock không đưa lên BE.
- Instant: ISO 8601 có timezone; `createdAt` bắt buộc với DTO master/setting hiện có;
  `updatedAt` có thể omitted/null. Không sửa ngày tạo khi sửa tên/trạng thái.
- Success có `code="SUCCESS"`, `message`; list/object command cần `result` đúng DTO.
  DELETE logical của Plant/Warehouse/Item/settings hiện FE cho phép `result` omitted/null.
  UOM activate/deactivate hiện cần trả UOM object. Không đổi cách này âm thầm.
- `errors` có thể omitted khi không có lỗi; validation error dùng `{field,message}`.
  Khi lỗi, `result` có thể omitted/null; FE phân nhánh theo `code`. Response header có `X-Trace-Id`.

**Ví dụ đề xuất envelope list** — không phải response thật:

```json
{"code":"SUCCESS","message":"Success","result":{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"first":true,"last":true}}
```

Page bắt đầu 0; mock hiện 20 record/page. GET list hỗ trợ `page`, `size`; thống nhất sort ổn định,
có tie-breaker ID để phân trang không lặp/mất dòng trên dữ liệu không đổi.
UOM collector đọc đủ trang và báo lỗi nếu metadata/content thiếu, không coi trang đầu là toàn danh mục.

### 3.3 API đã có trong source adapter FE — không khẳng định mới kiểm live

Các field viết trong body là tên wire. `C/P/W/I/S/U` lần lượt là UUID Company/Plant/Warehouse/Item/Setting/UOM.
Query chung `page,size`; Org thêm `search,status,sortBy,sortDir`; UOM thêm `search,status`.
Item master adapter hiện chỉ gửi `page,size,sortBy,sortDir`; reference adapter có `search,status,itemType`.
Thống nhất server search Item ở Q9 trước sửa adapter; không suy rằng mock filter đã là server filter.

| Method + path | Body/query thêm | result |
|---|---|---|
| GET /api/v1/companies | Query Org | Page Company |
| GET /api/v1/companies/{C}/plants | Query Org | Page Plant |
| POST /api/v1/companies/{C}/plants | code,name,timezone? | Plant |
| PATCH /api/v1/plants/{P} | name,timezone? | Plant |
| DELETE /api/v1/plants/{P} | Không body | Optional result |
| POST /api/v1/plants/{P}/activate | Không body | Plant |
| GET /api/v1/plants/{P}/warehouses | Query Org | Page Warehouse |
| POST /api/v1/plants/{P}/warehouses | code,name,type | Warehouse |
| PATCH /api/v1/warehouses/{W} | name,type | Warehouse |
| DELETE /api/v1/warehouses/{W} | Không body | Optional result |
| POST /api/v1/warehouses/{W}/activate | Không body | Warehouse |
| GET /api/v1/uoms | Query UOM | Page UOM |
| POST /api/v1/uoms | code,name,description? | UOM |
| PATCH /api/v1/uoms/{U} | name,description? | UOM |
| POST /api/v1/uoms/{U}/activate | Không body | UOM |
| POST /api/v1/uoms/{U}/deactivate | Không body | UOM |
| GET /api/v1/companies/{C}/items | Query Item | Page Item |
| POST /api/v1/companies/{C}/items | code,name,type,unit,lotTracked,serialTracked | Item |
| GET /api/v1/items/{I} | Không body | Item |
| PATCH /api/v1/items/{I} | name,unit | Item |
| DELETE /api/v1/items/{I} | Không body | Optional result |
| POST /api/v1/items/{I}/activate | Không body | Item |
| GET /api/v1/inventory/item-warehouse-settings | page,size,sortBy,sortDir,itemId?,warehouseId? + X-Plant-Id | Page Setting |
| PUT /api/v1/inventory/item-warehouse-settings | itemId,warehouseId,safetyStock,reorderPoint,leadTimeDays,defaultSupply,defaultOutput + X-Plant-Id | Setting |
| DELETE /api/v1/inventory/item-warehouse-settings/{S} | X-Plant-Id | Optional result |

Response DTO hiện FE đọc:

| Field | Kiểu | Nullable | Nguồn giá trị | UI sử dụng |
|---|---|---|---|---|
| Company.companyId / code / name / status | UUID / string / string / enum | Không | BE master Company | Công ty chỉ xem, references |
| Plant.plantId / companyId | UUID | Không | BE record/parent | ID thao tác, parent |
| Plant.code / name | string | Không | Form tạo; tên sửa được | Danh sách, form, references |
| Plant.timezone | string | Có/omitted | Form; default FE Asia/Ho_Chi_Minh | Form nhà máy |
| Warehouse.warehouseId / plantId | UUID | Không | BE record/parent | ID thao tác, lọc nhà máy |
| Warehouse.code / name / type | string / string / enum | Không | Form; type là wire của warehouseType UI | Danh sách, form, references |
| UOM.uomId / code / name | UUID / string / string | Không | BE record/form | Danh sách, lựa chọn unit |
| UOM.description | string | Có/omitted | Form | Form đơn vị |
| Item.itemId / companyId | UUID | Không | BE record/parent | Detail, references |
| Item.code / name / type / unit | string | Không | Form; unit là **UOM code**, không phải UOM UUID | Danh sách/form/thông tin |
| Item.lotTracked / serialTracked | boolean | Adapter hiện cho omitted → false; đề nghị explicit | Tracking form; serialTracked=false trong scope mới | Theo dõi lô |
| Setting.settingId / itemId / warehouseId | UUID | Không | BE mapping | Edit/deactivate |
| Setting.itemCode / itemName / warehouseCode / warehouseName | string | Không | Join master phía BE | Hiển thị mapping |
| Setting.safetyStock / reorderPoint | decimal string | Không | Form + NUMERIC(19,6) | Tồn an toàn/điểm đặt lại |
| Setting.leadTimeDays | integer | Không | Form | Thời gian cung ứng |
| Setting.defaultSupply / defaultOutput | boolean | Adapter hiện cho omitted → false; đề nghị explicit | Ý định người dùng + policy server | Kho mặc định |
| Master/Setting.status | ACTIVE hoặc INACTIVE | Không | Lifecycle server | Badge, eligibility |
| Master/Setting.createdAt | ISO instant | Không | Server lúc tạo | Dữ liệu ổn định/historical |
| Master/Setting.updatedAt | ISO instant | Có/omitted | Server lúc sửa | Metadata |

Warehouse type: RAW_MATERIAL, FINISHED_GOODS, WIP, QUALITY, SCRAP, GENERAL.
Item trong scope: RAW_MATERIAL, WIP, FINISHED_GOOD, CONSUMABLE; NONE/LOT trên UI ánh xạ
`lotTracked=false/true`, `serialTracked=false`. Không gửi `sku/category/trackingMethod/fields` của mock.
Plant address không có trong form/API hiện bàn giao; không tự thêm field này vào BE.

Giới hạn **[Đã kiểm chứng FE]**: code master `^[A-Z0-9._-]+$`, tối đa 100; name tối đa 255;
UOM code `^[A-Z0-9_]+$`, tối đa 20, name tối đa 100, description 500.
Plant/Warehouse form API hiện yêu cầu code/name tối thiểu 3; mock chung tối thiểu 1.
Item code/name tối thiểu 1, unit tối đa 30; timezone API form tối đa 100.
BE chốt giới hạn theo Q11; đây là khác biệt source đã thấy, không phải lỗi BE đã tái hiện.

### 3.4 Supplier/Customer — API và DTO đề xuất mới

**[Đề xuất BE, chưa có adapter live cho hai danh mục]**. Là danh mục Company; BE authorize
Company từ path/record. Không gán Company bằng Plant hiện tại và không đổi Company khi sửa.
Thiếu field/path/quyền dưới đây phải trả lời §5 trước tích hợp.

| Method + path đề xuất | Query/body | result | Phân trang | Người được gọi |
|---|---|---|---|---|
| GET /api/v1/companies/{C}/suppliers | search?,status?,page=0,size=20,sortBy=code,sortDir=asc | Page Supplier | Có | SUPPLIER_READ + COMPANY C/GLOBAL |
| POST /api/v1/companies/{C}/suppliers | code,name,contactName?,email?,phone?,address?,notes? | Supplier | Không | SUPPLIER_MANAGE + COMPANY C/GLOBAL |
| GET /api/v1/suppliers/{supplierId} | Không body | Supplier | Không | READ + Company record |
| PATCH /api/v1/suppliers/{supplierId} | name,contactName?,email?,phone?,address?,notes? | Supplier | Không | MANAGE + Company record |
| POST /api/v1/suppliers/{supplierId}/activate | Không body | Supplier | Không | MANAGE + Company record |
| POST /api/v1/suppliers/{supplierId}/deactivate | Không body | Supplier | Không | MANAGE + Company record |
| GET /api/v1/companies/{C}/customers | Query như suppliers | Page Customer | Có | CUSTOMER_READ + COMPANY C/GLOBAL |
| POST /api/v1/companies/{C}/customers | Body như suppliers | Customer | Không | CUSTOMER_MANAGE + COMPANY C/GLOBAL |
| GET /api/v1/customers/{customerId} | Không body | Customer | Không | READ + Company record |
| PATCH /api/v1/customers/{customerId} | Body PATCH như suppliers | Customer | Không | MANAGE + Company record |
| POST /api/v1/customers/{customerId}/activate | Không body | Customer | Không | MANAGE + Company record |
| POST /api/v1/customers/{customerId}/deactivate | Không body | Customer | Không | MANAGE + Company record |

READ/MANAGE viết ngắn trong bảng đại diện wire `PERM_SUPPLIER_READ/MANAGE` và
`PERM_CUSTOMER_READ/MANAGE`, không phải role name. Mutation có Idempotency-Key.
Create mặc định ACTIVE; lifecycle qua command riêng. PATCH không dùng để sửa code/status/companyId.

| Field đề xuất | Kiểu | Nullable | Nguồn/giới hạn | UI sử dụng |
|---|---|---|---|---|
| supplierId hoặc customerId | UUID | Không | Server tạo, field tùy loại | Edit/detail/status |
| companyId | UUID | Không | Parent create path, immutable | Phạm vi dữ liệu |
| code | string | Không | Form, trim/uppercase, 1–100, regex master | Mã danh mục, tìm kiếm |
| name | string | Không | Form, trim, 1–255 | Tên, tìm kiếm |
| contactName | string | Có | Form người liên hệ, tối đa 255 | Form; mock hiện tên nội bộ contact |
| email | string | Có | Form, tối đa 255, đúng email khi có | Form |
| phone | string | Có | Form, tối đa 255; không ép số vì có +/0 đầu | Form |
| address | string | Có | Form, tối đa 500 | Form |
| notes | string | Có | Form, tối đa 500 | Form |
| status | ACTIVE/INACTIVE | Không | BE lifecycle | Danh sách/badge |
| createdAt | ISO instant | Không | Server tạo, immutable | Metadata |
| updatedAt | ISO instant | Có | Server sửa | Metadata |

**[Đề xuất]** Optional text: POST omitted/null nghĩa không có; PATCH omitted nghĩa giữ giá trị,
null nghĩa xóa giá trị. Chuỗi rỗng sau trim chuyển null. Không có email uniqueness toàn hệ thống
trong yêu cầu này. Code unique theo Company + loại danh mục, kể cả record INACTIVE.
Form mock hiện giữ optional text bằng chuỗi rỗng; adapter tương lai phải map sang contract đã chốt.

### 3.5 Bộ dữ liệu và request/response minh họa

**Tất cả UUID dưới đây là fixture đề xuất, chưa tồn tại trên BE.** Khi chạy live lấy ID trả về thật,
ghi mapping cùng lượt chạy, không gửi nguyên UUID minh họa nếu server tự sinh.

| Ký hiệu | UUID minh họa | Dữ liệu |
|---|---|---|
| C1 | 11111111-1111-4111-8111-111111111111 | Công ty demo đã được BE seed |
| P1 | 22222222-2222-4222-8222-222222222222 | PLT-DEMO, timezone Asia/Ho_Chi_Minh |
| W1 | 33333333-3333-4333-8333-333333333331 | WH-DEMO-01, RAW_MATERIAL, thuộc P1 |
| W2 | 33333333-3333-4333-8333-333333333332 | WH-DEMO-02, GENERAL, thuộc P1 |
| I1 | 44444444-4444-4444-8444-444444444444 | RM-DEMO-01, RAW_MATERIAL, unit KG, lotTracked=true |
| S1 | 55555555-5555-4555-8555-555555555551 | I1+W1, ACTIVE |
| S2 | 55555555-5555-4555-8555-555555555552 | I1+W2, ACTIVE |
| Supplier1 | 66666666-6666-4666-8666-666666666666 | PARTNER-01, thuộc C1 |

**Ví dụ đề xuất tạo Supplier**, không phải request/response thật:

```http
POST /api/v1/companies/11111111-1111-4111-8111-111111111111/suppliers
Authorization: Bearer <redacted>
Content-Type: application/json
Idempotency-Key: 77777777-7777-4777-8777-777777777777
```

```json
{"code":" partner-01 ","name":"Nha cung cap demo","contactName":"Lien he demo","email":"supplier@example.com","phone":"+84 000000000","address":null,"notes":null}
```

HTTP 201 đề xuất, response ID thực do BE cấp (ID/instant dưới đây chỉ minh họa):

```json
{"code":"SUCCESS","message":"Success","result":{"supplierId":"66666666-6666-4666-8666-666666666666","companyId":"11111111-1111-4111-8111-111111111111","code":"PARTNER-01","name":"Nha cung cap demo","contactName":"Lien he demo","email":"supplier@example.com","phone":"+84 000000000","address":null,"notes":null,"status":"ACTIVE","createdAt":"2026-10-12T12:00:00Z"}}
```

Tạo Customer với cùng code `PARTNER-01` trong C1 được phép; Supplier thứ hai cùng code bị conflict.
PATCH tên không gửi code/companyId. Customer DTO thay `supplierId` bằng `customerId`.

**Ví dụ create Item theo adapter hiện có**:

```http
POST /api/v1/companies/11111111-1111-4111-8111-111111111111/items
Authorization: Bearer <redacted>
Content-Type: application/json
Idempotency-Key: 77777777-7777-4777-8777-777777777778
```

```json
{"code":"RM-DEMO-01","name":"Nguyen lieu demo","type":"RAW_MATERIAL","unit":"KG","lotTracked":true,"serialTracked":false}
```

BE cần UOM KG ACTIVE trước khi tạo; response Item chứa itemId,companyId, các field body,
status ACTIVE, createdAt và optional updatedAt, bọc envelope. UOM là code, không thay bằng UUID.

### 3.6 Defaults, lượng và lifecycle

**[Đã kiểm chứng FE mock]** Một mapping Item+Warehouse được giữ khi ngừng dùng; lưu lại mapping
đó kích hoạt lại. Pair đã có không tạo record thứ hai. Default supply/output tối đa một mỗi purpose
trong Item+Plant; có thể cùng một kho cho cả hai. Thiếu default phải hiển thị “chưa thiết lập”.

Safety Stock là ngưỡng tồn an toàn cấu hình; ROP là điểm đặt lại cấu hình. Giai đoạn này **không**
tự tính ROP từ Safety Stock/lead time. Ví dụ Safety Stock=75, ROP=100, lead time=5 ngày được lưu
nguyên như nhập; không sinh 175, không tự suy lượng mua. NUMERIC(19,6), chuỗi 6 chữ số thập phân;
FE làm tròn HALF_UP tối đa 6 số lẻ, tối đa 13 số nguyên; BE xác nhận Q12.

**Khoảng trống adapter hiện có:** PUT settings hiện gửi hai cờ nhưng chưa có `replaceDefaults`.
Confirmation live cũ cũng không chứng minh backend đã có conflict/atomicity. Không gửi thêm field
đề xuất dưới đây vào API đang chạy trước BE chốt/publish.

**[Đề xuất BE extension]** PUT giữ path hiện có, thêm `replaceDefaults?: boolean`, mặc định false.
false + default đang ở mapping khác ⇒ HTTP 409 `DEFAULT_CONFLICT`, không đổi dữ liệu.
true ⇒ trong cùng transaction bỏ default của peer cùng Item+Plant+cùng purpose, lưu target;
không đổi purpose khác. Server lock/constraint bảo đảm không có hai default khi request đồng thời.
Một mapping cùng Item/Warehouse được PUT lại là upsert theo pair; status ACTIVE sau thành công.

Ví dụ trước: S1 supply=true, output=true; S2 supply=false, output=false. Yêu cầu chuyển supply
sang S2, giữ output ở S1. **Request đề xuất** sau xác nhận:

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
Content-Type: application/json
X-Plant-Id: 22222222-2222-4222-8222-222222222222
Idempotency-Key: 77777777-7777-4777-8777-777777777779
```

```json
{"itemId":"44444444-4444-4444-8444-444444444444","warehouseId":"33333333-3333-4333-8333-333333333332","safetyStock":"30.000000","reorderPoint":"50.000000","leadTimeDays":7,"defaultSupply":true,"defaultOutput":false,"replaceDefaults":true}
```

HTTP 200 đề xuất:

```json
{"code":"SUCCESS","message":"Success","result":{"settingId":"55555555-5555-4555-8555-555555555552","itemId":"44444444-4444-4444-8444-444444444444","itemCode":"RM-DEMO-01","itemName":"Nguyen lieu demo","warehouseId":"33333333-3333-4333-8333-333333333332","warehouseCode":"WH-DEMO-02","warehouseName":"Kho demo 02","safetyStock":"30.000000","reorderPoint":"50.000000","leadTimeDays":7,"defaultSupply":true,"defaultOutput":false,"status":"ACTIVE","createdAt":"2026-10-12T12:00:00Z"}}
```

Sau refetch P1/I1: S1 supply=false, output=true; S2 supply=true, output=false. Nếu transaction thất bại,
cả hai giữ trạng thái trước. FE hủy confirmation thì không gửi request replacement.
Với concurrent confirmed replacements, Q8 đề xuất serialize; hai request có thể thành công theo thứ tự,
cuối cùng chỉ một supply default; FE refetch. Chưa thêm compare-and-swap/version token vào contract.

**[Đề xuất BE]** Ngừng setting xóa hai cờ của chính setting; không tự chọn peer thay thế.
Ngừng Warehouse/Plant khiến mapping đó không eligible dù cờ được lưu lịch sử; BE không trả kho inactive
làm default cho giao dịch mới. Mock hiện không cascade mapping khi ngừng Warehouse;
lifecycle active-child của Plant và cascade/default restoration còn cần Q4/Q5/Q7.
UOM INACTIVE không dùng tạo Item mới; sửa tên Item cũ giữ unit INACTIVE không được buộc đổi unit.

### 3.7 Nghiệm thu live — tất cả đang chờ

BE công bố version/OpenAPI và môi trường trước khi chạy. Dùng ID thật trả từ cùng lượt thử C1→P1→W1/W2→KG→I1,
không dùng ID minh họa làm evidence. Mỗi case ghi request, response, trace, môi trường/giờ, record IDs.

| ID | Dữ liệu/thao tác | Kết quả đúng/sai cần kiểm | Trạng thái |
|---|---|---|---|
| A01 | Tạo chuỗi parent/UOM/Item/partners rồi reload | ID/parent/unit/status không đổi; GET đọc đúng dữ liệu đã lưu | Chưa chạy |
| A02 | Supplier PARTNER-01 và supplier ` partner-01 ` cùng C1 | Request thứ hai conflict; chỉ một supplier | Chưa chạy |
| A03 | Customer PARTNER-01 cùng C1 | Tạo được, độc lập Supplier | Chưa chạy |
| A04 | Hai create đồng thời cùng supplier code | Chỉ một record; lỗi conflict có code/trace | Chưa chạy |
| A05 | PATCH đổi code/Plant parent/type/tracking/khác unit | Từ chối trường bất biến theo quyết định đã chốt, không đổi một phần | Chưa chạy |
| A06 | Create/activate Warehouse dưới Plant inactive | Bị từ chối, không tạo/kích hoạt kho | Chưa chạy |
| A07 | Deactivate Plant còn Warehouse active | Kết quả theo Q4; không cascade ngoài policy | Chưa chạy |
| A08 | UOM inactive: tạo Item mới; sửa tên Item cũ | Tạo mới bị chặn; sửa tên giữ UOM cũ được phép | Chưa chạy |
| A09 | Settings kho khác Plant/header mâu thuẫn | Bị từ chối; không thay mapping ở cả hai Plant | Chưa chạy |
| A10 | Safety Stock=-1, ROP=-1, NaN; leadTimeDays=1.5/-1; overflow | Validation lỗi đúng field, không persist | Chưa chạy |
| A11 | Lưu 0.000001 và 75.123456 | GET trả chính xác, không mất số lẻ | Chưa chạy |
| A12 | PUT lại cùng Item+Warehouse, gồm mapping inactive | Theo upsert Q7, không thêm mapping duplicate | Chưa chạy |
| A13 | Chuyển S1→S2 supply chưa/đã confirmed | Chưa: conflict không đổi; đã: S1 supply=false/output=true, S2 supply=true | Chưa chạy |
| A14 | Hai confirmed replacements concurrent và một transaction lỗi | Không có hai default; rollback transaction lỗi; refetch đúng cuối | Chưa chạy |
| A15 | Deactivate setting/warehouse, rồi chọn reference/default mới | Setting clear cờ khi deactivate; Warehouse inactive không eligible; không tự chọn kho | Chưa chạy |
| A16 | Xem chứng từ cũ dùng master inactive | Dữ liệu/snapshot lịch sử giữ nguyên; không mở tạo mới bằng inactive reference | Chưa chạy |
| A17 | Chỉ READ gọi mutation; user Company/Plant khác gọi ID trực tiếp | Server deny, không đọc/ghi chéo scope | Chưa chạy |
| A18 | Assignment hết hạn/thu hồi sau khi mở form | BE deny request mới; FE giữ input và refetch profile theo contract Auth | Chưa chạy |
| A19 | 125 UOM, tìm bản ghi ở trang cuối; filter status | Tìm được reference ACTIVE ngoài 100 đầu; tổng/trang đúng | Chưa chạy |
| A20 | Search code/name kết hợp status/Item type/tracking | Search trước pagination; totals phản ánh bộ lọc theo Q9 | Chưa chạy |
| A21 | Retry mutation cùng Idempotency-Key sau timeout | Một tác động, replay theo Q10; cùng key/body khác bị conflict | Chưa chạy |
| A22 | Optional partner PATCH omitted/null và sai email | Omitted giữ, null clear theo Q3; email sai giữ input, server không persist | Chưa chạy |

Rollout đề nghị: (1) trả lời §5 và đối chiếu existing APIs; (2) publish OpenAPI/examples/version;
(3) triển khai phần còn thiếu, permission catalog và role assignment được duyệt;
(4) FE sửa adapter sau PO đồng ý, không gửi mock IDs; (5) chạy A01–A22, ghi evidence;
(6) mới đóng tích hợp giai đoạn 2. Auth/email giai đoạn 1 nghiệm thu riêng, không bị đánh dấu hoàn tất ở đây.

## §4 — Hợp đồng lỗi mong đợi

**Toàn bộ HTTP/code dưới đây là [Đề xuất BE], không phải lỗi BE đã quan sát.** Code mock có thể khác:
mock dùng INVALID_INPUT cho nhiều validation, DUPLICATE_CODE cho pair trùng; không yêu cầu BE
copy nguyên lỗi mock. Nếu BE có code hiện hành tương đương, trả mapping/OpenAPI để FE thống nhất.

| Tình huống | HTTP | code mong đợi | FE hiển thị/hành vi |
|---|---|---|---|
| Field thiếu/sai format/enum, lượng âm, lead time lẻ/âm | 400 | VALIDATION_ERROR | Lỗi tại field; giữ input |
| Timezone không hợp lệ | 400 | INVALID_TIMEZONE | Kiểm tra múi giờ; giữ form |
| Code trùng sau normalize | 409 | DUPLICATE_CODE | Mã đã tồn tại; giữ các ô khác |
| Field bất biến bị đổi | 409 | IMMUTABLE_FIELD | Không cho đổi trường; refetch record |
| Plant cha inactive | 409 | INACTIVE_PARENT | Nhà máy đã ngừng dùng; chọn lại khi tạo |
| Reference Item/UOM/Warehouse inactive | 409 | INACTIVE_REFERENCE | Không dùng reference mới; giữ input |
| Deactivate Plant còn con active theo Q4 | 409 | ACTIVE_CHILDREN | Ngừng kho trước hoặc theo policy được chốt |
| Default đã ở peer, không có ý định replace | 409 | DEFAULT_CONFLICT | Refetch mapping rồi confirmation thay thế |
| Header/path/body Plant mâu thuẫn | 400 | PLANT_CONTEXT_MISMATCH | Yêu cầu chọn lại context; không tự retry mutation |
| Không có quyền hoặc scope hợp lệ | 403 | PERMISSION_DENIED | Không đủ quyền; cập nhật profile/context |
| ID không tồn tại | 404 | NOT_FOUND | Bản ghi không tồn tại; quay danh sách/refetch |
| Key replay với body khác | 409 | IDEMPOTENCY_CONFLICT | Không gửi lại cùng key với nội dung mới |
| Lỗi hệ thống/transaction | 500 | INTERNAL_ERROR | Lỗi lưu, giữ input; kèm trace cho điều tra |

Không tiết lộ record khác Company/Plant trong message; BE có thể chuẩn hóa deny/not-found chống
enumeration nếu đã có policy, ghi rõ Q13. Lỗi session 401 dùng contract Auth giai đoạn 1 hiện hành,
không gộp tất cả thành “không có quyền” hay tự đổi mã Auth trong task này.

**Response lỗi đề xuất**:

```json
{"code":"VALIDATION_ERROR","message":"Validation failed","errors":[{"field":"leadTimeDays","message":"Must be a non-negative integer"}]}
```

X-Trace-Id lấy từ response header thật khi thử; không có trace thật để điền trong tài liệu này.
BE ghi field wire như `contactName`, `leadTimeDays`; adapter FE map về nhãn nghiệp vụ.
Retry network chưa biết kết quả chỉ an toàn sau thống nhất idempotency; không tự tạo key mới cho
cùng ý định chưa rõ đã commit. Request sửa nội dung là ý định mới, key mới.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi mục là một quyết định; chưa có phản hồi BE.
“CHẶN” nghĩa chặn sửa adapter/nghiệm thu live phần liên quan, **không chặn FE/mock hay giai đoạn 3 đề xuất**.
Owner trả lời: BE; quyết định business/policy có PO cùng chốt.

1. Q1 — Chấp nhận path/DTO Supplier–Customer ở 3.4? FE nghiêng về có, dùng convention master hiện tại — **CHẶN** adapter hai danh mục.
2. Q2 — Chấp nhận bốn mã quyền Supplier/Customer ở 1.5? FE nghiêng về có, tách quyền từng danh mục — **CHẶN** adapter/authorization.
3. Q3 — Chấp nhận optional text PATCH omitted=giữ, null=xóa? FE nghiêng về có, tránh mất dữ liệu không gửi — **CHẶN** PATCH đối tác.
4. Q4 — Chặn deactivate Plant khi còn Warehouse ACTIVE? FE nghiêng về có, không cascade ngoài ý định người dùng — **CHẶN** lifecycle live.
5. Q5 — Ngừng Warehouse chỉ làm mappings/defaults không eligible và giữ flags lịch sử thay vì tự chuyển default? FE nghiêng về có, không chọn kho thay người dùng — **CHẶN** lifecycle/default live.
6. Q6 — Giữ unit của Item bất biến qua PATCH trong giai đoạn này? FE nghiêng về có, chưa triển khai quy đổi; FE form hiện khóa nhưng adapter cũ vẫn gửi unit không đổi — **CHẶN** sửa Item live.
7. Q7 — PUT settings upsert theo Item+Warehouse và kích hoạt lại mapping INACTIVE? FE nghiêng về có, giữ một pair và lịch sử ID — **CHẶN** settings live.
8. Q8 — Chấp nhận replaceDefaults=false/conflict, true/transaction serialized như 3.6? FE nghiêng về có, giữ mỗi purpose tối đa một mặc định; chưa yêu cầu compare-and-swap — **CHẶN** default live.
9. Q9 — Chấp nhận Item list query search,status,type,lotTracked để lọc trên server? FE nghiêng về có; hiện reference dùng itemType, BE cần trả mapping một tên thống nhất — **CHẶN** đồng bộ filter Item live.
10. Q10 — BE hỗ trợ replay cùng Idempotency-Key/body cho các mutation master? FE nghiêng về có, tránh duplicate sau timeout; BE cung cấp thời hạn giữ key trong câu trả lời — **CHẶN** nghiệm thu retry.
11. Q11 — Giữ Plant/Warehouse code/name tối thiểu 3 theo API form hiện có? FE nghiêng về có; mock chung tối thiểu 1 là khác biệt đã ghi, cần FE đồng bộ sau chốt — **CHẶN** validation live.
12. Q12 — Chấp nhận decimal NUMERIC(19,6) và HALF_UP tối đa 6 số lẻ? FE nghiêng về có, phù hợp serializer hiện có — **CHẶN** nghiệm thu lượng.
13. Q13 — Giữ code/HTTP ở §4 hoặc cung cấp mapping tương đương theo OpenAPI hiện hành? FE nghiêng về giữ code đã có nếu tương đương, tránh breaking change — **CHẶN** lỗi/acceptance live.
14. Q14 — Unique code theo Company cho Plant/Item/partners, theo Plant cho Warehouse, UOM toàn hệ thống? FE nghiêng về có, phù hợp ownership; mock hiện unique toàn danh mục trong một công ty demo — **CHẶN** duplicate live.
15. Q15 — Giữ GLOBAL cho tổ chức/UOM, Item access theo Plant hợp lệ, partners theo Company/GLOBAL như 1.5? FE nghiêng về giữ FE policy hiện tại đến khi chốt GLOBAL chung với giai đoạn 1 — **CHẶN** scope live.
16. Q16 — Khi activate Warehouse, mappings cũ không tự được khôi phục thành default nếu đã có default khác? FE nghiêng về có, tránh hai default hoặc thay lựa chọn mới — **CHẶN** activate/default live.

Không có câu trả lời không được hiểu là đồng ý. Nếu lựa chọn khác, BE ghi behavior + ví dụ JSON;
FE xin PO trước khi sửa source. Câu trả lời policy không phải ticket yêu cầu viết lại code ngay.

## §6 — FE đã tự xử lý

**[Đã kiểm chứng FE]**:

- Chuẩn hóa code/name, kiểm tra duplicate, parent inactive, UOM inactive, bất biến và lượng;
  giới hạn mock không thay cho server validation/transaction.
- Giữ form khi lỗi; READ-only không có nút lưu/status; confirmation critical lifecycle/default.
- Search/filter/pagination mock; searchable UOM/Warehouse. UOM API create Item đọc đủ trang,
  lỗi metadata/incomplete catalog báo retry, không dùng danh mục bị cắt làm dữ liệu đầy đủ.
- Đổi Plant remount workspace, đóng pending form/detail, settings chỉ theo Plant hiện tại.
- Sau mutation invalidate/refetch shared queries. Ngày tạo Item và giá trị planning legacy giữ lại;
  không chuyển `isPreferred` thành hai cờ mặc định, không gửi field planning chỉ thuộc mock lên BE.
- Local mock có persistence version 1, không phải migration DB và không gửi numeric/legacy IDs lên API.
- Partner API mode báo chưa sẵn sàng; không fallback live → mock. Existing adapters được giữ.
- Toàn bộ proposed wire extension/default policy ở tài liệu chưa có adapter mới; sau BE chốt cần
  FE implementation được PO duyệt và live acceptance. Chốt FE/mock không đóng phần tích hợp.

Không có lớp FE nào thay BE authorization, uniqueness constraint, locking, soft lifecycle hoặc audit.
Master/configuration không tự tăng/giảm On Hand/Reserved/Available, không post ledger/WIP.
Settings ảnh hưởng lựa chọn/hoạch định tương lai; không tính lại hay sửa snapshot chứng từ đã post.
BE xác định audit theo nền tảng hiện có; không kèm API audit mới trong phạm vi này.

## §7 — Tham chiếu

Các tên dưới đây để đối chiếu lịch sử; tài liệu này đã chứa thông tin cần triển khai, không cần mở repo FE.

- `BE_HANDOFF_GD_01_2026-10-09.md` — bàn giao Auth/tài khoản/quyền/scope trước đó; GLOBAL/session policy còn chờ, task này không thay thế.
- `CAPSTONE3_GD2_FE_PROPOSAL_2026-10-12.md` — phạm vi FE/mock được PO duyệt trước triển khai.
- `CAPSTONE3_GD2_FE_REVIEW_2026-10-12.md` — FE screens và ranh giới mock/live, nay PO đã chốt.
- `FETaskDeliveryRules.md` — quy tắc tạo tài liệu BE được PO áp dụng; đã dùng cấu trúc §0–§7 cho bản này.

### Checklist trước giao

- Đủ §0–§7; có ngoài phạm vi, màn hình mới/đã sửa, kết quả đúng/sai kiểm được.
- Existing source / proposed contract / live chưa thử được phân biệt; không gắn lỗi cho BE thiếu evidence.
- Bảng field đủ kiểu/nullability/nguồn/UI, endpoint đủ method/path/body/result/pagination/quyền.
- §5 riêng, chỉ xác nhận, một quyết định/mục và ảnh hưởng tích hợp rõ.
- JSON/request minh họa có nhãn; không token/secret thật, không trace/ID live tự tạo.
- Bằng chứng lỗi 5 mảnh và version BE: **không áp dụng báo lỗi ở bản này; chưa chạy live**.
- Tin nhắn 4 dòng kèm file đã chuẩn bị; PO tự gửi BE. Sau gửi lưu đúng phiên bản;
  sau BE hoàn thành chạy A01–A22 rồi mới đóng tích hợp, chưa đạt trả lời cùng task/bản `_v2`.

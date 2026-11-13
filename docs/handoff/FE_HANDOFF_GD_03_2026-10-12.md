# Giai đoạn 3 — Bàn giao Backend: Nhập Excel dữ liệu nền

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock đã hoàn thành nhập Excel Vật tư/Nhà cung cấp/Khách hàng; cần BE cung cấp validate, commit nguyên lô và lịch sử có kiểm soát quyền.
- Ảnh hưởng: màn hình Nhập dữ liệu và ba danh mục; hiện demo/mock dùng được, luồng nhập chưa dùng được ở chế độ API.
- Mức ưu tiên: P1 — luồng nhập file bị chặn ở chế độ API; các chức năng khác và lộ trình FE/mock vẫn tiếp tục được.

**Bản:** v1 · **Ngày dự kiến gửi:** 12/10/2026 · **FE triển khai:** Session 295 · **Bàn giao:** Session 296.
**PO:** người phụ trách sản phẩm trong phiên làm việc, yêu cầu “xuất tài liệu cho BE” sau bản FE hoàn tất;
ghi nhận chuyển giai đoạn 3 FE/mock sang bàn giao. Không đồng nghĩa nghiệm thu API thật.
**FE/BE owner cá nhân:** chưa được chỉ định trong phiên này.
**Trạng thái:** tài liệu đã chuẩn bị để PO gửi; chưa gửi bên ngoài, chưa chốt contract hoặc nghiệm thu BE.
Nếu gửi ngày khác, đổi ngày tên file trước lần gửi đầu; sửa sau khi gửi dùng `_v2`, `_v3`.

Nhãn trong tài liệu:

- **[Đã kiểm chứng FE]**: source, test hoặc hành vi demo/mock đã quan sát.
- **[Đề xuất BE]**: yêu cầu/DTO/path/policy cần BE thống nhất trước tích hợp, chưa phải API đã có.
- **[Giả định]**: dependency hoặc điều kiện chưa được xác minh trên môi trường BE.
- **[Chưa kiểm chứng live]**: chưa chạy request trên server thật. Các ví dụ JSON đều là dữ liệu đề xuất.

## §1 — Phạm vi

### 1.1 Màn hình mới và đã sửa

| Loại | Màn hình | Hành vi người dùng thấy |
|---|---|---|
| Mới | Nhập dữ liệu | Chọn Vật tư/Nhà cung cấp/Khách hàng; tải mẫu trống hoặc file minh họa riêng; upload/kéo thả; kiểm tra; xem trước; xác nhận; kết quả |
| Mới | Lịch sử nhập | Tìm tên file, lọc danh mục/trạng thái, phân trang, mở lần nhập của chính người dùng trong phạm vi hiện tại |
| Mới | Chi tiết lần nhập | Tổng/hợp lệ/lỗi, bảng theo dòng Excel; xem kết quả hoặc tiếp tục xác nhận lần nhập sẵn sàng |
| Sửa | Danh mục vật tư | Nút Nhập Excel mở đúng loại; nhập thành công cập nhật danh mục và tham chiếu vật tư |
| Sửa | Nhà cung cấp | Nút Nhập Excel; xem bản ghi mới sau nhập |
| Sửa | Khách hàng | Cùng luồng nhập như Nhà cung cấp; không gian mã riêng |
| Sửa | Sidebar | Mục Nhập dữ liệu chỉ hiện khi đủ quyền xem + quản lý ít nhất một loại hỗ trợ |

**[Đã kiểm chứng FE]** Ba màn hình mới là tab/trạng thái trên route FE `/admin/data-import`;
không có ba wizard riêng. Tên nghiệp vụ có VI/EN, không hiển thị mã permission/endpoint cho người dùng.
Bảng nhiều cột cuộn trong khung, phân trang 20 dòng; thẻ/bộ lọc trắng, bóng nhẹ, nút xanh.

### 1.2 Trong và ngoài phạm vi

**Trong phạm vi:** `.xlsx` đúng mẫu phiên bản `omniplant-import-v1`; ba loại `items`, `suppliers`,
`customers`; tạo mới ACTIVE; validate theo dòng/cột; confirmation; atomic commit; chống trùng/retry;
history/detail; quyền/phạm vi; mapping master; audit batch và truy xuất record được tạo.

**Ngoài phạm vi:** `.csv`, `.xls`, `.xlsm`; cập nhật/upsert/xóa master qua file; sửa từng dòng trong UI;
partial import/bỏ qua dòng lỗi; import Company/Plant/Warehouse/UOM; stock/lot/tồn đầu kỳ;
BOM/routing; giá/công nợ/thanh toán; PO/GRN/chứng từ đã ghi sổ; SERVICE, Serial Tracking;
thiết lập kho hoặc mặc định kho; tài khoản đăng nhập; thay Auth/email giai đoạn 1; UI quản trị đa công ty.
Không đổi semantics hoặc phá vỡ API master hiện có để thêm import.

**Môi trường:** demo/mock, Next.js production build cục bộ ngày 12/10/2026, mốc Session 295.
Master và batch lưu ở trình duyệt, không phải DB server hoặc hệ thống đa người dùng.
**[Chưa kiểm chứng live]** Không gọi API import trong phiên này; chưa xác minh phiên bản BE đang deploy.
Không kết luận BE chưa có endpoint chỉ vì FE chưa có adapter. BE rà API hiện có và trả OpenAPI phù hợp.
Các path §3 là đề xuất đầy đủ, có `/api/v1`; không cần BE truy cập repo/máy FE để đọc tài liệu này.

### 1.3 Luồng để BE tái hiện

1. Người dùng đã đăng nhập, chọn công ty/nhà máy hợp lệ, có cả quyền đọc và quản lý loại danh mục.
2. Chọn loại, tải mẫu trống hoặc minh họa riêng. Mẫu Vật tư có danh sách UOM đang hoạt động.
3. Giữ sheet/cột/metadata; điền từ dòng 2. Mã và điện thoại dạng Text, không dùng công thức.
4. Chọn file, bấm Kiểm tra. **Không tạo master ở bước validate**, kể cả toàn bộ dòng hợp lệ.
5. UI hiển thị tổng số dòng có dữ liệu/hợp lệ/lỗi, bảng phân trang và lỗi theo dòng/cột.
   Có lỗi: nút nhập bị khóa; sửa file gốc và upload lại. Không sửa inline hoặc commit phần hợp lệ.
6. Không lỗi: bấm Nhập dữ liệu, confirmation nêu số bản ghi sẽ tạo, không cập nhật record đã có.
   Hủy giữ bản xem trước, không tạo dữ liệu. Đổi file/loại khi đang xem trước cần xác nhận bỏ bản đang xem.
7. Xác nhận: server kiểm tra lại người dùng/quyền/scope, UOM và mã hiện tại; tất cả hợp lệ mới tạo cả lô.
8. Thành công: hiện số bản ghi đã tạo, link mở danh mục, nút nhập file mới và lịch sử.
   Reload/mất response rồi thử lại **cùng batch** phải trả lại kết quả đã lưu, không tạo thêm master.
9. History/detail theo owner và phạm vi; đổi người/công ty/nhà máy đóng trạng thái đang mở trên FE.

### 1.4 Quyền và chuyển trạng thái

**[Đã kiểm chứng FE]** Không thêm permission import riêng. Cần cả READ và MANAGE của cùng loại;
không ghép READ Vật tư với MANAGE Khách hàng để cho phép nhập. Supplier/Customer permissions là
mã đã đề xuất ở giai đoạn 2, **chưa xác nhận đã được BE deploy**.

| Loại | Permission | Scope FE và yêu cầu BE đối chiếu |
|---|---|---|
| items | PERM_ITEM_READ + PERM_ITEM_MANAGE | Có Plant được chọn; evaluator FE chấp nhận GLOBAL, PLANT đúng hoặc COMPANY bao phủ Plant; cần chốt Q2 |
| suppliers | PERM_SUPPLIER_READ + PERM_SUPPLIER_MANAGE | COMPANY đúng hoặc GLOBAL; batch/lịch sử theo Company, không theo Plant |
| customers | PERM_CUSTOMER_READ + PERM_CUSTOMER_MANAGE | COMPANY đúng hoặc GLOBAL; batch/lịch sử theo Company, không theo Plant |

Item master thuộc Company; Plant là ngữ cảnh authorization/import, không biến Item thành danh mục riêng
của Plant. Mock chỉ có công ty MVP; không dùng kết quả mock làm bằng chứng uniqueness đa công ty.

**[Đề xuất BE]** Validate/list/detail/commit đều yêu cầu quyền ở bảng trên. Owner lấy từ phiên/token,
không tin actorId từ body. Kiểm tra lại quyền còn hiệu lực ở từng request, kể cả trả receipt đã commit;
thu hồi/hết hạn assignment thì không được tiếp tục hoặc đọc lại batch. Không cho role name bỏ qua scope.
Truy cập batch ID của owner/công ty/Plant khác trả NOT_FOUND để không lộ sự tồn tại.
Confirmation của chủ batch là xác nhận nhập, không phải luồng Manager phê duyệt hai người;
không có yêu cầu tự phê duyệt riêng hoặc permission APPROVE trong phạm vi này.

| Thao tác | Trước → sau | Kiểm tra | Tác động master |
|---|---|---|---|
| File sai định dạng/template/giới hạn | Không có batch → không có batch | File-level | 0 |
| Validate file đọc được, có dòng lỗi | Không có → INVALID | Quyền/scope + từng dòng | 0 |
| Validate không lỗi | Không có → READY | Quyền/scope + từng dòng | 0 |
| Người dùng hủy confirmation | READY → READY | Không gửi commit | 0 |
| Commit đủ điều kiện | READY → COMMITTED | Tái kiểm tra + transaction | Tạo đúng N ACTIVE |
| Commit gặp stale domain | READY → INVALID | UOM/code hiện tại đã khác | 0 |
| Retry batch đã commit | COMMITTED → COMMITTED | Authorization trước khi trả receipt | 0 thêm |
| Commit lỗi DB | READY → READY | Rollback; không ghi receipt thành công | 0 |

**[Đề xuất BE]** INVALID phải sửa file và validate batch mới; không có API sửa nội dung batch.
Batch đã commit không có command undo/delete trong task này. Master dùng lifecycle thông thường.

## §2 — Bằng chứng tái hiện

**Không có báo lỗi BE hoặc live request ở lần bàn giao này.** Năm mảnh bằng chứng defect:

| Mảnh | Tình trạng |
|---|---|
| Request thật | Không có — FE import đang mock |
| Response thật/HTTP | Không có |
| X-Trace-Id | Không có; không tự tạo ID thay bằng chứng |
| Môi trường BE + thời điểm/version | Không có lượt thử BE; phiên bản deploy chưa xác minh |
| Record ID thật của BE cùng lượt thử | Không có; mọi UUID ví dụ §3 là giả định |

**[Đã kiểm chứng FE]** Session 295, 12/10/2026: 91 test files/413 tests đạt; lượt hồi quy cuối
bắt đầu 21:18:14, múi giờ máy Asia/Saigon, 47.26s. Build production/TypeScript và parity VI/EN đạt.
Lint không có lỗi; còn cảnh báo định dạng/cảnh báo có sẵn, không coi repo đã sạch mọi warning.
Kiểm thử workbook cả ba loại × VI/EN, domain, duplicate, stale UOM, quyền/scope, atomicity,
concurrent commit/retry, storage failure, confirmation/cancel, history và API-mode guard.

Browser demo: tải minh họa Nhà cung cấp → upload → preview → hủy/xác nhận → kết quả → danh mục
→ reload/history/detail; desktop 1440×900, tablet 1024×768/834×1112, VI/EN. Record minh họa có code
`DEMO-PARTNER-01`, phone `0000123456`; đó là dữ liệu browser mock, không phải ID/database record BE.
Item/Customer thành công được kiểm bằng test tự động; không ghi là đã chạy đủ ba luồng browser.

Mong đợi: validate không tạo master; commit tạo đủ lô; hủy/lỗi không tạo; retry cùng batch không tạo trùng.
Thực tế: các hành vi trên đạt trong FE/mock; chưa có bằng chứng transaction/concurrency/security trên BE.

## §3 — Đầu ra mong đợi — Cần BE triển khai

Tất cả mục §3 là **[Đề xuất BE]**, trừ thông tin schema FE được đánh dấu. Nếu API có sẵn đáp ứng,
BE cung cấp mapping/OpenAPI, không cần viết lại. Chờ trả lời §5 trước chốt wire contract.

### 3.1 Yêu cầu kiểm được

| ID | Yêu cầu đúng/sai kiểm được | Endpoint đề xuất | Chặn FE? |
|---|---|---|---|
| R01 | Server đọc file gốc, kiểm template/kind/locale/giới hạn; không tin kết quả validate client | POST /api/v1/data-import/batches | Chặn tích hợp, không chặn mock/lộ trình FE |
| R02 | Validate không tạo master; lưu batch INVALID/READY với dòng/lỗi để mở lại | Cùng R01 | Như trên |
| R03 | Commit chỉ lấy dữ liệu batch server đã lưu, kiểm lại domain/quyền và tạo nguyên lô trong transaction | POST /api/v1/data-import/batches/{id}/commit | Như trên |
| R04 | Retry cùng batch trả cùng createdIds/committedAt, không thêm record/audit tạo master | Cùng R03 | Như trên |
| R05 | Hai batch cùng mã commit đồng thời: chỉ một lô tạo mã đó; lô còn lại rollback toàn bộ | Cùng R03 + DB constraint | Như trên |
| R06 | History có tìm/lọc/phân trang và chỉ trả batch owner/phạm vi cho phép | GET /api/v1/data-import/batches | Như trên |
| R07 | Detail trả dòng/lỗi/receipt sau reload; ID trái owner/scope không lộ dữ liệu | GET /api/v1/data-import/batches/{id} | Như trên |
| R08 | Nhập thành công xuất hiện trong list/reference master hiện có; không tạo settings/stock | APIs master hiện có | Như trên |
| R09 | Batch/audit ghi actor, scope, file metadata, thời điểm, kết quả và liên kết record; không log secret | Nội bộ server, qua history/detail | Như trên |

### 3.2 Schema workbook — đã có trên FE

**[Đã kiểm chứng FE]** Sheet dữ liệu VI=`Dữ liệu`, EN=`Data`; sheet hướng dẫn=`Hướng dẫn`/`Guide`;
sheet tham chiếu=`Danh mục`/`References`. Metadata sheet `_OmniPlant`, trạng thái `veryHidden`:
`A1=omniplant-import-v1`, `B1=items|suppliers|customers`, `C1=vi|en`.
Không có Company/Plant/actor trong file. Chúng lấy từ ngữ cảnh request/phiên.
Tên/thứ tự header phải khớp locale; không có cột dữ liệu bổ sung. Header dòng 1, dữ liệu từ dòng 2.
Mẫu trống có định dạng tới dòng 501, vì vậy **không dùng số dòng có style để đếm record**.
File minh họa tải riêng, có một record demo; không tự bỏ dòng demo khi người dùng upload.

| Loại | Thứ tự cột VI | Thứ tự cột EN |
|---|---|---|
| items | Mã; Tên; Loại vật tư; Đơn vị tính; Theo dõi lô | Code; Name; Item type; Unit of measure; Lot tracking |
| suppliers/customers | Mã; Tên; Người liên hệ; Email; Điện thoại; Địa chỉ; Ghi chú | Code; Name; Contact person; Email; Phone; Address; Notes |

| Giá trị canonical | Nhãn VI trong mẫu | Nhãn EN trong mẫu | Mapping master |
|---|---|---|---|
| RAW_MATERIAL | Nguyên vật liệu | Raw material | Item.type |
| WIP | Bán thành phẩm | Work in progress | Item.type |
| FINISHED_GOOD | Thành phẩm | Finished good | Item.type |
| CONSUMABLE | Vật tư tiêu hao | Consumable | Item.type |
| NONE | Không | No | lotTracked=false, serialTracked=false |
| LOT | Có | Yes | lotTracked=true, serialTracked=false |

FE parser hiện chấp nhận nhãn theo locale hoặc enum canonical của type/tracking. Đề xuất server
áp dụng cùng mapping; không suy luận theo ngôn ngữ trình duyệt khi file có metadata locale khác.
Sheet tham chiếu chỉ hỗ trợ điền file; server **không tin** danh sách UOM trong file, phải tra UOM thật.
Tải mẫu/báo cáo lỗi hiện do FE tạo; bản đầu không yêu cầu thêm endpoint download riêng.

### 3.3 Trường dữ liệu và validation

Các giới hạn/normalization sau là **[Đã kiểm chứng FE]**; yêu cầu server kiểm độc lập là **[Đề xuất BE]**.
Trim text khi đọc, uppercase code và unit; không đổi số điện thoại thành number.
Một dòng có ít nhất một ô dữ liệu thì phải validate; chỉ bỏ dòng hoàn toàn trống.

| Field trong rows.values | Kiểu | Nullable | Nguồn/ràng buộc | Dùng trên UI |
|---|---|---|---|---|
| code | string | Không | Cột A; sau trim/uppercase, regex `^[A-Z0-9._-]{1,100}$`; unique kể cả INACTIVE | Preview, lỗi, master |
| name | string | Không | Cột B; trim, 1–255 ký tự | Preview/master |
| type, chỉ items | string enum | Không | Cột C; bốn enum ở bảng trên | Preview, Item.type |
| unit, chỉ items | string | Không | Cột D; UOM code đang ACTIVE; không phải UUID, không tạo UOM mới | Lookup UOM/preview |
| tracking, chỉ items | NONE hoặc LOT | Không | Cột E; mapping booleans ở bảng trên | Preview/theo dõi lô |
| contact, đối tác | string | Không trong preview, rỗng hợp lệ | Cột C; tối đa 255; khi tạo master map sang contactName | Preview/người liên hệ |
| email, đối tác | string | Không trong preview, rỗng hợp lệ | Cột D; tối đa 255; không rỗng phải khớp `^[^\s@]+@[^\s@]+\.[^\s@]+$` | Preview/master |
| phone, đối tác | string | Không trong preview, rỗng hợp lệ | Cột E; Text, tối đa 255; giữ `0000123456`, không ép kiểu số | Preview/master |
| address, đối tác | string | Không trong preview, rỗng hợp lệ | Cột F; tối đa 500 | Preview/master |
| notes, đối tác | string | Không trong preview, rỗng hợp lệ | Cột G; tối đa 500 | Preview/master |

Trường optional khi tạo đối tác có thể map rỗng → null theo contract master giai đoạn 2; BE xác nhận Q9.
Không gửi mock `fields`, `sku`, `trackingMethod` vào API master. Item dùng `code,name,type,unit,
lotTracked,serialTracked`; đối tác dùng `code,name,contactName,email,phone,address,notes`.
ID master là UUID do BE tạo, ACTIVE do server gán; không nhận ID/status từ Excel.

**[Đề xuất BE]** Uniqueness `(companyId, kind, normalizedCode)`, không có Plant trong khóa Item;
Supplier và Customer được trùng mã với nhau. Mock single-company hiện kiểm trong từng loại.
Code/phone numeric cell phải báo TEXT_REQUIRED, không đoán số 0 bị mất; công thức/shared formula
báo FORMULA, không evaluate; date/error/kiểu ô không hỗ trợ báo CELL_TYPE. FE có hỗ trợ rich text
dưới dạng ghép text; boolean hiện có thể stringify nên server strict hơn phải chốt Q10 và đồng bộ FE.

Giới hạn FE: file 1–5,242,880 byte; 1–500 dòng có dữ liệu. File có style hơn 10.000 dòng bị từ chối
template. Guard ZIP hiện đọc central directory, tối đa 64 entry/25 MiB advertised expanded size,
chặn entry vbaProject/externalLinks. **Đề xuất BE** giới hạn giải nén thực tế/CPU/thời gian, không tin
size quảng cáo hoặc extension; không fetch external link/chạy macro/formula. Các guard FE không
phải bằng chứng parser server đã an toàn. Xác nhận ngưỡng bổ sung ở Q11.

### 3.4 API đề xuất

Authentication: `Authorization: Bearer <da-che>` ở mọi request; không gửi secret trong tài liệu/log.
Owner lấy từ principal. Company phải tồn tại và được phép; Plant của Item phải thuộc Company.
Đối tác không cần Plant trong contract import dù UI hiện có Plant switcher.

| Method + full path | Query/body | Success result | Phân trang | Quyền/scope | Idempotency |
|---|---|---|---|---|---|
| POST /api/v1/data-import/batches | multipart: file, kind, companyId; plantId bắt buộc với items, bỏ với đối tác | HTTP 201, Batch; INVALID vẫn là validate thành công có row issues | Không | Bảng §1.4 | Idempotency-Key; retry upload cùng key/content trả batch đã tạo |
| POST /api/v1/data-import/batches/{id}/commit | JSON `{expectedVersion: 1}`; không nhận rows/file/owner từ client | HTTP 200, Batch COMMITTED; retry trả receipt cũ | Không | Owner + phạm vi batch + quyền hiện tại | Batch là business key chống trùng; thêm Idempotency-Key |
| GET /api/v1/data-import/batches | companyId; plantId?; kind?; status?; search?; page=0; size=20 | HTTP 200, Page BatchSummary | Server, size 1–100; newest first, id tie-break | Owner + quyền loại; Item cần đúng Plant | Không |
| GET /api/v1/data-import/batches/{id} | Không cần scope header/query; resolve từ batch | HTTP 200, Batch đủ rows/issues/receipt | Tối đa 500 rows, FE phân trang 20 | Owner + quyền + scope batch | Không |

List khi có plantId: kiểm Company–Plant, trả Item đúng Plant và đối tác trong Company.
List không có plantId: chỉ trả đối tác được phép; request kind=items thiếu Plant là lỗi.
search tìm tên file không phân biệt hoa thường; status chỉ INVALID/READY/COMMITTED; page zero-based.
`X-Plant-Id` chỉ thêm vào validate/list khi có plantId tường minh và trùng request, như convention hiện có.
Detail/commit theo aggregate ID không tự gắn global Plant header; server authorize Plant của batch.
Header không thay cho authorization. Đối chiếu Q3 trước khi viết adapter.

**DTO Batch/BatchSummary — đề xuất, không phải DTO mock đã tích hợp API:**

| Field | Kiểu | Nullable | Nguồn giá trị/công thức | UI sử dụng |
|---|---|---|---|---|
| id | UUID string | Không | Server batch | Detail/commit/retry |
| actorId | UUID string | Không | Principal server | Owner/history; không nhận từ request |
| companyId | UUID string | Không | Request được server xác minh | Phạm vi |
| plantId | UUID string | Có, null đối tác | Context Item đã xác minh | Phạm vi Item |
| kind / locale / templateVersion | enum / vi,en / string | Không | Chọn loại + metadata đã kiểm | Loại/file/report |
| fileName / fileSize | string / integer byte | Không | Tên an toàn + byte thực tế | History/detail |
| status / version | enum / integer >=1 | Không | Server state/revision; tăng khi state/dữ liệu validate đổi | Badge/expectedVersion |
| createdAt | ISO-8601 UTC instant | Không | Clock server | History |
| committedAt | ISO-8601 UTC instant | Có, null trước commit | Transaction commit | Receipt |
| rowCount / validRowCount / errorRowCount | integer >=0 | Không | Số dòng actual; dòng lỗi chỉ tính một lần dù nhiều issue | Summary cards |
| createdCount | integer >=0 | Không | 0 trước commit; N sau commit | Result |
| rows | array Row | Không, chỉ Detail/Batch | Đọc file + validation server | Preview/report |
| createdIds | UUID array | Không, chỉ Detail/Batch | [] trước commit; N IDs theo thứ tự rows sau commit | Receipt/master links |
| Row.line | integer >=2 | Không | Số dòng Excel gốc, không đánh lại số sau bỏ dòng trống | Cột Dòng Excel |
| Row.values | object string fields theo loại §3.3 | Không | Text/canonical sau normalization; ô rỗng là "" | Preview |
| Row.issues | array Issue | Không | [] khi không lỗi | Lọc Chỉ lỗi/report |
| Issue.field / column / code / message | string / Excel column / string / string | Không | Field canonical, A–G, machine code, giải thích server | Báo đúng ô và cách sửa |

BatchSummary là các field Batch ở trên **trừ rows và createdIds**, không trả raw file/PII của dòng
trong list. Mock hiện có `plantId` đối tác dạng string, không có version/count fields/server TTL;
adapter API sẽ map DTO này sau chốt, không yêu cầu BE copy localStorage model.
`rowCount = validRowCount + errorRowCount`; `errorRowCount` đếm dòng, không đếm số issue.
COMMITTED: `createdCount = rowCount = createdIds.length`, mọi rows.issues rỗng.

### 3.5 Ví dụ đề xuất — không phải request/response đã chạy

Fixture tự dựng trên DB thử: Company C=`11111111-1111-4111-8111-111111111111`,
Plant P=`22222222-2222-4222-8222-222222222222` thuộc C, actor A=`33333333-3333-4333-8333-333333333333`
có quyền Item READ+MANAGE tại P; UOM `KG` ACTIVE. `GD3-RM-0001` chưa tồn tại trong C.
Các UUID sau chỉ là placeholder hợp lệ, **không phải record BE thật**.

File `items-vi.xlsx`: metadata như §3.2 cho items/vi, Dữ liệu row 2:

```text
A2="GD3-RM-0001"; B2="Nguyên liệu minh họa"; C2="Nguyên vật liệu"; D2="KG"; E2="Có"
Tất cả các ô là Text; sheet có header đúng bảng §3.2.
```

Request đề xuất:

```http
POST /api/v1/data-import/batches
Authorization: Bearer <da-che>
X-Plant-Id: 22222222-2222-4222-8222-222222222222
Idempotency-Key: 44444444-4444-4444-8444-444444444444
Content-Type: multipart/form-data; boundary=<client-tu-tao>

file: items-vi.xlsx (binary XLSX, ví dụ 18200 byte)
kind: items
companyId: 11111111-1111-4111-8111-111111111111
plantId: 22222222-2222-4222-8222-222222222222
```

HTTP 201 đề xuất, lúc này số master tăng bằng 0:

```json
{
  "code": "SUCCESS", "message": "Success",
  "result": {
    "id": "55555555-5555-4555-8555-555555555555",
    "actorId": "33333333-3333-4333-8333-333333333333",
    "companyId": "11111111-1111-4111-8111-111111111111",
    "plantId": "22222222-2222-4222-8222-222222222222",
    "kind": "items", "locale": "vi", "templateVersion": "omniplant-import-v1",
    "fileName": "items-vi.xlsx", "fileSize": 18200,
    "status": "READY", "version": 1, "createdAt": "2026-10-12T14:30:00Z", "committedAt": null,
    "rowCount": 1, "validRowCount": 1, "errorRowCount": 0, "createdCount": 0,
    "rows": [{"line": 2, "values": {"code": "GD3-RM-0001", "name": "Nguyên liệu minh họa", "type": "RAW_MATERIAL", "unit": "KG", "tracking": "LOT"}, "issues": []}],
    "createdIds": []
  }
}
```

Commit đề xuất (không gửi file/rows lần nữa):

```http
POST /api/v1/data-import/batches/55555555-5555-4555-8555-555555555555/commit
Authorization: Bearer <da-che>
Idempotency-Key: 66666666-6666-4666-8666-666666666666
Content-Type: application/json

{"expectedVersion":1}
```

HTTP 200 trả **đủ Batch như trên**, với thay đổi sau; đây là trích đoạn result để tránh lặp JSON,
không phải response chỉ có các field này:

```json
{
  "status": "COMMITTED", "version": 2,
  "committedAt": "2026-10-12T14:31:00Z", "createdCount": 1,
  "createdIds": ["77777777-7777-4777-8777-777777777777"]
}
```

Item ID này có `code=GD3-RM-0001`, `type=RAW_MATERIAL`, `unit=KG`, `lotTracked=true`,
`serialTracked=false`, `status=ACTIVE`. Retry commit với expectedVersion cũ của cùng request trả
receipt COMMITTED này; không lỗi version cho request đã commit thành công và không tạo Item mới.

Kịch bản thử thay thế: UOM KG bị ngừng sau preview nhưng trước commit đầu tiên (chưa COMMITTED): HTTP 409, `code=IMPORT_REVALIDATION_FAILED`, result là Batch
đầy đủ chuyển INVALID/version=2, committedAt=null, createdCount=0/createdIds=[];
Row 2 có issue sau, các count thành rowCount=1/valid=0/error=1:

```json
{"line":2,"values":{"code":"GD3-RM-0001","name":"Nguyên liệu minh họa","type":"RAW_MATERIAL","unit":"KG","tracking":"LOT"},"issues":[{"field":"unit","column":"D","code":"INACTIVE_REFERENCE","message":"Đơn vị KG không tồn tại hoặc đã ngừng dùng"}]}
```

List đề xuất `GET /api/v1/data-import/batches?companyId=C&plantId=P&page=0&size=20`
(thay C/P bằng UUID fixture); result theo envelope chuẩn:

```json
{"code":"SUCCESS","message":"Success","result":{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}}
```

Ví dụ này mô tả list rỗng **trước khi tạo batch**; sau tạo có summary tương ứng và totalElements=1.
Response JSON thành công/lỗi dùng `{code,message,result?,errors?}`. `code/message` bắt buộc;
result có ở success; errors là array field errors khi áp dụng, có thể omitted. Trace ID từ header server.
Q6 chốt việc error 409 mang result Batch để FE hiển thị lỗi mới mà không mất preview.

### 3.6 Atomicity, concurrency, retry và dữ liệu

- Validate chỉ lưu file/dòng đã parse, lỗi và metadata batch; không gọi API create từng dòng để giả lập commit.
- Commit lấy row snapshot bất biến ở server, tái kiểm tra UOM/current duplicates và quyền; không tin
  preview trước đó, sheet References hoặc dữ liệu rows client gửi bổ sung.
- Transaction tạo N master + liên kết batch/createdIds + trạng thái COMMITTED/committedAt cùng nhau.
  Lỗi tại dòng cuối phải rollback toàn bộ. Receipt không được ghi thành công trước khi master commit.
- Khóa/serialize batch và unique constraint master chống hai batch tạo cùng mã. Race phát hiện sau
  revalidation vẫn rollback; trả lỗi conflict/dòng hiện tại, không chuyển sang partial success.
- Authorization trước idempotent receipt; batch COMMITTED trả receipt trước kiểm expectedVersion
  cũ của retry. Cùng idempotency key khác payload/batch là conflict, không trả receipt nhầm.
- Nếu mất response, FE lấy detail cùng ID hoặc retry cùng commit; không upload batch mới để đoán
  lần trước có thành công không. Master ID/order/committedAt của receipt ổn định sau reload/retry.
- Upload cùng key/content trả cùng batch; key khác có thể tạo preview mới nhưng không tự tạo master.
  Đề xuất lưu key theo actor + operation; fingerprint upload gồm hash byte file/fileName/kind/Company/Plant,
  fingerprint commit gồm batch ID/expectedVersion. Kiểm conflict của key trước trả receipt;
  sau authorization và key hợp lệ, COMMITTED retry không bị version check cũ chặn.
- Audit ghi batch ID, actor, company/Plant, kind, số dòng/kết quả và created IDs; không ghi token,
  không công khai file hoặc contact/email của người khác. Thời hạn lưu file/batch ở Q12, không tự
  ghi một thời hạn chưa chốt thành chính sách hiện hành.

**Tác động kho:** không có. On Hand/Reserved/Available/HOLD/REJECTED/WIP/value đều không đổi.
Không có quantity, decimal, giá hoặc stock ledger trong file. Chỉ tạo master ACTIVE.

### 3.7 Nghiệm thu live và rollout

Tất cả ca dưới đây **CHƯA CHẠY BE**, không phải test passed trên server. Fixture C/P/A/UOM ở §3.5
phải được BE tạo trên môi trường test, ghi lại UUID thật. Không dùng UUID minh họa làm evidence.

| ID | Đầu vào/thao tác | Kết quả mong đợi kiểm được |
|---|---|---|
| A01 | Ba loại × mẫu VI/EN hợp lệ, mã mới | Validate READY, 0 master mới; commit đủ N ACTIVE, list/reference đọc được |
| A02 | Mẫu trống/dòng chỉ style | EMPTY_FILE, không có master/batch thành công |
| A03 | .csv/.xlsm/archive sai hoặc metadata/header/kind sai | File-level error đúng code, 0 master |
| A04 | 5 MiB+1 byte hoặc 501 actual rows | FILE_SIZE/ROW_LIMIT; 500 dòng hợp lệ vẫn nhận |
| A05 | Dòng 2 trống, dòng 8/10 có dữ liệu lỗi | Báo line 8/10 và column thật, không đánh lại 2/3 |
| A06 | code/phone Text 0007/0000123456; bản khác dùng numeric | Text giữ số 0; numeric báo TEXT_REQUIRED, không tự sửa |
| A07 | Formula/shared formula/date/error/unsupported cell | FORMULA/CELL_TYPE tại đúng ô, không evaluate/fetch ngoài |
| A08 | Mã sai/rỗng, name rỗng/quá dài, email sai | INVALID, đúng field/code, không tạo dòng hợp lệ còn lại |
| A09 | Hai dòng code abc/ABC | Cả hai có DUPLICATE_FILE sau normalization; 0 master |
| A10 | Code đã tồn tại ACTIVE hoặc INACTIVE | DUPLICATE_CODE; record cũ không bị sửa/kích hoạt lại |
| A11 | Supplier/Customer cùng code; hai Plant cùng Company cho Item | Hai loại đối tác được trùng; Item không có namespace theo Plant |
| A12 | UOM không tồn tại/ngừng dùng; type/tracking ngoài enum | INVALID; không tự tạo UOM, không SERVICE/Serial |
| A13 | UOM ngừng sau READY | Commit 409 revalidation, INVALID, 0 master mới |
| A14 | Tạo mã bằng luồng master khác sau READY | Commit conflict/revalidation, cả lô 0 master mới |
| A15 | Hai batch cùng mã, mỗi batch còn dòng mã khác, commit đồng thời | Một lô thành công; lô còn lại rollback cả dòng mã khác |
| A16 | Hai commit cùng batch đồng thời/new key | Cùng receipt, N master tổng cộng, không 2N |
| A17 | Mất response rồi retry/reload/detail | Receipt/createdIds/committedAt ổn định; không tạo thêm |
| A18 | Inject lỗi DB khi tạo dòng cuối hoặc ghi receipt | 0 master của lô, chưa COMMITTED; retry sau khắc phục tạo đúng N |
| A19 | READ-only, MANAGE-only, quyền của hai loại khác nhau | Không đủ cặp quyền thì 403; 0 master |
| A20 | Owner khác/sai Company/sai Plant; truy cập ID trực tiếp | List không lộ batch; detail/commit NOT_FOUND; 0 mutation |
| A21 | Thu hồi/hết hạn assignment sau READY hoặc sau COMMITTED | Không commit hoặc đọc receipt trái quyền; authorization không bị retry bypass |
| A22 | Hủy xác nhận; chọn file sửa mới; mở history | Hủy không gửi commit; INVALID cũ không có command sửa rows; batch mới độc lập |
| A23 | History search/kind/status/page/size, nhiều batch | Totals đúng, newest first ổn định; không trả rows/PII trong summaries |
| A24 | companyId–plantId/header lệch; version/key conflict | 400/409 đúng code; không commit sai context hoặc trả receipt nhầm |
| A25 | Upload cùng key/content; cùng key/content khác | Cùng batch cũ; trường hợp khác bị IDEMPOTENCY_CONFLICT |
| A26 | Ngưỡng giải nén/entry/CPU, external link, macro | Server từ chối an toàn; không tác động master hoặc thực thi nội dung file |

**Evidence mỗi ca:** environment/version BE đã xác minh, ngày giờ, request/response nguyên văn
(che secret), HTTP/code, X-Trace-Id, batch ID và record ID thật từ **cùng lượt chạy**, DB/list trước–sau.
Với count: `deltaMaster = countAfter - countBefore`; atomic success N=3 ⇒ delta=3;
failed batch N=3 ⇒ delta=0; retry success ⇒ delta thêm=0. Đo trong đúng Company/kind/mã fixture,
không dùng tổng toàn DB có traffic khác.

**[Giả định dependency]** Company/Plant/UOM/Item và đối tác giai đoạn 2 có schema/API/permission
được BE chốt; unique constraint và transaction dùng cùng domain service master. Chưa xác minh deploy.
Thứ tự: trả Q1–Q14/OpenAPI → master/permission dependency → batch schema/constraints → validate
→ commit/receipt → history/detail/audit → A01–A26 → FE adapter được PO duyệt → nghiệm thu FE live.
Không seed quyền vào mọi tài khoản; migration/role assignment phải theo policy đã được chốt.
Không chuyển mock→API tới khi contract và dependency sẵn sàng; API lỗi không fallback mock.
**Kết quả live:** chưa thử. **Trạng thái:** chuẩn bị bàn giao/chờ contract, chưa hoàn tất tích hợp.
**Change log:** v1 tạo mới ngày 12/10/2026; chưa có bản đã gửi để thay thế.

## §4 — Hợp đồng lỗi mong đợi

**[Đề xuất BE]** Row-level issues trả trong Batch INVALID của validate HTTP 201;
không dùng HTTP 400 cho từng dòng khi file đọc được. File/request-level và commit lỗi có bảng dưới.
Các code import là đề xuất; BE chốt mapping trước adapter, không coi code mock là contract server.

| Tình huống | HTTP / code | FE hiển thị | Giữ input / retry |
|---|---|---|---|
| File sai loại/archive/macro/link | 400 FILE_FORMAT | File không được hỗ trợ | Giữ file, chọn bản đúng |
| Vượt byte/expanded limit | 413 FILE_SIZE | File vượt giới hạn | Giữ tên, chọn file nhỏ hơn |
| Metadata/header/kind sai | 400 TEMPLATE | Dùng đúng mẫu danh mục/phiên bản | Chọn lại mẫu |
| Rỗng/đếm quá 500 | 400 EMPTY_FILE / ROW_LIMIT | Không có dữ liệu hoặc vượt số dòng | Sửa file |
| Query/body/version thiếu/sai | 400 VALIDATION_ERROR | Lỗi field cụ thể | Giữ preview; sửa request/adapter |
| Thiếu quyền | 403 PERMISSION_DENIED | Không đủ quyền nhập | Không tự retry |
| Batch không có/trái owner/scope | 404 NOT_FOUND | Không tìm thấy lần nhập trong phạm vi | Không tự retry |
| Header/context lệch | 400 PLANT_CONTEXT_MISMATCH | Kiểm tra lại nhà máy đã chọn | Không gửi sang scope khác |
| Batch INVALID bị gọi commit | 409 IMPORT_BATCH_NOT_READY | Sửa file và kiểm tra lại | Không partial import |
| expectedVersion lệch | 409 IMPORT_BATCH_VERSION_CONFLICT | Tải lại chi tiết cùng batch | Giữ bản đang xem; không tự commit |
| Domain thay đổi trước commit/race mã | 409 IMPORT_REVALIDATION_FAILED + result Batch INVALID | Lỗi dòng mới, yêu cầu sửa file | Giữ preview; không master mới |
| Cùng key khác payload/batch | 409 IDEMPOTENCY_CONFLICT | Không thể dùng lại mã yêu cầu này | Không thay key để che conflict |
| Lỗi DB/server | 500 INTERNAL_ERROR | Chưa xác định kết quả, kiểm tra batch/thử lại | Giữ preview/key/batch ID; hỏi detail trước |
| Auth hết hạn/token lỗi | Theo contract Auth hiện hành | Refresh/đăng nhập theo code Auth | Không tạo cơ chế Auth riêng cho import |

Issue codes: CODE_FORMAT, REQUIRED_NAME, TOO_LONG, ENUM, INVALID_EMAIL, DUPLICATE_FILE,
DUPLICATE_CODE, INACTIVE_REFERENCE, FORMULA, TEXT_REQUIRED, CELL_TYPE.
Message không thay machine code; FE localize theo code và gắn field/line/column.
Unsupported raw file values không được render thành HTML hoặc công thức trong báo cáo XLSX.

Ví dụ lỗi đề xuất, không phải evidence:

```json
{"code":"VALIDATION_ERROR","message":"Invalid request","errors":[{"field":"companyId","message":"companyId is required"}]}
```

Lỗi 409 revalidation theo envelope `code/message/result` Batch §3.5; field errors chỉ bổ sung
khi phù hợp. Server phát `X-Trace-Id`; FE giữ lại khi báo lỗi, không log Authorization/refresh token.
STORAGE_FAILED/LOCK_UNAVAILABLE là lỗi **browser mock**, không yêu cầu BE trả hai code này.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** “Chặn” bên dưới là chặn adapter/API acceptance
của giai đoạn 3, **không chặn** tiếp tục FE/mock giai đoạn sau. Owner mọi câu: BE phụ trách + PO
khi là quyết định nghiệp vụ; cá nhân chưa chỉ định. Quyết định/ngày: **chưa chốt** cho từng câu.

1. **Q1:** Dùng atomic create-only cho ba loại, không partial/upsert, được không? — FE nghiêng về Có vì tránh nhập một phần — **CHẶN tích hợp**.
2. **Q2:** Item import cho phép COMPANY permission bao phủ Plant giống evaluator FE hiện tại, được không? — FE nghiêng về Có; batch vẫn ghi đúng Plant và Item thuộc Company — **CHẶN authorization live**.
3. **Q3:** Chấp nhận bốn path và convention Plant header ở §3.4 không? — FE nghiêng về Có; aggregate-ID resolve scope từ server — **CHẶN adapter**.
4. **Q4:** Server nhận/parse file gốc qua multipart, không nhận rows client làm nguồn commit, được không? — FE nghiêng về Có vì cần kiểm độc lập — **CHẶN tích hợp**.
5. **Q5:** Giữ metadata/version/header/mapping VI/EN ở §3.2, được không? — FE nghiêng về Có để mẫu hiện có round-trip — **CHẶN tích hợp**.
6. **Q6:** Validate row lỗi trả 201 Batch INVALID và commit stale trả 409 kèm result Batch INVALID, được không? — FE nghiêng về Có để UI cập nhật đúng lỗi mà giữ preview — **CHẶN adapter**.
7. **Q7:** COMMITTED retry trả receipt cũ trước kiểm expectedVersion nhưng sau authorization, được không? — FE nghiêng về Có vì mất response không được tạo trùng — **CHẶN nghiệm thu retry**.
8. **Q8:** Mã unique theo Company + loại, gồm cả INACTIVE, không theo Plant Item, được không? — FE nghiêng về Có, nhất quán danh mục giai đoạn 2 — **CHẶN nghiệp vụ live**.
9. **Q9:** Trường optional đối tác rỗng map sang null trong master, được không? — FE nghiêng về Có; preview vẫn dùng string rỗng — **CHẶN mapping adapter**.
10. **Q10:** Server chặn boolean/unsupported types và chỉ flatten rich text khi hợp lệ, được không? — FE nghiêng về Có; FE sẽ đồng bộ strict validation khi làm adapter — **CHẶN parser acceptance**.
11. **Q11:** Giữ 5 MiB/500 dòng, đồng thời server áp dụng ngưỡng entry/giải nén thực tế và timeout an toàn, được không? — FE nghiêng về Có; BE công bố cụ thể ngưỡng server trước A26 — **CHẶN file-security acceptance**.
12. **Q12:** Giai đoạn đầu giữ batch/receipt/audit theo policy lịch sử master, còn file gốc chỉ lưu nếu cần audit rồi áp dụng retention riêng, được không? — FE nghiêng về Có để receipt không biến mất làm retry sai; BE nêu thời hạn cụ thể — **KHÔNG CHẶN FE/mock, CHẶN rollout live**.
13. **Q13:** History/detail chỉ cho owner có quyền hiện tại, không mở admin xem batch người khác ở bản đầu, được không? — FE nghiêng về Có theo UI đã làm — **CHẶN scope acceptance**.
14. **Q14:** Giữ validate/commit đồng bộ cho tối đa 500 dòng, chưa thêm job queue/status PROCESSING, được không? — FE nghiêng về Có để giữ flow hiện tại; nếu BE không đáp ứng, trả phương án riêng trước code — **CHẶN adapter/rollout**.

## §6 — FE đã tự xử lý

**[Đã kiểm chứng FE]** Đã có template/example/report VI/EN; upload/drop; file/schema/parser guards;
normalize; domain errors; preview phân trang/Chỉ lỗi; confirmation/hủy; giữ file/preview khi lỗi;
khóa double-submit; result/history/detail; query invalidate sau commit; context change đóng trạng thái.
Code/phone dạng Text được giữ số 0; report giá trị dạng string, không tạo formula từ input.

Mock revalidate quyền/domain tại commit, stage cả lô, ghi receipt+master cùng snapshot; Web Locks
tuần tự hóa ghi giữa các tab workspace master/import. Đây không thay cho DB transaction/unique
constraint/authorization server hoặc đa người dùng. FE không tự tạo dữ liệu thật bằng vòng lặp API.

**Giới hạn:** không có import adapter live; API mode báo chưa sẵn sàng. Master/partner permissions
và hợp đồng giai đoạn 2 vẫn cần chốt; parser backend có thể strict hơn FE sau Q10. History mock không
có server revision/retention; DTO/version/error mới ở §3–§4 cần adapter sau BE/PO xác nhận.
Client invalidate không chứng minh các API master server đã trả master mới; A01/R08 kiểm riêng.
Không coi guard file FE, localStorage hoặc audit dependency là nghiệm thu bảo mật server.

Khi tích hợp: giữ source file/preview/batch ID qua lỗi; commit error có result phải map Batch,
không reset form; timeout lấy detail cùng batch; không tự bỏ dòng lỗi, đổi scope hoặc fallback mock.
Các thay đổi adapter là công việc sau khi contract và phạm vi code được PO duyệt.

## §7 — Tham chiếu

Các tên dưới là hồ sơ liên quan, **không yêu cầu BE truy cập repo FE**. Nội dung cần triển khai
giai đoạn 3 đã có đủ trong file này. Chưa xác nhận PO đã gửi các file tham chiếu cho BE.

- `FE_HANDOFF_GD_02_2026-10-12.md` — schema/permission/lifecycle Item, UOM và đối tác; các quyết định master còn chờ BE.
- `BE_HANDOFF_GD_01_2026-10-09.md` — Auth/session/email/RBAC; task này không thay đổi luồng đó.
- `CAPSTONE3_GD3_FE_PROPOSAL_2026-10-12.md` — phương án FE đã được PO duyệt trước code.
- `CAPSTONE3_GD3_FE_REVIEW_2026-10-12.md` — báo cáo màn hình và kiểm tra FE/mock Session 295, không phải API acceptance.
- `FE_HANDOFF_GD_03_MESSAGE_2026-10-12.md` — mô tả bốn dòng để PO gửi cùng tài liệu này.

### Checklist phát hành

- Đủ §0–§7, mức P1 dùng cho luồng import API; có ngoài phạm vi/môi trường/mốc FE/owner.
- Không có defect live; năm mảnh evidence ghi không có; chưa xác minh deploy mới nhất được nói rõ.
- JSON/request minh họa có nhãn đề xuất, không có token thật/trace ID/record BE giả làm evidence.
- Field đủ kiểu/nullability/nguồn/UI; endpoint đủ method/full path/body/result/quyền/pagination/retry.
- Yêu cầu/ca nghiệm thu đo được; code lỗi riêng; count/UUID fixture nhất quán, không lẫn lượt chạy.
- Tách cần triển khai §3 với chỉ cần trả lời §5; mỗi câu một quyết định, có hướng FE và mức chặn.
- Lớp FE phòng vệ và giới hạn được ghi; tin nhắn bốn dòng đi kèm lưu cùng bản v1.
- Chỉ đóng tích hợp sau A01–A26 live có evidence; sửa sau gửi dùng `_v2`, tiếp tục cùng task.
- Tài liệu chuẩn bị xong không có nghĩa đã gửi hoặc BE đã chấp nhận contract.

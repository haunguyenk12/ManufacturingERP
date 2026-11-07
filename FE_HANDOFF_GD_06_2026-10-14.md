# Giai đoạn 6 — Bàn giao Backend: Đơn bán hàng và Hoạch định vật tư

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock giai đoạn 6 đã hoàn thành; cần BE cung cấp hoặc đối chiếu contract Đơn bán hàng, phân bổ tồn cho đơn, MRP, chuyển đề xuất thành nháp sản xuất/mua hàng và nhập SO Excel.
- Ảnh hưởng: người bán hàng/lập kế hoạch dùng được demo; API SO/MRP/WO hiện có vẫn được giữ, nhưng chưa tích hợp các phần mở rộng mới tương đương FE/mock.
- Mức ưu tiên: P1 — chặn tích hợp các luồng mở rộng ở chế độ API; không chặn các module khác hoặc lộ trình FE/mock tiếp theo.

**Bản:** v1 · **Ngày dự kiến gửi:** 14/10/2026 · **Mốc FE:** Session 305, 13–14/10/2026 · **Bàn giao:** Session 306.
**PO:** yêu cầu “tạo tài liệu cho BE” sau delivery; ghi nhận chuyển GĐ6 FE/mock sang bàn giao, không phải nghiệm thu server/API.
**FE owner / BE owner cá nhân:** chưa được chỉ định. **Trạng thái:** đã chuẩn bị để PO gửi, chưa gửi ngoài, chưa xác nhận contract/deploy BE.
Nếu gửi ngày khác, đổi ngày/tên trước lần gửi đầu; sửa sau gửi phát hành `_v2`, `_v3`, tiếp tục cùng task.

Nhãn áp dụng xuyên suốt:

- **[Đã kiểm chứng FE]**: đọc source hoặc test/quan sát FE/mock; không chứng minh DB/API BE.
- **[Đề xuất BE]**: field/path/policy/HTTP cần BE thống nhất. Có API tương đương thì trả mapping/OpenAPI; không cần triển khai trùng.
- **[Giả định]**: chưa kiểm trên BE; không coi là contract đã duyệt.
- **[Chưa kiểm chứng live]**: không gọi request BE giai đoạn 6 trong lần bàn giao này.

## §1 — Phạm vi

### 1.1 Màn hình mới và đã sửa

| Loại | Màn hình/phần | Hành vi cần dữ liệu Backend |
|---|---|---|
| Mới | Phân bổ tồn cho SO | Dialog chọn kho/lô/lượng, giữ tồn đúng dòng đơn; bỏ phân bổ riêng |
| Mới | Nhập SO Excel | Mẫu VI/EN → kiểm tra nhóm → preview/lọc lỗi → xác nhận tạo nhiều DRAFT nguyên tử |
| Mới | Lịch sử/chi tiết nhập SO | Tệp, trạng thái, lỗi từng dòng, các đơn đã tạo; lịch sử chính người dùng theo Company/Plant |
| Mới | Chi tiết nhu cầu MRP | Panel phải: nguồn SO/BOM, kho, ngày, tính toán tồn/supply/safety và quantity còn chuyển được |
| Mới | Tạo nháp mua từ MRP | Chọn Supplier ACTIVE, quantity từng dòng; giữ sản phẩm/UOM/kho/ngày/nguồn |
| Mới | Chi tiết nháp mua | Panel phải: DRAFT, Supplier, kho nhận, dòng hàng và link lần hoạch định |
| Sửa | Danh sách SO | Search mã/khách/tham chiếu; status, khoảng ngày đặt/ngày cần; phân trang 20 |
| Sửa | Tạo/sửa SO | Form rộng: Customer master, tham chiếu khách, ngày đặt, nhiều dòng FG/WIP, qty/due date; UOM tự điền |
| Sửa | Chi tiết SO | Tabs Sản phẩm/Phân bổ/Chứng từ; tách ordered/held/WO coverage/produced/QC/delivered; panel giữ bộ lọc |
| Sửa | Hoạch định vật tư | Kỳ hiện tại; chọn dòng đơn còn nhu cầu; kho tính/kho đầu ra theo Item; lịch sử |
| Sửa | Kết quả MRP | Tabs Nhu cầu/Vật tư/Sản xuất/Mua hàng/Vật tư tiêu hao/Cần bổ sung/Chứng từ; bảng phân trang và drill-down |
| Sửa | Tạo WO từ MRP | Chuyển một phần hoặc nhiều lần, WO DRAFT cùng snapshot BOM/Routing, coverage và source trace |
| Sửa | WO list/detail | Đọc cùng record WO đã tạo từ MRP; qty 6 decimals, snapshot và link nguồn |
| Sửa | Kho/chi tiết kho | SO reservation dùng chung stock pool; nhãn nguồn/link đúng SO, không giả WO |
| Sửa | Nhập dữ liệu/navigation | Thêm kind SO và nút vào wizard theo quyền; các loại nhập master/BOM cũ giữ nguyên |

UI dùng tên nghiệp vụ VI/EN, card/input trắng, các thao tác ngắn có dialog xác nhận. Chi tiết bên phải có header cố định/body cuộn, không nằm dưới danh sách. BE không cần xây UI hoặc dùng permission code làm tên nghiệp vụ.

### 1.2 Trong phạm vi / Ngoài phạm vi / Môi trường

**Trong phạm vi:** Customer-linked SO DRAFT/save/confirm/cancel; stock allocation thuộc SO; metrics tách biệt; demand chưa được bao phủ; MRP snapshot, net theo Item/kho/ngày, BOM đa cấp, safety/ROP/open supply; MAKE WO DRAFT và BUY PO DRAFT tối thiểu; SO XLSX create-only DRAFT/history; scope/quyền, optimistic concurrency, atomic commands, retry và trace.

**Ngoài phạm vi:** PO confirm/partial receipt/QC mua đầy đủ (GĐ7); WO readiness/release/material issue/execution/WIP (GĐ8); production receipt/QC (GĐ9); shipment/Delivery/Invoice/payment (GĐ10). Không PR/RFQ/vendor bill/return/refund, forecast MPS, optimizer, Gantt, cross-Plant sourcing, serial, auto-confirm/release/post, tự chuyển kho để bù chênh kho đầu ra, partial import/upsert/ghi đè đơn từ Excel, sửa snapshot WO đã có, đổi Auth/quyền GĐ1 hoặc breaking change API cũ.

**Môi trường [Đã kiểm chứng FE]:** demo/mock, production build cục bộ, Session 305, kiểm tra 13–14/10/2026 theo Asia/Saigon, desktop/tablet VI/EN. Numeric ID/store/receipt của mock không phải UUID/transaction server. Không có commit/tag phát hành riêng; không khẳng định đã push/deploy.

**Existing API:** đọc adapter/DTO trong source FE ngày 14/10/2026; không fetch OpenAPI/runtime trong phiên này. Historical V66 integration không xác minh bản BE hiện đang deploy. Không kết luận BE chưa có tính năng chỉ vì FE chưa có adapter; §3 đề xuất mapping/extension.

### 1.3 Luồng nghiệp vụ tự chứa

1. Chọn Company/Plant có quyền. Chọn Customer ACTIVE thuộc Company, orderDate và tham chiếu khách tùy chọn. Thêm 1–500 dòng FG/WIP ACTIVE, quantity dương tối đa 6 decimals, dueDate >= orderDate; UOM chuẩn lấy từ Item. Không đòi BOM/Routing để bán từ tồn sẵn.
2. Lưu DRAFT, sửa chỉ DRAFT, xác nhận sau kiểm lại Customer/Item/UOM. Import chỉ DRAFT. Mã SO do server cấp theo policy chốt, không nhập mã nhóm Excel làm orderNo.
3. Đơn đã xác nhận: chọn dòng, kho cùng Plant, lot đủ điều kiện nếu tracked, quantity còn chưa được bao phủ; giữ riêng cho SO. HOLD/REJECTED/hết hạn và tồn đang giữ cho người khác không dùng được. Không giảm On Hand hoặc tự tạo giao hàng.
4. Xem số đã đặt/giữ tồn/coverage WO/sản xuất/QC/giao khách theo từng dòng/UOM. Receipt đạt QC không đồng nghĩa giao khách. Legacy thiếu chứng cứ phải hiện chưa có bằng chứng, không tự gán 0 hoặc FULFILLED.
5. Chọn kỳ MRP (1–90 ngày inclusive), chọn dòng chưa có coverage; chọn kho tính nhu cầu và kho nhận đầu ra từng Item nếu default thiếu. Chạy chỉ lưu lần tính; không reserve/create WO/PO.
6. MRP dùng available stock và open supply đủ điều kiện theo ngày/kho. Gom vật tư dùng chung, safety một pool, chỉ net MAKE mới explode BOM. Thiếu master/kho/policy/BOM/Routing có cảnh báo và chặn conversion tương ứng, không giả 0 dữ liệu.
7. Chọn đề xuất sản xuất: nhập quantity <= phần còn chuyển, xác nhận, tạo canonical WO DRAFT với immutable BOM/Routing/component requirements/kho/source. Coverage chỉ gắn dòng SO cùng output Item; WO bán thành phẩm phụ thuộc không gắn coverage sai cho FG.
8. Chọn BUY: một Supplier ACTIVE/Plant/kho nhận mỗi PO, có thể nhiều dòng ngày khác nhau; nhập lượng từng dòng <= còn chuyển. Tạo PO DRAFT lưu source MRP; chưa pricing/confirm/receipt. Nháp không là firm inbound.
9. Nếu nguồn thay đổi, kết quả cũ vẫn đọc được; conversion bị chặn đến khi tính lại. Conversion của chính run cập nhật ledger/version chuyển, không viết lại requirements snapshot. Retry cùng key không sinh thêm chứng từ.
10. Excel: validate cả file và nhóm; tất cả hợp lệ mới commit tạo N SO DRAFT trong một transaction. Một lỗi → 0 SO. Reload xem được lỗi/kết quả/link; owner/Company/Plant trái scope không đọc batch.
11. Hủy SO chỉ DRAFT/CONFIRMED và chưa có downstream đang được bảo vệ; bắt buộc lý do, giải phóng own reservation cùng transaction. Không reset coverage để bỏ qua WO/PO. GĐ7/8 sẽ chốt hủy chứng từ con trước, không tự cascade ở đây.

### 1.4 Quyền, scope và trạng thái

**[Đã kiểm chứng FE]** mock dùng mapping dưới. **[Đề xuất BE]** enforce auth/scope/master/state ở server mỗi command và trước idempotent replay. Không dùng role name, union permission hoặc nút ẩn thay authorization.

| Thao tác | State | Quyền FE hiện dùng | Scope/tác động |
|---|---|---|---|
| SO list/detail | Không đổi | PERM_SALES_ORDER_READ | Company/Plant của SO |
| Save/edit/confirm/cancel SO | Không có→DRAFT; DRAFT→DRAFT/CONFIRMED/CANCELLED; CONFIRMED→CANCELLED khi hợp lệ | READ + PERM_SALES_ORDER_MANAGE | Current Plant; kiểm source/version; cancel giải phóng own giữ tồn |
| SO reserve/release | Reservation ACTIVE→RELEASED; SO không phải DRAFT/CANCELLED | READ + MANAGE của SO | Dòng đơn/kho/lot cùng Plant; không dùng material reservation WO |
| SO import validate/commit | READY/INVALID; READY→COMMITTED | READ + MANAGE của SO | Actor lấy từ auth, Company/Plant hiện tại |
| SO import history/detail | Không đổi | Route mock hiện READ+MANAGE; read API đề xuất READ theo Q22 | Owner+Company+Plant, không lộ batch người khác |
| MRP list/detail/requirements | Không đổi | PERM_MRP_READ hoặc PERM_MRP_RUN | Run thuộc Company/Plant; history run không owner-only |
| Run MRP | Không có→snapshot mới | PERM_MRP_RUN | Current Plant, chỉ nguồn được phép; không post stock |
| MAKE/BUY conversion | Thêm conversion, tạo WO/PO DRAFT | MRP_READ hoặc MRP_RUN + PERM_SUPPLY_SUGGESTION_MANAGE | Run/source/warehouse/target cùng Company/Plant; không release/confirm |
| WO detail từ link | Không đổi | PERM_WORK_ORDER_READ | Aggregate scope WO, không thêm Plant header tùy tiện |
| PO DRAFT detail từ run | Không đổi | Mock qua quyền đọc MRP | Quyền PO riêng cần chốt Q5 trước GĐ7 |

Quyền selector Customer/Supplier/Item/kho/BOM/Routing: mock dùng nguồn master chia sẻ, không phải mẫu API cho phép lấy tất cả dữ liệu. BE phải chốt dedicated minimal reference read hoặc yêu cầu các READ nguồn theo Q6; endpoint trả code/name/UOM/status cần thiết, không trả dữ liệu riêng tư không phục vụ chọn. GLOBAL/COMPANY coverage của Plant dùng policy GĐ1 đã chốt, không tự nới quyền.

Vai trò dự kiến: nhân viên bán hàng xử lý SO, người lập kế hoạch chạy MRP/chuyển nháp, Manager có thể có cả hai tập quyền. Đây là mô tả công việc, không role-name authorization; BE dùng scoped permission ở bảng. Assignment thu hồi/hết hạn trong lúc chờ: request sau phải từ chối, kể cả replay. Direct-ID/batch/run trái scope hoặc owner đề xuất 404 NOT_FOUND; thiếu quyền trong phạm vi trả 403 PERMISSION_DENIED. Không có phê duyệt hai người mới: người có MANAGE tự xác nhận SO; bước duyệt suggestion cũ cần quyết định Q14.

## §2 — Bằng chứng tái hiện

**Không có defect API/live trong tài liệu này — handoff FE/mock và đề xuất contract.**

| Mảnh bằng chứng live | Trạng thái |
|---|---|
| Request thật method/path/header/body | Không có; không gọi API live GĐ6 trong phiên bàn giao |
| Response thật HTTP/code/JSON | Không có |
| X-Trace-Id response header | Không có; không tự tạo thay trace thật |
| Môi trường BE, timestamp request, bản mới nhất | Chưa xác minh; ngày test FE không phải giờ request BE |
| ID record BE của cùng lượt chạy | Không có; numeric fixture/mock không là UUID BE |

```text
Mong đợi: API mới/mapping cung cấp luồng và số liệu §3, sau khi BE/PO chốt §5.
Thực tế: FE/mock đã chạy; adapter API cũ được giữ, các extension mới chưa tích hợp/nghiệm thu live.
```

**[Đã kiểm chứng FE]** Session 305: full regression 97 files/471 tests; sau sửa seam Decimal, 5 files/47 tests liên quan đạt; focused GĐ6 cuối 2 files/20 tests. Final build/TypeScript 37 routes, VI/EN parity, targeted lint đạt. Browser: nhiều dòng, ngày/qty chính xác, giữ tồn, net đa cấp, thiếu Routing, split WO/PO draft, lỗi stale giữ input, Excel invalid/valid/history/reload/Plant isolation. Một test worker startup timeout lúc cùng build được xử lý bằng chạy tuần tự, không coi lượt timeout là đạt.

**Demo quan sát, không phải request/response BE:** SO-2026-0007 (mock id 7), FG 2000.000001 PCS và SF 5 PCS. Giữ FG 1 tại kho thành phẩm có On Hand 4 → Available 3. MRP-20261013-0001 gross FG 1999.000001 + safety30 − available3 = net2026.000001. Tạo WO-20261013-0001 qty1.000001 và PO-20261013-0001 qty2.000001 M2. Excel hợp lệ 3 dòng/2 nhóm tạo SO-0009/0010 DRAFT; nhóm quantity0 khóa batch. Không cần BE có các mã demo này; fixtures tự dựng ở §3.6.

Ảnh gửi riêng: **không có**; bố cục và số liệu cần thiết đã mô tả bằng chữ/bảng. Không có token/secret trong file.

## §3 — Đầu ra mong đợi — Cần BE triển khai hoặc cung cấp API tương đương

### 3.1 Yêu cầu nghiệm thu

“Chặn FE?” dưới đây là chặn **tích hợp/live** của phần tương ứng, không chặn lộ trình FE/mock. R là đầu ra cần cung cấp; Q tại §5 chỉ trả lời quyết định.

| # | Yêu cầu đúng/sai kiểm được | Endpoint/contract | Chặn FE? |
|---|---|---|---|
| R01 | SO lưu customerId từ Customer ACTIVE cùng Company; response có code/name snapshot; client không quyết customerName/UOM | SO draft save/read | Có, SO mới |
| R02 | Save cả header/1–500 lines atomic; chỉ DRAFT sửa bằng expectedVersion; qty >0 scale<=6, ngày hợp lệ due>=order; UOM/type/master kiểm lại | SO draft create/update | Có |
| R03 | List server search mã/khách/reference, status và date ranges, page0/size20; due range theo policy Q3, list đủ summary không cần FE gọi detail mỗi row | SO list | Có, list mới |
| R04 | Detail trả ordered, held, WO coverage, produced/QC/delivered có nguồn và unknown; không dùng QC accepted để xác nhận shipment hoặc tự FULFILLED | SO detail/metrics | Có |
| R05 | Reserve <= uncovered SO và eligible free stock, đúng lot/warehouse, gắn salesOrderId/lineId; On Hand/value không đổi, Reserved tăng/Available giảm | SO reservations | Có |
| R06 | Release chỉ own active reservation; cancel SO có lý do, state/version/downstream guards và giải phóng own reservations atomic; không mất giữ của đơn/WO khác | SO release/cancel | Có |
| R07 | Planning demand chỉ đơn đã xác nhận còn uncovered, không DRAFT/CANCELLED/CLOSED hoặc dòng hết nhu cầu; trả SO line ID riêng với planning demand ID | planning-demands | Có |
| R08 | MRP kiểm kỳ inclusive<=90, scope và mapping Item/warehouse; kết quả có source/warehouse resolution, thiếu mapping là cảnh báo/blocked, không dùng kho Plant khác | commercial run/requirements | Có |
| R09 | Net exact và gom component dùng chung theo Item/kho/ngày; stock/supply/safety carry một lần; chỉ net MAKE explode quantity/output/(1-scrap), lượng cấp con phải tương ứng lượng WO thực tế sau rounding Q28 | MRP calculation | Có |
| R10 | Open supply chỉ nguồn committed đúng Plant/Item/UOM/kho/due, còn open và chưa earmark; HOLD receipt giảm open nhưng không cộng AVAILABLE, draft không firm | MRP supply snapshot | Có |
| R11 | ROP riêng cho MRO/consumable, có policy/point/reorder qty/source; thiếu config báo lỗi, không tự tạo lượng | MRP ROP | Có |
| R12 | Saved demand/requirements/master/BOM/Routing/stock/supply giữ immutable; stale result vẫn đọc nhưng conversion phải kiểm current source version | run/detail/version | Có |
| R13 | MAKE partial/split atomic tạo canonical WO DRAFT + snapshots + source + SO root coverage + conversion receipt; không allocate WIP output cho FG, không reserve/release WO | conversion/WO | Có |
| R14 | BUY group một Supplier ACTIVE/Plant/warehouse, qty từng requirement<=remaining; tạo canonical PO DRAFT nguồn/date/UOM; không confirm/post và không firm | conversion/PO draft | Có |
| R15 | Conversion partial không mất remaining hoặc biến single convertedReference thành đã chuyển hết; cùng run/split/batch retry không double convert/over-cover | conversion ledger | Có |
| R16 | XLSX validate lại server, group-consistent hiện master, file<=5MiB/500 populated rows, reject formula; commit cả batch tạo mới DRAFT trong một transaction hoặc 0 | SO import | Có |
| R17 | Owner+Company+Plant import history/detail, dòng lỗi/group results, lỗi tải được; replay cùng batch trả cùng SO IDs; stale master làm cả commit fail | import history/commit | Có |
| R18 | Mỗi command kiểm auth/scope/state trước replay, transaction stock/coverage/document/receipt nhất quán; competing keys không vượt qty; audit actor/time/reason/source/trace | Tất cả mutation | Có |

### 3.2 API đã có trong adapter FE — không phải xác nhận deploy

Các path sau là **Backend path gồm `/api` context**, không phải URL máy FE. ID wire UUID string; query/header Plant chỉ dùng khi explicit Plant trong contract, aggregate-ID dùng scope record.

| Method/path đang được adapter gọi | Request/response hiện có | Khoảng cần mapping |
|---|---|---|
| GET/POST `/api/sales-orders/v1` | GET companyId/plantId/status/search/page/size; POST orderNo/customerName/orderDate/note/lines | Customer ID/reference, code generation, date filters/list summary mới |
| GET/PATCH `/api/sales-orders/v1/{id}` | DTO version/lines; PATCH expectedVersion/customerName/orderDate/note/full lines replacement | New draft save với Customer ID; quantities tách rõ |
| POST `/api/sales-orders/v1/{id}/confirm` | Adapter hiện body rỗng | New optimistic version guard cần compatibility |
| POST `/api/sales-orders/v1/{id}/cancel` | Body reason | Own reservation/downstream/version; không mặc định server hiện thiếu |
| GET `/api/sales-orders/v1/planning-demands` | plantId/horizonEnd; result array, planningDemandId khác salesOrderLineId | start/coverage/unknown policy và source metrics |
| GET/POST `/api/v1/planning-runs` | POST companyId/plantId/demandWarehouseId/horizon dates/demandLineIds | Adapter yêu cầu một demandWarehouseId; chưa gửi per-Item override/includeRop |
| GET `/api/v1/planning-runs/{id}` | Run status/counts/dates | Immutable inputs/sources/version và ledger mở rộng |
| GET `/api/v1/planning-runs/{id}/requirements` | Page, sort requirementLevel; warehouse/resolution/gross/available/reserved/openSupply/safety/projected/net | Multiple parents/source links, supply detail, carry ledger |
| GET `/api/v1/planning-runs/{id}/suggestions` | Page; MAKE/BUY, DRAFT/APPROVED/REJECTED/CONVERTED, exceptionState/messages, warehouse/output/receiving, convertedReference | Partial/split còn lượng, nhiều conversions, BUY draft |
| POST `/api/v1/supply-suggestions/{id}/approve` hoặc `/reject` | decisionNote; reject bắt buộc lý do | Mock mới chuyển trực tiếp, chưa có approve/reject UX; cần Q14 |
| POST `/api/v1/supply-suggestions/{id}/convert-to-work-order` | outputWarehouseId, optional workOrderNo/start/end/notes; trả suggestion | Adapter không gửi partial quantity/expectedSourceVersion; chỉ một reference |
| GET/POST `/api/v1/plants/{plantId}/work-orders`; GET/PATCH `/api/v1/work-orders/{id}` | Canonical WO, snapshot và các luồng production hiện có | WO GĐ6 phải cùng aggregate, không thêm WO store độc lập |

**[Đã kiểm chứng FE]** SO adapter hiện suy allocated=`max(ordered-openQuantity,0)` và đọc fulfilledQuantity. Ngữ nghĩa BE của open/fulfilled chưa được xác minh lần này; không đổi chúng thành delivered hoặc đồng nhất với held+WO trước Q2/Q26/Q27. API-mode BUY vẫn đọc-only/deferred. Master Customer/Supplier mapping tiếp tục dependency GĐ2; không phát minh endpoint deploy đã có.

### 3.3 Field/result đề xuất

**[Đề xuất BE]** giữ API cũ tương thích; BE trả mapping nếu tên hiện có khác. UUID khác code nghiệp vụ; mọi lượng wire dùng decimal string, không JSON number. Ví dụ giới hạn input mock <=999999999, qty scale6; giới hạn wire thật NUMERIC/overflow cần Q15. Ngày `YYYY-MM-DD` không timezone; timestamp ISO8601 có offset/Z. Parent/line arrays rỗng `[]`, không null. Actor từ auth, không nhận actorId do client chọn.

| Field | Kiểu | Nullable | Nguồn giá trị/công thức đề xuất | Dùng ở đâu trên UI |
|---|---|---|---|---|
| salesOrderId/companyId/plantId | UUID | Không | Record/scope server | SO/list/detail/link |
| orderNo | string | Không | Server sequence, unique scope Q1 | Mã SO |
| customerId | UUID | Có cho legacy chưa migrate; không cho SO mới | Customer cùng Company | Picker/link Customer |
| customerCode/customerName | string | Code có thể null legacy; name không | Snapshot lúc save; policy rename Q18 | List/header |
| customerReference | string | Có | Optional text, không tự unique/deduplicate | List/search/form |
| orderDate; dueDate | date | Không | Header và từng dòng | Filters/form |
| earliestDueDate/latestDueDate/lineCount | date/date/int | Không với >=1 line | Min/max/count server | SO list không N+1 |
| version/expectedVersion | integer>=0 | Không ở extension mutation | Optimistic aggregate version | Save/confirm/cancel conflict |
| salesOrderLineId/lineNo/itemId/itemSku/itemName/uom | UUID/int/UUID/string/string/string | Không | Server line numbering và Item snapshot | Lines và demand |
| orderedQuantity | decimal string | Không | Đã đặt >0 | Metrics |
| heldStockQuantity | decimal string | Không | Own ACTIVE held chưa sử dụng; không thuộc WO | Metrics/demand |
| workOrderCoveredQuantity | decimal string | Không | Coverage chưa giao, cùng output Item; incl DRAFT theo Q2 | Metrics/demand |
| producedQuantity | decimal string | Có | Output production có trace về SO line; không suy từ planned qty | Metric sản xuất |
| qcAcceptedQuantity | decimal string | Có | QC accepted có trace; không shipment | Metric QC |
| deliveredQuantity | decimal string | Có cho legacy thiếu evidence | Shipment/delivery posted; SO mới chưa giao 0 | Metric giao khách |
| metricsEvidence | object enum KNOWN/UNKNOWN + source refs | Không | Server giải thích null/history và nguồn | —/tooltip |
| uncoveredQuantity | decimal string | Không | Demand rule §3.5, sau normalization coverage | MRP candidates |
| linkedDocuments[] | array | Không | type/id/code/status/itemId/uom/quantityForThisOrder/source links | SO related docs, không dùng tổng WO cho mỗi SO |
| reservationId/salesOrderId/salesOrderLineId/warehouseId/itemId | UUID | Không | Owner và canonical stock position | Allocation tab |
| lotId/lotCode | UUID/string | Có, chỉ non-tracked | Required cho tracked resolved server | Lot picker/detail |
| quantity/remainingHeldQuantity/status/version | decimal/decimal/enum/int | Không | ACTIVE/RELEASED reservation, issued/consumed giữ trace | Bỏ phân bổ/metrics |
| runId/runCode/companyId/plantId/actorId/createdAt | UUID/string/UUID/UUID/UUID/instant | Không | Run auth/scope/time | History/header |
| inputSnapshot/sourceVersion | object/opaque string | Không | Validated input; revision token server, không client hash authority | Reload/stale conversion |
| snapshotSourceVersion | opaque string | Không | Revision gốc lúc tính, immutable | Trace tính toán |
| currentSourceState | enum CURRENT/STALE | Không | Server so current revisions, status không sửa requirements | Cảnh báo source cũ |
| requirementLineId/parentRequirementLineIds/sourceSalesOrderLineIds | UUID/UUID[]/UUID[] | Không | Graph/source server, nhiều nguồn sau gom | Drill-down/link |
| sourceKind | enum SALES/COMPONENT/ROP | Không | Root/dependent/independent | Tên nghiệp vụ và coverage |
| warehouseId/outputWarehouseId | UUID | Có chỉ row blocked hoặc BUY không MAKE | Netting warehouse khác output metadata; không tự transfer | Result/conversion |
| warehouseResolutionSource/settingSource | string enum | Không | Default/manual/legacy/missing | Cảnh báo mapping |
| grossRequiredQuantity/availableQuantity/openSupplyQuantity/safetyStockQuantity/netRequiredQuantity | decimal strings | Không khi calculable | Snapshot số liệu từng bucket §3.5 | Result/formula |
| calculationState | enum CALCULATED/BLOCKED | Không | Blocked không giả số authoritative thiếu | Cảnh báo |
| projectedAvailableQuantity | decimal string | Có chỉ blocked | Available + eligible supply trước gross; BE map tên Q8 | Result/source |
| dueDate/suggestedOrderDate | date/date | Không trong calculated row | Due và trừ leadDays, có thể trước kỳ | Result/conversion |
| warnings[] | array code/message/source ref | Không | Missing refs/policy/abnormal/late nếu policy Q17 | Cần bổ sung |
| supplySources[] | array | Không | Đúng kho/UOM/date, open qty và version; không draft | Sắp nhận |
| replenishmentPolicy/reorderPoint/reorderQuantity/policySource | enum/decimal/decimal/enum | Có khi missing, warning required | INDEPENDENT_ROP; CONFIGURED/LEGACY_FALLBACK/MISSING | ROP riêng |
| convertedQuantity/remainingConvertibleQuantity/conversionVersion | decimal/decimal/int | Không | Sum committed conversion receipts; net−converted | Partial conversion |
| conversions[] | array | Không | type/id/code/qty/at/requirementLineId/status | Related docs nhiều lần |
| planningSource/snapshots/componentWarehouseMappings | object/object/map UUID→UUID | Không khi WO từ MRP | SALES/COMPONENT/ROP, immutable BOM/Routing/source; WO BOM qty theo lượng WO | WO detail/readiness seam |
| unallocatedOutputQuantity | decimal string | Không | Phần WO output chưa gắn SO, ví dụ safety | WO source/không fake SO |
| purchaseOrderId/status/supplierId/warehouseId/lines[] | UUID/enum/UUID/UUID/array | Không | Canonical PO DRAFT, one Supplier/Plant/kho | PO draft panel/GĐ7 |
| PO line sourceRunId/sourceRequirementLineId/itemId/uom/quantity/dueDate | UUID/UUID/UUID/string/decimal/date | Không | Conversion source immutable | PO lines/trace |
| importBatchId/state/ownerId/companyId/plantId/fileName/templateVersion/version | UUID/enum/UUID/UUID/UUID/string/string/int | Không | Server validation batch/owner | Import/history |
| rowResults[]/groupResults[]/createdSalesOrderIds[] | arrays | Không | Row line/group/errorCode/field/message; group→SO on COMMITTED | Preview/lỗi/result links |
| validatedAt/committedAt/expiresAt | instant | committed/expiry có thể null | Server lifecycle/retention Q20 | History/stale |

Các object lồng cần đủ dữ liệu dưới đây, không yêu cầu FE mở master hiện tại để dựng lại snapshot cũ:

| Field | Kiểu | Nullable | Nguồn giá trị/công thức đề xuất | Dùng ở đâu trên UI |
|---|---|---|---|---|
| supplySources[].sourceType/documentId/lineId/documentCode/status | enum WO/PO, UUID, UUID, string, enum | Không | Canonical document/line; WO single-output có output-source ID ổn định | Trace sắp nhận |
| supplySources[].plantId/itemId/uom/warehouseId | UUID/UUID/string/UUID | Không | Source output/receiving warehouse, không suy từ Plant default | Match supply/netting |
| supplySources[].expectedAvailableDate | date | Không với eligible source | Ngày có thể nhận theo Plant timezone; không string-compare instant với date | Bucket due matching |
| supplySources[].expectedAt | instant | Có nếu chỉ date | ETA authoritative, giữ timezone/offset | Detail nguồn |
| supplySources[].openQuantity/sourceVersion/earmarked | decimal/opaque string/boolean | Không | Remaining posted-receipt-adjusted; target earmark trace | Eligibility/recompute |
| supplySources[].plannedQuantity/postedReceiptQuantity/closedRemainderQuantity | decimals | Không khi dùng làm proof open | Source aggregate snapshot; receipt HOLD vẫn posted | Giải thích open supply |
| bomSnapshot.bomId/code/version/outputItemId/outputQuantity/capturedAt | UUID/string/string/UUID/decimal/instant | Không | ACTIVE BOM lúc conversion, copied immutable | WO BOM snapshot |
| bomSnapshot.lines[].componentItemId/itemSku/itemName/uom/quantity/scrapRate | UUID/string/string/string/decimal/decimal | Không | Quantity theo batch outputQuantity, rate 0<=x<1, scale theo GĐ5 | Component demand/snapshot |
| routingSnapshot.routingId/code/version/capturedAt | UUID/string/string/instant | Không với MAKE cần Routing | ACTIVE Routing của output tại Plant, copied immutable | WO Routing snapshot |
| routingSnapshot.operations[].sequence/workCenterId/name/setupMinutes/runMinutesPerUnit/predecessorSequences | int/UUID/string/decimal/decimal/int[] | Không | Immutable production operation source, không live master name/time overwrite | WO operation snapshot |
| sourceContributions[].salesOrderLineId/parentRequirementLineId/kind/quantity/uom | UUID/UUID/enum/decimal/string | ID có thể null theo kind, không cả hai null trừ ROP/safety | Root/dependent contributions riêng dù chung bucket, Q10 | Exact root coverage/trace |

Supply date normalization cần BE trả rõ: nếu source chỉ có instant thì tính expectedAvailableDate theo timezone Plant, không timezone browser, không so ISO timestamp với `YYYY-MM-DD` bằng chuỗi. Reservation audit gồm actor/time/reason/source/record version/trace; actor auth, lot owner trace giữ sau release.

Missing source quantity nên nullable theo calculationState thay vì client suy số. Các field gross/net đã có ở adapter cũ có thể giữ non-null cho calculated rows; BE cần trả mapping migration blocked-row semantics, không silently breaking existing API.

### 3.4 Endpoint đề xuất và ví dụ wire

**Toàn bộ endpoint/JSON trong mục này là ĐỀ XUẤT, CHƯA CHẠY API.** Các UUID cố định bên dưới chỉ là ví dụ hợp lệ, không record BE. Tiền tố `C/P/K/I/S/L/R/Q` trong mô tả là tên fixture, không được gửi literal alias thay UUID. Nếu BE có endpoint tương đương, mapping vào contract này; các suffix mới nhằm giữ client API cũ tương thích.

GET dùng success envelope `code/message/result`. Page zero-based `{content,page,size,totalElements,totalPages}`. Search/filter ở server; sort ổn định có ID tie-break. Mutation dùng `Idempotency-Key`, aggregate/version, actor auth. X-Trace-Id lấy response header thật khi nghiệm thu. List mặc định20, max size và bulk detail hạn mức BE xác nhận; arrays trong detail có thể tách paged subresource khi lớn.

| Method/path đề xuất | Query/body | Success result / phân trang | Quyền/scope / idempotency |
|---|---|---|---|
| GET `/api/sales-orders/v1` mở rộng | companyId,plantId,search,status,orderDateFrom/To,dueDateFrom/To,page,size | Page SO summary §3.3 | SO_READ Plant; read |
| GET `/api/sales-orders/v1/{id}` mở rộng | ID | SO + metrics/source/linked docs; lines<=500 | SO_READ aggregate; read |
| POST `/api/sales-orders/v1/drafts` | companyId,plantId,customerId,customerReference?,orderDate,lines[{itemId,orderedQuantity,dueDate}] | SO DRAFT, generated orderNo, version | SO_READ+MANAGE; key |
| PATCH `/api/sales-orders/v1/{id}/draft` | expectedVersion,customerId,customerReference?,orderDate,full lines[] | Updated SO DRAFT | SO_READ+MANAGE; key; full replacement atomic |
| POST `/api/sales-orders/v1/{id}/confirm` extension | expectedVersion | CONFIRMED SO | SO_READ+MANAGE; key; preserve legacy contract |
| POST `/api/sales-orders/v1/{id}/cancel` extension | expectedVersion,reason | CANCELLED SO + released refs | SO_READ+MANAGE; key |
| GET `/api/sales-orders/v1/{id}/reservations` | page,size,lineId?,status? | Page own reservations, lot/code/kho | SO_READ; read |
| GET `/api/sales-orders/v1/{id}/lines/{lineId}/allocation-candidates` | warehouseId,page,size | Eligible positions + free qty, uncovered qty and stock version | SO_READ plus reference policy Q6; read |
| POST `/api/sales-orders/v1/{id}/reservations` | salesOrderLineId,warehouseId,lotId?,quantity,expectedVersion | Created reservation group + stock/line metrics versions; auto-lot có thể nhiều records | SO_READ+MANAGE theo Q4; key |
| POST `/api/sales-orders/v1/{id}/reservations/{reservationId}/release` | expectedVersion,reason? | Released reservation + updated metrics | SO_READ+MANAGE; key |
| GET `/api/sales-orders/v1/planning-demands` extension | plantId,horizonStart,horizonEnd,page?,size? | Existing array nếu legacy; extension Page theo Q7, covered/held/unknown fields | Planning-demand authorization Q6; read |
| POST `/api/v1/planning-runs/commercial` | companyId,plantId,horizonStartDate,horizonEndDate,salesOrderLineIds,itemWarehouseMappings,includeRop | Saved Run + sourceVersion/status/counts, không stock write | MRP_RUN; key |
| GET `/api/v1/planning-runs` mở rộng | companyId,plantId,search,page,size | Page runs | MRP_READ hoặc RUN; read |
| GET `/api/v1/planning-runs/{id}` mở rộng | ID | Input snapshot, source versions/state, counts/conversion version | MRP_READ hoặc RUN; read |
| GET `/api/v1/planning-runs/{id}/requirements` mở rộng | page,size,kind?,sourceKind?,warning? | Page requirements; stable IDs | MRP_READ hoặc RUN; read |
| GET `/api/v1/planning-runs/{id}/requirements/{requirementId}` | ID | Requirement + parents/SO/BOM trace/supply details | MRP_READ hoặc RUN; read |
| POST `/api/v1/planning-runs/{id}/conversions` | expectedSourceVersion,expectedConversionVersion,entries[{requirementLineId,quantity}],supplierId? | documents[] + conversion ledger + next versions; all MAKE hoặc all BUY | MRP_READ hoặc RUN + SUPPLY_SUGGESTION_MANAGE; key |
| GET `/api/v1/planning-runs/{id}/conversions` | page,size | Page source quantities/target docs | MRP_READ hoặc RUN; read |
| GET `/api/v1/purchase-orders/{id}` | ID | PO DRAFT header+lines/source; canonical GĐ7 seam | Q5 permission, record scope; read |
| GET `/api/sales-orders/v1/imports/template` | companyId,plantId,locale | Binary XLSX, content-disposition, không envelope cho file | SO_READ+MANAGE; read |
| POST `/api/sales-orders/v1/imports/validate` | Multipart file,companyId,plantId,templateVersion | Batch READY/INVALID + counts + row/group results | SO_READ+MANAGE owner; key |
| GET `/api/sales-orders/v1/imports` | companyId,plantId,state?,page,size | Page own batch summaries | READ theo Q22; owner; read |
| GET `/api/sales-orders/v1/imports/{batchId}` | page/size rows?,onlyErrors? | Batch + paged row results + group results/created refs | READ theo Q22; owner/scope; read |
| GET `/api/sales-orders/v1/imports/{batchId}/errors` | locale | Binary XLSX lỗi, không envelope cho file | READ theo Q22; owner; read |
| POST `/api/sales-orders/v1/imports/{batchId}/commit` | expectedVersion | COMMITTED batch + same SO refs after replay | SO_READ+MANAGE owner; key |

Template import: worksheet `Sales orders`; `_meta` row1 `version | omniplant-so-v1`, row2 `locale | en` hoặc `vi`. Các sheet hướng dẫn/tham chiếu có thể thêm. Headers đúng thứ tự:

| # | English | Tiếng Việt | Validation |
|---|---|---|---|
| 1 | Group code | Mã nhóm đơn | Required key trong file, không orderNo/upsert key |
| 2 | Customer code | Mã khách hàng | ACTIVE cùng Company; cùng nhóm cùng customer |
| 3 | Order date | Ngày đặt | Date; cùng nhóm cùng ngày |
| 4 | Customer reference | Tham chiếu khách hàng | Optional; cùng nhóm cùng reference, không unique toàn hệ thống |
| 5 | Item code | Mã sản phẩm | ACTIVE FG/WIP, UOM chuẩn ACTIVE |
| 6 | Quantity | Số lượng | Dương, tối đa6 decimals, không formula/scientific notation |
| 7 | Due date | Ngày cần | >=Order date; từng dòng có thể khác |

5 MiB = 5,242,880 bytes; tối đa500 populated data rows, bỏ blank rows. Mock guard physical rowCount>10000; server thêm parser bounds/decompression/resource guards theo policy, không chạy macros/formulas hoặc dựa vào cached formula result. Chỉ `.xlsx`, không CSV/XLS/XLSM. Date ISO hoặc Excel Date thực cần mapping serial/date-system Q19. Trim code/reference; code case normalization dùng master GĐ2. Một nhóm lỗi đánh cả nhóm invalid, nhưng giữ row-specific lỗi gốc. Một file lỗi → không có SO nào. Commit kiểm lại quyền/master hiện tại và batch version, không tin draft JSON gửi từ FE. Group code lặp gom một đơn, nhóm khác tạo đơn khác; repeated Customer reference không là idempotency key.

**Ví dụ đề xuất 1 — tạo SO, không gọi thật:**

```json
{
  "companyId": "11111111-1111-4111-8111-111111111111",
  "plantId": "22222222-2222-4222-8222-222222222222",
  "customerId": "33333333-3333-4333-8333-333333333333",
  "customerReference": "PO-DEMO-A",
  "orderDate": "2026-10-14",
  "lines": [{
    "itemId": "44444444-4444-4444-8444-444444444444",
    "orderedQuantity": "100.000000",
    "dueDate": "2026-10-28"
  }]
}
```

Ví dụ success dưới đây là projection rút gọn để minh họa contract, không response thật; tên code do server sinh chỉ là ví dụ.

```json
{
  "code": "SUCCESS", "message": "OK",
  "result": {
    "salesOrderId": "55555555-5555-4555-8555-555555555555",
    "orderNo": "SO-DEMO-A", "status": "DRAFT", "version": 0,
    "customerId": "33333333-3333-4333-8333-333333333333",
    "customerCode": "CUS-DEMO", "customerName": "Khách hàng minh họa",
    "lines": [{
      "salesOrderLineId": "66666666-6666-4666-8666-666666666666",
      "lineNo": 1, "itemId": "44444444-4444-4444-8444-444444444444", "uom": "PCS",
      "orderedQuantity": "100.000000", "heldStockQuantity": "0.000000",
      "workOrderCoveredQuantity": "0.000000", "producedQuantity": null,
      "qcAcceptedQuantity": "0.000000", "deliveredQuantity": "0.000000",
      "uncoveredQuantity": "100.000000", "dueDate": "2026-10-28",
      "metricsEvidence": {"produced": "UNKNOWN", "qcAccepted": "KNOWN", "delivered": "KNOWN"}
    }]
  }
}
```

**Ví dụ đề xuất 2 — sau confirm SO, giữ 30 từ kho A (stock khả dụng 30), không gọi thật:**

```json
{
  "salesOrderLineId": "66666666-6666-4666-8666-666666666666",
  "warehouseId": "77777777-7777-4777-8777-777777777777",
  "quantity": "30.000000", "expectedVersion": 1
}
```

lotId bỏ trống nghĩa auto-resolve các lot hợp lệ FIFO nếu tracked theo Q25, không bỏ tracking. Response canonical qty30, held30, uncovered70, On Hand30/Reserved30/Available0; lot IDs từng reservation phải có, không fake workOrderId.

**Ví dụ đề xuất 3 — MRP input sau giữ30:**

```json
{
  "companyId": "11111111-1111-4111-8111-111111111111",
  "plantId": "22222222-2222-4222-8222-222222222222",
  "horizonStartDate": "2026-10-14", "horizonEndDate": "2026-11-12",
  "salesOrderLineIds": ["66666666-6666-4666-8666-666666666666"],
  "includeRop": false,
  "itemWarehouseMappings": [{
    "itemId": "44444444-4444-4444-8444-444444444444",
    "demandWarehouseId": "77777777-7777-4777-8777-777777777777",
    "outputWarehouseId": "77777777-7777-4777-8777-777777777777"
  }]
}
```

Các component thiếu default phải chọn thêm mapping hoặc trả blocked; JSON trên chỉ nêu FG mapping, không giả mọi component đã cấu hình.

**Ví dụ đề xuất 4 — calculated FG requirement khi component mappings hợp lệ, safety0, supply0:**

```json
{
  "code": "SUCCESS", "message": "OK",
  "result": {
    "requirementLineId": "88888888-8888-4888-8888-888888888888",
    "sourceKind": "SALES", "sourceSalesOrderLineIds": ["66666666-6666-4666-8666-666666666666"],
    "parentRequirementLineIds": [], "calculationState": "CALCULATED",
    "itemId": "44444444-4444-4444-8444-444444444444", "uom": "PCS",
    "warehouseId": "77777777-7777-4777-8777-777777777777",
    "grossRequiredQuantity": "70.000000", "availableQuantity": "0.000000",
    "openSupplyQuantity": "0.000000", "safetyStockQuantity": "0.000000",
    "projectedAvailableQuantity": "0.000000", "netRequiredQuantity": "70.000000",
    "convertedQuantity": "0.000000", "remainingConvertibleQuantity": "70.000000",
    "dueDate": "2026-10-28", "warnings": [], "supplySources": []
  }
}
```

**Ví dụ đề xuất 5 — chuyển partial MAKE40 từ requirement trên; source/version là opaque server token, không hash FE:**

```json
{
  "expectedSourceVersion": "example-source-v1", "expectedConversionVersion": 0,
  "entries": [{
    "requirementLineId": "88888888-8888-4888-8888-888888888888", "quantity": "40.000000"
  }]
}
```

Success tạo một WO DRAFT40, SO held30/covered40/uncovered30, requirement net vẫn70/converted40/remaining30; WO không firm và stock không đổi. Response documents[] chứa WO ID/code/qty/state và conversionVersion1/sourceVersion cho lần tiếp. Lần2 chuyển30 tạo WO khác, remaining0. BUY dùng cùng shape entries[] + supplierId bắt buộc, không mixed MAKE/BUY, không mixed warehouse trong một PO.

**Ví dụ đề xuất 6 — batch INVALID giữ group errors (không commit, chưa chạy API):**

```json
{
  "code": "SUCCESS", "message": "Validated",
  "result": {
    "importBatchId": "99999999-9999-4999-8999-999999999999",
    "state": "INVALID", "version": 0, "createdSalesOrderIds": [],
    "rowResults": [
      {"line": 2, "groupCode": "A", "field": "quantity", "errorCode": "INVALID_QUANTITY", "message": "Quantity must be positive"},
      {"line": 3, "groupCode": "A", "field": null, "errorCode": "IMPORT_GROUP_INVALID", "message": "Another line in this group is invalid"},
      {"line": 4, "groupCode": "B", "field": null, "errorCode": null, "message": null}
    ],
    "groupResults": [{"groupCode": "A", "state": "INVALID"}, {"groupCode": "B", "state": "READY"}]
  }
}
```

Group B READY không cho nhập riêng khi batch INVALID. READY commit body `{expectedVersion:0}` tạo toàn bộ groups và result group→SO IDs; replay trả cùng IDs. Binary template/errors trả Content-Type/filename, không nhét binary vào JSON envelope.

### 3.5 Công thức và transaction boundaries

**[Đã kiểm chứng FE]** mock open demand = max(0, ordered − own active held − WO allocated). Mock SO mới chưa có shipment. **[Đề xuất BE]** normalize coverage chỉ lượng chưa giao và không overlap với held; nếu delivered đã known, uncovered = max(0, ordered − delivered − held chưa giao − WO coverage chưa giao). Produced/QC là tiến độ của coverage, không tiếp tục trừ hai lần. Nếu thiếu bằng chứng legacy khiến không xác định được demand, trả warning/state để PO quyết, không coi null=0. Q2/Q26/Q27 chốt coverage và mapping open/fulfilled trước migration.

MRP Item+warehouse pool:

```text
eligibleAvailable = AVAILABLE không hết hạn, trừ mọi reservation active của position.
projectedBeforeDemand = availableCarry + newlyEligibleCommittedOpenSupply.
netDependent = max(0, gross + safety - projectedBeforeDemand).
carryAfterBucket = max(0, projectedBeforeDemand + plannedNet - gross).
componentGross = netMAKE / BOM.outputQuantity * componentQuantity / (1 - scrapRate).
remainingConvertible = savedNet - sum(successfulConversionQuantities).
```

`plannedNet` ở carry là **giả định toán học trong cùng lần MRP** rằng proposal sẽ bù shortage; không thêm proposal vào bảng firm supply hoặc run sau. Shared stock/supply dùng một lần theo bucket ngày, safety được giữ trong carry, không mua lại toàn safety mỗi dòng. Root/dependent cùng Item/kho/ngày gom source arrays; ngữ nghĩa mixed SALES/COMPONENT cần Q10 tránh losing root coverage. Không cộng quantity khác UOM thành summary.

Open supply: PO open = confirmed quantity − physically posted receipt quantity − closed remainder; receipt HOLD vẫn giảm open. WO open = planned output − approved receipt quantity, nếu trạng thái committed và output chưa earmark. `approvedReceiptQuantity` gồm HOLD/REJECTED output sau QC nên phần reject không trở lại open supply. Nguồn rejected/đã hủy/đóng không firm; WO BLOCKED/COMPLETED còn open và ETA quá khứ cần Q11. Stock AVAILABLE sau QC chỉ xuất hiện trong available, không add inbound lần nữa.

ROP mock: projected<reorderPoint thì `max(reorderQuantity, max(0,safety-projected))`, còn lại0. Ví dụ projected3, point5, reorder20, safety10 → bổ sung20 (không tự lấy point−projected=2). projected=5 →0. Missing policy/reorder qty → blocked, không invent lượng. ROP không source SO hoặc dùng BOM-driven formula.

Lead time mock là calendar days: due−leadTimeDays, component due = parent releaseDate; không scheduler/capacity/working calendar. ReleaseDate có thể trước horizon/today, không silently clamp (Q17). Intermediate calculations exact; quantity hiển thị/WO component ceiling6. Ví dụ net1.000001, component0.5/output1/scrap0.02 →0.510204591836… →0.510205. Scrap rate scale cần theo GĐ5, không dùng qty serializer ép rate về scale6 gây mất hao hụt.

**Rounding đa cấp cần chốt Q28:** đọc source mock thấy child explosion dùng net nội bộ chưa ceiling, còn quantity để tạo WO đã ceiling6; chưa có live proof cho mọi tỷ lệ lớn. Ví dụ suy luận toán học: WIP net exact1.0000001 → WO1.000001, RAW ratio100 → nhu cầu từ net cũ100.00001, nhưng WO cần100.0001. **[Đề xuất BE]** chốt net MAKE có thể thực thi ở scale6 trước khi explode cấp con, giữ phép tính exact giữa các bước; cấp con không được thiếu so với WO thực tế. Đây là điểm cần đồng bộ FE khi adapter được duyệt, không claim mock đã nghiệm thu case này.

| Event | On Hand | Reserved | Available | HOLD/REJECTED | WIP/value | Liên kết |
|---|---|---|---|---|---|---|
| SO save/confirm/import | Không đổi | Không đổi | Không đổi | Không đổi | Không đổi | SO lines/batch |
| SO reserve x | Không đổi | +x | −x | Không sử dụng | Không đổi | SO line→reservation→lot/position |
| SO release/cancel held x | Không đổi | −x | +x nếu lot vẫn eligible | Lot bị HOLD/REJECTED/hết hạn không tự thành AVAILABLE | Không đổi | Release/source/reason/audit |
| Run MRP | Không đổi | Không đổi | Không đổi | Không đổi | Không đổi | Immutable run/source |
| MAKE→WO DRAFT | Không đổi | Không đổi | Không đổi | Không đổi | Không đổi | WO snapshots/coverage/ledger |
| BUY→PO DRAFT | Không đổi | Không đổi | Không đổi | Không đổi | Không đổi | PO lines/source/ledger |

Không tạo quantity movement giả để phản ánh giữ/bỏ giữ; nếu dùng stock audit movement loại RESERVE/RELEASE, qty delta On Hand=0. Release không phục hồi trạng thái lot. Chính sách audit cancel cần BE ghi history/reason nguyên tử; mock không chứng minh ledger server.

**Atomicity/concurrency [Đề xuất BE]:** transaction server lock/version stock positions+reservations+SO line hoặc run conversion+WO/PO+coverage+receipt. Validate tất cả lines trước post; exception ở line cuối rollback cả batch/header/lines/counters/links. Same key+payload trả cùng result; same key khác payload trả conflict. Khóa theo aggregate/position và unique receipt, không dùng browser Web Locks thay DB. Concurrent different keys kiểm remaining dưới lock. Authorization/owner kiểm trước replay. MRP snapshot lấy nguồn nhất quán; source stale check và create document nằm cùng transaction, không TOCTOU. Own conversion chỉ advance conversion ledger/current action version, giữ snapshotSourceVersion gốc.

Không tự xóa/hủy WO/PO khi hủy SO; chuyển một phần không đánh hết proposal nếu còn quantity. Hủy/chỉnh target draft và trả conversion quota/capacity cần Q21, tạm không tự hoàn quota để tránh double convert.

### 3.6 Nghiệm thu live và thứ tự rollout

Tất cả A01–A38 **chưa thử live**. BE dựng records mới; ghi lại UUID thật, request/response/trace/giờ/deploy version cùng lượt. Cột bằng chứng `E` nghĩa đủ 5 mảnh §2 + before/after/refetch cho lượng/state; không lấy demo ID/mock screenshot thay evidence.

Fixture tự chứa: Company C, Plant P và P2, active Customer K/supplier V thuộc C; active UOM PCS/KG; FG F có BOM output1 gồm WIP W qty1/scrap0 và RAW M qty0.5/scrap0.02; W có BOM output1 gồm RAW N qty2/scrap0. Active Routing của F/W tại P. Kho FG A, WIP B, RAW D tại P, kho E tại P2; settings active/defaultOutput/defaultSupply rõ, safety/leadDays theo từng case. SO S order14/10/2026, F100 due28/10/2026. Case thay quantity/date phải tạo fixture riêng hoặc restore state có audit; không reuse mutated record rồi kỳ vọng số ban đầu.

| ID | Input/record | Thao tác | Mong đợi kiểm được | Bằng chứng | Trạng thái |
|---|---|---|---|---|---|
| A01 | C/P/K/F ACTIVE | Save2lines qty0.1/0.2, due28/29 | DRAFT, UOM server, code/version/line IDs; reload giữ đúng | E | Chưa thử live |
| A02 | Customer INACTIVE/cross-C hoặc Item/UOM inactive | Save/confirm | Error phù hợp,0 changes, input FE còn | E | Chưa thử live |
| A03 | Qty0/negative/7decimals/overflow; date invalid/due<order | Save | Validation field đúng, không truncate/silent round input | E | Chưa thử live |
| A04 | DRAFT version0, concurrent edit | Save version0 hai payload | Một thành công, một409 stale, không lost update | E | Chưa thử live |
| A05 | SO DRAFT sau confirm | Edit/confirm lặp key | Edit state denied; same key không tạo transition lặp | E | Chưa thử live |
| A06 | SO S và list >20, nhiều due dates | Search/filter/page | Page/count ổn định, due policy đúng, không cross-Plant/N+1 requirement | E | Chưa thử live |
| A07 | S F100, A available30, safety0 | Reserve30 | Held30,uncovered70,OnHand30/Reserved30/Available0,value giữ | E | Chưa thử live |
| A08 | Tracked F AVAILABLE/HOLD/REJECTED/expired lots | Auto/manual reserve | Chỉ eligible, exact lot trace; không vượt uncovered/free | E | Chưa thử live |
| A09 | Other SO/WO giữ stock | Reserve/release/cancel S | Không dùng hoặc release reservation người khác | E | Chưa thử live |
| A10 | Own reservation, lot đổi HOLD trước release | Release | Reserved giảm, OnHand/status giữ, không tăng eligible Available | E | Chưa thử live |
| A11 | CONFIRMED S chỉ own reserve | Cancel reason/version | SO CANCELLED + own release/audit atomic | E | Chưa thử live |
| A12 | S có downstream protected WO/PO conversion | Cancel |409 downstream, không reset coverage hoặc release một phần | E | Chưa thử live |
| A13 | Produced50/QC40/delivered unknown legacy | SO metrics | Không delivered40/FULFILLED; unknown rõ | E | Chưa thử live |
| A14 | DRAFT/confirmed/covered100/cancelled SO | Demand query | Chỉ confirmed lifecycle có uncovered>0; IDs riêng đúng | E | Chưa thử live |
| A15 | MRP start/end1,90,91 inclusive | Run |1/90 hợp lệ;91 bị lỗi period | E | Chưa thử live |
| A16 | S held30, available0, safety0 | Run | Gross70/net70, không trừ held30 lần nữa | E | Chưa thử live |
| A17 | S uncovered10, F available10 | Run | F net0, không explode component hoặc require Routing cho lấy tồn | E | Chưa thử live |
| A18 | F thiếu, W stock đủ một phần, shared N nhiều parent | Run | Chỉ net F/W explode, shared RAW pool net một lần | E | Chưa thử live |
| A19 | Same Item/kho dates2buckets gross10/10,available0,safety5 | Run | First net15/carry5; second net10, không second15 | E | Chưa thử live |
| A20 | Net1.000001/BOM M0.5/scrap0.02; thêm WIP exact1.0000001/RAW ratio100 | Run/WO convert | M cần0.510205; WIP WO1.000001 phải có RAW100.0001 theo Q28; split0.1+0.2 chính xác | E | Chưa thử live |
| A21 | Missing/default ambiguous/khoE cross-P | Run/manual override | Warning/blocked; chỉ override valid P cho run, Master unchanged | E | Chưa thử live |
| A22 | MAKE thiếu BOM/Routing/master/UOM | Run/convert | Blocked có source/code, không tạo WO hoặc qty giả | E | Chưa thử live |
| A23 | Inbound same/dateAfter/otherWh/otherUOM/draft/earmarked | Run | Chỉ committed due<=bucket matching được net một lần | E | Chưa thử live |
| A24 | PO confirmed40, receipt25 HOLD rồi QC20good/5reject | Rerun qua GĐ7 fixture | Open15 sau receipt; HOLD available0, sau QC20available; không đếm thêm25/5reject | E | Chưa thử live |
| A25 | Consumable projected3,point5,reorder20,safety10 | ROP/missing-policy variant |20 khi configured,0+blocked khi missing, không SO source | E | Chưa thử live |
| A26 | Snapshot old, edit Master/stock/BOM/SO | Read/convert old run | Old requirements giữ nguyên; conversion409 source stale | E | Chưa thử live |
| A27 | S net70 MAKE | Convert40 rồi30 | WO DRAFT40/30, same canonical, converted70/remain0; không reserve/firm | E | Chưa thử live |
| A28 | RootFG versus dependentW/safety | Convert W/root/safety | W không cover FG; root đúng SO/UOM; safety unallocated source | E | Chưa thử live |
| A29 | Remaining30, two concurrent keys20/20 | Convert | Tổng<=30, một conflict, không orphan WO/coverage | E | Chưa thử live |
| A30 | Conversion same key same/different payload | Retry | Same result/sameIDs hoặc idempotency conflict, không duplicate | E | Chưa thử live |
| A31 | BUY2lines cùng V/P/D, dates khác | Convert; incompatible/inactive variant | OnePO DRAFT sources/dates; mixed kho/kind/inactive supplier bị từ chối | E | Chưa thử live |
| A32 | New WO/PO draft + rerun | Supply query/run | Draft không firm; output kho/source link đúng; source stale policy giữ | E | Chưa thử live |
| A33 | Valid XLSX3rows/groupA2/groupB1 | Validate/commit/reload/retry | Exactly2SO DRAFT, quantities/dates/master đúng, same IDs retry/history | E | Chưa thử live |
| A34 | Same file groupA qty0; groupB valid | Validate/commit | INVALID; commit0SO, error từng row/group, không partial B | E | Chưa thử live |
| A35 | Wrong template/header/formula/size>5MiB/501rows | Validate | Specific codes, không thực thi formula hoặc tạo SO | E | Chưa thử live |
| A36 | READY then Customer/Item inactive; last group write fails | Commit | Stale/validation fail và rollback toàn batch/receipt/links | E | Chưa thử live |
| A37 | READ-only/revoked assignment/wrong Plant/other actor batch | All read/write/replay |403/404 theo policy, không cross scope, replay không bỏ auth | E | Chưa thử live |
| A38 | Failure between stock/coverage/document/receipt writes | Reserve/cancel/convert/import, then retry | DB rollback mọi phần, retry coherent; history/source không orphan | E | Chưa thử live |

Thứ tự: BE trả §5/OpenAPI/schema/version → GĐ2 masters + GĐ4 inventory + GĐ5 BOM/Routing/settings → SO/metrics/reservations/demand → MRP snapshots/supply → conversion canonical WO/PO DRAFT → SO import/history → FE adapter đã được PO duyệt → nghiệm thu A01–A38. GĐ7 bổ sung committed PO/receipt/QC thật rồi chạy A24 đầy đủ; trước đó dùng fixture BE có đánh dấu, không claim live workflow mua hoàn tất.

Migration/seed: giữ SO/WO/inventory cũ, không reset; link Customer bằng mapping đã chốt, không join theo tên mơ hồ. Không đổi fulfilled→delivered nếu không có shipment evidence. Mã generated phải không collide với code cũ. Snapshot và receipts/version migrations rollback được. Feature/module rollout riêng, contract cũ không âm thầm thay shape/meaning.

Chuyển mock→API chỉ sau mapping và acceptance nguồn authoritative; FE không tính lại net/server balance/fulfillment hoặc fallback mock khi API lỗi. Không emulation batch bằng create-loop. Nếu run async, status PENDING/RUNNING/COMPLETED/FAILED và polling/error contract cần BE trả; UI hiện mock sync không chứng minh async acceptance. **Live acceptance:** chưa thử. **Trạng thái:** chuẩn bị bàn giao/chờ contract, không hoàn tất tích hợp.

**Change log v1:** lần đầu export GĐ6 từ Session305; chưa có bản đã gửi trước đó.

## §4 — Hợp đồng lỗi mong đợi

**[Đề xuất BE]** các code descriptive bên dưới cần mapping code thực tế; không coi mock shorthand CUSTOMER/STALE/FORBIDDEN là wire đã chốt. Success JSON code/message/result required; lỗi code/message required, result có thể không có/null, errors optional array(field/message), trace ở response header. FE rẽ nhánh theo code, không chỉ HTTP; giữ input/filter/file/key phù hợp. Permission/validation/state không retry mù; network retry same key sau kiểm auth/current context.

| Tình huống | HTTP | code mong đợi | FE hiển thị/field | Input/key và retry |
|---|---|---|---|---|
| Thiếu quyền |403| PERMISSION_DENIED | Không đủ quyền thao tác | Giữ input; không retry khi chưa có quyền |
| Direct-ID trái scope/owner hoặc missing |404| NOT_FOUND | Không tìm thấy trong phạm vi | Đóng record state/refetch; không fallback |
| Plant/kho mismatch/inactive |400| INVALID_WAREHOUSE | Chọn kho hợp lệ cho nhà máy | Giữ các dòng; sửa mapping |
| Customer/Supplier inactive/cross-company |400| CUSTOMER_NOT_AVAILABLE / SUPPLIER_NOT_AVAILABLE | Chọn nguồn đang hoạt động | Giữ form; refetch selector |
| Item/UOM không hợp lệ |400| ITEM_NOT_AVAILABLE / INVALID_UOM | Sản phẩm/đơn vị không hợp lệ | Giữ input; không client override UOM |
| Qty không dương/precision/overflow |400| INVALID_QUANTITY | Số lượng không hợp lệ, errors field chỉ line | Sửa qty; submission mới key mới |
| Date/period lỗi |400| INVALID_DATE / INVALID_PLANNING_PERIOD | Ngày cần/kỳ tối đa90inclusive | Giữ date; sửa |
| Command state sai |409| INVALID_STATE | Chứng từ không ở trạng thái cho phép | Refetch state; không retry nguyên payload |
| SO draft aggregate version stale |409| VERSION_CONFLICT | Dữ liệu đã đổi; tải lại trước lưu | Giữ unsaved input để đối chiếu |
| Run source thay đổi |409| PLANNING_SOURCE_STALE | Nguồn đã đổi; cần lập kế hoạch mới | Giữ qty/supplier; không convert cũ |
| Reservation vượt free stock |409| INSUFFICIENT_AVAILABLE_STOCK | Tồn khả dụng không đủ | Giữ warehouse/lot/qty; refetch candidate |
| Reserve/convert vượt nhu cầu/remaining |409| DEMAND_QUANTITY_EXCEEDED | Vượt nhu cầu còn lại | Giữ qty; refetch coverage/ledger |
| Lot HOLD/REJECTED/hết hạn |409| LOT_NOT_ELIGIBLE | Lô không dùng được | Giữ form; chọn lại lot |
| Cancel có downstream |409| DOWNSTREAM_DOCUMENT_EXISTS | Cần xử lý chứng từ liên quan trước | Giữ reason; link downstream |
| Missing mapping/BOM/Routing/policy |409| PLANNING_PROPOSAL_BLOCKED | Nêu warnings[] và nguồn thiếu | Không retry đến khi nguồn/rerun hợp lệ |
| Same key khác payload |409| IDEMPOTENCY_KEY_CONFLICT | Mã gửi lại không khớp dữ liệu | Không tự lấy key mới để lặp unknown post |
| Upload không hợp lệ |400/413| INVALID_FILE / FILE_TOO_LARGE / INVALID_TEMPLATE / IMPORT_ROW_LIMIT_EXCEEDED | Lỗi tệp cụ thể | Giữ file; thay file theo lỗi |
| Row/group validation |200| SUCCESS, result.state=INVALID | Bảng lỗi từng dòng/group | Không commit; tải lỗi/sửa file |
| Commit INVALID/master stale/batch expired |409| IMPORT_BATCH_INVALID / IMPORT_SOURCE_STALE / IMPORT_BATCH_EXPIRED | Không nhập, cần kiểm tra lại | Không partial; validate mới |
| Storage/transaction/server failure |500/503| INTERNAL_ERROR / SERVICE_UNAVAILABLE | Chưa xác nhận kết quả, giữ trace | Same key khi retry; không tạo lại với key mới |

Ví dụ lỗi **đề xuất**, không response thật; không bịa X-Trace-Id trong JSON:

```json
{
  "code": "PLANNING_SOURCE_STALE",
  "message": "Source data changed. Recompute before converting.",
  "errors": [{"field": "expectedSourceVersion", "message": "Version does not match current sources"}]
}
```

Auth 401 refresh/logout dùng contract GĐ1 đã chốt, không redefine ở đây. BE trả field paths ổn định (`lines[0].orderedQuantity`, `entries[0].quantity`, row line number) để FE gắn đúng trường. Không coi error message string là code.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định. “CHẶN” nghĩa chặn adapter/live phần đó, không chặn FE/mock giai đoạn tiếp. Owner quyết định BE+PO; ngày/quyết định tất cả **chưa chốt**.

1. **Q1:** SO mới để server sinh orderNo được không? — FE nghiêng về Có, unique Company/Plant theo sequence để không collide legacy/import — **CHẶN draft create/migration**.
2. **Q2:** Coverage WO có bao gồm WO DRAFT chưa hủy được không? — FE nghiêng về Có để không chuyển lệnh trùng; coverage vẫn khác sản lượng, QC và giao hàng — **CHẶN metrics/demand migration**.
3. **Q3:** Due range list chọn theo earliestDueDate của SO được không? — FE nghiêng về Có theo projection mock, latestDueDate trả riêng để dễ nhìn — **CHẶN filter adapter**.
4. **Q4:** SO allocation dùng SO_MANAGE được không? — FE nghiêng về Có theo nghiệp vụ SO; không dùng WO reservation permission hoặc fake WO owner — **CHẶN reservation authorization**.
5. **Q5:** PO draft read/convert ở GĐ6 dùng SUPPLY_SUGGESTION_MANAGE/MRP_READ trước khi GĐ7 có quyền PO riêng được không? — FE nghiêng về Có cho seam hiện tại, kiểm Plant và không lộ giá/private data — **CHẶN PO draft authorization**.
6. **Q6:** SO/MRP được đọc bộ reference tối thiểu qua quyền nghiệp vụ thay vì bắt user có mọi Master READ được không? — FE nghiêng về Có với server projection/scope cụ thể, không mở toàn master snapshot — **CHẶN selectors/demand/supply authorization**.
7. **Q7:** API commercial run nhận salesOrderLineIds riêng, không coi là planningDemandId, được không? — FE nghiêng về Có; server resolve validated demand, giữ legacy demandLineIds nguyên nghĩa — **CHẶN MRP input adapter**.
8. **Q8:** Net dùng available đã trừ reservation và carry safety/supply như §3.5 được không? — FE nghiêng về Có; không trừ reserved lần nữa hoặc cộng safety mỗi dòng — **CHẶN netting acceptance**, BE trả mapping projectedAvailableQuantity hiện có.
9. **Q9:** Component dùng cùng master defaultSupply, root dùng defaultOutput, override theo run khi thiếu được không? — FE nghiêng về Có, không fallback kho Plant khác hoặc sửa Master — **CHẶN warehouse resolution**.
10. **Q10:** Khi root WIP và dependent WIP cùng bucket, giữ source contributions riêng trong bucket gộp được không? — FE nghiêng về Có để chỉ root portion mới cover SO tương ứng — **CHẶN mixed-source allocation acceptance**; mock hiện sourceKind là một scalar, không đủ policy server.
11. **Q11:** WO BLOCKED/COMPLETED còn open không được tính firm nếu ETA chưa được xác nhận còn khả thi được không? — FE nghiêng về Có theo authoritative supply eligibility; mock dùng status whitelist+dueDate nên chưa có ETA confidence — **CHẶN committed WO supply**.
12. **Q12:** Earmarked WO output bị loại khỏi free supply khi SO coverage đã giảm demand được không? — FE nghiêng về Có để không đếm cùng output hai lần — **CHẶN supply/demand reconciliation**.
13. **Q13:** ROP dùng configured reorderQuantity với công thức/legacy trace §3.5 được không? — FE nghiêng về Có theo mock, missing config chặn thay vì tự lấy point deficit — **CHẶN ROP adapter**.
14. **Q14:** Commercial conversion cho phép trực tiếp tạo DRAFT mà không approve suggestion riêng được không? — FE nghiêng về Có theo UX đã duyệt; giữ approve/reject contract legacy, không coi mock đã đáp ứng lifecycle server — **CHẶN conversion lifecycle**.
15. **Q15:** SO input giới hạn tối đa999999999 được không? — FE nghiêng về Có theo guard mock hiện tại; scale6 được mô tả riêng trong contract, BE báo NUMERIC overflow nếu khác — **CHẶN quantity range validation**.
16. **Q16:** Kho output MAKE bắt buộc trùng kho netting cho phần root SO trong GĐ6 được không? — FE nghiêng về Có khi chưa có planned transfer, tránh proposal ở kho khác bị coi đã bù shortage — **CHẶN multi-warehouse live**; mock hiện có metadata hai kho và cho chọn khác, chưa tự chuyển kho.
17. **Q17:** Lead time dùng calendar days và quá hạn trả warning không tự clamp ngày được không? — FE nghiêng về Có, thiếu execution/scheduler nên không giả ETA khả thi — **CHẶN date/supply policy**, mock chưa có late-date warning riêng.
18. **Q18:** Customer name/code snapshot ở SO được giữ sau rename Master được không? — FE nghiêng về Có để lịch sử ổn định, edit DRAFT lấy current master — **CHẶN Customer migration/snapshot**.
19. **Q19:** Import dates chuẩn ISO text là định dạng khuyến nghị, server hỗ trợ Excel Date theo date system workbook được không? — FE nghiêng về Có; không dùng timezone máy đổi ngày, reject ambiguous text — **CHẶN XLSX date adapter**.
20. **Q20:** Import batches/receipts/audit giữ theo policy chung GĐ3, file gốc có retention riêng được không? — FE nghiêng về Có; BE trả thời hạn trước rollout — **CHẶN retention rollout, không chặn mock**.
21. **Q21:** Chưa tự hoàn conversion quota khi target draft bị hủy, chờ explicit reverse command GĐ7/8, được không? — FE nghiêng về Có để không double convert; current SO cancel chặn khi có conversion trace — **CHẶN cancel/reverse acceptance**.
22. **Q22:** SO import history/detail/errors chỉ SO_READ + owner/Company/Plant, validate/commit thêm MANAGE được không? — FE nghiêng về Có; route FE hiện cần READ+MANAGE, sẽ align adapter — **CHẶN read-history authorization**.
23. **Q23:** Stale kiểm server source revisions trong cùng conversion transaction được không? — FE nghiêng về Có để tránh TOCTOU; mock fingerprint toàn nguồn khá bảo thủ — **CHẶN stale server contract**.
24. **Q24:** Khi có protected downstream, cancel SO phải chặn thay vì cascade/reset allocation được không? — FE nghiêng về Có theo scope hiện tại; danh sách status downstream chốt cùng WO/PO contract — **CHẶN cancel policy**.
25. **Q25:** Bỏ lotId trong SO reservation của Item tracked cho phép server tự phân bổ FIFO các lot eligible được không? — FE nghiêng về Có, response phải giữ lot IDs từng reservation; không bỏ tracking — **CHẶN auto-lot allocation**.
26. **Q26:** fulfilledQuantity của SO API legacy hiện có mang nghĩa QC accepted được không? — FE nghiêng về Có theo nguồn mock legacy; nếu BE xác nhận Không, cung cấp nghĩa thật để mapping, không tự coi là shipment — **CHẶN fulfillment migration**.
27. **Q27:** openQuantity legacy có thể giữ nguyên nghĩa hiện có và cung cấp uncoveredQuantity riêng theo §3.5 được không? — FE nghiêng về Có để không breaking change hoặc suy held/WO/delivery từ một số không đủ nguồn — **CHẶN demand field mapping**.
28. **Q28:** Net MAKE được chốt ceiling6 trước khi explode cấp con được không? — FE nghiêng về Có để child demand khớp quantity WO thực tế; các phép tính còn lại giữ exact đến biên quantity — **CHẶN calculation rounding acceptance**, ví dụ WIP/RAW tại §3.5.

## §6 — FE đã tự xử lý

**[Đã kiểm chứng FE]**

- Customer/Item/UOM hiện tại, form nhiều dòng rộng, dialog xác nhận ngắn và panel phải; state loading/empty/error, business labels VI/EN. Input/filter/quantity/supplier/file giữ sau lỗi; ngày native giữ đúng khi edit trường khác.
- SO-owned reservation records phân biệt WO owner; cùng stock source, không giảm On Hand/value khi giữ; tracked lot/expiry/HOLD/REJECTED guards. Cancel không reset WO coverage hoặc release reservation người khác.
- Metrics tách riêng: mock produced chưa có line evidence hiện —; legacy fulfilled trình bày QC accepted, legacy delivered unknown; new SO delivered0 chưa shipment. Legacy FULFILLED/PARTIALLY_FULFILLED không được dùng như chứng cứ giao khách; API branch cũ chưa được đổi nghĩa.
- Decimal exact net/coverage, shared demand/safety pool, net-only multi-level BOM, explicit warehouse/output mapping, ROP policy trace và committed supply seam. Defensive bounded traversal chống malformed mock khi tính MRP không thay server circular BOM activation gate.
- Immutable results/sources, canonical WO DRAFT và PO seam, partial conversion ledger/source checks, quantity6decimals. Draft WO có coverage không firm, dependent WIP không cover FG; snapshot current BOM/Routing copied, source stale chặn mutation.
- Shared mock persistence giữ lazy/legacy stores, master/inventory/design locks, staged rollback/receipt, scope/quyền kiểm sau lock và trước replay; invalidate/refetch sau ghi/reload. Không là DB/server auth hoặc đồng thời nhiều người dùng.
- SO import group/type/date/qty/size/header/version guards, all-group DRAFT-only commit, owner+Company+Plant history và retry. Không create-loop/auto-confirm/upsert hoặc fake API result.
- Giữ SO/MRP/WO API adapters hiện có, không gửi wire fields/endpoints mới chưa thống nhất; API errors không fallback tính mock. Chưa thêm dependency hoặc đổi API flags trong GĐ6.

**Cần rà khi BE chốt:** orderNo generation; open/fulfilled/coverage/delivery migration; selectors/RBAC; demand ID mapping; mixed-source contribution; approved suggestion vs direct DRAFT; output-net warehouse; expired/late ETA; Decimal/rate; import history READ/date/retention; cancel/reverse quota; authoritative server stale token. Không gỡ guard trước khi nghiệm thu dữ liệu server tương đương. PO duyệt công việc adapter trước code theo quy tắc hiện hành.

## §7 — Tham chiếu

Các file trước đã chuẩn bị, **chưa xác nhận PO đã gửi hoặc BE đã nhận**. Chỉ tên file để trao đổi; không cần BE có repo FE. Nội dung GĐ6 cần triển khai đã tự chứa phía trên.

- `BE_HANDOFF_GD_01_2026-10-09.md` — Auth/quyền/scope/email, đối chiếu đúng bản PO gửi.
- `FE_HANDOFF_GD_02_2026-10-12.md` — Customer/Supplier/Item/UOM/Plant/settings và master lifecycle.
- `FE_HANDOFF_GD_03_2026-10-12.md` — Nền batch import/owner/retention/idempotency; thêm SO grouping ở đây.
- `FE_HANDOFF_GD_04_2026-10-12.md` — Canonical kho/lô/reservation/valuation, atomic stock; GĐ6 không post stock quantity/value.
- `FE_HANDOFF_GD_05_2026-10-13.md` — BOM/Routing snapshot/outputQuantity/scrap scale, Plant production sources.
- `FE_HANDOFF_GD_06_MESSAGE_2026-10-14.md` — Tin nhắn bốn dòng PO gửi cùng tài liệu này.

### Checklist phát hành v1

- §0 có 3 dòng/P1 đúng luồng extension API; §1 có Ngoài phạm vi và new/modified screens.
- Không defect live: nêu đủ 5 mảnh thiếu, BE latest deploy chưa xác minh; không bịa request/trace/record/time hoặc coi demo là server evidence.
- JSON copy được, UUID minh họa, không secret; ví dụ S100/held30/net70/convert40+30 nhất quán, không dùng số demo thực làm fixture live.
- R01–R18 đúng/sai; field 5 cột; endpoint/method/input/result/page/quyền/atomicity/retry; công thức và numeric fixtures đầy đủ; rounding đa cấp chưa nghiệm thu được nêu riêng Q28.
- §4 code/HTTP/input/retry; §5 Q1–Q28 chỉ xác nhận, quyết định riêng/FE preference/mức chặn; §6 lớp phòng vệ/giới hạn; §7 không đường dẫn máy/link repo.
- A01–A38 chưa thử live, không đánh đạt; dependency GĐ7 supply thật rõ. Đã lưu v1 và tin nhắn 4 dòng, chưa gửi ngoài. Chỉ đóng BE task sau FE live acceptance; sửa sau gửi dùng v2 cùng task.

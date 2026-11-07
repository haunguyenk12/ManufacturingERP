# Giai đoạn 10 — Bàn giao Backend: Giao hàng, hóa đơn và thanh toán

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock GĐ10 hoàn thành; cần BE triển khai hoặc cung cấp mapping API cho giao hàng từng phần, giá bán SO, hóa đơn nội bộ và ghi nhận thanh toán/phân bổ.
- Ảnh hưởng: thủ kho, bán hàng và quản lý chạy được demo; ba workspace GĐ10 chưa dùng được bằng API đã xác minh, các module khác tiếp tục theo contract riêng.
- Mức ưu tiên: P1 — luồng GĐ10 bằng API chưa sử dụng được; không chặn FE/mock tiếp tục giai đoạn sau.

**Bản:** v1 · **Ngày dự kiến gửi:** 15/10/2026 · **Mốc FE:** Session317 ngày 15/10/2026 · **Phiên tài liệu:** Session318.
**PO:** duyệt phương án bằng “ok”, yêu cầu tạo tài liệu BE sau FE delivery. **FE owner/BE owner cá nhân:** chưa chỉ định.
**Trạng thái:** chuẩn bị để PO gửi, chưa gửi bên ngoài, chưa nghiệm thu BE. Nếu gửi ngày khác, đổi ngày/tên trước lần gửi đầu; thay đổi sau gửi dùng `_v2`, `_v3` trong cùng task.

Nhãn áp dụng toàn tài liệu:

- **[Đã kiểm chứng FE]:** đối chiếu source, tests và kết quả browser mock; không chứng minh DB/API đang deploy.
- **[Đề xuất BE]:** yêu cầu/path/DTO/quyền/mã lỗi chờ mapping và xác nhận BE. Có API tương đương thì trả mapping/OpenAPI, không xây trùng hoặc đổi contract cũ ngầm.
- **[Giả định]:** chính sách FE/demo đã chọn nhưng BE chưa xác nhận áp dụng trên server. PO duyệt FE không thay xác nhận kỹ thuật BE.
- **[Chưa kiểm chứng live]:** không có request BE trong phiên tài liệu. Mọi JSON/UUID/token phiên bản minh họa dưới đây đều là **ví dụ đề xuất**, không phải response/record server thật.

## §1 — Phạm vi

### 1.1 Toàn bộ 19 màn hình/phần mới và sửa

| # | Loại | Màn hình/phần | Dữ liệu/hành vi cần BE |
|---|---|---|---|
| 1 | Mới | Danh sách Giao hàng | Search mã/SO/khách/Item, trạng thái/kho/ngày, phân trang |
| 2 | Mới | Tạo/sửa phiếu giao | Một SO, Customer/Plant kế thừa, nhiều dòng/kho/lô, nguồn hợp lệ và quota |
| 3 | Mới | Chi tiết giao hàng | Header/dòng/source/movement/audit và quyền thao tác hiện tại |
| 4 | Mới | Ghi sổ/hủy nháp giao | Confirm, kiểm hiện tại và atomic ISSUE; hủy có lý do |
| 5 | Mới | Danh sách Hóa đơn bán hàng | Search/khách/SO/ngày/trạng thái/tình trạng thu tiền và tổng VND |
| 6 | Mới | Tạo/sửa hóa đơn | Một SO, nhiều delivery POSTED, phần chưa billed/claimed, giá khóa và preview |
| 7 | Mới | Chi tiết hóa đơn | Dòng nguồn, tổng/đã thu/còn phải thu, due date và phân bổ liên quan |
| 8 | Mới | Ghi sổ/hủy nháp hóa đơn | Quota delivery-line, current pricing/base/revision, không stock effect |
| 9 | Mới | Danh sách Thanh toán khách hàng | Search/khách/ngày/trạng thái/phương thức, số tiền và pagination |
| 10 | Mới | Tạo/sửa thanh toán | Một khách/Plant/VND, nhiều invoice, phương thức/tham chiếu và allocations |
| 11 | Mới | Chi tiết thanh toán | Số tiền/allocations/nguồn invoice/SO và lịch sử |
| 12 | Mới | Ghi sổ/hủy nháp thanh toán | Atomic balance/claim checks, không gọi chuyển tiền |
| 13 | Sửa | Danh sách/chi tiết SO | QC/đã giao/còn giao/đã invoiced/đã thu riêng, related documents và CTA |
| 14 | Sửa | Giá bán SO | Optional khi DRAFT, bổ sung confirmed missing price có audit, known price khóa |
| 15 | Sửa | Giữ hàng/nguồn SO | Quota chứng từ khác physical reserve và production coverage, đúng owner |
| 16 | Sửa | Tồn kho/lô/biến động | Delivery provenance/ISSUE/reservation consumption, giá vốn độc lập giá bán |
| 17 | Sửa projection | Hoạch định vật tư | Current demand trừ delivered và remaining source coverage, không sửa saved runs |
| 18 | Sửa liên kết | Receipt/QC/lô và WO nguồn | Related POSTED deliveries từ source thật, scope-aware links |
| 19 | Sửa | Sidebar/quyền | Ba module, VI/EN business labels, read/manage/post và price action |

FE routes chỉ mô tả màn hình, **không phải API BE**: `/manufacturing/deliveries`, `/manufacturing/sales-invoices`, `/manufacturing/customer-payments`. Detail panel phải với phần thân cuộn; form nhiều dòng và hộp thao tác ngắn giữa màn hình. BE không cần repo/màn hình FE để thực hiện các quy tắc bên dưới.

**Trong phạm vi:** delivery một SO/Customer/Plant, nhiều dòng/kho/lô trong Plant; invoice một SO có thể gồm nhiều delivery; payment một Customer/Plant/VND có thể phân bổ nhiều invoice. Draft claim/edit/cancel, atomic posting, SO price/progress, nguồn hàng/ownership, current permission/version/idempotency, projections, migration legacy và basic receivable/payment tracking.

**Ngoài phạm vi:** vận chuyển/vận đơn/POD/xác nhận khách đã nhận; actual bank transfer/gateway; legal e-invoice, VAT/thuế, FX, discount/phí; advance/unallocated cash, refund, return/credit note/reversal; sửa/xóa posted; AR aging/GL đầy đủ; full material/labor/overhead costing và close GĐ11; serial/MES; thay đổi CSV SO import cũ để thêm giá; tự đóng các câu hỏi GĐ6/GĐ8/GĐ9 còn mở.

**Môi trường [đã kiểm chứng FE]:** dev/demo mock, Session317 ngày 15/10/2026, Plant1, UI localhost cổng3004; desktop1280×720 VI, tablet834×1112 EN; timezone Plant dùng Asia/Ho_Chi_Minh trong demo. Working tree chưa commit, không đặt commit/tag giả. Session318 chỉ tạo tài liệu, không gọi BE/OpenAPI hoặc kiểm phiên bản deploy mới nhất.

### 1.2 Existing API đã đối chiếu và phần còn thiếu

**[Đã kiểm chứng FE source ngày 15/10/2026]:** base context có `/api`. SO là controller-base `/sales-orders/v1`, **không** suy thành `/v1/sales-orders`. Các paths đầy đủ sau chỉ chứng minh adapter FE hiện có, chưa chứng minh server đang deploy.

| Existing API source | Request/DTO FE hiện có | Giới hạn GĐ10 |
|---|---|---|
| GET `/api/sales-orders/v1` | companyId, optional plantId/status/search/page/size; page SO | Adapter hiện hydrate thêm detail từng dòng; chưa có delivery/billing/payment summary authoritative |
| GET `/api/sales-orders/v1/{salesOrderId}` | salesOrderId/companyId/plantId/orderNo/customerName/orderDate/status/note/version/lines/timestamps | DTO chưa có customerId, unitPrice/currency, baseline known, delivered/invoiced/paid/source versions |
| POST `/api/sales-orders/v1` | companyId/plantId/orderNo/customerName/orderDate/note; lines itemId/orderedQuantity/dueDate | Chưa gửi master Customer ID, giá hoặc stable line ID; không tự thêm field trước mapping |
| PATCH `/api/sales-orders/v1/{salesOrderId}` | expectedVersion/customerName/orderDate/note/lines full replacement | Request chưa giữ salesOrderLineId; phải bảo toàn ID hoặc chặn sửa khi downstream, không remap bằng thứ tự |
| POST `/api/sales-orders/v1/{salesOrderId}/confirm` | Idempotency-Key, chưa có body version mới | Không tự giả existing endpoint nhận snapshot/version GĐ10 |
| POST `/api/sales-orders/v1/{salesOrderId}/cancel` | reason và Idempotency-Key | Cần downstream guard toàn server, không chỉ ẩn nút |

SalesOrderLineDto hiện có salesOrderLineId/lineNo/itemId/itemSku/itemName/uom/orderedQuantity/fulfilledQuantity/openQuantity/dueDate. `fulfilledQuantity/openQuantity` cũ chưa phải bằng chứng shipped/billed/paid; adapter cũ không được dùng để gán delivered. Không tìm thấy adapter Delivery/Invoice/CustomerPayment trong phần nguồn đã khảo sát; workspace GĐ10 ở chế độ API hiện hiển thị unavailable, không gọi endpoint đoán hoặc fallback mock. Nếu BE đã có module này, trả mapping trước khi code trùng.

### 1.3 Luồng, quyền và scope

**[Đã kiểm chứng FE; đề xuất mapping BE]:** mã kỹ thuật dưới đây chỉ dùng contract/phân quyền; UI dùng nhãn nghiệp vụ. Read module = có READ hoặc MANAGE hoặc POST tương ứng, không biến POST thành MANAGE. Scope phải là Company/Plant/assignment còn hiệu lực và user/session đang hoạt động, kiểm cả direct ID/history/source/replay. Actor lấy principal server, không tin actor do client gửi.

| Thao tác | Trạng thái | Quyền đề xuất | Điều kiện/tác động |
|---|---|---|---|
| Đọc Delivery/minimal SO/stock source | Không đổi | PERM_DELIVERY_READ hoặc MANAGE hoặc POST | Scoped minimal projection, không sales price/invoice/payment/cost |
| Tạo/sửa/hủy Delivery | Mới→DRAFT; DRAFT→DRAFT/CANCELLED | PERM_DELIVERY_MANAGE | Sửa/hủy nháp của creator; claim SO quota, no physical stock effect |
| Ghi sổ Delivery | DRAFT→POSTED | PERM_DELIVERY_POST | Người có quyền được post cả phiếu của mình hoặc creator khác; current guards; atomic ISSUE |
| Đọc Invoice/delivery selector | Không đổi | PERM_SALES_INVOICE_READ hoặc MANAGE hoặc POST | Commercial money visible, minimal upstream source; không mặc nhiên full stockcost |
| Tạo/sửa/hủy Invoice | Mới→DRAFT; DRAFT→DRAFT/CANCELLED | PERM_SALES_INVOICE_MANAGE | Creator sửa/hủy; claim delivery-line, chưa receivable |
| Ghi sổ Invoice | DRAFT→POSTED | PERM_SALES_INVOICE_POST | Current base/price/quantity checks, receivable tăng, stock không đổi |
| Đọc Payment/invoice selector | Không đổi | PERM_CUSTOMER_PAYMENT_READ hoặc MANAGE hoặc POST | Invoice ID/code/customer/order code/date/amount/outstanding/claim tối thiểu, không SO unit prices |
| Tạo/sửa/hủy Payment | Mới→DRAFT; DRAFT→DRAFT/CANCELLED | PERM_CUSTOMER_PAYMENT_MANAGE | Creator sửa/hủy; claim allocation, chưa paid |
| Ghi sổ Payment | DRAFT→POSTED | PERM_CUSTOMER_PAYMENT_POST | Recheck customer/current invoice balances, atomic allocations/paid/history |
| Giá bán | SO DRAFT sửa giá; confirmed chỉ missing→known | PERM_SALES_PRICE_MANAGE | Version/audit; known confirmed price không sửa, kể cả price 0 |
| SO summary/related docs | Không đổi SO business status | PERM_SALES_ORDER_READ và quyền module liên quan | Price chỉ price-manage/invoice-authority; invoice totals chỉ invoice-authority; links theo authority |

Đúng action permission là cần thiết nhưng chưa đủ: scope, state, creator khi sửa/hủy, source/current refs vẫn kiểm. Không bypass theo tên role. Demo FE seed thêm quyền chỉ Admin/Manager đã biết; production mapping theo role/assignment BE quản lý, không tự grant mọi custom role. Không thêm queue phê duyệt hai cấp.

**[Đề xuất BE về thương mại]:** invoice-reader được xem aggregate paid/outstanding cần cho hóa đơn, nhưng full payment/detail allocation ngoài hóa đơn đòi payment authority. Payment selector chỉ cấp projection tối thiểu để phân bổ, không cấp quyền mở full invoice/SO. Delivery source có source IDs/code/quantity và link capability, không buộc cấp full WO/receipt/cost. Không có quyền tiền thì field bị **omit**, không trả0/null giả. Business-known field chưa biết do legacy có `known=false` và null có lý do, khác bị redacted.

## §2 — Bằng chứng tái hiện

### 2.1 Bằng chứng BE/live

**Không có — chỉ bàn giao FE/mock và đề xuất contract, không báo defect BE.**

| Mảnh evidence cùng lượt | Tình trạng |
|---|---|
| Request thật method/path/header/body | Không có request GĐ10 tới BE trong phiên này |
| Response thật + HTTP + code | Không có |
| X-Trace-Id từ response header | Không có, không tự tạo để điền |
| Môi trường BE + thời điểm + version | Chưa gọi/chưa xác minh bản deploy mới nhất; chỉ biết mốc FE ngày 15/10/2026, không ghi giờ BE giả |
| ID bản ghi BE thật | Không có; ID mock dưới đây không dùng làm record server |

```text
Mong đợi: BE cung cấp mapping/OpenAPI/bản deploy và fixture IDs để FE gọi API GĐ10, lưu evidence cùng request/record.
Thực tế: FE đã chạy mock; chưa có bằng chứng tích hợp server, mọi ca live ở §3 còn chờ.
```

Khi nghiệm thu/báo lỗi sau này: kiểm deploy trước, lưu request/response nguyên văn đã che credential, HTTP/code, X-Trace-Id, environment/time/version và record ID **cùng một run**. Business provenance ID/movement ID không thay X-Trace-Id HTTP.

### 2.2 Đã quan sát FE/mock, không phải kết quả BE

Ngày15/10/2026, Session317, khách demo `CUS-GD10-QA`, SO id7/code `SO-2026-0007`, reference `DEMO-GD10-20261015`, FG-00110PCS×100000VND. Không ghi giờ browser chính xác vì chưa lưu evidence giờ đó. DN1 id`deliveries-1` POSTED3 từ childlot20/PR1; DN2 id`deliveries-2` POSTED1 từchild22/PR1 và4 từchild23/PR2. INV1 id`invoices-1` total800000; PAY1 id`payments-1`300000 vàPAY2 id`payments-2`500000 POSTED. Kết quả SO delivered8/remaining2/invoiced8, invoice paid800000/outstanding0.

SO mới không phân bổ vào WO thủ công cấp stock, nên SO QC accepted riêng vẫn0; không nhận vơ accepted8 của WO vào SO. Nguồn QC mới accepted8/rejected1; opening8 ở kho khác còn riêng, không tuyên bố tổng tồn toàn kho còn1. DN3 id`deliveries-3` nháp1 từ openinglot rồi hủy, không post/không đổi tồn/tiền; reload giữ cancelled ID. Plant2 invoice list rỗng, Plant1VI khôi phục; lot20physical0 link đúngDN1; desktop/tablet đã kiểm. Tất cả là ID/mock FE, không UUID BE.

Đã chạy trong Session317: full105files/590tests, sau sửa URL thêm regression chạy39focused và sau compactdialog9UI; production build/TypeScript46pages, i18n parity và Phase10lint đạt. Full590 trước test navigation cuối, không gọi là fresh591. Full lint0errors/45733warnings chủ yếu CRLF có sẵn; initial full defaultworkers21timeouts rồi bounded/full đạt; WindowsbuildEPERM resolved bằng rerun quyền phù hợp. Session318 tài liệu không chạy lại application tests/build và không coi test mock là live acceptance.

## §3 — Đầu ra mong đợi — Cần BE triển khai

Toàn bộ §3 là **[Đề xuất BE]**, xuất phát từ FE đã được PO duyệt. BE có chức năng tương đương thì cung cấp mapping; §5 chỉ yêu cầu trả lời quyết định. “Chặn FE?” ở bảng là **chặn tích hợp/live GĐ10**, không chặn FE/mock GĐ11.

### 3.1 Yêu cầu kiểm được

| # | Yêu cầu đúng/sai kiểm được | Endpoint/phạm vi đề xuất | Chặn FE? |
|---|---|---|---|
| R01 | SO mới có customerId ổn định, stable salesOrderLineId và baseline0 đã biết; legacy không tự chuyển fulfilled thành delivered | SO + fulfillment summary | Có |
| R02 | Giá VND optional trên SO DRAFT; missing khác0; confirmed chỉ fill missing, known price locked/audited | SO + commercial-prices | Có invoice |
| R03 | Mỗi list có page0/size20/totals/filter theo scope trước pagination; detail/source/history direct ID không lộ Plant khác | Ba collections/detail/reads | Có |
| R04 | Delivery candidates trả lượng chưa giao, other draft claims và source eligible/free/own reserve/entitlement, không money | delivery-candidates | Có |
| R05 | Delivery một SO/Customer/Plant, nhiều lines/kho/lô trong Plant, positive exact6 và tổng theo SOline≤quota | delivery create/edit/post | Có |
| R06 | Draft Delivery chỉ giữ document quota; edit loại own claim; cancel một lần trả quota và giữ reason/history, không ISSUE hoặc reserve mới | delivery create/edit/cancel | Có |
| R07 | Post Delivery recheck refs/customer/SOstate/lô/expiry/quantity/ownership hiện tại, không dựa preview cũ | delivery post | Có |
| R08 | ISSUE chỉ own reservation hoặc entitled free, không other SO/WO reserve/earmark; nhiều dòng không tái dùng cùng pool | delivery post + sources | Có |
| R09 | POSTED Delivery/negative movements/own reserve/stock/delivered/source/history/replay commit hoặc rollback cùng transaction | delivery post | Có |
| R10 | Delivery không dùng giá bán cho cost; known stockcost theo ledger, unknown remainsnull và permission redacted | inventory movement/value | Có |
| R11 | Invoice chỉ từ POSTED delivery cùng SO/Customer/Plant/VND; partial/multiple delivery allowed, unique deliveryLineId | invoice create/edit/post | Có |
| R12 | Invoice drafts claim unbilled quantity theo deliveryLineId, edit loại own claim/cancel trả claim, không AR/stock | invoice create/edit/cancel | Có |
| R13 | Invoice price lấy SOline locked; missing chặn, price 0 tạo zeroinvoice không payment giả | invoice preview/save/post | Có |
| R14 | Amount cộng dồn HALF_UP integerVND theo SOline; lines sum=header; nguồn/base đổi phải conflict giữ input/resave/reconfirm | invoice preview/save/post | Có |
| R15 | Post Invoice atomic allocations/invoice/billed/receivable/history/replay, không ISSUE hay paid | invoice post | Có |
| R16 | Payment một customer/Plant/VND, amount>0 nguyênVND bằng tổng unique invoice allocations>0, chỉ invoicePOSTED | payment create/edit/post | Có |
| R17 | Payment amount không vượt posted outstanding trừ other draft claims; draft không thay authoritative paid/outstanding | payment candidates/save/post | Có |
| R18 | Payment post rechecks balances/revisions/refs và atomically allocations/paid/outstanding/invoiceevents/replay; không bank/stock | payment post | Có |
| R19 | Posted documents bất biến; edit/cancel/delete qua đường khác đều bị chặn; cancelled không được tái post | ba lifecycle | Có |
| R20 | Current auth sau đợi lock và trước replay; direct ID, projections và commands đều kiểm Company/Plant/user/assignment | mọi API | Có |
| R21 | Idempotency cùng scope/principal/key/payload không lặp effect; khác payload conflict; mất response retry cùngkey an toàn | mọi mutation | Có |
| R22 | Expected doc/source/price/balance revisions và DB row locks/unique constraints bảo vệ cạnh tranh cùng quota/stock/base/balance | mọi mutation | Có |
| R23 | Actor/time/reason/source/audit do server ghi; history phân trang, source provenance không suy theo Item giống nhau | detail/history/source | Có |
| R24 | SO QC/production legacy/delivered/invoiced/paid độc lập; PAID invoice không autoSOclosed/delivered đủ | SO summary | Có |
| R25 | Current MRP trừ delivered/remaining source coverage, không bù lại lượng held đã ship; saved runs không bị viết lại | planning input/projections | Có |
| R26 | Có downstream draft/posted chưacancel thì SOcancel bị chặn; upstream price/item/line/customer không đổi phá source | SO + commands | Có |
| R27 | Date≤PlantToday; delivery≥SOdate, invoice≥all source delivery dates, payment≥all allocated invoice dates; due≥invoice | mọi save/post | Có |
| R28 | Minimal source selectors và permission money/cost tách riêng, không leak qua history/replay/related links | mọi read/command | Có |
| R29 | Legacy migration giữ IDs/history, flags unknown và read-only/chặn nguồn không đủ bằng chứng, không fake0 | migration/candidates | Có |
| R30 | API có capability/version/error envelope, X-Trace-Id và OpenAPI kèm deployversion để FE tích hợp không fallback | rollout | Có |

### 3.2 Contract chung và kiểu dữ liệu

- **IDs đề xuất:** UUID string cho Company/Plant/Customer/SO/SOline/Item/Warehouse/Lot/Receipt/WO/document/line/movement/actor; code/orderNo chỉ nhãn. Mock hiện trộn number/string, BE không phải copy ID mock. Stable source line ID không suy từ index, không gửi actor/company tự chọn trái principal.
- **Versions:** số nguyên an toàn JSON≥0 (đề xuất≤9007199254740991), tăng đơn điệu khi document thay đổi; snapshot version là opaque string do BE phát hành, không FE parse/ghép inventory revision. Pricing/billing/claim/source/availability revisions phải đại diện trạng thái liên quan, không chỉ documentversion.
- **Quantity:** decimal string canonical, positive input≤999999999, scale≤6; không scientific notation/comma/NaN/âm/0; không âm thầm round input quá scale. Tổng/công thức decimal exact. **Unit price:** decimal string0..999999999, scale≤6. **Amount:** decimal string nguyên0≤amount<10^18; payment/allocation>0; intermediate và total overflow đều chặn. Client Number không authoritative.
- **Dates:** YYYY-MM-DD hợp lệ theo Plant timezone IANA; không đoán UTC day của browser. Timestamp ISO8601 có offset hoặc UTC Z; ngày/hạn theo date riêng. Expiry cutoff/Plant fallback chờ Q19/Q20. Delivery cho physical stock hiện tại, không replay kho quá khứ do nhập document date lùi.
- **Required/optional:** create bỏ documentId/expectedVersion; edit dùng ID trên path và required expectedVersion. Server derive customer/Item/UOM/price/provenance từ source; reject mismatch nếu client gửi. `note` optional/empty, trimmed≤2000; `reference` optional/empty≤200; cancel reason trimmed5..2000; 1..100 lines/allocations, price action1..500 unique SOlines. Optional metadata absent không bắt null; nullable fields dưới có nghĩa rõ. Currency chỉVND.
- **Statuses:** DRAFT/POSTED/CANCELLED. Delivery progress NOT_DELIVERED/PARTIALLY_DELIVERED/DELIVERED; paymentState UNPAID/PARTIALLY_PAID/PAID/NO_AMOUNT_DUE độc lập invoice status. POSTED meaning đã xuất giao khỏi kho, không xác nhận POD. Lịch sử action SAVE/POST/CANCEL/PRICE/PAYMENT có actor/time/reason; allocation không đổi invoice face amount.
- **Envelope:** code/message required; success `result` required với shape tương ứng; lỗi không cần result; `errors` optional array `{field,message}`. Existing NON_NULL serialization có thể omit result/errors không áp dụng, không bắt lỗi phải có null. List result `{content,page,size,totalElements,totalPages,first?,last?}`; page0,size20 (đề xuấtmax100), sort ổn định dateDESC/idDESC. Tất cả HTTP response gửi X-Trace-Id, CORS expose trace và Retry-After khi cần.
- Header mutation: Authorization (credential che khi chia sẻ), X-Plant-Id UUID và Idempotency-Key UUIDv4; Company từ authenticated context hoặc selector đã authorise. plantId body/query phải khớp header/path/currentscope, không dùng body để đổi tenant. Detail có ownPlant cũng vẫn scope-check. Không thêm X-Company-Id vào existing contract khi BE chưa thống nhất.

### 3.3 Field yêu cầu — đủ kiểu/nullability/nguồn/UI

Các bảng mô tả **wire đề xuất**, không ép tên member mock. `?` = optional; “omit khi không quyền” khác null unknown; BE chốt mapping tại §5.

| Field | Kiểu | Nullable | Nguồn giá trị/công thức đề xuất | Dùng ở UI |
|---|---|---|---|---|
| documentId/code/type/status | UUID/string/DELIVERY hoặc INVOICE hoặc PAYMENT/enum | Không | Server identity/code và state machine | Lists/detail/links |
| companyId/plantId/customerId/customerName | UUID/UUID/UUID/string | Không với document mới | Scoped source SO/customer master; name snapshot | Header/filter |
| salesOrderId/orderNo | UUID/string | Không delivery/invoice; không có header payment | SO nguồn; payment qua invoice allocations | Nguồn/related |
| version | safe integer | Không | Document optimistic version | Edit/post/cancel |
| sourceSnapshotVersion | opaque string | Draft cần token hợp lệ; null khi nguồn invalid và có reason | Snapshot source/claims/base/balances hiện tại do BE ký/quản lý | Confirm/retry |
| documentDate/note | date/string | Không, note có thể empty | User input qua validation | Form/detail |
| createdBy/createdByName/createdAt | UUID/string/timestamp | Không mới; legacy có thể null và flag | Principal/time server | History/header |
| postedAt | timestamp | Có khi chưa POSTED | Server commit event | Detail |
| capabilities | object booleans + blockedReasons[] | Không | Current permission/state/owner/source, chỉ UX hint | Nút/giải thích |
| history events | page eventId/action/actorId/name/at/reason | reason empty hợp lệ, legacy actor có thể null rõ lý do | Immutable server events, quyền scoped | History |
| baselineKnown/unknownReason | boolean/string | Reason null khi known | Migration/reconciliation evidence, không suy legacy fulfilled | SO/progress/block |
| unitPrice/currency/pricingVersion | decimal/stringVND/opaque | Price null nếu chưa thiết lập; omit cả commercialfields nếu trái quyền | Source SOline locked, không stockcost | SO/Invoice |
| orderedQuantity/qcAcceptedQuantity | decimal string | QC có thể null nếu legacy unknown | SO đặt và QC có attributable allocation riêng | SO progress |
| deliveredQuantity/remainingToDeliver/draftDeliveryClaim | decimal string | Null khi baselineunknown | Posteddelivery sum; ordered−delivered; active draft sum | SO/candidate |
| ownDraftClaim/otherDraftClaims/availableToClaim | decimal string | Null khi unknown | Exclude documentId được server kiểm owner/scope | Edit candidate |
| sourceKey/sourceSnapshotVersion | opaque string | Không nguồn hợp lệ | BE source handle, không tin cấu trúc client | Delivery source |
| warehouseId/name/itemId/code/name/uom | UUID/string/UUID/string/string/string | Không | Current active master + source snapshots | Lines/selectors |
| lotId/lotCode | UUID/string | Có chỉ NON_TRACKED | Physical selectedlot; không fake lot cho mergedpool | Delivery/trace |
| productionReceiptId/code/workOrderId/code | UUID/string/UUID/string | Có nếu nguồnopening/purchase không có production source | Actual provenance, không đoán Item giống nhau | Source links |
| sourceEligibleQuantity/freeQuantity/ownReservedQuantity/entitledQuantity/availableForLine | decimal string | Null khi source evidence unknown; không cho chọn | Eligibility/physical/owner/source caps, không cộng các alias như độc lập | Source validation |
| deliveryLineId/salesOrderLineId/quantity | UUID/UUID/decimal string | Không | Stable line/source quantity | Delivery/invoice |
| stockMovementId | UUID | Có trướcpost; posted cần actualmovement | Atomic ISSUE event | Stock trace |
| deliveryId/code/deliveryLineId | UUID/string/UUID | Không invoice line | POSTED delivery source | Invoice sources |
| postedInvoicedQuantity/draftInvoicedClaim/unclaimedQuantity | decimal string | Không known delivery source | Posted invoice +draft quota, exclude own edit | Invoice selector |
| invoiceLineId/unitPrice/amount | UUID/decimal string/integer stringVND | Không postedinvoice; price missing chặn | Source locked price/cumulative rounding | Invoice lines |
| billingSnapshotVersion/billedQuantityBefore/billedAmountBefore | opaque/decimal/integerVND | Không preview/invoice draft hợp lệ | Posted billing cumulative source perSOline | Preview conflict |
| amount/currency/dueDate | integerVND/VND/date | Không invoice; payment không dueDate | Line sum/due≥date | Invoice totals |
| paidAmount/outstandingAmount/paymentState | integerVND/integerVND/enum | Null nếu legacybalanceunknown; omit nếu trái quyền | Posted allocations only, amount−paid | Invoice/SO |
| draftPaymentClaim/availableToAllocate | integerVND | Null nếu balanceunknown | Outstanding−otherdraftclaims, neverpaid | Payment selector |
| method/reference | CASH hoặc TRANSFER hoặc OTHER/string | Không, reference empty được | Manual record, reference không unique mặc định | Payment |
| allocationId/invoiceId/invoiceCode/amount | UUID/UUID/string/integerVND>0 | Không | Actual atomic allocation | Payment/detail |
| relatedPayments[].allocatedAmount | integerVND | Không khi được phép | Chỉ phần payment phân bổ invoice này, không toànpayment amount | Invoice related list |
| deliveryProgress/invoicedQuantity | enum/decimal | Null khi legacyunknown | Posted documents, không fulfillmentlegacy | SO list/detail |
| invoicedAmount/paidAmount/outstandingAmount | integerVND | Null nếu unknown; omit nếu khônginvoiceauthority | Posted invoices và related posted allocations | SO totals |
| sourceCoverageRemaining/mrpOpenDemand | decimal string | Null/block nếu legacycoverageunknown | Source-aware formula §3.5 | MRP input |
| unitCost/issuedValue/valuationStatus | decimal hoặc integer theo inventorycost contract/stringenum | Cost/value null khi unknown; omit khi nocostpermission | Canonical stock ledger, không unitPrice bán | Inventory only |
| relatedDocuments/relatedDeliveries | scoped minimal array ID/code/status/date/linkAllowed | Không mới; không giả empty khi source unavailable | Actual source links, POSTEDonly cho receipt/lot/WO | SO/source |

Capabilities không thay server authorization. Document commands trả identity/version tối thiểu, FE refetch detail có quyền; không replay lại full money payload đã cache khi user mất quyền đọc. Bản posted cần snapshot cũ lẫn refs hiện tại khi còn action upstream; source snapshot không được sửa làm mất lịch sử.

### 3.4 API đề xuất — method/path đầy đủ

Không endpoint mới nào ở bảng đã được xác minh deploy. Root mới đề xuất `/api/v1`; SO extension dùng existingroot `/api/sales-orders/v1`. BE có root khác thì xác nhận mapping, không yêu cầu đổi API hiện có chỉ để giống bảng. Các list `L` chung query `search,status,customerId,fromDate,toDate,page,size,sort`; thêm `salesOrderId` cho delivery/invoice, `warehouseId` delivery, `paymentState` invoice, `method,invoiceId` payment. Date inclusive. Emptyresult là page rỗng, không lỗi. Trạng thái enum validation400.

Mọi read scoped theo §1.3; mọi mutation cần Idempotency-Key, server principal, current refs/state/version và atomicity. `D`=delivery authority, `I`=invoice authority, `P`=payment authority; read = READ/MANAGE/POST, mutation nêu action riêng. Mọi candidates/history/source có page/size; payment allocations tối đa100 không là paging thay persisted lines.

| Endpoint đề xuất | Query/body | Success result | Phân trang | Quyền | Idempotency |
|---|---|---|---|---|---|
| GET `/api/v1/deliveries` | L + salesOrderId/warehouseId | page DeliverySummary | Có | D read | Không |
| GET `/api/v1/deliveries/{deliveryId}` | ID | DeliveryDetail + sourceSnapshotVersion/capabilities | Lines≤100 | D read | Không |
| GET `/api/v1/deliveries/{deliveryId}/history` | page/size | page Event | Có | D read | Không |
| GET `/api/v1/deliveries/{deliveryId}/sources` | page/size | page ActualSource + movement/linkcapability | Có | D read | Không |
| GET `/api/sales-orders/v1/{salesOrderId}/delivery-candidates` | optional salesOrderLineId, editingDeliveryId, page/size | page SOline + claims; source đọc riêng bên dưới | Có | D read | Không |
| GET `/api/sales-orders/v1/{salesOrderId}/lines/{salesOrderLineId}/delivery-sources` | editingDeliveryId,warehouseId,page/size | page SourceChoice, current source token | Có | D read | Không |
| POST `/api/v1/deliveries` | salesOrderId/expectedOrderVersion/sourceSnapshotVersion/documentDate/note/lines[{salesOrderLineId,sourceKey,quantity}] | CommandResult | Không | D MANAGE | Có |
| PATCH `/api/v1/deliveries/{deliveryId}` | expectedVersion + same immutable source fields/lines/current tokens | CommandResult | Không | D MANAGE+creator | Có |
| POST `/api/v1/deliveries/{deliveryId}/post` | expectedVersion/sourceSnapshotVersion | CommandResult | Không | D POST | Có |
| POST `/api/v1/deliveries/{deliveryId}/cancel` | expectedVersion/reason | CommandResult | Không | D MANAGE+creator | Có |
| GET `/api/v1/sales-invoices` | L + salesOrderId/paymentState | page InvoiceSummary | Có | I read | Không |
| GET `/api/v1/sales-invoices/{invoiceId}` | ID | InvoiceDetail + balances/sourceToken/capabilities | Lines≤100 | I read | Không |
| GET `/api/v1/sales-invoices/{invoiceId}/history` | page/size | page Event | Có | I read | Không |
| GET `/api/sales-orders/v1/{salesOrderId}/invoice-candidates` | editingInvoiceId,page/size | page posted DeliveryLine + price/postedbilled/draftclaim/cap | Có | I read | Không |
| POST `/api/v1/sales-invoices/preview` | salesOrderId/expectedOrderVersion/editingInvoiceId?/lines[{deliveryLineId,quantity}] | PricingPreview + billingSnapshotVersion/lineamounts/totals | Không | I MANAGE | Không effect; không giữ claim |
| POST `/api/v1/sales-invoices` | salesOrderId/expectedOrderVersion/billingSnapshotVersion/documentDate/dueDate/note/lines | CommandResult; GETdetail có canonicalamount/sourceToken | Không | I MANAGE | Có |
| PATCH `/api/v1/sales-invoices/{invoiceId}` | expectedVersion + same save fields/current preview | CommandResult | Không | I MANAGE+creator | Có |
| POST `/api/v1/sales-invoices/{invoiceId}/post` | expectedVersion/sourceSnapshotVersion | CommandResult | Không | I POST | Có |
| POST `/api/v1/sales-invoices/{invoiceId}/cancel` | expectedVersion/reason | CommandResult | Không | I MANAGE+creator | Có |
| GET `/api/v1/sales-invoices/{invoiceId}/payments` | page/size | page PaymentRef + allocatedAmount/status/date | Có | I read+P read | Không |
| GET `/api/v1/customer-payments` | L + invoiceId/method | page PaymentSummary | Có | P read | Không |
| GET `/api/v1/customer-payments/{paymentId}` | ID | PaymentDetail + allocations/sourceToken/capabilities | Allocations≤100 | P read | Không |
| GET `/api/v1/customer-payments/{paymentId}/history` | page/size | page Event | Có | P read | Không |
| GET `/api/v1/customer-payments/invoice-candidates` | customerId,editingPaymentId,page/size | page minimal posted InvoiceBalance + claim/token | Có | P read | Không |
| POST `/api/v1/customer-payments` | customerId/documentDate/method/reference/note/amount/allocations[{invoiceId,expectedInvoiceVersion,amount}] | CommandResult | Không | P MANAGE | Có |
| PATCH `/api/v1/customer-payments/{paymentId}` | expectedVersion + same save fields | CommandResult | Không | P MANAGE+creator | Có |
| POST `/api/v1/customer-payments/{paymentId}/post` | expectedVersion/sourceSnapshotVersion | CommandResult | Không | P POST | Có |
| POST `/api/v1/customer-payments/{paymentId}/cancel` | expectedVersion/reason | CommandResult | Không | P MANAGE+creator | Có |
| POST `/api/sales-orders/v1/{salesOrderId}/commercial-prices` | expectedVersion/prices[{salesOrderLineId,unitPrice}] | SO CommandResult + pricingVersion | Không | SALES_PRICE_MANAGE | Có |
| GET `/api/sales-orders/v1/{salesOrderId}/fulfillment-summary` | ID | known/lineprogress/allowedtotals/scopedrelated/capabilities | SO lines; related page riêng nếu dài | SO READ+fieldauthority | Không |
| GET `/api/v1/deliveries/related` | một receiptId hoặc workOrderId hoặc lotId,page/size | page posted DeliveryRef minimal | Có | D read | Không |

Customer minimal selector cần active customers trong Company/Plant đã authorise, Item/kho/lot từ source; dùng existing reference API nếu đủ, không yêu cầu P/D user đọc toàn bộ adminCustomer/WO. Nếu có nhiều source filters trên `/related`, phải định nghĩa AND/OR, FE hiện chỉ gửi một loại (§5).

`CommandResult` = `{documentId,type,version}` hoặc với giá `{salesOrderId,type:"SALES_PRICE",version,pricingVersion}`. Mutation HTTP200/201 tùycreate/command (đề xuất201create/200edit/post/cancel), replay trả cùng identity/version không thêm effect; codeSUCCESS. Createpreview POST đọc không mutate nên không cần Idempotency-Key; headers auth/Plant vẫn bắt buộc. GET detail và source có thể cung cấp preview chung thay endpointpreview nếu BE bảo toàn stale/reconfirm contract.

### 3.5 Công thức, ownership và transaction

Ký hiệu theo **source IDs trong scope**, không group chỉ Item/code. D=posted delivered, Cd=other active draft delivery claims; B=posted invoiced quantity theo delivery-line, Ci=other draft invoice claims; P=posted payment allocations, Cp=other draft payment claims.

| Số liệu | Công thức/điều kiện | Ví dụ kiểm được |
|---|---|---|
| Remaining delivery | ordered−D; inconsistent âm thì lỗi, UI không che âm bằng0 để cho post | ordered10,D8→remaining2 |
| Delivery draft capacity | ordered−D−Cd; exclude draft đang sửa theo ID đúng scope | ordered10,D3,otherdraft5→cap2; sửa own5 không trừ own5 lần nữa |
| Physical eligible/free | EligibleOnHand−all active reservations trên đúng position; ownReserved là phần SOline này trong số đó | eligible8,totalReserved5,own2→free3,physicalusable5, không free+allreserved=8 |
| Available for selected source | min(physicalfree+own, SOquota, accepted-source credit còn, own production allocation entitlement còn) tùy loại nguồn | ownaccepted allocation4,alreadyshipped1,physical5→maxsource3 |
| Invoice capacity | deliveryLinePostedQty−B−Ci | delivery5,billed2,otherdraft1→cap2 |
| Invoice incremental amount | ROUND_HALF_UP((SOlineBilledQtyBefore+newQty)×lockedPrice,0)−SOlineBilledAmountBefore | 1PCS×1VND ba phần .333333/.333333/.333334→0/1/0, tổng1 |
| Invoice total | sum line incremental amounts; cùngSOline xử lý thứ tự source lines xác định, cập nhật runningbase | 3+1+4PCS×100000→300000+100000+400000=800000 |
| Paid/outstanding | Paid=sum POSTED allocations; outstanding=posted invoice amount−Paid | 800000−300000=500000; sau500000→0 |
| Payment capacity | outstanding−Cp, editexcludeown | outstanding500000,otherdraft200000→cap300000 |
| Payment amount | sum positive unique invoice allocations = positiveheaderamount | 100000 vàoI1 +200000 vàoI2→payment300000 |
| SO reserve cap mới | ordered−postedDelivered−currentOwnHeld, còn phải thỏa physical/source entitlement | ordered10,delivered8,held0→cap2, không trừ productionallocated để chặn hàng đã sản xuất của chính đơn |
| Current MRP open demand | max(0,ordered−D−ownHeld−remainingProductionCoverage); coverage giảm theo matched allocation source, không trừ delivered từWOkhác | allocated10,sourceShipped3→remainingcoverage7; ordered10,D3,held0→demand0, không bù3 |

Mock current MRP dùng `max(0,ordered−held−delivered−max(0,allocated−productionSourcedDelivered))` trên known baseline. **[Giới hạn FE/đề xuất BE]** BE cần đối chiếu delivered từ đúng allocation/WO, tránh trừ một quantity nhiều coverage; general earmark/metric policy GĐ6 chưa được đóng bởi công thức demo. Nếu đã có physical-held cho phần coverage đó phải canonical netting loại overlap, không trừ cùng unit hai lần. Delivered từ stock free/manualWO không có allocation cho SO không giảm coverage củaWOkhác. Saved runs/historical decisions giữ snapshot cũ; chỉ current calculations đổi.

**Nguồn stock:** tracked child lot liên kết receipt/WO/QC thật. Quantity nguồn dùng được phải thỏa physical eligibility, accepted contribution chưa ship và allocation entitlement còn lại của đúng SO-line khi WO có demand allocations. Manual WO không allocation có thể cấp free stock nếu đủ evidence. Không nhận stock của SO khác chỉ vì cùng Item.

NON_TRACKED dùng physical pool chung nhưng cần ledger riêng cho entitlement/provenance, không tạo lot giả. Nếu pool có earmark và reservations không rõ receipt attribution, mock chặn generic reserve hoặc nguồn không đủ bằng chứng. BE cần chặn an toàn hoặc có đối soát chính thức; không lấy toàn bộ accepted như free. Source unavailable cần reason, không biểu diễn unknown bằng “hết hàng 0”.

**Posting/atomicity:** lock source quota/SO-lines, inventory positions/reservations/quality/source contributions, invoice billing base hoặc invoice allocation balances theo thứ tự ổn định; đọc lại auth/refs/state/version/claims dưới lock. Delivery nhiều dòng phải dùng remaining pool sau staging từng dòng. Transaction bao gồm document/lines/stock/value/reservations/delivered/source claims/history/replay, cùng commit hoặc rollback. Invoice atomic cho invoice/source billing/receivable/history/replay, không stock effect. Payment atomic cho payment/allocations/invoice balances/events/replay, không stock hoặc chuyển tiền. DB constraints chống duplicate không thay validation. Generic ISSUE hoặc SO cancel qua API cũ không được bypass owner/source/downstream guards.

Draft claim tồn tại theo trạng thái DRAFT, mock chưa TTL, không mất khi logout/reload. Hai nháp tranh quota phải serialize; preview không giữ quota. Post chuyển own claim sang posted usage trong cùng commit, không release rồi acquire lại tạo kẽ hở. Cancel trả claim một lần: key cũ replay thành công không lặp effect; key mới trên CANCELLED trả state error. Posted invoice tăng version/history khi payment post nhưng face amount/lines bất biến. Base đổi ảnh hưởng tiền phải preview/resave/reconfirm, không đổi amount âm thầm trong lần bấm cũ.

**Idempotency:** scope tối thiểu Company+Plant+principal+key, fingerprint method/resource/payload canonical. Cùng key khác command/resource/body trả409 IDEMPOTENCY_CONFLICT; cùng intent retry sau mất response trả cùng identity/version. Kiểm current auth trước replay, kể cả quyền đã thu hồi trong lúc đợi lock. BE dùng principal hiện tại, không tin actor parameter của mock/client. TTL/inflight/failed-attempt retention chờ Q24; validation failure không lưu committed-success. Lỗi không rõ đã commit hay chưa thì retry cùng intent/key. Chuyển Plant lúc đang đợi không được commit vào tenant khác.

### 3.6 Tác động kho/giá trị/công nợ

| Event | OnHand | Active Reserved | Free available | HOLD/REJECTED | WIP/value | Chứng từ/công nợ |
|---|---|---|---|---|---|---|
| Save/edit/cancel Delivery draft | 0 delta | 0 delta | 0 delta | Không đổi | Không đổi | Claim quota tăng/thay/giảm; delivered chưa tăng |
| Post Delivery lượngq, consume own r≤q | −q | −r | −(q−r) trên eligible pool | Không lấy hoặc đổi quality quantity | Stock value giảm theo canonical cost nếu biết; unknown giữ null; không tiêu hao WIP lần2 | Delivered+q, actual ISSUE/source/audit |
| Save/cancel Invoice draft | Không đổi | Không đổi | Không đổi | Không đổi | Không đổi | Chỉ claim delivery quantity, chưa billed/receivable |
| Post Invoice amounta | Không đổi | Không đổi | Không đổi | Không đổi | Không đổi | Billed quantity tăng, receivable+a, paid0 |
| Save/cancel Payment draft | Không đổi | Không đổi | Không đổi | Không đổi | Không đổi | Chỉ claim amount, paid/outstanding chưa đổi |
| Post Payment allocationa | Không đổi | Không đổi | Không đổi | Không đổi | Không đổi | Paid+a, outstanding−a, allocations/history; không chuyển tiền thực |
| Price action | Không đổi | Không đổi | Không đổi | Không đổi | Không đổi | SO price/pricing revision/audit |

Không lấy giá bán100000 làm giá vốn. Nếu inventory cost đã biết, ISSUE value theo canonical ledger. Nếu FG cost unknown, physical ship vẫn được với valuationStatus UNKNOWN và null value; không gán0 để cân bằng. Costing GĐ11 đối soát sau phải giữ nguyên posting/source history, không đổi tiền sales invoice bằng giá vốn. Balances chính thức chỉ áp dụng invoice POSTED; amount trên DRAFT là preview, không receivable. SO tổng hợp trước khi có posted invoice là0 khi baseline known; legacy unknown không tự0.

### 3.7 Fixture và ví dụ wire — đề xuất, chưa chạy BE

**F1 là fixture BE cần dựng mới**, cô lập nguồn thành phẩm này để dễ đối soát; không phải toàn kho/browser data thật ở §2. Company C1/Plant P1 active, Customer CUS1 active, FG1 LOT/UOM PCS, kho W1 trong P1, SO1 confirmed10PCS giá100000VND, baseline known=true, shipped/billing/paid ban đầu0. WO1 thủ công không demand allocation; PR1 approved5 (accepted child20=3, child22=1, rejected child21=1), PR2 approved4 (accepted child23=4). Physical9, eligible8, rejected1, reserved0; không thêm opening8 vào F1. Cost củaFG chưa biết, null. Ngày chứng từ15/10/2026 và due30/10/2026; PlantToday của môi trường test phải cho phép ngày này hoặc BE/FE cùng dịch toàn bộ ngày fixture, lưu lại run đó.

Các UUID sau **do tài liệu đề xuất**, không phải IDs live hoặc chuyển số mock sang UUID. Các code DN/INV/PAY minh họa, BE có thể phát code khác và ghi mapping.

| Alias | UUID đề xuất | Ý nghĩa |
|---|---|---|
| C1 | 10000000-0000-4000-8000-000000000001 | Company |
| P1 | 10000000-0000-4000-8000-000000000002 | Plant |
| CUS1 | 10000000-0000-4000-8000-000000000003 | Customer |
| SO1 | 10000000-0000-4000-8000-000000000004 | SO codeSO-F1, version8 trước giao |
| SOL1 | 10000000-0000-4000-8000-000000000005 | Stable SOline10PCS giá100000 |
| FG1 | 10000000-0000-4000-8000-000000000006 | Item FG-001 |
| W1 | 10000000-0000-4000-8000-000000000007 | Warehouse |
| L20 | 10000000-0000-4000-8000-000000000020 | Accepted child3PCS, PR1 |
| L21 | 10000000-0000-4000-8000-000000000021 | Rejected child1PCS, PR1 |
| L22 | 10000000-0000-4000-8000-000000000022 | Accepted child1PCS, PR1 |
| L23 | 10000000-0000-4000-8000-000000000023 | Accepted child4PCS, PR2 |
| PR1 | 10000000-0000-4000-8000-000000000031 | Production receipt5 |
| PR2 | 10000000-0000-4000-8000-000000000032 | Production receipt4 |
| WO1 | 10000000-0000-4000-8000-000000000041 | Manual WO nguồn, không allocation SO1 |
| DN1/DN2 | 10000000-0000-4000-8000-000000000051 / 10000000-0000-4000-8000-000000000052 | Delivery3 và5 |
| DL1/DL2/DL3 | 10000000-0000-4000-8000-000000000061 / 10000000-0000-4000-8000-000000000062 / 10000000-0000-4000-8000-000000000063 | Lines3/1/4PCS |
| INV1 | 10000000-0000-4000-8000-000000000071 | Invoice800000 |
| PAY1/PAY2 | 10000000-0000-4000-8000-000000000091 / 10000000-0000-4000-8000-000000000092 | Payments300000/500000 |

Version minh họa: DN1/DN2 create1→post2; SO8→9 sauDN1→10 sauDN2; INV1 create1→post2→3 sauPAY1→4 sauPAY2; PAY1/PAY2 create1→post2. History trả actor/at thực trên server, không cố định ngày giờ audit theo tài liệu. PricingVersion ban đầu`price-f1-v1`; opaque snapshot values là ví dụ, FE lấy nguyên token BE trả.

| Mốc F1 | OnHand từ hai receipt | Eligible | Rejected | Delivered | InvoicedQty | InvoiceAmount | Paid | Outstanding |
|---|---|---|---|---|---|---|---|---|
| Sau QC, trước draft | 9 | 8 | 1 | 0 | 0 | 0 | 0 | 0 |
| DN1 nháp3 | 9 | 8 | 1 | 0 | 0 | 0 | 0 | 0 |
| DN1 post3, L20→0 | 6 | 5 | 1 | 3 | 0 | 0 | 0 | 0 |
| DN2 nháp1+4 | 6 | 5 | 1 | 3 | 0 | 0 | 0 | 0 |
| DN2 post5, L22/L23→0 | 1 | 0 | 1 | 8 | 0 | 0 | 0 | 0 |
| INV1 nháp từ DL1/DL2/DL3 | 1 | 0 | 1 | 8 | 0 | 0 | 0 | 0 |
| INV1 post | 1 | 0 | 1 | 8 | 8 | 800000 | 0 | 800000 |
| PAY1 nháp300000 | 1 | 0 | 1 | 8 | 8 | 800000 | 0 | 800000 |
| PAY1 post300000 | 1 | 0 | 1 | 8 | 8 | 800000 | 300000 | 500000 |
| PAY2 post500000 | 1 | 0 | 1 | 8 | 8 | 800000 | 800000 | 0 |

Cuối SO còn giao2, khôngDELIVERED đủ/khôngautoCLOSED; INV1 PAID không nghĩa SO1hoàn tất. Rejected1 không mất hoặc dùng giao, receiptapproved9 không giảm bởi ship, nguyên liệu/WIP không debit lần2. SO1 QCaccepted riêng0 vì manualWO không allocation, khác total nguồnQC8. FixtureF1 không có externalopeningstock; browser§2 cóopening8 ởkho khác, hai thông tin không mâu thuẫn.

#### A. Delivery candidates, save và post

**Ví dụ đề xuất:** GET `/api/sales-orders/v1/10000000-0000-4000-8000-000000000004/lines/10000000-0000-4000-8000-000000000005/delivery-sources?page=0&size=20`, auth/PlantP1. Response đề xuất đầy đủ ba nguồn accepted của F1; rejected L21 không nằm trong lựa chọn:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "content": [
      {
        "sourceKey": "candidate-f1-l20",
        "sourceSnapshotVersion": "delivery-source-f1-initial",
        "warehouseId": "10000000-0000-4000-8000-000000000007",
        "warehouseName": "W1",
        "itemId": "10000000-0000-4000-8000-000000000006",
        "uom": "PCS",
        "lotId": "10000000-0000-4000-8000-000000000020",
        "lotCode": "F1-QC-20",
        "productionReceiptId": "10000000-0000-4000-8000-000000000031",
        "workOrderId": "10000000-0000-4000-8000-000000000041",
        "freeQuantity": "3",
        "ownReservedQuantity": "0",
        "availableForLine": "3"
      },
      {
        "sourceKey": "candidate-f1-l22",
        "sourceSnapshotVersion": "delivery-source-f1-initial",
        "warehouseId": "10000000-0000-4000-8000-000000000007",
        "warehouseName": "W1",
        "itemId": "10000000-0000-4000-8000-000000000006",
        "uom": "PCS",
        "lotId": "10000000-0000-4000-8000-000000000022",
        "lotCode": "F1-QC-22",
        "productionReceiptId": "10000000-0000-4000-8000-000000000031",
        "workOrderId": "10000000-0000-4000-8000-000000000041",
        "freeQuantity": "1",
        "ownReservedQuantity": "0",
        "availableForLine": "1"
      },
      {
        "sourceKey": "candidate-f1-l23",
        "sourceSnapshotVersion": "delivery-source-f1-initial",
        "warehouseId": "10000000-0000-4000-8000-000000000007",
        "warehouseName": "W1",
        "itemId": "10000000-0000-4000-8000-000000000006",
        "uom": "PCS",
        "lotId": "10000000-0000-4000-8000-000000000023",
        "lotCode": "F1-QC-23",
        "productionReceiptId": "10000000-0000-4000-8000-000000000032",
        "workOrderId": "10000000-0000-4000-8000-000000000041",
        "freeQuantity": "4",
        "ownReservedQuantity": "0",
        "availableForLine": "4"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 3,
    "totalPages": 1
  }
}
```

SourceKey lấy nguyên từ selector, client không tự chế. Ba lựa chọn chia sẻ snapshot ban đầu; sau một lần post phải lấy nguồn/token mới. Lưu DN1:

```http
POST /api/v1/deliveries
Authorization: [REDACTED]
X-Plant-Id: 10000000-0000-4000-8000-000000000002
Idempotency-Key: 20000000-0000-4000-8000-000000000001
Content-Type: application/json
```

```json
{
  "salesOrderId": "10000000-0000-4000-8000-000000000004",
  "expectedOrderVersion": 8,
  "sourceSnapshotVersion": "delivery-source-f1-initial",
  "documentDate": "2026-10-15",
  "note": "F1 giao lan 1",
  "lines": [{
    "salesOrderLineId": "10000000-0000-4000-8000-000000000005",
    "sourceKey": "candidate-f1-l20",
    "quantity": "3"
  }]
}
```

Success201/`SUCCESS` đề xuất, không phải resultthật:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "documentId": "10000000-0000-4000-8000-000000000051",
    "type": "DELIVERY",
    "version": 1
  }
}
```

GET detailDN1 sau save trả canonical DRAFT1/linesDL1/claims/sourceSnapshotVersion`delivery-dn1-saved-v1`. POST `/api/v1/deliveries/10000000-0000-4000-8000-000000000051/post`, key`20000000-0000-4000-8000-000000000002` mới, body:

```json
{
  "expectedVersion": 1,
  "sourceSnapshotVersion": "delivery-dn1-saved-v1"
}
```

Result DN1version2; GETdetailPOSTED, DL1qty3/movementId/sourceL20/PR1/WO1, SOversion9, physical6/eligible5. DN2 tương tự: latestSOversion9, nguồnL22qty1+L23qty4; create1/post2→SOversion10 và tổngDelivered8. Không reuse create/postkey. GET/source tokens phải lấy mới sau mỗi mutation, các chuỗi sample không dùng hardcode.

#### B. Invoice preview, save và post

POST `/api/v1/sales-invoices/preview` auth/PlantP1, không giữ claim. Body:

```json
{
  "salesOrderId": "10000000-0000-4000-8000-000000000004",
  "expectedOrderVersion": 10,
  "lines": [
    {"deliveryLineId": "10000000-0000-4000-8000-000000000061", "quantity": "3"},
    {"deliveryLineId": "10000000-0000-4000-8000-000000000062", "quantity": "1"},
    {"deliveryLineId": "10000000-0000-4000-8000-000000000063", "quantity": "4"}
  ]
}
```

Response preview200 đề xuất, không tạo INV trước save:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "currency": "VND",
    "billingSnapshotVersion": "billing-f1-before-inv1",
    "pricingVersion": "price-f1-v1",
    "amount": "800000",
    "lines": [
      {"deliveryLineId": "10000000-0000-4000-8000-000000000061", "salesOrderLineId": "10000000-0000-4000-8000-000000000005", "quantity": "3", "unitPrice": "100000", "amount": "300000"},
      {"deliveryLineId": "10000000-0000-4000-8000-000000000062", "salesOrderLineId": "10000000-0000-4000-8000-000000000005", "quantity": "1", "unitPrice": "100000", "amount": "100000"},
      {"deliveryLineId": "10000000-0000-4000-8000-000000000063", "salesOrderLineId": "10000000-0000-4000-8000-000000000005", "quantity": "4", "unitPrice": "100000", "amount": "400000"}
    ]
  }
}
```

POST `/api/v1/sales-invoices`, key`20000000-0000-4000-8000-000000000003`:

```json
{
  "salesOrderId": "10000000-0000-4000-8000-000000000004",
  "expectedOrderVersion": 10,
  "billingSnapshotVersion": "billing-f1-before-inv1",
  "documentDate": "2026-10-15",
  "dueDate": "2026-10-30",
  "note": "F1 hoa don hai lan giao",
  "lines": [
    {"deliveryLineId": "10000000-0000-4000-8000-000000000061", "quantity": "3"},
    {"deliveryLineId": "10000000-0000-4000-8000-000000000062", "quantity": "1"},
    {"deliveryLineId": "10000000-0000-4000-8000-000000000063", "quantity": "4"}
  ]
}
```

Result201 INV1typeINVOICEversion1; GETdetail gives canonical price/amount/lines and sourceSnapshotVersion`invoice-inv1-saved-v1`. POST `/api/v1/sales-invoices/10000000-0000-4000-8000-000000000071/post`, key`20000000-0000-4000-8000-000000000004`:

```json
{
  "expectedVersion": 1,
  "sourceSnapshotVersion": "invoice-inv1-saved-v1"
}
```

ResultINV1version2; detailPOSTED/amount800000/paid0/outstanding800000, no additional stockmovement. Nếu anotherinvoice làm thay cumulativebase sau preview/save, response409PRICE_STALE (snapshot stale chung có thể409STALE_VERSION theo mappingQ22), giữ input rồi preview/resave mới; không đổi amount trong cùng clickconfirm.

#### C. Payment allocation và số dư sau post

GET invoice-candidates customerCUS1 → INV1version2/outstanding800000/otherdraftclaim0. POST `/api/v1/customer-payments`, key`20000000-0000-4000-8000-000000000005`:

```json
{
  "customerId": "10000000-0000-4000-8000-000000000003",
  "documentDate": "2026-10-15",
  "method": "TRANSFER",
  "reference": "F1-PAY-01",
  "note": "Ghi nhan thu lan 1, khong chuyen tien",
  "amount": "300000",
  "allocations": [{
    "invoiceId": "10000000-0000-4000-8000-000000000071",
    "expectedInvoiceVersion": 2,
    "amount": "300000"
  }]
}
```

Result201PAY1typePAYMENTversion1; GETdetail gives sourceSnapshotVersion`payment-pay1-saved-v1`. POST `/api/v1/customer-payments/10000000-0000-4000-8000-000000000091/post`, key`20000000-0000-4000-8000-000000000006`:

```json
{
  "expectedVersion": 1,
  "sourceSnapshotVersion": "payment-pay1-saved-v1"
}
```

PAY1 post version2, INV1version3/paid300000/outstanding500000. PAY2create500000 từ current INV version3, methodCASH/referenceF1-PAY-02, unique create/post keys ending007/008; postPAY2version2→INV1version4. Invoice GET detail monetary/related projection sau cùng, **ví dụ đề xuất có trường header/lines được định nghĩa ở §3.3**:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "documentId": "10000000-0000-4000-8000-000000000071",
    "code": "INV-F1",
    "type": "INVOICE",
    "status": "POSTED",
    "version": 4,
    "companyId": "10000000-0000-4000-8000-000000000001",
    "plantId": "10000000-0000-4000-8000-000000000002",
    "customerId": "10000000-0000-4000-8000-000000000003",
    "customerName": "Customer F1",
    "salesOrderId": "10000000-0000-4000-8000-000000000004",
    "orderNo": "SO-F1",
    "documentDate": "2026-10-15",
    "dueDate": "2026-10-30",
    "currency": "VND",
    "amount": "800000",
    "paidAmount": "800000",
    "outstandingAmount": "0",
    "paymentState": "PAID",
    "relatedPayments": [
      {"paymentId": "10000000-0000-4000-8000-000000000091", "code": "PAY-F1-01", "status": "POSTED", "documentDate": "2026-10-15", "allocatedAmount": "300000"},
      {"paymentId": "10000000-0000-4000-8000-000000000092", "code": "PAY-F1-02", "status": "POSTED", "documentDate": "2026-10-15", "allocatedAmount": "500000"}
    ]
  }
}
```

Đây là projection minh họa monetary/related, không full detail bắt bỏ createdAt/lines/capabilities. Full detail vẫn theo §3.3. Người không P read không nhận relatedPayments; invoice-authority vẫn được xem paid/outstanding của invoice. Một payment phân bổ nhiều invoice: related row.amount chỉ allocatedAmount cho invoice đang xem, không cả payment.

#### D. Edit/cancel/price, partial rounding và timeout

- PATCH dùng full replacement draft lines/allocations trong cùng immutableSO hoặccustomer, expectedVersion; ownclaim phải exclude. ID source đã POSTED stable; không sửa source SO/customer khi edit draft.
- POST `/api/v1/deliveries/{deliveryId}/cancel` (invoice/payment tương tự), expectedVersion1/reason`Hủy nhu cau lap nhap`, unique commandkey; resultversion2/CANCELLED, giữ audit/quantitysnapshots, releaseclaim một lần. Không DELETE posted/draft để giấu history.
- SO giá còn thiếu: POST `/api/sales-orders/v1/{salesOrderId}/commercial-prices`, expectedVersion hiện tại/prices[{salesOrderLineId,unitPrice:"0"}], Idempotency-Key mới. Đây là fixture khác F1price100000 known, không dùng action để ghi đè F1confirmed price. Response SALES_PRICE/version+pricingVersion; legacyprice được bổ sung không tự baselineknown.
- RoundingF2: newSOline1PCS giá1VND, delivered1, ba invoice theoquantity0.333333/0.333333/0.333334. Cumulativebilled .333333/.666666/1, cumulativeamount0/1/1, increments0/1/0; no negativeamount/no per-invoice fixed rounding drift. Invoice1/3NO_AMOUNT_DUE, invoice2outstanding1; only payment1VND có ý nghĩa. Reset/đối soát billingbase trước mỗi test, không dùng F1amount800000.
- Timeout sau commit: cùng body/key post lại trả originalcommandresult, không event/ISSUE/allocation thứhai; sau đó GETdetail để biết state hiện tại. Không dùng newidempotencykey chỉ vì timeout. Thay đổi body/confirmintent sau conflict thì newkey; nếu key conflict không autoretry vô hạn.

### 3.8 Nghiệm thu live và rollout

**Tất cả ca dưới CHƯA CHẠY LIVE.** F1 là fixture§3.7, F2 là rounding fixture; ca khác BE cung cấp thêm IDs. Mỗi ca dùng snapshot riêng/reset fixture có kiểm soát, không chạy tuần tự trên cùng F1 đã bị phá điều kiện. `E` = log cùngrun gồm requestmethod/fullpath/headersđãche/body + response nguyênvăn/HTTP/code/X-Trace-Id + environment/ngàygiờ/deployversion + IDs thực + before/after đúngrecord. Concurrency cần cả hai responses/trace/DB reconciliation; không gán mockIDs vào cột evidence live.

| ID | Đầu vào/record | Thao tác | Kết quả mong đợi kiểm được | Bằng chứng | Trạng thái |
|---|---|---|---|---|---|
| A01 | F1: physical9/eligible8 | Đọc SO/candidates | Baseline known, delivered0, ordered10; nguồn3/1/4, không có lô rejected | E chưa có | Chưa chạy live |
| A02 | F1 | Lưu DN1 nháp3 | Claim3, physical9/eligible8/delivered0; không giữ hàng vật lý | E chưa có | Chưa chạy live |
| A03 | DN1 nháp3 | Sửa 3→2→3 | Thay own claim; không trừ own claim hai lần | E chưa có | Chưa chạy live |
| A04 | Draft có claim | Hủy rồi retry cùng key | Trả quota một lần; giữ lý do/history, không ISSUE | E chưa có | Chưa chạy live |
| A05 | F1 DN1 nháp3 | Ghi sổ DN1 | Physical6/eligible5/delivered3; DL1/ISSUE3 một lần | E chưa có | Chưa chạy live |
| A06 | F1 sau DN1 | Ghi sổ DN2 gồm1+4 | Physical1 rejected/eligible0, delivered8/còn2; đúng ba lô nguồn | E chưa có | Chưa chạy live |
| A07 | Ordered10/delivered8 | Lập hoặc post thêm3 | DELIVERY_EXCEEDED, không ghi một phần | E chưa có | Chưa chạy live |
| A08 | Fixture HOLD/rejected/expired | Chọn hoặc ép payload nguồn | Không giao nguồn không hợp lệ, không mất quantity chất lượng | E chưa có | Chưa chạy live |
| A09 | Eligible8/other reserved3/own2 | Giao own/free | Tối đa5; consume own2, reservation người khác không đổi | E chưa có | Chưa chạy live |
| A10 | Một pool5, hai dòng3+3 | Lưu/ghi sổ | Lỗi tổng physical cap; rollback toàn stock/document/revision/audit | E chưa có | Chưa chạy live |
| A11 | Draft hợp lệ | Đổi quality/qty/customer nguồn trước post | Current guard chặn, giữ nháp/input, không ISSUE | E chưa có | Chưa chạy live |
| A12 | WO earmark cho hai SO | SO2 lấy stock SO1 | Chặn sai owner; đúng SO chỉ dùng entitlement của mình | E chưa có | Chưa chạy live |
| A13 | NON_TRACKED có reservation không rõ attribution | Giữ hoặc ép giao nguồn | Chặn an toàn, không fake lot hoặc lấy entitlement đơn khác | E chưa có | Chưa chạy live |
| A14 | F1 DL1/DL2/DL3 | Preview/lưu INV1 | Amount800000, claim8; stock không đổi, receivable chưa tăng | E chưa có | Chưa chạy live |
| A15 | Delivery5/other invoice draftclaim3 | Lập invoice3 | INVOICE_EXCEEDED, cap2; sửa own claim được loại đúng | E chưa có | Chưa chạy live |
| A16 | Invoice DRAFT | Hủy/retry | Trả unbilled quota một lần; chưa billed/receivable, không stock effect | E chưa có | Chưa chạy live |
| A17 | INV1 DRAFT | Ghi sổ | Invoiced8/amount800000/outstanding800000/paid0; stock không đổi | E chưa có | Chưa chạy live |
| A18 | Delivery DRAFT hoặc SO khác | Ép nguồn invoice | SOURCE_CHANGED/STATE, không tạo billing effect | E chưa có | Chưa chạy live |
| A19 | Thiếu giá SO | Preview/lưu invoice | PRICE_UNKNOWN, không tự0; delivery vẫn được nếu nguồn hợp lệ | E chưa có | Chưa chạy live |
| A20 | Confirmed price missing hoặc known0 | Bổ sung0/thử ghi đè | Missing→0 có audit; giá known0 bị khóa | E chưa có | Chưa chạy live |
| A21 | F2 rounding | Lập ba invoice từng phần | Amount tăng0/1/0, tổng1; zero invoice NO_AMOUNT_DUE | E chưa có | Chưa chạy live |
| A22 | Hai preview cùng billing base | Post1 rồi post2 | PRICE_STALE, giữ input; invoice2 phải preview/resave/reconfirm | E chưa có | Chưa chạy live |
| A23 | Draft invoice amount bị thay trái phép | Ghi sổ | Server tính lại/chặn; không receivable hoặc partial commit | E chưa có | Chưa chạy live |
| A24 | INV1 POSTED | Lưu PAY1 300000 | Draft claim300000; paid0/outstanding800000 | E chưa có | Chưa chạy live |
| A25 | PAY1 DRAFT | Ghi sổ | Paid300000/outstanding500000; tăng invoice version/event | E chưa có | Chưa chạy live |
| A26 | INV1 sau PAY1 | Ghi sổ PAY2 500000 | Paid800000/outstanding0; SO còn giao2, chưa closed | E chưa có | Chưa chạy live |
| A27 | Outstanding500000 | Allocate500001 | PAYMENT_EXCEEDED, balance không đổi | E chưa có | Chưa chạy live |
| A28 | Amount300000/allocations200000 | Lưu/ghi sổ | ALLOCATION_MISMATCH; giữ form, không ghi sổ sai | E chưa có | Chưa chạy live |
| A29 | Hai invoice cùng khách/Plant | Một payment phân bổ100+200 | Total300; balance từng invoice đúng, không đổi stock | E chưa có | Chưa chạy live |
| A30 | Invoice khác khách/Plant hoặc không hợp lệ | Ép allocation | Denied/source conflict; không lộ record hoặc phát sinh effect | E chưa có | Chưa chạy live |
| A31 | Draft payment claim300000 trên invoice800000 | Đồng thời thêm draft600000 | Conflict, claims không vượt800000; edit own được loại | E chưa có | Chưa chạy live |
| A32 | Payment DRAFT | Hủy/retry | Claim trả một lần; paid không đổi, có audit/lý do | E chưa có | Chưa chạy live |
| A33 | Reference payment đã có | Tạo payment hợp lệ khác | Chỉ cảnh báo trùng; cap/authorization vẫn kiểm chặt | E chưa có | Chưa chạy live |
| A34 | Cùng post key/payload | Retry sau mất response | Cùng identity/result, chỉ một effect và một event | E chưa có | Chưa chạy live |
| A35 | Cùng key nhưng khác body/resource | Gửi command | IDEMPOTENCY_CONFLICT, không thêm effect | E chưa có | Chưa chạy live |
| A36 | Hai người tranh quota/stock | Post đồng thời | Serialize và recheck; không overdelivery/debit | E chưa có | Chưa chạy live |
| A37 | Hai người tranh outstanding | Post payments đồng thời | Tổng allocation không vượt amount; conflict khi không đủ | E chưa có | Chưa chạy live |
| A38 | Inject failure sau staging stock/allocation | Ghi sổ | Rollback cả data/audit/replay; retry đúng intent an toàn | E chưa có | Chưa chạy live |
| A39 | Profiles read-only/noPOST/noCost | Direct action/read source/detail | Chặn action đúng authority; không lộ giá bán/giá vốn | E chưa có | Chưa chạy live |
| A40 | Plant2 hoặc Company khác | List/direct ID/source/history/replay | 404/403 theo policy, không có dữ liệu Plant1 | E chưa có | Chưa chạy live |
| A41 | Assignment revoked/user locked/session expired khi đợi lock | Mutation rồi same-key replay | Kiểm current auth trước commit/replay và từ chối | E chưa có | Chưa chạy live |
| A42 | Future/date invalid/scale7/overflow/empty hoặc101lines | Lưu/ghi sổ | Mã và field error cụ thể; không có side effect | E chưa có | Chưa chạy live |
| A43 | Legacy fulfilled không có shipping ledger | Migration/read/force post | Giữ IDs/history; shipped/billing unknown, không tự0; chặn unsafe | E chưa có | Chưa chạy live |
| A44 | Ordered10/coverage10, ship3 đúng allocation WO | Tính current MRP | Remaining coverage7 + delivered3 → open0; saved run giữ nguyên | E chưa có | Chưa chạy live |
| A45 | Downstream draft/posted đang có hiệu lực | Cancel SO hoặc thay source line | Chặn phá source; cancelled draft có thể giải phóng guard khi không còn dependency | E chưa có | Chưa chạy live |
| A46 | FG cost unknown/giá bán100000 | Post delivery/read hạn chế tiền | Ship physical được; costnull, không100000/không leak | E chưa có | Chưa chạy live |
| A47 | Posted hoặc cancelled docs | Edit/delete/cancel/post key mới | State/immutable error; không xóa history | E chưa có | Chưa chạy live |
| A48 | Hơn20 records và source/related nhiều trang | Filter/page/reload | Scoped totals, stable IDs, không buộc N+1; balances/history giữ nguyên | E chưa có | Chưa chạy live |

**Rollout đề xuất:** (1) BE chốt Q/mapping/errors/quyền, stable IDs/customer/baseline; (2) migration legacy/lineage/cost unknown và guards cho endpoint cũ; (3) SO price/summary, delivery quota/source/atomic stock; (4) invoice preview/claims/posting; (5) payment allocations/claims/posting; (6) list/history/minimal source/related/MRP projections theo scope; (7) OpenAPI, deploy version, test roles và actual fixture IDs; (8) FE đối chiếu adapter rồi chạy A01–A48 live. Các gate GĐ6/GĐ8/GĐ9 độc lập, không trì hoãn guard nguồn kho vì API cũ đã dùng được một phần.

Điều kiện mock → API: contract/permission mapping đã chốt, snapshot có hiệu lực, BE deploy đúng bản, FE có typed adapter/UUID mapping/query/error handling, nghiệm thu đúng scope và IDs. Không bật API rồi fallback mock; không chuyển UUID thành number. Rollout từng phần phải có capability unavailable rõ; bảo vệ source posted qua cả API cũ. Trạng thái: **Chuẩn bị bàn giao / Chờ BE mapping và live acceptance**, chưa hoàn tất tích hợp. Change log v1: bản đầu15/10/2026 dựa Session317 FE và audit source Session318, chưa có xác nhận BE hoặc bằng chứng live.

## §4 — Hợp đồng lỗi mong đợi

**[Đề xuất BE]** FE mock có codes ngắn, bảng dưới là mapping cần thống nhất trước adapter. Không tuyên bố BE đã trả các codes này. Cùng HTTP409 có nhiều nghĩa, FE rẽ nhánh theo code. Field error dùng path cụ thể như `lines[0].quantity`, giữ form. Response/log không lộ token, tiền hoặc record ngoài scope; giữ X-Trace-Id để support.

| Tình huống | HTTP | code đề xuất (mock tương ứng) | FE message/field | Input giữ | Retry |
|---|---|---|---|---|---|
| Body/ID/lines/text/method không hợp lệ | 400 | VALIDATION_ERROR (LINES/TEXT/METHOD) | Field error đúng path và giới hạn | Có | Sửa input, intent mới dùng key mới |
| Quantity/price quá scale/range | 400 | QUANTITY_INVALID / PRICE_INVALID (QUANTITY/PRICE) | Tối đa6 số lẻ và giới hạn đã chốt | Có | Không auto retry |
| Amount có phần lẻ/không dương/overflow | 400 | MONEY_INVALID (MONEY) | Nguyên VND, payment phải dương | Có | Không auto retry |
| Ngày sai/future/trước source/due không hợp lệ | 400 | DATE_INVALID (DATE) | Field date tương ứng | Có | Sửa input |
| Lý do hủy thiếu hoặc sai độ dài | 400 | REASON_REQUIRED (REASON) | Lý do5..2000 ký tự sau trim | Có | Sửa input |
| Chưa đăng nhập/session expired | 401 | Theo auth contract hiện có | Reauth theo code, không gọi mọi401 là attack | Có khi an toàn | Auth flow đã chốt |
| Thiếu quyền/user locked/assignment expired | 403 | PERMISSION_DENIED | Không có quyền thao tác | Có; khóa action | Không auto retry |
| Plant header/path/body/context không khớp | 403 | PLANT_SCOPE_MISMATCH | Chọn Plant phù hợp | Có | Refetch context |
| ID không có hoặc ngoài scope | 404 | NOT_FOUND | Không tìm thấy trong phạm vi | Không lộ record | Không auto retry |
| Sai state/posted immutable | 409 | INVALID_STATE (STATE) | Đã ghi sổ/đã hủy, không thực hiện được | Có | Refetch |
| Không phải creator khi sửa/hủy | 403 | PERMISSION_DENIED | Không phải nháp của bạn | Có | Không auto retry |
| Document/order/source/balance stale | 409 | STALE_VERSION (STALE) | Nguồn đã đổi, xem lại trước confirm | Có | Refresh/resave/reconfirm |
| Customer/ref/lineage/source đã đổi | 409 | SOURCE_CHANGED / REFERENCE_INACTIVE (SOURCE_CHANGED/REFERENCE/CUSTOMER) | Nguồn không còn hợp lệ | Có | Chọn nguồn hoặc BE đối soát |
| Legacy baseline/entitlement chưa biết | 409 | SOURCE_UNKNOWN | Chưa đủ bằng chứng nguồn/số dư | Có | Đối soát, không tự0 |
| Vượt delivery quota | 409 | DELIVERY_EXCEEDED | Vượt lượng còn giao hoặc quota đang có draft khác | Có | Refetch claim |
| Không đủ physical/eligible/own/free/entitlement | 409 | INSUFFICIENT_STOCK (INSUFFICIENT) | Không đủ nguồn hợp lệ | Có | Refetch source |
| Vượt invoice quota | 409 | INVOICE_EXCEEDED | Dòng giao đã lập hoặc đang lập hóa đơn | Có | Refetch |
| Thiếu SO price | 409 | PRICE_UNKNOWN | Cần bổ sung giá bán nguồn | Có | Người có quyền bổ sung giá |
| Sửa known confirmed price | 409 | PRICE_LOCKED | Giá đã chốt không sửa | Có | Không auto retry |
| Pricing/cumulative billing base đổi | 409 | PRICE_STALE | Preview tiền đổi, xem và xác nhận lại | Có | Preview/resave/confirm mới |
| Overpayment/claim conflict | 409 | PAYMENT_EXCEEDED | Vượt số tiền còn phân bổ | Có | Refetch balances |
| Header khác tổng lines/allocations | 400/409 | ALLOCATION_MISMATCH (ALLOCATION) | Tổng không khớp;400 input/409 nguồn stored draft không nhất quán | Có | Sửa input/refetch |
| Key dùng cho intent khác | 409 | IDEMPOTENCY_CONFLICT | Key đã dùng cho thao tác khác | Có | Không retry mù |
| Command cùng key đang xử lý | 409 | REQUEST_IN_PROGRESS | Đang xử lý, giữ đúng intent/key | Có | Retry-After, cùng key |
| Server/transaction unavailable | 500/503 | INTERNAL_ERROR / SERVICE_UNAVAILABLE | Chưa xác định kết quả, lưu TraceId | Có | Cùng key nếu chưa rõ commit |
| Rate limit | 429 | RATE_LIMITED | Chờ Retry-After | Có | Cùng key, retry có giới hạn |

HTTP400 cho input sum mismatch, HTTP409 cho stored/source conflict; BE cần chốt mapping, FE ưu tiên code và field error. Không yêu cầu thay mã auth đã chốt GĐ1. Current session/assignment do BE kiểm; GĐ10 không phát sinh challenge/OTP/email.

**Ví dụ đề xuất response lỗi**, chưa có TraceId thật:

```json
{
  "code": "PAYMENT_EXCEEDED",
  "message": "Allocation exceeds the current invoice balance.",
  "errors": [{"field": "allocations[0].amount", "message": "Maximum allocatable amount is 500000 VND."}]
}
```

```json
{
  "code": "PRICE_STALE",
  "message": "Billing basis changed. Refresh the preview and confirm again."
}
```

Không đưa X-Trace-Id giả vàoJSON làm evidence; BE trả headertrace thật trên mọi response. Unauthorized error không được trả maximumamount chứa dữliệutrước khi  checkscope/permission.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Phần triển khai ở §3. Tất cả câu hỏi KHÔNG CHẶN FE code tiếp trên mock; “CHẶN API” nghĩa cần quyết định trước adapter/live tương ứng. Owner mặc định BE lead, PO tham gia khi cần chốt policy; chưa chỉ định cá nhân. Quyết định/ngày của tất cả câu: **chưa chốt**. Trả lời Q# có/không hoặc chọn phương án, kèm mapping nếu tên/path khác.

1. **Q1:** BE dùng ba root API mới ở §3.4? — FE nghiêng về: Có nếu chưa có module tương đương; nếu đã có thì trả mapping để tránh xây trùng. — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ10.
2. **Q2:** SO extension giữ root `/api/sales-orders/v1` hiện có? — FE nghiêng về: Có, bảo toàn adapter đang dùng. — KHÔNG CHẶN FE code tiếp; CHẶN API SO.
3. **Q3:** Document và source-line ID dùng UUID string ổn định? — FE nghiêng về: Có, không liên kết bằng code hoặc vị trí dòng. — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ10.
4. **Q4:** SO mới phải có customerId master khi xác nhận? — FE nghiêng về: Có, tên khách legacy không đủ làm khóa nguồn thương mại. — KHÔNG CHẶN FE code tiếp; CHẶN API source.
5. **Q5:** Legacy thiếu delivery/billing ledger giữ baseline unknown và chặn thao tác không an toàn? — FE nghiêng về: Có, không suy shipped từ fulfilled cũ. — KHÔNG CHẶN FE code tiếp; CHẶN API migration.
6. **Q6:** SO confirmed chỉ được bổ sung đơn giá còn thiếu? — FE nghiêng về: Có, đơn giá đã biết kể cả 0 đều khóa. — KHÔNG CHẶN FE code tiếp; CHẶN API pricing.
7. **Q7:** VND amount làm tròn HALF_UP cộng dồn theo SO-line như §3.5? — FE nghiêng về: Có, bảo toàn tổng khi lập nhiều hóa đơn từng phần. — KHÔNG CHẶN FE code tiếp; CHẶN API invoice.
8. **Q8:** Hóa đơn 0 dùng trạng thái NO_AMOUNT_DUE? — FE nghiêng về: Có, không tạo thanh toán giả 0 đồng. — KHÔNG CHẶN FE code tiếp; CHẶN API invoice.
9. **Q9:** Delivery draft chỉ giữ quota SO, chưa giữ tồn vật lý? — FE nghiêng về: Có, physical reservation là thao tác riêng. — KHÔNG CHẶN FE code tiếp; CHẶN API delivery.
10. **Q10:** Draft claim giữ đến sửa/hủy/ghi sổ, chưa có TTL? — FE nghiêng về: Có trong GĐ10; nếu tự hết hạn cần có quy trình và sự kiện rõ. — KHÔNG CHẶN FE code tiếp; CHẶN API claims.
11. **Q11:** Một delivery chỉ thuộc một SO, dù có nhiều kho/lô trong Plant? — FE nghiêng về: Có, để nguồn và quota nhất quán. — KHÔNG CHẶN FE code tiếp; CHẶN API delivery.
12. **Q12:** SourceKey do BE phát hành và kiểm scope? — FE nghiêng về: Có, client không tự ghép ID để suy quyền dùng nguồn. — KHÔNG CHẶN FE code tiếp; CHẶN API source.
13. **Q13:** NON_TRACKED pool thiếu attribution bị chặn tới khi đối soát? — FE nghiêng về: Có, bảo vệ hàng đang earmark cho đơn khác. — KHÔNG CHẶN FE code tiếp; CHẶN API nontracked.
14. **Q14:** Delivery tiêu thụ own reservation trước free stock? — FE nghiêng về: Có, giảm held đúng owner và tránh đếm hai lần. — KHÔNG CHẶN FE code tiếp; CHẶN API delivery.
15. **Q15:** Một invoice chỉ thuộc một SO nhưng có thể gộp nhiều delivery POSTED? — FE nghiêng về: Có, giá và nguồn thuộc cùng hợp đồng SO. — KHÔNG CHẶN FE code tiếp; CHẶN API invoice.
16. **Q16:** Một payment được phân bổ nhiều invoice POSTED cùng Customer/Plant/VND? — FE nghiêng về: Có, amount bằng tổng allocations. — KHÔNG CHẶN FE code tiếp; CHẶN API payment.
17. **Q17:** Payment reference trùng chỉ cảnh báo? — FE nghiêng về: Có, chưa có quy tắc nghiệp vụ bắt duy nhất. — KHÔNG CHẶN FE code tiếp; KHÔNG CHẶN API core, chốt trước adapter phần tương ứng.
18. **Q18:** Giới hạn số lượng/giá/amount/dòng/text ở §3.2 được chấp nhận? — FE nghiêng về: Có; không cắt hoặc làm tròn input âm thầm. — KHÔNG CHẶN FE code tiếp; CHẶN API validation.
19. **Q19:** Expiry dạng date-only được quy đổi sang cuối ngày Plant khi tính eligibility? — FE nghiêng về: Có cho contract mới; mock hiện so expiresAt với thời điểm hiện tại, chưa chứng minh phép quy đổi này. — KHÔNG CHẶN FE code tiếp; CHẶN API stock expiry.
20. **Q20:** Plant thiếu timezone thì API chặn cấu hình? — FE nghiêng về: Có; mock đang có fallback Asia/Ho_Chi_Minh, FE sẽ đối chiếu theo quyết định. — KHÔNG CHẶN FE code tiếp; CHẶN API dates.
21. **Q21:** BE cấp opaque sourceSnapshotVersion bao quát claims và balance liên quan? — FE nghiêng về: Có, FE không tự ghép revision để xác nhận. — KHÔNG CHẶN FE code tiếp; CHẶN API concurrency.
22. **Q22:** Cumulative billing base đổi trả PRICE_STALE và yêu cầu xem/xác nhận lại? — FE nghiêng về: Có, không đổi số tiền trong cùng lần xác nhận cũ. — KHÔNG CHẶN FE code tiếp; CHẶN API invoice.
23. **Q23:** Idempotency scope là Company+Plant+principal+key với fingerprint command/resource/body? — FE nghiêng về: Có, kiểm current auth trước replay. — KHÔNG CHẶN FE code tiếp; CHẶN API commands.
24. **Q24:** BE giữ successful idempotency tối thiểu 24 giờ? — FE nghiêng về: Có; BE trả rõ TTL, inflight và failed-attempt policy để FE retry đúng. — KHÔNG CHẶN FE code tiếp; CHẶN API retry.
25. **Q25:** Mutation trả ID/type/version rồi FE refetch detail có quyền? — FE nghiêng về: Có, hạn chế replay lộ dữ liệu tiền đã cache. — KHÔNG CHẶN FE code tiếp; CHẶN API commands.
26. **Q26:** READ hoặc MANAGE hoặc POST cho phép đọc module nhưng không tự cấp MANAGE? — FE nghiêng về: Có, đúng phân tách authority của FE. — KHÔNG CHẶN FE code tiếp; CHẶN API permission.
27. **Q27:** Chỉ creator được sửa/hủy nháp? — FE nghiêng về: Có theo FE hiện tại; quyền override quản trị cần chính sách riêng. — KHÔNG CHẶN FE code tiếp; CHẶN API lifecycle.
28. **Q28:** Người có POST authority được ghi sổ phiếu chính mình tạo? — FE nghiêng về: Có, PO đã duyệt FE không có phê duyệt hai cấp. — KHÔNG CHẶN FE code tiếp; CHẶN API posting policy.
29. **Q29:** Invoice-reader được đọc aggregate paid/outstanding, còn full payment cần payment authority? — FE nghiêng về: Có, chỉ cung cấp projection đúng mục đích. — KHÔNG CHẶN FE code tiếp; CHẶN API money.
30. **Q30:** Payment-only chỉ nhận minimal invoice balance, không có SO unit price/full invoice lines? — FE nghiêng về: Có, bảo vệ dữ liệu nguồn thương mại. — KHÔNG CHẶN FE code tiếp; CHẶN API projection.
31. **Q31:** Unknown FG cost vẫn cho physical delivery với valuation UNKNOWN/null? — FE nghiêng về: Có, không dùng giá bán làm giá vốn trong lúc chờ GĐ11. — KHÔNG CHẶN FE code tiếp; CHẶN API valuation.
32. **Q32:** Current MRP giảm coverage theo delivered từ đúng allocation WO nguồn? — FE nghiêng về: Có, tránh bù lại phần held đã ship và tránh overlap netting. — KHÔNG CHẶN FE code tiếp; CHẶN API MRP.
33. **Q33:** Posted GĐ10 bất biến, correction dùng workflow riêng ở giai đoạn sau? — FE nghiêng về: Có, không DELETE hoặc ghi đè bằng lượng âm. — KHÔNG CHẶN FE code tiếp; CHẶN API lifecycle.
34. **Q34:** List/filter/history/source dùng server pagination size20, max100 và scope trước count? — FE nghiêng về: Có, tổng dòng không lộ Plant khác. — KHÔNG CHẶN FE code tiếp; CHẶN API read.
35. **Q35:** Direct ID ngoài scope trả 404 NOT_FOUND? — FE nghiêng về: Có, lỗi header/context mismatch trả 403 riêng. — KHÔNG CHẶN FE code tiếp; CHẶN API error policy.
36. **Q36:** Các mã lỗi/envelope/HTTP ở §4 được giữ hoặc BE cung cấp mapping 1-1? — FE nghiêng về: Có mapping nếu BE đã có chuẩn; FE không phân nhánh chỉ theo HTTP. — KHÔNG CHẶN FE code tiếp; CHẶN API adapter.
37. **Q37:** SO cancel bị chặn khi còn downstream draft/posted chưa cancel? — FE nghiêng về: Có, bảo vệ nguồn qua cả API cũ. — KHÔNG CHẶN FE code tiếp; CHẶN API SO.
38. **Q38:** `/deliveries/related` nhận một loại source filter mỗi lần? — FE nghiêng về: Có, tránh ngữ nghĩa AND/OR mơ hồ. — KHÔNG CHẶN FE code tiếp; KHÔNG CHẶN API core, chốt trước adapter phần tương ứng.
39. **Q39:** Delivery POSTED chỉ nghĩa warehouse dispatched? — FE nghiêng về: Có, vận chuyển và POD nằm ngoài GĐ10. — KHÔNG CHẶN FE code tiếp; CHẶN API business meaning.
40. **Q40:** Delivered đủ hoặc paid không tự chuyển SO thành CLOSED? — FE nghiêng về: Có, manufacturing close GĐ11 độc lập với thanh toán. — KHÔNG CHẶN FE code tiếp; CHẶN API progress.

Q7/GĐ10 là rounding invoice, khác Q7/GĐ8 output warehouse; Q37/GĐ10 là SO cancel, khác Q37/GĐ8 fractional return value. Khi trả lời ghi kèm giai đoạn để không đóng nhầm task. Các quyết định GĐ6 metrics/warehouse/rounding/coverage, GĐ8 output lock/residual cost và GĐ9 QC/source vẫn độc lập.

## §6 — FE đã tự xử lý

- **[Đã kiểm chứng FE]** Ledger persistent và transaction chung inventory/SO; exact decimal, validation nguồn hiện tại, current authorization, replay và rollback. Đây là phòng vệ demo, không thay DB isolation khi nhiều người dùng trên server.
- Draft quota loại own claim khi sửa; kiểm physical eligibility/owner/earmark; posted bất biến; provenance và related documents từ nguồn thực. Legacy có known flag, giá vốn thiếu giữ unknown. Missing price khác 0; preview cộng dồn, conflict giữ input rồi yêu cầu xem/xác nhận lại.
- UI xác nhận trước mutation, busy lock và chống double submit. Lỗi giữ form/source/quantity/amount; retry cùng intent dùng cùng key, đổi intent dùng key mới. Thành công refetch/invalidate inventory/SO/planning/chứng từ nguồn; URL chuyển sang ID để reload không mở lại form create. Context Plant/user đổi thì remount và clear form, query key theo Company/Plant/user; service kiểm scope lại.
- Giá bán/thanh toán và inventory cost có projections riêng: Delivery-only không money, Payment-only không SO price, related links theo quyền. Demo chỉ migrate quyền Admin/Manager đã biết; không tự chốt production role policy. Tên nghiệp vụ trên UI không hiển thị mã PERM.
- API GĐ10 hiện explicit unavailable, chưa adapter hoặc fallback. Legacy thiếu evidence không được suy thành 0; NON_TRACKED thiếu attribution bị chặn, không fake lot hoặc genealogy từng đơn vị FG.

**Khác biệt source cần BE biết:** eligibility hiện kiểm `expiresAt > Date.now()`, chưa chứng minh date-only expiry được chuyển tới cuối ngày Plant (Q19); timezone mock có fallback (Q20). Mock tạo source-version từ revision/JSON nội bộ, API đề xuất dùng opaque token có hiệu lực được server kiểm. Mock giữ claim theo trạng thái, chưa TTL. Decimal serialization/nullability và IDs mock không phải wire BE. Không coi những đề xuất này là đã triển khai hoặc lỗi của BE.

**Cần rà khi BE trả contract:** UUID/stable line ID, SO customer link/schema edit; sourceKey/snapshot/claim/billing-balance version; precision/ceiling; expiry/timezone; field omission so với null unknown; owner/post permissions; safe replay; mã lỗi/headers; pagination candidate/history/related; guard qua API cũ; FG cost reconcile GĐ11; source-aware MRP và coverage/held overlap. Không tắt phòng vệ chỉ vì BE trả 200, không coi ẩn nút là security. Phiên tài liệu không sửa application policy.

## §7 — Tham chiếu

Chỉ tên file để PO/BE đối chiếu nếu cần; toàn bộ yêu cầu triển khai đã có trong tài liệu này. Các file nội bộ không được mặc nhiên coi là BE đã nhận; BE không cần mở repo hoặc môi trường local.

- FE_HANDOFF_GD_06_2026-10-14.md — nền SO/planning/coverage; các quyết định còn mở giữ riêng, đối chiếu đúng bản BE đã nhận.
- FE_HANDOFF_GD_08_2026-10-14.md — nguồn WO/consumption/return/WIP; output warehouse Q7 và fractional residual Q37 còn mở.
- FE_HANDOFF_GD_09_2026-10-15.md — production receipt/partial QC/eligible source và legacy cost unknown.
- FE_HANDOFF_GD_10_MESSAGE_2026-10-15.md — tin nhắn 4 dòng đi kèm bản v1 này.
- CAPSTONE3_GD10_FE_PROPOSAL_2026-10-15.md — phương án nội bộ PO đã duyệt, không phải contract BE đã chốt.
- CAPSTONE3_GD10_FE_REVIEW_2026-10-15.md — báo cáo nội bộ Session317; số liệu và giới hạn cần BE đã tóm tắt tại §2.
- FETaskDeliveryRules.md — rules PO áp dụng: §0–§7, self-contained, fact/proposal, version và evidence.

---

**Checklist trước giao v1:** đủ §0–§7, P1, ngoài phạm vi và 19 phần giao diện; 30 yêu cầu triển khai, 40 câu hỏi đóng, 48 ca live chưa chạy; kiểu/nullability/công thức/fixture/quyền/lỗi/retry/rollout riêng biệt. Không có live evidence, không báo defect hoặc tự tạo TraceId/commit/version/record server. JSON copy được, không secret hoặc phụ thuộc repo/local path; lưu tin nhắn 4 dòng riêng. PO tự gửi; chỉ đóng task sau FE nghiệm thu đúng request/IDs/deploy. Nếu chưa đạt, tiếp tục cùng task; bản sửa sau gửi dùng _v2.

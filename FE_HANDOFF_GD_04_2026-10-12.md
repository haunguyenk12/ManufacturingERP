# Giai đoạn 4 — Bàn giao Backend: Tồn kho, lô và chuyển kho

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock đã hoàn thành quản lý tồn kho/lô/chuyển kho; cần BE đối chiếu API hiện có và cung cấp contract cho chuyển kho atomic, kiểm đếm tuyệt đối, snapshot giá trị và truy xuất nguồn.
- Ảnh hưởng: nhân viên kho/quản lý dùng Kho, Lô tồn kho, Biến động kho và Chuyển kho; demo/mock dùng được, chuyển kho mới chưa dùng được ở chế độ API.
- Mức ưu tiên: P1 — luồng chuyển kho bị chặn ở API mode; các luồng hiện có và lộ trình FE/mock vẫn tiếp tục được.

**Bản:** v1 · **Ngày dự kiến gửi:** 12/10/2026 · **FE:** Session 298–299 · **Bàn giao:** Session 300.
**PO:** người phụ trách sản phẩm trong phiên làm việc yêu cầu “tạo tài liệu cho backend giai đoạn này” sau khi duyệt sửa UX; ghi nhận chuyển FE/mock giai đoạn 4 sang bàn giao. Không đồng nghĩa nghiệm thu API/server.
**FE owner / BE owner cá nhân:** chưa chỉ định. **Trạng thái:** đã chuẩn bị để PO gửi; chưa gửi bên ngoài, chờ contract/live acceptance. **Change log v1:** bàn giao phạm vi FE Session 298 và UX panel Session 299; chưa có bản GĐ4 đã gửi trước đó trong phiên này.
Ngày tên file là ngày dự kiến gửi; nếu gửi ngày khác thì đổi tên trước lần gửi đầu. Sửa sau khi đã gửi phải dùng `_v2`, `_v3`.

Nhãn thông tin: **[FE đã kiểm chứng]** = source/test/demo; **[Đề xuất BE]** = path/DTO/policy chưa chốt; **[Giả định]** = điều kiện cần BE xác minh; **[Chưa kiểm chứng live]** = chưa có bằng chứng server. Mọi JSON/UUID bên dưới là ví dụ đề xuất, không phải request/response đã chạy.

## §1 — Phạm vi

### 1.1 Màn hình mới và đã sửa

| Loại | Màn hình | Nội dung nghiệp vụ |
|---|---|---|
| Mới | Chuyển kho | Danh sách/tìm kiếm/lọc, tạo phiếu 1–100 dòng, hai kho khác nhau trong cùng nhà máy, chọn vật tư/lô/lượng và lý do |
| Mới | Chi tiết phiếu chuyển | Phiếu đã ghi nhận chỉ xem: mã, kho nguồn/đích, người/thời gian/lý do, dòng vật tư/lô/lượng và biến động hai phía |
| Sửa | Kho | Một dòng vật tư + kho; tồn thực tế/khả dụng/giữ chỗ/đang kiểm tra/không đạt/hết hạn, giá bình quân và giá trị tồn |
| Sửa | Chi tiết tồn kho | Panel bên phải: Thông tin, Lô, Giữ cho sản xuất, Lịch sử; lượng giữ còn lại và link tới lệnh sản xuất |
| Sửa | Nhập kho | Hộp thoại giữa màn hình; lượng, đơn giá nhập bắt buộc, mã lô mới/hạn dùng nếu theo lô; xác nhận trước ghi nhận |
| Sửa | Xuất kho | Chọn kho/lô, giới hạn khả dụng tự do; không xuất phần giữ chỗ hoặc lô không đủ điều kiện |
| Sửa | Điều chỉnh tồn | Nhập lượng kiểm đếm tuyệt đối, xem chênh lệch, lý do; không giảm dưới giữ chỗ; giá tăng theo cơ sở định giá |
| Sửa | Lô tồn kho và chi tiết lô | Danh sách theo lô + kho, lọc chất lượng/hạn dùng; panel đúng lô được chọn, thông tin/nguồn/ngày/lượng và lịch sử tại kho đó; đổi trạng thái có xác nhận |
| Sửa | Biến động kho và chi tiết | Lọc loại, xem nguồn/người/thời gian/lý do, lượng trước/sau và snapshot đơn giá/giá trị |
| Sửa | Sidebar | Thêm Chuyển kho theo quyền đọc tồn kho |

**[FE đã kiểm chứng]** Routes FE: `/manufacturing/stock`, `/manufacturing/inventory-lots`, `/manufacturing/stock-movements`, `/manufacturing/stock-transfers`. Phiếu chuyển và chi tiết cùng route cuối. Panel stock/lot rộng tối đa 720px, tiêu đề/đóng cố định, cuộn riêng; đóng giữ bộ lọc/trang bảng. Form ghi nhận ở giữa, có confirmation riêng; lịch sử biến động mở chi tiết hộp thoại. UI VI/EN dùng tên nghiệp vụ, không trình bày mã quyền cho người dùng.

### 1.2 Trong/ngoài phạm vi và môi trường

**Trong phạm vi:** tồn theo Item + Warehouse + Lot khi cần; lượng/giá trị; nhận/xuất thường/kiểm đếm; trạng thái lô; chuyển kho cùng Plant; lịch sử và liên kết nguồn; reservation và các điểm nối sản xuất hiện có; atomicity/idempotency/quyền/scope.

**Ngoài phạm vi:** chuyển khác Plant, in-transit, chuyển reservation, sửa/hủy/đảo phiếu đã post; Serial Tracking; FIFO costing; FX/VAT/chi phí mua hàng; sổ kế toán; tính đủ giá thành Material/Labor/Overhead; workflow mua hàng/GRN/giao hàng; import tồn đầu kỳ; màn hình WIP/Costing mới; sửa Auth/email/RBAC giai đoạn 1. Các luồng sản xuất cũ vẫn hoạt động, không thiết kế lại approval của chúng trong task này.

**Môi trường FE:** demo/mock cục bộ, mốc Session 298–299 ngày 12/10/2026. Số liệu canonical lưu tại trình duyệt; không phải DB hoặc bằng chứng đa người dùng. Source chưa có commit/tag riêng để phát hành.
**[Chưa kiểm chứng live]** Chưa xác minh bản BE deploy hiện tại, OpenAPI/version hoặc endpoint mới. API có adapter trong FE không đồng nghĩa đã nghiệm thu bản BE mới. BE ưu tiên đối chiếu/tái sử dụng API đang có trước khi thêm endpoint.

### 1.3 Luồng đủ để BE tái hiện

1. Người dùng đăng nhập, chọn Company/Plant hợp lệ, mở Kho. Server lọc kho theo phạm vi; không để FE cộng lô để thay số balance chính thức.
2. Chọn một dòng Item + Warehouse, mở panel Thông tin/Lô/Giữ cho sản xuất/Lịch sử. Từ dòng lô hoặc danh sách lô, mở đúng Lot + Warehouse; history không lẫn kho/lô khác.
3. Nhập trực tiếp: Item và Warehouse ACTIVE, quantity > 0, unitCost >= 0 nhập rõ ràng. Item LOT phải có mã lô mới; không tái dùng lô ở luồng nhập này. Hạn dùng nếu có phải còn hiệu lực. Xác nhận hiển thị lượng/giá/giá trị. Hủy không ghi; thành công cập nhật balance/lot/movement cùng giao dịch.
4. Xuất thường: chọn vị thế, quantity > 0 và <= available tự do. Item LOT bắt buộc chọn lô. HOLD, REJECTED, hết hạn và phần reservation không được xuất thường.
5. Kiểm đếm: nhập countedQuantity >= 0 cho Item/Warehouse, hoặc Lot nếu LOT; delta = countedQuantity − lượng server hiện tại. Không thay đổi thì không ghi. Có chênh lệch phải có lý do, không giảm dưới reservation, không sửa lô không đủ điều kiện. Kiểm lại version khi xác nhận để tránh ghi đè biến động mới.
6. Chuyển kho: chọn hai kho ACTIVE khác nhau cùng Plant, 1–100 dòng, mỗi lượng > 0 và có lý do chung. Chuyển phần AVAILABLE tự do; lô giữ nguyên mã/ngày nguồn/hạn dùng. Một dòng lỗi thì toàn phiếu không ghi. Thành công có phiếu và cặp movement OUT/IN từng dòng; reload và retry không chuyển lần hai.
7. Đổi trạng thái: chọn AVAILABLE/HOLD/REJECTED, lý do và confirmation. Không đổi sang cùng trạng thái, không chuyển lô hết hạn thành AVAILABLE. Có reservation còn mở thì từ chối. Mock đổi chất lượng của lô chung ở tất cả vị thế; BE phải chốt ngữ nghĩa Q6, không mặc nhiên coi warehouseId giới hạn tác động.
8. Mở Biến động kho: xem lượng/giá trị trước-sau, loại, nguồn và actor/time. Số liệu lịch sử là snapshot lúc post, không tính lại bằng giá bình quân hôm nay.
9. Luồng WO hiện có giữ/bỏ giữ/xuất phần của chính WO dùng cùng nguồn tồn. Nhập thành phẩm vào HOLD; QC chuyển AVAILABLE hoặc REJECTED. Giá chưa biết giữ null. Nguồn WO/receipt/QC phải truy ra được; đây chưa phải tính đủ giá thành hoặc sổ WIP.
10. Lỗi giữ đầu vào; retry cùng yêu cầu. Đổi Company/Plant/actor đóng trạng thái cũ; server kiểm lại quyền ở từng read/command/replay.

### 1.4 Quyền và phạm vi

| Thao tác | Permission FE hiện dùng | Scope/kiểm tra BE đề xuất | Thay đổi |
|---|---|---|---|
| List/detail/history kho/lô/phiếu/movement | PERM_INVENTORY_READ | Company đúng, Plant hiện tại, kho/nguồn trong phạm vi | Không có |
| Receive/Issue/Count/Transfer | READ + PERM_INVENTORY_MOVE | Kho ACTIVE, Plant ACTIVE; transfer cả nguồn/đích trong cùng Plant | Ghi tồn/value/history/receipt |
| Đổi trạng thái lô | READ + PERM_INVENTORY_MANAGE | Toàn bộ vị thế bị tác động phải được phép; kiểm reservation và expiry | Chất lượng + lịch sử, không đổi on-hand/value |
| WO reserve/issue/output/QC | Giữ quyền command của luồng sản xuất hiện có | Đúng WO/component/warehouse/lot và owner reservation | Dùng cùng ledger, không vượt lượng được phép |

**[FE đã kiểm chứng]** Mock inventory kiểm Company MVP, Plant và scope trong lúc giữ khóa; UI không là authorization. **[Đề xuất BE]** Token/session quyết định actor; không tin actorId/Company/Plant từ body. `X-Plant-Id` phải phù hợp request và assignment còn hiệu lực. Truy cập trực tiếp ID ngoài scope không lộ dữ liệu; đề xuất NOT_FOUND cho record ẩn, PERMISSION_DENIED cho quyền command thiếu. Quyền/scope được kiểm trước trả receipt replay. Không có bước Manager approval riêng cho nhập/xuất/kiểm đếm/chuyển trực tiếp của GĐ4; confirmation FE không thay approval sản xuất.

## §2 — Bằng chứng tái hiện

**Không có lỗi BE được báo trong tài liệu này — bàn giao FE/mock và đề xuất contract.** Không dùng mock để kết luận một API thật có lỗi.

| Mảnh bằng chứng live | Tình trạng |
|---|---|
| Request thật | Không có — không chạy request inventory mới trong phiên bàn giao |
| Response thật + HTTP/code | Không có |
| X-Trace-Id thật | Không có; trace mock hoặc Idempotency-Key không được coi là trace server |
| Môi trường/giờ/bản BE | Chưa xác minh BE deploy; mốc FE demo Session 298–299 ngày 12/10/2026, không có giờ live |
| ID record thật cùng lượt | Không có; ID seed/demo không phải record server |

```text
Mong đợi: sau khi BE chốt contract và deploy, các ca A01–A26 ở §3 đạt trên cùng record/request có bằng chứng server.
Thực tế: FE/mock đã kiểm tra; nghiệm thu API giai đoạn này chưa chạy.
```

**Bằng chứng FE đã có:** Session 298 full regression 93 file/433 tests đạt, build/TypeScript/VI–EN đạt; focused follow-ups cho inventory/production receipt đạt. Session 299 sáu UI tests, build/TypeScript/i18n/lint đạt; browser kiểm stock panel và lô được chọn. Browser demo Session 298 đã thử nhập/reload, chuyển hai dòng, xuất/kiểm đếm, HOLD→REJECTED, đổi Plant, desktop/tablet VI/EN. Đây là kiểm chứng FE, không đóng acceptance BE. Không cần BE mở repo hoặc máy FE để hiểu yêu cầu bên dưới.

## §3 — Đầu ra mong đợi — Cần BE triển khai

**[Đề xuất BE]** Các mục dưới là hành vi cần có; nếu API/ledger hiện tại đã đáp ứng thì BE chỉ đối chiếu và cung cấp contract, không viết lại. Chỉ các quyết định §5 là xác nhận thuần túy.

### 3.1 API có adapter FE và khoảng cách cần đối chiếu

Các path đầy đủ dưới đây gồm context `/api`; FE client dùng root `/v1` bên dưới context đó. Chỉ xác minh source FE, **chưa xác minh deploy BE trong phiên này**.

| API trong adapter FE | Input/result hiện được FE khai báo | Phần cần đối chiếu/mở rộng |
|---|---|---|
| GET /api/v1/inventory/balances/aggregate | warehouseId bắt buộc, itemId/page/size/sortBy/sortDir; page một dòng Item + Warehouse, mã/tên/UOM và sáu lượng | Thêm giá trị/giá bình quân/version; search và scope nhiều kho nếu cần, không cộng lot phía FE |
| GET /api/v1/inventory/balances | warehouseId, itemId/page/size; vị thế balance/lot | Tái sử dụng cho detail nếu đủ dữ liệu; aggregate vẫn là nguồn bảng chính |
| GET /api/v1/inventory/lots | warehouseId bắt buộc, itemId/status/search/expiryFrom/expiryTo/page/size/sort; Lot + Warehouse quantities/source/date | Ngày nhập phải từ server; chốt no-expiry và expiry riêng chất lượng, UOM/notes/version |
| GET /api/v1/inventory/lots/{lotId}?warehouseId=... | Lot metadata + balances[] | Giới hạn balances theo quyền; detail theo đúng kho đang mở |
| POST /api/v1/inventory/lots/{lotId}/status | warehouseId,newStatus,reason,referenceType/referenceId; Idempotency-Key | Chốt global lot hay per-warehouse; version và affected positions/receipts |
| GET /api/v1/inventory/movements | warehouseId,itemId,type,page,size; movementId,type,direction,quantity,reason,reference,createdAt | Thêm lotId filter, actor/before-after/valuation/trace/source snapshot chính thức |
| POST /api/v1/inventory/receive | warehouseId,itemId,quantity,lotId/lotCode,reason,reference; Idempotency-Key | Adapter cũ chưa gửi unitCost/expiry; thêm contract sau chốt, không giả đã tích hợp |
| POST /api/v1/inventory/issue | cùng nhóm field quantity/lot/reference; Idempotency-Key | Free-stock guard và valuation server; không cho xuất phần reservation qua command thường |
| POST /api/v1/inventory/adjust | quantityDelta có dấu khác 0 + reason, item/warehouse/lot/reference; Idempotency-Key | Đây là delta API cũ, khác form mock countedQuantity tuyệt đối; không đổi ngầm semantics |

Typed DTO movement hiện có **không** khai báo actor/before-after/cost/value chính thức; record UI có các field đó không chứng minh server đã trả. Nguồn/receivedAt còn optional; thiếu ngày không được lấy createdAt thay ngày nhận. API adapter hiện chưa nối đầy đủ định giá/transfer/panel mới; chỉ nối sau khi BE trả contract được duyệt.

### 3.2 Yêu cầu kiểm được

| ID | Yêu cầu | API liên quan | Chặn FE? |
|---|---|---|---|
| R01 | Aggregate trả đúng một dòng Item + Warehouse, lượng authoritative và không rò phạm vi | balances/aggregate | Chặn live bảng mới; không chặn mock |
| R02 | Lot detail/history phân biệt lotId + warehouseId; source/date không suy đoán | lots, movements | Chặn live chi tiết mới |
| R03 | Receive lượng > 0, giá nhập rõ >= 0; tạo lot mới khi LOT, quantity/value/movement/receipt cùng commit | receive | Chặn live nhập có giá |
| R04 | Issue thường chỉ trừ free AVAILABLE chưa hết hạn, không vượt khả dụng dù hai request đồng thời | issue | Chặn live post |
| R05 | Count tính delta từ lượng DB dưới lock; version stale trả conflict, không ghi đè stock mới | counts đề xuất | Chặn live kiểm đếm tuyệt đối |
| R06 | Transfer tất cả dòng atomic, nguồn/đích cùng Plant ACTIVE, không chuyển reservation; mỗi dòng OUT/IN cùng phiếu | transfers đề xuất | Chặn live chuyển kho |
| R07 | Status change từ chối reserved/no-op/expired→AVAILABLE; kiểm phạm vi mọi vị thế bị tác động | lots/{id}/status | Chặn live đổi trạng thái |
| R08 | Lượng dùng decimal tối đa 6 số lẻ, không âm sau commit; invalid precision/overflow không bị làm tròn âm thầm | Tất cả command | Chặn live post |
| R09 | Cùng actor+key+payload trả receipt cũ; key đổi payload trả conflict; mất response không post lần hai | Tất cả command | Chặn live post |
| R10 | Movement/value snapshot không thay đổi khi giá bình quân hiện tại thay đổi | movements, detail | Chặn live valuation |
| R11 | WO reserve/issue/output/QC dùng cùng ledger; WO dùng đúng phần của mình khi free=0; output HOLD/cost null khi chưa định giá | Command sản xuất hiện có | Chặn live liên thông |
| R12 | Actor/time/reference/trace do server ghi, cả thành công và lỗi có trace tương ứng request thật | Tất cả API | Chặn nghiệm thu/audit |

### 3.3 Contract đề xuất và dữ liệu

**Quy ước đề xuất:** ID UUID string ở wire, không dùng số seed FE làm ID BE. Quantity/amount/unitCost là chuỗi decimal; quantity NUMERIC(19,6), money tối đa 6 số lẻ (precision DB BE chốt Q10), averageCost 12 số lẻ. Timestamp ISO-8601 có timezone; expiry là instant, ranh giới hết hạn so với đồng hồ server (Q8). Thiếu giá là null, không phải 0. Field optional có thể bỏ khi không áp dụng; nullable chỉ null khi có ý nghĩa rõ. Version integer tăng theo pool/record do server cấp.

| Field | Kiểu | Nullable | Nguồn/công thức đề xuất | UI |
|---|---|---|---|---|
| onHandQuantity/reservedQuantity/availableQuantity | decimal string | Không | Ledger và reservation, công thức §3.4 | Kho/lot |
| qualityHoldQuantity/rejectedQuantity/expiredQuantity | decimal string | Không | Phân nhóm chất lượng/expiry §3.4 | Kho |
| averageCost | decimal string, 12 số lẻ | Có | stockValue/onHand khi có giá và onHand>0 | Kho/detail |
| stockValue | decimal string, 6 số lẻ | Có | Book value pool Item + Warehouse; null khi có phần chưa định giá | Kho/detail |
| currency | string | Không | VND trong MVP đề xuất | Giá trị |
| version | integer | Không | Version server của pool/lot | Count/status/stale |
| unitCost | decimal string, input | Không khi receive; optional count | Giá nhập rõ; count tăng dùng pool average hoặc giá explicit khi chưa có | Form |
| receivedAt | timestamp | Có cho lịch sử chưa xác định | Receipt source đã post; không giả bằng ngày tạo record | Lot |
| expiresAt | timestamp | Có | Hạn dùng do source khai báo; transfer giữ nguyên | Lot/filter |
| sourceReferenceType/sourceReferenceId/sourceCode/sourceAt | string/UUID/string/timestamp | Có | MANUAL/OPENING/WO/RECEIPT/TRANSFER/QC, bản ghi nguồn server | Lot/history |
| uom | string | Không khi Item có UOM | Item master | Lot/quantity |
| notes | string | Có | Ghi chú hợp lệ/audit status reason | Lot |
| beforeOnHandQuantity/afterOnHandQuantity | decimal string | Không | Snapshot pool Item + Warehouse tại post, kể cả movement có lotId; lượng Lot nằm ở lot detail | Movement |
| signedQuantityDelta | decimal string | Không | OUT âm, IN dương, status/reserve/release 0; không đổi field quantity cũ | Movement/detail |
| recordedUnitCost | decimal string | Có | Giá nhập hoặc average lúc xuất; null nếu chưa biết | Movement |
| movementValue/valueBefore/valueAfter | decimal string | Có | Signed book-value delta và pool before/after lúc post | Movement/detail |
| createdByUsername/occurredAt/traceId | string/timestamp/string | Không với giao dịch mới | Actor/session + server clock + audit trace; lịch sử thiếu cần chốt Q12 | Movement/transfer |
| workOrderId/componentId/remainingReservedQuantity | UUID/UUID/decimal string | ID nullable chỉ tồn đầu kỳ không có WO | Reservation thật; remaining=reserved−issued | Tab Giữ cho sản xuất |
| transferId/transferCode/lineNo/outMovementId/inMovementId | UUID/string/integer/UUID/UUID | Không | Phiếu/line/pair do server cấp | Chi tiết chuyển |

**Endpoint mở rộng đề xuất — chưa có adapter/live acceptance:**

| Method/path | Query/body | Success result | Phân trang | Quyền/idempotency |
|---|---|---|---|---|
| GET /api/v1/inventory/reservations | warehouseId,itemId,lotId?,status?,page,size | Page reservation với nguồn WO/component/remaining | Có; page=0,size=20 mặc định | READ; không key |
| POST /api/v1/inventory/counts | itemId,warehouseId,lotId?,countedQuantity,expectedVersion,unitCost?,reason | Count receipt, delta, movements[], newVersion | Không | READ+MOVE; key bắt buộc |
| POST /api/v1/inventory/transfers | fromWarehouseId,toWarehouseId,lines[{itemId,lotId?,quantity}],reason | Phiếu đã POSTED + line pairs + value snapshots | Không | READ+MOVE; key bắt buộc |
| GET /api/v1/inventory/transfers | warehouseId? (ở một trong hai phía),search?,page,size,sortBy?,sortDir?; Plant header bắt buộc | Page phiếu cùng Plant trong scope | Có | READ; không key |
| GET /api/v1/inventory/transfers/{id} | ID | Phiếu, lines[], linked movements[], actor/time/reason | Không | READ; direct ID scope |
| GET /api/v1/inventory/movements/{id} | ID | Snapshot movement đủ các field ở trên | Không | READ; direct ID scope |

Mở rộng GET movements thêm `lotId`, `search`, lọc loại TRANSFER_OUT/IN/LOT_STATUS_CHANGE/RESERVE/RELEASE và order time+ID ổn định. Thêm tìm kiếm aggregate `search`; có thể giữ warehouseId bắt buộc ban đầu, FE chọn một kho khi API mode. Không lấy một trang lot để tự dựng aggregate nhiều kho. No-expiry filter đề xuất `expiryState=NONE`; EXPIRED/EXPIRING do server tính, EXPIRING trong (now,now+30 ngày], không làm biến mất trạng thái chất lượng gốc. GET detail không ghi tồn. Không PUT/DELETE transfer ở phạm vi này.

**Ví dụ đề xuất, IDs hoàn toàn minh họa, không phải record thật:**

```json
{
  "fromWarehouseId": "00000000-0000-4000-8000-000000000101",
  "toWarehouseId": "00000000-0000-4000-8000-000000000102",
  "lines": [
    {"itemId": "00000000-0000-4000-8000-000000000201", "lotId": "00000000-0000-4000-8000-000000000301", "quantity": "2.000000"}
  ],
  "reason": "Chuyển vật tư sang kho sản xuất"
}
```

POST transfer dùng `Idempotency-Key: 00000000-0000-4000-8000-000000000901` và `X-Plant-Id` là UUID Plant sở hữu cả hai kho (không đưa token vào tài liệu). Ví dụ result tối thiểu dưới khớp case giá tại §3.4; DTO detail còn các field metadata/snapshot đã nêu trong bảng, không coi result tối thiểu thay thế detail đầy đủ.

```json
{
  "code": "SUCCESS", "message": "Success",
  "result": {
    "transferId": "00000000-0000-4000-8000-000000000401",
    "transferCode": "TR-00001", "status": "POSTED", "currency": "VND",
    "fromWarehouseId": "00000000-0000-4000-8000-000000000101",
    "toWarehouseId": "00000000-0000-4000-8000-000000000102",
    "lines": [{
      "lineNo": 1, "itemId": "00000000-0000-4000-8000-000000000201",
      "lotId": "00000000-0000-4000-8000-000000000301", "quantity": "2.000000",
      "transferredValue": "24.000000",
      "outMovementId": "00000000-0000-4000-8000-000000000501",
      "inMovementId": "00000000-0000-4000-8000-000000000502"
    }]
  }
}
```

**Ví dụ input bổ sung đề xuất:**

```json
{
  "itemId": "00000000-0000-4000-8000-000000000201",
  "warehouseId": "00000000-0000-4000-8000-000000000101",
  "quantity": "5.000000", "unitCost": "16.000000",
  "lotCode": "NEW-LOT-001", "expiresAt": "2027-12-31T16:59:59Z", "reason": "Nhập bổ sung"
}
```

```json
{
  "itemId": "00000000-0000-4000-8000-000000000201",
  "warehouseId": "00000000-0000-4000-8000-000000000101",
  "lotId": "00000000-0000-4000-8000-000000000301",
  "countedQuantity": "8.000000", "expectedVersion": 4, "reason": "Kiểm đếm thực tế"
}
```

Ví dụ count này là độc lập, không tiếp nối ví dụ transfer: giả định vị thế hiện có 10, version 4; count 8 => delta −2. API adjust cũ vẫn nhận delta, không được hiểu countedQuantity như quantityDelta. Đề xuất status body thêm expectedVersion, giữ warehouseId/newStatus/reason hiện có; server trả affected positions/new version để FE cập nhật mọi kho liên quan.

Page thành công dùng envelope dưới; content DTO tùy endpoint, quantity decimal string. Server lọc scope và search **trước** phân trang/count. Tổng của một trang không phải tổng ledger toàn kho.

```json
{"code":"SUCCESS","message":"Success","result":{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}}
```

### 3.4 Công thức, tác động và ví dụ số

**[FE đã kiểm chứng; đề xuất BE đối chiếu]** Vị thế p=(Item,Warehouse,Lot hoặc không lô), qty>=0; remaining reservation=sum(quantity−issued) của ACTIVE. Eligible = không lô hoặc status AVAILABLE và expiresAt không có hoặc > server now.

- onHand=sum qty mọi vị thế; reserved=sum remaining. available=sum(qty−remaining) chỉ các vị thế eligible, không phải onHand−reserved bỏ qua chất lượng/hết hạn.
- HOLD và REJECTED đếm theo chất lượng; expired trong mock chỉ đếm AVAILABLE đã hết hạn để không đếm hai lần với HOLD/REJECTED. BE chốt Q7; không coi expired là chất lượng thay thế mặc nhiên.
- Ví dụ lượng độc lập: AVAILABLE chưa hết hạn 10, giữ 3; HOLD 2; REJECTED 1; AVAILABLE hết hạn 4 => onHand=17,reserved=3,available=7,hold=2,rejected=1,expired=4.
- Moving average theo pool Item+Warehouse; value=sum book value đã biết, average=value/onHand khi biết và onHand>0. Receive cộng quantity×unitCost. Issue trừ lượng×average tại post, money làm tròn 6 số; xuất hết lấy toàn bộ value dư của pool. Transfer nhập đích đúng value xuất nguồn, không định giá lại theo average đích.
- Ví dụ giá độc lập cho Item ...201: nguồn kho ...101 có 10/value100, receive thêm 5×16=80 => nguồn15/value180, average12. Transfer2 sang kho ...102 đang có5/value100 => nguồn13/value156; đích7/value124, average17.714285714286. Tổng onHand20 trước/sau transfer; tổng value280 trước/sau transfer; cặp OUT −24 / IN +24. Phiếu JSON phía trên dùng đúng ví dụ này.
- Count giảm dùng average; count tăng dùng average hiện có hoặc giá explicit nếu không có average. Có bất kỳ phần chưa định giá thì value/average null; thêm lượng có giá không biến tồn cũ chưa biết thành giá0. Null có thể lan sang pool đích khi nhận stock thiếu giá; giữ lịch sử giá known, không bịa giá để đủ tổng.
- Chất lượng/expiry/reserve không tự giảm book value. REJECTED vẫn còn hiện vật; hủy/đánh giá giảm là workflow khác.

| Event | On Hand | Reserved | Available | Chất lượng/giá trị/nguồn |
|---|---|---|---|---|
| Receive trực tiếp | +q | Không đổi | +q khi hợp lệ | AVAILABLE; +q×giá explicit; nguồn trực tiếp |
| Issue thường | −q | Không đổi | −q | Free-stock only; snapshot average/value |
| Count | Bằng counted | Không phá reservation | Theo delta eligible | Lý do + before/after; stale conflict |
| WO reserve/release | Không đổi | +q/−q | −q/+q | Nguồn WO/component, value không đổi |
| Issue đúng reservation WO | −q | −q còn giữ | Free không bị trừ thêm q | WO dùng phần của chính mình; snapshot nguồn/value nối WIP |
| Transfer | Nguồn−q, đích+q | Không chuyển | Nguồn−q, đích+q | Giữ Lot/date/expiry; OUT/IN đối ứng book value |
| Status change/QC | Không đổi | Có reservation thì status change thường bị chặn | Theo eligible mới | Không ghi giảm value; reason/actor/history; QC theo luồng receipt hiện có |
| Production output | +q | Không đổi | 0 khi HOLD | HOLD; giá chưa biết null, trace WO/receipt/QC |

### 3.5 Atomicity, cạnh tranh và retry

**[Đề xuất BE]** Validate quyền/master/quantity/expiry/version dưới DB transaction/lock, không chỉ ở lần GET/preview. Lock theo thứ tự xác định các pool/vị thế và reservation để không deadlock. Sum các dòng trùng Item/Lot nguồn theo toàn phiếu; ví dụ hai dòng6 và5, free10 phải fail toàn bộ, không qua vì mỗi dòng riêng <10. Quy định có cho dòng trùng hay reject ở Q9.

Stock/value/lot/source/movements/phiếu/audit/receipt ghi một transaction; lỗi hoặc timeout trước commit không để phiếu một phía. Mã phiếu unique do server cấp, không dùng list.length. Sau post không sửa/hủy/đảo trong GĐ4. Read ngay sau post phải thấy kết quả đã commit.

Receipt key đề xuất unique theo Company+actor+command+Idempotency-Key; fingerprint canonical payload. Cùng key/payload trả đúng IDs/kết quả cũ, không gọi lại mutation. Cùng key khác payload => IDEMPOTENCY_CONFLICT. Kiểm auth/scope trước replay. Hai request đồng thời cùng key chỉ có một commit. Mất response sau commit retry vẫn đọc receipt; retention/race pending do BE chốt Q11. Trace mỗi HTTP request và immutable source transaction có thể khác, phải phân biệt; không dùng key thay X-Trace-Id bằng chứng.

Status global lot phải kiểm toàn bộ kho/Plant có vị thế kể cả kho không hiển thị trong panel; thiếu phạm vi bất kỳ vị thế thì fail toàn bộ, không lộ kho ngoài scope. Nếu BE chọn per-warehouse phải trả DTO rõ và FE sửa sau chốt, không áp policy global từ body warehouseId một cách ngầm định.

### 3.6 Nghiệm thu live và rollout

Tất cả dưới đây **chưa chạy live / CHỜ BE**. Các record được tạo riêng trên môi trường test, ghi lại ID thật/version, request/response HTTP/code, X-Trace-Id và giờ/version deploy cùng lượt. Không dùng UUID minh họa làm bằng chứng. BE “triển khai xong” và FE “nghiệm thu đạt” là hai trạng thái riêng.

| ID | Fixture/thao tác | Kết quả mong đợi kiểm được | Bằng chứng/trạng thái |
|---|---|---|---|
| A01 | Pool có nhiều lô như ví dụ lượng §3.4; GET aggregate | Một dòng,17/3/7/2/1/4 theo policy chốt | Live chưa chạy |
| A02 | Một lot ở hai kho; mở detail/history kho A | Metadata đúng lot; lượng/history đúng kho A, không lẫn B | Live chưa chạy |
| A03 | Receive5 giá16 vào pool10/value100 |15/value180/avg12, một movement và receipt | Live chưa chạy |
| A04 | Receive giá0 rõ ràng và receive thiếu giá riêng | Giá0 hợp lệ; thiếu giá lỗi không ghi | Live chưa chạy |
| A05 | Item LOT nhập mã mới; mã trùng khác hoa/thường | Mới tạo một lot; trùng lỗi/không ghi | Live chưa chạy |
| A06 | Receive expiry đã hết/invalid | Lỗi expiry, giữ tồn/value/history | Live chưa chạy |
| A07 | Free7 issue7; issue8 trên fixture riêng |7 thành công;8 lỗi không ghi | Live chưa chạy |
| A08 | Issue HOLD/REJECTED/expired | Mỗi ca không post | Live chưa chạy |
| A09 | Count từ10 về8 version hiện tại | Delta−2, đúng before/after/value | Live chưa chạy |
| A10 | Count bằng hiện tại | NO_CHANGE, không movement | Live chưa chạy |
| A11 | Count dưới remaining reservation | RESERVED_STOCK, không đổi | Live chưa chạy |
| A12 | GET version4, có movement mới, count expected4 | STALE_VERSION, không ghi đè | Live chưa chạy |
| A13 | Count tăng pool có/không average | Có dùng avg; chưa có cần giá explicit; thiếu lỗi | Live chưa chạy |
| A14 | Transfer2 theo ví dụ giá §3.4 |13/value156 và7/value124, một phiếu/cặp movement | Live chưa chạy |
| A15 | Transfer nhiều dòng, một dòng không hợp lệ | Không pool/value/phiếu/movement nào đổi | Live chưa chạy |
| A16 | Transfer cùng kho/khác Plant/kho inactive | Từng ca bị từ chối toàn bộ | Live chưa chạy |
| A17 | Transfer LOT có source date/expiry | Giữ lot/date/expiry và trace nguồn | Live chưa chạy |
| A18 | Transfer hai dòng cùng vị thế tổng>free | Fail toàn bộ hoặc reject duplicate theo Q9 | Live chưa chạy |
| A19 | Hai issue/transfer cạnh tranh free cuối | Không âm; chỉ lượng hợp lệ commit | Live chưa chạy |
| A20 | Double click/cùng key đồng thời/mất response retry | Một commit, cùng IDs/receipt, không ghi lần hai | Live chưa chạy |
| A21 | Cùng key đổi payload | IDEMPOTENCY_CONFLICT, giữ ledger | Live chưa chạy |
| A22 | READ-only, sai Company/Plant/direct ID, thu hồi quyền rồi retry | Không post/không rò record/không replay vượt quyền | Live chưa chạy |
| A23 | Status reserved/no-op/expired→AVAILABLE và global lot có vị thế ngoài scope | Từng ca fail; không đổi một phần | Live chưa chạy |
| A24 | Status hợp lệ; reserve/release; WO dùng own reservation khi free0 | OnHand/value status/reserve không đổi; WO issue giảm đúng onHand/remaining | Live chưa chạy |
| A25 | Output HOLD giá null→QC; thêm lượng known; xem movement cũ | Không giả giá0; không tự known cả pool; history snapshot không đổi | Live chưa chạy |
| A26 | Precision7/overflow và lỗi DB giữa transfer; reload/search/page | Invalid không post, transaction rollback cả phiếu; list ổn định/không mất nguồn | Live chưa chạy |

**Thứ tự rollout:** (1) BE trả Q1–Q14 + OpenAPI/version; (2) thống nhất ledger/reservation/valuation/migration; (3) read aggregate/lots/history; (4) receive/issue/count/status; (5) atomic transfer/receipt/detail; (6) nối/kiểm các command sản xuất; (7) FE sửa adapter sau PO duyệt scope; (8) chạy A01–A26 và mới bật luồng API tương ứng.
**Migration [Đề xuất BE]:** không import localStorage/mock seed sang production. Giữ IDs/qty/reservation/history/trace có thật; lịch sử thiếu giá/date/actor không fabricate; báo cáo backfill đã/chưa biết, chính sách định giá Q12. Opening reservation mock không có WO thật không được chuyển thành WO giả. Chỉ chuyển mock→API sau contract/live gates của luồng đạt, không fallback ngầm hoặc ghép issue+receive thành transfer. Không đổi API delta cũ gây breaking change.

## §4 — Hợp đồng lỗi mong đợi

**[Đề xuất BE]** Mã wire bên dưới cần đối chiếu mã hiện có trước tích hợp; không coi tên lỗi internal mock là code BE đã deploy. Field path chỉ đúng dòng lỗi; lỗi không reset input. Secret không có trong tài liệu.

| Tình huống | HTTP đề xuất | Code đề xuất | FE hiển thị |
|---|---|---|---|
| Thiếu/sai field, lượng <=0, count âm |400|VALIDATION_ERROR|Lỗi tại field/dòng |
| Quá precision6/overflow lượng/giá trị |400|DECIMAL_PRECISION_EXCEEDED / VALUE_OUT_OF_RANGE|Giới hạn số, không tự làm tròn |
| Receive thiếu/giá âm |400|UNIT_COST_REQUIRED / INVALID_UNIT_COST|Ô đơn giá |
| Không đủ free stock |409|INSUFFICIENT_AVAILABLE_STOCK|Khả dụng đã đổi, tải lại và giữ form |
| Phá/đổi status reservation |409|RESERVED_STOCK|Giải phóng đúng nguồn trước |
| Lot thiếu/trùng/khác Item |400/409|LOT_REQUIRED / LOT_CODE_ALREADY_EXISTS / LOT_ITEM_MISMATCH|Ô lô/dòng |
| HOLD/REJECTED/expired |409|LOT_NOT_ELIGIBLE / LOT_EXPIRED|Không thể xuất/chuyển/đổi khả dụng |
| Warehouse/Item/Plant inactive |409|WAREHOUSE_INACTIVE / ITEM_INACTIVE / PLANT_INACTIVE|Danh mục không dùng được |
| Source=destination / khác Plant |400/403|SAME_WAREHOUSE / PLANT_SCOPE_MISMATCH|Chọn hai kho hợp lệ cùng Plant |
| Count/status stale |409|STALE_VERSION|Số liệu đã đổi; refresh trước xác nhận lại |
| Không đổi quantity/status |409|NO_CHANGE|Không có thay đổi để ghi nhận |
| Key dùng nội dung khác |409|IDEMPOTENCY_CONFLICT|Mở thao tác mới; không tự post key mới |
| Thiếu quyền / ID ngoài scope |403/404|PERMISSION_DENIED / NOT_FOUND|Không đủ quyền / không tìm thấy |
| Hết phiên |401|Mã Auth hiện có theo contract giai đoạn1|Luồng đăng nhập lại đã có |
| Server lỗi trước commit |500|INTERNAL_ERROR|Giữ form, retry key cũ |

**Ví dụ response lỗi đề xuất**, không phải response thật; `traceId` nếu có trong JSON chỉ bổ sung header `X-Trace-Id` thật của server:

```json
{
  "code": "INSUFFICIENT_AVAILABLE_STOCK", "message": "Not enough available stock",
  "errors": [{"field": "lines[1].quantity", "message": "Requested quantity exceeds free available stock"}]
}
```

Success envelope `{code,message,result}`; error `{code,message,errors?}` có thể không có result theo NON_NULL hiện có. Không yêu cầu error trả result rỗng. GET/POST error phải có `X-Trace-Id` dùng tra log; timeout mất response thì FE ghi rõ chưa thu được trace, không tự tạo thay.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi câu là một quyết định; chưa trả lời không chặn FE/mock giai đoạn tiếp, nhưng chặn adapter/live của phần liên quan.

1. **Q1:** BE đồng ý tái sử dụng API read/post hiện có trước khi thêm endpoint ở §3 không? FE nghiêng về Có để tránh hai ledger. **CHẶN adapter/live, KHÔNG CHẶN mock.**
2. **Q2:** Chuyển kho GĐ4 chỉ cùng Plant và không có in-transit/approval riêng? FE nghiêng về Có vì phạm vi đã duyệt. **CHẶN adapter transfer, KHÔNG CHẶN mock.**
3. **Q3:** BE dùng endpoint count tuyệt đối riêng thay vì đổi semantics quantityDelta cũ? FE nghiêng về Có để tránh breaking change/cạnh tranh stale. **CHẶN adapter count, KHÔNG CHẶN mock.**
4. **Q4:** Count bắt buộc expectedVersion server của pool/vị thế? FE nghiêng về Có để không ghi đè biến động mới. **CHẶN live count, KHÔNG CHẶN mock.**
5. **Q5:** VND moving-average theo Item + Warehouse là policy GĐ4? FE nghiêng về Có; chưa triển khai full costing. **CHẶN valuation live, KHÔNG CHẶN mock.**
6. **Q6:** Chất lượng lot global trên mọi warehouse position, thay vì per-warehouse? FE nghiêng về Global với kiểm scope toàn bộ; mock đang theo Global. **CHẶN adapter/status live, KHÔNG CHẶN mock.**
7. **Q7:** expiredQuantity chỉ đếm AVAILABLE hết hạn, không cộng lại HOLD/REJECTED? FE nghiêng về Có để nhóm không trùng. **CHẶN aggregate live, KHÔNG CHẶN mock.**
8. **Q8:** Hạn dùng là timestamp so với đồng hồ server thay vì ngày-local? FE nghiêng về timestamp timezone rõ. **CHẶN expiry adapter/live, KHÔNG CHẶN mock.**
9. **Q9:** Transfer cho dòng trùng vị thế nhưng kiểm tổng toàn phiếu? FE nghiêng về Có; thứ tự line snapshot ổn định. **CHẶN transfer validation live, KHÔNG CHẶN mock.**
10. **Q10:** Money 6 số lẻ/average12, ROUND_HALF_UP và xuất hết lấy dư book value là quy tắc chung? FE nghiêng về Có; BE trả precision money DB tương ứng. **CHẶN valuation live, KHÔNG CHẶN mock.**
11. **Q11:** Receipt idempotency được lưu bền với phiếu không mất khả năng replay sau reload/retry? FE nghiêng về Có; BE công bố retention/pending request policy. **CHẶN post live, KHÔNG CHẶN mock.**
12. **Q12:** Giá lịch sử chưa biết giữ null thay vì backfill0? FE nghiêng về Có, chỉ backfill từ bằng chứng giá thật. **CHẶN valuation migration/live, KHÔNG CHẶN mock.**
13. **Q13:** Wire DTO decimal mới chỉ dùng string, UUID thay ID seed? FE nghiêng về Có để giữ độ chính xác. **CHẶN adapter mới, KHÔNG CHẶN mock.**
14. **Q14:** Bộ mã lỗi §4 được mapping vào code BE hiện có thay vì đổi toàn bộ code đang dùng? FE nghiêng về Có; BE trả bảng mapping chính xác/OpenAPI deploy. **CHẶN xử lý lỗi/acceptance live, KHÔNG CHẶN mock.**

**[Giả định cần xác minh]:** master Company/Plant/Warehouse/Item/UOM và quyền inventory giai đoạn trước đã sẵn sàng trên môi trường BE mục tiêu. Không mặc định toàn bộ các gate trước đã đạt chỉ vì GĐ4 FE xong.

## §6 — FE đã tự xử lý

- Canonical mock dùng chung positions/lots/reservations/values/movements/transfers/receipts; nối projection sản xuất hiện có, không dùng stock store riêng cho từng màn hình.
- Decimal quantity6 và price/value, kiểm quantity/eligible/reserved/master ACTIVE; production number cũ được chuẩn hóa ở boundary6, command string mới reject quá precision.
- Staged all-or-none và một lần lưu snapshot+receipt, Web Locks khóa master+inventory; context được đọc lại sau lấy khóa, đổi/thu hồi quyền/unmount không giữ quyền cũ. Đây là browser demo, không thay DB transaction/auth BE.
- Confirmation, chặn double click, giữ input khi lỗi, retry key cũ; null price không default0. Skeleton/loading/error/empty, filters/page20, VI/EN, scope switch đóng form/detail. Mock count tính trên snapshot sau khi lấy khóa; expectedVersion wire là phòng vệ BE đề xuất bổ sung, chưa phải field mock đã gửi.
- Panel bên phải chi tiết tồn/lô giữ bảng phía sau; selected lot/warehouse history riêng. Form mutations/confirmation vẫn giữa màn hình. Đây là UX FE, không yêu cầu BE xây panel.
- Mock status global và unknown-value policy nêu rõ §3/§5; chưa coi là policy BE được duyệt. Runtime legacy còn tồn giữ quantity/history/trace, thiếu giá null; không thể hồi phục dữ liệu runtime đã mất trước migration.
- API adapters hiện có vẫn lấy aggregate/lot/date chính thức; transfer API chưa sẵn sàng trên FE, không fallback mock hoặc gọi hai mutations. Chưa bật endpoint/valuation mới chỉ vì đã có mock.
- Không gửi tài liệu ra ngoài, không thay API flags/source ứng dụng trong phiên bàn giao. Kết quả test FE tại §2 không thay acceptance A01–A26.

## §7 — Tham chiếu

Tên file dưới đây chỉ để nhận diện; toàn bộ yêu cầu GĐ4 đã nằm trong tài liệu này, không yêu cầu BE có repo FE hoặc đã nhận mọi file.

- `BE_HANDOFF_GD_01_2026-10-09.md`: bản bàn giao GĐ1 đã chuẩn bị trước quy tắc đặt tên mới; dependency Auth/email/quyền/scope. Giữ tên bản lịch sử, không đổi tên bản đã phát hành trong task này.
- `FE_HANDOFF_GD_02_2026-10-12.md`: master Company/Plant/Warehouse/UOM/Item/Supplier/Customer và scope; chuẩn bị trước GĐ4.
- `FE_HANDOFF_GD_03_2026-10-12.md`: import create-only master; không bao gồm import stock/lot hoặc ledger.
- `FE_HANDOFF_GD_04_MESSAGE_2026-10-12.md`: bốn dòng PO copy gửi cùng file này.
- OpenAPI/version inventory của BE: **chưa nhận/đối chiếu bản deploy trong phiên bàn giao**; BE gửi bản tương ứng khi trả Q1–Q14.

**Checklist trước giao:** §0–§7 đầy đủ; có ngoài phạm vi và màn hình mới/sửa; không báo defect thiếu bằng chứng; năm mảnh live thiếu đã ghi rõ; JSON copy được, không chứa secret; R01–R12 và A01–A26 kiểm được; field đủ5cột; công thức/ví dụ nhất quán; code lỗi tách HTTP; Q1–Q14 xác nhận riêng; implementation/confirmation và verified/assumed/proposed tách rõ; companion4dòng; trạng thái vẫn chờ BE contract/live.

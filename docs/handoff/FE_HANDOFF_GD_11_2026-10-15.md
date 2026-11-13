# Giai đoạn 11 — Bàn giao Backend: Giá thành, chênh lệch và đóng lệnh

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock GĐ11 hoàn tất; cần BE cung cấp mapping hoặc triển khai đơn giá giờ, cơ sở chi phí theo lệnh, tính/phân bổ giá thành và chốt giá cùng đóng lệnh.
- Ảnh hưởng: quản lý chi phí, quản lý sản xuất và người xem checklist dùng được demo; hai workspace mở rộng GĐ11 chưa sử dụng được bằng API đã xác minh, các luồng trước vẫn độc lập.
- Mức ưu tiên: P1 — luồng giá thành/đóng lệnh mở rộng bằng API đang chờ contract và triển khai; không chặn tiếp tục FE/mock.

**Bản:** v1 · **Ngày dự kiến gửi:** 15/10/2026 · **Mốc FE:** Session320, 15/10/2026 · **Phiên tài liệu:** Session321.
**PO:** duyệt triển khai bằng “bắt đầu”, yêu cầu tạo tài liệu sau FE delivery. **FE owner/BE owner cá nhân:** chưa chỉ định.
**Trạng thái:** đã chuẩn bị để PO gửi, chưa gửi bên ngoài; chưa nghiệm thu BE. Nếu gửi ngày khác, đổi ngày/tên trước lần gửi đầu; sửa sau gửi dùng `_v2`, `_v3` trong cùng task.

Nhãn dùng trong toàn tài liệu:

- **[Đã kiểm chứng FE]:** đối chiếu source và bằng chứng kiểm thử FE/mock; không chứng minh DB/API đang deploy.
- **[Đề xuất BE]:** path, DTO, permission mapping, mã lỗi và yêu cầu triển khai cần BE xác nhận. Nếu BE đã có API tương đương, trả mapping/OpenAPI trước, không xây trùng hoặc đổi contract cũ ngầm.
- **[Giả định]:** policy demo đã chọn nhưng chưa có xác nhận server/kế toán của BE; PO duyệt FE không thay xác nhận kỹ thuật BE.
- **[Chưa kiểm chứng live]:** không có request BE trong phiên tài liệu; tất cả JSON, UUID, revision và thời điểm minh họa dưới đây là **ví dụ đề xuất**, không phải record/response BE thật.

## §1 — Phạm vi

### 1.1 Toàn bộ 22 màn hình/phần liên quan

| # | Loại | Màn hình/phần | Dữ liệu/hành vi cần BE |
|---|---|---|---|
| 1 | Mới | Danh sách Giá thành sản xuất | WO/Item/Plant/ngày/readiness, search và phân trang |
| 2 | Mới | Chi tiết — Tổng quan | Planned/actual/variance, unit cost, còn kho/đã xuất/loss và lý do unknown |
| 3 | Mới | Chi tiết — Vật tư | Requirement baseline, từng issue-line/consumption/return/unused/value và nguồn |
| 4 | Mới | Chi tiết — Công đoạn | Planned/actual minutes, capacity/rate snapshot, execution/retry và chi phí |
| 5 | Mới | Chi tiết — Chênh lệch | Material/labor/overhead/total, sản lượng/thời gian và phần trăm có ngữ nghĩa rõ |
| 6 | Mới | Chi tiết — Phân bổ và nguồn | Receipt/QC descendants, stock/COGS allocations, adjustments, attribution blocker |
| 7 | Mới | Lịch sử tính giá | Kết quả tính có version/source, actor/time và hồ sơ chốt bất biến |
| 8 | Mới trong trang hiện có | Đơn giá trung tâm sản xuất | Minimal Work Center + VND/giờ, phiên bản và lịch sử giá |
| 9 | Mới | Thiết lập đơn giá | Labor/overhead, reason, expected rate version, xác nhận và audit |
| 10 | Mới | Tính giá thành | Lưu preview có revision, không stock effect, không nhận client-calculated totals |
| 11 | Mới | Danh sách Đóng lệnh sản xuất | COMPLETED/CLOSED, readiness/filter/page, operational projection riêng |
| 12 | Mới | Kiểm tra trước đóng | Từng điều kiện đạt/chưa đạt, blocker code và source reference tối thiểu |
| 13 | Mới | Chốt giá thành và đóng lệnh | Một atomic command: final snapshot/allocations/value adjustment/CLOSED/audit/replay |
| 14 | Sửa | Danh sách/chi tiết WO | Related costing/checklist, closed actor/time; COMPLETE khác CLOSE |
| 15 | Sửa xử lý, dùng lại form | Phát hành WO | Capture cost baseline tại RELEASE; không nhập lại BOM/routing |
| 16 | Sửa liên kết | Thực thi sản xuất | Duration/consumption/retry nguồn và liên kết costing/checklist |
| 17 | Sửa | WIP/xuất/hoàn vật tư | Unused/residual, source values, chặn mutation sau đóng |
| 18 | Sửa liên kết/guard | Nhập thành phẩm/QC | Partial receipt/quality allocations, rejected giữ giá trị, closed guards |
| 19 | Sửa | Tồn kho/lô/biến động | Điều chỉnh giá trị có nguồn, quantity0, cost permission và history |
| 20 | Sửa | Chi tiết giao hàng | Effective cost của từng dòng, tách selling price/invoice/payment |
| 21 | Tương thích projection | MRP/SO | CLOSED không là future WO supply; giao/thu/demand riêng, không sửa saved runs |
| 22 | Sửa | Sidebar/quyền | Hai workspace, authority tính/xem/đóng/rate và minimal scoped references |

Route FE, **không phải endpoint BE**: `/manufacturing/production-costs`, `/manufacturing/production-close`; đơn giá nằm trong `/manufacturing/work-centers`. Detail panel phải/body cuộn, thao tác ngắn dùng hộp giữa. Mục21 giữ projection hiện có, không phải một trang MRP/SO mới.

**Trong phạm vi:** VND; cost baseline khi RELEASE, labor/overhead theo giờ hoạt động; actual material consumption/return; execution time/retry; partial receipt/QC allocations; direct-delivery cost trước/sau chốt; preview/history/checklist; atomic finalize+close; current scope/authorization/version/idempotency; stock value-only adjustments; legacy/unknown/valuation boundary.

**Ngoài phạm vi:** GL/sổ cái và chứng từ kế toán pháp lý; payroll, lương từng người, hóa đơn chi phí thực trả; VAT/FX/multi-currency; tính OEE/MES; thay giá bán, invoice, payment hoặc đợi khách thanh toán; tự write-off QC rejected; đảo bút toán/reopen/correction; tự sửa chứng từ đã post; hồi tố dữ liệu cũ bằng giá hôm nay; full moving-average replay mọi transfer/downstream graph; tự đóng các quyết định GĐ6/8/9/10 đang mở.

**Môi trường FE [đã kiểm chứng]:** dev/demo mock Session320 ngày15/10/2026, chưa commit/tag phát hành; desktop1280×720 VI, tablet834×1112 EN. Phiên tài liệu321 chỉ đọc source/tạo tài liệu, không gọi server, OpenAPI hoặc xác minh deployed version mới nhất.

### 1.2 Existing API và ranh giới mở rộng

**[Đã kiểm chứng FE source, 15/10/2026]:** base context `/api`, standard controller root `/v1`; bảng dưới ghi path đầy đủ. Đây không phải xác nhận endpoint đang deploy hoặc đủ policy GĐ11.

| Existing API trong adapter FE | Request/result đang dùng | Giới hạn so với GĐ11 |
|---|---|---|
| GET `/api/v1/plants/{plantId}/work-orders` | Danh sách WO, filters/page theo adapter hiện có | Chưa có list costing/checklist/rate extension đã mapping |
| GET `/api/v1/work-orders/{workOrderId}` | WorkOrderDto, snapshots/components/operations/quantities/status/timestamps | DTO đã đọc chưa có cost baseline/run/allocations/close actor hoặc optimistic version dùng cho extension |
| POST `/api/v1/work-orders/{workOrderId}/release` | Adapter command hiện có + Idempotency-Key | Chưa có bằng chứng server capture cost baseline mới; không tự thêm body cost |
| GET `/api/v1/work-orders/{workOrderId}/variance` | materialLines, outputVariance, wipSummary, timeVariance, costVariance | Material hiện có `actualIssuedQuantity`, không tự đổi nghĩa thành consumed; cost DTO hiện không biểu diễn unknown/source allocations mới |
| POST `/api/v1/work-orders/{workOrderId}/close` | **Không body**, Idempotency-Key; result WorkOrderDto | Không tự gửi runId/version/reason mới vào endpoint cũ trước mapping; server cần chốt đường tương thích không bypass invariant |

Variance cost DTO hiện có standardMaterialCost/standardLaborCost/standardOverheadCost/standardTotalCost và actual tương ứng/totalCostVariance. Adapter chuẩn hóa decimal bắt buộc, không chứng minh giá trị0 là known hoặc endpoint đã dùng baseline GĐ11. Existing output variance là actual−planned; `usageVarianceCost` chưa được suy thành tổng actual−planned mới.

**[Đã kiểm chứng FE]:** hai workspace/rate extension hiển thị unavailable ở API mode, không gọi path đoán hoặc fallback mock. **[Đề xuất BE]:** nếu tái sử dụng API cũ thì cung cấp schema/version/migration tương thích; nếu dùng extension mới thì chốt bảng endpoint §3.4. Không âm thầm thay nghĩa field issued, fulfilled, cost hoặc close.

### 1.3 Luồng và quyền

**[Đã kiểm chứng FE, đề xuất mapping BE]:** quyền là action + scope/assignment còn hiệu lực, không bypass theo tên role. Actor lấy principal server. Cost view = `PERM_WORK_ORDER_VARIANCE_READ` hoặc `PERM_PRODUCTION_COST_CALCULATE`. Rate-manage đơn lẻ được xem rate cần cấu hình, không mặc nhiên mở toàn bộ hồ sơ cost WO.

| Thao tác | Trạng thái trước → sau | Quyền | Validation/tác động |
|---|---|---|---|
| Đọc rate/minimal center/history | Không đổi | Cost view hoặc PERM_COST_RATE_MANAGE | Company/Plant, không đòi cấp full Work Center/calendar/capacity admin |
| Thiết lập rate | Rate version n→n+1 | PERM_COST_RATE_MANAGE | Center active đúng Plant, rates/reason/version, audit; không sửa baseline WO cũ |
| Đọc costing/runs/baseline/allocations | Không đổi | Cost view | Scoped direct ID/history, tối thiểu cost references; mở full nguồn đòi quyền nguồn |
| Đọc close queue/checklist | Không đổi | Cost view hoặc PERM_WORK_ORDER_CLOSE hoặc PERM_WORK_ORDER_READ hoặc PERM_WIP_READ | Người vận hành chỉ nhận trạng thái/quantity/checks, không tiền/rates/full source token |
| RELEASE | PLANNED/đủ điều kiện hiện có→RELEASED | Quyền release hiện có theo mapping BE | Capture baseline một lần cùng release; cost thiếu không mới chặn sản xuất |
| Tính preview | Lệnh không CANCELLED/CLOSED→thêm run | PERM_PRODUCTION_COST_CALCULATE | Current source/WO revision; không đổi quantity/value/WO status |
| Chốt giá và đóng | COMPLETED→CLOSED | PERM_WORK_ORDER_CLOSE **và** Cost view | Checklist đạt, preview đúng nguồn, reason; một atomic commit |
| Đọc effective delivery cost | Không đổi | Cost view **và** authority đọc/quản lý/ghi sổ Delivery | Scoped delivery-line/movements/adjustments, không nhận quyền commercial rộng hơn |
| Đọc stock cost adjustments | Không đổi | Quyền đọc stock tương ứng **và** Cost view | Stock-only không nhận tiền; related source IDs tối thiểu |

Demo Manager có thể tự cấu hình/tính/đóng khi có đủ quyền; chưa tách duyệt hai người. Custom role không tự được grant. Mọi read/command/replay kiểm user/session active, Company sở hữu Plant, resource thuộc Plant, assignment chưa hết hạn/thu hồi; header/query/body/path không được lệch scope. Resource ngoài scope trả404 như không tồn tại, không lộ metadata. Thay quyền trong lúc chờ lock phải có hiệu lực trước commit/replay.

## §2 — Bằng chứng tái hiện

### 2.1 BE/live

**Không có — chỉ bàn giao FE/mock và đề xuất contract, không báo defect BE.**

| Mảnh bằng chứng | Hiện có |
|---|---|
| Request BE thật method/path/header/body | Không có; chưa gọi BE trong phiên321 |
| Response thật HTTP/envelope/nguyên văn | Không có |
| X-Trace-Id response | Không có; không tự tạo trace thay bằng chứng |
| Môi trường/giờ BE/deployed version | Chưa kiểm chứng; không xác nhận môi trường đang chạy bản mới nhất |
| ID record BE thật trong cùng lượt | Không có; UUID ví dụ §3.7 không phải record server |

```text
Mong đợi: Sau khi BE chốt/deploy mapping GĐ11, fixture nguồn đã biết được chốt giá và CLOSED atomic; nguồn thiếu/chưa đối soát bị chặn.
Thực tế: FE/mock đã kiểm các quy tắc; extension API còn unavailable và chưa có bằng chứng live để nghiệm thu BE.
```

### 2.2 Bằng chứng FE đã có, không thay bằng chứng server

- Session320: full regression107files/626tests pass; sau đó24service tests và23UI/navigation tests pass. Full626 trước test UI cuối, không gọi thành full627 mới. Build/TypeScript48pages, lint phần thay đổi và VI/EN parity pass. Phiên321 không chạy lại app tests/build.
- Integration tests chạy command thật trên shared mock stores: fixture scrap/retry planned29000/actual34500; receipt5+4, QC8đạt/1loại, giao8 trước chốt; giữ cost34500 = giá vốn30666.666667 + hàng loại còn kho3833.333333. Có normal/all-scrap/unknown/0/rate history/rollback/current auth/concurrency/hydration/Plant/redaction cases.
- Browser mock ngày15/10/2026: CNC rate6000/3000 lưu/reload; WO id1 mãWO-20261013-0001 Good9/planned10 thiếu baseline/cost/value flow; preview idcost-1 mãCST-0001 và checklist giữ blocker. Plant2 không thấy WO Plant1, desktopVI/tabletEN kiểm được. Đây là record mock, **không** phải UUID/record BE hoặc lỗi BE.
- Chưa chạy full WO mới→chốt có tiền trên browser; luồng đầy đủ nêu trên là integration-test evidence. Không gán cost34500 cho WO browser cũ. Giờ/HTTP trace live không có. Không cần ảnh để BE thực hiện hợp đồng này; số liệu/công thức đã viết đầy đủ.

## §3 — Đầu ra mong đợi — Cần BE triển khai

Toàn bộ §3 là **[Đề xuất BE]**, trừ các dòng ghi rõ fact FE. “Chặn FE?” nói về tích hợp API/nghiệm thu GĐ11, không chặn làm FE/mock giai đoạn sau. Có chức năng tương đương thì đáp ứng bằng mapping, không bắt buộc tạo endpoint trùng.

### 3.1 Tiêu chí kiểm được

| # | Yêu cầu đúng/sai kiểm được | Endpoint/điểm xử lý | Chặn FE? |
|---|---|---|---|
| R01 | List costing lọc search/readiness/date/Plant trước page, count chỉ thuộc scope được phép | C3 | Có—list |
| R02 | Read WO cost trả snapshot/unknown reason và nguồn đúng lệnh, không suy missing thành0 | C4–C6 | Có—detail |
| R03 | Rate0 hợp lệ; negative/exponent/scale>6/vượt giới hạn trả validation, không đổi rate/audit | C2 | Có—rate |
| R04 | Save rate đúng expected version tạo revision/audit có giá cũ-mới hoặc từng snapshot, không sửa WO released | C2/C13 | Có—rate |
| R05 | RELEASE capture requirement/source warehouse/known price/routing/capacity/rate một lần trong cùng transaction | RELEASE/C5 | Có—baseline |
| R06 | Missing baseline/rate/price giữ unknown; thêm giá hiện tại không hồi tố WO cũ hoặc mới chặn production/delivery | C4–C7 | Có—unknown |
| R07 | Planned material/time/labor/overhead dùng đúng công thức §3.5, không lấy span ngày nghỉ hoặc giá bán | C4/C7 | Có—cost |
| R08 | Actual material đối chiếu issue/consumption/returns/lot/movements; required material thiếu chứng cứ trả unknown | C4/C7/C10 | Có—material |
| R09 | Hoàn nhiều lần giữ issued=consumed+returned+unused và value residual; không sửa posted returns | Return/C4/C10 | Có—value |
| R10 | Actual labor/overhead tính theo duration execution × captured rate, retry chỉ một lần, không cộng historical rework thành giờ | C4/C7 | Có—time |
| R11 | Duration thiếu/sai/overlap chưa giải thích trả blocker; không tự nhân employee/machine count để đoán chi phí | C4/C6/C10 | Có—time |
| R12 | Partial receipt dùng final-operation Good, không sum Good qua công đoạn hoặc nhận bù QC reject | Receipt/C4/C10 | Có—output |
| R13 | Receipt/QC allocations lũy kế theo thứ tự ổn định và cộng đúng total, cả multi-round QC và NON_TRACKED | C4/C9/C10 | Có—allocation |
| R14 | Production scrap hấp thụ vào Good; QC rejected giữ quantity/value, không tự write-off/available | C4/C10 | Có—policy |
| R15 | Good0 trả unitCost null, loss=known actual total và reason đóng, không sinh quantity/receipt giả | C4/C10 | Có—loss |
| R16 | Preview lưu run/snapshot/source revision nhưng không thay stock/value/CLOSED | C7–C9 | Có—preview |
| R17 | Checklist11 mã §3.6 có trạng thái/blocker/resource tối thiểu, không cho client bỏ qua điều kiện | C6/C10 | Có—close |
| R18 | Mọi thay đổi liên quan nguồn làm stale preview; invoice/payment độc lập không làm stale chỉ vì có activity | C6/C7/C10 | Có—revision |
| R19 | Pool pha trộn/transfer/downstream thiếu value replay chặn finalize với lý do; không gán tùy ý cost cho lot còn lại | C4/C6/C10 | Có—valuation |
| R20 | Giao trước chốt được cost adjustment đúng delivery-line/source; original ISSUE/quantity/price giữ nguyên | C10–C12 | Có—COGS |
| R21 | Chốt ghi final snapshot/allocations/adjustments/WO CLOSED/audit/replay trong một transaction | C10 | Có—atomic |
| R22 | Hai close cạnh tranh chỉ một commit; stale/version/source sai không ghi dở dữ liệu | C10 | Có—concurrency |
| R23 | Retry cùng actor/scope/key/intent trả cùng identity; đổi intent cùng key trả conflict; quyền hiện tại kiểm trước replay | C2/C7/C10 | Có—retry |
| R24 | Mọi production/issue/return/receipt/QC path kể cả API cũ chặn đổi nguồn WO đã CLOSED | Mutation cũ/C10 | Có—integrity |
| R25 | Hàng đạt còn kho sau close vẫn giao được theo cost đã chốt; customer payment không là điều kiện close | Delivery/C10/C11 | Có—integration |
| R26 | Xem lại final run dùng snapshot tại close, không tính lại từ tồn/rate hôm nay; reload giữ actor/time/reason | C8–C10 | Có—history |
| R27 | Operational checklist/stock-only payload không chứa money/rate/cost history/source token nhúng tiền; lọc scope trước serialization/cache | C3/C6/stock | Có—security |
| R28 | Rate-only authority nhận minimal center/rate/history, không yêu cầu full master admin hoặc nhận WO financials ngoài quyền | C1/C2/C13 | Có—security |
| R29 | Direct ID/history/source/replay ngoài Plant/Company/assignment hiện tại bị từ chối không lộ dữ liệu | Tất cả | Có—security |
| R30 | Migrate legacy giữ ID/history/unknown, không reset hoặc lấy current prices để tạo baseline giả | Migration | Có—migration |
| R31 | CLOSED bị loại future supply; SO remaining delivery/receivable và saved MRP runs không đổi vì chốt cost | MRP/SO/C10 | Có—consistency |
| R32 | Giữ existing variance/close tương thích hoặc công bố version/migration và guard thống nhất trước khi FE chuyển API | API cũ + C1–C13 | Có—rollout |

### 3.2 Chuẩn dữ liệu chung

- UUID dạng string cho mọi entity/source/event/operation/line ID; mã chứng từ là display code, không làm khóa và không đổi thành số để khớp mock. Mock id1/cost-1 không phải wire ID.
- `companyId`, `plantId` bắt buộc với read theo Plant; path dùng plantId, company xác định từ Plant và current context. Query companyId nếu có phải khớp. `X-Plant-Id` đề xuất bắt buộc/cross-check trên extension; BE không dùng header thay authorization.
- Quantity string decimal không exponent, scale≤6; NUMERIC(19,6) cần mapping range chính thức. Input operation/issue/return dương; Good/Scrap/Rework và amount cho0 theo ngữ nghĩa, không nhận NaN/Infinity.
- Cost/value VND string scale6, HALF_UP; giới hạn đề xuất abs<10^32 **sau rounding**. Rate labor/overhead string0..999999999, scale≤6. Đơn giá bình quân nguồn/planned minutes có thể giữ precision12 như mock; BE chốt precision ở Q3/Q4. Không dùng JS float hoặc làm tròn đơn giá6 rồi nhân ra tổng thay source amount.
- `expectedVersion`/`workOrderVersion`/`rateVersion`/`runVersion`: integer≥0, server-owned. `expectedSourceVersion`: opaque string không có thông tin tiền có thể decode; không dùng internal JSON mock làm wire. Cùng ledger nhưng mỗi snapshot phải đọc nhất quán.
- Timestamp ISO8601 UTC có offset/Z, server time cho audit; fromDate/toDate ISO date theo Plant timezone đã cấu hình, inclusive from đầu ngày đến hết toDate, không fallback timezone ngầm. Giới hạn search200ký tự, size1..100, page≥0; sort allowlist, stable tie-break bằng immutable sequence/ID.
- Financial object bị **omit** nếu không có quyền. Trong object được phép xem, field tiền required nhưng chưa biết giữ `null` với `unknownReasons`; known0 trả `"0.000000"`. Do envelope hiện có NON_NULL, BE cần chốt serializer field nullable bên trong DTO để FE phân biệt unknown với redacted; không tự áp dụng NON_NULL làm mất nghĩa này.
- Array required trả `[]` khi biết không có; không dùng `[]` để giả nguồn cost đã đủ. Cost completeness/checklist authoritative, không suy từ chiều dài array phía FE.

### 3.3 Field/DTO đề xuất

Các field dưới đây bắt buộc trong projection tương ứng nếu không ghi optional; “Nullable” là business unknown, khác omit vì thiếu quyền. Không trả full tài khoản/master/production document để chỉ vẽ một source link.

| Field | Kiểu | Nullable | Nguồn giá trị/công thức | Dùng ở đâu |
|---|---|---|---|---|
| companyId, plantId | UUID | Không | Resource scope server | Mọi header/list/command context |
| workOrderId, workOrderNo, status | UUID, string, WO enum | Không | WO canonical | List/detail/checklist |
| productItemId/code/name, outputUom | UUID, strings | Không | WO snapshot | List/detail |
| workOrderVersion | integer≥0 | Không | Revision source dùng cho command | Calculate/close |
| readiness | INCOMPLETE/BLOCKED/READY/STALE/CLOSED | Không | Thứ tự §3.6 | List/badge |
| createdAt, timeZone | UTC instant, IANA string | Không | WO/Plant | Date filter/audit |
| moneyVisible, allowedActions | boolean, string[] | Không | Current authority + state | Projection/CTA; không thay auth server |
| plannedQuantity, finalGoodQuantity, productionScrapQuantity, historicalReworkQuantity, shortfallQuantity | decimal string6 | Không với nguồn mới | WO/final operation; shortfall=max(0,planned−Good) | Overview/checklist |
| approvedReceiptQuantity, qcAcceptedQuantity, qcRejectedQuantity, qcPendingQuantity | decimal string6 | Có nếu legacy chưa đối soát | Receipt/QC ledger; pending=approved−accepted−rejected | Overview |
| unknownReasons | string[] | Không | MISSING_BASELINE/MISSING_RATE/MISSING_PRICE/MISSING_CONSUMPTION/INVALID_TIME/LEGACY_SOURCE/UNRECONCILED_VALUE | Cảnh báo, không hiển thị raw code cho người dùng |
| sourceVersion | opaque string | Không khi được Cost view; omit với operational | Revision toàn nguồn liên quan | Freshness/calculate/close |
| planned.material/labor/overhead/total | cost string6 | Có từng field | Baseline và §3.5 | Cost totals |
| actual.material/labor/overhead/total | cost string6 | Có từng field | Consumed source/time/rates | Cost totals |
| variance, variancePercent | signed decimal string6 | Có | actual−planned; percent chỉ khi planned>0 | Chênh lệch |
| unitCost, loss | cost string6 | Có | Good>0: total/Good và lossnull; Good0: unitnull/loss=total nếu known | Overview/close |
| remainingValue, issuedValue | cost string6 | Có | Attributed snapshot stock/COGS; không cộng giá bán | Allocation/overview |
| baselineId, capturedAt, capturedBy | UUID, instant, actor reference | Baseline object null nếu chưa có | RELEASE audit | Cơ sở chi phí |
| baseline.outputWarehouseId, sourceBoundary | UUID, opaque ledger boundary | Không trong baseline | Kho RELEASE và ledger cut | Valuation chứng cứ nguồn |
| baseline.materials[].componentLineId/itemId/warehouseId/uom/plannedQuantity | UUID/UUID/UUID/string/decimal6 | Không trong dòng hợp lệ | Immutable requirement/source kho | Vật tư kế hoạch |
| baseline.materials[].unitCost | decimal string≤12 | Có | Known inventory average tại RELEASE | Giá kế hoạch |
| baseline.operations[].operationId/workCenterId/name | UUID/UUID/string | Không trong dòng | Routing snapshot | Công đoạn |
| baseline.operations[].capacityUnits/plannedMinutes/rateVersion/laborRate/overheadRate | decimal, decimal≤12, integer, cost6, cost6 | Có từng field khi chưa biết | Cơ sở RELEASE | Planned/actual operating cost |
| rates[].workCenterId/code/name/status | UUID/strings/ACTIVE-INACTIVE | Không | Minimal reference trong Plant | Bảng đơn giá |
| rates[].rateVersion/laborRate/overheadRate | integer/cost6/cost6 | Rate object null khi chưa nhập | Rate revision hiện hành | Đơn giá |
| rateHistory[].version/laborRate/overheadRate/actor/at/reason | integer/cost6/cost6/ref/instant/string | Không cho event mới | Immutable giá từng lần save | Lịch sử đơn giá |
| materials[].issueId/issueLineId/movementId/itemId/warehouseId/lotId/uom | UUID fields và string | lotId null nếu NON_TRACKED | Canonical posting source | Material detail |
| materials[].issuedQuantity/consumedQuantity/returnedQuantity/unusedQuantity | decimal6 | consumed/unused có thể null nếu legacy | Ledger quantities §3.5 | Đối soát |
| materials[].issuedValue/consumedValue/returnedValue/unusedValue | cost6 | Có | Source posting values/residual | Vật tư thực tế |
| materials[].returns[].returnId/movementId/quantity/value | UUID/UUID/decimal6/cost6 | value có thể null | Return posted immutable | Truy xuất hoàn dư |
| operations[].plannedMinutes/actualMinutes, plannedLabor/plannedOverhead/actualLabor/actualOverhead | decimal minutes≤12 và cost6 | Có | Baseline + executions | Time/cost detail |
| operations[].executions[].executionId/sourceExecutionId/startedAt/endedAt/durationMinutes | UUID/UUID/instant/instant/decimal6 | sourceExecutionId null cho fresh | Recorded execution; retry có source | Thời gian/retry |
| allocations[].receiptId/warehouseId/quantity/amount | UUID/UUID/decimal6/cost6 | amount null nếu chưa đối soát | Cumulative receipt allocation | Phân bổ |
| allocations[].parts[].quality/lotId/quantity/amount/remainingQuantity/remainingValue | enum/UUID/decimal6/cost6/decimal6/cost6 | lotId null cho NON_TRACKED; value null nếu unknown | QC child hoặc receipt quality bucket | Lô/chất lượng |
| allocations[].parts[].deliveries[].deliveryId/lineId/movementId/quantity/amount | UUID/UUID/UUID/decimal6/cost6 | amount null trước attribution | Posted delivery-line nguồn | Giá vốn đã xuất |
| checks[].code/ok/reasonCodes/source | enum/bool/string[]/resource ref hoặc null | source nullable | §3.6; source={type,id,code}, không URL máy FE | Checklist/link xử lý |
| runId/runNo/runVersion/state/actor/at | UUID/string/integer/PREVIEW-FINALIZED/ref/instant | Không | Cost run canonical | Lịch sử |
| closedAt/closedBy/closeReason/finalRunId | instant/ref/string/UUID | Closed object null trước chốt | Atomic close audit | Hồ sơ đóng |
| adjustmentId/runId/receiptId/lotId/deliveryId/deliveryLineId/adjustedMovementId | UUID fields | Các source không áp dụng nullable, runId required | Value ledger source | History/genealogy |
| adjustment.type/itemId/warehouseId/workOrderId/at/actor/reason | COST_ADJUSTMENT/UUID/UUID/UUID/instant/ref/string | Không; reason có thể rỗng nếu audit lý do ở final run được liên kết | Canonical value event và close audit | Biến động có nguồn/thời điểm/người thực hiện |
| adjustment.quantity/stockValueDelta/allocatedAmount/stockValueBefore/stockValueAfter | decimal6/cost6/cost6/cost6/cost6 | before có thể null do unknown; after phải known khi đã chốt | quantity0; stock delta khác COGS allocation | Biến động giá trị |
| deliveryCost.lines[].lineId/quantity/amount/adjustmentIds/known | UUID/decimal6/cost6/UUID[]/bool | amount null khi chưa biết | Original cost known hoặc source adjustment; không cộng hai lần | Effective cost |

`actor` đề xuất object tối thiểu `{id, displayName}`; service không nhận actor/time/totals/allocations từ client. `runNo` là display code. `allowedActions` thuộc EDIT_RATE/CALCULATE/FINALIZE_CLOSE theo projection. `source.type` thuộc WORK_ORDER/WORK_CENTER/MATERIAL_ISSUE/MATERIAL_RETURN/PRODUCTION_EXECUTION/PRODUCTION_RECEIPT/PRODUCTION_QC/DELIVERY/STOCK_MOVEMENT, source.id là UUID; nguồn không được phép tham chiếu trả null. Trường child khác ngữ nghĩa phải trả field rõ, không yêu cầu FE parse internal token hoặc cộng source arrays để đoán completeness. Collections chi tiết có thể phân trang theo C4/C9; header totals/checks luôn toàn dataset, không chỉ page đang xem.

### 3.4 Endpoint đề xuất, không phải endpoint đã deploy

Ký hiệu C1–C13 dùng trong §3/R/A. Mọi POST/PUT có Idempotency-Key; context header/query/path khớp. Command success chỉ identity/version, FE refetch read có quyền hiện tại. GET không có side effect hoặc tạo run.

| ID · Method + path đầy đủ đề xuất | Query/body | Success result | Page/search | Quyền/scope | Idempotency |
|---|---|---|---|---|---|
| C1 · GET `/api/v1/plants/{plantId}/production-cost-rates` | search,status,page,size,sort | Page minimal center + rate hoặc null | Có | Rate-manage hoặc Cost view; Company/Plant | Không |
| C2 · PUT `/api/v1/plants/{plantId}/production-cost-rates/{workCenterId}` | expectedVersion,laborRate,overheadRate,reason,currency=VND | workCenterId,rateVersion | Không | Rate-manage; active center cùng Plant | Có |
| C3 · GET `/api/v1/plants/{plantId}/production-costs` | search,readiness,fromDate,toDate,mode=costs/close,page,size,sort | Page scoped WO summary; money tùy authority | Có; modeclose chỉ COMPLETED/CLOSED | Cost view cho costsmode; authority checklist cho closemode | Không |
| C4 · GET `/api/v1/work-orders/{workOrderId}/production-costs` | section=overview/materials/operations/variance/allocation, page,size cho collection | Header + data section; page cho collection | Có với collection; overview không page | Cost view + resource scope | Không |
| C5 · GET `/api/v1/work-orders/{workOrderId}/cost-baseline` | Không | baseline object hoặc null + unknownReasons | Không; component/operation detail qua C4 nếu lớn | Cost view + resource scope | Không |
| C6 · GET `/api/v1/work-orders/{workOrderId}/cost-checklist` | Không | Scoped operational header + checks; financial fields chỉ Cost view | Không | Checklist authority §1.3 | Không |
| C7 · POST `/api/v1/work-orders/{workOrderId}/cost-runs` | expectedVersion,expectedSourceVersion | workOrderId,runId,runVersion,workOrderVersion | Không | Calculate + scope; WO không CANCELLED/CLOSED | Có |
| C8 · GET `/api/v1/work-orders/{workOrderId}/cost-runs` | page,size,sort=at-desc | Page run summary/actor/time/totals permitted | Có | Cost view + scope | Không |
| C9 · GET `/api/v1/work-orders/{workOrderId}/cost-runs/{runId}` | section/page/size như C4 | Immutable run header + snapshot section | Collection có page | Cost view; run thuộc đúng WO/Plant | Không |
| C10 · POST `/api/v1/work-orders/{workOrderId}/finalize-cost-and-close` | expectedVersion,expectedSourceVersion,runId,expectedRunVersion,reason | workOrderId,runId,runVersion,workOrderVersion | Không | Close và Cost view + scope/checklist | Có |
| C11 · GET `/api/v1/deliveries/{deliveryId}/production-cost` | page,size khi nhiều dòng | Header + Page effective cost lines/known/adjustment references | Có | Cost view và Delivery authority | Không |
| C12 · GET `/api/v1/work-orders/{workOrderId}/cost-runs/{runId}/adjustments` | page,size,sort | Page value-only entries và nguồn | Có | Cost view + scope; mở full stock record đòi stock permission | Không |
| C13 · GET `/api/v1/plants/{plantId}/production-cost-rates/{workCenterId}/history` | page,size,sort=version-desc | Page rate revisions/actor/time/reason | Có | Rate-manage hoặc Cost view | Không |

C3 default modecosts, section C4/C9 defaultoverview, page0/size20. Stable sort default WOcreatedAt-desc+ID, material theo component/issue sequence, operation theo routing sequence, allocation theo posting sequence, run theo created sequence. Đề xuất read500row không page bị giới hạn size100, không trả toàn bộ ledger vào header. Company/Plant không được lấy từ nội dung tùy ý client để né scope.

### 3.5 Công thức và bảo toàn giá trị

**[Giả định policy demo; đề xuất BE]:** đây là cost quy đổi theo thời gian/đơn giá, chưa payroll/GL. Ký hiệu `C6(x)=HALF_UP(x,6)`, phép tính trung gian decimal precision đủ lớn, không round quantity lên để cân tiền.

1. Planned material-line = C6(requiredQuantity × source average unit cost tại RELEASE); total material = tổng các line đã làm tròn. Không cộng quantity của các UOM khác nhau.
2. Planned active minutes từng operation = (setupMinutes + runMinutesPerUnit × plannedQuantity) / captured capacityUnits. Không dùng khoảng thời gian lịch gồm nghỉ/chờ. Planned labor/overhead từng operation = C6(plannedMinutes × captured hourlyRate /60), sau đó tổng các operation.
3. Actual minutes = tổng duration execution hợp lệ trong từng operation; retry là record mới có sourceExecutionId, tính đúng duration của record đó một lần. Historical Rework là số lượng lịch sử, không phải giờ hoặc output bổ sung. Actual labor/overhead = tổng C6(actualMinutesOperation × captured rate/60).
4. Issue-line: Qi=Qc+Qr+Qu; original issued value Vi = consumed value Vc + posted returned value Vr + unused value Vu. Khi unused0 và nguồn known: Vc=Vi−Vr. Khi còn unused, preview Vc=C6(Vi×Qc/Qi), Vu=Vi−Vc−Vr; chưa được close. Nguồn thiếu/mismatch/giá trị âm/required material không có evidence làm actual material unknown; không0.
5. New return: tính target returned value lũy kế C6(Vi×cumulativeReturned/Qi); khi lần hoàn mới làm Qu=0, target=Vi−C6(Vi×currentConsumed/Qi). New return value=target−sum(values các return đã post). Không ghi đè return cũ; residual âm hoặc không cân thì block/reconcile, không lấy absolute value.
6. Planned/actual total = material + labor + overhead; nếu thành phần required unknown thì totalnull. Variance=actual−planned, percent=C6(variance×100/planned) chỉ khi planned>0, còn lại null; âm không mất dấu dù −0.25. Không phân rã usage/price variance chưa được duyệt bằng cách đổi nghĩa field cũ.
7. G=Good của final operation, R=sum approved receipts, A=QC accepted, J=QC rejected, H=R−A−J. Close cần R=G và H=0. Production scrap không nhập FG; chi phí của scrap hấp thụ vào G. QC rejected không giảm R hoặc mở quota receipt lần nữa, vẫn là stock/value của output đã kiểm.
8. G>0: unitCost=C6(actualTotal/G). **Phân bổ từ total authoritative**, không lấy unitCost6×quantity để quyết tổng. Receipt theo immutable posting order: delta receipt_i=C6(total×cumulativeReceiptQty/G)−sum(previous receipt amounts). Trong receipt, tiếp tục cùng công thức theo cumulative QC part quantity; delivery trong part tiếp tục cumulative theo posted delivery-line. Phần cuối nhận residual. Không chuyển cost của hàng loại sang hàng đạt đã bán.
9. G=0: unitCostnull; khi actual known, loss=actualTotal, received0, remainingValue0, issuedValue0 của FG; vẫn cần mọi checklist/shortfall reason/current authority. Không tạo receipt/lot/value stock để cân loss. WIP financial settlement/loss record do BE xác nhận Q24, không tuyên bố mock đã ghi GL.
10. At close: actualTotal = value còn kho thuộc nguồn + COGS đã xuất thuộc nguồn + loss. Sau close, run snapshot là **tại thời điểm đóng**, không đổi khi giao tiếp; current stock/delivery cost có thể đổi theo posting thật riêng.

**Ranh giới định giá bắt buộc:** mock dùng average pool Item+Warehouse, chỉ finalize khi output pool physical0 và value0 known tại RELEASE, sau đó ledger chỉ gồm receipt/QC/direct delivery đúng WO và zero-quantity reserve/release không đổi value. Có source lô không đồng nghĩa đã có cost layer từng lô. Trước chốt, output value thường null; nếu có physical>0 với pool known value không giải thích được, hoặc có movement ngoài graph, mock block.

**[Đề xuất BE]:** hỗ trợ ít nhất fixture isolated-source đầy đủ; nguồn mixed/unknown/transfer/downstream production chưa có replay authoritative trả VALUATION_UNRECONCILED. Nếu BE có cost-layer/value-flow replay tổng quát, trả thuật toán/layers/versions/mapping rồi nghiệm thu riêng, không reset opening, không áp toàn bộ cost vào lot còn lại, không cộng adjustment lần nữa vào issue đã biết cost.

### 3.6 Checklist, atomic close và ảnh hưởng dữ liệu

| Mã | Điều kiện đạt | Nguồn xử lý/điểm lưu ý |
|---|---|---|
| STATE | WO COMPLETED, chưa CLOSED/CANCELLED | COMPLETE chỉ kết thúc thực thi |
| OPERATIONS | Required operations completed; input=processed; không pending rework; transfer Good upstream hết; final Good khớp WO | Mock mới yêu cầu ít nhất1operation; no-routing legacy hỏi Q27 |
| ISSUES | Không supplemental issue PENDING_APPROVAL | Duyệt/từ chối trước; không tự approve |
| MATERIAL | Consumed/returned/unused known, unused0, quantity/value cân | Không tự hoàn dư |
| RESERVATIONS | Không còn active quantity chưa dùng của WO | Không đụng SO/WO khác |
| RECEIPTS | Approved=final Good=WO approved; không DRAFT/PENDING claim; không legacy source thiếu evidence | Không nhận bù scrap/rejected |
| QC | QCpending0, accepted+rejected=approved | Rejected có thể còn kho |
| BASELINE | Đủ RELEASE baseline/component/operation/captured rate và planned cost | Không dùng current rate để chữa lệnh cũ |
| COST | Material/time cost known, nguồn cân, không legacy issue ambiguous | Bao gồm required material evidence |
| VALUATION | Attributed receipt/QC/stock/delivery values cân hoặc Good0/received0 với loss known | Mixed graph thiếu bằng chứng block |
| PREVIEW | Run đúng WO/scope/current source, persisted evaluation không thay đổi; chưa finalized | Nguồn đổi phải tính lại |

Readiness: final run đã chốt→CLOSED; có preview cũ không khớp nguồn→STALE; đủ11checks→READY; planned hoặc actual totalunknown→INCOMPLETE; trường hợp còn lại→BLOCKED. Lệnh chưa tính có PREVIEW=false, không READY dù đủ các điều kiện khác. Operational reader biết blocker nghiệp vụ nhưng không được biết số tiền hoặc toàn bộ source token.

C10 trong cùng transaction/lock hoặc isolation tương đương: kiểm current authority/scope → xử lý replay hợp lệ → đọc current WO+all dependent revisions → kiểm state/version/source/run/checks/reason → tính và đối soát server → ghi final snapshot/allocations/adjustments/close audit/WO CLOSED+revision/replay result → commit. Ngay cả callback lỗi sau ghi stock nhưng trước audit cũng rollback toàn bộ. Client timeout có thể đã commit: retry **cùng key và intent**, không tạo key mới tự động.

Reason trimmed5..2000 ký tự cho rate/close; shortfall COMPLETE reason giữ riêng. `expectedRunVersion` là **đề xuất wire**, mock chưa có version run riêng mà đối chiếu full evaluation. Một run đã FINALIZED không sửa, không ghi financial run final độc lập trong khi WO còn mutable.

| Event | On Hand | Reserved/Available | HOLD/REJECTED | WIP/value | Chứng từ liên quan |
|---|---|---|---|---|---|
| Save rate/RELEASE baseline | Không thêm tác động lượng | Giữ tác động RELEASE hiện có | Không đổi | Chỉ snapshot giá, không mới post chi phí | Rate/WO/baseline audit |
| Calculate preview | Không đổi | Không đổi | Không đổi | Chưa đổi giá trị | PREVIEW run |
| Return material | +returned đúng source | Theo posting tồn hiện có | Chỉ source đủ eligibility | Hoàn giá trị nguồn/residual; giảm unused | Return và RECEIVE cũ theo GĐ8 |
| Receipt/QC trước chốt | Theo GĐ9, không nhập/tiêu hao lần hai | Chỉ QC accepted eligible | Rejected vẫn tồn | FG value có thể unknown | Receipt/QC immutable |
| Chốt phần FG còn kho | **0** | Không đổi quantity/reserve | Giữ nguyên quality/lot | stockValueDelta=remaining allocated amount cho isolated uncosted pool; known-before reconciliation phải rõ | COST_ADJUSTMENT + run/source |
| Chốt phần đã giao | **0** | Không đổi | Không đổi | stockValueDelta0, allocatedAmount=COGS cần ghi nguồn | Adjustment link original delivery ISSUE |
| Close all-scrap | **0** | Phải hết WO reserve trước | Không sinh FG | Loss record authoritative, không GL giả | Final run/CLOSE audit |
| Delivery sau close | ISSUE âm theo GĐ10 | Own entitlement/reserve checks như cũ | Không dùng rejected | Giá vốn từ pool known; final run lịch sử giữ nguyên | Posted delivery/ISSUE mới thật |

Ví dụ stockDelta0 nhưng allocatedAmount>0 là điều chỉnh giá vốn hàng đã xuất, không phải nhập hàng0 lượng. `stockValueBefore=null` không đồng nghĩa0: chỉ chuyển sang known khi evidence graph đã được kiểm; flag `known=true` từ client không được tin. Mọi legacy endpoint chạm nguồn CLOSED phải cùng guard; existing close không được là đường né C10 đối với WO thuộc policy mới.

### 3.7 Fixture và ví dụ contract đề xuất

**Tất cả nội dung mục này là ví dụ đề xuất, không phải request/response đã chạy BE.** BE tạo dataset isolated mới và trả actual IDs khi nghiệm thu; không dùng WO browser1 làm fixture chi phí34500. UUID có chung prefix `11000000-0000-4000-8000-` và suffix12ký tự trong bảng dưới; cách viết rút gọn chỉ phục vụ bảng, JSON dùng UUID đầy đủ.

| Alias fixture F1 | UUID suffix | Mô tả |
|---|---|---|
| COMPANY | 000000000001 | Company demo mới/scoped |
| PLANT | 000000000002 | Plant1, Asia/Ho_Chi_Minh |
| WO | 000000000003 | WO-COST-F1, planned10PCS/output FG |
| CENTER | 000000000004 | Work Center capacity1 |
| RM | 000000000005 | Vật tư đơn vị KG, NON_TRACKED |
| FG | 000000000006 | Thành phẩm PCS, LOT tracked |
| RAW_WH | 000000000007 | Opening RM100KG/value100000VND known |
| OUTPUT_WH | 000000000008 | Opening FG physical0/value0 known, không nguồn khác |
| BASELINE | 000000000009 | Chụp tại RELEASE |
| RUN | 000000000010 | Preview sau delivery, trước close |
| PR1 / PR2 | 000000000011 / 000000000012 | Approved5PCS /4PCS theo posting sequence |
| QC_A / QC_B / QC_R / QC_C | 000000000021 / 000000000022 / 000000000023 / 000000000024 | PR1accepted3, accepted1, rejected1; PR2accepted4 |
| DN1 / DN2 | 000000000031 / 000000000032 | Posted3PCS rồi5PCS |
| DL1 / DL2 / DL3 | 000000000041 / 000000000042 / 000000000043 | Delivery lines3,1,4PCS từ QC_A/B/C |
| ISSUE1 / ISSUE2 / RETURN1 | 000000000051 / 000000000052 / 000000000053 | Normal20KG, supplemental2KG được duyệt, hoàn1KG từ ISSUE2 |
| EX1 / EX2 | 000000000061 / 000000000062 | Fresh60phút Good8/Scrap1/Rework1; retry30phút Good1 |

FE test vật tư là KG; không đổi nó thành PCS của thành phẩm. Cùng fixture: material required20KG, giá1000VND/KG; routing setup0/run6phút×10/capacity1=60phút; rates6000/3000VND/giờ. RELEASE chụp rates trước execution. Consume ISSUE1=20KG, ISSUE2=1KG, hoàn1KG/value1000, unused0; totalactual21KG/21000. EX1/EX2 liền nhau không overlap; retry nguồn EX1, historical rework1 nhưng pending0. Shortfall1 có reason ở COMPLETE.

| Bước F1 | Số lượng/tiền kiểm được |
|---|---|
| Planned | Material20000 +labor6000 +overhead3000 =29000VND |
| Issue/consume/return | 22KG/value22000 =21KG/value21000 +1KG/value1000 +unused0 |
| Sau hoàn | RM100−22+1=79KG; source value100000−22000+1000=79000VND |
| Actual | 90phút→labor9000/OH4500; total34500; variance+5500; percent18.965517 |
| Output | Good9PCS, scrap1, final receipts5+4; QC8đạt/1loại/pending0 |
| Phân bổ receipt | PR1=19166.666667; PR2=15333.333333; tổng34500 |
| QC child allocation | QC_A=11500.000000; QC_B=3833.333334; QC_R=3833.333333; QC_C=15333.333333 |
| Giao trước close | DN1=11500; DN2=19166.666667; COGS30666.666667 |
| Còn kho khi close | QC_R1PCS/value3833.333333; quantity không đổi lúc close |
| Bảo toàn | COGS30666.666667 +stock3833.333333 +loss0 =34500.000000 |

SO có thể ordered10/price100000VND, ship8→invoice800000, nhưng đây là ledger thương mại độc lập theo GĐ10. Payment có thể chưa thu hoặc đã thu; close không thay invoice800000 hoặc quantity còn giao2. Không lấy price100000 làm giá vốn.

F2 độc lập: WO planned10/Good10, consume20KG, duration60phút, không scrap/retry; planned=actual29000, variance0, unit2900, nhập/QC10 rồi close. F3 độc lập: Good0/Scrap10, consume21KG/duration90phút, total34500→loss34500/unitnull/không receipt. F4 độc lập: issue3KG/value1VND, consumed1KG, hoàn1KG hai lần→return values0.333333 rồi0.333334, consumedvalue0.333333, tổng1. Không trộn ID/stock của F2–F4 vào F1.

**Ví dụ đề xuất E1 — C2 save rate**, HTTP PUT tới path có PLANT/CENTER bảng F1; `Idempotency-Key: <UUID mới của thao tác>`, `X-Plant-Id: <PLANT>`. Authorization dùng session hiện có, không gửi token trong tài liệu.

```json
{
  "expectedVersion": 0,
  "currency": "VND",
  "laborRate": "6000.000000",
  "overheadRate": "3000.000000",
  "reason": "Đơn giá vận hành cho fixture F1"
}
```

**E2 — success C2 đề xuất, HTTP200:**

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "workCenterId": "11000000-0000-4000-8000-000000000004",
    "rateVersion": 1
  }
}
```

**E3 — C7 calculate đề xuất, HTTP POST; expectedVersion20 là revision minh họa do server trả sau setup/production/receipt/delivery, không phải số tăng đã xác minh:**

```json
{
  "expectedVersion": 20,
  "expectedSourceVersion": "cost-src-demo-001"
}
```

**E4 — success C7 đề xuất, HTTP200; calculate không tăng WO production version:**

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "workOrderId": "11000000-0000-4000-8000-000000000003",
    "runId": "11000000-0000-4000-8000-000000000010",
    "runVersion": 1,
    "workOrderVersion": 20
  }
}
```

**E5 — C4 overview đề xuất khi đã có preview đúng nguồn, trước close; các value là dự tính, chưa post vào stock:**

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "workOrderId": "11000000-0000-4000-8000-000000000003",
    "workOrderNo": "WO-COST-F1",
    "companyId": "11000000-0000-4000-8000-000000000001",
    "plantId": "11000000-0000-4000-8000-000000000002",
    "productItemId": "11000000-0000-4000-8000-000000000006",
    "productItemCode": "FG-COST-F1",
    "productItemName": "Thành phẩm kiểm giá F1",
    "outputUom": "PCS",
    "status": "COMPLETED",
    "workOrderVersion": 20,
    "createdAt": "2026-10-15T00:00:00Z",
    "timeZone": "Asia/Ho_Chi_Minh",
    "readiness": "READY",
    "moneyVisible": true,
    "sourceVersion": "cost-src-demo-001",
    "allowedActions": ["CALCULATE", "FINALIZE_CLOSE"],
    "section": "overview",
    "data": {
      "plannedQuantity": "10.000000",
      "finalGoodQuantity": "9.000000",
      "productionScrapQuantity": "1.000000",
      "historicalReworkQuantity": "1.000000",
      "shortfallQuantity": "1.000000",
      "approvedReceiptQuantity": "9.000000",
      "qcAcceptedQuantity": "8.000000",
      "qcRejectedQuantity": "1.000000",
      "qcPendingQuantity": "0.000000",
      "currency": "VND",
      "planned": {"material": "20000.000000", "labor": "6000.000000", "overhead": "3000.000000", "total": "29000.000000"},
      "actual": {"material": "21000.000000", "labor": "9000.000000", "overhead": "4500.000000", "total": "34500.000000"},
      "variance": "5500.000000",
      "variancePercent": "18.965517",
      "unitCost": "3833.333333",
      "loss": null,
      "remainingValue": "3833.333333",
      "issuedValue": "30666.666667",
      "unknownReasons": []
    },
    "closed": null
  }
}
```

**E6 — C6 operational reader đề xuất, không Cost view; toàn bộ money/sourceVersion/runs/baseline bị omit, không null giả:**

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "workOrderId": "11000000-0000-4000-8000-000000000003",
    "workOrderNo": "WO-COST-F1",
    "status": "COMPLETED",
    "workOrderVersion": 20,
    "readiness": "READY",
    "moneyVisible": false,
    "allowedActions": [],
    "checks": [
      {"code": "STATE", "ok": true, "reasonCodes": [], "source": null},
      {"code": "OPERATIONS", "ok": true, "reasonCodes": [], "source": null},
      {"code": "ISSUES", "ok": true, "reasonCodes": [], "source": null},
      {"code": "MATERIAL", "ok": true, "reasonCodes": [], "source": null},
      {"code": "RESERVATIONS", "ok": true, "reasonCodes": [], "source": null},
      {"code": "RECEIPTS", "ok": true, "reasonCodes": [], "source": null},
      {"code": "QC", "ok": true, "reasonCodes": [], "source": null},
      {"code": "BASELINE", "ok": true, "reasonCodes": [], "source": null},
      {"code": "COST", "ok": true, "reasonCodes": [], "source": null},
      {"code": "VALUATION", "ok": true, "reasonCodes": [], "source": null},
      {"code": "PREVIEW", "ok": true, "reasonCodes": [], "source": null}
    ],
    "closed": null
  }
}
```

**E7 — C10 finalize+close đề xuất, HTTP POST, key khác thao tác calculate:**

```json
{
  "expectedVersion": 20,
  "expectedSourceVersion": "cost-src-demo-001",
  "runId": "11000000-0000-4000-8000-000000000010",
  "expectedRunVersion": 1,
  "reason": "Đã đối soát chi phí và sản lượng thiếu một thành phẩm"
}
```

**E8 — success C10 đề xuất, HTTP200; C9 sau đó trả FINALIZED/closed audit, replay cùng key trả cùng identity:**

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "workOrderId": "11000000-0000-4000-8000-000000000003",
    "runId": "11000000-0000-4000-8000-000000000010",
    "runVersion": 2,
    "workOrderVersion": 21
  }
}
```

**E9 — một stock adjustment entry trong C12 đề xuất; allocation QC_R, before unknown có evidence, after known; ID mới suffix71:**

```json
{
  "adjustmentId": "11000000-0000-4000-8000-000000000071",
  "type": "COST_ADJUSTMENT",
  "companyId": "11000000-0000-4000-8000-000000000001",
  "plantId": "11000000-0000-4000-8000-000000000002",
  "workOrderId": "11000000-0000-4000-8000-000000000003",
  "itemId": "11000000-0000-4000-8000-000000000006",
  "warehouseId": "11000000-0000-4000-8000-000000000008",
  "at": "2026-10-15T06:00:00Z",
  "actor": {"id": "11000000-0000-4000-8000-000000000081", "displayName": "Quản lý kiểm giá F1"},
  "reason": "Đã đối soát chi phí và sản lượng thiếu một thành phẩm",
  "runId": "11000000-0000-4000-8000-000000000010",
  "receiptId": "11000000-0000-4000-8000-000000000011",
  "lotId": "11000000-0000-4000-8000-000000000023",
  "deliveryId": null,
  "deliveryLineId": null,
  "adjustedMovementId": null,
  "quantity": "0.000000",
  "stockValueDelta": "3833.333333",
  "allocatedAmount": "3833.333333",
  "stockValueBefore": null,
  "stockValueAfter": "3833.333333"
}
```

Không bắt số lượng adjustment bằng sốlot: mock có thể lưu zero-value entries, BE có thể gộp theo source nếu mapping vẫn truy được và không duplicate. COGS adjustment DN1 có stockValueDelta0/allocatedAmount11500/sourceDL1+originalISSUE; valueBefore/after không tăng vì COGS. Original ISSUE trước chốt vẫn giữ null cost, effective cost đọc adjustment, không rewrite nó.

**E10 — C8 page rỗng đề xuất, HTTP200 (list contract chung):**

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {"content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "first": true, "last": true}
}
```

**E11 — lỗi nguồn stale đề xuất, HTTP409; X-Trace-Id lấy từ response header thật khi chạy, hiện chưa có:**

```json
{
  "code": "COST_SOURCE_STALE",
  "message": "Nguồn đã thay đổi, cần tính lại giá thành.",
  "errors": [{"field": "expectedSourceVersion", "message": "Kết quả tính không còn khớp nguồn hiện tại."}]
}
```

### 3.8 Nghiệm thu live và rollout

**Tất cả A01–A48 dưới đây CHƯA CHẠY trên BE.** Evidence cần ghi cùng lượt: full request/response/HTTP/code, X-Trace-Id response, version môi trường + UTC/Plant time và UUID actual records. Không dùng UUID fixture minh họa thay record thật. Có ca tương tự trong mock không tự đổi trạng thái live thành đạt.

| ID | Đầu vào/record | Thao tác | Kết quả mong đợi | Bằng chứng | Trạng thái |
|---|---|---|---|---|---|
| A01 | F2 mới, all sources known | Preview→close | Planned=actual29000, Good10/unit2900/CLOSED | C7/C10/C9+stock | Chưa chạy |
| A02 | F1 tại RELEASE | Đọc baseline | Giá1000/capacity1/rates6000-3000/minutes60 đúng cut | C5+source ledgers | Chưa chạy |
| A03 | F1 released | Sửa catalog rates | Baseline/planned WO không đổi; lịch sử rate có giá từng lần | C2/C5/C13 | Chưa chạy |
| A04 | WO thiếu rate tại RELEASE | Thêm rate hôm nay | Old baseline vẫn unknown, production không mới bị chặn, close bị chặn | C4/C10 | Chưa chạy |
| A05 | WO mới rate0 có chủ ý | Calculate | Labor/OH known0 khác missing | C1/C5/C7 | Chưa chạy |
| A06 | Legacy WO thiếu baseline | Calculate/close | Không current-price backfill; blocker cụ thể | C4/C6/C10 | Chưa chạy |
| A07 | Required material thiếu issue/consumption evidence | Calculate | Actual materialnull, không0 | C4/C6 | Chưa chạy |
| A08 | F1 issue22/consume21/return1 | Calculate | Materialactual21000, không22000 | C4 materials+source | Chưa chạy |
| A09 | F4 số lẻ | Return1 hai lần | .333333/.333334, consumed .333333, tổng1 | Return responses/ledger | Chưa chạy |
| A10 | Return cũ residual không cân | Finalize | Chặn, không sửa return cũ/giá trị âm | C10+unchanged ledger | Chưa chạy |
| A11 | F1 execution60+retry30 | Calculate | Actual90, labor9000/OH4500, retry một lần | C4 operations | Chưa chạy |
| A12 | Duration sai/missing | Preview/close | INVALID_TIME/blocker; không tính0 | C4/C10 | Chưa chạy |
| A13 | Execution overlap cùng operation | Preview/close | Cần đối soát; không silently double charge | C4/C10 | Chưa chạy |
| A14 | F1 hoàn tất9Good | Nhập5+4 | Approved9, không sum Good qua operations | Receipt/C6 | Chưa chạy |
| A15 | F1 QC nhiều đợt | Chia3/1/1 và4 | Accepted8/rejected1/pending0, allocation như F1 | QC/C4 | Chưa chạy |
| A16 | NON_TRACKED output tương đương | QC nhiều đợt/close | Không lot giả, receipt-quality/value cộng đúng | C4/C10 | Chưa chạy |
| A17 | QC rejected1 | Close | Physical/value rejected giữ nguyên, không receipt thêm1 | Stock/receipt/C10 | Chưa chạy |
| A18 | F3 all-scrap | Close | Loss34500/unitnull/no FG movement | C9/C12/stock | Chưa chạy |
| A19 | F1 current sources | Calculate preview | Không quantity/value/WOstatus effects | Before/after+C7 | Chưa chạy |
| A20 | Có preview, sau đó receipt/QC/return nguồn thay đổi | Close bằng source cũ | Stale409, không commit | C10+ledger | Chưa chạy |
| A21 | Preview đã lưu bị sửa/tổng client giả | Finalize | Server tính/kiểm lại, không tin client totals | C10/C9 | Chưa chạy |
| A22 | F1 đủ mọi điều kiện | Đọc checklist | 11checks đúng, readinessREADY | C6 | Chưa chạy |
| A23 | Supplemental issue chờ duyệt | Close | ISSUES false; không autoapprove | C6/C10 | Chưa chạy |
| A24 | Còn input/transfer/rework | Close | OPERATIONS false; giữ nguồn | C6/C10 | Chưa chạy |
| A25 | Còn unused material | Close | MATERIAL false; không tự hoàn | C6/C10 | Chưa chạy |
| A26 | Còn active WO reserve | Close | RESERVATIONS false; không release SO reserve | C6/C10/reservations | Chưa chạy |
| A27 | Receipt DRAFT/PENDING claim | Close | RECEIPTS false | C6/C10 | Chưa chạy |
| A28 | QC pending>0 | Close | QC false; rejected đã quyết định không coi pending | C6/C10 | Chưa chạy |
| A29 | Mixed opening/other-WO/unknown pool | Finalize | VALUATION_UNRECONCILED; không gán toàn cost vào stock còn | C4/C10 | Chưa chạy |
| A30 | Output đã transfer/downstream consume | Finalize không có replay | Chặn rõ graph, không reset nguồn | C10+unchanged ledger | Chưa chạy |
| A31 | F1 ship8 trước close | Finalize | COGS30666.666667 +stock3833.333333=34500 | C10/C11/C12/stock | Chưa chạy |
| A32 | F2 close rồi giao một phần | Post Delivery | ISSUE thật costknown; final run tại close không đổi | Delivery/C9/C11 | Chưa chạy |
| A33 | F1 có invoice/payment | Preview/close | Không đổi sellingprice/invoice/paid; payment không prerequisite | C7/C10/commercial | Chưa chạy |
| A34 | WO CLOSED | Thử issue/return/execute/input/receipt/QC qua mọi oldpath | Từ chối, không đổi sources | Các response+ledger | Chưa chạy |
| A35 | Hai C10 cùng version, key khác | Chạy cạnh tranh | Một commit, một conflict; adjustment/audit không lặp | Hai traces+DB/ledger | Chưa chạy |
| A36 | C10 đã commit nhưng timeout client | Retry same key | Cùng identity, không lặp effects | Same key/traces | Chưa chạy |
| A37 | Cùng key đổi run/reason/body | Retry | IDEMPOTENCY_CONFLICT; không mutation mới | Response/ledger | Chưa chạy |
| A38 | Quyền/assignment bị thu hồi trước retry | Retry old key | Auth hiện tại từ chối trước replay, không money leak | Auth change+trace | Chưa chạy |
| A39 | Thu hồi quyền trong lúc chờ lock | Gửi C10 | Không commit sau khi assignment mất hiệu lực | Concurrent traces/ledger | Chưa chạy |
| A40 | Lỗi injected sau value write/trước audit | C10/retry | Rollback stock/WO/run/audit/replay toàn bộ; retry không nhân đôi | Fault log/trace/snapshots | Chưa chạy |
| A41 | Operational-reader only | Đọc listclose/checklist/stock | Không money/rate/runs/sourceJSON; quantity/checks đủ | Raw response | Chưa chạy |
| A42 | Close permission only, không Cost view | C10 | 403, không đóng mù hoặc nhận tiền | Raw response+ledger | Chưa chạy |
| A43 | Rate-manage only | C1/C2/C13; thử C4 | Minimal rates dùng được; C4denied, không full master payload | Raw responses | Chưa chạy |
| A44 | Plant2/Company khác | Direct WO/run/delivery/rate IDs Plant1 | 404/no leak, list count scoped | Raw responses/traces | Chưa chạy |
| A45 | 25 scoped WOs/filter/date | List page0/1 và tìm kiếm | Page20+5, totals/filter/timezone đúng | C3 | Chưa chạy |
| A46 | Rate invalid/reason<5/overflow | Save/close | Errorcode/field đúng, rate/value/audit không đổi | C2/C10 | Chưa chạy |
| A47 | Reload/relogin sau close | C9/list/history/MRP/SO | Final audit/snapshot giữ; CLOSED không future supply, SO chưa tự đóng | Read responses | Chưa chạy |
| A48 | Baseline planned0 hoặc variance−.25 | Đọc variance | Percentnull khi denominator0, dấu âm/số lẻ giữ | C4/C9 | Chưa chạy |

Rollout: (1) BE trả §5/mapping OpenAPI/version/serializer/decimal policy; (2) schema/index/idempotency/authorization trước business commands; (3) rate và RELEASE baseline; (4) source consumption/returns/time/receipt/QC/delivery reads; (5) preview/checklist; (6) valuation guard/atomic close/old-path guards; (7) legacy migration và source projection; (8) fixtureIDs/deploy rồi FE typed adapters/live A01–A48. Publish matrix endpoint nào đã có, endpoint nào cần code và dependency GĐ4/8/9/10. Không bắt toàn bộ accounting graph hoàn thành để trả unavailable guard an toàn.

Chỉ chuyển extension mock→API sau mapping DTO/permissions/unknown/source versions/errors/transaction đã chốt; không fallback ngầm, không đổi old adapter body trước contract. FE cache key có user/Company/Plant/permissions/scopes; source change/costpost phải refetch affected stock/WO/cost/delivery/MRP, invoice/payment không bị chỉnh số.

**Trạng thái tích hợp:** chờ BE mapping/triển khai và API acceptance, chưa hoàn tất tích hợp. **Change log v1:** bàn giao sau FE Session320; không có thay đổi app trong phiên321. Không đóng task khi BE chỉ thông báo “xong”; cần FE nghiệm thu trên actual record/request cùng môi trường.

## §4 — Hợp đồng lỗi mong đợi

**[Đề xuất BE, chưa xác nhận/deploy]:** BE trả code ổn định, field errors theo DTO; FE không rẽ nhánh chỉ theo HTTP. Quyền lỗi không kèm financial details/token nhúng tiền.

| Tình huống | HTTP | Code đề xuất | FE hiển thị/field | Giữ input | Retry |
|---|---|---|---|---|---|
| Field/UUID/date/page/decimal/rate/reason sai | 400 | VALIDATION_ERROR | Field cụ thể laborRate/overheadRate/reason/expectedVersion | Có | Sửa rồi key mới |
| Chưa đăng nhập/session không còn hợp lệ | 401 | Mã auth hiện hành BE | Flow auth hiện có, không tự đặt mọi lỗi thành hết hạn token | Có nếu an toàn | Theo auth contract |
| Không action permission/assignment hết hiệu lực | 403 | PERMISSION_DENIED | Không đủ quyền; khóa action | Có | Không tự retry |
| Resource không tồn tại/ngoài scope | 404 | RESOURCE_NOT_FOUND | Không tìm thấy/không thuộc phạm vi | Có | Không |
| Rate/WO optimistic version khác | 409 | VERSION_CONFLICT | Dữ liệu đổi; refetch trước xác nhận | Có | Refetch/reconfirm |
| Source revision/run evaluation khác | 409 | COST_SOURCE_STALE | Cần tính lại, expectedSourceVersion/runId | Có | Calculate lại |
| WO CANCELLED/CLOSED hoặc chưa COMPLETED khi finalize | 409 | INVALID_STATE | Trạng thái không cho thao tác | Có | Không tự retry |
| Checklist vận hành chưa đạt | 409 | CLOSE_BLOCKED | Danh sách blocker có source tối thiểu | Có | Xử lý nguồn |
| Baseline/cost evidence chưa đủ | 409 | COST_DATA_INCOMPLETE | Không đủ cơ sở giá/tiêu hao/thời gian | Có | Đối soát, không nhập số bù |
| Mixed/transfer/downstream values chưa replay | 409 | VALUATION_UNRECONCILED | Nguồn giá trị chưa đối soát | Có | Chờ reconciliation |
| Return/source value residual âm/không cân | 409 | MATERIAL_VALUE_UNRECONCILED | Phiếu nguồn không cân giá trị | Có | Không sửa history ngầm |
| Duration sai/overlap chưa giải thích | 409 | EXECUTION_TIME_UNRECONCILED | Kiểm tra ghi nhận thời gian | Có | Đối soát |
| Ref inactive/mismatch sau đọc form | 409 | REFERENCE_INVALID | Ref tương ứng, không mất form | Có | Refetch/reference |
| Kết quả sau rounding vượt numeric range | 422 | COST_VALUE_OVERFLOW | Giá trị vượt giới hạn, không partial write | Có | Điều chỉnh cấu hình |
| Cùng key khác intent | 409 | IDEMPOTENCY_CONFLICT | Thao tác khác dùng cùng mã yêu cầu | Có | Không lặp cùng key sai |
| Lock contention/transient | 409 | CONCURRENT_MODIFICATION | Đang có thay đổi cạnh tranh | Có | Cùng key; refetch khi conflict thật |
| Rate limit | 429 | RATE_LIMITED | Đợi theo Retry-After | Có | Cùng key sau thời gian |
| Server không xử lý được | 500 | INTERNAL_ERROR | Có trace để đối chiếu | Có | Cùng key nếu intent giữ |

Success envelope required `code`, `message`, `result`; paged result required content/page/size/totalElements/totalPages, first/last theo mapping. Error required code/message, errors optional `{field,message}[]`, result omit theo NON_NULL envelope hiện có; chi tiết null bên trong financial DTO cần Q6. X-Trace-Id response header giữ nguyên khi có thật; hiện không có trace BE để đính kèm. Auth credential/token luôn che, không nhúng trong ví dụ. Mock error names RATE/REASON/STALE/STATE/CLOSE_BLOCKED/VALUE_RESIDUAL chưa phải server contract; adapter cần mapping code đề xuất hoặc BE tương đương.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi câu là một quyết định. Owner chung: BE phụ trách contract; policy kinh doanh/kế toán cần PO chốt cùng BE. Tất cả **chưa chốt**, ngày quyết định chưa có. CHẶN là chặn tích hợp/nghiệm thu slice nêu ra, không chặn tiếp tục FE/mock.

1. **Q1:** Dùng extension riêng C1–C13 thay vì đổi ngầm existing variance/close được không? — FE nghiêng về Có, giữ tương thích và mapping — **CHẶN adapter**.
2. **Q2:** Đơn giá giờ áp cho cả Work Center hoạt động, không phải từng người được không? — FE nghiêng về Có theo demo đã duyệt — **CHẶN rate semantics**.
3. **Q3:** Cost/value scale6 HALF_UP, kiểm abs<10^32 sau rounding được không? — FE nghiêng về Có để bảo toàn amount, FE helper hiện guard trước rounding cần reconcile edge range — **CHẶN decimal acceptance**.
4. **Q4:** Baseline average unit cost và planned minutes giữ precision12 được không? — FE nghiêng về Có, tránh round unit sớm lệch tổng — **CHẶN calculation mapping**.
5. **Q5:** Rate giới hạn0..999999999, scale6 được không? — FE nghiêng về Có theo form demo, BE công bố schema range nếu khác — **CHẶN rate validation**.
6. **Q6:** Trong financial projection được phép đọc, unknown nullable fields phải hiện null dù envelope dùng NON_NULL được không? — FE nghiêng về Có để phân biệt redacted omit với missing — **CHẶN DTO**.
7. **Q7:** Capture cost baseline tại RELEASE được không? — FE nghiêng về Có để không dùng current catalog hồi tố — **CHẶN baseline**.
8. **Q8:** Missing rate/price giữ unknown nhưng không mới chặn RELEASE/production/delivery được không? — FE nghiêng về Có, chỉ chặn financial finalize — **CHẶN lifecycle policy**.
9. **Q9:** Planned minutes chia captured capacityUnits như §3.5 được không? — FE nghiêng về Có theo scheduler demo — **CHẶN planned labor/OH**.
10. **Q10:** Actual labor/OH dùng captured rate × recorded duration, không rate ngày close được không? — FE nghiêng về Có để lịch sử ổn định — **CHẶN actual costs**.
11. **Q11:** Retry duration tính đúng một lần theo execution records được không? — FE nghiêng về Có, historical Rework không thành giờ giả — **CHẶN time acceptance**.
12. **Q12:** Overlap trong cùng operation phải block nếu chưa giải thích được không? — FE nghiêng về Có; overlap cùng Work Center giữa lệnh/operation khác chưa được mock kiểm tổng quát, không tự mở rộng policy nguồn lực — **CHẶN time policy**.
13. **Q13:** Actual material dùng consumed source value thay gross issue được không? — FE nghiêng về Có, unused còn lại block close — **CHẶN material cost**.
14. **Q14:** Required material thiếu posting/consumption evidence giữ unknown được không? — FE nghiêng về Có, ledger trống không chứng minh known0 — **CHẶN completeness**.
15. **Q15:** Final unused return nhận residual theo §3.5 được không? — FE nghiêng về Có, mock GĐ11 đã bổ sung; đây là xác nhận tiếp GĐ8Q37 chưa tự đóng gate BE cũ — **CHẶN fractional value acceptance**.
16. **Q16:** Không sửa các posted returns cũ để chữa residual được không? — FE nghiêng về Có, chặn/reconcile riêng nếu lịch sử không cân — **CHẶN migration/value**.
17. **Q17:** Production scrap cost hấp thụ vào final Good được không? — FE nghiêng về Có theo policy demo, chưa phân loại abnormal loss riêng — **CHẶN costing policy**.
18. **Q18:** QC rejected giữ stock/value, không tự write-off được không? — FE nghiêng về Có, không đổi quota receipt hoặc auto rework — **CHẶN QC value**.
19. **Q19:** Phân bổ cumulative amount theo receipt→QCpart→delivery thay unitCost6×qty được không? — FE nghiêng về Có để tổng residual bằng total — **CHẶN allocation**.
20. **Q20:** Immutable posting sequence làm thứ tự residual được không? — FE nghiêng về Có; mock hiện timestamp+ID tie-break nên BE phải công bố thứ tự để FE đồng bộ, không giả UUID order là chronology — **CHẶN rounding fixture**.
21. **Q21:** Khi chưa có general value-flow replay, mixed/transfer/downstream graph bị chặn finalize được không? — FE nghiêng về Có, supported isolated fixture vẫn đóng được — **CHẶN valuation scope**.
22. **Q22:** Hàng giao trước close dùng value-only cost adjustment đúng source được không? — FE nghiêng về Có, không rewrite original ISSUE hoặc selling price — **CHẶN delivery COGS**.
23. **Q23:** Một command chốt giá và CLOSED atomic được không? — FE nghiêng về Có, không final-cost post để WO tiếp tục mutable — **CHẶN transaction contract**.
24. **Q24:** Good0 ghi loss record riêng, không stock movement, được không? — FE nghiêng về Có; BE xác nhận WIP-value settlement authoritative, mock chưa GL — **CHẶN all-scrap close**.
25. **Q25:** Close không phụ thuộc customer payment được không? — FE nghiêng về Có vì financial cost và thu tiền độc lập — **CHẶN close policy**.
26. **Q26:** Sau CLOSED vẫn giao stock đạt còn lại bằng cost đã biết được không? — FE nghiêng về Có; final run là snapshot lịch sử — **CHẶN post-close fulfillment**.
27. **Q27:** Lệnh no-routing/legacy cần một policy aggregate đã đối soát riêng trước khi dùng close mở rộng được không? — FE nghiêng về Có; modern mock cần operation evidence, không tự phủ nhận existing aggregate close API trước đây — **CHẶN legacy/no-routing mapping**.
28. **Q28:** Người chốt phải có Cost view ngoài Close permission được không? — FE nghiêng về Có, không đóng mù — **CHẶN authorization**.
29. **Q29:** Người đã tính preview được tự chốt cùng lệnh khi có đủ quyền được không? — FE nghiêng về Có theo demo, chưa thêm duyệt hai người — **CHẶN role mapping**.
30. **Q30:** Rate-only permission nhận minimal rate references thay full Work Center read được không? — FE nghiêng về Có, tránh cấp quyền master rộng — **CHẶN references/security**.
31. **Q31:** Server dùng opaque source revision thay JSON snapshot mock được không? — FE nghiêng về Có, cần phát hiện thay đổi nguồn liên quan không lộ tiền — **CHẶN concurrency DTO**.
32. **Q32:** Run có version độc lập cho expectedRunVersion được không? — FE nghiêng về Có; mock đang full-evaluation compare, FE adapter cần mapping — **CHẶN finalization DTO**.
33. **Q33:** Lý do rate/close trimmed5..2000 được không? — FE nghiêng về Có, giữ shortfall reason COMPLETE riêng — **CHẶN field validation**.
34. **Q34:** Date filters dùng timezone Plant bắt buộc được không? — FE nghiêng về Có; mock có fallback thiết kế, BE không fallback khi config thiếu — **CHẶN date acceptance**.
35. **Q35:** Backend dùng404 thống nhất cho ID ngoài scope được không? — FE nghiêng về Có để không lộ resource existence — **CHẶN security acceptance**.
36. **Q36:** Cho phép BE gộp zero-value adjustment entries nếu source sums không đổi được không? — FE nghiêng về Có, FE không nên phụ thuộc số entry mock — **KHÔNG CHẶN FE code tiếp**, cần chốt trước fixture assertions.

Các Q GĐ6/8/9/10 vẫn thuộc đúng task trước. Đặc biệt GĐ8Q7 output warehouse của MAKE DRAFT chưa được GĐ11 tuyên bố sửa; GĐ11 chỉ capture kho đang được phép tại RELEASE. GĐ8Q37 residual mock đã bổ sung nhưng BE decision/live acceptance vẫn cần trả lời. GĐ9 partialQC/source và GĐ10 delivery/pricing/claim là dependency, không tự coi đã deploy.

## §6 — FE đã tự xử lý

- Hai workspace/list/filter/page/right detail, cost tabs, rate form/history và confirmations VI/EN. Cost/error labels là nghiệp vụ, PERM/code chỉ contract/internal; không bắt người vận hành nhập lại BOM/receipt/duration.
- Shared mock persistence/transactions; RELEASE baseline một lần, decimal calculations, cumulative return/receipt/QC/delivery allocation, unknown guards, required-material evidence, time source checks và isolated-pool valuation boundary. Full mean-cost replay chưa có, không dùng workaround gán giá.
- Preview bất biến về snapshot; source/current auth sau lock/trước replay, close kiểm lại evaluation/versions, atomic stockvalue+cost+WO+audit/replay, storage rollback. CLOSED guards các production/receipt paths; không reopen, không tự sửa posted sources.
- Operational-only payload không money/runs/baseline/full source JSON; stock-only monetary fields null và costAmount omit trong mock, BE đề xuất omit financial object có explicit authority. Phải đồng bộ nullable/omit mapping Q6 trước API. Rate-only page không fetch full master catalog. Query keys có user/Company/Plant/permissions/scopes, đổi scope remount/refetch, command success invalidate affected queries; lỗi giữ form/key khi retry intent giữ.
- Source audit khác wire: mock material baseline giữ average12, planned minutes12; duration theo ghi nhận GĐ8 và tolerance1e−6 so actual timestamps; sourceVersion là internal JSON và sort residual timestamp+ID; no-routing modernclose chưa hỗ trợ; rate/run mock IDs numeric/string không UUID, runversion độc lập chưa có. BE không copy cấu trúc persistence hoặc precision/sort không công bố thành contract ngầm.
- Existing API variance/close adapters vẫn giữ contract cũ; extension API unavailable, không fallback. UI kiểm quyền không thay server authorization. Demo seed quyền chỉ Admin/Manager đã biết, không đổi production roles tự động.
- Không code/đổi dependency/API flags/testconfig trong phiên321. BE mapping khác các đề xuất phải ghi change log trước FE adapter update, rà lại source precision/sequence/unknown/redaction/versions/date/no-routing và acceptance; không tự coi thiếu field là0 để hết lỗi UI.

## §7 — Tham chiếu

Chỉ tên file và mục đích; thông tin cần triển khai đã có ngay trong tài liệu này. Các file nội bộ FE ghi rõ chưa gửi BE, không yêu cầu BE có repo để đọc. Không có OpenAPI/deployed version/response BE mới nhận trong phiên321.

- FE_HANDOFF_GD_04_2026-10-12.md — bộ tài liệu đã chuẩn bị trước về stock/lot/value pool, reservation/transfer và quyền; PO/BE xác nhận đã nhận nếu chưa có.
- FE_HANDOFF_GD_08_2026-10-14.md — WO/issue/execution/WIP/returns; Q7 kho output và Q37 residual vẫn cần quyết định BE.
- FE_HANDOFF_GD_09_2026-10-15.md — partial receipt/claim/QC children/nontracked/source và unknown FG value, dependency allocations.
- FE_HANDOFF_GD_10_2026-10-15.md — delivery/invoice/payment/pricing/source revisions; price/receivable không phải cost.
- CAPSTONE3_GD11_FE_PROPOSAL_2026-10-15.md — phương án nội bộ PO duyệt, chưa phải contract server hoặc tài liệu đã gửi BE.
- CAPSTONE3_GD11_FE_REVIEW_2026-10-15.md — báo cáo FE/mock Session320, nguồn bằng chứng tự động/browser và22 phần giao diện, nội bộ chưa gửi BE.
- FE_HANDOFF_GD_11_MESSAGE_2026-10-15.md — tin nhắn4dòng kèm file này để PO gửi; chưa gửi bên ngoài.

Tự kiểm trước giao: §0 đủ3dòng/P1; có phạm vi/ngoài phạm vi; §2 ghi rõ cả5mảnh live chưa có, không defect hoặc giả trace; §3 có32 yêu cầu kiểm được/field5cột/13endpoint đề xuất/công thức/F1–F4/48ca live pending; §4 nêu code; §5 tách36quyết định chỉ trả lời; §6 có FE defenses và source-to-wire gaps; §7 không repo/local path. JSON ở code blocks, không secret; tiền/quantity/IDs lặp phải cùng fixture. Chưa có evidence deployed BE mới nhất, không tự tick live gate; PO gửi ngày khác cần đổi tên/ngày, sửa sau gửi dùng_v2.

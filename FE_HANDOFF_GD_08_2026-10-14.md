# Giai đoạn 8 — Bàn giao Backend: Lệnh sản xuất, cấp vật tư, công đoạn và dở dang

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock GĐ8 đã hoàn thành; cần BE cung cấp nghiệp vụ tương đương cho WO, cấp vật tư nhiều dòng/duyệt bổ sung, input/rework/tiêu hao từng công đoạn, WIP và hoàn vật tư.
- Ảnh hưởng: lập kế hoạch, thủ kho, vận hành và quản lý chạy được demo; các phần mở rộng input/WIP/return chưa tích hợp với API đã xác minh, các module trước vẫn hoạt động.
- Mức ưu tiên: P1 — chặn phần mở rộng sản xuất ở chế độ API; không chặn FE/mock tiếp tục giai đoạn sau.

**Bản:** v1 · **Ngày dự kiến gửi:** 14/10/2026 · **Mốc FE:** Session 311, 14/10/2026 · **Phiên chuẩn bị tài liệu:** Session 312.
**PO:** đã duyệt phương án GĐ8 bằng “có”; yêu cầu “tạo tài liệu cho BE” sau delivery là chuyển sang bàn giao, không phải nghiệm thu Backend.
**FE owner / BE owner cá nhân:** chưa chỉ định. **Trạng thái:** đã chuẩn bị để PO gửi; chưa gửi ra ngoài, chưa xác minh OpenAPI/deploy và chưa nghiệm thu live. Ngày tên file là ngày dự kiến gửi; nếu gửi ngày khác đổi trước lần gửi đầu. Sau gửi sửa dùng `_v2`, `_v3` trong cùng task.

Nhãn áp dụng xuyên suốt:

- **[Đã kiểm chứng FE]:** source, tests hoặc browser mock; không chứng minh server có endpoint/DB transaction tương ứng.
- **[Đề xuất BE]:** yêu cầu/DTO/path/permission/error/policy cần thống nhất. API tương đương đã có thì BE cung cấp mapping/OpenAPI, không xây trùng hoặc đổi hợp đồng cũ ngầm.
- **[Giả định]:** policy chưa được BE xác nhận. Quyết định ở §5 là độc lập với phê duyệt bố cục/luồng demo của PO.
- **[Chưa kiểm chứng live]:** không gọi BE trong phiên lập tài liệu. Mọi JSON là ví dụ đề xuất; UUID minh họa không phải record server đã thử.

## §1 — Phạm vi

### 1.1 Toàn bộ màn hình mới/sửa và dữ liệu cần BE

| Loại | Màn hình/phần giao diện | Dữ liệu/hành vi |
|---|---|---|
| Sửa | Danh sách Lệnh sản xuất | Search mã/Item/SO, trạng thái/khoảng ngày/phân trang, cùng canonical WO thủ công và MAKE |
| Mới/nâng cấp mock | Tạo/sửa nháp Lệnh sản xuất | Item/UOM/lượng/output warehouse/ngày, component warehouses, source locks, preview lịch |
| Sửa | Chi tiết Lệnh sản xuất | Panel Tổng quan/Vật tư/Công đoạn/Dở dang/Chứng từ; route chi tiết cũ vẫn đọc cùng WO |
| Mới/nâng cấp | Lập kế hoạch/lịch sản xuất | Immutable BOM/Routing, lịch theo Work Center/shift/calendar, overload/overlap warning, lịch đã chốt |
| Mới/nâng cấp | Giữ vật tư | Auto partial đúng kho component hoặc manual theo nguồn/lô; shortage/readiness hiện tại |
| Mới/nâng cấp | Phát hành/hủy/giải phóng giữ | Xác nhận trạng thái, reason hủy, reservation và conversion/SO quota một lần, trace |
| Sửa | Danh sách Cấp vật tư | POSTED/PENDING_APPROVAL/REJECTED/CANCELLED, bộ lọc, hàng chờ quản lý |
| Mới/nâng cấp | Tạo phiếu cấp nhiều dòng | Own reservations, quick-fill phần còn cần, supplemental free stock/reason/source execution |
| Mới/nâng cấp | Chi tiết/Duyệt cấp bổ sung | Kế hoạch/gross issued/requested/extra/current availability, approve/reject/owner cancel |
| Sửa | Ghi nhận sản xuất | WO/operation, input/Good/Scrap/Rework/time, confirmed material consumption, lịch sử |
| Mới | Xác nhận đầu vào | Fresh input đầu tiên, transfer Good chưa chuyển từ predecessor đã kết thúc |
| Mới | Ghi kết quả/xử lý lại | Attempt bất biến, retry source/pending pool, tiêu hao từ issue lines |
| Mới | Kết thúc công đoạn/hoàn thành WO | Kiểm input/rework/pending, Manager shortfall reason, không tự nhận FG |
| Mới | Sản xuất dở dang | Danh sách/chi tiết theo WO; vật tư/công đoạn/rework/chờ nhập kho/lịch sử |
| Mới | Danh sách Hoàn vật tư | Search/status/WO/date/phân trang |
| Mới | Tạo phiếu Hoàn vật tư | Dòng cấp POSTED/chưa dùng, kho/lô nguồn, reason, confirmation |
| Mới | Chi tiết Hoàn vật tư | WO/issue source, Item/UOM/kho/lô/lượng/giá trị theo quyền |
| Sửa | Tồn kho/biến động | Movement link WO/material issue/material return; không nhận hàng chung để bypass nguồn |
| Sửa | MRP input/result | Firm WO supply dùng actual terminal Good; saved run bất biến; coverage warning |
| Sửa | Đơn bán hàng/allocation | Hủy unstarted WO trả quota một lần; allocation lịch sử và effective coverage phân biệt |
| Sửa | Tải trung tâm làm việc | Committed schedule từ WO không phụ thuộc người dùng từng mở màn hình sản xuất |
| Sửa | Sidebar/danh mục quyền | WIP/return routes, guard Company/Plant, tên quyền nghiệp vụ VI/EN |

Routes UI để BE hiểu ngữ cảnh, **không phải endpoint API**: `/manufacturing/work-orders`, `/manufacturing/work-orders/[id]`, `/manufacturing/material-issues`, `/manufacturing/production-execution`, hai route mới `/manufacturing/wip`, `/manufacturing/material-returns`. Chi tiết ở panel phải có header cố định và body cuộn, form nhiều dòng rộng; lỗi không xóa input, giao diện desktop/tablet VI/EN. BE không cần FE repo để thực hiện nội dung dưới đây.

### 1.2 Môi trường và ngoài phạm vi

**[Đã kiểm chứng FE]** Dev/demo local mock, mốc Session 311; chưa có commit/tag phát hành riêng. Browser đã chạy ngày 14/10/2026 giờ Việt Nam; không có timestamp/trace live BE cùng run. Numeric ID/code `WO-20261013-0001`, `MI-0001`, `EX-0001`, `MR-0001` là hồ sơ mock, không được dùng tra DB BE.

**Trong phạm vi:** canonical WO GĐ6, immutable design/schedule/readiness, cancellation quota, batch issue/approval, sequential input/transfer/retry/material consumption, scoped WIP/return/value/source/history, liên kết inventory/MRP/SO/capacity. Fields và endpoints cụ thể ở §3.

**Ngoài phạm vi:** Production Receipt/HOLD/QC/genealogy implementation mới của GĐ9; shipment/invoice/payment; MES/OEE; serial tracking; parallel/branch routing; chuyển vật tư giữa kho; substitute ngoài BOM; full labor/overhead/GL/scrap valuation; reversal/correction chứng từ đã post; tự mua bù/tự lập WO bù/tự đóng WO; migration policy SO fulfilled/open chưa chốt GĐ6. Không reset lịch sử hoặc sửa adapter cũ chỉ vì mock có field khác.

### 1.3 API đã quan sát trong source FE

**[Đã kiểm chứng FE source, đối chiếu 14/10/2026; chưa xác minh BE latest deploy/OpenAPI]** Path đầy đủ gồm Backend `/api` context. Đây không phải cam kết version BE đang chạy:

| Source adapter hiện có | Path đang dùng | Điểm chưa đủ cho GĐ8 |
|---|---|---|
| WO list/create | GET/POST `/api/v1/plants/{plantId}/work-orders` | Create hiện gửi `workOrderNo`, `productItemId`, `plannedQuantity`, `outputWarehouseId`, planned timestamps |
| WO detail/edit/plan | GET/PATCH `/api/v1/work-orders/{woId}`; POST `.../plan` | Plan hiện không body; chưa gửi version/component warehouse/lịch extension |
| WO readiness/reserve | GET `.../material-readiness`, GET/POST `.../material-reservations`, POST `.../reserve`, DELETE `.../material-reservations/{reservationId}` | Existing reservation fields/codes không chứng minh current eligibility hoặc cancellation quota mới |
| WO lifecycle/variance | POST `.../release`, `.../cancel`, `.../close`; GET `.../variance` | Cancel gửi reason; `/close` không phải production-complete extension |
| Issue list/single | GET/POST `/api/v1/material-issues` | List có plantId/workOrderId/status/page/size/sort; single theo reservation |
| Issue batch/decision | POST `/api/v1/work-orders/{woId}/material-issues`; POST `.../{issueId}/approve`, `.../{issueId}/reject` | Batch line đã có componentLineId, optional reservation/lot, reasonCode/overrideReason/sourceExecutionId; approve hiện không body/version |
| Execution list/candidates | GET `/api/v1/production-executions?workOrderId=...`; GET `.../candidates?plantId=...` | Candidates hiện hydrate WO detail; không đủ input/transfer/consumption ledger |
| Execution create | POST `/api/v1/work-orders/{woId}/production-executions` | Good/Scrap/Rework/time/optional operationId; chưa có retry source và consumption array |

WO component DTO đã có stable `componentLineId`, quantities và UOM; operation có `workOrderOperationId` từ snapshot. Issue POSTED/PENDING_APPROVAL có posting metadata nullable. Execution DTO có các WO aggregate và availableToReceipt. **Không suy operation input cap từ planned/issued/remaining aggregate.** Không suy BE chưa có API chỉ vì source FE chưa có adapter. BE gửi OpenAPI/mapping/version trước extension integration.

### 1.4 Luồng và quyền

**[Đã kiểm chứng FE/mock; đề xuất BE tương đương sau chốt §5]**

1. Tạo manual DRAFT hoặc mở DRAFT MAKE canonical. Manual draft chưa có production design hợp lệ cho đến plan; plan chụp active BOM/Routing. MAKE DRAFT giữ snapshot/requirement/source đã tạo ở GĐ6, không recapture âm thầm. Item output demo cho FINISHED_GOOD/WIP/SEMI_FINISHED.
2. Source-linked draft khóa output Item/lượng/component warehouses/allocation/source identity; lịch ngày có thể sửa khi còn DRAFT. **[Source audit Session312] Output warehouse hiện vẫn sửa được trong form/service, chưa có lock như dự kiến; BE khóa output warehouse là đề xuất Q7, FE cần đồng bộ sau policy, không claim đã enforce.** Manual plan tính component theo snapshot, chọn kho active cùng Plant; chặn BOM/Routing rỗng/không hợp lệ/UOM mismatch. Không client gửi raw snapshot thay server resolution.
3. Auto reserve từng phần, FIFO nguồn đủ điều kiện trong đúng kho component, không lấy kho khác dù còn hàng. Manual quantity dương không vượt shortage/free source. Thiếu → BLOCKED, giữ phần thành công trong cùng command; đây là partial-reserve kết quả hợp lệ, khác lỗi batch issue phải rollback.
4. Release PLANNED/BLOCKED khi tất cả component đủ readiness hiện tại; lot/warehouse/reference/expiry kiểm lại, schedule dùng immutable Routing với current calendar. Sau release là RELEASED; posted issue/input chuyển IN_PROGRESS.
5. Normal issue dùng own active reservation, không vượt gross BOM; over-BOM normal bị chặn để người dùng chuyển supplemental có lý do. Supplemental được cấp từ free eligible stock khi reservation đã dùng hết, vẫn chỉ component BOM. Reason codes REWORK/PROCESS_LOSS/DAMAGE/SETUP_LOSS/OTHER; source execution nếu gửi phải cùng WO có historical Rework>0 và reasonCode REWORK, không đồng nghĩa pending Rework phải còn>0.
6. Nếu một dòng khiến gross issued+requested vượt requirement, **toàn phiếu** PENDING_APPROVAL, không trừ stock/reserve/value hoặc tăng WIP. Tạo pending vẫn kiểm nguồn đủ tồn hiện tại; không hold stock cho pending. Approve recheck stock/state/version/current permission và post tất cả atomically. Reject/owner cancel reason, không stock effect.
7. Operation theo sequence snapshot, chỉ input/execute/finish khi predecessor COMPLETED. First input dương, tổng≤planned, phải có ít nhất một GĐ8 POSTED issue; không suy output PCS từ issue KG. Downstream input dương≤predecessor Good chưa transferred; transfer atomic tăng input operation sau và debit available transfer trước.
8. Fresh results Good/Scrap/Rework≥0, tổng>0 và≤unprocessed fresh input. Retry là execution mới có source cùng WO/operation, tổng≤pending source; source retryUsed tăng bằng tổng retry, retry có thể sinh Rework mới. Historical Rework cộng lịch sử; pending Rework tính pool chưa xử lý, không đồng nghĩa nhau.
9. Consumption nhập theo original own POSTED issueLine/UOM, quantity dương≤issued−prior consumed−prior returned. Duplicate lines gộp trước kiểm cap. Consumption ghi cùng execution atomically, không trừ stock lần hai. Array rỗng hợp lệ nếu lần đó không dùng thêm vật tư; không auto Good×BOM.
10. Finish operation khi fresh input/rework hết; first input phải đủ planned, downstream phải nhận hết predecessor Good. Nếu predecessor all-scrap Good0, operation sau có thể finish0, không gửi input0 giả. WO final Good chỉ từ operation cuối; Scrap tổng operation, historical Rework là số lượt, không cộng vào receiptable output.
11. Complete WO khi mọi operation COMPLETED và không còn pending issue. Final Good<planned phải quản lý có quyền ghi reason; không fake Good, không CLOSED/FG stock. Firm supply terminal WO≤actual final Good−approved receipts, phân bổ nguồn theo GĐ6; effective SO coverage cần chốt riêng, không coi allocation lịch sử là thành phẩm thực có.
12. Return IN_PROGRESS/COMPLETED theo nguồn issued chưa dùng được xác nhận, quantity>0, reason. Server resolve Item/UOM/kho/lô từ issueLine, không cho client đổi nguồn; active kho/lô AVAILABLE chưa hết hạn. Quantity/value/movement/ledger/WO phiên bản ghi atomic. Không trả vào QC HOLD/REJECTED hoặc khôi phục reservation đã issue.
13. Cancel DRAFT/PLANNED/BLOCKED/RELEASED chưa issue/execution/receipt/pending; reason; giải phóng own unissued reservations và active MAKE conversion/SO allocation quota đúng một lần, trace còn nguyên. Release unused reservations riêng không làm movement. COMPLETED chỉ return/release remaining theo quyền; CLOSED/CANCELLED không mutate GĐ8.

**Authorization:** role names dưới đây chỉ persona demo, server kiểm mã quyền + current assignment/scope, không dựa role name hoặc flat union. Mọi GET/ID/action/replay phải kiểm Company/Plant record; revoked/expired assignment sau đợi transaction phải bị từ chối. Path tenant mismatch không lộ record/stock/cost. `READ+action` là profile UI khi cần vào màn hình; mock service thường kiểm action riêng, ngoại lệ return kiểm cả READ+MANAGE. Server policy cần Q1/Q2.

| Thao tác | State trước → sau | Permission FE/mock | Scope/validation/tác động |
|---|---|---|---|
| WO list/detail | Không đổi | PERM_WORK_ORDER_READ | Plant; related projection chỉ data đủ quyền |
| Manual create/edit/plan/release/cancel/complete | Như luồng trên | PERM_WORK_ORDER_MANAGE | Current version/master/source/state; complete thiếu thêm PERM_MATERIAL_ISSUE_APPROVE trong demo, mapping BE chờ Q4 |
| Reserve/release unissued | PLANNED/BLOCKED hoặc unissued phù hợp → cập nhật readiness | PERM_MATERIAL_RESERVATION_MANAGE | Own WO/component/source, quantity/eligibility |
| Issue list/detail | Không đổi | PERM_MATERIAL_ISSUE_MANAGE hoặc PERM_MATERIAL_ISSUE_APPROVE hoặc PERM_MATERIAL_ISSUE_OVERRIDE | Đây là read projection; OVERRIDE một mình không tạo/duyệt |
| Issue create | Active WO → POSTED/PENDING_APPROVAL | PERM_MATERIAL_ISSUE_MANAGE | Multiple lines/gross/available; whole-batch atomic |
| Approve/reject pending | PENDING_APPROVAL → POSTED/REJECTED | PERM_MATERIAL_ISSUE_APPROVE | Decision-time versions/current stock; self approve thêm PERM_MATERIAL_ISSUE_OVERRIDE |
| Owner cancel pending | PENDING_APPROVAL → CANCELLED | PERM_MATERIAL_ISSUE_MANAGE | Actor phải là requester; reason |
| Execution read | Không đổi | PERM_PRODUCTION_EXECUTION_READ hoặc PERM_PRODUCTION_EXECUTION_MANAGE | Operation/snapshot/source scoped |
| Input/execute/retry/finish | READY/IN_PROGRESS → IN_PROGRESS/COMPLETED | PERM_PRODUCTION_EXECUTION_MANAGE | Input/pool/time/consumption checks |
| WIP list/detail | Không đổi | PERM_WIP_READ (mới, đề xuất) | Read business projection, không cấp EXECUTE/RETURN ngầm |
| Return list/detail | Không đổi | PERM_MATERIAL_RETURN_READ (mới) | Scope/own source; giá theo variance quyền |
| Return post | Tạo POSTED return | READ + PERM_MATERIAL_RETURN_MANAGE (mới) | Version/unused/eligible source, atomic |
| Đọc giá trị cấp/dùng/hoàn/WIP | Không đổi | PERM_WORK_ORDER_VARIANCE_READ | Không quyền thì server omit field giá; quantity vẫn đọc được nếu đủ quyền nghiệp vụ |
| Capacity/MRP/SO/Inventory source links | Không đổi | READ module tương ứng | Minimal WO/supply/source projection; không buộc lộ full cost/history |

Demo migration chỉ thêm ba quyền mới cho seed Admin/Manager đã biết, không tự thêm Operator hoặc mọi custom role. READ của WIP không đồng nghĩa được mở full trang issue/execution/return. Projection capabilities server là đề xuất §3, không giả mock đã có một capability API.

## §2 — Bằng chứng tái hiện

**Không có defect/live request — đây là bàn giao FE/mock và đề xuất contract.**

| Mảnh evidence Backend | Hiện có |
|---|---|
| Request thật method/path/header/body | Không có, phiên tài liệu không gọi BE |
| Response thật nguyên văn/HTTP/envelope | Không có |
| X-Trace-Id từ response header | Không có; không tạo trace giả |
| Môi trường + thời điểm BE | Không có run BE; latest deploy/version chưa xác minh |
| ID record server cùng run | Không có; UUID dưới đây minh họa |

```text
Mong đợi: BE cung cấp contract/implementation GĐ8 và bằng chứng live theo §3; FE tích hợp sau mapping được duyệt.
Thực tế: FE/mock đã chạy; input/WIP/return API mở rộng và policy liên quan chưa được xác minh, chưa báo lỗi BE.
```

**[Đã kiểm chứng FE]** Session311: 22 service +4 UI +11 navigation tests passed trong rerun37; full regression đầu 531/532 passed, assertion navigation-count cũ đã sửa và kiểm lại. Build/TypeScript42 pages, translation parity, phase8 lint clean; full lint0errors/545 warnings formatting đã có. Không rerun application tests trong phiên viết tài liệu; không coi mock locks là DB concurrency.

Browser demo: WO FG001 planned10PCS, issue10PCS SF001 +5.102041M² RM004; operation đầu input10→Good8/Scrap1/Rework1→retry Good1; hai operation sau input9/Good9. WO complete actual9/short1 có Manager reason. Consumption9PCS+4.5M² nhập tường minh; return1PCS+0.602041M² đúng source; unused0, awaiting receipt9. Tablet EN/desktop VI, reload, Plant2 empty/Plant1 restored. Đây là số liệu local, **không** request/record server. Không đính kèm ảnh phụ thuộc máy FE; luồng/số liệu đủ bằng chữ.

## §3 — Đầu ra mong đợi — Cần BE triển khai

**[Đề xuất BE]** R01–R22 là tiêu chí đúng/sai sau khi mapping/policy §5 được chốt. Có API tương đương thì cung cấp contract thay vì làm trùng. “Chặn FE” dưới đây nghĩa chặn adapter/acceptance phần đó, **không chặn FE/mock làm giai đoạn tiếp**.

| # | Yêu cầu kiểm được | Endpoint/nhóm | Chặn FE? |
|---|---|---|---|
| R01 | WO từ MAKE giữ canonical ID/source/allocation; manual DRAFT và edit không sửa source-locked fields | WO CRUD/MAKE | Có, nguồn/nháp |
| R02 | Manual plan resolve active BOM/Routing thành snapshot bất biến, component warehouse mỗi dòng; MAKE snapshot đã chụp không đổi theo master mới | WO plan/detail | Có |
| R03 | Lịch gồm operation/Work Center/start/end/work minutes/calendar revision; release resolve current calendar, capacity đọc committed WO trực tiếp | Schedule/read/release/capacity | Có |
| R04 | Auto reserve thiếu lưu partial trong đúng component warehouse; HOLD/REJECTED/expiry/other-owner excluded; manual không vượt source/shortage | Reserve/readiness | Có |
| R05 | Release chỉ khi current eligible reserved+gross issued≥required mọi component; source/design/state hợp lệ | WO release | Có |
| R06 | Cancel unstarted WO release own reservation và MAKE/SO active quota đúng một lần, trace immutable; có activity/pending thì fail không partial | WO cancel | Có |
| R07 | Batch issue 1–500 positive lines post cùng stock/value/WO/WIP/issue/idempotency transaction; normal chỉ own reservation | Issue batch | Có |
| R08 | Any over-BOM supplemental line làm whole phiếu pending không effect; gross không trừ returns; reason/source validate và pending không hold stock | Issue batch | Có |
| R09 | Approve recheck issue+WO revision, active state, current stock/eligibility/quyền; thiếu một dòng rollback cả phiếu; reject/owner cancel không stock effect | Issue decisions | Có |
| R10 | Operation input/transfer server có ledger và authoritative remaining; predecessor chưa complete không input/execute; không suy output qty từ issued material | Operation input/detail | Có |
| R11 | Fresh result ≤fresh input remaining; retry ≤pending source same WO/op, immutable attempts và atomic pool debit; historical/pending riêng | Execution | Có |
| R12 | WO final Good bằng operation cuối, không sum Good mọi op; aggregates Scrap/Rework ghi rõ là tổng lượt | WO/Execution summary | Có |
| R13 | Consumption từng own POSTED issueLine≤issued−consumed−returned, duplicate aggregate; execution+consumption atomic, no stock debit | Execution | Có |
| R14 | Finish input/rework hết và transfer đầy đủ; zero downstream finish sau predecessor Good0; Complete thiếu có Manager reason, không tự close/receipt/stock | Finish/complete | Có |
| R15 | WIP tách materials/output/op/rework, UOM và consumptionKnown/unknown; legacy thiếu evidence không fake0/enable unsafe action | WIP | Có |
| R16 | Return nguyên nguồn, unused cap/current eligibility, attributable source cost, atomic stock/value/WIP/movement/version; duplicate retry một phiếu | Return | Có |
| R17 | Release unissued không OnHand/value; return không làm giảm gross issued, không khôi phục reservation, không tạo QC bypass | Release/return/readiness | Có |
| R18 | Terminal firm WO supply dùng actual Good−approved receipt theo allocated/unallocated source, không planned giả; SO historical allocation giữ trace và policy coverage riêng | MRP/SO projections | Có |
| R19 | Scope/current assignment trên GET/direct ID/action/replay; giá chỉ quyền variance; source business projections không mở full unrelated data | Mọi read/write | Có |
| R20 | Quantity wire UUID/string-decimal, UOM/time/nullability/version chuẩn; server sinh actor/audit/source identity và X-Trace-Id | Mọi endpoint | Có |
| R21 | Server search/filter/page/total/sort ổn định; list và candidate cùng permission/scope, không fetch100 rồi coi đầy đủ | Lists/candidates | Có |
| R22 | Idempotency same actor/scope/resource/payload cùng IDs, mismatch conflict; current auth trước replay; concurrency/DB failure không duplicate/lost update | Mọi command | Có |

### 3.1 Quy ước wire và số liệu

**[Đề xuất BE, chưa contract chốt]** UUID string cho mọi entity/line/operation/snapshot/source conversion; không lấy numeric mock ID làm wire. `version` integer≥0 do server; expected versions bắt buộc command cập nhật existing entity. UUID operation là **WO snapshot operation**, khác master Routing operation. Actor lấy access principal, timestamps/audit server, không tin client actor/company/cost.

- Quantity JSON string dạng decimal không exponent, scale≤6, positive khi input/issue/return/consumption; Good/Scrap/Rework cho0 nhưng tổng>0. Mock input max999999999; server NUMERIC(19,6) range thực cần Q24, không tự nhận đầy đủ range từ giới hạn demo. Manual requirement mock cộng exact rồi ceiling6; return value mock proportional và HALF_UP6 từng dòng, chưa kiểm hết fractional residual khi hoàn nhiều lần (Q26/Q37).
- `uom` snapshot server, không client đổi Item unit/convert UOM trong GĐ8. Bảng output qty không cộng với material qty khác đơn vị.
- Business dates `YYYY-MM-DD` theo Plant; timestamp ISO8601 có offset/Z. Demo Plant Asia/Ho_Chi_Minh; server resolve timezone. `plannedStartDate/dueDate` extension không thay existing planned timestamps khi chưa mapping.
- Required fields luôn có; nullable fields unknown trảnull, optional unauthorized money omit hoàn toàn. Quantity0 khác unknown. `consumptionKnown=false` ⇒ consumed/unused/returnable=null; `inputKnown=false` ⇒ input cap=null và actions unsafe disabled. Known quantities nonnegative; không clamp dữ liệu invalid âm thành0 để giấu ledger lỗi.
- Pending/nonposted issue `postedAt/stockMovementId/issueValue` nullable; no movement=no posted event. Các list dùng standard envelope và zero-based page, default20/max100 đề xuất; `totalElements/totalPages` trên scoped filtered data, sort stable ID tie-breaker.
- Error/success `code`,`message` required; success result required cho read/command trả projection; lỗi `result` có thểomit, `errors` optional array `field/message`; `X-Trace-Id` header server mọi response. Không gửi secret trong document/log.

**Công thức:**

```text
manual component required = planned/outputQuantity × quantityPer ÷ (1−scrapRate), cộng cùng Item/UOM trước biên scale6
reservation remaining = reserved quantity − issued from reservation, chỉ ACTIVE/eligible source
gross issued = tổng quantity mọi issue POSTED; một posting chỉ đếm một lần dù có reservation
readiness shortage = max(0, required − eligible own unissued reserved − gross issued)
normal quick-fill = max(0, required − gross issued), giới hạn own reservation source
free eligible = eligible physical quantity − all active unissued reservations
own issue availability = free eligible + selected own eligible reservation remaining
extra requested = max(0, gross issued + batch requested per Item − required)
fresh remaining = confirmed input − fresh processed (không debit thêm khi retry)
pending rework of execution = recorded rework − retryUsed
operation pending rework = tổng pending của mọi immutable attempts, kể cả retry sinh rework
downstream transferable = completed predecessor Good − transferred
final Good = cumulative Good operation cuối; receipt allowance = max(0, final Good − approved output receipt)
confirmed unused/returnable per issueLine = issued − confirmed consumed − posted returned
shortfall at complete = max(0, planned − final Good)
issue value = quantity × moving-average source cost theo policy GĐ4, biết hay unknown
consumed value trace = original issueLine value × consumed/issued (không post stock)
return value = original issueLine value × return quantity/issued; rounded scale6 HALF_UP demo
unused value trace = original issueLine value − consumed value trace − tổng posted return values
```

GĐ8 manual snapshot tính trực tiếp component của snapshot, không re-run multi-level MRP. MAKE giữ requirement GĐ6. Rounding required quantity/minutes/value, duplicate component identity và phần lẻ nhiều lần hoàn cần Q6/Q24/Q25/Q26/Q37; GĐ6 Q28 ceiling-before-child-explosion vẫn mở độc lập. WO supply theo trạng thái/date/kho/earmark GĐ6; COMPLETE shortfall chỉ giảm cap, không tự biến allocation thành delivery/QC fulfilled.

### 3.2 Fields mới/extension

**[Đề xuất BE]** Logical names dưới đây cần mapping trước FE code adapter. `?` nghĩa optional; không có `?` là required; money unauthorized bịomit, authorized unknown=null. Full DTO cũ không phải cần viết lại.

| Field/nhóm | Kiểu | Nullable | Nguồn/công thức | Dùng UI |
|---|---|---|---|---|
| WO `version`, command `expectedWorkOrderVersion` | integer | Không | Server revision/read token | Stale mọi command |
| Issue `version`, decision `expectedIssueVersion` | integer | Không | Server issue revision | Manager decision |
| WO source `{runId,requirementId,conversionId,sourceRevision}`? | UUID/string revision | Source object absent manual | Canonical MAKE ledger; server cấp stable source/revision | Source locks/history/quota |
| component `componentLineId,itemId,warehouseId,uom,requiredQuantity` | UUID/string/decimal | Không sau plan | Snapshot/selected component warehouse; draft mapping Item→warehouse | Requirement/reserve/issue |
| component `grossIssuedQuantity,eligibleReservedQuantity,shortageQuantity` | decimal string | Không | Formulas §3.1, live eligibility | Readiness/approval |
| reservation `remainingQuantity,eligible,ineligibleReason` | decimal/bool/string | Reason null eligible | Current stock/reference/expiry | Reserve/issue candidates |
| WO `inputKnown,consumptionKnown` | boolean | Không | Migration/evidence status, không từ planned | Safe legacy display |
| WO `finalGoodQuantity,actualScrapQuantity,historicalReworkQuantity,terminalShortfallQuantity` | decimal string | Terminal shortfall null trước complete; legacy unknown null | Operation aggregates, captured shortfall | Overview/WIP/MRP |
| WO `approvedReceiptQuantity,availableToReceipt` | decimal string | Null nếu legacy thiếu output evidence | Approval ledger/final Good | GĐ9 seam |
| `scheduleRevision,calendarRevision,timeZone,capturedAt` | string/timestamp | Null chưa plan | Server committed schedule context | Capacity/WO schedule |
| schedule row `{workOrderOperationId,workCenterId,startsAt,endsAt,workMinutes}` | UUID/ISO/decimal | Không | Snapshot Routing/current calendar at commitment | Lịch/tải; work minutes có thể khác elapsed wall time |
| preview `{rows,overlaps,warnings}` | arrays/bool | Không | Current calendar + proposed/current snapshot routing | Preview; không stock/capacity commitment |
| operation `status,inputQuantity,freshProcessedQuantity,freshRemainingQuantity,goodQuantity,scrapQuantity,historicalReworkQuantity,pendingReworkQuantity,transferredQuantity,transferableQuantity` | enum/decimal | Input/cap null nếu unknown | Input/transfer/execution ledger | Công đoạn/WIP |
| execution `sourceExecutionId`? | UUID | Null/absent fresh | Same WO/operation attempt | Retry genealogy |
| execution `processedQuantity,retryUsedQuantity,pendingReworkQuantity` | decimal string | Không với ledger mới | Tổng attempt/pending source | History/retry cap |
| execution `consumption[] {issueLineId,quantity}` | UUID/decimal array | Không, có thể[] | Own posted line/exact UOM, atomic record | Vật tư thực dùng |
| execution `actualStartedAt,actualEndedAt,durationMinutes,operatorUserId,operatorUsername,createdAt` | timestamp/decimal/UUID/text | Không | Actual client time validated, actor/server audit | Manual time/history |
| issue lines `issueLineId,componentLineId,reservationId?,warehouseId,lotId?,quantity,uom,stockMovementId?` | UUID/decimal/text | Reservation optional supplement, lot null NON_TRACKED, movement null pending | Server resolves own source | Batch/source/trace |
| issue metadata `supplemental,reasonCode?,reason?,sourceExecutionId?,requestedByUserId,requestedByUsername,requestedAt,postedAt?,decision?` | bool/enum/text/UUID/ISO/object | Reason/source per validation; posting/decision absent pending | Requester immutable, decision actor/time/reason | Manager queue/history |
| issue decision context `requiredQuantity,grossIssuedQuantity,batchRequestedQuantity,extraRequestedQuantity,currentAvailability,asOf` | decimals/ISO | Không | Current quantities per component/source; not stored guarantee | Decision preview/recheck |
| issueLine WIP `consumptionKnown,consumedQuantity,returnedQuantity,unusedQuantity,returnableQuantity` | bool/decimal | Consumed/unused/returnable null unknown; returned history known | Own source ledger | WIP/return candidates |
| optional authorized `issueValue,consumedValue,returnedValue,unusedValue,currency` | decimal/text | Value null unknown; fields absent unauthorized | Original moving-average issued trace, currencyVND demo | WIP cost columns |
| return `materialReturnId,code,workOrderId,status,reason,postedAt,postedByUserId,postedByUsername,lines[]` | UUID/text/POSTED/ISO/array | Không | Immutable posting | List/detail |
| return line `sourceIssueLineId,componentLineId,itemId,uom,warehouseId,lotId?,quantity,stockMovementId,returnValue?` | UUID/text/decimal | lot nullable NON_TRACKED/value null unknown or absent unauthorized | Resolve original issue source/cost | Return/stock link |
| movement `sourceType,workOrderId?,materialIssueId?,materialReturnId?,sourceIssueLineId?,quantityDelta,valueDelta?` | enum/UUID/decimal | Fields theo event; value unknown null | Posting nguồn, không generic text only | Inventory trace |
| history `eventId,action,actorUserId,actorUsername,occurredAt,reason?,sourceIds` | UUID/enum/text/ISO/object | reason optional event không cần reason | Append-only audit | WO/WIP/doc history |
| projection `capabilities` | object booleans + denyReason strings | Không | Server current auth/state/evidence | Action availability; mutation vẫn recheck |
| effective SO/MRP `effectiveCoverageQuantity,uncoveredQuantity,coveragePolicyVersion`? | decimal/string | Absent pending policy | Q20 + GĐ6 metrics; không overwrite historical allocations | Warning/replan, không shipment |

### 3.3 Endpoints và payload tối thiểu

**[Đề xuất BE]** E=existing source path, X=extension/new proposed. Giữ path tương đương nếu BE có; dấu `...` ở §1 chỉ viết tắt, bảng này nêu full paths. `{W}` WO UUID, `{O}` WO-operation UUID, `{I}` issue UUID, `{P}` Plant UUID. Mọi command dùng `Idempotency-Key`; resource từ path resolve tenant, không gắn X-Plant-Id global vào aggregate endpoint nếu contract không yêu cầu. Explicit Plant path/query phải cross-check header cùng ID.

| E/X method/full path | Query/body | Success result | Page/search | Permission/scope | Retry |
|---|---|---|---|---|---|
| E GET `/api/v1/plants/{P}/work-orders` | Existing filters; X search/fromDate/toDate | Page WO summary/source | Page default20/max100; date trên createdAt, tie ID | WO_READ Plant | Read refetch |
| E POST `/api/v1/plants/{P}/work-orders` | Existing create fields; X componentWarehouses + dates; no source spoof | WO DRAFT/version | Không | WO_MANAGE Plant | Key |
| E GET/PATCH `/api/v1/work-orders/{W}` | PATCH existing fields + X expectedVersion/componentWarehouses, DRAFT only | WO snapshot/source/versions | Không | READ/MANAGE record | PATCH Key |
| X POST `/api/v1/work-orders/{W}/schedule-preview` | expectedVersion + draft dates/qty/warehouses optional manual | Preview rows/warnings/timeZone | Không | WO_READ/MANAGE scoped projection | Pure read không key bắt buộc |
| E POST `/api/v1/work-orders/{W}/plan` | X expectedWorkOrderVersion/componentWarehouses | WO PLANNED/schedule | Không | WO_MANAGE | Key |
| E GET `/api/v1/work-orders/{W}/material-readiness` | Không | Required/current own eligible reserved/gross issued/shortage per component | Không | WO_READ or scoped operation projection | Read |
| E GET/POST `/api/v1/work-orders/{W}/material-reservations` | GET page; POST componentLineId/warehouseId/lotId?/quantity + X expectedVersion | Page / reservation+current WO version | Page; all pages accessible | WO_READ / RESERVATION_MANAGE | POST Key |
| E POST `/api/v1/work-orders/{W}/reserve` | X expectedWorkOrderVersion | Existing reservation[]; X WO revision/readiness mapping | Không | RESERVATION_MANAGE | Key |
| E DELETE `/api/v1/work-orders/{W}/material-reservations/{reservationId}` | X expectedWorkOrderVersion | Existing optional result; X current revision | Không | RESERVATION_MANAGE, own remaining only | Key |
| X POST `/api/v1/work-orders/{W}/release-reservations` | expectedWorkOrderVersion | Released quantities/current readiness/version | Không | RESERVATION_MANAGE | Key |
| E POST `/api/v1/work-orders/{W}/release` | X expectedWorkOrderVersion | WO RELEASED or specific error | Không | WO_MANAGE | Key |
| E POST `/api/v1/work-orders/{W}/cancel` | reason + X expectedWorkOrderVersion | WO CANCELLED/released quota audit | Không | WO_MANAGE | Key |
| X POST `/api/v1/work-orders/{W}/complete-production` | expectedWorkOrderVersion/reason if shortfall | WO COMPLETED/finalGood/shortfall | Không | WO_MANAGE + Q4 shortfall authority | Key |
| E GET `/api/v1/material-issues` | plantId/workOrderId/status/page/size/sort + X search/fromDate/toDate | Page issue summary | Server date requestedAt | Issue read union §1.4 | Read |
| X GET `/api/v1/material-issues/{I}` | Không | Issue/detail/current decision context | Lines fixed≤500 | Scoped issue read projection | Read |
| X GET `/api/v1/work-orders/{W}/material-issue-candidates` | componentLineId?/mode NORMAL/SUPPLEMENTAL/page/size | Requirement + eligible source page/current availability/asOf | Page sources | ISSUE_MANAGE | Read |
| E POST `/api/v1/work-orders/{W}/material-issues` | lines[] existing shape + X expectedWorkOrderVersion/supplemental; reason metadata existing per line | Issue POSTED/PENDING_APPROVAL + current WO version | 1–500lines | ISSUE_MANAGE | Key |
| E POST `/api/v1/work-orders/{W}/material-issues/{I}/approve` | X expectedWorkOrderVersion/expectedIssueVersion; reason optional | Issue POSTED/current WO revision | Không | ISSUE_APPROVE; self thêm OVERRIDE | Key |
| E POST `/api/v1/work-orders/{W}/material-issues/{I}/reject` | reason + X expected versions | Issue REJECTED/current revision | Không | ISSUE_APPROVE | Key |
| X POST `/api/v1/work-orders/{W}/material-issues/{I}/cancel` | expected versions/reason | Issue CANCELLED/current revision | Không | ISSUE_MANAGE requester only | Key |
| X GET `/api/v1/work-orders/{W}/operations` | Không | Operation states/input/pools/snapshot list | Bounded snapshot list | EXEC_READ/MANAGE or minimal WIP projection | Read |
| X POST `/api/v1/work-orders/{W}/operations/{O}/input` | expectedWorkOrderVersion/quantity; predecessor server resolve | WO version + operation/input/transfer event | Không | EXEC_MANAGE | Key |
| E POST `/api/v1/work-orders/{W}/production-executions` | Existing results/time/operation; X expectedVersion/sourceExecutionId?/consumption[] | Immutable execution + scoped authoritative operation/WO summary | Consumption≤500 đề xuất | EXEC_MANAGE | Key |
| E GET `/api/v1/production-executions` | workOrderId/page/size; X plantId?/search/op/date scoped | Page attempts/source/pending | CreatedAt date; ID tie | EXEC_READ/MANAGE | Read |
| E GET `/api/v1/production-executions/candidates` | plantId/page/size | Scoped executable WO+minimal snapshots; X inputKnown/caps | Page | EXEC_MANAGE/READ | Read |
| X POST `/api/v1/work-orders/{W}/operations/{O}/finish` | expectedWorkOrderVersion | Completed operation/current WO version | Không | EXEC_MANAGE | Key |
| X GET `/api/v1/plants/{P}/wip` | search/status/workOrderId/fromDate/toDate/page/size | Page WO WIP summary | Date WO createdAt | WIP_READ | Read |
| X GET `/api/v1/work-orders/{W}/wip` | section MATERIALS/OPERATIONS/REWORK/AWAITING_RECEIPT/HISTORY; page/size for histories | Scoped section page + totals/UOM/known/capabilities/version | All history pages | WIP_READ | Read |
| X GET `/api/v1/material-returns` | plantId/workOrderId/status POSTED/search/fromDate/toDate/page/size | Page return summary | Date postedAt | RETURN_READ | Read |
| X GET `/api/v1/material-returns/{returnId}` | Không | Return lines/audit/source | Lines≤500 | RETURN_READ same record Plant | Read |
| X GET `/api/v1/work-orders/{W}/material-return-candidates` | page/size | Own issue lines/known unused/eligible source | Page | RETURN_READ+MANAGE | Read |
| X POST `/api/v1/work-orders/{W}/material-returns` | expectedWorkOrderVersion/reason/lines{sourceIssueLineId,quantity} | Return POSTED/current WO revision/movement IDs | 1–500lines | RETURN_READ+MANAGE | Key |

`status`/dates/search semantics above are wire proposals, not all supported in existing adapters. Backend paths for Capacity/MRP/SO/Inventory giữ contract module đó, extension source/supply fields theo R03/R18/R19. GET linked record ngoài Plant fail không lộ tồn; không gửi raw database snapshot qua WIP API. No silent breaking changes tới existing reserve array/command responses: BE báo mapping schema version trước chuyển shape.

### 3.4 Transaction, sửa sai và migration

- Server transaction/locks bảo vệ WO revision, component/source stock/reservations, issue decision, input/rework pools, consumption/return cap, conversion/allocation và audit/idempotent result. Lock resource ổn định chống deadlock; mock master→inventory→design chỉ là FE demo, không yêu cầu DB dùng tên lock JS.
- Pending creation validate toàn nguồn nhưng không debit/hold. Approval validation+posting một transaction; tồn đã đổi không partial-post hoặc tự chọn lô/kho thay. Multi-source/duplicate lines gộp theo source và component để cap cả batch. Không tin preview availability.
- Same key/same actor+tenant+resource+canonical payload trả cùng IDs/kết quả với **current scoped projection**, không increment version lần nữa. Different payload/key reuse409; authorize trước replay. Key retry mạng giữ nguyên; người dùng sửa payload tạo key mới. Retention≥24h đề xuất, Q28.
- Manual DRAFT sửa theo expected revision; planned/released snapshots bất biến. POSTED issue/execution/return không PATCH/DELETE trong GĐ8; sửa sai/reversal cần task/policy riêng. Consumption totals và retryUsed có thể tăng qua ledger mới, original attempt input/results/source/time không bị rewrite.
- Existing WO/issue/execution phải preserve canonical IDs/posted quantities/audit. Chỉ enroll legacy pristine khi chưa issued/executed/received; lịch sử có activity thiếu input/consumption giữ unknown và block unsafe action. Không backfill consumed=issued hoặc0. Hủy GĐ6 MAKE DRAFT cần active conversion record dù chưa có GĐ8 runtime.
- Source quota cancellation không restore fulfilled/delivered receipt quantity, không auto SO cancel. Saved MRP run immutable; source revision stale yêu cầu replan, lịch sử cancelled conversion còn truy được.
- Receipt/QC GĐ9 sau này dùng final Good output; WIP material chưa dùng/tiêu hao có UOM riêng. Nhận FG không giải quyết bằng cách xóa quantity material toàn WO.

### 3.5 Stock và value effects

**[Đề xuất BE, quantity/value sign đã kiểm chứng mock]** Available dưới đây là eligible free quantity; own-issue availability còn cộng own unissued reservation. VND value trace, không full accounting allocation. HOLD/REJECTED không tham gia nguồn.

| Event | On Hand | Reserved chưa cấp | Free Available | HOLD/REJECTED | WIP/value | Related docs |
|---|---|---|---|---|---|---|
| Save/plan/preview | 0 | 0 | 0 | 0 | 0 | WO/schedule/source |
| Reserve q | 0 | +q | −q | 0 | 0 | Own reservation/readiness |
| Release unissued q | 0 | −q | +q nếu nguồn eligible | 0 | 0 | Reservation audit; no stock movement |
| Cancel unstarted WO | 0 | −own remaining | +eligible own remaining | 0 | 0 | Conversion/allocated quota inactive once |
| Normal issue q own reserve | −q | −q | 0 do reserved→issued | 0 | Material held at production +q; inventory value−v, WIP source+v | POSTED issue/movement/WO |
| Supplemental free issue q | −q | 0 | −q | 0 | +q/+v source | POSTED issue/movement |
| Pending/reject/owner cancel issue | 0 | 0 | 0 | 0 | 0 | Decision/audit only |
| Approve pending | Như posted source | Như source | Như source | 0 | Như source | Entire batch once |
| Input/transfer/execution | 0 | 0 | 0 | 0 | Operation/output pools; source material move unconsumed→consumed, no inventory posting | Input/execution/consumption ledger |
| Return unused q at issue cost v | +q original source | 0 | +q source eligible | 0 | Unused material−q; source WIP−v, inventory+v; gross issued unchanged | Return/movement/source issue |
| Finish/Complete | 0 | 0 | 0 | 0 | Output awaits receipt; final Good cap | WO completion/shortfall audit |

Unknown inventory/source value remains unknown, not0. Return cost lấy original issue value, không average **hiện tại**; số kho value sau return dùng stock valuation GĐ4. Material consumed không đồng nghĩa hoàn thành cost allocation xuống FG/scrap. Rework retry không debit fresh input hay stock lần hai; extra material phải issue riêng.

### 3.6 Fixture số xuyên suốt và JSON copy được

**Tất cả dữ liệu/JSON ở mục này là VÍ DỤ ĐỀ XUẤT, không phải request/response đã chạy.** Header token placeholder đã che; không có X-Trace-Id thật. Khi nghiệm thu BE seed mapping UUID thật và dùng đúng một run để thu request/response/trace/time/record.

| Alias | UUID minh họa | Ý nghĩa |
|---|---|---|
| P1 | 11111111-1111-4111-8111-111111111111 | Plant cùng Company được cấp quyền |
| W100 | 88888888-8888-4888-8888-000000000100 | Canonical WO planned10PCS/output khoFG |
| C1 | 88888888-8888-4888-8888-000000000101 | Component snapshot20KG, ItemRM, warehouseRAW |
| O1 / O2 | 88888888-8888-4888-8888-000000000111 / 88888888-8888-4888-8888-000000000112 | WO operations sequence10/20, không master operation IDs |
| RS1 | 88888888-8888-4888-8888-000000000121 | Own reservation20KG cho C1 |
| I1 / IL1 | 88888888-8888-4888-8888-000000000131 / 88888888-8888-4888-8888-000000000132 | Normal issue20KG / posted issue line |
| I2 / IL2 | 88888888-8888-4888-8888-000000000141 / 88888888-8888-4888-8888-000000000142 | Supplemental1KG / issue line |
| EX1 / EX2 / EX3 | 88888888-8888-4888-8888-000000000151 / 88888888-8888-4888-8888-000000000152 / 88888888-8888-4888-8888-000000000153 | O1 fresh, O1 retry, O2 final |
| MR1 | 88888888-8888-4888-8888-000000000161 | Return3KG |

**Fixture F1:** ItemRM NON_TRACKED UOMKG, source RAW kho cùng Plant ACTIVE. Opening stock100KG/value1000VND, no other reservations, average10VND/KG. W100 planned10PCS; BOM output1PCS dùng2KG, scrap0 ⇒ required20KG; Routing2 operations O1/O2. Item/UOM/BOM/Routing/kho ACTIVE, calendar hợp lệ. FG opening0. All event quantities exact; 10PCS output không bằng20KG material.

| Bước | RAW OnHand/Reserved/Free KG | Material gross issued/consumed/returned/unused KG | Source value VND | Output |
|---|---|---|---|---|
| Opening | 100/0/100 | 0/0/0/0 | Inventory1000/WIP0 | Planned10PCS |
| Reserve20 / release WO | 100/20/80 | 0/0/0/0 | Inventory1000/WIP0 | RELEASED |
| I1 normal20 post | 80/0/80 | 20/0/0/20 | Inventory800/issued200 | IN_PROGRESS |
| I2 supplemental1 pending | 80/0/80 | 20/0/0/20 | Inventory800; pending value chưa post | Gross20+request1>20 ⇒ extra1 |
| Manager approve I2 | 79/0/79 | 21/0/0/21 | Inventory790/issued210 | No FG stock |
| O1 input10; EX1 Good8/Scrap1/Rework1 consume16 từ IL1 | 79/0/79 | 21/16/0/5 | Consumed160/unused50 | O1freshRemaining0,pending1; WO finalGood0 |
| EX2 retry EX1 Good1 consume2 từ IL1 | 79/0/79 | 21/18/0/3 | Consumed180/unused30 | O1Good9/Scrap1/pending0,historicalRework1 |
| FinishO1; O2input9; EX3Good9; finishO2 | 79/0/79 | 21/18/0/3 | Không stock/value posting | FinalGood9, không18 |
| MR1 return IL1remaining2 + IL2remaining1 | 82/0/82 | 21/18/3/0 | Inventory820; returned30; consumed trace180 | Output vẫn9 |
| Complete Manager reason thiếu1 | 82/0/82 | 21/18/3/0 | Source issued210=consumed180+returned30 | COMPLETED, shortfall1, awaiting receipt9PCS |

MR1 thực hiện khi IN_PROGRESS trước Complete để minh họa: sau hoàn, xin thêm1KG vẫn xét gross21+1>required20, phải pending; nếu tạo thêm phiếu đó cần reject/cancel trước Complete. Dòng kiểm phụ này không thuộc chuỗi F1 stock/value, không giả extra đã được duyệt.

**F2 partial/eligibility:** W100 variant mới yêu cầu20KG nhưng RAW chỉ15KG eligible; kho khác500KG không tham gia; autoreserve15/shortage5/BLOCKED. Nhận thêm5KG **AVAILABLE** qua nguồn hợp lệ rồi reserve5 mới release. Receipt HOLD5KG chưa đủ. Lô hết hạn sau reserve chặn release; lot chuyển HOLD trước return chặn return và không posting.

**F3 scale6:** riêng WO/item fixture mới, issue1.000001KG, confirmed consume1KG, return0.000001KG; unused0, gross1.000001. Không truncation3decimals. Không dùng F3 làm thêm event của F1. **F4 unknown:** own legacy posted issue21KG nhưng thiếu consumption evidence ⇒ unused/returnable=null và action blocked; không tự suy unused21/0. Value null là unknown dù quantity có thểknown.

**Ví dụ đề xuất — normal batch body** cho POST `/api/v1/work-orders/88888888-8888-4888-8888-000000000100/material-issues`. UUID key mỗi command khác nhau; retry cùng payload giữ key. Expected revision1 là minh họa đã đọc sau release, không hardcode.

```http
Authorization: Bearer <REDACTED>
Content-Type: application/json
Idempotency-Key: 88888888-8888-4888-8888-000000000901
```

```json
{
  "expectedWorkOrderVersion": 1,
  "supplemental": false,
  "lines": [{
    "componentLineId": "88888888-8888-4888-8888-000000000101",
    "reservationId": "88888888-8888-4888-8888-000000000121",
    "warehouseId": "88888888-8888-4888-8888-000000000201",
    "quantity": "20.000000"
  }]
}
```

Warehouse UUID ending201 là RAW F1 cùng Plant; NON_TRACKED không gửi lotId. Server resolve Item/UOM/value. ComponentLineId liên kết đúng snapshot C1.

**Ví dụ đề xuất — supplemental1 pending**, POST cùng batch path, mới key ending902; warehouse cùng RAW:

```json
{
  "expectedWorkOrderVersion": 2,
  "supplemental": true,
  "lines": [{
    "componentLineId": "88888888-8888-4888-8888-000000000101",
    "warehouseId": "88888888-8888-4888-8888-000000000201",
    "quantity": "1.000000",
    "reasonCode": "PROCESS_LOSS",
    "overrideReason": "Cấp thêm để xử lý hao hụt công đoạn."
  }]
}
```

Mock reason metadata nằm cấp phiếu; existing wire cấp dòng. Body ở đây giữ wire per-line, **đề xuất** `supplemental` và expectedVersion; mapping/Q9 phải chốt, không gửi thêm field trước contract. Uniform reason trong demo không có nghĩa server được bỏ distinct line reason nếu đã hỗ trợ.

**Ví dụ đề xuất — response pending HTTP200**, scoped summary không có stockMovementId/value post, current WO version3. Decision context là per-component aggregate, source availability80KG tại thời điểm snapshot; không đảm bảo approve còn80.

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "issueId": "88888888-8888-4888-8888-000000000141",
    "code": "MI-F1-02",
    "workOrderId": "88888888-8888-4888-8888-000000000100",
    "status": "PENDING_APPROVAL",
    "version": 0,
    "workOrderVersion": 3,
    "postedAt": null,
    "lines": [{
      "issueLineId": "88888888-8888-4888-8888-000000000142",
      "componentLineId": "88888888-8888-4888-8888-000000000101",
      "quantity": "1.000000",
      "uom": "KG",
      "stockMovementId": null,
      "issueValue": null
    }],
    "decisionContext": [{
      "componentLineId": "88888888-8888-4888-8888-000000000101",
      "requiredQuantity": "20.000000",
      "grossIssuedQuantity": "20.000000",
      "batchRequestedQuantity": "1.000000",
      "extraRequestedQuantity": "1.000000",
      "currentAvailability": "80.000000",
      "asOf": "2026-10-14T02:00:00Z"
    }]
  }
}
```

Summary JSON cố ý chỉ fields liên quan, không full MaterialIssue DTO. Full detail/list schema phải trả fields required §3.2/existing DTO hoặc BE công bố projection name khác. `issueValue:null` minh họa user có quyền variance; user không có quyền thì omit.

**Ví dụ đề xuất — approve I2** POST `/api/v1/work-orders/88888888-8888-4888-8888-000000000100/material-issues/88888888-8888-4888-8888-000000000141/approve`, key903. Manager khác requester; same requester cần override. Response POSTED có movement/value/current WOversion4. Reject/cancel dùng cùng versions +reason, không stock effect.

```json
{ "expectedWorkOrderVersion": 3, "expectedIssueVersion": 0 }
```

**Ví dụ đề xuất — first input10** POST `/api/v1/work-orders/88888888-8888-4888-8888-000000000100/operations/88888888-8888-4888-8888-000000000111/input`, key904:

```json
{ "expectedWorkOrderVersion": 4, "quantity": "10.000000" }
```

**Ví dụ đề xuất — EX1 fresh**, POST `/api/v1/work-orders/88888888-8888-4888-8888-000000000100/production-executions`, key905; times là minh họa diễn ra trước lúc server post:

```json
{
  "expectedWorkOrderVersion": 5,
  "workOrderOperationId": "88888888-8888-4888-8888-000000000111",
  "goodQuantity": "8.000000",
  "scrapQuantity": "1.000000",
  "reworkQuantity": "1.000000",
  "actualStartedAt": "2026-10-14T02:05:00Z",
  "actualEndedAt": "2026-10-14T02:15:00Z",
  "consumption": [{
    "issueLineId": "88888888-8888-4888-8888-000000000132",
    "quantity": "16.000000"
  }]
}
```

**Ví dụ đề xuất — EX2 retry EX1**, cùng execution path, key906:

```json
{
  "expectedWorkOrderVersion": 6,
  "workOrderOperationId": "88888888-8888-4888-8888-000000000111",
  "sourceExecutionId": "88888888-8888-4888-8888-000000000151",
  "goodQuantity": "1.000000",
  "scrapQuantity": "0.000000",
  "reworkQuantity": "0.000000",
  "actualStartedAt": "2026-10-14T02:16:00Z",
  "actualEndedAt": "2026-10-14T02:18:00Z",
  "consumption": [{
    "issueLineId": "88888888-8888-4888-8888-000000000132",
    "quantity": "2.000000"
  }]
}
```

Sau retry WOversion7; finishO1→8; inputO2 quantity9→9; EX3 Good9/Scrap0/Rework0, `consumption:[]`→10; finishO2→11. Mỗi command mới key907–910 và đọc current revision; không hardcode nếu run phát sinh event khác. O1Good9/pending0/transferred9, O2Good9/freshRemaining0; finalGood9PCS, material unused IL1=2KG/IL2=1KG.

**Ví dụ đề xuất — MR1 return3**, POST `/api/v1/work-orders/88888888-8888-4888-8888-000000000100/material-returns`, key911:

```json
{
  "expectedWorkOrderVersion": 11,
  "reason": "Hoàn phần vật tư xác nhận chưa dùng sau khi kết thúc các công đoạn.",
  "lines": [
    { "sourceIssueLineId": "88888888-8888-4888-8888-000000000132", "quantity": "2.000000" },
    { "sourceIssueLineId": "88888888-8888-4888-8888-000000000142", "quantity": "1.000000" }
  ]
}
```

Server không nhận client Item/kho/lô/cost để override. Return IDsMR1, two movements, values20/10 VND, WOversion12. Complete-production key912/body expectedVersion12 + reason thiếu1 →WOversion13 COMPLETED/shortfall1, noFGmovement. No special key13 dùng lại cho another mutation.

**Ví dụ đề xuất — WIP section totals response HTTP200** cho final W100. `materials` là scoped summary để diễn tả formulas; detail theo issue lines vẫn giữ IDsIL1/IL2:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "workOrderId": "88888888-8888-4888-8888-000000000100",
    "version": 13,
    "status": "COMPLETED",
    "materials": [{
      "componentLineId": "88888888-8888-4888-8888-000000000101",
      "uom": "KG",
      "consumptionKnown": true,
      "grossIssuedQuantity": "21.000000",
      "consumedQuantity": "18.000000",
      "returnedQuantity": "3.000000",
      "unusedQuantity": "0.000000",
      "issueValue": "210.000000",
      "consumedValue": "180.000000",
      "returnedValue": "30.000000",
      "unusedValue": "0.000000",
      "currency": "VND"
    }],
    "output": {
      "uom": "PCS",
      "finalGoodQuantity": "9.000000",
      "approvedReceiptQuantity": "0.000000",
      "availableToReceipt": "9.000000",
      "terminalShortfallQuantity": "1.000000"
    },
    "capabilities": { "canExecute": false, "canReturn": false, "canReadCosts": true }
  }
}
```

Đây là user có quyền cost; canReturn=false vì unused0, không vì mất quyền. Legacy consumption unknown response dùng `consumptionKnown:false,consumedQuantity:null,unusedQuantity:null,returnableQuantity:null`, không thay null bằng0. API pending query standard page example:

```json
{ "code": "SUCCESS", "message": "Success", "result": { "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 } }
```

### 3.7 Nghiệm thu live và rollout

Mọi A01–A40 **Chưa thử live**, chưa nghiệm thu BE. Evidence E cho mỗi case: method/path/body/headers đã che secret, nguyên văn HTTP/envelope response, X-Trace-Id server, env+deploy version+timestamp timezone, **ID đúng run**, và refetch WO/ledger/inventory/source đối chứng khi có effect. Test mock trước không thay E. Failed action cũng cần GET đối chứng không có partial posting.

| ID | Fixture/đầu vào | Thao tác | Kết quả kiểm được | Bằng chứng | Trạng thái |
|---|---|---|---|---|---|
| A01 | Active masters/manual draft | Create/edit/plan | Canonical ID, immutable design/UOM/selected warehouse/source | E | Chưa thử live |
| A02 | MAKE DRAFT GĐ6/source refs | Edit locked qty/output/warehouses | SOURCE_LOCKED; allocation/quota unchanged | E | Chưa thử live |
| A03 | Master BOM/Routing đổi sau snapshot | Plan/read/release source WO | Không overwrite source design; current calendar policy đúng | E | Chưa thử live |
| A04 | F2 eligible15/rightWH, otherWH500 | Auto reserve/release | Hold15/short5/BLOCKED; wrongWH ignored, release fails | E | Chưa thử live |
| A05 | F2 thêm5AVAILABLE vs5HOLD | Reserve/release | Chỉ AVAILABLE bù5 và release | E | Chưa thử live |
| A06 | Active reservation lot hết hạn/kho inactive sau giữ | Release | Eligibility error, state unchanged | E | Chưa thử live |
| A07 | Snapshot routing/calendar changed | Preview/release/capacity | Preview pure; committed current schedule trace, capacity không phụ thuộc ghé UI | E | Chưa thử live |
| A08 | Manual reserve wrong owner/item/warehouse/lot | Reserve/issue | Specific denied/source code; no side effects | E | Chưa thử live |
| A09 | F1 ownRS20 | Partial issue12 rồi8 | OnHand80/reserved0/gross20; each retry once | E | Chưa thử live |
| A10 | Normal request vượtBOM | Issue | Over-BOM normal blocked; không bypass bằng reason thiếu | E | Chưa thử live |
| A11 | Supplemental batch1line over,1line valid | Create pending | Whole pending, stock/reserve/value/WIP unchanged | E | Chưa thử live |
| A12 | Pending tạo lúcstockđủ, sauđóstockthiếu | Approve | Specific current stock error, full pending preserved/no partial | E | Chưa thử live |
| A13 | F1 I2 khôngRS sauI1 đã consumed | Approve | POSTED1/gross21/inventory79/value790 once | E | Chưa thử live |
| A14 | Pending I2 variants | Reject/owner cancel | Reason/history terminal state, no stock/WIP | E | Chưa thử live |
| A15 | Requester approver nooverride / withoverride | Approve | First denied, explicit override policy permitted, auditactor | E | Chưa thử live |
| A16 | REWORKsource khácWO/op/historical0 | Supplemental/retry | Reject sai nguồn; extra issue nguồn historicalREWORK policy rõ | E | Chưa thử live |
| A17 | Batch laterlineinvalid/storagefail | Postissue/return/execution | Rollback all earlier lines, inventory/value/WO/ledger/key | E | Chưa thử live |
| A18 | O2 predecessor chưafinish | Input/execute/finish | Sequence error, pools unchanged | E | Chưa thử live |
| A19 | F1 first input10; thêm1 | Input | Confirm10 once, extra1 INPUT_EXCEEDED; requirespostedissue | E | Chưa thử live |
| A20 | F1 EX1 | Execute8/1/1 | Freshremaining0,pending1, finalWOgood0,consume16 no stockdebit | E | Chưa thử live |
| A21 | F1 EX1pending1 | RetryGood1 rồi retry1 nữa | FirstGood9/pending0; second cap error, immutable EX1/EX2 | E | Chưa thử live |
| A22 | Retry1→rework1→retry source child | Execute retry chain | New pending belongs child, parentdebit1, no duplicated pool | E | Chưa thử live |
| A23 | Consumption duplicate IL1entries/over cap/wrongWO | Execute | Aggregate cap; bad batch rollback results+consumption | E | Chưa thử live |
| A24 | Future/end<start/invalidtime | Execute | Time error/input retained; no event | E | Chưa thử live |
| A25 | Pendinginput/rework or predecessor Good chưatransferhết | Finish | Block; firstop inputmustplanned; no prematurecomplete | E | Chưa thử live |
| A26 | All input firstop scrap10/Good0 | Finish downstreamzero/complete | No input0fake; Manageractual0/short10, no FG | E | Chưa thử live |
| A27 | F1 O2input9/EX3Good9 | Finish/Complete no reason/noManager | FinalGood9not18; completion denied until authority+reason | E | Chưa thử live |
| A28 | F1 finalGood9/short1 | ManagerComplete | COMPLETED/shortfall1/receiptcap9/no CLOSED/no FG | E | Chưa thử live |
| A29 | F1 consumed18/issued21 | Return3/over4 | Valid3originalsources/value30; invalid4 no stock/WIP | E | Chưa thử live |
| A30 | F1 return3 followed active requestextra1 | Issue/approvalcontext | Gross remains21, extra2 overrequired20; no falsefreeBOM | E | Chưa thử live |
| A31 | Originalsource lot HOLD/REJECTED/expired/warehouseinactive | Return | Sourceeligibility fail, not genericReceive/QCbypass | E | Chưa thử live |
| A32 | Legacyunknown consumption/value | WIP/input/return | Unknownnull/blockedunsafe; no guessed0/cost | E | Chưa thử live |
| A33 | F3 exact6 + valuefraction repeatedreturns | Execute/return | Qtyexact6 and agreedroundingresidual, no negative trace | E | Chưa thử live |
| A34 | Pristine MAKEdraftlinkedSO/quota | Cancel/retry | Ownresrelease/quotaonce, historypreserved, sourcecancelled | E | Chưa thử live |
| A35 | WO issue/execution/receipt/pending | Cancel | Activityblocked; unrelatedreservations/quota unchanged | E | Chưa thử live |
| A36 | F1 COMPL9, no receipts; allocated/unallocatedvariants | MRP/SOsource reads | Actualfirmcap9, unallocatedportioncorrect, coveragepolicyexplicit; savedrunimmutable | E | Chưa thử live |
| A37 | Twoactors/concurrentissue/retry/return/approve | Commands | No overspend/input/rework/return overcap/lostupdate; onecommit orstalefailure | E | Chưa thử live |
| A38 | Samekey/samepayload vsdifferent; authrevokedbeforewait/replay | Retry | SameIDs/no duplicate; mismatch409; current403, no data leak | E | Chưa thử live |
| A39 | WIPonly/nocost/Plant2/directUUID | Read/write/replay | Minimalquantity allowed; costomit/actiondenied/otherPlant404; no fullotherhistory | E | Chưa thử live |
| A40 | >100rows/search/dateboundary/tablet; GĐ1–7regression | List/candidates/scopedlookup/handoffnext | Allpaged totals/sort/filter, timezonecorrect, no breakexistingflows | E | Chưa thử live |

**Thứ tự triển khai:** (1) BE phản hồi §5 và cấp OpenAPI/version + mappings existing endpoints; (2) canonical WO/source/revision + preserve history/migration, master/calendar/eligible inventory dependencies; (3) reservations/lifecycle/quota; (4) atomic issue approval/quantity/value/source; (5) input/transfer/rework/consumption ledger; (6) WIP/return/read projections + capacity/MRP/SO supply; (7) permission catalog/seed có kiểm scope; (8) FE adapter sau PO duyệt code, live A01–A40 đúng deployed version. GĐ9 receipt/QC xử lý sau, không gate ngầm sự hoàn thành FE GĐ8.

**Chuyển mock→API:** chỉ bật sau contract/authorization/source/value/nullability/time/replay mapping được chốt và adapter được kiểm; không fallback mock khi BE lỗi hoặc thiếu extension. Seed không reset legacy; production assignment không suy từ seed demo. Regression legacy Receipt/QC kiểm stock/good cap trước thay behavior. Latest deploy chưa xác minh và A01–A40 chưa thử ⇒ task BE ở trạng thái **Chờ contract/triển khai/API acceptance**, chưa “Hoàn tất tích hợp”.

**Change log v1:** bàn giao lần đầu từ FE Session311; gồm22 yêu cầu,40case livepending,§5 confirmation riêng và tin nhắn4dòng. Bản chuẩn bị chưa xác nhận PO gửi/BE nhận; không đánh dấu live pass từ tests mock.

## §4 — Hợp đồng lỗi mong đợi

**[Đề xuất BE]** Mã dưới đây là wire đề xuất để mapping, không khẳng định server đang dùng. Mock có short codes riêng QUANTITY/STATE/INPUT_EXCEEDED..., không gửi nguyên short code thiếu nghĩa nếu BE đã có canonical code tương đương. FE phân nhánh `code`, không chỉ HTTP.

| Tình huống | HTTP | code đề xuất | Message/field trên FE | Giữ input | Retry |
|---|---|---|---|---|---|
| Thiếu/invalidUUID/date/qty>range/scale>6/all0result | 400 | VALIDATION_ERROR | Specific `lines[n].quantity`, result/time field | Có | Correct/newkey |
| Thiếu quyền/currentassignment | 403 | PERMISSION_DENIED | Không có quyền thao tác ở phạm vi hiện tại | Có | Không tựretry |
| ID ngoàiCompany/Plant hoặc không tồn tại | 404 | NOT_FOUND | Không tìm thấy nguồn hợp lệ | Có | Không tựretry |
| Expected WO/issue version stale | 409 | VERSION_CONFLICT | Dữ liệu thay đổi, tải mới/previewlại; currentrevision nếuauthorized | Có | Refetch/newkey |
| Keyreuse khácpayload/actor/resource | 409 | IDEMPOTENCY_CONFLICT | Không gửi lại payloadkhác cùngkey | Có | Correct/newkey |
| WO/op/issue terminal hoặc transition sai | 409 | INVALID_STATE | Trạng thái hiện tại không cho thao tác | Có | Refetch |
| Source-linked draft edits locked field | 409 | WORK_ORDER_SOURCE_LOCKED | Field cụ thể output/qty/warehouse/source | Có | Correct |
| Item/UOM/kho/BOM/Routing không hợp lệ | 409 | PRODUCTION_REFERENCE_INVALID | Source/field detail, không mấtinput | Có | Refreshref/correct |
| Mastercycle/invalid snapshot | 409 | BOM_CIRCULAR_REFERENCE / PRODUCTION_DESIGN_INVALID | BOM/Routing source cụ thể | Có | Sửareference/plan |
| Component kho chưa chọn | 400 | COMPONENT_WAREHOUSE_REQUIRED | componentWarehouses/componentLineId | Có | Correct |
| Không có lịchkhảthi | 409 | PRODUCTION_SCHEDULE_UNAVAILABLE | Calendar/WorkCenter reason; overlap chỉ warning khi cóschedule | Có | Correct/refetch |
| Readiness thiếu/RS source mất eligibility | 409 | MATERIAL_NOT_READY | Shortage/current eligibility projection | Có | Reserve/refetch |
| RS khácWO/khôngactive/qtyvượt own | 409 | MATERIAL_RESERVATION_INVALID | reservationId/line source | Có | Correct/refetch |
| Tồn eligible thiếu khi post/approve | 409 | INSUFFICIENT_AVAILABLE_STOCK | lines[n] + currenteligibleavailability | Có | Refetch, không auto-source substitute |
| Normal over-BOM | 409 | MATERIAL_ISSUE_OVER_BOM | Chuyển cấpbổsung/lýdo; không post | Có | Newpayload/newkey |
| Thiếu reason/reasoncode hoặc reason ngoài5–2000char đề xuất | 400 | REASON_REQUIRED / VALIDATION_ERROR | reason/overrideReason/reasonCode | Có | Correct |
| Selfapprove khôngoverride | 403 | SELF_APPROVAL_DENIED | Cần người duyệt khác hoặc quyền override | Có | Không tựretry |
| Retry/sourceexecution không hợp lệ | 409 | REWORK_SOURCE_INVALID | sourceExecutionId | Có | Correct |
| Thiếu input/consumption evidence legacy | 409 | PRODUCTION_INPUT_UNKNOWN / MATERIAL_CONSUMPTION_UNKNOWN | Chưa có bằng chứng để xác định cap/hoàn | Có | Không suy/auto retry |
| Chưa có posted issue | 409 | MATERIAL_ISSUE_REQUIRED | Cấp vật tư trước khi xác nhận input | Có | Postissuethencorrect |
| Predecessor chưa kết thúc | 409 | OPERATION_SEQUENCE_INVALID | Công đoạn trước phải hoàn thành | Có | Refetch |
| Input/result vượt fresh/transfer pool | 409 | OPERATION_INPUT_EXCEEDED | quantity/results+authoritativecap | Có | Correct/refetch |
| Retry vượt source pending | 409 | REWORK_QUANTITY_EXCEEDED | results/sourcepending | Có | Correct/refetch |
| Consumption vượt ownlineunused | 409 | MATERIAL_CONSUMPTION_EXCEEDED | consumption[n].quantity | Có | Correct/refetch |
| Actualtimeinvalid/future | 400 | PRODUCTION_TIME_INVALID | actualStartedAt/actualEndedAt | Có | Correct |
| Finish còn input/rework/pendingissue | 409 | PRODUCTION_PENDING_WORK | Operation/pendingdocument cụ thể | Có | Resolve/refetch |
| Complete thiếu Managerpermission | 403 | WORK_ORDER_SHORTFALL_APPROVAL_REQUIRED | Quản lý xác nhận thiếu sản lượng | Có | Managerdecision |
| Hủy sau phát sinh | 409 | WORK_ORDER_HAS_ACTIVITY | Issue/execution/receipt/pending links theo quyền | Có | Không auto reverse |
| Return vượt issued−consumed−returned | 409 | MATERIAL_RETURN_EXCEEDED | lines[n].quantity/returnable cap | Có | Correct/refetch |
| OriginalreturnsourceHOLD/REJECTED/expired/inactive | 409 | MATERIAL_RETURN_SOURCE_INELIGIBLE | Originalwarehouse/lot/source reason | Có | Không genericreceive/QCbypass |
| Throttling | 429 | RATE_LIMITED | Retry-after server | Có | Samekey/backoff |
| DB/transaction/systemfailed | 500/503 | INTERNAL_ERROR / SERVICE_UNAVAILABLE | Lỗi cụ thể +trace đểtra; không optimistic partial success | Có | Transient samekey/refetch outcome |

**Ví dụ đề xuất — error400**, không phải actualresponse; server gắn X-Trace-Id header thật khi chạy, không đặt UUID fixture thaytrace:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Quantity must be positive with at most 6 decimal places",
  "errors": [{ "field": "lines[0].quantity", "message": "Maximum scale is 6" }]
}
```

Response409 error có thể thêm scoped cap/version ở `result` nếu contract công bố; FE không bắt buộc tự tính stock từ error. Errors optional cho business errors nhưng code/message required; never expose anothertenant source/currentcost. Networktimeoutkhôngcóresponse/trace ⇒ ghi rõ thiếu, retry samekey và queryoutcome, không inventcode/serverfailurecause. Auth401 theo contract auth hiện có, GĐ8 không đổi refresh/sessionpolicy.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; trả lời Có/Không hoặc chọn A/B và cung cấp mapping nếu khác. Owner chốt contract: BE phụ trách; policy nghiệp vụ cần PO xác nhận, cá nhân chưa chỉ định. **Tất cả quyết định/ngày: chưa chốt.** “CHẶN” là chặn adapter/API acceptance mục đó; mọi Q không chặn FE/mock code giai đoạn sau.

1. **Q1:** Dùng nguyên mapping mã quyền ở §1.4 được không? — FE nghiêng về Có, giữ quyền action và ba READ/RETURN mới riêng — **CHẶN authorization adapter**.
2. **Q2:** Server bắt buộc READ+action theo profile UI cho return và các workspace tương ứng được không? — FE nghiêng về Có cho quyền thao tác qua giao diện, business projection không cần full master READ; mock service ngoài return chủ yếu kiểm action — **CHẶN permission acceptance**.
3. **Q3:** Self-approve issue chỉ cho phép khi có cả ISSUE_APPROVE và ISSUE_OVERRIDE được không? — FE nghiêng về Có để tường minh, không dùng Admin role name bypass — **CHẶN approval acceptance**.
4. **Q4:** Complete WO thiếu sản lượng dùng WORK_ORDER_MANAGE + MATERIAL_ISSUE_APPROVE như demo được không? — FE nghiêng về Có để giữ mapping hiện tại; nếu cần authority shortfall riêng BE trả mã cụ thể trước tích hợp — **CHẶN completion authority**.
5. **Q5:** Manual WO capture design ở plan, MAKE WO giữ design đã chụp từ GĐ6 được không? — FE nghiêng về Có để không overwrite nguồn sau master đổi — **CHẶN snapshot lifecycle adapter**.
6. **Q6:** Nhu cầu nhiều BOM lines cùng Item/UOM được gộp thành một component WO stable ID để xét gross/warehouse được không? — FE nghiêng về Có theo mock; giữ source BOM-line mapping nếu BE giữ nhiều dòng, phải cung cấp quy tắc tổng cap — **CHẶN component identity/aggregation**.
7. **Q7:** Source-linked DRAFT khóa output warehouse được không? — FE nghiêng về Có để bảo vệ nguồn/kho netting trong lúc GĐ6 Q16 chưa chốt; hiện form/service mock vẫn cho sửa kho output, không claim lock này đã chạy, các khóa Item/lượng/component warehouses đã có — **CHẶN source edit/migration**, cần đồng bộ FE sau chốt.
8. **Q8:** Supplemental issue được chọn kho ACTIVE khác kho component đã reserve nếu cùng Plant được không? — FE nghiêng về Có theo mock source issue tự chọn, nhưng normal/auto reserve luôn đúng kho component — **CHẶN supplemental source mapping**.
9. **Q9:** Metadata reason/source giữ per-line existing wire, thêm supplemental/expectedVersion ở cấp phiếu được không? — FE nghiêng về Có để compatible batch API; mock dùng reason chung phiếu, không tuyên bố existing wire đã có flag — **CHẶN batch payload adapter**.
10. **Q10:** Pending creation phải kiểm nguồn đủ stock nhưng không reserve/hold stock cho yêu cầu được không? — FE nghiêng về Có; approve vẫn kiểm lại vì tồn có thể đổi — **CHẶN pending/approval stock policy**.
11. **Q11:** Gross cấp để xét vượt BOM không trừ quantity hoàn được không? — FE nghiêng về Có để không lặp cấp bổ sung bằng hoàn rồi cấp lại — **CHẶN gross issue semantics**.
12. **Q12:** Auto reserve thiếu trả partial hợp lệ và chuyển BLOCKED thay vì rollback toàn bộ được không? — FE nghiêng về Có; thiếu readiness khác lỗi batch issue — **CHẶN reservation result contract**.
13. **Q13:** Mapping mock reservation ISSUED sang API CONSUMED được không? — FE nghiêng về Có nếu nghĩa là đã issue hết; chỉ phần chưa cấp ACTIVE còn giữ availability — **CHẶN reservation state mapping**.
14. **Q14:** First input cần ít nhất một POSTED issue nhưng output cap chỉ từ planned xác nhận, không từ material quantity được không? — FE nghiêng về Có, không chia KG thành PCS ngầm — **CHẶN operation input adapter**.
15. **Q15:** GĐ8 dùng sequential routing, downstream chỉ nhận Good của predecessor COMPLETED được không? — FE nghiêng về Có, parallel/branch routing ngoài scope — **CHẶN transfer/sequence acceptance**.
16. **Q16:** Downstream được finish zero khi predecessor đã kết thúc với Good0 được không? — FE nghiêng về Có để all-scrap không bị kẹt bởi input>0 — **CHẶN all-scrap completion**.
17. **Q17:** REWORK supplemental source chỉ cần historical Rework>0, không bắt pending pool còn>0 được không? — FE nghiêng về Có theo mock, extra material và retry pool là hai nghiệp vụ khác nhau — **CHẶN supplemental source validation**.
18. **Q18:** Consumption array rỗng được chấp nhận khi lần execution không dùng thêm vật tư được không? — FE nghiêng về Có; không tự suy Good×BOM hoặc debit toàn issue — **CHẶN consumption adapter**.
19. **Q19:** WIP_READ được đọc minimal issue/execution/return quantities/source cần cho tabs mà không có full module READ được không? — FE nghiêng về Có, action/full unrelated history vẫn guard riêng; minimal source return values chỉ khi có VARIANCE_READ, không suy bằng0 từ việc thiếu full return history — **CHẶN WIP projection authorization**.
20. **Q20:** Server cung cấp effectiveCoverageQuantity riêng, giữ historical allocation khi WO complete thiếu được không? — FE nghiêng về Có, server resolve phân bổ thực theo SO/source; FE không tự phân chia hụt hoặc coi planned đã đáp ứng — **CHẶN SO/MRP coverage adapter**, bổ sung GĐ6 Q10/Q16/Q26/Q27, không chốt chúng thay BE.
21. **Q21:** Cancel unstarted WO giải phóng active MAKE conversion và SO quota đúng một lần được không? — FE nghiêng về Có, preserve inactive trace; không restore delivered/fulfilled hoặc tự hủy SO — **CHẶN cancellation quota contract**, bổ sung GĐ6 cancel semantics.
22. **Q22:** Legacy thiếu input/consumption phải giữ unknown và chặn unsafe action cho đến có evidence migration được không? — FE nghiêng về Có, không backfill0/issued từ aggregate — **CHẶN migration/WIP acceptance**.
23. **Q23:** Complete WO cho phép còn unused material, người dùng có thể return sau COMPLETED được không? — FE nghiêng về Có theo mock; pending issue/input/rework vẫn chặn, no auto receipt/close — **CHẶN completion/return states**.
24. **Q24:** Quantity input max999999999/scale6 như demo được không? — FE nghiêng về Có để guard đồng nhất; BE báo range NUMERIC thực nếu khác — **CHẶN numeric adapter**.
25. **Q25:** Manual component requirement cộng exact theo Item rồi ceiling6 được không? — FE nghiêng về Có như mock để không thiếu lượng cần; không tự chốt rounding MAKE-before-child GĐ6 Q28 — **CHẶN requirement rounding acceptance**.
26. **Q26:** Return value dùng proportional original issue value và HALF_UP6 từng posting được không? — FE nghiêng về Có để khớp demo; residual được hỏi riêng Q37 — **CHẶN value/rounding acceptance**.
27. **Q27:** Calendar lúc release là current, capacity đọc captured committed context được không? — FE nghiêng về Có, immutable Routing giữ nguyên, không âm thầm mutate schedule lịch sử — **CHẶN schedule/capacity acceptance**.
28. **Q28:** Idempotent result giữ tối thiểu24h và kiểm current auth trước replay được không? — FE nghiêng về Có; payload khác conflict, không replay cost cho người mất quyền — **CHẶN retry rollout**.
29. **Q29:** Mọi response thành công/lỗi có X-Trace-Id server được không? — FE nghiêng về Có để lấy evidence đúng run — **CHẶN live diagnostics**.
30. **Q30:** ID ngoài Company/Plant trả404 NOT_FOUND, thiếu action trong đúng scope trả403 được không? — FE nghiêng về Có để không lộ record tồn tại — **CHẶN direct-ID mapping**.
31. **Q31:** Actual endedAt được cho lệch tối đa60giây so với server now như demo được không? — FE nghiêng về Có cho clock skew tablet; invalid/end<start vẫn reject — **CHẶN time validation acceptance**.
32. **Q32:** Business date và lot expiry dùng timezone Plant do server resolve được không? — FE nghiêng về Có; demo Asia/Ho_Chi_Minh, expiry instant ≤now bị loại; không phụ thuộc browser timezone — **CHẶN calendar/expiry adapter**.
33. **Q33:** GĐ8 reason bắt buộc trim length5–2000chars được không? — FE nghiêng về Có theo mock; existing reject chỉ yêu cầu không rỗng, BE báo mapping nếu giữ giới hạn cũ — **CHẶN reason validation adapter**.
34. **Q34:** Demo seed chỉ thêm ba quyền mới cho Admin/Manager hiện có được không? — FE nghiêng về Có, không migration mọi custom role hoặc Operator ngầm — **KHÔNG CHẶN FE code tiếp**, chặn seed rollout nếu chưa duyệt.
35. **Q35:** WIP quantity known nhưng value unknown được trả value=null không? — FE nghiêng về Có, unknown khác0; field giá với người thiếu quyền vẫn omit theo R19 — **CHẶN WIP/value nullability**.
36. **Q36:** POSTED issue/execution/return giữ bất biến và reversal/correction ngoài GĐ8 được không? — FE nghiêng về Có, không cung cấp DELETE/PATCH làm mất trace hoặc stock effect — **KHÔNG CHẶN FE code tiếp**, chặn rollout sửa sai nếu muốn thêm scope.
37. **Q37:** Lần hoàn cuối làm hết phần unused được điều chỉnh phần dư để original issue value = consumed value + posted returns value được không? — FE nghiêng về Có khi source value known, server trả authoritative residual/value và không để giá trị âm; mock chưa có reconciliation phần dư cho mọi tỷ lệ, cần đồng bộ sau chốt — **CHẶN fractional value acceptance**.

Các Q là chốt contract, không defect. GĐ6 Q28 là MAKE rounding, GĐ7 Q28 là lot uniqueness, GĐ8 Q28 là idempotency; luôn trả lời kèm giai đoạn để tránh nhầm số. Mapping error/HTTP/envelope/path/versioned response khác đề xuất cần BE cung cấp bảng thay thế/OpenAPI trong cùng phản hồi, không yêu cầu sửa API đang đủ nghiệp vụ.

## §6 — FE đã tự xử lý

**[Đã kiểm chứng FE/mock]**

- Canonical WO GĐ6 và shared persistent ledgers; không tạo WO store thứ hai, preserve records GĐ7. Legacy issue/execution tham gia persistence, actual operation counters GĐ8 không bị tính lại round3 từ legacy. Source audit: output warehouse của MAKE DRAFT vẫn editable; intended lock là Q7 đề xuất, không sửa code trong phiên tài liệu.
- Decimal scale6 input, quantity/range/date/reference/snapshot/source guards; duplicates consumption/return được cộng trước cap. Manual requirement ceiling6; source MAKE requirement giữ nguyên, không giải quyết GĐ6 rounding đa cấp bằng giả định.
- Partial own-warehouse reservations/current readiness; batch issue pending/post/decision atomic, supplemental reason, current context/scope/permission/version sau lock và trước replay; failure rollback WO+stock+value+ledger+key trong mock.
- Input/predecessor transfer, fresh/retry pools, historical/pending Rework tách biệt; material consumption theo source/UOM, final Good chỉ operation cuối. Manual actual time có validation, no automatic MES/stock/FG output.
- Safe return theo confirmed unused/original eligible source và attributable source cost, legacy unknown blocked; giải phóng unissued khác return; cancelled quota trace một lần. Cost columns theo variance permission, scope-stock projection không trả data Plant khác.
- READ route/action guards và current context thay Plant, key retry payload cố định, busy controls, confirm mutations, errors giữ input; React Query invalidate/refetch sau shared storage writes và thay context, không coi cached UI permission là server authorization.
- Detail panel phải giữ list/filter, multi-line form rộng, compact input/reason dialogs, skeleton/empty/error states, business labels VI/EN. Sidebar WIP/return guard mới; migration quyền demo không cấp arbitrary role.
- Canonical committed schedule đọc capacity trực tiếp; terminal WO supply actual Good cap, saved MRP immutable; SO coverage policy còn pending được báo thay vì tự sửa history.
- Existing WO/issue/execution API adapter giữ nguyên; phần mở rộng chưa contract unavailable, no fallback/mock cap trong API. Không gửi expectedVersion/input/retry/consumption fields đề xuất trước OpenAPI và phê duyệt adapter.

**Giới hạn cần rà khi BE trả contract:** mock locks/localStorage không thay DB isolation/multi-user authorization; source ID/component aggregation/warehouse choices, revision mapping, WIP projection/cost redaction, legacy migration, exact6/value residual, source cancellation quota, schedule timezone/clockskew, server totals/candidates và receipt cap. Mock requirement trực tiếp không chứng minh GĐ6 multi-level rounding; source cost trace không full cost allocation. Chưa chạy A01–A40/live/latest-version check trong phiên này, không đóng task BE. Chỉ gỡ lớp phòng vệ khi có server evidence tương đương.

**Read projection cần đối chiếu Q19:** mock `returnedValue` cộng từ history return được cấp RETURN_READ. Profile WIP_READ + VARIANCE_READ nhưng không RETURN_READ chưa được nghiệm thu riêng cho các cột giá trị sau hoàn. BE cần trả authoritative minimal returned/unused value theo quyền variance, không coi thiếu full return history là tổng giá trị hoàn0; FE đồng bộ projection sau contract.

## §7 — Tham chiếu

Các file trước đã chuẩn bị; **chưa xác nhận PO đã gửi hoặc BE đã nhận**. Chỉ tên file và mục đích; nội dung để triển khai GĐ8 đã tự chứa phía trên, không yêu cầu BE mở source FE/máy FE.

- `BE_HANDOFF_GD_01_2026-10-09.md` — Auth/current assignment/scope, đối chiếu bản PO thực gửi.
- `FE_HANDOFF_GD_02_2026-10-12.md` — Item/UOM/Plant/Warehouse/master lifecycle và settings.
- `FE_HANDOFF_GD_04_2026-10-12.md` — Eligible stock/reservations/quantity/value ledger và valuation.
- `FE_HANDOFF_GD_05_2026-10-13.md` — BOM/Routing/snapshot/calendar/capacity, outputQuantity/scrap.
- `FE_HANDOFF_GD_06_2026-10-14.md` — Canonical MAKE WO/source/allocation/quota, MRP supply và Q10/Q16/Q26/Q27/Q28 chưa chốt.
- `FE_HANDOFF_GD_07_2026-10-14.md` — Mua hàng/HOLD/QC, lot/value/eligibility; GĐ8 không bypass QC sources.
- `FE_HANDOFF_GD_08_MESSAGE_2026-10-14.md` — Tin nhắn4dòng PO gửi kèm file này.

### Checklist phát hành v1

- Đủ §0–§7, kết luận3dòng/P1 đúng extension API, Ngoài phạm vi rõ, đủ22 màn hình mới/sửa.
- Không defect live: năm mảnh thiếu/BE latestdeploy chưa xác minh nêu riêng, không invent actual request/response/trace/time/record; mock và proposed UUID khác nhau.
- R01–R22 kiểm được; field5cột; endpointfullmethod/body/result/page/permissions/replay, nullability/UUID/Decimal/time/revisions/migration rõ; existing source path khác proposed extension. Output warehouse source lock chưa enforce được tách explicit khỏi fact; fractional return residual pendingQ37.
- JSON ở codeblock/copy được, token che, không fake trace. F1 100−21+3=82KG; value1000−210+30=820VND; issued210=consumed180+returned30; output9 không sum18; F2/F3/F4 riêng.
- Lỗi code/HTTP/field/input/retry; §5 riêng “không cần code”,37 quyết định đóng/FE preference/mức chặn/ownerdatepending; source/coverage/rounding gates trước độc lập.
- A01–A40 live chưa thử, evidence cùngrun; FE/mock checks không thành DB acceptance. §6 guards/giới hạn, §7 không repo/localpath dependencies.
- Saved localv1 +message4dòng; chưa gửi ngoài/commit/deploy/appchange; chỉ đóng BE task sau FE liveacceptance, chưa đạt giữ task và bản_v2 khi cần.

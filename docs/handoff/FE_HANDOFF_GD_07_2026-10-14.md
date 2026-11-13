# Giai đoạn 7 — Bàn giao Backend: Đơn mua hàng, Nhận hàng và QC

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock GĐ7 đã hoàn thành; cần BE cung cấp contract tương đương cho PO, nhận mua từng phần vào HOLD, QC theo lượng và nguồn cung PO cho MRP.
- Ảnh hưởng: người mua hàng, thủ kho, QC và người lập kế hoạch dùng được demo; ba màn hình mua hàng chưa được tích hợp với API đã xác minh.
- Mức ưu tiên: P1 — chặn luồng mua hàng ở chế độ API; các module khác và tiến độ FE/mock tiếp theo vẫn chạy.

**Bản:** v1 · **Ngày dự kiến gửi:** 14/10/2026 · **Mốc FE:** Session 308, 14/10/2026 · **Bàn giao:** Session 309.
**PO:** đã duyệt phương án FE bằng “có”; yêu cầu “tạo tài liệu cho BE” sau delivery được ghi nhận chuyển sang bàn giao, không phải nghiệm thu Backend.
**FE owner / BE owner cá nhân:** chưa được chỉ định. **Trạng thái:** đã chuẩn bị để PO gửi; chưa gửi ngoài, chưa xác nhận OpenAPI/deploy hoặc nghiệm thu live.
Nếu gửi ngày khác, đổi ngày và tên trước lần gửi đầu. Sửa sau khi gửi phải phát hành `_v2`, `_v3`, tiếp tục cùng task.

Nhãn áp dụng cho cả tài liệu:

- **[Đã kiểm chứng FE]**: source/test/browser mock; không chứng minh API, DB hoặc transaction BE.
- **[Đề xuất BE]**: endpoint, wire field, permission mapping, policy và mã lỗi cần BE thống nhất; API tương đương đã có thì cung cấp mapping/OpenAPI, không xây trùng.
- **[Giả định]**: điều chưa được BE xác nhận; không coi là contract deploy đã có.
- **[Chưa kiểm chứng live]**: không gọi BE purchasing trong phiên tạo tài liệu này. Tất cả JSON dưới đây là **ví dụ đề xuất**, UUID minh họa không phải record server đã thử.

## §1 — Phạm vi

### 1.1 Màn hình mới và đã sửa

| Loại | Màn hình | Dữ liệu/hành vi cần Backend |
|---|---|---|
| Mới | Danh sách Đơn mua hàng | Search mã/Supplier/tham chiếu, status/kho/ngày đặt, phân trang; nháp thủ công và nháp BUY GĐ6 cùng một nguồn |
| Mới | Tạo/sửa Đơn mua hàng | Một Supplier/Company/Plant/kho nhận, 1–500 dòng Item/UOM/lượng/giá VND/ngày dự kiến; nguồn MRP được khóa |
| Mới/nâng cấp nháp GĐ6 | Chi tiết Đơn mua hàng | Tabs dòng hàng, các phiếu nhận, QC, nguồn/lịch sử; confirm/cancel/close theo state/version/quyền |
| Mới | Danh sách Nhận hàng mua | Search phiếu/PO/Supplier, status/kho/ngày nhận, phân trang |
| Mới | Tạo/sửa phiếu nhận | Chọn PO hợp lệ, kế thừa Supplier/kho/Item/UOM/giá; lượng từng đợt, tách nhiều lô cho cùng dòng PO |
| Mới | Chi tiết phiếu nhận | Snapshot, lượng nhận/đạt/loại/chờ, movement, lô gốc/lô con, lịch sử quyết định; POSTED chỉ đọc |
| Mới | Hàng chờ QC mua | Dòng POSTED còn HOLD; tìm PO/phiếu/Item/lô, kho/ngày nhận; không lộ giá cho QC-only |
| Mới | Ghi kết quả QC | Một dòng nhận: lượng đạt/loại/lý do/còn chờ; xác nhận rồi lưu quyết định bất biến |
| Sửa | MRP, kết quả/nguồn và BUY conversion | PO đã xác nhận tham gia supply đúng kho/ngày/lượng còn mở; source revisions/stale; BUY cần quyền mua |
| Sửa | Tồn kho, chi tiết lô, lịch sử biến động | Quality cho hàng không theo lô, child lineage cho tracked; nguồn PO/GR/QC và chống bypass quarantine |
| Sửa | Sidebar/quyền | Ba workspace mua hàng theo permission; không dùng mã kỹ thuật làm tên nghiệp vụ UI |

FE dùng danh sách có bộ lọc, form nhiều dòng rộng, panel chi tiết bên phải có header cố định/body cuộn, dialog QC/xác nhận giữa màn hình; VI/EN và desktop/tablet. BE không cần dựng UI; mô tả này xác định projection và trạng thái cần trả.

### 1.2 Trong phạm vi / Ngoài phạm vi / Môi trường

**Trong phạm vi:** PO thủ công và kế thừa nháp BUY GĐ6; save DRAFT/confirm/cancel/close remainder; giá cơ bản VND; Goods Receipt DRAFT/save/cancel/post trực tiếp HOLD; partial receiving; partial QC accepted/rejected/pending cho tracked/non-tracked; tồn/value/source lineage; open PO supply vào MRP; authorization/concurrency/idempotency/audit/migration.

**Ngoài phạm vi:** PR/RFQ, supplier portal, vendor bill/payment, thuế/chiết khấu/phí/FX/landed cost; return/refund, correction/reversal của receipt/QC POSTED; nhận dư/tolerance, PO amendment sau confirm; XLSX PO; serial, cross-Plant sourcing; tự tạo mua bù hoặc release WO. Không đổi production receipt/QC thành purchasing endpoint; nâng cấp production QC một phần ở GĐ9. Không sửa WO/issue/execution/WIP GĐ8 hoặc quyết định mở GĐ6 về rounding/metrics/warehouse.

**Môi trường [Đã kiểm chứng FE]:** dev/demo mock cục bộ, Session 308 ngày 14/10/2026, Asia/Saigon; build production và browser desktop/tablet VI/EN. Không có commit/tag phát hành riêng. Numeric ID, local command key và mock movement trace không phải UUID/request/trace server.

**Existing API:** chưa có purchasing adapter/contract được xác minh trong FE GĐ7; không fetch OpenAPI hoặc xác minh bản BE đang deploy trong phiên này. Không suy ra BE chưa có API từ việc FE chưa có adapter. SO/MRP/WO và Inventory adapters hiện có giữ nguyên; bảng §3 là đề xuất mapping/extension, không xác nhận deploy.

### 1.3 Luồng nghiệp vụ tự chứa

**[Đã kiểm chứng FE; đề xuất BE triển khai tương đương sau chốt §5]**

1. Chọn Plant được cấp quyền. Tạo PO thủ công, hoặc mở nháp BUY đã tạo từ MRP GĐ6. Cùng một PO canonical, không sao chép sang chứng từ khác. Mock giữ run/requirement và lịch sử chuyển đổi; stable suggestion/conversion UUID và source revision trong DTO server là đề xuất, không phải field đã có trong mock. ID dòng ổn định.
2. Một Supplier ACTIVE và một kho ACTIVE thuộc Plant cho cả PO. Item mua hiện cho RAW_MATERIAL, CONSUMABLE, MRO, WIP, SEMI_FINISHED; UOM lấy từ Item ACTIVE. Không yêu cầu BOM/Routing để mua.
3. PO có 1–500 dòng. Quantity > 0, tối đa 999999999, scale ≤ 6. Đơn giá ≥ 0, cùng range/scale trong mock. Giá có thể thiếu ở DRAFT; confirm bắt buộc từng dòng có giá, explicit 0 hợp lệ. Ngày chuẩn YYYY-MM-DD, dueDate >= orderDate.
4. Nháp thủ công sửa được Supplier/kho/Item/lượng/giá/ngày. Nháp MRP khóa Item/UOM/lượng/kho/cấu trúc dòng và nguồn; Supplier/giá/ngày dự kiến/tham chiếu/ghi chú còn sửa được. Không cho biến nháp đã chiếm quota thành lượng tùy ý.
5. Confirm DRAFT sau kiểm lại master/scope/version/giá. PO đã confirm bất biến về nội dung trong GĐ7. Hủy DRAFT hoặc CONFIRMED khi chưa nhận và có lý do; close CONFIRMED/PARTIALLY_RECEIVED còn mở có lý do. Phiếu nhận DRAFT treo chặn cancel/close PO.
6. Tạo Receipt DRAFT từ PO CONFIRMED/PARTIALLY_RECEIVED. Server resolve Supplier/kho/Item/UOM/giá, không tin client gửi lại snapshot. Một dòng PO có thể tách nhiều dòng receipt để nhận nhiều lô. Từng quantity dương; tổng các dòng cùng poLineId trong một phiếu phải ≤ lượng PO còn mở. Các phiếu DRAFT chưa chiếm tồn hoặc received, post kiểm lại dưới lock.
7. Ngày nhận >= orderDate, <= ngày nghiệp vụ hiện tại. LOT_TRACKED bắt buộc mã lô; NON_TRACKED không tạo lô giả và không nhận lotCode/expiry. Mock expiry tùy chọn; nếu có phải hợp lệ, sau ngày nhận và chưa hết hạn lúc post. Mandatory expiry policy cần §5 xác nhận.
8. Lưu/sửa DRAFT không đổi tồn. POST trực tiếp, không có approval riêng, atomically tạo HOLD/quality bucket, movement/value, tăng received/giảm open PO và lưu idempotent result. POSTED không sửa/hủy trong scope này; CANCELLED DRAFT không post tồn.
9. QC một dòng POSTED còn pending: accepted ≥ 0, rejected ≥ 0, tổng > 0 và ≤ pending, scale ≤ 6; rejected > 0 cần reason, reason tối đa 500 ký tự. Có thể QC nhiều lần. Lô hết hạn không được accept vào AVAILABLE.
10. Tracked QC tạo child lot AVAILABLE/REJECTED có parent/source/expiry; giảm lượng parent HOLD, giữ phần chưa kiểm. Non-tracked giữ HOLD/REJECTED theo receipt line, chuyển accepted sang normal AVAILABLE pool; lịch sử quyết định vẫn truy được nguồn sau khi accepted gộp pool.
11. QC không tăng/giảm tổng On Hand/value, không thay received PO, không tự mua bù. Hàng loại vẫn là tồn REJECTED, không đổi Receipt thành “chưa post” hoặc mở lại PO. Có thể PO FULLY_RECEIVED mà QC chưa xong.
12. Open PO CONFIRMED/PARTIALLY_RECEIVED tham gia MRP theo Item/UOM/kho/ngày và eligibility nguồn. Khi post, received HOLD giảm scheduled supply nhưng không tăng available. QC đạt mới thêm canonical stock đủ điều kiện một lần. Run cũ giữ snapshot; nguồn đổi làm conversion stale, cần tính lại.
13. Cancel PO nguồn giải phóng active conversion quota đúng một lần và giữ lịch sử. Close chỉ đóng phần chưa nhận, không giải phóng toàn conversion hoặc đảo stock. Không tự cascade SO/WO, không sửa snapshot nhu cầu cũ.

### 1.4 Quyền, scope, trạng thái

**[Đã kiểm chứng FE]** UI dùng READ để vào workspace, action dùng permission riêng. **[Đề xuất BE]** kiểm auth/current assignment/scope, record ownership và quyền ở server, kể cả truy cập ID trực tiếp và idempotent replay. Không dùng role name, union permission hoặc nút ẩn thay authorization. READ+action trong bảng là profile UI; policy server chốt Q1.

| Thao tác | State trước → sau | Permission FE | Scope / tác động |
|---|---|---|---|
| PO list/detail | Không đổi | PERM_PURCHASE_ORDER_READ | Company/Plant record; giá chỉ trong projection cho quyền đọc tương ứng |
| PO create/edit | Mới/DRAFT → DRAFT | READ + PERM_PURCHASE_ORDER_MANAGE | Current Plant; canonical source locks |
| PO confirm | DRAFT → CONFIRMED | READ + MANAGE của PO | Master/giá/version; tạo firm open supply, chưa tồn |
| PO cancel | DRAFT/CONFIRMED → CANCELLED, received=0 | READ + MANAGE của PO | Reason; pending DRAFT guard; release active conversion once |
| PO close | CONFIRMED/PARTIALLY_RECEIVED → CLOSED, open>0 | READ + MANAGE của PO | Reason; đóng lượng còn lại, không đảo tồn/QC |
| Receipt list/detail/candidates | Không đổi | PERM_GOODS_RECEIPT_READ | Cùng Plant; không bắt có PO_READ để làm nhận hàng, chỉ source projection tối thiểu |
| Receipt create/edit/cancel | Mới/DRAFT → DRAFT/CANCELLED | READ + PERM_GOODS_RECEIPT_MANAGE | Snapshot PO server-resolved; DRAFT chưa tác động tồn |
| Receipt post | DRAFT → POSTED | READ + PERM_GOODS_RECEIPT_POST | Kiểm open/version/current references; atomic HOLD/value/PO |
| QC queue/history | Không đổi | PERM_PURCHASE_QC_READ | Cùng Plant; projection không có giá/notes thương mại |
| QC decision | POSTED còn pending → POSTED, pending giảm | QC_READ + PERM_PURCHASE_QC_MANAGE | Partial accepted/rejected; immutable decision, receipt version tăng |
| BUY conversion GĐ6 | Requirement remaining → PO DRAFT | Planning authorization GĐ6 + PERM_SUPPLY_SUGGESTION_MANAGE + PERM_PURCHASE_ORDER_MANAGE | Không suy PO_MANAGE từ quyền MRP; nguồn/quota/source revisions cùng transaction |
| MRP supply | Không đổi | Planning authorization GĐ6 | Projection lượng/ngày/kho/source version, không lộ giá khi không PO_READ |

Mock chỉ migrate quyền mới lên hai role Admin/Manager seed, không cấp cho Operator hoặc mọi role cũ. Đây không phải chỉ thị tự động mở quyền production; BE/PO duyệt seed policy ở Q24. Self-receive/self-QC được demo cho cùng actor nhưng phân quyền nhận và QC độc lập; separation-of-duties chưa chốt.

## §2 — Bằng chứng tái hiện

**Không có — đây là bàn giao FE/mock và đề xuất contract, không báo lỗi Backend.**

| Mảnh chứng cứ live | Trạng thái |
|---|---|
| Request BE thật gồm method/path/header/body | Không có; chưa gọi purchasing API |
| Response BE thật, HTTP và envelope code | Không có; JSON §3–§4 là ví dụ đề xuất |
| X-Trace-Id response header | Không có; không lấy trace của mock movement thay thế |
| Environment BE + timestamp request cụ thể | Không có; chưa xác minh version/deploy, không tự điền giờ test |
| ID record BE từ cùng request | Không có; ID/UUID ví dụ không phải record server |

**[Đã kiểm chứng FE]** từ delivery Session 308, không chạy lại application checks ở phiên tài liệu: 99 files/506 tests hồi quy; suite cuối 7 files/87 tests; production build/TypeScript tạo 40 pages; lint và key parity VI/EN đạt.

Browser mock ngày 14/10/2026, Plant 1: PO-20261014-0001 (local id 1) gồm RM-003 40 KG × 10 VND và RM-001 0.300001 KG × 125 VND. GR-20261014-0001 (local id 1) nhận 25/0.100001, GR-20261014-0002 (local id 2) nhận 15/0.2. Non-tracked QC tổng đạt 35/loại 5; tracked đợt đầu đạt 0.05/loại 0.01/chờ 0.040001, đợt hai chờ 0.2. Đây là **bản ghi demo local**, không dùng chúng tra DB BE. Reload giữ dữ liệu, Plant 2 không lộ Plant 1; tablet 834×1112 và desktop 1440×900 VI/EN đạt.

```text
Mong đợi FE/mock: PO nhận đủ vẫn tách lượng đạt/loại/chờ; HOLD/REJECTED không reserve được.
Thực tế FE/mock: PO Đã nhận đủ; QC queue chỉ còn 0.040001 KG và 0.2 KG của RM-001; các case stock/quota/rollback/replay đạt trong test.
```

Không có BE defect được xác nhận. Khi live acceptance bắt đầu, ghi đủ năm mảnh từ cùng run/record/version; giữ X-Trace-Id, che credential. Không dùng kết quả mock để đánh dấu live đã đạt.

## §3 — Đầu ra mong đợi — Cần BE triển khai hoặc cung cấp mapping tương đương

Toàn bộ mục này **[Đề xuất BE]**, dựa trên FE đã duyệt. Quyết định chưa chốt ở §5 không tự thành yêu cầu deploy; BE trả mapping khác có cùng hành vi để FE align.

### 3.1 Yêu cầu kiểm được

“Chặn FE?” = chặn adapter/live phần tương ứng khi thiếu; không chặn FE/mock giai đoạn tiếp theo.

| # | Yêu cầu đúng/sai kiểm được | Endpoint/nhóm | Chặn FE? |
|---|---|---|---|
| R01 | List/detail trả canonical PO kể cả nháp BUY cũ, ID/source line ổn định, không tạo bản PO thứ hai | PO GET/migration | Có |
| R02 | Create/PATCH chỉ DRAFT, kiểm 1–500 dòng/Plant/master/quantity/date; explicit zero price hợp lệ, missing price không confirm | PO save/confirm | Có |
| R03 | Edit PO nguồn không đổi Item/UOM/quantity/kho/cấu trúc/source, server kiểm thay vì tin sourceLocked từ client | PO PATCH | Có |
| R04 | Cancel chỉ chưa nhận, reason bắt buộc; release conversion once, giữ trace và invalidates old run | PO cancel/conversion | Có |
| R05 | Close còn open có reason, tăng closedQuantity bằng remainder; giữ received/stock/QC, pending receipt DRAFT chặn cancel/close | PO close/cancel | Có |
| R06 | Receipt DRAFT kế thừa snapshot và chỉ DRAFT sửa/cancel; client không override Supplier/kho/Item/UOM/giá | Receipt save | Có |
| R07 | Post cộng dồn từng poLineId, lock open mới nhất, không vượt PO giữa nhiều phiếu/cùng phiếu/concurrent posts | Receipt post | Có |
| R08 | Post atomic receipt status/PO counters/stock HOLD/value/movements/source/idempotent result; một lỗi ở dòng sau rollback toàn bộ | Receipt post | Có |
| R09 | Tracking LOT cần unique code/expiry policy, NON_TRACKED dùng quality bucket không fake lot; tồn có nguồn receipt line | Receipt post/inventory | Có |
| R10 | QC accepted+rejected >0 và <=pending, rejects có reason; repeated partial QC và receipt version được kiểm dưới lock | QC command | Có |
| R11 | QC tracked có child parent/expiry/source; non-tracked accepted/REJECTED trace đúng; pending giảm chính xác | QC/inventory/lineage | Có |
| R12 | QC không đổi total On Hand/value/PO received; nhận đủ không tự QC xong; reject không mở lại PO | QC/PO/detail | Có |
| R13 | HOLD/REJECTED/hết hạn không được MRP/reserve/issue/transfer; generic count/status không bypass QC ownership | Inventory và MRP | Có |
| R14 | MRP đọc open PO trực tiếp theo kho/ngày/status; receipt HOLD giảm inbound, accepted stock tính một lần; run cũ immutable/stale | Planning extension | Có |
| R15 | GET list có server search/filter/sort/page, detail/queue có tên/code/UOM/source/metrics không cần gọi master mỗi dòng | GET projections | Có |
| R16 | Auth/scope/direct-ID/revoked assignment/replay được kiểm server; QC/planning-only không có giá trong payload | Tất cả API | Có |
| R17 | Same actor/scope/key/payload replay trả cùng kết quả; khác payload/key collision báo conflict; stale version không ghi phần nào | Commands | Có |
| R18 | Lịch sử immutable actor/time/reason/state, posting/decision IDs, source revisions và field lỗi/trace đủ để đối chiếu live | History/errors | Có |
| R19 | Giá trị nhận decimal và giá trị unknown được bảo toàn; rounding/precision không dùng floating JS hoặc tự gán legacy null=0 | Receipt/value projection | Có |
| R20 | Existing SO/MRP/WO/production-QC API không breaking change; migrate PO/quota/quality có đối soát và replay an toàn | Rollout/migration | Có |

### 3.2 Wire conventions và dữ liệu

Backend path trong bảng có đủ `/api` context; base origin do môi trường xác định, không kèm URL máy FE. Các path đều **đề xuất**, không phải route đã thử. ID wire = UUID string; numeric/mock IDs không gửi lên BE. Alias mock → wire: `purchaseId`→`purchaseOrderId`, `quantity` PO→`orderedQuantity`, `unitCost`→`unitPrice`, `accepted/rejected`→`acceptedQuantity/rejectedQuantity`, `version` command→`expectedVersion`.

Decimal quantity/price/value là **string**, không scientific notation hoặc JS number; quantity/price scale ≤6, range input mock ≤999999999. Server trả explicit authoritative sums, không yêu cầu FE đọc N pages để tự cộng received/QC. Money precision/rounding theo Q18, không đồng nhất scale quantity với giá trị ledger chưa chốt.

Date = YYYY-MM-DD không timezone; instant audit/postedAt = RFC3339 offset/Z. Backend resolve Plant timezone/day boundary, không tin giờ trình duyệt; mock hiện Asia/Ho_Chi_Minh. Optional field bỏ khỏi request nghĩa không cung cấp; trong PATCH các `lines` là full replacement DRAFT, omitted optional text giữ cũ, explicit empty text xóa. Quy tắc price null DRAFT và line replacement cần Q3/Q4 chốt. No null expectedVersion; create không có expectedVersion, command/update bắt buộc.

#### PO projection và input

| Field | Kiểu | Nullable | Nguồn/công thức đề xuất | Dùng ở đâu UI |
|---|---|---|---|---|
| id, code, version | UUID, string, integer>=0 | Không | Server ID/sequence/revision; không user-supplied code | List/detail/commands |
| companyId, plantId | UUID | Không | Auth + explicit Plant; record tenant ownership | Guard/filter |
| supplierId, supplierCode, supplierName | UUID, string, string | Không | Supplier ACTIVE; code/name snapshot | List/detail/form |
| warehouseId, warehouseCode, warehouseName | UUID, string, string | Không | Kho nhận ACTIVE thuộc Plant, snapshot | Filter/form/detail |
| status | PO enum §1.4 | Không | State machine authoritative | Badge/actions |
| currencyCode | string enum VND | Không | Basic valuation; không FX | Giá |
| orderDate | date | Không | Input validated | Filter/form |
| reference, notes | string | Không | Optional input, mặc định empty; đề xuất max200/max2000 theo Q23 | Form/detail/search reference |
| sourceLocked | boolean | Không | Server policy khi có MRP conversions | Khóa form; server vẫn enforce |
| lines[].id | UUID | Không khi đọc; omitted dòng mới | Stable server line ID, giữ source ID qua migration | Edit/source matching |
| lines[].itemId, itemCode, itemName, uomCode | UUID, string, string, string | Không | Current Item/UOM khi draft save; snapshot khi confirm | Bảng dòng |
| lines[].orderedQuantity, receivedQuantity, closedQuantity, openQuantity | decimal string | Không | Ordered; sum POSTED; remainder đã close; ordered−received−closed | Nhận/còn nhận/đóng |
| lines[].unitPrice | decimal string | Có chỉ DRAFT | Input explicit, không null→0; confirm bắt buộc có | Form/giá |
| lines[].expectedDate | date | Không | Input>=orderDate; ngày scheduled supply | Form/MRP |
| lines[].sources[] | array of source DTO | Không, [] cho manual | runId/requirementId UUID khi nguồn MRP; suggestionId/conversionId/sourceVersion có thể null cho legacy thiếu bằng chứng; convertedQuantity string | Trace/quota; không tự tạo identity lịch sử |
| createdAt, createdBy, updatedAt | instant, actor DTO/id, instant | Không | Server auth/audit | Lịch sử |
| history[] | event DTO array | Không | action/fromStatus/toStatus/actor/at/reason, có source nếu cần | Tab lịch sử |
| reason | string | Có | Cancel/close reason, không tự lấp cho record legacy | Detail |

Input create/PATCH: supplierId, warehouseId, orderDate, reference?, notes?, lines[].{id? on PATCH,itemId,orderedQuantity,unitPrice?,expectedDate}; companyId/plantId explicit lúc create; update scope lấy record. Server lấy UOM, currency/source/code/metrics/actor, reject hoặc bỏ field readonly theo contract chốt; không tin client sources. Nguồn MRP tạo qua conversion command GĐ6, không giả source bằng PO manual POST. Supplier/Item/reference phải thuộc tenant được phép, không chỉ kiểm ACTIVE rồi dùng ID Company khác.

Reference DTO tối thiểu gồm kind/id/code/name/status; Supplier/warehouse có Company/Plant phù hợp, Item có type/uomCode/trackingPolicy/expiryRequired nếu có policy server; không trả private supplier pricing/contact. Tập reference phân trang theo kind (thêm query kind=SUPPLIER/ITEM/UOM/WAREHOUSE), chuẩn page envelope; Receipt candidates trả header/line IDs, Item/UOM/tracking/openQuantity/date/version và unitPrice theo Q15, không đòi full PO detail/history/notes.

#### Receipt, QC và stock projections

| Field | Kiểu | Nullable | Nguồn/công thức đề xuất | Dùng ở đâu UI |
|---|---|---|---|---|
| receipt id, code, version, status | UUID, string, integer, DRAFT/POSTED/CANCELLED | Không | Server stable record, lifecycle | List/detail/commands |
| purchaseOrderId, purchaseOrderCode | UUID, string | Không | Một PO nguồn, immutable sau create | Source links |
| companyId, plantId, supplierId/name, warehouseId/name | UUID/string | Không | Resolve từ PO, không client override | List/filter/detail |
| receivedDate, reference, notes | date, string, string | Không | Date guard, optional text empty | Form/list/detail |
| postedAt | instant | Có trước POSTED | Server thời điểm commit, khác receivedDate | Detail |
| lines[].id, purchaseOrderLineId | UUID | Không khi đọc | Stable receipt line, source PO line same PO | QC target/split |
| lines[].itemId/code/name/uomCode, unitPrice | UUID/string/decimal string | Không trong GR projection đã được phép | Snapshot PO khi save; giá server-owned | Bảng nhận; QC DTO bỏ giá |
| lines[].receivedQuantity, acceptedQuantity, rejectedQuantity, pendingQuantity | decimal string | Không | Received; sums immutable QC; pending=received−accepted−rejected cho POSTED | Detail/QC queue |
| lines[].trackingPolicy | LOT_TRACKED/NON_TRACKED | Không | Snapshot Item tracking | Hiện trường lô |
| lines[].lotId, lotCode, parentLotId | UUID/string/UUID | Có khi non-tracked/chưa post/no parent | Posted HOLD lot/child lineage; DRAFT LOT lotCode input có | Detail/trace |
| lines[].expiresOn | date | Có khi policy cho phép | Lot expiry date, child kế thừa; expiry instant mapping Q17 | Form/trace |
| lines[].qualityBucketId | UUID | Có khi tracked/chưa post | Server non-tracked quarantine owner | Trace không giả lot |
| lines[].movementIds | UUID array | Không, [] trước post | Receive posting events | Biến động |
| quality decisions id, receiptId, receiptLineId | UUID | Không | Immutable QC event scoped tới receipt | Lịch sử |
| decision acceptedQuantity/rejectedQuantity/reason/actor/at | string/string/string/actor/instant | Không | Validated input; actor/time server; reason empty nếu all-good | QC lịch sử |
| decision childLots[] | array | Không, [] non-tracked | id/code/parentLotId/status/quantity/expiresOn; tạo theo Q16 | Child genealogy |
| decision movementIds, receiptVersion | UUID array, integer | Không | Quality audit + revision sau decision | Refetch/retry |
| inventory onHand/reserved/available/hold/rejected | decimal string | Không | Shared canonical Item+Warehouse pool, không aggregation client | Kho/MRP |
| inventory value/averageUnitCost | decimal string | Có | Unknown legacy giữ null; value/QC rules §3.4 | Giá trị tồn |
| inventory source references | typed DTO | Có nếu legacy thiếu bằng chứng | PO/GR/line/QC/parent and lineage from server | Kho/lô/biến động |

POST/PATCH Receipt input chỉ purchaseOrderId (create), receivedDate, reference?, notes?, lines[].{id? on PATCH,purchaseOrderLineId,receivedQuantity,lotCode?,expiresOn?}; không gửi Supplier/kho/price/quality/stock metrics. Giữ receipt-line IDs khi edit theo Q3; chưa POSTED không hiển thị “pending QC” như tồn HOLD đã có, queue chỉ POSTED.

QC input `{expectedVersion,acceptedQuantity,rejectedQuantity,reason}` theo receipt revision. QC response có decision và receiptVersion/pending mới. Detail quyền GR_READ có thể có lịch sử QC tối thiểu; QC-only không đòi GR_READ hoặc PO_READ để quyết định; link tới workspace đầy đủ chỉ khi có quyền riêng. Price projection của GR và policy master-minimal chốt Q14–Q15.

### 3.3 Endpoint đề xuất

Các mutate nhận `Idempotency-Key: <UUIDv4>`; actor từ auth, không body. Auth/refresh theo contract GĐ1, không redefine ở đây. Explicit plant query/body phải khớp X-Plant-Id nếu header được dùng; aggregate `{id}` resolve scope từ record, không tự gắn global Plant header tới endpoint không khai báo Plant.

| Method · path đề xuất | Query/body | Success result | Phân trang/search | Quyền/scope | Idempotency |
|---|---|---|---|---|---|
| GET /api/v1/purchase-orders | companyId,plantId,search?,status?,warehouseId?,orderDateFrom?,orderDateTo?,page,size,sort | Page<PO summary>, đầy đủ status/version/names | Có; date inclusive, orderDate, default createdAt desc/id tie-break | PO_READ, explicit scope | Không |
| GET /api/v1/purchase-orders/{id} | id | PO detail + lines/history/source | Không; liên quan GR/QC dùng API riêng | PO_READ, record scope | Không |
| POST /api/v1/purchase-orders | §3.2 create + companyId,plantId | PO DRAFT detail | Không | PO_MANAGE, Q1 READ companion | Có |
| PATCH /api/v1/purchase-orders/{id} | expectedVersion + §3.2 full lines | PO DRAFT detail | Không | PO_MANAGE, record scope | Có |
| POST /api/v1/purchase-orders/{id}/confirm | expectedVersion | CONFIRMED detail | Không | PO_MANAGE | Có |
| POST /api/v1/purchase-orders/{id}/cancel | expectedVersion,reason | CANCELLED + updated conversion revisions | Không | PO_MANAGE | Có |
| POST /api/v1/purchase-orders/{id}/close | expectedVersion,reason | CLOSED + closed quantities | Không | PO_MANAGE | Có |
| GET /api/v1/goods-receipts | companyId,plantId,purchaseOrderId?,search?,status?,warehouseId?,receivedDateFrom?,receivedDateTo?,page,size,sort | Page<GR summary> | Có; receivedDate inclusive; source filter | GR_READ; PO tab linked reads Q14 | Không |
| GET /api/v1/goods-receipts/{id} | id | Receipt lines/snapshot/source/version | Không | GR_READ, record scope | Không |
| GET /api/v1/goods-receipts/purchase-order-candidates | companyId,plantId,search?,page,size | Page<minimal open PO refs+lines> | Có; only CONFIRMED/PARTIALLY_RECEIVED/open>0 | GR business permission, Q14–15 | Không |
| POST /api/v1/goods-receipts | §3.2 create | Receipt DRAFT | Không | GR_MANAGE, scope from PO | Có |
| PATCH /api/v1/goods-receipts/{id} | expectedVersion + full lines/date/text | Receipt DRAFT | Không | GR_MANAGE, record scope | Có |
| POST /api/v1/goods-receipts/{id}/post | expectedVersion | POSTED detail + updated PO version/state/counters + posting IDs | Không | GR_POST, same record scope | Có |
| POST /api/v1/goods-receipts/{id}/cancel | expectedVersion | CANCELLED DRAFT detail | Không | GR_MANAGE | Có |
| GET /api/v1/purchase-qc | companyId,plantId,search?,warehouseId?,receivedDateFrom?,receivedDateTo?,page,size,sort | Page<QC pending line DTO> incl receiptVersion/names/pending/tracking | Có; POSTED/pending>0 only, no prices | QC_READ, explicit scope | Không |
| POST /api/v1/goods-receipts/{id}/lines/{lineId}/qc | expectedVersion,acceptedQuantity,rejectedQuantity,reason | QC decision + receiptVersion + new pendingQuantity | Không | QC_MANAGE, resolve same receipt/line/Plant | Có |
| GET /api/v1/goods-receipts/{id}/quality-decisions | page,size | Page<decision DTO> | Có, ordered at desc + id | QC_READ or authorized GR/PO source projection, Q14 | Không |
| GET /api/v1/purchasing/references | companyId,plantId,purpose=PO/RECEIPT,search?,page,size | Minimal Supplier/Item/UOM/Warehouse refs and tracking, no private commercial fields | Có; không unbounded all-master dump | Quyền nghiệp vụ tương ứng, Q14 | Không |
| Existing planning run/detail/conversion | Mapping GĐ6; không tạo planning controller trùng | Extended authoritative open PO/source revisions; BUY draft canonical ID | Giữ contract cũ | Planning quyền + BUY PO_MANAGE | Conversion có |
| Existing inventory aggregate/lots/movements | Mapping GĐ4; giữ scope/pagination | Quality buckets/lot children/source/stock/value đúng §3.4 | Giữ contract cũ | Inventory quyền riêng | Mutation giữ guard ownership |

PO detail có thể trả nested related receipt/QC minimal projection thay GET thêm nếu authorization/paging rõ theo Q14. Không mở full GR detail chỉ bằng PO_READ, không lộ giá từ QC API. Candidate endpoint static phải route trước `{id}` trong router nếu cần.

**Ví dụ đề xuất — tạo PO thủ công, không phải request đã chạy:**

```http
POST /api/v1/purchase-orders
Content-Type: application/json
X-Plant-Id: 11111111-1111-4111-8111-111111111111
Idempotency-Key: aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa
```

```json
{
  "companyId": "00000000-0000-4000-8000-000000000001",
  "plantId": "11111111-1111-4111-8111-111111111111",
  "supplierId": "22222222-2222-4222-8222-222222222222",
  "warehouseId": "33333333-3333-4333-8333-333333333333",
  "orderDate": "2026-10-14",
  "reference": "TEST-PO-40",
  "notes": "",
  "lines": [{
    "itemId": "44444444-4444-4444-8444-444444444444",
    "orderedQuantity": "40",
    "unitPrice": "10",
    "expectedDate": "2026-10-14"
  }]
}
```

**Ví dụ response detail sau confirm, HTTP 200 đề xuất:** mã chứng từ minh họa riêng, không phải PO browser ở §2. Item ví dụ NON_TRACKED, baseline kho=0, không reservation/safety/earmark. PO id `555…`, PO line `666…` giữ nhất quán các ví dụ tiếp.

```json
{
  "code": "SUCCESS", "message": "OK",
  "result": {
    "id": "55555555-5555-4555-8555-555555555555",
    "code": "PO-EXAMPLE-001", "version": 2, "status": "CONFIRMED",
    "companyId": "00000000-0000-4000-8000-000000000001",
    "plantId": "11111111-1111-4111-8111-111111111111",
    "supplierId": "22222222-2222-4222-8222-222222222222",
    "supplierCode": "SUP-EXAMPLE", "supplierName": "Nhà cung cấp minh họa",
    "warehouseId": "33333333-3333-4333-8333-333333333333",
    "warehouseCode": "WH-EXAMPLE", "warehouseName": "Kho minh họa",
    "orderDate": "2026-10-14", "currencyCode": "VND", "sourceLocked": false,
    "reference": "TEST-PO-40", "notes": "", "reason": null,
    "createdAt": "2026-10-14T02:00:00Z", "updatedAt": "2026-10-14T02:01:00Z",
    "createdBy": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
    "lines": [{
      "id": "66666666-6666-4666-8666-666666666666",
      "itemId": "44444444-4444-4444-8444-444444444444",
      "itemCode": "RM-EXAMPLE", "itemName": "Nguyên liệu minh họa", "uomCode": "KG",
      "orderedQuantity": "40", "unitPrice": "10", "receivedQuantity": "0",
      "closedQuantity": "0", "openQuantity": "40", "expectedDate": "2026-10-14", "sources": []
    }],
    "history": [{"action":"CONFIRM_PO","fromStatus":"DRAFT","toStatus":"CONFIRMED",
      "actorId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","at":"2026-10-14T02:01:00Z","reason":""}]
  }
}
```

Confirm request vào `/api/v1/purchase-orders/55555555-5555-4555-8555-555555555555/confirm`: `expectedVersion` là version DRAFT trước confirm, không dùng version response 2 để giả lượt đã chạy.

```json
{"expectedVersion": 1}
```

**Ví dụ đề xuất Receipt create**, cùng PO, server copy price/scope/master. Có thể thêm nhiều dòng cùng purchaseOrderLineId nhưng tổng <=40. Server cấp Receipt id `777…`, line `888…`, version 1 cho ví dụ post tiếp.

```json
{
  "purchaseOrderId": "55555555-5555-4555-8555-555555555555",
  "receivedDate": "2026-10-14", "reference": "TEST-DELIVERY-1", "notes": "",
  "lines": [{
    "purchaseOrderLineId": "66666666-6666-4666-8666-666666666666",
    "receivedQuantity": "25"
  }]
}
```

POST `/api/v1/goods-receipts/77777777-7777-4777-8777-777777777777/post`, mới Idempotency-Key cho command, body:

```json
{"expectedVersion": 1}
```

**Ví dụ response post đề xuất** HTTP 200; receipt/PO projection rút gọn phần liên quan, GET detail vẫn đủ fields §3.2. Quality pending=25 chỉ có sau POSTED.

```json
{
  "code": "SUCCESS", "message": "OK",
  "result": {
    "receipt": {
      "id": "77777777-7777-4777-8777-777777777777", "code": "GR-EXAMPLE-001",
      "status": "POSTED", "version": 2, "postedAt": "2026-10-14T02:05:00Z",
      "lines": [{
        "id": "88888888-8888-4888-8888-888888888888",
        "purchaseOrderLineId": "66666666-6666-4666-8666-666666666666",
        "receivedQuantity": "25", "acceptedQuantity": "0", "rejectedQuantity": "0",
        "pendingQuantity": "25", "trackingPolicy": "NON_TRACKED", "lotId": null,
        "lotCode": null, "expiresOn": null,
        "qualityBucketId": "99999999-9999-4999-8999-999999999999",
        "movementIds": ["eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"]
      }]
    },
    "purchaseOrder": {
      "id": "55555555-5555-4555-8555-555555555555", "version": 3, "status": "PARTIALLY_RECEIVED",
      "lines": [{"id":"66666666-6666-4666-8666-666666666666",
        "receivedQuantity":"25","closedQuantity":"0","openQuantity":"15"}]
    },
    "posting": {"onHandDelta":"25","availableDelta":"0","holdDelta":"25","valueDelta":"250.000000","currencyCode":"VND"}
  }
}
```

**Ví dụ đề xuất QC** POST `/api/v1/goods-receipts/77777777-7777-4777-8777-777777777777/lines/88888888-8888-4888-8888-888888888888/qc`; cùng receipt version 2, mới key cho decision:

```json
{"expectedVersion":2,"acceptedQuantity":"20","rejectedQuantity":"5","reason":"5 kg không đạt kiểm tra"}
```

```json
{
  "code": "SUCCESS", "message": "OK",
  "result": {
    "decision": {
      "id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc",
      "receiptId":"77777777-7777-4777-8777-777777777777",
      "receiptLineId":"88888888-8888-4888-8888-888888888888",
      "acceptedQuantity":"20","rejectedQuantity":"5","reason":"5 kg không đạt kiểm tra",
      "actorId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","at":"2026-10-14T02:10:00Z",
      "childLots":[],"movementIds":["dddddddd-dddd-4ddd-8ddd-dddddddddddd"]
    },
    "receiptVersion":3,"pendingQuantity":"0"
  }
}
```

QC decision không có unitPrice, valueDelta hoặc giá thương mại trong QC-only response. Ledger/audit giữ value effect 0 nội bộ. Ví dụ một movement ID không bắt buộc server lưu một event thay vì tách accepted/rejected; mapping audit/event granularity cần BE xác nhận.

**Ví dụ page envelope đề xuất** cho QC queue sau đợt đầu chỉ còn các dòng chưa kiểm; empty page không lỗi:

```json
{"code":"SUCCESS","message":"OK","result":{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}}
```

### 3.4 Công thức, stock/value và transaction

Đây là các lượng **cumulative**, phân biệt với amount của một lần decision. DRAFT Receipt không là pending inventory.

```text
received_PO_line = sum(receivedQuantity của Receipt POSTED cùng PO line)
closed_PO_line >= 0, do close remainder tạo; không phải rejected quantity
open_PO_line = orderedQuantity − received_PO_line − closed_PO_line >= 0
accepted_GR_line = sum(acceptedQuantity của các decision hợp lệ cùng receipt line)
rejected_GR_line = sum(rejectedQuantity của các decision hợp lệ cùng receipt line)
pending_GR_line = receivedQuantity − accepted_GR_line − rejected_GR_line >= 0 (POSTED)
eligible_available = eligible on-hand − active reservations, không gồm HOLD/REJECTED/expired
receipt_value_delta = round_money(receivedQuantity × snapshot_unitPrice)
average_unit_cost = total_inventory_value / total_on_hand khi value known và on_hand > 0
QC total_on_hand_delta = 0; total_value_delta = 0; reserved_delta = 0
```

Invariant server phải chặn âm/over-allocation, không âm thầm clamp lỗi. Mock `purchaseOpen` có clamp0 để đọc dữ liệu cũ nhưng không cho phép ghi vượt lượng; không dùng clamp để che sai lệch DB. Với QC accepted tracked, lô con đủ điều kiện mới tăng available; rejected/HOLD không tạo free stock. Value legacy null/unknown không tự thành 0. Mock moving average dùng tổng On Hand kể cả HOLD/REJECTED, không chia cho AVAILABLE; unknown value hoặc On Hand0 trả average null. Server mapping valuation policy theo Q18–Q19, không tự write-off rejects trong GĐ7.

**Ví dụ số đề xuất**, cùng PO-EXAMPLE-001 ở §3.3: ordered40, price10 VND, kho baseline0, không reserved/safety/earmark, demand40 cùng kho/ngày, inbound ETA đủ điều kiện:

| Bước | PO received/open | On Hand | HOLD | AVAILABLE | REJECTED | Scheduled PO | Giá trị tồn VND | Shortage MRP |
|---|---|---|---|---|---|---|---|---|
| PO DRAFT | 0/40 | 0 | 0 | 0 | 0 | 0 | 0 | 40 |
| Confirm | 0/40 | 0 | 0 | 0 | 0 | 40 | 0 | 0 |
| Post GR1=25 | 25/15 | 25 | 25 | 0 | 0 | 15 | 250 | 25 |
| QC GR1 đạt20/loại5 | 25/15 | 25 | 0 | 20 | 5 | 15 | 250 | 5 |
| Post GR2=15 | 40/0 | 40 | 15 | 20 | 5 | 0 | 400 | 20 |
| QC GR2 đạt15 | 40/0 | 40 | 0 | 35 | 5 | 0 | 400 | 5 |

Shortage trong ví dụ = max(0,40−AVAILABLE−Scheduled PO) do giả định safety0/no reserved. Scheduled PO giảm thiếu hoạch định nhưng không cho reserve; chỉ after-QC case tất cả đạt mới reserve40 được. Nếu GR1 đạt25 thay vì20/loại5 thì cuối available40, shortage0, On Hand40/value400. Reject5 không tự giảm received hoặc quantity mua; người mua chạy MRP/lập PO bù chủ động.

Close sau post25: closedQuantity15/open0, On Hand25 và value250 vẫn giữ, QC phần25 vẫn làm được dù PO CLOSED. Cancel khi received0 không đổi stock/value, active conversion giải phóng một lần. Close không giải phóng conversion để vô tình convert lại cả ordered40; hoạch định mới xử lý thiếu theo supply mới.

Ví dụ tracked độc lập: receive0.100001; QC accepted0.05/rejected0.01 → parent HOLD0.040001, AVAILABLE child0.05, REJECTED child0.01, sum0.100001. Tại price125, receipt value=12.500125 VND, QC vẫn12.500125. Đây là phép tính minh họa, không claim giá trị baseline tổng kho browser.

| Event | On Hand | Reserved | Available | HOLD/REJECTED | WIP/value | Chứng từ |
|---|---|---|---|---|---|---|
| PO save/confirm | 0 delta | 0 | 0 | 0 | Không WIP/value; firm supply sau confirm | PO/revision |
| PO cancel/close | 0 | 0 | 0 | 0 | Không đảo giá; supply giảm, quota chỉ cancel | PO/conversion audit |
| Receipt save/cancel DRAFT | 0 | 0 | 0 | 0 | 0 | GR DRAFT/CANCELLED |
| Receipt post(q,p) | +q | 0 | 0 | HOLD +q | Value +round(q×p), không WIP | GR/PO/receive movement/lot/bucket |
| QC accept(a)/reject(r) | Tổng 0 | 0 | +a nếu eligible | HOLD −(a+r), REJECTED +r | Tổng value0, không WIP | Decision/child/quality audit |
| Generic issue/reserve/transfer | Không dùng quarantined quantity | Eligible-only | Canonical rules | Không đổi mua HOLD/REJECTED trái QC owner | Existing ledger | Không bypass GĐ7 bằng legacy mutation |

Atomic boundaries: PO cancel gồm status/quota/history/revisions; close gồm state/counters/history; post gồm tất cả lines/lot/bucket/movement/value/PO counters/status/GR status/key; QC gồm source pending/child/bucket/decision/history/revision/key. Một lỗi hoặc crash trước commit → không có effect từng phần. DB lock/optimistic revision theo resource thực, không bắt BE sao chép localStorage hoặc tên lock FE. Hai POST cạnh tranh open15, mỗi cái10: chỉ một thành công, cái còn lại recheck open5 và báo overreceipt, không quantity âm.

Idempotency đề xuất scoped actor+Company+Plant+method/resource+payload fingerprint. Retry cùng key/payload sau mất response trả cùng IDs/effects, không post/QC/cancel quota hai lần. Khác payload cùng key → conflict. Kiểm authorization/current scope trước trả replay; không lộ record sau thu hồi quyền. Thời hạn retention Q21; stale/conflict refetch hiện trạng nhưng giữ input, request sửa có key mới. Replay có thể chứa revision cũ, FE refetch canonical để thấy QC/PO sau đó, không overwrite bằng response cũ.

MRP cần đọc PO trên server kể cả FE chưa mở workspace; không register supply bằng hành động browser. Projection chứa type PO/documentId/lineId/status/Item/UOM/receiving warehouse/expectedDate/openQuantity/earmark eligibility/sourceVersion, không đơn giá. Cùng item khác kho hoặc PO đến sau ngày cần không bù kỳ trước. Run snapshots immutable; PO confirm/edit/cancel/close/post/QC thay nguồn/version tương ứng làm chuyển đổi stale. Không đổi GĐ6 Q28 làm tròn MAKE hoặc các Q10/Q16/Q26/Q27 trong bàn giao này.

### 3.5 Nghiệm thu live và rollout

Tất cả dưới đây **CHƯA CHẠY LIVE**. Record là kịch bản tạo mới ở môi trường BE được phép; BE/FE ghi ID thật, timestamp/version/request/response/trace từ cùng run trong cột evidence khi thực hiện. §2 browser record không thay thế. “M1” = scenario PO-EXAMPLE-001 §3.3–3.4; UUID ví dụ không được coi là record test sẵn có.

| ID | Đầu vào/record cần tạo | Thao tác | Kết quả mong đợi | Evidence | Trạng thái |
|---|---|---|---|---|---|
| A01 | PO BUY GĐ6 cũ + manual mới | Migrate/list/detail | Giữ ID/source/line/quota; không nhân đôi | Chưa có live | Chưa chạy |
| A02 | Manual PO 2 dòng active | Save/edit/confirm | Snapshot/version/master đúng, chưa stock | Chưa có live | Chưa chạy |
| A03 | Missing price và explicit0 | Confirm mỗi trường hợp | Missing bị chặn, 0 hợp lệ | Chưa có live | Chưa chạy |
| A04 | Qty0/−1/7 decimals/overflow/scientific | Save PO/GR/QC | Lỗi field, không partial write | Chưa có live | Chưa chạy |
| A05 | PO nguồn MRP | PATCH lượng/Item/kho/line/source | SOURCE_LOCKED, giữ quota/record | Chưa có live | Chưa chạy |
| A06 | Đổi master inactive/UOM/kho Plant | Confirm/post/QC | Revalidate theo contract, không partial effect | Chưa có live | Chưa chạy |
| A07 | M1 confirmed40 | DraftGR25 rồi post | DRAFT no stock; POST HOLD25/value250/open15 | Chưa có live | Chưa chạy |
| A08 | M1 còn15 | PostGR15 | Received40/open0/FULLY_RECEIVED, chưa QC đủ | Chưa có live | Chưa chạy |
| A09 | Cùng source line có split20+21 | Post | Overreceipt40 limit; rollback cả hai dòng | Chưa có live | Chưa chạy |
| A10 | Open15, hai GR10 concurrent | Post song song | Một thành công, một lỗi; received<=ordered | Chưa có live | Chưa chạy |
| A11 | Tracked thiếu/duplicate lot | Post multi-line | Field lỗi, không orphan lot/movement/key | Chưa có live | Chưa chạy |
| A12 | Non-tracked nhận25 | Post | HOLD bucket, không fake lot/AVAILABLE | Chưa có live | Chưa chạy |
| A13 | GR25/15 | QC all-good cả hai | OnHand40/value400/available40/reserve40 được | Chưa có live | Chưa chạy |
| A14 | M1 GR25/15 | QC20/5 rồi15/0 | available35/rejected5/shortage5, received40 | Chưa có live | Chưa chạy |
| A15 | Tracked receive0.100001 | QC0.05/0.01 | Parent0.040001, 2 children đúng nguồn/expiry | Chưa có live | Chưa chạy |
| A16 | Pending sau A15 | QC tiếp/reject hết | Sum bảo toàn; không chuyển toàn parent | Chưa có live | Chưa chạy |
| A17 | QC0/0, >pending, reject thiếu reason | Submit | Lỗi field/state, giữ source và input | Chưa có live | Chưa chạy |
| A18 | Same receipt revision, hai QC concurrent | Submit | Không double-consume pending; stale/recheck error | Chưa có live | Chưa chạy |
| A19 | Lot expiry policy + đã hết hạn lúc QC | Accept | Không đưa expired thành available | Chưa có live | Chưa chạy |
| A20 | HOLD/REJECTED tracked/non-tracked | Reserve/issue/transfer/generic count/status | Không bypass quarantine; accepted dùng được | Chưa có live | Chưa chạy |
| A21 | Confirmed PO chưa nhận/source quota | Cancel và replay | Supply0/quota release once/history retained | Chưa có live | Chưa chạy |
| A22 | Received25 + receipt DRAFT khác | Close/cancel | Pending draft block, không đổi stock/state | Chưa có live | Chưa chạy |
| A23 | Received25 không DRAFT treo | Close remaining15 rồi QC | open0/closed15; QC25 được; không reversevalue | Chưa có live | Chưa chạy |
| A24 | POSTED receipt/confirmed PO | Edit/cancel trái lifecycle | STATE error, không đảo tồn | Chưa có live | Chưa chạy |
| A25 | Same command key, response bị mất | Retry save/post/QC/cancel | Same result IDs/effects, không duplicate | Chưa có live | Chưa chạy |
| A26 | Same key đổi payload/resource | Submit | IDEMPOTENCY_CONFLICT, không effect mới | Chưa có live | Chưa chạy |
| A27 | Lỗi dòng cuối/storage/DB injected | Post/QC rollback | PO/GR/stock/value/audit/key cùng rollback | Chưa có live | Chưa chạy |
| A28 | Thu hồi assignment sau lần thành công | Read/command/replay | 403 hiện tại; không lộ replay | Chưa có live | Chưa chạy |
| A29 | Plant2/Company khác dùng ID Plant1 | Detail/line/QC/candidate/source | Denied/not-found theo policy, không leak | Chưa có live | Chưa chạy |
| A30 | QC-only, planning-only, receiving-only | List/detail/projections | Đúng scope, QC/MRP no prices; source full link guarded | Chưa có live | Chưa chạy |
| A31 | Open PO đủ ETA/cùng kho | Direct MRP chưa mở PO UI | Supply tham gia đúng, DRAFT không tham gia | Chưa có live | Chưa chạy |
| A32 | M1 sau receipt HOLD/QC | Run MRP từng bước | Các shortage §3.4; không count stock+supply trùng | Chưa có live | Chưa chạy |
| A33 | Late PO hoặc kho khác | Run MRP/conversion sau nguồn đổi | Không bù kỳ/kho sai; saved run immutable, stale action | Chưa có live | Chưa chạy |
| A34 | Existing stock value null + receipt giá mới | Post/QC/readvalue | Không suy legacy value=0; Q19 mapping authoritative | Chưa có live | Chưa chạy |
| A35 | Search/status/kho/date boundary + >20 rows | GET pages/related/QC | Total đúng scoped filter, sort ổn định, không FE tự aggregate | Chưa có live | Chưa chạy |
| A36 | Supplier/Item đổi tên sau confirm | Detail/receipt/source | Historical snapshot giữ nguyên, current selector đúng | Chưa có live | Chưa chạy |
| A37 | Plant timezone, date/expiry midnight | Save/post/QC | Date policy server đúng boundary, không lệch theo client | Chưa có live | Chưa chạy |
| A38 | Legacy SO/MRP/WO/production receiptQC | Hồi quy API | Không breaking permission/state/ledger | Chưa có live | Chưa chạy |

Thứ tự rollout: (1) BE trả lời §5 + OpenAPI/version; (2) đối soát canonical PO GĐ6/quota/line IDs, schema quality/source/ledger, rollback migration plan; (3) quyền/reference/read projections; (4) PO commands; (5) receipt/post atomic; (6) QC partial và guards mọi inventory path; (7) MRP source/revisions; (8) FE adapter và live acceptance cùng version, rollout permission seed được PO duyệt. Không reset dữ liệu/quota hoặc cấp quyền rộng để tiện demo.

Chuyển mock→API chỉ sau mapping decimal/nullability/versions/source authorization và transaction được chốt, endpoints deploy xác minh, test happy/error/replay/concurrency/scopes đạt. Không fallback mock khi API lỗi/missing. Chưa tích hợp API tương đương hoặc đóng task khi chỉ BE báo “xong”.

**Change log v1:** bàn giao GĐ7 sau FE delivery; 20 yêu cầu, 31 câu hỏi xác nhận, 38 case live pending; không thay quyết định GĐ6 còn mở, không có BE defect.

## §4 — Hợp đồng lỗi mong đợi

**[Đề xuất BE]** dùng error code ổn định theo nghiệp vụ; mã mock ngắn (QUANTITY/STATE/STALE/…) chưa là wire contract. Nếu BE đã có mã tương đương, trả mapping và HTTP. FE rẽ nhánh theo `code`, không chỉ status hoặc parse message.

| Tình huống | HTTP đề xuất | code đề xuất | Message/field errors FE | Input giữ lại | Retry |
|---|---|---|---|---|---|
| Thiếu/invalid field, ngày, số dòng | 400 | VALIDATION_ERROR | Field cụ thể: receivedDate/lines[0].expectedDate… | Có | Sửa rồi key mới |
| Quantity nonpositive/scale/range hoặc QC0+0 | 400 | INVALID_QUANTITY | lines[i].receivedQuantity/acceptedQuantity | Có | Sửa rồi key mới |
| Missing/negative/invalid price | 400 | INVALID_UNIT_PRICE | lines[i].unitPrice, missing confirm phân biệt0 | Có | Sửa draft |
| Master inactive/sai UOM/sai kho Plant | 409 | REFERENCE_NOT_ACTIVE hoặc WAREHOUSE_SCOPE_MISMATCH | Field reference, không chọn lại âm thầm | Có | Refetch refs + sửa |
| Source-linked draft sửa locked field | 409 | SOURCE_LOCKED | Chỉ rõ field không được đổi | Có | Không retry nguyên request |
| Version/source đổi | 409 | STALE_VERSION hoặc STALE_SOURCE | Thông báo tải hiện trạng/tính lại MRP | Có | Refetch, review, key mới |
| Lifecycle trái state/POSTED sửa hoặc repost key mới | 409 | INVALID_STATE | State hiện tại và allowed actions | Có | Không blind retry |
| PO có Receipt DRAFT treo | 409 | PENDING_GOODS_RECEIPT | Báo xử lý nháp trước close/cancel | Có | Sau khi resolve nháp |
| Receipt vượt open PO cộng dồn | 409 | PURCHASE_ORDER_OVER_RECEIPT | Field line + authoritative remainingQuantity nếu được phép | Có | Refetch/correct |
| Lot thiếu/duplicate/tracking không phù hợp | 400/409 | LOT_REQUIRED / LOT_DUPLICATE / TRACKING_POLICY_MISMATCH | Field lines[i].lotCode/expiresOn | Có | Sửa rồi key mới |
| Lot expiry không eligible | 409 | LOT_EXPIRED | expiry/line, không accept | Có | Không retry unchanged |
| QC vượt pending hoặc hết pending | 409 | QC_QUANTITY_EXCEEDED / QC_STATE_INVALID | accepted/rejectedQuantity + pending snapshot | Có | Refetch/correct |
| Reject/close/cancel thiếu lý do | 400 | REASON_REQUIRED | reason | Có | Sửa rồi key mới |
| Generic inventory mutation bypass mua QC | 409 | PURCHASE_QC_REQUIRED | Mở đúng workflow QC theo quyền | Có | Không legacy fallback |
| Value precision/overflow | 400 | VALUE_OVERFLOW | Giá trị vượt giới hạn; field nếu xác định | Có | Sửa theo policy |
| Same key khác fingerprint | 409 | IDEMPOTENCY_CONFLICT | Request khác cùng key, không tự replay | Có | Submission được review dùng key mới |
| Quyền/assignment/scope không còn hợp lệ | 403 | PERMISSION_DENIED | No permission; không hiện record/price ngoài scope | Có | Không retry403 |
| ID ngoài scope/không tồn tại | 404 | NOT_FOUND | Không khả dụng; anti-enumeration theo Q1 | Có | Không retry |
| Lỗi transient server/timeout | 503/504 | SERVICE_UNAVAILABLE / REQUEST_TIMEOUT | Chưa xác định outcome, giữ key và input | Có | Retry cùng key/payload |

Envelope proposal success: code/message/result bắt buộc, errors omitted khi success. Error: code/message bắt buộc, result omitted hoặc null theo NON_NULL mapping; errors array optional; `errors[].field` ổn định, `errors[].message` bắt buộc khi có phần tử. Non-field error có thể không errors. `X-Trace-Id` response header cho thành công/lỗi server theo Q22; không nhúng token/credential vào message/trace. HTTP201 create/200 command/GET là đề xuất, mapping existing code được ưu tiên sau chốt.

**Ví dụ lỗi đề xuất**, không trace/request thật; header X-Trace-Id phải lấy từ response thực khi live, không dùng UUID minh họa thay:

```json
{
  "code":"PURCHASE_ORDER_OVER_RECEIPT",
  "message":"Lượng nhận vượt phần còn lại của đơn mua",
  "errors":[{"field":"lines[0].receivedQuantity","message":"Phần còn lại: 15 KG"}]
}
```

401 refresh/logout dùng GĐ1, không biến business403 thành refresh retry. Lỗi dữ liệu không xóa form; không retry tự động command sửa giá trị bằng key cũ. Response unknown outcome thì giữ cùng key để xác định kết quả server.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định đóng; CHẶN nghĩa chặn FE adapter/live phần được nêu, không chặn FE/mock giai đoạn sau. Owner quyết định BE+PO; quyết định/ngày của mọi Q hiện **chưa chốt**. Đổi policy FE đã duyệt cần PO xem lại trước sửa ứng dụng.

1. **Q1:** Mapping bảy quyền §1.4 có dùng nguyên mã FE được không? — FE nghiêng về Có, READ/action riêng theo Plant và replay có auth hiện tại — **CHẶN authorization adapter**.
2. **Q2:** PO DRAFT từ BUY GĐ6 được giữ cùng canonical ID khi nâng cấp GĐ7 được không? — FE nghiêng về Có để không double quota/source — **CHẶN migration**.
3. **Q3:** PATCH DRAFT dùng full lines replacement với stable line IDs được không? — FE nghiêng về Có để nhiều dòng/lô sửa được và history/source không mất — **CHẶN save adapter**.
4. **Q4:** unitPrice=null/omitted ở DRAFT được giữ thiếu cho đến confirm được không? — FE nghiêng về Có, khác explicit0 — **CHẶN price/nullability adapter**.
5. **Q5:** GĐ7 chỉ dùng VND base unit price không thuế/phí được không? — FE nghiêng về Có theo scope đã duyệt — **CHẶN valuation adapter**.
6. **Q6:** Item mua dùng whitelist RAW_MATERIAL/CONSUMABLE/MRO/WIP/SEMI_FINISHED được không? — FE nghiêng về Có theo mock, không mở FG/serial ngầm — **CHẶN reference validation**.
7. **Q7:** Source-linked DRAFT giữ khóa lượng/Item/UOM/kho/cấu trúc dòng được không? — FE nghiêng về Có để quota/source nhất quán — **CHẶN source edit adapter**.
8. **Q8:** Receipt post trực tiếp vào HOLD không có approval riêng được không? — FE nghiêng về Có với GR_POST tách permission, khác production approval — **CHẶN lifecycle adapter**.
9. **Q9:** Receipt DRAFT chưa chiếm open PO như reservation receiving được không? — FE nghiêng về Có, post kiểm lại open dưới lock — **CHẶN draft/concurrency contract**.
10. **Q10:** Một receipt cho phép nhiều dòng cùng PO line để tách lô được không? — FE nghiêng về Có, cap kiểm tổng dòng — **CHẶN split receiving**.
11. **Q11:** Close/cancel PO bị chặn khi còn Receipt DRAFT được không? — FE nghiêng về Có, user xử lý nháp trước — **CHẶN cancel/close adapter**.
12. **Q12:** Cancel PO chưa nhận giải phóng active conversion quota đúng một lần được không? — FE nghiêng về Có qua explicit cancel, giữ inactive history; bổ sung GĐ6 Q21, không tự đổi SO/WO cancel — **CHẶN conversion reversal contract**.
13. **Q13:** Close remainder không giải phóng toàn conversion quota được không? — FE nghiêng về Có để không chuyển lại cả ordered quantity; chạy run mới xử lý shortage — **CHẶN close/source contract**.
14. **Q14:** Cho quyền nghiệp vụ đọc source/reference tối thiểu mà không cần mọi master/PO/GR READ được không? — FE nghiêng về Có, projection scope/fields cụ thể cho từng tab/candidate/QC — **CHẶN projection authorization**.
15. **Q15:** GR_READ được thấy receipt/candidate unitPrice kế thừa để nhận hàng được không? — FE nghiêng về Có theo mock; QC-only/MRP-only luôn bỏ giá — **CHẶN price read policy**.
16. **Q16:** Mỗi accepted/rejected phần tracked QC tạo child lot có parent, phần pending ở parent HOLD được không? — FE nghiêng về Có kể cả all-good, không đổi nguyên lot status trái partial lineage — **CHẶN QC split contract**; cách sinh unique code do server.
17. **Q17:** LOT expiry bắt buộc chỉ khi Item policy quy định được không? — FE nghiêng về Có; master mock chưa có expiryRequired nên đang tùy chọn, server trả expiresOn và eligibility/day boundary — **CHẶN expiry acceptance**.
18. **Q18:** Value receipt làm tròn scale6 HALF_UP sau quantity×unitPrice được không? — FE nghiêng về Có để khớp money mock; BE xác nhận precision/range/average policy — **CHẶN valuation acceptance**, không chốt rounding MAKE GĐ6 Q28.
19. **Q19:** Legacy inventory value thiếu giữ null/unknown sau nhận mới nếu không có opening value đáng tin được không? — FE nghiêng về Có, không giả tổng value bằng riêng lần nhận mới — **CHẶN legacy value mapping**.
20. **Q20:** Ngày nhận/expiry dùng timezone từng Plant do server resolve được không? — FE nghiêng về Có, demo hiện Asia/Ho_Chi_Minh; không phụ thuộc browser — **CHẶN date/time adapter**.
21. **Q21:** Idempotent result giữ tối thiểu 24 giờ theo actor/scope/resource/payload được không? — FE nghiêng về Có để retry mạng, authorization recheck trước replay — **CHẶN retry rollout**, BE báo retention nếu khác.
22. **Q22:** Mọi response server thành công/lỗi có X-Trace-Id được không? — FE nghiêng về Có để nghiệm thu và tra log cùng run — **CHẶN live evidence/diagnostics**.
23. **Q23:** reference tối đa200, notes2000, reason500 ký tự được không? — FE nghiêng về Có, mock chỉ guard reason500; không tuyên bố text length còn lại đã enforce — **CHẶN text validation mapping**.
24. **Q24:** Demo seed quyền mua chỉ Admin/Manager, không tự cấp mọi role cũ được không? — FE nghiêng về Có để tránh mở rộng quyền ngoài scope — **KHÔNG CHẶN FE code tiếp**, chặn seed rollout nếu chưa duyệt.
25. **Q25:** Server action mua hàng bắt buộc READ cùng action permission tương ứng được không? — FE nghiêng về Có cho profile UI hiện tại; permission action một mình không mở full workspace — **CHẶN action authorization mapping**; khác mock service chỉ kiểm action, server policy cần chốt riêng.
26. **Q26:** Truy cập ID ngoài tenant/Plant trả404 NOT_FOUND được không? — FE nghiêng về Có để không lộ record existence, thiếu quyền ở đúng scope trả403 — **CHẶN direct-ID error mapping**.
27. **Q27:** Cùng actor nhận hàng và QC được phép khi có cả hai action permissions được không? — FE nghiêng về Có theo demo, không áp dụng separation-of-duties bắt buộc trong scope hiện tại — **CHẶN QC authorization acceptance**.
28. **Q28:** Lot mới unique không phân biệt hoa thường theo Company+Item được không? — FE nghiêng về Có và không reuse lot khác receipt trong GĐ7, phù hợp mock single-Company — **CHẶN lot identity/migration**.
29. **Q29:** expiresOn là ngày bắt đầu hết hiệu lực tại00:00 timezone Plant được không? — FE nghiêng về Có để khớp instant expiry mock; nếu BE dùng hết ngày, FE cần đổi mapping boundary — **CHẶN expiry/date adapter**.
30. **Q30:** PO supply chỉ đưa phần không earmark vào nguồn cung tự do cho MRP được không? — FE nghiêng về Có theo rule GĐ6, mock PO hiện chưa có earmark và coi open là free; server phải cung cấp eligible quantity nếu có — **CHẶN committed supply mapping**.
31. **Q31:** Input quantity và unitPrice dùng max999999999/scale6 như mock được không? — FE nghiêng về Có để FE/server cùng range; BE báo giới hạn khác trước adapter — **CHẶN numeric validation mapping**.

Self-receive/self-QC chờ Q27; nếu cần separation-of-duties, PO xem xét thay đổi scope trước adapter. Mọi Q ở trên là **Q GĐ7**; Q28 GĐ7 là lot uniqueness, khác **Q28 GĐ6** về làm tròn MAKE. Các câu hỏi GĐ6 Q10/Q16/Q26/Q27/Q28 chưa được tài liệu này trả lời thay.

## §6 — FE đã tự xử lý

**[Đã kiểm chứng FE]**

- Tên nghiệp vụ VI/EN thay mã quyền; sidebar guards, action guards, link full source theo quyền, Plant-keyed queries/deep links. API purchasing chưa có contract xác minh thì unavailable, không fallback mock.
- PO/receipt/QC dùng cùng canonical source và shared transaction envelope; legacy PO không nhân đôi; line/version/source migration giữ trace, missing price không tự0. Read models tách received/accepted/rejected/pending/open/closed.
- Decimal validation sáu chữ số, positive/range/date/lot/expiry/reason, pending DRAFT guard và critical confirmation. Lỗi vẫn giữ input/reason/filter; busy guard ngăn double click; retry unchanged dùng cùng command key, changed input dùng key mới.
- Mock transaction master→inventory→design, staged rollback, key fingerprint, recheck quyền/scope/state/version; tests concurrent receipt/QC, failure injection/replay. Đây không thay DB transaction/idempotency enforcement server.
- Quarantine non-tracked, tracked child lineage, inventory eligibility/shared reserved issue/source links; generic mutation không biến HOLD/REJECTED mua thành usable. Quality changes không post receipt/value lần hai.
- MRP đọc PO trực tiếp, scoped revisions và source projections không giá; invalidate/refetch queries sau mutate/storage event/đổi Plant; snapshot run cũ giữ nguyên, panel đọc canonical latest status. Replay response cũ cần refetch current data.
- Skeleton/empty/error/read-only, wide forms/right panels/centered QC, desktop/tablet VI/EN và reload đã kiểm ở delivery; không rerun app checks trong phiên bàn giao tài liệu.

**Giới hạn cần align khi BE trả contract:** numeric mock→UUID, line replacement/source DTO, expiryRequired/timezone, money rounding/unknown value, permission/projection, exact wire errors và replay retention. FE không tự suy accepted/delivered từ một số legacy, không coi source price có thể đọc vì user được MRP. Chỉ gỡ guard/workaround sau nghiệm thu cùng API/version; tiếp tục giai đoạn 8 FE/mock độc lập các BE gates trước.

## §7 — Tham chiếu

Chỉ nêu tên tài liệu; nội dung triển khai cần thiết đã có trong file này. Trạng thái đã gửi của các file trước không được xác minh ở phiên này.

- FE_HANDOFF_GD_06_2026-10-14.md — SO/MRP, partial MAKE/BUY, canonical PO draft/source/quota; Q5/Q21 được GĐ7 đề xuất mở rộng, Q10/Q16/Q26/Q27/Q28 vẫn riêng.
- FE_HANDOFF_GD_04_2026-10-12.md — inventory canonical stock/decimal/reservation/ledger/lot; bổ sung quality mua cần hồi quy.
- FE_HANDOFF_GD_02_2026-10-12.md — Supplier/Item/UOM/Plant/kho và active-reference dependencies.
- FE_HANDOFF_GD_03_2026-10-12.md — import scope/retention nền; GĐ7 không thêm PO import.
- FE_HANDOFF_GD_05_2026-10-13.md — BOM/Routing/calendar snapshots; không thay nghiệp vụ trong bàn giao purchasing.
- FE_HANDOFF_GD_07_MESSAGE_2026-10-14.md — tin nhắn 4 dòng đi kèm task GĐ7, không phải authorization gửi ngoài.

### Checklist chuẩn bị phát hành v1

- [x] Có đầy đủ §0–§7, ba dòng kết luận P1, trong/ngoài phạm vi, môi trường/mốc FE/owner chưa chỉ định.
- [x] Không báo defect BE; năm mảnh live đều ghi chưa có, deploy chưa xác minh; mock/UUID/JSON đề xuất được phân biệt rõ.
- [x] 20 yêu cầu kiểm được, field tables 5 cột, endpoint method/path/input/result/paging/quyền/key, công thức và ví dụ nhất quán.
- [x] Error contract theo code/HTTP/field/input/retry; không có secret hoặc trace giả làm evidence.
- [x] §5 riêng chỉ trả lời, 31 quyết định đóng + phương án FE + mức chặn; chưa tự chốt GĐ6/open BE policies.
- [x] 38 live cases pending, rollout/migration/refetch/replay và FE defenses/limits được mô tả.
- [x] Tên FE_HANDOFF có ngày/v1, tự chứa không dựa đường dẫn/repo/màn hình FE; tin nhắn 4 dòng đã lưu.
- [ ] PO gửi file và xác nhận ngày gửi; chưa gửi ngoài ở phiên này.
- [ ] BE trả lời Q1–Q31, gửi mapping/OpenAPI/deploy version; chưa chốt contract.
- [ ] FE nghiệm thu live cùng record/request/version trước đóng task; chưa chạy.

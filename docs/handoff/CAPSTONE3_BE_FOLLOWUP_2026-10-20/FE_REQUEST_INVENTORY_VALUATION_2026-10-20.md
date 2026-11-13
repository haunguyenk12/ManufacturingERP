# Bổ sung sổ giá trị tồn kho và nguồn giá trị WIP

## §0 — Kết luận trong 3 dòng

- Vấn đề: Phản hồi GĐ4 và GĐ7 hoãn định giá, nên chưa đủ nguồn tiền để nghiệm thu tồn kho/WIP và giá thành.
- Ảnh hưởng: Màn hình kho, biến động, WIP và giá thành trong FE/dev-demo; phần quantity vẫn có thể tích hợp riêng.
- Mức ưu tiên: P1 — chặn một phần tích hợp/nghiệm thu tương ứng, không phải toàn hệ thống ngừng chạy.

## §1 — Phạm vi

**Bản:** v1 · **Ngày dự kiến gửi:** 20/10/2026 · **FE:** mốc Session324, nối FE/mock GĐ1–11 và review phản hồi Session323. Đã lưu cục bộ; chưa tự gửi. Nếu gửi ngày khác đổi ngày tên/file; sửa sau gửi dùng _v2 và giữ cùng mạch task. Owner FE/BE do PO phân công, chưa có tên trong phiên này.

**Trong phạm vi:** Tồn theo Item+Warehouse; giá trị nhập/xuất/chuyển/kiểm đếm, snapshot movement và nguồn vật tư đi vào/rời WIP. GET balances/aggregate, balances, movements/detail; POST receive/issue/adjust/counts/transfers; đường vật tư WO và nhận mua phải dùng cùng sổ giá trị.

**Ngoài phạm vi:** Không làm lại toàn bộ GĐ11 trong task này; không GL/thuế/FX/giá bán, không định giá đa nguồn tổng quát hoặc sửa chứng từ cũ. Rates/labor/overhead/final close theo handoff GĐ11, chỉ yêu cầu mapping dependency.

**Căn cứ và trạng thái:** [BE báo cáo] BE_RESPONSE_GD_04_2026-10-15.md §0 R03/R10 và §8 chưa có unitCost/averageCost/stockValue/value snapshot; BE_RESPONSE_GD_07_2026-10-18_v3.md R19 tiếp tục hoãn. [Chưa kiểm chứng live] Chưa biết phần này đã được triển khai ở giai đoạn sau hay chưa.

Nhãn trong file: **[BE báo cáo]** là nội dung phản hồi đã nhận, không là FE nghiệm thu; **[Đề xuất]** là tiêu chí/field/error mới cần BE mapping hoặc chốt; **[FE kiểm offline]** chỉ áp dụng phép tính/đọc tài liệu đã thực hiện. Bảng §3 và ví dụ là đề xuất, không là bằng chứng runtime. Nếu đã có implementation tương đương, BE trả mapping/build/test của nó, không làm trùng.

## §2 — Bằng chứng tái hiện

**Không có lượt gọi BE trong phiên này.** PO cho biết BE đang tắt; FE không khởi động server, không kiểm được deployment mới nhất và không dùng file phản hồi để giả làm request FE đã chạy.

| Mảnh bằng chứng live của FE | Hiện có |
|---|---|
| Request thật method/path/header/body | Không có |
| Response thật HTTP/code/nguyên văn | Không có |
| X-Trace-Id response của cùng lượt | Không có, không tự tạo |
| Môi trường + giờ + bản BE được kiểm | Chưa kiểm; chỉ có ngày làm tài liệu20/10/2026, không có giờ/live build |
| ID record BE của cùng lượt | Không có; fixture bên dưới là đề xuất, không dùng UUID cũ đã ngừng |

```text
Mong đợi: BE trả lời theo §3/§5 và cung cấp mapping/bằng chứng đúng phiên bản khi sẵn sàng.
Thực tế: Đã đối chiếu tài liệu BE; FE chưa gọi API hoặc nghiệm thu live, không gửi file này như FE_DEFECT.
```

## §3 — Đầu ra mong đợi — Cần BE triển khai hoặc cung cấp mapping đã có

**[Đề xuất]** R01… là đầu ra kiểm được. Nếu phần đã tồn tại ở bản mới hơn phản hồi, ghi rõ mapping thay vì coi file này buộc làm lại.

| # | Đầu ra đúng/sai kiểm được | Endpoint/điểm xử lý | Chặn FE? |
|---|---|---|---|
| R01 | Đọc aggregate trả giá trị tồn đã biết hoặc unknown có lý do; không lấy riêng lần nhập mới làm tổng giá trị của opening unknown. | GET /api/v1/inventory/balances/aggregate | Có—value |
| R02 | Receive có giá nhập rõ; quantity/value/movement/lot cùng commit hoặc không đổi gì. | POST /api/v1/inventory/receive; nhận mua dùng sổ chung | Có—receive value |
| R03 | Issue dùng giá bình quân Item+Warehouse tại commit; giá trên movement là snapshot, không đổi theo giá hiện tại. | POST /api/v1/inventory/issue; POST /api/v1/work-orders/{id}/material-issues; approve tương ứng | Có—WIP |
| R04 | Transfer cùng Plant bảo toàn tổng quantity và tổng value, hai phía/phiếu/movements atomic, replay không nhân đôi. | POST /api/v1/inventory/transfers | Có—transfer value |
| R05 | Count/adjust có rule định giá được công bố; thiếu giá cho phần tăng bị chặn hoặc dùng nguồn giá đã chốt, không tự gán0. | POST /api/v1/inventory/counts; POST /api/v1/inventory/adjust | Có—count value |
| R06 | QC phân loại lại không tăng/giảm tổng giá trị tồn; rejected vẫn có value cho tới workflow xử lý riêng. | QC mua/receipt inventory posting | Có—QC value |
| R07 | Nguồn issue/consume/return nối tới WO và movement; giá trị consumed+returned+unused bằng issued, phần cuối nhận residual không viết lại dòng cũ. | Đường vật tư WO đã có hoặc mapping BE cung cấp | Có—costing |
| R08 | Dữ liệu cũ thiếu opening value giữ unknown; đọc không có quyền tài chính không trả trường tiền; retry kiểm quyền hiện hành trước replay. | GET/POST trên | Có—legacy/auth |

### Contract bổ sung đề xuất, không coi đã deploy

Giữ các path hiện có, BE có thể mapping sang DTO tương đương thay vì tạo endpoint trùng. GET aggregate dùng filter Item/Warehouse/Plant hiện hành, page0,size20/max100; totals/known-state thuộc toàn scope, không cộng riêng một trang. GET /api/v1/inventory/movements/{movementId} trả snapshot của đúng movement; GET list giữ phân trang hiện hành. POST giữ body legacy, chỉ thêm unitCost ở nhận mới và valuation input đã chốt cho count tăng; không đổi nghĩa quantityDelta của adjust. UnitCost bắt buộc cho receive mới khi triển khai lát này; known0 phải là giá chủ động, không là fallback.

| Field đề xuất | Kiểu | Nullable | Nguồn giá trị / công thức | Dùng ở đâu UI |
|---|---|---|---|---|
| currency | enum VND | Có nếu unknown/không được đọc tiền thì omit | Cấu hình valuation scope | Kho/WIP |
| stockValue | decimal exact6 | Có khi thiếu opening/source | Tổng value authoritative Item+Warehouse | Tổng giá trị tồn |
| averageCost | decimal exact12 | Có khi quantity0 hoặc value unknown | stockValue/onHand, không chia available | Đơn giá bình quân |
| valueState / unknownReason | KNOWN/UNKNOWN + string | reason có khi UNKNOWN | Trạng thái nguồn đủ/thiếu | Cảnh báo |
| unitCost (request receive) | decimal >=0 scale6 | Không với receive mới có giá | Giá người nhập xác nhận | Nhập kho |
| recordedUnitCost / movementValue | decimal exact12 / exact6 | Có với legacy unknown | Snapshot lúc post, amount không tính lại lúc đọc | Biến động |
| valueBefore / valueAfter | decimal exact6 | Có nếu unknown | Sổ authoritative tại commit | Chi tiết movement |
| beforeOnHandQuantity / afterOnHandQuantity | quantity scale6 | Có cho lịch sử chưa lưu | Snapshot lúc post, không FE suy bằng trang history | Chi tiết movement |
| workOrderId / sourceMovementId | UUID | Có nếu không liên quan WO | Liên kết consumption/return | WIP/giá thành |

Decimal exact là yêu cầu độ chính xác; kiểu token wire chưa chốt, không yêu cầu đổi toàn API sang string trong task này. Quyết định wire được hỏi riêng FE_QUESTION_DECIMAL_WIRE_PRECISION_2026-10-20.md. Read monetary cần quyền tài chính hiện hành do BE nêu rõ; INVENTORY_READ không mặc nhiên được cấp thêm quyền xem tiền. Scope là Company/Plant/Warehouse của record, principal do server lấy; không tin actor/currency/value tổng từ client. Current auth/scope phải kiểm sau lock trước replay. Movement quantity0 chỉ dùng value adjustment có workflow rõ; không giả RECEIVE/ISSUE để sửa giá.

### Công thức và fixture đề xuất (chưa chạy)

VND, value scale6 HALF_UP; average nội bộ ít nhất12, rule precision/range cần BE chốt. Known opening:100 KG/value100000; receive20×1200=24000 → OnHand120/value124000; bình quân124000/120. Issue30 → value31000, còn90/value93000. Transfer10 → value10333.333333, nguồn80/value82666.666667, đích10/value10333.333333. Tổng sau transfer vẫn90/value93000. QC đổi trạng thái không đổi value.
Nếu issue có giá trị1 VND/3 đơn vị, consume1 và return1+1, dùng phân bổ lũy kế/residual: consumed0.333333, return0.333333+0.333334 =1; không làm mất0.000001 hoặc sửa return đã ghi.
Fixture legacy mở đầu value unknown, nhập20×1200 chỉ biết contribution24000; total value vẫn unknown, không được trả tổng24000 như toàn kho.

### Nghiệm thu / rollout

Chờ live: known0/unknown, receive/issue/transfer/count, legacy, QC tổng value, return residual, same-key replay/current revoked auth, lỗi dòng cuối rollback và2 Plant/stock-only redaction. BE cung cấp migration, mapping DTO, test/bản build đã có trước; nếu đã làm ở GĐ8/11 chỉ cần chỉ rõ nguồn triển khai tương đương, không code trùng. Không hồi tố giá cũ hoặc dời giá bán/invoice/payment.

## §4 — Hợp đồng lỗi mong đợi

[Đề xuất/mapping] Tái dùng mã BE khi phù hợp; mã mới ghi rõ đề xuất. BE trả bảng tương đương nếu khác, không bắt FE parse message:

| Tình huống | HTTP | code / contract | FE hiển thị/xử lý |
|---|---|---|---|
| Thiếu/âm/quá scale unitCost | 400 | VALIDATION_ERROR | Lỗi tại unitCost, giữ input |
| Nguồn giá chưa đủ để post an toàn | 409 | VALUATION_UNAVAILABLE (đề xuất, BE mapping nếu có mã khác) | Hiển thị nguồn thiếu, không tự gán0 |
| Version cũ | 409 | CONCURRENT_MODIFICATION | Refetch giữ input |
| Cùng key khác intent | 409 | IDEMPOTENCY_CONFLICT | Không tự gửi key mới để lặp post |
| Không quyền/scope | 403 | PERMISSION_DENIED | Không trả dữ liệu tiền |

Envelope hiện hành được giữ: code/message, result khi có dữ liệu và errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Riêng import revalidation409 có result=batch INVALID theo GĐ3. X-Trace-Id giữ cho chẩn đoán, không đưa Authorization/token vào evidence. Lỗi validation/business/stale không tự retry như lỗi mạng; giữ input và source context, chỉ retry cùng intent theo contract idempotency của endpoint. Không áp dụng replay chung cho master endpoints đang bỏ qua header.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; Q có thể cần PO chốt policy. Owner trả lời BE, PO duyệt nghiệp vụ nếu có thay đổi; tất cả hiện **chưa trả lời**.

1. **Q1:** Bản BE sau GĐ7 đã có sổ valuation tương đương R01–R08 chưa? — FE nghiêng về: Có nếu có, cung cấp mapping/build thay code trùng — **CHẶN tích hợp value, không chặn quantity**.
2. **Q2:** Mã quyền đọc tiền hiện hành có được BE công bố riêng trước mở projection không? — FE nghiêng về: Có, tránh tự mở rộng INVENTORY_READ — **CHẶN read tài chính**.
3. **Q3:** VND value scale6 HALF_UP và average nội bộ tối thiểu12 có được dùng cho lát này không? — FE nghiêng về: Có để giữ residual theo fixture — **CHẶN valuation acceptance**.
4. **Q4:** Count tăng khi không có nguồn giá đã biết có bị chặn thay vì tự gán0 không? — FE nghiêng về: Có để không tạo giá trị giả — **CHẶN count value**.

## §6 — FE đã tự xử lý

FE/mock có ledger quantity/value và giữ unknown; API value chưa tích hợp. Không tự tính total bằng giá PO hoặc giá bán. FE chịu trách nhiệm typed mapping/hiển thị missing, không đẩy việc sửa adapter sang BE. Chưa sửa app trong phiên324.

Phần còn lại FE tự đảm nhiệm khi được duyệt: DTO/adapter, hiển thị label/error, giữ input, quyền UI, cache invalidation và test phù hợp. Không xin BE sửa giao diện hoặc chứng minh pass FE thay FE. Không đổi flag/dependency, không tạo dữ liệu server trong phiên tài liệu; live acceptance vẫn chờ môi trường và contract được xác nhận.

## §7 — Tham chiếu

- BE_RESPONSE_GD_04_2026-10-15.md — §0/§8 giới hạn valuation.
- BE_RESPONSE_GD_07_2026-10-18_v3.md — R19/Q18–19 hoãn giá trị.
- FE_HANDOFF_GD_04_2026-10-12.md — yêu cầu quantity/value gốc.
- FE_HANDOFF_GD_08_2026-10-14.md — source WIP/return.
- FE_HANDOFF_GD_11_2026-10-15.md — baseline/consume/return/close; task này không thay toàn handoff.

Chỉ ghi tên file vì hai bên không dùng chung repo. File này tự chứa vấn đề, tiêu chí và ví dụ cần cho task; không yêu cầu BE mở source FE. Các file phản hồi được ghi theo đúng tên nhận, không tự sửa ngày/phiên bản của BE.

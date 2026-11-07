# Hiển thị nguồn mua đang chờ QC trong MRP

## §0 — Kết luận trong 3 dòng

- Vấn đề: Nguồn đã nhận nhưng chờ QC không còn trong open PO và chưa AVAILABLE, nên người lập kế hoạch không thấy phần đang chờ.
- Ảnh hưởng: MRP/đề xuất BUY trong FE/dev-demo; cần cảnh báo mua bổ sung, không đổi pending thành hàng dùng được.
- Mức ưu tiên: P2 — thiếu thông tin/phân loại hoặc giới hạn phạm vi, còn có phần nghiệp vụ dùng được.

## §1 — Phạm vi

**Bản:** v1 · **Ngày dự kiến gửi:** 20/10/2026 · **FE:** mốc Session324, nối FE/mock GĐ1–11 và review phản hồi Session323. Đã lưu cục bộ; chưa tự gửi. Nếu gửi ngày khác đổi ngày tên/file; sửa sau gửi dùng _v2 và giữ cùng mạch task. Owner FE/BE do PO phân công, chưa có tên trong phiên này.

**Trong phạm vi:** Projection pending QC có nguồn receipt/line/kho trên planning run và cảnh báo trước convert BUY; netting hiện hành giữ nguyên trong phạm vi mặc định.

**Ngoài phạm vi:** Không tự net pending QC như available/firm supply; không auto approve QC, auto-create/cancel PO, không earmark hoặc thay công thức MRP toàn hệ thống.

**Căn cứ và trạng thái:** [BE báo cáo] GĐ7v3 Q30/§6 xác nhận khoảng trống hiển thị. [Đối chiếu] Fixture FE GĐ7 cũng có shortage25 khi demand40/received HOLD25/open PO15, nên đây là yêu cầu bổ sung visibility, không cáo buộc công thức hiện tại sai.

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
| R01 | Requirement hiển thị quantity đang chờ QC đúng Item+Warehouse+scope, tách khỏi available/open PO/rejected. | GET /api/v1/planning-runs/{id}/requirements | Có—visibility |
| R02 | Có nguồn receipt/line và quantity để mở nguồn; không trả giá cho MRP-only. | Projection nguồn mới hoặc mapping tương đương | Có—trace |
| R03 | Demand40/open PO15/available0/pendingQC25 vẫn net shortage25 theo rule hiện hành nhưng báo rõ25 đang chờ QC. | Run/requirement/suggestion DTO | Có—không đổi netting |
| R04 | Projection cung cấp warning khi pendingQcQuantity>0 để FE cảnh báo trước BUY; stale conversion giữ contract GĐ6v2, không xin đường ghi mới trong task này. | GET /api/v1/planning-runs/{id}/suggestions hoặc projection nguồn tương đương | Có—conversion UX |
| R05 | Snapshot nguồn đã lưu trong run không bị QC tương lai viết lại; run mới phản ánh kết quả QC mới. | POST /api/v1/planning-runs; GET run sources | Có—snapshot |

### Field/endpoint đề xuất, chưa deploy

| Field | Kiểu | Nullable | Nguồn/công thức | Dùng ở đâu UI |
|---|---|---|---|---|
| pendingQcQuantity | decimal scale6 | Không cho run mới hỗ trợ; legacy thiếu phải phân biệt unknown | Tổng received−accepted−rejected của receipt POSTED còn chờ đúng Item+Warehouse | Requirement/cảnh báo |
| pendingQcSourceCount | integer>=0 | Có với legacy chưa capture | Số receipt-line sources sau lọc | Link nguồn |
| sources[].goodsReceiptId / goodsReceiptLineId | UUID | Không | Receipt owner | Mở nguồn |
| sources[].warehouseId / itemId / uomCode | UUID/UUID/string | Không | Nguồn cùng scope | Đối chiếu đơn vị/kho |
| sources[].pendingQuantity / receiptVersion | decimal/integer | Không | Snapshot lúc run | Quantity/version |
| sources[].code / state | string/POSTED | Không | Mã receipt business | Không hiển thị UUID như nhãn |

Endpoint mới **đề xuất** nếu DTO hiện có không đủ: GET /api/v1/planning-runs/{runId}/pending-qc-supplies?requirementId=&warehouseId=&page=0&size=20, max100. Không body, success result là page {content:[source fields trên],page,size,totalElements,totalPages}; sắp code/id ổn định. Quyền PERM_MRP_READ, Company/Plant/Warehouse của run; không cần full GR_READ để đọc projection tối thiểu, không giá/contact. Không có idempotency cho GET. Có thể trả mapping endpoint tương đương thay vì tạo trùng. New run capture immutable projection server-side; không lấy supply phụ thuộc việc FE đã mở QC workspace.

### Fixture / nghiệm thu đề xuất

Demand40KG, safety0, no reserve/other supply, PO40. Receive25 POSTED đang pending25: open PO15, available0, shortage=max(0,40−0−15)=25, pending visibility25. Không cộng pending25 hai lần vào netting. QC25 đạt20/loại5 → pending0, available20, open15, shortage5. Run cũ vẫn giữ checkpoint25; run mới cho5. Không hiện rejected5 như pending hoặc forecast available.
Chờ live: partial QC, cùng Item khác kho/Plant, nhiều trang source/tổng header độc lập page, MRP-only không đọc tiền, cập nhật QC sau run và cảnh báo conversion hiện hành.

## §4 — Hợp đồng lỗi mong đợi

[Đề xuất/mapping] Tái dùng mã BE khi phù hợp; mã mới ghi rõ đề xuất. BE trả bảng tương đương nếu khác, không bắt FE parse message:

| Tình huống | HTTP | code / contract | FE hiển thị/xử lý |
|---|---|---|---|
| Page/filter sai | 400 | VALIDATION_ERROR | Giữ bộ lọc |
| Run/source version cũ tại conversion | 409 | CONCURRENT_MODIFICATION hoặc mã stale GĐ6v2 xác nhận | Refetch và xác nhận lại, không auto convert |
| Run không đọc được | 403 | PERMISSION_DENIED | Không lộ source ngoài scope |

Envelope hiện hành được giữ: code/message, result khi có dữ liệu và errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Riêng import revalidation409 có result=batch INVALID theo GĐ3. X-Trace-Id giữ cho chẩn đoán, không đưa Authorization/token vào evidence. Lỗi validation/business/stale không tự retry như lỗi mạng; giữ input và source context, chỉ retry cùng intent theo contract idempotency của endpoint. Không áp dụng replay chung cho master endpoints đang bỏ qua header.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; Q có thể cần PO chốt policy. Owner trả lời BE, PO duyệt nghiệp vụ nếu có thay đổi; tất cả hiện **chưa trả lời**.

1. **Q1:** Giữ netting hiện hành và chỉ bổ sung pending visibility/cảnh báo trước mắt được không? — FE nghiêng về: Có, tránh tự coi hàng chưa QC là dùng được — **CHẶN semantics nguồn; không chặn adapter khác**.
2. **Q2:** MRP_READ có được đọc projection receipt tối thiểu không giá mà không cần full GR_READ không? — FE nghiêng về: Có để quyền nghiệp vụ không phụ thuộc toàn master — **CHẶN projection auth**.
3. **Q3:** BE có thể map nguồn pending từ snapshot của run thay vì lịch sử động không? — FE nghiêng về: Có để run cũ không bị thay số — **CHẶN history acceptance**.

## §6 — FE đã tự xử lý

FE không tự cộng trang QC queue thành tổng pending cho MRP. UI sẽ cảnh báo nguồn chờ khi có projection chuẩn; chưa thêm warning/API ở phiên324. Không xin BE sửa công thức shortage đã thống nhất.

Phần còn lại FE tự đảm nhiệm khi được duyệt: DTO/adapter, hiển thị label/error, giữ input, quyền UI, cache invalidation và test phù hợp. Không xin BE sửa giao diện hoặc chứng minh pass FE thay FE. Không đổi flag/dependency, không tạo dữ liệu server trong phiên tài liệu; live acceptance vẫn chờ môi trường và contract được xác nhận.

## §7 — Tham chiếu

- BE_RESPONSE_GD_07_2026-10-18_v3.md — Q30/§6 pending chưa hiện MRP.
- FE_HANDOFF_GD_07_2026-10-14.md — fixture40/25/15 và quota/source.
- FE_HANDOFF_GD_06_2026-10-14.md — immutable runs/conversion stale; bản BE v2 còn thiếu.

Chỉ ghi tên file vì hai bên không dùng chung repo. File này tự chứa vấn đề, tiêu chí và ví dụ cần cho task; không yêu cầu BE mở source FE. Các file phản hồi được ghi theo đúng tên nhận, không tự sửa ngày/phiên bản của BE.

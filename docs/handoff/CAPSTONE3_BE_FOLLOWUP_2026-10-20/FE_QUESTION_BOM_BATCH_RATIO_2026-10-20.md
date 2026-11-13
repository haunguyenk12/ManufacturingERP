# Chốt giới hạn tỉ lệ BOM theo lô và cách xử lý công thức 1 trên 3

## §0 — Kết luận trong 3 dòng

- Vấn đề: GĐ5 lưu quantityPer scale6 và từ chối component1/output3; FE cần chốt đây là giới hạn tạm hay cần mở rộng mô hình.
- Ảnh hưởng: Form/import BOM theo lô, MRP/WO snapshots và giá thành FE/dev-demo; không tự làm tròn định mức để lưu được.
- Mức ưu tiên: P2 — thiếu thông tin/phân loại hoặc giới hạn phạm vi, còn có phần nghiệp vụ dùng được.

## §1 — Phạm vi

**Bản:** v1 · **Ngày dự kiến gửi:** 20/10/2026 · **FE:** mốc Session324, nối FE/mock GĐ1–11 và review phản hồi Session323. Đã lưu cục bộ; chưa tự gửi. Nếu gửi ngày khác đổi ngày tên/file; sửa sau gửi dùng _v2 và giữ cùng mạch task. Owner FE/BE do PO phân công, chưa có tên trong phiên này.

**Trong phạm vi:** Một quyết định về source-of-truth/tính chính xác của BOM theo lô; ghi rõ validation hiện hành và hướng xử lý nếu cần hỗ trợ1/3.

**Ngoài phạm vi:** Không yêu cầu BE viết lại MRP/WO/costing ngay trong file QUESTION; không tự nâng scale toàn DB, không thay số đã gõ hoặc đổi recipe để né lỗi.

**Căn cứ và trạng thái:** [BE báo cáo] GĐ5 Q1/Q2 lưu quantityPer, componentQuantity là projection; Q19 scrapRate scale6/file scrap%4 số lẻ. Chưa có actual request1/3 từ FE; đây là hỏi quyết định contract.

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

## §3 — Đầu ra mong đợi — Chỉ cần cung cấp/xác nhận, chưa giao BE code

**Không có yêu cầu sửa code BE trong file QUESTION này.** D01… là đầu ra tài liệu/quyết định; mọi phương án thay mô hình cần task triển khai được chốt sau.

| # | Đầu ra đúng/sai kiểm được | Endpoint/điểm xử lý | Chặn FE? |
|---|---|---|---|
| D01 | BE xác nhận trường hợp componentQuantity1/outputQuantity3 hiện bị400 tại lines[i].componentQuantity thay vì lưu số đã làm tròn. | PUT /api/v1/companies/{companyId}/bom-drafts; kind=bom import | Có—form/import |
| D02 | Chốt source-of-truth dài hạn: hỗ trợ lượng theo lô chính xác hoặc giữ giới hạn scale6 có PO phê duyệt. | Quyết định contract; không code trong task này | Có—policy |
| D03 | Nếu mở rộng, BE nêu impact MRP/WO snapshots/legacy và rounding tại lượng cần thực thi, không hồi tố master cũ. | Bản đề xuất/mapping sau quyết định | Có—rollout sau quyết định |

### Câu chuyện nghiệp vụ tự chứa

Một BOM tạo3 PCS thành phẩm từ1 KG nguyên liệu. Lượng theo lô đều là số nguyên hợp lệ; per-unit=1/3 không có biểu diễn decimal hữu hạn chính xác. Tăng từ6 lên12 chữ số chỉ xấp xỉ, không tự giải quyết source-of-truth. Với sản xuất6 PCS cần đúng2 KG trước scrap; dùng0.333333 sẽ cho1.999998 và có thể tạo thiếu hụt/variance giả.
Ví dụ dễ biểu diễn: output10/component25 → quantityPer2.5 phải tiếp tục đọc lại đúng25. Copy/version/master mutation không được sửa snapshot WO cũ.

### Hướng FE đề xuất để BE/PO quyết định

Ưu tiên mô hình lưu authoritative outputQuantity+componentQuantity theo lô, per-unit là projection tính lúc cần và có chính sách làm tròn tại biên thao tác. Không cần trả lời “tăng lên12 là exact”. Nếu BE/PO chọn tiếp tục giới hạn6 ở hiện tại, ghi rõ không hỗ trợ1/3 và UI/import phải báo lỗi; đây là quyết định phạm vi, chưa được tài liệu BE một mình thay PO duyệt.
Không có endpoint/field mới yêu cầu trong QUESTION. Atomic draft hiện có PUT /api/v1/companies/{companyId}/bom-drafts; upload POST /api/v1/data-import/batches kind=bom + commit hiện hành. Quyền BOM_READ+MANAGE cấp Company theo GĐ5, active/WO giữ quy tắc cũ; không mở role-name bypass. Source authority/rounding mới nếu được chọn sẽ thành task triển khai riêng với contract5cột, không tự triển khai theo câu hỏi.

### Kiểm chứng sau khi quyết định

Case1/3 phải hoặc lưu đúng theo mô hình batch và MRP6→2, hoặc bị400 có field/message rõ và0 dữ liệu thay đổi theo giới hạn đã duyệt. Không được200 rồi mở lại thành0.999999/3. File Excel và form phải cùng quyết định; scrap5.1234%=0.051234 được scale6,5.12345% vượt policy hiện hành phải lỗi, không cắt âm thầm. Chờ kiểm thực tế sau khi BE bật và FE có đủ contract.

## §4 — Hợp đồng lỗi mong đợi

Không đề nghị đổi mã lỗi trong task hỏi contract. Bảng là đối chiếu hoặc điều cần xác nhận, không phải lỗi đã quan sát:

| Tình huống | HTTP | code / contract | FE hiển thị/xử lý |
|---|---|---|---|
| Tỉ lệ không biểu diễn được theo contract hiện tại | 400 | VALIDATION_ERROR, field lines[i].componentQuantity | Nêu giới hạn tỉ lệ, không tự làm tròn |
| Import BOM có tỉ lệ không hỗ trợ | 201 batch INVALID theo luật import; commit không tạo | Mã rows[].issues[] BE cần xác nhận, không tự đoán | Đánh dấu dòng/nhóm, không HTTP giả |
| Version cũ | 409 | CONCURRENT_MODIFICATION | Giữ form/refetch |
| Quyết định mở rộng mô hình | — | Không có thay đổi lỗi/endpoint theo mặc định | Chỉ trả lời Q trước |

Envelope hiện hành được giữ: code/message, result khi có dữ liệu và errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Riêng import revalidation409 có result=batch INVALID theo GĐ3. X-Trace-Id giữ cho chẩn đoán, không đưa Authorization/token vào evidence. Lỗi validation/business/stale không tự retry như lỗi mạng; giữ input và source context, chỉ retry cùng intent theo contract idempotency của endpoint. Không áp dụng replay chung cho master endpoints đang bỏ qua header.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; Q có thể cần PO chốt policy. Owner trả lời BE, PO duyệt nghiệp vụ nếu có thay đổi; tất cả hiện **chưa trả lời**.

1. **Q1:** BE có thể giữ componentQuantity/outputQuantity là nguồn chính xác theo lô thay vì yêu cầu per-unit hữu hạn ngay khi save không? — FE nghiêng về: Có về hướng mục tiêu, cần đánh giá impact trước code — **CHẶN hỗ trợ recipe1/3, không chặn recipe biểu diễn được**.
2. **Q2:** Nếu chưa mở rộng, giới hạn không hỗ trợ1/3 có được ghi rõ là chờ PO chốt phạm vi không? — FE nghiêng về: Có, không tự coi BE chọn là PO đã duyệt — **CHẶN nghiệm thu toàn BOM theo lô**.
3. **Q3:** Form và kind=bom import có cùng rule tỉ lệ/scale hiện hành không? — FE nghiêng về: Có để file không tạo được dữ liệu form không lưu được — **CHẶN import/form parity**.

## §6 — FE đã tự xử lý

FE giữ số người nhập và hiển thị lỗi, không round recipe để ép200. Chưa sửa form/import/algorithm. Quyết định mở rộng schema cần một task được chốt riêng; file này không giao BE code ngay.

Phần còn lại FE tự đảm nhiệm khi được duyệt: DTO/adapter, hiển thị label/error, giữ input, quyền UI, cache invalidation và test phù hợp. Không xin BE sửa giao diện hoặc chứng minh pass FE thay FE. Không đổi flag/dependency, không tạo dữ liệu server trong phiên tài liệu; live acceptance vẫn chờ môi trường và contract được xác nhận.

## §7 — Tham chiếu

- BE_RESPONSE_GD_05_2026-10-16.md — Q1/Q2/Q19 per-unit storage và scale.
- FE_HANDOFF_GD_05_2026-10-13.md — yêu cầu lượng BOM theo lô, version/import.
- FE_HANDOFF_GD_06_2026-10-14.md — lượng nhu cầu/carry và rounding không tự thay đổi.

Chỉ ghi tên file vì hai bên không dùng chung repo. File này tự chứa vấn đề, tiêu chí và ví dụ cần cho task; không yêu cầu BE mở source FE. Các file phản hồi được ghi theo đúng tên nhận, không tự sửa ngày/phiên bản của BE.

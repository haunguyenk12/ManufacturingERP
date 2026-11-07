# Chốt cách giữ chính xác decimal trên wire và phạm vi số

## §0 — Kết luận trong 3 dòng

- Vấn đề: JSON number NUMERIC(19,6) có thể mất chữ số khi FE parse mặc định, dù dùng Decimal sau đó.
- Ảnh hưởng: Quantity/value lớn trên FE/dev-demo; không khẳng định mọi số nhỏ sai hoặc đã có response BE gây lỗi.
- Mức ưu tiên: P1 — chặn một phần tích hợp/nghiệm thu tương ứng, không phải toàn hệ thống ngừng chạy.

## §1 — Phạm vi

**Bản:** v1 · **Ngày dự kiến gửi:** 20/10/2026 · **FE:** mốc Session324, nối FE/mock GĐ1–11 và review phản hồi Session323. Đã lưu cục bộ; chưa tự gửi. Nếu gửi ngày khác đổi ngày tên/file; sửa sau gửi dùng _v2 và giữ cùng mạch task. Owner FE/BE do PO phân công, chưa có tên trong phiên này.

**Trong phạm vi:** Xác nhận range/token types và phương án lossless giữa FE/BE; mặc định ưu tiên FE thay parser giữ token để BE chưa phải đổi toàn API.

**Ngoài phạm vi:** Không yêu cầu BE đổi mọi response sang string ngay; không tự tăng DB precision hoặc nới domain; không gửi mã lỗi precision giả như lỗi runtime đã gặp.

**Căn cứ và trạng thái:** [BE báo cáo] GĐ2Q12/GĐ4Q13/GĐ5 mapping decimal response là JSON number, không đổi toàn API sang string. [FE kiểm offline20/10] JSON.parse số9999999999999.999999 cho10000000000000; không là request/response server.

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
| D01 | BE xác nhận response decimal là number và liệt kê precision/scale/range từng nhóm quantity/value/minutes/rates hiện hành. | Contract numeric; không endpoint mới | Có—exact mapping |
| D02 | BE cung cấp mẫu số biên dưới dạng JSON text nguyên gốc, không qua JS number hoặc screenshot; xác định string decimal request có được nhận hay không. | Tài liệu/ví dụ offline, ghi rõ minh họa hay actual | Có—request/response typing |
| D03 | Hai bên chốt FE dùng parser lossless giữ nguyên numeric token, integer vẫn integer; BE không phải đổi type toàn API nếu phương án này đáp ứng. | Quyết định interface; không giao BE code trong task này | Có—full range |

### Phép kiểm offline đã chạy (không phải API BE)

```javascript
const text = '{"quantity":9999999999999.999999}';
JSON.parse(text).quantity; // 10000000000000
```
NUMERIC(19,6) có13 chữ số nguyên+6 số lẻ. JS number không giữ được toàn bộ range; Decimal nhận lại10000000000000 không biết token gốc là9999999999999.999999. UUID và count/version không được biến thành decimal strings tùy tiện.

### Phương án FE nghiêng về

Giữ contract response number như BE đã chọn; FE đọc text bằng parser lossless đã được kiểm toàn app và giữ decimal tokens dưới dạng text nội bộ trước Decimal. **Đây là thay đổi FE chưa thực hiện**, cần kiểm client/interceptor/error parsing/integer schemas và build riêng; không thêm dependency trong phiên tài liệu.
Nếu BE chọn decimal string cho một extension thì cần field matrix và version/migration plan, không đổi âm thầm endpoint đang chạy. Nếu giới hạn domain để dùng parser thường, phải công bố range+scale an toàn và test biên, không nói chỉ6 số lẻ là đủ.
Không yêu cầu endpoint/field mới trong task này. Ví dụ minh họa có thể dùng raw JSON text; không coi một object đã parse qua number là bằng chứng exact.

### Tiêu chí đối chiếu sau quyết định

Các chuỗi giá trị 0 / 0.000001 / 999999999.999999 / 9999999999999.999999 giữ đúng về Decimal sau serialize/parse thích hợp; vượt DB range bị validation, integer version5 gửi dạng5, không5.0 hoặctoFixed. Cho request decimal cần BE công bố number/string/both và exponent policy riêng; không suy response number nghĩa request phải Number(input). Chưa chạy lại app tests hoặc contract/live.

## §4 — Hợp đồng lỗi mong đợi

Không đề nghị đổi mã lỗi trong task hỏi contract. Bảng là đối chiếu hoặc điều cần xác nhận, không phải lỗi đã quan sát:

| Tình huống | HTTP | code / contract | FE hiển thị/xử lý |
|---|---|---|---|
| Số input ngoài range/quá scale | 400 | VALIDATION_ERROR theo contract hiện hành | Field error, giữ input |
| Parse mất precision trong FE | Không có HTTP giả | Không phát minh BE error code | Chặn FE mapping sai; không cáo buộc server500 |
| Câu hỏi lựa chọn numeric contract | — | Không có thay đổi code/lỗi BE theo mặc định | Chỉ cần trả lời Q |

Envelope hiện hành được giữ: code/message, result khi có dữ liệu và errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Riêng import revalidation409 có result=batch INVALID theo GĐ3. X-Trace-Id giữ cho chẩn đoán, không đưa Authorization/token vào evidence. Lỗi validation/business/stale không tự retry như lỗi mạng; giữ input và source context, chỉ retry cùng intent theo contract idempotency của endpoint. Không áp dụng replay chung cho master endpoints đang bỏ qua header.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; Q có thể cần PO chốt policy. Owner trả lời BE, PO duyệt nghiệp vụ nếu có thay đổi; tất cả hiện **chưa trả lời**.

1. **Q1:** BE có giữ response decimal JSON number cho contract hiện hành không? — FE nghiêng về: Có theo phản hồi đã gửi, tránh breaking change hàng loạt — **CHẶN numeric matrix**.
2. **Q2:** FE dùng parser lossless trước Decimal có được xem là hướng đáp ứng exact mà chưa cần BE đổi wire type không? — FE nghiêng về: Có để giữ compatibility — **CHẶN full-range integration**.
3. **Q3:** BE có nhận decimal string chuẩn trong request cho các field BigDecimal không? — FE nghiêng về: Có nếu thực tế hỗ trợ, cần xác nhận từng nhóm trước adapter — **CHẶN serializer; không đổi integer**.
4. **Q4:** BE có thể cung cấp range/scale chính thức theo từng nhóm số không? — FE nghiêng về: Có vì value/minutes có thể khác quantity — **CHẶN boundary acceptance**.

## §6 — FE đã tự xử lý

Đã chứng minh rủi ro bằng Node offline và dùng Decimal trong service/mô hình số; parser HTTP mặc định hiện chưa lossless. FE nhận trách nhiệm sửa parser/serializer theo quyết định, không chuyển việc đó thành BE defect. Chưa đổi app/dependency.

Phần còn lại FE tự đảm nhiệm khi được duyệt: DTO/adapter, hiển thị label/error, giữ input, quyền UI, cache invalidation và test phù hợp. Không xin BE sửa giao diện hoặc chứng minh pass FE thay FE. Không đổi flag/dependency, không tạo dữ liệu server trong phiên tài liệu; live acceptance vẫn chờ môi trường và contract được xác nhận.

## §7 — Tham chiếu

- BE_RESPONSE_GD_02_2026-10-14_v2.md — Q12/§3 integer strict.
- BE_RESPONSE_GD_04_2026-10-15.md — Q13 numeric wire.
- BE_RESPONSE_GD_05_2026-10-16.md — mapping number/minutes precision.

Chỉ ghi tên file vì hai bên không dùng chung repo. File này tự chứa vấn đề, tiêu chí và ví dụ cần cho task; không yêu cầu BE mở source FE. Các file phản hồi được ghi theo đúng tên nhận, không tự sửa ngày/phiên bản của BE.

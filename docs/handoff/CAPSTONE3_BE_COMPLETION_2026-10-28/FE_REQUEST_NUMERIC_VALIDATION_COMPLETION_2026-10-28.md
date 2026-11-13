# Hoàn thiện validation số trước khi DB làm tròn

## §0 — Kết luận trong 3 dòng

- Vấn đề: Báo cáo CF còn nhóm đường ghi nhận quá scale rồi DB làm tròn, response đầu có thể khác dữ liệu đọc lại.
- Ảnh hưởng: Độ chính xác số lượng/giá chuẩn/settings/minutes trên các đường được báo còn hở; FE form không thay server validation.
- Mức ưu tiên: P1 — chặn phần tích hợp/nghiệm thu dữ liệu tương ứng; không phải P0 toàn hệ thống.

## §1 — Phạm vi và quyết định

**Phiên bản:** v1 · **Ngày dự kiến gửi:** 28/10/2026 · **Mốc FE:** Session326. Owner FE/BE do PO phân công, chưa có tên trong phiên.
Bản đầu của đầu việc này; các handoff gốc vẫn giữ lịch sử.
Nếu gửi ngày khác đổi ngày trước gửi; sửa nội dung sau gửi dùng phiên bản tiếp theo, giữ cùng mạch task.

**Trong phạm vi:** Các field/path còn thiếu validation trong numeric contract; áp đúng scale/range đã công bố, gồm legacy aliases/import ghi cùng dữ liệu.

**Ngoài phạm vi:** Không đổi response number sang string toàn hệ thống, không tăng DB precision, không hạ mọi field xuống6 nếu contract thật là12; không sửa dữ liệu lịch sử đã round.

[PO đã chốt] BOM per-unit/rate giữ giới hạn hiện hành; không tự round recipe. Với các trường khác, yêu cầu harden theo precision/scale đúng của chúng, không suy tất cả money/minutes đều giống quantity.

**Căn cứ:** [BE báo cáo] CF §2/§8 nêu standard-cost, settings, manual demand, supplier-item, purchase request, legacy PO/SO/WO và routing minutes còn nhận7 số lẻ ở một số field. Numeric matrix chi tiết chưa được cung cấp; chưa có lỗi live FE tái hiện.

Nhãn: **[PO đã chốt]** là quyết định trực tiếp của PO ngày28/10, thay cho thông tin “BE báo PO đã chốt27/10” ở các nhận xét trước; chỉ áp cho ba chính sách đã nêu, không tự duyệt mọi hạn chế khác. **[BE báo cáo]** không phải FE live acceptance. Mọi tiêu chí/field/mã lỗi mới ghi dưới đây là **[Đề xuất]** hoặc cần BE mapping; chưa được coi là API đã deploy.

## §2 — Bằng chứng và giới hạn

**Không có lượt gọi BE trong phiên tài liệu.** PO cho biết BE chưa bật; không khởi động BE, không chạy SQL/migration hoặc gọi endpoint. Không dùng báo cáo BE làm bằng chứng FE đã chạy.

| Mảnh bằng chứng live cùng lượt | Hiện có |
|---|---|
| Request thật method/path/header/body | Không có |
| Response thật HTTP/code/nguyên văn | Không có |
| X-Trace-Id từ response thật | Không có, không tự tạo |
| Môi trường/giờ/bản BE mới nhất | Chưa kiểm; ngày viết28/10/2026, không có giờ/live build |
| ID record BE cùng lượt | Không có; fixture phía dưới là minh họa đề xuất |

```text
Mong đợi: BE trả mapping/đầu ra kiểm được ở §3 và trả từng xác nhận §5 theo quyết định PO.
Thực tế: Đã đối chiếu tài liệu BE offline; không báo FE_DEFECT hoặc nghiệm thu live trong file này.
```

## §3 — Đầu ra mong đợi — BE hoàn thiện hoặc cung cấp mapping đã có

**[Đề xuất triển khai]** R01… là tiêu chí đúng/sai kiểm được. Nếu đã đáp ứng ở bản mới hơn, cung cấp mapping/test/build thay vì code trùng; chỉ sửa phần còn thiếu.

| # | Đầu ra kiểm được | Endpoint/điểm xử lý | Chặn FE? |
|---|---|---|---|
| R01 | BE gửi bảng từng method/path/field/precision/scale/range trước sửa, tách field6/12 và integer. | Numeric-contract artifact; không endpoint mới | Có—coverage |
| R02 | Field scale6 nhận1.2345678 hoặc1.2345670 phải400 trước ghi; field scale12 vẫn giữ12 theo contract, không hạ precision. | Các path trong matrix R01 | Có—exact data |
| R03 | Vượt integer digits/range trả validation có field, không chờ DB overflow rồi trả422 không rõ field. | Các path trong matrix R01 | Có—error mapping |
| R04 | Invalid multi-line không ghi một phần master/doc/audit/stock/replay-success; read-back dữ liệu cũ giữ nguyên. | Legacy/new writes và import handler cùng nguồn | Có—atomicity |
| R05 | Trường hợp valid đọc lại bằng giá trị decimal exact như response; integer không bị nhận thực rồi cắt phần lẻ. | Field matrix + GET read-back | Có—persistence |

### Quy tắc đề xuất / ví dụ chưa gọi BE

NUMERIC(19,6): tối đa13 chữ số nguyên và6 số lẻ; max9.999.999.999.999,999999. 1.234567 hợp lệ,1.2345678 và1.2345670 quá lexical scale6 theo chính sách strict đã nêu phải lỗi; không lưu thành1.234568 hoặc1.234567. Giá trị âm chỉ hợp lệ ở signed delta có contract cho phép; quantity nhận/xuất phải>0, rate0 có thể hợp lệ theo field.
Numeric(25,12) giữ range/scale của nó; không dùng rule6 chung cho average/minutes snapshot. Với integer giữ ruleBE từ GĐ2: không chấp nhận5.0 hoặc chuỗi có phần thập phân, không cắt1.5→1. Decimal request có thể number/string theo matrix; validation phải áp trước serialization/coercion làm mất scale gốc nếu contract strict như trên.

### Bảng field BE cần điền trong phản hồi

| Field (gắn method/path cụ thể) | Kiểu/precision-scale | Nullable | Nguồn/rule server | Dùng ở FE |
|---|---|---|---|---|
| Các field nhóm CF còn thiếu validation | Decimal/integer theo từng DTO, chưa có matrix chi tiết | Theo DTO, không tự đoán | Cùng validation trước DB cho mọi alias | Form/adapter/error |

Không xin field response mới; bảng là yêu cầu BE công bố contract còn thiếu. Giữ authorization hiện hành theo resource; không tạo role admin bypass numeric. Giữ Idempotency-Key theo từng endpoint, invalid không lưu success receipt; sau sửa input dùng intent/key mới khi cần theo fingerprint contract. Không tự áp replay cho master đang không hỗ trợ.
Chờ test server từng field/path/biên null/0/âm/max/overflow/scale; multi-line dòng cuối invalid rollback; number và string; write/read-back; alias cũ; import; quyền/2Plant. Đây là BE validation task, FE parser lossless là đầu việc FE riêng chưa thực hiện.

## §4 — Hợp đồng lỗi

Tái dùng mã BE hiện có khi đúng nghĩa. Mã/mapping mới là đề xuất và cần BE trả bảng tương đương nếu khác; không bắt FE parse message.

| Tình huống | HTTP | code / field | FE hiển thị/xử lý |
|---|---|---|---|
| Quá scale/range/sai định dạng input | 400 | VALIDATION_ERROR; errors[].field chính xác kể cả nested | Hiện lỗi field, giữ input |
| Integer gửi phần thập phân | 400 | VALIDATION_ERROR | Không cắt số |
| Dữ liệu lỗi DB không thuộc invalid input đã biết | Theo contract hiện hành | Không đổi mọi lỗi hệ thống thành validation | Giữ trace, không bịa field |

Giữ envelope code/message và result khi có dữ liệu, errors[] khi có field error. Không ép result:null cho mọi lỗi; import409 IMPORT_REVALIDATION_FAILED có result=batch INVALID theo contract batch. Missing object do redaction khác field unknown/null; không suy số0.
Giữ X-Trace-Id khi có, che Authorization/mật khẩu/token. Validation/business/stale không auto retry như network; giữ input/source context, version server là authoritative. Retry cùng intent dùng key cũ khi endpoint hỗ trợ; intent thay đổi dùng key theo fingerprint policy, không giả master endpoints có replay.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi lại PO có chốt ba chính sách hay chưa — PO đã xác nhận ngày28/10. BE xác nhận mapping/triển khai/bằng chứng; quyết định mới ngoài ba chính sách vẫn phải nêu riêng. Tất cả Q dưới đây chưa có phản hồi cho bản này.

1. **Q1:** BE có thể giữ type wire hiện hành và chỉ thêm guard trước DB không? — FE nghiêng về: Có, giảm breaking type change — **KHÔNG CHẶN wire type, chặn validation coverage**.
2. **Q2:** Matrix R01 có thể chỉ rõ path mới/cũ nào được kiểm bằng test và còn deferred không? — FE nghiêng về: Có để không tuyên bố toàn API đã strict khi còn alias — **CHẶN acceptance**.
3. **Q3:** Strict scale có áp dụng đồng đều cho string và number ở cùng field không? — FE nghiêng về: Có để không có đường né bằng token type — **CHẶN serializer validation**.

## §6 — FE đã xử lý và phần FE tự đảm nhiệm

FE giữ Decimal nội bộ nhưng parser HTTP chưa lossless; sẽ tự sửa parser/validator sau duyệt, không yêu cầu BE đổi JSON number toàn hệ thống. Không coi chặn form là đã khép server validation.

Phiên326 chỉ tạo tài liệu và ghi quyết định; chưa sửa ứng dụng, parser, dependency, API flag, dữ liệu server hoặc chạy test/build/browser. FE sẽ tự mapping DTO, label, quyền UI, error/field, input preservation và test sau khi được duyệt code; không giao BE sửa FE. Nhận báo cáo BE không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- BE_REPORT_CF_COMPLETED_2026-10-28.md — §2/§8 nhóm còn round DB.
- BE_RESPONSE_GD_02_2026-10-14_v2.md — strict integer đã chốt.
- FE_QUESTION_DECIMAL_WIRE_PRECISION_2026-10-20.md — parser/wire, khác server validation.

Chỉ ghi tên file, không phụ thuộc repo/đường dẫn máy FE. Nội dung task đã tự chứa quyết định, phạm vi và tiêu chí; nếu reference đang thiếu thì BE cung cấp theo contract task, không coi file đó đã được FE đọc.

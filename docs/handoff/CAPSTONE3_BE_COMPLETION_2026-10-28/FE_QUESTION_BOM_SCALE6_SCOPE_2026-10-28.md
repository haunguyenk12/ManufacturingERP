# Xác nhận giới hạn BOM theo lô trong phạm vi đơn giản

## §0 — Kết luận trong 3 dòng

- Vấn đề: PO đã chọn giữ giới hạn hiện hành; cần xác nhận validation form/import cùng rule, không mở rộng tỉ lệ1/3.
- Ảnh hưởng: Form/import BOM và snapshot nhu cầu; không yêu cầu thay model lớn.
- Mức ưu tiên: P2 — cần chốt rõ contract/phạm vi để tránh hiểu sai; không mở thêm phân hệ.

## §1 — Phạm vi và quyết định

**Phiên bản:** v1 · **Ngày dự kiến gửi:** 28/10/2026 · **Mốc FE:** Session326. Owner FE/BE do PO phân công, chưa có tên trong phiên.
Bản đầu của đầu việc này; các handoff gốc vẫn giữ lịch sử.
Nếu gửi ngày khác đổi ngày trước gửi; sửa nội dung sau gửi dùng phiên bản tiếp theo, giữ cùng mạch task.

**Trong phạm vi:** Ghi nhận quyết định BOM ratio hữu hạn scale6, thông báo field/dòng rõ, không thay công thức người nhập.

**Ngoài phạm vi:** Không rational-number model, không tăng scale toàn DB, không hỗ trợ1/3 bằng số làm tròn, không sửa snapshot WO cũ.

**[PO đã chốt trực tiếp ngày28/10/2026]** Giữ giới hạn6 số lẻ cho tỉ lệ per-unit theo contract BE hiện có; chưa hỗ trợ recipe như1 KG/3 PCS. Báo lỗi rõ ở form/import, không tự sửa định mức để lưu. Tiếp tục giới hạn scrapRate6 số lẻ và % trong mẫu tối đa4 số lẻ như BE mô tả.

**Căn cứ:** [BE báo cáo] GĐ5Q2 và CF-0 giữ quantityPer scale6. Quyết định này trả lời câu hỏi BOM_BATCH_RATIO20/10, không giao BE code mở rộng CF-7.

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

## §3 — Đầu ra tài liệu/contract — chưa giao BE code

**Không có yêu cầu sửa code BE trong file QUESTION.** D01… là xác nhận/cung cấp contract hiện có. Nếu phát hiện implementation chưa đáp ứng, BE nêu sai khác để xử lý đúng task; không tự mở rộng phạm vi theo câu hỏi.

| # | Đầu ra kiểm được | Endpoint/điểm xử lý | Chặn FE? |
|---|---|---|---|
| D01 | Xác nhận recipe output3/component1 bị400 field lines[0].componentQuantity, không tạo/update BOM. | PUT /api/v1/companies/{companyId}/bom-drafts | Chặn form parity |
| D02 | Xác nhận import cùng recipe tạo batch INVALID với INVALID_FORMAT tại cột F; commit không tạo BOM nào. | POST /api/v1/data-import/batches kind=bom; POST /api/v1/data-import/batches/{id}/commit | Chặn import parity |
| D03 | Xác nhận output10/component25 vẫn lưu/đọc đúng25 và quantityPer2.5; không làm tròn âm thầm hoặc hồi tố. | BOM draft/detail/tree | Chặn validation acceptance |

### Ví dụ và điểm biên — chưa gọi BE

Output3 PCS/component1 KG → per-unit1/3 không hữu hạn; chặn theo quyết định. Output10/component25 →2.5 hợp lệ. Không đổi1 KG thành0.999999 KG để ép lưu.
Scrap5.1234% →0.051234 hợp lệ;5.12345% →0.0512345 vượt6 số lẻ, phải lỗi thay vì cắt. Quantity/input vẫn positive và0<=scrapRate<1.
Đây là giới hạn phạm vi được PO chốt, không gọi lỗi validation này là BE defect. Chỉ ghi “hợp lệ” khi mọi rule tham chiếu/state/quyền khác cũng đạt.

### Không có endpoint/field mới

Giữ draft atomic và batch chung kind=bom; Company BOM_READ+MANAGE như contract GĐ5, current authorization trước replay. Cần xác nhận field/code lỗi dùng thống nhất; không yêu cầu riêng template endpoint.
Tiêu chí kiểm sau khi BE bật: form/import cùng rule; batch có một nhóm sai không commit riêng nhóm còn lại; lỗi không thay BOM cũ; không có app test/live trong phiên tài liệu.

## §4 — Hợp đồng lỗi

Không yêu cầu đổi mã lỗi trong task QUESTION. Bảng là mapping hiện hành cần xác nhận, không là lỗi FE đã quan sát.

| Tình huống | HTTP | code / field | FE hiển thị/xử lý |
|---|---|---|---|
| Recipe không biểu diễn scale6 | 400 | VALIDATION_ERROR, lines[i].componentQuantity | Giải thích giới hạn per-unit |
| Scrap vượt scale/range | 400 | VALIDATION_ERROR theo field | Không tự làm tròn |
| Dòng import ratio sai | 201 | SUCCESS với batch INVALID, issue INVALID_FORMAT cột F theo CF report | Hiện đúng dòng, không commit |
| Commit INVALID | 409 | STATE_CONFLICT | Yêu cầu sửa file/tải lại |

Giữ envelope code/message và result khi có dữ liệu, errors[] khi có field error. Không ép result:null cho mọi lỗi; import409 IMPORT_REVALIDATION_FAILED có result=batch INVALID theo contract batch. Missing object do redaction khác field unknown/null; không suy số0.
Giữ X-Trace-Id khi có, che Authorization/mật khẩu/token. Validation/business/stale không auto retry như network; giữ input/source context, version server là authoritative. Retry cùng intent dùng key cũ khi endpoint hỗ trợ; intent thay đổi dùng key theo fingerprint policy, không giả master endpoints có replay.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi lại PO có chốt ba chính sách hay chưa — PO đã xác nhận ngày28/10. BE xác nhận mapping/triển khai/bằng chứng; quyết định mới ngoài ba chính sách vẫn phải nêu riêng. Tất cả Q dưới đây chưa có phản hồi cho bản này.

1. **Q1:** Validation ratio form và import hiện đã đúng D01/D02 không? — FE nghiêng về: Có nếu đã có mapping/test, không code trùng — **CHẶN form/import parity**.
2. **Q2:** BE có giữ nguyên dữ liệu và snapshot lịch sử khi áp dụng giới hạn không? — FE nghiêng về: Có, chỉ kiểm các lần ghi mới — **CHẶN rollout**.
3. **Q3:** BE có thể gửi file numeric/BOM-ratio đã nhắc trong CF report để FE map đúng field/error không? — FE nghiêng về: Có, cung cấp tài liệu hiện có — **CHẶN validation mapping**.

## §6 — FE đã xử lý và phần FE tự đảm nhiệm

FE sẽ báo lỗi rõ và giữ nguyên số nhập; chưa thay parser/validator hay form trong phiên này. Không xin BE tăng precision để hỗ trợ1/3.

Phiên326 chỉ tạo tài liệu và ghi quyết định; chưa sửa ứng dụng, parser, dependency, API flag, dữ liệu server hoặc chạy test/build/browser. FE sẽ tự mapping DTO, label, quyền UI, error/field, input preservation và test sau khi được duyệt code; không giao BE sửa FE. Nhận báo cáo BE không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- BE_RESPONSE_GD_05_2026-10-16.md — Q1/Q2/Q19 scale.
- BE_REPORT_CF_COMPLETED_2026-10-28.md — CF-7 giữ giới hạn.
- FE_QUESTION_BOM_BATCH_RATIO_2026-10-20.md — câu hỏi cũ đã được PO chốt về phạm vi.

Chỉ ghi tên file, không phụ thuộc repo/đường dẫn máy FE. Nội dung task đã tự chứa quyết định, phạm vi và tiêu chí; nếu reference đang thiếu thì BE cung cấp theo contract task, không coi file đó đã được FE đọc.

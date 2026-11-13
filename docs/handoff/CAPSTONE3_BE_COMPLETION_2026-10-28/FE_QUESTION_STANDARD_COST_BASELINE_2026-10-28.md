# Xác nhận áp dụng giá chuẩn và hoãn sổ giá trị tồn/WIP

## §0 — Kết luận trong 3 dòng

- Vấn đề: PO đã chốt nguồn giá chuẩn; cần ghi rõ contract GĐ11 và phạm vi valuation được hoãn để hai bên không triển khai trùng.
- Ảnh hưởng: Giá thành/đóng WO và nhãn chi phí FE; chưa phải nghiệm thu API.
- Mức ưu tiên: P2 — cần chốt rõ contract/phạm vi để tránh hiểu sai; không mở thêm phân hệ.

## §1 — Phạm vi và quyết định

**Phiên bản:** v1 · **Ngày dự kiến gửi:** 28/10/2026 · **Mốc FE:** Session326. Owner FE/BE do PO phân công, chưa có tên trong phiên.
Bản đầu của đầu việc này; các handoff gốc vẫn giữ lịch sử.
Nếu gửi ngày khác đổi ngày trước gửi; sửa nội dung sau gửi dùng phiên bản tiếp theo, giữ cùng mạch task.

**Trong phạm vi:** Xác nhận implementation/mapping giá chuẩn chụp tại RELEASE, giữ legacy/unknown và ranh giới với inventory/WIP ledger.

**Ngoài phạm vi:** Không triển khai moving average, ledger giá trị tồn/WIP, giá mua thực tế, GL/FX hoặc định giá nhiều nguồn.

**[PO đã chốt trực tiếp ngày28/10/2026]** Dùng giá chuẩn vật tư có tại thời điểm phát hành WO; chụp và khóa theo lệnh. Tiếp tục hoãn sổ giá trị tồn/WIP (CF-6), không coi hoãn là đã nghiệm thu. Giá thành vật tư phản ánh lượng tiêu thụ × giá chuẩn đã chụp; labor/overhead theo thời gian/đơn giá snapshot. Không sửa lịch sử.

**Căn cứ:** [BE báo cáo] GĐ11 D1 và CF report đã theo hướng này. Khác nguồn average-price FE/mock trước đây; đây là chốt policy, không báo BE chưa có13 endpoint.

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
| D01 | Cung cấp mapping đặt/đọc giá chuẩn, baseline RELEASE và cost sections C1–C13 hiện có; không giao viết lại hệ giá thành. | PUT /api/v1/companies/{companyId}/items/{itemId}/standard-cost; GET /api/v1/work-orders/{id}/cost-baseline; GET /api/v1/work-orders/{id}/production-costs | Chặn adapter giá chuẩn |
| D02 | Xác nhận đổi giá master sau release không thay baseline; WO thiếu giá giữ unknown và không hồi tố bằng giá mới. | POST /api/v1/work-orders/{id}/release; cost-baseline | Chặn snapshot acceptance |
| D03 | Nêu rõ legacy variance dùng công thức khác với extension costing và các trường read bị omit theo quyền. | GET /api/v1/work-orders/{id}/variance; production-costs | Chặn label/mapping |

### Fixture chính sách — minh họa, chưa gọi BE

Item RM giá chuẩn1.000 VND/KG lúc RELEASE, planned material20 KG; đổi master thành1.200 sau RELEASE. Tiêu thụ21 KG → kế hoạch20.000, thực tế vật tư21.000 theo snapshot1.000; không đổi thành25.200. Lịch sử WO và cost run đã chốt không bị viết lại. WO mới release sau thay giá lấy1.200.
Thiếu giá khác known0: không tự dùng0, giá bán hay giá mua để thay; production có thể tiếp tục theo rule GĐ11, financial close chặn khi thiếu dữ liệu. PO không mở lại source valuation ledger chỉ để làm fixture.

### Chỉ cần contract, chưa giao BE code trong file này

Đề nghị gửi DTO request/result hiện hành của standard-cost (tên field, currency/null, scale/range, permission/scope, version/replay) vì FE chưa có OpenAPI mới; không tự đoán body rồi gọi server. Với C7/C10 dùng expectedSourceVersion, các version và key theo GĐ11 đã mô tả. Những endpoint đã có tương đương chỉ cần mapping/build.
UI FE sẽ dùng nhãn “Chi phí vật tư theo giá chuẩn”; tồn kho chưa có value không hiển thị như giá trị0. Non-tracked post-close COGS chưa biết và old variance khác công thức tiếp tục được ghi là giới hạn, không tự cam kết mở rộng chúng.

## §4 — Hợp đồng lỗi

Không yêu cầu đổi mã lỗi trong task QUESTION. Bảng là mapping hiện hành cần xác nhận, không là lỗi FE đã quan sát.

| Tình huống | HTTP | code / field | FE hiển thị/xử lý |
|---|---|---|---|
| Thiếu nguồn giá khi tính/chốt | 409 | COST_DATA_INCOMPLETE, errors theo checklist GĐ11 | Hiện thiếu giá, không gán0 |
| Nguồn thay đổi | 409 | COST_SOURCE_STALE | Đọc lại/tính lại giữ context |
| Không quyền | 403 | PERMISSION_DENIED | Không hiện tiền ngoài quyền |

Giữ envelope code/message và result khi có dữ liệu, errors[] khi có field error. Không ép result:null cho mọi lỗi; import409 IMPORT_REVALIDATION_FAILED có result=batch INVALID theo contract batch. Missing object do redaction khác field unknown/null; không suy số0.
Giữ X-Trace-Id khi có, che Authorization/mật khẩu/token. Validation/business/stale không auto retry như network; giữ input/source context, version server là authoritative. Retry cùng intent dùng key cũ khi endpoint hỗ trợ; intent thay đổi dùng key theo fingerprint policy, không giả master endpoints có replay.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi lại PO có chốt ba chính sách hay chưa — PO đã xác nhận ngày28/10. BE xác nhận mapping/triển khai/bằng chứng; quyết định mới ngoài ba chính sách vẫn phải nêu riêng. Tất cả Q dưới đây chưa có phản hồi cho bản này.

1. **Q1:** BE xác nhận baseline vật tư mới đúng nguồn giá chuẩn theo quyết định này không? — FE nghiêng về: Có, giữ implementation GĐ11, không chuyển sang moving average — **CHẶN adapter cost**.
2. **Q2:** BE có thể cung cấp contract standard-cost đầy đủ trong phản hồi thay yêu cầu FE đoán DTO không? — FE nghiêng về: Có, dùng DTO đã triển khai — **CHẶN đặt giá trước RELEASE**.
3. **Q3:** CF-6 có thể được ghi là hoãn, không đánh dấu đạt bằng kết quả GĐ11 không? — FE nghiêng về: Có, để báo cáo đúng phạm vi — **KHÔNG CHẶN quantity/standard-cost code**.

## §6 — FE đã xử lý và phần FE tự đảm nhiệm

FE chưa đổi baseline mock đang lấy average; sẽ đổi/mapping nhãn theo giá chuẩn sau khi được duyệt code FE. Không yêu cầu BE sửa giao diện.

Phiên326 chỉ tạo tài liệu và ghi quyết định; chưa sửa ứng dụng, parser, dependency, API flag, dữ liệu server hoặc chạy test/build/browser. FE sẽ tự mapping DTO, label, quyền UI, error/field, input preservation và test sau khi được duyệt code; không giao BE sửa FE. Nhận báo cáo BE không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- BE_RESPONSE_GD_11_2026-10-27.md — D1/baseline/cost extension.
- BE_REPORT_CF_COMPLETED_2026-10-28.md — CF-6 hoãn.
- FE_REQUEST_INVENTORY_VALUATION_2026-10-20.md — yêu cầu ledger được hoãn theo quyết định mới, không đóng là pass.

Chỉ ghi tên file, không phụ thuộc repo/đường dẫn máy FE. Nội dung task đã tự chứa quyết định, phạm vi và tiêu chí; nếu reference đang thiếu thì BE cung cấp theo contract task, không coi file đó đã được FE đọc.

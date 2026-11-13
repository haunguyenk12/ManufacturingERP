# Bộ tài liệu BE bổ sung sau phản hồi CF ngày30/10

30/10/2026 · Session328 · **Mục lục nội bộ PO/FE; gửi từng file task tự chứa cho BE.**

## Quyết định giữ nguyên

PO28/10: standard cost tại RELEASE, hoãn stock/WIP ledger, BOMper-unit6/no1/3, allowance×(1+rate). PO30/10: MRP CEILING6 và WO HALF_UP6, chênh tối đa0.000001 mỗi dòng cùng source/cơ sở; BE bổ sung test còn thiếu trước khi chốt hoàn tất. Không mở tính năng mới hoặc đổi lịch sử.
Không dùng tolerance mỗi dòng để che sai BOM/version/UOM hoặc áp thành mức sai khác toàn bộ nhiều cấp. Baseline dùng WO quantity đã lưu, không nhân allowance hai lần.

## Gói gửi BE

**4 task tiếp nối:1 FE_QUESTION +3 FE_REQUEST**, mỗi task có8mục§0–§7 và message4dòng.

[Tải ZIP gồm4 tài liệu và4 tin nhắn](CAPSTONE3_BE_SUPPLEMENT_2026-10-30.zip). Mục lục nội bộ này không nằm trong ZIP; từng task vẫn tự chứa để gửi riêng.

| Thứ tự | Nội dung | Bản mới | Tài liệu | Tin nhắn |
|---|---|---|---|---|
| 1 | Gửi đủ contract và OpenAPI còn thiếu sau CF-8 | v3 / FE_QUESTION | [Mở](FE_QUESTION_GD06_GD07_CONTRACT_2026-10-30_v3.md) | [Mở](FE_QUESTION_GD06_GD07_CONTRACT_2026-10-30_v3_MESSAGE.md) |
| 2 | Bổ sung test xuyên BOM–MRP–WO–giá thành theo cách làm tròn đã chốt | v2 / FE_REQUEST | [Mở](FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT_2026-10-30_v2.md) | [Mở](FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT_2026-10-30_v2_MESSAGE.md) |
| 3 | Bổ sung test chuyển kho, giữ chỗ và xuất hàng không theo lô đang chờ QC | v3 / FE_REQUEST | [Mở](FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-30_v3.md) | [Mở](FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-30_v3_MESSAGE.md) |
| 4 | Chạy lại smoke phiếu nhập và chốt giá thành trên binary sau bản sửa version | v2 / FE_REQUEST | [Mở](FE_REQUEST_COST_CLOSE_VERIFICATION_2026-10-30_v2.md) | [Mở](FE_REQUEST_COST_CLOSE_VERIFICATION_2026-10-30_v2_MESSAGE.md) |

## Phần đã được ghi nhận, không giao lại

- Standard-cost/BOM policy đã được PO chốt và BE có DTO/bằng chứng: không hỏi lại, không mở average ledger hoặc recipe1/3.
- CF-9 lot race và CF-10 numeric guards đã được BE báo có test/smoke: không mở lại task viết validator/unique chỉ để có thay đổi.
- A39/A40, QC count/adjust đã có IT theo report: không tiếp tục ghi “chưa có test”. Chỉ xin coverage transfer/reserve/issue nontracked và test chuỗi hao hụt còn thiếu.
- Smoke29/10 là bản trước patch approve-version; xin transcript mới của binary cuối hoặc successor, không ghi đè evidence cũ.

## Cách dùng

Gửi từng file cùng message trong mạch tương ứng; bản mới đã tăngv2/v3, không ghi đè file28/10. Ngày30/10 là ngày dự kiến PO gửi; gửi ngày khác đổi ngày trước gửi, sửa sau gửi tăng version tiếp. Các task đã tự chứa không cần BE mở repo FE.
Mục lục này không nằm trong ZIP gửi BE. Tài liệu tạo local không cho phép tự gửi bên ngoài; phiên này không send hoặc gọi server.

## Cổng hoàn tất

BE gửi đủ11artifact, bổ sung assertions/test theo quyết định PO và smoke sau patch; ghi rõ pass/IT/HTTP/chưa chạy, không biến tổng suite xanh thành mọi case đều đạt. FE vẫn cần tích hợp và nghiệm thu thật với build/record/trace/permission/scope đúng.
Giới hạn nontracked COGS sauclose/no-routing close/future-WO actual vẫn nêu riêng, không tự được PO duyệt/mở rộng từ gói này. GĐ12 và live gates còn mở; không sửa FE trong phiên tài liệu.

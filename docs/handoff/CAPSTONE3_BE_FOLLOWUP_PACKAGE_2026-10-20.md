# Bộ tài liệu bổ sung BE sau phản hồi GĐ2–7

20/10/2026 · Session324 · **Mục lục nội bộ cho PO/FE, không dùng file này thay các task tự chứa gửi BE.**

Đã chuẩn bị **6 FE_REQUEST và3 FE_QUESTION**, mỗi đầu việc một file và một tin nhắn4 dòng. Không có FE_DEFECT vì chưa tái hiện runtime; BE đang tắt. Đây là bổ sung/đối soát trong mạch handoff cũ, không đánh dấu BE hoặc FE đã nghiệm thu và không phải duyệt code GĐ12.

Tải ZIP gồm9 tài liệu và9 tin nhắn (`CAPSTONE3_BE_FOLLOWUP_2026-10-20.zip`, không có trong repo). ZIP chỉ chứa các file gửi BE; mục lục nội bộ này không nằm trong ZIP.

| Thứ tự gửi đề xuất | Loại/ưu tiên | Chủ đề | Tài liệu gửi BE | Tin nhắn4 dòng |
|---|---|---|---|---|
| 1 | FE_QUESTION / P1 | Cung cấp đủ phản hồi nền GĐ6/GĐ7 và mốc contract | [Mở tài liệu](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_QUESTION_GD06_GD07_CONTRACT_2026-10-20.md) | [Mở tin nhắn](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_QUESTION_GD06_GD07_CONTRACT_MESSAGE_2026-10-20.md) |
| 2 | FE_REQUEST / P1 | Bổ sung sổ giá trị tồn kho và nguồn giá trị WIP | [Mở tài liệu](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_INVENTORY_VALUATION_2026-10-20.md) | [Mở tin nhắn](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_INVENTORY_VALUATION_MESSAGE_2026-10-20.md) |
| 3 | FE_REQUEST / P1 | Chặn hàng hết hạn xuyên MRP, giữ chỗ và xuất vật tư | [Mở tài liệu](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_EXPIRED_STOCK_GUARDS_2026-10-20.md) | [Mở tin nhắn](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_EXPIRED_STOCK_GUARDS_MESSAGE_2026-10-20.md) |
| 4 | FE_REQUEST / P1 | Bảo vệ nguồn hàng mua đang QC trước mutation kho chung | [Mở tài liệu](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-20.md) | [Mở tin nhắn](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_MESSAGE_2026-10-20.md) |
| 5 | FE_REQUEST / P1 | Đồng nhất công thức capacity giữa xem trước và phát hành WO | [Mở tài liệu](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_CAPACITY_RELEASE_ALIGNMENT_2026-10-20.md) | [Mở tin nhắn](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_CAPACITY_RELEASE_ALIGNMENT_MESSAGE_2026-10-20.md) |
| 6 | FE_REQUEST / P2 | Hiển thị nguồn mua đang chờ QC trong MRP | [Mở tài liệu](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_MRP_PENDING_QC_VISIBILITY_2026-10-20.md) | [Mở tin nhắn](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_MRP_PENDING_QC_VISIBILITY_MESSAGE_2026-10-20.md) |
| 7 | FE_REQUEST / P2 | Tách số đang chờ QC và bị loại cho hàng không theo lô | [Mở tài liệu](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_NONTRACKED_QC_BREAKDOWN_2026-10-20.md) | [Mở tin nhắn](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_REQUEST_NONTRACKED_QC_BREAKDOWN_MESSAGE_2026-10-20.md) |
| 8 | FE_QUESTION / P1 | Chốt cách giữ chính xác decimal trên wire và phạm vi số | [Mở tài liệu](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_QUESTION_DECIMAL_WIRE_PRECISION_2026-10-20.md) | [Mở tin nhắn](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_QUESTION_DECIMAL_WIRE_PRECISION_MESSAGE_2026-10-20.md) |
| 9 | FE_QUESTION / P2 | Chốt giới hạn tỉ lệ BOM theo lô và cách xử lý công thức 1 trên 3 | [Mở tài liệu](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_QUESTION_BOM_BATCH_RATIO_2026-10-20.md) | [Mở tin nhắn](CAPSTONE3_BE_FOLLOWUP_2026-10-20/FE_QUESTION_BOM_BATCH_RATIO_MESSAGE_2026-10-20.md) |

Các vấn đề B01–B09 trong review đã được bao phủ1–1: valuation, expiry, quarantine, capacity, pending QC visibility, nontracked QC breakdown, missing base contracts, decimal precision và BOM ratio. Các lỗi/khoảng cách adapter FE trong review là việc FE tự sửa sau duyệt, không giao BE làm thay.

Cách dùng: gửi từng file cần xử lý cùng tin nhắn tương ứng trong mạch đúng chủ đề; không cần gửi một file tổng hợp nhiều task. Trong file QUESTION, §3 là đầu ra tài liệu/quyết định, §5 chỉ cần trả lời; không mở code task từ đề xuất đang chờ chốt. Trong REQUEST, §3 là implementation/mapping, §5 là confirmation riêng.

Phần mới trong REQUEST là đề xuất, không khẳng định BE đã đồng ý. Pending QC visibility không tự đổi netting; number wire QUESTION ưu tiên FE parser lossless để giữ API; BOM QUESTION không tự giao viết lại MRP. Nếu BE đã xử lý ở giai đoạn sau thì cung cấp build/mapping/test thay làm lại.

Ngày20/10 là ngày dự kiến PO gửi; gửi ngày khác phải đổi tên/ngày trước gửi. Sau khi gửi, sửa nội dung dùng _v2/_v3, không ghi đè file cùng tên đã gửi. Toàn bộ đang lưu local, chưa gửi bên ngoài.

**Kiểm tra trước gửi:** đủ§0–§7, kết luận3 dòng, priority theo ảnh hưởng, outside scope,5 bằng chứng thiếu ghi rõ, field5cột và ví dụ số khi có xin số liệu; không source path/repo link/credential; current auth/scope/replay và unknown/legacy không bị bỏ qua. Khi BE bật, FE mới xác nhận bản triển khai và chạy acceptance. Không lấy test BE báo cáo hoặc fixture đề xuất làm FE live evidence.

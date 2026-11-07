# Bộ tài liệu BE hoàn thiện — chính sách đơn giản đã chốt

28/10/2026 · Session326 · **Mục lục nội bộ cho PO/FE, không thay các file task tự chứa gửi BE.**

## Quyết định PO trực tiếp ngày28/10

1. **Giá thành theo giá chuẩn vật tư chụp tại RELEASE.** Đổi giá sau đó không sửa baseline/WO lịch sử; tiếp tục hoãn sổ giá trị tồn/WIP, không gọi GĐ11 là moving-average actual-purchase costing.
2. **BOM per-unit giữ6 số lẻ.** Chưa hỗ trợ1/3; báo lỗi form/import, không tự round recipe hoặc mở model rational.
3. **Bù hao hụt = lượng cơ bản ×(1+rate).** BOM/MRP/WO phải cùng semantics; baseline kế thừa requirement, không nhân hao hụt hai lần. Chi tiết rounding cần BE xác nhận theo file task, không đổi historical snapshots.

Đây là xác nhận PO mới nhất, không còn chỉ là “BE báo PO chốt27/10”. Policy approval này **không phải** duyệt code FE hoặc nghiệm thu API.

## File gửi BE và tin nhắn đi kèm

Mỗi task một file theo FETaskDeliveryRules: **5 FE_REQUEST +3 FE_QUESTION**, mỗi file có§0–§7 và tin nhắn4 dòng.

[Tải ZIP gồm8 tài liệu và8 tin nhắn](CAPSTONE3_BE_COMPLETION_2026-10-28.zip). Mục lục nội bộ này không nằm trong ZIP; mỗi file BE vẫn tự chứa để gửi riêng theo task.

| Thứ tự | Loại / mức | Nội dung | Tài liệu | Tin nhắn |
|---|---|---|---|---|
| 1 | FE_QUESTION / P2 | Xác nhận áp dụng giá chuẩn và hoãn sổ giá trị tồn/WIP | [Mở](FE_QUESTION_STANDARD_COST_BASELINE_2026-10-28.md) | [Mở](FE_QUESTION_STANDARD_COST_BASELINE_2026-10-28_MESSAGE.md) |
| 2 | FE_QUESTION / P2 | Xác nhận giới hạn BOM theo lô trong phạm vi đơn giản | [Mở](FE_QUESTION_BOM_SCALE6_SCOPE_2026-10-28.md) | [Mở](FE_QUESTION_BOM_SCALE6_SCOPE_2026-10-28_MESSAGE.md) |
| 3 | FE_REQUEST / P1 | Đồng nhất bù hao hụt giữa BOM, MRP, WO và giá thành | [Mở](FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT_2026-10-28.md) | [Mở](FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT_2026-10-28_MESSAGE.md) |
| 4 | FE_QUESTION / P1 | Bổ sung contract còn thiếu sau phản hồi GĐ7–11 và CF | [Mở](FE_QUESTION_GD06_GD07_CONTRACT_2026-10-28_v2.md) | [Mở](FE_QUESTION_GD06_GD07_CONTRACT_2026-10-28_v2_MESSAGE.md) |
| 5 | FE_REQUEST / P1 | Hoàn thiện validation số trước khi DB làm tròn | [Mở](FE_REQUEST_NUMERIC_VALIDATION_COMPLETION_2026-10-28.md) | [Mở](FE_REQUEST_NUMERIC_VALIDATION_COMPLETION_2026-10-28_MESSAGE.md) |
| 6 | FE_REQUEST / P1 | Khép phần bảo vệ QC còn thiếu sau CF-1 | [Mở](FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-28_v2.md) | [Mở](FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-28_v2_MESSAGE.md) |
| 7 | FE_REQUEST / P1 | Bảo đảm mã lô không trùng khác hoa thường khi nhận đồng thời | [Mở](FE_REQUEST_LOT_IDENTITY_CONCURRENCY_2026-10-28.md) | [Mở](FE_REQUEST_LOT_IDENTITY_CONCURRENCY_2026-10-28_MESSAGE.md) |
| 8 | FE_REQUEST / P1 | Hoàn thiện kiểm thử và bằng chứng chốt giá thành | [Mở](FE_REQUEST_COST_CLOSE_VERIFICATION_2026-10-28.md) | [Mở](FE_REQUEST_COST_CLOSE_VERIFICATION_2026-10-28_MESSAGE.md) |

## Cách dùng và phần không mở rộng

Gửi riêng task đúng mạch cùng message. Hai task quarantine/contract làv2 tiếp nối chủ đề20/10, ngày phát hành mới28/10; không ghi đè bản cũ. Nếu gửi ngày khác cần đổi tên/ngày trước gửi; sửa sau gửi tăng version tiếp. Toàn bộ hiện lưu local, chưa gửi ra ngoài.

QUESTION chỉ cần BE trả contract/mapping, không tự mở code task từ đề xuất. REQUEST cho phép BE hoàn thiện phần chưa có và test; phần đã báo làm có thể đáp ứng bằng mapping/build/evidence, không làm lại. Không lập FE_DEFECT khi BE đang tắt và FE chưa có5 mảnh bằng chứng cùng run.

Task valuation ledger ngày20/10 chuyển sang **hoãn theo PO**, không đánh dấu pass; câu hỏi mở rộng BOM1/3 được chốt **chưa hỗ trợ**, không phải đã triển khai. Pending-QC/expiry/scheduler CF đã báo làm không bị giao lại; lấy đủ contract và nghiệm thu sau.
Các giới hạn MRP future-WO theo actual, trace/COGS non-tracked, no-routing close hoặc source-overlap ngoài3 quyết định này **chưa được PO tự động chấp nhận hoặc yêu cầu mở rộng**. BE phải tiếp tục nêu rõ trong contract/report; FE không giả completed cho chúng. Không tăng phạm vi ERP chỉ để khép tài liệu.

FE vẫn tự làm parser lossless, typed adapters, mandatory version bodies, label và permission UI sau phê duyệt code; các task này không nhờ BE làm FE. Khi BE bật cần build+schema+behavior proof rồi acceptance theo vai trò/2 Plant; GĐ12/live gates chưa hoàn tất.

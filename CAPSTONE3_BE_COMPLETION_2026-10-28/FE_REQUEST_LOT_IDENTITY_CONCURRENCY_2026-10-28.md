# Bảo đảm mã lô không trùng khác hoa thường khi nhận đồng thời

## §0 — Kết luận trong 3 dòng

- Vấn đề: GĐ7v2 báo hai PO khác nhau có thể cùng tạo lot code chỉ khác hoa thường vì khóa theo từng PO.
- Ảnh hưởng: Danh tính lô và trace mua/sản xuất; chưa có FE request live tái hiện trên bản mới.
- Mức ưu tiên: P1 — chặn phần tích hợp/nghiệm thu dữ liệu tương ứng; không phải P0 toàn hệ thống.

## §1 — Phạm vi và quyết định

**Phiên bản:** v1 · **Ngày dự kiến gửi:** 28/10/2026 · **Mốc FE:** Session326. Owner FE/BE do PO phân công, chưa có tên trong phiên.
Bản đầu của đầu việc này; các handoff gốc vẫn giữ lịch sử.
Nếu gửi ngày khác đổi ngày trước gửi; sửa nội dung sau gửi dùng phiên bản tiếp theo, giữ cùng mạch task.

**Trong phạm vi:** Unique lot identity không phân biệt hoa thường theo Company+Item hiện hành, gồm generic receipt/mua/sản xuất/QC child code.

**Ngoài phạm vi:** Không đổi cấu trúc lot tracking, không merge/xóa lot cũ, không đổi mã đã được chứng từ tham chiếu; không khóa toàn hệ thống chỉ để chống trùng.

[Đề xuất hoàn thiện integrity] Giữ quy tắc mã lô đã thống nhất; bảo đảm ở DB hoặc khóa canonical theo Item/namespace tương đương, không chỉ khóa từng PO.

**Căn cứ:** [BE báo cáo] GĐ7v2 §6 mục4 nêu casefold application check race. GĐ9 có case-insensitive validation nhưng chưa nêu fix global uniqueness; CF summary không đề cập. Có thể bản sau đã sửa, cần mapping/test trước code trùng.

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
| R01 | Hai PO cùng Item tạo LOT-A và lot-a đồng thời chỉ một record namespace hợp lệ được tạo. | POST /api/v1/goods-receipts/{id}/post và đường mua legacy | Có—lot identity |
| R02 | Mọi đường tạo lot dùng cùng namespace guard; QC child suffix không đua với lot code được tạo qua đường khác. | POST /api/v1/inventory/receive; production receipt approve; QC child creation | Có—aliases |
| R03 | Request thua nhận lỗi ổn định không500; cả phiếu/tồn/movement/lot/audit/replay-success của transaction thua không ghi một phần. | Post multi-line / approval | Có—atomicity |
| R04 | Nếu đã tồn tại collision legacy, rollout báo rõ danh sách/count nội bộ và chặn ghi mới không an toàn; không tự merge/xóa history. | Migration/validation plan | Có—safe rollout |

### Fixture đề xuất

CompanyC/ItemI có hai PO khác nhau, cùng lot code sau trim/casefold làLOT-X. Hai post đồng thời tạo "LOT-X" và "lot-x": một200/201 theo command contract và một409 LOT_CODE_ALREADY_EXISTS; chỉ một lot mới, chỉ receipt thắng tăng OnHand/PO received; receipt thua giữ trạng thái trước post và có thể sửa mã rồi thử lại bằng intent/key phù hợp.
Không dùng UUID fake làm record thật. Cần test riêng same Item khác Company/Item namespace theo rule đã có: khác Item được dùng lại code nếu contract hiện hành cho phép; không tự đặt unique toàn cầu.
Test đủ legacy receive/new GR/production approve/child code path, multi-line dòng sau trùng rollback và2 Plant scope. Hệ thống trùng cùng code đúng chữ đã có không có nghĩa casefold đã an toàn.

### Implementation không bắt buộc một thuật toán mới

BE chọn unique casefold index hoặc transaction lock theo namespace đủ mạnh. Nếu dữ liệu cũ không cho migration sạch phải nêu blocker/mapping và cách sửa được PO duyệt; không cần schema redesign để gửi một báo cáo. Command giữ quyền hiện hành của kho/receipt/QC, current auth/replay, date/quantity rules; không thêm endpoint hay field mới.

## §4 — Hợp đồng lỗi

Tái dùng mã BE hiện có khi đúng nghĩa. Mã/mapping mới là đề xuất và cần BE trả bảng tương đương nếu khác; không bắt FE parse message.

| Tình huống | HTTP | code / field | FE hiển thị/xử lý |
|---|---|---|---|
| Code trùng sau trim/casefold | 409 | LOT_CODE_ALREADY_EXISTS theo contract receipt; mapping stable cho alias cần BE nêu | Lỗi tại lotCode/lotNumber tương ứng, giữ input |
| Legacy collision làm rollout không an toàn | Không dựng lỗi HTTP giả | Báo migration blocker, không tự merge | PO xử lý dữ liệu có audit |
| Không quyền | 403 | PERMISSION_DENIED | Không lộ record ngoài scope |

Giữ envelope code/message và result khi có dữ liệu, errors[] khi có field error. Không ép result:null cho mọi lỗi; import409 IMPORT_REVALIDATION_FAILED có result=batch INVALID theo contract batch. Missing object do redaction khác field unknown/null; không suy số0.
Giữ X-Trace-Id khi có, che Authorization/mật khẩu/token. Validation/business/stale không auto retry như network; giữ input/source context, version server là authoritative. Retry cùng intent dùng key cũ khi endpoint hỗ trợ; intent thay đổi dùng key theo fingerprint policy, không giả master endpoints có replay.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi lại PO có chốt ba chính sách hay chưa — PO đã xác nhận ngày28/10. BE xác nhận mapping/triển khai/bằng chứng; quyết định mới ngoài ba chính sách vẫn phải nêu riêng. Tất cả Q dưới đây chưa có phản hồi cho bản này.

1. **Q1:** Bản sau GĐ7v2 đã có guard namespace dùng chung đủ chặn race này chưa? — FE nghiêng về: Có nếu có implementation/test, không code trùng — **CHẶN lot concurrency acceptance**.
2. **Q2:** Request thua ở tất cả đường tạo có thể map LOT_CODE_ALREADY_EXISTS thay500 không? — FE nghiêng về: Có để FE sửa đúng field — **CHẶN error mapping**.
3. **Q3:** Namespace Company+Item không phân biệt hoa thường có giữ nguyên như contract receipt hiện có không? — FE nghiêng về: Có, không tự mở unique toàn hệ thống — **CHẶN identity migration**.

## §6 — FE đã xử lý và phần FE tự đảm nhiệm

FE kiểm tra sớm chỉ giúp UX; không dùng search list rồi create để giả uniqueness concurrency. Chưa thêm API/UI hoặc thay lot cũ.

Phiên326 chỉ tạo tài liệu và ghi quyết định; chưa sửa ứng dụng, parser, dependency, API flag, dữ liệu server hoặc chạy test/build/browser. FE sẽ tự mapping DTO, label, quyền UI, error/field, input preservation và test sau khi được duyệt code; không giao BE sửa FE. Nhận báo cáo BE không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- BE_RESPONSE_GD_07_2026-10-18_v2.md — §6 race hai PO.
- BE_RESPONSE_GD_09_2026-10-21.md — lot identity production receipt.
- BE_RESPONSE_GD_09_2026-10-23_v3.md — QC child code namespace.

Chỉ ghi tên file, không phụ thuộc repo/đường dẫn máy FE. Nội dung task đã tự chứa quyết định, phạm vi và tiêu chí; nếu reference đang thiếu thì BE cung cấp theo contract task, không coi file đó đã được FE đọc.

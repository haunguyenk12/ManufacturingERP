# Khép phần bảo vệ QC còn thiếu sau CF-1

## §0 — Kết luận trong 3 dòng

- Vấn đề: CF-1 báo đã chặn adjust tracked lot; cần đối chiếu count và non-tracked trước đóng task owner invariant.
- Ảnh hưởng: Count/adjust và QC mua/sản xuất; không báo lại tracked adjust như phần BE chưa làm.
- Mức ưu tiên: P1 — chặn phần tích hợp/nghiệm thu dữ liệu tương ứng; không phải P0 toàn hệ thống.

## §1 — Phạm vi và quyết định

**Phiên bản:** v2 · **Ngày dự kiến gửi:** 28/10/2026 · **Mốc FE:** Session326. Owner FE/BE do PO phân công, chưa có tên trong phiên.
Bản v2 tiếp nối cùng chủ đề của file ngày20/10 được liệt kê ở §7; cập nhật theo phản hồi mới, không ghi đè bản cũ. Ngày phát hành mới là28/10; không khẳng định thời điểm thực gửi bản trước.
Nếu gửi ngày khác đổi ngày trước gửi; sửa nội dung sau gửi dùng phiên bản tiếp theo, giữ cùng mạch task.

**Trong phạm vi:** Phần còn lại của FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-20.md: generic count và non-tracked conservation, test đồng thời.

**Ngoài phạm vi:** Không sửa/đảo quyết định QC, không thêm correction workflow phức tạp, không cấm điều chỉnh phần free hợp lệ; không triển khai lại tracked guard nếu đã có.

[Phạm vi tối giản đề xuất] Dùng guard canonical hiện có mở rộng phần thiếu: không cho generic mutation làm mất lượng protected hoặc biến thành AVAILABLE; xử lý ngoại lệ nguồn bằng workflow riêng sau, không thêm bypass theo tên role.

**Căn cứ:** [BE báo cáo] CF-1 đã409 STATE_CONFLICT trên adjust tracked lot pending mua/sản xuất hoặc rejected child; non-tracked không bị chặn trong phạm vi đã làm. Chưa có fileCF-1 chi tiết/count evidence; không khẳng định non-tracked đã bị bypass.

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
| R01 | BE cung cấp coverage matrix adjust/count/transfer/reserve/issue/status cho tracked và non-tracked; ghi rõ đã có/thiếu/test để không sửa trùng. | POST /api/v1/inventory/adjust; POST /api/v1/inventory/counts; các mutation kho hiện hành | Có—coverage |
| R02 | Absolute count/adjust không làm mất protected pending/rejected source, không chuyển phần đó thành free AVAILABLE. | Canonical stock mutations | Có—owner invariant |
| R03 | Non-tracked tổng lượng không thấp hơn phần protected chưa xử lý và reservation hợp lệ; không clamp bucket âm để cho command qua. | adjust/count non-tracked | Có—conservation |
| R04 | Đồng thời QC+count/adjust: serialize/recheck invariant dưới lock, toàn transaction rollback nếu lỗi; current auth trước replay. | Mutation sources chung | Có—race |
| R05 | Guard tracked CF-1 đang có và source status/cancel guards không hồi quy; non-owner HOLD giữ workflow hợp lệ. | Inventory/QC sources | Có—regression |

### Fixture đề xuất — chưa gọi BE

Tracked receipt4: accept2.5/reject0.5 → parent HOLD1, accepted child2.5, rejected child0.5. Generic count parent1→0 hoặc adjust parent+1 bị chặn nếu còn owner/protected; quantity/receipt/QC/movement/replay-success không đổi khi chặn.
Non-tracked nhận25, accept20/reject5, no reservation: protected5, free20. Count tổng4 không hợp lệ vì thấp hơn protected5; không được đổi rejected5 thành4 hoặc bỏ bucket để lưu được. Count tổng24 có thể hợp lệ **nếu** chỉ thay phần free20→19 theo generic audited workflow và giữ nguyên rejected5. Task này không yêu cầu khóa mọi thay đổi non-tracked; source-owner không được bị xóa/biến usable.
Nếu chưa xác định protected do legacy thiếu nguồn, không tự gán0; BE nêu blocker/đường xử lý rõ, không backfill source giả.

### Không thêm endpoint/phân hệ

Tái sử dụng inventory/QC handlers. PERM_INVENTORY_MOVE và các quyền QC hiện hành theo Company/Plant/kho; kiểm current auth/scope trước ghi/replay, không cấp bypass ADMIN. Field input/body/version giữ endpoint hiện hành. Nếu mapping đã có đủ R02/R03 chỉ cần test/evidence đúng bản; không bắt BE sửa cho có thay đổi.
Đề nghị test IT có đồng bộ hai transaction tạo race, rồi smoke tracked/nontracked/count khi môi trường sẵn sàng. Test giả lập lỗi không đưa vào API production. Không có số liệu actual của FE trong task này.

## §4 — Hợp đồng lỗi

Tái dùng mã BE hiện có khi đúng nghĩa. Mã/mapping mới là đề xuất và cần BE trả bảng tương đương nếu khác; không bắt FE parse message.

| Tình huống | HTTP | code / field | FE hiển thị/xử lý |
|---|---|---|---|
| Vi phạm owner/protected invariant | 409 | STATE_CONFLICT (đề xuất mapping nếu chưa có mã ổn định) | Hướng xử lý qua nguồn QC, giữ input |
| Issue/reserve lot không eligible | 409 | LOT_NOT_ELIGIBLE | Chọn nguồn hợp lệ |
| Version cũ | 409 | CONCURRENT_MODIFICATION | Refetch giữ input, không auto retry intent cũ |
| Thiếu quyền | 403 | PERMISSION_DENIED | Không lộ source ngoài phạm vi |

Giữ envelope code/message và result khi có dữ liệu, errors[] khi có field error. Không ép result:null cho mọi lỗi; import409 IMPORT_REVALIDATION_FAILED có result=batch INVALID theo contract batch. Missing object do redaction khác field unknown/null; không suy số0.
Giữ X-Trace-Id khi có, che Authorization/mật khẩu/token. Validation/business/stale không auto retry như network; giữ input/source context, version server là authoritative. Retry cùng intent dùng key cũ khi endpoint hỗ trợ; intent thay đổi dùng key theo fingerprint policy, không giả master endpoints có replay.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi lại PO có chốt ba chính sách hay chưa — PO đã xác nhận ngày28/10. BE xác nhận mapping/triển khai/bằng chứng; quyết định mới ngoài ba chính sách vẫn phải nêu riêng. Tất cả Q dưới đây chưa có phản hồi cho bản này.

1. **Q1:** R02/R03 đã được canonical count/adjust bảo vệ trong bản hiện tại chưa? — FE nghiêng về: Có nếu có test/mapping, chỉ sửa phần còn thiếu — **CHẶN owner gate**.
2. **Q2:** Non-tracked có thể chỉ khóa phần protected và vẫn cho audit adjustment phần free hợp lệ không? — FE nghiêng về: Có, tránh khóa cả kho không cần thiết — **CHẶN boundary semantics**.
3. **Q3:** CF-1 có test race QC+count/adjust trên cùng nguồn không? — FE nghiêng về: Có bằng test DB concurrency; smoke ghi riêng nếu chưa đo — **CHẶN concurrency acceptance**.

## §6 — FE đã xử lý và phần FE tự đảm nhiệm

FE chưa sửa guard/API UI trong phiên này; tiếp tục khóa lựa chọn không hợp lệ khi tích hợp nhưng server chịu trách nhiệm invariant. Không lặp lại cáo buộc CF-1 chưa chặn tracked adjust.

Phiên326 chỉ tạo tài liệu và ghi quyết định; chưa sửa ứng dụng, parser, dependency, API flag, dữ liệu server hoặc chạy test/build/browser. FE sẽ tự mapping DTO, label, quyền UI, error/field, input preservation và test sau khi được duyệt code; không giao BE sửa FE. Nhận báo cáo BE không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-20.md — task gốc, bản này tiếp nối v2 ngày28/10.
- BE_REPORT_CF_COMPLETED_2026-10-28.md — phạm vi CF-1 đã báo làm.
- BE_RESPONSE_GD_07_2026-10-18_v3.md — quality buckets mua.
- BE_RESPONSE_GD_09_2026-10-23_v3.md — source QC sản xuất.

Chỉ ghi tên file, không phụ thuộc repo/đường dẫn máy FE. Nội dung task đã tự chứa quyết định, phạm vi và tiêu chí; nếu reference đang thiếu thì BE cung cấp theo contract task, không coi file đó đã được FE đọc.

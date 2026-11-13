# Hoàn thiện kiểm thử và bằng chứng chốt giá thành

## §0 — Kết luận trong 3 dòng

- Vấn đề: GĐ11 báo atomic/current auth nhưng A39/A40 chưa có test riêng và chưa có live smoke bản chạy thật.
- Ảnh hưởng: Chốt giá thành/CLOSED và bút toán value-only; chưa đủ bằng chứng để đóng gate dữ liệu/quyền.
- Mức ưu tiên: P1 — chặn phần tích hợp/nghiệm thu dữ liệu tương ứng; không phải P0 toàn hệ thống.

## §1 — Phạm vi và quyết định

**Phiên bản:** v1 · **Ngày dự kiến gửi:** 28/10/2026 · **Mốc FE:** Session326. Owner FE/BE do PO phân công, chưa có tên trong phiên.
Bản đầu của đầu việc này; các handoff gốc vẫn giữ lịch sử.
Nếu gửi ngày khác đổi ngày trước gửi; sửa nội dung sau gửi dùng phiên bản tiếp theo, giữ cùng mạch task.

**Trong phạm vi:** BE bổ sung test nhắm A39/A40 và smoke chuẩn theo giá chuẩn đã chốt; sửa code chỉ nếu test phát hiện lỗi.

**Ngoài phạm vi:** Không thêm valuation ledger, không đổi C1–C13, không tạo test-failure endpoint production, không hồi tố WO cũ hoặc bypass checklist.

[PO đã chốt] Dùng nguồn giá chuẩn tại RELEASE, không chờ full inventory valuation để kiểm GĐ11. Các guard unknown/isolated pool/version/current auth vẫn phải đạt, không bị lược bỏ bởi hướng đơn giản.

**Căn cứ:** [BE báo cáo] GĐ11 §7 chưa smoke app đang chạy; A39 thu hồi quyền lúc chờ lock và A40 lỗi ghi mới được bảo đảm thiết kế. CF20smoke không thay toàn GĐ11A01–A48.

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
| R01 | Test giữ transaction chờ WO lock, thu hồi assignment trước khi lock được cấp, xác nhận C10 không commit/không trả financial replay trái quyền. | POST /api/v1/work-orders/{id}/finalize-cost-and-close | Có—A39 |
| R02 | Test inject lỗi sau một phần write thuộc C10, xác nhận run final/adjustments/WO/audit/replay-success rollback toàn bộ. | C10 transaction, test-only injection | Có—A40 |
| R03 | Retry sau lỗi rollback với cùng intent hợp lệ chỉ có một finalization; hai close cùng source/version chỉ một thắng. | C10 idempotency/concurrency | Có—consistency |
| R04 | Khi môi trường có sẵn, BE gửi smoke transcript đặt standard/rates→release→consume→receipt/QC→preview→close/readback, có guards/stale/permission. | C1–C13 và workflow nguồn hiện hành | Có—runtime readiness |
| R05 | Evidence phân loại test IT/HTTP, cùng record/time/build/trace; không gọi IT là FE live acceptance. | Report test/smoke | Có—traceability |

### Tiêu chí và fixture tối giản đề xuất (chưa chạy BE)

Một WO10 PCS với required RM20 KG, giá chuẩn1.000 VND/KG chụp RELEASE; một operation60 phút, labor6.000/OH3.000 VND mỗi giờ → planned29.000. Consume20, Good10, không scrap/rework, rate/time đúng snapshot → actual29.000, unit2.900. Receipt/QC10 trên pool đầu ra physical0 trước release, không mixed/transfer/downstream → preview READY; C10 đóng CLOSED, value-only adjustments quantity0, physical quantity vẫn10. Không cần invoice/payment để close.
Case source/version sai trả stale, không có write; stock-only không thấy money; known0 khác missing. Dùng UUID mới từ server thật trong report khi chạy, không dùng chữ WO1 như record BE.

### Điểm kiểm A39/A40

Dùng latch/test harness ở môi trường test để bảo đảm timing, không sleep may rủi; current authorization được đọc sau lock. Kiểm DB trước/sau gồm status/version WO, state/version run, adjustment rows, stock quantities, audit và idempotency success record. Sau rollback có thể lưu error log nhưng không fake success audit/receipt.
C10 body giữ {expectedVersion,expectedSourceVersion,runId,expectedRunVersion,reason}; header Idempotency-Key, principal server, quyền PERM_WORK_ORDER_CLOSE và cost view (PERM_WORK_ORDER_VARIANCE_READ hoặc PERM_PRODUCTION_COST_CALCULATE), Company/Plant scope. Không tin tổng cost client hoặc run bị tamper. Đọc lại GET /api/v1/work-orders/{id}/cost-runs/{runId}, GET /api/v1/work-orders/{id}/cost-runs/{runId}/adjustments và GET /api/v1/work-orders/{id} để chứng minh, không chỉ HTTP200.
Smoke khi BE sẵn sàng phải ghi request/response thật, X-Trace-Id, ngày giờ/môi trường/build và record IDs cùng lượt; chưa chạy thì ghi chưa chạy. Rollback injection không bắt buộc làm qua HTTP trên server dùng dữ liệu thật; IT với DB thật có thể bảo đảm case an toàn, không phát minh live evidence.

## §4 — Hợp đồng lỗi

Tái dùng mã BE hiện có khi đúng nghĩa. Mã/mapping mới là đề xuất và cần BE trả bảng tương đương nếu khác; không bắt FE parse message.

| Tình huống | HTTP | code / field | FE hiển thị/xử lý |
|---|---|---|---|
| Quyền bị thu hồi trước commit | 403 | PERMISSION_DENIED | Không commit, không lộ tiền |
| Version hoặc run version cũ | 409 | CONCURRENT_MODIFICATION | Refetch giữ context |
| Nguồn đổi | 409 | COST_SOURCE_STALE | Tính lại |
| Checklist chưa đủ / dữ liệu giá thiếu | 409 | CLOSE_BLOCKED / COST_DATA_INCOMPLETE | Hiện blocker |
| Lỗi inject ghi trong test | 5xx theo mapping hệ thống | Không ép thành success hoặc conflict giả | Rollback và retry có kiểm soát |

Giữ envelope code/message và result khi có dữ liệu, errors[] khi có field error. Không ép result:null cho mọi lỗi; import409 IMPORT_REVALIDATION_FAILED có result=batch INVALID theo contract batch. Missing object do redaction khác field unknown/null; không suy số0.
Giữ X-Trace-Id khi có, che Authorization/mật khẩu/token. Validation/business/stale không auto retry như network; giữ input/source context, version server là authoritative. Retry cùng intent dùng key cũ khi endpoint hỗ trợ; intent thay đổi dùng key theo fingerprint policy, không giả master endpoints có replay.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi lại PO có chốt ba chính sách hay chưa — PO đã xác nhận ngày28/10. BE xác nhận mapping/triển khai/bằng chứng; quyết định mới ngoài ba chính sách vẫn phải nêu riêng. Tất cả Q dưới đây chưa có phản hồi cho bản này.

1. **Q1:** BE có thể bổ sung test riêng A39/A40 thay vì chỉ trỏ transaction/guard code không? — FE nghiêng về: Có, test DB có timing/fault injection xác định — **CHẶN integrity acceptance**.
2. **Q2:** Smoke dùng giá chuẩn và pool cô lập như fixture có phù hợp phạm vi GĐ11 hiện hành không? — FE nghiêng về: Có, không cần mở CF-6ledger — **CHẶN smoke scope**.
3. **Q3:** BE có thể ghi rõ các trường hợp nontracked sauclose/no-routing còn không hỗ trợ trong report không? — FE nghiêng về: Có để không giả toàn flow đều đạt — **CHẶN kết luận nghiệm thu tương ứng**.

## §6 — FE đã xử lý và phần FE tự đảm nhiệm

FE chưa chạy BE; không khẳng định từng xảy ra privilege bypass/partial close. Sau khi BE có contract/bằng chứng, FE vẫn phải chạy acceptance của mình; nhận report không tự đóng task.

Phiên326 chỉ tạo tài liệu và ghi quyết định; chưa sửa ứng dụng, parser, dependency, API flag, dữ liệu server hoặc chạy test/build/browser. FE sẽ tự mapping DTO, label, quyền UI, error/field, input preservation và test sau khi được duyệt code; không giao BE sửa FE. Nhận báo cáo BE không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- BE_RESPONSE_GD_11_2026-10-27.md — §6/§7 thiết kếvàtestcònthiếu.
- FE_HANDOFF_GD_11_2026-10-15.md — A39/A40 và11checklist.
- BE_REPORT_CF_COMPLETED_2026-10-28.md — CFsmokekhôngthayfullGĐ11.

Chỉ ghi tên file, không phụ thuộc repo/đường dẫn máy FE. Nội dung task đã tự chứa quyết định, phạm vi và tiêu chí; nếu reference đang thiếu thì BE cung cấp theo contract task, không coi file đó đã được FE đọc.

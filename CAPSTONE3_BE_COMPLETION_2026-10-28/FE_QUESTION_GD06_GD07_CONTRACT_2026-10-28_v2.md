# Bổ sung contract còn thiếu sau phản hồi GĐ7–11 và CF

## §0 — Kết luận trong 3 dòng

- Vấn đề: Đã nhận đủ GĐ7; còn thiếu11 file contract/OpenAPI được tham chiếu để FE map đúng bản BE.
- Ảnh hưởng: Typed adapter, numeric matrix và nghiệm thu đúng build; không kết luận thiếu endpoint chỉ vì thiếu file.
- Mức ưu tiên: P1 — chặn phần tích hợp/nghiệm thu dữ liệu tương ứng; không phải P0 toàn hệ thống.

## §1 — Phạm vi và quyết định

**Phiên bản:** v2 · **Ngày dự kiến gửi:** 28/10/2026 · **Mốc FE:** Session326. Owner FE/BE do PO phân công, chưa có tên trong phiên.
Bản v2 tiếp nối cùng chủ đề của file ngày20/10 được liệt kê ở §7; cập nhật theo phản hồi mới, không ghi đè bản cũ. Ngày phát hành mới là28/10; không khẳng định thời điểm thực gửi bản trước.
Nếu gửi ngày khác đổi ngày trước gửi; sửa nội dung sau gửi dùng phiên bản tiếp theo, giữ cùng mạch task.

**Trong phạm vi:** Tiếp nối task contract20/10, cập nhật chỉ xin các file chưa nhận, mốc build và version chain.

**Ngoài phạm vi:** Không xin BE viết lại CF đã làm, không bật server/SQL DB chỉ để trả lời tài liệu; không đòi password/token.

[Đã chốt phạm vi] GĐ7v1/v2 đã được bổ sung nên không xin gửi lại. Ba chính sách PO28/10 trong cùng bộ tài liệu là quyết định chính thức mới; không cần chờ BE suy từ các quyết định được báo lại ngày27/10.

**Căn cứ:** [Đã kiểm tài liệu] Folder GĐ7–11 có14 phản hồi và CF report; CF report nhắc8 file chi tiết và OpenAPI chưa có, GĐ6v1/v2 vẫn thiếu. Không có runtime/OpenAPI live được FE kiểm.

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
| D01 | Cung cấp11 file dưới đây hoặc bản hợp nhất đủ nội dung với mapping version thay thế rõ. | Tài liệu/contract artifact; không endpoint mới | Có—contract |
| D02 | Nêu current build/commit hoặc checksum artifact và migration tương ứng; không dùng migration94 một mình làm bằng chứng binary đúng. | Build metadata/report | Có—live identification |
| D03 | Nêu phép smoke đúng bản và fixture mới được phép dùng khi BE sẵn sàng; không tái dùng record đã dọn. | Evidence/readiness plan | Có—acceptance |

### Những file chưa nhận (không phải xin11 task code)

1. BE_RESPONSE_GD_06_2026-10-17.md
2. BE_RESPONSE_GD_06_2026-10-17_v2.md
3. BE_RESPONSE_VERSION_MAP_2026-10-28.md
4. BE_RESPONSE_NUMERIC_CONTRACT_2026-10-28.md
5. BE_RESPONSE_BOM_RATIO_2026-10-28.md
6. BE_RESPONSE_CF_1_2026-10-28.md
7. BE_RESPONSE_CF_2_2026-10-28.md
8. BE_RESPONSE_CF_3_2026-10-28.md
9. BE_RESPONSE_CF_4_2026-10-28.md
10. BE_RESPONSE_CF_5_2026-10-28.md
11. BE_OPENAPI_2026-10-28.json

Có thể gửi offline; nếu tên thực khác, cho mapping. OpenAPI phải đúng artifact/bản BE dự kiến tích hợp và có hash xuất file nếu có; số256 path không thay xác nhận behavior.
Migration92/93/94 là schema mốc, code guard CF-1/2/3 có thể không cần migration riêng. Cung cấp fingerprint hành vi khi chạy: QC tracked adjust409, expired reserve409, pending-QC WARNING, capacity2 preview=release. Không yêu cầu FE quyền DB hoặc tự chạy SQL trong tài liệu.
Bản hợp nhất cần full method/path/query/body/result/nullability/numeric type/permission-scope/versions/errors/idempotency/legacy/deferred và acceptance chưa chạy. Ghi rõ endpoint body bắt buộc version; “release không đổi” ở GĐ11 hiểu so với contract GĐ8 mới, không phải quay lại bodyless legacy.
Không có field/endpoint mới nên bảng5cột không áp dụng. Tài liệu phải tách implemented/tested/known limitation; không xin credential. Khi BE chưa bật, chỉ cần gửi artifact và kế hoạch, chưa cần tạo live trace giả.

## §4 — Hợp đồng lỗi

Không yêu cầu đổi mã lỗi trong task QUESTION. Bảng là mapping hiện hành cần xác nhận, không là lỗi FE đã quan sát.

| Tình huống | HTTP | code / field | FE hiển thị/xử lý |
|---|---|---|---|
| Xin contract | — | Không có đổi error code BE trong task QUESTION | Chỉ cung cấp bảng hiện hành |
| Live fingerprint chưa chạy | — | Không phát minh SUCCESS/trace | Ghi rõ chờ môi trường |

Giữ envelope code/message và result khi có dữ liệu, errors[] khi có field error. Không ép result:null cho mọi lỗi; import409 IMPORT_REVALIDATION_FAILED có result=batch INVALID theo contract batch. Missing object do redaction khác field unknown/null; không suy số0.
Giữ X-Trace-Id khi có, che Authorization/mật khẩu/token. Validation/business/stale không auto retry như network; giữ input/source context, version server là authoritative. Retry cùng intent dùng key cũ khi endpoint hỗ trợ; intent thay đổi dùng key theo fingerprint policy, không giả master endpoints có replay.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi lại PO có chốt ba chính sách hay chưa — PO đã xác nhận ngày28/10. BE xác nhận mapping/triển khai/bằng chứng; quyết định mới ngoài ba chính sách vẫn phải nêu riêng. Tất cả Q dưới đây chưa có phản hồi cho bản này.

1. **Q1:** BE có thể cung cấp đủ11 file hoặc bản hợp nhất tương đương không? — FE nghiêng về: Có, không xin lại GĐ7 đã nhận — **CHẶN contract matrix**.
2. **Q2:** BE có thể cung cấp mốc binary ngoài migration DB không? — FE nghiêng về: Có bằng commit/artifact checksum hoặc cơ chế tương đương — **CHẶN xác minh đúng bản**.
3. **Q3:** BE có thể nêu riêng case nào mới là design/IT chứ chưa HTTP smoke không? — FE nghiêng về: Có để FE không nhầm loại bằng chứng — **CHẶN live gate, không chặn đọc tài liệu**.

## §6 — FE đã xử lý và phần FE tự đảm nhiệm

FE đã archive nguyên15file nhận28/10, không sửa bản gốc và không gọi endpoint/SQL. Adapter/version/parser FE sẽ tự triển khai sau duyệt, không giao BE làm FE.

Phiên326 chỉ tạo tài liệu và ghi quyết định; chưa sửa ứng dụng, parser, dependency, API flag, dữ liệu server hoặc chạy test/build/browser. FE sẽ tự mapping DTO, label, quyền UI, error/field, input preservation và test sau khi được duyệt code; không giao BE sửa FE. Nhận báo cáo BE không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- FE_QUESTION_GD06_GD07_CONTRACT_2026-10-20.md — task cũ, tiếp nối v2 chỉ hỏi phần còn thiếu.
- BE_REPORT_CF_COMPLETED_2026-10-28.md — danh sách file kèm/mốc schema.
- BE_RESPONSE_GD_07_2026-10-18.md — đã nhận, không yêu cầu gửi lại.
- BE_RESPONSE_GD_11_2026-10-27.md — body/versions GĐ11 dựa trên GĐ8.

Chỉ ghi tên file, không phụ thuộc repo/đường dẫn máy FE. Nội dung task đã tự chứa quyết định, phạm vi và tiêu chí; nếu reference đang thiếu thì BE cung cấp theo contract task, không coi file đó đã được FE đọc.

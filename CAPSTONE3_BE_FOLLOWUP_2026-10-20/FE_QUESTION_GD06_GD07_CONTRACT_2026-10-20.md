# Cung cấp đủ phản hồi nền GĐ6/GĐ7 và mốc contract

## §0 — Kết luận trong 3 dòng

- Vấn đề: Hai bản v3 chỉ bổ sung import SO/QC mua, còn thiếu v1/v2 nên FE chưa có contract toàn bộ SO/MRP/PO/GR.
- Ảnh hưởng: Tích hợp API GĐ6/GĐ7 trên FE/dev-demo; không chặn việc xem/chuẩn bị những phần contract đã rõ.
- Mức ưu tiên: P1 — chặn một phần tích hợp/nghiệm thu tương ứng, không phải toàn hệ thống ngừng chạy.

## §1 — Phạm vi

**Bản:** v1 · **Ngày dự kiến gửi:** 20/10/2026 · **FE:** mốc Session324, nối FE/mock GĐ1–11 và review phản hồi Session323. Đã lưu cục bộ; chưa tự gửi. Nếu gửi ngày khác đổi ngày tên/file; sửa sau gửi dùng _v2 và giữ cùng mạch task. Owner FE/BE do PO phân công, chưa có tên trong phiên này.

**Trong phạm vi:** Cung cấp bản contract đã có, quyết định version chain và mapping cho FE, không giao code nghiệp vụ mới.

**Ngoài phạm vi:** Không xin sửa SO/MRP/PO/GR; không coi missing file là missing implementation; không khởi động BE hoặc gửi credential để trả lời.

**Căn cứ và trạng thái:** [Đã kiểm chứng tài liệu] GĐ6v3 mở đầu ghi không thay v1 SO/v2 hoạch định; GĐ7v3 ghi v1 PO/v2 GR vẫn đúng và v3 chỉ thêm QC. FE chỉ nhận hai v3 trong bộ20/10; chưa có bốn bản trước để đối chiếu.

Nhãn trong file: **[BE báo cáo]** là nội dung phản hồi đã nhận, không là FE nghiệm thu; **[Đề xuất]** là tiêu chí/field/error mới cần BE mapping hoặc chốt; **[FE kiểm offline]** chỉ áp dụng phép tính/đọc tài liệu đã thực hiện. Bảng §3 và ví dụ là đề xuất, không là bằng chứng runtime. Nếu đã có implementation tương đương, BE trả mapping/build/test của nó, không làm trùng.

## §2 — Bằng chứng tái hiện

**Không có lượt gọi BE trong phiên này.** PO cho biết BE đang tắt; FE không khởi động server, không kiểm được deployment mới nhất và không dùng file phản hồi để giả làm request FE đã chạy.

| Mảnh bằng chứng live của FE | Hiện có |
|---|---|
| Request thật method/path/header/body | Không có |
| Response thật HTTP/code/nguyên văn | Không có |
| X-Trace-Id response của cùng lượt | Không có, không tự tạo |
| Môi trường + giờ + bản BE được kiểm | Chưa kiểm; chỉ có ngày làm tài liệu20/10/2026, không có giờ/live build |
| ID record BE của cùng lượt | Không có; fixture bên dưới là đề xuất, không dùng UUID cũ đã ngừng |

```text
Mong đợi: BE trả lời theo §3/§5 và cung cấp mapping/bằng chứng đúng phiên bản khi sẵn sàng.
Thực tế: Đã đối chiếu tài liệu BE; FE chưa gọi API hoặc nghiệm thu live, không gửi file này như FE_DEFECT.
```

## §3 — Đầu ra mong đợi — Chỉ cần cung cấp/xác nhận, chưa giao BE code

**Không có yêu cầu sửa code BE trong file QUESTION này.** D01… là đầu ra tài liệu/quyết định; mọi phương án thay mô hình cần task triển khai được chốt sau.

| # | Đầu ra đúng/sai kiểm được | Endpoint/điểm xử lý | Chặn FE? |
|---|---|---|---|
| D01 | Gửi lại đủ bốn phản hồi v1/v2 được nêu dưới đây, hoặc một bản hợp nhất có phạm vi tương đương và chỉ rõ thay thế. | Không có endpoint mới | Có—contract đầy đủ |
| D02 | Bản bàn giao chỉ rõ OpenAPI/build/migration tương ứng và liệt kê path/DTO/permission/error đã thay giữa các bản. | Tài liệu/contract artifact; không cần server đang bật | Có—adapter |
| D03 | Nêu endpoint/smoke nhận diện bản mới và môi trường dự kiến khi bật; không dùng các port lịch sử như cấu hình mặc định. | Tài liệu trả lời | Có—live readiness |

### Bốn tài liệu cần bổ sung

1. BE_RESPONSE_GD_06_2026-10-17.md — v1 SO create/edit/confirm/cancel/Customer/metrics.
2. BE_RESPONSE_GD_06_2026-10-17_v2.md — MRP/netting/sources/conversion MAKE/BUY/stale/quota.
3. BE_RESPONSE_GD_07_2026-10-18.md — v1 PO lifecycle/price/source lock/supply.
4. BE_RESPONSE_GD_07_2026-10-18_v2.md — GR draft/post/cancel/quarantine/candidates/permission.

Nếu tên thực khác, gửi bản đúng kèm mapping tên thay vì đổi nội dung v3 đã gửi. GĐ6v3 tên ngày17/10 nhưng nội dung ngày gửi18/10; GĐ7v3 tên18/10 nhưng nội dung19/10. Chỉ hỏi mốc version, không báo lỗi runtime.

### Nội dung FE cần trong bản hợp nhất nếu không gửi lại bản cũ

Full method/path, query/body, required/nullable, UUID/decimal, page/filter/sort, version/source token, error codes/HTTP, permission+scope/current auth before replay, idempotency, effects/rollback, legacy migration, phần deferred và acceptance chưa chạy. Tách rõ BE đã triển khai với quyết định PO còn chờ. Không có field/endpoint mới xin thêm trong task này, nên bảng5cột không áp dụng.
Không cần gửi mật khẩu/token. Fixture cũ đã ngừng hoạt động không được coi là record sẵn sàng để FE dùng lại. Có thể gửi OpenAPI file offline khi BE đang tắt; không bắt BE chạy môi trường chỉ để trả lời câu hỏi tài liệu.
**Nghiệm thu tài liệu:** FE có đủ SO/MRP/PO/GR và phần v3 để lập matrix; mọi phần chưa rõ được đánh số, không tự đoán từ mock. Chưa có nghiệm thu live.

## §4 — Hợp đồng lỗi mong đợi

Không đề nghị đổi mã lỗi trong task hỏi contract. Bảng là đối chiếu hoặc điều cần xác nhận, không phải lỗi đã quan sát:

| Tình huống | HTTP | code / contract | FE hiển thị/xử lý |
|---|---|---|---|
| Câu hỏi tài liệu | — | Không có thay đổi error contract | BE cung cấp bảng lỗi hiện hành trong phản hồi; không code |
| Khi tích hợp sau này | Theo contract BE cung cấp | Mã cụ thể trong OpenAPI/phản hồi | Không tự mặc định404 cho mọi ID hoặc409 cho mọi lỗi |

Envelope hiện hành được giữ: code/message, result khi có dữ liệu và errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Riêng import revalidation409 có result=batch INVALID theo GĐ3. X-Trace-Id giữ cho chẩn đoán, không đưa Authorization/token vào evidence. Lỗi validation/business/stale không tự retry như lỗi mạng; giữ input và source context, chỉ retry cùng intent theo contract idempotency của endpoint. Không áp dụng replay chung cho master endpoints đang bỏ qua header.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; Q có thể cần PO chốt policy. Owner trả lời BE, PO duyệt nghiệp vụ nếu có thay đổi; tất cả hiện **chưa trả lời**.

1. **Q1:** BE có thể cung cấp bộ bốn phản hồi còn thiếu hoặc bản hợp nhất đầy đủ không? — FE nghiêng về: Có để FE không suy contract từ mock — **CHẶN tích hợp SO/MRP/PO/GR**.
2. **Q2:** Hai v3 đang nhận có giữ vai trò phụ lục, không thay nội dung v1/v2 không? — FE nghiêng về: Có theo chính phần mở đầu của BE — **CHẶN version matrix**.
3. **Q3:** BE có thể cung cấp OpenAPI/build và một phép smoke nhận diện phiên bản trong cùng phản hồi không? — FE nghiêng về: Có, có thể trả lời offline; chưa yêu cầu bật server ngay — **CHẶN live acceptance, không chặn đọc tài liệu**.

## §6 — FE đã tự xử lý

FE đã lưu nguyên bản sáu phản hồi và tách trạng thái BE tự kiểm/FE tích hợp/live; chưa đổi adapter hoặc gọi API. Những khác biệt path/field/version/error FE sẽ tự mapping sau khi có bộ đầy đủ và PO duyệt code.

Phần còn lại FE tự đảm nhiệm khi được duyệt: DTO/adapter, hiển thị label/error, giữ input, quyền UI, cache invalidation và test phù hợp. Không xin BE sửa giao diện hoặc chứng minh pass FE thay FE. Không đổi flag/dependency, không tạo dữ liệu server trong phiên tài liệu; live acceptance vẫn chờ môi trường và contract được xác nhận.

## §7 — Tham chiếu

- BE_RESPONSE_GD_06_2026-10-17_v3.md — mở đầu G6c chỉ bổ sung.
- BE_RESPONSE_GD_07_2026-10-18_v3.md — mở đầu G7c chỉ bổ sung.
- FE_HANDOFF_GD_06_2026-10-14.md — phạm vi đầy đủ SO/MRP/import.
- FE_HANDOFF_GD_07_2026-10-14.md — phạm vi đầy đủ PO/GR/QC.

Chỉ ghi tên file vì hai bên không dùng chung repo. File này tự chứa vấn đề, tiêu chí và ví dụ cần cho task; không yêu cầu BE mở source FE. Các file phản hồi được ghi theo đúng tên nhận, không tự sửa ngày/phiên bản của BE.

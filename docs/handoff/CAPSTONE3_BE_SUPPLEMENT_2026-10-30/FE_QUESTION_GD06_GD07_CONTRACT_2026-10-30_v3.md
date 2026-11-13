# Gửi đủ contract và OpenAPI còn thiếu sau CF-8

## §0 — Kết luận trong 3 dòng

- Vấn đề: BE đã liệt kê11artifact là gửi kèm nhưng FE chưa nhận bytes, nên chưa kiểm được contract đầy đủ.
- Ảnh hưởng: Tích hợp SO/MRP/CF và typed DTO; không có kết luận thiếu endpoint hay yêu cầu BE làm lại chức năng.
- Mức ưu tiên: P1 — chặn phần tích hợp/acceptance tương ứng, không phải P0 hoặc kết luận API đã lỗi.

## §1 — Phạm vi và bản tiếp nối

**Bản:** v3 · **Ngày dự kiến gửi:** 30/10/2026 · **Mốc FE:** Session328, nối review Session327.
Tiếp nối **FE_QUESTION_GD06_GD07_CONTRACT_2026-10-28_v2.md** trong cùng chủ đề; không ghi đè bản cũ. Nếu gửi ngày khác đổi ngày tên/file trước gửi; sửa sau gửi tăng version trong cùng mạch. Owner FE/BE do PO phân công, chưa có tên trong phiên này. Lưu local, chưa tự gửi.

**Trong phạm vi:** Tiếp nối task contract, nhận đủ artifact thật và mapping version/build. Standard-cost DTO trong CF-8 đã được nhận, không xin viết lại.

**Ngoài phạm vi:** Không xin endpoint mới, không viết lại CF đã báo hoàn tất, không cần bật server chỉ để gửi file; không gửi credential.

**Căn cứ:** [Đã đối chiếu tài liệu] BE_RESPONSE_CF_8_2026-10-29.md §8.2 nói gửi11file cùng phản hồi; BE_REPORT_CF_COMPLETED_2026-10-30.md nói OpenAPI ngày28 vẫn đúng. [FE kiểm offline] Chưa thấy11file ở những vị trí PO đã cung cấp. Không giả định thiếu file nghĩa là thiếu implementation.

**Quyết định PO đã có:** ngày28/10 chọn giá chuẩn vật tư chụp RELEASE, hoãn ledger tồn/WIP, giữ BOM per-unit6/no1/3, bù hao hụt×(1+rate). Ngày30/10 giữ MRP CEILING6/WO HALF_UP6, chấp nhận chênh tối đa0.000001 mỗi dòng cùng cơ sở/source; BE bổ sung test chuỗi hao hụt và nontracked transfer/reserve/issue trước chốt hoàn tất. Không hỏi lại policy này hoặc tự mở rộng các giới hạn khác.

Thông tin **[BE báo cáo]** không là FE live acceptance. Những case, dữ liệu và tiêu chí dưới đây là **[Đề xuất nghiệm thu]** trừ quyết định PO đã ghi rõ; không là request/response server đã chạy. Phần đã đạt có thể đáp ứng bằng mapping/test/evidence phù hợp, không code trùng.

## §2 — Bằng chứng và giới hạn

Không có lỗi live được FE tái hiện trong phiên tạo tài liệu; không lập FE_DEFECT. Phiên này không probe trạng thái BE, không gọi API/management port, không chạy DB/SQL hoặc startup server.

| Mảnh bằng chứng live cùng lượt của FE | Hiện có |
|---|---|
| Request thật method/path/header/body | Không có |
| Response thật HTTP/code/nguyên văn | Không có |
| X-Trace-Id từ response thật | Không có; không tự tạo |
| Môi trường + giờ + binary BE đã xác minh | Chưa kiểm; chỉ có ngày soạn30/10/2026, không có giờ/live build của FE |
| ID record BE cùng lượt | Không có; fixture dưới đây là đề xuất, cần tạo record mới khi chạy |

```text
Mong đợi: BE cung cấp các đầu ra còn thiếu và evidence đúng bản theo §3, trả từng Q ở §5.
Thực tế: FE chỉ đối chiếu hai phản hồi BE; test đã báo đạt được ghi nhận, chưa có FE live acceptance.
```

## §3 — Đầu ra tài liệu — chỉ cung cấp/xác nhận, không giao code

**Không có yêu cầu code BE trong file QUESTION này.** D01… là đầu ra tài liệu/contract; không mở task triển khai từ một yêu cầu gửi file.

| # | Đầu ra đúng/sai kiểm được | Endpoint/điểm xử lý | Chặn FE? |
|---|---|---|---|
| D01 | Gửi đủ11file §3 hoặc một gói hợp nhất tự chứa có mapping tên/phiên bản thay thế rõ. | Artifact bàn giao; không endpoint mới | Chặn contract matrix |
| D02 | OpenAPI đi kèm đúng bản dự kiến tích hợp và hash thực; nếu dùng bản28/10 ghi rõ áp dụng sau CF13, ràng buộc @Digits đọc từ đâu. | BE_OPENAPI_2026-10-28.json hoặc bản thay thế | Chặn DTO/validation mapping |
| D03 | Tài liệu đánh dấu phần BE đã test unit/IT/HTTP và phần chưa chạy; mốc binary riêng với migration, không chỉ ghi đã soạn. | Version map và evidence report | Chặn đóng task tài liệu |

### Danh sách phải có trong gói thực tế

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

Không xin lại GĐ7v1/v2/v3 hoặc CF-8/report30 đã nhận. Nếu gửiZIP, liệt kê entry để không nhầm danh sách tên file với file thật. Không đưa password/token vào gói.

### Mốc OpenAPI và version

BE đã nêu SHA256 OpenAPI220ee57728813005b48496f04cd1f358c12cc416a4defeb90a1d37c6198e95aa, nhưng FE chưa có file để hash/parse. Nếu đúng file này, gửi bytes; nếu bản mới đổi, gửi hash/build/version mapping thay vì ép hash cũ.
OpenAPI chưa thể hiện @Digits phải kèm bảng path/field/precision/scale hiện hành, bao gồm20field/14request CF-10 và các exception response12decimal. Không tự đổi read schema12 thành input6. Phản hồi tổng mới đã cung cấp bảng14request nên có thể dùng nó làm appendix, không yêu cầu code validator mới ở task này.
Binary cuối BE báo30/10 là448000a60bc6d1b7e56b121da681943dfda456d1/build2026-10-30T02:09:25.395Z, jar SHA2560e259a896dcaaa87e37d307d98231aad14febedd5a9dd3b7aed8b3faf2fd6c91, migration95. Nếu có bản mới hơn nêu mốc mới và quan hệ kế thừa; không dùng migration một mình làm proof code. Không cần cấp FE quyền SQL/cổng quản trị để gửi tài liệu offline.

### Điều kiện khép

FE có bytes đọc được/parse được và map đủ SO/MRP/CF, biết path/body/required-nullability/numeric/permissions/scope/versions/errors/replay/legacy theo từng endpoint. Có thể tái sử dụng artifact hiện có, không cần làm tài liệu mới chỉ để đổi ngày. Bản cũ được giữ lịch sử, cần chỉ rõ superseded phần nào.
Không có field/endpoint mới được xin, nên bảng5cột không áp dụng. Task QUESTION chỉ cung cấp/xác nhận, không triển khai API.

## §4 — Hợp đồng lỗi

Không thay error contract trong task này. Bảng dưới chỉ nêu cách xử lý phần tài liệu thiếu.

| Tình huống | HTTP | code / contract | FE xử lý |
|---|---|---|---|
| Xin tài liệu | — | Không có đổi mã lỗi BE | Chỉ gửi artifact/mapping |
| OpenAPI chưa thể hiện constraint | — | Không tạo lỗi runtime giả | Kèm numeric matrix thật, không giả FE đã gọi API |

Envelope giữ code/message, result khi có dữ liệu, errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Import revalidation409 có thể mang result=batch INVALID. LOT_CODE_ALREADY_EXISTS ở race có thể thiếu errors[]; nhận theo code. Không parse message để quyết định nghiệp vụ.
Giữ X-Trace-Id trong evidence, che Authorization/password/token. Error/stale không tự retry như transport; giữ input, dùng version server và key đúng endpoint. Không suy master upsert có replay/expectedVersion nếu server không hỗ trợ.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi PO chọn lại công thức/rounding; BE xác nhận cách cung cấp và loại bằng chứng. Tất cả Q chưa có phản hồi cho bản tiếp nối này.

1. **Q1:** BE có thể gửi đủ bytes11artifact hoặc ZIP tương đương không? — FE nghiêng về: Có; danh sách tên hoặc câu đã soạn chưa thay file thật — **CHẶN contract matrix**.
2. **Q2:** BE có xác nhận file OpenAPI gửi kèm áp dụng cho binary cuối sau patch version không? — FE nghiêng về: Có, nếu đổi file thì nêu hash/mốc mới — **CHẶN typed adapter**.
3. **Q3:** Bảng version có phân biệt smoke trước patch và sau patch không? — FE nghiêng về: Có để không ghi nhầm final binary đã smoke — **CHẶN acceptance metadata**.

## §6 — FE đã xử lý

FE đã archive nguyên hai phản hồi và ghi rõ artifact chưa nhận, không sửa bản BE hoặc tự dựng OpenAPI. Các DTO/parser/permission/version của FE là FE tự làm sau duyệt code.

FE tự làm parser lossless, adapter/DTO, mandatory version bodies, label/permission UI và field errors sau khi được duyệt code. Phiên328 chỉ tài liệu: không sửa app/mock/dependency/API flag, không chạy test ứng dụng/build/browser hoặc phát sinh record BE. BE cung cấp evidence không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- FE_QUESTION_GD06_GD07_CONTRACT_2026-10-28_v2.md — task trước, bản nàyv3 chỉ hỏi phần chưa nhận.
- BE_RESPONSE_CF_8_2026-10-29.md — §8.2 danh sách11file.
- BE_REPORT_CF_COMPLETED_2026-10-30.md — OpenAPI hash/binary mới.

Chỉ ghi tên file vì hai bên không dùng chung repo. Nội dung task tự chứa tiêu chí/fixture; không yêu cầu BE mở source FE. Các file còn thiếu được xin ở contract task, không mặc định FE đã nhận.

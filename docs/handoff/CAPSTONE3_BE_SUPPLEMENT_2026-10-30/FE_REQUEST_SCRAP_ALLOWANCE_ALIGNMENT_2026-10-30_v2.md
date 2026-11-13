# Bổ sung test xuyên BOM–MRP–WO–giá thành theo cách làm tròn đã chốt

## §0 — Kết luận trong 3 dòng

- Vấn đề: Từng khâu đã có test nhưng BE xác nhận chưa có test chuỗi hao hụt>0; PO yêu cầu bổ sung trước chốt.
- Ảnh hưởng: Nhu cầu vật tư và baseline chi phí trong luồng sản xuất; đây là yêu cầu coverage, chưa là defect số liệu.
- Mức ưu tiên: P1 — chặn phần tích hợp/acceptance tương ứng, không phải P0 hoặc kết luận API đã lỗi.

## §1 — Phạm vi và bản tiếp nối

**Bản:** v2 · **Ngày dự kiến gửi:** 30/10/2026 · **Mốc FE:** Session328, nối review Session327.
Tiếp nối **FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT_2026-10-28.md** trong cùng chủ đề; không ghi đè bản cũ. Nếu gửi ngày khác đổi ngày tên/file trước gửi; sửa sau gửi tăng version trong cùng mạch. Owner FE/BE do PO phân công, chưa có tên trong phiên này. Lưu local, chưa tự gửi.

**Trong phạm vi:** Test chuỗi canonical server có hao hụt>0, source/snapshot/rounding và baseline theo requiredQuantity đã lưu; chỉ sửa logic nếu test chứng minh thiếu/sai.

**Ngoài phạm vi:** Không đổi MRP sang HALF_UP để bằng WO, không thay netting/MAKE lot sizing hoặc mở rationals1/3, không hồi tố snapshot cũ, không mở sổ tồn/WIP.

**Căn cứ:** [BE báo cáo] CF11 dùng1+rate, MRP CEILING6/WO HALF_UP6, response WO đã round6 và baseline kế thừa. BE_RESPONSE_CF_8_2026-10-29.md §7.2/§8 và report30/10 đều nêu test chuỗi chưa viết. [PO đã chốt30/10] Giữ rounding hiện tại, BE bổ sung test trước hoàn tất.

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

## §3 — Đầu ra kiểm thử/bằng chứng — BE bổ sung phần còn thiếu

**Yêu cầu trọng tâm là test/evidence.** Chỉ sửa implementation nếu test chứng minh sai hoặc thiếu; không mở thêm phân hệ. R01… là tiêu chí kiểm được; chỉ gửi tổng suite xanh chưa đủ nếu chưa có assertion đúng case.

| # | Đầu ra đúng/sai kiểm được | Endpoint/điểm xử lý | Chặn FE? |
|---|---|---|---|
| R01 | Test đi qua BOM source→run MRP→MAKE conversion/WO→RELEASE baseline bằng canonical workflow, không ghi thẳng expected quantities rồi coi là đã test chuỗi. | BOM draft; POST /api/v1/planning-runs; conversion GĐ6; WO release | Chặn chain gate |
| R02 | Case allowance10% cho base20 có MRP gross22 và WO required22 từ cùng BOM; cost baseline vật tư22×1000=22000, không nhân allowance lần hai. | Requirements/WO component/cost-baseline DTO | Chặn semantics |
| R03 | Case biên có cùng cơ sở cho MRP CEILING6 và WO HALF_UP6, chênh hợp lệ<=0.000001 mỗi dòng; không assert phải bằng nhau. | MRP source line và WO componentLineId tương ứng | Chặn rounding coverage |
| R04 | Baseline lấy requiredQuantity WO đã lưu và giá chuẩn tại RELEASE; đổi master sau đó không hồi tố baseline/run/WO cũ. | GET /api/v1/work-orders/{id}/cost-baseline; snapshots | Chặn source consistency |
| R05 | Test phân biệt gross requirement với net sau stock/safety; không dùng một giá trị net0 để bỏ kiểm gross22. | MRP run requirements | Chặn đúng ý nghĩa test |
| R06 | BE trả tên test, dữ liệu/expected/actual và bản chạy; nếu source hợp lệ khác biệt ngoài rounding thì nêu sai khác, không clamp hoặc gán tolerance chung che lỗi. | Báo cáo IT + smoke nếu đã có | Chặn kết luận |

### Quyết định đã chốt, không hỏi lại

Lượng cơ bản×(1+rate); giá chuẩn chụp RELEASE; BOMper-unit6/no1/3. PO30/10 giữ MRP làm tròn lên6 và WO HALF_UP6. Chênh1e-6 chỉ trên dòng cùng source/cơ sở; không phải tolerance của mọi dòng cộng lại hoặc mọi cấp, không che sai BOM/version/UOM. Yêu cầu HALF_UP6 thống nhất mọi nơi trong bản trước được thay bằng quyết định này.

### Fixture đề xuất — chưa chạy BE

C01: BOM1 PCS cần2 KG, rate0.1; demand/MAKE/WO10 PCS, no finished stock/no other output supply/no lot-size rounding. RM có sẵn đủ nguồn hợp lệ, safety0; MRP gross requirement22, net có thể0 do RM đủ, **không nhầm net với gross**. WOrequired22; standardRM1000 tạiRELEASE →planned material22000. Nếu cần kết quả total, planned operation60phút/capacity1, labor6000/OH3000 →planned total31000, khác fixture không hao hụt29000. Không trộn hai bộ số. Có thể consume22/Good10 để cost actual31000, nhưng không bắt buộc full close để chứng minh baseline.
C02: cùng nguồn finite per-unit1.000001, planned10, rate0.000001 (0.0001%) →unrounded10.00002000001; MRP CEILING6=10.000021, WO HALF_UP6=10.000020. Giá chuẩn1000 →baseline material10000.020000, không lấy MRP amount10000.021000 thay WO.
C03 unit boundary độc lập (không ép đổi rule làm tròn MAKE):1.2×1.000001×1.1=1.32000132 →MRP1.320002/WO1.320001, baseline material1320.001000 ở giá1000.
Giá trị MRP so sánh là lượng kế hoạch tại biên CEILING6 mà BE công bố; nếu DTO còn trả gross trung gian precision cao thì BE chỉ rõ field nguồn và field đã làm tròn, không ép mọi field read thành6 số lẻ. C01 có gross22 chính xác nên không phụ thuộc khác biệt biểu diễn này.
Nếu conversion có chính sách lot sizing, BE dựng C01/C02 với batch setting không đổi planned10 và ghi config. Không giả tạo suggestion hoặc bỏ qua conversion mà vẫn gọi test “MRP→MAKE”. Test manual WO có thể thêm riêng nhưng không thay chainconversion.

### Cách kiểm và quyền

BOM dùng Company permission hiện hành, MRP run/manage và conversion theo runtime GĐ6, WO/RELEASE đúng Plant/kho/principal. Cost read đúng quyền, không lộ tiền cho MRP-only. Test setup master/stock trước run được phép; expected requirement phải do command thật tạo. Snapshot source IDs/version đối chiếu xuyên DTO, reload/read-back chứng minh số lưu. Không thêm endpoint/field mới; giữ body/version/key theo contract hiện hành.
Chạy test trên DB thật/transaction thích hợp với assertions kiểm lỗi nếu đổi1+rate sang chia hoặc nhân rate lần hai. BE có thể tái sử dụng test nếu chứng minh đúng chuỗi, không chỉ gửi tổng số suite xanh.

## §4 — Hợp đồng lỗi

Giữ mã BE đã có; chỗ chưa rõ mapping phải được BE nêu, không dùng test sai trạng thái/quyền để giả đã kiểm protected stock. Đây không là danh sách lỗi FE đã gặp.

| Tình huống | HTTP | code / contract | FE xử lý |
|---|---|---|---|
| Input rate sai scale/range | 400 | VALIDATION_ERROR theo field | Giữ input, không tự round recipe |
| Import sai rate | 201 batch INVALID | SUCCESS với row issue theo contract; commit409 STATE_CONFLICT | Không partial create |
| Version/source stale | 409 | CONCURRENT_MODIFICATION hoặc stale code GĐ6 hiện hành | Refetch/xác nhận lại |
| Test phát hiện số ngoài policy | Không giả lỗi HTTP | Báo assertion/diff đúng record source | Không clamp thành pass |

Envelope giữ code/message, result khi có dữ liệu, errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Import revalidation409 có thể mang result=batch INVALID. LOT_CODE_ALREADY_EXISTS ở race có thể thiếu errors[]; nhận theo code. Không parse message để quyết định nghiệp vụ.
Giữ X-Trace-Id trong evidence, che Authorization/password/token. Error/stale không tự retry như transport; giữ input, dùng version server và key đúng endpoint. Không suy master upsert có replay/expectedVersion nếu server không hỗ trợ.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi PO chọn lại công thức/rounding; BE xác nhận cách cung cấp và loại bằng chứng. Tất cả Q chưa có phản hồi cho bản tiếp nối này.

1. **Q1:** Test chain R01 có thể dùng conversion thật và kiểm source IDs xuyên các bước không? — FE nghiêng về: Có, để không nhầm unit calculator test với flow — **CHẶN chain acceptance**.
2. **Q2:** BE xác nhận giữ CEILING6/HALF_UP6 theo PO30/10 trong expected values mới không? — FE nghiêng về: Có, không sửa BE chỉ để hai số luôn bằng nhau — **KHÔNG CHẶN quyết định policy; chặn test mapping**.
3. **Q3:** BE có thể chỉ rõ assertions bắt double-allowance và sai source rounding không? — FE nghiêng về: Có, test đúng lỗi thay vì chỉ đủ số lượng — **CHẶN hoàn tất BE**.

## §6 — FE đã xử lý

FE đã ghi hai quyết định30/10, chưa sửa mock/parser/adapter. Không báo số BE đã sai khi chưa chạy; file chỉ khép coverage thiếu theo chính BE xác nhận.

FE tự làm parser lossless, adapter/DTO, mandatory version bodies, label/permission UI và field errors sau khi được duyệt code. Phiên328 chỉ tài liệu: không sửa app/mock/dependency/API flag, không chạy test ứng dụng/build/browser hoặc phát sinh record BE. BE cung cấp evidence không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT_2026-10-28.md — task trước, bản nàyv2 thay expectation rounding theo PO30/10.
- BE_REPORT_CF_COMPLETED_2026-10-30.md — CF11/§7 test chain chưa có.
- BE_RESPONSE_CF_8_2026-10-29.md — §7.2/§8.1 phân loại unit/IT/HTTP.

Chỉ ghi tên file vì hai bên không dùng chung repo. Nội dung task tự chứa tiêu chí/fixture; không yêu cầu BE mở source FE. Các file còn thiếu được xin ở contract task, không mặc định FE đã nhận.

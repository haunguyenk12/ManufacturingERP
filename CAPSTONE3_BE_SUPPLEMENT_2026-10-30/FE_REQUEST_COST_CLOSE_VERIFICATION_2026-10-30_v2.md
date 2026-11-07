# Chạy lại smoke phiếu nhập và chốt giá thành trên binary sau bản sửa version

## §0 — Kết luận trong 3 dòng

- Vấn đề: Smoke29/10 chạy trước patch receipt approve nontracked version+2; cần kiểm bản cuối và phân loại bằng chứng đúng.
- Ảnh hưởng: Dual-version receipt/WO và close/replay; không phải khẳng định patch mới vẫn có lỗi.
- Mức ưu tiên: P1 — chặn phần tích hợp/acceptance tương ứng, không phải P0 hoặc kết luận API đã lỗi.

## §1 — Phạm vi và bản tiếp nối

**Bản:** v2 · **Ngày dự kiến gửi:** 30/10/2026 · **Mốc FE:** Session328, nối review Session327.
Tiếp nối **FE_REQUEST_COST_CLOSE_VERIFICATION_2026-10-28.md** trong cùng chủ đề; không ghi đè bản cũ. Nếu gửi ngày khác đổi ngày tên/file trước gửi; sửa sau gửi tăng version trong cùng mạch. Owner FE/BE do PO phân công, chưa có tên trong phiên này. Lưu local, chưa tự gửi.

**Trong phạm vi:** Smoke targeted trên binary cuối/higher successor, approve tracked/nontracked và đường cost-close cơ bản; nhận lại metadata/evidence.

**Ngoài phạm vi:** Không làm lại A39/A40 IT đã báo pass chỉ để đủ số test, không inject lỗi production, không mở stock valuation/COGS nontracked/no-routing hoặc đòi đổi7cf3703 rồirollback.

**Căn cứ:** [BE báo cáo] Smoke thuộc7cf3703eb99eeed9cd8296f743e33b9e4593a1d9; CF8 cuối file nói patch version ở commit kế. Report30/10 nêu448000a60bc6d1b7e56b121da681943dfda456d1/build02:09:25.395Z, IT fix pass nhưng HTTP chưa lặp. A39/A40 IT nay đã có, không tiếp tục ghi là thiếu test riêng.

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
| R01 | Ghi app.commit/build.time và jar hash của đúng process chạy smoke; nếu bản mới hơn448000a nêu quan hệ chứa fix, migration không thay binary proof. | GET /actuator/info ở cổng quản trị khi được phép hoặc metadata BE cung cấp | Chặn đúng bản |
| R02 | Approve production receipt tracked vànontracked mỗi lần làm receipt version+1 và WOversion+1; GET đọc lại trùng revision nhận, replay không approve lần hai. | POST /api/v1/work-orders/{W}/production-receipts/{R}/approve; GET receipt/WO | Chặn version regression |
| R03 | Lặp đường chuẩn29k từ standard/rates→release→consume→receipt/QC→preview→finalize/readback/replay trên dataset mới. | GĐ11C1–C13 và source workflow | Chặn running-app proof |
| R04 | Case expectedVersion sai không ghi; same-key replay trả cùng kết quả/không thêmadjustment; tiền và quantity giữ đúng. | POST /api/v1/work-orders/{id}/finalize-cost-and-close; GET run/adjustments | Chặn command effects |
| R05 | Báo rõ A39/A40 là IT, phần nào HTTP được lặp trên bảncuối, phần nào chưa chạy; không đổi7/7 cũ thành7/7 bảnmới. | Report cùng build/fixture/trace | Chặn kết luận |
| R06 | Cung cấp wire enum run state và mapping/version DTO đúng GET mới, không chỉ dùng chữCLOSED trong bảng diễn giải. | GET /api/v1/work-orders/{id}/cost-runs/{runId}; OpenAPI đi kèm | Chặn DTO mapping |

### Mốc tham chiếu do BE báo, không phải FE đã gọi

| Mốc | Binary / artifact |
|---|---|
| Smoke 29/10 | commit 7cf3703eb99eeed9cd8296f743e33b9e4593a1d9; build 2026-10-29T15:50:55.463Z; jar SHA256 9f46693515802501b9d09e7147c6fb2c0ffa460768a6ae571cf413a51b338a00 |
| Bản cuối report 30/10 | commit 448000a60bc6d1b7e56b121da681943dfda456d1; build 2026-10-30T02:09:25.395Z; jar SHA256 0e259a896dcaaa87e37d307d98231aad14febedd5a9dd3b7aed8b3faf2fd6c91; migration95 |

Không tự coi hai mốc là mâu thuẫn. Không ép dùng binary cũ nếu có successor; cần chứng minh process chạy smoke chứa fix. Cổng8091/8090 là mốc dev cũ, không là cấu hình mặc định cho FE. Nếu FE không vào management port, BE cung cấp evidence tương đương.

### Dataset / expected đề xuất

Fixture29k riêng, không trộn case hao hụt: WO10 PCS, RM20 KG×standard1000 chụp RELEASE, capacity1/setup0/run6 phút mỗi PCS, planned/actual60 phút, labor6000/OH3000 mỗi giờ. Consume20, Good10, scrap/rework0, receipt/QC10; output Item+Warehouse physical0 trước release, không mixed pool/transfer/downstream. Planned=actual=20000+6000+3000=29000; unit2900. Close không đợi invoice/payment. C10 ghi một transaction, một adjustment quantity0/value29000 theo partition của fixture, FG physical10 không đổi.
Dùng dataset tracked và nontracked riêng nếu cần tránh trộn source. Approve lấy version từ submit response, không đoán revision ban đầu. Assert approvedReceipt.version=submittedReceipt.version+1 và workOrderVersion tương ứng+1. Replay dùng cùng key/body; đọc lại canonical vì receipt replay có thể trả trạng thái hiện tại.
C10 body hiện hành {expectedVersion,expectedSourceVersion,runId,expectedRunVersion,reason}, header Idempotency-Key. Sau preview dùng source token mới nhất từ GET. Wrong version409 không write; chốt đúng đưa WO CLOSED, run final state theo enum thực, C9/C12 read-back và replay không thêm row. Không tạo fractional/wastage fixture ở task này; allowance có task test riêng.

### Quyền và bằng chứng

C10 cần PERM_WORK_ORDER_CLOSE và cost view (PERM_WORK_ORDER_VARIANCE_READ hoặc PERM_PRODUCTION_COST_CALCULATE), đúng Company/Plant, principal server. Không trộn fix version với quyền creator/approver; dùng actor được phép, không gửi password/token.
Mỗi smoke case ghi đủ5 mảnh cùng run: request method/path/body/header đã che, response HTTP/code/nguyên văn, X-Trace-Id, môi trường/giờ/build và record IDs. Giữ lịch sử smoke7cf3703, không ghi đè trace.
Chưa có môi trường thì ghi “Chưa chạy” cùng điều kiện. Không đổi nhãn IT thành HTTP smoke. Expired/capacity2 probes chưa lặp có thể ghi riêng evidence IT; không giả đã đo hoặc sửa SQL dữ liệu thật để tạo lỗi.

## §4 — Hợp đồng lỗi

Giữ mã BE đã có; chỗ chưa rõ mapping phải được BE nêu, không dùng test sai trạng thái/quyền để giả đã kiểm protected stock. Đây không là danh sách lỗi FE đã gặp.

| Tình huống | HTTP | code / contract | FE xử lý |
|---|---|---|---|
| Receipt/WO version sai | 409 | CONCURRENT_MODIFICATION theo contract hiện hành | Refetch giữ input |
| Cost source stale | 409 | COST_SOURCE_STALE | Tính lại khi cần |
| Legacy close trên WOcóbaseline | 409 | CLOSE_BLOCKED | Dùng C10 |
| Quyền không đủ | 403 | PERMISSION_DENIED | Không chốt/lộ tiền |
| Lỗi quan sát lúc smoke | Theo response thực | Không dựngcode/trace để đủ checklist | Ghi nguyên actual cùng record và build |

Envelope giữ code/message, result khi có dữ liệu, errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Import revalidation409 có thể mang result=batch INVALID. LOT_CODE_ALREADY_EXISTS ở race có thể thiếu errors[]; nhận theo code. Không parse message để quyết định nghiệp vụ.
Giữ X-Trace-Id trong evidence, che Authorization/password/token. Error/stale không tự retry như transport; giữ input, dùng version server và key đúng endpoint. Không suy master upsert có replay/expectedVersion nếu server không hỗ trợ.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi PO chọn lại công thức/rounding; BE xác nhận cách cung cấp và loại bằng chứng. Tất cả Q chưa có phản hồi cho bản tiếp nối này.

1. **Q1:** Bản chạy smoke mới có chứa fix approve-version và metadata ứng với đúng process không? — FE nghiêng về: Có, cho phép successor nếu chỉ rõ build — **CHẶN runtime evidence**.
2. **Q2:** BE có thể gửi riêng transcript after-fix thay ghi lại trace cũ thành kết quả mới không? — FE nghiêng về: Có, giữ cả lịch sử trước/sau — **CHẶN chốt smoke**.
3. **Q3:** Wire enum state của cost run có được xác nhận bằng GET/OpenAPI thực tế thay chữ mô tả không? — FE nghiêng về: Có, FE không tự suy FINALIZED hay CLOSED từ prose — **CHẶN run-state mapping**.

## §6 — FE đã xử lý

FE chỉ đối chiếu hai bản report, chưa gọi management/API hoặc chạy smoke; chưa sửa adapter. FE luôn lấy version từserver sẽ tránh tự cộng sai nhưng không thay test contract+1 mà BE đã chốt.

FE tự làm parser lossless, adapter/DTO, mandatory version bodies, label/permission UI và field errors sau khi được duyệt code. Phiên328 chỉ tài liệu: không sửa app/mock/dependency/API flag, không chạy test ứng dụng/build/browser hoặc phát sinh record BE. BE cung cấp evidence không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- FE_REQUEST_COST_CLOSE_VERIFICATION_2026-10-28.md — task trước; v2 ghi nhận IT đã có, chỉ xin smoke/mapping còn thiếu.
- BE_RESPONSE_CF_8_2026-10-29.md — transcript trước sửa và note §8.
- BE_REPORT_CF_COMPLETED_2026-10-30.md — binary cuối và test fix version.

Chỉ ghi tên file vì hai bên không dùng chung repo. Nội dung task tự chứa tiêu chí/fixture; không yêu cầu BE mở source FE. Các file còn thiếu được xin ở contract task, không mặc định FE đã nhận.

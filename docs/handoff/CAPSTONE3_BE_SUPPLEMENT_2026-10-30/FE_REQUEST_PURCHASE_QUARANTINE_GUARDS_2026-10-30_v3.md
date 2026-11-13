# Bổ sung test chuyển kho, giữ chỗ và xuất hàng không theo lô đang chờ QC

## §0 — Kết luận trong 3 dòng

- Vấn đề: Count/adjust đã có test nhưng ma trận CF12 còn thiếu transfer/reserve/issue nontracked; PO yêu cầu bổ sung trước chốt.
- Ảnh hưởng: Bảo vệ quantity pending/rejected tại Item+Warehouse; thiếu test không đồng nghĩa đã tái hiện bypass.
- Mức ưu tiên: P1 — chặn phần tích hợp/acceptance tương ứng, không phải P0 hoặc kết luận API đã lỗi.

## §1 — Phạm vi và bản tiếp nối

**Bản:** v3 · **Ngày dự kiến gửi:** 30/10/2026 · **Mốc FE:** Session328, nối review Session327.
Tiếp nối **FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-28_v2.md** trong cùng chủ đề; không ghi đè bản cũ. Nếu gửi ngày khác đổi ngày tên/file trước gửi; sửa sau gửi tăng version trong cùng mạch. Owner FE/BE do PO phân công, chưa có tên trong phiên này. Lưu local, chưa tự gửi.

**Trong phạm vi:** Coverage còn thiếu của nontracked purchase quarantine, canonical transfer/reservation/issue và rollback; giữ các test count/adjust hiện có.

**Ngoài phạm vi:** Không sửa/đảo QC, không tạo fake lot cho nontracked, không thêm workflow correction nguồn hoặc khóa mọi stock free; không giao lại count/adjust nếu assertions đã đủ.

**Căn cứ:** [BE báo cáo] CF8 §7.4 và report30/10 §7/§8 ghi transfer/reserve/issue nontracked thiếu test. Count25/accept20/reject5 và race QC+count đã có IT. [PO30/10] Bổ sung các test này trước hoàn tất, không thêm tính năng.

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
| R01 | Test transfer từ vị thế có protected5 chỉ chuyển được free hợp lệ; chuyển vượt free không ăn vào bucket QC. | POST /api/v1/inventory/transfers | Chặn transfer coverage |
| R02 | Test reservation đúng vị thế không giữ protected5; auto/manual behavior theo contract không thể trả đủ giả nhờ phần QC. | POST /api/v1/work-orders/{id}/reserve; POST /api/v1/work-orders/{id}/material-reservations | Chặn reserve coverage |
| R03 | Test issue nontracked không dùng pending/rejected; đủ state/source/permission để assertion kiểm đúng stock guard. | POST /api/v1/inventory/issue; WO material issue canonical mapping nếu dùng chung ledger | Chặn issue coverage |
| R04 | Test valid free flow không bị khóa cả position; OnHand/reserved/available/protected và source receipt/QC giữ invariant. | Canonical read-after-write | Chặn positive path |
| R05 | Multi-line dòng sau vi phạm protected rollback toàn bộ; replay không post hai lần, không giảm bucket; trường hợp cạnh tranh thích hợp có assertions cuối transaction. | Transfer/issue/reservation transaction | Chặn integrity |
| R06 | Cập nhật ma trận unit/IT/HTTP theo đúng test tên và assertion, giữ thông tin chỗ chưa test; tái dùng test sẵn có nếu chứng minh đủ. | Evidence matrix CF12 bổ sung | Chặn đóng task |

### Fixture đề xuất, mỗi ca độc lập — chưa chạy FE/BE

Một Item nontracked tạiWH1 đã nhận25, QC accept20/reject5: OnHand25, qualityHold/protected5, available20, reserved0. Không fake lot; receipt-line/bucket source được giữ. Có thể thêm case pending5 thay rejected5 để không chỉ bảo vệ một trạng thái. Các case dưới reset fixture bằng setup test riêng, không cộng nối kết quả giữa các dòng.

| Case | Thao tác | Kết quả kiểm được |
|---|---|---|
| T01 | Transfer21 từWH1 sangWH2 hợp lệ cùngPlant | Không ăn protected; command không ghi phiếu/stock/movement một phần |
| T02 | Transfer5 từfree | WH1 OnHand20/protected5/available15; WH2 nhận5free; source QC không đổi |
| H01 | Manual reserve21 đúngWH1 choWO hợp lệ, noother supply | Từ chối và0write theo contract manual all-or-nothing; không tạo reservation21 bằng protected |
| H02 | Manual reserve5 hợp lệ | OnHand25/protected5/reserved5/available15, bucket/receipt không đổi |
| I01 | Generic issue21 | Từ chối, stock/movement/bucket không đổi |
| I02 | Generic issue5free | OnHand20/protected5/available15, đúng một posting và source QC không đổi |

Với manual reservation nếu contract là all-or-nothing phải trả insufficient và0write; không dùng tùy chọn cap để đổi behavior. Auto reserve có thể partial<=20 theo contract hiện hành, không coi partial là lỗi. WO test phải đủ trạng thái/component quota/kho hợp lệ để chạm đến guard stock; không dùng “WO chưa release” hoặc lượng input sai làm bằng chứng đã chặn QC.
Nếu test WO issue/approve riêng, dựng command hợp lệ và nguồn entitlement hợp lệ theoNORMAL/SUPPLEMENTAL, kiểm lại free lúc post/approve. Không suy chỉ từ inventory issue mà mọi alias WO đã đủ khi chúng không dùng chung canonical guard; BE ghi mapping cụ thể.
Không yêu cầu mọi case phải HTTP trên server dùng dữ liệu thật. IT DB thật với assertions ledger/source đủ cho coverage, smoke đánh dấu riêng khi có.

### Invariants / concurrency / quyền

OnHand không thấp hơn protected + reservations disjoint hợp lệ; protected không âm và không giảm chỉ vì generic command. Không clamp bucket để cho command qua. Free stock khác có thể được dùng bình thường; không đổi25nontracked thành lot mới.
BE dùng auth/scope hiện hành: inventory transfer/issue PERM_INVENTORY_MOVE tại kho liên quan, WO reservation PERM_MATERIAL_RESERVATION_MANAGE đúng WO/Plant; nếu dùng issue WO cần quyền issue/approve tương ứng và principal hiện hành. Không cấp thêm role hoặc quyền theo tên ADMIN.
Tranh chấp issue/transfer/reserve vớiQC trên cùng nguồn phải kiểm canonical availability tại thời điểm ghi; assertion số lượng cuối, rollback dòng cuối và current auth/replay. Task không mở lại count/adjust code đã được CF12 chứng minh. Tracked HOLD/REJECTED reservation còn thiếu test được báo tiếp trong matrix, không tự thêm vào phạm vi PO mới chốt về nontracked.

## §4 — Hợp đồng lỗi

Giữ mã BE đã có; chỗ chưa rõ mapping phải được BE nêu, không dùng test sai trạng thái/quyền để giả đã kiểm protected stock. Đây không là danh sách lỗi FE đã gặp.

| Tình huống | HTTP | code / contract | FE xử lý |
|---|---|---|---|
| Vượt free/available quantity | 409 | INSUFFICIENT_AVAILABLE_STOCK hoặc mã quota tương đương do BE nêu theo endpoint | Giữ input, không lấy phầnQC |
| Vi phạm source/protected invariant | 409 | STATE_CONFLICT hoặc LOT_NOT_ELIGIBLE khi endpoint liên quan lot | Không post một phần |
| Version cũ | 409 | CONCURRENT_MODIFICATION | Refetch giữ input |
| Không quyền/scope | 403 | PERMISSION_DENIED | Không lộ nguồn ngoài phạm vi |

Envelope giữ code/message, result khi có dữ liệu, errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Import revalidation409 có thể mang result=batch INVALID. LOT_CODE_ALREADY_EXISTS ở race có thể thiếu errors[]; nhận theo code. Không parse message để quyết định nghiệp vụ.
Giữ X-Trace-Id trong evidence, che Authorization/password/token. Error/stale không tự retry như transport; giữ input, dùng version server và key đúng endpoint. Không suy master upsert có replay/expectedVersion nếu server không hỗ trợ.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi PO chọn lại công thức/rounding; BE xác nhận cách cung cấp và loại bằng chứng. Tất cả Q chưa có phản hồi cho bản tiếp nối này.

1. **Q1:** Các test hiện có có thể được tái dùng nếu BE trỏ đúng assertions R01–R05 không? — FE nghiêng về: Có, không bắt viết test trùng — **KHÔNG CHẶN code; chặn coverage evidence**.
2. **Q2:** BE có thể tách số liệu từng fixture độc lập và báo rõ test path WO/generic dùng guard chung không? — FE nghiêng về: Có để không chứng minh nhầm alias — **CHẶN integrity acceptance**.
3. **Q3:** BE có giữ partial auto-reserve và all-or-nothing manual theo contract hiện hành thay vì đổi semantics để test qua không? — FE nghiêng về: Có, task chỉ thêm coverage, không thay behavior — **CHẶN assertion mapping**.

## §6 — FE đã xử lý

FE đã ghi PO cần BE test trước hoàn tất, không báo lỗi kho đã xảy ra. Chưa đổi UI/guard/API hoặc chạy mutation; FE vẫn phải nghiệm thu độc lập khi tích hợp.

FE tự làm parser lossless, adapter/DTO, mandatory version bodies, label/permission UI và field errors sau khi được duyệt code. Phiên328 chỉ tài liệu: không sửa app/mock/dependency/API flag, không chạy test ứng dụng/build/browser hoặc phát sinh record BE. BE cung cấp evidence không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-28_v2.md — bản trước, v3 chỉ bổ sung coverage còn thiếu.
- BE_RESPONSE_CF_8_2026-10-29.md — §7.4 ma trận test.
- BE_REPORT_CF_COMPLETED_2026-10-30.md — CF12 count/adjust đã có, nontracked paths thiếu.

Chỉ ghi tên file vì hai bên không dùng chung repo. Nội dung task tự chứa tiêu chí/fixture; không yêu cầu BE mở source FE. Các file còn thiếu được xin ở contract task, không mặc định FE đã nhận.

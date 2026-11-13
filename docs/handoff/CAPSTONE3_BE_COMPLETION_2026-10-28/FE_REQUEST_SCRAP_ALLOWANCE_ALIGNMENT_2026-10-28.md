# Đồng nhất bù hao hụt giữa BOM, MRP, WO và giá thành

## §0 — Kết luận trong 3 dòng

- Vấn đề: PO đã chốt bù hao hụt là lượng cộng thêm; phải tránh MRP và WO dùng hai công thức khác nhau.
- Ảnh hưởng: Nhu cầu vật tư, mua/cấp cho sản xuất và cost baseline; chặn nghiệm thu consistency có scrap>0.
- Mức ưu tiên: P1 — chặn phần tích hợp/nghiệm thu dữ liệu tương ứng; không phải P0 toàn hệ thống.

## §1 — Phạm vi và quyết định

**Phiên bản:** v1 · **Ngày dự kiến gửi:** 28/10/2026 · **Mốc FE:** Session326. Owner FE/BE do PO phân công, chưa có tên trong phiên.
Bản đầu của đầu việc này; các handoff gốc vẫn giữ lịch sử.
Nếu gửi ngày khác đổi ngày trước gửi; sửa nội dung sau gửi dùng phiên bản tiếp theo, giữ cùng mạch task.

**Trong phạm vi:** Các phép explosion/requirement mới cho một nguồn BOM và các projection/cost baseline kế thừa chúng; form/import biểu diễn cùng rate.

**Ngoài phạm vi:** Không thay netting nguồn cung, không đổi MAKE lot sizing/multi-level rounding chưa chốt, không xếp lịch mới, không ghi đè run/WO snapshot lịch sử.

**[PO đã chốt trực tiếp ngày28/10/2026]** Hao hụt là phần dự phòng vật tư cộng thêm: lượng cơ bản × (1+scrapRate). Dùng nhãn nghiệp vụ “Bù hao hụt (%)”. Áp dụng cùng nghĩa giữa BOM, MRP và WO; không dùng chia(1−rate).

**Căn cứ:** [BE báo cáo] GĐ8Q25 đã nhân1+rate, HALF_UP6 theo từng dòng; FE/mock trước đây chia1−rate và gộp Item trước ceiling6. [Chưa kiểm] Chưa đủ GĐ6v2/OpenAPI để chứng minh toàn bộ đường BE đồng nhất.

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
| R01 | MRP và WO mới dùng cùng requirement theo rate cộng thêm từ đúng phiên bản BOM, không chia1−rate. | POST /api/v1/planning-runs; POST /api/v1/plants/{plantId}/work-orders; MAKE conversion theo mapping BE | Có—consistency |
| R02 | BOM theo lô quy đổi cơ bản theo outputQuantity rồi áp rate từng dòng; giữ componentLineId, không tự gộp mất trace. | GET /api/v1/boms/{id}; WO component DTO | Có—source |
| R03 | Baseline cost dùng requiredQuantity server đã lưu × giá chuẩn tại RELEASE, không áp rate lần thứ hai. | POST /api/v1/work-orders/{id}/release; GET /api/v1/work-orders/{id}/cost-baseline | Có—cost |
| R04 | Đổi rate/công thức chỉ áp dụng tạo/run mới; run và WO đã chụp giữ số cũ, báo rõ version/mốc rollout. | Planning run/WO/source snapshots | Có—history |
| R05 | Form/API/import dùng0<=rate<1 và scale đã chốt; quá precision bị từ chối trước ghi, không tự làm tròn recipe. | BOM draft và kind=bom import hiện hành | Có—validation |

### Công thức / fixture đề xuất

Nếu DTO lưu componentQuantity theo lô: base = plannedQty/outputQuantity × componentQuantity.
Nếu DTO lưu quantityPer per-unit: base = plannedQty × quantityPer.
Chỉ chọn một cách quy đổi đúng schema, **không chia outputQuantity thêm lần nữa** khi quantityPer đã per-unit. RequiredLine = round6_HALF_UP(base×(1+scrapRate)) là đề xuất giữ rule BE GĐ8 hiện hành, xác nhận ở Q1. MRP có thể cộng nhu cầu theo Item nhưng phải bảo toàn tổng các dòng nguồn đã tính; không thay ràng buộc batch rounding/MAKE của GĐ6 bằng ví dụ này.

Fixture: BOM output10 PCS cần20 KG, rate0.1; WO10 PCS →base20, required22; WO5 PCS →base10, required11. MRP cùng snapshot/no lot-sizing/no safety/no stock phải hiện gross22 hoặc11 tương ứng. Giá chuẩn1.000 VND/KG → planned material cost22.000 cho WO10, không thành24.200 vì nhân hao hụt lần hai. Rate0 →required20.
Case rounding phân biệt: two different component lines of same Item, base1.000001 mỗi dòng, rate0.5 →mỗi dòng1.500002 (HALF_UP6), tổng3.000004. Đề xuất tổng được lấy từ dòng authoritative; nếu BE GĐ6 dùng thứ tự rounding khác phải trả đối chiếu để chốt trước rollout, không tự đổi master/snapshot.

### Contract / quyền / nghiệm thu

Không xin endpoint mới. BE trả mapping từng đường hiện có và success fields requiredQuantity/grossRequirementQuantity/standard planned material của đúng source; giữ permission MRP_RUN/MRP_READ, BOM_READ/MANAGE cấp Company, WO_MANAGE và current scope hiện hành. Không trả cost cho MRP-only. Decimal wire giữ contract hiện có với FE parser lossless sau duyệt.
Nếu field bổ sung cần để giải thích source thì BE đề xuất contract riêng; không bắt client gửi tổng requirement. Server authoritative và snapshot cũ không bị sửa bằng request client. Chờ kiểm BOM→MRP→MAKE/manual WO→RELEASE cùng fixture có scrap>0, fractional, duplicate Item source lines, input lỗi/permission/Plant, read-back/persistence và historical unchanged.

## §4 — Hợp đồng lỗi

Tái dùng mã BE hiện có khi đúng nghĩa. Mã/mapping mới là đề xuất và cần BE trả bảng tương đương nếu khác; không bắt FE parse message.

| Tình huống | HTTP | code / field | FE hiển thị/xử lý |
|---|---|---|---|
| Rate âm hoặc>=1/quá scale | 400 | VALIDATION_ERROR theo field | Giữ input, báo Bù hao hụt |
| Import rate sai | 201 batch INVALID | Issue theo contract batch; commit bị STATE_CONFLICT | Không tạo một phần |
| Snapshot/version cũ lúc ghi | 409 | CONCURRENT_MODIFICATION hoặc source-stale code GĐ6 xác nhận | Refetch và xác nhận lại |
| Không quyền | 403 | PERMISSION_DENIED | Không đọc source ngoài scope |

Giữ envelope code/message và result khi có dữ liệu, errors[] khi có field error. Không ép result:null cho mọi lỗi; import409 IMPORT_REVALIDATION_FAILED có result=batch INVALID theo contract batch. Missing object do redaction khác field unknown/null; không suy số0.
Giữ X-Trace-Id khi có, che Authorization/mật khẩu/token. Validation/business/stale không auto retry như network; giữ input/source context, version server là authoritative. Retry cùng intent dùng key cũ khi endpoint hỗ trợ; intent thay đổi dùng key theo fingerprint policy, không giả master endpoints có replay.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Không hỏi lại PO có chốt ba chính sách hay chưa — PO đã xác nhận ngày28/10. BE xác nhận mapping/triển khai/bằng chứng; quyết định mới ngoài ba chính sách vẫn phải nêu riêng. Tất cả Q dưới đây chưa có phản hồi cho bản này.

1. **Q1:** Giữ HALF_UP6 từng dòng rồi cộng tổng là rule nhất quán cho các đường mới được không? — FE nghiêng về: Có để giữ cách GĐ8 đã triển khai, cần BE đối chiếu GĐ6 — **CHẶN rounding acceptance**.
2. **Q2:** Baseline có thể kế thừa requiredQuantity đã có mà không áp scrap lại không? — FE nghiêng về: Có để tránh double allowance — **CHẶN planned cost**.
3. **Q3:** BE có thể công bố mapping source/rule cho MRP và conversion hiện hành trước khi FE đổi mock không? — FE nghiêng về: Có, không đoán theo một ví dụ scrap0 — **CHẶN adapter consistency**.

## §6 — FE đã xử lý và phần FE tự đảm nhiệm

Quyết định công thức đã chốt, nhưng FE chưa code: sẽ sửa mô hình/mock/label sau phạm vi triển khai được duyệt. Task này giao BE bảo đảm phía server nhất quán, không nhờ BE sửa FE.

Phiên326 chỉ tạo tài liệu và ghi quyết định; chưa sửa ứng dụng, parser, dependency, API flag, dữ liệu server hoặc chạy test/build/browser. FE sẽ tự mapping DTO, label, quyền UI, error/field, input preservation và test sau khi được duyệt code; không giao BE sửa FE. Nhận báo cáo BE không tự đóng acceptance FE hoặc GĐ12.

## §7 — Tham chiếu

- BE_RESPONSE_GD_08_2026-10-19.md — Q25 công thức hiện hành BE.
- FE_HANDOFF_GD_08_2026-10-14.md — requirement source/rounding cũ cần đối soát.
- FE_HANDOFF_GD_06_2026-10-14.md — multi-level/MAKE rounding vẫn độc lập.
- BE_RESPONSE_GD_11_2026-10-27.md — baseline planned material.

Chỉ ghi tên file, không phụ thuộc repo/đường dẫn máy FE. Nội dung task đã tự chứa quyết định, phạm vi và tiêu chí; nếu reference đang thiếu thì BE cung cấp theo contract task, không coi file đó đã được FE đọc.

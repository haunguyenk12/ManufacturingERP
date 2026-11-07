# Tách số đang chờ QC và bị loại cho hàng không theo lô

## §0 — Kết luận trong 3 dòng

- Vấn đề: Rejected non-tracked đang cộng trong qualityHoldQuantity, nên nhãn chờ kiểm không khớp pending0 trên receipt.
- Ảnh hưởng: Aggregate tồn kho và đối chiếu receipt/QC trong FE/dev-demo; tổng OnHand/eligible có thể đúng nhưng phân loại còn thiếu.
- Mức ưu tiên: P2 — thiếu thông tin/phân loại hoặc giới hạn phạm vi, còn có phần nghiệp vụ dùng được.

## §1 — Phạm vi

**Bản:** v1 · **Ngày dự kiến gửi:** 20/10/2026 · **FE:** mốc Session324, nối FE/mock GĐ1–11 và review phản hồi Session323. Đã lưu cục bộ; chưa tự gửi. Nếu gửi ngày khác đổi ngày tên/file; sửa sau gửi dùng _v2 và giữ cùng mạch task. Owner FE/BE do PO phân công, chưa có tên trong phiên này.

**Trong phạm vi:** Read-only aggregate quantities phân loại pending/rejected non-tracked theo Item+Warehouse và current quality source.

**Ngoài phạm vi:** Không đảo QC, không thay tổng quantity/value, không tạo fake lots, không trả hàng loại thành AVAILABLE; không đổi nghĩa field cũ âm thầm.

**Căn cứ và trạng thái:** [BE báo cáo] GĐ7v3 §1 mục2/§6: nhận25, accept20/reject5 thì OnHand25/qualityHold5/available20; không có cột rejected riêng cho non-tracked. Không phải FE đã gọi server tái hiện.

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

## §3 — Đầu ra mong đợi — Cần BE triển khai hoặc cung cấp mapping đã có

**[Đề xuất]** R01… là đầu ra kiểm được. Nếu phần đã tồn tại ở bản mới hơn phản hồi, ghi rõ mapping thay vì coi file này buộc làm lại.

| # | Đầu ra đúng/sai kiểm được | Endpoint/điểm xử lý | Chặn FE? |
|---|---|---|---|
| R01 | Aggregate có số pending/rejected non-tracked authoritative độc lập pagination queue. | GET /api/v1/inventory/balances/aggregate | Có—phân loại |
| R02 | Các field mới additive giữ qualityHoldQuantity legacy, công bố rõ field cũ gồm pending+rejected thuộc receipt và phần hold khác nếu có. | Aggregate DTO | Có—compatibility |
| R03 | Sau nhận25/accept20/reject5 trả pending0/rejected5, available20/OnHand25; không đếm rejected hai lần. | GET aggregate | Có—totals |
| R04 | Nếu non-tracked có nguồn hold legacy không phân loại được, trả unknown breakdown rõ ràng, không ép tất cả thành pending0 hoặc rejected0. | GET aggregate; nguồn legacy | Có—legacy |
| R05 | Projection không giá đọc bằng INVENTORY_READ scoped, phản ánh QC mutation canonical sau reload. | GET aggregate / inventory read | Có—auth |

### Contract đề xuất

GET /api/v1/inventory/balances/aggregate giữ filter/search/page/sort hiện hành, một row Item+Warehouse; các số là tổng của row, không phải tổng trang queue. PERM_INVENTORY_READ tại scope kho/Plant; không yêu cầu quyền tài chính. Không endpoint hoặc command mới.

| Field additive đề xuất | Kiểu | Nullable | Nguồn/công thức | Dùng ở đâu UI |
|---|---|---|---|---|
| pendingPurchaseQcQuantity | decimal scale6 | Có khi legacy không xác định | Σ pending của POSTED receipt non-tracked đúng row | Đang chờ QC mua |
| rejectedPurchaseQcQuantity | decimal scale6 | Có khi legacy không xác định | Σ rejected chưa được disposition sau này của bucket đúng row | Đã loại |
| unclassifiedQualityHoldQuantity | decimal scale6 | Có khi không đủ nguồn | Hold không phân loại được; không clamp để che sai | Cần đối soát |
| qualityBreakdownKnown | boolean | Không | Có đủ source cho breakdown | Trạng thái dữ liệu |
| qualityHoldQuantity (giữ legacy) | decimal scale6 | Theo contract hiện hành | Giữ nghĩa cũ, không tự thay bằng pending | Tương thích client cũ |

Với fixture mới không có hold khác: legacyQualityHold = pendingPurchaseQc + rejectedPurchaseQc. Các tổng hiển thị mới phải dùng disjoint groups, không cộng thêm rejected mới vào qualityHold cũ rồi tính OnHand lần nữa. heldLotQuantity/rejectedQuantity tracked hiện hành không bị tự sửa semantics trong task này. Nếu thêm grand-total nhóm mới, BE phải công bố công thức để FE không trộn các nhóm chồng nhau.

### Fixture / nghiệm thu đề xuất

Non-tracked nhận25, QC đợt1 accept10/reject5 → pending10, rejected5, available10, qualityHold15, OnHand25. Đợt2 accept10 → pending0, rejected5, available20, qualityHold5. Tổng quantity/value không đổi vì QC. Với reservation trên accepted, available giảm theo reserved nhưng pending/rejected không đổi. Tracked parent/child vẫn theo R11 GĐ7, không tạo lot cho non-tracked.
Chờ live: partial/repeated QC, nhiều receipt/Item/kho/Plant, queue page20 trong khi source>20, legacy không rõ, quyền inventory-only không lộ giá. Migration chỉ tạo breakdown khi có proof nguồn; không sửa quyết định QC cũ.

## §4 — Hợp đồng lỗi mong đợi

[Đề xuất/mapping] Tái dùng mã BE khi phù hợp; mã mới ghi rõ đề xuất. BE trả bảng tương đương nếu khác, không bắt FE parse message:

| Tình huống | HTTP | code / contract | FE hiển thị/xử lý |
|---|---|---|---|
| Query sai | 400 | VALIDATION_ERROR | Giữ filter |
| Không đủ quyền/scope | 403 | PERMISSION_DENIED | Không trả breakdown kho khác |
| Legacy thiếu nguồn trong read | 200 | SUCCESS, qualityBreakdownKnown=false (đề xuất) | Hiển thị chưa phân loại; không đổi unknown thành0 |

Envelope hiện hành được giữ: code/message, result khi có dữ liệu và errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Riêng import revalidation409 có result=batch INVALID theo GĐ3. X-Trace-Id giữ cho chẩn đoán, không đưa Authorization/token vào evidence. Lỗi validation/business/stale không tự retry như lỗi mạng; giữ input và source context, chỉ retry cùng intent theo contract idempotency của endpoint. Không áp dụng replay chung cho master endpoints đang bỏ qua header.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; Q có thể cần PO chốt policy. Owner trả lời BE, PO duyệt nghiệp vụ nếu có thay đổi; tất cả hiện **chưa trả lời**.

1. **Q1:** Có thể bổ sung field additive và giữ nguyên nghĩa qualityHoldQuantity cho client cũ không? — FE nghiêng về: Có để tránh breaking change âm thầm — **CHẶN DTO mapping**.
2. **Q2:** Legacy thiếu nguồn có được trả breakdown unknown thay vì suy rejected0 không? — FE nghiêng về: Có để không làm sai dữ liệu — **CHẶN legacy acceptance**.

## §6 — FE đã tự xử lý

FE chịu trách nhiệm đổi nhãn/tổng theo mapping, không suy từ một trang queue. Đây là xin projection server còn thiếu, không nhờ BE sửa CSS/nhãn FE. Chưa sửa ứng dụng trong phiên324.

Phần còn lại FE tự đảm nhiệm khi được duyệt: DTO/adapter, hiển thị label/error, giữ input, quyền UI, cache invalidation và test phù hợp. Không xin BE sửa giao diện hoặc chứng minh pass FE thay FE. Không đổi flag/dependency, không tạo dữ liệu server trong phiên tài liệu; live acceptance vẫn chờ môi trường và contract được xác nhận.

## §7 — Tham chiếu

- BE_RESPONSE_GD_07_2026-10-18_v3.md — §1/§6/§8 hạn chế rejected non-tracked.
- BE_RESPONSE_GD_04_2026-10-15.md — heldLot/qualityHold aggregate semantics.
- FE_HANDOFF_GD_07_2026-10-14.md — quality/quantity conservation.

Chỉ ghi tên file vì hai bên không dùng chung repo. File này tự chứa vấn đề, tiêu chí và ví dụ cần cho task; không yêu cầu BE mở source FE. Các file phản hồi được ghi theo đúng tên nhận, không tự sửa ngày/phiên bản của BE.

# Bảo vệ nguồn hàng mua đang QC trước mutation kho chung

## §0 — Kết luận trong 3 dòng

- Vấn đề: GĐ7v3 xác nhận manual adjust trên lô HOLD chưa được chặn, trong khi R13/A20 yêu cầu không bypass QC ownership.
- Ảnh hưởng: Nhận hàng/QC mua, kiểm đếm và kho trong FE/dev-demo; chưa có bằng chứng FE rằng adjust đã biến hàng HOLD thành AVAILABLE.
- Mức ưu tiên: P1 — chặn một phần tích hợp/nghiệm thu tương ứng, không phải toàn hệ thống ngừng chạy.

## §1 — Phạm vi

**Bản:** v1 · **Ngày dự kiến gửi:** 20/10/2026 · **FE:** mốc Session324, nối FE/mock GĐ1–11 và review phản hồi Session323. Đã lưu cục bộ; chưa tự gửi. Nếu gửi ngày khác đổi ngày tên/file; sửa sau gửi dùng _v2 và giữ cùng mạch task. Owner FE/BE do PO phân công, chưa có tên trong phiên này.

**Trong phạm vi:** Generic adjust/count/transfer/reserve/issue/status tác động lot/bucket do Goods Receipt/QC sở hữu; giữ source conservation cho tracked/non-tracked.

**Ngoài phạm vi:** Không sửa/đảo quyết định QC đã ghi; không thêm returns/refunds/write-off hoặc quyền ADMIN bypass; không cấm mọi HOLD không thuộc receipt nếu workflow đó hợp lệ.

**Căn cứ và trạng thái:** [BE báo cáo] BE_RESPONSE_GD_07_2026-10-18_v3.md §6 mục2/§8 nêu điều chỉnh tồn HOLD chưa chặn; status và cancel GR sau QC đã chặn. [Đối chiếu tài liệu] FE_HANDOFF_GD_07_2026-10-14.md R13/A20 bao gồm generic count/status và quarantine owner.

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
| R01 | Generic adjust/count không tăng/giảm quantity của lot/bucket còn được receipt/quarantine sở hữu mà không qua workflow sửa nguồn được chốt. | POST /api/v1/inventory/adjust; POST /api/v1/inventory/counts | Có—R13/A20 |
| R02 | Generic status/issue/reserve/transfer không làm pending/rejected mua thành usable hoặc rút phần không eligible. | POST /api/v1/inventory/lots/{id}/status; issue/transfers; WO reserve/material-issues | Có—quarantine |
| R03 | Non-tracked giữ bucket lineage và không cho điều chỉnh tổng kho làm OnHand thấp hơn phần protected mà không đối soát owner. | Inventory mutation và GR qualityBucketId | Có—non-tracked |
| R04 | QC là nơi duy nhất chuyển pending thành accepted/rejected; post cùng version chỉ có một người thắng và các ledger atomic. | POST /api/v1/goods-receipts/{id}/lines/{lineId}/qc | Có—conservation |
| R05 | Phiếu đã có QC decision tiếp tục không đảo nhập qua cancel; guard dùng record server, không tin cờ client. | POST /api/v1/goods-receipts/{id}/cancel | Có—existing guard |

### Hành vi đề xuất

Tái sử dụng endpoint/body và permission hiện hành; không thêm quyền cho ADMIN theo tên role. Generic kho dùng PERM_INVENTORY_MOVE; QC dùng PERM_PURCHASE_QC_MANAGE trên kho receipt; scope các record liên quan phải cùng Company/Plant/kho được phép. Command lấy owner từ source ledger, kiểm dưới lock và trước ghi/replay, toàn intent rollback nếu có một dòng bị chặn. Chặn nghiệp vụ không thay thế kiểm quyền.

### Fixture đề xuất / công thức (chưa chạy)

Tracked GR nhận4: QC accept2.5/reject0.5 → parent HOLD1, child AVAILABLE2.5, child REJECTED0.5; tổng4. Count parent từ1 xuống0 hoặc adjust+1 trên parent phải bị chặn nếu không có correction nguồn; không sửa receipt/QC và không thêm movement khi chặn. Reserve/issue/transfer parent1 hoặc rejected0.5 đều không được; child accepted2.5 chỉ được dùng theo stock entitlement.
Non-tracked receipt25, accept20/reject5: protected rejected5, pending0, eligible tối đa20 trước các reservation khác. Count tổng xuống4 không thể giữ nguyên rejected5; guard phải chặn hoặc đi qua workflow correction nguồn rõ ràng, không clamp âm. Một vị thế còn stock free khác không tự làm owner quantity có thể chỉnh tùy ý.
Không yêu cầu sửa quantity về0, không kết luận thành công của adjust chứng minh hàng đã usable; BE cung cấp test invariant đúng nguồn và trường hợp ngoại lệ nếu có.
Chờ live: tracked/nontracked, còn pending/đã loại/accepted free, generic delta và absolute count, row-last rollback, concurrent QC+adjust, quyền bị thu hồi,2 Plant; existing status/cancel guard không hồi quy.

## §4 — Hợp đồng lỗi mong đợi

[Đề xuất/mapping] Tái dùng mã BE khi phù hợp; mã mới ghi rõ đề xuất. BE trả bảng tương đương nếu khác, không bắt FE parse message:

| Tình huống | HTTP | code / contract | FE hiển thị/xử lý |
|---|---|---|---|
| Mutation generic vi phạm quarantine owner | 409 | STATE_CONFLICT (đề xuất; không yêu cầu parse message) | Nêu phải xử lý qua nguồn nhận/QC; giữ input |
| Xuất/reserve/status lot pending/rejected | 409 | LOT_NOT_ELIGIBLE | Dẫn đúng màn QC nếu còn pending |
| Cancel GR có QC | 409 | STATE_CONFLICT | Chặn đảo nhập |
| QC version cũ | 409 | CONCURRENT_MODIFICATION | Refetch, không tự retry QC |
| Không quyền/scope | 403 | PERMISSION_DENIED | Không lộ owner ngoài phạm vi |

Envelope hiện hành được giữ: code/message, result khi có dữ liệu và errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Riêng import revalidation409 có result=batch INVALID theo GĐ3. X-Trace-Id giữ cho chẩn đoán, không đưa Authorization/token vào evidence. Lỗi validation/business/stale không tự retry như lỗi mạng; giữ input và source context, chỉ retry cùng intent theo contract idempotency của endpoint. Không áp dụng replay chung cho master endpoints đang bỏ qua header.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; Q có thể cần PO chốt policy. Owner trả lời BE, PO duyệt nghiệp vụ nếu có thay đổi; tất cả hiện **chưa trả lời**.

1. **Q1:** BE có thể kiểm R01–R03 trong canonical inventory service thay vì chỉ ẩn nút FE không? — FE nghiêng về: Có để request trực tiếp cũng không bypass — **CHẶN live R13/A20**.
2. **Q2:** Correction quantity thuộc receipt owner có được dành cho workflow riêng, generic adjust/count bị chặn trước mắt không? — FE nghiêng về: Có, tránh tạo exception không audit — **CHẶN mutation policy**.
3. **Q3:** Mã STATE_CONFLICT có được dùng nhất quán cho owner guard không? — FE nghiêng về: Có nếu chưa có code ổn định tương đương — **CHẶN error mapping**.

## §6 — FE đã tự xử lý

FE giữ QC-only không giá và khóa mutation không hợp lệ trong mock. Chưa thêm generic API guard ở FE; ẩn nút sau tích hợp chỉ phòng vệ UX, không chứng minh server an toàn. Lịch sử source không bị sửa trong phiên tài liệu.

Phần còn lại FE tự đảm nhiệm khi được duyệt: DTO/adapter, hiển thị label/error, giữ input, quyền UI, cache invalidation và test phù hợp. Không xin BE sửa giao diện hoặc chứng minh pass FE thay FE. Không đổi flag/dependency, không tạo dữ liệu server trong phiên tài liệu; live acceptance vẫn chờ môi trường và contract được xác nhận.

## §7 — Tham chiếu

- BE_RESPONSE_GD_07_2026-10-18_v3.md — §6/§8 thừa nhận adjust HOLD chưa chặn.
- FE_HANDOFF_GD_07_2026-10-14.md — R13/A20 là yêu cầu gốc, không mở scope QC reversal.
- BE_RESPONSE_GD_04_2026-10-15.md — adjust delta/count tuyệt đối và permission kho.

Chỉ ghi tên file vì hai bên không dùng chung repo. File này tự chứa vấn đề, tiêu chí và ví dụ cần cho task; không yêu cầu BE mở source FE. Các file phản hồi được ghi theo đúng tên nhận, không tự sửa ngày/phiên bản của BE.

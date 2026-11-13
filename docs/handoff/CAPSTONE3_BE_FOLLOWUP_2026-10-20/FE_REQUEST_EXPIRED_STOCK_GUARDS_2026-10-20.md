# Chặn hàng hết hạn xuyên MRP, giữ chỗ và xuất vật tư

## §0 — Kết luận trong 3 dòng

- Vấn đề: GĐ4 mới chặn hết hạn ở kho thủ công; tài liệu chưa chứng minh reserve/issue WO/MRP cùng kiểm điều kiện này.
- Ảnh hưởng: Kế hoạch vật tư, reserve/release và xuất vật tư trên FE/dev-demo; không chốt live eligibility bằng riêng test issue thủ công.
- Mức ưu tiên: P1 — chặn một phần tích hợp/nghiệm thu tương ứng, không phải toàn hệ thống ngừng chạy.

## §1 — Phạm vi

**Bản:** v1 · **Ngày dự kiến gửi:** 20/10/2026 · **FE:** mốc Session324, nối FE/mock GĐ1–11 và review phản hồi Session323. Đã lưu cục bộ; chưa tự gửi. Nếu gửi ngày khác đổi ngày tên/file; sửa sau gửi dùng _v2 và giữ cùng mạch task. Owner FE/BE do PO phân công, chưa có tên trong phiên này.

**Trong phạm vi:** Eligibility server cho lot AVAILABLE có expiresAt; auto/manual reserve, release readiness, issue trong/vượt định mức và approve sau chờ, MRP eligible supply. Giữ expiry theo giờ server.

**Ngoài phạm vi:** Không sửa hạn lô, không tự đổi status toàn cục hoặc tự hủy reservation; không write-off/reconcile quantity cũ, không thay policy mua hàng hoặc lead time.

**Căn cứ và trạng thái:** [BE báo cáo] BE_RESPONSE_GD_04_2026-10-15.md §1/§8 nói reserve, issue WO và hoạch định chưa xét hạn trong lượt đó. [BE báo cáo] GĐ7v3 chặn QC accept lot hết hạn. [Suy luận] Lot có thể hết hạn sau khi đã QC đạt nên guard QC không thay guard issue.

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
| R01 | MRP không tính lot expiresAt<=now(server) là AVAILABLE/free stock; OnHand vẫn giữ nguyên. | POST /api/v1/planning-runs và snapshot requirements | Có—netting |
| R02 | Auto/manual reserve không tạo reservation mới trên lot hết hạn, kể cả gọi thẳng API. | POST /api/v1/work-orders/{id}/reserve; POST /api/v1/work-orders/{id}/material-reservations | Có—reserve |
| R03 | Issue và approve kiểm lại hạn tại lúc ghi dưới lock; lot hết hạn trong thời gian chờ duyệt bị từ chối, không trừ kho/WIP. | POST /api/v1/work-orders/{id}/material-issues; POST /api/v1/work-orders/{id}/material-issues/{issueId}/approve | Có—issue |
| R04 | Readiness/release không coi reservation trên lot đã hết hạn là đủ100%; báo blocker, không âm thầm đổi lot đã chọn. | POST /api/v1/work-orders/{id}/release và readiness tương đương | Có—release |
| R05 | Current authorization trước replay; cùng intent hợp lệ post rồi retry chỉ đọc receipt cũ, không issue lần hai; expiry mới không làm post lại receipt đã có. | Các command trên | Có—retry |

### Quy tắc và contract

Không yêu cầu endpoint mới hoặc đổi body legacy; BE áp dụng guard ở service canonical, bao gồm mọi alias/bulk mutation liên quan. Giữ permission hiện hành: reservation PERM_MATERIAL_RESERVATION_MANAGE, release PERM_WORK_ORDER_MANAGE, issue PERM_MATERIAL_ISSUE_MANAGE, approval quyền phê duyệt hiện có; MRP giữ quyền hoạch định theo runtime/OpenAPI. Scope kiểm WO/lot/warehouse cùng Company/Plant hiện hành, server principal, không cần quyền ngoài scope để xác định expired.

Eligible = ACTIVE refs + lot AVAILABLE + (expiresAt null hoặc expiresAt>giờ commit server) + stock entitlement hợp lệ. HOLD/REJECTED vẫn không eligible dù chưa hết hạn. expiresAt là instant; task này không đổi sang cuối ngày. Lot null expiry giữ policy hiện hành, không tự bắt buộc toàn Item.

### Fixture và nghiệm thu đề xuất (chưa gọi BE)

Ngày giờ T do test server kiểm soát, không lấy đồng hồ trình duyệt. Item cần10 KG, có lot A10 KG AVAILABLE/hết hạnT và lot B6 KG AVAILABLE/hết hạnT+1 ngày, safety0/no supply khác: OnHand16, eligible6, shortage4 ở T; MRP không cấp10 từA. Auto reserve tối đa6 nếu contract cho partial, nếu all-or-nothing thì trả shortage không post partial; BE chốt phản hồi hiện hành, không đổi sang đủ10 giả.
Case reserveA trước T, tớiT mới issue/release/approve: block, quantity/WIP/movement không đổi; không tự release reservation, user xử lý theo workflow. Case posted issue trướcT rồi retry cùng key sauT với quyền còn hiệu lực: cùng receipt, không issue lần hai. Mọi replay sau thu hồi quyền phải bị từ chối.
Chờ live: expiry bằngT/quáT/chưaT/null, HOLD/REJECTED,2 Plant, thiếu quyền, expiry giữa request và lock/approval, rollback multi-line nếu dòng cuối expired. Test IT kiểm giờ biên được ghi riêng với smoke HTTP, không dựng chứng cứ bằng SQL production.

## §4 — Hợp đồng lỗi mong đợi

[Đề xuất/mapping] Tái dùng mã BE khi phù hợp; mã mới ghi rõ đề xuất. BE trả bảng tương đương nếu khác, không bắt FE parse message:

| Tình huống | HTTP | code / contract | FE hiển thị/xử lý |
|---|---|---|---|
| Lot hết hạn khi reserve/issue/approve | 409 | LOT_NOT_ELIGIBLE (tái dùng mã kho, cần BE xác nhận) | Lô đã hết hạn, chọn nguồn khác; giữ input |
| Release có reservation không còn eligible | 409 | STATE_CONFLICT hoặc mã readiness hiện hành BE công bố | Hiển thị blocker có nguồn, không auto retry |
| Không đủ eligible quantity | 409 | INSUFFICIENT_AVAILABLE_STOCK | Giữ form, refetch eligible |
| Không quyền | 403 | PERMISSION_DENIED | Không lộ kho khác |

Envelope hiện hành được giữ: code/message, result khi có dữ liệu và errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Riêng import revalidation409 có result=batch INVALID theo GĐ3. X-Trace-Id giữ cho chẩn đoán, không đưa Authorization/token vào evidence. Lỗi validation/business/stale không tự retry như lỗi mạng; giữ input và source context, chỉ retry cùng intent theo contract idempotency của endpoint. Không áp dụng replay chung cho master endpoints đang bỏ qua header.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; Q có thể cần PO chốt policy. Owner trả lời BE, PO duyệt nghiệp vụ nếu có thay đổi; tất cả hiện **chưa trả lời**.

1. **Q1:** Bản sau GĐ4 đã kiểm expiry ở đủ R01–R05 chưa? — FE nghiêng về: Có nếu có mapping/test/build chứng minh, tránh code trùng — **CHẶN live eligibility**.
2. **Q2:** expiresAt đúng bằng giờ server có bị loại khỏi eligible không? — FE nghiêng về: Có, giữ quy ước <= của GĐ4 — **CHẶN boundary acceptance**.
3. **Q3:** Reservation đã hết hạn có giữ lịch sử và yêu cầu xử lý explicit thay vì tự xóa không? — FE nghiêng về: Có để không đổi dữ liệu ngoài intent — **CHẶN stale reservation UX**.

## §6 — FE đã tự xử lý

FE/mock loại HOLD/REJECTED/expired và giữ input. FE sẽ sửa mapping expiry/status theo server khi được duyệt; không dùng browser time làm quyền quyết định post. Không khẳng định đã gọi server để thấy issue expired thành công.

Phần còn lại FE tự đảm nhiệm khi được duyệt: DTO/adapter, hiển thị label/error, giữ input, quyền UI, cache invalidation và test phù hợp. Không xin BE sửa giao diện hoặc chứng minh pass FE thay FE. Không đổi flag/dependency, không tạo dữ liệu server trong phiên tài liệu; live acceptance vẫn chờ môi trường và contract được xác nhận.

## §7 — Tham chiếu

- BE_RESPONSE_GD_04_2026-10-15.md — §1/§8 phạm vi guard hết hạn.
- BE_RESPONSE_GD_07_2026-10-18_v3.md — QC expired không thay issue guard.
- FE_HANDOFF_GD_04_2026-10-12.md — eligibility kho.
- FE_HANDOFF_GD_07_2026-10-14.md — R13/A20 xuyên nghiệp vụ.

Chỉ ghi tên file vì hai bên không dùng chung repo. File này tự chứa vấn đề, tiêu chí và ví dụ cần cho task; không yêu cầu BE mở source FE. Các file phản hồi được ghi theo đúng tên nhận, không tự sửa ngày/phiên bản của BE.

# Đồng nhất công thức capacity giữa xem trước và phát hành WO

## §0 — Kết luận trong 3 dòng

- Vấn đề: GĐ5 báo preview chia capacityUnits nhưng release chưa chia, nên cùng nguồn có thể cho hai lịch khác nhau.
- Ảnh hưởng: Capacity preview, lịch WO mới và cơ sở giờ kế hoạch trên FE/dev-demo; chặn nghiệm thu center capacity lớn hơn1.
- Mức ưu tiên: P1 — chặn một phần tích hợp/nghiệm thu tương ứng, không phải toàn hệ thống ngừng chạy.

## §1 — Phạm vi

**Bản:** v1 · **Ngày dự kiến gửi:** 20/10/2026 · **FE:** mốc Session324, nối FE/mock GĐ1–11 và review phản hồi Session323. Đã lưu cục bộ; chưa tự gửi. Nếu gửi ngày khác đổi ngày tên/file; sửa sau gửi dùng _v2 và giữ cùng mạch task. Owner FE/BE do PO phân công, chưa có tên trong phiên này.

**Trong phạm vi:** Scheduler release mới và preview cùng calendar/routing/capacity snapshot; semantics phút công suất và thời gian hoạt động; bảo toàn lịch WO đã release.

**Ngoài phạm vi:** Không dời WO lịch sử, không xếp lịch song song/optimizer/MES, không đổi thuật toán cộng tải sẵn có hoặc tự thay BOM/routing.

**Căn cứ và trạng thái:** [BE báo cáo] BE_RESPONSE_GD_05_2026-10-16.md §6/§8: preview có chia, release chưa chia. [Chưa kiểm chứng live] Chưa biết GĐ8 hoặc bản sau đã đồng nhất hay chưa.

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
| R01 | Cùng nguồn/version/calendar/start/quantity/capacity cho preview và release mới tạo cùng cửa sổ operation nếu không có thay đổi nguồn. | POST /api/v1/plants/{plantId}/capacity/preview; POST /api/v1/work-orders/{id}/release | Có—schedule |
| R02 | required resource-minutes=setup+quantity×run; active elapsed=required/captured capacityUnits; ngắt giờ nghỉ theo cùng calendar. | Scheduler canonical cho hai đường trên | Có—formula |
| R03 | Source đổi giữa preview và release phải kiểm lại nguồn và trả kết quả/cảnh báo rõ; không dùng preview cũ như lịch được bảo đảm. | Hai đường trên và WO response | Có—stale UX |
| R04 | Giữ snapshot/lịch WO đã release; đổi calendar/capacity không tự dời hoặc tính lại historical active duration. | GET /api/v1/work-orders/{id} và release | Có—history |
| R05 | Snapshot có thông tin đủ giải thích capacity/minutes đã dùng và dependency graph đã capture hoặc giới hạn được BE công bố. | WO operation snapshot DTO | Có—trace |

### Endpoint / trường đề xuất

Không thêm endpoint. Preview giữ body {routingId,quantity,requestedStart,periodStart,periodEnd}; result giữ operations/sourceVersions hiện hành. Release hiện có bodyless: task không tự thêm required body làm gãy client. Nếu cần xác nhận source revision cho release, BE đề xuất extension tương thích và ghi rõ khi nào bắt buộc. Preview không tạo WO/reservation/movement; release chỉ thay trạng thái/lịch/snapshot như workflow hiện hành.

| Field (DTO snapshot; tên là đề xuất) | Kiểu | Nullable | Nguồn/công thức | Dùng ở đâu |
|---|---|---|---|---|
| capturedCapacityUnits | integer >0 | Có với legacy thiếu snapshot | Capacity tại release | Giải thích lịch/giá thành |
| requiredResourceMinutes | decimal exact | Có với legacy | setup+quantity×run | Tải công suất |
| activeElapsedMinutes | decimal exact | Có với legacy | required/capacity, không gồm nghỉ | Lịch/chi phí kế hoạch |
| plannedStartAt / plannedEndAt | RFC3339 instant | Theo trạng thái WO | Scheduler cùng calendar | Lịch đã phát hành |
| predecessorOperationIds | UUID[] | [] nếu không có; legacy thiếu không tự gán graph | Dependency của routing snapshot, không đọc master mới | Trace nguồn |

Phân biệt graph snapshot với xếp lịch song song: giữ dependency không yêu cầu triển khai parallel. Decimal/minutes precision cần BE xác nhận, không tự làm tròn thời gian lịch ở FE.

### Fixture đề xuất (chưa chạy)

Ca08:00–17:00 nghỉ12:00–13:00, capacity2, start08:00, quantity10, setup0/run12 → required120 resource-minutes, elapsed60 active phút, kết thúc09:00. Preview và release mới phải cùng09:00 nếu cùng nguồn; capacity1 → elapsed120, kết thúc10:00.
Một ca bắt đầu11:30/capacity2/required120 →30 phút trước nghỉ+30 phút sau nghỉ, kết thúc13:30. Tải công suất vẫn120, không đổi thành60 khi cộng tải. Nguồn/version bị đổi thì kết quả mới phải phản ánh nguồn mới và không giả cùng lịch preview cũ.
Chờ live: center1/2, ca đêm/giờ nghỉ, kỳ/múi giờ, source thay đổi, quyền thiếu từng thành phần, WO cũ bất biến; kiểm preview không ghi dữ liệu. Quyền preview giữ conjunction CAPACITY_READ+WORK_CENTER_READ+WORK_CALENDAR_READ+SHIFT_READ trên Plant và BOM_READ+ROUTING_READ trên Company; release WORK_ORDER_MANAGE scoped và các rule readiness hiện hành, current principal sau lock.

## §4 — Hợp đồng lỗi mong đợi

[Đề xuất/mapping] Tái dùng mã BE khi phù hợp; mã mới ghi rõ đề xuất. BE trả bảng tương đương nếu khác, không bắt FE parse message:

| Tình huống | HTTP | code / contract | FE hiển thị/xử lý |
|---|---|---|---|
| Source revision cũ nếu thêm expected source guard | 409 | CONCURRENT_MODIFICATION (đề xuất mapping) | Yêu cầu xem lại lịch, giữ input |
| Không xếp được lịch | 409 | SCHEDULING_UNAVAILABLE | Nêu điều kiện chưa đủ |
| Trùng predecessor/successor khi chỉnh lịch | 409 | OPERATION_SEQUENCE_CONFLICT | Không ghi điều chỉnh |
| Thiếu permission/scope | 403 | PERMISSION_DENIED | Không tự lấy source ngoài Plant |

Envelope hiện hành được giữ: code/message, result khi có dữ liệu và errors[] khi có lỗi field; không ép result:null cho mọi lỗi. Riêng import revalidation409 có result=batch INVALID theo GĐ3. X-Trace-Id giữ cho chẩn đoán, không đưa Authorization/token vào evidence. Lỗi validation/business/stale không tự retry như lỗi mạng; giữ input và source context, chỉ retry cùng intent theo contract idempotency của endpoint. Không áp dụng replay chung cho master endpoints đang bỏ qua header.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; Q có thể cần PO chốt policy. Owner trả lời BE, PO duyệt nghiệp vụ nếu có thay đổi; tất cả hiện **chưa trả lời**.

1. **Q1:** Bản BE sau GĐ5 đã dùng chung scheduler chia capacity cho release mới chưa? — FE nghiêng về: Có nếu có mapping/build/test, không sửa trùng — **CHẶN lịch live**.
2. **Q2:** Đổi scheduler có chỉ áp dụng release mới và giữ nguyên WO đã phát hành không? — FE nghiêng về: Có để bảo toàn lịch sử — **CHẶN rollout**.
3. **Q3:** WO mới có capture dependency IDs độc lập với master không? — FE nghiêng về: Có để giải thích snapshot dù chưa parallel — **CHẶN snapshot acceptance**.

## §6 — FE đã tự xử lý

FE/mock có lịch theo capacity và snapshot. API release legacy vẫn bodyless; chưa đổi form/adapter. FE sẽ không tự chia plannedEndAt do BE đã lưu để làm giao diện trông khớp preview.

Phần còn lại FE tự đảm nhiệm khi được duyệt: DTO/adapter, hiển thị label/error, giữ input, quyền UI, cache invalidation và test phù hợp. Không xin BE sửa giao diện hoặc chứng minh pass FE thay FE. Không đổi flag/dependency, không tạo dữ liệu server trong phiên tài liệu; live acceptance vẫn chờ môi trường và contract được xác nhận.

## §7 — Tham chiếu

- BE_RESPONSE_GD_05_2026-10-16.md — §6/§8 nêu khác scheduler.
- FE_HANDOFF_GD_05_2026-10-13.md — preview/lịch/capacity gốc.
- FE_HANDOFF_GD_08_2026-10-14.md — snapshot/release, không mặc định quyết định BE đã đóng.
- FE_HANDOFF_GD_11_2026-10-15.md — active duration kế hoạch liên quan.

Chỉ ghi tên file vì hai bên không dùng chung repo. File này tự chứa vấn đề, tiêu chí và ví dụ cần cho task; không yêu cầu BE mở source FE. Các file phản hồi được ghi theo đúng tên nhận, không tự sửa ngày/phiên bản của BE.

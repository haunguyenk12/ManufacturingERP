# Giai đoạn 5 — Bàn giao Backend: Định mức, quy trình và công suất

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock giai đoạn 5 đã hoàn thành; cần BE thống nhất và cung cấp dữ liệu/command BOM, nhập BOM, quy trình, trung tâm, lịch/ca và công suất theo mô tả bên dưới.
- Ảnh hưởng: người thiết lập sản xuất và lập kế hoạch dùng được bản demo; các module mới chưa tích hợp API, BOM API cũ chưa tương đương toàn bộ form mới.
- Mức ưu tiên: P1 — chặn các luồng này ở chế độ API; các màn hình khác và lộ trình FE/mock vẫn tiếp tục được.

**Bản:** v1 · **Ngày dự kiến gửi:** 13/10/2026 · **Mốc FE:** Session 302 · **Bàn giao:** Session 303.
**PO:** yêu cầu “tạo tài liệu cho BE” sau bản FE hoàn thành; ghi nhận chuyển FE/mock giai đoạn 5 sang bàn giao, không phải nghiệm thu server/API.
**FE/BE owner cá nhân:** chưa được chỉ định. **Trạng thái:** đã chuẩn bị để PO gửi; chưa gửi bên ngoài, chưa xác nhận contract hoặc deploy BE.
Ngày trong tên file là ngày gửi: nếu gửi ngày khác, đổi trước lần gửi đầu; sửa sau gửi dùng `_v2`, `_v3` và tiếp tục cùng task.

Nhãn sử dụng xuyên suốt:

- **[Đã kiểm chứng FE]**: đã đọc source hoặc quan sát/test FE/mock. Không chứng minh DB/API BE.
- **[Đề xuất BE]**: chính sách, field, đường dẫn và HTTP bên dưới cần thống nhất; BE có API tương đương thì trả mapping/OpenAPI, không cần viết lại.
- **[Giả định]**: điều kiện chưa kiểm chứng trên BE.
- **[Chưa kiểm chứng live]**: không có request thật giai đoạn 5 trong lần bàn giao này.

## §1 — Phạm vi

### 1.1 Màn hình mới và đã sửa

| Loại | Màn hình/phần | Hành vi nghiệp vụ và dữ liệu cần |
|---|---|---|
| Mới | Nhập BOM Excel | Tải mẫu VI/EN; upload; kiểm tra từng dòng/nhóm BOM; xem trước; xác nhận tạo nguyên batch DRAFT |
| Mới | Kết quả, lịch sử và chi tiết nhập BOM | Trạng thái chờ nhập/có lỗi/đã tạo nháp; dòng lỗi; BOM đã tạo; panel bên phải; lịch sử của chính người dùng trong Company |
| Mới | Xem trước lịch công đoạn | Chọn quy trình, lượng và thời điểm bắt đầu; xem thời gian dự kiến, phút cần, trong kỳ và sau kỳ; không tạo lệnh sản xuất |
| Mới | Panel chi tiết trung tâm/lịch/ca | Thông tin, hiệu lực, lịch tuần, ngày nghỉ, khung làm/nghỉ và tham chiếu; nội dung cuộn trong panel |
| Sửa | Danh sách BOM | Tìm mã/sản phẩm; lọc trạng thái; mặc định phiên bản mới nhất; phân trang 20 |
| Sửa | Tạo/sửa BOM | Form rộng: mã, sản phẩm, lượng đầu ra, ngày hiệu lực, ghi chú, nhiều thành phần/lượng/hao hụt %; UOM từ Item |
| Sửa | Chi tiết/cây/phiên bản BOM | Panel có Thành phần/Cấu trúc/Phiên bản; xem bản con đang dùng; đóng giữ bộ lọc; bản ACTIVE/INACTIVE chỉ đọc, sao chép sang nháp |
| Sửa | Quy trình sản xuất | Danh sách/chi tiết/phiên bản; form rộng, thứ tự công đoạn, trung tâm, chuẩn bị/phút chạy/công đoạn trước; lifecycle |
| Sửa | Trung tâm sản xuất | Danh sách, form giữa màn hình; loại/số đơn vị công suất, lịch, ca; tải theo kỳ chuyển sang Bảng công suất |
| Sửa | Lịch & Ca | Hai danh sách/form giữa màn hình; giờ làm, nghỉ, hiệu lực, lịch tuần/ngày nghỉ; lifecycle ACTIVE/INACTIVE |
| Sửa | Bảng công suất/điều chỉnh | Kỳ hiện tại, lọc trung tâm, timezone; nhu cầu/đã xếp/khả dụng/quá tải; chỉnh công đoạn có lý do/xác nhận |
| Sửa | Sidebar/Nhập dữ liệu | Hiện module theo quyền; thêm loại nhập BOM và nút mở từ danh sách BOM |

Các phần mới nằm trong các màn hình trên, không yêu cầu BE thiết kế UI. Tên người dùng thấy là tên nghiệp vụ VI/EN; mã `PERM_*` chỉ dùng trong contract, không dùng làm nhãn UI.

### 1.2 Trong phạm vi / Ngoài phạm vi / Môi trường

**Trong phạm vi:** BOM đa cấp, version/lifecycle, dữ liệu cây tham chiếu, SAVE nháp nguyên BOM; XLSX BOM mới DRAFT; master Ca → Lịch → Trung tâm; quy trình có phiên bản; công suất theo kỳ và preview tuần tự; điều chỉnh công đoạn kế hoạch đã tồn tại; permission/scope, decimal, transaction, retry, lịch sử/audit và dữ liệu liên kết cho các màn hình §1.1.

**Ngoài phạm vi:** Gantt kéo-thả, optimizer, phân máy/người cụ thể, xử lý va chạm giữa nhiều WO, tự release WO/tạo reservation; thay đổi SO/MRP/WO/execution/receipt trong các giai đoạn sau; sửa snapshot WO đã có; tạo tồn kho/WIP/giá thành khi kích hoạt BOM hoặc preview; MES; serial; import Routing/Ca/Lịch/Trung tâm; CSV/XLS/XLSM; partial import/upsert/ghi đè/kích hoạt BOM bằng file; tự kích hoạt phiên bản tương lai; đa công ty UI; refactor Auth/email/quyền giai đoạn 1; đổi API BOM cũ gây breaking change.

**Môi trường [Đã kiểm chứng FE]:** demo/mock, Next.js production build cục bộ ngày 13/10/2026, Session 302, Desktop/Tablet VI/EN. Dữ liệu master/design và receipt lưu browser; numeric ID fixture không phải UUID BE. Chưa có commit/tag riêng được dùng làm bản bàn giao; không khẳng định code đã push/deploy.

**Existing API:** kiểm tra adapter BOM trong source FE ngày 13/10/2026, không fetch OpenAPI/runtime BE trong phiên này. Lịch sử dự án có tích hợp BOM UUID nhưng không thay thế xác minh bản BE đang deploy hiện tại. Không kết luận BE chưa có module chỉ vì FE chưa có adapter; các endpoint mới tại §3 là đề xuất. API mode hiện giữ BOM adapter cũ; các module mới chưa có contract hiển thị chưa sẵn sàng, không fallback mock.

### 1.3 Luồng để BE dựng lại mà không cần repo FE

1. Người dùng đăng nhập, chọn Company/Plant có scope hợp lệ. Tạo ca có giờ làm/nghỉ và ngày hiệu lực; tạo lịch tuần gán ca/ngày nghỉ; tạo trung tâm gán lịch và tập ca thuộc lịch. Các master này tạo ACTIVE trong mock.
2. Tạo BOM nháp: chọn sản phẩm FG/WIP (source cũ gọi SEMI_FINISHED), lượng đầu ra >0; thêm vật tư RAW/WIP, lượng >0, UOM chuẩn, hao hụt %. Một thành phần không lặp và không chính sản phẩm. Lưu cần xác nhận; lỗi giữ form.
3. Kích hoạt BOM nháp: kiểm nguồn hiện tại, ngày hiệu lực, kiểm vòng đa cấp ở BE; thành công chuyển bản ACTIVE cũ cùng Company/sản phẩm sang INACTIVE trong cùng transaction. Chỉ nháp sửa trực tiếp; ACTIVE/INACTIVE sao chép tạo version nháp mới. Không sửa WO snapshot.
4. Cây BOM mở bản con ACTIVE hiện tại trong Company; thiếu BOM con cần chỉ rõ, không coi cây này là snapshot WO. Bản mock giới hạn 8 cấp hiển thị; giới hạn tree API cần BE xác nhận riêng.
5. Tạo quy trình nháp cho sản phẩm cùng Plant, có các công đoạn thứ tự duy nhất, trung tâm ACTIVE, phút chuẩn bị >=0, phút chạy/đơn vị >0, các công đoạn trước có thứ tự nhỏ hơn. Lưu cả graph; kích hoạt thay ACTIVE cũ cùng Plant/sản phẩm. Copy ACTIVE/INACTIVE tạo nháp; mock chỉ một nháp mở của family khi copy.
6. Bảng công suất mặc định tuần hiện tại. Chọn kỳ, quy trình ACTIVE, lượng >0, bắt đầu trong kỳ; kiểm BOM ACTIVE cùng sản phẩm và nguồn còn hợp lệ. Preview trả lịch qua khung làm việc/nghỉ/ngày nghỉ/qua đêm, chia công suất song song, hiện phần chuyển kỳ. Preview không ghi lịch cam kết.
7. Với công đoạn kế hoạch đã có, chọn bắt đầu mới và lý do >=5 ký tự, xác nhận điều chỉnh. Chỉ đổi công đoạn được chọn; nếu đè lên công đoạn trước/sau cùng WO thì từ chối, không tự dời cả chuỗi. Mock có fixture tháng 7/2026; server không seed tháng cố định cho logic.
8. Nhập BOM: điền file theo §3.4; validate không tạo BOM. Toàn bộ hợp lệ → READY → xác nhận → tạo N BOM DRAFT cùng transaction. Một lỗi → không cho commit. Reload/history mở được dòng và kết quả; retry cùng batch không tạo thêm.
9. Đổi actor/Company/Plant đóng form/panel. BOM vẫn thuộc Company, đổi Plant cùng Company không chia BOM thành hai master. Lịch sử nhập thuộc actor+Company, không thuộc Plant.

### 1.4 Quyền và trạng thái

**[Đã kiểm chứng FE]** mapping dưới đây dùng mã quyền hiện tại. **[Đề xuất BE]** server enforce đúng scope trên mỗi request, direct-ID và retry; không dựa vào role name/nút ẩn.

| Thao tác | Trạng thái trước → sau | Permission | Scope | Kiểm tra / tác động |
|---|---|---|---|---|
| BOM list/detail/tree/version | Không đổi | PERM_BOM_READ | Company của record | Không lấy BOM Company khác |
| BOM create/edit/copy/activate/deactivate | Không có→DRAFT; DRAFT→DRAFT/ACTIVE; ACTIVE→INACTIVE | PERM_BOM_READ + PERM_BOM_MANAGE | Company | Kiểm nguồn, state, version; không động kho |
| Upload/commit BOM | Không có→READY/INVALID; READY→COMMITTED | PERM_BOM_READ + PERM_BOM_MANAGE | Company + owner | Atomic tạo mới DRAFT; actor lấy từ auth |
| History/detail BOM import | Không đổi | PERM_BOM_READ; quyền MANAGE để commit | Company + owner | Mock history service có READ nhưng route import đòi READ+MANAGE; quyền API read cần chốt Q13 |
| Trung tâm | Không có→ACTIVE; ACTIVE↔INACTIVE; cập nhật | PERM_WORK_CENTER_READ; thêm PERM_WORK_CENTER_MANAGE khi ghi | Plant | Ref lịch/ca cùng Plant; chặn ngừng dùng nếu có tải mở |
| Lịch | Không có→ACTIVE; ACTIVE↔INACTIVE; cập nhật | PERM_WORK_CALENDAR_READ; thêm PERM_WORK_CALENDAR_MANAGE khi ghi | Plant | Không phá ref trung tâm ACTIVE |
| Ca | Không có→ACTIVE; ACTIVE↔INACTIVE; cập nhật | PERM_SHIFT_READ; thêm PERM_SHIFT_MANAGE khi ghi | Plant | Không phá lịch/trung tâm ACTIVE |
| Quy trình | Không có→DRAFT; DRAFT→ACTIVE; ACTIVE→INACTIVE; copy→DRAFT | PERM_ROUTING_READ; thêm PERM_ROUTING_MANAGE khi ghi | Plant | Không đổi Plant/sản phẩm/code family khi cập nhật |
| Đọc công suất | Không đổi | PERM_CAPACITY_READ | Plant | Dữ liệu kỳ đúng Plant |
| Xem trước công suất | Không đổi | PERM_CAPACITY_READ + PERM_ROUTING_READ + PERM_WORK_CENTER_READ + PERM_WORK_CALENDAR_READ + PERM_SHIFT_READ; PERM_BOM_READ ở Company | Plant + Company nguồn BOM | Không lộ master không có quyền đọc |
| Điều chỉnh công đoạn | PLANNED/RELEASED giữ nguyên WO status | PERM_CAPACITY_READ + PERM_CAPACITY_MANAGE | Plant của công đoạn/WO | Lý do, thứ tự, current scope; audit; không release WO |

Assignment hết hạn/thu hồi phải làm request sau bị từ chối, kể cả lấy receipt. Authorization kiểm trước idempotent replay. Detail của record/batch trái scope/owner đề xuất NOT_FOUND để không lộ record; thiếu quyền trong scope trả PERMISSION_DENIED. GLOBAL/COMPANY coverage cho Plant cần dùng policy RBAC đã chốt giai đoạn 1, không tự grant từ một permission union. Không có phê duyệt hai người: cùng người có manage được xác nhận/kích hoạt.

## §2 — Bằng chứng tái hiện

**Không có lỗi API/live được báo trong tài liệu này — đây là handoff FE/mock và đề xuất contract, không phải FE_DEFECT.**

| Mảnh bằng chứng live | Trạng thái |
|---|---|
| Request thật method/path/header/body | Không có — không gọi live giai đoạn 5 trong phiên này |
| Response thật HTTP/code/JSON | Không có |
| X-Trace-Id response | Không có; không tự tạo |
| Môi trường BE, bản deploy mới nhất, giờ request | Chưa xác minh; ngày 13/10/2026 là ngày FE test/bàn giao, không phải timestamp request BE |
| ID record thật cùng lượt request | Không có; mã/UUID minh họa §3 không phải record thật |

```text
Mong đợi: FE/mock validate không tạo BOM; commit hợp lệ tạo đúng N bản nháp; kích hoạt thay một ACTIVE; preview không ghi WO/kho.
Thực tế: các luồng FE/mock đã qua test/browser ở Session 302; chưa chứng minh kết quả trên BE.
```

**Bằng chứng FE có sẵn, không cần BE chạy repo để hiểu:** 95 files/452 tests pass; sau tinh chỉnh panel lịch sử 7 files/72 tests pass; TypeScript, scoped lint, VI/EN parity và production build pass. Browser kiểm tạo BOM version nháp/reload, form rộng/gọn, preview 1.000 sản phẩm và chuyển kỳ, file sai UOM bị khóa, file 2 dòng hợp lệ tạo 1 BOM DRAFT, lịch sử/panel/link, đổi Plant, tablet 1024×768 VI/EN. Đây là mốc kiểm tra cũ, không rerun app cho lần tạo tài liệu.

Ảnh: không đính kèm; bố cục và dữ liệu màn hình đã diễn đạt bằng chữ ở §1.1. Test DB/auth/concurrency/live phải thu bằng chứng riêng khi nghiệm thu §3.8.

## §3 — Đầu ra mong đợi — Cần BE triển khai

**Toàn bộ mục này là [Đề xuất BE], trừ bảng 3.2 ghi rõ adapter hiện có.** Nếu server đã có chức năng tương đương, trả contract/mapping thay vì tạo API trùng. Các quyết định §5 phải chốt trước adapter live; chúng không chặn FE/mock giai đoạn tiếp theo.

### 3.1 Tiêu chí triển khai kiểm được

| # | Yêu cầu | Endpoint/nguồn | Chặn FE? |
|---|---|---|---|
| R01 | Read BOM trả lượng đầu ra, UOM, hiệu lực, version, các dòng, trạng thái và khóa concurrency; list hỗ trợ latest/search/page | BOM reads + extension 3.2 | Chặn tích hợp giai đoạn 5; không chặn mock |
| R02 | SAVE một BOM ghi header+tất cả dòng+receipt trong một transaction; lỗi dòng cuối không để lại header/dòng mới | Atomic draft 3.3 | Như trên |
| R03 | Activate chỉ DRAFT hợp lệ/đến ngày hiệu lực; vòng đa cấp trả CIRCULAR_BOM; có đúng một ACTIVE Company/sản phẩm sau hai request đồng thời | BOM activate | Như trên |
| R04 | Copy tạo version nháp mới, không sửa bản nguồn/snapshot WO; version unique được cấp ở server | Copy BOM/Quy trình | Như trên |
| R05 | Parse file gốc theo mẫu 3.4, validate không tạo BOM, INVALID/READY có dòng/lỗi mở lại | Import upload | Như trên |
| R06 | Commit batch chỉ dữ liệu server đã validate; revalidate domain, unique, quyền; toàn bộ mới DRAFT hoặc không tạo gì | Import commit | Như trên |
| R07 | Retry cùng batch trả cùng createdIds, committedAt; quyền hiện tại kiểm trước receipt; history/detail không lộ owner/Company khác | Import read/commit | Như trên |
| R08 | Shift/calendar/center CRUD/lifecycle giữ phạm vi Plant, immutable code/Plant và ref hợp lệ; trả giờ thuần/tên tham chiếu | Master 3.3/3.5 | Như trên |
| R09 | Save Routing nguyên graph, từ chối trùng sequence/công đoạn trước sai/center sai Plant; ACTIVE không sửa; activation thay prior ACTIVE atomic | Routing 3.3/3.5 | Như trên |
| R10 | Preview dùng nguồn ACTIVE hiện tại, timezone Plant, công thức 3.6; trả source versions/window segments; không ghi WO/reservation/stock/committed load | Capacity preview | Như trên |
| R11 | Bảng tải trả đủ phút nhu cầu/in-period/khả dụng/quá tải theo kỳ, không gán toàn tải lịch sử vào mọi tuần | Capacity read/preview | Như trên |
| R12 | Adjustment ghi lịch công đoạn + audit lý do + receipt atomic; giữ WO state và lượng; không tự dời sibling, từ chối chồng trước/sau | Capacity adjustment | Như trên |
| R13 | Mutations chống stale revision và cùng-key-khác-payload; direct IDs kiểm phạm vi; không bypass bằng Idempotency-Key/header Plant | Tất cả command | Chặn server acceptance |
| R14 | Server dùng exact decimal, date/time hợp lệ, giới hạn parser/domain; không tin calculated fields/client actor/status/version | Tất cả schema | Chặn server acceptance |
| R15 | Thay master không đổi BOM/Routing snapshots của WO hiện hữu; preview nguồn current khác với công đoạn WO dùng nguồn snapshot | Production source seam | Chặn compatibility acceptance; WO workflow mới ngoài scope |

### 3.2 BOM API adapter đang có — giữ tương thích

**[Đã kiểm chứng source FE, chưa kiểm chứng deploy]** đường dẫn dưới đây là đầy đủ phía BE gồm `/api/v1`. FE proxy sở hữu `/api`; adapter dùng `/v1`.

| Method + path | Input adapter hiện có | Result adapter đang đọc |
|---|---|---|
| GET /api/v1/companies/{companyId}/boms | parentItemId?, status?, page?, size?, sortBy?, sortDir? | Page<Bom> |
| GET /api/v1/boms/{bomId} | ID | Bom |
| GET /api/v1/items/{itemId}/active-bom | Item ID | Bom |
| GET /api/v1/boms/{bomId}/tree | BOM ID | TreeNode recursive |
| POST /api/v1/companies/{companyId}/boms | parentItemId, revision, description? | Bom |
| PATCH /api/v1/boms/{bomId} | revision, description? | Bom |
| POST /api/v1/boms/{bomId}/lines | componentItemId, lineNo, quantityPer, scrapRate | Bom |
| PATCH /api/v1/bom-lines/{lineId} | Như create line | Bom |
| DELETE /api/v1/bom-lines/{lineId} | Không body | Không có result bắt buộc |
| POST /api/v1/boms/{bomId}/activate | Không body | Bom |
| DELETE /api/v1/boms/{bomId} | Không body | Không có result bắt buộc; soft-deactivate |

Bom cũ: `bomId/companyId/parentItemId` UUID, `parentItemCode/parentItemName/revision/status/createdAt`, optional `description/updatedAt`, `lines[]`. Line: `lineId/bomId/componentItemId`, `lineNo`, code/name, `quantityPer/scrapRate`. Tree: itemId/code/name, optional bomId/revision, hasActiveBom, quantityPer/scrapRate, components[]. Mutations có `Idempotency-Key`; chưa xác minh server acceptance/concurrency trong phiên này.

**Khoảng khác biệt:** API cũ không có `bomCode/outputQuantity/outputUomCode/effectiveFrom/versionNumber/lockVersion`; header+line là nhiều request, không phải một atomic SAVE. `revision` string không mặc nhiên là số version. Không tự gửi field mới vào API cũ hoặc dùng chuỗi tạo line để cam kết atomic.

**Mapping đề xuất:** giữ `parentItemId` cho sản phẩm, `description` cho ghi chú, `revision` làm nhãn phiên bản; thêm code/versionNumber/outputQuantity/effectiveFrom và lượng component theo batch. `quantityPer = componentQuantity / outputQuantity` là projection per-unit để giữ consumer cũ; KHÔNG thay nghĩa `quantityPer` thành lượng batch. Quyết định lưu batch/rounding tại Q1–Q3.

Ví dụ chỉ minh họa mapping: BOM output `10 PCS`, component `25 KG`, scrap `0.05` ⇒ quantityPer trước hao hụt `2.5 KG/PCS`. Phase 5 không chọn công thức gross MRP `1/(1-rate)` hay `1+rate`; giữ rate như dữ liệu, không đổi công thức hoạch định đã chốt chỉ bằng handoff này. Mapping display per-unit/batch phải đồng nhất giữa BOM, tree và WO snapshot.

### 3.3 Endpoint đề xuất và quy ước chung

Mọi path sau là **đề xuất, chưa được gọi**. Danh sách sử dụng server search/page: `page=0`, `size=20` (1–100), sort newest-first + ID tie-break; status filter theo domain. GET direct-ID resolve scope từ aggregate, không đòi FE đoán Plant. Ghi dùng `Idempotency-Key` UUID; cập nhật dùng `expectedVersion` = lockVersion, không dùng versionNumber nghiệp vụ để chống stale.

| Method + path đề xuất | Query/body | HTTP / success result | Phân trang | Quyền/scope |
|---|---|---|---|---|
| GET /api/v1/companies/{companyId}/boms (extension) | query cũ + search?, latestOnly? | 200 Page<BomDesign> | Có | BOM_READ Company |
| PUT /api/v1/companies/{companyId}/bom-drafts | AtomicBomInput; bomId? để cập nhật | 200 BomDesign; mới DRAFT | Không | BOM_READ+MANAGE Company |
| POST /api/v1/boms/{bomId}/copy | bomCode, effectiveFrom?, description? | 201 BomDesign DRAFT | Không | BOM_READ+MANAGE source Company |
| GET /api/v1/boms/{bomId}/versions | page,size | 200 Page<BomDesign summary> cùng Company/sản phẩm | Có | BOM_READ |
| POST /api/v1/boms/{bomId}/activate (extension) | expectedVersion | 200 BomDesign + prior active có thể refetch | Không | BOM_READ+MANAGE |
| POST /api/v1/boms/{bomId}/deactivate | expectedVersion | 200 BomDesign INACTIVE; không đổi DELETE cũ | Không | BOM_READ+MANAGE |
| GET/POST /api/v1/plants/{plantId}/shifts | GET search/status/page/size; POST ShiftInput | 200 Page<Shift> / 201 Shift ACTIVE | GET có | SHIFT_READ; ghi thêm MANAGE |
| GET/PATCH /api/v1/shifts/{id} | GET ID; PATCH ShiftInput bỏ code/plant, thêm expectedVersion | 200 Shift | Không | Shift Plant |
| POST /api/v1/shifts/{id}/status | status ACTIVE/INACTIVE, expectedVersion | 200 Shift | Không | SHIFT_READ+MANAGE |
| GET/POST /api/v1/plants/{plantId}/work-calendars | GET search/status/page/size; POST CalendarInput | 200 Page<Calendar> / 201 Calendar ACTIVE | GET có | WORK_CALENDAR_READ; ghi thêm MANAGE |
| GET/PATCH /api/v1/work-calendars/{id} | GET ID; PATCH CalendarInput bỏ code/plant, thêm expectedVersion | 200 Calendar | Không | Calendar Plant |
| POST /api/v1/work-calendars/{id}/status | status, expectedVersion | 200 Calendar | Không | WORK_CALENDAR_READ+MANAGE |
| GET/POST /api/v1/plants/{plantId}/work-centers | GET search/status/page/size; POST CenterInput | 200 Page<Center> / 201 Center ACTIVE | GET có | WORK_CENTER_READ; ghi thêm MANAGE |
| GET/PATCH /api/v1/work-centers/{id} | GET ID; PATCH CenterInput bỏ code/plant, thêm expectedVersion | 200 Center | Không | Center Plant |
| POST /api/v1/work-centers/{id}/status | status, expectedVersion | 200 Center | Không | WORK_CENTER_READ+MANAGE |
| GET /api/v1/plants/{plantId}/routings | search/status/outputItemId/workCenterId/page/size | 200 Page<Routing summary> | Có | ROUTING_READ Plant |
| PUT /api/v1/plants/{plantId}/routing-drafts | RoutingInput + routingId?/expectedVersion? | 200 Routing DRAFT nguyên graph | Không | ROUTING_READ+MANAGE |
| GET /api/v1/routings/{id} | ID | 200 Routing đủ operations | Không | ROUTING_READ aggregate Plant |
| GET /api/v1/routings/{id}/versions | page,size | 200 Page<Routing summary> cùng family | Có | ROUTING_READ |
| POST /api/v1/routings/{id}/copy | effectiveFrom, name?, description?; code kế thừa | 201 Routing DRAFT mới | Không | ROUTING_READ+MANAGE |
| POST /api/v1/routings/{id}/activate hoặc /deactivate | expectedVersion | 200 Routing ACTIVE/INACTIVE | Không | ROUTING_READ+MANAGE |
| GET /api/v1/plants/{plantId}/capacity | periodStart,periodEnd,workCenterId? | 200 CapacityBoard | Không cho summary; operations page=0/size=20 nếu lớn | CAPACITY_READ |
| POST /api/v1/plants/{plantId}/capacity/preview | routingId,quantity,requestedStart,periodStart,periodEnd | 200 CapacityPreview, không commit | Không; toàn bộ routing ops | Quyền nguồn §1.4 |
| POST /api/v1/capacity/operations/{id}/adjustments | requestedStart,reason,periodStart,periodEnd,expectedVersion | 200 ScheduledOperation + lockVersion mới | Không | CAPACITY_READ+MANAGE aggregate Plant |
| POST /api/v1/data-import/batches | multipart file,kind=bom,companyId; không plantId | 201 BomBatch READY/INVALID | Không | BOM_READ+MANAGE Company |
| POST /api/v1/data-import/batches/{id}/commit | expectedVersion; không rows/file | 200 BomBatch COMMITTED | Không | Owner + BOM_READ+MANAGE |
| GET /api/v1/data-import/batches | companyId,kind=bom,status?,search?,page,size; không plantId | 200 Page<BomBatchSummary> | Có | Owner + BOM_READ; Q13 |
| GET /api/v1/data-import/batches/{id} | ID; resolve Company | 200 BomBatch đủ rows/issues/createdIds | FE chia 20 dòng, <=500 rows | Owner + BOM_READ; Q13 |

Import là mở rộng `kind=bom` trên nền batch đề xuất giai đoạn 3; không coi contract giai đoạn 3 đã chốt. Plant-scope URL attach `X-Plant-Id` nếu policy proxy hiện tại yêu cầu, giá trị phải trùng path. Company BOM/import và aggregate-ID không tự gắn global Plant header. `ownerId/companyId/status/version/calculated fields` do server cấp/resolve, không tin client.

**Envelope đề xuất chung:** success HTTP theo bảng, `code/message/result` required, `errors` optional. Page result có `content/page/size/totalElements/totalPages`. Command result trả object có ID/status/lockVersion; không dùng payload JSON row giả làm response thật. Timestamps ISO8601 UTC `Z`, dates `YYYY-MM-DD`, giờ ca `HH:mm`, timezone IANA ở Plant; các ID server UUID string. Decimal trên wire dùng string không exponent, numeric mock không chuyển thẳng sang UUID/contract live.

### 3.4 BOM/import: field và workbook

Field được đề xuất mới/mapping phải rõ nguồn và nơi hiển thị. `AtomicBomInput` gồm các field input trong bảng và `lines[]`; cập nhật cần bomId+expectedVersion, sản phẩm không đổi. Create không gửi status/versionNumber/owner. Response `BomDesign` kế thừa Bom cũ và các projection dưới đây.

| Field | Kiểu | Nullable | Nguồn / quy tắc đề xuất | Dùng trên UI |
|---|---|---|---|---|
| bomId / companyId / parentItemId | UUID string | Không | Server ID, Company path, Item chuẩn | Chi tiết/scope/sản phẩm |
| bomCode | string 1–100 | Không | Nhập, trim uppercase; unique Company case-insensitive | Mã/search; không thay revision |
| versionNumber | integer >=1 | Không | Cấp tăng theo Company/sản phẩm, unique | Phiên bản/latest |
| revision | string | Không | Nhãn version cũ; mapping chờ Q3 | API cũ/tree |
| outputQuantity | decimal string >0, <10^13, scale<=6 | Không | Nhập; mặc định UI 1 | Lượng đầu ra |
| outputUomId / outputUomCode | UUID/string | Không | UOM canonical của Item; không conversion tự động | Đơn vị đầu ra |
| effectiveFrom | date | Có | Nhập; trống = không trì hoãn hiệu lực, Q4 | Form/activation |
| description | string | Có | Ghi chú; optional create, null để xóa khi cập nhật; giới hạn cần BE công bố | Form/detail |
| status | DRAFT/ACTIVE/INACTIVE | Không | Server lifecycle | Badge/actions |
| lockVersion | integer >=1 | Không | Tăng mọi lần ghi | expectedVersion/stale |
| lines[].lineId | UUID | Không ở response; bỏ khi mới | Server cấp; update giữ ID dòng còn tồn tại | Row identity |
| lines[].lineNo | integer >=1 | Không | Thứ tự 1..N unique trong BOM | Bảng thành phần |
| lines[].componentItemId | UUID | Không | RAW/WIP ACTIVE cùng Company, không output | Chọn thành phần |
| lines[].componentQuantity | decimal string >0, <10^13, scale<=6 | Không | Lượng cho outputQuantity, không phải quantityPer | Form batch |
| lines[].componentUomId / componentUomCode | UUID/string | Không | UOM canonical ACTIVE của component | Đơn vị |
| lines[].quantityPer | decimal string | Không trong compatibility projection | componentQuantity/outputQuantity; scale/rounding Q2 | Consumer API cũ |
| lines[].scrapRate | decimal string, 0<=x<1; đề xuất scale<=8 | Không | UI %/100; % scale6 có thể tạo rate scale8; adapter cũ chỉ scale6, cần Q19 | Hao hụt |
| parent/component Item code/name | string | Không | Master current; snapshot WO dùng bản lưu riêng | Nhãn dropdown/tree |
| tree.components / hasActiveBom / bomId / revision | recursive[] / boolean / UUID? / string? | components không null, BOM ref có thể null | Resolve ACTIVE tại Company, node quantity định nghĩa Q6 | Cấu trúc/thiếu cấu hình |

**[Đã kiểm chứng FE] workbook `omniplant-bom-v1`:** sheet `BOM`, header dòng 1, dữ liệu dòng 2; `_meta` veryHidden có A1=`version`, B1=`omniplant-bom-v1`, A2=`locale`, B2=`vi` hoặc `en`. Sheet `Hướng dẫn`/`Guide`, `Tham chiếu`/`References` giúp đọc, không phải nguồn tin cậy validate. Reference list có Code/Name/Type/UOM ACTIVE lúc tải. File trống, tối đa 5 MiB=5.242.880 byte, 500 dòng có dữ liệu (header/style/hoàn toàn rỗng không tính). Parser mock chặn sheet rowCount>10.000; server cần giới hạn giải nén/cell tài nguyên riêng.

| Cột | Header VI | Header EN | Required / nội dung |
|---|---|---|---|
| A | Mã định mức | BOM code | Có; gom theo mã uppercase; code thực tế cùng nhóm phải nhất quán |
| B | Mã sản phẩm | Product code | Có; code FG/WIP ACTIVE |
| C | Lượng đầu ra | Output quantity | Có; decimal >0, scale<=6 |
| D | Đơn vị đầu ra | Output UOM | Có; đúng UOM canonical, không conversion |
| E | Mã thành phần | Component code | Có; RAW/WIP ACTIVE, không trùng trong nhóm |
| F | Lượng thành phần | Component quantity | Có; decimal >0 cho lượng đầu ra ở C |
| G | Đơn vị thành phần | Component UOM | Có; đúng UOM canonical |
| H | Hao hụt (%) | Scrap (%) | Optional, blank=0; decimal 0<=x<100; 5=5% |
| I | Ghi chú | Notes | Optional; lặp đúng giá trị cho cả nhóm; map description |

Không có cột effectiveFrom: import tạo nháp effectiveFrom=null theo đề xuất Q4; không tự nhận giờ máy FE thành ngày hiệu lực. Không có revision/status/ID/Plant column. Một dòng=1 component; lặp A/B/C/D/I cho nhóm. Trường số tối đa6 decimals; không âm/exponent, không công thức/rich text/hyperlink/boolean/error/object cell. Code/UOM để text; Item lookup code case-insensitive nhưng UOM phải match canonical. Header phải đúng locale/version; không dùng tên cột gần giống. Một output mới chỉ một BOM trong batch; sản phẩm đã có bất kỳ BOM DRAFT/ACTIVE/INACTIVE bị chặn, không tạo version/upsert qua file. Validation phía server không tin cache ref sheet.

Fixture **minh họa**, cần seed riêng trên test BE; không là ID thật. Company C, output FG-DEMO (FINISHED_GOOD, PCS), component RM-DEMO (RAW_MATERIAL, KG), cả hai/UOM ACTIVE. Nhóm `BOM-DEMO-01`, 2 dòng chỉ khi có thêm component khác; ví dụ tối giản một dòng: C=10, F=25, H=5. Commit 1 nhóm→1 BOM DRAFT, 1 line, rate=0.05, output=10, component=25.

| Field batch đề xuất | Kiểu | Nullable | Nguồn / công thức | UI |
|---|---|---|---|---|
| id, companyId, ownerId | UUID | Không | Server/batch/auth, không nhận owner từ multipart | History/detail/commit |
| kind, templateVersion, locale, fileName | string | Không | kind=bom; metadata/file basename đã sanitize | Header/history |
| state | READY/INVALID/COMMITTED | Không | Domain validation/commit | Badge/actions |
| lockVersion | integer >=1 | Không | Server, expectedVersion commit | Stale/recovery |
| rowCount, validRowCount, invalidRowCount | integer >=0 | Không | Dòng có dữ liệu, valid+invalid=rowCount | Preview counts |
| bomCount | integer >=0 | Không | Nhóm output/BOM trong file, không phải rowCount | Confirmation |
| rows[].line, values | integer>=2, array9 string | Không | Dòng Excel gốc và cell đã parse | Bảng preview |
| rows[].issues[] | array {code,column?,field?,message} | Không | Lỗi server; lỗi nhóm gắn các dòng nhóm | Lỗi dòng/cột |
| createdIds | UUID[] | Không | [] trước commit, IDs N BOM sau thành công | Links kết quả |
| createdAt, committedAt | UTC timestamp | committedAt có thể null | Server clock | History/receipt |

Summary không trả rows/file bytes hoặc thông tin owner khác. Batch READY/INVALID dữ liệu bất biến sau validate, sửa file tạo batch mới; mock preview có thể ghi lại local key trước commit nhưng **không** xem đó là contract server sửa batch. Commit stale nguồn trả lỗi conflict; toàn bộ rollback, READY chưa COMMITTED; không silently bỏ dòng. Batch COMMITTED chỉ trả receipt, không validate master lại rồi tạo thêm.

### 3.5 Field Ca/Lịch/Trung tâm/Quy trình

Response mọi master: UUID id, plantId, code, name, description nullable, status, lockVersion, createdAt, updatedAt. Input create có code/name/description; Plant lấy path, update không đổi code/Plant. Code uppercase `[A-Z0-9_-]`, 1–30; name 1–120; description <=500. Unique code theo Plant+loại; Routing code là family, nhiều version cùng code. Dates phải là ngày thực, không chỉ đúng regex.

| Field | Kiểu | Nullable | Nguồn / validation đề xuất | UI |
|---|---|---|---|---|
| Plant.timezone | IANA string | Không cho scheduling | Plant master authoritative; demo Asia/Ho_Chi_Minh | Chi tiết/nhập bắt đầu |
| Shift.effectiveFrom/effectiveTo | date/date | from không; to có | from<=to nếu có; inclusive shift-start date | Form/availability |
| Shift.workingIntervals[] | array {id?,startTime,endTime} | Không, >=1 | HH:mm, không độ dài0/trùng/overlap; end<start qua đêm, tối đa <24h một khoảng | Form/chi tiết |
| Shift.breaks[] | array {id?,name,startTime,endTime} | Không, [] được | Nằm trong giờ làm; không trùng; không ăn hết ca | Form/chi tiết |
| Shift.workingMinutes | decimal string >0 | Không response | Tổng khung làm - nghỉ; server tính, không nhận client | Chi tiết |
| Calendar.effectiveFrom/effectiveTo | date/date | from không; to có | Inclusive; ref ca phủ phạm vi sử dụng, Q8 | Form |
| Calendar.weeklySchedule[] | array7 {weekday,shiftIds[]} | Không | Mỗi MONDAY..SUNDAY đúng1 lần; ít nhất1 ngày có ca; refs ACTIVE samePlant | Lịch tuần |
| Calendar.nonWorkingDates[] | array {id?,date,name} | Không, [] được | Ngày unique, trong hiệu lực; server ID, tên1–120 | Ngày nghỉ |
| Center.capacityUnitType | MACHINE/LINE/LABOR_TEAM | Không | Người dùng chọn; không phải ID máy cụ thể | Loại công suất |
| Center.capacityUnits | integer 1..999 | Không | Số đơn vị song song | Form/công thức |
| Center.calendarId, shiftIds[] | UUID, UUID[] >=1 | Không | Calendar/ca ACTIVE cùngPlant; ca trong lịch; hiệu lực có giao nhau | Form/chi tiết |
| Center.calendarCode/name; shifts code/name | string projections | Không | Ref authoritative, không FE nhập ID thủ công | Nhãn |
| Routing.outputItemId/code/name/outputUomCode | UUID/string/string/string | Không | FG/WIP ACTIVE cùngCompany; output immutable sau create | Sản phẩm |
| Routing.versionNumber, status, effectiveFrom | integer>=1, enum, date | Không | Version theo Plant/sản phẩm; DRAFT/ACTIVE/INACTIVE; ngày thật | Phiên bản/activation |
| Routing.operations[].clientKey / operationId | string / UUID | clientKey cần khi create; UUID response không | clientKey unique nội bộ request, server cấp ID; update map IDs | Giữ row/predecessor |
| Routing.operations[].sequence/name/description | int>=1 / string1–120 / string<=300 | description có | sequence unique; text nghiệp vụ | Form |
| Routing.operations[].workCenterId | UUID | Không | Center ACTIVE cùngPlant | Trung tâm |
| Routing.operations[].setupMinutes | decimal string >=0 | Không | Chuẩn bị cho request sản xuất, một lần theo mock | Form/preview |
| Routing.operations[].runMinutesPerUnit | decimal string >0 | Không | Phút công suất cho một đơn vị Item output | Form/preview |
| Routing.operations[].predecessorClientKeys / predecessorOperationIds | string[] / UUID[] | Không, [] được | Request mới dùng keys; response IDs; không tự/sai/trùng; sequence nhỏ hơn | Công đoạn trước |
| Routing.totalSetupMinutes / totalRunMinutesPerUnit | decimal string | Không response | Sum operations, server tính | Summary |
| Routing.activatedAt/deactivatedAt | UTC timestamp | Có | Server lifecycle | Chi tiết |

Request RoutingInput gồm code/name/outputItemId/effectiveFrom/description?/operations; cập nhật routingId+expectedVersion, không đổi output/code. Operation update có operationId nếu cũ, clientKey cho mọi dòng để resolve graph; không dùng client UUID làm server UUID. Các phút đề xuất <=6 decimals và <10^13, BE cần công bố giới hạn nếu khác. Xóa operation bị ref trong graph phải gửi graph cuối không dangling hoặc trả lỗi. Version family BOM và Routing đều theo sản phẩm; Routing code kế thừa khi copy, không nhầm khóa concurrency với version.

**Lifecycle/reference:** mock master tạo ACTIVE, không có DRAFT ca/lịch/trung tâm. Chặn deactivate ca đang trong lịch ACTIVE/trung tâm ACTIVE, lịch có trung tâm ACTIVE; trung tâm có tải mở bị chặn. Calendar edit không bỏ ca đang được trung tâm ACTIVE dùng; Shift/Calendar có ref trung tâm ACTIVE không được thu hẹp range cũ (kể cả từ vô hạn thành ngày kết thúc). Một số guard mock còn dựa fixture tải cũ; server phải dùng ref/tải mở thực tế, policy trung tâm có Routing ACTIVE tại Q9. Không tự giả định sửa giờ sẽ tự reschedule WO; tác động tại Q10.

### 3.6 Công suất: công thức, thời gian và field

Kỳ `[periodStart 00:00, ngày sau periodEnd 00:00)` theo timezone Plant; ngày cuối inclusive khi nhập, tính half-open để không đếm lặp. **Giới hạn thực tế mock:** hiệu hai ngày <=90 ngày, tức tối đa91 ngày lịch inclusive; UI review gọi “90 ngày” chưa phân biệt. Q11 chọn thống nhất API/UI trước tích hợp, không trình bày đây là cap91 đã được BE chấp nhận. Horizon preview mock tới ngày `periodEnd+180`, không đồng nghĩa cam kết cấp máy.

```text
requiredMinutes_i = setupMinutes_i + quantity * runMinutesPerUnit_i
elapsedWorkingMinutes_i = requiredMinutes_i / capacityUnits_center
availableMinutes_center = sum(net working window overlap with period) * capacityUnits
previewInPeriod_i = working time allocated inside period * capacityUnits_center
previewAfterPeriod_i = requiredMinutes_i - previewInPeriod_i (requestedStart trong kỳ)
baseInPeriod_center = tổng công suất của lịch hiện có nằm trong kỳ, mỗi segment chỉ tính overlap
demandMinutes_center = baseInPeriod_center + sum(full requiredMinutes của preview)
allocatedMinutes_center = baseInPeriod_center + sum(previewInPeriod)
overloadMinutes_center = max(0, demandMinutes_center - availableMinutes_center)
```

Phút required là công suất thô: chia capacityUnits để tính elapsed một lần, không chia lại khi so tải. Cột nhu cầu có cả phần sau kỳ của request preview bắt đầu trong kỳ; cột đã xếp chỉ phần trong kỳ. Lấy thời gian elapsed giữa start/end không đủ vì có nghỉ/cuối tuần; cần segments hoặc tổng overlap server tính. Không dựng capacity từ toàn WO duration lặp cho mỗi tuần. Summary tính toàn kết quả cùng filter trước phân trang operations, không tính riêng trang đang thấy.

sourceVersions đề xuất có bomVersionNumber/bomLockVersion/routingVersionNumber/routingLockVersion; centers[] gồm id/lockVersion, calendars[] gồm id/lockVersion, shifts[] gồm id/lockVersion. Snapshot này mô tả nguồn đã đọc cho preview, không là WO snapshot hay token cho commit. UUID/tên lấy từ nguồn, không client tự điền. Nested interval/break/nonWorkingDate ID do server cấp ở response; request tạo bỏ ID, update giữ ID các dòng còn tồn tại.

**Ví dụ số nhất quán, không phải kết quả thật:** ca Thứ Ba/Thứ Tư 08:00–17:00, nghỉ12:00–13:00 ⇒480 phút/ngày/đơn vị, Center capacityUnits=2. Một operation setup30, run2, quantity500 ⇒required1030, elapsed515. Bắt đầu 13/10/2026 08:00 Asia/Ho_Chi_Minh ⇒ hết14/10/2026 08:35. Nếu kỳ chỉ13/10: available960, previewInPeriod960, after70. Có baseInPeriod100 ⇒ demand1130, allocated1060, overload170. Allocated1060>available960 là có thể xảy ra vì preview độc lập không tránh lịch base; UI phải cảnh báo, không gọi đó là lịch khả thi đã commit.

**Qua đêm:** ca13/10 22:00→14/10 06:00, nghỉ02:00–02:30 ⇒450 phút. Kỳ14/10 lấy đuôi ca hôm trước00:00→06:00 trừ30=330 phút/đơn vị. Ngày nghỉ/hiệu lực xét ngày bắt đầu ca: ngày14 nghỉ không tự cắt đuôi ca13. Ngày13 nghỉ loại cả ca13. Calendar/center lọc ca được phép, hợp nhất windows overlap để không nhân công suất hai lần. Xác định timezone từ Plant server, không browser timezone; timezone DST/giờ mơ hồ chưa được mock acceptance, Q12.

Preview đi tuần tự sequence, dù graph chỉ định một số predecessors; không tối ưu song song công đoạn. Routing/BOM phải ACTIVE và effectiveFrom<=ngày bắt đầu request. Không giả định sinh BOM requirements/netting/reservations tại preview. Điều chỉnh công đoạn WO hiện hữu dùng snapshot khối lượng/thời gian đã lưu, không lấy Routing master mới để thay nội dung lệnh; ref lịch khả dụng cần Q10.

| Field | Kiểu | Nullable | Nguồn / công thức đề xuất | UI |
|---|---|---|---|---|
| periodStart/periodEnd/timezone | date/date/IANA | Không | Query validated + Plant | Header/filter |
| routingId, bomId, sourceVersions | UUID/UUID/object | Không preview | Resolve ACTIVE sameItem/scope; business + lockVersion nguồn | Kiểm nguồn/stale |
| quantity, requestedStart | decimal string, UTC instant | Không | Input; local UI chuyển theo Plant; server kiểm lại | Form preview |
| operations[].id | UUID | Không với existing; preview có clientKey thay ID | Server planoperation; preview không tạo persisted ID | Điều chỉnh |
| operations[].workOrderId/code/status | UUID/string/PLANNED hoặc RELEASED | Có với preview; không với existing | WO thật; không bịa WO cho preview | Bảng kế hoạch |
| operations[].sequence/name/workCenterId/code/name | integer/string/UUID/string/string | Không | Snapshot cho existing; Routing/currentCenter cho preview | Bảng |
| operations[].requiredMinutes/inPeriodMinutes/afterPeriodMinutes | decimal string >=0 | Không | Công thức trên | Phút cần/chuyển kỳ |
| operations[].proposedStart/proposedEnd | UTC ISO instant | Không khi tính thành công | Windows/parallel/timezone | Bắt đầu/kết thúc |
| operations[].segments[] | array {start,end,capacityMinutes} | Không, >=1 | Windows đã allocate; capacityMinutes thô | Kiểm overlap/period |
| operations[].lockVersion/manualAdjustment | integer/object {requestedStart,reason,adjustedAt,actorId} | lockVersion chỉ existing; adjustment có | Server concurrency/audit | Điều chỉnh/chi tiết |
| loads[].workCenterId/baseInPeriodMinutes/previewRequiredMinutes | UUID/decimal/decimal | Không | Base kế hoạch trong kỳ + preview full | Tách current/preview |
| loads[].demandMinutes/allocatedMinutes/availableMinutes/overloadMinutes | decimal string | Không | Công thức trên | Bảng tải |
| loads[].utilizationPercent | decimal string | Có khi available=0 | demand/available*100; null không hiển thị 0 giả | Nếu UI hiển thị phần trăm |
| loads[].status | AVAILABLE/NEAR_CAPACITY/OVERLOADED | Không nếu dùng | overload>0 là OVERLOADED; threshold near cần Q15 | Badge tùy mapping |
| summary / sourceGeneratedAt | totals object / UTC instant | Không | Cùngfilter/serverclock; không nhận client totals | Summary/freshness |

### 3.7 Ví dụ wire đề xuất — KHÔNG phải request/response thật

Các UUID `111...`=Company C, `222...`=Plant P, `333...`=FG-DEMO, `444...`=RM-DEMO, `555...`=BOM, `666...`=Center, `777...`=Routing, `888...`=operation, `999...`=batch đều chỉ minh họa. Seed test phải lấy ID server thật; không dùng các ví dụ để tra log.

**Atomic BOM create**, Idempotency-Key UUID mới cho lần submit; Bearer token đã che:

```http
PUT /api/v1/companies/11111111-1111-4111-8111-111111111111/bom-drafts
Authorization: Bearer [REDACTED]
Content-Type: application/json
Idempotency-Key: aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa
```

```json
{"bomCode":"BOM-DEMO-01","parentItemId":"33333333-3333-4333-8333-333333333333","outputQuantity":"10","effectiveFrom":null,"description":"BOM minh họa","lines":[{"lineNo":1,"componentItemId":"44444444-4444-4444-8444-444444444444","componentQuantity":"25","scrapRate":"0.05"}]}
```

Response đề xuất HTTP200, **projection tối thiểu cho ví dụ** (full detail còn code/name/UOM/timestamp/lineIDs như bảng, không coi chúng là optional vì bị rút gọn ở đây):

```json
{"code":"SUCCESS","message":"OK","result":{"bomId":"55555555-5555-4555-8555-555555555555","companyId":"11111111-1111-4111-8111-111111111111","parentItemId":"33333333-3333-4333-8333-333333333333","bomCode":"BOM-DEMO-01","versionNumber":1,"revision":"1","outputQuantity":"10","outputUomCode":"PCS","effectiveFrom":null,"status":"DRAFT","lockVersion":1,"lines":[{"lineNo":1,"componentItemId":"44444444-4444-4444-8444-444444444444","componentQuantity":"25","quantityPer":"2.5","componentUomCode":"KG","scrapRate":"0.05"}]}}
```

RoutingInput ví dụ một operation để dùng capacity ví dụ (create qua PUT Plant routing-drafts):

```json
{"code":"ROUTE-DEMO","name":"Lắp ráp mẫu","outputItemId":"33333333-3333-4333-8333-333333333333","effectiveFrom":"2026-10-01","operations":[{"clientKey":"assembly","sequence":1,"name":"Lắp ráp","workCenterId":"66666666-6666-4666-8666-666666666666","setupMinutes":"30","runMinutesPerUnit":"2","predecessorClientKeys":[]}]}
```

Preview input POST Plant capacity/preview; prerequisite BOM/Quy trình đã ACTIVE, Center/calendar/shift như §3.6. Không gửi client timezone/available/calc values:

```json
{"routingId":"77777777-7777-4777-8777-777777777777","quantity":"500","requestedStart":"2026-10-13T01:00:00Z","periodStart":"2026-10-13","periodEnd":"2026-10-13"}
```

Response **projection minh họa** HTTP200; start/end UTC khớp08:00/08:35 local, segments tính cả nghỉ:

```json
{"code":"SUCCESS","message":"OK","result":{"timezone":"Asia/Ho_Chi_Minh","periodStart":"2026-10-13","periodEnd":"2026-10-13","operations":[{"sequence":1,"name":"Lắp ráp","workCenterId":"66666666-6666-4666-8666-666666666666","requiredMinutes":"1030","inPeriodMinutes":"960","afterPeriodMinutes":"70","proposedStart":"2026-10-13T01:00:00Z","proposedEnd":"2026-10-14T01:35:00Z","segments":[{"start":"2026-10-13T01:00:00Z","end":"2026-10-13T05:00:00Z","capacityMinutes":"480"},{"start":"2026-10-13T06:00:00Z","end":"2026-10-13T10:00:00Z","capacityMinutes":"480"},{"start":"2026-10-14T01:00:00Z","end":"2026-10-14T01:35:00Z","capacityMinutes":"70"}]}],"loads":[{"workCenterId":"66666666-6666-4666-8666-666666666666","baseInPeriodMinutes":"100","previewRequiredMinutes":"1030","demandMinutes":"1130","allocatedMinutes":"1060","availableMinutes":"960","overloadMinutes":"170"}]}}
```

Adjustment input POST operation `888.../adjustments`, Idempotency-Key khác; server resolve operation Plant, giữ requestStartUTC và lý do:

```json
{"requestedStart":"2026-10-13T01:00:00Z","reason":"Đổi giờ theo kế hoạch","periodStart":"2026-10-13","periodEnd":"2026-10-19","expectedVersion":3}
```

Response: ScheduledOperation full + manualAdjustment actor/time, lockVersion4. Nếu đè predecessor/successor trả lỗi, không tăng version/đổi lịch.

Import multipart POST batches: file `.xlsx`, kind=`bom`, companyId=C; không gửi rows/status/owner/Plant. HTTP201 với object sau là projection, rowCount1 và bomCount1 đúng fixture đơn dòng §3.4:

```json
{"code":"SUCCESS","message":"OK","result":{"id":"99999999-9999-4999-8999-999999999999","kind":"bom","companyId":"11111111-1111-4111-8111-111111111111","fileName":"bom-demo.xlsx","templateVersion":"omniplant-bom-v1","locale":"vi","state":"READY","lockVersion":1,"rowCount":1,"validRowCount":1,"invalidRowCount":0,"bomCount":1,"rows":[{"line":2,"values":["BOM-DEMO-01","FG-DEMO","10","PCS","RM-DEMO","25","KG","5","BOM minh họa"],"issues":[]}],"createdIds":[],"committedAt":null}}
```

Commit body POST batch `999.../commit`:

```json
{"expectedVersion":1}
```

Ví dụ atomic create và import là **hai kịch bản riêng, reset fixture trước mỗi kịch bản**; không import FG-DEMO sau khi đã tạo BOM ở ví dụ create. Success commit: cùng batch, COMMITTED, lockVersion2, createdIds=[bomId thực của lần import], committedAt server; retry trả đúng các giá trị cũ. Không tự đổi createdIds sang ID BOM của kịch bản khác.

### 3.8 Atomicity, concurrency, tác động dữ liệu và nghiệm thu

- Atomic SAVE: transaction gồm header, lines/graph, lockVersion, audit và receipt. Xóa dòng không còn trong graph trong cùng transaction. Unique code/version và Item/UOM được kiểm tại commit. Khóa browser không thay transaction DB.
- Activation: khóa family; kiểm version, refs, ngày và vòng bên trong transaction. Prior ACTIVE → INACTIVE và new ACTIVE cùng commit. Race không được kết thúc với 2 ACTIVE. Không cascade update snapshot WO.
- Import: lưu batch đã validate từ file gốc phía server; authorization trước receipt; commit từ batch, không nhận client rows. Khóa batch, kiểm unique master; tất cả N BOM/lines/receipt/COMMITTED cùng transaction. Một lỗi rollback toàn bộ. Cùng key khác fingerprint → IDEMPOTENCY_CONFLICT; cùng batch/key/content → cùng kết quả. Retry COMMITTED trả receipt sau auth/conflict check, không reject chỉ vì expectedVersion cũ của chính request đã thành công.
- Mất response: FE lấy detail cùng ID hoặc retry cùng key; không tự tạo batch/key mới để đoán. Upload retry cùng key/content trả cùng batch. Assignment thu hồi không được đọc receipt. Quota/retention theo Q14.
- Reference master: chặn phá ref ACTIVE/kế hoạch theo policy đã chốt. Nguồn đổi sau preview phải recompute; không dùng preview để tự commit WO. Preview chỉ đọc; adjustment ghi lịch/audit/receipt atomic. Điều kiện PLANNED/RELEASED và chưa bắt đầu tại Q16.
- Tác động kho: **không có** cho save/copy/activate/import/preview/adjust. On Hand, Reserved, Available, HOLD/REJECTED, WIP/value không đổi; không sinh movement/issue/receipt. Adjustment chỉ thay lịch công đoạn hiện hữu, không quantity/status/nội dung snapshot WO. Giai đoạn 5 không tạo ledger kho/costing.

Các case dưới đều **pending live**, không chuyển “đạt” từ 452 FE tests. BE seed Company C/Plant P và Plant khác, master ACTIVE, calendar/center/routing/BOM theo ví dụ; actor Manage đủ quyền, actor Read và actor khác. Tên fixture trong bảng phải được thay bằng ID server thật khi chạy.

| ID | Đầu vào / thao tác | Kết quả mong đợi kiểm được |
|---|---|---|
| A01 | SAVE BOM output 10, component 25, rate 0.05 | 1 DRAFT đủ lines, quantityPer 2.5, output 10, lockVersion 1 |
| A02 | Qty 0/âm/7 decimals/exponent/tràn; scrapRate 1 | Validation code/field; 0 writes; input FE giữ nguyên |
| A03 | Output RAW/component FG/self/duplicate/UOM sai/inactive | Ref/domain error; 0 writes |
| A04 | SAVE nhiều lines, lỗi line cuối hoặc DB failure | Header, toàn bộ lines và receipt rollback; DB delta 0 |
| A05 | Update DRAFT đúng version / stale version | Đúng version cập nhật cả graph; stale 409, không mất update |
| A06 | Edit ACTIVE/INACTIVE / copy nguồn ACTIVE | Edit 409; copy DRAFT version mới, source unchanged |
| A07 | Activate A rồi B cùng output; 2 request concurrent | Tối đa 1 ACTIVE sau commit; version/family unique |
| A08 | A chứa B, B chứa A; activate | CIRCULAR_BOM từ BE, status cũ giữ; không client traversal |
| A09 | Activate ngày tương lai / đúng ngày Company timezone | Tương lai 409; đúng ngày được; không auto-publish |
| A10 | Tree thiếu child / đổi version child | Ref ACTIVE current/hasActiveBom đúng; snapshot WO cũ không đổi |
| A11 | Import đúng 1 nhóm, 2 components, output chưa có BOM | Validate READY không tạo; commit 1 BOM DRAFT + 2 lines |
| A12 | Header/version/formula/type sai; >5 MiB/>500 rows/empty | File error hoặc INVALID row issues; không tạo BOM |
| A13 | Group code/output qty/UOM/notes lệch; trùng component | Cả group có lỗi; batch không commit |
| A14 | Import output đã có BOM hoặc 2 codes cùng output | Chặn create-only; 0 writes |
| A15 | Ref inactive hoặc mã trùng sau READY | Commit 409; rollback cả batch; không silent overwrite |
| A16 | Commit N=3, lỗi BOM thứ 3 | Delta BOM/lines/receipt thành công = 0; state không COMMITTED |
| A17 | Commit/timeout/retry/commit đồng thời cùng batch | Tổng N; cùng createdIds/committedAt; không 2N/audit trùng |
| A18 | Hai batch có 1 output trùng và các output khác; concurrent | Một batch thắng; batch thua rollback toàn bộ |
| A19 | Cùng key khác payload; upload retry cùng key/content | Khác payload conflict; giống content trả batch cũ |
| A20 | Ca 08–17, nghỉ 12–13; overlap/zero/outside break | Net 480; invalid có lỗi field; không lưu cấu hình sai |
| A21 | Ca 22–06, nghỉ 02–02:30; kỳ ngày sau | Net 450; đuôi trong kỳ 330/đơn vị; ngày nghỉ theo shift start |
| A22 | Lịch 7 weekdays; duplicate weekday/date; ref ca Plant khác | Domain validation; không ghi một phần graph |
| A23 | Deactivate ca/lịch đang dùng; thu hẹp range/bỏ ca có ref | REFERENCE_IN_USE; ref/kế hoạch không hỏng |
| A24 | Center units 0/1000; ref inactive/Plant khác | Validation/ref error; không tạo center |
| A25 | Routing sequence/predecessor sai; save graph fault | Invalid graph bị từ chối; rollback toàn bộ operations |
| A26 | Copy/activate Routing; ngày tương lai; activation concurrent | Thay prior ACTIVE atomic; tối đa 1 ACTIVE Plant/output |
| A27 | Preview 500; setup 30/run 2/units 2/kỳ 13 Oct/base 100 | Required 1030; start 13 Oct 08:00, end 14 Oct 08:35; in 960/after 70; demand 1130/allocated 1060/available 960/over 170 |
| A28 | Preview BOM/routing/ref inactive; thiếu quyền nguồn | Specific error/403; không tính bằng nguồn bị từ chối |
| A29 | Preview không windows/horizon; qty 0/kỳ sai | Specific error; không lịch giả/0 minutes fallback |
| A30 | Preview hợp lệ 2 lần; DB WO/kho trước–sau | Delta WO/reservation/movement/committed plan = 0 |
| A31 | Operation hiện hữu vắt 2 kỳ; filters/pages | In-period theo overlap segments; summary không phụ thuộc page |
| A32 | Adjustment lý do ngắn/bắt đầu ngoài kỳ/chồng sibling | Từ chối; lịch/audit/receipt/version unchanged |
| A33 | Adjustment đúng; retry/stale/concurrent | Same key 1 adjustment; stale conflict; lượng/status/snapshot giữ |
| A34 | Read-only; direct ID other Plant/Company/owner | Không lộ record; 403/404 như §4; 0 writes |
| A35 | Assignment thu hồi sau preview/trước commit/replay | Từ chối request; không receipt leak hoặc bypass auth |
| A36 | WO snapshot version 1; activate master 2/edit calendar | Nội dung snapshot giữ; lịch existing theo quyết định Q10 |

**Bằng chứng cho từng case:** môi trường + giờ + BE version đã xác minh, request/response JSON che token, HTTP/code, X-Trace-Id, Company/Plant/batch/record ID thật cùng lượt, DB/list trước–sau. Concurrent case cần cả 2 trace và tổng kết quả. Preview delta đo trên fixture riêng; không lấy record từ lượt khác.

**Rollout:** BE trả Q1–Q19 và OpenAPI/version → dependency Item/UOM/Plant timezone/quyền → Ca/Lịch/Trung tâm → atomic BOM/lifecycle/tree → Routing → import batch → capacity read/preview/adjust → PO duyệt công việc adapter FE → live A01–A36. Migration cần backfill outputQuantity/revision mapping, unique ACTIVE và lockVersion; không suy ra lượng batch cũ khi thiếu nguồn. Seed test bằng record có thể thu hồi/ID thật; không biến fixture tháng 7 thành logic production.

**Điều kiện chuyển mock → API:** contract/mapping chốt, nguồn auth/decimal/timezone sẵn sàng; adapter đã được duyệt và đạt case liên quan. Không bật flag cho module chưa hỗ trợ hoặc fallback ngầm. **Live acceptance:** chưa thử. **Trạng thái:** tài liệu chuẩn bị, chờ PO gửi/BE trả lời; chưa đóng API integration. **Changelog:** v1 ban đầu Session 303; không thay application.

## §4 — Hợp đồng lỗi mong đợi

**[Đề xuất BE]** FE rẽ nhánh theo code, không chỉ HTTP. Chưa khẳng định code mới tồn tại; BE trả mapping nếu code đang có tương đương. Các mã ngắn mock như OUTPUT/SCRAP/IMMUTABLE hoặc lowercase predecessorInvalid không tự xem là wire contract.

| Tình huống | HTTP / code đề xuất | Message/field trên FE | Giữ input / retry |
|---|---|---|---|
| Không auth/token hết hạn | 401 theo Auth contract giai đoạn 1 | Login/refresh đúng Auth code | Không retry 403 bằng refresh |
| Thiếu quyền trong scope | 403 PERMISSION_DENIED | Không có quyền thực hiện | Giữ form; không auto-submit |
| ID không tồn tại/trái scope detail | 404 NOT_FOUND | Không tìm thấy dữ liệu được phép | Không retry tạo record |
| Qty/date/time/scale/graph invalid | 400 VALIDATION_ERROR | errors.field, ví dụ lines[0].componentQuantity | Giữ mọi input, sửa trước submit mới |
| Item/UOM/ref không hợp lệ | 409 REFERENCE_UNAVAILABLE | Chọn nguồn đang hoạt động | Refetch nguồn; giữ form |
| Component trùng | 400 DUPLICATE_COMPONENT | Thành phần đã có | Giữ rows |
| BOM vòng | 409 CIRCULAR_BOM | BOM có tham chiếu vòng; path nếu BE trả | Giữ DRAFT; không tự bỏ component |
| State không cho sửa/kích hoạt | 409 INVALID_STATE | Bản đang dùng chỉ sao chép; action không phù hợp | Refetch detail, không overwrite |
| Ngày hiệu lực tương lai | 409 EFFECTIVE_DATE_FUTURE | Chưa tới ngày hiệu lực | Không auto-publish |
| Unique code/output/version conflict | 409 DUPLICATE_CODE hoặc BOM_OUTPUT_EXISTS | Lỗi tại mã/sản phẩm/group | Không upsert/partial |
| Stale lockVersion | 409 VERSION_CONFLICT | Dữ liệu thay đổi, tải lại | Giữ form để so sánh, không auto-save |
| Reference ngăn ngừng dùng | 409 REFERENCE_IN_USE | Đang được sử dụng; ref summary có kiểm quyền | Giữ record; không cascade |
| Routing có nháp mở khi copy | 409 OPEN_DRAFT_EXISTS | Mở nháp đã có | Không tạo nháp ẩn |
| File format/template/size/rows | 400 IMPORT_FILE_INVALID / IMPORT_TEMPLATE_INVALID / IMPORT_LIMIT_EXCEEDED / EMPTY_FILE | Lỗi file, không nhầm lỗi DB | Giữ file; sửa rồi upload mới |
| Parse hợp lệ nhưng dòng lỗi | 201 SUCCESS, result.state=INVALID | rows.issues code/column/line | Không commit; sửa file thành batch mới |
| Commit INVALID/stale version/ref | 409 IMPORT_BATCH_NOT_READY / IMPORT_BATCH_VERSION_CONFLICT / IMPORT_STALE | Tải lại batch hoặc sửa file/validate | Giữ batch ID; không tạo cục bộ |
| Cùng key khác payload | 409 IDEMPOTENCY_CONFLICT | Mã yêu cầu không khớp | Không đổi key để che conflict |
| Không windows/horizon | 409 SCHEDULING_UNAVAILABLE | Chưa có lịch tính được | Giữ input; không giả 0 phút |
| Adjustment đè trước/sau | 409 OPERATION_SEQUENCE_CONFLICT | Thời gian xung đột chuỗi công đoạn | Giữ requestedStart/reason |
| Lỗi DB/chưa biết kết quả | 500 INTERNAL_ERROR | Kiểm tra kết quả/thử lại | Giữ key/ID; đọc detail trước retry |

Ví dụ lỗi **đề xuất**, HTTP 400; X-Trace-Id server trả trong response header và expose để FE đọc; không bịa giá trị trong tài liệu:

```json
{"code":"VALIDATION_ERROR","message":"Lượng thành phần phải lớn hơn 0","result":null,"errors":[{"field":"lines[0].componentQuantity","message":"Phải lớn hơn 0"}]}
```

Error envelope: code/message required; result nullable hoặc omitted theo NON_NULL serializer BE; errors optional array gồm field/message. Import issues thêm line/column trong result batch khi parse được. Không đưa token/secret/raw stack trace vào message. Code stable cho VI/EN, FE dịch nhãn nghiệp vụ/code; message BE cần đúng tình huống. 401 chỉ refresh theo Auth code đã chốt; timeout retry cùng key; không tự retry domain 409.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q là một quyết định; BE trả Có/Không hoặc chọn A/B, ghi phương án khác nếu không đồng ý. Owner: BE đề xuất, PO chốt khi đổi nghiệp vụ; quyết định/ngày hiện đều **chưa chốt**. “CHẶN” là chặn adapter/live cụ thể, **không chặn FE/mock giai đoạn tiếp theo**.

1. **Q1:** Lưu componentQuantity theo outputQuantity và giữ quantityPer là projection per-unit được không? — FE nghiêng về Có để tái hiện form và không phá consumer cũ — **CHẶN BOM adapter**.
2. **Q2:** Phép chia quantityPer không ra hữu hạn 6 decimals sẽ giữ độ chính xác nội bộ cao, chỉ round ở boundary công khai được không? — FE nghiêng về Có; BE công bố scale/mode trước adapter, không silently truncate input batch — **CHẶN decimal compatibility**.
3. **Q3:** revision tiếp tục là nhãn string, versionNumber server cấp tăng theo Company/sản phẩm, được không? — FE nghiêng về Có để không parse revision cũ làm số — **CHẶN version migration**.
4. **Q4:** BOM effectiveFrom=null nghĩa là không trì hoãn activation, được không? — FE nghiêng về Có vì form/file cho phép thiếu ngày; không tự lấy ngày máy FE — **CHẶN BOM/import contract**.
5. **Q5:** Ngày kích hoạt BOM dùng timezone Company (MVP Asia/Ho_Chi_Minh), được không? — FE nghiêng về Có vì BOM Company-scoped, không phụ thuộc Plant đang chọn — **CHẶN activation acceptance**.
6. **Q6:** Tree trả quantityPer per-unit ở từng node, thêm componentQuantity/outputQuantity để hiển thị batch, được không? — FE nghiêng về Có để giữ consumer cũ và tránh nhân lặp — **CHẶN tree adapter**.
7. **Q7:** Có dùng một atomic SAVE header+lines/graph thay chuỗi request line cũ cho UI mới không? — FE nghiêng về Có; các path 3.3 là gợi ý, giữ API cũ tương thích — **CHẶN SAVE adapter**.
8. **Q8:** Calendar hiệu lực phải nằm trong phạm vi ca được tham chiếu khi tạo/cập nhật, được không? — FE nghiêng về Có theo guard mock, center chỉ yêu cầu giao hiệu lực — **CHẶN master validation**.
9. **Q9:** Trung tâm còn được Routing ACTIVE tham chiếu có bị chặn deactivate ngay cả khi chưa có tải mở không? — FE nghiêng về Có; mock hiện chủ yếu chặn tải fixture, cần policy server rõ — **CHẶN lifecycle acceptance**.
10. **Q10:** Chỉnh ca/lịch chỉ ảnh hưởng preview mới, lịch WO đã cam kết giữ nguyên tới explicit adjustment, được không? — FE nghiêng về Có để không âm thầm reschedule; mock existing fixture còn tính theo lịch hiện tại nên cần rà adapter — **CHẶN existing plan semantics**.
11. **Q11:** Giới hạn kỳ là tối đa 90 ngày lịch inclusive thay vì date-difference<=90 (91 ngày inclusive) được không? — FE nghiêng về 90 inclusive theo tên UI; sẽ chỉnh boundary FE khi tích hợp — **CHẶN period adapter, không chặn mock**.
12. **Q12:** Rollout đầu chỉ hỗ trợ timezone IANA không DST như Asia/Ho_Chi_Minh được không? — FE nghiêng về Có tới khi BE/FE có policy giờ mơ hồ và test DST — **CHẶN rollout timezone**.
13. **Q13:** API read history/detail nhập BOM chỉ cần BOM_READ + owner/company, còn upload/commit cần thêm MANAGE được không? — FE nghiêng về Có theo read service; route FE hiện vẫn cần manage, sẽ align khi adapter — **CHẶN history authorization**.
14. **Q14:** Receipt/batch/audit giữ theo policy master history, file gốc có retention riêng, được không? — FE nghiêng về Có để retry không tạo trùng; BE nêu thời hạn cụ thể khi trả lời — **CHẶN rollout retention, không chặn mock**.
15. **Q15:** NEAR_CAPACITY dùng utilization>=85% và <=100% khi chưa overload được không? — FE nghiêng về Có theo threshold mock cũ, available=0 và demand>0 phải OVERLOADED — **KHÔNG CHẶN bảng phút hiện tại; chặn badge nếu bật**.
16. **Q16:** Adjustment được dùng cho PLANNED và RELEASED chỉ khi công đoạn chưa bắt đầu, được không? — FE nghiêng về Có; current mock chưa có execution gate đầy đủ — **CHẶN adjustment live**.
17. **Q17:** Preview tuần tự sequence, không trừ lịch base để giải xung đột máy, được không? — FE nghiêng về Có theo scope đã làm; phải giữ nhãn ước tính/cảnh báo quá tải — **CHẶN preview contract**.
18. **Q18:** Một nháp mở mỗi Routing family khi copy, còn BOM cho phép nhiều version nháp, được không? — FE nghiêng về Có theo hành vi hiện tại; không tự ép cả hai cùng policy — **CHẶN copy/version acceptance**.
19. **Q19:** Rate hao hụt trong contract mới dùng scale tối đa 8 để giữ chính xác phần trăm nhập scale 6, được không? — FE nghiêng về Có; ví dụ 0.000001% = rate 0.00000001, adapter cũ scale 6 cần mapping riêng, không silently round về 0 — **CHẶN scrap decimal adapter**.

## §6 — FE đã tự xử lý

**[Đã kiểm chứng FE]**

- List/form/panel giữ bộ lọc và input qua lỗi. Lưu/kích hoạt/import commit/điều chỉnh có xác nhận. Read-only theo quyền/state. Labels VI/EN nghiệp vụ, không dùng PERM/ID kỹ thuật làm tên nghiệp vụ.
- Validate quantity, scrap, ngày, UOM/type, duplicate và self component; UI % chuyển rate/100. Item/UOM từ master hiện tại. Mock numeric BOM legacy chỉ nhận decimal roundtrip không mất precision; không thay server decimal acceptance.
- Scope/quyền kiểm sau chờ khóa; đổi actor/Company/Plant đóng workspace; invalidate/refetch sau ghi. BOM Company, production design Plant; không tính preview khi thiếu quyền nguồn.
- Mock Web Locks serialize master/design, snapshot rollback khi storage lỗi, giữ module chưa tải và receipt fingerprint/idempotency. Không phải server auth/transaction/đa người dùng; BE vẫn enforce.
- Workbook 5 MiB/500 rows/template VI/EN/metadata/type guards; group lỗi khóa cả batch; chỉ DRAFT mới. Giữ file/preview/key qua lỗi, lịch sử owner/Company; reload không tạo lại record.
- Scheduling theo Plant timezone, breaks/qua đêm/đuôi ca hôm trước/hiệu lực. Bảng nhu cầu, allocated và chuyển kỳ tách riêng; preview không commit WO. Adjustment có lý do, xác nhận và thứ tự; không tự release.
- Circular BOM chỉ là error fixture mock; **không có** client graph detection. Cây 8 cấp UI không thay kiểm vòng BE. WO đã lưu không đổi theo master; cây hiện tại không là snapshot WO.
- Giữ API BOM cũ. Module mới chưa có adapter API hiển thị chưa sẵn sàng, không fallback mock hoặc giả lập import bằng create từng dòng. Chưa thêm wire path đề xuất vào app.

**Cần rà khi BE chốt:** batch/per-unit/rounding/revision migration; kỳ 90 ngày inclusive; history read policy; reference deactivate; freeze lịch WO; DST; execution gate adjustment; enum WIP/SEMI_FINISHED. Không gỡ guard trước khi nguồn server tương đương được nghiệm thu. PO duyệt công việc tích hợp trước thay adapter.

## §7 — Tham chiếu

Các file dưới đã được chuẩn bị ở các lần bàn giao trước; **chưa xác nhận PO đã gửi/BE đã nhận**. Không yêu cầu BE mở repo/máy FE; nội dung giai đoạn 5 cần triển khai đã nằm trong file này.

- `BE_HANDOFF_GD_01_2026-10-09.md` — Auth, quyền/scope/email và quyết định bảo mật; đối chiếu bản PO thực gửi.
- `FE_HANDOFF_GD_02_2026-10-12.md` — Item/UOM/Plant, lifecycle; dependency WIP/timezone/reference.
- `FE_HANDOFF_GD_03_2026-10-12.md` — Nền import batch đề xuất; bổ sung kind=bom ở đây, không đổi create-only Item/partner.
- `FE_HANDOFF_GD_04_2026-10-12.md` — Kho/lô/reservation/valuation; giai đoạn 5 không ghi kho, giữ seam snapshot.
- `FE_HANDOFF_GD_05_MESSAGE_2026-10-13.md` — Tin nhắn bốn dòng PO có thể gửi cùng bản này.

### Checklist phát hành v1

- §0 đủ 3 dòng, P1 chỉ luồng API bị chặn; §1 có danh sách màn hình và Ngoài phạm vi.
- Không có defect live: cả 5 mảnh thiếu ở §2 được nêu. Không bịa trace/version/ID/time. BE mới nhất chưa xác minh, không claim lỗi server.
- JSON trong code block; UUID/ví dụ minh họa. Required 1030/in 960/after 70/demand 1130/allocated 1060/available 960/over 170 khớp nhau.
- R01–R15 cần triển khai đúng/sai; field đủ 5 cột; API path/input/result/page/permission; công thức/fixture; atomicity/retry được nêu.
- §4 có code/HTTP/input/retry; §5 Q1–Q19 chỉ trả lời, mỗi mục một quyết định/hướng FE/mức chặn; §6 phòng vệ/giới hạn; §7 không link repo/đường dẫn máy.
- A01–A36 pending live; thiếu deploy/acceptance evidence không được đánh “đạt”. Đã lưu v1/tin nhắn 4 dòng, che token; chưa gửi ngoài. Chỉ đóng BE task sau API acceptance; sửa sau gửi phát hành v2.

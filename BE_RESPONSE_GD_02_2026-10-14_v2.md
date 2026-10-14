# BE trả lời — Giai đoạn 2: Tổ chức và danh mục nền

**Trả lời cho:** `FE_HANDOFF_GD_02_2026-10-12.md` (v1) · **Ngày gửi:** 14/10/2026 · **Bản:** **v2** (thay hoàn toàn bản v1)
**Mức:** P1 · **Breaking:** CÓ (7 thay đổi hành vi, liệt kê từng cái ở §3) · **Thứ tự deploy:** không ràng buộc — FE adapter hiện tại tiếp tục chạy, chỉ các request vốn sai dữ liệu mới bắt đầu bị từ chối.

> **v2 khác v1 ở đúng một điểm:** A10 đã sửa. Gửi số có dấu chấm (`1.5`, kể cả `5.0`) vào field **số nguyên**
> nay trả **400 `VALIDATION_ERROR`** kèm tên field, thay vì bị cắt thành `1` rồi lưu. Thay đổi áp cho **mọi
> endpoint**, không riêng thiết lập kho — xem §3 mục 7, mẫu thật ở §4.6. Mọi phần khác giữ nguyên như v1.

> Mọi request/response trong file này **copy nguyên văn** từ lượt gọi thật ngày 14/10/2026 (giờ VN), môi trường mô
> tả ở §7. UUID là của fixture dùng-một-lần (đã ngừng hoạt động sau khi đo) — **đừng** dùng lại các ID này để gọi.
> Token đã che; `X-Trace-Id` giữ nguyên để đối chiếu.

---

## §0 — Kết luận và phân loại

**Một câu:** phần thiếu đã làm đủ (Khách hàng mới, Nhà cung cấp theo đúng hình dạng FE đề xuất, Thiết lập kho có
xác nhận thay mặc định + lọc theo nhà máy + chống ghi đồng thời, bộ lọc tổ chức/vật tư, kiểm múi giờ/đơn vị tính/nhà
máy còn kho); BE **khác đề xuất FE ở 4 điểm** (quy ước PATCH, `replaceDefaults` khi bỏ trống, mã lỗi, số thập phân là
JSON number). Lỗi số lẻ bị cắt im lặng (A10) đã sửa cho **toàn hệ thống**.

### 0.1 Mười một kết quả R1–R11

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R1 | Supplier/Customer: list/create/detail/update/activate/deactivate | 🟡 **Thiếu thật, đã làm** | Customer trước đây **không tồn tại**. Supplier thiếu đường theo công ty, thiếu `activate`, thiếu `contactName`/`notes` — nay đủ (§4.1, §4.2) |
| R2 | Mã trim + viết hoa; trùng bị từ chối; Supplier và Customer được trùng mã | ✅ **Đúng** cho Supplier/Customer (mẫu §4.1: `" partner-01 "` lưu `PARTNER-01`; trùng ⇒ 409; Supplier cùng mã với Customer ⇒ 201). ⚠️ **Plant/Warehouse/Item KHÔNG tự chuẩn hoá** — gửi chữ thường bị 400 `VALIDATION_ERROR`; FE tiếp tục chuẩn hoá phía mình như đang làm | §4.1 |
| R3 | Không đổi mã/cha/loại/tracking; đơn vị theo Q6 | 🟡 **Một phần thiếu, đã làm**: `unit` trước đây đổi được qua PATCH — nay bị chặn. ⚠️ `code`/`type`/`lotTracked` gửi kèm PATCH **bị bỏ qua im lặng** (200, giá trị cũ giữ nguyên), **không** trả lỗi | §4.3 — mẫu "PATCH item kèm code/type/lotTracked" |
| R4 | Không tạo/kích hoạt kho dưới nhà máy ngừng; không ghi thiết lập cho dữ liệu ngừng | ✅ Kho: đã có. 🟡 **Thiết lập kho không kiểm nhà máy ngừng** — đã làm | §4.4 |
| R5 | GET thiết lập trả đúng nhà máy đã cấp quyền | 🔴 **Lỗ hổng thật, đã sửa.** Trước đây endpoint **không có** bộ lọc nhà máy và **bỏ qua** header `X-Plant-Id`; gọi không kèm `warehouseId` thì trả thiết lập của **mọi công ty** cho tài khoản có quyền toàn hệ thống. Nay có `plantId` + kiểm quyền theo nhà máy + đối chiếu header | §4.5 |
| R6 | Tồn an toàn/điểm đặt lại ≥ 0 thập phân; thời gian cung ứng nguyên ≥ 0 | ✅ Âm ⇒ 400 đúng field. 🔴 **Lỗi thật, đã sửa:** `leadTimeDays: 1.5` trước đây được nhận và **lưu thành `1`** (200, trace `4efc9c9fb6144584`). Nguyên nhân là cấu hình chung nên **mọi** field số nguyên trên **mọi** endpoint đều bị cắt như vậy — nay đều trả 400 kèm tên field | §4.6 |
| R7 | Không hai thiết lập ACTIVE cùng cặp; tối đa một mặc định mỗi vật tư + nhà máy + mục đích | ✅ **Đã có** từ trước, ràng buộc ở cơ sở dữ liệu | — |
| R8 | Thay mặc định có xác nhận, nguyên tử, an toàn khi đồng thời | 🔴 **Hai lỗi thật, đã sửa.** (1) Trước đây BE **luôn tự chuyển** cờ sang kho mới, không có cách nào để FE hỏi người dùng trước. (2) Hai request đồng thời có thể **cùng thành công và để lại HAI kho mặc định** — đo được bằng test tự động trên PostgreSQL thật; ràng buộc cơ sở dữ liệu có sẵn **không** chặn được trường hợp này | §4.5 + §7 |
| R9 | Ngừng là logic, không xoá lịch sử | ✅ Đúng cho mọi danh mục. 🟡 **Ngừng một thiết lập trước đây vẫn giữ cờ mặc định** — đã sửa (nay xoá cả hai cờ) | §4.5 — "Lưu lại cặp I1+W2" |
| R10 | Tìm/lọc trước phân trang, tổng đúng, sắp xếp ổn định | 🔴 **Lỗi thật, đã sửa.** `GET .../plants` và `GET .../warehouses` **bỏ qua im lặng** `search`/`status` FE gửi ⇒ live trả danh sách không lọc. Vật tư chỉ có `search`. Nay lọc trong truy vấn trước phân trang + sắp xếp phụ theo ID | §4.3, §4.4 |
| R11 | Sai quyền/phạm vi bị từ chối ở server | ✅ Mọi endpoint kiểm quyền ở server; ngoại lệ duy nhất là R5 (đã sửa). ID không tồn tại trả **403**, không phải 404 — xem Q13 | §4.1 — "Customer id không tồn tại" |

**Ba lỗi BE tìm thêm trong lúc đối chiếu (FE không báo):**

| Lỗi | Kết luận |
|---|---|
| Múi giờ rác (vd `Mars/Olympus`) được **lưu bình thường**, rồi về sau làm lệnh phát hành lệnh sản xuất của nhà máy đó trả **500** | 🔴 **Bug thật, đã sửa** — nay 400 ngay lúc tạo/sửa nhà máy |
| `unit` của vật tư là chuỗi tự do, **không** kiểm với danh mục Đơn vị tính | 🟡 **Thiếu thật, đã làm** — tạo vật tư nay đòi mã UOM tồn tại và đang hoạt động |
| Ngừng nhà máy khi còn kho hoạt động **thành công** và để lại kho mồ côi | 🟡 **Thiếu thật, đã làm** — nay bị từ chối (Q4) |

### 0.2 Trả lời 16 câu §5

| Q | Câu hỏi (tóm tắt) | BE trả lời | Khác đề xuất FE? |
|---|---|---|---|
| Q1 | Path/DTO Supplier–Customer §3.4 | **Có.** Customer đúng nguyên hình dạng §3.4. Supplier: thêm đường mới **song song**, đường cũ (`GET /api/v1/suppliers?companyId=&keyword=`, `PATCH /api/v1/suppliers/{id}/deactivate`) **vẫn chạy** vì luồng mua hàng đang dùng. Response Supplier có thêm `companyCode`, `taxCode`; giới hạn Supplier khác Customer: `phone` ≤ 60, `address` ≤ 4000 (Customer: 255 / 500) | Khác nhỏ (2 field thừa, 2 giới hạn) |
| Q2 | Bốn mã quyền | **Có**: `PERM_CUSTOMER_READ`, `PERM_CUSTOMER_MANAGE` mới; `PERM_SUPPLIER_*` đã có. Cấp sẵn cho **ADMIN + MANAGER** (sao chép đúng cách cấp quyền Supplier); **OPERATOR không có** | Không |
| Q3 | PATCH: bỏ field = giữ, `null` = xoá | **Không theo đề xuất.** Quy ước của BE: **bỏ field hoặc `null` = giữ nguyên; chuỗi rỗng `""` = xoá**. `name` không xoá được. Lý do: toàn hệ thống đang dùng quy ước này; đổi riêng cho hai endpoint sẽ làm một API có hai nghĩa của `null`. Mẫu thật ở §4.1 (gửi `"notes":""` ⇒ `notes` thành `null`; gửi `"contactName":null` ⇒ giữ nguyên) | **Có** |
| Q4 | Chặn ngừng nhà máy còn kho ACTIVE | **Có** — **422** `ACTIVE_CHILDREN_EXIST` (không phải 409 `ACTIVE_CHILDREN`). Không cascade | Khác mã/HTTP |
| Q5 | Ngừng kho không tự chuyển mặc định, giữ cờ lịch sử | **Có, đúng đề xuất**: ngừng kho **không** đụng tới thiết lập hay cờ của nó. ⚠️ Phần "không còn hợp lệ cho giao dịch mới" (hoạch định có bỏ qua kho ngừng không) **chưa rà trong lượt này** — §7 | Không |
| Q6 | Đơn vị tính bất biến qua PATCH | **Có.** Gửi lại đúng giá trị cũ (không phân biệt hoa thường) ⇒ 200, adapter cũ không vỡ; giá trị khác ⇒ **422** `OPERATION_NOT_ALLOWED` | Không |
| Q7 | PUT upsert theo cặp, kích hoạt lại thiết lập đã ngừng | **Có** — lưu lại một cặp đã ngừng trả **đúng `settingId` cũ** (mẫu §4.5). Chỉ tạo bản ghi mới khi cặp chưa từng tồn tại | Không |
| Q8 | `replaceDefaults` false ⇒ conflict, true ⇒ thay trong transaction tuần tự | **Có, nhưng khi BỎ TRỐNG field thì khác**: bỏ trống ⇒ **tự chuyển cờ** như hành vi cũ (để client đang chạy không bắt đầu nhận 409); `false` ⇒ **409** `DEFAULT_CONFLICT`, không ghi gì; `true` ⇒ chuyển cờ. Request đồng thời được xếp hàng ở server ⇒ luôn chỉ còn một mặc định. ⇒ **FE luôn gửi tường minh `false` hoặc `true`** | **Có** (giá trị khi bỏ trống) |
| Q9 | Lọc vật tư `search`,`status`,`type`,`lotTracked` ở server | **Có.** Tên tham số chốt là **`type`** (trùng tên field trong response), không phải `itemType` | Không |
| Q10 | Replay cùng `Idempotency-Key` | **Không hỗ trợ ở các endpoint danh mục** — header được **bỏ qua**, không gây lỗi, FE cứ gửi. Thay vào đó: tạo lặp lại bị chặn bởi mã duy nhất ⇒ **409** `RESOURCE_ALREADY_EXISTS` (FE refetch theo mã để lấy bản đã tạo); PATCH/activate/deactivate gửi lại cho cùng kết quả | **Có** |
| Q11 | Mã/tên Plant/Warehouse tối thiểu 3 | **BE không áp tối thiểu 3** — chỉ "không rỗng", mã ≤ 100, tên ≤ 255. Giữ tối thiểu 3 là quyết định giao diện của FE; BE **không** thêm vì sẽ từ chối dữ liệu hiện đang hợp lệ | **Có** |
| Q12 | Thập phân NUMERIC(19,6), làm tròn HALF_UP 6 số lẻ | Lưu **NUMERIC(19,6)** — **đúng**. Nhưng trên wire là **JSON number, không phải chuỗi**, và **số chữ số thập phân không cố định**: response của PUT lặp lại đúng cách viết trong request (`"reorderPoint": 100`), còn GET trả 6 số lẻ (`"reorderPoint": 100.000000`). FE parse như number là đúng ở cả hai. **FE gửi tối đa 6 số lẻ** (đã làm tròn sẵn như hiện tại) — BE không có bước làm tròn riêng | **Có** (kiểu dữ liệu) |
| Q13 | Mã/HTTP §4 hay bảng tương đương | **Dùng mã hiện có của BE** — bảng đối chiếu đầy đủ ở §5 | **Có** |
| Q14 | Mã duy nhất: Plant/Item/đối tác theo công ty, Warehouse theo nhà máy, UOM toàn hệ thống | **Có, đúng đề xuất** — kể cả bản ghi đã ngừng vẫn giữ mã của nó | Không |
| Q15 | Phạm vi GLOBAL tổ chức/UOM, vật tư theo nhà máy, đối tác theo công ty | **Giữ nguyên như hiện tại**; chính sách GLOBAL chung chờ chốt cùng giai đoạn 1. ⚠️ Thiết lập kho dùng **`PERM_INVENTORY_READ` / `PERM_INVENTORY_MANAGE`**, **không** phải `PERM_ITEM_*` như bảng 1.5 của FE. Giới hạn đã biết: §6 | Khác mã quyền thiết lập kho |
| Q16 | Kích hoạt lại kho không tự khôi phục mặc định | **Đúng** — kích hoạt kho không đụng thiết lập. Nếu trong lúc kho ngừng FE đã chuyển mặc định sang kho khác thì cờ của kho cũ đã bị gỡ ngay lúc chuyển (thiết lập của kho đang ngừng **vẫn** được tính là đang giữ vai trò, nên FE sẽ thấy `DEFAULT_CONFLICT` nêu tên kho đó) ⇒ không bao giờ có hai mặc định. Kết luận từ đọc mã, **chưa đo live** | Không |

---

## §1 — Nguyên nhân thật

- **Từ bao giờ:** các lỗi ở §0 tồn tại từ khi từng endpoint ra đời — không phải hồi quy gần đây. Lỗi R5 và R8 có từ
  lúc có màn hình thiết lập kho; lỗi bộ lọc tổ chức (R10) có từ khi có danh sách nhà máy/kho.
- **Phạm vi:** đã rà **đúng** các endpoint trong bảng §3.3 + §3.4 của FE. Các danh sách khác của hệ thống (lệnh sản
  xuất, mua hàng, …) **không** nằm trong lượt rà này.
- **Dữ liệu cũ:** **không** bị sửa. Nhà máy đã lưu múi giờ sai, vật tư đã lưu đơn vị không có trong danh mục UOM,
  nhà máy đã ngừng mà còn kho — đều giữ nguyên; quy tắc mới chỉ áp cho lần ghi tiếp theo. Riêng vật tư cũ có đơn vị
  ngoài danh mục: sửa tên vẫn được, miễn gửi lại đúng đơn vị cũ.

---

## §2 — Thay đổi contract

### 2.1 Endpoint mới

| Endpoint (đầy đủ) | Quyền |
|---|---|
| `GET /api/v1/companies/{companyId}/customers?search=&status=&page=0&size=20&sortBy=code&sortDir=asc` | `PERM_CUSTOMER_READ` |
| `POST /api/v1/companies/{companyId}/customers` | `PERM_CUSTOMER_MANAGE` |
| `GET /api/v1/customers/{customerId}` | `PERM_CUSTOMER_READ` |
| `PATCH /api/v1/customers/{customerId}` | `PERM_CUSTOMER_MANAGE` |
| `POST /api/v1/customers/{customerId}/activate` · `POST /api/v1/customers/{customerId}/deactivate` | `PERM_CUSTOMER_MANAGE` |
| `GET /api/v1/companies/{companyId}/suppliers?search=&status=&page=0&size=20&sortBy=code&sortDir=asc` | `PERM_SUPPLIER_READ` |
| `POST /api/v1/companies/{companyId}/suppliers` | `PERM_SUPPLIER_MANAGE` |
| `POST /api/v1/suppliers/{supplierId}/activate` · `POST /api/v1/suppliers/{supplierId}/deactivate` | `PERM_SUPPLIER_MANAGE` |

`GET /api/v1/suppliers/{supplierId}` và `PATCH /api/v1/suppliers/{supplierId}` **đã có từ trước**, dùng luôn.

### 2.2 Field và tham số mới

| Tên | Kiểu | Nullable? | Nguồn giá trị | Ví dụ thật |
|---|---|---|---|---|
| Customer (toàn bộ DTO) `customerId, companyId, code, name, contactName, email, phone, address, notes, status, createdAt, updatedAt` | UUID / string / enum `ACTIVE`·`INACTIVE` / ISO instant | `contactName`, `email`, `phone`, `address`, `notes`: **có**. Còn lại: không (`updatedAt` luôn có giá trị, bằng `createdAt` lúc vừa tạo) | `code` = input sau trim + viết hoa; `companyId` lấy từ path, không đổi được | §4.1 |
| Supplier `contactName` (request + response, **cuối object**) | string ≤ 255 | Có | Form | `"Supplier Contact"` |
| Supplier `notes` (request + response, **cuối object**) | string ≤ 500 | Có | Form | `"net 30"` |
| Thiết lập kho — request `replaceDefaults` | boolean | Có (bỏ trống được) | Ý định người dùng sau hộp xác nhận | `false` / `true` |
| Thiết lập kho — query `plantId` | UUID | Có | Nhà máy đang chọn | §4.5 |
| Thiết lập kho — header `X-Plant-Id` trên `GET`, `PUT`, `DELETE` | UUID | Có | Nhà máy đang chọn; **phải trùng** nhà máy của kho / `plantId` | §4.5 |
| `GET .../plants`, `GET .../warehouses` — query `search`, `status` | string / `ACTIVE`·`INACTIVE` | Có | `search` khớp một phần mã hoặc tên, không phân biệt hoa thường | §4.4 |
| `GET .../items` — query `status`, `type`, `lotTracked` | enum / enum / boolean | Có | `type` ∈ `RAW_MATERIAL`, `WIP`, `FINISHED_GOOD`, `CONSUMABLE`, `SERVICE` | §4.3 |

### 2.3 Field / tham số bị bỏ

**Không có.** Mọi field và đường dẫn cũ vẫn còn.

---

## §3 — Breaking change

**`Wire: CÓ`** — 7 thay đổi hành vi. **Không** có field nào bị bỏ, **không** đổi kiểu dữ liệu field nào,
**không** đổi giá trị mặc định của query param.

| # | Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|---|
| 1 | `DELETE /api/v1/plants/{plantId}` | Nhà máy còn kho `ACTIVE` | 200, kho bị bỏ lại | **422 `ACTIVE_CHILDREN_EXIST`** | Chặn mới | Bắt mã này, hiển thị "ngừng kho trước" |
| 2 | `POST /api/v1/companies/{companyId}/plants`, `PATCH /api/v1/plants/{plantId}` | `timezone` không phải mã IANA hợp lệ | 201/200, lưu chuỗi rác | **400 `VALIDATION_ERROR`**, `errors[0].field = "timezone"` | Chặn mới | Hiển thị lỗi tại ô múi giờ (form đã bind theo `errors[]` thì không phải sửa) |
| 3 | `POST /api/v1/companies/{companyId}/items` | `unit` không có trong danh mục UOM / UOM đã ngừng | 201 | **404 `ENTITY_NOT_FOUND`** / **422 `RESOURCE_INACTIVE`** | Chặn mới | Chỉ cho chọn đơn vị từ danh mục UOM đang hoạt động (FE đã làm) |
| 4 | `PATCH /api/v1/items/{itemId}` | `unit` khác giá trị đang lưu | 200, đổi đơn vị | **422 `OPERATION_NOT_ALLOWED`** | Chặn mới | Khoá ô đơn vị (FE đã khoá); vẫn được gửi lại giá trị cũ |
| 5 | `PUT /api/v1/inventory/item-warehouse-settings`, `DELETE .../{settingId}`, `GET ...` | Header `X-Plant-Id` lệch nhà máy / sai định dạng | Header bị bỏ qua | **409 `STATE_CONFLICT`** / **400 `FIELD_FORMAT_INVALID`** | Chặn mới | Gửi đúng nhà máy đang chọn (FE đang làm vậy); không gửi header thì không bị kiểm |
| 6 | `PUT /api/v1/inventory/item-warehouse-settings` | Kho thuộc nhà máy đã ngừng | 200 | **422 `RESOURCE_INACTIVE`** | Chặn mới | Không cho chọn kho của nhà máy đã ngừng |
| 7 🆕 v2 | **Mọi endpoint nhận body JSON** | Số có dấu chấm gửi vào field **số nguyên**: `leadTimeDays`, `capacityUnits`, `lineNo`, `sequence`, `priority`, `headerRowIndex`, `firstDataRowIndex`, `expectedVersion` | 200, **phần lẻ bị cắt rồi lưu** (`1.5` ⇒ `1`) | **400 `VALIDATION_ERROR`**, `errors[0].field` = tên field (field lồng: `lines[0].lineNo`), `message` = `"must be a whole number"`. **Không ghi gì** | Chặn mới | Gửi số nguyên **không có dấu chấm**. 🔴 **`5.0` cũng bị từ chối** — nếu form dùng `Number(input)` rồi `JSON.stringify` thì vẫn ra `5`, nhưng nếu ghép chuỗi hoặc dùng `toFixed` thì sẽ ra `"5.0"` và bị 400. Field **thập phân** (`safetyStock`, `reorderPoint`, `quantity`, `quantityPer`…) **không** bị ảnh hưởng |

**Thay đổi kết quả, không phá client** (khai để FE không bất ngờ):

| Endpoint | Thay đổi |
|---|---|
| `GET .../plants`, `GET .../warehouses` | `search`/`status` FE vẫn gửi nay **thật sự lọc** — danh sách live sẽ ngắn hơn trước, đúng như FE kỳ vọng |
| Mọi danh sách trong §2 | Thứ tự mặc định **không đổi**; chỉ thêm sắp xếp phụ theo ID ⇒ các dòng trùng khoá sắp xếp nay có thứ tự cố định giữa các trang |
| `PUT` thiết lập kho cho cặp đã ngừng | Trả lại **`settingId` cũ** thay vì tạo bản ghi mới |
| `DELETE` thiết lập kho | Thiết lập bị ngừng nay có `defaultSupply = false`, `defaultOutput = false` |

**Thứ tự deploy:** không ràng buộc. Cả 7 mục đều là "dữ liệu sai bắt đầu bị từ chối"; adapter FE hiện tại vốn đã chặn
phần lớn các trường hợp đó ở giao diện.

---

## §4 — Cách dùng (mẫu thật)

### 4.1 Khách hàng

**Tạo** — mã có khoảng trắng và chữ thường được chuẩn hoá:

```http
POST /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/customers
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":" partner-01 ","name":"Demo Customer","contactName":"Demo Contact","email":"buyer@example.com","phone":"0000123456","address":null,"notes":"first order"}
```

HTTP **201** · `X-Trace-Id: 7237dd7a3d42478b`

```json
{
  "code": "SUCCESS",
  "result": {
    "customerId": "82fb4914-4095-46b0-aeef-7f7a641afeff",
    "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
    "code": "PARTNER-01",
    "name": "Demo Customer",
    "contactName": "Demo Contact",
    "email": "buyer@example.com",
    "phone": "0000123456",
    "address": null,
    "notes": "first order",
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:09:58.811825900Z",
    "updatedAt": "2026-10-14T05:09:58.811825900Z"
  },
  "message": "Created successfully"
}
```

**Lỗi hay gặp — trùng mã trong cùng công ty:**

```http
POST /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/customers
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":"PARTNER-01","name":"Duplicate"}
```

HTTP **409** · `X-Trace-Id: 596056335c6c4be2`

```json
{
  "code": "RESOURCE_ALREADY_EXISTS",
  "message": "Customer code already exists: PARTNER-01"
}
```

**Lỗi theo field — email sai:**

```http
POST /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/customers
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":"PARTNER-02","name":"Bad email","email":"not-an-email"}
```

HTTP **400** · `X-Trace-Id: 2e0905bb51ce410d`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "email: must be a well-formed email address",
  "errors": [
    {
      "field": "email",
      "message": "must be a well-formed email address"
    }
  ]
}
```

**Danh sách:**

```http
GET /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/customers?search=partner&status=ACTIVE
Authorization: Bearer <redacted>
```

HTTP **200** · `X-Trace-Id: 13fe831fe07c4f56`

```json
{
  "code": "SUCCESS",
  "result": {
    "content": [
      {
        "customerId": "82fb4914-4095-46b0-aeef-7f7a641afeff",
        "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
        "code": "PARTNER-01",
        "name": "Demo Customer",
        "contactName": "Demo Contact",
        "email": "buyer@example.com",
        "phone": "0000123456",
        "address": null,
        "notes": null,
        "status": "ACTIVE",
        "createdAt": "2026-10-14T05:09:58.811826Z",
        "updatedAt": "2026-10-14T05:10:00.502845Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "message": "OK"
}
```

**PATCH — chuỗi rỗng xoá field, field không gửi thì giữ:**

```http
PATCH /api/v1/customers/82fb4914-4095-46b0-aeef-7f7a641afeff
Authorization: Bearer <redacted>
Content-Type: application/json

{"notes":""}
```

HTTP **200** · `X-Trace-Id: d97ba708ce31494c`

```json
{
  "code": "SUCCESS",
  "result": {
    "customerId": "82fb4914-4095-46b0-aeef-7f7a641afeff",
    "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
    "code": "PARTNER-01",
    "name": "Demo Customer",
    "contactName": "Demo Contact",
    "email": "buyer@example.com",
    "phone": "0000123456",
    "address": null,
    "notes": null,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:09:58.811826Z",
    "updatedAt": "2026-10-14T05:09:58.811826Z"
  },
  "message": "OK"
}
```

**PATCH — `null` cũng là giữ nguyên (khác đề xuất Q3); `code` gửi kèm bị bỏ qua:**

```http
PATCH /api/v1/customers/82fb4914-4095-46b0-aeef-7f7a641afeff
Authorization: Bearer <redacted>
Content-Type: application/json

{"contactName":null,"code":"HACKED"}
```

HTTP **200** · `X-Trace-Id: d5272c5588c84434`

```json
{
  "code": "SUCCESS",
  "result": {
    "customerId": "82fb4914-4095-46b0-aeef-7f7a641afeff",
    "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
    "code": "PARTNER-01",
    "name": "Demo Customer",
    "contactName": "Demo Contact",
    "email": "buyer@example.com",
    "phone": "0000123456",
    "address": null,
    "notes": null,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:09:58.811826Z",
    "updatedAt": "2026-10-14T05:10:00.502845Z"
  },
  "message": "OK"
}
```

**Ngừng** (kích hoạt lại: `POST /api/v1/customers/{customerId}/activate`, response cùng hình dạng, `status: "ACTIVE"`):

```http
POST /api/v1/customers/82fb4914-4095-46b0-aeef-7f7a641afeff/deactivate
Authorization: Bearer <redacted>
```

HTTP **200** · `X-Trace-Id: 94961acb9cdd4153`

```json
{
  "code": "SUCCESS",
  "result": {
    "customerId": "82fb4914-4095-46b0-aeef-7f7a641afeff",
    "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
    "code": "PARTNER-01",
    "name": "Demo Customer",
    "contactName": "Demo Contact",
    "email": "buyer@example.com",
    "phone": "0000123456",
    "address": null,
    "notes": null,
    "status": "INACTIVE",
    "createdAt": "2026-10-14T05:09:58.811826Z",
    "updatedAt": "2026-10-14T05:10:00.502845Z"
  },
  "message": "OK"
}
```

**ID không tồn tại trả 403, không phải 404** (xem Q13 ở §5):

```http
GET /api/v1/customers/00000000-0000-4000-8000-000000000000
Authorization: Bearer <redacted>
```

HTTP **403** · `X-Trace-Id: c60407ffabaf4e49`

```json
{
  "code": "PERMISSION_DENIED",
  "message": "Insufficient permissions"
}
```

### 4.2 Nhà cung cấp

**Tạo theo công ty — cùng mã với Khách hàng ở trên vẫn hợp lệ:**

```http
POST /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/suppliers
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":"PARTNER-01","name":"Demo Supplier","contactName":"Supplier Contact","notes":"net 30"}
```

HTTP **201** · `X-Trace-Id: 251227bcef214d61`

```json
{
  "code": "SUCCESS",
  "result": {
    "supplierId": "84456720-9b8e-44c5-844c-935b3b6952ca",
    "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
    "companyCode": "G2SMK120952",
    "code": "PARTNER-01",
    "name": "Demo Supplier",
    "email": null,
    "phone": null,
    "address": null,
    "taxCode": null,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:09:59.504631200Z",
    "updatedAt": "2026-10-14T05:09:59.504631200Z",
    "contactName": "Supplier Contact",
    "notes": "net 30"
  },
  "message": "Created successfully"
}
```

**Lỗi hay gặp — Supplier thứ hai cùng mã sau khi chuẩn hoá:**

```http
POST /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/suppliers
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":" partner-01 ","name":"Duplicate supplier"}
```

HTTP **409** · `X-Trace-Id: 45c97768f7b24dd7`

```json
{
  "code": "RESOURCE_ALREADY_EXISTS",
  "message": "Supplier code already exists: PARTNER-01"
}
```

**Danh sách theo công ty:**

```http
GET /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/suppliers?search=partner&status=ACTIVE
Authorization: Bearer <redacted>
```

HTTP **200** · `X-Trace-Id: 20e681113ed44cf9`

```json
{
  "code": "SUCCESS",
  "result": {
    "content": [
      {
        "supplierId": "84456720-9b8e-44c5-844c-935b3b6952ca",
        "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
        "companyCode": "G2SMK120952",
        "code": "PARTNER-01",
        "name": "Demo Supplier",
        "email": null,
        "phone": null,
        "address": null,
        "taxCode": null,
        "status": "ACTIVE",
        "createdAt": "2026-10-14T05:09:59.504631Z",
        "updatedAt": "2026-10-14T05:09:59.504631Z",
        "contactName": "Supplier Contact",
        "notes": "net 30"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "message": "OK"
}
```

**Kích hoạt lại** (ngừng: `POST /api/v1/suppliers/{supplierId}/deactivate`, cùng hình dạng):

```http
POST /api/v1/suppliers/84456720-9b8e-44c5-844c-935b3b6952ca/activate
Authorization: Bearer <redacted>
```

HTTP **200** · `X-Trace-Id: 39964e99eccb439b`

```json
{
  "code": "SUCCESS",
  "result": {
    "supplierId": "84456720-9b8e-44c5-844c-935b3b6952ca",
    "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
    "companyCode": "G2SMK120952",
    "code": "PARTNER-01",
    "name": "Demo Supplier",
    "email": null,
    "phone": null,
    "address": null,
    "taxCode": null,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:09:59.504631Z",
    "updatedAt": "2026-10-14T05:10:02.540581Z",
    "contactName": "Supplier Contact",
    "notes": "net 30"
  },
  "message": "OK"
}
```

### 4.3 Vật tư

**Tạo — `unit` gửi `kg` được lưu `KG`:**

```http
POST /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/items
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":"RM120952","name":"Smoke material","type":"RAW_MATERIAL","unit":"kg","lotTracked":true,"serialTracked":false}
```

HTTP **201** · `X-Trace-Id: 5ed5026a8dea4387`

```json
{
  "code": "SUCCESS",
  "result": {
    "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
    "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
    "code": "RM120952",
    "name": "Smoke material",
    "type": "RAW_MATERIAL",
    "unit": "KG",
    "lotTracked": true,
    "serialTracked": false,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:09:57.330417400Z",
    "updatedAt": "2026-10-14T05:09:57.330417400Z"
  },
  "message": "Created successfully"
}
```

**Lỗi — đơn vị không có trong danh mục UOM:**

```http
POST /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/items
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":"RM120952","name":"Smoke material","type":"RAW_MATERIAL","unit":"NOPE120952","lotTracked":true,"serialTracked":false}
```

HTTP **404** · `X-Trace-Id: 068c3c1c56464335`

```json
{
  "code": "ENTITY_NOT_FOUND",
  "message": "UOM code not found: NOPE120952"
}
```

**Lỗi — đơn vị đã ngừng hoạt động:**

```http
POST /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/items
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":"RMX120952","name":"Should fail","type":"RAW_MATERIAL","unit":"ZZSMK120952","lotTracked":false,"serialTracked":false}
```

HTTP **422** · `X-Trace-Id: ad04f38b2ab34930`

```json
{
  "code": "RESOURCE_INACTIVE",
  "message": "Inactive UOM cannot be used for a new item: ZZSMK120952"
}
```

**Lỗi — PATCH đổi đơn vị:**

```http
PATCH /api/v1/items/fd3ee0ca-8d6b-4287-a276-23271315d592
Authorization: Bearer <redacted>
Content-Type: application/json

{"name":"Smoke material","unit":"PCS"}
```

HTTP **422** · `X-Trace-Id: 046cd0471a8e4335`

```json
{
  "code": "OPERATION_NOT_ALLOWED",
  "message": "Item unit cannot be changed (KG): fd3ee0ca-8d6b-4287-a276-23271315d592"
}
```

**PATCH gửi kèm `code`/`type`/`lotTracked` — bị bỏ qua, không báo lỗi** (R3):

```http
PATCH /api/v1/items/fd3ee0ca-8d6b-4287-a276-23271315d592
Authorization: Bearer <redacted>
Content-Type: application/json

{"name":"Smoke material v2","unit":"KG","code":"HACKED","type":"FINISHED_GOOD","lotTracked":false}
```

HTTP **200** · `X-Trace-Id: 709775530e4e47a1`

```json
{
  "code": "SUCCESS",
  "result": {
    "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
    "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
    "code": "RM120952",
    "name": "Smoke material v2",
    "type": "RAW_MATERIAL",
    "unit": "KG",
    "lotTracked": true,
    "serialTracked": false,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:09:57.330417Z",
    "updatedAt": "2026-10-14T05:09:57.992586Z"
  },
  "message": "OK"
}
```

**Danh sách có bộ lọc mới:**

```http
GET /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/items?type=RAW_MATERIAL&lotTracked=true&status=ACTIVE
Authorization: Bearer <redacted>
```

HTTP **200** · `X-Trace-Id: 5079a4cd5ea44c07`

```json
{
  "code": "SUCCESS",
  "result": {
    "content": [
      {
        "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
        "companyId": "5551c7aa-d993-451c-ab51-483c8c364ba0",
        "code": "RM120952",
        "name": "Smoke material v2",
        "type": "RAW_MATERIAL",
        "unit": "KG",
        "lotTracked": true,
        "serialTracked": false,
        "status": "ACTIVE",
        "createdAt": "2026-10-14T05:09:57.330417Z",
        "updatedAt": "2026-10-14T05:09:57.992586Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "message": "OK"
}
```

### 4.4 Nhà máy và kho

**Múi giờ sai:**

```http
POST /api/v1/companies/5551c7aa-d993-451c-ab51-483c8c364ba0/plants
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":"PL120952","name":"Smoke plant","timezone":"Mars/Olympus"}
```

HTTP **400** · `X-Trace-Id: f1d4966fbf254b7d`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "timezone: must be a valid IANA time zone id, e.g. Asia/Ho_Chi_Minh",
  "errors": [
    {
      "field": "timezone",
      "message": "must be a valid IANA time zone id, e.g. Asia/Ho_Chi_Minh"
    }
  ]
}
```

**Ngừng nhà máy còn kho hoạt động:**

```http
DELETE /api/v1/plants/4aff71c2-73fe-48dd-a73a-9101971e9230
Authorization: Bearer <redacted>
```

HTTP **422** · `X-Trace-Id: 4b6d4631cb9747ee`

```json
{
  "code": "ACTIVE_CHILDREN_EXIST",
  "message": "Plant still has active warehouses; deactivate them first: 4aff71c2-73fe-48dd-a73a-9101971e9230"
}
```

**Tạo kho dưới nhà máy đã ngừng:**

```http
POST /api/v1/plants/6a1e764b-e138-4574-bf42-b3abef6d9452/warehouses
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":"WX120952","name":"Should fail","type":"GENERAL"}
```

HTTP **422** · `X-Trace-Id: 00ca9279d3d1418a`

```json
{
  "code": "RESOURCE_INACTIVE",
  "message": "Cannot create warehouse under inactive plant: 6a1e764b-e138-4574-bf42-b3abef6d9452"
}
```

**Danh sách kho với `search` + `status`** (trước đây hai tham số này bị bỏ qua; `GET /api/v1/companies/{companyId}/plants` nhận cùng hai tham số):

```http
GET /api/v1/plants/4aff71c2-73fe-48dd-a73a-9101971e9230/warehouses?search=raw&status=ACTIVE
Authorization: Bearer <redacted>
```

HTTP **200** · `X-Trace-Id: 21c3a2e97af345ee`

```json
{
  "code": "SUCCESS",
  "result": {
    "content": [
      {
        "warehouseId": "241043be-ed1b-4df1-bd05-b47eeec3665d",
        "plantId": "4aff71c2-73fe-48dd-a73a-9101971e9230",
        "code": "W1120952",
        "name": "Raw store",
        "type": "RAW_MATERIAL",
        "status": "ACTIVE",
        "createdAt": "2026-10-14T05:09:53.569139Z",
        "updatedAt": "2026-10-14T05:09:53.569139Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "message": "OK"
}
```

### 4.5 Thiết lập kho của vật tư

Luồng đúng theo §1.4 bước 5–6 của FE: W1 giữ cả hai mặc định → chuyển mặc định cấp sang W2 → hỏi người dùng → gửi lại.

**Bước 1 — W1 làm mặc định cấp và nhận:**

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
X-Plant-Id: 4aff71c2-73fe-48dd-a73a-9101971e9230
Content-Type: application/json

{"itemId":"fd3ee0ca-8d6b-4287-a276-23271315d592","warehouseId":"241043be-ed1b-4df1-bd05-b47eeec3665d","safetyStock":"75.123456","reorderPoint":"100","leadTimeDays":5,"defaultSupply":true,"defaultOutput":true}
```

HTTP **200** · `X-Trace-Id: 283f7784ed6643ec`

```json
{
  "code": "SUCCESS",
  "result": {
    "settingId": "5af6ce95-3cd1-4952-a34d-67b905ddb98f",
    "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
    "itemCode": "RM120952",
    "itemName": "Smoke material v2",
    "warehouseId": "241043be-ed1b-4df1-bd05-b47eeec3665d",
    "warehouseCode": "W1120952",
    "warehouseName": "Raw store",
    "safetyStock": 75.123456,
    "reorderPoint": 100,
    "leadTimeDays": 5,
    "defaultSupply": true,
    "defaultOutput": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:10:03.098373Z",
    "updatedAt": "2026-10-14T05:10:03.098373Z"
  },
  "message": "OK"
}
```

**Bước 2 — chuyển mặc định cấp sang W2 với `replaceDefaults: false` ⇒ bị từ chối, không ghi gì:**

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
X-Plant-Id: 4aff71c2-73fe-48dd-a73a-9101971e9230
Content-Type: application/json

{"itemId":"fd3ee0ca-8d6b-4287-a276-23271315d592","warehouseId":"8a5d3cbc-6113-49f4-abe7-5f02193e3820","safetyStock":"30","reorderPoint":"50","leadTimeDays":7,"defaultSupply":true,"defaultOutput":false,"replaceDefaults":false}
```

HTTP **409** · `X-Trace-Id: 734d03cbb5c8454e`

```json
{
  "code": "DEFAULT_CONFLICT",
  "message": "Default role already held by warehouse W1120952; resend with replaceDefaults=true to move it"
}
```

**Bước 3 — người dùng xác nhận, gửi lại với `replaceDefaults: true`:**

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
X-Plant-Id: 4aff71c2-73fe-48dd-a73a-9101971e9230
Content-Type: application/json

{"itemId":"fd3ee0ca-8d6b-4287-a276-23271315d592","warehouseId":"8a5d3cbc-6113-49f4-abe7-5f02193e3820","safetyStock":"30","reorderPoint":"50","leadTimeDays":7,"defaultSupply":true,"defaultOutput":false,"replaceDefaults":true}
```

HTTP **200** · `X-Trace-Id: 354c4e838bea4e9b`

```json
{
  "code": "SUCCESS",
  "result": {
    "settingId": "5c1261cc-0421-4ec3-88c4-b56eb221b2e3",
    "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
    "itemCode": "RM120952",
    "itemName": "Smoke material v2",
    "warehouseId": "8a5d3cbc-6113-49f4-abe7-5f02193e3820",
    "warehouseCode": "W2120952",
    "warehouseName": "General store",
    "safetyStock": 30,
    "reorderPoint": 50,
    "leadTimeDays": 7,
    "defaultSupply": true,
    "defaultOutput": false,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:10:04.745596400Z",
    "updatedAt": "2026-10-14T05:10:04.745596400Z"
  },
  "message": "OK"
}
```

**Bước 4 — refetch theo nhà máy: W1 chỉ còn mặc định nhận, W2 giữ mặc định cấp** (mục đích không được yêu cầu không bị đụng tới):

```http
GET /api/v1/inventory/item-warehouse-settings?plantId=4aff71c2-73fe-48dd-a73a-9101971e9230
Authorization: Bearer <redacted>
X-Plant-Id: 4aff71c2-73fe-48dd-a73a-9101971e9230
```

HTTP **200** · `X-Trace-Id: e82423638c77423f`

```json
{
  "code": "SUCCESS",
  "result": {
    "content": [
      {
        "settingId": "5c1261cc-0421-4ec3-88c4-b56eb221b2e3",
        "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
        "itemCode": "RM120952",
        "itemName": "Smoke material v2",
        "warehouseId": "8a5d3cbc-6113-49f4-abe7-5f02193e3820",
        "warehouseCode": "W2120952",
        "warehouseName": "General store",
        "safetyStock": 30.000000,
        "reorderPoint": 50.000000,
        "leadTimeDays": 7,
        "defaultSupply": true,
        "defaultOutput": false,
        "status": "ACTIVE",
        "createdAt": "2026-10-14T05:10:04.745596Z",
        "updatedAt": "2026-10-14T05:10:04.745596Z"
      },
      {
        "settingId": "5af6ce95-3cd1-4952-a34d-67b905ddb98f",
        "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
        "itemCode": "RM120952",
        "itemName": "Smoke material v2",
        "warehouseId": "241043be-ed1b-4df1-bd05-b47eeec3665d",
        "warehouseCode": "W1120952",
        "warehouseName": "Raw store",
        "safetyStock": 75.123456,
        "reorderPoint": 100.000000,
        "leadTimeDays": 5,
        "defaultSupply": false,
        "defaultOutput": true,
        "status": "ACTIVE",
        "createdAt": "2026-10-14T05:10:03.098373Z",
        "updatedAt": "2026-10-14T05:10:04.741958Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "message": "OK"
}
```

**Header `X-Plant-Id` lệch nhà máy:**

```http
GET /api/v1/inventory/item-warehouse-settings?plantId=4aff71c2-73fe-48dd-a73a-9101971e9230
Authorization: Bearer <redacted>
X-Plant-Id: 5551c7aa-d993-451c-ab51-483c8c364ba0
```

HTTP **409** · `X-Trace-Id: 1ad9f066d40f4b32`

```json
{
  "code": "STATE_CONFLICT",
  "message": "X-Plant-Id does not match the plant referenced by this request"
}
```

**Header `X-Plant-Id` sai định dạng:**

```http
GET /api/v1/inventory/item-warehouse-settings?plantId=4aff71c2-73fe-48dd-a73a-9101971e9230
Authorization: Bearer <redacted>
X-Plant-Id: not-a-uuid
```

HTTP **400** · `X-Trace-Id: cceee559464c4447`

```json
{
  "code": "FIELD_FORMAT_INVALID",
  "message": "X-Plant-Id must be a valid UUID"
}
```

**Ngừng một thiết lập:**

```http
DELETE /api/v1/inventory/item-warehouse-settings/5c1261cc-0421-4ec3-88c4-b56eb221b2e3
Authorization: Bearer <redacted>
X-Plant-Id: 4aff71c2-73fe-48dd-a73a-9101971e9230
```

HTTP **200** · `X-Trace-Id: 92a29f78dcb5403d`

```json
{
  "code": "SUCCESS",
  "message": "Item warehouse setting deactivated successfully"
}
```

**Lưu lại cặp vừa ngừng ⇒ kích hoạt lại đúng bản ghi cũ** (`settingId` trùng với bước 3; hai cờ đã được xoá lúc ngừng):

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
X-Plant-Id: 4aff71c2-73fe-48dd-a73a-9101971e9230
Content-Type: application/json

{"itemId":"fd3ee0ca-8d6b-4287-a276-23271315d592","warehouseId":"8a5d3cbc-6113-49f4-abe7-5f02193e3820","safetyStock":"0.000001","reorderPoint":"0","leadTimeDays":0}
```

HTTP **200** · `X-Trace-Id: 8945bd8ff92d4f33`

```json
{
  "code": "SUCCESS",
  "result": {
    "settingId": "5c1261cc-0421-4ec3-88c4-b56eb221b2e3",
    "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
    "itemCode": "RM120952",
    "itemName": "Smoke material v2",
    "warehouseId": "8a5d3cbc-6113-49f4-abe7-5f02193e3820",
    "warehouseCode": "W2120952",
    "warehouseName": "General store",
    "safetyStock": 0.000001,
    "reorderPoint": 0,
    "leadTimeDays": 0,
    "defaultSupply": false,
    "defaultOutput": false,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:10:04.745596Z",
    "updatedAt": "2026-10-14T05:10:06.053673Z"
  },
  "message": "OK"
}
```

**Đọc lại — `0.000001` và `75.123456` giữ nguyên, GET trả đủ 6 số lẻ** (so với response PUT ở trên chỉ lặp lại cách viết trong request):

```http
GET /api/v1/inventory/item-warehouse-settings?plantId=4aff71c2-73fe-48dd-a73a-9101971e9230&itemId=fd3ee0ca-8d6b-4287-a276-23271315d592
Authorization: Bearer <redacted>
X-Plant-Id: 4aff71c2-73fe-48dd-a73a-9101971e9230
```

HTTP **200** · `X-Trace-Id: 65e8351eb24346a5`

```json
{
  "code": "SUCCESS",
  "result": {
    "content": [
      {
        "settingId": "5c1261cc-0421-4ec3-88c4-b56eb221b2e3",
        "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
        "itemCode": "RM120952",
        "itemName": "Smoke material v2",
        "warehouseId": "8a5d3cbc-6113-49f4-abe7-5f02193e3820",
        "warehouseCode": "W2120952",
        "warehouseName": "General store",
        "safetyStock": 0.000001,
        "reorderPoint": 0.000000,
        "leadTimeDays": 0,
        "defaultSupply": false,
        "defaultOutput": false,
        "status": "ACTIVE",
        "createdAt": "2026-10-14T05:10:04.745596Z",
        "updatedAt": "2026-10-14T05:10:06.313548Z"
      },
      {
        "settingId": "5af6ce95-3cd1-4952-a34d-67b905ddb98f",
        "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
        "itemCode": "RM120952",
        "itemName": "Smoke material v2",
        "warehouseId": "241043be-ed1b-4df1-bd05-b47eeec3665d",
        "warehouseCode": "W1120952",
        "warehouseName": "Raw store",
        "safetyStock": 75.123456,
        "reorderPoint": 100.000000,
        "leadTimeDays": 5,
        "defaultSupply": false,
        "defaultOutput": true,
        "status": "ACTIVE",
        "createdAt": "2026-10-14T05:10:03.098373Z",
        "updatedAt": "2026-10-14T05:10:04.741958Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "message": "OK"
}
```

**Lỗi theo field — số âm:**

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
X-Plant-Id: 4aff71c2-73fe-48dd-a73a-9101971e9230
Content-Type: application/json

{"itemId":"fd3ee0ca-8d6b-4287-a276-23271315d592","warehouseId":"241043be-ed1b-4df1-bd05-b47eeec3665d","safetyStock":"-1","reorderPoint":"100","leadTimeDays":5}
```

HTTP **400** · `X-Trace-Id: ef4f7f1d61de4d0e`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "safetyStock: must be greater than or equal to 0",
  "errors": [
    {
      "field": "safetyStock",
      "message": "must be greater than or equal to 0"
    }
  ]
}
```

### 4.6 Số lẻ gửi vào field số nguyên *(🆕 v2 — FE A10)*

**Trước khi sửa** (lượt đo 12:09, bản build cũ) — `1.5` được nhận, response báo `"leadTimeDays": 1`, và đó là giá
trị đã **lưu**:

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
X-Plant-Id: 4aff71c2-73fe-48dd-a73a-9101971e9230
Content-Type: application/json

{"itemId":"fd3ee0ca-8d6b-4287-a276-23271315d592","warehouseId":"241043be-ed1b-4df1-bd05-b47eeec3665d","safetyStock":"75.123456","reorderPoint":"100","leadTimeDays":1.5,"defaultSupply":true,"defaultOutput":true}
```

HTTP **200** · `X-Trace-Id: 4efc9c9fb6144584`

```json
{
  "code": "SUCCESS",
  "result": {
    "settingId": "5af6ce95-3cd1-4952-a34d-67b905ddb98f",
    "itemId": "fd3ee0ca-8d6b-4287-a276-23271315d592",
    "itemCode": "RM120952",
    "itemName": "Smoke material v2",
    "warehouseId": "241043be-ed1b-4df1-bd05-b47eeec3665d",
    "warehouseCode": "W1120952",
    "warehouseName": "Raw store",
    "safetyStock": 75.123456,
    "reorderPoint": 100,
    "leadTimeDays": 1,
    "defaultSupply": true,
    "defaultOutput": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T05:10:03.098373Z",
    "updatedAt": "2026-10-14T05:10:03.098373Z"
  },
  "message": "OK"
}
```

**Sau khi sửa** (lượt đo thứ hai, bản build mới — mốc nhận biết: cùng request này nay **không** còn trả 200):

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
X-Plant-Id: 53b4cc8f-b877-4e2a-83f2-58bd07f6962c
Content-Type: application/json

{"itemId":"572e6513-e705-4b72-9254-532ec01253f7","warehouseId":"a7f0db7d-c458-4947-a52b-4945a9d01188","safetyStock":"75.123456","reorderPoint":"100","leadTimeDays":1.5}
```

HTTP **400** · `X-Trace-Id: a673e375e001420c`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "leadTimeDays: must be a whole number",
  "errors": [
    {
      "field": "leadTimeDays",
      "message": "must be a whole number"
    }
  ]
}
```

**`5.0` cũng bị từ chối** — Jackson coi mọi số có dấu chấm là số thực, không phân biệt `5.0` với `5.5`:

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
X-Plant-Id: 53b4cc8f-b877-4e2a-83f2-58bd07f6962c
Content-Type: application/json

{"itemId":"572e6513-e705-4b72-9254-532ec01253f7","warehouseId":"a7f0db7d-c458-4947-a52b-4945a9d01188","safetyStock":"75.123456","reorderPoint":"100","leadTimeDays":5.0}
```

HTTP **400** · `X-Trace-Id: 3d028770ac504260`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "leadTimeDays: must be a whole number",
  "errors": [
    {
      "field": "leadTimeDays",
      "message": "must be a whole number"
    }
  ]
}
```

**Hai lần bị từ chối không ghi gì** — danh sách của nhà máy vẫn rỗng:

```http
GET /api/v1/inventory/item-warehouse-settings?plantId=53b4cc8f-b877-4e2a-83f2-58bd07f6962c
Authorization: Bearer <redacted>
X-Plant-Id: 53b4cc8f-b877-4e2a-83f2-58bd07f6962c
```

HTTP **200** · `X-Trace-Id: 3e8da54ea58f4483`

```json
{
  "code": "SUCCESS",
  "result": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  },
  "message": "OK"
}
```

**Số nguyên không dấu chấm vẫn chạy bình thường:**

```http
PUT /api/v1/inventory/item-warehouse-settings
Authorization: Bearer <redacted>
X-Plant-Id: 53b4cc8f-b877-4e2a-83f2-58bd07f6962c
Content-Type: application/json

{"itemId":"572e6513-e705-4b72-9254-532ec01253f7","warehouseId":"a7f0db7d-c458-4947-a52b-4945a9d01188","safetyStock":"75.123456","reorderPoint":"100","leadTimeDays":5}
```

HTTP **200** · `X-Trace-Id: 3a545c7aedaa4045`

```json
{
  "code": "SUCCESS",
  "result": {
    "settingId": "128cd128-2af8-48e9-8e19-8b61b1e1c9f4",
    "itemId": "572e6513-e705-4b72-9254-532ec01253f7",
    "itemCode": "IF191258",
    "itemName": "Float item",
    "warehouseId": "a7f0db7d-c458-4947-a52b-4945a9d01188",
    "warehouseCode": "WF191258",
    "warehouseName": "Float store",
    "safetyStock": 75.123456,
    "reorderPoint": 100,
    "leadTimeDays": 5,
    "defaultSupply": false,
    "defaultOutput": false,
    "status": "ACTIVE",
    "createdAt": "2026-10-14T12:13:02.403536900Z",
    "updatedAt": "2026-10-14T12:13:02.403536900Z"
  },
  "message": "OK"
}
```

**Áp cho mọi endpoint, không riêng thiết lập kho** — ví dụ `capacityUnits` của tổ sản xuất:

```http
POST /api/v1/plants/53b4cc8f-b877-4e2a-83f2-58bd07f6962c/work-centers
Authorization: Bearer <redacted>
Content-Type: application/json

{"code":"WCF191258","name":"Float WC","capacityUnitType":"LINE","capacityUnits":1.5}
```

HTTP **400** · `X-Trace-Id: 55d8a07171bd4b90`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "capacityUnits: must be a whole number",
  "errors": [
    {
      "field": "capacityUnits",
      "message": "must be a whole number"
    }
  ]
}
```

---

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — dùng cùng một envelope:

```json
{
  "code":    "SUCCESS",
  "result":  { },
  "message": "OK",
  "errors":  [ { "field": "quantity", "message": "must be greater than 0" } ]
}
```

- `code` là tên hằng, FE rẽ nhánh theo nó — **không** parse `message` (văn bản tiếng Anh, có thể đổi).
- `errors` chỉ có khi lỗi theo field; một field có thể xuất hiện nhiều lần.
- Khi lỗi, `result` **vắng mặt** (không phải `"result": null`). `DELETE` thành công cũng không có `result`.
- Danh sách: `result` = `{ content, page, size, totalElements, totalPages, first, last }`; `page` bắt đầu từ 0,
  `size` mặc định 20, tối đa 100 (gửi lớn hơn bị kẹp về 100, không báo lỗi).

### 5.1 Đối chiếu mã lỗi FE đề xuất (§4 của FE) ↔ mã thật (Q13)

| Tình huống | FE đề xuất | **Mã thật** | **HTTP thật** | `errors[]`? |
|---|---|---|---|---|
| Thiếu field / sai định dạng / enum sai / số âm | `VALIDATION_ERROR` 400 | `VALIDATION_ERROR` | 400 | Có |
| 🆕 v2 — Số có dấu chấm cho field số nguyên (`1.5`, `5.0`) | `VALIDATION_ERROR` 400 | `VALIDATION_ERROR`, `message` = `"must be a whole number"` | 400 | **Có** (field lồng: `lines[0].lineNo`) |
| JSON hỏng (thiếu ngoặc, cắt giữa chừng) | — | `VALIDATION_ERROR`, `"Request body is missing or not valid JSON"` | 400 | **Không** — không có field nào để chỉ |
| Múi giờ sai | `INVALID_TIMEZONE` 400 | **`VALIDATION_ERROR`**, `field = "timezone"` | 400 | Có |
| Mã trùng sau chuẩn hoá | `DUPLICATE_CODE` 409 | **`RESOURCE_ALREADY_EXISTS`** | 409 | Không |
| Đổi field bất biến (`code`, `type`, `lotTracked`, `companyId`…) | `IMMUTABLE_FIELD` 409 | **Không có lỗi** — field bị bỏ qua, response trả giá trị cũ | **200** | — |
| Đổi `unit` của vật tư | `IMMUTABLE_FIELD` 409 | **`OPERATION_NOT_ALLOWED`** | **422** | Không |
| Cha đã ngừng (tạo kho dưới nhà máy ngừng, kích hoạt con khi cha ngừng) | `INACTIVE_PARENT` 409 | **`RESOURCE_INACTIVE`** | **422** | Không |
| Tham chiếu đã ngừng (UOM, vật tư, kho, nhà máy của kho) | `INACTIVE_REFERENCE` 409 | **`RESOURCE_INACTIVE`** | **422** | Không |
| Ngừng nhà máy còn kho hoạt động | `ACTIVE_CHILDREN` 409 | **`ACTIVE_CHILDREN_EXIST`** | **422** | Không |
| Mặc định đang ở kho khác, `replaceDefaults: false` | `DEFAULT_CONFLICT` 409 | `DEFAULT_CONFLICT` | 409 | Không |
| `X-Plant-Id` lệch nhà máy của request | `PLANT_CONTEXT_MISMATCH` 400 | **`STATE_CONFLICT`** | **409** | Không |
| `X-Plant-Id` không phải UUID | — | **`FIELD_FORMAT_INVALID`** | 400 | Không |
| Vật tư và kho khác công ty | — | `RESOURCE_SCOPE_MISMATCH` | 422 | Không |
| Không có quyền / sai phạm vi | `PERMISSION_DENIED` 403 | `PERMISSION_DENIED` | 403 | Không |
| ID không tồn tại — endpoint theo `/{id}` của bản ghi (khách hàng, nhà cung cấp, vật tư, thiết lập kho, …) | `NOT_FOUND` 404 | **`PERMISSION_DENIED`** | **403** — với **mọi** tài khoản, kể cả quản trị (đo được, §4.1) | Không |
| ID cha không tồn tại trong path danh sách / tạo (`/companies/{companyId}/…`, `/plants/{plantId}/…`) | `NOT_FOUND` 404 | Tài khoản quyền toàn hệ thống: **`ENTITY_NOT_FOUND`** · tài khoản phạm vi giới hạn: **`PERMISSION_DENIED`** | 404 · 403 | Không |
| Mã UOM không tồn tại khi tạo vật tư | `NOT_FOUND` 404 | **`ENTITY_NOT_FOUND`** | 404 | Không |
| Lặp `Idempotency-Key` với body khác | `IDEMPOTENCY_CONFLICT` 409 | **Không áp dụng** — header bị bỏ qua ở các endpoint danh mục (Q10) | — | — |
| Lỗi hệ thống | `INTERNAL_ERROR` 500 | **`INTERNAL_SERVER_ERROR`** | 500 | Không |
| Phiên đăng nhập | — | Không đổi so với giai đoạn 1 | 401 | Không |

🔴 **Hai dòng FE dễ hiểu nhầm nhất:**

1. **ID không tồn tại thường trả 403, không phải 404.** Quyền được kiểm **trước** khi tìm bản ghi, và quyền được
   suy ra từ chính bản ghi đó (bản ghi thuộc công ty nào) — không có bản ghi thì không chứng minh được quyền. Hệ quả
   phụ có lợi: không ai dò được một ID có tồn tại hay không. FE nên hiển thị chung "Không tìm thấy hoặc bạn không có
   quyền" rồi quay về danh sách.
2. **Cùng HTTP 422 có ba mã khác nhau** (`RESOURCE_INACTIVE`, `ACTIVE_CHILDREN_EXIST`, `OPERATION_NOT_ALLOWED`) và
   **cùng 409 có ba mã** (`RESOURCE_ALREADY_EXISTS`, `DEFAULT_CONFLICT`, `STATE_CONFLICT`) — rẽ nhánh theo `code`.

---

## §6 — Quyền, phạm vi và giới hạn đã biết

| Dữ liệu | Đọc | Quản lý | Cấp sẵn cho | Phạm vi kiểm |
|---|---|---|---|---|
| Khách hàng | `PERM_CUSTOMER_READ` | `PERM_CUSTOMER_MANAGE` | ADMIN, MANAGER | Công ty của bản ghi |
| Nhà cung cấp | `PERM_SUPPLIER_READ` | `PERM_SUPPLIER_MANAGE` | ADMIN, MANAGER | Công ty của bản ghi |
| Thiết lập kho | **`PERM_INVENTORY_READ`** | **`PERM_INVENTORY_MANAGE`** | không đổi so với trước | Có `plantId` ⇒ nhà máy đó; có `warehouseId` ⇒ kho đó |
| Tổ chức, UOM, vật tư | không đổi | không đổi | không đổi | không đổi |

READ và MANAGE là **hai quyền riêng** — có MANAGE không tự có READ (khớp cách FE đang làm).

**Giới hạn đã biết — đặc điểm sẵn có, không phải lỗi của task này:**

1. **Tài khoản chỉ được gán phạm vi NHÀ MÁY bị 403 ở các endpoint kiểm quyền toàn hệ thống**: danh sách công ty,
   danh sách/tạo UOM, và `GET /api/v1/inventory/item-warehouse-settings` **khi không truyền** `plantId` lẫn
   `warehouseId`. ⇒ Màn hình thiết lập kho **luôn truyền `plantId`**. Với ô chọn đơn vị khi tạo vật tư: tài khoản
   phạm vi nhà máy hiện **không đọc được** danh mục UOM — chính sách GLOBAL chờ chốt cùng giai đoạn 1.
2. **OPERATOR không có quyền Khách hàng/Nhà cung cấp** — đúng như cấp quyền hiện tại, không phải lỗi.
3. **Khách hàng chưa nối với Đơn bán hàng** — đơn bán hàng vẫn lưu tên khách dạng chữ tự do (ngoài phạm vi giai đoạn này).

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

| Lệnh | Kết quả |
|---|---|
| `mvn -o clean verify` (PostgreSQL thật qua container) | **1325** test đơn vị + **184** test tích hợp · `failures = 0`, `errors = 0` · chạy **sau** thay đổi code cuối cùng của v2 |
| Bản v1 (cùng lệnh) | 1320 + 184 ⇒ v2 thêm **5** test đơn vị cho A10; không test cũ nào bị xoá |
| Trước khi bắt đầu giai đoạn này (cùng lệnh) | 1225 + 154 ⇒ cả giai đoạn thêm **100** test đơn vị, **30** test tích hợp |

Các test chạy trên **PostgreSQL thật** (container), không phải giả lập, cho những điều chỉ cơ sở dữ liệu thật trả lời
được: ghi đồng thời thiết lập kho (2 request song song × 5 vòng), lọc theo nhà máy với dữ liệu **hai công ty**, tìm
kiếm khi tham số tìm bỏ trống, migration và cấp quyền. Nghiệm thu bằng cách **cố ý phá code** rồi xác nhận test đỏ:

| Phá gì | Kết quả |
|---|---|
| Bỏ cơ chế xếp hàng request đồng thời của thiết lập kho | Test đồng thời **đỏ ngay vòng đầu**: còn lại **2** kho mặc định |
| Vô hiệu bộ lọc nhà máy | 2 test đỏ (thấy thiết lập của công ty khác) |
| Bỏ ép kiểu tham số tìm kiếm Khách hàng | 2 test đỏ (danh sách không có `search` trả lỗi 500) |
| 🆕 v2 — Bật lại việc cắt số lẻ (cấu hình) | 4 test đỏ, đúng lý do: request `1.5` trả **200** thay vì 400 |
| 🆕 v2 — Bỏ phần chỉ tên field trong lỗi | 4 test đỏ: thiếu `errors[0].field` |

Mọi chỗ phá đã được khôi phục trước khi chạy bộ test cuối.

### 7.2 Kiểm chứng qua HTTP thật

- **Môi trường:** backend chạy local (profile dev), cổng 8081, PostgreSQL + Redis thật qua Docker (không phải container test).
- **Hai lượt đo**, cùng ngày 14/10/2026 (giờ VN), tài khoản quản trị hệ thống:
  - **Lượt 1** (12:09–12:10, bản build v1) — toàn bộ G2, fixture hậu tố `120952`.
  - **Lượt 2** (19:12, bản build v2) — riêng A10, fixture hậu tố `191258`.
- **Dữ liệu:** cả hai fixture **đã được ngừng hoạt động** cuối lượt; riêng UOM thử `ZZSMK120952` (dùng để kiểm "UOM
  đã ngừng") còn lại ở trạng thái **INACTIVE** trong danh mục UOM dùng chung của môi trường dev.

**Lượt 1:**

59 lượt gọi của lượt 1, theo đúng thứ tự:

| # | Probe | HTTP | `X-Trace-Id` |
|---|---|---|---|
| 1 | Tạo công ty fixture | 201 | `a1668c3da3fb468a` |
| 2 | Plant với timezone sai ⇒ 400, errors[0].field = timezone | 400 | `f1d4966fbf254b7d` |
| 3 | Plant hợp lệ | 201 | `bca2ee3c04d84ce0` |
| 4 | Kho W1 | 201 | `710f092e16384da3` |
| 5 | Kho W2 | 201 | `c2bb115cccb64054` |
| 6 | List kho có search + status (trước đây bị bỏ qua) | 200 | `21c3a2e97af345ee` |
| 7 | Ngừng plant còn kho ACTIVE ⇒ 422 ACTIVE_CHILDREN_EXIST | 422 | `4b6d4631cb9747ee` |
| 8 | List plant có search + status | 200 | `01e07a5302594b85` |
| 9 | Plant P2 (chưa có kho) | 201 | `ed0ba286af7d41d9` |
| 10 | Ngừng P2 (không còn kho ACTIVE) ⇒ 200 | 200 | `6df19d1e9f42426b` |
| 11 | Tạo kho dưới plant đã ngừng ⇒ 422 RESOURCE_INACTIVE | 422 | `00ca9279d3d1418a` |
| 12 | Đọc UOM KG | 200 | `5702ceaf586a43aa` |
| 13 | Item với unit không có trong UOM ⇒ 404 | 404 | `068c3c1c56464335` |
| 14 | Tạo UOM thử ZZSMK120952 | 201 | `99d14ca46f254220` |
| 15 | Ngừng UOM thử | 200 | `95f09dde7eef4358` |
| 16 | Item với UOM đã ngừng ⇒ 422 RESOURCE_INACTIVE | 422 | `ad04f38b2ab34930` |
| 17 | Item với unit kg (thường) ⇒ 201, lưu KG | 201 | `5ed5026a8dea4387` |
| 18 | PATCH item đổi unit ⇒ 422 | 422 | `046cd0471a8e4335` |
| 19 | PATCH item gửi lại unit cũ ⇒ 200 | 200 | `8e7aac89a6634df1` |
| 20 | PATCH item kèm code/type/lotTracked ⇒ 200, ba field đó bị bỏ qua | 200 | `709775530e4e47a1` |
| 21 | List item lọc type + lotTracked | 200 | `5079a4cd5ea44c07` |
| 22 | Customer, code có khoảng trắng + chữ thường ⇒ 201 | 201 | `7237dd7a3d42478b` |
| 23 | Customer trùng mã trong cùng công ty ⇒ 409 | 409 | `596056335c6c4be2` |
| 24 | Supplier cùng mã PARTNER-01 (đường company-scoped) ⇒ 201 | 201 | `251227bcef214d61` |
| 25 | Supplier thứ hai mã ' partner-01 ' ⇒ 409 | 409 | `45c97768f7b24dd7` |
| 26 | Customer email sai ⇒ 400 | 400 | `2e0905bb51ce410d` |
| 27 | PATCH customer: notes rỗng ⇒ xoá, contactName bỏ qua ⇒ giữ | 200 | `d97ba708ce31494c` |
| 28 | PATCH customer: contactName null ⇒ GIỮ (không xoá), code gửi kèm bị bỏ qua | 200 | `d5272c5588c84434` |
| 29 | Đọc customer theo id | 200 | `0e979c380a104543` |
| 30 | Customer id không tồn tại ⇒ 403 (không phải 404) | 403 | `c60407ffabaf4e49` |
| 31 | List supplier theo công ty, search | 200 | `20e681113ed44cf9` |
| 32 | List customer search | 200 | `13fe831fe07c4f56` |
| 33 | Ngừng customer | 200 | `94961acb9cdd4153` |
| 34 | Kích hoạt lại customer | 200 | `9f1bd7b1bf784189` |
| 35 | Ngừng supplier (POST) | 200 | `2dbc81297d574ac4` |
| 36 | Kích hoạt lại supplier | 200 | `39964e99eccb439b` |
| 37 | Setting I1+W1, default supply + output | 200 | `283f7784ed6643ec` |
| 38 | Setting safetyStock âm ⇒ 400 | 400 | `ef4f7f1d61de4d0e` |
| 39 | Setting leadTimeDays = 1.5 | 200 | `4efc9c9fb6144584` |
| 40 | Setting leadTimeDays = -1 ⇒ 400 | 400 | `26550d6c53c84e38` |
| 41 | Đặt lại setting W1 (leadTimeDays 5) | 200 | `a8afc8abaac34f88` |
| 42 | Chuyển supply sang W2, replaceDefaults=false ⇒ 409 DEFAULT_CONFLICT | 409 | `734d03cbb5c8454e` |
| 43 | Cùng request, replaceDefaults=true ⇒ 200 | 200 | `354c4e838bea4e9b` |
| 44 | List theo plantId — W1 còn output, W2 có supply | 200 | `e82423638c77423f` |
| 45 | List theo plantId với X-Plant-Id lệch ⇒ 409 | 409 | `1ad9f066d40f4b32` |
| 46 | List theo plantId với X-Plant-Id sai định dạng ⇒ 400 | 400 | `cceee559464c4447` |
| 47 | PUT với X-Plant-Id lệch ⇒ 409, không ghi | 409 | `600bb48b691f47d5` |
| 48 | Ngừng setting W2 | 200 | `92a29f78dcb5403d` |
| 49 | Lưu lại cặp I1+W2 ⇒ kích hoạt lại đúng settingId cũ | 200 | `8945bd8ff92d4f33` |
| 50 | List sau khi kích hoạt lại — số lẻ đọc lại nguyên văn | 200 | `65e8351eb24346a5` |
| 51 | Dọn: ngừng setting W1 | 200 | `87ed50de6dbb4758` |
| 52 | Dọn: ngừng setting W2 | 200 | `d09e11cc6d60435b` |
| 53 | Dọn: ngừng item | 200 | `8a8c227c61ec4458` |
| 54 | Dọn: ngừng customer | 200 | `99fa3b9bc36740e8` |
| 55 | Dọn: ngừng supplier | 200 | `8914f7b6b6174fc8` |
| 56 | Dọn: ngừng W1 | 200 | `623dec2e4e8748ba` |
| 57 | Dọn: ngừng W2 | 200 | `3b20c79c71ba4a90` |
| 58 | Dọn: ngừng plant (nay hết kho ACTIVE ⇒ 200) | 200 | `261fcdc2c4904203` |
| 59 | Dọn: ngừng công ty | 200 | `e40b636bd31f4b49` |

**Lượt 2 (🆕 v2):**

14 lượt gọi của lượt 2, theo đúng thứ tự:

| # | Probe | HTTP | `X-Trace-Id` |
|---|---|---|---|
| 1 | Tạo công ty fixture | 201 | `6df56c811bf341dc` |
| 2 | Plant | 201 | `b28e48d36bde4533` |
| 3 | Kho | 201 | `84bc1775973b4d86` |
| 4 | Vật tư | 201 | `cfa5231ac361425f` |
| 5 | Setting leadTimeDays = 1.5 ⇒ 400, không lưu | 400 | `a673e375e001420c` |
| 6 | Setting leadTimeDays = 5.0 ⇒ 400 (dấu chấm cũng bị từ chối) | 400 | `3d028770ac504260` |
| 7 | List theo plantId sau hai lần từ chối ⇒ chưa có thiết lập nào | 200 | `3e8da54ea58f4483` |
| 8 | Setting leadTimeDays = 5 ⇒ 200 | 200 | `3a545c7aedaa4045` |
| 9 | Tổ sản xuất capacityUnits = 1.5 ⇒ 400 (endpoint khác, cùng quy tắc) | 400 | `55d8a07171bd4b90` |
| 10 | Dọn: ngừng setting | 200 | `3168c972c9ec4a6a` |
| 11 | Dọn: ngừng vật tư | 200 | `e888680d0941479a` |
| 12 | Dọn: ngừng kho | 200 | `2b8595c5050c44c5` |
| 13 | Dọn: ngừng plant | 200 | `36d76af6651b46b3` |
| 14 | Dọn: ngừng công ty | 200 | `d94425ef0481403d` |

### 7.3 Chưa nghiệm thu

| Mục | Vì sao chưa | Cần gì để đo |
|---|---|---|
| A04 — hai lệnh tạo Supplier cùng mã đồng thời | Chưa đo live. Mã duy nhất được ràng buộc ở cơ sở dữ liệu nên bản ghi thứ hai không thể tồn tại, nhưng **mã lỗi** request thua nhận được trong tình huống đua chưa kiểm | Một lượt bắn song song |
| A14 — hai lệnh thay mặc định đồng thời | **Chỉ đo bằng test tự động** trên PostgreSQL thật (§7.1), chưa bắn live qua HTTP | Một lượt bắn song song qua HTTP |
| A16 — chứng từ cũ dùng dữ liệu đã ngừng | Ngoài các endpoint đã sửa; không đụng tới | — |
| A17 — tài khoản chỉ có quyền đọc gọi lệnh ghi; tài khoản công ty khác gọi ID trực tiếp | Chỉ kiểm bằng test tự động tầng phân quyền; **chưa** gọi live bằng tài khoản không phải quản trị | Tài khoản test có phạm vi giới hạn trên môi trường FE |
| A18 — phân quyền hết hạn giữa chừng | Thuộc giai đoạn 1 | — |
| A19 — 125 UOM, tìm ở trang cuối | Chưa dựng dữ liệu 125 dòng. Lọc chạy trong truy vấn trước phân trang (đã kiểm bằng test cho tổ chức/vật tư/khách hàng) | Bộ dữ liệu lớn |
| A21 — retry cùng `Idempotency-Key` | Không áp dụng — Q10 | — |
| Q5 — kho đã ngừng có bị hoạch định bỏ qua không | Không nằm trong lượt rà này | Rà riêng phần hoạch định |
| Q16 | Kết luận từ đọc mã, chưa đo live | Một lượt: ngừng W1 → chuyển mặc định sang W2 → kích hoạt W1 → refetch |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

### 8.1 Chưa làm

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Replay `Idempotency-Key` cho danh mục (Q10) | Chỉ các nghiệp vụ ghi tồn kho/chứng từ có replay; mở rộng sang ~20 endpoint danh mục là một đợt riêng | Có yêu cầu nghiệp vụ ngoài nghiệm thu A21 |
| `null` = xoá trong PATCH (Q3) | Giữ một quy ước cho toàn API | — (đã chốt, dùng `""` để xoá) |
| `replaceDefaults` mặc định `false` khi bỏ trống (Q8) | Sẽ làm mọi client đang chạy bắt đầu nhận 409 | — (FE gửi tường minh) |
| Thập phân dạng chuỗi (Q12) | Đổi kiểu của mọi số trên mọi endpoint | Yêu cầu riêng, có phối hợp |
| Tối thiểu 3 ký tự cho mã/tên nhà máy, kho (Q11) | Sẽ từ chối dữ liệu đang hợp lệ | — |
| Tự chuẩn hoá mã Plant/Warehouse/Item như Supplier/Customer | FE không yêu cầu; FE đã tự chuẩn hoá | Có yêu cầu |
| Nối Đơn bán hàng với Khách hàng | Ngoài phạm vi (§1.3 của FE) | Giai đoạn bán hàng |

### 8.2 Việc FE cần làm tiếp

**Bắt buộc:**

- [ ] PATCH đối tác: gửi `""` để xoá field tuỳ chọn (không phải `null`); bỏ field = giữ nguyên.
- [ ] PUT thiết lập kho: **luôn gửi `replaceDefaults` tường minh** — `false` ở lần lưu đầu, `true` sau khi người dùng xác nhận; bắt `409 DEFAULT_CONFLICT` để mở hộp xác nhận.
- [ ] GET thiết lập kho: truyền `plantId` của nhà máy đang chọn (cùng với `X-Plant-Id`).
- [ ] Đổi bảng mã lỗi theo §5.1 — đặc biệt: 422 (không phải 409) cho cha/tham chiếu đã ngừng và nhà máy còn kho; 409 `STATE_CONFLICT` (không phải 400) cho `X-Plant-Id` lệch; 403 cho ID không tồn tại.
- [ ] Thiết lập kho dùng quyền `PERM_INVENTORY_READ` / `PERM_INVENTORY_MANAGE` để ẩn/hiện nút, không phải `PERM_ITEM_*`.
- [ ] Parse số thập phân như **number**, không giả định số chữ số thập phân cố định.
- [ ] Bộ lọc vật tư dùng tên tham số `type` (không phải `itemType`).
- [ ] 🆕 v2 — Field **số nguyên** gửi dạng số **không có dấu chấm** (`5`, không phải `5.0`) trên **mọi** form, và hiển thị lỗi `VALIDATION_ERROR` theo `errors[0].field` (§3 mục 7, §4.6).

**Nên làm:**

- [ ] Giữ kiểm số nguyên ở form như hiện tại — server nay cũng chặn, nhưng báo lỗi ngay ở form vẫn nhanh hơn một vòng request.
- [ ] Sau timeout khi tạo đối tác/danh mục: nếu gửi lại nhận `409 RESOURCE_ALREADY_EXISTS`, refetch theo mã thay vì báo lỗi.
- [ ] Chạy lại A01–A22 trên môi trường của FE và gửi kết quả — BE nói "xong", **FE nói "đạt"** mới đóng giai đoạn 2.

### 8.3 Tin nhắn Zalo đi kèm

```
[BE→FE] Giai đoạn 2 — Tổ chức và danh mục nền
Mức: P1 · Breaking: CÓ
Cần FE làm: sửa adapter theo §8.2 (PATCH "" = xoá, replaceDefaults tường minh, plantId, bảng mã lỗi §5.1, số nguyên không dấu chấm) rồi chạy A01–A22
File: BE_RESPONSE_GD_02_2026-10-14_v2.md (thay bản v1 — v2 sửa A10 cho toàn hệ thống)
Thứ tự deploy: không ràng buộc
```

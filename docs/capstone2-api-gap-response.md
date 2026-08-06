# Phản hồi `BACKEND_CAPSTONE2_API_GAPS.md` — Backend → FE

> Ngày lập: 2026-08-04 · **Cập nhật: 2026-08-06** · Đối tượng: FE team
> Nguồn đối chiếu: **code thật trên branch hiện tại**, không phải OpenAPI snapshot.
> Tài liệu đối ngoại chính vẫn là `docs/api-guide-for-frontend.md` — đã cập nhật cùng ngày.

> 🔴 **Đổi mới từ 2026-08-06 (đọc trước nếu đã đọc bản 2026-08-04 rồi):** ba đợt còn lại của track
> `C2-*` (`C2-6` Work Center, `C2-7` Shift/Work Calendar, `C2-8` Capacity Board + schedule adjustment)
> đã **xong hết** — xem **§8** (section mới). §3 cũng vừa được sửa lại tương ứng. Nợ "concurrent
> refresh" mà bảng nghiệm thu ở §6.1 của `BACKEND_CAPSTONE2_API_GAPS.md` từng nêu là chưa qua cũng đã
> đóng — xem **§9**. **FE đã trả lời cả 2 câu chặn ở §5** (audit đợt 1 không cần diff, Lot `HOLD` bắt
> buộc qua QC disposition) — `C2-1` và `C2-2` **đang được triển khai**. Chỉ còn câu 5 (hành chính,
> snapshot OpenAPI) là chưa có phản hồi.

---

## 0. Trước hết: baseline hai bên đang lệch

Tài liệu của các bạn ghi baseline là `plans/openapi/openapi-2026-08-04.json`. **File đó không có trong
repo backend** — nó là artifact bên FE, nên chúng tôi không kiểm được nó chụp từ commit nào. Ba mục
dưới đây báo "thiếu" nhưng thực tế **đã có từ trước**, rất có thể vì snapshot chụp trước khi endpoint
được thêm, hoặc vì cách quét theo path.

**Đề nghị:** commit snapshot đó vào repo backend (hoặc ghi rõ commit hash sinh ra nó) để lần đối chiếu
sau hai bên nói cùng một cơ sở.

---

## 1. Ba mục đã có sẵn — không cần chờ backend

### 1.1 §4.1 BOM deactivate — **đã có**

```text
DELETE /api/v1/boms/{bomId}
```

### 1.2 §4.2 Routing deactivate — **đã có**

```text
DELETE /api/v1/routings/{routingId}
```

🔴 **Vì sao các bạn không tìm thấy:** repo dùng verb **`DELETE`** cho deactivate, không phải
`POST /…/deactivate`. Đây là quy tắc `C6` của backend: **không hard-delete chứng từ nghiệp vụ**, nên
`DELETE` không xoá gì — nó chuyển status sang `INACTIVE` và trả `200` + `{"result": null}`.

Áp dụng cho **toàn bộ** deactivate của hệ thống, không chỉ BOM/Routing:
`companies`, `plants`, `warehouses`, `items`, `bom-lines`, `material-reservations`.

**Chúng tôi không đổi verb** — đổi sẽ phá 6 endpoint FE đang gọi đúng. Thay vào đó đã sửa
`@Operation(summary)` của cả hai để OpenAPI nói thẳng *"this IS the deactivate command"* + mô tả hệ quả.
Lần sync tới các bạn sẽ thấy trong Swagger.

**Ngữ nghĩa cần biết khi gọi:**
- Deactivate BOM/Routing **không** đổi work order đã tạo — WO giữ snapshot riêng (bất biến `B12`/`B49`).
- Sau khi routing `ACTIVE` bị deactivate, convert supply suggestion `MAKE` sẽ trả **`409 MISSING_ROUTING`**
  cho tới khi có routing `ACTIVE` mới. Tạo WO **thủ công** vẫn được (snapshot routing = `null`).

### 1.3 §5 `/work-orders/{id}/variance` — endpoint đã có, **nhưng yêu cầu của các bạn chưa đủ**

Endpoint tồn tại: `GET /api/v1/work-orders/{workOrderId}/variance` (permission `PERM_WORK_ORDER_VARIANCE_READ`).

Các bạn viết *"mô tả đầy đủ material, **time** và output variance"*. Đối chiếu thực tế:

| Loại variance | Có? | Field |
|---|---|---|
| Material | ✅ | `materialLines[]` — planned vs issued theo component, `status` = `OVER_ISSUED`/`UNDER_ISSUED` |
| Output | ✅ | `outputVariance` — `plannedQuantity`, `actualOutputQuantity`, `varianceQuantity` |
| Scrap / rework | ✅ | `wipSummary` — `scrapQuantity`, `reworkQuantity` |
| **Time** | ❌ **không có** | — |

✅ **Đã đóng ở `C2-4` (2026-08-05).** `variance` nay trả thêm khối `timeVariance`
(`plannedMinutes`/`actualMinutes`/`varianceMinutes`) — công thức và bất biến `B86`:
`module/workorder/CLAUDE.md`. Đã cập nhật `api-guide-for-frontend.md` và `@Operation`.

---

## 2. Hai chỗ tài liệu của các bạn mô tả **nhẹ hơn thực tế** — cần biết trước khi thiết kế UI

### 2.1 §3.3 Audit — `oldValue`/`newValue` sẽ **rỗng** ở bản đầu

Bảng `audit_log_changes` và entity tương ứng **đã tồn tại** trong DB từ migration `V6`, nhưng **không có
dòng code nào ghi vào đó** — `AuditLogService` hiện chỉ ghi *sự kiện* (ai, hành động gì, entity nào,
khi nào, trace id), **không** ghi diff từng field.

⇒ Nếu backend giao API đọc audit ngay, `changes[]` sẽ **luôn là mảng rỗng**, không phải vì lỗi.
Chúng tôi tách làm hai đợt:

| Đợt | Nội dung | FE nhận được gì |
|---|---|---|
| **1** | `GET /audit-logs` + `/audit-logs/{id}` trên dữ liệu **đang có** | actor, action, entityType/entityId, description, status, clientIp, userAgent, traceId, createdAt |
| **2** | Field-level diff capture (sửa audit aspect) | `changes[] = [{fieldName, oldValue, newValue, changeType}]` |

**Cần FE xác nhận:** màn hình Audit của các bạn có dùng được với đợt 1 (không có diff) hay bắt buộc phải
chờ đợt 2? Câu trả lời quyết định thứ tự làm.

Thêm: `audit_logs` **không có cột `plant_id`** mà filter §3.3 yêu cầu. Chúng tôi sẽ thêm cột, nhưng
**mọi dòng lịch sử sẽ là `null`** (audit là append-only, không backfill được). ⇒ filter `plantId` chỉ
lọc đúng cho dòng sinh **sau** khi deploy.

### 2.2 §3.2 Inventory Lot — `warehouseId` không nằm trên lot, và có một ràng buộc nghiệp vụ cứng

**(a) Lot không thuộc warehouse.** Entity `InventoryLot` chỉ có `item`, `lotCode`, `status`,
`receivedAt`, `expiresAt`. Một lot có thể có tồn ở **nhiều** kho. Số lượng
(`onHandQuantity`/`reservedQuantity`/`availableQuantity`) đến từ `stock_balances` theo cặp
`(lot, warehouse)`. ⇒ `warehouseId` bắt buộc như các bạn đề nghị là **đúng**, nhưng nó là bộ lọc trên
balance, không phải thuộc tính của lot. Response sẽ phản ánh điều đó.

**(b) Không có `manufactureDate`.** Chỉ có `receivedAt` (thời điểm nhập) và `expiresAt`. Chúng tôi sẽ
**map `manufactureDate` ← `receivedAt`** thay vì thêm cột rỗng — nói rõ để các bạn không hiểu đây là
ngày sản xuất thật do xưởng khai.

**(c) 🔴 `POST /inventory/lots/{lotId}/status` sẽ KHÔNG cho tự do `HOLD → AVAILABLE`.**
Lý do là nghiệp vụ, không phải kỹ thuật: fulfillment Sales Order được kích hoạt **chỉ** bởi QC
disposition trả `AVAILABLE` (bất biến `B62`). Nếu lot của production receipt được chuyển sang
`AVAILABLE` qua endpoint này, thì:

- lot dùng được, tồn kho tăng — **nhưng** `fulfilledQuantity` của Sales Order line **không bao giờ tăng**,
- đơn hàng treo `IN_PROGRESS` **vĩnh viễn, không có lỗi nào báo**.

Đây đúng là lỗi backend vừa sửa xong (nợ #17, phase `D5`) và chúng tôi không dựng lại nó.

⇒ Với lot sinh từ production receipt **chưa QC**, endpoint sẽ trả **`409 LOT_NOT_ELIGIBLE`** và FE phải
gọi `POST /work-orders/{id}/production-receipts/{rid}/qc-disposition`. Endpoint lot-status dùng cho các
tình huống **khác** (hàng mua về bị giữ, hết hạn, phán quyết lại lot không gắn receipt).

**Cần FE xác nhận:** màn hình Inventory Lots của các bạn có đường dẫn người dùng sang QC disposition
khi lot đang `HOLD` vì chờ QC không?

---

## 3. Kế hoạch phía backend

Thứ tự dưới đây sắp theo **giá trị/rủi ro**, không theo thứ tự trong tài liệu của các bạn. Chi tiết nội
bộ: roadmap `C2-*`.

| Đợt | Nội dung | Trạng thái |
|---|---|---|
| `C2-0` | Phản hồi này + sửa OpenAPI summary + cập nhật `api-guide-for-frontend.md` | ✅ **xong** (2026-08-04) |
| `C2-1` | §3.3 Audit read API (đợt 1, chưa có diff) | FE đã xác nhận (2026-08-06) — đang làm |
| `C2-2` | §3.2 Inventory Lot list/detail/status | FE đã xác nhận (2026-08-06) — đang làm |
| `C2-3` | §3.1 UOM master CRUD + lifecycle | ✅ **xong** (2026-08-04) — xem §7 |
| `C2-4` | §4.3-§4.6: `PATCH /sales-orders/{id}`, Role/Scope lifecycle, `GET /access/assignments`, chốt contract over-BOM, **+ time variance** | ✅ **xong** (2026-08-05) — xem `CLAUDE.md §0.27` |
| `C2-5` | §5: **CORS cho origin FE**, seed 2 plant + account theo role, **+ sửa lệch RBAC seed** | ✅ **xong** (2026-08-04) — xem §6 |
| `C2-6` | §3.4 Work Center CRUD + lifecycle | ✅ **xong** (2026-08-05) — xem §8 |
| `C2-7` | §3.5 Shift + Work Calendar CRUD + lifecycle | ✅ **xong** (2026-08-05) — xem §8 |
| `C2-8` | §3.6 Capacity Board + schedule adjustment | ✅ **xong** (2026-08-05) — xem §8 |

⇒ **Toàn bộ track `C2-*` đã đóng**, chỉ còn `C2-1`/`C2-2` đang chờ hai câu trả lời ở §2 (lặp lại ở §5
để dễ tìm). Không còn hạng mục nào trong `C2-*` đang "chưa bắt đầu".

✅ **Về CORS (đã xong, không cần theo dõi nữa):** lúc viết bản 2026-08-04, backend chưa có dòng cấu
hình CORS nào. Đã đóng ở `C2-5` (§6.1) — origin whitelist qua biến môi trường `CORS_ALLOWED_ORIGINS`.

✅ **Về §3.6 Capacity (đã xong, không cần theo dõi nữa):** quyết định "sinh lịch lúc nào" đã chốt ở
`C2-8` — lúc `POST /work-orders/{id}/release`, không phải lúc tạo hay `plan()`. Chi tiết đầy đủ: §8.

---

## 4. Về 3 lỗi các bạn nêu ở §1 của tài liệu

| Lỗi | Trạng thái |
|---|---|
| Auth refresh | ✅ Đã có RTR (reuse detection ⇒ thu hồi mọi phiên) + absolute timeout 30 ngày. 🔴 Ba mã lỗi **khác nhau** đều trả **401** trên `POST /auth/refresh`: `REFRESH_TOKEN_EXPIRED`, `TOKEN_REUSE_DETECTED`, `SESSION_ABSOLUTE_TIMEOUT`. FE **phải** rẽ theo `code`, không theo HTTP status — chỉ `TOKEN_REUSE_DETECTED` mới nên hiện cảnh báo bảo mật |
| 500 ở `GET /work-orders` | ✅ Đã sửa 2026-08-04. Nguyên nhân: lỗi type binding trong JPQL khi **không** truyền `?search=` (Postgres chọn sai overload). `GET /suppliers` có y nguyên lỗi đó và cũng đã sửa cùng lượt |
| 500 ở `GET /inventory/movements` | ✅ Đã sửa 2026-08-04 — nay trả **400 `VALIDATION_ERROR`** + `errors[{field: "warehouseId"}]`. 🔴 **Nhưng nguyên nhân gốc là tài liệu của chúng tôi sai:** `api-guide-for-frontend.md` ghi endpoint này không có query param nào, trong khi `warehouseId` là **bắt buộc**. Đã sửa. Xin lỗi vì FE gọi thiếu là hệ quả trực tiếp của chỗ đó |

---

## 6. `C2-5` đã xong (2026-08-04) — CORS, account test, và một defect RBAC các bạn chưa biết

### 6.1 CORS — đã cấu hình, **không cần** các bạn trả lời câu 3 nữa

Origin thành **biến môi trường** nên các bạn tự set được, chúng tôi không cần biết trước:

- Default dev: `http://localhost:3000`, `http://localhost:5173`.
- Đổi bằng `CORS_ALLOWED_ORIGINS` (phân tách bằng dấu phẩy).
- Header gửi lên được phép: `Authorization`, `Content-Type`, `Idempotency-Key`, `X-Plant-Id`, `X-Trace-Id`.
- Header JS **đọc được**: `X-Trace-Id`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`,
  `X-RateLimit-Rule`, `Retry-After` — trước đây browser che hết, giờ đã expose.
- `Access-Control-Allow-Credentials: true`.

🔴 **`"*"` không dùng được**, backend sẽ **từ chối khởi động** nếu cấu hình vậy: API gửi `Authorization`
mọi request, CORS spec cấm wildcard trên credentialed request. Gửi cho chúng tôi origin dev/demo thật.

Đã kiểm bằng curl thật: preflight từ origin trong danh sách → **200** kèm đủ header; từ origin lạ →
**403**, không có `Allow-Origin`.

### 6.2 Account test — chạy `db/dev-seed.sql`

1 company + **2 plant** + 6 warehouse + 3 account, **mật khẩu chung `Admin@123`**:
`manager.a` (MANAGER@PLANT-A), `operator.a` (OPERATOR@PLANT-A), `manager.b` (MANAGER@PLANT-B).
Hướng dẫn chạy nằm ở đầu chính file SQL. Nó **không** phải Flyway migration — có chủ đích, để dữ liệu
demo không bao giờ tới môi trường thật.

### 6.3 🔴 Defect chúng tôi tìm ra khi làm việc này — ảnh hưởng trực tiếp tới các bạn

Seed phân quyền của backend cấp **12 permission lõi cho riêng `ADMIN`** (`PERM_WORK_ORDER_READ`,
`PERM_BOM_READ`, `PERM_INVENTORY_READ`, `PERM_ORG_READ`, `PERM_PLANNING_READ`…). Nghĩa là **trước hôm
nay, mọi account không phải `admin` đều nhận 403 ở các màn hình lõi** — kể cả trên plant của chính nó.

⇒ Nếu các bạn từng thử một account non-admin và thấy 403 ở màn hình Work Order, **đó là lỗi backend,
không phải các bạn gọi sai**. Đã sửa bằng migration `V41`.

Hai điều cần biết khi test phân quyền:

| Tình huống | Kết quả đúng |
|---|---|
| `manager.a` xem work order của **PLANT-B** | **403 `PERMISSION_DENIED`** — **không phải** mảng rỗng |
| `operator.a` **tạo** work order | **403** — tạo/release là quyền `MANAGER` |
| `operator.a` **xem** work order PLANT-A | **200** |

🔴 **Thấy mảng rỗng thay vì 403 là bug**, báo ngay — nghĩa là bộ lọc scope đã nới sai.

⚠️ **`@Valid` chạy trước kiểm quyền:** body sai định dạng trả **400 `VALIDATION_ERROR`** kể cả khi user
không có quyền. Đừng kết luận "tôi có quyền" từ việc không thấy 403 — chúng tôi đã mắc đúng lỗi này khi
tự kiểm và phải làm lại probe.

---

## 7. `C2-3` đã xong (2026-08-04) — UOM master

7 endpoint như §3.1 liệt kê: `GET /uoms`, `POST /uoms`, `GET /uoms/{id}`, `PATCH /uoms/{id}`,
`POST /uoms/{id}/activate`, `POST /uoms/{id}/deactivate`.

**Ba quyết định các bạn cần biết trước khi code UI:**

1. **UOM là global, không có `companyId`/`plantId`.** Query của các bạn ở §3.1 không lọc theo company
   nên chúng tôi đọc đó là danh mục dùng chung toàn hệ thống. Nếu thực tế cần company-scope, báo sớm —
   đảo lại quyết định này lúc này rẻ hơn nhiều so với sau khi có dữ liệu.
2. **`code` không sửa được sau khi tạo.** `PATCH /uoms/{id}` chỉ nhận `name`/`description`. Trùng
   `code` khi tạo trả `409 RESOURCE_ALREADY_EXISTS`.
3. **`activate`/`deactivate` là idempotent** — gọi `activate` trên UOM đã `ACTIVE` trả `200`, không lỗi.
4. **Validate "không xoá khi đang được dùng" (§3.1) hiện là no-op** — `items.unit` vẫn là text tự do,
   chưa có gì tham chiếu tới UOM. Sẽ có hiệu lực thật khi backend nối `items` với bảng `uoms` (chưa có
   lịch).

**Không làm trong đợt này** (báo trước để FE không chờ): `DELETE` xoá cứng riêng biệt với deactivate,
đơn vị quy đổi (kg↔g), company-scope.

---

## 8. `C2-6`+`C2-7`+`C2-8` đã xong (2026-08-05) — Work Center, Shift/Work Calendar, Capacity Board

Đây là phần **lớn nhất** của track `C2-*` (§3.4-§3.6 trong tài liệu của các bạn) và giờ đã đóng hết.
Field list đầy đủ nằm ở `docs/api-guide-for-frontend.md` (mục "Work Center", "Shift & Work Calendar",
"Capacity Board (CRP tĩnh)") — phần dưới đây chỉ nêu **quyết định nghiệp vụ** FE cần biết trước khi
dựng UI, không lặp lại toàn bộ field.

### 8.1 Work Center (`C2-6`) — 7 endpoint

`POST`/`GET /plants/{plantId}/work-centers`, `GET`/`PATCH /work-centers/{id}`,
`POST /work-centers/{id}/activate`, `POST /work-centers/{id}/deactivate`, `DELETE /work-centers/{id}`.

- **Per-plant, không có `companyId`** — khác UOM (global).
- `DELETE` gọi **cùng hành vi** với `deactivate` — Work Center là ngoại lệ duy nhất có cả ba verb
  (`activate`/`deactivate`/`DELETE`) cùng tồn tại; đừng suy ra pattern này áp dụng cho resource khác.
- 🔴 **Breaking change trên Routing:** `RoutingOperationRequest.workCenterCode` (text tự do trước đây)
  đã đổi thành `workCenterId` (UUID) — phải tạo Work Center trước rồi mới tạo/sửa Routing operation
  tham chiếu nó. Mọi operation trong cùng routing phải cùng plant, khác plant trả
  `422 OPERATION_NOT_ALLOWED`. `RoutingOperationResponse` vẫn trả cả `workCenterId` lẫn
  `workCenterCode` (resolve qua join) để FE không cần gọi thêm API để hiện tên.

### 8.2 Shift + Work Calendar (`C2-7`) — 14 endpoint

`POST`/`GET /plants/{plantId}/shifts`, `GET`/`PATCH`/`DELETE /shifts/{id}`, activate/deactivate; và
bộ tương tự cho `/plants/{plantId}/work-calendars` + `/work-calendars/{id}`.

- **Shift là MỘT khoảng liên tục** (`startTime`/`endTime` kiểu `TIME`, không phải danh sách nhiều ca
  con). `endTime < startTime` nghĩa là **ca qua đêm** (vd `22:00:00`-`06:00:00`), không phải lỗi input.
- `weeklyShifts[]` của Work Calendar: một weekday có thể gán **nhiều** shift (vd ca ngày + ca đêm cùng
  chạy thứ Hai).
- `exceptions[]` chỉ có **một chiều** — đánh dấu một ngày cụ thể thành `NON_WORKING`, không có kiểu
  "làm bù"/ngày đặc biệt khác.
- `WorkCenter` có field tuỳ chọn `workCalendarId` để gán lịch làm việc — phải cùng plant.
- 🔴 `breaks[]`/`weeklyShifts[]`/`exceptions[]` trên `PATCH` theo quy ước **full-replace-khi-có-mặt**:
  không gửi field = giữ nguyên; gửi `[]` = xoá sạch; gửi mảng có phần tử = thay thế toàn bộ, không
  phải "thêm vào".

### 8.3 Capacity Board + schedule adjustment (`C2-8`) — 2 endpoint

`GET /plants/{plantId}/capacity-board?from=&to=&workCenterId=&status=&page=&size=` và
`POST /work-orders/{workOrderId}/operations/{operationId}/schedule-adjustments`.

- 🔴 **Lịch của một operation (`plannedStartAt`/`plannedEndAt`) chỉ sinh MỘT LẦN, lúc
  `POST /work-orders/{id}/release`** — không phải lúc tạo hay `plan()` work order. WO chưa release thì
  operation của nó **không xuất hiện** trên Capacity Board — đúng thiết kế, không phải thiếu dữ liệu.
- Mỗi dòng board là **một operation**, kèm `dayCapacityMinutes` (`null` = Work Center chưa gắn Work
  Calendar, **không phải** `0`), `dayExistingLoadMinutes`, `utilizationPercent`, `overload` (boolean),
  `calendarExceptionApplies`.
- 🔴 **Đây là lịch "infinite capacity"** — hệ thống không tự phát hiện/ngăn hai Work Order cùng chiếm
  một Work Center; nó chỉ **báo cáo** khi việc đó đã xảy ra (`overload = true`). Giải quyết xung đột là
  việc của con người.
- 🔴 **`schedule-adjustments` KHÔNG BAO GIỜ tự dời operation khác và KHÔNG chặn cứng** khi lịch mới đụng
  operation liền kề hoặc vượt capacity — chỉ chặn cứng khi `expectedVersion` lệch (`409
  CONCURRENT_MODIFICATION`) hoặc `plannedEndAt <= plannedStartAt` (`400`). Mọi xung đột khác trả về
  dưới dạng **cờ tư vấn** trong response thành công: `sequenceConflict`, `calendarConflict`,
  `capacityOverload` — FE tự quyết định hiển thị cảnh báo, người dùng tự xử lý bằng tay.
- Nợ **chưa làm, có chủ đích**: validate thứ tự phụ thuộc operation (`predecessorOperationIds`) —
  tách phase riêng, chưa xếp lịch.

---

## 9. Concurrent refresh-token race — đã đóng (2026-08-05)

`BACKEND_CAPSTONE2_API_GAPS.md §6` (bảng nghiệm thu của các bạn) từng đánh dấu 🔴 mục "Auth refresh
rotation và concurrent refresh pass" là **chưa qua** phần concurrent refresh, và ghi rõ cần mở phase
riêng nếu FE coi đây là điều kiện nghiệm thu. Chúng tôi coi đây là điều kiện nghiệm thu và đã đóng nó.

**Vấn đề đã sửa:** hai request `refresh` cùng lúc trên **cùng** một `refreshToken`/`tokenId` (vd do
client retry mạng, double-submit) trước đây có thể khiến request thứ hai bị chẩn đoán nhầm thành
`TOKEN_REUSE_DETECTED` (401) và **đăng xuất oan** toàn bộ phiên của user, dù không có ai đánh cắp
token thật.

**Cách sửa:** advisory lock trên `tokenId` (`SET NX PX`, TTL 2s) trước khi validate + breadcrumb kết
quả rotate (TTL 5s) — request thua cuộc trong race nhận lại **đúng** cặp token mà request thắng vừa
sinh ra, thay vì tự rotate lần hai hoặc bị coi là kẻ trộm. **Không có grace window** cho token thật sự
bị đánh cắp — cơ chế cũ (`TOKEN_REUSE_DETECTED` sau khi bị rotate away) không đổi.

🔴 **Không có gì để FE đổi ở client** — đây là sửa lỗi phía trong, response shape/mã lỗi không đổi.
Chỉ nêu ở đây để đóng đúng mục 🔴 mà bảng nghiệm thu của các bạn đã gắn cờ.

---

## 5. Việc cần FE trả lời để backend chạy tiếp

1. ~~**§2.1** — màn hình Audit dùng được khi `changes[]` rỗng, hay phải chờ diff capture?~~ ✅ **FE xác
   nhận (2026-08-06):** dùng được đợt 1 (không cần chờ diff capture). `C2-1` không code phase field-level
   diff (`audit_log_changes`) — chỉ đọc dữ liệu event hiện có.
2. ~~**§2.2(c)** — xác nhận màn hình Inventory Lots dẫn người dùng sang QC disposition cho lot `HOLD` chờ QC?~~
   ✅ **FE xác nhận (2026-08-06):** đúng, màn hình Lots bắt buộc dẫn sang QC disposition cho lot `HOLD`
   sinh từ production receipt. `C2-2` giữ nguyên thiết kế đã mô tả ở §2.2(c): `POST
   /inventory/lots/{lotId}/status` trả `409 LOT_NOT_ELIGIBLE` cho lot `HOLD` chưa QC.
3. ~~**CORS** — FE chạy same-origin proxy, hay cần backend whitelist origin?~~ ✅ **Đã giải quyết ở
   `C2-5`** (§6.1) — origin là biến môi trường, các bạn chỉ cần cho biết origin dev/demo thật để set.
4. ~~**§4.6 over-BOM** — chọn contract nào: (1) Manager có quyền override post trực tiếp kèm
   `overrideReason` *(đúng với code hiện tại)*, hay (2) Operator submit → Manager approve bằng endpoint
   riêng *(cần bảng + state machine mới)*?~~ ✅ **Đã chốt ở `C2-4`** (2026-08-04): phương án **(1)**
   — đây **là** hành vi code hiện tại, không cần thêm gì. `POST /material-issues` (endpoint phẳng
   spec §4.1): thiếu `PERM_MATERIAL_ISSUE_OVERRIDE` khi vượt định mức BOM → **403**; có quyền nhưng
   thiếu `overrideReason` → **422** (`errors[]` nêu tên field); đủ cả hai → cho phép, lưu
   `over_issue`/`override_reason` trên `material_issue_lines`. Bất biến `B15`
   (`module/workorder/CLAUDE.md`). Nếu FE về sau muốn phương án (2), đó là việc code mới (bảng +
   state machine) — báo lại để xếp phase riêng.
5. **Snapshot OpenAPI** — commit vào repo backend hoặc cho biết commit hash, để lần đối chiếu sau không
   lệch baseline nữa.

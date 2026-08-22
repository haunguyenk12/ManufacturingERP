Đây là file trả lời cho FE, mục đích để FE biết các sử dụng các chức năng hoặc lỗi đã sửa phần mới làm xong. Nội dung sẽ ghi xuống bên dưới không được xóa dòng này. Nội dung mới sẽ ghi đè lên nội dung cũ.

# Trả lời `BE_SYSTEM_ISSUES_RESOLUTION_PLAN_2026-08-19` + 3 contract FE đang chờ (2026-08-21)

## 0. Ba contract các bạn đang chờ — **cả ba đã xong, không phải chỉ trả lời**

Các bạn ghi *"đang chờ Backend deploy V66 và phản hồi ba contract"*. Cả ba **đã được implement, test và
chạy thử thật**, không còn là câu hỏi mở:

| # | Contract | Kết cục |
|---|---|---|
| 1 | **Server-side `PENDING_APPROVAL` queue** | ✅ Đã thêm filter `status` cho **cả hai** endpoint Material Issue — xem §1 |
| 2 | **Authoritative `receivedAt`** | ✅ Lot list **và** lot detail nay trả `receivedAt` riêng — xem §2 |
| 3 | **Atomic default replacement** | ✅ Một call là chuyển được kho mặc định; **không còn `409`** — xem §3 |
| 4 | **Batch-resolve `lotNumber` cho dòng chờ duyệt** *(yêu cầu thêm)* | ✅ Manager luôn thấy mã lô; FE **không** gọi API chi tiết từng dòng — xem §11.2b |

Việc còn lại duy nhất là **deploy** — nằm ngoài phạm vi task này (xem §11).

---

## 1. Hàng đợi duyệt Over-BOM — filter phía server (yêu cầu #1)

### 1.1 Trước đây không có

Hai endpoint danh sách Material Issue **không** nhận `status`. Muốn có hàng đợi duyệt thì FE phải kéo
hết lịch sử rồi lọc trong trình duyệt — và cách đó **hỏng ngay khi một lệnh sản xuất có nhiều chứng từ
hơn một trang**: hàng đợi trông rỗng trong khi đề nghị vẫn đang chờ. Nay lọc ở server.

### 1.2 Dùng thế nào

```http
# hàng đợi duyệt toàn nhà máy — màn hình của manager
GET /api/v1/material-issues?plantId={…}&status=PENDING_APPROVAL&page=0&size=20

# hàng đợi của đúng một lệnh sản xuất
GET /api/v1/work-orders/{workOrderId}/material-issues?status=PENDING_APPROVAL
```

| | |
|---|---|
| Giá trị nhận | `PENDING_APPROVAL` · `POSTED` · `REJECTED` · `CANCELLED` |
| Bỏ trống | Không lọc — y hệt hành vi cũ, client hiện tại không gãy |
| Giá trị lạ | `400 VALIDATION_ERROR`, `errors[0].field = "status"` — **không** im lặng bỏ qua filter |
| Quyền | `PERM_MATERIAL_ISSUE_MANAGE` (như cũ). Duyệt/từ chối mới cần `PERM_MATERIAL_ISSUE_APPROVE` |

`status` kết hợp được với `workOrderId`, và **không** phá phạm vi nhà máy: chứng từ nhà máy khác vẫn
không lọt ra dù lọc theo status nào.

### 1.3 🔴 Sắp xếp mặc định đổi từ `postedAt` → `requestedAt`

Đây là thay đổi **có ảnh hưởng tới client hiện tại**, báo rõ để các bạn không bất ngờ.

Chứng từ đang chờ duyệt **chưa có `postedAt`** — nó `null` cho tới lúc được duyệt. Sắp xếp hàng đợi
theo cột đó thì thực chất **không sắp xếp gì cả**. `requestedAt` có trên **mọi** chứng từ (kể cả dòng
lịch sử, `V66` đã backfill), nên nó thành mặc định mới.

Muốn giữ thứ tự cũ: truyền `?sortBy=postedAt` tường minh.

### 1.4 Đo thật qua HTTP

Đo trên nhà máy demo **sau khi đã dọn dữ liệu kẹt** (§11.1) — 5 chứng từ, 3 trạng thái:

```text
?status=PENDING_APPROVAL  → totalElements = 2    ← hàng đợi duyệt
?status=POSTED            → totalElements = 2
?status=REJECTED          → totalElements = 1
không filter              → totalElements = 5    ← tổng, đúng bằng 2+2+1
?status=WAITING           → 400 VALIDATION_ERROR  {"field":"status"}
```

Dòng `PENDING_APPROVAL` có `postedAt = null` (đó là lý do §1.3 đổi sắp xếp mặc định).

---

## 2. `receivedAt` authoritative trên Lot (yêu cầu #2)

### 2.1 Các bạn nhận định đúng

Lot response trước đây **không** có field ngày nhận nào đọc đúng nghĩa: chỉ có `manufactureDate` (tên
sai nghĩa) và `createdAt` (dấu thời gian audit của dòng dữ liệu). Gắn nhãn "Ngày nhận" cho `createdAt`
là **sai**, và sai một cách khó thấy.

### 2.2 Nay có field riêng

`receivedAt` đã thêm vào **cả hai**: `GET /inventory/lots` (mỗi dòng) và `GET /inventory/lots/{lotId}`.

```jsonc
{
  "lotCode": "LOT-A",
  "receivedAt":      "2026-07-15T06:30:00Z",   // ← MỚI: dùng cái này cho "Ngày nhận"
  "manufactureDate": "2026-07-15T06:30:00Z",   // alias cũ, CÙNG giá trị, tên sai nghĩa
  "createdAt":       "2026-08-05T09:00:00Z",   // audit dòng dữ liệu — KHÔNG phải ngày nhận
  "updatedAt":       "2026-08-05T09:00:00Z"
}
```

### 2.3 🔴 Vì sao lỗi này khó thấy — và vì sao vẫn phải sửa

Với lot **được tạo bởi chính lần nhận đầu tiên của nó**, `receivedAt` và `createdAt` chênh nhau vài
mili-giây. Đo trên dữ liệu thật:

```text
LOT-A   receivedAt = 2026-08-21T03:46:37.340398Z
        createdAt  = 2026-08-21T03:46:37.344159Z    ← lệch 4ms, nhìn UI không thấy khác
```

Nên hiển thị `createdAt` **trông vẫn đúng** trong hầu hết trường hợp thử. Hai giá trị chỉ tách nhau ra
khi **lot được nhập bổ sung về sau** — đúng lúc con số quan trọng nhất thì nó sai. Vì thế test của
backend cố tình dựng fixture hai mốc cách nhau ba tuần: nếu chỉ dựng dữ liệu "đẹp", một mapper nối
nhầm field vẫn xanh.

### 2.4 Ba field ngày trên lot, đừng dùng lẫn

| Field | Nghĩa | Dùng cho |
|---|---|---|
| `receivedAt` | Thời điểm lot vào kho — **authoritative** | Nhãn "Ngày nhận" |
| `manufactureDate` | **Alias cũ của `receivedAt`**, giữ để không phá client | Đừng hiển thị như ngày sản xuất — hệ thống **không** lưu ngày sản xuất riêng |
| `createdAt` / `updatedAt` | Audit dòng dữ liệu | Màn hình kỹ thuật/debug |
| `sourceAt` | Thời điểm của bút toán `RECEIVE` **sớm nhất** của lot | "Nhận từ chứng từ nào, lúc nào" — đi kèm `sourceReferenceType`/`sourceReferenceId` |

`manufactureDate` **chưa xoá** vì xoá là breaking change với client đang đọc nó. Muốn backend bỏ hẳn
thì báo lại, đó là quyết định của các bạn.

---

## 3. Atomic default replacement (contract #3)

### 3.1 Trả lời thẳng: **một call, không cần dọn trước**

```jsonc
PUT /api/v1/inventory/item-warehouse-settings
{ "itemId": "…", "warehouseId": "<kho MỚI>", "safetyStock": 10, "reorderPoint": 5,
  "leadTimeDays": 3, "defaultSupply": true, "defaultOutput": false }
```

Backend **tự gỡ vai trò đó khỏi kho đang giữ**, trong **cùng một transaction**. Không phải gọi 2 lần,
không phải clear thủ công.

### 3.2 Đây là thay đổi so với hành vi trước — **`409` đã biến mất**

Bản đầu của `V66` **từ chối** khi vai trò đã có chủ (`409 STATE_CONFLICT`), buộc FE phải: gỡ mặc định
cũ → rồi set mặc định mới. Hai call đó **không có transaction nào bao ngoài**: hỏng giữa chừng thì
nhà máy còn lại **không có** mặc định nào, và MRP sẽ chặn **mọi** đề xuất của item đó bằng
`AMBIGUOUS_WAREHOUSE_POLICY` cho tới khi ai đó phát hiện. Trạng thái đó tệ hơn cả hai đầu, nên backend
đổi sang chuyển vai trò trong một call.

### 3.3 Hai điều cần biết

1. **Hai vai trò độc lập.** Giành `defaultSupply` **không** đụng `defaultOutput` của kho cũ. Kho cũ
   giữ cả hai vai trò thì sau khi bị giành SUPPLY nó **vẫn còn** OUTPUT.
2. **Không gửi cờ nào (`false`/`false`) thì không có gì bị chuyển** — sửa ngưỡng tồn kho bình thường
   không âm thầm đụng cấu hình mặc định của kho khác.

### 3.4 Đo thật qua HTTP

```text
A giành SUPPLY                      → 200, A.defaultSupply = true
B giành SUPPLY (một call duy nhất)  → 200, B.defaultSupply = true      ← trước đây: 409
trạng thái sau cùng:
   FG…  defaultSupply=True   defaultOutput=False
   RM…  defaultSupply=False  defaultOutput=False
   → đúng 1 mặc định, không có khoảnh khắc nào 0 mặc định
```

---

## 4. Tóm tắt 12 vấn đề của kế hoạch gốc

| ID | Kết cục | FE phải làm gì |
|---|---|---|
| `ISS-01` | ✅ **một** ô search chung cho `orderNo` + `customerName` | Gộp 2 ô thành 1, param `search` |
| `ISS-02` | ✅ Không đổi backend — Item search `code`/`name` + `balances?itemId=` đã đủ | Giữ autocomplete Item |
| `ISS-03` | ✅ Không đổi backend — precision giữ nguyên | Format ở FE |
| `ISS-04` | 🔴 **Breaking** — Over-BOM là **2 bước** | Màn hình duyệt/từ chối + **hàng đợi §1** |
| `ISS-05` | ✅ `sourceWipTraceIds` giữ nguyên | Không làm gì |
| `ISS-06` | 🔴 **Breaking (số MRP đổi)** — netting theo kho từng cấp | `demandWarehouseId` + **cấu hình kho mặc định §3** |
| `ISS-07` | ✅ Netting, lot hợp lệ, exception state, BUY read-only | Hiện `warehouseResolutionSource` + `messages[]` |
| `ISS-08` | ✅ Lot unique + trim, validate sớm, approval atomic | Không làm gì |
| `ISS-09` | ✅ Endpoint aggregate mới 1 dòng / `Item + Warehouse` | Đổi màn hình tồn kho — §7 |
| `ISS-10` | ⚠️ Deferred, **có sai lệch với kế hoạch** | Coi Serial là ngoài phạm vi — §9 |
| `ISS-11` | ✅ `costVariance` có cờ `authoritative` | **Không** hiện cost như số thật — §8 |
| `ISS-12` | ✅ Rework không tự thành good | Hiện rework tách khỏi good |

---

## 5. `ISS-04` + `ISS-12` — Over-BOM hai bước (`DEC-09`)

**Trước:** dòng vượt định mức kèm `overrideReason` **ghi tồn kho ngay**.
**Nay:** nó **chỉ tạo đề nghị**; tồn kho không đổi tới khi manager duyệt.

| Bước | Gọi gì | Quyền | Kết quả |
|---|---|---|---|
| 1. Đề nghị | `POST /v1/work-orders/{woId}/material-issues` | `PERM_MATERIAL_ISSUE_MANAGE` | `201` + `PENDING_APPROVAL` |
| 2a. Duyệt | `POST /v1/work-orders/{woId}/material-issues/{issueId}/approve` | **`PERM_MATERIAL_ISSUE_APPROVE`** | `200` + `POSTED` — **lúc này** mới ghi movement |
| 2b. Từ chối | `.../{issueId}/reject` body `{"reason":"…"}` | **`PERM_MATERIAL_ISSUE_APPROVE`** | `200` + `REJECTED` |

Dòng **trong** định mức vẫn `POSTED` ngay ở bước 1 — không đổi.

### 5.1 `PERM_MATERIAL_ISSUE_APPROVE` là quyền mới (ADMIN + MANAGER)

`OPERATOR` **cố ý không có** — người xin vật tư không được tự duyệt.

🔴 **Nếu FE đang ẩn/hiện nút theo `PERM_MATERIAL_ISSUE_OVERRIDE` thì phải đổi.** Quyền đó nay **không
còn được kiểm ở bất kỳ đâu**; vai trò của nó đã chuyển sang `PERM_MATERIAL_ISSUE_APPROVE` ở bước duyệt.
Nó vẫn nằm trong bảng `permissions` (không xoá để không phá cấu hình role) nhưng **giữ nó không mở
thêm được gì**. Nút "đề nghị" nay chỉ cần `PERM_MATERIAL_ISSUE_MANAGE`.

### 5.2 Breaking trên DTO

| Field | Đổi gì |
|---|---|
| `MaterialIssueResponse.status` | **4** giá trị: `PENDING_APPROVAL` · `POSTED` · `REJECTED` · `CANCELLED` |
| `MaterialIssueResponse.postedAt` | **Nullable** — null khi `PENDING_APPROVAL`/`REJECTED` |
| `MaterialIssueLineResponse.stockMovementId` | **Nullable** — chưa có movement khi chưa duyệt |
| *(mới)* `requestedAt`, `decidedAt`, `decidedBy`, `rejectionReason` | Vết quyết định |
| *(mới, trên dòng)* `reasonCode`, `reason`, `sourceExecutionId` | `DEC-08` |

### 5.3 🔴 Lỗi backend tự tìm thấy khi chạy thử — đã sửa

Đề nghị Over-BOM cho vật tư **lot-tracked** mà **không** chọn lô được backend nhận
(`201 PENDING_APPROVAL`), nhưng tới lúc duyệt lại trả `422` — đề nghị đó **không bao giờ duyệt được**,
manager không sửa được, operator đã rời màn hình, nó nằm lại `PENDING_APPROVAL` vĩnh viễn.

**Đã sửa:** chặn ngay lúc đề nghị bằng `400 LOT_REQUIRED` và **không** tạo dòng nào.

⇒ **Form đề nghị phải bắt buộc chọn lô khi component là lot-tracked.** Dòng có `reservationId` thì
không cần (lô lấy theo reservation).

### 5.4 `DEC-08` — vật tư cho rework

```jsonc
{ "componentLineId": "…", "warehouseId": "…", "lotId": "…", "quantity": 25,
  "overrideReason": "Rework do lỗi hàn",   // bắt buộc khi vượt định mức
  "reasonCode": "REWORK",                  // bắt buộc khi vượt định mức
  "sourceExecutionId": "…" }               // tuỳ chọn
```

`reasonCode` ∈ `REWORK` · `PROCESS_LOSS` · `DAMAGE` · `SETUP_LOSS` · `OTHER`. `sourceExecutionId` phải
trỏ tới Production Execution **có `reworkQuantity > 0` của chính WO đó**, sai thì `409 STATE_CONFLICT`.

🔴 **Nhập `reworkQuantity` KHÔNG tự trừ vật tư** — muốn ghi nhận thì gửi đề nghị Over-BOM riêng.

### 5.5 Mã lỗi

| Mã | HTTP | Nghĩa |
|---|---|---|
| `LOT_REQUIRED` | 400 | Dòng Over-BOM lot-tracked chưa chọn lô (§5.3) |
| `APPROVAL_REASON_REQUIRED` | 400 | `reject` thiếu `reason` |
| `OVER_BOM_APPROVAL_REQUIRED` | 409 | Nhiều dòng cùng component cộng lại vượt định mức giữa chừng |
| `STATE_CONFLICT` | 409 | Chứng từ đã quyết định rồi, hoặc WO sai trạng thái |
| `INSUFFICIENT_AVAILABLE_STOCK` | 409 | Kho không đủ — kiểm lại **lúc duyệt**, không chỉ lúc đề nghị |
| `RESERVATION_EXCEEDED` | 409 | Xuất quá phần còn lại của reservation |

### 5.6 Đo thật

```text
1. Đề nghị 25 (định mức còn 20) → 201 PENDING_APPROVAL
   tồn kho: onHand 140 / available 80 / reserved 20      ← KHÔNG đổi
2. Duyệt                        → 200 POSTED
   tồn kho: onHand 115 / available 55 / reserved 20      ← lúc này mới trừ
   component: required 20 / issued 25 ; WO: RELEASED → IN_PROGRESS
3. Duyệt lại                    → 409 STATE_CONFLICT
```

---

## 6. `ISS-06` + `ISS-07` — MRP đa kho (`DEC-03`)

### 6.1 🔴 Số MRP sẽ đổi — đây là sửa sai

Trước đây cả cây netting dùng **một** kho. Nay **mỗi cấp tự giải ra kho của nó**, chỉ tồn kho **ở đúng
kho đó** mới được trừ. Kết quả cũ gộp mọi kho ⇒ báo thừa hàng ⇒ đề xuất thiếu.

### 6.2 Request

```jsonc
POST /api/v1/planning-runs        // Idempotency-Key: tuỳ chọn nhưng nên gửi
{ "companyId": "…", "plantId": "…",
  "demandWarehouseId": "…",   // ← DÙNG CÁI NÀY
  "warehouseId": "…",         // ← deprecated, còn nhận để không phá client cũ
  "horizonStartDate": "2026-08-21", "horizonEndDate": "2026-12-31",
  "demandLineIds": ["…"] }
```

### 6.3 Response

```jsonc
{ "itemSku": "RM-001", "requirementLevel": 1,
  "warehouseId": "…", "warehouseCode": "KHO-NVL",
  "warehouseResolutionSource": "WAREHOUSE_TYPE_FALLBACK",   // ← MỚI
  "grossRequiredQuantity": 20.0,
  "availableQuantity": 100.0,          // chỉ lot AVAILABLE ở ĐÚNG kho này
  "openSupplyQuantity": 0.0, "safetyStockQuantity": 0.0,
  "projectedAvailableQuantity": 100.0, "netRequiredQuantity": 0.0,
  "excludedLotCount": 1,               // số lot bị loại vì HOLD/REJECTED/EXPIRED
  "requirementStatus": "COVERED" }
```

```text
projectedAvailable = availableQuantity + openSupplyQuantity
netRequired        = max(0, gross + safetyStock − projectedAvailable)
```

Suggestion mang thêm `outputWarehouseId` (MAKE) và `receivingWarehouseId` (BUY).

### 6.4 🔴 Thứ tự chọn kho — vì sao phải cấu hình kho mặc định

Backend **không bao giờ** chọn đại bản ghi đầu tiên:

| Hạng | Nguồn | `warehouseResolutionSource` |
|---|---|---|
| 1 | Kho đáp ứng của dòng Sales Order | `DEMAND_WAREHOUSE` |
| 2 | `demandWarehouseId` của lượt chạy | `RUN_DEMAND_WAREHOUSE` |
| 3 | Nhà máy chỉ có **đúng 1** kho `ACTIVE` | `SINGLE_ACTIVE_WAREHOUSE` |
| 4 | Setting có cờ mặc định đúng vai trò | `ITEM_WAREHOUSE_DEFAULT` |
| 5 | Item chỉ có **đúng 1** setting | `ITEM_WAREHOUSE_ONLY` |
| 6 | Chỉ **đúng 1** kho `ACTIVE` khớp loại item | `WAREHOUSE_TYPE_FALLBACK` |
| 7 | Không có / nhiều lựa chọn ngang nhau | **`BLOCKED`** |

Hạng 7 sinh `MISSING_WAREHOUSE_POLICY` (không kho nào phù hợp) và `AMBIGUOUS_WAREHOUSE_POLICY` (nhiều
kho phù hợp, chưa ai chỉ định mặc định).

⇒ **Nhà máy có từ 2 kho cùng loại trở lên sẽ `BLOCKED` cho tới khi có kho mặc định.** Màn hình
Item-Warehouse Setting cần 2 công tắc `defaultSupply` / `defaultOutput` — cách set: **§3**.

### 6.5 `messages[]`

`MATERIAL_SHORTAGE` · `MISSING_BOM` · `MISSING_ROUTING` · **`MISSING_WAREHOUSE_POLICY`** ·
**`AMBIGUOUS_WAREHOUSE_POLICY`** · `SYSTEM_FALLBACK_USED` · `PURCHASING_DEFERRED`.

`SYSTEM_FALLBACK_USED` nay còn nghĩa "kho được đoán bằng hạng 3 hoặc 6" — chạy được nhưng chưa ai cấu
hình. Nên hiện cảnh báo vàng.

### 6.6 `DEC-04` — BUY read-only

`supplyType: "BUY"` **không** approve/reject/convert được: `422 OPERATION_NOT_ALLOWED` — *"BUY
suggestions are read-only; purchasing conversion and stock side effects are deferred"*. Render chỉ đọc,
ẩn nút.

### 6.7 Đo thật — một lượt chạy, hai cấp, **hai kho khác nhau**

```text
cấp 0  FG   kho KHO-TP   RUN_DEMAND_WAREHOUSE     gross 10  projected   0  → net 10
cấp 1  RM   kho KHO-NVL  WAREHOUSE_TYPE_FALLBACK  gross 20  projected 100  → net  0
```

Kho NVL có **140** hàng vật lý nhưng `projectedAvailable` chỉ **100** — 40 nằm ở lô `HOLD` đã bị loại
(`excludedLotCount: 1`).

---

## 7. `ISS-09` — Tồn kho tổng hợp (`DEC-02`)

```http
GET /api/v1/inventory/balances/aggregate?warehouseId={bắt buộc}&itemId={tuỳ chọn}&page=&size=
```

**Một dòng cho mỗi `Item + Warehouse`** — không lặp mỗi lô một dòng.

```jsonc
{ "itemCode": "RM-001", "uom": "KG",
  "onHandQuantity": 172.0,      // tồn vật lý, GIỮ NGUYÊN kể cả hàng bị giữ
  "reservedQuantity": 3.0,
  "availableQuantity": 9.0,     // chỉ lot AVAILABLE, đã trừ reserved + quality hold
  "qualityHoldQuantity": 3.0,   // hàng KHÔNG lot-tracked chờ QC
  "rejectedQuantity": 50.0, "expiredQuantity": 7.0,
  "lotCount": 4, "updatedAt": "…" }
```

🔴 **Đừng suy trạng thái lô từ `availableQuantity > 0` nữa.**

⚠️ **Các xô không cộng lại thành `onHand`, và cố ý không có xô riêng cho lô `HOLD`.** `available` đã
trừ `reserved` và `qualityHold`; lượng trong lô `HOLD` không xuất hiện ở xô nào. Cần con số "đang giữ
chờ QC" theo từng lô thì đọc `GET /v1/inventory/lots?warehouseId=` và lọc `status`.

**Đo thật:** nhận 100 (lô A) + 40 (lô B) → chuyển lô B sang `HOLD` ⇒ `onHand` **vẫn 140**,
`available` 140 → **100**.

---

## 8. `ISS-11` + `ISS-12` — Variance

```jsonc
"outputVariance": { "plannedQuantity": 10.0, "actualOutputQuantity": 0, "varianceQuantity": -10.0 },
"wipSummary":    { "scrapQuantity": 1.0, "reworkQuantity": 2.0 },
"timeVariance":  { "plannedMinutes": 0, "actualMinutes": 60, "varianceMinutes": 60 },
"costVariance":  { "…": 0.0, "authoritative": false, "authorityStatus": "DEFERRED_NON_AUTHORITATIVE" }
```

| Khối | Nguồn |
|---|---|
| `outputVariance` | Kế hoạch WO vs Good thực tế |
| `wipSummary` | Scrap / Rework từ Production Execution |
| `timeVariance` | Routing snapshot vs tổng thời lượng execution (**bỏ** execution dở dang) |
| `materialLines` | BOM snapshot vs Material Issue **đã POSTED** |
| `costVariance` | ⚠️ **Chưa authoritative** — costing chi tiết vẫn deferred |

🔴 `costVariance` trả `0.0` **chứ không phải `null`**, nhưng `authoritative: false`. FE **phải** đọc cờ
đó và hiện "chưa có dữ liệu chi phí", không phải "chi phí bằng 0".

### Ngữ nghĩa rework

```text
Báo: good 6, scrap 1, rework 2
→ actualGoodQuantity = 6   ← rework KHÔNG cộng vào đây
  actualScrapQuantity = 1 ; actualReworkQuantity = 2
  WO = IN_PROGRESS (good 6 < plan 10)
```

Rework là hàng chờ làm lại, làm xong thì báo một Execution **mới**. Một đơn vị **không** vừa Scrap vừa
Rework.

⚠️ **Giới hạn công bố rõ:** backend **chưa** kiểm `good + scrap + rework ≤ số lượng đầu vào công đoạn`
(chưa có sổ cái đầu vào theo công đoạn). **Đừng quảng cáo ràng buộc này**; chỉ trần `cumulative good ≤
plannedQuantity` (`409 PLANNED_QUANTITY_EXCEEDED`) là thật.

---

## 9. ⚠️ `ISS-10` Serial Tracking — sai lệch với kế hoạch

Kế hoạch ghi *"Runtime OpenAPI hiện tại không quảng bá hỗ trợ Serial Tracking"*. **Không đúng với code
hiện tại**, và backend **cố ý không** gỡ: `TrackingMethod` vẫn có `SERIAL_TRACKED`, `Item.serialTracked`
/ `MaterialIssueLineRequest.serialId` / `ProductionReceiptPostRequest.serialNumber` đều đang chạy —
chúng ship từ một phase trước, không phải mới thêm.

Gỡ đi là **xoá tính năng đang chạy** để khớp một câu tài liệu, nên backend báo lại thay vì âm thầm chọn.

**Đúng tinh thần kế hoạch, đợt này KHÔNG mở rộng gì:** không thêm model/migration/endpoint serial; Goods
Receipt vẫn chưa nhận serial (`SERIAL_REQUIRED`); dòng Over-BOM có `serialId` bị từ chối.

⇒ **Cứ coi Serial là ngoài phạm vi.** Muốn backend gỡ hẳn khỏi contract thì cần các bạn xác nhận, vì nó
phá client nào đang đọc field đó.

---

## 10. Kiểm chứng

### 10.1 Test

`mvn -o clean verify` (Postgres thật qua Testcontainers) — **1152 case unit + 140 case IT / 20 class IT,
failures = 0, errors = 0**, `BUILD SUCCESS`.

Riêng ba contract ở §1–§3 thêm **7 case unit + 3 case IT**; trước đó cả ba **không có case nào**:

| Vùng | Thêm gì |
|---|---|
| Filter `status` (JPQL) | **3 case IT** — lọc đúng status, không phá phạm vi nhà máy, và bản không lọc |
| Filter `status` (HTTP) | **3 case** — forward đúng enum, giá trị lạ ra 400, cả 2 endpoint |
| `receivedAt` | **1 case mapper** + **2 assertion controller**, fixture để `receivedAt` ≠ `createdAt` |
| Atomic default | **3 case** — chuyển vai trò, không đụng vai trò còn lại, không lọc khi không giành |

### 10.2 Cố tình phá code (đều đã revert)

| Phá gì | Kết quả |
|---|---|
| Đảo vế `status` trong JPQL nhà máy | **2 case IT đỏ**, các case khác xanh |
| Nối `receivedAt` vào `createdAt` | **1 case đỏ** — đúng lỗi mà fixture "đẹp" sẽ không bắt được |
| Bỏ `flush` trước khi giành vai trò mặc định | **1 case đỏ** (`InOrder`) — trigger DB sẽ thấy hai chủ cùng lúc |

### 10.3 Chạy thật qua HTTP

Backend thật trên Postgres + Redis (`docker compose`, không phải Testcontainer). Kết quả trích ở §1.4,
§2.3, §3.4, §5.6, §6.7, §7.

---

## 11. Chuẩn bị live test — deploy `V66` và dữ liệu acceptance

### 11.1 🔴 Record `MI-F193741D` đã được xử lý — **không** dùng nó làm dữ liệu acceptance

Các bạn nói đúng: nó là chứng từ **kẹt từ trước bản sửa** (đề nghị Over-BOM cho vật tư lot-tracked mà
không chọn lô), **không bao giờ duyệt được**, nên dùng nó để nghiệm thu là nghiệm thu trên một trạng
thái mà hệ thống đã không còn tạo ra nữa. Đã xác nhận lại bằng cách bấm duyệt thật:

```text
POST .../material-issues/f193741d-…/approve
  → 422 OPERATION_NOT_ALLOWED  "Lot-tracked item requires a lot code or lot id"
```

**Đã dọn bằng `reject`, KHÔNG xoá bản ghi:**

```text
POST .../material-issues/f193741d-…/reject
  {"reason": "Stranded pre-fix request: lot-tracked component with no lot named,
              could never be approved. Cleared before live test."}
  → 200  status = REJECTED  decidedAt = 2026-08-21T10:02:35Z
```

Vì sao `reject` chứ không phải `DELETE`: chứng từ nghiệp vụ trong repo này **không hard-delete**, chỉ
đóng bằng trạng thái (`coding-rules.md` C6). `reject` **không** đụng tồn kho, có audit, giữ lại vết để
sau này còn giải thích được vì sao nó tồn tại. Và quan trọng hơn cho các bạn: **đây cũng chính là cách
xử lý mọi bản ghi kẹt tương tự ở môi trường của các bạn** — không cần ai vào DB sửa tay.

### 11.2 Dữ liệu acceptance thay thế — đã tạo bằng chính bản đã sửa

| Chứng từ | Trạng thái | Dùng để |
|---|---|---|
| `MI-9EE176F9` | `POSTED` | Bằng chứng vòng đời **đầy đủ** chạy được: đề nghị → duyệt → ghi tồn kho |
| `MI-5E935174` | `PENDING_APPROVAL` | Hàng đợi duyệt — dòng chọn lô bằng **`lotId`** (đúng cách picker của FE sẽ gửi) |
| `MI-92C92D75` | `PENDING_APPROVAL` | Hàng đợi duyệt — dòng gửi **cả `lotId` lẫn `lotNumber`** |

Cả ba đều **duyệt được thật** (khác hẳn bản ghi cũ): đều có lô, work order `IN_PROGRESS`, còn tồn kho.
`MI-9EE176F9` đã được duyệt để chứng minh, hai cái còn lại **cố ý để nguyên `PENDING_APPROVAL`** làm
dữ liệu cho màn hình hàng đợi.

Hàng đợi hiện trả về đúng như FE sẽ render:

```text
GET /api/v1/material-issues?plantId=…&status=PENDING_APPROVAL   → totalElements = 2
  MI-92C92D75  PENDING_APPROVAL  requestedAt 2026-08-21T10:04:51  postedAt null
       RM… 3.0 KG | lot LOT-A1787283996 | REWORK | "Bu hao hut khi ret lai"
  MI-5E935174  PENDING_APPROVAL  requestedAt 2026-08-21T10:03:12  postedAt null
       RM… 5.0 KG | lot 8c5c87bc-…      | REWORK | "Cho duyet: bu vat tu rework dot 2"
```

### 11.2b ✅ `lotNumber` nay luôn có trên dòng chờ duyệt — batch, không N+1

Theo yêu cầu của các bạn, backend **đã làm**: `lines[].lotNumber` nay được resolve sẵn cho dòng
`PENDING_APPROVAL`, kể cả khi người đề nghị chỉ gửi `lotId`. **FE không phải gọi
`GET /inventory/lots/{lotId}` cho từng dòng nữa.**

Trước và sau, trên đúng hai dòng acceptance ở trên:

```text
TRƯỚC  MI-5E935174  lotId 8c5c87bc-…  lotNumber = null        ← manager chỉ thấy UUID
SAU    MI-5E935174  lotId 8c5c87bc-…  lotNumber = LOT-A1787283996
       MI-92C92D75  lotId 8c5c87bc-…  lotNumber = LOT-A1787283996
```

**Một query cho cả trang, không phải một query mỗi dòng** (rule C14). Đo bằng SQL log thật của một
request lấy trang 2 dòng — đúng **một** câu:

```sql
select … from public.inventory_lots il1_0 where il1_0.lot_id in (?)
```

Dạng `in (…)` chính là bằng chứng: nếu resolve theo từng dòng thì log sẽ ra nhiều câu `lot_id = ?`.
Trace id của lượt đo: `960f37e5d84749d2`.

🔴 **Thứ tự ưu tiên — quan trọng cho màn hình duyệt, và backend đã sửa lại một lần:** khi một dòng
mang **cả** `lotId` lẫn `lotNumber` mà hai thứ **không khớp nhau**, response trả mã của lô ứng với
**`lotId`**, không phải mã đã gõ. Lý do: lúc duyệt, `InventoryMovementService` resolve lô theo `lotId`
trước và chỉ dùng mã khi không có id — nên hiển thị mã đã gõ sẽ cho manager thấy **một lô** trong khi
**lô khác** rời kho. Bản nháp đầu của backend làm ngược (ưu tiên mã đã gõ); nghiệm thu mutation phát
hiện, đã sửa và khoá bằng test.

Dòng đã duyệt (`POSTED`) không đổi: nó lấy mã từ lô thật đã gắn, như trước.

### 11.3 Deploy `V66` — checklist

**Thứ tự:** audit lô (trước) → deploy → audit đề nghị kẹt (sau) → cấu hình kho mặc định → live test.

| # | Việc | Khi nào | Ghi chú |
|---|---|---|---|
| 1 | Chạy `scripts/audit-output-lot-integrity.sql` | **TRƯỚC** deploy | 🔴 **Bắt buộc.** `V66` **cố ý ném lỗi và rollback** nếu có mã lô trùng nhau sau khi trim (`RAISE EXCEPTION 'inventory lot codes conflict after trim normalization'`). Query #2 của script chính là query đó. Có dòng trả về ⇒ **sửa dữ liệu trước**, đừng deploy |
| 2 | Deploy backend | — | Flyway chạy `V66` lúc khởi động |
| 3 | Chạy `scripts/audit-stranded-over-bom-requests.sql` (**mới**) | **SAU** deploy | Phải trả **0 dòng** ở cả 3 query trước khi bắt đầu live test. Có dòng ⇒ `reject` qua API (§11.1), **không** sửa DB tay |
| 4 | 🔴 Cấu hình kho mặc định | **SAU** deploy | Xem dưới |

⚠️ **Script #3 chỉ chạy được SAU khi deploy — đừng chạy trước.** Nó đọc `material_issue_lines.requested_lot_id`
và trạng thái `PENDING_APPROVAL`, cả hai **do chính `V66` tạo ra**; chạy trên schema cũ sẽ chỉ báo lỗi
"column does not exist".

**Môi trường của các bạn nhiều khả năng sẽ trả 0 dòng ngay từ đầu.** Bản ghi kẹt chỉ sinh ra được bởi
một build có `V66` **nhưng chưa có** bản sửa fail-fast — đúng tình huống của DB dev bên backend, vì nó
chạy bản trung gian trong lúc điều tra. Nếu các bạn deploy `V66` **cùng** bản code hiện tại thì không
có bản ghi kẹt nào được tạo ra. Script #3 vì thế là **lưới an toàn**, không phải bước sửa dữ liệu bắt buộc.

**Rủi ro đã kiểm và loại trừ:** việc `V66` nới `chk_material_issues_status` là **mở rộng** tập giá trị
(`POSTED`,`CANCELLED` → thêm `PENDING_APPROVAL`,`REJECTED`), nên không dòng lịch sử nào vi phạm được.
Backfill `requested_at = COALESCE(posted_at, created_at)` cũng an toàn vì `created_at` là `NOT NULL`.
`V66` đã chạy thật trên DB dev **đang có dữ liệu** (không phải DB trắng) và trên DB trắng qua
`FlywayMigrationIT`.

🔴 **Ngay sau deploy, mọi Item-Warehouse Setting đều `defaultSupply = false` / `defaultOutput = false`**
(migration không đoán hộ). Với nhà máy có **từ 2 kho cùng loại trở lên**, MRP sẽ trả
`AMBIGUOUS_WAREHOUSE_POLICY` và **chặn đề xuất** cho tới khi có kho mặc định. Nhà máy 1 kho hoặc mỗi
loại đúng 1 kho thì không ảnh hưởng (hạng 3 và 6 ở §6.4 vẫn giải được). Cách set: **§3**.

---

## 12. Việc còn lại

- 🔴 **Deploy nằm ngoài phạm vi task này.** Backend đã sửa + kiểm trên source; `V66` **chưa** được
  deploy lên môi trường của các bạn. Cả ba contract ở §1–§3 nằm trong cùng đợt deploy đó. Checklist
  deploy: **§11.3**.
- `docs/api-guide-for-frontend.md` đã cập nhật: vòng đời Over-BOM, hàng đợi `status`, bảng quyền,
  và ghi chú `receivedAt` vs `createdAt`. `docs/roles-and-permissions.md` đã thêm
  `PERM_MATERIAL_ISSUE_APPROVE`.
- Migration của đợt này vẫn là **`V66`** — ba thay đổi ở §1–§3 **không cần migration mới**: cột
  `requested_at`/`status` và cờ mặc định đều đã có trong `V66`, `receivedAt` là cột `inventory_lots`
  có sẵn từ lâu (chỉ chưa được expose ra wire).
- `MI-F193741D` **đã được dọn** (reject, có audit) và thay bằng dữ liệu acceptance hợp lệ — **§11.1**,
  **§11.2**. Mọi con số trong tài liệu này là trạng thái **sau** khi dọn.
- **Dọn dẹp còn treo (không ảnh hưởng contract):** hằng số `OVERRIDE_PERMISSION` và hai import
  `AccessDeniedException` / `SecurityContextHolder` trong `MaterialIssueService` không còn ai dùng sau
  `DEC-09`. Không xoá trong đợt này vì nằm ngoài phạm vi — để thành một dọn dẹp riêng.

Hai chỗ còn phải chốt với các bạn: **§6.4** (kho mặc định cho nhà máy nhiều kho) và **§9** (có gỡ
`SERIAL_TRACKED` khỏi contract không). Ngoài hai chỗ đó, phía backend không còn gì chờ phản hồi.

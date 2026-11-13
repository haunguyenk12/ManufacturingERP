# BE trả lời — Giai đoạn 6: Đơn bán hàng và hoạch định vật tư

**Trả lời cho:** `FE_HANDOFF_GD_06_2026-10-14.md` (v1, P1) · **Ngày gửi:** 18/10/2026 · **Bản:** v2 — **phần hoạch định + chuyển đổi (`G6b`)**
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. Bản này **bổ sung** `BE_RESPONSE_GD_06_2026-10-17.md` (v1, đơn bán
hàng) — không thay nó. Phần **nhập đơn Excel** gửi ở `_v3`. Chưa phải "FE nghiệm thu đạt": task chỉ đóng khi FE chạy A01–A38 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Có một lượt chạy MRP mới theo dòng đơn bán (`POST /planning-runs/commercial`) lập kế hoạch cho phần *chưa phủ* của dòng đơn, ghi
lại "phiên bản nguồn", và cho chuyển từng phần đề xuất thành lệnh sản xuất nháp hoặc đơn mua nháp nhiều lần — có sổ ghi lượng đã chuyển,
khoá chống chuyển trùng, và chặn khi nguồn đã đổi. Cùng lượt này BE sửa hai lỗi tính toán của MRP (áp cho cả lượt chạy cũ): tồn an toàn
bị mua lại một lần cho mỗi dòng nhu cầu, và nhu cầu linh kiện không được làm tròn lên trước khi nổ cấp dưới.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R08 | Kỳ ≤ 90 ngày tính cả hai đầu; mapping kho theo vật tư; thiếu mapping là cảnh báo/blocked; không dùng kho nhà máy khác | 🟡 **Thiếu thật, đã làm** | Endpoint mới. Kỳ 91 ngày ⇒ `400` field `horizonEndDate`; kho khác nhà máy ⇒ `422 RESOURCE_SCOPE_MISMATCH`; không mapping ⇒ dùng cấu hình kho mặc định như cũ, thiếu/mơ hồ ⇒ đề xuất `BLOCKED` |
| R09 | Tồn/nguồn cung/tồn an toàn dùng một lần; chỉ nổ phần net của MAKE; lượng cấp con khớp lệnh SX thật | 🔴 **Lệch số thật, đã sửa** | Tồn an toàn từng bị cộng **cho mỗi dòng** cùng vật tư–kho. Đo A/B trên dữ liệu demo `D26-`: **11 → 6 đề xuất, 8 → 3 đề xuất mua** (§1) |
| R10 | Nguồn cung mở chỉ tính nguồn đã cam kết, đúng kho/vật tư | ✅ **Có sẵn, không đổi** | Lệnh SX `RELEASED`/`IN_PROGRESS` + đơn mua `SENT`/`PARTIALLY_RECEIVED`, theo đúng kho của dòng nhu cầu (có từ 07/2026). Nháp **không** tính (A32) |
| R11 | ROP riêng cho vật tư tiêu hao, thiếu cấu hình ⇒ chặn | 🔴 **Không làm được — từ chối rõ ràng** | Cấu hình vật tư–kho **không có** "lượng đặt lại" (`reorderQuantity`). `includeRop=true` ⇒ `422 OPERATION_NOT_ALLOWED`; không bịa lượng |
| R12 | Kết quả lưu bất biến; đọc lại được khi nguồn cũ; chuyển đổi phải kiểm phiên bản nguồn hiện tại | 🟡 **Thiếu thật, đã làm** | `sourceVersion` (sha-256) lưu trên run; `GET /planning-runs/{id}` trả `currentSourceState` `CURRENT`/`STALE`; chuyển đổi khi `STALE` ⇒ `409 PLANNING_SOURCE_STALE` |
| R13 | MAKE một phần/chia nhỏ tạo lệnh SX nháp + snapshot + gắn đơn; không gắn bán thành phẩm cho thành phẩm; không giữ/phát hành | 🟡 **Thiếu thật, đã làm** | Mỗi entry một lệnh SX `DRAFT` qua đúng đường tạo lệnh từ MRP sẵn có (snapshot BOM + quy trình). Chỉ dòng **cấp 0** gắn đơn; không giữ vật tư, không phát hành |
| R14 | BUY gom một nhà cung cấp/nhà máy/kho, lượng ≤ còn lại; tạo đơn mua nháp; không gửi | 🟡 **Thiếu thật, đã làm** | Một đơn mua `DRAFT` (`PO-MRP-xxxxxxxx`) cho mọi entry; khác kho nhận ⇒ `422`; nhà cung cấp ngừng ⇒ `422 RESOURCE_INACTIVE` |
| R15 | Chuyển một phần không làm mất phần còn lại; retry/chia/tranh chấp không chuyển trùng | 🔴 **Chưa có gì, đã làm** | Trước đây một đề xuất chỉ có **một** cặp "đã chuyển thành X" ⇒ chuyển xong là hết. Nay có sổ `supply_suggestion_conversions`; còn lại = đề xuất − tổng đã chuyển |
| R18 | Mỗi lệnh kiểm quyền/phạm vi/trạng thái trước replay; tranh chấp không vượt lượng | ✅ **Đã làm cho chuyển đổi** | Khoá dòng run + `expectedConversionVersion`: hai lệnh cùng version ⇒ một thắng, một `409` (§7) |

### Trả lời Q (phần hoạch định)

| Q | Trả lời |
|---|---|
| Q5 | **Có.** Tạo đơn mua nháp từ chuyển đổi cần `PERM_SUPPLY_SUGGESTION_MANAGE` trên nhà máy của run; xem đơn mua vẫn dùng quyền đơn mua hiện có. Đơn mua nháp không có giá |
| Q7 | **Có.** `salesOrderLineIds` là mã **dòng đơn bán**; server tự tìm nhu cầu hoạch định `OPEN` của dòng đó. `demandLineIds` của `POST /planning-runs` cũ giữ nguyên nghĩa (mã nhu cầu hoạch định) |
| Q8 | **Có, với công thức thật này:** `khả dụng = tồn − đã giữ (lệnh SX + đơn bán) − giữ QC`, chỉ lô `AVAILABLE`; `net = max(0, gross + tồn an toàn − còn lại của vị trí)`. Tồn an toàn (lấy max với điểm đặt lại) chỉ cộng **ở dòng đầu tiên** của mỗi cặp vật tư–kho; các dòng sau dùng phần còn lại. `projectedAvailableQuantity` là đúng số "còn lại" đã dùng để net (đẳng thức trên luôn đúng từng dòng) |
| Q9 | **Có.** Gốc dùng kho đầu ra mặc định, linh kiện dùng kho cấp mặc định; `itemWarehouseMappings` ghi đè **cho riêng run đó** (`warehouseResolutionSource = RUN_ITEM_MAPPING`), không sửa danh mục, không lấy kho nhà máy khác |
| Q10 | **Một phần — lệch đề xuất.** Engine giữ **một dòng nhu cầu cho mỗi nguồn** (mỗi dòng có đúng một cha, một dòng đơn gốc), không gộp các nguồn vào một bucket. Tồn dùng chung vẫn chỉ net một lần (cùng "pool" vật tư–kho). Vì không gộp nên không có "đóng góp nhiều nguồn trong một bucket": `sourceSalesOrderLineIds`/`parentRequirementLineIds` là mảng nhưng hiện luôn có tối đa một phần tử. Chỉ dòng `sourceKind = SALES` (cấp 0) gắn đơn khi chuyển |
| Q11 | **Không đổi:** lệnh SX tính là nguồn cung mở khi `RELEASED`/`IN_PROGRESS`; `BLOCKED`/`COMPLETED`/`DRAFT` không tính |
| Q12 | **Có phần.** Đầu ra lệnh SX đã gắn đơn không được trừ riêng khỏi nguồn cung mở; nhưng lượng chưa phủ của dòng đơn đã trừ phần lệnh SX đó phủ, nên run thương mại không lập kế hoạch lại phần đó |
| Q13 | **Không làm** (R11): chưa có "lượng đặt lại" trong cấu hình |
| Q14 | **Có.** Đường `/conversions` mới không cần duyệt đề xuất. Đường cũ (`approve` rồi `convert-to-work-order`) giữ nguyên. Đề xuất đã chuyển qua đường mới chuyển sang `CONVERTED` để đường cũ không chuyển thêm lần nữa |
| Q16 | **Có.** Kho đầu ra MAKE = kho tính nhu cầu. `outputWarehouseId` khác `demandWarehouseId` ⇒ `400` |
| Q17 | **Có.** Lead time theo ngày lịch; ngày đặt hàng đề xuất có thể trước hôm nay, không tự kẹp. Chưa có cảnh báo "trễ" riêng |
| Q21 | **Có.** Huỷ lệnh SX/đơn mua nháp sau đó **không** hoàn lại lượng đã chuyển |
| Q23 | **Có.** Phiên bản nguồn kiểm **trong cùng transaction** chuyển đổi, dưới khoá dòng run |
| Q28 | **Có — đã làm cho cả lượt chạy cũ.** Nhu cầu linh kiện làm tròn **lên** 6 số lẻ trước khi net và nổ tiếp |

---

## §1 — Nguyên nhân thật

1. 🔴 **Tồn an toàn bị mua lại một lần cho mỗi dòng nhu cầu.** Engine cộng `max(tồn an toàn, điểm đặt lại)` vào **mỗi** dòng nhu cầu
   của cùng vật tư–kho (nhiều dòng đơn cùng sản phẩm, hoặc linh kiện dùng chung cho nhiều cha). Đo A/B trên công ty demo `D26-OMNIPLANT`,
   cùng dữ liệu, lượt chạy cũ `POST /planning-runs`:

   | | Trước (RUN-8BB65B70) | Sau (RUN-706FD135) |
   |---|---|---|
   | Đề xuất | 11 | **6** |
   | Đề xuất mua | 8 | **3** |
   | `D26-RM-GRIP` (tồn an toàn 40, hai dòng nhu cầu 11 + 8) | dòng 2: net **35** | dòng 2: net **0** — còn 64 − 11 − 8 = 45 ≥ 40 |
   | `D26-RM-BRAKE` | mua 14 + 68 | mua 14 + **8** |
   | Lệnh SX đề xuất | 3 | 3 (không đổi) |

   `X-Trace-Id`: trước `aad0459ee3b34f20`, sau `05a9305327c948d9`.
2. **Nhu cầu linh kiện không được làm tròn trước khi nổ tiếp** (Q28). Ví dụ đo được ở lượt smoke: thành phẩm 70 × 0,333333 × 1,02 =
   23,7999762 ⇒ nay **23,799977**; nguyên liệu 100/đv ⇒ **2379,9977** (trước đây nổ từ số chưa làm tròn và cột lưu làm tròn half-up).
   Dữ liệu demo toàn số nguyên nên A/B không đổi số nào ở phần này.
3. **Đề xuất chỉ "chuyển một lần".** Đề xuất có đúng một cặp "đã chuyển thành X", nên không có cách chuyển 40 rồi 30. Nay có sổ chuyển đổi.
4. **Chưa có run theo dòng đơn.** Run cũ lập kế hoạch toàn bộ lượng đặt của nhu cầu; nếu đơn đã giữ tồn thì vừa trừ tồn đã giữ vừa lập
   kế hoạch đủ lượng đặt — **đếm hai lần**. Run thương mại lập kế hoạch đúng phần chưa phủ (A16). Run cũ **không đổi** điểm này.

---

## §2 — Thay đổi contract

### Endpoint (tiền tố `/api`)

| Method + path | Body / query | Kết quả | Quyền |
|---|---|---|---|
| `POST /v1/planning-runs/commercial` | `companyId, plantId, horizonStartDate, horizonEndDate, salesOrderLineIds[1..200], itemWarehouseMappings[]?{itemId, demandWarehouseId, outputWarehouseId?}, includeRop?` · header `Idempotency-Key` tuỳ chọn | **201** run `COMPLETED` (hoặc `FAILED`), có `sourceVersion` | `PERM_MRP_RUN` nhà máy; `X-Plant-Id` đối chiếu |
| `GET /v1/planning-runs/{id}` | — | thêm `runKind`, `sourceVersion`, `currentSourceState`, `conversionVersion`, `inputSnapshot` | `PERM_MRP_READ` |
| `GET /v1/planning-runs/{id}/requirements` | như cũ | mỗi dòng thêm nguồn, đề xuất, đã chuyển, còn lại (bảng dưới) | `PERM_MRP_READ` |
| `GET /v1/planning-runs/{id}/requirements/{requirementLineId}` | — | một dòng + `conversions[]` | `PERM_MRP_READ` |
| `POST /v1/planning-runs/{id}/conversions` | `expectedSourceVersion, expectedConversionVersion, entries[1..100]{requirementLineId, quantity}, supplierId?` · header `Idempotency-Key` tuỳ chọn | **201** `{conversionVersion, sourceVersion, documents[], conversions[]}` | `PERM_SUPPLY_SUGGESTION_MANAGE` trên nhà máy của run |
| `GET /v1/planning-runs/{id}/conversions` | `page, size` | trang sổ chuyển đổi | `PERM_MRP_READ` |

`POST /v1/planning-runs`, `approve`/`reject`/`convert-to-*` **giữ nguyên** (chỉ số liệu đổi theo §1).

### Field mới

| Ở đâu | Field | Ghi chú |
|---|---|---|
| Run | `runKind` | `STANDARD` (cũ) / `COMMERCIAL` |
| Run | `sourceVersion` | chuỗi mờ (sha-256), chỉ dùng để gửi lại; `null` trên run cũ |
| Run | `currentSourceState` | `CURRENT`/`STALE` — **chỉ** ở `GET /planning-runs/{id}` của run thương mại; `null` ở danh sách và run cũ |
| Run | `conversionVersion`, `inputSnapshot` | `inputSnapshot = {salesOrderLineIds, itemWarehouseMappings, includeRop}` |
| Dòng nhu cầu | `sourceKind` | `SALES` (cấp 0 từ đơn bán), `COMPONENT`, hoặc `MANUAL`/`FORECAST` cho nhu cầu tay cấp 0 |
| Dòng nhu cầu | `sourceSalesOrderLineIds[]`, `parentRequirementLineIds[]` | tối đa một phần tử (Q10); `parentRequirementLineId` cũ vẫn còn |
| Dòng nhu cầu | `calculationState` | `BLOCKED` khi đề xuất bị chặn (thiếu BOM/quy trình/kho), còn lại `CALCULATED` |
| Dòng nhu cầu | `supplySuggestionId`, `supplyType`, `exceptionState`, `messages[]` | `null`/rỗng khi đã đủ (không có đề xuất) |
| Dòng nhu cầu | `convertedQuantity`, `remainingConvertibleQuantity` | `null` khi không có đề xuất |
| Dòng nhu cầu | `conversions[]` | chỉ ở `GET .../requirements/{id}`; `null` ở danh sách |
| `warehouseResolutionSource` | giá trị mới `RUN_ITEM_MAPPING` | khi kho lấy từ `itemWarehouseMappings` |

### Công thức

```
uncovered (lượng run thương mại lập kế hoạch cho dòng đơn) = như v1: max(0, đặt − QC chấp nhận − đã giữ − lệnh SX phủ)
khả dụng = tồn − đã giữ − giữ QC        (chỉ lô AVAILABLE; tồn đơn bán đang giữ đã trừ ở đây ⇒ không trừ lần hai)
net      = max(0, gross + tồnAnToàn(chỉ dòng đầu của cặp vật tư–kho) − còn lại của cặp)
gross linh kiện = làm tròn LÊN 6 số lẻ ( net cha × định mức × (1 + tỉ lệ hao hụt) )
remainingConvertible = lượng đề xuất − Σ lượng đã chuyển      (đề xuất đã chuyển bằng đường cũ ⇒ 0)
```

🔴 **Hao hụt: BE dùng `× (1 + tỉ lệ)`, không phải `÷ (1 − tỉ lệ)` như mock FE.** Đây là quy ước của toàn hệ thống (MRP, lệnh SX, giá
thành). Ví dụ A20 của FE: 1,000001 × 0,5 × 1,02 = 0,51000051 ⇒ **0,510001** (FE kỳ vọng 0,510205). Nếu cần đổi quy ước, đó là quyết
định riêng cho mọi module.

### Chuyển đổi — quy tắc

- Tất cả entry phải cùng loại: **MAKE** (mỗi entry một lệnh SX `DRAFT`, mã `MRP-xxxxxxxxxxxx`) hoặc **BUY** (một đơn mua `DRAFT`,
  `supplierId` bắt buộc, mọi entry cùng kho nhận). Không cần duyệt đề xuất trước.
- Lệnh SX từ dòng **cấp 0** của đơn bán gắn vào dòng đơn, **phần gắn tối đa bằng lượng chưa phủ đọc lại lúc chuyển** — run khác đã phủ
  thì phần vượt không gắn đơn. Lệnh SX của bán thành phẩm/linh kiện không gắn đơn (A28).
- Thứ tự kiểm, tất cả trong một transaction, dưới khoá dòng run: `Idempotency-Key` (trả lại kết quả cũ) → run phải là run thương mại
  `COMPLETED` → `expectedConversionVersion` → `expectedSourceVersion` khớp run → nguồn hiện tại khớp → từng entry (thuộc run, có đề xuất,
  không bị từ chối, không bị chặn) → lượng ≤ còn lại → tạo chứng từ + ghi sổ + tăng `conversionVersion`.

**Phiên bản nguồn gồm gì:** mỗi dòng đơn nguồn (vật tư, lượng đặt, ngày cần, đơn còn được hoạch định không, lượng đang giữ) + BOM và quy
trình `ACTIVE` của mọi vật tư sản xuất trong run. **Không** gồm tồn tự do / nguồn cung mở (thay đổi liên tục) và **không** gồm lượng lệnh SX
phủ / QC chấp nhận (chính lệnh chuyển đổi của run làm thay đổi chúng — FE yêu cầu chuyển đổi của chính run không làm run thành `STALE`).

---

## §3 — Breaking change

**`Wire: CÓ`** — về số liệu MRP, áp cho **cả** `POST /planning-runs` cũ:

| # | Thay đổi | Trước | Sau |
|---|---|---|---|
| 1 | Tồn an toàn khi nhiều dòng nhu cầu cùng vật tư–kho | cộng ở **mỗi** dòng | cộng **một lần** (dòng đầu); các dòng sau `safetyStockQuantity = 0` |
| 2 | Nhu cầu linh kiện có hơn 6 số lẻ | nổ từ số chưa làm tròn, lưu làm tròn half-up | làm tròn **lên** 6 số lẻ trước khi net |
| 3 | Đề xuất đã chuyển qua `/conversions` | — | `status = CONVERTED` dù còn lại > 0 ⇒ `approve`/`convert-to-*` cũ trả `409` cho nó. Đọc `remainingConvertibleQuantity`, đừng đọc `status` |

Additive: 3 endpoint mới, field mới ở run và dòng nhu cầu, giá trị enum mới `RUN_ITEM_MAPPING`, mã lỗi mới `PLANNING_SOURCE_STALE`.
Không bỏ field nào, không đổi kiểu.

---

## §4 — Cách dùng (copy từ lượt gọi thật `112703`, 18/10/2026 ~11:27 giờ VN)

Dữ liệu: thành phẩm FG (BOM: bán thành phẩm WIP 0,333333 hao hụt 2% + vật tư mua BUY 2), WIP (BOM: nguyên liệu RAW 100, tồn an toàn 5).
Đơn 100 FG, đã giữ 30 tồn FG.

### 4.1 Run thương mại (A16) · `X-Trace-Id: 7d7f368a9a8f492d`

```json
POST /api/v1/planning-runs/commercial
{
  "companyId": "2f8a6f75-e15a-4a0b-b197-2ffbc20d98e3",
  "plantId": "c116a090-2c19-49ee-9a77-da396b837909",
  "horizonStartDate": "2026-10-18",
  "horizonEndDate": "2026-11-16",
  "salesOrderLineIds": ["b32c2e27-fe1c-4247-9ac2-475982ed1603"],
  "includeRop": false,
  "itemWarehouseMappings": [{
    "itemId": "9c8cbf07-27bc-43ad-bb56-73b5e796e645",
    "demandWarehouseId": "e2b32cc6-01c7-4acb-aebf-3de46831e343",
    "outputWarehouseId": "e2b32cc6-01c7-4acb-aebf-3de46831e343"
  }]
}
```

```json
HTTP 201 (rút gọn)
{
  "code": "SUCCESS",
  "result": {
    "mrpRunId": "e65900d4-6ec7-4aea-b886-2d81f77c7986",
    "code": "RUN-E65900D4",
    "status": "COMPLETED",
    "grossDemandQuantity": 70.000000,
    "plannedWorkOrders": 2,
    "plannedPurchaseRecommendations": 2,
    "blockedProposals": 0,
    "runKind": "COMMERCIAL",
    "sourceVersion": "0b3cc94a18a17a402df899617cdc63140613721802e4a02c37877ce983842d54",
    "currentSourceState": null,
    "conversionVersion": 0,
    "inputSnapshot": { "salesOrderLineIds": ["b32c2e27-fe1c-4247-9ac2-475982ed1603"], "itemWarehouseMappings": [ … ], "includeRop": false }
  },
  "message": "Created successfully"
}
```

Dòng FG trong `GET .../requirements` (`X-Trace-Id: cf9c0ad5ba0a4a93`) — gross **70** (không phải 100), tồn đã giữ 30 không trừ lần hai:

```json
{
  "mrpRequirementLineId": "ced31f58-20eb-44b4-9c8b-4d76764035aa",
  "requirementLevel": 0,
  "grossRequiredQuantity": 70.000000,
  "availableQuantity": 0.000000,
  "reservedQuantity": 30.000000,
  "safetyStockQuantity": 0.000000,
  "projectedAvailableQuantity": 0.000000,
  "netRequiredQuantity": 70.000000,
  "warehouseResolutionSource": "RUN_ITEM_MAPPING",
  "sourceKind": "SALES",
  "sourceSalesOrderLineIds": ["b32c2e27-fe1c-4247-9ac2-475982ed1603"],
  "parentRequirementLineIds": [],
  "calculationState": "CALCULATED",
  "supplySuggestionId": "56043ac5-a107-4226-9ec9-67a303930aef",
  "supplyType": "MAKE",
  "exceptionState": "READY",
  "messages": ["MATERIAL_SHORTAGE"],
  "convertedQuantity": 0,
  "remainingConvertibleQuantity": 70.000000,
  "conversions": null
}
```

Ba dòng còn lại: WIP gross **23.799977**, RAW gross **2379.9977** (+ tồn an toàn 5 ⇒ net 2384.9977), BUY gross **140**.

### 4.2 Chuyển 40 (A27) · `X-Trace-Id: 5382f62955204310`

```json
POST /api/v1/planning-runs/e65900d4-6ec7-4aea-b886-2d81f77c7986/conversions
Idempotency-Key: conv-…
{
  "expectedSourceVersion": "0b3cc94a18a17a402df899617cdc63140613721802e4a02c37877ce983842d54",
  "expectedConversionVersion": 0,
  "entries": [{ "requirementLineId": "ced31f58-20eb-44b4-9c8b-4d76764035aa", "quantity": "40" }]
}
```

```json
HTTP 201
{
  "code": "SUCCESS",
  "result": {
    "mrpRunId": "e65900d4-6ec7-4aea-b886-2d81f77c7986",
    "conversionVersion": 1,
    "sourceVersion": "0b3cc94a18a17a402df899617cdc63140613721802e4a02c37877ce983842d54",
    "documents": [{
      "type": "WORK_ORDER",
      "documentId": "3f6d7919-5149-44a2-a336-5477f2b9d79c",
      "documentCode": "MRP-4E31AA0C5BFA",
      "status": "DRAFT",
      "quantity": 40,
      "requirementLineIds": ["ced31f58-20eb-44b4-9c8b-4d76764035aa"]
    }],
    "conversions": [{
      "conversionId": "1193fb5e-ff99-483e-865e-fa8445a426b0",
      "requirementLineId": "ced31f58-20eb-44b4-9c8b-4d76764035aa",
      "supplySuggestionId": "56043ac5-a107-4226-9ec9-67a303930aef",
      "entryNo": 1,
      "quantity": 40,
      "targetType": "WORK_ORDER",
      "targetId": "3f6d7919-5149-44a2-a336-5477f2b9d79c",
      "targetLineId": null,
      "targetCode": "MRP-4E31AA0C5BFA",
      "conversionVersion": 1
    }]
  },
  "message": "Created successfully"
}
```

Sau lệnh này, đơn: đã giữ 30 / lệnh SX phủ 40 / chưa phủ **30**. Chuyển thêm 30 (version 1) ⇒ chưa phủ **0**, còn lại 0.

### 4.3 Nguồn đã đổi (A26) · `X-Trace-Id: 854e688d35d044ec`

Bỏ giữ 30 tồn của đơn ⇒ `GET /planning-runs/{id}` báo `"currentSourceState": "STALE"`; chuyển tiếp:

```json
HTTP 409
{
  "code": "PLANNING_SOURCE_STALE",
  "message": "expectedSourceVersion: the sources of this run changed; run planning again",
  "errors": [{ "field": "expectedSourceVersion", "message": "the sources of this run changed; run planning again" }]
}
```

### 4.4 Mua (A31) · `X-Trace-Id: ed89ada820bd479c`

```json
POST /api/v1/planning-runs/e65900d4-…/conversions
{
  "expectedSourceVersion": "0b3cc94a…",
  "expectedConversionVersion": 3,
  "supplierId": "c1e5cbd3-833d-4bcc-9070-cc119240e87a",
  "entries": [
    { "requirementLineId": "a9c7c705-1924-44a2-b9f8-a919f1488c0c", "quantity": "100" },
    { "requirementLineId": "16b75f68-6375-468d-bd29-e9ab3b5e39ef", "quantity": "50" }
  ]
}
```

Kết quả: **một** đơn mua `PO-MRP-BA8ABADC` `DRAFT`, hai dòng (100 cần 30/10, 50 cần 28/10), ngày đặt = hôm nay theo múi giờ nhà máy,
ngày dự kiến đầu phiếu = ngày cần sớm nhất. `conversions[].targetLineId` là mã dòng đơn mua.

---

## §5 — Envelope và hợp đồng lỗi

| Tình huống | HTTP | `code` | Ghi chú |
|---|---|---|---|
| Thiếu quyền | 403 | `PERMISSION_DENIED` | |
| Run / dòng nhu cầu / dòng đơn không tồn tại, khác Công ty/nhà máy | 404 | `ENTITY_NOT_FOUND` | |
| Kỳ quá 90 ngày, ngày kết thúc trước ngày bắt đầu, trùng dòng/vật tư, `outputWarehouseId` khác kho tính nhu cầu, `supplierId` thiếu (BUY) / thừa (MAKE), lượng ≤ 0 hoặc quá 6 số lẻ | 400 | `VALIDATION_ERROR` + `errors[{field}]` | field: `horizonEndDate`, `salesOrderLineIds[i]`, `itemWarehouseMappings[i].…`, `supplierId`, `entries[i].quantity` |
| `includeRop = true`; trộn MAKE + BUY; BUY khác kho nhận | 422 | `OPERATION_NOT_ALLOWED` | |
| Kho/vật tư khác nhà máy/Công ty; nhà cung cấp/kho ngừng | 422 | `RESOURCE_SCOPE_MISMATCH` / `RESOURCE_INACTIVE` | FE đề xuất `INVALID_WAREHOUSE` / `SUPPLIER_NOT_AVAILABLE` |
| Dòng đơn không còn gì để lập kế hoạch / đơn không ở trạng thái hoạch định / dòng nhu cầu đã đủ / đề xuất bị từ chối / run không phải run thương mại `COMPLETED` | 409 | `STATE_CONFLICT` | FE đề xuất `INVALID_STATE` |
| Đề xuất bị chặn | 409 | `MISSING_BOM` / `MISSING_ROUTING` / `MISSING_WAREHOUSE_POLICY` / `AMBIGUOUS_WAREHOUSE_POLICY` | đúng mã đã chặn nó; FE đề xuất `PLANNING_PROPOSAL_BLOCKED` |
| `expectedConversionVersion` lệch (lệnh khác vừa chuyển) | 409 | `CONCURRENT_MODIFICATION` | FE đề xuất `VERSION_CONFLICT` |
| `expectedSourceVersion` sai hoặc nguồn đã đổi | 409 | **`PLANNING_SOURCE_STALE`** (mới) + `errors[{field:"expectedSourceVersion"}]` | trùng đề xuất FE |
| Lượng vượt phần còn lại | 409 | `PLANNED_QUANTITY_EXCEEDED` + `errors[{field:"entries[i].quantity"}]` | FE đề xuất `DEMAND_QUANTITY_EXCEEDED` |
| Cùng `Idempotency-Key`, payload khác | 409 | `IDEMPOTENCY_CONFLICT` | FE đề xuất `IDEMPOTENCY_KEY_CONFLICT` |

---

## §6 — Quyền, phạm vi và giới hạn đã biết

- Không thêm quyền mới. Run thương mại: `PERM_MRP_RUN`; đọc run/dòng/sổ: `PERM_MRP_READ`; chuyển đổi: `PERM_SUPPLY_SUGGESTION_MANAGE` —
  đều theo nhà máy của run. `X-Plant-Id` đối chiếu ở `POST /commercial` (nêu `plantId`), không ở endpoint theo `{runId}`.

**Giới hạn đã biết:**
- **Không gộp nhu cầu nhiều nguồn vào một bucket** (Q10, lệch đề xuất D7 của kế hoạch BE): mỗi dòng nhu cầu một nguồn. Kết quả net giống
  vì pool tồn dùng chung, nhưng màn hình sẽ thấy nhiều dòng cùng vật tư–kho (mỗi dòng cha một dòng).
- **ROP chưa có** (R11/Q13): cần thêm "lượng đặt lại" vào cấu hình vật tư–kho trước.
- **Chỉ run thương mại chuyển đổi được qua `/conversions`.** Run cũ dùng đường `approve` + `convert-to-*` như trước.
- **Gửi lại một lệnh chuyển với key mới sau khi lệnh trước đã chạy** ⇒ `409 CONCURRENT_MODIFICATION` (version đã đi tiếp), không chuyển hai lần.
  Replay **cùng key** trả lại đúng chứng từ; `documents[].status` ở lần replay là `null` (trạng thái hiện tại xem ở chính chứng từ).
- `createdAt` của sổ chuyển đổi có thể khác vài trăm nano giây giữa response đầu và lần đọc lại (giới hạn toàn hệ thống của trường do JPA ghi,
  đã ghi từ GĐ4). `completedAt` của run đã cắt về micro giây.
- Lượng in `0` thay vì `0.000000` ở vài field (`convertedQuantity` khi chưa chuyển, `quantity` echo lại số gửi lên). Cùng giá trị.

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

`mvn -o clean verify` (unit + integration trên PostgreSQL thật qua Testcontainers): **1621 case unit + 230 case integration, failures = 0,
errors = 0**. Trước phần này: 1583 + 224. Không xoá test cũ nào.

**Kiểm chứng "test có bắt được lỗi thật không":** cố ý phá 8 chỗ (tồn an toàn cộng mỗi dòng, bỏ làm tròn lên, bỏ kiểm lượng còn lại, bỏ
kiểm nguồn hiện tại, đổi mã "nguồn đã đổi" sang một mã 409 khác, bỏ giới hạn phần gắn đơn, nới bộ lọc vật tư sản xuất của phiên bản nguồn,
lập kế hoạch lượng đặt thay vì lượng chưa phủ) — **8/8 lần test đỏ đúng chỗ**; đã khôi phục.

### 7.2 Kiểm chứng qua HTTP thật

- **Môi trường:** backend local (profile dev), PostgreSQL + Redis thật qua Docker. Migration mới chạy được trên DB dev có dữ liệu demo.
- **Lượt `112703`** (18/10, ~11:27): 39 lượt gọi ngoài dựng dữ liệu, **mọi HTTP status đúng kỳ vọng**. Công ty thử `GD6B-112703`.

| Ca FE | Kết quả | `X-Trace-Id` |
|---|---|---|
| A15 kỳ 91 ngày / 90 ngày | 400 field `horizonEndDate` / 201 | `486d4bcfdc2844b5`, `a4b82e2d789c417a` |
| A25 `includeRop=true` | 422 `OPERATION_NOT_ALLOWED` | `1971b5fc545e4d9a` |
| Q16 kho đầu ra khác kho nhu cầu | 400 field `itemWarehouseMappings[0].outputWarehouseId` | `4a988e34555e4077` |
| A16 run thương mại đặt 100 / giữ 30 | 201, gross 70, net 70 | `7d7f368a9a8f492d` |
| Replay run cùng key | 201, **cùng** `mrpRunId` | `4bdd96a3496c4d4d` |
| A20 làm tròn | WIP 23.799977, RAW 2379.9977 | `cf9c0ad5ba0a4a93` |
| A27 chuyển 40 → 30 | 201 ×2; đơn phủ 40 → 70, chưa phủ 30 → 0 | `5382f62955204310`, `46264e8ce6b548fa` |
| A30 replay cùng key / cùng key khác lượng | 201 cùng lệnh SX (DB vẫn 1 lệnh) / 409 `IDEMPOTENCY_CONFLICT` | `c9c1a728849d49fc`, `5e91c2755e4948b1` |
| A29 version cũ | 409 `CONCURRENT_MODIFICATION` | `5be0b04b2c434018` |
| Chuyển 31 khi còn 30 | 409 `PLANNED_QUANTITY_EXCEEDED`, field `entries[0].quantity` | `c6a9fa5e48e44bd0` |
| A28 chuyển bán thành phẩm 10 | 201; đơn **không** đổi phủ (vẫn 70) | `272c3516a45c4772` |
| A31 mua thiếu nhà cung cấp / trộn MAKE+BUY / mua 2 dòng | 400 / 422 / 201 một đơn mua `DRAFT` | `ffa3c06cba1c4dd3`, `381cb7e86c064a43`, `ed89ada820bd479c` |
| Mua quá 6 số lẻ | 400 field `entries[0].quantity` | `4539bcb77a514002` |
| A26 bỏ giữ tồn rồi đọc run / chuyển | `STALE` / 409 `PLANNING_SOURCE_STALE` | `9f5254f1f57c4ec8`, `854e688d35d044ec` |
| Sổ chuyển đổi; chi tiết dòng; dòng không có | 200 (5 dòng) / 200 converted 70 / 404 | `4233b42a435942c0`, `e443d555431b4f34`, `70a1495e88a641f3` |
| Đường cũ `approve` đề xuất đã chuyển | 409 `STATE_CONFLICT` | `52d2dff4bb964eb8` |

Sổ trong DB sau lượt: `WORK_ORDER 40, 30, 10 · PURCHASE_ORDER 100, 50`, `conversion_version = 4`; tồn FG 30 / đã giữ 0 (sau bỏ giữ) — chuyển
đổi **không** đụng tồn kho.

### 7.3 Chưa nghiệm thu

| Ca | Vì sao | Cần gì |
|---|---|---|
| A29 hai lệnh chuyển **đồng thời qua HTTP** | Gửi tuần tự (lệnh thứ hai dùng version cũ); có khoá dòng run + `@Version` | Bắn song song |
| A17–A19, A21–A23 chi tiết | Có test tự động cho net/carry/mapping/nguồn cung; lượt live chỉ phủ một BOM | Dữ liệu FE |
| A24 | Cần luồng nhận hàng + QC đơn mua của GĐ7 | GĐ7 |
| A32 lệnh nháp không tính nguồn cung | Luật có sẵn (chỉ `RELEASED`/`IN_PROGRESS`); không chạy lại run trong lượt này | — |
| A37/A38 | Quyền có test deny + allow; lỗi giữa chừng: test tự động (một entry lỗi ⇒ không tạo chứng từ nào) | Tài khoản thiếu quyền |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm:** ROP; gộp nhiều nguồn vào một bucket; lọc `kind`/`sourceKind`/`warning` trên danh sách dòng nhu cầu; cảnh báo trễ hạn riêng;
đổi quy ước hao hụt; hoàn lại lượng đã chuyển khi huỷ chứng từ nháp; nhập đơn Excel (⇒ `_v3`).

**Việc FE cần làm tiếp:**

Bắt buộc:
- [ ] Lập kế hoạch đơn bán qua `POST /v1/planning-runs/commercial` với **mã dòng đơn**; giữ `sourceVersion` + `conversionVersion` của run.
- [ ] Chuyển đổi qua `/conversions`: gửi `expectedSourceVersion`, `expectedConversionVersion`; lấy version mới từ response cho lệnh kế tiếp.
- [ ] Đọc `remainingConvertibleQuantity`, **không** đọc `status` của đề xuất để biết còn chuyển được bao nhiêu.
- [ ] `PLANNING_SOURCE_STALE` ⇒ đề nghị chạy run mới, không retry.
- [ ] Hao hụt: hiển thị theo `× (1 + tỉ lệ)` của BE.
- [ ] Số MRP sau bản này đổi (tồn an toàn một lần) — số cũ trong mock không còn khớp.

Nên làm:
- [ ] Hiển thị `currentSourceState` ở màn hình chi tiết run; màn hình danh sách không có field này.
- [ ] Chạy A15–A32 trên môi trường FE và báo kết quả theo mã ca.

**Thứ tự deploy:** không ràng buộc.

---

**Tin nhắn kèm (Zalo):**

```
[BE→FE] Giai đoạn 6 (phần 2/3: hoạch định + chuyển đổi) — run theo dòng đơn, chuyển từng phần thành lệnh SX/đơn mua nháp, chặn khi nguồn đổi
Mức: P1 · Breaking: CÓ (số MRP đổi: tồn an toàn chỉ cộng 1 lần, linh kiện làm tròn lên 6 số lẻ — áp cả run cũ)
Cần FE làm: POST /planning-runs/commercial, POST /conversions với 2 version, đọc remainingConvertibleQuantity, bắt PLANNING_SOURCE_STALE
File: BE_RESPONSE_GD_06_2026-10-17_v2.md (bổ sung v1, nhập Excel gửi ở _v3)
Thứ tự deploy: không ràng buộc
```

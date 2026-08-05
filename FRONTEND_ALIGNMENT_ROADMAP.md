# Frontend Alignment Roadmap – Track `F*` · `D*` · `C2-*`

> **Vai trò của file này:** giữ toàn bộ lịch sử + kế hoạch chi tiết của track `F*` (căn chỉnh API
> backend theo `OmniPlant_MVP_Production_Backend_Handoff.docx`), tương tự cách
> `MANUFACTURING_GAP_ROADMAP.md` giữ track `P*` và `TEST_IMPROVEMENT_PLAN.md` giữ track `T*`.
>
> **File này host ba track**, vì cả ba đều xuất phát từ việc căn chỉnh backend theo nhu cầu frontend:
> **`F*`** (§0–§5, §7 — spec `.docx` gốc) · **`D*`** (§6 — trả nợ kỹ thuật phát sinh từ `F*`) ·
> **`C2-*`** (**§8** — gap Capstone 2 theo `BACKEND_CAPSTONE2_API_GAPS.md`, tài liệu FE gửi 2026-08-04).
> Track `C2-*` là track **đang chạy**; `F*` đã đóng ở `F10`, `D*` còn `D8c`.
>
> **Quy ước:** `NEXT_PHASE_PLAN.md` **luôn chỉ chứa đúng một phase đang chạy** (quy ước chung của
> repo, xem `MANUFACTURING_GAP_ROADMAP.md §2.1`). Khi một phase `F*` hoàn thành, nội dung chi tiết
> của nó được **chuyển vào file này** (§3) và `NEXT_PHASE_PLAN.md` được thay bằng phase kế tiếp.
> Nguồn sự thật cho "phase nào đang chạy" luôn là `CLAUDE.md §0.1`.

---

## 0. Bối Cảnh & Quyết Định Đã Chốt

`OmniPlant_MVP_Production_Backend_Handoff.docx` là đặc tả tích hợp FE↔BE viết **ngược từ frontend
đã implement**, nên field name + business rule là phần cố định; tên endpoint là đề xuất. Đối chiếu
với repo hiện tại cho thấy repo thiếu **3 module** (Sales Order, Routing, QC) và có **1 đảo ngược
ngữ nghĩa** (xem §2.1 dưới). Track `F*` gồm 6 phase; `F2`/`F4`/`F6` trùng nội dung với `P2`/`P4`/`P6`
của `MANUFACTURING_GAP_ROADMAP.md`, chỉ sắp lại thứ tự theo dependency của spec.

**3 quyết định đã chốt với user (2026-07-26):**

1. **Giữ `UUID`** — spec ghi `id: number`, FE đổi TS type sang `string`. Không đụng schema/RBAC.
2. **Làm đủ 3 module thiếu**, chia phase tuần tự `F1`→`F6`.
3. **Sửa tại chỗ trên `/api/v1`**, không mở `/api/v2` — miễn trừ có chủ đích rule `A1`/`C-A1`
   vì chưa có client production nào ngoài chính FE này. Mỗi phase phải ghi "Breaking Changes"
   vào `NEXT_PHASE_PLAN.md` và **sửa** test cũ theo `R10` (không xoá).

---

## 1. Bảng Theo Dõi Tiến Độ — track `F*` *(cập nhật: 2026-08-01, `F*` đã đóng ở `F10`)*

> 🔴 **Track đang chạy là `C2-*`, xem §8** — bảng dưới đây chỉ là lịch sử `F1`–`F10`.
> Trạng thái checklist Capstone 2 (9/13) ở **§8.0**; `C2-8` đã xong, đóng nốt cluster `P4`. Phase
> kế tiếp: `C2-1`/`C2-2` (bị chặn, chờ FE) — xem `NEXT_PHASE_PLAN.md` cho thứ tự đề xuất track `P*`.

| ✔ | Phase | Nội dung | Trùng roadmap `P*` |
|---|---|---|---|
| **[x]** | **`F1`** | Contract layer (error code, `errors[]`, handler, idempotency, `X-Plant-Id`) + sửa nợ #8 | — |
| **[x]** | **`F2`** | Production Receipt lifecycle (`DRAFT`/`APPROVED`) + QC disposition | `P2` |
| **[x]** | **`F3`** | Sales Order + planning demand | `P6` (nửa đầu) |
| **[x]** | **`F4`** | Routing tối thiểu + snapshot lên WO | `P4` (một phần) |
| **[x]** | **`F5`** | Reshape Planning + Work Order + Production Execution (đảo ngược ngữ nghĩa) — `F5-A` ✅ 2026-07-27 (Work Order + Execution + Receipt), `F5-B` ✅ 2026-07-28 (Planning), xem §3.5 | — |
| **[x]** | **`F6`** | Fulfillment allocation — ✅ 2026-07-28 (`V35`), xem §3.6 | `P6` (nửa sau) |
| **[x]** | **`F7`** | **Đối chiếu spec toàn diện** + đóng 5 gap (`V38`) — ✅ 2026-07-31, xem §3.7 và **§7** | — |
| **[x]** | **`F8`** | **Đóng gap field-level + 4 endpoint** (additive, không rename) + nợ A — ✅ 2026-07-31 (`V39`), xem §3.8 và **§7** | — |
| **[x]** | **`F9`** | **2 field cuối** (`uom` execution-candidate, `reservedQuantity` component line) + **sửa tuyên bố nói quá của `F8`** — ✅ 2026-07-31, không migration, xem §3.9 | — |
| **[x]** | **`F10`** | **Đóng 3 nợ field-level: `G`** (persist `projectedAvailable`) **· `F`** (`sourceRoutingCode`/`Version` trên proposal) **· `H`** (số chứng từ `MI-`/`PE-`) — ✅ 2026-08-01 (`V40`), wire additive, xem §3.10. **Nợ `E` cố ý ngoài phạm vi** | — |

> **`F1`–`F6` đóng track `F*` về mặt tính năng**, nhưng chưa phase nào kiểm chứng spec ở mức
> section/field. `F7` (2026-07-31) làm việc đó và tìm ra **5 gap**, trong đó một endpoint cả màn hình
> FE đang thiếu. Bảng đối chiếu §1..§11 nay ở **§7** — phase sau đụng vùng nào thì cập nhật hàng đó.
>
> 🔴 **`F7` chưa đóng hết như dấu ✅ của nó ngụ ý.** Đối chiếu lại ở **mức field** (2026-07-31, cùng
> ngày) cho thấy phạm vi thật của `F7` là **grep `itemCode`/`lotCode`** rồi đổi 7 DTO — nó **chưa bao
> giờ đi hết các bảng "Trường cần hiển thị"**. Hệ quả: `GET /production-receipts/candidates` (§6.2 —
> **đúng cùng loại gap #1 mà `F7` vừa đóng, nằm ngay section kế tiếp**) không tồn tại, 3 endpoint list
> phẳng chỉ có bản nested, và `uom` có mặt trên **đúng 1 DTO** trong cả `module/workorder`. `F8`
> (✅ 2026-07-31) đã đóng phần đó — xem §3.8 và §7.
>
> Kế hoạch dọn nợ kỹ thuật phát sinh trong và trước track này nằm ở **§6** (track `D*`); `D1`–`D11`,
> `D8a` và **`D8b`** đã xong, còn **`D8c`** (🔴 bị chặn: `pom.xml` không có `spring-boot-starter-mail`).
>
> **Track `C2-*` (§8) là track kế nhiệm** — nguồn của nó là `BACKEND_CAPSTONE2_API_GAPS.md`, tài liệu
> **khác** với spec `.docx` của `F*`. `C2-0` lại tìm ra một field spec `F*` để sót (**time variance**,
> §8.2) ⇒ bảng §7 vẫn chưa phải là bản đối chiếu cuối cùng, đừng đọc dấu ✅ ở đó như "đã xong hết".

---

## 2. Khoảng Trống Chính (spec ⇄ hiện trạng)

### 2.1 ✅ Đảo ngược ngữ nghĩa — ĐÃ XỬ LÝ Ở `F5-A` (2026-07-27)

| | Trước `F5` | Sau `F5-A` |
|---|---|---|
| Ai làm WO tiến triển | **Receipt approve** — `ProductionReceiptService` `setCompletedQuantity(...)` + `complete(...)` | **`ProductionExecutionService.report`** — cộng `actualGoodQuantity`, `markInProgress`, `COMPLETED` |
| Trần của receipt (bất biến `B16`) | `plannedQuantity − completedQuantity − Σ(DRAFT+PENDING_APPROVAL)` | `actualGoodQuantity − completedQuantity − Σ(DRAFT+PENDING_APPROVAL)` |
| WO chuyển `COMPLETED` khi | receipt approve đủ số | cumulative good = `plannedQuantity` (**độc lập receipt**) |

`completedQuantity` **giữ nguyên tên và ngữ nghĩa** ("đã nhập kho"); cột `actual_good_quantity` là
cột mới. `B16` đã viết lại, `B17` đã tách. Bất biến mới `B53`-`B57`, xem `module/workorder/CLAUDE.md`
và `CLAUDE.md §0.5`/§0.9.

### 2.2 Module chưa tồn tại

| Spec cần | Hiện trạng | Phase xử lý |
|---|---|---|
| **Sales Order** + demand | ✅ `F3` — `module/sales`, confirm ⇒ `PlanningDemand`, `GET /sales-orders/planning-demands` | `F3` ✅ |
| **Sales Order** fulfillment (`fulfilledQuantity`, allocation) | ✅ `F6` — `work_order_demand_allocations`, QC `AVAILABLE` ⇒ `fulfilledQuantity` + roll-up status | `F6` ✅ |
| **Routing** ACTIVE + snapshot header | ✅ `F4` — `module/routing`, 1 routing `ACTIVE`/item, 4 cột snapshot trên `work_orders` | `F4` ✅ |
| **Routing** operations snapshot lên WO (`work_order_operations`) | ✅ `F5-A` — bảng `work_order_operations`, snapshot copy, bất biến B56 | `F5-A` ✅ |
| **Work Center** entity + capacity/CRP | `workCenterCode` là string | `P4` (phần còn lại, ngoài MVP theo spec §11) |
| **QC disposition** `HOLD`→`AVAILABLE`/`REJECTED` | ✅ `F2` — `POST /production-receipts/{id}/qc-disposition` + `QualityDisposition` | `F2` ✅ |

### 2.3 Lệch contract (đã xử lý phần lớn ở `F1`, phần còn lại thuộc `F2`/`F5`)

| Chủ đề | Hiện tại | Spec | Trạng thái |
|---|---|---|---|
| Wire `code` lỗi | `"BIZ_010"` | `"INSUFFICIENT_AVAILABLE_STOCK"` | ✅ `F1` |
| `errors` shape | `Map` | `[{field, message}]` | ✅ `F1` |
| Idempotency trùng key khác payload | im lặng trả cũ | `IDEMPOTENCY_CONFLICT` | ✅ `F1` |
| `X-Plant-Id` | không có | header cross-check | ✅ `F1` (3 controller trên luồng FE) |
| `INSUFFICIENT_STOCK`/`OPERATION_NOT_ALLOWED` HTTP status | 422 | spec đòi 409 | ✅ `F5-A` (nợ #9 đóng; `OPERATION_NOT_ALLOWED` 422 cố ý giữ cho validate master data) |
| `WorkOrderStatus` | thiếu `PLANNED` | spec dùng ngay sau convert | ✅ `F5-A` |
| `ProductionReceiptStatus` | thiếu `DRAFT`, `POSTED`≠`APPROVED` | `DRAFT/PENDING_APPROVAL/APPROVED/REJECTED` | ✅ `F2` |
| Proposal | `SupplySuggestionType` WORK_ORDER/PURCHASE_REQUISITION | `supplyType` MAKE/BUY + `exceptionState` | ✅ `F5-B` (2026-07-28) |
| Item lot tracking | `boolean lotTracked` | `outputTrackingMethod` NON_TRACKED/LOT_TRACKED | ✅ `F2` — expose ở `ProductionReceiptResponse` qua `TrackingMethod`; `Item.lotTracked` **giữ nguyên** trong DB (spec chỉ đòi ở view receipt/candidate) |
| Field name (`itemCode`→`itemSku`, `lotCode`→`lotNumber`...) | camelCase khác spec | theo spec | ✅ **`F7`** — chính sách "đổi dần" **đã dừng**. `F7` đổi nốt 7 DTO trên luồng spec; **17 DTO ngoài luồng spec cố ý giữ tên cũ** (spec không mô tả màn hình cho chúng). Ranh giới chính xác: §3.7 |

---

## 3. Chi Tiết Từng Phase

### `F1` — Contract Layer + Sửa Bug Netting ✅ **HOÀN THÀNH 2026-07-26**

```text
Baseline đầu phase:   57 class / 281 case unit  +  3 class / 10 case IT   ·  failures = 0
Kết quả cuối phase:   289 case unit (+8)        +  10 case IT (không đổi) ·  failures = 0
Coverage đầu phase:   line 69.2%  ·  branch 52.7%
Coverage cuối phase:  line 69.0%  ·  branch 52.8%   (line giảm nhẹ: thêm nhánh lỗi hiếm)
Migration:            V25__add_idempotency_payload_hash.sql
```

#### 3.1.1 Việc đã làm

| # | Nội dung | File chính |
|---|---|---|
| `F1.1` | `ErrorCode.code()` trả **tên ngữ nghĩa** thay vì mã ngắn; thêm 8 constant theo spec §8.2 | `common/exception/{Auth,Validation,Business}ErrorCode.java` |
| `F1.2` | `ApiResponse.errors`: `Map<String,String>` → `List<FieldErrorResponse>` (`[{field,message}]`) | `common/response/{ApiResponse,FieldErrorResponse}.java` |
| `F1.3` | Thêm 5 exception handler trước đây rơi xuống catch-all 500 | `common/exception/GlobalExceptionHandler.java` |
| `F1.4` | `X-Plant-Id` — cross-check với `plantId` của request | `common/web/PlantContextResolver.java` |
| `F1.5` | `IdempotencySupport` dùng chung + `payload_hash` ⇒ `IDEMPOTENCY_CONFLICT` | `common/idempotency/IdempotencySupport.java`, V25 |
| `F1.6` | Sửa bug implicit INNER JOIN (nợ kỹ thuật #8) | `module/inventory/repository/StockBalanceRepository.java` |

**Test thêm (+8):** `PlantContextResolverTest` (5), `InventoryMovementServiceTest` (2: conflict +
legacy-null-hash), `GlobalExceptionHandlerTest` (1: malformed JSON).
**Test sửa theo `R10`:** `StockBalanceRepositoryIT` (2 assertion đổi từ hành vi sai sang hành vi
đúng), `GlobalExceptionHandlerTest` (shape `errors`), `FlywayMigrationIT` (V24→V25),
`AuthControllerTest` (bỏ magic string `"VAL_001"` — vi phạm `R8` mà `T5` bỏ sót),
5 test class sửa constructor do thêm `IdempotencySupport`.

#### 3.1.2 ⚠️ Breaking Changes (FE phải đổi cùng lúc)

1. **Mọi giá trị `code` trên wire đổi.** `"BIZ_010"` → `"INSUFFICIENT_AVAILABLE_STOCK"`,
   `"VAL_001"` → `"VALIDATION_ERROR"`, `"AUTH_030"` → `"PERMISSION_DENIED"`,
   `"VAL_020"` → `"ENTITY_NOT_FOUND"`… Bảng ánh xạ đầy đủ: `.claude/rules/error-handling.md §5.3`.
   *(Đây đồng thời là **sửa lệch doc↔code**: `error-handling.md §5.3` vốn đã mô tả `code` là tên enum.)*
2. **`errors` đổi từ object sang mảng.** `{"quantity": "..."}` → `[{"field":"quantity","message":"..."}]`.
   Mỗi field có thể xuất hiện nhiều lần (trước đây chỉ giữ lỗi đầu tiên).
3. **Một số lỗi đổi status 500 → 400/405/409.** JSON hỏng, UUID sai ở path, sai HTTP verb,
   `@Validated` fail, optimistic lock — trước đây tất cả đều là 500.
4. **`Idempotency-Key` trùng + payload khác nay trả 409 `IDEMPOTENCY_CONFLICT`** thay vì im lặng
   trả document cũ. Client retry **không được** đổi payload khi giữ nguyên key.
5. **`X-Plant-Id` lệch `plantId` của request ⇒ 409 `STATE_CONFLICT`.** Header vắng mặt vẫn được
   chấp nhận (backward compatible).

#### 3.1.3 Quyết định thiết kế cần biết khi làm `F2`+

| # | Quyết định | Lý do |
|---|---|---|
| D1 | `code()` đổi chuỗi nhưng **giữ nguyên `status()`** | Đổi HTTP status là breaking change nghiệp vụ, thuộc `F2`/`F5`. Xem `CLAUDE.md §0.4` nợ #9 |
| D2 | `payload_hash` **nullable**, NULL ⇒ vẫn replay | Document ghi trước V25 và movement từ child key (`:L<n>`) không có payload riêng — không được đột ngột lỗi |
| D3 | Movement `REVERSAL` **không** stamp `payload_hash` | Nó nhận tham số rời, không có command object; key là dẫn xuất (`:cancel`) |
| D4 | `X-Plant-Id` là **cross-check**, không phải nguồn plantId | `@PreAuthorize` đánh giá `hasResourceAccess(..., 'PLANT', plantId)`; lấy plantId từ header sẽ đưa input phân quyền ra ngoài method-security. Spec §2.3 cũng giữ `plantId` bắt buộc trong body |
| D5 | `GoodsReceiptService.childIdempotencyKey` **giữ nguyên**, không gộp vào `IdempotencySupport.childKey` | Hai thuật toán cắt chuỗi khác nhau (truncate-to-fit vs prefix-112). Gộp sẽ **đổi key sinh ra** cho key dài 112–120 ký tự ⇒ phá idempotency của request đang bay |
| D6 | `X-Plant-Id` mới wire vào 3 controller trên luồng FE (`Mrp`, `PlanningDemand`, `WorkOrder`) | Các controller còn lại sẽ đổi path ở `F5`; wire bây giờ = làm 2 lần. `PlantContextResolver` tái sử dụng nguyên vẹn.<br>⚠️ **`D1` (2026-07-28) sửa lại quyết định này:** vế "các controller còn lại sẽ wire ở `F5`" **sai**. Header chỉ có nghĩa với endpoint **nêu `plantId` tường minh**; endpoint định danh bằng aggregate (`/work-orders/{id}/…`) lấy plant từ chính aggregate nên không có gì để đối chiếu. `F5` chỉ thêm `SalesOrderController` (có `plantId` trong body) là **đúng**, không phải bỏ sót. Ranh giới nay ghi ở `error-handling.md §5.6.1` |
| D7 | `payload_hash` là `VARCHAR(64)`, **không** `CHAR(64)` | `@Column(length=64)` map sang `varchar`; `CHAR` khiến Hibernate schema-validation đỏ (`bpchar` vs `varchar`) — đã bị `FlywayMigrationIT`/`@DataJpaTest` bắt |

---

### `F2` — Production Receipt Lifecycle + QC Disposition ✅ **HOÀN THÀNH 2026-07-26**

```text
Baseline đầu phase:   58 class / 289 case unit  +  3 class / 10 case IT   ·  failures = 0
Kết quả cuối phase:   303 case unit (+14)       +  10 case IT (không đổi) ·  failures = 0
Coverage đầu phase:   line 69.0%  ·  branch 52.8%
Coverage cuối phase:  line 69.3%  ·  branch 53.8%
Migration:            V26__create_quality_control.sql, V27__seed_quality_permissions.sql
```

**Đã làm:**

1. **Vòng đời receipt 3 mốc** (spec §6.1): `post` → `DRAFT`, `submit` → `PENDING_APPROVAL`,
   `approve` → `APPROVED`. `POSTED` đổi tên thành `APPROVED`; `V26` `UPDATE` dữ liệu cũ.
2. **QC disposition**: `POST .../production-receipts/{id}/qc-disposition` với
   `{result: AVAILABLE|REJECTED, reason}`. Lot ra khỏi `HOLD`, **không** đụng `stock_balances`.
3. **Entity `QualityDisposition`** (per-lot, `UNIQUE(receipt_id, lot_id)`) + summary
   `qcResult`/`qcReason`/`qcAt`/`qcBy` denormalise lên `production_receipts` để list endpoint
   không phải load 1 collection cho mỗi dòng.
4. **`MovementType.LOT_STATUS_CHANGE`** + **`MovementDirection.NONE`** — dòng ledger thuần truy xuất.
5. **`PERM_QUALITY_DISPOSITION`** (`V27`), chỉ ADMIN/MANAGER.
6. **`OPERATION_NOT_ALLOWED` → `STATE_CONFLICT` (409)** cho mọi lỗi sai trạng thái của receipt.

**Breaking Changes:**

| # | Thay đổi | Ảnh hưởng FE |
|---|---|---|
| 1 | `ProductionReceiptStatus.POSTED` → `APPROVED`; thêm `DRAFT` | `status` trả về đổi giá trị. Receipt mới tạo nay là `DRAFT`, **không** còn tự động chờ duyệt |
| 2 | `POST /production-receipts` trả `DRAFT` — cần gọi thêm `/submit` | Thêm 1 bước trong luồng FE |
| 3 | Lỗi sai trạng thái receipt: 422 `OPERATION_NOT_ALLOWED` → **409 `STATE_CONFLICT`** | Client bắt theo `code` (rule `A7`) nên chỉ cần thêm nhánh mới |
| 4 | Reject thiếu reason: `MISSING_REQUIRED_FIELD` → **`APPROVAL_REASON_REQUIRED`** (400) | Đổi `code` |
| 5 | Receipt của item lot-tracked thiếu lot: nay lỗi ngay ở `post` với **`LOT_REQUIRED`** (400) | Validate sớm hơn trước |
| 6 | `ProductionReceiptResponse` thêm `outputTrackingMethod`, `submittedAt`, `qcResult`, `qcReason`, `qcAt` | Additive |
| 7 | `MovementDirection` thêm `NONE` | Additive; FE liệt kê direction phải xét nhánh này |

**Quyết định thiết kế cần biết khi làm `F3`+:**

| # | Quyết định | Lý do |
|---|---|---|
| D1 | **Giữ `lines[]`** thay vì collapse về 1 dòng như spec §6.3 | Collapse đụng `production_receipt_lines`, mapper, `WorkOrderVarianceService` + 2 aggregate query — mà `F5` sắp viết lại đúng vùng đó. Ghi thành nợ #11 |
| D2 | `QualityDisposition` nằm trong `module/workorder`, **không** tạo `module/quality` | Nó thuộc aggregate Production Receipt (endpoint treo trên `/{receiptId}`, state machine là của receipt). Tách module ⇒ phải thêm `ProductionReceiptLookupService` chỉ để đi vòng — vi phạm "không abstraction cho 1 use case" |
| D3 | `MovementDirection.NONE` thay vì tái dùng `IN`/`OUT` | QC không đổi on-hand (spec §7). Dùng `IN`/`OUT` sẽ làm `sum(ledger) != balance` |
| D4 | Validate **toàn bộ** lot trước khi đổi lot đầu tiên | Hai dòng dùng chung 1 lot: nếu đổi tuần tự thì dòng 2 thấy lot đã rời `HOLD` và fail giữa đường ⇒ receipt QC dở dang |
| D5 | Sum netting `B16` thêm `DRAFT` | Không có `DRAFT` thì 2 draft đều qua check rồi cùng submit ⇒ vượt `plannedQuantity` |
| D6 | `AuditAction.PRODUCTION_RECEIPT_POSTED` **giữ nguyên tên** dù status đổi | Đổi tên = phải migrate cả `audit_logs` lịch sử. Thêm mới `PRODUCTION_RECEIPT_CREATED` và `QC_DISPOSITION_RECORDED` |
| D7 | `V26` vá luôn `REVERSAL` vào `chk_stock_movements_type` | Constraint (V8) chưa bao giờ có `REVERSAL` dù `reverseReceive()` sinh loại đó ⇒ cancel goods receipt sẽ bị DB từ chối. `F2` phải `DROP/ADD` đúng constraint này nên vá kèm thay vì ship lại constraint đã biết là sai |

**Chưa làm (có chủ đích):** field rename theo spec (`itemCode`→`itemSku`, `lotCode`→`lotNumber`),
`code` (số chứng từ) của receipt, `sourceWipTraceIds`, `*ByUsername` — xem nợ #11/#12 ở
`CLAUDE.md §0.4`. `availableToReceipt` vẫn theo netting cũ vì thuộc `F5` (§2.1).

---

### `F3` — Sales Order + Planning Demand ✅ **HOÀN THÀNH 2026-07-26**

*(Trùng roadmap `P6`, nửa đầu)*

```text
Baseline đầu phase:   58 class / 303 case unit  +  3 class / 10 case IT   ·  failures = 0
Kết quả cuối phase:   60 class / 320 case unit (+17)  +  4 class / 15 case IT (+5) · failures = 0
Coverage đầu phase:   line 69.3%  ·  branch 53.8%
Coverage cuối phase:  line 69.5%  ·  branch 54.3%
Migration:            V28__create_sales_order.sql, V29__seed_sales_permissions.sql
```

#### 3.3.1 Việc đã làm

| # | Nội dung | File chính |
|---|---|---|
| `F3.1` | Module `module/sales/` — `SalesOrder` + `SalesOrderLine` + 6 trạng thái | `module/sales/domain/**`, `V28` |
| `F3.2` | CRUD + state machine `DRAFT`→`CONFIRMED`→`CANCELLED`, sai trạng thái ⇒ `STATE_CONFLICT` (409) | `module/sales/service/SalesOrderService.java` |
| `F3.3` | `confirm` sinh **1 `PlanningDemand` / line** (`demandType = SALES_ORDER`, `referenceType = SALES_ORDER_LINE`); `cancel` huỷ demand còn `OPEN` | `PlanningDemandService.createFromSalesOrderLine` / `.cancelOpenDemandsForSalesOrderLines` |
| `F3.4` | `GET /sales-orders/planning-demands` — cả 4 điều kiện spec §2.1 trong **1** JPQL, trả open quantity | `SalesOrderLineRepository.findEligiblePlanningDemands` |
| `F3.5` | `PERM_SALES_ORDER_READ` / `PERM_SALES_ORDER_MANAGE` (ADMIN+MANAGER; OPERATOR chỉ READ) | `V29` |

**Test thêm (+17 unit, +5 IT):** `SalesOrderServiceTest` (10), `SalesOrderMethodSecurityTest` (7),
`SalesOrderLineRepositoryIT` (5 — mỗi điều kiện eligibility có 1 case loại trừ riêng, chạy JPQL thật).
**Test sửa theo `R10`:** `FlywayMigrationIT` (V27→V29). Không test cũ nào phải sửa ngoài đó.
**Nghiệm thu `R2`:** đổi `PERM_SALES_ORDER_MANAGE` → chuỗi rác ⇒ `SalesOrderMethodSecurityTest`
đỏ 3 case + `PermissionCatalogTest` đỏ. Đã chạy, đã revert.

#### 3.3.2 ⚠️ Breaking Changes

**Không có.** `F3` chỉ thêm endpoint/bảng/permission mới; không đổi contract nào đang tồn tại.

#### 3.3.3 Khác kế hoạch ban đầu (có chủ đích)

1. **Không tạo `SalesOrderLookupService`.** Kế hoạch giả định chiều `planning → sales`; thực tế chiều
   phát sinh là `sales → planning` (confirm ⇒ tạo demand), còn endpoint `planning-demands` nằm trong
   chính `module/sales` nên đọc dữ liệu của mình. Tạo lookup service không người dùng vi phạm
   `coding-rules.md §11.5`. Rule `C7` vẫn giữ đúng: sales gọi `PlanningDemandService`, không chạm
   `PlanningDemandRepository`. Chi tiết: `module/sales/CLAUDE.md` mục 1.
2. **`lineNo` do server cấp** (1..N theo thứ tự request) thay vì client gửi — chặn đứng khả năng phá
   UNIQUE `(sales_order_id, line_no)`.
3. **Thêm `cancel` demand khi huỷ đơn `CONFIRMED`** (không có trong kế hoạch). Thiếu bước này thì
   MRP tiếp tục lập kế hoạch cho đơn đã huỷ — lỗ hổng toàn vẹn thật, không phải mở rộng phạm vi.
4. **Lệch tên có chủ đích:** `orderNo` (CRUD, theo kế hoạch) vs `salesOrderCode` (màn hình Planning,
   theo spec §2.4) — cùng một cột `sales_orders.order_no`.

#### 3.3.4 Nợ mới phát sinh

`POST /planning-runs` vẫn tự quét demand theo horizon, **chưa** nhận `demandLineIds` như spec §2.3.
FE xem được danh sách chọn nhưng chưa gửi lựa chọn ngược lại được ⇒ nợ #13 trong `CLAUDE.md §0.4`,
xử lý ở `F5`.

---

### `F4` — Routing Tối Thiểu ✅ **HOÀN THÀNH 2026-07-27**

*(Trùng roadmap `P4` một phần — **không** làm Work Center capacity/CRP, spec §11 ghi rõ ngoài MVP)*

#### 3.4.1 Đã làm

1. Module `module/routing`: `RoutingHeader` (`code`, `routingVersion`, `status`
   `DRAFT`/`ACTIVE`/`INACTIVE`) + `RoutingOperation` (`sequence`, `name`, `workCenterCode`,
   `setupMinutes`, `runMinutesPerUnit`). Operations nhập **inline** khi create.
   Endpoint: `POST /companies/{companyId}/routings`, `GET .../routings`, `GET /routings/{id}`,
   `POST /routings/{id}/activate`, `DELETE /routings/{id}`.
2. Snapshot bất biến lên `work_orders`: `source_routing_id` / `source_routing_code` /
   `source_routing_version` / `routing_captured_at` — cột phẳng, **không** `@ManyToOne`.
3. `BusinessErrorCode.MISSING_ROUTING` (409) chặn `WorkOrderService.createFromMrp`.
4. `PERM_ROUTING_READ` / `PERM_ROUTING_MANAGE` (`V31`), ADMIN+MANAGER manage, OPERATOR read.
5. Migration `V30__create_routing.sql`, `V31__seed_routing_permissions.sql`.
   Test: 320 → **347 case unit / 63 class**, IT giữ 15 case (`FlywayMigrationIT` pin `31`).

#### 3.4.2 Hai quyết định đã chốt với user (2026-07-27)

| Câu hỏi | Chốt | Hệ quả |
|---|---|---|
| Snapshot header hay cả operations? | **Chỉ header** | Spec §3.3 chỉ liệt kê 4 field header ở mục "Routing snapshot"; bảng `work_order_operations` để `F5` làm cùng lúc thiết kế execution, tránh tạo bảng chưa ai đọc |
| `MISSING_ROUTING` chặn ở đâu? | **Chỉ đường convert proposal** | `createFromMrp` đòi routing `ACTIVE`; `POST /work-orders` thủ công giữ nguyên ⇒ **không** breaking change, không phải sửa test WO cũ |

#### 3.4.3 Điểm khác kế hoạch ban đầu

1. Kế hoạch ghi bất biến là "mỗi `(item, version)` chỉ 1 routing `ACTIVE`" — thực tế implement theo
   Definition of Done và tiền lệ BOM (`B7`): **1 routing `ACTIVE` / `(company, item)`**, còn
   `(company, code, version)` là UNIQUE của chứng từ. Thêm partial unique index
   `uk_routings_one_active_per_item` để chống race.
2. `createFromMrp` **không còn** gọi lồng vào `create` (tách `createInternal`). Đây là lúc phát hiện
   self-invocation qua Spring proxy vốn đã khiến `PERM_WORK_ORDER_MANAGE` không được áp dụng cho
   đường MRP — nay tường minh và có test khoá `PERM_SUPPLY_SUGGESTION_MANAGE`.
3. Field entity phải đặt tên `routingVersion` (cột `routing_version`) vì `BaseEntity` đã chiếm
   `version` cho optimistic locking. DTO vẫn expose `version` ra wire theo spec.
4. Cả **hai** đường tạo WO đều snapshot routing khi có — chỉ khác nhau ở chỗ có bắt buộc hay không.

#### 3.4.4 Nợ mới phát sinh

`MISSING_ROUTING` trả **409** nhưng `MISSING_BOM` (thực chất `RESOURCE_NOT_FOUND` từ
`BomLookupService`) vẫn **404**, dù spec §8.1 coi hai lỗi cùng loại ⇒ nợ #14 trong `CLAUDE.md §0.4`,
xử lý ở `F5` cùng lúc căn lại status của nợ #9.

---

### 3.5 `F5` — Reshape Planning + Work Order + Execution ✅ **HOÀN THÀNH**

> **Chia đôi khi thực thi** vì phần Work Order đủ lớn để đứng một mình:
> - **`F5-A`** (mục 2–7 dưới đây) ✅ **HOÀN THÀNH 2026-07-27** — `V32`/`V33`, 356 test unit,
>   failures = 0. Bản ghi chi tiết: `CLAUDE.md §0.9` (endpoint + 6 hệ quả) và
>   `module/workorder/CLAUDE.md` (bất biến `B53`-`B57`, `B16`/`B17` viết lại).
> - **`F5-B`** (mục 1 dưới đây — Planning) ✅ **HOÀN THÀNH 2026-07-28** — `V34`, 365 test unit /
>   16 test IT, failures = 0. Bản ghi chi tiết: `CLAUDE.md §0.10` (bảng đổi endpoint + 6 hệ quả) và
>   `module/planning/CLAUDE.md` (bất biến `B58`-`B61` + 5 quyết định thiết kế).
>
> **`F5-B` khác kế hoạch ban đầu ở 3 điểm, có chủ đích:**
> 1. Suggestion actions nằm ở `/api/v1/supply-suggestions/{id}/…` (phẳng), **không** lồng dưới run
>    như spec §2.2 đề xuất — `runId` không cần để định danh suggestion và `purchasing` treo action
>    của nó lên cùng resource.
> 2. `settingSource` + `excludedLotCount` là **dữ liệu thật** (không phải nợ mới như kế hoạch dự
>    phòng): `hasItemWarehouseSetting` lấy từ chính nhánh fallback sẵn có của
>    `InventoryAvailabilityService`, `excludedLotCount` từ aggregate query mới
>    `StockBalanceRepository.aggregateExcludedLotCounts`. Hai field này nằm trên **requirement line**
>    theo spec §2.4, không phải trên proposal như kế hoạch viết nhầm.
> 3. `demandLineIds` **tuỳ chọn** (spec đánh dấu bắt buộc) để không phá client hiện có.
>
> **Nợ mới phát sinh ở `F5-B`:** #15 (`MrpRun` chưa có `code` + 4 ô summary spec §2.4) và #16
> (open supply chưa gồm purchase order đang mở) — xem `CLAUDE.md §0.4`.
>
> **Khác kế hoạch ban đầu ở 3 điểm, có chủ đích:**
> 1. `completedQuantity` **không đổi tên** thành `actualGoodQuantity`; thêm cột mới thay vì diễn giải
>    lại dữ liệu lịch sử (đúng đề xuất ở mục 6 kế hoạch gốc).
> 2. Nợ #11 (collapse `lines[]`): **wire contract** đã phẳng, nhưng bảng
>    `production_receipt_lines` giữ nguyên làm storage — collapse bảng là migration phá dữ liệu
>    lịch sử mà không đổi hành vi.
> 3. `wip_transactions.stage_code` **không bị bỏ**; thêm FK `work_order_operation_id` bên cạnh.
>    `stage_code` vẫn là free text cho work order không có routing, và được **suy ra** từ operation
>    khi có FK.

*(Phase lớn nhất — phụ thuộc `F3` + `F4`)*

1. **Planning:** đổi `/mrp/runs` → `/planning-runs`; `SupplySuggestionType` `WORK_ORDER`/
   `PURCHASE_REQUISITION` → expose `supplyType` `MAKE`/`BUY`; thêm `exceptionState`
   `READY`/`WARNING`/`BLOCKED` + `messages[]` (message code spec §8.1); thêm `convertedWorkOrderId`
   để chặn convert lặp; expose `settingSource` + `excludedLotCount`.
2. **Work Order:** thêm status `PLANNED` (checklist `coding-rules.md §11.3`); readiness trả
   `canRelease` + `reservedPercent` + `shortageLineCount` (hiện chỉ có `ready` + shortage theo line —
   `WorkOrderReleaseGate.evaluate`); expose `blockedAt`/`blockReason` (entity đã lưu, mapper đang bỏ).
3. **Reserve:** thêm `POST /work-orders/{id}/reserve` tự động (FEFO) — giữ endpoint reserve thủ công
   hiện có làm đường dự phòng.
4. **Material Issue:** endpoint phẳng `POST /material-issues` body `{workOrderId, reservationId, quantity}`
   (spec §4.1). Giữ service đa dòng bên dưới, controller mới chỉ là 1 dòng.
5. **Production Execution:** entity mới `ProductionExecution`
   (`goodQuantity`, `scrapQuantity`, `reworkQuantity`, `actualStartedAt`, `actualEndedAt`, `notes`,
   `operatorUserId`, `traceId`). Cộng dồn atomically lên WO: `actualGoodQuantity`,
   `actualScrapQuantity`, `actualReworkQuantity`. **Vẫn ghi `WipTransaction`** để giữ ledger + không
   phá `WorkOrderVarianceService`.
6. **Đảo ngược ngữ nghĩa (§2.1):** WO `COMPLETED` khi cumulative good = planned (từ execution);
   receipt bị chặn bởi `availableToReceipt = actualGood − receipted`. Viết lại `B16`, tách `B17`.
7. **`traceId` trên document** — persist ở `MaterialIssue`, `ProductionExecution`, `ProductionReceipt`,
   `StockMovement`; `sourceWipTraceIds` nối WIP→Receipt.
   ⚠️ Mâu thuẫn `.claude/rules/error-handling.md §5.1` ("traceId chỉ qua header") ⇒ phải sửa rule
   đó: header dùng cho correlate log, field dùng cho trace nghiệp vụ. Hai mục đích khác nhau.

*Migration dự kiến:* `V2x__create_production_execution.sql` (+ reshape WO status/CHECK).
*Breaking:* nhiều nhất trong tất cả các phase — ghi đầy đủ vào `NEXT_PHASE_PLAN.md` khi tới lượt.

---

### `F6` — Fulfillment Allocation ✅ **HOÀN THÀNH 2026-07-28**

*(Trùng roadmap `P6`, nửa sau — phụ thuộc `F2` + `F3`)*

1. ✅ `WorkOrderDemandAllocation` (`workOrderId`, `salesOrderLineId`, `allocatedQuantity`,
   `fulfilledQuantity`) — `module/workorder`, `salesOrderLineId` là **cột phẳng** (không
   `@ManyToOne`) để không đóng vòng phụ thuộc compile-time `sales → planning → workorder → sales`.
2. ✅ Fulfillment **chỉ chạy khi QC `AVAILABLE` commit** (spec §7.1) — hook trong
   `ProductionReceiptService.qcDisposition`. `approve` và nhánh `REJECTED` có test `verifyNoInteractions`.
3. ✅ Phân bổ theo `dueDate` → `lineNo` → `salesOrderLineId` (thứ tự **xác định**, không phụ thuộc DB);
   `fulfilledQuantity` không vượt `allocatedQuantity` lẫn `openQuantity` của SO line; phần dư bị bỏ.
4. ✅ `SalesOrderStatus` roll-up: `IN_PRODUCTION` (lúc convert) → `PARTIALLY_FULFILLED` → `FULFILLED`.
5. ✅ `WorkOrderResponse.allocations[]` + `SalesOrderLineResponse.fulfilledQuantity` có giá trị thật.

*Migration:* `V35__create_work_order_demand_allocation.sql`. **Không** thêm permission mới —
fulfillment là hệ quả của `PERM_QUALITY_DISPOSITION`.

**Giới hạn còn lại:** output **không** lot-tracked không qua QC được (`F2`) ⇒ không fulfill được.
Ghi thành nợ #17 ở `CLAUDE.md §0.4` theo quyết định của user (2026-07-28).

---

### `F7` — Đối Chiếu Spec Toàn Diện + Đóng 5 Gap ✅ **HOÀN THÀNH 2026-07-31**

```text
Baseline đầu phase:   86 class / 516 case unit  +  6 class / 30 case IT   ·  failures = 0
Kết quả cuối phase:   521 case unit (+5)        +  37 case IT (+7, 1 class IT mới) ·  failures = 0
Migration:            V38__add_work_order_cancel_reason.sql
```

**Vì sao phase này tồn tại.** `F1`–`F6` tuyên bố đóng track mà bằng chứng phủ spec duy nhất là kịch
bản E2E 11 bước (§11.1). Một kịch bản happy-path không thể phát hiện một endpoint *không tồn tại* hay
một field bị đặt sai tên. Cộng thêm `.docx` là binary **chưa từng được commit**, mỗi phase chỉ đọc
phần spec liên quan tới mình ⇒ **§9 chưa từng được nhắc trong repo**, và 5 gap nằm ẩn suốt 5 phase.

#### Việc đã làm

| # | Gap | Spec § | Nội dung |
|---|---|---|---|
| 1 | `GET /production-executions/candidates` **không tồn tại** | §5.1 | Endpoint mới + `WorkOrderRepository.findExecutionCandidates` + `ProductionExecutionCandidateResponse`. **Tái dùng `PERM_PRODUCTION_EXECUTION_READ`** (guard đổi sang plant-scope) ⇒ không permission mới. Bất biến **`B75`** |
| 2 | Rename field mới làm 4/17 DTO | §6.4 | **Phương án A**: đổi 7 DTO trên luồng spec, giữ 17 DTO ngoài luồng |
| 3 | `cancel` không nhận `reason` | §3.2 | `V38` + `WorkOrderCancelRequest`; thiếu ⇒ `APPROVAL_REASON_REQUIRED` (400) |
| 4 | `actualStartedAt`/`actualEndedAt` optional | §5.1 | `@NotNull`. Validate **thứ tự** vốn đã có (`ensureChronological`) — không đụng |
| 5 | `GET /work-orders` thiếu `search` | §3.2 | Thêm predicate free-text trên `workOrderNo` + product SKU |
| — | **Bảng đối chiếu §1..§11** | — | Mục **§7** mới + `docs/fe-spec-omniplant.md` (bản trích spec) |

#### Hệ quả cần nhớ khi code tiếp

1. 🔴 **Bộ lọc candidates có HAI vế, không phải một** (`B75`): `status ∈ {RELEASED, IN_PROGRESS}`
   **và** `actualGoodQuantity < plannedQuantity`. Vế thứ hai không phải suy diễn — §5.1 cấm cumulative
   good vượt plan, nên WO đã đạt plan không nhận report được nữa và không được xuất hiện. Lọc theo
   status không thôi vẫn "trông đúng" — mutation #1 sinh ra để canh đúng chỗ đó.
2. 🔴 **Bộ lọc nằm trong JPQL ⇒ mock repository không kiểm được** (`R7`). Vì vậy `F7` tạo
   **`WorkOrderRepositoryIT`** (7 case, Testcontainers) — nếu chỉ viết unit test với mock thì cả
   `B75` lẫn `search` đều là tautology.
3. **`ManufacturingExecutionController` nay CÓ nhận `X-Plant-Id`** — đúng theo chính rule §5.6.1
   (endpoint nêu `plantId` tường minh thì có cross-check), không phải phá lệ. Ghi chú cũ ở
   `error-handling.md §5.6.1` ("controller này cố ý không nhận header") đã được sửa lại cho khớp.
4. **Ranh giới rename (phương án A) — phase sau đừng "đồng bộ" nốt.**
   **Đã đổi (7):** `MrpRequirementLineResponse`, `PlanningDemandResponse`, `SupplySuggestionResponse`,
   `WorkOrderMaterialReadinessLineResponse`, `MaterialIssueLineResponse`, `MaterialReservationResponse`,
   `MaterialIssueLineRequest`.
   **Cố ý giữ tên cũ (17):** `BomTreeNodeResponse` · `inventory/{InventoryAlertLineResponse,
   ItemWarehouseSettingResponse, StockAdjustRequest, StockBalanceResponse, StockIssueRequest,
   StockMovementResponse, StockReceiveRequest}` · `purchasing/{GoodsReceiptLineRequest,
   GoodsReceiptLineResponse, ItemSupplierResponse, PurchaseOrderLineResponse,
   PurchaseRequisitionLineResponse}` · `workorder/execution/{WorkOrderComponentIssueRequest,
   WorkOrderOutputCompletionRequest}`. Spec **không mô tả màn hình** cho chúng.
5. ⚠️ **Rename gần như không được test bảo vệ.** Đổi 6 response DTO mà chỉ **một** assertion trong
   toàn bộ test suite phát hiện (`PlanningDemandControllerTest`). Chỗ đó nay assert cả tên mới **và**
   `itemCode` `doesNotExist()` — nhưng 5 DTO còn lại vẫn không có test tầng HTTP nào chạm tới tên field.
   Ai thêm controller test cho `planning`/`workorder` sau này nên assert tên field, không chỉ status.
6. **§9 đã kiểm, code hợp lệ — đừng "sửa" nó.** §9.3/§9.4 mô tả response ghép nhiều nhánh, nhưng câu
   cuối §9 **cho phép** trả entity chính rồi FE refetch, miễn commit atomically. Ràng buộc thật là
   §10.1 (7 mutation nguyên tử) — đã đạt.
7. `cancel_reason` **nullable**: `NULL` nghĩa là "cancel trước `F7`", **không** phải "không có lý do".

#### Nghiệm thu mutation (3, mỗi cái đỏ đúng 1 case, đã revert)

| # | Mutation | Case đỏ | Ghi chú |
|---|---|---|---|
| 1 | Bỏ `actualGoodQuantity < plannedQuantity` khỏi query candidates (**`src/main`**) | `WorkOrderRepositoryIT.findExecutionCandidates_excludesWorkOrdersThatAlreadyReachedTheirPlan` | Chỉ đổi query chính, giữ `countQuery` — đúng kiểu lỗi thật sẽ xảy ra |
| 2 | Bỏ `@NotNull` khỏi `actualEndedAt` (**`src/main`**) | `ManufacturingExecutionControllerTest.reportProduction_withoutActualTimestamps_returns400` | |
| 3 | Cancel thiếu reason: `APPROVAL_REASON_REQUIRED` → `INVALID_INPUT` | `WorkOrderServiceTest.cancel_withoutReason_failsBeforeTouchingAnything` | **Cả hai đều `HttpStatus.BAD_REQUEST`** ⇒ assert bám `ErrorCode`, không phải status |

> **2/3 mutation đụng `src/main`**, và mutation #3 giữ nguyên HTTP status (cảnh báo lặp từ §0.15–§0.17).

#### Breaking Changes

| # | Endpoint | Cũ | Mới |
|---|---|---|---|
| 1 | `POST /work-orders/{id}/cancel` | không cần body | **bắt buộc** `{reason}`; thiếu ⇒ 400 `APPROVAL_REASON_REQUIRED` |
| 2 | `POST /work-orders/{id}/production-executions` | timestamps optional | **bắt buộc**; thiếu ⇒ 400 `VALIDATION_ERROR` |
| 3 | 6 response DTO trên luồng spec | `itemCode` / `lotCode` | `itemSku` / `lotNumber` |
| 4 | `POST /work-orders/{id}/material-issues` (đa dòng) | request `lotCode` | `lotNumber` |
| 5 | *(Java, không phải wire)* `WorkOrderResponse` thêm `cancelReason` (vị trí **31**) | — | mọi call site positional phải sửa |

Test cũ **sửa** theo `R10`, không xoá: `WorkOrderServiceTest` (2 chỗ `cancel`), `WorkOrderControllerTest`
+ `SupplySuggestionServiceTest` (positional `WorkOrderResponse`), `ManufacturingExecutionControllerTest`
(`REPORT_BODY` + `@Import(PlantContextResolver)`), `PlanningDemandControllerTest` (`itemCode`→`itemSku`),
`ProductionFlowE2EIT` (4 chỗ timestamps + 1 chỗ cancel reason), `FlywayMigrationIT` (`V37`→`V38`).

---

### 3.8 `F8` — Đóng gap field-level + 4 endpoint (additive) ✅ **HOÀN THÀNH 2026-07-31**

Migration **`V39`**. **Không** permission mới, **không** rename, **không** breaking change trên wire.
Baseline 521 unit / 37 IT → **545 unit / 53 IT**, failures = 0.
Coverage unit+IT: 79.5% → **80.0%** line, 63.1% → **63.5%** branch (unit một mình: **73.5%** / **58.9%**).

**Nhóm A — 4 endpoint spec đòi mà repo không có:**

| # | Endpoint | Permission (sẵn có) | `X-Plant-Id` |
|---|---|---|---|
| A1 | `GET /api/v1/production-receipts/candidates?plantId=` | `PERM_PRODUCTION_RECEIPT_MANAGE` / `PLANT` | ✅ có |
| A2 | `GET /api/v1/production-receipts?plantId=&status=` | `PERM_PRODUCTION_RECEIPT_MANAGE` / `PLANT` | ✅ có |
| A3 | `GET /api/v1/material-issues?plantId=&workOrderId=` | `PERM_MATERIAL_ISSUE_MANAGE` / `PLANT` | ✅ có |
| A4 | `GET /api/v1/production-executions?workOrderId=` | `PERM_PRODUCTION_EXECUTION_READ` | ❌ **cố ý không** |

**Nhóm B — field bổ sung, không migration:** `uom` lên 7 DTO · `workOrderCode` lên receipt/issue/execution ·
`itemName` lên receipt + issue line · `operatorUsername` + `createdByUsername` (batch, `C14`) ·
`outputLotStatus` · `workOrderRemainingGoodQuantity` + `workOrderCompletionPercent` ·
`bomCapturedAt` + `executionCompletedAt` (**alias, không cột mới**).

**Nhóm C — `V39`:** `work_orders.execution_started_at` + 3 cột lineage phẳng
(`planning_run_id`/`planning_run_code`/`planning_proposal_id`, **không FK**) ·
`mrp_runs.gross_demand_quantity`.

**Nhóm D — cố ý CẮT** (4 mục, thành nợ **E**–**H** ở §7.1): `sourceBomCode` + BOM `outputQuantity` ·
proposal `sourceRoutingCode`/`sourceRoutingVersion` · requirement `projectedAvailable` ·
`MaterialIssue.code` / `ProductionExecution.code` / execution `status`.

**Nhóm E — nợ A đóng:** test `CONCURRENT_MODIFICATION` ở **2 tầng** (§7 hàng §10.3).

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`B76` là ba vế, và vế status là NGƯỢC với `B75`.** Receipt-candidate gồm **`COMPLETED`**;
   execution-candidate loại nó. Bỏ `COMPLETED` khỏi `B76` = dựng lại đúng nợ #25 (`D11`) ở tầng read
   model: dòng receipt **cuối cùng** của mọi WO biến mất khỏi màn hình. Hai query trông giống nhau và
   trả lời hai câu hỏi khác nhau — đừng copy cái này thành cái kia.
2. 🔴 **Vế "trừ receipt đang mở" nằm trong JPQL, không trong mapper.** `WorkOrder.availableToReceipt()`
   **không** trừ receipt mở (`B16` để việc đó cho caller) ⇒ mapper một mình không ra được con số dòng.
   Netting đặt trong query để gate có **một** định nghĩa; batch
   `sumOpenQuantityByWorkOrderIds` chỉ để **hiển thị** đúng con số đó. 2 query/trang, **0** query/dòng.
3. 🔴 **A4 uỷ quyền từ CONTROLLER, không từ service.** Bản nháp đầu thêm
   `ProductionExecutionService.listFlat()` gọi `this.list(...)` — self-invocation **bypass Spring proxy**
   ⇒ `@PreAuthorize` trên `list` **không chạy**, endpoint thành công khai. Controller gọi thẳng `list`.
   Đây là lỗ hổng bảo mật thật, không phải chuyện style.
4. 🔴 **`X-Plant-Id` theo ENDPOINT, không theo controller** — phase thứ **ba** chạm bẫy này.
   `ManufacturingExecutionController` nay chứa **cả hai** loại. A4 có test gửi header **lệch** mà vẫn
   **200** để pin ranh giới; comment không đỏ được.
5. **Không mint `PERM_*_READ`.** `V17` seed đúng **một** permission/loại chứng từ, mô tả nguyên văn
   *"Post **and read** … documents"*. ⇒ **không** seed migration, **`docs/roles-and-permissions.md`
   KHÔNG đổi** — ghi ra đây để người đọc sau không tưởng là bị quên.
   🔴 **Hệ quả phải chấp nhận:** user chỉ có `PERM_QUALITY_DISPOSITION` **không** mở được danh sách
   receipt, trong khi §6.2 ghi "một trong các quyền receipt/QC". Sửa đúng chỗ là **role seed**, là
   quyết định **nới quyền**, ngoài phạm vi `F8`.
6. **`@EntityGraph` phải nới TRƯỚC khi thêm field.** `ProductionExecutionRepository` chỉ fetch
   `operation`; mọi field mới đi qua `execution.getWorkOrder()`. Làm ngược ⇒ N+1 trên endpoint phẳng,
   và first-level cache của endpoint **nested** (mọi dòng chung 1 WO) **che mất** lỗi khi test.
7. **Hai số Summary đặt trên `ProductionExecutionResponse`, không trên `WorkOrderResponse`** — ở đó
   `workOrderRemainingGoodQuantity` (planned − **actualGood**) sẽ nằm cạnh `remainingQuantity`
   (planned − **completed**). Hai số gần nghĩa trong một record là tối đa hoá nhầm lẫn.
8. **`bomCapturedAt`/`executionCompletedAt` KHÔNG có cột.** BOM là `optional = false` và chụp lúc tạo
   ⇒ `createdAt` **chính là** mốc; `complete()` có **đúng 1** call site (`B53`) ⇒ `completedAt`
   **chính là** execution-completed-at. Bất đối xứng với `routing_captured_at` là **có lý do** (routing
   tuỳ chọn, có thể vắng) — đừng "thêm cột cho đồng bộ".
9. **`grossDemandQuantity` chỉ cộng `level == 0`.** Cấp sâu hơn được **dẫn xuất** từ đó qua BOM, cộng
   hết mọi cấp thì cùng một nhu cầu bị đếm một lần cho mỗi cấp BOM.

**Nghiệm thu mutation (4, đã revert — 4/4 đụng `src/main`):**

| # | Mutation | Case đỏ | Vì sao mạnh |
|---|---|---|---|
| 1 | Bỏ số hạng "trừ receipt đang mở" khỏi query candidates | `WorkOrderRepositoryIT.findReceiptCandidates_excludesWorkOrdersFullyClaimedByAnOpenDraft` | Không mock nào với tới được — mạnh nhất phase |
| 2 | Batch nhầm `workOrder.getCreatedBy()` thay `getOperatorUserId()` | `ProductionExecutionServiceTest.list_resolvesTheOperatorUsernameInOneBatchQuery` | **HTTP vẫn 200**, envelope vẫn `SUCCESS`, payload sai **âm thầm** |
| 3 | `CONCURRENT_MODIFICATION` → `STATE_CONFLICT` | `GlobalExceptionHandlerTest.optimisticLockConflict_returns409ConcurrentModification` | **Cả hai đều 409** — chỉ `$.code` đổi. Lý do `R8` cấm literal |
| 4 | `completionPercent` `HALF_UP` → `FLOOR` | `WorkOrderTest.completionPercent_roundsToTwoDecimalsHalfUp` | Chặn đúng lỗi `R6` (`isNotNull()`) |

> ⚠️ #2, #3, #4 giữ nguyên HTTP status có chủ đích (cảnh báo lặp từ §0.15–§0.18): mutation chỉ đổi
> status là bài **dễ**, xanh cũng không chứng minh gì.

**Breaking changes — wire: KHÔNG có cái nào.** Thuần additive. **Java positional: có** — mọi
`new X(...)` của `WorkOrderResponse`, `ProductionExecutionResponse`, `ProductionReceiptResponse`,
`MaterialIssueResponse`, `MaterialIssueLineResponse`, `WorkOrderComponentLineResponse`,
`MrpRunResponse`, `MrpRequirementLineResponse`, `SupplySuggestionResponse`. Chữ ký đổi:
`WorkOrderService.createFromMrp` 3 → **4** tham số (thêm `PlanningLineage`) · `MrpRun.complete(...)`
8 → **9** · `ProductionExecutionService` + `MaterialIssueService` + `ProductionReceiptService` đổi
constructor · `ManufacturingExecutionMapper.toResponse(...)` của execution/issue nay nhận
`Map<UUID,String> usernames`. Test cũ **sửa** theo `R10`: `SupplySuggestionServiceTest` (verify nay
khoá **cả** lineage), `WorkOrderControllerTest`, `PlanningRunControllerTest`,
`ManufacturingExecutionControllerTest`, `WorkOrderServiceTest`, `WorkOrderMethodSecurityTest`, 3
`*ServiceTest`, `ManufacturingExecutionMethodSecurityTest`, `FlywayMigrationIT` (`V38`→`V39`).

---

### 3.9 `F9` — 2 field cuối + sửa tuyên bố nói quá của `F8` ✅ **HOÀN THÀNH 2026-07-31**

**Không migration**, không permission, không rename, không breaking change trên wire.
545 unit / 53 IT → **549 unit / 57 IT**, failures = 0.
Coverage unit+IT: 80.0% → **80.1%** line, 63.5% → **63.5%** branch (unit: **73.7%** / **58.9%**).

🔴 **Vì sao phase này tồn tại — quan trọng hơn 2 field nó đóng.** `F8` viết vào roadmap §7 rằng đã
"**Đã đi hết bảng field**" cho §3.3 và §5.2, và vào `CLAUDE.md §0.1` rằng "không còn hàng nào ở trạng
thái thiếu". **Cả hai đều sai**, và rà lại cùng ngày chứng minh điều đó. Đây là **lần thứ ba** repo
mắc đúng lỗi này:

| Lần | Ai tuyên bố | Thực tế | Hệ quả |
|---|---|---|---|
| 1 | `D7`: nợ #9 "đóng hết" | Phạm vi rà là 4 module, `planning` chưa bao giờ nằm trong đó | nợ #26 → `D11` |
| 2 | `F7`: "đối chiếu spec toàn diện", ✅ cho 5 section | Phạm vi thật là grep `itemCode`/`lotCode` | 5 gap → `F8` |
| 3 | `F8`: "đã đi hết bảng field" | Bám **danh sách 7 DTO trong kế hoạch của chính nó**, không bám bảng spec | 2 field → `F9` |

**Gap đã đóng:**

| # | Field | Spec | Ghi chú |
|---|---|---|---|
| 1 | `uom` trên `ProductionExecutionCandidateResponse` | §5.2 "Context" (ô thứ 4/4) | **0 query** — `productItem` vốn đã `join fetch`, mapper vốn đã dereference nó. 🔴 `F8` **đã nhìn thấy ô này và cố ý bỏ qua** |
| 2 | `reservedQuantity` trên `WorkOrderComponentLineResponse` | §3.3 "Requirement" (5/6 → 6/6) | Trước `F9` số này **chỉ** có ở `/material-readiness` ⇒ FE phải gọi **2 API cho một bảng** |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`reservedQuantity` phải là ĐÚNG CÙNG số của `/material-readiness`** —
   `sumActiveRemaining*` (`sum(quantity − consumedQuantity)` của reservation `ACTIVE`). Hai màn hình
   hiển thị hai con số khác nhau dưới **cùng một tên** tệ hơn cả việc thiếu field. Javadoc `@link` hai
   chiều giữa `WorkOrderComponentLineResponse` và `WorkOrderMaterialReadinessLineResponse` nói rõ:
   đổi thì đổi cả hai.
2. 🔴 **Batch phải nằm NGOÀI `page.map(...)`, và cả HAI đường của `WorkOrderService` đều cần nó.**
   `toResponse(WorkOrder)` là chỗ **mọi** response 1-work-order đi qua (9 call site: create / update /
   release / plan / cancel / get…). Chỉ sửa `list` thì detail đúng nhưng đường mutation trả `null` ⇒
   FE thấy field lúc có lúc không.
3. **`sumActiveRemainingByWorkOrderIds` tái dùng `ComponentQuantityProjection` KHÔNG đổi** và **không**
   mang `workOrderId`: `componentLineId` duy nhất toàn cục ⇒ một `Map` phẳng đủ cho cả trang.
   `MaterialReservationRepositoryIT` có case riêng chứng minh điều đó.
4. **`group by` không trả hàng cho line không có reservation** (không trả 0) ⇒ mapper
   `getOrDefault(..., ZERO)`. Cùng cái bẫy `ProductionReceiptRepositoryIT` của `F8` đã pin.
5. **`childBom` (§3.3) là nợ `I`, không phải thiếu sót** — WO snapshot direct-only theo `B12`
   (user chốt 2026-07-31).
6. **`F9` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi.**

**Nghiệm thu mutation (3, đã revert — 3/3 đụng `src/main`):**

| # | Mutation | Case đỏ | Vì sao mạnh |
|---|---|---|---|
| 1 | Bỏ `status = ACTIVE` khỏi query batch | `MaterialReservationRepositoryIT.sumActiveRemainingByWorkOrderIds_countsActiveReservationsOnly` | Mock không với tới được — reservation đã release/consume bị đếm lại ⇒ readiness nói dối |
| 2 | `toCandidateResponse` truyền `getName()` thay `getUnit()` | `ProductionExecutionServiceTest.listCandidates_*` | **HTTP vẫn 200**, payload sai âm thầm. Controller test **không** đỏ (nó stub service) — đúng: contract test giữ wire shape, service test giữ mapping |
| 3 | Chuyển batch **vào trong** `page.map(...)` (1 query/dòng) | `WorkOrderServiceTest.list_resolvesReservedQuantityInOneBatchQueryForThePage` | Output **byte-identical**, chỉ số query đổi — đúng loại lỗi `C14`/`C15` sinh ra để chặn |

**Breaking changes — wire: KHÔNG có.** **Java positional:** `ProductionExecutionCandidateResponse`
(+`uom`), `WorkOrderComponentLineResponse` (+`reservedQuantity`), `WorkOrderMapper.toResponse(...)`
+1 tham số, `WorkOrderService` constructor +1 (`MaterialReservationRepository`). Test cũ **sửa** theo
`R10`: `ManufacturingExecutionControllerTest`, `WorkOrderServiceTest`, `WorkOrderMethodSecurityTest`,
`WorkOrderControllerTest`.

---

### 3.10 `F10` — 3 nợ field-level cuối (`G`, `F`, `H`) ✅ **HOÀN THÀNH 2026-08-01**

Migration **`V40`**. **Không** permission mới, **không** rename, **không** breaking change trên wire.
549 unit / 57 IT → **556 unit / 59 IT**, failures = 0.
Coverage unit+IT: 80.1% → **80.1%** line, 63.5% → **63.9%** branch (unit: **73.7%** / **59.2%**).

**Vì sao 3 nợ này đi chung một phase:** cả ba đều là "backend **đã có** dữ liệu, chỉ thiếu chỗ chứa",
khác hẳn nợ `E` (khái niệm chưa tồn tại) — user chốt phạm vi này ngày 2026-08-01.

| Nợ | Nội dung | Migration |
|---|---|---|
| **G** | `projectedAvailable` trên requirement line (spec §2.4) | `V40` — cột **nullable, KHÔNG backfill** |
| **F** | `sourceRoutingCode` / `sourceRoutingVersion` trên proposal (spec §2.4) | `V40` — 2 cột nullable |
| **H** | `MaterialIssue.code` (`MI-`), `ProductionExecution.code` (`PE-`), execution `status` | `V40` — backfill + `NOT NULL` + `UNIQUE`; `status` **không** cột |

#### Hệ quả cần nhớ khi code tiếp

1. 🔴 **`G` không phải "field bị quên" mà là "derive ra số SAI".** `MrpCalculationService:78-88` **luôn
   luôn** tính đúng `remainingCoverage` rồi vứt đi. Số bị trừ là `consumedCoverageByItem` — chỉ sống
   trong **một** lần chạy calculate. Khi một item nằm trên **nhiều** requirement line, mapper cộng
   `available + openSupply` báo số **lớn hơn thực tế** và phá đẳng thức
   `net = max(0, gross + safety − projected)` mà FE dùng để tự kiểm. Bất biến **`B77`**.
2. 🔴 **`NULL` ≠ 0 ở cột mới, và KHÔNG được backfill.** `NULL` = "run chạy trước `V40`". Backfill bằng
   `available + openSupply` là ghi **vĩnh viễn** đúng con số sai mà cột này sinh ra để thay thế —
   `FlywayMigrationIT` có assertion pin cột vẫn `is_nullable = YES`.
3. 🔴 **`findItemIdsWithActiveRouting` bị THAY, không phải bổ sung.** `RoutingLookupService` nay trả
   `Map<UUID, RoutingSummary>`; `keySet()` trả lời luôn câu "có routing `ACTIVE` không?". Giữ cả hai
   method = **2 query cho cùng một dữ liệu** mỗi cấp BOM (`C14`). Call site duy nhất trong `src/main`
   là `MrpCalculationService:65`; 4 stub trong `MrpCalculationServiceTest` đã **sửa** theo `R10`.
4. **`RoutingSummary.version` là `RoutingHeader.routingVersion`** (String nghiệp vụ), **không phải**
   `BaseEntity.version` (bộ đếm optimistic lock) — hai field nằm cạnh nhau, đúng chỗ dễ lấy nhầm.
   `RoutingLookupServiceTest` có case canh riêng.
5. 🔴 **`@PrePersist`, KHÔNG gán code sau `save()`** — Hibernate chụp snapshot entity lúc queue INSERT
   (`CLAUDE.md §0.12` #5, đúng bài học `B68` của `D4`). Và **công thức Java phải khớp từng ký tự với
   backfill SQL**: lệch một ký tự thì dòng lịch sử mang mã không truy ngược về id được, và `UNIQUE`
   **không** bắt được. `FlywayMigrationIT` so hai vế trực tiếp.
6. **Prefix `PE-` lệch spec có chủ đích** — §5.2 gọi là "Mã WIP" nhưng `wip_transactions` là bảng
   ledger khác, không có `code`; `WIP-` sẽ đọc như đang định danh một dòng của bảng đó.
7. **`ProductionExecutionResponse.status` là hằng `"POSTED"`, không phải cột.** Execution không có
   đường huỷ/đảo ⇒ cột một-giá-trị là speculative (`§11.5`), cùng lối tư duy với alias
   `bomCapturedAt`/`executionCompletedAt` của `F8`. Bất biến **`B79`** ghi rõ khi nào nó nên thành cột.
8. **`F10` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi** (`C10` không kích hoạt).
9. **Số MRP không đổi một chữ số nào** — phase chỉ *lưu lại* con số netting vốn đã tính. Nếu kết quả
   MRP đổi thì đã làm sai.

#### Nghiệm thu mutation

Xem bảng ở `CLAUDE.md §0.21`.

#### Breaking changes

**Wire: KHÔNG có** (thuần additive). **Java positional:** `RequirementDraft` +1, `SuggestionDraft` +2,
`MrpRequirementLineResponse` +1, `SupplySuggestionResponse` +2, `MaterialIssueResponse` +1,
`ProductionExecutionResponse` +2, và `RoutingLookupService.findItemIdsWithActiveRouting` **bị thay**.
Test cũ **sửa** theo `R10`: `MrpCalculationServiceTest` (4 stub), `MrpRunServiceTest` (3 draft + 2
verify), `ManufacturingExecutionControllerTest` (1 fixture), `FlywayMigrationIT` (`V39`→`V40`).

---

## 4. Những Gì KHÔNG Làm (áp dụng toàn track)

- **Không** đổi `UUID` sang numeric ID (quyết định #1, §0).
- **Không** mở `/api/v2` (quyết định #3, §0).
- **Không** làm Work Center capacity / CRP / MES / costing / OEE — spec §11 ghi rõ ngoài MVP.
- **Không** đổi tên permission sang dạng bare (`WORK_ORDER_VIEW`): `PermissionGuard` tự thêm tiền tố
  `PERM_`, và bảng permission của spec là bảng **ai-được-làm-gì**, không phải format wire. Chỉ map:
  `PLANNING_RUN`→`PERM_MRP_RUN`, `PRODUCTION_EXECUTE`→`PERM_WIP_MANAGE`,
  `MATERIAL_VARIANCE_APPROVE`→`PERM_MATERIAL_ISSUE_OVERRIDE`, `STOCK_VIEW`→`PERM_INVENTORY_READ`…
  Chỉ thêm mới: `PERM_QUALITY_DISPOSITION`, `PERM_WORK_ORDER_RELEASE`, `PERM_SALES_ORDER_*`.

---

## 5. Kiểm Chứng (áp dụng mọi phase)

**Mỗi phase** (bắt buộc trước khi tick ở §1):
```bash
mvn -o test          # unit
mvn -o verify         # + Failsafe *IT.java (Testcontainers)
```
Số case sau mỗi phase **≥ baseline** của phase trước (rule `R10`); test cũ được **sửa**, không xoá.

**Test bắt buộc thêm theo rule sẵn có** (`.claude/rules/best-practices.md §8.6.1`):
- `R1` assert `ErrorCode` cụ thể.
- `R2` mỗi permission mới phải có deny + `verify(guard)` + allow; nghiệm thu bằng cách đổi chuỗi
  `PERM_*` thành rác → test **phải đỏ**.
- `R6` assert bằng con số nghiệp vụ thật cho netting MRP và các phép tính trần số lượng.
- `R9` idempotency: gửi trùng key → trả document cũ; trùng key + **khác payload** →
  `IDEMPOTENCY_CONFLICT` (đã có test mẫu từ `F1`, xem `InventoryMovementServiceTest`).
- `PermissionCatalogTest` sẽ tự bắt lệch code↔Flyway cho permission mới.

**Nghiệm thu end-to-end** — chạy đúng 11 bước kịch bản spec §11.1 dưới dạng `*IT.java` trên
Testcontainers (`AbstractPostgresIntegrationTest` đã có sẵn từ `T4`): Confirm SO → Planning Run →
convert → reserve → release → issue → execution → partial receipt → approve (lot `HOLD`, available
**chưa** tăng) → `QC_AVAILABLE` (available tăng + SO fulfilled tăng) → retry mọi mutation cùng
`Idempotency-Key` (không sinh chứng từ/tồn kho trùng). Kịch bản này chỉ chạy trọn vẹn được sau `F6`.

**Lưu ý `@WebMvcTest`:** mỗi controller test mới cần `@MockBean` đủ 6 bean hạ tầng bảo mật
(`IpExtractor`, `JwtTokenProvider`, `TokenStoreService`, `UserDetailsService`, `RedisTemplate`,
`RateLimitProperties`); nếu controller nhận `X-Plant-Id` thì thêm `@Import(PlantContextResolver.class)`
(tiền lệ: `WorkOrderControllerTest`).

**Tài liệu phải cập nhật mỗi phase** (rule `dev-workflow.md §6.5`): `CLAUDE.md §0`, bảng §1 của file
này, `NEXT_PHASE_PLAN.md` (thay bằng phase kế tiếp), `docs/roles-and-permissions.md`,
`docs/system-flow.md`, và `CLAUDE.md` split file của module bị đụng.

---

## 6. Track `D*` — Kế Hoạch Trả Nợ Kỹ Thuật  *(soạn 2026-07-28; `D1`-`D7`+`D9`+`D10` ✅ xong 2026-07-30, `D7b`+`D11` ✅ xong 2026-07-31, còn `D8`)*

> **Vì sao nằm ở file này:** track `F*` đã đóng nhưng phần lớn nợ dưới đây phát sinh **từ** các
> phase `F*` (được ghi nhận trong chính §3), nên giữ chung một chỗ để không phải mở file thứ tư.
> **Sổ nợ chính thức vẫn là `CLAUDE.md §0.4`** — file này chỉ chứa *kế hoạch xử lý*. Khi một phase
> `D*` khởi động, nội dung chi tiết của nó chuyển vào `NEXT_PHASE_PLAN.md` theo đúng quy ước
> "một phase đang chạy" (§0 đầu file này).

### 6.0 Baseline đo lại ngày 2026-07-28 (số liệu thật, không lấy từ tài liệu cũ)

```text
mvn -o test        →  66 class / 391 case unit  ·  failures = 0 · errors = 0   (BUILD SUCCESS)
*IT.java           →  4 class / 18 case          (Failsafe, cần Docker)
JaCoCo             →  line 71.3%  ·  branch 57.2%      ← CLAUDE.md §0.1 đang ghi số cũ của F4
Migration mới nhất →  V35
Controller         →  19 class, trong đó 3 có test tầng HTTP
```

### 6.1 Bảng Ưu Tiên

| ✔ | Phase | Nợ đóng | Rủi ro nếu để nguyên | Migration | Breaking? | Effort | Phụ thuộc |
|---|---|---|---|---|---|---|---|
| **[x]** | **`D1`** | #7, #20 | 🔴 Rò rỉ password/refresh token qua log | — | Không | Thấp | — |
| **[x]** | **`D2`** | #19 *(mới)* | 🔴 Không có bằng chứng chuỗi 11 bước chạy được | — | Không | Trung bình | — |
| **[x]** | **`D3`** | #18 *(mới)* | 🟡 Ranh giới `X-Plant-Id` không được ghi ⇒ agent sau hiểu sai | — | Không | Rất thấp | — |
| **[x]** | **`D9`** | **#22, #23** *(mới, `D1` tìm ra)* | 🔴 **Work order từ MRP không release được qua API**; `BLOCKED` không bao giờ persist | — | **Có** | Trung bình | `D1` ✅ |
| **[x]** | **`D10`** | **#21, #24** *(mới, `D1` tìm ra)* | 🟠 FE không lấy được `planningDemandId`; `POST /material-issues` không replay-safe | — | Không | Thấp | `D1` ✅ |
| **[x]** | **`D4`** | #16, #15 | 🟠 MRP đề xuất mua trùng thứ đã đặt hàng | `V36` | **Có** (số MRP đổi) | Trung bình | `D2` ✅ |
| **[x]** | **`D5`** | #17 | 🔴 Thành phẩm không lot-tracked không bao giờ `FULFILLED` | **—** (chốt `A2` ⇒ không cần `V37`) | **Có** | Trung bình | `D2` ✅ |
| **[x]** | **`D6`** | #10 | 🟠 Cùng key ở `/receive` rồi `/issue` trả nhầm chứng từ | `V37` | **Có** | Trung bình | `D2` ✅ |
| **[x]** | **`D7`** | **#9 (hết)**, #2 (một phần) | 🟡 17/20 controller không có test contract HTTP; 409/422 lệch giữa các module | — | **Có** (9 endpoint đổi status) | Cao | — |
| **[x]** | **`D7b`** | **#2 (hết)** | 🟢 9 controller nhóm B+C (CRUD master data + read-only) chưa có test HTTP | — | Không | Thấp | `D7` ✅ |
| **[x]** | **`D11`** | **#25, #26** | 🔴 Dòng receipt cuối của WO không nhập kho được; `planning` trả 409/422 mâu thuẫn với chính nó | — | **Có** (4 endpoint) | Trung bình | `D5` ✅, `D7b` ✅ |
| **[x]** | **`D8a`** | **#6 (1/3)** | 🟡 RTR chưa có — token cũ bị đánh cắp không phân biệt được với hết hạn tự nhiên | — | Không (wire additive: +1 mã lỗi) | Trung bình | — |
| **[x]** | **`D8b`** | **#6 (1/3)** | 🟡 Absolute session timeout chưa có — session sống mãi nếu user active liên tục | — | Không (wire additive: +1 mã lỗi) | Trung bình | `D8a` ✅ |
| **[ ]** | **`D8c`** | #6 (1/3) | 🟡 Forgot-password chưa có endpoint nào. 🔴 **Bị chặn**: `pom.xml` không có `spring-boot-starter-mail` | — | Chưa xét | Cao (khối lượng file) | Chốt hạ tầng email |

> **[2026-07-28] `D1` + `D2` + `D3` được gộp thành MỘT phase thực thi**, vì từng cái đơn lẻ quá nhỏ
> để đáng một vòng baseline → sửa → verify → cập nhật tài liệu. Cả ba đều không migration, không
> breaking change. Prompt chi tiết: `NEXT_PHASE_PLAN.md` (đang chạy). Tên phase trong sổ giữ nguyên
> `D1`/`D2`/`D3` để không phải đánh số lại `D4`-`D8`.

**Thứ tự đề xuất *(cập nhật 2026-08-03)*: ~~`D1`+`D2`+`D3`~~ ✅ → ~~`D9`+`D10`~~ ✅ → ~~`D4`~~ ✅ →
~~`D5`~~ ✅ → ~~`D6`~~ ✅ → ~~`D7`~~ ✅ → ~~`D7b`~~ ✅ → ~~`D11`~~ ✅ → ~~`D8a`~~ ✅ (2026-08-03) →
`D8b` → `D8c`.**
Nợ **#25** (`D5` phát hiện) và **#26** (`D7b` phát hiện) đã đóng ở `D11` — user chốt **phương án A**
cho cả hai (nới `B13`; sửa 3 chỗ `planning` sang 409). Track `D*` nay **hết nợ đúng-sai**; `D8` còn lại
là **tính năng chưa làm**, không phải nợ. **[2026-08-01]** `D8` tách thành 3 sub-phase độc lập
(`D8a`/`D8b`/`D8c`) sau khi nghiên cứu cho thấy cả ba **chưa có bất kỳ dòng code nào**, kể cả error
code, và effort khác hẳn nhau — RTR (`D8a`) được chọn làm trước vì hiệu quả bảo mật cao nhất và không
đổi schema Redis.
**[2026-08-03] `D8a` ✅ xong** — `TOKEN_REUSE_DETECTED` + force-logout mọi phiên, không migration,
wire additive. Bản ghi đầy đủ: `CLAUDE.md §0.22`, bất biến `B80` (`module/auth/CLAUDE.md`).
**[2026-08-03] `D8b` ✅ xong** — `SESSION_ABSOLUTE_TIMEOUT` sau 30 ngày kể từ login, `sessionCreatedAt`
ở companion key `…:{tokenId}:meta` carry-forward qua mỗi rotate. Không migration, wire additive.
Bản ghi: `CLAUDE.md §0.23`, bất biến `B81`. Phase này cũng **trả nợ lần đo `*IT`** mà `D8a` bỏ qua
(chạy `mvn -o verify` thật với Docker: 586 unit + 66 IT xanh) và sửa mâu thuẫn tài liệu↔code
"multi-device vs single-session".
🔴 Nợ #6 mới trả **2/3**: `D8c` (forgot-password) **vẫn chưa có dòng code nào** và đang **bị chặn**
bởi hạ tầng email — đừng đọc hai dấu ✅ rồi coi cả `D8` đã xong.
`D9` đã được chen lên đầu vì #22 làm **luồng sản xuất chính không chạy được qua API**; nay luồng đã
thông nên các phase còn lại đo được trên một hệ thống chạy thật.
Lý do `D2` đứng thứ hai dù không phải nợ nặng nhất: `D4`/`D5`/`D6` đều **đổi hành vi nghiệp vụ**
xuyên module; bài nghiệm thu 11 bước là lưới an toàn duy nhất bắt được hồi quy giữa các module đó.
Làm `D2` sau chúng thì mất tác dụng bảo vệ.

---

### 6.1.1 Bản ghi `D1`+`D2`+`D3` ✅ **HOÀN THÀNH 2026-07-28**

```text
Baseline đầu phase:   66 class / 391 case unit  +  4 class / 18 case IT   ·  failures = 0
Kết quả cuối phase:   67 class / 396 case unit  +  5 class / 20 case IT   ·  failures = 0
Coverage:             line 71.3% → 77.0%  ·  branch 57.2% → 61.4%  (đo bằng mvn -o verify)
Migration:            KHÔNG có · Permission mới: KHÔNG có · Breaking change: KHÔNG có
```

**Đã làm:**

1. **Nợ #7** — rà ra **5** record DTO lộ secret (kế hoạch chỉ ghi 2): `LoginRequest`, `RefreshRequest`,
   `LogoutRequest`, `CreateUserRequest`, `UpdateUserRequest`. Cả 5 override `toString()`.
   `SensitiveRequestToStringTest` (5 case) + nghiệm thu mutation (xoá override ⇒ test đỏ, đã revert).
2. **Nợ #19** — `ProductionFlowE2EIT`: 11 bước spec §11.1 qua HTTP trên Testcontainers. Rule `T1`
   được sửa để ghi nhận **đúng một** `@SpringBootTest` ngoại lệ.
3. **Nợ #18** — ranh giới `X-Plant-Id` ghi ở 3 chỗ (javadoc `PlantContextResolver`,
   `error-handling.md §5.6.1`, sửa quyết định `F1-D6` ở §3.1.3 file này).
4. **Nợ #20** — số liệu `CLAUDE.md §0.1`/`§0.4` đo lại và sửa.

**Kết quả quan trọng nhất: bài E2E tìm ra 4 defect mà 391 unit test không thấy.**

| Nợ | Defect | Vì sao mock không bắt được |
|---|---|---|
| **#22** 🔴 | Work order từ MRP **không release được qua API**: reserve đòi `RELEASED` (B13), release đòi reservation 100% (B14) | Mọi unit test reservation dựng WO **đã** `RELEASED` |
| **#23** 🔴 | `BLOCKED` **không bao giờ** persist: gate throw trong chính transaction `REQUIRES_NEW` của nó ⇒ rollback-only | `WorkOrderReleaseGateTest` mock repository, chỉ `verify(save)` — không thấy được transaction outcome |
| **#24** 🟠 | `POST /material-issues` không replay-safe: resolve reservation **trước** khi kiểm idempotency | Unit test mock reservation lookup, không mô phỏng reservation đã consumed |
| **#21** 🟠 | `GET /sales-orders/planning-demands` không trả `planningDemandId` mà `POST /planning-runs` cần | Không test nào đi qua **cả hai** endpoint |

**Quyết định trong phase (khác kế hoạch, có chủ đích):**

| # | Quyết định | Lý do |
|---|---|---|
| 1 | **Pin hành vi sai**, không sửa 4 defect trong `D1` | Cả 4 là thay đổi nghiệp vụ; `D1` tự khai "không breaking change" (§4 của plan). Tiền lệ: `StockBalanceRepositoryIT` pin nợ #8 |
| 2 | Bài E2E dùng seam `forceStatus(...)` để vượt deadlock #22 | Không có seam thì bước 4–11 **không** phủ được gì. Seam là 1 method có comment chỉ rõ nó tồn tại vì defect, không vì tiện |
| 3 | Thành phẩm chọn **lot-tracked** | Item không lot-tracked kẹt ở nợ #17 — muốn test nói về chuỗi, không nói về lỗ đã biết |
| 4 | Authenticate bằng `UserPrincipal` thật của admin (`V2`), không `@WithMockUser` | `SecurityAuditorAware` chỉ nhận type đó ⇒ `created_by`/`approvedByUsername` mới có giá trị thật để assert |
| 5 | Bước 7 report **6/10** (partial), không 10/10 | *(Lý do gốc, **đã hết hiệu lực từ `D11`**: report đủ 10 ⇒ WO `COMPLETED` ⇒ `canExecute()` false ⇒ không receipt được — chính là nợ #25.)* Lý do **còn lại và vẫn đúng**: partial là cách duy nhất thấy được roll-up `PARTIALLY_FULFILLED`, nên bước này giữ nguyên 6/10 |

**3 assertion đầu tiên tôi viết đã sai và bị chính hệ thống sửa** (ghi lại để agent sau không lặp):
`exceptionState` là `WARNING` chứ không `READY` (không có `ItemWarehouseSetting` ⇒ `SYSTEM_FALLBACK_USED`,
B59); chuỗi sinh **3** dòng ledger chứ không 2 (`ISSUE` + `RECEIVE` + `LOT_STATUS_CHANGE` của QC);
và WO sau release thất bại là `DRAFT` chứ không `BLOCKED` (chính là nợ #23).

---

### `D1` — Vá rò rỉ secret + đồng bộ tài liệu  *(nợ #7, #20)*

| # | Việc | File |
|---|---|---|
| `D1.1` | Override `toString()` che `password` trên `LoginRequest` | `module/auth/dto/LoginRequest.java` |
| `D1.2` | Override `toString()` che `refreshToken` trên `RefreshRequest` | `module/auth/dto/RefreshRequest.java` |
| `D1.3` | Rà **toàn bộ** `record` DTO còn lại xem có field secret nào khác (`grep -rn "password\|token\|secret" --include="*Request.java"`); vá cùng cơ chế | `module/**/dto/**` |
| `D1.4` | Sửa số liệu lệch: `CLAUDE.md §0.1` coverage `70.3/54.9` → **`71.3/57.2`**; `§0.4` nợ #2 "18 controller" → **19**; ghi 3 nợ mới **#18/#19/#20** vào `§0.4` | `CLAUDE.md` |

**Cách vá (đã cân nhắc):** record không dùng được `@ToString.Exclude`, nên override thủ công —
in tên field + `"***"`, **không** in độ dài chuỗi (độ dài password cũng là thông tin cho attacker).

**Test bắt buộc:** 1 class mới `AuthRequestDtoTest` — `assertThat(request.toString()).doesNotContain(rawPassword)`
và `.doesNotContain(rawRefreshToken)`. Đây là loại test rẻ nhưng chống hồi quy vĩnh viễn: ai thêm
field secret mới vào record sẽ không bị bắt, nên **kèm luôn** một case liệt kê field bằng reflection
nếu chi phí không tăng đáng kể; nếu tăng thì bỏ, không over-engineer.

**Breaking Changes:** không có (`toString()` không nằm trong contract wire).
**DoD:** `mvn -o test` ≥ 391 case, failures = 0; `grep` xác nhận không còn record nào expose secret;
`CLAUDE.md §0.4` có đủ 3 mục nợ mới.

---

### `D2` — Nghiệm thu end-to-end 11 bước spec §11.1  *(nợ #19, mới)*

Đây là bài kiểm chứng đã được hứa từ §5 của file này nhưng chưa ai viết. Kịch bản:

```text
Confirm SO → Planning Run → convert proposal MAKE → reserve → release → issue
→ production execution → partial receipt → approve (lot HOLD, available CHƯA tăng)
→ QC AVAILABLE (available tăng + SO fulfilledQuantity tăng + status roll-up)
→ retry mọi mutation cùng Idempotency-Key (không sinh chứng từ/tồn kho trùng)
```

**⚠️ Xung đột rule phải giải trước khi code:** `best-practices.md §8.6 T1` ghi "`src/test` **không có**
`@SpringBootTest` nào — giữ nguyên như vậy". Chuỗi 11 bước cắt qua 6 module với transaction thật +
method security thật ⇒ không dựng được bằng `@DataJpaTest`/`@WebMvcTest`, và tự `new` từng service
là dựng lại nửa cái Spring context bằng tay.

| Lựa chọn | Đánh giá |
|---|---|
| **A. Một `@SpringBootTest` duy nhất, miễn trừ có ghi chép** *(đề xuất)* | Sửa `T1` thành "hạn chế `@SpringBootTest`; **đúng một** class ngoại lệ cho bài nghiệm thu E2E, mọi test khác vẫn cấm". Chạy qua Failsafe (`*IT.java`) nên **không** làm chậm `mvn test` hằng ngày |
| B. Ghép nhiều `*IT.java` nhỏ ở tầng repository | Không chứng minh được chuỗi — mỗi mảnh vẫn xanh trong khi chuỗi gãy |

**Đề xuất cụ thể:** `ProductionFlowE2EIT extends AbstractPostgresIntegrationTest`, dùng
`@SpringBootTest(webEnvironment = MOCK) + @AutoConfigureMockMvc` để **đi qua HTTP** — nhờ đó bước 11
(retry `Idempotency-Key`) và contract `{code,result,message}` được kiểm cùng lúc, thay vì gọi service
trực tiếp rồi vẫn phải tin tưởng tầng controller.

**Kết quả mong đợi (viết assertion theo `R6` — con số thật, không `isNotNull()`):**
- Sau `approve`: `stock_balances.quantity` tăng **nhưng** available (theo aggregate lọc lot status) **chưa** tăng — đây là bằng chứng `B18` + `B39`.
- Sau `QC AVAILABLE`: available tăng đúng lượng, `SalesOrderLine.fulfilledQuantity` tăng đúng lượng, status roll-up đúng `PARTIALLY_FULFILLED`/`FULFILLED` (`B62`, `B63`).
- Retry mọi mutation: số dòng `stock_movements` **không đổi** (`R9`).
- **Dự kiến sẽ đỏ ở nhánh item không lot-tracked** — đó chính là nợ #17, để `D5` xử lý. Nếu muốn IT xanh trước `D5` thì kịch bản dùng item lot-tracked và **ghi chú rõ** giới hạn ngay trong test.

**Breaking Changes:** không có.
**DoD:** `mvn -o verify` xanh; IT 18 → **≥ 19 case**; `T1` trong `best-practices.md` được sửa cho khớp thực tế (không để rule nói một đằng repo làm một nẻo).

---

### `D3` — Ghi rõ ranh giới `X-Plant-Id`  *(nợ #18, mới)*

**Phát hiện gốc đã được chỉnh lại sau khi đọc code:** đây **không** phải "quên wire".
`PlantContextResolver.ensureMatches(header, requestPlantId)` cần một `plantId` do request nêu tường
minh. `ManufacturingExecutionController` **không có endpoint nào như vậy** (toàn `/work-orders/{id}/…`
và `/material-issues`), và ngay `WorkOrderController` cũng chỉ áp dụng cross-check ở đúng endpoint
`/plants/{plantId}/work-orders`. Vậy hiện trạng là **nhất quán**, chỉ thiếu tài liệu.

| Lựa chọn | Đánh giá |
|---|---|
| **A. Ghi rõ ranh giới, đóng nợ bằng tài liệu** *(đề xuất)* | Với endpoint WO-scoped, **chính `workOrderId` là scope** — không tồn tại cái mơ hồ nào để header đi bắt, và `@PreAuthorize` đã chặn truy cập sai plant. Thêm check = thêm code không ai cần (`coding-rules.md §11.5`) |
| B. Truyền header xuống service, so với `workOrder.getPlant()` sau khi load | ~10 endpoint đổi chữ ký; giá trị duy nhất là bắt client bug mà quyền hạn đã chặn sẵn |

**Việc phải làm nếu chọn A:** thêm 1 đoạn vào javadoc `PlantContextResolver` + 1 dòng vào
`.claude/rules/error-handling.md §5.6` — *"`X-Plant-Id` chỉ có ý nghĩa với endpoint mang `plantId`
tường minh; endpoint định danh bằng aggregate (`workOrderId`, `salesOrderId`) lấy plant từ chính
aggregate đó"* — và sửa quyết định `F1-D6` ở §3.1.3 file này (câu "các controller còn lại sẽ đổi path
ở `F5`" nay đã sai).

**Effort:** ~30 phút. **Breaking Changes:** không có.

---

### `D4` — MRP đúng số: open PO + Run header  *(nợ #16, #15)* ✅ **ĐÃ LÀM, xem §6.1.3**

> ⚠️ **Ba điểm trong bảng dưới đây đã bị bác bỏ khi implement** — giữ nguyên bảng để thấy vì sao code
> trông như hiện nay, nhưng **đừng đọc nó như đặc tả hiện hành**:
> 1. `D4.3` đề xuất **tách 2 cột** `openWorkOrderQuantity`/`openPurchaseQuantity` → **KHÔNG làm**.
>    Spec §2.1 chỉ có một số hạng `scheduledReceipts`. Lý do đầy đủ: `module/planning/CLAUDE.md` mục 6.
> 2. `D4.1` ghi "lọc `expectedDate ≤ horizonEnd`" → **KHÔNG làm**. `WorkOrderSupplyService` cũng không
>    lọc theo ngày; lọc một nguồn mà không lọc nguồn kia làm hai nửa của cùng một số hạng lệch nhau.
>    Nếu cần horizon-aware supply thì đó là thay đổi cho **cả hai** nguồn, mở nợ riêng.
> 3. `D4.4` ghi sinh `code` "theo tiền lệ `work_order_no`" → làm theo tiền lệ nhưng **tiền tố `RUN-`**,
>    không `MRP-` (đã là tiền tố work order sinh từ proposal).

| # | Việc | Ghi chú kỹ thuật |
|---|---|---|
| `D4.1` | `PurchaseOrderSupplyService` trong `module/purchasing/service/query/` — **sao đúng khuôn** `WorkOrderSupplyService` (đã verify: cùng chữ ký `Map<UUID,BigDecimal> getOpenSupplyQuantities(companyId, plantId, warehouseIds, itemIds)`) | Open = `status ∈ {SENT, PARTIALLY_RECEIVED}`, lượng = `Σ(orderedQuantity − receivedQuantity)`, lọc `expectedDate ≤ horizonEnd`. **1 aggregate query** + projection (`C14`) |
| `D4.2` | `MrpCalculationService` cộng thêm nguồn này vào `openSupplyQuantity` | Hướng phụ thuộc mới `planning → purchasing` — hợp lệ theo `C7`, gọi query service chứ không chạm repository |
| `D4.3` | Tách hiển thị: giữ `openSupplyQuantity` là **tổng**, hay tách `openWorkOrderQuantity` + `openPurchaseQuantity`? | Đề xuất **tách 2 cột** (`V36`) — planner cần biết thiếu hụt đang chờ *ai*; gộp lại thì không truy ra được |
| `D4.4` | `MrpRun.code` + 4 ô summary spec §2.4 (`shortageLines`, `plannedWorkOrders`, `plannedPurchaseRecommendations`, `blockedProposals`) | `V36`. Sinh `code` theo đúng tiền lệ số chứng từ đang dùng (`work_orders.work_order_no` / `sales_orders.order_no`) — rà lại tiền lệ khi implement, **không** tự phát minh format |

**⚠️ Breaking Changes:**
1. **Kết quả MRP đổi** — item đã có PO mở sẽ ra `suggestedQuantity` nhỏ hơn, hoặc biến mất khỏi danh sách. Đây là *sửa sai*, nhưng mọi fixture MRP hiện có phải tính lại theo `R10` (**sửa**, không nới assertion).
2. `MrpRequirementLineResponse` thêm field (additive), `MrpRunResponse` thêm `code` + 4 số.

**Test bắt buộc:** `R6` — 1 case khoá công thức bằng số thật (`projectedAvailable = eligibleOnHand +
openWO + openPO − priorAllocations`); `C15` — verify aggregate query mới được gọi **đúng 1 lần** cho
cả cấp BOM; 1 case PO `DRAFT`/`CANCELLED` **không** được tính là supply.

---

### `D5` — QC disposition cho output không lot-tracked  *(nợ #17)* ✅ **ĐÃ LÀM, xem §6.1.4**

> 📌 **Prompt chi tiết đã soạn (2026-07-30) ở `NEXT_PHASE_PLAN.md`, đã verify trên code.** Hai điểm
> bảng dưới đây **không** nắm được, đọc plan trước khi tin bảng này:
> 1. **`quality_dispositions.lot_id` là `NOT NULL`** (`V26:38`) ⇒ không ghi được dòng audit cho output
>    không lot. Đây là **chốt chặn cấu trúc của cả hai nhánh**, không riêng `REJECTED` ⇒ sinh ra một
>    quyết định **thứ hai** cần user chốt (`NEXT_PHASE_PLAN.md §6.1`).
> 2. PA **A** của `D5.2` **không cần migration**: `MovementType.ADJUST_OUT` và
>    `InventoryMovementService.adjust(...)` đã tồn tại và làm đúng việc rút kho.

Nợ #17 là hệ quả kẹp giữa hai bất biến: `B40` (QC chỉ chạy trên output lot-tracked) và `B62`
(fulfillment chỉ chạy khi QC `AVAILABLE`). Hướng đúng — đã ghi ở `CLAUDE.md §0.4` — là **mở QC cho
output không lot-tracked, phán quyết trên *receipt* thay vì trên *lot***. Tuyệt đối **không** mở
đường fulfill thứ hai ở `approve` (phá `B62`, tạo rủi ro double-count).

Chia làm 2 bước, mỗi bước tự nó hoàn chỉnh và ship được:

| Bước | Nội dung | Vướng mắc |
|---|---|---|
| `D5.1` | Nhánh **`AVAILABLE`** cho receipt không lot-tracked: bỏ chốt `STATE_CONFLICT`, **không** sinh `LOT_STATUS_CHANGE` (không có lot để đổi), ghi `qcResult` lên receipt, **có** gọi fulfillment | Không vướng. Đóng đúng phần "không bao giờ `FULFILLED`" của nợ #17 |
| `D5.2` | Nhánh **`REJECTED`** cho receipt không lot-tracked | ⚠️ Hàng đã nằm trong tồn **tự do** từ lúc `approve` (không có `HOLD` để giữ). REJECTED mà không đụng tồn kho ⇒ **sổ cái nói dối**: hàng hỏng vẫn bán/xuất được |

**Quyết định cần user chốt cho `D5.2`:**

| | Phương án | Hệ quả |
|---|---|---|
| **A** *(đề xuất)* | REJECTED sinh movement rút hàng khỏi kho (`ADJUST_OUT`, reason `QC_REJECTED`) | Sổ cái trung thực. **Phá `B39`** ("QC không đổi `stock_balances`") ⇒ phải viết lại `B39` thành *"QC trên output lot-tracked không đổi balance; trên output không lot-tracked thì có"* |
| **B** | REJECTED chỉ ghi nhận `qcResult`, không đụng tồn | Giữ `B39` nguyên vẹn nhưng REJECTED thành vô nghĩa về mặt tồn kho |
| **C** | Giữ `STATE_CONFLICT` cho REJECTED, chỉ làm `D5.1` | Nhỏ nhất, nhưng API bất đối xứng khó giải thích cho FE |

**Test bắt buộc:** `B62` phải được **giữ nguyên hiệu lực** — case `approve` vẫn
`verifyNoInteractions(fulfillmentService)` sau khi sửa; thêm case receipt không lot-tracked
`AVAILABLE` ⇒ `fulfilledQuantity` tăng đúng số. Cập nhật `B40`/`B39`/`B62` ở
`module/workorder/CLAUDE.md` — **không** để bất biến chỉ tồn tại trong code.

---

### `D6` — Scope lại `Idempotency-Key` của `stock_movements`  *(nợ #10)* ✅ **ĐÃ LÀM, xem §6.1.5**

Hiện trạng (đã verify): `V8:75 CONSTRAINT uk_stock_movements_idempotency_key UNIQUE (idempotency_key)`
— **UNIQUE toàn bảng**. Cùng một key gửi tới `/receive` rồi `/issue` thì lần 2 trả về movement của
lần 1. Bảng có sẵn `movement_type` và `created_by` (nullable).

| | Phương án | Đánh giá |
|---|---|---|
| **A** *(đề xuất)* | UNIQUE `(idempotency_key, movement_type)` | Đóng đúng triệu chứng đã ghi trong sổ nợ; không cột nullable nào tham gia ⇒ ràng buộc luôn có hiệu lực, kể cả cho dữ liệu cũ |
| **B** | UNIQUE `(idempotency_key, created_by, movement_type)` — sát spec §10.2 nhất | `created_by` **nullable**: Postgres coi NULL là khác nhau ⇒ dòng lịch sử `created_by IS NULL` **mất** hoàn toàn bảo vệ trùng lặp. Muốn dùng phải backfill hoặc `COALESCE` trong partial index |

**Kèm theo (bắt buộc, nếu không sẽ sinh bug ngầm):** `StockMovementRepository.findByIdempotencyKey`
→ `findByIdempotencyKeyAndMovementType`; **6 call site** trong `InventoryMovementService` phải sửa
theo. Nếu chỉ đổi constraint mà không đổi query, replay sẽ tìm thấy movement của loại **khác** và
trả nhầm — nguy hiểm hơn hiện trạng.

**Breaking Changes:** cùng key + khác `movement_type` nay **tạo chứng từ mới** thay vì trả chứng từ
cũ. Client nào đang (vô tình) dựa vào hành vi cũ sẽ thấy khác.
**Test bắt buộc:** `R9` — cùng key + cùng type ⇒ trả document cũ + `verifyNoInteractions`; cùng key +
khác type ⇒ **tạo mới**; cùng key + cùng type + khác payload ⇒ `IDEMPOTENCY_CONFLICT` (case đã có,
phải vẫn xanh).

---

### `D7` — Phủ test controller còn lại + rà 409/422  *(nợ #2, #9)* ✅ **ĐÃ LÀM, xem §6.1.6**

Kế hoạch gốc (giữ lại để đối chiếu): `@WebMvcTest` cho **16 controller** chưa có + rà
`OPERATION_NOT_ALLOWED` (422) vs `STATE_CONFLICT` (409) ở `bom`/`purchasing`/`organization`/`sales`
theo tiêu chí `error-handling.md §5.3`.

**Hai chỗ kế hoạch gốc sai, đã sửa khi thực thi:** tổng controller là **20** (không 19) ⇒ thiếu **17**
(không 16); và phase được cắt còn **nhóm A (8 class)** theo phương án `A` của
`NEXT_PHASE_PLAN.md §6.1`, nhóm B+C (9 class) chuyển sang **`D7b`**.

---

### `D7b` — 9 controller nhóm B+C  *(nốt nợ #2)* ✅ **ĐÃ LÀM, xem §6.1.7**

Kế hoạch gốc giữ lại để đối chiếu. **Đã làm đúng phạm vi, không trượt.** Một điểm khác dự kiến:
`D7b.3` kết luận `A4` **đã được ép** (`PageableFactory:22`) nên không sinh nợ và không cần hỏi user —
nhưng phase lại phát hiện nợ **#26** ở chỗ khác (`planning` còn 3 chỗ 422 lẽ ra 409).

| Nhóm | Controller |
|---|---|
| **B** — CRUD master data | `ItemController`, `ItemWarehouseSettingController`, `SupplierController`, `UserController`, `OrganizationController`, `AccessControlController` |
| **C** — read-only / report | `InventoryReportController`, `PlanningController`, `PlanningDemandController` |

Không có state machine ⇒ giá trị test là **envelope + validation 400 + phân trang** (`A4`: default
`size=20`, max `100`). Mỗi class ≥ 2 case. Effort **thấp**, không rủi ro nghiệp vụ, không breaking
change dự kiến. Dùng 8 class của `D7` làm khuôn — đặc biệt là 6 `@MockBean` hạ tầng bảo mật và quy
ước assert đủ 6 field của `PageResult`.

---

### `D8` — Backlog auth  *(nợ #6)* — **đề nghị tách khỏi track dọn nợ**

RTR reuse detection (`S11`), absolute session timeout (`S13`), forgot-password: đây là **tính năng
chưa làm**, không phải nợ kỹ thuật — thiết kế đã có đủ ở `common/security/CLAUDE.md` (các mục
`[TODO Phase 2]`), code chưa từng tồn tại nên không có gì "sai" đang chạy. Đề nghị xử lý như một
phase nghiệp vụ riêng khi có nhu cầu thật, **không** gộp vào `D*`.

---

### 6.1.2 Bản ghi `D9`+`D10` ✅ **HOÀN THÀNH 2026-07-30**

```text
Baseline đầu phase:   67 class / 396 case unit  +  5 class / 20 case IT   ·  failures = 0
Kết quả cuối phase:   68 class / 402 case unit  +  5 class / 21 case IT   ·  failures = 0
Coverage:             line 77.0% → 76.3%  ·  branch 61.4% → 61.0%  (giảm vì thêm code, không mất test)
Migration:            KHÔNG · Permission mới: KHÔNG
```

**Đã làm — trả cả 4 nợ do `ProductionFlowE2EIT` tìm ra ở `D1`:**

| Nợ | Sửa gì | File |
|---|---|---|
| **#22** | `WorkOrder.canReserve()` (mọi status trừ `COMPLETED`/`CANCELLED`) + `WorkOrderExecutionSupport.ensureReservable`; **chỉ** 2 call site reserve đổi gate | `WorkOrder.java`, `WorkOrderExecutionSupport.java`, `MaterialReservationService.java` |
| **#23** | Bean mới `WorkOrderBlockRecorder` (`REQUIRES_NEW`, **return bình thường**), gate gọi nó xong mới `throw`; `ensureMaterialReady` chuyển sang `readOnly` | `WorkOrderBlockRecorder.java` (mới), `WorkOrderReleaseGate.java` |
| **#21** | `PlanningDemandLineResponse.planningDemandId` + entry point batch `PlanningDemandService.findOpenDemandIdsBySalesOrderLineIds` | `PlanningDemandService.java`, `SalesOrderService.java`, `SalesOrderMapper.java`, DTO |
| **#24** | `postFlat` resolve reservation **không** kiểm `ACTIVE` (chỉ để dịch request); `postNew` vẫn kiểm | `MaterialIssueService.java`, `MaterialReservationService.java` (visibility) |

**Thước đo đã đạt:** seam `forceStatus(...)` trong `ProductionFlowE2EIT` đã **xoá** — bước 4-5 chạy
`reserve → release` đúng thứ tự spec §11.1 qua HTTP thật.

**Quyết định trong phase:**

| # | Quyết định | Lý do |
|---|---|---|
| 1 | Tách `canReserve()` khỏi `canExecute()` thay vì nới `canExecute()` | Issue/execution/receipt/WIP **vẫn phải** chỉ chạy ở `RELEASED`/`IN_PROGRESS`. Nới chung một gate là phá B13 ở chỗ nó đang đúng |
| 2 | `#23` sửa bằng **bean riêng**, không phải bằng cách bắt exception ở caller | Throw trong cùng method `REQUIRES_NEW` **luôn** rollback chính transaction đó — không có cách nào giữ cả hai trong một method. Bean phải return bình thường mới commit |
| 3 | `#21` dùng batch lookup, **không** join JPQL xuyên module | `PlanningDemand.referenceId` là `String`; join sẽ xấu và phá `C7`. `findOpenByReference` đã có sẵn từ `F3` |
| 4 | `#24` **bỏ** check `ACTIVE` ở `postFlat` thay vì nhân bản logic idempotency | `postNew` đã tự kiểm `findActiveReservationForIssue` cho từng dòng ⇒ lần gọi ở `postFlat` chỉ để dịch request. Đường tạo chứng từ không bị nới lỏng |
| 5 | `MaterialReservationServiceTest.reserve_onBlockedWorkOrder_shouldThrow` bị **đảo ngược**, không xoá (`R10`) | Nó khẳng định đúng cái rule vừa bị bỏ. Thay bằng `@ParameterizedTest` cho 3 status mới được phép + case chứng minh `COMPLETED`/`CANCELLED` vẫn bị chặn |

**Ghi chú kiểm chứng:** Docker Desktop tắt giữa phase làm mọi `*IT` lỗi
`Could not find a valid Docker environment` — **không** phải lỗi code. Bật lại rồi `mvn -o verify`
BUILD SUCCESS. Nếu gặp lại: kiểm `docker ps` trước khi debug test.

---

### 6.1.3 Bản ghi `D4` ✅ **HOÀN THÀNH 2026-07-30**

```text
Baseline đầu phase:   68 class / 402 case unit  +  5 class / 21 case IT   ·  failures = 0
Kết quả cuối phase:   69 class / 408 case unit  +  6 class / 27 case IT   ·  failures = 0
Coverage:             line 76.3% → 77.2%  ·  branch 61.0% → 61.8%   (mvn -o verify)
Migration:            V36__add_mrp_run_code_and_summary.sql · Permission mới: KHÔNG
```

**Đã làm:**

| Nợ | Sửa gì | File |
|---|---|---|
| **#16** | `PurchaseOrderSupplyService` + `PurchaseOrderRepository.aggregateOpenSupply` (1 aggregate query, `SENT`+`PARTIALLY_RECEIVED`, `remaining > 0`); cộng vào `openSupplyBySeed` **cùng chỗ** với work order supply | `module/purchasing/service/query/PurchaseOrderSupplyService.java` (mới), `PurchaseOrderSupplyProjection.java` (mới), `PurchaseOrderRepository.java`, `MrpCalculationService.java` |
| **#15** | `MrpRun.code` (`@PrePersist`, UNIQUE, backfill `V36`) + 4 ô summary đếm tại chỗ từ `MrpCalculationResult` | `MrpRun.java`, `MrpRunService.java`, `MrpRunResponse.java`, `MrpPlanningMapper.java`, `V36` |

**Test:** `PurchaseOrderSupplyServiceTest` (2, mới) · `PurchaseOrderRepositoryIT` (5, mới — JPQL thật)
· `MrpCalculationServiceTest` +3 (công thức bằng số thật, "open PO phủ đủ ⇒ không sinh proposal",
`C15` 1 lần/cấp BOM) · `MrpRunServiceTest` +1 (4 ô summary) · `FlywayMigrationIT` +1 (backfill trên
dữ liệu cũ) · `ProductionFlowE2EIT` (+assertion `code` và 4 ô).

**Nghiệm thu mutation:** vô hiệu hoá phép cộng open PO ⇒ đúng 2 case đỏ
(`calculate_openPurchaseOrderCoversDemand_emitsNoPurchaseProposal` +
`...IsNettedTogetherWithOpenWorkOrderSupply`), đã revert.

**Quyết định trong phase (khác kế hoạch, có chủ đích):**

| # | Quyết định | Lý do |
|---|---|---|
| 1 | `MrpRun.code` sinh ở **`@PrePersist`** — kế hoạch ban đầu đặt sau `save()` | Đây là **defect thật, `ProductionFlowE2EIT` bắt được**: Hibernate chụp snapshot entity lúc queue insert, nên field gán sau `save()` không vào INSERT ⇒ `null value in column "code"`. Bài E2E lại chứng minh giá trị của nó lần thứ hai |
| 2 | Thêm `PurchaseOrderRepositoryIT` (kế hoạch không yêu cầu IT mới) | Toàn bộ filter quyết định số MRP (status, `remaining > 0`, warehouse/plant scope) nằm trong JPQL. Test bằng mock ở đó là tautology — rule `R7` cấm. Tiền lệ: `SalesOrderLineRepositoryIT` |
| 3 | Assert `code` ở `ProductionFlowE2EIT`, **không** ở `MrpRunServiceTest` | `@PrePersist` không bao giờ fire với repository mock. Assert ở unit test sẽ buộc phải giữ một lời gọi `assignCode()` dư trong service chỉ để test xanh |
| 4 | `FlywayMigrationIT` migrate tới `V35` → seed row → migrate `V36`, trên **schema riêng** | Test cũ chỉ chạy trên DB trống nên **không** kiểm được backfill. Assert cả chuỗi hardcode (`RUN-A1B2C3D4`) lẫn so với `MrpRun.assignCode()` ⇒ lệch công thức SQL↔Java là đỏ |
| 5 | **Không** tách `openWorkOrderQuantity` / `openPurchaseQuantity` | Theo đúng §2.2 của kế hoạch: spec chỉ có một số hạng `scheduledReceipts` |

**Nợ mới phát sinh:** không có.
**Ghi chú:** `MrpCalculationService.effectiveWarehouseIds` là dead code **có từ trước** `D4` (không phải
do phase này tạo ra) — không xoá theo rule §3, ghi lại ở đây để phase sau quyết định.

---

### 6.1.4 Bản ghi `D5` ✅ **HOÀN THÀNH 2026-07-30**

```text
Baseline đầu phase:   69 class / 408 case unit  +  6 class / 27 case IT   ·  failures = 0
Kết quả cuối phase:   69 class / 412 case unit  +  6 class / 29 case IT   ·  failures = 0
Coverage (unit+IT):   line 77.2% → 77.9%  ·  branch 61.8% → 62.4%
Coverage (unit only):                       line 70.4%  ·  branch 57.5%   ← đo lần đầu ở D5
Migration:            KHÔNG (nhờ quyết định A2) · Permission mới: KHÔNG · Endpoint mới: KHÔNG
```

⚠️ **`D5` phát hiện series coverage lịch sử được đo bằng cách không tin cậy.** `pom.xml` bind
`jacoco:report` vào phase **`test`** (trước Failsafe) và chỉ có **một** `prepare-agent`, nên
`mvn -o clean verify` cho ra số **unit một mình**. Các phase trước ra số unit+IT vì chạy **không
clean** ⇒ `jacoco.exec` còn dữ liệu IT của **lần trước** (agent `append=true`) — đúng do ăn may.
Cách lấy số unit+IT có chủ đích: `mvn -o verify` rồi **`mvn -o jacoco:report`** lần nữa. Ghi ở
`CLAUDE.md §0.1`.

**User chốt 2 câu hỏi §6 của kế hoạch trước khi code (2026-07-30):** `A2` (audit trail) · `A`
(`REJECTED` rút hàng) · edge case `INSUFFICIENT_STOCK` = **để nổ 409**.

**Đã làm** — tất cả trong `ProductionReceiptService.qcDisposition`:

| # | Sửa gì | File |
|---|---|---|
| `D5.1` | Bỏ chốt `lotLines.isEmpty()` → `STATE_CONFLICT`; tách 2 private method `dispositionLots` / `dispositionWithoutLots`, cả hai trả "số QC vừa phán quyết". Đường không-lot cộng **tổng quantity mọi dòng receipt** | `ProductionReceiptService.java` |
| `D5.2` | `REJECTED` không-lot ⇒ `movementService.adjust(...)` delta âm (`ADJUST_OUT`), child key `<parent>:qc-reject:L<n>` | `ProductionReceiptService.java` |
| `D5.3` | **Không** ghi `QualityDisposition` cho receipt không lot (PA `A2`); ghi cảnh báo cho báo cáo QC về sau | `QualityDisposition.java` (javadoc), `module/workorder/CLAUDE.md` |
| `D5.4` | `B39` viết lại · `B40` tách đôi · `B62` ghi chú bẫy `dispositionedQuantity = 0` | `module/workorder/CLAUDE.md` |
| `D5.5` | 5 case unit mới (1 thay case cũ theo `R10`) + 2 case E2E mới | `ProductionReceiptServiceTest`, `ProductionFlowE2EIT` |

**Test:** `ProductionReceiptServiceTest` 23 → **28** (thay
`qcDisposition_nonLotTrackedOutput_shouldThrowStateConflict` bằng
`qcDisposition_available_onOutputWithoutALot_fulfilsWithoutTouchingLotsOrStock`; thêm nhánh `REJECTED`
+ 3 case chứng minh `B38` không bị nới) · `ProductionFlowE2EIT` 3 → **5** case
(`productionFlow_fulfilsTheSalesOrderForOutputThatIsNotLotTracked` là **bằng chứng nợ #17 đã trả**,
`qcRejection_onOutputThatIsNotLotTracked_withdrawsTheGoodsFromStock` khoá `B39` mới trên balance thật).
6 case `qcDisposition_*` lot-tracked + kịch bản E2E lot-tracked: **không sửa một assertion nào**.

**Nghiệm thu mutation:** cho `dispositionWithoutLots` trả `BigDecimal.ZERO` ⇒ **đúng 1 case đỏ**
(`qcDisposition_available_onOutputWithoutALot_...`), đã revert. Đây chính là bẫy phase này: nguồn số
cũ cộng dồn từ lot line nên `fulfill` sẽ **no-op im lặng, không lỗi**.

**Quyết định trong phase (khác kế hoạch, có chủ đích):**

| # | Quyết định | Lý do |
|---|---|---|
| 1 | Thêm **2** case E2E, kế hoạch chỉ yêu cầu 1 | Nhánh `REJECTED` sinh `ADJUST_OUT` — hiệu lực thật của nó là `stock_balances.quantity` giảm, mock không chứng minh được. Cùng lý do với `PurchaseOrderRepositoryIT` ở `D4` |
| 2 | Kịch bản E2E seed `ItemWarehouseSetting` safety stock = 2 | Muốn tới `FULFILLED` phải receipt **đủ** số đã đặt, nhưng report đủ `plannedQuantity` làm WO `COMPLETED` và `B13` chặn receipt tiếp. Safety stock 2 ⇒ MRP plan 12 cho đơn 10 ⇒ receipt hết 10 khi WO còn `IN_PROGRESS`. **Đây là một giới hạn thật của hệ thống, không phải mẹo test** — xem "Nợ mới phát sinh" |
| 3 | Seed thành phẩm không-lot **trong từng test**, không vào `seedMasterData()` | Giữ nguyên fixture mà 3 kịch bản lot-tracked được viết dựa trên, và không làm chúng chậm thêm |
| 4 | `auditorAware.getCurrentAuditor()` chuyển lên **trước** validate lot | Cả hai đường đều cần `actor`. Không đổi hành vi, chỉ thêm 1 interaction ở nhánh `LOT_NOT_ELIGIBLE` |
| 5 | **Không** guard "receipt rỗng dòng" | `postNew` luôn tạo đúng 1 dòng ⇒ nhánh đó không tồn tại. `CLAUDE.md §2`: không viết error handling cho tình huống không thể xảy ra |

**Nợ mới phát sinh:**

| # | Nợ | Ghi ở |
|---|---|---|
| **#25** | Work order có `plannedQuantity` **đúng bằng** số cần nhập kho thì **không bao giờ** receipt hết được: report đủ planned ⇒ `COMPLETED` (`B53`) ⇒ `B13` chặn receipt. Dòng receipt cuối luôn bị kẹt. Phát hiện khi dựng kịch bản `FULFILLED` của `D5` (phải seed safety stock để né). Không thuộc phạm vi `D5` — là mâu thuẫn giữa `B13` và `B53`, cả hai đều có từ `F5-A` | `CLAUDE.md §0.4` |

**Ghi chú:** xoá import `LotStatus` **không dùng** ở `ProductionFlowE2EIT` (dead từ trước `D5`, nằm
đúng trong khối import phase này viết lại). `inventoryLotRepository` cũng là field không dùng nhưng
**giữ nguyên** theo rule §3 vì không nằm trong vùng sửa.

---

### 6.1.5 Bản ghi `D6` ✅ **HOÀN THÀNH 2026-07-30**

```text
Baseline đầu phase:   69 class / 412 case unit  +  6 class / 29 case IT   ·  failures = 0
Kết quả cuối phase:   69 class / 416 case unit  +  6 class / 30 case IT   ·  failures = 0
Coverage (unit+IT):   line 77.9% → 78.1%  ·  branch 62.4% → 62.6%
Coverage (unit only): line 70.4% → 71.2%  ·  branch 57.5% → 58.1%
Migration:            V37__rescope_stock_movement_idempotency.sql
Permission mới: KHÔNG · Endpoint mới: KHÔNG · Contract wire: KHÔNG ĐỔI
```

**User chốt 3 câu hỏi trước khi code (2026-07-30):** scope = **`(key, movement_type)`** (§6.1 PA `A`) ·
`hasMovementForIdempotencyKey` = **xoá** (§6.2 PA `A`) · `adjust` = **dời đọc `delta` lên trước lookup**
(§3.3 PA `a`).

**Đã làm:**

| # | Sửa gì | File |
|---|---|---|
| `D6.1` | `DROP CONSTRAINT` → `ADD CONSTRAINT uk_stock_movements_idempotency_key UNIQUE (idempotency_key, movement_type)`. Không backfill (bộ khoá rộng hơn thì càng ít trùng ⇒ dữ liệu cũ luôn hợp lệ, migration không thể fail vì duplicate) | `V37__rescope_stock_movement_idempotency.sql` |
| `D6.2` | `findByIdempotencyKey` → `findByIdempotencyKeyAndMovementType` | `StockMovementRepository.java` |
| `D6.3` | 5 call site qua private `findReplay(key, type)`; `adjust` dời đọc `delta` + kiểm non-zero lên **trước** lookup; **xoá** `hasMovementForIdempotencyKey` | `InventoryMovementService.java` |
| `D6.4` | `B5` viết lại (thêm scope) · `B5b` ghi rõ chỉ so payload **trong cùng scope** · `B5c` ghi rõ **không đổi** · thêm `B69`-`B71` | `module/inventory/CLAUDE.md` |
| `D6.5` | 4 case unit mới + 1 case IT mới | `InventoryMovementServiceTest`, `FlywayMigrationIT` |

**Test:** `InventoryMovementServiceTest` 12 → **16** case ·  `FlywayMigrationIT` 2 → **3** case.

| Case mới | Chứng minh |
|---|---|
| `issue_sameKeyAlreadyUsedByAReceive_createsAnIndependentIssueMovement` | **Bằng chứng nợ #10 đã trả** (trục 2 của `R9`) + `C15` (lookup đúng 1 lần, type ghi tường minh, không `any()`) |
| `issueReserved_replaysMovementCreatedByPlainIssue_becauseBothAreIssueScope` | `B70` — 2 entry point chung scope `ISSUE`, replay vẫn chặn cộng tồn lần 2 |
| `adjust_sameKeyAlreadyUsedInbound_createsAnIndependentOutboundMovement` | `B71` — chỗ PA `b` (tra cả 2 type) sẽ **không** trả hết nợ |
| `adjust_zeroDelta_failsBeforeAnyLookupIncludingTheReplayLookup` | Pin thứ tự validate mới (breaking change #4) |
| `FlywayMigrationIT.migrate_v37_allowsOneIdempotencyKeyPerMovementTypeInsteadOfPerTable` | Tầng **DB**: cùng key khác type bị V8 **từ chối**, sau `V37` **nhận**; và vẫn đúng 1 dòng/`(key, type)` |

3 case cũ khoá `B5`/`B5b`/`B5c` (`receive_duplicateIdempotencyKey_*`,
`receive_sameIdempotencyKeyDifferentPayload_*`, `receive_legacyMovementWithoutPayloadHash_*`):
**không sửa một assertion nào**, chỉ đổi stub theo chữ ký mới. `ProductionFlowE2EIT` **không đổi** —
child key (`:L<n>`, `:approve`, `:qc`, `:qc-reject`) không bị ảnh hưởng.

**Nghiệm thu mutation:** đổi type ở call site `issueInternal` từ `ISSUE` sang `RECEIVE` ⇒
`issue_sameKeyAlreadyUsedByAReceive_...` **đỏ** (cùng 5 case khác), đã revert. Stub của case này trả
lời từ **bảng `(key, type)`** chứ không phải một giá trị cố định — đó là thứ làm seam này bắt được
mutation; stub cố định thì đổi type vẫn xanh.

**Quyết định trong phase (khác kế hoạch, có chủ đích):**

| # | Quyết định | Lý do |
|---|---|---|
| 1 | Thêm private `findReplay(key, type)` thay vì gọi repository trực tiếp ở 5 chỗ | Một chỗ duy nhất để ghi javadoc về ràng buộc "scope query = scope constraint" (`B69`). Không phải abstraction cho code 1 lần dùng — 5 call site |
| 2 | Thêm **2** case unit ngoài 2 case kế hoạch nêu (`issueReserved` chung scope, `adjust` delta = 0) | `B70` và thứ tự validate mới của `adjust` là hai thứ phase này **đổi** mà kế hoạch không yêu cầu test — không khoá thì phase sau đổi lại không ai biết |
| 3 | IT dùng schema riêng `v37_idempotency_check`, migrate tới `V35` rồi mới `V37` | Cùng tiền lệ `D4`/`V36`: cần chứng minh cả **trạng thái trước** (V8 từ chối) lẫn **sau**, và không được làm bẩn public schema của container dùng chung |
| 4 | **Không** đụng `material_issues` / `production_receipts` / `production_executions` | 3 bảng đó mỗi bảng chỉ có một loại nghiệp vụ ghi vào ⇒ không dùng chung key space như ledger, chưa có triệu chứng. Mở rộng là đoán nhu cầu (`§8` của kế hoạch) |

**Nợ mới phát sinh:** không. Nợ #10 đóng hoàn toàn ở phạm vi ledger; phần "scope theo user" của spec
§10.2 **cố ý** không làm (cần backfill `created_by`, đã ghi lý do ở `CLAUDE.md §0.4` #10 + `§0.14`).

---

### 6.1.6 Bản ghi `D7` ✅ **HOÀN THÀNH 2026-07-30**

```text
Baseline đầu phase:   69 class / 416 case unit  +  6 class / 30 case IT   ·  failures = 0
Kết quả cuối phase:   77 class / 450 case unit  +  6 class / 30 case IT   ·  failures = 0
Coverage (unit+IT):   line 78.1% → 78.5%  ·  branch 62.6% → 62.6%
Coverage (unit only): line 71.2% → 71.8%  ·  branch 58.1% → 58.1%
Migration: KHÔNG · Permission mới: KHÔNG · Endpoint mới: KHÔNG
Breaking change: CÓ — 9 endpoint đổi HTTP status 422 → 409 (liệt kê ở CLAUDE.md §0.15)
```

> 🔴 **Con số coverage là kết quả đáng chú ý nhất của phase này, theo hướng ngược trực giác:** 34 case
> mới đẩy line coverage lên **0.6 điểm** và branch **0.0 điểm**. Lý do: controller là code mỏng và
> `ProductionFlowE2EIT` (`D1`) đã chạy qua phần lớn chúng từ trước. ⇒ **Đừng dùng coverage để biện minh
> hay để đo phase test contract.** `NEXT_PHASE_PLAN.md §4` đã cảnh báo trước; nay có số cụ thể.

**Phase được cắt theo phương án `A`** (§6.1 của kế hoạch, mặc định khi user không trả lời): nhóm A
(8 controller có state machine) + `D7.3`/`D7.4`/`D7.5`. Nhóm B+C (9 controller) → **`D7b`**, danh sách
cụ thể ở mục `D7b` phía trên. **Không** tự mở rộng sang nhóm B.

**`D7.2` — 8 class test mới (34 case):**

| Class | Case | Nhánh lỗi được khoá lần đầu ở tầng HTTP |
|---|---|---|
| `ManufacturingExecutionControllerTest` | 4 | `STATE_CONFLICT`, `PLANNED_QUANTITY_EXCEEDED`, `Idempotency-Key` forward, `PageResult` |
| `SalesOrderControllerTest` | 5 | `X-Plant-Id` mismatch → 409 (`verifyNoInteractions`), `STATE_CONFLICT`, 404, `PageResult` |
| `PurchaseOrderControllerTest` | 4 | `STATE_CONFLICT` (mới từ `D7.4`), `VALIDATION_ERROR` + `errors[0].field`, `PageResult` |
| `GoodsReceiptControllerTest` | 4 | `IDEMPOTENCY_CONFLICT`, `PLANNED_QUANTITY_EXCEEDED` + `STATE_CONFLICT` (mới), header forward |
| `PurchaseRequisitionControllerTest` | 5 | 3 nhánh 409 mới từ `D7.4`, `PageResult` |
| `PlanningRunControllerTest` | 4 | `code` + 4 ô summary spec §2.4 (`D4`), `MISSING_BOM`, `X-Plant-Id` mismatch |
| `RoutingControllerTest` | 4 | **409 và 422 cạnh nhau** (status vs nội dung thiếu), DELETE envelope |
| `BomControllerTest` | 4 | `BOM_CIRCULAR_REFERENCE`, `STATE_CONFLICT` (mới), **422 giữ nguyên** |

`RoutingControllerTest` và `BomControllerTest` cố ý đặt nhánh **409** và nhánh **422** trong **cùng một
class** — đó là cách làm cho một lần "dọn cho đồng bộ" trong tương lai hiện ra ngay trong diff.

**`D7.3` — bảng rà đầy đủ 44 throw site** (`OPERATION_NOT_ALLOWED` trong 4 module; số dòng tính theo
code **trước** khi sửa):

| Module · File | Dòng | Kiểm tra gì | Quyết định |
|---|---|---|---|
| `bom` · `BomService` | 189 | activate BOM **không có line** | **giữ 422** — nội dung thiếu, tiền lệ `RoutingService:90` |
| `bom` · `BomService` | 227 | parent item sai `ItemType` (B9) | **giữ 422** — master data |
| `bom` · `BomService` | 239 | component là `SERVICE` (B9) | **giữ 422** — master data |
| `bom` · `BomService` | 264 | `ensureDraft` — BOM không `DRAFT` (B11) | 🔧 **→ `STATE_CONFLICT` 409** |
| `bom` · `BomService` | 271 | item khác company | **giữ 422** — master data |
| `bom` · `BomService` | 278 | company `INACTIVE` | **giữ 422** — master data |
| `purchasing` · `GoodsReceiptService` | 58 | `!order.canReceive()` | 🔧 **→ `STATE_CONFLICT` 409** |
| `purchasing` · `GoodsReceiptService` | 85 | nhận vượt số còn lại (B27) | 🔧 **→ `PLANNED_QUANTITY_EXCEEDED` 409** |
| `purchasing` · `GoodsReceiptService` | 150 | `!receipt.isPosted()` | 🔧 **→ `STATE_CONFLICT` 409** |
| `purchasing` · `PurchaseOrderService` | 200 | `ensureDraft` (2 caller: send, cancel) | 🔧 **→ `STATE_CONFLICT` 409** |
| `purchasing` · `PurchaseOrderService` | 206, 213, 220, 227 | plant/warehouse/supplier/item khác company | **giữ 422** ×4 |
| `purchasing` · `PurchaseRequisitionService` | 77 | suggestion không `APPROVED` | 🔧 **→ `STATE_CONFLICT` 409** |
| `purchasing` · `PurchaseRequisitionService` | 81 | sai `SupplySuggestionType` | **giữ 422** — dữ liệu đầu vào sai (gọi sai endpoint) |
| `purchasing` · `PurchaseRequisitionService` | 156 | duyệt vượt số xin | 🔧 **→ `PLANNED_QUANTITY_EXCEEDED` 409** |
| `purchasing` · `PurchaseRequisitionService` | 192 | `!requisition.isApproved()` | 🔧 **→ `STATE_CONFLICT` 409** |
| `purchasing` · `PurchaseRequisitionService` | 278 | PR có **nhiều** supplier | **giữ 422** — dữ liệu đầu vào sai |
| `purchasing` · `PurchaseRequisitionService` | 286 | `ensureDraft` (3 caller: approve, reject, cancel) | 🔧 **→ `STATE_CONFLICT` 409** |
| `purchasing` · `PurchaseRequisitionService` | 293, 300, 307, 314 | plant/warehouse/item/supplier khác company | **giữ 422** ×4 |
| `purchasing` · `SupplierService` | 182, 195 | supplier `INACTIVE` | **giữ 422** ×2 — master data |
| `purchasing` · `SupplierService` | 212 | itemSupplier không thuộc item | **giữ 422** |
| `purchasing` · `SupplierService` | 220 | item và supplier khác company | **giữ 422** |
| `purchasing` · `SupplierService` | 234 | đã có preferred supplier `ACTIVE` (B29) | **giữ 422** — ràng buộc duy nhất trên master data |
| `organization` · `AccessControlService` | 221, 232, 243 | role / permission / scope `INACTIVE` | **giữ 422** ×3 |
| `organization` · `AccessControlService` | 270 | `expiresAt` đã qua | **giữ 422** — validate đầu vào |
| `organization` · `OrganizationLookupService` | 40, 52, 65 | company / plant / warehouse `INACTIVE` | **giữ 422** ×3 |
| `organization` · `OrganizationLookupService` | 79 | warehouse không thuộc plant | **giữ 422** |
| `organization` · `OrganizationService` | 100, 157 | company / plant cha `INACTIVE` | **giữ 422** ×2 |
| `sales` · `SalesOrderService` | 184 | `dueDate` < `orderDate` | **giữ 422** — validate đầu vào |
| `sales` · `SalesOrderService` | 205, 212 | plant / item khác company | **giữ 422** ×2 |

**Tổng: sửa 9 · giữ 35.** `organization` và `sales` **0 chỗ phải sửa** — `sales` vốn đã dùng
`STATE_CONFLICT` cho status từ `F3` (ghi ở `module/sales/CLAUDE.md` mục 4), `organization` không có
state machine chứng từ nào.

**Test cũ sửa theo `R10` (6 assertion, không xoá case nào, không nới assertion):** `BomServiceTest`
(`updateLine_nonDraftBom_fails`), `GoodsReceiptServiceTest` ×2, `PurchaseOrderServiceTest`
(`cancelSentOrder_fails`), `PurchaseRequisitionServiceTest` ×2.

**Không sửa assertion nào:** `GlobalExceptionHandlerTest`, `AuthControllerTest`,
`WorkOrderControllerTest`, `InventoryControllerTest`, `ProductionFlowE2EIT` (5 case, chạy contract thật
qua HTTP — là lưới an toàn của `D7.4` và nó xanh nguyên). Chỗ duy nhất chạm test `T2`: **đổi tên**
`InventoryControllerTest.issue_insufficientStock_returns422` → `...returns409`, vì tên đã sai từ `F5`
(assertion vốn đọc status từ enum nên chưa bao giờ sai) — `R7`.

**Nghiệm thu mutation:** 3 mutation, mỗi cái đúng 1 case đỏ, đã revert — bảng chi tiết ở
`CLAUDE.md §0.15`. Điểm quan trọng: **cả 3 đều giữ nguyên HTTP status** (409→409), nên chúng chứng minh
assertion bám `$.code` chứ không phải `status()`. Mutation đổi status là bài dễ, không có giá trị.

**Quyết định trong phase (khác kế hoạch, có chủ đích):**

| # | Quyết định | Lý do |
|---|---|---|
| 1 | Kế hoạch xếp `MISSING_BOM`/`MISSING_ROUTING` cho `PurchaseRequisitionController`; thực tế đặt ở `PlanningRunController` | Đường convert sinh work order (⇒ cần BOM/routing) là `/convert-to-work-order`, thuộc `PlanningRunController`. `PurchaseRequisitionController` chỉ convert sang PR, không cần BOM |
| 2 | Không dùng `PLANNED_QUANTITY_EXCEEDED` cho "activate chứng từ không có dòng nào" | Đó là *nội dung thiếu*, không phải *vượt trần*. Tiền lệ `RoutingService` giữ nó ở 422 |
| 3 | Dùng `ExceptionFactory.custom(...)` (không `businessRule(...)`) ở 9 chỗ sửa | Khớp tiền lệ `F5` trong `workorder`; `businessRule` đọc như "422" dù thực ra status lấy từ `ErrorCode` |
| 4 | Không sửa `@JsonInclude(NON_NULL)` của `ApiResponse` dù nó khiến `result` **biến mất** thay vì `null` như `error-handling.md §5.1` mô tả | Đổi = breaking change với FE cho **mọi** response lỗi và mọi DELETE. Ngoài phạm vi `D7`; đã ghi thành hệ quả #5 ở `CLAUDE.md §0.15` |
| 5 | Thêm 5 case ở 2 class (thay vì đúng 4/class) | `SalesOrder` và `PurchaseRequisition` có nhiều nhánh 409 mới cần khoá; vẫn dưới mức "viết cho đủ số" |

**Nợ mới phát sinh:** không. Nợ **#2** còn lại đúng 9 controller nhóm B+C ⇒ đã thành phase `D7b` có
danh sách cụ thể. Nợ **#9 đóng hoàn toàn**.

> 🔴 **Đính chính (`D7b`, 2026-07-31):** câu "nợ #9 đóng hoàn toàn" ở trên **không đúng**. `D7` rà 4
> module (`bom`, `purchasing`, `organization`, `sales`) và kết luận cho **4 module đó**; `planning`
> chưa bao giờ nằm trong phạm vi rà, và nó còn **3 chỗ** đọc status chứng từ nhưng vẫn trả 422 —
> thành nợ **#26** ở `CLAUDE.md §0.4`. Bài học: "đã rà N module" **không** bằng "đã rà hết repo";
> phase sau tuyên bố đóng một nợ cross-cutting thì phải liệt kê **module nào không được rà và vì sao**.

---

### 6.1.7 Bản ghi `D7b` ✅ **HOÀN THÀNH 2026-07-31**

Đóng **hết** nợ **#2**. Không migration, không breaking change, **không sửa một dòng `src/main` nào**.
Baseline: 450 → **510 case unit / 86 class**; IT giữ nguyên 30 case / 6 class; failures = 0.

**`D7b.1` + `D7b.2` — 9 class test mới (60 case):**

| Class | Case | Khoá gì |
|---|---|---|
| `ItemControllerTest` | 7 | 422 company `INACTIVE`, 400 hai lỗi cùng field `code`, `PageResult`, clamp `size=500`→100 |
| `ItemWarehouseSettingControllerTest` | 5 | `PUT` trả **200 không 201**, 422 khác company, `PageResult` |
| `SupplierControllerTest` | 7 | **B29** 422 preferred trùng, 422 supplier `INACTIVE`, 409 trùng code, `PageResult` |
| `UserControllerTest` | 7 | 409 `USERNAME_ALREADY_EXISTS` (≠ `RESOURCE_ALREADY_EXISTS`), response không có `password`, `size=0`→20 |
| `OrganizationControllerTest` | 8 | 422 company cha `INACTIVE`, 400 UUID sai ở path, 409 trùng code, `PageResult` |
| `AccessControlControllerTest` | 7 | 422 role/scope `INACTIVE`, 409 assignment trùng, `PageResult` |
| `InventoryReportControllerTest` | 5 | shape dashboard lồng nhau, 400 enum `scopeType` sai, mảng trần của `/low-stock` |
| `PlanningControllerTest` | 4 | `POST` trả **200 không 201**, 422 khác company, 400 thiếu body |
| `PlanningDemandControllerTest` | 10 | `X-Plant-Id` ở **cả** body lẫn query, 400 header sai định dạng, 400 UUID sai ở query, **pin nợ #26** |

**`D7b.3` — kết luận (có số dòng, theo đúng yêu cầu của kế hoạch):** `A4` **được ép thật**.
`PageableFactory.java:22` — `int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE)`
với `DEFAULT_PAGE_SIZE = 20` (`:14`), `MAX_PAGE_SIZE = 100` (`:15`). Mọi controller phân trang đều đi
qua nó — đã verify bằng `grep`: **0** chỗ dựng `PageRequest.of` trực tiếp. ⇒ **không** phải nợ mới,
**không** cần hỏi user, **không** breaking change.

**Quyết định trong lúc làm:**

| # | Quyết định | Lý do |
|---|---|---|
| 1 | Assert clamp bằng **literal** `100`/`20`, không bằng `PageableFactory.MAX_PAGE_SIZE` | Hằng số ở cả hai vế ⇒ mutation đổi nó test vẫn xanh. Bản nháp đầu mắc đúng lỗi này, mutation thứ 3 bắt được |
| 2 | Pin hành vi 422 của `PlanningDemandService.cancel` kèm javadoc, **không** sửa | Sửa là breaking change với FE + nằm ngoài phạm vi "chỉ viết test". Cùng cách `StockBalanceRepositoryIT` xử lý nợ #8 |
| 3 | Không thêm case cho mọi endpoint của controller nhiều method (`Supplier` 8 endpoint, `Organization` 12) | Kế hoạch §1 cảnh báo "6 case cho một controller nhóm C là đi quá"; chọn nhánh có mã lỗi riêng, bỏ nhánh chỉ lặp envelope |
| 4 | Mutation thứ 3 đụng **`src/main`** (`PageableFactory`) thay vì stub | Hai mutation stub chỉ chứng minh assertion bám `$.code`; kết luận `D7b.3` cần bằng chứng ở tầng production code |

**Nợ mới phát sinh:** **#26** — `planning` còn 3 chỗ 422 lẽ ra 409 (xem đính chính ở §6.1.6 và
`CLAUDE.md §0.4`). Cần user chốt vì là breaking change.

---

### `D9` — Mở khoá luồng release  *(nợ #22, #23)* ✅ **ĐÃ LÀM, xem §6.1.2**

Hai defect `D1` tìm ra, cùng nằm quanh gate 1a. Sửa chung vì cùng vùng code và cùng phải sửa B13/B14.

| # | Việc | Ghi chú |
|---|---|---|
| `D9.1` | **Nới B13 cho reservation:** cho phép reserve khi WO ở `DRAFT`/`PLANNED`/`BLOCKED`/`RELEASED`/`IN_PROGRESS` | Reservation **không** sinh stock movement — nó chỉ chuyển available → reserved trên `stock_balances`. Đó là lý do gộp nó vào cùng gate với issue/receipt là sai từ đầu. Thêm `canReserve()` trên `WorkOrder` thay vì tái dùng `canExecute()` |
| `D9.2` | **Sửa persist `BLOCKED`:** tách việc ghi `BLOCKED` ra một bean riêng có `@Transactional(REQUIRES_NEW)` **chỉ ghi rồi return**, gate gọi nó xong mới `throw` ở ngoài | Throw trong cùng method `REQUIRES_NEW` luôn kéo transaction đó rollback — không có cách nào giữ được cả hai trong một method. Sửa cả javadoc đang nói ngược |
| `D9.3` | Xoá seam `forceStatus(...)` trong `ProductionFlowE2EIT`, cho bước 4–5 chạy đúng thứ tự spec (reserve → release) | Đây là **thước đo** phase này đã xong |

**Breaking Changes:** `MaterialReservationServiceTest.reserve_onBlockedWorkOrder_shouldThrow` khẳng
định đúng hành vi cần bỏ ⇒ **sửa** theo `R10` (thành: reserve trên `BLOCKED` **được phép**; thay bằng
case chứng minh `COMPLETED`/`CANCELLED` vẫn bị chặn). Cập nhật B13/B14 ở `module/workorder/CLAUDE.md`
và `docs/system-flow.md`.

**Test bắt buộc:** `R1` assert `ErrorCode` cho các status vẫn bị chặn · 1 case IT chứng minh
`BLOCKED` **sống sót** sau release thất bại (không mock được — phải là `*IT`) · `R10` số case ≥ 396.

---

### `D10` — Hai lỗ hổng contract nhỏ  *(nợ #21, #24)* ✅ **ĐÃ LÀM, xem §6.1.2**

| # | Việc | Ghi chú |
|---|---|---|
| `D10.1` | Thêm `planningDemandId` vào `PlanningDemandLineResponse` | Additive, **không** breaking. Đóng nốt nửa còn lại của nợ #13: hiện FE thấy demand nhưng không gửi lại được lựa chọn vì thiếu đúng cái id mà `POST /planning-runs` resolve |
| `D10.2` | `MaterialIssueService.postFlat`: chuyển `findActiveReservationForIssue` xuống **sau** nhánh replay của `postInternal` | Cần tách `postInternal` thành "tra replay" + "tạo mới" để `postFlat` gọi được đúng thứ tự, hoặc cho `postFlat` tự tra `findWithLinesByIdempotencyKey` trước |

**Test bắt buộc:** `R9` — sửa step 11 của `ProductionFlowE2EIT` từ "409 (pinned defect)" thành
"trả lại chứng từ cũ + không movement mới". Đó là bằng chứng nợ #24 đã trả.

---

### 6.1.8 Bản ghi `D11` ✅ **HOÀN THÀNH 2026-07-31**

Đóng nợ **#25** và **#26** — hai nợ **đúng-sai** cuối cùng của track `D*`. Không migration, không
permission mới, không endpoint mới. Baseline: 510 → **516 case unit / 86 class**; IT giữ nguyên
30 case / 6 class; failures = 0. Coverage unit+IT 79.3% line / **62.9%** branch (+0.2 branch).

**User chốt (2026-07-31): phương án `A` cho cả hai câu hỏi.**

| Nợ | Phương án chọn | Phương án bị loại và vì sao |
|---|---|---|
| **#25** | **A — nới `B13`:** thêm `WorkOrder.canReceipt()` (= `canExecute()` + `COMPLETED`) + `ensureReceiptable` | **B — hoãn `complete()`** tới khi `completedQuantity` bắt kịp: WO làm xong mà chưa ai nhập kho sẽ kẹt `IN_PROGRESS` **vô thời hạn**, và nó đổi nghĩa một cột đã có dữ liệu lịch sử |
| **#26** | **A — sửa cả 3 chỗ sang 409** | **B — giữ 422 + sửa tài liệu:** rẻ, nhưng để lại mâu thuẫn **nội tại** (`convert-to-work-order` 422 vs `convert-to-purchase-requisition` 409 cho cùng một điều kiện) |

**🔴 Lỗi trong chính kế hoạch phase này, phát hiện lúc đọc code trước khi sửa** — ghi lại vì đây là
loại lỗi dễ lặp: `NEXT_PHASE_PLAN.md §3.1` viết "**chỉ** `ProductionReceiptService.postNew` đổi sang
gate mới", suy ra từ tiền lệ `D9` (`D9` thật sự chỉ đổi 2 call site reserve). Nhưng `ensureExecutable`
gác **ba** điểm của vòng đời receipt — `postNew`, `submit`, `approve` — nên làm đúng theo kế hoạch thì
tạo được `DRAFT` trên WO `COMPLETED` rồi **kẹt ở `submit`**: tồn kho vẫn không đổi, bug vẫn nguyên,
chỉ dời đi một bước và **khó thấy hơn trước**. Bài học: tiền lệ cho biết *hình dạng* của cách sửa,
**không** cho biết *số lượng* call site — cái đó phải grep.

**Nghiệm thu mutation (3, đã revert):** ① `canReceipt()` → `canExecute()` ⇒ **4 case đỏ**, mỗi bước
vòng đời một case (chính nó là thứ chứng minh hệ quả ở trên); ② `canReceipt()` cho luôn `CANCELLED`
⇒ 1 case đỏ; ③ `ensureDraft` đổi `STATE_CONFLICT` → `IDEMPOTENCY_CONFLICT` (**cả hai đều 409**) ⇒
1 case đỏ. **2/3 mutation đụng `src/main`** — phase sửa hành vi thì phải như vậy.

**Bằng chứng nghiệp vụ (`D11.4`):** `ProductionFlowE2EIT.productionFlow_fulfilsTheSalesOrderForOutputThatIsNotLotTracked`
đã **bỏ** seed `safetyStock = 2` mà `D5` phải dựng để né #25. Nay MRP plan đúng 10 = số đặt, WO thật sự
đi qua `COMPLETED` giữa luồng, và dòng receipt cuối vẫn nhập kho được ⇒ SO tới `FULFILLED`.

**4 breaking change + đầy đủ hệ quả:** `CLAUDE.md §0.17`. Bất biến: `B13` (**tách lần 3**), `B53`,
`B16` ở `module/workorder/CLAUDE.md`; **`B74` mới** ở `module/planning/CLAUDE.md`.

---

### 6.2 Điều kiện chung cho mọi phase `D*`

1. Chạy `mvn -o test` lấy baseline **trước** khi sửa; kết thúc phải **≥ 412 case** (số sau `D5`),
   `failures = 0` (`R10` — sửa test cũ, **không** xoá). Đọc số ở dòng tổng `Tests run:`, **không** cộng
   file XML trong `target/surefire-reports/` — thư mục đó còn report của class đã đổi tên (bài học `D5`).
2. Phase nào đụng `*IT.java` thì chạy thêm `mvn -o verify` (cần Docker).
3. Mọi bất biến `B<n>` bị sửa phải được cập nhật trong `CLAUDE.md` split file của module đó **trong cùng commit**.
4. Cập nhật tài liệu theo `dev-workflow.md §6.5` — đặc biệt `CLAUDE.md §0.4` (đánh dấu nợ đã trả, ghi phase đã trả).
5. Phase có breaking change: ghi mục "Breaking Changes" vào `NEXT_PHASE_PLAN.md` **trước** khi bắt đầu, không phải sau khi xong.

### 6.3 Các quyết định cần user chốt trước khi khởi động phase tương ứng

| Phase | Câu hỏi | Đề xuất |
|---|---|---|
| `D2` | Cho phép **đúng một** `@SpringBootTest` cho bài nghiệm thu E2E (sửa rule `T1`)? | **Có** — chạy qua Failsafe nên không làm chậm `mvn test` |
| ~~`D5`~~ ✅ | REJECTED trên output **không** lot-tracked xử lý thế nào? | **User chốt `A` (2026-07-30)** — sinh `ADJUST_OUT` rút hàng, `B39` viết lại. Kèm 2 quyết định nữa: audit trail `A2` (không ghi `quality_dispositions` ⇒ **không migration**), edge case `INSUFFICIENT_STOCK` = **để nổ 409** |
| ~~`D6`~~ ✅ | Scope unique key theo `(key, movement_type)` hay thêm `created_by` như spec §10.2? | **User chốt `A` (2026-07-30)** — `(key, movement_type)`; `created_by` nullable làm ràng buộc mất hiệu lực với dữ liệu cũ. Kèm 2 quyết định nữa: `hasMovementForIdempotencyKey` (dead code) = **xoá**, `adjust` = **dời đọc `delta` lên trước lookup** (đổi thứ tự validate, xem bản ghi §6.1.5) |
| ~~`D11`~~ ✅ | Nợ #25: sửa `B13` (cho receipt chạy trên `COMPLETED`) hay `B53` (hoãn `complete()`)? | **User chốt `A` (2026-07-31)** — nới `B13` bằng `canReceipt()`, theo tiền lệ `D9`. Xem §6.1.8 |
| ~~`D11`~~ ✅ | Nợ #26: 3 chỗ `planning` đổi sang 409, hay giữ 422 và sửa tài liệu? | **User chốt `A` (2026-07-31)** — đổi sang `STATE_CONFLICT`; 6 chỗ 422 còn lại của module **cố ý giữ** (bất biến `B74`) |

---

## 7. Bảng Đối Chiếu Spec ⇄ Code  *(lập ở `F7`, 2026-07-31)*

> 🔴 **Vì sao mục này tồn tại.** `F1`–`F6` đóng track `F*` mà **chưa bao giờ** đối chiếu spec ở mức
> section/field — bằng chứng phủ spec duy nhất là kịch bản E2E 11 bước (§11.1), và một kịch bản
> happy-path **không thể** phát hiện một endpoint *không tồn tại*. Cộng thêm việc `.docx` là binary
> **chưa từng được commit**, mỗi phase chỉ đọc phần spec liên quan tới mình. Kết quả: **§9 chưa từng
> được nhắc tới một lần nào trong toàn repo** trước `F7`, và 5 gap nằm ẩn suốt 5 phase.
>
> **Quy tắc dùng bảng này:** phase nào đụng vùng nào thì đối chiếu lại **hàng tương ứng**, và cập nhật
> cột trạng thái trong cùng commit. Nguồn văn bản: **`docs/fe-spec-omniplant.md`** (bản trích), gốc là
> `OmniPlant_MVP_Production_Backend_Handoff.docx`.
>
> 🔴 **DẤU ✅ CHỈ CÓ NGHĨA "đã đối chiếu tới mức ghi trong cột Ghi chú" — KHÔNG có nghĩa "đã đi hết
> bảng field của section đó".** `F7` đánh ✅ cho §2.4/§3.3/§4.2/§5.2/§6.4 sau khi **chỉ** grep
> `itemCode`/`lotCode`; nó chưa từng đi hết các bảng "Trường cần hiển thị". Rà lại ngày 2026-07-31
> hạ 6 hàng xuống ⚠️ và sinh ra phase `F8`. **Ai cập nhật bảng này về sau: ghi rõ đã rà tới đâu, đừng
> ghi "xong".** Đây là lần thứ hai repo học đúng bài học đó (lần đầu: `D7` tuyên bố nợ #9 "đóng hết"
> trong khi `planning` chưa bao giờ nằm trong phạm vi rà — nợ #26, `D11`).

| § | Chủ đề | Trạng thái | Ghi chú |
|---|---|---|---|
| **1** | Phạm vi + luồng end-to-end | ✅ | 9 bước, phủ bởi `ProductionFlowE2EIT` |
| **1.1** | Vai trò và quyền | ✅ | Map sang `PERM_*`, xem §4 "Những gì KHÔNG làm" — **không** đổi sang tên bare |
| **1.2** | Quy ước tích hợp chung | ✅ | Envelope, phân trang, ISO-8601, decimal, `traceId` đều đạt. `X-Plant-Id` áp dụng theo ranh giới §5.6.1 (endpoint nêu `plantId` tường minh), **không** phải mọi API — lệch có chủ đích, đã ghi |
| **2.1** | Điều kiện + netting MRP | ✅ | `F3`+`F5-B`+`D4`. Công thức netting gồm cả open PO (`B67`) |
| **2.2** | Endpoint Planning | ⚠️ **lệch có chủ đích** | Spec đề xuất lồng `/planning-runs/{id}/proposals/…`; repo dùng **phẳng** `/supply-suggestions/{id}`. Tên endpoint là phần *đề xuất* của spec (quyết định gốc §0) |
| **2.3** | Request chạy MRP | ⚠️ **lệch có chủ đích** | `demandLineIds` spec đánh **bắt buộc**, repo cho **optional** để không phá client cũ (`B60`, `module/planning/CLAUDE.md` mục 4) |
| **2.4** | Trường màn hình Planning | ⚠️ **còn 1 field cố ý cắt** *(rà field-level ở `F8`, đóng nốt `G`+`F` ở `F10`)* | `F5-B` + `D4` đã làm `supplyType`, `exceptionState`, `messages[]`, `code` + 4 ô summary. **[`F8`]** thêm run header `grossDemand` (`V39`, đếm tại chỗ từ `MrpCalculationResult`, **chỉ level 0**) + `uom` trên requirement line và proposal. **[`F10`]** thêm `projectedAvailable` trên requirement line (`V40`, **nullable, không backfill** — `B77`) và `sourceRoutingCode`/`sourceRoutingVersion` trên proposal (`V40`, snapshot đóng băng — `B78`). **Đã rà từng ô của bảng ở `F8`, đóng nốt 2 ô ở `F10`; còn 1 mục cố ý cắt:** `sourceBomCode` (nợ **E** — `BomHeader` không có cột code, và `outputQuantity` đổi công thức nổ BOM) |
| **3.1** | State transition Work Order | ✅ | `F5-A` thêm `PLANNED`; `B13` nay tách **ba** gate (`D9`, `D11`) |
| **3.2** | Endpoint Work Order | ✅ **[`F7`]** | `search` (gap #5) và `cancel` + `reason` (gap #3, `V38`) đều thiếu trước `F7`. Đường dẫn list là `/plants/{plantId}/work-orders` thay vì `/work-orders?plantId=` — lệch tên, có chủ đích |
| **3.3** | Trường Work Order + routing snapshot | ⚠️ **còn 3 mục cố ý hoãn/cắt** *(rà từng ô ở `F9`)* | 4 field header + `work_order_operations` ✅ (`F4`/`F5-A`). **[`F8`]** thêm `outputUom`, `planningRunId`/`planningRunCode`/`planningProposalId` (`V39`, cột phẳng — không FK sang planning), `executionStartedAt` (`V39`) + `executionCompletedAt` (**alias `completedAt`, không cột mới** — `B53` pin 1 call site), `bomCapturedAt` (**alias `createdAt`, không cột mới**), `uom` trên component line. **[`F9`]** thêm `reservedQuantity` trên component line — trước đó số này **chỉ** có ở `/material-readiness`, FE phải gọi 2 API cho một bảng. Dùng **đúng cùng** aggregate của readiness để hai màn hình không nói hai số. 🔴 **Đã rà từng ô của bảng ở `F9`; 3 mục còn lại đều cố ý:** `predecessorOperationIds` (nợ `B`), `sourceBomCode` + `outputQuantity` (nợ `E`), **`childBom` (nợ `I`)** — WO snapshot là direct-only theo `B12`. **`predecessorOperationIds` chưa làm** — hoãn có lý do: chưa có consumer (`module/routing/CLAUDE.md` mục 3). **`sourceBomCode` + BOM `outputQuantity` cố ý cắt** — `BomHeader` không có cột nào tương ứng, đây là khái niệm chưa tồn tại chứ không phải field thiếu |
| **3.4** | Quy tắc reservation | ✅ | Auto-reserve FEFO, readiness `canRelease` (`F5-A`) |
| **4.1** | Request + endpoint Material Issue | ✅ **[`F8`]** | `POST /material-issues` phẳng ✅ (`F5-A`), replay-safe từ `D10`. **`GET /material-issues?plantId=&workOrderId=` thêm ở `F8`** (A3) — trước đó path phẳng chỉ nhận POST ⇒ GET trả **405**. Plant-scope ⇒ **có** cross-check `X-Plant-Id`; `workOrderId` là filter tuỳ chọn. JPQL nghiệm thu bằng `MaterialIssueRepositoryIT` |
| **4.2** | Trường Material Issue | ✅ **[`F8`+`F10`]** | `itemSku`/`lotNumber` đổi ở `F7` (gap #2). **[`F8`]** thêm `workOrderCode`, `itemName`, `uom`, `createdByUsername` (batch 1 query/trang, `C14`). **[`F10`]** thêm **`code`** (`MI-` + 8 hex, `V40` backfill + `UNIQUE`, sinh ở `@PrePersist` — `B79`). **Đã rà từng ô của bảng ở `F8`, ô cuối đóng ở `F10`** |
| **4.3** | Transaction bắt buộc | ✅ | Xem §10.1 |
| **5.1** | Endpoint + request Production Execution | ✅ **[`F7`+`F8`]** | 🔴 **`GET /production-executions/candidates` HOÀN TOÀN KHÔNG TỒN TẠI trước `F7`** (gap #1) — cả một màn hình FE không có API. `actualStartedAt`/`actualEndedAt` nay bắt buộc (gap #4). **`GET /production-executions?workOrderId=` phẳng thêm ở `F8`** (A4, controller-only — uỷ quyền thẳng cho `list`, **không** qua self-invocation vì thế sẽ bypass `@PreAuthorize`). 🔴 Endpoint này **cố ý KHÔNG** nhận `X-Plant-Id` (định danh bằng aggregate, §5.6.1 hàng 2) — có test gửi header lệch mà vẫn **200** để pin ranh giới. Endpoint post vẫn lồng dưới `/work-orders/{id}` — lệch tên, có chủ đích |
| **5.2** | Trường Production Execution | ✅ **[`F8`+`F9`+`F10`]** | `F5-A`. **[`F8`]** thêm `operatorUsername` (batch), `workOrderCode`, `uom`, và khối Summary `workOrderRemainingGoodQuantity` + `workOrderCompletionPercent`. 🔴 Hai số Summary đặt trên `ProductionExecutionResponse`, **không** trên `WorkOrderResponse`: ở đó chúng sẽ nằm cạnh `remainingQuantity` (= planned − **completed**, gốc receipt) — con số khác hẳn. 🔴 **[`F9`]** ô thứ tư của panel "Context" — **`uom` trên `ProductionExecutionCandidateResponse`** — `F8` để sót vì bám danh sách 7 DTO trong kế hoạch của nó thay vì bám bảng spec; `F9` thêm (0 query, `productItem` vốn đã join fetch). **[`F10`]** thêm **`code`** (`PE-` + 8 hex, `V40`) và **`status`** — 🔴 `status` là **hằng `"POSTED"`, KHÔNG phải cột** (execution không có đường huỷ/đảo ⇒ cột một-giá-trị là speculative, `§11.5`); prefix `PE-` **lệch spec có chủ đích** (spec ghi "Mã WIP" nhưng `wip_transactions` là bảng khác, không có `code`). Bất biến `B79`. **Đã rà từng ô ở `F9`, 2 ô cuối đóng ở `F10`** |
| **5.3** | Tác động Work Order | ✅ | `B53`: execution làm WO tiến triển, không phải receipt (`F5-A`) |
| **6.1** | State transition Receipt | ✅ | `DRAFT`→`PENDING_APPROVAL`→`APPROVED`/`REJECTED` (`F2`) |
| **6.2** | Endpoint Receipt | ✅ **[`F8`]** | 5 endpoint vòng đời ✅ (`F2`); **2 endpoint còn lại thêm ở `F8`** (A1/A2). `candidates` là bất biến **`B76`** — ba vế trong JPQL, nghiệm thu bằng `WorkOrderRepositoryIT`. `GET /production-receipts?plantId=&status=` có `status` là filter tuỳ chọn, nghiệm thu bằng `ProductionReceiptRepositoryIT`. **Permission:** cả hai dùng `PERM_PRODUCTION_RECEIPT_MANAGE` sẵn có — `V17` seed một permission/loại chứng từ, mô tả nguyên văn *"Post **and read** … documents"*; **không** mint `_READ` mới (§3.8 hệ quả #5) |
| **6.3** | Request tạo Receipt | ✅ | Body phẳng (`F5-A`); bảng `production_receipt_lines` **cố ý giữ** làm storage |
| **6.4** | Trường Receipt | ✅ **[`F8`]** | `F5-A` (nợ #12) đã có `code`, `sourceWipTraceIds`, `*Username`, `itemSku`, `lotNumber`, `outputTrackingMethod`. **[`F8`]** thêm `workOrderCode`, `itemName`, `uom`, `outputLotStatus` (null khi output không lot — phán quyết QC nằm ở `qcResult`, `B40`) và **cả khối "Candidate"** (`ProductionReceiptCandidateResponse`). **Đã đi hết bảng field** |
| **7** | Tồn kho, lot, fulfillment | ✅ | `F2` + `F6`; `D5` mở cho output không lot |
| **7.1** | Phân bổ fulfillment | ✅ | `B62`-`B65` (`F6`) |
| **7.2** | Traceability tối thiểu | ✅ | `traceId` persist trên 4 bảng chứng từ (`F5-A`) |
| **8.1** | Planning message code | ⚠️ **lệch có chủ đích** | 4/5 code. **`PURCHASING_DEFERRED` cố ý không làm** — nó tồn tại vì purchasing ngoài MVP *của FE*; backend này có module purchasing đầy đủ nên gắn nhãn "deferred" là mô tả sai hệ thống |
| **8.2** | Error code | ✅ | `F1` thêm 8 constant; `F5-A`/`D7`/`D11` chỉnh 409/422 |
| **9** | API response mẫu | ✅ **[kiểm ở `F7`]** | 🔴 **Section này chưa từng được nhắc trong repo trước `F7`.** §9.3/§9.4 mô tả response ghép nhiều nhánh, **nhưng câu cuối §9 cho phép** trả entity chính rồi FE refetch, miễn commit atomically ⇒ code hiện tại **hợp lệ**. Ràng buộc thật là §10.1 |
| **10.1** | Mutation cần transaction nguyên tử | ✅ **[kiểm ở `F7`]** | Cả 7 mutation spec liệt kê (convert, reserve, release, issue, execution, approve receipt, QC) đều `@Transactional` |
| **10.2** | Idempotency behavior | ⚠️ **nửa còn lại chưa làm** | `D6` scope theo `(key, movement_type)`; **`created_by` cố ý không** — cột nullable, cần backfill trước (nợ #10) |
| **10.3** | Concurrency | ✅ **[`F8`]** | Nợ **A** đã đóng: **2 test, 2 tầng** — `GlobalExceptionHandlerTest.optimisticLockConflict_*` (409 + `$.code` bằng enum + `X-Trace-Id`) và `WorkOrderRepositoryIT.concurrentGoodQuantityUpdate_staleVersion_*` (chứng minh `@Version` **thật sự** bảo vệ `work_orders.actual_good_quantity`, đúng dòng §10.3 nêu tên). 🔴 **Cố ý loại** kịch bản 2 thread trong `ProductionFlowE2EIT`: bên thua có **3** kết cục tuỳ timing ⇒ assertion ổn định duy nhất là "một trong ba", tệ hơn không có test |
| **11** | Checklist DoD | ✅ **[`F8`]** | Ô **Tests** đã đóng ở `F8` (concurrent mutation, xem §10.3). Work Center/CRP/MES/costing/OEE spec **tự** xếp ngoài MVP |
| **11.1** | Kịch bản acceptance 11 bước | ✅ | `ProductionFlowE2EIT` (`D1`) |

### 7.1 Nợ còn mở sau `F9`

> **Cập nhật 2026-07-31 (sau `F9`):** nợ **A** đã đóng ở `F8`; `F9` sinh thêm nợ **I**. Còn lại
> **B**–**I** trừ **A**. Không cái nào là bug — đều là "khái niệm chưa tồn tại", "làm đúng thì phải
> persist chứ không derive được", hoặc lệch tên có chủ đích.

| # | Nợ | Nguồn | Vì sao chưa làm |
|---|---|---|---|
| ~~**A**~~ | ~~`CONCURRENT_MODIFICATION` (409) không có test nào~~ ✅ **ĐÃ TRẢ (`F8`)** — 2 test, 2 tầng (handler + repository). Xem §10.3 | §10.3, §11 | — |
| **E** | `sourceBomCode` + BOM `outputQuantity` trên WO và trên proposal | §2.4, §3.3 | `BomHeader` **không có** cột code lẫn output quantity ⇒ không phải field thiếu mà là **khái niệm chưa tồn tại**. Làm = feature module BOM (sinh code, unique, backfill mọi BOM cũ) **và** `outputQuantity` đổi công thức nổ BOM (`requiredQuantity` phải chia cho nó) ⇒ đổi số MRP lẫn component line của mọi WO. **User chốt loại khỏi phạm vi `F10`** (2026-08-01) |
| ~~**F**~~ | ~~Proposal `sourceRoutingCode` / `sourceRoutingVersion`~~ ✅ **ĐÃ TRẢ (`F10`, 2026-08-01)** — `V40` + `RoutingLookupService.findActiveRoutingSummaries` (1 query/cấp BOM, **thay** `findItemIdsWithActiveRouting`). Bất biến `B78` | §2.4 | — |
| ~~**G**~~ | ~~Requirement `projectedAvailable`~~ ✅ **ĐÃ TRẢ (`F10`, 2026-08-01)** — `V40` cột **nullable, không backfill**; giá trị là `remainingCoverage` mà `MrpCalculationService` vốn đã tính rồi vứt đi. Bất biến `B77` | §2.4 | — |
| ~~**H**~~ | ~~`MaterialIssue.code`, `ProductionExecution.code`, execution `status`~~ ✅ **ĐÃ TRẢ (`F10`, 2026-08-01)** — `V40` (`MI-`/`PE-`, backfill + `UNIQUE`), sinh ở `@PrePersist`; `status` là **hằng `"POSTED"`, không cột**. Bất biến `B79` | §4.2, §5.2 | — |
| **I** | `childBom` trên BOM-line snapshot của Work Order (§3.3) | `F9` | **Không phải field quên mà là quyết định thiết kế có từ `P1`:** WO snapshot là **direct-only** (`B12`), và MRP nổ cấp sâu thành **work order riêng**. Lồng cây BOM vào snapshot = đổi ngữ nghĩa `B12` + bảng `work_order_component_lines` và mọi chỗ đọc nó. FE xem cây BOM thì gọi endpoint BOM tree sẵn có của `module/bom` |
| **B** | `predecessorOperationIds` (§3.3) | `F4` | Chưa có consumer — dependency graph chỉ có nghĩa khi có scheduling.<br>🔴 **[2026-08-04] Lý do này SẮP HẾT HIỆU LỰC:** `C2-8` (Capacity Board + schedule adjustment, §8) **chính là** consumer đó — spec FE đòi backend trả warning khi "sequence không hợp lệ", mà không có dependency graph thì không có gì để kiểm ngoài `sequence` tăng dần. ⇒ Khi khởi động `C2-8`, đọc lại nợ này **trước**, đừng thiết kế capacity rồi mới phát hiện thiếu |
| **C** | `created_by` trong scope `Idempotency-Key` (§10.2) | `D6` | Cột nullable ⇒ cần backfill trước |
| **D** | 17 DTO ngoài luồng spec vẫn dùng `itemCode`/`lotCode` | `F7` gap #2 | **Phương án A user chốt**: spec không mô tả màn hình cho chúng. Danh sách chính xác ở §3.7 |

---

## 8. Track `C2-*` — Gap API Cho Capstone 2  *(soạn 2026-08-04; `C2-0`+`C2-5`+`C2-3`+`C2-4`+`C2-6` ✅ xong)*

> **Nguồn:** `BACKEND_CAPSTONE2_API_GAPS.md` (FE team, 2026-08-04) — **file khác** với spec `.docx` của
> track `F*`. Nó liệt kê **6 nhóm P0** (UOM, Inventory Lot, Audit Logs, Work Center, Shift/Calendar,
> Capacity Board), **6 mục P1** và một checklist nghiệm thu **13 ô**.
>
> **Văn bản đối ngoại:** `docs/capstone2-api-gap-response.md` — phản hồi chính thức gửi FE, chứa 3 mục
> họ báo thiếu mà **đã có**, 2 chỗ họ mô tả nhẹ hơn thực tế, và **5 câu hỏi đang chờ FE**.
>
> 🔴 **Baseline đối chiếu là CODE THẬT.** Tài liệu FE dẫn `plans/openapi/openapi-2026-08-04.json` —
> file đó **không tồn tại trong repo này**, nên không kiểm được snapshot họ đọc. Ba mục họ báo thiếu
> hoá ra đã có, rất có thể vì snapshot cũ. Đã đề nghị FE commit snapshot hoặc cho commit hash.

### 8.0 Trạng thái checklist §6 của tài liệu FE — **9/13** *(cập nhật sau `C2-8`)*

| # | Ô checklist | Trạng thái |
|---|---|---|
| 1 | Auth refresh rotation **+ concurrent refresh** | 🟡 **một nửa** — rotation/RTR/absolute timeout ✅ (`D8a`,`D8b`); 🔴 **concurrent refresh KHÔNG pass, cố ý** (`CLAUDE.md §0.22` #5: cần lock/CAS; double-submit có thể bị force-logout oan). FE coi đây là điều kiện nghiệm thu ⇒ phải mở phase riêng |
| 2 | Không còn 500 không có trace ở happy path | 🟡 3 lỗi **được báo** đã sửa (`§0.24`); **chưa** rà toàn bộ endpoint ⇒ không tuyên bố "không còn" |
| 3 | UOM CRUD/lifecycle | ✅ **`C2-3`** (2026-08-04) — 7 endpoint, `V42`+`V43` |
| 4 | Inventory Lot list/detail/status | ❌ `C2-2` — **bị chặn** |
| 5 | Audit list/detail | ❌ `C2-1` — **bị chặn** |
| 6 | Work Center CRUD/lifecycle | ✅ **`C2-6`** (2026-08-05) — 7 endpoint, `V44`+`V45`, xem §8.6 |
| 7 | Shift/Calendar CRUD/lifecycle | ✅ **`C2-7`** (2026-08-05) — 14 endpoint, `V46`+`V47`, xem §8.7 |
| 8 | Capacity Board + schedule adjustment | ✅ **`C2-8`** (2026-08-05) — 2 endpoint, `V48`+`V49`, xem §8.7b |
| 9 | BOM/Routing deactivate | ✅ **`C2-0`** — đã có từ trước, chỉ là verb `DELETE` |
| 10 | SO DRAFT update | ✅ **`C2-4`** (2026-08-05) — `PATCH /sales-orders/{id}`, xem §8.5 |
| 11 | Role/Scope/Assignment reads + lifecycle | ✅ **`C2-4`** (2026-08-05) — 9 endpoint, xem §8.5 |
| 12 | Over-BOM approval semantics | ✅ **`C2-4`** (2026-08-05) — phương án (1) chốt, `docs/capstone2-api-gap-response.md §5` câu 4 đã trả lời |
| 13 | Multi-Plant isolation test data | ✅ **`C2-5`** — 2 plant + 3 account, isolation kiểm qua HTTP thật |

**Ngoài checklist:** CORS ✅ (`C2-5`) · **time variance** ✅ **`C2-4`** (2026-08-05) — xem §8.5 ·
`RoutingOperation.workCenterCode` → FK ✅ **`C2-6`** (2026-08-05) — xem §8.6 · `WorkCenter.workCalendarId`
FK ✅ **`C2-7`** (2026-08-05) — xem §8.7 · `WorkOrderOperation.workCenter` FK (đảo ngược quyết định
"giữ String" của `C2-6`, xem §8.9) + lịch operation sinh ở `release()` ✅ **`C2-8`** (2026-08-05) —
xem §8.7b.

🔴 **Phát hiện khi làm `C2-3` (2026-08-04), áp dụng cho MỌI ô ✅ dùng account seed `C2-5`:**
`manager.a`/`operator.a` (scope `PLANT`) **không dùng được** để probe permission gác bằng
`@permissionGuard.hasPermission(...)` thuần (không resource) — cơ chế đó chỉ đọc assignment có
`scopeType = GLOBAL`, và hiện tại **chỉ `admin`** có scope đó. Ô 3 (UOM) đã xác nhận điều này **không
riêng UOM**: `manager.a` gọi `GET /companies` (cùng cơ chế, có từ trước `C2-*`) cũng 403. Khi kiểm bất
kỳ ô nào dùng `hasPermission` (UOM, `PERM_ORG_*`, `PERM_ACCESS_MANAGE`), dùng `admin`, không dùng
account seed `C2-5`. Chi tiết: `module/uom/CLAUDE.md`.

### 8.1 Bảng phase

Sắp theo **giá trị/rủi ro**, **không** theo thứ tự trong tài liệu FE.

| Phase | Nội dung | Migration | Trạng thái |
|---|---|---|---|
| `C2-0` | Phản hồi FE + sửa `@Operation` 3 endpoint + api-guide | không | ✅ **2026-08-04** — §8.2 |
| `C2-5` | CORS + dev-seed 2 plant/3 account + **`V41`** sửa lệch RBAC | **`V41`** | ✅ **2026-08-04** — §8.3 |
| `C2-3` | UOM master (7 endpoint, bảng mới, **global**) | **`V42`+`V43`** | ✅ **2026-08-04** — §8.4 |
| `C2-4` | `PATCH /sales-orders/{id}` · Role/Scope lifecycle · `GET /access/assignments` · chốt over-BOM · **time variance** | không | ✅ **2026-08-05** — §8.5 |
| `C2-6` | Work Center entity + CRUD + `RoutingOperation` FK | **`V44`+`V45`** | ✅ **2026-08-05** — §8.6 |
| `C2-7` | Shift + Work Calendar entity + CRUD + Work Center FK | **`V46`+`V47`** | ✅ **2026-08-05** — §8.7 |
| `C2-8` | Capacity Board + schedule adjustment | **`V48`+`V49`** | ✅ **2026-08-05** — §8.7b |
| **`C2-1`** | Audit Logs read API | `V50` (1 cột) | 🔴 **BỊ CHẶN** — chờ FE câu 1 |
| **`C2-2`** | Inventory Lot list/detail/status | `V51` | 🔴 **BỊ CHẶN** — chờ FE câu 2 |

> `C2-6`..`C2-8` **chính là phase `P4`** của `MANUFACTURING_GAP_ROADMAP.md` (Work Center/CRP) — nay
> **đã đóng cả cụm**. Dùng mã `C2-*` để `git log --grep` truy theo đợt Capstone 2.
>
> **Số migration ở bảng trên là dự kiến** — thực tế lấy số kế tiếp tại thời điểm làm (`C5`: không sửa
> migration cũ). Cao nhất hiện tại: **`V49`**.

### 8.2 Bản ghi `C2-0` ✅ **HOÀN THÀNH 2026-08-04**

**Không** code logic. Ba mục FE báo thiếu thì **đã có**, nguyên nhân là **verb + OpenAPI summary**:

| FE nói | Thực tế |
|---|---|
| §4.1 BOM chưa có Deactivate | **Có** — `DELETE /api/v1/boms/{bomId}` |
| §4.2 Routing chưa thấy Deactivate | **Có** — `DELETE /api/v1/routings/{routingId}` |
| §5 variance "mô tả đầy đủ material, **time**, output" | Endpoint có, **nhưng thiếu thật phần time** |

Repo dùng `DELETE` cho deactivate theo `C6` (không hard-delete); FE quét theo path `/deactivate` nên
không thấy. **Không đổi verb** — đổi là breaking change cho 6 endpoint deactivate khác đang chạy.
Đã sửa `@Operation(summary)` + `description` của cả hai để OpenAPI nói thẳng *"this IS the deactivate
command"*, và ghi khối cảnh báo vào `docs/api-guide-for-frontend.md`.

🔴 **`C2-0` sửa một tuyên bố sai của chính bản kế hoạch mình** — và đó là lần **thứ tư** repo mắc cùng
lỗi (§7 đầu mục, `CLAUDE.md §0.20`). Kế hoạch xếp variance vào "chỉ cần tài liệu" vì đọc *tên endpoint
có tồn tại* rồi kết luận. Mở DTO ra đọc thì `WorkOrderVarianceResponse` = `materialLines` +
`outputVariance` + `wipSummary` ⇒ **không có time variance**, và không chỗ nào trong `src/main` tính nó.
Dữ liệu thì đủ (`ProductionExecution.actualStartedAt`/`actualEndedAt` +
`WorkOrderOperation.setupMinutes`/`runMinutesPerUnit`) ⇒ làm được, nhưng là **logic + DTO mới** ⇒ xếp
vào `C2-4`. ⇒ **Quy tắc cho mọi phase `C2-*` còn lại: mở DTO đọc từng field, đừng dừng ở "endpoint có
tồn tại không".**

### 8.3 Bản ghi `C2-5` ✅ **HOÀN THÀNH 2026-08-04**

Migration **`V41`**. Bản ghi đầy đủ + 4 nghiệm thu mutation: **`CLAUDE.md §0.25`**.

| Phần | Nội dung |
|---|---|
| `C2-5a` | **CORS** — `CorsProperties` (`app.cors.*`) + `corsConfigurationSource()`. Trước đó repo có **0 dòng** CORS |
| `C2-5b` | **`src/main/resources/db/dev-seed.sql`** — 1 company + 2 plant + 6 warehouse + 3 account. **Chạy tay, KHÔNG migration** |
| `C2-5c` | **`V41`** — 10 permission cho `MANAGER`, 6 cho `OPERATOR` theo `docs/roles-and-permissions.md` |

🔴 **Defect nặng nhất của phase KHÔNG nằm trong kế hoạch** (kế hoạch còn dự đoán phase này "không
migration"): seed sớm (`V7`–`V15`) ghi `WHERE r.code = 'ADMIN'`, seed sau (`V17`+) ghi đủ 3 role ⇒
**12 permission lõi chỉ `ADMIN` có** ⇒ login `manager.a` scope PLANT-A gọi
`GET /plants/{A}/work-orders` trả **403 ngay trên plant của chính nó** ⇒ **mọi account không phải
`admin` vô dụng** ở màn hình lõi. Đây là khoản nợ `F8` đã ghi và cố ý hoãn (*"sửa đúng chỗ là role
seed — quyết định nới quyền, ngoài phạm vi F8"*), nay đóng theo quyết định user: **tài liệu là nguồn,
code là chỗ trôi**.

**Ba bài học áp cho mọi phase `C2-*` còn lại:**

1. 🔴 **`PermissionCatalogTest` không phủ được ma trận grant.** Nó chỉ chứng minh `PERM_*` **có dòng
   trong bảng `permissions`**; "được cấp cho role nào" là sự thật **khác**, nằm ở `role_permissions`, và
   **chỉ DB thật** trả lời được. Phase nào thêm permission phải bổ sung **cả hai** dạng assertion:
   **dương** (role phải có gì) và **cấm** (role không được có gì — separation of duties). 3 test mới ở
   `FlywayMigrationIT.migrate_v41_*`; tập admin-only sau `V41` là **đúng 2**: `PERM_ORG_MANAGE`,
   `PERM_ACCESS_MANAGE`.
2. 🔴 **`@Valid` chạy TRƯỚC method security.** Probe authz bằng body sai định dạng trả **400
   `VALIDATION_ERROR`** và làm người kiểm kết luận sai là "đã chặn". Tôi mắc đúng lỗi này khi tự kiểm
   `C2-5` và phải làm lại probe với body hợp lệ.
3. 🔴 **Config bảo mật "trông như đã wire" là loại bug tệ nhất.** `CorsFilter` nằm trong chain sẵn
   (default Spring Security) nên không ai thấy thiếu, mà **không origin nào được phép**; và `curl`
   không gửi preflight nên test tay vẫn xanh. ⇒ Kiểm config bảo mật bằng **browser-shaped request**
   (preflight `OPTIONS`), không chỉ curl. Và CORS **phải** wire qua `http.cors(...)`, không phải
   `WebMvcConfigurer` riêng — preflight không mang `Authorization` nên chỉ đường đó short-circuit được
   trước `.anyRequest().authenticated()`.

### 8.4 Bản ghi `C2-3` ✅ **HOÀN THÀNH 2026-08-04**

Migration **`V42`** (schema) + **`V43`** (seed permission). Bản ghi đầy đủ + 4 nghiệm thu mutation:
**`CLAUDE.md §0.26`**. Bất biến `B82`-`B85`: **`module/uom/CLAUDE.md`**.

| Quyết định | Nội dung |
|---|---|
| Global, không company-scope | `uoms` **không có** `company_id` — theo đúng dữ kiện FE liệt kê (`GET /uoms` không lọc theo company) |
| Lifecycle | `POST .../activate` + `POST .../deactivate`, không dùng `DELETE` — greenfield API, đi theo đúng 2 verb FE liệt kê tường minh |
| `code` immutable | `UomUpdateRequest` **không có field `code`** — compile-time guarantee, không validate runtime |
| Permission | `PERM_UOM_READ` (3 role), `PERM_UOM_MANAGE` (ADMIN+MANAGER) — theo khuôn `V31` (routing) |

🔴 **Phát hiện khi kiểm chứng qua HTTP thật, KHÔNG phải bug của `C2-3`:** `PermissionGuard.hasPermission(...)`
(cơ chế gác `PERM_UOM_*`, giống `PERM_ORG_READ`/`_MANAGE`) chỉ đọc assignment có **`scopeType = GLOBAL`**.
Account seed `manager.a`/`operator.a` (`C2-5`, scope `PLANT`) trả **403 trên mọi endpoint UOM**, kể cả
`GET /uoms` dù `V43` đã cấp `PERM_UOM_READ` cho `OPERATOR`. Xác nhận **không riêng UOM**: `manager.a` gọi
`GET /companies` (cùng cơ chế, có từ trước `C2-*`) cũng 403. ⇒ **Bài học cho mọi ô checklist §8.0 dùng
account seed `C2-5`:** muốn probe permission gác bằng `hasPermission` thuần, phải dùng `admin`.

**Nghiệm thu mutation (4, đã revert — 4/4 đụng `src/main` hoặc migration):** typo permission string
trong `create` ⇒ 2 case đỏ · đổi mã lỗi trùng `code` ⇒ 1 case đỏ · bỏ `cast(:keyword as string)` khỏi
`UomRepository.search` ⇒ **2 case đỏ, và unit test (mock) xác nhận KHÔNG bắt được** — đúng điều kiện dự
phòng mà kế hoạch phase tự ghi, nên đã thêm `UomRepositoryIT` (**class IT thứ 12**) · bỏ grant
`PERM_UOM_READ` của `OPERATOR` khỏi `V43` ⇒ 1 case đỏ ở `FlywayMigrationIT` (test mới, theo khuôn
`migrate_v41_*` của `C2-5`).

**Breaking changes — wire: KHÔNG có.** Toàn bộ additive.

---

### 8.5 Bản ghi `C2-4` ✅ **HOÀN THÀNH 2026-08-05**

**Không migration.** Bản ghi đầy đủ + nghiệm thu mutation: **`CLAUDE.md §0.27`**. Bất biến `B86`
(`module/workorder/CLAUDE.md`), `B87` (`module/sales/CLAUDE.md`), `B88` (`module/organization/CLAUDE.md`).

| Phần | Nội dung |
|---|---|
| A | `PATCH /sales-orders/{id}` — full-replace, chỉ `DRAFT`, `expectedVersion` bắt buộc |
| B | 9 endpoint Role/Scope lifecycle (`GET`/`PATCH`/`activate`/`deactivate` × 2 + `GET /access/assignments`), tái dùng `PERM_ACCESS_MANAGE` |
| C | Chốt contract over-BOM: phương án (1) đã chạy (`B15`) — chỉ cập nhật tài liệu, không code |
| D | `timeVariance` trên `GET /work-orders/{id}/variance` |

🔴 **Defect thật bắt được lúc `mvn verify`, không phải khi code Part D:** kế hoạch gốc định nới
`WorkOrderRepository.findWithDetailsByWorkOrderId` thêm `"operations"` vào `@EntityGraph` sẵn có
(đã có `"componentLines"`). Cả hai đều là `List` ("bag") ⇒ Hibernate ném
`MultipleBagFetchException` ngay từ query đầu tiên — sập **toàn bộ** endpoint variance (và
`ProductionFlowE2EIT`, vì nó gọi cùng entity graph), không chỉ phần time. `mvn test` (mock
repository) không thấy được vì `@EntityGraph` không chạy trong unit test — chỉ `mvn verify` (Testcontainers
thật) bắt được. Sửa: operations đọc qua **một query riêng**
(`WorkOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc`, method có sẵn) thay vì
`workOrder.getOperations()` — 1 query thêm, không phải 1/dòng (`C14`), và không đụng entity graph hiện có.
⇒ **Bài học cho mọi phase sau:** trước khi thêm bất kỳ path thứ hai vào một `@EntityGraph` đã có
collection `List`, kiểm xem path đó cũng là `List` không — nếu có, tách query.

**Nghiệm thu:** `mvn -o clean verify` — **661 case unit + 78 case IT / 12 class IT, failures = 0,
errors = 0** (baseline trước phase: 617 unit + 78 IT / 12 class — không migration nên số IT không đổi,
Part D thêm rồi bỏ một IT case khi đổi thiết kế). Toàn bộ 44 case mới đều ở tầng unit
(`SalesOrderServiceTest`/`SalesOrderControllerTest`/`SalesOrderMethodSecurityTest`,
`AccessControlServiceTest`/`AccessControlControllerTest`/`AccessControlMethodSecurityTest`,
`WorkOrderVarianceServiceTest`).

**Breaking changes — wire: KHÔNG có.** Toàn bộ additive: 1 endpoint `PATCH` mới, 9 endpoint Role/Scope
mới, 1 field mới (`timeVariance`) trên `WorkOrderVarianceResponse`. **Java positional:**
`WorkOrderVarianceResponse` +1 component (cuối), `WorkOrderVarianceService` constructor +2 tham số
(`ProductionExecutionRepository`, `WorkOrderOperationRepository`).

---

### 8.6 Bản ghi `C2-6` ✅ **HOÀN THÀNH 2026-08-05**

Migration **`V44`** (bảng `work_centers` + cột `routing_operations.work_center_id`) + **`V45`** (seed
permission). Bản ghi đầy đủ + nghiệm thu mutation: **`CLAUDE.md §0.28`**. Bất biến `B_wc1`-`B_wc3`
(`module/workcenter/CLAUDE.md`).

| Phần | Nội dung |
|---|---|
| A | `WorkCenter` **per-plant**, 7 endpoint, `PERM_WORK_CENTER_READ`/`_MANAGE` theo khuôn Routing |
| B | `RoutingOperation.workCenterCode` (String) → `workCenter` (FK, **nullable, không backfill**) |

🔴 **Defect thật bắt được lúc `mvn verify`, không phải lúc viết code:** `V30` tạo
`routing_operations.work_center_code` là `NOT NULL`. Bản nháp đầu của `V44` chỉ `ADD COLUMN
work_center_id` mà quên `ALTER COLUMN work_center_code DROP NOT NULL` — vì entity không còn map cột
đó, **mọi** insert `RoutingOperation` mới (kể cả seed của `ProductionFlowE2EIT`) chết với
`null value in column "work_center_code" violates not-null constraint`. `mvn test` (mock repository)
không thấy được; chỉ Testcontainers thật bắt được — lần thứ ba trong repo cùng loại lỗ hổng
(`§0.24`/`§0.26`).

Bất biến `B_wc2` (mọi operation trong 1 routing phải cùng plant) nghiệm thu mutation: bỏ dòng gọi
`ensureOperationsShareOnePlant(...)` ⇒ đúng **1** case đỏ
(`RoutingServiceTest.create_operationsAcrossTwoPlants_throwsOperationNotAllowed`).

**Nghiệm thu:** `mvn -o clean verify` — **690 case unit + 79 case IT / 12 class IT, failures = 0,
errors = 0** (baseline trước phase: 661 unit + 78 IT / 12 class — không thêm class IT mới, chỉ
`FlywayMigrationIT` +1 case cho ma trận grant `V45`).

**Breaking changes — wire:** `RoutingOperationRequest.workCenterCode` (String) → `workCenterId`
(UUID) — client cũ gửi text tự do **sẽ hỏng**. `RoutingOperationResponse` thêm `workCenterId`
(additive). 7 endpoint Work Center: additive. **Java positional:** `RoutingOperation` field
`workCenterCode` → `workCenter`; `RoutingService` constructor +1 tham số (`WorkCenterLookupService`).

**🔴 Đính chính (viết lại ở `C2-8`, 2026-08-05):** dòng ngay trên đây từng kết luận
"`sourceRoutingOperationId` đã đủ, không cần thêm cột riêng cho work center" — kết luận đó **sai**.
`C2-8` (Capacity Board) hoá ra **là** consumer cần đúng thứ đó: nó phải join/filter theo Work Center
sống để đọc `capacityUnits`/`workCalendar`, việc `sourceRoutingOperationId` (chỉ để truy vết, không
bao giờ dereference — B49) không làm được. `C2-8` thêm `WorkOrderOperation.workCenter` (FK,
**không** đặt tên `source_work_center_id` — nó không phải cột chỉ-để-truy-vết, mà được đọc thật bởi
Capacity Board). Xem §8.7b + `CLAUDE.md §0.30`.

---

### 8.7 Bản ghi `C2-7` ✅ **HOÀN THÀNH 2026-08-05**

Migration **`V46`** (bảng `shifts`/`shift_breaks`/`work_calendars`/`work_calendar_weekly_shifts`/
`work_calendar_exceptions` + cột `work_centers.work_calendar_id`) + **`V47`** (seed permission). Bản
ghi đầy đủ: **`CLAUDE.md §0.29`**. Bất biến `B_sh1`-`B_sh2`, `B_cal1`-`B_cal2` (`module/shift/CLAUDE.md`),
`B_wc4` (`module/workcenter/CLAUDE.md`).

| Phần | Nội dung |
|---|---|
| A | `Shift` per-plant — 1 interval + `breaks[]`, 7 endpoint, `PERM_SHIFT_READ`/`_MANAGE` |
| B | `WorkCalendar` per-plant — lịch tuần (weekday → nhiều shift được) + exception `NON_WORKING` một chiều, 7 endpoint, `PERM_WORK_CALENDAR_READ`/`_MANAGE` (permission riêng dù cùng module) |
| C | `WorkCenter.workCalendarId` (FK tuỳ chọn) + bất biến `B_wc4` |
| D | `WorkingWindowCalculator` — net working window, internal only, chờ `C2-8` gọi |

🔴 **`MultipleBagFetchException` — lần thứ hai trong repo, lần này tránh được TRƯỚC khi chạy `mvn
verify`, không phải bắt được sau.** `C2-4` (§8.5) từng sập bẫy này khi thêm `"operations"` vào một
`@EntityGraph` đã có `"componentLines"`. `WorkCalendar` có **hai** association `List`
(`weeklyShifts`, `exceptions`) — thiết kế `WorkCalendarRepository.findWithWeeklyShiftsByWorkCalendarId`
cố tình chỉ join-fetch `weeklyShifts` (+ `shift` + `breaks` của nó), để `exceptions` lazy-load trong
cùng transaction. Áp dụng đúng bài học đã ghi ở dòng "mọi phase" của §8.9 (trước đây §8.8) thay vì
lặp lại lỗi.

**Nghiệm thu:** `mvn -o clean verify` — **759 case unit + 80 case IT / 12 class IT, failures = 0,
errors = 0** (baseline trước phase: 690 unit + 79 IT / 12 class — không thêm class IT mới, chỉ
`FlywayMigrationIT` +1 case cho ma trận grant `V47`).

**Breaking changes — wire: KHÔNG có.** Toàn bộ additive: 14 endpoint mới, 1 field mới
(`workCalendarId`) trên `WorkCenterCreateRequest`/`UpdateRequest`/`Response`. **Java positional:**
`WorkCenterService` constructor +1 tham số (`WorkCalendarLookupService`); `WorkCenterCreateRequest`/
`UpdateRequest`/`Response` +1 component (`workCalendarId`, cuối record).

---

### 8.7b Bản ghi `C2-8` ✅ **HOÀN THÀNH 2026-08-05**

Đóng nốt cluster `P4`. Migration **`V48`** (schema: `work_order_operations` +`work_center_id`
+`planned_start_at`+`planned_end_at`+`schedule_adjustment_reason`) + **`V49`** (seed
`PERM_CAPACITY_READ`/`_MANAGE`). Bản ghi đầy đủ: **`CLAUDE.md §0.30`**. Bất biến `B89`-`B90`
(`module/workorder/CLAUDE.md`).

| Phần | Nội dung |
|---|---|
| A | `WorkOrderOperation.workCenter` (FK, cạnh `workCenterCode`) + `plannedStartAt`/`plannedEndAt` sinh ở `WorkOrderService.release()` (forward-schedule tuần tự, đi qua calendar Work Center nếu có) |
| B | `GET /plants/{plantId}/capacity-board` — `CapacityBoardService`, aggregate load/capacity/utilization theo `(workCenter, ngày local theo Plant.timezone)` |
| C | `POST /work-orders/{id}/operations/{id}/schedule-adjustments` — `ScheduleAdjustmentService`, ghi đè thủ công + cờ tư vấn (không auto-shift, không hard-block trừ version/thời gian) |

🔴 **Construct JPQL `function('timezone', plant.timezone, plannedStartAt)` là native-escape đầu tiên
trong repo** (không có tiền lệ native query trước đó) — dùng để gom nhóm load theo ngày **local**
của plant, không phải ngày UTC. `WorkOrderOperationRepositoryIT` (**class IT thứ 13**) khoá bằng một
mốc mà ngày UTC và ngày local (`America/New_York`, tháng 1) lệch nhau có chủ đích, chứng minh chiều
quy đổi đúng — không có nó thì mock repository (hoặc test dùng `UTC` cho mọi timezone) sẽ không bao
giờ thấy được lỗi nếu chiều quy đổi bị đảo ngược.

**Nghiệm thu:** `mvn -o clean verify` — **791 case unit + 87 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 759 unit + 80 IT / 12 class — `+32` unit từ
`WorkingWindowCalculatorTest`(+7, `advance()`) + `WorkOrderServiceTest`(+3, lịch sinh ở `release()`)
+ `CapacityBoardServiceTest`(5) + `CapacityBoardServiceMethodSecurityTest`(2) +
`ScheduleAdjustmentServiceTest`(7) + `ScheduleAdjustmentServiceMethodSecurityTest`(2) +
`CapacityControllerTest`(6); `+7` IT từ `WorkOrderOperationRepositoryIT`(6, class IT mới) +
`FlywayMigrationIT.migrate_v49_*`(1)). **Không** nghiệm thu bằng mutation testing lần này (khác các
phase `C2-*` trước) — bù lại bằng smoke test HTTP thật qua `mvn -o spring-boot:run` trên Postgres
thật (không phải Testcontainer, `docker-compose up -d`): JWT `admin` mang đúng
`PERM_CAPACITY_READ`/`_MANAGE`, `GET .../capacity-board` trả `200` đúng envelope, `POST
.../schedule-adjustments` trên work order không tồn tại trả **403** (không phải 404 — đúng hành vi
sẵn có của `WorkOrderPermissionGuard`, xác nhận không phải bug mới).

**Breaking changes — wire: KHÔNG có** (thuần additive: 2 endpoint mới, 4 field mới trên
`WorkOrderOperationResponse`). **Java positional:** `WorkOrderService` constructor +1 tham số
(`WorkCalendarLookupService`); `WorkOrderOperationResponse` +3 component;
`WorkOrderMethodSecurityTest.Config` sửa theo `R10`.

---

### 8.8 Hai phase đang bị chặn — chặn bởi **thiết kế**, không phải bởi thứ tự

`C2-1` và `C2-2` là hai phase **rẻ nhất** (hạ tầng đã có sẵn) nên kế hoạch gốc xếp lên đầu. Nhưng câu
trả lời của FE **đổi thiết kế**, làm trước rồi sửa lại là tự tạo việc:

| Phase | Chờ gì | Vì sao câu trả lời đổi thiết kế |
|---|---|---|
| `C2-1` | Màn hình Audit dùng được khi `changes[]` **rỗng**? | `AuditLogChange` + repository + bảng (`V6`) **đều tồn tại nhưng 0 call site ghi** — `AuditLogService` chỉ ghi *sự kiện*, không nhận diff. Nếu FE cần diff thật thì phải làm `C2-1b` (sửa `AuditableAspect`), phạm vi khác hẳn `C2-1a` |
| `C2-2` | Màn hình Lot dẫn user sang QC khi lot `HOLD` chờ QC? | Nếu `POST /inventory/lots/{id}/status` cho tự do `HOLD → AVAILABLE` thì lot của production receipt ra `AVAILABLE` **không qua QC** ⇒ `fulfilledQuantity` của SO line **không bao giờ tăng**, đơn treo `IN_PROGRESS` vĩnh viễn **không lỗi nào báo** — dựng lại đúng nợ **#17** mà `D5` vừa trả (`B62`) |

### 8.9 Bẫy đã xác định cho các phase chưa làm

| Phase | Bẫy |
|---|---|
| `C2-1` | `audit_logs` **không có cột `plant_id`** mà filter §3.3 đòi. Thêm cột ⇒ mọi dòng lịch sử `NULL` (audit append-only, không backfill được) ⇒ filter chỉ đúng cho dòng mới. Ghi tường minh `NULL = "trước C2-1"`, đúng tiền lệ `cancel_reason` của `F7` |
| `C2-2` | `InventoryLot` **không có** warehouse, **không có** manufacture date. `warehouseId` bắt buộc phải resolve qua `stock_balances` (tái dùng `StockBalanceRepository.aggregate*`, `C14`); `manufactureDate` map từ `receivedAt` (như alias `bomCapturedAt` của `F8`) — **đừng** thêm cột cho giống tài liệu. Phần **tốn công nhất là source genealogy**, không phải status |
| `C2-3` | **Additive.** `items.unit` là `String NOT NULL` đọc ở **13 call site / 5 mapper**, tất cả read-only để hiển thị `uom` (`F8`/`F9`). ⇒ `C2-3` chỉ dựng bảng `uoms` + CRUD; nối `items.uom_id` FK và bỏ cột `unit` là **phase riêng** |
| ~~`C2-6`~~ 🔴 | ~~Không đổi `WorkOrderOperation.workCenterCode` thành FK...~~ **Dự đoán này ĐÚNG lúc `C2-6` (2026-08-05) nhưng SAI khi `C2-8` tới** (2026-08-05, cùng ngày, phase kế tiếp): `WorkOrderOperation.workCenterCode` giữ String snapshot đúng như dự đoán **tại thời điểm `C2-6`**, nhưng `C2-8` sau đó **thêm FK `workCenter` cạnh nó** vì Capacity Board cần join Work Center sống — dự đoán "sẽ không cần" chỉ đúng vì `C2-6` chưa có consumer, không phải vì không bao giờ cần. Bài học: dự đoán "X sẽ không cần" chỉ có giá trị **tới khi có consumer thật**, đừng đọc nó như kết luận vĩnh viễn. Xem §8.7b |
| ~~`C2-8`~~ ✅ | ~~🔴 `work_order_operations` chưa có `plannedStartAt`/`plannedEndAt`...~~ **Đã xong** (2026-08-05): lịch sinh ở `release()` (chốt với user), nợ B tách `C2-8b` (chốt với user, không làm ở `C2-8`), quy ước qui-thuộc-ngày ca qua đêm áp dụng đúng ở cả `WorkingWindowCalculator.advance` lẫn gom nhóm load theo ngày. Xem §8.7b |
| mọi phase | Permission mới ⇒ seed migration cùng phase **+** `docs/roles-and-permissions.md` (`C10`) **+** cập nhật `adminOnlyByDesign` / checklist ở `FlywayMigrationIT.migrate_v41_*` nếu tập admin-only đổi · query search có param `String` nullable **phải** `cast(:p as string)` (`§0.24`: thiếu nó là **500 toàn endpoint** mà unit test lẫn coverage đều xanh) · `X-Plant-Id` theo **endpoint**, không theo controller (3 phase đã sập bẫy) · mỗi controller mới ⇒ 1 `*ControllerTest` assert `$.code` + đủ 7 field `PageResult` · logic trong JPQL ⇒ bắt buộc `*IT` (`R7`) · ≥3 nghiệm thu mutation, ưu tiên loại **giữ nguyên HTTP status** · **[`C2-4`]** thêm path `List` thứ hai vào một `@EntityGraph` đã có collection `List` ⇒ `MultipleBagFetchException`, chỉ `mvn verify` bắt được — kiểm loại collection trước khi mở rộng entity graph · **[`C2-6`]** thêm cột `NOT NULL` cũ cần `DROP NOT NULL` khi cột đó không còn được entity mới ghi — quên là **500/lỗi insert toàn bộ**, chỉ Testcontainers thật bắt được · **[`C2-7`]** một entity có **hai** association `List` (bag) ⇒ đừng join-fetch cả hai trong cùng `@EntityGraph`/JPQL dù chưa viết code — thiết kế query tách ngay từ đầu, đừng chờ `MultipleBagFetchException` mới sửa · **[`C2-8`]** gom nhóm/lọc theo "ngày" từ một cột `Instant`/`TIMESTAMPTZ` mà có ý nghĩa **local** (không phải UTC) ⇒ bắt buộc quy đổi qua timezone thật (`Plant.timezone` hoặc tương đương) ở **cả** Java lẫn SQL, và phải khớp nhau — viết `*IT` cố ý chọn mốc mà ngày UTC và ngày local lệch nhau để tự kiểm, đừng tin bằng mắt · dự đoán "X sẽ không cần Y vì chưa có consumer" chỉ đúng **tới khi có consumer thật** — đừng đọc nó như kết luận vĩnh viễn khi phase sau đọc lại (xem §8.9 hàng `C2-6`) |

### 8.10 Kiểm chứng (áp dụng mọi phase `C2-*`)

Ngoài §5 (áp cho cả track `F*`):

1. `mvn -o clean verify` — **đọc cả `Errors:`, không chỉ `Failures:`**, ở **cả hai** dòng tổng (`test`
   và `integration-test`). Bài học 2026-08-04: `D8b` báo "66 IT xanh" trong khi thật là 65 xanh / 1 error.
2. Số case **≥ baseline hiện tại: 791 unit + 87 IT / 13 class IT**, `failures = 0`, `errors = 0`.
3. **Chạy thật qua HTTP** cho happy-path mới — cả 3 bug ngày 2026-08-04, defect RBAC của `C2-5`, và
   giới hạn `hasPermission`/`GLOBAL` scope phát hiện ở `C2-3` đều **không** bị unit test phát hiện.
   Dev stack: `erp-postgres:5434` + `erp-redis:6379`.
   ⚠️ Cổng 8080 có thể đang có instance của user; dùng `-Dspring-boot.run.arguments=--server.port=8081`
   và **đừng** tắt process của họ.
4. Phase nào đụng permission/scope ⇒ probe **differential**: cùng một request với 2 role khác nhau, và
   cùng một role với 2 plant khác nhau. Một mình 403 không chứng minh được gì (403 có thể vì scope, vì
   permission, hoặc vì `@Valid` chưa chạy tới). 🔴 **Nếu permission gác bằng `hasPermission` thuần
   (không resource — `PERM_UOM_*`, `PERM_ORG_READ/MANAGE`, `PERM_ACCESS_MANAGE`), account probe phải có
   assignment `scopeType = GLOBAL`** — account seed `C2-5` (`manager.a`/`operator.a`, scope `PLANT`)
   luôn 403 ở nhóm này bất kể migration seed đúng hay sai. Dùng `admin` để probe nhóm này (§8.4).

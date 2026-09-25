# Manufacturing Gap Roadmap – Kế Hoạch Bù Đắp Khoảng Trống So Với Lý Thuyết

> **Bối cảnh:** File này tổng hợp các phần **còn thiếu** của hệ thống khi đối chiếu với:
> 1. Tài liệu lý thuyết **`TÀI LIỆU PHÂN TÍCH CHUYÊN SÂU (1).pdf`** — *"Sự khác biệt giữa ERP Sản xuất
>    và ERP Quản lý Doanh nghiệp Thương mại"* (10 trang, ở thư mục gốc repo)
> 2. Sơ đồ nghiệp vụ mục tiêu `business_flow.jpg`
>
> Sau đó chia thành các **phase độc lập, kiểm soát được**, sắp theo dependency + giá trị.
>
> 🔴 **Đối chiếu gần nhất: 2026-09-25** — đọc lại toàn bộ PDF rồi kiểm từng yêu cầu **trực tiếp trên
> code/schema**, không suy từ tài liệu cũ. Kết quả đầy đủ ở **§2.0**; 5 phase mới (`P7`–`P11`) sinh ra
> từ đúng lượt đối chiếu đó. Hiện trạng đo được: **16 module**, **64 bảng**, **66 migration** (mới nhất
> `V68`).
>
> **Ràng buộc nền tảng:** Hệ thống **CHƯA có tầng OT** (không kết nối trực tiếp máy móc / PLC /
> sensor). Vì vậy roadmap này **chỉ gồm các hạng mục làm được thuần ở tầng ERP**. Các hạng mục
> phụ thuộc OT được tách riêng ở [Mục Deferred](#phase-deferred--phụ-thuộc-ot-chưa-làm) và
> **không nằm trong kế hoạch triển khai hiện tại**.

---

## 1. Nguyên Tắc Chung

- **Tái sử dụng hạ tầng sẵn có:** ưu tiên bám theo pattern đã có (BOM explosion, snapshot line,
  Material Issue/Receipt, Variance service, dynamic RBAC permission) thay vì phát minh mới.
- **Mỗi phase phải build + test PASS độc lập**, không để hệ thống ở trạng thái nửa vời.
- **Không đụng OT:** mọi dữ liệu real-time từ máy móc đều nằm ngoài phạm vi.
- Migration Flyway tiếp nối số hiện tại — **mới nhất là `V68`** (track `AR-*`, audit append-only,
  2026-09-02), phase mới bắt đầu từ `V69`. *(Dòng này từng ghi `V24` suốt từ `P1` — đã lệch 44
  migration; sửa 2026-09-25.)*

---

## 2. Đối Chiếu Tài Liệu & Bảng Tổng Quan Các Phase

### 2.0 Ma Trận Đối Chiếu Với PDF Lý Thuyết  *(kiểm chứng trên code 2026-09-25)*

> **Cách đọc:** ✅ = có đủ · 🟡 = có một phần, khoảng trống ghi rõ ở cột cuối · ❌ = chưa có ·
> ⛔ = chờ tầng OT (xem [Deferred](#phase-deferred--phụ-thuộc-ot-chưa-làm)).
>
> 🔴 **Mọi ô trong bảng này được kiểm bằng cách đọc entity/migration thật, không phải bằng cách đọc
> `CLAUDE.md`.** Đó là lý do nó tìm ra khoảng trống mà các bản roadmap trước đã tick `[x]` — xem
> "Ba khoảng trống lớn nhất" ngay dưới bảng.

#### §3 + §5 — So sánh theo module

| Yêu cầu của PDF | | Bằng chứng trong code | Khoảng trống chính xác |
|---|---|---|---|
| **BOM đa cấp + Version control** (§3.1, §5) | ✅ | `bom_headers`/`bom_lines`; `UNIQUE (company, parent_item, revision)` giữ **mọi** revision cũ, đúng một `ACTIVE`; `MrpCalculationService.expandChildren` bung đệ quy; WO chụp ảnh BOM lúc create (`B12`) | Không có **date-effectivity** (`effectiveFrom`/`To`). Không phải lỗ hổng: snapshot trên WO đã trả lời "lệnh này dùng BOM nào" — xem §4 |
| **Routing** (§3.1) | ✅ | `routings`/`routing_operations`, 1 `ACTIVE`/`(company,item)`, snapshot bất biến lên `work_order_operations` (`B49`/`B56`) | — |
| **Work Order** (§3.1, §5) | ✅ | `work_orders`, 8 trạng thái `DRAFT→PLANNED→RELEASED→IN_PROGRESS→COMPLETED→CLOSED` (+ `BLOCKED`/`CANCELLED`) | — |
| **MRP I** (§3.1, §3.4) | ✅ | `mrp_runs`/`mrp_requirement_lines`/`supply_suggestions`; netting `gross + max(safety, reorder) − (available + openWO + openPO)` | — |
| **MRP II / CRP** (§3.1, §3.4, §5) | 🟡 | `CapacityBoardService` + `ScheduleAdjustmentService` (`C2-8`): load/capacity/utilization theo `(work_center, ngày)`, cờ `overload` | **CRP là một read RIÊNG, không đóng vòng vào MRP run.** Lượt chạy MRP không bao giờ đánh dấu một đề xuất là bất khả thi về năng lực ⇒ chưa phải MRP II closed-loop. **→ `P10`** |
| **Capacity Planning** (§3.1) | 🟡 | Như trên; lịch operation sinh ở `release()`, **infinite-capacity** (không biết WO khác đang chiếm cùng work center) | Cùng khoảng trống với hàng trên. **→ `P10`** |
| **Shop Floor Control** (§3.1) | 🟡 | `production_executions`: good/scrap/rework theo **từng operation**, `actualStartedAt`/`EndedAt`, `operatorUserId`; `wip_transactions` có `work_order_operation_id` | `WorkOrderOperation` **không có cột `status`** — không có `PENDING/IN_PROGRESS/DONE`, không endpoint start/complete ⇒ không trả lời được "công đoạn 20 đang chạy, công đoạn 30 chưa bắt đầu". **→ `P8`** |
| **WIP Inventory** (§3.2) | 🟡 | `wip_transactions` (6 loại: `START`, `MATERIAL_ISSUED`, `OUTPUT_COMPLETED`, `OUTPUT_RECEIPTED`, `SCRAP_REPORTED`, `REWORK_REPORTED`); `WarehouseType.WIP` | Ledger **chỉ có `quantity`, không có cột giá trị** — xem hàng "giá trị WIP" ở §10.6 bên dưới. **→ `P7`** |
| **Kho theo lệnh sản xuất** (§3.2, §4) | ✅ | `material_issues`/`material_issue_lines` (Raw→WIP), `production_receipts`/`production_receipt_lines` (WIP→FG); `WarehouseType` = `RAW_MATERIAL`/`WIP`/`FINISHED_GOODS`/`QUALITY`/`SCRAP`/`GENERAL` | — |
| **Batch/Lot tracking** (§3.2, §3.5) | ✅ | `inventory_lots` + `Item.lotTracked`, FEFO, `LotStatus` `AVAILABLE`/`HOLD`/`REJECTED`/`EXPIRED` | — |
| **Serial tracking** (§3.2, §3.5) | 🟡 | `serial_numbers` + `Item.serialTracked` (loại trừ `lotTracked`), nối vào issue/receipt/adjust (`P5`) | **Goods Receipt chưa nối serial** — nhận NVL serial-tracked qua PO nổ `SERIAL_REQUIRED`. Nợ có chủ đích từ `P5`. **→ `P9`** |
| **Standard Costing** (§3.3, §5) | ✅ | `item_standard_costs` (material + labor + overhead), `CostingService` roll-up BOM đệ quy | Không có history theo thời gian (quyết định `P3`, xem §4) |
| **Job Costing** (§3.3) | ✅ | `work_order_cost_accumulators` — cộng dồn actual material/labor/overhead **theo từng WO** | — |
| **Process Costing** (§3.3) | ❌ | — | Không có equivalent-unit / giá thành theo kỳ-công đoạn. **Cố ý không làm** — xem §4 |
| **Variance Analysis** (§3.3, §5) | 🟡 | `WorkOrderVarianceService`: quantity variance + Material **Usage** Variance (`usageVarianceCost`) + `costVariance` (standard vs actual) | **Không có bảng `ProductionVariance`** — variance tính live mỗi lần gọi, không chốt lúc WO `CLOSED` ⇒ sửa standard cost hôm nay làm đổi variance của lệnh đã đóng năm ngoái. PDF §10.6.7 liệt kê bảng này. **→ `P7`** |
| **Traceability toàn chuỗi** (§3.5, §5, Rule 4) | 🟡 | Lot/serial được ghi ở **mọi** mắt xích: `material_issue_lines.lot_id`, `production_receipt_lines.lot_id`, `stock_movements.lot_id`, `serial_numbers` | **Không có truy vấn phả hệ nào.** Dữ liệu nối được qua `work_order_id` nhưng **không service/endpoint nào làm việc đó**: không truy ngược (lô thành phẩm → lô NVL đã tiêu) và không truy xuôi (lô NVL lỗi → những lô thành phẩm nào dính). Grep `genealogy`/`traceability` chỉ ra `traceId` của request, **không liên quan**. **→ `P9`** |

#### §10.6 — Quy trình nhập/xuất kho theo BOM

| Bước / yêu cầu | | Bằng chứng | Khoảng trống |
|---|---|---|---|
| §10.6.2-1 Tạo WO từ **MRP**/MPS | 🟡 | `SupplySuggestionService.convertToWorkOrder` | **Không có MPS** (Master Production Schedule). MRP chạy từ `planning_demands` (Sales Order + `MANUAL`/`FORECAST` nhập tay) — `FORECAST` mới chỉ là **một giá trị enum**, chưa có engine dự báo hay kế hoạch sản xuất chủ đạo theo kỳ. **→ `P11`** |
| §10.6.2-2 BOM Explosion | ✅ | `WorkOrderService.snapshotComponentLines` + `MrpCalculationService` | — |
| §10.6.2-3 Material Reservation | ✅ | `material_reservations`, FEFO, gate release phủ 100% (`P1` gate 1a) | — |
| §10.6.2-4 Material Issue (Raw→WIP) | ✅ | `MaterialIssueService` + `WipTransactionType.MATERIAL_ISSUED` | — |
| §10.6.3 Theo dõi hao hụt so với BOM | ✅ | `material_issue_lines.over_issue`/`override_reason`; `WorkOrderVarianceService` so `issued` vs `required` | — |
| §10.6.4-1 Báo cáo hoàn thành | ✅ | `ProductionExecutionService.report` (good/scrap/rework) | Nguồn là nhập tay; "từ MES" ⛔ chờ OT |
| §10.6.4-2 Tạo Production Receipt | ✅ | `ProductionReceiptService`, vòng đời `DRAFT→PENDING_APPROVAL→APPROVED` | — |
| §10.6.4-3 **Chuyển GIÁ TRỊ từ WIP → Finished Goods** | ❌ | — | Chỉ **số lượng** dịch chuyển. Không có bút toán giải phóng WIP, không có giá trị gắn lên lô thành phẩm. **→ `P7`** |
| §10.6.4-4 Cập nhật tồn kho thành phẩm | ✅ | `stock_movements` `RECEIVE` + `stock_balances` | — |
| §10.6.5 WIP — NVL đã xuất cho lệnh nào | ✅ | `material_issues.work_order_id` | — |
| §10.6.5 WIP — đang ở **công đoạn** nào | 🟡 | Suy được từ `production_executions.operation_id` / `wip_transactions.work_order_operation_id` | Chỉ biết công đoạn **đã có báo cáo**, không biết **trạng thái** công đoạn. **→ `P8`** |
| §10.6.5 WIP — hoàn thành / scrap / rework | ✅ | `work_orders.actual_good/scrap/rework_quantity` | — |
| §10.6.5 WIP — **giá trị WIP theo từng giai đoạn** | ❌ | `work_order_cost_accumulators` có `UNIQUE (work_order_id)` ⇒ **một dòng tổng/WO**, không tách theo công đoạn; `wip_transactions` không có cột tiền | Không trả lời được "đang nằm bao nhiêu tiền ở công đoạn Hàn". **→ `P7`** |
| §10.6.6 **Rule 1** — không xuất vượt tồn khả dụng ⇒ Reject | ✅ | `INSUFFICIENT_AVAILABLE_STOCK` (409) | — |
| §10.6.6 **Rule 2** — xuất vượt BOM ⇒ Warning + phê duyệt | ✅ | `PERM_MATERIAL_ISSUE_OVERRIDE` + bắt buộc **cả** `reasonCode` lẫn `overrideReason`, cờ `over_issue` lưu trên dòng | Là **phê duyệt trước bằng quyền**, không phải workflow 2 bước `PENDING_APPROVAL`. Quyết định có chủ đích của `P1` ("phương án nhẹ") |
| §10.6.6 **Rule 3** — không nhập vượt WO ⇒ Reject | ✅ | Trần `B16` + `PLANNED_QUANTITY_EXCEEDED` (409) | — |
| §10.6.6 **Rule 4** — bắt buộc Lot/Batch/Serial + traceability | 🟡 | Lot/serial ghi đủ ở mọi mắt xích | Thiếu truy vấn phả hệ + Goods Receipt chưa nối serial. **→ `P9`** |

#### §10.6.7 — "Cấu trúc Database cần bổ sung"

| Bảng PDF yêu cầu | | Bảng thật trong repo |
|---|---|---|
| `BOM_Header` & `BOM_Detail` | ✅ | `bom_headers`, `bom_lines` |
| `WorkOrder` | ✅ | `work_orders` (+ `work_order_component_lines`, `work_order_operations`) |
| `MaterialIssue` & `Detail` | ✅ | `material_issues`, `material_issue_lines` |
| `ProductionReceipt` & `Detail` | ✅ | `production_receipts`, `production_receipt_lines` |
| `WIP_Transaction` | ✅ | `wip_transactions` |
| `LotTracking` / `BatchTracking` | ✅ | `inventory_lots` (+ `serial_numbers`) |
| **`ProductionVariance`** | ❌ | **Không có** — tính live trong `WorkOrderVarianceService`. **→ `P7`** |

#### §6 — Tích hợp MES / ISA-95 / OEE

⛔ **Toàn bộ chờ tầng OT**, đã nằm đúng chỗ ở [Deferred](#phase-deferred--phụ-thuộc-ot-chưa-làm).
Kiểm lại 2026-09-25: grep `MesTransaction`/`Isa95`/`OEE`/`Downtime` trong `src/main` ra **0** kết quả
nghiệp vụ (chỉ một dòng javadoc nhắc tên OEE). Không có gì bị làm dở dang — đúng như kế hoạch.

#### §8 — Khuyến nghị triển khai của PDF

| Khuyến nghị | | Ghi chú |
|---|---|---|
| 1. Xác định loại hình khách hàng | ✅ | Không phải hạng mục code |
| 2. GĐ1 — kho mở rộng được (`IsManufacturing = true`) | ✅ **vượt yêu cầu** | Repo dùng `WarehouseType` **6 giá trị** thay vì một cờ boolean — phân biệt được kho NVL / WIP / thành phẩm / QC / phế liệu |
| 3. GĐ2 — BOM + Work Order + MRP | ✅ | `P1`–`P6` |
| 4. GĐ3 — Tích hợp MES (ISA-95) | ⛔ | Deferred |
| 5. Làm ngay sau Purchase Receipt: Material Issue + Production Receipt | ✅ | Cả hai xong từ trước `P1` |

### 2.0.1 Ba Khoảng Trống Lớn Nhất Còn Lại

> Xếp theo khoảng cách so với lý thuyết, không theo effort.

1. 🔴 **Dòng chảy GIÁ TRỊ dừng ở Work Order, không đi qua WIP** (`P7`). Repo trả lời được *"lệnh này
   tốn bao nhiêu"* nhưng **không** trả lời được *"đang có bao nhiêu tiền nằm trong xưởng, ở công đoạn
   nào"* — đúng hai câu PDF nhấn ở §10.6.4-3 và §10.6.5. Nguyên nhân cụ thể đã kiểm:
   `work_order_cost_accumulators` có `UNIQUE (work_order_id)` ⇒ **một dòng tổng cho mỗi WO**, và
   `wip_transactions` **không có cột tiền nào**. Đây là khoảng trống lý thuyết lớn nhất còn lại sau
   khi `P3` đóng.
2. 🔴 **Traceability có dữ liệu nhưng không có đường đọc** (`P9`). Lot/serial được ghi ở mọi mắt
   xích, nhưng không truy vấn nào nối chúng lại. Hệ quả nghiệp vụ thật: khi một lô NVL bị phát hiện
   lỗi, **không có cách nào liệt kê những lô thành phẩm đã dùng nó** ⇒ không thu hồi có mục tiêu
   được, đúng thứ Rule 4 tồn tại để phục vụ.
3. 🔴 **Công đoạn không có trạng thái** (`P8`). Thiết kế gốc của `P4` trong chính file này ghi rõ
   *"mỗi dòng status `PENDING/IN_PROGRESS/DONE`"* nhưng phần đó **chưa bao giờ được implement**, mà
   `P4` vẫn được tick `[x]`. Đây là lần thứ tư repo mắc lỗi "tuyên bố đóng rộng hơn phạm vi đã rà"
   (ba lần trước: `CLAUDE.md §0.20`) ⇒ khi tick một phase, ghi rõ **đã làm tới đâu**, đừng ghi "xong".

---

### 2.A Bảng Tổng Quan Các Phase

| ✔ | Phase | Tên | Nhóm giá trị | Phụ thuộc | Effort | Cần OT? |
|---|---|---|---|---|---|---|
| **[x]** | **P1** | Approval Workflow & Business Gates | Khớp `business_flow` (4 chốt duyệt) | — | Trung bình | Không |
| **[x]** | **P2** | Quality Control (QC) Module | Khớp `business_flow` (QC + HOLD) | P1 ✅ | Trung bình | Không |
| **[x]** | **P3** | Costing Engine | Giá thành chuẩn + Job Costing + Usage Variance | — | Cao | Không |
| **[x]** | **P4** | Routing + Work Center + CRP tĩnh + Labor Time | Master Data + Capacity Check | — | Cao | Không |
| **[x]** | **P5** | Serial Number Tracking | Traceability cấp đơn vị | — | Trung bình–Cao | Không |
| **[x]** | **P6** | Sales Order & Fulfillment + WO Close | Đóng vòng end-to-end | P2 ✅ | Cao | Không |
| **[ ]** | **P7** | **WIP Valuation, Cost Flow & Variance Snapshot** | Đóng khoảng trống lý thuyết lớn nhất còn lại | P3 ✅, P4 ✅ | Cao | Không |
| **[ ]** | **P8** | **Shop Floor Control cấp công đoạn** | Trả lời "đang ở công đoạn nào" | P4 ✅ | Trung bình | Không |
| **[ ]** | **P9** | **Traceability toàn chuỗi (Genealogy)** | Rule 4 — thu hồi có mục tiêu | P5 ✅ | Trung bình | Không |
| **[ ]** | **P10** | **CRP đóng vòng vào MRP run (MRP II)** | MRP II closed-loop | P4 ✅ | Trung bình | Không |
| **[ ]** | **P11** | **MPS (Master Production Schedule)** | Nguồn thứ hai cho MRP | — | Cao | Không |
| **[ ]** | **P-Deferred** | MES/ISA-95, OEE, WIP real-time, CRP động | Industry 4.0 | Tầng OT | — | **Có** |

> `P6`: Sales Order (`F3`) + Fulfillment allocation (`F6`) ✅ **xong 2026-07-28**. **WO Close/reconcile**
> (status `CLOSED`, khoá hoàn toàn, do manager qua `POST /work-orders/{id}/close`) ✅ **xong
> 2026-08-06** — bất biến `B100`, migration `V53`. Bản ghi đầy đủ: `CLAUDE.md §0.34`.
>
> 🔴 **`P7`–`P11` thêm 2026-09-25**, sinh ra từ lượt đối chiếu PDF ở §2.0 — **không** phải ý tưởng mới,
> mà là những yêu cầu PDF nêu tường minh nhưng code chưa có. Ba cái đầu là ba khoảng trống ở §2.0.1;
> `P10`/`P11` là hai mức tinh chỉnh của vòng hoạch định. Trước khi bắt tay `P7`, đọc lại §2.0 để biết
> **chính xác** ô nào của PDF đang trống — đừng làm theo trí nhớ về "costing đã xong ở `P3`".

### 2.1 Bảng Theo Dõi Tiến Độ  *(cập nhật: 2026-09-25)*

> **Quy tắc cập nhật:** phase chỉ được tick `[x]` khi **toàn bộ** Definition of Done của phase đó
> đã đạt và `mvn -o test` PASS. Khi tick xong một phase ⇒ ghi prompt của phase kế tiếp vào
> `NEXT_PHASE_PLAN.md` (file đó luôn chỉ chứa **đúng một** phase đang chạy).

> ⚠️ **Cập nhật 2026-07-26 — track `P*` đang được thực thi qua track `F*`.** Sau khi đối chiếu với
> `OmniPlant_MVP_Production_Backend_Handoff.docx` (đặc tả FE↔BE), thứ tự phase được sắp lại theo
> dependency của spec: `F2`≈`P2` (QC), `F4`≈`P4` (Routing, chỉ phần spec cần), `F3`+`F6`≈`P6`
> (Sales Order tách làm demand và fulfillment). `P3` (Costing) và `P5` (Serial) **không** nằm trong
> MVP của spec. Nguồn sự thật về phase đang chạy: `CLAUDE.md §0.1` + `NEXT_PHASE_PLAN.md`.

**Track nghiệp vụ (`P*` – file này):**

- [x] **P1 – Approval Workflow & Business Gates** — hoàn thành 2026-07-24
  - [x] Gate 1a: release yêu cầu reservation phủ 100%, thiếu ⇒ WO `BLOCKED` + 422
  - [x] Gate 1b: over-issue cần `PERM_MATERIAL_ISSUE_OVERRIDE` + `overrideReason`
  - [x] Gate 1c: receipt `post` → `PENDING_APPROVAL` → `approve` → `POSTED` + lot `HOLD`
  - [x] Migration `V23__create_work_order_approval_gates.sql`, `V24__seed_approval_gate_permissions.sql`
  - [x] Docs cập nhật: `architecture.md`, `system-flow.md`, `roles-and-permissions.md`
  - Chi tiết + 2 giới hạn đã biết: `CLAUDE.md §0.3`
- [x] **P2 – Quality Control (QC) Module** — hoàn thành 2026-07-26 (thực thi dưới tên `F2`)
  - [x] Vòng đời receipt `DRAFT` → `PENDING_APPROVAL` → `APPROVED` (+ `submit` endpoint); `POSTED` đổi tên thành `APPROVED`
  - [x] `POST /production-receipts/{id}/qc-disposition` → lot `HOLD` sang `AVAILABLE`/`REJECTED`, reason bắt buộc
  - [x] Entity `QualityDisposition` (per-lot) + summary `qcResult`/`qcReason`/`qcAt`/`qcBy` trên receipt
  - [x] `MovementType.LOT_STATUS_CHANGE` + `MovementDirection.NONE` (ledger truy xuất, không đổi balance)
  - [x] Permission `PERM_QUALITY_DISPOSITION` (`V27`), chỉ ADMIN/MANAGER — separation of duties
  - [x] Migration `V26__create_quality_control.sql`, `V27__seed_quality_permissions.sql`
  - [x] Docs cập nhật: `CLAUDE.md §0.6`, `roles-and-permissions.md`, `system-flow.md`, `module/{workorder,inventory}/CLAUDE.md`
  - [x] **[`D5`, 2026-07-30]** QC mở cho output **không** lot-tracked: điều kiện rẽ nhánh là cấp dòng
    (`line.lot != null`), phán quyết ghi trên receipt, `REJECTED` rút hàng bằng `ADJUST_OUT`. Trả nợ
    #17 — trước đó thành phẩm không lot-tracked không bao giờ lên `FULFILLED`. Không migration.
    Chi tiết: `CLAUDE.md §0.13`
  - Chi tiết + giới hạn còn lại: `CLAUDE.md §0.6` và nợ #11/#12 ở `§0.4`
  - *Khác thiết kế gốc:* entity tên `QualityDisposition` (không phải `QualityInspection`), permission
    tên `PERM_QUALITY_DISPOSITION` (không phải `PERM_QUALITY_INSPECT`) — theo đúng đặc tả FE §6.2.
- [x] **P3 – Costing Engine** — ✅ **xong 2026-08-05**. Module mới `module/costing`: `ItemStandardCost`
  (upsert, company-scoped) + `CostingService` (BOM cost roll-up đệ quy, cùng khuôn
  `MrpCalculationService`) + `WorkOrderCostAccumulator` (2 hook: `MaterialIssueService`,
  `ProductionExecutionService`). `GET /work-orders/{id}/variance` mở rộng `usageVarianceCost` +
  `costVariance`. **Chỉ Material Usage Variance** — không làm Price Variance (không có cột giá trên
  `stock_movements`). `laborCost`/`overheadCost` nhập tay theo rate cố định, **không** tính từ
  `WorkOrderOperation.runMinutesPerUnit`. Permission `PERM_COSTING_READ`/`_MANAGE` (ADMIN+MANAGER
  only). Bản ghi đầy đủ: `CLAUDE.md §0.31`.
- [x] **P4 – Routing + Work Center + CRP tĩnh** — ✅ **đóng nốt 2026-08-05**. Routing master data xong 2026-07-27 (`F4`); Work Center/Shift/Calendar/CRP thực thi dưới mã `C2-6`/`C2-7`/`C2-8` (track Capstone 2, xem `FRONTEND_ALIGNMENT_ROADMAP.md §8`)
  - [x] Module `module/routing`: `RoutingHeader` + `RoutingOperation`, 1 routing `ACTIVE` / `(company, item)`
  - [x] Snapshot bất biến lên `work_orders` (`source_routing_id/code/version` + `routing_captured_at`)
  - [x] `MISSING_ROUTING` (409) chặn convert proposal MAKE thiếu routing `ACTIVE`
  - [x] `PERM_ROUTING_READ` / `PERM_ROUTING_MANAGE` (`V31`), docs cập nhật
  - [x] `WorkCenter` entity + CRUD/lifecycle, `RoutingOperation.workCenterCode` (string) → FK — **xong 2026-08-05 dưới tên `C2-6`** (per-plant, `capacityUnitType`+`capacityUnits` thay `capacityHoursPerDay`, xem `module/workcenter/CLAUDE.md`)
  - [x] `Shift` + `WorkCalendar` entity + CRUD/lifecycle + `WorkCenter.workCalendarId` FK — **xong 2026-08-05 dưới tên `C2-7`** (net working window nội bộ, chưa endpoint public — xem `module/shift/CLAUDE.md`)
  - [x] `work_order_operations` snapshot + `stage_code` → FK — **xong 2026-07-27 dưới tên `F5-A`** (bảng snapshot copy, bất biến B56; `wip_transactions.work_order_operation_id` thêm cạnh `stage_code`, không bỏ cột cũ vì WO không routing vẫn cần free text)
  - [x] CRP tĩnh (`CapacityBoardService` + `ScheduleAdjustmentService`) + Capacity Board — **xong 2026-08-05 dưới mã `C2-8`** (lịch operation sinh ở `WorkOrderService.release()`, infinite-capacity; Labor **cost** theo operation time vẫn thuộc `P3`, xem `CLAUDE.md §0.30`)
  - [x] Routing `ACTIVE` được kiểm ngay ở tầng MRP: proposal MAKE thiếu routing ⇒ `exceptionState = BLOCKED`
        + message `MISSING_ROUTING`, không đợi đến lúc convert — **xong 2026-07-28 dưới tên `F5-B`**
  - *Khác thiết kế gốc:* field entity là `routingVersion` (không phải `revision`) vì `BaseEntity` đã
    chiếm `version`; operations nhập inline khi create thay vì endpoint line riêng như BOM.
- [x] **P5 – Serial Number Tracking** — hoàn thành 2026-08-06
  - [x] `SerialNumber` entity (mirror `InventoryLot`, mọi movement chạm serial luôn `quantity = 1`) +
        `Item.serialTracked` loại trừ `lotTracked` (service + CHECK constraint, chốt với user)
  - [x] Nối vào `InventoryMovementService` (receive/issue/adjust), `MaterialIssueService`,
        `ProductionReceiptService` (post/approve/qcDisposition) — migration `V52`
  - [x] Quyết định chốt với user: serial-tracked output **không** HOLD chờ QC (mirror non-lot-tracked
        của `D5`, tránh phải viết lại `StockBalanceRepository.aggregate*`)
  - [ ] Goods Receipt (`module/purchasing`) — **chưa nối**, nợ có chủ đích, xem `module/purchasing/CLAUDE.md`
  - Chi tiết + bất biến B96-B99: `CLAUDE.md §0.33`
- [x] **P6 – Sales Order & Fulfillment + WO Close** — *`F3` 2026-07-26, `F6` 2026-07-28, WO Close 2026-08-06*
  - [x] **[F6]** `WorkOrderDemandAllocation` (`V35`) — nối WO ↔ SO line lúc convert proposal MAKE level-0
  - [x] **[F6]** Fulfillment chỉ chạy khi QC `AVAILABLE` commit (spec §7.1); `REJECTED`/`approve` không chạm
  - [x] **[F6]** Roll-up `IN_PRODUCTION` → `PARTIALLY_FULFILLED` → `FULFILLED`; `WorkOrderResponse.allocations[]`
  - [x] **WO Close/reconcile** (node 25, 2026-08-06): `WorkOrderStatus.CLOSED` chỉ vào từ `COMPLETED`, khoá
        hoàn toàn (chốt với user); `close()` cũng giải phóng reservation `ACTIVE` còn sót (bước
        "Reconcile"). Migration `V53`, bất biến `B100`. Bug thật phát hiện + sửa cùng phase: `canReserve()`
        là danh sách phủ định, thiếu loại trừ `CLOSED`. Chi tiết: `CLAUDE.md §0.34`
  - [x] Module `module/sales`: `SalesOrder` + `SalesOrderLine`, 6 trạng thái, `lineNo` do server cấp
  - [x] `confirm` ⇒ sinh independent demand (`PlanningDemand`, `demandType = SALES_ORDER`); `cancel` ⇒ huỷ demand `OPEN`
  - [x] `GET /sales-orders/planning-demands` — 4 điều kiện eligibility spec §2.1 trong 1 aggregate query
  - [x] Permission `PERM_SALES_ORDER_READ` / `PERM_SALES_ORDER_MANAGE` (`V29`), OPERATOR chỉ READ
  - [x] Migration `V28__create_sales_order.sql`, `V29__seed_sales_permissions.sql`
  - [x] Docs: `CLAUDE.md §0.7`, `module/sales/CLAUDE.md` (B43-B47), `roles-and-permissions.md`, `system-flow.md`
  - [ ] **Nửa sau (`F6`)**: fulfillment allocation (`fulfilledQuantity` tăng khi QC `AVAILABLE`), WO Close
- [ ] **P7 – WIP Valuation, Cost Flow & Variance Snapshot** — *thêm 2026-09-25 từ đối chiếu PDF §10.6.4-3,
      §10.6.5, §10.6.7*
  - [ ] Giá trị WIP **theo công đoạn**: hiện `work_order_cost_accumulators` là `UNIQUE (work_order_id)` ⇒
        một dòng tổng/WO; `wip_transactions` không có cột tiền
  - [ ] Giải phóng WIP → Finished Goods khi `approve` receipt (hiện chỉ số lượng chuyển, giá trị không)
  - [ ] Bảng `production_variances` — chốt variance lúc WO `CLOSED` (PDF §10.6.7 liệt kê; hiện tính live)
- [ ] **P8 – Shop Floor Control cấp công đoạn** — *thêm 2026-09-25 từ PDF §3.1, §10.6.5*
  - [ ] `work_order_operations.status` (`PENDING`/`IN_PROGRESS`/`COMPLETED`) + endpoint start/complete
  - [ ] 🔴 Chính thiết kế gốc của `P4` (§3 file này) đã ghi mục này nhưng **chưa bao giờ implement**
- [ ] **P9 – Traceability toàn chuỗi (Genealogy)** — *thêm 2026-09-25 từ PDF §3.5, Rule 4*
  - [ ] Truy ngược: lô/serial thành phẩm → WO → `material_issue_lines` → lô/serial NVL đã tiêu
  - [ ] Truy xuôi (thu hồi): lô NVL → những WO đã dùng → những lô thành phẩm dính
  - [ ] Nối serial vào Goods Receipt — nợ có chủ đích còn lại của `P5`
- [ ] **P10 – CRP đóng vòng vào MRP run** — *thêm 2026-09-25 từ PDF §3.1, §3.4, §5*
  - [ ] MRP run đánh giá năng lực và gắn `exceptionState`/message cho đề xuất bất khả thi
  - [ ] Hiện `CapacityBoardService` là một read **riêng**, không đụng MRP
- [ ] **P11 – MPS (Master Production Schedule)** — *thêm 2026-09-25 từ PDF §10.6.2-1*
  - [ ] Kế hoạch sản xuất chủ đạo theo kỳ cho thành phẩm, làm nguồn demand thứ hai cho MRP
  - [ ] Hiện `PlanningDemandType.FORECAST` mới chỉ là **một giá trị enum**, không có engine nào đứng sau
- [ ] **P-Deferred** — chờ tầng OT, không lên lịch

**Track kiểm thử (`T*` – chi tiết ở `TEST_IMPROVEMENT_PLAN.md`):**

- [x] **T0 – JaCoCo & coverage baseline** (line 62.3% / branch 48.9%)
- [x] **T1 – Method-security hardening** — hoàn thành (218 case)
- [x] **T2 – Controller / GlobalExceptionHandler contract** — hoàn thành 2026-07-25 (281 case; 3/18 controller đại diện, xem `TEST_IMPROVEMENT_PLAN.md §0`)
- [x] **T3 – Auth core** (`refresh`/`logout`, `TokenStoreService`, `JwtTokenProvider`) — hoàn thành 2026-07-25 (257 case)
- [x] **T4 – Testcontainers + JPQL RBAC + migration** — hoàn thành 2026-07-25
- [x] **T5 – Dọn over-mocking, tên test sai** — hoàn thành 2026-07-25 (281 case)

> **Vì sao `P2` bị hoãn:** `P2` thêm `PERM_QUALITY_*` vào đúng vùng mà `T1` đang vá.
> Làm `P2` trước ⇒ permission mới cũng bị "test" bằng pattern vô hiệu. Xong `T1` rồi mới mở `P2`.

> **Thứ tự khuyến nghị:** P1 → P2 trước (rẻ, khớp trực tiếp sơ đồ mới nhất, mở khóa QC).
> P3–P6 **có thể hoán đổi thứ tự theo ưu tiên nghiệp vụ** — nếu ưu tiên báo cáo giá thành thì
> kéo **P3 (Costing) lên làm trước**, vì nó standalone và là điểm phân biệt cốt lõi
> Manufacturing vs Commercial ERP.

### Sơ đồ phụ thuộc

```text
── Đã xong ─────────────────────────────────────────────────────────────
P1 (Gates/Approval) ──► P2 (QC) ──► P6 (Sales Order & Close)
P3 (Costing)        ── độc lập
P4 (Routing/CRP)    ── độc lập  (unlock: stage_code có nghĩa, Labor cost cho P3)
P5 (Serial)         ── độc lập

── Còn lại (thêm 2026-09-25) ───────────────────────────────────────────
P3 ✅ ─┬─► P7 (WIP Valuation + Variance snapshot)
P4 ✅ ─┘      ▲
              └── P8 (Operation status) làm P7 chia được giá trị theo công đoạn
P4 ✅ ────► P8 (Shop Floor Control cấp công đoạn)
P4 ✅ ────► P10 (CRP đóng vòng vào MRP run)
P5 ✅ ────► P9 (Genealogy + serial cho Goods Receipt)
            P11 (MPS) ── độc lập
```

> **`P8` không chặn `P7`, nhưng làm `P7` tốt hơn hẳn.** Không có trạng thái công đoạn thì `P7` chỉ
> chia được giá trị WIP theo *công đoạn đã có báo cáo*, không theo *công đoạn đang chạy*. Làm `P8`
> trước là rẻ hơn làm `P7` hai lần.

---

## 3. Chi Tiết Từng Phase

### P1 — Approval Workflow & Business Gates  ✅ **ĐÃ HOÀN THÀNH (2026-07-24)**

> Kết quả thực tế: 3 gate hoạt động, migration `V23`+`V24`, `WorkOrderStatus` có thêm `BLOCKED`,
> `ProductionReceiptStatus` có thêm `PENDING_APPROVAL`/`REJECTED`.
> **Khác thiết kế ban đầu:** không thêm status `PLANNED` (trùng nghĩa `DRAFT`) và chọn
> **phương án nhẹ** cho gate 1b (permission override, không thêm `PENDING_APPROVAL` cho material issue).
> Hai giới hạn đã biết ảnh hưởng trực tiếp `P2`: xem `CLAUDE.md §0.3`.

**Mục tiêu:** Bổ sung 3 chốt kiểm soát trong `business_flow.jpg` mà code hiện tại chưa có.
Hiện hệ thống **chưa có bất kỳ approval-workflow nào**, nên phase này cũng đặt nền cho cơ chế
duyệt dùng lại về sau.

| # | Gate (theo `business_flow`) | Hiện trạng | Việc cần làm |
|---|---|---|---|
| 1a | **Reserve đủ 100% mới cho Release** (node 12) | `release()` chỉ check component lines không rỗng | Thêm status `PLANNED`/`BLOCKED` vào `WorkOrderStatus`; `WorkOrderService.release()` tính tỷ lệ reserved theo từng component line, chưa đủ → chặn / giữ `BLOCKED` |
| 1b | **Xuất vượt BOM → duyệt Material Variance** (node 15) | `support.ensureDoesNotExceed()` ném exception, chặn cứng | **Phương án nhẹ (khuyến nghị):** thêm permission `PERM_MATERIAL_ISSUE_OVERRIDE`; nếu có quyền + có `overrideReason` thì cho vượt, ghi audit mức WARNING. **Phương án đầy đủ (tùy chọn):** thêm status `PENDING_APPROVAL` + endpoint `approve` |
| 1c | **Duyệt Production Receipt → output vào HOLD** (node 22) | Receipt post thẳng `POSTED`, lot ra `AVAILABLE` ngay | Thêm bước duyệt; khi duyệt, output lot tạo ở `LotStatus.HOLD` thay vì `AVAILABLE` (mở khóa cho P2 QC) |

**Thay đổi chính:**
- `WorkOrderStatus`: thêm `PLANNED`, `BLOCKED` (+ cập nhật `chk_work_orders_status`).
- `MaterialIssueStatus` / `ProductionReceiptStatus`: cân nhắc `PENDING_APPROVAL` (nếu chọn phương án đầy đủ).
- `InventoryMovementService.resolveReceiveLot()`: cho phép chỉ định lot status khởi tạo (`HOLD` cho output sản xuất).
- Seed permission mới cho các quyền override/approve.

**Definition of Done:** 3 gate hoạt động; test cho từng nhánh (đủ/thiếu reservation, vượt/không vượt BOM, duyệt/từ chối receipt); build + test PASS.

**Effort:** Trung bình. **OT:** Không.

---

### P2 — Quality Control (QC) Module

**Mục tiêu:** Thêm bước QC sau Production Receipt như node 23 trong `business_flow`:
output `HOLD` → (Accept) `AVAILABLE` / (Reject) `REJECTED`.

**Thuận lợi:** `LotStatus` **đã có sẵn** đủ giá trị (`AVAILABLE`, `HOLD`, `REJECTED`, `EXPIRED`) —
chỉ thiếu workflow điều khiển transition.

**Thay đổi chính:**
- Entity mới `QualityInspection`: `inspectionId`, tham chiếu `production_receipt_line` / `lot`,
  `result` (ACCEPTED/REJECTED), `inspectedBy`, `note`, `inspectedAt`.
- Migration `V2x__create_quality_control.sql`.
- `QualityControlService`: endpoint `accept` → lot sang `AVAILABLE`; `reject` → lot sang `REJECTED`
  (không cho bán/không cộng available).
- Permission `PERM_QUALITY_INSPECT`.

**Phụ thuộc:** P1 (1c) — output phải nằm ở `HOLD` trước thì QC mới có gì để duyệt.

**Definition of Done:** post receipt → lot HOLD → QC accept/reject chuyển đúng trạng thái, phản ánh đúng available quantity; test đủ 2 nhánh; build + test PASS.

**Effort:** Trung bình. **OT:** Không.

---

### P3 — Costing Engine ⭐ (khoảng trống lý thuyết lớn nhất **tại thời điểm 2026-07**)

> ✅ **Đã xong (2026-08-05).** Phần dưới đây là **thiết kế phác thảo gốc**, giữ lại làm lịch sử —
> triển khai thật lệch vài chỗ, đã chốt với user trước khi viết kế hoạch chi tiết:
> - `ItemStandardCost` **không** có `effectiveDate`/version-theo-thời-gian — upsert một dòng/item
>   (không có "cost tại một thời điểm trong quá khứ" nào cần dùng lúc này, thêm sẽ là speculative).
>   Company-scoped, **không** per-plant như bản nháp gợi ý.
> - **Bỏ hẳn Material Price Variance**, chỉ làm Material Usage Variance — `stock_movements` không có
>   cột giá, và giá duy nhất trong hệ thống (`PurchaseOrderLine.unitPrice`) tách rời khỏi ledger xuất
>   kho.
> - `WorkOrderCostAccumulator` cộng dồn material cost ở `MaterialIssueService.postNew` (đúng như bản
>   nháp) nhưng cộng dồn labor/overhead ở `ProductionExecutionService.reportNew` (báo sản lượng),
>   **không phải** lúc `ProductionReceiptService` hoàn tất — khớp với đảo ngược ngữ nghĩa `F5`
>   (`CLAUDE.md §0.5`): chính execution làm WO tiến triển, không phải receipt.
>
> Bản ghi đầy đủ (thiết kế thật, bất biến B91-B94, breaking changes): `CLAUDE.md §0.31`.

**Mục tiêu:** Trả lời được câu hỏi *"sản xuất lô này tốn bao nhiêu tiền"* — hiện tại `Item` không
có field chi phí nào, `WorkOrderVarianceService` chỉ so sánh **số lượng**, chưa có tiền.

**Thay đổi chính:**
1. **Entity `ItemStandardCost`** (tách khỏi `Item` vì cost theo company/plant + có hiệu lực theo
   thời gian): `materialCost`, `laborCost`, `overheadCost`, `effectiveDate`, `version`.
2. **BOM cost roll-up** — `CostingService.calculateStandardCost(item)` đệ quy y hệt cách
   `MrpCalculationService` bung BOM:
   `cost(FG) = Σ(component.standardCost × quantityPer × (1 + scrapRate)) + labor + overhead của chính FG`.
3. **Actual costing** — `WorkOrderCostAccumulator` (bảng mới, 1-1 với `WorkOrder`): mỗi lần
   `MaterialIssueService` post → cộng dồn `issuedQuantity × standardCost`. Khi
   `ProductionReceiptService` hoàn tất → chốt actual cost / đơn vị.
4. **Variance theo tiền** — mở rộng `WorkOrderVarianceService` (đang có variance số lượng) để thêm
   Material Price Variance & Material Usage Variance, tái dùng `issuedQuantity`/`requiredQuantity`
   nhân với cost.

**Ghi chú:** `laborCost`/`overheadCost` giai đoạn này có thể là **rate phẳng khai báo tay** trên
`ItemStandardCost`; khi có P4 (Routing) thì có thể tính labor chính xác hơn theo operation time —
nhưng **P3 không phụ thuộc P4**, làm trước được.

**Definition of Done:** roll-up standard cost cho BOM đa cấp đúng; WO tích lũy actual cost; báo cáo
variance có cột tiền tệ; test tính toán chi phí + variance; build + test PASS.

**Effort:** Cao. **OT:** Không. Tái dùng nhiều nhất hạ tầng (BOM explosion + Variance service).

---

### P4 — Routing + Work Center + CRP Tĩnh + Labor Time

> ✅ **Đã xong hết (2026-08-05, dưới mã `C2-6`/`C2-7`/`C2-8`).** Phần dưới đây là **thiết kế phác thảo
> gốc**, giữ lại làm lịch sử — triển khai thật lệch vài chỗ: `WorkCenter.capacityHoursPerDay` →
> `capacityUnitType`+`capacityUnits`; `CapacityCalculationService` chạy trong `MrpRunService` → tách
> thành `CapacityBoardService` (read riêng, không đụng MRP) + `ScheduleAdjustmentService`; đánh dấu
> `OVER_CAPACITY` ở MRP → cờ `overload` trên Capacity Board (một read độc lập, không phải nhánh của
> MRP run). Bản ghi thật: `CLAUDE.md §0.28`-`§0.30`, `FRONTEND_ALIGNMENT_ROADMAP.md §8.6`-`§8.7b`.

**Mục tiêu:** Bổ sung Master Data còn thiếu (`Routing`, `Work Center` — node 2) và Capacity Check
tĩnh (node 5/6/7), làm cho `wip_transactions.stage_code` thực sự có nghĩa.

**Thay đổi chính:**
- **Master data mới** (theo pattern `BomHeader`/`BomLine`):
  - `WorkCenter`: `workCenterId`, `plant`, `code`, `name`, `capacityHoursPerDay`, `status`.
  - `Routing`: giống `BomHeader` — `item`, `revision`, `status` (ACTIVE/DRAFT).
  - `RoutingOperation`: `routing`, `operationNo`, `workCenter`, `standardSetupTime`,
    `standardRunTimePerUnit`, `sequence`.
- **Snapshot khi release:** `WorkOrder.release()` snapshot thêm `WorkOrderOperationLine` (giống
  `snapshotComponentLines()` snapshot từ `BomLine`), mỗi dòng status `PENDING/IN_PROGRESS/DONE`.
- **`stage_code` → FK** trỏ tới `WorkOrderOperationLine` thay vì `VARCHAR` tự do.
- **Labor time** (node 19): ghi nhận thời gian thực hiện theo operation (nhập tay, không cần OT).
- **CRP tĩnh** — `CapacityCalculationService` chạy song song `MrpCalculationService` trong
  `MrpRunService`: `Σ(standardRunTimePerUnit × plannedQuantity)` theo `WorkCenter` theo ngày/tuần,
  so với `capacityHoursPerDay × số ngày làm việc` → đánh dấu `OVER_CAPACITY` (giống cách
  `MrpRequirementStatus` đánh dấu COVERED/thiếu).

**Lưu ý phân biệt với OT:** đây là **CRP tĩnh** — chỉ so *kế hoạch* với *công suất khai báo sẵn*,
không đọc trạng thái máy real-time. CRP động (theo tải máy thực) thuộc phần Deferred.

**Definition of Done:** tạo được Routing/WorkCenter; WO snapshot operation lines; CRP report cảnh
báo over-capacity; test roll-up giờ tải; build + test PASS.

**Effort:** Cao. **OT:** Không (chỉ CRP tĩnh).

---

### P5 — Serial Number Tracking

> ✅ **Đã xong (2026-08-06).** Phần dưới đây là **thiết kế phác thảo gốc**, giữ lại làm lịch sử —
> triển khai thật lệch một chỗ quan trọng, đã chốt với user trước khi viết kế hoạch chi tiết:
> `MaterialIssueLineRequest` **không** đổi sang nhận danh sách `serialId` như phác thảo dưới đây dự
> đoán — mỗi movement chạm serial luôn `quantity = 1` nên chỉ cần thêm field optional `serialId`, và
> N đơn vị dùng N dòng trong `lines[]` đã có sẵn (**không phải breaking change**). Quyết định thứ hai
> chốt với user: serial-tracked output **không** có HOLD chờ QC (mirror non-lot-tracked của `D5`),
> tránh phải thêm `serial_id` vào `stock_balances` + viết lại `StockBalanceRepository.aggregate*`.
> Chi tiết đầy đủ + bất biến B96-B99: `CLAUDE.md §0.33`.

**Mục tiêu:** Truy vết cấp **từng đơn vị** (Rule 4 – Serial), bổ sung bên cạnh Lot đã có.

**Thay đổi chính:**
- Entity mới `SerialNumber` (song song `InventoryLot`): `serialId`, `item`, `serialCode` (unique),
  `status` (AVAILABLE/ISSUED/SOLD…).
- Thêm `serialId` (nullable FK) vào `stock_movements`, `material_issue_lines`,
  `production_receipt_lines` — đúng pattern `lot_id`.
- Thêm cờ `Item.serialTracked` song song `lotTracked`.

**Điểm khó (không chỉ là thêm cột):** Lot theo lô (nhiều đơn vị / 1 mã), Serial theo từng đơn vị
(1 sản phẩm = 1 mã). Khi `ProductionReceipt` nhận 100 sản phẩm serial-tracked → phải sinh **100
record Serial**. `MaterialIssueLineRequest` hiện nhận 1 `quantity` (BigDecimal) — với serial phải
đổi sang nhận **danh sách `serialId`**, là thay đổi API/DTO đáng kể.

**Definition of Done:** item serial-tracked; receipt sinh serial theo đơn vị; issue theo danh sách
serial; test; build + test PASS.

**Effort:** Trung bình–Cao. **OT:** Không.

---

### P6 — Sales Order & Fulfillment + WO Close/Reconcile

> ✅ **Đã xong (2026-08-06).** Phần dưới đây là **thiết kế phác thảo gốc** cho "WO Close/reconcile"
> (Sales Order + Fulfillment đã xong sớm hơn ở `F3`/`F6`), giữ lại làm lịch sử — triển khai thật khớp
> phác thảo, chỉ thêm một chi tiết chốt với user trước khi viết kế hoạch chi tiết: `CLOSED` **khoá
> hoàn toàn**, không có carve-out đọc/ghi nào (phương án đơn giản hơn trong hai phương án được hỏi).
> "Bước đối chiếu tường minh" ở dòng dưới chính là `close()` gọi lại
> `materialReservationService.cancelActiveReservations(...)` — giải phóng reservation `ACTIVE` còn sót
> về lại tồn khả dụng trước khi khoá vĩnh viễn. Migration `V53`, bất biến `B100`. Bản ghi đầy đủ:
> `CLAUDE.md §0.34`.

**Mục tiêu:** Đóng vòng end-to-end như đầu và cuối `business_flow` (node 3, 10, 24, 25). Hiện chỉ
có `PlanningDemand` với enum type `SALES_ORDER` — chưa có entity SO thật.

**Thay đổi chính:**
- Module `sales` mới: `Customer`, `SalesOrder`, `SalesOrderLine` (item, quantity, dueDate, fulfilledQuantity).
- **SO → demand:** SO sinh `PlanningDemand` (type `SALES_ORDER`) làm đầu vào MRP.
- **SO ↔ WO allocation** (node 10): liên kết nhu cầu SO với Work Order phục vụ nó.
- **Fulfillment update** (node 24): khi output QC-accepted (`AVAILABLE`) → cập nhật
  `fulfilledQuantity` của SO line.
- **WO Close/reconcile** (node 25): thêm bước đối chiếu tường minh + status `CLOSED` (hiện chỉ có
  auto-`COMPLETED` theo số lượng, không có bước close do manager).

**Phụ thuộc:** P2 (QC) — fulfillment chỉ tính output đã QC-accepted.

**Definition of Done:** tạo SO → MRP nhận demand → WO phục vụ SO → output accepted cập nhật
fulfillment → WO close khi reconcile đủ; test luồng; build + test PASS.

**Effort:** Cao. **OT:** Không.

---

### P7 — WIP Valuation, Cost Flow & Variance Snapshot ⭐ *(khoảng trống lý thuyết lớn nhất còn lại)*

> Thêm 2026-09-25 từ đối chiếu PDF §10.6.4-3, §10.6.5, §10.6.7. **Không** trùng `P3`: `P3` trả lời
> *"lệnh này tốn bao nhiêu"*, `P7` trả lời *"đang có bao nhiêu tiền nằm trong xưởng, ở đâu"*.

**Hiện trạng đã kiểm trên schema (không phải suy từ tài liệu):**

| Sự thật đo được | Hệ quả |
|---|---|
| `work_order_cost_accumulators` có `CONSTRAINT uk_work_order_cost_accumulators_work_order UNIQUE (work_order_id)` | **Đúng một dòng tổng cho mỗi WO** — không có chiều công đoạn để chia giá trị |
| `wip_transactions` chỉ có `quantity`, **không cột tiền nào** (grep `cost` và `value` trên 3 bảng issue/receipt/wip ⇒ 0 kết quả) | Ledger WIP là ledger **số lượng**, không phải ledger giá trị |
| `ProductionReceiptService.approve` sinh `RECEIVE` movement + lot, **không** có bút toán nào giảm WIP | Giá trị vào WIP rồi **ở lại đó vĩnh viễn** — accumulator chỉ tăng, không bao giờ được giải phóng |
| Không có bảng `production_variances` | Variance tính live ⇒ sửa standard cost hôm nay làm **đổi variance của lệnh đã đóng năm ngoái** |

**Mục tiêu:** hoàn tất dòng chảy giá trị `Raw Material → WIP → Finished Goods` mà PDF mô tả, và chốt
được con số variance tại thời điểm đóng lệnh.

**Thay đổi chính:**
1. **Giá trị lên ledger WIP** — thêm cột tiền vào `wip_transactions` (material/labor/overhead, hoặc
   một `amount` + `cost_component`), ghi cùng lúc với `quantity` ở 2 hook đã có
   (`MaterialIssueService.postNew`, `ProductionExecutionService.reportNew`).
2. **Chia theo công đoạn** — `work_order_operation_id` **đã có sẵn** trên `wip_transactions`; chỉ cần
   dùng nó làm chiều gom nhóm. ⚠️ Ý nghĩa của con số phụ thuộc `P8`: không có trạng thái công đoạn thì
   chỉ gom được theo *công đoạn đã có báo cáo*.
3. **Giải phóng WIP khi nhập kho** — `ProductionReceiptService.approve` ghi một `wip_transactions` âm
   (tái dùng `OUTPUT_RECEIPTED` đã có) mang giá trị tương ứng; đó chính là bước 3 của PDF §10.6.4.
   🔴 Phải chốt phương pháp định giá đơn vị thành phẩm (standard cost × số lượng, **hay** actual WIP
   chia đều) — hai lựa chọn cho hai con số khác nhau, và nó quyết định phần dư còn lại trong WIP là
   variance hay là lỗi.
4. **`production_variances`** — snapshot lúc WO `CLOSED`: quantity variance + usage variance + cost
   variance, đóng băng bằng standard cost **tại thời điểm đó**. Đọc lịch sử thì đọc bảng; WO chưa đóng
   thì vẫn tính live như hiện nay.
5. **Read API** — `GET /work-orders/{id}/wip-valuation` và/hoặc report WIP theo plant/work center.

**Bẫy đã biết trước khi bắt đầu:**
- 🔴 **Không sửa `work_order_cost_accumulators` thành nhiều dòng/WO.** Nó là nguồn của
  `costVariance.actual*` trong `WorkOrderVarianceService` (`B94`) và ràng buộc `UNIQUE` chính là thứ
  giữ cho `upsert` đúng. Thêm chiều công đoạn ở **ledger** (`wip_transactions`), không ở accumulator.
- 🔴 **`WipTransactionType.OUTPUT_COMPLETED` ≠ `OUTPUT_RECEIPTED`** — cái đầu là "xưởng làm ra", cái
  sau là "đã nhập kho" (`CLAUDE.md §0.5`). Giải phóng giá trị phải bám cái **sau**.
- Giá component lấy qua `ItemStandardCostLookupService.findStandardUnitCost` (**fully-loaded**, đã
  roll-up), không phải field `materialCost` thô — xem `CLAUDE.md §0.31` hệ quả #2.

**Definition of Done:** tồn giá trị WIP đọc được theo WO **và** theo công đoạn; nhập kho thành phẩm
làm giảm đúng giá trị WIP; WO `CLOSED` có dòng `production_variances` bất biến; test số nghiệp vụ
thật (rule `R6`) + `*IT` cho ledger giá trị; `mvn -o clean verify` PASS.

**Effort:** Cao. **OT:** Không. **Phụ thuộc:** `P3` ✅, `P4` ✅ (nên làm sau `P8`).

---

### P8 — Shop Floor Control Cấp Công Đoạn

> Thêm 2026-09-25 từ PDF §3.1 ("Shop Floor Control") và §10.6.5 ("sản phẩm đang nằm ở công đoạn nào").

🔴 **Đây là phần `P4` đã thiết kế nhưng không làm.** Mục "Thay đổi chính" của `P4` ở trên ghi nguyên
văn *"mỗi dòng status `PENDING/IN_PROGRESS/DONE`"* — `work_order_operations` thật **không có cột
`status`** (đã kiểm entity 2026-09-25), mà `P4` vẫn được tick `[x]`.

**Hiện trạng:** `production_executions` đã ghi good/scrap/rework **theo từng operation**, kèm
`actualStartedAt`/`actualEndedAt`/`operatorUserId`; `work_order_operations` có `plannedStartAt`/
`plannedEndAt` (từ `C2-8`). Nghĩa là dữ liệu *"đã xảy ra gì"* có đủ — thiếu *"đang ở trạng thái gì"*.

**Thay đổi chính:**
- `work_order_operations.status` + CHECK constraint (theo checklist `coding-rules.md §11.3`).
- Endpoint start/complete operation, hoặc suy trạng thái tự động từ execution đầu tiên/cuối cùng —
  **chốt một trong hai**, đừng làm cả hai (hai nguồn sự thật cho cùng một trạng thái).
- `GET /work-orders/{id}` trả trạng thái từng công đoạn; đây là thứ màn hình xưởng cần.

**Bẫy:** thêm giá trị enum là breaking change ngầm — chạy đủ checklist `coding-rules.md §11.3`.

**Definition of Done:** trả lời được "WO này đang ở công đoạn nào"; state machine có test cả nhánh
vào lẫn nhánh ra; `mvn -o clean verify` PASS.

**Effort:** Trung bình. **OT:** Không. **Phụ thuộc:** `P4` ✅.

---

### P9 — Traceability Toàn Chuỗi (Genealogy)

> Thêm 2026-09-25 từ PDF §3.5 và Rule 4 ("Traceability toàn chuỗi").

🔴 **Dữ liệu đã đủ, đường đọc thì không có.** Lot/serial được ghi ở **mọi** mắt xích
(`material_issue_lines.lot_id`/`serial_id`, `production_receipt_lines.lot_id`, `stock_movements.lot_id`),
và `work_order_id` nối hai đầu lại — nhưng **không service/endpoint nào đi theo đường đó**. Grep
`genealogy`/`traceability` trong `src/main` chỉ ra `traceId` (id correlate log của request), **hoàn
toàn không liên quan** — đừng đọc nhầm nó thành traceability nghiệp vụ.

**Hệ quả nghiệp vụ thật:** khi một lô NVL bị phát hiện lỗi, hệ thống **không liệt kê được** những lô
thành phẩm đã dùng nó ⇒ không thu hồi có mục tiêu được. Đó đúng là việc Rule 4 sinh ra để phục vụ.

**Thay đổi chính:**
1. **Truy ngược** — `GET /inventory/lots/{lotId}/genealogy`: lô thành phẩm → `production_receipt_lines`
   → `work_order` → `material_issue_lines` → lô/serial NVL đã tiêu (đệ quy lên nếu NVL cũng do sản
   xuất ra).
2. **Truy xuôi (thu hồi)** — `GET /inventory/lots/{lotId}/where-used`: lô NVL → những WO đã tiêu nó →
   những lô thành phẩm sinh ra.
3. **Nối serial vào Goods Receipt** — nợ có chủ đích còn lại của `P5`: nhận NVL serial-tracked qua PO
   hiện nổ `SERIAL_REQUIRED`. Không đóng nó thì chuỗi truy vết đứt ngay ở đầu vào.

**Bẫy:** đây là **1 aggregate query mỗi cấp**, không loop theo dòng (`coding-rules.md C14`). Đệ quy
phải có cycle guard như `CostingService` đã làm. Cross-module đi qua lookup service (`C7`) —
`inventory → workorder` đã có tiền lệ `LotQcOriginLookupService` (`C2-2`).

**Definition of Done:** từ một lô thành phẩm liệt kê đủ lô NVL cấp 1 và cấp sâu hơn; từ một lô NVL
liệt kê đủ lô thành phẩm dính; Goods Receipt nhận được item serial-tracked; `*IT` trên Postgres thật
(JPQL nhiều cấp — rule `R7`); `mvn -o clean verify` PASS.

**Effort:** Trung bình. **OT:** Không. **Phụ thuộc:** `P5` ✅.

---

### P10 — CRP Đóng Vòng Vào MRP Run (MRP II Closed-Loop)

> Thêm 2026-09-25 từ PDF §3.1 ("MRP I/II", "Capacity Planning") và §3.4 ("MRP + CRP").

**Hiện trạng:** CRP tĩnh **có** (`CapacityBoardService` + `ScheduleAdjustmentService`, `C2-8`) nhưng
là một **read riêng biệt** — `CLAUDE.md §0.30` ghi rõ: *"đánh dấu `OVER_CAPACITY` ở MRP → cờ
`overload` trên Capacity Board (một read độc lập, **không phải nhánh của MRP run**)"*. Lịch operation
sinh ở `release()` là **infinite-capacity**. Vì vậy một lượt MRP **không bao giờ** nói cho người lập
kế hoạch biết rằng kế hoạch nó vừa đề xuất là bất khả thi về năng lực.

**Thay đổi chính:** `MrpRunService` gọi đánh giá năng lực sau khi có đề xuất, gắn
`exceptionState`/`messages[]` (cơ chế **đã có sẵn** từ `F5-B`: `READY`/`WARNING`/`BLOCKED`) cho đề
xuất vượt năng lực work center trong kỳ. Tái dùng `CapacityBoardService.dayCapacityMinutes`/
`utilizationPercent` — **không** viết công thức thứ hai (hai endpoint phải báo cùng một con số quá
tải, đúng lý do `C2-8` để mấy method đó `public`).

**Quyết định cần chốt trước khi code:** vượt năng lực là `WARNING` (vẫn convert được) hay `BLOCKED`
(chặn convert)? `BLOCKED` hiện đang dành cho "thiếu BOM/routing" — thiếu dữ liệu, khác hẳn "đủ dữ
liệu nhưng xưởng không kham nổi". Khuyến nghị `WARNING`.

**Definition of Done:** lượt MRP trên dữ liệu vượt năng lực trả đề xuất có cảnh báo; con số khớp
Capacity Board; `mvn -o clean verify` PASS.

**Effort:** Trung bình. **OT:** Không (vẫn là CRP **tĩnh**; CRP động ⛔ Deferred).

---

### P11 — MPS (Master Production Schedule)

> Thêm 2026-09-25 từ PDF §10.6.2-1 ("Tạo Work Order từ MRP/**MPS**").

**Hiện trạng:** MRP chạy từ `planning_demands` — nguồn là Sales Order đã confirm, hoặc demand nhập
tay. `PlanningDemandType.FORECAST` **tồn tại nhưng chỉ là một giá trị enum**: không engine dự báo,
không kế hoạch sản xuất chủ đạo theo kỳ đứng sau nó.

**Vì sao xếp cuối:** nhu cầu nhập tay (`MANUAL`) đã phủ phần lớn tác dụng thực tế của MPS trong một
hệ sản xuất theo đơn hàng, nên đây là **tinh chỉnh**, không phải lỗ hổng chặn nghiệp vụ. Làm khi
khách hàng thật sự sản xuất theo kế hoạch tồn kho (make-to-stock) chứ không theo đơn.

**Thay đổi chính:** entity kế hoạch theo kỳ cho thành phẩm (item × kỳ × số lượng), sinh
`PlanningDemand` type `FORECAST`; quy tắc khử trùng với nhu cầu Sales Order thật trong cùng kỳ
(*demand consumption* — nếu không, nhu cầu bị **đếm hai lần** và MRP đề xuất gấp đôi).

🔴 **`PlanningDemandStatus.CONSUMED` đã khai báo trong enum nhưng chưa dòng code nào set** (ghi nhận ở
`CLAUDE.md §0.45`) — rất có thể nó được thêm cho đúng cơ chế này. Kiểm trước khi thiết kế lại.

**Definition of Done:** MPS sinh demand; MRP nhận; nhu cầu SO thật tiêu trừ forecast cùng kỳ thay vì
cộng dồn; `mvn -o clean verify` PASS.

**Effort:** Cao. **OT:** Không.

---

## 4. Ghi Chú: Hạng Mục KHÔNG Khuyến Nghị

- **Ép Lot/Serial "bắt buộc toàn hệ thống":** cơ chế lot đã có sẵn ở mức "tùy chọn theo Item"
  (`Item.lotTracked`). Ép `true` toàn bộ chỉ đổi 1 nhánh validation, nhưng **không nên làm** — thiết
  kế tùy chọn phản ánh đúng thực tế hơn (không ai lot-track từng con ốc vít). Giữ nguyên.

> Bốn mục dưới đây thêm 2026-09-25 sau lượt đối chiếu §2.0 — chúng **xuất hiện trong PDF** nhưng vẫn
> không nên làm. Ghi ra để lần đối chiếu sau không mở lại chúng như "khoảng trống mới phát hiện".

- **Process Costing** (PDF §3.3 liệt kê cạnh Job/Standard): **không làm.** Process costing (chi phí
  theo kỳ-công đoạn, equivalent units) dành cho sản xuất liên tục — hoá chất, thực phẩm dạng dòng
  chảy. Repo này là discrete/assembly: mọi thứ neo vào `WorkOrder`, và Job Costing
  (`work_order_cost_accumulators`) là mô hình **đúng** cho hình thái đó. Thêm process costing là dựng
  một mô hình chi phí thứ hai không ai dùng.
- **Material Price Variance:** đã quyết định loại ở `P3` và lý do vẫn đúng — `stock_movements` không
  có cột giá, và giá duy nhất trong hệ thống (`PurchaseOrderLine.unitPrice`) tách rời khỏi ledger
  xuất kho. Làm được nó đòi một tầng actual-costing (FIFO/bình quân gia quyền) chưa tồn tại. `P7`
  **không** mở lại mục này.
- **BOM date-effectivity** (`effectiveFrom`/`effectiveTo`): PDF chỉ yêu cầu "Version control", và
  repo **đã có**: `UNIQUE (company, parent_item, revision)` giữ mọi revision cũ, đúng một `ACTIVE`,
  cộng snapshot bất biến lên WO lúc create (`B12`). Câu "lệnh này dùng BOM nào" đã trả lời được bằng
  snapshot — chính xác hơn effectivity dating. Chỉ thêm khi có yêu cầu nghiệp vụ thật.
- **`Customer` master data:** thiết kế gốc của `P6` (§3 file này) có liệt kê, nhưng bản triển khai
  dùng `sales_orders.customer_name` (text tự do) và **chưa ai cần** danh mục khách hàng. Không phải
  yêu cầu của PDF. Ghi lại để không bị đọc nhầm thành hạng mục bị bỏ quên.

---

## Phase Deferred — Phụ Thuộc OT (CHƯA làm)

> Các hạng mục dưới đây **bị chặn vì hệ thống chưa có tầng OT**. Có code cũng vô dụng khi chưa có
> nguồn dữ liệu real-time từ máy móc để nuôi. **Không nằm trong kế hoạch triển khai hiện tại** —
> chỉ ghi lại để không bỏ sót khi có OT.

| Hạng mục | Vì sao phải chờ OT |
|---|---|
| **MES Integration (ISA-95)** | Bản chất MES là cầu nối ERP ↔ máy móc; không có OT thì không có gì để đọc/ghi real-time |
| **OEE** (Availability × Performance × Quality) | Cần downtime, tốc độ máy, tỷ lệ lỗi real-time — chỉ máy biết |
| **WIP tracking real-time theo công đoạn** | Cần tín hiệu máy báo "đang chạy công đoạn nào" thay vì nhập tay |
| **CRP động** (điều độ theo tải máy thực) | Cần biết máy rảnh/bận theo thời gian thực |

---

## 5. Tóm Tắt Ưu Tiên

> **Trạng thái 2026-09-25:** `P1`–`P6` ✅ xong hết. `P7`–`P11` mới mở từ lượt đối chiếu PDF (§2.0).
> Xem bảng theo dõi ở §2.1. *(Đoạn cũ ở đây dừng lại ở "P1 xong, đang chèn T0+T1" — lệch 6 phase;
> sửa cùng lượt này.)*

**Đã xong:** `P1` (Gates) · `P2` (QC) · `P3` (Costing) · `P4` (Routing/Work Center/CRP tĩnh) ·
`P5` (Serial) · `P6` (Sales Order + WO Close).

**Thứ tự khuyến nghị cho phần còn lại:**

1. **`P8` (Shop Floor Control cấp công đoạn)** — rẻ nhất, và làm `P7` tốt hơn hẳn. Cũng là mục `P4`
   thiết kế mà chưa làm, nên đóng nó là trả nợ chứ không phải mở phạm vi mới.
2. **`P7` (WIP Valuation + Variance snapshot)** — khoảng trống lý thuyết **lớn nhất còn lại**; đây là
   thứ phân biệt Manufacturing ERP với Commercial ERP rõ nhất sau BOM/WO. Làm sau `P8`.
3. **`P9` (Genealogy)** — giá trị nghiệp vụ cao (thu hồi có mục tiêu), effort trung bình, dữ liệu đã
   sẵn. Có thể làm song song `P7` vì không đụng nhau.
4. **`P10` (CRP đóng vòng)** — tinh chỉnh vòng hoạch định, tái dùng gần hết `C2-8`.
5. **`P11` (MPS)** — chỉ làm khi khách hàng sản xuất make-to-stock.
6. **Chờ OT:** toàn bộ Phase Deferred.

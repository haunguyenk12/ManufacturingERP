# System Flow – Manufacturing ERP

> Tài liệu mô tả luồng nghiệp vụ tổng thể và chi tiết từng module.  
> Cập nhật lần cuối: 2025 · Stack: Java 17 · Spring Boot 3 · PostgreSQL

---

## Tổng quan

```
┌─────────────────────────────────────────────────────────────────────┐
│                      LUỒNG TỔNG HỆ THỐNG                           │
│                                                                     │
│  Login & Auth → Setup Master Data → Planning / MRP                 │
│                                          │                          │
│                          ┌───────────────┴───────────────┐         │
│                          ▼                               ▼         │
│                    WORK_ORDER                  PURCHASE_REQUISITION │
│                          │                               │         │
│               WO → Issue → WIP → Receipt         PR → PO → GR      │
│                          │                               │         │
│                          └───────────────┬───────────────┘         │
│                                          ▼                          │
│                            Inventory Movement (ISSUE / RECEIVE)     │
│                                          │                          │
│                              Dashboard / Variance / Audit           │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Flow 0 – Luồng tổng hệ thống

> Sơ đồ bao quát toàn bộ từ đăng nhập đến kết quả cuối cùng.

```
[User Login]
     │
     ▼
[Authenticate JWT + Check Permission & Scope]
     │
     ▼
[Setup Master Data]
     ├─ Company / Plant / Warehouse
     ├─ Item / Lot / Stock Settings
     ├─ BOM (Create & Activate)
     └─ Supplier / Item Supplier
     │
     ▼
[Create Planning Demand]
     │
     ▼
[Run MRP]
     │
     ▼
[Calculate Requirement Lines]
     │
     ▼
◇ Supply Suggestion Type?
     │
     ├─── WORK_ORDER ──────────────────────────────────────────────────┐
     │         │                                                        │
     │    [Convert to Work Order]                                       │
     │         │                                                        │
     │    [Release Work Order]                                          │
     │         │                                                        │
     │    [Reserve Material]                                            │
     │         │                                                        │
     │    [Post Material Issue]──────────────► [Inventory ISSUE Move]  │
     │         │                                                        │
     │    [Record WIP Transaction]                                      │
     │         │                                                        │
     │    [Post Production Receipt]──────────► [Inventory RECEIVE Move]│
     │                                         [Increase Finished      │
     │                                          Goods Stock]           │
     │                                                                  │
     └─── PURCHASE_REQUISITION ───────────────────────────────────────┐│
               │                                                       ││
          [Convert to Purchase Requisition]                            ││
               │                                                       ││
          [Approve PR]                                                 ││
               │                                                       ││
          [Convert to Purchase Order]                                  ││
               │                                                       ││
          [Send PO]                                                    ││
               │                                                       ││
          [Post Goods Receipt]─────────────────► [Inventory RECEIVE Move]
                                                  [Increase Raw        ││
                                                   Material Stock]     ││
                                                                       ││
     ◄─────────────────────────────────────────────────────────────────┘│
     ◄──────────────────────────────────────────────────────────────────┘
     │
     ▼
[Dashboard / Variance / Audit]
```

---

## Flow 1 – Khởi tạo hệ thống (System Setup)

> **Actor:** ADMIN  
> **Mục đích:** Cấu hình đầy đủ dữ liệu nền trước khi hệ thống sẵn sàng lập kế hoạch.

```
Start
  │
  ▼
[Login]
  │
  ▼
◇ Authentication successful?
  ├── No  → [Return login error] → End
  └── Yes ▼
          [Check permissions and access scope]
          │
          ▼
          [Set up master data]
          │
          ├─ [Create Company / Plant / Warehouse]
          ├─ [Create Items, Lots & Stock settings]
          ├─ [Create and Activate BOM]
          └─ [Create Suppliers and Item Suppliers]
          │
          ▼
          [Optional: Manual stock receive]
          │
          ▼
          [System ready for planning]
          │
         End
```

### Ghi chú
| Bước | Mô tả |
|------|-------|
| Authentication | JWT token được sinh và kiểm tra scope |
| Check permissions | RBAC động – kiểm tra role + access scope (Company / Plant / Warehouse) |
| Activate BOM | Chỉ 1 BOM `ACTIVE` / sản phẩm / plant tại một thời điểm |
| Manual stock receive | Tuỳ chọn – nhập tồn kho ban đầu trước khi bắt đầu vận hành |

---

## Flow 2 – Lập kế hoạch & MRP

> **Actor:** MANAGER (trigger, duyệt) + SYSTEM (tính toán tự động)  
> **Mục đích:** Phát hiện thiếu hụt vật tư, tạo gợi ý cung ứng.

```
Start
  │
  ├── [Create sales order (DRAFT)] → [Confirm sales order]
  │        │                              │
  │        │                              ▼
  │        │                     [1 planning demand / line]   ← F3: demandType = SALES_ORDER
  │        │                              │                      referenceType = SALES_ORDER_LINE
  │        │                              ▼
  │        │                     [View selectable demand]     ← GET /sales-orders/planning-demands
  │        │                                                     (PERM_MRP_RUN, không phải quyền sales)
  │        └── [Cancel sales order] → [Cancel its OPEN demands]
  │                                       (chỉ từ DRAFT/CONFIRMED; IN_PRODUCTION ⇒ STATE_CONFLICT)
  │
  ├── [Create planning demand]  ← demand nhập tay (MANUAL / FORECAST)
  │
  ▼
[Run MRP]                       ← POST /planning-runs
  │                                F5-B: body có demandLineIds ⇒ chỉ những demand đó vào run;
  │                                vắng mặt ⇒ quét mọi demand OPEN trong horizon (hành vi cũ)
  ▼
[Snapshot open demands]
  │
  ▼
[Explode active BOM]            ← SYSTEM: phân rã BOM đa cấp
  │
  ▼
[Calculate net requirements]    ← Gross + stockTarget − (Available + open WO + open PO)
  │                                F5-B: mỗi dòng ghi settingSource + excludedLotCount
  │                                D4: open PO (SENT/PARTIALLY_RECEIVED) vào scheduledReceipts
  ▼
◇ Shortage found?
  ├── No  → [Mark requirement as covered]
  │           → [View requirement lines] → End
  │
  └── Yes ▼
          [Create supply suggestions]   ← F5-B: gán exceptionState + messages[]
          │                                READY / WARNING (SYSTEM_FALLBACK_USED)
          │                                BLOCKED (MISSING_BOM | MISSING_ROUTING)
          ▼
          [View supply suggestions]     ← GET /planning-runs/{id}/suggestions
          │
          ▼
          [Manager reviews suggestions]
          │
          ▼
          ◇ Approve suggestion?
            ├── No  → [Reject suggestion with note] → End
            └── Yes ▼
                    ◇ exceptionState = BLOCKED?
                      ├── Yes → [409 MISSING_BOM | MISSING_ROUTING]  ← không tạo được chứng từ (B58)
                      └── No  ▼
                              ◇ supplyType?
                                ├── BUY  → ► Flow 3: Purchasing
                                │            POST /supply-suggestions/{id}/convert-to-purchase-requisition
                                └── MAKE → ► Flow 4: Work Order
                                             POST /supply-suggestions/{id}/convert-to-work-order
```

### Ghi chú
| Bước | Mô tả |
|------|-------|
| Explode BOM | Đệ quy từng cấp BOM → tính tổng nhu cầu component |
| Net requirements | `net = max(0, gross + stockTarget − (available + scheduledReceipts − priorAllocations))`, với `stockTarget = max(safetyStock, reorderPoint)`. Reserved **không** là available |
| **`scheduledReceipts` (`D4`)** | Work order `RELEASED`/`IN_PROGRESS` **+** purchase order `SENT`/`PARTIALLY_RECEIVED`. PO `DRAFT` **không** tính. Trước `D4` chỉ có work order ⇒ MRP đề xuất mua trùng thứ đã đặt hàng (nợ #16). Bất biến `B67` |
| Run header (`D4`) | `code` (`RUN-<8 hex đầu của id>`) + `shortageLines` / `plannedWorkOrders` / `plannedPurchaseRecommendations` / `blockedProposals` — đếm tại chỗ từ kết quả run, không query lại |
| Supply suggestion | Loại: `PURCHASE_REQUISITION` hoặc `WORK_ORDER` |
| Reject with note | Ghi lý do từ chối vào suggestion record |
| Sales order demand (`F3`) | Chỉ `confirm` mới sinh demand — 1 `PlanningDemand` cho mỗi line. Đơn `DRAFT` không sinh demand |
| Demand line eligible (spec §2.1) | Đủ **cả 4**: order ∈ `CONFIRMED`/`IN_PRODUCTION`/`PARTIALLY_FULFILLED` · cùng plant · `dueDate ≤ horizonEnd` (biên inclusive) · `openQuantity = ordered − fulfilled > 0` |

---

## Flow 3 – Mua hàng (Purchasing)

> **Actor:** MANAGER (duyệt PR/PO) + OPERATOR (post GR)  
> **Điểm vào:** Từ approved purchase suggestion (Flow 2) hoặc tạo thủ công.

```
Start from approved purchase suggestion
  │
  ▼
[Convert to Purchase Requisition (PR)]
  │
  ▼
[Review Purchase Requisition]
  │
  ▼
◇ Approve PR?
  ├── No  → [Reject / Cancel PR] → End
  └── Yes ▼
          [Convert to Purchase Order (PO)]
          │
          ▼
          [Send Purchase Order to Supplier]
          │
          ▼
          [Post Goods Receipt (GR)]
          │
          ▼
          ◇ Goods receipt valid?
            ├── No  → [Reject Goods Receipt] → End
            └── Yes ▼
                    [Inventory RECEIVE movement]     ─┐
                    [Increase Raw Material Stock]      │── ► Flow 5: Inventory
                    │
                    ▼
                    [Update received quantity & PO status]
                    │
                    ▼
                    ◇ Need to rerun MRP?
                      ├── Yes → [Run MRP again] → ► Flow 2
                      └── No  → [Dashboard / Audit] → End
```

### Ghi chú
| Bước | Mô tả |
|------|-------|
| PR | Purchase Requisition – yêu cầu nội bộ |
| PO | Purchase Order – lệnh chính thức gửi supplier |
| GR valid | Kiểm tra số lượng, lot, điều kiện nhận hàng |
| RECEIVE movement | Append vào `stock_movements` ledger, cập nhật `stock_balances` |

---

## Flow 4 – Sản xuất (Work Order Execution)

> **Actor:** MANAGER (tạo/release) + OPERATOR (thực thi) + SYSTEM (tính variance)  
> **Điểm vào:** Từ approved work order suggestion (Flow 2) hoặc tạo thủ công.

```
Start from approved Work Order suggestion
  │
  ▼
[Create Work Order]       ← Snapshot BOM requirements → work_order_lines
  │                       ← Snapshot Routing header  → source_routing_id/code/version
  │                          + routing_captured_at (F4, bất biến – copy chứ không tham chiếu)
  │
  ├── Đường convert proposal MAKE, item không có Routing ACTIVE
  │      → 409 MISSING_ROUTING, KHÔNG tạo Work Order
  │   Đường tạo thủ công: vẫn tạo được, 4 cột snapshot = NULL
  │
  ▼
[Reserve Material]        ← Tăng reserved_quantity trong stock_balances
  │
  ▼
[Release Work Order]      ← GATE 1a: reservation phải phủ 100% requirement
  │
  ├── Thiếu reservation → 409 STATE_CONFLICT + Status: DRAFT → BLOCKED (persist thật, D9)
  │      GET /work-orders/{id}/material-readiness → xem thiếu bao nhiêu
  │      → [Reserve thêm] → thử release lại (BLOCKED → RELEASED)
  │
  └── Đủ ▼
          Status: DRAFT | BLOCKED → RELEASED
          │
          ▼
          [Post Material Issue]
          │
          ├── Vượt định mức BOM? → GATE 1b
          │     ├── Không có PERM_MATERIAL_ISSUE_OVERRIDE → 403
          │     ├── Có quyền nhưng thiếu overrideReason  → 400 (MISSING_REQUIRED_FIELD)
          │     └── Có quyền + lý do → line.over_issue = true (lưu vết)
          │
          ├──► [Inventory ISSUE movement]     ─┐
          │    [Stock quantity decreases]       │── ► Flow 5: Inventory
          │
          ▼
          [Record WIP Transaction]    ← Append-only tracking
          │
          ▼
          ◇ Scrap or rework occurs?
            ├── Yes → [Record WIP scrap / rework] → [Continue production]
            └── No  ▼
                    [Report Production Execution]  ← F5 – LÕI CỦA VÒNG ĐỜI
                    POST /work-orders/{id}/production-executions
                    good / scrap / rework  →  cộng dồn actual_* trên work_orders
                    → WIP OUTPUT_COMPLETED / SCRAP_REPORTED / REWORK_REPORTED
                    → cumulative good = planned ⇒ Work Order COMPLETED (ngay tại đây,
                      KHÔNG chờ receipt nào được duyệt)
                    → mở trần availableToReceipt = actual_good − completed
                    │
                    ▼
                    [Create Production Receipt]   ← GATE 1c – bước 1 (F2)
                    Status: DRAFT
                    KHÔNG tạo movement, KHÔNG cộng completed_quantity
                    │
                    ▼
                    [Submit Production Receipt]   ← GATE 1c – bước 2 (F2)
                    Status: PENDING_APPROVAL
                    Vẫn KHÔNG đụng tồn kho
                    │
                    ▼
                    ◇ Người duyệt quyết định (PERM_PRODUCTION_RECEIPT_APPROVE)
                      ├── Reject → Status: REJECTED, không đụng tồn kho → End
                      │            (reason bắt buộc – APPROVAL_REASON_REQUIRED)
                      └── Approve ▼                ← GATE 1c – bước 3
                              Status: APPROVED
                              [Inventory RECEIVE movement]      ─┐
                              [Increase Finished Goods Stock]    │── ► Flow 5
                              [Lot mới tạo → status = HOLD]      │
                              │
                              ▼
                              ◇ QC disposition (PERM_QUALITY_DISPOSITION)   ← F2
                                │  Receipt vẫn APPROVED; chỉ lot đổi status.
                                │  Sinh LOT_STATUS_CHANGE movement (direction NONE),
                                │  KHÔNG đổi stock_balances.quantity.
                                ├── REJECTED  → Lot: REJECTED
                                │               available KHÔNG tăng → End
                                └── AVAILABLE → Lot: AVAILABLE
                                                available tăng (lot hết HOLD)
                              │
                              ▼
                              [Cộng completed_quantity = đã nhập kho]   ← F5
                              [Ghi WIP OUTPUT_RECEIPTED]
                              Status Work Order KHÔNG đổi ở bước này
                              │
                              ▼
                              ◇ Còn hàng chưa nhập kho? (availableToReceipt > 0)
                                ├── Có  → ► Back to [Create Production Receipt]
                                └── Hết ▼
                                        [Dashboard / Variance / Audit]
                                        │
                                       End
```

### Ghi chú
| Bước | Mô tả |
|------|-------|
| Create WO | Snapshot BOM tại thời điểm tạo → `work_order_lines` cố định |
| Reserve | `reserved_quantity` tăng, `available = qty − reserved` |
| **Gate 1a – Release** | Chặn release khi `required − issued > reserved`; WO ghi `BLOCKED` + trả **409 `STATE_CONFLICT`**. `BLOCKED` commit ở transaction riêng qua `WorkOrderBlockRecorder` — bean tách riêng vì phải **return bình thường** mới commit được, gate throw sau đó (nợ #23, sửa ở `D9`) |
| **Reserve trước hay release trước?** | **Reserve trước** — `canReserve()` cho phép `DRAFT`/`PLANNED`/`BLOCKED`, chỉ chặn `COMPLETED`/`CANCELLED`. Trước `D9` reserve đòi `RELEASED` nên hai rule khoá nhau và WO từ MRP không release được (nợ #22) |
| **Gate 1b – Over-issue** | Vượt định mức cần `PERM_MATERIAL_ISSUE_OVERRIDE` + `overrideReason`; lưu vết `material_issue_lines.over_issue` |
| **Gate 1c – Receipt approval** | `post` → `DRAFT`; `submit` → `PENDING_APPROVAL` (cả hai **không** đụng kho); `approve` → `APPROVED` + RECEIVE movement; `reject` → `REJECTED` (reason bắt buộc) |
| **QC disposition (F2 + D5)** | Chỉ trên receipt `APPROVED`, chỉ **một lần**, reason bắt buộc. Receipt **vẫn** `APPROVED` sau QC. Hai đường theo **cấp dòng** (`line.lot != null`), không theo cờ item:<br>• **có lot** → lot `HOLD` → `AVAILABLE`/`REJECTED` + `LOT_STATUS_CHANGE` movement + dòng `quality_dispositions`<br>• **không lot** (`D5`) → verdict chỉ trên `production_receipts`; `AVAILABLE` không sinh movement, `REJECTED` sinh **`ADJUST_OUT`** rút hàng khỏi kho |
| ISSUE movement | Append-only vào `stock_movements`, gắn `work_order_id` |
| WIP transaction | Append-only, ghi scrap/rework/progress |
| RECEIVE movement | Chỉ sinh khi **approve**; lot mới mở ở `HOLD`, chờ QC disposition chuyển sang `AVAILABLE`/`REJECTED` |
| Variance | `Planned qty − Actual qty` per component |
| Status flow | `DRAFT ⇄ BLOCKED → RELEASED → IN_PROGRESS → COMPLETED / CANCELLED` |

> **Output không lot-tracked (`D5`, 2026-07-30 — nợ #17 đã trả):** item không lot-tracked không có chỗ
> mang `HOLD`, nên output đó vào kho là **dùng được ngay từ lúc `approve`**. Trước `D5`,
> `qc-disposition` trên receipt như vậy trả `STATE_CONFLICT` ⇒ đơn hàng của nó treo ở `IN_PROGRESS`
> vĩnh viễn (fulfillment chỉ chạy từ QC `AVAILABLE`). Nay QC chạy được: `AVAILABLE` chỉ ghi verdict +
> mở đường fulfillment (không đổi tồn kho — hàng đã available); `REJECTED` **phải** rút hàng bằng
> `ADJUST_OUT` vì không có lot status nào để làm hàng hỏng thành không dùng được. Nếu hàng đã ra khỏi
> kho trước khi QC kịp phán quyết thì `REJECTED` nổ `INSUFFICIENT_AVAILABLE_STOCK` (409) và rollback —
> có chủ đích, cần người xử lý.
>
> Ngược lại, receipt của item lot-tracked **bắt buộc** có `lotCode`/`lotId` ngay ở bước `post`
> (`LOT_REQUIRED`). Lot đã tồn tại (trùng `lotCode`) giữ nguyên status hiện tại — chỉ lot **mới tạo**
> mới nhận `HOLD`, nên lot tái sử dụng sẽ bị `LOT_NOT_ELIGIBLE` khi QC.
> Khuyến nghị: mỗi production receipt dùng một `lotCode` mới.

---

## Flow 5 – Quản lý Tồn kho (Inventory)

> **Actor:** OPERATOR (thao tác thủ công) + SYSTEM (từ Production & Purchasing)  
> **Mục đích:** Mọi thay đổi tồn kho đều đi qua một điểm duy nhất – `stock_movements` ledger.

```
Start
  │
  ▼
◇ Inventory action type?
  │
  ├── ManualReceive   → [Manual stock receive]              ─┐
  ├── ManualIssue     → [Manual stock issue]                 │
  ├── Adjustment      → [Stock adjustment]                   ├──► [Create stock movement]
  ├── FromProduction  → [Inventory movement from production] │
  └── FromPurchasing  → [Inventory movement from GR]        ─┘
                                  │
                                  ▼
                        [Create stock movement]   ← Append-only vào stock_movements
                                  │
                                  ▼
                        [Update stock balance]    ← Cập nhật stock_balances (projection)
                                  │
                                  ▼
                        [View stock balances and movements]
                                  │
                                  ▼
                        [Check low stock & reorder alerts]  ← SYSTEM tự động kiểm tra
                                  │
                                  ▼
                        [View inventory dashboard]
                                  │
                                  ▼
                        [View audit logs if needed]
                                  │
                                 End
```

### Inventory action types
| Action Type | Nguồn gốc | Movement type |
|-------------|-----------|---------------|
| `ManualReceive` | OPERATOR nhập kho thủ công | `RECEIVE` |
| `ManualIssue` | OPERATOR xuất kho thủ công | `ISSUE` |
| `Adjustment` | Kiểm kê, sai lệch, đảo bút toán | `ADJUST` / `REVERSAL` |
| `FromProduction` | Post Material Issue hoặc Production Receipt | `ISSUE` / `RECEIVE` |
| `FromPurchasing` | Post Goods Receipt từ Purchase Order | `RECEIVE` |
| `QcDisposition` | QC disposition trên Production Receipt đã `APPROVED` | `LOT_STATUS_CHANGE` (direction `NONE`) |

### Quy tắc bất biến của Inventory
```
✅ stock_movements  → APPEND-ONLY, không sửa / xoá record cũ
✅ stock_balances   → Projection đọc nhanh, cập nhật sau mỗi movement
✅ Sai lệch         → Tạo ADJUSTMENT hoặc REVERSAL movement mới (không sửa cũ)
✅ Lot HOLD / REJECTED / EXPIRED → KHÔNG được dùng cho sản xuất / reserve
✅ LOT_STATUS_CHANGE → chỉ đổi lot.status, KHÔNG đổi stock_balances.quantity (direction NONE)
✅ Mỗi movement POST phải có Idempotency-Key
✅ Stock update + document nghiệp vụ phải nằm trong cùng 1 transaction
```

#### Phạm vi của `Idempotency-Key` trên ledger *(D6, V37)*

```
Scope replay = (idempotency_key, movement_type)   ← KHÔNG phải key một mình

Cùng key + CÙNG   movement_type  →  replay: trả chứng từ cũ, KHÔNG cộng tồn lần 2
Cùng key + CÙNG   type + payload khác  →  IDEMPOTENCY_CONFLICT (409)
Cùng key + KHÁC  movement_type  →  2 chứng từ ĐỘC LẬP, mỗi cái đổi tồn của nó
```

Trước `D6` constraint là `UNIQUE (idempotency_key)` **toàn bảng** và query replay không kèm type, nên
một key gửi tới `/receive` rồi `/issue` thì lần 2 trả về movement `RECEIVE` và **không xuất gì** —
client nhận 200 với chứng từ của nghiệp vụ khác (nợ #10).

- Constraint (`uk_stock_movements_idempotency_key`) và query (`findByIdempotencyKeyAndMovementType`)
  phải **luôn khớp scope**; đổi một bên mà không đổi bên kia thì replay trả về dòng không xác định
  trước — tệ hơn bug gốc. Bất biến `B69`.
- `issue` và `issueReserved` **cùng** type `ISSUE` ⇒ chung một scope, có chủ đích (`B70`).
- `adjust` scope theo `ADJUST_IN`/`ADJUST_OUT` tuỳ dấu delta ⇒ delta phải được đọc trước khi tra
  replay, nên delta = 0 bị từ chối **trước** khi validate item/warehouse (`B71`).
- **Không** scope theo user như spec §10.2: `created_by` nullable, Postgres coi 2 `NULL` là khác nhau
  ⇒ dòng lịch sử sẽ mất bảo vệ trùng lặp. Cần backfill trước, chưa xếp lịch.
- `material_issues` / `production_receipts` / `production_executions` vẫn `UNIQUE` toàn bảng (mỗi bảng
  chỉ một loại nghiệp vụ ghi vào); `goods_receipts` scope theo `(purchase_order_id, idempotency_key)`.

---

## Liên kết giữa các Flow

```
         ┌──────────────┐
         │   Flow 1     │
         │   Setup      │ ← ADMIN cấu hình một lần
         └──────┬───────┘
                │ System ready
                ▼
         ┌──────────────┐   Rerun MRP
         │   Flow 2     │◄──────────────────────────────┐
         │   MRP        │                               │
         └──────┬───────┘                               │
                │                                       │
     ┌──────────┴───────────┐                          │
     │ PURCHASE_REQUISITION │    WORK_ORDER             │
     ▼                      ▼                          │
┌─────────────┐    ┌─────────────────┐                 │
│   Flow 3    │    │    Flow 4       │                 │
│  Purchasing │    │   Work Order    │                 │
└──────┬──────┘    └───────┬─────────┘                 │
       │                   │                            │
       │  GR RECEIVE       │  Issue / Receipt           │
       ▼                   ▼                            │
┌──────────────────────────────────────┐               │
│             Flow 5: Inventory        │               │
│  (Create movement → Update balance)  │───────────────┘
│  (Check low stock → Dashboard/Audit) │  Nếu shortage sau GR → rerun
└──────────────────────────────────────┘
```

---

## Trạng thái nghiệp vụ

### Sales Order
```
DRAFT → CONFIRMED → IN_PRODUCTION → PARTIALLY_FULFILLED → FULFILLED
  ↘         ↘ CANCELLED
```
> `confirm` sinh independent demand cho MRP (1 `PlanningDemand` / line); `cancel` huỷ demand còn `OPEN`.
> Chỉ huỷ được từ `DRAFT`/`CONFIRMED` — từ `IN_PRODUCTION` trở đi trả `STATE_CONFLICT` (409)
> vì đã có Work Order và có thể đã xuất vật tư.
>
> **[F6] Ba trạng thái sau `CONFIRMED` nay đã có đường vào:**
>
> | Trạng thái | Ai set | Điều kiện |
> |---|---|---|
> | `IN_PRODUCTION` | `WorkOrderDemandAllocationService.allocate` (lúc convert proposal MAKE) | có ≥ 1 allocation gắn vào đơn; **chỉ** từ `CONFIRMED` |
> | `PARTIALLY_FULFILLED` | `SalesOrderFulfillmentService.applyFulfillment` | có line `fulfilledQuantity > 0` nhưng chưa đủ mọi line |
> | `FULFILLED` | `SalesOrderFulfillmentService.applyFulfillment` | **mọi** line `fulfilledQuantity >= orderedQuantity` |
>
> `fulfilledQuantity` **chỉ** tăng khi QC disposition = `AVAILABLE` commit (spec §7.1) — không phải
> lúc receipt approve, vì lúc đó lot còn `HOLD` và không giao được cho khách.
>
> **[D5]** Điều đó nay đúng cho **cả** thành phẩm không lot-tracked. Trước `D5` những item đó không QC
> được nên đơn hàng của chúng treo ở `IN_PRODUCTION` vĩnh viễn, không có lỗi nào báo (nợ #17).

### Work Order
```
DRAFT → PLANNED ⇄ BLOCKED → RELEASED → IN_PROGRESS → COMPLETED
   ↘        ↘        ↘             ↘ CANCELLED
```
> **Ba gate khác nhau, đừng gộp** (bất biến `B13`, đã tách 2 lần):
>
> | Thao tác | Status được phép | Method |
> |---|---|---|
> | Issue / WIP / production execution | `RELEASED`, `IN_PROGRESS` | `canExecute()` |
> | **Reserve** **[D9]** | mọi status trừ `COMPLETED`, `CANCELLED` | `canReserve()` |
> | **Receipt** (post/submit/approve) **[D11]** | `RELEASED`, `IN_PROGRESS`, **`COMPLETED`** | `canReceipt()` |
>
> `PLANNED` **[F5]** = lịch đã chốt, chưa chuẩn bị vật tư — hành xử như `DRAFT` với execution, nhưng
> **được** release và cancel. Release được từ `DRAFT`, `PLANNED`, hoặc `BLOCKED`.
> `BLOCKED` = release bị chặn vì reservation chưa phủ đủ requirement (Gate 1a).
> **[F7]** `cancel` **bắt buộc** `reason` (spec §3.2), lưu ở `work_orders.cancel_reason` (`V38`);
> thiếu ⇒ 400 `APPROVAL_REASON_REQUIRED`. Cột **nullable**: `NULL` nghĩa là "cancel trước `F7`",
> không phải "không có lý do".
> `BLOCKED` được release lại sau khi reserve đủ, hoặc cancel; **không** được update
> (đổi `plannedQuantity` sẽ làm `required < reserved`).
>
> ⚠️ **[F5] `COMPLETED` do Production Execution quyết định**, không phải receipt: khi cumulative
> `actual_good_quantity` = `planned_quantity`. Receipt approve chỉ cộng `completed_quantity`
> (= đã nhập kho) và **không** đổi status. Xem `CLAUDE.md §0.5`.
>
> ⚠️ **[D11] `COMPLETED` KHÔNG đóng đường receipt.** Nó nghĩa là "xưởng làm xong", còn hàng vào kho là
> `completed_quantity` — cột riêng và luôn đi **sau**. Trước `D11`, WO có `planned_quantity` đúng bằng
> số cần nhập kho thì dòng receipt **cuối cùng** không bao giờ lấy ra được (nợ #25). Trần số lượng vẫn
> do `B16` giữ, không phải do status.

### Production Receipt
```
DRAFT → PENDING_APPROVAL → APPROVED
                         ↘ REJECTED
```
> `APPROVED` = đã ghi vào tồn kho (F2 đổi tên từ `POSTED`; `V26` migrate dữ liệu cũ).
> Chỉ `approve` mới sinh movement; lot mới ra `HOLD`.
> Cần `PERM_PRODUCTION_RECEIPT_APPROVE` để approve/reject (OPERATOR không có).
>
> **QC disposition không phải một status của receipt** — receipt giữ `APPROVED`, còn quyết định QC
> nằm ở `production_receipts.qc_result`. Với output **có lot**, nó cũng được ghi per-lot vào
> `quality_dispositions` và trạng thái thật của hàng nằm ở `inventory_lots.status`:
> ```
> Lot: HOLD → AVAILABLE   (QC pass — available tăng)
>          ↘ REJECTED     (QC fail — on-hand giữ nguyên, available KHÔNG tăng)
> ```
> Với output **không lot** (`D5`) thì không có lot nào để chuyển trạng thái và **không** có dòng
> `quality_dispositions` — verdict chỉ tồn tại trên receipt, và `REJECTED` rút hàng bằng `ADJUST_OUT`
> nên chính `stock_balances.quantity` giảm.
> Cần `PERM_QUALITY_DISPOSITION` (chỉ ADMIN/MANAGER).

### Material Issue
```
POSTED → CANCELLED
```
> Line vượt định mức BOM được đánh dấu `over_issue = true` kèm `override_reason`.

### Purchase Requisition
```
DRAFT → PENDING_APPROVAL → APPROVED → CONVERTED_TO_PO
                        ↘ REJECTED / CANCELLED
```

### Purchase Order
```
DRAFT → CONFIRMED → SENT → PARTIALLY_RECEIVED → RECEIVED → CLOSED
                         ↘ CANCELLED
```

### BOM
```
DRAFT → ACTIVE
     ↘ INACTIVE
```
> Chỉ `ACTIVE` được dùng cho MRP / Work Order.

### Routing *(F4)*
```
DRAFT → ACTIVE
     ↘ INACTIVE
```
> Mỗi `(company, item)` chỉ **1** routing `ACTIVE`; activate bản mới ⇒ bản cũ tự động `INACTIVE`.
> Activate lần 2 ⇒ `STATE_CONFLICT` (409). Routing không có operation ⇒ không activate được.

### Stock Movement (Ledger)
```
Types: RECEIVE | ISSUE | ADJUST | REVERSAL | TRANSFER
Rule:  APPEND-ONLY – không sửa, không xoá
```

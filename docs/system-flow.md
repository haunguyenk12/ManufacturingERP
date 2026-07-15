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
  ▼
[Create planning demand]
  │
  ▼
[Run MRP]
  │
  ▼
[Snapshot open demands]
  │
  ▼
[Explode active BOM]            ← SYSTEM: phân rã BOM đa cấp
  │
  ▼
[Calculate net requirements]    ← Gross demand − Available − Reserved
  │
  ▼
◇ Shortage found?
  ├── No  → [Mark requirement as covered]
  │           → [View requirement lines] → End
  │
  └── Yes ▼
          [Create supply suggestions]
          │
          ▼
          [View supply suggestions]
          │
          ▼
          [Manager reviews suggestions]
          │
          ▼
          ◇ Approve suggestion?
            ├── No  → [Reject suggestion with note] → End
            └── Yes ▼
                    ◇ Suggestion type?
                      ├── PURCHASE_REQUISITION → ► Flow 3: Purchasing
                      └── WORK_ORDER          → ► Flow 4: Work Order
```

### Ghi chú
| Bước | Mô tả |
|------|-------|
| Explode BOM | Đệ quy từng cấp BOM → tính tổng nhu cầu component |
| Net requirements | `Gross − (Available stock − Reserved)` |
| Supply suggestion | Loại: `PURCHASE_REQUISITION` hoặc `WORK_ORDER` |
| Reject with note | Ghi lý do từ chối vào suggestion record |

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
  │
  ▼
[Release Work Order]      ← Status: DRAFT → RELEASED
  │
  ▼
[Reserve Material]        ← Tăng reserved_quantity trong stock_balances
  │
  ▼
◇ Enough materials?
  ├── No  → [Raise shortage & record WO shortage]
  │           → [Run MRP again] → ► Flow 2
  │
  └── Yes ▼
          [Post Material Issue]
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
                    [Post Production Receipt]
                    │
                    ▼
                    ◇ Production receipt valid?
                      ├── No  → [Adjust / reject receipt] → End
                      └── Yes ▼
                              [Inventory RECEIVE movement]      ─┐
                              [Increase Finished Goods Stock]    │── ► Flow 5
                              │
                              ▼
                              [Update production quantity]
                              │
                              ▼
                              [Mark WIP output as completed]
                              │
                              ▼
                              ◇ Work Order completed?
                                ├── No  → ► Back to [Post Material Issue]
                                └── Yes ▼
                                        [Mark Work Order as COMPLETED]
                                        │
                                        ▼
                                        [Dashboard / Variance / Audit]
                                        │
                                       End
```

### Ghi chú
| Bước | Mô tả |
|------|-------|
| Create WO | Snapshot BOM tại thời điểm tạo → `work_order_lines` cố định |
| Reserve | `reserved_quantity` tăng, `available = qty − reserved` |
| ISSUE movement | Append-only vào `stock_movements`, gắn `work_order_id` |
| WIP transaction | Append-only, ghi scrap/rework/progress |
| RECEIVE movement | Nhận thành phẩm vào kho (FINISHED_GOOD / WIP) |
| Variance | `Planned qty − Actual qty` per component |
| Status flow | `DRAFT → RELEASED → IN_PROGRESS → COMPLETED / CANCELLED` |

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

### Quy tắc bất biến của Inventory
```
✅ stock_movements  → APPEND-ONLY, không sửa / xoá record cũ
✅ stock_balances   → Projection đọc nhanh, cập nhật sau mỗi movement
✅ Sai lệch         → Tạo ADJUSTMENT hoặc REVERSAL movement mới (không sửa cũ)
✅ Lot HOLD / REJECTED / EXPIRED → KHÔNG được dùng cho sản xuất / reserve
✅ Mỗi movement POST phải có Idempotency-Key
✅ Stock update + document nghiệp vụ phải nằm trong cùng 1 transaction
```

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

### Work Order
```
DRAFT → RELEASED → IN_PROGRESS → COMPLETED
                              ↘ CANCELLED
```
> Chỉ `RELEASED` / `IN_PROGRESS` mới được issue / receipt / WIP update.

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

### Stock Movement (Ledger)
```
Types: RECEIVE | ISSUE | ADJUST | REVERSAL | TRANSFER
Rule:  APPEND-ONLY – không sửa, không xoá
```

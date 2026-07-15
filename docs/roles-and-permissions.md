# Roles & Permissions – Manufacturing ERP

> Tài liệu tham chiếu nhanh về vai trò và quyền hạn trong hệ thống.  
> Cập nhật lần cuối: 2025 · Stack: Java 17 · Spring Boot 3 · Dynamic RBAC

---

## Tổng quan

Hệ thống có **3 vai trò người dùng** + **1 actor tự động**:

| Vai trò    | Mô tả                                                          |
|------------|----------------------------------------------------------------|
| `ADMIN`    | Quản trị hệ thống, cấu hình tổ chức và kiểm soát truy cập     |
| `MANAGER`  | Quản lý toàn bộ luồng nghiệp vụ, lập kế hoạch và phê duyệt   |
| `OPERATOR` | Thực thi trực tiếp: nhập/xuất kho, sản xuất, mua hàng         |
| `SYSTEM`   | Tự động hóa nội bộ (MRP, cảnh báo, tính variance, audit log)  |

> **Mô hình phân quyền:** Dynamic RBAC — quyền gắn với **scope** (Company / Plant / Warehouse),  
> không hardcode role vào business logic.

---

## 🛡️ ADMIN

**Trách nhiệm:** Cấu hình hệ thống, quản lý tổ chức và kiểm soát truy cập.  
ADMIN **không** can thiệp vào luồng sản xuất hay mua hàng.

### Master Data
| Quyền | Mô tả |
|-------|-------|
| `ITEM_WRITE` | Tạo / sửa / vô hiệu Item (vật tư, bán thành phẩm, thành phẩm) |
| `LOT_WRITE` | Quản lý Lot/Batch: trạng thái AVAILABLE · HOLD · REJECTED · EXPIRED |
| `BOM_WRITE` | Tạo / sửa BOM Header & BOM Lines |
| `SUPPLIER_WRITE` | Tạo / sửa Supplier Master và Item Supplier |

### Organization
| Quyền | Mô tả |
|-------|-------|
| `COMPANY_WRITE` | Tạo / sửa Company |
| `PLANT_WRITE` | Tạo / sửa Plant thuộc Company |
| `WAREHOUSE_WRITE` | Tạo / sửa Warehouse thuộc Plant |

### Security & Access Control
| Quyền | Mô tả |
|-------|-------|
| `AUTH_LOGIN` | Đăng nhập / Đăng xuất / Refresh token |
| `USER_WRITE` | Tạo / sửa / vô hiệu hoá tài khoản người dùng |
| `ROLE_WRITE` | Tạo / sửa vai trò và gán permission cho role |
| `SCOPE_WRITE` | Định nghĩa Access Scope (Company / Plant / Warehouse) |
| `USER_ROLE_ASSIGN` | Gán / thu hồi vai trò cho người dùng |
| `AUDIT_LOG_VIEW` | Xem toàn bộ lịch sử audit log |

---

## 📋 MANAGER

**Trách nhiệm:** Quản lý và phê duyệt toàn bộ luồng nghiệp vụ — từ lập kế hoạch đến sản xuất và mua hàng.  
MANAGER **xem** master data nhưng **không cấu hình** hệ thống.

### Authentication
| Quyền | Mô tả |
|-------|-------|
| `AUTH_LOGIN` | Đăng nhập / Đăng xuất / Refresh token |

### Master Data (View & Manage)
| Quyền | Mô tả |
|-------|-------|
| `ITEM_READ` | Xem danh mục vật tư, cài đặt item |
| `BOM_READ` | Xem BOM và cây BOM |
| `SUPPLIER_READ` | Xem danh sách nhà cung cấp |
| `ORG_READ` | Xem cấu trúc tổ chức (Company / Plant / Warehouse) |

### Planning & MRP
| Quyền | Mô tả |
|-------|-------|
| `PLANNING_WRITE` | Tạo kế hoạch sản xuất (Production Plan) |
| `MRP_RUN` | Chạy tính toán MRP (Material Requirement Planning) |
| `REQUIREMENT_LINE_READ` | Xem danh sách nhu cầu vật tư sau MRP |
| `SUPPLY_SUGGESTION_APPROVE` | Duyệt / Từ chối gợi ý cung ứng từ MRP |
| `SUGGESTION_TO_WO` | Chuyển gợi ý MRP → Work Order |
| `SUGGESTION_TO_PR` | Chuyển gợi ý MRP → Purchase Requisition |

### Purchasing
| Quyền | Mô tả |
|-------|-------|
| `PR_APPROVE` | Duyệt / Từ chối Purchase Requisition |
| `PO_WRITE` | Tạo / Gửi / Hủy Purchase Order |

### Production Control
| Quyền | Mô tả |
|-------|-------|
| `WO_WRITE` | Tạo / Cập nhật Work Order |
| `WO_RELEASE` | Phát lệnh (RELEASED) / Huỷ Work Order |
| `WO_VARIANCE_READ` | Xem sai lệch kế hoạch vs thực tế (Variance) |

### Reports
| Quyền | Mô tả |
|-------|-------|
| `REPORT_INVENTORY_READ` | Xem dashboard tồn kho, cảnh báo hàng thấp |

---

## ⚙️ OPERATOR

**Trách nhiệm:** Thực thi trực tiếp các thao tác kho, sản xuất và nhận hàng mua.  
OPERATOR **không** phê duyệt, **không** cấu hình hệ thống.

### Authentication
| Quyền | Mô tả |
|-------|-------|
| `AUTH_LOGIN` | Đăng nhập / Đăng xuất / Refresh token |

### Inventory
| Quyền | Mô tả |
|-------|-------|
| `STOCK_BALANCE_READ` | Xem tồn kho, lịch sử movement |
| `STOCK_RECEIVE` | Nhập kho thủ công (RECEIVE movement) |
| `STOCK_ISSUE` | Xuất kho thủ công (ISSUE movement) |
| `STOCK_ADJUST` | Điều chỉnh tồn kho (ADJUST / reversal movement) |

### Purchasing Execution
| Quyền | Mô tả |
|-------|-------|
| `GR_POST` | Đăng nhận hàng (Goods Receipt) → tự động cập nhật tồn kho |

### Manufacturing Execution
| Quyền | Mô tả |
|-------|-------|
| `MATERIAL_RESERVE` | Đặt trước vật tư cho Work Order (reservation) |
| `MATERIAL_ISSUE_POST` | Cấp vật tư (Material Issue) → tự động ISSUE movement |
| `WIP_RECORD` | Ghi nhận WIP, phế liệu, làm lại (scrap / rework) |
| `PRODUCTION_RECEIPT_POST` | Nhận thành phẩm (Production Receipt) → tự động RECEIVE movement |
| `WO_VARIANCE_READ` | Xem sai lệch kế hoạch vs thực tế (Variance) |

### Reports
| Quyền | Mô tả |
|-------|-------|
| `REPORT_INVENTORY_READ` | Xem dashboard tồn kho, cảnh báo hàng thấp |

---

## 🤖 SYSTEM (Tự động hóa)

Không phải người dùng – là các **luồng xử lý nội bộ** được kích hoạt tự động.

| Tác vụ tự động | Trigger |
|----------------|---------|
| Thu hồi / Xoay vòng JWT token | Khi refresh token được gọi |
| Kiểm tra Circular Reference BOM | Trước khi Activate BOM |
| Phân rã BOM đa cấp (Explosion) | Khi chạy MRP hoặc tạo Work Order |
| Tính nhu cầu vật tư (MRP) | Khi Manager trigger Run MRP |
| Phát hiện Shortage | Sau khi MRP chạy xong |
| Cập nhật tồn kho (stock_balances) | Sau mỗi RECEIVE / ISSUE / ADJUST movement |
| Tính Variance (planned vs actual) | Sau khi Post Material Issue hoặc Production Receipt |
| Cảnh báo tồn kho thấp | Sau khi stock_balances cập nhật |
| Ghi Audit Log | Sau mỗi thao tác nghiệp vụ quan trọng |

---

## Ma trận RACI tóm tắt

> `R` = Responsible · `A` = Accountable · `C` = Consulted · `I` = Informed

| Thao tác chính                     | ADMIN | MANAGER | OPERATOR | SYSTEM |
|------------------------------------|:-----:|:-------:|:--------:|:------:|
| **Đăng nhập / Logout**             |   A   |    I    |    R     |   R    |
| **Quản lý User & Phân quyền**      |   A   |    C    |    I     |        |
| **Cấu hình tổ chức**               |   A   |    C    |    I     |        |
| **Quản lý Item Master**            |   C   |    A    |    R     |        |
| **Nhập kho (Receive)**             |   I   |    A    |    R     |        |
| **Xuất kho / Cấp vật tư (Issue)**  |   I   |    A    |    R     |        |
| **Điều chỉnh tồn kho (Adjust)**    |   C   |    A    |    R     |        |
| **Cảnh báo tồn kho thấp**          |   I   |    I    |          |   R    |
| **Tạo & Quản lý BOM**              |   I   |    A    |    R     |        |
| **Activate BOM**                   |   I   |    A    |    C     |        |
| **BOM Explosion**                  |       |    A    |    I     |   R    |
| **Tạo & Duyệt kế hoạch SX**        |   I   |    A    |    R     |        |
| **Tính nhu cầu vật tư (MRP)**      |       |    A    |    I     |   R    |
| **Phát hiện Shortage**             |       |    A    |    C     |   R    |
| **Tạo & Release Work Order**       |   I   |    A    |    R     |        |
| **Cấp vật tư & Cập nhật WIP**      |   I   |    C    |    R     |        |
| **Nhận thành phẩm (Receipt)**      |   I   |    A    |    R     |        |
| **Tính Variance**                  |       |    A    |    I     |   R    |
| **Quản lý Supplier**               |   C   |    A    |    R     |        |
| **PR & Duyệt mua hàng**            |   I   |    A    |    R     |        |
| **PO & Nhận hàng (GR)**            |   I   |    A    |    R     |        |
| **Báo cáo tổng hợp**               |   I   |    A    |    C     |   R    |
| **Ghi & Xem Audit Log**            |   A   |    C    |          |   R    |

---

## Quy tắc quan trọng

```
✅ Mỗi thao tác có ĐÚNG 1 ô A (Accountable).
✅ SYSTEM không bao giờ là A – con người luôn chịu trách nhiệm cuối cùng.
✅ ADMIN không tham gia luồng sản xuất / mua hàng.
✅ OPERATOR không phê duyệt bất kỳ tài liệu nghiệp vụ nào.
✅ Mọi movement (RECEIVE / ISSUE / ADJUST) phải có Idempotency-Key.
✅ Lot HOLD / REJECTED / EXPIRED không được dùng cho sản xuất.
✅ Chỉ BOM ACTIVE mới được dùng cho Planning / Work Order.
```

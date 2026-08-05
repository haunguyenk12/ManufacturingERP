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
>
> 🔴 **[`C2-3`, 2026-08-04] Có HAI cơ chế kiểm quyền khác nhau, dễ nhầm khi test:**
> `@permissionGuard.hasResourceAccess(auth, code, 'COMPANY'|'PLANT'|'WAREHOUSE', id)` đi theo cây
> Company→Plant→Warehouse — role có permission qua **bất kỳ** scope nào phủ resource đó là qua.
> `@permissionGuard.hasPermission(auth, code)` (dùng cho quyền **không có resource cụ thể**: toàn bộ
> `PERM_ORG_READ`/`PERM_ORG_MANAGE`/`PERM_ACCESS_MANAGE`/`PERM_UOM_*`) **chỉ** đọc assignment có
> **`AccessScope.scopeType = GLOBAL`** — role có permission mà scope là `PLANT`/`COMPANY` vẫn bị 403.
> Trong dữ liệu hiện tại **chỉ `admin`** có scope `GLOBAL_ALL`. ⇒ Test tay các quyền nhóm thứ hai
> **phải dùng `admin`**, không dùng account seed `manager.a`/`operator.a` (`dev-seed.sql`, scope
> `PLANT`) — 403 ở đó không có nghĩa là seed permission sai. Chi tiết: `module/uom/CLAUDE.md`.

---

## 🔴 [`C2-5`, 2026-08-04] `V41` — seed đã được sửa cho khớp tài liệu này

**Vấn đề:** từ `V7` tới `V15`, các seed sớm cấp permission cho **riêng `ADMIN`**
(`WHERE r.code = 'ADMIN'`); từ `V17` trở đi mới cấp đủ `IN ('ADMIN','MANAGER','OPERATOR')`. Kết quả:
**12 permission lõi** chỉ `ADMIN` có, trong khi tài liệu này mô tả MANAGER/OPERATOR sử dụng chúng.

**Hệ quả đo được:** login một account `MANAGER` scope PLANT-A rồi gọi
`GET /api/v1/plants/{A}/work-orders` trả **403 `PERMISSION_DENIED` ngay trên plant của chính nó**
⇒ mọi account không phải `admin` đều không mở được các màn hình lõi.

**`V41` cấp thêm** (quyết định của user: **tài liệu này là nguồn, code là chỗ trôi**):

| Role | Permission được cấp thêm ở `V41` |
|---|---|
| `MANAGER` | `PERM_WORK_ORDER_READ` · `PERM_WORK_ORDER_MANAGE` · `PERM_WORK_ORDER_EXECUTE` · `PERM_BOM_READ` · `PERM_BOM_MANAGE` · `PERM_INVENTORY_READ` · `PERM_INVENTORY_MOVE` · `PERM_INVENTORY_MANAGE` · `PERM_ORG_READ` · `PERM_PLANNING_READ` |
| `OPERATOR` | `PERM_WORK_ORDER_READ` · `PERM_WORK_ORDER_EXECUTE` · `PERM_BOM_READ` · `PERM_INVENTORY_READ` · `PERM_INVENTORY_MOVE` · `PERM_ORG_READ` |

**Tập admin-only sau `V41` là ĐÚNG HAI:** `PERM_ORG_MANAGE` và `PERM_ACCESS_MANAGE` — hai quyền "cấu
hình hệ thống" mà mục MANAGER dưới đây loại trừ tường minh (*"MANAGER xem master data nhưng không cấu
hình hệ thống"*). Thêm vào tập này là **quyết định bảo mật**, không phải thủ tục: nó phải sửa đồng thời
ở `FlywayMigrationIT.migrate_v41_leavesNoCorePermissionGrantedToAdminAlone` và ở tài liệu này.

**Ba test canh, ở `FlywayMigrationIT`** (DB thật — `PermissionCatalogTest` **không** thay được: nó chỉ
chứng minh permission **có dòng trong bảng `permissions`**, còn "được cấp cho role nào" nằm ở
`role_permissions`):
- `migrate_v41_leavesNoCorePermissionGrantedToAdminAlone` — không permission nào chỉ superuser dùng được.
- `migrate_v41_grantsManagerTheCorePermissionsTheRolesDocPromises` — checklist dương cho MANAGER.
- `migrate_v41_keepsOperatorOutOfApprovalAndConfigurationPermissions` — separation of duties (cấm).

> ⚠️ **Hai chỗ tài liệu này tự mâu thuẫn, chưa sửa** (ghi ra để người đọc sau không tưởng là lỗi mới):
> 1. **Tên quyền cũ.** Các mục dưới đây còn dùng tên thiết kế ban đầu (`WO_WRITE`, `WO_RELEASE`,
>    `ITEM_READ`, `STOCK_RECEIVE`, `MATERIAL_ISSUE_POST`…) trong khi code dùng `PERM_WORK_ORDER_MANAGE`,
>    `PERM_INVENTORY_MOVE`… Ánh xạ nằm ở `V41` và ở bảng trên; **`PERM_*` trong code là tên thật**.
> 2. **Bảng RACI rộng hơn các mục chi tiết.** RACI cho OPERATOR là `R` ở "Quản lý Item Master" và
>    "Tạo & Release Work Order", nhưng mục OPERATOR **không** liệt kê quyền quản lý item hay tạo WO.
>    `V41` đi theo **các mục chi tiết** (hẹp hơn) + separation of duties, **không** theo RACI: tạo và
>    release work order giữ ở MANAGER. Muốn nới thêm thì đó là quyết định riêng.

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
| `PERM_UOM_MANAGE` | Tạo / sửa / activate / deactivate đơn vị tính (`C2-3`, `V43`). **Global** — không gắn company/plant |

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
| `ROLE_WRITE` | Tạo / sửa vai trò và gán permission cho role. **[`C2-4`]** Nay có đủ CRUD + lifecycle: `GET`/`PATCH /access/roles/{id}`, `POST .../activate`, `POST .../deactivate` — role `is_system` (ADMIN/MANAGER/OPERATOR) không deactivate được, kể cả bởi `admin` |
| `SCOPE_WRITE` | Định nghĩa Access Scope (Company / Plant / Warehouse). **[`C2-4`]** Cùng bộ CRUD + lifecycle: `GET`/`PATCH /access/scopes/{id}`, `POST .../activate`, `POST .../deactivate` |
| `USER_ROLE_ASSIGN` | Gán / thu hồi vai trò cho người dùng. **[`C2-4`]** `GET /access/assignments?userId=&roleId=&scopeId=` để tra cứu, cùng quyền `PERM_ACCESS_MANAGE` — không thêm permission mới |
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
| `PERM_UOM_READ` | Xem danh mục đơn vị tính |
| `PERM_UOM_MANAGE` | Tạo / sửa / activate / deactivate đơn vị tính (`C2-3`, `V43`) |

### Sales
| Quyền | Mô tả |
|-------|-------|
| `PERM_SALES_ORDER_READ` | Xem Sales Order và các dòng đơn hàng (`F3`, `V29`) |
| `PERM_SALES_ORDER_MANAGE` | Tạo / Confirm / Cancel Sales Order. **Confirm sinh independent demand cho MRP**, Cancel huỷ luôn demand còn `OPEN` (`F3`, `V29`). **[`C2-4`]** Cũng gác `PATCH /sales-orders/{id}` — sửa full-replace, chỉ áp dụng đơn `DRAFT` |

### Routing
| Quyền | Mô tả |
|-------|-------|
| `PERM_ROUTING_READ` | Xem routing và danh sách công đoạn (`F4`, `V31`) |
| `PERM_ROUTING_MANAGE` | Tạo / Activate / Deactivate routing. **Activate deactivate bản `ACTIVE` cũ của cùng item**; routing `ACTIVE` là điều kiện bắt buộc để convert proposal MAKE thành Work Order (`F4`, `V31`) |

### Work Center
| Quyền | Mô tả |
|-------|-------|
| `PERM_WORK_CENTER_READ` | Xem danh sách work center của plant (`C2-6`, `V45`) |
| `PERM_WORK_CENTER_MANAGE` | Tạo / sửa / activate / deactivate work center. Cùng tầng quyền với `PERM_ROUTING_MANAGE` — work center là master data sản xuất, không phải cấu trúc tổ chức (`C2-6`, `V45`) |

### Shift & Work Calendar
| Quyền | Mô tả |
|-------|-------|
| `PERM_SHIFT_READ` | Xem danh sách shift của plant (`C2-7`, `V47`) |
| `PERM_SHIFT_MANAGE` | Tạo / sửa / activate / deactivate shift. Cùng tầng quyền với `PERM_WORK_CENTER_MANAGE` (`C2-7`, `V47`) |
| `PERM_WORK_CALENDAR_READ` | Xem lịch tuần + exception của plant (`C2-7`, `V47`) |
| `PERM_WORK_CALENDAR_MANAGE` | Tạo / sửa / activate / deactivate work calendar. Permission **riêng** với Shift dù cùng module — hai resource tách biệt trên wire (`C2-7`, `V47`) |

> Endpoint `GET /sales-orders/planning-demands` **không** dùng quyền sales — nó là màn hình của
> planner nên gác bằng `PERM_MRP_RUN` (spec §2.2 gán `PLANNING_RUN` cho endpoint này).

### Planning & MRP
| Quyền | Mô tả |
|-------|-------|
| `PLANNING_WRITE` | Tạo kế hoạch sản xuất (Production Plan) |
| `MRP_RUN` | Chạy tính toán MRP (Material Requirement Planning) |
| `REQUIREMENT_LINE_READ` | Xem danh sách nhu cầu vật tư sau MRP |
| `SUPPLY_SUGGESTION_APPROVE` | Duyệt / Từ chối gợi ý cung ứng từ MRP |
| `SUGGESTION_TO_WO` | Chuyển gợi ý MRP → Work Order |
| `SUGGESTION_TO_PR` | Chuyển gợi ý MRP → Purchase Requisition |

> **`F5-B` (2026-07-28) đổi đường dẫn, không đổi permission.** `/api/v1/mrp/runs**` →
> `/api/v1/planning-runs**`; `/api/v1/mrp/suggestions/{id}/**` → `/api/v1/supply-suggestions/{id}/**`
> (kể cả `convert-to-purchase-requisition` của module `purchasing`). Chuỗi `PERM_MRP_RUN` /
> `PERM_MRP_READ` / `PERM_SUPPLY_SUGGESTION_MANAGE` giữ nguyên — **không** có migration permission mới.

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
| `PERM_MATERIAL_ISSUE_OVERRIDE` | Cấp vật tư **vượt định mức BOM** kèm lý do bắt buộc (Gate 1b) |
| `PERM_PRODUCTION_RECEIPT_APPROVE` | Duyệt / Từ chối Production Receipt đang chờ (Gate 1c) |
| `PERM_PRODUCTION_EXECUTION_MANAGE` | Báo cáo sản lượng xưởng (`F5`, `V33`) – MANAGER cũng có để sửa/bổ sung thay ca |
| `PERM_PRODUCTION_EXECUTION_READ` | Xem lịch sử báo cáo sản lượng (`F5`, `V33`) |

### Quality Control
| Quyền | Mô tả |
|-------|-------|
| `PERM_QUALITY_DISPOSITION` | QC disposition: giải phóng lot thành phẩm khỏi `HOLD` sang `AVAILABLE` hoặc `REJECTED` (`F2`, `V27`). **[F6]** Nhánh `AVAILABLE` còn tăng `fulfilledQuantity` của Sales Order line được allocate — fulfillment là **hệ quả** của quyền này, `F6` **không** thêm permission mới (spec §7.1). **[D5]** Áp dụng cho **cả** output không lot-tracked (phán quyết trên receipt thay vì trên lot; `REJECTED` rút hàng bằng `ADJUST_OUT`) — `D5` cũng **không** thêm permission mới |

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

### Sales
| Quyền | Mô tả |
|-------|-------|
| `PERM_SALES_ORDER_READ` | Xem Sales Order để truy nguồn gốc Work Order (`F3`, `V29`). **Không** có `PERM_SALES_ORDER_MANAGE` — confirm/cancel đơn hàng là cam kết thương mại, thuộc MANAGER |

### Routing
| Quyền | Mô tả |
|-------|-------|
| `PERM_ROUTING_READ` | Xem công đoạn của routing đang chạy trên Work Order (`F4`, `V31`). **Không** có `PERM_ROUTING_MANAGE` — routing là master data ràng buộc thứ được phép sản xuất, thuộc MANAGER |

### Work Center
| Quyền | Mô tả |
|-------|-------|
| `PERM_WORK_CENTER_READ` | Xem work center của operation đang chạy (`C2-6`, `V45`). **Không** có `PERM_WORK_CENTER_MANAGE` — tạo/sửa/deactivate work center là cấu hình master data, thuộc MANAGER |

### Shift & Work Calendar
| Quyền | Mô tả |
|-------|-------|
| `PERM_SHIFT_READ` | Xem shift của work center đang chạy (`C2-7`, `V47`). **Không** có `PERM_SHIFT_MANAGE` |
| `PERM_WORK_CALENDAR_READ` | Xem lịch tuần + exception của plant (`C2-7`, `V47`). **Không** có `PERM_WORK_CALENDAR_MANAGE` |

### UOM
| Quyền | Mô tả |
|-------|-------|
| `PERM_UOM_READ` | Xem danh mục đơn vị tính để hiển thị trên màn hình (`C2-3`, `V43`). **Không** có `PERM_UOM_MANAGE` — tạo/sửa/deactivate đơn vị tính là cấu hình master data, thuộc MANAGER |

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
| `PRODUCTION_RECEIPT_POST` | Tạo Production Receipt (`DRAFT`) và `submit` sang `PENDING_APPROVAL` – chưa vào tồn kho |
| `PERM_PRODUCTION_EXECUTION_MANAGE` | **Báo cáo sản lượng xưởng** (good / scrap / rework) – đây là thao tác làm Work Order tiến triển và `COMPLETED` (`F5`, `V33`) |
| `PERM_PRODUCTION_EXECUTION_READ` | Xem lịch sử báo cáo sản lượng của Work Order (`F5`, `V33`) |
| `WO_VARIANCE_READ` | Xem sai lệch kế hoạch vs thực tế (Variance) |

> **Separation of duties (P1 + F2):** OPERATOR **KHÔNG** có `PERM_MATERIAL_ISSUE_OVERRIDE`,
> `PERM_PRODUCTION_RECEIPT_APPROVE` và `PERM_QUALITY_DISPOSITION`. Người xuất/nhập không được tự
> duyệt phần vượt định mức hoặc thành phẩm của chính mình, và người sản xuất không được tự cho
> hàng của mình qua QC — cả ba quyền này chỉ cấp cho ADMIN và MANAGER (`V24`, `V27`).

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
| **Tạo & Quản lý Routing**          |   I   |    A    |    I     |        |
| **Activate Routing**               |   I   |    A    |    C     |        |
| **Tạo & Duyệt kế hoạch SX**        |   I   |    A    |    R     |        |
| **Tính nhu cầu vật tư (MRP)**      |       |    A    |    I     |   R    |
| **Phát hiện Shortage**             |       |    A    |    C     |   R    |
| **Tạo & Release Work Order**       |   I   |    A    |    R     |        |
| **Cấp vật tư & Cập nhật WIP**      |   I   |    C    |    R     |        |
| **Báo cáo sản lượng xưởng**        |   I   |    C    |    R     |        |
| **Duyệt xuất vượt định mức**       |   I   |    A    |          |        |
| **Gửi Receipt chờ duyệt**          |   I   |    C    |    R     |        |
| **Duyệt / Từ chối Receipt**        |   I   |    A    |          |        |
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
✅ Mỗi item chỉ có ĐÚNG 1 Routing ACTIVE; convert proposal MAKE bắt buộc phải có nó.
✅ Người gửi chứng từ không được là người duyệt chính chứng từ đó.
```

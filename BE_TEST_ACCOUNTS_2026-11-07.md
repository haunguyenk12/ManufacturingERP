# Tài khoản test — luồng tài khoản, mật khẩu và phân quyền

**BE_TEST_ACCOUNTS_2026-11-07 · v1 · 07/11/2026** — chỉ liệt kê tài khoản. Cách dựng môi trường và kịch bản test: `BE_GUIDE_ACCOUNT_PASSWORD_TEST_ENV_2026-11-07.md`.

> ⚠️ **Chỉ dùng cho môi trường test cô lập.** Mật khẩu ở đây là mật khẩu thử, nằm trong DB riêng của môi trường này; **không** có trên backend dev `:8080` và không được dùng cho môi trường thật.

## 1. Nơi dùng các tài khoản này

| | |
|---|---|
| API (qua Radmin VPN) | `http://26.154.206.236:18080/api` |
| Hộp thư Mailpit (đọc mã email) | `http://26.154.206.236:58025` |
| Đăng nhập | `POST /api/auth/v1/login` `{"username", "password"}` |
| Công ty | `QA-GD12` — `companyId` `bd62de8e-888e-4481-b59e-151b03805017` |
| Nhà máy `QA-A` | `plantId` `495525b1-33d0-43c7-a840-d52852905d02` |
| Nhà máy `QA-B` | `plantId` `02c9cf26-eb49-474e-ad29-f35d7329df68` |

Mỗi username chỉ có **một phiên**: đăng nhập lần hai sẽ đá phiên trước. Sai mật khẩu 5 lần ⇒ khoá username 15 phút (`423`).

## 2. Bảng tổng hợp

| Tài khoản | Mật khẩu | Trạng thái | Vai trò | Phạm vi | Số quyền |
|---|---|---|---|---|---|
| `admin` | `ApqAdmin!Local2026` | ACTIVE | ADMIN | toàn hệ thống (GLOBAL) | 80 |
| `qa_admin` | `QaAdmin@2026!` | ACTIVE | ADMIN | toàn hệ thống (GLOBAL) | 80 |
| `qa_only_a` | `QaUser@2026` | ACTIVE | MANAGER | chỉ nhà máy `QA-A` | 77 |
| `qa_only_b` | `QaUser@2026` | ACTIVE | MANAGER | chỉ nhà máy `QA-B` | 77 |
| `qa_manager_a` | `QaUser@2026` | ACTIVE | MANAGER | chỉ nhà máy `QA-A` | 77 |
| `qa_operator_a` | `QaUser@2026` | ACTIVE | OPERATOR | chỉ nhà máy `QA-A` | 24 |
| `qa_operator_b` | `QaUser@2026` | ACTIVE | OPERATOR | chỉ nhà máy `QA-B` | 24 |
| `qa_commercial_a` | `QaUser@2026` | ACTIVE | QA-ROLE-COMMERCIAL | chỉ nhà máy `QA-A` | 17 |
| `qa_readonly_a` | `QaUser@2026` | ACTIVE | QA-ROLE-READONLY | chỉ nhà máy `QA-A` | 19 |
| `qa_no_price` | `QaUser@2026` | ACTIVE | QA-ROLE-NO-PRICE | chỉ nhà máy `QA-A` | 73 |
| `qa_no_finance` | `QaUser@2026` | ACTIVE | QA-ROLE-NO-FINANCE | chỉ nhà máy `QA-A` | 64 |
| `qa_step_planning` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-PLANNING | chỉ nhà máy `QA-A` | 12 |
| `qa_step_purchasing` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-PURCHASING | chỉ nhà máy `QA-A` | 8 |
| `qa_step_receiving` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-RECEIVING | chỉ nhà máy `QA-A` | 7 |
| `qa_step_purchase_qc` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-PURCHASE-QC | chỉ nhà máy `QA-A` | 7 |
| `qa_step_workorder` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-WORKORDER | chỉ nhà máy `QA-A` | 9 |
| `qa_step_issue` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-ISSUE | chỉ nhà máy `QA-A` | 8 |
| `qa_step_execution` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-EXECUTION | chỉ nhà máy `QA-A` | 9 |
| `qa_step_receipt` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-RECEIPT | chỉ nhà máy `QA-A` | 6 |
| `qa_step_approval` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-APPROVAL | chỉ nhà máy `QA-A` | 8 |
| `qa_step_qc` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-QC | chỉ nhà máy `QA-A` | 7 |
| `qa_step_delivery` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-DELIVERY | chỉ nhà máy `QA-A` | 8 |
| `qa_step_invoice` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-INVOICE | chỉ nhà máy `QA-A` | 10 |
| `qa_step_payment` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-PAYMENT | chỉ nhà máy `QA-A` | 8 |
| `qa_step_costing` | `QaUser@2026` | ACTIVE | QA-ROLE-STEP-COSTING | chỉ nhà máy `QA-A` | 9 |
| `qa.newhire` | `An0ther!Pass9` | ACTIVE | OPERATOR mặc định, **không có phân công phạm vi** | không có | 0 (để thử luồng mật khẩu) |
| `qa.newhire2`, `qa.newhire3` | `Init1al!Pass` (mật khẩu ban đầu) | **PENDING_SETUP** | như trên | không có | chưa có token, đăng nhập sẽ vào luồng nhập mã email |

## 3. Tài khoản quản trị

| Tài khoản | Mật khẩu | userId |
|---|---|---|
| `admin` | `ApqAdmin!Local2026` | tạo bằng bootstrap lần đầu |
| `qa_admin` | `QaAdmin@2026!` | `fa5b3deb-9c8c-4863-850f-29b8b0c29ec0` |

Cả hai có vai trò **ADMIN**, phạm vi **GLOBAL_ALL** (không hết hạn), 80 quyền. Chỉ hai tài khoản này mở được các màn hình toàn cục (`GET /v1/companies`, `/uoms`, quản trị người dùng và phân quyền); mọi tài khoản bên dưới nhận `403` ở đó vì được gán phạm vi `PLANT`.

## 4. Giới hạn một nhà máy

### `qa_only_a`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `cabc683b-9271-490f-b0d6-ab95900356af`
- **Vai trò:** MANAGER  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 77
- **Quyền:** toàn bộ quyền của vai trò MANAGER (xem phần cuối file)

### `qa_only_b`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `844daeab-9baf-4336-be59-f448be37b86a`
- **Vai trò:** MANAGER  ·  **Phạm vi:** chỉ nhà máy `QA-B` (`02c9cf26-eb49-474e-ad29-f35d7329df68`)
- **Số quyền hiệu lực:** 77
- **Quyền:** toàn bộ quyền của vai trò MANAGER (xem phần cuối file)

## 5. Ma trận quyền của FE

### `qa_manager_a`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `3b304899-daab-4b48-b1bf-07d378668a25`
- **Vai trò:** MANAGER  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 77
- **Quyền:** toàn bộ quyền của vai trò MANAGER (xem phần cuối file)

### `qa_operator_a`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `ee3e3e17-182c-4b5b-ae99-8086e98b1614`
- **Vai trò:** OPERATOR  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 24
- **Quyền:** toàn bộ quyền của vai trò OPERATOR (xem phần cuối file)

### `qa_operator_b`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `f2cf5313-37ed-4a25-bb08-d76559b0d036`
- **Vai trò:** OPERATOR  ·  **Phạm vi:** chỉ nhà máy `QA-B` (`02c9cf26-eb49-474e-ad29-f35d7329df68`)
- **Số quyền hiệu lực:** 24
- **Quyền:** toàn bộ quyền của vai trò OPERATOR (xem phần cuối file)

### `qa_commercial_a`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `a74ff0d4-22c8-4086-bab9-4e5503c5fda1`
- **Vai trò:** QA-ROLE-COMMERCIAL  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 17
- **Quyền:** `PERM_CUSTOMER_MANAGE`, `PERM_CUSTOMER_PAYMENT_MANAGE`, `PERM_CUSTOMER_PAYMENT_POST`, `PERM_CUSTOMER_PAYMENT_READ`, `PERM_CUSTOMER_READ`, `PERM_DELIVERY_MANAGE`, `PERM_DELIVERY_POST`, `PERM_DELIVERY_READ`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_SALES_INVOICE_MANAGE`, `PERM_SALES_INVOICE_POST`, `PERM_SALES_INVOICE_READ`, `PERM_SALES_ORDER_MANAGE`, `PERM_SALES_ORDER_READ`, `PERM_SALES_PRICE_MANAGE`
- **Thấy giá bán:** có

### `qa_readonly_a`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `f291590a-3f95-43de-97d5-d4753358f420`
- **Vai trò:** QA-ROLE-READONLY  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 19
- **Quyền:** `PERM_BOM_READ`, `PERM_DELIVERY_READ`, `PERM_GOODS_RECEIPT_READ`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_MRP_READ`, `PERM_ORG_READ`, `PERM_PLANNING_READ`, `PERM_PRODUCTION_EXECUTION_READ`, `PERM_PRODUCTION_RECEIPT_READ`, `PERM_PURCHASE_ORDER_READ`, `PERM_PURCHASE_REQUISITION_READ`, `PERM_ROUTING_READ`, `PERM_SALES_ORDER_READ`, `PERM_SHIFT_READ`, `PERM_SUPPLIER_READ`, `PERM_WORK_CALENDAR_READ`, `PERM_WORK_CENTER_READ`, `PERM_WORK_ORDER_READ`
- **Thấy giá bán:** không

## 6. Thiếu quyền

### `qa_no_price`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `f084ea94-7ea9-407b-a4ef-51b52bcfdfb2`
- **Vai trò:** QA-ROLE-NO-PRICE  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 73
- **Quyền:** MANAGER trừ `PERM_SALES_INVOICE_MANAGE`, `PERM_SALES_INVOICE_POST`, `PERM_SALES_INVOICE_READ`, `PERM_SALES_PRICE_MANAGE`
- **Cố ý không thấy:** giá bán trên đơn bán (không có field `commercial`) và hoá đơn bán hàng
- **Thấy giá bán:** không

### `qa_no_finance`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `bbaf2290-e2db-45e6-b531-bf5c5b8fcbc2`
- **Vai trò:** QA-ROLE-NO-FINANCE  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 64
- **Quyền:** MANAGER trừ `PERM_COSTING_MANAGE`, `PERM_COSTING_READ`, `PERM_COST_RATE_MANAGE`, `PERM_CUSTOMER_PAYMENT_MANAGE`, `PERM_CUSTOMER_PAYMENT_POST`, `PERM_CUSTOMER_PAYMENT_READ`, `PERM_PRODUCTION_COST_CALCULATE`, `PERM_SALES_INVOICE_MANAGE`, `PERM_SALES_INVOICE_POST`, `PERM_SALES_INVOICE_READ`, `PERM_SALES_PRICE_MANAGE`, `PERM_WORK_ORDER_CLOSE`, `PERM_WORK_ORDER_VARIANCE_READ`
- **Cố ý không có:** giá bán, hoá đơn, thanh toán, giá thành chuẩn, giá thành sản xuất, variance, đóng lệnh
- **Thấy giá bán:** không

## 7. Quyền theo từng bước

### `qa_step_planning`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `65f267b1-40fa-4a49-8430-0a2426130d19`
- **Vai trò:** QA-ROLE-STEP-PLANNING  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 12
- **Bước:** Hoạch định: xem BOM/quy trình/đơn bán, chạy MRP, quản lý nhu cầu và đề xuất cung ứng
- **Quyền:** `PERM_BOM_READ`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_MRP_READ`, `PERM_MRP_RUN`, `PERM_ORG_READ`, `PERM_PLANNING_DEMAND_MANAGE`, `PERM_PLANNING_DEMAND_READ`, `PERM_PLANNING_READ`, `PERM_ROUTING_READ`, `PERM_SALES_ORDER_READ`, `PERM_SUPPLY_SUGGESTION_MANAGE`
- **Thấy giá bán:** không

### `qa_step_purchasing`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `98cd507e-aa6e-4f16-be80-aa4d7b711c53`
- **Vai trò:** QA-ROLE-STEP-PURCHASING  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 8
- **Bước:** Mua hàng: yêu cầu mua (PR) và đơn mua (PO), xem nhà cung cấp
- **Quyền:** `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_PURCHASE_ORDER_MANAGE`, `PERM_PURCHASE_ORDER_READ`, `PERM_PURCHASE_REQUISITION_MANAGE`, `PERM_PURCHASE_REQUISITION_READ`, `PERM_SUPPLIER_READ`
- **Thấy giá bán:** không

### `qa_step_receiving`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `ce4f0815-d88f-416b-b0f6-529c98a6eb98`
- **Vai trò:** QA-ROLE-STEP-RECEIVING  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 7
- **Bước:** Nhận hàng: tạo, sửa, ghi sổ phiếu nhận hàng theo PO
- **Quyền:** `PERM_GOODS_RECEIPT_MANAGE`, `PERM_GOODS_RECEIPT_POST`, `PERM_GOODS_RECEIPT_READ`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_PURCHASE_ORDER_READ`
- **Thấy giá bán:** không

### `qa_step_purchase_qc`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `2c77f043-0dbb-42c1-a24f-a784f5f9b528`
- **Vai trò:** QA-ROLE-STEP-PURCHASE-QC  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 7
- **Bước:** QC hàng mua: xem hàng đợi và quyết định chấp nhận/loại lô nhập
- **Quyền:** `PERM_GOODS_RECEIPT_READ`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_PURCHASE_ORDER_READ`, `PERM_PURCHASE_QC_MANAGE`, `PERM_PURCHASE_QC_READ`
- **Thấy giá bán:** không

### `qa_step_workorder`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `e8efd0af-84e5-47e5-a5e9-10177ea0bea2`
- **Vai trò:** QA-ROLE-STEP-WORKORDER  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 9
- **Bước:** Lệnh sản xuất: tạo, phát hành lệnh, giữ chỗ vật tư
- **Quyền:** `PERM_BOM_READ`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_MATERIAL_RESERVATION_MANAGE`, `PERM_ORG_READ`, `PERM_ROUTING_READ`, `PERM_WORK_CENTER_READ`, `PERM_WORK_ORDER_MANAGE`, `PERM_WORK_ORDER_READ`
- **Thấy giá bán:** không

### `qa_step_issue`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `ca02bd0c-adf6-489a-8601-a9fb66cc6aaf`
- **Vai trò:** QA-ROLE-STEP-ISSUE  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 8
- **Bước:** Cấp vật tư: xuất vật tư cho lệnh, hoàn vật tư chưa dùng
- **Quyền:** `PERM_INVENTORY_MOVE`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_MATERIAL_ISSUE_MANAGE`, `PERM_MATERIAL_RETURN_MANAGE`, `PERM_MATERIAL_RETURN_READ`, `PERM_ORG_READ`, `PERM_WORK_ORDER_READ`
- **Thấy giá bán:** không

### `qa_step_execution`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `53b74f28-3cc1-4802-95b7-da9525e5016b`
- **Vai trò:** QA-ROLE-STEP-EXECUTION  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 9
- **Bước:** Báo sản lượng: ghi nhận good/scrap/rework, xem dở dang (WIP)
- **Quyền:** `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_PRODUCTION_EXECUTION_MANAGE`, `PERM_PRODUCTION_EXECUTION_READ`, `PERM_WIP_MANAGE`, `PERM_WIP_READ`, `PERM_WORK_ORDER_EXECUTE`, `PERM_WORK_ORDER_READ`
- **Thấy giá bán:** không

### `qa_step_receipt`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `e733dcb4-d565-4da9-996b-c0532986d273`
- **Vai trò:** QA-ROLE-STEP-RECEIPT  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 6
- **Bước:** Phiếu nhập thành phẩm: tạo, gửi duyệt (không có quyền duyệt)
- **Quyền:** `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_PRODUCTION_RECEIPT_MANAGE`, `PERM_PRODUCTION_RECEIPT_READ`, `PERM_WORK_ORDER_READ`
- **Thấy giá bán:** không

### `qa_step_approval`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `2933060f-41a0-4dab-88d8-df1856700d7e`
- **Vai trò:** QA-ROLE-STEP-APPROVAL  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 8
- **Bước:** Duyệt: duyệt phiếu nhập thành phẩm, duyệt và override xuất vật tư vượt định mức
- **Quyền:** `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_MATERIAL_ISSUE_APPROVE`, `PERM_MATERIAL_ISSUE_OVERRIDE`, `PERM_ORG_READ`, `PERM_PRODUCTION_RECEIPT_APPROVE`, `PERM_PRODUCTION_RECEIPT_READ`, `PERM_WORK_ORDER_READ`
- **Thấy giá bán:** không

### `qa_step_qc`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `2aea7c72-c560-44bd-96fd-34bd0d6c7ff9`
- **Vai trò:** QA-ROLE-STEP-QC  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 7
- **Bước:** QC thành phẩm: xem phiếu đã duyệt và phán quyết giải phóng lô
- **Quyền:** `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_PRODUCTION_QC_READ`, `PERM_PRODUCTION_RECEIPT_READ`, `PERM_QUALITY_DISPOSITION`, `PERM_WORK_ORDER_READ`
- **Thấy giá bán:** không

### `qa_step_delivery`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `b900c41a-17d6-428f-b336-2e2323de2ad5`
- **Vai trò:** QA-ROLE-STEP-DELIVERY  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 8
- **Bước:** Giao hàng: tạo, sửa, ghi sổ phiếu giao hàng (không thấy giá)
- **Quyền:** `PERM_CUSTOMER_READ`, `PERM_DELIVERY_MANAGE`, `PERM_DELIVERY_POST`, `PERM_DELIVERY_READ`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_SALES_ORDER_READ`
- **Thấy giá bán:** không

### `qa_step_invoice`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `981fa4fd-81d8-43ab-ae1a-42c678c580f7`
- **Vai trò:** QA-ROLE-STEP-INVOICE  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 10
- **Bước:** Hoá đơn bán hàng: lập, ghi sổ hoá đơn, bổ sung giá (thấy giá bán)
- **Quyền:** `PERM_CUSTOMER_READ`, `PERM_DELIVERY_READ`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_SALES_INVOICE_MANAGE`, `PERM_SALES_INVOICE_POST`, `PERM_SALES_INVOICE_READ`, `PERM_SALES_ORDER_READ`, `PERM_SALES_PRICE_MANAGE`
- **Thấy giá bán:** có

### `qa_step_payment`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `6059a2d3-87b3-44b9-9b99-0689cb1adf79`
- **Vai trò:** QA-ROLE-STEP-PAYMENT  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 8
- **Bước:** Thanh toán khách hàng: lập, ghi sổ phiếu thu (có quyền đọc hoá đơn nên thấy giá bán)
- **Quyền:** `PERM_CUSTOMER_PAYMENT_MANAGE`, `PERM_CUSTOMER_PAYMENT_POST`, `PERM_CUSTOMER_PAYMENT_READ`, `PERM_CUSTOMER_READ`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_SALES_INVOICE_READ`
- **Thấy giá bán:** có

### `qa_step_costing`

- **Mật khẩu:** `QaUser@2026`  ·  **userId:** `1e519316-7b4d-4217-9872-47b48e2d7845`
- **Vai trò:** QA-ROLE-STEP-COSTING  ·  **Phạm vi:** chỉ nhà máy `QA-A` (`495525b1-33d0-43c7-a840-d52852905d02`)
- **Số quyền hiệu lực:** 9
- **Bước:** Giá thành: tính, chốt giá thành, đơn giá giờ, đóng lệnh, xem variance
- **Quyền:** `PERM_COSTING_READ`, `PERM_COST_RATE_MANAGE`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_PRODUCTION_COST_CALCULATE`, `PERM_WORK_ORDER_CLOSE`, `PERM_WORK_ORDER_READ`, `PERM_WORK_ORDER_VARIANCE_READ`
- **Thấy giá bán:** không

## 8. Tài khoản thử luồng mật khẩu

| Tài khoản | Mật khẩu | Trạng thái | Email | Dùng để |
|---|---|---|---|---|
| `qa.newhire` | `An0ther!Pass9` | ACTIVE | `qa.newhire@omniplant.vn` | thử đăng nhập, đổi mật khẩu, quên mật khẩu bằng mã |
| `qa.newhire2` | `Init1al!Pass` | PENDING_SETUP | `qa.newhire2@omniplant.vn` | thử đăng nhập lần đầu (nhận mã qua Mailpit) |
| `qa.newhire3` | `Init1al!Pass` | PENDING_SETUP | `qa.newhire3@omniplant.vn` | như trên |

Muốn thử đăng nhập lần đầu nhiều lượt: admin tạo tài khoản mới qua `POST /api/admin/users/v1/accounts/commands`. Một tài khoản chỉ đi qua bước setup được một lần.

## 9. Quyền của vai trò MANAGER (77 quyền)

`PERM_BOM_MANAGE`, `PERM_BOM_READ`, `PERM_CAPACITY_MANAGE`, `PERM_CAPACITY_READ`, `PERM_COSTING_MANAGE`, `PERM_COSTING_READ`, `PERM_COST_RATE_MANAGE`, `PERM_CUSTOMER_MANAGE`, `PERM_CUSTOMER_PAYMENT_MANAGE`, `PERM_CUSTOMER_PAYMENT_POST`, `PERM_CUSTOMER_PAYMENT_READ`, `PERM_CUSTOMER_READ`, `PERM_DATA_IMPORT_EXECUTE`, `PERM_DATA_IMPORT_READ`, `PERM_DELIVERY_MANAGE`, `PERM_DELIVERY_POST`, `PERM_DELIVERY_READ`, `PERM_GOODS_RECEIPT_MANAGE`, `PERM_GOODS_RECEIPT_POST`, `PERM_GOODS_RECEIPT_READ`, `PERM_INVENTORY_MANAGE`, `PERM_INVENTORY_MOVE`, `PERM_INVENTORY_READ`, `PERM_ITEM_MANAGE`, `PERM_ITEM_READ`, `PERM_MATERIAL_ISSUE_APPROVE`, `PERM_MATERIAL_ISSUE_MANAGE`, `PERM_MATERIAL_ISSUE_OVERRIDE`, `PERM_MATERIAL_RESERVATION_MANAGE`, `PERM_MATERIAL_RETURN_MANAGE`, `PERM_MATERIAL_RETURN_READ`, `PERM_MRP_READ`, `PERM_MRP_RUN`, `PERM_ORG_READ`, `PERM_PLANNING_DEMAND_MANAGE`, `PERM_PLANNING_DEMAND_READ`, `PERM_PLANNING_READ`, `PERM_PRODUCTION_COST_CALCULATE`, `PERM_PRODUCTION_EXECUTION_MANAGE`, `PERM_PRODUCTION_EXECUTION_READ`, `PERM_PRODUCTION_QC_READ`, `PERM_PRODUCTION_RECEIPT_APPROVE`, `PERM_PRODUCTION_RECEIPT_MANAGE`, `PERM_PRODUCTION_RECEIPT_READ`, `PERM_PURCHASE_ORDER_MANAGE`, `PERM_PURCHASE_ORDER_READ`, `PERM_PURCHASE_QC_MANAGE`, `PERM_PURCHASE_QC_READ`, `PERM_PURCHASE_REQUISITION_MANAGE`, `PERM_PURCHASE_REQUISITION_READ`, `PERM_QUALITY_DISPOSITION`, `PERM_ROUTING_MANAGE`, `PERM_ROUTING_READ`, `PERM_SALES_INVOICE_MANAGE`, `PERM_SALES_INVOICE_POST`, `PERM_SALES_INVOICE_READ`, `PERM_SALES_ORDER_MANAGE`, `PERM_SALES_ORDER_READ`, `PERM_SALES_PRICE_MANAGE`, `PERM_SHIFT_MANAGE`, `PERM_SHIFT_READ`, `PERM_SUPPLIER_MANAGE`, `PERM_SUPPLIER_READ`, `PERM_SUPPLY_SUGGESTION_MANAGE`, `PERM_UOM_MANAGE`, `PERM_UOM_READ`, `PERM_WIP_MANAGE`, `PERM_WIP_READ`, `PERM_WORK_CALENDAR_MANAGE`, `PERM_WORK_CALENDAR_READ`, `PERM_WORK_CENTER_MANAGE`, `PERM_WORK_CENTER_READ`, `PERM_WORK_ORDER_CLOSE`, `PERM_WORK_ORDER_EXECUTE`, `PERM_WORK_ORDER_MANAGE`, `PERM_WORK_ORDER_READ`, `PERM_WORK_ORDER_VARIANCE_READ`

## 10. Quyền của vai trò OPERATOR (24 quyền)

`PERM_BOM_READ`, `PERM_CAPACITY_READ`, `PERM_GOODS_RECEIPT_POST`, `PERM_INVENTORY_MOVE`, `PERM_INVENTORY_READ`, `PERM_ITEM_READ`, `PERM_MATERIAL_ISSUE_MANAGE`, `PERM_MRP_READ`, `PERM_ORG_READ`, `PERM_PLANNING_DEMAND_READ`, `PERM_PRODUCTION_EXECUTION_MANAGE`, `PERM_PRODUCTION_EXECUTION_READ`, `PERM_PRODUCTION_RECEIPT_MANAGE`, `PERM_PURCHASE_ORDER_READ`, `PERM_PURCHASE_REQUISITION_READ`, `PERM_ROUTING_READ`, `PERM_SALES_ORDER_READ`, `PERM_SHIFT_READ`, `PERM_UOM_READ`, `PERM_WIP_MANAGE`, `PERM_WORK_CALENDAR_READ`, `PERM_WORK_CENTER_READ`, `PERM_WORK_ORDER_EXECUTE`, `PERM_WORK_ORDER_READ`

## 11. Quyền của vai trò ADMIN (80 quyền)

Gồm toàn bộ quyền của MANAGER và thêm: `PERM_ACCESS_MANAGE`, `PERM_AUDIT_READ`, `PERM_ORG_MANAGE`.

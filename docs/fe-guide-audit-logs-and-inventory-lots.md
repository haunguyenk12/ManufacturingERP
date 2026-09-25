# Hướng dẫn tích hợp: Audit Logs (C2-1) & Inventory Lots (C2-2)

> 🔴 **CẬP NHẬT LỚN 2026-09-02 (track `AR-*`, refactor hạ tầng audit).** Response của
> `GET /audit-logs` và `GET /audit-logs/{id}` **thêm 11 field** và endpoint list **thêm 6 filter**.
> **Thuần additive — không field nào bị xoá hay đổi tên**, FE hiện tại không hỏng. Ba thay đổi FE
> **cần biết ngay**:
>
> 1. **`entityId` quay lại response.** Trước đó nó chỉ là query param, nên UI thấy được *loại* đối
>    tượng nhưng không có khoá ổn định nào để link sang hay lọc theo. Nay có ở cả list và detail.
> 2. **`plantId` bắt đầu có giá trị thật.** Cảnh báo cũ ở §1.4 ("filter plant luôn trả rỗng") **đã
>    hết đúng** cho audit sinh ra từ 2026-09-02 trở đi. Dòng lịch sử vẫn `null` — backend **không**
>    backfill, vì suy plant của một dòng cũ từ dữ liệu hôm nay là làm giả bản ghi lịch sử.
> 3. **`entities[]` (chỉ ở detail)** — mọi đối tượng mà một sự kiện chạm tới, không chỉ đối tượng nó
>    được đặt tên theo. Ví dụ `PERMISSION_GRANTED` trước đây chỉ ghi role và **bỏ mất permission**;
>    nay có đủ cả hai.
>
> Chi tiết ở §1.5.

> **Cập nhật 2026-08-17:** Audit field-level diff đã được triển khai. Với audit sinh ra sau bản cập
> nhật này, `changes[]` chứa `fieldName`, `oldValue`, `newValue`, `changeType`; các ghi chú cũ bên
> dưới nói mảng này "luôn rỗng" chỉ mô tả trạng thái trước ngày 2026-08-17.
>
> 🔴 **Sửa 2026-08-25 (`BACKEND_AUDIT_LOG_VALUE_CONTRACT_2026-08-25.md`):** `oldValue`/`newValue`
> **luôn** là `string` hoặc `null`. Bản 2026-08-17 trả kiểu JSON gốc, nên một snapshot có cấu trúc
> (ví dụ `componentLines` của `WORK_ORDER_CREATED`) ra tới FE dưới dạng object và làm sập màn hình
> chi tiết. Nay: object/array → chuỗi JSON compact, số/boolean → chuỗi, chữ → không kèm dấu nháy,
> `null` → `null`.

> Ngày viết: 2026-08-06 · Đối tượng: FE team
> Phạm vi: đúng 2 tính năng vừa hoàn thành, backend đang chờ FE tích hợp — **không** phải bản tổng
> hợp toàn bộ API. Envelope/pagination/auth chung: `docs/api-guide-for-frontend.md §2`.
> Bối cảnh đầy đủ + Q&A trước đó với FE: `docs/capstone2-api-gap-response.md §10` và `§11`.

---

## 0. Việc chung trước khi đọc tiếp

Cả 2 API dưới đây dùng đúng contract chung của toàn hệ thống — nếu FE đã tích hợp các API khác rồi
thì không có gì mới ở tầng này:

- **Envelope**: mọi response đều `{code, result, message}` (lỗi có thêm `errors[]`).
- **Header**: `Authorization: Bearer <accessToken>` như mọi endpoint có bảo vệ khác.
- **Phân trang**: `page`/`size`/`sortBy`/`sortDir` → `result` là `PageResult` (`content`, `page`,
  `size`, `totalElements`, `totalPages`, `first`, `last`).
- **Lỗi validate query/body**: `400 VALIDATION_ERROR` kèm `errors[{field, message}]`.

---

## 1. Audit Logs — `GET /audit-logs`, `GET /audit-logs/{auditLogId}`

### 1.1 Ai gọi được

🔴 **ADMIN only.** MANAGER/OPERATOR gọi sẽ nhận `403 PERMISSION_DENIED` — đây là quyết định có chủ
đích (audit trail lộ IP/user-agent/lịch sử hành động của **mọi** user trong hệ thống), không phải
thiếu sót cần báo bug.

### 1.2 Danh sách — `GET /audit-logs`

```
GET /api/v1/audit-logs?actorUserId=&entityType=&entityId=&action=&plantId=&traceId=&from=&to=&page=&size=&sortBy=&sortDir=
```

Mọi filter đều **tuỳ chọn**, kết hợp với nhau bằng AND:

| Param | Kiểu | Ghi chú |
|---|---|---|
| `actorUserId` | UUID | Ai thực hiện hành động |
| `entityType` | string | Ví dụ `"WorkOrder"`, `"BomHeader"` — tên class entity, phân biệt hoa/thường |
| `entityId` | string | ID của entity đó (dạng string vì một số entity dùng mã, không phải UUID) |
| `action` | enum `AuditAction` | Ví dụ `LOGIN`, `WORK_ORDER_CREATED`, `UOM_UPDATED`... danh sách đầy đủ xem Swagger (`@Tag "Audit Logs"`). Gửi giá trị không tồn tại trong enum → `400 VALIDATION_ERROR`, **không phải** danh sách rỗng |
| `plantId` | UUID | 🔴 Xem cảnh báo ở §1.4 — filter này **luôn trả rỗng** hôm nay |
| `traceId` | string | Đối chiếu với header `X-Trace-Id` của request gây ra hành động |
| `from`, `to` | ISO datetime | Khoảng `createdAt`, ví dụ `2026-08-01T00:00:00Z` |
| `page`, `size`, `sortBy`, `sortDir` | | Mặc định `sortBy=createdAt`, `sortDir=desc` (mới nhất trước) |

Response — mỗi phần tử trong `content[]`:

```jsonc
{
  "auditId": "a1b2c3d4-...",
  "userId": "9f8e7d6c-...",
  "username": "admin",
  "action": "WORK_ORDER_CREATED",
  "entityType": "WorkOrder",
  "entityName": "WO-2026-001",
  "description": null,
  "status": "SUCCESS",
  "clientIp": "10.0.0.5",
  "userAgent": "Mozilla/5.0 ...",
  "traceId": "a9c41e1a07534f1d",
  "plantId": null,
  "createdAt": "2026-08-06T09:12:33.120Z"
}
```

Danh sách **không** có `changes[]` trong mỗi dòng — xem lý do ở §1.4.

### 1.3 Chi tiết — `GET /audit-logs/{auditLogId}`

Trả về đúng các field ở trên **cộng thêm** `changes[]`:

```jsonc
{
  "auditId": "a1b2c3d4-...",
  "userId": "9f8e7d6c-...",
  "username": "admin",
  "action": "WORK_ORDER_UPDATED",
  "entityType": "WorkOrder",
  "entityName": "WO-2026-001",
  "description": null,
  "status": "SUCCESS",
  "clientIp": "10.0.0.5",
  "userAgent": "Mozilla/5.0 ...",
  "traceId": "a9c41e1a07534f1d",
  "plantId": null,
  "createdAt": "2026-08-06T09:12:33.120Z",
  "changes": []
}
```

`auditLogId` không tồn tại → `404 ENTITY_NOT_FOUND`.

`entityId` vẫn là query parameter để lọc chính xác nhưng không còn xuất hiện trong response.
`entityName` là snapshot tên/mã/số chứng từ tại thời điểm thao tác; audit lịch sử trước migration
`V65` trả `null` vì backend không suy diễn tên hiện tại thành dữ liệu lịch sử.

### 1.4 Hai cảnh báo cũ — cả hai nay đã HẾT ĐÚNG

> Giữ lại nguyên văn để đối chiếu, vì UI có thể vẫn đang code theo chúng.

1. ~~**`changes[]` luôn là mảng rỗng.**~~ Hết đúng từ **2026-08-17**: field-level diff đã được ghi
   thật. Audit trước ngày đó vẫn rỗng.
2. ~~**`plantId` trên mọi dòng đều là `null`.**~~ Hết đúng từ **2026-09-02**: `plantId` (và
   `companyId`, `warehouseId`) được điền từ chính lệnh nghiệp vụ. **Audit lịch sử vẫn `null`** — nếu
   UI lọc theo plant, kết quả sẽ chỉ gồm sự kiện từ 2026-09-02 trở đi, và đó là hành vi đúng chứ
   không phải mất dữ liệu.

### 1.5 Field và filter mới (2026-09-02, thuần additive)

**Field mới trên cả list và detail:**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `entityId` | string \| null | **Khôi phục.** Khoá ổn định của đối tượng chính — thứ để link sang màn hình chi tiết |
| `outcome` | `"SUCCESS"` \| `"FAILURE"` | Dạng chính tắc của `status`. **`status` vẫn còn**, giá trị y hệt |
| `reasonCode` | string \| null | Lý do máy đọc được. Với sự kiện thất bại, đây là `code` trong error envelope (ví dụ `INSUFFICIENT_AVAILABLE_STOCK`) — **đừng parse `description`** |
| `source` | `HTTP` \| `AUTH` \| `SCHEDULED_JOB` \| `MESSAGE` \| `BATCH` \| `SYSTEM` | Nguồn sự kiện. `null` với audit lịch sử |
| `httpMethod`, `requestPath` | string \| null | Request đã tạo ra sự kiện |
| `companyId`, `warehouseId` | UUID \| null | Phạm vi, cùng cách `plantId` |
| `occurredAt` | ISO instant | **Lúc hành động xảy ra.** `createdAt` là lúc dòng được ghi xuống. Hai mốc lệch nhau vài trăm mili-giây là bình thường (pipeline bất đồng bộ). Với audit lịch sử, `occurredAt` = `createdAt` |

**Chỉ ở detail:**

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `entities[]` | `{relation, entityType, entityId, entityName}[]` | Đúng **1** phần tử `relation = "PRIMARY"` (trùng với `entityType`/`entityId`/`entityName` phẳng) + n phần tử `"RELATED"`. Rỗng với audit lịch sử |
| `metadata` | string \| null | JSON đã lọc do chính lệnh cung cấp. Hiện dùng cho **`{"noOp":true}`** — lệnh chạy thành công nhưng **không đổi gì** (ví dụ cấp một quyền mà role đã có). UI nên hiện khác với một thay đổi thật |

**Filter mới trên `GET /audit-logs`:** `outcome`, `source`, `companyId`, `warehouseId`,
`relatedEntityType`, `relatedEntityId`.

`relatedEntityType`/`relatedEntityId` trả lời câu **"mọi sự kiện đã chạm tới đối tượng này"**, kể cả
sự kiện được đặt tên theo thứ khác — ví dụ lọc `relatedEntityType=Permission&relatedEntityId=<id>` để
xem toàn bộ lịch sử cấp/thu hồi của một quyền. Cột phẳng cũ **không** trả lời được câu này.

**Hai thay đổi hành vi nhỏ:**

- **`sortBy` nay có allowlist:** `occurredAt` (mặc định), `createdAt`, `action`, `username`,
  `outcome`. Giá trị khác trả **400 `VALIDATION_ERROR`** thay vì 500 như trước.
- **`from > to` trả 400** thay vì im lặng trả trang rỗng — trang rỗng là câu trả lời dễ gây hiểu nhầm
  nhất mà endpoint này có thể đưa ra.

---

## 2. Inventory Lots — `GET /inventory/lots`, `GET /inventory/lots/{lotId}`, `POST /inventory/lots/{lotId}/status`

### 2.1 Ai gọi được

Dùng lại quyền đã có sẵn — **không** có quyền mới nào phải xin cấp:

- `GET /inventory/lots`, `GET /inventory/lots/{lotId}` → cần `PERM_INVENTORY_READ`.
- `POST /inventory/lots/{lotId}/status` → cần `PERM_INVENTORY_MOVE`.

### 2.2 Danh sách lot trong một kho — `GET /inventory/lots`

```
GET /api/v1/inventory/lots?warehouseId=&itemId=&status=&search=&expiryFrom=&expiryTo=&page=&size=&sortBy=&sortDir=
```

| Param | Kiểu | Ghi chú |
|---|---|---|
| `warehouseId` | UUID | 🔴 **Bắt buộc** — xem lý do ở §2.5 |
| `itemId` | UUID | Tuỳ chọn |
| `status` | enum `LotStatus` (`AVAILABLE`/`HOLD`/`REJECTED`/`EXPIRED`) | Tuỳ chọn |
| `search` | string | Tuỳ chọn — tìm gần đúng, không phân biệt hoa/thường trên `lotCode` |
| `expiryFrom`, `expiryTo` | ISO datetime | Tuỳ chọn — lọc theo `expiresAt` |
| `page`, `size`, `sortBy`, `sortDir` | | Mặc định `sortBy=updatedAt`, `sortDir=desc` |

Response — mỗi phần tử trong `content[]`:

```jsonc
{
  "lotId": "11111111-...",
  "itemId": "22222222-...",
  "itemCode": "RM-001",
  "itemName": "Steel Coil",
  "warehouseId": "33333333-...",
  "warehouseCode": "WH1",
  "warehouseName": "Warehouse 1",
  "lotCode": "LOT-2026-0731-01",
  "status": "HOLD",
  "onHandQuantity": 500.000000,
  "reservedQuantity": 0.000000,
  "availableQuantity": 500.000000,
  "manufactureDate": "2026-07-31T08:00:00Z",
  "expiresAt": null,
  "sourceMovementType": "RECEIVE",
  "sourceReferenceType": "WORK_ORDER",
  "sourceReferenceId": "44444444-...",
  "sourceAt": "2026-07-31T08:00:00Z",
  "version": 0,
  "createdAt": "2026-07-31T08:00:00Z",
  "updatedAt": "2026-07-31T08:00:00Z"
}
```

Ghi chú field:
- `manufactureDate` là **alias** của ngày nhận hàng — không phải cột riêng, không có "ngày sản xuất
  thật" tách biệt trong hệ thống hiện tại.
- `sourceMovementType`/`sourceReferenceType`/`sourceReferenceId`/`sourceAt` cho biết lot này sinh ra
  từ đâu, lấy từ dòng ghi kho `RECEIVE` sớm nhất của lot:
  - `sourceReferenceType = "WORK_ORDER"` + `sourceReferenceId` là `workOrderId` → lot sinh từ
    Production Receipt của work order đó (dùng `workOrderId` này để gọi tiếp
    `GET /production-receipts?workOrderId=` nếu cần xem chi tiết receipt/QC).
  - `sourceReferenceType = "GOODS_RECEIPT"` + `sourceReferenceId` là `goodsReceiptId` → lot nhận từ
    goods receipt của một PO.
  - Cả 4 field này có thể là `null` nếu không tìm thấy dòng `RECEIVE` gốc (hiếm, lot cũ/chỉnh tay).

### 2.3 Chi tiết một lot — `GET /inventory/lots/{lotId}?warehouseId=`

FE phải truyền lại `warehouseId` đang dùng ở màn danh sách:

```http
GET /api/v1/inventory/lots/{lotId}?warehouseId={selectedWarehouseId}
```

Với request có `warehouseId`, backend kiểm tra `PERM_INVENTORY_READ` theo warehouse/plant/company
và chỉ trả balance cùng source movement thuộc kho đó. Chế độ không truyền param chỉ dành cho
company/global/admin và trả toàn bộ balances để giữ tương thích với client quản trị cũ.

```jsonc
{
  "lotId": "11111111-...",
  "itemId": "22222222-...",
  "itemCode": "RM-001",
  "itemName": "Steel Coil",
  "lotCode": "LOT-2026-0731-01",
  "status": "HOLD",
  "manufactureDate": "2026-07-31T08:00:00Z",
  "expiresAt": null,
  "sourceMovementType": "RECEIVE",
  "sourceReferenceType": "WORK_ORDER",
  "sourceReferenceId": "44444444-...",
  "sourceAt": "2026-07-31T08:00:00Z",
  "version": 0,
  "createdAt": "2026-07-31T08:00:00Z",
  "updatedAt": "2026-07-31T08:00:00Z",
  "balances": [
    {
      "warehouseId": "33333333-...",
      "warehouseCode": "WH1",
      "warehouseName": "Warehouse 1",
      "onHandQuantity": 500.000000,
      "reservedQuantity": 0.000000,
      "availableQuantity": 500.000000
    }
  ]
}
```

🔴 **Không có field `warehouseId` ở cấp ngoài.** Khi FE truyền `warehouseId`, `balances[]` có đúng
một phần tử của kho đó. Lot không tồn tại, hoặc lot không có balance tại warehouse đã chọn, trả
`404 ENTITY_NOT_FOUND`; warehouse ngoài scope trả `403 PERMISSION_DENIED`.

### 2.4 Đổi trạng thái lot — `POST /inventory/lots/{lotId}/status`

```jsonc
// Request
{
  "warehouseId": "33333333-...",
  "newStatus": "REJECTED",
  "reason": "Phát hiện hàng lỗi khi kiểm tra định kỳ",
  "referenceType": null,
  "referenceId": null
}
```

- `warehouseId`: **bắt buộc** — chọn từ `balances[].warehouseId` của chi tiết lot (§2.3).
- `newStatus`: **bắt buộc**, chỉ nhận `AVAILABLE` / `HOLD` / `REJECTED`. Gửi `EXPIRED` → `422
  OPERATION_NOT_ALLOWED` (chưa có nghiệp vụ nào chuyển tay sang hết hạn).
- `reason`: **bắt buộc**, không được để trống.
- `referenceType`/`referenceId`: tuỳ chọn, tự do — dùng nếu UI muốn gắn thao tác này với một chứng
  từ nội bộ của FE (không bắt buộc điền).
- Header `Idempotency-Key`: tuỳ chọn nhưng **khuyến khích gửi** cho thao tác ghi này, giống mọi POST
  đụng tồn kho khác trong hệ thống (gửi lại cùng key + cùng payload → nhận lại đúng kết quả cũ, không
  ghi trùng).

Response thành công — trả về `InventoryLotResponse` (đúng shape ở §2.2) phản ánh trạng thái mới.

**Lỗi cần xử lý riêng:**

| HTTP | `code` | Khi nào | FE làm gì |
|---|---|---|---|
| `409` | `LOT_NOT_ELIGIBLE` | Lot đang `HOLD` từ Production Receipt **chưa qua QC** — xem §2.5, business rule quan trọng nhất của API này | Dẫn user sang màn hình QC disposition thay vì cho đổi trạng thái trực tiếp |
| `422` | `OPERATION_NOT_ALLOWED` | `newStatus = EXPIRED` | Không cho chọn `EXPIRED` trong UI ngay từ đầu |
| `400` | `VALIDATION_ERROR` | Thiếu `reason`, thiếu `warehouseId`, hoặc `newStatus` không hợp lệ | Validate form trước khi gửi |
| `404` | `ENTITY_NOT_FOUND` | `lotId` không tồn tại | |

### 2.5 Hai business rule quan trọng nhất — đọc kỹ trước khi code

**(a) Lot `HOLD` sinh từ Production Receipt bắt buộc phải qua QC disposition, không đổi trạng thái
trực tiếp được.** Đây là điều FE đã xác nhận với backend trước khi làm API này — **giữ nguyên đúng
như đã thống nhất**, không có gì đổi khác đi:

- Nếu lot đang `HOLD` **vì mới được sản xuất ra và chưa qua QC**, gọi
  `POST /inventory/lots/{lotId}/status` với `newStatus` khác `HOLD` sẽ trả `409 LOT_NOT_ELIGIBLE`.
  UI phải dẫn user sang endpoint QC đã có sẵn từ trước (`F2`):
  ```
  POST /api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/qc-disposition
  ```
  (Lấy `workOrderId` từ `sourceReferenceId` của chính lot đó nếu `sourceReferenceType =
  "WORK_ORDER"` — xem §2.2.)
- Nếu lot đang `HOLD` **vì lý do khác** (ví dụ nhân viên kho tự tay đưa vào `HOLD` để cách ly tạm
  thời qua chính API này), thì **không** bị chặn — `POST .../status` cho đổi trạng thái tự do như
  bình thường. Hệ thống phân biệt được hai trường hợp này dựa trên lịch sử QC thật của lot, không chỉ
  dựa vào việc lot có nguồn gốc sản xuất hay không — nên nếu một lot đã từng qua QC hợp lệ một lần rồi
  sau đó bị đưa lại `HOLD` thủ công, nó **sẽ không** bị chặn lần nữa.
- Gợi ý UX: khi gặp `409 LOT_NOT_ELIGIBLE`, hiện thông báo dạng "Lot này cần được QC trước khi đổi
  trạng thái" kèm nút điều hướng sang màn QC disposition, thay vì chỉ hiện lỗi chung chung.

**(b) Một lot có thể có hàng ở nhiều kho cùng lúc.** Vì vậy:
- Danh sách (`GET /inventory/lots`) **bắt buộc** phải chọn 1 kho qua `warehouseId` — không có chế độ
  "xem tất cả kho" cho danh sách lot (giống cách `/inventory/balances` đã hoạt động).
- Chi tiết từ màn list phải gọi `GET /inventory/lots/{lotId}?warehouseId={selectedWarehouseId}`.
  Response `balances[]` chỉ có kho đang xem; không gọi lại endpoint thiếu param cho Manager/Operator.
- Khi đổi trạng thái (`POST .../status`), UI phải cho user chọn **đúng kho** muốn thao tác (thường là
  kho đang xem trong màn hình chi tiết) rồi gửi `warehouseId` đó lên.

---

## 3. Checklist tích hợp nhanh

- [ ] Audit: màn hình danh sách gọi `GET /audit-logs`, hiển thị `changes[]` rỗng là bình thường
- [ ] Audit: **không** hiển thị bộ lọc theo plant cho tới khi có thông báo tiếp theo (luôn trả rỗng)
- [ ] Audit: chỉ hiện menu/route này cho user có role ADMIN (tránh gọi API rồi nhận 403)
- [ ] Inventory Lots: màn hình danh sách yêu cầu chọn kho trước khi gọi API
- [ ] Inventory Lots: khi mở detail, truyền lại chính `warehouseId` của màn danh sách
- [ ] Inventory Lots: màn hình chi tiết hiển thị đúng theo `balances[]`, không giả định 1 kho
- [ ] Inventory Lots: form đổi trạng thái bắt validate `reason` bắt buộc, không cho chọn `EXPIRED`
- [ ] Inventory Lots: xử lý riêng `409 LOT_NOT_ELIGIBLE` → điều hướng sang QC disposition
- [ ] Inventory Lots: gửi `Idempotency-Key` cho `POST .../status`

---

## 4. Tài liệu liên quan

- Envelope/pagination/auth chung, danh sách đầy đủ mã lỗi: `docs/api-guide-for-frontend.md`
- Bối cảnh quyết định thiết kế (vì sao ADMIN-only, vì sao chặn HOLD...) + lịch sử Q&A với FE:
  `docs/capstone2-api-gap-response.md §10` (Audit), `§11` (Inventory Lots)
- Swagger UI (danh sách đầy đủ `AuditAction`, thử API trực tiếp): `/swagger-ui.html` trên môi trường
  đang chạy

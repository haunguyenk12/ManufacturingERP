# Backend Handoff — Dashboard API Requirements

> Gửi đội Backend OmniPlant  
> Ngày: 2026-08-14  
> Mức ưu tiên: MVP  
> FE owner: OmniPlant Frontend  
> OpenAPI live đã kiểm tra: `http://26.154.206.236:8080/v3/api-docs`

## 1. Mục đích

Frontend chuẩn bị chuyển `/dashboard` từ mock sang API thật trong MVP. Hai report endpoint hiện có là nền tảng phù hợp, nhưng response hiện thiếu một số field hiển thị. FE đề nghị Backend xác nhận semantics và bổ sung DTO để Dashboard chỉ cần một aggregate request, không phải gọi N+1 sang Item, Warehouse, UOM và User.

## 2. Endpoint hiện có đã xác nhận

### 2.1 Inventory Dashboard

```http
GET /api/v1/reports/inventory-dashboard
  ?scopeType=PLANT
  &scopeId={plantUuid}
```

- `scopeType`: bắt buộc, enum `COMPANY | PLANT | WAREHOUSE`.
- `scopeId`: bắt buộc, UUID.
- Response: `ApiResponseInventoryDashboardResponse`.

### 2.2 Low Stock

```http
GET /api/v1/reports/low-stock
  ?scopeType=PLANT
  &scopeId={plantUuid}
  &status=LOW_STOCK
```

- `status`: tùy chọn, enum `OK | LOW_STOCK | REORDER_NEEDED`.
- Response: `ApiResponseListInventoryAlertLineResponse`.

### 2.3 Related APIs

- `GET /api/v1/inventory/movements` — yêu cầu `warehouseId`, không phù hợp để lấy movements toàn Plant nếu FE phải tự lặp qua mọi kho.
- `GET /api/v1/audit-logs` — có thể tái sử dụng cho Admin; không yêu cầu endpoint Dashboard Audit riêng trong MVP.

## 3. Contract hiện tại và khoảng trống

### 3.1 `InventoryDashboardResponse`

Backend hiện trả:

```text
scopeType, scopeId, companyId,
totalItemCount, totalWarehouseCount,
okCount, lowStockCount, reorderNeededCount,
shortageSummary,
topLowStockLines,
recentMovements
```

Các field tổng hợp là đúng hướng. FE cần Backend xác nhận thêm các định nghĩa ở mục 5.

### 3.2 `InventoryAlertLineResponse`

Backend hiện trả:

```text
settingId, itemId, itemCode, itemName,
warehouseId, warehouseCode, warehouseName,
safetyStock, reorderPoint, leadTimeDays,
availableQuantity, status
```

Field còn thiếu để render UI chính xác:

| Field đề nghị | Kiểu | Bắt buộc | Lý do |
|---|---|---:|---|
| `uomCode` | string | Có | Mọi quantity phải có đơn vị hiển thị |
| `shortageQuantity` | decimal | Có | Tránh FE tự đoán công thức shortage |
| `onHandQuantity` | decimal | Nên có | Giúp giải thích available và debug tồn |
| `reservedQuantity` | decimal | Nên có | Giúp giải thích available và shortage |
| `safetyStockGapQuantity` | decimal | Tùy chọn | Hiển thị chi tiết nếu status dựa trên safety stock |
| `reorderPointGapQuantity` | decimal | Tùy chọn | Hiển thị chi tiết nếu status dựa trên reorder point |

Nếu `shortageQuantity` chính thức luôn bằng `max(0, reorderPoint - availableQuantity)`, vui lòng ghi rõ trong OpenAPI description và test contract.

### 3.3 `recentMovements`

Aggregate hiện dùng `StockMovementResponse`, chỉ có IDs và dữ liệu kỹ thuật:

```text
movementId, itemId, warehouseId, lotId, lotCode,
movementType, direction, quantity, reason,
referenceType, referenceId, idempotencyKey, createdAt
```

UI cần các business label sau:

| Field đề nghị | Kiểu | Bắt buộc | Lý do |
|---|---|---:|---|
| `itemCode` | string | Có | Nhận diện vật tư |
| `itemName` | string | Có | Nội dung chính của movement card |
| `uomCode` | string | Có | Đơn vị của quantity |
| `warehouseCode` | string | Có | Nhận diện kho |
| `warehouseName` | string | Có | Hiển thị thân thiện |
| `actorUserId` | UUID/null | Tùy chọn | Truy vết người thao tác |
| `actorUsername` | string/null | Nên có | Hiển thị mà không gọi User API |
| `referenceNo` | string/null | Nên có | Số chứng từ nghiệp vụ; `referenceId` không thân thiện |

Khuyến nghị tạo nested DTO riêng, ví dụ `DashboardRecentMovementResponse`, thay vì mở rộng mọi response movement nếu Backend muốn giữ endpoint Inventory hiện tại tối giản.

## 4. Response đề nghị cho MVP

FE ưu tiên giữ endpoint hiện tại và hoàn thiện aggregate DTO:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "scopeType": "PLANT",
    "scopeId": "e5dd4166-46b1-4dd0-942b-ca8fee642b47",
    "companyId": "17c29678-a859-4dbb-bef0-70ae7f5d4b1c",
    "generatedAt": "2026-08-14T05:00:00Z",
    "totalItemCount": 5,
    "totalWarehouseCount": 1,
    "okCount": 2,
    "lowStockCount": 1,
    "reorderNeededCount": 2,
    "shortageSummary": {
      "alertLineCount": 3,
      "lowStockLineCount": 1,
      "reorderNeededLineCount": 2,
      "safetyStockGapQuantity": 20.000000,
      "reorderPointGapQuantity": 35.000000
    },
    "topLowStockLines": [
      {
        "settingId": "uuid",
        "itemId": "uuid",
        "itemCode": "OMD-RM-WOOD",
        "itemName": "Mặt bàn gỗ sồi tự nhiên",
        "uomCode": "PCS",
        "warehouseId": "uuid",
        "warehouseCode": "WH-RM",
        "warehouseName": "Kho nguyên vật liệu",
        "onHandQuantity": 97.000000,
        "reservedQuantity": 20.000000,
        "availableQuantity": 77.000000,
        "safetyStock": 20.000000,
        "reorderPoint": 100.000000,
        "shortageQuantity": 23.000000,
        "leadTimeDays": 7,
        "status": "REORDER_NEEDED"
      }
    ],
    "recentMovements": [
      {
        "movementId": "uuid",
        "movementType": "ISSUE",
        "direction": "OUT",
        "itemId": "uuid",
        "itemCode": "OMD-RM-WOOD",
        "itemName": "Mặt bàn gỗ sồi tự nhiên",
        "uomCode": "PCS",
        "warehouseId": "uuid",
        "warehouseCode": "WH-RM",
        "warehouseName": "Kho nguyên vật liệu",
        "lotId": "uuid",
        "lotCode": "GO-SOI-202608-A",
        "quantity": 20.000000,
        "actorUserId": "uuid",
        "actorUsername": "admin",
        "referenceType": "MANUAL_ISSUE",
        "referenceId": "uuid-or-business-id",
        "referenceNo": "ISSUE-20260814-001",
        "reason": "Xuất vật tư cho lệnh sản xuất",
        "createdAt": "2026-08-14T04:30:00Z"
      }
    ]
  }
}
```

### Field mới bắt buộc tối thiểu

Nếu cần giới hạn thay đổi cho MVP, FE cần tối thiểu:

1. `generatedAt` ở response tổng;
2. `uomCode` và `shortageQuantity` trong low-stock line;
3. `itemCode`, `itemName`, `uomCode`, `warehouseCode`, `warehouseName` trong recent movement;
4. xác nhận chính thức semantics/count/order ở mục 5.

`actorUsername` và `referenceNo` có thể nullable trong đợt đầu, nhưng không nên buộc FE gọi N+1 để lấy chúng.

## 5. Các câu hỏi Backend cần xác nhận

### 5.1 Count semantics

1. `totalItemCount` là số Item ACTIVE duy nhất trong Company/scope hay số item có stock/settings?
2. `okCount`, `lowStockCount`, `reorderNeededCount` đếm Item duy nhất hay dòng Item–Warehouse?
3. Ba count trạng thái có loại trừ nhau hoàn toàn không?
4. `totalWarehouseCount` có loại kho INACTIVE không?

FE đề nghị count cảnh báo là số dòng **Item–Warehouse**, vì reorder point và safety stock được cấu hình theo Warehouse.

### 5.2 Availability và lot status

Vui lòng xác nhận:

```text
availableQuantity = eligibleOnHand - reservedQuantity
```

Trong đó `eligibleOnHand` chỉ gồm lot AVAILABLE. HOLD và REJECTED phải bị loại; lot hết hạn cũng bị loại nếu backend có trạng thái/expiry policy tương ứng.

Không trả available âm; sử dụng `max(0, ...)` nếu reservation tạm thời vượt eligible on-hand do trạng thái dữ liệu.

### 5.3 Alert status

Backend cần ghi rõ điều kiện cho:

- `OK`;
- `LOW_STOCK`;
- `REORDER_NEEDED`.

Đề nghị hai trạng thái cảnh báo loại trừ nhau và `REORDER_NEEDED` nghiêm trọng hơn `LOW_STOCK`.

### 5.4 Ordering và limit

Đề nghị:

- `topLowStockLines`: `REORDER_NEEDED` trước, shortage giảm dần, rồi `itemCode`, `warehouseCode`; mặc định 5 dòng.
- `recentMovements`: `createdAt desc`, rồi `movementId desc`; mặc định 5 dòng.
- Có thể thêm query `lowStockLimit` và `movementLimit`, default 5, max 20. Nếu Backend giữ limit cố định, vui lòng document rõ.

### 5.5 Scope và quyền

Vui lòng xác nhận:

- permission bắt buộc là `PERM_INVENTORY_READ`;
- user chỉ được gọi scope nằm trong effective assignment của họ;
- `scopeType=PLANT` và `scopeId=plantUuid` phải cross-check với `X-Plant-Id`;
- COMPANY scope tổng hợp mọi Plant/Warehouse được phép;
- WAREHOUSE scope chỉ tổng hợp đúng một Warehouse;
- scope không thuộc quyền trả 403 với error code ổn định, không trả dữ liệu rỗng giả.

## 6. Error contract cần công bố trong OpenAPI

Backend vui lòng xác nhận code thực tế cho các trường hợp:

| HTTP | Trường hợp | Code mong muốn ổn định |
|---:|---|---|
| 400 | thiếu/sai `scopeType`, `scopeId`, limit hoặc status | `VALIDATION_ERROR` hoặc code cụ thể đã chuẩn hóa |
| 401 | access token hết hạn/không hợp lệ | code auth hiện có |
| 403 | thiếu `PERM_INVENTORY_READ` | `PERMISSION_DENIED` |
| 403 | scope không thuộc effective assignment | `SCOPE_ACCESS_DENIED` hoặc code tương đương |
| 404 | scope UUID không tồn tại | `RESOURCE_NOT_FOUND` hoặc code tương đương |
| 409 | `X-Plant-Id` không khớp Plant scope | code cross-check hiện có |

FE sẽ quyết định behavior theo `code`, không parse `message`.

## 7. OpenAPI requirements

Backend cần bổ sung:

- description cho từng count và quantity;
- enum đầy đủ cho `movementType`, `direction`, `status`, `scopeType`;
- nullable/required rõ ràng cho actor, lot và reference;
- decimal scale/precision hoặc example thống nhất;
- response schema cho 400/401/403/404/409, không chỉ schema 200;
- example response cho COMPANY, PLANT và WAREHOUSE;
- xác nhận timezone của `generatedAt`/`createdAt` là ISO-8601 UTC Instant.

## 8. Acceptance cases Backend cần test

1. PLANT có nhiều Warehouse: count và top lines tổng hợp đúng.
2. WAREHOUSE scope: không lẫn dữ liệu kho khác.
3. COMPANY scope: chỉ gồm Plant/Warehouse trong Company.
4. Cùng Item ở hai Warehouse được đánh giá thành hai alert line độc lập.
5. Lot AVAILABLE đóng góp vào available; HOLD/REJECTED không đóng góp.
6. Reservation làm giảm available đúng một lần.
7. Không có Item-Warehouse setting: quy tắc fallback hoặc loại khỏi alert được document rõ.
8. Movement trả đủ label mà không cần hydrate.
9. Hai movement cùng thời điểm có thứ tự ổn định.
10. User có permission nhưng không có scope phù hợp nhận 403.
11. Scope không tồn tại nhận 404.
12. Dữ liệu rỗng vẫn trả `SUCCESS`, count 0 và mảng rỗng.

## 9. Definition of Ready cho Frontend

Frontend bắt đầu migration khi Backend cung cấp đủ:

- OpenAPI live có contract cuối;
- câu trả lời mục 5;
- field tối thiểu ở mục 4;
- error code/status ở mục 6;
- một Plant demo có ít nhất một `OK`, một `LOW_STOCK`, một `REORDER_NEEDED` và một recent movement;
- xác nhận `PERM_INVENTORY_READ` + scope behavior.

Sau đó FE dự kiến cần 2–2.5 ngày để hoàn thành adapter, hooks, UI alignment, tests và live acceptance.

## 10. Phản hồi đề nghị từ Backend

Backend có thể phản hồi trực tiếp theo checklist:

```text
[ ] Xác nhận count semantics
[ ] Xác nhận availability và lot exclusion
[ ] Xác nhận alert status rules
[ ] Xác nhận ordering/limit
[ ] Xác nhận permission/scope/error codes
[ ] Chọn mở rộng StockMovementResponse hoặc tạo DashboardRecentMovementResponse
[ ] Bổ sung generatedAt/uomCode/shortageQuantity/movement labels
[ ] Publish OpenAPI mới
[ ] Cung cấp Plant demo và trace ID của một request thành công
```

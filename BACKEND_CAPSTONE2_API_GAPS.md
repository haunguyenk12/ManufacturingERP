# OmniPlant Backend — Capstone 2 API Gaps

> Ngày lập: 2026-08-04  
> Đối tượng: Backend team  
> Baseline: đối chiếu OpenAPI snapshot `plans/openapi/openapi-2026-08-04.json` với scope Capstone 2 và nhu cầu FE.

## 1. Trạng thái đã xác nhận

- Backend thông báo lỗi auth/refresh và lỗi 500 đã được sửa; FE sẽ regression-test trước khi đóng issue.
- Core production APIs đã tồn tại cho Inventory, Sales Order, Planning/MRP, Work Order, Material Issue,
  Production Execution, Production Receipt và QC disposition.
- Các API dưới đây là phần còn thiếu hoặc chưa đủ để FE bỏ mock hoàn toàn.

## 2. Quy ước chung cho API mới

Tất cả endpoint phải tuân theo:

- Prefix `/api/v1`.
- `Authorization: Bearer <accessToken>`.
- Permission code được công bố rõ trong OpenAPI.
- Endpoint có `plantId` tường minh phải cross-check `X-Plant-Id`.
- UUID cho toàn bộ entity ID.
- Quantity/decimal theo `NUMERIC(19,6)` và serialize nhất quán.
- Page response dùng `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.
- Response envelope:

```json
{
  "code": "",
  "message": "",
  "result": {}
}
```

- Mutation retry-safe phải nhận `Idempotency-Key` và replay cùng key/cùng payload.
- Error phải có stable `code`, message cụ thể, field errors và `X-Trace-Id`.
- Lifecycle command phải được backend kiểm tra trạng thái; FE không phải nguồn sự thật.

## 3. P0 — API chặn Capstone 2 live

### 3.1 UOM Master

OpenAPI hiện chưa có UOM endpoint. FE cần tối thiểu:

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/uoms` | List/search/filter status, paginated |
| POST | `/uoms` | Tạo UOM |
| GET | `/uoms/{uomId}` | Chi tiết |
| PATCH | `/uoms/{uomId}` | Sửa tên/mô tả khi hợp lệ |
| DELETE | `/uoms/{uomId}` | Xóa khi chưa được tham chiếu |
| POST | `/uoms/{uomId}/activate` | Kích hoạt |
| POST | `/uoms/{uomId}/deactivate` | Ngừng sử dụng |

Field tối thiểu: `uomId`, `code`, `name`, `description`, `status`, `createdAt`, `updatedAt`.

Business rules:

- `code` duy nhất và immutable sau khi tạo.
- Không delete/deactivate nếu làm hỏng Item/BOM/transaction đang tham chiếu; trả conflict cụ thể.

### 3.2 Inventory Lot lifecycle

Hiện chỉ có lot data nằm trong balance/movement; chưa đủ cho Inventory Lots và QC traceability.

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/inventory/lots` | List theo warehouse/item/status/lotCode/expiry, paginated |
| GET | `/inventory/lots/{lotId}` | Detail, quantities và source genealogy |
| POST | `/inventory/lots/{lotId}/status` | Chuyển HOLD/AVAILABLE/REJECTED theo rule |

Query list tối thiểu:

- `warehouseId` bắt buộc hoặc contract scope thay thế được mô tả rõ.
- `itemId`, `status`, `search`, `expiryFrom`, `expiryTo`, `page`, `size`, `sortBy`, `sortDir`.

Response tối thiểu:

- Lot/item/warehouse IDs và codes.
- `onHandQuantity`, `reservedQuantity`, `availableQuantity`.
- `status`, manufacture/expiry dates.
- Source type/reference, Production Receipt/QC trace khi có.

Business rules:

- HOLD và REJECTED không đóng góp vào available stock/MRP.
- QC disposition chỉ thực hiện một lần nếu current receipt contract yêu cầu.
- Status mutation cần reason, permission và idempotency.

### 3.3 Audit Logs

OpenAPI hiện không có Audit API.

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/audit-logs` | Danh sách/filter audit, paginated |
| GET | `/audit-logs/{auditLogId}` | Chi tiết old/new value và trace |

Filters tối thiểu:

- `entityType`, `entityId`, `action`, `actorUserId`, `plantId`, `traceId`, `from`, `to`, `page`, `size`.

Response tối thiểu:

- Actor, action, entity type/id/code.
- Old value, new value hoặc field changes dạng JSON.
- Timestamp, Plant, request/trace ID.

### 3.4 Work Center

OpenAPI hiện không có Work Center API.

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/plants/{plantId}/work-centers` | List/search/filter, paginated |
| POST | `/plants/{plantId}/work-centers` | Tạo Work Center |
| GET | `/work-centers/{workCenterId}` | Chi tiết |
| PATCH | `/work-centers/{workCenterId}` | Sửa |
| DELETE | `/work-centers/{workCenterId}` | Xóa khi chưa được tham chiếu |
| POST | `/work-centers/{workCenterId}/activate` | Activate |
| POST | `/work-centers/{workCenterId}/deactivate` | Deactivate |

Field tối thiểu:

- ID, Plant, code, name, description.
- `capacityUnitType`: MACHINE, LINE hoặc LABOR_TEAM.
- Positive integer `capacityUnits`.
- Calendar reference và Shift references.
- Status, timestamps và optimistic version.

Không cho cross-Plant calendar/shift reference; không deactivate khi còn active planned load nếu business rule yêu cầu.

### 3.5 Shift và Work Calendar

OpenAPI hiện không có Calendar/Shift API.

Đề nghị tách resource:

| Method | Endpoint |
|---|---|
| GET/POST | `/plants/{plantId}/shifts` |
| GET/PATCH/DELETE | `/shifts/{shiftId}` |
| POST | `/shifts/{shiftId}/activate` |
| POST | `/shifts/{shiftId}/deactivate` |
| GET/POST | `/plants/{plantId}/work-calendars` |
| GET/PATCH/DELETE | `/work-calendars/{calendarId}` |
| POST | `/work-calendars/{calendarId}/activate` |
| POST | `/work-calendars/{calendarId}/deactivate` |

Shift cần intervals và breaks. Work Calendar cần:

- Effective from/to.
- Weekly schedule theo weekday và shift IDs.
- Non-working dates/exceptions.
- Plant scope và status.

Backend phải tính net working windows nhất quán, kể cả shift qua đêm và ngày nghỉ.

### 3.6 Capacity Board và schedule adjustment

OpenAPI hiện không có Capacity API.

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/plants/{plantId}/capacity-board` | Load/capacity theo horizon và Work Center |
| POST | `/work-orders/{workOrderId}/operations/{operationId}/schedule-adjustments` | Manager điều chỉnh một operation |

Capacity query tối thiểu: `from`, `to`, `workCenterId`, `status`, `page`, `size`.

Response cần:

- Operation, WO, Work Center và Plant references.
- Planned start/end.
- Setup/run/capacity minutes.
- Available capacity, existing load, utilization và overload flag.
- Calendar/shift exceptions ảnh hưởng.
- Version dùng cho concurrency check.

Adjustment cần planned start/end mới, reason và expected version. Backend không tự động dời các operation khác; trả warning/conflict khi sequence, calendar hoặc capacity không hợp lệ.

## 4. P1 — Hoàn thiện contract đang có

### 4.1 BOM deactivate

Đã có Activate nhưng chưa có Deactivate:

```text
POST /boms/{bomId}/deactivate
```

Yêu cầu reason tùy policy, idempotency, state validation và không thay đổi BOM snapshot của Work Order cũ.

### 4.2 Routing deactivate

Đã có Activate nhưng chưa thấy Deactivate:

```text
POST /routings/{routingId}/deactivate
```

Không thay đổi Routing snapshot đã capture; Work Order mới không được dùng Routing inactive.

### 4.3 Sales Order DRAFT update

FE có edit DRAFT nhưng OpenAPI chưa có mutation phù hợp:

```text
PATCH /sales-orders/{salesOrderId}
```

Chỉ cho sửa DRAFT; backend quản lý `lineNo`, validate quantity/due date và trả conflict nếu version cũ.

### 4.4 Roles lifecycle

Hiện có create và permission grant/revoke. Cần xác nhận/bổ sung:

```text
GET    /access/roles/{roleId}
PATCH  /access/roles/{roleId}
DELETE /access/roles/{roleId}
POST   /access/roles/{roleId}/activate
POST   /access/roles/{roleId}/deactivate
```

Không cho xóa system role hoặc role còn assignment nếu không có migration policy.

### 4.5 Access Scope lifecycle và assignment reads

Hiện có create scope, add resources, create/delete assignment nhưng thiếu khả năng dựng lại workspace quản trị.

Đề nghị:

```text
GET    /access/scopes/{scopeId}
PATCH  /access/scopes/{scopeId}
DELETE /access/scopes/{scopeId}
POST   /access/scopes/{scopeId}/activate
POST   /access/scopes/{scopeId}/deactivate
GET    /access/assignments?userId=&roleId=&scopeId=&page=&size=
```

Làm rõ Warehouse-level resources có được trả đầy đủ trong `/auth/me.scopes[]` hay không.

### 4.6 Material Issue over-BOM approval

Backend đã có batch/override fields nhưng cần chốt một trong hai contract:

1. Manager có permission override và post trực tiếp kèm `overrideReason`; hoặc
2. Operator submit request → Manager approve/reject bằng endpoint riêng.

OpenAPI phải mô tả rõ permission, trạng thái, reason, stock posting time và response khi chưa được duyệt.

## 5. P2 — Contract quality và acceptance support

- Công bố stable DTO cho Production Execution candidates và Production Receipt candidates.
- Đảm bảo `/work-orders/{id}/variance` mô tả đầy đủ material, time và output variance.
- Bổ sung trace ID nhất quán trong header hoặc response cho mọi lỗi.
- Cung cấp ít nhất hai Plant và account theo vai trò để test isolation.
- Cấu hình CORS cho origin FE, hoặc chính thức hóa việc dùng same-origin proxy.
- Cập nhật OpenAPI ngay khi endpoint/DTO thay đổi; FE không suy đoán URL hoặc field.

## 6. Acceptance checklist cho backend

- [x] Auth refresh rotation và concurrent refresh pass. *(rotation/RTR/absolute timeout ✅ `D8a`/`D8b`; concurrent refresh ✅ **đã sửa 2026-08-05** — advisory lock `SET NX PX` + breadcrumb kết quả rotate, không grace window, xem `CLAUDE.md §0.32`, bất biến `B95`)*
- [~] Không còn lỗi 500 không có trace trong core happy path. *(3 lỗi được báo đã sửa, xem `CLAUDE.md §0.24`; **chưa** rà toàn bộ endpoint nên không tuyên bố "không còn")*
- [x] UOM CRUD/lifecycle có OpenAPI và permission. *(`C2-3`, 2026-08-04 — 7 endpoint, `V42`+`V43`)*
- [ ] Inventory Lot list/detail/status có OpenAPI và business rules. *(`C2-2` — bị chặn, chờ FE trả lời `docs/capstone2-api-gap-response.md §5` câu 2)*
- [ ] Audit list/detail có OpenAPI. *(`C2-1` — bị chặn, chờ FE trả lời `docs/capstone2-api-gap-response.md §5` câu 1)*
- [x] Work Center CRUD/lifecycle có OpenAPI. *(`C2-6`, 2026-08-05 — 7 endpoint, xem `FRONTEND_ALIGNMENT_ROADMAP.md §8.6`)*
- [x] Shift/Calendar CRUD/lifecycle có OpenAPI. *(`C2-7`, 2026-08-05 — 14 endpoint, xem `FRONTEND_ALIGNMENT_ROADMAP.md §8.7`)*
- [x] Capacity Board và schedule adjustment có OpenAPI. *(`C2-8` — ✅ hoàn thành 2026-08-05: `GET /plants/{plantId}/capacity-board` + `POST /work-orders/{id}/operations/{id}/schedule-adjustments`, `V48`+`V49`. Xem `FRONTEND_ALIGNMENT_ROADMAP.md §8.7b`, `CLAUDE.md §0.30`)*
- [x] BOM/Routing deactivate được chốt. *(`C2-0`, 2026-08-04 — đã có từ trước, chỉ lệch verb `DELETE`, xem `FRONTEND_ALIGNMENT_ROADMAP.md §8.2`)*
- [x] SO DRAFT update được chốt. *(`C2-4`, 2026-08-05 — `PATCH /sales-orders/{id}`, xem `FRONTEND_ALIGNMENT_ROADMAP.md §8.5`)*
- [x] Role/Scope/Assignment reads và lifecycle được chốt. *(`C2-4`, 2026-08-05 — 9 endpoint, xem `FRONTEND_ALIGNMENT_ROADMAP.md §8.5`)*
- [x] Over-BOM approval semantics được chốt. *(`C2-4`, 2026-08-05 — phương án (1) chốt, `docs/capstone2-api-gap-response.md §5` câu 4 đã trả lời)*
- [x] Multi-Plant isolation test data sẵn sàng. *(`C2-5`, 2026-08-04 — 2 plant + 3 account, isolation kiểm qua HTTP thật)*

## 7. Ngoài scope Capstone 2

Không cần bổ sung trong đợt này:

- Full Purchasing/PO/Goods Receipt nhà cung cấp.
- Delivery, invoice và payment.
- MES/PLC/IoT realtime.
- OEE và detailed costing.
- Automatic scheduling optimization.


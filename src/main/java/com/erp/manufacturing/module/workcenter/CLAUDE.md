# module/workcenter — Business Rule Invariants

> Thêm ở `C2-6` (2026-08-05, track `C2-*` — `FRONTEND_ALIGNMENT_ROADMAP.md §8`). Chỉ nạp khi agent
> làm việc trong `module/workcenter/**` hoặc sửa `RoutingOperation`/`RoutingService` liên quan tới
> work center.

Work center master data (`BACKEND_CAPSTONE2_API_GAPS.md §3.4`). Bảng `work_centers`, migration
`V44` (schema, cùng migration đó cũng thêm `routing_operations.work_center_id`) + `V45` (seed
permission).

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B_wc1 | `work_centers` là **per-plant** (`plant_id NOT NULL`), không phải company-level — khác quyết định `C2-3` cho UOM (global). `UNIQUE (plant_id, code)`, không phải `UNIQUE (code)` toàn cục | `WorkCenterServiceTest.create_duplicateCodeInSamePlant_throws`, `.create_sameCodeDifferentPlant_isAllowed` |
| B_wc2 | Mọi `RoutingOperation` trong **cùng một** `RoutingHeader` phải trỏ Work Center của **cùng một Plant**. Validate ở `RoutingService.create()` — resolve toàn bộ `workCenterId` trước, kiểm tập `plantId` phân biệt > 1 ⇒ `OPERATION_NOT_ALLOWED` (422, input sai — không phải state machine) **trước khi** build bất kỳ entity nào (rule C9). `RoutingHeader` vẫn company-level (không đổi schema) — đây là hệ quả **chấp nhận** của quyết định "Work Center per-plant", không phải thiếu sót | `RoutingServiceTest.create_operationsAcrossTwoPlants_throwsOperationNotAllowed` |
| B_wc3 | `activate`/`deactivate` **không** kiểm tra tham chiếu (khác BOM/Routing `activate` — không có "revision cũ" cần deactivate). Deactivate **không** chặn khi đang được `RoutingOperation` tham chiếu — `RoutingOperation` giữ nguyên FK dù Work Center `INACTIVE` (giống Warehouse `INACTIVE` vẫn giữ FK từ `stock_balances`). `DELETE` và `POST .../deactivate` gọi **cùng** service method (`deactivate`), không phải hai hành vi khác nhau | `WorkCenterServiceTest.deactivateThenActivate_roundTrips`, `WorkCenterControllerTest.delete_returns200NoContentEnvelope` (verify `service.deactivate` được gọi, cùng method với endpoint `POST .../deactivate`) |
| B_wc4 | **[`C2-7`]** `WorkCenter.workCalendar` (FK nullable, thêm ở `C2-7` Part C) phải cùng plant với chính `WorkCenter` — validate ở `WorkCenterService.resolveWorkCalendarInPlant`, gọi `WorkCalendarLookupService.getActiveWorkCalendar` (rule C7, entry point ở `module/shift`) rồi so `plantId`. 422, không phải 409 — cùng hình dạng `B_wc2`. `null` ở cả create lẫn update = "không gắn calendar" / "giữ nguyên", không có đường gỡ một calendar đã gán (xem `module/shift/CLAUDE.md` mục 5) | `WorkCenterServiceTest.create_workCalendarInSamePlant_isAttached`, `.create_workCalendarOfDifferentPlant_throws`, `.create_inactiveWorkCalendar_propagatesRejection`, `.update_workCalendarOfDifferentPlant_throws` |

## Quyết định thiết kế cần nhớ

### 1. `routing_operations.work_center_id` **nullable, KHÔNG backfill**

Dữ liệu `work_center_code` cũ là text tự do không có plant gắn kèm (routing company-level, không
biết dòng cũ thuộc plant nào để tạo Work Center tương ứng) — đoán bừa plant sẽ ghi sai dữ liệu vĩnh
viễn. Theo đúng tiền lệ `B77` (`projected_available_quantity`, `F10`): `NULL` = "trước phase này",
không backfill. Cột `work_center_code` (String) **giữ nguyên trong DB**, nhưng **không còn được map
vào JPA entity** — dòng lịch sử đọc được ở tầng SQL, nhưng API (`RoutingOperationResponse`) trả
`workCenterId`/`workCenterCode` = `null` cho các dòng đó. Đây là hệ quả **chấp nhận** của quyết
định, không phải bug — `RoutingMapper.toResponse(RoutingOperation)` null-safe cho đúng lý do này.

### 2. Mọi write mới bắt buộc `workCenterId` — enforce ở tầng service, không phải DB

`RoutingOperationRequest.workCenterId` là `@NotNull` (400 nếu thiếu). Cột DB **không** `NOT NULL`
(lý do #1). Nghĩa là: request thiếu `workCenterId` bị chặn ở validation tầng HTTP; chỉ dữ liệu cũ
(insert trực tiếp trước `C2-6`, hoặc test dựng entity thẳng) mới có `work_center_id = NULL`.

### 3. `WorkCenterLookupService` — entry point duy nhất cho `routing` (rule C7)

`routing` **không** được đụng `WorkCenterRepository` trực tiếp. `RoutingService.buildOperation`
resolve qua `WorkCenterLookupService.getActiveWorkCenter(workCenterId)` — ném `RESOURCE_NOT_FOUND`
(404) nếu không tồn tại, `OPERATION_NOT_ALLOWED` (422) nếu `INACTIVE` — cùng khuôn
`ItemLookupService.getActiveItem`.

### 4. `WorkOrderOperation.workCenterCode` (snapshot) **không đổi thành FK**

`WorkOrderService.snapshotRouting` đọc `operation.getWorkCenter().getCode()` (thay vì
`operation.getWorkCenterCode()` cũ) rồi copy **giá trị** vào `WorkOrderOperation.workCenterCode`
(vẫn là `String`, vẫn snapshot bất biến — `B56`/`B49` không đổi). Work order KHÔNG cần biết Work
Center nào — chỉ cần cái tên hiển thị lúc snapshot, y hệt cách nó đã giữ `sourceRoutingCode` mà
không giữ FK sống tới `RoutingHeader`.

### 5. `WorkCenterResponse` có field `version` — khác most response khác trong repo

Spec §3.4 đòi "optimistic version" tường minh. Sales Order/UOM response **không** expose
`BaseEntity.version` ra wire; `WorkCenterResponse` là ngoại lệ có chủ đích, đọc theo đúng spec thay
vì theo tiền lệ nội bộ.

### 6. Permission cùng tầng Routing/BOM, không cùng tầng Warehouse

`PERM_WORK_CENTER_READ`/`_MANAGE` theo khuôn `PERM_ROUTING_READ`/`_MANAGE` (ADMIN+MANAGER manage,
OPERATOR chỉ read) — Work Center là master data **sản xuất** ràng buộc routing/capacity, không phải
cấu trúc tổ chức Company→Plant→Warehouse (`PERM_ORG_*`).

### 7. Chưa làm — chờ phase sau

Capacity Board, schedule adjustment (`C2-8`) — xem `NEXT_PHASE_PLAN.md` lịch sử `C2-6`/`C2-7` mục
"KHÔNG làm gì". ✅ **Calendar reference đã xong ở `C2-7`** (bất biến `B_wc4` ở trên) — dòng này trước
đây liệt kê nó là "chưa làm", nay đã đóng.

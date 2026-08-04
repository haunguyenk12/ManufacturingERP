# module/routing — Business Rule Invariants

> Thêm ở `F4` (2026-07-27). Chỉ nạp khi agent làm việc trong `module/routing/**`.

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B48 | Mỗi `(company, item)` chỉ có **1 routing `ACTIVE`** tại một thời điểm; activate bản mới ⇒ bản `ACTIVE` cũ tự động `INACTIVE`. Chốt chặn cuối là partial unique index `uk_routings_one_active_per_item` ở `V30` (chống race khi 2 request activate song song) | `RoutingServiceTest.activate_deactivatesThePreviousActiveRoutingOfTheSameItem`, `.activate_noPreviousActiveRouting_justActivates` |
| B49 | Routing snapshot trên Work Order là **bản copy bất biến**: `sourceRoutingId`/`sourceRoutingCode`/`sourceRoutingVersion`/`routingCapturedAt` là cột phẳng, **không** phải `@ManyToOne`. Sửa routing master sau khi tạo WO **không** đổi WO đã tạo | `WorkOrderServiceTest.createFromMrp_routingMasterRevisedAfterwards_workOrderSnapshotStaysFrozen`, `.createFromMrp_snapshotsActiveRoutingHeaderOntoWorkOrder` |
| B50 | Chỉ routing `DRAFT` mới activate được; activate lần 2 ⇒ `STATE_CONFLICT` (409). Routing không có operation nào **không** được activate | `RoutingServiceTest.activate_alreadyActiveRouting_throwsStateConflict`, `.activate_routingWithoutOperations_fails` |
| B51 | Item của routing chỉ được là `WIP` hoặc `FINISHED_GOOD` và phải thuộc đúng company — cùng ràng buộc với BOM parent item (`B9`) | `RoutingServiceTest.create_rawMaterialItem_fails`, `.create_itemOfAnotherCompany_fails` |
| B52 | `sequence` của operation là **duy nhất trong một routing** và `> 0`; validate ở service **trước khi** chạm DB, UNIQUE `(routing_id, sequence)` ở `V30` là chốt chặn cuối | `RoutingServiceTest.create_duplicateOperationSequence_fails` |

---

## Quyết định thiết kế cần nhớ

### 1. `MISSING_ROUTING` chỉ chặn đường convert proposal, **không** chặn tạo WO thủ công

Quyết định chốt với user 2026-07-27 (`NEXT_PHASE_PLAN.md` bản `F4` §1.3):

| Đường vào | Ràng buộc routing | Kết quả khi item không có routing `ACTIVE` |
|---|---|---|
| `WorkOrderService.createFromMrp` (convert proposal MAKE) | **Bắt buộc** | `MISSING_ROUTING` (409), **không** tạo WO |
| `WorkOrderService.create` (thủ công) | Tuỳ chọn | Tạo WO bình thường, 4 cột snapshot = `NULL` |

Cả hai đường đều snapshot khi routing **có** tồn tại. Hệ quả: `work_orders.source_routing_id`
**nullable**, mọi chỗ đọc nó phải null-safe. Nếu `F5` muốn chặn cứng thì đó là breaking change và
phải ghi mục Breaking Changes + sửa (không xoá) test cũ theo `R10`.

Vì `createFromMrp` **không còn** gọi lồng vào `create` (F4 tách ra `createInternal`), `@PreAuthorize`
của nó là chốt phân quyền **duy nhất** cho đường MRP — trước đây self-invocation qua Spring proxy
cũng đã khiến `PERM_WORK_ORDER_MANAGE` không được áp dụng, nay điều đó là tường minh và có test
(`WorkOrderMethodSecurityTest.createFromMrp_deniedWhenSupplySuggestionManageScopeMissing`).

### 2. Hướng phụ thuộc là `workorder → routing`; `RoutingLookupService` **thật sự có người gọi**

Khác `F3` (xem `module/sales/CLAUDE.md` mục 1, nơi lookup service hoá ra không ai gọi).
`WorkOrderService` gọi `RoutingLookupService`, tuyệt đối không chạm `RoutingHeaderRepository`
(rule `C7`). `RoutingLookupService` **không** có `@PreAuthorize`: caller đã được authorize trên
plant của mình, và đây là đọc master data read-only — cùng pattern `BomLookupService`.

### 3. ✅ Work Center là **entity thật** từ `C2-6` — mục này mô tả trạng thái lúc `F4`

> ⚠️ **[`C2-6`, 2026-08-05] Mục này đã hết hiệu lực.** `RoutingOperation.workCenterCode` (String) đã
> đổi thành `RoutingOperation.workCenter` (`@ManyToOne WorkCenter`, nullable — dòng cũ không
> backfill). Xem bất biến `B_wc2` + toàn bộ quyết định thiết kế ở
> `module/workcenter/CLAUDE.md`. Giữ đoạn dưới để hiểu vì sao `F4` từng chọn free-text.

`RoutingOperation.workCenterCode` **từng là** `VARCHAR(100)`. Spec §11 đặt capacity/CRP ngoài MVP và
MVP không có màn hình quản lý work center ⇒ tạo entity + CRUD + permission cho nó từng là mở rộng
phạm vi (`coding-rules.md §11.5`). `C2-6` (cluster `P4` — Work Center/Shift/Calendar/Capacity) là
lúc nâng lên entity.

Spec §3.3 còn liệt kê `predecessorOperationIds` cho Operation — **vẫn chưa** làm (không có consumer;
dependency graph giữa operation chỉ có ý nghĩa khi có scheduling, thuộc `C2-8`).

### 4. `F4` chỉ snapshot **header** — `work_order_operations` ✅ đã có từ `F5-A`

> ⚠️ **[`F7`] Mục này mô tả trạng thái lúc `F4`, không phải hiện tại.** Bảng
> `work_order_operations` **đã tồn tại** từ `F5-A` (entity `WorkOrderOperation`, bất biến `B56`).
> Giữ lại đoạn dưới để hiểu vì sao `F4` cố ý dừng ở header.

Quyết định chốt với user 2026-07-27. Spec §3.3 mục "Routing snapshot" chỉ có 4 field header; mục
"Operation ... Công đoạn snapshot" thuộc về `F5` — Production Execution ghi nhận theo từng operation,
nên bảng operations sẽ được thiết kế **cùng lúc** với execution thay vì tạo trước một bảng chưa ai đọc.

### 5. `routingVersion`, không phải `version`

Field/cột tên `routing_version` vì `BaseEntity` đã chiếm `version` cho optimistic locking
(`@Version BIGINT`). DTO `RoutingResponse` vẫn expose tên `version` ra wire theo spec — lệch tên
giữa entity và DTO là **có chủ đích**, không "thống nhất" lại.

### 6. `MISSING_ROUTING` trả 409 — `MISSING_BOM` ✅ nay cũng 409 (nợ #14 đã trả ở `F5-A`)

> ⚠️ **[`F7`] Mục này mô tả trạng thái lúc `F4`.** `F5-A` đã thêm `BusinessErrorCode.MISSING_BOM`
> (409) và `BomLookupService.getActiveBom` dùng nó thay `RESOURCE_NOT_FOUND` — nợ #14 đóng, xem
> `CLAUDE.md §0.4`. Giữ đoạn dưới để hiểu bối cảnh quyết định gốc.

`BomLookupService.getActiveBom` ném `RESOURCE_NOT_FOUND` (404). Spec §8.1 coi cả hai là planning
message code cùng loại (`BLOCKED`, không cho tạo WO) nhưng §8.2 không xếp code nào cho chúng.
`F4` chọn 409 cho `MISSING_ROUTING` theo họ `STATE_CONFLICT`; **không** đổi `MISSING_BOM` vì đó là
breaking change ngoài phạm vi. Ghi thành nợ #14 (`CLAUDE.md §0.4`), xử lý ở `F5`.

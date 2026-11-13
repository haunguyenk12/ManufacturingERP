# module/workorder — Business Rule Invariants

> Tách từ `CLAUDE.md` §10.3 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/workorder/**`.

## Luồng Nghiệp Vụ (từ `AGENTS.md` §7)

```text
Work Order
-> BOM Explosion / requirement snapshot
-> Material Reservation
-> Material Issue
-> WIP Tracking
-> Production Completion / Receipt
-> Inventory Update
-> Variance
```

`WorkOrderStatus` **[F5]**: `DRAFT`, `PLANNED`, `BLOCKED`, `RELEASED`, `IN_PROGRESS`, `COMPLETED`,
`CANCELLED` (xem B13-B14 dưới đây cho status nào được phép reserve/issue/receipt/WIP).
`PLANNED` là **lịch đã chốt, chưa chuẩn bị vật tư** — hành xử y hệt `DRAFT` với execution (không
reserve/issue/receipt/WIP), nhưng **được** release và **được** cancel.

> ⚠️ **`F5` đã đảo ngược ai làm work order tiến triển** (`CLAUDE.md §0.5`). Ba cột số lượng, đừng nhầm:
>
> | Cột | Ý nghĩa | Ai tăng |
> |---|---|---|
> | `plannedQuantity` | kế hoạch | planner |
> | `actualGoodQuantity` / `actualScrapQuantity` / `actualReworkQuantity` | **thực tế xưởng làm ra** | `ProductionExecutionService.report` |
> | `completedQuantity` | **đã nhập kho** (giữ nguyên ngữ nghĩa cũ, **không** đổi tên) | `ProductionReceiptService.approve` |
>
> `availableToReceipt = actualGoodQuantity − completedQuantity`.

`ProductionReceiptStatus` **[F2]**: `DRAFT` → `PENDING_APPROVAL` → `APPROVED`, nhánh cụt `REJECTED`,
cộng `CANCELLED` (chưa dùng). `POSTED` **đã bị đổi tên** thành `APPROVED` ở `V26` — dữ liệu cũ được
`UPDATE` trong chính migration đó, không còn giá trị `POSTED` nào tồn tại.

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B12 | Khi tạo WO phải **snapshot** component requirement từ BOM `ACTIVE` (có tính scrap rate). WO không phụ thuộc BOM đổi về sau.<br>**[F9]** Snapshot là **direct-only** — chỉ component **trực tiếp**, không lồng cây BOM. MRP nổ cấp sâu thành **work order riêng**, nên `childBom` mà spec §3.3 nêu là **nợ `I` có chủ đích** (roadmap §7.1), không phải field quên. Lồng cây vào đây = đổi ngữ nghĩa bất biến này và bảng `work_order_component_lines`.<br>**[F9]** `WorkOrderComponentLineResponse.reservedQuantity` dùng **đúng cùng** aggregate của `/material-readiness` (`sumActiveRemaining*`) — hai màn hình không được nói hai số dưới cùng một tên | `WorkOrderServiceTest.create_snapshotsActiveBomDirectRequirements` |
| B13 | **[D9 → D11 – tách ba]** Ba gate, **đừng gộp lại**:<br>• **Issue / WIP / production execution** — chỉ `RELEASED`/`IN_PROGRESS` (`canExecute()`).<br>• **Reserve** — mọi status trừ `COMPLETED`/`CANCELLED` (`canReserve()`, `D9`): reservation không sinh movement, chỉ chuyển available → reserved, và gate 1a đòi reservation trước khi release nên buộc reserve phải chờ `RELEASED` là deadlock (nợ #22).<br>• **Receipt** (`post` + `submit` + `approve`) — `canExecute()` **cộng `COMPLETED`** (`canReceipt()`, `D11`): `B53` đóng WO ngay khi cumulative good chạm plan, nhưng `completedQuantity` (đã nhập kho) đi sau, nên `canExecute()` chặn mất dòng receipt **cuối cùng** của mọi WO có `plannedQuantity` đúng bằng số cần nhập kho (nợ #25). `CANCELLED`/`BLOCKED` vẫn bị chặn ở cả ba | `MaterialIssueServiceTest.post_onBlockedWorkOrder_shouldThrow`, `ProductionReceiptServiceTest.post_onBlockedWorkOrder_shouldThrow` + `.post_onCancelledWorkOrder_shouldThrow` + `.post_onCompletedWorkOrder_receiptsTheOutputThatIsNotWarehousedYet` + `.submit_onCompletedWorkOrder_isAllowed` + `.approve_onCompletedWorkOrder_warehousesTheOutputAndRaisesCompletedQuantity`, `MaterialReservationServiceTest.reserve_isAllowedBeforeRelease` + `.reserve_onClosedWorkOrder_shouldThrowBeforeTouchingStock`, `ProductionFlowE2EIT.reserve_onCancelledWorkOrder_isStillRefused` + `.productionFlow_fulfilsTheSalesOrderForOutputThatIsNotLotTracked` |
| B14 | **[P1-1a]** Release yêu cầu reservation phủ **100%** nhu cầu còn lại; thiếu → WO `BLOCKED` + 409. `BLOCKED` được persist bằng `WorkOrderBlockRecorder` (bean riêng, `REQUIRES_NEW`, **return bình thường** rồi gate mới throw — nợ #23) | `WorkOrderReleaseGateTest` (uỷ quyền cho recorder) · `WorkOrderBlockRecorderTest` (field ghi đúng) · `ProductionFlowE2EIT.releaseWithoutReservation_persistsBlocked_andReservingOutOfItAllowsRelease` (chứng minh row **sống sót** rollback — mock không kiểm được) |
| B15 | **[P1-1b]** Issue vượt định mức BOM: không quyền → **403**; có quyền nhưng thiếu `overrideReason` → **422**; đủ cả hai → cho phép + lưu vết `over_issue` | `MaterialIssueServiceTest` |
| B16 | **[F5 – viết lại]** Không receipt vượt `actualGoodQuantity − completedQuantity − Σ(receipt DRAFT + PENDING_APPROVAL)`. Trần là **hàng xưởng đã làm ra và chưa nhập kho**, không còn là phần kế hoạch còn lại. Vượt ⇒ `PLANNED_QUANTITY_EXCEEDED` (409). **[F2]** `DRAFT` vẫn được tính vào phần đã claim — nếu không, hai draft đều qua được check rồi cùng submit. **[D11]** `D11` **không** nới trần này; nó là thứ duy nhất giữ cho WO `COMPLETED` không thành cửa mở, nên đừng chuyển check số lượng ra sau gate status | `ProductionReceiptServiceTest.post_exceedProducedGoodIncludingOpenReceipts_shouldThrow`, `.post_overProducedGoodQuantity_failsBeforeMovement`, `.post_onCompletedWorkOrder_stillCannotExceedWhatWasProduced` |
| B17 | **[P1-1c → F2 → F5 – tách đôi]** `post` tạo `DRAFT` và `submit` chuyển sang `PENDING_APPROVAL`, **cả hai đều KHÔNG** đụng tồn kho; `approve` tạo movement + cộng `completedQuantity` + ghi WIP `OUTPUT_RECEIPTED`. **`approve` KHÔNG còn đổi status work order** — không `markInProgress`, không `complete`. Việc đó thuộc B53 | `ProductionReceiptServiceTest.approve_shouldCreateReceiveMovementWithHoldLot_withoutCompletingWorkOrder` |
| B18 | **[P1-1c, FE4-5B3]** Lot **mới tạo** từ approve mở ở `HOLD`; lot **đã tồn tại** giữ nguyên status. Output `NON_TRACKED` không có lot để mang status nên approve cộng đồng thời `quantity` và `quality_hold_quantity`: on-hand tăng nhưng available vẫn 0 tới QC | `InventoryMovementServiceTest.receive_*Hold*`, `.receive_nonTrackedItemWithHoldStatus_increasesOnHandAndQualityHoldButNotAvailable`, `ProductionFlowE2EIT.productionFlow_fulfilsTheSalesOrderForOutputThatIsNotLotTracked` |
| B19 | Reservation không được vượt available stock; release reservation phải trả lại `reserved_quantity` | `MaterialReservationServiceTest` |
| B20 | Variance = actual − planned, phân loại `OVER_ISSUED` / `UNDER_ISSUED`; output variance tính cả scrap/rework | `WorkOrderVarianceServiceTest` |

> 🔴 **[`D1`, 2026-07-28] Hai defect chặn luồng, phát hiện bởi `ProductionFlowE2EIT` — đọc trước khi
> sửa bất cứ thứ gì quanh B13/B14.**
>
> 1. **Nợ #22 — work order tạo từ MRP không release được qua API.** `reserve` và `reserveAutomatically`
>    đều đòi `canExecute()` (`RELEASED`/`IN_PROGRESS` — B13), còn release đòi reservation phủ 100%
>    (B14). Không bước nào đi trước được. Mọi unit test reservation dựng WO **đã** `RELEASED` nên
>    blind spot này tồn tại từ `P1`.
> 2. **Nợ #23 — `BLOCKED` không bao giờ được persist.** `WorkOrderReleaseGate.ensureMaterialReady`
>    `block()` + `save()` rồi throw **trong chính** transaction `REQUIRES_NEW` của nó ⇒ rollback-only
>    ⇒ mất luôn `BLOCKED`. WO thực tế vẫn `DRAFT`. Javadoc của method nói ngược lại — **đừng tin
>    javadoc đó**, nó mô tả ý định chứ không phải hành vi.
>
> `ProductionFlowE2EIT` hiện **pin hành vi sai** (có giải thích) như tiền lệ nợ #8, và dùng một seam
> `forceStatus(...)` để phủ được các bước sau. Khi sửa #22/#23: xoá seam đó, `MaterialReservationServiceTest`
> sẽ phải sửa theo (`R10`), và cập nhật lại B13/B14 ở đây. Chi tiết: `CLAUDE.md §0.4` #22/#23.

> ⚠️ **[`D1`] Nợ #24** — `POST /material-issues` (phẳng) không replay-safe: `postFlat` resolve
> reservation **trước** khi `postInternal` kiểm idempotency ⇒ retry cùng key trả 409 thay vì chứng từ
> cũ. Endpoint đa dòng không bị. Pin ở step 11 của `ProductionFlowE2EIT`.

> Bối cảnh 3 business gate P1 (release/BLOCKED, over-issue override, production receipt 2 bước) và
> 2 giới hạn đã biết của P1 nằm ở `CLAUDE.md` §0.3 (root) — không lặp lại ở đây để tránh 2 nơi phải sửa khi phase đổi.

## Bất biến nằm ở thư mục con (tách 2026-11-12, nguyên văn)

Hai nhóm bất biến dưới đây nằm cạnh code của chúng và chỉ tự nạp khi đọc file trong thư mục đó.
**Đọc chúng cả khi sửa controller, DTO, entity hay repository của cùng luồng.**

| Luồng | File | Mục |
|---|---|---|
| Cấp vật tư, hoàn vật tư, dở dang, công đoạn, báo sản lượng, phiếu nhập thành phẩm, QC, serial, tích luỹ chi phí | `service/execution/CLAUDE.md` | Bất Biến QC Disposition; Bất Biến Production Execution + Work Order Operations; Bất Biến Costing; Bất Biến Serial Tracking; G8b; G8c; G8d; G9a; G9b; G9c; CF-2 |
| Giá thành sản xuất, chênh lệch, chốt giá và đóng lệnh (`G11`) | `service/cost/CLAUDE.md` | G11 |

Bản gộp trước khi tách: `docs/archive/rules-2026-11-12/workorder-CLAUDE.md`.

## Bất Biến Số Chứng Từ (F10)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B79 | `MaterialIssue.code` (`MI-`) và `ProductionExecution.code` (`PE-`) derive từ **chính id của dòng** (8 hex đầu, chữ hoa) và sinh ở **`@PrePersist`** — 🔴 **không** gán sau `save()`: Hibernate chụp snapshot entity lúc queue INSERT nên field gán sau đó **không vào câu INSERT** và cột `NOT NULL` nổ (bài học `CLAUDE.md §0.12` #5, giống `B68`). Công thức trong Java **phải khớp từng ký tự** với backfill `V40`; lệch một ký tự thì dòng lịch sử mang mã không truy ngược về id được, và `UNIQUE` **không** bắt được chuyện đó.<br>**Prefix `PE-` lệch spec có chủ đích:** §5.2 gọi nó "Mã WIP", nhưng `wip_transactions` là **bảng ledger khác** và không có cột `code` ⇒ `WIP-` sẽ đọc như đang định danh một dòng wip transaction.<br>**`ProductionExecutionResponse.status` là hằng `"POSTED"`, KHÔNG phải cột** — execution không có đường huỷ/đảo nào, cột một-giá-trị là speculative (`coding-rules.md §11.5`), cùng lối tư duy với alias `bomCapturedAt`/`executionCompletedAt` của `F8`. Khi nào có reversal thì **đó** mới là lúc nó thành cột + enum | `MaterialIssueRepositoryIT.persist_withoutACode_derivesTheDocumentNumberFromTheId`, `FlywayMigrationIT.migrate_v40_backfillsExistingDocumentsWithTheSameCodeFormulaAsTheEntities` (parity Java↔SQL cho **cả hai** bảng), `ProductionFlowE2EIT` (insert thật qua HTTP, cả `code` lẫn `status`) |

## Bất Biến Time Variance (C2-4)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B86 | `GET /work-orders/{id}/variance` → `timeVariance`: `plannedMinutes = Σ(setupMinutes + runMinutesPerUnit × plannedQuantity)` trên mọi `WorkOrderOperation` snapshot của work order (`B56`); `actualMinutes = Σ Duration.between(actualStartedAt, actualEndedAt)` trên mọi `ProductionExecution`, **loại** execution còn dở dang (`actualEndedAt == null`) — thời gian dở dang không phải thời gian đã tiêu tốn xong, tính vào sẽ đánh giá sai giữa chừng ca làm việc.<br>🔴 **Operations đọc qua `WorkOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc`, KHÔNG qua `workOrder.getOperations()`.** `WorkOrderRepository.findWithDetailsByWorkOrderId` đã join-fetch `componentLines` (một `List`/"bag"); thêm `"operations"` (cũng `List`) vào cùng `@EntityGraph` làm Hibernate ném `MultipleBagFetchException` ngay từ query đầu tiên — phá **toàn bộ** endpoint variance, không chỉ phần time. Đây là lỗi thật bắt được lúc chạy `mvn verify` (`ProductionFlowE2EIT` sập theo vì nó gọi cùng entity graph), không phải giả định | `WorkOrderVarianceServiceTest.getVariance_timeVariance_sumsOperationsAndExecutionsExcludingInProgress`, `.getVariance_noOperations_plannedMinutesIsZero`, `.getVariance_noExecutions_actualMinutesIsZero` |

## Bất Biến Capacity / Schedule (C2-8)

> Thiết kế đầy đủ + bối cảnh quyết định: `CLAUDE.md §0.30`. Bảng dưới chỉ liệt kê bất biến.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B89 | Lịch (`WorkOrderOperation.plannedStartAt`/`plannedEndAt`) sinh **đúng một lần**, ở `WorkOrderService.release()` — không phải lúc tạo WO, không phải lúc `plan()`. Tuần tự theo `sequence`: operation sau bắt đầu khi operation trước kết thúc, operation đầu neo vào `workOrder.plannedStartAt` (nếu có) hoặc `now()`. Operation có `workCenter.workCalendar` thì gọi `WorkCalendarLookupService.computeEndInstant` (bỏ qua giờ không làm việc, đi qua `WorkingWindowCalculator.advance`); không có calendar thì cộng phút liên tục. Đây là lịch **infinite-capacity** — không biết WO khác có đang chiếm cùng Work Center hay không, cố ý tách khỏi việc báo quá tải (đó là Capacity Board, một read riêng) | `WorkOrderServiceTest.release_schedulesOperationsSequentially_continuousTimeWhenWorkCenterHasNoCalendar`, `.release_schedulesOperations_delegatesToTheWorkCenterCalendarWhenPresent`, `.release_withoutAnExplicitPlannedStartAt_anchorsTheFirstOperationAtNow`, `WorkingWindowCalculatorTest.advance_*` (6 case) |
| B90 | 🔴 **[G5: phần "xung đột sequence không chặn" bị thay bởi B167 — nay 409]** `POST .../schedule-adjustments` chỉ chặn cứng khi version lệch (409 `CONCURRENT_MODIFICATION`) hoặc thời gian vô nghĩa (`plannedEndAt <= plannedStartAt`, 400 `INVALID_INPUT`), cộng hai gate tiền điều kiện (chưa có Work Center, hoặc chưa từng được `release()` — cả hai 422 `OPERATION_NOT_ALLOWED`). Xung đột **sequence**/**calendar**/**capacity** đều **không chặn** — trả về dưới dạng cờ tư vấn trên `ScheduleAdjustmentResponse`; backend **không bao giờ** tự dời operation khác | `ScheduleAdjustmentServiceTest.adjust_operationWithoutWorkCenter_throwsOperationNotAllowedBeforeAnyWrite`, `.adjust_neverScheduled_throwsOperationNotAllowedBeforeAnyWrite`, `.adjust_staleExpectedVersion_throwsConcurrentModificationBeforeAnyWrite`, `.adjust_endNotAfterStart_throwsInvalidInputBeforeAnyWrite`, `.adjust_cleanWindow_persistsAndReportsNoSequenceConflict`, `.adjust_windowStartingBeforePredecessorEnds_stillPersistsButFlagsSequenceConflict` |

**Quyết định cần nhớ:**

1. 🔴 **`WorkOrderOperation.workCenter` (FK) đảo ngược quyết định `C2-6`.** `module/workcenter/CLAUDE.md`
   mục 4 từng nói cột này giữ `String` mãi mãi vì chưa có consumer — `C2-8` là consumer đó. FK **thêm
   vào cạnh** `workCenterCode` (không thay thế), nullable, không backfill dòng lịch sử — cùng hình
   dạng `B_wc2`/`B77`.
2. **Ranh giới local-time ↔ `Instant` nằm ở `Plant.timezone`** — field có sẵn từ lâu nhưng `C2-8` là
   lần đầu nó được nối vào toán ngày tháng thật. `WorkCalendarLookupService.computeEndInstant` là nơi
   duy nhất quy đổi; JPQL `function('timezone', plant.timezone, plannedStartAt)` (Postgres
   `timezone(zone, ts)`, tương đương `AT TIME ZONE`) dùng đúng phép quy đổi đó ở tầng SQL để hai bên
   không lệch ngày. Đây là construct `function(...)` **đầu tiên** trong repo — không có tiền lệ native
   query nào trước đó. `WorkOrderOperationRepositoryIT` khoá bằng một mốc mà ngày UTC và ngày local
   (`America/New_York`, tháng 1) lệch nhau có chủ đích.
3. **"Existing load" của Capacity Board luôn tính trên `{RELEASED, IN_PROGRESS, COMPLETED}`, bất kể
   tham số `status` filter đang lọc gì** — filter chỉ thu hẹp dòng hiển thị, không thu hẹp mẫu số
   utilization, để hai lượt gọi khác filter không báo hai con số khác nhau cho cùng một
   Work Center/ngày.
4. **`dayCapacityMinutes`/`utilizationPercent` là `null`, không phải `0`, khi Work Center không có
   `workCalendar`** — "không biết" khác "không có sức chứa". `overload` khi đó luôn `false`.
5. **Ngày quy-thuộc của một operation = ngày `plannedStartAt` rơi vào (local)**, toàn bộ thời lượng
   gán hết cho ngày đó, không chia theo số ngày trải dài — đơn giản hoá có chủ đích của CRP tĩnh,
   cùng quy ước "gán cho ngày ca bắt đầu" của ca qua đêm (`module/shift/CLAUDE.md` mục 1).
6. `CapacityBoardService.buildDayContext`/`attributedDay`/`dayCapacityMinutes`/`utilizationPercent`/
   `key` là `public` dù chỉ `ScheduleAdjustmentService` (khác package) gọi tới — hai endpoint phải
   báo cùng một con số "quá tải" cho cùng `(workCenter, ngày)`, nên chia sẻ một implementation thay
   vì hai công thức có thể trôi lệch nhau.
7. `ScheduleAdjustmentService`/`CapacityBoardService` **không** có permission-guard component riêng —
   tái dùng `workOrderPermissionGuard`/`permissionGuard` tổng quát đã có.

## Bất Biến Close/Reconcile (P6, 2026-08-06)

Trả nợ track `P6` cuối cùng (Sales Order + Fulfillment đã xong ở `F3`/`F6`). Migration `V53`. Quyết
định chốt với user (`AskUserQuestion`): **`CLOSED` khoá hoàn toàn** — không carve-out đọc/ghi nào,
khác phương án "cho phép điều chỉnh nhẹ" từng cân nhắc.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B100 | `CLOSED` chỉ vào được từ `COMPLETED` (`canClose()`), qua hành động tường minh
`WorkOrderService.close` — **không bao giờ tự động**. Đây là nửa "Reconcile" của tên phase: `close()`
gọi lại **đúng** `materialReservationService.cancelActiveReservations(workOrder)` mà `cancel()` đã
dùng từ trước — giải phóng mọi reservation `ACTIVE` còn sót (component bị over-reserve nhưng chưa bao
giờ issue) về lại available, vì một khi `CLOSED` thì WO không còn cách nào khác để giải phóng nó nữa.
🔴 **Bug thật do phase này phát hiện và sửa**: `canReserve()` (trước phase) là **danh sách phủ định**
`!= COMPLETED && != CANCELLED` — một giá trị enum mới không tự động bị loại trừ khỏi danh sách phủ
định, nên nếu không sửa, một WO `CLOSED` vẫn nhận reserve mới được. Đã thêm `&& != CLOSED` tường minh.
Mọi gate khác (`canExecute()`, `canReceipt()`, `canRelease()`, `canPlan()`, danh sách status tường
minh trong `cancel()`) là **danh sách khẳng định** (positive list) nên tự động loại `CLOSED` mà không
cần sửa gì — đã rà đủ theo checklist `coding-rules.md §11.3` (`grep -rn "WorkOrderStatus\."
src/main`). `CapacityBoardService.LOAD_STATUSES` (đọc, không phải gate ghi) **có sửa**: `CLOSED` được
thêm cạnh `COMPLETED` vì lịch của một WO đã đóng vẫn là load lịch sử thật — đóng WO không được âm
thầm viết lại utilization của một ngày đã báo cáo trước đó | `WorkOrderTest.canClose_onlyTrueWhenCompleted`, `.close_setsStatusAndClosedAt`, `.canReserve_isFalseOnceClosed`; `WorkOrderServiceTest.close_completedWorkOrder_succeedsAndReleasesActiveReservations`, `.close_notCompletedWorkOrder_throwsStateConflictBeforeTouchingAnything`, `.close_alreadyClosedWorkOrder_throwsStateConflict`; `MaterialReservationServiceTest.reserve_onClosedWorkOrder_shouldThrowBeforeTouchingStock` (nay thêm `CLOSED` vào `@EnumSource`); `MaterialIssueServiceTest.post_onClosedWorkOrder_shouldThrow`; `ProductionReceiptServiceTest.post_onClosedWorkOrder_shouldThrow`; `CapacityBoardServiceTest.loadStatuses_includesClosedAndCompletedExcludesCancelled` |

**Quyết định cần nhớ:**

1. Endpoint `POST /work-orders/{id}/close` **không nhận body** — đóng không đối kháng như cancel
   (spec không đòi lý do, quyết định của user chỉ nói "khoá hoàn toàn", không nói gì về reason), nên
   không thêm `reason` bắt buộc như `WorkOrderCancelRequest` (`F7`). Không thêm scope ngoài yêu cầu.
2. Tái dùng `PERM_WORK_ORDER_MANAGE` — cùng quyền đã gác `cancel`/`plan`/`release`/`update`, mọi hành
   động đổi vòng đời trên aggregate này. **Không** permission mới ⇒ không migration seed, không đụng
   `docs/roles-and-permissions.md` (`coding-rules.md C10` chỉ bắt buộc khi có permission **mới**).
3. `closedAt` mirror `cancelledAt`/`completedAt` — cột nullable đơn giản, **không** có `closedBy`
   riêng: ai đóng đã được `updatedBy` (JPA auditing) ghi lại, đúng cách mọi status transition khác
   trên entity này đã làm.

## Bất Biến Fulfillment Allocation (F6)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B62 | Fulfillment chạy **chỉ** khi QC disposition = `AVAILABLE` commit thành công (spec §7.1). `approve` không fulfill; QC `REJECTED` không fulfill. Số lượng đưa sang là quantity vừa QC, không phải `completedQuantity` hay `actualGoodQuantity`. Với output không lot, release `quality_hold_quantity` và fulfillment cùng transaction; release lỗi thì receipt/SO đều rollback | `ProductionReceiptServiceTest.qcDisposition_available_fulfilsTheAllocatedSalesOrderQuantity`, `.qcDisposition_rejected_neverReachesFulfilment`, `.approve_shouldCreateReceiveMovementWithHoldLot_withoutCompletingWorkOrder`, `.qcDisposition_available_onOutputWithoutALot_releasesQualityHoldAndFulfils` |
| B63 | `WorkOrderDemandAllocation.fulfilledQuantity` không vượt **cả hai** trần: `allocatedQuantity` của chính nó, và `openQuantity` của SO line (một WO khác có thể đã phủ line đó). Phần QC dư ra **không** gây lỗi — đó là tồn kho tự do. CHECK `chk_wo_demand_allocations_fulfilled` (`V35`) là chốt chặn cuối | `WorkOrderDemandAllocationServiceTest.fulfill_moreThanEveryAllocationClaims_dropsTheSurplusWithoutFailing`, `.fulfill_lineAlreadyCoveredElsewhere_appliesNothingToIt` |
| B64 | Allocation **chỉ** sinh từ suggestion có lineage `requirementLevel = 0` + demand `referenceType = SALES_ORDER_LINE`. Component cấp dưới thừa hưởng `sourceDemand` của cha (`expandChildren`) nên **phải** bị loại — nếu không, WO bán thành phẩm sẽ fulfill SO line lần thứ hai. Demand `MANUAL`/`FORECAST` và `referenceId` không parse được ⇒ không allocation, **không** lỗi | `SupplySuggestionServiceTest.convertToWorkOrder_componentLevelProposal_isNotAllocatedToTheInheritedSalesOrderLine`, `.convertToWorkOrder_demandNotFromSales_allocatesNothingAndStillConverts`, `.convertToWorkOrder_unparsableDemandReference_allocatesNothingAndStillConverts` |
| B65 | Thứ tự phân bổ là **xác định**: `dueDate` → `lineNo` → `salesOrderLineId`. Không được phụ thuộc thứ tự DB trả về — hai line khác đơn có thể trùng cả due date lẫn line no | `WorkOrderDemandAllocationServiceTest.fulfill_spreadsAcrossAllocationsEarliestDueDateFirst` |

> **Hướng phụ thuộc `workorder → sales`.** `WorkOrderDemandAllocation.salesOrderLineId` là **cột
> `UUID` phẳng**, không `@ManyToOne`: `sales → planning → workorder` đã tồn tại, nên một association
> có kiểu sẽ đóng vòng phụ thuộc compile-time. Mọi thứ `workorder` cần biết về SO line (due date,
> line no, open quantity) đọc qua `SalesOrderFulfillmentService` (rule `C7`); khoá ngoại vẫn có ở
> tầng DB. **Đừng** "tiện tay" đổi thành quan hệ.

> ✅ **[`D5`, 2026-07-30] Nợ #17 đã trả.** Trước `D5`, thành phẩm **không** lot-tracked không đi qua QC
> được (B40 bản cũ) ⇒ allocation của nó không bao giờ fulfill, đơn hàng treo ở `IN_PROGRESS` vĩnh viễn
> **không có lỗi nào báo**. `D5` mở QC cho output không lot (B40 tách đôi, B39 viết lại). Vẫn **không**
> được thêm đường fulfill thứ hai ở `approve` để "tiện" — phá B62 và tạo rủi ro double-count.
>
> **Cảnh báo cho ai viết báo cáo QC:** phán quyết QC của receipt **không lot** chỉ nằm trên
> `production_receipts.qc_result`/`qc_reason`/`qc_at`/`qc_by`, **không** có dòng trong
> `quality_dispositions` (quyết định `A2` của `D5`: bảng đó định nghĩa là "một dòng cho mỗi **lot**",
> dòng không lot là tự mâu thuẫn). Báo cáo query từ `quality_dispositions` sẽ **thiếu** những receipt
> này — phải union với `production_receipts` hoặc đọc thẳng từ đó.

> **Vị trí code:** `ProductionExecution` và `WorkOrderOperation` nằm trong `module/workorder` vì
> thuộc aggregate Work Order (endpoint treo trên `/work-orders/{id}`) — cùng lý do với
> `QualityDisposition` ở `F2`, không phải vì "production execution là con của work order".

> **Vị trí code:** `QualityDisposition` nằm trong `module/workorder` chứ không phải module `quality`
> riêng, vì nó là một phần của aggregate Production Receipt (endpoint treo trên `/{receiptId}`,
> state machine là của receipt). Việc đổi status lot vẫn đi qua
> `InventoryMovementService.changeLotStatus` để không phá module boundary (rule `C7`).

## G5 — Công suất theo kỳ, xem trước, điều chỉnh chặn chồng lịch (2026-10-16)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B165 | **Phân bổ tải theo kỳ (D9):** phút của một công đoạn (`setup + run × qty`, phút **công suất**) chia cho từng kỳ/ngày theo tỉ lệ **phút làm việc** của nó rơi vào đó (không lịch ⇒ theo giờ đồng hồ) — `CapacityLoadCalculator.allocate`. Board chọn công đoạn **giao** với kỳ (không còn "bắt đầu trong kỳ"), ranh giới kỳ đổi sang Instant theo `Plant.timezone` ở Java; `aggregateExistingLoad` đã xoá, thay bằng `findScheduledOverlapping`. Sức chứa ngày = phút làm việc **trong ngày** × `capacityUnits` (đuôi ca đêm thuộc ngày nó chạy) | `CapacityBoardServiceTest.getBoard_operationSpanningTwoDays_*`, `.getPeriodLoads_*`, `WorkOrderOperationRepositoryIT.findScheduledOverlapping_*` (mutation "dồn về ngày bắt đầu" ⇒ đỏ) |
| B166 | **Xem trước chỉ đọc** (`@Transactional(readOnly = true)`, không repository ghi): routing ACTIVE + hiệu lực, BOM ACTIVE, trung tâm cùng nhà máy + có lịch ACTIVE (không ⇒ `SCHEDULING_UNAVAILABLE`, **không** xếp theo giờ liên tục). Tuần tự theo `sequence`; **elapsed = required / capacityUnits** (khác release, vốn không chia); khung cắt tại cuối kỳ để mỗi segment nằm trọn trong/ngoài kỳ; tối đa 180 ngày sau kỳ. Kỳ ≤ 90 ngày tính cả hai đầu. Quyền: 6 quyền (`CapacityPermissionGuard`) | `CapacityPreviewServiceTest.preview_matchesTheExampleOfTheHandoff_A27` (+`verifyNoMoreInteractions`), `CapacityPermissionGuardTest` |
| B167 | **Điều chỉnh (D10, thay B90):** cửa sổ chồng công đoạn trước/sau ⇒ **409 `OPERATION_SEQUENCE_CONFLICT`**, không ghi. Chỉ WO `RELEASED`/`IN_PROGRESS` và công đoạn chưa có `ProductionExecution` (409 `STATE_CONFLICT`). `plannedEndAt` vắng ⇒ tính như release. Lý do ≥ 5 ký tự. Calendar/capacity vẫn chỉ là cờ. `sequenceConflict` luôn `false` | `ScheduleAdjustmentServiceTest.adjust_window*_isRefused*`, `.adjust_completedWorkOrder_*`, `.adjust_operationAlreadyStarted_*`, `.adjust_withoutPlannedEnd_*` |

## G6a — Bảng giữ tồn có hai loại chủ (2026-10-17)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B175 | `material_reservations` thuộc **đúng một** chủ: (`work_order_id` + `component_line_id`) **hoặc** (`sales_order_id` + `sales_order_line_id`) — `chk_material_reservations_single_owner` (V74). Hai cột WO nay **nullable**; chủ đơn bán là **UUID phẳng** (tránh vòng `sales → planning → workorder → sales`). Lượng giữ vẫn cộng vào **một** cột `stock_balances.reserved_quantity` ⇒ MRP/dashboard/gate ghi tồn kho tự thấy phần đơn bán giữ, không có "hai con số". 🔴 Mọi query đọc reservation **theo kho** phải **left join** `workOrder`/`componentLine` (`searchByWarehouse` — trước G6a inner join ⇒ dòng đơn bán biến mất khỏi màn hình kho). Query theo WO/component (`sumActiveRemainingBy*`, readiness, issue) tự loại dòng đơn bán vì lọc theo WO id. `findReservationForWorkOrder` coi dòng không có WO là "không thuộc lệnh này" (`STATE_CONFLICT`), không NPE | `MaterialReservationRepositoryIT.searchByWarehouse_returnsSalesOrderReservationsNextToWorkOrderOnes`, `.sumActiveRemainingBySalesOrderLineIds_countsActiveHoldsOnly`, `.singleOwnerCheck_*` (2), `FlywayMigrationIT.migrate_v74_*` |
| B176 | `SalesOrderStockReservationService.reserve`: có `lotId` ⇒ đúng lô đó, lô phải `AVAILABLE` **và chưa hết hạn** (`LOT_NOT_ELIGIBLE`); không `lotId` + vật tư theo lô ⇒ **FEFO** qua `StockBalanceRepository.findReservableBalancesFefo` (loại lô quá hạn — **khác** `findIssuableBalancesFefo` của WO vốn không xét hạn, G4 B154), mỗi lô một dòng giữ, **all-or-nothing** (thiếu ⇒ `INSUFFICIENT_STOCK`, không ghi gì); vật tư sê-ri ⇒ 422. `release` chỉ dòng ACTIVE **của chính đơn** (`404` cho đơn khác/WO) | `SalesOrderStockReservationServiceTest` (10), `SalesOrderReservationIT.reserveWithoutALot_*` (mutation bỏ điều kiện hết hạn ⇒ đỏ) |

`SalesOrderCoverageLookupService` (`service/query/`) là entry point đọc coverage cho `sales`: `sumOpenCoverageBySalesOrderLineIds` (loại WO `CANCELLED`/`CLOSED`) và `findLinkedWorkOrders` (cho `linkedDocuments[]`).

## G6b — Phần gắn đơn của lệnh SX từ chuyển đổi (2026-10-18)

`WorkOrderService.createFromMrp(…, allocationCap)` + `WorkOrderDemandAllocationService.allocate(workOrder, lineId, cap)`: `cap = null`
giữ luật F6 (`min(planned, openQuantity)`); chuyển đổi G6b truyền `uncovered` của dòng đơn đọc lại lúc chuyển ⇒ hai run cùng phủ một dòng
không gắn quá phần còn thiếu (phần vượt là WO không gắn đơn). Bản 4 tham số cũ gọi bản 5 tham số qua `this` — nó tự mang
`@PreAuthorize`/`@Auditable` nên không mất guard. Test: `PlanningConversionServiceTest.convert_firstPartialMake_*` (B183, planning).

## G8a — Nháp lệnh SX: kho từng dòng, version bắt buộc, lịch đã chốt, trả quota khi huỷ (2026-10-19)

Nguồn: `FE_HANDOFF_GD_08_2026-10-14.md`; quyết định D0–D12 chốt 2026-10-19 (tất cả theo khuyến nghị, D11 **hoãn**). Migration `V79`.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B207 | **Kho cho từng dòng vật tư (D2).** `work_order_component_lines.warehouse_id` nullable; `componentWarehouses: [{itemId, warehouseId}]` khoá theo **vật tư** (áp mọi dòng của vật tư đó — một BOM có thể liệt kê một vật tư nhiều dòng, WO giữ một dòng/dòng BOM). Có kho ⇒ giữ tồn **chỉ** ở kho đó: tự động (`reserveAutomatically`) chỉ hỏi kho đó; tay từ kho khác ⇒ **422 `RESOURCE_SCOPE_MISMATCH`** field `warehouseId`. `NULL` (mọi dòng cũ, dòng không chọn) = phạm vi nhà máy như trước. Đổi kho của vật tư đang có reservation `ACTIVE` ở kho khác ⇒ 409 `STATE_CONFLICT` (giải phóng trước). Chỉ đặt được khi `DRAFT` (create / PATCH / plan) | `MaterialReservationServiceTest.reserveAutomatically_lineWithAWarehouse_looksInThatWarehouseOnly`, `.reserve_lineWithAWarehouse_fromAnotherWarehouse_*`, `WorkOrderServiceTest.update_componentWarehouse*` |
| B208 | **Version bắt buộc trên mọi lệnh WO (D5):** PATCH (`expectedVersion`), plan/release/cancel/reserve tự động/release-reservations (`expectedWorkOrderVersion` trong body), tạo reservation (body), xoá reservation (query). Thiếu ⇒ 400 field; lệch ⇒ 409 `CONCURRENT_MODIFICATION` (`WorkOrderVersions.ensure`). Mọi lệnh **bump đúng một** version qua `WorkOrder.touch()` + `saveAndFlush` (`WorkOrderExecutionSupport.touchAndFlush`) — reservation/dòng/công đoạn là collection inverse, đổi chúng không tự `UPDATE work_orders` (bẫy B112). `Idempotency-Key` **tuỳ chọn** (`work_order_idempotency_records`, `UNIQUE (key, command)`): replay kiểm **trước** version (lần đầu đã bump version) và trả **trạng thái hiện tại**, không chạy lại; khác payload ⇒ 409 `IDEMPOTENCY_CONFLICT`. Đường cũ không có version: `component-issues`, `output-completions`, `POST /material-issues` phẳng, `/wip-transactions`, `close` | `WorkOrderRepositoryIT.childOnlyChange_leavesTheVersion_andTouchMovesItOnByExactlyOne`, `WorkOrderServiceTest.update_staleExpectedVersion_*`, `.plan_replayedKeyWithSamePayload_*` (mutation "replay sau version" ⇒ đỏ), `MaterialReservationServiceTest.reserve_staleWorkOrderVersion_*` |
| B209 | **Lịch đã chốt (D9).** Một công thức duy nhất `WorkOrderScheduler` cho release **và** `POST /work-orders/{W}/schedule-preview` (xem trước = đúng thứ release sẽ chốt; **không** chia `capacityUnits`, khác preview nhà máy G5). Release ghi `schedule_revision` +1, `schedule_calendar_revision` (fingerprint nội dung lịch — `WorkCalendarLookupService.calendarRevision`, SHA-256 16 hex, tính cả ca/nghỉ/ngày nghỉ vì chúng là dòng con không bump version), `schedule_time_zone`, `schedule_captured_at` (µs). Điều chỉnh tay (`ScheduleAdjustmentService`) cũng +1 revision, giữ calendar revision cũ. `0` lưu = `null` trên wire. `workMinutes` trên công đoạn = `ceil(setup + run × planned)` (giờ làm, không phải giờ đồng hồ). Preview chỉ cho `DRAFT`/`PLANNED`/`BLOCKED`, cảnh báo `NO_ROUTING`/`NO_WORK_CENTER`/`NO_CALENDAR`/`OVERLAP` (tải WO khác đã chốt cùng trung tâm, không tính chính nó) | `WorkOrderServiceTest.release_commitsScheduleRevisionCalendarRevisionAndTimeZone`, `WorkOrderSchedulePreviewServiceTest` (4), `WorkCalendarLookupServiceTest.calendarRevision_*` |
| B210 | **Huỷ WO (FE R06/Q21):** còn phiếu cấp `PENDING_APPROVAL` ⇒ 409 `STATE_CONFLICT`. Huỷ thành công trả quota hoạch định **một lần**: `WorkOrderPlanningLinkService.releaseWorkOrder` đánh dấu dòng ledger `WORK_ORDER` của WO `released_*` (giữ dòng, không xoá) + tăng `conversionVersion` của run — **cùng routine** với huỷ PO G7a (`PurchaseOrderPlanningLinkService.releaseTarget`). Phân bổ đơn bán không cần ghi: coverage đã bỏ WO `CANCELLED` (G6a). WO chuyển đổi one-shot trước G6b không có dòng ledger ⇒ không trả gì (cùng luật G7a). Hướng phụ thuộc mới `workorder → planning` (planning đã gọi workorder; cùng tiền lệ vòng `sales ↔ workorder`) | `WorkOrderServiceTest.cancel_withAnIssuePendingApproval_*`, `.cancel_unstartedWorkOrder_releasesItsPlanningQuotaWithTheReason`, `WorkOrderPlanningLinkServiceTest` (2) |
| B211 | **Khoá nguồn (FE Q7):** WO có `planningProposalId` ⇒ PATCH `plannedQuantity`/`outputWarehouseId` **khác giá trị hiện tại** ⇒ 409 `SOURCE_LOCKED` theo field. Ngày, ghi chú, kho vật tư vẫn sửa được (chuyển đổi không chụp kho vật tư nên không có gì của nguồn cần giữ) | `WorkOrderServiceTest.update_sourceLinkedDraft_*` (2) |

**Khác:** `POST /work-orders/{W}/release-reservations` giải phóng phần chưa cấp của mọi reservation `ACTIVE` (không biến động kho), được cả khi `COMPLETED`, từ chối `CANCELLED`/`CLOSED`. List WO thêm `fromDate`/`toDate` (ngày theo múi giờ nhà máy trên `createdAt`, nửa mở `[from 00:00, to+1 00:00)`, cast vế `is null` — `WorkOrderRepositoryIT.search_createdRange_isHalfOpenOnCreatedAt`). `WorkOrderResponse` thêm `version` ⇒ mọi đường ghi trả WO nay `saveAndFlush`/`flush` (bẫy §0.39).

## CF-5 — Một công thức lịch cho xem trước và release (2026-10-28)

Migration `V94` (`work_order_operations.captured_capacity_units`/`required_resource_minutes`/`active_elapsed_minutes`, nullable, CHECK cùng có/cùng
vắng, không backfill). Không quyền mới, không mã lỗi mới. **Thay B89 (phần "đi qua `WorkingWindowCalculator.advance`"), phần "không chia
`capacityUnits`" của B166 và B209.**

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B269 | **Một bước đi lịch duy nhất `CapacityPlacement.place`**: cửa sổ giờ làm việc (`computeWorkingIntervals` — gộp ca chồng, tính đuôi ca đêm) dùng theo thứ tự, `capacityUnits` song song, khúc cuối kết thúc sau `ceil(còn lại × 60 / units)` giây. Bản xem trước công suất nhà máy (G5), release và bản xem trước lịch của lệnh SX (G8a, cùng `WorkOrderScheduler`), và điều chỉnh lịch thiếu `plannedEndAt` (B167) đều gọi nó. `WorkOrderScheduler` lấy cửa sổ theo khúc 31 ngày, tối đa 3650 ngày (như `advance` cũ); công đoạn trên lịch **bắt đầu ở phút làm việc đầu tiên**; không lịch ⇒ giờ liên tục `ceil` giây; không trung tâm ⇒ 1 tổ. Release chụp `capturedCapacityUnits`/`requiredResourceMinutes`/`activeElapsedMinutes` lên công đoạn; điều chỉnh lịch dùng số tổ **đã chụp** (`null` = công đoạn trước `V94` ⇒ 1, đúng công thức lịch đó từng dùng). Đổi `capacityUnits` của trung tâm sau release **không** dời lịch đã chốt. `workMinutes` giữ nghĩa G8a (`ceil(setup + run × planned)`) | `CapacityPlacementTest`, `WorkOrderSchedulerTest`, ca so khớp xem trước ↔ release, `WorkOrderServiceTest.release_*capture*`, `ScheduleAdjustmentServiceTest.*captured*`, `FlywayMigrationIT.migrate_v94_*` |
| B270 | **Tải trên bảng công suất không chia** — vẫn `setup + run × planned` (phút công suất), chia theo phút làm việc của từng kỳ (B165). Lịch ngắn lại khi `capacityUnits > 1` nhưng tải không đổi; sức chứa ngày đã nhân `capacityUnits` | `CapacityBoardServiceTest` (tải 120 với 2 tổ) |
| B273 | **Bù hao hụt một công thức: `lượng cơ bản × quantityPer × (1 + scrapRate)`** ở BOM, MRP, lệnh SX, ước lượng, giá chuẩn; baseline giá thành **kế thừa** `requiredQuantity` đã lưu, không nhân lần hai (CF-11, PO chốt phương án A 2026-10-29). Lệnh SX làm tròn **`HALF_UP` 6 số lẻ tường minh** trong `WorkOrderService.requiredQuantity` (cả tạo từ BOM lẫn `recalculateRequirements`) ⇒ response lúc tạo = số lưu = số đọc lại (trước đây response in 12 chữ số lẻ, §0.60 #6). MRP giữ `CEILING` 6 (B179) ⇒ dòng lệnh SX có thể thấp hơn run đã đề xuất nó **tối đa 1e-6** — MRP lập kế hoạch dư, không bao giờ thiếu. Không có chỗ nào chia `(1 − rate)` | `WorkOrderServiceTest.create_requiredQuantityIsRoundedHalfUpToSixDecimals` (5 dòng, `1.2 × 1.000001 = 1.200001` phân biệt HALF_UP với CEILING), `.update_plannedQuantity_recalculatesRequiredQuantityWithTheSameRounding` (mutation bỏ `setScale` / HALF_UP→CEILING / chia `1 − rate` ⇒ đỏ) |
| B274 | **Duyệt phiếu nhập tăng `version` phiếu đúng +1, kể cả hàng không theo lô** (smoke CF-8 tìm ra, sửa 2026-10-30). `ProductionReceiptService.approve` đọc nguồn WIP (`sourceWipTraceIds`, một SELECT ⇒ auto-flush) **trước** mọi thay đổi. Trước đây nó chạy **giữa** `receipt.approve(...)` và `setSourceWipTraceIds(...)`: với hàng không lô, chưa có flush nào trước đó nên WO bẩn kéo theo một auto-flush ghi phiếu lần một, rồi `saveAndFlush` ghi lần hai ⇒ +2. Hàng theo lô tình cờ đúng vì tạo lô mới trong vòng `receive` đã flush sớm. 🔴 Bẫy +2 lần thứ tư (sau §0.61, §0.64, §0.68), dạng mới: không phải thứ tự `touch`/flush mà là **một SELECT nằm giữa hai lần đổi cùng entity** — rà lệnh nào có query giữa hai mutation | `ProductionFlowE2EIT` (luồng có lô + `productionFlow_fulfilsTheSalesOrderForOutputThatIsNotLotTracked`: version duyệt = gửi + 1, GET khớp; bỏ bản sửa ⇒ đỏ `expected 2, but was 3`) |

**Quyết định cần nhớ:** `calendarRevision` **không** gồm `capacityUnits` — số tổ đã nằm trên từng công đoạn. `predecessorOperationIds` vẫn chưa snapshot
lên công đoạn của lệnh SX (`C2-8b`).

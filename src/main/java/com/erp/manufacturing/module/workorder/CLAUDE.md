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

## Bất Biến QC Disposition (F2)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B38 | QC disposition chỉ chạy trên receipt `APPROVED`, và chỉ **một lần** — `qcResult != null` là chốt chặn. Receipt **giữ nguyên** `APPROVED` sau QC: QC phán quyết trên *lot*, không đảo ngược tồn kho đã nhập | `ProductionReceiptServiceTest.qcDisposition_onPendingApprovalReceipt_*`, `.qcDisposition_twice_*` |
| B39 | **[FE4-5B3 – viết lại nhánh không lot]** Output **có lot**: QC `AVAILABLE`/`REJECTED` không đổi `stock_balances.quantity`, chỉ đổi `lot.status`. Output **không lot/serial**: approve đã cộng `quality_hold_quantity`; QC `AVAILABLE` trừ đúng lượng đó để mở available nhưng không đổi on-hand; QC `REJECTED` giữ nguyên cả on-hand và hold, không sinh `ADJUST_OUT`. Hàng hỏng vẫn truy vết được nhưng không reserve/issue/MRP dùng được | `ProductionReceiptServiceTest.qcDisposition_available_onOutputWithoutALot_releasesQualityHoldAndFulfils`, `.qcDisposition_rejected_onOutputWithoutALot_keepsQualityHoldAndNeverFulfils`, `ProductionFlowE2EIT.qcRejection_onOutputThatIsNotLotTracked_keepsTheGoodsOnQualityHold` |
| B40 | **[D5 + FE4-5B3]** QC áp dụng cho **mọi** output; điều kiện rẽ nhánh là **cấp dòng** (`line.lot != null` / `line.serial != null`). **Có lot** ⇒ mọi lot phải đang `HOLD`, validate toàn bộ trước khi đổi. **Không lot/serial** ⇒ phán quyết authoritative nằm trên receipt (`qc_result`/`qc_reason`/`qc_at`/`qc_by`), `outputLotStatus` vẫn null, không sinh `LOT_STATUS_CHANGE`/`quality_dispositions`; `APPROVED + qcResult == null` nghĩa là `PENDING_QUALITY` và lượng giữ nằm ở `StockBalance.qualityHoldQuantity` | `ProductionReceiptServiceTest.qcDisposition_lotNoLongerOnHold_shouldThrowLotNotEligibleBeforeAnyChange`, `.qcDisposition_available_onOutputWithoutALot_releasesQualityHoldAndFulfils`, `.qcDisposition_onOutputWithoutALot_*` |
| B41 | Receipt của item lot-tracked **bắt buộc** có `lotCode`/`lotId` ngay ở bước `post` (`LOT_REQUIRED`, 400) — không có lot thì output không bao giờ ra khỏi `HOLD` được | `ProductionReceiptServiceTest.post_lotTrackedOutputWithoutLot_shouldThrowLotRequired` |

### Entry point cho module khác (`C2-2`, 2026-08-06)

`LotQcOriginLookupService` (`service/query/`) là **cách duy nhất** module khác đọc "lot này có phải
QC trước khi thoát `HOLD` không" — `module/inventory` gọi nó từ `InventoryLotService.changeStatus`
để gác `POST /inventory/lots/{lotId}/status` (rule `C7`; bất biến **B102-B103** ở
`module/inventory/CLAUDE.md`). Chỉ một method: `requiresQcDispositionBeforeRelease(UUID lotId)` —
`true` khi lot có `ProductionReceiptLine` tham chiếu **và** chưa từng có `QualityDisposition`. Đây là
hướng phụ thuộc **mới** `inventory → workorder`, ngược với phần lớn quan hệ hiện có (`workorder`
thường gọi vào `inventory`) — hợp lệ theo `C7` vì đi qua lookup service, không phải repository.
`ProductionReceiptLineRepository.existsByLotLotId`/`QualityDispositionRepository.existsByLotLotId`
là hai derived query mới hỗ trợ nó, không có `*IT` riêng (derived query đơn giản, không phải `@Query`
JPQL phức tạp — `R7` không áp dụng).

## Bất Biến Production Execution + Work Order Operations (F5)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B53 | **Chỉ** `ProductionExecutionService.report` làm work order tiến triển: cộng `actualGood/Scrap/Rework`, `markInProgress`, và `COMPLETED` khi cumulative good = planned — **độc lập hoàn toàn với receipt**. Đây là bằng chứng đảo ngược ngữ nghĩa của `F5`.<br>**[D11]** `COMPLETED` ở đây nghĩa là **xưởng làm xong**, *không* phải "hàng đã vào kho" — `completedQuantity` là cột riêng và luôn đi sau. Vì vậy `COMPLETED` **không** đóng đường receipt (`canReceipt()`, B13 nhánh 3). Đây chính là mâu thuẫn `B13`↔`B53` của nợ #25, đã gỡ bằng cách nới gate chứ **không** hoãn `complete()`: hoãn thì WO làm xong mà chưa ai nhập kho sẽ kẹt `IN_PROGRESS` vô thời hạn và đổi nghĩa một cột đã có dữ liệu lịch sử | `ProductionExecutionServiceTest.report_cumulativeGoodReachesPlanned_completesWorkOrderWithoutAnyReceipt`, `ProductionReceiptServiceTest.approve_onCompletedWorkOrder_warehousesTheOutputAndRaisesCompletedQuantity` |
| B54 | Cumulative good **không được vượt** `plannedQuantity` ⇒ `PLANNED_QUANTITY_EXCEEDED` (409), và fail **trước** khi ghi bất cứ thứ gì (C9) | `ProductionExecutionServiceTest.report_cumulativeGoodOverPlanned_throwsPlannedQuantityExceededBeforeAnyWrite` |
| B55 | Mỗi report ghi WIP ledger đầy đủ: good → `OUTPUT_COMPLETED`, scrap → `SCRAP_REPORTED`, rework → `REWORK_REPORTED`. Số lượng bằng 0 **không** sinh dòng ledger rác. Receipt approve ghi loại **khác**: `OUTPUT_RECEIPTED` | `ProductionExecutionServiceTest.report_scrapAndRework_doNotCountTowardsCompletion` |
| B56 | `work_order_operations` là **snapshot bất biến** của routing lúc tạo WO — copy giá trị, `sourceRoutingOperationId` chỉ để truy vết, **không** dereference. Cùng hợp đồng với B49 | `WorkOrderServiceTest.create_snapshots*` |
| B57 | Report/WIP chỉ được gắn vào operation **thuộc chính work order đó**; operation của WO khác ⇒ `RESOURCE_NOT_FOUND`, không ghi gì | `ProductionExecutionServiceTest.report_operationOfAnotherWorkOrder_throwsResourceNotFound` |
| B76 | **[F8]** Candidate của màn hình **tạo receipt** (`GET /production-receipts/candidates`, spec §6.2) có **ba** vế: ① status ∈ `canReceipt()` = {`RELEASED`, `IN_PROGRESS`, **`COMPLETED`**}; ② `actualGoodQuantity − completedQuantity − Σ(receipt `DRAFT` + `PENDING_APPROVAL`) > 0`; ③ đúng plant.<br>🔴 **Vế ① NGƯỢC với `B75`** — đừng copy query kia sang đây. `B75` loại `COMPLETED` vì không được **report** thêm; `B76` **phải giữ** nó vì hàng đã làm ra vẫn chưa vào kho (`completedQuantity` luôn đi sau, `CLAUDE.md §0.5`). Bỏ `COMPLETED` = dựng lại đúng nợ #25 (`D11`) ở tầng read model: dòng receipt **cuối cùng** của mọi WO biến mất khỏi màn hình.<br>🔴 **Vế ② phải nằm trong JPQL, không trong mapper.** `WorkOrder.availableToReceipt()` **không** trừ receipt mở (`B16` để việc đó cho caller) ⇒ mapper một mình không ra được con số dòng. Netting đặt trong query để gate có **một** định nghĩa; `ProductionReceiptRepository.sumOpenQuantityByWorkOrderIds` (batch, `C14`) chỉ để **hiển thị** đúng con số đó. Thiếu vế này ⇒ FE hiện một dòng mà `postNew` **chắc chắn** từ chối bằng `PLANNED_QUANTITY_EXCEEDED`.<br>🔴 Cả ba vế nằm trong **JPQL** ⇒ mock repository không kiểm được, bắt buộc test ở tầng repository (`R7`) | `WorkOrderRepositoryIT.findReceiptCandidates_*` (5 case: trả về WO còn hàng chưa nhập kho, **trả về `COMPLETED`**, loại WO bị draft claim hết, loại status không receipt được, loại plant khác), `ProductionReceiptServiceTest.listCandidates_asksForCompletedWorkOrdersToo_unlikeTheExecutionCandidates` + `.listCandidates_reportsTheCeilingNetOfOpenReceipts_inOneBatchQuery`, `ProductionReceiptRepositoryIT.sumOpenQuantityByWorkOrderIds_*` |
| B75 | **[F7]** Candidate của màn hình post sản lượng (`GET /production-executions/candidates`, spec §5.1) có **hai** điều kiện, không phải một: status ∈ {`RELEASED`, `IN_PROGRESS`} **và** `actualGoodQuantity < plannedQuantity`. Vế thứ hai không phải suy diễn — §5.1 cấm cumulative good vượt plan (`B54`), nên WO đã đạt plan **không nhận report được nữa**; đưa nó vào danh sách là đưa cho operator một nút bấm luôn lỗi. 🔴 Cả hai vế nằm trong **JPQL** ⇒ mock repository không kiểm được, bắt buộc test ở tầng repository (`R7`) | `WorkOrderRepositoryIT.findExecutionCandidates_*` (4 case: 2 status hợp lệ, loại WO đạt plan, loại status khác, loại plant khác), `ProductionExecutionServiceTest.listCandidates_asksOnlyForReportableStatuses_andReportsWhatIsLeftToProduce` |

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

## Bất Biến Costing (P3)

> Thiết kế đầy đủ (roll-up BOM, entry points, quyết định permission): `module/costing/CLAUDE.md`.
> Bảng dưới chỉ liệt kê phần thuộc `workorder`: hai hook tích luỹ + phần mở rộng variance.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B93 | Hai hook tích luỹ chi phí thực tế, mỗi cái đặt **đúng trong** guard chống double-count đã có sẵn của luồng đó — không thêm guard riêng: `MaterialIssueService.postNew` cộng `quantity × findStandardUnitCost(component)` vào `materialCostAccumulated`, bên trong `if (movementResult.created())` (cùng điều kiện bảo vệ `addIssuedQuantity`/`markInProgress`); `ProductionExecutionService.reportNew` cộng `good × (laborCost + overheadCost)` của **product item** (không phải component) vào `laborCostAccumulated`/`overheadCostAccumulated`, chỉ khi `good > ZERO` — scrap/rework-only report không tạo dòng `WorkOrderCostAccumulator` | `MaterialIssueServiceTest.post_createdMovement_accumulatesMaterialCostExactlyOnce`, `.postInternal_replayedIdempotencyKey_doesNotDoubleAccumulateMaterialCost`, `ProductionExecutionServiceTest.report_good_accumulatesLaborAndOverheadCost`, `.report_scrapAndReworkOnly_accumulatesNothing` |
| B94 | `GET /work-orders/{id}/variance` mở rộng: `materialLines[].usageVarianceCost = varianceQuantity × findStandardUnitCost(component)` (Material Usage Variance — **không** có Price Variance, xem `module/costing/CLAUDE.md` mở đầu); `costVariance.standard* = findStandardCostBreakdown(productItem) × plannedQuantity`; `costVariance.actual*` đọc `WorkOrderCostAccumulator` — **không tồn tại** ⇒ cả ba field actual là `ZERO` (WO chưa issue/report gì), không phải lỗi | `WorkOrderVarianceServiceTest.getVariance_calculatesMaterialOutputAndWipSummary` (usageVarianceCost bằng số thật, rule R6), `.getVariance_costVariance_standardIsBreakdownTimesPlannedQuantity_actualIsZeroWithoutAnAccumulator`, `.getVariance_costVariance_actualReadsTheAccumulatorRowWhenItExists` |

**Xác nhận qua HTTP thật** (`mvn -o spring-boot:run`, Postgres/Redis thật qua `docker-compose up -d`):
tạo item nguyên liệu (`materialCost=5`) + item lắp ráp (`labor=3`, `overhead=1`) + BOM 2:1 → `GET
.../standard-cost` của item lắp ráp trả `totalStandardCost=14` (2×5+3+1, tính lại lúc đọc) → tạo/
release/reserve/issue 4kg (kế hoạch 6kg) → `GET .../variance` trả `usageVarianceCost=-10`
(-2×5) và `actualMaterialCost=20` (4×5) **trước khi** report sản lượng → report `good=2` → variance
đọc lại: `actualLaborCost=6` (2×3), `actualOverheadCost=2` (2×1), `totalCostVariance` cập nhật đúng
— xác nhận cả hai hook chạy đúng qua ledger/DB thật, không chỉ qua mock.

## Bất Biến Serial Tracking (P5, 2026-08-06)

> Thiết kế đầy đủ (mirror `InventoryLot`, quyết định chốt với user): `module/inventory/CLAUDE.md`
> mục Serial Tracking + `CLAUDE.md §0.33`. Bảng dưới chỉ liệt kê phần thuộc `workorder`: receipt/issue
> ràng buộc quantity=1, và **quyết định cắt phạm vi QC quan trọng nhất của phase**.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B98 | 🔴 **Serial-tracked output KHÔNG có HOLD chờ QC** — khác hẳn lot (`B18`). Chốt với user
(AskUserQuestion) để tránh phải thêm `serial_id` vào `stock_balances` + viết lại mọi
`StockBalanceRepository.aggregate*`. `approve()` mở serial ở `AVAILABLE` ngay (không nhận tham số
`initialStatusForNewLot` như lot) ⇒ hàng khả dụng ngay khi approve. Khác output `NON_TRACKED` sau
`FE4-5B3`, serial vẫn không dùng `quality_hold_quantity` (`B98`). `qcDisposition` (`dispositionSerials`, mirror
`dispositionWithoutLots`) vẫn chạy: `AVAILABLE` chỉ ghi verdict + fulfill; `REJECTED` **phải** rút
hàng bằng `ADJUST_OUT` (không có gate trạng thái nào ngăn hàng lỗi được xuất) **cộng thêm** flip
`serial.status = REJECTED` cho mục đích truy xuất nguồn gốc đơn vị — mutation trực tiếp qua entity
đã managed trong transaction (dirty-checking), không qua `InventoryMovementService.changeLotStatus`
vì serial không cần sinh dòng ledger `LOT_STATUS_CHANGE`-tương-đương | `ProductionReceiptServiceTest.qcDisposition_available_onSerialTrackedOutput_fulfilsAndMarksSerialAvailable`, `.qcDisposition_rejected_onSerialTrackedOutput_withdrawsAndMarksSerialRejected` |
| B99 | Receipt/issue của item serial-tracked luôn `quantity = 1` — enforce ở **hai** nơi: `ProductionReceiptService.postNew` (`SERIAL_REQUIRED` 400 nếu thiếu `serialNumber`, `OPERATION_NOT_ALLOWED` 422 nếu `quantity != 1` — mirror `B41`/`LOT_REQUIRED` **nhưng ở tầng cao hơn**, tương tự cách `LOT_REQUIRED` chỉ tồn tại ở đây chứ không ở `InventoryMovementService`) và `InventoryMovementService` tự nó (`ensureSerialQuantityIsOne`, `OPERATION_NOT_ALLOWED`, defense-in-depth cho mọi caller khác). Nhận/xuất N đơn vị serial-tracked = N receipt/N dòng issue riêng — **không** có shape `List<serialId>` nào trong DTO, giữ `MaterialIssueLineRequest`/`ProductionReceiptPostRequest` **hoàn toàn additive** (không phải breaking change như phác thảo roadmap ban đầu dự đoán) | `ProductionReceiptServiceTest.post_serialTrackedOutputWithoutSerial_shouldThrowSerialRequired`, `.post_serialTrackedOutputWithQuantityOtherThanOne_shouldThrowOperationNotAllowed`, `MaterialIssueServiceTest.post_serialTrackedComponent_threadsSerialIdIntoIssueCommandAndOntoLine` |

**Quyết định cần nhớ:**

1. `MaterialIssueFlatRequest`/`postFlat` **không** đổi — không có chỗ cho `serialId`. Issue một
   component serial-tracked qua đường flat sẽ tự nhiên rơi vào `resolveExistingSerial`'s `serialId ==
   null` branch và ném `OPERATION_NOT_ALLOWED` — **không** cần thêm code đặc biệt nào ở
   `MaterialIssueService` để chặn đường này, đúng như thiết kế đã dự đoán.
2. Reservation **không** biết serial nào sẽ được issue — chỉ giữ số lượng trên bucket chung. Serial cụ
   thể luôn đến từ `MaterialIssueLineRequest.serialId` do người dùng chọn tại thời điểm issue, kể cả
   khi line đó cũng mang `reservationId`.
3. `SerialNumber` không có FK `warehouse` (giống `InventoryLot`) — vị trí kho suy ra từ
   `StockMovement.warehouse`/`stock_balances`, không lưu trùng trên serial.

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

## G8b — Cấp vật tư: phiếu bổ sung, kiểm lại lúc duyệt, huỷ của người tạo, chi tiết + ứng viên nguồn (2026-10-19)

Nguồn: `FE_HANDOFF_GD_08_2026-10-14.md` R07–R09, Q3/Q8–Q11/Q33. Migration `V80` (`material_issues.supplemental`, `cancellation_reason`).

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B212 | **Version bắt buộc trên phiếu cấp nhiều dòng (D5):** `POST /work-orders/{W}/material-issues` cần `expectedWorkOrderVersion`; duyệt/từ chối/huỷ cần **cả** `expectedWorkOrderVersion` **và** `expectedIssueVersion` (phiếu có `@Version` riêng). Mọi lệnh ghi phiếu bump version WO **đúng một** lần. 🔴 Post/duyệt chuyển WO `RELEASED → IN_PROGRESS`: phải `markInProgress()` + `touch()` **trước** vòng lặp ghi tồn và cuối chỉ `flushTouched` — `touchAndFlush` sau `saveAndFlush(issue)` làm version +2 (smoke `220136`; guard `ProductionFlowE2EIT`). Huỷ/từ chối/phiếu chờ không đổi WO nên `touchAndFlush` vẫn đúng. Đường phẳng `POST /material-issues` (một reservation) và `component-issues` **vẫn không** đòi version (gọi `postInternal(…, false)`) | `MaterialIssueServiceTest.post_withoutAWorkOrderVersion_isRefusedNamingTheField`, `.approve_staleIssueVersion_*` |
| B213 | **Vượt BOM xét theo vật tư trên cả phiếu** (gross đã cấp + mọi dòng của phiếu cho dòng vật tư đó vs `requiredQuantity`), không từng dòng. Có vượt: `supplemental = false` **tường minh** ⇒ **409 `OVER_BOM_APPROVAL_REQUIRED`** field `lines[i].quantity` (phiếu thường không tự thành phiếu chờ); `true` hoặc **vắng** (client cũ) ⇒ **cả phiếu** `PENDING_APPROVAL`, mọi dòng của vật tư vượt `overIssue = true`. Gross **không** trừ hoàn (Q11) | `.post_twoLinesThatTogetherGoOverTheBom_*`, `.post_normalIssueOverTheBom_isRefusedInsteadOfTurningPending` |
| B214 | **Quy tắc dòng, kiểm cả phiếu trước khi ghi (C9):** phiếu bổ sung không được nêu `reservationId`, mỗi dòng cần `reasonCode` + `overrideReason`; dòng thường **không** reservation phải lấy đúng kho của dòng vật tư nếu dòng có kho (D2) ⇒ **422 `RESOURCE_SCOPE_MISMATCH`** `lines[i].warehouseId` (phiếu bổ sung lấy được mọi kho `ACTIVE` của nhà máy — Q8); `sourceExecutionId` chỉ với `REWORK`; lý do 5–2000 sau trim (`ReasonRules`, Q33). Phiếu chờ **không** giữ tồn nhưng nguồn tồn tự do phải đủ lúc tạo (Q10) — cộng theo nguồn `(vật tư, kho, lô)` ⇒ 409 `INSUFFICIENT_AVAILABLE_STOCK` `lines[i].quantity` | `.post_supplementalLines_*`, `.post_normalLineFromAnotherWarehouse*`, `.post_sourceExecutionWithoutReworkReason_*`, `.post_pendingRequestWhoseSourceHoldsTooLittle_*` |
| B215 | **Duyệt kiểm lại** version + trạng thái WO + tồn hiện tại trong transaction ghi; thiếu một dòng ⇒ rollback cả phiếu, phiếu giữ `PENDING_APPROVAL`. **Tự duyệt** phiếu của chính mình cần thêm `PERM_MATERIAL_ISSUE_OVERRIDE` (Q3) — không phải tên role ⇒ 403 `PERMISSION_DENIED`. **Huỷ** (`/cancel`): chỉ người tạo (`created_by`), `PENDING_APPROVAL → CANCELLED`, lý do bắt buộc 5–2000, **không** biến động kho; người khác (kể cả ADMIN) ⇒ 403 — quản lý dùng `reject`. Replay `Idempotency-Key` kiểm **trước** version | `.approve_ownRequestWithoutOverride_*`, `.cancel_byTheRequester_*`, `.cancel_bySomeoneElse_isDenied` |
| B216 | **Đọc:** `GET /material-issues/{I}` + list theo WO/nhà máy mở cho **bất kỳ** `MANAGE`/`APPROVE`/`OVERRIDE` (`hasAnyWorkOrderAccess`/`hasMaterialIssueAccess`) — hàng đợi duyệt mở cho quản lý không cấp vật tư. Chi tiết kèm `decisionContext[]` theo dòng vật tư: required / gross / batch / `extraRequestedQuantity` (**chỉ** phiếu chờ, `null` khi đã quyết) / tồn hiện tại của nguồn (snapshot, mỗi nguồn đếm một lần). Ứng viên nguồn `GET /work-orders/{W}/material-issue-candidates?mode=NORMAL\|SUPPLEMENTAL` (quyền `MANAGE`): `NORMAL` = reservation `ACTIVE` của WO, gợi ý `min(còn lại, required − gross)`, lô không đạt vẫn liệt kê kèm `ineligibleReason`; `SUPPLEMENTAL` = tồn tự do đạt chuẩn (FEFO, loại lô quá hạn) ở mọi kho của nhà máy, không gợi ý. List nhà máy thêm `search` (mã phiếu / số WO) + `fromDate`/`toDate` (ngày nhà máy trên `requestedAt`, nửa mở) | `WorkOrderPermissionGuardTest` (4), `MaterialIssueCandidateServiceTest` (2), `.getDetail_*` (2), `MaterialIssueRepositoryIT.findByPlant_searchesCodeAndWorkOrderNumber_*` |

## G8c — Công đoạn: đầu vào/chuyển giao, retry, vật tư thực dùng, sản lượng cuối, hoàn thành có hụt (2026-10-20)

Nguồn: `FE_HANDOFF_GD_08_2026-10-14.md` R10–R14, Q4/Q14–Q18/Q22/Q23/Q31. Migration `V81` (`work_orders.operation_tracking` + `terminal_shortfall_quantity`/`shortfall_reason`, `work_order_operations.completed_at`/`completed_by`, `production_executions.source_execution_id`, bảng `work_order_operation_inputs` + `production_execution_consumptions`).

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B217 | **Một nguồn sự thật cho công đoạn (D1):** mọi số của một công đoạn (đầu vào, đã xử lý mới, còn lại, tốt/phế, làm lại lịch sử/chờ, đã chuyển/còn chuyển) **dẫn xuất** từ sổ `work_order_operation_inputs` + `production_executions` (`OperationLedger`, 4 query/WO bất kể số công đoạn). Thứ **duy nhất** lưu là lần kết thúc (`completed_at`). Trạng thái: `COMPLETED` nếu đã kết thúc, `IN_PROGRESS` nếu có đầu vào hoặc execution, `READY` nếu là công đoạn đầu hoặc công đoạn trước đã kết thúc, còn lại `PENDING`. 🔴 Lệch khuyến nghị D1(a) (thêm bộ đếm lên cột) có chủ đích: cột đếm là nguồn thứ hai có thể trôi khỏi sổ | `OperationServiceTest.list_derivesStatusAndPoolsFromTheLedgers`, `OperationLedgerRepositoryIT` (4) |
| B218 | **Hai nhóm, không gộp (D7):** execution mới (`sourceExecutionId` null) dùng `input − fresh processed` của công đoạn; retry dùng `rework − đã xử lý lại` của **đúng** execution nguồn (cùng WO, cùng công đoạn, sai ⇒ 422 `RESOURCE_SCOPE_MISMATCH` field `sourceExecutionId`). Retry không bao giờ trừ đầu vào. Làm lại chờ của công đoạn = tổng làm lại − tổng retry (retry chỉ trỏ tới nguồn cùng công đoạn). Vượt ⇒ 409 `PLANNED_QUANTITY_EXCEEDED` field `goodQuantity` | `ProductionExecutionServiceTest.report_freshWorkOverTheConfirmedInputLeft_*`, `.report_retryWithinTheSourcesPendingRework_*`, `.report_retryOverTheSourcesPendingRework_*`, `.report_retryOfAnExecutionOfAnotherOperation_*` |
| B219 | **Sản lượng cuối = công đoạn cuối (R12):** `actualGoodQuantity` của WO chỉ cộng good của công đoạn **cuối**; good công đoạn trước là dở dang chuyển tiếp. Phế/làm lại vẫn là tổng mọi công đoạn. Nhân công/chi phí chung chỉ hấp thụ trên good cuối. `COMPLETED` tự động khi good cuối chạm kế hoạch giữ nguyên (`B53`, D6) | `.report_goodOnAnEarlierOperation_isNotFinalGoodAndAbsorbsNoLabour`, `.report_goodOnTheLastOperation_isFinalGood` |
| B220 | **Vật tư thực dùng (R13, D4):** `consumption[]` chỉ trỏ dòng cấp của **chính WO** (khác ⇒ 422 `RESOURCE_SCOPE_MISMATCH`), phiếu phải `POSTED` (⇒ 409 `STATE_CONFLICT`); nhiều mục cùng dòng **cộng trước** khi so trần `đã cấp − đã dùng` (vượt ⇒ 409 `PLANNED_QUANTITY_EXCEEDED` field `consumption[i].quantity`); ghi **một** hàng/dòng cấp/execution, cùng transaction với execution, **không** biến động kho. Mảng rỗng/vắng hợp lệ (Q18). Chi phí vật tư vẫn tính **lúc cấp** (P3) — consumption là truy vết, không phải con số chi phí thứ hai | `.report_duplicateConsumptionOfOneLine_*`, `.report_consumptionWithinTheLineLeft_*`, `.report_consumptionOfAnotherWorkOrdersLine_*`, `.report_consumptionOfAPendingIssue_*` |
| B221 | **Đầu vào / kết thúc / hoàn thành:** đầu vào công đoạn đầu cần ít nhất một phiếu cấp `POSTED` (⇒ 409 `MATERIAL_ISSUE_REQUIRED`) và tổng ≤ kế hoạch (Q14, không suy từ lượng vật tư); công đoạn sau chỉ nhận khi công đoạn trước đã kết thúc (⇒ 409 `OPERATION_SEQUENCE_CONFLICT`, Q15), tối đa `good − đã chuyển` của công đoạn trước. Kết thúc cần còn lại = 0, làm lại chờ = 0 và mọi good của công đoạn trước đã chuyển vào; công đoạn **đầu** cần tổng đầu vào **đúng bằng** kế hoạch (FE A25 — thiếu thì ghi phế, không dừng đầu vào); công đoạn sau công đoạn good 0 kết thúc được với 0 (Q16); kết thúc được cả khi WO đã `COMPLETED`. `complete-production` (`PERM_WORK_ORDER_MANAGE`): WO `RELEASED`/`IN_PROGRESS`, không phiếu chờ, mọi công đoạn đã kết thúc; hụt `max(0, kế hoạch − good cuối)` > 0 cần lý do 5–2000 **và** `PERM_MATERIAL_ISSUE_APPROVE` (Q4, thiếu ⇒ 403); chụp hụt + lý do một lần; **không** nhận kho/đóng/biến động. Báo sản lượng: `actualEndedAt` ≤ now + 60s (Q31). WO đã báo sản lượng trước `V81` (`operation_tracking = false`): đầu vào/cap = `null`, đầu vào/retry/consumption ⇒ 409 `PRODUCTION_INPUT_UNKNOWN` (Q22), báo sản lượng giữ luật F5. Mọi lệnh bump version WO **đúng một** lần (touch trước mọi ghi, cuối `flushTouched`, `B212`) | `OperationServiceTest` (24), `.report_endTime*` (2), `.report_consumptionOnAWorkOrderWithoutInputEvidence_*`, `FlywayMigrationIT.migrate_v81_*`, `ProductionFlowE2EIT` (version +1 sau báo sản lượng) |

## G8d — Dở dang + hoàn vật tư (2026-10-20)

Nguồn: `FE_HANDOFF_GD_08_2026-10-14.md` R15–R17, Q11/Q13/Q19/Q22/Q23/Q34–Q37, A29–A32. Migration `V82` (bảng `material_returns` + `material_return_lines`, `MATERIAL_RETURNED` vào CHECK của `wip_transactions`, quyền `PERM_WIP_READ` / `PERM_MATERIAL_RETURN_READ` / `_MANAGE` cho ADMIN + MANAGER).

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B222 | **Một sổ cho vật tư của dòng cấp:** `chưa dùng = đã cấp − đã dùng − đã hoàn` của **một** dòng cấp `POSTED`, dẫn xuất từ `material_issue_lines` + `production_execution_consumptions` + `material_return_lines` (`MaterialSourceLedger`, 3 query/WO), không lưu bộ đếm. Cùng công thức là trần của `consumption[]` khi báo sản lượng (G8c B220 nay trừ cả phần đã hoàn) **và** trần của phiếu hoàn. WO báo sản lượng trước `V81` (`operation_tracking = false`): đã dùng / chưa dùng / có thể hoàn = `null`, không bao giờ đoán 0 hay "toàn bộ đã cấp" (Q22) | `MaterialReturnServiceTest.candidates_*` (2), `ProductionExecutionServiceTest.report_consumptionOfMaterialAlreadyReturned_isOverTheCap`, `MaterialReturnRepositoryIT.returnLines_*` |
| B223 | **Hoàn về đúng nguồn (R16, R17, D3):** mỗi dòng hoàn trỏ một dòng cấp `POSTED` của **chính WO** (khác ⇒ 422 `RESOURCE_SCOPE_MISMATCH` field `lines[i].sourceIssueLineId`); hàng về **đúng** kho + lô của dòng cấp bằng biến động `RECEIVE` (`reference_type = MATERIAL_RETURN`, `reference_id` = phiếu hoàn); client không chọn được nguồn khác. Nguồn phải nhận lại được **ngay lúc hoàn**: vật tư + kho `ACTIVE` (⇒ 422 `RESOURCE_INACTIVE`), lô `AVAILABLE` và chưa hết hạn (⇒ 409 `LOT_NOT_ELIGIBLE` — không lối tắt vòng qua QC, A31), không phải dòng theo số sê-ri (⇒ 422 `OPERATION_NOT_ALLOWED`). Mục cùng dòng **cộng trước** khi so trần (vượt ⇒ 409 `PLANNED_QUANTITY_EXCEEDED` field `lines[i].quantity`). Hoàn **không** giảm `issuedQuantity` của dòng vật tư (Q11 — vượt định mức vẫn xét theo tổng đã cấp) và **không** khôi phục giữ tồn (R17). WO phải `IN_PROGRESS` hoặc `COMPLETED` (Q23; khác ⇒ 409 `STATE_CONFLICT`) và theo dõi được (⇒ 409 `PRODUCTION_INPUT_UNKNOWN`) | `MaterialReturnServiceTest` (16), `ProductionFlowE2EIT` (hoàn 4 qua HTTP, tồn +4) |
| B224 | **Lệnh hoàn:** `Idempotency-Key` **bắt buộc**, lưu trên chính phiếu (`UNIQUE`) với dấu vân tay gồm cả WO — gửi lại cùng key + body ⇒ phiếu cũ, không biến động thứ hai; khác body ⇒ 409 `IDEMPOTENCY_CONFLICT`. Kiểm `expectedWorkOrderVersion`, lý do 5–2000 (`ReasonRules`, thiếu ⇒ 400 `APPROVAL_REASON_REQUIRED` field `reason`), rồi mọi dòng **trước** khi ghi (C9). Phiếu, biến động kho, dòng sổ WIP `MATERIAL_RETURNED` và version WO đi cùng một transaction; WO `touch()` **trước** flush đầu tiên ⇒ version +1 đúng một lần (`B212`). Phiếu `POSTED` khi ghi, không sửa/xoá (Q36). Mã `MR-` + 8 hex id sinh ở `@PrePersist` | `.post_replayedKey_*`, `.post_replayedKeyWithAnotherBody_*`, `.post_withAStaleVersion_*`, `.post_withoutAReason_*`, `ProductionFlowE2EIT` (replay, version +1) |
| B225 | **Dở dang là mô hình đọc, không lưu (R15, D8):** `GET /plants/{P}/wip` (mặc định `RELEASED`/`IN_PROGRESS`/`COMPLETED`, làm lại chờ của dòng = tổng làm lại − tổng retry đã xử lý, một query/trang) và `GET /work-orders/{W}/wip?section=` đọc trực tiếp sổ có sẵn; phần không hỏi là `null` và **không** được đọc. `availableToReceipt = good cuối − đã nhập kho` (phiếu nhập mở báo riêng `openReceiptQuantity`, không trừ — `B16`). Không trường giá trị nào (D10). `HISTORY` không có — lịch sử là các danh sách chứng từ | `WorkOrderWipServiceTest` (6), `MaterialReturnRepositoryIT.searchWip_*`, `.sumReworkByWorkOrderIds_*` |

> 🔴 **Giới hạn đã biết, không sửa trong G8d:** variance (`WorkOrderVarianceService`) và chi phí vật tư thực tế (`WorkOrderCostAccumulator`, P3) vẫn tính theo lượng **đã cấp** — vật tư đã hoàn vẫn bị tính là đã dùng ở hai chỗ đó. Đổi là đổi số variance/chi phí, quyết định riêng.

## G9a — Phiếu nhập thành phẩm: vòng đời, version, hạn mức nhập, đọc (2026-10-21)

Nguồn: `FE_HANDOFF_GD_09_2026-10-15.md` R01–R08, R17–R21, Q1–Q5/Q7–Q9/Q11–Q14/Q23/Q25. Migration `V83` (`received_on` backfill rồi `NOT NULL`, `expires_on`, ai/khi/lý do của gửi/từ chối/rút/huỷ, quyền `PERM_PRODUCTION_RECEIPT_READ` / `PERM_PRODUCTION_QC_READ` cho ADMIN + MANAGER).

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B226 | **Một định nghĩa hạn mức nhập (`ReceiptQuota`):** `G` = good cuối (`actualGoodQuantity`, B219), `C = min(P, G)`, `A` = đã nhập (`completedQuantity`), `D` = tổng phiếu `DRAFT` + `PENDING_APPROVAL` (B16), `W = max(0, C − A)`, `U = max(0, C − A − D)`. Tạo phiếu: `q ≤ U`; sửa nháp: `q ≤ U` sau khi trả lại phần của chính nó; duyệt: `A + q ≤ C` kiểm lại trên WO hiện tại (vượt ⇒ 409 `PLANNED_QUANTITY_EXCEEDED` field `quantity`, trước mọi biến động). Rút giữ phần đã giữ; huỷ/từ chối trả đúng một lần vì phiếu không còn mở. Danh sách ứng viên lọc trong JPQL bằng **cùng** công thức. 🔴 `A ≤ G ≤ P` là CHECK của DB (`V14`, `V32`) — `C` thực tế bằng `G`; đừng viết nhánh "G > P" (không bao giờ chạy được) | `ReceiptQuotaTest` (5), `ProductionReceiptServiceTest.post_aboveWhatIsUnclaimed_*`, `.update_quotaExcludesTheDraftsOwnClaim`, `.approve_overTheCurrentCap_*`, `.get_reportsTheQuotaAndTheRequestersActions`, `WorkOrderRepositoryIT.findReceiptCandidates_*` |
| B227 | **Mọi lệnh phiếu nhập sau khi tạo mang hai version** (`expectedVersion` của phiếu + `expectedWorkOrderVersion`; thiếu ⇒ 400 field, lệch ⇒ 409 `CONCURRENT_MODIFICATION`); tạo mang `expectedWorkOrderVersion` (đường nội bộ `completeOutput` thì không). Khuôn G8: replay `Idempotency-Key` (bảng `work_order_idempotency_records`, lệnh `RECEIPT_*`; tạo vẫn dùng key trên chính phiếu) → version → luật → `touch()` WO → một lần flush ⇒ version WO **+1 đúng một lần** cho mọi lệnh (FE Q12). Duyệt: `completedQuantity` + `touch()` **trước** vòng biến động (bẫy +2 của G8b). Response mang `version`/`workOrderVersion` đã ghi (`saveAndFlush`, bẫy §0.39) | `.post_staleWorkOrderVersion_*`, `.submit_staleReceiptVersion_*`, `.approve_raisesCompletedQuantityBeforeTheMovementCanFlushTheWorkOrder`, `.approve_replayedKey_runsNothingAgain`, `ProductionFlowE2EIT` (+1 sau tạo và sau duyệt) |
| B228 | **Ai làm gì:** sửa / gửi / rút / huỷ chỉ **người tạo** (`created_by`, kể cả ADMIN cũng 403 — khuôn G8b); duyệt / từ chối cần `PERM_PRODUCTION_RECEIPT_APPROVE` (tự duyệt phiếu của mình được phép — FE Q10). Lý do rút / huỷ / từ chối: thiếu ⇒ 400 `APPROVAL_REASON_REQUIRED`, ngoài 5–2000 sau trim ⇒ 400 field `reason` (`ReasonRules`). Rút: `PENDING_APPROVAL → DRAFT`; huỷ: `DRAFT`/`PENDING_APPROVAL → CANCELLED` (lần đầu tiên có đường vào `CANCELLED` từ `F2`). Không biến động kho ở cả bốn | `.update_byAnotherUser_*`, `.submit_byAnotherUser_*`, `.cancel_byAnotherUser_*`, `.withdraw_*` (3), `.cancel_*` (2), `.reject_recordsWhoRejected` |
| B229 | **Nguồn khoá theo WO, ngày do server phán:** đích nhập phải là kho đầu ra của WO (khác ⇒ 422 `RESOURCE_SCOPE_MISMATCH` field `destinationWarehouseId`) và còn `ACTIVE` lúc duyệt. `receivedOn ≤ hôm nay của nhà máy`; `expiresOn` chỉ cho hàng theo lô, `> receivedOn` và `> hôm nay` — sai lúc tạo/sửa ⇒ 400 field; quá hạn lúc gửi/duyệt ⇒ 409 `LOT_NOT_ELIGIBLE` field `expiresOn`. Lô mới hết hạn **đầu ngày** `expiresOn` theo múi giờ nhà máy (FE Q23). Mã lô trùng (không phân biệt hoa thường, B205) ⇒ 409 `LOT_CODE_ALREADY_EXISTS` field `lotNumber`, kiểm lúc tạo/sửa/gửi/duyệt; nháp **không** giữ chỗ mã lô (D11 — DB `uk_inventory_lots_item_code` là bảo đảm thật, và nó phân biệt hoa thường) | `.post_toAnotherWarehouseOfThePlant_*`, `.post_receivedOnAfterThePlantsToday_*`, `.post_expiry*` (2), `.approve_passesTheExpiryAsTheStartOfThePlantDay`, `.approve_expiredSinceTheDraftWasSaved_*`, `.post_lotNumberAlreadyUsedInAnotherCase_*` |
| B230 | **Đọc (D6):** chi tiết / lịch sử / danh sách theo WO: một trong `PERM_PRODUCTION_RECEIPT_READ`, `_MANAGE`, `_APPROVE`, `PERM_PRODUCTION_QC_READ`, `PERM_QUALITY_DISPOSITION`; danh sách theo nhà máy: cùng năm quyền trên nhà máy; ứng viên: ba quyền phiếu nhập. Chỉ có quyền QC ⇒ chỉ phiếu `APPROVED` (chi tiết khác ⇒ 403, danh sách trạng thái khác ⇒ trang rỗng). `allowedActions` tính theo trạng thái + người tạo + quyền của người gọi trên nhà máy — tiện cho nút, mọi lệnh vẫn tự kiểm. Lịch sử đọc từ cột của chính phiếu (D9): chỉ lần gửi/rút **gần nhất**; đủ vết ở `audit_logs` | `ManufacturingExecutionMethodSecurityTest` (G9a, 6), `.get_withQualityPermissionsOnly_refusesADraft`, `.listByPlant_withQualityPermissionsOnly_*`, `.get_anotherUsersDraft_offersNoAction`, `.events_*`, `ProductionReceiptRepositoryIT.findByPlant_*` (3 mới), `FlywayMigrationIT.migrate_v83_*` |

## G9b — Phiếu nhập: phân bổ theo lượt báo sản lượng + truy xuất nguồn (2026-10-23)

Nguồn: `FE_HANDOFF_GD_09_2026-10-15.md` R05, R13, R22, Q6/Q27/Q29/Q30. Migration `V84` (bảng `production_receipt_contributions`: một dòng cho mỗi cặp phiếu – lượt báo, `UNIQUE (receipt_id, production_execution_id)`, CHECK lượng > 0; **không** backfill). Không quyền mới, không mã lỗi mới.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B231 | **Server phân bổ, client không khai nguồn (D3, Q6).** Lúc tạo phiếu và mỗi lần sửa nháp, `ReceiptContributionLedger.allocate` lấy lượng từ các lượt báo **good cuối** — chỉ lượt của công đoạn cuối (B219); lệnh không có công đoạn hoặc `operation_tracking = false` thì mọi lượt — **lượt cũ nhất trước** (`actualEndedAt`, rồi id). Mỗi lượt chỉ được giữ tới good của chính nó, tính mọi phiếu còn giữ (`DRAFT`/`PENDING_APPROVAL`/`APPROVED`). Phiếu viết trước `V84` không có dòng: lượng của nó được **tính trừ vào các lượt cũ nhất trước** (đúng thứ tự `sourceWipTraceIds` luôn giả định), nên phiếu mới không bao giờ lấy trùng. Phiếu cũ trả `outputEvidenceKnown = false` + `outputUnknownReason` + `contributions = null` — **không** dựng nguồn giả (R22, Q30). Sửa một nháp cũ thì nó được phân bổ và có dòng từ đó | `ProductionReceiptServiceTest.post_allocatesTheReceiptToTheOldestReportsFirst`, `.post_skipsWhatOtherReceiptsAlreadyClaimOfAReport`, `.post_chargesUnrecordedLegacyReceiptsToTheOldestReports`, `.post_drawsOnlyOnTheLastOperationsReports`, `.post_reportsThatNoLongerCoverTheReceipt_*`, `.update_reallocatesTheDraftFromScratch`, `.get_ofALegacyReceipt_*`, `ProductionReceiptRepositoryIT` (G9b, 4), `ProductionFlowE2EIT` |
| B232 | **Nguồn không bao giờ cũ đi ⇒ không kiểm lại lúc gửi/duyệt, không mã `PRODUCTION_RECEIPT_SOURCE_CHANGED`** (lệch kế hoạch §16 có chủ đích): lượt báo không có đường sửa/đảo (F10 #6); mọi lệnh phiếu nhập `touch()` WO (B227) nên hai lần phân bổ đồng thời không cùng qua được `@Version`; phần tính cho phiếu cũ chỉ có thể **giảm** (huỷ/từ chối). Gửi/rút/duyệt giữ nguyên dòng; từ chối/huỷ giữ dòng làm lịch sử nhưng không còn tính. Sửa nháp: xoá dòng cũ (bulk JPQL, chạy ngay) rồi ghi dòng mới; response dùng đúng danh sách vừa ghi, không đọc lại | `.update_reallocatesTheDraftFromScratch` (`InOrder` delete → saveAll), `ProductionReceiptRepositoryIT.deleteByReceiptId_*`, `.sumLiveByExecution_countsLiveReceiptsAndCanExcludeOne` |
| B233 | **Truy xuất nguồn hai tầng (R13, Q27):** `GET …/production-receipts/{R}/source` trả các lượt báo của phiếu và vật tư của **cả lệnh** (`materialAttribution = WORK_ORDER_LEVEL`, cùng con số với phần vật tư của màn hình dở dang — `MaterialSourceLedger`); lệnh không theo dõi công đoạn ⇒ `UNKNOWN`. Không gán vật tư cho từng đơn vị thành phẩm. Cùng quyền đọc với chi tiết phiếu (B230; chỉ quyền QC ⇒ chỉ phiếu `APPROVED`) | `.source_ofATrackedReceipt_*`, `.source_ofALegacyReceipt_isUnknownNotEmpty`, `.source_withQualityPermissionsOnly_refusesADraft`, `ManufacturingExecutionMethodSecurityTest.productionReceiptSource_*`, `ManufacturingExecutionControllerTest.getProductionReceiptSource_*` |

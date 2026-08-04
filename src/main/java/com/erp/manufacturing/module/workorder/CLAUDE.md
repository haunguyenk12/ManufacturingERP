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
| B18 | **[P1-1c]** Lot **mới tạo** từ approve mở ở `HOLD`; lot **đã tồn tại** giữ nguyên status | `InventoryMovementServiceTest.receive_*Hold*` |
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
| B39 | **[D5 – viết lại]** Output **có lot**: QC `AVAILABLE`/`REJECTED` **không đổi** `stock_balances.quantity` — chỉ đổi `lot.status`. Available tăng gián tiếp vì aggregate query lọc theo lot status (xem `module/inventory/CLAUDE.md` B3). Output **không lot**: `AVAILABLE` cũng không đổi balance (hàng đã ở tồn tự do từ lúc `approve`), nhưng `REJECTED` **phải** rút hàng ra bằng `ADJUST_OUT` (delta âm qua `InventoryMovementService.adjust`) — không có lot status nào để làm hàng hỏng thành không dùng được, nên nếu không rút thì hàng hỏng vẫn xuất/bán được | `ProductionReceiptServiceTest.qcDisposition_available_*`, `.qcDisposition_rejected_*`, `.qcDisposition_rejected_onOutputWithoutALot_withdrawsTheStockAndNeverFulfils`, `ProductionFlowE2EIT.qcRejection_onOutputThatIsNotLotTracked_withdrawsTheGoodsFromStock` |
| B40 | **[D5 – tách đôi]** QC áp dụng cho **mọi** output; điều kiện rẽ nhánh là **cấp dòng** (`line.lot != null`), **không** phải cờ `item.lotTracked`. **Có lot** ⇒ mọi lot phải đang ở `HOLD`, validate **toàn bộ** lot trước khi đổi lot đầu tiên ⇒ không tồn tại receipt bị QC dở dang (nguyên văn cũ, **giữ nguyên**). **Không lot** ⇒ phán quyết ghi trên **receipt** (`qc_result`/`qc_reason`/`qc_at`/`qc_by`), **không** sinh `LOT_STATUS_CHANGE`, **không** ghi dòng `quality_dispositions` | `ProductionReceiptServiceTest.qcDisposition_lotNoLongerOnHold_shouldThrowLotNotEligibleBeforeAnyChange`, `.qcDisposition_available_onOutputWithoutALot_fulfilsWithoutTouchingLotsOrStock`, `.qcDisposition_onOutputWithoutALot_*` (3 case B38) |
| B41 | Receipt của item lot-tracked **bắt buộc** có `lotCode`/`lotId` ngay ở bước `post` (`LOT_REQUIRED`, 400) — không có lot thì output không bao giờ ra khỏi `HOLD` được | `ProductionReceiptServiceTest.post_lotTrackedOutputWithoutLot_shouldThrowLotRequired` |

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

## Bất Biến Fulfillment Allocation (F6)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B62 | Fulfillment chạy **chỉ** khi QC disposition = `AVAILABLE` commit thành công (spec §7.1). `approve` **không** fulfill (lot còn `HOLD`, không giao được), nhánh QC `REJECTED` **không** fulfill. Số lượng đưa sang là **quantity vừa được QC phán quyết**, không phải `completedQuantity` hay `actualGoodQuantity`. **[D5]** Hiệu lực **không đổi** — `D5` chỉ mở rộng tập receipt đủ điều kiện QC, **không** thêm trigger thứ hai. Với output không lot, số đưa sang là **tổng `quantity` của mọi dòng receipt** (nguồn cũ cộng dồn từ lot line nên sẽ ra 0 ⇒ `fulfill` no-op **im lặng**; đây là cái bẫy chính của `D5`) | `ProductionReceiptServiceTest.qcDisposition_available_fulfilsTheAllocatedSalesOrderQuantity`, `.qcDisposition_rejected_neverReachesFulfilment`, `.approve_shouldCreateReceiveMovementWithHoldLot_withoutCompletingWorkOrder`, `.qcDisposition_available_onOutputWithoutALot_fulfilsWithoutTouchingLotsOrStock` |
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

# module/planning — Business Rule Invariants

> Tách từ `CLAUDE.md` §10.4 (2026-07-25). Chỉ nạp khi agent làm việc trong `module/planning/**` (MRP).

> Đây là các **bất biến** (invariant) của hệ thống. Vi phạm = bug nghiệp vụ, không phải "lựa chọn thiết kế".
> Mỗi bất biến trong bảng dưới **phải có ít nhất 1 test** bảo vệ.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B21 | Net requirement = gross − available − open supply + safety stock. **Reserved không tính là available** | `MrpCalculationServiceTest.calculate_reservedStockIsNotAvailable_*` |
| B22 | Open Work Order supply **làm giảm** net requirement | `MrpCalculationServiceTest.calculate_openWorkOrderSupplyReducesNetRequirement` |
| B23 | `suggestedOrderDate = dueDate − leadTimeDays` | `MrpCalculationServiceTest.calculate_safetyStockAndLeadTimeDrivePurchaseSuggestion` |
| B24 | Item có BOM `ACTIVE` → suggestion `WORK_ORDER` + nổ tiếp component; không có BOM → `PURCHASE_REQUISITION` | `MrpCalculationServiceTest` |
| B25 | Component lặp lại ở nhiều nhánh BOM phải được **cộng dồn**, không nhân đôi dòng | `PlanningServiceTest.estimateProduction_multiLevelBom_aggregatesRepeatedLeafComponent` |
| B26 | Suggestion chỉ chuyển đổi được khi `APPROVED`; đã `CONVERTED` không chuyển lần 2 | `SupplySuggestionServiceTest`, `PurchaseRequisitionServiceTest` |

## Bất Biến Bổ Sung (`F5-B`)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B58 | Suggestion `BLOCKED` **không bao giờ** thành work order. Lỗi trả về đúng message code đã chặn nó (`MISSING_BOM` / `MISSING_ROUTING`, 409), không phải một code chung chung | `SupplySuggestionServiceTest.convertToWorkOrder_blockedSuggestion_failsWithTheMessageCodeThatBlockedIt`, `.convertToWorkOrder_blockedByMissingBom_reportsMissingBomNotMissingRouting` |
| B59 | Item **manufacturable** thiếu BOM/Routing `ACTIVE` vẫn sinh proposal MAKE, ở trạng thái `BLOCKED` + message code. Không được **im lặng bỏ qua** — planner phải nhìn thấy dòng để biết master data nào cần sửa (spec §2.1). `MATERIAL_SHORTAGE` chỉ mô tả shortage của item mà proposal đang bù và **không tự làm BLOCKED**; một FG level 0 thiếu vẫn có code này dù component level 1 đã `COVERED` | `MrpCalculationServiceTest.calculate_manufacturableItemWithoutActiveBom_stillEmitsABlockedMakeProposal`, `.calculate_makeItemWithoutActiveRouting_blocksTheProposalWithMissingRouting`, `.calculate_componentCovered_materialShortageOnlyDescribesTheFinishedGoodAndDoesNotBlockMake` |
| B60 | `demandLineIds` được gửi ⇒ **chỉ** những demand đó vào run; demand khác company/plant/warehouse của run ⇒ `RESOURCE_NOT_FOUND` (404), demand không `OPEN` ⇒ `STATE_CONFLICT` (409). Cả hai fail **trước khi** `MrpRun` được ghi | `MrpRunServiceTest.run_withDemandLineIds_plansOnlyTheSelectedDemandsAndSkipsTheHorizonSweep`, `.run_demandLineIdOfAnotherPlant_isRejectedAsNotFoundBeforeTheRunIsCreated`, `.run_cancelledDemandLineId_isRejectedWithStateConflict` |
| B61 | `settingSource = SYSTEM_DEFAULT` ⇔ item-warehouse setting `ACTIVE` **không tồn tại** ⇒ safety stock / reorder point / lead time = 0. Không được bịa giá trị mặc định khác | `InventoryAvailabilityServiceTest.getPlanningQuantities_noActiveSetting_reportsFallbackAndCountsExcludedLots`, `MrpCalculationServiceTest.calculate_noItemWarehouseSetting_recordsSystemDefaultSourceAndWarns` |

## Bất Biến Bổ Sung (`D4`)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B67 | `openSupplyQuantity` (= `scheduledReceipts` của spec §2.1) là **tổng hai nguồn**: work order `RELEASED`/`IN_PROGRESS` **+** purchase order `SENT`/`PARTIALLY_RECEIVED`. PO `DRAFT` **không** tính (chưa gửi nhà cung cấp thì chưa phải hàng sẽ về); dòng PO đã nhận đủ **không** tính (hàng đã nằm trong on-hand ⇒ đếm hai lần). Cả hai nguồn đọc **1 lần / cấp BOM**, không theo từng seed (`C14`) | `MrpCalculationServiceTest.calculate_openPurchaseOrderSupplyIsNettedTogetherWithOpenWorkOrderSupply` (công thức bằng số thật), `.calculate_openPurchaseOrderCoversDemand_emitsNoPurchaseProposal` (bằng chứng nợ #16 đã trả), `.calculate_readsEachSupplyServiceOncePerBomLevel_notOncePerSeed`, `PurchaseOrderRepositoryIT` (5 case, JPQL thật) |
| B68 | `MrpRun.code` derive từ chính `mrpRunId` (`"RUN-" + 8 hex đầu`, chữ hoa) và **phải** cùng công thức với backfill `V36`. Sinh ở `@PrePersist` — **không** sau `save()`: Hibernate chụp snapshot entity lúc queue insert nên field gán sau đó không vào INSERT, và cột là NOT NULL | `FlywayMigrationIT.migrate_v36_backfillsExistingRunsWithTheSameCodeFormulaAsTheEntity`, `ProductionFlowE2EIT` (insert thật qua HTTP) |

## Bất Biến Bổ Sung (`D11`)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B74 | **Đọc status chứng từ ⇒ `STATE_CONFLICT` (409); master data / input sai ⇒ `OPERATION_NOT_ALLOWED` (422).** Ba chỗ đọc status của module này trả 409: `PlanningDemandService.cancel` (`!demand.isOpen()`), `SupplySuggestionService.convertToWorkOrder` (`!suggestion.isApproved()`), `SupplySuggestionService.ensureDraft` (`approve`/`reject`). Lý do mạnh nhất **không** phải là tài liệu mà là tính nhất quán nội tại: `PurchaseRequisitionService` đã đổi nhánh "suggestion không `APPROVED`" sang 409 ở `D7`, nên trước `D11` **cùng một điều kiện nghiệp vụ trả 2 status khác nhau tuỳ đường convert nào** (nợ #26). 🔴 **Các chỗ 422 còn lại của module là CỐ Ý, đừng "đồng bộ" nốt:** `ensurePlantBelongsToCompany` / `ensureItemBelongsToCompany` / `ensureWarehouseBelongsToPlant`, `PlanningService` (item khác company scope), và **`convertToWorkOrder` nhánh sai `SupplySuggestionType`** — chỗ cuối nằm ngay dưới chỗ vừa đổi thành 409, dễ nhầm nhất | `PlanningDemandServiceTest.cancel_nonOpenDemand_fails`, `SupplySuggestionServiceTest.reject_convertedSuggestion_fails` + `.convertToWorkOrder_suggestionNotApproved_failsWithStateConflict` + `.convertToWorkOrder_purchaseSuggestion_fails` (**giữ 422**), `PlanningDemandControllerTest.cancel_nonOpenDemand_returns409StateConflict`, `PlanningControllerTest.estimateProduction_productOutsideScopeCompany_returns422` (**giữ 422**) |

## Bất Biến Bổ Sung (`F10`)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B77 | **`projectedAvailableQuantity` là ĐÚNG con số netting đã dùng, kể cả phần bị kẹp ≥ 0** — `max(0, available + openSupply − coverage đã bị các line TRƯỚC ăn)`. 🔴 **Không derive được từ các cột khác:** số bị trừ là `consumedCoverageByItem`, chỉ sống trong **một** lần chạy `MrpCalculationService` và chưa bao giờ persist ⇒ mapper cộng `available + openSupply` sẽ báo số **lớn hơn thực tế** ngay khi một item xuất hiện trên **nhiều** requirement line, và phá luôn đẳng thức FE dựa vào: `net = max(0, gross + safetyStock − projected)`. Công thức spec §2.1 không kẹp, nhưng bản kẹp mới là thứ netting thật sự dùng — hiển thị số chưa kẹp thì FE không tái lập được `net`. Cột **nullable, KHÔNG backfill**: `NULL` = "run chạy trước `V40`", **không** phải "không còn gì"; backfill bằng `available + openSupply` chính là ghi vĩnh viễn con số sai mà cột này sinh ra để thay thế | `MrpCalculationServiceTest.calculate_sameItemOnTwoRequirementLines_reportsTheCoverageLeftForTheSecondLineNotTheGrossAvailable` (2 line, số nghiệp vụ thật), `.calculate_projectedAvailableIsTheFigureTheNettingUsed` (đẳng thức), `MrpRunServiceTest.run_snapshotsDemandAndPersistsCalculationOutput` (tới được entity), `PlanningRunControllerTest.listRequirements_exposesTheProjectedAvailableUsedByTheNetting`, `FlywayMigrationIT.migrate_v40_*` (cột nullable) |
| B78 | `supply_suggestions.source_routing_code` / `_version` là **snapshot đóng băng** routing `ACTIVE` **lúc chạy run**, cùng hợp đồng với `work_orders.source_routing_code` (`B49`) — không phải lookup sống, routing sửa sau đó **không** viết lại lịch sử. `NULL` **là thông tin**: proposal `BUY` không có routing nào, và proposal `MAKE` không có routing `ACTIVE` chính là ca `BLOCKED` + `MISSING_ROUTING` (`B58`/`B59`). 🔴 Đọc qua **`RoutingLookupService.findActiveRoutingSummaries`** — 1 query/cấp BOM (`C14`); `keySet()` của nó trả lời luôn câu hỏi "có routing `ACTIVE` không?" nên **đừng** thêm lại một query id-set thứ hai bên cạnh | `MrpCalculationServiceTest.calculate_makeProposal_freezesTheActiveRoutingCodeAndVersion_buyProposalCarriesNone`, `.calculate_makeItemWithoutActiveRouting_blocksTheProposalWithMissingRouting` (null), `MrpRunServiceTest.run_snapshotsDemandAndPersistsCalculationOutput`, `PlanningRunControllerTest.listSuggestions_exposesTheFrozenRoutingSnapshot` |

---

## Bất Biến Idempotency Của Planning Run (2026-08-14)

Nguồn: `live-data-audit.md` (FE) — gửi **một** `Idempotency-Key` ba lần với payload y hệt và nhận về
**ba** run (`RUN-63033D7C`, `RUN-EA182AFB`, `RUN-4C77B225`). Migration **`V58`**. Bản ghi:
`CLAUDE.md §0.44`.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B117 | `POST /planning-runs` nhận `Idempotency-Key` **tuỳ chọn**. Có key + đã tồn tại run mang key đó ⇒ trả **đúng run cũ**, **không** chạy calculate lần hai (assertion mạnh nhất là `verifyNoInteractions(calculationService)`, không phải so id). Có key + payload khác ⇒ `409 IDEMPOTENCY_CONFLICT` **trước** mọi lần ghi. Không có key ⇒ hành vi **y hệt trước V58**. Scope replay (`findByIdempotencyKey`, toàn bảng) phải khớp `uk_mrp_runs_idempotency_key` — cùng bài học `B69` của `stock_movements`, chỉ khác là ở đây **một key = một lần chạy** nên constraint không kèm cột thứ hai | `MrpRunServiceTest.run_replayedKeyWithSamePayload_returnsTheExistingRunAndDoesNotRecalculate`, `.run_replayedKeyWithDifferentPayload_throwsIdempotencyConflictBeforeAnyWrite`, `.run_freshKey_isPersistedOnTheRunWithItsPayloadHash`, `.run_withoutAnIdempotencyKey_neverConsultsTheReplayLookup`, `PlanningRunControllerTest.run_forwardsTheIdempotencyKeyHeader`, `FlywayMigrationIT.migrate_v58_rejectsADuplicateRunKeyButStillAllowsManyRunsWithoutOne` |

**Quyết định cần nhớ:**

1. 🔴 **Run `FAILED` VẪN chiếm key.** Row run được ghi **trước** khi calculate (để có id cho requirement/
   suggestion), và `run()` **nuốt** exception rồi đánh dấu `FAILED` thay vì ném ra ⇒ key đã thuộc về
   run đó vĩnh viễn. Muốn chạy lại sau thất bại: **dùng key mới**. Đây là hệ quả của thiết kế sẵn có,
   không phải thiếu sót — đã ghi vào javadoc `run()` và tài liệu FE.
2. **`saveAndFlush` cho row run đầu tiên, không phải `save`.** Key phải được "chiếm" ở DB **ngay**;
   nếu để flush hoãn tới commit thì một request trùng key chạy song song sẽ tính hết cả run rồi mới
   đụng constraint — đúng thứ tính năng này sinh ra để tránh (bài học save-vs-flush của `§0.39`).
3. **Hai request song song cùng key ⇒ request thứ hai `409 RESOURCE_ALREADY_EXISTS`** (từ
   `DataIntegrityViolationException`), **không** phải `IDEMPOTENCY_CONFLICT` và **không** chờ run thứ
   nhất xong. Chấp nhận có chủ đích: nó fail nhanh, và đây là ca hiếm (double-click), không phải
   đường đi chính.
4. 🔴 **Ba run trùng của FE còn kéo theo một "lỗi" thứ hai mà backend KHÔNG có:** mỗi run sinh một bộ
   suggestion song song cho cùng demand, nên FE convert suggestion của run này rồi đọc suggestion của
   run kia và tưởng `convertToWorkOrder` không atomic. Đã kiểm chứng bằng dữ liệu thật:
   `work_orders.planning_proposal_id` trỏ đúng suggestion `CONVERTED`, còn suggestion họ nhắc tới
   thuộc run khác và chưa từng convert (lần thử của nó chết vì trùng `workOrderNo` rồi rollback sạch).
   `convertToWorkOrder` **không đổi một dòng nào** trong lần sửa này.

## Quyết định thiết kế cần nhớ (`F5-B`)

### 1. Đường dẫn endpoint

| Cũ | Mới |
|---|---|
| `/api/v1/mrp/runs**` | `/api/v1/planning-runs**` |
| `/api/v1/mrp/suggestions/{id}/**` | `/api/v1/supply-suggestions/{id}/**` |

Spec §2.2 đề xuất lồng proposal dưới run (`/planning-runs/{id}/proposals/{proposalId}/work-order`).
Repo chọn **phẳng** vì `runId` không cần để định danh một suggestion, và `purchasing` treo
`convert-to-purchase-requisition` lên cùng resource đó. Tên endpoint là phần **đề xuất** của spec;
field name + business rule mới là phần cố định (`CLAUDE.md §0.1`).
Class `MrpController` đổi tên thành `PlanningRunController`.

### 2. `messages[]` lưu chuỗi nối bằng dấu phẩy, không phải bảng con

`supply_suggestions.message_codes VARCHAR(200)`. Mỗi suggestion có tối đa 4 code, luôn đọc cả cụm,
không bao giờ query theo từng code ⇒ bảng con `supply_suggestion_messages` chỉ thêm 1 join mà không
mua được gì. `SupplySuggestion.messages()` tách chuỗi; `MrpRunService` nối lại khi ghi.

### 3. `PURCHASING_DEFERRED` **cố ý không** được implement

Spec §8.1 có code này vì purchasing nằm ngoài MVP của **frontend**. Backend này có module
`purchasing` đầy đủ và proposal BUY convert được thành purchase requisition ⇒ gắn nhãn "deferred"
lên nó là mô tả sai hệ thống.

### 4. `demandLineIds` rỗng ⇒ giữ nguyên hành vi quét horizon

Spec §2.3 đánh dấu field là bắt buộc. Repo cho phép **vắng mặt** để không phá client hiện có
(`MrpRunService.resolveDemands`). Khi có danh sách, horizon **không** còn lọc demand nữa — planner đã
lọc theo due date ở màn hình chọn demand, lọc lại lần nữa sẽ âm thầm bỏ dòng họ cố ý chọn.

### 5. `PLANNED` work order vẫn **chưa** được tính là open supply

`WorkOrderRepository.aggregateOpenSupply` chỉ tính `RELEASED` + `IN_PROGRESS` (kế thừa từ `F5-A`).
`F5-B` **không** đổi — đó là quyết định nghiệp vụ riêng, xem `NEXT_PHASE_PLAN.md` mục Ràng Buộc.
`D4` cũng **không** đổi: nó chỉ thêm nguồn purchase order, không rà lại status của work order.

---

## Quyết định thiết kế cần nhớ (`D4`)

### 6. `openSupplyQuantity` là **một** số, không tách 2 cột

Spec §2.1 chỉ định nghĩa một số hạng `scheduledReceipts` và FE hiển thị một số. Tách thành
`openWorkOrderQuantity` / `openPurchaseQuantity` = 2 cột migration + đổi DTO (breaking positional)
cho thông tin **chưa ai yêu cầu** (`coding-rules.md §11.5`). Nếu planner cần biết đang chờ ai thì mở
nợ riêng khi có yêu cầu thật.

### 7. Hướng phụ thuộc mới `planning → purchasing`

Qua `PurchaseOrderSupplyService` (`module/purchasing/service/query/`) — query service, **không** chạm
`PurchaseOrderRepository` (`C7`). Cùng khuôn với `planning → workorder` (`WorkOrderSupplyService`) và
`planning → routing` (`RoutingLookupService`, thêm ở `F5-B`).

### 8. 4 ô summary của Run header là **cache**, không phải nguồn sự thật

`MrpRunService` đếm tại chỗ từ `MrpCalculationResult` đang có trong tay — không query lại. Hệ quả:
run tạo **trước** `V36` mang giá trị `0` ở cả 4 ô (migration không backfill chúng); số thật của run
lịch sử vẫn đọc được từ `mrp_requirement_lines` / `supply_suggestions`.

---

## Bất Biến Run Thương Mại + Sổ Chuyển Đổi (`G6b`, 2026-10-18)

Nguồn: `FE_HANDOFF_GD_06_2026-10-14.md`; quyết định D5–D10 + hai quyết định số liệu của user ở `NEXT_PHASE_PLAN.md §13`. Migration
**`V75`**. Trả lời FE: `BE_RESPONSE_GD_06_2026-10-17_v2.md`.

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B178 | **Stock target (`max(safetyStock, reorderPoint)`) cộng MỘT lần cho mỗi pool `(item, warehouse)`** — dòng đầu tiên của pool trong lượt chạy (qua mọi cấp BOM), các dòng sau `safetyStockQuantity = 0`. Áp cho **cả** run cũ (user chốt). Trước G6b mỗi dòng nhu cầu cùng pool mua lại cả tồn an toàn: A/B trên `D26-OMNIPLANT` 11 → 6 đề xuất, 8 → 3 đề xuất mua. Đẳng thức `B77` vẫn đúng **từng dòng** | `MrpCalculationServiceTest.calculate_twoDemandsInOnePool_addTheSafetyStockOnlyOnce` (mutation cộng mỗi dòng ⇒ đỏ) |
| B179 | **Gross linh kiện = `ceil6(net cha × quantityPer × (1 + scrapRate))`** trước khi net (Q28, áp cả run cũ). Net cha luôn scale 6 (mọi số hạng đều scale 6) nên đó cũng chính là "ceil net MAKE trước khi nổ". Hao hụt **`× (1 + rate)`**, không phải `÷ (1 − rate)` của mock FE — quy ước toàn repo | `.calculate_componentDemand_isRoundedUpToSixDecimalsBeforeItIsNettedAndExploded` (0.33999966 ⇒ 0.340000, RAW 34.000000) |
| B180 | **`POST /planning-runs/commercial`** lập kế hoạch `uncovered` của dòng đơn (B172), **không** `orderedQuantity` — tồn đơn đang giữ đã trừ ở khả dụng nên không đếm hai lần (A16). Mọi kiểm (kỳ ≤ 90 ngày cả hai đầu, `includeRop=true` ⇒ 422, mapping trùng/`outputWarehouseId ≠ demandWarehouseId` ⇒ 400, dòng ngoài plant ⇒ 404, đơn không hoạch định được / `uncovered = 0` / thiếu demand `OPEN` ⇒ 409) chạy **trước** khi row run tồn tại. `itemWarehouseMappings` ghi đè kho cho **mọi cấp** trong run (`RUN_ITEM_MAPPING`), không sửa master. Demand snapshot (`mrp_run_demands.required_quantity`) = lượng đã lập kế hoạch | `CommercialPlanningRunServiceTest` (9), `MrpCalculationServiceTest.calculate_runItemMappingAndQuantityOverride_winForRootAndComponents` |
| B181 | **`sourceVersion`** = sha-256 của (mỗi dòng đơn nguồn: item, ordered, dueDate, đơn còn hoạch định được, held) + (mỗi item FG/WIP của run: id BOM `ACTIVE`, id routing `ACTIVE`). Tính lúc run xong; `computeCurrent` đọc lại **đúng các đầu vào đó** (SO line từ `mrp_run_demands`, item từ requirement lines). Cố ý **không** gồm tồn tự do/nguồn cung mở (đổi liên tục) và coverage WO/QC (chính chuyển đổi của run làm đổi) | `PlanningConversionRepositoryIT.sourceVersionInputs_*` (mutation nới bộ lọc type ⇒ đỏ), `CommercialPlanningRunServiceTest.get_commercialRunWhoseSourcesChanged_reportsStale_A26` |
| B182 | **Chuyển đổi** (`PlanningConversionService.convert`, một transaction, dưới `PESSIMISTIC_WRITE` dòng run): replay theo `(run, Idempotency-Key)` → run COMMERCIAL + COMPLETED (`STATE_CONFLICT`) → `expectedConversionVersion` (`CONCURRENT_MODIFICATION`) → `expectedSourceVersion` = run (`PLANNING_SOURCE_STALE`) → `computeCurrent` = run (`PLANNING_SOURCE_STALE`, Q23) → entry thuộc run, có đề xuất, không `REJECTED`, không `BLOCKED` (mã B58) → cùng một loại → quantity ≤ `suggested − Σ sổ` (`PLANNED_QUANTITY_EXCEEDED` + field). Đề xuất chuyển qua đường cũ (CONVERTED, sổ rỗng) còn lại **0**. Lần chuyển đầu `markConverted` ⇒ đường `approve`/`convert-to-*` cũ không chuyển trùng | `PlanningConversionServiceTest` (14 — mutation bỏ kiểm còn lại / bỏ kiểm nguồn hiện tại / đổi mã stale ⇒ đỏ) |
| B183 | **MAKE**: mỗi entry một WO `DRAFT` qua `WorkOrderService.createFromMrp` (snapshot BOM/routing, routing `ACTIVE` bắt buộc). Chỉ dòng **cấp 0** của demand `SALES_ORDER_LINE` gắn đơn, cap phần gắn = `uncovered` **đọc lại lúc chuyển** (`WorkOrderDemandAllocationService.allocate(…, cap)`). **BUY**: một PO `DRAFT` qua `PurchaseOrderService.createFromPlanning`, mọi entry cùng kho nhận, `supplierId` bắt buộc | `.convert_firstPartialMake_createsADraftWorkOrderCappedByTheUncoveredQuantity_A27` (mutation cap `null` ⇒ đỏ), `.convert_componentProposal_isNotAllocatedToTheSalesOrder_A28`, `.convert_buy_createsOneDraftPurchaseOrderForAllEntries_A31` |

**Quyết định cần nhớ:**

1. 🔴 **Không có bảng `mrp_requirement_sources` (lệch D7 của kế hoạch).** Engine không bao giờ gộp nhiều nguồn vào một dòng — mỗi seed
   một dòng, một cha, một demand gốc — nên bảng nối sẽ luôn đúng một hàng/dòng. `sourceKind`/`sourceSalesOrderLineIds`/
   `parentRequirementLineIds` suy từ cột có sẵn trong mapper. Pool `(item, warehouse)` vẫn net tồn dùng chung một lần (A18).
2. **`/conversions` chỉ nhận run thương mại** — run cũ không có `sourceVersion`, dùng đường cũ.
3. **Replay trả `documents[].status = null`** — sổ không lưu trạng thái hiện tại của chứng từ.
4. **Hướng phụ thuộc mới `planning → sales`** (`SalesOrderPlanningSourceService`) và `planning → purchasing` thêm `createFromPlanning`.
   Không vòng bean: `SalesOrderPlanningSourceService` chỉ phụ thuộc repository sales + hai service workorder chỉ đọc repository.
5. **`completedAt` của run cắt µs** (§0.51 #3); `createdAt` của sổ (auditing) thì chưa — giới hạn toàn hệ thống.

## G7a — Huỷ PO trả lại quota chuyển đổi (2026-10-18)

`supply_suggestion_conversions` có thêm `released_at`/`released_by`/`release_reason` (`V76`). Dòng **đã giải phóng** (PO đích bị huỷ) **không**
còn tính vào lượng đã chuyển / còn chuyển được, nhưng **vẫn** đếm là "ledger đã chạm đề xuất này": `MrpPlanningMapper.convertedQuantity(...,
hasLedgerRows)` chỉ coi đề xuất `CONVERTED` là chuyển hết khi **không có dòng ledger nào** (đường one-shot cũ). Thiếu vế đó thì huỷ PO không
trả lại được gì vì đề xuất vẫn `CONVERTED`. `sumBySuggestionIds` trả tổng **dòng giữ** + `rowCount` của **mọi** dòng — phần `case when`
chỉ Postgres chứng minh được (`PlanningConversionRepositoryIT.ledgerSum_*`). `PurchaseOrderPlanningLinkService` là đường duy nhất purchasing
chạm ledger (đọc nguồn + giải phóng), không `@PreAuthorize`, caller duy nhất `PurchaseOrderService`. Giải phóng tăng `conversionVersion` của
từng lượt liên quan (khoá theo thứ tự id). Bất biến chính ở `module/purchasing/CLAUDE.md` B190.

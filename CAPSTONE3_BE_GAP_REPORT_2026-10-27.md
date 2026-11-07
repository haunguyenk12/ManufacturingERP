# Đối chiếu bộ yêu cầu `CAPSTONE3_BE_FOLLOWUP_2026-10-20` với code BE hiện tại

**Ngày đối chiếu:** 2026-10-27 · **Nhánh/commit:** `feature/D8b-absolute-session-timeout` @ `542760c` (G11) · **Migration mới nhất:** `V92`
**Nguồn:** 9 tài liệu `FE_REQUEST_*` / `FE_QUESTION_*` (mỗi cái kèm một file `*_MESSAGE_*` 4 dòng, không có yêu cầu riêng) và mục lục `CAPSTONE3_BE_FOLLOWUP_PACKAGE_2026-10-20.md`.

> **Phương pháp.** Chỉ đọc code (grep/đọc file), **chưa chạy app, chưa chạy test, chưa gọi API**. Mỗi kết luận ghi `file:dòng` để kiểm lại. Chỗ nào chưa kiểm được thì ghi rõ "chưa kiểm". FE cũng chưa có bằng chứng live (BE đang tắt lúc họ viết) nên các bảng "đề xuất" của FE không phải bằng chứng lỗi runtime.

---

## 0. Tóm tắt

| # | Chủ đề | Loại / ưu tiên | Kết luận | Việc BE còn lại |
|---|---|---|---|---|
| 1 | Phản hồi nền GĐ6/GĐ7 | QUESTION / P1 | ✅ **Tài liệu đã có**, chưa có mapping version | Viết 1 file mapping + gửi lại; **không code** |
| 2 | Sổ giá trị tồn kho / WIP | REQUEST / P1 | ❌ **Chưa làm** (cố ý hoãn từ GĐ4) | Phase lớn, cần PO chốt 4 câu hỏi |
| 3 | Hết hạn xuyên MRP/reserve/issue | REQUEST / P1 | 🟡 **Làm một phần** — 4 chỗ còn hở | Sửa 5 điểm, **đổi số MRP** nên cần quyết định |
| 4 | Bảo vệ nguồn hàng mua đang QC | REQUEST / P1 | 🟡 **Gần đủ** — hở đúng `adjust` trên lô tracked | 1 guard + test |
| 5 | Capacity preview ↔ release | REQUEST / P1 | ❌ **Chưa làm**, đã tự ghi nhận là lệch có chủ đích | Đổi scheduler release + snapshot |
| 6 | Pending QC hiện trong MRP | REQUEST / P2 | ❌ **Chưa làm** | Field + projection mới, không đổi netting |
| 7 | Tách pending/rejected non-tracked | REQUEST / P2 | ❌ **Chưa làm** | 4 field additive trên aggregate |
| 8 | Decimal trên wire | QUESTION / P1 | ✅ Hành vi đã rõ, **thiếu tài liệu** | Trả lời 4 Q; **không code** |
| 9 | BOM theo lô (1/3) | QUESTION / P2 | ✅ Hành vi đúng như FE mô tả, **cần PO chốt** | Trả lời 3 Q; **không code** |

**Đề xuất thứ tự làm:** #4 (nhỏ, an toàn) → #3 (sửa hẹp, nhưng đổi số) → #6 + #7 (additive, chỉ đọc) → #5 (đổi lịch release) → trả lời #1/#8/#9 bằng tài liệu → #2 (phase riêng, sau khi PO chốt).

---

## 1. `FE_QUESTION_GD06_GD07_CONTRACT` — thiếu phản hồi nền GĐ6/GĐ7

**Trạng thái: ✅ nội dung đã có, ❌ chưa có tài liệu mapping.** Không có việc code.

FE nói họ chỉ nhận hai bản `v3` và thiếu 4 bản nền. Cả 4 đều **có trong repo gốc**:

| FE cần | File trong repo |
|---|---|
| GĐ6 v1 (SO) | `BE_RESPONSE_GD_06_2026-10-17.md` |
| GĐ6 v2 (MRP/chuyển đổi) | `BE_RESPONSE_GD_06_2026-10-17_v2.md` |
| GĐ7 v1 (PO) | `BE_RESPONSE_GD_07_2026-10-18.md` |
| GĐ7 v2 (phiếu nhận) | `BE_RESPONSE_GD_07_2026-10-18_v2.md` |

Gói `BE_RESPONSE_G7-11.zip` ở thư mục gốc có thể chính là bộ chưa gửi. **Chưa mở zip để xác nhận**.

**Còn thiếu (D02, D03):**

- **D02** — một file liệt kê *bản nào thay bản nào*: tên file ↔ nội dung thật ↔ migration (`V74`–`V78` cho GĐ6/7). Lưu ý FE đã nhận ra hai chỗ tên file lệch ngày nội dung (GĐ6 v3 tên 17/10 nhưng nội dung 18/10; GĐ7 v3 tên 18/10 nội dung 19/10). `CLAUDE.md §0.54–§0.59` đã có đủ dữ liệu để dựng bảng này.
- **D03** — "smoke nhận diện bản mới". `application.yml` có cấu hình `info:` (dòng 98) và exposure actuator (dòng 93) nhưng **chưa kiểm** `/actuator/info` có trả build/version/migration hay không. Phương án rẻ nhất là chỉ ra `flyway_schema_history` max version = `92` làm dấu nhận diện.

**Việc làm:** viết `BE_RESPONSE_VERSION_MAP_<ngày>.md` (1 trang). Tiện thể: GĐ8–GĐ11 cũng đã có phản hồi (`BE_RESPONSE_GD_08…11`), nên bảng nên kéo dài tới GĐ11 để FE không hỏi lần nữa.

---

## 2. `FE_REQUEST_INVENTORY_VALUATION` — sổ giá trị tồn kho & WIP

**Trạng thái: ❌ chưa làm.** Đây là việc lớn nhất trong bộ.

### 2.1 Hiện trạng (đã kiểm)

- `grep stockValue|averageCost|unitCost|valueBefore|valueAfter|VALUATION_UNAVAILABLE|movementValue` trong `src/main` **chỉ trúng module `workorder`** (giá thành G11, `ProductionCostAdjustment`, `ProductionCostBaselineMaterial`, `WorkOrderCostAccumulatorService`…). **Module `inventory` không có một trường tiền nào.**
- `StockMovement`, `StockBalance`, `StockReceiveRequest`, `StockTransfer*`, nhận mua (`GoodsReceipt*`) đều không mang giá.
- G11 (`CLAUDE.md §0.71`, D1/D3) **cố ý** nói rõ: "kho vẫn không có giá trị"; giá vật tư G11 là giá chuẩn `ItemStandardCost` chụp lúc phát hành, "giá trị còn kho" chỉ là bút toán phân bổ ghi nhận, **không phải số dư kế toán**. `P7` (WIP valuation) trong `MANUFACTURING_PHASE_PLAN_P7_P11.md` vẫn chưa làm (đã đánh số lại `V94`).
- Quyết định hoãn định giá đã lặp **năm lần** (`D7` ở G10b/c/d, G9c…). Yêu cầu này là lần đầu FE đòi tường minh.

### 2.2 Từng đầu ra FE đòi

| FE | Có chưa | Ghi chú |
|---|---|---|
| R01 aggregate trả `stockValue`/`averageCost`/`valueState` | ❌ | Cần cột/projection mới |
| R02 receive có `unitCost`, cùng commit | ❌ | `unitCost` **bắt buộc** cho receive mới ⇒ **breaking** với mọi caller (seed demo, `GoodsReceiptPoster`, `ProductionReceiptService`, nhập Excel) |
| R03 issue theo bình quân Item+Kho tại commit, snapshot trên movement | ❌ | Cần `valueBefore/After`, `recordedUnitCost` trên `stock_movements` |
| R04 transfer bảo toàn tổng value, residual | ❌ | Fixture FE: `10333.333333` / `82666.666667` |
| R05 count/adjust: tăng không nguồn giá ⇒ chặn | ❌ | Mã mới `VALUATION_UNAVAILABLE` (409) |
| R06 QC không đổi tổng value | ❌ (trivially đúng khi chưa có value) | Phải có test khi làm |
| R07 nguồn issue/consume/return nối WO, residual cuối | 🟡 | Sổ vật tư theo dòng cấp có rồi (`MaterialSourceLedger`, G8d) nhưng **chưa có giá** |
| R08 legacy `UNKNOWN`, quyền đọc tiền riêng | ❌ | Cần quyền mới |

### 2.3 Việc cần làm nếu PO duyệt

1. **Migration** (số tiếp theo `V93`; kế hoạch P7–P11 đã đánh số lại từ `V93`/`V94`, **phải đánh số lại lần nữa**): `stock_balances.stock_value NUMERIC(19,6) NULL` + `value_known BOOLEAN`; `stock_movements.unit_cost`, `movement_value`, `value_before`, `value_after`, `before_on_hand`, `after_on_hand`. **Không backfill** (legacy = `UNKNOWN`, đúng nguyên tắc `V67`/`V65`).
2. **Quyền mới** để đọc tiền (FE Q2). `PERM_COSTING_READ` (ADMIN+MANAGER) là ứng viên tái dùng, nhưng nó gắn với `ItemStandardCost`; cần quyết định tái dùng hay tách `PERM_INVENTORY_VALUE_READ`. **Phải ghi `docs/roles-and-permissions.md` + seed migration** (rule C10).
3. **Công thức** (FE Q3): VND, value scale 6 `HALF_UP`, bình quân nội bộ ≥ 12 chữ số. Phần dư dồn vào lần cuối (cùng mẫu `lastReturn_takesTheResidual` của G11).
4. **Điểm chạm code:** `InventoryMovementService` (receive/issue/adjust/count/transfer), `GoodsReceiptPoster` (đưa giá PO vào), `ProductionReceiptService` (giá nhập thành phẩm lấy từ đâu? — **chưa có câu trả lời**, giá thành G11 là nguồn tự nhiên nhưng chỉ chốt lúc đóng lệnh), `MaterialIssueService`, `StockTransferService`, `MaterialReturnService`, `PurchaseQualityService`, aggregate DTO, tất cả `Idempotency` fingerprint (thêm `unitCost` vào payload hash như bài học `§0.52 #7` về `expiresAt`).

### 2.4 Rủi ro / quyết định PO bắt buộc

- **Breaking:** `unitCost` bắt buộc ở receive. Nên chuyển bằng "tuỳ chọn trước, bắt buộc sau" hoặc cờ cấu hình — FE không đòi cách nào, cần chốt.
- **Chưa có nguồn giá cho thành phẩm vào kho** (production receipt). Nếu không chốt, tồn thành phẩm sẽ mãi `UNKNOWN`.
- **Trùng với `P7`** (`MANUFACTURING_PHASE_PLAN_P7_P11.md`) — nên gộp, đừng làm hai lần.
- Q1–Q4 của FE (§5) **chưa ai trả lời**: đã có valuation chưa (không), quyền đọc tiền, scale/rounding, count tăng không giá có bị chặn không.

**Đề xuất:** coi là một phase riêng (đặt tên `G12` hoặc gộp `P7`), **không** làm lẻ trong lượt này.

---

## 3. `FE_REQUEST_EXPIRED_STOCK_GUARDS` — chặn hàng hết hạn xuyên MRP / giữ chỗ / xuất vật tư

**Trạng thái: 🟡 một phần.** Quy ước biên khớp sẵn: hạn `<= now` là không đủ điều kiện (`!isAfter(now)` ở Java, `expiresAt > :now` ở JPQL).

### 3.1 Đã có (đừng làm lại)

| Chỗ | Bằng chứng | Phase |
|---|---|---|
| Xuất kho thủ công `POST /inventory/issue` từ chối lô hết hạn | `InventoryMovementService.issueManual` + `ensureNotExpired` (`:140`, `:624`) | G4 |
| Kiểm đếm tracked từ chối lô hết hạn | `resolveCountLot` → `ensureNotExpired` (`:616`) | G4 |
| Chuyển kho từ chối | `StockTransferService:226,231` | G4 |
| Giữ tồn cho **đơn bán** bỏ lô hết hạn | `findReservableBalancesFefo` (`StockBalanceRepository:227,236`) | G6a |
| Phiếu giao hàng từ chối | `DeliverySupport` | G10b |
| Hoàn vật tư / ứng viên cấp vật tư xét hạn | `MaterialSourceLedger.ineligibleReason` (`:82`), `MaterialIssueCandidateService:120` | G8b, G8d |
| QC mua không chấp nhận lô hết hạn | `PurchaseQualityService` | G7c |
| `aggregateBalances` (màn hình tồn) tách `expiredQuantity` | `StockBalanceRepository:38-49` | G4 |

### 3.2 Còn hở (khớp R01–R05 của FE)

| FE | Hở ở đâu | Bằng chứng |
|---|---|---|
| **R01 MRP** | `aggregateAvailableQuantities`, `aggregatePlanningQuantities`, `aggregateStockQuantitiesByWarehouse` chỉ lọc `l.status = AVAILABLE`, **không** xét `expiresAt`. Ba hàm gọi từ `InventoryAvailabilityService:50,68,87` | `StockBalanceRepository:250-307` |
| **R02a reserve tự động** | `findIssuableBalancesFefo` không xét hạn | `StockBalanceRepository:199-207`; gọi từ `MaterialReservationService:151` |
| **R02b reserve thủ công** | `ensureAvailableForReservation` chỉ kiểm `canIssue()` (= `status == AVAILABLE`), không kiểm hạn | `WorkOrderExecutionSupport:130-138`; `InventoryLot.canIssue():61` |
| **R03 issue / approve** | `MaterialIssueService` kiểm `canIssue()` (`:206`) rồi gọi `movementService.issue/issueReserved` với `rejectExpiredLot=false`. Javadoc `InventoryMovementService:134-139` ghi đây là **quyết định có chủ đích của user ngày 2026-10-15** | `MaterialIssueService:362,376,683,688` |
| **R04 readiness/release** | `sumActiveRemainingByWorkOrderId` cộng mọi reservation `ACTIVE`, không nhìn trạng thái/hạn lô ⇒ reservation trên lô đã hết hạn vẫn tính đủ 100% | `MaterialReservationRepository:131-139` |
| **R05 replay** | Chưa kiểm riêng; mẫu replay-trước-version của G8 đã áp, nhưng chưa có test nào cho "retry sau khi lô hết hạn" | cần test |

### 3.3 Cách sửa đề xuất

1. `StockBalanceRepository`: thêm tham số `:now` và điều kiện `(l is null or l.expiresAt is null or l.expiresAt > :now)` vào 4 query ở bảng trên. Đồng bộ `InventoryAvailabilityService` truyền `Instant.now()`.
2. `WorkOrderExecutionSupport.ensureAvailableForReservation`: thêm kiểm hạn ⇒ `LOT_NOT_ELIGIBLE`.
3. `MaterialIssueService` (post + approve): gọi biến thể `rejectExpiredLot=true`. Cân nhắc đổi tên cho rõ nghĩa (hiện `issueManual` đã dùng cờ này).
4. Readiness: query `sumActiveRemaining` mới chỉ cộng reservation trên lô `AVAILABLE` còn hạn (hoặc không lô), trả thêm blocker `EXPIRED_RESERVATION` trong `WorkOrderMaterialReadinessLineResponse`. **Không** tự huỷ reservation (FE Q3 đồng ý: giữ lịch sử, xử lý tường minh).
5. Test: bắt buộc `*RepositoryIT` cho 4 query (mock không thấy được), biên `expiresAt = now`, `null`, `HOLD` chưa hết hạn, 2 Plant; rollback nhiều dòng khi dòng cuối hết hạn.

### 3.4 Rủi ro — **cần quyết định**

- 🔴 **Đổi số MRP.** Kho có lô hết hạn sẽ ra nhu cầu mua lớn hơn. Cùng loại với `G6b` (đã đo A/B). Nên làm A/B trên `D26-OMNIPLANT` trước khi báo FE.
- Hiện tại **hai con số mâu thuẫn**: `aggregateBalances` báo lô hết hạn là `expiredQuantity` còn MRP vẫn tính nó là khả dụng — đúng kiểu "một hệ thống hai con số" của `§0.44`. Sửa MRP là *giảm* mâu thuẫn, không thêm.
- Đây là việc **đảo ngược một quyết định đã ghi** (`InventoryMovementService:134-139`: "moving that rule into planning is a separate decision"). Nên ghi rõ "user chốt lại ngày …" vào `CLAUDE.md` khi làm.
- Reservation đã tồn tại trên lô vừa hết hạn: **không** huỷ (FE Q3). Hệ quả: WO phát hành sẽ bị chặn tới khi người dùng giải phóng tay — cần endpoint/luồng rõ ràng (`release-reservations` của G8a đã có).

**FE Q1/Q2/Q3 trả lời được ngay:** Q1 — chưa, 4/5 hở (bảng 3.2). Q2 — có, `<=` loại. Q3 — có, không tự xoá.

---

## 4. `FE_REQUEST_PURCHASE_QUARANTINE_GUARDS` — nguồn hàng mua đang QC

**Trạng thái: 🟡 gần đủ.** Phần lớn guard đã có sau G7c; hở đúng **một chỗ**.

### 4.1 Đã đủ

| FE | Hiện trạng |
|---|---|
| R02 status/issue/reserve/transfer | Đổi trạng thái lô đang chờ QC / lô con bị loại ⇒ `LOT_NOT_ELIGIBLE` (`B205`/`B206`, `InventoryLotService:120`). Issue/reserve/transfer đều `canIssue()` ⇒ `HOLD`/`REJECTED` bị từ chối (`InventoryMovementService:707`, `WorkOrderExecutionSupport:131`, `StockTransferService:226`) |
| R03 non-tracked, **chiều rút** | `adjust` âm dùng `ensureSufficientAvailableStock` (= quantity − reserved − qualityHold) ⇒ không rút được phần protected. `count` tuyệt đối chặn `counted < reserved + qualityHold` ⇒ `STATE_CONFLICT` (`InventoryMovementService` quanh `:560`) |
| R04 QC là đường duy nhất pending → accepted/rejected | `PurchaseQualityService`, khoá phiếu theo `B194` (hai QC cùng version: một thắng) |
| R05 cancel GR sau QC | `STATE_CONFLICT` (đã có, G7c) |
| Mã lỗi FE đề nghị (Q3) | `STATE_CONFLICT` đã là mã đang dùng cho guard này ở count |

### 4.2 Còn hở

- **R01 — `adjust` trên lô tracked không phải `AVAILABLE`.** `adjust()` gọi `resolveExistingLotForAdjustment` (`InventoryMovementService:~710`) = `resolveExistingLot` **không kiểm trạng thái lô**. Hệ quả: `ADJUST_IN` / `ADJUST_OUT` lên lô cha còn `HOLD` hoặc lô con `-R<n>` `REJECTED` *chạy được* và đổi `quantity` mà không qua phiếu nhận/QC. `count` tracked thì đã chặn (`resolveCountLot` → `canIssue`).
  - Ghi nhận từ chính GĐ7 v3 §6 mục 2/§8 (BE tự thừa nhận).
- **R03 chiều tăng của non-tracked:** `ADJUST_IN` tăng tổng kho — là hàng *tự do* nên hợp lệ; không phải hở. Cần **test chứng minh** thay vì sửa (FE yêu cầu "BE cung cấp test invariant").

### 4.3 Cách sửa đề xuất

- Trong `adjust()` (và để đồng bộ, mọi nhánh dùng `resolveExistingLotForAdjustment`): nếu lô thuộc nguồn QC còn sở hữu thì ném `STATE_CONFLICT`. Dùng lại đúng hai tra cứu **đã có** `InventoryLotService.changeStatus` đang gọi: `PurchaseQcLookupService` (phần chờ QC mua / lô con từ `PURCHASE_QC_DECISION`) và `LotQcOriginLookupService` (QC sản xuất). Không viết detector mới.
- ⚠️ **Phạm vi hẹp có chủ đích:** FE ghi rõ *"không cấm mọi HOLD không thuộc receipt"*. Đừng chặn `adjust` trên mọi lô `!= AVAILABLE` — chỉ lô còn thuộc nguồn QC.
- Phụ thuộc: `InventoryMovementService` đang là service lõi; thêm phụ thuộc `purchasing`/`workorder` vào nó dễ **vòng bean**. Đặt guard ở `InventoryService` (tầng gọi `adjust`) hoặc `InventoryLotService`, đúng chỗ `changeStatus` đã làm — đừng đặt trong `InventoryMovementService`.
- Test: `*IT` cho tracked parent `HOLD` (1 → 0 và +1), child `REJECTED`, và không lùi: lô `HOLD` thủ công *không thuộc receipt* vẫn adjust được; concurrent QC + adjust; thu hồi quyền.

**FE Q1/Q2/Q3:** Q1 có (đặt ở service canonical, không chỉ ẩn nút). Q2 có (adjust/count bị chặn, correction đi workflow riêng — *workflow đó chưa tồn tại*, ghi rõ trong trả lời). Q3 có.

---

## 5. `FE_REQUEST_CAPACITY_RELEASE_ALIGNMENT` — preview ↔ release

**Trạng thái: ❌ chưa làm — và code tự ghi nhận đây là lệch có chủ đích.**

### 5.1 Hiện trạng

- `WorkOrderScheduler` (javadoc `:27-30`): *"Unlike the plant capacity preview (G5) it does **not** divide by `capacityUnits`: release never has (CLAUDE.md §0.53 #6), and the preview of a work order must match its release."*
- `CapacityPreviewService:44-46,132`: *"each spread over its work center's working time with `capacityUnits` in parallel: elapsed = required / units"*.
- `CLAUDE.md §0.53 #6` đã ghi: *"Release và xem trước xếp lịch KHÁC nhau … Cố ý chưa đổi release (dời lịch WO sau này)"*.
- Hai preview **khác nhau**: `POST /v1/work-orders/{W}/schedule-preview` (G8a) dùng đúng `WorkOrderScheduler` nên khớp release; `POST /plants/{P}/capacity/preview` (G5) mới là cái chia `capacityUnits`. FE đang so cái thứ hai với release.

### 5.2 Fixture FE (kiểm bằng tay công thức)

Ca 08:00–17:00, nghỉ 12:00–13:00, `capacityUnits = 2`, qty 10, setup 0, run 12 ⇒ required 120 phút-tài-nguyên ⇒ preview 60 phút ⇒ 09:00. **Release hiện tại** không chia ⇒ 120 phút ⇒ 10:00. Lệch đúng 1 giờ.

### 5.3 FE R01–R05

| FE | Hiện trạng |
|---|---|
| R01 cùng kết quả khi cùng nguồn | ❌ khi `capacityUnits > 1` |
| R02 elapsed = required / capturedCapacityUnits | ❌ release |
| R03 nguồn đổi giữa preview và release | 🟡 `schedule-preview` đã có `scheduleRevision`/`calendarRevision`; release ghi lại (G8a), nhưng chưa có "expected source revision" |
| R04 giữ lịch WO đã release | ✅ release chỉ ghi lúc release; WO cũ không bị dời |
| R05 snapshot đủ giải thích | ❌ chưa có `capturedCapacityUnits`, `requiredResourceMinutes`, `activeElapsedMinutes`, `predecessorOperationIds` trên snapshot công đoạn của WO. (Có `plannedStartAt/EndAt`, `workCenterId`.) |

### 5.4 Việc cần làm

1. Cho `WorkOrderScheduler` chia `capacityUnits` **chụp lúc release** (cùng công thức `CapacityPreviewService.place`). Gom về một chỗ để không có hai bản công thức nữa — đây là chính cái lệch đang bị than phiền.
2. Migration: thêm vào `work_order_operations` các cột snapshot (`captured_capacity_units`, `required_resource_minutes`, `active_elapsed_minutes`, nullable, không backfill — WO cũ trả `null`). Dependency IDs: kiểm `RoutingOperationPredecessor` đã snapshot vào WO chưa — **chưa kiểm**.
3. Tải trên Capacity Board **không đổi** (vẫn `required`, không chia) — FE fixture đã nói rõ "tải vẫn 120".
4. Giữ release **không body** (FE đồng ý; không thêm field bắt buộc).

### 5.5 Rủi ro — cần quyết định

- **Đổi lịch của mọi WO release sau này** (không chạm WO cũ — FE Q2 đã đồng ý). Test cũ `WorkOrderServiceTest.release_schedules*` và `WorkOrderSchedulePreviewServiceTest` sẽ phải sửa theo `R10`.
- Có thể vào trong `G8a`-style nhưng đổi *kết quả* nên là breaking nhẹ (wire shape không đổi, số đổi).
- Ca biên FE nêu (11:30 + 120 phút/cap 2 ⇒ 13:30 vắt qua giờ nghỉ) cần ca test riêng.

---

## 6. `FE_REQUEST_MRP_PENDING_QC_VISIBILITY` — pending QC hiện trong MRP

**Trạng thái: ❌ chưa làm.** `grep pendingQcQuantity|pendingQcSourceCount|pending-qc-supplies` chỉ trúng module `workorder` (phiếu nhập sản xuất, **không phải** nguồn mua).

### 6.1 Vì sao không tự có

Hàng nhận mua vào `HOLD`/vùng chờ (G7b) ⇒ không còn trong `openQuantity` PO (đã nhận) và chưa `AVAILABLE` ⇒ MRP không thấy. Đúng ý FE R14 nhưng người lập kế hoạch không có cảnh báo (`BE_RESPONSE_GD_07_…_v3` Q30 tự ghi nhận).

### 6.2 Việc cần làm

- **Không đổi netting** (FE Q1 đồng ý — fixture 40/15/25 vẫn shortage 25).
- Mỗi lần chạy run, **chụp** vào snapshot (cùng cách `input_snapshot` của G6b `mrp_runs`): với mỗi dòng nhu cầu, tổng `received − accepted − rejected` của dòng phiếu nhận `POSTED` đúng Item+Kho. Thêm `pendingQcQuantity`, `pendingQcSourceCount` vào `MrpRequirementLineResponse` (record này đã bị +11 ở G6b — kiểm compat constructor).
- Nguồn: `GoodsReceiptLine.received − accepted − rejected` với `GoodsReceipt.status = POSTED` và `quality_held = true` (cột đã có ở `V77`). Một query tổng hợp theo (item, warehouse) — **không loop** (`C14`).
- Endpoint mở nguồn `GET /v1/planning-runs/{id}/pending-qc-supplies` (FE ghi là *đề xuất*, có thể thay bằng mapping tương đương). Snapshot bất biến ⇒ cần bảng con `mrp_pending_qc_sources` (run_id, requirement_line_id, receipt_id, line_id, pending, receipt_version) — nếu chỉ lưu tổng thì endpoint này không dựng lại đúng nguồn lúc chạy (FE Q3 muốn đúng snapshot).
- Quyền: `PERM_MRP_READ` đọc được, không cần `PERM_GOODS_RECEIPT_READ`, **không trả giá/liên hệ** (FE Q2). Cần một projection tối thiểu, không dùng lại `GoodsReceiptResponse`.
- Cảnh báo trên đề xuất `BUY`: thêm vào `messages[]` mã `PENDING_QC_SUPPLY` khi `pendingQcQuantity > 0` (cơ chế `exceptionState`/`messages[]` đã có từ F5-B).

### 6.3 Ghi chú

- Chỉ **nguồn mua**. Phiếu nhập sản xuất chờ QC (G9c) không nằm trong yêu cầu này.
- Run cũ (trước migration) ⇒ `NULL` = "chưa chụp", **không** `0` (cùng nguyên tắc `B77`).
- Migration mới ⇒ số kế tiếp sau `V92`.

---

## 7. `FE_REQUEST_NONTRACKED_QC_BREAKDOWN` — tách pending / rejected cho hàng không theo lô

**Trạng thái: ❌ chưa làm.** Không trường nào trong 4 field FE đề xuất tồn tại.

### 7.1 Hiện trạng

Hàng non-tracked: `qualityHoldQuantity` là **một con số gộp** pending + rejected (và có thể cả hold khác). Không có cột "rejected" riêng cho non-tracked (đã nêu ở `B116` và GĐ7 v3 §6). Nhận 25, đạt 20 / loại 5 ⇒ onHand 25, `qualityHold` 5, available 20 — FE đọc nhãn "chờ QC = 5" trong khi receipt báo pending 0.

### 7.2 Việc cần làm

Trên `GET /v1/inventory/balances/aggregate` thêm **additive** (giữ nguyên nghĩa `qualityHoldQuantity`):

| Field | Công thức |
|---|---|
| `pendingPurchaseQcQuantity` | Σ pending của dòng phiếu nhận `POSTED` non-tracked đúng (item, kho) |
| `rejectedPurchaseQcQuantity` | Σ `rejected` đã ghi quyết định của cùng nhóm |
| `unclassifiedQualityHoldQuantity` | `qualityHold − pending − rejected` khi đủ nguồn; `null` khi không |
| `qualityBreakdownKnown` | `false` nếu không dựng được từ nguồn |

- **Nguồn đã có sẵn:** `goods_receipt_lines.accepted_quantity/rejected_quantity` + `purchase_quality_decisions` (V78). Nhận bằng một query tổng hợp theo trang aggregate (`C14`), không loop.
- **Legacy:** phiếu nhận trước `V77` có `quality_held = false` (không có hold để phân loại) ⇒ không ảnh hưởng. Hold non-tracked đến từ nguồn khác (sản xuất G9c: `qualityBucketId` = dòng phiếu nhập SX) ⇒ vào `unclassified` hoặc phải nối thêm nguồn thứ hai; **cần chốt** có tính phiếu nhập sản xuất vào các field `…PurchaseQc…` hay không (tên field FE đặt là "Purchase", nên không).
- Quyền: `PERM_INVENTORY_READ` như cũ, không trả tiền (FE R05).
- Test bắt buộc `*RepositoryIT` (JPQL tổng hợp) cho fixture FE: nhận 25 → đạt 10 / loại 5 ⇒ pending 10, rejected 5, available 10, qualityHold 15; đợt 2 đạt 10 ⇒ pending 0, rejected 5, available 20, qualityHold 5; quyền inventory-only không lộ giá.
- Lưu ý đẳng thức: `qualityHold = pending + rejected + unclassified`. FE nhấn mạnh không cộng rejected hai lần vào OnHand.

---

## 8. `FE_QUESTION_DECIMAL_WIRE_PRECISION` — decimal trên wire

**Trạng thái: ✅ hành vi đã rõ, ❌ chưa có tài liệu trả lời.** Không code.

Sự thật trong code (kiểm được ngay):

- Response BigDecimal ra **JSON number** (`application.yml`: `jackson` không có `write-bigdecimal-as-plain` hay cấu hình string; `default-property-inclusion: always`). ⇒ **Q1 = Có**, giữ number.
- `@Digits(integer = 13, fraction = 6)` trên `quantity`, `quantityPer`, `quantity` ở ít nhất 3 DTO; `scrapRate` `@Digits(integer = 1, fraction = 6)`. DB: `NUMERIC(19,6)`. Số lẻ thứ 7 ⇒ 400 (G4). ⇒ **Q4 một phần**: quantity 13+6; scrapRate 1+6; phần còn lại (minutes, rates, `capacityUnits`) cần liệt kê bằng grep `@Digits` toàn repo rồi lập bảng.
- Chưa **thử** request gửi decimal dạng chuỗi (`"1.5"`). Jackson mặc định cho phép ép chuỗi → số (`ALLOW_COERCION_OF_SCALARS`) và `accept-float-as-int: false` chỉ chặn *số lẻ cho số nguyên*. Dự đoán: `BigDecimal` nhận cả `1.5` lẫn `"1.5"`, `int/long` nhận `"5"` nhưng không nhận `5.0`. **Phải thử bằng một request thật** trước khi trả lời Q3 (không trả lời từ suy đoán).
- Q2 (FE dùng parser lossless) là quyết định phía FE; BE chỉ cần xác nhận không đổi wire. **Không có gì BE phải làm**.

**Việc làm:** viết `BE_RESPONSE_NUMERIC_CONTRACT_<ngày>.md`: (a) bảng range/scale theo nhóm (grep `@Digits`, cột `NUMERIC(…)` trong migration), (b) 4 mẫu biên `0 / 0.000001 / 999999999.999999 / 9999999999999.999999` kèm kết quả thật của một request (đo, không suy), (c) trả lời Q1–Q4. Nếu muốn khoá lại thì thêm một ca vào `JsonWireFormatTest` chứng minh BigDecimal 13+6 chữ số được serialize **đủ chữ số** (hiện test đó mới kiểm ngày/giờ và null).

---

## 9. `FE_QUESTION_BOM_BATCH_RATIO` — BOM theo lô, công thức 1/3

**Trạng thái: ✅ hành vi đúng như FE mô tả; chờ PO chốt hướng.** Không code.

- **D01 xác nhận được:** `BomService:443-446` trả lỗi tại `lines[i].componentQuantity` khi `perUnit(component, output)` không biểu diễn được. `perUnit` (`:516-518`) dùng `componentQuantity.divide(outputQuantity)` **không có MathContext** nên `1 ÷ 3` ném `ArithmeticException` (không kết thúc) và bị bắt thành lỗi field — tức **không** làm tròn âm thầm. `BomService:455` mô tả "a quotient that does not fit 6 decimals" ⇒ 400. Khớp mô tả FE.
  - **Chưa kiểm bằng request thật** (không chạy app); nên có sẵn một ca test (có thể đã có trong `BomServiceTest`/`BomDraftIT` — chưa grep) hoặc đo 1 request.
- **Nhập Excel** (`kind=bom`): dùng cùng `BomService` nên cùng rule (FE Q3 ✅ về nguyên tắc). Mã lỗi trong `rows[].issues[]` — **chưa kiểm** có phải một mã ổn định hay không.
- **D02/D03 là quyết định PO**, không phải BE: giữ giới hạn scale 6 (không hỗ trợ 1/3) hay lưu `componentQuantity`+`outputQuantity` làm nguồn chính xác theo lô. Ghi nhận:
  - Mô hình hiện tại: `quantity_per` là nguồn duy nhất (D1 của G5); `output_quantity` chỉ để form đọc lại (`§0.53 #1`). `componentQuantity` là projection.
  - Hướng FE muốn (lưu theo lô, per-unit tính lúc cần) là **đảo nguồn chính xác** — đụng MRP nổ BOM, snapshot WO (`B12`), giá thành, copy/version, và import. Đây chính là lý do nợ `E` (F10) từng bị loại. Cần task riêng, không nằm trong follow-up này.
  - Tác động số: sản xuất 6 PCS từ công thức 1 KG/3 PCS cần đúng 2 KG; `0.333333 × 6 = 1.999998` ⇒ thiếu 0.000002 KG mỗi lần. MRP đã làm tròn lên 6 số lẻ trước khi nổ (Q28 G6b) nên sai số một chiều là *thừa*, không thiếu ở mức MRP — nhưng chênh lệch vẫn lọt vào variance WO.

**Việc làm:** trả lời Q1–Q3 bằng văn bản, ghi "chờ PO chốt phạm vi" ở Q2 (FE cố ý yêu cầu không coi BE chọn thay PO). Nếu PO chọn hướng mở rộng thì mở phase riêng, bắt đầu bằng đo tác động.

---

## 10. Việc BE cần làm, theo thứ tự

| Thứ tự | Việc | Loại | Cần quyết định trước | Ước lượng phạm vi |
|---|---|---|---|---|
| 1 | Guard `adjust` trên lô thuộc nguồn QC (#4 R01) + test invariant | Code nhỏ | Không | 1 guard, ~6 test, 1 `*IT` |
| 2 | Hết hạn xuyên MRP/reserve/issue/readiness (#3) | Code vừa | **Có** — đổi số MRP, đảo quyết định 2026-10-15; nên A/B | 4 query + 3 service + 1 DTO + `*IT` |
| 3 | Pending QC trong MRP (#6) | Code vừa, additive | Hỏi lại có cần endpoint riêng không | 1 migration + 1 bảng snapshot + DTO + query |
| 4 | Breakdown non-tracked (#7) | Code nhỏ, additive | Có tính phiếu nhập SX không | 1 query + 4 field + `*IT` |
| 5 | Scheduler release chia capacity + snapshot (#5) | Code vừa | **Có** — đổi lịch WO mới | scheduler + 1 migration + sửa test cũ |
| 6 | Tài liệu: version map (#1), numeric contract (#8), BOM ratio (#9) | Tài liệu | PO chốt Q2 của #9 | 3 file `.md` |
| 7 | Định giá tồn kho (#2) | Phase lớn | **Có**, 4 câu + gộp với `P7` | nhiều migration, breaking `unitCost` |

### Quyết định PO cần có (gom lại)

1. **#3** — chấp nhận MRP đổi số khi có lô hết hạn? (và ghi đảo quyết định 2026-10-15).
2. **#2** — làm định giá không? Nếu có: quyền đọc tiền (`PERM_COSTING_READ` tái dùng hay mới); `unitCost` bắt buộc ngay hay tuỳ chọn trước; nguồn giá của thành phẩm nhập kho; gộp với `P7`.
3. **#5** — đồng ý lịch WO release mới ngắn lại khi `capacityUnits > 1`.
4. **#9** — giữ scale 6 hay mở rộng mô hình (FE nghiêng về mở rộng).
5. **#7** — phiếu nhập sản xuất có vào breakdown không (đề xuất: không, thuộc nhóm `unclassified`/tách sau).

### Lưu ý khi triển khai (từ bài học trong repo)

- Mọi thay đổi JPQL ở #3, #6, #7 **bắt buộc có `*RepositoryIT`** — mock repository không bắt được lỗi kiểu dữ liệu/`lower(bytea)`/join (`R7`, `CLAUDE.md §0.24`). Tham số `Instant` chỉ xuất hiện ở vế `IS NULL` cần `cast(... as timestamp)` (`§0.36 #4`).
- Mọi migration mới đánh số **sau `V92`**; `MANUFACTURING_PHASE_PLAN_P7_P11.md` đang giữ `V93`+ nên phải đánh số lại.
- Thêm field vào record response ⇒ giữ compat constructor như các phase G6–G10 đã làm; thêm field vào command có `Idempotency` ⇒ kiểm fingerprint cũ vẫn khớp (`§0.52 #7`).
- Mỗi lần xong một mục: cập nhật `CLAUDE.md §0`, `NEXT_PHASE_PLAN.md`, và `docs/api-guide-for-frontend.md` nếu đổi wire (`dev-workflow.md §6.5`).
- Smoke HTTP thật sau khi `mvn verify` xanh — nhiều phase gần đây (G8b, G9a, G10b) chỉ lộ bug version +2 / µs ở bước này.

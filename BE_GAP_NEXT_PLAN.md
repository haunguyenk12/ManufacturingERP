# BE_GAP_NEXT_PLAN — Kế hoạch các phase chưa làm của bộ `CAPSTONE3_BE_FOLLOWUP_2026-10-20`

**Lập ngày:** 2026-10-27 · **Nền:** commit `542760c` (G11), migration mới nhất `V92`, baseline **2141 unit + 315 IT**
**Căn cứ:** `CAPSTONE3_BE_GAP_REPORT_2026-10-27.md` (kết quả đối chiếu, có `file:dòng`) và 9 tài liệu trong `CAPSTONE3_BE_FOLLOWUP_2026-10-20/`.
**Không thay** `NEXT_PHASE_PLAN.md`. Khi bắt đầu một phase ở đây, chuyển nó vào `NEXT_PHASE_PLAN.md` theo `dev-workflow.md §6.6` (mỗi lúc đúng một phase đang chạy).

> **Mã phase `CF-*`** = *Capstone-3 Follow-up*. Chưa trùng mã `G*`/`P*`/`F*`/`D*`/`C2-*` nào.
> Mọi quyết định đánh dấu **[PO]** là **đề xuất mặc định của BE, chưa được duyệt**. Phase có cổng **[PO]** chưa được bắt đầu code cho tới khi có câu trả lời.

---

## 0. Tổng quan & thứ tự

| Phase | Nội dung | Loại | Migration | Cổng [PO] | Phụ thuộc |
|---|---|---|---|---|---|
| **CF-0** | Tài liệu: version map, numeric contract, trả lời BOM ratio | Tài liệu + 1 phép đo | — | Q2 của BOM (chỉ để *ghi*, không chặn) | — |
| **CF-1** | Guard `adjust` trên lô thuộc nguồn QC | Code nhỏ | — | — | — |
| **CF-2** | Chặn hàng hết hạn: MRP + giữ chỗ + xuất vật tư + readiness | Code vừa, **đổi số MRP** | — | **Có** (đảo quyết định 2026-10-15) | — |
| **CF-3** | Tách pending/rejected cho hàng không theo lô (aggregate) | Code nhỏ, additive | — | Có (phạm vi phiếu nhập SX) | — |
| **CF-4** | Hiển thị nguồn mua chờ QC trong MRP | Code vừa, additive | 1 | Có (endpoint riêng) | CF-3 (dùng lại query) |
| **CF-5** | Thống nhất công thức capacity giữa xem trước và release | Code vừa, **đổi lịch WO mới** | 1 | **Có** | — |
| **CF-6** | Sổ giá trị tồn kho / WIP | **Phase lớn**, chia 4 | nhiều | **Có** (4 câu) | CF-2, CF-3 xong; cân nhắc gộp `P7` |
| **CF-7** | Nguồn chính xác BOM theo lô (1/3) | Chỉ mở nếu PO chọn | 1+ | **Có** | — |
| **CF-8** | *(đợt bổ sung 28/10, §12)* Trả lời tài liệu: giá chuẩn, BOM scale 6, contract + nhận diện bản | Tài liệu + vài phép đo | — | Nhẹ (build-info, §12.9) | — |
| **CF-9** | Mã lô không trùng khác hoa thường khi nhận đồng thời | Code nhỏ + migration, **sửa lỗi thật** | 1 (`V95`) | Có nếu có lô trùng sẵn | — |
| **CF-10** | Chặn số lẻ thứ 7 / quá 13 chữ số nguyên trước khi DB làm tròn | Code vừa, **chặn mới** | — | — | — |
| **CF-11** | Một công thức bù hao hụt `×(1+rate)` giữa BOM–MRP–WO–giá thành | Chủ yếu test + 1 sửa nhỏ | — | **Có** (quy tắc làm tròn) | — |
| **CF-12** | Khép phần bảo vệ QC sau CF-1 (count / non-tracked / race) | Ma trận + IT, code nếu IT đỏ | — | — | — |
| **CF-13** | Test A39/A40 + smoke chốt giá thành | Chủ yếu test | — | — | — |

**Thứ tự đề xuất:** `CF-0` ∥ `CF-1` → `CF-2` → `CF-3` → `CF-4` → `CF-5` → (chờ PO) `CF-6` → (chờ PO) `CF-7`.

Lý do thứ tự:
- `CF-1` nhỏ, an toàn, không cần quyết định — làm ngay để có một phase xanh.
- `CF-2` trước `CF-6`: định giá phải dựa trên một định nghĩa "tồn khả dụng" đã thống nhất; sửa sau khi có cột giá là sửa hai lần.
- `CF-3` trước `CF-4`: cùng nguồn dữ liệu (`goods_receipt_lines` chờ QC), `CF-4` dùng lại query tổng hợp của `CF-3`.
- `CF-5` độc lập, để sau vì cần PO duyệt và đổi lịch WO mới.
- `CF-6` cuối cùng trong nhóm code: chạm gần như mọi đường ghi tồn kho (bán kính ảnh hưởng lớn nhất), nên phải đặt trên nền đã ổn định.
- `CF-7` có thể không bao giờ mở.

`CF-0` song song với mọi phase vì không đụng code.

---

## 1. Quy tắc chung cho mọi phase `CF-*`

Rút từ bài học đã ghi trong `CLAUDE.md` — không lặp lại lý do, chỉ liệt kê điều bắt buộc.

1. **Đo baseline trên `git worktree` sạch của HEAD trước khi sửa** (`§0.52 #10`). Không sửa file trong lúc `mvn` đang chạy — kể cả `CLAUDE.md` nằm dưới `src/main/java` (`§0.65 #7`). Phải dừng app dev trước `mvn clean`.
2. **JPQL mới/đổi ⇒ bắt buộc `*RepositoryIT`** (mock không bắt được lỗi kiểu/join — `R7`). Tham số `Instant`/`String` nullable chỉ xuất hiện ở vế `IS NULL` ⇒ `cast(... as timestamp|string)` (`§0.24`, `§0.36 #4`).
3. **Mutation testing cho mọi bất biến mới**, mỗi mutation đúng một case đỏ; nêu cả mutation giữ nguyên HTTP status (assert bám `ErrorCode`, không bám status). Dọn `*.mutbak` bằng `find src -name "*.mutbak"` sau mỗi lượt.
4. **Smoke HTTP trên app chạy thật** (Postgres + Redis qua `docker compose`, `java -jar` profile dev) sau khi `mvn -o clean verify` xanh. Kiểm bằng *giá trị lúc ghi = giá trị đọc lại* cho mọi `Instant` (bẫy µs, `§0.51 #3`) và `version` +1 đúng một lần ở lệnh nào đổi chứng từ có `version` (bẫy `B112`/`B212`).
5. **Thêm field vào record response ⇒ giữ compat constructor** như G6–G10 đã làm; **thêm field vào command có `Idempotency` ⇒ chứng minh fingerprint cũ vẫn khớp** (`§0.52 #7`).
6. **Migration thuần additive, không backfill giá trị suy diễn.** `NULL` nghĩa là "không ai chụp lúc đó", không phải `0` (`B77`, `V67`). Số migration: **số kế tiếp tại thời điểm làm** (hiện `V93`). `MANUFACTURING_PHASE_PLAN_P7_P11.md` đang giữ `V93`–`V97` ⇒ phase nào đáp trước thì lấy số trước, file P7–P11 phải đánh số lại ngay trong commit đó.
7. **Cuối mỗi phase, theo `dev-workflow.md §6.5–§6.6`:** kiểm chứng → cập nhật `CLAUDE.md §0` (+ bất biến `B<n>` ở `CLAUDE.md` của module, đánh số tiếp từ **`B263`**) → `docs/api-guide-for-frontend.md` nếu đổi wire → `docs/roles-and-permissions.md` nếu thêm quyền → commit `feat(<module>): CF-n — …`, **không push**.
8. **Trả lời FE:** mỗi phase có thay đổi wire kèm một `BE_RESPONSE_CF_<n>_<ngày>.md` theo `BETaskDeliveryRules.md`, ví dụ copy từ lần gọi thật (không qua bộ làm đẹp JSON — `§0.50 #7`).
9. **Định nghĩa Xong của mỗi phase = Definition of Done ở `coding-rules.md §11.7`** cộng: số test ≥ baseline, 0 failures/errors, test cũ **sửa** (R10) không xoá.

---

## 2. CF-0 — Tài liệu trả lời (không code)

**Nguồn:** FE_QUESTION ×3 (GĐ6/7 contract, Decimal wire, BOM ratio). **Ước lượng:** nửa ngày.

### 2.1 Việc làm

| # | Sản phẩm | Nội dung | Kiểm chứng |
|---|---|---|---|
| CF-0.1 | `BE_RESPONSE_VERSION_MAP_<ngày>.md` | Bảng *bản nào thay bản nào* GĐ2→GĐ11: tên file ↔ nội dung thật ↔ ngày ↔ migration (`CLAUDE.md §0.50–§0.71` đã có đủ). Nêu rõ hai chỗ tên file lệch ngày nội dung (GĐ6 v3, GĐ7 v3). Dấu nhận diện bản BE: `flyway_schema_history` max = `92` (hoặc `/actuator/info` nếu kiểm ra có) | Mở `BE_RESPONSE_G7-11.zip` để xác nhận nó là bộ nào, tránh gửi thừa/thiếu |
| CF-0.2 | `BE_RESPONSE_NUMERIC_CONTRACT_<ngày>.md` | (a) bảng range/scale theo nhóm — dựng từ `grep @Digits` + `NUMERIC(…)` trong migration, không viết từ trí nhớ; (b) 4 mẫu biên `0 / 0.000001 / 999999999.999999 / 9999999999999.999999` kèm response **thật**; (c) trả lời Q1–Q4 | **Một phép đo bắt buộc:** gửi request với decimal dạng chuỗi `"1.5"` và dạng số cho một field `BigDecimal` và một field `Integer`, ghi kết quả. Không trả lời Q3 từ suy đoán |
| CF-0.3 | `BE_RESPONSE_BOM_RATIO_<ngày>.md` | Xác nhận D01 (đã đọc code: `BomService.perUnit` bắt `ArithmeticException` → 400 tại `lines[i].componentQuantity`); xác nhận form và `kind=bom` dùng chung `BomService` ⇒ cùng rule; ghi **"chờ PO chốt phạm vi"** cho D02/D03 | Đo 1 request `PUT bom-drafts` với 1/3 và 1 file import 1/3; ghi mã lỗi thật trong `rows[].issues[]` (hiện *chưa kiểm*) |
| CF-0.4 | Thêm ca vào `JsonWireFormatTest` | BigDecimal 13+6 chữ số được serialize **đủ chữ số** (hiện test chỉ kiểm ngày/giờ/null) — khoá câu trả lời Q1 bằng test | `mvn -o test -Dtest=JsonWireFormatTest` |

### 2.2 Không làm

Không đổi wire, không đổi `application.yml`. Nếu phép đo CF-0.2 cho thấy request chuỗi **không** được nhận, ghi đúng kết quả; mở rộng chấp nhận là quyết định riêng.

### 2.3 Xong khi

3 file trả lời có ví dụ đo thật; `JsonWireFormatTest` +1 ca xanh; `CLAUDE.md §0.1` ghi dòng ngắn (không migration).

---

## 3. CF-1 — Guard `adjust` trên lô thuộc nguồn QC

**Nguồn:** `FE_REQUEST_PURCHASE_QUARANTINE_GUARDS` R01–R03. **Ước lượng:** ~1 ngày. **Không migration. Không quyền mới.**

### 3.1 Phạm vi — hẹp có chủ đích

Chỉ `POST /api/v1/inventory/adjust` trên **lô tracked còn thuộc nguồn QC**:
- lô cha đang chờ QC mua (`PurchaseQcLookupService` — còn phần chờ);
- lô con `-R<n>` từ `PURCHASE_QC_DECISION` (đã bị loại);
- lô còn phần chờ QC sản xuất (`LotQcOriginLookupService`, G9c).

**Không** chặn lô `HOLD` thủ công không thuộc receipt nào (FE nói rõ: *"không cấm mọi HOLD không thuộc receipt"*). **Không** chặn `ADJUST_IN` trên hàng non-tracked (là hàng tự do). `count` tracked đã chặn (`resolveCountLot` → `canIssue`); non-tracked đã chặn `counted < reserved + qualityHold` ⇒ `STATE_CONFLICT`.

### 3.2 Thay đổi

| Việc | Chỗ |
|---|---|
| Guard đặt ở **`InventoryService.adjust`** (tầng gọi) hoặc `InventoryLotService`, **không** trong `InventoryMovementService` | Cùng chỗ `InventoryLotService.changeStatus` đã gọi hai lookup này — tránh vòng bean `inventory → purchasing/workorder` thêm lần nữa |
| Mã lỗi: `STATE_CONFLICT` (409) cho lô thuộc nguồn QC; **không** tạo mã mới | FE Q3 đồng ý |
| Thứ tự: kiểm quyền (đã có ở `@PreAuthorize`) → replay Idempotency → guard → ghi. Guard kiểm **dưới khoá** nếu đã có khoá lô; nếu chưa, đọc lại sau `lockPosition…` | Ghi vào javadoc |

### 3.3 Test

- Unit: guard cho cả 3 nguồn; lô `HOLD` thủ công **không** nguồn ⇒ `adjust` vẫn chạy (ranh giới hẹp); non-tracked `ADJUST_IN` chạy.
- `*IT` (class mới hoặc mở rộng `PurchaseQualityIT`): sau QC nhận 4 → đạt 2.5 / loại 0.5: lô cha `HOLD` 1, `adjust +1` ⇒ 409, `adjust −1` ⇒ 409, lô con `REJECTED` 0.5 ⇒ 409; lô con `AVAILABLE` 2.5 ⇒ `adjust` hợp lệ; **không** sinh movement khi bị chặn; hai thao tác QC + adjust đồng thời ⇒ một thắng.
- Thu hồi quyền giữa chừng ⇒ 403 trước replay.
- **Mutation:** bỏ guard ⇒ đỏ; đổi `STATE_CONFLICT` → `IDEMPOTENCY_CONFLICT` (cùng 409) ⇒ đỏ; mở rộng guard sang mọi lô `!= AVAILABLE` ⇒ ca "HOLD thủ công" đỏ.

### 3.4 Trả lời FE

Q1 có · Q2 có (**ghi rõ:** workflow "sửa số lượng nguồn" chưa tồn tại — adjust/count bị chặn, chưa có đường thay thế) · Q3 có. Thêm bảng "phần nào đã đủ từ trước": status/issue/reserve/transfer/count/cancel-GR (G4/G7c) kèm tên test hiện có.

### 3.5 Xong khi

Guard + test xanh; `B263` ghi vào `module/inventory/CLAUDE.md`; `docs/api-guide-for-frontend.md` thêm dòng lỗi mới cho `adjust`; **breaking hẹp:** `adjust` lô nguồn QC từ 200 ⇒ 409.

---

## 4. CF-2 — Chặn hàng hết hạn xuyên MRP, giữ chỗ, xuất vật tư

**Nguồn:** `FE_REQUEST_EXPIRED_STOCK_GUARDS` R01–R05. **Ước lượng:** 2–3 ngày. **Không migration. Không quyền mới.**

### 4.1 Cổng [PO] — phải có trước khi code

1. **Chấp nhận MRP đổi số** khi kho có lô hết hạn (nhu cầu mua tăng). Đề xuất: **Có**, vì hiện hai con số mâu thuẫn (`aggregateBalances` báo `expiredQuantity`, MRP vẫn tính khả dụng — đúng kiểu `§0.44`).
2. **Ghi nhận đảo quyết định** của user ngày 2026-10-15 (`InventoryMovementService:134-139`: *"moving that rule into planning is a separate decision"*). Ghi vào `CLAUDE.md` kèm ngày chốt mới.
3. Reservation đã tồn tại trên lô vừa hết hạn: **giữ nguyên, không tự huỷ** (FE Q3); xử lý bằng `POST /work-orders/{W}/release-reservations` (G8a) có sẵn.

### 4.2 Bước 0 — đo A/B trước khi sửa (bắt buộc, như G6b)

Chạy `POST /planning-runs/commercial` trên `D26-OMNIPLANT` với 1–2 lô hết hạn (đặt bằng `scripts/demo-capstone2/set-lot-expiry.sh`) bằng bản cũ (worktree) và bản mới; ghi chênh lệch số đề xuất `BUY`. Kết quả vào `BE_RESPONSE_CF_2`.

### 4.3 Thay đổi

| # | FE | Việc | Chỗ |
|---|---|---|---|
| a | R01 MRP | Thêm `:now` và `(l is null or l.expiresAt is null or l.expiresAt > :now)` vào `aggregateAvailableQuantities`, `aggregatePlanningQuantities`, `aggregateStockQuantitiesByWarehouse`. `InventoryAvailabilityService:50,68,87` truyền `Instant.now()` | `StockBalanceRepository:250-307` |
| b | R02a reserve tự động | Cùng điều kiện cho `findIssuableBalancesFefo` | `StockBalanceRepository:199-207`; gọi từ `MaterialReservationService:151` |
| c | R02b reserve thủ công | `ensureAvailableForReservation` thêm kiểm hạn ⇒ `LOT_NOT_ELIGIBLE` | `WorkOrderExecutionSupport:130-138` |
| d | R03 issue/approve | `MaterialIssueService` post **và** approve gọi biến thể `rejectExpiredLot=true`; tái dùng cờ của `issueManual` thay vì thêm cờ thứ hai (đổi tên cho đúng nghĩa nếu cần) | `MaterialIssueService:362,376,683,688`; `InventoryMovementService:134-145,404-418` |
| e | R04 readiness | Query mới `sumActiveRemaining…` chỉ cộng reservation trên lô `AVAILABLE` còn hạn (hoặc không lô); thêm `blocker`/`ineligibleReason` vào `WorkOrderMaterialReadinessLineResponse`. **Không** huỷ reservation | `MaterialReservationRepository:131-139` |
| f | R05 replay | Không đổi code; thêm **test** chứng minh: issue đã post trước `T`, retry cùng key sau `T` ⇒ trả receipt cũ, không issue lần hai; replay sau thu hồi quyền ⇒ 403 | `MaterialIssueServiceTest`, E2E |

**Đồng bộ:** `heldLotQuantity`/`expiredQuantity` của `aggregateBalances` không đổi. Candidates (`MaterialIssueCandidateService`) đã xét hạn, không đổi.

### 4.4 Test

- `*RepositoryIT` cho (a), (b), (e): biên `expiresAt = now` (loại), `now + 1s` (vào), `null` (vào), `HOLD` chưa hết hạn (loại), 2 Plant, 2 kho. Fixture phải có **lô có hạn lẫn không hạn** (bài học `§0.45 #2` — dữ liệu quá sạch che lỗi).
- Fixture FE: lô A 10 KG hết hạn `T`, lô B 6 KG hết hạn `T+1d`, cần 10 ⇒ OnHand 16, eligible 6, shortage 4; auto-reserve tối đa 6 (partial, đúng `reserveAutomatically` hiện tại), **không** đổi sang đủ 10.
- Biên giữa request và khoá: lô hết hạn trong lúc phiếu cấp chờ duyệt ⇒ approve 409, không trừ kho/WIP/giá thành accumulator.
- Rollback nhiều dòng khi dòng cuối hết hạn.
- **Mutation:** bỏ từng điều kiện hạn ở (a)(b)(c)(d)(e) ⇒ mỗi cái ≥ 1 case đỏ; `>` → `>=` ở biên ⇒ case biên đỏ.

### 4.5 Rủi ro

- Số MRP đổi ⇒ test cũ `MrpCalculationServiceTest`, `ProductionFlowE2EIT` có thể cần sửa (R10, không nới assertion).
- WO đã `RELEASED` có reservation trên lô sắp hết hạn: issue sẽ bị chặn đúng lúc xưởng cần — **đây là hành vi FE muốn**, nhưng cần nói rõ với FE.

### 4.6 Xong khi

5 điểm hở đóng; A/B ghi lại; `B264`–`B265`; breaking: MRP đổi số, reserve/issue/approve trên lô hết hạn ⇒ 409 `LOT_NOT_ELIGIBLE`, readiness trả blocker.

---

## 5. CF-3 — Tách pending / rejected cho hàng không theo lô

**Nguồn:** `FE_REQUEST_NONTRACKED_QC_BREAKDOWN`. **Ước lượng:** ~1 ngày. **Không migration.**

### 5.1 Cổng [PO] (nhẹ)

Phiếu nhập **sản xuất** non-tracked chờ QC (G9c, `qualityBucketId`) có vào các field `…PurchaseQc…` không? **Đề xuất: không** — tên field FE đặt là "Purchase"; phần hold đó rơi vào `unclassifiedQualityHoldQuantity`. Nếu PO muốn, tách thêm field `…ProductionQc…` ở phase sau.

### 5.2 Thay đổi — `GET /v1/inventory/balances/aggregate`, additive

| Field | Công thức | Null khi |
|---|---|---|
| `pendingPurchaseQcQuantity` | Σ(`received − accepted − rejected`) dòng phiếu nhận `POSTED`, `quality_held`, non-tracked, đúng (item, kho) | không dựng được nguồn |
| `rejectedPurchaseQcQuantity` | Σ `rejected_quantity` cùng nhóm | như trên |
| `unclassifiedQualityHoldQuantity` | `qualityHold − pending − rejected` (không clamp âm — âm nghĩa là lỗi dữ liệu, để lộ ra) | không đủ nguồn |
| `qualityBreakdownKnown` | `true` khi mọi hold của dòng giải thích được | luôn có giá trị |

- `qualityHoldQuantity` **giữ nguyên nghĩa** (gồm cả pending + rejected + hold khác).
- Một query tổng hợp theo trang (`C14`): lấy `(itemId, warehouseId)` của trang, group-by → map; không loop per dòng.
- Fixture FE (nhận 25): đạt 10/loại 5 ⇒ pending 10, rejected 5, available 10, qualityHold 15, OnHand 25. Đợt 2 đạt 10 ⇒ pending 0, rejected 5, available 20, qualityHold 5.
- Quyền `PERM_INVENTORY_READ` như cũ; **không** trả tiền; lọc theo scope kho/plant như aggregate hiện tại.

### 5.3 Test

- `*RepositoryIT` mới cho query tổng hợp (partial/repeated QC, nhiều phiếu/Item/kho/Plant, trang 20 dòng khi nguồn > 20 — số **không** phụ thuộc phân trang, legacy `quality_held=false`).
- Unit: đẳng thức `qualityHold = pending + rejected + unclassified` khi known; `unclassified` không clamp.
- **Mutation:** cộng rejected vào pending ⇒ đỏ; bỏ lọc `POSTED` ⇒ đỏ; bỏ lọc `quality_held` ⇒ đỏ.

### 5.4 Xong khi

`B266`; `docs/api-guide-for-frontend.md` thêm 4 field; **breaking: không** (additive).

---

## 6. CF-4 — Hiển thị nguồn mua đang chờ QC trong MRP

**Nguồn:** `FE_REQUEST_MRP_PENDING_QC_VISIBILITY`. **Ước lượng:** 2 ngày. **1 migration.**

### 6.1 Nguyên tắc cố định

**Không đổi netting.** Fixture: demand 40 / PO mở 15 / khả dụng 0 / chờ QC 25 ⇒ shortage vẫn 25; chỉ **hiển thị** 25 đang chờ. Không cộng pending vào supply hai lần.

### 6.2 Cổng [PO]

Có cần endpoint `GET /v1/planning-runs/{id}/pending-qc-supplies` riêng không, hay `sources[]` nhúng trong requirement đủ? **Đề xuất:** làm endpoint riêng (phân trang, vì một item có thể có nhiều phiếu), và chỉ trả *tổng + số nguồn* trên requirement.

### 6.3 Thay đổi

| # | Việc | Ghi chú |
|---|---|---|
| a | Migration: bảng `mrp_pending_qc_sources` (`run_id`, `requirement_line_id`, `goods_receipt_id`, `goods_receipt_line_id`, `item_id`, `warehouse_id`, `uom_code`, `pending_quantity NUMERIC(19,6)`, `receipt_version`, `receipt_code`); cột `pending_qc_quantity`, `pending_qc_source_count` nullable trên `mrp_requirement_lines`; CHECK `pending_quantity > 0` | **Không backfill:** run cũ = `NULL` = "chưa chụp" (không phải 0 — `B77`) |
| b | `CommercialPlanningRunService`/`MrpRunService`: sau khi tính, **chụp** nguồn bằng 1 query theo (item, kho) của các dòng nhu cầu — dùng lại query tổng hợp của CF-3 | Bất biến: QC sau run **không** viết lại snapshot (FE R05) |
| c | `MrpRequirementLineResponse` +2 component (giữ compat constructor — record này đã +11 ở G6b) | |
| d | Cảnh báo: `messages[]` của đề xuất `BUY` thêm mã `PENDING_QC_SUPPLY` khi `pendingQcQuantity > 0` (cơ chế `exceptionState`/`messages[]` từ F5-B). **Chỉ `WARNING`, không chặn convert** | Stale conversion giữ nguyên contract G6b |
| e | Endpoint đọc nguồn, `PERM_MRP_READ`, scope theo run; projection tối thiểu: **không giá, không liên hệ nhà cung cấp**, không dùng lại `GoodsReceiptResponse`; sắp theo `receipt_code, line_id` ổn định; page 0/size 20/max 100 | FE Q2: không đòi `PERM_GOODS_RECEIPT_READ` |

### 6.4 Test

- `*RepositoryIT`: snapshot nguồn đúng (item, kho, scope), loại `rejected`, nhiều trang; `NULL` cho run cũ.
- Unit: fixture 40/15/25 ⇒ shortage 25 không đổi + `pendingQcQuantity 25`; sau QC 20/5 và **run mới** ⇒ shortage 5, pending 0; **run cũ** vẫn giữ 25.
- Quyền: chỉ `PERM_MRP_READ` đọc được endpoint nguồn, không thấy giá.
- **Mutation:** cộng pending vào supply (đổi netting) ⇒ fixture 40/15/25 đỏ; chụp lại nguồn khi đọc (không từ snapshot) ⇒ ca "run cũ giữ 25" đỏ.

### 6.5 Xong khi

`B267`–`B268`; `docs/api-guide-for-frontend.md`; `docs/roles-and-permissions.md` **không đổi** (dùng `PERM_MRP_READ`); breaking: không.

---

## 7. CF-5 — Thống nhất công thức capacity giữa xem trước và release

**Nguồn:** `FE_REQUEST_CAPACITY_RELEASE_ALIGNMENT`. **Ước lượng:** 2–3 ngày. **1 migration.**

### 7.1 Cổng [PO]

Đồng ý **lịch WO release mới ngắn lại** khi `capacityUnits > 1`? WO đã release **không** bị dời (FE Q2 đã đồng ý). Đây là đảo `CLAUDE.md §0.53 #6` ("cố ý chưa đổi release").

### 7.2 Thay đổi

| # | Việc | Chỗ |
|---|---|---|
| a | `WorkOrderScheduler` chia `capacityUnits` **chụp lúc release**; **gom công thức về một nơi** (hiện có hai bản: `CapacityPreviewService.place` có chia, `WorkOrderScheduler` không — chính cái lệch đang bị than phiền) | `WorkOrderScheduler` (javadoc `:27-30` phải sửa), `CapacityPreviewService:129-135` |
| b | `POST /v1/work-orders/{W}/schedule-preview` (G8a) dùng cùng `WorkOrderScheduler` ⇒ tự khớp release; không cần sửa riêng | |
| c | Migration: `work_order_operations.captured_capacity_units INT`, `required_resource_minutes NUMERIC`, `active_elapsed_minutes NUMERIC` — nullable, **không backfill** (WO cũ = `null`); CHECK `captured_capacity_units > 0` | |
| d | `WorkOrderOperationResponse` +3 field; `predecessorOperationIds`: **kiểm trước** `RoutingOperationPredecessor` đã snapshot vào `WorkOrderOperation` chưa (chưa kiểm) — nếu chưa, đây là thêm bảng snapshot thứ hai ⇒ cân nhắc tách `CF-5b` | |
| e | Fingerprint nguồn lịch (G8a `scheduleRevision`/`calendarRevision`) **thêm `capacityUnits`** để stale-detection thấy trung tâm đổi công suất | |
| f | **Tải trên Capacity Board không đổi** (vẫn `required`, không chia) — FE fixture: tải vẫn 120 | `CapacityLoadCalculator`, `CapacityBoardService` |
| g | Release giữ **không body** | FE đồng ý |

### 7.3 Test

- Fixture FE: ca 08–17, nghỉ 12–13, cap 2, qty 10, run 12 ⇒ 09:00 (preview **và** release); cap 1 ⇒ 10:00; bắt đầu 11:30, cap 2, required 120 ⇒ 13:30 (vắt qua nghỉ).
- Preview ↔ release **cùng kết quả** khi cùng nguồn — test so hai đường với cùng fixture, không so với hằng số.
- WO đã release trước phase: `GET` vẫn trả lịch cũ, 3 field mới `null`; đổi `capacityUnits` của trung tâm sau đó **không** dời lịch.
- Sửa theo R10: `WorkOrderServiceTest.release_schedules*`, `WorkOrderSchedulePreviewServiceTest`, `ProductionFlowE2EIT` (có thể phụ thuộc lịch).
- **Mutation:** bỏ chia ⇒ fixture cap 2 đỏ; chia theo `capacityUnits` hiện tại thay vì chụp ⇒ ca "đổi công suất sau release" đỏ; Capacity Board cũng chia ⇒ ca "tải vẫn 120" đỏ.

### 7.4 Xong khi

`B269`–`B270`; breaking: lịch WO release mới đổi (shape không đổi); ghi vào `module/workcenter/CLAUDE.md` mục 4 (đang mô tả hành vi cũ).

---

## 8. CF-6 — Sổ giá trị tồn kho / WIP *(phase lớn — chỉ bắt đầu sau khi PO duyệt cổng)*

**Nguồn:** `FE_REQUEST_INVENTORY_VALUATION` R01–R08. **Ước lượng:** 2–3 tuần, chia 4 lát. **Nhiều migration. Có quyền mới. Có breaking.**

### 8.1 Cổng [PO] — **chặn toàn bộ phase**

| # | Câu hỏi | Đề xuất BE |
|---|---|---|
| Q1 | Làm định giá tồn kho không? (đã hoãn 5 lần: D7 các phase G9c/G10) | Làm, nhưng **sau** CF-2/CF-3 |
| Q2 | Quyền đọc tiền | Tách `PERM_INVENTORY_VALUE_READ` (ADMIN + MANAGER). Tái dùng `PERM_COSTING_READ` buộc mọi ai xem giá chuẩn cũng thấy giá tồn |
| Q3 | Rounding | VND, value `NUMERIC(19,6)` `HALF_UP`, bình quân nội bộ ≥ 12 chữ số, **phần dư dồn vào lần cuối** (mẫu `lastReturn_takesTheResidual` của G11) |
| Q4 | Count tăng không có nguồn giá | **Chặn** `VALUATION_UNAVAILABLE` (409), không tự gán 0 |
| Q5 | `unitCost` của receive | **Tuỳ chọn trước, bắt buộc sau** một mốc (cờ cấu hình) — bắt buộc ngay làm gãy seed demo, `GoodsReceiptPoster`, `ProductionReceiptService`, nhập Excel, mọi client |
| Q6 | Nguồn giá thành phẩm nhập kho | Chưa có đáp án tự nhiên (giá thành G11 chỉ chốt lúc đóng lệnh). Đề xuất: giá chuẩn `ItemStandardCost` lúc duyệt phiếu nhập, **điều chỉnh bằng bút toán giá trị** khi đóng lệnh (G11 đã có `production_cost_adjustments`) |
| Q7 | Gộp với `P7` (`MANUFACTURING_PHASE_PLAN_P7_P11.md`) | **Gộp** — hai phase cùng một sổ; làm hai lần là sai |

### 8.2 Chia lát (mỗi lát một commit, mỗi lát xanh độc lập)

| Lát | Nội dung | Wire |
|---|---|---|
| **CF-6a** Nền | Migration: `stock_balances.stock_value`, `value_known`; `stock_movements.unit_cost`, `movement_value`, `value_before`, `value_after`, `before_on_hand`, `after_on_hand` — nullable, **legacy = UNKNOWN** (không backfill). Quyền mới + seed (`docs/roles-and-permissions.md`). Quy tắc bình quân + residual thành **một hàm thuần** (kiểu `InvoicePricing`/`ProductionCostCalculator`) | Không wire |
| **CF-6b** Vào/ra | `receive` (+`unitCost`), nhận mua (giá PO), `issue`, `adjust`, `count`. Mã `VALUATION_UNAVAILABLE`. Snapshot trên movement lúc commit | `unitCost` tuỳ chọn; response movement thêm field (có quyền mới mới thấy) |
| **CF-6c** Chuyển & QC | `transfer` bảo toàn tổng value (fixture FE: `10333.333333` / `82666.666667`, nguồn+đích = 93000); QC đổi trạng thái **không** đổi value; rejected vẫn có value; lô con kế thừa bình quân lô cha | |
| **CF-6d** Đọc & WIP | `balances/aggregate` thêm `stockValue`/`averageCost`/`valueState`/`currency` (totals thuộc toàn scope, không cộng riêng một trang); `movements/{id}` thêm snapshot; WIP/hoàn vật tư: consumed + returned + unused = issued, phần cuối nhận residual, **không viết lại dòng cũ** (FE R07) | Quyền đọc tiền: thiếu quyền ⇒ **field vắng mặt** (không `null`) — mẫu `commercial` của G10a (`@JsonInclude(NON_NULL)` trên component, test kiểm JSON thô) |

### 8.3 Bất biến phải có ngay từ CF-6a

- Mỗi đường ghi: số lượng, value, movement, lô **cùng commit hoặc không đổi gì** (một transaction).
- Giá trên movement là **snapshot lúc post**, không tính lại khi đọc.
- Tổng value trước/sau transfer bằng nhau; QC bằng nhau.
- Legacy thiếu opening ⇒ tổng `UNKNOWN`; **không** lấy riêng lần nhập mới làm tổng cả kho (fixture FE: opening unknown + nhập 20×1200 ⇒ tổng vẫn unknown, không phải 24000).
- Replay kiểm quyền hiện hành **trước** (kể cả quyền đọc tiền bị thu hồi); `unitCost` vào payload hash — chứng minh fingerprint cũ vẫn khớp khi `unitCost` vắng.

### 8.4 Test (tối thiểu)

- Hàm thuần: fixture FE end-to-end (100 KG/100000 + nhập 20×1200 ⇒ 120/124000; xuất 30 ⇒ 31000, còn 90/93000; transfer 10); 1 VND/3 đơn vị ⇒ `0.333333 + 0.333333 + 0.333334`.
- `*RepositoryIT` cho aggregate (tổng độc lập phân trang), `FlywayMigrationIT` (migrate từ DB có tồn legacy: toàn bộ `UNKNOWN`, không dòng nào thành 0).
- E2E: `ProductionFlowE2EIT` đi hết nhận → cấp → báo → nhập → QC, kiểm value ở mỗi bước.
- Quyền: tiền vắng mặt ở mọi response khi chỉ có `PERM_INVENTORY_READ`.
- **Mutation:** snapshot tính lại lúc đọc; residual không dồn lần cuối; QC làm đổi value; transfer làm mất 1 phần triệu; count tăng không giá không bị chặn; bỏ kiểm quyền tiền. Mỗi cái ≥ 1 case đỏ.

### 8.5 Rủi ro

- Bán kính ảnh hưởng lớn nhất: chạm gần như mọi file trong `inventory`, `purchasing`, `workorder/service/execution`, nhập Excel, seed demo (`scripts/demo-capstone2` cần `unitCost` khi bắt buộc).
- Phase có thể kéo dài: nếu quá 3 tuần, dừng sau CF-6b và để FE nghiệm thu phần vào/ra trước.
- Không GL/thuế/FX/giá bán (FE đã loại khỏi phạm vi); không hồi tố giá cũ; không thay hoá đơn/thanh toán.

---

## 9. CF-7 — Nguồn chính xác BOM theo lô *(chỉ mở nếu PO chọn mở rộng)*

**Nguồn:** `FE_QUESTION_BOM_BATCH_RATIO` D02/D03. **Ước lượng:** chưa xác định — **bước 1 là đo tác động**, chưa phải code.

### 9.1 Cổng [PO]

| Phương án | Hệ quả |
|---|---|
| **A. Giữ giới hạn scale 6** (không hỗ trợ 1/3) | Không có phase. Chỉ cần CF-0.3 ghi "PO duyệt". Người dùng nhập BOM 1 KG → 3 PCS phải đổi sang đơn vị khác |
| **B. Lưu `componentQuantity` + `outputQuantity` làm nguồn, per-unit là projection** (FE nghiêng về) | Đảo nguồn chính xác (`G5` D1). Đụng nổ BOM MRP, snapshot WO (`B12`), giá thành `P3`/`G11`, copy/version, nhập Excel. Chính là lý do nợ `E` (F10) từng bị loại |

### 9.2 Nếu chọn B — bước đầu (đo, không code)

1. Liệt kê mọi chỗ đọc `quantity_per`: `grep -rn "getQuantityPer\|quantityPer"` — lập bảng consumer.
2. Với mỗi consumer, ghi: cần số *tại biên thao tác* (rounding ở nơi tiêu thụ) hay *per-unit chính xác*.
3. Đo A/B trên các BOM `D26-` (tất cả per-unit biểu diễn được ⇒ kết quả phải **byte-identical** — đây là test chấp nhận chính, vì mọi dữ liệu hiện có không đổi).
4. Chỉ khi 3 bước trên xong mới viết kế hoạch chi tiết `CF-7` thành phase, kèm quyết định rounding (làm tròn lên theo biên, như `ceil6` của G6b).

### 9.3 Test chấp nhận nếu chọn B

1/3 hoặc lưu đúng theo lô và MRP `6 → 2` KG; hoặc 400 rõ field + 0 dữ liệu đổi. Không bao giờ `200` rồi mở lại thành `0.999999/3`. Form và `kind=bom` cùng quyết định. Scrap `5.1234%` ⇒ `0.051234`; `5.12345%` ⇒ lỗi, không cắt âm thầm.

---

## 10. Bảng theo dõi

| Phase | Trạng thái | Migration | Bất biến | Breaking wire | Ghi chú |
|---|---|---|---|---|---|
| CF-0 | ✅ xong (2026-10-28) | — | `JsonWireFormatTest` +5 ca | không | 4 file trả lời + OpenAPI ở thư mục gốc; phát hiện nhóm field không có `@Digits` (ghi trong file numeric §2.3) |
| CF-1 | ✅ `21c162c` (2026-10-27) | — | `B263` | hẹp (`adjust` lô nguồn QC 200→409) | 2151 + 317 |
| CF-2 | ✅ `2c6fc95` (2026-10-27) | — | `B264`–`B265` | **có** (MRP đổi số; reserve/issue lô hết hạn 409) | PO duyệt 2026-10-27; A/B số MRP chưa đo (gộp smoke cuối) |
| CF-3 | ✅ `335f7f6` (2026-10-27) | — | `B266` | không | PO: phiếu nhập SX → `unclassified` |
| CF-4 | ✅ `1c75b7a` (2026-10-28) | `V93` | `B267`–`B268` | hẹp (đề xuất mua `READY` → `WARNING`) | PO: endpoint riêng; 2193 + 331 |
| CF-5 | ✅ (2026-10-28) | `V94` | `B269`–`B270` | **có** (lịch WO release mới) | PO duyệt 2026-10-27; 2219 + 332 |
| CF-6 | ⏸ hoãn (PO 2026-10-27) | nhiều | `B271`+ | **có** | gộp `P7` |
| CF-7 | ✅ không mở — PO chọn **A** (2026-10-27) | — | — | — | ghi ở CF-0.3 |
| CF-8 | ✅ (2026-10-30) | — | `B274` (lỗi do smoke tìm ra) | không | 7/7 smoke PASS; `BE_RESPONSE_CF_8_2026-10-29.md`; build-info bật (8.5) |
| CF-9 | ✅ (2026-10-29) | `V95` | `B271` | không (đua khác hoa thường ⇒ 409) | DB dev 0 cặp trùng; 2317 + 336 |
| CF-10 | ✅ (2026-10-29) | — | `B272` | **có, hẹp** (200/422 → 400) | PO chấp nhận 2026-10-29; 20 field / 14 DTO + guard |
| CF-11 | ✅ (2026-10-29) | — | `B273` | không | PO chọn A; IT chuỗi chưa viết |
| CF-12 | ✅ (2026-10-29) chỉ test | — | — (không sửa) | không | `@Version` chặn đua QC ↔ đếm; 2317 + 345 |
| CF-13 | ✅ (2026-10-29) chỉ test (A39/A40) | — | — | không | smoke giá thành gộp CF-8 |

*Số `B<n>` là dự kiến, đánh số tiếp từ `B263` (bất biến cuối ghi trong `CLAUDE.md` là `B262`); đổi nếu phase khác chen vào.*

---

## 11. Câu hỏi [PO] gom lại

1. **CF-2** — chấp nhận MRP đổi số khi có lô hết hạn, và ghi đảo quyết định 2026-10-15?
2. **CF-3** — phiếu nhập SX có vào `…PurchaseQc…` không? (đề xuất: không)
3. **CF-4** — endpoint nguồn riêng hay nhúng? (đề xuất: riêng)
4. **CF-5** — lịch WO release mới ngắn lại khi `capacityUnits > 1`?
5. **CF-6** — Q1–Q7 ở §8.1; riêng Q1 (làm hay không) và Q7 (gộp `P7`) là cổng đầu tiên.
6. **CF-7** — phương án A hay B?

Trả lời của PO ghi trực tiếp vào bảng §10 cột "Ghi chú" và vào `CLAUDE.md §0` khi phase bắt đầu.

---

## 12. Đợt bổ sung 28/10/2026 — `CAPSTONE3_BE_COMPLETION_2026-10-28`

**Lập ngày:** 2026-10-29 · **Nền:** commit `b439ed8`, migration mới nhất `V94`, baseline **2219 unit + 332 IT** (số của CF-5; CF-0 chỉ thêm 5 ca `JsonWireFormatTest`, đo lại trên worktree sạch trước khi sửa).
**Nguồn:** `CAPSTONE3_BE_COMPLETION_PACKAGE_2026-10-28.md` + 8 file FE (5 `FE_REQUEST`, 3 `FE_QUESTION`). FE chưa chạy BE — mọi tiêu chí là **đề xuất của FE**, không phải lỗi quan sát được.

### 12.1 Ba quyết định PO trực tiếp 28/10 (đã chốt, không hỏi lại)

1. Giá thành theo **giá chuẩn vật tư chụp lúc RELEASE**; tiếp tục hoãn sổ giá trị tồn/WIP (`CF-6`), không gọi GĐ11 là moving-average.
2. BOM per-unit **giữ 6 số lẻ**, chưa hỗ trợ 1/3 (= phương án A của `CF-7`, đã làm ở `CF-0.3`).
3. Bù hao hụt = **lượng cơ bản × (1 + rate)**, cùng nghĩa BOM / MRP / WO; baseline kế thừa `requiredQuantity`, không nhân lần hai.

FE nói rõ **không** mở rộng: MRP future-WO theo actual, truy vết/COGS hàng non-tracked sau đóng lệnh, đóng lệnh không routing, source-overlap, `CF-6`. Không đưa các mục này vào phase nào dưới đây — chỉ nêu lại trong tài liệu trả lời như "giới hạn đã biết".

### 12.2 Bản đồ 8 file FE → phase

| # | File FE | Loại / mức | Việc thật sự còn lại | Phase |
|---|---|---|---|---|
| 1 | `FE_QUESTION_STANDARD_COST_BASELINE` | Q / P2 | Chỉ trả lời. Cần DTO `standard-cost` + 1 phép đo "đổi giá master sau RELEASE không đổi baseline" | **CF-8** |
| 2 | `FE_QUESTION_BOM_SCALE6_SCOPE` | Q / P2 | Chỉ trả lời. Bù 2 phép đo còn thiếu: `5.12345%` qua HTTP, và `10 → 25` ra `2.5` | **CF-8** |
| 3 | `FE_QUESTION_GD06_GD07_CONTRACT v2` | Q / P1 | 11 file **đã soạn ở CF-0** (xem 12.3). Còn thiếu: mốc binary ngoài migration + phân loại bằng chứng IT/HTTP | **CF-8** |
| 4 | `FE_REQUEST_LOT_IDENTITY_CONCURRENCY` | R / P1 | **Lỗi thật**: unique index của lô phân biệt hoa thường | **CF-9** |
| 5 | `FE_REQUEST_NUMERIC_VALIDATION_COMPLETION` | R / P1 | ~14 DTO còn nhận 7 số lẻ rồi DB làm tròn | **CF-10** |
| 6 | `FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT` | R / P1 | Công thức đã đúng ở mọi đường; còn lệch **chiều làm tròn** + thiếu test chuỗi | **CF-11** |
| 7 | `FE_REQUEST_PURCHASE_QUARANTINE_GUARDS v2` | R / P1 | Phần lớn đã có (CF-1, G4); thiếu ma trận phủ + test đua QC ↔ count/adjust | **CF-12** |
| 8 | `FE_REQUEST_COST_CLOSE_VERIFICATION` | R / P1 | Chưa có test A39/A40 và chưa có smoke chốt giá thành | **CF-13** |

### 12.3 Việc phải làm ngay, không cần code: gửi FE các file đã soạn

FE liệt kê đúng 11 file chưa nhận — **tất cả đã được soạn ngày 28/10** ở thư mục gốc repo (`BE_RESPONSE_VERSION_MAP`, `_NUMERIC_CONTRACT`, `_BOM_RATIO`, `_CF_1`…`_CF_5`, `BE_OPENAPI_2026-10-28.json`, cộng `BE_RESPONSE_GD_06_2026-10-17.md` và `_v2.md`) + `BE_REPORT_CF_COMPLETED_2026-10-28.md`. FE xác nhận đã archive 15 file nhận trong ngày nhưng chưa thấy các file này ⇒ **chưa chắc đã gửi**. Trước khi làm `CF-8`: xác nhận với user các file đó đã tới FE chưa; nếu chưa thì gửi theo tin nhắn 4 dòng đã soạn.

> ⚠️ `BE_OPENAPI_2026-10-28.json` sẽ **lỗi thời** sau `CF-9`/`CF-10` (ràng buộc `@Digits` mới xuất hiện trong schema). Xuất lại **một lần ở cuối** (sau `CF-13`) và ghi SHA-256; đừng xuất lại sau từng phase.

### 12.4 Thứ tự và lý do

`CF-8` ∥ `CF-9` → `CF-10` → `CF-11` → `CF-12` → `CF-13`.

- `CF-8` không đụng code, mở khoá FE ngay (nhiều mục "CHẶN adapter").
- `CF-9` là **lỗi toàn vẹn dữ liệu thật** (hai lô chỉ khác hoa thường cùng tồn tại) — ưu tiên code cao nhất, và càng để lâu càng dễ có dữ liệu trùng sẵn.
- `CF-10` trước `CF-11`: ma trận số của `CF-10` cũng là bằng chứng phủ cho trường `scrapRate`/`quantityPer`.
- `CF-12`, `CF-13` chủ yếu là test; làm cuối vì có thể không phải sửa code nào.

Quy tắc chung §1 áp dụng nguyên (worktree sạch đo baseline, JPQL ⇒ IT, mutation, smoke HTTP, `B<n>` tiếp từ **`B271`**, commit `feat(<module>): CF-n — …`, **không push**). Số `B271`+ trước đây dự kiến cho `CF-6` — nếu `CF-6` mở lại thì đánh số tiếp, không đổi số đã cấp.

---

### 12.5 CF-8 — Trả lời tài liệu: giá chuẩn · BOM scale 6 · contract & nhận diện bản

**Ước lượng:** nửa ngày–1 ngày. **Không migration.** Đầu ra: **một** file `BE_RESPONSE_CF_8_<ngày>.md` theo `BETaskDeliveryRules.md` (tự chứa, §0–§8) trả đủ Q1–Q3 của cả 3 file QUESTION.

| # | Nội dung | Cần đo / lấy từ đâu |
|---|---|---|
| 8.1 | **Giá chuẩn (file 1).** Q1: baseline đọc `requiredQuantity` × `ItemStandardCost` chụp lúc RELEASE (`ProductionCostBaselineService:89-92`) ⇒ **Có**. Q2: DTO `PUT/GET /api/v1/companies/{C}/items/{I}/standard-cost` (field, scale, null, quyền, replay) lấy từ OpenAPI đã xuất + `ItemStandardCostRequest`. Q3: `CF-6` hoãn ⇒ **Có**. D03: nêu `/variance` (P3, theo vật tư) khác `/production-costs` (G11) | **Đo:** đặt giá RM 1.000 → tạo + release lệnh (plan 20 KG) → đổi giá master 1.200 → đọc `cost-baseline`: vẫn 20.000; tiêu thụ 21 ⇒ thực tế 21.000 (không 25.200); lệnh release **sau** đó lấy 1.200. Nếu `ProductionFlowE2EIT` chưa có ca "đổi **giá chuẩn vật tư**" (chỉ có ca đổi **đơn giá giờ**), thêm một IT |
| 8.2 | **BOM scale 6 (file 2).** D01/D02 đã đo ở CF-0.3. D03 `10 → 25` ⇒ `quantityPer 2.5` đọc lại đúng 25; Q2 dữ liệu/snapshot cũ không đổi (giới hạn chỉ áp lần ghi mới); Q3 file numeric + BOM-ratio đã soạn | **Đo thêm qua HTTP:** (a) `10 → 25` lưu rồi `GET` ra 25 và 2.5; (b) `scrapRate` `5.1234%` ⇒ 201 `0.051234`, `5.12345%` ⇒ lỗi (CF-0 ghi "chưa đo HTTP") — cả form lẫn `kind=bom` |
| 8.3 | **Nhận diện bản (file 3, D02).** Hiện `/actuator/info` trả `{}`. Cung cấp: **SHA-256 của jar** đang chạy + `git rev-parse HEAD` + 4 dấu hiệu hành vi (đã có trong smoke CF: `adjust` lô QC → 409, giữ chỗ lô hết hạn → 409, đề xuất mua có chờ QC → `WARNING`, `capacityUnits=2` preview = release) | Tính khi build jar cuối cùng |
| 8.4 | **Phân loại bằng chứng (file 3, D03/Q3).** Bảng mỗi hành vi CF-1…CF-5: *IT* / *unit* / *HTTP smoke* / *chưa đo* — để FE không nhầm loại bằng chứng | Lấy từ `CLAUDE.md §0.72` (CF-1…5 đã ghi nghiệm thu từng phase) |

**[PO] nhẹ — 8.5 (tuỳ chọn, ~5 dòng):** để mốc binary tự đọc được thay vì tính tay mỗi lần, bật `spring-boot-maven-plugin:build-info` (đã có plugin, chạy offline được) + `management.info.build.enabled` và `info.app.commit: ${APP_COMMIT:unknown}` đặt lúc khởi động. Ảnh hưởng: `/actuator/info` hết rỗng. **Đề xuất: làm**, vì `max(flyway version)` không phân biệt được `CF-1/2/3` (không migration). Không làm nếu PO không muốn đổi `application.yml`; khi đó chỉ giao SHA-256.

**Không làm:** không sửa code nghiệp vụ; không mở `CF-6`/`CF-7`.
**Xong khi:** file trả lời có ví dụ copy từ lần gọi thật; nếu 8.1 thêm IT thì xanh; `CLAUDE.md §0.72` một dòng.

---

### 12.6 CF-9 — Mã lô không trùng khác hoa thường khi nhận đồng thời

**Ước lượng:** 1–1.5 ngày. **1 migration (`V95`).** Nguồn: `FE_REQUEST_LOT_IDENTITY_CONCURRENCY`.

**Hiện trạng đã kiểm trong code:**
- DB chỉ có `uk_inventory_lots_item_code UNIQUE (item_id, lot_code)` (`V8`) — **phân biệt hoa thường**.
- Mọi đường tạo lô kiểm "không phân biệt hoa thường" **ở tầng ứng dụng** rồi mới insert: `GoodsReceiptPoster:111`, `ProductionReceiptService:725/1288`, `PurchaseQualityService:226`, `InventoryMovementService:237/688`. Hai request đồng thời `LOT-X` / `lot-x` cùng qua bước kiểm, cùng insert, **DB cho cả hai** ⇒ hai lô chỉ khác hoa thường tồn tại (khoá `lockOrder` của G7b chỉ theo từng PO nên không chặn được hai PO khác nhau).
- Hai mã **trùng đúng chữ** đã được DB chặn và `DataIntegrityErrorMapper` đã đổi thành `409 LOT_CODE_ALREADY_EXISTS` (không phải 500) ⇒ FE Q2 một nửa đã đạt; nửa còn lại là nhánh khác hoa thường.

**Việc làm:**

| # | Việc | Ghi chú |
|---|---|---|
| a | **Đo trước:** truy vấn DB dev (và nêu cho PO câu truy vấn để chạy ở môi trường thật) `select item_id, lower(lot_code), count(*) … group by … having count(*) > 1` | R04: nếu có trùng sẵn ⇒ **dừng**, báo danh sách/số lượng, **không** tự gộp/xoá lịch sử (cổng [PO]) |
| b | `V95`: `CREATE UNIQUE INDEX uk_inventory_lots_item_code_ci ON inventory_lots (item_id, lower(lot_code))`. **Giữ** constraint cũ (không sửa migration cũ, và bỏ nó không thêm bảo vệ) | Mã lô được `trim` ở service trước khi lưu — kiểm đường nào lưu không trim thì sửa hoặc ghi vào index (`lower(btrim(lot_code))`) |
| c | `DataIntegrityErrorMapper.KNOWN` thêm tên index mới ⇒ `LOT_CODE_ALREADY_EXISTS` (cùng thông điệp với hai entry hiện có) | Tên constraint **không** ra response (EH-3) |
| d | Kiểm kẻ thua đua đi qua `DataIntegrityViolationException` **ở commit** (ngoài `try` của service) vẫn ra 409 và rollback sạch: phiếu, tồn, movement, lô, PO received, audit, replay-success | Bài học `G3`: va chạm ở dòng cuối lọt ra lúc commit |

**Test (`*IT`, bắt buộc — mock không thấy):**
- Class IT mới theo khuôn `ItemWarehouseSettingConcurrencyIT` (`NOT_SUPPORTED` + `TransactionTemplate`, nhiều vòng): hai PO khác nhau cùng Item, post đồng thời `LOT-X` và `lot-x` ⇒ đúng 1 thắng, 1 `409 LOT_CODE_ALREADY_EXISTS`; đúng 1 lô mới; chỉ phiếu thắng tăng tồn + `received` của PO; phiếu thua vẫn `DRAFT`, sửa mã rồi post lại được.
- Cùng ca cho `inventory/receive` ↔ GR post và `production receipt approve` ↔ GR post (đường nào cũng đi qua cùng index).
- Nhiều dòng: dòng sau trùng ⇒ rollback cả phiếu.
- Cùng Item khác công ty / **khác Item cùng mã** vẫn được dùng lại (không mở unique toàn cục — FE Q3).
- `FlywayMigrationIT`: migrate từ DB có hai lô khác hoa thường ⇒ nêu rõ hành vi (migration thất bại có thông báo) — quyết định ở cổng [PO].
- **Mutation:** bỏ `V95` index ⇒ IT đua đỏ; bỏ entry mapper ⇒ ca đua trả mã sai/500; đổi `lower` thành so sánh phân biệt hoa thường ⇒ đỏ.

**Wire:** không đổi shape. Hành vi: đua khác hoa thường từ "cả hai thành công" thành `409`. `docs/api-guide-for-frontend.md` thêm một dòng. Bất biến **`B271`** (`module/inventory/CLAUDE.md`).

---

### 12.7 CF-10 — Hoàn thiện validation số trước khi DB làm tròn

**Ước lượng:** 2 ngày. **Không migration.** Nguồn: `FE_REQUEST_NUMERIC_VALIDATION_COMPLETION`. Đây là **chặn mới** (breaking hẹp).

**Hiện trạng:** CF-0 đã đo — field `BigDecimal` không có `@Digits` nhận `1.1234567` ⇒ 200, response lặp lại số chưa làm tròn, DB lưu `1.123457`; quá 13 chữ số nguyên ⇒ `422 BUSINESS_RULE_VIOLATION` không có `errors[]`. Quét sơ bộ trong `dto/**Request` (danh sách ứng viên, **phải xác nhận lại bằng quét đầy đủ**): `ItemStandardCostRequest` (3 giá), `ItemWarehouseSettingRequest` (safety/reorder), `PlanningDemandCreateRequest.requiredQuantity`, request ước lượng sản xuất (`targetQuantity`), `ItemSupplierRequest` (MOQ, giá), `PurchaseOrderLineRequest` (đường tạo cũ), `PurchaseRequisitionLineRequest` + body duyệt (`approvedQuantity`), `RoutingOperationRequest` + dòng routing nháp (`setupMinutes`, `runMinutesPerUnit`), `SalesOrderLineRequest` (đường cũ), `MaterialIssueFlatRequest`, `WipTransactionRequest`, `WorkOrderComponentIssueRequest`, `WorkOrderOutputCompletionRequest`, `WorkOrderCreateRequest.plannedQuantity`. Đã chặn từ trước: số lượng kho (G4), phiếu nhận, đơn mua nháp, đơn bán nháp, BOM, lệnh SX mới, báo sản lượng, phiếu nhập, tiền hoá đơn/thu tiền.

**Việc làm:**

| # | Việc |
|---|---|
| a | **Ma trận R01** (artifact, gửi FE): quét tự động mọi record trong `**/dto/**` có thành phần `BigDecimal`; với mỗi cái ghi method/path, `@Digits`, kiểu cột DB tương ứng (đọc migration, **không viết từ trí nhớ**), nullable. Tách nhóm `NUMERIC(19,6)` / `NUMERIC(9,6)` (`scrapRate`) / `NUMERIC(19,0)` (tiền đồng) / `NUMERIC(25,12)` (nội bộ, **không** đụng) / số nguyên |
| b | Thêm `@Digits(integer = 13, fraction = 6)` (hoặc đúng cột) vào các field còn thiếu. `integer` suy từ `precision − scale` của **cột thật**, không áp 13/6 mù quáng — minutes và đơn giá có thể khác |
| c | Import Excel (`kind=item/bom/so`, mapped-profile) ghi cùng cột: kiểm từng handler có chặn trước khi ghi không (lỗi theo dòng, `INVALID_FORMAT`) |
| d | **Không** đổi kiểu JSON, không hạ precision, không sửa dữ liệu đã làm tròn |

**Test:**
- **Guard hoàn chỉnh (chống trôi):** một test reflection duyệt mọi record request, **fail nếu** có thành phần `BigDecimal` thiếu `@Digits` mà không nằm trong danh sách ngoại lệ có chú thích lý do. Đây là câu trả lời cho FE Q2 ("không tuyên bố toàn API đã strict khi còn alias") và ngăn DTO mới quên.
- Test dạng bảng (validator, không HTTP): mỗi field × {`1.2345678`, `1.2345670`, 14 chữ số nguyên, biên hợp lệ `9999999999999.999999`, `0`, âm khi không cho phép} ⇒ vi phạm đúng đường dẫn thuộc tính (kể cả lồng `lines[2].quantity`).
- Number và string trong JSON cho cùng một field ⇒ cùng kết quả (FE Q3). Bẫy đã biết: `@Digits` đếm cả số 0 thừa (`1.2345670` bị từ chối) — đúng ý FE.
- Controller test cho vài đại diện: `400 VALIDATION_ERROR` + `errors[{field}]`, và **dòng cuối invalid không ghi gì** (R04).
- Scripts seed (`scripts/demo*`) chỉ dùng ≤ 6 số lẻ — rà trước khi coi là không vỡ.
- **Mutation:** bỏ `@Digits` ở một field ⇒ test dạng bảng đỏ; thêm field không `@Digits` vào DTO ⇒ test guard đỏ.

**Wire:** **breaking hẹp** — input >6 số lẻ / >13 chữ số nguyên trên các field trên từ `200`/`422` thành `400`. Gửi FE bảng "trước/sau". Bất biến **`B272`** (đặt ở `CLAUDE.md` gốc mục API vì xuyên module).

---

### 12.8 CF-11 — Một công thức bù hao hụt giữa BOM, MRP, WO và giá thành

**Ước lượng:** 1–1.5 ngày. **Không migration.** Nguồn: `FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT`.

**Hiện trạng đã kiểm trong code — công thức *đã đúng* ở mọi đường, FE chỉ chưa có bằng chứng:**

| Đường | Công thức | Làm tròn |
|---|---|---|
| MRP nổ linh kiện (`MrpCalculationService:257`) | `net cha × quantityPer × (1+scrapRate)` | **`CEILING` 6** (Q28/B179, quyết định user) |
| Lệnh SX (`WorkOrderService:621,628`) | `planned × quantityPer × (1+scrapRate)` | không làm tròn trong Java ⇒ cột `NUMERIC(19,6)` **HALF_UP** lúc ghi |
| Ước lượng sản xuất (`PlanningService:89`) | `… × (1+scrapRate)` | không làm tròn |
| Giá chuẩn BOM (`CostingService:60`) | `giá con × quantityPer × (1+scrapRate)` | không làm tròn |
| Baseline giá thành (`ProductionCostBaselineService:89-92`) | `line.getRequiredQuantity() × giá chuẩn` | **kế thừa** số đã lưu ⇒ **không** nhân hao hụt lần hai (R03 đã đạt) |

Không có chỗ nào chia `(1−rate)`. BOM theo lô: `quantityPer` đã là per-unit (`outputQuantity` chỉ để form đọc lại) ⇒ không chia thêm output (R02 đạt).

**Chỗ còn lệch thật:**
1. **Chiều làm tròn khác nhau:** MRP `CEILING` vs lệnh SX `HALF_UP` ⇒ cùng một dòng có thể lệch tối đa **1e-6** (ví dụ cơ sở `1.0000011`: MRP `1.000002`, lệnh SX `1.000001`). MRP chỉ lập kế hoạch **dư** chứ không thiếu — đúng ý B179 — nhưng FE muốn "một công thức" và ghi rõ cần BE xác nhận.
2. Response *tạo* lệnh SX in `requiredQuantity` chưa làm tròn (12 chữ số), đọc lại mới ra 6 (đã ghi ở `§0.60 #6`).
3. **Chưa có test chuỗi** BOM → MRP → MAKE/WO thủ công → release → baseline với `scrapRate > 0` (R01/R03/Q3).

**[PO] — cổng:** quy tắc làm tròn của dòng lệnh SX.
- **A (đề xuất):** giữ MRP `CEILING6` (đã là quyết định user) và lệnh SX `HALF_UP6`; **đặt `setScale(6, HALF_UP)` tường minh** trong `requiredQuantity`/`recalculateRequirements` để response lúc tạo = số lưu = số đọc lại; tài liệu hoá đúng chênh ≤ 1e-6 theo từng đường. Không đổi dữ liệu nào, không đổi số của lệnh đã có.
- **B:** đổi lệnh SX sang `CEILING6` để khớp MRP tuyệt đối. Đổi số yêu cầu của **lệnh SX mới** (≤ 1e-6), và đảo ý "HALF_UP" mà GĐ8 Q25 đã nói với FE.
- FE đề nghị "HALF_UP6 từng dòng" cho mọi đường — **không khả thi nếu giữ B179** (MRP sẽ có thể thiếu 1e-6 so với lệnh SX sinh ra). Nêu rõ điều này với FE.

**Việc làm (theo A):** (a) `setScale` tường minh ở 2 chỗ lệnh SX; (b) kiểm `PlanningService.estimate` có cần làm tròn để khớp MRP không — nếu chỉ là hiển thị thì ghi rõ "không làm tròn", không sửa; (c) thêm test dưới; (d) tài liệu `BE_RESPONSE_CF_11`: một bảng công thức + quy tắc làm tròn từng đường + bảng đối chiếu fixture FE.

**Test:**
- Bảng công thức thuần: `10 PCS` (BOM 20 KG / 10 PCS = per-unit 2.0, rate 0.1) ⇒ cần **22**; `5 PCS` ⇒ **11**; `rate 0` ⇒ **20**; hai dòng cùng vật tư, cơ sở `1.000001`, rate `0.5` ⇒ mỗi dòng `1.500002`, tổng `3.000004`; một ca nửa-đơn-vị (`…0000015`) phân biệt `HALF_UP` với `CEILING` ở lệnh SX.
- **IT chuỗi:** BOM(per-unit 2.0, rate 0.1) → run MRP thương mại (không tồn, không an toàn) cho gross **22** → chuyển MAKE → lệnh SX `required 22` → giá chuẩn 1.000 → release → `cost-baseline planned = 22.000` (**không** 24.200). Đổi `scrapRate` trong BOM sau đó ⇒ run và lệnh đã có **giữ số cũ** (R04).
- Đường nhập Excel BOM và form cho cùng rate ⇒ cùng giá trị lưu (`5.1234% → 0.051234`).
- **Mutation:** baseline nhân `(1+rate)` lần hai ⇒ IT chuỗi đỏ; lệnh SX chia `(1−rate)` ⇒ bảng đỏ; bỏ `setScale` tường minh ⇒ ca "response = read-back" đỏ.

**Wire:** không đổi (chỉ response *tạo* lệnh SX hết dư chữ số). Bất biến **`B273`** (`module/workorder/CLAUDE.md`, ghi cả chênh làm tròn có chủ đích).

---

### 12.9 CF-12 — Khép phần bảo vệ QC còn thiếu sau CF-1

**Ước lượng:** 1–1.5 ngày. **Không migration.** Nguồn: `FE_REQUEST_PURCHASE_QUARANTINE_GUARDS v2`. FE nói rõ: không báo lại tracked adjust (CF-1 đã chặn) và **không** khẳng định non-tracked bị bypass — họ xin ma trận phủ + test đua.

**Hiện trạng đã kiểm:**
- Tracked: `adjust` (CF-1, `B263`), `count` qua `canIssue` (G4), đổi trạng thái / giữ chỗ / xuất / chuyển kho chỉ nhận lô `AVAILABLE` (G7c/G4/CF-2), huỷ phiếu đã QC bị chặn (G7c).
- Non-tracked: `count` có chặn `counted < reserved + qualityHold` ⇒ `STATE_CONFLICT` (`InventoryMovementService:585`); `adjust` âm bị giới hạn bởi tồn khả dụng (đã trừ `qualityHold`) — **cần xác nhận bằng test**, hiện chưa có test đặt tên rõ.
- Phần *hold* trên `stock_balances.quality_hold_quantity` có CHECK `reserved + qualityHold ≤ quantity` (V56) nên vi phạm tồi tệ nhất là lỗi ràng buộc, không phải dữ liệu sai âm thầm.

**Việc làm:**

| # | Việc |
|---|---|
| a | **Ma trận phủ (R01):** hàng = adjust / count / transfer / reserve / issue / đổi trạng thái / huỷ phiếu nhận; cột = tracked, non-tracked; ô = đã có / thiếu + **tên test** thật. Điền từ code + test hiện có, không từ trí nhớ |
| b | **Test non-tracked (R02/R03):** nhận 25, đạt 20 / loại 5, không giữ chỗ ⇒ `count` tổng 4 ⇒ `409`; tổng 24 (chỉ giảm phần free 20→19, giữ nguyên 5) ⇒ hợp lệ; `adjust −21` (vượt free) ⇒ `409`; `adjust −1` ⇒ hợp lệ; hold legacy không có nguồn ⇒ **không** coi là 0 (`qualityBreakdownKnown=false`, rule dùng chính cột hold) |
| c | **Đua QC ↔ count/adjust (R04, Q3):** IT hai thread — `PurchaseQualityService` quyết định đang giữ khoá phiếu; đồng thời `count`/`adjust` cùng vị trí tồn. Kiểm `releaseQualityHold` (`InventoryMovementService:119`) và đường QC có `lockPosition…` **trước khi** đổi `qualityHold` không. Nếu không ⇒ **sửa**: lấy khoá vị trí (thứ tự khoá `phiếu → vị trí`, nhất quán với `lockPositions` đã có) |
| d | Mã lỗi: giữ `STATE_CONFLICT` cho vi phạm owner/protected; ghi bảng tương đương nếu non-tracked đang trả `INSUFFICIENT_AVAILABLE_STOCK` (cùng 409). FE chấp nhận cả hai nếu có mapping rõ |
| e | Hồi quy: guard CF-1 + non-owner `HOLD` thủ công (không gắn phiếu) vẫn đổi được |

**Mutation:** bỏ guard `counted < reserved + hold` ⇒ ca count 4 đỏ; bỏ khoá vị trí ở đường QC ⇒ IT đua đỏ **hoặc** chứng minh không tái hiện được (khi đó ghi lại kèm lý do và dừng, không sửa cho có thay đổi — đúng tinh thần "chỉ sửa phần còn thiếu" của FE).

**Wire:** không đổi dự kiến. Nếu (c) phải sửa ⇒ ghi bất biến **`B274`**.

---

### 12.10 CF-13 — Test A39/A40 và smoke chốt giá thành

**Ước lượng:** ~2 ngày. **Không migration.** Nguồn: `FE_REQUEST_COST_CLOSE_VERIFICATION`. Code chỉ sửa **nếu test phát hiện lỗi**.

**Hiện trạng:** `ProductionCostCloseServiceTest` (7 ca unit) và `ProductionFlowE2EIT.productionCost_*` có ca hai lệnh chốt song song (200 + 409). Tìm `A39`/`A40` trong test ⇒ **không có**. `§0.71` ghi "chưa smoke trên app chạy thật". Thiết kế đã có: khoá dòng lệnh → kiểm quyền lại dưới khoá → tính lại phía server → snapshot + bút toán + `CLOSED` trong một transaction (`B260`).

| # | Việc | Chi tiết |
|---|---|---|
| a | **A39 — thu hồi quyền khi đang chờ khoá** | `@SpringBootTest` (loại IT duy nhất được phép, như `ProductionFlowE2EIT`), có method security thật. Thread A giữ khoá dòng lệnh bằng latch; thread B gọi chốt (`PERM_WORK_ORDER_CLOSE`) và bị chặn trên khoá; thu hồi assignment của B (commit); nhả A ⇒ B phải nhận **403**, không run/bút toán/`CLOSED`/audit thành công/replay-success. Dùng latch xác định, **không `sleep`** |
| b | **A40 — lỗi giữa chừng rollback toàn bộ** | `@SpyBean`/`@MockitoSpyBean` ném lỗi **sau** khi một phần ghi đã xảy ra (ví dụ sau khi run chuyển `FINALIZED`, trước khi ghi bút toán): kiểm DB trước/sau — status/version lệnh, trạng thái/version run, số dòng bút toán, tồn kho, audit, bản ghi idempotency thành công. **Không** thêm endpoint hay cờ lỗi vào production |
| c | **Retry sau rollback** (R03) | Cùng intent + cùng `Idempotency-Key` sau lỗi ⇒ đúng **một** bản chốt; hai close cùng `expectedVersion` ⇒ 1 thắng, 1 `CONCURRENT_MODIFICATION` (ca có sẵn — chỉ cần ghi mapping) |
| d | **Smoke HTTP (R04)** trên jar chạy thật (Postgres + Redis docker, profile dev, fixture mới — không dùng lại bản ghi đã dọn) | Fixture FE: lệnh 10 PCS, vật tư 20 KG × 1.000 chụp lúc RELEASE, một công đoạn 60 phút, nhân công 6.000 + chi phí chung 3.000 mỗi giờ ⇒ kế hoạch **29.000**; tiêu thụ 20, good 10 ⇒ thực tế **29.000**, đơn vị **2.900**; nhập + QC 10 vào pool đầu ra vật lý 0 ⇒ preview `READY`; chốt ⇒ `CLOSED`, bút toán giá trị-thuần `quantity 0`, tồn vật lý vẫn 10. Thêm ca `COST_SOURCE_STALE` (nguồn đổi), version sai, thiếu quyền, `known 0` khác thiếu giá |
| e | **Báo cáo (R05, Q3)** | Phân loại từng bằng chứng IT / HTTP; cùng build + giờ + trace + id; ghi rõ **không hỗ trợ**: hàng non-tracked sau đóng lệnh (COGS chưa biết), đóng lệnh không routing, `/variance` công thức khác. Không gọi IT là nghiệm thu FE |

**Mutation:** bỏ bước kiểm quyền lại dưới khoá ⇒ A39 đỏ (mã/HTTP có thể không đổi nếu không assert side-effect — assert DB); bỏ `@Transactional` hoặc ghi bút toán ngoài transaction ⇒ A40 đỏ.

**Wire:** không đổi. Nếu A39/A40 tìm ra lỗi thật ⇒ sửa trong phase này, ghi bất biến + mục "lỗi do test tìm ra" (khuôn `G8b`).

---

### 12.11 Câu hỏi [PO] gom lại cho đợt này

1. **CF-11** — quy tắc làm tròn của lệnh SX: **A** (giữ MRP `CEILING6`, lệnh SX `HALF_UP6`, thêm `setScale` tường minh) hay **B** (lệnh SX cũng `CEILING6`)? Đề xuất: A.
2. **CF-9** — nếu DB thật đã có hai lô chỉ khác hoa thường: dừng và để PO duyệt cách xử lý (không tự gộp/xoá). Chỉ cần trả lời khi bước (a) thấy dữ liệu trùng.
3. **CF-8.5** — bật `build-info` + `info.app.commit` cho `/actuator/info`? Đề xuất: có.
4. **CF-10** — xác nhận chấp nhận **chặn mới** (>6 số lẻ / >13 chữ số nguyên ⇒ 400) cho các field nêu ở 12.7; FE đã đề nghị, chỉ cần PO biết đây là breaking hẹp.

### 12.12 Rủi ro & lưu ý chung của đợt

- Đợt này **chủ yếu là chứng minh và khép lỗ hổng**, ít tính năng mới: nếu một phase ra "đã đủ, không cần sửa" thì **ghi đúng như vậy** kèm bằng chứng — FE đã nói rõ không muốn code trùng.
- `CF-9` và `CF-10` là hai phase duy nhất chắc chắn đổi hành vi; `CF-10` làm vỡ client gửi quá scale (chủ yếu script/seed) — rà `scripts/**` trước.
- Mọi phase vẫn theo quy ước test của đợt CF: test do sub agent viết/chạy, kết quả đối chiếu lại bằng `mvn -o clean verify`.
- Docker Desktop thường tắt qua đêm (`§0.70 #7`): bật `docker compose up -d postgres redis` trước IT/smoke, đọc **nguyên nhân** lỗi trước khi tính một mutation là "bị giết".

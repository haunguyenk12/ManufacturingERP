# Next Phase Plan — Roadmap toàn bộ phase còn lại

> **`G6` — FE handoff Giai đoạn 6** (`FE_HANDOFF_GD_06_2026-10-14.md`): ✅ **BE xong cả 3 phần, chờ FE nghiệm thu A01–A38** — user chốt tách 3 phần làm liền.
> ✅ **`G6a` xong 2026-10-17** (đơn bán hàng nối khách, giữ tồn cho đơn, `V74`, `BE_RESPONSE_GD_06_2026-10-17.md` v1;
> **1583 unit / 184 class + 224 IT / 33 class**, 0/0; bản ghi `CLAUDE.md §0.54`). Tiếp theo **`G6b`** (hoạch định + chuyển đổi),
> sau đó **`G6c`** (nhập Excel). Chi tiết **§13**.
> ✅ **`G6b` xong 2026-10-18** (run thương mại + chuyển đổi một phần, `V75`, `_v2`; **1621 unit + 230 IT**, 0/0; bản ghi `CLAUDE.md §0.55`).
> ✅ **`G6c` xong 2026-10-18** (nhập đơn bán Excel `kind=so`, không migration, `_v3`; **1634 unit / 187 class + 233 IT / 34 class**, 0/0; bản ghi `CLAUDE.md §0.56`).
>
> **`G5` — FE handoff Giai đoạn 5** (`FE_HANDOFF_GD_05_2026-10-13.md`): ✅ **BE xong + nghiệm thu
> 2026-10-16** — `G5-1`..`G5-6` hoàn tất, file trả lời **`BE_RESPONSE_GD_05_2026-10-16.md`** (**user tự gửi qua
> Zalo**). Migration **`V73`**. Phase chỉ đóng khi **FE** báo A01–A36 đạt. **1548 case unit / 179 class + 215 case IT /
> 32 class IT · failures = 0, errors = 0**. Bản ghi `CLAUDE.md §0.53`, chi tiết **§12**.
>
> **`G2` — FE handoff Giai đoạn 2** (`FE_HANDOFF_GD_02_2026-10-12.md`): ✅ **BE xong + nghiệm thu
> 2026-10-14** — `G2-1`..`G2-5` hoàn tất, file trả lời **`BE_RESPONSE_GD_02_2026-10-14_v2.md`** (thay v1,
> **user tự gửi qua Zalo**). Lỗi `leadTimeDays: 1.5` bị cắt thành `1` **đã sửa cho toàn hệ thống** (§9.6).
> Phase chỉ đóng khi **FE** báo A01–A22 đạt. **1325 case unit / 162 class + 184 case IT / 27 class IT ·
> failures = 0, errors = 0** (`mvn -o clean verify` thật với Docker). Bản ghi `CLAUDE.md §0.50`, chi tiết **§9**.
>
> Trước đó: **Track `AR-*` — refactor toàn bộ hạ tầng Audit** hoàn thành
> **2026-09-02** — bản ghi đầy đủ `CLAUDE.md §0.49`, kế hoạch + log triển khai + danh sách "cố ý chưa
> làm" ở `AuditRefactorPlan.md §13`, chi tiết kỹ thuật `common/audit/CLAUDE.md` (viết lại hoàn toàn).
> Migration **`V67`** (outbox + `audit_log_entities` + 10 cột mới, thuần additive, **không backfill**)
> + **`V68`** (trigger append-only). **Không permission mới, không breaking change wire.**
> Phạm vi và failure policy (**`FAIL_OPEN` toàn hệ thống**) do user chốt qua `AskUserQuestion`.
> 🔴 **Ba defect gốc, cả ba im lặng — không log, không lỗi, không test nào đỏ:** mọi event auth bị
> vứt bỏ (`AuthService` không có `@Transactional`, mà `AFTER_COMMIT` listener cần một transaction để
> giao event) · event `FAILURE` mất khi rollback · một `X-Trace-Id` dài làm hỏng insert **sau khi**
> business đã commit, tức client tự tắt được audit trail của chính mình. Sửa bằng transactional
> outbox (`B119`–`B123`). **1225 case unit / 158 class + 154 case IT / 22 class IT · failures = 0,
> errors = 0** (`mvn -o clean verify` thật với Docker); `AuditPipelineIT` (class IT thứ 22) chứng
> minh commit/rollback/idempotency/append-only trên Postgres thật.
>
> Trước đó: **Sửa contract giá trị của Audit Log** hoàn thành **2026-08-25** — bản ghi
> đầy đủ `CLAUDE.md §0.48`, trả lời FE `FE_SingleTask_Response.md`, nguồn
> `BACKEND_AUDIT_LOG_VALUE_CONTRACT_2026-08-25.md`. **Không migration, không permission mới, không
> endpoint mới.** `changes[].oldValue`/`newValue` của `GET /v1/audit-logs/{id}` trở lại **`string` |
> `null`** đúng như OpenAPI công bố — hồi quy từ commit `caps2 done` (đổi DTO sang `JsonNode` cùng
> lượt bật field-level diff) khiến một snapshot có cấu trúc lọt ra dạng object và làm sập màn hình
> Audit của FE. Bất biến **`B118`** (`common/audit`). **Breaking change wire: có, hẹp** — là khôi phục
> contract cũ. **1184 case unit / 152 class + 142 case IT / 21 class IT · failures = 0, errors = 0**
> (`mvn -o clean verify` thật với Docker) · nghiệm thu mutation 3/3 đụng `src/main` · smoke test A/B
> qua HTTP thật trên cùng một bản ghi audit.
>
> Trước đó: **Track `EH-*` — refactor hạ tầng xử lý lỗi** hoàn thành **6/8 hạng mục**
> 2026-08-23 — bản ghi đầy đủ `CLAUDE.md §0.47`, kế hoạch + khảo sát + breaking changes
> `ExceptionHandlerRefactorPlan.md`. **Không migration, không permission mới, không endpoint mới.**
> `EH-1` (lưới an toàn filter chain + `ApiErrorController`) · `EH-2` (tách `OPERATION_NOT_ALLOWED`
> thành 3 mã trên 59/96 throw site — **breaking change wire**, đổi `code`, giữ nguyên HTTP 422) ·
> `EH-3` (`DataIntegrityErrorMapper`, FK/`CHECK`/`NOT NULL` 409 → **422**) · `EH-4`
> (`MrpRunStateRecorder`, `REQUIRES_NEW` + sanitize `errorMessage`) · `EH-5` (xoá 4 mã chỉ khai báo,
> giữ 5 kèm lý do) · `EH-6` (tài liệu). `EH-7`/`EH-8` **hoãn có chủ đích**, lý do ở §4 của plan.
> **1181 case unit / 152 class + 142 case IT / 21 class IT · failures = 0, errors = 0**
> (`mvn -o clean verify` thật với Docker) · nghiệm thu mutation 5/5 đụng `src/main`.
>
> Trước đó: **Trả lời `live-data-audit.md` của FE** hoàn thành 2026-08-14 — bản ghi
> đầy đủ `CLAUDE.md §0.44`, hướng dẫn FE `FE_SingleTask_Response.md`. Migration **`V58`**
> (`mrp_runs` + idempotency). Hai thay đổi: available của lot `HOLD`/`REJECTED` nay là `0` trên
> `/inventory/balances` + `/inventory/lots*` (`B116`), và `POST /planning-runs` nhận
> `Idempotency-Key` tuỳ chọn (`B117`). Mục thứ ba FE báo (convert suggestion "không atomic")
> **không phải defect** — đã bác bỏ bằng dữ liệu thật, không sửa dòng nào.
> **1025 case unit + 120 case IT / 17 class IT · failures = 0, errors = 0**.
>
> Trước đó: **FE handoff Inventory Dashboard API** hoàn thành 2026-08-14 — bản ghi
> đầy đủ `CLAUDE.md §0.43`, hướng dẫn FE `FE_SingleTask_Response.md`. Không migration, không permission
> mới, không endpoint mới; `GET /reports/inventory-dashboard` bổ sung nhãn hiển thị + `generatedAt` +
> `lowStockLimit`/`movementLimit`. **1009 case unit + 119 case IT / 17 class IT · failures = 0,
> errors = 0** (`mvn -o clean verify` thật với Docker). Bất biến `B114`, `B115`.
>
> Trước đó: **FE-4 5C Item Master permission contract** hoàn thành 2026-08-09.
> Migration `V57__separate_item_master_permissions.sql`; unit suite `977/977` xanh. Flyway IT đã được
> bổ sung nhưng chưa chạy lại trong phiên này vì Docker engine không hoạt động.
>
> Phase trước: **`C2-2` — Inventory Lot lifecycle API** ✅ **HOÀN THÀNH 2026-08-06.** Bản ghi đầy đủ:
> `CLAUDE.md §0.37`. `GET /inventory/lots` + `GET /inventory/lots/{lotId}` +
> `POST /inventory/lots/{lotId}/status`. HOLD-escape gate qua `LotQcOriginLookupService` (cross-module
> lookup mới, `inventory → workorder`). Không migration.
>
> **925 case unit + 105 case IT / 14 class IT · failures = 0, errors = 0** (đo bằng `mvn -o clean
> verify` thật với Docker) · migration mới nhất vẫn `V55` (`C2-2` không migration).

---

## Vì sao file này có hình dạng khác các đợt trước

Từ trước tới nay file này chỉ giữ **một** phase đang chạy tại một thời điểm (`.claude/rules/dev-
workflow.md §6.6`). Bản này (2026-08-05) là ngoại lệ có chủ đích: viết sẵn roadmap cho **toàn bộ**
công việc còn lại của backend — cả track `C2-*` (Capstone 2) lẫn track `P*` (nghiệp vụ gốc) và nợ kỹ
thuật còn mở — để không phải lật `CLAUDE.md`/`FRONTEND_ALIGNMENT_ROADMAP.md`/
`MANUFACTURING_GAP_ROADMAP.md` mỗi lần muốn biết còn gì chưa làm. **Quy tắc "một phase đang chạy"
vẫn áp dụng khi thực thi** — mục "Trạng thái" ngay dưới đây luôn chỉ đúng một phase là "ĐANG CHẠY",
phần còn lại là "CHỜ".

**Phạm vi:** mọi phase chưa xong và chưa bị loại có chủ đích. Loại trừ tường minh: nhóm `P-Deferred`
(MES/ISA-95, OEE, real-time WIP, dynamic CRP — chờ lớp OT, ngoài phạm vi dự án) và nợ nhỏ đã bị từ
chối có chủ đích (`D`, `E`, `I` ở `FRONTEND_ALIGNMENT_ROADMAP.md §7.1` — xem Phụ lục cuối file).

## Trạng thái

| | |
|---|---|
| **Đang chạy** | **`G10` — FE handoff Giai đoạn 10: giao hàng, hoá đơn và thanh toán** (`FE_HANDOFF_GD_10_2026-10-15.md`, P1) — 🔄 **đang chạy** (quyết định chốt 2026-10-25, §17.2): **`G10a` ✅ · `G10b` ✅ 2026-10-25 · `G10c` ✅ 2026-10-26** · `G10d` — xem **§17**. 🔴 Khác mọi handoff trước: **gần như toàn bộ là dựng mới** và là lần đầu repo chạm **tiền**. Không có entity/bảng/quyền/endpoint nào cho giao hàng, hoá đơn, thanh toán; đơn bán **chưa có giá**; `customerId` của đơn còn nullable; kho **không có** sổ "lô này thuộc dòng đơn nào" như FE giả định. `G9` đã xong ở BE, chờ FE nghiệm thu |
| **Chờ FE** | `G9` (§16) — ✅ BE xong cả 3 phần 2026-10-21/23, bản ghi `CLAUDE.md §0.64`–`§0.66`. `G8` (§15) — ✅ BE xong cả 4 phần 2026-10-19/20, bản ghi `CLAUDE.md §0.60`–`§0.63`. `G7` (§14) — ✅ 2026-10-18/19. `G6` (§13) — ✅ 2026-10-17/18. `G5` (§12) — ✅ 2026-10-16. `G4` (§11) — ✅ 2026-10-15. `G2` (§9), `G3` (§10) — ✅ 2026-10-14 |
| **Ứng viên kế tiếp** | **`G5` — FE handoff Giai đoạn 5: định mức, quy trình, công suất** (`FE_HANDOFF_GD_05_2026-10-13.md`, P1) — 📝 **đã lập kế hoạch 2026-10-15, chờ user chốt 12 quyết định (§12.2) trước khi code** — xem **§12**. Định giá tồn kho (D1 của G4, gộp với `P7`) — **chưa lập kế hoạch**. `G1` — FE handoff Giai đoạn 1 (Auth/tài khoản/quyền) — **chưa lập kế hoạch** |
| **Bị chặn** | *(không có)*. ⚠️ `MANUFACTURING_PHASE_PLAN_P7_P11.md` dự kiến migration `V69`→`V73`, nhưng `V69`–`V72` đã dùng (`G2`/`G3`/`G4`) ⇒ **phải đánh số lại từ `V73`** trước khi làm `P7` |

---

## Thứ tự đề xuất

| # | Phase | Trạng thái | Vì sao xếp ở đây |
|---|---|---|---|
| 1 | ~~`C2-8` — CRP tĩnh + Capacity Board + Schedule Adjustment~~ | ✅ **Đã xong (2026-08-05)** | Đóng nốt `P4`. Bản ghi: `CLAUDE.md §0.30` |
| 2 | ~~`P3` — Costing Engine~~ | ✅ **Đã xong (2026-08-05)** | Bản ghi: `CLAUDE.md §0.31` |
| 3 | ~~Concurrent refresh-token race (mở rộng `D8`)~~ | ✅ **Đã xong (2026-08-05)** | Bản ghi: `CLAUDE.md §0.32` |
| 4 | ~~`P5` — Serial Number Tracking~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.33` |
| 5 | ~~`P6` — WO Close/Reconcile~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.34` |
| 6 | ~~`C2-1` — Audit Logs read API~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.36` |
| 7 | ~~`C2-2` — Inventory Lot lifecycle API~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.37` |
| 8 | ~~`D8c` — Forgot-password / Account Recovery~~ | ✅ **Đã xong (2026-08-06)** | Bản ghi: `CLAUDE.md §0.35` |
| 9 | **`G2` — FE handoff Giai đoạn 2 (tổ chức + danh mục nền)** | ✅ **BE xong (2026-10-14)** — chờ FE nghiệm thu | Nguồn `FE_HANDOFF_GD_02_2026-10-12.md`, P1. Chi tiết **§9**, bản ghi `CLAUDE.md §0.50` |
| 10 | **`G3` — FE handoff Giai đoạn 3 (nhập Excel dữ liệu nền)** | ✅ **BE xong — chờ FE nghiệm thu** | Nguồn `FE_HANDOFF_GD_03_2026-10-12.md`, P1. Phụ thuộc `G2` (Customer/Supplier master) — đã xong. Chi tiết **§10** |
| 11 | **`G4` — FE handoff Giai đoạn 4 (tồn kho, lô, chuyển kho)** | ✅ **BE xong (2026-10-15)** — chờ FE nghiệm thu | Nguồn `FE_HANDOFF_GD_04_2026-10-12.md`, P1. Quyết định lớn nhất: có làm định giá tồn kho (moving average) hay không — đụng vùng `P7` (D1). Chi tiết **§11** |
| 12 | **`G5` — FE handoff Giai đoạn 5 (định mức, quy trình, công suất)** | 📝 **Đã lập kế hoạch — chờ chốt §12.2** | Nguồn `FE_HANDOFF_GD_05_2026-10-13.md`, P1. File FE đề xuất **đảo ngược 4 quyết định thiết kế đã chốt** ở `F4`/`C2-7`/`C2-8`/`F10` — phải chốt từng cái trước khi viết dòng nào. Chi tiết **§12** |
| 13 | **`G6` — FE handoff Giai đoạn 6 (đơn bán hàng + hoạch định vật tư)** | ✅ **BE xong 2026-10-17/18 — chờ FE nghiệm thu** | Nguồn `FE_HANDOFF_GD_06_2026-10-14.md`, P1. Mở lại hai thứ đã **cố ý hoãn**: nối `SalesOrder` ↔ `Customer` (`G2 §9.5`) và giữ tồn cho đơn bán (reservation hiện **chỉ** thuộc WO). Chi tiết **§13** |
| 14 | **`G7` — FE handoff Giai đoạn 7 (đơn mua hàng, nhận hàng, QC)** | ✅ **BE xong cả 3 phần 2026-10-18/19 — chờ FE nghiệm thu** | Nguồn `FE_HANDOFF_GD_07_2026-10-14.md`, P1. Module mua hàng **đã có** — việc chính là đổi vòng đời (PO `CLOSED`/sửa nháp, phiếu nhận `DRAFT`, nhận vào `HOLD`) cộng **hai thứ chưa từng có**: QC mua hàng và giá trị tồn kho. Chi tiết **§14** |
| 15 | **`G8` — FE handoff Giai đoạn 8 (lệnh sản xuất, cấp vật tư, công đoạn, dở dang)** | ✅ **BE hoàn thành — `G8a`/`G8b` 2026-10-19, `G8c`/`G8d` 2026-10-20; chờ FE nghiệm thu** | Nguồn `FE_HANDOFF_GD_08_2026-10-14.md`, P1. Chi tiết **§15** |
| 16 | **`G9` — FE handoff Giai đoạn 9 (nhập thành phẩm, kiểm tra chất lượng)** | ✅ **BE xong (`G9a` 2026-10-21 · `G9b`/`G9c` 2026-10-23) — chờ FE nghiệm thu** | Nguồn `FE_HANDOFF_GD_09_2026-10-15.md`, P1. Vòng đời phiếu nhập + QC **đã có** (`F2`/`D5`), trần từ good công đoạn cuối **vừa đúng** nhờ `G8c`, lô con + `splitLot` **vừa dựng** ở `G7c`. Việc chính: QC theo **lượng** nhiều lượt, phân bổ theo từng lượt báo (truy xuất nguồn), rút/huỷ/chi tiết/`PATCH`, 2 quyền đọc. Chi tiết **§16** |
| 17 | **`G10` — FE handoff Giai đoạn 10 (giao hàng, hoá đơn, thanh toán)** | 🔄 **Đang chạy** — `G10a` ✅ 2026-10-25 | Nguồn `FE_HANDOFF_GD_10_2026-10-15.md`, P1. 30 yêu cầu, 40 câu hỏi, 48 ca, 19 màn hình. **Dựng mới gần như toàn bộ**: ba aggregate chứng từ, 10 quyền mới, kiểu tiền VND, giá bán trên đơn. Ba rủi ro chính: lộ giá bán cho người không có quyền tiền (mẫu "hai hình dạng theo quyền" chưa tồn tại), trừ tồn kho theo hai nguồn (phần giữ của đơn + hàng tự do), và MRP có đếm đôi phần đã giao. Chi tiết **§17** |

> `C2-8`..`8` là thứ tự **đề xuất**, không phải bắt buộc — xác nhận lại với user trước khi bắt đầu
> từng phase, vì độ ưu tiên nghiệp vụ có thể đổi giữa chừng.

---

## 1. `C2-8` — CRP tĩnh + Capacity Board + Schedule Adjustment ✅ ĐÃ XONG (2026-08-05)

Đóng nốt `P4`. Bản ghi đầy đủ (thiết kế, quyết định đã chốt, bất biến B89-B90, breaking changes):
`CLAUDE.md §0.30`. Ba quyết định chốt với user trước khi viết kế hoạch: lịch operation sinh ở
`WorkOrderService.release()` (không phải lúc tạo/`plan()`); nợ B
(`predecessorOperationIds`/sequence-dependency) tách `C2-8b`, không làm ở đây; `schedule-adjustments`
không bao giờ tự dời operation khác, chỉ trả cờ tư vấn. Migration `V48`+`V49`.

---

## 2. `P3` — Costing Engine ✅ ĐÃ XONG (2026-08-05)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P3). Bản ghi đầy đủ (thiết kế, quyết định đã chốt,
bất biến B91-B92, breaking changes): `CLAUDE.md §0.31`. Hai quyết định chốt với user trước khi viết
kế hoạch chi tiết: `laborCost`/`overheadCost` nhập tay theo rate cố định trên `ItemStandardCost`
(không tính từ `WorkOrderOperation.runMinutesPerUnit`); **bỏ Material Price Variance**, chỉ làm
Material Usage Variance (không có cột giá trên `stock_movements`, dữ liệu giá duy nhất
— `PurchaseOrderLine.unitPrice` — tách rời khỏi ledger xuất kho). Module mới `module/costing`.
Migration `V50`+`V51`.

---

## 3. Concurrent refresh-token race (mở rộng `D8`) ✅ ĐÃ XONG (2026-08-05)

Đóng giới hạn "race double-submit" mà `D8a` chấp nhận có chủ đích (`common/security/CLAUDE.md
§4.12`). Bản ghi đầy đủ (thiết kế thật, bất biến `B95`, breaking changes): `CLAUDE.md §0.32`.
Quyết định chốt với user trước khi viết kế hoạch chi tiết: **hard lock, không grace window** — token
cũ không bao giờ được chấp nhận lại làm credential lần hai. Thiết kế thật lệch bản phác thảo gốc ở
đúng một chỗ: khoá dùng `SET NX PX` (`opsForValue().setIfAbsent`, atomic một lệnh) thay vì Lua script
— đọc lại `TokenStoreService.incrementFailCount` (bộ đếm brute-force) mới phát hiện nó **chưa từng**
dùng Lua như tài liệu cũ ghi (đã sửa cùng phase, xem `CLAUDE.md §0.32` hệ quả #3). Không migration.

---

## 4. `P5` — Serial Number Tracking ✅ ĐÃ XONG (2026-08-06)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P5). Bản ghi đầy đủ (thiết kế, quyết định đã chốt, bất
biến B96-B99, breaking changes): `CLAUDE.md §0.33`. Hai quyết định chốt với user trước khi viết kế
hoạch chi tiết: `lotTracked`/`serialTracked` **loại trừ nhau**; serial-tracked output **không** có
HOLD chờ QC (mirror non-lot-tracked của `D5`, tránh phải thêm `serial_id` vào `stock_balances` +
viết lại `StockBalanceRepository.aggregate*`). Thiết kế thật lệch bản phác thảo gốc ở đúng một chỗ,
theo hướng **tốt hơn dự đoán**: mỗi movement chạm serial luôn `quantity = 1` nên
`MaterialIssueLineRequest`/`ProductionReceiptPostRequest` chỉ cần thêm field optional
(`serialId`/`serialNumber`) — **không phải breaking change** như roadmap gốc dự đoán (`List<serialId>`
thay `quantity`). Migration `V52`.

---

## 5. `P6` — WO Close/Reconcile ✅ ĐÃ XONG (2026-08-06)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P6). Đóng nốt track `P6` (Sales Order + Fulfillment đã
xong ở `F3`/`F6`) — và đóng nốt **toàn bộ** track `P*` (chỉ còn `P-Deferred`, chờ tầng OT, ngoài phạm
vi). Bản ghi đầy đủ (thiết kế, bất biến B100, breaking changes): `CLAUDE.md §0.34`. Quyết định chốt
với user trước khi viết kế hoạch chi tiết: `CLOSED` **khoá hoàn toàn** — không carve-out đọc/ghi nào
sau khi đóng (phương án đơn giản hơn trong hai phương án được hỏi). `close()` cũng là bước "Reconcile"
tường minh: giải phóng mọi reservation `ACTIVE` còn sót về lại tồn khả dụng trước khi khoá vĩnh viễn
— tái dùng đúng `materialReservationService.cancelActiveReservations(...)` mà `cancel()` đã có sẵn.
Tái dùng `PERM_WORK_ORDER_MANAGE`, không permission mới. Migration `V53`. Phase phát hiện và sửa một
bug thật: `canReserve()` là danh sách phủ định, thiếu loại trừ `CLOSED` tường minh.

---

## 6. `C2-1` — Audit Logs read API ✅ ĐÃ XONG (2026-08-06)

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.3`. FE xác nhận (`docs/capstone2-api-gap-response.md §5`
câu 1, 2026-08-06): màn hình Audit dùng được với `changes[]` rỗng — đợt 1 không cần chờ diff capture
thật (đợt 2, `AuditableAspect` field-level, vẫn chưa xếp lịch). `PERM_AUDIT_READ` **ADMIN-only**
(chốt với user qua `AskUserQuestion`). Cột `plant_id` thêm vào `audit_logs` (`V54`) nhưng **chỉ dừng
ở schema** — không wiring populate real-time, mọi dòng (cũ lẫn mới) vẫn `NULL` cho tới phase riêng.
2 endpoint: `GET /audit-logs` (list, không `changes[]`), `GET /audit-logs/{id}` (detail, `changes[]`
join thật vào `AuditLogChangeRepository`, luôn rỗng hôm nay vì chưa ai ghi vào bảng đó — không phải
hardcode `[]`). Phát hiện + sửa biến thể mới của lỗi `lower(bytea)` (`CLAUDE.md §0.24`): tham số
`Instant` chỉ xuất hiện ở vế `IS NULL` khiến Postgres không suy được type — sửa bằng
`cast(:from as timestamp) IS NULL`. Migration `V54`+`V55`. Bản ghi đầy đủ: `CLAUDE.md §0.36`,
`common/audit/CLAUDE.md §9.12`.

---

## 7. `C2-2` — Inventory Lot lifecycle API ✅ ĐÃ XONG (2026-08-06)

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.2`. FE xác nhận (`docs/capstone2-api-gap-response.md §5`
câu 2, 2026-08-06): màn hình Inventory Lots bắt buộc dẫn user sang QC disposition khi lot `HOLD` chờ
QC — giữ nguyên thiết kế đã mô tả. 3 endpoint: `GET /inventory/lots`, `GET /inventory/lots/{lotId}`,
`POST /inventory/lots/{lotId}/status`. **Không migration.**

Quyết định chốt với user (`AskUserQuestion`): HOLD-escape gate dùng **cross-module lookup thật**
(`LotQcOriginLookupService` mới trong `module/workorder`, kiểm `ProductionReceiptLine` tồn tại **và**
`QualityDisposition` chưa tồn tại), không phải heuristic cùng-module suy từ `referenceType` — heuristic
có lỗ hổng thật với lot đã QC rồi bị đưa lại `HOLD` thủ công. Hướng phụ thuộc mới `inventory →
workorder`, hợp lệ theo rule C7. `InventoryMovementService.changeLotStatus` (đã có từ `F2`) không đổi
gì — gate nằm ở tầng gọi mới, luồng QC disposition hiện có không bị ảnh hưởng. Bản ghi đầy đủ (bất
biến `B102`-`B106`, breaking changes): `CLAUDE.md §0.37`.

---

## 8. `D8c` — Forgot-password / Account Recovery ✅ ĐÃ XONG (2026-08-06)

Trả nốt 3/3 nợ #6 (`D8a` RTR + `D8b` absolute session timeout đã trả 2/3 trước đó). Thiết kế đã có
sẵn ở `common/security/CLAUDE.md §4.14` — phase này triển khai đúng thiết kế, không thiết kế lại.
Bản ghi đầy đủ (bất biến `B101`, breaking changes): `CLAUDE.md §0.35`. Quyết định hạ tầng chốt với
user trước khi viết kế hoạch chi tiết: gửi email bằng **mock/log console** — không thêm
`spring-boot-starter-mail`, không SMTP/SES/SendGrid nào được tích hợp, không interface cho một
implementation duy nhất (`coding-rules.md §11.5`). 3 endpoint mới: `POST /auth/forgot-password`,
`POST /auth/reset-password`, `PATCH /admin/users/{id}/unlock` (controller `/admin` đầu tiên trong
repo). Không migration.

---

## 9. `G2` — FE handoff Giai đoạn 2: Tổ chức và danh mục nền ✅ BE XONG (2026-10-14) — chờ FE nghiệm thu

Nguồn: `FE_HANDOFF_GD_02_2026-10-12.md` (v1, P1, PO chốt FE ngày 12/10/2026). FE đã chốt mock; BE phải
(1) **trả lời** 16 câu §5 của họ, (2) **đối chiếu** 11 kết quả R1–R11 với API hiện có, (3) **chỉ code
phần thiếu**. Đầu ra gửi FE theo `BETaskDeliveryRules.md`: file `BE_RESPONSE_GD_02_<ngày gửi>.md`.

> 🔴 **Đọc như bảng câu hỏi, không như danh sách việc** (`BETaskDeliveryRules.md §5` lỗi #1, đã mắc
> thật ở `§0.41`/`§0.43`). Trong 16 câu §5, phần lớn trả lời được bằng hành vi hiện có; chỉ những mục
> đánh dấu **CODE** ở §9.1 mới là việc phải viết. FE cũng ghi rõ: *"Không coi bảng này là xác nhận rằng
> API hiện có đang sai."*

### 9.1 Đối chiếu yêu cầu FE ↔ code thật *(rà ngày 2026-10-13, bằng đọc source — chưa gọi live)*

Phạm vi đã rà: `SupplierController/Service/Repository`, `ItemWarehouseSettingService/Request/Response`,
`OrganizationController` + `OrganizationService.deactivatePlant/activateWarehouse/resolveTimezone`,
`ItemController` + `ItemService.create/update/listItems`, `UomController`, `UomLookupService`,
`PageableFactory`, `V13`/`V20`/`V21`/`V66` + unique constraint của `plants`/`warehouses`/`items`/`uoms`,
`application.yml` (`spring.jackson`). **Chưa rà:** `AccessControl`/scope `GLOBAL` (thuộc `G1`), FE
adapter (không có repo FE).

| # | FE yêu cầu | Hiện trạng code | Loại |
|---|---|---|---|
| R1 | Supplier: list/create/detail/update/activate/deactivate | **Đã có** nhưng **lệch hình dạng**: path `/v1/suppliers?companyId=` (không phải `/companies/{C}/suppliers`), query `keyword` (không phải `search`), deactivate là `PATCH .../deactivate`, **không có activate**, thiếu `contactName`/`notes`, thừa `taxCode`/`companyCode` | **CODE** (§9.2 D1) |
| R1 | Customer master | **Không tồn tại.** `sales_orders.customer_name` là text tự do | **CODE** — module mới |
| R2 | code trim/uppercase, unique theo Company + loại danh mục | Supplier: `UNIQUE (company_id, code)` (`V20`) + service uppercase. Item/Plant/Warehouse: `@Pattern("^[A-Z0-9._-]+$")` chạy **trước** normalize ⇒ `" partner-01 "` bị **400**, không được chuẩn hoá | Trả lời (FE đã tự normalize) |
| R3 | Update không đổi code/parent/type/tracking; unit theo Q6 | `ItemUpdateRequest` chỉ có `name`+`unit` ⇒ code/type/tracking **không đổi được**, nhưng field lạ bị **bỏ qua im lặng** (`fail-on-unknown-properties: false`, `§0.42`) chứ không bị từ chối. **`unit` sửa được** qua PATCH | Trả lời + **CODE** (Q6, D5) |
| R4 | Không tạo/kích hoạt Warehouse dưới Plant INACTIVE; không ghi settings cho master INACTIVE | Đã có (`activateWarehouse`, `B108`; settings dùng `getActiveItem` + `findActiveWarehouse`). ⚠️ Settings **không** kiểm Plant của kho có ACTIVE không | Trả lời + **CODE nhỏ** |
| R5 | GET settings đúng Plant đã authorize | 🔴 **Lỗ hổng thật:** `GET /v1/inventory/item-warehouse-settings` **không** có filter plant; không truyền `warehouseId` thì guard là `hasPermission` (chỉ đọc scope `GLOBAL`) và trả settings của **mọi công ty** — đúng cái bẫy đã ghi ở `CLAUDE.md §0.46` (PF-03). `X-Plant-Id` FE gửi bị bỏ qua | **CODE** |
| R6 | safety/ROP decimal ≥ 0, leadTime nguyên ≥ 0 | Đã có (`@PositiveOrZero`, `@Min(0)`, `Integer`). ⚠️ Wire là **JSON number**, không phải "decimal string" như FE ghi | Trả lời (Q12) |
| R7 | Không 2 mapping ACTIVE cùng pair; ≤ 1 default ACTIVE / Item+Plant+mục đích | Đã có ở DB: `uk_item_wh_settings_active` (partial, `V13`) + trigger `trg_item_warehouse_default_role` (`V66`) | Trả lời |
| R8 | Thay default có xác nhận, nguyên tử, serialize đồng thời | 🔴 **Ngược thiết kế FE:** `upsert` **luôn tự chuyển** cờ từ kho cũ sang kho mới, **không hỏi** (`releaseDefaultRoleFromOtherWarehouses`). Nguyên tử trong 1 transaction: có. Serialize đồng thời: **không** — hai request cùng đọc rồi cùng flush, trigger chặn một bên nhưng mã lỗi trả ra chưa ai kiểm | **CODE** (D2) + `*IT` |
| R9 | Ngừng dùng là logical; reference mới loại INACTIVE | Đã có cho mọi master. ⚠️ `ItemWarehouseSetting.deactivate()` **không xoá cờ** default ⇒ row INACTIVE giữ `defaultSupply=true` | **CODE nhỏ** (cần cho Q7) |
| R10 | Search/filter trước pagination, tie-breaker ổn định | 🔴 `GET /companies/{C}/plants` và `/plants/{P}/warehouses` **không có** `search`/`status` — FE adapter gửi, Spring **bỏ qua im lặng** ⇒ live trả danh sách không lọc. Item list chỉ có `search`. Phần lớn list dùng `PageableFactory.of(4 tham số)` — **không** tie-breaker | **CODE** |
| R11 | Sai quyền/scope bị từ chối trên server | Đã có `@PreAuthorize` mọi method. Ngoại lệ chính là R5 | Trả lời + R5 |
| §4 | `INVALID_TIMEZONE` | 🔴 **Bug tiềm ẩn:** `resolveTimezone` chỉ `trim()`, **không** `ZoneId.of` ⇒ timezone rác được lưu, rồi nổ **500** về sau ở `WorkCalendarLookupService.computeEndInstant` lúc release WO (`C2-8`) | **CODE** |
| A08 | UOM INACTIVE không dùng tạo Item mới | 🔴 **Chưa nối:** `UomLookupService` có **0 caller ngoài module `uom`**; `Item.unit` là `String` tự do, không kiểm với bảng `uoms` (nợ ghi từ `C2-3`) | **CODE** (D5) |
| Q4 | Chặn deactivate Plant còn Warehouse ACTIVE | `deactivatePlant` **không kiểm gì** | **CODE** (D4) |
| Q10 | Replay `Idempotency-Key` cho mutation master | **Không** controller master nào đọc header | Trả lời (D6) |

### 9.2 Quyết định phải chốt với user trước khi code ✅ *(chốt 2026-10-13: user trả lời "làm" ⇒ cả 7 theo cột "Khuyến nghị BE")*

Hai chi tiết con chốt khi giao việc: D4 dùng **mã mới** `ACTIVE_CHILDREN_EXIST` (422); timezone sai dùng
**`VALIDATION_ERROR` + `errors[{field:"timezone"}]`** (mã đã có, tương đương `INVALID_TIMEZONE` của FE — không thêm mã).

| # | Quyết định | Khuyến nghị BE | Lý do | Chặn |
|---|---|---|---|---|
| **D1** | Supplier: đổi sang hình dạng FE đề xuất, hay **thêm song song**? | **Thêm song song (additive)**: thêm `GET/POST /v1/companies/{C}/suppliers` (query `search`) + `POST /v1/suppliers/{id}/activate` + `POST /v1/suppliers/{id}/deactivate`; **giữ** path cũ (`/v1/suppliers?companyId=`, `PATCH .../deactivate`) | Path cũ đang dùng cho luồng mua hàng (`§0.46` seed gọi nó). Đổi tại chỗ = breaking không cần thiết. Customer dùng **đúng** hình dạng mới ngay từ đầu | `G2-2` |
| **D2** | `replaceDefaults` trên `PUT` settings | `Boolean replaceDefaults` **nullable**: `null` ⇒ **giữ hành vi hiện tại** (tự chuyển cờ), `false` ⇒ `409 DEFAULT_CONFLICT` nếu kho khác giữ cờ, `true` ⇒ chuyển cờ. Thêm lock bi quan theo `(item, plant)` để serialize | FE đề xuất mặc định `false` ⇒ mọi client hiện tại (adapter cũ, script seed `D26-`/`VIETBIKE`) đang thành công sẽ **bắt đầu nhận 409** — breaking. Nullable vừa cho FE đúng hành vi mới, vừa không phá ai | `G2-3` |
| **D3** | PATCH đối tác: `omitted = giữ, null = xoá` (Q3)? | **Không theo FE** — giữ quy ước toàn repo: `null`/omitted = giữ, **chuỗi rỗng `""` = xoá** | Java `record` không phân biệt được omitted với `null` nếu không thêm `JsonNullable`/`Map` — một cơ chế mới chỉ cho 2 endpoint. Tiền lệ: `CLAUDE.md §0.29` hệ quả #4. Trả lời Q3 bằng phương án khác + ví dụ JSON | `G2-1`, `G2-2` (PATCH) |
| **D4** | Deactivate Plant còn Warehouse ACTIVE (Q4) | **Chặn**, `422` mã mới `ACTIVE_CHILDREN_EXIST` (hoặc tái dùng `OPERATION_NOT_ALLOWED` nếu user không muốn mã mới) | Không cascade ngoài ý định người dùng — FE cũng nghiêng về chặn. ⚠️ Rà script seed có deactivate plant không trước khi bật | `G2-4` |
| **D5** | `Item.unit`: bất biến qua PATCH (Q6) + kiểm với UOM master (A08) | **Có cả hai**: create ⇒ `unit` phải là UOM **ACTIVE** (`RESOURCE_INACTIVE`/`ENTITY_NOT_FOUND`); PATCH ⇒ `unit` khác giá trị cũ thì `422`, giống thì chấp nhận (adapter cũ vẫn gửi `unit` không đổi). Giữ `unit` trong `ItemUpdateRequest` để không breaking | 🔴 **Rủi ro:** item tạo với unit **không có** trong bảng `uoms` (dữ liệu demo, script seed) sẽ hết tạo được. Phải rà `scripts/demo*/catalogue.sh` tạo UOM trước item chưa | `G2-4` |
| **D6** | `Idempotency-Key` cho mutation master (Q10) | **Không làm ở phase này** — trả lời: header bị bỏ qua; retry create được chặn bởi unique code ⇒ `409 RESOURCE_ALREADY_EXISTS` (FE hiểu là "đã tạo"), retry PATCH/activate là idempotent tự nhiên | `A2` giới hạn idempotency cho POST nhạy cảm ghi tồn kho/chứng từ. Làm cho ~20 endpoint master là phase riêng, chưa ai cần ngoài nghiệm thu A21 | Không (chỉ trả lời) |
| **D7** | Permission settings: FE ghi `PERM_ITEM_*`, code dùng `PERM_INVENTORY_*` | **Giữ `PERM_INVENTORY_*`**, trả lời FE mapping đúng | Đổi permission = đổi cấu hình role của mọi môi trường. Customer: thêm `PERM_CUSTOMER_READ/_MANAGE` theo đúng ma trận grant hiện có của `PERM_SUPPLIER_*` (Q2) | `G2-1` (perm Customer), `G2-0` |

### 9.3 Gói công việc *(thứ tự thực thi; mỗi gói là một commit theo `dev-workflow.md §6.6`)*

#### `G2-0` — Baseline + trả lời §5 cho FE *(không code `src/main`, không bị chặn)*

1. `mvn -o clean verify` với Docker ⇒ ghi baseline thật (số cuối cùng đã ghi là **1225 unit / 158
   class + 154 IT / 22 class**, `§0.49` — **đo lại, không chép**).
2. Chạy backend + gọi live từng endpoint hiện có ở FE §3.3 trên DB dùng-một-lần (chuỗi C1→P1→W1/W2→
   KG→I1) để có **request/response thật** cho file trả lời (`B2`: cấm mẫu viết tay).
3. Viết `BE_RESPONSE_GD_02_<ngày>.md` §0–§8: trả lời Q1–Q16 (đã làm / sẽ làm sau quyết định §9.2 /
   khác đề xuất + ví dụ JSON), bảng mapping mã lỗi FE §4 ↔ mã thật (Q13):

   | FE đề xuất | Mã thật | HTTP thật |
   |---|---|---|
   | `DUPLICATE_CODE` | `RESOURCE_ALREADY_EXISTS` | 409 |
   | `INACTIVE_PARENT` / `INACTIVE_REFERENCE` | `RESOURCE_INACTIVE` | **422**, không phải 409 |
   | `NOT_FOUND` | `ENTITY_NOT_FOUND` | 404 |
   | `PLANT_CONTEXT_MISMATCH` | `STATE_CONFLICT` | **409**, không phải 400 (`error-handling.md §5.6.1`) |
   | `IMMUTABLE_FIELD` | *(không có — field lạ bị bỏ qua, response trả giá trị cũ)* | 200 |
   | `INTERNAL_ERROR` | `INTERNAL_SERVER_ERROR` | 500 |
   | `INVALID_TIMEZONE` · `ACTIVE_CHILDREN` · `DEFAULT_CONFLICT` | chưa có ⇒ theo `G2-3`/`G2-4` | — |

   Kèm trả lời Q12 (decimal là **JSON number**, scale theo DB `NUMERIC(19,6)`), Q14 (unique: Plant/Item/
   Supplier theo Company, Warehouse theo Plant, UOM toàn hệ thống — **đúng như FE đề xuất**, đã có ở
   DB), Q15 (chờ `G1`).
4. Gửi **sau** khi user chốt §9.2 — câu trả lời phụ thuộc D1–D7.

#### `G2-1` — Customer master *(mới hoàn toàn; chỉ chặn bởi D3 cho PATCH)*

- **Vị trí:** `module/sales/` (entity `Customer` + repository + service + controller + lookup service),
  đối xứng với `Supplier` nằm trong `module/purchasing`. **Không** nối `SalesOrder.customerName` →
  `customer_id` (FE ghi bán hàng đầy đủ là ngoài phạm vi).
- **Migration `V69`:** bảng `customers` (`customer_id` UUID, `company_id` FK, `code` 100, `name` 255,
  `contact_name` 255, `email` 255, `phone` 255, `address` 500, `notes` 500, `status` + `CHECK`, audit
  field, `version`), `UNIQUE (company_id, code)`, index `(company_id, status)`. Cùng migration: thêm
  `contact_name`/`notes` vào `suppliers` (cho `G2-2`).
- **Migration `V70`:** seed `PERM_CUSTOMER_READ`/`_MANAGE`, grant **đúng ma trận** `PERM_SUPPLIER_*`
  đang có trong DB (đọc từ `role_permissions` thật, không đoán từ `V21`); ghi `docs/roles-and-permissions.md` (`C10`).
- **6 endpoint** đúng FE §3.4: `GET/POST /v1/companies/{C}/customers`, `GET/PATCH /v1/customers/{id}`,
  `POST /v1/customers/{id}/activate|deactivate`. Guard: `hasResourceAccess(..., 'COMPANY', ...)` +
  guard record cho path `{id}` (mẫu `purchasingPermissionGuard.hasSupplierAccess`). Sort mặc định `code
  asc` **có tie-breaker** id.
- `AuditAction`: `CUSTOMER_CREATED/UPDATED/ACTIVATED/DEACTIVATED`.
- **Test:** service (uniqueness, normalize, lifecycle idempotent, Supplier + Customer cùng code hợp lệ),
  method-security (deny + allow + `verify` chuỗi `PERM_*`), controller (envelope, 201, 409, 400 email),
  `FlywayMigrationIT.migrate_v70_*` (ma trận grant **dương + cấm**), `CustomerRepositoryIT` nếu `search`
  dùng `concat`/`like` trên tham số nullable (bẫy `lower(bytea)`, `§0.24`).

#### `G2-2` — Căn chỉnh Supplier *(chặn bởi D1, D3)*

- Thêm `contactName`/`notes` vào request/response (additive). Giữ `taxCode`.
- Theo D1: thêm path company-scoped + `activate` + `POST deactivate`; path cũ giữ nguyên.
  `Supplier.activate()` chặn khi Company `INACTIVE` (đúng `B108`).
- Test sửa theo `R10` cho record positional (`SupplierResponse`, `SupplierCreateRequest`).

#### `G2-3` — Item–Warehouse settings *(chặn bởi D2)*

1. **R5 (lỗ hổng scope):** thêm query `plantId` vào `GET`; guard `hasResourceAccess(..., 'PLANT',
   plantId)`; repository lọc `warehouse.plant`. Cross-check `X-Plant-Id` qua `PlantContextResolver` cho
   `GET` (có `plantId`) và `PUT` (đối chiếu plant của `warehouseId`) — theo `§5.6.1`.
   ⚠️ Giữ nhánh `warehouseId` hiện có. Bỏ hẳn nhánh "không filter + `hasPermission`" là **breaking** ⇒ ghi
   rõ, hỏi user nếu phải bỏ.
2. **R8/D2:** `replaceDefaults` nullable + `BusinessErrorCode.DEFAULT_CONFLICT` (409) + lock bi quan
   (`@Lock(PESSIMISTIC_WRITE)` trên query lấy settings của `(item, plant)`) **trước** khi đọc peer.
3. **Q7:** upsert tìm cả row `INACTIVE` của cùng pair ⇒ **kích hoạt lại** (giữ `settingId`) thay vì
   insert row mới.
4. **R9:** `deactivate()` xoá cả hai cờ default (bắt buộc cho bước 3 — nếu không, kích hoạt lại một row
   mang cờ cũ sẽ đụng trigger `V66`).
5. **R4:** từ chối khi Plant của kho `INACTIVE` (`RESOURCE_INACTIVE`).
6. **Test:** unit cho cả 3 nhánh `replaceDefaults`; **`ItemWarehouseSettingConcurrencyIT`** (2 thread,
   cùng item+plant, cả hai `replaceDefaults=true` ⇒ cuối cùng đúng 1 default, không 500) — mock **không**
   kiểm được lock/trigger (`R7`); IT cho filter `plantId` (đúng bẫy `§0.46` PF-03: fixture **phải có 2
   công ty**).

#### `G2-4` — Organization + Item: filter, lifecycle, validation *(chặn bởi D4, D5)*

1. **R10:** `search` (code/name) + `status` cho list plants/warehouses; `status`/`type`/`lotTracked`
   cho list item (Q9 — tên tham số chốt `type`, khớp field response). Mọi list trong phạm vi dùng
   `PageableFactory.of(..., tieBreaker)`. JPQL nullable ⇒ `cast(... as string)` + `*IT` (bẫy `§0.24`,
   enum `null` cũng cần kiểm).
2. **Timezone:** `resolveTimezone` validate bằng `ZoneId.of` ⇒ `400` mã mới `INVALID_TIMEZONE` (hoặc
   `VALIDATION_ERROR` + `errors[{field:"timezone"}]` — chốt cùng D4). **Không** sửa dữ liệu cũ đã lưu.
3. **D4:** `deactivatePlant` chặn khi còn Warehouse `ACTIVE`.
4. **D5:** `ItemService.create` resolve `unit` qua `UomLookupService` (ACTIVE); `update` từ chối đổi
   `unit`. Lần đầu nối `items.unit` với UOM master — **không** đổi cột (vẫn là code string).
5. **Test:** service + controller cho từng nhánh mới; `OrganizationRepositoryIT`/`ItemRepositoryIT` cho
   filter; regression: chạy lại `scripts/demo-capstone2/seed.sh` trên DB trắng (D5 có thể phá seed).

#### `G2-5` — Nghiệm thu + bàn giao

- `mvn -o clean verify` thật với Docker; số case **≥ baseline G2-0** (`R10`).
- Nghiệm thu mutation, ưu tiên các chỗ mock không thấy: bỏ lock bi quan (IT concurrency phải đỏ), bỏ
  filter `plantId` trong JPQL, đổi `DEFAULT_CONFLICT` sang mã khác **cùng 409**, bỏ vế `ACTIVE` khi
  resolve UOM.
- Smoke test HTTP thật cho A01–A22 phần thuộc BE (A18 thuộc `G1`; A21 trả lời theo D6) — ghi request/
  response/`X-Trace-Id`/giờ đo.
- Cập nhật `BE_RESPONSE_GD_02_*.md` (§7 bằng chứng + mục **chưa nghiệm thu**), `CLAUDE.md §0`
  (bản ghi mới), `module/sales|purchasing|inventory|organization/CLAUDE.md` (bất biến mới — tiếp số từ
  `B123`), `docs/api-guide-for-frontend.md`, `docs/roles-and-permissions.md`.

### 9.4 Breaking change dự kiến *(tuỳ quyết định §9.2)*

| Thay đổi | Breaking? | Phụ thuộc |
|---|---|---|
| Customer + `PERM_CUSTOMER_*` + path Supplier mới + `contactName`/`notes` | **Không** (additive) | D1 |
| `replaceDefaults` nullable | **Không** nếu theo khuyến nghị D2; **có** nếu mặc định `false` như FE đề xuất | D2 |
| `GET settings` thêm `plantId` | Không (tuỳ chọn); **có** nếu bỏ nhánh không-filter | §9.3 G2-3 #1 |
| Deactivate Plant còn kho ACTIVE ⇒ 422 | **Có** (request trước đây 200) | D4 |
| Item create với unit không có/INACTIVE trong UOM ⇒ lỗi; PATCH đổi unit ⇒ 422 | **Có** | D5 |
| Timezone rác ⇒ 400 | **Có** (sửa sai: trước đây lưu rồi 500 về sau) | — |
| Filter `search`/`status` trên list org | Nới lỏng (trước đây bị bỏ qua) | — |

### 9.6 Kết quả thực thi *(2026-10-14)*

Làm đúng §9.3; breaking change thực tế khớp §9.4 (6 mục "chặn mới", chi tiết `BE_RESPONSE_GD_02_2026-10-14.md §3`).
Bản ghi đầy đủ: `CLAUDE.md §0.50`.

| Còn mở | Trạng thái | Cần gì |
|---|---|---|
| ~~`leadTimeDays: 1.5` ⇒ lưu `1`~~ (FE A10) | ✅ **Đã sửa cho toàn hệ thống** (user chốt): `accept-float-as-int: false` + handler trả `errors[{field}]`. `5.0` cũng bị từ chối. Breaking mục 7 trong `_v2` | — (đã gửi trong `BE_RESPONSE_GD_02_2026-10-14_v2.md`) |
| FE nghiệm thu A01–A22 | Chờ FE | FE gửi kết quả; chưa đạt ⇒ trả lời bằng `_v2`, không mở file mới |
| A04/A14 live, A17 bằng tài khoản giới hạn, A19 (125 UOM), Q5, Q16 live | Chưa đo — ghi ở `BE_RESPONSE_GD_02_2026-10-14.md §7.3` | Xem file đó |

### 9.5 Ngoài phạm vi *(theo FE §1.3 + quyết định BE)*

CRUD Company; Import Excel; PO/GRN, giá, công nợ; nối `SalesOrder` với `Customer`; serial tracking;
UOM quy đổi; tự chọn kho khi thiếu mapping; `Idempotency-Key` cho master (D6); mọi thay đổi Auth/scope
`GLOBAL` (thuộc `G1`); đổi định dạng decimal sang string toàn hệ thống (Q12 — tác động mọi endpoint,
`BETaskDeliveryRules.md §5` lỗi #7).

---

## 10. `G3` — FE handoff Giai đoạn 3: Nhập Excel dữ liệu nền ✅ BE XONG (2026-10-14) — chờ FE nghiệm thu

Nguồn: `FE_HANDOFF_GD_03_2026-10-12.md` (v1, P1). FE/mock đã xong; BE phải (1) trả lời Q1–Q14 §5, (2) đối chiếu R01–R09 với API có sẵn, (3) chỉ code phần thiếu, (4) trả `BE_RESPONSE_GD_03_<ngày>.md` theo `BETaskDeliveryRules.md`. Mọi path/DTO/mã lỗi trong file FE là **[Đề xuất BE]**, không phải API đã có; FE chưa gọi live lần nào (§2: không có bằng chứng lỗi).

> 🔴 **Bảng câu hỏi, không phải danh sách việc** (`BETaskDeliveryRules.md §5` lỗi #1). Và lần này còn một điều nặng hơn: **repo đã có module nhập dữ liệu** (`module/dataimport`, `V59`/`V60`/`V64`, `/v1/import-runs`) mà file FE **không biết tới** — nó được dựng cho một hợp đồng khác. Quyết định lớn nhất của phase là reuse hay dựng song song (D1).

### 10.1 Đối chiếu yêu cầu FE ↔ code thật *(rà 2026-10-14 bằng đọc source, chưa gọi live)*

Phạm vi đã rà: `ImportRunController`, `ImportRunService`, `ImportRowApplyService`, `ImportRun`, `ImportTargetType`, `ImportTargetRegistry`, `pom.xml` (POI 5.5.1), `application.yml` (`multipart`, `data-import.max-rows`), `V60`. **Chưa rà:** `SpreadsheetReader`/`SpreadsheetTemplateWriter` (guard ZIP, kiểu ô), `ItemImportTargetHandler`, `ImportProfile`/`ColumnMapping`, `DataImportPermissionGuard`, `V59`/`V64` chi tiết — phải đọc ở `G3-0`.

| Chủ đề | FE yêu cầu | Hiện trạng | Lệch? |
|---|---|---|---|
| Luồng | `POST batches` (upload + validate một phát) → `POST .../commit` | `POST /v1/import-runs` (chỉ stage, **không** validate) → `POST .../validate` (cần **profile** ánh xạ cột) → `POST .../apply` | 🔴 Có — FE dùng **mẫu cố định** `omniplant-import-v1`, không có profile/mapping cột |
| Atomicity | **Tất cả hoặc không** (R03, A15, A18) | `apply` lặp từng dòng, mỗi dòng `REQUIRES_NEW`, có trạng thái `PARTIALLY_APPLIED`; javadoc ghi đó là "import contract" | 🔴 **Xung đột ngữ nghĩa trực diện** |
| Đối tượng | `items` + `suppliers` + `customers` | Chỉ `ImportTargetType.ITEM` | 🔴 Thiếu 2 handler (Supplier/Customer master đã có từ `G2`) |
| Quyền | READ+MANAGE **của chính loại** (`PERM_ITEM_*`/`SUPPLIER_*`/`CUSTOMER_*`), không permission import riêng | `PERM_DATA_IMPORT_READ/EXECUTE` (`V60`), một cặp cho mọi target | 🔴 Có — mô hình quyền khác hẳn |
| Giới hạn | 5 MiB, **500** dòng | `max-file-size` 10MB, `max-rows` **5000** | 🟡 Cấu hình |
| Retry | `Idempotency-Key` trên cả upload lẫn commit; commit lại trả receipt cũ | Chỉ `apply`, key tuỳ chọn, replay trả run cũ (đã đúng tinh thần) | 🟡 Thiếu ở upload |
| History | list/detail theo **owner** + quyền loại | `list` theo công ty (`PERM_DATA_IMPORT_READ`), **không** lọc owner | 🟡 Lệch (Q13) |
| Mẫu `.xlsx` | metadata `_OmniPlant` veryHidden, VI/EN, tải mẫu do **FE** tạo | Có `SpreadsheetTemplateWriter` (chưa rõ mẫu), ánh xạ theo tên cột người dùng chọn | 🟡 Cần đọc |
| Item type/tracking | enum + `NONE\|LOT` | `ItemService.createItem` có `serialTracked`, `type` | Trả lời |
| Trùng mã | unique `(company, loại, code)` kể cả INACTIVE | Đã có ở DB cho `items`/`suppliers`/`customers` (`G2`) | Trả lời (Q8) |
| `unit` | UOM ACTIVE thật, không tin sheet tham chiếu | `ItemService.create` đã resolve UOM ACTIVE từ `G2` (D5) | Trả lời |

### 10.2 Quyết định đã chốt với user *(2026-10-14, `AskUserQuestion` — cả 8 theo cột "Khuyến nghị BE"; D4/D6 không chặn nên theo đề xuất FE)*

| # | Quyết định | Khuyến nghị BE | Lý do | Chặn |
|---|---|---|---|---|
| **D1** | Reuse `module/dataimport` hay dựng module batch mới song song? | **Mở rộng `module/dataimport`**: thêm đường "mẫu cố định" cạnh đường profile (path mới `/v1/data-import/batches`, **giữ** `/v1/import-runs`); thêm target `SUPPLIER`/`CUSTOMER` vào `ImportTargetType` + 2 `ImportTargetHandler` | Tái dùng `ImportRun`/`ImportRow`, `SpreadsheetReader`, registry, audit. Dựng bảng `data_import_batches` riêng = hai hệ nhập cho cùng việc. Rủi ro: hai luồng cùng tồn tại — chấp nhận, đường cũ không đổi | `G3-1` |
| **D2** | Atomic all-or-nothing (FE) vs per-row partial (đang có) | **Atomic cho đường mới, giữ per-row cho đường cũ.** Commit mới chạy **một** transaction (500 dòng ⇒ nhỏ), bất kỳ dòng nào lỗi ⇒ rollback cả lô + trả `409 IMPORT_REVALIDATION_FAILED` | FE dựa vào atomic (A15, A18) và loại partial khỏi phạm vi. Đường cũ có sẵn khách (seed/script) — đổi ngữ nghĩa tại chỗ là breaking. Cần trạng thái `READY`/`INVALID`/`COMMITTED` riêng với enum cũ | `G3-1` |
| **D3** | Mô hình quyền | **Theo FE**: đường mới cần READ+MANAGE **của đúng loại**; kiểm trong service với `hasResourceAccess` đúng scope (Item: PLANT/COMPANY/GLOBAL; đối tác: COMPANY/GLOBAL). **Không** dùng `PERM_DATA_IMPORT_*` cho đường mới | FE đã thiết kế và chặn ghép chéo loại. `PERM_DATA_IMPORT_*` giữ nguyên cho đường cũ. ⚠️ cần đọc `DataImportPermissionGuard` xem có tái dùng được không | `G3-1` |
| **D4** | Path: `/v1/data-import/batches` (FE) | **Theo FE** | Không xung đột `/v1/import-runs`. Context-path `/api` đã ở ngoài | — |
| **D5** | Giới hạn 500 dòng / 5 MiB | **Theo FE** cho đường mới (cấu hình riêng `max-rows-fixed-template`, **không** hạ ngưỡng 5000 của đường cũ). Thêm giới hạn giải nén thực tế/entry trong parser | A04 đòi 500 dòng hợp lệ nhận, 501 bị từ chối. Q11 đòi BE công bố ngưỡng server cụ thể | `G3-1` |
| **D6** | Mẫu `.xlsx`: ai là nguồn sự thật | **BE chỉ đọc** theo `omniplant-import-v1` (sheet metadata `_OmniPlant` `A1/B1/C1`), **không** thêm endpoint tải mẫu (FE đã tự tạo, §3.2) | Đúng đề xuất FE "bản đầu không yêu cầu endpoint download riêng" | — |
| **D7** | History/detail: lọc theo **owner** (FE Q13) hay công ty | **Owner + quyền hiện tại của loại**, ID trái owner/scope ⇒ `404` (không lộ tồn tại) | Khớp UI FE; đường cũ lọc theo công ty — khác biệt có chủ đích, ghi rõ trong response | `G3-1` |
| **D8** | Cắt số lẻ & kiểu ô (Q10): server strict hơn FE (boolean/ô công thức) | **Strict**: formula ⇒ `FORMULA`, số ở cột mã/điện thoại ⇒ `TEXT_REQUIRED`, ngày/lỗi ⇒ `CELL_TYPE`; rich text ghép chữ; boolean ⇒ `CELL_TYPE` | FE đồng ý đồng bộ strict khi làm adapter. ⚠️ liên quan nợ `leadTimeDays` ở `G2` (§9.6): cùng loại lỗi "ép kiểu im lặng" | `G3-1` |

### 10.2a Kết quả *(2026-10-14)*

- `G3-0`/`G3-1`/`G3-2` xong; chi tiết + bất biến `B142`–`B148`: `CLAUDE.md §0.51`, `module/dataimport/CLAUDE.md`.
- **Khác kế hoạch (có chủ đích):** batch không `READY` ⇒ `STATE_CONFLICT`, `expectedVersion` lệch ⇒ `CONCURRENT_MODIFICATION`
  (mã sẵn có theo `error-handling.md §5.3`, không thêm `IMPORT_BATCH_NOT_READY`/`_VERSION_CONFLICT`); `X-Plant-Id` lệch ⇒ 409
  `STATE_CONFLICT` (quy ước sẵn có, không phải `400 PLANT_CONTEXT_MISMATCH`); plant khác công ty ⇒ 422 `RESOURCE_SCOPE_MISMATCH`.
- **Hai lỗi phát sinh, đã sửa trong phase:** `MaxUploadSizeExceededException` → 500 (nay 413); `committedAt`/`createdAt` lệch µs giữa
  response đầu và lần đọc lại (smoke lượt 1 bắt được).
- **Chưa nghiệm thu:** A21 nhánh "thu hồi assignment **sau** COMMITTED" chỉ đo bằng method-security test (smoke dùng user không quyền
  từ đầu); A26 "CPU/timeout" không có timeout riêng — chặn bằng ngưỡng kích thước; bộ mẫu `.xlsx` **do FE tạo** chưa từng được BE đọc thử
  (smoke dùng file tự dựng theo §3.2).

### 10.3 Gói công việc *(thứ tự thực thi; mỗi gói một commit theo `dev-workflow.md §6.6`)*

#### `G3-0` — Đọc nốt + baseline *(không code `src/main`)*

1. Đọc những file còn thiếu ở §10.1 (đặc biệt `SpreadsheetReader` — kiểu ô/ZIP guard; `ItemImportTargetHandler`; `DataImportPermissionGuard`; `V59`/`V64`). Cập nhật bảng §10.1 nếu có gì đổi.
2. `mvn -o clean verify` với Docker ⇒ baseline thật (số cuối đã ghi: **1320 unit / 162 class + 184 IT / 27 class**, `§0.50` — **đo lại**).
3. Chạy `/v1/import-runs` live (upload → validate → apply) trên DB dùng-một-lần để có request/response **thật** cho file trả lời (`B2`).
4. Chốt D1–D8 với user.

#### `G3-1` — Đường "mẫu cố định" atomic *(chặn bởi D1–D3, D5, D7, D8)*

- **Migration `V71`** *(V69/V70 đã dùng ở `G2`; ⚠️ `MANUFACTURING_PHASE_PLAN_P7_P11.md` phải đánh số lại)*: thêm vào `import_runs` các cột cho batch (`kind`, `locale`, `template_version`, `owner_user_id`, `version`, `created_ids` jsonb) + mở rộng `CHECK` status/`target_type`; **không backfill**, dòng cũ giữ nguyên. Thêm `idempotency_key` cho upload (unique `(owner_user_id, key)`).
- `ImportTargetType` + `SUPPLIER`, `CUSTOMER`; 2 handler gọi đúng service tạo master của `G2` (không INSERT thẳng — bài học `§0.45` #4).
- `SpreadsheetReader` đọc mẫu cố định + guard giải nén/entry/macro/external link.
- Service: `validate` (một phát, ghi batch `INVALID`/`READY`, **0 master**), `commit` (một transaction, revalidate UOM/mã/quyền, `expectedVersion`, retry COMMITTED trả receipt cũ **sau** authorization), `list`/`detail` theo owner.
- Mã lỗi theo FE §4 ↔ mã thật (đối chiếu `error-handling.md`; mã nghiệp vụ import là enum riêng `ImportErrorCode` đã có — **chốt mapping** ở `G3-0`).
- **Test:** unit (từng issue code, atomic, retry), method-security (deny+allow+`verify` chuỗi `PERM_*` cho **cả ba loại**, ghép chéo bị từ chối), controller, **`DataImportBatchIT`** (rollback thật khi dòng cuối lỗi; hai commit song song cùng mã ⇒ một lô thắng; 0 master khi validate — mock **không** kiểm được, `R7`; fixture **hai công ty**, bẫy `§0.46`), `FlywayMigrationIT.migrate_v71_*`.

#### `G3-2` — Nghiệm thu + bàn giao

- `mvn -o clean verify` thật; số case **≥ baseline** (`R10`).
- Nghiệm thu mutation, ưu tiên chỗ mock không thấy: đổi transaction commit về per-row (IT atomic phải đỏ), bỏ revalidate UOM ở commit, bỏ lọc owner ở list, đổi `IMPORT_REVALIDATION_FAILED` sang mã khác **cùng 409**.
- Smoke HTTP thật cho A01–A26 phần BE; **A21 (thu hồi quyền)** cần tài khoản giới hạn — ghi rõ nếu chưa đo.
- Docs: `BE_RESPONSE_GD_03_*.md` (Q1–Q14 + §7 bằng chứng + mục **chưa nghiệm thu**), `CLAUDE.md §0`, `module/dataimport/CLAUDE.md` (**chưa tồn tại — tạo**; bất biến tiếp từ `B141`), `docs/api-guide-for-frontend.md`, `docs/roles-and-permissions.md` nếu đổi quyền.

### 10.4 Breaking change dự kiến

| Thay đổi | Breaking? |
|---|---|
| Path `/v1/data-import/batches*` mới, `SUPPLIER`/`CUSTOMER` target mới, cột `import_runs` mới | **Không** (additive) nếu theo D1/D2 |
| Atomic thay per-row | **Có** nếu áp lên `/v1/import-runs` — **tránh** bằng D2 (chỉ đường mới) |
| Giới hạn 500 dòng | Chỉ đường mới |

### 10.5 Ngoài phạm vi *(theo FE §1.2)*

`.csv`/`.xls`/`.xlsm`, upsert/xoá qua file, partial import, import Company/Plant/Warehouse/UOM, tồn đầu kỳ/BOM/routing, giá/công nợ, tài khoản đăng nhập, job queue/`PROCESSING` (Q14), endpoint tải mẫu (D6), xem batch người khác của admin (Q13), thay Auth/email giai đoạn 1.

---

## 11. `G4` — FE handoff Giai đoạn 4: Tồn kho, lô và chuyển kho ✅ BE XONG (2026-10-15) — chờ FE nghiệm thu

> **Kết quả (2026-10-15).** Quyết định user qua `AskUserQuestion`: D1 **không** làm định giá; hết hạn chặn
> **chỉ ở luồng kho thủ công**; D8 **từ chối dòng trùng**; D7/D10 thêm `NO_CHANGE` + `RESERVED_STOCK`; và
> (phát sinh từ smoke) aggregate thêm `heldLotQuantity` + lô quá hạn vào `expiredQuantity`. D2–D6, D9 theo
> khuyến nghị — **trừ D5**: thiếu quyền ở một kho của lô trả **403**, không phải 404 (lô vẫn đọc được ở kho
> A nên 404 là sai nghĩa). Migration `V72`. Bất biến `B149`–`B154` (`module/inventory/CLAUDE.md`).
> `mvn -o clean verify` **1461 unit / 173 class + 202 IT / 29 class** (baseline đo lại trên worktree sạch:
> 1394 + 192). Mutation **7/7** bị giết. Smoke HTTP thật 4 lượt × 70–72 gọi, 0 lệch status. Bản ghi
> đầy đủ: `CLAUDE.md §0.52`. Phát hiện ngoài kế hoạch: `GET /movements` **đã có** filter `lotId` (§11.1
> R02 ghi sai); `issue` không kiểm hạn dùng (R04 "chưa rõ" → đã rõ, đã chặn ở luồng thủ công); và
> `postedAt` lệch µs giữa tạo và đọc lại (đã sửa, cùng lỗi §0.51 #3).

Nguồn: `FE_HANDOFF_GD_04_2026-10-12.md` (v1, P1). FE/mock đã xong 4 màn hình (Kho, Lô, Biến động, **Chuyển kho mới**); BE phải (1) trả lời Q1–Q14 §5, (2) đối chiếu R01–R12 với API có sẵn, (3) chỉ code phần thiếu, (4) trả `BE_RESPONSE_GD_04_<ngày>.md` theo `BETaskDeliveryRules.md`. FE chưa gọi live lần nào; mọi path/DTO/mã lỗi là **[Đề xuất BE]**.

> 🔴 **Bảng câu hỏi, không phải danh sách việc** (`BETaskDeliveryRules.md §5` lỗi #1). Và lần này phạm vi FE mô tả **rộng hơn nhiều** so với tên phase: ngoài chuyển kho (thứ duy nhất FE ghi là "bị chặn"), file còn đòi **định giá tồn kho** (giá bình quân, giá trị tồn, snapshot giá trên từng movement) — một năng lực repo **chưa từng có** và `P3` đã **cố ý từ chối** (`CLAUDE.md §0.31` quyết định #2). Phạm vi phải chốt trước (D1), không suy ra từ độ dài file FE.

### 11.1 Đối chiếu yêu cầu FE ↔ code thật *(rà 2026-10-15 bằng đọc source, chưa gọi live)*

Phạm vi đã rà: `InventoryController` (6 endpoint), `InventoryLotController` (3), `InventoryLotService.changeStatus`, `MovementType`, `StockReceiveRequest`, `StockAdjustRequest`, `StockMovementResponse`, `StockBalanceAggregateResponse`, `StockBalance` (cột), `InventoryLot.expiresAt`, grep `unit_cost`/`@Lock` trong `module/inventory`. **Chưa rà:** `InventoryMovementService` chi tiết (khoá, thứ tự check), `MaterialReservationService` (R11), nhánh `adjust` với reservation, `InventoryMapper` aggregate, migration `V8`/`V16`/`V56`.

| # | FE yêu cầu | Hiện trạng code | Loại |
|---|---|---|---|
| R01 | Aggregate 1 dòng Item+Warehouse, 6 lượng | **Đã có** `GET /v1/inventory/balances/aggregate` đủ 6 lượng + `lotCount`. Thiếu `averageCost`/`stockValue`/`version`/`search` | Trả lời + **CODE** (`search`; giá trị theo D1) |
| R02 | Lot detail/history theo `lotId`+`warehouseId` | **Đã có** `GET /lots/{id}?warehouseId=`. `GET /movements` **không** có filter `lotId` | **CODE nhỏ** (filter `lotId`) |
| R03 | Receive có `unitCost` bắt buộc + `expiresAt` | `StockReceiveRequest` **không có** `unitCost` lẫn `expiresAt`. `InventoryLot.expiresAt` có cột nhưng **không có đường ghi qua API** (đúng phát hiện `§0.46`, `set-lot-expiry.sh` phải chạy SQL) | **CODE** (`expiresAt`); `unitCost` theo D1 |
| R04 | Issue chỉ trừ free AVAILABLE chưa hết hạn, an toàn đồng thời | Gate lot status có (`B3`). **Chưa rõ** có loại lot hết hạn và có khoá khi hai issue tranh nhau không — `grep @Lock` trong `module/inventory/repository` chỉ ra **`ItemRepository`** (từ `G2`) | Rà ở `G4-0` + `*IT` concurrency |
| R05 | Count tuyệt đối + `expectedVersion` | **Không có.** Chỉ `POST /adjust` nhận `quantityDelta` có dấu. `StockBalance` có `@Version` (từ `BaseEntity`) ⇒ đủ làm `expectedVersion` | **CODE** — endpoint mới (D3) |
| R06 | Chuyển kho atomic, cùng Plant, cặp OUT/IN | **Không tồn tại** (grep `transfer` = 0). `MovementType` không có `TRANSFER_OUT`/`TRANSFER_IN` ⇒ đụng `CHECK` constraint (`coding-rules.md §11.3`) | **CODE** — aggregate mới |
| R07 | Đổi status: từ chối reserved / no-op / expired→AVAILABLE; kiểm scope mọi vị thế | 🔴 `changeStatus` đổi `InventoryLot.status` — tức **đã là global** (Q6) — nhưng `@PreAuthorize` chỉ kiểm **một** `warehouseId` trong body ⇒ người có quyền ở kho A đổi được chất lượng lô đang nằm ở kho B ngoài phạm vi. **Không** thấy check reservation, no-op, hay expired→AVAILABLE. Quyền là `PERM_INVENTORY_MOVE`, FE ghi `PERM_INVENTORY_MANAGE` | **CODE** (lỗ hổng scope + 3 check) |
| R08 | Decimal ≤ 6 số lẻ, không làm tròn âm thầm | Cột `NUMERIC(19,6)`; request nhận `BigDecimal` không giới hạn scale ⇒ Postgres **làm tròn** `1.2345678` thành `1.234568` mà không báo | **CODE** (`@Digits(integer=13, fraction=6)`) — cùng họ lỗi "ép kiểu im lặng" `§0.50` #6 |
| R09 | Idempotency theo actor+command+key | Có theo `(key, movement_type)` (`B69`), **không** theo actor (nợ `C` ở Phụ lục) | Trả lời; transfer/count làm key riêng theo tài liệu |
| R10 | Snapshot giá trên movement | **Không có** cột giá nào trên `stock_movements` | Theo D1 |
| R11 | WO reserve/issue/output/QC dùng cùng ledger | Đã đúng thiết kế (`B1`–`B6`, `B62`) | Trả lời |
| R12 | Actor/time/trace do server ghi | `created_by`/`created_at`/`trace_id` có trên `stock_movements`; `StockMovementResponse` **không** trả `createdBy`/`traceId` | **CODE nhỏ** (`createdByUsername` batch, `traceId`) |
| — | `GET /inventory/reservations` | Không có endpoint đọc reservation theo kho/item | **CODE** (read-only) |
| — | `GET /inventory/movements/{id}` | Không có | **CODE** (read-only) |
| — | Decimal là **chuỗi** trên wire (Q13) | Toàn repo trả **JSON number** (`G2` Q12) | Trả lời — **không đổi** (D9) |

### 11.2 Quyết định phải chốt với user trước khi code

| # | Quyết định | Khuyến nghị BE | Lý do | Chặn |
|---|---|---|---|---|
| **D1** | Có làm **định giá tồn kho** (moving average theo Item+Warehouse, `unitCost` trên receive, `averageCost`/`stockValue`, snapshot giá trên movement) trong `G4` không? | **Không làm ở `G4`** — tách thành phase riêng, gộp với `P7` (WIP valuation) | Đây là lớp actual-costing mà `P3` đã từ chối có chủ đích. Làm nó đụng **mọi** đường ghi tồn kho (receive/issue/adjust/QC/goods receipt/material issue/production receipt), cần migration cột giá trên `stock_balances` + `stock_movements`, chính sách cho dữ liệu lịch sử không có giá (Q12), làm tròn (Q10). Một phase FE handoff không phải chỗ đặt nó. FE đã xác nhận chưa trả lời thì **không chặn mock**; trả `null` cho các field giá là hợp đồng hợp lệ theo chính đề xuất của FE ("thiếu giá là null, không phải 0") | Cả phase |
| **D2** | Chuyển kho: cùng Plant, không in-transit, không approval, không sửa/huỷ sau post (Q2) | **Theo FE** | Đúng phạm vi FE đã duyệt; không có gì cần đàm phán | `G4-2` |
| **D3** | Count tuyệt đối: endpoint **mới** `POST /v1/inventory/counts`, giữ nguyên `POST /adjust` (Q3) | **Theo FE** — count tính `delta = counted − quantity` dưới khoá rồi gọi đúng đường `adjust` hiện có (`ADJUST_IN`/`ADJUST_OUT`) | Không đổi ngữ nghĩa API cũ (breaking). Không thêm `MovementType` mới cho count | `G4-3` |
| **D4** | `expectedVersion` bắt buộc cho count (Q4) — version của **dòng `stock_balances`** nào? | Version của **đúng vị thế** (Item+Warehouse+Lot, hoặc không lô); trả `version` trên `StockBalanceResponse` | Aggregate gộp nhiều dòng nên không có một version duy nhất. Count theo lot ⇒ phải đếm theo vị thế, đúng như FE mô tả bước 5 | `G4-3` |
| **D5** | Lot status: global (đã đúng code) **và** kiểm scope **mọi** kho có vị thế của lô (Q6, R07) | **Có cả hai.** Thiếu quyền ở bất kỳ kho nào ⇒ 404 (không lộ kho ngoài scope) | Đây là sửa **lỗ hổng phân quyền thật** ở §11.1 R07, không phải tính năng mới | `G4-1` |
| **D6** | Đổi status: quyền `PERM_INVENTORY_MOVE` (code) hay `PERM_INVENTORY_MANAGE` (FE)? | **Giữ `MOVE`**, trả lời FE | Đổi quyền = đổi cấu hình role mọi môi trường (cùng lý do `G2` D7). Sau `V57`, `PERM_INVENTORY_MANAGE` là quyền **thiết lập kho**, không phải chất lượng lô | Không (chỉ trả lời) |
| **D7** | Đổi status bị chặn khi lô còn **reservation `ACTIVE`** (R07) | **Có**, `409` mã mới hoặc tái dùng `STATE_CONFLICT` — chốt ở `G4-0` theo `error-handling.md §5.3` | Đổi lô đang được giữ cho WO sang `HOLD` âm thầm làm WO đó không xuất được nữa | `G4-1` |
| **D8** | Transfer: cho dòng trùng vị thế và kiểm **tổng** toàn phiếu (Q9) | **Từ chối dòng trùng** (`VALIDATION_ERROR` trỏ `lines[i]`) | Đơn giản hơn và loại bỏ hẳn lớp lỗi "mỗi dòng < free nhưng tổng > free". FE nghiêng về "cho trùng + kiểm tổng" — cả hai đều đúng tinh thần; reject rẻ hơn để test | `G4-2` |
| **D9** | Decimal trên wire là chuỗi (Q13) | **Không** — giữ JSON number như toàn hệ thống | Đổi kiểu số cho riêng endpoint mới là hai hợp đồng trong một API; đổi toàn hệ thống là ngoài phạm vi (cùng lý do `G2` Q12) | — |
| **D10** | Mã lỗi (Q14) | Ánh xạ vào mã **sẵn có** theo `§5.3`; chỉ thêm mã khi không có câu trả lời | Ví dụ ánh xạ dự kiến: `WAREHOUSE_INACTIVE`/`ITEM_INACTIVE`/`PLANT_INACTIVE` → `RESOURCE_INACTIVE` (422); `PLANT_SCOPE_MISMATCH` → `RESOURCE_SCOPE_MISMATCH` (422); `STALE_VERSION` → `CONCURRENT_MODIFICATION` (409); `LOT_ITEM_MISMATCH` → `RESOURCE_SCOPE_MISMATCH`; `DECIMAL_PRECISION_EXCEEDED` → `VALIDATION_ERROR` + `errors[{field}]`. Ứng viên mã mới: `SAME_WAREHOUSE`?, `NO_CHANGE`?, `RESERVED_STOCK`? — chốt từng cái | Tất cả gói |

### 11.3 Gói công việc *(thứ tự thực thi; mỗi gói một commit theo `dev-workflow.md §6.6`)*

#### `G4-0` — Đọc nốt + baseline + trả lời §5 *(không code `src/main`)*

1. Đọc những phần "chưa rà" ở §11.1 — đặc biệt khoá đồng thời của `InventoryMovementService.issue` và `adjust` (R04), check hết hạn ở gate xuất, `MaterialReservationService` (reservation theo vị thế hay theo item).
2. `mvn -o clean verify` với Docker ⇒ baseline thật (số cuối đã ghi: **1394 unit / 169 class + 192 IT / 28 class**, `§0.51` — **đo lại**).
3. Chạy live các endpoint FE liệt kê ở §3.1 trên DB dùng-một-lần để có request/response **thật** cho file trả lời (`B2`).
4. Chốt D1–D10 với user.

#### `G4-1` — Lot status: phân quyền mọi vị thế + 3 check *(chặn bởi D5, D7)*

- Guard mới: resolve mọi `warehouseId` có `stock_balances` của lô, yêu cầu `PERM_INVENTORY_MOVE` ở **tất cả**; thiếu ⇒ 404.
- Từ chối: cùng trạng thái (no-op), `expiresAt <= now` ⇒ `AVAILABLE`, còn reservation `ACTIVE` trên lô.
- **Test:** method-security (deny khi một kho ngoài scope), service từng nhánh, **`*IT`** cho query "mọi kho của lô" (fixture lô nằm ở **hai** kho thuộc **hai** plant — bẫy `§0.46`).

#### `G4-2` — Chuyển kho *(chặn bởi D2, D8)*

- **Migration `V72`** *(V69–V71 đã dùng ở `G2`/`G3`; ⚠️ `MANUFACTURING_PHASE_PLAN_P7_P11.md` phải đánh số lại)*: bảng `stock_transfers` + `stock_transfer_lines` (`code` sinh ở `@PrePersist` — `B68`, `idempotency_key` + `payload_hash`, `UNIQUE`), `MovementType` + `TRANSFER_OUT`/`TRANSFER_IN` + sửa `chk_stock_movements_type` (`§11.3`), cột `stock_movements.transfer_line_id` nếu cần nối cặp.
- Service: validate trước mọi ghi (`C9`) — hai kho `ACTIVE`, khác nhau, cùng Plant `ACTIVE`, 1–100 dòng, không trùng vị thế; **khoá** các vị thế nguồn theo thứ tự xác định (`balance_id` tăng dần) để không deadlock; lô giữ nguyên mã/ngày/hạn; chỉ chuyển phần free AVAILABLE chưa hết hạn; một transaction cho cả phiếu.
- 4 endpoint: `POST/GET /v1/inventory/transfers`, `GET /v1/inventory/transfers/{id}`. Cross-check `X-Plant-Id` (endpoint nêu kho ⇒ suy plant; `§5.6.1`).
- Quyền: tái dùng `PERM_INVENTORY_MOVE`/`_READ` ⇒ **không** permission mới, `docs/roles-and-permissions.md` không đổi.
- **Test:** unit mỗi nhánh từ chối + `verifyNoInteractions` (`R5`), idempotency (`R9`), method-security, controller, **`StockTransferIT`** (rollback thật khi dòng cuối lỗi; hai transfer tranh free cuối ⇒ không âm; lô hai kho), `FlywayMigrationIT` cho `CHECK` mới.

#### `G4-3` — Kiểm đếm tuyệt đối *(chặn bởi D3, D4)*

- `POST /v1/inventory/counts` — khoá dòng `stock_balances`, so `expectedVersion`, `delta = counted − quantity`; `0` ⇒ từ chối không ghi; `counted < reservedQuantity` ⇒ từ chối; delta ≠ 0 đi qua đường `adjust` hiện có (không `MovementType` mới). `Idempotency-Key` bắt buộc theo FE.
- `version` thêm vào `StockBalanceResponse` (additive).

#### `G4-4` — Read model + validation nhỏ

- `GET /movements`: filter `lotId`, trả `createdByUsername` (batch, `C14`) + `traceId`; `GET /movements/{id}`.
- `GET /balances/aggregate`: filter `search` (code/name, `cast(... as string)` + `*IT` — bẫy `§0.24`).
- `GET /v1/inventory/reservations` (read-only, phân trang).
- `StockReceiveRequest.expiresAt` (chỉ khi tạo **lô mới**; quá khứ ⇒ 400) — đóng luôn lý do tồn tại của `scripts/demo-capstone2/set-lot-expiry.sh`.
- `@Digits(fraction = 6)` cho mọi quantity trên request inventory (R08).

#### `G4-5` — Nghiệm thu + bàn giao

- `mvn -o clean verify` thật; số case **≥ baseline** (`R10`).
- Nghiệm thu mutation, ưu tiên chỗ mock không thấy: bỏ khoá vị thế nguồn (IT tranh chấp phải đỏ), bỏ transaction chung (IT rollback phải đỏ), guard status chỉ kiểm một kho, đổi mã lỗi sang mã khác **cùng HTTP status**.
- Smoke HTTP thật A01–A26 phần thuộc BE; ca định giá (A03, A04, A13, A14 phần giá trị, A25) ghi **"ngoài phạm vi theo D1"** nếu D1 = không làm.
- Docs: `BE_RESPONSE_GD_04_*.md`, `CLAUDE.md §0`, `module/inventory/CLAUDE.md` (bất biến mới tiếp từ `B148`), `docs/api-guide-for-frontend.md`.

### 11.4 Breaking change dự kiến

| Thay đổi | Breaking? |
|---|---|
| Transfer, count, reservations, movement detail, filter `lotId`/`search`, field `version`/`createdByUsername`/`traceId`/`expiresAt` | **Không** (additive) |
| Lot status từ chối khi kho khác ngoài scope / reserved / no-op / expired→AVAILABLE | **Có** — request trước đây 200 nay bị chặn (sửa sai) |
| Quantity quá 6 số lẻ ⇒ 400 trên endpoint inventory | **Có** — trước đây bị làm tròn im lặng (sửa sai) |
| Valuation | Chỉ khi D1 = làm; thêm field `null` là additive, bắt buộc `unitCost` trên receive là **breaking** |

### 11.5 Ngoài phạm vi *(theo FE §1.2 + quyết định BE)*

Chuyển khác Plant, in-transit, sửa/huỷ/đảo phiếu đã post, chuyển reservation, serial tracking, FIFO, FX/VAT, sổ kế toán, full costing, import tồn đầu kỳ, decimal-as-string toàn hệ thống (D9), idempotency theo actor cho ledger cũ (nợ `C`), định giá tồn kho nếu D1 = không làm.

---

## 12. `G5` — FE handoff Giai đoạn 5: Định mức, quy trình và công suất ✅ BE XONG (2026-10-16) — chờ FE nghiệm thu

> **Kết quả (2026-10-16):** D1–D12 đã chốt — D1 giữ `quantityPer`, D2/D4/D6/D11 theo khuyến nghị, D3 `effectiveFrom` là
> Instant, **D5/D7/D8 giữ thiết kế cũ**, D9 chia theo ngày, **D10 chặn 409**, D12 làm trong G5. Mọi gói `G5-0`..`G5-6` xong
> trong **một** commit (một migration `V73` cho cả BOM và routing). Lệch so với kế hoạch: (1) tải theo kỳ là endpoint mới
> `GET /plants/{P}/capacity/loads` cạnh `capacity-board` cũ; (2) điều chỉnh cho cả `IN_PROGRESS` (công đoạn chưa báo sản lượng);
> (3) đường mới từ chối thành phần FINISHED_GOOD; (4) release vẫn **không** chia `capacityUnits` (xem trước có chia). Bản ghi
> đầy đủ: `CLAUDE.md §0.53`; trả lời FE: `BE_RESPONSE_GD_05_2026-10-16.md`.

Nguồn: `FE_HANDOFF_GD_05_2026-10-13.md` (v1, P1, 541 dòng). FE/mock đã xong BOM (form rộng, phiên bản, cây, sao chép), **nhập BOM Excel**, quy trình có phiên bản, Ca/Lịch/Trung tâm, bảng công suất + **xem trước lịch công đoạn** + điều chỉnh. BE phải (1) trả lời Q1–Q19 §5, (2) đối chiếu R01–R15 với API có sẵn, (3) chỉ code phần thiếu, (4) trả `BE_RESPONSE_GD_05_<ngày>.md` theo `BETaskDeliveryRules.md`. FE chưa gọi live lần nào; mọi path/DTO/mã lỗi là **[Đề xuất BE]**.

> 🔴 **Đây là handoff khác hẳn ba lần trước.** `G2`–`G4` chủ yếu là "thêm thứ còn thiếu". `G5` đề xuất **đảo ngược 4 quyết định thiết kế đã chốt có lý do** trong repo: routing company-scoped (`F4`), ca một khung giờ (`C2-7`), work center không có `shiftIds[]` (`C2-7`), điều chỉnh lịch chỉ cảnh báo chứ không chặn (`C2-8`, `B90`) — cộng `outputQuantity` của BOM mà `F10` đã **loại khỏi phạm vi có chủ đích** (nợ `E`). Mỗi cái là một quyết định của user, **không** suy ra từ việc FE đã làm mock theo hướng khác. Đây cũng là lý do số quyết định ở §12.2 lớn hơn mọi phase trước.
>
> 🔴 **Bảng câu hỏi, không phải danh sách việc** (`BETaskDeliveryRules.md §5` lỗi #1). FE tự ghi: "nếu server đã có chức năng tương đương, trả contract/mapping thay vì tạo API trùng".

### 12.1 Đối chiếu yêu cầu FE ↔ code thật *(rà 2026-10-15 bằng đọc source, chưa gọi live)*

Phạm vi đã rà: cột của `BomHeader`/`BomLine`/`RoutingHeader`/`RoutingOperation`/`Shift`/`WorkCalendar`/`WorkCalendarException`/`WorkCenter`; mapping của `BomController`, `RoutingController`, `ShiftController`, `WorkCalendarController`, `WorkCenterController`, `CapacityController`; `CapacityUnitType`; `ImportTargetType`. **Chưa rà:** có `expectedVersion` trên PATCH master nào không; guard deactivate ca/lịch đang được tham chiếu; check vòng BOM có chạy lúc activate không (`B8`); `CapacityBoardService` chia tải theo ngày; `ScheduleAdjustmentRequest`; `WorkingWindowCalculator` với ca qua đêm + ngày nghỉ (FE A21).

| Nhóm | FE yêu cầu | Hiện trạng code | Loại |
|---|---|---|---|
| **BOM** header | `bomCode`, `versionNumber`, `outputQuantity`, `outputUom`, `effectiveFrom`, `lockVersion` | Chỉ `revision` (string 40), `status`, `description`. **Không** có code, lượng đầu ra, ngày hiệu lực, số phiên bản | 🔴 **Quyết định** (D1–D3) |
| BOM dòng | `componentQuantity` theo lô đầu ra; `quantityPer` = projection; `scrapRate` scale 8 | `quantity_per NUMERIC(19,6)` per-unit; `scrap_rate NUMERIC(9,6)` | Theo D1, D11 |
| BOM ghi | **Một** `PUT bom-drafts` lưu header + mọi dòng atomic | 11 endpoint cũ, header và từng dòng là **request riêng** — không atomic ở mức "một lần lưu form" | **CODE** (D4) |
| BOM vòng đời | copy → DRAFT mới, `/versions`, `POST /deactivate`, chỉ DRAFT sửa được | Có `activate`, `DELETE` (deactivate), `ensureDraft` (`B73`). **Không** có copy, versions, POST deactivate | **CODE** |
| BOM vòng | `CIRCULAR_BOM` lúc activate | `B8` ghi có check vòng — chưa rà nó chạy ở `activate` hay ở thêm dòng | Rà ở `G5-0` |
| **Routing** phạm vi | `/plants/{plantId}/routings`, version theo **Plant + sản phẩm** | 🔴 **Company-scoped** (`/companies/{companyId}/routings`, cột `company_id`), 1 `ACTIVE`/item **trong company** (`F4`) | 🔴 **Quyết định** (D5) |
| Routing công đoạn trước | `predecessorOperationIds` | **Không có** — đây đúng là nợ B, `C2-8` đã tách sang `C2-8b` chưa làm | **CODE** nếu D6 = làm |
| Routing ghi/phiên bản | `PUT routing-drafts` cả graph, copy, `/versions`, `effectiveFrom`, `versionNumber` | Chỉ create + activate + `DELETE`; **không** có PATCH, copy, versions, `effectiveFrom`; `routing_version` string | **CODE** |
| **Ca** | `workingIntervals[]` (nhiều khung), `effectiveFrom/To`, `workingMinutes` server tính | 🔴 **Một** khung `start_time`/`end_time` + `breaks[]` — quyết định có chủ đích của `C2-7`. **Không** có hiệu lực trên ca | 🔴 **Quyết định** (D7) |
| Lịch | 7 weekday, ngày nghỉ có tên, hiệu lực | **Đã có** (`effective_from/to`, `WorkCalendarException.reason`) | Trả lời + mapping |
| **Trung tâm** | `capacityUnitType` MACHINE/LINE/LABOR_TEAM, `capacityUnits` 1..999, `calendarId`, **`shiftIds[]`** | **Đã có** loại + số đơn vị + `work_calendar_id`. 🔴 **Không** có `shiftIds[]` — `C2-7` chốt "chỉ một cột `work_calendar_id`" | 🔴 **Quyết định** (D8) |
| Master lifecycle | `POST /{id}/status` + `expectedVersion` | Có `/activate` + `/deactivate` + `DELETE` riêng | Trả lời — mapping, **không** thêm endpoint `/status` trùng |
| **Bảng công suất** | Kỳ `[from, to]`, tải tính theo **phần giao nhau với kỳ** (segment) | `GET /plants/{id}/capacity-board?from&to` có. 🔴 `C2-8` §0.30 #4: **toàn bộ** thời lượng gán cho ngày bắt đầu — đúng thứ FE R11 gọi là sai ("không gán toàn tải vào mọi tuần") | 🔴 **Quyết định** (D9) |
| Xem trước lịch | `POST /plants/{id}/capacity/preview` — không ghi gì | **Không tồn tại**. `WorkCalendarLookupService.computeEndInstant` (`C2-8`) là lõi tái dùng được | **CODE** |
| Điều chỉnh | Đè lên công đoạn trước/sau ⇒ **từ chối** | 🔴 `B90`: **không chặn**, chỉ trả cờ `sequenceConflict` — quyết định có chủ đích của `C2-8` | 🔴 **Quyết định** (D10) |
| **Nhập BOM Excel** | Mẫu `omniplant-bom-v1`, gom dòng theo mã BOM, tạo N BOM `DRAFT` atomic | Hạ tầng batch của `G3` (`ImportTargetType` = `ITEM`/`SUPPLIER`/`CUSTOMER`, commit một transaction, lịch sử theo owner) **tái dùng được**; mẫu khác (nhóm nhiều dòng thành một BOM) | **CODE** — phụ thuộc D1 (lượng đầu ra) |
| Decimal | Chuỗi trên wire | Toàn repo trả JSON number (`G2` Q12, `G4` D9) | Trả lời — **không đổi** |

### 12.2 Quyết định phải chốt với user trước khi code

| # | Quyết định | Khuyến nghị BE | Lý do | Chặn |
|---|---|---|---|---|
| **D1** | BOM có `outputQuantity` + `componentQuantity` theo lô, `quantityPer` thành projection (Q1, Q2, Q6)? | **Có, nhưng lưu `quantityPer` như hiện nay** — `outputQuantity` là cột mới mặc định `1` cho mọi BOM cũ; `componentQuantity = quantityPer × outputQuantity` tính lúc đọc | Nợ `E` bị `F10` loại vì "đổi công thức nổ BOM, đổi số MRP lẫn component line của mọi WO". Lưu `quantityPer` làm nguồn giữ MRP, WO snapshot, costing roll-up, tree **không đổi một chữ số nào** — chỉ thêm cách nhập/hiển thị theo lô. Hướng ngược lại (lưu theo lô, chia ra khi đọc) đụng làm tròn ở **mọi** consumer | `G5-1`, `G5-5` |
| **D2** | `bomCode` + `versionNumber` (server cấp, tăng theo Company + sản phẩm) cạnh `revision` (Q3) | **Có** — migration backfill `versionNumber` theo `created_at` từng family, `bomCode` sinh từ mã item + revision cho dữ liệu cũ; `revision` giữ là nhãn | Không parse `revision` cũ thành số (FE cũng nghiêng về vậy) | `G5-1` |
| **D3** | `effectiveFrom` trên BOM; `null` = không trì hoãn; ngày tính theo timezone nào (Q4, Q5) | **Có, `null` = hiệu lực ngay**; ngày so theo timezone **Company**… nhưng `companies` **không có cột timezone** (chỉ `plants` có) ⇒ đề xuất so theo **UTC** hoặc thêm cột — hỏi user | FE giả định có timezone Company; repo không có | `G5-1` |
| **D4** | Atomic SAVE `PUT /companies/{C}/bom-drafts` cạnh 11 endpoint cũ (Q7) | **Có, song song** — giữ nguyên API cũ (cùng tiền lệ `G2` D1, `G3` D1) | Đổi tại chỗ là breaking cho seed script và FE adapter cũ | `G5-1` |
| **D5** | Routing chuyển sang **Plant-scoped** | 🔴 **Không đổi phạm vi** — giữ company-scoped, thêm filter `plantId` suy từ work center của công đoạn nếu FE cần lọc | Đổi `company_id` → `plant_id` là migration phá dữ liệu (routing cũ không có plant; `B_wc2` chỉ khoá một routing vào **một** plant qua work center), đổi `F4` B48–B52, đổi `RoutingLookupService` mà MRP/WO đang gọi. Lợi ích FE (lọc theo nhà máy) đạt được bằng filter | `G5-2` |
| **D6** | Làm `predecessorOperationIds` (nợ B / `C2-8b`) | **Có** — bảng nối `routing_operation_predecessors`, kiểm "trước phải có `sequence` nhỏ hơn", không vòng | Đây là nợ đã ghi tên sẵn, FE là consumer đầu tiên. Preview vẫn tuần tự theo `sequence` (Q17) nên **không** đụng `WorkOrderService.scheduleOperations` | `G5-2` |
| **D7** | Ca có **nhiều** khung làm việc + hiệu lực | 🔴 **Không** — giữ một khung + `breaks[]` (`C2-7`); ca "08–12 và 13–17" biểu diễn bằng một khung 08–17 có nghỉ 12–13. Hiệu lực đã nằm ở **lịch** | Đổi sang nhiều khung viết lại `ShiftTimeWindow.containsInterval` + `WorkingWindowCalculator` — chính chỗ `§0.29` #2 cảnh báo sửa là ảnh hưởng mọi tính toán giờ làm. FE A20/A21 đều biểu diễn được bằng mô hình hiện tại. Trả FE mapping | `G5-3` |
| **D8** | Trung tâm có `shiftIds[]` (tập con ca của lịch) | 🔴 **Không** — trung tâm làm theo **mọi** ca của lịch gán cho nó (`C2-7`); cần tập ca khác thì tạo lịch khác | Thêm `shiftIds[]` tạo hai nguồn sự thật cho "trung tâm làm giờ nào" và mọi chỗ đọc lịch phải lọc thêm | `G5-3` |
| **D9** | Bảng công suất tính tải theo **phần giao nhau với kỳ** thay vì gán cả công đoạn cho ngày bắt đầu (R11) | **Có** — đây là chỗ FE đúng: một công đoạn 3 ngày đang bị dồn cả vào ngày đầu, báo quá tải giả ngày 1 và trống giả ngày 2–3. `C2-8` ghi rõ đó là đơn giản hoá có chủ đích, không phải đúng | Đổi số trên một endpoint đang chạy ⇒ **breaking về số liệu**, cần ghi rõ cho FE | `G5-4` |
| **D10** | Điều chỉnh đè công đoạn trước/sau ⇒ **từ chối** (đảo `B90`) | **Hỏi user — hai phương án hợp lý.** (a) Theo FE: chặn `409`. (b) Giữ `B90`: cho lưu + cờ `sequenceConflict` (đã có trên response) | `B90` chọn "manager tự xử lý" có chủ đích; FE chọn chặn. Không có đúng/sai kỹ thuật — là chính sách | `G5-4` |
| **D11** | `scrapRate` scale 8 (Q19) | **Không** — giữ scale 6 (`NUMERIC(9,6)`), từ chối input > 6 số lẻ bằng `@Digits` (cùng tinh thần `G4` R08) | `0.000001%` không phải nhu cầu nghiệp vụ thật; đổi scale đụng MRP gross formula + cột snapshot WO | `G5-1` |
| **D12** | Nhập BOM Excel trong `G5` hay tách phase (FE ghi "Mới") | **Trong `G5`**, nhưng **sau** `G5-1` (cần `outputQuantity` + atomic save) | Hạ tầng `G3` làm phần khó (batch, commit atomic, owner, idempotency); phần mới là gom nhóm dòng + validate theo nhóm | `G5-5` |

> Các câu FE còn lại trả lời bằng hành vi có sẵn hoặc theo hướng FE nghiêng: Q8 (lịch nằm trong hiệu lực ca — **mất nghĩa nếu D7 = không**, ca không có hiệu lực), Q9 (trung tâm còn routing `ACTIVE` ⇒ chặn deactivate — **có**), Q10 (sửa ca/lịch không tự dời WO đã lên lịch — **đúng hành vi hiện tại**, lịch chỉ sinh lúc `release()`, `B89`), Q11 (90 ngày inclusive), Q12 (chỉ timezone không DST lượt đầu), Q13 (đọc lịch sử nhập chỉ cần READ — giống `G3`), Q14 (retention theo `G3`), Q15 (ngưỡng 85%), Q16 (chỉ PLANNED/RELEASED chưa bắt đầu), Q17 (preview tuần tự theo `sequence`), Q18 (một nháp mở/routing family; BOM nhiều nháp).

### 12.3 Gói công việc *(thứ tự thực thi; mỗi gói một commit theo `dev-workflow.md §6.6`)*

> Migration tiếp theo là **`V73`** (`V69`–`V72` đã dùng ở `G2`–`G4`). ⚠️ `MANUFACTURING_PHASE_PLAN_P7_P11.md` lại phải đánh số lại thêm một lần.

#### `G5-0` — Đọc nốt + baseline + trả lời §5 *(không code `src/main`)*

1. Đọc phần "chưa rà" §12.1 — ưu tiên: check vòng BOM chạy ở đâu (A08), `CapacityBoardService` chia tải (D9), `WorkingWindowCalculator` với ca qua đêm + ngày nghỉ theo ngày bắt đầu ca (A21), guard deactivate ca/lịch đang được dùng (A23).
2. `mvn -o clean verify` với Docker ⇒ baseline thật (số cuối đã ghi: **1461 unit + 202 IT**, `G4` — **đo lại**).
3. Chạy live các endpoint FE liệt kê ở §3.2 + capacity-board trên DB dùng-một-lần để có request/response **thật** (`B2`).
4. Chốt D1–D12 với user.

#### `G5-1` — BOM: lượng đầu ra, phiên bản, atomic save, copy *(chặn bởi D1–D4, D11)*

- Migration: `bom_headers` + `bom_code`, `version_number`, `output_quantity` (DEFAULT 1), `effective_from`; backfill theo D2; `UNIQUE (company_id, bom_code)` và `(company_id, parent_item_id, version_number)`.
- `PUT /v1/companies/{C}/bom-drafts` (header + mọi dòng một transaction; `expectedVersion` khi cập nhật), `POST /v1/boms/{id}/copy`, `GET /v1/boms/{id}/versions`, `POST /v1/boms/{id}/deactivate`; list thêm `search` + `latestOnly`.
- Activate: kiểm `effectiveFrom`, vòng (`B8`), khoá family để **hai activate đồng thời không để lại hai `ACTIVE`** (bài học `G2` #1 — đừng tin constraint/trigger một mình ở `READ COMMITTED`).
- **Test:** `*IT` concurrency activate (`NOT_SUPPORTED` + `TransactionTemplate`), rollback khi dòng cuối lỗi, `FlywayMigrationIT` backfill. 🔴 **Regression:** MRP/WO snapshot/costing cho BOM cũ phải ra **đúng số cũ** (D1) — assert literal trên fixture có sẵn.

#### `G5-2` — Routing: công đoạn trước, ghi cả graph, phiên bản *(chặn bởi D5, D6)*

- Migration: bảng `routing_operation_predecessors`; `routing_headers` + `version_number`, `effective_from`.
- `PUT .../routing-drafts` (graph theo `clientKey`), copy, versions, `POST /deactivate`; một nháp mở/family (Q18).
- 🔴 `WorkOrderService.snapshotRouting` **không** chụp predecessor ở lượt này trừ khi user yêu cầu — snapshot WO là bất biến `B49`/`B56`.

#### `G5-3` — Ca / Lịch / Trung tâm *(chặn bởi D7, D8)*

- Theo khuyến nghị D7/D8: **không** đổi mô hình; thêm `workingMinutes` (server tính) trên response ca, `expectedVersion` trên PATCH nếu `G5-0` thấy thiếu, guard deactivate khi còn tham chiếu `ACTIVE` (A23) + chặn deactivate trung tâm còn routing `ACTIVE` (Q9).
- Nếu user chọn đổi mô hình ⇒ gói này phình to, phải viết kế hoạch riêng trước.

#### `G5-4` — Công suất: phân bổ theo kỳ + xem trước + điều chỉnh *(chặn bởi D9, D10)*

- `CapacityBoardService`: tải = phần giao nhau với kỳ (segments). 🔴 JPQL bucket theo ngày (`function('timezone', …)`, `§0.30` #1) sẽ phải đổi — `WorkOrderOperationRepositoryIT` là guard có sẵn.
- `POST /v1/plants/{P}/capacity/preview` — chỉ đọc; tái dùng `WorkCalendarLookupService`; trả `segments[]`, `sourceVersions`; **không** ghi WO/reservation/movement (A30 đo delta = 0).
- Điều chỉnh theo D10.
- **Test:** ví dụ số của FE (A27: `required 1030 / in 960 / after 70 / demand 1130 / overload 170`) làm fixture literal; ca qua đêm A21 (`450` / `330`).

#### `G5-5` — Nhập BOM Excel *(chặn bởi D12; sau `G5-1`)*

- `ImportTargetType.BOM` + handler; mẫu `omniplant-bom-v1` (gom theo cột A, kiểm nhất quán B/C/D/I trong nhóm, không trùng component, sản phẩm đã có bất kỳ BOM nào ⇒ chặn); commit tạo N BOM `DRAFT` qua đúng service của `G5-1` (không INSERT thẳng — bài học `§0.45` #4).
- Tái dùng nguyên `DataImportBatchCommitter`/guard owner của `G3`; 🔴 cẩn thận bẫy `flush()` trong `try` (`§0.51` #2): va chạm ở **nhóm cuối** mới lộ.

#### `G5-6` — Nghiệm thu + bàn giao

- `mvn -o clean verify`; số case **≥ baseline** (`R10`).
- Nghiệm thu mutation, ưu tiên chỗ mock không thấy: bỏ khoá family khi activate (IT concurrency phải đỏ), bỏ transaction chung của atomic save, preview vô tình ghi một dòng, tải theo kỳ quay về "gán ngày bắt đầu".
- Smoke HTTP thật A01–A36 phần thuộc BE.
- Docs: `BE_RESPONSE_GD_05_*.md`, `CLAUDE.md §0`, `module/bom|routing|shift|workcenter|workorder|dataimport/CLAUDE.md` (bất biến mới), `docs/api-guide-for-frontend.md`.

### 12.4 Breaking change dự kiến

| Thay đổi | Breaking? |
|---|---|
| Field mới trên BOM/Routing, endpoint atomic/copy/versions/preview, `ImportTargetType.BOM` | **Không** (additive) |
| Bảng công suất tính theo phần giao nhau với kỳ (D9) | **Có — về số liệu**: cùng request, con số tải/quá tải theo ngày khác trước (sửa sai) |
| Điều chỉnh đè công đoạn ⇒ 409 (nếu D10 = chặn) | **Có** — request trước đây 200 + cờ nay bị từ chối |
| Deactivate ca/lịch/trung tâm còn tham chiếu `ACTIVE` ⇒ chặn | **Có** — chặn mới |
| Routing đổi phạm vi Plant (nếu D5 = đổi) | **Có, lớn** — đổi path + migration dữ liệu |

### 12.5 Ngoài phạm vi *(theo FE §1.2 + quyết định BE)*

Gantt kéo-thả, optimizer, phân máy/người cụ thể, xử lý va chạm nhiều WO, tự release/reserve, sửa snapshot WO, tồn kho/WIP/giá thành khi kích hoạt BOM, MES, serial, import Routing/Ca/Lịch/Trung tâm, partial import/upsert/kích hoạt BOM qua file, tự kích hoạt phiên bản tương lai, decimal-as-string, timezone có DST (Q12).

---

## 13. `G6` — FE handoff Giai đoạn 6: Đơn bán hàng và hoạch định vật tư 🔄 ĐANG CHẠY — `G6a` ✅ 2026-10-17, `G6b` ✅ 2026-10-18, `G6c` ✅ 2026-10-18 — chờ FE nghiệm thu

Nguồn: `FE_HANDOFF_GD_06_2026-10-14.md` (v1, P1, 605 dòng). FE/mock đã xong: đơn bán hàng nối Customer, **giữ tồn cho đơn**, 6 con số tiến độ theo từng dòng, chạy MRP thương mại, chi tiết nhu cầu, **chuyển đề xuất thành lệnh sản xuất / đơn mua nháp (một phần, nhiều lần)**, và **nhập đơn bán hàng Excel**. BE phải (1) trả lời Q1–Q28 §5, (2) đối chiếu R01–R18 với API có sẵn, (3) chỉ code phần thiếu, (4) trả `BE_RESPONSE_GD_06_<ngày>.md` theo `BETaskDeliveryRules.md`. FE chưa gọi live lần nào.

> 🔴 **Đây là handoff lớn nhất tới nay** — 28 câu hỏi, 18 yêu cầu, 38 ca nghiệm thu, và phạm vi xấp xỉ `G2`+`G3`+`G4` gộp lại. Nó cũng **mở lại hai thứ repo đã cố ý hoãn**: nối `SalesOrder` ↔ `Customer` (`G2 §9.5` ghi rõ "ngoài phạm vi") và giữ tồn cho đơn bán (reservation hiện **chỉ** thuộc lệnh sản xuất, `work_order_id NOT NULL`). Vì vậy `D0` — **có tách phase hay không** — phải chốt trước mọi quyết định khác.
>
> 🔴 **Hai thứ FE hỏi mà code đã trả lời được, không cần code gì:** `fulfilledQuantity` của API cũ **đúng là QC accepted** (Q26 — `ProductionReceiptService` QC `AVAILABLE` → `WorkOrderDemandAllocationService.fulfill`, đúng thiết kế `F6`/`§0.11`), và **không có** entity Shipment/Delivery nào trong repo ⇒ `deliveredQuantity` **chưa có nguồn dữ liệu nào** cho tới GĐ10. Đừng ánh xạ `fulfilled` thành "đã giao".

### 13.1 Đối chiếu yêu cầu FE ↔ code thật *(rà 2026-10-16 bằng đọc source, chưa gọi live)*

Phạm vi đã rà: cột của `SalesOrder`/`SalesOrderLine`/`MaterialReservation`/`SupplySuggestion`/`MrpRequirementLine`/`WorkOrderDemandAllocation`; mapping của `SalesOrderController`, `CustomerController`, `PlanningRunController`, `PlanningDemandController`, `PurchaseOrderController`, `PurchaseRequisitionController`; `MrpRunCreateRequest`; `SalesOrderLine.openQuantity()`; call site của `fulfill`; grep toàn repo cho `class Shipment`/`class Delivery`. **Chưa rà:** `SalesOrderService.list` có `search`/date range chưa; `MrpCalculationService` chi tiết (pool, carry, safety, scrap — cần cho D8); `MaterialReservationService.reserveAutomatically` (FEFO) có tái dùng được cho đơn bán không; `PlanningDemandService` coverage; `IdempotencySupport` trên `sales`.

| Nhóm | FE yêu cầu | Hiện trạng code | Loại |
|---|---|---|---|
| **SO** ↔ Customer | `customerId` trỏ Customer `ACTIVE` cùng công ty; `customerCode`/`customerName` là snapshot | 🔴 `sales_orders.customer_name` là **text tự do** (`length 255`). Customer master **đã có** từ `G2` nhưng **chưa nối** — `G2 §9.5` loại có chủ đích. Không có `customer_reference` | 🔴 **Quyết định** (D1) |
| SO mã đơn | server sinh `orderNo` | Client gửi `orderNo` trong body; `UNIQUE` theo công ty | **CODE** (D2) |
| SO lưu nháp | **một** `PUT`/`POST` lưu header + 1–500 dòng atomic, `expectedVersion` | `POST /v1` + `PATCH /v1/{id}` (full replace dòng, có `expectedVersion`) — **đã atomic** từ `§0.40`/`C2-4` | Trả lời + mapping |
| SO 6 con số tiến độ | ordered / held / WO coverage / produced / QC / delivered, mỗi số có `metricsEvidence` | 🔴 Chỉ `ordered_quantity` + `fulfilled_quantity`; `openQuantity()` = ordered − fulfilled. `WorkOrderDemandAllocation` có `allocated`/`fulfilled` (nền cho "WO coverage") | **CODE** (D3) |
| `fulfilledQuantity` nghĩa gì (Q26/Q27) | FE đoán là QC accepted | ✅ **Đúng** — tăng ở QC disposition `AVAILABLE` (`F6`). **Không có** Shipment/Delivery ⇒ `delivered` phải là `null` + `UNKNOWN` | Trả lời |
| **Giữ tồn cho đơn** | reservation thuộc `salesOrderId`/`salesOrderLineId`, dùng chung pool tồn, `On Hand` không đổi | 🔴 `MaterialReservation.work_order_id` **NOT NULL** ⇒ reservation hiện **chỉ** thuộc WO. Không có đường giữ tồn cho đơn bán | 🔴 **Quyết định** (D4) |
| Hủy đơn | bắt buộc lý do, giải phóng reservation của **chính nó**, chặn khi có chứng từ con | `POST /{id}/cancel` có `reason`, huỷ demand `OPEN`; **không** có reservation của đơn để giải phóng, **không** gate chứng từ con | **CODE** (sau D4) |
| **MRP** input | `salesOrderLineIds` + `itemWarehouseMappings[]` (theo từng vật tư) + `includeRop` | `MrpRunCreateRequest` có `demandLineIds` (**là `planningDemandId`**, không phải SO line) + **một** `demandWarehouseId`. Không có mapping theo vật tư, không `includeRop` | **CODE** (D6) |
| Nhu cầu nhiều nguồn | `parentRequirementLineIds[]`, `sourceSalesOrderLineIds[]`, `sourceKind` SALES/COMPONENT/ROP | 🔴 `parentRequirementLine` là `@ManyToOne` — **một** parent; không `sourceKind`; không mảng nguồn SO | **CODE** (D7) |
| Snapshot bất biến + `sourceVersion` + cờ cũ/mới | đọc lại run cũ được, nhưng chuyển đổi phải chặn khi nguồn đã đổi | Requirement/suggestion đã persist (bất biến trên thực tế). **Không** có `sourceVersion`/`currentSourceState` | **CODE** |
| **Chuyển một phần / nhiều lần** | `entries[{requirementLineId, quantity}]`, ledger lượng đã chuyển, `remainingConvertible` | 🔴 `SupplySuggestion` có **một** cặp `converted_reference_type`/`_id` ⇒ chuyển xong là đánh dấu hết, không có lượng, không ledger | **CODE** — bảng mới (D5) |
| BUY ⇒ đơn mua nháp | `POST .../conversions` tạo **PO DRAFT** trực tiếp, một nhà cung cấp/phiếu | Hiện là `convert-to-purchase-requisition` (**PR**, không phải PO). `POST /v1/purchase-orders` có nhưng **không** nối MRP | **CODE** (D9) |
| Duyệt đề xuất | FE muốn chuyển trực tiếp, không cần duyệt riêng (Q14) | `approve`/`reject` + `ensureDraft` gate (`D11`, `B74`) — convert **đòi** `APPROVED` | **Quyết định** (D10) |
| Làm tròn đa cấp (Q28) | chốt net MAKE `ceiling 6` **trước** khi nổ cấp con | Cần rà `MrpCalculationService`. 🔴 Nếu đổi ⇒ **đổi số MRP**, cùng loại rủi ro `§0.12` #1 | 🔴 **Quyết định** (D8) |
| **Nhập SO Excel** | `kind=so`, gom nhóm, tạo N đơn `DRAFT` atomic | Hạ tầng batch của `G3` tái dùng được; `ImportTargetType` chưa có `SALES_ORDER` | **CODE** (D11) |
| Decimal là **chuỗi** trên wire | FE đòi chuỗi cho mọi lượng | Toàn repo trả JSON number (`G2` Q12, `G4` D9, `G5`) | Trả lời — **không đổi** (D12) |
| `search` + khoảng ngày trên danh sách SO | `search`, `orderDateFrom/To`, `dueDateFrom/To`, `earliestDueDate`/`lineCount` | Chưa rà — xem `G6-0` | Rà rồi quyết |

### 13.2 Quyết định phải chốt với user trước khi code

| # | Quyết định | Khuyến nghị BE | Lý do | Chặn |
|---|---|---|---|---|
| **D0** | **Làm cả `G6` một lượt, hay tách?** | 🔴 **Tách ba: `G6a` (đơn bán hàng) → `G6b` (hoạch định + chuyển đổi) → `G6c` (nhập Excel).** Mỗi phần một commit + một lượt trả lời FE (`_v2`/`_v3` cùng task) | Phạm vi này xấp xỉ `G2`+`G3`+`G4` gộp: 2 bảng mới, ~5 cột mới trên `sales_orders`/`_lines`, đổi khoá ngoại `MaterialReservation`, mở rộng MRP input/graph, và có thể **đổi số MRP** (D8). `G5` đã là phase lớn nhất từng làm và nó chỉ bằng một phần ba việc ở đây. Tách ra thì mỗi lượt nghiệm thu mutation + smoke live còn đo được; gộp lại thì "xanh" không còn nói gì | **Toàn bộ** |
| **D1** | Nối `SalesOrder` → `Customer` (`customerId`) + `customerReference` | **Có, additive:** thêm `customer_id` **nullable** (FK) + `customer_code`/`customer_reference`; **giữ** `customer_name` làm snapshot (`NOT NULL`, như hiện nay). Đơn mới **bắt buộc** `customerId`; đơn cũ `NULL` = "tạo trước G6", **không backfill** bằng cách so tên | `G2 §9.5` hoãn đúng vì so tên là mơ hồ; FE cũng nói "không join theo tên mơ hồ". `customer_name` phải ở lại: đổi tên khách trong danh mục **không** được viết lại lịch sử đơn (Q18) | `G6a` |
| **D2** | `orderNo` do server sinh (Q1) | **Thêm đường mới sinh mã** (`POST .../drafts`), **giữ** đường cũ nhận `orderNo` từ client | Tiền lệ `G2` D1 / `G3` D1 / `G4`: thêm song song, không đổi tại chỗ. Seed script `D26-`/`VIETBIKE` đang gửi `orderNo` tường minh | `G6a` |
| **D3** | 6 con số tiến độ + `metricsEvidence` | **Có, nhưng `delivered` luôn `null` + `UNKNOWN`** cho tới GĐ10 (không có Shipment). `qcAccepted` = `fulfilled_quantity` hiện có. `workOrderCovered` suy từ `WorkOrderDemandAllocation` (batch, `C14`). `produced` cần rà có trace WO→SO line theo **sản lượng** hay không; nếu không ⇒ `null` + `UNKNOWN`, **không** suy từ `plannedQuantity` | FE nói thẳng: "legacy thiếu bằng chứng phải hiện chưa có bằng chứng, không tự gán 0 hoặc FULFILLED". Trả `0` cho `delivered` là **nói dối** có hệ thống | `G6a` |
| **D4** | Giữ tồn cho đơn bán: nới `MaterialReservation` hay **bảng riêng**? | **Nới bảng hiện có:** `work_order_id` thành nullable + thêm `sales_order_id`/`sales_order_line_id` + `CHECK` **đúng một** chủ sở hữu. **Không** tạo bảng thứ hai | Hai bảng reservation ⇒ mọi chỗ tính tồn khả dụng (`StockBalanceRepository.aggregate*`, `B3`, 3 gate ghi tồn kho, MRP) phải cộng **hai** nguồn — đúng loại "một hệ thống hai con số" mà `§0.44` đã trả giá. ⚠️ Rủi ro: `work_order_id NOT NULL` đang là bảo đảm ngầm ở nhiều query; phải rà hết `MaterialReservationRepository` trước | `G6a` |
| **D5** | Chuyển đổi một phần/nhiều lần: ledger | **Bảng mới** `supply_suggestion_conversions` (`suggestion_id`, `quantity`, `target_type`, `target_id`, `idempotency_key` + `UNIQUE`); `remainingConvertible = suggestedQuantity − Σ`. **Giữ** `converted_reference_*` làm "lần chuyển đầu tiên" cho client cũ | Một cặp cột không biểu diễn được "đã chuyển 40/70". Không có ledger thì retry/split đều không kiểm được (FE A27–A30) | `G6b` |
| **D6** | MRP input: `salesOrderLineIds` + mapping theo vật tư + `includeRop` (Q7, Q9) | **Endpoint mới** `POST /v1/planning-runs/commercial`, **giữ** `POST /v1/planning-runs` nguyên nghĩa (`demandLineIds` = `planningDemandId`) | FE tự đề nghị giữ nghĩa cũ. Nhồi 3 field mới vào request cũ thì `demandLineIds` và `salesOrderLineIds` sống cạnh nhau trong một DTO — chỗ dễ gửi sai nhất | `G6b` |
| **D7** | Nhu cầu nhiều parent + nhiều SO line nguồn + `sourceKind` (Q10) | **Có**, nhưng là **bảng nối** `mrp_requirement_sources` (`requirement_line_id`, `kind`, `sales_order_line_id?`, `parent_requirement_line_id?`, `quantity`), **giữ** `parent_requirement_line_id` hiện có cho client cũ | Q10 là điều kiện để "chỉ phần gốc mới cover đúng đơn" — gộp bucket mà mất nguồn thì coverage gắn sai đơn. Một cột `@ManyToOne` không mang được nhiều nguồn | `G6b` |
| **D8** | 🔴 Chốt net MAKE `ceiling 6` **trước** khi nổ cấp con (Q28) | **Hỏi user.** Nếu làm ⇒ **số MRP đổi** cho BOM nhiều cấp có tỷ lệ lớn; phải đo A/B trên bộ `D26-` và ghi rõ cho FE, đúng cách `§0.12` #1 đã làm | FE nói rõ chưa có bằng chứng live, và chỉ suy luận toán học (WIP `1.0000001` → WO `1.000001` ⇒ RAW cần `100.0001` chứ không phải `100.00001`). Lập luận **đúng về nghiệp vụ** (cấp con không được thiếu so với WO thật) nhưng đây là đổi kết quả hoạch định ⇒ quyết định của user, không phải chi tiết triển khai | `G6b` |
| **D9** | BUY ⇒ `PO DRAFT` trực tiếp hay giữ `PR` | **Thêm đường PO DRAFT** từ `/conversions`, **giữ** `convert-to-purchase-requisition`. Quyền: tái dùng `PERM_SUPPLY_SUGGESTION_MANAGE` + guard công ty/nhà máy, **chưa** thêm quyền PO riêng (Q5 — để GĐ7) | Luồng PR→PO là nghiệp vụ mua hàng thật của repo (`B72`), không bỏ. FE chỉ cần một nháp để GĐ7 nối tiếp | `G6b` |
| **D10** | Chuyển trực tiếp không cần `approve` (Q14) | **Có** cho đường `/conversions` mới; **giữ** gate `APPROVED` của `convert-to-work-order` cũ (`B74`) | Hai đường, hai hợp đồng — không nới gate của đường cũ (nới là mất một bước kiểm soát mà `D11` đặt ra có lý do) | `G6b` |
| **D11** | Nhập SO Excel trong `G6` hay tách | **`G6c`, sau `G6a`** (cần `customerId` + đường sinh `orderNo`) | Hạ tầng `G3` làm phần khó rồi; phần mới là gom nhóm + kiểm nhất quán trong nhóm. ⚠️ Bẫy `flush()` trong `try` (`§0.51` #2) áp dụng y nguyên | `G6c` |
| **D12** | Decimal là chuỗi trên wire (Q13 của FE §3.3) | **Không** — giữ JSON number | Đổi riêng vài endpoint = hai hợp đồng số trong một API; đổi toàn hệ thống là ngoài phạm vi (cùng lý do `G2` Q12, `G4` D9) | — |

> Các câu còn lại trả lời bằng hành vi có sẵn hoặc theo hướng FE nghiêng: Q2 (coverage **gồm** WO `DRAFT`), Q3 (lọc theo `earliestDueDate`), Q4 (`PERM_SALES_ORDER_MANAGE` cho giữ tồn — **không** dùng quyền reservation của WO), Q6 (bộ đọc tham chiếu tối thiểu — rà xem `hasResourceAccess` hiện có đủ chưa), Q8 (net dùng available **đã trừ** reservation + carry — rà `MrpCalculationService` rồi trả lời bằng công thức thật, đừng hứa trước), Q11/Q12 (nguồn cung `WO BLOCKED`/đã earmark — rà `WorkOrderSupplyService`), Q15 (`999999999` + scale 6 ⇒ `@Digits(13,6)` như `G4`), Q16 (kho đầu ra MAKE = kho tính nhu cầu), Q17 (lead time theo ngày lịch, quá hạn ⇒ cảnh báo, **không** tự kẹp ngày), Q18 (giữ snapshot tên khách — xem D1), Q19 (ngày ISO + Excel Date thật), Q20 (retention theo `G3`), Q21 (**không** tự hoàn quota khi huỷ nháp), Q22 (đọc lịch sử nhập chỉ cần `READ` + owner — giống `G3` D7), Q23 (kiểm nguồn cũ **trong cùng** transaction chuyển đổi), Q24 (huỷ đơn có chứng từ con ⇒ **chặn**, không cascade), Q25 (bỏ `lotId` ⇒ server tự chọn lô FEFO — **rà** `reserveAutomatically` có tái dùng được không), Q26/Q27 (đã trả lời ở §13.1).

### 13.3 Gói công việc *(thứ tự thực thi; mỗi gói một commit theo `dev-workflow.md §6.6`)*

> Migration tiếp theo là **`V74`** (`V69`–`V73` đã dùng ở `G2`–`G5`). ⚠️ `MANUFACTURING_PHASE_PLAN_P7_P11.md` phải đánh số lại thêm một lần nữa.

#### `G6-0` — Đọc nốt + baseline + trả lời §5 *(không code `src/main`)*

1. Đọc phần "chưa rà" §13.1 — ưu tiên theo thứ tự rủi ro: (a) `MaterialReservationRepository` + mọi chỗ đọc `work_order_id` (quyết định D4 có khả thi không); (b) `MrpCalculationService` pool/carry/safety/scrap + chỗ làm tròn (D8, Q8); (c) `WorkOrderSupplyService` (Q11/Q12); (d) `SalesOrderService.list` (`search`/date range); (e) `MaterialReservationService.reserveAutomatically` (Q25); (f) `WorkOrderDemandAllocation` có trace sản lượng theo dòng đơn hay không (D3 `produced`).
2. `mvn -o clean verify` trên `git worktree` **sạch** ⇒ baseline thật (số cuối đã ghi: **1548 unit + 215 IT**, `§0.53` — **đo lại**; bẫy VS Code Java LS ở `§0.52` #10a).
3. Chạy live các endpoint SO/MRP/suggestion hiện có trên DB dùng-một-lần để có request/response **thật** cho file trả lời (`B2`).
4. Chốt D0–D12 với user. **D0 trước**, vì nó quyết định `G6` là một phase hay ba.

#### `G6a` — Đơn bán hàng *(chặn bởi D1–D4)*

- **Migration `V74`:** `sales_orders` + `customer_id` (FK, nullable)/`customer_code`/`customer_reference`; `sales_order_lines` + cột cho các số tiến độ **chỉ khi** không suy được lúc đọc (ưu tiên suy — xem D3); `material_reservations` `work_order_id` → nullable + `sales_order_id`/`sales_order_line_id` + `CHECK` đúng một chủ sở hữu + index. **Không backfill.**
- Endpoint: `POST /v1/sales-orders/v1/drafts` (sinh `orderNo`), `PATCH .../{id}/draft`, `GET .../{id}/reservations`, `GET .../{id}/lines/{lineId}/allocation-candidates`, `POST .../{id}/reservations`, `POST .../reservations/{rid}/release`; mở rộng `GET /v1` (`search` + 4 khoảng ngày + `earliestDueDate`/`lineCount`), `GET /v1/{id}` (6 số + `metricsEvidence` + `linkedDocuments[]`).
- Huỷ đơn: giải phóng reservation **của chính đơn** trong cùng transaction + chặn khi có chứng từ con.
- 🔴 **Test bắt buộc:** `*IT` cho tồn khả dụng khi có **cả hai** loại reservation (WO + SO) trên cùng vị thế — đây là chỗ D4 sai thì sai âm thầm; `*IT` concurrency hai request giữ tồn tranh phần cuối (khuôn `ItemWarehouseSettingConcurrencyIT`/`StockTransferIT`); regression: tồn khả dụng/MRP/dashboard cho dữ liệu **chỉ có WO reservation** phải ra **đúng số cũ**.

#### `G6b` — Hoạch định + chuyển đổi *(chặn bởi D5–D10)*

- Migration: bảng `supply_suggestion_conversions` + `mrp_requirement_sources`; cột `source_version`/`source_kind` nếu cần.
- `POST /v1/planning-runs/commercial`; `GET .../requirements/{id}`; `POST .../{id}/conversions` (một transaction, `expectedSourceVersion` kiểm **trong** transaction — Q23, chống TOCTOU); `GET .../{id}/conversions`.
- MAKE ⇒ `WorkOrderService.createFromMrp` (đường canonical sẵn có, **không** tạo WO store thứ hai); BUY ⇒ PO `DRAFT`.
- 🔴 **Nếu D8 = làm:** đo A/B số MRP trên bộ `D26-` **trước và sau**, ghi vào file trả lời FE như một breaking change về số liệu (khuôn `§0.52` #2).

#### `G6c` — Nhập đơn bán hàng Excel *(chặn bởi D11; sau `G6a`)*

- `ImportTargetType`/`kind` + `SALES_ORDER`, mẫu `omniplant-so-v1` (gom theo `Group code`, kiểm nhất quán khách/ngày/tham chiếu trong nhóm), commit tạo N đơn `DRAFT` qua **đúng** service của `G6a`.
- Tái dùng nguyên `DataImportBatchCommitter`/guard owner của `G3`; 🔴 bẫy `flush()` trong `try` (`§0.51` #2) — va chạm ở **nhóm cuối** mới lộ.

#### `G6-9` — Nghiệm thu + bàn giao *(mỗi gói tự nghiệm thu nếu D0 = tách)*

- `mvn -o clean verify`; số case **≥ baseline** (`R10`).
- Nghiệm thu mutation, ưu tiên chỗ mock không thấy: bỏ reservation của đơn khỏi phép tính tồn khả dụng, bỏ `CHECK` một-chủ-sở-hữu, bỏ ledger lượng đã chuyển (chuyển hai lần vượt nhu cầu), bỏ kiểm nguồn cũ trong transaction, đổi mã lỗi sang mã khác **cùng HTTP status**.
- Smoke HTTP thật A01–A38 phần thuộc BE; **A24 cần luồng mua hàng thật của GĐ7** ⇒ ghi "ngoài phạm vi" nếu chưa có.
- Docs: `BE_RESPONSE_GD_06_*.md`, `CLAUDE.md §0`, `module/sales|planning|workorder|inventory|purchasing|dataimport/CLAUDE.md` (bất biến tiếp từ `B170`), `docs/api-guide-for-frontend.md`.

### 13.4 Breaking change dự kiến

| Thay đổi | Breaking? |
|---|---|
| Cột/endpoint/field mới (customer link, 6 số, reservation của đơn, conversions, `kind=so`) | **Không** (additive) nếu theo D1/D2/D5/D6/D9 |
| Tồn khả dụng nay trừ **cả** reservation của đơn bán | **Có — về số liệu**: cùng request, tồn khả dụng/MRP/dashboard đổi khi có đơn giữ tồn (đúng ý định, nhưng phải báo FE) |
| Số MRP đổi do làm tròn đa cấp (D8) | **Có — về số liệu**, nếu D8 = làm |
| Huỷ đơn có chứng từ con ⇒ 409 | **Có** — request trước đây 200 |
| Decimal thành chuỗi | **Không làm** (D12) |

### 13.5 Ngoài phạm vi *(theo FE §1.2 + quyết định BE)*

Xác nhận/nhận một phần/QC đơn mua đầy đủ (GĐ7); sẵn sàng vật tư/phát hành WO/xuất vật tư/thực thi/WIP (GĐ8); phiếu nhập sản xuất/QC (GĐ9); giao hàng/hoá đơn/thanh toán (GĐ10) ⇒ **`deliveredQuantity` chưa có nguồn, phải là `UNKNOWN`**. Cũng ngoài phạm vi: PR/RFQ/trả hàng, MPS/dự báo, optimizer/Gantt, cấp hàng xuyên nhà máy, số sê-ri, tự chuyển kho để bù chênh kho, nhập một phần/ghi đè đơn từ Excel, sửa snapshot WO đã có, đổi Auth/quyền GĐ1, decimal-as-string.

---

## 14. `G7` — FE handoff Giai đoạn 7: Đơn mua hàng, nhận hàng và QC ✅ BE HOÀN THÀNH — `G7a` ✅ `G7b` ✅ 2026-10-18 · `G7c` ✅ 2026-10-19

Nguồn: `FE_HANDOFF_GD_07_2026-10-14.md` (v1, P1, 597 dòng). FE/mock đã xong: đơn mua hàng thủ công + nháp BUY của `G6b`, nhận hàng **từng phần vào vùng chờ kiểm (HOLD)**, **QC theo lượng** (đạt/loại/còn chờ, nhiều đợt), lô con theo lineage, và nguồn cung PO cho MRP. BE phải (1) trả lời Q1–Q31 §5, (2) đối chiếu R01–R20 với API có sẵn, (3) chỉ code phần thiếu, (4) trả `BE_RESPONSE_GD_07_<ngày>.md` theo `BETaskDeliveryRules.md`. FE chưa gọi live lần nào — §2 ghi rõ **cả năm mảnh bằng chứng live đều "không có"**.

> 🔴 **Khác mọi handoff trước: module này ĐÃ CÓ.** `module/purchasing` tồn tại từ lâu (Supplier, PR → PO → Goods Receipt + cancel/reversal), có `PurchaseOrderController` (5 endpoint), `GoodsReceiptController` (4 endpoint), nguồn cung PO đã vào MRP từ `D4` (`B67`), và `G6b` vừa thêm `createFromPlanning` để BUY sinh **đúng một** PO nháp canonical. FE viết "chưa có purchasing adapter được xác minh" — đó là trạng thái **phía FE**, không phải "BE chưa có API" (chính §1.2 của họ cũng cảnh báo đừng suy ra thế). ⇒ Phần lớn công việc là **mở rộng và đổi vòng đời**, không phải dựng mới.
>
> 🔴 **Hai thứ FE đòi mà repo CHƯA CÓ GÌ, và cả hai đều lớn hơn phần còn lại cộng lại:**
> 1. **QC mua hàng.** Không có một dòng code nào. `QualityDisposition` nằm trong `module/workorder`, `lot_id NOT NULL`, gắn `production_receipt_lines` (§0.13 #4) ⇒ không tái dùng được. Cần entity + lô con + vùng chờ kiểm cho hàng không theo lô + ledger quyết định bất biến.
> 2. **Giá trị tồn kho.** `stock_movements`/`stock_balances` **không có một cột giá nào** — `P3` (§0.31 quyết định #2) đã **cố ý** loại Material Price Variance đúng vì lý do này. FE §3.4 lại đòi `receipt_value_delta`, `average_unit_cost`, `inventory value`. Đó là một **lớp actual-costing chưa tồn tại**, không phải một field thiếu.
>
> 🔴 **Một đảo ngược ngữ nghĩa, phải chốt trước khi viết dòng nào:** hiện `POST /v1/goods-receipts/{id}/cancel` huỷ phiếu **đã POSTED** và ghi movement `REVERSAL` để hoàn tồn. FE §1.3 mục 8 nói **ngược lại**: `POSTED` bất biến, chỉ `DRAFT` mới huỷ được. Hai nghĩa trên **cùng một** endpoint — xem `D3`.

### 14.1 Đối chiếu yêu cầu FE ↔ code thật *(rà 2026-10-18 bằng đọc source, chưa gọi live)*

Phạm vi đã rà: `PurchaseOrderController`, `GoodsReceiptController`, `GoodsReceiptService` (đọc hết), `PurchaseOrder`/`PurchaseOrderLine`/`GoodsReceipt`/`GoodsReceiptStatus`/`PurchaseOrderStatus`, `PurchaseOrderCreateRequest`/`LineRequest`, `GoodsReceiptPostRequest`/`LineRequest`, `InventoryReceiveCommand` + overload trạng thái lô của `InventoryMovementService.receive`, `InventoryLot`/`StockBalance` (toàn bộ field), grep permission trong migration + `src/main`, `PlanningConversionService` (đường BUY của `G6b`). **Chưa rà:** `PurchaseOrderService` đầy đủ (`create`/`send`/`cancel`/`createFromPlanning`); `PurchasingPermissionGuard`; `PurchaseOrderSupplyService` lọc theo kho/ngày tới đâu (cần cho R14); `supply_suggestion_conversions` có `target_line_id` không (cần cho `sources[]`); `InventoryMapper` nhánh `qualityHoldQuantity`; `PurchaseRequisitionService.convertToPurchaseOrder` có đụng vòng đời PO mới không.

| Nhóm | FE yêu cầu | Hiện trạng code | Loại |
|---|---|---|---|
| Đọc PO (R01, R15) | List có `search`/`status`/kho/khoảng `orderDate`, phân trang, sort ổn định | `GET /v1/purchase-orders` có `companyId`/`plantId`/`warehouseId`/`supplierId`/`status` + phân trang. **Thiếu** `search` và khoảng ngày | **CODE** nhỏ (bẫy `lower(bytea)` §0.24) |
| Nháp BUY là canonical (R01, Q2) | Nháp BUY `G6` giữ cùng ID, không nhân đôi | ✅ **Đã đúng** — `G6b` gọi `PurchaseOrderService.createFromPlanning`, ledger `supply_suggestion_conversions` giữ nguồn/quota | **Trả lời** (Q2 = Có, đã có) |
| `sources[]` trên dòng PO (R01, R04) | `runId`/`requirementId`/`suggestionId`/`conversionId`/`sourceVersion`/`convertedQuantity` | `PurchaseOrderLine` **không** có cột nguồn nào (chỉ `purchaseRequisitionLine`). Dữ liệu **có** trong ledger `G6b`, chỉ chưa được chiếu ra | **CODE** (chiếu, không thêm cột — xem `D12`) |
| Mã PO (R02, §3.2) | Server cấp, **không** nhận từ client | `purchaseOrderNo` là `@NotBlank` **client gửi** | **CODE** + quyết định `D2` |
| Trạng thái PO (§1.4) | `DRAFT`/`CONFIRMED`/`PARTIALLY_RECEIVED`/`FULLY_RECEIVED`/`CLOSED`/`CANCELLED` | `DRAFT`/**`SENT`**/`PARTIALLY_RECEIVED`/**`RECEIVED`**/`CANCELLED` — **không có `CLOSED`** | **CODE** + `D1` (§11.3 checklist) |
| Sửa nháp PO (R02, R03) | `PATCH` chỉ `DRAFT`, thay cả `lines[]`, khoá field của nháp nguồn | **Không có `PATCH`** — chỉ `create`/`send`/`cancel` | **CODE** (bẫy `B112` thứ tự flush khi thay dòng) |
| `expectedVersion` (R17) | Bắt buộc trên **mọi** command | Purchasing **không** nhận `expectedVersion` ở đâu cả (`send`/`cancel` không body) | **CODE** + `D11` |
| Lý do huỷ/đóng (R04, R05) | Bắt buộc, lưu lại | **Không có** cột/field | **CODE** + migration |
| Đóng phần còn lại (R05) | `close` + `closedQuantity` trên dòng | **Không có** | **CODE** + migration |
| Vòng đời phiếu nhận (R06, Q8) | `DRAFT` → `POSTED`; `POSTED` **bất biến**; huỷ chỉ `DRAFT` | `GoodsReceiptStatus` chỉ `POSTED`/`CANCELLED`; `post()` tạo **thẳng** `POSTED`; `cancel()` huỷ **POSTED** + `REVERSAL` | **CODE** + 🔴 `D3` (đảo ngược) |
| Nhận vào vùng chờ kiểm (R09, Q8) | Nhận mua vào **HOLD**, chưa dùng được | `GoodsReceiptService` gọi `receive(cmd, key)` ⇒ **`LotStatus.AVAILABLE`**. Overload HOLD **đã có** (phiếu nhập sản xuất dùng) | **CODE** nhỏ, **đổi số liệu** — `D4` |
| Danh sách phiếu nhận / ứng viên PO (R15, §3.3) | `GET /goods-receipts` toàn nhà máy + `/purchase-order-candidates` | Chỉ `GET /v1/purchase-orders/{id}/goods-receipts` (theo một PO) | **CODE** |
| Tách lô cùng dòng PO (R07, Q10) | Nhiều dòng phiếu cùng `poLineId`, cộng dồn ≤ còn mở | ✅ Cộng dồn **đã đúng** (`orderLine.receive()` trong vòng lặp nên dòng sau so với số đã giảm) | **Trả lời** (Q10 = Có) |
| Tranh chấp hai phiếu (R07, A10) | Một thành công, một báo vượt | Chỉ có `@Version` của `BaseEntity` ⇒ kẻ thua nhận `CONCURRENT_MODIFICATION`, **không** phải "dòng nào vượt". Không có khoá bi quan trên dòng PO | **CODE** + `D11` (bài học `G4 B151`) |
| **QC mua hàng** (R10–R12) | Quyết định theo lượng, nhiều đợt, bất biến, giảm `pending` | 🔴 **Không có gì.** `QualityDisposition` thuộc `module/workorder`, `lot_id NOT NULL`, gắn phiếu nhập **sản xuất** | **CODE** — phần lớn nhất, `D5` |
| Lô con theo lineage (R11, Q16) | Lô con `AVAILABLE`/`REJECTED` có `parentLotId`/nguồn/hạn | `InventoryLot` có đúng 6 field, **không có `parent_lot_id`**, không có nguồn | **CODE** + migration, `D6` |
| Vùng chờ kiểm hàng không theo lô (R09, R11) | `qualityBucketId` truy được nguồn | `stock_balances.quality_hold_quantity` **đã có** (`V56`, `B2`) nhưng **không có id** nào để trace | **CODE** + `D7` |
| Hàng chờ/loại không dùng được (R13) | HOLD/REJECTED/quá hạn không vào MRP/giữ/xuất/chuyển | ✅ **Đã đúng** — `B3`, `B116` (lô không `AVAILABLE` ⇒ khả dụng 0), `B154` (quá hạn, `G4`), aggregate loại HOLD | **Trả lời** |
| Nguồn cung PO cho MRP (R14) | Open PO theo vật tư/đơn vị/kho/ngày; nhận HOLD giảm inbound không tăng khả dụng; QC đạt tính một lần | ✅ Phần lớn **đã có** — `PurchaseOrderSupplyService` (`D4`/`B67`) cộng PO `SENT`+`PARTIALLY_RECEIVED`, bỏ dòng đã nhận đủ. Cần rà lọc theo **kho/ngày** và đổi tên trạng thái theo `D1` | **Trả lời + CODE** nhỏ |
| **Giá trị tồn / bình quân** (R19, §3.4, Q18–Q19) | `receipt_value_delta`, `average_unit_cost`, `inventory value`, `valueDelta` khi post | 🔴 **Không có cột giá nào** trên `stock_movements`/`stock_balances`. `P3` **cố ý** loại vì đúng lý do này | **CODE** lớn hoặc **hoãn** — 🔴 `D8` |
| Quyền (§1.4, Q1) | 7 quyền: PO_READ/MANAGE, GR_READ/MANAGE/POST, QC_READ/MANAGE | Có **3**: `PERM_PURCHASE_ORDER_READ`/`_MANAGE`, `PERM_GOODS_RECEIPT_POST`. **Thiếu 4** | **CODE** + migration seed, `C10` kích hoạt, `D9` |
| Idempotency (R17, Q21) | Mọi command nhận `Idempotency-Key` | Có ở `post` phiếu nhận (`(purchase_order_id, key)`, `V20`). PO commands + QC: **không có** | **CODE** |
| `X-Trace-Id` (Q22) | Mọi response | ✅ Đã có (`TraceIdFilter`) | **Trả lời** |
| Giới hạn số (Q31) | ≤ 999999999, 6 số lẻ | `@Digits(13,6)` đã áp cho inventory ở `G4`; purchasing **chưa** | **CODE** nhỏ |
| Hợp đồng lỗi (§4) | ~19 mã | Phần lớn ánh xạ được vào mã sẵn có (vượt số mở ⇒ `PLANNED_QUANTITY_EXCEEDED`, `B27`); cần vài mã mới | **Trả lời + CODE** nhỏ |

### 14.2 Quyết định phải chốt với user trước khi code ✅ *(chốt 2026-10-18, `AskUserQuestion`: D0 = (b) tách làm liền; D1 = (a); D3/D4 = (a); D8 = (a) hoãn; còn lại theo cột "Khuyến nghị BE")*

| # | Quyết định | Hai phương án | Khuyến nghị BE |
|---|---|---|---|
| **D0** | **Tách phase hay làm một lượt** | (a) một lượt `G7`; (b) tách `G7a` vòng đời PO → `G7b` phiếu nhận DRAFT + vào HOLD → `G7c` QC mua + lô con → `G7d` giá trị tồn | 🔴 **(b)**, đúng tiền lệ `D0` của `G6`. Phạm vi lớn hơn `G6`: 20 yêu cầu, 31 câu hỏi, 38 ca nghiệm thu, **cộng hai thứ chưa từng có** (QC mua, giá trị tồn). `G7c` một mình đã bằng một phase bình thường |
| **D1** | Trạng thái PO: đổi `SENT`→`CONFIRMED`, `RECEIVED`→`FULLY_RECEIVED`, thêm `CLOSED` | (a) đổi tên + `UPDATE` dữ liệu cũ; (b) giữ tên cũ, FE tự ánh xạ | **(a) đổi tên; `CLOSED` thì bắt buộc dù chọn gì.** Có tiền lệ: `ProductionReceiptStatus.POSTED → APPROVED` + `V26` (§0.6). FE là client duy nhất. 🔴 Phải chạy đủ checklist `coding-rules.md §11.3` (grep mọi so sánh, sửa `CHECK`, sửa seed `D26-`/`VIETBIKE`) |
| **D2** | Mã PO do server cấp | (a) đường mới `POST /v1/purchase-orders/drafts` server cấp mã, **song song** đường cũ; (b) sửa tại chỗ, bỏ qua mã client gửi | **(a)** — đúng tiền lệ `G6a D2` (`sales_order_number_seq`). Sửa tại chỗ làm vỡ `scripts/demo-capstone2` và mọi client đang gửi mã |
| **D3** | 🔴 Vòng đời phiếu nhận: `POSTED` bất biến | (a) thêm `DRAFT` + `cancel` chỉ cho `DRAFT`, **giữ** đường huỷ `POSTED` + `REVERSAL` hiện có (một endpoint, hai nghĩa theo trạng thái); (b) bỏ đường huỷ `POSTED` | **(a)** — đường `REVERSAL` là hành vi thật đang chạy và bộ dữ liệu demo dựa vào nó; bỏ nó là xoá một khả năng nghiệp vụ mà FE chỉ xếp "ngoài phạm vi GĐ7", không nói "phải bỏ" |
| **D4** | Nhận hàng mua vào `HOLD` | (a) áp cho **cả hai** đường (cũ + mới); (b) chỉ đường mới | **(a)** — (b) tạo **hai con số** cho cùng lô hàng, đúng loại lỗi `§0.44`. Chấp nhận breaking: hàng mua không dùng được tới khi QC. **Không backfill** lô cũ |
| **D5** | QC mua: entity | (a) bảng riêng `purchase_quality_decisions` trong `purchasing`; (b) nới `QualityDisposition` của `workorder` | **(a)** — `QualityDisposition.lot_id` là `NOT NULL` và bảng được định nghĩa "một dòng cho mỗi lô của phiếu nhập **sản xuất**" (§0.13 #4); nới nó là đổi nghĩa một bảng đã có dữ liệu lịch sử |
| **D6** | Lô con | (a) thêm `parent_lot_id` nullable + nguồn (phiếu/dòng/quyết định) vào `inventory_lots`; (b) chờ `P9` (traceability) | **(a)**, **chỉ** `parent_lot_id` + nguồn — không làm genealogy đầy đủ của `P9` |
| **D7** | `qualityBucketId` cho hàng không theo lô | (a) trả `receiptLineId` làm chủ sở hữu vùng chờ (không bảng mới); (b) bảng `quality_buckets` riêng | **(a)** — FE chỉ cần **một id truy được**; `quality_hold_quantity` đã mang lượng, dòng phiếu đã mang nguồn |
| **D8** | 🔴 **Giá trị tồn + bình quân di động** | (a) **hoãn** — trả `null`/unknown (đúng chính Q19 của FE), không dựng actual-costing trong `G7`; (b) dựng lớp valuation (cột giá trên ledger + bình quân) | **(a) hoãn.** `P3` đã cân và loại đúng việc này; (b) là một phase riêng cỡ `P3`, và FE §3.4 tự cho phép `null` khi không có giá trị đáng tin. Nếu user muốn (b) thì nó là `G7d` **riêng**, không gộp |
| **D9** | 4 quyền mới + seed role | (a) `PERM_GOODS_RECEIPT_READ`/`_MANAGE` + `PERM_PURCHASE_QC_READ`/`_MANAGE`, seed **ADMIN+MANAGER** (Q24); (b) tái dùng quyền PO | **(a)** — `C10` kích hoạt (migration seed + `docs/roles-and-permissions.md`) + test ma trận grant ở `FlywayMigrationIT` theo khuôn `migrate_v41_*` |
| **D10** | Hạn dùng bắt buộc (Q17) | (a) tuỳ chọn, không thêm `Item.expiryRequired`; (b) thêm cờ policy trên `Item` | **(a)** — `G4` đã cho ghi `expiresAt` khi nhận; thêm cờ chưa ai dùng là code speculative (`§11.5`) |
| **D11** | Đồng thời + `expectedVersion` | (a) `expectedVersion` mọi command + **khoá bi quan dòng PO** khi post; (b) chỉ `@Version` | **(a)** — `G4 B151` đã chứng minh: chỉ `@Version` thì kẻ thua nhận 409 **chung**, không phải "dòng nào vượt bao nhiêu" như A10 đòi |
| **D12** | `sources[]` trên dòng PO | (a) suy từ `supply_suggestion_conversions` (`G6b`), không thêm cột; (b) thêm cột nguồn lên `purchase_order_lines` | **(a)** — ledger đã là nguồn duy nhất của quota/nguồn; thêm cột là hai nguồn sự thật |

> **Hai thứ KHÔNG hỏi vì code đã trả lời:** Q2 (nháp BUY canonical — `G6b` đã làm đúng), Q10 (nhiều dòng cùng dòng PO — đã cộng dồn đúng). **Một thứ không hỏi vì đã đạt:** Q22 (`X-Trace-Id`).

### 14.3 Gói công việc *(thứ tự thực thi; mỗi gói một commit theo `dev-workflow.md §6.6`)*

| Gói | Nội dung | Phụ thuộc |
|---|---|---|
| `G7-0` | Đọc nốt phần "chưa rà" §14.1 (ưu tiên `PurchaseOrderService`, `PurchasingPermissionGuard`, `PurchaseOrderSupplyService`, `supply_suggestion_conversions`) · baseline `mvn -o clean verify` trên **`git worktree` sạch** (bẫy `§0.52` #10a) · chạy live PO/GR hiện có để biết chính xác hành vi **trước** khi đổi | — |
| `G7a` | **Vòng đời PO:** `CLOSED` + đổi tên trạng thái (`D1`, đủ checklist `§11.3`) · đường nháp server cấp mã (`D2`) · `PATCH` nháp (bẫy `B112`) · `expectedVersion` + lý do + `close`/`closedQuantity` · `search` + khoảng ngày · `sources[]` (`D12`) · 4 quyền + seed (`D9`) | `D1`,`D2`,`D9`,`D11`,`D12` · migration **`V76`** |
| `G7b` | **Phiếu nhận:** `DRAFT` + `cancel` theo trạng thái (`D3`) · nhận vào `HOLD` (`D4`) · danh sách toàn nhà máy + `purchase-order-candidates` · khoá bi quan dòng PO khi post (`D11`) · `@Digits(13,6)` | `G7a`, `D3`, `D4` |
| `G7c` | **QC mua:** bảng quyết định (`D5`) · lô con `parent_lot_id` + nguồn (`D6`) · vùng chờ hàng không theo lô (`D7`) · hàng đợi QC + lịch sử · chặn mọi đường ghi tồn kho đi vòng qua QC (R13) | `G7b`, `D5`–`D7` |
| `G7d` | *(chỉ nếu user chọn `D8` = (b))* Lớp giá trị tồn + bình quân di động | `D8` |
| `G7-9` | `mvn -o clean verify` + nghiệm thu mutation (nhắm: cộng dồn vượt số mở, khoá dòng PO, `HOLD` thay `AVAILABLE`, `pending` sau nhiều đợt QC, lineage lô con) + smoke HTTP thật + `BE_RESPONSE_GD_07_<ngày>.md` + docs (`CLAUDE.md §0.57`, `module/purchasing/CLAUDE.md`, `module/inventory/CLAUDE.md`, `roles-and-permissions.md`, `api-guide-for-frontend.md`) | tất cả |

🔴 **Mỗi gói tự nghiệm thu đủ** (`mvn -o clean verify` + mutation + smoke + một bản `_vN` cho FE) nếu `D0` = tách — đúng cách `G6a`/`G6b`/`G6c` đã làm.

### 15.3d Kết quả `G8d` *(2026-10-20)*

Migration **`V82`**; bất biến **`B222`–`B225`** (`module/workorder/CLAUDE.md`); bản ghi `CLAUDE.md §0.63`; trả lời FE `BE_RESPONSE_GD_08_2026-10-20_v4.md`.
Đã làm: dở dang sống theo `section` (D8) — `GET /plants/{P}/wip`, `GET /work-orders/{W}/wip` · phiếu hoàn vật tư (D3) — `POST …/material-returns`,
`material-return-candidates`, danh sách + chi tiết · hoàn = `RECEIVE` về đúng kho + lô dòng cấp, trần `cấp − dùng − hoàn`, kiểm nguồn lúc hoàn, không đổi
"đã cấp", không trả giữ tồn · trần vật tư dùng của báo sản lượng trừ thêm phần đã hoàn · 3 quyền (`C10`). Chưa làm: giá trị (D10), `HISTORY`, variance trừ
phần đã hoàn. Nghiệm thu: `mvn -o clean verify` 1913 + 269, mutation 27/27 (D23/D25 viết lại ngữ nghĩa sau lần "giết" giả do query không khởi động),
smoke HTTP 33/33 (lượt `162845`, ngay lần đầu). Script `demo-capstone2` chưa có bước hoàn — chưa chạy lại.

### 15.3c Kết quả `G8c` *(2026-10-20)*

Migration **`V81`**; bất biến **`B217`–`B221`** (`module/workorder/CLAUDE.md`); bản ghi `CLAUDE.md §0.62`; trả lời FE `BE_RESPONSE_GD_08_2026-10-20_v3.md`.
Đã làm: sổ đầu vào/chuyển giao công đoạn (`POST …/operations/{O}/input`), kết thúc công đoạn (`…/finish`), `GET …/operations`, `complete-production`
(hụt cần lý do + `PERM_MATERIAL_ISSUE_APPROVE`, Q4) · lượt xử lý lại `sourceExecutionId` với nhóm chờ riêng (D7) · vật tư thực dùng `consumption[]` (D4,
không trừ kho) · sản lượng cuối = công đoạn cuối (R12) · version bắt buộc trên báo sản lượng (D5) · dung sai 60 s (Q31) · lệnh cũ `operation_tracking =
false` ⇒ `null` + `PRODUCTION_INPUT_UNKNOWN` (Q22). 🔴 **Lệch D1(a) có chủ đích:** không cột đếm nào trên công đoạn — mọi số dẫn xuất từ sổ (`B217`), chỉ
lưu `completed_at`. Nghiệm thu: `mvn -o clean verify` 1871 + 263, mutation 26/26 (C21 sống sót lần đầu ⇒ thêm test), smoke HTTP 32/32 (lượt `105705`). 🔴 Đối
chiếu bảng A-case của FE trước khi gửi lộ **A25** (công đoạn đầu chỉ kết thúc khi nhận đủ kế hoạch) — BE đã nới, sửa theo FE. Script `demo-capstone2`
(`seed.sh` `report_output`, `verify.sh`) đi qua từng công đoạn — **chưa chạy lại** trên DB trắng.

### 15.3b Kết quả `G8b` *(2026-10-19)*

Migration **`V80`**; bất biến **`B212`–`B216`** (`module/workorder/CLAUDE.md`); bản ghi `CLAUDE.md §0.61`; trả lời FE `BE_RESPONSE_GD_08_2026-10-19_v2.md`.
Đã làm: version bắt buộc trên phiếu nhiều dòng + hai version khi duyệt/từ chối/huỷ (D5) · phiếu bổ sung (`supplemental`, tồn tự do mọi kho nhà máy,
lý do từng dòng) · vượt định mức theo vật tư trên cả phiếu · kiểm tồn nguồn lúc tạo phiếu chờ (Q10) · tự duyệt cần thêm `OVERRIDE` (Q3) · huỷ của người
tạo · `GET /material-issues/{I}` + `decisionContext[]` · ứng viên nguồn `NORMAL`/`SUPPLEMENTAL` · đọc phiếu cho `MANAGE`/`APPROVE`/`OVERRIDE` (Q2) ·
`search` + khoảng ngày · lý do 5–2000 (Q33, cả huỷ WO). Nghiệm thu: `mvn -o clean verify` 1816 + 258, mutation 20/20, smoke HTTP 32/32 (lượt `220914`).
🔴 Smoke lượt đầu lộ **version WO +2** khi phiếu đầu tiên chuyển WO sang `IN_PROGRESS` — sửa ở post + duyệt, guard ở `ProductionFlowE2EIT`. **Áp cho
`G8c`:** báo sản lượng/hoàn thành cũng đổi trạng thái WO — phải `touch()` trước mọi flush, cuối `flushTouched`.

### 15.3a Kết quả `G8a` *(2026-10-19)*

Migration **`V79`**; bất biến **`B207`–`B211`** (`module/workorder/CLAUDE.md`); bản ghi `CLAUDE.md §0.60`; trả lời FE `BE_RESPONSE_GD_08_2026-10-19.md` (v1).
Đã làm: `componentWarehouses` (create/PATCH/plan) + giữ tồn chỉ ở kho dòng (D2) · version bắt buộc + `touch()` + `Idempotency-Key` tuỳ chọn cho
PATCH/plan/release/cancel/reserve/giữ tay/bỏ giữ (D5) · lịch đã chốt (`scheduleRevision`/`calendarRevision`/`scheduleTimeZone`/`scheduleCapturedAt`,
`workMinutes`) + `POST /work-orders/{W}/schedule-preview` chung công thức release (D9) · `POST /work-orders/{W}/release-reservations` · khoảng ngày
trên list · huỷ: chặn phiếu chờ duyệt + trả quota ledger một lần · khoá nguồn WO MAKE (Q7). **Không làm ở `G8a` (để `G8b`):** issue thường phải dùng
reservation của dòng; version trên phiếu cấp. Nghiệm thu: `mvn -o clean verify` 1785 + 256, mutation 16/16, smoke HTTP 39/39 (lượt `210812`).
🔴 Baseline sạch lộ flake `migrate_v78_*` có từ G7c (truy vấn catalog không lọc schema) — đã sửa trong phase.

### 14.3a Kết quả `G7a` *(2026-10-18)*

✅ Xong. Bản ghi đầy đủ `CLAUDE.md §0.57`, bất biến `B187`–`B192`, trả lời FE `BE_RESPONSE_GD_07_2026-10-18.md` (v1). Lệch kế hoạch có chủ đích:

1. **Bốn quyền mới (`D9`) dời sang gói dùng chúng** — `PERM_GOODS_RECEIPT_READ`/`_MANAGE` ở `G7b`, `PERM_PURCHASE_QC_*` ở `G7c`. `G7a` không có
   endpoint nào cần chúng; seed trước là quyền không ai kiểm.
2. **Chặn huỷ/đóng khi còn phiếu nhận `DRAFT` (Q11) dời sang `G7b`** — phiếu nhận `DRAFT` chưa tồn tại.
3. **`history[]` dựng từ cột** (+ `previous_status`), không bảng sự kiện; không có bước do phiếu nhận.
4. **Thêm ngoài kế hoạch:** chặn huỷ phiếu nhận `POSTED` của PO `CLOSED` (đảo nhập sẽ mở lại PO đã đóng); tie-break thứ tự dòng PO (smoke tìm ra).

### 14.3b Kết quả `G7b` *(2026-10-18)*

✅ Xong. Bản ghi đầy đủ `CLAUDE.md §0.58`, bất biến `B193`–`B198`, trả lời FE `BE_RESPONSE_GD_07_2026-10-18_v2.md`. Migration **`V77`** +
`PERM_GOODS_RECEIPT_READ`/`_MANAGE` (ADMIN + MANAGER, dời từ `G7a`). Lệch kế hoạch có chủ đích:

1. **Khoá không phải `@Lock` trên query entity.** Kế hoạch ghi "khoá bi quan dòng PO"; bản đầu làm đúng thế và IT xanh, nhưng smoke HTTP thật
   cho kẻ thua `CONCURRENT_MODIFICATION` thay vì `PLANNED_QUANTITY_EXCEEDED`: open-session-in-view + guard `hasReceiptAccess` đã nạp PO/dòng vào
   EntityManager trước transaction, và Hibernate **kiểm version** thay vì đọc lại khi query khoá (hoặc `refresh` kèm lock mode) gặp entity đã
   được quản lý. Nay: khoá native chỉ id → khởi tạo `order.lines` → `refresh` thường (B194). Mất **hai** vòng smoke mới đúng — vòng hai lộ ra
   collection chưa khởi tạo không được cascade refresh.
2. **Chặn huỷ/đóng PO còn phiếu nhận `DRAFT`** (Q11, dời từ `G7a`) ⇒ mã mới `PENDING_GOODS_RECEIPT`.
3. **Thêm ngoài kế hoạch:** cờ `goods_receipts.quality_held` — đảo nhập phiếu mua không theo lô đã vào hold phải gỡ hold trước, nếu không CHECK
   `reserved + qualityHold <= quantity` từ chối; phiếu cũ (trước `V77`) không có hold nên không được gỡ.
4. **Script seed demo** (`demo-capstone2`, `VIETBIKE`) gọi đường nhận cũ — nay hàng vào `HOLD`. **Chưa chạy lại** các script đó.
5. **Smoke tìm thêm:** đảo nhập phiếu `POSTED` (và post đường cũ) ghi bằng `save` rồi map response ⇒ `version` trả về cũ (bẫy §0.39, lộ ra vì G7b mới đưa `version` lên phiếu nhận). Đổi sang `saveAndFlush`.

### 14.3c Kết quả `G7c` *(2026-10-19)*

✅ Xong. Bản ghi đầy đủ `CLAUDE.md §0.59`, bất biến `B199`–`B204` (purchasing) + `B205`–`B206` (inventory), trả lời FE
`BE_RESPONSE_GD_07_2026-10-18_v3.md`. Migration **`V78`** + `PERM_PURCHASE_QC_READ`/`_MANAGE` (ADMIN + MANAGER). Không mã lỗi mới. Lệch kế hoạch
có chủ đích:

1. **Không có bucket "đã loại" riêng cho hàng không theo lô** (D7): phần loại ở lại `qualityHoldQuantity`, truy theo `qualityBucketId` = id dòng phiếu.
   Thêm một cột tồn mới là đổi mọi tổng tồn — rộng hơn phạm vi QC.
2. **Chặn đi vòng QC chỉ phủ đổi trạng thái lô** (R13) + đảo nhập phiếu đã QC. `POST /v1/inventory/adjust` trên lô `HOLD` vẫn chạy (có từ trước G7)
   — ghi là giới hạn trong v3 §6.
3. **Khoá phiếu đúng khuôn `B194`** (native id lock → find → khởi tạo dòng → refresh) ngay từ đầu, nhờ bài học G7b: smoke lần đầu đã ra
   `200` + `409 CONCURRENT_MODIFICATION` đúng.
4. **Smoke tìm thêm:** `childLots[].parentLotId` trong response lệnh QC là `null` (cột phản chiếu chỉ-đọc chưa có giá trị trên lô con vừa tạo) —
   IT chỉ kiểm cột DB nên xanh. Sửa + assertion trên response.
5. **Mutation lộ hai lỗ hổng test trước khi gửi:** `splitLot` trừ phần đã giữ chỗ chưa test nào chứng minh (thêm case); nhánh "QC xong ⇒ lô hết chờ"
   của `existsPendingQualityForLot` chưa IT nào chạm (thêm quyết định thứ hai vào IT).

### 14.4 Breaking change dự kiến

| Thay đổi | Breaking? |
|---|---|
| Endpoint/field/quyền mới (nháp, `PATCH`, `close`, danh sách phiếu nhận, ứng viên, QC) | **Không** (additive) |
| Đổi tên trạng thái PO (`D1`) | **Có — wire**. Mọi client đọc `SENT`/`RECEIVED` hỏng. Có tiền lệ `V26` |
| Hàng mua nhận vào `HOLD` (`D4`) | **Có — về số liệu**: tồn khả dụng/MRP/dashboard giảm cho **mọi** lần nhận mua mới; hàng chỉ dùng được sau QC |
| `expectedVersion` bắt buộc trên command PO | **Có** — request trước đây không gửi gì nay 400 |
| Lý do bắt buộc khi huỷ/đóng | **Có** — `cancel` trước đây không có body |
| Khoá bi quan + mã lỗi vượt số mở | **Có, hẹp** — 409 đổi từ `CONCURRENT_MODIFICATION` sang mã vượt số |
| Mã PO do server cấp | **Không** nếu `D2` = (a) (đường song song) |
| Giá trị tồn | **Không làm** nếu `D8` = (a) |

### 14.5 Ngoài phạm vi *(theo FE §1.2 + quyết định BE)*

PR/RFQ, cổng nhà cung cấp, hoá đơn/thanh toán, thuế/chiết khấu/phí/tỷ giá/landed cost; trả hàng/hoàn tiền; đảo ngược phiếu nhận/QC **đã POSTED** (trừ đường `REVERSAL` cũ giữ theo `D3`); nhận dư/dung sai; sửa PO sau xác nhận; nhập PO từ Excel; số sê-ri; cấp hàng xuyên nhà máy; tự tạo PO bù hoặc tự phát hành WO. Không đổi phiếu nhập/QC **sản xuất** thành endpoint mua hàng (QC sản xuất một phần là GĐ9). Không đụng WO/xuất vật tư/thực thi/WIP (GĐ8), và không trả lời thay các câu còn mở của GĐ6 (Q10/Q16/Q26/Q27/Q28). **Giá trị tồn + bình quân di động: ngoài phạm vi theo khuyến nghị `D8`** — nếu user chọn làm thì là `G7d` riêng.

---

## 15. `G8` — FE handoff Giai đoạn 8: Lệnh sản xuất, cấp vật tư, công đoạn và dở dang ✅ BE HOÀN THÀNH — `G8a`/`G8b` 2026-10-19, `G8c`/`G8d` 2026-10-20 (chờ FE nghiệm thu)

Nguồn: `FE_HANDOFF_GD_08_2026-10-14.md` (v1, P1, 717 dòng). FE/mock đã xong: nháp lệnh sản xuất + xem trước lịch, kho cho **từng dòng** vật tư, cấp vật tư nhiều dòng với duyệt bổ sung, **xác nhận đầu vào và chuyển sản lượng giữa công đoạn**, **ghi kết quả + xử lý lại (retry)**, **vật tư thực dùng theo từng dòng cấp**, kết thúc công đoạn / hoàn thành lệnh có lý do hụt, màn hình **dở dang**, và **hoàn vật tư**. BE phải (1) trả lời Q1–Q37 §5, (2) đối chiếu R01–R22 với API có sẵn, (3) chỉ code phần thiếu, (4) trả `BE_RESPONSE_GD_08_<ngày>.md` theo `BETaskDeliveryRules.md`. FE chưa gọi live lần nào.

> 🔴 **Handoff lớn nhất tới nay, vượt cả `G7`:** 22 yêu cầu, **37 câu hỏi**, 40 ca nghiệm thu, và nó **đổi mô hình miền** của `module/workorder` chứ không chỉ thêm endpoint. Ba thứ dưới đây là lý do:
> 1. **Công đoạn hiện KHÔNG có trạng thái và KHÔNG có bộ đếm nào.** `WorkOrderOperation` có đúng 11 field: id, WO, nguồn routing, thứ tự, tên, trung tâm, phút setup/run, lịch, lý do điều chỉnh. Mọi con số sản lượng được **dẫn xuất** từ `production_executions`. FE đòi `status` + `inputQuantity`/`freshProcessed`/`freshRemaining`/`good`/`scrap`/`historicalRework`/`pendingRework`/`transferred`/`transferable` — tức một **sổ đầu vào + chuyển giao + nhóm xử lý lại** chưa từng tồn tại, cộng một máy trạng thái cấp công đoạn.
> 2. **Dòng vật tư của lệnh KHÔNG có kho.** `WorkOrderComponentLine` chỉ có `quantityPer`/`scrapRate`/`requiredQuantity`/`issuedQuantity`. Giữ tồn hiện phân giải theo **phạm vi nhà máy** (`§0.45` #6 — có chủ đích: thiếu ở *mọi* kho của nhà máy mới là thiếu thật). FE đòi kho theo **từng dòng** và "không bao giờ lấy kho khác dù còn hàng" — đổi hành vi giữ tồn, không phải thêm field.
> 3. **Hoàn vật tư: `grep MaterialReturn` toàn repo = 0 kết quả.** Không entity, không bảng, không loại biến động, không quyền. Phần này là một module con mới trọn vẹn.
>
> 🔴 **Hai thứ FE đòi mà repo đã CỐ Ý loại bỏ hai lần — phải chốt lại, không âm thầm làm:**
> - **Giá trị cấp/dùng/hoàn/dở dang.** Công thức §3.1 của FE dựa trên "giá bình quân di động theo policy GĐ4" — **policy đó không tồn tại**: `stock_movements`/`stock_balances` không có cột giá nào, `P3` (§0.31 #2) loại Material Price Variance đúng vì lý do đó, và `G7 D8` (2026-10-18) **vừa hoãn** lớp giá trị tồn. Chính FE cũng cho `value = null` (Q35) ⇒ khuyến nghị trả `null`, xem `D10`.
> - **`COMPLETED` do người dùng ra lệnh.** Hiện `COMPLETED` **tự** sinh khi sản lượng tích luỹ chạm kế hoạch (`B53`, `§0.5`), và `CLOSED` là trạng thái khoá hoàn toàn do `P6` chốt với user. FE đòi `POST .../complete-production` có lý do hụt và nói rõ "không tự đóng". Đây là đổi `B53` — xem `D6`.
>
> ✅ **Tin tốt, lớn hơn tưởng:** đường cấp vật tư **đã có phần lớn**. `MaterialIssueStatus` đã đủ `PENDING_APPROVAL`/`POSTED`/`REJECTED`/`CANCELLED`; `MaterialIssueLine` đã có `reasonCode`, `issueReason`, `sourceExecution`, `overIssue`, `overrideReason`, `requestedLotCode`; endpoint lô `POST /work-orders/{W}/material-issues` + `approve` + `reject` đã có. Phần thiếu là *hẹp*: huỷ của người tạo, chi tiết một phiếu, ứng viên nguồn, `expectedVersion`.

### 15.1 Đối chiếu yêu cầu FE ↔ code thật *(rà 2026-10-19 bằng đọc source, chưa gọi live)*

Phạm vi đã rà: toàn bộ 43 mapping của 4 controller trong `module/workorder`; cột của `WorkOrderOperation`, `ProductionExecution`, `WorkOrderComponentLine`, `MaterialIssue`, `MaterialIssueLine`; `MaterialIssueStatus`, `WipTransactionType`; grep `MaterialReturn` toàn `src/main`; grep quyền `PERM_PRODUCTION_EXECUTION_*`/`PERM_WIP_*`/`PERM_MATERIAL_*` trong migration. **Chưa rà:** `WorkOrderService` (`plan`/`release`/`cancel`/`close`/`scheduleOperations`) chi tiết; `MaterialIssueService.postNew` (luật `PENDING_APPROVAL` hiện tại — điều kiện nào đẩy cả phiếu sang chờ duyệt); `MaterialReservationService.reserveAutomatically` (FEFO + phạm vi kho); `ProductionExecutionService.reportNew` (gate `B54`/`B75`); `WipTransactionService`; `WorkOrderCostAccumulator` (chồng lấn với "vật tư thực dùng" của `D4`); `WorkOrderDemandAllocationService` (cần cho R18/Q20/Q21); `CapacityBoardService` đọc lịch đã chốt tới đâu (R03).

| Nhóm | FE yêu cầu | Hiện trạng code | Loại |
|---|---|---|---|
| Đọc WO (R21) | List thêm `search`/khoảng ngày | `GET /v1/plants/{P}/work-orders` đã có `search` (`F7`), **thiếu** khoảng ngày | **CODE** nhỏ |
| Nháp MAKE canonical (R01) | Giữ cùng ID/nguồn/phân bổ | ✅ **Đã đúng** — `createFromMrp` + ledger `G6b`, `WorkOrderDemandAllocation` (`F6`) | **Trả lời** |
| Kho cho từng dòng vật tư (R02, R04, Q8) | `componentWarehouses` lúc tạo/`plan`; giữ tồn **chỉ** ở kho đó | 🔴 `WorkOrderComponentLine` **không có** cột kho; `reserveAutomatically` phân giải **phạm vi nhà máy** có chủ đích | **CODE** + migration, 🔴 `D2` |
| Ảnh chụp thiết kế (R02, Q5) | Chụp BOM/routing lúc `plan`, bất biến | ✅ **Đã đúng** — `B12` (BOM lúc create), `B49` (routing snapshot) | **Trả lời**; 🔴 lệch: repo chụp lúc **create**, FE nói lúc **plan** |
| Lịch đã chốt (R03) | `scheduleRevision`/`calendarRevision`/`timeZone`/`capturedAt` + `workMinutes` từng dòng | Lịch sinh ở `release()` (`B89`); **không có** revision/`capturedAt`/`workMinutes` trên wire | **CODE** |
| Xem trước lịch của WO (§3.3) | `POST /work-orders/{W}/schedule-preview` | `G5` có `POST /v1/plants/{P}/capacity/preview` (theo nhà máy). Cấp WO: **không có** | **CODE** |
| `expectedVersion` (R22, D5) | Bắt buộc trên **mọi** lệnh WO/phiếu cấp | `@Version` có trên entity (`BaseEntity`), **không** lệnh nào của `module/workorder` nhận | **CODE** |
| Giữ tồn một phần (R04, Q12) | Thiếu ⇒ giữ phần được + `BLOCKED` | ✅ **Đã đúng** — "thành công một phần là có chủ đích" (`§0.9`), `BLOCKED` persist thật (`D9`) | **Trả lời** |
| Giải phóng phần chưa cấp (§3.3) | `POST /work-orders/{W}/release-reservations` | Có `cancelActiveReservations` **nội bộ** (gọi từ `cancel`/`close`), **không** endpoint | **CODE** nhỏ |
| Cấp vật tư nhiều dòng + chờ duyệt (R07, R08, R09) | 1–500 dòng, một dòng vượt BOM ⇒ **cả phiếu** chờ duyệt, duyệt kiểm lại | ✅ Phần lớn **đã có** — `PENDING_APPROVAL`, `approve`/`reject`, `reasonCode`/`sourceExecution`/`overIssue` trên dòng | **Trả lời + CODE** nhỏ (`expectedVersion`, kiểm lại lúc duyệt) |
| Huỷ phiếu chờ của người tạo (§1.4) | `POST .../material-issues/{I}/cancel`, chỉ người tạo | `CANCELLED` **có** trong enum, **không** endpoint nào dẫn tới | **CODE** |
| Chi tiết một phiếu cấp + ứng viên nguồn (§3.3) | `GET /material-issues/{I}`, `GET .../material-issue-candidates` | Chỉ có list (`GET /v1/material-issues`) | **CODE** |
| **Trạng thái + sổ đầu vào của công đoạn** (R10, R14) | `status`, `input`, `transferable`, `transferred`, `POST .../operations/{O}/input`, `.../finish` | 🔴 `WorkOrderOperation` **không có** trạng thái/bộ đếm nào; không endpoint nào | **CODE** — phần lớn nhất, 🔴 `D1` |
| **Xử lý lại (retry) + nhóm chờ** (R11, Q17) | `sourceExecutionId`, `retryUsed`, `pendingRework` ≠ `historicalRework` | 🔴 `ProductionExecution` **không có** `sourceExecutionId`; `reworkQuantity` hiện là một số cộng dồn, không phân biệt lượt/nhóm chờ | **CODE** + 🔴 `D7` |
| **Vật tư thực dùng** (R13, Q18) | `consumption[] {issueLineId, quantity}` ghi cùng execution | 🔴 Không có gì. ⚠️ Chồng lấn `WorkOrderCostAccumulator` (`P3`) — nó cộng chi phí vật tư **lúc cấp**, không lúc dùng | **CODE** + `D4` |
| Sản lượng cuối = công đoạn cuối (R12) | `finalGood` chỉ từ công đoạn cuối | 🔴 Hiện `actualGoodQuantity` của WO cộng **mọi** lần báo, không phân biệt công đoạn (`§0.5`) | **CODE**, đổi số liệu |
| Hoàn thành WO có lý do hụt (R14, Q4, Q23) | `POST .../complete-production` + lý do nếu hụt, **không** tự đóng | 🔴 `COMPLETED` **tự** sinh (`B53`); `close` của `P6` là `CLOSED` khoá hoàn toàn | **CODE** + 🔴 `D6` |
| **Dở dang** (R15) | `GET /plants/{P}/wip`, `GET /work-orders/{W}/wip` theo `section` | Có sổ `wip_transactions` + 2 endpoint ghi/đọc thô (`/wip-transactions`). **Không** có projection theo mục | **CODE** + `D8` |
| **Hoàn vật tư** (R16, R17) | 4 endpoint, nguồn là dòng cấp, nguyên tử, không khôi phục giữ tồn | 🔴 **Không có gì** — grep = 0 | **CODE** + migration, `D3` |
| Liên kết biến động kho (R17) | `sourceType`/`materialIssueId`/`materialReturnId`/`sourceIssueLineId` trên biến động | `StockMovement` có `referenceType`/`referenceId` **dạng text tự do** (`§0.43` #8 đã ghi là nợ) | **CODE** nhỏ hoặc trả lời |
| Nguồn cung WO cho MRP (R18, Q20) | Dùng sản lượng cuối **thực tế** − phiếu nhập đã duyệt | `WorkOrderSupplyService` tính theo kế hoạch; `availableToReceipt` có sẵn (`B16`) | **CODE** + `D11` |
| Hạn ngạch khi huỷ WO (R06, Q21) | Trả quota chuyển đổi + phân bổ đơn bán **đúng một lần** | `G6b` đã làm cho huỷ **đơn mua**; huỷ **WO** thì chưa | **CODE** |
| 3 quyền mới (§1.4, Q1, Q34) | `PERM_WIP_READ`, `PERM_MATERIAL_RETURN_READ`/`_MANAGE` | Có `PERM_WIP_MANAGE`, **không** có `PERM_WIP_READ`; không có quyền hoàn vật tư | **CODE** + migration seed, `C10` kích hoạt |
| `capabilities` trên projection (§3.2) | Object cờ boolean + lý do từ chối | Không có ở bất kỳ module nào | **Trả lời — khuyến nghị không làm** (`D12`) |
| Lệch múi giờ ≤ 60s (Q31) | `actualEndedAt` cho lệch 60 giây | `F7` đã ép `@NotNull` + validate thứ tự; **không** có dung sai | **CODE** nhỏ |
| Lý do 5–2000 ký tự (Q33) | Chuẩn hoá mọi lý do GĐ8 | Hiện chỉ "không rỗng" (`APPROVAL_REASON_REQUIRED`); `G5` đã có tiền lệ ≥ 5 cho điều chỉnh lịch | **CODE** nhỏ |
| `X-Trace-Id`, số/ngày/phân trang (R20, R21, Q24, Q29, Q32) | — | ✅ **Đã có** (`TraceIdFilter`, `@Digits(13,6)` từ `G4`, `PageableFactory`, múi giờ nhà máy từ `C2-8`) | **Trả lời** |

### 15.2 Quyết định phải chốt với user trước khi code ✅ *(chốt 2026-10-19, `AskUserQuestion`: D0 = (b) tách 4 làm liền; D2 = (a) dòng cũ `NULL` = cả nhà máy; **D11 = hoãn** — giữ nguồn cung WO theo kế hoạch; D1, D3–D10, D12 theo cột "Khuyến nghị BE")*

| # | Quyết định | Hai phương án | Khuyến nghị BE |
|---|---|---|---|
| **D0** | **Tách phase hay làm một lượt** | (a) một lượt `G8`; (b) tách **4**: `G8a` nháp WO + kho từng dòng + lịch → `G8b` cấp vật tư (hoàn thiện phần hẹp) → `G8c` công đoạn: đầu vào/chuyển giao/retry/vật tư dùng/hoàn thành → `G8d` dở dang + hoàn vật tư | 🔴 **(b)**, đúng tiền lệ `D0` của `G6`/`G7`. Lớn hơn `G7`: 22 yêu cầu, 37 câu hỏi, 40 ca, **cộng** ba thứ chưa tồn tại. `G8c` một mình đổi mô hình miền — không gộp nó với bất cứ gì |
| **D1** | 🔴 **Máy trạng thái + sổ đầu vào cấp công đoạn** | (a) thêm trạng thái + bộ đếm lên `work_order_operations` + bảng chuyển giao riêng, **dẫn xuất vẫn là nguồn cho số cũ**; (b) tính mọi thứ live từ `production_executions`, không thêm cột | **(a)**. (b) không diễn đạt được "đầu vào đã xác nhận" — một đại lượng **không** suy được từ lần báo sản lượng nào (FE Q14 nói rõ: không suy sản lượng từ lượng vật tư). Nhưng đây là thay đổi lớn nhất của phase: mọi số sản lượng hiện đang dẫn xuất, thêm cột là thêm **nguồn thứ hai** ⇒ phải có bất biến "cột là ảnh chụp của sổ, sổ là chân lý" + IT chứng minh |
| **D2** | 🔴 **Kho cho từng dòng vật tư** | (a) thêm `warehouse_id` lên `work_order_component_lines` + giữ tồn chỉ ở kho đó; (b) giữ phạm vi nhà máy như hiện tại | **(a)**, nhưng **có điều kiện**: `§0.45` #6 ghi rõ phạm vi nhà máy là có chủ đích, và `WorkOrderService.release` gate 100% readiness (`B13`/gate 1a) sẽ **chặt hơn hẳn** — một lệnh hôm nay release được có thể thành `BLOCKED` vì hàng nằm kho khác. Dữ liệu cũ: **không backfill**, dòng cũ `NULL` = "mọi kho của nhà máy" (hành vi cũ) |
| **D3** | **Hoàn vật tư** | (a) entity + bảng `material_returns`/`_lines` trong `module/workorder` + 2 loại biến động mới; (b) tái dùng `adjust` của inventory | **(a)** — (b) mất hẳn liên kết về dòng cấp gốc (R16 đòi "nguyên nguồn"), và `§0.43` #8 đã ghi `referenceType` dạng text là nợ. Hoàn **không** khôi phục giữ tồn, **không** giảm `grossIssued` (Q11) |
| **D4** | **Vật tư thực dùng** (`consumption[]`) | (a) bảng nối `production_execution_consumptions` (dòng cấp → lượng), **không** đụng tồn kho; (b) không làm, suy theo BOM | **(a)**; (b) chính FE cấm (Q18: không `Good × BOM`). 🔴 Phải đối chiếu với `WorkOrderCostAccumulator` (`P3`) — nó cộng chi phí vật tư **lúc cấp**; nếu thêm "giá trị đã dùng" mà không nói rõ hai thứ khác nhau thì sẽ có **hai con số chi phí vật tư** cho cùng một lệnh |
| **D5** | `expectedVersion` trên mọi lệnh | (a) bắt buộc, như `G7a`; (b) tuỳ chọn | **(a)** — `G7a` đã làm đúng thế cho đơn mua, và `§0.40` #2 là bài học: không có nó thì hai lệnh đồng thời cùng ghi đè im lặng |
| **D6** | 🔴 **`COMPLETED` tự động vs ra lệnh** | (a) thêm `POST .../complete-production` (có lý do hụt) **và giữ** `COMPLETED` tự động khi đạt kế hoạch; (b) bỏ tự động, chỉ còn ra lệnh | **(a)**. (b) đổi `B53` — một bất biến có dữ liệu lịch sử và được `§0.5` ghi là quyết định; `D11` cũng đã từ chối "hoãn `complete()`" vì WO sẽ kẹt `IN_PROGRESS` vô thời hạn. Với (a), `complete-production` là đường **cho trường hợp hụt**; `CLOSED` của `P6` không đụng |
| **D7** | **Nhóm xử lý lại (retry)** | (a) `source_execution_id` + `retry_used` trên `production_executions`, `pendingRework` = tổng `rework − retryUsed`; (b) không làm, giữ `reworkQuantity` một số | **(a)** — nhưng nghĩa của `reworkQuantity` hiện tại **đổi vai**: nó trở thành "lượt đã ghi" (historical), còn "chờ xử lý" là số dẫn xuất mới. Mọi chỗ đọc `actualReworkQuantity` phải rà (`§11.3`) |
| **D8** | **Dở dang** | (a) projection đọc theo `section`, tính live từ sổ có sẵn; (b) bảng tổng hợp riêng | **(a)** — cùng lối `CapacityBoardService`/`MrpCalculationService` (không lưu kết quả trung gian). Cộng `PERM_WIP_READ` mới (hiện chỉ có `_MANAGE`) |
| **D9** | **Lịch đã chốt + xem trước cấp WO** | (a) `scheduleRevision`/`calendarRevision`/`capturedAt` lên `work_orders` + endpoint xem trước riêng cho WO; (b) chỉ trả lịch, không revision | **(a)** — R03 đòi "công suất đọc lịch đã chốt **trực tiếp**", và `§0.53` #6 đã ghi release và xem trước **khác công thức** (release không chia `capacityUnits`); không có revision thì FE không phân biệt được lịch nào đang hiển thị |
| **D10** | 🔴 **Giá trị cấp/dùng/hoàn/dở dang** | (a) **hoãn** — trả `null`/vắng mặt (đúng Q35 + R19); (b) dựng lớp giá bình quân di động | **(a) hoãn.** `P3` loại một lần, `G7 D8` hoãn một lần nữa cách đây **một ngày**. Làm (b) ở đây là dựng lớp actual-costing trong một phase vốn đã lớn nhất; nếu user muốn thì đó là phase riêng, **trước** `G8d` (hoàn vật tư cần giá để trả `returnValue`) |
| **D11** | **Nguồn cung WO cho MRP theo sản lượng thực** | (a) đổi `WorkOrderSupplyService` sang `finalGood − phiếu nhập đã duyệt` cho WO **terminal**; (b) giữ theo kế hoạch | **(a)**, nhưng 🔴 **đổi số MRP** — lần thứ ba trong tuần (`G6b` đã đổi hai lần). Phải đo A/B trên `D26-OMNIPLANT` và báo FE, đúng cách `G6b` làm |
| **D12** | `capabilities` trên mọi projection | (a) **không làm**; (b) trả object cờ + lý do từ chối | **(a)** — FE tự nói "mutation vẫn phải kiểm lại ở server", nên đây là tiện lợi hiển thị. Chưa module nào trong repo có, và nó nhân đôi luật phân quyền ra tầng đọc (`§11.5`) |

> **Bốn thứ KHÔNG hỏi vì code đã trả lời:** Q12 (giữ tồn một phần — `§0.9` đã thế), Q13 (`ISSUED` ↔ `CONSUMED` — enum hiện là `CONSUMED`, chỉ cần ánh xạ), Q24/Q29/Q32 (giới hạn số, `X-Trace-Id`, múi giờ nhà máy — đã có từ `G4`/`C2-8`), Q36 (chứng từ đã ghi là bất biến — repo chưa bao giờ có `PATCH`/`DELETE` cho chúng).
>
> 🔴 **Một lệch cần nêu trong bản trả lời FE, không phải quyết định:** FE Q5 nói "nháp thủ công chụp thiết kế lúc `plan`", nhưng repo chụp BOM **lúc create** (`B12`) và routing lúc create qua `createInternal` (`B49`/`§0.8` #3). Hành vi hiện tại **không** sai — chỉ khác thời điểm FE giả định.

### 15.3 Gói công việc *(thứ tự thực thi; mỗi gói một commit theo `dev-workflow.md §6.6`)*

| Gói | Nội dung | Phụ thuộc |
|---|---|---|
| `G8-0` | Đọc nốt phần "chưa rà" §15.1 (ưu tiên `MaterialIssueService.postNew`, `reserveAutomatically`, `ProductionExecutionService`, `WorkOrderCostAccumulator`, `WorkOrderDemandAllocationService`) · baseline `mvn -o clean verify` trên **`git worktree` sạch** · chạy live WO/cấp vật tư/báo sản lượng hiện có để biết hành vi **trước** khi đổi | — |
| `G8a` | **Nháp WO + kho từng dòng + lịch:** `componentWarehouses` (`D2`) · `expectedVersion` mọi lệnh (`D5`) · lịch đã chốt + revision (`D9`) · `schedule-preview` cấp WO · `release-reservations` · khoảng ngày trên list · trả quota khi huỷ WO (R06) | `D2`,`D5`,`D9` · migration **`V79`** |
| `G8b` | **Cấp vật tư:** huỷ phiếu chờ của người tạo · `GET /material-issues/{I}` · ứng viên nguồn · kiểm lại lúc duyệt + `expectedIssueVersion` · tự duyệt cần cả hai quyền (Q3) · lý do 5–2000 (Q33) | `G8a` |
| `G8c` | 🔴 **Công đoạn:** trạng thái + sổ đầu vào/chuyển giao (`D1`) · retry + nhóm chờ (`D7`) · vật tư thực dùng (`D4`) · sản lượng cuối = công đoạn cuối · `finish` + `complete-production` (`D6`) · dung sai 60s | `G8b`, `D1`,`D4`,`D6`,`D7` |
| `G8d` | **Dở dang + hoàn vật tư:** projection theo `section` (`D8`) · entity + 4 endpoint hoàn vật tư (`D3`) · liên kết biến động kho · 3 quyền mới + seed (`C10`) | `G8c`, `D3`,`D8`,`D10` |
| `G8e` | *(chỉ nếu `D11` = (a))* Nguồn cung WO theo sản lượng thực + đo A/B trên `D26-` | `G8c` |
| `G8-9` | `mvn -o clean verify` + nghiệm thu mutation (nhắm: trần đầu vào, chuyển giao nguyên tử, trần nhóm retry, trần vật tư dùng theo từng dòng cấp, trần hoàn theo phần chưa dùng, quota huỷ đúng một lần) + smoke HTTP thật + `BE_RESPONSE_GD_08_<ngày>.md` + docs (`CLAUDE.md §0.60`, `module/workorder/CLAUDE.md`, `module/inventory/CLAUDE.md`, `roles-and-permissions.md`, `api-guide-for-frontend.md`) | tất cả |

🔴 **Mỗi gói tự nghiệm thu đủ** (`mvn -o clean verify` + mutation + smoke + một bản `_vN` cho FE) nếu `D0` = tách — đúng cách `G6`/`G7` đã làm.

### 15.4 Breaking change dự kiến

| Thay đổi | Breaking? |
|---|---|
| Endpoint/field/quyền mới (xem trước lịch, đầu vào, kết thúc, dở dang, hoàn vật tư) | **Không** (additive) |
| Kho cho từng dòng vật tư (`D2`) | **Có — về hành vi**: lệnh có hàng ở kho khác của cùng nhà máy nay `BLOCKED` thay vì release được. Dòng cũ `NULL` ⇒ giữ hành vi cũ |
| `expectedVersion` bắt buộc | **Có** — lệnh trước đây không gửi gì nay 400 |
| Sản lượng cuối = công đoạn cuối | **Có — về số liệu**: WO nhiều công đoạn nay báo số khác (hiện đang cộng mọi lần báo) |
| `reworkQuantity` đổi vai (`D7`) | **Có — về nghĩa**: trở thành "lượt đã ghi", "chờ xử lý" là số mới |
| Nguồn cung WO theo sản lượng thực (`D11`) | **Có — số MRP đổi**, nếu `D11` = (a) |
| Lý do 5–2000 ký tự | **Có, hẹp** — lý do 1–4 ký tự nay 400 |
| `COMPLETED` tự động | **Không đổi** nếu `D6` = (a) |
| Giá trị dở dang/hoàn | **Không làm** nếu `D10` = (a) |

### 15.5 Ngoài phạm vi *(theo FE §1.2 + quyết định BE)*

Phiếu nhập sản xuất / `HOLD` / QC / truy xuất nguồn gốc kiểu mới của GĐ9 (repo **đã có** vòng đời phiếu nhập + QC từ `F2`/`D5` — GĐ9 là bản nâng cấp, không phải dựng mới); giao hàng/hoá đơn/thanh toán; MES/OEE; số sê-ri trong luồng này; quy trình song song/rẽ nhánh (Q15 — GĐ8 chỉ tuần tự); chuyển vật tư giữa kho để bù; vật tư thay thế ngoài BOM; chi phí nhân công/chung/kế toán/định giá phế đầy đủ; đảo/sửa chứng từ đã ghi (Q36); tự mua bù / tự lập WO bù / tự đóng WO; chính sách `fulfilled`/`open` của đơn bán còn mở từ GĐ6 (Q10/Q16/Q26/Q27 của **GĐ6**, không trả lời thay). **Giá trị tồn/dở dang: ngoài phạm vi theo khuyến nghị `D10`** — nếu user muốn làm thì là phase riêng, xếp **trước** `G8d`.

---

## 16. `G9` — FE handoff Giai đoạn 9: Nhập thành phẩm và kiểm tra chất lượng 🔄 ĐANG CHẠY — `G9a` ✅ 2026-10-21

Nguồn: `FE_HANDOFF_GD_09_2026-10-15.md` (v1, P1, 559 dòng). FE/mock đã xong: danh sách + tạo/sửa **nháp phiếu nhập từng phần**, gửi/rút/huỷ, hàng chờ duyệt, **duyệt nguyên tử vào `HOLD`**, màn hình **kiểm tra thành phẩm**, **ghi QC từng phần nhiều lượt**, chi tiết/lịch sử QC, **truy xuất nguồn** (công đoạn cuối → vật tư cấp), cộng các màn hình đọc liên quan (WO, dở dang, tồn kho/lô, MRP, đơn bán). BE phải (1) trả lời Q1–Q32 §5, (2) đối chiếu R01–R24 với API có sẵn, (3) chỉ code phần thiếu, (4) trả `BE_RESPONSE_GD_09_<ngày>.md` theo `BETaskDeliveryRules.md`. FE chưa gọi live lần nào (§2 ghi rõ cả năm mảnh bằng chứng đều trống).

> 🔴 **Handoff này khác hẳn `G7`/`G8` ở chỗ: repo ĐÃ CÓ cả vòng đời chứng từ lẫn QC từ `F2`/`D5`, và cỗ máy FE cần nhất thì vừa được dựng HÔM QUA cho mua hàng.** Vì thế `G9` phần lớn là **nâng cấp**, không phải dựng mới:
> - `ProductionReceiptStatus` đã đủ `DRAFT`/`PENDING_APPROVAL`/`APPROVED`/`REJECTED`/`CANCELLED`; `post`/`submit`/`approve`/`reject`/`qc-disposition` đã chạy từ `F2`, `candidates` + list phẳng từ `F8`.
> - **`G7c` (2026-10-19) đã dựng đúng thứ QC từng phần cần:** `PurchaseQualityDecision` (bảng bất biến mang **lượng đạt/lượng loại**, lô con, biến động, ai–khi) + `InventoryMovementService.splitLot` (lô con `AVAILABLE`/`REJECTED`, phần chờ **ở lại lô cha `HOLD`**, `LOT_SPLIT_OUT`/`LOT_SPLIT_IN` cùng một `Idempotency-Key`, tồn thực tế **không đổi**, mã lô con kiểm không phân biệt hoa thường — `B205`). Đó gần như nguyên văn R10/Q15 của FE.
> - **`G8c` (2026-10-20) đã đóng R03 trước khi FE hỏi:** `actualGoodQuantity` nay **chỉ** là good của công đoạn cuối (`B219`), đúng cái FE gọi là `G` và Q3 đang xin xác nhận.
>
> 🔴 **Ba thứ chưa tồn tại — đây là phần code thật:**
> 1. **QC từng phần không biểu diễn được.** `ProductionReceipt.qcResult` là **một** giá trị enum, `qcAt`/`qcBy`/`qcReason` là **một** bộ; `QualityDisposition` **không có cột lượng** nào và `lot_id` là `NOT NULL` (theo đúng định nghĩa "một dòng cho mỗi lô", `D5`). Nên "đạt 3 / loại 1 / còn chờ 1" **không có chỗ để lưu**, và `isQcDecided()` (`qcResult != null`) đang là cổng chặn QC lần hai.
> 2. **Phân bổ theo từng lượt báo (contributions).** Trần tổng **đã có** (`B16`/`B76`: `actualGoodQuantity − completedQuantity − Σ phiếu DRAFT/PENDING`), nhưng **không** có gì nối một phiếu nhập với *những lượt báo cụ thể* nào. R05 đòi "mỗi nguồn: tổng phân bổ ≤ good của chính nguồn đó" và R13 đòi truy xuất về execution thật — cả hai cần bảng nối mới. `source_wip_trace_ids` hiện là **một chuỗi text** ghi lúc duyệt, chỉ đọc nguyên khối.
> 3. **Nửa còn lại của vòng đời + ba endpoint đọc.** Không có `GET` một phiếu, `PATCH` nháp, `withdraw`, `cancel` (🔴 `CANCELLED` **có** trong enum từ `F2` mà **không endpoint nào dẫn tới** — `§0.45` đã ghi đúng điều này), `GET .../source`, `GET .../events`. `submit`/`approve` **không nhận body** ⇒ không có `expectedVersion`. `ProductionReceiptResponse` **không có** `version`, `receivedOn`, `expiresOn`, `qualityState`, bộ đếm A/D/W/U, `contributions`, `allowedActions`.
>
> 🔴 **Hai thứ repo đã CỐ Ý loại bỏ, nay FE xin lại — phải chốt, không âm thầm làm:**
> - **Giá trị thành phẩm (Q26, R23, F2).** `P3` (`§0.31` #2) loại Material Price Variance vì `stock_movements` không có cột giá; `G7 D8` hoãn lớp giá trị tồn (2026-10-18); `G8 D10` hoãn lần nữa (2026-10-19). Chính FE cho `value` unknown ≠ 0 ⇒ khuyến nghị **hoãn lần thứ tư**, chỉ trả `valuationStatus = PENDING_COSTING` và **vắng mặt** `receiptValue` (xem `D10`).
> - **404 cho ID ngoài phạm vi (§1.3, R18).** Repo trả **403** vì guard tra bản ghi để suy công ty rồi từ chối **trước** service — đúng với cả tài khoản quản trị, có sẵn toàn repo (`§0.50` #9, `§0.30`). Đổi sang 404 là đổi hành vi của **mọi** module, không phải của `G9`.
>
> ✅ **Tin tốt thứ hai: bốn yêu cầu đã đúng sẵn, chỉ cần trả lời.** R04 (nháp/chờ duyệt giữ quota — `B16`/`B76`), R07 (duyệt nguyên tử: `approve` một transaction ghi lô `HOLD` + biến động + `completedQuantity` + WIP `OUTPUT_RECEIPTED` + audit + khoá idempotency), R12 nửa đầu (`InventoryLotService.changeStatus` **đã** chặn gỡ `HOLD` của phiếu nhập sản xuất, `C2-2`), R16 nửa đầu (`G6a` đã có `qcAcceptedQuantity` + `deliveredQuantity` luôn `null` trên dòng đơn bán).

### 16.1 Đối chiếu yêu cầu FE ↔ code thật *(rà 2026-10-20 bằng đọc source, chưa gọi live)*

Phạm vi đã rà: 8 mapping phiếu nhập/QC của `ManufacturingExecutionController`; cột của `ProductionReceipt`, `QualityDisposition`, `ProductionReceiptStatus`; `ProductionReceiptResponse`/`CandidateResponse`/`PostRequest`; `WorkOrder.availableToReceipt`/`canReceipt`; trần `B16` trong `ProductionReceiptService` (dòng 514–518) + `ensureSamePayload`/`payloadHash`; `@PreAuthorize` của cả service; grep `PERM_PRODUCTION_RECEIPT_READ`/`PERM_PRODUCTION_QC_READ` trong `src/main` **và** migration; `InventoryMovementService.splitLot` + `LotSplitCommand`/`Result`; `PurchaseQualityDecision`; `InventoryLotService.changeStatus` (cổng `HOLD`); `WorkOrderRepository.aggregateOpenSupply`; `InventoryLotRepository.existsByItemItemIdAndLotCodeIgnoreCase`; `SalesOrderLineResponse` (6 số tiến độ `G6a`). **Chưa rà:** `ProductionReceiptService.post`/`submit`/`approve`/`qcDisposition` **đọc hết từng dòng** (nhất là thứ tự tạo lô ↔ biến động ↔ WIP, và `dispositionLots`/`dispositionWithoutLots` của `D5`); `ProductionReceiptRepository` (các query `sumOpenQuantity*`, `findReceiptCandidates`); `ProductionReceiptLine` (storage một dòng/phiếu, `F5-A` nợ #11); `PurchaseQualityService` đọc hết (bản mẫu để nhân bản); `LotQcOriginLookupService` (`C2-2`); `WorkOrderDemandAllocationService.fulfill` (trần phân bổ, `F6`/`B64`); `WorkOrderCostAccumulator` (liên quan `D10`); giá trị `referenceType` mà duyệt phiếu ghi vào `stock_movements`; `WipTransactionService` phần `OUTPUT_RECEIPTED`.

| Nhóm | FE yêu cầu | Hiện trạng code | Loại |
|---|---|---|---|
| Trần từ good công đoạn cuối (R03, Q3) | Cap lấy good công đoạn cuối, gồm good của lượt xử lý lại | ✅ **Đã đúng từ `G8c` hôm qua** — `actualGoodQuantity` = good công đoạn cuối (`B219`) | **Trả lời** |
| Tách `G` và `C = min(P,G)` (R03, Q4) | Trả **riêng** `actualFinalGoodQuantity` và `receiptableGoodCap` | Chỉ có `actualGoodQuantity` + `availableToReceipt`; **không** kẹp theo `plannedQuantity` | **CODE** nhỏ + `D12` |
| Nháp/chờ duyệt giữ quota (R04, Q5, Q7) | `U = max(0, C−A−D)`, sửa nháp loại chính nó, rút vẫn giữ, huỷ/từ chối trả một lần | ✅ **Đã đúng** ở mức tổng — `B16`/`B76` trừ phiếu `DRAFT`+`PENDING_APPROVAL`; không có TTL nào (đúng Q7) | **Trả lời** |
| **Phân bổ theo lượt báo** (R05, R13, Q6) | `contributions[] {executionId, quantity}`, server tự cấp theo thời gian+ID, mỗi nguồn ≤ good của nguồn | 🔴 **Không có gì.** `source_wip_trace_ids` là text đọc nguyên khối, ghi lúc duyệt | **CODE** + migration, 🔴 `D3` |
| Một phiếu một WO/vật tư/kho/lô (R02, Q1) | Khoá nguồn theo WO hiện tại | ✅ **Đã đúng** — `ProductionReceiptPostRequest` phẳng từ `F5-A` (nợ #11), vật tư/UOM lấy từ WO | **Trả lời** |
| Nhận từng phần khi WO `IN_PROGRESS` (Q2) | Không bắt chờ `COMPLETED` | ✅ **Đã đúng** — `canReceipt()` = `canExecute()` + `COMPLETED` (`D11`, nợ #25) | **Trả lời** |
| `GET` một phiếu, `PATCH` nháp (§3.3) | Chi tiết + sửa nháp (lượng/lô/ngày/ghi chú), nguồn bất biến | 🔴 **Không có** cả hai | **CODE** |
| `withdraw` / `cancel` (R06, §1.3) | `PENDING → DRAFT` giữ quota; `DRAFT/PENDING → CANCELLED` trả quota | 🔴 `CANCELLED` **có trong enum, không endpoint nào dẫn tới**; `withdraw` không có | **CODE** + `D8` |
| `expectedVersion`/`expectedWorkOrderVersion` (R19, R20) | Bắt buộc trên **mọi** lệnh phiếu nhập | `@Version` có trên `BaseEntity`; `submit`/`approve` **không nhận body**; response **không trả `version`** | **CODE** + 🔴 bẫy `§0.39` |
| Duyệt nguyên tử (R07) | Phiếu + quota + `HOLD` + biến động + WO + WIP + audit + replay cùng một transaction | ✅ **Đã đúng** — `approve` là `@Transactional`, ghi đủ cả sáu (`F2`/`F5-A`) | **Trả lời** |
| Kiểm lại lúc duyệt (R08) | Kiểm lại nguồn/trần/hạn/trùng lô/version hiện tại, không tự đổi kho/lô | Có kiểm trần (`B16`) + trạng thái; **chưa** kiểm version, hạn lô, "nguồn đã đổi" | **CODE** |
| **QC từng phần** (R09, R10, Q15, Q19, Q20) | Nhiều lượt, mỗi lượt bất biến, `accepted≥0`/`rejected≥0`/`0<tổng≤pending`, lô con, `qualityState` | 🔴 `qcResult` **một giá trị**; `QualityDisposition` **không có cột lượng**, `lot_id NOT NULL`; `isQcDecided()` chặn lần hai | **CODE** + migration, 🔴 `D1` — nhưng nhân bản `G7c` |
| Endpoint QC cũ (Q29, R24, §3.7) | Không để API cũ ghi đè quyết định mới | `POST .../qc-disposition` nguyên phiếu vẫn còn và **đang** là đường duy nhất | **CODE** + 🔴 `D2` |
| Hàng không theo lô (Q16) | Vùng chờ theo từng phiếu, không lô giả | `D5` đã có đường không-lô (`qualityHoldQuantity`); **nhưng** không có bucket "đã loại" riêng — đúng giới hạn `G7c` đã ghi (`§0.59` #4) | **CODE** nhỏ + `D4` |
| Lô con + `parentLotId` (R10) | Lô con mang `parentLotId`, phần chờ ở lại lô cha | ✅ **Có sẵn** — `splitLot` + `inventory_lots.parent_lot_id` (`V78`, `B205`) | **Tái dùng** |
| `receivedOn` / `expiresOn` (R21, Q23, Q24, Q34) | Ngày nghiệp vụ ≤ PlantToday; hạn > ngày nhận | 🔴 **Không có** cả hai cột. ⚠️ `InventoryLot.expiresAt` **chưa có đường ghi qua REST** (`§0.46` 2026-08-25) — phiếu nhập là chỗ đúng để mở | **CODE** + migration, `D5` |
| Hai quyền đọc mới (R01, R18, Q11, Q22) | `PERM_PRODUCTION_RECEIPT_READ`, `PERM_PRODUCTION_QC_READ` | 🔴 **Không tồn tại** (grep `src/main` + migration = 0). Đọc phiếu hiện đòi `_MANAGE` — đúng hệ quả `F8` đã ghi và cố ý hoãn (`§0.19` #5) | **CODE** + migration, `D6` |
| Bộ lọc list (R01, Q21) | `search`/`workOrderId`/`destinationWarehouseId`/`receivedFrom`/`receivedTo`/`qualityState`/`pendingQc`/`hasQcDecision` | Chỉ có `status`; `candidates` **không có** `workOrderId`/`search` | **CODE** |
| Truy xuất nguồn (R13, Q27) | Hai tầng: phiếu → execution thật; phiếu → WO → phiếu cấp/vật tư dùng → kho/lô, `materialAttribution = WORK_ORDER_LEVEL` | Sổ đã đủ dữ liệu sau `G8c`/`G8d` (`production_execution_consumptions`, `material_issue_lines`); **không** có endpoint gom | **CODE** |
| `GET .../events` (§3.3) | Lịch sử hành động/ai/lý do/khi | Dấu vết nằm ở `audit_logs` (`AR-*`) + các cột `*At`/`*By` trên phiếu; **không** endpoint riêng | **CODE** + `D9` |
| Chặn đi vòng QC (R12) | `HOLD`/`REJECTED`/hết hạn không dùng được; lệnh kho chung không bypass | ✅ Nửa đầu **đã đúng** — `changeStatus` chặn lô `HOLD` của phiếu nhập (`C2-2`), lô `HOLD`/hết hạn đã bị loại khỏi khả dụng (`B116`/`G4`). 🔴 `POST /v1/inventory/adjust` vẫn chạy trên lô `HOLD` (`§0.59` #5, có từ trước) | **Trả lời** + nợ ghi rõ |
| Số lượng `NUMERIC(19,6)` (R17, Q25) | Từ chối số lẻ thứ 7/tràn, không làm tròn | `G4` đã áp `@Digits(13,6)` cho **inventory**; `ProductionReceiptPostRequest.quantity` chỉ có `@Positive` | **CODE** nhỏ |
| Idempotency (R20, Q13, Q14) | Phạm vi tenant+nhà máy+người+lệnh+key, so fingerprint, giữ ≥ 24h | ✅ Fingerprint **đã có** (`payloadHash` + `ensureSamePayload`); phạm vi hiện là `UNIQUE` toàn bảng, **không** theo người/nhà máy; **không** TTL (bảng, không Redis) | **Trả lời** + `D7` |
| Lô trùng (Q8, Q9) | Company+vật tư+`trim`, không phân biệt hoa thường; nháp cũng giữ chỗ mã lô | ✅ Đã **theo vật tư, không phân biệt hoa thường** (`uk_inventory_lots_item_code`); vật tư vốn thuộc công ty ⇒ khớp Q8. Nháp giữ chỗ mã lô: **không có** | **Trả lời** + `D11` |
| Nguồn cung WO cho MRP (R15, Q32, A38) | Duyệt xong thì cung tương lai về 0 | 🔴 `aggregateOpenSupply` = `plannedQuantity − completedQuantity` cho WO `RELEASED`/`IN_PROGRESS` ⇒ ví dụ của FE (kế hoạch 10, duyệt 9) cho **1**, không phải 0. `G8 D11` **đã hoãn** đúng việc này (2026-10-19) | **Trả lời** + 🔴 `D13` |
| Đơn bán (R16, Q31) | `productionQcAccepted` tách `delivered`/`fulfilled`; QC không làm đơn `FULFILLED` | ✅ Nửa đầu đã có (`G6a`: `qcAcceptedQuantity`, `deliveredQuantity` = `null`). 🔴 Nửa sau **ngược**: QC `AVAILABLE` **đang** gọi `allocationService.fulfill` và đẩy đơn sang `FULFILLED` (`F6`/`B62`) | **Trả lời** + 🔴 `D14` |
| Giá trị thành phẩm (R23, Q26, F2) | Unknown ≠ 0, QC không tạo giá trị lần hai | 🔴 Không có cột giá nào trong kho (`P3`); `WorkOrderCostAccumulator` chỉ tích chi phí **vật tư/nhân công/chung** của WO | **Không làm** nếu `D10` = (a) |
| Di trú dữ liệu cũ (R22, Q30) | Giữ ID/lịch sử, thiếu bằng chứng ⇒ chỉ đọc/unknown, không bịa nguồn | Phiếu cũ có `source_wip_trace_ids` nhưng **không** có phân bổ theo execution | **CODE** nhỏ (cờ) + `D3` |

### 16.2 Quyết định phải chốt với user trước khi code ✅ *(chốt 2026-10-20, `AskUserQuestion`: D0 = (b) tách 3 làm liền; D12 = (a) kẹp; D13 + D14 = giữ cả hai; D1–D11, D15 theo cột "Khuyến nghị BE")*

> 🔴 **Đính chính sau khi làm `G9a`:** D12 "chặn mới về số liệu" **không có thật** — `A ≤ G ≤ P` là CHECK của DB từ `V14`/`V32`, nên `C = min(P, G)` luôn bằng `G`. Breaking change dự kiến ở §16.4 hàng D12 không xảy ra (`CLAUDE.md §0.64` #1).

| # | Quyết định | Hai phương án | Khuyến nghị BE |
|---|---|---|---|
| **D0** | **Tách phase hay làm một lượt** | (a) một lượt `G9`; (b) tách **3**: `G9a` vòng đời + đọc (chi tiết/`PATCH`/rút/huỷ/`version`/ngày/bộ lọc/2 quyền đọc) → `G9b` phân bổ theo lượt báo + truy xuất nguồn + lịch sử → `G9c` QC từng phần + các projection liên quan | 🔴 **(b)**, đúng tiền lệ `D0` của `G6`/`G7`/`G8`. Nhỏ hơn `G8` (24 yêu cầu/32 câu/40 ca nhưng phần lớn là nâng cấp), tuy vậy `G9c` một mình đổi mô hình QC và đụng `stock_balances` ⇒ không gộp với `G9a` |
| **D1** | 🔴 **Mô hình QC từng phần** | (a) bảng **mới** `production_quality_decisions` nhân bản `PurchaseQualityDecision` của `G7c` (lượng đạt/loại, lô con, biến động, ai–khi, bất biến) + tái dùng `splitLot`; (b) thêm cột lượng vào `quality_dispositions` có sẵn | **(a)**. `quality_dispositions` định nghĩa là "**một dòng cho mỗi lô**, phán quyết nguyên lô" và `lot_id` là `NOT NULL` — thêm lượng vào đó là đổi nghĩa một bảng **đã có dữ liệu lịch sử**, và vẫn không chứa được phán quyết của hàng không theo lô (`D5` cố ý ghi trên phiếu). Nhân bản `G7c` còn được cả `splitLot` + `parent_lot_id` + bài học mutation của nó. Giữ `quality_dispositions` nguyên trạng, **không** xoá |
| **D2** | 🔴 **Endpoint QC nguyên phiếu cũ** | (a) giữ chạy, cài lại **bên trên** sổ mới = "một quyết định cho toàn bộ phần còn chờ"; (b) chặn hẳn khi phiếu đã dùng sổ từng phần | **(a)** — FE §3.7 nói "chặn hoặc điều phối", và (a) là điều phối: client cũ (kể cả `scripts/demo-capstone2`, `ProductionFlowE2EIT`) không vỡ, mà vẫn chỉ có **một** sổ chân lý. `qcResult`/`qcAt`/`qcBy` giữ làm **ảnh chụp tóm tắt** cho list, cộng `qualityState` mới (`NOT_STARTED`/`PARTIAL`/`ACCEPTED`/`REJECTED`/`MIXED`). 🔴 Cổng `isQcDecided()` phải đổi sang "hết phần chờ", nếu không lượt QC thứ hai bị chặn |
| **D3** | 🔴 **Phân bổ theo lượt báo (contributions)** | (a) bảng nối `production_receipt_contributions` (phiếu → execution → lượng), **server tự cấp** theo thời gian+ID; (b) không làm, giữ `source_wip_trace_ids` | **(a)**; (b) không trả lời được R05 ("mỗi nguồn ≤ good của nguồn") và R13. Theo đúng Q6: **không** nhận nguồn do client khai. 🔴 Phiếu cũ **không backfill** — cờ `outputEvidenceKnown = false` + `contributions` vắng mặt, đúng khuôn "`NULL` = không ai chụp lúc đó" (`AR-*` #4, `B77`). Giữ `source_wip_trace_ids`, **không** xoá (dữ liệu lịch sử) |
| **D4** | **Hàng không theo lô: vùng "đã loại"** | (a) giữ như `G7c` — phần đạt rời `qualityHoldQuantity`, phần loại **ở lại** vùng chờ, không bucket riêng; (b) thêm cột `rejectedQuantity` vào `stock_balances` | **(a)**, và **nói thẳng giới hạn cho FE**: màn hình tồn sẽ cộng "chờ kiểm" với "đã loại" thành một số. `G7c` đã chọn đúng thế (`D7` của `G7`, `§0.59` #4); thêm cột tồn là đổi **mọi** tổng tồn kho trong hệ thống — việc riêng, không nhét vào đây |
| **D5** | **`receivedOn` / `expiresOn`** | (a) thêm cả hai cột + validate `receivedOn ≤ PlantToday`, `expiresOn > receivedOn`, truyền hạn xuống lô; (b) chỉ `receivedOn` | **(a)**. ⚠️ Đây cũng là chỗ đóng một nợ đã ghi: `InventoryLot.expiresAt` hiện **không có đường ghi qua REST API** nên `scripts/demo-capstone2/set-lot-expiry.sh` phải chạy SQL (`§0.46`, 2026-08-25). Phiếu cũ: `received_on` backfill từ `(posted_at AT TIME ZONE plant.timezone)::date` rồi `NOT NULL` — **đúng khuôn `V77`** đã làm cho phiếu nhận mua |
| **D6** | **Hai quyền đọc mới** | (a) `PERM_PRODUCTION_RECEIPT_READ` + `PERM_PRODUCTION_QC_READ`, ADMIN + MANAGER, như `G7b`/`G7c`; (b) tái dùng quyền `_MANAGE`/`_APPROVE`/`PERM_QUALITY_DISPOSITION` hiện có | **(a)** — `V17` seed **một** quyền cho mỗi loại chứng từ với mô tả "Post *and read*", và hệ quả đã được ghi là nợ từ `F8`: người chỉ có `PERM_QUALITY_DISPOSITION` **không mở được** danh sách phiếu. Đọc vẫn phải chấp nhận `_MANAGE`/`_APPROVE` (đừng bắt cấp thêm quyền cho người đang làm được việc) — đúng khuôn `hasAnyWorkOrderAccess` của `G8b` |
| **D7** | **Phạm vi khoá idempotency** | (a) giữ `UNIQUE` toàn bảng trên `production_receipts.idempotency_key` + fingerprint như hiện tại; (b) đổi sang `(tenant, plant, principal, command, key)` như FE Q13 | **(a) giữ** cho phiếu nhập (fingerprint đã chặn đúng cái cần chặn), **nhưng** các lệnh mới (`PATCH`/rút/huỷ/QC) dùng bảng `work_order_idempotency_records` của `G8a` (`UNIQUE (key, command)`) — đã là khuôn chung của `module/workorder`. (b) là migration trên bảng có dữ liệu để đổi một bảo đảm đang hoạt động đúng. Không TTL: trả lời Q14 là "không hết hạn", **mạnh hơn** 24h FE xin |
| **D8** | **Rút và huỷ** | (a) hai endpoint mới, chỉ người tạo, lý do 5–2000 qua `ReasonRules` của `G8b`; (b) chỉ `cancel` | **(a)** — `CANCELLED` đã nằm trong enum từ `F2` mà không có đường vào; rút (`PENDING → DRAFT`) là thứ FE cần để sửa nháp đã gửi. "Chỉ người tạo" theo đúng tiền lệ `G8b` (`created_by`, ADMIN cũng 403 — quản lý dùng `reject`) |
| **D9** | **`GET .../events`** | (a) đọc từ các cột `*At`/`*By` trên phiếu + sổ quyết định QC, **không** bảng mới; (b) bảng sự kiện riêng | **(a)** — mọi hành động đã có cột dấu thời gian + người, và `audit_logs` (`AR-*`) là sổ bất biến thật. Bảng thứ hai là nguồn thứ hai sẽ trôi. ⚠️ Lịch sử trả về sẽ **không** có `PATCH` nháp trừ khi `D5` thêm cột — chấp nhận, hoặc đọc `audit_logs` theo `entityId` |
| **D10** | 🔴 **Giá trị thành phẩm** | (a) **hoãn** — `valuationStatus = PENDING_COSTING`, `receiptValue`/`currency` **vắng mặt**; (b) dựng lớp giá | **(a) hoãn.** Lần thứ **tư**: `P3` loại, `G7 D8` hoãn, `G8 D10` hoãn. FE tự cho unknown (Q26) và §F2 nói rõ "full costing không thuộc task này". Trả `valuationStatus` là đủ để FE không hiển thị `0` |
| **D11** | **Nháp giữ chỗ mã lô (Q9)** | (a) **không làm** — chỉ kiểm lúc duyệt dưới khoá; (b) kiểm cả mã lô của nháp | **(a)** — (b) là một bảo đảm **không** thực thi được: hai nháp có thể cùng khai một mã rồi cả hai qua, nên nó chỉ là báo sớm cho UI (`§11.5`). `uk_inventory_lots_item_code` dưới khoá lúc duyệt là chỗ bảo đảm thật. Nói rõ cho FE: báo sớm là tiện lợi, không phải hợp đồng |
| **D12** | **Kẹp theo kế hoạch (`C = min(P,G)`)** | (a) trả **cả hai** số (`actualFinalGoodQuantity` = `G`, `receiptableGoodCap` = `min(P,G)`) và **kẹp** trần theo `C`; (b) giữ trần theo `G` như hiện tại, chỉ thêm field hiển thị | **(a)**, đúng Q4 — nhưng 🔴 **đây là chặn mới**: WO báo good **vượt** kế hoạch (hợp lệ hôm nay, `B54` chỉ chặn ở chỗ khác) nay chỉ nhận được tới `plannedQuantity`. Phải nêu trong "Breaking changes" và trong bản trả lời FE; FE cũng xin "G > P phải cảnh báo, không che bằng `max(0,…)`" |
| **D13** | 🔴 **Nguồn cung WO cho MRP (A38)** | (a) mở lại `G8 D11` — cung = sản lượng cuối thực − phiếu đã duyệt; (b) giữ theo kế hoạch, **nói rõ số FE mong là khác** | **(b) giữ.** `G8 D11` **vừa hoãn** đúng việc này một ngày trước, và nó **đổi số MRP** (lần thứ ba trong tuần sau `G6b`). Hệ quả phải báo FE thẳng: ví dụ A38 của họ (kế hoạch 10, duyệt 9) cho cung tương lai **1**, không phải **0**; con số về 0 khi WO sang `COMPLETED` (WO đó rời khỏi `OPEN_SUPPLY_STATUSES`). Muốn đúng như FE thì phải làm `D11` của `G8` ⇒ phase riêng, đo A/B trên `D26-OMNIPLANT` |
| **D14** | 🔴 **QC có còn làm đơn bán `FULFILLED`?** | (a) giữ nguyên `F6` — QC `AVAILABLE` tăng `fulfilledQuantity` và roll-up đơn; (b) tách: QC chỉ tăng `qcAcceptedQuantity`, `fulfilled` chờ GĐ10 | **(a) giữ trong `G9`.** (b) là đổi `B62`/`B64`/`B66` — mắt xích cuối của track `F*` — trong lúc **chưa có** chứng từ giao hàng (GĐ10 ngoài phạm vi), nên đơn sẽ **không bao giờ** `FULFILLED` ⇒ màn hình đơn bán đứng yên. Trả lời FE: `qcAcceptedQuantity` (`G6a`) **là** số họ cần, `deliveredQuantity` đã luôn `null`; đổi nghĩa `fulfilled` thuộc GĐ10 cùng với chứng từ giao |
| **D15** | **QC từng phần cho hàng theo sê-ri** | (a) **ngoài phạm vi** — sê-ri giữ đường `P5` hiện tại; (b) làm luôn | **(a)** — FE tự xếp sê-ri ngoài phạm vi (§1.1), và `B98` đã chốt output theo sê-ri **không** có `HOLD` chờ QC. `ProductionReceiptPostRequest.serialNumber` hiện `@Schema(hidden = true)` ⇒ giữ nguyên |

> **Sáu thứ KHÔNG hỏi vì code đã trả lời:** Q1 (một phiếu một WO/vật tư/kho — `F5-A` đã phẳng hoá), Q2 (nhận khi `IN_PROGRESS` — `D11`/nợ #25), Q3 (trần từ good công đoạn cuối — `G8c`/`B219`), Q7 (quota nháp không tự hết hạn — không có TTL nào trong repo), Q17/Q18 (loại không mở lại trần, không tự ghi giảm — `qcDisposition` của `D5` đã thế: `REJECTED` giữ tồn, `AVAILABLE` không sinh biến động), Q19 (quyết định bất biến — repo chưa bao giờ có `PATCH`/`DELETE` cho chứng từ đã ghi).
>
> 🔴 **Ba lệch cần nêu trong bản trả lời FE, không phải quyết định:** (1) ID ngoài phạm vi trả **403**, không 404 (`§0.50` #9 — hành vi toàn repo); (2) `POST /v1/inventory/adjust` vẫn chạy trên lô `HOLD` nên "generic mutation không bypass production QC" chỉ đúng với **đổi trạng thái lô**, không đúng với điều chỉnh (`§0.59` #5, có từ trước `G7`); (3) `postedAt` của phiếu hiện `NOT NULL` và được gán `now()` **ngay từ lúc tạo nháp** — FE giả định nó là "thời điểm đã ghi sổ" (`optional trước post`); đổi nó là migration trên cột `NOT NULL` có dữ liệu, cần chốt riêng nếu FE thật sự cần.

### 16.3 Gói công việc *(thứ tự thực thi; mỗi gói một commit theo `dev-workflow.md §6.6`)*

**`G9-0` — đọc nốt + baseline *(trước khi viết dòng code nào)***
Đọc hết phần "chưa rà" §16.1, ưu tiên `ProductionReceiptService.post`/`approve`/`qcDisposition` **từng dòng** (`dispositionLots`/`dispositionWithoutLots` của `D5` là chỗ `G9c` sẽ viết lại) và `PurchaseQualityService` (bản mẫu). Đo baseline thật trên `git worktree` **sạch** (bẫy `§0.52` #10a). Chạy live luồng phiếu nhập + QC hiện có để có **ảnh A/B trước khi đổi** — đúng cách `G6b` đã làm.

**`G9a` — vòng đời phiếu nhập + đọc *(migration `V83`)***
`receivedOn` (backfill theo khuôn `V77` rồi `NOT NULL`) + `expiresOn` + `cancelled_at`/`cancel_reason`/`withdraw_reason`; 2 quyền đọc mới (`D6`); `version` + `qualityState` + `receivedOn`/`expiresOn` + bộ đếm `G`/`C`/`A`/`D`/`W`/`U` + `allowedActions` trên response; `GET` một phiếu, `PATCH` nháp, `withdraw`, `cancel`; `expectedVersion`/`expectedWorkOrderVersion` trên **mọi** lệnh (khuôn `G8a`: replay → version → luật → `touch()` → `flushTouched`); bộ lọc list + `candidates`; `@Digits(19,6)`; `GET .../events` (`D9`). 🔴 **Thêm `version` vào response ⇒ mọi đường ghi phải flush trước khi map** — bẫy `§0.39`, repo đã dính **năm** lần (`§0.58` #4, `§0.60` #2).

### 16.3a Kết quả `G9a` *(2026-10-21)*

✅ BE HOÀN THÀNH. Migration `V83`; 5 endpoint mới (chi tiết, lịch sử, `PATCH`, rút, huỷ), 5 bộ lọc list, `workOrderId`/`search` cho ứng viên; submit/approve/reject nhận body hai version; tạo bắt buộc `expectedWorkOrderVersion` + `receivedOn`, kho = kho đầu ra của WO; mọi lệnh bump version WO +1; hạn mức `G/C/A/D/W/U` + `allowedActions` trên mọi response; 2 quyền đọc. `mvn -o clean verify` 1968 + 274, mutation 15/15, smoke 45/45 (lượt `004655`). Smoke lộ hai lỗi đã sửa: version phiếu không tăng khi sửa chỉ lượng (`B112`), bẫy µs. Bản ghi: `CLAUDE.md §0.64`, bất biến `B226`–`B230`.

**`G9b` — phân bổ theo lượt báo + truy xuất nguồn**
Bảng `production_receipt_contributions` + sinh phía server theo thời gian+ID (`D3`); trần mỗi nguồn ≤ good của nguồn (đọc sổ `G8c`); `outputEvidenceKnown`/`outputUnknownReason` cho phiếu cũ; `GET .../source` hai tầng (execution + WO-level `materialAttribution`, đọc `production_execution_consumptions` + `material_issue_lines` của `G8c`/`G8d`). Kiểm lại nguồn lúc gửi/duyệt ⇒ `PRODUCTION_RECEIPT_SOURCE_CHANGED`.

### 16.3b Kết quả `G9b` *(2026-10-23)*

✅ BE HOÀN THÀNH. Migration `V84` (bảng phân bổ, không backfill); `GET …/source`; `outputEvidenceKnown`/`outputUnknownReason`/`contributions[]` trên mọi response phiếu nhập. Server phân bổ lúc tạo + sửa nháp, công đoạn cuối, lượt cũ nhất trước; phiếu cũ tính trừ vào lượt cũ nhất. 🔴 **Lệch kế hoạch:** không kiểm lại lúc gửi/duyệt và **không** mã `PRODUCTION_RECEIPT_SOURCE_CHANGED` — lượt báo bất biến, phân bổ tuần tự hoá qua version WO, phần phiếu cũ chỉ giảm (B232). `mvn -o clean verify` 1982 + 279, mutation 14/14, smoke 16/16 (lượt `084754`). Bản ghi: `CLAUDE.md §0.65`, bất biến `B231`–`B233`.

**`G9c` — QC từng phần *(migration riêng)***
Bảng `production_quality_decisions` (nhân bản `G7c`, bất biến) + tái dùng `InventoryMovementService.splitLot`; `qualityState` + tổng đạt/loại/chờ; cài lại `qc-disposition` cũ bên trên sổ mới (`D2`) và đổi cổng `isQcDecided()` → "hết phần chờ"; đường không theo lô theo `D4`; `GET .../qc-decisions` + bộ lọc `pendingQc`/`hasQcDecision`; projection WO/dở dang/tồn kho/lô mang `receiptId`/`qcDecisionId`; `valuationStatus = PENDING_COSTING` (`D10`).

### 16.3c Kết quả `G9c` *(2026-10-23)*

✅ BE HOÀN THÀNH. Migration `V85` (sổ quyết định bất biến + tổng trên dòng, backfill từ `qc_result` cũ); `POST`/`GET …/qc-decisions`; `qualityState` + ba tổng + `valuationStatus`; bộ lọc `qualityState`/`pendingQc`/`hasQcDecision`; `qc-disposition` cũ cài lại trên cùng sổ (D2); cổng đổi trạng thái lô tay theo "còn phần chờ". `mvn -o clean verify` 2009 + 283, mutation 17/17, smoke 24/24 (lượt `094029`). Bản ghi: `CLAUDE.md §0.66`, bất biến `B234`–`B237`. **Chưa làm:** projection `receiptId`/`qcDecisionId` trên biến động kho/WIP (lô con đã mang nguồn `PRODUCTION_QC_DECISION` + id quyết định), vùng "đã loại" riêng (D4), lớp giá trị (D10).

**`G9-9` — nghiệm thu**
`mvn -o clean verify` + nghiệm thu mutation (nhắm: trần mỗi nguồn, cổng "hết phần chờ", `splitLot` cho phần chờ ở lại lô cha, `receivedOn ≤ PlantToday`, quyền đọc mới, `version` bắt buộc) + smoke HTTP thật + `BE_RESPONSE_GD_09_<ngày>.md` + cập nhật `CLAUDE.md §0.1`/§0.2/bản ghi + `docs/roles-and-permissions.md` (`C10` **có** kích hoạt: 2 quyền mới) + `docs/api-guide-for-frontend.md`.

> 🔴 **Migration kế tiếp là `V83`** (`V82` của `G8d` là mới nhất) ⇒ `MANUFACTURING_PHASE_PLAN_P7_P11.md` phải đánh số lại từ **`V84`** trở lên nếu `G9` dùng một số, hoặc từ số sau cùng nếu `D0` = tách và mỗi gói một migration.
>
> ⚠️ **Hai thứ sẽ phải chạy lại sau `G9`:** `scripts/demo-capstone2` (phiếu nhập của nó đi đường QC nguyên phiếu, và sẽ thiếu `receivedOn`), `ProductionFlowE2EIT` (bước QC). `scripts/demo` (`VIETBIKE`) vốn đã lệch bố cục URL (`§0.46` #1) — không sửa.

### 16.4 Breaking changes dự kiến *(ghi đầy đủ vào `BE_RESPONSE_GD_09_*` khi làm)*

| Thay đổi | Breaking? |
|---|---|
| Endpoint/field/quyền mới (chi tiết, `PATCH`, rút, huỷ, nguồn, lịch sử, sổ QC) | **Không** (additive) |
| `expectedVersion`/`expectedWorkOrderVersion` bắt buộc | **Có** — lệnh trước đây không gửi body nay 400 (đúng như `G8a` đã làm với lệnh WO) |
| `receivedOn` bắt buộc khi tạo | **Có** — client cũ không gửi nay 400 |
| Trần kẹp theo `plannedQuantity` (`D12`) | **Có — về số liệu**: WO có good vượt kế hoạch nay chỉ nhận tới kế hoạch |
| `qc-disposition` cũ ghi vào sổ từng phần (`D2`) | **Có, hẹp** — hành vi giữ nguyên với phiếu chưa QC; phiếu **đã** QC một phần nay nhận được lượt nữa thay vì bị chặn |
| Hai quyền đọc mới (`D6`) | **Không** — đọc vẫn chấp nhận `_MANAGE`/`_APPROVE` |
| Số lẻ thứ 7 ở `quantity` phiếu nhập | **Có, hẹp** — 400 thay vì làm tròn im lặng |
| Hạn lô truyền từ phiếu xuống lô (`D5`) | **Có — về hành vi**: lô mới nay có thể hết hạn, nên bị loại khỏi khả dụng (`G4`/`B154`) |
| Nguồn cung WO cho MRP | **Không đổi** nếu `D13` = (b) — nhưng **lệch kỳ vọng A38 của FE**, phải nói rõ |
| Đơn bán `FULFILLED` do QC | **Không đổi** nếu `D14` = (a) |
| Giá trị thành phẩm | **Không làm** nếu `D10` = (a) |

### 16.5 Ngoài phạm vi *(theo FE §1.1 + quyết định BE)*

Giao hàng/hoá đơn/thanh toán (GĐ10); chi phí/định giá/đóng kỳ đầy đủ (GĐ11, và `D10` hoãn lần thứ tư); sửa/đảo/xoá chứng từ đã ghi và sửa lại quyết định QC (Q19/Q36 — repo chưa bao giờ có `PATCH`/`DELETE` cho chúng); khách trả hàng thành phẩm; tự nhập bù / tự tạo lệnh làm lại từ QC; phiếu nhiều dòng / nhiều WO; **sê-ri** (`D15`, `B98`); MES; đổi kho/vật tư/lượng của WO; vùng "đã loại" riêng trong `stock_balances` (`D4`); đổi 403 → 404 cho ID ngoài phạm vi (hành vi toàn repo); `capabilities` object; và **không** tự chốt các câu còn mở của GĐ6 (Q10/Q16/Q26/Q27/Q28) và GĐ8 (Q7 kho đầu ra của nháp MAKE, Q37 giá trị dư khi hoàn) — FE §5 nói rõ chúng độc lập.

---

## 17. `G10` — FE handoff Giai đoạn 10: Giao hàng, hoá đơn và thanh toán 🔄 ĐANG CHẠY — `G10a` ✅ `G10b` ✅ 2026-10-25 · `G10c` ✅ 2026-10-26

Nguồn: `FE_HANDOFF_GD_10_2026-10-15.md` (v1, P1, 786 dòng). FE/mock đã xong: danh sách + nháp + ghi sổ **phiếu giao hàng** (một đơn bán, nhiều dòng/kho/lô), **hoá đơn bán hàng** (một đơn bán, gộp nhiều phiếu giao), **thanh toán khách hàng** (một khách, phân bổ nhiều hoá đơn), **giá bán trên đơn bán**, và các màn hình đọc liên quan (đơn bán, giữ hàng, tồn/lô/biến động, hoạch định, liên kết ngược từ phiếu nhập/lô/WO). BE phải (1) trả lời Q1–Q40 §5, (2) đối chiếu R01–R30 với API có sẵn, (3) chỉ code phần thiếu, (4) trả `BE_RESPONSE_GD_10_<ngày>.md` theo `BETaskDeliveryRules.md`. FE chưa gọi live lần nào (§2.1 ghi rõ cả năm mảnh bằng chứng đều trống).

> 🔴 **Đây là handoff đầu tiên mà gần như TOÀN BỘ là dựng mới, và là lần đầu repo chạm tiền.** Trong `module/sales` chỉ có đúng một dòng javadoc ở `SalesOrderLineMetricsEvidence` nhắc "`delivered`: chưa có chứng từ giao hàng (GĐ10)"; các kết quả `grep -ril "delivery\|invoice\|customerpayment" src/main` còn lại là từ ngẫu nhiên (ngày giao của PO/phiếu nhận mua, `redelivery` của audit outbox). Không entity, không bảng, không quyền, không endpoint. Ba aggregate chứng từ mới (giao hàng / hoá đơn / thanh toán), mỗi cái có vòng đời `DRAFT → POSTED | CANCELLED`, claim hạn mức, lịch sử, idempotency; **10 quyền mới**; và một **kiểu dữ liệu chưa từng tồn tại**: tiền (VND nguyên).
>
> 🔴 **Ba thứ làm phase này khó hơn số endpoint của nó:**
> 1. **Đơn bán chưa có giá.** `SalesOrderLine` có đúng: id, đơn, vật tư, `lineNo`, `orderedQuantity`, `fulfilledQuantity`, `dueDate`. Không `unitPrice`, không tiền tệ. `SalesOrder` có `customerId` nhưng **nullable** (đơn cũ chỉ có tên khách — `G6a`/`B171`). Hoá đơn và thanh toán cần cả hai.
> 2. **Hàng trong kho không có "chủ" theo dòng đơn bán — ngoại trừ phần giữ chỗ của `G6a`.** Lô `AVAILABLE` là hàng tự do; WO nhập kho rồi QC chỉ tăng `fulfilledQuantity` của dòng đơn **theo số lượng** (`F6`), không gắn lô nào vào dòng nào. FE (Q12/Q13, A12/A13) giả định một sổ "quyền hưởng theo nguồn" (entitlement) mà repo **không có** — xem `D2`.
> 3. **Giao hàng là lần đầu một chứng từ trừ tồn kho theo cả hai nguồn cùng lúc.** `issueInternal(consumeReserved=true)` chỉ trừ từ phần đã giữ; `consumeReserved=false` chỉ trừ từ phần tự do. Một dòng giao "dùng phần giữ của chính đơn này trước, thiếu thì lấy hàng tự do" cần **hai** lần ghi `ISSUE` với khoá con riêng (`…:R` / `…:F`) — hợp lệ vì `uk_stock_movements` là `(key, movement_type)` nhưng **cùng** `movement_type = ISSUE` thì phải khác key (`B69`/`B70`).
>
> ✅ **Tin tốt:** năm thứ cỗ máy đã có sẵn và nên tái dùng, không dựng lại: (a) khoá bi quan theo thứ tự ổn định + `touch()`/`flushTouched` của `G8` cho mọi lệnh bump version đúng +1; (b) khuôn "khoá native chỉ id → nạp collection → `refresh`" của `G7b` (`B194`) chống bẫy open-session-in-view; (c) `WorkOrderIdempotency`/`purchasing_idempotency_records` — bảng `UNIQUE (key, command)` không TTL; (d) `allowedActions` + `ReasonRules` (5–2000) của `G9a`/`G8b`; (e) giữ chỗ của đơn bán (`G6a`: `MaterialReservation` có `salesOrderLineId`, `consume()`, `cancelActiveForSalesOrder`) — chính là "phần giữ của chính đơn" mà FE gọi là `ownReserved`.

### 17.1 Đối chiếu yêu cầu FE ↔ code thật *(rà 2026-10-23 bằng đọc source, chưa gọi live)*

Phạm vi đã rà: toàn bộ danh sách file `module/sales` (46 file); cột của `SalesOrder`/`SalesOrderLine`/`SalesOrderStatus`; `SalesOrderLineMetricsEvidence`; `SalesOrderService.updateDraft`/`update`/`replaceLines`/`cancel` (đọc thân hàm); `MaterialReservation` (cột `salesOrderId`/`salesOrderLineId`/`consumedQuantity`, `consume`/`release`/`cancel`); `InventoryMovementService.issue`/`issueManual`/`issueReserved`/`issueInternal` (đọc thân hàm); `StockBalance` (API công khai); grep quyền `PERM_SALES*`/`PERM_CUSTOMER*`/`PERM_DELIVERY*`/`PERM_INVOICE*` trong `module/`; grep `delivery|invoice|customerpayment` trong `src/main` và `db/migration`; số migration mới nhất (`V85`). **Chưa rà:** `SalesOrderService.confirm` (có ép `customerId` không); `SalesOrderPlanningSourceService` + công thức `uncovered` của `G6a`/`G6b` (có đếm đôi `qcAccepted` với `delivered` không — `D8`); `SalesOrderReservationService.reserve`/`candidates` (FEFO, cách tìm "phần giữ của chính đơn" theo vị trí kho); `SalesOrderDetailAssembler` (nơi số tiến độ được ráp); `SalesPermissionGuard`; `StockTransferService` (khuôn khoá bi quan `B151` để sao chép); `StockBalanceRepository` các query khoá; `InventoryMovementService.findBalance`/`ensureSufficient*` (có khoá hay chỉ `@Version`); cách `Customer` trạng thái `INACTIVE` được kiểm ở nơi khác; mọi chỗ khác đọc `SalesOrder.fulfilledQuantity` (rà `§11.3`).

| Nhóm | FE yêu cầu | Hiện trạng code | Loại |
|---|---|---|---|
| **Giá bán trên đơn** (R02, §3.4) | `unitPrice` VND tuỳ chọn khi `DRAFT`; bổ sung giá còn thiếu khi đã xác nhận; giá đã biết (kể cả 0) khoá; mọi lần đặt giá có lịch sử | 🔴 **Không có cột nào.** `SalesOrderLine` không có giá/tiền tệ | **CODE** + migration |
| Khách hàng ổn định (R01, Q4) | `customerId` master trên mọi đơn mới; đơn cũ không tự suy | `sales_orders.customer_id` **nullable** (`G6a`); đơn cũ chỉ có `customerName` | **CODE** + 🔴 `D4` |
| Mã dòng đơn ổn định (§1.2) | `salesOrderLineId` không đổi qua `PATCH`; hoặc chặn sửa khi có chứng từ phía sau | 🔴 `updateDraft`/`replaceLines` đều `lines.clear()` rồi dựng lại ⇒ **id đổi mỗi lần lưu**. **Không gây hại ở GĐ10**: `PATCH` chỉ cho `DRAFT` (`B87`), mà giao hàng đòi đơn đã xác nhận — id đóng băng từ lúc xác nhận | **Trả lời** (kèm test canh ranh giới) |
| Baseline đã giao (R01, Q5) | Đơn mới `delivered = 0` đã biết; đơn cũ `unknown`, không suy từ `fulfilled` | `SalesOrderLineResponse.deliveredQuantity` **luôn `null`** + evidence `UNKNOWN` (`G6a`, đúng dành chỗ cho GĐ10). Chưa có cờ "baseline đã biết" | **CODE** + migration, 🔴 `D3` |
| **Phiếu giao hàng** (R04–R09, §3.4) | 4 `GET`, `POST`, `PATCH`, `post`, `cancel`; nhiều dòng/kho/lô; nháp chỉ giữ hạn mức; ghi sổ nguyên tử | 🔴 **Không có gì** | **CODE**, phần lớn nhất |
| Hạn mức giao (R04–R06) | `ordered − D − Cd`; sửa nháp loại hạn mức của chính nó; huỷ trả một lần | Chưa có `D` (đã giao) hay `Cd` (nháp khác). Mẫu đã có: `B16`/`B76` của phiếu nhập (`ReceiptQuotaTest`) | **CODE** (khuôn có sẵn) |
| **Nguồn hàng giao + quyền hưởng** (R08, Q12–Q14, A09, A12, A13) | `free`/`ownReserved`/`entitled` theo vị trí; `sourceKey` do BE phát hành; không lấy hàng của đơn khác | 🔴 Có `free` (`StockBalance.availableQuantity`) và `ownReserved` (reservation đơn bán). **Không có `entitled`**: không bảng nào nối lô/vị trí với một dòng đơn bán ngoài reservation | **CODE** + 🔴 `D2` |
| Trừ tồn khi giao (R09, §3.6) | Một transaction: phiếu + dòng + `ISSUE` + trừ phần giữ của chính đơn + `delivered` + lịch sử + replay | `issue`/`issueManual` trừ từ phần **tự do**; `issueReserved` trừ từ phần **đã giữ**; **không** có đường trộn. `issueManual` đã từ chối lô quá hạn (`G4`); `issue` thì không | **CODE** (hai lần `ISSUE` + `consume()` reservation; thêm biến thể từ chối lô hết hạn cho cả hai nhánh) |
| Khoá tranh chấp (R22, A36) | Nhiều người tranh quota/tồn ⇒ tuần tự hoá, không giao quá | `StockTransferService` (`B151`) đã có khuôn khoá bi quan `balanceId` theo thứ tự; `issueInternal` hiện chỉ dựa `@Version` (kẻ thua nhận `409 CONCURRENT_MODIFICATION`) | **CODE** (sao chép `B151`) |
| **Hoá đơn bán hàng** (R11–R15) | `preview`/`POST`/`PATCH`/`post`/`cancel`; gộp nhiều phiếu giao POSTED; giá khoá từ dòng đơn; làm tròn HALF_UP cộng dồn theo dòng đơn | 🔴 **Không có gì.** Không có kiểu tiền, không công thức làm tròn tích luỹ | **CODE** |
| Cơ sở lập hoá đơn cộng dồn (R14, Q22, A21, A22) | `billedQuantityBefore`/`billedAmountBefore` theo dòng đơn; nền đổi ⇒ `PRICE_STALE` | Chưa có. Phải **dẫn xuất từ sổ** hoá đơn POSTED (không cột đếm — khuôn `B217`) và khoá dòng đơn khi ghi sổ | **CODE** |
| **Thanh toán khách hàng** (R16–R18) | `POST`/`PATCH`/`post`/`cancel`; một khách/nhà máy/VND; phân bổ nhiều hoá đơn; tổng phân bổ = số tiền | 🔴 **Không có gì** | **CODE** |
| Số dư hoá đơn (R17, §3.5) | `paid = Σ phân bổ POSTED`; `outstanding = amount − paid`; trần `outstanding − Cp` | Chưa có; dẫn xuất từ sổ phân bổ | **CODE** |
| Vòng đời + bất biến (R19, Q33) | POSTED/CANCELLED bất biến, huỷ có lý do, không `DELETE` | Repo chưa bao giờ có `PATCH`/`DELETE` cho chứng từ đã ghi — khớp | **Trả lời** + test |
| Quyền (R20, Q26–Q28) | `READ`/`MANAGE`/`POST` mỗi loại; đọc = bất kỳ cái nào; chỉ người tạo sửa/huỷ; người có `POST` ghi sổ phiếu của chính mình | Tiền lệ `PERM_GOODS_RECEIPT_POST` (`V21`) cho quyền `POST` tách rời `MANAGE`; khuôn đọc-bằng-bất-kỳ-quyền `hasAnyWorkOrderAccess` (`G8b`); khuôn người tạo (`G8b`/`G9a`) | **CODE** + migration; 🔴 `C10` kích hoạt: **10 quyền mới** |
| **Chỉ hiện trường tiền theo quyền** (R28, §1.3, Q29–Q30) | Delivery-only không thấy giá; payment-only không thấy giá dòng đơn; field **vắng mặt** (không `null`) khi không quyền | 🔴 `spring.jackson.default-property-inclusion: always` (`§0.42`) ⇒ `null` luôn được in. **Chưa có** mẫu "cùng endpoint, hình dạng khác theo người gọi" ở đâu trong repo | **CODE** + 🔴 `D6` |
| Tiền trên wire (§3.2) | "decimal string", số nguyên VND `< 10^18` | Cả repo đưa `BigDecimal` ra **số JSON** (`§0.50` #8, đã trả lời FE từ `G2`). `10^18` **không** biểu diễn được trong số JSON an toàn (`2^53 ≈ 9×10^15`) | **Trả lời** + 🔴 `D5` |
| Idempotency (R21, Q23–Q25) | Phạm vi nhà máy+người+lệnh+key, so fingerprint, replay trước version, kiểm quyền hiện tại trước replay | Khuôn `G8a`/`G7a` (`UNIQUE (key, command)`, không TTL, replay **trước** version). Phạm vi "theo người" chưa có | **CODE** nhỏ + 🔴 `D9` |
| Hạn mức hoá đơn/thanh toán đồng thời (R12, R17, A24, A31) | Hai nháp tranh cùng hạn mức ⇒ một thắng | Cần khoá dòng giao / hoá đơn theo thứ tự ổn định lúc lưu nháp **và** ghi sổ | **CODE** (khuôn `B194`) |
| Ngày (R27, Q19, Q20) | `date ≤ PlantToday`; giao ≥ ngày đơn; hoá đơn ≥ mọi phiếu giao nguồn; hạn ≥ ngày hoá đơn; thanh toán ≥ mọi hoá đơn được phân bổ | Khuôn `G9a` (`plantToday`, múi giờ nhà máy thiếu ⇒ `422`) | **CODE** (khuôn có sẵn) |
| Tiêu thụ trong hoạch định (R25, Q32, A44) | Nhu cầu hiện tại trừ phần đã giao; coverage giảm theo allocation WO nguồn; run đã lưu bất biến | 🔴 Công thức `uncovered` (`G6a`) là `ordered − qcAccepted − held − workOrderCovered` — **không có `delivered`**. Sau khi hàng rời kho, tồn giảm nhưng nhu cầu của đơn không giảm | **CODE** + 🔴 `D8` (đổi số MRP) |
| Liên kết ngược (§1.1 mục 18, `GET /deliveries/related`) | Từ phiếu nhập/lô/WO tới các phiếu giao POSTED liên quan | Cần lần ngược `stock_movements` (`referenceType/Id` là text) hoặc dòng phiếu giao → vị trí kho → lô | **CODE** |
| SO không huỷ khi còn chứng từ phía sau (R26, Q37, A45) | Có phiếu giao/hoá đơn DRAFT hoặc POSTED chưa huỷ ⇒ chặn huỷ đơn | `cancel` hiện chỉ cho `DRAFT`/`CONFIRMED`; `IN_PRODUCTION` đã bị chặn (`G6` Q24); chưa biết chứng từ GĐ10 | **CODE** nhỏ |
| `SalesOrderStatus` (Q40, R24) | Đủ hàng/đủ tiền **không** tự đóng đơn | Enum **không có `CLOSED`**; `FULFILLED` = QC đạt đủ (`F6`, `G9 D14` đã chốt giữ). Không cần đổi gì | **Trả lời** |
| Giá vốn thành phẩm (R10, Q31, A46) | Giao được kể cả khi giá vốn chưa biết; `null`, không lấy giá bán | Kho **không có cột giá nào** (`P3` loại; `G7 D8`/`G8 D10`/`G9 D10` hoãn) ⇒ luôn "chưa biết" | **Không làm** nếu `D7` = hoãn |
| Di trú dữ liệu cũ (R29, A43) | Giữ id/lịch sử; không bịa 0 | Chưa có sổ giao hàng nào để di trú; chỉ cờ baseline | **CODE** nhỏ (cờ) + `D3` |
| Phân trang + lọc (R03, Q34, A48) | `page 0/size 20/max 100`, `totalElements` đúng bộ lọc **trước** phân trang | Khuôn `PageableFactory` sẵn có | **CODE** (khuôn có sẵn) |

### 17.2 Quyết định phải chốt với user trước khi code ✅ *(chốt 2026-10-25, `AskUserQuestion`: D0 = (b) tách 4 làm liền; **D8 = trừ `max(qcAccepted, delivered)`** — đo ở `G10-0`: giữ nguyên tạo nhu cầu ảo khi giao từ phần giữ / hàng tự do, trừ thẳng `delivered` đếm đôi với `qcAccepted`; D1–D7, D9 theo cột "Khuyến nghị BE")*

| # | Quyết định | Hai phương án | Khuyến nghị BE |
|---|---|---|---|
| **D0** | **Tách phase hay làm một lượt** | (a) một lượt `G10`; (b) tách **4**: `G10a` nền đơn bán (giá + khách bắt buộc + cờ baseline + chặn huỷ/sửa + 10 quyền) → `G10b` giao hàng (nguồn, hạn mức, trừ tồn, hạ nhu cầu MRP) → `G10c` hoá đơn → `G10d` thanh toán + các màn hình đọc liên quan (tiến độ đơn, liên kết ngược, nguồn gốc kho) | 🔴 **(b)**, đúng tiền lệ `G6`–`G9`. `G10b` một mình chạm tồn kho thật, khoá bi quan và (nếu `D8` = làm) số MRP — tách riêng để bị mutation và smoke riêng. `G10c`/`G10d` **không đụng tồn kho** nên rủi ro khác hẳn |
| **D1** | **Đặt code ở đâu** | (a) hai package mới: `module/delivery` (có tồn kho) + `module/billing` (hoá đơn + thanh toán, thuần tiền); (b) nhét hết vào `module/sales` | **(a)**. `module/sales` đã 46 file; ba aggregate mới sẽ gấp đôi nó và trộn hai loại rủi ro (tồn kho / tiền). Phụ thuộc vòng `sales ↔ delivery`/`billing` (đơn bán đọc tiến độ, phiếu giao đọc đơn) giải bằng lookup service hai chiều, **không** chạm repository của nhau (`C7`) — đúng tiền lệ `sales ↔ workorder` (`§0.54` #7) |
| **D2** | 🔴 **"Quyền hưởng theo nguồn" (entitlement)** | (a) **không dựng sổ**: nguồn giao = hàng tự do + phần giữ của **chính đơn**; WO nhập kho/QC **không** là earmark vật lý (nhất quán `G6`/`G9 D14`); (b) dựng sổ gắn lô/vị trí với dòng đơn từ lúc QC | **(a)**. Repo **không có** cột nào nói "lô này thuộc đơn nào"; F6 chỉ cộng `fulfilledQuantity` theo số lượng. (b) là một ledger mới chạm `StockBalance`/QC/MRP — quy mô một phase riêng, và **chính là** câu hỏi Q10/Q16/Q26/Q27/Q28 của **GĐ6 còn mở**. Hệ quả phải nói thẳng với FE: ca **A12** ("SO2 lấy stock của SO1") **không thể xảy ra** theo nghĩa FE hình dung (không có earmark để vi phạm), thứ chặn duy nhất là phần giữ của đơn khác; ca **A13** (NON_TRACKED thiếu attribution) tự nhiên được thoả vì reservation là nguồn attribution **duy nhất** và đã có `salesOrderLineId` |
| **D3** | 🔴 **Baseline "đã giao" của đơn cũ** | (a) mọi đơn xác nhận **trước migration** có `delivery_baseline_known = FALSE` ⇒ không giao/hoá đơn được (`SOURCE_UNKNOWN`); đơn tạo sau đó `TRUE`; (b) coi tất cả là `0` đã biết (chưa từng có chứng từ giao nào) | **(a)**, đúng nguyên tắc "`NULL` ≠ 0, không backfill" (`B77`, `§0.21` #2) và Q5 của FE. ⚠️ Hệ quả thật: **dữ liệu demo hiện có** (`D26-`/`VIETBIKE`) không giao được cho tới khi dựng lại — seed sinh đơn mới là `TRUE`. (b) đúng về mặt sổ sách trong hệ thống nhưng bịa sự im lặng về hàng đã rời kho ngoài hệ thống. Không có endpoint "đối soát baseline" trong `G10` — nếu cần thì phase riêng |
| **D4** | **Khách hàng bắt buộc** | (a) xác nhận đơn **và** mọi chứng từ GĐ10 đòi `customerId`; đơn cũ chỉ có tên khách ⇒ `SOURCE_UNKNOWN` cho tới khi gán; (b) vẫn cho đơn chỉ có tên khách | **(a)** — hoá đơn và thanh toán gắn **khách master** (Q16, R16); tên khách là snapshot hiển thị, không khoá được. Cần thêm: gán `customerId` cho đơn `DRAFT` đã có (`G6a` làm được qua `PATCH …/draft`). Đơn đã xác nhận mà thiếu `customerId` ⇒ không có đường sửa trong `G10` ⇒ nằm chung nhóm `D3` |
| **D5** | 🔴 **Tiền trên wire** | (a) **số nguyên JSON** như toàn repo, trần `amount ≤ 9 007 199 254 740 991` (an toàn trong JS), vượt ⇒ `400 MONEY_INVALID`; (b) chuỗi số nguyên cho riêng trường tiền | **(a)**. FE đề xuất `< 10^18` nhưng số JSON không biểu diễn được tới đó mà không mất chữ số (JS `Number`); 9×10^15 đồng = 9 triệu tỷ — vượt mọi nhu cầu thật. (b) đúng kỹ thuật hơn nhưng phá "một kiểu số" mà FE đã chấp nhận từ `G2` (lượng cũng là số JSON, `§0.50` #8) và buộc FE parse hai kiểu. Nếu FE bác (a), chuyển (b) **chỉ** cho tiền — vẫn không chạm lượng |
| **D6** | 🔴 **Hình dạng response theo quyền** | (a) mỗi loại chứng từ có **hai** record response (đầy đủ / không tiền) và mapper chọn theo quyền, tính **một lần mỗi request**; trường tiền vắng mặt thật; (b) một record, trường tiền `null` khi không quyền | **(a)** — `always` (`§0.42`) in `null`, nên (b) không phân biệt được "chưa biết" với "bị che", đúng thứ FE cấm (§1.3 cuối). Chi phí: số record tăng; chỗ nguy hiểm là `SalesOrderLineResponse`/`SalesOrderResponse` (hiện mọi người có `PERM_SALES_ORDER_READ` đều thấy) — thêm `unitPrice`/tổng tiền vào đó **phải** đi cùng cơ chế này, nếu không là lộ giá bán cho thủ kho. Đây là **mẫu mới** của repo, cần một bất biến riêng + IT chứng minh bằng JSON thô |
| **D7** | 🔴 **Giá vốn / định giá tồn** | (a) **hoãn lần thứ năm** — `unitCost`/`issuedValue` không có; trả `valuationStatus = PENDING_COSTING` trên biến động giao; (b) dựng lớp giá bình quân | **(a).** `P3` loại, `G7 D8`, `G8 D10`, `G9 D10` đều hoãn. Chính FE (Q31, R10) cho giá vốn chưa biết ≠ 0 và cấm lấy giá bán thay. Nếu user muốn làm, đó là phase riêng đứng **trước** GĐ11 (đóng kỳ), không nhét vào đây |
| **D8** | 🔴 **Nhu cầu MRP có trừ phần đã giao không** | (a) trừ `delivered` khỏi nhu cầu hiện tại của đơn (và coverage theo allocation WO nguồn, FE Q32), đo A/B trên `D26-OMNIPLANT`; (b) giữ nguyên | **(a) — có điều kiện, và `G10-0` phải đo trước.** Giao hàng là phép **đầu tiên** làm tồn kho giảm vì lý do bán hàng; nếu nhu cầu đơn không giảm theo, MRP sẽ thấy "thiếu" đúng phần vừa giao. Nhưng công thức `uncovered` của `G6a` có `− qcAccepted` (tích luỹ) nên có thể đã tự bù — **chưa rà**. Nếu `G10-0` chứng minh không đếm đôi thì chọn (b). Nếu (a): **đổi số MRP lần thứ năm** (sau `G6b` ×2; `G8 D11`, `G9 D13` đã hoãn đúng loại thay đổi này) ⇒ báo FE kèm A/B |
| **D9** | **Lưu idempotency + claim** | (a) **một** bảng `commercial_idempotency_records` `UNIQUE (key, command)` (khuôn `G8a`), không TTL; claim là **dẫn xuất từ sổ** dòng nháp (không cột đếm); (b) ba bảng riêng + cột đếm claim | **(a)**. Cột đếm là nguồn thứ hai sẽ trôi khỏi sổ (`B217`, đã trả giá một lần ở `G8c`). Phạm vi "theo người" của FE (Q23) cố ý **không** làm: `(key, command)` + so fingerprint đã chặn đúng cái cần chặn, và key trùng giữa hai người là xung đột key thật. Retention: **không hết hạn** — mạnh hơn "≥ 24h" FE xin (Q24) |

> **Năm thứ KHÔNG hỏi vì code đã trả lời:** Q1/Q2 (ba root mới + giữ `/api/sales-orders/v1` — repo đúng bố cục đó, `§0.46` #1), Q3 (UUID — mọi khoá chính repo đều UUID), Q9/Q10 (nháp chỉ giữ hạn mức, không TTL — đúng `B16`/`B76`), Q35 (xem "Lệch" dưới), Q40 (không có `CLOSED` để tự chuyển sang).
>
> 🔴 **Bốn lệch cần nêu trong bản trả lời FE, không phải quyết định:**
> 1. **Nhà máy lấy từ `plantId`, không từ `X-Plant-Id`.** Mọi bảng `L` của §3.4 không có `plantId` trong query; repo dùng header chỉ để **đối chiếu** (`F1-D6`, `error-handling.md §5.6.1`). Danh sách phải nhận `plantId` tường minh — đúng như mọi list khác của repo.
> 2. **ID ngoài phạm vi trả 403, không 404** (Q35) — guard tra bản ghi để suy công ty rồi từ chối **trước** service (`§0.50` #9); hành vi toàn repo, đã giải thích cho FE ở GĐ2 và GĐ9.
> 3. **Hạn lô date-only: hai handoff của FE tự mâu thuẫn.** GĐ10 Q19 nghiêng về "cuối ngày Plant"; GĐ9 Q23 nghiêng về "đến đầu ngày `expiresOn`" và BE đã làm đúng thế (`G9a`, smoke: hạn lô = 00:00 giờ VN). Giữ nguyên `G9a` trừ khi FE chốt ngược lại — đổi sẽ làm lô đã tạo hết hạn khác đi.
> 4. **Tiền là số JSON, không phải chuỗi** (`D5`).

### 17.3 Gói công việc *(thứ tự thực thi; mỗi gói một commit theo `dev-workflow.md §6.6`)*

**`G10-0` — đọc nốt + baseline *(trước khi viết dòng code nào)***
Đọc hết phần "chưa rà" §17.1, ưu tiên: `SalesOrderService.confirm` (có ép `customerId`), công thức `uncovered` + `SalesOrderPlanningSourceService` (để chốt `D8` bằng số đo thật, không bằng suy luận), `SalesOrderReservationService` (cách tìm "phần giữ của chính đơn" theo vị trí), và `StockTransferService` (khuôn khoá `B151`). Đo baseline trên `git worktree` **sạch** (bẫy `§0.52` #10a). Chạy live luồng đơn bán → giữ hàng → QC → `fulfilled` hiện có để có ảnh A/B trước khi đổi.

**`G10a` — nền đơn bán *(migration `V86`)***
`sales_order_lines.unit_price` (nullable: `NULL` = chưa có, `0` = đã biết là 0) + bảng sự kiện giá (ai/khi/giá cũ→mới, không sửa/xoá); `delivery_baseline_known` (đơn cũ `FALSE`, đơn mới `TRUE` — `D3`); `customerId` bắt buộc khi xác nhận (`D4`); `POST …/commercial-prices` (1–500 dòng duy nhất, giá đã biết khoá kể cả 0); `unitPrice` tuỳ chọn trên request tạo/sửa nháp (CSV `kind=so` **không đổi** — FE tự xếp ngoài phạm vi); chặn `cancel` đơn khi còn chứng từ phía sau (chuẩn bị cho `G10b`); 10 quyền (ADMIN + MANAGER theo khuôn `G7b`/`G7c`, `docs/roles-and-permissions.md`); mẫu "hai hình dạng theo quyền" (`D6`) áp cho `SalesOrder*Response`; `commercial_idempotency_records` (`D9`). 🔴 **Thêm giá vào response của đơn bán là thay đổi nhạy cảm nhất của cả phase** — thủ kho đang gọi được `GET /sales-orders/v1/{id}`.

**`G10b` — giao hàng *(migration `V87`)***
Bảng `deliveries` + `delivery_lines` (+ sự kiện); hạn mức giao dẫn xuất từ sổ (`ordered − D − Cd`); `delivery-candidates` + `delivery-sources` (`free` / `ownReserved`, `sourceKey` do BE phát hành, `D2`); `POST`/`PATCH`/`post`/`cancel`; ghi sổ = khoá dòng đơn + khoá `StockBalance` theo thứ tự ổn định (`B151`) → với mỗi dòng: `ISSUE` từ phần giữ của chính đơn (`:R`, `consume()` reservation) rồi `ISSUE` từ phần tự do (`:F`), **cả hai** từ chối lô hết hạn → `delivered`/`deliveryProgress` → lịch sử → replay, **một** transaction; `valuationStatus = PENDING_COSTING` (`D7`); `GET /deliveries/related`; nếu `D8` = (a): hạ nhu cầu MRP + A/B. 🔴 **Mọi lệnh theo khuôn `G8`**: replay → version → luật → `touch()` → ghi → `flushTouched` (đúng +1; E2E canh). 🔴 **IT phải gồm một ca thật sự tranh chấp hai transaction** (`StockTransferIT`/`ItemWarehouseSettingConcurrencyIT` là tiền lệ) — mock không thấy khoá.

**`G10c` — hoá đơn *(migration `V88`)***
Bảng `sales_invoices` + `sales_invoice_lines`; `invoice-candidates` (dòng phiếu giao POSTED + đã lập + nháp khác); `preview` (không giữ hạn mức); `POST`/`PATCH`/`post`/`cancel`; làm tròn HALF_UP cộng dồn theo dòng đơn, `billingSnapshotVersion` mờ (opaque) bao `billedQuantityBefore`/`billedAmountBefore` ⇒ nền đổi giữa hai lần xác nhận ⇒ `PRICE_STALE` (không đổi số tiền trong cùng lần bấm cũ); hoá đơn 0 đồng ⇒ `NO_AMOUNT_DUE`; **không** có tác động tồn kho. Ca A21 (0/1/0 ⇒ 1) và A22 (hai preview cùng nền) là hai ca khó nhất — IT trên Postgres thật với hai transaction.

**`G10d` — thanh toán + màn hình đọc *(migration `V89`)***
Bảng `customer_payments` + `customer_payment_allocations`; `invoice-candidates` tối thiểu (không giá dòng đơn — `Q30`); `POST`/`PATCH`/`post`/`cancel`; `paid`/`outstanding`/`paymentState` dẫn xuất từ sổ phân bổ POSTED; `GET /sales-invoices/{id}/payments`; tiến độ đơn (`fulfillment-summary`: đã giao/còn giao/đã lập hoá đơn/đã thu, tách `qcAccepted` — `G6a` đã có chỗ); evidence `delivered` của `SalesOrderLineMetricsEvidence` thành `KNOWN` có điều kiện baseline; liên kết ngược phiếu nhập/lô/WO; tham chiếu giao hàng trên biến động tồn. Trọng tâm bảo mật: payment-only **không** thấy giá dòng đơn, delivery-only **không** thấy tiền (IT chứng minh bằng JSON thô, không bằng DTO).

**`G10-9` — nghiệm thu**
`mvn -o clean verify` + nghiệm thu mutation (nhắm: hạn mức giao `ordered − D − Cd`, thứ tự khoá, "trừ phần giữ của chính đơn trước", từ chối lô hết hạn ở **cả hai** nhánh `ISSUE`, làm tròn cộng dồn, `PRICE_STALE`, trần phân bổ `≤ outstanding − Cp`, giá đã biết khoá kể cả 0, field tiền vắng mặt theo quyền, cổng huỷ đơn) + smoke HTTP thật + `BE_RESPONSE_GD_10_<ngày>[_v2…].md` + `CLAUDE.md §0.1`/`§0.2`/bản ghi + `docs/roles-and-permissions.md` (🔴 `C10`: **10 quyền mới**) + `docs/api-guide-for-frontend.md`.

> 🔴 **Migration kế tiếp là `V86`** (`V85` của `G9c` là mới nhất) ⇒ `MANUFACTURING_PHASE_PLAN_P7_P11.md` phải đánh số lại từ **`V90`** nếu `D0` = tách 4, từ **`V87`** nếu một lượt.
>
> ⚠️ **Ba thứ sẽ phải chạy lại / sửa sau `G10`:** dữ liệu demo hiện có (`D3`: đơn cũ không giao được ⇒ phải dựng lại bằng seed; `scripts/demo-capstone2` cần thêm bước giá + khách master trên đơn); `ProductionFlowE2EIT` (bước đơn bán/`FULFILLED`) vì đơn nay đòi `customerId`; `scripts/demo` (`VIETBIKE`) vốn đã lệch bố cục URL (`§0.46` #1) — không sửa.

### 17.3a Kết quả `G10a` *(2026-10-25)*

✅ Migration `V86`, endpoint `POST /sales-orders/v1/{id}/commercial-prices`, mã `PRICE_LOCKED`, 10 quyền, bất biến `B238`–`B240`. `G10-0`
đã đo D8 (giữ nguyên ⇒ nhu cầu ảo khi giao; trừ thẳng `delivered` ⇒ đếm đôi với `qcAccepted`) ⇒ user chốt `max(qcAccepted, delivered)`.
Lệch kế hoạch: D6 làm bằng **một** object `commercial` tuỳ chọn (vắng mặt khi không quyền) thay cho hai record; đường tạo đơn cũ nhận
`customerId` tuỳ chọn để vẫn xác nhận được. `mvn -o clean verify` 2032 unit + 284 IT xanh; mutation 8/8. **Chưa:** smoke HTTP (chạy chung
với `G10b`), sửa `scripts/demo-capstone2/seed.sh` gửi `customerId` (`G10-9`). Bản ghi: `CLAUDE.md §0.67`.

### 17.3b Kết quả `G10b` *(2026-10-25)*

✅ Migration `V87`, module mới `module/delivery` (10 endpoint), mã `SOURCE_UNKNOWN`, bất biến `B241`–`B245`. D8 làm: `uncovered` trừ
`max(qcAccepted, delivered)` ở detail + `planning-demands`; huỷ đơn còn phiếu giao ⇒ 409. Lệch kế hoạch: **không** có bảng sự kiện riêng —
lịch sử dẫn xuất từ cột ai/khi/lý do của phiếu; **không** `sourceSnapshotVersion` (kiểm lại dưới khoá lúc ghi sổ). Smoke HTTP (gộp `G10a`)
tìm ra version +2 ở bổ sung giá và sửa nháp đổi ghi chú — đã sửa, lượt `171137` 39/39. `mvn -o clean verify` 2053 unit + 294 IT xanh;
mutation 12/12. Trả lời FE: `BE_RESPONSE_GD_10_2026-10-25.md` (phần 1–2). Bản ghi: `CLAUDE.md §0.68`.

### 17.3c Kết quả `G10c` *(2026-10-26)*

✅ Migration `V88`, module mới `module/billing` (9 endpoint), mã `PRICE_UNKNOWN`/`PRICE_STALE`, bất biến `B246`–`B250`. Lệch kế hoạch:
**không** đổi version đơn khi lập/ghi sổ hoá đơn (hoá đơn không đổi trường nào của đơn); ghi sổ **không** nhận `sourceSnapshotVersion`
(server so với snapshot đã lưu trên hoá đơn); lịch sử dẫn xuất từ cột ai/khi/lý do. Mutation lộ một lỗ hổng test (nền dời mà tiền không
đổi) — đã thêm IT, 17/17. `mvn -o clean verify` 2068 unit + 303 IT xanh; smoke `213414` 31/31. Trả lời FE:
`BE_RESPONSE_GD_10_2026-10-26_v2.md`. Bản ghi: `CLAUDE.md §0.69`.

### 17.4 Breaking changes dự kiến *(ghi đầy đủ vào `BE_RESPONSE_GD_10_*` khi làm)*

| Thay đổi | Breaking? |
|---|---|
| Endpoint/field/quyền mới (ba loại chứng từ, giá, tiến độ, liên kết ngược) | **Không** (additive) |
| Xác nhận đơn bán đòi `customerId` (`D4`) | **Có** — client cũ chỉ gửi tên khách nay `422`/`400` ở bước xác nhận |
| Đơn xác nhận trước migration không giao/hoá đơn được (`D3`) | **Có — về hành vi**, chỉ với dữ liệu cũ; đơn mới không ảnh hưởng |
| `SalesOrder*Response` thêm giá/tiền, ẩn theo quyền (`D6`) | **Không** với người có quyền đọc đơn trước đây; trường **vắng mặt** với người không có quyền tiền — nếu FE giả định field luôn có thì gãy |
| `cancel` đơn bị chặn khi còn chứng từ phía sau | **Có, hẹp** — chỉ khi có chứng từ GĐ10 |
| Biến động `ISSUE` từ giao hàng từ chối lô hết hạn | **Không** với đường cũ (`issue`); là luật riêng của giao hàng |
| Nhu cầu MRP hiện tại trừ phần đã giao (`D8`) | **Có — số MRP đổi** nếu chọn (a); không đổi nếu (b). Run đã lưu bất biến |
| `deliveredQuantity` của dòng đơn từ luôn `null` sang có giá trị | **Có, hẹp** — field đã có, nghĩa đổi từ "chưa có chứng từ" sang số thật khi baseline đã biết |
| Giá vốn thành phẩm | **Không làm** nếu `D7` = (a) |
| Tiền là số JSON, trần `2^53 − 1` (`D5`) | **Lệch FE** (xin chuỗi `< 10^18`) — nêu trong bản trả lời, không phải breaking của client cũ |

### 17.5 Ngoài phạm vi *(theo FE §1.1 + quyết định BE)*

Vận chuyển / vận đơn / POD / xác nhận khách đã nhận (`POSTED` chỉ nghĩa "đã xuất kho", Q39); chuyển tiền thật / cổng thanh toán; hoá đơn điện tử hợp pháp, VAT / thuế, ngoại tệ, chiết khấu, phí; tiền ứng / tiền chưa phân bổ, hoàn tiền, trả hàng, ghi chú giảm giá, đảo chứng từ; sửa / xoá chứng từ đã ghi (Q33 — repo chưa bao giờ có); báo cáo tuổi nợ AR, sổ cái; **giá vốn / định giá tồn / đóng kỳ** (GĐ11; `D7` hoãn lần thứ năm); sê-ri / MES; sổ "quyền hưởng theo nguồn" gắn lô với dòng đơn (`D2` — chính là câu hỏi GĐ6 còn mở); đổi CSV nhập đơn bán để thêm giá; endpoint đối soát baseline đơn cũ; `capabilities` kiểu object (dùng `allowedActions` theo khuôn `G9a`); và **không** tự đóng các câu còn mở của GĐ6 (Q10/Q16/Q26/Q27/Q28), GĐ8 (Q7 kho đầu ra của nháp MAKE, Q37 giá trị dư khi hoàn) và GĐ9 — FE §5 nói rõ chúng độc lập; lưu ý **Q7 và Q37 của GĐ10 là câu khác** (làm tròn hoá đơn; chặn huỷ đơn) so với cùng số ở GĐ8.

---

## Phụ lục — nợ nhỏ không thành phase riêng (đọc để biết, không phải action item)

| Nợ | Trạng thái | Ghi chú |
|---|---|---|
| `C` (idempotency `created_by` scope, `D6`) | Mở, chưa lên lịch | Cần backfill cột `created_by` (nullable) trước khi scope theo nó — việc lớn hơn một phase nhỏ, gộp vào bất kỳ phase nào đụng `stock_movements` nếu tiện |
| `D` (17 DTO còn `itemCode`/`lotCode` cũ) | **Từ chối có chủ đích** (`F7`) | Không đổi — 17 DTO này ngoài luồng spec |
| `E` (`sourceBomCode` + BOM `outputQuantity`) | **Loại khỏi phạm vi có chủ đích** (`F10`, user xác nhận 2026-08-01) | Đổi công thức nổ BOM, ảnh hưởng số MRP của mọi WO — rủi ro khác hẳn, chỉ làm nếu có yêu cầu mới rõ ràng |
| `I` (`childBom` trên snapshot component line) | **Từ chối có chủ đích** (thiết kế từ `P1`, bất biến `B12`) | WO snapshot direct-only; FE gọi endpoint BOM tree sẵn có thay vì nest vào snapshot |
| Sweep 500 toàn endpoint | Một phần đã làm (`§0.24`), chưa rà hết | Không phải phase riêng — làm dần khi chạm tới từng module |
| Commit OpenAPI snapshot vào repo | Process/hành chính, không phải code | Câu hỏi 5 cho FE, `docs/capstone2-api-gap-response.md §5` |

---

## Checklist tổng — tick khi xong, cập nhật cùng lúc với `CLAUDE.md §0.1`

- [x] **`C2-8`** — CRP tĩnh + Capacity Board + Schedule Adjustment ✅ **2026-08-05**
  - [x] Quyết định thời điểm sinh lịch — chốt: lúc `release()`
  - [x] Quyết định phạm vi nợ B (`predecessorOperationIds`) — chốt: tách `C2-8b`
  - [x] Migration (`V48`+`V49`) + scheduling trong `WorkOrderService.release()`
  - [x] Endpoint `GET .../capacity-board` + `POST .../schedule-adjustments`
  - [x] Test (unit + method-security + controller + `WorkOrderOperationRepositoryIT` cho JPQL
        timezone-bucketing) — 791 case unit + 87 case IT / 13 class IT
  - [x] Docs cập nhật (`CLAUDE.md` §0.30, `module/workorder`+`workcenter`+`shift` CLAUDE.md,
        `FRONTEND_ALIGNMENT_ROADMAP.md`, `roles-and-permissions.md`, `api-guide-for-frontend.md`,
        `BACKEND_CAPSTONE2_API_GAPS.md` checklist)
- [x] **`P3`** — Costing Engine ✅ **2026-08-05**
  - [x] Quyết định labor/overhead cost: nhập tay theo rate cố định — chốt với user
  - [x] Quyết định bỏ Material Price Variance, chỉ làm Usage Variance — chốt với user
  - [x] `ItemStandardCost` entity + migration (`V50`)
  - [x] `CostingService` (BOM cost roll-up nhiều cấp, đệ quy giống `MrpCalculationService`)
  - [x] `WorkOrderCostAccumulator` + wiring vào `MaterialIssueService`/`ProductionExecutionService`
  - [x] Variance tiền trên `WorkOrderVarianceResponse` (`usageVarianceCost` + `costVariance`)
  - [x] Endpoint CRUD `ItemStandardCost` + permission mới (`PERM_COSTING_READ`/`_MANAGE`, `V51`)
  - [x] Test (821 case unit + 88 case IT / 13 class IT) + docs + smoke test HTTP thật
- [x] **Concurrent refresh-token race** ✅ **2026-08-05**
  - [x] Quyết định có grace window hay khoá cứng — chốt: khoá cứng, không grace window
  - [x] Advisory lock (`SET NX PX`, không Lua) + breadcrumb kết quả rotate qua `TokenStoreService`
  - [x] Test đồng thời (mô phỏng race, breadcrumb absent/present, lock not-acquired)
  - [x] Docs cập nhật (`common/security/CLAUDE.md` §4.8+§4.12+§4.12a mới, `module/auth/CLAUDE.md`
        `B95`, `architecture-decisions.md`, `FRONTEND_ALIGNMENT_ROADMAP.md §8.0`)
- [x] **`P5`** — Serial Number Tracking ✅ **2026-08-06**
  - [x] Quyết định `lotTracked`+`serialTracked` loại trừ nhau — chốt với user
  - [x] Quyết định serial-tracked output không HOLD chờ QC — chốt với user
  - [x] `SerialNumber` entity + migration (`V52`) + FK trên 3 bảng (`stock_movements`,
        `material_issue_lines`, `production_receipt_lines`)
  - [x] `Item.serialTracked` flag + validate loại trừ (service + CHECK constraint)
  - [x] Nối vào `InventoryMovementService` (receive/issue/adjust) + `MaterialIssueService` +
        `ProductionReceiptService` (post/approve/qcDisposition) — **additive, không breaking**
  - [x] Test (850 case unit + 89 case IT / 13 class IT) + docs
- [x] **`P6`** — WO Close/Reconcile ✅ **2026-08-06**
  - [x] Quyết định phạm vi khoá của `CLOSED` — chốt với user: khoá hoàn toàn
  - [x] `WorkOrderStatus.CLOSED` + rà switch/so sánh status hiện có (checklist `coding-rules.md §11.3`)
        — phát hiện + sửa bug thật: `canReserve()` là danh sách phủ định, thiếu loại trừ `CLOSED`
  - [x] `close()` reconcile: giải phóng reservation `ACTIVE` còn sót (`materialReservationService
        .cancelActiveReservations`), tái dùng `PERM_WORK_ORDER_MANAGE`, không permission mới
  - [x] Endpoint `POST /work-orders/{id}/close` — migration `V53`
  - [x] Test (nhánh vào + nhánh ra + regression mỗi gate ghi) — 864 case unit + 89 case IT / 13 class IT + docs
- [x] **`D8c`** — Forgot-password / Account Recovery ✅ **2026-08-06**
  - [x] Quyết định hạ tầng gửi email — chốt với user: mock/log console, không `spring-boot-starter-mail`
  - [x] `PasswordResetTokenService` (Redis `auth:reset:{token}` + reverse index, TTL 15m, single-use)
  - [x] `AuthService.forgotPassword`/`resetPassword`/`adminUnlockAccount` — nợ #6 trả đủ 3/3
  - [x] Endpoint `POST /auth/forgot-password`, `POST /auth/reset-password`, `PATCH /admin/users/{id}/unlock`
  - [x] Test (885 case unit + 89 case IT / 13 class IT) + docs
- [x] **`C2-1`** — Audit Logs read API ✅ **2026-08-06**
  - [x] Quyết định phạm vi permission — chốt với user: `PERM_AUDIT_READ` ADMIN-only
  - [x] Quyết định phạm vi cột `plant_id` — chốt: schema-only, không wiring populate real-time
  - [x] `AuditLogQueryService` (tách khỏi `AuditLogService`) + `AuditLogRepository.search`
  - [x] Endpoint `GET /audit-logs` + `GET /audit-logs/{id}` — migration `V54`+`V55`
  - [x] Phát hiện + sửa biến thể mới của lỗi `lower(bytea)` (`Instant` bare `IS NULL`)
  - [x] Test (898 case unit + 98 case IT / 14 class IT) + docs
- [x] **`C2-2`** — Inventory Lot lifecycle API ✅ **2026-08-06**
  - [x] Quyết định cơ chế HOLD-escape gate — chốt với user: cross-module lookup thật, không heuristic
  - [x] `LotQcOriginLookupService` (mới, `module/workorder`) — entry point cross-module `inventory → workorder`
  - [x] `InventoryLotService` (list/get/changeStatus) — reuse `InventoryMovementService.changeLotStatus`
  - [x] Endpoint `GET /inventory/lots` + `GET /inventory/lots/{lotId}` + `POST .../status` — không migration
  - [x] Test (925 case unit + 105 case IT / 14 class IT) + docs
- [ ] **`G2`** — FE handoff Giai đoạn 2: tổ chức + danh mục nền *(BE xong 2026-10-14; mở cho tới khi FE nghiệm thu đạt)*
  - [x] Chốt 7 quyết định §9.2 (D1–D7) với user — 2026-10-13, theo khuyến nghị
  - [x] Baseline đo thật 2026-10-13: **1225 unit + 154 IT, failures = 0, errors = 0** (`mvn -o clean verify`, Docker)
  - [x] `G2-1` Customer master (`V69`+`V70`, 6 endpoint, `PERM_CUSTOMER_*`) — `B136`
  - [x] `G2-2` Supplier: `contactName`/`notes` + path company-scoped + activate — `B141`
  - [x] `G2-3` Settings: filter `plantId` (lỗ hổng R5), `replaceDefaults`, lock, kích hoạt lại pair cũ — `B137`, `B138`
  - [x] `G2-4` Org/Item: `search`/`status`/`type` filter, timezone, Plant có kho ACTIVE, unit ↔ UOM — `B139`, `B140`
  - [x] `G2-5` `mvn -o clean verify` (**1320 unit + 184 IT**, 0/0) + mutation 3/3 + smoke 59 lượt gọi + docs
  - [x] `G2-0` `BE_RESPONSE_GD_02_2026-10-14.md` trả lời R1–R11 + Q1–Q16, mẫu JSON copy từ lượt gọi thật
  - [x] `leadTimeDays` số lẻ (§9.6) — user chốt sửa **toàn hệ thống**; 1325 unit + 184 IT, mutation 2/2, smoke live; `BE_RESPONSE_GD_02_2026-10-14_v2.md`
  - [ ] FE báo A01–A22 đạt
- [x] **`G3`** — FE handoff Giai đoạn 3: nhập Excel dữ liệu nền *(BE xong 2026-10-14, §10 — chờ FE nghiệm thu)*
  - [x] `G3-0` đọc nốt file §10.1 + baseline đo thật (1325 + 184, khớp) — *live `/v1/import-runs` không chạy: đường cũ không đổi, smoke chỉ kiểm nó không thấy batch mới*
  - [ ] Chốt 8 quyết định §10.2 (D1–D8) với user
  - [x] `G3-1` đường mẫu cố định atomic (`V71`, target `SUPPLIER`/`CUSTOMER`, validate/commit/list/detail theo owner)
  - [x] `G3-2` `mvn -o clean verify` (1394 + 192) + mutation 10/10 + smoke 2 lượt × 78 gọi + `BE_RESPONSE_GD_03_2026-10-14.md` + docs
  - [ ] FE nghiệm thu
- [x] **`G4`** — FE handoff Giai đoạn 4: tồn kho, lô, chuyển kho *(BE xong 2026-10-15, §11 — chờ FE nghiệm thu)*
  - [x] `G4-0` đọc nốt phần "chưa rà" §11.1 + baseline đo thật (1394 + 192 trên worktree sạch) — *live endpoint cũ chạy trong smoke chung, không có lượt riêng trước khi sửa*
  - [x] Chốt D1, D7/D8/D10 + quy tắc hết hạn với user; D2–D6/D9 theo khuyến nghị (D5 trả 403, xem ghi chú §11)
  - [x] `G4-1` lot status: kiểm scope mọi kho có vị thế + từ chối reserved/no-op/expired→AVAILABLE
  - [x] `G4-2` chuyển kho atomic (`V72`, `TRANSFER_OUT`/`TRANSFER_IN`, 3 endpoint — create/list/detail)
  - [x] `G4-3` kiểm đếm tuyệt đối `POST /inventory/counts` + `version` trên balance
  - [x] `G4-4` read model: movement actor/trace + detail, aggregate `search` + `heldLotQuantity`/expired, reservations, receive `expiresAt`, `@Digits(6)`
  - [x] `G4-5` `mvn -o clean verify` (1461 + 202) + mutation 7/7 + smoke 4 lượt + `BE_RESPONSE_GD_04_2026-10-15.md` + docs
  - [ ] FE nghiệm thu
- [x] **`G5`** — FE handoff Giai đoạn 5: định mức, quy trình, công suất *(BE xong 2026-10-16, §12 — chờ FE nghiệm thu)*
  - [x] `G5-0` đọc nốt phần "chưa rà" §12.1 + baseline đo thật + chạy live BOM/capacity hiện có
  - [x] Chốt 12 quyết định §12.2 (D1–D12) với user — D1 giữ `quantityPer`, D3 Instant, **D5/D7/D8 giữ thiết kế cũ**, D9 chia theo ngày, **D10 chặn 409**, D12 làm trong G5
  - [x] `G5-1` BOM: `outputQuantity`/`bomCode`/`versionNumber`/`effectiveFrom`, atomic save, copy, versions, khoá activate — `V73`, `B155`–`B158`
  - [x] `G5-2` Routing: công đoạn trước (nợ B), ghi cả graph, copy, versions — `B159`–`B161`
  - [x] `G5-3` Ca/Lịch/Trung tâm: `workingMinutes`, `expectedVersion`, guard tham chiếu (`REFERENCE_IN_USE`) — `B162`–`B164`
  - [x] `G5-4` Công suất: tải theo phần giao nhau với kỳ, preview chỉ đọc, điều chỉnh theo D10 (409 `OPERATION_SEQUENCE_CONFLICT`) — `B165`–`B167`
  - [x] `G5-5` Nhập BOM Excel (`ImportTargetType.BOM`, mẫu `omniplant-bom-v1`) — `B168`–`B170`
  - [x] `G5-6` `mvn -o clean verify` (1548 unit + 215 IT, 0/0) + mutation 7/7 + smoke lượt `231527` (92 gọi) + `BE_RESPONSE_GD_05_2026-10-16.md` + docs
  - [ ] FE nghiệm thu A01–A36
- [ ] **`G6`** — FE handoff Giai đoạn 6: đơn bán hàng + hoạch định vật tư *(đã lập kế hoạch 2026-10-16, §13)*
  - [x] `G6-0` đọc nốt phần "chưa rà" §13.1 (ưu tiên `MaterialReservationRepository` + `MrpCalculationService`) + baseline trên worktree sạch + chạy live SO/MRP hiện có
  - [x] **Chốt `D0` — tách `G6a`/`G6b`/`G6c` hay làm một lượt** — user: **tách 3, làm liền cả 3**
  - [x] Chốt 12 quyết định còn lại §13.2 (D1–D12) — D4 mở rộng `material_reservations`; D8 + safety stock một lần: **làm, cho cả đường cũ và mới**; còn lại theo khuyến nghị
  - [x] `G6a` Đơn bán hàng: nối Customer, sinh `orderNo`, 6 số tiến độ, giữ tồn cho đơn, huỷ đơn (`V74`) — `B171`–`B177`; 1583 + 224, mutation 7/7, smoke `142411`, `BE_RESPONSE_GD_06_2026-10-17.md` v1
  - [x] `G6b` Hoạch định: run thương mại, nguồn nhu cầu nhiều chiều, ledger chuyển đổi, PO nháp (`V75`) — `B178`–`B183`; 1621 + 230, mutation 8/8, A/B `D26-`, smoke `112703`, `_v2`. 🔴 Lệch D7: không có `mrp_requirement_sources` (engine không gộp nguồn)
  - [x] `G6c` Nhập đơn bán hàng Excel (`omniplant-so-v1`, `kind=so`, không migration) — `B184`–`B186`; 1634 + 233, mutation 9/9, smoke `120521`, `_v3`
  - [x] `G6-9` `mvn -o clean verify` + mutation + smoke + `BE_RESPONSE_GD_06_*.md` (v1/v2/v3) + docs — mỗi gói tự nghiệm thu (D0 = tách). A24 cần luồng mua thật của GĐ7
  - [ ] FE nghiệm thu A01–A38
- [ ] **`G7`** — FE handoff Giai đoạn 7: đơn mua hàng, nhận hàng và QC *(đã lập kế hoạch 2026-10-18, §14)*
  - [x] `G7-0` đọc nốt phần "chưa rà" §14.1 + baseline trên worktree sạch (1634 + 233, khớp)
  - [x] **Chốt `D0` — tách `G7a`/`G7b`/`G7c`, làm liền** (2026-10-18)
  - [x] Chốt 12 quyết định còn lại §14.2 — tất cả theo khuyến nghị (D3 giữ `REVERSAL`, D4 `HOLD` cả hai đường, D8 hoãn)
  - [x] `G7a` Vòng đời PO: `CLOSED`, đổi tên trạng thái, nháp server cấp mã, `PATCH`, `close`, lý do, `expectedVersion`, `search`, `sources[]`, idempotency, trả quota khi huỷ (`V76`) — `B187`–`B192`; 1686 + 243, mutation 12/13 (M11 = code thừa đã gỡ), smoke `211857`, v1. Bốn quyền mới dời sang `G7b`/`G7c` (§14.3a)
  - [x] `G7b` Phiếu nhận: `DRAFT`, nhận vào `HOLD`, danh sách toàn nhà máy + ứng viên PO, khoá dòng PO (`V77`) — `B193`–`B198`; 1721 + 249, mutation 18/18, smoke `233219`, v2 (§14.3b)
  - [x] `G7c` QC mua: bảng quyết định bất biến, lô con có `parent_lot_id`, vùng chờ hàng không theo lô, hàng đợi QC (`V78`) — `B199`–`B206`; 1749 + 253, mutation 17/17, smoke `132237`, v3 (§14.3c)
  - [x] ~~`G7d`~~ — không làm: `D8` = hoãn (giá trị tồn trả `null`)
  - [x] `G7-9` `mvn -o clean verify` + mutation + smoke + `BE_RESPONSE_GD_07_*.md` (v1/v2/v3) + docs — mỗi gói tự nghiệm thu (D0 = tách)
  - [ ] FE nghiệm thu A01–A38
- [ ] **`G8`** — FE handoff Giai đoạn 8: lệnh sản xuất, cấp vật tư, công đoạn và dở dang *(đang chạy; `G8a` ✅ + `G8b` ✅ 2026-10-19, `G8c` ✅ 2026-10-20, §15)*
  - [x] `G8-0` đọc nốt phần "chưa rà" §15.1 (ưu tiên `MaterialIssueService.postNew`, `reserveAutomatically`, `ProductionExecutionService`, `WorkOrderCostAccumulator`) + baseline trên worktree sạch + chạy live WO/cấp vật tư/báo sản lượng hiện có
  - [x] **Chốt `D0` — tách `G8a`/`G8b`/`G8c`/`G8d` hay làm một lượt**
  - [x] Chốt 12 quyết định còn lại §15.2 *(2026-10-19: theo khuyến nghị, D11 hoãn)* — **`D1`** (trạng thái + sổ đầu vào của công đoạn), **`D2`** (kho từng dòng, đổi hành vi giữ tồn/release), **`D6`** (`COMPLETED` tự động vs ra lệnh, đụng `B53`) và **`D10`** (giá trị: hoãn lần thứ ba hay dựng) là bốn cái quyết định độ lớn
  - [x] `G8a` Nháp WO: kho từng dòng, `expectedVersion`, lịch đã chốt + revision, xem trước lịch, giải phóng phần chưa cấp, trả quota khi huỷ (`V79`)
  - [x] `G8b` Cấp vật tư: huỷ phiếu chờ của người tạo, chi tiết một phiếu, ứng viên nguồn, kiểm lại lúc duyệt (`V80`) — `B212`–`B216`; 1816 + 258, mutation 20/20, smoke `220914`, v2 (§15.3b)
  - [x] `G8c` Công đoạn: đầu vào/chuyển giao, retry + nhóm chờ, vật tư thực dùng, sản lượng cuối = công đoạn cuối, kết thúc + hoàn thành có lý do hụt (`V81`) — `B217`–`B221`; 1871 + 263, mutation 26/26, smoke `105705`, v3 (§15.3c)
  - [x] `G8d` Dở dang + hoàn vật tư: projection theo mục, entity + 4 endpoint, 3 quyền mới (`V82`) — `B222`–`B225`; 1913 + 269, mutation 27/27, smoke `162845`, v4 (§15.3d)
  - [x] ~~`G8e`~~ *(D11 = hoãn, 2026-10-19 — không làm trong `G8`)*
  - [x] `G8-9` `mvn -o clean verify` + mutation + smoke + `BE_RESPONSE_GD_08_*.md` + docs (mỗi phần tự nghiệm thu, v1–v4)
  - [ ] FE nghiệm thu A01–A40
- [ ] **`G9`** — FE handoff Giai đoạn 9: nhập thành phẩm và kiểm tra chất lượng *(đã lập kế hoạch 2026-10-20, §16)*
  - [x] `G9-0` đọc nốt phần "chưa rà" §16.1 (ưu tiên `ProductionReceiptService.post`/`approve`/`qcDisposition` từng dòng + `PurchaseQualityService` làm bản mẫu) + baseline trên worktree sạch + chạy live luồng phiếu nhập/QC hiện có để có ảnh A/B
  - [x] **Chốt `D0` — tách `G9a`/`G9b`/`G9c` làm liền** (2026-10-20)
  - [x] Chốt 15 quyết định còn lại §16.2 *(2026-10-20: D12 kẹp, D13/D14 giữ, còn lại theo khuyến nghị)* — **`D1`** (mô hình QC từng phần: nhân bản `G7c` hay sửa `quality_dispositions`), **`D2`** (endpoint QC nguyên phiếu cũ), **`D3`** (phân bổ theo lượt báo), **`D13`** (nguồn cung WO cho MRP — kỳ vọng A38 của FE lệch số thật) và **`D14`** (QC có còn làm đơn bán `FULFILLED`) là năm cái quyết định độ lớn
  - [x] `G9a` Vòng đời + đọc *(2026-10-21, `BE_RESPONSE_GD_09_2026-10-21.md`)*: `receivedOn`/`expiresOn`, rút/huỷ/chi tiết/`PATCH`, `version` + `qualityState` + bộ đếm trên response, `expectedVersion` mọi lệnh, bộ lọc list, 2 quyền đọc (`V83`)
  - [x] `G9b` Phân bổ theo lượt báo + truy xuất nguồn hai tầng *(2026-10-23, `BE_RESPONSE_GD_09_2026-10-23_v2.md`; kiểm lại lúc gửi/duyệt bỏ có chủ đích — B232)*
  - [x] `G9c` QC từng phần *(2026-10-23, `BE_RESPONSE_GD_09_2026-10-23_v3.md`)*: bảng quyết định bất biến (nhân bản `G7c`), tái dùng `splitLot`, cài lại endpoint QC cũ bên trên sổ mới, hàng đợi/lịch sử, projection kho/WO/dở dang
  - [x] `G9-9` `mvn -o clean verify` + mutation + smoke + `BE_RESPONSE_GD_09_*.md` + docs (🔴 `C10` **có** kích hoạt: 2 quyền mới ⇒ `docs/roles-and-permissions.md`)
  - [ ] FE nghiệm thu A01–A40
- [ ] **`G10`** — FE handoff Giai đoạn 10: giao hàng, hoá đơn và thanh toán *(đã lập kế hoạch 2026-10-23, §17)*
  - [x] `G10-0` đọc nốt phần "chưa rà" §17.1 (ưu tiên `SalesOrderService.confirm`, công thức `uncovered` + `SalesOrderPlanningSourceService` để chốt `D8` bằng số đo, `SalesOrderReservationService`, `StockTransferService` làm khuôn khoá) + baseline trên worktree sạch + chạy live luồng đơn → giữ hàng → QC → `fulfilled`
  - [x] **Chốt `D0` — tách `G10a`/`G10b`/`G10c`/`G10d` hay làm một lượt** *(2026-10-25: tách 4 làm liền)*
  - [ ] Chốt 9 quyết định còn lại §17.2 — **`D2`** (có dựng sổ "quyền hưởng theo nguồn" không: khuyến nghị không), **`D3`** (đơn cũ baseline chưa biết ⇒ không giao được), **`D5`** (tiền là số JSON, trần `2^53`), **`D6`** (hai hình dạng response theo quyền) và **`D8`** (MRP trừ phần đã giao — đổi số) là năm cái quyết định độ lớn
  - [x] `G10a` Nền đơn bán: giá (`unit_price` nullable + sự kiện), cờ baseline, `customerId` bắt buộc khi xác nhận, `commercial-prices`, 10 quyền, mẫu response theo quyền, bảng idempotency (`V86`)
  - [x] `G10b` Giao hàng: hạn mức dẫn xuất từ sổ, nguồn (`free`/`ownReserved`), ghi sổ nguyên tử hai nhánh `ISSUE`, `related`, (nếu `D8`) hạ nhu cầu MRP (`V87`)
  - [x] `G10c` Hoá đơn: preview, hạn mức theo dòng giao, làm tròn HALF_UP cộng dồn, `PRICE_STALE` (`V88`)
  - [ ] `G10d` Thanh toán + màn hình đọc: phân bổ nhiều hoá đơn, `paid`/`outstanding`, tiến độ đơn, liên kết ngược (`V89`)
  - [ ] `G10-9` `mvn -o clean verify` + mutation + smoke + `BE_RESPONSE_GD_10_*.md` + docs (🔴 `C10` **có** kích hoạt: 10 quyền mới ⇒ `docs/roles-and-permissions.md`)
  - [ ] FE nghiệm thu A01–A48

---

## Quy trình khi bắt đầu một phase trong roadmap này

1. Cập nhật dòng "Đang chạy" ở mục **Trạng thái** đầu file.
2. Đo lại baseline thật (`mvn -o clean verify`) — **đừng chép số ở đầu file này**, số liệu đổi theo
   từng phase.
3. Nếu phase có mục "Quyết định phải chốt với user" — hỏi và ghi lại quyết định vào phần thiết kế của
   phase đó trước khi viết migration/entity.
4. Viết kế hoạch chi tiết hơn cho riêng phase đó (theo đúng khuôn `.claude/rules/dev-workflow.md`),
   có thể thay thế phần tóm tắt ở trên bằng bản đầy đủ khi thực thi.
5. Sau khi xong: tick đủ các dòng con trong Checklist tổng, cập nhật `CLAUDE.md §0.1`/§0.2/bản ghi
   phase mới, dời "Phase trước" trong file này, và **không xoá** các phase còn CHỜ khác — chỉ tick
   phase vừa xong.

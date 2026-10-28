Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.



---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

do not delete 47 lines above

# Manufacturing ERP – Capstone Project
> **Role**: Backend Developer | **Stack**: Spring Boot 3.x · PostgreSQL · Redis · Java 17

---

## 0. TRẠNG THÁI DỰ ÁN  *(cập nhật: 2026-07-30)*

> **Đọc mục này TRƯỚC KHI sửa bất kỳ dòng code nào.**
> Thứ tự tài liệu bắt buộc: `CLAUDE.md` (file này) →
> `NEXT_PHASE_PLAN.md` (phase nghiệp vụ đang chạy) → `TEST_IMPROVEMENT_PLAN.md` (phase test).
> *(`AGENTS.md` đã xoá 2026-07-25 — nội dung được phân bổ vào `CLAUDE.md` và các file split, xem "BẢN ĐỒ TÀI LIỆU" ở cuối file này.)*

### 0.1 Phase Hiện Tại

| | |
|---|---|
| **Phase đang chạy** | **`CF-*` — Capstone 3 follow-up** (`BE_GAP_NEXT_PLAN.md`, nguồn `CAPSTONE3_BE_FOLLOWUP_2026-10-20/`). PO chốt 2026-10-27: làm `CF-0`→`CF-5`; `CF-6` (sổ giá trị tồn kho, gộp `P7`) **hoãn**; `CF-7` chọn **phương án A** (giữ giới hạn scale 6, không hỗ trợ BOM 1/3). Tiến độ: **`CF-1` ✅**, **`CF-2` ✅**, **`CF-3` ✅** (2026-10-27). `G2`–`G11` chờ FE nghiệm thu. Bản ghi: **§0.72** |
| **Việc mới nhất** *(CF)* | **`CF-3` — aggregate tồn tách phần chờ / phần loại QC mua của hàng không lô** ✅ (2026-10-27): 4 field additive, một query mỗi trang, không migration. Bất biến **`B266`**. Trước đó **`CF-2`** (lô hết hạn khỏi MRP/giữ chỗ/cấp vật tư/readiness, `B264`–`B265`, đổi số MRP) và **`CF-1`** (`B263`). Bản ghi: **§0.72** |
| **Việc mới nhất** | **`G11` — giá thành sản xuất: đơn giá giờ theo trung tâm, cơ sở chi phí lúc phát hành, giá thành theo lệnh, tính thử, checklist 11 mã, chốt giá + đóng nguyên tử** ✅ **BE XONG** (2026-10-27). Migration **`V90`** (`production_cost_rates` bất biến theo phiên bản; `production_cost_baselines` + dòng vật tư/công đoạn; 3 quyền `PERM_PRODUCTION_COST_CALCULATE`/`PERM_COST_RATE_MANAGE`/`PERM_WORK_ORDER_CLOSE` cho ADMIN + MANAGER), **`V91`** (`production_cost_runs`, snapshot JSONB, một run `FINALIZED`/lệnh), **`V92`** (`production_cost_adjustments` chỉ-giá-trị, CHECK `quantity = 0`). 13 endpoint C1–C13 (`ProductionCostController`). Mã lỗi mới `COST_SOURCE_STALE`, `CLOSE_BLOCKED`, `COST_DATA_INCOMPLETE`, `VALUATION_UNRECONCILED`. 🔴 Giá vật tư = **giá chuẩn `P3` chụp lúc phát hành** (kho chỉ biết lượng); thực tế vật tư = tiêu thụ × giá đó; hệ này **khác** `/variance` (D2). 🔴 Chốt: khoá dòng lệnh (native + `refresh`) → kiểm quyền lại dưới khoá → tính lại phía server → 11 kiểm tra → snapshot + bút toán + `CLOSED`; đường `close` cũ ⇒ `409 CLOSE_BLOCKED` cho lệnh có baseline (D5). 🔴 Mutation lộ hai lỗ hổng test: số F1/F4 của FE tình cờ không phân biệt phân bổ lũy kế với chia riêng, và luật phần dư với chia tỷ lệ — đã thêm ca số lẻ. Bất biến **`B256`–`B262`** (`module/workorder`). **Breaking change wire: CÓ, hẹp** (§0.71). Bản ghi: **§0.71** |
| **Việc trước** *(2026-10-27, `G10d`)* | **`G10d` — thanh toán khách hàng + tiến độ đơn + liên kết ngược phiếu giao** ✅ **BE XONG** (2026-10-27) — **GĐ10 đủ cả 4 phần ở BE**; `G10-9` cùng ngày: seed demo tạo khách master + giá bán + (profile `sample`) giao hàng / hoá đơn / thu tiền, chạy thật trên Postgres trắng 49/49 + 19/19 PASS; guide FE thêm mục GĐ10. Migration **`V89`** (`customer_payment_number_seq`, `customer_payments` — tiền `NUMERIC(19,0)` > 0, phương thức `CASH`/`TRANSFER`/`OTHER`; `customer_payment_allocations` — `UNIQUE (payment_id, invoice_id)`, CHECK > 0). Endpoint mới: `GET /v1/customer-payments` (`plantId` bắt buộc), `…/{id}`, `…/{id}/history`, `…/invoice-candidates?plantId&customerId`, `POST`, `PATCH /{id}`, `POST /{id}/post`, `POST /{id}/cancel`, `GET /v1/sales-invoices/{id}/payments`, `GET /sales-orders/v1/{id}/fulfillment-summary`; hoá đơn thêm `paidAmount`/`outstandingAmount`/`paymentState` + bộ lọc `paymentState`; `GET /v1/deliveries/related` thêm `productionReceiptId`/`workOrderId` (đúng một nguồn). Không quyền mới, không mã lỗi mới. 🔴 Trần phân bổ `amount − P − Cp` dẫn xuất từ sổ phân bổ; mọi lệnh khoá hàng hoá đơn theo id rồi `refresh`; ghi sổ kiểm lại dưới khoá và dời version mỗi hoá đơn đúng +1. 🔴 Tiến độ đơn sống ở `module/billing`; tiền vắng mặt (không `null`) khi thiếu quyền. Bất biến **`B251`–`B255`** (`module/billing`). **Breaking change wire: KHÔNG** (§0.70). Bản ghi: **§0.70** |
| **Việc trước** *(2026-10-26, `G10c`)* | **`G10c` — hoá đơn bán hàng: hạn mức theo dòng phiếu giao, HALF_UP cộng dồn theo dòng đơn, `billingSnapshotVersion` + `PRICE_STALE`** ✅ **BE XONG** (2026-10-26). Migration **`V88`** (`sales_invoice_number_seq`, `sales_invoices` — tiền `NUMERIC(19,0)`, CHECK `due_date ≥ document_date`; `sales_invoice_lines` — giá + nền đã lập trước, `UNIQUE (invoice_id, delivery_line_id)`). Module mới `module/billing`. Endpoint mới: `GET /v1/sales-invoices` (`plantId` bắt buộc), `GET …/{id}`, `…/{id}/history`, `POST /v1/sales-invoices/preview`, `POST`, `PATCH /{id}`, `POST /{id}/post`, `POST /{id}/cancel`, `GET /sales-orders/v1/{id}/invoice-candidates`. Mã lỗi mới `PRICE_UNKNOWN`, `PRICE_STALE`. Không quyền mới (seed ở `V86`). 🔴 Server tự tính tiền (giá từ dòng đơn), lưu đòi đúng snapshot vừa xem trước, ghi sổ tính lại dưới khoá đơn — nền dời mà tiền không đổi **vẫn** stale. 🔴 Hoá đơn không đụng tồn, không đổi version đơn. Mutation lộ một lỗ hổng test (ca nền dời mà tiền giữ nguyên) — đã thêm IT. Bất biến **`B246`–`B250`** (`module/billing`, file **mới**). **Breaking change wire: KHÔNG** (§0.69). Bản ghi: **§0.69** |
| **Việc trước** *(2026-10-25, `G10b`)* | **`G10b` — phiếu giao hàng: hạn mức từ sổ, nguồn tự do + phần giữ của chính đơn, ghi sổ hai chân, nhu cầu MRP trừ phần đã giao** ✅ **BE XONG** (2026-10-25). Migration **`V87`** (`delivery_number_seq`, `deliveries` + `delivery_lines` — CHECK lượng > 0, phần lấy từ giữ chỗ `0..quantity`, FK hai biến động `ISSUE`). Module mới `module/delivery`. Endpoint mới: `GET /v1/deliveries` (`plantId` bắt buộc), `GET …/{id}`, `…/{id}/history`, `…/related?lotId`, `POST`, `PATCH /{id}`, `POST /{id}/post`, `POST /{id}/cancel`, `GET /sales-orders/v1/{id}/delivery-candidates`, `GET /sales-orders/v1/{id}/lines/{lineId}/delivery-sources`. Mã lỗi mới `SOURCE_UNKNOWN`. Không quyền mới (seed ở `V86`). 🔴 **D8:** `uncovered = max(0, đặt − max(qcAccepted, delivered) − giữ − WO phủ)` (detail + `planning-demands`). 🔴 Ghi sổ: khoá dòng đơn → `lockPositions` theo thứ tự ổn định → mỗi dòng `ISSUE` từ phần giữ **của chính dòng** trước (`:R`) rồi tự do (`:F`), cả hai từ chối lô hết hạn; một transaction. Huỷ đơn còn phiếu giao ⇒ 409. 🔴 **Smoke HTTP tìm ra version +2 ở hai lệnh mà unit + IT đều xanh** (bổ sung giá của `G10a`; sửa nháp phiếu giao khi đổi ghi chú) — bẫy `B212` lần thứ ba. Bất biến **`B241`–`B245`** (`module/delivery`, file **mới**). **Breaking change wire: CÓ, hẹp** (§0.68). Bản ghi: **§0.68** |
| **Việc trước** *(2026-10-25, `G10a`)* | **`G10a` — nền thương mại của đơn bán: giá, khách bắt buộc, baseline giao hàng, 10 quyền** ✅ **BE XONG** (2026-10-25). Migration **`V86`** (`sales_order_lines.unit_price` nullable + CHECK 0..999 999 999; `sales_orders.pricing_revision` + `delivery_baseline_known` — `FALSE` cho mọi đơn đã qua `DRAFT`; bảng `sales_order_price_events` + `commercial_idempotency_records`; 10 quyền `PERM_DELIVERY_*`/`PERM_SALES_INVOICE_*`/`PERM_CUSTOMER_PAYMENT_*` × READ/MANAGE/POST + `PERM_SALES_PRICE_MANAGE` cho ADMIN + MANAGER). Endpoint mới: `POST /sales-orders/v1/{id}/commercial-prices`. Mã lỗi mới `PRICE_LOCKED`. 🔴 Xác nhận đơn **đòi `customerId`** (422 field) — đường tạo cũ nhận `customerId` tuỳ chọn để vẫn đi tiếp được. 🔴 Giá chỉ hiện cho người có quyền giá/hoá đơn: object `commercial` **vắng mặt** (không `null`) với người khác (D6, `@JsonInclude(NON_NULL)` trên component). Bất biến **`B238`–`B240`** (`module/sales`). **Breaking change wire: CÓ, hẹp** (§0.67). Bản ghi: **§0.67** |
| **Việc trước** *(2026-10-23, `G9c`)* | **`G9c` — phiếu nhập: QC theo lượng nhiều lượt, lô con** ✅ **BE HOÀN THÀNH** (2026-10-23) — **GĐ9 đủ cả 3 phần ở BE**. Migration **`V85`** (bảng bất biến `production_quality_decisions`; `production_receipt_lines.qc_accepted_quantity`/`qc_rejected_quantity` + CHECK `≤ quantity`; backfill tổng + một dòng quyết định từ `qc_result` cũ — DB dev 6/6 khớp). Endpoint mới: `POST`/`GET …/production-receipts/{R}/qc-decisions`; list thêm `qualityState`/`pendingQc`/`hasQcDecision`; response thêm `qualityState`, ba tổng QC, `valuationStatus = PENDING_COSTING`. Không quyền mới, không mã lỗi mới. Hàng theo lô ⇒ lô con `<lô>-A<n>`/`-R<n>` (nguồn `PRODUCTION_QC_DECISION`), phần chờ ở lại lô gốc `HOLD`; hàng không lô ⇒ chỉ phần đạt rời vùng chờ (D4); chỉ phần đạt fulfill đơn bán (D14); lô hết hạn chỉ được loại. 🔴 `qc-disposition` cũ cài lại trên **cùng sổ** (D2): chưa ai quyết ⇒ đổi trạng thái cả lô như cũ + dòng quyết định; đã quyết một phần ⇒ tách phần còn chờ; `qcResult` chỉ còn là ảnh tóm tắt (`null` khi `PARTIAL`/`MIXED`). 🔴 Cổng đổi trạng thái lô tay đổi từ "chưa có `quality_dispositions`" sang "dòng `APPROVED` còn phần chờ"; lô con bị QC sản xuất loại không bao giờ đổi tay được. 🔴 Hàm tính chất lượng nhận danh sách dòng truyền vào — `receipt.getLines()` trên trang danh sách là N+1 (C14). Bất biến **`B234`–`B237`** (`module/workorder`). **Breaking change wire: CÓ, hẹp** (§0.66). Bản ghi: **§0.66** |
| **Việc trước** *(2026-10-23, `G9b`)* | **`G9b` — phiếu nhập: phân bổ theo lượt báo sản lượng + truy xuất nguồn** ✅ **BE HOÀN THÀNH** (2026-10-23). Migration **`V84`** (bảng `production_receipt_contributions`, `UNIQUE (receipt_id, production_execution_id)`, CHECK lượng > 0, không backfill). Endpoint mới: `GET …/production-receipts/{R}/source`. Mọi response phiếu nhập thêm `outputEvidenceKnown`/`outputUnknownReason`/`contributions[]`. Không quyền mới, không mã lỗi mới. 🔴 Server tự phân bổ lúc tạo và mỗi lần sửa nháp: chỉ lượt báo của công đoạn cuối, lượt cũ nhất trước, mỗi lượt ≤ good của nó tính mọi phiếu còn giữ; phiếu trước `V84` (không dòng) được tính trừ vào lượt cũ nhất và trả `outputEvidenceKnown = false`. 🔴 **Lệch kế hoạch có chủ đích:** không kiểm lại nguồn lúc gửi/duyệt, không mã `PRODUCTION_RECEIPT_SOURCE_CHANGED` — lượt báo bất biến, mọi lệnh phiếu nhập bump version WO nên hai lần phân bổ không cùng qua được, phần tính cho phiếu cũ chỉ giảm. 🔴 Hai lần build đỏ không do code: fixture IT dùng `CANCELLED` thiếu `cancelled_at` (CHECK V83), và sửa `CLAUDE.md` trong `src/main/java` lúc `mvn` chạy ⇒ Java LS dọn `target/classes` ⇒ `repackage` "Unable to find main class". Bất biến **`B231`–`B233`** (`module/workorder`). **Breaking change wire: KHÔNG** (§0.65). Bản ghi: **§0.65** |
| **Việc trước** *(2026-10-21, `G9a`)* | **`G9a` — phiếu nhập thành phẩm: vòng đời, version, hạn mức nhập, ngày, quyền đọc** ✅ **BE HOÀN THÀNH** (2026-10-21). Migration **`V83`** (`received_on` backfill theo giờ nhà máy rồi `NOT NULL`, `expires_on`, ai/khi/lý do gửi/từ chối/rút/huỷ, `PERM_PRODUCTION_RECEIPT_READ`/`PERM_PRODUCTION_QC_READ` cho ADMIN + MANAGER). Endpoint mới: `GET …/production-receipts/{R}`, `…/events`, `PATCH …/{R}`, `POST …/withdraw`, `POST …/cancel` (lần đầu có đường vào `CANCELLED` từ `F2`); list thêm 5 bộ lọc, ứng viên thêm `workOrderId`/`search`. Không mã lỗi mới. 🔴 Gửi/duyệt/từ chối **bắt buộc** body hai version; tạo bắt buộc `expectedWorkOrderVersion` + `receivedOn`; kho nhập = kho đầu ra của WO; mọi lệnh phiếu nhập bump version WO đúng +1. 🔴 `A ≤ G ≤ P` là CHECK của DB (`V14`/`V32`) ⇒ kẹp D12 không đổi số nào thật. 🔴 **Smoke HTTP tìm ra hai lỗi mà unit + IT đều xanh:** (1) sửa nháp chỉ đổi lượng không bump version phiếu (dòng là collection inverse — bẫy `B112`); (2) bẫy µs lần thứ tư (`postedAt`/`approvedAt` 100 ns). Bất biến **`B226`–`B230`** (`module/workorder`). **Breaking change wire: CÓ** (§0.64). Bản ghi: **§0.64** |
| **Việc trước** *(2026-10-20, `G8d`)* | **`G8d` — dở dang (WIP) + hoàn vật tư về đúng nguồn** ✅ **BE HOÀN THÀNH** (2026-10-20). Migration **`V82`** (bảng `material_returns` + `material_return_lines`, `WipTransactionType.MATERIAL_RETURNED`, quyền `PERM_WIP_READ`/`PERM_MATERIAL_RETURN_READ`/`_MANAGE` cho ADMIN + MANAGER). Endpoint mới: `GET /v1/plants/{P}/wip`, `GET /v1/work-orders/{W}/wip?section`, `GET …/material-return-candidates`, `POST /v1/work-orders/{W}/material-returns`, `GET /v1/material-returns`, `GET /v1/material-returns/{R}`. Không mã lỗi mới. 🔴 Hoàn = biến động `RECEIVE` về **đúng** kho + lô của dòng cấp (`referenceType = MATERIAL_RETURN`), trần `cấp − đã dùng − đã hoàn` theo dòng cấp (cộng mục trùng), không đổi `issuedQuantity`, không khôi phục giữ tồn, lô `HOLD`/`REJECTED`/hết hạn ⇒ `409 LOT_NOT_ELIGIBLE`; `Idempotency-Key` **bắt buộc**. 🔴 Trần vật tư dùng khi báo sản lượng nay trừ cả phần đã hoàn (chặn mới, hẹp). 🔴 WIP là read model sống, **không** cột/bảng chụp; lệnh cũ trả `null`. Chưa có giá trị (D10). Bất biến **`B222`–`B225`** (`module/workorder`). **Breaking change wire: CÓ, hẹp** (§0.63). Bản ghi: **§0.63** |
| **Việc trước** *(2026-10-20, `G8c`)* | **`G8c` — công đoạn: đầu vào/chuyển giao, xử lý lại, vật tư thực dùng, sản lượng cuối, hoàn thành có hụt** ✅ **BE HOÀN THÀNH** (2026-10-20). Migration **`V81`** (`work_orders.operation_tracking` DEFAULT TRUE, `FALSE` cho lệnh đã có báo sản lượng + `terminal_shortfall_quantity`/`shortfall_reason`; `work_order_operations.completed_at`/`completed_by`; `production_executions.source_execution_id`; bảng `work_order_operation_inputs` + `production_execution_consumptions`). Endpoint mới: `GET /v1/work-orders/{W}/operations`, `POST …/operations/{O}/input`, `POST …/operations/{O}/finish`, `POST /v1/work-orders/{W}/complete-production`. Mã lỗi mới `MATERIAL_ISSUE_REQUIRED`, `PRODUCTION_INPUT_UNKNOWN`. Không quyền mới. 🔴 Báo sản lượng **bắt buộc** `expectedWorkOrderVersion` (+ `workOrderOperationId` khi có quy trình); **`actualGoodQuantity` = good công đoạn cuối** (đổi số liệu); lượt mới dùng đầu vào còn lại, lượt xử lý lại dùng phần chờ của lượt nguồn; `consumption[]` cộng mục trùng rồi so `cấp − đã dùng`, không trừ kho; dung sai 60 s. 🔴 **Không cột đếm nào trên công đoạn** — dẫn xuất từ sổ (lệch D1(a) có chủ đích). 🔴 Đối chiếu A-case FE trước khi gửi lộ **A25** (công đoạn đầu chỉ kết thúc khi nhận đủ kế hoạch) — đã sửa theo FE. Mutation lộ một lỗ hổng test (phần chờ làm lại phải trừ phần đã xử lý lại). Bất biến **`B217`–`B221`** (`module/workorder`). **Breaking change wire: CÓ** (§0.62). Bản ghi: **§0.62** |
| **Việc trước** *(2026-10-19, `G8b`)* | **`G8b` — cấp vật tư: phiếu bổ sung, kiểm lại lúc duyệt, huỷ của người tạo, chi tiết + ứng viên nguồn** ✅ **BE HOÀN THÀNH** (2026-10-19). Migration **`V80`** (`material_issues.supplemental` DEFAULT FALSE + `cancellation_reason`). Endpoint mới: `POST /v1/work-orders/{W}/material-issues/{I}/cancel`, `GET /v1/material-issues/{I}` (kèm `decisionContext[]`), `GET /v1/work-orders/{W}/material-issue-candidates` (`NORMAL`/`SUPPLEMENTAL`); list thêm `search` + `fromDate`/`toDate`. Không quyền mới, không mã lỗi mới. 🔴 Phiếu nhiều dòng bắt buộc `expectedWorkOrderVersion`; duyệt/từ chối/huỷ bắt buộc **cả** version lệnh lẫn version phiếu. Vượt định mức xét theo vật tư **trên cả phiếu**; `supplemental: false` + vượt ⇒ `409 OVER_BOM_APPROVAL_REQUIRED`; phiếu chờ kiểm tồn nguồn lúc tạo (cộng dòng cùng nguồn); tự duyệt cần thêm `OVERRIDE`; lý do 5–2000. 🔴 **Smoke HTTP tìm ra lỗi mà unit + IT đều xanh:** phiếu cấp đầu tiên của WO `RELEASED` tăng version **+2** (`markInProgress` bị flush cùng phiếu, rồi `touchAndFlush` ghi lần nữa) — sửa cả đường post lẫn duyệt (`flushTouched`), guard ở `ProductionFlowE2EIT`. 🔴 Mutation lộ hai lỗ hổng test (cộng dòng cùng nguồn; gửi lại lệnh duyệt) — đã thêm test. Bất biến **`B212`–`B216`** (`module/workorder`). **Breaking change wire: CÓ** (§0.61). Bản ghi: **§0.61** |
| **Việc trước** *(2026-10-19, `G8a`)* | **`G8a` — nháp lệnh SX: kho từng dòng, version bắt buộc, lịch đã chốt, trả quota khi huỷ** ✅ **BE HOÀN THÀNH** (2026-10-19). Migration **`V79`** (`work_order_component_lines.warehouse_id` nullable; `work_orders.schedule_revision`/`schedule_calendar_revision`/`schedule_time_zone`/`schedule_captured_at`; bảng `work_order_idempotency_records`). Endpoint mới: `POST /v1/work-orders/{W}/schedule-preview`, `POST /v1/work-orders/{W}/release-reservations`; list WO thêm `fromDate`/`toDate`. Không quyền mới, không mã lỗi mới. 🔴 **Version bắt buộc** trên PATCH/plan/release/cancel/reserve/giữ tay/bỏ giữ (D5) — adapter FE cũ nhận `400` ngay khi deploy; mọi lệnh bump đúng một version qua `touch()` (bẫy B112). Giữ tồn chỉ ở kho của dòng khi dòng có kho (D2, `NULL` = cả nhà máy như cũ). Xem trước lịch = **đúng** công thức release (`WorkOrderScheduler`). Huỷ WO trả quota ledger một lần (routine chung với huỷ PO G7a); phiếu cấp chờ duyệt chặn huỷ; WO MAKE khoá `plannedQuantity`/`outputWarehouseId`. 🔴 Baseline sạch lộ **flake có sẵn từ G7c** (`migrate_v78_*` đọc `pg_constraint` không lọc schema) — đã sửa. Smoke HTTP 39/39, không lỗi mới. Bất biến **`B207`–`B211`** (`module/workorder`). **Breaking change wire: CÓ** (§0.60). Bản ghi: **§0.60** |
| **Việc trước** *(2026-10-19, `G7c`)* | **`G7c` — QC mua hàng theo số lượng, lô con** ✅ **BE HOÀN THÀNH** (2026-10-19). Migration **`V78`** (bảng bất biến `purchase_quality_decisions`; `goods_receipt_lines.accepted_quantity`/`rejected_quantity` + CHECK `≤ received`; `inventory_lots.parent_lot_id` + `source_reference_type`/`_id`; `MovementType` `LOT_SPLIT_OUT`/`LOT_SPLIT_IN`; quyền `PERM_PURCHASE_QC_READ`/`_MANAGE` cho ADMIN + MANAGER). Endpoint mới: `GET /v1/purchase-qc`, `POST /v1/goods-receipts/{id}/lines/{lineId}/qc`, `GET /v1/goods-receipts/{id}/quality-decisions`. Không mã lỗi mới. Hàng theo lô ⇒ lô con `<lô>-A<n>` `AVAILABLE` / `-R<n>` `REJECTED`, phần chờ ở lại lô gốc `HOLD`; hàng không theo lô ⇒ chỉ phần đạt rời `qualityHoldQuantity`. Tồn thực tế + PO không đổi. Đổi trạng thái lô đang chờ QC / lô con bị loại ⇒ `LOT_NOT_ELIGIBLE`; phiếu đã QC không đảo nhập được. 🔴 **Smoke HTTP tìm ra lỗi mà unit + IT đều xanh:** `childLots[].parentLotId` trong response lệnh QC là `null` — `InventoryLot.parentLotId` là cột phản chiếu chỉ-đọc, rỗng trên lô con vừa tạo; IT chỉ kiểm cột DB. 🔴 Mutation lộ hai lỗ hổng test (split trừ phần reserved; nhánh "QC xong ⇒ hết chờ") — đã thêm test trước khi gửi. Bất biến **`B199`–`B204`** (`module/purchasing`), **`B205`–`B206`** (`module/inventory`). **Breaking change wire: CÓ, hẹp** (chặn mới, §0.59). Bản ghi: **§0.59** |
| **Việc trước** *(2026-10-18, `G7b`)* | **`G7b` — phiếu nhận nháp, nhận mua vào vùng chờ kiểm** ✅ **BE HOÀN THÀNH** (2026-10-18). Migration **`V77`** (phiếu nhận `DRAFT` + `received_date`/`reference`/`posted_by`/`cancelled_by`/`quality_held`, `posted_at`/`idempotency_key` nullable, `goods_receipt_lines.expires_on`, `goods_receipt_number_seq`, quyền `PERM_GOODS_RECEIPT_READ`/`_MANAGE` cho ADMIN + MANAGER). Endpoint mới: `GET /v1/goods-receipts` (toàn nhà máy), `GET /v1/goods-receipts/purchase-order-candidates`, `POST /v1/goods-receipts`, `PATCH /{id}`, `POST /{id}/post`; `POST /{id}/cancel` hai nghĩa theo trạng thái. Mã lỗi mới `PENDING_GOODS_RECEIPT`. 🔴 **Mọi lần nhận mua vào `HOLD`**, cả đường cũ — tồn khả dụng không tăng tới QC (`G7c`). 🔴 **Smoke HTTP tìm ra hai lỗi mà unit + IT đều xanh:** (1) với open-session-in-view, guard `hasReceiptAccess` nạp PO + dòng vào EntityManager **trước** transaction ⇒ câu `@Lock` gặp entity đã quản lý thì **kiểm version** thay vì đọc lại ⇒ kẻ thua của hai lần post đồng thời chết `CONCURRENT_MODIFICATION` thay vì `PLANNED_QUANTITY_EXCEEDED` — mất **hai** vòng smoke (vòng hai: `refresh` không cascade vào collection dòng chưa khởi tạo); (2) đảo nhập / post cũ dùng `save` ⇒ `version` trả về cũ (bẫy §0.39). Bất biến **`B193`–`B198`** (`module/purchasing`). **Breaking change wire: CÓ** (hàng mua vào `HOLD`, §0.58). Bản ghi: **§0.58** |
| **Việc trước** *(2026-10-18, `G7a`)* | **`G7a` — vòng đời đơn mua hàng** ✅ **BE HOÀN THÀNH** (2026-10-18). Migration **`V76`** (đổi tên `SENT`→`CONFIRMED`, `RECEIVED`→`FULLY_RECEIVED` tại chỗ + `CLOSED`; `closed_quantity` + CHECK `received + closed <= ordered`; reference/lý do/ai-khi xác nhận-huỷ-đóng + `previous_status`; `purchase_order_number_seq`; bảng `purchasing_idempotency_records`; `released_*` trên `supply_suggestion_conversions`). Endpoint mới: `POST /v1/purchase-orders/drafts`, `PATCH /{id}`, `/confirm`, `/close`; `/cancel` nhận body tuỳ chọn; list thêm `search` + khoảng ngày. Mã lỗi mới `SOURCE_LOCKED`. Không permission mới (chuyển BUY nay cần thêm `PERM_PURCHASE_ORDER_MANAGE`). Huỷ PO trả quota hoạch định **một lần**, đóng thì không. 🔴 **Hai lỗi thật do chính phase tìm ra:** (1) `V76` bản đầu `UPDATE` trước khi gỡ CHECK ⇒ nâng cấp chết trên mọi DB có PO `SENT` (DB trống xanh, chỉ IT seed ở V75 bắt được); (2) smoke lộ thứ tự dòng PO **đảo giữa hai lần đọc** (dòng cùng flush có `created_at` bằng nhau sau khi làm tròn µs) — có từ trước G7, nay tie-break theo id. Bất biến **`B187`–`B192`** (`module/purchasing`). **Breaking change wire: CÓ** (tên trạng thái, §0.57). Bản ghi: **§0.57** |
| **Việc trước** *(2026-10-18, `G6c`)* | **`G6c` — nhập đơn bán hàng Excel `kind=so`** ✅ **BE HOÀN THÀNH** (2026-10-18). **Không migration, không permission mới, không mã lỗi mới, không endpoint mới** — `kind=so` trên đường batch `G3` có sẵn (`/v1/data-import/batches`), mẫu `omniplant-so-v1` (sheet `Sales orders`, `_meta` key/value như BOM). Mỗi nhóm một `SalesOrderService.createDraft` (đường `G6a`: mã `SO-…` server cấp, audit riêng) trong **một** transaction cả lô; nhóm cuối lỗi ⇒ 0 đơn. Plant-scoped như item. Ngày: chữ `yyyy-MM-dd` hoặc ô ngày thật đọc theo **hệ ngày của workbook** (1900/1904). 🔴 Mutation lộ ra pattern ngày chưa được test nào chứng minh (`LocalDate.parse` một mình đã chặn `20/10/2026`) — nó chặn năm có dấu `+12026-…`; đã thêm case. Bất biến **`B184`–`B186`** (`module/dataimport`). **Breaking change wire: KHÔNG** (additive: `kind=so`, `salesOrderCount`). Bản ghi: **§0.56** |
| **Việc trước** *(2026-10-18, `G6b`)* | **`G6b` — run MRP thương mại + chuyển đổi một phần** ✅ **BE HOÀN THÀNH** (2026-10-18). Migration **`V75`** (`mrp_runs` + `run_kind`/`source_version`/`conversion_version`/`input_snapshot`; bảng `supply_suggestion_conversions`; CHECK `warehouse_resolution_source` + `RUN_ITEM_MAPPING`; không backfill). Endpoint mới: `POST /v1/planning-runs/commercial`, `GET /v1/planning-runs/{id}/requirements/{lineId}`, `POST`/`GET /v1/planning-runs/{id}/conversions`. Mã lỗi mới `PLANNING_SOURCE_STALE`. Không permission mới. 🔴 **Lệch số MRP thật, áp cả run cũ (user chốt):** tồn an toàn bị cộng **mỗi dòng** cùng vật tư–kho — A/B trên `D26-OMNIPLANT` **11 → 6 đề xuất, 8 → 3 đề xuất mua**; nhu cầu linh kiện nay làm tròn **lên** 6 số lẻ trước khi nổ (Q28). Bất biến **`B178`–`B183`**. **Breaking change wire: CÓ** (số MRP). Bản ghi: **§0.55** |
| **Việc trước** *(2026-10-17, `G6a`)* | **`G6a` — đơn bán hàng nối khách + giữ tồn cho đơn** ✅ **BE HOÀN THÀNH** (2026-10-17). Migration **`V74`** (`sales_orders` + `customer_id`/`customer_code`/`customer_reference`/`cancel_reason`, sequence `sales_order_number_seq`; `material_reservations` nay có **hai loại chủ** — WO hoặc đơn bán, `chk_material_reservations_single_owner`; không backfill). Endpoint mới: `POST /sales-orders/v1/drafts`, `PATCH /{id}/draft`, giữ/bỏ giữ/danh sách giữ tồn + `allocation-candidates`; confirm/cancel nhận body tuỳ chọn; list thêm `search` + khoảng ngày; detail thêm **6 số tiến độ** (`produced`/`delivered` = `null` + `UNKNOWN`); planning-demands thêm `uncoveredOnly`. Không permission mới, không mã lỗi mới. 🔴 Giữ tồn dùng **chung** `stock_balances.reserved_quantity` với WO ⇒ mọi số khả dụng tự trừ phần đơn giữ (một nguồn, không hai con số). 🔴 `searchByWarehouse` từng **inner join** WO ⇒ dòng đơn bán sẽ biến mất khỏi màn hình kho — đổi left join. Bất biến **`B171`–`B176`**. **Breaking change wire: CÓ, hẹp** (§0.54). Bản ghi: **§0.54** |
| **Việc trước** *(2026-10-16, `G5`)* | **`G5` — BOM, quy trình, ca/lịch/trung tâm, công suất** ✅ **BE HOÀN THÀNH** (2026-10-16). Migration **`V73`** (`bom_headers` + `bom_code`/`version_number`/`output_quantity`/`effective_from`, `routings` + `version_number`/`name`/`effective_from`, bảng `routing_operation_predecessors`; backfill version theo thứ tự tạo, mã BOM = `sản phẩm-revision`). Endpoint mới: `PUT /v1/companies/{C}/bom-drafts`, `PUT /v1/companies/{C}/routing-drafts`, copy/versions/POST deactivate cho BOM + routing, `GET /v1/plants/{P}/capacity/loads`, `POST /v1/plants/{P}/capacity/preview`, `kind=bom` cho `/v1/data-import/batches`. Không permission mới. 🔴 **Lệch số liệu thật đã sửa:** bảng công suất dồn **cả** công đoạn vào ngày bắt đầu (công đoạn 3 ngày = quá tải giả ngày 1, trống giả ngày 2–3; công đoạn bắt đầu trước kỳ không hiện). 🔴 **Activate đồng thời** chỉ dựa unique index ⇒ request thua chết thay vì nhường — nay khoá item. Bất biến **`B155`–`B170`**. **Breaking change wire: CÓ** (§0.53). Bản ghi: **§0.53** |
| **Việc trước** *(2026-10-15, `G4`)* | **`G4` — tồn kho, lô, chuyển kho** ✅ **BE HOÀN THÀNH** (2026-10-15). Migration **`V72`** (`stock_transfers` + `stock_transfer_lines`, `MovementType` thêm `TRANSFER_OUT`/`TRANSFER_IN` + sửa `chk_stock_movements_type`). Endpoint mới: `POST/GET /v1/inventory/transfers`, `GET /v1/inventory/transfers/{id}`, `POST /v1/inventory/counts`, `GET /v1/inventory/movements/{id}`, `GET /v1/inventory/reservations`. Không permission mới (tái dùng `PERM_INVENTORY_READ`/`_MOVE`). 🔴 **Lỗ hổng phân quyền thật đã đóng:** đổi trạng thái lô (global) trước đây chỉ kiểm **một** kho trong body ⇒ người có quyền ở kho A đổi được chất lượng hàng ở kho B ngoài scope. 🔴 **Lệch số liệu thật do smoke tìm ra:** aggregate để lô `HOLD` nằm ngoài **mọi** bucket và tính lô `AVAILABLE` quá hạn vào `available` trong khi `/issue` từ chối nó. 🔴 **Lặp lại lỗi µs của G3** trên `postedAt` (đã sửa). Bất biến **`B149`–`B154`** (`module/inventory/CLAUDE.md`). **Breaking change wire: CÓ** (chặn mới + số aggregate đổi, xem §0.52). Bản ghi: **§0.52** |
| **Việc trước** *(2026-10-14, `G3`)* | **`G3` — nhập Excel mẫu cố định, atomic** ✅ **BE HOÀN THÀNH** (2026-10-14). Migration **`V71`** (cột batch trên `import_runs`: `kind`, `owner_user_id`, `locale`, `template_version`, `committed_at`, upload key; không backfill). 4 endpoint `/v1/data-import/batches*` **song song** `/v1/import-runs*` (D1); commit **một transaction cả lô** (D2); quyền = READ+MANAGE **của chính loại**, không dùng `PERM_DATA_IMPORT_*` (D3); lịch sử theo **owner**, batch người khác ⇒ 404 (D7); 500 dòng/5 MiB riêng (D5); ô Excel **strict** (D8). Target mới `SUPPLIER`/`CUSTOMER` cho **cả hai** đường. 🔴 **Hai lỗi thật do chính phase tìm ra:** (1) `MaxUploadSizeExceededException` rơi xuống catch-all ⇒ upload > 10 MB trả **500** (cùng họ `§0.24`/`§0.41`) — nay 413 `FILE_SIZE`; (2) smoke HTTP lộ **`committedAt` không ổn định giữa lần commit và lần đọc lại** — clock Windows 100 ns, Postgres làm tròn về µs ⇒ FE A17 sẽ fail; sửa + guard IT. Bất biến **`B142`–`B148`** (`module/dataimport/CLAUDE.md`, file **mới**). **Breaking change wire: KHÔNG** (thuần additive; 500→413 là sửa sai). Bản ghi: **§0.51**, trả lời FE: `BE_RESPONSE_GD_03_2026-10-14.md` |
| **Việc trước** *(2026-10-14, `G2`)* | **`G2` — danh mục nền cho FE giai đoạn 2** ✅ **BE HOÀN THÀNH** (2026-10-14). Migration **`V69`** (bảng `customers` + `suppliers.contact_name`/`notes`) + **`V70`** (`PERM_CUSTOMER_READ/_MANAGE`, grant sao chép từ `PERM_SUPPLIER_*`). Customer master mới (6 endpoint); Supplier thêm đường company-scoped + activate **song song** đường cũ; thiết lập kho có `replaceDefaults` (nullable) + filter `plantId` + **khoá bi quan row item**; filter `search`/`status` cho Plant/Warehouse (trước đây **bị bỏ qua im lặng**), `status`/`type`/`lotTracked` cho Item; timezone validate `ZoneId.of`; deactivate Plant còn kho ⇒ `ACTIVE_CHILDREN_EXIST`; `items.unit` lần đầu nối UOM master. 🔴 **Ba lỗi thật, không ai báo:** (1) `GET /item-warehouse-settings` không truyền `warehouseId` trả settings của **mọi công ty**; (2) hai upsert đồng thời để lại **hai** kho mặc định — trigger `V66` **không** chặn được ở `READ COMMITTED` (đo bằng IT, mutation bỏ khoá ⇒ đỏ vòng 0); (3) timezone rác được lưu rồi nổ **500** lúc release WO. Bất biến **`B136`–`B141`** (đánh số từ 136 vì `B124`–`B135` đã giữ chỗ cho P7–P11). **Breaking change wire: CÓ** (7 chặn mới, không bỏ field nào — mục thứ 7 là số lẻ cho field số nguyên trên **mọi** endpoint). Bản ghi: **§0.50**, trả lời FE: `BE_RESPONSE_GD_02_2026-10-14_v2.md` |
| **Việc trước** *(2026-09-02)* | **Refactor toàn bộ hạ tầng Audit — track `AR-*`** ✅ **HOÀN THÀNH** (2026-09-02). Nguồn: `AuditRefactorPlan.md` (AR-0 → AR-10, phạm vi do user chốt; failure policy **`FAIL_OPEN` toàn hệ thống**, §11.1). Migration **`V67`** (outbox + 10 cột mới trên `audit_logs` + bảng `audit_log_entities`) + **`V68`** (trigger append-only). 🔴 **Ba defect gốc, cả ba đều IM LẶNG — không log, không lỗi, không test nào đỏ:** (1) `@TransactionalEventListener(AFTER_COMMIT)` chỉ giao event khi thread publish đang trong transaction, mà **`AuthService` không có `@Transactional` ở bất kỳ đâu** ⇒ **mọi** event auth (`LOGIN`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `SUSPICIOUS_TOKEN_REUSE`, `SESSION_ABSOLUTE_TIMEOUT`, `PASSWORD_RESET`) bị **vứt bỏ hoàn toàn** — nửa quan trọng nhất về bảo mật của audit trail **chưa bao giờ tồn tại**; (2) khoảng giữa business `COMMIT` và `INSERT` của listener không có bảo đảm nào, và event `FAILURE` trong transaction rollback **không bao giờ** được ghi (rollback mang theo luôn bằng chứng); (3) `X-Trace-Id` (filter nhận tới 64 ký tự) đổ vào cột `VARCHAR(32)` ⇒ **client tự tắt được audit trail của chính mình bằng một header**, insert chết **sau khi** business đã commit. Sửa bằng **transactional outbox**: outbox row nằm **trong** transaction nghiệp vụ (`B119`), failure ghi ở `REQUIRES_NEW` qua **bean riêng** (`B120`), `eventId` do producer sinh + unique index làm retry idempotent (`B121`), trigger `V68` chặn UPDATE/DELETE cho **mọi** role kể cả superuser (`B122`), sanitize ở **một** boundary (`B123`). Cộng: model sự kiện đa-entity (`audit_log_entities` — `PERMISSION_GRANTED` trước đây ghi role mà **bỏ mất permission**), `@Auditable` v2 thấy được argument (mọi method `void` trước đây ghi entity id `null`), `plantId` **lần đầu tiên được nạp thật** (cột có từ `V54`, `NULL` trên mọi dòng vì chưa từng có writer nào set), Read API v2 (**khôi phục `entityId`**), metrics + health, retention **mặc định TẮT**. **Breaking change wire: KHÔNG** (thuần additive). Bản ghi: **§0.49**, chi tiết kỹ thuật: `common/audit/CLAUDE.md` (viết lại hoàn toàn), log triển khai: `AuditRefactorPlan.md §13` |
| **Việc trước** *(2026-08-25)* | **Sửa contract giá trị của Audit Log — `changes[].oldValue`/`newValue` về `string`** ✅ **HOÀN THÀNH** (2026-08-25). Nguồn: `BACKEND_AUDIT_LOG_VALUE_CONTRACT_2026-08-25.md` (FE báo: màn hình chi tiết Audit **sập** vì một object lọt vào cây render React). 🔴 **Hồi quy do chính commit `caps2 done` gây ra, chưa ai ghi nhận:** `AuditLogChangeResponse` đổi `String` → `JsonNode` cùng lượt bật field-level diff (2026-08-17) ⇒ kiểu runtime của **cùng một field** đổi theo từng dòng (chuỗi / số / boolean / object / array), trong khi cột lưu là `jsonb` nên đủ mọi hình dạng. Đo A/B thật trên **cùng một** audit `WORK_ORDER_CREATED`: trước sửa `['int','list','str']`, sau sửa `['str']` + `null`. `AuditLogQueryService.textValue` chuẩn hoá: object/array → **JSON compact**, số/boolean → dạng chuỗi, chuỗi → **bỏ dấu nháy JSON**, JSON `null`/SQL `NULL` → `null`, chuỗi không parse được → giữ nguyên. **Không migration, không permission mới, không endpoint mới.** Bất biến **`B118`** (`common/audit`). **Breaking change wire: CÓ, hẹp** — nhưng là **khôi phục** contract mà OpenAPI vẫn công bố (`type: string`), tức trả về đúng hình dạng của `C2-1`. Bản ghi: **§0.48**, trả lời FE: `FE_SingleTask_Response.md` |
| **Việc trước** *(2026-08-23)* | **Refactor hạ tầng xử lý lỗi — track `EH-*`** ✅ **6/8 HẠNG MỤC XONG** (2026-08-23). Nguồn: `ExceptionHandlerRefactorPlan.md`. **Không migration, không permission mới.** `EH-1` lưới an toàn cho lỗi thoát khỏi filter chain (Redis chết ⇒ trước đây rơi vào `/error` mặc định của Boot, một hình dạng JSON FE chưa từng đọc được) + `ApiErrorController`. `EH-2` 🔴 **việc lớn nhất**: tách `OPERATION_NOT_ALLOWED` — **96 throw site dùng chung một mã** ⇒ FE chỉ còn cách parse `message` tiếng Anh. Khảo sát phân loại rồi tách **3 mã mới** trên **59** site (`RESOURCE_INACTIVE` 23, `RESOURCE_SCOPE_MISMATCH` 32, `DOCUMENT_HAS_NO_LINES` 4); **37 site còn lại cố ý giữ nguyên** làm catch-all. Phạm vi do user chốt qua `AskUserQuestion`. `EH-3` `DataIntegrityErrorMapper` — khoá ngoại/`CHECK`/`NOT NULL` không còn bị trả nhầm là "đã tồn tại" (409 → **422**). `EH-4` `MrpRunService` không còn ghi message nội bộ ra response, và bản ghi `FAILED` nay sống sót qua transaction đang hỏng (`MrpRunStateRecorder`, `REQUIRES_NEW`, đúng khuôn `WorkOrderBlockRecorder`). `EH-5` xoá 4 mã chưa từng ném, **giữ 5** kèm lý do. `EH-6` viết lại `error-handling.md §5.2` (cây exception 12 lớp trong tài liệu **chưa bao giờ tồn tại** trong code). **Breaking change wire: CÓ** (đổi `code`, **không** đổi HTTP status) — bảng đầy đủ ở `§0.47`. `EH-7`/`EH-8` hoãn có chủ đích. Bản ghi: **§0.47** |
| **Việc trước** *(2026-08-22)* | **Bộ dữ liệu demo SẠCH cho Capstone 2 (namespace `D26-`)** ✅ **HOÀN THÀNH** (2026-08-22). Nguồn: `BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx`. Xoá sạch DB rồi seed lại **qua REST API thật** một bộ dữ liệu trình diễn cho **khách hàng/hội đồng**: 1 công ty, 1 nhà máy, 5 kho, 10 vật tư, 3 BOM 2 cấp, 3 quy trình, 4 tổ sản xuất, tồn kho đầu kỳ. **Không** tạo Sales Order (đổi lại theo yêu cầu của user sau lượt bàn giao đầu tiên — người vận hành tự tạo đơn hàng của mình, seed lại phần còn lại được nhiều lần). 🔴 Khác hẳn seeder `VIETBIKE` (`§0.45`): bộ này **không** có bản ghi lỗi/`INACTIVE`/`BLOCKED` nào, và **cố ý dừng trước MRP** để người trình bày chạy Planning trực tiếp. Nghiệm thu bằng `verify.sh` trên DB dùng-một-lần: **PF-01→PF-15 và smoke test 9 bước §12 đều PASS** (blockedProposals=0, reservedPercent=100, lô HOLD→AVAILABLE, đơn hàng FULFILLED). **Không sửa một dòng `src/main` nào**, không migration. Bản ghi: **§0.46**, hướng dẫn: `docs/demo-capstone2-guide.md`. **[2026-08-23] mở rộng**: 40 vật tư / 3 dòng sản phẩm / 9 BOM / 9 routing / 8 tổ sản xuất, thêm nhà cung cấp + giá thành chuẩn + mua hàng + bán hàng + 3 lượt MRP + 8 lệnh sản xuất phủ 6 trạng thái, để mọi màn hình có dữ liệu chụp hình làm slide. **[2026-08-25] tách hai công ty**: `seed.sh` nhận `PROFILE` — `sample` dựng `D26-OMNIPLANT` (đi hết luồng, để chụp hình), `demo` dựng `DEMO-OMNIPLANT` (dừng ở tồn kho đầu kỳ, để diễn trực tiếp); cộng lấp mọi trường ngày/giờ API cho ghi (xem §0.46) |
| **Việc trước** *(2026-08-14)* | **Bộ dữ liệu demo `VIETBIKE` + sửa lỗi P0 `MultipleBagFetchException`** ✅ **HOÀN THÀNH** (2026-08-14). Dựng `scripts/demo/` (1 orchestrator + 2 lib + 15 stage) seed lại **toàn bộ** dữ liệu demo từ DB trắng **qua REST API thật**, chủ đề nhà máy xe đạp, tên tiếng Việt có dấu, phủ **mọi** trạng thái của mọi vòng đời chứng từ. 🔴 **Lỗi P0 do chính bộ dữ liệu này phát hiện:** `WorkCalendarRepository` join-fetch **hai** bag (`weeklyShifts` + `Shift.breaks` — bag thứ hai nằm xa hơn một association) ⇒ **mọi** `POST /work-orders/{id}/release` qua lịch làm việc có ca **kèm giờ nghỉ** trả **500**; đây là **lần thứ ba** repo dính bẫy này (sau `§0.27`, `§0.29`). Sửa + `WorkCalendarLookupServiceIT` (class IT thứ **18**), nghiệm thu mutation 4/4 đỏ. Không migration, không permission mới, **không breaking change wire**. Bản ghi: **§0.45**, hướng dẫn demo: `docs/demo-dataset-guide.md` |
| **Việc trước đó** | **Trả lời `live-data-audit.md`: available theo lot status + idempotency cho planning run** ✅ **HOÀN THÀNH** (2026-08-14). FE báo 3 mục; kết cục **khác nhau**: (1) 🔴 **bug thật** — `/inventory/balances` + `/inventory/lots*` báo `available > 0` cho lot `HOLD`/`REJECTED` vì `StockBalance.availableQuantity()` không đọc `lot.status`, trong khi aggregate của MRP/dashboard **có** lọc ⇒ một hệ thống hai con số; sửa ở `InventoryMapper.issuableQuantity` (**không** đụng domain method — 3 gate ghi tồn kho dựa vào nó). (2) 🟡 **gap thật** — `POST /planning-runs` bỏ qua `Idempotency-Key` (FE gửi 1 key 3 lần ⇒ 3 run); implement đầy đủ theo khuôn `stock_movements`, migration **`V58`**, header **tuỳ chọn**. (3) ✅ **báo nhầm** — "convert suggestion không atomic" **không có defect**, WO của họ sinh từ suggestion của **run khác** (đã `CONVERTED` đúng), suggestion họ nhắc chết vì trùng `workOrderNo` rồi rollback sạch; gốc rễ chính là (2). Bất biến **`B116`** (`module/inventory`), **`B117`** (`module/planning`). **Breaking change wire: có, hẹp** — `availableQuantity` của lot bị giữ nay là `0`. Bản ghi: **§0.44**, hướng dẫn FE: `FE_SingleTask_Response.md` |
| **Việc trước nữa** | **FE handoff: Inventory Dashboard API** ✅ **HOÀN THÀNH** (2026-08-14). Nguồn: `BACKEND_HANDOFF_DASHBOARD_API_REQUIREMENTS.md`. FE chuyển `/dashboard` từ mock sang API thật, cần `GET /reports/inventory-dashboard` trả đủ nhãn để **một** request là đủ (không N+1 sang Item/Warehouse/User). Thêm: `generatedAt`; `uomCode`/`onHandQuantity`/`reservedQuantity`/`qualityHoldQuantity`/`shortageQuantity` trên alert line; DTO mới `DashboardRecentMovementResponse` (item/warehouse label + `actorUsername` batch-resolve); `lowStockLimit`/`movementLimit` (mặc định 10, kẹp `[1,20]`); thứ tự `REORDER_NEEDED` → shortage desc → `itemCode` → `warehouseCode`, ledger tie-break `movementId desc`. 🔴 `shortageQuantity` dùng **`max(safetyStock, reorderPoint)`**, không phải reorder point một mình như FE đề xuất — repo cấu hình `safetyStock ≥ reorderPoint` nên công thức FE đề xuất báo `0` cho **mọi** dòng `LOW_STOCK` (đã giải thích cho FE). Không migration, không permission mới. **Breaking change wire: nhẹ** — `recentMovements[]` đổi sang DTO riêng, mất `idempotencyKey` (FE chưa dùng, còn ở mock). Bất biến **`B114`**, **`B115`** (`module/inventory`). Bản ghi: **§0.43**, hướng dẫn FE: `FE_SingleTask_Response.md` |
| **Bugfix mới nhất** | **Nợ #27: wire format ngày/giờ về ISO** ✅ **HOÀN THÀNH** (2026-08-12). FE yêu cầu tường minh (`ADMIN_RBAC_BACKEND_RESPONSE_REVIEW_2026-08-12.md §4`) **và** xác nhận adapter của họ nhận cả hai định dạng ⇒ điều kiện còn thiếu từ `§0.40` nay đã có. Xoá `@Bean ObjectMapper` trong `RedisConfig` — bean đó chỉ định dùng cho Redis nhưng làm `JacksonAutoConfiguration` của Boot nhường chỗ ⇒ **mọi** dòng `spring.jackson.*` chết lặng ⇒ `LocalDate` ra `[2026,8,8]`, `Instant` ra số epoch, ở **mọi** endpoint. 🔴 **Ghi đè `default-property-inclusion` `non_null` → `always`** (quyết định của user): để `non_null` có hiệu lực cùng lúc là xoá mọi field null khỏi mọi response — thay đổi payload toàn hệ thống không ai yêu cầu. Hệ quả phụ đã báo FE: field lạ trong request body nay bị bỏ qua thay vì 400. **Breaking change wire: CÓ** (nhưng là sửa sai — tài liệu hứa ISO từ đầu). Không migration, không permission mới. Bản ghi: **§0.42**, hướng dẫn FE: `FE_SingleTask_Response.md` |
| **Bugfix trước** | **Admin RBAC: đọc membership Role↔Permission / Scope↔Resource + unmapped path trả 500** ✅ **HOÀN THÀNH** (2026-08-12). FE báo qua `ADMIN_RBAC_BACKEND_API_CONTRACT_REQUEST.md`. **Ba khoản sửa:** (1) **blocker** — `GET /access/roles/{roleId}/permissions` **không tồn tại** ⇒ FE không có cách hợp lệ nào biết role đang giữ quyền nào, không dựng được checkbox (chỉ có catalog toàn cục); (2) `GET /access/scopes/{scopeId}/resources` cũng thiếu — `addScopeResource` từ trước tới nay **chỉ ghi được, không đọc được**; (3) 🔴 **lỗi toàn cục, không riêng RBAC:** request tới path **không** có handler trả **500 `INTERNAL_SERVER_ERROR`** (trace `bbad9f8c37824349`) — Spring 6.1 ném `NoResourceFoundException`, không handler nào bắt nên rơi xuống catch-all ⇒ URL gõ sai trông như backend sập và alert 5xx nổ oan. Đây là **đúng cùng một lỗ hổng** `§0.24` đã vá cho `MissingServletRequestParameterException`, sót lại một loại. Không migration, không permission mới, **không breaking change wire**. Bất biến **`B113`** (`module/organization`). Bản ghi: **§0.41**, hướng dẫn FE: `FE_SingleTask_Response.md` |
| **Bugfix trước đó** | **Sales Order full-replacement `PATCH` + version bump khi thay dòng** ✅ **HOÀN THÀNH** (2026-08-10). FE báo `PATCH /sales-orders/{id}` chỉ chạy được khi **không** kèm `lines[]`; kèm vào thì trả `Data constraint violation`. 🔴 **Hai bug thật, cả hai chỉ lộ trên Postgres thật — 977 case unit đều xanh:** (1) `clear()` + add lại `lineNo` 1..N trong **cùng một flush** ⇒ Hibernate xếp `INSERT` con **trước** `DELETE` orphan-removal ⇒ đụng `uk_sales_order_lines_order_line_no`; (2) phát hiện thêm lúc smoke test: `lines` là collection **inverse** (`mappedBy`) nên thay dòng **không** làm dirty header ⇒ PATCH chỉ có `lines` trả 200 mà `version` **đứng yên** ⇒ hai request thay dòng đồng thời đều qua check `expectedVersion` (**lost update**). Không migration, không breaking change wire. Bất biến **`B112`** (`module/sales`). Bản ghi: **§0.40**, hướng dẫn FE: `FE_SingleTask_Response.md` |
| **Phase trước** | **FE contract fix: Sales Order `version` + activate API (Plant/Warehouse/Item)** ✅ **HOÀN THÀNH** (2026-08-08). FE báo 2 điểm khi tích hợp `PATCH /sales-orders/{id}`: (1) endpoint đòi `expectedVersion` nhưng không response nào trả `version`; (2) Plant/Warehouse/Item chỉ có `DELETE` (deactivate), không có đường quay lại `ACTIVE`. Sửa #1: `SalesOrderResponse` thêm `version`. Sửa #2: `POST /plants/{id}/activate`, `/warehouses/{id}/activate`, `/items/{id}/activate` (mới; **không** làm cho `Company` — ngoài phạm vi FE hỏi), tái dùng permission có sẵn, **chặn nếu cha đang `INACTIVE`** (422, quyết định tường minh của user qua `AskUserQuestion`). 🔴 **Bug thật phát hiện lúc smoke test thủ công, không phải giả định:** thêm `version` vào response mới lộ ra `SalesOrderService.update`/`confirm`/`cancel` dùng `save()` (không flush) rồi map response ngay trong transaction ⇒ `version` trả về **cũ hơn** giá trị vừa thật sự persist, khiến client dùng nó làm `expectedVersion` lần sau sẽ luôn bị `409 CONCURRENT_MODIFICATION` giả. Sửa bằng `saveAndFlush` ở cả 3 method. Không migration. Bất biến `B108` (`module/organization`), `B109` (`module/inventory`), `B111` (`module/sales`). Bản ghi: **§0.39**, hướng dẫn FE: `docs/fe-guide-sales-order-version-and-activate.md` |
| **Phase trước đó** | **Bugfix P0 auth** ✅ **HOÀN THÀNH** (2026-08-06). FE báo 2 lỗi P0: (1) `/auth/refresh` trả `401 TOKEN_MALFORMED` dù `permitAll`; (2) access token hợp lệ dùng được ở `/auth/me` nhưng không dùng được ở endpoint khác. Nguyên nhân #1: `refresh()` bí mật phụ thuộc access token để resolve `userId` — sửa bằng reverse lookup `auth:refresh:owner:{tokenId}` (`TokenStoreService`), refresh giờ chỉ cần `{refreshToken, tokenId}`. `JwtAuthenticationFilter` thêm `BYPASS_PATHS` (login/refresh/forgot-password/reset-password — **không** gồm logout/logout-all, có lý do). Nguyên nhân #2: không tái hiện được từ code (đã loại trừ CORS) — rất có thể phía client; sửa được phần chẩn đoán sai: `JwtAuthEntryPoint` hardcode `TOKEN_MALFORMED` cho mọi request thiếu credential, nay dùng `AuthErrorCode.AUTHENTICATION_REQUIRED` mới, tách bạch "không gửi gì" khỏi "gửi nhưng hỏng". Không migration. Bất biến `B37` (`common/security`), `B107` (`module/auth`). Bản ghi: **§0.38** |
| **Phase `C2-2`** | **`C2-2` – Inventory Lot lifecycle API** ✅ **HOÀN THÀNH** (2026-08-06). `GET /inventory/lots`, `GET /inventory/lots/{lotId}`, `POST /inventory/lots/{lotId}/status`. Không migration. Bất biến `B102`-`B106`. Bản ghi: **§0.37** |
| **Phase `C2-1`** | **`C2-1` – Audit Logs read API** ✅ **HOÀN THÀNH** (2026-08-06). `GET /audit-logs`, `GET /audit-logs/{id}`. `PERM_AUDIT_READ` ADMIN-only. Migration `V54`+`V55`. Bản ghi: **§0.36** |
| **Phase `D8c`** | **`D8c` – Forgot-password / Account Recovery** ✅ **HOÀN THÀNH** (2026-08-06). Trả nốt 3/3 nợ #6. Không migration. Bất biến `B101`. Bản ghi: **§0.35** |
| **Phase `D8b`** | **`D8b` – Absolute Session Timeout** ✅ **HOÀN THÀNH** (2026-08-03). `SESSION_ABSOLUTE_TIMEOUT` (401) sau 30 ngày kể từ **login** + force logout mọi phiên; `sessionCreatedAt` lưu ở **companion key** `auth:refresh:{userId}:{tokenId}:meta`, **carry-forward** qua mỗi lần rotate. **Không migration** (thuần Redis), wire **additive**. Bất biến **`B81`**. Trả **2/3** nợ #6 — **`D8c` vẫn mở**. Bản ghi: §0.23 |
| **Phase `D8a`** | **`D8a` – Refresh Token Reuse Detection (RTR)** ✅ HOÀN THÀNH (2026-08-03). `TOKEN_REUSE_DETECTED` (401) + force logout **cả** refresh token **lẫn** device session; thứ tự rotate lưu-mới→mark-used→xoá-cũ. Không migration, wire additive. Bất biến **`B80`**. Giới hạn "race double-submit" mà phase này chấp nhận **đã đóng**, xem §0.32. Bản ghi: §0.22 |
| **Phase kế tiếp** | *(chưa chốt)* — track `C2-*` và `D8` đã đóng hết. 🔴 **`P*` thì KHÔNG**: lượt đối chiếu lại `TÀI LIỆU PHÂN TÍCH CHUYÊN SÂU (1).pdf` ngày **2026-09-25** mở thêm **`P7`–`P11`** — WIP Valuation + Variance snapshot (`P7`), Shop Floor Control cấp công đoạn (`P8`), Traceability toàn chuỗi/genealogy (`P9`), CRP đóng vòng vào MRP run (`P10`), MPS (`P11`). Ma trận đối chiếu từng yêu cầu PDF ↔ code: **`MANUFACTURING_GAP_ROADMAP.md §2.0`**; ba khoảng trống lớn nhất: **§2.0.1**. Dòng này trước đó ghi "`P*` đã đóng hết" — sai, vì chưa ai đối chiếu lại PDF kể từ `P6`. Xem `NEXT_PHASE_PLAN.md` để biết còn gì chưa làm. |
| **Migration mới nhất** | **`V90__create_production_cost_rates_and_baselines.sql`** + **`V91__create_production_cost_runs.sql`** + **`V92__create_production_cost_adjustments.sql`** (2026-10-27, `G11`) — thuần additive, không backfill (lệnh cũ không có baseline = `unknown`, đúng thiết kế). `MANUFACTURING_PHASE_PLAN_P7_P11.md` **đã** đánh số lại thành `V93`–`V97`. Trước đó: **`V89__create_customer_payments.sql`** (2026-10-27, `G10d`) — thuần additive: sequence + hai bảng, không backfill. Chạy sạch trên DB dev. `MANUFACTURING_PHASE_PLAN_P7_P11.md` **đã** đánh số lại thành `V90`–`V94` (2026-10-27). Trước đó: **`V88__create_sales_invoices.sql`** (2026-10-26, `G10c`) — thuần additive: sequence + hai bảng, không backfill. Chạy sạch trên DB dev. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V90`** (`G10d` dự kiến `V89`). Trước đó: **`V87__create_deliveries.sql`** (2026-10-25, `G10b`) — thuần additive: sequence + hai bảng mới, không backfill. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V90`** (`G10c`/`G10d` dự kiến `V88`/`V89`). Trước đó: **`V86__add_sales_order_prices_baseline_and_commercial_permissions.sql`** (2026-10-25, `G10a`) — thuần additive + một `UPDATE` (`delivery_baseline_known = FALSE` cho đơn khác `DRAFT`). 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V90`** (`G10b`–`G10d` dự kiến `V87`–`V89`). Trước đó: **`V85__create_production_quality_decisions.sql`** (2026-10-23, `G9c`) — hai cột tổng QC (DEFAULT 0) + CHECK `≥ 0` và `đạt + loại ≤ quantity` trên `production_receipt_lines`; bảng `production_quality_decisions` (CHECK lượng, loại ⇒ có lý do); backfill tổng + một dòng quyết định cho mọi phiếu có `qc_result` (lý do/khi/ai từ cột cũ, nêu chính lô, không dựng lô con). Chạy sạch trên DB dev có dữ liệu demo (6 phiếu đã QC ⇒ 6 dòng quyết định, 0 lệch). 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V86`**. Trước đó: **`V84__create_production_receipt_contributions.sql`** (2026-10-23, `G9b`) — thuần additive: bảng `production_receipt_contributions` (FK phiếu + lượt báo, `UNIQUE (receipt_id, production_execution_id)`, CHECK lượng > 0, index theo lượt báo); không backfill (phiếu cũ = không rõ nguồn). Chạy sạch trên DB dev có dữ liệu demo. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V86`** (G9c dự kiến `V85`). Trước đó: **`V83__extend_production_receipt_lifecycle.sql`** (2026-10-21, `G9a`) — 10 cột nullable trên `production_receipts`, `received_on` backfill `(COALESCE(approved_at, posted_at) AT TIME ZONE plant.timezone)::date` rồi `NOT NULL` (khuôn `V77`), `cancelled_at` backfill cho dòng `CANCELLED` cũ (V16) trước CHECK, CHECK `expires_on > received_on`, 2 quyền seed. Chạy sạch trên DB dev có dữ liệu demo. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V86`** (G9b/G9c dự kiến dùng `V84`/`V85`). Trước đó: **`V82__create_material_returns_and_wip_permissions.sql`** (2026-10-20, `G8d`) — thuần additive: hai bảng `material_returns` (`status` CHECK chỉ `POSTED`, `idempotency_key` UNIQUE) + `material_return_lines` (CHECK lượng > 0, FK dòng cấp + biến động kho NOT NULL); `chk_wip_transactions_type` viết lại **đủ** danh sách + `MATERIAL_RETURNED`; 3 quyền seed cho ADMIN + MANAGER. Chạy sạch trên DB dev có dữ liệu demo. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V83`**. Trước đó: **`V81__add_operation_input_ledger_retry_and_consumption.sql`** (2026-10-20, `G8c`) — thuần additive + một `UPDATE`: `operation_tracking` DEFAULT TRUE rồi `FALSE` cho lệnh đã có `production_executions` (không backfill đầu vào/vật tư dùng — Q22); CHECK `terminal_shortfall_quantity >= 0`; hai bảng sổ (CHECK lượng > 0, đầu vào không tự trỏ). Chạy sạch trên DB dev có dữ liệu demo (4 lệnh `FALSE`, 19 `TRUE`). 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V82`**. Trước đó: **`V80__add_material_issue_supplemental_and_cancellation.sql`** (2026-10-19, `G8b`) — thuần additive: `supplemental BOOLEAN NOT NULL DEFAULT FALSE` (mọi phiếu cũ là phiếu thường), `cancellation_reason TEXT` nullable. Chạy sạch trên DB dev có dữ liệu demo. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V81`**. Trước đó: **`V79__add_work_order_component_warehouses_and_committed_schedule.sql`** (2026-10-19, `G8a`) — thuần additive: `warehouse_id` nullable (không backfill), 4 cột lịch (`schedule_revision` NOT NULL DEFAULT 0 + CHECK ≥ 0), bảng idempotency `UNIQUE (key, command)`. Chạy sạch trên DB dev có dữ liệu demo. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V80`**. Trước đó: **`V78__add_purchase_quality_decisions.sql`** (2026-10-19, `G7c`) — thuần additive: 2 cột lượng QC (DEFAULT 0) + CHECK trên `goods_receipt_lines`; `parent_lot_id` (FK tự trỏ) + nguồn trên `inventory_lots`; `chk_stock_movements_type` viết lại **đủ** danh sách + `LOT_SPLIT_OUT`/`LOT_SPLIT_IN`; bảng `purchase_quality_decisions` (CHECK lượng > 0, loại ⇒ có lý do); 2 quyền seed. Chạy sạch trên DB dev có dữ liệu demo. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V79`**. Trước đó: **`V77__add_goods_receipt_drafts.sql`** (2026-10-18, `G7b`) — CHECK status thêm `DRAFT`; `posted_at`/`idempotency_key` bỏ NOT NULL + CHECK `POSTED ⇒ posted_at`; `received_date` backfill `(posted_at AT TIME ZONE plant.timezone)::date` rồi NOT NULL; `quality_held` DEFAULT FALSE (phiếu cũ không có hold để gỡ); `goods_receipt_lines.stock_movement_id` nullable + `expires_on`; sequence; 2 quyền seed. Chạy sạch trên DB dev (5 phiếu `POSTED` cũ). 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V79`** (`G7c` dùng `V78`). Trước đó: **`V76__extend_purchase_order_lifecycle.sql`** (2026-10-18, `G7a`) — gỡ `chk_purchase_orders_status` → đổi tên dữ liệu → thêm CHECK mới (thứ tự bắt buộc); cột mới trên `purchase_orders`/`purchase_order_lines`; sequence; bảng idempotency mua hàng; `released_*` trên ledger chuyển đổi. Chạy sạch trên DB dev có dữ liệu demo (2 `SENT` + 1 `RECEIVED` đổi đúng tên). 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V79`** (G7b/G7c dự kiến dùng `V77`/`V78`). Trước đó: **`V75__add_commercial_planning_runs_and_conversion_ledger.sql`** (2026-10-18, `G6b`) — 4 cột trên `mrp_runs` (`run_kind` DEFAULT `STANDARD`, phần còn lại nullable/0), bảng `supply_suggestion_conversions` (`uk_…_run_key_entry`, `chk_…_quantity > 0`), CHECK `chk_mrp_req_warehouse_resolution_source` viết lại đủ danh sách + `RUN_ITEM_MAPPING`. Chạy sạch trên DB dev có dữ liệu demo. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` đánh số lại từ **`V76`**. Trước đó: **`V74__link_sales_orders_to_customers_and_sales_order_reservations.sql`** (2026-10-17, `G6a`) — 4 cột nullable trên `sales_orders` (FK `customers`), sequence `sales_order_number_seq`, `material_reservations.work_order_id`/`component_line_id` **bỏ NOT NULL** + `sales_order_id`/`sales_order_line_id` + CHECK đúng một chủ. Chạy sạch trên DB dev có dữ liệu demo. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` phải **đánh số lại từ `V75`** (và `G6b` cũng dùng số kế tiếp). Trước đó: **`V73__add_bom_routing_versions_and_routing_predecessors.sql`** (2026-10-16, `G5`) — cột mới + backfill (không đổi `quantity_per` nào), `uk_bom_headers_company_bom_code` trên `UPPER(bom_code)`, `uk_bom_headers_company_parent_version_number`, `uk_routings_company_item_version_number`, bảng `routing_operation_predecessors` (`chk_..._not_self`). Chạy sạch trên DB dev có dữ liệu demo. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` phải **đánh số lại từ `V74`**. Trước đó: **`V72__create_stock_transfers.sql`** (2026-10-15, `G4`) — thuần additive cho dữ liệu cũ; `chk_stock_movements_type` viết lại **đủ** danh sách (giữ `REVERSAL`/`LOT_STATUS_CHANGE`) + `TRANSFER_OUT`/`TRANSFER_IN`; `chk_stock_transfers_distinct_warehouses`; `UNIQUE (idempotency_key)`/`(code)`. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` phải **đánh số lại từ `V73`**. Trước đó: **`V71__extend_import_runs_for_fixed_template_batches.sql`** (2026-10-14, `G3`) — thuần additive, `kind` DEFAULT `MAPPED_PROFILE` (đúng cho mọi dòng cũ), `CHECK` batch phải có owner, status thêm `READY`/`INVALID`/`COMMITTED`, unique **partial** `(owner_user_id, upload_idempotency_key)`. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` dự kiến `V69`–`V73` ⇒ **phải đánh số lại từ `V72`** trước khi làm P7. Trước đó: **`V69__create_customers_and_extend_suppliers.sql`** + **`V70__seed_customer_permissions.sql`** (2026-10-14, `G2`). `V69` thuần additive: bảng `customers` (`UNIQUE (company_id, code)`, index `(company_id, status)`) + 2 cột nullable trên `suppliers`. `V70` cấp quyền Customer bằng cách **đọc grant thật của `PERM_SUPPLIER_*` trong DB**, không chép danh sách role. 🔴 `MANUFACTURING_PHASE_PLAN_P7_P11.md` dự kiến `V69`–`V73` ⇒ **phải đánh số lại trước khi làm P7**. Trước đó: **`V67__create_audit_outbox_and_extend_audit_logs.sql`** + **`V68__enforce_audit_append_only.sql`** (2026-09-02, track `AR-*`). `V67` thuần additive: bảng `audit_outbox`, bảng `audit_log_entities`, 10 cột mới trên `audit_logs` (`event_id` + unique index, `company_id`, `warehouse_id`, `source`, `reason_code`, `http_method`, `request_path`, `occurred_at`, `metadata`, `payload_hash`), `trace_id` nới 32→64, 4 index mới. **Không backfill dòng lịch sử** — suy `plant_id` của một dòng cũ từ dữ liệu hôm nay là làm giả snapshot. `V68` cài trigger `audit_reject_mutation()` chặn UPDATE/DELETE trên 3 bảng audit, đường thoát duy nhất là GUC `audit.maintenance`. Trước đó: `V58__add_idempotency_to_mrp_runs.sql` (2026-08-14). `mrp_runs` + `idempotency_key`/`payload_hash` + `UNIQUE`, nullable không backfill — làm `POST /planning-runs` replay-safe (`B117`). Trước đó: `V57__separate_item_master_permissions.sql` (FE-4 5C) |
| **Baseline test** | 180 case / 44 class → T0+T1: 218 → T3: 257 → T2/T4/T5: 281 case / 57 class → F1: 289 → F2: 303 → F3: 320 → F4: 347 → F5-A: 356 → F5-B: 365 → F6: 391 → D1: 396 → D9+D10: 402 → D4: 408 → D5: 412 → D6: 416 → D7: 450 → D7b: 510 → D11: 516 → F7: 521 → F8: 545 → F9: 549 → F10: 556 case unit + 59 case IT / 10 class IT → `GET /auth/me` (2026-08-01): 571 case unit + 66 case IT → `D8a` (2026-08-03): 577 case unit → `D8b` (2026-08-03): 586 case unit + 66 case IT / 10 class IT → bugfix `lower(bytea)` + missing-param 500 (2026-08-04): 587 case unit + 70 case IT / 11 class IT → `C2-5` (2026-08-04): 593 case unit + 73 case IT / 11 class IT → `C2-3` (2026-08-04): 617 case unit + 78 case IT / 12 class IT → `C2-4` (2026-08-05): 661 case unit + 78 case IT / 12 class IT → `C2-6` (2026-08-05): 690 case unit + 79 case IT / 12 class IT → `C2-7` (2026-08-05): 759 case unit + 80 case IT / 12 class IT → `C2-8` (2026-08-05): 791 case unit + 87 case IT / 13 class IT → `P3` (2026-08-05): 821 case unit + 88 case IT / 13 class IT → concurrent refresh-token race (2026-08-05): 831 case unit + 88 case IT / 13 class IT → `P5` (2026-08-06): 850 case unit + 89 case IT / 13 class IT → `P6` (2026-08-06): 864 case unit + 89 case IT / 13 class IT → `D8c` (2026-08-06): 885 case unit + 89 case IT / 13 class IT (`+21` unit: `PasswordResetTokenServiceTest` +6 (mới), `AuthServiceTest` +5, `AuthMethodSecurityTest` +2 (mới), `AuthControllerTest` +5, `AdminControllerTest` +1 (mới), `SensitiveRequestToStringTest` +2; `+0` IT — không migration, không JPQL mới) → `C2-1` (2026-08-06): 898 case unit + 98 case IT / 14 class IT (`+13` unit: `AuditLogQueryServiceTest` +5 (mới), `AuditLogMethodSecurityTest` +4 (mới), `AuditLogControllerTest` +4 (mới); `+9` IT từ `AuditLogRepositoryIT` (mới, **class IT thứ 14**) + `+1` case `FlywayMigrationIT.migrate_v55_grantsAuditReadToAdminOnly`) → `C2-2` (2026-08-06): 925 case unit + 105 case IT / 14 class IT (`+27` unit: `InventoryLotServiceTest` +9 (mới), `LotQcOriginLookupServiceTest` +3 (mới), `InventoryLotMethodSecurityTest` +6 (mới), `InventoryLotControllerTest` +7 (mới), `InventoryPermissionGuardTest` +2; `+7` IT trong `StockBalanceRepositoryIT` đã có, **không** thêm class IT mới) → bugfix P0 auth (2026-08-06): 942 case unit + 105 case IT / 14 class IT, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker (`+17` unit: `JwtAuthenticationFilterTest` +13 (mới), `JwtAuthEntryPointTest` +1 (mới), `TokenStoreServiceTest` +3, `AuthServiceTest` +2 ròng (thêm 2 case mới, 1 case đổi tên/viết lại — không xoá), `JwtTokenProviderTest` −2 (xoá 2 case `extractClaimsFromExpired`, method đã orphan); `+0` IT — không đụng repository/JPQL nào) → **FE contract fix: Sales Order version + activate API (2026-08-08): 964 case unit + 105 case IT / 14 class IT**, failures = 0, errors = 0 (`+22` unit: `OrganizationServiceTest` +6, `OrganizationMethodSecurityTest` +4, `OrganizationControllerTest` +4, `ItemServiceTest` +3, `ItemMethodSecurityTest` +2 (mới), `ItemControllerTest` +2, `SalesOrderServiceTest` +1 (`update_persistsThroughSaveAndFlush_...`, pin đúng `saveAndFlush`); `+0` IT — không migration, không JPQL mới) → FE-4 5C Item Master permission (`V57`): 977 case unit → **bugfix Sales Order full-replacement PATCH (2026-08-10): 979 case unit + 110 case IT / 15 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker (`+2` unit: `SalesOrderServiceTest` +2 (`update_replacingLines_flushesTheOrphanDeletesBeforeBuildingTheReplacements` dùng `InOrder`, `update_replacementLineFromAnotherCompany_flushesTheDeletesButNeverCommits`); `+4` IT từ `SalesOrderUpdateLinesIT` (**class IT thứ 15**, mới) — 🔴 đây là loại bug mà **chỉ** `*IT` bắt được, xem `§0.40`) → **fix contract Admin RBAC (2026-08-12): 994 case unit**, failures = 0, errors = 0 — đo bằng `mvn -o test` (`+15` unit: `AccessControlServiceTest` +4, `AccessControlMethodSecurityTest` +4, `AccessControlControllerTest` +5, `GlobalExceptionHandlerTest` +2). Phần IT của lần đó lúc nghiệm thu **chưa đo được** (Docker Desktop tắt giữa chừng, mọi class IT lỗi như nhau) — ✅ **đã đo bù cùng ngày**, xem mốc kế tiếp → **trả nợ #27 wire format ISO (2026-08-12): 997 case unit + 114 case IT / 16 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker (`+3` unit: `JsonWireFormatTest` +3 (mới); `+4` IT là của `RolePermissionRepositoryIT` (**class IT thứ 16**) từ lượt trước, nay mới chạy được thật — bản thân lượt này không thêm case IT nào, không đụng repository/JPQL) → **FE báo Assignment `expiresAt` mất (2026-08-13): 998 case unit + 115 case IT / 16 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker. 🔴 **Không sửa `src/main`** — chẩn đoán ra là cùng lỗi `§0.42`, môi trường FE test chưa chạy bản sửa (`+1` unit: `AccessControlControllerTest`; `+1` IT: `UserRoleAssignmentRepositoryIT`; `AccessControlServiceTest.assignRole_success` **sửa tại chỗ** theo `R10` — trước đó truyền `expiresAt` mà không assert gì về nó, xem `§0.42a`) → **FE handoff Dashboard API (2026-08-14): 1009 case unit + 119 case IT / 17 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker (`+11` unit: `InventoryAlertServiceTest` +7, `InventoryReportControllerTest` +3, `InventoryAvailabilityServiceTest` +1 ròng — 1 case cũ **sửa tại chỗ** theo `R10` khi `getAvailableQuantitiesByWarehouse` đổi thành `getStockQuantitiesByWarehouse`; `+4` IT: `StockMovementRepositoryIT` (**class IT thứ 17**, mới) 3 case + `StockBalanceRepositoryIT` +1) → **trả lời `live-data-audit.md` (2026-08-14): 1025 case unit + 120 case IT / 17 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker (`+16` unit: `InventoryMapperTest` +9 (mới), `InventoryLotServiceTest` +1, `MrpRunServiceTest` +4, `PlanningRunControllerTest` +2; `+1` IT: `FlywayMigrationIT.migrate_v58_*`, **không** thêm class IT mới. ⚠️ Lần chạy đầu **mọi** class IT lỗi vì Docker Desktop tắt giữa chừng — bẫy `§0.41`, phải bật lại rồi đo lại) → **bộ dữ liệu demo + sửa lỗi `MultipleBagFetchException` (2026-08-14): 1025 case unit / 127 class + 124 case IT / 18 class IT**, failures = 0, errors = 0 (`+0` unit — lượt này **không** thêm case unit nào, có chủ đích: lỗi nằm trong `@EntityGraph` nên unit test dùng mock repository không bao giờ dựng được câu query để bắt, xem `§0.45` hệ quả #2; `+4` IT từ `WorkCalendarLookupServiceIT`, **class IT thứ 18**, mới) → **track `EH-*` (2026-08-23): 1181 case unit / 152 class + 142 case IT / 21 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker. Baseline ngay trước track này là **1155 unit / 147 class + 140 IT / 20 class** (số của các lượt sau `§0.45` chưa từng được ghi vào hàng này). `+26` unit: `RateLimitFilterErrorEnvelopeTest` +4 (mới), `DataIntegrityErrorMapperTest` +8 (mới), `ApiErrorControllerTest` +3 (mới), `OrganizationLookupServiceTest` +4 (do nghiệm thu mutation phát hiện lỗ hổng, xem §0.47 hệ quả #5), `GlobalExceptionHandlerTest` +3, `MrpRunServiceTest` +2, `JwtAuthenticationFilterTest` +1, `BomServiceTest` +1; `+2` IT từ `MrpRunStateRecorderIT` (**class IT thứ 21**, mới). 🔴 **23 assertion cũ đổi theo `R10`, không xóa cái nào** — và đó chỉ là 23/59 throw site bị đổi mã, xem §0.47 hệ quả #4 → **contract giá trị Audit Log (2026-08-25): 1184 case unit / 152 class + 142 case IT / 21 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker. `+3` unit: `AuditLogQueryServiceTest` +2 (mọi hình dạng `jsonb` → `String`/`null`; SQL `NULL` không thành chuỗi `"null"`), `AuditLogControllerTest` +1 (`isString()` ở tầng HTTP — `value(...)` một mình **cũng xanh** cho một object node, nên assertion kiểu mới là thứ khoá thật). `+0` IT — không đụng repository/JPQL/schema nào. 3 assertion cũ **sửa tại chỗ** theo `R10` (bỏ `.asText()`), không xoá case nào → **track `AR-*` (2026-09-02): 1225 case unit / 158 class + 154 case IT / 22 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker. Baseline ngay trước track này: **1184 unit / 152 class + 142 IT / 21 class** (đo lại thật, khớp hàng cũ). `+41` unit / `+6` class: `AuditInputSanitizerTest` 11 (mới), `AuditRecorderTest` 8 (mới), `AuditExpressionEvaluatorTest` 7 (mới), `RbacAuditDescriptorProviderTest` 5 (mới), `AuditOutboxPropertiesTest` 4 (mới), `AuditPayloadCodecTest` 3 (mới), `AuditableAspectTest` +3 ròng (1→4, **viết lại** theo `R10`). `+12` IT / `+1` class: `AuditPipelineIT` 10 (**class IT thứ 22**, mới) + `FlywayMigrationIT` +2 (`migrate_v67_*`, `migrate_v68_*`). 🔴 **4 assertion cũ sửa tại chỗ, không xoá case nào**: `FlywayMigrationIT` pin version `66`→`68`, `AuditLogControllerTest` ×2 đổi `entityId` từ `doesNotExist()` sang **có giá trị** (AR-6 khôi phục field đó — assertion cũ pin đúng cái gap, nay pin cái fix), `AuditChangeCaptureServiceTest` + `AuditLogQueryServiceTest`/`AuditLogMethodSecurityTest` sửa constructor/chữ ký → **`G2` (2026-10-14): 1320 case unit / 162 class + 184 case IT / 27 class IT**, failures = 0, errors = 0 — đo bằng `mvn -o clean verify` thật với Docker, chạy lại **sau** thay đổi `src/main` cuối cùng. Baseline đo lại trước phase: 1225 + 154 (khớp). `+95` unit / `+4` class, `+30` IT / `+5` class IT: `CustomerRepositoryIT`, `ItemWarehouseSettingRepositoryIT`, **`ItemWarehouseSettingConcurrencyIT`** (2 case × 5 vòng, `NOT_SUPPORTED` + `TransactionTemplate`), `OrganizationSearchRepositoryIT`, `ItemRepositoryIT` (**class IT thứ 23–27**) + `FlywayMigrationIT` +2. Test cũ **sửa** theo `R10` (pin version `68`→`70`, fixture method-security có warehouse/plant, constructor đổi), không xoá case nào → **`G2` A10 — số lẻ cho field số nguyên (2026-10-14): 1325 case unit / 162 class + 184 case IT / 27 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker. `+5` unit, không thêm class: `JsonWireFormatTest` +1 (đọc `application.yml` thật), `GlobalExceptionHandlerTest` +3 (field phẳng, field lồng `lines[0].lineNo`, không echo giá trị), `ItemWarehouseSettingControllerTest` +1 (A10 + `verifyNoInteractions`). `malformedJsonBody_*` **thêm** một assertion `errors` `doesNotExist()` theo `R10` → **`G3` (2026-10-14): 1394 case unit / 169 class + 192 case IT / 28 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker, chạy **sau** thay đổi `src/main` cuối cùng. Baseline đo lại trước phase: 1325 + 184 (khớp). `+69` unit / `+7` class (`FixedTemplateReaderTest` 11, `FixedTemplateValidatorTest` 10, `DataImportBatchControllerTest` 12, `DataImportBatchMethodSecurityTest` 8, `DataImportBatchCommitterTest` 10, `DataImportBatchServiceTest` 12, `PartnerImportTargetHandlerTest` 4, `GlobalExceptionHandlerTest` +2); `+8` IT / `+1` class (`DataImportBatchIT` 6, **class IT thứ 28**; `FlywayMigrationIT` +1; `ImportRunRepositoryIT` +1). Test cũ **sửa** theo `R10` (pin version `70`→`71`, constructor `DataImportProperties`), không xoá case nào → **`G4` (2026-10-15): 1461 case unit / 173 class + 202 case IT / 29 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker. 🔴 Baseline **phải đo lại trên một `git worktree` sạch của HEAD** (1394 + 192, khớp): lần đo đầu trong repo lỗi 636 case vì VS Code Java LS build lại `target/classes` giữa chừng khi file nguồn bị sửa (`NoClassDefFoundError: CompanyRepository`) — **không sửa file trong lúc `mvn` đang chạy**. `+67` unit / `+4` class (`StockTransferServiceTest` 14, `StockTransferMethodSecurityTest` 7, `StockTransferControllerTest` 8, `InventoryReservationQueryServiceMethodSecurityTest` 2, cộng case mới ở `InventoryMovementServiceTest`/`InventoryLotServiceTest`/`InventoryPermissionGuardTest`/`InventoryMethodSecurityTest`/`InventoryControllerTest`/`InventoryMapperTest`); `+10` IT / `+1` class (`StockTransferIT` 4, **class IT thứ 29**; `StockBalanceRepositoryIT` +4; `MaterialReservationRepositoryIT` +1; `FlywayMigrationIT` +1). Test cũ **sửa** theo `R10` (pin version `71`→`72`, constructor, guard lot status mới, `aggregateBalances` +2 tham số), không xoá case nào → **`G5` (2026-10-16): 1548 case unit / 179 class + 215 case IT / 32 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker; baseline đo lại trên `git worktree` sạch (1461 + 202, khớp). `+87` unit / `+6` class (`BomDraftServiceTest` 15, `RoutingDraftServiceTest` 9, `CapacityPreviewServiceTest` 7, `CapacityPermissionGuardTest` 8, `WorkCalendarLookupServiceTest` 5, `BomImportHandlerTest` 10, cộng case mới ở test có sẵn); `+13` IT / `+3` class (`BomDraftIT` 4, `RoutingDraftIT` 3, `ShiftCalendarReferenceIT` 2 — **class IT thứ 30–32**; `FlywayMigrationIT` +2; `DataImportBatchIT` +1; `WorkOrderOperationRepositoryIT` +1 ròng). Test cũ **sửa** theo `R10` (điều chỉnh chồng lịch 200→409, bảng công suất chia theo ngày, pin version `72`→`73`, fixture BOM/routing thêm cột NOT NULL), không xoá case nào → **`G6a` (2026-10-17): 1583 case unit / 184 class + 224 case IT / 33 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 1548 + 215). `+35` unit / `+5` class (`SalesOrderStockReservationServiceTest` 10, `SalesOrderReservationServiceTest` 5, `SalesOrderDraftServiceTest` 11, `SalesOrderReservationMethodSecurityTest` 4, cộng case mới ở `SalesOrderControllerTest`); `+9` IT / `+1` class (`SalesOrderReservationIT` 4 — **class IT thứ 33**; `MaterialReservationRepositoryIT` +4; `FlywayMigrationIT` +1). Test cũ **sửa** theo `R10` (constructor/chữ ký `SalesOrderService`, list → `searchExtended`, pin version `73`→`74`), không xoá case nào → **`G6b` (2026-10-18): 1621 case unit / 186 class + 230 case IT / 34 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 1583 + 224). `+38` unit / `+2` class (`PlanningConversionServiceTest` 14, `CommercialPlanningRunServiceTest` 9, `PlanningConversionMethodSecurityTest` 5, `MrpCalculationServiceTest` +3, `PlanningRunControllerTest` +7); `+6` IT / `+1` class (`PlanningConversionRepositoryIT` 5 — **class IT thứ 34**; `FlywayMigrationIT` +1). Test cũ **sửa** theo `R10` (constructor `MrpRunService`, pin version `74`→`75`), không xoá case nào → **`G6c` (2026-10-18): 1634 case unit / 187 class + 233 case IT / 34 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 1621 + 230). `+13` unit / `+1` class (`SalesOrderImportHandlerTest` 12, `DataImportBatchControllerTest` +1); `+3` IT trong `DataImportBatchIT` (không thêm class IT). Test cũ **sửa** theo `R10` (constructor `FixedTemplateValidator`/`DataImportBatchCommitter`, `DataImportBatchResponse` +1 component, `searchBatches` +1 tham số), không xoá case nào → **`G7a` (2026-10-18): 1686 case unit / 189 class + 243 case IT / 34 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker; baseline đo lại trên `git worktree` sạch (1634 + 233, khớp). `+52` unit / `+2` class (`PurchaseOrderServiceTest` +27, `PurchaseOrderControllerTest` +12, `PurchaseOrderMethodSecurityTest` +6, `PurchaseOrderPlanningLinkServiceTest` 4, `MrpPlanningMapperConvertedQuantityTest` 3, `GoodsReceiptServiceTest` +1); `+10` IT trong class có sẵn (`PurchaseOrderRepositoryIT` +6, `PlanningConversionRepositoryIT` +3, `FlywayMigrationIT` +1). Test cũ **sửa** theo `R10` (đổi tên hằng trạng thái, `save`→`saveAndFlush`, chữ ký `cancel`/`list`/`search`, constructor `PurchaseOrderService`, projection ledger +`getRowCount`, pin version `75`→`76`), không xoá case nào → **`G7b` (2026-10-18): 1721 case unit / 191 class + 249 case IT / 35 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 1686 + 243). `+35` unit / `+2` class (`GoodsReceiptDraftServiceTest` 15, `GoodsReceiptDraftMethodSecurityTest` 6, `GoodsReceiptServiceTest` +7, `GoodsReceiptControllerTest` +5, `PurchaseOrderServiceTest` +1, `GoodsReceiptMethodSecurityTest` +1); `+6` IT / `+1` class (`GoodsReceiptPostingIT` 5 — **class IT thứ 35**; `FlywayMigrationIT` +1). Test cũ **sửa** theo `R10` (constructor `GoodsReceiptService`/`PurchaseOrderService`, `cancel` 3 tham số, `save`→`saveAndFlush`, pin version `76`→`77`), không xoá case nào → **`G7c` (2026-10-19): 1749 case unit / 194 class + 253 case IT / 36 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 1721 + 249). `+28` unit / `+3` class (`PurchaseQualityServiceTest` 13, `PurchaseQualityMethodSecurityTest` 5, `PurchaseQcControllerTest` 3, `InventoryMovementServiceTest` +4, `InventoryLotServiceTest` +2, `GoodsReceiptServiceTest` +1); `+4` IT / `+1` class (`PurchaseQualityIT` 3 — **class IT thứ 36**; `FlywayMigrationIT` +1). Test cũ **sửa** theo `R10` (constructor `InventoryLotService`, `InventoryLotDetailResponse` +1 component, assertion dòng phiếu sau post, pin version `77`→`78`), không xoá case nào → **`G8a` (2026-10-19): 1785 case unit + 256 case IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker; baseline đo lại trên `git worktree` sạch (1749 + 253, **1 IT đỏ** = flake `migrate_v78_*` có từ G7c, sửa trong phase). `+36` unit (`WorkOrderServiceTest` +14, `MaterialReservationServiceTest` +6, `WorkOrderSchedulePreviewServiceTest` 4 mới, `WorkOrderControllerTest` +4, `WorkOrderMethodSecurityTest` +3, `WorkCalendarLookupServiceTest` +2, `WorkOrderPlanningLinkServiceTest` 2 mới, `ManufacturingExecutionMethodSecurityTest` +1); `+3` IT trong class có sẵn (`WorkOrderRepositoryIT` +2, `FlywayMigrationIT` +1). Test cũ **sửa** theo `R10` (chữ ký có version/key, `save`→`saveAndFlush`, constructor, `ProductionFlowE2EIT` gửi version, pin `78`→`79`), không xoá case nào → **`G8b` (2026-10-19): 1816 case unit / 198 class + 258 case IT / 36 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker, chạy lại **sau** bản sửa +2 version (baseline 1785 + 256). `+31` unit (`MaterialIssueServiceTest` +16, `ManufacturingExecutionControllerTest` +5, `WorkOrderPermissionGuardTest` 4 mới, `ManufacturingExecutionMethodSecurityTest` +3, `MaterialIssueCandidateServiceTest` 2 mới, `WorkOrderServiceTest` +1); `+2` IT trong class có sẵn (`MaterialIssueRepositoryIT` +1, `FlywayMigrationIT` +1) + assertion version mới trong `ProductionFlowE2EIT`. Test cũ **sửa** theo `R10` (constructor `MaterialIssueService`, body duyệt/từ chối có version, `listByPlant`/`findByPlant` +3 tham số, `save`→`saveAndFlush`, pin `79`→`80`), không xoá case nào → **`G8c` (2026-10-20): 1871 case unit / 199 class + 263 case IT / 37 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker, chạy lại **sau** thay đổi `src/main` cuối cùng (baseline 1816 + 258). `+55` unit / `+1` class (`OperationServiceTest` 26 mới, `ProductionExecutionServiceTest` +17, `ManufacturingExecutionMethodSecurityTest` +6, `ManufacturingExecutionControllerTest` +6); `+5` IT / `+1` class (`OperationLedgerRepositoryIT` 4 — **class IT thứ 37**; `FlywayMigrationIT` +1). Test cũ **sửa** theo `R10` (constructor `ProductionExecutionService`, body báo sản lượng có version + công đoạn, `save`→`saveAndFlush`, `ProductionFlowE2EIT` xác nhận đầu vào trước khi báo, pin `80`→`81`), không xoá case nào → **`G8d` (2026-10-20): 1913 case unit / 203 class + 269 case IT / 38 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 1871 + 263). `+42` unit / `+4` class (`MaterialReturnServiceTest` 16, `WorkInProgressMethodSecurityTest` 9, `WorkInProgressControllerTest` 8, `WorkOrderWipServiceTest` 6, `WorkOrderPermissionGuardTest` +2, `ProductionExecutionServiceTest` +1); `+6` IT / `+1` class (`MaterialReturnRepositoryIT` 5 — **class IT thứ 38**; `FlywayMigrationIT` +1). Test cũ **sửa** theo `R10` (constructor `ProductionExecutionService`/`WorkOrderPermissionGuard`, `ProductionFlowE2EIT` hoàn 4 kg + số biến động 3→4, pin `81`→`82`), không xoá case nào → **`G9a` (2026-10-21): 1968 case unit / 204 class + 274 case IT / 38 class IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker, chạy lại **sau** thay đổi `src/main` cuối cùng; baseline đo lại trên `git worktree` sạch (1913 + 269, khớp). `+55` unit / `+1` class (`ProductionReceiptServiceTest` +35, `ReceiptQuotaTest` 5 mới, `ManufacturingExecutionControllerTest` +9, `ManufacturingExecutionMethodSecurityTest` +6); `+5` IT trong class có sẵn (`ProductionReceiptRepositoryIT` +3, `WorkOrderRepositoryIT` +1, `FlywayMigrationIT` +1) + assertion version +1 trong `ProductionFlowE2EIT`. Test cũ **sửa** theo `R10` (chữ ký submit/approve/reject có body + key, `findByPlant`/`findReceiptCandidates` thêm tham số, `save`→`saveAndFlush`, fixture có version + người tạo + `receivedOn`, pin `82`→`83`), không xoá case nào → **`G9b` (2026-10-23): 1982 case unit + 279 case IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 1968 + 274). `+14` unit (`ProductionReceiptServiceTest` +11, `ManufacturingExecutionControllerTest` +2, `ManufacturingExecutionMethodSecurityTest` +1); `+5` IT trong class có sẵn (`ProductionReceiptRepositoryIT` +4, `FlywayMigrationIT` +1) + assertion phân bổ/nguồn trong `ProductionFlowE2EIT`. Test cũ **sửa** theo `R10` (constructor `ProductionReceiptService` +3, `ProductionReceiptResponse` +3 component, pin `83`→`84`), không xoá case nào → **`G9c` (2026-10-23): 2009 case unit + 283 case IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 1982 + 279). `+27` unit (`ProductionReceiptServiceTest` +16, `ProductionQualityStateTest` 4 mới, `ManufacturingExecutionMethodSecurityTest` +3, `ManufacturingExecutionControllerTest` +3, `InventoryLotServiceTest` +1); `+4` IT trong class có sẵn (`ProductionReceiptRepositoryIT` +3, `FlywayMigrationIT` +1) + assertion sổ QC trong `ProductionFlowE2EIT`. Test cũ **sửa** theo `R10` (`LotQcOriginLookupServiceTest` viết lại cho nghĩa "còn phần chờ", 3 case giữ nguyên số; hai fixture "đã QC" mang tổng trên dòng; `findByPlant`/`listByPlant` +3 tham số; constructor +1; pin `84`→`85`), không xoá case nào → **`G10a` (2026-10-25): 2032 case unit + 284 case IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 2009 + 283). `+23` unit (`SalesOrderPricingServiceTest` 8 mới, `SalesOrderCommercialShapeTest` 7 mới, `SalesOrderPricingMethodSecurityTest` 2 mới, `SalesOrderDraftServiceTest` +3, `SalesOrderControllerTest` +2, `SalesOrderServiceTest` +1); `+1` IT (`FlywayMigrationIT.migrate_v86_*`). Test cũ **sửa** theo `R10` (confirm có khách master, constructor assembler +1, `ProductionFlowE2EIT` tạo khách master, pin `85`→`86`), không xoá case nào → **`G10b` (2026-10-25): 2053 case unit + 294 case IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker, chạy **sau** bản sửa version +2 (baseline 2032 + 284). `+21` unit (`DeliverySupportTest` 8 mới, `DeliveryMethodSecurityTest` 6 mới, `DeliveryControllerTest` 5 mới, `SalesOrderServiceTest` +2); `+10` IT (`DeliveryPostingIT` 10 — **class IT mới**, gồm hai ca tranh chấp thật). Test cũ **sửa** theo `R10` (constructor assembler/planning source/`SalesOrderService` +`DeliveryLookupService`, pin `86`→`87`), không xoá case nào → **`G10c` (2026-10-26): 2068 case unit + 303 case IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 2053 + 294). `+15` unit (`InvoicePricingTest` 4, `SalesInvoiceMethodSecurityTest` 6, `SalesInvoiceControllerTest` 5 — đều mới); `+9` IT (`SalesInvoiceIT` 9 — **class IT mới**, gồm một ca tranh chấp thật). Pin `87`→`88`, không xoá case nào → **`G10d` (2026-10-27): 2085 case unit + 312 case IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 2068 + 303). `+17` unit (`CustomerPaymentMethodSecurityTest` 6, `CustomerPaymentControllerTest` 4, `FulfillmentSummaryServiceTest` 3, `PaymentStateTest` 2, `DeliveryRelatedTest` 2 — đều mới); `+9` IT (`CustomerPaymentIT` 8 — **class IT mới**, gồm một ca tranh chấp thật; `ProductionReceiptRepositoryIT` +1). Test cũ **sửa** theo `R10` (constructor `SalesInvoiceQueryService`/`DeliveryQueryService` +1, `SalesInvoiceControllerTest` +2 `@MockBean`, pin `88`→`89`), không xoá case nào → **`G11` (2026-10-27): 2141 case unit + 315 case IT**, failures = 0, errors = 0 — `mvn -o clean verify` thật với Docker (baseline 2085 + 312). `+56` unit (`ProductionCostCalculatorTest` 18, `ProductionCostMethodSecurityTest` 10, `ProductionCostControllerTest` 9, `ProductionCostCloseServiceTest` 7, `ProductionCostRateServiceTest` 6, `ProductionCostSourceReaderTest` 4, `WorkOrderServiceTest` +2); `+3` IT (`ProductionFlowE2EIT.productionCost_*` — hai lệnh chốt song song thật; `FlywayMigrationIT` +2). Test cũ **sửa** theo `R10` (constructor `WorkOrderService` +1, pin `89`→`92`), không xoá case nào |
| **Coverage tool** | ✅ JaCoCo 0.8.12 — **unit một mình: line 74.3% / branch 59.5%**; **unit + IT: line 80.5% / branch 64.2%** (cả hai đo lại 2026-08-03 sau `D8b`; số unit+IT trước đó 80.1% / 63.9% là của `F10`). ⚠️ **Xu hướng đã xác nhận tám phase liên tiếp:** `D7` +34 case ⇒ +0.6 line; `D7b` +60 ⇒ +0.8; `D11` +6 ⇒ +0.0 / +0.2; `F7` +12 ⇒ +0.2 / +0.2; `F8` +40 ⇒ +0.5 / +0.4; `F9` +8 ⇒ +0.1 / +0.0; `F10` +7 unit / +2 IT ⇒ +0.0 / +0.4; **`D8b` +9 unit ⇒ +0.1 line / +0.1 branch** (unit một mình). 🔴 **Số ở hàng này là số đo sau `D8b`; `§0.24` (bugfix), `§0.25` (`C2-5`), `§0.26` (`C2-3`), `§0.27` (`C2-4`), `§0.28` (`C2-6`), `§0.29` (`C2-7`), `§0.30` (`C2-8`), `§0.31` (`P3`), `§0.32` (concurrent refresh-token race), `§0.33` (`P5`), `§0.34` (`P6`), `§0.35` (`D8c`), `§0.36` (`C2-1`), `§0.37` (`C2-2`), `§0.38` (bugfix P0 auth), FE contract fix Sales Order version + activate API (2026-08-08) KHÔNG đo lại** — đừng đọc nó như đã tính các phần đó. **Coverage không đo được contract** — thước đo thật là nghiệm thu mutation (§0.15–§0.27). `C2-5` là ví dụ thêm: `PermissionCatalogTest` phủ 100% đường permission mà **không** thấy 12 quyền chỉ `ADMIN` có, vì "tồn tại" và "được cấp cho role" là hai sự thật khác nhau. `D8b` là ví dụ sắc nhất tới nay: mutation #1 của nó (carry-forward stamp `now`) **giữ nguyên 100% coverage, response byte-identical, mã lỗi và HTTP status không đổi** — tính năng thành no-op hoàn toàn mà mọi thước đo trừ assertion đối số đều báo xanh. Xem cảnh báo cách đo ngay dưới bảng |
| **Bảng theo dõi phase** | Nghiệp vụ `P*`: `MANUFACTURING_GAP_ROADMAP.md §2.1` (**`P1`–`P6` xong, `P7`–`P11` mở 2026-09-25**; ma trận đối chiếu PDF lý thuyết ở **§2.0**) · Kiểm thử `T*`: `TEST_IMPROVEMENT_PLAN.md §0` (xong hết) · Căn chỉnh FE `F*`: `FRONTEND_ALIGNMENT_ROADMAP.md §1` (lịch sử, đã đóng ở `F10`) · Trả nợ `D*`: cùng file **§6** · **Capstone 2 `C2-*` (đã đóng hết, `C2-1`+`C2-2` xong 2026-08-06): cùng file §8** — bảng phase §8.1, bẫy từng phase §8.8 · `NEXT_PHASE_PLAN.md` (roadmap) |

> **Track `F*` là gì:** `OmniPlant_MVP_Production_Backend_Handoff.docx` là đặc tả tích hợp viết
> **ngược từ frontend đã implement**, nên field name + business rule là phần cố định, tên endpoint
> là đề xuất. Đối chiếu cho thấy repo thiếu **3 module** (Sales Order, Routing, QC) và có **1 đảo
> ngược ngữ nghĩa** (xem §0.5). `F*` gồm 6 phase; `F2`/`F4`/`F6` trùng nội dung với `P2`/`P4`/`P6`
> của `MANUFACTURING_GAP_ROADMAP.md`, chỉ sắp lại thứ tự theo dependency của spec.
>
> **3 quyết định đã chốt với user (2026-07-26):**
> 1. **Giữ `UUID`** — spec ghi `id: number`, FE đổi TS type sang `string`. Không đụng schema/RBAC.
> 2. **Làm đủ 3 module thiếu**, chia phase tuần tự `F1`→`F6`.
> 3. **Sửa tại chỗ trên `/api/v1`**, không mở `/api/v2` — miễn trừ có chủ đích rule `A1`/`C-A1`
>    vì chưa có client production nào ngoài chính FE này. Mỗi phase phải ghi "Breaking Changes"
>    vào `NEXT_PHASE_PLAN.md` và **sửa** test cũ theo `R10` (không xoá).

> ⚠️ **Hai cái bẫy khi đo lại số liệu — cả hai đều từng làm sai số đã ghi (phát hiện ở `D5`):**
>
> 1. **Đếm test case:** lấy số ở dòng tổng `Tests run:` của `mvn -o verify`, **đừng** cộng file XML
>    trong `target/surefire-reports/` — thư mục đó giữ lại report của class đã **đổi tên/xoá** từ
>    những lần build cũ (`InventoryServiceTest.xml` từ `T5` làm lệch **+10 case**). Muốn đọc file thì
>    `mvn clean` trước.
> 2. **Coverage:** `pom.xml` chỉ có **một** `prepare-agent` và bind `jacoco:report` vào phase **`test`**
>    — tức report được sinh **trước khi** Failsafe chạy `*IT`. Nên:
>    - `mvn -o clean verify` ⇒ `target/site/jacoco` là **unit một mình** (70.4%).
>    - Muốn số **unit + IT** (con số của series lịch sử) phải chạy `mvn -o verify` rồi
>      **`mvn -o jacoco:report`** lần nữa — lúc đó `jacoco.exec` đã có cả hai (agent `append=true`).
>    - `mvn -o verify` **không clean** cũng ra số unit+IT nhưng vì `jacoco.exec` còn dữ liệu IT của
>      **lần chạy trước** — số đúng do ăn may, không phải do thiết kế. Đừng dựa vào nó.



> **T0+T1 đã đạt:** 22 assertion method-security giờ có `verify(<guard>)` khoá chặt chuỗi `PERM_*`
> (sai tên permission ⇒ test đỏ, đã nghiệm thu mutation trên `BomService`/`SupplierService`/`InventoryService`);
> mỗi class có ≥ 1 nhánh allow; `PermissionCatalogTest` chống lệch code↔Flyway; bất biến **B13**
> (`BLOCKED` bị chặn issue/receipt/reserve) đã có test bảo vệ.

> **T3 đã đạt:** 3 class lõi bảo mật trước đây trần trụi nay được khoá bằng test (218 → **257 case**).
> `JwtTokenProvider`: round-trip `sub`/`roles`/`jti`, `TOKEN_EXPIRED` vs `TOKEN_MALFORMED`, đường
> expired-token của logout/refresh. `TokenStoreService`: **key string + TTL** của mọi Redis key ở §4.4,
> nhánh SCAN rỗng không gọi `delete`, blacklist TTL ≤ 0 là no-op, fail-counter chỉ set expire ở lần đầu.
> `AuthService`: rotation verify **cả** xoá token cũ lẫn lưu token mới; 3 nhánh `REFRESH_TOKEN_EXPIRED`
> đều chứng minh `never().saveRefreshToken`.
> **Giới hạn:** Redis vẫn là mock ⇒ TTL/SCAN thật chưa được kiểm — thuộc `T4`.

> **T2 đã đạt:** contract `{code,result,message}` (`.claude/rules/error-handling.md §5.1`) giờ có test
> ở đúng nơi trước đây trần trụi — `GlobalExceptionHandler` (cả 6 nhánh + `X-Trace-Id`) và 3 controller
> đại diện (`AuthController`, `WorkOrderController`, `InventoryController`), kể cả Idempotency-Key
> header forward (`WorkOrderController.issueComponent`, `InventoryController.receive`).
> `@WithMockUser` được dùng lần đầu trong repo (`I9` đã xử lý).
> **Giới hạn (bản `T2`, đã thu hẹp ở `D7` — nay còn 9/20, xem §0.4 #2):** 15/18 controller còn lại vẫn chưa có test (có chủ đích, xem `NEXT_PHASE_PLAN.md` T2 §11);
> `@WebMvcTest` trong repo này auto-detect mọi bean `Filter` trên classpath dù không `@Import` tường
> minh, nên mỗi `@WebMvcTest` mới (kể cả ở `P2`) cần `@MockBean` đủ 6 bean hạ tầng bảo mật
> (`IpExtractor`, `JwtTokenProvider`, `TokenStoreService`, `UserDetailsService`, `RedisTemplate`,
> `RateLimitProperties`) để context load được — chi tiết `TEST_IMPROVEMENT_PLAN.md` mục "Cập nhật sau T2".

> **T4+T5 đã đạt:** kích hoạt `Testcontainers` lần đầu (`AbstractPostgresIntegrationTest`, Singleton
> Container pattern) + `maven-failsafe-plugin` (chạy `*IT.java` tách khỏi `mvn test`). `FlywayMigrationIT`
> xác nhận `V1..V24` chạy sạch trên DB trống. `UserRoleAssignmentRepositoryIT` (6 case) chạy thật JPQL
> RBAC — bịt điểm mù bất biến **B32** (assignment/role/permission/scope hết hạn/`INACTIVE` bị loại đúng
> ở tầng JPQL, không chỉ mock). T5 dọn 5 khoản nợ nhỏ (`WipTransactionServiceTest` dùng helper thật,
> đổi tên test tautology, đổi tên `InventoryServiceTest`→`InventoryMovementServiceTest`, bỏ magic string
> `"BIZ_100"`, rà 71 chỗ `isInstanceOf(AppException.class)` sửa 2 chỗ thiếu `ErrorCode`).
> **Phát hiện quan trọng (bug thật, chưa sửa — quyết định của user):** `StockBalanceRepositoryIT` lộ ra
> `StockBalanceRepository.aggregateAvailableQuantities`/`aggregatePlanningQuantities`/
> `aggregateAvailableQuantitiesByWarehouse` dùng path expression `b.lot.status` khiến Hibernate sinh
> INNER JOIN, loại bỏ mọi `StockBalance` không lot-tracked khỏi kết quả — vi phạm bất biến B3, ảnh hưởng
> `InventoryAvailabilityService`/MRP. Ghi thành nợ kỹ thuật ở `module/inventory/CLAUDE.md`, IT hiện pin
> lại hành vi sai này (có giải thích) chờ sửa ở commit nghiệp vụ riêng — **quan trọng cho P2** vì P2 đụng
> đúng vùng `StockBalanceRepository`.
> **Giới hạn:** JaCoCo report từ `mvn test` không bao gồm coverage của `*IT.java` (chạy qua Failsafe ở
> phase `verify` riêng biệt).

### 0.2 Module Đã Hoàn Thành

| Module | Trạng thái | Ghi chú |
|---|---|---|
| `auth` + `user` | ✅ Done | JWT HS256, refresh rotation, multi-device session, brute-force Lua. **[2026-08-01]** `GET /api/v1/auth/me` — profile + permissions + `scopes[]` theo company/plant + `defaultPlantId` (endpoint duy nhất của `AuthController` không permit-all). Không phải phase `F*`/`D*`/`P*`, không migration. Xem `module/auth/CLAUDE.md` + `module/organization/CLAUDE.md` (B80) |
| `organization` + dynamic RBAC | ✅ Done | `roles` / `permissions` / `access_scopes` / `user_role_assignments`, scope Company→Plant→Warehouse |
| `inventory` | ✅ Done | `stock_movements` append-only ledger, `stock_balances` projection, lot tracking, idempotency. **[D6]** `Idempotency-Key` scope theo `(key, movement_type)` (V37). **[P5]** Serial tracking (`SerialNumber`, mirror lot nhưng quantity luôn = 1, `Item.serialTracked` loại trừ `lotTracked`) — xem §0.33 + `module/inventory/CLAUDE.md` B96-B97. Xem §0.14 + B69-B71 |
| `bom` | ✅ Done | BOM đa cấp, circular reference check, activate/deactivate revision |
| `planning` + `mrp` | ✅ Done | MRP run, requirement explosion, supply suggestion, safety stock + lead time |
| `purchasing` | ✅ Done | Supplier, PR → PO → Goods Receipt (+ cancel/reversal) |
| `workorder` | ✅ Done | WO core, material reservation/issue, WIP, production receipt, variance |
| `workorder` – **P1 gates** | ✅ Done | 3 business gate (chi tiết §0.3) |
| `common/audit`, `common/security`, `common/response`, `common/exception` | ✅ Done | |
| `quality` (QC disposition) | ✅ Done (`F2`, `D5`) | **Không** là package riêng — `QualityDisposition` nằm trong `module/workorder` vì thuộc aggregate Production Receipt. **[D5]** QC chạy cho **cả** output không lot-tracked (phán quyết trên receipt); `quality_dispositions` vẫn chỉ ghi khi có lot. Xem `module/workorder/CLAUDE.md` B38-B41 |
| `sales` | ✅ Done (`F3`, `F6`) | Sales Order + line, confirm ⇒ independent demand cho MRP, endpoint `planning-demands`. **[F6]** fulfillment + roll-up status. Xem `module/sales/CLAUDE.md` B43-B47, B66 |
| `routing` | ✅ Done (`F4`, FK Work Center ở `C2-6`) | `RoutingHeader` + `RoutingOperation`, 1 routing `ACTIVE`/item, snapshot bất biến lên WO. **[C2-6]** `RoutingOperation.workCenterCode` → `workCenter` (FK), bất biến `B_wc2`; CRP/capacity vẫn chưa làm (`C2-8`). Xem `module/routing/CLAUDE.md` B48-B52 |
| `planning` – **F5-B** | ✅ Done | Endpoint `/planning-runs` + `/supply-suggestions`, `demandLineIds`, `supplyType` `MAKE`/`BUY`, `exceptionState` + `messages[]`, `settingSource` + `excludedLotCount` (V34). Xem §0.10 + `module/planning/CLAUDE.md` B58-B61 |
| `planning` – **D4** | ✅ Done | Open purchase order vào netting (`PurchaseOrderSupplyService`), `MrpRun.code` + 4 ô summary Run header (V36). Xem §0.12 + `module/planning/CLAUDE.md` B67-B68 |
| `workorder` – **F5-A** | ✅ Done | `ProductionExecution` + `WorkOrderOperation` (snapshot routing), status `PLANNED`, đảo ngược B16/B17, `PERM_PRODUCTION_EXECUTION_*` (V32/V33). Xem §0.9 |
| `workorder` – **F6** | ✅ Done | `WorkOrderDemandAllocation` (V35) nối WO ↔ Sales Order line; QC `AVAILABLE` ⇒ fulfillment. Xem §0.11 + `module/workorder/CLAUDE.md` B62-B65 |
| `uom` | ✅ Done (`C2-3`) | Unit of measure master data, **global** (không `company_id`). 7 endpoint, permission `PERM_UOM_READ`/`_MANAGE`. **Chưa nối** với `items.unit` (vẫn `String` tự do) — xem `module/uom/CLAUDE.md` |
| `workcenter` | ✅ Done (`C2-6`, calendar FK ở `C2-7`) | Work center master data, **per-plant**. 7 endpoint `/api/v1/plants/{plantId}/work-centers` + `/api/v1/work-centers/{id}`, permission `PERM_WORK_CENTER_READ`/`_MANAGE`. **[C2-7]** thêm FK tuỳ chọn `workCalendarId` (bất biến `B_wc4`). Xem `module/workcenter/CLAUDE.md` B_wc1-B_wc4 |
| `shift` | ✅ Done (`C2-7`) | Shift (1 interval + `breaks[]`) + Work Calendar (lịch tuần + exception `NON_WORKING`), **per-plant**. 14 endpoint `/api/v1/plants/{plantId}/shifts`+`/api/v1/shifts/{id}` và `/api/v1/plants/{plantId}/work-calendars`+`/api/v1/work-calendars/{id}`, permission `PERM_SHIFT_READ`/`_MANAGE`, `PERM_WORK_CALENDAR_READ`/`_MANAGE`. **[C2-8]** `WorkingWindowCalculator`/`WorkCalendarLookupService` nay có người gọi thật ngoài module (xem dưới). Xem `module/shift/CLAUDE.md` B_sh1-B_sh2, B_cal1-B_cal2 |
| `costing` | ✅ Done (`P3`) | `ItemStandardCost` (upsert, company-scoped, không có history/`effectiveDate`) + `CostingService` (BOM cost roll-up đệ quy) + `WorkOrderCostAccumulator` (`module/workorder`, tích luỹ material/labor/overhead thực tế qua 2 hook ở `MaterialIssueService`/`ProductionExecutionService`). 3 endpoint `/api/v1/companies/{companyId}/items/{itemId}/standard-cost` (+ list), permission `PERM_COSTING_READ`/`_MANAGE` (**ADMIN+MANAGER only**, không có OPERATOR — khác mọi permission `C2-*` gần đây). `GET /work-orders/{id}/variance` mở rộng `usageVarianceCost` + `costVariance`. **Không** làm Material Price Variance (quyết định có chủ đích — không có cột giá trên `stock_movements`). Xem `module/costing/CLAUDE.md` B91-B92, `module/workorder/CLAUDE.md` B93-B94 |
| Giá thành sản xuất (`G11`) | ✅ Done | Nằm trong `module/workorder` (`service/cost/`, `ProductionCostController`): đơn giá giờ theo trung tâm (phiên bản), cơ sở chi phí lúc phát hành, giá thành theo lệnh, run tính thử, checklist 11 mã, chốt + đóng nguyên tử, bút toán chỉ-giá-trị, giá vốn phiếu giao. Khác hệ `P3` (`module/costing`, `/variance`). Xem §0.71 + `module/workorder/CLAUDE.md` B256-B262 |
| `billing` | ✅ Done (`G10c`, `G10d`) | Hoá đơn bán hàng nội bộ của phiếu giao đã ghi sổ (hạn mức theo dòng phiếu giao, HALF_UP cộng dồn, `billingSnapshotVersion` + `PRICE_STALE`) và thu tiền khách hàng (phân bổ nhiều hoá đơn, trần `amount − P − Cp`, `paymentState` dẫn xuất) + tiến độ đơn `fulfillment-summary`; không tác động tồn kho, `PERM_SALES_INVOICE_*`/`PERM_CUSTOMER_PAYMENT_*`. Xem §0.69–§0.70 + `module/billing/CLAUDE.md` B246-B255 |
| `delivery` | ✅ Done (`G10b`) | Phiếu giao hàng của đơn bán: hạn mức dẫn xuất từ sổ, nguồn = hàng tự do + phần giữ của chính đơn, ghi sổ hai chân `ISSUE` nguyên tử, `PERM_DELIVERY_*`. Xem §0.68 + `module/delivery/CLAUDE.md` B241-B245 |
| Capacity Board (CRP tĩnh) | ✅ Done (`C2-8`) | `GET /plants/{plantId}/capacity-board` + `POST /work-orders/{id}/operations/{id}/schedule-adjustments`, permission `PERM_CAPACITY_READ`/`_MANAGE`. Lịch (`WorkOrderOperation.plannedStartAt`/`plannedEndAt`) sinh ở `WorkOrderService.release()`, "infinite capacity" (không biết WO khác đang chiếm cùng Work Center); Capacity Board là một read riêng tổng hợp load/capacity/utilization. Xem `module/workorder/CLAUDE.md` B89-B90 |

### 0.3 P1 – Approval Workflow & Business Gates (ĐÃ HOÀN THÀNH)

| Gate | Nội dung | Hệ quả cần nhớ khi code tiếp |
|---|---|---|
| **1a** | Release WO yêu cầu reservation phủ **100%**; thiếu → WO chuyển `BLOCKED` + trả **409** (`F5` đổi từ 422). `BLOCKED` **thật sự được persist** từ `D9` (trước đó bị rollback cùng exception — nợ #23) | `WorkOrderStatus` có thêm `BLOCKED`. Mọi `switch`/so sánh status phải xét nhánh này. `BLOCKED` **không** được issue / receipt / WIP, **nhưng ĐƯỢC reserve** (`D9`, nợ #22) — đó chính là trạng thái planner reserve để thoát ra |
| **1b** | Xuất vật tư vượt định mức BOM cần `PERM_MATERIAL_ISSUE_OVERRIDE` + `overrideReason` | Constraint `issued_quantity <= required_quantity` **đã bị gỡ** (giờ chỉ còn `>= 0`). Lưu vết ở cột `material_issue_lines.over_issue` + `override_reason` |
| **1c** | Production Receipt tách 2 bước: `post` → `PENDING_APPROVAL` (**không đụng tồn kho**) → `approve` → `POSTED` + movement + lot `HOLD`. ⚠️ **`F2` đã mở rộng thành 3 bước** (`DRAFT` → `PENDING_APPROVAL` → `APPROVED`) — xem §0.6 | `ProductionReceiptLine.stockMovement` giờ **nullable**. Mọi chỗ đọc `getStockMovement()` phải null-safe |

**2 giới hạn đã biết của P1 — đã được xử lý ở `F2`:**
1. Item **không** lot-tracked ⇒ không có chỗ mang trạng thái `HOLD` ⇒ output dùng được ngay.
   QC chỉ áp dụng cho output **lot-tracked**. ⇒ `F2` chặn `qc-disposition` trên receipt không
   lot-tracked bằng `STATE_CONFLICT`, và bắt buộc `lotCode` ngay từ bước `post` (`LOT_REQUIRED`).
2. Lot đã tồn tại (trùng `lotCode`) **giữ nguyên** status hiện tại — chỉ lot **mới tạo** mới nhận `HOLD`.
   Khuyến nghị nghiệp vụ: mỗi production receipt dùng một `lotCode` mới. ⇒ `F2` **chưa** ép buộc
   điều này; nếu lot tái sử dụng không ở `HOLD` thì `qc-disposition` trả `LOT_NOT_ELIGIBLE`.

### 0.6 F2 – Production Receipt Lifecycle & QC Disposition (ĐÃ HOÀN THÀNH 2026-07-26)

| Bước | Endpoint | Permission | Tác động |
|---|---|---|---|
| Create | `POST /work-orders/{id}/production-receipts` | `PERM_PRODUCTION_RECEIPT_MANAGE` | → `DRAFT`, **không** tồn kho |
| Submit | `POST .../production-receipts/{rid}/submit` | `PERM_PRODUCTION_RECEIPT_MANAGE` | → `PENDING_APPROVAL`, **không** tồn kho |
| Approve | `POST .../production-receipts/{rid}/approve` | `PERM_PRODUCTION_RECEIPT_APPROVE` | → `APPROVED` + RECEIVE movement + lot `HOLD` + cộng `completedQuantity` |
| Reject | `POST .../production-receipts/{rid}/reject` | `PERM_PRODUCTION_RECEIPT_APPROVE` | → `REJECTED`, reason bắt buộc (`APPROVAL_REASON_REQUIRED`) |
| QC | `POST .../production-receipts/{rid}/qc-disposition` | `PERM_QUALITY_DISPOSITION` | lot → `AVAILABLE`/`REJECTED` + `LOT_STATUS_CHANGE` movement. Receipt **vẫn** `APPROVED` |

**Hệ quả cần nhớ khi code tiếp:**
1. `ProductionReceiptStatus.POSTED` **không còn tồn tại** — đổi tên thành `APPROVED`, dữ liệu cũ
   được `UPDATE` trong `V26`. Mọi so sánh status phải xét cả `DRAFT`.
2. `MovementDirection` có giá trị thứ ba `NONE` (chỉ dùng cho `LOT_STATUS_CHANGE`).
3. Lỗi *sai trạng thái* của receipt nay trả `STATE_CONFLICT` (**409**), không còn
   `OPERATION_NOT_ALLOWED` (422) — trả một phần nợ #9 trong đúng phạm vi vòng đời receipt.
4. QC **không** đổi `stock_balances`; available tăng gián tiếp qua lot status.

### 0.4 Nợ Kỹ Thuật Đã Biết (không phải bug, đừng "tiện tay sửa")

| # | Nợ | Phase xử lý |
|---|---|---|
| 1 | ~~10 class `*MethodSecurityTest` chỉ test nhánh deny~~ ✅ **ĐÃ TRẢ (T1)** – mọi assertion deny có `verify(<guard>)` khoá `PERM_*`, mỗi class có nhánh allow, thêm `PermissionCatalogTest` + 6 class test cho 11 permission trước đó chưa phủ | ~~`T1`~~ |
| 2 | ~~`GlobalExceptionHandler` + controller không có test~~ ✅ **ĐÃ TRẢ HẾT (T2 → `D7` → `D7b`)** – `GlobalExceptionHandler` (cả 6 nhánh) + **20/20** controller nay khoá contract `{code,result,message}`: 3 từ `T2` (`Auth`, `WorkOrder`, `Inventory`) + 8 từ `D7` (nhóm A, có state machine) + **9 từ `D7b`** (nhóm B+C: `Item`, `ItemWarehouseSetting`, `Supplier`, `User`, `Organization`, `AccessControl`, `InventoryReport`, `Planning`, `PlanningDemand`). ⚠️ Tổng là **20**, không phải 18/19 như các bản trước ghi | ~~`D7b`~~ |
| 3 | ~~`AuthService.refresh` / `logout` / `logoutAll`, `TokenStoreService`, `JwtTokenProvider` không có test~~ ✅ **ĐÃ TRẢ (T3)** – 39 case mới. Còn lại: Redis là mock, chưa chạy thật (→ `T4`) | ~~`T3`~~ |
| 4 | ~~Logic phân quyền (hạn hiệu lực assignment, status role/org) nằm trong JPQL nhưng JPQL chưa bao giờ được chạy trong test~~ ✅ **ĐÃ TRẢ (T4)** – `UserRoleAssignmentRepositoryIT` (6 case) chạy JPQL thật qua Testcontainers, nghiệm thu mutation xác nhận | ~~`T4`~~ |
| 5 | ~~`Testcontainers` đã khai báo trong `pom.xml` nhưng chưa dùng lần nào~~ ✅ **ĐÃ TRẢ (T4)** – `AbstractPostgresIntegrationTest` + 3 `*IT.java`. `spring-security-test` ✅ đã dùng từ T2 | ~~`T4`~~ |
| 6 | 🟡 **Trả 2/3 — nợ VẪN MỞ.** RTR: ✅ **đã trả (`D8a`, 2026-08-03)**, §0.22 + `B80`. Absolute session timeout: ✅ **đã trả (`D8b`, 2026-08-03)**, §0.23 + `B81`. **Forgot-password (`D8c`): vẫn chưa có bất kỳ dòng code nào** — và đang **bị chặn** vì `pom.xml` không có `spring-boot-starter-mail` (phải chốt hạ tầng gửi email trước). 🔴 Đừng ghi "nợ #6 đã đóng" — đúng bài học §0.20 (repo đã ba lần tuyên bố đóng nợ rộng hơn phạm vi thật) | RTR: ~~`D8a`~~ · timeout: ~~`D8b`~~ · còn lại: `D8c` |
| 7 | ~~`LoginRequest` / `RefreshRequest` là **record thuần** ⇒ `toString()` tự sinh **lộ password / raw token**~~ ✅ **ĐÃ TRẢ (`D1`, 2026-07-28)** – rà ra **5** record dính (không phải 2): `LoginRequest`, `RefreshRequest`, `LogoutRequest`, `CreateUserRequest`, `UpdateUserRequest`. Cả 5 override `toString()` che secret, **giữ** `tokenId` hiện (là định danh Redis, không phải credential) và **giữ** phân biệt `null` vs `***` (thiếu password và ẩn password là hai bug khác nhau). `SensitiveRequestToStringTest` (5 case) là regression guard, đã nghiệm thu mutation | ~~Commit riêng~~ `D1` |
| 8 | ~~`StockBalanceRepository.aggregate*` dùng path expression `b.lot.status` ⇒ INNER JOIN loại bỏ mọi `StockBalance` không lot-tracked~~ ✅ **ĐÃ TRẢ (`F1.6`, 2026-07-26)** – cả 3 query dùng `left join b.lot l` tường minh; `StockBalanceRepositoryIT` nay assert hành vi đúng và là regression guard | ~~`F1`~~ |
| 9 | ~~Spec đặt `INSUFFICIENT_AVAILABLE_STOCK` / `STATE_CONFLICT` ở **409**, code trả **422**~~ ✅ **ĐÃ TRẢ HẾT (`F5-A` + `D7`)** – `F5-A`: `INSUFFICIENT_STOCK` → 409, state machine của work order / issue / reservation / WIP → `STATE_CONFLICT` (409), lot sai trạng thái → `LOT_NOT_ELIGIBLE` (409). **`D7`**: rà nốt 4 module còn lại theo đúng tiêu chí `.claude/rules/error-handling.md §5.3`, **44 throw site** phân loại từng chỗ ⇒ sửa **9** (`bom` 1, `purchasing` 8), `organization`/`sales` **0** (vốn đã đúng). `OPERATION_NOT_ALLOWED` (422) **cố ý giữ lại** cho validate master data **và** cho "chứng từ chưa có dòng nào" — bảng liệt kê từng chỗ ở `FRONTEND_ALIGNMENT_ROADMAP.md §6.1.6` | ~~`F5`~~ + ~~`D7`~~ |
| 10 | ~~`Idempotency-Key` của `stock_movements` là **UNIQUE toàn bảng**, không scope theo `(userId, operation)` như spec §10.2. Hệ quả: cùng một key gửi tới `/receive` rồi `/issue` thì lần 2 trả về movement của lần 1~~ ✅ **ĐÃ TRẢ (`D6`, 2026-07-30)** – `V37` đổi constraint sang `UNIQUE (idempotency_key, movement_type)` **và** `findByIdempotencyKey` → `findByIdempotencyKeyAndMovementType` trong **cùng commit** (đổi lệch nhau còn tệ hơn hiện trạng — xem `B69`). Scope theo `created_by` như spec §10.2 **cố ý không** làm: cột nullable + Postgres coi 2 `NULL` là khác nhau ⇒ mọi dòng lịch sử mất bảo vệ trùng lặp; cần backfill trước. `issue`/`issueReserved` cùng scope `ISSUE` có chủ đích (`B70`). Bất biến `B69`-`B71` | ~~`D6`~~ |
| 11 | ~~Spec §6.3 mô tả Production Receipt là **một dòng**, code dùng `lines[]`~~ ✅ **ĐÃ TRẢ (`F5-A`)** – request/response nay phẳng (`quantity` + `destinationWarehouseId` + `lotNumber`). Bảng `production_receipt_lines` **cố ý giữ nguyên** làm storage (mỗi receipt đúng 1 dòng) — collapse bảng là migration phá dữ liệu lịch sử mà không đổi hành vi | ~~`F5`~~ |
| 13 | ~~`POST /planning-runs` vẫn tự quét demand theo horizon, **chưa** nhận `demandLineIds` như spec §2.3~~ ✅ **ĐÃ TRẢ (`F5-B`)** – `MrpRunCreateRequest.demandLineIds` chọn đúng demand vào run; vắng mặt thì giữ hành vi quét horizon cũ (bất biến `B60`) | ~~`F5-B`~~ |
| 15 | ~~`MrpRun` chưa có `code` và 4 ô summary spec §2.4~~ ✅ **ĐÃ TRẢ (`D4`, 2026-07-30)** – `code` (`RUN-<8 hex>`, sinh ở `@PrePersist`, UNIQUE, backfill trong `V36`) + `shortageLines` / `plannedWorkOrders` / `plannedPurchaseRecommendations` / `blockedProposals`. Đếm **tại chỗ** từ `MrpCalculationResult`, không query lại; run cũ mang 0 ở 4 ô (số thật vẫn đọc được từ requirement/suggestion lines). Bất biến `B68` | ~~`D4`~~ |
| 16 | ~~`scheduledReceipts` thiếu purchase order đang mở ⇒ MRP đề xuất mua trùng thứ đã đặt hàng~~ ✅ **ĐÃ TRẢ (`D4`, 2026-07-30)** – `PurchaseOrderSupplyService` (`module/purchasing/service/query/`, entry point cross-module theo `C7`) cộng PO `SENT`+`PARTIALLY_RECEIVED` vào `openSupplyQuantity`. `DRAFT` **không** tính; dòng đã nhận đủ **không** tính (đã nằm trong on-hand). 1 query/cấp BOM (`C14`), JPQL nghiệm thu thật bằng `PurchaseOrderRepositoryIT`. Bất biến `B67` | ~~`D4`~~ |
| 17 | ~~**Thành phẩm không lot-tracked không bao giờ fulfill được Sales Order.**~~ ✅ **ĐÃ TRẢ (`D5`, 2026-07-30)** — QC disposition nay chạy cho **cả** output không lot: điều kiện rẽ nhánh là **cấp dòng** (`line.lot != null`), phán quyết ghi trên `production_receipts` (`qc_result`/`qc_reason`/`qc_at`/`qc_by`). Giữ đúng 2 điều user đã bác: **không** đường fulfill thứ hai ở `approve`, **không** ép item phải lot-tracked. Số đưa sang fulfillment là **tổng quantity dòng receipt** — nguồn cũ cộng dồn từ lot line nên ra 0 và `fulfill` no-op **im lặng** (bẫy chính của phase, đã khoá bằng nghiệm thu mutation). Không migration. Bất biến `B39` viết lại + `B40` tách đôi | ~~`D5`~~ |
| 26 | ~~**`planning` còn 3 chỗ trả 422 mà 3 tiêu chí §5.3 xếp vào 409.**~~ ✅ **ĐÃ TRẢ (`D11`, 2026-07-31)** — `D7` rà `bom`/`purchasing`/`organization`/`sales` và tuyên bố nợ #9 "đã đóng hết", nhưng **`planning` chưa bao giờ nằm trong phạm vi rà đó**. Ba chỗ đọc **status chứng từ** nay trả `STATE_CONFLICT` (409) qua `ExceptionFactory.custom`: `PlanningDemandService.cancel` (`!demand.isOpen()`), `SupplySuggestionService.convertToWorkOrder` (`!suggestion.isApproved()`), `SupplySuggestionService.ensureDraft` (`approve`/`reject`). Lập luận quyết định **không** phải tài liệu mà là mâu thuẫn nội tại: nhánh "suggestion không `APPROVED`" của `PurchaseRequisitionService` đã sang 409 từ `D7` ⇒ cùng một điều kiện nghiệp vụ trả 2 status tuỳ đường convert. 🔴 Các chỗ 422 còn lại của `planning` (`ensurePlantBelongsToCompany`, `ensureItemBelongsToCompany`, `ensureWarehouseBelongsToPlant`, `PlanningService` item khác company, **"sai `SupplySuggestionType`" ngay dưới chỗ vừa đổi**) là master data / input sai ⇒ **cố ý giữ 422, đừng đụng**. Bất biến `B74` | ~~`D11`~~ |
| 25 | ~~**Dòng receipt cuối của work order không bao giờ nhập kho được.**~~ ✅ **ĐÃ TRẢ (`D11`, 2026-07-31)** — `WorkOrder.report` gọi `complete()` ngay khi `actualGoodQuantity >= plannedQuantity` (`B53`) trong khi `canExecute()` loại `COMPLETED` (`B13`) ⇒ report đủ số kế hoạch xong thì không receipt được nữa. Sửa bằng **phương án A (nới `B13`)** theo đúng tiền lệ `D9`: thêm `WorkOrder.canReceipt()` (= `canExecute()` **+ `COMPLETED`**, vẫn loại `CANCELLED`/`BLOCKED`) + `WorkOrderExecutionSupport.ensureReceiptable`. ⚠️ **Đổi cả 3 call site của vòng đời receipt** (`postNew`, `submit`, `approve`) — kế hoạch ban đầu chỉ ghi `postNew`, nhưng thế thì draft tạo được rồi kẹt không submit/approve được, tồn kho vẫn không đổi. `issue`/`report`/WIP **giữ** `ensureExecutable`. Trần `B16` không nới. **Phương án B (hoãn `complete()`) bị loại**: WO làm xong mà chưa ai nhập kho sẽ kẹt `IN_PROGRESS` vô thời hạn + đổi nghĩa cột có dữ liệu lịch sử. Thước đo: `ProductionFlowE2EIT` đã **bỏ** seed safety stock = 2 của `D5` | ~~`D11`~~ |
| 14 | ~~`MISSING_ROUTING` trả 409 nhưng `MISSING_BOM` trả 404~~ ✅ **ĐÃ TRẢ (`F5-A`)** – thêm `BusinessErrorCode.MISSING_BOM` (409), `BomLookupService.getActiveBom` dùng nó thay `RESOURCE_NOT_FOUND` | ~~`F5`~~ |
| 12 | ~~Spec §6.4 đòi `code`, `sourceWipTraceIds`, `*Username`, rename `itemCode`→`itemSku` / `lotCode`→`lotNumber`~~ ✅ **ĐÃ TRẢ (`F5-A`)** – đủ cả; username resolve qua `UserLookupService` bằng **1 query batch** cho cả trang (rule C14) | ~~`F5`~~ |
| 18 | ~~Ranh giới áp dụng `X-Plant-Id` chưa được ghi ⇒ quyết định `F1-D6` ("các controller còn lại sẽ wire ở `F5`") gây hiểu nhầm là có code bị bỏ sót~~ ✅ **ĐÃ TRẢ (`D1`, 2026-07-28)** – **không** phải bỏ sót: header chỉ có nghĩa với endpoint nêu `plantId` tường minh; endpoint định danh bằng aggregate (`/work-orders/{id}/…`) lấy plant từ chính aggregate nên không có giá trị thứ hai để đối chiếu, và `@PreAuthorize` đã chặn. Ranh giới nay ghi ở `.claude/rules/error-handling.md §5.6.1` + javadoc `PlantContextResolver` | ~~`D1`~~ |
| 19 | ~~Kịch bản nghiệm thu end-to-end 11 bước (spec §11.1) chưa được viết thành `*IT.java`~~ ✅ **ĐÃ TRẢ (`D1`)** – `ProductionFlowE2EIT` (2 case, `@SpringBootTest` **duy nhất** được phép, chạy qua HTTP + Testcontainers). Nó **lập tức tìm ra 4 defect** mà 391 unit test không thấy: nợ #21, #22, #23, #24 | ~~`D1`~~ |
| 24 | ~~`POST /material-issues` (endpoint phẳng spec §4.1) không replay-safe~~ ✅ **ĐÃ TRẢ (`D10`, 2026-07-30)** – `postFlat` nay resolve reservation **không** kiểm `ACTIVE` (chỉ để dịch request); `postNew` vẫn kiểm nên đường tạo chứng từ không bị nới. Retry cùng `Idempotency-Key` trả lại đúng chứng từ cũ (`R9`), đã assert bằng `issueId` ở bước 11 của `ProductionFlowE2EIT` | ~~`D10`~~ |
| 20 | ~~Số liệu trong `§0.1`/`§0.4` lệch thực tế (coverage còn là số của `F4`, "18 controller")~~ ✅ **ĐÃ TRẢ (`D1`)** – đo lại và sửa. **Bài học:** coverage phải đo lại mỗi phase, không chép số phase trước | ~~`D1`~~ |
| 21 | ~~`GET /sales-orders/planning-demands` không trả `planningDemandId` mà `POST /planning-runs` cần~~ ✅ **ĐÃ TRẢ (`D10`, 2026-07-30)** – `PlanningDemandLineResponse` thêm `planningDemandId` (additive). Lấy qua **1 query batch** `PlanningDemandService.findOpenDemandIdsBySalesOrderLineIds` (rule `C7`+`C14`), **không** join JPQL xuyên module vì `referenceId` là `String`. Đóng nốt nửa còn lại của nợ #13 | ~~`D10`~~ |
| 22 | ~~Work order tạo từ MRP không bao giờ release được qua API (reserve đòi `RELEASED`, release đòi reservation 100%)~~ ✅ **ĐÃ TRẢ (`D9`, 2026-07-30)** – thêm `WorkOrder.canReserve()` (mọi status trừ `COMPLETED`/`CANCELLED`) + `WorkOrderExecutionSupport.ensureReservable`; **chỉ** 2 call site reserve đổi sang gate mới, issue/execution/receipt/WIP vẫn `canExecute()`. B13 tách đôi. Thước đo: seam `forceStatus` trong `ProductionFlowE2EIT` đã **xoá**, bước 4-5 chạy reserve → release đúng thứ tự spec §11.1 | ~~`D9`~~ |
| 23 | ~~`BLOCKED` của gate 1a không bao giờ được persist (throw trong chính transaction `REQUIRES_NEW` của nó ⇒ rollback-only)~~ ✅ **ĐÃ TRẢ (`D9`, 2026-07-30)** – tách `WorkOrderBlockRecorder` (bean riêng, `REQUIRES_NEW`, **return bình thường** nên commit được), gate gọi nó xong mới throw. Javadoc sai đã sửa. `ProductionFlowE2EIT` đọc lại từ DB để chứng minh row sống sót rollback — mock không kiểm được điều này | ~~`D9`~~ |
| 27 | ~~🔴 **`RedisConfig` khai báo `@Bean ObjectMapper` trần ⇒ ghi đè ObjectMapper auto-config của Spring Boot cho TOÀN BỘ app, làm mọi cấu hình `spring.jackson.*` trong `application.yml` bị vô hiệu im lặng.** Hệ quả **đo được qua HTTP thật** (2026-08-10): `write-dates-as-timestamps: false` (`application.yml:54`) không có tác dụng ⇒ `LocalDate` serialize thành **mảng** `[2026, 8, 8]` và `Instant` thành **số epoch** `1786350234.709543`, trong khi `docs/api-guide-for-frontend.md:193-194` hứa với FE là ISO (`"2026-08-15"` / `"2026-08-03T08:00:00Z"`). `default-property-inclusion: non_null` cũng bị vô hiệu (đang bị `@JsonInclude` trên chính record `ApiResponse` che). Bean đó tồn tại để cấu hình serializer **Redis**, nhưng không đặt tên/`@Qualifier` nên thắng luôn vai trò ObjectMapper của web layer. **Ảnh hưởng mọi endpoint có field ngày/thời điểm**, không riêng sales.~~ ✅ **ĐÃ TRẢ (2026-08-12)** — FE yêu cầu tường minh chuyển sang ISO (`ADMIN_RBAC_BACKEND_RESPONSE_REVIEW_2026-08-12.md §4`) và xác nhận adapter của họ nhận **cả hai** định dạng, nên cửa sổ đổi wire mở ra mà không cần release đồng thời. Bean đã xoá; Boot tự cấu hình lại `ObjectMapper` (jsr310 sẵn trên classpath). 🔴 **`default-property-inclusion` cố ý ghi đè `non_null` → `always`**: để `non_null` có hiệu lực cùng lúc là **xoá mọi field null khỏi mọi response** — thay đổi payload toàn hệ thống mà không ai yêu cầu, phải là quyết định riêng có phối hợp FE (quyết định của user qua `AskUserQuestion`). Hệ quả phụ **có** xảy ra và đã báo FE: `fail-on-unknown-properties: false` nay có hiệu lực ⇒ field lạ trong request body bị bỏ qua thay vì trả 400. Bản ghi: **§0.42** | ~~*(chưa chốt)*~~ **2026-08-12** |

### 0.7 F3 – Sales Order + Planning Demand (ĐÃ HOÀN THÀNH 2026-07-26)

| Bước | Endpoint | Permission | Tác động |
|---|---|---|---|
| Create | `POST /sales-orders` | `PERM_SALES_ORDER_MANAGE` | → `DRAFT`, **chưa** sinh demand |
| Confirm | `POST /sales-orders/{id}/confirm` | `PERM_SALES_ORDER_MANAGE` | → `CONFIRMED` + **1 `PlanningDemand` / line** (`demandType = SALES_ORDER`) |
| Cancel | `POST /sales-orders/{id}/cancel` | `PERM_SALES_ORDER_MANAGE` | → `CANCELLED` + huỷ demand còn `OPEN`. Chỉ từ `DRAFT`/`CONFIRMED` |
| List / Get | `GET /sales-orders`, `GET /sales-orders/{id}` | `PERM_SALES_ORDER_READ` | — |
| Planning demand | `GET /sales-orders/planning-demands?plantId=…&horizonEnd=…` | **`PERM_MRP_RUN`** (spec §2.2 gán `PLANNING_RUN`, không phải quyền sales) | 1 aggregate query, trả open quantity |

**Hệ quả cần nhớ khi code tiếp:**
1. `PlanningDemandService` có thêm **2 entry point không `@PreAuthorize`** dành riêng cho sales
   (`createFromSalesOrderLine`, `cancelOpenDemandsForSalesOrderLines`) — caller đã được authorize
   trên cùng plant. Lý do đầy đủ ở `module/sales/CLAUDE.md`. **Không** gọi chúng từ controller.
2. Hướng phụ thuộc là `sales → planning`. **Không có** `SalesOrderLookupService` (không ai gọi vào
   `sales`) — khác với dự kiến ban đầu của kế hoạch `F3`, xem `module/sales/CLAUDE.md` mục 1.
3. `SalesOrderStatus` khai báo đủ 6 giá trị nhưng `IN_PRODUCTION`/`PARTIALLY_FULFILLED`/`FULFILLED`
   **chưa có đường vào** — `F6` mới set. `fulfilledQuantity` luôn = 0 trong `F3`.
4. Lệch tên có chủ đích: `orderNo` (CRUD) vs `salesOrderCode` (màn hình Planning) — cùng một cột.
5. Chưa nối vào `MrpRunService`: MRP vẫn chọn demand qua `PlanningDemandRepository.findOpenDemandsForRun`
   theo horizon, nên demand từ sales tự động được nhặt. Endpoint `POST /planning-runs` nhận
   `demandLineIds` như spec §2.3 mô tả thì thuộc `F5` (reshape Planning).

### 0.8 F4 – Routing Tối Thiểu + Snapshot Lên Work Order (ĐÃ HOÀN THÀNH 2026-07-27)

| Bước | Endpoint | Permission | Tác động |
|---|---|---|---|
| Create | `POST /companies/{companyId}/routings` | `PERM_ROUTING_MANAGE` | → `DRAFT` + operations inline |
| Activate | `POST /routings/{routingId}/activate` | `PERM_ROUTING_MANAGE` | → `ACTIVE` + routing `ACTIVE` cũ **của cùng item** → `INACTIVE` |
| Deactivate | `DELETE /routings/{routingId}` | `PERM_ROUTING_MANAGE` | → `INACTIVE` |
| List / Get | `GET /companies/{companyId}/routings`, `GET /routings/{routingId}` | `PERM_ROUTING_READ` | — |

**Hệ quả cần nhớ khi code tiếp:**
1. `work_orders` có thêm 4 cột **nullable**: `source_routing_id` / `source_routing_code` /
   `source_routing_version` / `routing_captured_at`. Mọi chỗ đọc phải null-safe.
2. Snapshot là **cột phẳng, không phải `@ManyToOne`** — đọc qua association sẽ trả master data
   sống và phá bất biến B49. Đừng "tiện tay" đổi thành quan hệ.
3. `WorkOrderService.createFromMrp` **không còn** gọi lồng vào `create`; cả hai gọi
   `createInternal(plantId, request, routingRequired)`. Chỉ đường MRP đòi routing `ACTIVE`
   (`MISSING_ROUTING`, 409); tạo WO thủ công vẫn chạy với snapshot rỗng.
4. `RoutingOperation.workCenterCode` là **string**, không có entity Work Center — capacity/CRP
   ngoài MVP (spec §11). `predecessorOperationIds` của spec §3.3 chưa làm.
5. **Chưa có** bảng `work_order_operations` — thuộc `F5`, thiết kế cùng Production Execution.
6. `RoutingHeader.routingVersion` (cột `routing_version`) vì `BaseEntity` đã chiếm `version` cho
   optimistic locking; DTO vẫn expose tên `version` ra wire.

### 0.9 F5-A – Production Execution + Work Order Reshape (ĐÃ HOÀN THÀNH 2026-07-27)

| Bước | Endpoint | Permission | Tác động |
|---|---|---|---|
| Schedule | `POST /work-orders/{id}/plan` | `PERM_WORK_ORDER_MANAGE` | `DRAFT` → `PLANNED`, không đụng vật tư |
| Auto reserve | `POST /work-orders/{id}/reserve` | `PERM_MATERIAL_RESERVATION_MANAGE` | Reserve FEFO mọi component thiếu; **thành công một phần là có chủ đích** |
| Issue phẳng | `POST /material-issues` | `PERM_MATERIAL_ISSUE_MANAGE` | 1 dòng theo reservation (spec §4.1); không hỗ trợ over-issue |
| **Report sản lượng** | `POST /work-orders/{id}/production-executions` | `PERM_PRODUCTION_EXECUTION_MANAGE` | **+`actualGood/Scrap/Rework`, `IN_PROGRESS`, `COMPLETED`** + WIP ledger |
| Receipt create | `POST /work-orders/{id}/production-receipts` | `PERM_PRODUCTION_RECEIPT_MANAGE` | → `DRAFT`, body **phẳng**, trần = `availableToReceipt` |
| Receipt approve | `.../{rid}/approve` | `PERM_PRODUCTION_RECEIPT_APPROVE` | movement + lot `HOLD` + `completedQuantity` + WIP `OUTPUT_RECEIPTED`. **Không** đổi status WO |

**Hệ quả cần nhớ khi code tiếp:**
1. `WorkOrderStatus` có thêm `PLANNED`. Mọi `switch`/so sánh status phải xét nhánh này — nó hành xử
   như `DRAFT` với execution nhưng **được** release và cancel.
2. `WipTransactionType` có thêm `OUTPUT_RECEIPTED`. `OUTPUT_COMPLETED` nay nghĩa là "xưởng làm ra",
   không còn là "đã nhập kho".
3. `ProductionReceiptPostRequest`/`Response` **phẳng** (`quantity`, `destinationWarehouseId`,
   `lotNumber`) — `lines[]` và `ProductionReceiptLineRequest/Response` đã xoá. Bảng
   `production_receipt_lines` vẫn còn làm storage, đúng 1 dòng/receipt.
4. `WipTransactionRequest` thêm `workOrderOperationId` ở **vị trí thứ 2** — mọi call site positional
   phải sửa.
5. `TraceIdProvider` là bean mới; `InventoryMovementService`, `MaterialIssueService`,
   `ProductionReceiptService`, `ProductionExecutionService` đều nhận nó qua constructor.
6. `UserLookupService` (module `user`) là entry point mới cho cross-module resolve username —
   **batch**, không gọi từng dòng (C14).

**Chưa làm trong `F5-A`** (đã làm ở `F5-B`, xem §0.10): `/mrp/runs` → `/planning-runs`, `demandLineIds`,
`supplyType`/`exceptionState`/`messages[]`/`convertedWorkOrderId`/`settingSource`/`excludedLotCount`.

### 0.10 F5-B – Reshape Planning (ĐÃ HOÀN THÀNH 2026-07-28)

| Endpoint cũ | Endpoint mới | Ghi chú |
|---|---|---|
| `POST /mrp/runs` | `POST /planning-runs` | body thêm `demandLineIds` (tuỳ chọn) |
| `GET /mrp/runs`, `/mrp/runs/{id}`, `/{id}/requirements`, `/{id}/suggestions` | `GET /planning-runs…` | không đổi tham số |
| `POST /mrp/suggestions/{id}/approve\|reject\|convert-to-work-order` | `POST /supply-suggestions/{id}/…` | |
| `POST /mrp/suggestions/{id}/convert-to-purchase-requisition` | `POST /supply-suggestions/{id}/…` | thuộc `PurchaseRequisitionController` |

**Hệ quả cần nhớ khi code tiếp:**
1. `SupplySuggestionResponse.suggestionType` **đã đổi tên** thành `supplyType`, giá trị wire là
   `MAKE`/`BUY`. Enum lưu DB vẫn là `WORK_ORDER`/`PURCHASE_REQUISITION` — đọc qua
   `SupplySuggestionType.supplyType()`, **đừng** đổi tên constant.
2. Suggestion nay có `exceptionState` (`READY`/`WARNING`/`BLOCKED`) + `messages[]`. Item
   manufacturable thiếu BOM/routing `ACTIVE` **vẫn sinh** proposal MAKE ở trạng thái `BLOCKED` —
   trước `F5-B` những dòng đó biến mất khỏi kết quả. Convert một suggestion `BLOCKED` trả
   `MISSING_BOM`/`MISSING_ROUTING` (409). Bất biến `B58`-`B59`.
3. `MrpRequirementLine` thêm `settingSource` + `excludedLotCount` (V34) — dữ liệu **thật**, không
   phải placeholder: nguồn là `InventoryAvailabilityService.PlanningInventoryQuantity`
   (`hasItemWarehouseSetting`, `excludedLotCount`), đo bằng aggregate query mới
   `StockBalanceRepository.aggregateExcludedLotCounts`.
4. `PlanningInventoryQuantity` là **record 8 field** (thêm 2) — mọi call site positional phải sửa.
5. Hướng phụ thuộc mới: `planning → routing` (`RoutingLookupService.findItemIdsWithActiveRouting`,
   batch theo từng cấp BOM). Rule `C7`/`C14`.
6. Class `MrpController` **đã xoá**, thay bằng `PlanningRunController`.

### 0.11 F6 – Fulfillment Allocation (ĐÃ HOÀN THÀNH 2026-07-28)

Phase **cuối** của track `F*`. Đóng mắt xích cuối: work order giờ biết nó được tạo cho đơn hàng nào,
và QC giải phóng lot là thứ biến sản lượng thành số lượng đã giao của Sales Order.

| Bước | Ở đâu | Tác động |
|---|---|---|
| Tạo allocation | `POST /supply-suggestions/{id}/convert-to-work-order` | 1 `WorkOrderDemandAllocation` khi lineage dẫn về SO line; `allocatedQuantity = min(plannedQuantity, openQuantity)`; đơn hàng → `IN_PRODUCTION` |
| Fulfill | `POST /work-orders/{id}/production-receipts/{rid}/qc-disposition` (result = `AVAILABLE`) | Phân bổ theo `dueDate` → `lineNo`; tăng `fulfilledQuantity` của allocation **và** SO line; roll-up → `PARTIALLY_FULFILLED`/`FULFILLED` |
| Đọc | `GET /work-orders`, `GET /work-orders/{id}` | `allocations[]` (`salesOrderCode`, `salesOrderLineId`, `allocatedQuantity`, `fulfilledQuantity`, `uom`, `dueDate`) |

**Hệ quả cần nhớ khi code tiếp:**
1. `WorkOrderService.createFromMrp` nay có **tham số thứ 3** `salesOrderLineId` (nullable). Chỉ
   `SupplySuggestionService` gọi nó. `WorkOrderCreateRequest` **không** đổi — allocation không phải
   thứ client tự khai.
2. `WorkOrderMapper.toResponse` nay nhận **2 tham số** (`WorkOrder`, `List<…AllocationResponse>`);
   `WorkOrderResponse` có field thứ 36 `allocations`. Mọi call site positional phải sửa.
3. `ProductionReceiptService` nhận thêm `WorkOrderDemandAllocationService` qua constructor
   (**vị trí thứ 10**, ngay sau `auditorAware`).
4. `WorkOrderDemandAllocation.salesOrderLineId` là **`UUID` phẳng**, không `@ManyToOne` — giữ
   `sales → planning → workorder → sales` không thành vòng phụ thuộc compile-time. Đọc dữ liệu SO
   line qua `SalesOrderFulfillmentService` (rule `C7`).
5. **Không** thêm permission mới: fulfillment là hệ quả của `PERM_QUALITY_DISPOSITION` (spec §7.1).
6. Chỉ suggestion **level 0** có demand `SALES_ORDER_LINE` mới sinh allocation — component thừa
   hưởng `sourceDemand` của cha nên phải bị loại (bất biến `B64`).
7. Giới hạn mới: item **không** lot-tracked không fulfill được — nợ **#17** ở §0.4.

**Breaking changes:** mục 1, 2, 3 ở trên. Test cũ đã **sửa** theo `R10`, không xoá: 365 → **391**
unit, 16 → **18** IT.

### 0.12 D4 – MRP Đúng Số: Open PO Vào Netting + Run Header (ĐÃ HOÀN THÀNH 2026-07-30)

Trả nợ **#16** (defect nghiệp vụ nặng nhất còn lại) và **#15**. Không permission mới, không endpoint mới.

| Thay đổi | Ở đâu | Tác động |
|---|---|---|
| Open PO vào `scheduledReceipts` | `PurchaseOrderSupplyService` → `MrpCalculationService.loadLevelSupplySnapshot` | `net = gross + max(safetyStock, reorderPoint) − (available + openWO + **openPO**)`. Item đã có PO mở ra số nhỏ hơn hoặc **biến mất** khỏi danh sách suggestion |
| Run header spec §2.4 | `MrpRun` + `MrpRunService` + `V36` | `code` + `shortageLines` / `plannedWorkOrders` / `plannedPurchaseRecommendations` / `blockedProposals` |

**Hệ quả cần nhớ khi code tiếp:**
1. **Kết quả MRP đã đổi** — đây là sửa sai, không phải regression. Fixture MRP nào assert số cũ phải
   tính lại theo `R10`, **không** nới assertion.
2. `MrpCalculationService` nhận `PurchaseOrderSupplyService` qua constructor ở **vị trí thứ 4**
   (trước `RoutingLookupService`) — mọi test dựng service này phải sửa.
3. `MrpRun.complete(...)` nay nhận **8 tham số** (thêm 4 số summary). Call site duy nhất: `MrpRunService`.
4. `MrpRunResponse` thêm `code` (vị trí **2**) + 4 số (sau `totalSuggestionLines`) — additive trên
   wire, **positional** trong Java.
5. `MrpRun.code` sinh ở **`@PrePersist`**, không sau `save()`: Hibernate chụp snapshot entity lúc queue
   insert nên field gán sau đó **không** vào INSERT và cột NOT NULL nổ. Đây là defect thật, do
   `ProductionFlowE2EIT` bắt được — mock repository không thấy được (bất biến `B68`).
6. Hướng phụ thuộc mới `planning → purchasing`, hợp lệ theo `C7` (query service, không chạm repository).
7. **Không** tách `openWorkOrderQuantity` / `openPurchaseQuantity` thành 2 cột — spec chỉ có một số
   hạng `scheduledReceipts`. Lý do đầy đủ: `module/planning/CLAUDE.md` mục 6.

### 0.13 D5 – QC Disposition Cho Output Không Lot-Tracked (ĐÃ HOÀN THÀNH 2026-07-30)

Trả nợ **#17** — mắt xích cuối của track `F*` bị hở. Không permission mới, không endpoint mới,
**không migration**. Toàn bộ thay đổi nằm trong `ProductionReceiptService.qcDisposition`.

| Điều kiện | Trước `D5` | Sau `D5` |
|---|---|---|
| Receipt **có** lot (`line.lot != null`) | validate mọi lot ở `HOLD` → `changeLotStatus` → ghi `quality_dispositions`/lot → fulfill nếu `AVAILABLE` | **không đổi một dòng nào** |
| Receipt **không** lot | `STATE_CONFLICT` (409) | `AVAILABLE`: chỉ ghi verdict trên receipt + fulfill · `REJECTED`: verdict + **`ADJUST_OUT`** rút hàng |

**Hệ quả cần nhớ khi code tiếp:**
1. Điều kiện rẽ nhánh là **cấp dòng** (`line.getLot() != null`), **không** phải `item.isLotTracked()`.
   Đừng đi tìm cờ `lotTracked` trong đường QC — nó không nằm ở đó.
2. `dispositionedQuantity` của đường không-lot là **tổng `quantity` mọi dòng receipt**. Nguồn cũ cộng
   dồn từ lot line ⇒ ra 0 ⇒ `fulfill(...)` **no-op im lặng, không lỗi**. Đây là bẫy chính của phase;
   đã khoá bằng nghiệm thu mutation (đổi hàm trả `ZERO` ⇒ đúng 1 case đỏ).
3. `qcDisposition` tách thành 2 private method `dispositionLots` / `dispositionWithoutLots`, cả hai
   trả về "số QC vừa phán quyết". `auditorAware.getCurrentAuditor()` nay được gọi **trước** khi
   validate lot (trước đây sau) — không đổi hành vi, chỉ đổi thứ tự.
4. **Quyết định `A2`:** receipt không lot **không** ghi dòng `quality_dispositions` (bảng đó định
   nghĩa là "một dòng cho mỗi lot"; `lot_id` vẫn `NOT NULL`, không migration). Báo cáo QC query từ
   bảng đó sẽ **thiếu** những receipt này — cảnh báo đã ghi ở `module/workorder/CLAUDE.md` + javadoc
   entity. Bảng vẫn chưa có call site đọc nào trong `src/main`.
5. **Quyết định `A`** cho `REJECTED`: rút hàng bằng `movementService.adjust(...)` delta âm
   (⇒ `ADJUST_OUT` + `direction = OUT`). `ADJUST_OUT` đã có sẵn trong enum **và** trong
   `chk_stock_movements_type` ⇒ không migration. Child idempotency key: `<parent>:qc-reject:L<n>`.
6. **Edge case đã chốt:** nếu hàng đã bị issue/bán trước khi QC kịp phán quyết, `adjust` nổ
   `INSUFFICIENT_AVAILABLE_STOCK` (409) và **cả** QC disposition rollback ⇒ receipt kẹt chờ người xử
   lý. Có chủ đích: hàng lỗi đã ra khỏi kho là sự cố nghiệp vụ thật, không che bằng cách rút thiếu.
7. `AVAILABLE` trên output không lot **không** sinh movement nào — hàng đã ở tồn tự do từ lúc
   `approve`. Đừng "sửa cho giống" nhánh lot-tracked bằng cách thêm movement.

**Breaking change:** `qc-disposition` trên receipt không lot **không còn** trả `STATE_CONFLICT` (409),
nay trả 200. Đây là sửa sai. Test cũ `qcDisposition_nonLotTrackedOutput_shouldThrowStateConflict` đã
**sửa** theo `R10` (thay bằng case chứng minh hành vi mới), không xoá.

---

### 0.14 D6 – Scope Lại `Idempotency-Key` Của `stock_movements` (ĐÃ HOÀN THÀNH 2026-07-30)

Trả nợ **#10**. Không permission mới, không endpoint mới, không đổi contract wire. Migration `V37`.

| Thay đổi | Ở đâu | Tác động |
|---|---|---|
| Constraint | `V37` | `UNIQUE (idempotency_key)` → **`UNIQUE (idempotency_key, movement_type)`** |
| Query replay | `StockMovementRepository` | `findByIdempotencyKey` → **`findByIdempotencyKeyAndMovementType`** |
| 5 call site | `InventoryMovementService` | mỗi đường truyền type tường minh qua private `findReplay(key, type)` |
| Dead code | `InventoryMovementService` | **xoá** `hasMovementForIdempotencyKey` (0 call site, quyết định của user) |

**Hệ quả cần nhớ khi code tiếp:**
1. **Constraint và query là một cặp, đổi phải cùng commit** (bất biến `B69`). Đổi constraint mà không
   đổi query thì DB cho 2 dòng cùng key khác type nhưng query trả dòng **đầu tiên nó gặp** — replay
   trả nhầm chứng từ **không xác định trước**, tệ hơn hiện trạng cũ (cũ ít nhất còn deterministic).
2. **Không** scope theo `created_by` như spec §10.2: cột nullable (`V8`) và Postgres coi 2 `NULL` là
   khác nhau ⇒ mọi dòng lịch sử `created_by IS NULL` **mất hoàn toàn** bảo vệ trùng lặp. Muốn sát
   spec phải backfill trước — việc lớn hơn nợ đang trả. Quyết định của user.
3. `adjust` nay đọc `quantityDelta` **trước** khi tra replay (phải biết `ADJUST_IN`/`ADJUST_OUT` mới
   scope được). ⇒ delta = 0/null nổ `NEGATIVE_QUANTITY` **trước** `RESOURCE_NOT_FOUND` của
   item/warehouse. Thứ tự này có chủ đích, bất biến `B71`.
4. `issue` và `issueReserved` **cùng** scope `ISSUE` — có chủ đích, đừng bịa movement type để tách
   (bất biến `B70`).
5. `material_issues` / `production_receipts` / `production_executions` **vẫn** `UNIQUE` toàn bảng và
   vẫn dùng `findByIdempotencyKey` không scope. **Cố ý ngoài phạm vi**: mỗi bảng chỉ có một loại
   nghiệp vụ ghi vào nên không dùng chung key space như ledger.
6. Mock repository **không** kiểm được constraint. Bằng chứng cần **cả hai** tầng: unit test chứng
   minh *query* đã scope, `FlywayMigrationIT` chứng minh *DB* đã cho phép cặp đó. Thiếu một nửa là
   đúng kiểu lỗ hổng của nợ #8/#23 từng lọt qua.

**Breaking change:** cùng `Idempotency-Key` + **khác** `movement_type` nay **tạo chứng từ mới** thay vì
trả chứng từ cũ. Client nào (vô tình) dựa vào hành vi cũ sẽ thấy tồn kho đổi ở lần 2 — đây là **sửa
sai**, hành vi cũ là bug. `ProductionFlowE2EIT` không đổi assertion nào (child key `:L<n>`, `:approve`,
`:qc`, `:qc-reject` không bị ảnh hưởng).

---

### 0.15 D7 – Test Tầng HTTP Nhóm A + Rà 409/422 (ĐÃ HOÀN THÀNH 2026-07-30)

Trả nợ **#9 hết** và **#2 một phần**. Không permission mới, không endpoint mới, **không migration**.

| Phần | Kết quả |
|---|---|
| `D7.2` | **8 `*ControllerTest` mới** (nhóm A) — 34 case. Nhóm B+C (9 controller) hoãn sang `D7b` theo phương án `A` §6.1 của kế hoạch |
| `D7.3` | Rà **44 throw site** `OPERATION_NOT_ALLOWED` ở 4 module, phân loại **từng chỗ**. Bảng đầy đủ: `FRONTEND_ALIGNMENT_ROADMAP.md §6.1.6` |
| `D7.4` | Sửa **9** chỗ: `bom` 1, `purchasing` 8. `organization` **0**, `sales` **0** (vốn đã đúng tiêu chí) |
| `D7.5` | Sửa số controller **19 → 20** / **16 → 17** ở 4 chỗ tài liệu |

**Ba tiêu chí phân loại rút ra (dùng cho mọi module về sau, đã ghi vào `.claude/rules/error-handling.md §5.3`):**

| Kiểm tra gì | Code | HTTP |
|---|---|---|
| Đọc **status** chứng từ (`isDraft()`, `isApproved()`, `canReceive()`, `status != X`) | `STATE_CONFLICT` | 409 |
| Số lượng vượt **trần của chứng từ** (nhận vượt số đặt, duyệt vượt số xin) | `PLANNED_QUANTITY_EXCEEDED` | 409 |
| Master data (`INACTIVE`, sai `ItemType`, khác company/plant) · **chứng từ chưa có dòng nào** · dữ liệu đầu vào sai | `OPERATION_NOT_ALLOWED` | **422** |

**Hệ quả cần nhớ khi code tiếp:**
1. 🔴 **"Chứng từ chưa có dòng nào" là 422, KHÔNG phải 409.** `RoutingService.activate` (`F4`) là tiền lệ
   gốc và đã tách đúng hai nhánh này từ trước `D7`; `BomService.activateBom` được giữ 422 theo đúng nó.
   Đây là ranh giới dễ nhầm nhất — đừng "đồng bộ" nốt.
2. `ExceptionFactory.businessRule(...)` đọc như "422" nhưng thực ra chỉ lấy status từ `ErrorCode`. Chỗ
   nào ném `STATE_CONFLICT`/`PLANNED_QUANTITY_EXCEEDED` thì dùng `custom(...)` cho khớp tiền lệ `F5`
   trong `workorder` — **không** phải vì `businessRule` sai chức năng.
3. `PLANNED_QUANTITY_EXCEEDED` nay dùng cả ở **purchasing** (nhận vượt số đặt — `B27`; duyệt vượt số
   xin), không chỉ ở `workorder`. Tên nó chung ("vượt trần kế hoạch"), đừng bịa mã mới cho từng module.
4. Bất biến mới: **`B72`** (`module/purchasing/CLAUDE.md`), **`B73`** (`module/bom/CLAUDE.md`) ghi rõ
   chỗ nào 409 và chỗ nào **cố ý giữ** 422. `B11`/`B27` được viết lại kèm mã lỗi.
5. `@JsonInclude(NON_NULL)` trên **cả record** `ApiResponse` ⇒ `result` **biến mất khỏi JSON** khi null,
   không phải `"result": null` như ví dụ ở `error-handling.md §5.1` mô tả. Test `D7` assert
   `$.result` `doesNotExist()` cho DELETE/lỗi. **Chưa sửa** — đổi hành vi này là breaking change với
   FE, ngoài phạm vi `D7`; ví dụ trong tài liệu là chỗ lệch, không phải code.
6. Mỗi `@WebMvcTest` mới vẫn cần **6 `@MockBean`** hạ tầng bảo mật, và `@Import(PlantContextResolver.class)`
   khi controller nhận `X-Plant-Id` (`R3`: nó chứa logic thật, mock nó thành no-op là xoá thứ đang test).

**Nghiệm thu mutation (3 mutation, mỗi cái đúng 1 case đỏ, đã revert):**

| Mutation | Case đỏ | Chứng minh |
|---|---|---|
| `GoodsReceiptControllerTest`: stub ném `IDEMPOTENCY_CONFLICT` thay `STATE_CONFLICT` | `cancel_receiptNotPosted_returns409StateConflict` | Assert bám `$.code`, **không** phải `status()` — cả hai đều 409 |
| `ManufacturingExecutionControllerTest`: stub ném `RESERVATION_EXCEEDED` thay `PLANNED_QUANTITY_EXCEEDED` | `reportProduction_overPlannedQuantity_...` | như trên, cả hai đều 409 |
| `GoodsReceiptService`: đổi `PLANNED_QUANTITY_EXCEEDED` về `STATE_CONFLICT` | `GoodsReceiptServiceTest.postOverRemainingQuantity_...` | Tầng service cũng khoá đúng mã, không chỉ đúng status |

> ⚠️ **Cả 3 mutation đều giữ nguyên HTTP status.** Mutation đổi status (409↔422) là bài **dễ**; nó xanh
> cũng không nói gì. Phase sau kiểm lại thì phải chọn hai mã **cùng status**.

**Breaking changes** (9 endpoint đổi HTTP status 422 → 409, FE đang code theo `code` cũ sẽ hỏng —
liệt kê từng cái, không gộp):

| # | Endpoint | Điều kiện | Cũ | Mới |
|---|---|---|---|---|
| 1 | `PATCH /boms/{id}`, `POST /boms/{id}/lines`, `PATCH /bom-lines/{id}`, `DELETE /bom-lines/{id}`, `POST /boms/{id}/activate` (5 call site của `ensureDraft`; `DELETE /boms/{id}` **không** đụng vì deactivate không gọi nó) | BOM không `DRAFT` | `OPERATION_NOT_ALLOWED` 422 | `STATE_CONFLICT` 409 |
| 2 | `POST /purchase-orders/{id}/goods-receipts` | PO không `SENT`/`PARTIALLY_RECEIVED` | 422 | `STATE_CONFLICT` 409 |
| 3 | `POST /purchase-orders/{id}/goods-receipts` | nhận vượt số còn lại (`B27`) | 422 | `PLANNED_QUANTITY_EXCEEDED` 409 |
| 4 | `POST /goods-receipts/{id}/cancel` | receipt không `POSTED` | 422 | `STATE_CONFLICT` 409 |
| 5 | `POST /purchase-orders/{id}/send`, `.../cancel` | PO không `DRAFT` | 422 | `STATE_CONFLICT` 409 |
| 6 | `POST /supply-suggestions/{id}/convert-to-purchase-requisition` | suggestion không `APPROVED` | 422 | `STATE_CONFLICT` 409 |
| 7 | `POST /purchase-requisitions/{id}/approve` | duyệt vượt số xin | 422 | `PLANNED_QUANTITY_EXCEEDED` 409 |
| 8 | `POST /purchase-requisitions/{id}/convert-to-purchase-order` | PR không `APPROVED` | 422 | `STATE_CONFLICT` 409 |
| 9 | `POST /purchase-requisitions/{id}/approve\|reject\|cancel` | PR không `DRAFT` | 422 | `STATE_CONFLICT` 409 |

6 test cũ được **sửa** theo `R10` (không xoá, không nới assertion): `BomServiceTest`,
`GoodsReceiptServiceTest` ×2, `PurchaseOrderServiceTest`, `PurchaseRequisitionServiceTest` ×2.
`GlobalExceptionHandlerTest` + 3 controller test của `T2` + `ProductionFlowE2EIT`: **0 assertion bị sửa**
(chỉ đổi **tên** một method của `InventoryControllerTest` vốn ghi "422" từ trước `F5` — `R7`).

---

### 0.16 D7b – Test Tầng HTTP Nhóm B+C (ĐÃ HOÀN THÀNH 2026-07-31)

Trả **hết** nợ **#2**. Không permission mới, không endpoint mới, **không migration**, **không breaking
change** — phase này chỉ thêm test, không sửa một dòng `src/main` nào.

| Phần | Kết quả |
|---|---|
| `D7b.1` | **6 `*ControllerTest` nhóm B** (CRUD master data): `Item` (7 case), `ItemWarehouseSetting` (5), `Supplier` (7), `User` (7), `Organization` (8), `AccessControl` (7) |
| `D7b.2` | **3 `*ControllerTest` nhóm C** (read-only): `InventoryReport` (5), `Planning` (4), `PlanningDemand` (10) |
| `D7b.3` | `A4` **được ép thật** — `PageableFactory:22` (`size < 1 ? 20 : min(size, 100)`). Không phải nợ. Nay có test HTTP ở `ItemControllerTest` (size=500 ⇒ 100) và `UserControllerTest` (size=0 ⇒ 20) |
| `D7b.4` | Tài liệu: `§0.1`, `§0.4` #2 → đã trả hết + **#26 mới**, `best-practices.md` T3/T5 → 20/20, `TEST_IMPROVEMENT_PLAN.md` I3, roadmap §6.1 + §6.1.7 |

**Hệ quả cần nhớ khi code tiếp:**
1. 🔴 **Nợ #9 chưa đóng hết như `D7` tuyên bố** — `planning` chưa bao giờ được rà. 3 chỗ đọc status
   chứng từ vẫn trả 422. Chi tiết đầy đủ + danh sách chỗ **đúng 422 đừng đụng**: nợ **#26** ở §0.4.
   `PlanningDemandControllerTest.cancel_nonOpenDemand_returns422_pendingTheStateConflictDecision`
   **pin hành vi hiện tại kèm javadoc giải thích** — cùng cách `StockBalanceRepositoryIT` từng xử lý
   nợ #8. Đừng đọc test đó rồi tưởng 422 là kết luận đã chốt.
2. **Đừng assert phân trang bằng `PageableFactory.MAX_PAGE_SIZE`/`DEFAULT_PAGE_SIZE`.** Hằng số nằm ở
   cả hai vế ⇒ đổi nó thì test **vẫn xanh** (tautology, `R6`). `D7b` assert literal `100`/`20` vì đó
   là con số `best-practices.md A4` hứa với client. Bản nháp đầu của phase này mắc đúng lỗi đó và bị
   nghiệm thu mutation bắt.
3. `PUT /inventory/item-warehouse-settings` trả **200, không 201** kể cả lần ghi đầu (§5.8: PUT là
   full replace) — `POST /planning/production-estimates` cũng **200, không 201** (không persist gì).
   Cả hai đã có test; đừng "sửa cho đúng REST".
4. `PlanningDemandController` nhận `X-Plant-Id` ở **cả** `create` (body) và `list` (query param) —
   §5.6.1 áp dụng cho query string, không chỉ body. Cả hai nhánh 409 đã có test + `verifyNoInteractions`.
5. Header `X-Plant-Id` **sai định dạng** trả `FIELD_FORMAT_INVALID` (**400**), không phải
   `STATE_CONFLICT` (409) — không parse được là input sai, khác với "parse được nhưng lệch".
6. `MethodArgumentTypeMismatchException` (§5.4, thêm ở `F1`) nay được khoá trên **controller thật**
   (`PlanningDemandControllerTest`, `OrganizationControllerTest`, `InventoryReportControllerTest` cho
   enum sai) — trước `D7b` chỉ có `GlobalExceptionHandlerTest` với controller giả.
7. `GET /reports/low-stock` trả **mảng trần**, không `PageResult` — có chủ đích (một dòng cho mỗi
   ngưỡng đã cấu hình). Đừng "thêm phân trang cho đồng bộ" mà không có yêu cầu.

**Nghiệm thu mutation (3 mutation, mỗi cái đúng 1 case đỏ / 21 case chạy, đã revert):**

| Mutation | Case đỏ | Chứng minh |
|---|---|---|
| `SupplierControllerTest`: stub ném `BUSINESS_RULE_VIOLATION` thay `OPERATION_NOT_ALLOWED` | `addItemSupplier_secondPreferred_returns422OperationNotAllowed` | Assert bám `$.code`, **không** `status()` — cả hai đều **422** |
| `UserControllerTest`: stub ném `RESOURCE_ALREADY_EXISTS` thay `USERNAME_ALREADY_EXISTS` | `create_duplicateUsername_returns409UsernameAlreadyExists` | như trên, cả hai đều **409** |
| `PageableFactory` (**production code**): `MAX_PAGE_SIZE` 100 → 500 | `ItemControllerTest.list_sizeAboveMax_isClampedToHundred` | Kết luận `D7b.3` là **test-backed**, không phải đọc source rồi tin |

> ⚠️ Hai mutation đầu giữ nguyên HTTP status (đúng cảnh báo `§0.15`). Mutation thứ ba đụng
> **`src/main`** — `D7` chỉ có 1/3 loại này, `D7b` giữ tỷ lệ đó vì phase không sửa production code.

---

### 0.17 D11 – Đóng Hai Nợ Đúng-Sai Cuối Cùng (#25, #26) (ĐÃ HOÀN THÀNH 2026-07-31)

Trả nợ **#25** và **#26**. Không permission mới, không endpoint mới, **không migration**. Phase đầu
tiên của track `D*` **sửa hành vi** thay vì thêm test ⇒ coverage và số case đều **không** phải thước đo.

| Phần | Kết quả |
|---|---|
| `D11.1` | Nợ #25: `WorkOrder.canReceipt()` (= `canExecute()` **+ `COMPLETED`**) + `WorkOrderExecutionSupport.ensureReceiptable`. **`B13` tách lần thứ ba** (lần 1 ở `D9`) |
| `D11.2` | Nợ #26: 3 chỗ `planning` đọc status chứng từ đổi `OPERATION_NOT_ALLOWED` → `STATE_CONFLICT` qua `ExceptionFactory.custom` |
| `D11.3` | 3 test cũ **sửa** theo `R10` + **6 case mới**. `PlanningDemandControllerTest` gỡ javadoc "pending", nay pin **fix** thay vì pin gap |
| `D11.4` | `ProductionFlowE2EIT` **bỏ** seed safety stock = 2 của `D5` — bằng chứng nghiệm thu thật |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Phải đổi CẢ 3 call site của vòng đời receipt, không phải một.** Kế hoạch gốc chỉ ghi
   `postNew`, nhưng `ensureExecutable` gác **ba** chỗ: `postNew` (`:382`), `submit` (`:108`),
   `approve` (`:122`). Đổi mỗi `postNew` thì tạo được `DRAFT` trên WO `COMPLETED` rồi kẹt luôn ở
   `submit` — **tồn kho vẫn không đổi, bug vẫn nguyên**, chỉ dời đi một bước và khó thấy hơn.
   `issue` / `report` / WIP **giữ** `ensureExecutable` — đụng thêm là trượt phạm vi.
2. **`COMPLETED` không còn là trạng thái cuối tuyệt đối** với receipt. Nó nghĩa là "xưởng làm xong",
   không phải "hàng đã vào kho" — `completedQuantity` là cột riêng và luôn đi sau (`§0.5`). Mọi
   `switch`/so sánh `WorkOrderStatus` đã rà (`§11.3`): không có `switch` nào; `WorkOrderSupplyService`
   (open supply) và `WorkOrderService` (cancel gate) đều **cố ý** vẫn loại `COMPLETED`, đúng như trước.
3. **Trần `B16` không nới** và là thứ **duy nhất** giữ cho WO `COMPLETED` không thành cửa mở. Đừng
   chuyển check số lượng ra sau gate status — có test riêng
   (`post_onCompletedWorkOrder_stillCannotExceedWhatWasProduced`) canh đúng chỗ đó.
4. **Phương án B của nợ #25 (hoãn `complete()`) đã bị loại có lý do**, đừng quay lại: WO làm xong mà
   chưa ai nhập kho sẽ kẹt `IN_PROGRESS` **vô thời hạn**, và nó đổi nghĩa một cột đã có dữ liệu lịch sử.
5. 🔴 **Trong `SupplySuggestionService.convertToWorkOrder` có 2 check liền nhau, giờ khác mã lỗi:**
   `!isApproved()` → **409** (status chứng từ), `!= SupplySuggestionType.WORK_ORDER` → **422** (input
   sai). Đây là chỗ dễ "đồng bộ nhầm" nhất trong repo. Bất biến `B74`.
6. `D7` tuyên bố nợ #9 "đóng hết" nhưng phạm vi rà của nó là **4 module**, `planning` không nằm trong
   đó. **Bài học:** khi một phase tuyên bố đóng nợ, ghi rõ **phạm vi đã rà**, đừng ghi "hết".

**Nghiệm thu mutation (3 mutation, đã revert):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | `canReceipt()` trả lại đúng `canExecute()` (**`src/main`**) | **4** — `post_onCompletedWorkOrder_receiptsTheOutputThatIsNotWarehousedYet`, `submit_onCompletedWorkOrder_isAllowed`, `approve_onCompletedWorkOrder_...`, `post_onCompletedWorkOrder_stillCannotExceedWhatWasProduced` | Mỗi bước vòng đời có case riêng ⇒ nếu chỉ đổi `postNew` như kế hoạch gốc thì 3 case vẫn đỏ. Đây là thứ bắt được lỗ hổng ở hệ quả #1 |
| 2 | `canReceipt()` cho luôn `CANCELLED` (**`src/main`**) | **1** — `post_onCancelledWorkOrder_shouldThrow` | Nới gate không nới quá tay |
| 3 | `ensureDraft` đổi `STATE_CONFLICT` → `IDEMPOTENCY_CONFLICT` (**cả hai đều `HttpStatus.CONFLICT`**) | **1** — `SupplySuggestionServiceTest.reject_convertedSuggestion_fails` | Assert bám `ErrorCode`, **không** phải status |

> ⚠️ Mutation #3 giữ nguyên HTTP status (đúng cảnh báo `§0.15`/`§0.16`). Khác `D7b`: **2/3** mutation
> lần này đụng `src/main` — phase sửa hành vi thì phải như vậy.

**Breaking changes** *(ghi từng cái, không gộp)*:

| # | Endpoint | Điều kiện | Cũ | Mới |
|---|---|---|---|---|
| 1 | `POST /work-orders/{id}/production-receipts` **+ `/{rid}/submit` + `/{rid}/approve`** | WO đã `COMPLETED` | `STATE_CONFLICT` 409 | **200/201** (nay hợp lệ) |
| 2 | `PATCH /planning/demands/{id}/cancel` | demand không `OPEN` | `OPERATION_NOT_ALLOWED` 422 | `STATE_CONFLICT` 409 |
| 3 | `POST /supply-suggestions/{id}/convert-to-work-order` | suggestion không `APPROVED` | 422 | `STATE_CONFLICT` 409 |
| 4 | `POST /supply-suggestions/{id}/approve\|reject` | suggestion không `DRAFT` | 422 | `STATE_CONFLICT` 409 |

> #1 là **nới lỏng** (request trước đây lỗi nay thành công) ⇒ không phá client đang chạy đúng.
> #2-#4 **đổi mã** ⇒ FE bắt theo `code` cũ sẽ hỏng. Không migration ở cả bốn.

---

### 0.18 F7 – Đối Chiếu Spec FE Toàn Diện + Đóng 5 Gap (ĐÃ HOÀN THÀNH 2026-07-31)

Phase **đầu tiên** kiểm chứng spec ở mức section/field. Migration `V38`. Bản ghi đầy đủ +
**bảng đối chiếu §1..§11**: `FRONTEND_ALIGNMENT_ROADMAP.md §3.7` và **§7**.

🔴 **Vì sao 5 gap nằm ẩn suốt 5 phase — quan trọng hơn bản thân 5 gap.** `F1`–`F6` tuyên bố đóng track
`F*` mà bằng chứng phủ spec **duy nhất** là kịch bản E2E 11 bước (§11.1). Một kịch bản happy-path
không thể phát hiện một endpoint *không tồn tại*. Cộng thêm `.docx` là binary **chưa từng được commit**
(`git log` rỗng) nên không agent nào đọc được spec gốc — mỗi phase chỉ đọc lại tham chiếu `spec §N`
rải trong markdown. Kết quả: **§9 chưa từng được nhắc tới một lần nào trong toàn repo**.
⇒ `F7` trích spec ra `docs/fe-spec-omniplant.md` và lập bảng đối chiếu §1..§11.

| # | Gap | Spec § | Nội dung |
|---|---|---|---|
| 1 | `GET /production-executions/candidates` **không tồn tại** — cả một màn hình FE không có API | §5.1 | Endpoint + query + DTO lean. Tái dùng `PERM_PRODUCTION_EXECUTION_READ` ⇒ **không** permission mới. Bất biến **`B75`** |
| 2 | Rename field mới làm 4/17 DTO ⇒ FE thấy **2 từ vựng** | §6.4 | **Phương án A** (user chốt): đổi 7 DTO trên luồng spec, giữ 17 DTO ngoài luồng |
| 3 | `cancel` không nhận `reason` | §3.2 | `V38` + `WorkOrderCancelRequest`; thiếu ⇒ `APPROVAL_REASON_REQUIRED` (400) |
| 4 | timestamps optional | §5.1 | `@NotNull`. Validate **thứ tự** vốn đã có — không đụng |
| 5 | thiếu param `search` | §3.2 | Free-text trên `workOrderNo` + product SKU |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`B75` có HAI vế.** `status ∈ {RELEASED, IN_PROGRESS}` **và** `actualGoodQuantity <
   plannedQuantity`. WO đã đạt plan không nhận report được nữa (`B54`) ⇒ đưa vào danh sách là đưa
   operator một nút bấm luôn lỗi. Lọc status không thôi vẫn "trông đúng".
2. 🔴 **Cả hai vế nằm trong JPQL ⇒ mock repository không kiểm được** (`R7`). `F7` vì thế tạo
   **`WorkOrderRepositoryIT`** (class IT thứ 7). Viết unit test với mock cho vùng này là tautology.
3. **`ManufacturingExecutionController` nay CÓ nhận `X-Plant-Id`** — đúng theo chính rule §5.6.1
   (endpoint nêu `plantId` tường minh thì cross-check), không phải phá lệ. Ranh giới là theo
   **endpoint**, không theo controller; ghi chú cũ ở `error-handling.md §5.6.1` đã sửa.
4. **Ranh giới rename là phần của quyết định, không phải chuyện dở dang.** 17 DTO ngoài luồng spec
   **cố ý giữ** `itemCode`/`lotCode`. Danh sách chính xác: roadmap §3.7 hệ quả #4.
5. ⚠️ **Rename gần như không được test bảo vệ:** đổi 6 response DTO mà chỉ **một** assertion trong
   toàn suite phát hiện. Chỗ đó nay assert cả tên mới lẫn `itemCode` `doesNotExist()`.
6. **§9 đã kiểm, code hợp lệ — đừng "sửa".** §9.3/§9.4 mô tả response ghép nhiều nhánh nhưng câu cuối
   §9 **cho phép** trả entity chính rồi FE refetch, miễn commit atomically. Ràng buộc thật là §10.1.
7. `cancel_reason` **nullable**: `NULL` = "cancel trước `F7`", **không** phải "không có lý do".

**Nghiệm thu mutation (3, đã revert):** ① bỏ vế số lượng khỏi query candidates (`src/main`) ⇒
`WorkOrderRepositoryIT.findExecutionCandidates_excludesWorkOrdersThatAlreadyReachedTheirPlan`;
② bỏ `@NotNull` khỏi `actualEndedAt` (`src/main`) ⇒ case 400; ③ `APPROVAL_REASON_REQUIRED` →
`INVALID_INPUT` (**cả hai đều `BAD_REQUEST`**) ⇒ `WorkOrderServiceTest.cancel_withoutReason_*`.
**2/3 đụng `src/main`.**

**Breaking changes:** cancel **bắt buộc** body `{reason}` · production-execution **bắt buộc**
`actualStartedAt`/`actualEndedAt` · 6 response DTO đổi `itemCode`→`itemSku` / `lotCode`→`lotNumber` ·
`MaterialIssueLineRequest.lotCode`→`lotNumber` · *(Java)* `WorkOrderResponse` thêm `cancelReason` ở
**vị trí 31**. Endpoint `candidates` và `?search=` là **additive**.

**Nợ nhỏ còn mở sau `F7`** (4 khoản, không cái nào là bug): `FRONTEND_ALIGNMENT_ROADMAP.md §7.1`.

---

### 0.19 F8 – Đóng Gap Field-Level + 4 Endpoint Spec FE (ĐÃ HOÀN THÀNH 2026-07-31)

Phase **đầu tiên** đi hết từng hàng các bảng "Trường cần hiển thị" của spec. Migration `V39`.
**Không** permission mới, **không** rename, **không** breaking change trên wire. Bản ghi đầy đủ:
`FRONTEND_ALIGNMENT_ROADMAP.md §3.8`; bảng đối chiếu đã cập nhật từng hàng: **§7**.

🔴 **Vì sao phase này tồn tại.** `F7` tuyên bố "đối chiếu spec toàn diện" và đánh ✅ cho §2.4/§3.3/
§4.2/§5.2/§6.4, nhưng phạm vi thật của nó là **grep `itemCode`/`lotCode`** — nó chưa bao giờ đi hết
bảng field. Hệ quả: `GET /production-receipts/candidates` (§6.2) **không tồn tại** — đúng cùng loại
gap #1 mà `F7` vừa đóng, nằm **ngay section kế tiếp** trong cùng file spec.
⇒ **Bài học (lần thứ hai repo học nó, sau nợ #26):** khi tuyên bố đóng một nợ, ghi rõ **phạm vi đã
rà**, đừng ghi "hết".

| Nhóm | Nội dung |
|---|---|
| **A** | 4 endpoint: `GET /production-receipts/candidates` (`B76`), `GET /production-receipts?plantId=&status=`, `GET /material-issues?plantId=&workOrderId=`, `GET /production-executions?workOrderId=` |
| **B** | ~20 field: `uom` (7 DTO), `workOrderCode` (3), `itemName` (2), `operatorUsername`/`createdByUsername` (batch), `outputLotStatus`, `workOrderRemainingGoodQuantity`/`workOrderCompletionPercent`, `bomCapturedAt`/`executionCompletedAt` (**alias, không cột mới**) |
| **C** | `V39`: `work_orders.execution_started_at` + 3 cột lineage phẳng + `mrp_runs.gross_demand_quantity` |
| **D** | 4 mục **cố ý cắt** ⇒ nợ `E`–`H` (roadmap §7.1) |
| **E** | Nợ **A** đóng: test `CONCURRENT_MODIFICATION` ở **2 tầng** |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`B76` có ba vế, và vế status NGƯỢC với `B75`.** Receipt-candidate **gồm `COMPLETED`**;
   execution-candidate loại nó. Hai query trông giống nhau, trả lời hai câu hỏi khác nhau — **đừng
   copy cái này thành cái kia**. Bỏ `COMPLETED` = dựng lại nợ #25 (`D11`) ở tầng read model.
2. 🔴 **Vế "trừ receipt đang mở" phải nằm trong JPQL.** `availableToReceipt()` **không** trừ receipt
   mở (`B16` để việc đó cho caller) ⇒ mapper một mình ra số **sai**. Query giữ gate, batch
   `sumOpenQuantityByWorkOrderIds` chỉ để hiển thị đúng số đó. 2 query/trang, 0 query/dòng.
3. 🔴 **Endpoint alias phải uỷ quyền từ CONTROLLER, không từ service.** Bản nháp đầu thêm
   `ProductionExecutionService.listFlat()` gọi `this.list(...)`: self-invocation **bypass Spring
   proxy** ⇒ `@PreAuthorize` **không chạy**, endpoint thành công khai. Lỗ hổng bảo mật thật.
4. 🔴 **`X-Plant-Id` theo ENDPOINT, không theo controller — phase thứ BA chạm bẫy này.**
   `ManufacturingExecutionController` nay chứa **cả hai loại cùng lúc**: 3 endpoint plant-scope **có**
   cross-check, endpoint thứ tư (định danh bằng aggregate) **cố ý không**. Cả hai phía đã pin bằng
   test (409 vs 200). Chi tiết: `error-handling.md §5.6.1`.
5. **`F8` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi** (`C10` không kích hoạt).
   Ghi tường minh để người đọc sau không tưởng là bị quên. `V17` seed đúng **một** permission/loại
   chứng từ, mô tả nguyên văn *"Post **and read** … documents"*, nên 4 endpoint đọc dùng `MANAGE` sẵn có.
   🔴 Hệ quả chấp nhận: user chỉ có `PERM_QUALITY_DISPOSITION` **không** mở được danh sách receipt.
   Sửa đúng chỗ là **role seed** — quyết định nới quyền, ngoài phạm vi `F8`.
6. **Nới `@EntityGraph` TRƯỚC khi thêm field** (ràng buộc thứ tự cứng duy nhất của phase).
   `ProductionExecutionRepository` chỉ fetch `operation`; mọi field mới đi qua `getWorkOrder()`. Làm
   ngược ⇒ N+1 trên endpoint phẳng, và first-level cache của endpoint **nested** (mọi dòng chung 1 WO)
   **che mất** lỗi khi test.
7. **Hai số Summary đặt trên `ProductionExecutionResponse`, KHÔNG trên `WorkOrderResponse`** — ở đó
   `workOrderRemainingGoodQuantity` (planned − **actualGood**) sẽ nằm cạnh `remainingQuantity`
   (planned − **completed**), hai con số khác hẳn.
8. **`bomCapturedAt`/`executionCompletedAt` KHÔNG có cột** — là alias của `createdAt`/`completedAt`.
   Bất đối xứng với `routing_captured_at` **có lý do** (routing tuỳ chọn, có thể vắng); đừng "thêm cột
   cho đồng bộ".
9. **`grossDemandQuantity` chỉ cộng `level == 0`** — cấp sâu hơn được dẫn xuất qua BOM, cộng hết mọi
   cấp thì cùng một nhu cầu bị đếm một lần cho mỗi cấp.

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main`**):** ① bỏ số hạng "trừ receipt đang mở"
khỏi query candidates ⇒ `WorkOrderRepositoryIT.findReceiptCandidates_excludes…ClaimedByAnOpenDraft`;
② batch nhầm `workOrder.getCreatedBy()` thay `getOperatorUserId()` ⇒
`ProductionExecutionServiceTest.list_resolvesTheOperatorUsername…` (**HTTP vẫn 200**, payload sai âm
thầm); ③ `CONCURRENT_MODIFICATION` → `STATE_CONFLICT` (**cả hai đều 409**) ⇒
`GlobalExceptionHandlerTest.optimisticLockConflict_*`; ④ `completionPercent` `HALF_UP` → `FLOOR` ⇒
`WorkOrderTest.completionPercent_roundsToTwoDecimalsHalfUp`. Mỗi mutation làm đỏ **đúng 1** case.

**Breaking changes — wire: KHÔNG có cái nào** (thuần additive, FE đang chạy không hỏng).
**Java positional: có** — 9 record thêm component; `WorkOrderService.createFromMrp` 3 → **4** tham số;
`MrpRun.complete(...)` 8 → **9**; 3 service đổi constructor;
`ManufacturingExecutionMapper.toResponse(...)` của execution/issue nhận thêm `Map<UUID,String>`.
Test cũ **sửa** theo `R10`, không xoá, không nới assertion.

> 🔴 **Đính chính (thêm ở `F9`, cùng ngày):** `F8` **để sót 2 field** và — nghiêm trọng hơn — **tuyên
> bố quá phạm vi nó thực sự đã rà**. Nó ghi "Đã đi hết bảng field" vào roadmap §7 hàng §3.3/§5.2 trong
> khi thực tế bám **danh sách 7 DTO trong kế hoạch của chính nó**, không bám bảng spec. Hai field thiếu
> (`uom` trên execution-candidate, `reservedQuantity` trên component line) đóng ở **`F9`, §0.20**.
> Đây là **lần thứ ba** repo mắc lỗi "tuyên bố đóng rộng hơn phạm vi đã rà" — xem bảng ở §0.20.

---

### 0.20 F9 – Hai Field Cuối + Sửa Tuyên Bố Nói Quá Của `F8` (ĐÃ HOÀN THÀNH 2026-07-31)

**Không migration**, không permission, không rename, không breaking change trên wire.
Bản ghi đầy đủ: `FRONTEND_ALIGNMENT_ROADMAP.md §3.9`.

🔴 **Lý do phase tồn tại quan trọng hơn 2 field nó đóng — đây là LẦN THỨ BA repo mắc cùng một lỗi:**

| Lần | Tuyên bố | Phạm vi thật | Hệ quả |
|---|---|---|---|
| 1 | `D7`: nợ #9 "**đóng hết**" | 4 module; `planning` chưa bao giờ nằm trong đó | nợ #26 → `D11` |
| 2 | `F7`: "**đối chiếu spec toàn diện**", ✅ cho 5 section | grep `itemCode`/`lotCode` | 5 gap → `F8` |
| 3 | `F8`: "**Đã đi hết bảng field**" | Bám **danh sách 7 DTO trong kế hoạch của chính nó**, không bám bảng spec | 2 field → `F9` |

⇒ **Quy tắc bắt buộc từ nay:** khi cập nhật roadmap §7, ghi **đã rà tới đâu** vào cột Ghi chú.
**Không bao giờ ghi "xong"/"hết"/"toàn diện".** Và khi kế hoạch phase liệt kê một danh sách field,
danh sách đó là **gợi ý**, bảng spec mới là **nguồn** — `F8` sai đúng ở chỗ đảo ngược hai thứ này.

| # | Field đã đóng | Spec | Ghi chú |
|---|---|---|---|
| 1 | `uom` trên `ProductionExecutionCandidateResponse` | §5.2 "Context" (ô 4/4) | **0 query** (`productItem` vốn đã `join fetch`). `F8` **đã nhìn thấy ô này và cố ý bỏ qua** |
| 2 | `reservedQuantity` trên `WorkOrderComponentLineResponse` | §3.3 "Requirement" (5/6 → 6/6) | Trước `F9` chỉ có ở `/material-readiness` ⇒ FE gọi **2 API cho một bảng** |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`reservedQuantity` phải là ĐÚNG CÙNG số của `/material-readiness`**
   (`sumActiveRemaining*` = `sum(quantity − consumedQuantity)` của reservation `ACTIVE`). Hai màn hình
   cùng tên field, khác số, là tệ hơn thiếu field. Javadoc `@link` hai chiều đã ghi: đổi thì đổi cả hai.
2. 🔴 **Batch phải NGOÀI `page.map(...)`, và CẢ HAI đường của `WorkOrderService` đều cần.**
   `toResponse(WorkOrder)` là chỗ **mọi** response 1-work-order đi qua (9 call site). Chỉ sửa `list`
   thì đường mutation trả `null` ⇒ FE thấy field lúc có lúc không.
3. **`sumActiveRemainingByWorkOrderIds` tái dùng `ComponentQuantityProjection` KHÔNG đổi**, không mang
   `workOrderId` — `componentLineId` duy nhất toàn cục.
4. **`group by` không trả hàng cho line không có reservation** ⇒ `getOrDefault(..., ZERO)`.
5. **`childBom` (§3.3) là nợ `I`** — WO snapshot direct-only theo `B12`, MRP nổ cấp sâu thành WO riêng.
   Không phải field quên (user chốt).
6. **`F9` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi.**

**Nghiệm thu mutation (3, đã revert — 3/3 đụng `src/main`):** ① bỏ `status = ACTIVE` khỏi query batch
⇒ `MaterialReservationRepositoryIT.sumActiveRemainingByWorkOrderIds_countsActiveReservationsOnly`;
② `toCandidateResponse` truyền `getName()` thay `getUnit()` ⇒ `ProductionExecutionServiceTest`
(**HTTP vẫn 200**; controller test **không** đỏ vì nó stub service — đúng phân vai); ③ chuyển batch
**vào trong** `page.map(...)` ⇒ `WorkOrderServiceTest.list_resolvesReservedQuantityInOneBatchQuery…`
(output **byte-identical**, chỉ số query đổi).

**Breaking changes — wire: KHÔNG có.** Java positional: 2 record +1 component,
`WorkOrderMapper.toResponse` +1 tham số, `WorkOrderService` constructor +1
(`MaterialReservationRepository`). Test cũ **sửa** theo `R10`.

---

### 0.21 F10 – Ba Nợ Field-Level Cuối Của Spec FE (`G`, `F`, `H`) (ĐÃ HOÀN THÀNH 2026-08-01)

Migration **`V40`**. **Không** permission mới, **không** rename, **không** breaking change trên wire.
Bản ghi đầy đủ: `FRONTEND_ALIGNMENT_ROADMAP.md §3.10`.

**Phạm vi do user chốt (2026-08-01):** đóng nợ **`G`** + **`F`** + **`H`** ở roadmap §7.1 — cả ba đều
là *"backend đã có dữ liệu, chỉ thiếu chỗ chứa"*. **Nợ `E` cố ý loại khỏi phạm vi** (`BomHeader` không
có cột `code`, và `outputQuantity` đổi **công thức nổ BOM** ⇒ đổi số MRP lẫn component line của mọi WO
— rủi ro khác hẳn).

| Nợ | Đóng bằng | Bất biến |
|---|---|---|
| **G** | `mrp_requirement_lines.projected_available_quantity` — **nullable, KHÔNG backfill**. Giá trị là `remainingCoverage` mà `MrpCalculationService:78-88` vốn **đã tính rồi vứt đi** | **`B77`** |
| **F** | `supply_suggestions.source_routing_code`/`_version` + `RoutingLookupService.findActiveRoutingSummaries` (**thay** `findItemIdsWithActiveRouting`) | **`B78`** |
| **H** | `material_issues.code` (`MI-`) + `production_executions.code` (`PE-`), backfill + `UNIQUE`, sinh ở `@PrePersist`; execution `status` = **hằng `"POSTED"`, không cột** | **`B79`** |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Nợ `G` không phải "thiếu field" mà là "derive ra SỐ SAI".** Số bị trừ (`consumedCoverageByItem`)
   chỉ sống trong **một** lần chạy calculate và chưa bao giờ persist ⇒ khi một item nằm trên **nhiều**
   requirement line, mapper cộng `available + openSupply` báo số **lớn hơn thực tế** và phá đẳng thức
   `net = max(0, gross + safety − projected)` mà FE dùng để tự kiểm.
2. 🔴 **`NULL` ≠ 0, và KHÔNG được backfill.** `NULL` = "run chạy trước `V40`". Backfill bằng
   `available + openSupply` là ghi **vĩnh viễn** đúng con số sai mà cột này sinh ra để thay thế.
3. 🔴 **`findItemIdsWithActiveRouting` bị THAY, không phải bổ sung** — `keySet()` của map mới trả lời
   luôn câu "có routing `ACTIVE` không?". Giữ cả hai = **2 query cho cùng dữ liệu** mỗi cấp BOM (`C14`).
4. 🔴 **`@PrePersist`, không gán code sau `save()`**, và **công thức Java phải khớp từng ký tự với
   backfill SQL** — lệch một ký tự thì dòng lịch sử mang mã không truy ngược về id được, và `UNIQUE`
   **không** bắt được. Đây là bài học `§0.12` #5 (`B68`) áp dụng lần thứ hai.
5. **Prefix `PE-` lệch spec có chủ đích** — §5.2 gọi "Mã WIP" nhưng `wip_transactions` là bảng ledger
   khác và không có `code`.
6. **`status` của execution KHÔNG thành cột** — không có đường huỷ/đảo nên cột một-giá-trị là
   speculative (`§11.5`), cùng lối tư duy alias `bomCapturedAt`/`executionCompletedAt` của `F8`.
7. **Số MRP không đổi một chữ số nào** — phase chỉ *lưu lại* con số netting vốn đã tính.
8. **`F10` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi** (`C10` không kích hoạt).

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Persist `available + openSupply` thay `remainingCoverage` | **1** — `MrpCalculationServiceTest.calculate_sameItemOnTwoRequirementLines_reportsTheCoverageLeftForTheSecondLineNotTheGrossAvailable` | Case đẳng thức (`.calculate_projectedAvailableIsTheFigureTheNettingUsed`) **vẫn xanh** — 1 line thì hai công thức trùng nhau ⇒ phải có **cả hai** case mới khoá được |
| 2 | Bỏ `@PrePersist` khỏi `MaterialIssue.assignCode()` | `MaterialIssueRepositoryIT` (5 case error) | `NOT NULL` nổ ngay ở INSERT — đúng failure mode javadoc mô tả; mock repository **không** thấy được |
| 3 | Bỏ vế `suggestionType == WORK_ORDER` khỏi `carriesRouting` | **1** — `.calculate_makeProposal_freezesTheActiveRoutingCodeAndVersion_buyProposalCarriesNone` | 🔴 **Lần chạy đầu XANH** ⇒ lộ ra test bản nháp assert `null` **vì lý do sai** (component không có routing để mang). Test đã sửa: stub cấp routing cho **cả** component |
| 4 | Prefix `MI-` → `MX-` **chỉ trong Java**, giữ SQL | **2** — `FlywayMigrationIT.migrate_v40_backfills…` + `MaterialIssueRepositoryIT.persist_withoutACode_…` | Canh đúng chỗ Java↔SQL lệch nhau; assert bằng **literal**, không bằng `assignCode()` (tautology `D7b.3`) |

> 🔴 **Mutation #3 là kết quả giá trị nhất của phase** — nó chứng minh vì sao "nghiệm thu mutation" khác
> "chạy test cho xanh": assertion `isNull()` xanh **không** có nghĩa là nhánh đó được khoá.

---

### 0.22 D8a – Refresh Token Reuse Detection (RTR) (ĐÃ HOÀN THÀNH 2026-08-03)

**Không migration** (thuần Redis), **không** permission mới, **không** đổi request/response DTO.
Phase đầu tiên của track `D8` — và là phase `D*` thứ hai **sửa hành vi** thay vì thêm test (sau `D11`).
Trả **1/3** nợ #6. Thiết kế gốc: `common/security/CLAUDE.md` §4.12. Bất biến: **`B80`**.

**Vấn đề đã đóng:** trước `D8a`, refresh token cũ bị đánh cắp rồi dùng lại sau khi đã rotate chỉ trả
`REFRESH_TOKEN_EXPIRED` — **giống hệt** hết hạn tự nhiên. Server không có cách nào phân biệt sự cố
bảo mật với hoạt động bình thường.

| Thay đổi | Ở đâu |
|---|---|
| `markRefreshTokenUsed` / `wasRefreshTokenUsed` + key `auth:refresh:used:{tokenId}` TTL 60s | `TokenStoreService` |
| Tách nhánh `stored == null` khỏi nhánh mismatch; nhánh reuse force-logout + audit + throw | `AuthService.refresh` |
| `TOKEN_REUSE_DETECTED` (401) | `AuthErrorCode` |
| `SUSPICIOUS_TOKEN_REUSE` | `AuditAction` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Thứ tự Redis khi rotate là lưu-mới → mark-used → xoá-cũ, và cả ba bước đều bắt buộc.**
   Marker "used" phải tồn tại **trước khi** key cũ biến mất — nếu không, một request reuse rơi đúng
   khoảng hở đó đọc thấy `stored == null` **và** `used` chưa có ⇒ bị chẩn đoán nhầm thành hết hạn
   bình thường, **bỏ lọt tín hiệu tấn công**. Và **không** được bỏ bước xoá riêng: không xoá thì token
   cũ vẫn `stored != null`, rotation coi như vô hiệu (token cũ sống tới hết TTL 7 ngày).
2. 🔴 **RTR chỉ áp dụng nhánh `stored == null`.** Nhánh `stored != null` nhưng giá trị mismatch
   **giữ** `REFRESH_TOKEN_EXPIRED` — tokenId còn sống nghĩa là nó **chưa từng** bị rotate away, đó là
   giá trị sai/bị sửa chứ không phải replay. Có test `never()` canh đúng chỗ này.
3. 🔴 **Key `used` cố ý KHÔNG có segment `{userId}`.** Nó phải nằm **ngoài** pattern SCAN
   `auth:refresh:{userId}:*` của `deleteAllUserTokens`, nếu không chính thao tác force-logout sẽ xoá
   mất cái marker chứng minh có reuse. Có test canh (`markRefreshTokenUsed_keyIsNotSweptBy…`).
4. **Force-logout gọi CẢ `deleteAllUserTokens` LẪN `deleteAllDeviceSessions`** — thiết kế §4.12 chỉ
   ghi cái đầu; `D8a` gọi cả hai cho khớp `logoutAll()`.
5. **Hai giới hạn đã biết, CHẤP NHẬN — đừng tự mở rộng phạm vi để "sửa":** (a) race 2 request refresh
   đồng thời cùng 1 token hợp lệ không giải được bằng thứ tự thao tác, cần lock/CAS mà thiết kế không
   có; (b) hệ quả là client double-submit (network retry) có thể bị force-logout **oan** — giới hạn cố
   hữu của RTR cơ bản không có grace window.
6. **`D8a` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi** (`C10` không kích hoạt).

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Bỏ nhánh `if (wasRefreshTokenUsed(...))`, luôn nhảy thẳng `REFRESH_TOKEN_EXPIRED` | `refresh_reusedToken_throwsTokenReuseDetectedAndForceLogoutAll` (assertion dòng 263) | **HTTP vẫn 401** ở cả hai mã ⇒ assert phải bám `ErrorCode`, không bám status. ⚠️ Case thứ hai (`refresh_storedTokenNull_*`) cũng đỏ nhưng **do `UnnecessaryStubbing`**, không phải kill hành vi — đừng tính là bằng chứng |
| 2 | Đổi thứ tự thành mark-used → xoá-cũ → lưu-mới | **1** — `refresh_validToken_rotatesAndReturnsNewPair` (`InOrder`) | Response **byte-identical**; chỉ assertion thứ tự bắt được. Đây là mutation giá trị nhất của phase |
| 3 | Bỏ `deleteAllDeviceSessions` khỏi nhánh reuse | **1** — `refresh_reusedToken_*` (dòng 268) | Mã lỗi **và** HTTP status đều không đổi ⇒ chỉ `verify` side-effect bắt được (`R4`) |
| 4 | Cho nhánh mismatch cũng gọi `wasRefreshTokenUsed` | **1** — `refresh_storedTokenMismatch_throwsRefreshTokenExpired` (`never()` vi phạm) | Khoá ranh giới ở hệ quả #2 |

**Breaking changes — wire: KHÔNG có** (thuần additive: 1 mã lỗi mới, DTO không đổi).
**Java positional: không có.** **Hành vi:** reuse token cũ nay **buộc đăng xuất toàn bộ thiết bị**
thay vì chỉ báo hết hạn — đó là mục đích của tính năng, không phải tác dụng phụ.

---

### 0.23 D8b – Absolute Session Timeout (ĐÃ HOÀN THÀNH 2026-08-03)

**Không migration** (thuần Redis), **không** permission mới, **không** đổi request/response DTO.
Phase thứ hai của track `D8`. Trả **2/3** nợ #6. Thiết kế: `common/security/CLAUDE.md` §4.15.
Bất biến: **`B81`**.

**Vấn đề đã đóng:** refresh token TTL 7 ngày **sliding** ⇒ user active liên tục thì phiên **không
bao giờ** kết thúc. Rủi ro `S13` đã ghi trong `best-practices.md §8.1` từ lâu, chưa có dòng code nào.

| Thay đổi | Ở đâu |
|---|---|
| `saveSessionStart` / `getSessionStart` + key `auth:refresh:{userId}:{tokenId}:meta` TTL 7d | `TokenStoreService` |
| `deleteRefreshToken` xoá **cả** key `:meta` (tránh orphan) | `TokenStoreService` |
| `login` stamp session start; `refresh` check `B81` + carry-forward | `AuthService` |
| `absoluteSessionTimeoutMs` (component thứ 4) + `app.jwt.absolute-session-timeout-ms: 2592000000` | `JwtProperties`, `application.yml`, `application-dev.yml` |
| `SESSION_ABSOLUTE_TIMEOUT` (401) · `AuditAction.SESSION_ABSOLUTE_TIMEOUT` | `AuthErrorCode`, `AuditAction` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Carry-forward khi rotate là bất biến, không phải chi tiết triển khai.** Rotate phải mang
   **nguyên** `sessionCreatedAt` cũ sang `newTokenId`. Stamp `now` ở đó ⇒ đồng hồ absolute reset mỗi
   15 phút ⇒ **tính năng thành no-op hoàn toàn**, trong khi response **byte-identical**, mã lỗi và
   HTTP status không đổi, **coverage không đổi**. Chỉ assertion đối số bắt được (mutation #1).
2. 🔴 **Vị trí check: SAU validate `stored`, TRƯỚC rotate.** *Sau* — caller không cầm token hợp lệ
   thì không được biết gì về tuổi phiên. *Trước* — phiên hết hạn tuyệt đối **không được** nhận cặp
   token mới. Đặt sau rotate thì vẫn ném đúng mã lỗi, đúng 401, chỉ khác là Redis đã có cặp mới —
   chỉ `verify(never())` bắt được (mutation #2).
3. 🔴 **Key `:meta` cố ý NẰM TRONG pattern SCAN `auth:refresh:{userId}:*`** ⇒ `deleteAllUserTokens`
   quét luôn, **không cần** sửa method đó. Đây là yêu cầu **NGƯỢC** với key `auth:refresh:used:`
   của `D8a` (cố ý nằm **ngoài**, để force-logout không xoá mất marker chứng minh reuse). Hai key,
   hai yêu cầu trái nhau, cùng namespace — mỗi cái có một test canh đúng chiều của nó, đặt cạnh nhau
   trong `TokenStoreServiceTest`. **Đừng "đồng bộ" một trong hai.**
4. **Phiên không có stamp (login trước `D8b`) là fail-open** — coi như bắt đầu **từ bây giờ**, không
   phải "đã hết hạn". Fail-closed sẽ đăng xuất **toàn bộ** user đang online ngay lúc deploy mà không
   tăng bảo mật (refresh TTL 7 ngày ⇒ trong một tuần mọi phiên sống đều có stamp). Quyết định này
   **có test + mutation riêng**, không để ngầm.
5. **Chốt companion key, KHÔNG phải JSON payload** như thiết kế gốc §4.15 mô tả (user chốt
   2026-08-03). Nhờ vậy `saveRefreshToken`/`getRefreshToken`/toàn bộ `B80` **không đổi một dòng nào**
   — `InOrder` của `D8a` vẫn đúng vì nó chỉ ràng buộc thứ tự **tương đối** giữa 3 lời gọi đã verify.
6. **Giới hạn đã biết, chấp nhận:** timeout chỉ được đánh giá **khi refresh**. Access token đang cầm
   (TTL 15 phút) vẫn dùng được tới hết hạn dù phiên vừa vượt mốc — cửa sổ tối đa bằng đúng
   access-token TTL. Đóng nó cần kiểm ở `JwtAuthenticationFilter` (mỗi request thêm một lượt đọc
   Redis), không tương xứng rủi ro.
7. **`D8b` KHÔNG thêm permission ⇒ `docs/roles-and-permissions.md` KHÔNG đổi** (`C10` không kích hoạt).
8. **Sửa mâu thuẫn tài liệu ↔ code về session** (user chốt): `architecture-decisions.md` +
   `common/security/CLAUDE.md §4.10` từng ghi "cho phép nhiều thiết bị đăng nhập đồng thời", nhưng
   `AuthService.login` xoá sạch mọi phiên cũ mỗi lần login ⇒ code là **single-session**. **Sửa tài
   liệu theo code, KHÔNG đổi hành vi** — bỏ 2 dòng `deleteAll*` là quyết định bảo mật riêng.

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Carry-forward stamp `Instant.now()` thay vì start cũ | **1** — `refresh_carriesTheOriginalSessionStartForwardToTheNewTokenId` | 🔴 **Giá trị nhất của phase.** Response byte-identical, mã lỗi/status/coverage đều không đổi, tính năng thành no-op. Chỉ assertion **đối số** bắt được |
| 2 | Dời check timeout xuống **sau** rotate | **1** — `refresh_sessionOlderThanAbsoluteTimeout_…` (`NeverWantedButInvoked` ở `never().saveRefreshToken`) | Mã lỗi **và** HTTP status đều không đổi ⇒ chỉ `verify` side-effect bắt được (`R4`). ⚠️ 2 case khác cũng đỏ nhưng **do mock `jwtProperties` trả 0 mặc định**, không phải kill hành vi — đừng tính là bằng chứng |
| 3 | `SESSION_ABSOLUTE_TIMEOUT` → `REFRESH_TOKEN_EXPIRED` (**cả hai đều 401**) | **1** — `refresh_sessionOlderThanAbsoluteTimeout_…` | Assert bám `ErrorCode`, **không** bám status — đúng cảnh báo §0.15 |
| 4 | Nhánh legacy dùng `Instant.EPOCH` thay `now` (fail-closed) | **1** — `refresh_sessionWithoutStartStamp_isTreatedAsStartingNow` | Quyết định fail-open là **test-backed**, không phải chỉ văn xuôi |

**Breaking changes — wire: KHÔNG có** (thuần additive: 1 mã lỗi mới trên `POST /auth/refresh`,
DTO không đổi). **Java positional: có** — `JwtProperties` 3 → **4** component; 2 test class đã **sửa**
theo `R10` (`TokenStoreServiceTest`, `JwtTokenProviderTest`). **Hành vi:** phiên quá 30 ngày buộc
login lại dù user vẫn active — đó là **mục đích** tính năng.

---

### 0.24 Bugfix Từ Log Production: `lower(bytea)` + 500 Do Thiếu Query Param (2026-08-04)

**Không** phase, **không** migration, **không** permission, **không** đổi contract. Ba lỗi do user báo
từ log thật, không phải từ đối chiếu spec — đây là **lần đầu** repo sửa bug phát hiện bằng đường đó.

| # | Lỗi | Chỗ sửa |
|---|---|---|
| 1 | `GET /plants/{plantId}/work-orders` trả **500** khi **không** có `?search=` | `WorkOrderRepository.search` — `cast(:search as string)` |
| 2 | `GET /suppliers` có **y nguyên** lỗi #1 (chưa ai gọi tới nên chưa lộ) | `SupplierRepository.search` — cùng cách |
| 3 | `GET /inventory/movements` thiếu `warehouseId` trả **500 `INTERNAL_SERVER_ERROR`** | `GlobalExceptionHandler` — handler thứ **7** cho `MissingServletRequestParameterException` ⇒ **400 `VALIDATION_ERROR`** + `errors[{field}]` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Parameter `String` nullable trong JPQL BẮT BUỘC `cast(... as string)` khi nó đi vào `concat`/
   `like`.** Bind `null` không kèm JDBC type ⇒ Postgres phải suy type cho `'%' || ? || '%'` từ một
   mình parameter, chọn overload **`bytea || bytea`**, rồi chết ở **parse time**:
   `function lower(bytea) does not exist`. Nhánh short-circuit `:search is null` **không cứu được** —
   Postgres định type toàn bộ expression **trước khi** evaluate bất cứ thứ gì. Đây là lỗi **toàn bộ
   endpoint 500**, không phải lọc sai.
2. 🔴 **`grep "concat('%'"` trước khi viết query search mới.** Hai chỗ duy nhất trong repo dùng pattern
   này thì **cả hai** đều dính — nó là lỗi copy-paste của pattern, không phải sai sót một lần.
3. 🔴 **Lỗi này mock repository không thấy được** (`R7`) và **coverage không thấy được** — dòng vẫn
   được "phủ" bởi unit test dùng mock. Chỉ chạy JPQL thật mới bắt: `WorkOrderRepositoryIT` **đã có**
   case đúng chỗ (`search_withoutATermReturnsEveryWorkOrderOfThePlant`) và **đang đỏ** suốt từ `F7` —
   nghĩa là lỗ hổng thật không phải thiếu test mà là **đọc kết quả build không kỹ** (xem đính chính ở
   hàng "Baseline test" §0.1). `SupplierRepositoryIT` (**class IT thứ 11**) là guard mới cho #2.
4. **Thiếu `@RequestParam` bắt buộc là 400, không phải 500.** `F1` thêm 5 handler vào
   `error-handling.md §5.4` để một UUID sai định dạng không thành lỗi server, nhưng
   `MissingServletRequestParameterException` **bị sót**. Trả 500 khiến FE không phân biệt được "tôi
   gọi sai" với "backend chết", và làm alert 5xx nổ oan.
5. **`MissingRequestHeaderException` cố ý KHÔNG có handler** — mọi `@RequestHeader` trong repo đều
   `required = false` (`Idempotency-Key`, `X-Plant-Id`) nên nó **không có đường ném**; thêm handler là
   code speculative (`§11.5`). Có header bắt buộc đầu tiên thì **đó** mới là lúc thêm.
6. **`docs/api-guide-for-frontend.md` dòng "Lịch sử movement" trước đây ghi trống query param** trong
   khi dòng ngay trên nó ghi rõ `balances?warehouseId=` (bắt buộc) ⇒ FE gọi thiếu là hệ quả trực tiếp
   của tài liệu, không phải FE tự sai. Đã sửa. Đúng cảnh báo 🔴 ở "BẢN ĐỒ TÀI LIỆU": file đó là thứ FE
   đang dùng để wire API.

**Nghiệm thu mutation (2, đã revert — **2/2 đụng `src/main`**):** ① bỏ `cast` khỏi
`SupplierRepository.search` ⇒ **2** case đỏ (`search_withoutAKeywordReturnsEverySupplierOfTheCompany`,
`...StillHonoursTheStatusFilter`) — 2 case có keyword vẫn xanh, đúng như dự đoán, nên **phải có case
keyword `null`** mới khoá được; ② đổi `@ExceptionHandler` sang exception khác ⇒ **1** case đỏ
(`GlobalExceptionHandlerTest.missingRequiredQueryParam_returns400NamingTheParameter`, `500 != 400`).

**Coverage: KHÔNG đo lại** — bugfix 3 dòng production code, và số coverage ở §0.1 vẫn là số đo sau
`D8b`. Đừng đọc nó như đã tính phần này.

**Breaking changes — wire: KHÔNG có.** #1/#2 là endpoint trước đây **luôn 500** nay hoạt động; #3 đổi
**500 → 400** cho request vốn đã sai (client đang chạy đúng không bị ảnh hưởng; client bắt 5xx để retry
thì nay thấy 400 — đó là **sửa sai**).

---

### 0.25 C2-5 – CORS + Dữ Liệu Test Multi-Plant + Sửa Lệch RBAC Seed↔Tài Liệu (2026-08-04)

Phase thứ hai của track **`C2-*`** (đóng gap API cho Capstone 2 theo `BACKEND_CAPSTONE2_API_GAPS.md`;
phase đầu là `C2-0` — xem `docs/capstone2-api-gap-response.md`). Migration **`V41`**.
**Không** endpoint mới, **không** đổi DTO, **không** breaking change trên wire.

| Phần | Nội dung |
|---|---|
| `C2-5a` | **CORS** — `CorsProperties` (`app.cors.*`) + `corsConfigurationSource()` trong `SecurityConfig`. Trước phase này repo có **0 dòng** cấu hình CORS |
| `C2-5b` | **`src/main/resources/db/dev-seed.sql`** — 1 company + 2 plant + 6 warehouse + 3 account (`manager.a`, `operator.a`, `manager.b`), scope theo plant. **Chạy tay, KHÔNG phải migration** |
| `C2-5c` | **`V41`** — cấp 10 permission lõi cho `MANAGER`, 6 cho `OPERATOR` theo `docs/roles-and-permissions.md` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Defect nặng nhất của phase không nằm trong kế hoạch: seed RBAC cấp 12 permission lõi cho
   riêng `ADMIN`.** Quy luật: seed **sớm** (`V7`–`V15`) chỉ ghi `WHERE r.code = 'ADMIN'`; seed **sau**
   (`V17`+) ghi `IN ('ADMIN','MANAGER','OPERATOR')`. Hệ quả đo được: login `manager.a` (scope PLANT-A)
   gọi `GET /plants/{A}/work-orders` trả **403 `PERMISSION_DENIED` ngay trên plant của chính nó** ⇒
   **mọi account không phải `admin` đều vô dụng** với các màn hình lõi. Đây là khoản nợ `F8` đã ghi và
   cố ý hoãn ("*sửa đúng chỗ là role seed — quyết định nới quyền, ngoài phạm vi F8*"), nay đóng bằng
   `V41` theo quyết định của user: **tài liệu là nguồn, code là chỗ trôi**.
2. 🔴 **`PermissionCatalogTest` không bắt được lỗi này, và đó là bài học.** Nó chỉ chứng minh mọi
   `PERM_*` trong `@PreAuthorize` **có dòng trong bảng `permissions`** — "tồn tại" và "được cấp cho
   role nào" là **hai sự thật khác nhau**, sự thật thứ hai chỉ nằm ở `role_permissions` và chỉ DB thật
   trả lời được. 3 test mới ở `FlywayMigrationIT`:
   - `migrate_v41_leavesNoCorePermissionGrantedToAdminAlone` — bất biến hình dạng *"mọi permission phải
     dùng được bởi ai đó không phải superuser, trừ khi khai báo tường minh là cấu hình hệ thống"*.
     Tập admin-only **đầy đủ** sau `V41` là **đúng 2**: `PERM_ORG_MANAGE`, `PERM_ACCESS_MANAGE`.
   - `migrate_v41_grantsManagerTheCorePermissionsTheRolesDocPromises` — checklist **dương** cho MANAGER.
     Cần cả hai: test trên nhận "MANAGER **hoặc** OPERATOR" nên một mình nó không thấy quyền rơi sai vế.
   - `migrate_v41_keepsOperatorOutOfApprovalAndConfigurationPermissions` — separation of duties viết
     dưới dạng **cấm**, phần mà checklist dương không bao giờ diễn đạt được.
3. 🔴 **`dev-seed.sql` KHÔNG được biến thành migration, và có HAI lý do — lý do thứ hai mới là lý do
   quyết định.** (a) Migration dưới `db/migration` chạy ở **mọi** môi trường, account demo có mật khẩu
   công khai không được tới đó. (b) **`spring.profiles.active` default là `dev`, và default đó áp dụng
   cả khi chạy test** ⇒ wire vào `spring.flyway.locations` của `application-dev.yml` là bơm dữ liệu demo
   vào Testcontainer của **cả 11 class IT** và âm thầm đổi thứ chúng assert.
4. 🔴 **CORS phải wire qua `http.cors(...)`, không phải `WebMvcConfigurer` riêng.** Preflight `OPTIONS`
   **không** mang `Authorization` nên không thể qua `.anyRequest().authenticated()`; chỉ đường
   `http.cors(...)` mới cho Spring Security short-circuit preflight **trước** authorization. Làm sai thì
   `curl` vẫn chạy (không phải browser, không preflight) mà **mọi** request từ browser đều chết — đúng
   loại bug sống sót qua test tay.
5. **`SecurityConfig` cần `@EnableConfigurationProperties(CorsProperties.class)`.**
   `@ConfigurationPropertiesScan` của app phủ full context, nhưng `@WebMvcTest` slice chỉ `@Import`
   `SecurityConfig` thì **không** chạy scan đó ⇒ 6 test của `SecurityFilterChainTest` +
   `AuthMeSecurityTest` chết vì thiếu bean. Sửa ở **production config** (nó là consumer), không mock ở
   từng test (`R3`: `CorsProperties.validate()` chứa logic).
6. **`"*"` bị từ chối ngay lúc khởi động**, không phải "khuyến cáo không nên": API này gửi
   `Authorization` mọi request, mà CORS spec cấm wildcard origin trên credentialed request. Ném từ
   `CorsProperties.validate()` để thông báo nêu **tên property** thay vì lỗi từ trong lòng filter.
7. **`exposed-headers` là phần của contract, không phải trang trí.** `X-Trace-Id` là thứ FE trích dẫn
   khi báo lỗi, `X-RateLimit-*` + `Retry-After` là thứ FE cần để backoff — không expose thì JS **không
   đọc được** dù server có gửi.
8. **`items.unit`, UOM, lot, audit… không đụng** — `C2-5` chỉ làm hạ tầng chạy được. Các phase còn lại
   của `C2-*`: xem plan roadmap + `docs/capstone2-api-gap-response.md` §3.

**Nghiệm thu mutation (4, đã revert — **3/4 đụng `src/main` hoặc migration**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Bỏ `.cors(...)` khỏi `SecurityConfig` (**`src/main`**) | **4/6** `CorsConfigurationTest` | 2 case sống sót đúng như dự đoán: origin lạ vẫn 403 (vì lý do khác), và test wildcard là unit thuần ⇒ **phải có case origin được whitelist** mới khoá được |
| 2 | `validate()` không còn từ chối `"*"` (**`src/main`**) | **1** — `wildcardOrigin_isRejectedWithAnExplanation` | Quyết định fail-fast là test-backed, không phải văn xuôi |
| 3 | Bỏ 1 dòng grant của MANAGER khỏi `V41` (**migration**) | **2** — checklist dương **và** test admin-only | Cặp test mạnh hơn dự tính: quyền OPERATOR **không** có mà MANAGER mất thì thành admin-only ⇒ cả hai đỏ |
| 4 | Cấp `PERM_QUALITY_DISPOSITION` cho OPERATOR trong `V41` (**migration**) | **1** — `...keepsOperatorOutOfApprovalAndConfigurationPermissions` | Separation of duties không thể chứng minh bằng danh sách dương |

**Kiểm chứng qua HTTP thật** (`mvn -o spring-boot:run`, port 8081 vì 8080 đang có instance khác):

| Probe | Kết quả |
|---|---|
| Preflight `OPTIONS` từ `http://localhost:5173` | **200** + `Allow-Origin`/`Allow-Credentials`/`Expose-Headers` đầy đủ |
| Preflight từ `http://evil.example.com` | **403**, không có `Allow-Origin` |
| `manager.a` → `GET /plants/{A}/work-orders` | **200** (trước `V41`: 403) |
| `manager.a` → `GET /plants/{B}/work-orders` | **403 `PERMISSION_DENIED`** ⇒ isolation đúng |
| `operator.a` → `POST /plants/{A}/work-orders` | **403** (thiếu `PERM_WORK_ORDER_MANAGE`) |
| `manager.a` → cùng request đó | **404 `ENTITY_NOT_FOUND`** ⇒ đã qua authz, chết ở item giả |

> ⚠️ **Probe đầu tiên của tôi cho `operator.a` trả 400 `VALIDATION_ERROR`, không phải 403** — `@Valid`
> trên body chạy **trước** method security. Muốn probe authz thì body **phải hợp lệ**, nếu không sẽ kết
> luận sai là "đã chặn".

**Breaking changes — wire: KHÔNG có.** **Hành vi:** MANAGER/OPERATOR nay **làm được** việc tài liệu đã
hứa (nới quyền có chủ đích, quyết định của user); browser từ origin ngoài danh sách nay bị chặn tường
minh thay vì "chặn vì không có cấu hình nào".

---

### 0.26 C2-3 – UOM Master (ĐÃ HOÀN THÀNH 2026-08-04)

Phase thứ ba của track `C2-*` (`FRONTEND_ALIGNMENT_ROADMAP.md §8`), theo đúng
`NEXT_PHASE_PLAN.md` đã viết cho phase này. Migration **`V42`** (schema) + **`V43`** (seed permission).
Module mới `module/uom/` — 7 endpoint `/api/v1/uoms`. **Không** breaking change wire.

| Quyết định | Nội dung |
|---|---|
| Global, không company-scope | `uoms` **không có** `company_id` — khác mọi master data khác trong repo. Theo đúng dữ kiện FE liệt kê (`GET /uoms` không lọc theo company) |
| Lifecycle | `POST .../activate` + `POST .../deactivate` (không dùng `DELETE` như BOM/Routing) — đi theo đúng 2 verb FE liệt kê tường minh vì đây là greenfield API, tránh lặp lại vòng "FE báo thiếu lần 2" của `C2-0` |
| `code` immutable | Không validate runtime — `UomUpdateRequest` **không có field `code`**, compile-time guarantee |
| Permission | `PERM_UOM_READ` (ADMIN+MANAGER+OPERATOR), `PERM_UOM_MANAGE` (ADMIN+MANAGER) — theo đúng khuôn `V31` (routing) |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Phát hiện khi kiểm chứng qua HTTP thật, không phải bug của phase này:** `PermissionGuard.hasPermission(...)`
   (cơ chế gác `PERM_UOM_*`, giống `PERM_ORG_READ`/`_MANAGE` của `OrganizationService.listCompanies`/
   `createCompany`) chỉ đọc assignment có **`scopeType = GLOBAL`**. Account seed `manager.a`/`operator.a`
   của `C2-5` chỉ có scope `PLANT` ⇒ **403 trên mọi endpoint UOM**, kể cả `GET /uoms` dù `V43` đã cấp
   `PERM_UOM_READ` cho `OPERATOR`. Đã xác nhận **không riêng UOM** — `manager.a` gọi `GET /companies`
   (cùng cơ chế) cũng 403. Đây là đặc điểm **có sẵn toàn repo**, không phải lỗi `C2-3`. Chi tiết + hệ quả
   cho việc test sau này: `module/uom/CLAUDE.md`.
2. `items.unit` **hoàn toàn không đụng** — vẫn `String` tự do, đọc ở 13 call site/5 mapper. Nối
   `items.uom_id` là phase riêng (ngoài phạm vi `C2-3`, xem "KHÔNG làm gì" đã ghi trong kế hoạch phase).
3. Validate "không deactivate khi đang tham chiếu" (spec §3.1) **cố ý là no-op hiện tại** — chưa bảng
   nào FK tới `uoms`. Đừng đọc code kiểm tra chỗ đó rồi tưởng là dead code; nó chờ `items.uom_id`.
4. `AuditAction` thêm 4 constant: `UOM_CREATED`/`UOM_UPDATED`/`UOM_ACTIVATED`/`UOM_DEACTIVATED`
   (rule "Create/update/deactivate master data" bắt buộc audit, `common/audit/CLAUDE.md`).

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main` hoặc migration**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Đổi `PERM_UOM_MANAGE` → chuỗi rác trong `@PreAuthorize` của `create` (**`src/main`**) | **2** — `UomMethodSecurityTest.create_deniedWhenUomManageMissing` + `.create_allowedWhenUomManagePresent` | Test pin đúng chuỗi permission, không phải mock trả `false` mặc định trùng hợp |
| 2 | Đổi mã lỗi trùng `code` từ `RESOURCE_ALREADY_EXISTS` sang `INVALID_INPUT` (**`src/main`**) | **1** — `UomServiceTest.create_duplicateCode_throwsBeforeSaving` | Bám `ErrorCode`, không bám status — cả hai khác status thật (409 vs 400) nên cũng bắt được qua HTTP nếu có test đó |
| 3 | Bỏ `cast(:keyword as string)` khỏi `UomRepository.search` (**`src/main`**) | **2** — `UomRepositoryIT.search_withoutAKeywordReturnsEveryUom` + `.search_withoutAKeywordStillHonoursTheStatusFilter` | 🔴 **Xác nhận unit test (mock repository) không bắt được** — chạy `UomServiceTest`+`UomMethodSecurityTest` dưới mutation này vẫn xanh 15/15. Đúng cách `lower(bytea)` đã hai lần bắt được ai đó trong ngày (`§0.24`), lần thứ ba là tự bắt trước khi ai báo |
| 4 | Bỏ grant `PERM_UOM_READ` cho `OPERATOR` khỏi `V43` (**migration**) | **1** — `FlywayMigrationIT.migrate_v43_grantsUomPermissionsToTheDocumentedRoles` | Test mới, theo đúng khuôn `migrate_v41_*` của `C2-5` — ma trận grant chỉ DB thật trả lời được |

> 🔴 Mutation #3 là kết quả giá trị nhất: **plan của chính phase này** viết "có thể không cần `*IT`" rồi
> tự ghi điều kiện dự phòng ("nếu không chắc, thêm 1 case") — chạy thử xác nhận unit test **không** bắt
> được, nên `UomRepositoryIT` (**class IT thứ 12**) được thêm đúng theo điều kiện dự phòng đó, không bỏ qua.

**Kiểm chứng qua HTTP thật** (`mvn -o spring-boot:run`, port 8081): tạo UOM (`kg` → `KG`, 201) · list
**không** `search` (200, không 500 — đúng bẫy `§0.24`) · list có `search` · trùng `code` (409) ·
`deactivate`→`activate` round-trip · `admin` tạo được (201) · `operator.a`/`manager.a` 403 (do giới hạn
`GLOBAL` scope ở mục 1, không phải do V43 sai — đã đối chiếu với `listCompanies` để xác nhận).

**Breaking changes — wire: KHÔNG có.** Toàn bộ additive: bảng mới, endpoint mới, 2 permission mới, 4
audit action mới.

---

### 0.27 C2-4 – SO PATCH · Role/Scope Lifecycle · Over-BOM Contract · Time Variance (ĐÃ HOÀN THÀNH 2026-08-05)

Phase thứ tư của track `C2-*`. **Không migration.** Bốn phần độc lập về code, gộp một phase vì đều là
"hoàn thiện contract đang có". Bất biến `B86` (`module/workorder/CLAUDE.md`), `B87`
(`module/sales/CLAUDE.md`), `B88` (`module/organization/CLAUDE.md`).

| Phần | Endpoint | Quyền |
|---|---|---|
| A | `PATCH /sales-orders/{id}` | `PERM_SALES_ORDER_MANAGE` (tái dùng guard `confirm`/`cancel`) |
| B | `GET`/`PATCH /access/roles/{id}`, `.../activate`, `.../deactivate`; cùng bộ cho `/access/scopes/{id}`; `GET /access/assignments?userId=&roleId=&scopeId=` | `PERM_ACCESS_MANAGE` (không permission mới) |
| C | *(không endpoint)* — chốt tài liệu | — |
| D | `GET /work-orders/{id}/variance` (field mới `timeVariance`) | `PERM_WORK_ORDER_VARIANCE_READ` (không đổi) |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Defect thật, không phải giả định — bắt được lúc `mvn verify`, không phải lúc viết Part D.**
   Kế hoạch gốc định nới `WorkOrderRepository.findWithDetailsByWorkOrderId` thêm `"operations"` vào
   `@EntityGraph` đã có `"componentLines"`. **Cả hai đều là `List` ("bag")** ⇒ Hibernate ném
   `MultipleBagFetchException` ngay từ câu query đầu tiên — sập **toàn bộ** endpoint variance (và kéo
   theo `ProductionFlowE2EIT`, vì nó gọi cùng entity graph), không riêng phần time. `mvn test` (mock
   repository, `@EntityGraph` không chạy) **không** thấy được; chỉ `mvn -o verify` (Testcontainers
   thật) bắt được. Sửa: `WorkOrderVarianceService` đọc operations qua **một query riêng**
   (`WorkOrderOperationRepository.findByWorkOrderWorkOrderIdOrderBySequenceAsc` — method đã có sẵn từ
   trước, không phải thêm mới), không đụng `@EntityGraph` hiện có. 1 query thêm, không phải 1/dòng
   (`C14`). **Bài học:** trước khi thêm bất kỳ path thứ hai vào một `@EntityGraph` đã có collection
   `List`, kiểm xem path đó cũng là `List` không.
2. `plannedMinutes = Σ(setupMinutes + runMinutesPerUnit × plannedQuantity)` trên **mọi**
   `WorkOrderOperation` snapshot; `actualMinutes = Σ Duration.between(actualStartedAt, actualEndedAt)`
   trên **mọi** `ProductionExecution`, **loại** execution còn dở dang (`actualEndedAt == null`) — thời
   gian dở dang không phải thời gian đã tiêu tốn xong.
3. `PATCH /sales-orders/{id}`: `expectedVersion` so **tường minh** với `SalesOrder.getVersion()` ở
   tầng service, **trước khi** mutate bất cứ field nào (rule `C9`) — JPA không tự phát hiện được vì
   entity vừa load lại, không có gì "cũ" để so ở đây. Validate `dueDate ≥ orderDate` chạy lại cho
   **toàn bộ** dòng liên quan (dòng mới nếu `lines` được thay, dòng cũ nếu chỉ đổi `orderDate`) —
   đổi `orderDate` một mình vẫn có thể làm dòng cũ (không đổi) trở nên invalid.
4. Role `is_system = true` (ADMIN/MANAGER/OPERATOR) **không bao giờ** deactivate được, kể cả bởi
   `admin` — chặn ở **`deactivate`** là đủ; `activate` không cần chặn thêm vì role hệ thống không có
   đường vào `INACTIVE`. `PATCH` role/scope chỉ nhận `name`/`description` — `code`/`is_system`/
   `scopeType` immutable (compile-time: field không có trong request record).
5. `UserRoleAssignmentRepository.search` lọc 3 tham số **trực tiếp trên cột `UUID` phẳng** của
   `UserRoleAssignment` (`userId`/`roleId`/`scopeId`) — **không** cần join `Role`/`AccessScope`/`User`,
   khác các query B32 ở cùng file phải join để biết permission nào được cấp. Vì vậy **không** cần
   `*IT` cho query này (không phải JPQL phức tạp, `R7` không áp dụng) — chỉ unit test verify đúng
   tham số được forward.
6. `docs/capstone2-api-gap-response.md §5` câu 4 (over-BOM) đã trả lời: phương án (1) **là** hành vi
   code hiện tại (`B15`), không code gì thêm cho Part C.
7. `C2-4` **không** thêm permission mới ⇒ `docs/roles-and-permissions.md` không thêm quyền, chỉ ghi
   chú 3 dòng ở mục ADMIN/Sales nói rõ endpoint mới tái dùng quyền có sẵn.

**Nghiệm thu mutation (4, đã revert — 4/4 đụng `src/main`):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Bỏ vế `runMinutesPerUnit × plannedQuantity` khỏi `plannedMinutesFor` | **2** — `getVariance_timeVariance_sumsOperationsAndExecutionsExcludingInProgress`, `getVariance_noExecutions_actualMinutesIsZero` | Cả hai case có `runMinutesPerUnit > 0` đều lộ ra số planned sai |
| 2 | Đảo chiều `Duration.between(actualEndedAt, actualStartedAt)` (ngược) | **1** — `getVariance_timeVariance_sumsOperationsAndExecutionsExcludingInProgress` (`50` → `-50`) | Assert đúng dấu dương của tổng, không chỉ độ lớn |
| 3 | Bỏ `if (role.isSystem())` khỏi `deactivateRole` | **1** — `deactivateRole_systemRole_throwsOperationNotAllowedBeforeSaving` | Guard bảo vệ role hệ thống là test-backed, không phải văn xuôi |
| 4 | Bỏ check `expectedVersion` khỏi `SalesOrderService.update` | **1** — `update_staleExpectedVersion_throwsConcurrentModificationBeforeSaving` | Optimistic-lock tường minh ở tầng service có test khoá, không chỉ dựa vào `@Version` của JPA |

**Nghiệm thu:** `mvn -o clean verify` — **661 case unit + 78 case IT / 12 class IT, failures = 0,
errors = 0** (baseline trước phase: 617 unit + 78 IT / 12 class — không migration nên IT không đổi số
ròng: Part D thêm 1 case `WorkOrderRepositoryIT` cho N+1 rồi phải bỏ khi đổi thiết kế ở hệ quả #1).

**Breaking changes — wire: KHÔNG có.** Toàn bộ additive: 1 endpoint `PATCH` mới, 9 endpoint Role/Scope
mới, 1 field mới (`timeVariance`) trên `WorkOrderVarianceResponse`. **Java positional:**
`WorkOrderVarianceResponse` +1 component (cuối cùng), `WorkOrderVarianceService` constructor +2 tham
số (`ProductionExecutionRepository`, `WorkOrderOperationRepository`).

---

### 0.28 C2-6 – Work Center Entity + CRUD + Routing FK (ĐÃ HOÀN THÀNH 2026-08-05)

Phase thứ năm của track `C2-*`, mở đầu cluster `P4` (Work Center/Shift/Calendar/Capacity).
Migration **`V44`** (bảng `work_centers` + cột `routing_operations.work_center_id`) + **`V45`**
(seed permission). Module mới `module/workcenter/` — 7 endpoint. **Không** đổi request/response DTO
ngoài Routing (xem Breaking changes).

| Phần | Nội dung |
|---|---|
| A | `WorkCenter` **per-plant** (không company-level, khác `UOM` global của `C2-3`) — 7 endpoint, `PERM_WORK_CENTER_READ`/`_MANAGE` theo khuôn Routing (không theo khuôn `PERM_ORG_*`) |
| B | `RoutingOperation.workCenterCode` (free text) → `RoutingOperation.workCenter` (FK `WorkCenter`, **nullable, không backfill**) |

**Ba quyết định đã chốt với user trước khi viết phase** (đầy đủ ở `NEXT_PHASE_PLAN.md` lịch sử
`C2-6` §1): Work Center per-plant; `RoutingOperation` khoá vào 1 plant qua Work Center (bất biến
`B_wc2`); chưa thêm Calendar/Shift reference (chờ `C2-7`); `DELETE` + `POST .../activate|deactivate`
cùng tồn tại, `DELETE`/`deactivate` gọi **cùng** service method.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Defect thật bắt được lúc `mvn verify`, không phải lúc viết code.** `V30` tạo
   `routing_operations.work_center_code` là `NOT NULL`. Bản nháp đầu của `V44` chỉ `ADD COLUMN
   work_center_id` mà quên `ALTER COLUMN work_center_code DROP NOT NULL` — vì entity không còn map
   cột đó nữa, **mọi** insert `RoutingOperation` mới (kể cả `ProductionFlowE2EIT` seed dữ liệu)
   chết với `null value in column "work_center_code" violates not-null constraint`. `mvn test`
   (mock repository) không thấy được; chỉ Testcontainers thật (`FlywayMigrationIT`,
   `ProductionFlowE2EIT`) bắt được — đúng kiểu lỗ hổng `§0.24`/`§0.26` đã ghi hai lần trước đó,
   nay là lần thứ ba trong repo.
2. **`FlywayMigrationIT` là nơi duy nhất kiểm được ma trận grant `V45`** — `migrate_v45_grantsWorkCenterPermissionsToTheDocumentedRoles`, cùng khuôn `migrate_v43_*`/`migrate_v41_*`. `PermissionCatalogTest` chỉ chứng minh permission tồn tại, không chứng minh được cấp cho role nào (bài học lặp lại từ `C2-5`).
3. **B_wc2 validate ở `RoutingService.create()`, KHÔNG ở `WorkCenterService`.** Resolve toàn bộ
   `workCenterId` của request trước, kiểm tập `plantId` phân biệt > 1 mới build entity (rule C9).
   Nghiệm thu mutation: bỏ dòng gọi `ensureOperationsShareOnePlant(...)` ⇒ đúng
   **1** case đỏ (`RoutingServiceTest.create_operationsAcrossTwoPlants_throwsOperationNotAllowed`),
   các case khác (cùng plant) vẫn xanh — xác nhận check không nới quá tay.
4. **`WorkOrderOperation.workCenterCode` (String) không đổi** — vẫn là snapshot bất biến
   (`B56`/`B49`). `WorkOrderService.snapshotRouting` đổi đúng 1 dòng:
   `operation.getWorkCenter().getCode()` thay vì `operation.getWorkCenterCode()` (đã xoá field đó
   khỏi `RoutingOperation`).
5. **`RoutingOperationResponse` giữ cả `workCenterId` lẫn `workCenterCode`** — code nay resolve qua
   `operation.getWorkCenter()` (join), không phải cột riêng; null-safe cho dòng lịch sử chưa có
   Work Center (`workCenter == null` ⇒ cả hai field trả `null`).
6. **Mọi test dựng `RoutingOperation`/`RoutingOperationRequest` trực tiếp phải sửa** — không chỉ
   file mới viết mà cả `RoutingServiceTest`, `RoutingControllerTest`, `RoutingMethodSecurityTest`,
   `WorkOrderServiceTest`, `ProductionFlowE2EIT` đều dùng `workCenterCode`/`WC-01` (String) trước
   phase này; tất cả **sửa** theo `R10`, không xoá.
7. **`C2-6` KHÔNG thêm `*IT` mới** (vẫn 12 class IT) — đúng nhận định trong kế hoạch: query của
   `WorkCenterRepository.search` chỉ là `=`/`is null` đơn giản, không phải `concat`/`like` phức tạp
   nên mock repository là đủ (`R7` không áp dụng).

**Nghiệm thu:** `mvn -o clean verify` — **690 case unit + 79 case IT / 12 class IT, failures = 0,
errors = 0** (baseline trước phase: 661 unit + 78 IT / 12 class — `+29` unit từ `WorkCenterServiceTest`
(9) + `WorkCenterMethodSecurityTest` (8) + `WorkCenterControllerTest` (10) + 2 case mới trong
`RoutingServiceTest`; `+1` IT từ `FlywayMigrationIT.migrate_v45_*`, không thêm class IT mới).

**Breaking changes — wire:**
- `RoutingOperationRequest.workCenterCode` (String) → `workCenterId` (UUID). Client gửi text tự do
  cho field này **sẽ hỏng** — phải tạo Work Center trước rồi lấy `workCenterId`.
- `RoutingOperationResponse` thêm `workCenterId` (additive).
- 7 endpoint Work Center: additive.

**Java positional:** `RoutingOperation` field `workCenterCode` (String) → `workCenter` (`WorkCenter`);
`RoutingService` constructor +1 tham số (`WorkCenterLookupService`).

---

### 0.29 C2-7 – Shift + Work Calendar Entity + CRUD + Work Center FK (ĐÃ HOÀN THÀNH 2026-08-05)

Phase thứ sáu của track `C2-*`, phần thứ hai của cluster `P4` (Work Center/Shift/Calendar/Capacity),
nối tiếp `C2-6`. Migration **`V46`** (schema, gộp cả 3 phần A+B+C) + **`V47`** (seed permission).
Module mới `module/shift/` — 14 endpoint. **Không** breaking change wire ngoài field additive trên
Work Center.

| Phần | Nội dung |
|---|---|
| A | `Shift` **per-plant** — 1 interval (`startTime`/`endTime`, kiểu `TIME`) + `breaks[]`. 7 endpoint `/api/v1/plants/{plantId}/shifts` + `/api/v1/shifts/{id}`, `PERM_SHIFT_READ`/`_MANAGE` theo khuôn Work Center |
| B | `WorkCalendar` **per-plant** — lịch tuần (`weekday → shift`, một weekday nhận **nhiều** shift) + `WorkCalendarException` (`NON_WORKING`, một chiều). 7 endpoint `/api/v1/plants/{plantId}/work-calendars` + `/api/v1/work-calendars/{id}`, `PERM_WORK_CALENDAR_READ`/`_MANAGE` — permission **riêng** với Shift dù cùng module |
| C | `WorkCenter.workCalendarId` (FK tuỳ chọn, nullable) + bất biến `B_wc4` (cùng plant) |
| D | `WorkingWindowCalculator` + `WorkCalendarLookupService.computeWorkingWindows` — net working window, **internal only**, không endpoint public |

**Bốn quyết định đã chốt với user trước khi viết phase** (đầy đủ ở `NEXT_PHASE_PLAN.md` lịch sử
`C2-7` §1): Shift = 1 interval + `breaks[]`, không phải nhiều "work interval" con; `WorkCalendarException`
chỉ `NON_WORKING`, không có "override ca đặc biệt"; `WorkCenter` chỉ thêm đúng 1 cột `work_calendar_id`,
không có `shiftIds[]` riêng; net working window không có endpoint public ở phase này (chờ `C2-8`).

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`WorkCalendarRepository.findWithWeeklyShiftsByWorkCalendarId` cố ý KHÔNG fetch `exceptions`
   trong cùng `@EntityGraph`.** `weeklyShifts` và `exceptions` đều là `List` (bag) trên `WorkCalendar`
   ⇒ join-fetch cả hai cùng lúc ném `MultipleBagFetchException` — **đúng cái bẫy `C2-4` đã gặp** giữa
   `operations`/`componentLines` (`§0.27` hệ quả #1), lần thứ hai trong repo. Sửa: `exceptions` lazy-load
   trong cùng transaction (`calendar.getExceptions().size()` ngay sau khi fetch) thay vì fetch cùng lúc
   — đây là **một** aggregate detail, không phải report lặp theo dòng, nên rule C14 không áp dụng.
2. 🔴 **`ShiftTimeWindow.containsInterval` dùng phép đo circular, KHÔNG có nhánh if/else riêng cho ca
   qua đêm.** `durationMinutes(start, end)` luôn trả về số phút không âm, tự "wrap" qua nửa đêm khi
   `end` đứng trước `start`. Công thức này dùng chung cho **cả hai**: (a) một break có nằm trong shift
   không (`ShiftService`), và (b) shift chiếm bao nhiêu phút trong ngày (`WorkingWindowCalculator`,
   Part D). Sửa công thức ảnh hưởng cả hai nơi cùng lúc — xem `module/shift/CLAUDE.md` mục 2.
3. **Quy ước qui-thuộc-ngày cho ca qua đêm là hợp đồng cho `C2-8`, không phải chi tiết nội bộ.** Shift
   qua đêm (`endTime < startTime`) gán cho weekday nó **bắt đầu**; interval trả về có thể kết thúc ở
   ngày dương lịch **kế tiếp**. Ghi đủ chi tiết ở `module/shift/CLAUDE.md` mục 1 vì đó là chỗ `C2-8` sẽ
   đọc lại khi cộng dồn giờ làm cho Capacity Board.
4. **`WorkCalendarUpdateRequest.effectiveTo` và `WorkCenterUpdateRequest.workCalendarId` giữ quy ước
   "`null` = không đổi" dù cả hai đều nullable-có-ý-nghĩa** (không hết hạn / không gắn calendar) — hệ
   quả chấp nhận: không có đường "xoá về trống" hai field này qua `PATCH`, để nhất quán với mọi field
   khác trong repo thay vì thêm sentinel riêng cho hai chỗ này.
5. **`Shift`/`WorkCalendar` validate lẫn nhau bằng cách gọi thẳng `ShiftRepository`, không qua lookup
   service riêng** — khác `WorkCenter`↔`Routing` (hai module khác nhau, bắt buộc rule C7). `Shift` và
   `WorkCalendar` cùng module nên không có ranh giới cross-module cần bảo vệ thêm.
6. **`B_wc2` (Routing↔WorkCenter cùng plant) và `B_cal1` (WorkCalendar↔Shift cùng plant) và `B_wc4`
   (WorkCenter↔WorkCalendar cùng plant) là ba bất biến "cùng hình dạng" ở ba cặp entity khác nhau** —
   cả ba đều validate ở tầng service, **trước khi** build entity (rule C9), cả ba đều trả 422
   `OPERATION_NOT_ALLOWED` (input sai, không phải state machine).
7. **`C2-7` KHÔNG thêm permission cho ADMIN riêng** — `V47` cấp `PERM_SHIFT_*`/`PERM_WORK_CALENDAR_*`
   cho `ADMIN`+`MANAGER` (cả hai) và chỉ 2 quyền `_READ` cho `OPERATOR`, đúng khuôn `V45`/`V43`. Test
   ma trận grant ở `FlywayMigrationIT.migrate_v47_*` (3 case, DB thật) — `PermissionCatalogTest` không
   đủ để kiểm điều này (bài học lặp lại từ `C2-5`/`C2-3`/`C2-6`).

**Nghiệm thu:** `mvn -o clean verify` — **759 case unit + 80 case IT / 12 class IT, failures = 0,
errors = 0** (baseline trước phase: 690 unit + 79 IT / 12 class — `+69` unit từ `ShiftServiceTest`(12)/
`ShiftMethodSecurityTest`(8)/`ShiftControllerTest`(9)/`WorkCalendarServiceTest`(12)/
`WorkCalendarMethodSecurityTest`(8)/`WorkCalendarControllerTest`(9)/`WorkingWindowCalculatorTest`(7) +
4 case `B_wc4` mới trong `WorkCenterServiceTest`; `+1` IT từ `FlywayMigrationIT.migrate_v47_*`, không
thêm class IT mới — `ShiftRepository.search`/`WorkCalendarRepository.search` chỉ `=`/`is null` đơn
giản nên mock repository là đủ, đúng điều kiện dự phòng đã ghi trong kế hoạch phase).

**Breaking changes — wire: KHÔNG có** (thuần additive: 14 endpoint mới, 1 field mới `workCalendarId`
trên Work Center create/update/response, tuỳ chọn).

**Java positional:** `WorkCenterService` constructor +1 tham số (`WorkCalendarLookupService`);
`WorkCenterCreateRequest`/`UpdateRequest`/`Response` +1 component (`workCalendarId`, cuối record).
Test cũ dựng các record này positional đã **sửa** theo `R10`.

---

### 0.30 C2-8 – CRP Tĩnh + Capacity Board + Schedule Adjustment (ĐÃ HOÀN THÀNH 2026-08-05)

Phase thứ bảy của track `C2-*`, đóng nốt cluster `P4` (Work Center → Shift/Work Calendar →
Capacity) mà `C2-6`/`C2-7` đã dựng. Migration **`V48`** (schema) + **`V49`** (seed permission).
**Không** module mới — mọi thứ nằm trong `module/workorder` (query/write service mới cạnh
`WorkOrderVarianceService`) cộng **một** method mới trên `module/shift`'s `WorkCalendarLookupService`
+ `WorkingWindowCalculator` (rule C7 — `workorder` gọi vào, không tự làm lại toán lịch).

**Ba quyết định đã chốt với user trước khi viết kế hoạch chi tiết** (đầy đủ ở `NEXT_PHASE_PLAN.md`
lịch sử `C2-8` §1): lịch operation sinh **ở `release()`**, không phải lúc tạo WO hay lúc `plan()`;
nợ B (`predecessorOperationIds`/sequence-dependency validation) **tách `C2-8b`**, không làm ở đây;
`schedule-adjustments` **không bao giờ tự dời** operation khác — chỉ trả cờ warning/conflict.

| Phần | Nội dung |
|---|---|
| A | `WorkOrderOperation` thêm `workCenter` (FK, nullable), `plannedStartAt`/`plannedEndAt` (`Instant`, nullable), `scheduleAdjustmentReason`. `WorkOrderService.scheduleOperations` (gọi từ `release()`) forward-schedule tuần tự theo `sequence`, đi qua lịch làm việc của Work Center (nếu có) |
| B | `GET /api/v1/plants/{plantId}/capacity-board` — `CapacityBoardService` (mới, `module/workorder/service/query`), aggregate load/capacity/utilization theo `(workCenter, ngày local theo `Plant.timezone`)` |
| C | `POST /api/v1/work-orders/{id}/operations/{id}/schedule-adjustments` — `ScheduleAdjustmentService` (mới, `module/workorder/service`), ghi đè thủ công + cờ tư vấn |
| D | Permission `PERM_CAPACITY_READ`/`_MANAGE` (`V49`) — cùng tầng Work Center/Shift: ADMIN+MANAGER cả hai, OPERATOR chỉ `_READ` (gap doc §3.6: "Manager điều chỉnh") |

**Quyết định đảo ngược tiền lệ đã ghi — flag rõ để không đọc nhầm `module/workcenter/CLAUDE.md` cũ:**

`module/workcenter/CLAUDE.md` mục 4 (viết ở `C2-6`) từng nói `WorkOrderOperation.workCenterCode`
giữ nguyên `String`, **không** đổi thành FK, vì lúc đó chưa có consumer thật. `C2-8` **là** consumer
đó — Capacity Board phải join/filter theo Work Center và đọc `capacityUnits`/`workCalendar`, việc
free-text không làm được. `C2-8` thêm cột **thứ hai**, `work_center_id` (nullable, FK), **cạnh**
`work_center_code` (giữ nguyên, vẫn là display snapshot) — dòng lịch sử (routing operation không có
Work Center, `B_wc2`) mang `NULL` và đơn giản là vô hình với Capacity Board, cùng hình dạng "không
backfill" đã có ở `B_wc2`/`B77`. `module/workcenter/CLAUDE.md` mục 4/7 đã cập nhật để trỏ ngược lại
đây.

**Hệ quả cần nhớ khi code tiếp:**

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B89 | Lịch (`plannedStartAt`/`plannedEndAt`) sinh **đúng một lần**, ở `WorkOrderService.release()`, tuần tự theo `sequence`: operation sau bắt đầu khi operation trước kết thúc (hoặc `workOrder.plannedStartAt`/`now()` cho operation đầu). Operation có Work Center gắn `workCalendar` thì gọi `WorkCalendarLookupService.computeEndInstant` (bỏ qua giờ không làm việc); không có calendar thì cộng phút liên tục. Đây là lịch **infinite-capacity** — không biết WO khác có đang chiếm cùng Work Center hay không, đó là việc của Capacity Board (read riêng), cố ý tách hai mối quan tâm theo đúng định nghĩa CRP tĩnh | `WorkOrderServiceTest.release_schedulesOperationsSequentially_continuousTimeWhenWorkCenterHasNoCalendar`, `.release_schedulesOperations_delegatesToTheWorkCenterCalendarWhenPresent`, `.release_withoutAnExplicitPlannedStartAt_anchorsTheFirstOperationAtNow` |
| B90 | `schedule-adjustments` chỉ chặn cứng khi **version lệch** (409 `CONCURRENT_MODIFICATION`) hoặc **thời gian vô nghĩa** (`plannedEndAt <= plannedStartAt`, 400). Xung đột **sequence** (đè lên cửa sổ của operation liền trước/sau), **calendar** (0 phút làm việc ngày đó), **capacity** (load vượt capacity) đều **không chặn** — ghi vào response dưới dạng cờ tư vấn (`sequenceConflict`/`calendarConflict`/`capacityOverload`) để manager tự xử lý. Backend **không bao giờ** tự dời operation khác | `ScheduleAdjustmentServiceTest.adjust_staleExpectedVersion_throwsConcurrentModificationBeforeAnyWrite`, `.adjust_endNotAfterStart_throwsInvalidInputBeforeAnyWrite`, `.adjust_cleanWindow_persistsAndReportsNoSequenceConflict`, `.adjust_windowStartingBeforePredecessorEnds_stillPersistsButFlagsSequenceConflict` |

1. 🔴 **Ranh giới local-time ↔ Instant nằm ở `Plant.timezone`, một field đã tồn tại từ lâu nhưng
   trước `C2-8` chưa từng được nối vào toán ngày tháng thật nào.** `module/shift` hoàn toàn "naive
   local time" (`LocalDateTime`, không zone) — `WorkingWindowCalculator.advance` nhận/trả
   `LocalDateTime`; `WorkCalendarLookupService.computeEndInstant` là **nơi duy nhất** chuyển đổi
   qua lại `Instant` bằng `ZoneId.of(plant.getTimezone())`. Việc gom nhóm load theo "ngày" trong
   `WorkOrderOperationRepository` (JPQL) dùng đúng cùng phép quy đổi
   (`function('timezone', plant.timezone, plannedStartAt)`, Postgres `timezone(zone, ts)` — tương
   đương `AT TIME ZONE`) để hai phía Java/SQL không lệch ngày với nhau. `WorkOrderOperationRepositoryIT`
   dựng cố ý một mốc mà ngày UTC và ngày local lệch nhau (`America/New_York`, tháng 1) để chứng minh
   chiều quy đổi đúng — đây là construct JPQL `function(...)` **đầu tiên** trong repo (chưa có tiền lệ
   native query nào trước đó), nên đừng copy công thức này sang chỗ khác mà không kiểm lại bằng `*IT`
   thật (rule R7, bài học `lower(bytea)` `§0.24`).
2. 🔴 **"Existing load" luôn tính trên `{RELEASED, IN_PROGRESS, COMPLETED}`, bất kể tham số `status`
   filter của board đang lọc gì** — filter chỉ thu hẹp **dòng hiển thị**, không thu hẹp mẫu số của
   utilization. Nếu không tách hai khái niệm này, hai lượt gọi board với filter khác nhau sẽ báo hai
   con số utilization khác nhau cho cùng một Work Center/ngày — tự mâu thuẫn.
3. **`dayCapacityMinutes`/`utilizationPercent` là `null` khi Work Center không có `workCalendar`,
   không phải `0`.** Không có calendar nghĩa là "không biết", không phải "không có sức chứa" —
   `overload` khi đó luôn `false` (không đủ dữ liệu để khẳng định quá tải).
4. **Ngày quy-thuộc của một operation là ngày `plannedStartAt` rơi vào (theo local time), toàn bộ
   thời lượng gán hết cho ngày đó** — không chia tỷ lệ theo số ngày operation trải dài qua. Đây là
   đơn giản hoá có chủ đích của "CRP tĩnh" (không phải bug), cùng quy ước "gán cho ngày ca bắt đầu"
   `module/shift/CLAUDE.md` mục 1 đã dùng cho ca qua đêm.
5. **`ScheduleAdjustmentService` tái dùng `workOrderPermissionGuard` có sẵn** (không phải component
   permission-guard riêng) — nó là guard tổng quát khoá theo chuỗi permission code truyền vào, đã
   dùng cho mọi action của `WorkOrderService`. `CapacityBoardService.getBoard` cũng tái dùng
   `permissionGuard.hasResourceAccess(..., 'PLANT', ...)` có sẵn. Không thêm bean guard mới.
6. **`CapacityBoardService.buildDayContext`/`attributedDay`/`dayCapacityMinutes`/`utilizationPercent`/
   `key` là `public`** dù chỉ có một caller khác (`ScheduleAdjustmentService`, khác package) — hai
   endpoint phải báo cùng một con số "quá tải" cho cùng một `(workCenter, ngày)`, nên chia sẻ đúng
   một implementation thay vì hai công thức có thể trôi lệch nhau.
7. **`WorkOrderOperationResponse` thêm field** (`workCenterId`, `plannedStartAt`, `plannedEndAt`,
   `version`) — additive trên wire, nhưng **positional trong Java** (record).

**Không làm gì (có chủ đích, không phải thiếu sót):**
- Nợ B (`predecessorOperationIds`, validate thứ tự operation) — chờ `C2-8b`, xem quyết định #2.
- Không có bảng lưu "kế hoạch capacity" — mọi con số tính live từ `WorkOrderOperation` đã persist,
  giống cách `MrpCalculationService`/`WorkOrderVarianceService` không lưu kết quả trung gian.
- Không nghiệm thu bằng mutation testing (khác nhiều phase `C2-*` trước) — thời lượng phase không
  cho phép; bù lại bằng bộ test rộng hơn ở mọi tầng (unit, method-security, controller, repository
  `*IT`, migration `*IT`) cộng smoke test HTTP thật qua `mvn -o spring-boot:run` trên Postgres thật
  (không phải Testcontainer) sau khi `mvn -o clean verify` xanh. Nếu phase sau muốn nghiệm thu mutation
  cho `C2-8`, B89/B90 là hai bất biến nên nhắm tới trước.

**Nghiệm thu:** `mvn -o clean verify` — **791 case unit + 87 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 759 unit + 80 IT / 12 class — xem hàng "Baseline test" §0.1 để
biết đúng test nào cộng vào đâu). Smoke test HTTP thật (`mvn -o spring-boot:run`, Postgres +
Redis qua `docker-compose up -d`, không phải Testcontainer): login `admin` → JWT mang đúng
`PERM_CAPACITY_READ`/`PERM_CAPACITY_MANAGE` (xác nhận `V49` seed đúng) → tạo company/plant/work
center qua HTTP → `GET .../capacity-board` trả `200` đúng envelope `PageResult` rỗng → `POST
.../schedule-adjustments` trên work order không tồn tại trả **403** (không phải 404) — đúng hành vi
sẵn có của `WorkOrderPermissionGuard.hasWorkOrderAccess` (aggregate không tồn tại ⇒ `orElse(false)`
⇒ từ chối trước khi service kịp trả `RESOURCE_NOT_FOUND`), nhất quán với mọi action khác của
`WorkOrderService` — không phải bug của phase này.

**Breaking changes — wire: KHÔNG có** (thuần additive: 2 endpoint mới, 4 field mới trên
`WorkOrderOperationResponse`). **Java positional:** `WorkOrderService` constructor +1 tham số
(`WorkCalendarLookupService`, trước `mapper`); `WorkOrderOperationResponse` +3 component;
`WorkOrderOperation.builder()` +4 field (không phá call site cũ, Lombok builder). Test cũ dựng
`WorkOrderService`/`WorkOrderMethodSecurityTest.Config` đã **sửa** theo `R10`.

---

### 0.31 P3 – Costing Engine (ĐÃ HOÀN THÀNH 2026-08-05)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P3, "khoảng trống lớn nhất về lý thuyết" — trước phase
này repo **không có cột chi phí nào** ở bất kỳ đâu, `Item` xác nhận không field cost, đọc trực tiếp
`WorkOrderVarianceResponse` xác nhận thuần số lượng, không có `$` nào). Module mới `module/costing`.
Migration **`V50`** (schema: `item_standard_costs` + `work_order_cost_accumulators`) + **`V51`**
(seed `PERM_COSTING_READ`/`_MANAGE`).

**Hai quyết định chốt với user trước khi viết kế hoạch chi tiết:**
1. **`laborCost`/`overheadCost` nhập tay theo rate cố định** trên `ItemStandardCost` — không tính từ
   `WorkOrderOperation.runMinutesPerUnit` × rate/phút. Đơn giản hơn, không khoá costing vào dữ liệu
   routing/work-center (`C2-6`/`C2-7`), ship được ngay.
2. **Không làm Material Price Variance, chỉ Material Usage Variance.** `stock_movements` không có cột
   giá (grep xác nhận), và dữ liệu giá duy nhất trong hệ thống (`PurchaseOrderLine.unitPrice`) tách
   rời khỏi ledger xuất kho — tính "giá thực trả cho vật tư đã xuất cho WO này" đòi một lớp
   actual-costing (FIFO/weighted-average) chưa tồn tại và không ai yêu cầu.

| Phần | Nội dung |
|---|---|
| A | `ItemStandardCost` — upsert (`PUT`, không phải full CRUD, cùng khuôn `ItemWarehouseSettingController.upsert`), company-scoped (như `BomHeader`/`RoutingHeader`, không per-plant như `WorkCenter`, không global như `Uom`). 3 endpoint: `PUT`/`GET .../items/{itemId}/standard-cost`, `GET .../items/standard-costs` (list) |
| B | `CostingService.calculateStandardCost` — đệ quy giống `MrpCalculationService.expandChildren`, cycle guard `LinkedHashSet<UUID>` defense-in-depth (B8 lẽ ra đã chặn BOM vòng lặp `ACTIVE`) |
| C | `WorkOrderCostAccumulator` (`module/workorder`) — 2 hook: `MaterialIssueService.postNew` (material, bên trong guard chống double-count có sẵn), `ProductionExecutionService.reportNew` (labor+overhead, chỉ khi `good > 0`) |
| D | `GET /work-orders/{id}/variance` mở rộng: `materialLines[].usageVarianceCost`, khối `costVariance` mới (`standard*`/`actual*`/`totalCostVariance`) |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`ItemStandardCostLookupService` có BA method, mỗi cái phục vụ đúng một việc, đừng gộp:**
   `findStandardUnitCost` (cost fully-loaded, roll-up — dùng cho component bị issue và cho
   `usageVarianceCost`), `findLaborOverheadCost` (rate thẳng, không roll-up — chỉ dùng cho product
   item của chính WO), `findStandardCostBreakdown` (tách 3 thành phần — dùng cho baseline
   `costVariance`). Chi tiết đầy đủ: `module/costing/CLAUDE.md` mục 1.
2. 🔴 **Cost của một component bị issue là cost fully-loaded của nó (`findStandardUnitCost`), KHÔNG
   phải chỉ field `materialCost` riêng của nó.** Nếu component tự nó là hàng lắp ráp (có BOM), cost
   fully-loaded đã gồm cả labor/overhead của chính nó — đây là cách absorbed-cost roll-up nhiều cấp
   hoạt động đúng. Dùng nhầm field `materialCost` thô sẽ undercount nghiêm trọng cho mọi component là
   sub-assembly (field đó thường bỏ trống/0 cho item có BOM, theo đúng thiết kế B91).
3. **`ItemStandardCostService.upsert` dùng MỘT action audit (`ITEM_STANDARD_COST_UPSERTED`), không
   tách CREATED/UPDATED.** `@Auditable` là AOP theo method, không hỗ trợ chọn action theo runtime
   state (create-vs-update) mà không tự đâm vào một trong hai vấn đề: self-invocation bypass proxy
   (tách 2 method) hoặc gọi `AuditLogService` thủ công qua `RequestContextHolder` (phá vỡ khả năng
   unit-test service bằng constructor + mock thuần — `@Auditable` là no-op an toàn ngoài Spring proxy,
   gọi thủ công thì không). Chọn phương án đơn giản nhất, đúng tinh thần `ItemWarehouseSettingService`
   (tiền lệ upsert gần nhất) vốn **không** audit gì cả.
4. **Permission dùng `hasResourceAccess(..., 'COMPANY', companyId)`, KHÔNG phải `hasPermission` như
   UOM.** Cố ý tránh giới hạn đã ghi ở `module/uom/CLAUDE.md`: `hasPermission` chỉ đọc assignment
   `scope_type = GLOBAL`, hiện chỉ `admin` có — `manager.a`/`operator.a` (seed `C2-5`, scope `PLANT`)
   sẽ bị 403 dù đã được cấp quyền. `hasResourceAccess(..., 'COMPANY', ...)` không dính bẫy đó.
5. **`PERM_COSTING_READ`/`_MANAGE`: ADMIN+MANAGER, KHÔNG có OPERATOR** — xác nhận bằng cách đọc
   `V17__seed_manufacturing_execution_permissions.sql`: `PERM_WORK_ORDER_VARIANCE_READ` (quyền mà
   cost figures của phase này gắn vào) đã luôn ADMIN+MANAGER only. Khác mọi permission `C2-*` gần đây
   (Work Center/Shift cho OPERATOR đọc). `FlywayMigrationIT.migrate_v51_*` là test **cấm** thuần —
   `PermissionCatalogTest` không chứng minh được cấp cho role nào (bài học lặp lại từ `C2-5`/`C2-3`/
   `C2-6`).
6. **`totalStandardCost` trên `ItemStandardCostResponse` tính lúc đọc (`CostingMapper.toResponse`
   gọi `CostingService`), không lưu cột, không cache.** Sửa một BOM line thì mọi `GET
   .../standard-cost` của item cha phản ánh ngay — đánh đổi là mỗi read tốn thêm truy vấn đệ quy,
   chấp nhận được vì đây không phải hot path.
7. **Không có `ItemStandardCostRepositoryIT`** — `search` chỉ so `=`/`is null` trên UUID, không có
   `concat`/`like` trên `String` nullable nên không dính bẫy "lower(bytea)" (`§0.24`). Cùng lý do
   `WorkCenterRepository.search` (`C2-6`) không có `*IT`.
8. **Không nghiệm thu bằng mutation testing** (khác nhiều phase `C2-*` gần đây) — bù lại bằng test
   rộng ở mọi tầng (unit, method-security, controller, `FlywayMigrationIT`) cộng smoke test HTTP thật
   qua `mvn -o spring-boot:run` trên Postgres/Redis thật: BOM 2 cấp (nguyên liệu 5đ/kg, lắp ráp
   labor=3+overhead=1, BOM 2kg/unit) → `standard-cost` trả `totalStandardCost=14` đúng công thức
   (2×5+3+1) → issue 4kg (kế hoạch 6kg) → `variance` trả `usageVarianceCost=-10` (-2×5) và
   `actualMaterialCost=20` (4×5) **trước khi** report sản lượng (accumulator row tồn tại độc lập với
   report) → report `good=2` → `actualLaborCost=6` (2×3), `actualOverheadCost=2` (2×1) — xác nhận cả
   hai hook chạy đúng qua ledger/DB thật, không chỉ qua mock.

**Nghiệm thu:** `mvn -o clean verify` — **821 case unit + 88 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 791 unit + 87 IT / 13 class — `+30` unit, `+1` IT, không thêm
class IT mới, xem hàng "Baseline test" §0.1 để biết đúng test nào cộng vào đâu).

**Breaking changes — wire: KHÔNG có** (thuần additive: 3 endpoint mới, `usageVarianceCost` +
`costVariance` mới trên `WorkOrderVarianceResponse`). **Java positional:**
`WorkOrderMaterialVarianceLineResponse` +1 component (cuối cùng); `WorkOrderVarianceResponse` +1
component (cuối cùng); `WorkOrderVarianceService` constructor +2 tham số
(`WorkOrderCostAccumulatorRepository`, `ItemStandardCostLookupService`); `MaterialIssueService`
constructor +1 tham số (`WorkOrderCostAccumulatorService`); `ProductionExecutionService` constructor
+1 tham số (`WorkOrderCostAccumulatorService`). Test cũ dựng các service/DTO này đã **sửa** theo
`R10`.

---

### 0.32 Concurrent Refresh-Token Race — Mở Rộng `D8` (ĐÃ HOÀN THÀNH 2026-08-05)

Đóng giới hạn "race double-submit" mà `D8a` (§0.22) chấp nhận có chủ đích. Nguồn: FE coi đây là điều
kiện nghiệm thu (`FRONTEND_ALIGNMENT_ROADMAP.md §8.0` mục 1, nay ✅). Không migration (thuần Redis),
không permission mới, không đổi `AuthResponse`.

**Vấn đề đã đóng** (trace từ code thật, `AuthService.refresh`): request A rotate xong (lưu mới →
mark used → xoá cũ) nhưng client không nhận được response → client retry với **chính** `(tokenId,
refreshToken)` cũ (chưa từng thấy cặp mới) → request B đọc `stored == null` → `wasRefreshTokenUsed`
trả **true** (A vừa mark) → B bị chẩn đoán nhầm thành kẻ trộm dùng lại token đã đánh cắp →
`TOKEN_REUSE_DETECTED` → **toàn bộ phiên hợp lệ của B bị force-logout oan**.

**Quyết định chốt với user (AskUserQuestion, không phải đoán):** hard lock, **không** grace window —
token cũ không bao giờ được chấp nhận lại làm credential lần hai; hai request race trên cùng
`tokenId` phải được nhận diện là **cùng một hành động logic**.

| Cơ chế | Ở đâu | TTL |
|---|---|---|
| Khoá tư vấn theo `tokenId` | `TokenStoreService.acquireRefreshLock` — `SET NX PX` qua `opsForValue().setIfAbsent`, **một lệnh Redis atomic**, không Lua | 2s |
| Breadcrumb kết quả rotate | `TokenStoreService.saveRotationResult`/`getRotationResult` — winner ghi ngay sau `deleteRefreshToken` hiện có | 5s |

`AuthService.refresh` gọi `acquireRefreshLock` **đầu tiên**, trước mọi validate — khoá được thì đi
tiếp ngay (đường nhanh, không đổi hiệu năng của tuyệt đại đa số request); khoá **không** được thì ngủ
một lần ~150ms rồi đi tiếp vào luồng validate y hệt cũ. Trong nhánh `stored == null` (đúng chỗ RTR
sống), **trước** `wasRefreshTokenUsed`: nếu breadcrumb tồn tại và cặp nó trỏ tới còn sống → trả về
**đúng cặp đó** (mint access token mới — vốn đã làm ở mọi lần refresh) thay vì ném
`TOKEN_REUSE_DETECTED`. Breadcrumb vắng mặt/hết hạn/cặp trỏ tới đã mất ⇒ rơi xuống
`wasRefreshTokenUsed` **y hệt trước phase này** — RTR thật (`B80`) không đổi một dòng.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Cần CẢ HAI cơ chế, không phải một.** Chỉ khoá không đủ: theo đúng trace ở trên, request A đã
   **hoàn tất hết** (kể cả xoá) trước khi B gọi `getRefreshToken` — B khoá được ngay (không có gì để
   chờ) và vẫn cần một câu trả lời cho `stored == null`; breadcrumb là thứ trả lời câu đó. Chỉ
   breadcrumb không đủ: hai request đọc `stored != null` ở CÙNG một khoảnh khắc (chưa ai kịp ghi gì)
   sẽ cả hai tự rotate độc lập, sinh 2 phiên hợp lệ từ 1 hành động; khoá cho request thứ hai một
   khoảng chờ ngắn để **thấy** kết quả của request thứ nhất thay vì tự làm lại.
2. 🔴 **Không unlock tường minh — dựa hoàn toàn vào TTL 2s.** Vùng găng (critical section) chỉ là vài
   lượt gọi Redis, hoàn tất trong vài mili-giây, nên compare-and-delete an toàn (Lua) không đáng công
   thêm cho lợi ích chỉ xuất hiện ở trường hợp đụng độ 3 request hiếm gặp.
3. **Tiện thể sửa một câu tài liệu sai đã tồn tại từ lâu, không phải bug mới.**
   `common/security/CLAUDE.md §4.8` và `.claude/rules/architecture-decisions.md` từng ghi bộ đếm
   brute-force "dùng Lua script Redis để atomic increment+expire" — đọc lại
   `TokenStoreService.incrementFailCount` thật thì đó là `opsForValue().increment` (tự atomic) rồi
   một `expire()` **riêng, không atomic**. Grep `RedisScript`/`DefaultRedisScript` toàn repo ra **0
   kết quả** — chưa từng có Lua script nào. Phase này **không** sửa hành vi đó (ngoài phạm vi, chưa ai
   yêu cầu), chỉ sửa mô tả cho khớp code — khoá mới ở đây dùng `SET NX PX` (atomic một lệnh), không
   phải Lua, nên tài liệu không còn có thể trích dẫn nhầm "đã có tiền lệ Lua" nữa.
4. **`respondWithExistingPair` KHÔNG rotate lại, KHÔNG gọi `saveRefreshToken`/`saveSessionStart`
   lần hai.** Nó chỉ đọc lại cặp mà winner đã tạo và mint một access token mới (JWT stateless, không
   lưu, mint mới ở **mọi** lần refresh vốn đã vậy — không phải cơ chế mới). `sessionCreatedAt` (B81)
   không cần xử lý gì thêm ở đường này vì winner đã carry-forward đúng nó rồi.
5. **Không audit `TOKEN_REUSE_DETECTED` cho nhánh breadcrumb-hit** — đây không phải sự cố bảo mật, chỉ
   là hệ thống nhận đúng một request trùng lặp của chính nó. Có log `DEBUG`, không thêm `AuditAction`
   mới (tránh phình enum cho một sự kiện không phải audit event).
6. **Test unit cho `AuthService.refresh` cần default `acquireRefreshLock` trả `true`** (lenient stub
   trong `@BeforeEach`) — nếu không, method-return mặc định của Mockito cho `boolean` là `false`,
   khiến **mọi** test `refresh_*` hiện có phải trả giá `Thread.sleep(150)` thật một cách vô ích. Chỉ
   test cố ý kiểm nhánh "khoá không acquire được" mới override thành `false`.

**Nghiệm thu:** `mvn -o clean verify` — **831 case unit + 88 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 821 unit + 88 IT / 13 class — `+10` unit, `+0` IT vì phase này
không đụng repository/JPQL nào, xem hàng "Baseline test" §0.1). Không smoke test HTTP thủ công — race
Redis không tái lập được đáng tin cậy qua curl tuần tự; bộ test unit mô phỏng đúng chuỗi sự kiện đã
trace ra là bằng chứng thật.

**Breaking changes — wire: KHÔNG có** (không đổi `AuthResponse`, không endpoint mới, không mã lỗi
mới — nhánh breadcrumb-hit trả **cùng hình dạng response 200** như rotate bình thường). **Java
positional: không có** — chỉ thêm method mới trên `TokenStoreService`, không đổi constructor nào.

---

### 0.33 P5 – Serial Number Tracking (ĐÃ HOÀN THÀNH 2026-08-06)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P5). Module mới nằm trong `module/inventory` (entity/
repository) + nối vào `module/workorder` (Material Issue, Production Receipt). **Không** đụng
`module/purchasing`, `module/sales`. Migration **`V52`**.

**Hai quyết định chốt với user trước khi viết kế hoạch chi tiết (cả hai qua `AskUserQuestion`):**
1. **`Item.lotTracked` và `Item.serialTracked` loại trừ nhau** — một item chỉ là lot-tracked,
   serial-tracked, hoặc không tracking gì, không bao giờ cả hai. Validate ở service **và** DB
   (`chk_items_tracking_exclusive`).
2. **Serial-tracked production output KHÔNG có HOLD chờ QC** — khác lot. Chấp nhận đánh đổi này để
   tránh phải thêm cột `serial_id` vào `stock_balances` và viết lại toàn bộ
   `StockBalanceRepository.aggregate*` (rủi ro/quy mô lớn nhất được xác định lúc research). Serial-
   tracked output vào thẳng tồn khả dụng ngay khi `approve`, giống hệt cách item không lot-tracked đã
   hoạt động từ `D5`.

**Thiết kế cốt lõi:** `SerialNumber` mirror `InventoryLot` gần như nguyên vẹn, nhưng **mọi movement
chạm một serial luôn có `quantity = 1`** — một serial là đúng một đơn vị vật lý, không phải bucket số
lượng tuỳ ý như lot. Nhờ vậy:
- Nhận/xuất N đơn vị serial-tracked = N `StockMovement` riêng (N lần gọi `receive`/`issue`), **không**
  cần schema/API mới cho "nhiều đơn vị trong một lần gọi".
- `MaterialIssueLineRequest` (+`serialId`) và `ProductionReceiptPostRequest` (+`serialNumber`) chỉ
  thêm field **optional** — hoàn toàn additive, **không phải breaking change** như bản phác thảo gốc
  của roadmap từng dự đoán (`List<serialId>` thay `quantity`). Xuất N đơn vị dùng **N dòng** trong
  mảng `lines[]` đã có sẵn của `MaterialIssuePostRequest`, không cần list field mới.
- `stock_balances`/`StockBalanceRepository` và `MaterialReservation` **không đổi** — item serial-
  tracked dùng chung "bucket không lot" với item không tracking, và reservation vẫn thuần số lượng;
  serial cụ thể chỉ được chọn ở bước issue.

**Bất biến mới:** `B96` (mutual exclusivity, `module/inventory/CLAUDE.md`), `B97` (quantity=1 +
lifecycle AVAILABLE→ISSUED, cùng file), `B98` (quyết định #2 ở trên — không HOLD,
`module/workorder/CLAUDE.md`), `B99` (quantity=1 ở tầng receipt/issue, cùng file).

**Nợ để lại có chủ đích:** Goods Receipt (`module/purchasing`) chưa nối serial — nhận nguyên liệu
serial-tracked qua PO sẽ nổ `SERIAL_REQUIRED` (lỗi thấy được, không silently sai). Ghi chú đầy đủ:
`module/purchasing/CLAUDE.md`.

**Nghiệm thu:** `mvn -o clean verify` — **850 case unit + 89 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 831 unit + 88 IT / 13 class — `+19` unit, `+1` IT, không thêm
class IT mới; chi tiết ở hàng "Baseline test" §0.1). Không nghiệm thu mutation (khác một số phase
`C2-*`/`P3` gần đây) — bù lại bằng test rộng ở service (Item/InventoryMovementService/
MaterialIssueService/ProductionReceiptService) + `FlywayMigrationIT` cho CHECK constraint.

**Breaking changes — wire: KHÔNG có** (thuần additive: `ItemCreateRequest`/`ItemResponse` +
`serialTracked`, `MaterialIssueLineRequest`/`Response` + `serialId`/`serialNumber`,
`ProductionReceiptPostRequest`/`Response` + `serialNumber`/`serialId`, `TrackingMethod` +
`SERIAL_TRACKED`, 2 mã lỗi mới `SERIAL_REQUIRED`/`SERIAL_NOT_ELIGIBLE`). **Java positional: có** —
`InventoryReceiveCommand`/`InventoryIssueCommand`/`InventoryAdjustCommand` +2 component mỗi record
(`serialId`, `serialCode`); `InventoryMovementService`/`MaterialIssueService.issueCommand` constructor/
signature đổi. Test cũ dựng các record/constructor này đã **sửa** theo `R10`.

---

### 0.34 P6 – WO Close/Reconcile (ĐÃ HOÀN THÀNH 2026-08-06)

Nguồn: `MANUFACTURING_GAP_ROADMAP.md §3` (mục P6). Đóng nốt track `P*` — chỉ còn `P-Deferred` (chờ
tầng OT, ngoài phạm vi dự án). Migration **`V53`**. Bất biến: **`B100`**.

**Quyết định chốt với user (`AskUserQuestion`) trước khi viết kế hoạch chi tiết:** `CLOSED` **khoá
hoàn toàn** — không carve-out đọc/ghi nào sau khi đóng, thay vì phương án "cho phép điều chỉnh nhẹ"
cũng được đưa ra cân nhắc.

**Thiết kế cốt lõi:** `WorkOrderStatus.CLOSED` chỉ vào được từ `COMPLETED`, qua hành động tường minh
`WorkOrderService.close` — **không bao giờ tự động** (khác `COMPLETED`, tự sinh khi cumulative good
chạm plan, `B53`). Đây là nửa "Reconcile" của tên phase: `close()` gọi lại **đúng**
`materialReservationService.cancelActiveReservations(workOrder)` mà `cancel()` đã dùng — giải phóng
mọi reservation `ACTIVE` còn sót (component bị over-reserve nhưng chưa bao giờ issue) về lại tồn khả
dụng, vì một khi `CLOSED` thì WO không còn cách nào khác để giải phóng nó nữa.

| Thay đổi | Ở đâu |
|---|---|
| `canClose()` (chỉ `COMPLETED`) + `close(Instant)` (set `CLOSED` + `closedAt`) | `WorkOrder` |
| `WorkOrderService.close(UUID)` — `STATE_CONFLICT` (409) nếu không `COMPLETED`, reconcile rồi khoá | `WorkOrderService` |
| `POST /work-orders/{id}/close` — không nhận body, tái dùng `PERM_WORK_ORDER_MANAGE` | `WorkOrderController` |
| `WorkOrderResponse.closedAt` (additive, field cuối cùng) | DTO/mapper |
| `WorkOrderAuditAction.WORK_ORDER_CLOSED` | `AuditAction` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Bug thật do phase này phát hiện và sửa, không phải giả định.** `canReserve()` trước phase là
   **danh sách phủ định** `status != COMPLETED && status != CANCELLED`. Một giá trị enum mới không tự
   động bị một danh sách phủ định loại trừ — nếu không sửa, một WO `CLOSED` vẫn nhận reserve mới được,
   đúng loại lỗ hổng checklist `coding-rules.md §11.3` được viết ra để bắt. Đã thêm
   `&& status != CLOSED` tường minh, có test regression riêng (`WorkOrderTest.canReserve_isFalseOnceClosed`)
   và tham số hoá vào `@EnumSource` sẵn có của `MaterialReservationServiceTest`.
2. **Mọi gate khác tự động đúng, không cần sửa.** `canExecute()`, `canReceipt()`, `canRelease()`,
   `canPlan()`, và danh sách status tường minh trong `cancel()` đều là **danh sách khẳng định**
   (positive list) — một WO đã chuyển `CLOSED` (không còn là `RELEASED`/`IN_PROGRESS`/`COMPLETED`/…)
   tự động fail mọi điều kiện đó mà không cần đụng một dòng code. Đã rà đủ theo
   `grep -rn "WorkOrderStatus\." src/main`.
3. **`CapacityBoardService.LOAD_STATUSES` (đọc, không phải gate ghi) có sửa** — thêm `CLOSED` cạnh
   `COMPLETED`: lịch của một WO đã đóng vẫn là load lịch sử **thật**, đóng WO không được âm thầm viết
   lại utilization của một ngày đã báo cáo trước đó. `CANCELLED` vẫn bị loại (lý do khác: lịch của WO
   bị huỷ chưa từng là load thật). Test literal (không tautology, không so sánh hằng số với chính nó):
   `CapacityBoardServiceTest.loadStatuses_includesClosedAndCompletedExcludesCancelled`.
4. **Không permission mới** — tái dùng `PERM_WORK_ORDER_MANAGE`, cùng quyền đã gác `cancel`/`plan`/
   `release`/`update`. Không migration seed, không đụng `docs/roles-and-permissions.md`
   (`coding-rules.md C10` chỉ bắt buộc khi có permission **mới**).
5. **`closedAt` mirror `cancelledAt`/`completedAt`** — cột nullable đơn giản, **không** có `closedBy`
   riêng: ai đóng đã được `updatedBy` (JPA auditing) ghi lại, đúng cách mọi status transition khác
   trên entity này đã làm từ trước.
6. **Endpoint không nhận body** — đóng không đối kháng như cancel (spec/quyết định của user chỉ nói
   "khoá hoàn toàn", không nói gì về lý do), nên **không** thêm `reason` bắt buộc như
   `WorkOrderCancelRequest` (`F7`). Không mở rộng phạm vi ngoài yêu cầu.

**Nghiệm thu:** `mvn -o clean verify` — **864 case unit + 89 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 850 unit + 89 IT / 13 class — `+14` unit, `+0` IT ròng vì phase
này không thêm case IT mới, chỉ bump version assertion trong `FlywayMigrationIT`; xem hàng "Baseline
test" §0.1 để biết đúng test nào cộng vào đâu). Không nghiệm thu mutation riêng — bù lại bằng test ở
mọi tầng bị ảnh hưởng: domain (`WorkOrderTest`), service (`WorkOrderServiceTest`), method-security
(`WorkOrderMethodSecurityTest`), controller (`WorkOrderControllerTest`), và **một test quy hồi cho mỗi
gate ghi** (`MaterialReservationServiceTest`, `MaterialIssueServiceTest`, `ProductionReceiptServiceTest`)
chứng minh `CLOSED` bị chặn — đúng tinh thần bug thật ở hệ quả #1: gate ghi là nơi một dòng code thiếu
mới thực sự gây hại.

**Breaking changes — wire: KHÔNG có** (thuần additive: 1 endpoint mới, 1 field mới `closedAt` trên
`WorkOrderResponse`). **Java positional:** `WorkOrderResponse` +1 component (`closedAt`, cuối cùng).
Test cũ dựng `WorkOrderResponse` theo vị trí (`WorkOrderControllerTest`, `SupplySuggestionServiceTest`)
đã **sửa** theo `R10`.

---

### 0.35 D8c – Forgot-password / Account Recovery (ĐÃ HOÀN THÀNH 2026-08-06)

Nguồn: `NEXT_PHASE_PLAN.md §8` (nợ #6, phần cuối cùng — `D8a` RTR và `D8b` absolute session timeout
đã trả 2/3 trước đó). Thiết kế đầy đủ đã có sẵn từ trước ở `common/security/CLAUDE.md §4.14` +
`module/auth/CLAUDE.md` "Trách nhiệm chính" — phase này **triển khai đúng thiết kế đã viết**, không
thiết kế lại. **Không migration** (thuần Redis, không đụng schema). Bất biến: **`B101`**.

**Quyết định hạ tầng chốt với user (`AskUserQuestion`) trước khi viết kế hoạch chi tiết:** gửi email
bằng **mock/log console** — không thêm `spring-boot-starter-mail`, không SMTP/SES/SendGrid nào được
tích hợp. `EmailNotificationService` chỉ log dòng chứa reset link, không có interface (chỉ một
implementation tồn tại — `coding-rules.md §11.5` cấm abstraction speculative cho một implementation
duy nhất; nếu sau này chốt gửi email thật thì đó mới là lúc thêm interface).

| Thay đổi | Ở đâu |
|---|---|
| `PasswordResetTokenService` (mới) — `auth:reset:{token}` → userId + reverse index `auth:reset:user:{userId}`, cả hai TTL 15m | `module/auth/service/` |
| `EmailNotificationService` (mới) — mock/log, không interface | `module/auth/service/` |
| `AuthService.forgotPassword`/`resetPassword`/`adminUnlockAccount` (mới) | `AuthService` |
| `POST /auth/forgot-password`, `POST /auth/reset-password` (permit-all, đã nằm trong matcher `/api/v1/auth/**` sẵn có) | `AuthController` |
| `PATCH /admin/users/{id}/unlock` — controller `/admin` **đầu tiên** trong repo | `AdminController` (mới) |
| `AuthErrorCode.RESET_TOKEN_INVALID` (401), `AuditAction.PASSWORD_RESET`/`ACCOUNT_UNLOCKED` | `common/exception`, `common/audit` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`forgotPassword` trả `void` và không branch theo kết quả — đây là cơ chế chống account
   enumeration thật, không phải chi tiết triển khai.** Nhánh "email không tồn tại" **không** gọi
   `PasswordResetTokenService`/`EmailNotificationService`, **không** audit gì — hai nhánh phải giống
   hệt nhau ở **mọi** collaborator, không chỉ ở response text trả về client. Controller tự soạn message
   cố định, không đọc gì từ kết quả service (vì service không trả gì để đọc).
2. **Reverse index `auth:reset:user:{userId}` là bắt buộc, không phải tối ưu.** Nếu không có nó,
   `generateToken` không biết token cũ nào (nếu có) cần xoá khi user request forgot-password lần thứ
   hai — sẽ tích luỹ nhiều token sống cùng lúc, vi phạm "không cho tích lũy" mà thiết kế gốc đòi hỏi.
3. **`RESET_TOKEN_INVALID` cố ý là MỘT mã cho cả "chưa từng tồn tại" lẫn "đã hết hạn"** — Redis TTL
   không phân biệt được hai trường hợp, đúng cách `REFRESH_TOKEN_EXPIRED` đã xử lý cho refresh token.
   Không thêm `RESET_TOKEN_EXPIRED` dù bảng draft ở `error-handling.md §5.3` từng liệt kê cả hai —
   thêm một mã không ai từng ném tới là code speculative.
4. **`resetPassword` xoá token cả hai chiều** (`invalidate(token, userId)` xoá cả `auth:reset:{token}`
   lẫn `auth:reset:user:{userId}`) rồi mới force-logout toàn bộ phiên
   (`deleteAllUserTokens`+`deleteAllDeviceSessions`, cùng cặp `logoutAll` đã dùng) — thứ tự không quan
   trọng ở đây (khác `B80`/`B81` của refresh rotation, nơi thứ tự Redis là bất biến), nhưng **cả bốn
   thao tác đều bắt buộc**, thiếu một là để lại orphan key hoặc phiên cũ còn sống.
5. **`adminUnlockAccount` dùng `@PreAuthorize("hasRole('ADMIN')")` — role-based, không phải `PERM_*`
   scope-based** như phần lớn service khác trong repo gần đây. Đây là lựa chọn nhất quán: nó khớp
   cách `UserService` (`create`/`delete`/`assignRole`) đã gác các hành động ADMIN-only từ trước, không
   phải ngoại lệ mới.
6. **`AdminController` gọi thẳng `AuthService`, không qua `UserService`** dù nó thao tác lên `User` —
   ba hành động account-recovery (forgot/reset/unlock) được nhóm cùng một service theo đúng thiết kế
   đã viết sẵn từ trước, không tách theo "ai sở hữu entity nào". Đừng "dọn cho gọn" bằng cách chuyển
   `adminUnlockAccount` sang `UserService` — đó là đổi kiến trúc đã quyết định, không phải refactor.
7. **`User.lock()`/`UserStatus.LOCKED` vẫn không có call site nào** — khoá tự động hiện tại thuần
   Redis fail-count, không đụng `users.status`. `adminUnlockAccount` gọi `user.activate()` đúng theo
   thiết kế đã chốt, và tiện thể khôi phục được user bị `UserService.delete()` đưa về `INACTIVE`.
8. **`D8c` KHÔNG thêm permission mới** ⇒ `docs/roles-and-permissions.md` KHÔNG đổi (`coding-rules.md
   C10` chỉ bắt buộc khi có permission **mới**; `hasRole('ADMIN')` không phải permission catalog).

**Nghiệm thu:** `mvn -o clean verify` — **885 case unit + 89 case IT / 13 class IT, failures = 0,
errors = 0** (baseline trước phase: 864 unit + 89 IT / 13 class — `+21` unit, `+0` IT vì phase này
không đụng schema/JPQL nào; xem hàng "Baseline test" §0.1 để biết đúng test nào cộng vào đâu). Không
nghiệm thu mutation riêng — bù lại bằng test ở mọi tầng: Redis key contract (`PasswordResetTokenServiceTest`),
service (`AuthServiceTest`, cả hai nhánh forgot-password + reset-password + unlock), method-security
(`AuthMethodSecurityTest`, deny/allow `hasRole('ADMIN')`), controller (`AuthControllerTest`,
`AdminControllerTest`), và regression guard cho secret masking (`SensitiveRequestToStringTest`).

**Breaking changes — wire: KHÔNG có** (thuần additive: 3 endpoint mới, 1 mã lỗi mới, 2 audit action
mới). **Java positional: có** — `AuthService` constructor +2 tham số (`PasswordResetTokenService`,
`EmailNotificationService`). Test cũ dựng `AuthService`/`AuthServiceTest` đã **sửa** theo `R10`.

---

### 0.36 C2-1 – Audit Logs Read API (ĐÃ HOÀN THÀNH 2026-08-06)

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.3`. FE xác nhận (`docs/capstone2-api-gap-response.md §5`
câu 1, 2026-08-06): màn hình Audit dùng được với `changes[]` rỗng — đợt 1 (event-level, đã ghi từ lâu)
là đủ, **không** cần chờ đợt 2 (field-level diff qua `AuditableAspect`, vẫn chưa xếp lịch). Phase này
chỉ là read API trên dữ liệu `audit_logs` đã tồn tại và đang được ghi. Migration **`V54`** (schema) +
**`V55`** (seed permission).

**Quyết định chốt với user (`AskUserQuestion`) trước khi viết code:** `PERM_AUDIT_READ` là
**ADMIN-only** — audit trail lộ IP/user-agent/lịch sử hành động của mọi user, cùng tầng nhạy cảm với
`PERM_ORG_MANAGE`/`PERM_ACCESS_MANAGE` (cả hai đã ADMIN-only), không phải tầng `PERM_COSTING_READ`
(ADMIN+MANAGER).

| Thay đổi | Ở đâu |
|---|---|
| `AuditLog.plantId` (mới, nullable, FK `plants`) | `common/audit/AuditLog.java`, `V54` |
| `AuditLogQueryService` (mới) — `list`/`get`, tách khỏi `AuditLogService` (service đó chỉ publish, fire-and-forget) | `common/audit/` |
| `AuditLogRepository.search(...)` — 8 filter, tất cả exact-match | `common/audit/AuditLogRepository.java` |
| `AuditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc` (mới) | `common/audit/AuditLogChangeRepository.java` |
| `GET /audit-logs` (list, không `changes[]`), `GET /audit-logs/{id}` (detail, có `changes[]`) | `common/audit/controller/AuditLogController.java` (mới) |
| `PERM_AUDIT_READ` — chỉ `ADMIN` | `V55` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Cột `plant_id` chỉ dừng ở schema — quyết định phạm vi có chủ đích, không phải thiếu sót.**
   `V54` thêm cột nhưng **không** wiring populate real-time: không đụng `RequestContext`,
   `AuditLogEvent`, `AuditableAspect`, hay bất kỳ call site `@Auditable`/`auditLogService.log*()` nào.
   Mọi dòng — **cả lịch sử lẫn dòng mới tạo hôm nay** — vẫn `plant_id = NULL` cho tới khi có phase
   riêng wiring nó. Lọc theo `plantId` hôm nay **luôn trả rỗng**. Bản 2026-08-04 của
   `docs/capstone2-api-gap-response.md` từng hứa nhẹ hơn ("dòng mới sẽ có") — đã sửa lại cho khớp thật.
2. 🔴 **`changes[]` trên `GET /audit-logs/{id}` là JOIN THẬT, không phải hardcode `[]`.** Nó gọi
   `AuditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc` — bảng `audit_log_changes` **đã tồn
   tại từ `V6`** (đính chính: `common/audit/CLAUDE.md` trước đây ghi sai là "chưa implement", xem
   §9.12 file đó) nhưng **0 call site nào ghi vào nó** (đợt 2, ngoài phạm vi). Kết quả hôm nay luôn
   rỗng, nhưng sẽ tự động có dữ liệu thật ngay khi đợt 2 triển khai — không cần sửa code ở đây.
3. **List endpoint cố ý KHÔNG kèm `changes[]` mỗi dòng** — batch-query một kết quả luôn-rỗng cho mọi
   trang là lãng phí vô nghĩa. Chỉ detail endpoint mới join.
4. 🔴 **Phát hiện biến thể MỚI của lỗi `lower(bytea)` (`§0.24`), lần đầu KHÔNG liên quan `concat`/
   `like`.** Tham số `Instant` (`:from`/`:to`) chỉ xuất hiện ở vế `IS NULL` — Hibernate sinh một `?`
   positional **riêng** cho mỗi lần xuất hiện tên tham số, nên vế đó không có ngữ cảnh type nào khác
   để Postgres suy ra ⇒ `could not determine data type of parameter`. Khác lỗi cũ (toàn bộ endpoint
   500 do `concat`), lỗi này chỉ nổ khi filter đó **được dùng** (`from`/`to` khác null). Sửa bằng
   `cast(:from as timestamp) IS NULL OR a.createdAt >= :from` — chỉ cast vế `IS NULL`, giữ nguyên vế
   so sánh (tránh đụng ngữ nghĩa `timestamptz`). Test `AuditLogRepositoryIT.search_filtersByCreatedAtRange`
   là nơi duy nhất bắt được (`AuditLogQueryServiceTest` dùng mock, không thấy). Xác nhận filter
   UUID/String cùng query (`actorUserId`, `entityType`...) **không** cần cast — đã có tiền lệ hoạt động
   đúng ở `WorkOrderRepository.productItemId`/`UomRepository.status`. Ghi chi tiết:
   `common/audit/CLAUDE.md §9.12`.
5. **Không `X-Plant-Id` cross-check trên filter `plantId`** — endpoint gác bằng permission global
   ADMIN-only, không phải plant scope; cơ chế cross-check (`error-handling.md §5.6.1`) tồn tại để giữ
   session/URL của caller plant-scoped nhất quán, không áp dụng cho một read xuyên-plant.
6. `AuditLogController` đặt trong `common/audit/controller/` (subpackage mới), không phải
   `module/audit/` riêng — `common/audit` đã là nơi chứa mọi thứ audit, thêm module riêng cho 2
   endpoint là quá tay.

**Nghiệm thu:** `mvn -o clean verify` — **898 case unit + 98 case IT / 14 class IT, failures = 0,
errors = 0** (baseline trước phase: 885 unit + 89 IT / 13 class — `+13` unit
(`AuditLogQueryServiceTest` 5 + `AuditLogMethodSecurityTest` 4 + `AuditLogControllerTest` 4), `+9` IT
từ `AuditLogRepositoryIT` (**class IT thứ 14**, mới) + `+1` case trong `FlywayMigrationIT`
(`migrate_v55_grantsAuditReadToAdminOnly`); xem hàng "Baseline test" §0.1). Không nghiệm thu mutation
riêng — bù lại bằng test ở mọi tầng: service (filter forwarding, detail 404, `changes[]` từ repo thật
không hardcode), method-security (deny+allow), controller (envelope, 400 type-mismatch, 404),
repository `*IT` (8 case, mỗi filter riêng + kết hợp), `FlywayMigrationIT` (ma trận grant ADMIN-only).

**Breaking changes — wire: KHÔNG có** (thuần additive: 2 endpoint mới, 1 permission mới, 1 cột mới
trên `audit_logs`). **Java positional: không có** — chỉ thêm field/method mới, không đổi constructor
nào có sẵn.

---

### 0.37 C2-2 – Inventory Lot Lifecycle API (ĐÃ HOÀN THÀNH 2026-08-06)

Nguồn: `BACKEND_CAPSTONE2_API_GAPS.md §3.2`. FE xác nhận (`docs/capstone2-api-gap-response.md §5`
câu 2, 2026-08-06): màn hình Inventory Lots bắt buộc dẫn user sang QC disposition khi lot `HOLD` chờ
QC — giữ nguyên thiết kế đã mô tả từ trước (`NEXT_PHASE_PLAN.md §7`). **Không migration** — module
mới chỉ là read/write API trên dữ liệu `InventoryLot`/`StockBalance`/`StockMovement` đã tồn tại từ
trước, không thêm cột/bảng nào.

| Thay đổi | Ở đâu |
|---|---|
| `GET /inventory/lots`, `GET /inventory/lots/{lotId}`, `POST /inventory/lots/{lotId}/status` | `module/inventory/controller/InventoryLotController.java` (mới) |
| `InventoryLotService` (list/get/changeStatus) — reuse `InventoryMovementService.changeLotStatus` (đã có từ `F2`) | `module/inventory/service/` |
| `LotQcOriginLookupService` — entry point cross-module mới, `inventory → workorder` | `module/workorder/service/query/` (mới) |
| `InventoryPermissionGuard.hasLotAccess` — resolve `lot → item → company`, mirror `hasItemAccess` | `module/inventory/service/InventoryPermissionGuard.java` |
| `AuditAction.INVENTORY_LOT_STATUS_CHANGED` (mới) | `common/audit/AuditAction.java` |

**Quyết định chốt với user (`AskUserQuestion`) trước khi viết code:** cơ chế phát hiện "lot này có
phải qua QC trước khi thoát `HOLD` không" là **cross-module lookup thật** (`LotQcOriginLookupService`,
kiểm `ProductionReceiptLine` tồn tại **và** `QualityDisposition` chưa tồn tại cho lot đó), **không**
phải heuristic cùng-module (suy nguồn gốc từ `referenceType` của `RECEIVE` movement sớm nhất). Lý do:
heuristic có lỗ hổng thật — một lot đã QC hợp lệ một lần (thoát `HOLD`) rồi bị đưa lại `HOLD` thủ công
qua chính endpoint mới này sẽ bị heuristic chặn **vĩnh viễn** khỏi thoát `HOLD` lần nữa, vì nó không
biết QC đã từng xảy ra. Cross-module lookup không có lỗ hổng đó.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Hướng phụ thuộc mới `inventory → workorder`** — ngược với phần lớn quan hệ hiện có (`workorder`
   thường gọi **vào** `inventory`, vd `MaterialIssueService` → `InventoryMovementService`). Hợp lệ
   theo rule `C7` (đi qua lookup service, không phải repository), cùng tiền lệ `planning →
   purchasing` (`D4`) và `planning → routing` (`F5-B`). Chi tiết đầy đủ: `module/inventory/CLAUDE.md`
   B102-B103, `module/workorder/CLAUDE.md` "Entry point cho module khác".
2. **`InventoryMovementService.changeLotStatus` (đã có từ `F2`) không đổi một dòng nào.** Gate mới
   nằm ở tầng gọi (`InventoryLotService.changeStatus`, method mới), chạy **trước khi** delegate xuống
   — nhờ vậy luồng QC disposition hiện có (`ProductionReceiptService.dispositionLots`, cũng là một
   cách hợp lệ để thoát `HOLD`) hoàn toàn không bị ảnh hưởng, không cần sửa test nào của nó.
3. **Một lot có thể tồn tại ở nhiều warehouse** — `uk_stock_balances_item_warehouse_lot` unique theo
   `(item, warehouse, lot)`, không phải `(item, lot)`. `GET /inventory/lots` **bắt buộc**
   `warehouseId` (đúng tiền lệ `/inventory/balances`/`/inventory/movements`); `GET
   /inventory/lots/{lotId}` không nêu warehouse, trả `balances[]` — mảng theo từng kho.
4. **`InventoryLot` không thêm cột nào** — `manufactureDate` trên response là alias của `receivedAt`
   (đúng pattern `bomCapturedAt` của `F8`); `warehouseId` luôn resolve qua `StockBalance` (rule C14).
5. `POST /inventory/lots/{lotId}/status` chỉ nhận target ∈ {`AVAILABLE`, `HOLD`, `REJECTED`} —
   `EXPIRED` bị từ chối (`OPERATION_NOT_ALLOWED`, 422), chưa có luồng chuyển-tay-sang-`EXPIRED` nào
   đã xác lập trong repo (`coding-rules.md §11.5`, tránh code speculative).
6. **Không permission mới** — tái dùng `PERM_INVENTORY_READ` (list, get) và `PERM_INVENTORY_MOVE`
   (changeStatus, cùng quyền gác `receive`/`issue`/`adjust`). Không migration seed, không đụng
   `docs/roles-and-permissions.md` (`coding-rules.md C10` chỉ bắt buộc khi có permission **mới**).
7. `sourceMovementType`/`sourceReferenceType`/`sourceReferenceId`/`sourceAt` trên response lot lấy từ
   `RECEIVE` `StockMovement` **sớm nhất** của lot — thuần trong `module/inventory`, chỉ phục vụ hiển
   thị "nguồn gốc" cho FE, **không** dùng để quyết định gate thoát `HOLD` (đó là lý do quyết định #1
   ở trên tồn tại — cùng loại tín hiệu này KHÔNG đủ chính xác cho việc gác cổng).

**Nghiệm thu:** `mvn -o clean verify` — **925 case unit + 105 case IT / 14 class IT, failures = 0,
errors = 0** (baseline trước phase: 898 unit + 98 IT / 14 class — `+27` unit
(`InventoryLotServiceTest` 9 + `LotQcOriginLookupServiceTest` 3 + `InventoryLotMethodSecurityTest` 6 +
`InventoryLotControllerTest` 7 + `InventoryPermissionGuardTest` +2), `+7` IT trong
`StockBalanceRepositoryIT` đã có (`searchLots` 6 case + `findByLotLotId` 1 case) — **không** thêm
class IT mới, vẫn 14; xem hàng "Baseline test" §0.1). Không nghiệm thu mutation riêng — bù lại bằng
test ở mọi tầng: service (HOLD-gate cả ba nhánh: blocked/allowed/never-consulted, EXPIRED rejected
trước khi chạm bất cứ thứ gì), lookup service riêng (4 case, cả hai boolean), method-security
(deny+allow cho cả `PERM_INVENTORY_READ` lẫn `PERM_INVENTORY_MOVE`), controller (envelope, 400 type-
mismatch, 404), repository `*IT` (7 case mới: mỗi filter riêng + kết hợp + multi-warehouse lot).

**Breaking changes — wire: KHÔNG có** (thuần additive: 3 endpoint mới, không đổi DTO/permission có
sẵn). **Java positional: có** — `InventoryPermissionGuard` constructor +1 tham số
(`InventoryLotRepository`). Test cũ dựng `InventoryPermissionGuard` đã **sửa** theo `R10`
(`InventoryPermissionGuardTest`).

---

### 0.38 Bugfix P0 Auth: `/auth/refresh` Phụ Thuộc Ngầm Vào Access Token + `TOKEN_MALFORMED` Sai Nghĩa (2026-08-06)

**Không** phase, **không** migration, **không** permission mới, **không** đổi request/response DTO
trên wire. FE báo hai lỗi P0 chặn regression suite, trace `a9c41e1a07534f1d`. Đây là bugfix thứ hai
phát hiện từ log/báo cáo thật thay vì đối chiếu spec, sau `§0.24`.

| # | Lỗi FE báo | Kết luận |
|---|---|---|
| 1 | `POST /auth/refresh` trả `401 TOKEN_MALFORMED` khi không gửi access token | **Bug thật, đã sửa** |
| 2 | Access token hợp lệ dùng được ở `/auth/me` nhưng không dùng được ở Company/UOM/Work Order/Sales Order | **Không tái hiện được từ backend** — đã loại trừ CORS (xem dưới), phần chẩn đoán sai đã sửa |

**Lỗi #1 — nặng hơn báo cáo cho thấy.** `/auth/refresh` chỉ *permitAll trên danh nghĩa*:
`AuthService.refresh` đọc `authenticatedUserId` từ request attribute mà **chỉ**
`JwtAuthenticationFilter` set được, và chỉ set được khi parse **thành công** một access token từ
header `Authorization`. Header **thiếu hẳn** → trả `401 REFRESH_TOKEN_EXPIRED` (sai, nhưng không đúng
triệu chứng báo cáo). Header **có nhưng rác/rỗng** (`"Bearer "`, `"Bearer null"` — mẫu hình phổ biến
khi HTTP interceptor luôn gắn bất cứ gì đang có trong storage) → filter ném `401 TOKEN_MALFORMED` và
chặn request **trước khi** chạm validate refresh token thật. Dù kiểu nào, refresh **không** dùng được
chỉ với `{refreshToken, tokenId}` như tài liệu ngầm hứa — đúng lúc access token hỏng lại là lúc client
cần refresh nhất.

**Lỗi #2 — đã xác nhận không có đường code nào tái hiện.** `JwtTokenProvider.validateAndExtractClaims`
là hàm thuần trên chuỗi token, không tham số path. `JwtAuthenticationFilter.handleNormalPath` giống hệt
cho `/auth/me` và mọi endpoint khác. Chỉ có **một** `SecurityFilterChain`. Đã kiểm `app.cors.allowed-
headers` trong `application.yml` — `Authorization` **có** trong danh sách, loại trừ CORS chặn header.
Cách duy nhất tái hiện triệu chứng là header **không tới được** backend ở những cuộc gọi đó — rất có
thể phía client (một instance/interceptor HTTP khác không gắn `Authorization` cho các cuộc gọi đó),
**không phải** backend defect. Điều **có thể** sửa từ backend: `JwtAuthEntryPoint` (nơi request thiếu
credential rơi vào) trước đây hardcode `TOKEN_MALFORMED` cho **mọi** trường hợp — kể cả "không gửi gì
cả" — khiến lỗi #2 trông giống lỗi JWT thay vì "cuộc gọi này thiếu header". Đã sửa (xem dưới).

**Ba thay đổi:**

1. **`JwtAuthenticationFilter.BYPASS_PATHS`** — `login`/`refresh`/`forgot-password`/`reset-password`
   được filter xử lý y hệt "không có token" bất kể header chứa gì (rác, rỗng, hết hạn). 🔴
   **`logout`/`logout-all` cố ý KHÔNG có trong danh sách này** — khác 4 endpoint trên,
   `AuthService.logout`/`logoutAllDevices` vẫn đọc `authenticatedUserId` (và `logout` đọc lại header
   thô để lấy `jti`) để biết revoke cái gì, và **im lặng no-op** khi thiếu. Bypass hai endpoint này sẽ
   biến logout thành không-revoke-gì-cả mỗi khi có token hỏng đính kèm — đổi bug hiện tại lấy một lỗ
   hổng bảo mật âm thầm còn tệ hơn. Suýt mắc lỗi này khi thiết kế fix — bắt được bằng cách đọc kỹ
   `AuthService.logout`/`logoutAllDevices` trước khi viết `BYPASS_PATHS`, không chỉ nhìn `SecurityConfig`.
2. **`TokenStoreService.saveRefreshToken` ghi thêm `auth:refresh:owner:{tokenId}` → `userId`** — mọi
   caller (login, mọi lần rotate) tự động có key này, không cần sửa call site nào khác. Method mới
   `getTokenOwner(tokenId)`. `AuthService.refresh` giờ resolve `userId` qua key này thay vì đọc request
   attribute — **không** còn phụ thuộc header `Authorization` chút nào. Key **không** bị xoá tường minh
   khi logout/rotate — tự hết hạn theo cùng TTL refresh token (7 ngày), giống `:used`/`:rotated`; để nó
   sống sót ngắn hạn sau một lần xoá là vô hại vì quyết định cấp quyền thật vẫn là
   `stored.equals(refreshToken)` so với key chính. `B80`/`B81`/`B95` (RTR, absolute timeout,
   concurrent-race) **không đổi một dòng nào** — cả ba chạy **sau** khi `principal` đã resolve xong.
3. **`JwtAuthEntryPoint`** dùng `AuthErrorCode.AUTHENTICATION_REQUIRED` (mới) thay vì `TOKEN_MALFORMED`.
   An toàn: đã xác nhận handler này **chỉ** có thể bị gọi khi không có credential nào được gửi — bất kỳ
   token **được gửi** nào, hỏng hay không, đều bị `JwtAuthenticationFilter` bắt và trả lời trực tiếp
   trước khi tới tầng authorization của Spring Security.

**`JwtTokenProvider.extractClaimsFromExpired` đã xoá** — orphan sau khi bỏ cơ chế "refresh path cho
phép token hết hạn" (không còn cần thiết: refresh không đọc access token nữa). 2 test tương ứng trong
`JwtTokenProviderTest` xoá theo. `extractJtiUnchecked` (dùng cho logout) **không đổi** — khác hàm,
khác mục đích.

Chi tiết đầy đủ + Redis key mới: `common/security/CLAUDE.md §4.20` (bất biến `B37`),
`module/auth/CLAUDE.md` (bất biến `B107`). Mã lỗi mới: `.claude/rules/error-handling.md §5.3`.

**Nghiệm thu:** `mvn -o clean verify` — **942 case unit + 105 case IT / 14 class IT, failures = 0,
errors = 0** (baseline trước phase: 925 unit + 105 IT / 14 class — `+17` unit, `+0` IT vì phase này
không đụng repository/JPQL nào; xem hàng "Baseline test" §0.1). Không nghiệm thu mutation riêng — bù
lại bằng test mới ở đúng tầng bug xảy ra: `JwtAuthenticationFilterTest` (mới, class chưa từng có test
trực tiếp trước đây — 13 case: bypass path bỏ qua token rác/rỗng, logout path vẫn validate bình
thường, non-bypass path hành vi không đổi), `JwtAuthEntryPointTest` (mới), `TokenStoreServiceTest` (+3
case reverse-lookup), `AuthServiceTest` (13 test `refresh_*` sửa theo `R10` để stub
`tokenStore.getTokenOwner`/`userRepository.findById` thay vì request attribute, +2 case mới trong đó
có `refresh_neverReadsAuthorizationHeader_identityComesFromTokenIdAlone` — regression guard trực tiếp
cho lỗi #1). Xác nhận qua HTTP thật (`mvn -o spring-boot:run`, Postgres/Redis thật): login → refresh
**không** header `Authorization` → 200 (trước fix: 401 `TOKEN_MALFORMED`) → refresh **với** header
`Authorization: Bearer garbage-not-a-jwt` → 200 (cũng trước fix: 401) → gọi endpoint có bảo vệ không
header → 401 `AUTHENTICATION_REQUIRED` (trước fix: `TOKEN_MALFORMED`) → gọi `/auth/logout` với header
rác → vẫn 401 `TOKEN_MALFORMED` (xác nhận logout **không** bị bypass, hành vi giữ nguyên).

**Breaking changes — wire: KHÔNG có** (thuần additive: 1 mã lỗi mới `AUTHENTICATION_REQUIRED`, không
đổi DTO nào; hành vi `/auth/refresh` **nới lỏng** — request trước đây lỗi nay thành công, không phá
client đang chạy đúng). **Java positional: không có** — chỉ thêm method mới trên `TokenStoreService`,
không đổi constructor nào có sẵn.

---

### 0.39 FE Contract Fix: Sales Order `version` + Activate API Cho Plant/Warehouse/Item (2026-08-08)

**Không** phase, **không** migration, **không** permission mới. FE báo 2 điểm khi tích hợp
`PATCH /sales-orders/{id}` (`.claude/rules/dev-workflow.md` không áp dụng phase `C2-*`/`D*`/`P*` cho
việc này — đây cùng dạng "sửa từ phản hồi FE thật" như `§0.24`/`§0.38`, không phải track có mã).

| # | FE báo | Kết luận |
|---|---|---|
| 1 | `PATCH /sales-orders/{id}` đòi `expectedVersion` nhưng không response nào trả `version` — không có cách hợp lệ lấy giá trị phải gửi | **Đúng, đã sửa** |
| 2 | Plant/Warehouse/Item chỉ có `DELETE` (deactivate), không có API activate lại; `INACTIVE` có phải vĩnh viễn không | **Đúng — thêm activate API cho cả ba** (không làm `Company`, FE chỉ hỏi về ba resource này) |

**Sửa #1 — `SalesOrderResponse.version`:** thêm field `Long version`, `SalesOrderMapper.toResponse`
truyền `order.getVersion()`. Additive trên wire.

**Sửa #2 — Activate API:** theo đúng khuôn `Uom.activate()`/`UomController` đã có sẵn trong repo
(idempotent, `@Auditable`, permission ở tầng service, không `@PreAuthorize` trên controller — rule
`C1`).

| Endpoint | Permission (tái dùng, không mới) | Chặn khi |
|---|---|---|
| `POST /plants/{id}/activate` | `PERM_ORG_MANAGE` (scope `PLANT`) | Company cha `INACTIVE` → `422 OPERATION_NOT_ALLOWED` |
| `POST /warehouses/{id}/activate` | `PERM_ORG_MANAGE` (scope `WAREHOUSE`) | Plant cha `INACTIVE` → `422 OPERATION_NOT_ALLOWED` |
| `POST /items/{id}/activate` | `PERM_ITEM_MANAGE` (qua `inventoryPermissionGuard.hasItemAccess`, V57) | Company cha `INACTIVE` → `422 OPERATION_NOT_ALLOWED` |

**Quyết định chốt với user (`AskUserQuestion`) trước khi viết code:** activate một record con khi cha
đang `INACTIVE` phải bị **chặn** — đây là bất biến **đầu tiên** áp cho chiều *activate*; trước đó check
"cha phải `ACTIVE`" chỉ tồn tại ở chiều *create* (`createPlant`/`createWarehouse`/`createItem`).

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Bug thật phát hiện lúc smoke test thủ công qua HTTP thật, không phải lúc viết code — và không
   phải lỗi của tính năng activate, mà là hệ quả của việc thêm `version` vào response.**
   `SalesOrderService.update`/`confirm`/`cancel` đều gọi `salesOrderRepository.save(order)` rồi map
   response **ngay trong cùng transaction**. Hibernate chỉ bump field `@Version` trong bộ nhớ **lúc
   flush** — mặc định là lúc transaction commit, tức **sau** khi response đã build xong — nên response
   trả `version` **cũ hơn** con số vừa thật sự persist vào DB. Xác nhận bằng smoke test: `PATCH` trả
   `version: 0` trong khi `SELECT` trực tiếp DB cho thấy `version = 1` ngay sau đó. Hệ quả nếu không
   sửa: client tin theo response rồi gửi `expectedVersion` đó ở lần `PATCH` kế tiếp sẽ luôn lệch 1,
   y hệt bị người khác vừa sửa đè — `409 CONCURRENT_MODIFICATION` **giả**, mỗi lần đúng một lần.
2. Sửa bằng `saveAndFlush` (đã có tiền lệ trong repo: `BomService.saveAndFlush`,
   `RoutingService.saveAndFlush`) thay `save` ở **cả ba** method `update`/`confirm`/`cancel` — không
   chỉ `update` (là method duy nhất có `expectedVersion` trong request), vì `confirm`/`cancel` cũng
   trả `version` trong response và cũng cần chính xác cho lần `PATCH` kế tiếp của client.
3. `create` **không cần sửa** — entity mới có `version` khởi tạo `0`/`null`, câu `INSERT` không bump
   field đó (chỉ `UPDATE` mới bump), nên không có gì để đọc lệch.
4. Mock `returnFirstArgument()` trong test **không tự tái hiện được** độ trễ thật của Hibernate —
   test bảo vệ phải pin đúng **phương thức được gọi** (`saveAndFlush`, không phải giá trị trả về):
   `SalesOrderServiceTest.update_persistsThroughSaveAndFlush_soTheResponseVersionCanBeTrustedAsTheNextExpectedVersion`.
5. `Company` **cố ý không có** endpoint activate — cùng pattern deactivate-only vẫn giữ, mở rộng sang
   `Company` là ngoài phạm vi đúng những gì FE hỏi (`coding-rules.md §11.5`, tránh code speculative).

**Nghiệm thu:** `mvn -o clean verify` — **964 case unit + 105 case IT / 14 class IT, failures = 0,
errors = 0** (baseline trước phase: 942 unit + 105 IT / 14 class — `+22` unit, `+0` IT vì không
migration/JPQL mới; xem hàng "Baseline test" §0.1). Không nghiệm thu mutation riêng — bù lại bằng
smoke test HTTP thật qua `mvn -o spring-boot:run` (Postgres/Redis thật, port 8081): tạo
Company→Plant→Warehouse→Item → deactivate Plant → activate Warehouse trả `422` → re-activate Plant →
activate Warehouse trả `200` → activate lại (idempotent) trả `200` không đổi gì → tương tự cho Item
với Company cha → tạo Sales Order → `PATCH` hai lần liên tiếp dùng `version` của response trước làm
`expectedVersion` → cả hai `200`, `version` tăng đúng `0→1→2` (xác nhận trực tiếp bug #1 đã đóng, đối
chiếu với `SELECT version FROM sales_orders` trong DB thật).

**Breaking changes — wire: KHÔNG có** (thuần additive: 3 endpoint mới, 1 field mới `version` trên
`SalesOrderResponse`). **Java positional: không có** — không đổi constructor nào có sẵn.

---

### 0.40 Bugfix P0: Sales Order Full-Replacement `PATCH` Chết Với `lines[]` + `version` Không Bump Khi Thay Dòng (2026-08-10)

**Không** phase, **không** migration, **không** permission mới, **không** breaking change wire. Nguồn:
`BACKEND_SALES_ORDER_LINES_PATCH_DEFECT_2026-08-10.md` (FE báo, tái hiện lần đầu 2026-08-08). Đây là
bugfix thứ ba phát hiện từ báo cáo thật thay vì đối chiếu spec, sau `§0.24` và `§0.38`.

| # | Lỗi | Cách sửa |
|---|---|---|
| 1 | `PATCH /sales-orders/{id}` kèm `lines[]` trả **`RESOURCE_ALREADY_EXISTS`** ("Data constraint violation"); **không** kèm `lines[]` thì chạy bình thường | `salesOrderRepository.flush()` **giữa** `clear()` và vòng lặp add trong `replaceLines` |
| 2 | *(phát hiện thêm lúc smoke test, FE **không** báo)* PATCH **chỉ có** `lines[]` trả 200 nhưng `version` **đứng yên** | `order.setUpdatedAt(Instant.now())` trong `replaceLines` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Lỗi #1 là thứ tự flush của Hibernate, không phải mapping sai.** Trong **một** flush, Hibernate
   xếp `INSERT` entity con **trước** `DELETE` của orphan-removal. Một full replace **bắt buộc** tái dùng
   `lineNo` 1..N (`B47`/`B87` yêu cầu đúng thế), nên `INSERT` dòng mới luôn đụng
   `uk_sales_order_lines_order_line_no` với dòng cũ **chưa kịp bị xoá** ⇒ chết **toàn bộ** PATCH, không
   phải chỉ sai một dòng. `orphanRemoval = true` và `cascade = ALL` trên `SalesOrder.lines` **đều đã
   đúng** — đừng đi sửa mapping. Cách sửa là **ép thứ tự tường minh**, không phải tin vào action
   ordering của Hibernate: xoá → flush → mới build dòng thay thế.
2. 🔴 **Lỗi #2 nguy hiểm hơn lỗi #1 dù không ai báo, vì nó im lặng.** `lines` là collection **inverse**
   (`mappedBy = "salesOrder"`, FK nằm ở bảng con) ⇒ thay dòng **không** làm dirty row `sales_orders` ⇒
   Hibernate không phát `UPDATE` nào ⇒ `@Version` không bump. Hệ quả: hai request thay dòng đồng thời
   cùng đọc `version = n`, **cả hai** qua được check `expectedVersion`, request thứ hai ghi đè im lặng —
   phá đúng thứ `expectedVersion` (`B87`) sinh ra để chặn. Lỗi #1 che nó suốt: trước bản sửa này mọi
   PATCH có `lines[]` đều chết nên không ai thấy được `version` không đổi.
3. 🔴 **Đừng dùng `LockModeType.OPTIMISTIC_FORCE_INCREMENT` cho lỗi #2.** Khi header **cũng** đổi
   (chính là request FE gửi: `customerName` + `orderDate` + `note` + `lines`), entity đã dirty sẵn ⇒
   `UPDATE` bình thường bump một lần **và** force-increment bump lần nữa ⇒ **+2**, phá luôn điều kiện
   "đúng một lần" mà nó được thêm vào để bảo đảm. Làm dirty một field audit là cách duy nhất bump
   **đúng một lần** ở **cả hai** trường hợp (có/không đổi header). Giá trị `updatedAt` gán tay bị
   `@LastModifiedDate` ghi đè lúc flush — thứ cần là **tính dirty**, không phải giá trị.
4. 🔴 **Cả hai lỗi đều vô hình với mock repository, và với coverage.** `SalesOrderServiceTest` phủ kín
   `update` từ `C2-4` (5 case, kể cả case thay dòng) và **vẫn xanh** với cả hai bug — mock không có
   constraint để vi phạm và không có `@Version` thật để không-bump. `SalesOrderUpdateLinesIT` (**class
   IT thứ 15**) import service thật vào slice `@DataJpaTest` + Postgres thật; **nghiệm thu mutation**:
   bỏ dòng `flush()` ⇒ **2/4** case đỏ với **đúng** thông báo `uk_sales_order_lines_order_line_no` FE
   báo (case thứ 3 xanh có lý do đúng — nó chết trước khi tới flush). Đây là lần thứ tư repo học đúng
   bài học `R7`, sau `§0.24` (`lower(bytea)`), `§0.27` (`MultipleBagFetchException`), `§0.28`
   (`NOT NULL` sót trong `V44`).
5. **`@DataJpaTest` slice cần bật auditing tường minh** (`@TestConfiguration` + `@EnableJpaAuditing` +
   `AuditorAware` trả `Optional.empty()`): dòng do service build lấy `createdAt`/`updatedAt` (NOT NULL)
   từ auditing đúng như production. Back-fill hai cột bằng tay trong test sẽ làm câu `INSERT` đang test
   **khác** câu `INSERT` thật — mà `INSERT` chính là chỗ bug #1 sống.
6. **`Idempotency-Key` KHÔNG được implement trên endpoint này** (grep `module/sales`: 0 kết quả) — đúng
   `A2`, header đó dành cho POST ghi tồn kho, không phải PATCH master/chứng từ. Replay-safety của PATCH
   đến từ `expectedVersion`: gửi lại **cùng** payload lần hai trả **409 `CONCURRENT_MODIFICATION`**
   (version đã đi tiếp), tức không bao giờ áp dụng hai lần — nhưng **không** trả lại 200 với kết quả cũ
   như FE checklist mong đợi. Đã ghi rõ cho FE, **không** tự implement (ngoài phạm vi, chưa ai yêu cầu).

**Nghiệm thu:** `mvn -o clean verify` — **979 case unit + 110 case IT / 15 class IT, failures = 0,
errors = 0** (baseline trước bugfix: 977 unit + 106 IT / 14 class). Kiểm chứng qua **HTTP thật**
(`mvn -o spring-boot:run` port 8081, Postgres + Redis qua `docker-compose`, **không** phải
Testcontainer) trên một DRAFT dùng-một-lần, đúng 8 mục FE checklist §9: request FE báo lỗi nay
**200** (`X-Trace-Id: a3efc95f33ba430d`) · thay dòng chỉ với `lines[]` bump `version` 1→2 · thêm dòng
thứ hai (`lineNo` 1,2) · bỏ một dòng còn một · header-only vẫn 200 và **không** đụng dòng nào ·
`expectedVersion` cũ trả **409 `CONCURRENT_MODIFICATION`** · dòng thay thế `dueDate < orderDate` trả
**422** và **không** field nào dính (`version` + `customerName` + dòng cũ y nguyên — chứng minh deletes
đã flush **được rollback**) · `version` tăng **đúng một** đơn vị mỗi PATCH thành công (0→1→2→3→4→5) ·
vòng đời `confirm`→`cancel` và gate `STATE_CONFLICT` trên đơn `CONFIRMED` không đổi.

**Breaking changes — wire: KHÔNG có** (không đổi DTO, không endpoint mới, không mã lỗi mới). Hành vi
**nới lỏng**: request trước đây luôn lỗi nay thành công. **Java positional: không có.**

---

### 0.41 Fix Contract Admin RBAC: Đọc Membership Role/Scope + Unmapped Path Trả 500 (2026-08-12)

**Không** phase, **không** migration, **không** permission mới, **không** breaking change wire. Nguồn:
`ADMIN_RBAC_BACKEND_API_CONTRACT_REQUEST.md` (FE). Bất biến **`B113`** (`module/organization`).

> ⚠️ **Tài liệu nguồn là một BẢNG CÂU HỎI, không phải báo cáo lỗi.** Phần lớn nội dung của nó là xin
> xác nhận contract (idempotency, thời điểm quyền có hiệu lực, error code…) — những thứ đó trả lời
> **bằng tài liệu**, không phải bằng code. Chỉ **ba** mục là defect thật và chỉ ba mục đó được sửa;
> phần còn lại đã trả lời trong `FE_SingleTask_Response.md`. Đừng đọc file yêu cầu đó rồi tưởng mọi ô
> `☐` trong nó đều là việc phải code.

| # | Lỗi | Cách sửa |
|---|---|---|
| 1 | **Blocker.** `GET /access/roles/{roleId}/permissions` **không tồn tại** ⇒ FE chỉ có catalog toàn cục, không có cách hợp lệ nào biết role đang giữ quyền nào | `RolePermissionRepository.findPermissionsByRoleId` + `AccessControlService.listRolePermissions` + endpoint |
| 2 | `GET /access/scopes/{scopeId}/resources` thiếu — `addScopeResource` **chỉ ghi được, không đọc được** từ khi ra đời | `AccessScopeResourceRepository.findByScopeId` + `listScopeResources` + endpoint |
| 3 | 🔴 **Toàn cục, không riêng RBAC:** path không có handler trả **500** thay vì 404 | `GlobalExceptionHandler` handler thứ **9** cho `NoResourceFoundException` + `NoHandlerFoundException` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Lỗi #3 là lần thứ hai repo mắc đúng một lỗ hổng.** `§0.24` đã vá
   `MissingServletRequestParameterException` vì cùng lý do (rơi xuống catch-all ⇒ 500 cho một lỗi của
   client), nhưng chỉ vá **một** loại. Spring 6.1 đẩy request không khớp handler nào sang
   `ResourceHttpRequestHandler` ⇒ `NoResourceFoundException` ⇒ catch-all ⇒ 500. **Bài học:** khi thêm
   handler cho một exception "client gọi sai", rà luôn các exception **anh em** của nó trong cùng
   nhóm, đừng vá đúng cái vừa được báo.
2. 🔴 **Nhánh 404 mới KHÔNG được nuốt mất 405.** Path **có** tồn tại mà gọi sai verb vẫn phải là
   `HttpRequestMethodNotSupportedException` → 405 (handler 8). Có test canh **cả hai chiều**
   (`unmappedPath_returns404…` và `wrongVerbOnExistingPath_stays405`) — một test cho nhánh mới thôi
   thì không chứng minh được nó không lấn sang nhánh cũ.
3. 🔴 **`findPermissionsByRoleId` cố ý KHÔNG lọc `status`.** Permission đã deactivate mà role vẫn
   đang giữ thì vẫn phải trả về: đó là grant **có thật trong DB**. Lọc nó đi ⇒ admin thấy ô unchecked
   ⇒ lần save kế tiếp **âm thầm gỡ** một quyền không ai định gỡ. Có test riêng
   (`RolePermissionRepositoryIT.findPermissionsByRoleId_includesGrantedPermissionsThatAreNoLongerActive`).
4. **Cả hai endpoint đọc trả 404 khi role/scope không tồn tại, không trả page rỗng** — page rỗng
   không phân biệt được với "role có thật nhưng chưa được cấp quyền nào". Check tồn tại chạy **trước**
   khi query membership (`verifyNoInteractions` canh đúng chỗ đó).
5. **Chọn MỘT nguồn authoritative, đúng như FE yêu cầu:** endpoint riêng, **không** nhúng
   `permissions[]` vào `RoleResponse`. Nhờ vậy `RoleResponse`/`AccessScopeResponse` **không đổi một
   dòng nào** ⇒ zero breaking change, và list role không phải gánh một query membership cho mỗi dòng.
6. **Không permission mới** — tái dùng `PERM_ACCESS_MANAGE` đúng khuôn `B88`. Không migration seed,
   không đụng `docs/roles-and-permissions.md` (`C10` chỉ kích hoạt khi có permission **mới**).
7. **Scope resource vẫn chỉ có `add` + `read`, cố ý chưa có `remove`/`replace-all`** — FE hỏi
   "add, remove **hoặc** replace-all", tức chấp nhận backend chọn một. Đường thu hồi hiện có là
   `deactivateScope` hoặc revoke assignment. Thêm `DELETE` khi chưa ai thật sự cần là code
   speculative (`§11.5`), cùng lý do `Company` không có endpoint activate ở `§0.39`.

**Nghiệm thu mutation (2, đã revert — **2/2 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Bỏ `NoResourceFoundException.class` khỏi `@ExceptionHandler`, giữ `NoHandlerFoundException` | **1** — `unmappedPath_returns404NotAnInternalServerError` (`404 != 500`) | Tái hiện **đúng** triệu chứng FE báo. `wrongVerbOnExistingPath_stays405` **vẫn xanh** ⇒ hai nhánh độc lập thật, không phải một test che cho cả hai |
| 2 | Bỏ `findRoleById(roleId)` khỏi `listRolePermissions` | **1** — `listRolePermissions_unknownRole_throwsBeforeQueryingMembership` | ⚠️ Case thứ hai cũng đỏ nhưng **do `UnnecessaryStubbing`**, không phải kill hành vi — không tính là bằng chứng (đúng cảnh báo `§0.22` mutation #1) |

**Nghiệm thu:** `mvn -o test` — **994 case unit, failures = 0, errors = 0** (baseline trước:
979 unit; `+15` đúng bằng số case thêm mới). 🔴 **Phần IT CHƯA chạy được:** Docker Desktop trên máy
dev tắt giữa chừng (`docker info` báo pipe `dockerDesktopLinuxEngine` không tồn tại) nên
`AbstractPostgresIntegrationTest` không dựng được container ⇒ `RolePermissionRepositoryIT` (**class IT
thứ 16**, mới) **và** mọi class IT có sẵn đều lỗi như nhau — đã xác nhận bằng
`UserRoleAssignmentRepositoryIT` đỏ y hệt, nên **không** phải lỗi của test mới. Cũng **chưa** smoke
test HTTP thật vì backend cần Postgres/Redis qua Docker. **Việc còn lại:** bật Docker rồi chạy
`mvn -o clean verify` + smoke test 2 endpoint mới.
>
> ✅ **[2026-08-12, cùng ngày] Nợ nghiệm thu này đã đóng** trong lượt sửa `§0.42`: Docker bật lại,
> `mvn -o clean verify` xanh — **114 case IT / 16 class IT** đúng bằng con số kỳ vọng ghi ở trên, tức
> `RolePermissionRepositoryIT` chạy thật trên Postgres và 4 case của nó xanh. Đừng đọc đoạn "chưa đo
> được" ở trên như trạng thái hiện tại.

**Breaking changes — wire: KHÔNG có** (thuần additive: 2 endpoint mới; đổi **500 → 404** cho path
không tồn tại là sửa sai, client đang chạy đúng không bị ảnh hưởng). **Java positional: không có** —
chỉ thêm method mới, không đổi constructor nào.

---

### 0.42 Trả Nợ #27: Wire Format Ngày/Giờ Về ISO (2026-08-12)

**Không** phase, **không** migration, **không** permission mới, **không** endpoint mới. Nguồn:
`ADMIN_RBAC_BACKEND_RESPONSE_REVIEW_2026-08-12.md §4` — FE đề nghị tường minh chuyển canonical sang
ISO **và** xác nhận adapter của họ nhận cả hai định dạng, tức cửa sổ đổi wire mở ra mà không cần
release đồng thời hai bên. Đây là điều kiện còn thiếu suốt từ `§0.40` (2026-08-10) khiến nợ #27 nằm im.

| Thay đổi | Ở đâu |
|---|---|
| **Xoá** `@Bean ObjectMapper` | `RedisConfig` — thay bằng javadoc cảnh báo, xem hệ quả #1 |
| `default-property-inclusion: non_null` → **`always`** | `application.yml` — **cố ý ghi đè**, xem hệ quả #2 |
| Test guard mới `JsonWireFormatTest` (3 case) | `src/test/java/.../config/` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Đừng bao giờ khai báo `@Bean ObjectMapper` ở bất kỳ `@Configuration` nào trong repo này.**
   `JacksonAutoConfiguration` của Boot là `@ConditionalOnMissingBean` ⇒ **một** bean cùng kiểu, dù đặt
   ở config chỉ phục vụ Redis, cũng thắng luôn vai trò mapper của **toàn bộ web layer** và làm **mọi**
   dòng `spring.jackson.*` chết lặng. Không có lỗi, không có warning, không rớt test nào — chỉ có wire
   format sai suốt nhiều tháng. Cần mapper riêng cho Redis thì dựng **cục bộ trong serializer**, không
   expose thành bean.
2. 🔴 **`always` không phải giá trị cũ, và cũng không phải giá trị viết trong file trước đó.** Bỏ bean
   ObjectMapper "mở khoá" cùng lúc **cả ba** dòng `spring.jackson.*`, trong đó `non_null` sẽ **xoá mọi
   field null khỏi mọi response** — đổi payload toàn hệ thống trong khi FE chỉ yêu cầu ISO. Quyết định
   của user (`AskUserQuestion`): giữ nguyên hành vi null, ghi đè `always`, để việc bỏ null thành quyết
   định riêng có phối hợp FE. `ApiResponse` vẫn tự lược `result`/`errors` bằng `@JsonInclude` của chính
   nó — envelope không liên quan tới dòng config này, đừng nhầm hai chỗ.
3. **Hệ quả phụ có thật, đã báo FE:** `fail-on-unknown-properties: false` nay **có hiệu lực** ⇒ field
   lạ trong request body bị **bỏ qua** thay vì trả 400 như trước. Là nới lỏng (client đúng không bị
   ảnh hưởng, và đây đúng ý định đã khai báo trong file config từ đầu), nhưng nghĩa là **gõ sai tên
   field không còn báo lỗi** — chỉ còn thấy lỗi validate của field bắt buộc còn thiếu.
4. 🔴 **`@WebMvcTest` KHÔNG bắt được lớp bug này và sẽ không bao giờ bắt được** — slice không nạp
   `RedisConfig` nên nó luôn chạy với mapper "sạch" của Boot; 20/20 controller test vẫn xanh suốt thời
   gian bug sống. `JsonWireFormatTest` vì thế dựng context bằng `ApplicationContextRunner` +
   `ConfigDataApplicationContextInitializer` (đọc `application.yml` **thật**) + `JacksonAutoConfiguration`
   + chính `RedisConfig` — đúng ba thành phần của app đang chạy, mà **không** cần Docker.

**Nghiệm thu mutation (2, đã revert — **2/2 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Khai báo lại `@Bean ObjectMapper` trong `RedisConfig` | **2** — `localDate_isWrittenAsAnIsoStringNotAnArray`, `instant_isWrittenAsAnIsoUtcStringNotAnEpochNumber` | Tái hiện **đúng** triệu chứng FE báo. Case null **vẫn xanh** ⇒ ba case độc lập thật, không phải một điều kiện che cho cả ba |
| 2 | `application.yml` đổi `always` → `non_null` | **1** — `nullFields_areStillPresentOnTheWire` | Quyết định giữ null ở hệ quả #2 là **test-backed**, không phải chỉ văn xuôi trong comment |

**Nghiệm thu:** `mvn -o clean verify` — **997 case unit + 114 case IT / 16 class IT, failures = 0,
errors = 0** (baseline trước: 994 unit; `+3` đúng bằng `JsonWireFormatTest`). 🔴 **Con số IT này là số
ĐO THẬT với Docker bật** — nó đóng luôn khoản nghiệm thu còn treo của `§0.41` (`RolePermissionRepositoryIT`,
class IT thứ 16, chạy được và xanh).

**Smoke test HTTP thật** (`mvn -o spring-boot:run` port 8081, Postgres + Redis qua `docker-compose`,
**không** phải Testcontainer) — đo trên **đúng bản ghi** mà `§0.40` từng đo ra định dạng sai:

| Probe | Kết quả |
|---|---|
| `GET /sales-orders` — `orderDate` | **`"2026-08-08"`** (trước: `[2026, 8, 8]`) |
| `GET /audit-logs` — `createdAt` | **`"2026-08-10T08:43:10.431001Z"`** (trước: số epoch) |
| Field null (`description`, `plantId` trên audit log) | **vẫn có mặt** dạng `null` ⇒ quyết định #2 đúng trên wire |
| `POST /access/roles` kèm field lạ trong body | **201** (trước: 400) ⇒ xác nhận hệ quả #3 |
| `GET /access/roles/{id}/permissions` · unknown path | **200** (53 permission) · **404** ⇒ đóng nốt smoke test còn thiếu của `§0.41` |

> ⚠️ Smoke test tạo một role `ZZ_SMOKE_UNKNOWN_FIELD` trong DB dev (bằng chứng cho probe field lạ) —
> đã `deactivate`, **không** xoá được vì repo không có hard-delete cho role (`C6`).

**Breaking changes — wire: CÓ, và đây là loại đổi rộng nhất repo từng làm trong một lượt** — mọi
endpoint có field ngày/giờ đổi hình dạng (mảng/số → chuỗi ISO). Không phá client trong thực tế vì FE
đã xác nhận nhận cả hai định dạng, và đây là **sửa sai**: `docs/api-guide-for-frontend.md §2.6` hứa ISO
từ đầu. **Java positional: không có.**

#### 0.42a Hệ quả đo được của nợ #27, báo dưới dạng một bug khác (2026-08-13)

FE báo `ADMIN_RBAC_BACKEND_ASSIGNMENT_EXPIRY_GAP_2026-08-13.md`: `POST /access/assignments` kèm
`expiresAt` tương lai trả và lưu `null`, đề nghị backend kiểm 4 chỗ (DTO bind, service persist, mapper,
thêm `*IT`). **Không sửa một dòng `src/main` nào** — chẩn đoán ra đây là **cùng một** lỗi `§0.42`, chỉ
là môi trường FE test chưa chạy bản sửa.

| Build | Response `expiresAt` |
|---|---|
| Trước `§0.42` | `1787245199.000000000` — **giải mã đúng bằng `2026-08-20T16:59:59Z` FE gửi** |
| Sau `§0.42` | `"2026-08-20T16:59:59Z"` |

1. 🔴 **Bài học phương pháp, quan trọng hơn bản thân sự việc: A/B thật đã đảo ngược kết luận.** Lần
   chạy đối chứng đầu tiên "chứng minh" build cũ cũng đúng — sai, vì tiến trình app cũ **vẫn giữ cổng
   8081** (`TaskStop` không giết được tiến trình con `java`) nên cả hai lượt đo đều bắn vào **cùng một**
   build. Chỉ lộ ra khi đọc kỹ exit code của lệnh chạy nền: `BUILD FAILURE — Port 8081 was already in
   use`. ⇒ Khi A/B bằng cách restart app, **luôn** đo một field mốc để biết chắc build nào đang chạy
   (ở đây: `audit-logs.createdAt` là số hay chuỗi), đừng tin là restart đã có hiệu lực.
2. **Lỗ hổng test thật do vụ này lộ ra:** `AccessControlServiceTest.assignRole_success` **có** truyền
   `expiresAt` nhưng **không assert gì về nó** ⇒ service/mapper bỏ rơi field vẫn xanh. Đã **sửa** case
   đó theo `R10` (capture entity được `save` + assert cả response), không thêm case mới.
3. **3 tầng test nay khoá `expiresAt`:** `UserRoleAssignmentRepositoryIT.search_returnsTheExpiresAtInstantThatWasPersisted`
   (round-trip `TIMESTAMPTZ` qua **đúng** query của endpoint list), `AccessControlServiceTest.assignRole_success`
   (persist + response), `AccessControlControllerTest.assignRole_withExpiresAt_...` (bind từ body +
   trả ISO). Nghiệm thu mutation: đặt `insertable = false` cho cột ⇒ **2 case IT đỏ, 998 case unit vẫn
   xanh** — minh hoạ lại rule `R7`; mapper trả `null` ⇒ case service đỏ. Cả hai đã revert.
4. **`@WebMvcTest` không bao giờ tái hiện được lỗi gốc** (slice dùng mapper sạch của Boot), nên
   controller test ở trên khoá *binding/echo*, còn cấu hình mapper vẫn là việc của `JsonWireFormatTest`
   — hai trục khác nhau, đừng gộp.

**Nghiệm thu:** `mvn -o clean verify` — **998 case unit + 115 case IT / 16 class IT, failures = 0,
errors = 0** (`+1` unit: `AccessControlControllerTest`; `+1` IT: `UserRoleAssignmentRepositoryIT`;
`AccessControlServiceTest` sửa tại chỗ nên không cộng case). Không migration, không breaking change.
Trả lời FE: `FE_SingleTask_Response.md`.

---

### 0.43 FE Handoff: Inventory Dashboard API — Nhãn Hiển Thị + Hợp Đồng Đếm/Thứ Tự (2026-08-14)

**Không** phase, **không** migration, **không** permission mới, **không** endpoint mới. Nguồn:
`BACKEND_HANDOFF_DASHBOARD_API_REQUIREMENTS.md` (FE) — FE chuyển `/dashboard` từ mock sang API thật và
liệt kê field còn thiếu để **một** request là đủ, cộng 5 nhóm câu hỏi về ngữ nghĩa cần backend xác
nhận. Bất biến **`B114`**, **`B115`** (`module/inventory/CLAUDE.md`).

> ⚠️ **Phần lớn tài liệu nguồn là CÂU HỎI XÁC NHẬN, không phải yêu cầu code** (§5 count semantics, §5.2
> availability, §5.3 alert status, §5.5 scope/quyền, §6 error contract). Những mục đó trả lời **bằng
> tài liệu** — code đã đúng sẵn. Chỉ §4 ("field mới bắt buộc tối thiểu") và §5.4 (ordering/limit) là
> việc phải viết code. Đừng đọc checklist §10 của FE rồi tưởng mọi ô là một thay đổi backend.

| Nhóm | Nội dung |
|---|---|
| A | `InventoryDashboardResponse` + `generatedAt` (`Instant`, không cache — mọi số đọc live) |
| B | `InventoryAlertLineResponse` + `uomCode`, `onHandQuantity`, `reservedQuantity`, `qualityHoldQuantity`, `shortageQuantity` (5 field, additive) |
| C | `DashboardRecentMovementResponse` (**mới**) thay `StockMovementResponse` trong `recentMovements[]` |
| D | `lowStockLimit`/`movementLimit` (mặc định 10, kẹp `[1,20]`), thứ tự sắp xếp cố định cho cả hai mảng |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`shortageQuantity` CỐ Ý lệch công thức FE đề xuất, và đây là quyết định nghiệp vụ chứ không
   phải hiểu nhầm.** FE đề nghị `max(0, reorderPoint − available)`. Trong repo này `resolveStatus`
   xếp `LOW_STOCK` là dải **giữa** hai ngưỡng (`available > reorderPoint` nhưng `< safetyStock`), và
   cấu hình thường gặp là `safetyStock ≥ reorderPoint` ⇒ công thức FE đề xuất trả **`0` cho mọi dòng
   `LOW_STOCK`** — đúng những dòng cần con số đó nhất. Đang dùng
   `max(0, max(safetyStock, reorderPoint) − available)`; với chính ví dụ số FE đưa (safety 20, reorder
   100, available 77) hai công thức **cho cùng kết quả 23**, nên không có gì phải đàm phán lại trừ khi
   FE đổi ý về convention ngưỡng.
2. 🔴 **`left join fetch m.lot` trong `findRecentByWarehouseIds` là load-bearing, không phải tối ưu.**
   Đổi thành inner join (rất dễ khi ai đó "dọn" ba dòng fetch cho đều nhau) làm **mọi** movement của
   item không lot-tracked biến mất khỏi feed — mà phần lớn movement thật là loại đó. Nghiệm thu
   mutation: **3/3** case của `StockMovementRepositoryIT` đỏ, trong khi `InventoryAlertServiceTest`
   (mock repository) **vẫn xanh** — lần thứ năm repo gặp đúng bài học `R7` sau `§0.24`, `§0.27`,
   `§0.28`, `§0.40`. Đó là lý do `StockMovementRepositoryIT` (**class IT thứ 17**) tồn tại.
3. 🔴 **`Map.of()` ném NPE khi `get(null)`.** Movement ghi ngoài request của user có `created_by =
   null`; nếu tra thẳng `usernames.get(movement.getCreatedBy())` thì batch map rỗng/immutable làm nổ
   **cả** endpoint. Guard `getCreatedBy() == null ? null : …` có test riêng (mutation bỏ guard ⇒ 1 case
   đỏ với `NullPointerException`).
4. **`getAvailableQuantitiesByWarehouse` bị THAY, không phải bổ sung** — `getStockQuantitiesByWarehouse`
   trả cả 4 số (`onHand`/`reserved`/`qualityHold`/`available`) trong **cùng một** query. Consumer duy
   nhất của method cũ chính là dashboard, và nó cần cả bốn; giữ cả hai là hai query cho cùng dữ liệu
   (`C14`). `StockAvailabilityByWarehouseProjection` đã xoá theo (orphan do chính thay đổi này tạo ra).
5. **Không clamp `max(0, …)` cho availability** dù FE đề nghị. `chk_stock_balances_reserved_quantity`
   (`V16`) + `chk_stock_balances_quality_hold_quantity` (`V56`) đã ép `reserved + qualityHold <=
   quantity` trên **từng dòng** ⇒ tổng không thể âm. Clamp chỉ có tác dụng **che** một vi phạm
   constraint nếu nó xảy ra thật — thà để lộ ra.
6. **Limit kẹp `[1,20]` thay vì trả 400**, đúng tiền lệ `PageableFactory` (`A4`, `D7b.3`). Giá trị
   **không parse được** (`?lowStockLimit=many`) vẫn là **400 `VALIDATION_ERROR`** qua handler
   `MethodArgumentTypeMismatchException` — hai chuyện khác nhau, có test cho cả hai.
7. **Mặc định giữ 10, không đổi thành 5 như FE đề xuất** — đổi mặc định là âm thầm thu hẹp payload của
   một endpoint đang chạy; FE muốn 5 thì truyền `?lowStockLimit=5`. Đã nói rõ để FE phản hồi nếu muốn.
8. **`referenceNo` (số chứng từ nghiệp vụ) cố ý CHƯA làm.** `referenceType` là text tự do do service
   ghi (`WORK_ORDER`, `GOODS_RECEIPT`, `MANUAL_RECEIPT`, …) ⇒ resolve nó cần một lookup cross-module
   **cho mỗi loại**, tức nhiều hướng phụ thuộc mới cho một field FE xếp vào nhóm "nên có". Không nằm
   trong danh sách tối thiểu §4 của FE.
9. **Không đụng `X-Plant-Id`.** `§5.6.1` bắt cross-check khi endpoint có **hai** nguồn plant;
   `scopeType`/`scopeId` ở đây là đa hình (`COMPANY`/`PLANT`/`WAREHOUSE`) và bản thân nó **là** scope
   được `@PreAuthorize` gác — không có giá trị thứ hai để đối chiếu.
10. **`totalWarehouseCount` tính cả kho `INACTIVE`** (`OrganizationLookupService.resolveScope` không
    lọc status) — giữ nguyên có chủ đích: hàng trong kho đã ngừng hoạt động vẫn là hàng có thật, và
    `resolveScope` còn được MRP/reservation dùng chung nên đổi nó là đổi hành vi hoạch định.

**Nghiệm thu mutation (3, đã revert — **3/3 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | `left join fetch m.lot` → `join fetch m.lot` | **3/3** `StockMovementRepositoryIT` | Unit test dùng mock **vẫn xanh** cùng lượt chạy ⇒ minh hoạ trực tiếp `R7`; đây là mutation giá trị nhất của lượt này |
| 2 | `shortageQuantity` dùng `reorderPoint` một mình | **1** — `listAlerts_lineCarriesTheUnitTheAvailabilityTermsAndTheShortage` | Assert bằng **con số nghiệp vụ** (13 = max(20,8) − 7), không phải `isNotNull` (`R6`) |
| 3 | Bỏ guard `getCreatedBy() == null` | **1** — `getDashboard_recentMovementsCarryLabelsAndResolveActorsInOneBatch` (`NullPointerException`) | Ledger row không có actor là trường hợp **thật**, không phải phòng thủ giả định |

**Nghiệm thu:** `mvn -o clean verify` — **1009 case unit + 119 case IT / 17 class IT, failures = 0,
errors = 0** (baseline trước: 998 unit + 115 IT / 16 class).

**Smoke test HTTP thật** (`mvn -o spring-boot:run` port 8081, Postgres + Redis qua `docker-compose`,
**không** phải Testcontainer). Dựng một fixture dùng-một-lần đúng theo "Definition of Ready" §9 của FE
(1 company → 1 plant → 2 warehouse → 3 item, phủ đủ `OK` + `LOW_STOCK` + `REORDER_NEEDED` + 4 movement,
trong đó **cùng một item ở hai kho** để chứng minh hai dòng độc lập):

| Probe | Kết quả |
|---|---|
| `GET /reports/inventory-dashboard?scopeType=PLANT` | **200**, `X-Trace-Id: aeb44aafa5e14120`, `generatedAt` ISO, 3 alert line xếp `REORDER_NEEDED`(20) → `REORDER_NEEDED`(15) → `LOW_STOCK`(5) |
| Nhãn trên `recentMovements[]` | `itemCode`/`itemName`/`uomCode`/`warehouseCode`/`warehouseName`/`actorUsername = "admin"` đủ, **không** cần gọi thêm API nào |
| `scopeType=WAREHOUSE` + `lowStockLimit=2&movementLimit=2` | **200**, đúng 1 kho / 2 dòng / 2 movement (kẹp có tác dụng) |
| Thiếu `scopeId` · `scopeType=GALAXY` | **400** `VALIDATION_ERROR` (kèm `errors[0].field`) |
| `scopeId` không tồn tại | **404** `ENTITY_NOT_FOUND` |
| Không gửi token | **401** `AUTHENTICATION_REQUIRED` |
| `manager.a` (scope PLANT-A) gọi plant lạ | **403** `PERMISSION_DENIED` — không trả dữ liệu rỗng giả |

> ⚠️ Fixture để lại trong DB dev (company `DASH<epoch>`): dữ liệu demo dùng-một-lần, không đụng dữ
> liệu FE. Id cụ thể ghi trong `FE_SingleTask_Response.md` để FE dùng luôn làm scope demo.

**Breaking changes — wire: có, hẹp.** `recentMovements[]` đổi từ `StockMovementResponse` sang
`DashboardRecentMovementResponse`: **thêm** 7 field nhãn, **mất** `idempotencyKey` (chỉ mảng này; `GET
/inventory/movements` **không đổi một dòng nào**). FE đang ở mock nên không có client nào đang đọc
field đó. Alert line và `generatedAt` là thuần additive. **Java positional: có** —
`InventoryAlertLineResponse` +5 component (cuối record), `InventoryDashboardResponse` +1 (`generatedAt`,
cuối) và đổi kiểu phần tử `recentMovements`, `InventoryAlertService` constructor +1 tham số
(`UserLookupService`), `InventoryAlertService.getDashboard` +2 tham số. Test cũ **sửa** theo `R10`,
không xoá.

---

### 0.44 Trả Lời `live-data-audit.md`: Available Theo Lot Status + Idempotency Cho Planning Run (2026-08-14)

**Không** phase, **không** permission mới, **không** endpoint mới. Migration **`V58`**. Nguồn:
`live-data-audit.md` + `fixture-ids.json` (FE, cùng ngày) — FE seed dữ liệu live cho bộ slide và nêu
**3 mục "Backend support required"**. Bất biến **`B116`** (`module/inventory`), **`B117`**
(`module/planning`).

> ⚠️ **Ba mục FE báo có ba kết cục khác nhau — đừng đọc lướt thành "sửa cả ba".** 1 bug thật, 1 gap
> thật, **1 báo nhầm**. Mục #3 (convert suggestion "không atomic") **không có defect nào**; nó là hệ
> quả nhìn thấy được của mục #2, và bằng chứng bác bỏ nằm ở dữ liệu thật, không phải ở suy luận.

| # | FE báo | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| 1 | Lot `HOLD`/`REJECTED` vẫn báo available > 0 | 🔴 **Bug thật, đã sửa** | `StockBalance.availableQuantity()` là phép tính thuần theo dòng, **không đọc `lot.status`**; query DB xác nhận đúng số FE báo (HOLD → 2, REJECTED → 1). Trong khi `StockBalanceRepository.aggregate*` **có** lọc ⇒ **một hệ thống, hai con số** cho cùng lô hàng |
| 2 | `POST /planning-runs` bỏ qua `Idempotency-Key` | 🟡 **Gap thật, đã implement** | `grep` module `planning` = **0** tham chiếu header; `mrp_runs` không có cột nào. 3 run trùng có thật trong DB. `best-practices.md A2` lại **đang hứa** MRP run có idempotency ⇒ tài liệu nói dối về code |
| 3 | Convert suggestion không atomic | ✅ **KHÔNG phải lỗi, không sửa dòng nào** | `work_orders.planning_proposal_id` của WO họ trích dẫn trỏ tới suggestion `80e4985c` (run `EA182AFB`) — suggestion đó **đang `CONVERTED`** với reference đúng. Suggestion FE nhắc (`492763fd`) thuộc run **khác** (`63033D7C`), chưa từng convert: lần thử chết vì trùng `workOrderNo` và **rollback sạch** — đúng thiết kế |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Bug #1 sửa ở MAPPER, không sửa domain method — và đây là ranh giới an toàn, không phải lười.**
   `StockBalance.availableQuantity()` có **15 call site**, trong đó **3 gate ghi tồn kho**
   (`InventoryMovementService:561`, `WorkOrderExecutionSupport:104`, `MaterialReservationService:114`)
   gọi nó **sau khi** đã tự validate lot status (`B3`) ⇒ sửa domain method **không** sửa được gì cho
   chúng, nhưng **sẽ** làm đường QC `adjust` rút hàng khỏi lot `REJECTED` (`§0.13`) nổ
   `INSUFFICIENT_STOCK`. Fix nằm ở `InventoryMapper.issuableQuantity`, dùng ở **cả 3** chỗ map response.
2. 🔴 **`onHandQuantity` phải giữ số thật.** Hàng `HOLD` vẫn tồn tại vật lý — chỉ `availableQuantity`
   về `0`. Giấu luôn on-hand là phá màn hình kiểm kê và biến một bug hiển thị thành một bug dữ liệu.
3. **`qualityHoldQuantity` cố ý KHÔNG nhận lượng của lot `HOLD`** dù FE đề nghị. Cột đó là carrier QC
   cho hàng **không** lot-tracked (`B2`/`V56`); nhồi thêm nghĩa thứ hai vào nó thì `REJECTED` vẫn
   không thuộc "quality hold", nên FE vẫn phải đọc `lot.status` — tức đổi mà không đóng được gì.
4. 🔴 **Bug #2 đẻ ra "bug" #3, và đó là bài học đáng nhớ nhất của lượt này:** mỗi run MRP sinh **một
   bộ suggestion song song** cho cùng demand. Ba run trùng ⇒ ba bộ proposal; convert proposal của run
   này rồi đọc proposal của run kia thì trông y hệt "transaction không atomic". Khi một client báo lỗi
   nghiệp vụ "biến mất", hãy kiểm **id của bản ghi họ trích dẫn** trước khi đọc code — 2 câu SQL đã
   bác bỏ toàn bộ mục #3.
5. **Run `FAILED` vẫn chiếm key** (row ghi trước khi calculate, `run()` nuốt exception rồi đánh dấu
   `FAILED`). Retry sau thất bại phải **đổi key**. Đã ghi javadoc + tài liệu FE, không để ngầm.
6. **`saveAndFlush` cho row run đầu tiên**: key phải được chiếm ở DB **ngay**, nếu không một request
   trùng key chạy song song sẽ tính hết cả run rồi mới đụng constraint — đúng thứ tính năng này sinh
   ra để tránh (bài học save-vs-flush của `§0.39`, áp dụng lần thứ hai).
7. **Header `Idempotency-Key` là TUỲ CHỌN.** `IdempotencySupport.normalizeKey` **ném lỗi** khi thiếu
   key, nên chỉ được gọi trong nhánh có header — ép bắt buộc là breaking change với mọi client đang
   chạy.
8. **Hai phát hiện thêm trong artifact của FE** (họ chưa biết, đã báo lại):
   `fixture-ids.json` ghi `planningRun = RUN-63033D7C` nhưng WO dùng cho slide lại sinh từ
   `RUN-EA182AFB` ⇒ hai slide sẽ nói khác nhau về cùng một dòng công việc; và mojibake `BÃ...` **không
   phải "legacy data"** mà do script seed của FE — `bom_headers.description` = `"Cấu trúc sản xuất
   chuẩn cho " + <tên item>` với **tiền tố sạch, chỉ phần tên item hỏng**, trong khi `items.name` trong
   DB sạch và backend **không có dòng code nào** ghép chuỗi đó (grep = 0 hit) ⇒ chuỗi hỏng do client
   gửi lên, sửa tay vô ích vì reseed sẽ hỏng lại.

**Nghiệm thu mutation (4, đã revert — **4/4 đụng `src/main` hoặc migration**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | `issuableQuantity` bỏ nhánh `status != AVAILABLE` | **7** — 6 case `InventoryMapperTest` + `InventoryLotServiceTest.list_lotOnHold_...` | Khoá ở **cả hai** tầng (mapper thuần và service thật), không phải chỉ một |
| 2 | Bỏ nhánh replay khỏi `MrpRunService.run` | **2** — `run_replayedKeyWithSamePayload_...` (NPE vì chạy tiếp vào luồng đầy đủ), `run_replayedKeyWithDifferentPayload_...` | ⚠️ Case thứ ba đỏ vì `UnnecessaryStubbing`, **không** tính là kill (đúng cảnh báo `§0.22`) |
| 3 | Giữ replay nhưng bỏ `ensureSamePayload` | **1** — `run_replayedKeyWithDifferentPayload_throwsIdempotencyConflictBeforeAnyWrite` | Replay đúng ≠ replay an toàn: thiếu vế này là **âm thầm** trả run cũ cho một payload khác |
| 4 | Xoá `UNIQUE` khỏi `V58` (**migration**) | **1** — `FlywayMigrationIT.migrate_v58_...` | Bảo đảm thật nằm ở **DB**, không phải ở nhánh `if` trong service |

**Nghiệm thu:** `mvn -o clean verify` — **1025 case unit + 120 case IT / 17 class IT, failures = 0,
errors = 0** (baseline trước: 1009 unit + 119 IT). ⚠️ Docker Desktop **tắt giữa chừng** lần chạy đầu
(`Could not find a valid Docker environment` ⇒ **mọi** class IT lỗi như nhau) — đúng bẫy `§0.41`; phải
bật lại rồi đo lại, đừng đọc lần chạy đó như kết quả thật.

**Smoke test HTTP thật** (`mvn -o spring-boot:run` port 8081, Postgres + Redis qua `docker-compose`) —
đo trên **đúng** bản ghi FE trích dẫn, không phải fixture tự dựng:

| Probe | Kết quả |
|---|---|
| `GET /inventory/lots?warehouseId=dc5d66e5-…` | HOLD `C2-DEMO-LOT-0810154309` → `available 0` (trước: `2`), `onHand` vẫn `2`; REJECTED `SLIDE-260814-FG-REJECTED` → `available 0`, `onHand` vẫn `1` |
| Cùng warehouse, lot `AVAILABLE` (`FE0-LOT-2107`, `SLIDE-260814-FG-PARTIAL`) | **không đổi** (`5`, `3`) ⇒ sửa không quá tay |
| `GET /inventory/balances?warehouseId=dc5d66e5-…` | y hệt — hai read model nay nói cùng một con số |
| `POST /planning-runs` ×2 cùng key + payload | Cả hai trả **`RUN-CCBDDAFF`**, `count(mrp_runs)` 10 → **11** (trước đây sẽ là 12) |
| Cùng key, đổi `horizonEndDate` | **409 `IDEMPOTENCY_CONFLICT`** |
| **Không** gửi header | **201**, run mới `RUN-38B890AB`, `idempotency_key = NULL` ⇒ không hồi quy |
| Trace id của request thành công | `692dcfbfcab04400` |

**Breaking changes — wire: CÓ (hẹp nhưng thấy được trên UI).** `availableQuantity` của dòng lot
`HOLD`/`REJECTED`/`EXPIRED` đổi từ số dương → `0` trên `GET /inventory/balances`,
`GET /inventory/lots`, `GET /inventory/lots/{lotId}`. Là **sửa sai** (số cũ mâu thuẫn với chính
MRP/dashboard của cùng hệ thống). `Idempotency-Key` trên `/planning-runs` là **tuỳ chọn** ⇒ **không**
breaking. **Java positional:** `MrpRunService.run(...)` +1 tham số, constructor +1
(`IdempotencySupport`). Test cũ **sửa** theo `R10`, không xoá.

---

### 0.45 Bộ Dữ Liệu Demo `VIETBIKE` + Sửa Lỗi P0 `MultipleBagFetchException` (2026-08-14)

**Không** phase, **không** migration, **không** permission mới, **không** breaking change wire.
Yêu cầu của user: *"seed full data lại từ đầu để chuẩn bị cho demo, đặt tên rõ ràng, đẹp, dễ đọc dễ
nhớ"*. Bất biến bị sửa: mục 4 của `module/shift/CLAUDE.md` (viết lại).

**Bốn quyết định chốt với user (`AskUserQuestion`) trước khi viết code:** seed **qua REST API** chứ
không INSERT thẳng · phủ **đầy đủ mọi trạng thái** chứ không chỉ một luồng happy-path · chủ đề **nhà
máy xe đạp** · **xoá sạch DB dựng lại từ đầu** (script cố ý **không** idempotent). Quyết định thứ
năm phát sinh giữa chừng: **sửa lỗi backend** thay vì né trong dữ liệu seed.

| Thành phần | Nội dung |
|---|---|
| `scripts/demo/seed-demo.sh` | Orchestrator, source lần lượt 15 stage trong **cùng một shell** |
| `scripts/demo/lib/common.sh` | `call` / `call_expect` / `assert_status` / `remember` / date helper / guard |
| `scripts/demo/lib/catalogue.sh` | **Toàn bộ** mã, tên, số lượng. Không có một lời gọi HTTP nào |
| `scripts/demo/stages/*.sh` | 15 stage, từ preflight tới manifest |
| `demo-ids.json` | 170 định danh, sinh lúc chạy (không commit) |
| `docs/demo-dataset-guide.md` | Bảng tra cứu khi thuyết trình + kịch bản 8 phút |
| `.gitattributes` | `*.sh text eol=lf` — CRLF trên dòng `set -euo pipefail` phá script theo kiểu trông như lỗi API |

**🔴 Lỗi P0 do chính bộ dữ liệu này phát hiện — phần quan trọng nhất của lượt làm việc.**
`WorkCalendarRepository.findWithWeeklyShiftsByWorkCalendarId` khai báo `@EntityGraph` gồm
`"weeklyShifts.shift.breaks"` cạnh `"weeklyShifts"` ⇒ join-fetch **hai** bag ⇒
`MultipleBagFetchException` ⇒ **mọi** `POST /work-orders/{id}/release` mà tổ sản xuất có gắn lịch,
và lịch đó dùng ca **có giờ nghỉ**, trả **500 `INTERNAL_SERVER_ERROR`**. Tức là mọi lịch nhà máy
thực tế. Sửa: bỏ `.breaks` khỏi graph, để nó lazy-load trong cùng transaction.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Lần thứ BA repo dính đúng bẫy này** (sau `§0.27` `operations`+`componentLines`, `§0.29`
   `weeklyShifts`+`exceptions`). Điểm khác của lần này, và là lý do nó lọt: hai lần trước **cả hai
   bag nằm trên cùng một entity** nên nhìn graph là thấy; lần này bag thứ hai nằm **cách một
   association** (`weeklyShifts.shift.breaks`). ⇒ Quy tắc: trước khi thêm path vào một
   `@EntityGraph` đã có collection, **đi hết association** và hỏi path đó có giải ra `List` không —
   đừng chỉ nhìn các field của entity gốc.
2. 🔴 **Không test nào bắt được, và lý do đáng ghi hơn bản thân lỗi.** Unit test mock repository nên
   **không bao giờ dựng câu query**; các `*IT` có sẵn tuy có `Shift` nhưng **không ca nào có
   break**, nên bag thứ hai chưa từng bị chạm. Bộ dữ liệu demo là thứ đầu tiên trong repo có đủ
   **ca có giờ nghỉ + lịch trỏ tới nó + một WO được release**. `WorkCalendarLookupServiceIT` (class
   IT thứ **18**) là guard. Đây là bài học `R7` lần thứ **sáu** (sau `§0.24`, `§0.27`, `§0.28`,
   `§0.40`, `§0.43`).
3. ⚠️ **Bản nháp của bản sửa có một dòng thừa mà mutation đã bác bỏ.** Nó thêm
   `weeklyShifts.forEach(w -> w.getShift().getBreaks().size())` vào `loadWithExceptions` kèm javadoc
   khẳng định "thiếu dòng này sẽ âm thầm báo dư giờ làm". Chạy mutation bỏ dòng đó ⇒ **cả 4 case vẫn
   xanh**: `WorkingWindowCalculator` chạy trong đúng transaction ấy và tự trigger load, đúng bằng số
   query. Dòng đó chỉ **trông giống** biện pháp an toàn. Đã gỡ, javadoc viết lại theo sự thật đo
   được. Bài học: một khẳng định trong comment cũng phải qua mutation như một assertion.
4. 🔴 **Seed phải qua API, không INSERT thẳng** — mã chứng từ sinh ở `@PrePersist` (`B68`, `B79`),
   `stock_balances` là **projection** của `stock_movements`, WO chụp ảnh BOM/routing lúc create
   (`B12`, `B49`), `@Version`/`created_by`/audit do tầng service và AOP ghi. INSERT tay tạo ra những
   dòng mà chính hệ thống **không bao giờ sinh ra được**.
5. 🔴 **409 khi release KHÔNG chứng minh WO đã vào `BLOCKED`.** `WorkOrderService.release` có **ba**
   guard và **hai** trong số đó ném cùng `STATE_CONFLICT`, nhưng chỉ guard material-readiness mới
   persist `BLOCKED` (guard "BOM không có dòng nào" chạy trước và không persist gì). Vì thế mọi bước
   trong stage 70 đều `assert_status` đọc lại sau khi gọi — thiếu nó thì script "chạy thành công"
   trong khi WO nằm ở `DRAFT`.
6. 🔴 **`reserveAutomatically` resolve phạm vi PLANT, không phải kho đầu ra của WO.** Muốn một WO
   thiếu vật tư một cách chắc chắn thì component phải vắng mặt ở **mọi kho của nhà máy** — đó là vai
   trò của `BANH-20` (không nhập kho ở đâu cả). Đừng "tiện tay" nhập kho cho nó.
7. 🔴 **`READY` vs `WARNING` của đề xuất MRP do PHẠM VI KHO CỦA LƯỢT CHẠY quyết định, không phải do
   vật tư.** `WARNING` chỉ đến từ `SYSTEM_FALLBACK_USED` = không có `ItemWarehouseSetting` `ACTIVE`
   cho **kho của lượt chạy**. Bộ dữ liệu tạo đúng **một** dòng như vậy (`XE-PHO-26` @ `KHO-TP`,
   hai ngưỡng bằng 0) để một lượt chạy cho ra cả hai trạng thái. Stage 99 **không được** ghi đè nó.
8. **Ngưỡng cảnh báo tồn kho tính TỪ tồn khả dụng thật ở cuối lượt seed**, không hardcode: tới bước
   đó reservation/issue/QC/goods-receipt đã dịch chuyển hết các con số, số cứng chọn từ đầu sẽ trôi
   sang nhóm khác. Kết quả đo được: **7 `OK` / 4 `LOW_STOCK` / 5 `REORDER_NEEDED`**.
9. **Mốc lịch neo vào "thứ Hai kế tiếp", không phải "hôm nay + N".** Bản đầu dùng `+2 ngày`, chạy
   hôm thứ Sáu ⇒ rơi vào Chủ nhật ⇒ bảng năng lực báo quá tải vì **lý do nhàm chán** (không ai làm
   hôm đó, capacity 0, `utilizationPercent` `null`) thay vì lý do cần minh hoạ. Và `TO-HAN` phải để
   `capacityUnits = 1`: năng lực = phút làm việc **nhân** số tổ, để 4 thì 1240 phút tải trên 3540
   phút năng lực = 35%, không quá tải. Sau khi sửa: **140.11%, `overload = true`**.
10. **Hàm tạo entity phải set biến toàn cục `NEW_ID`, không được `echo` rồi gọi trong `$(...)`** —
    command substitution chạy ở **subshell**, nên `remember` bên trong không lưu được gì về shell
    cha: entity có thật trong DB nhưng biến mất khỏi `demo-ids.json`.
11. **`extract` phải đọc stdin dạng bytes rồi decode UTF-8 tường minh.** `json.load(sys.stdin)` trên
    Windows dùng code page ANSI (cp1252) và chết với `'charmap' codec can't decode byte 0x8d` —
    nhưng chỉ khi byte đó tình cờ rơi vào payload, nên nó chạy đúng cả trăm lần rồi hỏng ở một cái
    tên không ai sửa. Phát hiện lúc viết smoke test, không phải lúc seed.
12. **Manifest lồng theo dấu chấm, mà username có dấu chấm** ⇒ `users.quanly.hanoi` bung thành
    `users → quanly → hanoi`. Đã đổi: khoá là slug (`quanly_hanoi`), username thành **giá trị**.

**Nghiệm thu mutation (2, đã revert — 2/2 đụng `src/main`):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Trả `"weeklyShifts.shift.breaks"` vào `@EntityGraph` | **4/4** `WorkCalendarLookupServiceIT` | Tái hiện **đúng** `MultipleBagFetchException` của lỗi production |
| 2 | Bỏ vòng lặp `.size()` cho `breaks` khỏi `loadWithExceptions` | **0** — vẫn xanh | 🔴 Kết quả **giá trị nhất**: bác bỏ chính khẳng định trong bản nháp javadoc, dòng code đó là thừa. Xem hệ quả #3 |

**Nghiệm thu:** `mvn -o test` — **1025 case unit / 127 class, failures = 0, errors = 0** (đúng bằng
baseline `§0.44`, bản sửa không phá gì). `mvn -o verify` phần IT — **124 case IT / 18 class IT,
failures = 0, errors = 0** (baseline: 120 IT / 17 class; `+4` IT từ `WorkCalendarLookupServiceIT`,
class IT thứ 18).

**Nghiệm thu bộ dữ liệu** (Postgres + Redis qua `docker compose`, backend thật, **không** phải
Testcontainer) — census SQL trên mọi bảng chứng từ cho thấy **đủ toàn bộ** trạng thái mục tiêu:

| Bảng | Trạng thái có mặt |
|---|---|
| `work_orders` | DRAFT · PLANNED · BLOCKED · RELEASED · IN_PROGRESS · COMPLETED · CLOSED · CANCELLED (**đủ 8**) |
| `production_receipts` | DRAFT · PENDING_APPROVAL · APPROVED · REJECTED (**đủ 4**) |
| `inventory_lots` | AVAILABLE · HOLD · REJECTED |
| `sales_orders` | DRAFT · CONFIRMED · IN_PRODUCTION · PARTIALLY_FULFILLED · FULFILLED · CANCELLED (**đủ 6**) |
| `purchase_requisitions` | DRAFT · APPROVED · CONVERTED · REJECTED · CANCELLED (**đủ 5**) |
| `purchase_orders` | DRAFT · SENT · PARTIALLY_RECEIVED · RECEIVED · CANCELLED (**đủ 5**) |
| `goods_receipts` | POSTED · CANCELLED |
| `supply_suggestions` | status DRAFT/APPROVED/REJECTED/CONVERTED · exception READY/WARNING/BLOCKED · type MAKE/BUY |
| `planning_demands` | OPEN · CANCELLED |

Smoke API: dashboard 7/4/5 ba nhóm cảnh báo · lô `HOLD`/`REJECTED` báo `available = 0` mà `onHand`
vẫn còn hàng · bảng năng lực có ngày `TO-HAN` **140.11% quá tải** và `TO-KCS` `capacity = null` ·
`SO-1004` `PARTIALLY_FULFILLED` 6/10 · variance ra tiền thật (chuẩn 116,4tr, thực tế 17,3tr) ·
`quanly.saigon` gọi plant HANOI trả **403**, gọi plant của mình trả **200**.

**Kiểm tra mojibake:** `items` / `bom_headers` / `sales_orders` đều **0 dòng** khớp `Ã|Â|á»|áº`;
tên tiếng Việt hiện đúng dấu trong DB. Script còn tự kiểm round-trip UTF-8 ngay sau khi tạo công ty
đầu tiên và **dừng lại** nếu shell mã hoá sai — đóng đúng lỗi FE từng mắc (`§0.44` hệ quả #8).

**Trạng thái KHÔNG seed được, đã kiểm và ghi rõ trong guide** (không phải thiếu sót):
`PlanningDemandStatus.CONSUMED` (không dòng code nào set), `MrpRunStatus.FAILED`,
`ProductionReceiptStatus.CANCELLED` (enum khai báo nhưng không endpoint nào dẫn tới),
`LotStatus.EXPIRED`, nhập mua hàng theo số sê-ri.

---

### 0.46 Bộ Dữ Liệu Demo SẠCH Cho Capstone 2 — Namespace `D26-` (2026-08-22)

**Không** phase, **không** migration, **không** permission mới, **không sửa một dòng `src/main`
nào**. Yêu cầu của user: xoá hết dữ liệu hiện tại và seed lại theo
`BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx` để demo trước hội đồng, **đối tượng xem dữ liệu là
khách hàng**. Hướng dẫn bàn giao: `docs/demo-capstone2-guide.md`.

**Vì sao KHÔNG tái dùng seeder `VIETBIKE` (`§0.45`).** Hai bộ dữ liệu phục vụ hai mục đích ngược
nhau. `VIETBIKE` cố ý phủ **mọi** trạng thái, kể cả trạng thái xấu — lô `HOLD`/`REJECTED`, vật tư
và kho `INACTIVE`, đề xuất `BLOCKED` vì thiếu BOM/quy trình, lệnh sản xuất kẹt ở `BLOCKED`, phiếu
nhập bị từ chối. Đó là thứ cần cho kiểm thử và **đúng thứ không được** cho một buổi trình diễn
trước khách hàng. Bộ `D26-` là mặt còn lại: một luồng duy nhất, sạch, không bản ghi lỗi nào.

| Thành phần | Nội dung |
|---|---|
| `scripts/demo-capstone2/catalogue.sh` | **Toàn bộ** mã, tên, số lượng. Không có lời gọi HTTP nào |
| `scripts/demo-capstone2/seed.sh` | Seed + preflight chỉ đọc (PF-01…PF-08, PF-15), dừng ở tồn kho đầu kỳ — KHÔNG tạo Sales Order |
| `scripts/demo-capstone2/verify.sh` | Nghiệm thu **ghi dữ liệu** (PF-10, PF-12…PF-14 + smoke test §12) trên một Sales Order do người dùng tự tạo và CONFIRM trước — đòi `ALLOW_DESTRUCTIVE=1` **và** `SALES_ORDER_NO` |
| `scripts/demo-capstone2/reset-db.sh` | `docker compose down -v` + `up -d` + đợi Postgres |
| `docs/demo-capstone2-guide.md` | Tài khoản, kịch bản 9 bước, cách dựng lại, các chỗ lệch tài liệu |

Bộ dữ liệu (đếm thật trong DB sau lượt bàn giao): 1 công ty · 1 nhà máy · 5 kho · 10 vật tư ·
10 cấu hình item-warehouse · 3 BOM `ACTIVE` / 9 dòng · 3 quy trình `ACTIVE` / 5 công đoạn ·
4 tổ sản xuất · 1 ca · 1 lịch · 9 phiếu nhập tồn đầu kỳ · 1 đơn hàng CONFIRMED · 1 dòng nhu cầu
OPEN · **0** lượt chạy MRP · **0** lệnh sản xuất · 3 tài khoản. Không dòng nào dính mojibake.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Bố cục URL của API là HỖN HỢP, và đoán sai trả 401 chứ không phải 404.** Phần lớn
   controller nằm ở `/api/v1/...`, nhưng `auth`/`users`/`access`/`sales-orders` đặt `/v1` **sau**
   tiền tố tài nguyên (`/api/auth/v1/login`, `/api/sales-orders/v1`, `/api/access/v1/roles`) —
   `server.servlet.context-path` là `/api`, `@RequestMapping` cấp class là `/auth`, `@PostMapping`
   là `/v1/login`. Một đường dẫn sai không khớp handler nào ⇒ Spring Security trả **401
   `AUTHENTICATION_REQUIRED`**, tức **trông hệt như sai mật khẩu**, không phải 404. `seed.sh` vì
   thế đối chiếu 25 đường dẫn bắt buộc với **OpenAPI runtime** và dừng ngay nếu lệch (đúng câu cuối
   §15 của tài liệu). 🔴 **Seeder `VIETBIKE` (`scripts/demo/`) vẫn dùng `BASE_URL=.../api/v1` và
   nay đã lệch hợp đồng — nó chưa được chạy lại sau khi bố cục URL đổi.**
2. 🔴 **Phạm vi `PLANT` KHÔNG nhận tài nguyên `WAREHOUSE` nữa.**
   `AccessControlService.isResourceTypeAllowed` (siết ở `V63`) ép `COMPANY→COMPANY`,
   `PLANT→PLANT`, `WAREHOUSE_GROUP→WAREHOUSE`. Bản nháp đầu của phase này liệt kê cả 5 kho vào
   phạm vi `PLANT` — đúng hình dạng seeder `VIETBIKE` đang dùng — và chết với
   `OPERATION_NOT_ALLOWED`. Và **không cần** liệt kê: `PermissionGuard.hasWarehouseAccess` kiểm
   quyền trực tiếp trên kho **trước**, rồi lùi về nhà máy và công ty của kho đó, nên một tài nguyên
   `PLANT` phủ trọn cả 5 kho. Đã kiểm thật: `congnhan.demo` đọc được tồn kho `D26-FG`.
3. 🔴 **Tài khoản `admin` mặc định đã bị `V61` vô hiệu hoá — phải cấp qua bootstrap, và bước đó
   KHÔNG gộp được với lần khởi động thường.** `AdminBootstrapService` ném `IllegalStateException`
   nếu cờ bật trong khi tài khoản quản trị **đã** `ACTIVE`, nên để `APP_BOOTSTRAP_ADMIN_ENABLED=true`
   trong `.env` sẽ làm backend **không khởi động lại được** ở lần sau. Quy trình đúng: khởi động
   một lần với `--app.bootstrap.admin.enabled=true` trên dòng lệnh, dừng, rồi khởi động lại bình
   thường. `.env` giữ `false`. Mật khẩu bootstrap bắt buộc ≥ 15 ký tự và khác `Admin@123`.
4. **`ItemWarehouseSetting` phải có ĐÚNG MỘT dòng cho mỗi vật tư trong một nhà máy.**
   `MrpWarehouseResolutionService.resolve` trả `AMBIGUOUS_WAREHOUSE_POLICY` khi `defaults.size() > 1`
   **hoặc** `settings.size() > 1` — tức dòng thứ hai làm hỏng hoạch định kể cả khi chỉ một dòng là
   default. `PF-03` canh đúng bất biến này bằng cách lấy `max` số cấu hình trên mỗi vật tư.
5. **Không dòng nhu cầu nào rơi vào `SYSTEM_FALLBACK_USED`** vì cấp 0 lấy kho từ
   `demandWarehouseId` của lượt chạy, còn các cấp dưới do DEC-03 phân giải theo `defaultSupply` của
   **từng** vật tư. Đó là lý do bảng §5 phải đủ 10 dòng — thiếu một dòng là một đề xuất `WARNING`.
6. **Bán thành phẩm nạp sẵn 12 cái là thứ giữ cho `blockedProposals = 0`.** Nhu cầu cấp 1 đều
   `COVERED` (net = 0) nên `expandChildren` không nổ xuống cấp 2, không sinh đề xuất mua nào, và
   `plannedPurchaseRecommendations` bằng 0 đúng như §9.1.
7. **`verify.sh` tách khỏi `seed.sh` là quyết định, không phải tiện tay.** §9 của tài liệu cấm seed
   sẵn Planning Run ("để người trình bày chạy Planning trực tiếp"), trong khi §11/§14 lại đòi bằng
   chứng `blockedProposals = 0`. Hai yêu cầu chỉ dung hoà được bằng **hai lượt**: nghiệm thu trên
   một cơ sở dữ liệu dùng-một-lần, rồi xoá sạch và seed lại để bàn giao.
8. **Tài khoản gán phạm vi `PLANT` vẫn bị 403 ở `GET /v1/companies`** — giới hạn có sẵn của repo
   (`hasPermission` chỉ đọc assignment `GLOBAL`, xem `module/uom/CLAUDE.md`), **không** phải lỗi bộ
   dữ liệu. Đã ghi vào hướng dẫn để người trình bày không gặp nó trên sân khấu.

**Nghiệm thu:** chạy `seed.sh` rồi `verify.sh` trên một cơ sở dữ liệu dựng lại từ trắng —
**PF-01 → PF-15 và toàn bộ 9 bước smoke test §12 đều PASS**: nhu cầu cấp 0 `gross 10 / net 10`,
4 dòng cấp 1 `COVERED`, đúng 1 đề xuất `MAKE`/`READY` với thông điệp duy nhất `MATERIAL_SHORTAGE`,
`reservedPercent = 100`, tổng lượng xuất 50, lô `D26-BIKE16-B001` ở `HOLD` báo tồn thực tế 10 mà
khả dụng 0, sau QC thành `AVAILABLE` và tồn khả dụng `D26-FG` tăng đúng 10, đơn hàng
`SO-DEMO26-001` → `FULFILLED` 10/10. Sau đó xoá sạch và seed lại lượt bàn giao.

**Breaking changes: KHÔNG có** — phase này không đụng `src/main`, không migration, không đổi wire.

**[Cùng ngày, sau lượt bàn giao đầu tiên] Bỏ Sales Order khỏi seed.sh, theo yêu cầu của user.**
Người dùng muốn tự tạo Sales Order của riêng mình và seed lại phần còn lại của bộ dữ liệu (tổ
chức, vật tư, BOM/routing, tồn kho) **nhiều lần** mà không dính một đơn hàng cố định lặp đi lặp
lại. `seed.sh` nay dừng lại đúng ở cuối tồn kho đầu kỳ; PF-09/PF-11 (vốn kiểm Sales Order) bị bỏ
khỏi preflight của nó. `verify.sh` — vẫn cần một Sales Order CONFIRMED để chạy Planning — đổi từ
đọc `salesOrder.id`/`salesOrder.lineId` trong manifest sang **tra cứu qua API** theo biến môi
trường bắt buộc `SALES_ORDER_NO` (dùng `GET /sales-orders/v1?search=...`, xác nhận `CONFIRMED`
trước khi dùng). `workOrderNo` sinh ra trong `verify.sh` cũng đổi từ hằng số sang
`WO-VERIFY-<giờ:phút:giây>` để không đụng độ giữa các lượt chạy trên các đơn hàng khác nhau.
Đã nghiệm thu lại toàn bộ: `seed.sh` cho PF-01→PF-08/PF-15 PASS mà không tạo SO nào; tạo tay
`SO-TEST-001`, CONFIRM, rồi `SALES_ORDER_NO=SO-TEST-001 verify.sh` cho PF-10→PF-14 + smoke test
9 bước đều PASS; sau đó xoá sạch và seed lại lượt bàn giao thật (0 sales order, 0 MRP run,
0 work order, 3 tài khoản). `docs/demo-capstone2-guide.md` đã cập nhật theo (bảng dữ liệu, kịch
bản 9 bước bắt đầu bằng bước "bạn tự tạo Sales Order", §4/§5 hướng dẫn dùng `SALES_ORDER_NO`).

---

**[2026-08-23] Mở rộng bộ dữ liệu và cho seed đi HẾT luồng, theo yêu cầu của user** (*"seed lại
bộ dataset đầy đủ, nhiều 1 tí để chụp hình giao diện làm slide"*). Bộ `D26-` nay có **40 vật tư /
3 dòng sản phẩm / 9 BOM / 9 routing / 8 tổ sản xuất / 2 ca / 2 lịch**, cộng **nhà cung cấp, giá
thành chuẩn, chứng từ mua hàng, đơn bán hàng, 3 lượt chạy MRP và 8 lệnh sản xuất phủ 6 trạng
thái** — mục đích là mọi màn hình đều có dữ liệu để chụp hình. Tên hiển thị chuyển sang **tiếng
Anh** (thay đổi user đã tự làm dở trong `catalogue.sh`, nay hoàn tất). Vẫn giữ tinh thần "sạch":
không `BLOCKED`, không lô `REJECTED`, không bản ghi `INACTIVE`, không phiếu bị từ chối; và vẫn để
lại **32 đề xuất `DRAFT` + 2 đơn hàng `CONFIRMED` chưa hoạch định** cho người trình bày diễn
trực tiếp. `verify.sh` không còn bắt buộc — `seed.sh` tự chạy **42 mục preflight** ở cuối (số kỳ
vọng **suy ra từ `catalogue.sh`**, không viết cứng) và từ chối bàn giao nếu có mục nào trượt.
**Không sửa một dòng `src/main` nào**, không migration.

🔴 **Ba cái bẫy đã trả giá để biết, ghi lại cho lần sau:**
1. **Phải tắt `app.rate-limit.enabled` khi seed.** Cờ này ghi cứng `true` trong `application.yml`
   (không đọc env), ngưỡng 500 request/phút/người dùng, còn bộ dữ liệu này bắn hơn 600 request —
   seed chết giữa chừng ở `RATE_LIMIT_EXCEEDED`. Ghi đè tạm bằng `-Dapp.rate-limit.enabled=false`
   trên dòng lệnh; **đừng** sửa `application.yml`.
2. **Số lượng của lệnh sản xuất sinh từ MRP là số RÒNG, không phải số trên đơn hàng.** Netting đã
   trừ tồn kho thành phẩm sẵn có, nên báo sản lượng theo số đặt hàng trả **409
   `PLANNED_QUANTITY_EXCEEDED`** (`B54`). Script nay đọc `plannedQuantity` từ chính lệnh sản xuất
   thay vì dùng hằng số.
3. **`git bash` và `python` của Windows KHÔNG cùng hiểu `/tmp`.** Git Bash ánh xạ `/tmp` sang
   `%TEMP%`, còn python thấy `C:	mp` — ghi file bằng `>` rồi đọc bằng python theo cùng đường dẫn
   sẽ `FileNotFoundError`. Truyền dữ liệu qua **stdin** (đúng cách `extract` của `lib/common.sh`
   đang làm), đừng qua file tạm đường dẫn POSIX.

Bản ghi số liệu và hướng dẫn chụp màn hình: `docs/demo-capstone2-guide.md`.

**[2026-08-25] Một hệ thống, HAI công ty + lấp mọi trường thời gian** (yêu cầu của user). `seed.sh`
nay nhận **`PROFILE`**: `sample` dựng công ty `D26-OMNIPLANT` *(Sample Data)* đi hết luồng để chụp
hình, `demo` dựng công ty `DEMO-OMNIPLANT` *(Live Demo)* dừng ở tồn kho đầu kỳ để diễn trực tiếp.
Hai lượt chạy trên **cùng một** cơ sở dữ liệu; catalogue giữ nguyên namespace `D26-` còn seed.sh
đổi tiền tố tại chỗ, tài khoản tách theo đuôi `.sample` / `.demo`. Nghiệm thu: **42/42** mục
preflight (sample) và **20/20** (demo), đo thật. **Không sửa `src/main`**, không migration.

🔴 **Không gộp được file backup vào hệ thống theo nghĩa đen** — `backups/*.dump` là `pg_dump`
**toàn cơ sở dữ liệu** và dùng đúng cùng dãy mã `D26-`, nên nạp chồng sẽ đụng `uk` của
`companies.code` / `items.code` và hàng chục bảng khác. Công ty thứ hai vì thế được **dựng lại bằng
chính script** với namespace riêng; các dump cũ vẫn nạp lại nguyên trạng được bằng `restore-db.sh`.

**Lấp trường ngày/giờ** (trước đó FE thấy toàn dấu "—"): phân quyền có `expiresAt` (+365 ngày),
lệnh sản xuất có `plannedEndAt` (+5 ngày kể từ `plannedStartAt`), phiếu ghi sản lượng gắn vào
**công đoạn cuối** của ảnh chụp quy trình (nhờ đó có công đoạn/thứ tự/tổ sản xuất). Riêng
`InventoryLot.expiresAt` **không có đường ghi qua REST API** (không request DTO nào mang field đó)
⇒ tách hẳn ra `scripts/demo-capstone2/set-lot-expiry.sh` — script **duy nhất** của bộ này chạy SQL,
để `seed.sh` giữ nguyên bất biến "không một câu SQL nào". Danh sách trường **cố ý vẫn trống** (và
lý do từng cái) ở `docs/demo-capstone2-guide.md §9`.

🔴 **Ba cái bẫy nữa, trả giá lần này:**
1. **`GET /v1/inventory/item-warehouse-settings` KHÔNG có tham số `plantId`** — truyền vào thì bị
   bỏ qua **im lặng** và endpoint trả cấu hình của **mọi** công ty. Preflight PF-03 vì thế "đúng"
   suốt khi chỉ có một công ty, và báo gấp đôi ngay khi có công ty thứ hai. Nay cộng theo **từng
   kho**. Bài học: một phép kiểm chỉ chạy trên một tập dữ liệu duy nhất chưa chứng minh nó lọc đúng.
2. **Backend là single-session** (`AuthService.login` thu hồi mọi phiên cũ của cùng tài khoản) ⇒
   hai tiến trình seed cùng đăng nhập `admin` thì tiến trình chạy trước chết giữa chừng với
   `TOKEN_REVOKED`. Thấy mã đó gần như chắc chắn là còn một lượt seed cũ đang chạy nền.
3. **Biên dịch tăng dần không sửa được `target/classes` bị cắt dở.** Giết Maven giữa lúc biên dịch
   để lại class hỏng (lần này: `UserMapperImpl` mất mệnh đề `implements`, Spring báo *No qualifying
   bean of type UserMapper*), và `mvn compile` lần sau tưởng không có gì thay đổi nên không sửa.
   Phải `mvn -o -DskipTests clean compile`.

---

### 0.47 Track `EH-*` – Refactor Hạ Tầng Xử Lý Lỗi (2026-08-23)

**Không migration, không permission mới, không endpoint mới.** Nguồn: `ExceptionHandlerRefactorPlan.md`
(đánh giá 2026-08-22, khảo sát ~380 throw site + 48 catch block). **Không** phải phase nghiệp vụ
`P*`/`F*`/`D*`/`C2-*` — đây là nợ kỹ thuật xuyên module về hạ tầng lỗi. 6/8 hạng mục xong; `EH-7`/`EH-8`
hoãn có chủ đích (lý do ở §4 của plan).

| ID | Nội dung | Đụng `src/main` |
|---|---|---|
| `EH-1` | Lưới an toàn cho lỗi thoát khỏi filter chain + `ApiErrorController` + `/error` `permitAll` | ✅ |
| `EH-2` | Tách `OPERATION_NOT_ALLOWED` thành 3 mã cụ thể trên 59/96 site | ✅ |
| `EH-3` | `DataIntegrityErrorMapper` thay chuỗi `contains()` hardcode | ✅ |
| `EH-4` | `MrpRunService.fail`: sanitize message + `MrpRunStateRecorder` (`REQUIRES_NEW`) | ✅ |
| `EH-5` | Xoá 4 mã lỗi chưa từng ném, giữ 5 kèm javadoc nêu lý do | ✅ |
| `EH-6` | Viết lại `error-handling.md` §5.2/§5.3 + thêm §5.4.1/§5.4.2 | ❌ (tài liệu) |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Đừng bao giờ để một exception thoát khỏi một `Filter`.** `@RestControllerAdvice` **chỉ** phủ
   những gì phát sinh trong `DispatcherServlet`; filter chạy trước nó. Trước `EH-1`, Redis không kết
   nối được trong `assertNotBlacklisted` (hoặc bất kỳ `RuntimeException` lạ nào trong 3 filter bảo mật)
   bay thẳng ra ngoài chain và client nhận trang `/error` mặc định của Boot —
   `{timestamp,status,error,path}`, hình dạng JSON **không client nào trong hệ thống này parse được**.
   Mỗi filter nay tự bắt và trả envelope; `ApiErrorController` là lưới cuối cùng.
2. 🔴 **`/error` phải `permitAll`, và đó không phải chi tiết thừa.** Spring Boot đăng ký security chain
   cho **cả** dispatcher type `ERROR`, nên thiếu dòng đó thì một request chưa xác thực mà lỗi thật là
   500 sẽ bị trả lời lại thành **401** và **mất status thật**. `ApiErrorController` không tiết lộ gì về
   sự cố (không echo message, không stack trace) nên mở nó không tốn gì.
3. 🔴 **`EH-2` KHÔNG "thay thế" `OPERATION_NOT_ALLOWED`** — nó **thu hẹp** mã đó về đúng vai catch-all.
   **37/96 site cố ý giữ nguyên**: sai `ItemType`, lot/serial tracking không khớp, `dueDate <
   orderDate`, break ngoài ca, nhiều supplier trong 1 PR… Đừng "đồng bộ nốt" — nhóm đó không có trục
   chung nào để FE rẽ nhánh, tách tiếp chỉ tạo ra mã không ai phân biệt nổi (`§11.5` theo chiều ngược).
   Bảng phân loại đầy đủ 96 site: `ExceptionHandlerRefactorPlan.md §8`.
4. 🔴 **Bài học đo được của lượt này: đổi 59 throw site mà chỉ 23 assertion đỏ.** Hơn một nửa số chỗ
   được sửa **không có test nào pin `code`** — chúng chỉ được phủ qua `isInstanceOf(AppException.class)`
   (đúng lỗ hổng rule `R1` sinh ra để chặn) hoặc qua mock stub **tự nhất quán** ở tầng controller (stub
   ném mã cũ, rồi assert đúng mã cũ ⇒ xanh vĩnh viễn dù service thật đã đổi). Khi đổi mã lỗi hàng loạt,
   **phải đọc *test nào đỏ*, không chỉ chờ build xanh** — và rà cả controller test có stub.
5. 🔴 **Mutation #5 lộ ra lỗ hổng lớn nhất, và nó nằm ở đúng chỗ ít ngờ nhất.**
   `OrganizationLookupService.getActive{Company,Plant,Warehouse}` là **bộ validate được tái dùng nhiều
   nhất repo** — mọi module chạm master data đều đi qua — nhưng `OrganizationLookupServiceTest` chỉ phủ
   `resolveScope`, **không case nào pin `code` của ba nhánh `INACTIVE`**. Các test *trông như* phủ chúng
   (`OrganizationServiceTest.createPlant_underInactiveCompany_fails`…) thực ra kiểm nhánh **riêng** bên
   trong `OrganizationService`, không đi qua lookup service. Đổi mã ở đó ⇒ **toàn bộ suite vẫn xanh**.
   Đã bổ sung 4 case. **Bài học:** class càng được tái dùng rộng thì càng dễ bị tưởng là "chắc đã có ai
   test rồi".
6. **`EH-3`: tên constraint KHÔNG bao giờ ra response** — chỉ ghi log, cùng lý do catch-all không echo
   message exception. Thêm entry vào `KNOWN` khi một constraint **thật sự** từng làm ai đó mất công chẩn
   đoán, không phải vì nó tồn tại: schema có ~170 constraint có tên, mỗi cái một mã thì client không
   phân biệt nổi.
7. 🔴 **`EH-4`: `saveAndFlush` KHÔNG đủ để một hàng "sống sót" transaction đang hỏng.** Hàng đã flush
   nhưng **chưa commit** là **vô hình** với mọi transaction khác, kể cả `REQUIRES_NEW` — nên bản nháp
   "chỉ thêm recorder" sẽ không tìm thấy hàng nào để đánh dấu `FAILED`. Phải commit hàng `mrp_runs`
   **trước** khi tính toán (`MrpRunStateRecorder.start`), rồi mới ghi nhận thất bại ở transaction thứ
   hai. Hệ quả phụ **có lợi và có chủ đích**: lời hứa "một `Idempotency-Key` trùng sẽ fail fast trên
   `uk_mrp_runs_idempotency_key`" (javadoc `MrpRunService.run` viết từ `§0.44`) nay **mới thành sự
   thật** — trước đó request thứ hai **chờ** trên index cho tới khi request đầu chạy xong cả lượt MRP.
   Đánh đổi đã chấp nhận: nếu transaction chính chết **sau** khi tính xong, run nằm lại ở `RUNNING`
   thay vì biến mất — một trạng thái thấy được và chẩn đoán được.
8. **`EH-5` giữ lại 5 mã chưa từng ném, mỗi cái có javadoc nêu lý do** — `ITEM_ALREADY_ISSUED` /
   `PRODUCTION_ORDER_CLOSED` (đã hứa với FE trong `docs/api-guide-for-frontend.md`),
   `LOT_WAREHOUSE_CONFLICT` / `RECEIPT_STATE_CONFLICT` / `BOM_REQUIREMENT_EXCEEDED` (hợp đồng lỗi của
   phase BE-2/BE-4, `BE_SYSTEM_ISSUES_RESOLUTION_PLAN_2026-08-19.md`). **Đừng xoá chúng vì "grep ra 0
   kết quả"** — đó chính là câu hỏi `EH-5` đã trả lời rồi.
9. **`EH-6`: cây exception 12 lớp trong `error-handling.md §5.2` CHƯA BAO GIỜ tồn tại.** Code thật chỉ
   có `AppException` + `MultiErrorException` + interface `ErrorCode` với 3 enum triển khai — **đơn giản
   hơn** bản vẽ, không phức tạp hơn. Ai đọc tài liệu trước code sẽ đi tìm nhầm class và tệ hơn là tưởng
   phải *thêm một lớp exception* cho mỗi loại lỗi mới (thực tế: thêm **một hằng** vào enum).

**Hai phát hiện phụ, cố ý KHÔNG sửa trong lượt này** (ghi lại để không mất, chi tiết ở plan §8.1):

1. 🔴 **`ImportRunService.invalidState` trả 422 nhưng đọc *status chứng từ*** (3 call site). Theo đúng
   ba tiêu chí `error-handling.md §5.3` thì phải là `STATE_CONFLICT` (**409**) — **cùng loại nợ `#26`**
   mà `D11` đã đóng cho `planning`; `dataimport` ra đời sau nên chưa từng được rà. Ngoài phạm vi `EH-2`
   (EH-2 tách mã **trong cùng** status 422; đây là đổi status, một quyết định riêng).
2. **`WorkOrderService.ensureDoesNotExceed` (`:543`) là dead code** — private, 0 call site. Chỉ ghi
   nhận, không xoá (dead code ngoài phạm vi task).

**Nghiệm thu mutation (5, **5/5 đụng `src/main`**, đã revert):** bảng đầy đủ ở
`ExceptionHandlerRefactorPlan.md §9.5`. Đáng chú ý: #2 giữ **nguyên HTTP status** ở cả hai phía (409 vs
409) nên chỉ assert bám `$.code` mới bắt được; #4 làm đỏ **2/2 case IT** trong khi **1177 case unit vẫn
xanh** — minh hoạ trực tiếp `R7`; #5 lần chạy đầu **0 case đỏ**, xem hệ quả #5 ở trên.

**Breaking changes — wire: CÓ.** `code` đổi ở 59 throw site (`EH-2`) và ở ràng buộc CSDL không phải
trùng khoá (`EH-3`, 409 → 422); **HTTP status không đổi ở `EH-2`**. Bảng đầy đủ từng nhóm:
`ExceptionHandlerRefactorPlan.md §9.1`. FE đã được cập nhật trong `docs/api-guide-for-frontend.md`
(user tự báo FE — quyết định của user qua `AskUserQuestion`). **Java positional:** `MrpRunService`
constructor **+1** tham số (`MrpRunStateRecorder`). Test cũ **sửa** theo `R10`, không xoá.

---


### 0.48 Contract Giá Trị Của Audit Log — `changes[].oldValue`/`newValue` Về `string` (2026-08-25)

**Không** phase, **không** migration, **không** permission mới, **không** endpoint mới. Nguồn:
`BACKEND_AUDIT_LOG_VALUE_CONTRACT_2026-08-25.md` (FE báo: màn hình chi tiết Audit **sập** vì một
object lọt vào cây render React). Bugfix thứ tư phát hiện từ báo cáo thật thay vì đối chiếu spec, sau
`§0.24`, `§0.38`, `§0.40`. Bất biến **`B118`** (`common/audit/CLAUDE.md`).

| | Trước (2026-08-17 → 2026-08-25) | Sau |
|---|---|---|
| Kiểu Java của DTO | `JsonNode` | **`String`** |
| Kiểu runtime **trong cùng một** audit `WORK_ORDER_CREATED` | `['int', 'list', 'str']` | **`['str']`** (+ `null`) |
| Schema OpenAPI runtime | `$ref: JsonNode` ⇒ `{"type":"object"}` | **`{"type":"string"}`** |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Đây là hồi quy do chính commit `caps2 done` (`e124885`) gây ra, và không dòng tài liệu nào ghi
   nhận.** Lượt đó bật field-level diff thật (`AuditChangeCaptureService`) **và** đổi
   `AuditLogChangeResponse` từ `String` sang `JsonNode` trong cùng một commit khổng lồ (~50 file, kèm
   `.docx`/`.pdf`). Tài liệu FE thậm chí ghi lại **hệ quả** như thể đó là tính năng —
   *"`oldValue`/`newValue` giữ đúng kiểu JSON"* — nên không ai đọc ra rằng contract vừa bị phá. Cột
   lưu là `jsonb`: đo trên DB demo có **2758** dòng thì `string` 2266 · `boolean` 222 · `number` 162 ·
   `array` 108 ⇒ **cùng một field, năm kiểu runtime**, thứ mà không client sinh mã nào type nổi.
2. 🔴 **Sửa ở tầng đọc, KHÔNG đụng đường ghi.** `AuditFieldChange`, `AuditChangeCaptureService`,
   `AuditLogListener` và cột `jsonb` giữ **nguyên** — chúng lưu đúng thứ cần lưu (JSON có cấu trúc,
   truy vấn được bằng toán tử `jsonb` của Postgres). Chỉ `AuditLogQueryService.textValue` chuẩn hoá
   lúc map ra wire. Ép đường ghi thành text là mất khả năng query cấu trúc mà không đổi gì cho FE.
3. 🔴 **Không được trả thẳng chuỗi thô trong cột ra ngoài** — nghe như "đơn giản nhất" nhưng sai:
   một giá trị chữ được lưu **kèm dấu nháy JSON** (`"DRAFT"`), nên trả thô sẽ hiện `"DRAFT"` **có
   nháy** trên UI cho **2266/2758** dòng. Phải parse rồi mới rẽ nhánh: `isValueNode() → asText()`,
   còn lại `toString()`.
4. **Compact, không pretty.** Tài liệu FE gợi ý chuỗi có xuống dòng; backend trả **byte-for-byte**
   thứ đã lưu (compact) — một `JSON.parse` là lấy lại cấu trúc, còn muốn đẹp thì `JSON.stringify(…, null, 2)`
   ở phía hiển thị. Định dạng để đọc là việc của tầng hiển thị, không phải của wire.
5. **`JSON null` và SQL `NULL` gộp về `null`** — cả hai đều nghĩa "không có giá trị"; để `json null`
   thành chuỗi `"null"` là dựng một giá trị giả mà FE sẽ hiển thị nguyên văn.
6. 🔴 **Kiểu `String` trong record là bảo đảm mạnh hơn mọi test ở đây.** Sau khi đổi kiểu, một
   `JsonNode` **không compile được** vào DTO này — hồi quy cũ không thể tái diễn mà không ai nhận ra.
   Test chỉ phải canh phần *nội dung* của chuẩn hoá.
7. **`isString()` chứ không phải `value(...)` ở tầng HTTP.** `jsonPath("...").value("X")` **cũng xanh**
   khi giá trị là object node có nội dung tương ứng — đúng loại assertion đã để lọt lỗi này. Case mới
   ở `AuditLogControllerTest` assert `isString()` **trước**, rồi mới assert nội dung.

**Nghiệm thu mutation (3, đã revert — **3/3 đụng `src/main`**):**

| # | Mutation | Case đỏ | Chứng minh |
|---|---|---|---|
| 1 | Luôn `node.toString()` (không bỏ nháy cho scalar) | **3** — kể cả 2 case đã có từ `C2-1` | Bắt đúng bẫy hệ quả #3: contract vẫn "là `String`" nhưng nội dung sai cho phần lớn dòng |
| 2 | Luôn `node.asText()` (container bị làm phẳng thành `""`) | **1** — `get_everyStoredJsonShape_isRenderedAsStringOrNull` | Object/array **không** được âm thầm biến mất; đây là dòng dữ liệu FE cần nhất |
| 3 | Bỏ nhánh `node.isNull()` | **1** — cùng case trên | `json null` không được thành chuỗi `"null"` |

**Nghiệm thu:** `mvn -o clean verify` — **1184 case unit + 142 case IT / 21 class IT, failures = 0,
errors = 0** (baseline trước: 1181 unit + 142 IT / 21 class).

**Smoke test HTTP thật** (`mvn -o spring-boot:run -Dspring-boot.run.profiles=dev`, Postgres + Redis
qua `docker compose`, **không** phải Testcontainer) — chạy **A/B**, cùng một bản ghi, hai build:

| Probe | Trước sửa | Sau sửa |
|---|---|---|
| `GET /v1/audit-logs/9db449ce-…` (`WORK_ORDER_CREATED`, `D26-WO-1004`) | `componentLines` là **list**, `plannedQuantity` là **int** | cả hai là **string**; `componentLines` = `[{"uom":"EA","lineNo":1,…}]` |
| Tập kiểu của **mọi** `newValue` trên audit đó | `['int', 'list', 'str']` | **`['str']`** |
| `ITEM_CREATED` (`lotTracked`/`serialTracked`) | boolean | `"true"` / `"false"` |
| `BOM_ACTIVATED` (`UPDATE`, có `oldValue`) | — | `old='DRAFT'` → `new='ACTIVE'`, **không** kèm dấu nháy |
| `USER_CREATED` (`roles`) | array | `'["OPERATOR"]'` |
| `GET /v3/api-docs` → `AuditLogChangeResponse` | `$ref: JsonNode` ⇒ `{"type":"object"}` | **`{"type":"string"}`** |

> ⚠️ **Bẫy đo lường gặp lại lần thứ hai (sau `§0.42a`):** `pkill -f "spring-boot:run"` **không** giết
> tiến trình con `java`, nên lượt A/B đầu tiên suýt đo hai lần trên **cùng một** build. Chỉ lộ ra vì
> lần khởi động sau báo `Port 8081 was already in use`. Phải `netstat -ano | grep :<port>` rồi
> `taskkill //PID <pid> //F`. Và trap `/tmp` của `§0.46` cũng tái diễn: `curl -o /tmp/x.json` rồi
> `python` đọc `/tmp/x.json` ⇒ `FileNotFoundError` (Git Bash ánh xạ `/tmp` sang `%TEMP%`, python thấy
> đường dẫn Windows khác hẳn) — truyền qua **stdin**.

**Breaking changes — wire: CÓ, hẹp.** `changes[].oldValue`/`newValue` đổi từ JSON đa kiểu về `string`
| `null`. Nhưng đây là **khôi phục** contract mà OpenAPI vẫn công bố suốt thời gian đó (`type: string`,
đúng hình dạng `C2-1`), nên client viết theo tài liệu **hết hỏng**, không phải bắt đầu hỏng; FE cũng
đã tự chuẩn hoá phòng vệ nên bản sửa này biến lớp đó thành no-op. **Java positional: không có** —
không đổi constructor nào.

---


### 0.49 Track `AR-*` – Refactor Hạ Tầng Audit (2026-09-02)

Nguồn: `AuditRefactorPlan.md` (AR-0 → AR-10). **Không** phải phase nghiệp vụ — đây là nợ kỹ thuật hạ
tầng, cùng loại với track `EH-*` (`§0.47`). Migration **`V67`** + **`V68`**, **không** permission mới,
**không** breaking change wire. Bất biến mới **`B119`–`B123`** (`common/audit/CLAUDE.md`).

**Hai quyết định user chốt trước khi viết code** (`AskUserQuestion`): phạm vi **toàn bộ AR-0 → AR-10**;
failure policy **`FAIL_OPEN` toàn hệ thống** (§11.1) — audit hỏng không bao giờ đổi kết quả nghiệp vụ.

#### Ba defect gốc — cả ba đều im lặng

| # | Defect | Hệ quả |
|---|---|---|
| 1 | `@TransactionalEventListener(AFTER_COMMIT)` **chỉ** giao event khi thread publish đang trong transaction, mà **`AuthService` không có `@Transactional` ở bất kỳ đâu** | **Mọi** event auth bị vứt bỏ hoàn toàn. Nửa quan trọng nhất về bảo mật của audit trail **chưa bao giờ tồn tại** |
| 2 | Khoảng giữa business `COMMIT` và `INSERT` của listener không có bảo đảm nào; event `FAILURE` trong transaction rollback không bao giờ được ghi | Thay đổi nghiệp vụ còn, bản ghi ai làm thì mất. Lệnh bị từ chối **không để lại dấu vết nào** |
| 3 | `X-Trace-Id` (filter nhận 64 ký tự) → cột `VARCHAR(32)`; `User-Agent` client tự đặt → `VARCHAR(512)` | **Client tự tắt được audit trail của chính mình bằng một header** |

🔴 **Không log, không lỗi, không test nào đỏ.** 1184 case unit và 142 case IT đều xanh suốt thời gian
cả ba defect còn sống — đúng bài học `R7` lần thứ bảy trong repo (sau `§0.24`, `§0.27`, `§0.28`,
`§0.40`, `§0.43`, `§0.45`).

#### Hệ quả cần nhớ khi code tiếp

1. 🔴 **Outbox row nằm TRONG transaction nghiệp vụ (`B119`), và đó là toàn bộ lý do nó tồn tại.**
   `AuditOutboxWriter` dùng `Propagation.REQUIRED`. Đổi thành `REQUIRES_NEW` là phá bất biến này
   **mà mọi test đơn vị vẫn xanh** — chỉ `AuditPipelineIT` bắt được.
2. 🔴 **Failure writer phải là BEAN RIÊNG (`B120`).** Spring áp `@Transactional` qua proxy, nên gọi
   từ `AuditOutboxWriter` sang method `REQUIRES_NEW` của **chính nó** sẽ bypass proxy ⇒ annotation bị
   bỏ qua im lặng ⇒ mọi failure audit lại biến mất khi rollback. **Bẫy này suýt tái diễn lần thứ hai
   ngay trong lượt làm việc này**: bản nháp `AuditOutboxDispatcher` gọi `claimBatch()`/`deliver()`
   trên chính nó — đã tách thành `AuditOutboxProcessor`. Cùng bẫy từng tạo lỗ hổng bảo mật thật ở
   `§0.19` hệ quả #3.
3. 🔴 **Thứ tự AOP ↔ transaction phải ghim tường minh.** Cả `AuditableAspect` lẫn transaction advisor
   mặc định là `Ordered.LOWEST_PRECEDENCE`, và **thứ tự giữa hai advisor bằng nhau là không xác định**
   ⇒ tính nguyên tử của outbox insert sẽ phụ thuộc thứ tự đăng ký bean. Nay: transaction
   `LOWEST_PRECEDENCE - 100` (`@EnableTransactionManagement` ở `AuditConfig`), aspect
   `LOWEST_PRECEDENCE - 50`. Khai `@EnableTransactionManagement` làm Boot **nhường** auto-config của
   nó — giá trị vẫn thấp hơn hẳn method-security nên `@PreAuthorize` vẫn chạy ngoài cả hai.
4. 🔴 **KHÔNG backfill cột mới.** Suy `plant_id` của một dòng lịch sử từ dữ liệu hôm nay là **làm giả
   snapshot**. `NULL` nghĩa là "không ai chụp lại lúc đó" — câu trả lời đúng duy nhất. Cùng lý do
   `V65` không backfill `entity_name`.
5. 🔴 **`occurred_at` và `created_at` là hai thứ khác nhau, mọi query lọc/sắp thời gian phải dùng
   `COALESCE(occurred_at, created_at)`.** Dòng trước `V67` không có `occurred_at`; lọc theo một mình
   cột mới làm **toàn bộ lịch sử biến mất** khỏi kết quả — read API sẽ trông như vừa mất nhiều năm
   trail đúng lúc deploy.
6. 🔴 **Heuristic cũ đoán `CREATE`/`UPDATE`/`DELETE` từ HẬU TỐ TÊN ENUM.** Sai cho mọi action không
   theo quy ước (`BOM_ACTIVATED`, `WORK_ORDER_RELEASED`, `PERMISSION_GRANTED`, `ROLE_REVOKED`). Một
   lệnh thu hồi bị ghi thành UPDATE không phải chuyện đặt tên — đó là khác biệt giữa "grant này bị gỡ"
   và "có gì đó về role thay đổi". `AuditOperation` nay khai tường minh; `INFERRED` giữ heuristic cũ
   cho call site chưa di trú.
7. 🔴 **`BomService.updateLine` ghi `entity_id` của BOM HEADER dưới `entityType = "BomLine"`** — id
   trỏ tới một dòng **có thật nhưng khác**, tệ hơn `null` vì nó chỉ người điều tra đi sai một cách tự
   tin. Đã sửa + `BomLineAuditProvider` cho diff thật (differ tự động so `BomHeader` scalar với
   `BomResponse` ⇒ mọi lệnh sửa dòng báo **"không có thay đổi nào"**, đọc như bằng chứng rằng không
   có gì xảy ra).
8. **Class implement CẢ HAI SPI bắt buộc override `order()`** — Java từ chối kế thừa hai default
   method cùng chữ ký (`BomLineAuditProvider`).
9. **Trigger `FOR EACH ROW` KHÔNG chặn `TRUNCATE`** — phần đó do lệnh `REVOKE` gánh (mẫu ghi trong
   `V68`). Đó cũng là lý do `AuditPipelineIT` dọn bảng bằng `TRUNCATE` được.
10. **Tài liệu KHÔNG được gọi đây là "tamper-proof".** `payload_hash` đã tính và lưu, nhưng hash nằm
    **cùng** database mà nó bảo vệ thì không chống được tampering ở mức DBA. Gọi đúng là
    **"append-only activity trail"** cho tới khi có anchor ngoài (§11.3 chưa chốt).
11. **`@DataJpaTest` bọc mỗi test trong transaction rollback** ⇒ test outbox phải
    `@Transactional(propagation = NOT_SUPPORTED)` + `TransactionTemplate` tường minh, nếu không mọi
    assertion đều vô nghĩa.

#### Còn mở, cố ý (không phải bỏ sót) — chi tiết `AuditRefactorPlan.md §13.2`

Tamper anchor ngoài DB · partition theo tháng (chưa có số đo volume) · bật retention ở production
(§11.2 cần người duyệt pháp lý) · export endpoint + masking IP/User-Agent (§11.7) · rà 131
`AuditAction` để deprecate action stale · xoá legacy path (giữ ít nhất một release ổn định theo §8.1).

**Nghiệm thu:** `mvn -o clean verify` — **1225 case unit / 158 class + 154 case IT / 22 class IT,
failures = 0, errors = 0**. `AuditPipelineIT` (class IT thứ 22) chứng minh trên Postgres thật: commit
sinh **đúng một** row; rollback **không** sinh `SUCCESS` nhưng **có** `FAILURE`; event auth **không có
transaction** vẫn được ghi; header dài bị cắt thay vì làm mất event; snapshot quá cỡ thành marker;
redelivery cùng `eventId` không trùng; lease hết hạn được thu hồi; dead letter **không bị xoá**;
UPDATE/DELETE bị DB từ chối; đường maintenance là lối duy nhất xoá được.

**Breaking changes — wire: KHÔNG có** (thuần additive: 11 field mới + 6 filter mới trên read API,
`entityId` được **khôi phục**, `status`/`createdAt` giữ nguyên). **Java positional: có** —
`AuditLogQueryService.list(...)` nhận `AuditLogSearchCriteria` thay 8 tham số rời;
`AuditLogResponse`/`AuditLogDetailResponse` đổi hình dạng record; `AuditableAspect` và
`AuditChangeCaptureService` đổi constructor. Test cũ **sửa** theo `R10`, không xoá case nào.

---

### 0.50 `G2` – Danh Mục Nền Cho FE Giai Đoạn 2 (2026-10-14)

Nguồn: `FE_HANDOFF_GD_02_2026-10-12.md` (P1). Kế hoạch + 7 quyết định D1–D7: `NEXT_PHASE_PLAN.md §9`.
Trả lời FE: `BE_RESPONSE_GD_02_2026-10-14.md` (theo `BETaskDeliveryRules.md`, **user tự gửi qua Zalo**).
Migration **`V69`** + **`V70`**. Bất biến **`B136`** (sales), **`B137`–`B139`** (inventory), **`B140`**
(organization), **`B141`** (purchasing).

> ⚠️ **File FE là bảng câu hỏi, không phải danh sách việc** (`BETaskDeliveryRules.md §5` lỗi #1). 16 câu
> §5 phần lớn trả lời bằng hành vi có sẵn; chỉ phần đánh **CODE** ở `NEXT_PHASE_PLAN.md §9.1` mới được viết.

| Gói | Nội dung |
|---|---|
| `G2-1` | Customer master (`module/sales`), 6 endpoint đúng hình dạng FE §3.4, `PERM_CUSTOMER_*` |
| `G2-2` | Supplier: `contactName`/`notes`, đường `/v1/companies/{C}/suppliers` + `activate` + `POST deactivate` **song song** đường cũ (D1) |
| `G2-3` | Thiết lập kho: filter `plantId` + cross-check `X-Plant-Id`, `replaceDefaults` nullable (D2), khoá bi quan, kích hoạt lại cặp cũ, `deactivate` xoá cờ, chặn plant ngừng |
| `G2-4` | Plant/Warehouse `search`/`status`; Item `status`/`type`/`lotTracked`; timezone `ZoneId.of`; `ACTIVE_CHILDREN_EXIST` (D4); `items.unit` ↔ UOM (D5) |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Trigger `trg_item_warehouse_default_role` (`V66`) CHƯA BAO GIỜ là lớp chống ghi đồng thời.** Ở
   `READ COMMITTED` nó không thấy row chưa commit của transaction kia ⇒ hai upsert song song cùng thành
   công, plant còn **hai** default supply. Đo bằng `ItemWarehouseSettingConcurrencyIT`; mutation bỏ khoá
   ⇒ **cả 2 case đỏ ngay vòng 0**. Bảo vệ thật là `ItemRepository.lockByItemId` (`PESSIMISTIC_WRITE`)
   **trước** khi đọc peer (`B137`). Bất kỳ ràng buộc "tối đa một X" nào khác viết bằng trigger cũng nên
   bị nghi ngờ cùng cách.
2. 🔴 **`createForCompany` mang `@PreAuthorize`/`@Auditable` RIÊNG**, chỉ chia sẻ routine **private** —
   gọi method public kia qua `this` là bypass proxy, mất cả guard lẫn audit (bẫy `§0.19` #3, lần thứ ba).
3. 🔴 **Hai cách chuẩn hoá mã cùng tồn tại, có chủ đích:** Customer/Supplier trim + uppercase **rồi**
   kiểm regex (`" partner-01 "` ⇒ `PARTNER-01`); Plant/Warehouse/Item vẫn `@Pattern` chạy **trước** ⇒
   chữ thường bị 400. FE không yêu cầu đồng bộ — đừng "tiện tay" đổi (đổi là breaking cho client cũ).
4. **PATCH: `null`/omitted = giữ, `""` = xoá** (D3) — **khác** đề xuất FE (`null` = xoá). Java `record`
   không phân biệt omitted với `null` nếu không thêm `JsonNullable`; giữ một quy ước cho toàn API.
5. **`replaceDefaults` nullable** (D2): `null` = tự chuyển cờ (hành vi cũ, mọi client + seed script dựa
   vào), `false` = `DEFAULT_CONFLICT` 409 **không ghi gì**, `true` = chuyển. FE đề xuất mặc định `false`
   ⇒ sẽ làm client đang chạy bắt đầu nhận 409.
6. 🔴 **ĐÃ SỬA (toàn hệ thống, user chốt) — `leadTimeDays: 1.5` từng được nhận và lưu thành `1`** (200,
   đo live, trace `4efc9c9fb6144584`). Nguyên nhân: Jackson `ACCEPT_FLOAT_AS_INT` mặc định bật ⇒ **mọi**
   field số nguyên trên **mọi** endpoint đều bị cắt im lặng. Sửa bằng
   `spring.jackson.deserialization.accept-float-as-int: false` trong `application.yml` + handler
   `HttpMessageNotReadableException` đọc `MismatchedInputException.getPath()` để trả `errors[{field}]`
   (`"must be a whole number"`; field lồng `lines[0].lineNo`; **không** echo giá trị). 🔴 **`5.0` cũng bị
   từ chối** — Jackson coi mọi số có dấu chấm là số thực; `5` vẫn nhận. Đã rà repo: **không** script seed
   nào gửi số có dấu chấm vào field số nguyên (các số `0.08`/`0.15` là `quantityPer`, kiểu thập phân).
   Chỉ đổi cấu hình mà không sửa handler thì FE nhận "body is not valid JSON" **không** kèm field — đúng
   loại lỗi FE không hiển thị được. Nghiệm thu mutation 2/2 (bật lại coercion ⇒ 4 case đỏ vì 200; bỏ nhánh
   field ⇒ 4 case đỏ vì thiếu `errors[0].field`). Smoke live: `1.5`/`5.0` ⇒ 400 không ghi gì, `5` ⇒ 200,
   `capacityUnits: 1.5` ở tổ sản xuất cũng 400.
7. 🔴 **Bẫy bằng chứng (vi phạm `B2` suýt lọt vào file gửi FE):** smoke script đầu tiên prettify response
   qua `json.loads`/`json.dumps` của Python ⇒ `0.000001` thành `1e-06`, `30.000000` thành `30.0` — tức
   **viết sai chính câu trả lời Q12** (định dạng số trên wire). Đo lại bằng printer giữ nguyên literal số
   (parse số thành placeholder rồi thay lại). Bài học: mọi công cụ "làm đẹp" JSON giữa server và tài
   liệu đều là một lần viết tay.
8. **Số thập phân là JSON number với scale KHÔNG cố định**: response `PUT` lặp lại cách viết của request
   (`100`), `GET` sau khi đọc DB trả `100.000000`. Không đổi (đổi kiểu số toàn hệ thống là ngoài phạm vi),
   đã ghi rõ cho FE.
9. **ID không tồn tại trên endpoint `/{id}` trả 403, không phải 404** — guard tra bản ghi để suy công ty,
   không có bản ghi ⇒ `false` ⇒ từ chối **trước** service. Đúng với cả tài khoản quản trị (đo live). Có sẵn
   toàn repo (cùng hình dạng `WorkOrderPermissionGuard`, `§0.30`), không phải lỗi của phase này.
10. **Seed `D26-` không vỡ vì D5:** catalogue chỉ dùng đơn vị `EA`/`KG`/`PR`/`SET`, đều có trong
    `UOM_ROWS` và được tạo **trước** item. Không IT nào tạo item qua service/HTTP. Seeder `VIETBIKE`
    (`scripts/demo/`) **chưa** chạy lại — nó vốn đã lệch bố cục URL (`§0.46` #1).
11. **Q5 chưa rà:** hoạch định có bỏ qua kho `INACTIVE` khi chọn kho mặc định không — ngoài phạm vi
    lượt này, đã ghi vào mục "chưa nghiệm thu" của file gửi FE.

**Nghiệm thu mutation (3, đã revert — 3/3 đụng `src/main`):** bỏ `cast(:search as string)` khỏi
`CustomerRepository` ⇒ 2 case IT đỏ với đúng `lower(bytea)`; bỏ khoá item ⇒ 2/2 case concurrency đỏ vòng
0; vô hiệu filter `plantId` trong JPQL ⇒ 2 case IT đỏ (fixture **hai công ty**, bẫy PF-03 `§0.46`).

**Nghiệm thu:** `mvn -o clean verify` — **1320 case unit / 162 class + 184 case IT / 27 class IT,
failures = 0, errors = 0** (baseline 1225 + 154). Smoke HTTP thật (`spring-boot:run` profile dev, 8081,
Postgres + Redis qua `docker compose`) **59 lượt gọi, mọi status đúng kỳ vọng** trừ A10 (hệ quả #6);
fixture hậu tố `120952` đã ngừng hết, UOM thử `ZZSMK120952` còn `INACTIVE` trong danh mục dev.

**Breaking changes — wire: CÓ** (6 mục "chặn mới", không bỏ field, không đổi kiểu, không đổi mặc định
query param): deactivate Plant còn kho ⇒ 422 · timezone sai ⇒ 400 · tạo Item với UOM không có/ngừng ⇒
404/422 · PATCH đổi `unit` ⇒ 422 · `X-Plant-Id` lệch/sai định dạng trên settings ⇒ 409/400 · settings của
kho thuộc plant ngừng ⇒ 422 · **số có dấu chấm cho field số nguyên trên mọi endpoint ⇒ 400** (hệ quả #6). Bảng đầy đủ: `BE_RESPONSE_GD_02_2026-10-14_v2.md §3`. **Java positional: có** —
`SupplierCreateRequest`/`UpdateRequest`/`Response` +2 component cuối, `ItemWarehouseSettingRequest` +1,
`ItemService`/`ItemWarehouseSettingService`/`ItemImportTargetHandler` đổi constructor,
`ItemService.listItems` + `OrganizationService.listPlants`/`listWarehouses` đổi chữ ký. Test cũ **sửa**
theo `R10`.

---

### 0.51 `G3` – Nhập Excel Dữ Liệu Nền, Mẫu Cố Định, Atomic (2026-10-14)

Nguồn: `FE_HANDOFF_GD_03_2026-10-12.md` (P1). Kế hoạch + D1–D8 (user chốt, đều theo khuyến nghị): `NEXT_PHASE_PLAN.md §10`.
Trả lời FE: `BE_RESPONSE_GD_03_2026-10-14.md`. Migration **`V71`**. Bất biến **`B142`–`B148`** trong
`module/dataimport/CLAUDE.md` (**file mới** — module tồn tại từ `V59` mà chưa từng có tài liệu riêng).

| Endpoint | Ghi chú |
|---|---|
| `POST /v1/data-import/batches` | multipart; validate cả file ⇒ batch `READY`/`INVALID` **201**, **0 master** |
| `POST /v1/data-import/batches/{id}/commit` | `{expectedVersion}`; một transaction cả lô; stale ⇒ 409 `IMPORT_REVALIDATION_FAILED` **kèm `result`** |
| `GET /v1/data-import/batches[/{id}]` | theo **owner**; batch người khác ⇒ 404 |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Hai đường dùng chung `import_runs`, phân biệt bằng `kind`.** Mọi query đường cũ nay lọc
   `MAPPED_PROFILE` (`findWithDetailsByImportRunId` có `@Query` riêng — **giữ tên cũ** để không vỡ test, javadoc
   nói rõ). Bỏ lọc ⇒ `/v1/import-runs/{id}/cancel` hủy được batch `READY`.
2. 🔴 **`flush()` bên trong `try` của `DataImportBatchCommitter.commit` là load-bearing — và test đầu tiên KHÔNG
   chứng minh được điều đó.** Nghiệm thu mutation lần 1 (bỏ `flush`) **sống sót**: mã trùng đặt ở dòng 1, nên
   query kiểm trùng của dòng 2 tự auto-flush dòng 1 **trong** `try`. Chỉ va chạm ở **dòng cuối** mới lọt ra lúc
   commit transaction, ngoài `try`. Test race nay đặt mã chung **cuối** có chủ đích; mutation bị giết.
3. 🔴 **Bug thật do smoke HTTP tìm ra, unit + IT ban đầu đều xanh:** clock JVM trên Windows có độ phân giải
   **100 ns**, `TIMESTAMPTZ` lưu **µs** và **làm tròn** ⇒ response commit đầu nói `…612675100Z`, mọi lần đọc
   lại nói `…612675Z` — FE A17 ("receipt ổn định sau reload/retry") sẽ fail. Sửa: `committedAt` sinh sẵn ở
   độ chính xác µs; upload `refresh` entity trước khi trả `createdAt`. Guard: 2 assertion mới trong
   `DataImportBatchIT`, nghiệm thu mutation 2/2. 🔴 **Cùng hiện tượng tồn tại ở mọi response "vừa tạo" khác
   trong repo** (`createdAt` từ JPA auditing) — chỉ sửa trong phạm vi batch; toàn hệ thống là việc riêng.
4. 🔴 **`MaxUploadSizeExceededException` không có handler** ⇒ upload > 10 MB trả **500**. Lần thứ ba repo
   vá một exception "client gửi sai" rơi xuống catch-all (sau `§0.24`, `§0.41`). Nay 413 `FILE_SIZE`.
5. **Revalidation trước khi ghi là phòng thủ chồng lớp**: bỏ nó IT vẫn xanh (service master tự chặn), chỉ unit
   test bắt được. Giữ để lỗi trả **đúng ô** thay vì một exception chung.
6. **Mã lỗi: dùng mã sẵn có khi §5.3 đã có câu trả lời** — batch không `READY` ⇒ `STATE_CONFLICT`,
   `expectedVersion` lệch ⇒ `CONCURRENT_MODIFICATION` (không tạo `IMPORT_BATCH_NOT_READY`/`_VERSION_CONFLICT`
   như FE đề xuất). Mã file-level mới (`FILE_FORMAT`, `FILE_SIZE` 413, `TEMPLATE`, `EMPTY_FILE`, `ROW_LIMIT`)
   giữ đúng tên FE. Lỗi theo dòng dùng `ImportErrorCode` sẵn có + 3 mới (`FORMULA`, `TEXT_REQUIRED`, `CELL_TYPE`).
7. **`version` = `@Version` JPA ⇒ 0 sau upload**, không phải 1 như ví dụ FE (cùng quy ước `SalesOrder`, `§0.39`).
8. **Supplier `phone` ≤ 60** (cột `V20`) — template FE ghi 255 cho cả hai loại đối tác.
9. **Không permission mới** ⇒ `docs/roles-and-permissions.md` không đổi (`C10` không kích hoạt).

**Nghiệm thu mutation (10, đã revert — 10/10 đụng `src/main`, 10/10 bị giết):** bỏ `flush` trong `try`
(sau khi sửa test) · bỏ lọc owner · `left join` → `join` plant · `IMPORT_REVALIDATION_FAILED` → `STATE_CONFLICT`
(**cùng 409**) · guard bỏ cặp quyền cho owner · trùng mã chỉ gắn lần sau · bỏ revalidation trước ghi · ô số ở
cột mã được nhận · bỏ `refresh` · bỏ cắt µs.

**Nghiệm thu:** `mvn -o clean verify` — **1394 unit / 169 class + 192 IT / 28 class IT**, 0 failures/errors.
Smoke HTTP thật (`spring-boot:run` dev, 8081, Postgres + Redis Docker) **2 lượt × 78 lượt gọi**, 0 lệch kỳ vọng;
lượt 1 (hậu tố `200509`) phát hiện hệ quả #3, lượt 2 (`201002`) trên bản sửa — field mốc A/B: `committedAt`
khác/khớp giữa commit và retry. Commit 500 dòng: **~2.9 s**. Fixture đã ngừng (công ty, nhà máy, UOM, user).

**Breaking changes — wire: KHÔNG có** (thuần additive; `> 10 MB` 500 → 413 là sửa sai). **Java positional:
có** — `DataImportProperties` +2 component, `ImportTargetType` +2 hằng, `ImportRunStatus` +3, handler Item lộ
hằng `FIELD_*` `public`.

---

### 0.52 `G4` – Tồn Kho, Lô Và Chuyển Kho (2026-10-15)

Nguồn: `FE_HANDOFF_GD_04_2026-10-12.md` (P1). Kế hoạch + D1–D10: `NEXT_PHASE_PLAN.md §11`. Trả lời FE:
`BE_RESPONSE_GD_04_2026-10-15.md`. Migration **`V72`**. Bất biến **`B149`–`B154`** (`module/inventory/CLAUDE.md`).
**Không** permission mới ⇒ `docs/roles-and-permissions.md` không đổi (`C10` không kích hoạt).

| Gói | Nội dung |
|---|---|
| `G4-1` | Đổi trạng thái lô: kiểm quyền ở **mọi** kho còn tồn của lô; từ chối `NO_CHANGE` / quá hạn→`AVAILABLE` / còn reservation |
| `G4-2` | Chuyển kho atomic trong một plant: `POST/GET /v1/inventory/transfers`, `GET /v1/inventory/transfers/{id}` |
| `G4-3` | Kiểm đếm tuyệt đối `POST /v1/inventory/counts` + `version` trên `StockBalanceResponse` |
| `G4-4` | `GET /v1/inventory/movements/{id}`, `traceId`/`createdBy`/`createdByUsername` trên movement, `search` + `heldLotQuantity` trên aggregate, `GET /v1/inventory/reservations`, `expiresAt` khi nhận lô mới, `@Digits(13,6)` |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Lỗ hổng phân quyền thật, không ai báo:** trạng thái lô là **một** row `inventory_lots` dùng chung mọi
   kho, nhưng `@PreAuthorize` chỉ kiểm `warehouseId` trong body ⇒ người có quyền ở kho A đổi được chất lượng
   hàng nằm ở kho B ngoài scope. Nay kiểm mọi kho có `quantity > 0` (`B149`). Thiếu quyền ⇒ **403**, lệch
   D5 của kế hoạch (404): lô vẫn đọc được ở kho A nên 404 là sai nghĩa.
2. 🔴 **Smoke HTTP tìm ra lệch số liệu mà mọi test xanh không thấy:** `GET /balances/aggregate` có
   `onHand 12 = available 5 + rejected 1` + **6 đơn vị không nằm trong bucket nào** — lô `HOLD` không vào
   `qualityHoldQuantity` (cột đó là carrier hàng **không** lô, `B116`), và lô `AVAILABLE` quá hạn vẫn bị
   tính `available` trong khi `/issue` từ chối nó. User chốt: thêm `heldLotQuantity` (additive), lô quá hạn
   sang `expiredQuantity`. Test aggregate cũ (từ `caps2 done`) **pin đúng chỗ thiếu đó** mà không ghi lý do
   — đã thêm assertion `heldLotQuantity`, không xoá gì.
3. 🔴 **Lỗi µs của G3 (§0.51 #3) lặp lại ở bảng mới:** `postedAt` trả `…134628100Z` lúc tạo, `…134628Z` khi
   đọc lại. Sửa bằng `truncatedTo(MICROS)` trên **cột thường**. ⚠️ Cùng cách đó **không** sửa được
   `createdAt`/`updatedAt` của movement/balance: `@CreatedDate`/`@LastModifiedDate` ghi đè lúc persist —
   bản nháp đã cắt µs cho count rồi phải **gỡ** vì là no-op, và unit test kèm theo xanh chỉ vì mock không
   chạy auditing (bài học `R7` lần thứ tám). Sửa đúng chỗ là `DateTimeProvider` của auditing — toàn hệ thống,
   chưa làm.
4. 🔴 **Hết hạn chỉ chặn ở luồng kho thủ công** (quyết định user): `InventoryMovementService.issueManual` tách
   khỏi `issue()` — `MaterialIssueService` và FEFO reservation vẫn dùng `issue()`/`issueReserved()` không xét
   hạn, MRP/dashboard không đổi. Đừng "đồng bộ" bằng cách sửa `InventoryLot.canIssue()` — đó là đổi kết quả
   hoạch định, quyết định riêng.
5. 🔴 **Khoá nguồn chuyển kho là load-bearing** (`B151`): một `SELECT … FOR UPDATE` theo `balanceId`, **không**
   join fetch (Postgres từ chối `FOR UPDATE` trên phía nullable của outer join). Mutation bỏ `@Lock`: không âm
   kho (vẫn có `@Version`) nhưng kẻ thua nhận 409 chung thay vì dòng nào thiếu ⇒ `StockTransferIT` đỏ.
6. **Kiểm đếm không có `MovementType` mới** — ghi `ADJUST_IN`/`ADJUST_OUT` với `reference_type = STOCK_COUNT`;
   replay tra **cả hai** loại vì dấu chỉ biết sau khoá. `POST /adjust` giữ nguyên nghĩa delta.
7. **`InventoryReceiveCommand.expiresAt` mang `@JsonInclude(NON_NULL)`** để fingerprint idempotency lưu trước
   G4 vẫn khớp khi replay (có test băm JSON literal). Mapper production (Boot) có `JavaTimeModule`; mapper
   `new ObjectMapper()` trong test thì **không** — test nào băm command có `Instant` phải đăng ký module.
8. `@Digits(fraction = 6)` **đếm cả số 0 thừa** (Hibernate Validator không `stripTrailingZeros`): `1.1234560`
   ⇒ 400. Chấp nhận (nhất quán, FE gửi tối đa 6 số lẻ), đã ghi cho FE.
9. **Phát hiện tài liệu sai trong chính kế hoạch:** `GET /movements` **đã có** filter `lotId` (§11.1 R02 ghi
   "không có"). Không viết lại.
10. 🔴 **Bẫy công cụ, trả giá trong lượt này:** (a) VS Code Java LS ghi đè `target/classes` khi file nguồn bị sửa
    lúc `mvn` đang chạy ⇒ 636 case lỗi `NoClassDefFoundError`; baseline phải đo trên `git worktree` sạch.
    (b) Hoàn tác mutation bằng `git checkout -- <file>` **xoá luôn mọi thay đổi chưa commit của file đó** —
    đã mất và phải áp lại `StockBalanceRepository`; từ đó dùng `cp` bản sao lưu. (c) `spring-boot:run` giữ
    `target/` ⇒ phải dừng app trước `mvn clean`.

**Nghiệm thu mutation (7, đã revert — 7/7 bị giết):** bỏ `@Lock` nguồn chuyển kho (IT tranh chấp đỏ) · bỏ
`@Transactional` của create (3/4 case IT đỏ, gồm case rollback giữa chừng) · guard chỉ kiểm kho trong body
(case "một kho ngoài scope" đỏ) · `NO_CHANGE` → `STATE_CONFLICT` (**cùng 409**) · bỏ gate hết hạn ở
`issueManual` · bỏ sàn `reserved` khi đếm · bỏ điều kiện hết hạn khỏi bucket `available` (IT đỏ).

**Nghiệm thu:** `mvn -o clean verify` — **1461 unit / 173 class + 202 IT / 29 class**, 0 failures/errors
(baseline đo lại trên worktree sạch: 1394 + 192). Sau lượt đó chỉ gỡ một dòng no-op ở count + một assertion
(hệ quả #3), chạy lại `InventoryMovementServiceTest` + `StockTransferServiceTest` xanh (53 case). Smoke HTTP
thật (`spring-boot:run` dev, cổng 8090, Postgres + Redis Docker) **4 lượt × 70–72 gọi, 0 lệch status**;
lượt 2 (`170021`) xác nhận bucket aggregate cộng đủ, lượt 5 (`170856`) xác nhận `postedAt` khớp ở tạo /
retry / chi tiết / danh sách. Fixture công ty `GD4-*` đã ngừng; lô quá hạn dựng bằng một câu SQL (API cố ý
không cho ghi hạn quá khứ).

**Breaking changes — wire: CÓ** (bảng đầy đủ ở `BE_RESPONSE_GD_04_2026-10-15.md §3`): đổi trạng thái lô bị chặn
khi kho khác ngoài scope / cùng trạng thái / quá hạn→AVAILABLE / còn reservation · `POST /v1/inventory/issue`
từ chối lô quá hạn · số lẻ thứ 7 ⇒ 400 trên mọi request inventory · aggregate `availableQuantity` giảm với lô
quá hạn (chuyển sang `expiredQuantity`) · `availableQuantity` của lô quá hạn trên `/balances`/`/lots` về `0`.
Additive: 6 endpoint, `version`, `traceId`/`createdBy`/`createdByUsername`, `heldLotQuantity`, `expiresAt`,
`search`. **Java positional: có** — `InventoryReceiveCommand` +1, `StockMovementResponse` +3,
`StockBalanceResponse` +1, `StockBalanceAggregateResponse` +1, `aggregateBalances` +2 tham số,
`InventoryService`/`InventoryLotService`/`InventoryPermissionGuard` đổi constructor.

---

### 0.53 `G5` – BOM, Quy Trình, Ca/Lịch/Trung Tâm, Công Suất (2026-10-16)

Nguồn: `FE_HANDOFF_GD_05_2026-10-13.md` (P1). Kế hoạch + D1–D12: `NEXT_PHASE_PLAN.md §12`. Trả lời FE:
`BE_RESPONSE_GD_05_2026-10-16.md`. Migration **`V73`**. Bất biến **`B155`–`B158`** (bom), **`B159`–`B161`** (routing),
**`B162`–`B164`** (shift; workcenter mục 8), **`B165`–`B167`** (workorder), **`B168`–`B170`** (dataimport).
**Không** permission mới ⇒ `docs/roles-and-permissions.md` không đổi.

| Gói | Nội dung |
|---|---|
| `G5-1` | BOM: `bomCode`/`versionNumber`/`outputQuantity`/`effectiveFrom`, `PUT bom-drafts` atomic, copy, versions, POST deactivate, `search`/`latestOnly`, khoá họ khi activate |
| `G5-2` | Routing: công đoạn trước, `PUT routing-drafts` cả đồ thị, copy, versions, một nháp mở/sản phẩm, lọc `plantId`/`workCenterId`/`search` |
| `G5-3` | Ca `workingMinutes` + chặn nghỉ chồng/0 phút; `expectedVersion` PATCH; chặn ngừng khi còn tham chiếu (`REFERENCE_IN_USE`); `search` |
| `G5-4` | Tải chia theo phần giờ làm việc (D9), `GET capacity/loads`, `POST capacity/preview` (chỉ đọc), điều chỉnh chồng lịch ⇒ 409 (D10) |
| `G5-5` | Nhập BOM Excel `kind=bom`, mẫu `omniplant-bom-v1`, một `saveDraft` mỗi nhóm trong transaction của batch |

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`quantity_per` vẫn là nguồn duy nhất** (D1). Nợ `E` (`F10`) bị loại vì "đổi công thức nổ BOM"; G5 né đúng chỗ đó: lưu
   `output_quantity` chỉ để form theo lô đọc lại, MRP/snapshot WO/costing không đổi. Thương không vừa 6 số lẻ ⇒ từ chối, **không**
   làm tròn — làm tròn là form mở lại hiện số khác số đã gõ.
2. 🔴 **Khoá họ sản phẩm phải lấy TRƯỚC khi load BOM/routing.** Activate đọc `parentItemId` bằng query riêng → `lockItem` → mới load.
   Load trước rồi khoá thì request sau làm việc trên ảnh cũ trong persistence context. Mutation bỏ khoá ⇒ `BomDraftIT` đỏ.
3. 🔴 **Hai kiểu "đỗ tạm" khác nhau:** dòng BOM đỗ ở `lineNo` âm (không có CHECK), công đoạn routing đỗ ở `sequence ≥ 1 000 000`
   (`chk_routing_operations_sequence > 0`). Mutation bỏ bước đỗ routing ⇒ `RoutingDraftIT` đỏ với vi phạm unique.
4. 🔴 **Mutation bỏ `@Transactional` của `saveDraft` KHÔNG làm đỏ ca "dòng cuối lỗi ở DB"** — tạo mới là **một** `saveAndFlush`
   cascade nên tự nguyên tử. Ca đó chứng minh rollback, không chứng minh annotation; các ca khác của `BomDraftIT` mới bắt được.
5. 🔴 **Bảng công suất từng dồn cả công đoạn vào ngày bắt đầu** (`§0.30` #4 gọi là "đơn giản hoá có chủ đích"). G5 chia theo tỉ lệ
   **phút làm việc** (`CapacityLoadCalculator.allocate`), không lịch thì theo giờ đồng hồ; `aggregateExistingLoad` + `CapacityLoadProjection`
   đã xoá; board chọn công đoạn **giao** kỳ, ranh giới kỳ đổi sang Instant ở Java (không còn `function('timezone', …)` trong JPQL).
   Lịch phải phủ **toàn bộ** khoảng của công đoạn, nếu không mẫu số thiếu (`spanStart`/`spanEnd`).
6. 🔴 **Release và xem trước xếp lịch KHÁC nhau:** xem trước chia `capacityUnits` (công thức FE), release thì không. Cố ý chưa đổi
   release (dời lịch WO sau này). Ghi rõ trong file trả lời FE.
7. **`computeWorkingIntervals` đánh giá từ ngày trước `from`** để đếm đuôi ca đêm (A21 330) và **gộp** khung chồng — `computeDay`
   một mình không làm cả hai. `advance` (release) vẫn dùng `computeDay` theo thứ tự danh sách — chưa sửa (ngoài phạm vi).
8. **BOM import không phải `ImportTargetHandler`** (đường mapped-profile không gom nhóm được). `createdIds` nay `distinct`.
9. **Chặn ngừng theo tham chiếu tạo phụ thuộc gói ngược** (`shift → workcenter`, `workcenter → routing/workorder`) qua lookup service —
   không vòng bean (đã kiểm), cùng tiền lệ `inventory → workorder` (`C2-2`).
10. 🔴 **Bẫy công cụ:** heredoc bash chứa `'''`/xen kẽ nháy vẫn có lúc vỡ — viết script ra file bằng Write. Docker Desktop có thể tắt
    qua đêm: smoke cần `docker compose up -d` trước khi chạy app.

**Nghiệm thu mutation (7, đã revert — 7/7 bị giết):** bỏ `lockItem` khi activate BOM · bỏ `@Transactional` của `saveDraft` (3/4 ca
`BomDraftIT` đỏ) · bỏ chia `capacityUnits` ở xem trước · allocate quay về "trọn ngày bắt đầu" · `OPERATION_SEQUENCE_CONFLICT` →
`STATE_CONFLICT` (**cùng 409**) · bỏ `cast(:search as string)` ở `ShiftRepository` (IT đỏ, `lower(bytea)`) · bỏ bước đỗ sequence routing.

**Nghiệm thu:** `mvn -o clean verify` — **1548 unit / 179 class + 215 IT / 32 class**, 0 failures/errors (baseline đo lại trên
worktree sạch: 1461 + 202). Smoke HTTP thật (`spring-boot:run` dev, cổng 8090, rate limit tắt) lượt **`231527`**: 92 lượt gọi,
mọi status đúng kỳ vọng nghiệp vụ (một kỳ vọng fixture viết sai: reserve trả 201). A27 khớp ví dụ FE (1030 / 960 / 70, kết thúc
08:35 hôm sau); xem trước gọi 2 lần, đếm DB (WO/reservation/movement/operation) không đổi. `V73` chạy sạch trên DB dev có dữ liệu
demo. Fixture công ty `GD5-231527` **chưa** ngừng (Docker tắt trước khi dọn).

**Breaking changes — wire: CÓ** (bảng đầy đủ `BE_RESPONSE_GD_05_2026-10-16.md §3`): điều chỉnh chồng lịch 200+cờ → 409; điều
chỉnh WO không `RELEASED`/`IN_PROGRESS` hoặc công đoạn đã báo sản lượng → 409; lý do < 5 ký tự → 400; bảng công suất đổi số liệu;
activate BOM/routing với vật tư/trung tâm ngừng → 422; API dòng BOM cũ quá 6 số lẻ → 400; ca nghỉ chồng/0 phút → 422; ngừng
ca/lịch/trung tâm còn tham chiếu → 422 `REFERENCE_IN_USE`; lịch gán ca ngừng → 422. **Java positional: có** — `BomResponse` +6,
`BomLineResponse` +2, `BomTreeNodeResponse` +2, `RoutingResponse` +7, `RoutingOperationResponse` +2, `ShiftResponse` +1,
`WorkCenterResponse` +2, `CapacityBoardLineResponse` +1, `DataImportBatchResponse` +1, update request ca/lịch/trung tâm +1;
`activateBom`/`activate` (routing) +1 tham số, `listBoms`/`list` (routing, ca, lịch, trung tâm) thêm tham số; constructor
`ShiftService`, `WorkCalendarService`, `WorkCenterService`, `CapacityBoardService`, `ScheduleAdjustmentService`,
`FixedTemplateValidator`, `DataImportBatchCommitter`, `CapacityBoardService.DayContext` đổi.

---

### 0.72 Track `CF-*` – Capstone 3 Follow-up (từ 2026-10-27)

Nguồn: `BE_GAP_NEXT_PLAN.md` + `CAPSTONE3_BE_FOLLOWUP_2026-10-20/`. Quyết định PO (2026-10-27, `AskUserQuestion`): làm `CF-0`→`CF-5`
theo đề xuất mặc định của BE; `CF-6` hoãn; `CF-7` phương án **A**. Test của mỗi phase do sub agent viết và chạy (yêu cầu user).

#### CF-1 – Chặn điều chỉnh lô QC còn sở hữu (2026-10-27)

`POST /v1/inventory/adjust` trên lô còn phần chờ QC mua (`PurchaseQcLookupService`), còn phần chờ QC sản xuất (`LotQcOriginLookupService`)
hoặc lô con `REJECTED` nguồn `PURCHASE_QC_DECISION`/`PRODUCTION_QC_DECISION` ⇒ `409 STATE_CONFLICT`. Guard (`LotQcOwnershipGuard`) chạy qua overload
`InventoryMovementService.adjust(command, key, lotGuard)`: sau replay, sau resolve lô, trước ghi. Lô `HOLD` đặt tay, lô con đạt, hàng không lô
**không** bị chặn. `count` tracked/non-tracked đã chặn từ G4 (`canIssue` / `counted < reserved + qualityHold`). Bất biến **`B263`**.
**Hệ quả:** chưa có workflow "sửa số lượng nguồn" — lô nguồn QC không điều chỉnh/kiểm đếm được cho tới khi QC quyết. Lô con bị QC **sản xuất** loại
cũng bị chặn (rộng hơn plan một bước, cùng lý do với `changeStatus`).
**Nghiệm thu mutation (5/5 bị giết):** bỏ guard · `STATE_CONFLICT` → `IDEMPOTENCY_CONFLICT` (cùng 409) · mở rộng sang mọi lô ≠ `AVAILABLE` · gọi
guard trên đường replay · bỏ vế `PRODUCTION_QC_DECISION`.
**Nghiệm thu:** `mvn -o clean verify` — **2151 unit + 317 IT**, 0 failures/errors (baseline 2141 + 315). **Breaking wire: hẹp** — `adjust` lô nguồn
QC 200 → 409. **Java positional:** `InventoryService` constructor +1 (`LotQcOwnershipGuard`).

#### CF-2 – Lô hết hạn bị loại khỏi hoạch định và luồng lệnh SX (2026-10-27)

PO chốt (2026-10-27) **đảo** quyết định 2026-10-15 ("hết hạn chỉ chặn luồng kho thủ công", `B154`, §0.52 #4). Lô `AVAILABLE` có `expiresAt <= now`:
loại khỏi 3 aggregate khả dụng/hoạch định + FEFO giữ chỗ WO, đếm vào `excludedLotCount`; giữ chỗ tay ⇒ `LOT_NOT_ELIGIBLE`; `MaterialIssueService` post/approve
dùng `issueManual`/`issueReservedManual`. Readiness/release gate trừ phần giữ trên lô không còn xuất được (`sumIneligibleActiveRemainingByWorkOrderId`) — thêm
`ineligibleReservedQuantity` + `blocker`; `reservedQuantity` giữ nghĩa F9. Reservation cũ **không** tự huỷ. Bất biến **`B264`** (inventory), **`B265`** (workorder).
**Hệ quả:** (1) số MRP đổi khi kho có lô hết hạn — dữ liệu demo không có lô hết hạn trừ khi chạy `set-lot-expiry.sh`, nên A/B trên `D26-` cần bước đó;
(2) `excludedLotCount` tính thêm lô hết hạn (rộng hơn plan một bước, để planner thấy vì sao tồn biến mất); (3) `issue()`/`issueReserved()` không xét hạn chỉ còn
cho đường nội bộ (QC) — test cũ `issue_workOrderPath_*` đổi tên thành `issue_internalPath_*`, assertion giữ nguyên.
**Nghiệm thu mutation (9/9 bị giết):** bỏ điều kiện hạn ở `aggregatePlanningQuantities` · ở FEFO · ở giữ chỗ tay · post về `issue` · approve về `issueReserved` · bỏ
vế hạn ở query ineligible · `>` → `>=` (biên) · release gate bỏ qua ineligible · bỏ vế hạn ở `aggregateExcludedLotCounts`. Mutation query dùng `or 1 = 1` để giữ
tham số bind (xoá vế làm hỏng query lúc khởi động ⇒ "giết" giả).
**Nghiệm thu:** `mvn -o clean verify` — **2173 unit + 325 IT**, 0 failures/errors (baseline 2151 + 317). **Chưa:** đo A/B số MRP trên app chạy thật, smoke HTTP.
**Breaking wire: CÓ** — số MRP đổi khi có lô hết hạn; giữ chỗ/xuất/duyệt lô hết hạn 200 → 409; release có thể chặn với phần giữ trên lô hết hạn. Additive: 2 field
readiness (compat constructor 8 tham số). **Java positional:** 4 query `StockBalanceRepository` + `findIssuableBalancesFefo` +1 tham số `Instant now`;
`WorkOrderMaterialReadinessLineResponse` +2.

#### CF-3 – Tách phần chờ / phần loại QC mua trong vùng chờ (2026-10-27)

`GET /v1/inventory/balances/aggregate` thêm `pendingPurchaseQcQuantity`, `rejectedPurchaseQcQuantity`, `unclassifiedQualityHoldQuantity` (không clamp),
`qualityBreakdownKnown` — chỉ dòng phiếu nhận **không lô**, phiếu `POSTED` + `quality_held`. Phần chờ QC sản xuất không lô ⇒ `unclassified` (PO chốt).
Một query tổng hợp mỗi trang. Không migration, không quyền mới. Bất biến **`B266`**.
**Hệ quả:** mutation "gọi lookup mỗi dòng" sống sót lượt đầu vì `times(1)` gắn với đúng đối số của lần gọi cả trang — phải thêm kiểm tổng số lần gọi bất kể
đối số (bài học C15: kiểm "đúng một lần" phải đếm mọi lần gọi, không chỉ lần gọi khớp đối số).
**Nghiệm thu mutation (6/6 bị giết):** pending cộng lại phần loại · bỏ lọc `POSTED` · bỏ lọc `quality_held` · bỏ lọc dòng không lô · clamp `unclassified` · lookup mỗi dòng.
**Nghiệm thu:** `mvn -o clean verify` — **2180 unit + 327 IT**, 0 failures/errors (baseline 2173 + 325). **Breaking wire: KHÔNG** (additive; compat constructor
14 tham số). **Java positional:** `InventoryService` constructor +1 (`PurchaseQcLookupService`), `StockBalanceAggregateResponse` +4.

---

### 0.71 `G11` – Giá Thành Sản Xuất, Chênh Lệch Và Đóng Lệnh (2026-10-27)

Nguồn: `FE_HANDOFF_GD_11_2026-10-15.md` (P1). Kế hoạch + D0–D12 (user chốt 2026-10-27, đều theo khuyến nghị): `NEXT_PHASE_PLAN.md §18`.
Trả lời FE: `BE_RESPONSE_GD_11_2026-10-27.md`. Migration **`V90`**–**`V92`**. Bất biến **`B256`–`B262`** (`module/workorder/CLAUDE.md`).
Quyền mới: 3 (`docs/roles-and-permissions.md`, `C10` kích hoạt). Mã lỗi mới: 4.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Kho vẫn không có giá trị (D1, D3).** Giá vật tư của G11 là `ItemStandardCost` chụp lúc phát hành; "giá trị còn kho" và giá vốn
   phiếu giao là **phân bổ ghi nhận** trong `production_cost_adjustments`, không phải số dư kế toán. Pool (thành phẩm, kho đầu ra) chỉ
   định giá được khi tồn lúc phát hành = 0 và sau đó chỉ có biến động `WORK_ORDER` của chính lệnh / `PRODUCTION_QC_DECISION` của lệnh /
   `DELIVERY`. Giá bình quân kho thật vẫn là việc của `P7` (nay `V94`).
2. 🔴 **Hai hệ giá thành (D2):** `/variance` (`P3`, theo vật tư) **không đổi**; `/production-costs` (G11, theo trung tâm + thời gian).
   Đừng "đồng bộ" một bên theo bên kia — đó là breaking change cho FE.
3. 🔴 **Đường `close` cũ (D5):** lệnh có dòng `production_cost_baselines` ⇒ `409 CLOSE_BLOCKED`. Seed demo
   (`scripts/demo-capstone2`) và `scripts/demo` **chưa** gọi đường mới — chưa chạy lại; lệnh của seed phát hành sau bản này sẽ không
   đóng được bằng `close`.
4. **Code nằm trong `module/workorder`** (không module mới) vì nguồn là sổ của lệnh (rule C7). `OperationLedger` đổi sang `public` để
   gói `service/cost` dùng; hướng phụ thuộc mới `workorder → delivery` (`DeliveryLookupService.findPostedPoolLines`, không vòng bean)
   và `workorder → inventory` (`StockPoolLookupService`, mới).
5. **Khoá khi chốt theo khuôn B194:** native `select … for update` theo id rồi `refresh` — guard `@PreAuthorize` đã nạp lệnh vào
   persistence context (OSIV). E2E chứng minh: luồng thua chờ khoá rồi nhận `CONCURRENT_MODIFICATION` (version 11 ≠ 10).
6. 🔴 **Bài học mutation:** số của fixture FE (F1 `19166.666667 + 15333.333333 = 34500` đúng tuyệt đối; F4 `Vi = 1`) **không phân
   biệt được** phân bổ lũy kế với chia riêng, hay luật phần dư với chia tỷ lệ — hai đột biến sống sót cho tới khi thêm ca số lẻ
   (`cumulativeReceiptShares_*`, `lastReturn_takesTheResidual_*`). Số mẫu "đẹp" của người đặt yêu cầu không phải là test đủ.
7. **R24 không cần sửa code:** mọi đường ghi đã loại `CLOSED` từ trước (§ cuối `module/workorder/CLAUDE.md`).
8. **Lọc `readiness` ở C3 tính giá thành của mọi lệnh khớp bộ lọc khác** (readiness dẫn xuất từ nguồn sống) — lệch C14 có chủ đích,
   đã nói rõ với FE. Không lọc thì chỉ tính các lệnh của trang.
9. 🔴 **Bẫy công cụ lặp lại:** heredoc bash chứa ba dấu nháy đơn liền nhau vỡ (§0.53 #10) — viết script ra file bằng Write; đọc `.env` bị chặn ⇒ smoke
   HTTP trên app chạy thật **chưa chạy** (cần thông tin đăng nhập admin).

**Nghiệm thu mutation (13, đã hoàn tác — 13/13 bị giết, 2 sau khi thêm ca):** phân bổ phần QC không lũy kế · lần hoàn cuối không nhận
phần dư · vật tư theo giá trị cấp · bỏ kiểm chồng giờ · phân bổ phiếu nhập không lũy kế · bỏ lượt xử lý lại · fingerprint bỏ dòng giao
· coi mọi phiếu giao là của lệnh · sai thứ tự ưu tiên mã lỗi · bỏ chặn `close` cũ · bỏ kiểm version đơn giá · không chia
`capacityUnits` · bút toán còn kho có `delta = 0`.

**Nghiệm thu:** `mvn -o clean verify` — **2141 unit + 315 IT**, 0 failures/errors (baseline 2085 + 312). `V90`–`V92` chạy sạch trên DB
trống (Testcontainers). E2E (`ProductionFlowE2EIT.productionCost_*`, HTTP + Postgres thật): baseline 22250 không đổi sau khi tăng đơn
giá; `close` cũ 409; checklist `BLOCKED`/`NO_PREVIEW`; tính thử không đổi version/biến động; `READY`, thực tế 92000; nguồn sai 409;
hai lệnh chốt song song 200 + 409; run `FINALIZED`, version lệnh +1, một bút toán 0 lượng / 92000. **Chưa:** smoke trên app chạy
thật; `V90`–`V92` chưa chạy trên DB dev có dữ liệu demo.

**Breaking changes — wire: CÓ, hẹp:** `POST /v1/work-orders/{id}/close` ⇒ `409 CLOSE_BLOCKED` cho lệnh phát hành sau bản này. Còn
lại additive (13 endpoint). **Java positional: có** — `WorkOrderService` constructor +1 (`ProductionCostBaselineService`);
`WorkOrderCommand` +3, `AuditAction` +3, `BusinessErrorCode` +4; `OperationLedger` public.

---

### 0.70 `G10d` – Thanh Toán Khách Hàng, Tiến Độ Đơn, Liên Kết Ngược (2026-10-27)

Nguồn: `FE_HANDOFF_GD_10_2026-10-15.md` R16–R18, R24, Q8, Q26–Q30, Q33, Q38, A24–A33, A37. Kế hoạch: `NEXT_PHASE_PLAN.md §17`. Trả lời FE:
`BE_RESPONSE_GD_10_2026-10-27_v3.md` (phần 4/4 — **GĐ10 đủ ở BE**). Migration **`V89`**. Bất biến **`B251`–`B255`** (`module/billing/CLAUDE.md`).
Không quyền mới (seed ở `V86`), không mã lỗi mới.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Khoá hàng hoá đơn, không khoá đơn bán.** Một thanh toán có thể trải nhiều đơn của một khách; thứ tuần tự hoá hai nháp tranh cùng phần còn
   phải thu là `lockRows` native theo id tăng dần + `refresh` (khuôn `B194`). Mutation bỏ khoá ⇒ ca tranh chấp IT đỏ.
2. 🔴 **Ghi sổ dời version của từng hoá đơn được trả** (FE Q12, khuôn `G9a`) — `setUpdatedAt` trước `saveAllAndFlush`. Lưu nháp trên version cũ của
   hoá đơn ⇒ `409 CONCURRENT_MODIFICATION` field `allocations[i].expectedInvoiceVersion`; ghi sổ **không** kiểm version hoá đơn (số dư mới là thứ quan trọng).
3. **Không cột "đã thu" trên hoá đơn** — `paidAmount`/`outstandingAmount`/`paymentState` dẫn xuất từ phân bổ `POSTED` mỗi lần đọc (một query batch mỗi
   trang). Bộ lọc `paymentState` lặp lại cùng công thức trong JPQL (`B254`) — đổi một nơi phải đổi cả hai.
4. **Tiến độ đơn ở `module/billing`** để không thêm chiều `sales → billing`. Trạng thái đơn **không** đổi theo giao/thu — giao đủ, thu đủ vẫn `CONFIRMED`.
5. **`related` theo phiếu nhập/WO** đi qua `ProductionOutputLotLookupService` (`workorder/service/query`): lô đầu ra của phiếu `APPROVED` + lô con QC
   chấp nhận. Hàng không theo lô không truy ngược được.
6. **Không có lớp giá trị** (D7 hoãn lần thứ năm): thanh toán không sinh bút toán; không có tiền ứng / chưa phân bổ, không hoàn tiền.
7. **Bẫy lặp lại:** Docker Desktop khởi động lại qua đêm ⇒ `erp-postgres` `Exited (255)`, app chết `Connection to localhost:5434 refused` —
   `docker compose up -d postgres redis` trước smoke; sửa test rồi chạy `mvn verify` không `clean` ⇒ Java LS ghi đè `target/test-classes` ⇒ IT
   "Unresolved compilation problems" — chạy lại bằng `clean`.

**Nghiệm thu mutation (23, đã revert — 23/23 bị giết):** phần đã thu tính cả nháp (IT) · sửa tính trùng phần của chính nó (IT) · bỏ Cp · bỏ P · bỏ so
header với tổng phân bổ · nhận hoá đơn của khách khác · nhận hoá đơn nháp · bỏ kiểm version hoá đơn · ghi sổ không dời version hoá đơn · ghi sổ bỏ kiểm
lại · ai cũng sửa/huỷ · bỏ khoá hoá đơn (IT tranh chấp) · thanh toán trước ngày hoá đơn · hoá đơn 0 đồng thành `UNPAID` · đảo bộ lọc `paymentState` (IT) ·
MANAGE ở một nhà máy là đủ · quyền hoá đơn đọc được thanh toán · khoản phải thu không cần quyền hoá đơn · giá không cần quyền giá · `related` nhận hai
nguồn · lô đầu ra gồm phiếu chưa duyệt (IT) · lô con bỏ lọc WO (IT) · sửa không dirty header trước bulk delete (IT — ca chuyển phân bổ với header y
nguyên, thêm **trước** khi chạy mutation vì ca có sẵn đổi `amount` nên header tự dirty).

**Nghiệm thu:** `mvn -o clean verify` — **2085 unit + 312 IT**, 0 failures/errors (baseline 2068 + 303). `V89` chạy sạch trên DB dev. Smoke HTTP thật
(`java -jar` dev, cổng 8090) lượt **`100050`**: **44/44** (bảng ở `BE_RESPONSE_GD_10_2026-10-27_v3.md §7`). Fixture `GD10D-100050` (kèm user
`g10d.reader.*`/`g10d.biller.*`/`g10d.cashier.*`) **chưa** ngừng.

**Breaking changes — wire: KHÔNG** (thuần additive: 10 endpoint, 3 field + 1 bộ lọc trên hoá đơn, 2 tham số trên `related`; `related` không có nguồn
nào vẫn là `400 VALIDATION_ERROR` field `lotId` như trước). **Java positional: có** — `SalesInvoiceResponse` +3, `SalesInvoiceQueryService`
constructor +1 (`CustomerPaymentSupport`) + `list` +1 tham số, `SalesInvoiceRepository.search` +1, `DeliveryQueryService` constructor +1
(`ProductionOutputLotLookupService`) + `related` +2 tham số, `DeliveryLookupService` +1 method, `AuditAction` +4.

---

### 0.69 `G10c` – Hoá Đơn Bán Hàng (2026-10-26)

Nguồn: `FE_HANDOFF_GD_10_2026-10-15.md` R11–R15, R27, Q7, Q8, Q15, Q22, A14–A23. Kế hoạch: `NEXT_PHASE_PLAN.md §17`. Trả lời FE:
`BE_RESPONSE_GD_10_2026-10-26_v2.md` (phần 3/4). Migration **`V88`**. Module mới `module/billing`, bất biến **`B246`–`B250`**
(`module/billing/CLAUDE.md`). Không quyền mới. Mã lỗi mới `PRICE_UNKNOWN`, `PRICE_STALE`.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Tiền do server tính, không bao giờ từ client.** `InvoicePricing` là hàm thuần: `round((billedBefore + qty) × price) − billedAmountBefore`,
   HALF_UP, nền = hoá đơn `POSTED`, các dòng cùng dòng đơn đi theo thứ tự request. Đổi thứ tự dòng có thể đổi cách chia đồng lẻ giữa các dòng
   (tổng không đổi).
2. 🔴 **Snapshot ≠ số tiền.** Ghi sổ so **cả** snapshot đã lưu **lẫn** số tiền đã lưu. Lượt mutation đầu lộ ra test A22 (lượng lẻ) không chứng
   minh được bước so snapshot vì số tiền cũng đổi; ca lượng nguyên mới chứng minh được — `aMovedBaseIsStale_evenWhenTheAmountsWouldNotChange`.
3. **Hoá đơn khoá cùng hàng với phiếu giao** (dòng `sales_orders`) nhưng **không** `touch` đơn — nó không đổi trường nào của đơn.
4. **Hoá đơn không có cột đếm "đã lập"** trên dòng phiếu giao — dẫn xuất từ `sales_invoice_lines` (B và Ci), như hạn mức giao (`B241`).
5. **`paid`/`outstanding`/`paymentState`** chưa có — `G10d` thêm cùng thanh toán; hoá đơn 0 đồng đã lập được (`NO_AMOUNT_DUE` ở `G10d`).
6. **Smoke HTTP lần này không tìm ra lỗi mới**; một lần chạy hỏng là do script (đơn đặt 10 mà giao tới 11).

**Nghiệm thu mutation (17, đã revert — 17/17 bị giết sau khi thêm ca C8):** làm tròn DOWN · nền không dời trong một hoá đơn · snapshot bỏ
`pricingVersion` · hạn mức bỏ nháp khác · hạn mức bỏ hoá đơn đã ghi sổ · sửa tính trùng phần của chính nó · lưu tin mọi snapshot · ghi sổ bỏ
so nền (🔴 **sống sót lần đầu**) · ghi sổ tin số tiền đã lưu · phiếu giao nháp làm nguồn · dòng của đơn khác làm nguồn · ai cũng sửa/huỷ ·
version sau bulk delete · ngày trước phiếu giao nguồn · tạo không khoá đơn · `PRICE_STALE` → `STATE_CONFLICT` (**cùng 409**) · replay sau version.

**Nghiệm thu:** `mvn -o clean verify` — **2068 unit + 303 IT**, 0 failures/errors (baseline 2053 + 294). `V88` chạy sạch trên DB dev. Smoke HTTP
thật (`java -jar` dev, cổng 8090) lượt **`213414`**: **31/31** (bảng ở `BE_RESPONSE_GD_10_2026-10-26_v2.md §7`). Lượt `213401` dừng giữa chừng
do lỗi script. Fixture `GD10C-213401`, `GD10C-213414` (kèm user `g10c.store.*`/`g10c.biller.*`) **chưa** ngừng.

**Breaking changes — wire: KHÔNG** (9 endpoint mới, 2 mã lỗi mới). **Java positional: có** — `DeliveryLookupService` +2 method,
`DeliveryLineRepository` +2 query, `BusinessErrorCode` +2, `AuditAction` +4.

---

### 0.68 `G10b` – Phiếu Giao Hàng (2026-10-25)

Nguồn: `FE_HANDOFF_GD_10_2026-10-15.md` R03–R09, R19–R28, Q9–Q14, Q19, Q25, Q28, Q32–Q40. Kế hoạch: `NEXT_PHASE_PLAN.md §17` (D1, D2,
D7, D8, D9). Trả lời FE: `BE_RESPONSE_GD_10_2026-10-25.md` (v1, phần 1–2/4, gộp `G10a`). Migration **`V87`**. Module mới `module/delivery`,
bất biến **`B241`–`B245`** (`module/delivery/CLAUDE.md`). Không quyền mới (10 quyền seed ở `V86`). Mã lỗi mới `SOURCE_UNKNOWN`.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Bẫy `B212` lần thứ ba, smoke tìm ra:** header phải dirty **trước** flush đầu tiên. Bổ sung giá (`G10a`) đặt `updatedAt` sau
   `idempotency.record` (flush) ⇒ +2; sửa nháp phiếu giao đổi ghi chú ⇒ bulk `deleteByDeliveryId` (`flushAutomatically`) flush header ⇒ +2.
   Unit (mock) và IT (không đi qua lệnh có đổi ghi chú) đều xanh. IT mới: `update_thatChangesTheNoteToo_*`, `setPrices_movesTheOrderVersionByExactlyOne`.
2. 🔴 **Ca tranh chấp cùng đơn không chứng minh khoá vị trí** — khoá dòng đơn đã tuần tự hoá. Phải có ca **hai đơn** cùng vị trí
   (`concurrentPostsOfTwoOrders_*`); mutation bỏ `lockPositions` chỉ ca đó đỏ.
3. **Thứ tự khoá cố định: dòng đơn → vị trí tồn (item → kho → lô).** Lệnh nào sau này khoá cả hai (hoá đơn không khoá tồn) phải giữ đúng thứ tự.
4. **Guard đọc đơn qua `findOrder` (Optional)** — guard ném `AppException` qua một bean `@Transactional` sẽ đánh dấu rollback-only.
5. **`uncovered` đổi công thức (D8)** ở **cả** detail lẫn `planning-demands`; run đã lưu không đổi.
6. **`deliveredQuantity` từ luôn `null` thành số thật** khi baseline biết; `metricsEvidence.delivered = KNOWN`. Đơn cũ (`V86`) vẫn `null`.
7. **Không** có lớp giá vốn (`D7`): `valuationStatus = PENDING_COSTING`. Liên kết ngược theo phiếu nhập/WO và tổng tiền đơn: `G10d`.

**Nghiệm thu mutation (12, đã revert — 12/12 bị giết):** hạn mức bỏ nháp khác · tồn bỏ phần giữ của chính đơn · ghi sổ lấy tự do trước
(IT) · bỏ khoá vị trí (IT, ca hai đơn) · D8 trừ `delivered` như số hạng thứ tư · bỏ chặn huỷ đơn · nháp không loại trừ chính nó (IT) · bỏ
kiểm baseline · ai cũng sửa nháp (IT) · version sau bulk delete (IT) · bổ sung giá dirty header sau `record` (IT; 🔴 bản mutation đầu chỉ xoá
dòng `setUpdatedAt` và **sống sót** — `pricing_revision` vốn đã làm header dirty; mutation đúng là **dời** dòng đó ra sau flush) · lô `HOLD`
tính vào tự do.

**Nghiệm thu:** `mvn -o clean verify` — **2053 unit + 294 IT**, 0 failures/errors (baseline 2032 + 284). `V87` chạy sạch trên DB dev. Smoke HTTP
thật (`java -jar` dev, cổng 8090) **hai lượt** gộp `G10a`: `170755` (lộ +2) và `171137` trên code cuối — **39/39** (giá + ẩn giá với thủ kho, đơn
cũ, ứng viên, nguồn, nháp, vượt hạn mức, sửa 5→4→5, nguồn giả, ngày tương lai, ghi sổ hai chân, replay, ghi sổ lại/huỷ phiếu đã ghi sổ, nhu
cầu 8 − 5 = 3, huỷ nháp trả hạn mức, huỷ đơn bị chặn, `X-Plant-Id` lệch, danh sách/tìm, liên quan theo lô, lịch sử, lô `HOLD`, thủ kho tạo + ghi
sổ phiếu của mình). Docker Desktop tắt qua đêm giữa lượt mutation ⇒ 10 IT "lỗi" trong 1.4 s là môi trường, không phải kill. Fixture
`GD10-170755`, `GD10-171137` **chưa** ngừng.

**Breaking changes — wire: CÓ, hẹp** (`BE_RESPONSE_GD_10_2026-10-25.md §3`): huỷ đơn còn phiếu giao ⇒ 409; `deliveredQuantity` có số thật;
`uncoveredQuantity` giảm theo lượng đã giao; đơn cũ giao ⇒ `409 SOURCE_UNKNOWN`. Additive: 10 endpoint. **Java positional: có** —
`SalesOrderService`/`SalesOrderDetailAssembler`/`SalesOrderPlanningSourceService` constructor +1 (`DeliveryLookupService`),
`SalesOrderMapper.toDetailResponse` +1 tham số, `SalesOrderMapper.uncovered` +1, overload `SalesOrderLineMetricsEvidence.of(boolean)`,
`InventoryMovementService` +3 method public, `AuditAction` +4.

---

### 0.67 `G10a` – Nền Thương Mại Của Đơn Bán: Giá, Khách Bắt Buộc, Baseline, Quyền (2026-10-25)

Nguồn: `FE_HANDOFF_GD_10_2026-10-15.md` R01, R02, R20, R28, Q4–Q6, Q26. Kế hoạch: `NEXT_PHASE_PLAN.md §17` (D0 tách 4; D3, D4, D6, D9).
Migration **`V86`**. Bất biến **`B238`–`B240`** (`module/sales/CLAUDE.md`). Quyền mới: 10 (`docs/roles-and-permissions.md`). Mã lỗi mới
`PRICE_LOCKED`.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`G10-0` đo trước khi chốt D8:** `held` là phần còn lại của giữ chỗ `ACTIVE`, nên giao từ phần giữ làm `uncovered` **tăng** đúng lượng
   vừa giao; giao từ hàng tự do (không giữ, không WO) thì tồn giảm mà nhu cầu đứng yên. Trừ thẳng `delivered` lại đếm đôi với `qcAccepted`
   (đặt 10, QC 6, WO đóng hụt, giao 6 ⇒ ra 0). User chốt `max(qcAccepted, delivered)` — làm ở `G10b`.
2. 🔴 **Xác nhận đơn đòi khách master** — đường tạo cũ `POST /sales-orders/v1` (E2E, seed demo, adapter FE) nay nhận `customerId` tuỳ chọn;
   không có nó thì đơn chỉ xác nhận được sau khi `PATCH /{id}/draft` gắn khách. `scripts/demo-capstone2/seed.sh` **chưa** gửi `customerId` ⇒
   bước xác nhận đơn của seed sẽ `422` cho tới khi sửa (làm ở `G10-9`).
3. 🔴 **"Không có quyền" là field vắng mặt**: `default-property-inclusion: always` (§0.42) in mọi `null`, nên tiền phải nằm trong một object con
   có `@JsonInclude(NON_NULL)` trên chính record component. Test kiểm **JSON thô**, không kiểm DTO.
4. **Lệch D6 có chủ đích:** một object `commercial` tuỳ chọn thay cho "hai record response" — cùng hiệu ứng trên wire, không nhân đôi DTO.
5. **Lệnh GĐ10 trả `CommercialCommandResult`** (`documentId`, `type`, `version`, `pricingVersion?`) — không trả tiền (FE Q25); `Idempotency-Key`
   **bắt buộc** (khác G6a/G8 tuỳ chọn), replay **trước** version.
6. **Nháp không có lịch sử giá riêng** — giá nháp đổi cùng nháp (id dòng đổi mỗi lần lưu), audit đơn đã ghi; `sales_order_price_events` chỉ
   cho đơn đã xác nhận (FK tới dòng không bao giờ bị xoá).
7. **Smoke HTTP của `G10a` chạy chung với `G10b`** (bản trả lời FE cần ví dụ copy từ lần gọi thật) — chưa chạy ở commit này.

**Nghiệm thu mutation (8, đã revert — 8/8 bị giết):** `PERM_SALES_ORDER_READ` mở giá · bỏ khoá giá đã biết · bỏ `NON_NULL` trên tiền dòng ·
cho xác nhận không khách · bỏ dirty header khi đặt giá · replay sau version · so giá nháp bỏ qua 0 ↔ null · `V86` đánh mọi đơn là không biết (IT).

**Nghiệm thu:** `mvn -o clean verify` — **2032 unit + 284 IT**, 0 failures/errors (baseline 2009 + 283).

**Breaking changes — wire: CÓ, hẹp:** xác nhận đơn không có `customerId` ⇒ `422 OPERATION_NOT_ALLOWED` field `customerId`; đơn đã qua
`DRAFT` trước `V86` có `deliveryBaselineKnown = false` (sẽ không giao/lập hoá đơn được ở `G10b`/`G10c`). Additive: 1 endpoint, 3 field trên
đơn, 1 trên dòng, `unitPrice` tuỳ chọn trên dòng nháp, `customerId` tuỳ chọn trên tạo cũ. **Java positional: có** —
`SalesOrderDetailAssembler` constructor +1, `SalesOrderMapper.toDetailResponse` +1 tham số, `SalesOrderResponse` +3 /
`SalesOrderLineResponse` +1 / `SalesOrderDraftLineRequest` +1 / `SalesOrderCreateRequest` +1 (compat constructor giữ arity cũ).

---

### 0.66 `G9c` – Phiếu Nhập: QC Theo Lượng Nhiều Lượt, Lô Con (2026-10-23)

Nguồn: `FE_HANDOFF_GD_09_2026-10-15.md` R09–R12, Q15–Q21, Q24, Q26, Q29. Kế hoạch: `NEXT_PHASE_PLAN.md §16` (`D1`/`D2`/`D4`/`D10`/`D14`/`D15`). Trả lời FE:
`BE_RESPONSE_GD_09_2026-10-23_v3.md` (phần 3/3 — **GĐ9 đủ ở BE**). Migration **`V85`**. Bất biến **`B234`–`B237`** (`module/workorder/CLAUDE.md`).
**Không** quyền mới, **không** mã lỗi mới.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`isQcDecided()` đổi nghĩa: hết phần chờ**, không còn "`qcResult != null`". `qcResult`/`qcReason`/`qcAt`/`qcBy` chỉ còn là ảnh tóm tắt;
   `qcResult = null` khi `PARTIAL`/`MIXED`. Ai đọc `qcResult` để biết "đã QC chưa" phải chuyển sang `qualityState`/`pendingQcQuantity`.
2. 🔴 **Hàm tính chất lượng là static, nhận danh sách dòng** (`ProductionReceipt.qualityState(status, lines)`…). Trang danh sách nạp dòng theo batch mà không
   điền collection `receipt.lines` — gọi instance method ở đó là một query mỗi hàng.
3. **Cổng đổi trạng thái lô tay** nay là `ProductionReceiptLineRepository.existsPendingQualityForLot` (JPQL, có IT); `existsByLotLotId` của hai repository
   cũ đã xoá (orphan). Lô cha QC xong còn 0 vẫn `HOLD` — đổi trạng thái tay trả `422 NEGATIVE_QUANTITY` vì đổi trạng thái luôn ghi một biến động bằng **toàn
   bộ** tồn dương của lô (quy tắc có từ trước; smoke C12b ban đầu kỳ vọng sai).
4. **Đường cũ `qc-disposition` giữ hành vi cũ khi phiếu chưa ai quyết** (đổi trạng thái cả lô, `quality_dispositions` vẫn ghi) để `scripts/demo-capstone2`,
   `verify.sh` và `ProductionFlowE2EIT` không đổi; nhưng cũng ghi dòng quyết định + tổng ⇒ một sổ duy nhất.
5. **Một dòng mỗi phiếu là giả định của lệnh QC mới** (`singleLine`, như `PATCH` của G9a); phiếu nhiều dòng trước `F5-A` chỉ đi đường cũ.
6. **Không có lớp giá trị** (`D10` hoãn lần thứ tư): `valuationStatus` luôn `PENDING_COSTING`, không field tiền nào.
7. **Mutation runner + JPQL:** các mutation trên bộ lọc phải giữ JPQL hợp lệ (đổi vế điều kiện, không xoá ngoặc) — lần đầu viết C17 suýt "giết" giả.

**Nghiệm thu mutation (17, đã revert — 17/17 bị giết):** cho vượt phần chờ · loại không cần lý do · đạt lô hết hạn · phần loại cũng rời vùng chờ · phần loại
fulfill đơn bán · lô con loại thành `AVAILABLE` · quyết định không touch WO · không dirty phiếu · replay sau luật · đường cũ quyết lại cả lô khi đã quyết một
phần · mã lô con bỏ qua hậu tố đã dùng · mất `PARTIAL` khi có phần loại · tóm tắt ghi `AVAILABLE` cho `MIXED` · cổng lô bỏ phần chờ (IT) · lô con QC loại đổi
tay được · bộ lọc `pendingQc` bỏ trạng thái (IT) · `NOT_STARTED` bỏ kiểm quyết định (IT).

**Nghiệm thu:** `mvn -o clean verify` — **2009 unit + 283 IT**, 0 failures/errors (baseline 1982 + 279). `V85` chạy sạch trên DB dev (6/6 phiếu đã QC ⇒ 6 dòng
quyết định, tổng khớp). Smoke HTTP thật (`java -jar` dev, cổng 8090) lượt **`094029`**: **24/24** — phiếu duyệt ⇒ `NOT_STARTED`, chờ 5, `PENDING_COSTING`; 0/0 ⇒
400 `acceptedQuantity`; loại không lý do ⇒ 400 `APPROVAL_REASON_REQUIRED`; 4+2 trên 5 ⇒ 409 `acceptedQuantity`; version cũ ⇒ 409; số lẻ thứ 7 ⇒ 400; 3/1 ⇒
`PARTIAL` 3/1/1, WO +1, lô con `-A1` 3 `AVAILABLE` + `-R1` 1 `REJECTED` dưới lô gốc, tồn thực tế 5 không đổi, khả dụng +3; replay cùng quyết định, khác body
409; đổi tay lô gốc khi còn chờ ⇒ 409 `LOT_NOT_ELIGIBLE`; đổi tay lô con loại ⇒ 409; đường cũ quyết nốt 1 ⇒ `MIXED`, `-A2`, `qcResult null`; đường cũ lần
nữa ⇒ 409 `STATE_CONFLICT`; lịch sử 2 dòng mới nhất trước; bộ lọc `MIXED`/`hasQcDecision`/`pendingQc`; không lô 3/1 ⇒ khả dụng +3, vùng chờ 4 → 1, không
lô, `qualityBucketId` = dòng phiếu; lô hết hạn (SQL) ⇒ đạt 409, loại 200 `REJECTED`. Lượt `094003` có 23/24: C12b kỳ vọng sai (hệ quả #3). Fixture
`GD9C-094003`, `GD9C-094029` **chưa** ngừng.

**Breaking changes — wire: CÓ, hẹp** (`BE_RESPONSE_GD_09_2026-10-23_v3.md §3`): `qcResult` có thể `null` trên phiếu đã có quyết định (`PARTIAL`/`MIXED`);
`qc-disposition` trên phiếu đã quyết một phần chỉ quyết phần còn chờ (trước: không có trạng thái này); lô con bị QC sản xuất loại không đổi
trạng thái tay được. Additive: 2 endpoint, 3 bộ lọc, 5 field. **Java positional: có** — `ProductionReceiptService` constructor +1
(`ProductionQualityDecisionRepository`), `listByPlant` +3 / `ProductionReceiptRepository.findByPlant` +3 tham số; `ProductionReceiptResponse` +5;
`LotQcOriginLookupService` constructor −1 (bỏ `QualityDispositionRepository`); `WorkOrderCommand` +1, `AuditAction` +1.

---

### 0.65 `G9b` – Phiếu Nhập: Phân Bổ Theo Lượt Báo Sản Lượng + Truy Xuất Nguồn (2026-10-23)

Nguồn: `FE_HANDOFF_GD_09_2026-10-15.md` R05, R13, R22, Q6/Q27/Q29/Q30. Kế hoạch: `NEXT_PHASE_PLAN.md §16` (`D3`). Trả lời FE:
`BE_RESPONSE_GD_09_2026-10-23_v2.md` (phần 2/3). Migration **`V84`**. Bất biến **`B231`–`B233`** (`module/workorder/CLAUDE.md`). **Không** quyền mới,
**không** mã lỗi mới.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Phân bổ chỉ đúng vì mọi lệnh phiếu nhập `touch()` WO (B227).** `ReceiptContributionLedger.allocate` đọc phần đã giữ **không khoá**; thứ chặn hai
   lần phân bổ đồng thời là `@Version` của WO. Một lệnh phiếu nhập mới mà quên `touch()` sẽ mở đúng cửa cho hai phiếu cùng giữ một lượt báo.
2. **Nguồn = lượt báo của công đoạn cuối** (B219), lượt cũ nhất trước (`actualEndedAt`, rồi id dạng chuỗi). Lệnh không có công đoạn hoặc
   `operation_tracking = false` ⇒ mọi lượt — đúng thứ `actualGoodQuantity` đã đếm.
3. **Phiếu cũ (không dòng) bị tính trừ vào lượt cũ nhất trước** (`ProductionReceiptRepository.sumLiveWithoutContributions`) — bỏ bước này thì phiếu mới
   lấy trùng phần phiếu cũ đã nhận. Sửa một nháp cũ ⇒ nó được phân bổ và có dòng từ đó.
4. **Không kiểm lại lúc gửi/duyệt** (lệch §16 có chủ đích, B232). Nếu sau này có đường **đảo** lượt báo sản lượng, bất biến này hết đúng — phải thêm
   kiểm lại khi đó.
5. **Response của lệnh vừa ghi dùng đúng danh sách vừa lưu**, không đọc lại (`toResponse(saved, contributions)`): trong unit test phiếu mới chưa có id nên
   đọc lại ném NPE ở `List.of(null)`; trong production nó dựa vào auto-flush. Hệ quả: lượng trên response vừa ghi có scale như đã tính (`2`), đọc lại thì
   `2.000000` (§0.50 #8).
6. **`deleteByReceiptId` là bulk JPQL** (`@Modifying(flushAutomatically = true)`) — chạy ngay nên dòng mới không đụng `UNIQUE` với dòng cũ (khác bẫy
   orphan-removal của §0.40).
7. 🔴 **Bẫy công cụ lặp lại:** (a) sửa `src/main/java/**/CLAUDE.md` lúc `mvn` đang chạy cũng kích Java LS ⇒ `repackage` "Unable to find main class" sau
   khi unit đã xanh — không chỉ file `.java`; (b) `sed` sửa test xong mà không `clean` ⇒ failsafe "failed to discover tests"; (c) fixture IT dùng trạng thái
   `CANCELLED` phải có `cancelled_at` (CHECK `V83`); (d) Docker Desktop khởi động lại qua đêm ⇒ container dev `Exited (255)`, phải `docker compose up -d`
   rồi đợi `healthy`.

**Nghiệm thu mutation (14, đã revert — 14/14 bị giết):** lượt mới nhất trước · mọi công đoạn là nguồn · bỏ phần phiếu khác đã giữ · không tính phiếu cũ ·
lệch sổ thì phân bổ thiếu im lặng · sửa nháp giữ phần của chính nó · sửa nháp không xoá dòng cũ · không ghi dòng · phiếu cũ báo là đã rõ nguồn (**sống sót
lần đầu ⇒ thêm `get_ofALegacyReceipt_*`**) · nguồn luôn `WORK_ORDER_LEVEL` · nguồn bỏ kiểm quyền QC-only · JPQL tính cả phiếu bị từ chối (IT) · JPQL không
loại trừ phiếu đang sửa (IT) · JPQL phiếu cũ tính cả phiếu có dòng (IT).

**Nghiệm thu:** `mvn -o clean verify` — **1982 unit + 279 IT**, 0 failures/errors (baseline 1968 + 274). `V84` chạy sạch trên DB dev. Smoke HTTP thật
(`java -jar` dev, cổng 8090) lượt **`084754`**: **16/16** — lệnh hai công đoạn, Cut good 10, Weld good 3 rồi 5; tạo 4 ⇒ `[Weld3 3, Weld5 1]` (không lấy
Cut); tạo 2 ⇒ `[Weld5 2]`; sửa phiếu đầu xuống 3 ⇒ `[Weld3 3]`, phiếu kia không đổi; tạo 4 khi còn 3 ⇒ 409 field `quantity`; tạo 3 ⇒ `[Weld5 3]`; huỷ giữ
dòng làm lịch sử; xoá dòng phiếu đầu bằng SQL (giả lập phiếu cũ) ⇒ `outputEvidenceKnown false`, phiếu mới 2 ⇒ `[Weld5 2]` (phần cũ tính vào Weld3); duyệt
giữ dòng; `…/source` ⇒ `WORK_ORDER_LEVEL` + vật tư cấp 20; nguồn phiếu cũ ⇒ unknown; id lạ ⇒ 404; tồn thành phẩm chỉ tăng theo phiếu đã duyệt. Lượt
`084742` dừng ở lỗi in ký tự `⇒` của chính script (console cp1252) sau 10 PASS. Fixture `GD9B-084742`, `GD9B-084754` **chưa** ngừng.

**Breaking changes — wire: KHÔNG** (thuần additive: 1 endpoint, 3 field trên phiếu nhập). **Java positional: có** — `ProductionReceiptService` constructor +3
(`ProductionReceiptContributionRepository`, `ReceiptContributionLedger`, `MaterialSourceLedger`); `ProductionReceiptResponse` +3; mapper
`toResponse(…, contributions)` +1 tham số.

---

### 0.64 `G9a` – Phiếu Nhập Thành Phẩm: Vòng Đời, Version, Hạn Mức Nhập, Đọc (2026-10-21)

Nguồn: `FE_HANDOFF_GD_09_2026-10-15.md` R01–R08, R17–R21, Q1–Q5/Q7–Q14/Q23/Q25. Kế hoạch: `NEXT_PHASE_PLAN.md §16` (D0 = tách 3 làm liền, user chốt
2026-10-20). Trả lời FE: `BE_RESPONSE_GD_09_2026-10-21.md` (phần 1/3). Migration **`V83`**. Bất biến **`B226`–`B230`** (`module/workorder/CLAUDE.md`).
Quyền mới `PERM_PRODUCTION_RECEIPT_READ`, `PERM_PRODUCTION_QC_READ` (ADMIN + MANAGER, `docs/roles-and-permissions.md`). **Không** mã lỗi mới.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **`A ≤ G ≤ P` là CHECK của DB** (`chk_work_orders_completed_quantity` V14, `chk_work_orders_actual_good` +
   `chk_work_orders_completed_within_good` V32). Kế hoạch §16 ghi D12 là "chặn mới về số liệu" — sai: `C = min(P, G)` luôn bằng `G`. Bản nháp có cảnh
   báo `GOOD_EXCEEDS_PLAN`/`APPROVED_EXCEEDS_CAP` đã gỡ (code cho tình huống không thể xảy ra, §11.5) — phát hiện vì IT dựng fixture `G > P` bị CHECK
   từ chối. Đọc CHECK trước khi viết nhánh "bất thường".
2. 🔴 **Bẫy `B112` lần thứ ba:** sửa nháp chỉ đổi lượng (ở dòng — collection inverse) không làm phiếu dirty ⇒ `version` phiếu đứng yên. Unit (mock) và
   IT (không đi qua service) đều xanh; smoke thấy submit nhận `expectedVersion: 1` sau **hai** lần sửa. Sửa: `receipt.setUpdatedAt(now())` trong
   `update`. Mọi lệnh chỉ đổi dòng con của một aggregate có `version` trên response phải làm header dirty.
3. 🔴 **Bẫy µs lần thứ tư** (sau §0.51 #3, §0.52 #3, §0.55 #7a): `postedAt` (`@Builder.Default Instant.now()`) và `approvedAt` trả 100 ns lúc ghi, µs
   khi đọc lại. Mọi mốc vòng đời phiếu nhập nay qua `now()` cắt µs; smoke kiểm "giá trị lúc ghi = giá trị đọc lại".
4. **Mọi lệnh phiếu nhập bump version WO đúng +1** (FE Q12): tạo/sửa/gửi/rút/huỷ/duyệt/từ chối/QC đều `touch()` WO **trước** flush đầu tiên; duyệt đặt
   `completedQuantity` **trước** vòng biến động (khuôn G8b). E2E canh +1 sau tạo và sau duyệt.
5. **Kho nhập = kho đầu ra của WO** (R02). Đường nội bộ `WorkOrderService.completeOutput` gửi đúng kho đó; script `demo-capstone2` cũng vậy.
6. **Mã lô trùng kiểm không phân biệt hoa thường ở service** (B205), nhưng `uk_inventory_lots_item_code` (V8) phân biệt hoa thường — bảo đảm cuối ở DB
   chỉ chặn trùng đúng chữ. Nháp không giữ chỗ mã lô (D11).
7. **Đọc chỉ bằng quyền QC ⇒ chỉ `APPROVED`**: chi tiết khác ⇒ 403, danh sách trạng thái khác ⇒ trang rỗng (không 403, để màn QC dùng chung list).
8. **Script seed `demo-capstone2` đã cập nhật** (`rcpt_versions`, `plant_today` trong `scripts/demo/lib/common.sh`) — **chưa chạy lại** trên DB trắng.
9. **`ObjectMapper` trần trong unit test không băm được `LocalDate`** (bẫy §0.52 #7 lần hai): `IdempotencySupport` trong test dùng
   `new ObjectMapper().findAndRegisterModules()`.

**Nghiệm thu mutation (15, đã revert — 15/15 bị giết):** sửa không trả lại phần của chính nó · duyệt bỏ kiểm trần · `completedQuantity` sau biến động ·
ai cũng thao tác được nháp · hạn quá từ lúc lưu là 400 thay 409 · mã lô so phân biệt hoa thường · quyền QC đọc mọi phiếu · replay sau luật trạng thái ·
`allowedActions` bỏ người tạo · hạn lô theo UTC · tạo phiếu không touch WO (E2E) · ứng viên bỏ lọc `workOrderId` (IT) · `receivedTo` loại trừ (IT) · tìm
bỏ số lô của nháp (IT) · sửa chỉ lượng không dirty phiếu. 🔴 Runner mutation phải chuẩn hoá CRLF — 7 anchor nhiều dòng lần đầu báo "lệch anchor".

**Nghiệm thu:** `mvn -o clean verify` — **1968 unit + 274 IT**, 0 failures/errors (baseline worktree sạch 1913 + 269). `V83` chạy sạch trên DB dev. Smoke
HTTP thật (`java -jar` dev, cổng 8090) **ba lượt**: `002951` (lộ µs), `004159` (lộ version nháp), `004655` trên code cuối — **45/45**: ứng viên G9/C9/A0/
D0/W9/U9; thiếu version / ngày mai / kho khác / số lẻ thứ 7 / hạn = ngày nhận ⇒ đúng field + mã; tạo 5 ⇒ v0, WO +1, U 4; replay + khác body 409; tạo thêm
5 ⇒ 409 field `quantity`; sửa 4 rồi 5 ⇒ version phiếu +1 mỗi lần; gửi không body 400; rút lý do 4 ký tự 400; rút ⇒ DRAFT giữ phần; duyệt version cũ 409;
duyệt ⇒ WO +1, lô HOLD, tồn +5, hạn lô 00:00 giờ VN; replay duyệt không biến động thứ hai; khác body 409; mã lô khác hoa thường 409; từ chối/huỷ trả phần;
huỷ lần hai 409; người khác có MANAGE huỷ ⇒ 403; bộ lọc tìm/ngày/trạng thái/kho; `X-Plant-Id` lệch 409; lịch sử; QC nguyên phiếu cũ vẫn chạy + WO +1;
user chỉ có QC_READ: phiếu duyệt 200, phiếu huỷ 403, list DRAFT rỗng, ứng viên 403. Fixture `GD9A-002951`, `GD9A-004159`, `GD9A-004655` (kèm user
`g9a.clerk.*`/`g9a.qcread.*`, role `CLERK-*`/`QCREAD-*`, scope `SC-*`) **chưa** ngừng.

**Breaking changes — wire: CÓ** (`BE_RESPONSE_GD_09_2026-10-21.md §3`): tạo bắt buộc `expectedWorkOrderVersion` + `receivedOn`; kho khác kho đầu ra ⇒ 422;
số lẻ thứ 7 ⇒ 400; mã lô trùng khác hoa thường ⇒ 409; submit/approve bắt buộc body version, reject thêm version + lý do 5–2000; submit chỉ người tạo;
duyệt kiểm lại kho/hạn/trần; mọi lệnh phiếu nhập bump version WO. Additive: 5 endpoint, 5 bộ lọc, 23 field phiếu, 10 field ứng viên, 2 quyền. **Java
positional: có** — `ProductionReceiptService` constructor +2 (`WorkOrderIdempotency`, `PermissionGuard`; field `lotRepository` tự-wire đã gỡ), `submit`/
`approve`/`reject` thêm body + key, `listByPlant` +5 / `listCandidates` +2 tham số; `ProductionReceiptRejectRequest` **xoá**, thay bằng
`ProductionReceiptReasonRequest`; `ProductionReceiptPostRequest` +3 (compat constructor 7 tham số); `ProductionReceiptResponse` +23 /
`ProductionReceiptCandidateResponse` +10; `ProductionReceipt.submit`/`reject` +1 tham số (actor); mapper `toResponse(receipt, lines, usernames, quota,
actions)` thay hai overload cũ; `WorkOrderCommand` +6, `AuditAction` +3.

---

### 0.63 `G8d` – Dở Dang (WIP) + Hoàn Vật Tư Về Đúng Nguồn (2026-10-20)

Nguồn: `FE_HANDOFF_GD_08_2026-10-14.md` R15–R17 (+ phần trần của R13), Q1/Q2/Q11/Q13/Q19/Q22/Q23/Q30/Q34/Q36, A29–A33/A39. Kế hoạch:
`NEXT_PHASE_PLAN.md §15` (`15.3d`). Trả lời FE: `BE_RESPONSE_GD_08_2026-10-20_v4.md` (phần 4/4 — **GĐ8 đủ cả 4 phần ở BE**). Migration **`V82`**.
Bất biến **`B222`–`B225`** (`module/workorder/CLAUDE.md`). Quyền mới `PERM_WIP_READ`, `PERM_MATERIAL_RETURN_READ`, `PERM_MATERIAL_RETURN_MANAGE`
(ADMIN + MANAGER, `docs/roles-and-permissions.md`). **Không** mã lỗi mới.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Sổ vật tư theo dòng cấp không có cột đếm (`B222`).** `MaterialSourceLedger` dẫn xuất `chưa dùng = cấp − Σ consumption − Σ hoàn` (3 query/lệnh).
   Trần vật tư dùng của báo sản lượng (G8c) nay cũng trừ phần đã hoàn — hai trần phải đọc **cùng** ba nguồn, đừng tính riêng ở một chỗ.
2. **Hoàn là `RECEIVE` thường** về đúng kho + lô của dòng cấp, `referenceType = "MATERIAL_RETURN"`, child key `MR:<key>:L<n>`, lô `AVAILABLE`. Không
   `MovementType` mới, không cột liên kết trên `stock_movements` — liên kết nằm ở `material_return_lines.stock_movement_id`.
3. **Hoàn không đổi `issuedQuantity` và không trả giữ tồn** (FE Q11/R17). Hệ quả đã biết, chưa sửa: variance/cost accumulator vẫn tính vật tư đã hoàn
   là đã dùng.
4. **Kiểm nguồn lúc hoàn** — lô không `AVAILABLE`/hết hạn ⇒ `409 LOT_NOT_ELIGIBLE` (không đi vòng QC); kho/vật tư ngừng ⇒ `422 RESOURCE_INACTIVE`;
   sê-ri ⇒ `422 OPERATION_NOT_ALLOWED`. Ứng viên hoàn trả `returnableQuantity 0` + `ineligibleReason` thay vì giấu dòng.
5. **WIP là read model sống** (D8): không bảng chụp, lệnh `operation_tracking = false` trả `null` cho mọi số dẫn xuất từ sổ G8c. `section` chỉ thu hẹp
   phần trả về; `HISTORY` không hỗ trợ (400).
6. **Mọi lệnh hoàn theo khuôn G8**: replay key → version → luật → `touch()` → ghi → `flushTouched` (+1 đúng một lần, E2E canh).
7. **Mutation xoá tham số bind khỏi JPQL "giết" giả** (query không khởi động) — lần thứ hai sau §0.55 #7c; dùng mutation ngữ nghĩa (`or 1 = 1`).
8. **Script seed `demo-capstone2`** chưa có bước hoàn vật tư — chưa chạy lại.

**Nghiệm thu mutation (27, đã revert — 27/27 bị giết; D23/D25 viết lại ngữ nghĩa sau lần "giết" giả).**

**Nghiệm thu:** `mvn -o clean verify` — **1913 unit / 203 class + 269 IT / 38 class**, 0 failures/errors (baseline 1871 + 263). `V82` chạy sạch trên DB dev.
Smoke HTTP thật (`java -jar` dev, cổng 8090) lượt **`162845`**: **33/33** ngay lần đầu — vượt trần 409; 0.5 + 0.5 cùng dòng ⇒ một dòng 1, tồn 60 → 61,
version +1, `issuedQuantity` vẫn 20; replay + khác payload 409; dùng sau khi hoàn 409 field `consumption[0].quantity`; WIP đủ 4 phần; hoàn sau
`COMPLETED`; `X-Plant-Id` lệch 409; lệnh `RELEASED` 409; dòng lệnh khác 422; kho ngừng 422; lô `HOLD` ⇒ `returnableQuantity 0` + 409, về `AVAILABLE` ⇒
hoàn vào đúng lô 5 → 7; lệnh cũ `null` + `409 PRODUCTION_INPUT_UNKNOWN`; người chỉ có READ ⇒ đọc 200, tạo/ứng viên 403. Fixture `GD8D-162845` (kèm user
`g8d.reader.*`, role `WIP-READER-*`, scope `SC-*`) **chưa** ngừng.

**Breaking changes — wire: CÓ, hẹp** (`BE_RESPONSE_GD_08_2026-10-20_v4.md §3`): `consumption[]` vào phần đã hoàn ⇒ `409 PLANNED_QUANTITY_EXCEEDED`.
Additive: 6 endpoint, 3 quyền. **Java positional: có** — `ProductionExecutionService` constructor +1 (`MaterialReturnLineRepository`),
`WorkOrderPermissionGuard` constructor +1 (`MaterialReturnRepository`), `WipTransactionType` +1, `AuditAction` +1.

---

### 0.62 `G8c` – Công Đoạn: Đầu Vào/Chuyển Giao, Xử Lý Lại, Vật Tư Thực Dùng, Hoàn Thành Có Hụt (2026-10-20)

Nguồn: `FE_HANDOFF_GD_08_2026-10-14.md` R10–R14, Q4/Q14–Q18/Q22/Q23/Q31, A18–A28. Kế hoạch: `NEXT_PHASE_PLAN.md §15` (`15.3c`). Trả lời FE:
`BE_RESPONSE_GD_08_2026-10-20_v3.md` (phần 3/4). Migration **`V81`**. Bất biến **`B217`–`B221`** (`module/workorder/CLAUDE.md`). **Không** quyền mới; mã lỗi
mới `MATERIAL_ISSUE_REQUIRED`, `PRODUCTION_INPUT_UNKNOWN`.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Công đoạn không có cột đếm (`B217`).** `OperationLedger` dẫn xuất mọi số từ `work_order_operation_inputs` + `production_executions` (4 query/lệnh);
   chỉ `completed_at` được lưu. Lệch khuyến nghị D1(a) có chủ đích — cột đếm là nguồn thứ hai sẽ trôi khỏi sổ. Ai thêm cột "cho nhanh" phải chứng minh
   bằng IT rằng nó luôn bằng sổ.
2. 🔴 **`actualGoodQuantity` đổi nghĩa (`B219`)**: chỉ good công đoạn cuối; `WorkOrder.reportProduction(…, countsAsFinalGood, …)`. Nhân công/chi phí chung
   cũng chỉ hấp thụ trên good cuối. `availableToReceipt`/candidates đi theo. Phế/làm lại vẫn tổng mọi công đoạn.
3. **Hai nhóm (`B218`)**: lượt mới so `input − freshProcessed`; lượt xử lý lại so `rework − đã xử lý lại` của **đúng** lượt nguồn. JPQL
   `sumByOperation` tách bằng `case when sourceExecutionId is null` — `OperationLedgerRepositoryIT` canh (mutation gộp hai nhánh ⇒ đỏ).
4. **Mọi lệnh bump version đúng một** — báo sản lượng đổi trạng thái WO nên theo khuôn `B212`: `reportProduction` + `touch()` trước mọi ghi, cuối
   `flushTouched`; E2E canh +1.
5. **`Map.of().getOrDefault(null)` ném NPE** (lần thứ hai sau §0.43): mapper execution phải kiểm id null trước khi tra map.
6. **Bẫy công cụ lặp lại:** VS Code Java LS ghi đè `target/test-classes` giữa hai lượt `mvn` ⇒ "Unresolved compilation problem" — chạy lại bằng `clean`;
   heredoc bash chứa `\"`/`$(...)` lồng trong python vẫn vỡ — dùng Edit hoặc file script; chạy mutation nền với `timeout` có thể bị giết giữa chừng để lại
   file đột biến — chạy nền **không** timeout và luôn `find src -name "*.mutbak"`.
7. **Script seed `demo-capstone2`** đi qua từng công đoạn (công đoạn trước công đoạn cuối làm đủ kế hoạch, công đoạn cuối nhận số lượng) — **chưa chạy lại**.

**Nghiệm thu mutation (26, đã revert — 26/26 bị giết):** cap lượt mới so đầu vào thay phần còn lại · cap xử lý lại bỏ phần đã dùng · xử lý lại khác công
đoạn · vật tư không cộng mục trùng · vật tư bỏ phần đã dùng · vật tư phiếu chờ · bỏ chặn công đoạn trước · good mọi công đoạn thành good cuối · lệnh cũ nhận
vật tư · bỏ dung sai 60 s · bỏ version · touch sau flush (E2E) · đầu vào không cần phiếu cấp · cap đầu vào bỏ phần đã nhận · cap chuyển giao theo good thay
phần còn chuyển · kết thúc khi còn làm lại · kết thúc khi chưa chuyển hết · hụt không cần APPROVE · hoàn thành khi công đoạn chưa kết thúc · replay sau
version · phần chờ không trừ xử lý lại (**sống sót lần đầu ⇒ thêm test**) · lệnh cũ báo đầu vào 0 thay `null` · JPQL gộp lượt mới/xử lý lại (IT) · JPQL
đầu vào mọi lệnh (IT) · hụt = kế hoạch · bỏ luật A25.

**Nghiệm thu:** `mvn -o clean verify` — **1871 unit / 199 class + 263 IT / 37 class**, 0 failures/errors (baseline 1816 + 258). `V81` chạy sạch trên DB dev.
Smoke HTTP thật (`java -jar` dev, cổng 8090) lượt **`105705`** trên code cuối: **32/32** — đầu vào trước khi cấp `409 MATERIAL_ISSUE_REQUIRED`; đầu vào 11/10
`409` field `quantity`; replay + khác payload; công đoạn sau trước khi kết thúc `409 OPERATION_SEQUENCE_CONFLICT`; báo thiếu version / thiếu công đoạn
`400`; 8+1+2 trên 10 `409`; lượt 8/1/1 + vật tư 16+2 ⇒ một hàng 18, good cuối 0, tồn kho không đổi, version +1; vật tư 3 khi còn 2 `409`; xử lý lại 2 khi
chờ 1 `409`; kết thúc khi còn làm lại `409`; giờ kết thúc +5 phút `400`, +30 giây nhận; kết thúc `Cut` good 9; chuyển 10/9 `409`, 9 nhận; good cuối 9;
hoàn thành khi `Weld` mở `409`, không lý do `400`, lý do 3 ký tự `400`, đúng ⇒ `COMPLETED` hụt 1; A25 7/10 `409`; phế toàn bộ ⇒ công đoạn sau kết thúc
với 0; quản lý thiếu APPROVE `403`; lệnh cũ `null` + `PRODUCTION_INPUT_UNKNOWN` + báo sản lượng luật cũ. Fixture `GD8C-104926`, `GD8C-104936`,
`GD8C-104949`, `GD8C-105001`, `GD8C-105705` (kèm user `g8c.mgr.*`, role `WO-MANAGER-*`, scope `SC-*`) **chưa** ngừng.

**Breaking changes — wire: CÓ** (`BE_RESPONSE_GD_08_2026-10-20_v3.md §3`): báo sản lượng bắt buộc `expectedWorkOrderVersion`, lệnh có quy trình bắt buộc
`workOrderOperationId` + đầu vào trước; công đoạn sau trước khi công đoạn trước kết thúc ⇒ 409; `actualEndedAt` > now + 60 s ⇒ 400; 7 chữ số lẻ ⇒ 400;
`actualGoodQuantity` của lệnh nhiều công đoạn = good công đoạn cuối. Additive: 4 endpoint, 7 field execution, 6 field lệnh. **Java positional: có** —
`ProductionExecutionService` constructor +3 (`OperationLedger`, `ProductionExecutionConsumptionRepository`, `MaterialIssueLineRepository`);
`ProductionExecutionPostRequest` +3 (compat constructor 7 tham số); `ProductionExecutionResponse` +7; `WorkOrderResponse` +6 (hai compat constructor);
`ManufacturingExecutionMapper.toResponse(execution, …)` +2 tham số; `WorkOrder.reportProduction` overload 5 tham số; `KeyedQuantityProjection.getRefId`.

---

### 0.61 `G8b` – Cấp Vật Tư: Phiếu Bổ Sung, Kiểm Lại Lúc Duyệt, Huỷ Của Người Tạo (2026-10-19)

Nguồn: `FE_HANDOFF_GD_08_2026-10-14.md` R07–R09, Q1–Q3/Q8–Q11/Q33. Kế hoạch: `NEXT_PHASE_PLAN.md §15` (`15.3b`). Trả lời FE:
`BE_RESPONSE_GD_08_2026-10-19_v2.md` (phần 2/4). Migration **`V80`**. Bất biến **`B212`–`B216`** (`module/workorder/CLAUDE.md`). **Không** quyền mới,
**không** mã lỗi mới.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Một lệnh đổi chính WO (không chỉ dòng con) phải đổi xong và `touch()` TRƯỚC mọi flush, cuối chỉ flush.** Phiếu cấp đầu tiên chuyển
   `RELEASED → IN_PROGRESS`; `saveAndFlush(issue)` flush luôn WO dirty (+1), rồi `touchAndFlush` làm dirty lần nữa (+1) ⇒ **+2**. Unit test mock
   repository, IT không đi qua service ⇒ cả hai xanh; chỉ smoke HTTP thấy (lượt `220136`: 3 → 5). Sửa: `markInProgress()` + `touch()` trước vòng lặp,
   cuối `WorkOrderExecutionSupport.flushTouched` (không touch lại) — ở **cả** `postNew` lẫn `approve`. Guard: `ProductionFlowE2EIT` (phiếu phẳng đầu
   tiên ⇒ đúng +1), mutation đưa về `touchAndFlush` ⇒ đỏ. Bài học `R7` lần thứ mười hai. 🔴 Bất kỳ lệnh G8c/G8d nào đổi trạng thái WO (báo sản lượng,
   `complete-production`) cũng phải theo khuôn này — `touchAndFlush` chỉ đúng khi lệnh **không** tự làm WO dirty trước đó.
2. **Vắng `supplemental` = hành vi cũ** (vượt định mức ⇒ phiếu chờ) để client cũ không vỡ; `false` tường minh mới chặn `409`. Đường phẳng
   `POST /material-issues` và `component-issues` vẫn không đòi version (`postInternal(…, false)`).
3. **Đọc phiếu mở cho `MANAGE`/`APPROVE`/`OVERRIDE`** (`hasAnyWorkOrderAccess`, `hasMaterialIssueAccess`) — nới quyền đọc có chủ đích (Q2). Huỷ
   phiếu chỉ người tạo (`created_by`), kể cả ADMIN cũng bị 403 — quản lý dùng `reject`.
4. **Kiểm tồn sớm không giữ chỗ** — hai phiếu chờ cùng nguồn có thể cùng qua lúc tạo; duyệt kiểm lại trong transaction ghi. Lô chỉ nêu bằng mã bị bỏ
   qua ở kiểm sớm.
5. **Mutation B18 lần đầu "sống sót" là đúng:** gỡ `cast` ở vế `is null` vô hại (Postgres suy text cho `$1 IS NULL`); lỗi `lower(bytea)` thật nằm ở
   vế `concat` — mutation gỡ ở đó ⇒ 7 case IT lỗi. Khi viết mutation cho bẫy `§0.24`, nhắm vào `concat`, không vào `is null`.
6. **`ReasonRules.lengthChecked`** là quy tắc lý do chung (5–2000 sau trim) — `WorkOrderService.cancel` đã dùng; G8c/G8d dùng lại.

**Nghiệm thu mutation (20, đã revert — 20/20 bị giết):** bỏ kiểm version phiếu nhiều dòng · phiếu thường tự thành phiếu chờ · xét vượt từng dòng · cho
phiếu bổ sung nêu reservation · cho phiếu thường lấy kho khác · bỏ ràng buộc `REWORK` · bỏ kiểm tồn sớm · kiểm tồn không cộng dòng cùng nguồn · cho tự
duyệt · bỏ so version phiếu · cho người khác huỷ · gửi lại sau version · bỏ sàn 5 ký tự · `extraRequested` trên phiếu đã quyết · gợi ý bỏ "còn cần" · lô
hết hạn thành đạt · guard phiếu bỏ tra lệnh · bỏ `cast` ở `concat` (IT) · `requestedTo` thành `<=` (IT) · tăng version hai lần (IT E2E). 🔴 Hai lần đầu
sống sót (cộng dòng cùng nguồn; gửi lại lệnh duyệt sau khi phiếu đã post) — đã thêm test rồi chạy lại.

**Nghiệm thu:** `mvn -o clean verify` — **1816 unit + 258 IT**, 0 failures/errors (baseline 1785 + 256). `V80` chạy sạch trên DB dev. Smoke HTTP thật
(`java -jar` dev, cổng 8090) **hai lượt**: `220136` (lộ +2 version) và `220914` trên code cuối — **32/32**: ứng viên `NORMAL` (gợi ý 20) /
`SUPPLEMENTAL` (RAW 10, RAW2 50) / `mode` sai 400; thiếu version 400; phiếu thường 3 → 4; kho khác 422; `supplemental:false` vượt 409; nguồn không đủ
(40 + 20 trên 50) 409 theo dòng; phiếu bổ sung 7 + 7 ⇒ cả phiếu chờ, tồn không đổi; chi tiết 20/8/14/2/50; ADMIN huỷ phiếu người khác 403; lý do 3 ký tự
400; version phiếu sai 409; người tạo huỷ ⇒ `CANCELLED` + replay; tồn hụt sau khi tạo ⇒ duyệt 409, phiếu vẫn chờ; từ chối không lý do 400 / có lý do
200; tự duyệt thiếu `OVERRIDE` 403; duyệt đúng ⇒ RAW2 50 → 36, gross 8 → 22; tìm theo mã; khoảng ngày; `X-Plant-Id` lệch 409; huỷ WO lý do 3 ký tự 400;
duyệt phiếu chờ đầu tiên của WO `RELEASED` 4 → 5 + `IN_PROGRESS`. Fixture `GD8B-220117`, `GD8B-220128`, `GD8B-220136`, `GD8B-220914` (kèm user
`g8b.op.*`/`g8b.apr.*`, role `ISSUE-APPROVER-*`, scope `SC-*`) **chưa** ngừng.

**Breaking changes — wire: CÓ** (`BE_RESPONSE_GD_08_2026-10-19_v2.md §3`): phiếu nhiều dòng bắt buộc `expectedWorkOrderVersion`; duyệt/từ chối bắt
buộc hai version; tự duyệt thiếu `OVERRIDE` ⇒ 403; lý do 1–4 ký tự (từ chối, huỷ WO) ⇒ 400; phiếu thường không reservation từ kho khác ⇒ 422; phiếu chờ
nguồn không đủ ⇒ 409; `supplemental:false` vượt định mức ⇒ 409; hai dòng cùng vật tư cộng lại vượt ⇒ cả phiếu chờ (trước: dòng hai 409); 7 chữ số lẻ /
hơn 500 dòng ⇒ 400; đọc phiếu nới cho `APPROVE`/`OVERRIDE`. Additive: 3 endpoint, 5 field phiếu, `search` + khoảng ngày. **Java positional: có** —
`MaterialIssueService` constructor +1 (`WorkOrderIdempotency`), `approve`/`reject` đổi kiểu body + thêm key, `listByPlant` +3 tham số;
`MaterialIssuePostRequest` +2 (compat constructor 2 tham số), `MaterialIssueResponse` +5 (compat constructor); `MaterialIssueRepository.findByPlant`
+3; `MaterialIssueDecisionRequest` **xoá**, thay bằng `MaterialIssueVersionedDecisionRequest`; `WorkOrderPermissionGuard` +2 method.

---

### 0.60 `G8a` – Nháp Lệnh SX: Kho Từng Dòng, Version Bắt Buộc, Lịch Đã Chốt, Trả Quota Khi Huỷ (2026-10-19)

Nguồn: `FE_HANDOFF_GD_08_2026-10-14.md` (P1). Kế hoạch: `NEXT_PHASE_PLAN.md §15` (D0 = tách 4 phần làm liền; D1–D12 theo khuyến nghị; D11 hoãn).
Trả lời FE: `BE_RESPONSE_GD_08_2026-10-19.md` (v1, phần 1/4). Migration **`V79`**. Bất biến **`B207`–`B211`** (`module/workorder/CLAUDE.md`).
**Không** quyền mới, **không** mã lỗi mới.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Mọi lệnh WO mới phải theo khuôn:** replay `Idempotency-Key` (tuỳ chọn) → load → `WorkOrderVersions.ensure` → kiểm trạng thái → `record` key →
   ghi → `touch()` + `saveAndFlush`. Replay **trước** version (lần đầu đã bump; mutation đảo thứ tự ⇒ test đỏ). `touch()` là thứ duy nhất làm version
   tăng khi lệnh chỉ đổi dòng con (reservation, dòng vật tư, công đoạn) — `WorkOrderRepositoryIT.childOnlyChange_*` chứng minh trên Postgres thật.
2. 🔴 **`WorkOrderResponse` có `version` ⇒ mọi đường ghi trả WO phải flush trước khi map** (bẫy §0.39 lần thứ tư). `issueComponent`/`completeOutput`
   gọi `workOrderRepository.flush()` trước `toResponse`; `close` sang `saveAndFlush`.
3. **`WorkOrderScheduler` là công thức lịch duy nhất** — release và xem trước cấp lệnh. Đừng "tiện tay" chia `capacityUnits` (đó là preview nhà máy
   G5, §0.53 #6). Fingerprint lịch chỉ hỏi module shift khi có lịch thật (test continuous-time `verifyNoInteractions` giữ điều đó).
4. **Hướng phụ thuộc mới `workorder → planning`** (`WorkOrderPlanningLinkService`, không `@PreAuthorize`). Không vòng bean (planning service chỉ
   repository); vòng gói đã có tiền lệ `sales ↔ workorder`. Release ledger dùng chung `PurchaseOrderPlanningLinkService.releaseTarget`.
5. **Script seed `demo-capstone2` đã cập nhật** (`wo_cmd`/`wo_version` trong `scripts/demo/lib/common.sh`) — **chưa chạy lại** trên DB trắng. Seeder
   `VIETBIKE` vốn đã lệch URL (§0.46 #1), không sửa. `scripts/seed-work-order-test-fixture.sh` dùng bố cục URL cũ, không sửa.
6. **Hai điều smoke thấy, có từ trước, không sửa:** response lệnh *tạo* in `requiredQuantity` chưa làm tròn (`20.000000000000`, đọc lại `20.000000`);
   `createdAt`/`releasedAt` của lệnh vừa ghi in 100 ns (§0.52 #3). Ghi rõ cho FE ở v1 §6.
7. 🔴 **Baseline worktree sạch đỏ 1 IT** — `FlywayMigrationIT.migrate_v78_*` (G7c) đọc `pg_constraint` **không lọc schema**, mà test khác dựng schema
   phụ dừng ở V76/V77 ⇒ lấy nhầm bản cũ tuỳ thứ tự chạy. G7c ghi "0 failures" vì thứ tự lần đó khác. Sửa bằng `connamespace = 'public'::regnamespace`
   (cùng ở case G4 `chk_stock_movements_type`). Mọi truy vấn catalog mới trong `FlywayMigrationIT` phải lọc schema.

**Nghiệm thu mutation (16, đã revert — 16/16 bị giết):** bỏ `touch` · tha version lệch · giữ tồn tự động bỏ kho dòng · giữ tay bỏ kiểm kho dòng ·
huỷ không trả quota · huỷ bỏ chặn phiếu chờ · khoảng ngày lệch một ngày · bỏ khoá nguồn · release không ghi calendar revision · đổi kho bỏ qua giữ
tồn kho khác · replay sau version · release-reservations trên lệnh huỷ · xem trước tính tải của chính lệnh · bộ xếp lịch bỏ lịch · `<` → `<=` cuối
khoảng (IT) · bỏ cast vế `is null` (IT). Hai test được siết **trước** khi chạy mutation vì đọc code thấy sẽ sống: giữ tồn tự động thêm assertion
`updatedAt` (bắt "bỏ touch"), replay đặt version lệnh 1 với body 0 (bắt "replay sau version").

**Nghiệm thu:** `mvn -o clean verify` — **1785 unit + 256 IT**, 0 failures/errors. `V79` chạy sạch trên DB dev có dữ liệu demo. Smoke HTTP thật
(`java -jar` dev, cổng 8090) lượt **`210812`**: 39/39 — tạo với kho dòng, version thiếu/lệch, PATCH 7 số lẻ, vật tư không thuộc lệnh, xem trước
(90/30 phút, 08:00→09:30→10:00 VN, không ghi), plan, giữ tự động 15 từ kho dòng bỏ qua 500 ở kho khác, release thiếu ⇒ `BLOCKED`, giữ tay kho sai
422, nhập thêm 5 + giữ nốt + release (lịch = xem trước, `scheduleRevision` 1, calendar revision trùng), giải phóng phần chưa cấp + replay + khác
payload 409, phiếu chờ chặn huỷ, khoảng ngày, WO MAKE khoá nguồn + huỷ trả quota (dòng ledger giữ, `conversionVersion` +1, chuyển đổi lại được,
huỷ lần hai 409). Fixture `GD8A-210801` (lượt hỏng do script), `GD8A-210812`, `GD8A-210815` **chưa** ngừng.

**Breaking changes — wire: CÓ** (`BE_RESPONSE_GD_08_2026-10-19.md §3`): version bắt buộc trên PATCH/plan/release/cancel/reserve/giữ tay/bỏ giữ
(thiếu ⇒ 400, lệch ⇒ 409); huỷ khi còn phiếu cấp chờ duyệt ⇒ 409; giữ tồn theo kho dòng (chặn kho khác 422); WO MAKE đổi số/kho đầu ra ⇒ 409
`SOURCE_LOCKED`; số lẻ thứ 7 ở `plannedQuantity`/`quantity` reservation ⇒ 400. Additive: 2 endpoint, `componentWarehouses`, `fromDate`/`toDate`,
`version`/`scheduleRevision`/`calendarRevision`/`scheduleTimeZone`/`scheduleCapturedAt`, `warehouseId`/`warehouseCode` dòng, `workMinutes`,
`workOrderVersion` trên reservation. **Java positional: có** — `WorkOrderService` constructor (bỏ `WorkCalendarLookupService`, thêm `WorkOrderScheduler`,
`MaterialIssueRepository`, `WorkOrderPlanningLinkService`, `WorkOrderIdempotency`, `SecurityAuditorAware`), `update`/`plan`/`release`/`cancel`/`list`
đổi chữ ký; `MaterialReservationService` constructor +1, `reserve`/`reserveAutomatically`/`release` +tham số; `WorkOrderResponse` +5 /
`WorkOrderComponentLineResponse` +2 (compat constructor giữ arity cũ); `WorkOrderOperationResponse` +1; `MaterialReservationResponse` +1 (compat);
`WorkOrderCreateRequest` +1 (compat), `WorkOrderUpdateRequest` +2, `WorkOrderCancelRequest` +1 (compat), `MaterialReservationCreateRequest` +1;
`WorkOrderRepository.search` +2.

---

### 0.59 `G7c` – QC Mua Hàng Theo Số Lượng, Lô Con (2026-10-19)

Nguồn: `FE_HANDOFF_GD_07_2026-10-14.md` (P1). Kế hoạch: `NEXT_PHASE_PLAN.md §14` (`14.3c`). Trả lời FE: `BE_RESPONSE_GD_07_2026-10-18_v3.md`
(phần 3/3). Migration **`V78`**. Bất biến **`B199`–`B204`** (`module/purchasing/CLAUDE.md`), **`B205`–`B206`** (`module/inventory/CLAUDE.md`).
Quyền mới `PERM_PURCHASE_QC_READ`/`_MANAGE` (ADMIN + MANAGER, `docs/roles-and-permissions.md`). **Không** mã lỗi mới.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Cột phản chiếu chỉ-đọc (`insertable = false, updatable = false`) rỗng trên entity vừa tạo trong cùng transaction.** `InventoryLot.parentLotId`
   chỉ có giá trị khi đọc từ DB; response của lệnh QC phải đọc `getParentLot().getLotId()`. Smoke HTTP thấy `parentLotId: null` trong response trong
   khi DB và `GET /v1/inventory/lots` đúng — IT chỉ kiểm cột DB nên xanh. Bài học `R7` lần thứ mười một, chiều mới: **IT phải kiểm cả response, không
   chỉ trạng thái DB**.
2. **Khoá phiếu theo đúng khuôn `B194`** (native id lock → `find` → khởi tạo dòng → `refresh`) — guard `hasReceiptAccess` nạp phiếu dưới OSIV y như
   G7b. Làm đúng ngay từ đầu nên smoke lần đầu đã ra `200` + `409 CONCURRENT_MODIFICATION` cho hai quyết định cùng version. Mutation bỏ `refresh` ⇒ IT đỏ.
3. **Tách lô dùng hai dòng sổ kho cùng `Idempotency-Key`** (`LOT_SPLIT_OUT` + `LOT_SPLIT_IN`) — hợp lệ chỉ vì `uk_stock_movements` là
   `(key, movement_type)` (`B69`). Key mỗi phần là `PQC-<decisionId>:A`/`:R`; dùng chung một key cho hai phần ⇒ phần loại replay thành lô con của phần
   đạt (mutation Q16 đỏ).
4. **Hàng không theo lô bị loại ở lại `qualityHoldQuantity`** — chưa có bucket "rejected" riêng; màn hình tồn cộng chung với phần chờ. Cố ý (D7, thêm
   cột tồn là đổi mọi tổng tồn).
5. **Chặn đi vòng QC chỉ phủ đổi trạng thái lô** (`InventoryLotService.changeStatus`, phụ thuộc mới `inventory → purchasing` qua
   `PurchaseQcLookupService`) và đảo nhập phiếu đã QC. `POST /v1/inventory/adjust` trên lô `HOLD` vẫn chạy — có từ trước G7, ghi giới hạn cho FE.
6. **MRP không thấy hàng đang chờ kiểm** — không còn là lượng mở PO, chưa khả dụng. Đúng ý FE R14 ("HOLD giảm inbound, accepted tính một lần") nhưng
   nghĩa là MRP có thể đề xuất mua lại trong lúc QC. Ghi rõ ở v3 Q30.
7. **Script seed demo** (`demo-capstone2`, `VIETBIKE`) chưa chạy lại — hàng mua của chúng nằm ở chờ kiểm, cần thêm bước QC.

**Nghiệm thu mutation (17, đã revert — 17/17 bị giết):** bỏ kiểm version · bỏ `setUpdatedAt` (IT) · nới trần lượng chờ · cho đạt lô hết hạn · gỡ chờ
kiểm cả phần loại (IT) · lô con loại thành `AVAILABLE` (IT) · bỏ `refresh` dưới khoá (IT) · tắt cổng `HOLD` và cổng lô con bị loại ở
`changeStatus` · cho đảo nhập phiếu đã QC · hàng đợi giữ dòng đã xong (IT) · `existsPendingQualityForLot` tính cả dòng xong (IT) · `splitLot` bỏ trừ
reserved · sai chuỗi quyền · bỏ kiểm trùng mã lô con · dùng chung key tách lô (IT) · response đọc `parentLotId` từ cột phản chiếu (IT). 🔴 **Hai mutation
lần đầu sống sót / bị bỏ qua** và chỉ bị giết sau khi bổ sung test: `splitLot` bỏ trừ reserved (thêm `splitLot_reservedPartOfTheParent_isNotSplittable`);
anchor của Q11 trùng giữa query chính và `countQuery` (neo lại vào `select l`). Q12 chỉ bị giết vì IT được thêm quyết định thứ hai dùng hết phần chờ.

**Nghiệm thu:** `mvn -o clean verify` — **1749 unit / 194 class + 253 IT / 36 class**, 0 failures/errors (baseline 1721 + 249). `V78` chạy sạch trên DB
dev có dữ liệu demo. Smoke HTTP thật (`java -jar` dev, cổng 8090) **hai lượt**: `131813` (lộ `parentLotId: null`; hai lần gọi đổi trạng thái lô thiếu
`Idempotency-Key` là lỗi script) và `132237` trên code cuối — 23 lượt gọi kiểm tra, 0 lệch: hàng đợi 2 dòng + tìm theo mã lô + `X-Plant-Id` lệch 409;
0+0 / thiếu lý do / vượt chờ / 7 chữ số lẻ / version cũ đúng field + mã; đổi trạng thái lô chờ QC ⇒ 409; QC theo lô 2.5/0.5 ⇒ `-A1`/`-R1` có
`parentLotId` + hạn của lô gốc, lô gốc 1 `HOLD`, tồn thực tế 4 → 4, 4 dòng tách lô; replay cùng `decisionId`, đổi payload 409; lô con loại không đổi
trạng thái được; QC không theo lô 20/5 ⇒ 25 / chờ 5; hai quyết định song song ⇒ một 200 + một `CONCURRENT_MODIFICATION`, đã chấp nhận 3.1; lịch sử 3
dòng; đảo nhập phiếu đã QC 409; lô hết hạn đạt 409 / loại 200. Fixture `GD7C-131813`, `GD7C-132237` **chưa** ngừng.

**Breaking changes — wire: CÓ, hẹp** (`BE_RESPONSE_GD_07_2026-10-18_v3.md §3`): đổi trạng thái lô đang chờ QC mua / lô con bị QC loại ⇒ 409
`LOT_NOT_ELIGIBLE`; huỷ (đảo nhập) phiếu đã có quyết định QC ⇒ 409 `STATE_CONFLICT`. Additive: 3 endpoint, 4 field dòng phiếu, `parentLotId` trên lô, 2
quyền. **Java positional: có** — `InventoryLotService` constructor +1 (`PurchaseQcLookupService`), `InventoryLotResponse` +1 (compat constructor giữ arity
cũ) / `InventoryLotDetailResponse` +1, `GoodsReceiptLineResponse` +4 (compat constructor), `MovementType` +2, `AuditAction` +1, `PurchasingCommand` +1.

---

### 0.58 `G7b` – Phiếu Nhận Nháp, Nhận Mua Vào Vùng Chờ Kiểm (2026-10-18)

Nguồn: `FE_HANDOFF_GD_07_2026-10-14.md` (P1). Kế hoạch: `NEXT_PHASE_PLAN.md §14` (`14.3b`). Trả lời FE: `BE_RESPONSE_GD_07_2026-10-18_v2.md`
(phần 2/3). Migration **`V77`**. Bất biến **`B193`–`B198`** (`module/purchasing/CLAUDE.md`); ghi chú ở `module/inventory/CLAUDE.md`. Quyền mới
`PERM_GOODS_RECEIPT_READ`/`_MANAGE` (ADMIN + MANAGER, `docs/roles-and-permissions.md`). Mã lỗi mới `PENDING_GOODS_RECEIPT`.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Open-session-in-view (mặc định bật, repo chưa từng tắt) làm `@Lock` trên query entity KHÔNG đủ.** `@PreAuthorize` chạy **ngoài**
   transaction nhưng **trong** EntityManager của request; guard nào tra entity (ở đây `hasReceiptAccess` nạp phiếu + PO + dòng) để lại bản cũ
   trong persistence context. Câu khoá — và cả `refresh(…, PESSIMISTIC_WRITE)` — gặp entity đã quản lý thì Hibernate **kiểm version** chứ không
   đọc lại ⇒ `StaleObjectState` ⇒ 409 `CONCURRENT_MODIFICATION`. Cách đúng (`GoodsReceiptPoster.lockOrder`): khoá native chỉ id → khởi tạo
   collection dòng → `refresh` thường. **Bất kỳ khoá bi quan nào khác trong repo có guard tra cùng entity cũng nên nghi ngờ cùng cách** — chưa rà
   (`ItemRepository.lockByItemId` của `G2`/`G5`, khoá nguồn chuyển kho `G4`, khoá run `G6b`).
2. 🔴 **`refresh` không cascade vào collection CHƯA khởi tạo.** Bản sửa đầu (refresh order) vẫn đỏ ở smoke vì guard chạm dòng PO qua
   `receipt.lines.purchaseOrderLine` chứ không qua `order.lines`. IT đầu tiên "tái hiện" OSIV bằng `order.getLines().size()` nên **che** đúng
   lỗi đó — IT phải chạm **đúng** đường guard chạm.
3. 🔴 **Bài học `R7` lần thứ mười, theo chiều mới:** lần này IT **có** chạy Postgres thật nhưng **không** có method security, nên không có OSIV.
   Chỉ smoke HTTP thật mới có đủ guard + OSIV + transaction. Hai vòng smoke liền là bằng chứng — đừng dừng khi IT xanh.
4. **`version` trên response ⇒ mọi đường ghi phải `saveAndFlush`** (bẫy §0.39 lần thứ ba). Đường đảo nhập và post cũ có từ trước G7 dùng `save`,
   vô hại cho tới khi G7b thêm `version` vào `GoodsReceiptResponse`. Smoke thấy huỷ POSTED trả `version` không đổi.
5. **Mọi lần nhận mua vào `HOLD`, cả đường cũ** (D4) — số khả dụng/MRP/dashboard giảm cho mọi lần nhận mới. Script seed demo (`demo-capstone2`,
   `VIETBIKE`) gọi đường cũ ⇒ hàng nằm ở hold; **chưa chạy lại** script nào.
6. **Đảo nhập phiếu đã nhận vào hold phải gỡ hold trước** (CHECK `reserved + qualityHold <= quantity`). Cờ `quality_held` phân biệt phiếu cũ
   (trước `V77`, không có hold) — không có cờ thì đảo nhập phiếu cũ sẽ gỡ một hold không tồn tại.
7. **Nháp không giữ chỗ lượng mở** (FE Q9): kiểm sớm lúc lưu chỉ để báo lỗi; post kiểm lại tất cả dưới khoá.
8. **`createdAt` của response "vừa tạo" vẫn 100 ns rồi đọc lại µs** (§0.52 #3, toàn hệ thống, chưa sửa) — `postedAt`/`cancelledAt` thì đã cắt µs.

**Nghiệm thu mutation (18, đã revert — 18/18 bị giết):** 12 từ lượt đầu (bỏ khoá, nhận `AVAILABLE`, kiểm lượng mở không cộng dồn, nhận lại mã lô,
đường cũ vào lô `AVAILABLE`, đảo nhập không gỡ hold, đóng PO bỏ qua phiếu nháp, không đánh dấu `quality_held`, nạp phiếu trước khoá, bỏ `cast` ở
search, `PENDING_GOODS_RECEIPT` → `STATE_CONFLICT` (**cùng 409**), chi tiết bắt buộc `PURCHASE_ORDER_READ`) + 6 cho phần sửa sau smoke: bỏ khoá dòng
native, bỏ `refresh`, đường cũ không khoá, bỏ refresh phiếu, không khởi tạo collection dòng trước `refresh` (IT OSIV đỏ), hai chỗ `save` thay
`saveAndFlush`.

**Nghiệm thu:** `mvn -o clean verify` — **1721 unit / 191 class + 249 IT / 35 class**, 0 failures/errors (baseline 1686 + 243). `V77` chạy sạch trên
DB dev có dữ liệu demo. Smoke HTTP thật (`java -jar` dev, cổng 8090) **bốn lượt**: `225459` (lộ OSIV, kẻ thua `CONCURRENT_MODIFICATION` trên
`purchase_orders`), `231431` (lộ dòng PO chưa refresh, `CONCURRENT_MODIFICATION` trên `purchase_order_lines`), `232420` (lộ `version` cũ sau huỷ
POSTED), `233219` trên code cuối — 26 lượt gọi kiểm tra, 0 lệch (huỷ POSTED ⇒ `version` 2 → 3):
nháp không đụng tồn, replay cùng id, vượt mở ⇒ `lines[1]`, lô thiếu/thừa theo field, `PENDING_GOODS_RECEIPT` ×2, post ⇒ RAW 26/hold 26 + lô `HOLD` hạn
`2026-12-16T17:00Z`, khả dụng API 0, `postedAt` ổn định, post lại 409, mã lô trùng (khác hoa thường) 409, hai post đồng thời ⇒ một thắng + một
`PLANNED_QUANTITY_EXCEEDED`, đường cũ vào hold 36 → 38, đảo nhập ⇒ 12/12. Fixture `GD7B-225459`, `GD7B-231431`, `GD7B-232420`, `GD7B-233219` **chưa** ngừng.

**Breaking changes — wire: CÓ** (`BE_RESPONSE_GD_07_2026-10-18_v2.md §3`): mọi lần nhận mua (cả `POST /purchase-orders/{id}/goods-receipts` cũ) vào
`HOLD` — tồn khả dụng không tăng; đường cũ nêu lô `AVAILABLE` có sẵn ⇒ 409 `LOT_NOT_ELIGIBLE`; huỷ/đóng PO còn phiếu nháp ⇒ 409 `PENDING_GOODS_RECEIPT`;
huỷ phiếu `POSTED` chỉ còn `PERM_PURCHASE_ORDER_MANAGE`; `postedAt` có thể `null` (phiếu nháp); số lẻ thứ 7 ở `receivedQuantity` ⇒ 400. Additive: 5
endpoint, 15 field phiếu, 7 field dòng, 2 quyền. **Java positional: có** — `GoodsReceiptService` constructor đổi + `cancel` thêm overload 3 tham số,
`GoodsReceiptResponse` +15 / `GoodsReceiptLineResponse` +7 (compat constructor), `GoodsReceiptCancelRequest` +1, `PurchaseOrderService` constructor +1,
`GoodsReceiptPoster` (mới, package-private) nhận `EntityManager` + `PurchaseOrderRepository`.

---

### 0.57 `G7a` – Vòng Đời Đơn Mua Hàng (2026-10-18)

Nguồn: `FE_HANDOFF_GD_07_2026-10-14.md` (P1). Kế hoạch: `NEXT_PHASE_PLAN.md §14`. Trả lời FE: `BE_RESPONSE_GD_07_2026-10-18.md` (v1, phần 1/3).
Migration **`V76`**. Bất biến **`B187`–`B192`** (`module/purchasing/CLAUDE.md`); ghi chú ở `module/planning/CLAUDE.md`. Không permission mới.

**Quyết định user (`AskUserQuestion`):** D0 tách `G7a`/`G7b`/`G7c` làm liền; D1 đổi tên trạng thái + `CLOSED`; D3/D4 thêm phiếu nhận `DRAFT`,
**giữ** huỷ phiếu `POSTED` + `REVERSAL`, nhận mua vào `HOLD` cho cả hai đường (làm ở `G7b`); D8 **hoãn** giá trị tồn. D2/D5–D7/D9–D12 theo khuyến nghị.

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Migration đổi tên giá trị bị CHECK chặn phải gỡ CHECK trước.** Bản đầu của `V76` `UPDATE … SET status = 'CONFIRMED'` dưới CHECK cũ ⇒
   chết trên **mọi** DB có PO `SENT`; DB trống của Testcontainers vẫn xanh. Chỉ `FlywayMigrationIT.migrate_v76_*` (seed ở `target("75")` rồi
   migrate tiếp) bắt được — đúng khuôn `migrate_v36_*`/`migrate_v73_*`. Đổi tên enum có dữ liệu ⇒ **luôn** có một test kiểu này.
2. 🔴 **Thứ tự dòng theo `createdAt` không ổn định** khi các dòng lưu cùng flush (Postgres làm tròn µs ⇒ bằng nhau). Có từ trước G7; smoke G7a
   lộ ra khi cùng PO trả hai thứ tự. Nay `PurchasingMapper.LINE_ORDER` tie-break theo id **dạng chuỗi** (`UUID.compareTo` so long có dấu).
   Mọi chỗ đánh chỉ số `lines[i]` phải dùng **cùng** comparator. GR lines và các chứng từ khác có cùng mẫu sắp — chưa rà.
3. 🔴 **Huỷ PO phải trả quota mà đề xuất vẫn `CONVERTED`** (G6b #6): `convertedQuantity` nay phân biệt "không dòng ledger nào" (one-shot cũ ⇒
   chuyển hết) với "có dòng nhưng đã giải phóng hết" (⇒ 0). Thiếu phân biệt này thì release vô hiệu mà mọi số khác trông đúng.
4. **Mutation M11 sống sót có lý do:** vế "chưa nhận gì" của `canCancel()` thừa vì `DRAFT`/`CONFIRMED` đã hàm ý received = 0 — đã gỡ (bài học
   §0.45 #3: một điều kiện trông như bảo vệ cũng phải qua mutation).
5. **Chuyển BUY cần thêm `PERM_PURCHASE_ORDER_MANAGE`** — `@PreAuthorize` trên `createFromPlanning`, caller gọi qua proxy nên guard chạy và lỗi
   rollback cả lượt chuyển đổi.
6. **Idempotency lệnh PO dùng bảng riêng** (`purchasing_idempotency_records`, `UNIQUE (key, command)`, không TTL) — lệnh đổi một chứng từ có sẵn
   nên key không nằm trên chính document như `goods_receipts`. Replay trả **trạng thái hiện tại**, không phải response cũ (FE §3.4 chấp nhận).
7. 🔴 **Bẫy công cụ lặp lại:** (a) heredoc bash chứa `'''` hoặc nháy lồng vẫn vỡ — viết script/file bằng Write (§0.53 #10); (b) script mutation
   khôi phục bằng `shutil.copyfile` **lỗi `Errno 22`** khi OneDrive khoá file ⇒ file nguồn nằm lại ở trạng thái bị đột biến; đã thêm retry,
   và luôn `find src -name "*.mutbak"` sau mỗi lượt; (c) smoke script ghi bằng chứng bằng **tham chiếu** tới dict request rồi sửa dict đó sau
   khi gọi ⇒ JSONL ghi giá trị *sau*, không phải giá trị đã gửi (vi phạm B2) — phải chụp bản sao lúc gửi; (d) Docker Desktop tắt khi bắt đầu
   phiên ⇒ bật trước khi đo baseline; đường dẫn scratchpad quá dài cho `git worktree` trên Windows ⇒ dùng `C:/Users/hau12/wt-g7base`.

**Nghiệm thu mutation (13, đã revert — 12/13 bị giết, M11 = code thừa đã gỡ):** lượng mở bỏ `closed` (IT) · ledger cộng cả dòng đã giải phóng
(IT) · `convertedQuantity` về luật cũ · huỷ không trả quota · bỏ khoá nguồn · `SOURCE_LOCKED` → `STATE_CONFLICT` (**cùng 409**) · bỏ `cast` ở
`search` (IT, `lower(bytea)`) · đóng cũng trả quota · bỏ chặn huỷ phiếu nhận của PO `CLOSED` · bỏ `@PreAuthorize` của `createFromPlanning` ·
khoá lượt theo thứ tự chèn thay vì id · bỏ tie-break thứ tự dòng.

**Nghiệm thu:** `mvn -o clean verify` — **1686 unit / 189 class + 243 IT / 34 class**, 0 failures/errors (baseline worktree sạch 1634 + 233).
`V76` chạy sạch trên DB dev có dữ liệu demo (2 `SENT` → `CONFIRMED`, 1 `RECEIVED` → `FULLY_RECEIVED`). Smoke HTTP thật (`spring-boot:run` dev, cổng
8090) lượt cuối **`211857`**: 54 lượt gọi kiểm tra, 0 lệch — nháp/replay/xung đột key, loại vật tư, chữ số lẻ thứ 7, `X-Plant-Id`, `PATCH` giữ id
dòng, confirm thiếu giá đúng dòng, close 25/40 ⇒ closed 15, đảo phiếu nhận của PO đóng ⇒ 409, huỷ có/không body, PO MRP `SOURCE_LOCKED`, huỷ ⇒
còn chuyển được 15 → 75 + `conversionVersion` 1 → 2 + chuyển lại được. Đọc một PO 5 lần: thứ tự dòng giống nhau. Fixture `GD7A-211137`,
`GD7A-211156`, `GD7A-211857` **chưa** ngừng.

**Breaking changes — wire: CÓ** (`BE_RESPONSE_GD_07_2026-10-18.md §3`): `status` `SENT`→`CONFIRMED`, `RECEIVED`→`FULLY_RECEIVED`, giá trị mới
`CLOSED`; lọc `status=SENT` ⇒ 400; `remainingQuantity` của PO đóng = `openQuantity`; chuyển BUY thiếu `PERM_PURCHASE_ORDER_MANAGE` ⇒ 403; huỷ phiếu
nhận của PO `CLOSED` ⇒ 409; `X-Plant-Id` lệch trên list PO ⇒ 409. Additive: 4 endpoint, body tuỳ chọn cho `cancel`, 12 field PO, 4 field dòng,
2 field ledger. **Java positional: có** — `PurchaseOrderService` constructor +4, `cancel`/`list` đổi chữ ký, `PurchaseOrderRepository.search` +3,
`PurchaseOrderResponse` +12 / `PurchaseOrderLineResponse` +4 (compat constructor giữ arity cũ), `PlanningConversionResponse` +2,
`SuggestionQuantityProjection.getRowCount()`, `MrpPlanningMapper.convertedQuantity` overload 3 tham số.

---

### 0.56 `G6c` – Nhập Đơn Bán Hàng Excel (2026-10-18)

Nguồn: `FE_HANDOFF_GD_06_2026-10-14.md` (P1), phần nhập SO. Kế hoạch: `NEXT_PHASE_PLAN.md §13` (D11). Trả lời FE:
`BE_RESPONSE_GD_06_2026-10-17_v3.md`. **Không migration** (`import_runs.target_type` là `VARCHAR`, không CHECK), không permission mới, không
mã lỗi mới. Bất biến **`B184`–`B186`** (`module/dataimport/CLAUDE.md`).

**Hệ quả cần nhớ khi code tiếp:**

1. **Không làm đường `/sales-orders/v1/imports/*` FE đề xuất** — `kind=so` trên đường batch `G3` (cùng quyết định D1 của `G3`, tiền lệ `kind=bom`).
   Mẫu tải về / file lỗi tải về (binary XLSX) **không** có — FE tự sinh mẫu như G3/G5.
2. **`SalesOrderImportHandler` gọi `createDraft` public** (có `@PreAuthorize` MANAGE trên nhà máy + `@Auditable`) — chạy trong security context
   của người commit, nên guard và audit vẫn áp **mỗi đơn**. Đúng hướng: không có đường INSERT tắt.
3. **Q22 (lịch sử chỉ cần READ) không làm** — giữ READ + MANAGE như mọi loại batch (`B146`); route FE hiện cũng đòi cả cặp.
4. 🔴 **Bẫy fixture, không phải bug code:** test 1904 đầu tiên đỏ vì `addNewWorkbookPr()` tạo phần tử `workbookPr` **thứ hai** (POI đọc cái đầu).
   Phải đọc nguyên nhân trước khi sửa reader.
5. **Mutation #8 sống sót lần đầu** (bỏ pattern ngày): mọi case cũ đều bị `LocalDate.parse` chặn sẵn. Pattern có giá trị thật ở chỗ khác —
   năm 5 chữ số có dấu (`+12026-10-20`) `parse` nhận. Đã thêm case; lần chạy lại bị giết.

**Nghiệm thu mutation (9, đã revert — 9/9 bị giết):** bỏ lọc nhà máy cho batch đơn bán ở `searchBatches` (IT) · đọc serial theo hệ 1900 cố định ·
bỏ "loại cả nhóm" · bỏ tham chiếu khỏi luật nhóm · bỏ kiểm đơn vị ngừng · `salesOrderCount` đếm nhầm field (IT) · nhận ô ngày có giờ · bỏ pattern
ngày (sau khi thêm case) · tra khách bằng tập rỗng (3 case IT đỏ).

**Nghiệm thu:** `mvn -o clean verify` — **1634 unit / 187 class + 233 IT / 34 class**, 0 failures/errors (baseline 1621 + 230). Smoke HTTP thật
(`java -jar` dev, cổng 8090) lượt **`120521`**: 33 lượt gọi (10 dựng dữ liệu + 23 kiểm), mọi status đúng kỳ vọng — 3 dòng/2 nhóm ⇒ `READY`, 0 đơn; commit ⇒ `SO-000006`
(2 dòng, `2000.000001` + `5.000000`) + `SO-000007` `DRAFT`, 2 audit `SALES_ORDER_CREATED`; commit lại + đọc lại ⇒ cùng `createdIds` và `committedAt`;
nhóm A số lượng 0 ⇒ `INVALID`, commit `409 STATE_CONFLICT`, 0 đơn; sai version/sheet ⇒ `400 TEMPLATE`; công thức ⇒ `FORMULA`; 5 MiB + 1 ⇒ `413`;
501 dòng ⇒ `400 ROW_LIMIT`; workbook 1904 ⇒ đúng `2026-10-20`; khách ngừng sau `READY` ⇒ `409 IMPORT_REVALIDATION_FAILED`, 0 đơn; thiếu `plantId`
⇒ `400`; `X-Plant-Id` lệch ⇒ `409`; lịch sử nhà máy khác rỗng. Fixture công ty `GD6C-120521` **chưa** ngừng.

**Breaking changes — wire: KHÔNG** (additive: `kind=so`, `salesOrderCount` trên batch). **Java positional: có** — `DataImportBatchResponse` +1
component, `FixedTemplateValidator`/`DataImportBatchCommitter` constructor +1, `ImportRunRepository.searchBatches` +1 tham số,
`DataImportKind`/`ImportTargetType` +1 hằng.

---

### 0.55 `G6b` – Run MRP Thương Mại + Chuyển Đổi Một Phần (2026-10-18)

Nguồn: `FE_HANDOFF_GD_06_2026-10-14.md` (P1). Kế hoạch: `NEXT_PHASE_PLAN.md §13`. Trả lời FE: `BE_RESPONSE_GD_06_2026-10-17_v2.md` (bổ sung v1).
Migration **`V75`**. Bất biến **`B178`–`B183`** (`module/planning/CLAUDE.md`); ghi chú ở workorder/purchasing/sales. Không permission mới.

**Quyết định user:** D5 (sổ chuyển đổi), D6 (endpoint thương mại riêng), D9 (BUY ⇒ PO nháp), D10 (không cần duyệt) theo khuyến nghị; **sửa
tồn an toàn chỉ cộng một lần** và **D8 làm tròn 6 số lẻ trước khi nổ — cho cả đường cũ lẫn mới** (đổi số MRP, đo A/B).

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Hai lỗi số MRP, đều áp cho run cũ.** Tồn an toàn cộng ở mỗi dòng cùng pool ⇒ mua lại nhiều lần (A/B `D26-`: RUN-8BB65B70 →
   RUN-706FD135, 11 → 6 đề xuất, BUY 8 → 3; `GRIP` dòng 2 net 35 → 0, tồn cuối 45 ≥ 40). Gross linh kiện nay `ceil6` trước khi net. Hao hụt vẫn
   `× (1 + rate)` — lệch mock FE (`÷ (1 − rate)`), chỉ trả lời, không đổi.
2. 🔴 **Run thương mại lập kế hoạch `uncovered`, không phải `ordered`.** Run cũ vẫn lập kế hoạch `requiredQuantity` của demand ⇒ với đơn
   đang giữ tồn nó đếm hai lần (trừ held ở khả dụng **và** lập kế hoạch đủ lượng đặt). Có chủ đích không đổi run cũ — FE dùng run thương mại.
3. 🔴 **Không có `mrp_requirement_sources`** (lệch D7): engine không gộp nguồn, bảng nối luôn một hàng/dòng. Mảng nguồn suy trong mapper.
4. **Phiên bản nguồn cố ý loại coverage WO/QC** — nếu không, chuyển 40 xong thì chuyển 30 tiếp bị `STALE`. Over-cover giữa hai run chặn bằng
   cap `uncovered` đọc lại lúc chuyển (B183), không bằng phiên bản nguồn.
5. **Khoá dòng run + `@Version` của `MrpRun`**: hai lệnh cùng `expectedConversionVersion` — một thắng, một `CONCURRENT_MODIFICATION`.
6. **Đề xuất chuyển qua đường mới thành `CONVERTED` dù còn lại > 0** — để đường `approve`/`convert-to-*` cũ không chuyển full lần nữa.
7. 🔴 **Bẫy lặp lại:** (a) `completedAt` 100 ns ≠ µs lưu DB (§0.51 #3) — đã cắt µs; (b) Docker Desktop tắt giữa lượt mutation ⇒ IT lỗi
   `Could not find a valid Docker environment`, trông như mutation bị giết — **đọc nguyên nhân lỗi**, đừng đếm đỏ; (c) mutation xoá dòng JPQL
   làm hỏng query lúc khởi động ⇒ "giết" giả, phải dùng mutation ngữ nghĩa.

**Nghiệm thu mutation (8, đã revert — 8/8 bị giết):** tồn an toàn cộng mỗi dòng · bỏ `ceil6` · bỏ kiểm còn lại · bỏ kiểm nguồn hiện tại ·
`PLANNING_SOURCE_STALE` → `STATE_CONFLICT` (**cùng 409**) · cap phần gắn đơn `null` · nới bộ lọc type của đầu vào phiên bản nguồn (IT) · lập
kế hoạch `requiredQuantity` thay `uncovered`.

**Nghiệm thu:** `mvn -o clean verify` — **1621 unit / 186 class + 230 IT / 34 class**, 0 failures/errors (baseline 1583 + 224). `V75` chạy sạch
trên DB dev có dữ liệu demo. A/B legacy run trên `D26-OMNIPLANT` bằng worktree `f6d7b8e` (cổng 8091) và bản mới (8090), trace `aad0459ee3b34f20`
/ `05a9305327c948d9`. Smoke HTTP thật lượt **`112703`**: 39 lượt gọi ngoài dựng dữ liệu, mọi status đúng kỳ vọng (đơn 100 giữ 30 ⇒ gross 70; chuyển
40 → 30 ⇒ chưa phủ 30 → 0; WIP không gắn đơn; bỏ giữ ⇒ `STALE` + 409). Sau đó chỉ sửa `completedAt` (cắt µs) + một assertion; chạy lại
`CommercialPlanningRunServiceTest`/`MrpRunServiceTest` xanh. Fixture `GD6B-112703` + 2 run trên `D26-OMNIPLANT` **chưa** dọn.

**Breaking changes — wire: CÓ** (`BE_RESPONSE_GD_06_2026-10-17_v2.md §3`): số MRP đổi (tồn an toàn một lần, ceil6) cho cả `POST /planning-runs`;
đề xuất chuyển qua `/conversions` thành `CONVERTED` ⇒ đường cũ 409. Additive: 3 endpoint, 5 field run, 11 field dòng nhu cầu, `RUN_ITEM_MAPPING`,
`PLANNING_SOURCE_STALE`. **Java positional: có** — `MrpRunService` constructor +4, `MrpRunResponse` +5 (compat constructor giữ arity cũ),
`MrpRequirementLineResponse` +11 (compat constructor), `MrpCalculationService.calculate` overload 4 tham số, `createFromMrp`/`allocate` overload
+1, `SupplySuggestionService.ensureNotBlocked` → `static`.

---

### 0.54 `G6a` – Đơn Bán Hàng Nối Khách + Giữ Tồn Cho Đơn (2026-10-17)

Nguồn: `FE_HANDOFF_GD_06_2026-10-14.md` (P1). Kế hoạch + D0–D12: `NEXT_PHASE_PLAN.md §13`. Trả lời FE: `BE_RESPONSE_GD_06_2026-10-17.md`
(v1, chỉ phần G6a). Migration **`V74`**. Bất biến **`B171`–`B174`** (sales), **`B175`–`B176`** (workorder). **Không** permission mới,
**không** mã lỗi mới ⇒ `docs/roles-and-permissions.md` không đổi.

**Quyết định user:** D0 tách `G6a`/`G6b`/`G6c` và làm liền cả ba; D4 **mở rộng `material_reservations`** thay vì bảng mới; D1–D3 theo
khuyến nghị (giữ `customerName` snapshot + thêm `customerId`; đường nháp mới song song đường cũ; `produced`/`delivered` trả `null` + `UNKNOWN`).

**Hệ quả cần nhớ khi code tiếp:**

1. 🔴 **Một nguồn lượng giữ.** Giữ tồn cho đơn cộng vào **cùng** `stock_balances.reserved_quantity` với WO ⇒ MRP, dashboard, gate xuất
   kho tự thấy, không phải sửa gì ở đó. Bảng riêng (phương án bị loại) sẽ cần sửa mọi chỗ tính khả dụng — đúng loại "một hệ thống hai
   con số" của `§0.44`.
2. 🔴 **`searchByWarehouse` phải left join.** Trước G6a nó inner join `workOrder`/`componentLine` ⇒ dòng giữ của đơn bán (không có WO)
   sẽ biến mất khỏi `GET /v1/inventory/reservations`. Bắt bằng `MaterialReservationRepositoryIT` (mock không thấy — `R7` lần thứ chín).
3. 🔴 **Bump version đơn ở mọi lệnh giữ/bỏ giữ** (`setUpdatedAt` + `saveAndFlush`): dòng giữ ở bảng con nên đơn không tự dirty — đúng
   bẫy `B112` #2. Không bump ⇒ gửi lại lệnh giữ chạy hai lần. Mutation bỏ bump ⇒ `SalesOrderReservationIT` đỏ.
4. **FEFO cho đơn bán loại lô quá hạn** (`findReservableBalancesFefo`) — query FEFO của WO **không đổi** (G4 B154: hết hạn chỉ chặn luồng
   kho thủ công). Hai query trông giống nhau, khác điều kiện hạn có chủ đích.
5. **`uncovered = max(0, ordered − qcAccepted − held − workOrderCovered)`**; WO `DRAFT` **được** tính phủ (FE Q2), `CANCELLED`/`CLOSED`
   thì không. `fulfilledQuantity`/`openQuantity` giữ nghĩa cũ.
6. **Mã đơn từ sequence**, bỏ qua giá trị đã bị mã cũ chiếm (đơn cũ do client tự đặt có thể là `SO-000003`). Sequence không rollback
   ⇒ có lỗ hổng số, chấp nhận.
7. **Hướng phụ thuộc mới `sales → workorder`** (2 service không `@PreAuthorize`). `module/sales/CLAUDE.md` mục 1 cũ đã lỗi thời — ghi chú
   ở mục G6a của file đó.

**Nghiệm thu mutation (7, đã revert — 7/7 bị giết):** inner join lại `searchByWarehouse` · bỏ điều kiện `ACTIVE` khi cộng lượng giữ ·
bỏ bump version đơn khi giữ · bỏ điều kiện hết hạn FEFO đơn bán · `PLANNED_QUANTITY_EXCEEDED` → mã 409 khác (**cùng status**) · bỏ
`cast(... as date)` ở list (lỗi kiểu tham số Postgres) · bỏ trả tồn khi huỷ đơn.

**Nghiệm thu:** `mvn -o clean verify` — **1583 unit / 184 class + 224 IT / 33 class**, 0 failures/errors (baseline 1548 + 215). `V74` chạy
sạch trên DB dev có dữ liệu demo. Smoke HTTP thật (`spring-boot:run` dev, cổng 8090) lượt **`142411`**: 55 lượt gọi (kể cả dựng dữ liệu),
mọi status đúng kỳ vọng; số liệu khớp ví dụ FE (đặt 100, giữ 30 ⇒ chưa phủ 70; tồn 30 / giữ 30 / khả dụng 0; FEFO lấy lô sớm 4 + lô muộn 3,
bỏ qua lô HOLD và lô quá hạn). Fixture công ty `GD6-142411` (và `GD6-142354` của lượt hỏng do script) **chưa** ngừng.

**Breaking changes — wire: CÓ, hẹp** (`BE_RESPONSE_GD_06_2026-10-17.md §3`): tồn khả dụng mọi màn hình giảm theo lượng đơn giữ · đổi trạng
thái lô đang bị đơn giữ ⇒ `409 RESERVED_STOCK` · huỷ đơn `CONFIRMED` qua đường cũ nay trả hết tồn đơn giữ · `GET /v1/inventory/reservations`
có dòng `ownerType = "SALES_ORDER"` với field lệnh SX `null`. Additive: 5 endpoint, 7 field trên dòng đơn, 8 field trên đơn, 3 trên nhu cầu.
**Java positional: có** — `SalesOrderLineResponse` +7, `SalesOrderResponse` +8, `PlanningDemandLineResponse` +3,
`InventoryReservationResponse` +5; `SalesOrderService` constructor + `confirm`/`cancel`/`list`/`planningDemands` đổi chữ ký;
`InventoryReservationQueryService` constructor +1; `SalesOrderRepository.search*` → `searchExtended`.

---

### 0.5 ✅ Đảo Ngược Ngữ Nghĩa — ĐÃ XỬ LÝ Ở `F5-A` (2026-07-27)

Đây từng là rủi ro lớn nhất của track `F*`. **Đã xong**, giữ lại bảng để agent sau hiểu vì sao code
trông như hiện nay:

| | Trước `F5` | Sau `F5-A` (hiện tại) |
|---|---|---|
| Ai làm WO tiến triển | **Receipt approve** | **`ProductionExecutionService.report`** — operator post good/scrap/rework |
| Trần của receipt (**B16**) | `plannedQuantity − completedQuantity − Σ(DRAFT + PENDING_APPROVAL)` | `actualGoodQuantity − completedQuantity − Σ(DRAFT + PENDING_APPROVAL)` |
| WO → `COMPLETED` khi | receipt approve đủ số | cumulative good = planned, **độc lập receipt** |

**Ba cột số lượng trên `work_orders`, đừng nhầm:** `plannedQuantity` (kế hoạch) ·
`actualGoodQuantity`/`actualScrapQuantity`/`actualReworkQuantity` (xưởng làm ra) ·
`completedQuantity` (**đã nhập kho** — giữ nguyên tên và ngữ nghĩa cũ, có chủ đích, để dữ liệu lịch
sử không bị diễn giải lại). Chi tiết + bất biến `B53`-`B57`: `module/workorder/CLAUDE.md`.

## 1. PROJECT OVERVIEW

### 1.1 Description
Manufacturing ERP là hệ thống hoạch định nguồn lực doanh nghiệp dành riêng cho **sản xuất** (nhà máy lắp ráp, sản xuất theo quy trình). Khác với Commercial ERP (mua-bán-phân phối), hệ thống quản lý **vòng đời sản xuất đầu cuối** — từ nguyên liệu thô đến thành phẩm.

**Mục tiêu nghiệp vụ lõi** (từ `AGENTS.md` §1, đã xoá):
- Inventory accuracy
- Multi-level BOM
- Shortage detection
- Work Order execution
- Material issue / production receipt / WIP tracking / variance

### 1.2 So Sánh với Commercial ERP

| Chiều | Manufacturing ERP | Commercial ERP |
|---|---|---|
| Sản xuất | BOM đa cấp, Work Order, Routing, MRP I/II | Không có / cơ bản |
| Tồn kho | WIP, theo dõi Batch/Lot/Serial | Chỉ kho tiêu chuẩn |
| Chi phí | Job/Process/Standard Costing + Phân tích variance | Chỉ COGS |
| Hoạch định | MRP + CRP (tự động yêu cầu vật tư/năng lực) | Dự báo cầu cơ bản |
| Truy xuất nguồn gốc | Toàn chuỗi (bắt buộc) | Tùy chọn |
| Tích hợp MES | Giao diện ERP ↔ MES 2 chiều | Không cần |

### 1.3 Lộ Trình Module

```
Phase 1 – Foundation
├── Auth & Security (JWT, RBAC, Session management)
├── User & Organization Management
└── Inventory / Warehouse (cờ IsManufacturing)

Phase 2 – Manufacturing Core
├── Bill of Materials (BOM đa cấp)
├── Work Order Management
├── Material Requirements Planning (MRP)
└── Shop Floor Control

Phase 3 – Advanced & Integration
├── MES Integration (chuẩn ISA-95)
├── Quality Control (truy xuất Batch/Lot)
├── Costing Engine (Standard/Job/Process)
└── Reporting & Analytics (OEE, Variance)
```

### 1.4 Tham Chiếu Thực Tế
- **VinFast / Foxconn**: BOM 8–10 cấp + MES thời gian thực trên dây chuyền lắp ráp
- **Masan / TH True Milk**: Sản xuất theo quy trình + kiểm soát Batch/phế liệu
- Mô hình kiến trúc: **ISA-95** (chuẩn tích hợp ERP ↔ MES)

---

## 2. TECHNOLOGY STACK

```yaml
language:       Java 17 (records, sealed classes, pattern matching)
framework:      Spring Boot 3.x
security:       Spring Security 6 + JWT (jjwt)
database:
  primary:      PostgreSQL (quan hệ, ACID)
  cache:        Redis (token store, session blacklist, rate-limit)
orm:            Spring Data JPA + Hibernate
migration:      Flyway
build:          Maven
documentation:  Springdoc OpenAPI 3 (Swagger UI)
testing:        JUnit 5 · Mockito · MockMvc · Testcontainers
monitoring:     Spring Boot Actuator + Micrometer
logging:        SLF4J + Logback (JSON có cấu trúc trong prod)
containerize:   Docker + Docker Compose
```

---

## 3. PROJECT CONVENTIONS

### 3.1 Quy Tắc Đặt Tên
- **PascalCase** → tên class (`WorkOrderService`, `BomController`)
- **camelCase** → phương thức & biến (`findByWorkOrderId`, `isExpired`)
- **ALL_CAPS** → hằng số (`DEFAULT_PAGE_SIZE`, `TOKEN_PREFIX`)
- **snake_case** → cột database, file migration Flyway

### 3.2 Cấu Trúc Package
```
com.erp.manufacturing
├── common/
│   ├── exception/          # Phân cấp exception toàn cục
│   ├── response/           # ApiResponse, PageResult wrappers
│   ├── security/           # JWT utils, filters, IpExtractor, TokenStoreService — CLAUDE.md riêng
│   ├── audit/              # BaseEntity, AuditLog entity, AuditLogService, Aspect — CLAUDE.md riêng
│   └── context/            # RequestContext (userId, ip, traceId) – ThreadLocal holder
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── AsyncConfig.java            # @EnableAsync + Executor truyền MDC
│   ├── JpaAuditingConfig.java      # @EnableJpaAuditing
│   ├── RateLimitProperties.java    # @ConfigurationProperties
│   └── OpenApiConfig.java
├── module/
│   ├── auth/               # AuthController, AuthService, PasswordResetTokenService — CLAUDE.md riêng
│   ├── user/
│   ├── organization/       # roles/permissions/access_scopes, AccessControlService — CLAUDE.md riêng
│   ├── inventory/          # CLAUDE.md riêng
│   ├── bom/                # CLAUDE.md riêng
│   ├── workorder/          # CLAUDE.md riêng
│   ├── routing/            # Routing master data + snapshot lên Work Order — CLAUDE.md riêng
│   ├── sales/              # Sales Order + independent demand cho MRP — CLAUDE.md riêng
│   ├── planning/           # MRP run, requirement explosion, supply suggestion — CLAUDE.md riêng
│   ├── purchasing/         # Supplier, PR → PO → Goods Receipt — CLAUDE.md riêng
│   └── reporting/          # Report controller theo domain (inventory/workorder/purchasing)
└── ManufacturingErpApplication.java
```

> Các thư mục ghi "CLAUDE.md riêng" có file `CLAUDE.md` lồng bên trong — Claude Code chỉ tự nạp file đó
> khi có agent thực sự đọc/sửa code trong đúng thư mục ấy. Xem bảng đầy đủ ở "Bản Đồ Tài Liệu" dưới đây.

### 3.3 Quy Ước API
- Base path: `/api/v1/`
- Auth endpoints: `/api/v1/auth/**` (permit all)
- Protected: `/api/v1/**` (yêu cầu JWT hợp lệ)
- HTTP status codes: tuân thủ chặt chẽ ngữ nghĩa REST
- Phân trang: query params `page`, `size`, `sortBy`, `sortDir`

---

## BẢN ĐỒ TÀI LIỆU

> File này đã được chia nhỏ (2026-07-25) để agent chỉ tự nạp phần liên quan đến task đang làm,
> thay vì luôn nạp toàn bộ ~1500 dòng. Cơ chế:
> - `.claude/rules/*.md` → Claude Code **luôn** tự nạp cùng `CLAUDE.md`, dùng cho rule cross-cutting (áp dụng mọi module).
> - `<package>/CLAUDE.md` (lồng trong `src/main/java/...`) → chỉ tự nạp khi agent đọc/sửa file trong đúng thư mục đó.

| Chủ đề | File | Cơ chế nạp |
|---|---|---|
| Error handling / response contract | `.claude/rules/error-handling.md` | Luôn nạp |
| Git workflow / commit convention / migration naming | `.claude/rules/dev-workflow.md` | Luôn nạp |
| Key architectural decisions | `.claude/rules/architecture-decisions.md` | Luôn nạp |
| Best practices checklist (security/API/DB/code quality/perf/test/observability) | `.claude/rules/best-practices.md` | Luôn nạp |
| Coding rules hàng ngày (N+1, breaking change, cấm) | `.claude/rules/coding-rules.md` | Luôn nạp |
| Auth/Session design (JWT, Redis key, rate limit, RTR, fingerprint) + bất biến B35-B36 | `src/main/java/com/erp/manufacturing/common/security/CLAUDE.md` | Chỉ khi chạm `common/security/**` |
| AuthService/AuthController + bất biến B33-B34 | `src/main/java/com/erp/manufacturing/module/auth/CLAUDE.md` | Chỉ khi chạm `module/auth/**` |
| Audit Log Architecture | `src/main/java/com/erp/manufacturing/common/audit/CLAUDE.md` | Chỉ khi chạm `common/audit/**` |
| Bất biến RBAC B30-B32, B37 | `src/main/java/com/erp/manufacturing/module/organization/CLAUDE.md` | Chỉ khi chạm `module/organization/**` |
| Bất biến Inventory B1-B6 | `src/main/java/com/erp/manufacturing/module/inventory/CLAUDE.md` | Chỉ khi chạm `module/inventory/**` |
| Bất biến BOM B7-B11 | `src/main/java/com/erp/manufacturing/module/bom/CLAUDE.md` | Chỉ khi chạm `module/bom/**` |
| Bất biến Work Order & Manufacturing Execution B12-B20 | `src/main/java/com/erp/manufacturing/module/workorder/CLAUDE.md` | Chỉ khi chạm `module/workorder/**` |
| Bất biến Planning/MRP B21-B26 | `src/main/java/com/erp/manufacturing/module/planning/CLAUDE.md` | Chỉ khi chạm `module/planning/**` |
| Bất biến Routing B48-B52 + quyết định `MISSING_ROUTING` / Work Center FK (`C2-6`) | `src/main/java/com/erp/manufacturing/module/routing/CLAUDE.md` | Chỉ khi chạm `module/routing/**` |
| Bất biến Sales Order B43-B47 + quyết định `sales`→`planning` | `src/main/java/com/erp/manufacturing/module/sales/CLAUDE.md` | Chỉ khi chạm `module/sales/**` |
| Bất biến Purchasing B27-B29 | `src/main/java/com/erp/manufacturing/module/purchasing/CLAUDE.md` | Chỉ khi chạm `module/purchasing/**` |
| Bất biến UOM B82-B85 + giới hạn `hasPermission`/`GLOBAL` scope (`C2-3`) | `src/main/java/com/erp/manufacturing/module/uom/CLAUDE.md` | Chỉ khi chạm `module/uom/**` |
| Bất biến Work Center B_wc1-B_wc4 (`C2-6`, `B_wc4` ở `C2-7`) | `src/main/java/com/erp/manufacturing/module/workcenter/CLAUDE.md` | Chỉ khi chạm `module/workcenter/**` |
| Bất biến Shift/Work Calendar B_sh1-B_sh2, B_cal1-B_cal2 + quy ước qui-thuộc-ngày ca qua đêm (`C2-7`) | `src/main/java/com/erp/manufacturing/module/shift/CLAUDE.md` | Chỉ khi chạm `module/shift/**` |
| Bất biến Billing B246-B255 (hoá đơn bán hàng `G10c`, thanh toán + tiến độ đơn `G10d`) | `src/main/java/com/erp/manufacturing/module/billing/CLAUDE.md` | Chỉ khi chạm `module/billing/**` |
| Bất biến Delivery B241-B245 (phiếu giao hàng, `G10b`) | `src/main/java/com/erp/manufacturing/module/delivery/CLAUDE.md` | Chỉ khi chạm `module/delivery/**` |
| Bất biến Data Import B142-B148 + hai đường nhập chung bảng (`G3`) | `src/main/java/com/erp/manufacturing/module/dataimport/CLAUDE.md` | Chỉ khi chạm `module/dataimport/**` |
| Bất biến Costing B91-B92 + entry points `ItemStandardCostLookupService` (`P3`) | `src/main/java/com/erp/manufacturing/module/costing/CLAUDE.md` | Chỉ khi chạm `module/costing/**` |
| **Hướng dẫn API cho FE** (envelope, auth, luồng 10 bước, mã lỗi, chỗ lệch spec) | `docs/api-guide-for-frontend.md` | Đọc thủ công — **tài liệu đối ngoại**, viết cho team FE |
| **Session bootstrap cho FE** (decode JWT lấy permissions, workaround profile/plant/scope) | `docs/fe-session-bootstrap.md` | Đọc thủ công — **tài liệu đối ngoại**. Ghi rõ 2 khoảng trống: không có `GET /auth/me`, không có "default plant" |
| **Bộ dữ liệu demo Capstone 2** (namespace `D26-`: 3 tài khoản, kịch bản trình diễn 9 bước, cách dựng lại, các chỗ script cố ý lệch tài liệu nguồn) | `docs/demo-capstone2-guide.md` | Đọc thủ công — cặp với `scripts/demo-capstone2/`. 🔴 Đây là bộ dữ liệu **cho khách hàng xem** (sạch, không bản ghi lỗi); bộ `VIETBIKE` ở hàng dưới là bộ **kiểm thử** (cố ý phủ cả trạng thái xấu) — **đừng trộn hai bộ trên cùng một môi trường**. Sửa dữ liệu thì sửa `scripts/demo-capstone2/catalogue.sh` |
| **Bộ dữ liệu demo** (chủ đề nhà máy xe đạp `VIETBIKE`: tài khoản, cây BOM, ma trận trạng thái, kịch bản thuyết trình, các trạng thái **không** seed được) | `docs/demo-dataset-guide.md` | Đọc thủ công — cặp với `scripts/demo/seed-demo.sh`. 🔴 Sửa dữ liệu demo thì sửa **`scripts/demo/lib/catalogue.sh`**, không sửa các file trong `stages/` |
| **Phản hồi gap Capstone 2** (đối chiếu `BACKEND_CAPSTONE2_API_GAPS.md` của FE với code thật) | `docs/capstone2-api-gap-response.md` | Đọc thủ công — **tài liệu đối ngoại**, thêm ở `C2-0` (2026-08-04). Chứa 3 mục FE báo thiếu mà **đã có**, 2 chỗ FE mô tả nhẹ hơn thực tế (audit diff rỗng, lot-status vs `B62`), và **5 câu hỏi đang chờ FE trả lời** — `C2-1`/`C2-2` bị chặn cho tới khi có câu 1 và 2 |

> 🔴 **`docs/api-guide-for-frontend.md` là tài liệu FE đang dùng để wire API.** Phase nào đổi
> endpoint / DTO / mã lỗi trên luồng sản xuất **phải** cập nhật file đó trong cùng commit — để nó lệch
> code chính là làm FE gắn sai. Nguồn của nó là **code thật**, không phải spec; spec gốc ở
> `docs/fe-spec-omniplant.md`, phần lệch giữa hai bên nằm ở §9 của guide.

> Nội dung không mất — chỉ đổi vị trí. Khi cần tra một bất biến `B<n>` hay một mục `4.x`/`9.x` cụ thể mà
> không chắc nằm ở file nào, grep theo mã bất biến hoặc theo tên class liên quan trong bảng trên.

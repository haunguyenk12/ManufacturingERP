# Giai đoạn 11 — BE trả lời FE: giá thành, chênh lệch và đóng lệnh

**Bản:** v1 · **Ngày:** 27/10/2026 · **Trả lời:** `FE_HANDOFF_GD_11_2026-10-15.md` (v1).

## §0 — Kết luận

- BE đã làm đủ 13 endpoint C1–C13 theo đúng path FE đề xuất: đơn giá giờ theo trung tâm (có phiên bản, lịch sử), cơ sở chi phí chụp lúc phát hành lệnh, giá thành theo lệnh (5 section), checklist 11 mã, tính thử lưu thành lần chạy, **một lệnh nguyên tử chốt giá + đóng lệnh**, bút toán chỉ-giá-trị, giá vốn của phiếu giao.
- **Giá vật tư là giá chuẩn của vật tư tại giây phát hành**, không phải "giá bình quân kho" — kho của hệ thống chỉ biết số lượng, chưa có giá trị (§1, Q4/Q7). Chênh lệch đo được là chênh lệch **lượng + thời gian**, không phải chênh lệch giá mua.
- Breaking change duy nhất: `POST /v1/work-orders/{id}/close` trả `409 CLOSE_BLOCKED` cho lệnh **phát hành sau bản này** (có cơ sở chi phí) — phải đóng bằng `finalize-cost-and-close`. Lệnh cũ đóng như trước.

## §1 — Các quyết định (BE + PO chốt 27/10/2026)

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Giá vật tư = giá chuẩn (`PUT /v1/companies/{C}/items/{I}/standard-cost`) chụp lúc RELEASE; thực tế vật tư = **lượng tiêu thụ × giá đã chụp** | Kho không có cột giá; dựng giá bình quân kho là việc riêng cỡ một giai đoạn |
| D2 | Hệ giá thành G11 là extension **riêng**; `/variance` và `costVariance` cũ **không đổi** | FE Q1. `/variance.costVariance` = giá chuẩn theo vật tư, tức thời; `/production-costs` = giá thành theo trung tâm + thời gian, có chốt. Hai con số "thực tế" khác công thức là có chủ đích |
| D3 | Chỉ định giá **pool cô lập**: (thành phẩm, kho đầu ra) có tồn = 0 lúc phát hành và sau đó chỉ có phiếu nhập/QC/phiếu giao của chính lệnh | FE Q21. Pool lẫn ⇒ `VALUATION_UNRECONCILED` |
| D4 | Thiếu giá/đơn giá **không** chặn phát hành | FE Q8; chỉ chặn chốt giá |
| D5 | Đường `close` cũ trả 409 cho lệnh có cơ sở chi phí; lệnh cũ giữ hành vi cũ | FE R32, Q27 |
| D6 | 3 quyền mới (ADMIN + MANAGER); cost view = `PERM_WORK_ORDER_VARIANCE_READ` **hoặc** `PERM_PRODUCTION_COST_CALCULATE` | FE §1.3 |
| D7 | Tiền là **số JSON** scale 6, trần `NUMERIC(19,6)` | Xem §2.3 |
| D12 | ID ngoài phạm vi trả **403** như toàn hệ thống | Xem §2.3 |

## §2 — Contract

### 2.1 Endpoint (tất cả mới; base `/api`)

| ID | Method + path | Quyền | Ghi chú |
|---|---|---|---|
| C1 | `GET /v1/plants/{plantId}/production-cost-rates?status&search&page&size` | `PERM_COST_RATE_MANAGE` hoặc cost view | Hàng: `workCenterId, plantId, code, name, status, capacityUnits, rateVersion` (0 = chưa có), `rate` (`null` khi chưa đặt) |
| C2 | `PUT /v1/plants/{plantId}/production-cost-rates/{workCenterId}` | `PERM_COST_RATE_MANAGE` | Body `{expectedVersion, laborRate, overheadRate, reason, currency?}` ⇒ `{workCenterId, rateVersion}` |
| C13 | `GET …/production-cost-rates/{workCenterId}/history` | như C1 | Mới nhất trước: `rateId, rateVersion, laborRate, overheadRate, currency, reason, actor{id,displayName}, at` |
| C3 | `GET /v1/plants/{plantId}/production-costs?mode=costs\|close&search&readiness&fromDate&toDate&page&size` | `costs`: cost view; `close`: thêm `PERM_WORK_ORDER_CLOSE` / `_READ` / `PERM_WIP_READ` | `costs` = RELEASED…CLOSED; `close` = COMPLETED/CLOSED. Không có cost view ⇒ `planned`/`actual`/`variance` **vắng mặt** |
| C4 | `GET /v1/work-orders/{id}/production-costs?section=overview\|materials\|operations\|variance\|allocation&page&size` | cost view | Header + `data` (object cho overview/variance, page cho 3 section còn lại). Section viết thường hay hoa đều nhận |
| C5 | `GET /v1/work-orders/{id}/cost-baseline` | cost view | `{workOrderId, baseline, unknownReasons}`; `baseline = null` + `MISSING_BASELINE` cho lệnh cũ |
| C6 | `GET /v1/work-orders/{id}/cost-checklist` | cost view, `PERM_WORK_ORDER_CLOSE`, `_READ`, `PERM_WIP_READ` | 11 `checks[]`. Không có cost view ⇒ `sourceVersion` và `cost` **vắng mặt** (E6) |
| C7 | `POST /v1/work-orders/{id}/cost-runs` | `PERM_PRODUCTION_COST_CALCULATE` | Body `{expectedVersion, expectedSourceVersion}` ⇒ `{workOrderId, runId, runVersion: 1, workOrderVersion}`. **Không** đổi version lệnh |
| C8 | `GET /v1/work-orders/{id}/cost-runs?page&size` | cost view | Mới nhất trước: `runId, runNo (CST-…), runVersion, state, sourceVersion, workOrderVersion, plannedTotal, actualTotal, actor, at, finalizedAt, finalizedBy, closeReason` |
| C9 | `GET /v1/work-orders/{id}/cost-runs/{runId}?section…` | cost view | Như C4 nhưng đọc **snapshot của run** — run đã chốt đọc lại đúng số lúc đóng (R26) |
| C10 | `POST /v1/work-orders/{id}/finalize-cost-and-close` | `PERM_WORK_ORDER_CLOSE` **và** cost view | Body `{expectedVersion, expectedSourceVersion, runId, expectedRunVersion, reason}` ⇒ `{workOrderId, runId, runVersion: 2, workOrderVersion}` |
| C11 | `GET /v1/deliveries/{deliveryId}/production-cost` | quyền đọc phiếu giao **và** cost view | `lines[]{deliveryLineId, lineNo, itemId, lotId, quantity, amount, known, adjustmentIds, unknownReason}` |
| C12 | `GET /v1/work-orders/{id}/cost-runs/{runId}/adjustments?page&size` | cost view | Bút toán chỉ-giá-trị, `quantity` luôn `0` (E9) |

Mọi POST/PUT **bắt buộc** `Idempotency-Key` (thiếu ⇒ `400 MISSING_REQUIRED_FIELD`). Cùng key + cùng body ⇒ cùng kết quả; cùng key khác body ⇒ `409 IDEMPOTENCY_CONFLICT`. Endpoint theo nhà máy (C1, C2, C3, C13) đối chiếu `X-Plant-Id` (lệch ⇒ `409`).

**Phát hành lệnh** (`POST /v1/work-orders/{id}/release`) nay chụp cơ sở chi phí trong cùng transaction — body và response không đổi.

### 2.2 Công thức (FE §3.5, đều có test với số F1–F4 của FE)

- Kế hoạch vật tư từng dòng = `C6(lượng yêu cầu × giá chuẩn)`. Phút kế hoạch = `(setup + run × kế hoạch) / capacityUnits` (12 số lẻ). Nhân công/CPC = `C6(phút × đơn giá / 60)`.
- Thực tế vật tư = giá trị **tiêu thụ** từng dòng cấp: `Vi = C6(Qi × giá)`; hoàn lũy kế `C6(Vi × hoàn lũy kế / Qi)`; lần hoàn làm dòng hết chưa dùng nhận phần dư `Vi − C6(Vi × tiêu thụ / Qi)` (GĐ8 Q37 — F4 ra đúng `.333333` / `.333334`).
- Thực tế thời gian = tổng thời lượng từng lượt báo của công đoạn; lượt xử lý lại tính một lần. Thiếu / rỗng / chồng giờ trong một công đoạn ⇒ `null` + `INVALID_TIME`.
- Phân bổ từ tổng, lũy kế: phiếu nhập (theo `approvedAt` rồi id dạng chuỗi) → phần QC (theo `decidedAt` rồi id; đạt trước loại; phần chờ cuối) → dòng giao (thứ tự ghi sổ). Các phần luôn cộng lại đúng tổng. F1 ra đúng `19166.666667 / 15333.333333`, `11500 / 3833.333334 / 3833.333333 / 15333.333333`, giá vốn `30666.666667` + còn kho `3833.333333` = `34500`.
- `Good = 0` ⇒ `unitCost = null`, `loss = tổng thực tế`, không bút toán kho.
- Readiness: CLOSED (đã chốt) → STALE (run gần nhất khác nguồn) → READY (đủ 11) → INCOMPLETE (tổng kế hoạch hoặc thực tế chưa biết) → BLOCKED.

### 2.3 Lệch so với đề xuất FE

| FE đề xuất | BE làm | Vì sao |
|---|---|---|
| Giá bình quân kho lúc RELEASE (`baseline.materials[].unitCost`) | Giá chuẩn của vật tư lúc RELEASE | Kho chưa có giá (D1). Vật tư chưa có giá chuẩn ⇒ `unitCost = null` + `MISSING_PRICE` |
| Tiền là chuỗi, trần `abs < 10^32` | Số JSON, scale ≤ 6, trần `9 999 999 999 999,999999` | Cột `NUMERIC(19,6)`; nhất quán toàn hệ thống. JS `Number` chính xác đến ~9×10^15 nên với 6 số lẻ an toàn tới ~10^9 đồng — **FE nên đọc tiền bằng thư viện decimal** nếu cần số lớn hơn |
| Đơn vị scale 12 cho giá/phút | Có: giá NUMERIC(25,12), phút NUMERIC(25,12) | — |
| 404 cho ID ngoài phạm vi | **403** `PERMISSION_DENIED` | Như GĐ2/9/10; guard từ chối trước khi service biết bản ghi có tồn tại không |
| `VERSION_CONFLICT`, `INVALID_STATE` | `CONCURRENT_MODIFICATION`, `STATE_CONFLICT` | Mã sẵn có cùng nghĩa |
| `MATERIAL_VALUE_UNRECONCILED`, `EXECUTION_TIME_UNRECONCILED`, `REFERENCE_INVALID`, `COST_VALUE_OVERFLOW` | Không có mã riêng: lý do nằm trong `errors[]` của `CLOSE_BLOCKED` / `COST_DATA_INCOMPLETE` (vd field `MATERIAL` message `MATERIAL_VALUE_UNRECONCILED`; field `COST` message `INVALID_TIME`, `COST_VALUE_OVERFLOW`) | Giữ ít mã; mỗi mã một cách xử lý |
| `source.type` gồm nhiều loại | Hiện trả `WORK_ORDER`, `MATERIAL_ISSUE`, `PRODUCTION_RECEIPT`, `COST_RUN` (hoặc `null`) | Đủ để mở màn hình xử lý; bổ sung khi FE cần loại khác |
| Một bút toán mỗi lô | Bút toán gộp theo **phần QC** (một dòng cho phần còn kho nếu có, một dòng mỗi dòng giao); phần còn kho 0 không ghi | FE Q36 cho phép |
| Lọc `readiness` trước khi phân trang | Có, nhưng phải tính giá thành của mọi lệnh khớp bộ lọc khác rồi mới phân trang | Readiness dẫn xuất từ nguồn sống; chậm với nhà máy có rất nhiều lệnh mở. Không lọc `readiness` thì chỉ tính các lệnh trong trang |
| Giá vốn phiếu giao **sau** khi đóng cho hàng không theo lô | `amount = null`, `unknownReason = NOT_FINALIZED` | Chỉ hàng theo lô truy được về phần đã chốt; hàng giao trước khi đóng (mọi loại) có giá vốn chính xác từ bút toán |

## §3 — Breaking change

| Thay đổi | Ảnh hưởng |
|---|---|
| `POST /v1/work-orders/{id}/close` ⇒ `409 CLOSE_BLOCKED` cho lệnh phát hành **sau** bản này | Lệnh đó phải đóng bằng C10. Lệnh phát hành trước bản này (không có cơ sở chi phí) đóng như cũ |

Không đổi field hay status nào khác. `/variance` không đổi.

## §4 — Mã lỗi

| HTTP | `code` | Khi nào | `errors[].field` |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | rate âm / > 999 999 999 / > 6 số lẻ; lý do không 5–2000; `mode`/`section`/`readiness` lạ; thiếu `expectedSourceVersion` | tên field |
| 400 | `MISSING_REQUIRED_FIELD` | thiếu `Idempotency-Key` | — |
| 403 | `PERMISSION_DENIED` | thiếu quyền, hoặc ID ngoài phạm vi | — |
| 404 | `ENTITY_NOT_FOUND` | trung tâm không thuộc nhà máy của path; run không thuộc lệnh | — |
| 409 | `CONCURRENT_MODIFICATION` | `expectedVersion` (rate hoặc lệnh) / `expectedRunVersion` lệch; hoặc thua khi hai lệnh chốt tranh nhau | `expectedVersion` / `expectedRunVersion` |
| 409 | `COST_SOURCE_STALE` | nguồn đổi kể từ lúc đọc / từ lúc tính run | `expectedSourceVersion` hoặc `runId` |
| 409 | `STATE_CONFLICT` | tính thử khi lệnh chưa phát hành/đã huỷ/đã đóng; chốt khi lệnh chưa `COMPLETED`; run đã chốt | mã kiểm tra `STATE` |
| 409 | `CLOSE_BLOCKED` | checklist vận hành chưa đạt (OPERATIONS/ISSUES/MATERIAL/RESERVATIONS/RECEIPTS/QC); hoặc `close` cũ trên lệnh có cơ sở chi phí | mã kiểm tra, message = lý do |
| 409 | `COST_DATA_INCOMPLETE` | BASELINE / COST chưa đủ | như trên |
| 409 | `VALUATION_UNRECONCILED` | pool lẫn, giao không khớp phần QC | như trên |
| 409 | `IDEMPOTENCY_CONFLICT` | cùng key khác body | — |
| 422 | `RESOURCE_INACTIVE` | đặt đơn giá cho trung tâm ngừng | — |

## §5 — Trả lời Q1–Q36

Q1 Có — extension riêng (D2). · Q2 Có — đơn giá theo trung tâm. · Q3 Scale 6 HALF_UP **có**; trần `NUMERIC(19,6)` thay vì `10^32` (§2.3). · Q4 Có — 12 số lẻ. · Q5 Có — `0..999 999 999`, scale ≤ 6. · Q6 Có — trong object được xem, trường chưa biết là `null` kèm `unknownReasons`; object không được xem thì **vắng mặt**. · Q7 Có — chụp trong transaction RELEASE. · Q8 Có. · Q9 Có. · Q10 Có — đơn giá đã chụp × thời lượng ghi nhận. · Q11 Có. · Q12 Có — chồng giờ trong **một** công đoạn ⇒ chặn (`INVALID_TIME`); không kiểm giữa các lệnh. · Q13 Có — tiêu thụ, còn chưa dùng ⇒ chặn đóng. · Q14 Có — dòng định mức không có phiếu cấp nào ⇒ `null` (`MISSING_CONSUMPTION`). · Q15 Có — luật phần dư §2.2 (cũng là đáp án GĐ8 Q37). · Q16 Có — không sửa phiếu hoàn cũ; giá trị hoàn được **tính** từ sổ, không lưu, nên không có gì để sửa. · Q17 Có. · Q18 Có — hàng loại giữ lượng và giá trị, không tự ghi giảm. · Q19 Có. · Q20 Thứ tự: phiếu nhập `approvedAt` rồi `receiptId` dạng chuỗi; quyết định QC `decidedAt` rồi `decisionId` dạng chuỗi; dòng giao `postedAt` rồi `deliveryId` rồi `lineNo`. · Q21 Có. · Q22 Có — bút toán `stockValueDelta = 0`, `allocatedAmount` = giá vốn, nối `deliveryLineId` + `adjustedMovementId` (ISSUE gốc không đổi). · Q23 Có — một transaction (§6). · Q24 Có — `loss` nằm trong snapshot run cuối, không biến động kho; **không có** "WIP settlement" giá trị trong hệ thống. · Q25 Có — đóng lệnh không đọc hoá đơn/thanh toán. · Q26 Có — giao tiếp được; run cuối không đổi. · Q27 Lệnh không routing / không theo dõi công đoạn: checklist `OPERATIONS` trượt (`NO_OPERATIONS` / `LEGACY_TRACKING`) nên không đóng qua C10; nếu lệnh **không** có cơ sở chi phí thì vẫn đóng được qua `close` cũ. · Q28 Có. · Q29 Có — người tính được tự chốt. · Q30 Có — C1/C13 chỉ trả tham chiếu tối thiểu. · Q31 Có — `cs1-` + 32 hex, không giải mã ra tiền. · Q32 Có — `runVersion` riêng (1 khi preview, 2 khi chốt). · Q33 Có. · Q34 Có — múi giờ nhà máy; nhà máy không có múi giờ hợp lệ thì không lưu được từ GĐ2. · Q35 Không — 403 (§2.3). · Q36 Có — xem §2.3.

## §6 — Đảm bảo của lệnh chốt (C10)

Thứ tự trong **một** transaction: kiểm quyền → replay theo key → lý do → **khoá dòng lệnh** → **kiểm quyền lại dưới khoá** (quyền bị thu hồi lúc đang chờ khoá thì không commit — A39) → version lệnh → run (`PREVIEW`, đúng `expectedRunVersion`) → **tính lại toàn bộ phía server** (không dùng tổng của client hay số đã lưu trong run — A21) → nguồn khớp cả request lẫn run → 11 kiểm tra → ghi snapshot cuối + bút toán + `CLOSED`. Lỗi ở bất kỳ bước nào ⇒ rollback toàn bộ. Hai lệnh chốt cùng version: một `200`, một `409 CONCURRENT_MODIFICATION` (đã đo bằng test tự động hai luồng thật).

Sau `CLOSED`: mọi đường cấp/hoàn/báo sản lượng/nhập/QC đều từ chối (đã như vậy từ trước; checklist bảo đảm không còn phần chờ QC).

## §7 — Bằng chứng

- `mvn -o clean verify`: **2141 unit + 315 integration, 0 lỗi** (trước bản này: 2085 + 312).
- Kịch bản tự động đi qua toàn bộ HTTP + Postgres thật: đặt giá chuẩn + đơn giá → phát hành (baseline 22250; đổi đơn giá sau phát hành không đổi baseline) → cấp, tiêu thụ, báo 480 phút, kết thúc công đoạn → nhập, duyệt, QC → `close` cũ `409 CLOSE_BLOCKED` → checklist `BLOCKED` (`NO_PREVIEW`) → tính thử (version lệnh và biến động kho không đổi) → readiness `READY`, thực tế 92000, đơn giá 9200 → chốt với nguồn sai `409 COST_SOURCE_STALE` → **hai lệnh chốt song song: 200 + 409** → run `FINALIZED`, readiness `CLOSED`, version lệnh +1, một bút toán số lượng 0, giá trị 92000, tồn không đổi.
- Kiểm thử đột biến: 13 đột biến vào công thức, thứ tự, quyền, đường cũ — **13/13 bị test bắt**; hai trong số đó chỉ bị bắt sau khi thêm ca số lẻ (số của F1/F4 tình cờ không phân biệt được hai công thức).

**Chưa nghiệm thu:** chưa chạy smoke trên ứng dụng đang chạy (không có ví dụ request/response copy từ lần gọi live trong bản này); chưa chạy A01–A48 trên môi trường FE; ca lỗi chèn giữa lúc ghi (A40) và thu hồi quyền trong lúc chờ khoá (A39) mới được bảo đảm bằng thiết kế (một transaction, kiểm quyền lại dưới khoá), chưa có test riêng.

## §8 — FE cần làm

**Bắt buộc:** gửi `Idempotency-Key` cho C2/C7/C10 và giữ nguyên key khi thử lại · lấy `expectedSourceVersion` từ C4/C6 **ngay trước** khi tính/chốt · sau mỗi lệnh đọc lại bằng GET (lệnh chỉ trả id + version) · đóng lệnh mới bằng C10, không bằng `close`.

**Nên làm:** đặt giá chuẩn vật tư và đơn giá trung tâm **trước** khi phát hành (thêm sau không hồi tố) · đọc tiền bằng decimal · hiển thị `unknownReasons` bằng nhãn nghiệp vụ.

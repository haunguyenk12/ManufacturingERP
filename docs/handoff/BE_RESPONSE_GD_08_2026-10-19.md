# BE trả lời — Giai đoạn 8: Lệnh sản xuất, cấp vật tư, công đoạn và dở dang

**Trả lời cho:** `FE_HANDOFF_GD_08_2026-10-14.md` (v1, P1) · **Ngày gửi:** 19/10/2026 · **Bản:** v1 — **phần 1/4: nháp lệnh sản xuất (`G8a`)**
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. Giai đoạn 8 phía BE được chia **4 phần** gửi lần lượt: v1 nháp lệnh +
kho từng dòng + lịch (bản này) · v2 cấp vật tư · v3 công đoạn (đầu vào / chuyển giao / xử lý lại / vật tư thực dùng / hoàn thành) · v4 dở dang +
hoàn vật tư. Chưa phải "FE nghiệm thu đạt": task chỉ đóng khi FE chạy A01–A40 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Lệnh sản xuất nay chọn được kho cho từng vật tư và chỉ giữ tồn ở đúng kho đó, mọi lệnh trên lệnh sản xuất bắt buộc gửi version vừa đọc,
lịch được xem trước bằng đúng công thức release sẽ chốt và mang revision khi đã chốt, và huỷ lệnh chưa chạy trả lại hạn ngạch hoạch định đúng một lần.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | WO từ MAKE giữ ID/nguồn/phân bổ; nháp nguồn không sửa được trường bị khoá | 🟡 **Một phần đã có, phần khoá đã làm** | ID/nguồn/phân bổ vốn đã đúng (GĐ6). Mới: đổi `plannedQuantity`/`outputWarehouseId` của WO có nguồn ⇒ `409 SOURCE_LOCKED` theo field (§4.9) |
| R02 | Kho cho từng dòng vật tư; ảnh chụp thiết kế bất biến | 🟡 **Kho từng dòng đã làm**; ảnh chụp vốn đã có | `componentWarehouses` lúc tạo/sửa/plan (§4.1). Ảnh chụp: xem Q5 — chụp lúc **tạo**, không phải lúc plan |
| R03 | Lịch có revision/calendar revision/timeZone/capturedAt + workMinutes; capacity đọc lịch đã chốt | 🟡 **Đã làm** | Release trả `scheduleRevision: 1`, `calendarRevision`, `scheduleTimeZone`, `scheduleCapturedAt`, công đoạn có `workMinutes` (§4.6). Bảng công suất **vốn đã** đọc trực tiếp lịch đã chốt trên công đoạn |
| §3.3 | Xem trước lịch cấp lệnh | 🟡 **Đã làm** | `POST /api/v1/work-orders/{W}/schedule-preview`, chỉ đọc; kết quả trùng **khớp** lịch release chốt sau đó (§4.3, §4.6) |
| R04 | Giữ tồn tự động một phần, đúng kho, `BLOCKED` khi thiếu | 🟡 **Kho đúng đã làm**; một phần + `BLOCKED` vốn đã có | Kho đúng có 15, kho khác 500, cần 20 ⇒ giữ 15, release `409` + `BLOCKED` (§4.4, §4.5) |
| §3.3 | Giải phóng phần chưa cấp | 🟡 **Đã làm** | `POST /api/v1/work-orders/{W}/release-reservations` (§4.7) |
| R06 | Huỷ lệnh chưa chạy: trả giữ tồn + hạn ngạch MAKE/đơn bán **một lần**; có hoạt động/chờ duyệt thì từ chối | 🟡 **Đã làm** | Phiếu cấp chờ duyệt ⇒ `409`; huỷ WO MAKE ⇒ dòng chuyển đổi giữ lại, có `releasedAt` + lý do; huỷ lần hai ⇒ `409` (§4.8, §4.10) |
| R21 (phần WO) | List lệnh có khoảng ngày | 🟡 **Đã làm** | `fromDate`/`toDate` (§2.1) |
| R22 (phần WO) | Version bắt buộc; idempotency cùng key ⇒ cùng kết quả, khác payload ⇒ xung đột | 🟡 **Đã làm** | Thiếu ⇒ `400` field; lệch ⇒ `409 CONCURRENT_MODIFICATION`; gửi lại cùng key ⇒ không chạy lại; khác payload ⇒ `409 IDEMPOTENCY_CONFLICT` (§4.2, §4.7) |
| R07–R09 | Cấp vật tư nhiều dòng / chờ duyệt / duyệt kiểm lại | ⏳ **Phần v2** | — |
| R10–R14 | Công đoạn: đầu vào, xử lý lại, sản lượng cuối, vật tư thực dùng, kết thúc/hoàn thành | ⏳ **Phần v3** | — |
| R15–R17 | Dở dang, hoàn vật tư, liên kết biến động kho | ⏳ **Phần v4** | — |
| R18 | Nguồn cung WO cho MRP theo sản lượng thực | ⏳ **Hoãn có chủ đích** | Xem §8 |
| R19 (giá trị) | Giá trị cấp/dùng/hoàn/dở dang | ⏳ **Hoãn có chủ đích** — trả `null` | Xem §8 |

### Trả lời Q (những câu thuộc phần này)

| Q | Trả lời |
|---|---|
| Q5 | **Khác giả định của FE, không sai.** Hệ thống chụp BOM **và** quy trình lúc **tạo** lệnh (thủ công lẫn MAKE), không phải lúc plan. Sau khi tạo, đổi BOM/quy trình gốc không đổi lệnh. Plan không chụp lại |
| Q6 | **Không gộp.** Mỗi dòng BOM là một dòng vật tư của lệnh với `componentLineId` ổn định; một vật tư có thể xuất hiện ở nhiều dòng. Trần cấp/giữ tính **theo từng dòng**. `componentWarehouses` khoá theo **vật tư** nên áp cho mọi dòng của vật tư đó |
| Q7 | **Có, khoá `plannedQuantity` và `outputWarehouseId`** của lệnh có nguồn hoạch định (`planningProposalId` khác `null`). Gửi lại đúng giá trị hiện tại không bị coi là sửa. **Kho vật tư vẫn sửa được** trên lệnh MAKE: lượt chuyển đổi GĐ6 không chụp kho vật tư, nên không có gì của nguồn cần bảo vệ; dòng của lệnh MAKE mặc định `warehouseId: null` = mọi kho của nhà máy |
| Q12 | **Có** (vốn đã thế). Giữ được phần nào lưu phần đó; release thiếu ⇒ `409` và lệnh chuyển `BLOCKED` |
| Q13 | **Có.** Trạng thái reservation là `ACTIVE` / `CONSUMED` (đã cấp hết) / `RELEASED` / `CANCELLED`; mock `ISSUED` ⇔ `CONSUMED` |
| Q21 | **Có.** Hạn ngạch chuyển đổi trả lại **một lần**, dòng chuyển đổi giữ nguyên làm vết (`releasedAt`, `releaseReason`); phân bổ đơn bán giữ nguyên làm vết và tự thôi tính phủ vì lệnh đã `CANCELLED`. Không tự huỷ đơn bán. Lệnh tạo bằng đường chuyển đổi cũ (trước GĐ6, không có dòng chuyển đổi) huỷ xong **không** trả gì — đường đó không có sổ để trả |
| Q24 | Mọi số lượng lưu `NUMERIC(19,6)`: tối đa **13 chữ số nguyên + 6 chữ số lẻ**. Field mới/đổi của phần này kiểm đúng giới hạn đó (số lẻ thứ 7 ⇒ `400` theo field). Giới hạn 999 999 999 của mock chặt hơn và vẫn hợp lệ |
| Q25 | **Khác mock.** Nhu cầu vật tư của lệnh = `kế hoạch × quantityPer × (1 + scrapRate)` (nhân, không chia `1 − scrapRate`), lưu làm tròn **nửa lên** ở 6 chữ số lẻ, không cộng gộp theo vật tư (xem Q6). Ví dụ thật: kế hoạch 10, `quantityPer` 2, hao hụt 0 ⇒ `20.000000`. ⚠️ Response của lệnh **tạo** in số chưa làm tròn (`20.000000000000`), đọc lại in `20.000000` — có từ trước GĐ8, giá trị lưu là một |
| Q27 | **Có.** Release dùng lịch làm việc **hiện tại** với quy trình đã chụp; lịch đã chốt nằm trên từng công đoạn (`plannedStartAt`/`plannedEndAt`) và bảng công suất đọc thẳng từ đó, không phụ thuộc ai mở màn hình nào. Đổi lịch làm việc sau release **không** viết lại lịch đã chốt |
| Q28 | **Có, dài hơn 24 giờ:** key của lệnh trên lệnh sản xuất **không hết hạn**. Gửi lại cùng key + cùng payload ⇒ trả **trạng thái hiện tại** (không chạy lại, không tăng version); khác payload ⇒ `409 IDEMPOTENCY_CONFLICT`. Quyền được kiểm lại trước khi trả |
| Q29 | **Có.** Mọi response có header `X-Trace-Id` |
| Q30 | ⚠️ **Một phần.** Endpoint dạng `/work-orders/{id}/...` với id **không tồn tại hoặc ngoài phạm vi** trả **`403 PERMISSION_DENIED`**, không phải 404 — kiểm quyền chạy trước và không tìm được bản ghi để suy phạm vi. Không lộ dữ liệu, nhưng FE không phân biệt được "không có" với "không có quyền". Đặc điểm có sẵn toàn hệ thống, không đổi trong phần này |
| Q32 | **Có.** Ngày nghiệp vụ (vd `fromDate`/`toDate`) tính theo múi giờ của **nhà máy** do server biết; không phụ thuộc trình duyệt |
| Q36 | **Có** (vốn đã thế): chứng từ đã ghi không có `PATCH`/`DELETE` |
| Q1–Q4, Q8–Q11, Q14–Q19, Q22, Q23, Q26, Q31, Q33–Q35, Q37 | Trả lời ở bản v2–v4 cùng phần code tương ứng |
| Q20 | **Hoãn** cùng R18 (§8) |

---

## §1 — Nguyên nhân thật

Không có lỗi FE báo — đây là phần **chưa có**. Ba điều ảnh hưởng tới FE, theo thứ tự:

1. **Mọi lệnh trên lệnh sản xuất nay bắt buộc gửi version** (sửa nháp, plan, release, huỷ, giữ tồn tự động/tay, bỏ giữ, giải phóng phần chưa cấp).
   Adapter hiện tại gọi plan/release/reserve **không body** sẽ nhận `400` ngay khi BE deploy — đây là breaking change lớn nhất của phần này (§3).
2. **Giữ tồn theo kho của dòng.** Dòng có kho chỉ lấy hàng ở kho đó, dù kho khác cùng nhà máy còn hàng. Dòng không có kho (mọi lệnh tạo trước
   ngày nay, lệnh MAKE, lệnh không chọn) giữ nguyên hành vi cũ: lấy ở mọi kho của nhà máy.
3. **Nghiệm thu qua HTTP thật không tìm ra lỗi nào** ở phần này; nó xác nhận lịch xem trước và lịch release chốt trùng nhau từng giây (§7).

---

## §2 — Thay đổi contract

### 2.1 Endpoint

| Method | Path | Mới / đổi | Quyền |
|---|---|---|---|
| POST | `/api/v1/plants/{P}/work-orders` | **Đổi (additive):** body thêm `componentWarehouses?` | `PERM_WORK_ORDER_MANAGE` (nhà máy) |
| GET | `/api/v1/plants/{P}/work-orders?...&fromDate&toDate` | **Đổi (additive):** `fromDate`/`toDate` dạng `YYYY-MM-DD`, ngày theo múi giờ nhà máy trên `createdAt`, gồm cả hai đầu | `PERM_WORK_ORDER_READ` |
| PATCH | `/api/v1/work-orders/{W}` | **Đổi:** `expectedVersion` **bắt buộc**; thêm `componentWarehouses?`; khoá nguồn (Q7); header `Idempotency-Key` tuỳ chọn | `PERM_WORK_ORDER_MANAGE` |
| POST | `/api/v1/work-orders/{W}/schedule-preview` | **Mới** — chỉ đọc; body tuỳ chọn `{expectedVersion?, plannedStartAt?, plannedQuantity?}` | `PERM_WORK_ORDER_READ` |
| POST | `/api/v1/work-orders/{W}/plan` | **Đổi:** body `{expectedWorkOrderVersion, componentWarehouses?}` **bắt buộc** | `PERM_WORK_ORDER_MANAGE` |
| POST | `/api/v1/work-orders/{W}/release` | **Đổi:** body `{expectedWorkOrderVersion}` **bắt buộc** | `PERM_WORK_ORDER_MANAGE` |
| POST | `/api/v1/work-orders/{W}/cancel` | **Đổi:** body `{reason, expectedWorkOrderVersion}` — version **bắt buộc**; trả hạn ngạch; chặn khi còn phiếu cấp chờ duyệt | `PERM_WORK_ORDER_MANAGE` |
| POST | `/api/v1/work-orders/{W}/reserve` | **Đổi:** body `{expectedWorkOrderVersion}` **bắt buộc**; chỉ kho của dòng | `PERM_MATERIAL_RESERVATION_MANAGE` |
| POST | `/api/v1/work-orders/{W}/material-reservations` | **Đổi:** body thêm `expectedWorkOrderVersion` **bắt buộc**; kho phải là kho của dòng (nếu dòng có kho) | `PERM_MATERIAL_RESERVATION_MANAGE` |
| DELETE | `/api/v1/work-orders/{W}/material-reservations/{R}?expectedWorkOrderVersion=` | **Đổi:** query **bắt buộc** | `PERM_MATERIAL_RESERVATION_MANAGE` |
| POST | `/api/v1/work-orders/{W}/release-reservations` | **Mới** — body `{expectedWorkOrderVersion}` | `PERM_MATERIAL_RESERVATION_MANAGE` |

Mọi lệnh **có thay đổi** ở bảng trên nhận header `Idempotency-Key` tuỳ chọn (Q28). Tên field version: `expectedVersion` cho PATCH và xem trước
lịch (theo đề xuất §3.3 của FE), `expectedWorkOrderVersion` cho mọi lệnh khác.

🔴 **Không đổi trong phần này** (không cần version): `POST .../close`, `POST .../component-issues`, `POST .../output-completions`,
`POST /api/v1/material-issues` (dạng phẳng), `/wip-transactions`, phiếu cấp nhiều dòng + duyệt/từ chối (phần v2 sẽ thêm), báo sản lượng (phần v3).
Chúng vẫn làm version của lệnh tăng khi đổi lệnh, nên lệnh gửi version cũ sau đó vẫn nhận `409` đúng.

### 2.2 Field mới trên response

| Field | Kiểu | Nullable? | Nguồn giá trị | Ví dụ thật |
|---|---|---|---|---|
| `WorkOrder.version` | integer | không | version của lệnh — gửi lại làm `expectedVersion` / `expectedWorkOrderVersion` | `0` lúc tạo, `1` sau PATCH |
| `WorkOrder.scheduleRevision` | integer | **có** — `null` khi chưa chốt lịch | +1 mỗi lần release và mỗi lần điều chỉnh lịch tay | `1` |
| `WorkOrder.calendarRevision` | string(16) | có — `null` khi không công đoạn nào dùng lịch | dấu vân tay nội dung lịch làm việc đã dùng (ca, nghỉ, ngày nghỉ) lúc chốt; trùng giá trị xem trước nếu lịch không đổi | `"f57ac6a262b51678"` |
| `WorkOrder.scheduleTimeZone` | string | có | múi giờ nhà máy lúc chốt | `"Asia/Ho_Chi_Minh"` |
| `WorkOrder.scheduleCapturedAt` | timestamp | có | thời điểm chốt (độ chính xác µs, ổn định giữa lệnh và lần đọc lại) | `"2026-10-19T14:08:13.713878Z"` |
| `componentLines[].warehouseId` / `warehouseCode` | UUID / string | **có** — `null` = mọi kho của nhà máy | kho đã chọn cho vật tư của dòng | `"RAW-210812"` |
| `operations[].workMinutes` | integer | không | `ceil(setup + run × kế hoạch)` — **phút làm việc**, không phải giờ đồng hồ; khung `plannedStartAt→plannedEndAt` có thể dài hơn vì băng qua giờ nghỉ | `90` (30 + 6 × 10) |
| `MaterialReservation.workOrderVersion` | integer | có (`null` chỉ ở đường đọc cũ không có lệnh) | version của lệnh **sau** lệnh giữ tồn này | `3` |

Response xem trước lịch: `{workOrderId, version, timeZone, calendarRevision, quantity, plannedStartAt, rows[], warnings[], generatedAt}`;
mỗi `rows[]`: `{workOrderOperationId, sequence, name, workCenterId, workCenterCode, startsAt, endsAt, workMinutes, onCalendar, overlappingOperations}`;
mỗi `warnings[]`: `{code, workOrderOperationId, message}` với `code` ∈ `NO_ROUTING` / `NO_WORK_CENTER` / `NO_CALENDAR` / `OVERLAP`.
`overlappingOperations` = số công đoạn của **lệnh khác** đã chốt lịch trên cùng trung tâm trong khung đó — chỉ cảnh báo, không chặn.

Response giải phóng phần chưa cấp: `{workOrderId, workOrderVersion, releasedReservationCount, lines[]{reservationId, componentLineId, itemId, warehouseId, lotId, releasedQuantity}}`.

**Công thức lịch** (xem trước và release dùng chung): công đoạn chạy nối tiếp theo `sequence`; công đoạn đầu bắt đầu ở `plannedStartAt` (của body
xem trước, nếu không thì của lệnh, nếu không thì "bây giờ"); mỗi công đoạn cần `workMinutes` phút làm việc, đi theo lịch của trung tâm (bỏ giờ
nghỉ/ngoài ca); trung tâm không có lịch ⇒ giờ liên tục + cảnh báo `NO_CALENDAR`. **Không** chia cho số máy của trung tâm (khác màn xem trước công
suất nhà máy GĐ5). Ví dụ thật: bắt đầu 08:00 thứ Hai (giờ VN), ca 08:00–17:00 nghỉ 12:00–13:00, cắt 90 phút ⇒ 08:00→09:30; hàn 30 phút ⇒ 09:30→10:00.

---

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `PATCH /work-orders/{W}` | không gửi `expectedVersion` | `200` | `400 VALIDATION_ERROR` field `expectedVersion` | **Bắt buộc mới** | gửi `version` vừa đọc |
| `POST .../plan`, `.../release`, `.../reserve` | không body / không version | `200`/`201` | `400 VALIDATION_ERROR` field `expectedWorkOrderVersion` | **Bắt buộc mới** | gửi body `{expectedWorkOrderVersion}` |
| `POST .../cancel` | có lý do, không version | `200` | `400` field `expectedWorkOrderVersion` (thiếu lý do vẫn `400 APPROVAL_REASON_REQUIRED` như cũ, kiểm trước) | **Bắt buộc mới** | thêm version vào body |
| `POST .../material-reservations` | không version | `201` | `400` field `expectedWorkOrderVersion` | **Bắt buộc mới** | thêm vào body |
| `DELETE .../material-reservations/{R}` | không query | `200` | `400` field `expectedWorkOrderVersion` | **Bắt buộc mới** | thêm query |
| mọi lệnh trên | version cũ | `200` | `409 CONCURRENT_MODIFICATION` | **Bắt buộc mới** | đọc lại lệnh rồi gửi lại với key mới |
| `POST .../cancel` | còn phiếu cấp `PENDING_APPROVAL` | `200` | `409 STATE_CONFLICT` | **Đổi mã** (chặn mới) | từ chối/huỷ phiếu chờ trước |
| `POST .../reserve` | dòng có kho, hàng chỉ có ở kho khác | giữ ở kho khác | không giữ, lệnh vẫn thiếu | **Đổi hành vi** | không — chỉ xảy ra khi FE chọn kho |
| `POST .../material-reservations` | dòng có kho, gửi kho khác | `201` | `422 RESOURCE_SCOPE_MISMATCH` field `warehouseId` | **Đổi mã** (chặn mới) | chọn đúng kho của dòng |
| `PATCH /work-orders/{W}` | lệnh có nguồn, đổi `plannedQuantity`/`outputWarehouseId` | `200` | `409 SOURCE_LOCKED` theo field | **Đổi mã** (chặn mới) | khoá hai ô này trên form lệnh MAKE |
| `POST /plants/{P}/work-orders`, `PATCH` | `plannedQuantity` có chữ số lẻ thứ 7 | lưu (làm tròn) | `400` field `plannedQuantity` | **Đổi mã** | giới hạn 6 chữ số lẻ |
| `POST .../material-reservations` | `quantity` có chữ số lẻ thứ 7 | lưu | `400` field `quantity` | **Đổi mã** | như trên |
| `POST .../cancel` | lệnh MAKE chưa chạy | hạn ngạch chuyển đổi bị giữ mãi | được trả lại; run tăng `conversionVersion` | **Nới lỏng** | đọc lại run trước khi chuyển đổi tiếp |

Additive (không phá): 2 endpoint mới, `componentWarehouses`, `fromDate`/`toDate`, các field ở §2.2.

**Thứ tự deploy: FE trước hoặc cùng lúc.** BE **không** có giai đoạn nhận cả hai dạng — sau khi BE deploy, adapter cũ gọi plan/release/reserve/
huỷ không có version sẽ nhận `400`. FE có thể gửi sẵn các field version ngay bây giờ: BE bản cũ bỏ qua field lạ (không lỗi).

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Môi trường dev, cổng 8090, 19/10/2026 ~21:08 giờ VN. Token che. UUID là bản ghi thật của lượt chạy `210812`.

### 4.1 Tạo nháp với kho cho vật tư

```http
POST /api/v1/plants/ec5f69a4-a0ad-438b-b173-8ef71aab9ed5/work-orders
Authorization: Bearer <REDACTED>
Content-Type: application/json
```
```json
{"workOrderNo": "WO-210812", "productItemId": "39f4478c-1ee5-44af-a1eb-541c4d465f9e", "outputWarehouseId": "b9c9e4b0-9239-47c8-ac21-737efe2d128b", "plannedQuantity": 10, "plannedStartAt": "2026-10-26T01:00:00Z", "componentWarehouses": [{"itemId": "036e679b-c163-4030-ba65-db81072d8061", "warehouseId": "11d24262-7470-4f84-8951-1f0655a44932"}]}
```
`201`, `X-Trace-Id: 2d3d8e7ec98448bd` — trích các phần liên quan của response (nguyên văn):
```json
{"code":"SUCCESS","result":{"workOrderId":"917d4c44-b88f-45c6-9859-62c843e53d5e", "...":"...", "status":"DRAFT","plannedStartAt":"2026-10-26T01:00:00Z",
 "componentLines":[{"componentLineId":"2571466d-8ee7-425c-a145-1b201884d12f","bomLineId":"a35bc8d6-e93b-411b-92c5-300fee9ccd8e","lineNo":10,"componentItemId":"036e679b-c163-4030-ba65-db81072d8061","componentItemCode":"RM-210812","componentItemName":"Raw material","uom":"KG","quantityPer":2.000000,"scrapRate":0.000000,"requiredQuantity":20.000000000000,"reservedQuantity":0,"issuedQuantity":0,"remainingQuantity":20.000000000000,"warehouseId":"11d24262-7470-4f84-8951-1f0655a44932","warehouseCode":"RAW-210812"}],
 "operations":[{"workOrderOperationId":"76a6b790-38e1-4dbf-8c18-523284405787","sourceRoutingOperationId":"ee5ffef4-c69c-4770-a35b-992017c96154","sequence":10,"name":"Cut","workCenterCode":"WC-210812","workCenterId":"ff2af4c9-9077-415a-9cc4-4cbb69b77b66","setupMinutes":30.000000,"runMinutesPerUnit":6.000000,"plannedStartAt":null,"plannedEndAt":null,"version":0,"workMinutes":90}, "..."],
 "version":0,"scheduleRevision":null,"calendarRevision":null,"scheduleTimeZone":null,"scheduleCapturedAt":null},"message":"Created successfully"}
```
(`"...":"..."` là chỗ tôi lược các field không đổi của DTO lệnh sản xuất để file gọn — response thật trả đủ.)

### 4.2 Version: thiếu và lệch

```http
PATCH /api/v1/work-orders/917d4c44-b88f-45c6-9859-62c843e53d5e
```
```json
{"notes": "x"}
```
`400`, `X-Trace-Id: 3cfa325711084063`
```json
{"code":"VALIDATION_ERROR","message":"expectedVersion: must not be null","errors":[{"field":"expectedVersion","message":"must not be null"}]}
```
Cùng path, body `{"notes": "x", "expectedVersion": 5}` ⇒ `409`, `X-Trace-Id: 1b4786ea5a654db9`
```json
{"code":"CONCURRENT_MODIFICATION","message":"Work order version is 0, not 5; reload it"}
```

### 4.3 Xem trước lịch

```http
POST /api/v1/work-orders/917d4c44-b88f-45c6-9859-62c843e53d5e/schedule-preview
```
```json
{}
```
`200`, `X-Trace-Id: e22dd74012284289`
```json
{"code":"SUCCESS","result":{"workOrderId":"917d4c44-b88f-45c6-9859-62c843e53d5e","version":1,"timeZone":"Asia/Ho_Chi_Minh","calendarRevision":"f57ac6a262b51678","quantity":10.000000,"plannedStartAt":"2026-10-26T01:00:00Z","rows":[{"workOrderOperationId":"76a6b790-38e1-4dbf-8c18-523284405787","sequence":10,"name":"Cut","workCenterId":"ff2af4c9-9077-415a-9cc4-4cbb69b77b66","workCenterCode":"WC-210812","startsAt":"2026-10-26T01:00:00Z","endsAt":"2026-10-26T02:30:00Z","workMinutes":90,"onCalendar":true,"overlappingOperations":0},{"workOrderOperationId":"0fbdb7e0-fa92-4630-9291-42390640756f","sequence":20,"name":"Weld","workCenterId":"ff2af4c9-9077-415a-9cc4-4cbb69b77b66","workCenterCode":"WC-210812","startsAt":"2026-10-26T02:30:00Z","endsAt":"2026-10-26T03:00:00Z","workMinutes":30,"onCalendar":true,"overlappingOperations":0}],"warnings":[],"generatedAt":"2026-10-19T14:08:13.375278Z"},"message":"OK"}
```
Không ghi gì: đọc lại lệnh ngay sau đó, `version` vẫn `1`, công đoạn vẫn chưa có giờ. Lệnh đã `RELEASED`/`IN_PROGRESS`/… ⇒ `409 STATE_CONFLICT`.

### 4.4 Plan rồi giữ tồn tự động đúng kho

`POST /api/v1/work-orders/917d4c44-b88f-45c6-9859-62c843e53d5e/plan` body `{"expectedWorkOrderVersion": 1}` ⇒ `200`, `status: "PLANNED"`, `version: 2`
(`X-Trace-Id: ff8ea10fb7b94f10`).

Kho của dòng (`RAW-210812`) có 15 KG, một kho khác cùng nhà máy có 500 KG, cần 20 KG:
```http
POST /api/v1/work-orders/917d4c44-b88f-45c6-9859-62c843e53d5e/reserve
```
```json
{"expectedWorkOrderVersion": 2}
```
`201`, `X-Trace-Id: be6a470da9784143`
```json
{"code":"SUCCESS","result":[{"reservationId":"1f397f55-1d5a-4854-9daa-be1c8334cdb2","workOrderId":"917d4c44-b88f-45c6-9859-62c843e53d5e","componentLineId":"2571466d-8ee7-425c-a145-1b201884d12f","itemId":"036e679b-c163-4030-ba65-db81072d8061","itemSku":"RM-210812","warehouseId":"11d24262-7470-4f84-8951-1f0655a44932","warehouseCode":"RAW-210812","lotId":null,"lotNumber":null,"quantity":15.000000,"consumedQuantity":0,"remainingQuantity":15.000000,"status":"ACTIVE","createdAt":"2026-10-19T14:08:13.520480100Z","updatedAt":"2026-10-19T14:08:13.520480100Z","workOrderVersion":3}],"message":"Created successfully"}
```

### 4.5 Release thiếu ⇒ `BLOCKED`; giữ tay từ kho sai

`POST .../release` body `{"expectedWorkOrderVersion": 3}` ⇒ `409`, `X-Trace-Id: e2160597d830499a`
```json
{"code":"STATE_CONFLICT","message":"Cannot release: 1 component(s) not fully reserved"}
```
Đọc lại: `status: "BLOCKED"`, `version: 4` (ghi `BLOCKED` cũng tăng version — **luôn đọc lại sau release bị từ chối**).

```http
POST /api/v1/work-orders/917d4c44-b88f-45c6-9859-62c843e53d5e/material-reservations
```
```json
{"componentLineId": "2571466d-8ee7-425c-a145-1b201884d12f", "warehouseId": "fe4be702-e0ab-4af2-a83a-49dd4e117f58", "quantity": 5, "expectedWorkOrderVersion": 4}
```
`422`, `X-Trace-Id: 148b5be030b443df`
```json
{"code":"RESOURCE_SCOPE_MISMATCH","message":"warehouseId: the component is reserved from warehouse RAW-210812 only","errors":[{"field":"warehouseId","message":"the component is reserved from warehouse RAW-210812 only"}]}
```

### 4.6 Nhập thêm 5 vào đúng kho, giữ nốt, release chốt lịch

Giữ tự động lần hai ⇒ thêm 5 KG từ `RAW-210812`, `workOrderVersion: 5` (`X-Trace-Id: 0b18b59840584709`). Rồi:
```http
POST /api/v1/work-orders/917d4c44-b88f-45c6-9859-62c843e53d5e/release
```
```json
{"expectedWorkOrderVersion": 5}
```
`200`, `X-Trace-Id: a7ebb54b9c8345ea` — trích:
```json
{"status":"RELEASED","releasedAt":"2026-10-19T14:08:13.713878200Z",
 "operations":[{"workOrderOperationId":"76a6b790-38e1-4dbf-8c18-523284405787","sequence":10,"name":"Cut","plannedStartAt":"2026-10-26T01:00:00Z","plannedEndAt":"2026-10-26T02:30:00Z","version":1,"workMinutes":90},
               {"workOrderOperationId":"0fbdb7e0-fa92-4630-9291-42390640756f","sequence":20,"name":"Weld","plannedStartAt":"2026-10-26T02:30:00Z","plannedEndAt":"2026-10-26T03:00:00Z","version":1,"workMinutes":30}],
 "version":6,"scheduleRevision":1,"calendarRevision":"f57ac6a262b51678","scheduleTimeZone":"Asia/Ho_Chi_Minh","scheduleCapturedAt":"2026-10-19T14:08:13.713878Z"}
```
Lịch chốt trùng từng giây với §4.3; `calendarRevision` trùng giá trị xem trước (lịch không đổi giữa hai lần). `scheduleCapturedAt` đọc lại không đổi.

### 4.7 Giải phóng phần chưa cấp + `Idempotency-Key`

```http
POST /api/v1/work-orders/917d4c44-b88f-45c6-9859-62c843e53d5e/release-reservations
Idempotency-Key: 53e3d729-883f-40c4-9b7e-d01d7d38526d
```
```json
{"expectedWorkOrderVersion": 6}
```
`200`, `X-Trace-Id: 1c7e25ab45224c5c`
```json
{"code":"SUCCESS","result":{"workOrderId":"917d4c44-b88f-45c6-9859-62c843e53d5e","workOrderVersion":7,"releasedReservationCount":2,"lines":[{"reservationId":"501f993c-96ac-4a90-99f0-be149d86d0ea","componentLineId":"2571466d-8ee7-425c-a145-1b201884d12f","itemId":"036e679b-c163-4030-ba65-db81072d8061","warehouseId":"11d24262-7470-4f84-8951-1f0655a44932","lotId":null,"releasedQuantity":5.000000},{"reservationId":"1f397f55-1d5a-4854-9daa-be1c8334cdb2","componentLineId":"2571466d-8ee7-425c-a145-1b201884d12f","itemId":"036e679b-c163-4030-ba65-db81072d8061","warehouseId":"11d24262-7470-4f84-8951-1f0655a44932","lotId":null,"releasedQuantity":15.000000}]},"message":"OK"}
```
Gửi lại **cùng key, cùng body** ⇒ `200`, `releasedReservationCount: 0`, `lines: []`, `workOrderVersion: 7` (không chạy lại). Cùng key, body
`{"expectedWorkOrderVersion": 999}` ⇒ `409`, `X-Trace-Id: 21f938bfd1984c6b`:
```json
{"code":"IDEMPOTENCY_CONFLICT","message":"Idempotency-Key was already used with a different payload"}
```
Tồn kho sau đó: kho `RAW-210812` tồn 20, giữ 0. Không có dòng biến động kho nào (chỉ phần giữ đổi).

### 4.8 Huỷ bị chặn bởi phiếu cấp chờ duyệt

Có một phiếu cấp vượt BOM đang `PENDING_APPROVAL`:
```http
POST /api/v1/work-orders/917d4c44-b88f-45c6-9859-62c843e53d5e/cancel
```
```json
{"reason": "Smoke cancel", "expectedWorkOrderVersion": 7}
```
`409`, `X-Trace-Id: 8ae7321a15114663`
```json
{"code":"STATE_CONFLICT","message":"Work order with a material issue pending approval cannot be cancelled; reject or cancel it first"}
```
Từ chối phiếu chờ rồi huỷ lại ⇒ `200 CANCELLED` (`X-Trace-Id: f1dd135202194d08`).

### 4.9 Lệnh MAKE: khoá nguồn

Lệnh `MRP-7B8B0C5ED00A` sinh từ chuyển đổi run thương mại:
```http
PATCH /api/v1/work-orders/748c9b2e-7133-4b55-a876-187c4bd6bc4d
```
```json
{"plannedQuantity": 3, "expectedVersion": 1}
```
`409`, `X-Trace-Id: 02c39291bcae450a`
```json
{"code":"SOURCE_LOCKED","message":"plannedQuantity: comes from planning and cannot be changed","errors":[{"field":"plannedQuantity","message":"comes from planning and cannot be changed"}]}
```
Sửa `notes` trên cùng lệnh ⇒ `200`.

### 4.10 Huỷ lệnh MAKE trả hạn ngạch một lần

`POST /api/v1/work-orders/748c9b2e-7133-4b55-a876-187c4bd6bc4d/cancel` body `{"reason": "Customer withdrew", "expectedWorkOrderVersion": 2}` ⇒ `200 CANCELLED`
(`X-Trace-Id: 3c1240e38857414f`). Rồi `GET /api/v1/planning-runs/d9b4e4ee-532d-4209-a598-8ad26d6ec717/conversions` (`X-Trace-Id: c884ec1cb9414cca`):
```json
{"code":"SUCCESS","result":{"content":[{"conversionId":"1014fedf-47d2-430a-8882-4b0658db2e5b","conversionBatchId":"876908cc-9dc6-41e5-9f24-82eaea6b7f89","mrpRunId":"d9b4e4ee-532d-4209-a598-8ad26d6ec717","requirementLineId":"83c1ab82-cce5-4b56-a128-324ffbf4dde9","supplySuggestionId":"84cdcd6d-7257-4e5b-ae54-10fd6e2dfae4","entryNo":1,"quantity":10.000000,"targetType":"WORK_ORDER","targetId":"748c9b2e-7133-4b55-a876-187c4bd6bc4d","targetLineId":null,"targetCode":"MRP-7B8B0C5ED00A","conversionVersion":1,"createdAt":"2026-10-19T14:08:14.814594Z","createdBy":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","releasedAt":"2026-10-19T14:08:14.975820Z","releaseReason":"Customer withdrew"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"first":true,"last":true},"message":"OK"}
```
`conversionVersion` của run tăng một (1 → 2), dòng nhu cầu về `convertedQuantity: 0` (chuyển đổi lại được). Huỷ lần hai ⇒ `409 STATE_CONFLICT`
(`X-Trace-Id: daffb0768acb4999`), không trả hạn ngạch lần nữa.

---

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — cùng một envelope:

```json
{
  "code":    "SUCCESS",
  "result":  { },
  "message": "OK",
  "errors":  [ { "field": "quantity", "message": "must be greater than 0" } ]
}
```
`code` là tên hằng, FE rẽ nhánh theo nó. `errors` chỉ có khi lỗi theo field. Danh sách: `result` = `{content, page, size, totalElements, totalPages, first, last}`.

| Tình huống | HTTP | `code` | `errors[]`? |
|---|---|---|---|
| thiếu version | 400 | `VALIDATION_ERROR` | ✅ `expectedVersion` / `expectedWorkOrderVersion` |
| version lệch | 409 | `CONCURRENT_MODIFICATION` | — |
| cùng `Idempotency-Key`, khác payload | 409 | `IDEMPOTENCY_CONFLICT` | — |
| `componentWarehouses` có vật tư không thuộc lệnh / trùng | 400 | `VALIDATION_ERROR` | ✅ `componentWarehouses[i].itemId` |
| kho không thuộc nhà máy / không tồn tại / ngừng | 422 / 404 / 422 | `RESOURCE_SCOPE_MISMATCH` / `ENTITY_NOT_FOUND` / `RESOURCE_INACTIVE` | — |
| đổi kho của vật tư đang được giữ ở kho khác | 409 | `STATE_CONFLICT` | — |
| giữ tay từ kho khác kho của dòng | 422 | `RESOURCE_SCOPE_MISMATCH` | ✅ `warehouseId` |
| sửa trường bị khoá của lệnh MAKE | 409 | `SOURCE_LOCKED` | ✅ field bị khoá |
| release thiếu giữ tồn | 409 | `STATE_CONFLICT` (lệnh thành `BLOCKED`) | — |
| huỷ khi còn phiếu cấp chờ duyệt / đã cấp / đã báo sản lượng / trạng thái sai | 409 | `STATE_CONFLICT` | — |
| xem trước lịch của lệnh đã chốt lịch | 409 | `STATE_CONFLICT` | — |
| lịch làm việc không có giờ làm trong 10 năm (cả xem trước và release) | 422 | `OPERATION_NOT_ALLOWED` | — |
| `fromDate` sai dạng / `toDate` trước `fromDate` | 400 | `VALIDATION_ERROR` | ✅ `toDate` (trường hợp sau) |
| giải phóng phần chưa cấp trên lệnh `CANCELLED`/`CLOSED` | 409 | `STATE_CONFLICT` | — |

Mã FE đề xuất ánh xạ: `VERSION_CONFLICT` → `CONCURRENT_MODIFICATION`; `INVALID_STATE` / `WORK_ORDER_HAS_ACTIVITY` → `STATE_CONFLICT`;
`WORK_ORDER_SOURCE_LOCKED` → `SOURCE_LOCKED`; `MATERIAL_NOT_READY` → `STATE_CONFLICT` (release); `MATERIAL_RESERVATION_INVALID` (sai kho) →
`RESOURCE_SCOPE_MISMATCH`; `COMPONENT_WAREHOUSE_REQUIRED` → **không có** (dòng không chọn kho là hợp lệ, xem §6); `PRODUCTION_SCHEDULE_UNAVAILABLE`
→ `OPERATION_NOT_ALLOWED` (422) như trên; `NOT_FOUND` → `ENTITY_NOT_FOUND` (nhưng xem Q30).

---

## §6 — Quyền, phạm vi và giới hạn đã biết

**Không quyền mới** trong phần này. Quyền dùng như §2.1; `ADMIN`/`MANAGER` đã có cả ba (`PERM_WORK_ORDER_READ`/`_MANAGE`,
`PERM_MATERIAL_RESERVATION_MANAGE`); `OPERATOR` có `PERM_WORK_ORDER_READ` (xem trước lịch được) nhưng không có quyền sửa lệnh.

Giới hạn đã biết:

1. **Chọn kho vật tư là tuỳ chọn.** Dòng không có kho giữ tồn ở mọi kho của nhà máy (hành vi cũ). BE **không** trả `COMPONENT_WAREHOUSE_REQUIRED`
   khi plan — nếu FE muốn bắt buộc chọn kho thì chặn trên form.
2. **Lệnh MAKE không có sẵn kho vật tư** (chuyển đổi không chụp) — dòng của chúng `warehouseId: null` cho tới khi người dùng chọn.
3. **Lệnh không có version:** đóng lệnh (`/close`) và các đường cũ ở §2.1 chưa nhận version.
4. **Release bị từ chối vẫn tăng version** (ghi `BLOCKED`), nên version FE đang cầm sau `409` release là version cũ — đọc lại lệnh.
5. **Replay giữ tồn tự động** trả danh sách reservation `ACTIVE` **hiện tại** của lệnh, không phải đúng danh sách lần đầu tạo.
6. **id không tồn tại / ngoài phạm vi trên `/work-orders/{id}/...` trả `403`** (Q30).
7. **Độ chính xác thời gian:** `createdAt`/`updatedAt`/`releasedAt` trong response của chính lệnh vừa ghi có thể in tới 100 ns
   (`...520480100Z`), đọc lại in µs (`...520480Z`) — có từ trước, toàn hệ thống. `scheduleCapturedAt` thì đã ổn định.
8. **Số lẻ trên response của lệnh tạo** (`requiredQuantity: 20.000000000000`) — xem Q25.

---

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — **1785 case unit + 256 case IT, failures = 0, errors = 0** (trước phần này: 1749 + 253) |
| Kiểm tra đột biến | 16 lỗi cố ý cấy vào code (bỏ bump version, bỏ kiểm version, bỏ giới hạn kho, bỏ trả hạn ngạch, bỏ chặn phiếu chờ, lệch một ngày khoảng ngày, bỏ khoá nguồn, …) — **16/16 bị test bắt** |
| Nâng cấp CSDL | chạy sạch trên CSDL dev có dữ liệu demo |
| HTTP thật | dev, Postgres + Redis thật, cổng 8090, 19/10/2026 ~21:08 giờ VN, lượt `210812`: **39/39 kiểm tra đạt** (các mẫu ở §4 lấy từ chính lượt này) |

Phạm vi HTTP đã thử: A01, A02, A04, A05, A07 (một phần — lịch xem trước = lịch chốt; chưa thử "đổi lịch làm việc sau khi chốt"), A08 (phần
kho), A34, A35 (phần phiếu chờ), A38 (phần giải phóng phần chưa cấp), A40 (phần khoảng ngày + sai dạng ngày).

**Chưa nghiệm thu:**

- A03 (đổi BOM/quy trình gốc sau khi tạo) và A06 (lô hết hạn/kho ngừng sau khi giữ) — hành vi không đổi trong phần này, chưa chạy lại qua HTTP.
- Hai người gửi cùng version **cùng lúc** qua HTTP — đã chứng minh bằng test trên Postgres thật (đổi chỉ dòng con không tăng version; có bump thì
  tăng đúng một và `@Version` chặn người thứ hai), chưa đo đồng thời qua HTTP.
- A36 (nguồn cung MRP theo sản lượng thực) — hoãn (§8).

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Phần sau của Giai đoạn 8:** v2 cấp vật tư (huỷ phiếu chờ của người tạo, chi tiết một phiếu, ứng viên nguồn, version phiếu, tự duyệt cần thêm
quyền override, lý do 5–2000 ký tự) · v3 công đoạn · v4 dở dang + hoàn vật tư + 3 quyền mới.

**Hoãn có chủ đích (PO/BE đã chốt):**

- **R18/Q20 — nguồn cung lệnh cho MRP theo sản lượng thực:** MRP vẫn tính lệnh đang mở theo `kế hoạch − đã nhập kho`. Đổi sang sản lượng thực
  là đổi số MRP lần thứ ba trong tuần, cần đo riêng; làm thành phase riêng nếu PO cần.
- **R19 / giá trị cấp/dùng/hoàn/dở dang:** hệ thống chưa có lớp giá bình quân (cùng lý do hoãn giá trị tồn ở GĐ7) — các field giá trị sẽ trả `null`
  ở v2–v4 (đúng Q35 của FE).
- **`capabilities` trên projection:** không làm — mọi lệnh vẫn kiểm lại ở server, FE tự suy nút từ trạng thái + quyền.

**FE cần làm:**

1. Gửi version trên mọi lệnh ở §2.1 (có thể gửi ngay — BE bản cũ bỏ qua field lạ), đọc lại lệnh sau mọi `409`.
2. Đổi adapter plan/release/reserve sang có body; DELETE reservation thêm query.
3. Form nháp: `componentWarehouses` theo vật tư; khoá `plannedQuantity`/`outputWarehouseId` khi lệnh có `planningProposalId`.
4. Màn hình lịch: dùng `schedule-preview` trước release, hiển thị `scheduleRevision`/`calendarRevision`/`scheduleTimeZone` sau release.
5. Chạy A01, A02, A04, A05, A07, A08, A34, A35 trên bản BE đã deploy và báo kết quả.

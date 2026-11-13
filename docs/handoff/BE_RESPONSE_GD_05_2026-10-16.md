# BE trả lời — Giai đoạn 5: Định mức, quy trình và công suất

**Trả lời cho:** `FE_HANDOFF_GD_05_2026-10-13.md` (v1, P1) · **Ngày gửi:** 16/10/2026 · **Bản:** v1
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. **Chưa** phải "FE nghiệm thu đạt" — task chỉ đóng
khi FE chạy A01–A36 trên adapter của mình và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**BOM có lưu nháp một lần (header + mọi dòng), lượng đầu ra, số phiên bản, ngày hiệu lực, sao chép và nhập Excel; quy trình
có công đoạn trước và lưu cả đồ thị một lần; bảng công suất nay chia tải theo phần thời gian thật sự rơi vào từng ngày, có xem
trước lịch không ghi gì; điều chỉnh đè công đoạn trước/sau bị từ chối — nhưng BE **giữ** ba thiết kế cũ: quy trình theo Công ty,
ca một khung giờ + giờ nghỉ, trung tâm không có `shiftIds[]`; FE cần ánh xạ theo §2.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | Đọc BOM có lượng đầu ra, UOM, hiệu lực, version, khoá concurrency; list latest/search | 🟡 **Thiếu thật, đã làm** | Trước đây chỉ có `revision` (chuỗi). Nay thêm `bomCode`, `versionNumber`, `outputQuantity`, `outputUomCode`, `effectiveFrom`, `version`, và query `search` + `latestOnly` |
| R02 | Lưu nháp nguyên BOM một transaction | 🟡 **Thiếu thật, đã làm** | Trước đây header và từng dòng là request riêng. Nay `PUT /api/v1/companies/{companyId}/bom-drafts` |
| R03 | Activate chỉ DRAFT hợp lệ/đến ngày; vòng; một ACTIVE dưới đồng thời | 🔴 **Thiếu một phần, đã sửa** | Vòng và "chỉ DRAFT" **đã có**. Mới: chặn ngày hiệu lực tương lai, chặn vật tư đã ngừng; **khoá theo sản phẩm** — đo bằng test DB thật: bỏ khoá ⇒ hai activate đồng thời, một request chết thay vì nhường nhau |
| R04 | Copy tạo nháp version mới | 🟡 **Thiếu thật, đã làm** | `POST /boms/{id}/copy`, `POST /routings/{id}/copy` |
| R05–R07 | Nhập BOM Excel: validate không tạo, commit cả lô, retry ổn định | 🟡 **Thiếu thật, đã làm** | Dùng lại hạ tầng nhập của Giai đoạn 3, `kind=bom`, mẫu `omniplant-bom-v1` |
| R08 | Ca/Lịch/Trung tâm CRUD + lifecycle + tham chiếu | 🟡 **Có sẵn, bổ sung chặn** | Đã có CRUD + activate/deactivate từ 08/2026. Mới: `workingMinutes`, `expectedVersion`, `search`, chặn ngừng khi còn tham chiếu ACTIVE |
| R09 | Lưu Routing nguyên đồ thị, công đoạn trước | 🟡 **Thiếu thật, đã làm** | `PUT /api/v1/companies/{companyId}/routing-drafts`; trước đây không có công đoạn trước và không sửa được routing |
| R10 | Xem trước theo nguồn ACTIVE, không ghi gì | 🟡 **Thiếu thật, đã làm** | `POST /plants/{plantId}/capacity/preview`; đo DB trước/sau: số lệnh SX / giữ chỗ / biến động / công đoạn không đổi |
| R11 | Tải theo kỳ, không gán toàn tải vào một ngày | 🔴 **Lệch thật, đã sửa** | Trước đây cả công đoạn bị dồn vào **ngày bắt đầu**: công đoạn 3 ngày báo quá tải giả ngày 1, trống giả ngày 2–3; công đoạn bắt đầu trước kỳ thì **không hiện** trong kỳ |
| R12 | Điều chỉnh từ chối chồng trước/sau | 🔴 **Đổi hành vi theo FE** | Trước đây cho lưu + trả cờ `sequenceConflict`. Nay `409 OPERATION_SEQUENCE_CONFLICT`, không ghi gì |
| R13 | Chống stale + cùng key khác payload | 🟡 **Đã có một phần** | `expectedVersion` trên mọi lệnh sửa mới + PATCH ca/lịch/trung tâm (tuỳ chọn). Idempotency-Key chỉ áp cho nhập Excel (§6) |
| R14 | Số chính xác, ngày giờ hợp lệ | ✅ | Tối đa 6 số lẻ (`400`), số lẻ cho field số nguyên `400` (từ Giai đoạn 2) |
| R15 | Đổi master không đổi snapshot Lệnh SX | ✅ **Đã đúng từ trước** | Lệnh SX chụp BOM/quy trình lúc tạo; xem trước đọc nguồn ACTIVE hiện tại |

### Trả lời Q1–Q19

| Q | Câu hỏi | Trả lời |
|---|---|---|
| Q1 | Lưu theo lô, `quantityPer` là projection? | **Có, nhưng ngược chiều lưu:** BE **lưu `quantityPer`** (như cũ — hoạch định, Lệnh SX, giá thành đọc nó, không đổi một chữ số) và lưu thêm `outputQuantity`; `componentQuantity = quantityPer × outputQuantity` tính khi đọc. FE gửi `componentQuantity`, đọc lại đúng số đã gửi |
| Q2 | Thương không hết 6 số lẻ thì giữ nội bộ, làm tròn ở biên? | **Không làm tròn — từ chối.** `componentQuantity / outputQuantity` cần quá 6 số lẻ ⇒ `400` trên `lines[i].componentQuantity` (vd 1 / 3). Lý do: lưu số khác số người dùng gõ thì form mở lại hiện số khác |
| Q3 | `revision` là nhãn, `versionNumber` server cấp? | **Có.** `versionNumber` 1..n theo Công ty + sản phẩm. Không gửi `revision` ⇒ mặc định bằng `versionNumber` |
| Q4 | `effectiveFrom = null` là không trì hoãn? | **Có.** Lưu ý: là **thời điểm** (ISO timestamp), không phải ngày — xem Q5 |
| Q5 | So ngày theo timezone Công ty? | **Không** — Công ty không có timezone. `effectiveFrom` là thời điểm, so với đồng hồ server. FE muốn "có hiệu lực từ ngày 20/10" thì gửi `2026-10-19T17:00:00Z` (00:00 giờ VN) |
| Q6 | Cây trả `quantityPer` per-unit + thêm lượng theo lô? | **Có.** Mỗi node thêm `outputQuantity` (của BOM con, `null` nếu không có) và `componentQuantity` (theo BOM cha, `null` ở gốc) |
| Q7 | Một SAVE atomic cho UI mới? | **Có**, API cũ giữ nguyên |
| Q8 | Hiệu lực lịch nằm trong hiệu lực ca? | **Không áp dụng** — ca **không** có hiệu lực; hiệu lực nằm ở lịch (§2) |
| Q9 | Trung tâm còn Routing ACTIVE ⇒ chặn ngừng? | **Có**, cũng chặn khi còn công đoạn của Lệnh SX chưa xong |
| Q10 | Sửa ca/lịch chỉ ảnh hưởng xem trước mới? | **Có.** Lịch Lệnh SX chỉ sinh **một lần** lúc release; sửa ca/lịch không dời lịch đã có |
| Q11 | Tối đa 90 ngày, tính cả hai đầu? | **Có.** 13/10 → 10/01 năm sau (90 ngày) nhận; thêm 1 ngày ⇒ `400` trên `periodEnd` |
| Q12 | Chỉ timezone không DST? | **Có** |
| Q13 | Lịch sử nhập BOM chỉ cần BOM_READ? | **Không** — giống Giai đoạn 3: mọi thao tác batch cần **cả** `PERM_BOM_READ` và `PERM_BOM_MANAGE` trên Công ty; batch của người khác ⇒ `404` |
| Q14 | Retention | Batch, dòng và kết quả commit giữ **không hết hạn**; file gốc **không** lưu (chỉ lưu hash + các ô đã đọc) |
| Q15 | NEAR_CAPACITY ≥ 85 % | **Có.** Có tải mà sức chứa 0 ⇒ `OVERLOADED`; không có lịch ⇒ `status: null` |
| Q16 | Điều chỉnh cho PLANNED/RELEASED chưa bắt đầu? | **Khác một chút:** `RELEASED` **và** `IN_PROGRESS`, miễn công đoạn **đó** chưa có báo cáo sản lượng. `PLANNED` chưa có lịch (lịch sinh lúc release) nên không có gì để chỉnh |
| Q17 | Xem trước tuần tự, không trừ tải sẵn có? | **Có** |
| Q18 | Một nháp mở/quy trình, BOM nhiều nháp? | **Có.** Tạo nháp mới hoặc sao chép khi sản phẩm đã có nháp quy trình ⇒ `409 OPEN_DRAFT_EXISTS` |
| Q19 | Hao hụt scale 8? | **Không — scale 6** (`scrapRate`), quá 6 số lẻ ⇒ `400`. Trong file Excel, % nhập tối đa **4** số lẻ (5,1234 % = 0,051234) |

---

## §1 — Nguyên nhân thật

- **Bảng công suất (R11):** từ 08/2026 tải của một công đoạn được tính trọn vào ngày nó **bắt đầu**. Không phải lỗi dữ liệu —
  là cách tính. Nay chia theo phần **giờ làm việc** của công đoạn rơi vào từng ngày (không có lịch thì theo giờ đồng hồ).
- **Điều chỉnh lịch (R12):** trước đây cố ý chỉ cảnh báo. Đổi theo yêu cầu FE, quyết định của PO.
- **Activate đồng thời (R03):** chỉ một ràng buộc DB giữ "một ACTIVE" — đủ để không có hai ACTIVE, nhưng request thua nhận lỗi
  thay vì nhường. Nay khoá theo sản phẩm, request sau chờ rồi thay bản trước.
- **Ca:** giờ nghỉ chồng nhau và ca 0 phút từng được nhận, làm giờ làm thực bị trừ hai lần.
- Phần còn lại (lưu một lần, version, copy, xem trước, nhập Excel, công đoạn trước) là **chức năng chưa có**, không phải lỗi.

---

## §2 — Thay đổi contract

### Endpoint

| Endpoint | Trước | Sau | Phá client đang chạy? |
|---|---|---|---|
| `PUT /api/v1/companies/{companyId}/bom-drafts` | không có | lưu nháp BOM một lần, **200** | Không |
| `POST /api/v1/boms/{bomId}/copy` | không có | **201**, nháp version mới | Không |
| `GET /api/v1/boms/{bomId}/versions` | không có | mọi version của sản phẩm, mới nhất trước | Không |
| `POST /api/v1/boms/{bomId}/deactivate` | không có | như `DELETE`, bắt buộc `expectedVersion` | Không |
| `POST /api/v1/boms/{bomId}/activate` | không body | body `{expectedVersion}` **tuỳ chọn** | Có (chặn mới — §3) |
| `GET /api/v1/companies/{companyId}/boms` | | thêm `search`, `latestOnly` | Không |
| `PUT /api/v1/companies/{companyId}/routing-drafts` | không có | lưu nháp quy trình cả đồ thị, **200** | Không |
| `POST /api/v1/routings/{id}/copy`, `GET .../versions`, `POST .../deactivate` | không có | như BOM | Không |
| `GET /api/v1/companies/{companyId}/routings` | | thêm `plantId`, `workCenterId`, `search` | Không |
| `GET /api/v1/plants/{plantId}/capacity/loads?periodStart&periodEnd&workCenterId` | không có | tải theo kỳ từng trung tâm | Không |
| `POST /api/v1/plants/{plantId}/capacity/preview` | không có | xem trước, **không ghi** | Không |
| `GET /api/v1/plants/{plantId}/capacity-board` | dồn tải vào ngày bắt đầu | chia theo ngày | **Có — số liệu** (§3) |
| `POST /api/v1/work-orders/{woId}/operations/{opId}/schedule-adjustments` | chồng ⇒ 200 + cờ | chồng ⇒ 409; `plannedEndAt` tuỳ chọn | **Có** (§3) |
| `GET /api/v1/plants/{plantId}/shifts`, `/work-calendars`, `/work-centers` | | thêm `search` (mã hoặc tên) | Không |
| `POST /api/v1/data-import/batches` | `kind` = items/suppliers/customers | thêm `kind=bom` | Không |

### Ánh xạ field FE ↔ BE (những chỗ BE **không** đổi theo FE)

| FE đề xuất | BE trả / nhận | Ghi chú |
|---|---|---|
| `lockVersion` | BOM: `version` · Routing: `lockVersion` · Ca/Lịch/Trung tâm: `version` | Routing đã dùng `version` cho **nhãn** từ trước nên khoá đặt tên `lockVersion` |
| Routing theo Plant (`/plants/{P}/routings`, `routing-drafts`) | `/companies/{C}/routings` (+ lọc `plantId`), `/companies/{C}/routing-drafts` | Mọi công đoạn của một quy trình phải ở cùng một nhà máy — BE trả `plantId` suy từ trung tâm |
| Routing `outputItemId`, `description` | `itemId`, `note` | |
| Routing `versionNumber` + nhãn | `versionNumber` (số) + `version` (nhãn, mặc định bằng số) | |
| Operation `predecessorClientKeys` / `predecessorOperationIds` | request `predecessorClientKeys`, response `predecessorOperationIds` | Như FE đề xuất. Giữ id công đoạn khi lưu lại: gửi `routingOperationId` |
| Shift `workingIntervals[]` + `breaks[]` | **một** khung `startTime`/`endTime` + `breaks[]` | Ca "08–12 và 13–17" = khung 08–17 + nghỉ 12–13. `endTime < startTime` = ca qua đêm |
| Shift `effectiveFrom/To` | không có | Hiệu lực nằm ở **lịch** |
| Calendar `weeklySchedule[7]{weekday, shiftIds[]}` | `weeklyShifts[]{weekday, shiftId}` | Một ngày nhiều ca = nhiều phần tử cùng `weekday`; ngày không có phần tử = ngày nghỉ |
| Calendar `nonWorkingDates[]{date, name}` | `exceptions[]{exceptionDate, reason}` | |
| Center `shiftIds[]` | không có | Trung tâm làm theo **mọi** ca của lịch gán cho nó; muốn tập ca khác ⇒ lịch khác |
| `POST /{id}/status` | `POST /{id}/activate`, `POST /{id}/deactivate` (có sẵn) | |
| `GET /plants/{P}/capacity` | `GET /plants/{P}/capacity/loads` (tổng theo trung tâm) + `GET .../capacity-board` (từng công đoạn, phân trang) | |
| Adjustment `requestedStart`, `periodStart/End` | `plannedStartAt`, `plannedEndAt` (tuỳ chọn), không có kỳ | Bỏ `plannedEndAt` ⇒ server tự tính theo lịch, giống lúc release |
| Decimal là chuỗi | **JSON number** toàn hệ thống | Như các giai đoạn trước |

### Field mới

| Field | Kiểu | Nullable? | Nguồn | Ví dụ thật |
|---|---|---|---|---|
| BOM `bomCode` | string | Không | Người dùng nhập, server viết hoa; duy nhất trong Công ty | `"BOM-231527"` |
| BOM `versionNumber` | integer | Không | Server cấp 1..n theo sản phẩm | `1` |
| BOM `outputQuantity` | number | Không | Người dùng; BOM cũ = `1` | `10` |
| BOM `outputUomCode` | string | Không | Đơn vị của sản phẩm | `"P231527"` |
| BOM `effectiveFrom` | ISO timestamp | Có | Người dùng | `null` |
| BOM `version` | integer | Không | Khoá concurrency, gửi lại làm `expectedVersion` | `0` |
| Dòng BOM `componentQuantity`, `componentUomCode` | number, string | Không | `quantityPer × outputQuantity`; đơn vị vật tư | `25.000000`, `"K231527"` |
| Cây BOM `outputQuantity`, `componentQuantity` | number | Có | Q6 | |
| Routing `name`, `versionNumber`, `effectiveFrom`, `plantId`, `totalSetupMinutes`, `totalRunMinutesPerUnit`, `lockVersion` | | `name`/`effectiveFrom`/`plantId` có | Server | §4.2 |
| Operation `workCenterName`, `predecessorOperationIds` | string, UUID[] | Không (`[]`) | | §4.2 |
| Ca `workingMinutes` | integer | Không | Khung − giờ nghỉ, server tính | `480`, `450` |
| Trung tâm `workCalendarCode`, `workCalendarName` | string | Có | Lịch gán | |
| Board line `inPeriodMinutes` | number | Không | Phần tải của công đoạn trong `[from, to]` | `130.000000` |
| Batch `bomCount` | integer | Có (chỉ `kind=bom`) | Số nhóm mã BOM trong file | `1` |
| Request `expectedVersion` trên PATCH ca/lịch/trung tâm | integer | Có (tuỳ chọn) | | |

### Công thức

```
BOM:        quantityPer       = componentQuantity / outputQuantity      (lưu, 6 số lẻ, không làm tròn)
            componentQuantity = quantityPer × outputQuantity            (trả ra)
Ca:         workingMinutes    = (endTime − startTime, qua nửa đêm nếu cần) − Σ giờ nghỉ
Công suất:  requiredMinutes   = setup + quantity × runMinutesPerUnit    (phút công suất)
            elapsed           = requiredMinutes / capacityUnits         (chỉ khi XẾP lịch xem trước)
            availableMinutes  = phút làm việc của lịch trong kỳ × capacityUnits
            baseInPeriod      = Σ phần tải các công đoạn đã có lịch rơi vào kỳ
                                (tỉ lệ theo phút làm việc của công đoạn trong kỳ / tổng phút làm việc của nó)
            demand            = base + Σ requiredMinutes của xem trước
            allocated         = base + Σ phần xem trước nằm trong kỳ
            overload          = max(0, demand − available)
            utilization %     = demand / available × 100   (null nếu available = 0 hoặc không có lịch)
Ngày nghỉ / hiệu lực xét theo ngày CA BẮT ĐẦU; đuôi ca đêm thuộc ngày nó chạy (FE A21: 330 phút).
```

---

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST .../schedule-adjustments` | cửa sổ mới chồng công đoạn trước/sau | 200, `sequenceConflict: true` | `409 OPERATION_SEQUENCE_CONFLICT`, không ghi | Bắt buộc mới | Giữ form, báo xung đột |
| `POST .../schedule-adjustments` | Lệnh SX không ở `RELEASED`/`IN_PROGRESS`, hoặc công đoạn đã có báo cáo sản lượng | 200 | `409 STATE_CONFLICT` | Bắt buộc mới | |
| `POST .../schedule-adjustments` | `reason` dưới 5 ký tự | 200 | `400` trên `reason` | Bắt buộc mới | |
| `GET .../capacity-board` | công đoạn kéo dài nhiều ngày, hoặc bắt đầu trước `from` | dồn vào ngày bắt đầu; không hiện nếu bắt đầu trước `from` | chia theo ngày; có hiện | Đổi số liệu | Không đổi code |
| `GET .../capacity-board` | ca qua đêm | `dayCapacityMinutes` tính cả đuôi vào ngày bắt đầu ca | đuôi tính vào ngày nó chạy | Đổi số liệu | |
| `POST /boms/{id}/activate` | sản phẩm/thành phần đã ngừng | 200 | `422 RESOURCE_INACTIVE` | Bắt buộc mới | |
| `POST /routings/{id}/activate` | trung tâm đã ngừng | 200 | `422 RESOURCE_INACTIVE` | Bắt buộc mới | |
| `POST /bom-lines`, `PATCH /bom-lines/{id}` (API cũ) | `quantityPer`/`scrapRate` quá 6 số lẻ | 200, DB làm tròn | `400` | Bắt buộc mới | Giới hạn 6 số lẻ |
| Ca: tạo/sửa | giờ nghỉ chồng nhau; ca 0 phút; giờ nghỉ ăn hết ca | 201/200 | `422 OPERATION_NOT_ALLOWED` | Bắt buộc mới | |
| Ca: ngừng (`POST deactivate` và `DELETE`) | còn trên lịch ACTIVE | 200 | `422 REFERENCE_IN_USE` | Bắt buộc mới | "Đang được sử dụng" |
| Lịch: ngừng | còn trung tâm ACTIVE dùng | 200 | `422 REFERENCE_IN_USE` | Bắt buộc mới | |
| Lịch: tạo/sửa | gán ca đã ngừng | 201/200 | `422 RESOURCE_INACTIVE` | Bắt buộc mới | |
| Trung tâm: ngừng | còn quy trình ACTIVE, hoặc công đoạn của Lệnh SX chưa xong | 200 | `422 REFERENCE_IN_USE` | Bắt buộc mới | |

Không bỏ field nào, không đổi kiểu, không đổi mặc định query param. `sequenceConflict` vẫn có trên response điều chỉnh nhưng
nay luôn `false`. **Thứ tự deploy:** không ràng buộc — các chặn mới là sửa sai hoặc theo yêu cầu FE.

---

## §4 — Cách dùng (copy từ lượt gọi thật `231527`, 15/10/2026 ~23:15 giờ VN)

Mọi request cần `Authorization: Bearer <access token>` (đã che). UUID là ID thật của lượt đó. Số trong response là nguyên văn
server trả.

### 4.1 `PUT /api/v1/companies/{companyId}/bom-drafts` — lưu nháp mới (A01) · `X-Trace-Id: 30988e3cb975486f`

```json
{"bomId":null,"expectedVersion":null,"bomCode":"bom-231527","parentItemId":"c8a64aba-6acc-4c22-8928-2c8a8550226f",
 "outputQuantity":10,"effectiveFrom":null,"description":"BOM minh họa",
 "lines":[{"lineNo":1,"componentItemId":"ce119aa2-90a4-4b9f-99a4-8236f3f8e4a2","componentQuantity":25,"scrapRate":0.05}]}
```

`200`:

```json
{"code":"SUCCESS","result":{"bomId":"d754fd7d-8211-4235-8539-333ec1c55a8d","companyId":"016183df-0481-405c-aed0-2112958fb3a9",
"parentItemId":"c8a64aba-6acc-4c22-8928-2c8a8550226f","parentItemCode":"FG-231527","parentItemName":"FG","revision":"1",
"status":"DRAFT","description":"BOM minh họa","createdAt":"2026-10-15T16:15:28.396218500Z","updatedAt":"2026-10-15T16:15:28.396218500Z",
"lines":[{"lineId":"bf0bf07d-2322-484b-869b-b59d964e8aa6","bomId":"d754fd7d-8211-4235-8539-333ec1c55a8d","lineNo":1,
"componentItemId":"ce119aa2-90a4-4b9f-99a4-8236f3f8e4a2","componentItemCode":"RM-231527","componentItemName":"RM",
"quantityPer":2.500000,"scrapRate":0.05,"componentQuantity":25.000000,"componentUomCode":"K231527"}],
"bomCode":"BOM-231527","versionNumber":1,"outputQuantity":10,"outputUomCode":"P231527","effectiveFrom":null,"version":0},"message":"OK"}
```

Sửa nháp: gửi lại toàn bộ với `"bomId"` + `"expectedVersion": <version vừa đọc>`. Dòng giữ nguyên **thành phần** thì giữ
`lineId`; thành phần không còn trong request thì bị xoá. Trường vắng mặt bị xoá (`description`, `effectiveFrom`) — trừ
`revision` (vắng thì giữ). Đổi sản phẩm ⇒ `400` trên `parentItemId` (sao chép thay vì sửa).

### 4.2 `PUT /api/v1/companies/{companyId}/routing-drafts` (A25) · `X-Trace-Id: 0233adff36ae4ad6`

```json
{"code":"ROUTE-231527","name":"Lap rap mau","itemId":"c8a64aba-6acc-4c22-8928-2c8a8550226f","operations":[
 {"clientKey":"assembly","sequence":1,"name":"Lap rap","workCenterId":"d186bbdb-3bb8-4a37-915d-d0a3df256284",
  "setupMinutes":30,"runMinutesPerUnit":2,"predecessorClientKeys":[]},
 {"clientKey":"paint","sequence":2,"name":"Son","workCenterId":"2082de86-3219-4e3a-bd50-b62d347ddc5a",
  "setupMinutes":10,"runMinutesPerUnit":1,"predecessorClientKeys":["assembly"]}]}
```

`200` (rút gọn ở `operations[0]`, mọi field có mặt như dòng thứ hai):

```json
{"code":"SUCCESS","result":{"routingId":"997f2f53-2d58-494d-a6f8-c7c5317bf000","companyId":"016183df-0481-405c-aed0-2112958fb3a9",
"itemId":"c8a64aba-6acc-4c22-8928-2c8a8550226f","itemSku":"FG-231527","itemName":"FG","code":"ROUTE-231527","version":"1",
"status":"DRAFT","note":null,"createdAt":"2026-10-15T16:15:30.179005800Z","updatedAt":"2026-10-15T16:15:30.179005800Z",
"operations":[{"routingOperationId":"864cd7f4-38d6-4268-a874-baf293771728","sequence":1,"…":"…"},
{"routingOperationId":"84d71fb2-b026-4ef3-8cf4-8752ac995a16","sequence":2,"name":"Son","workCenterId":"2082de86-3219-4e3a-bd50-b62d347ddc5a",
"workCenterCode":"PNT-231527","setupMinutes":10,"runMinutesPerUnit":1,"workCenterName":"Son",
"predecessorOperationIds":["864cd7f4-38d6-4268-a874-baf293771728"]}],
"name":"Lap rap mau","versionNumber":1,"effectiveFrom":null,"plantId":"343c0fb4-ed75-4fea-977e-3c9435085868",
"totalSetupMinutes":40,"totalRunMinutesPerUnit":3,"lockVersion":0},"message":"OK"}
```

Công đoạn trước sai thứ tự ⇒ `400`, `errors[0] = {"field":"operations[0].predecessorClientKeys","message":"'paint' must have a smaller sequence"}`
(`6ba8807a27b842a6`). Lưu lại: thêm `"routingId"`, `"expectedVersion": <lockVersion>`, và `"routingOperationId"` cho công đoạn
muốn giữ id. `code` và `itemId` không đổi được.

### 4.3 `POST /api/v1/plants/{plantId}/capacity/preview` (A27) · `X-Trace-Id: a364f3e218724e32`

Dữ liệu: ca 08:00–17:00 nghỉ 12:00–13:00 mọi ngày; trung tâm Lắp ráp 2 đơn vị, Sơn 1 đơn vị; đã có một Lệnh SX 50 cái đã
release ngày 20/10 (tải nền Lắp ráp 130, Sơn 60).

```json
{"routingId":"997f2f53-2d58-494d-a6f8-c7c5317bf000","quantity":500,"requestedStart":"2026-10-20T01:00:00Z",
 "periodStart":"2026-10-20","periodEnd":"2026-10-20"}
```

`200` (công đoạn 1 + tải; công đoạn 2 có cùng cấu trúc):

```json
{"code":"SUCCESS","result":{"plantId":"343c0fb4-ed75-4fea-977e-3c9435085868","timezone":"Asia/Ho_Chi_Minh",
"periodStart":"2026-10-20","periodEnd":"2026-10-20","quantity":500,"requestedStart":"2026-10-20T01:00:00Z",
"operations":[{"routingOperationId":"864cd7f4-38d6-4268-a874-baf293771728","sequence":1,"name":"Lap rap",
"workCenterId":"d186bbdb-3bb8-4a37-915d-d0a3df256284","workCenterCode":"ASM-231527","workCenterName":"Lap rap","capacityUnits":2,
"requiredMinutes":1030.000000,"inPeriodMinutes":960.000000,"afterPeriodMinutes":70.000000,
"proposedStart":"2026-10-20T01:00:00Z","proposedEnd":"2026-10-21T01:35:00Z",
"segments":[{"start":"2026-10-20T01:00:00Z","end":"2026-10-20T05:00:00Z","capacityMinutes":480.000000},
{"start":"2026-10-20T06:00:00Z","end":"2026-10-20T10:00:00Z","capacityMinutes":480.000000},
{"start":"2026-10-21T01:00:00Z","end":"2026-10-21T01:35:00Z","capacityMinutes":70.000000}]}, "…"],
"loads":[{"workCenterId":"d186bbdb-3bb8-4a37-915d-d0a3df256284","workCenterCode":"ASM-231527","workCenterName":"Lap rap",
"capacityUnits":2,"baseInPeriodMinutes":130.000000,"previewRequiredMinutes":1030.000000,"demandMinutes":1160.000000,
"allocatedMinutes":1090.000000,"availableMinutes":960.000000,"overloadMinutes":200.000000,"utilizationPercent":120.83,
"status":"OVERLOADED"}, "…"],
"sourceVersions":{"routingId":"997f2f53-2d58-494d-a6f8-c7c5317bf000","routingVersionNumber":1,"routingLockVersion":1,
"bomId":"ddc1f875-4f57-4c52-9a33-b4a23bc0b7f1","bomVersionNumber":2,"bomLockVersion":1,
"workCenters":[{"id":"d186bbdb-3bb8-4a37-915d-d0a3df256284","lockVersion":0},{"id":"2082de86-3219-4e3a-bd50-b62d347ddc5a","lockVersion":0}],
"workCalendars":[{"id":"686d5fb4-3c19-4c17-b907-68ffe7e788ae","lockVersion":0}]},"generatedAt":"2026-10-15T16:15:31.278758Z"},"message":"OK"}
```

Khớp đúng ví dụ §3.6 của FE: 1030 phút, bắt đầu 08:00, kết thúc 08:35 hôm sau, trong kỳ 960 / sau kỳ 70. Khác ví dụ chỉ ở
tải nền (130 thay vì 100) ⇒ demand 1160, allocated 1090, overload 200. Gọi hai lần, đếm DB trước/sau
(Lệnh SX / giữ chỗ / biến động / công đoạn): `9/45/200/24` → `9/45/200/24`.

### 4.4 Tải theo kỳ · `GET /api/v1/plants/{plantId}/capacity/loads?periodStart=2026-10-20&periodEnd=2026-10-26` · `d41c6d0c8067443c`

```json
{"code":"SUCCESS","result":{"plantId":"343c0fb4-ed75-4fea-977e-3c9435085868","timezone":"Asia/Ho_Chi_Minh",
"periodStart":"2026-10-20","periodEnd":"2026-10-26","generatedAt":"2026-10-15T16:15:31.628087Z",
"loads":[{"workCenterId":"d186bbdb-3bb8-4a37-915d-d0a3df256284","workCenterCode":"ASM-231527","workCenterName":"Lap rap",
"capacityUnits":2,"baseInPeriodMinutes":130.000000,"previewRequiredMinutes":0.000000,"demandMinutes":130.000000,
"allocatedMinutes":130.000000,"availableMinutes":6720.000000,"overloadMinutes":0.000000,"utilizationPercent":1.93,
"status":"AVAILABLE"}, "…"]},"message":"OK"}
```

`6720 = 7 ngày × 480 phút × 2 đơn vị`. Danh sách gồm mọi trung tâm ACTIVE của nhà máy + trung tâm nào còn tải trong kỳ.

### 4.5 Điều chỉnh công đoạn (A32/A33)

Chồng công đoạn trước (`0fb3b4798ece4533`):

```json
{"plannedStartAt":"2026-10-20T01:30:00Z","reason":"Doi gio theo ke hoach","expectedVersion":1}
```
```json
{"code":"OPERATION_SEQUENCE_CONFLICT","message":"Operation would start at 2026-10-20T01:30:00Z, before operation 1 ends at 2026-10-20T03:10:00Z"}
```

Hợp lệ, không gửi `plannedEndAt` (`2febdc4af2204428`):

```json
{"plannedStartAt":"2026-10-20T06:00:00Z","reason":"Doi gio theo ke hoach","expectedVersion":1}
```
```json
{"code":"SUCCESS","result":{"workOrderOperationId":"2d7d5c6c-15fa-4849-9ead-88f96a322b88","plannedStartAt":"2026-10-20T06:00:00Z",
"plannedEndAt":"2026-10-20T07:00:00Z","scheduleAdjustmentReason":"Doi gio theo ke hoach","version":2,"sequenceConflict":false,
"calendarConflict":false,"capacityOverload":false,"dayCapacityMinutes":480.000000,"dayExistingLoadMinutes":60.000000,
"utilizationPercent":12.50},"message":"OK"}
```

Gửi lại với `expectedVersion: 1` ⇒ `409 CONCURRENT_MODIFICATION` (`8d374956bc954067`).

### 4.6 Nhập BOM Excel — `POST /api/v1/data-import/batches` (multipart: `file`, `kind=bom`, `companyId`)

Mẫu `omniplant-bom-v1` đúng như FE §3.4: sheet `BOM`, header dòng 1 (9 cột A–I theo VI hoặc EN), sheet `_meta`
(A1 `version` / B1 `omniplant-bom-v1`, A2 `locale` / B2 `vi`|`en`). Ô số lượng có thể là ô số hoặc ô chữ; BE đọc **giá trị**
ô số, không đọc chuỗi hiển thị. Upload `201` với batch `READY`/`INVALID` (như Giai đoạn 3), thêm `bomCount`. Commit
(`44e9cb062f3b47a6`, `Idempotency-Key` riêng):

```json
{"expectedVersion":0}
```
```json
{"code":"SUCCESS","result":{"id":"ae71b5f9-56c4-47f3-b2a0-d7fc86070d03","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82",
"companyId":"016183df-0481-405c-aed0-2112958fb3a9","plantId":null,"kind":"bom","locale":"vi","templateVersion":"omniplant-bom-v1",
"fileName":"bom-demo.xlsx","fileSize":5620,"status":"COMMITTED","version":1,"createdAt":"2026-10-15T16:15:32.699539Z",
"committedAt":"2026-10-15T16:15:32.944193Z","rowCount":2,"validRowCount":2,"errorRowCount":0,"createdCount":2,
"rows":[{"line":2,"values":{"notes":"BOM minh họa","bomCode":"IMP-231527","outputUom":"P231527","productCode":"FG3-231527",
"componentUom":"K231527","scrapPercent":"5","componentCode":"RM-231527","outputQuantity":"10","componentQuantity":"25"},"issues":[]},
{"line":3,"values":{"…":"…","scrapPercent":"0","componentCode":"RM2-231527","componentQuantity":"5"},"issues":[]}],
"createdIds":["3edb7856-9811-4dd7-9804-91545d4bc7da"],"bomCount":1},"message":"OK"}
```

`createdIds` có **một id cho mỗi BOM**, không phải mỗi dòng; `createdCount` vẫn là số **dòng** đã ghi. Tên field trong
`rows[].values`: `bomCode, productCode, outputQuantity, outputUom, componentCode, componentQuantity, componentUom, scrapPercent, notes`.
File có lỗi (`d41cfde6e6fa4b0f`) — các mã lỗi theo dòng: `GROUP_MISMATCH` (cột B/C/D/I khác dòng đầu nhóm),
`DUPLICATE_IN_FILE` (trùng thành phần trong nhóm, một sản phẩm hai mã BOM), `ALREADY_EXISTS` (mã BOM đã có, sản phẩm đã có
BOM bất kỳ trạng thái), `RULE_VIOLATION` (đơn vị không phải đơn vị của vật tư, vật tư ngừng; và mọi dòng còn lại của một nhóm
có lỗi — "cả BOM bị từ chối"), `INVALID_FORMAT` (số sai dạng, ≤ 0, % ≥ 100, % quá 4 số lẻ, thương quá 6 số lẻ),
`REFERENCE_NOT_FOUND`, `NOT_ALLOWED_VALUE` (sản phẩm không phải FG/WIP, thành phần là FG).

---

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — cùng một envelope:

```json
{
  "code":    "SUCCESS",
  "result":  { },
  "message": "OK",
  "errors":  [ { "field": "lines[0].componentQuantity", "message": "numeric value out of bounds (<13 digits>.<6 digits> expected)" } ]
}
```

- `code` là **tên hằng**, FE rẽ nhánh theo nó — nhiều mã dùng chung HTTP 409/422.
- `errors` chỉ xuất hiện khi có lỗi theo field; khi lỗi, `result` **vắng mặt** (trừ `IMPORT_REVALIDATION_FAILED` như Giai đoạn 3).
- Phân trang: `result = { content, page, size, totalElements, totalPages, first, last }`.

| Tình huống (mã FE đề xuất) | HTTP | `code` BE | `errors[]`? |
|---|---|---|---|
| Số/ngày/đồ thị sai, trùng thành phần (`DUPLICATE_COMPONENT`), tự làm thành phần, công đoạn trước sai, kỳ > 90 ngày, bắt đầu ngoài kỳ | 400 | `VALIDATION_ERROR` | ✅ `lines[i].…`, `operations[i].…`, `periodEnd`, `requestedStart` |
| Vòng BOM (`CIRCULAR_BOM`) | **422** | `BOM_CIRCULAR_REFERENCE` (mã có sẵn) | — |
| Sửa bản không phải nháp, activate không phải nháp, xem trước quy trình không ACTIVE (`INVALID_STATE`) | 409 | `STATE_CONFLICT` | — |
| Ngày hiệu lực tương lai 🆕 | 409 | `EFFECTIVE_DATE_FUTURE` | — |
| Mã BOM / mã+nhãn quy trình đã có (`DUPLICATE_CODE`) | 409 | `RESOURCE_ALREADY_EXISTS` | — |
| `expectedVersion` cũ (`VERSION_CONFLICT`) | 409 | `CONCURRENT_MODIFICATION` | — |
| Còn tham chiếu ACTIVE khi ngừng 🆕 (`REFERENCE_IN_USE`) | **422** (FE đề xuất 409) | `REFERENCE_IN_USE` | — |
| Đã có nháp quy trình 🆕 | 409 | `OPEN_DRAFT_EXISTS` | — |
| Không có giờ làm việc / không có lịch / quá 180 ngày sau kỳ 🆕 | 409 | `SCHEDULING_UNAVAILABLE` | — |
| Điều chỉnh chồng trước/sau 🆕 | 409 | `OPERATION_SEQUENCE_CONFLICT` | — |
| BOM ACTIVE chưa có khi xem trước | 409 | `MISSING_BOM` (mã có sẵn) | — |
| Vật tư/trung tâm/ca đã ngừng (`REFERENCE_UNAVAILABLE`) | 422 | `RESOURCE_INACTIVE` | — |
| Khác Công ty / khác nhà máy | 422 | `RESOURCE_SCOPE_MISMATCH` | — |
| Thành phần là thành phẩm; ca 0 phút / giờ nghỉ chồng | 422 | `OPERATION_NOT_ALLOWED` | — |
| `X-Plant-Id` khác `plantId` trên đường dẫn | 409 | `STATE_CONFLICT` | — |
| File sai/không phải mẫu/quá giới hạn (`IMPORT_*`) | 400 / 413 | `FILE_FORMAT`, `TEMPLATE`, `EMPTY_FILE`, `ROW_LIMIT` / `FILE_SIZE` | — |
| Commit batch không READY / version cũ / dữ liệu đã đổi | 409 | `STATE_CONFLICT` / `CONCURRENT_MODIFICATION` / `IMPORT_REVALIDATION_FAILED` (kèm `result`) | — |
| Cùng key, nội dung khác | 409 | `IDEMPOTENCY_CONFLICT` | — |
| Thiếu quyền, kể cả ID không tồn tại (xem §6) | 403 | `PERMISSION_DENIED` | — |
| Hết phiên | 401 | mã Auth hiện có | — |

---

## §6 — Quyền, phạm vi và giới hạn đã biết

| Thao tác | Quyền | Phạm vi |
|---|---|---|
| Lưu nháp / sao chép / ngừng BOM (mới) | `PERM_BOM_READ` **và** `PERM_BOM_MANAGE` | Công ty |
| Activate BOM, API dòng BOM cũ | `PERM_BOM_MANAGE` (như cũ) | Công ty của BOM |
| Lưu nháp / sao chép / ngừng quy trình (mới) | `PERM_ROUTING_READ` **và** `PERM_ROUTING_MANAGE` | Công ty |
| Ca / Lịch / Trung tâm | như cũ: `PERM_SHIFT_*`, `PERM_WORK_CALENDAR_*`, `PERM_WORK_CENTER_*` | Nhà máy |
| Tải theo kỳ, bảng công suất | `PERM_CAPACITY_READ` | Nhà máy |
| Xem trước | `PERM_CAPACITY_READ` + `PERM_WORK_CENTER_READ` + `PERM_WORK_CALENDAR_READ` + `PERM_SHIFT_READ` trên nhà máy **và** `PERM_ROUTING_READ` + `PERM_BOM_READ` trên Công ty của nhà máy — thiếu một quyền là 403 | |
| Điều chỉnh công đoạn | `PERM_CAPACITY_MANAGE` (như cũ) | Lệnh SX |
| Nhập BOM | `PERM_BOM_READ` **và** `PERM_BOM_MANAGE` | Công ty; batch của người khác ⇒ 404 |

Không có permission mới. Tài khoản chỉ gán theo **nhà máy** không đọc được BOM/quy trình (dữ liệu cấp Công ty) nên cũng không
xem trước được — giống hiện trạng.

**Giới hạn đã biết:**

- **ID không tồn tại trả 403, không phải 404** trên các endpoint chi tiết (`/boms/{id}`, `/routings/{id}`, …) — như mọi
  endpoint chi tiết hiện có.
- **`createdAt`/`updatedAt` trong response vừa ghi có thể có 9 chữ số lẻ, đọc lại thì 6** (vd `…396218500Z` →
  `…396219Z`). Có sẵn trên mọi bản ghi tạo qua API, không riêng giai đoạn này. So sánh theo `version`, không theo thời gian.
- **Release Lệnh SX vẫn xếp lịch không chia `capacityUnits`**, còn xem trước có chia (theo công thức FE). Với trung tâm nhiều
  đơn vị, lịch Lệnh SX thật sẽ **dài hơn** lịch xem trước. Chưa đổi để không dời lịch Lệnh SX hiện có (§8).
- Xem trước và release đều **tuần tự theo `sequence`**; công đoạn trước (`predecessorOperationIds`) được lưu và kiểm nhưng
  chưa dùng để xếp song song, và **không** được chụp vào Lệnh SX.
- Mã BOM là duy nhất trong Công ty **cho từng version** — bản sao phải có mã mới. Mã quy trình là "họ" — bản sao giữ mã.
- BOM cũ (trước giai đoạn này) được gán `bomCode = mã sản phẩm + "-" + revision`, `versionNumber` theo thứ tự tạo,
  `outputQuantity = 1`.
- Idempotency-Key chỉ áp cho nhập Excel (upload + commit). Lưu nháp/sao chép/activate dựa vào `expectedVersion`; lưu nháp
  **mới** gửi lại lần hai sẽ nhận `409 RESOURCE_ALREADY_EXISTS` (mã BOM đã có) chứ không trả lại bản cũ.
- `operationMinutes` trên bảng công suất có thể in tới 12 số lẻ (`130.000000000000`) — có sẵn từ 08/2026.

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

`mvn -o clean verify` (unit + integration trên PostgreSQL thật qua Testcontainers):
**1548 case unit + 215 case integration, failures = 0, errors = 0**. Trước giai đoạn này: 1461 + 202 (đo lại trên một bản
sạch, khớp số đã ghi). Không xoá test cũ nào; các test cũ đổi theo hành vi mới (điều chỉnh chồng lịch nay 409, bảng công
suất chia theo ngày) được **sửa**, không xoá.

Integration đáng chú ý (DB thật):
- Hai bản nháp cùng sản phẩm activate **đồng thời**, 5 vòng: luôn đúng **một** ACTIVE, không request nào lỗi.
- Lưu lại nháp **đảo** số dòng giữ nguyên `lineId` (không vướng ràng buộc số dòng duy nhất).
- Lưu nháp hỏng ở dòng cuối **tại DB** ⇒ không còn header, không còn dòng.
- Quy trình lưu lại: đảo thứ tự hai công đoạn, bỏ công đoạn đang là "công đoạn trước" của công đoạn khác ⇒ id giữ nguyên,
  liên kết viết lại đúng.
- Nhập BOM 2 nhóm: validate không tạo BOM; commit hỏng ở **nhóm cuối** ⇒ nhóm đầu cũng không còn, batch vẫn `READY`; commit
  lại tạo đúng 2 BOM nháp, `quantityPer = 2.5`.
- Migration trên dữ liệu cũ: số version theo thứ tự tạo, mã BOM trùng (khác hoa thường) được thêm hậu tố, `outputQuantity = 1`.

**Kiểm chứng "test có bắt được lỗi thật không":** cố ý phá 7 chỗ (bỏ khoá sản phẩm khi activate, bỏ transaction của lưu nháp,
bỏ chia `capacityUnits` khi xem trước, quay lại "dồn tải vào ngày bắt đầu", đổi `OPERATION_SEQUENCE_CONFLICT` sang một mã 409
khác, bỏ ép kiểu ở ô tìm kiếm ca, bỏ bước tạm khi đảo thứ tự công đoạn) — **7/7 lần test đỏ đúng chỗ**; đã khôi phục.

### 7.2 Kiểm chứng qua HTTP thật

- **Môi trường:** backend local (profile dev), PostgreSQL + Redis thật qua Docker, tài khoản quản trị. Migration mới chạy được
  trên DB dev có sẵn dữ liệu demo.
- **Lượt `231527`** (15/10, ~23:15): 92 lượt gọi, mọi HTTP status đúng kỳ vọng (một bước dựng dữ liệu kỳ vọng sai `200` cho
  giữ chỗ vật tư, server trả `201` — không phải lỗi). Công ty thử `GD5-231527`.

| Ca FE | Kết quả | `X-Trace-Id` |
|---|---|---|
| A01 lưu nháp 10 / 25 / 0,05 | 200, `quantityPer 2.500000`, `version 0` | `30988e3cb975486f` |
| A02 7 số lẻ / hao hụt 1 / đầu ra 0 / thương 1÷3 | 400 ×4, đúng field | `980b7ed61d66487c`, `8d11f275b84b44dd`, `c0494f972e26404a`, `85cc65906cb94466` |
| A03 thành phần là thành phẩm / tự làm thành phần + trùng / mã trùng | 422 / 400 (2 field) / 409 | `d20f344e89d74d8b`, `1c903cd41a3745e7`, `f2a6e57041334a9b` |
| A05 sửa đúng version / version cũ | 200 / 409 `CONCURRENT_MODIFICATION` | `b82717155dbb43f6`, `ff9971d61c6d408f` |
| A06 activate version cũ / đúng; sửa bản ACTIVE; sao chép | 409 / 200 / 409 `STATE_CONFLICT` / 201 nháp `revision 2` | `a9d1356f39c84619`, `cba59f3b5ce44803`, `3134188dea1b420a`, `3566e8ad65044ebc` |
| A07 activate bản sao | 200; DB còn đúng 1 ACTIVE cho sản phẩm | `cf52d87ec2cb4dcb` |
| A09 hiệu lực 2099 | 409 `EFFECTIVE_DATE_FUTURE` | `d6024237fd7c4591` |
| A10 cây 2 cấp | 200, node có `outputQuantity`/`componentQuantity` | `d34d24486b1648a6` |
| A11 nhập 1 nhóm 2 thành phần | 201 READY, không tạo BOM; commit 200, 1 BOM nháp 2 dòng | `04b496e9287f43b2`, `44e9cb062f3b47a6` |
| A13 nhóm lệch lượng đầu ra + trùng thành phần + % 100 | 201 INVALID, lỗi theo dòng | `d41cfde6e6fa4b0f` |
| A14 nhập lại cùng file | 201 INVALID: mã + sản phẩm `ALREADY_EXISTS` | `97eb3de46d3b4f97` |
| A17 commit lại cùng key | cùng `committedAt`, cùng `createdIds` (3 lần đọc) | `b024756a036d4ba3`, `bfd2201e9761420b` |
| A19 upload lại cùng key + file | cùng batch `id` | `ea6aec445f5a4756` |
| A20 ca 08–17 nghỉ 12–13 / nghỉ chồng / 0 phút | 201 `workingMinutes 480` / 422 / 422 | `4874ec7f6dbd4534`, `b551322fdfb04f0c`, `1beed30661b24ea9` |
| A21 ca 22–06 nghỉ 02–02:30 | 201 `workingMinutes 450` (đuôi 330 — test tự động) | `2438eb47f6224f54` |
| A23 ngừng ca / lịch đang dùng | 422 `REFERENCE_IN_USE` ×2 | `8037259fbc224ab2`, `59e547fcf7054d88` |
| A24 trung tâm 0 đơn vị | 400 | `6d36e403086643f6` |
| A25 công đoạn trước sai / đúng | 400 / 200 | `6ba8807a27b842a6`, `0233adff36ae4ad6` |
| A26 activate quy trình; nháp thứ hai; sao chép khi có nháp | 200 / 409 `OPEN_DRAFT_EXISTS` ×2 | `ad09911b17f7463d`, `903d2dcd8ddd484e`, `1d752ce001064137` |
| Q9 ngừng trung tâm có quy trình ACTIVE | 422 `REFERENCE_IN_USE` | `ec605da29ae9490f` |
| A27 xem trước 500 cái | 1030 / 960 / 70, kết thúc 08:35 hôm sau (§4.3) | `a364f3e218724e32` |
| A28 xem trước quy trình nháp | 409 `STATE_CONFLICT` | `31d044df7f8f454f` |
| A29 lượng 0 / bắt đầu ngoài kỳ | 400 / 400 | `58277b39636d45b6`, `6c3bdb81af4b4ad1` |
| A30 xem trước 2 lần | DB không đổi | `4f778fd50b2341c0` |
| A31 tải theo tuần / bảng công suất | 200, nền 130 | `d41c6d0c8067443c`, `a3145b1457da47ac` |
| A32 chồng công đoạn trước / lý do 3 ký tự / công đoạn 1 kéo dài đè công đoạn 2 | 409 / 400 / 409 | `0fb3b4798ece4533`, `19a732f4c4e346ec`, `8a20732fae394662` |
| A33 điều chỉnh đúng / version cũ | 200 / 409 | `2febdc4af2204428`, `8d374956bc954067` |
| Q11 91 ngày / 90 ngày | 400 / 200 | `ba6dab5a968b4ffd`, `a7821428d5b346ab` |
| `X-Plant-Id` sai | 409 `STATE_CONFLICT` | `0ee3403a962441f4` |

### 7.3 Chưa nghiệm thu

| Ca | Vì sao | Cần gì |
|---|---|---|
| A04 lỗi DB giữa chừng | Chỉ integration test (dòng cuối vượt kích thước cột), không dựng được qua HTTP | — |
| A07 hai activate **đồng thời qua HTTP** | Chỉ integration test 5 vòng | Bắn song song |
| A08 BOM vòng | Lượt live dựng BOM 2 cấp nhưng **không** vòng; vòng chỉ có test tự động (mã `BOM_CIRCULAR_REFERENCE` có từ 07/2026) | Dựng A↔B qua API |
| A12 file > 5 MiB / > 500 dòng / sai header | Dùng chung bộ đọc của Giai đoạn 3 (đã nghiệm thu ở đó); riêng mẫu BOM chỉ có test tự động cho sai mẫu | |
| A15, A16, A18 commit stale / lỗi nhóm 3 / hai batch đồng thời | Chỉ integration test (lỗi nhóm cuối) và test Giai đoạn 3 | |
| A22 lịch 7 ngày / trùng ngày | Không chạy live | |
| A34, A35 tài khoản không đủ quyền, thu hồi quyền | Lượt live chỉ dùng tài khoản quản trị; quyền xem trước có test tự động 6 tổ hợp | Tài khoản thiếu từng quyền |
| A36 snapshot Lệnh SX khi đổi master | Hành vi có từ trước (`B12`, `B49`), không đo lại | |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm:**

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Quy trình theo Nhà máy | Đổi phạm vi là migration dữ liệu + đổi hoạch định; lợi ích (lọc theo nhà máy) đã có bằng `plantId` | Quyết định riêng |
| Ca nhiều khung giờ, hiệu lực trên ca | Viết lại toàn bộ cách tính giờ làm; mô hình hiện tại biểu diễn được A20/A21 | Có ca thật không biểu diễn được bằng khung + nghỉ |
| `shiftIds[]` trên trung tâm | Hai nguồn sự thật cho "trung tâm làm giờ nào" | Như trên |
| Endpoint gộp `POST /{id}/status` | Đã có activate/deactivate riêng | — |
| Số thập phân dạng chuỗi | Toàn hệ thống dùng JSON number | Quyết định chung |
| Timezone Công ty | Công ty không có timezone; `effectiveFrom` là thời điểm | |
| Xếp lịch song song theo công đoạn trước; chụp công đoạn trước vào Lệnh SX | FE Q17 chốt tuần tự | Phase lập lịch |
| Release chia `capacityUnits` như xem trước | Đổi lịch mọi Lệnh SX release sau này | Quyết định riêng |
| Idempotency-Key cho lưu nháp/sao chép | `expectedVersion` đủ chống ghi đè; key cần bảng lưu riêng | Nếu FE gặp lỗi mạng tạo bản trùng |

**Việc FE cần làm tiếp:**

Bắt buộc:
- [ ] Form BOM: gửi `PUT /bom-drafts` với `componentQuantity` theo lô; giữ `version` để gửi lại `expectedVersion`; bắt
      `EFFECTIVE_DATE_FUTURE`, `CONCURRENT_MODIFICATION`, `RESOURCE_INACTIVE`, lỗi field `lines[i].componentQuantity`.
- [ ] `effectiveFrom` gửi **thời điểm** (00:00 giờ VN = `T17:00:00Z` ngày trước).
- [ ] Quy trình: dùng đường dẫn Công ty + lọc `plantId`; đổi tên field theo bảng ánh xạ §2; giữ `lockVersion`.
- [ ] Ca/Lịch/Trung tâm: ánh xạ `workingIntervals` → một khung + giờ nghỉ, `weeklySchedule` → `weeklyShifts`; bỏ `shiftIds[]`.
- [ ] Bắt `REFERENCE_IN_USE` (422), `OPEN_DRAFT_EXISTS`, `OPERATION_SEQUENCE_CONFLICT`, `SCHEDULING_UNAVAILABLE`.
- [ ] Điều chỉnh công đoạn: gửi `plannedStartAt` (+ tuỳ chọn `plannedEndAt`), lý do ≥ 5 ký tự.
- [ ] Nhập BOM: `kind=bom`, đọc `bomCount`; `createdIds` là id BOM.

Nên làm:
- [ ] Bảng công suất: dùng `GET /capacity/loads` cho tổng, `capacity-board` cho từng công đoạn; hiển thị `inPeriodMinutes`.
- [ ] Chạy A01–A36 trên môi trường FE và báo kết quả theo mã ca.

**Thứ tự deploy:** không ràng buộc.

---

**Tin nhắn kèm (Zalo):**

```
[BE→FE] Giai đoạn 5 — BOM, quy trình, ca/lịch/trung tâm, công suất
Mức: P1 · Breaking: CÓ
Cần FE làm: lưu nháp BOM/quy trình một lần, map field theo §2 (quy trình theo Công ty, ca 1 khung + nghỉ, trung tâm không shiftIds), bắt mã lỗi mới
File: BE_RESPONSE_GD_05_2026-10-16.md
Thứ tự deploy: không ràng buộc
```

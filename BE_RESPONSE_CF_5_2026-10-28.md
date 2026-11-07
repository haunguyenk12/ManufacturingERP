# BE trả lời — Một công thức lịch cho xem trước công suất và phát hành lệnh SX

**Trả lời cho:** `FE_REQUEST_CAPACITY_RELEASE_ALIGNMENT_2026-10-20.md` (v1) · **Ngày gửi:** 28/10/2026 · **Bản:** v1
**Mức:** P1 · **Breaking:** CÓ (lịch của lệnh phát hành mới đổi, §3) · **Thứ tự deploy:** không ràng buộc

---

## §0 — Kết luận trong một câu, và bảng phân loại

Phát hành lệnh SX nay xếp lịch bằng đúng công thức của xem trước công suất nhà máy — chia cho `capacityUnits` của trung tâm, đi theo giờ làm việc của cùng lịch — và chụp lại số tổ, số phút cần, số phút chạy lên từng công đoạn.

| # | FE nêu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | Cùng nguồn ⇒ xem trước và phát hành ra cùng cửa sổ | 🔴 **Lệch thật, đã sửa** | §4: xem trước lịch của lệnh = phát hành từng giây; xem trước công suất nhà máy = phát hành (test tự động so hai đường) |
| R02 | phút cần = setup + lượng × run; phút chạy = phút cần / số tổ đã chụp; ngắt giờ nghỉ theo cùng lịch | 🔴 **Đã sửa** | §4: Cut 30 + 6 × 10 = 90 phút cần, 2 tổ ⇒ 45 phút, 08:00 → 08:45 giờ VN |
| R03 | Nguồn đổi giữa xem trước và phát hành ⇒ không dùng xem trước cũ làm lịch bảo đảm | ✅ **Đã đúng** | Phát hành luôn tính lại từ nguồn hiện tại, không đọc kết quả xem trước; `calendarRevision` có ở cả hai để FE so |
| R04 | Lệnh đã phát hành giữ lịch; đổi lịch / số tổ không dời | 🟡 **Đã làm** | §4: đổi số tổ 2 → 1 sau phát hành, lịch và số đã chụp không đổi |
| R05 | Ảnh chụp đủ giải thích số tổ / phút đã dùng và phụ thuộc giữa công đoạn | 🟡 **Một phần** | Có `capturedCapacityUnits`, `requiredResourceMinutes`, `activeElapsedMinutes`. **Chưa** chụp phụ thuộc công đoạn (§8) |

## §1 — Nguyên nhân thật

Từ GĐ5 (16/10), xem trước công suất nhà máy chia thời lượng cho `capacityUnits`, còn phát hành thì không — và hai bên dùng hai cách đi lịch khác nhau. Trung tâm có từ 2 tổ trở lên thì phát hành ra lịch dài hơn xem trước. Phát hành và xem trước lịch của lệnh luôn khớp nhau (cùng một hàm); chỗ lệch là giữa hai bên đó và xem trước công suất nhà máy. Lệnh đã phát hành trước bản này **không** bị tính lại; ba field mới của chúng là `null`.

Ba thay đổi hành vi đi kèm, áp cho lệnh phát hành mới:
1. Lịch chia `capacityUnits` (2 tổ ⇒ nửa thời gian làm việc).
2. Công đoạn trên lịch **bắt đầu ở phút làm việc đầu tiên** (trước đây bắt đầu đúng mốc con trỏ, kể cả khi mốc nằm ngoài ca).
3. Giờ kết thúc làm tròn lên **theo giây** (trước đây theo phút) — áp cả trung tâm không có lịch: 9.5 phút nay kết thúc ở +570 giây, trước là +600.

## §2 — Thay đổi contract: bảng trước / sau

Field mới trên mỗi công đoạn của response lệnh SX (`operations[]`):

| Field | Kiểu | Nullable | Nguồn giá trị | Ví dụ thật (§4, công đoạn Cut) |
|---|---|---|---|---|
| `capturedCapacityUnits` | integer | có — `null` trước khi phát hành, và với lệnh phát hành trước bản này | `capacityUnits` của trung tâm **lúc phát hành** (không có trung tâm ⇒ 1) | `2` |
| `requiredResourceMinutes` | number | có — như trên | `setupMinutes + runMinutesPerUnit × plannedQuantity`, 6 số lẻ | `90.0` |
| `activeElapsedMinutes` | number | có — như trên | `requiredResourceMinutes / capturedCapacityUnits`, 6 số lẻ | `45.0` |

`workMinutes` giữ nghĩa cũ (`ceil(setup + run × lượng)`, phút công suất). Bảng công suất (`capacity-board`, `capacity/loads`) **không đổi**: tải vẫn là phút công suất chưa chia; sức chứa ngày đã nhân `capacityUnits`.

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /api/v1/work-orders/{id}/release` | trung tâm có `capacityUnits > 1` | lịch không chia | lịch chia `capacityUnits` | Đổi số | Không sửa code; lịch ngắn lại |
| `POST /api/v1/work-orders/{id}/release`, `POST /api/v1/work-orders/{id}/schedule-preview` | mốc bắt đầu nằm ngoài ca | công đoạn bắt đầu ở mốc | bắt đầu ở phút làm việc đầu tiên | Đổi số | Không |
| như trên + `POST …/operations/{opId}/schedule-adjustments` khi bỏ trống `plannedEndAt` | mọi lệnh mới | kết thúc làm tròn theo phút | theo giây | Đổi số | Không |

Không đổi mặc định query param, thứ tự sắp xếp, không bỏ field nào. Hình dạng response không đổi ngoài 3 field thêm.

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Fixture: ca 08:00–17:00 nghỉ 12:00–13:00 (giờ VN), trung tâm 2 tổ, quy trình Cut (setup 30, run 6) + Weld (run 3), lệnh 10 cái bắt đầu thứ Hai 08:00 giờ VN (01:00Z).

### §4.1 Xem trước lịch của lệnh

```http
POST /api/v1/work-orders/dd048585-357c-426f-ae4e-a28ef48b9fee/schedule-preview
Content-Type: application/json

{}
```

HTTP **200** · `X-Trace-Id: aa4a0da487ce4923`

```json
{"code":"SUCCESS","result":{"workOrderId":"dd048585-357c-426f-ae4e-a28ef48b9fee","version":0,"timeZone":"Asia/Ho_Chi_Minh","calendarRevision":"7d4f357e687fe93b","quantity":10.000000,"plannedStartAt":"2026-11-02T01:00:00Z","rows":[{"workOrderOperationId":"21124f77-f258-4175-b35a-e5a1f2394d99","sequence":10,"name":"Cut","workCenterId":"eebdd3ba-9c43-42a1-9ce4-43a75d294077","workCenterCode":"WC-170603","startsAt":"2026-11-02T01:00:00Z","endsAt":"2026-11-02T01:45:00Z","workMinutes":90,"onCalendar":true,"overlappingOperations":0},{"workOrderOperationId":"9664b39e-d723-4bad-9e51-763d57dc901d","sequence":20,"name":"Weld","workCenterId":"eebdd3ba-9c43-42a1-9ce4-43a75d294077","workCenterCode":"WC-170603","startsAt":"2026-11-02T01:45:00Z","endsAt":"2026-11-02T02:00:00Z","workMinutes":30,"onCalendar":true,"overlappingOperations":0}],"warnings":[],"generatedAt":"2026-10-28T10:06:06.169125Z"},"message":"OK"}
```

### §4.2 Phát hành — cùng cửa sổ với xem trước, kèm số đã chụp

```http
POST /api/v1/work-orders/dd048585-357c-426f-ae4e-a28ef48b9fee/release
Content-Type: application/json

{"expectedWorkOrderVersion": 1}
```

HTTP **200** · `X-Trace-Id: 388ad2c95b6f419e`

```json
{"code":"SUCCESS","result":{"workOrderId":"dd048585-357c-426f-ae4e-a28ef48b9fee","companyId":"2d3d2119-71e5-43ff-8024-a2400b2a8839","plantId":"23ede658-1df7-4a70-a5cf-a9aadd456778","plantCode":"P-170603","workOrderNo":"WO5-170603","productItemId":"2d61d573-a24a-45f1-8efc-eda5c54a6173","productItemCode":"FG-170603","productItemName":"Finished good","outputUom":"EA","bomId":"2e401ff6-2784-422b-af7a-6c1bf04e7309","bomRevision":"R1","bomCapturedAt":"2026-10-28T10:06:06.134564Z","sourceRoutingId":"ab32ea96-29ed-42c4-9179-625cb2029a89","sourceRoutingCode":"RT-170603","sourceRoutingVersion":"1","routingCapturedAt":"2026-10-28T10:06:06.132795Z","planningRunId":null,"planningRunCode":null,"planningProposalId":null,"outputWarehouseId":"7bac77c0-a6e3-4582-a029-acae655a08db","outputWarehouseCode":"FG-170603","plannedQuantity":10.000000,"completedQuantity":0.000000,"remainingQuantity":10.000000,"actualGoodQuantity":0.000000,"actualScrapQuantity":0.000000,"actualReworkQuantity":0.000000,"availableToReceipt":0.000000,"status":"RELEASED","plannedStartAt":"2026-11-02T01:00:00Z","plannedEndAt":null,"releasedAt":"2026-10-28T10:06:06.241007100Z","executionStartedAt":null,"executionCompletedAt":null,"completedAt":null,"cancelledAt":null,"cancelReason":null,"blockedAt":null,"blockReason":null,"notes":null,"createdAt":"2026-10-28T10:06:06.134564Z","updatedAt":"2026-10-28T10:06:06.253044800Z","componentLines":[{"componentLineId":"75bc704c-1b91-440d-a114-96feab2ad8e9","bomLineId":"5ae561da-1398-43bd-a330-8357a7cae387","lineNo":10,"componentItemId":"1f4dbd46-3d02-4ed4-8223-2b92f245d7c7","componentItemCode":"RM-170603","componentItemName":"Raw material","uom":"KG","quantityPer":2.000000,"scrapRate":0.000000,"requiredQuantity":20.000000,"reservedQuantity":20.000000,"issuedQuantity":0.000000,"remainingQuantity":20.000000,"warehouseId":null,"warehouseCode":null}],"operations":[{"workOrderOperationId":"21124f77-f258-4175-b35a-e5a1f2394d99","sourceRoutingOperationId":"721a50eb-23ea-4ed4-9cdb-dbb29b72de0b","sequence":10,"name":"Cut","workCenterCode":"WC-170603","workCenterId":"eebdd3ba-9c43-42a1-9ce4-43a75d294077","setupMinutes":30.000000,"runMinutesPerUnit":6.000000,"plannedStartAt":"2026-11-02T01:00:00Z","plannedEndAt":"2026-11-02T01:45:00Z","version":1,"workMinutes":90,"capturedCapacityUnits":2,"requiredResourceMinutes":90.000000,"activeElapsedMinutes":45.000000},{"workOrderOperationId":"9664b39e-d723-4bad-9e51-763d57dc901d","sourceRoutingOperationId":"926688c5-d50b-46e1-95aa-748a20efb006","sequence":20,"name":"Weld","workCenterCode":"WC-170603","workCenterId":"eebdd3ba-9c43-42a1-9ce4-43a75d294077","setupMinutes":0.000000,"runMinutesPerUnit":3.000000,"plannedStartAt":"2026-11-02T01:45:00Z","plannedEndAt":"2026-11-02T02:00:00Z","version":1,"workMinutes":30,"capturedCapacityUnits":2,"requiredResourceMinutes":30.000000,"activeElapsedMinutes":15.000000}],"allocations":[],"closedAt":null,"version":2,"scheduleRevision":1,"calendarRevision":"7d4f357e687fe93b","scheduleTimeZone":"Asia/Ho_Chi_Minh","scheduleCapturedAt":"2026-10-28T10:06:06.241007Z","inputKnown":true,"consumptionKnown":true,"finalGoodQuantity":0.000000,"historicalReworkQuantity":0.000000,"terminalShortfallQuantity":null,"shortfallReason":null},"message":"OK"}
```

### §4.3 Bảng tải của ngày đó — tải vẫn là phút công suất chưa chia

(Gọi **sau** khi đổi trung tâm về 1 tổ, nên `capacityUnits` hiện 1; tải của lệnh vẫn 120 = 90 + 30.)

```http
GET /api/v1/plants/23ede658-1df7-4a70-a5cf-a9aadd456778/capacity/loads?periodStart=2026-11-02&periodEnd=2026-11-02
Content-Type: application/json
```

HTTP **200** · `X-Trace-Id: b4b45fec389f4ffb`

```json
{"code":"SUCCESS","result":{"plantId":"23ede658-1df7-4a70-a5cf-a9aadd456778","timezone":"Asia/Ho_Chi_Minh","periodStart":"2026-11-02","periodEnd":"2026-11-02","generatedAt":"2026-10-28T10:06:06.357351Z","loads":[{"workCenterId":"eebdd3ba-9c43-42a1-9ce4-43a75d294077","workCenterCode":"WC-170603","workCenterName":"Cell","capacityUnits":1,"baseInPeriodMinutes":120.000000,"previewRequiredMinutes":0.000000,"demandMinutes":120.000000,"allocatedMinutes":120.000000,"availableMinutes":480.000000,"overloadMinutes":0.000000,"utilizationPercent":25.00,"status":"AVAILABLE"}]},"message":"OK"}
```

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — dùng cùng một envelope:

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "quantity", "message": "must be greater than 0" } ] }
```

Không có mã lỗi mới. Phát hành vẫn giữ các lỗi cũ (thiếu `expectedWorkOrderVersion` ⇒ 400; version lệch ⇒ 409 `CONCURRENT_MODIFICATION`; thiếu vật tư ⇒ 409 `STATE_CONFLICT` + `BLOCKED`). Lịch không có giờ làm việc trong 3650 ngày ⇒ 422 `OPERATION_NOT_ALLOWED` (như trước).

## §6 — Quyền, phạm vi và giới hạn đã biết

- Quyền không đổi: phát hành `PERM_WORK_ORDER_MANAGE`; xem trước lịch của lệnh `PERM_WORK_ORDER_READ`.
- **Trả lời Q1:** trước bản này **chưa** dùng chung; từ bản này có. **Q2:** **có**, chỉ lệnh phát hành mới; lệnh đã phát hành giữ nguyên. **Q3:** **chưa** — xem §8.
- `calendarRevision` không gồm `capacityUnits`; số tổ nằm trên từng công đoạn (`capturedCapacityUnits`).

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — **2219 unit + 332 IT**, 0 failures, 0 errors. Có đủ ba ví dụ của FE: 2 tổ từ 08:00 ⇒ 09:00; 1 tổ ⇒ 10:00; bắt đầu 11:30, 2 tổ, 120 phút cần ⇒ 13:30 (vắt qua giờ nghỉ) — cho cả công thức chung lẫn bộ xếp lịch của phát hành |
| So hai đường | Một test cho cùng quy trình / trung tâm / lịch vào xem trước công suất nhà máy **và** phát hành, so từng công đoạn với nhau (không so với hằng số) |
| Mutation | 9/9 bị test bắt (bỏ chia số tổ · điều chỉnh lịch dùng số tổ hiện tại thay vì đã chụp · bảng tải cũng chia · làm tròn xuống thay lên ×2 · không neo phút làm việc đầu tiên · không lưu số tổ · bỏ ràng buộc "đủ ba hoặc không có" · bỏ cắt cửa sổ ở cuối kỳ) |
| HTTP thật | §4: phát hành Cut 01:00Z → 01:45Z, Weld 01:45Z → 02:00Z, chụp 2/90/45 và 2/30/15; xem trước lịch = phát hành; đổi trung tâm 2 → 1 tổ sau phát hành ⇒ lịch và số đã chụp không đổi |
| Môi trường | profile dev, jar cổng 8090, Postgres + Redis docker, DB dev có dữ liệu demo (migration mới chạy sạch trên đó); đo 28/10/2026 lúc 17:06 (giờ VN) |

**Chưa nghiệm thu qua HTTP:** so khớp với `POST /api/v1/plants/{plantId}/capacity/preview` (xem trước công suất nhà máy) — chỉ có test tự động so hai đường.

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Chụp phụ thuộc giữa công đoạn (`predecessorOperationIds`) lên lệnh SX (Q3, R05) | Cần thêm bảng ảnh chụp thứ hai; lịch hiện vẫn chạy tuần tự theo `sequence` nên phụ thuộc chưa ảnh hưởng số | PO chốt cần giải thích phụ thuộc trên lệnh |

**FE cần làm tiếp**
- [ ] (nên làm) Hiện 3 field mới ở chi tiết công đoạn; `null` = lệnh phát hành trước bản này.
- [ ] (nên làm) Báo người dùng lịch của trung tâm nhiều tổ sẽ ngắn hơn trước.

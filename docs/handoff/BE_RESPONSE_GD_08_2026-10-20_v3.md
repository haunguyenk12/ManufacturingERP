# BE trả lời — Giai đoạn 8: Lệnh sản xuất, cấp vật tư, công đoạn và dở dang

**Trả lời cho:** `FE_HANDOFF_GD_08_2026-10-14.md` (v1, P1) · **Ngày gửi:** 20/10/2026 · **Bản:** v3 — **phần 3/4: công đoạn (`G8c`)**
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. Bản này **bổ sung** v1 (nháp lệnh) và v2 (cấp vật tư), không thay chúng. Còn
v4 (dở dang + hoàn vật tư). Chưa phải "FE nghiệm thu đạt": task chỉ đóng khi FE chạy A01–A40 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Lệnh có quy trình nay chạy từng công đoạn theo thứ tự: xác nhận đầu vào, báo sản lượng lượt mới hoặc lượt xử lý lại, ghi vật tư thực dùng, kết thúc
công đoạn, rồi hoàn thành lệnh (kể cả khi thiếu sản lượng, có lý do và quyền duyệt). Sản lượng tốt của lệnh nay chỉ lấy từ công đoạn cuối.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R10 | Sổ đầu vào / chuyển giao, số còn lại do server tính; công đoạn trước chưa kết thúc thì không nhận đầu vào / không báo sản lượng; không suy sản lượng từ vật tư | 🟡 **Đã làm** | Đầu vào trước khi cấp vật tư ⇒ `409 MATERIAL_ISSUE_REQUIRED`; chuyển 10 khi còn 9 ⇒ `409` field `quantity` (§4.1, §4.5) |
| R11 | Lượt mới ≤ đầu vào còn lại; lượt xử lý lại ≤ phần chờ của lượt nguồn cùng lệnh + công đoạn; lịch sử / chờ tách riêng | 🟡 **Đã làm** | 8 + 1 + 2 trên đầu vào 10 ⇒ `409`; xử lý lại 2 khi nguồn còn 1 ⇒ `409` (§4.2, §4.3) |
| R12 | Sản lượng tốt của lệnh = công đoạn cuối, không cộng mọi công đoạn | 🟡 **Đã làm** | Good 8 ở công đoạn đầu ⇒ lệnh vẫn `0`; good 9 ở công đoạn cuối ⇒ lệnh `9` (§4.2, §4.5) |
| R13 | Vật tư dùng theo dòng cấp `POSTED` của chính lệnh, ≤ cấp − đã dùng, cộng mục trùng, cùng transaction, không trừ kho | 🟡 **Đã làm** | 16 + 2 cùng dòng ⇒ một hàng 18; tồn kho không đổi; 3 khi còn 2 ⇒ `409` field `consumption[0].quantity` (§4.2, §4.3) |
| R14 | Kết thúc khi đầu vào / làm lại hết và đã chuyển đủ; công đoạn sau kết thúc được với 0 khi công đoạn trước good 0; hoàn thành thiếu có lý do của quản lý, không tự đóng / nhập kho / trừ kho | 🟡 **Đã làm** | §4.4, §4.6 |
| R22 (phần công đoạn) | Version + idempotency trên mọi lệnh | 🟡 **Đã làm** | Thiếu ⇒ `400` field; lệch ⇒ `409`; gửi lại cùng key ⇒ trạng thái hiện tại, version không đổi |
| R15–R17 | Dở dang, hoàn vật tư | ⏳ **Phần v4** | — |

### Trả lời Q (những câu thuộc phần này)

| Q | Trả lời |
|---|---|
| Q4 | **Có.** `complete-production` cần `PERM_WORK_ORDER_MANAGE`; khi thiếu sản lượng cần **thêm** `PERM_MATERIAL_ISSUE_APPROVE`, thiếu ⇒ `403 PERMISSION_DENIED` (§4.6). Không có mã lỗi riêng `WORK_ORDER_SHORTFALL_APPROVAL_REQUIRED` — dùng `PERMISSION_DENIED` như mọi chỗ thiếu quyền |
| Q14 | **Có.** Đầu vào công đoạn đầu cần ít nhất một phiếu cấp `POSTED`; trần là **kế hoạch** (`plannedQuantity`), không chia lượng vật tư ra số sản phẩm |
| Q15 | **Có.** Tuần tự: công đoạn sau chỉ nhận chuyển giao sau khi công đoạn trước **kết thúc**, tối đa `good − đã chuyển` của công đoạn trước. Không có quy trình song song |
| Q16 | **Có.** Công đoạn trước kết thúc với good 0 ⇒ công đoạn sau kết thúc được với đầu vào 0 (§4.4). Kèm theo A25: công đoạn **đầu** chỉ kết thúc khi đã nhận đủ kế hoạch — phần thiếu ghi thành phế |
| Q17 | Đã trả lời ở v2: phiếu cấp bổ sung nguồn `REWORK` chỉ cần lượt nguồn có `reworkQuantity > 0`, **không** đòi phần chờ còn > 0 — cấp thêm vật tư và lượt xử lý lại là hai việc khác nhau |
| Q18 | **Có.** `consumption` rỗng hoặc vắng được nhận. BE không tự suy `good × BOM` |
| Q22 | **Có.** Lệnh đã báo sản lượng **trước** bản này: `inputKnown = false`, đầu vào / trần / `finalGoodQuantity` trả `null` (không đoán 0 hay "đã cấp hết"); đầu vào, xử lý lại, vật tư dùng ⇒ `409 PRODUCTION_INPUT_UNKNOWN`. Báo sản lượng của lệnh đó giữ luật cũ (§4.7). Trên dev: 4 lệnh rơi vào nhóm này, 19 lệnh còn lại theo dõi được |
| Q23 | **Có.** Hoàn thành chặn khi còn phiếu cấp chờ duyệt, còn đầu vào / làm lại chưa xử lý (qua điều kiện "mọi công đoạn đã kết thúc"); vật tư chưa dùng vẫn còn đó cho phần hoàn vật tư (v4). Không tự nhập kho, không tự đóng |
| Q31 | **Có.** `actualEndedAt` được lệch tối đa **60 giây** sau giờ server; xa hơn ⇒ `400` field `actualEndedAt`; kết thúc trước bắt đầu vẫn `400` như cũ |
| Q26, Q34, Q37 | Trả lời ở v4 |

---

## §1 — Nguyên nhân thật

Không có lỗi FE báo — đây là luồng mới. Ba điều ảnh hưởng tới FE:

1. **`POST /work-orders/{W}/production-executions` bắt buộc `expectedWorkOrderVersion`**, và lệnh có quy trình bắt buộc `workOrderOperationId`.
   Body cũ nhận `400` ngay khi BE deploy (§3).
2. **`actualGoodQuantity` của lệnh đổi nghĩa:** trước đây cộng good của **mọi** lần báo, nay chỉ good của **công đoạn cuối**. Lệnh hai công đoạn báo 8
   ở công đoạn đầu trước đây hiện 8, nay hiện 0 cho tới khi công đoạn cuối báo. Phế và làm lại vẫn là tổng mọi công đoạn.
3. **Công đoạn không lưu bộ đếm.** Mọi số của một công đoạn (đầu vào, còn lại, chờ làm lại, đã chuyển…) tính từ sổ đầu vào và các lần báo sản lượng —
   không có cột nào có thể lệch khỏi sổ. Thứ duy nhất lưu là lần kết thúc công đoạn. Đây là khác biệt có chủ đích so với khuyến nghị ban đầu
   "thêm bộ đếm lên công đoạn".

Nghiệm thu HTTP lần này **không** tìm ra lỗi nào của BE (các lần chạy hỏng là lỗi kịch bản kiểm thử). Nghiệm thu mutation tìm ra một chỗ test chưa khoá
(phần chờ làm lại phải trừ phần đã xử lý lại) — đã thêm test trước khi gửi. Đối chiếu lại bảng ca nghiệm thu của FE trước khi gửi tìm ra một luật BE đã
nới so với FE: **A25 — công đoạn đầu chỉ kết thúc khi đã nhận đủ kế hoạch**. Đã sửa theo FE, thêm test + mutation, chạy lại smoke.

---

## §2 — Thay đổi contract

### 2.1 Endpoint

| Method | Path | Mới / đổi | Quyền |
|---|---|---|---|
| GET | `/api/v1/work-orders/{W}/operations` | **Mới** — công đoạn + các nhóm số | `PERM_PRODUCTION_EXECUTION_READ` **hoặc** `_MANAGE` |
| POST | `/api/v1/work-orders/{W}/operations/{O}/input` | **Mới** — body `{expectedWorkOrderVersion, quantity}`; `Idempotency-Key` tuỳ chọn | `PERM_PRODUCTION_EXECUTION_MANAGE` |
| POST | `/api/v1/work-orders/{W}/operations/{O}/finish` | **Mới** — body `{expectedWorkOrderVersion}`; `Idempotency-Key` tuỳ chọn | `PERM_PRODUCTION_EXECUTION_MANAGE` |
| POST | `/api/v1/work-orders/{W}/complete-production` | **Mới** — body `{expectedWorkOrderVersion, reason?}`; `Idempotency-Key` tuỳ chọn | `PERM_WORK_ORDER_MANAGE` (+ `PERM_MATERIAL_ISSUE_APPROVE` khi thiếu) |
| POST | `/api/v1/work-orders/{W}/production-executions` | **Đổi:** thêm `expectedWorkOrderVersion` **bắt buộc**, `sourceExecutionId?`, `consumption[]?` (≤ 500); `workOrderOperationId` **bắt buộc** khi lệnh có quy trình; số lượng tối đa 6 chữ số lẻ; `Idempotency-Key` vẫn bắt buộc như cũ | `PERM_PRODUCTION_EXECUTION_MANAGE` |

### 2.2 Field mới trên response

| Field | Kiểu | Nullable? | Nguồn giá trị | Ví dụ thật |
|---|---|---|---|---|
| công đoạn `status` | `PENDING`·`READY`·`IN_PROGRESS`·`COMPLETED` | không | đã kết thúc ⇒ `COMPLETED`; có đầu vào / lần báo ⇒ `IN_PROGRESS`; công đoạn đầu hoặc công đoạn trước đã kết thúc ⇒ `READY` | `"IN_PROGRESS"` |
| công đoạn `inputKnown` | boolean | không | `false` với lệnh báo sản lượng trước bản này | `true` |
| công đoạn `inputQuantity`, `freshRemainingQuantity` | decimal | **có** — `null` khi `inputKnown = false` | tổng đầu vào; đầu vào − đã xử lý lượt mới | `10.000000`, `0.000000` |
| công đoạn `freshProcessedQuantity`, `goodQuantity`, `scrapQuantity`, `historicalReworkQuantity`, `pendingReworkQuantity` | decimal | không | tổng các lần báo của công đoạn; chờ = làm lại − đã xử lý lại | `9.000000`, `1.000000`, `0.000000` |
| công đoạn `transferredQuantity`, `transferableQuantity` | decimal | **có** — `null` ở công đoạn cuối hoặc khi `inputKnown = false` | đã chuyển sang công đoạn sau; good − đã chuyển | `0`, `9.000000` |
| công đoạn `executionCount`, `completedAt`, `completedBy`, `workOrderVersion` | int / ISO / UUID / int | `completedAt`/`completedBy` có | | `2`, `"2026-10-20T03:57:10.490791Z"`, `8` |
| execution `sourceExecutionId` | UUID | có — `null` với lượt mới | lượt nguồn của lượt xử lý lại | `"cfd5473b-…"` |
| execution `processedQuantity`, `retryUsedQuantity`, `pendingReworkQuantity` | decimal | không | good + phế + làm lại; phần làm lại của lượt này đã được xử lý lại; làm lại − đã xử lý lại | `10`, `0`, `1` |
| execution `consumption[]` | `{issueLineId, quantity}[]` | không (có thể `[]`) | một phần tử cho mỗi dòng cấp, đã cộng mục trùng | `[{"issueLineId":"d84446f9-…","quantity":18}]` |
| execution `durationMinutes`, `workOrderVersion` | int | `durationMinutes` có | phút giữa bắt đầu và kết thúc; version lệnh **sau** lần báo | `10`, `6` |
| lệnh `inputKnown`, `consumptionKnown` | boolean | không | như công đoạn | `true` |
| lệnh `finalGoodQuantity` | decimal | **có** — `null` khi `inputKnown = false` | good công đoạn cuối (= `actualGoodQuantity`) | `9.000000` |
| lệnh `historicalReworkQuantity` | decimal | không | = `actualReworkQuantity` dưới tên FE | `1.000000` |
| lệnh `terminalShortfallQuantity`, `shortfallReason` | decimal / string | có — `null` trước khi hoàn thành | `max(0, kế hoạch − good cuối)` chụp lúc hoàn thành; lý do đã trim | `1.000000`, `"Steel ran out"` |

### 2.3 Quy tắc

| Lệnh | Điều kiện | Kết quả |
|---|---|---|
| đầu vào, công đoạn đầu | chưa có phiếu cấp `POSTED` | `409 MATERIAL_ISSUE_REQUIRED` |
| đầu vào, công đoạn đầu | tổng đầu vào > kế hoạch | `409 PLANNED_QUANTITY_EXCEEDED` field `quantity` |
| đầu vào, công đoạn sau | công đoạn trước chưa kết thúc | `409 OPERATION_SEQUENCE_CONFLICT` |
| đầu vào, công đoạn sau | > good − đã chuyển của công đoạn trước | `409 PLANNED_QUANTITY_EXCEEDED` field `quantity` |
| đầu vào / kết thúc / báo sản lượng | công đoạn đã kết thúc | `409 STATE_CONFLICT` |
| báo sản lượng, lượt mới | good + phế + làm lại > đầu vào còn lại | `409 PLANNED_QUANTITY_EXCEEDED` field `goodQuantity` |
| báo sản lượng, xử lý lại | lượt nguồn khác lệnh hoặc khác công đoạn | `422 RESOURCE_SCOPE_MISMATCH` field `sourceExecutionId` |
| báo sản lượng, xử lý lại | > phần chờ của lượt nguồn | `409 PLANNED_QUANTITY_EXCEEDED` field `goodQuantity` |
| vật tư dùng | dòng cấp của lệnh khác | `422 RESOURCE_SCOPE_MISMATCH` field `consumption[i].issueLineId` |
| vật tư dùng | phiếu cấp chưa `POSTED` | `409 STATE_CONFLICT` field `consumption[i].issueLineId` |
| vật tư dùng | tổng của dòng (đã cộng mục trùng) > cấp − đã dùng | `409 PLANNED_QUANTITY_EXCEEDED` field `consumption[i].quantity` (`i` = mục đầu tiên của dòng đó) |
| báo sản lượng | `actualEndedAt` > giờ server + 60 s | `400 VALIDATION_ERROR` field `actualEndedAt` |
| kết thúc | còn đầu vào chưa xử lý / còn làm lại chờ / good công đoạn trước chưa chuyển hết | `409 STATE_CONFLICT` |
| kết thúc, công đoạn đầu | tổng đầu vào ≠ kế hoạch (FE A25 — thiếu thì ghi phế, không dừng đầu vào) | `409 STATE_CONFLICT` |
| hoàn thành | lệnh không `RELEASED`/`IN_PROGRESS`; còn phiếu cấp chờ duyệt; còn công đoạn chưa kết thúc | `409 STATE_CONFLICT` |
| hoàn thành, thiếu sản lượng | không có lý do | `400 APPROVAL_REASON_REQUIRED` field `reason` |
| hoàn thành, thiếu sản lượng | lý do < 5 hoặc > 2000 ký tự | `400 VALIDATION_ERROR` field `reason` |
| hoàn thành, thiếu sản lượng | không có `PERM_MATERIAL_ISSUE_APPROVE` | `403 PERMISSION_DENIED` |
| đầu vào / xử lý lại / vật tư dùng | lệnh báo sản lượng trước bản này | `409 PRODUCTION_INPUT_UNKNOWN` |

Lệnh **không** có quy trình: không cần đầu vào, để trống `workOrderOperationId`, trần vẫn là kế hoạch, xử lý lại vẫn dùng được (lượt nguồn cùng lệnh).
Đạt đủ kế hoạch ở công đoạn cuối vẫn **tự** chuyển `COMPLETED` như trước; `complete-production` là đường cho trường hợp **thiếu**. Công đoạn cuối kết
thúc được cả khi lệnh đã `COMPLETED`.

---

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /work-orders/{W}/production-executions` | không gửi `expectedWorkOrderVersion` | `201` | `400 VALIDATION_ERROR` field `expectedWorkOrderVersion` | **Bắt buộc mới** | gửi `version` của lệnh vừa đọc |
| như trên | lệnh có quy trình, không gửi `workOrderOperationId` | `201` | `400` field `workOrderOperationId` | **Bắt buộc mới** | chọn công đoạn |
| như trên | lệnh có quy trình, chưa xác nhận đầu vào công đoạn | `201` | `409 PLANNED_QUANTITY_EXCEEDED` (đầu vào còn lại 0) | **Bắt buộc mới** | gọi `.../operations/{O}/input` trước |
| như trên | báo ở công đoạn sau khi công đoạn trước chưa kết thúc | `201` | `409 OPERATION_SEQUENCE_CONFLICT` | **Chặn mới** | kết thúc công đoạn trước |
| như trên | `actualEndedAt` xa hơn 60 s sau giờ server | `201` | `400` field `actualEndedAt` | **Chặn mới** | dùng giờ thật |
| như trên | số lượng có chữ số lẻ thứ 7 | lưu (làm tròn) | `400` | **Chặn mới** | giới hạn 6 chữ số lẻ |
| `GET /work-orders/{W}`, response báo sản lượng | lệnh nhiều công đoạn | `actualGoodQuantity` = tổng good mọi lần báo | = good công đoạn cuối | **Đổi số liệu** | đọc như "sản lượng tốt cuối" |
| như trên | lệnh báo sản lượng trước bản này | `actualGoodQuantity` | giữ số cũ; `finalGoodQuantity = null` | **Mới** | hiện "không xác định" thay vì 0 |

Additive: 4 endpoint, 7 field execution, 6 field lệnh. **Thứ tự deploy: FE trước hoặc cùng lúc** — BE bản mới không nhận body báo sản lượng cũ. FE gửi
sẵn được field mới ngay: BE bản cũ bỏ qua field lạ.

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Môi trường dev, cổng 8090, 20/10/2026 ~10:57 giờ VN, lượt `105705`. Token che. Lệnh `WO-105705`: kế hoạch 10 `EA`, quy trình hai công đoạn `Cut` (10)
→ `Weld` (20), định mức 20 kg, đã cấp 20 kg (một dòng cấp `d84446f9-…`). Response dài được lược, đánh dấu `…`.

### 4.1 Đầu vào công đoạn đầu

Trước khi có phiếu cấp nào (`X-Trace-Id: 10fd3197b433421f`):

```http
POST /api/v1/work-orders/{W}/operations/69326f67-c958-4d7b-8f1a-9aba3ba163dc/input
{"expectedWorkOrderVersion": 3, "quantity": 10}
→ 409 {"code":"MATERIAL_ISSUE_REQUIRED","message":"Issue material to the work order before confirming the input of its first operation"}
```

Sau khi cấp 20 kg (`X-Trace-Id: c40c6cedba60411d`):

```http
POST /api/v1/work-orders/{W}/operations/69326f67-c958-4d7b-8f1a-9aba3ba163dc/input
Idempotency-Key: …
{"expectedWorkOrderVersion": 4, "quantity": 10}
→ 200 {"code":"SUCCESS","result":{"workOrderOperationId":"69326f67-…","sequence":10,"name":"Cut","workCenterCode":"WC-105705",
  "status":"IN_PROGRESS","inputKnown":true,"inputQuantity":10.000000,"freshProcessedQuantity":0,"freshRemainingQuantity":10.000000,
  "goodQuantity":0,"scrapQuantity":0,"historicalReworkQuantity":0,"pendingReworkQuantity":0,"transferredQuantity":0,
  "transferableQuantity":0,"executionCount":0,"completedAt":null,"completedBy":null,"workOrderVersion":5},"message":"OK"}
```

Gửi lại cùng key + cùng body ⇒ `200`, version vẫn 5 (`1dc99fceb3dc420a`). Cùng key, `quantity: 9` ⇒ `409 IDEMPOTENCY_CONFLICT` (`f86bba85c2bb451c`).
Đầu vào 11 ⇒ `409 PLANNED_QUANTITY_EXCEEDED` field `quantity`.

### 4.2 Báo sản lượng lượt mới + vật tư dùng (`X-Trace-Id: b4a00c3936974119`)

```http
POST /api/v1/work-orders/{W}/production-executions
Idempotency-Key: …
{"actualStartedAt":"2026-10-20T03:37:09Z","actualEndedAt":"2026-10-20T03:47:09Z",
 "workOrderOperationId":"69326f67-c958-4d7b-8f1a-9aba3ba163dc","goodQuantity":8,"scrapQuantity":1,"reworkQuantity":1,
 "expectedWorkOrderVersion":5,
 "consumption":[{"issueLineId":"d84446f9-1b98-4a94-839a-2836ab449d6a","quantity":16},
                {"issueLineId":"d84446f9-1b98-4a94-839a-2836ab449d6a","quantity":2}]}
→ 201 {"code":"SUCCESS","result":{"productionExecutionId":"cfd5473b-5c1d-4be0-a5c8-8dd3b41505ad","code":"PE-CFD5473B","status":"POSTED",
  …,"goodQuantity":8,"scrapQuantity":1,"reworkQuantity":1,…,
  "workOrderActualGoodQuantity":0.000000,"workOrderActualScrapQuantity":1.000000,"workOrderActualReworkQuantity":1.000000,…,
  "sourceExecutionId":null,"processedQuantity":10,"retryUsedQuantity":0,"pendingReworkQuantity":1,
  "consumption":[{"issueLineId":"d84446f9-1b98-4a94-839a-2836ab449d6a","quantity":18}],"durationMinutes":10,"workOrderVersion":6},
  "message":"Created successfully"}
```

Good 8 ở công đoạn đầu **không** vào sản lượng tốt của lệnh (`workOrderActualGoodQuantity: 0`). Tồn kho kg của kho cấp trước và sau lần báo: `10` / `10`.
Lượt 8 + 1 + 2 = 11 trên đầu vào 10 ⇒ `409` field `goodQuantity`. Không gửi version ⇒ `400` field `expectedWorkOrderVersion`. Không gửi công đoạn ⇒
`400` field `workOrderOperationId`.

### 4.3 Xử lý lại phần làm lại

Vật tư 3 kg khi dòng cấp chỉ còn 2 kg (`X-Trace-Id: 4b7a0e8d50bb4169`):

```http
→ 409 {"code":"PLANNED_QUANTITY_EXCEEDED","message":"consumption[0].quantity: only 2.000000 of this issue line is left unconsumed",
  "errors":[{"field":"consumption[0].quantity","message":"only 2.000000 of this issue line is left unconsumed"}]}
```

Xử lý lại 2 khi lượt nguồn chỉ chờ 1 ⇒ `409` field `goodQuantity`. `actualEndedAt` 5 phút sau giờ server ⇒ `400` field `actualEndedAt`. Đúng phần chờ,
kết thúc 30 giây sau giờ server (`X-Trace-Id: 927dd7bdab734fe3`):

```http
{"actualStartedAt":"2026-10-20T03:48:10Z","actualEndedAt":"2026-10-20T03:57:40Z","workOrderOperationId":"69326f67-…",
 "sourceExecutionId":"cfd5473b-5c1d-4be0-a5c8-8dd3b41505ad","goodQuantity":1,"expectedWorkOrderVersion":6,
 "consumption":[{"issueLineId":"d84446f9-…","quantity":2}]}
→ 201 {…,"sourceExecutionId":"cfd5473b-5c1d-4be0-a5c8-8dd3b41505ad","processedQuantity":1,"retryUsedQuantity":0,"pendingReworkQuantity":0,
  "consumption":[{"issueLineId":"d84446f9-…","quantity":2}],"durationMinutes":9,"workOrderVersion":7}
```

Danh sách báo sản lượng sau đó: lượt `PE-CFD5473B` có `retryUsedQuantity: 1`, `pendingReworkQuantity: 0`.

### 4.4 Kết thúc công đoạn (`X-Trace-Id: 4b565243f07a441c`)

Khi còn làm lại chờ ⇒ `409 STATE_CONFLICT`. Sau lượt xử lý lại:

```http
POST /api/v1/work-orders/{W}/operations/69326f67-…/finish
{"expectedWorkOrderVersion": 7}
→ 200 {…,"status":"COMPLETED","inputQuantity":10.000000,"freshProcessedQuantity":10.000000,"freshRemainingQuantity":0.000000,
  "goodQuantity":9.000000,"scrapQuantity":1.000000,"historicalReworkQuantity":1.000000,"pendingReworkQuantity":0.000000,
  "transferredQuantity":0,"transferableQuantity":9.000000,"executionCount":2,"completedAt":"2026-10-20T03:57:10.490791Z",
  "completedBy":"23fce5b6-…","workOrderVersion":8}
```

Công đoạn đầu chỉ kết thúc khi đã nhận **đủ kế hoạch** (FE A25). Trên một lệnh khác, đầu vào 7 / 10, xử lý hết 7 (`X-Trace-Id: c8e5a2da2f2c4c13`):

```http
→ 409 {"code":"STATE_CONFLICT","message":"The first operation must take the whole planned quantity as input before it finishes; 7.000000 of 10.000000 confirmed"}
```

Thêm 3, phế cả 10, kết thúc `Cut` ⇒ `200`; `Weld` (công đoạn trước good 0) kết thúc với đầu vào 0 ⇒ `200 … "status":"COMPLETED","inputQuantity":0`
(`c672acd38e974785`, Q16 / A26).

### 4.5 Công đoạn cuối

Đầu vào `Weld` trước khi `Cut` kết thúc ⇒ `409 OPERATION_SEQUENCE_CONFLICT`. Sau khi kết thúc, chuyển 10 khi còn 9 (`X-Trace-Id: e610541d04e14b1b`):

```http
→ 409 {"code":"PLANNED_QUANTITY_EXCEEDED","message":"quantity: Only 9.000000 good output of the previous operation is left to transfer",
  "errors":[{"field":"quantity","message":"Only 9.000000 good output of the previous operation is left to transfer"}]}
```

Chuyển 9 ⇒ `200`, `"inputQuantity":9.000000,"transferredQuantity":null,"transferableQuantity":null` (công đoạn cuối, `1ae4d1e6495749ea`). Kết thúc
`Weld` trước khi xử lý ⇒ `409`. Báo good 9 với `"consumption": []` (`4086b998cffb4f93`) ⇒ `"workOrderActualGoodQuantity":9.000000,
"workOrderAvailableToReceipt":9.000000,"workOrderCompletionPercent":90.00,"workOrderStatus":"IN_PROGRESS"`. Kết thúc `Weld` ⇒ `200`
(`89d074446c83451c`).

### 4.6 Hoàn thành thiếu sản lượng (`X-Trace-Id: 318ccd4aaf9c4729`)

Còn công đoạn chưa kết thúc ⇒ `409`. Không lý do ⇒ `400 APPROVAL_REASON_REQUIRED` field `reason`; lý do 3 ký tự ⇒ `400` field `reason`. Đúng:

```http
POST /api/v1/work-orders/{W}/complete-production
Idempotency-Key: …
{"expectedWorkOrderVersion": 11, "reason": "Steel ran out"}
→ 200 {"code":"SUCCESS","result":{"workOrderId":"0232f3b5-63cd-4c52-9c59-4a810e2bc28f","workOrderNo":"WO-105705","status":"COMPLETED",
  "version":12,"plannedQuantity":10.000000,"finalGoodQuantity":9.000000,"terminalShortfallQuantity":1.000000,
  "shortfallReason":"Steel ran out","completedAt":"2026-10-20T03:57:11.264385Z"},"message":"OK"}
```

`completedQuantity` (đã nhập kho) vẫn `0`. Người có `PERM_WORK_ORDER_MANAGE` nhưng không có `PERM_MATERIAL_ISSUE_APPROVE`, trên lệnh phế toàn bộ ở §4.4
(`X-Trace-Id: de46d6912ee3462e`):

```http
→ 403 {"code":"PERMISSION_DENIED","message":"Completing below the plan also requires PERM_MATERIAL_ISSUE_APPROVE"}
```

### 4.7 Lệnh báo sản lượng trước bản này

`GET /api/v1/work-orders/{W3}/operations` ⇒ `"inputKnown":false,"inputQuantity":null,"freshRemainingQuantity":null,"transferableQuantity":null`.
Đầu vào ⇒ `409 PRODUCTION_INPUT_UNKNOWN`; báo sản lượng kèm `consumption` ⇒ `409 PRODUCTION_INPUT_UNKNOWN`; báo sản lượng không kèm, không nêu công đoạn
(luật cũ) ⇒ `201` (`68ad417d6ca94a08`), `GET` lệnh ⇒ `finalGoodQuantity: null`. *(Lệnh này được đánh dấu bằng một câu SQL trên dev để giả lập lệnh cũ —
API không tạo được trạng thái đó.)*

---

## §5 — Envelope và hợp đồng lỗi

Envelope không đổi `{code, result, message, errors?}`. Lỗi theo field có `errors[{field, message}]`. Hai mã mới:

| `code` | HTTP | Khi nào | FE nên làm gì |
|---|---|---|---|
| `MATERIAL_ISSUE_REQUIRED` | 409 | xác nhận đầu vào công đoạn đầu khi chưa có phiếu cấp `POSTED` | dẫn người dùng sang cấp vật tư; tải lại không giúp |
| `PRODUCTION_INPUT_UNKNOWN` | 409 | đầu vào / xử lý lại / vật tư dùng trên lệnh báo sản lượng trước bản này | ẩn các thao tác đó cho lệnh `inputKnown = false` |

Mã FE đề xuất ánh xạ vào mã sẵn có: `OPERATION_SEQUENCE_INVALID` → `OPERATION_SEQUENCE_CONFLICT`; `OPERATION_INPUT_EXCEEDED` /
`REWORK_QUANTITY_EXCEEDED` / `MATERIAL_CONSUMPTION_EXCEEDED` → `PLANNED_QUANTITY_EXCEEDED` (phân biệt bằng `errors[].field`: `quantity` /
`goodQuantity` / `consumption[i].quantity`); `REWORK_SOURCE_INVALID` → `422 RESOURCE_SCOPE_MISMATCH` field `sourceExecutionId`;
`PRODUCTION_TIME_INVALID` → `400 VALIDATION_ERROR` field `actualEndedAt`; `PRODUCTION_PENDING_WORK` / `INVALID_STATE` → `STATE_CONFLICT`;
`WORK_ORDER_SHORTFALL_APPROVAL_REQUIRED` → `403 PERMISSION_DENIED`; `MATERIAL_CONSUMPTION_UNKNOWN` → `PRODUCTION_INPUT_UNKNOWN` (một mã cho cả đầu vào lẫn
vật tư dùng — cùng một nguyên nhân).

---

## §6 — Quyền, phạm vi và giới hạn đã biết

- Không thêm quyền mới. Đọc công đoạn: `PERM_PRODUCTION_EXECUTION_READ` hoặc `_MANAGE`; đầu vào / kết thúc: `_MANAGE`; hoàn thành:
  `PERM_WORK_ORDER_MANAGE` (+ `PERM_MATERIAL_ISSUE_APPROVE` khi thiếu).
- **Vật tư dùng là truy vết, không phải chi phí.** Chi phí vật tư vẫn cộng lúc **cấp** (như trước); "giá trị đã dùng" thuộc phần giá trị đã hoãn (D10).
- `GET /production-executions/candidates` **chưa** trả `inputKnown` / các trần — FE đọc `GET /work-orders/{W}/operations` khi mở màn hình báo sản lượng.
- `actualGoodQuantity` / `availableToReceipt` của lệnh nhiều công đoạn chỉ tăng ở công đoạn cuối — phiếu nhập thành phẩm vẫn lấy trần từ đó.
- Tuần tự nghĩa là công đoạn đầu phải **kết thúc** trước khi công đoạn sau nhận hàng: lệnh làm dở một phần rồi muốn sang công đoạn sau thì công đoạn
  đầu kết thúc ở phần đã làm, phần còn lại chỉ đóng được bằng hoàn thành thiếu.
- `createdAt` của execution vừa tạo có thể in 9 chữ số giây lẻ (`…845866500Z`), đọc lại thì 6 — hiện tượng có từ trước, toàn hệ thống.

---

## §7 — Bằng chứng nghiệm thu

| Hạng mục | Kết quả |
|---|---|
| `mvn -o clean verify` | **1871 unit / 199 class + 263 IT / 37 class**, 0 lỗi (trước bản này 1816 + 258) |
| Migration `V81` | chạy sạch trên DB dev có dữ liệu demo; 4 lệnh đã có báo sản lượng ⇒ `inputKnown = false`, 19 lệnh còn lại `true` |
| Nghiệm thu mutation | 26/26 bị test bắt (một mutation lần đầu sống sót — phần chờ làm lại phải trừ phần đã xử lý lại — đã thêm test rồi chạy lại) |
| Smoke HTTP thật | lượt `105705` trên code cuối: **32/32** kiểm tra đạt (lượt `105001` 30/30 trước khi bổ sung luật A25) |
| Test end-to-end | luồng 11 bước nay xác nhận đầu vào trước khi báo sản lượng; một lần báo tăng version lệnh **đúng 1**; vật tư dùng không đổi tồn kho |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm:** giá trị vật tư dùng / dở dang (D10, hoãn); quy trình song song; `inputKnown` trên candidates; nguồn cung lệnh cho MRP theo sản lượng thực
(D11, hoãn). Dở dang theo mục + hoàn vật tư: **v4**.

**FE làm tiếp:**
1. Gửi `expectedWorkOrderVersion` (+ `workOrderOperationId` khi có quy trình) trên mọi lần báo sản lượng; đọc `workOrderVersion` trong response cho lệnh kế.
2. Màn hình công đoạn: gọi `GET .../operations`, nút "xác nhận đầu vào" / "kết thúc" theo `status` và các số còn lại.
3. Đổi nhãn `actualGoodQuantity` thành "sản lượng tốt cuối"; hiện "không xác định" khi `finalGoodQuantity = null`.
4. Ẩn đầu vào / xử lý lại / vật tư dùng khi `inputKnown = false`.
5. Chạy lại A-case phần công đoạn (A19–A24 theo bảng FE) trên bản deploy này và báo kết quả.

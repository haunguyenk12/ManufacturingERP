# BE trả lời — Giai đoạn 7: Đơn mua hàng, nhận hàng và QC

**Trả lời cho:** `FE_HANDOFF_GD_07_2026-10-14.md` (v1, P1) · **Ngày gửi:** 18/10/2026 · **Bản:** v1 — **phần vòng đời đơn mua hàng (`G7a`)**
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. Giai đoạn 7 được **tách ba phần, làm liền**: bản này là phần 1
(đơn mua hàng). Phần 2 (phiếu nhận `DRAFT`, nhận vào `HOLD`, danh sách phiếu nhận, ứng viên PO) và phần 3 (QC mua hàng, lô con) sẽ đến
trong `_v2` và `_v3` — **chưa có trong bản này**. Chưa phải "FE nghiệm thu đạt": task chỉ đóng khi FE chạy A01–A38 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Đơn mua hàng nay có vòng đời đủ như FE mô tả — nháp do server cấp mã, sửa nháp thay cả dòng với id dòng ổn định, xác nhận, huỷ có lý
do, đóng phần còn lại — trạng thái đổi tên theo FE (`CONFIRMED`/`FULLY_RECEIVED`) và thêm `CLOSED`; huỷ một PO sinh từ MRP trả lại lượng
cho lượt hoạch định đúng một lần.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | List/detail trả PO canonical kể cả nháp BUY cũ, id ổn định, không nhân đôi | ✅ **Có sẵn (GĐ6) + đã mở rộng** | Nháp BUY của GĐ6 vẫn là đúng một PO; detail nay có `sources[]` trỏ về lượt hoạch định (§4.6) |
| R02 | Tạo/sửa chỉ `DRAFT`, 1–500 dòng, giá 0 hợp lệ, thiếu giá không xác nhận được | 🟡 **Chưa có, đã làm** | `POST /drafts` + `PATCH /{id}`; xác nhận khi thiếu giá ⇒ `400` đúng dòng (§4.3) |
| R03 | Sửa PO sinh từ MRP không đổi được vật tư/lượng/kho/cấu trúc dòng, server tự kiểm | 🟡 **Chưa có, đã làm** | `409 SOURCE_LOCKED` kèm field (§4.6) |
| R04 | Huỷ chỉ khi chưa nhận, lý do bắt buộc, trả quota một lần, giữ vết | 🟡 **Chưa có, đã làm** | Huỷ PO MRP ⇒ phần còn chuyển được của dòng nhu cầu **15 → 75**, dòng ledger được đánh dấu "đã trả", không xoá (§4.7) |
| R05 | Đóng phần còn lại có lý do, tăng `closedQuantity`, giữ lượng đã nhận | 🟡 **Chưa có, đã làm** | Nhận 25/40 rồi đóng ⇒ `closed 15`, `open 0`, `received 25` giữ nguyên (§4.4) |
| R14 | MRP đọc PO mở | ✅ **Có sẵn**, đã sửa theo trạng thái mới | Nguồn cung PO = PO `CONFIRMED` + `PARTIALLY_RECEIVED`, lượng mở = đặt − nhận − **đóng** (§1) |
| R15 | List có tìm kiếm, lọc trạng thái/kho/khoảng ngày, phân trang, thứ tự ổn định | 🟡 **Thiếu một phần, đã làm** | `search`, `orderDateFrom/To` mới; thứ tự ổn định có tie-break id (§4.5) |
| R17 | Gửi lại cùng key trả cùng kết quả; khác payload ⇒ xung đột; version cũ không ghi gì | 🟡 **Chưa có trên lệnh PO, đã làm** | Replay cùng key ⇒ cùng PO, khác payload ⇒ `409 IDEMPOTENCY_CONFLICT` (§4.1, §4.7) |
| R06–R13, R16, R18–R20 | Phiếu nhận, QC, tồn chờ kiểm, giá trị tồn | ⏳ **Phần 2/3 — chưa có trong bản này** | Xem §8 |

### Trả lời Q (những câu phần 1 trả lời được)

| Q | Trả lời |
|---|---|
| Q1 | **Có một phần.** Phần 1 chỉ dùng `PERM_PURCHASE_ORDER_READ` / `PERM_PURCHASE_ORDER_MANAGE` (đã có từ trước, tên đúng như FE). Bốn quyền phiếu nhận/QC sẽ được tạo ở phần 2/3 với **đúng tên FE đề xuất** (`PERM_GOODS_RECEIPT_READ`, `PERM_GOODS_RECEIPT_MANAGE`, `PERM_PURCHASE_QC_READ`, `PERM_PURCHASE_QC_MANAGE`); `PERM_GOODS_RECEIPT_POST` đã có |
| Q2 | **Có.** Nháp BUY của GĐ6 giữ nguyên id PO và id dòng; không có bước nâng cấp nào sinh PO thứ hai. `sources[]` đọc thẳng từ sổ chuyển đổi của GĐ6 |
| Q3 | **Có.** `PATCH` thay **toàn bộ** `lines[]`: dòng gửi kèm `id` được giữ và cập nhật (id không đổi), dòng không có `id` là dòng mới, dòng vắng mặt bị xoá |
| Q4 | **Có.** `unitPrice` bỏ trống/`null` ở nháp = "chưa có giá"; `0` là giá hợp lệ. Xác nhận khi còn dòng `null` ⇒ `400` chỉ đúng `lines[i].unitPrice` |
| Q5 | **Có.** Đường nháp mới gán `currencyCode = "VND"` cho mọi dòng, không thuế/phí/tỷ giá. PO cũ có thể còn dòng không có tiền tệ (`null`) |
| Q6 | **Có, theo danh mục loại vật tư của hệ thống:** `RAW_MATERIAL`, `CONSUMABLE`, `WIP`. Hệ thống **không có** loại `MRO` hay `SEMI_FINISHED` (bán thành phẩm dùng `WIP`). `FINISHED_GOOD`, `SERVICE` và vật tư quản lý **theo số sê-ri** bị từ chối — áp cho đường nháp mới và cho dòng có vật tư **mới/đổi** khi `PATCH`. Đường tạo PO cũ không đổi |
| Q7 | **Có.** PO còn giữ lượng hoạch định (`sourceLocked = true`) không đổi được kho, vật tư, lượng, và không thêm/bớt dòng; nhà cung cấp, giá, ngày, tham chiếu, ghi chú vẫn sửa được. Server tự kiểm, không tin cờ từ client |
| Q11 | **Chưa áp dụng được ở phần 1** — hiện chưa có phiếu nhận `DRAFT` nào để chặn. Chặn huỷ/đóng khi còn phiếu nhận nháp sẽ có ở phần 2 cùng lúc với phiếu nhận `DRAFT` |
| Q12 | **Có.** Huỷ PO (có hay không có body) trả lại lượng PO đó đang giữ cho **mọi** lượt hoạch định đã sinh ra nó, **đúng một lần** — dòng sổ chuyển đổi được đánh dấu `releasedAt` + lý do, **không xoá**; huỷ lại / gửi lại không trả thêm. Lượt hoạch định bị tăng `conversionVersion` để client đang giữ version cũ phải tải lại |
| Q13 | **Có.** Đóng phần còn lại **không** trả quota |
| Q21 | **Có, và lâu hơn 24 giờ:** key của lệnh PO được lưu **vĩnh viễn**, phạm vi = (key, loại lệnh); dấu vân tay gồm cả id PO và body. Quyền được kiểm lại theo phân quyền **hiện tại** trước khi trả kết quả replay. Header **tuỳ chọn** — không gửi thì lệnh chạy như bình thường |
| Q22 | **Có** (`X-Trace-Id` có trên mọi response, đã có từ trước) |
| Q23 | **Có một phần:** `reference` ≤ 200, `notes` ≤ 2000, `reason` ≤ 500 — đúng như đề xuất |
| Q24 | Phần 1 không thêm quyền nào. Quyền PO hiện có: `ADMIN` + `MANAGER` đủ đọc/quản lý; `OPERATOR` chỉ đọc. Quyền mới của phần 2/3 sẽ chỉ cấp `ADMIN` + `MANAGER` như FE đề xuất |
| Q26 | **Không như đề xuất (giữ hành vi có sẵn):** id PO **không tồn tại hoặc ngoài phạm vi** trả **403**, không phải 404 — server cần đọc bản ghi để biết nó thuộc nhà máy nào, không có bản ghi thì từ chối trước khi tới tầng nghiệp vụ. Áp dụng cho mọi endpoint `/{id}` trong hệ thống, không riêng mua hàng |
| Q31 | **Khác giới hạn FE:** lượng và đơn giá nhận **tối đa 13 chữ số nguyên, 6 chữ số lẻ** (giống mọi màn hình kho/đơn bán đã thống nhất ở GĐ4/GĐ6), không phải 999 999 999. Chữ số lẻ thứ 7 ⇒ `400` theo field (§4.2). Chú ý: `5.0000000` cũng bị từ chối vì đếm cả số 0 thừa |

Các Q còn lại (Q8–Q10, Q14–Q20, Q25, Q27–Q30) thuộc phiếu nhận/QC/giá trị tồn — trả lời ở `_v2`/`_v3`.

---

## §1 — Nguyên nhân thật

Không có lỗi FE báo — đây là phần **chưa có**. Bốn điều đáng nói, theo thứ tự ảnh hưởng tới FE:

1. **Tên trạng thái PO đã đổi trên toàn hệ thống**, kể cả dữ liệu cũ: `SENT` → `CONFIRMED`, `RECEIVED` → `FULLY_RECEIVED`, thêm `CLOSED`.
   Bản ghi cũ được đổi ngay tại chỗ (đã chạy trên DB dev có dữ liệu demo: 2 PO `SENT` và 1 PO `RECEIVED` đổi đúng tên). Lọc
   `status=SENT` nay là `400`, không phải danh sách rỗng.
2. **Nguồn cung PO cho hoạch định** nay tính lượng mở = đặt − nhận − **đóng**, chỉ trên PO `CONFIRMED`/`PARTIALLY_RECEIVED`. PO `CLOSED`
   không còn là nguồn cung. Số MRP **không đổi** với dữ liệu hiện có (chưa PO nào đóng).
3. **Chuyển đề xuất BUY sang PO (GĐ6) nay cần thêm `PERM_PURCHASE_ORDER_MANAGE`** trên nhà máy (FE §1.4 yêu cầu). Trước đây chỉ cần quyền
   hoạch định. `ADMIN`/`MANAGER` có sẵn cả hai nên không ai đang dùng bị chặn.
4. **Trong lúc nghiệm thu tìm thấy:** các dòng PO lưu cùng một lúc có thể trả **theo thứ tự khác nhau giữa hai lần đọc** (thời điểm tạo
   bị làm tròn tới micro-giây nên bằng nhau). Đã sửa: dòng sắp theo thời điểm tạo rồi theo id. Lỗi này có từ trước GĐ7, ảnh hưởng mọi
   màn hình chi tiết PO.

---

## §2 — Thay đổi contract

Tiền tố mọi đường dẫn: `/api`.

### 2.1 Endpoint

| Endpoint | Trước | Sau | Phá client đang chạy? |
|---|---|---|---|
| `POST /v1/purchase-orders/drafts` | không có | **mới** — nháp, server cấp mã `PO-000001`… | Không |
| `PATCH /v1/purchase-orders/{id}` | không có | **mới** — lưu nháp, thay cả dòng | Không |
| `POST /v1/purchase-orders/{id}/confirm` | không có | **mới** — body `{expectedVersion}` bắt buộc | Không |
| `POST /v1/purchase-orders/{id}/close` | không có | **mới** — body `{expectedVersion, reason}` bắt buộc | Không |
| `POST /v1/purchase-orders/{id}/cancel` | không body, chỉ `DRAFT` | **body tuỳ chọn** `{expectedVersion, reason}`: có body thì huỷ được cả `CONFIRMED` chưa nhận; không body giữ nguyên luật cũ (chỉ `DRAFT`) | Không |
| `POST /v1/purchase-orders/{id}/send` | `DRAFT → SENT` | `DRAFT → CONFIRMED` (giữ để tương thích, không kiểm giá/version) | **Có** — tên trạng thái trả về |
| `GET /v1/purchase-orders` | `companyId, plantId, warehouseId?, supplierId?, status?, page, size, sortBy, sortDir` | thêm `search?`, `orderDateFrom?`, `orderDateTo?` (yyyy-MM-dd, bao gồm hai đầu) + header `X-Plant-Id` (nếu gửi phải khớp `plantId`) | Chỉ khi FE gửi `X-Plant-Id` lệch |
| `POST /v1/purchase-orders` (tạo với mã do client đặt) | — | **không đổi** | Không |
| `POST /v1/goods-receipts/{id}/cancel` | huỷ được mọi phiếu `POSTED` | PO đã `CLOSED` ⇒ `409 STATE_CONFLICT` | Có, hẹp |

Mọi lệnh mới + `cancel` nhận header **`Idempotency-Key` tuỳ chọn**.

### 2.2 Field mới trên PO (detail và list) — tất cả additive, field cũ giữ nguyên

| Field | Kiểu | Nullable? | Nguồn giá trị | Ví dụ thật |
|---|---|---|---|---|
| `code` | string | không | **cùng giá trị** `purchaseOrderNo` (tên FE) | `"PO-000007"` |
| `version` | integer | không | version lạc quan của PO; gửi lại làm `expectedVersion` | `3` |
| `warehouseName` | string | không | tên kho nhận | `"Kho nguyen lieu"` |
| `reference` | string | **có** | tham chiếu NCC/giao hàng, ≤ 200 | `"TEST-PO-40"` |
| `currencyCode` | string | **có** | tiền tệ **duy nhất** của các dòng; `null` nếu không dòng nào có hoặc lẫn tiền tệ. **List luôn `null`** (không tải dòng) | `"VND"` |
| `sourceLocked` | boolean | không | PO còn giữ lượng của ít nhất một lượt hoạch định | `true` |
| `reason` | string | **có** | lý do huỷ/đóng | `"NCC ngung mat hang"` |
| `confirmedAt` / `cancelledAt` / `closedAt` | instant | **có** | thời điểm server ghi. PO xác nhận **trước** GĐ7 có `confirmedAt = null` (không ai ghi lại lúc đó) | `"2026-10-18T14:18:59.845382Z"` |
| `createdBy` | uuid | **có** | người tạo | `"23fce5b6-…"` |
| `history[]` | mảng | không (rỗng ở list) | các bước `CREATE`/`CONFIRM`/`CANCEL`/`CLOSE`: `action`, `fromStatus`, `toStatus`, `actorId`, `actorUsername`, `at`, `reason`. **Không** gồm bước đổi trạng thái do phiếu nhận (phiếu nhận là bằng chứng của chúng) | xem §4.4 |

### 2.3 Field mới trên dòng PO

| Field | Kiểu | Nullable? | Nguồn giá trị | Ví dụ thật |
|---|---|---|---|---|
| `uomCode` | string | không | đơn vị của vật tư | `"K211857"` |
| `closedQuantity` | number | không | phần còn lại đã bị đóng | `15.000000` |
| `openQuantity` | number | không | `orderedQuantity − receivedQuantity − closedQuantity` | `0.000000` |
| `remainingQuantity` (cũ) | number | không | **nay bằng `openQuantity`** (trước: đặt − nhận). Chỉ khác với PO đã đóng | `0.000000` |
| `sources[]` | mảng | không (`[]` cho dòng tạo tay) | mỗi phần tử một lần chuyển đổi hoạch định: `conversionId`, `runId`, `runCode`, `requirementId`, `suggestionId`, `sourceVersion`, `convertedQuantity`, `convertedAt`, `releasedAt`, `releaseReason` | xem §4.6 |

### 2.4 Field mới trên sổ chuyển đổi hoạch định (GĐ6)

`GET /api/v1/planning-runs/{id}/conversions` và `conversions[]` trong `GET /api/v1/planning-runs/{id}/requirements/{lineId}` thêm
`releasedAt` (instant, nullable) và `releaseReason` (string, nullable). Dòng có `releasedAt` **không còn tính** vào `convertedQuantity` /
`remainingConvertibleQuantity`.

### 2.5 Số liệu / công thức

- `openQuantity = orderedQuantity − receivedQuantity − closedQuantity`. Ví dụ đo thật: đặt 40, nhận 25, đóng ⇒ `closedQuantity 15`, `openQuantity 0`.
- Phần còn chuyển được của dòng nhu cầu = đề xuất − tổng lượng các lần chuyển **chưa bị trả**. Ví dụ đo thật: nhu cầu ròng 75, chuyển 60
  sang PO ⇒ còn 15; huỷ PO ⇒ còn **75**, `convertedQuantity 0`.

---

## §3 — Breaking change

**Wire: CÓ.**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| Mọi response PO (`status`), `POST …/send` | PO đã gửi NCC | `"SENT"` | `"CONFIRMED"` | **Đổi giá trị** | badge/hành động theo tên mới |
| Mọi response PO (`status`) | PO nhận đủ | `"RECEIVED"` | `"FULLY_RECEIVED"` | **Đổi giá trị** | như trên |
| Mọi response PO (`status`) | PO bị đóng phần còn lại | — | `"CLOSED"` (giá trị mới) | **Giá trị mới** | thêm nhánh hiển thị |
| `GET /v1/purchase-orders?status=SENT` / `RECEIVED` | lọc theo tên cũ | danh sách | `400 VALIDATION_ERROR`, field `status` | **Đổi mã** | dùng tên mới |
| `remainingQuantity` trên dòng PO | PO đã đóng | đặt − nhận | đặt − nhận − đóng (= `openQuantity`) | **Đổi số** (chỉ PO đóng) | không cần nếu chưa dùng đóng |
| Chuyển đề xuất BUY (`POST /v1/planning-runs/{id}/conversions`) | người dùng có quyền hoạch định nhưng **không** có `PERM_PURCHASE_ORDER_MANAGE` | `201` | `403 PERMISSION_DENIED` | **Bắt buộc mới** (quyền) | không — `ADMIN`/`MANAGER` có sẵn |
| `POST /v1/goods-receipts/{id}/cancel` | PO của phiếu đã `CLOSED` | `200` (đảo nhập kho, mở lại PO) | `409 STATE_CONFLICT` | **Bắt buộc mới** | hiển thị thông báo |
| `GET /v1/purchase-orders` | gửi `X-Plant-Id` khác `plantId` | header bị bỏ qua | `409 STATE_CONFLICT` | **Bắt buộc mới** | gửi đúng nhà máy |

Không bỏ field nào. Không đổi giá trị mặc định của query param nào. Thứ tự mặc định của list **giữ** `createdAt desc`, nay thêm tie-break
`purchaseOrderId` (chỉ ảnh hưởng các PO tạo cùng micro-giây). **Thứ tự dòng trong một PO** nay ổn định (thời điểm tạo, rồi id) — trước đây
có thể đảo giữa hai lần đọc.

**Thứ tự deploy:** BE trước. FE cũ đọc `SENT`/`RECEIVED` sẽ thấy trạng thái lạ cho tới khi cập nhật — **không** có giai đoạn BE trả cả hai tên.

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Môi trường dev, ngày 18/10/2026, fixture công ty `GD7A-211857`. Token che đi. Mọi JSON dưới đây là **nguyên văn** response.

### 4.1 Tạo nháp — `POST /api/v1/purchase-orders/drafts`

Header: `Idempotency-Key: g7a-cfef45325da74f9a` · `X-Trace-Id` response: `e02fd0f2d2164e00` · **HTTP 201**

```json
{"companyId": "30408a9f-18db-45bf-9425-cf4fd0c3339f", "plantId": "4f4b2ad7-0bee-4091-88f3-708e8c9bf192", "supplierId": "fd7d39fd-2795-4a5c-8375-f029f8fe930f", "warehouseId": "d1f31eed-6aaf-4237-b79d-88eeeefd9727", "orderDate": "2026-10-18", "reference": "TEST-PO-40", "notes": "", "lines": [{"itemId": "9b5e2155-3db0-4f41-8264-99570d3842fe", "orderedQuantity": "40", "unitPrice": "10", "expectedDate": "2026-10-18"}, {"itemId": "8515dd31-716c-430f-8651-eb08be7d9c15", "orderedQuantity": "0.300001", "expectedDate": "2026-10-21"}]}
```

```json
{"code":"SUCCESS","result":{"purchaseOrderId":"04d912ae-7475-4cf8-8230-8881f3743340","companyId":"30408a9f-18db-45bf-9425-cf4fd0c3339f","companyCode":"GD7A-211857","plantId":"4f4b2ad7-0bee-4091-88f3-708e8c9bf192","plantCode":"P1-211857","warehouseId":"d1f31eed-6aaf-4237-b79d-88eeeefd9727","warehouseCode":"RM-211857","supplierId":"fd7d39fd-2795-4a5c-8375-f029f8fe930f","supplierCode":"SUP-211857","supplierName":"NCC Kim loai","purchaseOrderNo":"PO-000007","status":"DRAFT","orderDate":"2026-10-18","expectedDate":"2026-10-18","sourceRequisitionId":null,"note":null,"createdAt":"2026-10-18T14:18:59.107865Z","updatedAt":"2026-10-18T14:18:59.107865Z","lines":[{"purchaseOrderLineId":"0d160838-94ad-4fa5-8986-1a2fcf6d4043","purchaseRequisitionLineId":null,"itemId":"9b5e2155-3db0-4f41-8264-99570d3842fe","itemCode":"RM1-211857","itemName":"RM1","orderedQuantity":40,"receivedQuantity":0,"remainingQuantity":40,"unitPrice":10,"currencyCode":"VND","expectedDate":"2026-10-18","uomCode":"K211857","closedQuantity":0,"openQuantity":40,"sources":[]},{"purchaseOrderLineId":"a74d3b5e-0f5f-4470-9a31-cd62218e12fe","purchaseRequisitionLineId":null,"itemId":"8515dd31-716c-430f-8651-eb08be7d9c15","itemCode":"RM2-211857","itemName":"RM2","orderedQuantity":0.300001,"receivedQuantity":0,"remainingQuantity":0.300001,"unitPrice":null,"currencyCode":"VND","expectedDate":"2026-10-21","uomCode":"K211857","closedQuantity":0,"openQuantity":0.300001,"sources":[]}],"code":"PO-000007","version":0,"warehouseName":"Kho nguyen lieu","reference":"TEST-PO-40","currencyCode":"VND","sourceLocked":false,"reason":null,"confirmedAt":null,"cancelledAt":null,"closedAt":null,"createdBy":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","history":[{"action":"CREATE","fromStatus":null,"toStatus":"DRAFT","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","actorUsername":"admin","at":"2026-10-18T14:18:59.107865Z","reason":null}]},"message":"Created successfully"}
```

Ghi chú: `notes: ""` ⇒ lưu `null` (chuỗi rỗng = không có). `expectedDate` đầu phiếu = ngày dự kiến **sớm nhất** của các dòng. Số
lượng/giá trên wire là **số JSON**, không phải chuỗi (thống nhất toàn hệ thống từ GĐ2); response ngay sau ghi lặp lại cách viết của request
(`40`), đọc lại từ DB có 6 chữ số lẻ (`40.000000`).

Gửi lại **cùng key + cùng body** ⇒ `201`, **cùng** `purchaseOrderId`, DB chỉ có một PO mã này (trace `c0cca3e990d64e29`).
Cùng key, body khác ⇒ (trace `c4363b2060a74aca`):

```json
{"code":"IDEMPOTENCY_CONFLICT","message":"Idempotency-Key was already used with a different payload"}
```

### 4.2 Nhánh lỗi khi tạo nháp

Vật tư thành phẩm (trace `19347a93ce1745ef`, **422**):

```json
{"code":"OPERATION_NOT_ALLOWED","message":"lines[0].itemId: item type FINISHED_GOOD cannot be purchased here","errors":[{"field":"lines[0].itemId","message":"item type FINISHED_GOOD cannot be purchased here"}]}
```

Chữ số lẻ thứ 7 `"orderedQuantity": "1.1234567"` (trace `e6196457fd2441ad`, **400**):

```json
{"code":"VALIDATION_ERROR","message":"lines[0].orderedQuantity: numeric value out of bounds (<13 digits>.<6 digits> expected)","errors":[{"field":"lines[0].orderedQuantity","message":"numeric value out of bounds (<13 digits>.<6 digits> expected)"}]}
```

`X-Plant-Id` khác `plantId` trong body (trace `7d2a664a20ec495b`, **409**):

```json
{"code":"STATE_CONFLICT","message":"X-Plant-Id does not match the plant referenced by this request"}
```

### 4.3 Sửa nháp và xác nhận

`PATCH /api/v1/purchase-orders/{id}` với `expectedVersion` cũ (trace `4b5521c1eec44937`, **409**) — **không ghi gì**:

```json
{"code":"CONCURRENT_MODIFICATION","message":"Purchase order version is 0, not 5; reload it"}
```

Body đúng (trace `e40cb8abd5e44471`, **200**, version 0 → 1, hai id dòng giữ nguyên, `reference: ""` ⇒ xoá tham chiếu):

```json
{"expectedVersion": 0, "supplierId": "fd7d39fd-2795-4a5c-8375-f029f8fe930f", "warehouseId": "d1f31eed-6aaf-4237-b79d-88eeeefd9727", "orderDate": "2026-10-18", "reference": "", "lines": [{"id": "0d160838-94ad-4fa5-8986-1a2fcf6d4043", "itemId": "9b5e2155-3db0-4f41-8264-99570d3842fe", "orderedQuantity": "40", "unitPrice": "10", "expectedDate": "2026-10-18"}, {"id": "a74d3b5e-0f5f-4470-9a31-cd62218e12fe", "itemId": "8515dd31-716c-430f-8651-eb08be7d9c15", "orderedQuantity": "0.300001", "expectedDate": "2026-10-21"}]}
```

Quy tắc text khi `PATCH`: field **vắng/`null` = giữ**, **chuỗi rỗng = xoá** (áp cho `reference`, `notes`). `supplierId`, `warehouseId`,
`orderDate`, `lines` bắt buộc (trạng thái đầy đủ của nháp).

`POST /api/v1/purchase-orders/{id}/confirm` khi dòng 2 chưa có giá — `{"expectedVersion": 1}` (trace `cbd925c3c97a4501`, **400**):

```json
{"code":"VALIDATION_ERROR","message":"lines[1].unitPrice: is required to confirm","errors":[{"field":"lines[1].unitPrice","message":"is required to confirm"}]}
```

Chỉ số `lines[i]` theo **đúng thứ tự dòng trong response detail**. Đặt giá `0` cho dòng đó rồi xác nhận `{"expectedVersion": 2}`
(header `Idempotency-Key: g7a-4528361a40ff4cdb`, trace `b6059708b9bb448c`, **200**):

```json
{"code":"SUCCESS","result":{"purchaseOrderId":"04d912ae-7475-4cf8-8230-8881f3743340","companyId":"30408a9f-18db-45bf-9425-cf4fd0c3339f","companyCode":"GD7A-211857","plantId":"4f4b2ad7-0bee-4091-88f3-708e8c9bf192","plantCode":"P1-211857","warehouseId":"d1f31eed-6aaf-4237-b79d-88eeeefd9727","warehouseCode":"RM-211857","supplierId":"fd7d39fd-2795-4a5c-8375-f029f8fe930f","supplierCode":"SUP-211857","supplierName":"NCC Kim loai","purchaseOrderNo":"PO-000007","status":"CONFIRMED","orderDate":"2026-10-18","expectedDate":"2026-10-18","sourceRequisitionId":null,"note":null,"createdAt":"2026-10-18T14:18:59.107865Z","updatedAt":"2026-10-18T14:18:59.845926800Z","lines":[{"purchaseOrderLineId":"0d160838-94ad-4fa5-8986-1a2fcf6d4043","purchaseRequisitionLineId":null,"itemId":"9b5e2155-3db0-4f41-8264-99570d3842fe","itemCode":"RM1-211857","itemName":"RM1","orderedQuantity":40.000000,"receivedQuantity":0.000000,"remainingQuantity":40.000000,"unitPrice":10.000000,"currencyCode":"VND","expectedDate":"2026-10-18","uomCode":"K211857","closedQuantity":0.000000,"openQuantity":40.000000,"sources":[]},{"purchaseOrderLineId":"a74d3b5e-0f5f-4470-9a31-cd62218e12fe","purchaseRequisitionLineId":null,"itemId":"8515dd31-716c-430f-8651-eb08be7d9c15","itemCode":"RM2-211857","itemName":"RM2","orderedQuantity":0.300001,"receivedQuantity":0.000000,"remainingQuantity":0.300001,"unitPrice":0.000000,"currencyCode":"VND","expectedDate":"2026-10-21","uomCode":"K211857","closedQuantity":0.000000,"openQuantity":0.300001,"sources":[]}],"code":"PO-000007","version":3,"warehouseName":"Kho nguyen lieu","reference":null,"currencyCode":"VND","sourceLocked":false,"reason":null,"confirmedAt":"2026-10-18T14:18:59.845382Z","cancelledAt":null,"closedAt":null,"createdBy":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","history":[{"action":"CREATE","fromStatus":null,"toStatus":"DRAFT","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","actorUsername":"admin","at":"2026-10-18T14:18:59.107865Z","reason":null},{"action":"CONFIRM","fromStatus":"DRAFT","toStatus":"CONFIRMED","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","actorUsername":"admin","at":"2026-10-18T14:18:59.845382Z","reason":null}]},"message":"OK"}
```

Gửi lại cùng key ⇒ `200`, cùng trạng thái, **không** xác nhận lần hai (trace `e8886761120b4995`). Đọc lại PO: `confirmedAt` trùng từng
chữ số với response (trace `81ee22fc724c4de0`). `PATCH` PO đã xác nhận ⇒ `409 STATE_CONFLICT` (trace `2d7b86b6febc4c72`).

### 4.4 Đóng phần còn lại — `POST /api/v1/purchase-orders/{id}/close`

Sau khi nhận 25/40 của dòng 1 qua đường nhận hàng hiện có, PO là `PARTIALLY_RECEIVED`, version 4. Huỷ lúc này ⇒ **409** (trace
`6ffffa3bb0424e90`):

```json
{"code":"STATE_CONFLICT","message":"Only a DRAFT or CONFIRMED purchase order with nothing received can be cancelled; close the remainder instead"}
```

Đóng `{"expectedVersion": 4, "reason": "NCC ngung mat hang"}` (trace `e1f77d0fb35945ff`, **200**):

```json
{"code":"SUCCESS","result":{"purchaseOrderId":"04d912ae-7475-4cf8-8230-8881f3743340","companyId":"30408a9f-18db-45bf-9425-cf4fd0c3339f","companyCode":"GD7A-211857","plantId":"4f4b2ad7-0bee-4091-88f3-708e8c9bf192","plantCode":"P1-211857","warehouseId":"d1f31eed-6aaf-4237-b79d-88eeeefd9727","warehouseCode":"RM-211857","supplierId":"fd7d39fd-2795-4a5c-8375-f029f8fe930f","supplierCode":"SUP-211857","supplierName":"NCC Kim loai","purchaseOrderNo":"PO-000007","status":"CLOSED","orderDate":"2026-10-18","expectedDate":"2026-10-18","sourceRequisitionId":null,"note":null,"createdAt":"2026-10-18T14:18:59.107865Z","updatedAt":"2026-10-18T14:19:00.362379500Z","lines":[{"purchaseOrderLineId":"0d160838-94ad-4fa5-8986-1a2fcf6d4043","purchaseRequisitionLineId":null,"itemId":"9b5e2155-3db0-4f41-8264-99570d3842fe","itemCode":"RM1-211857","itemName":"RM1","orderedQuantity":40.000000,"receivedQuantity":25.000000,"remainingQuantity":0.000000,"unitPrice":10.000000,"currencyCode":"VND","expectedDate":"2026-10-18","uomCode":"K211857","closedQuantity":15.000000,"openQuantity":0.000000,"sources":[]},{"purchaseOrderLineId":"a74d3b5e-0f5f-4470-9a31-cd62218e12fe","purchaseRequisitionLineId":null,"itemId":"8515dd31-716c-430f-8651-eb08be7d9c15","itemCode":"RM2-211857","itemName":"RM2","orderedQuantity":0.300001,"receivedQuantity":0.000000,"remainingQuantity":0.000000,"unitPrice":0.000000,"currencyCode":"VND","expectedDate":"2026-10-21","uomCode":"K211857","closedQuantity":0.300001,"openQuantity":0.000000,"sources":[]}],"code":"PO-000007","version":5,"warehouseName":"Kho nguyen lieu","reference":null,"currencyCode":"VND","sourceLocked":false,"reason":"NCC ngung mat hang","confirmedAt":"2026-10-18T14:18:59.845382Z","cancelledAt":null,"closedAt":"2026-10-18T14:19:00.361267Z","createdBy":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","history":[{"action":"CREATE","fromStatus":null,"toStatus":"DRAFT","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","actorUsername":"admin","at":"2026-10-18T14:18:59.107865Z","reason":null},{"action":"CONFIRM","fromStatus":"DRAFT","toStatus":"CONFIRMED","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","actorUsername":"admin","at":"2026-10-18T14:18:59.845382Z","reason":null},{"action":"CLOSE","fromStatus":"PARTIALLY_RECEIVED","toStatus":"CLOSED","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","actorUsername":"admin","at":"2026-10-18T14:19:00.361267Z","reason":"NCC ngung mat hang"}]},"message":"OK"}
```

Lượng mở còn lại của PO trong DB sau khi đóng: `0`. Đảo phiếu nhận của PO đã đóng ⇒ **409** (trace `068fe1a838504fee`):

```json
{"code":"STATE_CONFLICT","message":"Goods receipts of a CLOSED purchase order cannot be cancelled"}
```

### 4.5 Danh sách — `GET /api/v1/purchase-orders`

`?companyId=30408a9f-…&plantId=4f4b2ad7-…&search=po-000007&status=CONFIRMED&orderDateFrom=2026-10-18&orderDateTo=2026-10-18` ⇒ **200**,
`totalElements: 1` (trace `8beab2bce3e04115`, PO lúc đó còn `CONFIRMED`). `search` khớp **không phân biệt hoa thường** trên mã PO, tham
chiếu, mã NCC, tên NCC. Dòng trong list có `lines: []`, `history: []`, `currencyCode: null`; `sourceLocked` **có giá trị thật** (một truy vấn
cho cả trang). `orderDateFrom` > `orderDateTo` ⇒ `400` field `orderDateFrom`. `status=SENT` (trace `86441a8342214a8e`, **400**):

```json
{"code":"VALIDATION_ERROR","message":"status: must be a valid PurchaseOrderStatus","errors":[{"field":"status","message":"must be a valid PurchaseOrderStatus"}]}
```

### 4.6 PO sinh từ hoạch định — nguồn và khoá

Lượt hoạch định thương mại (GĐ6) cho đơn bán 50 FG, BOM 2 RM1/FG, tồn RM1 khả dụng 25 ⇒ dòng nhu cầu RM1: gộp 100, ròng 75. Chuyển 60
sang PO. `GET /api/v1/purchase-orders/{id}` ⇒ `sourceLocked: true`, dòng có `sources[0].convertedQuantity: 60.000000`, `releasedAt: null`.

Sửa lượng 60 → 59 (trace `6fc92062dc2e4a64`, **409**) — nhà cung cấp/giá/ngày trong **cùng** request vẫn sửa được nếu không đụng lượng:

```json
{"expectedVersion": 0, "supplierId": "fd7d39fd-2795-4a5c-8375-f029f8fe930f", "warehouseId": "d1f31eed-6aaf-4237-b79d-88eeeefd9727", "orderDate": "2026-10-18", "lines": [{"id": "842d0a48-cd9a-4e94-a71a-27d091d550e8", "itemId": "9b5e2155-3db0-4f41-8264-99570d3842fe", "orderedQuantity": "59", "unitPrice": "7", "expectedDate": "2026-10-30"}]}
```

```json
{"code":"SOURCE_LOCKED","message":"lines[0].orderedQuantity: comes from planning and cannot be changed","errors":[{"field":"lines[0].orderedQuantity","message":"comes from planning and cannot be changed"}]}
```

Giữ lượng 60, chỉ đổi giá 7 ⇒ `200` (trace `f1e2bc87348247d6`). Field bị khoá có thể báo: `warehouseId`, `lines` (thêm/bớt dòng),
`lines[i].itemId`, `lines[i].orderedQuantity`.

### 4.7 Huỷ PO và trả lượng hoạch định — `POST /api/v1/purchase-orders/{id}/cancel`

Trước khi huỷ: phần còn chuyển được của dòng nhu cầu = **15**, `conversionVersion` của lượt = 1. Huỷ
`{"expectedVersion": 1, "reason": "NCC het hang"}` (trace `4f6fc0a80c5541ed`, **200**):

```json
{"code":"SUCCESS","result":{"purchaseOrderId":"ef106c8c-1b65-4c74-87ac-93ed68573c9d","companyId":"30408a9f-18db-45bf-9425-cf4fd0c3339f","companyCode":"GD7A-211857","plantId":"4f4b2ad7-0bee-4091-88f3-708e8c9bf192","plantCode":"P1-211857","warehouseId":"d1f31eed-6aaf-4237-b79d-88eeeefd9727","warehouseCode":"RM-211857","supplierId":"fd7d39fd-2795-4a5c-8375-f029f8fe930f","supplierCode":"SUP-211857","supplierName":"NCC Kim loai","purchaseOrderNo":"PO-MRP-D2AE5D6F","status":"CANCELLED","orderDate":"2026-10-18","expectedDate":"2026-10-30","sourceRequisitionId":null,"note":"Created from MRP run RUN-0CE8FB15","createdAt":"2026-10-18T14:19:01.621961Z","updatedAt":"2026-10-18T14:19:01.942709700Z","lines":[{"purchaseOrderLineId":"842d0a48-cd9a-4e94-a71a-27d091d550e8","purchaseRequisitionLineId":null,"itemId":"9b5e2155-3db0-4f41-8264-99570d3842fe","itemCode":"RM1-211857","itemName":"RM1","orderedQuantity":60.000000,"receivedQuantity":0.000000,"remainingQuantity":60.000000,"unitPrice":7.000000,"currencyCode":"VND","expectedDate":"2026-10-30","uomCode":"K211857","closedQuantity":0.000000,"openQuantity":60.000000,"sources":[{"conversionId":"33cd050e-334c-4af7-8b23-2e1f70834eb7","runId":"0ce8fb15-7981-4034-bf6d-ad80cce3f75a","runCode":"RUN-0CE8FB15","requirementId":"c11ec00b-fdd7-4ccd-a2b8-7c87db1dfbdd","suggestionId":"4e1fa397-b4f7-4aa7-ae64-dd1533b89549","sourceVersion":"3d00664cd57f931977a1059e72d8bee048fc252596d8fbc7b9592b64148c7583","convertedQuantity":60.000000,"convertedAt":"2026-10-18T14:19:01.629922Z","releasedAt":"2026-10-18T14:19:01.937431Z","releaseReason":"NCC het hang"}]}],"code":"PO-MRP-D2AE5D6F","version":2,"warehouseName":"Kho nguyen lieu","reference":null,"currencyCode":"VND","sourceLocked":false,"reason":"NCC het hang","confirmedAt":null,"cancelledAt":"2026-10-18T14:19:01.937431Z","closedAt":null,"createdBy":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","history":[{"action":"CREATE","fromStatus":null,"toStatus":"DRAFT","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","actorUsername":"admin","at":"2026-10-18T14:19:01.621961Z","reason":null},{"action":"CANCEL","fromStatus":"DRAFT","toStatus":"CANCELLED","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","actorUsername":"admin","at":"2026-10-18T14:19:01.937431Z","reason":"NCC het hang"}]},"message":"OK"}
```

Sau khi huỷ, `GET /api/v1/planning-runs/0ce8fb15-…/requirements/c11ec00b-…` (trace `5abae7f5384b40f4`) trả `"convertedQuantity":0`,
`"remainingConvertibleQuantity":75.000000`, dòng sổ chuyển đổi có `releasedAt`/`releaseReason`. `conversionVersion` của lượt = 2: chuyển đổi
với version cũ 1 ⇒ (trace `f65c8e9867e74a8d`, **409**)

```json
{"code":"CONCURRENT_MODIFICATION","message":"Run conversion version is 2, not 1; reload the run"}
```

và với version 2 ⇒ `201`, chuyển lại được 60 vào một PO mới (trace `7765c69e7d0c425f`).

Huỷ **không body** một PO đã xác nhận (trace `d065daabff414451`, **409**):

```json
{"code":"STATE_CONFLICT","message":"Only DRAFT purchase orders can be cancelled without a body; send {expectedVersion, reason} to cancel a confirmed order"}
```

Huỷ có body: `reason` được cắt khoảng trắng hai đầu (`"  Sai nha cung cap  "` ⇒ `"Sai nha cung cap"`). Gửi lại cùng key + body ⇒ `200`
cùng trạng thái, không trả quota lần hai; cùng key, lý do khác ⇒ `409 IDEMPOTENCY_CONFLICT` (trace `d05b0d9795364b0e`).

---

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — dùng cùng một envelope:

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "quantity", "message": "must be greater than 0" } ] }
```

- `code` là tên hằng, FE rẽ nhánh theo nó. `result` **vắng mặt** khi lỗi; `errors` chỉ có khi lỗi theo field.
- Danh sách: `result` = `{ content, page, size, totalElements, totalPages, first, last }`.

| Tình huống | HTTP | `code` | `errors[]`? |
|---|---|---|---|
| Thiếu field, sai ngày (`lines[i].expectedDate` trước `orderDate`), quá 500 dòng, chữ số lẻ thứ 7, `orderDateFrom > orderDateTo`, thiếu `expectedVersion`/`reason` | 400 | `VALIDATION_ERROR` | có |
| Xác nhận khi còn dòng chưa có giá | 400 | `VALIDATION_ERROR` | có — `lines[i].unitPrice` |
| Vật tư sai loại / theo sê-ri | 422 | `OPERATION_NOT_ALLOWED` | có — `lines[i].itemId` |
| NCC/kho/vật tư/nhà máy/công ty đang ngừng | 422 | `RESOURCE_INACTIVE` | không |
| NCC/kho/vật tư khác công ty/nhà máy | 422 | `RESOURCE_SCOPE_MISMATCH` | không |
| `expectedVersion` lệch | 409 | `CONCURRENT_MODIFICATION` | không |
| Sửa/xác nhận PO không `DRAFT`; huỷ PO đã nhận; đóng PO không còn gì mở; huỷ không body PO đã xác nhận; đảo phiếu nhận của PO đã đóng | 409 | `STATE_CONFLICT` | không |
| Sửa field bị khoá của PO sinh từ hoạch định | 409 | **`SOURCE_LOCKED`** (mới) | có |
| Cùng `Idempotency-Key`, payload khác | 409 | `IDEMPOTENCY_CONFLICT` | không |
| `X-Plant-Id` lệch `plantId` | 409 | `STATE_CONFLICT` | không |
| Thiếu quyền, hoặc id PO không tồn tại | 403 | `PERMISSION_DENIED` | không |

Ánh xạ mã FE đề xuất ⇒ mã thật: `INVALID_QUANTITY` ⇒ `VALIDATION_ERROR` + field; `INVALID_UNIT_PRICE` ⇒ `VALIDATION_ERROR` + field
`lines[i].unitPrice`; `REFERENCE_NOT_ACTIVE` ⇒ `RESOURCE_INACTIVE`; `WAREHOUSE_SCOPE_MISMATCH` ⇒ `RESOURCE_SCOPE_MISMATCH`;
`STALE_VERSION` ⇒ `CONCURRENT_MODIFICATION`; `INVALID_STATE` ⇒ `STATE_CONFLICT`; `REASON_REQUIRED` ⇒ `VALIDATION_ERROR` field `reason`;
`SOURCE_LOCKED` ⇒ **đúng tên**; `NOT_FOUND` ⇒ `403 PERMISSION_DENIED` (xem Q26). `PENDING_GOODS_RECEIPT`, `PURCHASE_ORDER_OVER_RECEIPT`,
`LOT_*`, `QC_*`, `PURCHASE_QC_REQUIRED`, `VALUE_OVERFLOW` thuộc phần 2/3.

---

## §6 — Quyền, phạm vi và giới hạn đã biết

| Endpoint | Quyền | Phạm vi |
|---|---|---|
| `GET /v1/purchase-orders`, `GET /{id}` | `PERM_PURCHASE_ORDER_READ` | nhà máy (list theo `plantId`, detail theo nhà máy của PO) |
| `POST /drafts`, `PATCH /{id}`, `/confirm`, `/send`, `/cancel`, `/close`, `POST /v1/purchase-orders` | `PERM_PURCHASE_ORDER_MANAGE` | nhà máy |
| Chuyển đề xuất BUY (`POST /v1/planning-runs/{id}/conversions`) | `PERM_SUPPLY_SUGGESTION_MANAGE` **+ `PERM_PURCHASE_ORDER_MANAGE`** (mới) | nhà máy của lượt |

Cấp sẵn: `ADMIN`, `MANAGER` có cả READ + MANAGE; `OPERATOR` chỉ READ. **Không có quyền mới trong phần 1.**

**Giới hạn đã biết:**
1. **Id PO không tồn tại trả 403**, không phải 404 (Q26) — đặc điểm có sẵn của mọi endpoint `/{id}`.
2. **`createdAt`/`updatedAt` trong response ngay sau khi ghi có thể có 7–9 chữ số lẻ** (ví dụ `"2026-10-18T14:18:59.845926800Z"`), đọc lại
   thì 6 chữ số (`…845926Z`… làm tròn). Đây là hiện tượng có sẵn toàn hệ thống (đồng hồ máy chủ chi tiết hơn độ chính xác lưu). FE so
   sánh thời điểm thì **so sánh theo thời gian, đừng so sánh chuỗi**. Các mốc **mới** của GĐ7 (`confirmedAt`, `cancelledAt`, `closedAt`,
   `history[].at` của các bước đó, `releasedAt`) đã cắt sẵn về micro-giây nên trùng khít khi đọc lại.
3. **Tạo/huỷ PO cũ vẫn không cần `expectedVersion`** (đường tương thích). Muốn chặn ghi đè đồng thời thì luôn gửi body có `expectedVersion`.
4. `history[]` không có bước `CONFIRM` cho PO đã gửi trước GĐ7, và không có bước do phiếu nhận gây ra.

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

`mvn -o clean verify` (Postgres thật qua Testcontainers): **1686 case unit + 243 case tích hợp, `failures = 0`, `errors = 0`** (trước
phần này: 1634 + 233). Nghiệm thu đột biến (sửa cố ý một chỗ code, test phải đỏ): **12/13 bị bắt**; chỗ còn lại hoá ra là một điều kiện
thừa không trạng thái nào chạm tới — đã gỡ khỏi code.

Một lỗi thật do chính test tích hợp bắt được trước khi tới FE: bước đổi tên trạng thái chạy trước khi gỡ ràng buộc cũ ⇒ sẽ làm hỏng nâng
cấp trên **mọi** DB có PO `SENT` (DB trống thì không lộ). Đã sửa; nâng cấp sau đó chạy sạch trên DB dev có dữ liệu demo.

### 7.2 Kiểm chứng qua HTTP thật

Môi trường: backend dev, Postgres + Redis thật (không phải container test), cổng 8090, 18/10/2026 khoảng 21:19 (giờ VN). Fixture tạo mới:
công ty `GD7A-211857`. **54 lượt gọi kiểm tra, 0 lượt sai kỳ vọng.** Mỗi bước có `X-Trace-Id` ở §4. Đọc một PO 5 lần liên tiếp: thứ tự
dòng giống nhau cả 5 lần.

### 7.3 Chưa nghiệm thu

| Mục | Vì sao | Cần gì |
|---|---|---|
| Phiếu nhận `DRAFT`, nhận vào `HOLD`, QC, giá trị tồn | Phần 2/3, chưa làm | `_v2`, `_v3` |
| Hai lệnh đồng thời trên cùng PO (A10-kiểu cho PO) | Đã có khoá lạc quan + kiểm `expectedVersion`; chưa đo hai request **thật** song song qua HTTP | đo cùng lúc với phần 2 (khoá dòng PO khi post phiếu nhận) |
| Người chỉ có quyền hoạch định chuyển BUY ⇒ 403 | Đã kiểm bằng test phân quyền tự động; chưa dựng tài khoản như vậy để gọi live | tài khoản có `PERM_SUPPLY_SUGGESTION_MANAGE` mà không có `PERM_PURCHASE_ORDER_MANAGE` |
| Tranh chấp key idempotency (hai request cùng key gửi song song) | Request thứ hai dừng ở ràng buộc duy nhất của key và nhận `409 RESOURCE_ALREADY_EXISTS`, chưa đo live | — |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm:**

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| R06–R13, R16, R18, R20 (phiếu nhận, QC, tồn chờ kiểm) | Phần 2 và 3 của Giai đoạn 7 | đang làm tiếp, gửi `_v2`/`_v3` |
| R19 giá trị tồn / giá bình quân / `valueDelta` | **Hoãn có chủ đích**: hệ thống chưa có lớp tính giá trị tồn (không cột giá nào trên sổ kho). Sẽ trả `null` như FE Q19 cho phép | PO duyệt một phase riêng cho định giá tồn |
| `GET /api/v1/purchasing/references` | Không làm: dùng các endpoint danh mục có sẵn (NCC, vật tư, kho) của GĐ2 | nếu FE thật sự cần một endpoint gộp, nêu lại |
| Lượng/giá dạng **chuỗi** trên wire | Toàn hệ thống trả số JSON (đã thống nhất ở GĐ2) | quyết định toàn hệ thống riêng |
| Quota theo **kỳ/ngày** khi đưa PO vào MRP (R14/A33 "PO đến sau ngày cần không bù kỳ trước") | Hoạch định hiện **không chia kỳ**: PO mở cùng vật tư + cùng kho bù cho mọi nhu cầu trong tầm nhìn, bất kể ngày dự kiến. Lọc **kho** thì đúng | quyết định riêng về MRP chia kỳ |

**Việc FE cần làm tiếp:**

Bắt buộc:
- [ ] Đổi nhãn/logic trạng thái: `SENT` → `CONFIRMED`, `RECEIVED` → `FULLY_RECEIVED`, thêm `CLOSED`
- [ ] Tạo PO thủ công qua `POST /api/v1/purchase-orders/drafts` (không gửi mã)
- [ ] Mọi lệnh sửa/xác nhận/huỷ/đóng gửi `expectedVersion` = `version` của lần đọc gần nhất; xử lý `409 CONCURRENT_MODIFICATION` bằng tải lại
- [ ] Hiển thị `SOURCE_LOCKED` theo `errors[].field`; dùng `sourceLocked` để khoá form
- [ ] Huỷ PO đã xác nhận: gửi body `{expectedVersion, reason}`

Nên làm:
- [ ] Gửi `Idempotency-Key` (UUID mới cho mỗi lần bấm, giữ nguyên khi retry) trên tạo nháp/sửa/xác nhận/huỷ/đóng
- [ ] Sau khi huỷ PO sinh từ hoạch định, tải lại lượt hoạch định (`conversionVersion` đã tăng)
- [ ] Chạy A01–A05, A21, A24 (phần PO), A35–A36 trên môi trường dev và báo kết quả

# BE trả lời — Giai đoạn 4: Tồn kho, lô và chuyển kho

**Trả lời cho:** `FE_HANDOFF_GD_04_2026-10-12.md` (v1, P1) · **Ngày gửi:** 15/10/2026 · **Bản:** v1
**Trạng thái:** BE đã triển khai + tự nghiệm thu trên môi trường dev. **Chưa** phải "FE nghiệm thu đạt" — task chỉ đóng
khi FE chạy A01–A26 trên adapter của mình và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Chuyển kho, kiểm đếm tuyệt đối, chi tiết movement và danh sách "Giữ cho sản xuất" đã có API; đổi trạng thái lô đã
chặn đủ 3 trường hợp và kiểm quyền ở mọi kho; định giá tồn kho (giá bình quân, giá trị tồn, snapshot giá) CHƯA làm
trong giai đoạn này — mọi field giá không có trên API.**

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | Aggregate một dòng Item + Warehouse, 6 lượng | 🔴 **Lệch thật, đã sửa** | Đo live: `onHand 12` = `available 5` + `rejected 1` + **6 đơn vị không nằm trong nhóm nào** (lô HOLD không được đếm ở đâu; lô AVAILABLE quá hạn bị tính vào available). Nay thêm `heldLotQuantity`, lô quá hạn vào `expiredQuantity` — §2 |
| R02 | Lịch sử theo lô + kho | ✅ **Đã có**, chỉ bổ sung | `GET /movements` **đã** có filter `lotId`. Lô sang kho mới nhận nguồn là phiếu chuyển — §4.6 |
| R03 | Receive có `unitCost` + `expiresAt` | 🟡 `expiresAt` **đã làm**; `unitCost` **chưa làm** (§8) | |
| R04 | Issue chỉ trừ free AVAILABLE chưa hết hạn, an toàn đồng thời | 🔴 **Thiếu thật, đã sửa một phần** | Trước đây `/issue` **xuất được lô đã quá hạn**. Nay `/issue` từ chối. Đồng thời: đã an toàn từ trước (không âm kho) |
| R05 | Kiểm đếm tuyệt đối + `expectedVersion` | 🟡 **Thiếu thật, đã làm** | Endpoint mới `POST /api/v1/inventory/counts` |
| R06 | Chuyển kho atomic | 🟡 **Thiếu thật, đã làm** | 3 endpoint mới — §4.1–4.3 |
| R07 | Đổi trạng thái: chặn reserved / không đổi / quá hạn→AVAILABLE; kiểm mọi vị thế | 🔴 **Lỗ hổng thật, đã sửa** | Trước đây chỉ kiểm quyền ở **một** kho trong body ⇒ người có quyền ở kho A đổi được chất lượng hàng nằm ở kho B ngoài phạm vi. Cả 3 trường hợp đều **không** bị chặn |
| R08 | Tối đa 6 số lẻ, không làm tròn âm thầm | 🔴 **Lỗi thật, đã sửa** | Trước đây `1.2345678` được nhận và DB làm tròn thành `1.234568`. Nay `400` |
| R09 | Idempotency | ✅ **Đã có**; chuyển kho + kiểm đếm theo cùng quy tắc | Q11 |
| R10 | Snapshot giá trên movement | ⏸ **Chưa làm** (§8) | |
| R11 | WO dùng chung sổ tồn | ✅ **Đã đúng từ trước**, không sửa | |
| R12 | Actor / thời gian / trace do server ghi | 🟡 **Đã có trong DB, nay trả ra API** | `traceId`, `createdBy`, `createdByUsername` trên movement |
| — | `GET /inventory/reservations`, `GET /inventory/movements/{id}` | 🟡 **Đã làm** | §4.5, §4.4 |

### Trả lời Q1–Q14

| Q | Câu hỏi | Trả lời |
|---|---|---|
| Q1 | Tái dùng API có sẵn trước khi thêm mới? | **Có.** receive/issue/adjust/balances/lots/movements giữ nguyên đường dẫn, chỉ thêm field |
| Q2 | Chuyển kho chỉ cùng nhà máy, không in-transit/duyệt? | **Có.** Không sửa/huỷ phiếu sau khi ghi |
| Q3 | Kiểm đếm là endpoint riêng, không đổi nghĩa `quantityDelta`? | **Có.** `POST /adjust` giữ nguyên nghĩa delta |
| Q4 | Kiểm đếm bắt buộc `expectedVersion`? | **Có** — là `version` của **đúng vị thế** (Item + Kho + Lô, hoặc không lô), lấy từ `GET /api/v1/inventory/balances`. Aggregate gộp nhiều dòng nên **không** có một version duy nhất |
| Q5 | VND moving-average theo Item + Kho? | **Chưa làm ở giai đoạn này** (§8). Không trả field giá nào — FE coi như "chưa có giá" (tương đương `null`) |
| Q6 | Chất lượng lô toàn cục hay theo kho? | **Toàn cục**, và kiểm quyền ở **mọi** kho còn tồn của lô |
| Q7 | `expiredQuantity` chỉ đếm AVAILABLE quá hạn? | **Có**, cộng thêm lô có trạng thái `EXPIRED` (hiện không luồng nào tạo ra trạng thái này). Lô HOLD/REJECTED đã quá hạn vẫn nằm ở nhóm HOLD/REJECTED — không đếm hai lần |
| Q8 | Hạn dùng là thời điểm so với đồng hồ server? | **Có.** Quá hạn khi `expiresAt <= giờ server` |
| Q9 | Chuyển kho cho dòng trùng vị thế + kiểm tổng? | **Khác FE đề xuất: từ chối dòng trùng** (`400`, `errors[0].field = "lines[1]"`). Lý do: loại bỏ hẳn lỗi "mỗi dòng đủ, tổng thì thiếu"; FE gộp hai dòng trước khi gửi |
| Q10 | Tiền 6 số lẻ, average 12, làm tròn? | Không áp dụng — chưa có định giá |
| Q11 | Idempotency lưu bền? | **Có, không hết hạn**: key lưu trên chính phiếu/dòng sổ tồn. Cùng key + cùng nội dung ⇒ trả lại đúng kết quả cũ (cùng `transferId`, cùng `movementId`). Cùng key + nội dung khác ⇒ `409 IDEMPOTENCY_CONFLICT`. Quyền được kiểm **trước** khi trả kết quả cũ. Giới hạn: key duy nhất theo **loại thao tác**, chưa theo người dùng (§6) |
| Q12 | Giá lịch sử chưa biết giữ null? | Không áp dụng — chưa có định giá |
| Q13 | Số thập phân là chuỗi trên wire? | **Không.** Toàn hệ thống trả **JSON number**; đổi riêng endpoint mới là hai hợp đồng trong một API. Lưu ý: số trong response **không cố định số lẻ** — response vừa ghi lặp lại cách viết của request (`2`), đọc lại từ DB trả `2.000000`. FE so sánh theo giá trị, không theo chuỗi |
| Q14 | Ánh xạ mã lỗi vào mã có sẵn? | **Có**, chỉ thêm 2 mã mới: `NO_CHANGE`, `RESERVED_STOCK` — bảng đầy đủ ở §5 |

---

## §1 — Nguyên nhân thật

- **Đổi trạng thái lô:** trạng thái là của **lô** (một lô có thể nằm ở nhiều kho), nhưng quyền chỉ được kiểm ở kho FE gửi
  lên. Có từ khi endpoint ra đời (08/2026). Dữ liệu cũ **không** bị ảnh hưởng — chỉ là ai được phép bấm.
- **Lô quá hạn:** hệ thống lưu hạn dùng nhưng **không** nơi nào so nó với đồng hồ. Nay chặn ở **luồng kho thủ công**
  (xuất kho, chuyển kho, kiểm đếm, đổi sang AVAILABLE). **Giữ chỗ / xuất vật tư cho Lệnh sản xuất và hoạch định vẫn chưa xét
  hạn** — quyết định có chủ đích, để không đổi kết quả hoạch định trong giai đoạn này.
- **Aggregate:** cột "đang kiểm tra" chỉ đếm hàng **không có lô**; lô HOLD không có cột nào. Cùng lỗi với dòng tồn theo lô
  không có, vì ở đó FE đọc trạng thái lô.
- **Số lẻ:** cột lưu 6 số lẻ, API nhận không giới hạn ⇒ DB làm tròn im lặng. Ảnh hưởng mọi lệnh ghi tồn kho đã gửi quá 6 số
  lẻ — **không** thể biết lại giá trị gốc của dữ liệu cũ đã bị làm tròn.

---

## §2 — Thay đổi contract

### Endpoint

| Endpoint | Trước | Sau | Phá client đang chạy? |
|---|---|---|---|
| `POST /api/v1/inventory/transfers` | không có | tạo phiếu, **201** | Không |
| `GET /api/v1/inventory/transfers` | không có | danh sách theo nhà máy | Không |
| `GET /api/v1/inventory/transfers/{transferId}` | không có | chi tiết + dòng + cặp movement | Không |
| `POST /api/v1/inventory/counts` | không có | kiểm đếm tuyệt đối, **200** | Không |
| `GET /api/v1/inventory/movements/{movementId}` | không có | chi tiết một movement | Không |
| `GET /api/v1/inventory/reservations` | không có | giữ chỗ cho sản xuất theo kho | Không |
| `GET /api/v1/inventory/balances/aggregate` | — | thêm query `search` (mã hoặc tên vật tư, không phân biệt hoa thường) | Không |
| `POST /api/v1/inventory/lots/{lotId}/status` | 3 trường hợp lọt | bị chặn | **Có** — §3 |
| `POST /api/v1/inventory/issue` | xuất được lô quá hạn | bị chặn | **Có** — §3 |

### Field mới

| Field | Kiểu | Nullable? | Nguồn giá trị | Ví dụ thật |
|---|---|---|---|---|
| `heldLotQuantity` (aggregate) | number | Không | Tổng tồn của các lô `HOLD` | `2.000000` |
| `version` (`GET /balances`, `balance` trong kết quả kiểm đếm) | integer | Không | Tăng 1 sau **mỗi** lần ghi vào vị thế đó | `2` |
| `traceId` (movement) | string | Có (dòng cũ / ghi ngoài request) | Mã truy vết server ghi lúc tạo movement — trùng `X-Trace-Id` của request đó | `"b21c102eb1e14401"` |
| `createdBy` (movement) | UUID | Có | Người đăng nhập thực hiện | `"23fce5b6-…"` |
| `createdByUsername` (movement) | string | Có | Tên đăng nhập của `createdBy` | `"admin"` |
| `expiresAt` (request `POST /receive`) | ISO timestamp | Có | Chỉ khi receipt **tạo lô mới**; phải ở tương lai | `"2031-12-31T16:59:59Z"` |

### Công thức aggregate mới (bốn nhóm không chồng nhau)

```
availableQuantity = Σ (tồn − giữ chỗ − đang kiểm tra)  của: hàng không lô + lô AVAILABLE còn hạn
heldLotQuantity   = Σ tồn của lô HOLD
rejectedQuantity  = Σ tồn của lô REJECTED
expiredQuantity   = Σ tồn của lô EXPIRED + lô AVAILABLE đã quá hạn
qualityHoldQuantity giữ nguyên nghĩa: phần đang kiểm tra của hàng KHÔNG có lô
```

Ví dụ đo thật (lượt `170309`, một vật tư theo lô ở Kho A): lô 5 còn hạn, lô HOLD 2, lô REJECTED 1, lô AVAILABLE quá hạn 4
⇒ `onHand 12 = available 5 + heldLot 2 + rejected 1 + expired 4`.

---

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /lots/{id}/status` | lô còn tồn ở kho mà người gọi **không** có quyền | 200 | `403 PERMISSION_DENIED` | Bắt buộc mới | Hiển thị "không đủ quyền" |
| `POST /lots/{id}/status` | đổi sang đúng trạng thái hiện tại | 200 | `409 NO_CHANGE` | Bắt buộc mới | Hiển thị "không có thay đổi" |
| `POST /lots/{id}/status` | lô quá hạn → `AVAILABLE` | 200 | `409 LOT_NOT_ELIGIBLE` | Bắt buộc mới | |
| `POST /lots/{id}/status` | lô còn giữ chỗ cho Lệnh sản xuất | 200 | `409 RESERVED_STOCK` | Bắt buộc mới | "Giải phóng giữ chỗ trước" |
| `POST /issue` | lô AVAILABLE đã quá hạn | 200 | `409 LOT_NOT_ELIGIBLE` | Bắt buộc mới | |
| `POST /receive`, `/issue`, `/adjust` | số lượng > 6 số lẻ (kể cả số 0 thừa: `1.1234560`) | 200, bị làm tròn | `400 VALIDATION_ERROR` trên `quantity`/`quantityDelta` | Bắt buộc mới | Giới hạn 6 số lẻ ở form |
| `GET /balances/aggregate` | có lô AVAILABLE quá hạn | tính vào `availableQuantity` | tính vào `expiredQuantity` | Đổi số liệu | Không đổi code, số tự đúng |
| `GET /balances`, `GET /lots`, `GET /lots/{id}` | lô AVAILABLE quá hạn | `availableQuantity` > 0 | `availableQuantity = 0` (`onHandQuantity` giữ nguyên) | Đổi số liệu | |

Không đổi giá trị mặc định query param nào. Thứ tự sắp xếp mặc định: `GET /movements` thêm `movementId` làm tiêu chí phụ khi
trùng thời điểm — thứ tự giữa các dòng **cùng** `createdAt` nay ổn định (trước đây không xác định). Không bỏ field nào.
**Thứ tự deploy:** không ràng buộc — các chặn mới là sửa sai; client gửi đúng không bị ảnh hưởng.

---

## §4 — Cách dùng (copy từ lượt gọi thật `170309`, 15/10/2026 ~17:03 giờ VN)

Mọi request cần `Authorization: Bearer <access token>` (đã che). Số trong request là số FE gửi; số trong response là nguyên
văn server trả.

### 4.1 `POST /api/v1/inventory/transfers` — tạo phiếu chuyển kho

Header: `Idempotency-Key: g4-5c34b29bc6ac49b7` (**bắt buộc**), `X-Plant-Id: 9b401078-d575-42bf-8f7b-d3e38544ff4b` (tuỳ chọn;
nếu gửi phải là nhà máy của hai kho).

```json
{"fromWarehouseId": "86d973e3-49c0-482d-bad0-11eb273892c2", "toWarehouseId": "c923998c-6f3a-4c9b-b333-f3e6659d3a58", "reason": "Chuyen sang kho B", "lines": [{"itemId": "380df8ef-cbe8-4900-94ea-0dad90f303f9", "quantity": 2}, {"itemId": "34b11c9c-0494-4a95-87bd-b65b0525361d", "lotId": "a03dc563-322e-4bc5-9d48-0ec644002437", "quantity": 2}]}
```

→ **201**, `X-Trace-Id: 068f975680204e9d`

```json
{
  "code": "SUCCESS",
  "result": {
    "transferId": "d8961ad8-18f3-467c-8edb-2b81363e326e",
    "code": "TR-D8961AD8",
    "status": "POSTED",
    "plantId": "9b401078-d575-42bf-8f7b-d3e38544ff4b",
    "fromWarehouseId": "86d973e3-49c0-482d-bad0-11eb273892c2",
    "fromWarehouseCode": "WA-170309",
    "fromWarehouseName": "Kho A",
    "toWarehouseId": "c923998c-6f3a-4c9b-b333-f3e6659d3a58",
    "toWarehouseCode": "WB-170309",
    "toWarehouseName": "Kho B",
    "reason": "Chuyen sang kho B",
    "postedAt": "2026-10-15T10:03:15.134628100Z",
    "traceId": "068f975680204e9d",
    "createdBy": "23fce5b6-dd3b-4d30-a8b8-c33962a38a82",
    "createdByUsername": "admin",
    "version": 0,
    "lines": [
      {
        "transferLineId": "e649b460-9e21-4ab8-9dc3-04f40b064f86",
        "lineNo": 1,
        "itemId": "380df8ef-cbe8-4900-94ea-0dad90f303f9",
        "itemCode": "N-170309",
        "itemName": "Thep tam",
        "uom": "G4170309",
        "lotId": null,
        "lotCode": null,
        "lotExpiresAt": null,
        "quantity": 2,
        "outMovementId": "c451be20-6063-4c09-8fb9-51e4fa2efbd6",
        "inMovementId": "c60276d2-af41-4dde-ae3d-ae0bae7138a9"
      },
      {
        "transferLineId": "3cac9b2c-f5da-42a2-a81f-9b7fe570ccef",
        "lineNo": 2,
        "itemId": "34b11c9c-0494-4a95-87bd-b65b0525361d",
        "itemCode": "L-170309",
        "itemName": "Son lo",
        "uom": "G4170309",
        "lotId": "a03dc563-322e-4bc5-9d48-0ec644002437",
        "lotCode": "LOT1-170309",
        "lotExpiresAt": "2031-12-31T16:59:59Z",
        "quantity": 2,
        "outMovementId": "b323423e-c648-417b-a6bc-45b9934f1599",
        "inMovementId": "09558d98-1fd8-4fbc-b01f-40b73b101e7e"
      }
    ]
  },
  "message": "Created successfully"
}
```

> ⚠️ Mẫu trên lấy từ lượt `170309`, **trước** bản sửa cuối: `postedAt` có 9 chữ số lẻ (`…134628100Z`) trong khi đọc lại
> trả `…134628Z`. Đã sửa — ở bản đang chạy, lượt `170856` đo được `postedAt` **giống hệt** ở response tạo, retry cùng key,
> chi tiết và danh sách (`2026-10-15T10:09:01.769845Z`, trace `e17515d8b2c44bfe` / `3e6cbcc1b5ef4bfc` / `b83d226d8ab94edd` /
> `c1d7c14d4bb34d64`). Mọi field khác không đổi.

Lỗi hay gặp nhất — một dòng thiếu tồn tự do, **không dòng nào được ghi** → **409**, `X-Trace-Id: b71b9e4aaf9c49d9`:

```json
{
  "code": "INSUFFICIENT_AVAILABLE_STOCK",
  "message": "lines[1].quantity: Requested quantity exceeds free available stock",
  "errors": [
    {
      "field": "lines[1].quantity",
      "message": "Requested quantity exceeds free available stock"
    }
  ]
}
```

Đọc lại tồn Kho A ngay sau đó: vật tư dòng 1 vẫn `5.000000` (không bị trừ 1) — trace `75c4115623bb4ac7`.

### 4.2 `GET /api/v1/inventory/transfers?plantId=…&warehouseId=…&search=TR-`

`plantId` bắt buộc. `warehouseId` (tuỳ chọn) lọc phiếu chạm kho đó ở **một trong hai phía**. `search` theo mã phiếu. Sắp xếp
mặc định `postedAt` giảm dần. Mỗi dòng giống §4.1 nhưng `"lines": null` (xem chi tiết để có dòng). → **200**, trace
`bb2df5a456b6481e`. Thiếu `plantId` ⇒ `400 VALIDATION_ERROR`, `errors[0].field = "plantId"`.

### 4.3 `GET /api/v1/inventory/transfers/{transferId}`

→ **200**, trace `cc864c2cfd104b7b` — hình dạng như §4.1, `lines[].quantity` đọc từ DB là `2.000000`. Không có quyền đọc **cả
hai** kho của phiếu ⇒ `403 PERMISSION_DENIED`.

### 4.4 `POST /api/v1/inventory/counts` — kiểm đếm tuyệt đối

Bước 1 — lấy `version` của vị thế: `GET /api/v1/inventory/balances?warehouseId=…&itemId=…` → `"quantity": 9.000000, …, "version": 1`.

Bước 2 — header `Idempotency-Key: g4-9b4199d946994cf8` (**bắt buộc**):

```json
{"itemId": "380df8ef-cbe8-4900-94ea-0dad90f303f9", "warehouseId": "86d973e3-49c0-482d-bad0-11eb273892c2", "countedQuantity": 7, "expectedVersion": 1, "reason": "Dem"}
```

→ **200**, `X-Trace-Id: b21c102eb1e14401`

```json
{
  "code": "SUCCESS",
  "result": {
    "previousQuantity": 9.000000,
    "countedQuantity": 7,
    "quantityDelta": -2.000000,
    "movement": {
      "movementId": "99c29615-94be-42eb-a4d2-27a702a2e5cc",
      "itemId": "380df8ef-cbe8-4900-94ea-0dad90f303f9",
      "warehouseId": "86d973e3-49c0-482d-bad0-11eb273892c2",
      "lotId": null,
      "lotCode": null,
      "movementType": "ADJUST_OUT",
      "direction": "OUT",
      "quantity": 2.000000,
      "reason": "Dem",
      "referenceType": "STOCK_COUNT",
      "referenceId": null,
      "idempotencyKey": "g4-9b4199d946994cf8",
      "createdAt": "2026-10-15T10:03:14.837905700Z",
      "traceId": "b21c102eb1e14401",
      "createdBy": "23fce5b6-dd3b-4d30-a8b8-c33962a38a82",
      "createdByUsername": "admin"
    },
    "balance": {
      "balanceId": "ae648e7d-361d-4442-ae31-ee7cef0e94dc",
      "itemId": "380df8ef-cbe8-4900-94ea-0dad90f303f9",
      "warehouseId": "86d973e3-49c0-482d-bad0-11eb273892c2",
      "lotId": null,
      "lotCode": null,
      "quantity": 7.000000,
      "reservedQuantity": 0.000000,
      "qualityHoldQuantity": 0.000000,
      "availableQuantity": 7.000000,
      "updatedAt": "2026-10-15T10:03:14.833643900Z",
      "version": 2
    }
  },
  "message": "OK"
}
```

Lỗi hay gặp nhất — đã có biến động mới sau khi FE đọc (`expectedVersion` cũ) → **409**, trace `0576522f04e242c2`:

```json
{
  "code": "CONCURRENT_MODIFICATION",
  "message": "Stock position changed since it was read; reload and count again"
}
```

Đếm đúng bằng số hiện tại → `409 NO_CHANGE` (`"Counted quantity equals the current quantity; nothing to record"`), trace
`a5b3117805f74b6c`. Vật tư theo lô **phải** đếm theo lô (`lotId`); vị thế chưa từng có tồn ⇒ `404` (dùng nhập kho).

### 4.5 `GET /api/v1/inventory/reservations?warehouseId=…`

Query: `warehouseId` (bắt buộc), `itemId`, `lotId`, `status` (`ACTIVE`/`RELEASED`/`CONSUMED`/`CANCELLED`, bỏ trống = tất cả),
`page`, `size`. Mỗi dòng: `reservationId, workOrderId, workOrderNo, workOrderStatus, componentLineId, componentLineNo, itemId,
itemCode, itemName, uom, warehouseId, lotId, lotCode, quantity, consumedQuantity, remainingQuantity, status, createdAt`
(`remainingQuantity = quantity − consumedQuantity`). Lượt đo live **không** có Lệnh sản xuất nên trang rỗng — trace
`f924016c22684473`:

```json
{
  "code": "SUCCESS",
  "result": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  },
  "message": "OK"
}
```

Dòng có dữ liệu thật chỉ được kiểm bằng test tự động trên Postgres (§7), **chưa** qua HTTP.

### 4.6 `GET /api/v1/inventory/movements/{movementId}` và lô sau khi chuyển

Movement `TRANSFER_OUT` của dòng 1 — **200**, trace `27456f1d20494df9`:

```json
{
  "code": "SUCCESS",
  "result": {
    "movementId": "c451be20-6063-4c09-8fb9-51e4fa2efbd6",
    "itemId": "380df8ef-cbe8-4900-94ea-0dad90f303f9",
    "warehouseId": "86d973e3-49c0-482d-bad0-11eb273892c2",
    "lotId": null,
    "lotCode": null,
    "movementType": "TRANSFER_OUT",
    "direction": "OUT",
    "quantity": 2.000000,
    "reason": "Chuyen sang kho B",
    "referenceType": "STOCK_TRANSFER",
    "referenceId": "d8961ad8-18f3-467c-8edb-2b81363e326e",
    "idempotencyKey": "g4-5c34b29bc6ac49b7:L1",
    "createdAt": "2026-10-15T10:03:15.161975Z",
    "traceId": "068f975680204e9d",
    "createdBy": "23fce5b6-dd3b-4d30-a8b8-c33962a38a82",
    "createdByUsername": "admin"
  },
  "message": "OK"
}
```

`movementType` mới: `TRANSFER_OUT` (kho nguồn), `TRANSFER_IN` (kho đích); `referenceType = "STOCK_TRANSFER"`,
`referenceId = transferId`. Kiểm đếm ghi `ADJUST_OUT`/`ADJUST_IN` với `referenceType = "STOCK_COUNT"`.

Lô sau khi chuyển, xem tại kho đích (`GET /api/v1/inventory/lots/{lotId}?warehouseId=<kho B>`) — **200**, trace
`f1ebd694cafc445a`: cùng `lotCode`, cùng `receivedAt`, cùng `expiresAt` `"2031-12-31T16:59:59Z"`, `"sourceMovementType":
"TRANSFER_IN"`, `"sourceReferenceType": "STOCK_TRANSFER"`, `"sourceReferenceId": "d8961ad8-…"`, `balances` chỉ có Kho B
(`onHandQuantity 2.000000`). Lịch sử theo lô ở từng kho tách riêng: `GET /movements?warehouseId=<A>&lotId=…` chỉ trả
`TRANSFER_OUT`/`RECEIVE` của kho A (trace `46c894f013bb4816`).

### 4.7 Đổi trạng thái lô, nhập lô có hạn dùng, aggregate

- Đổi lô HOLD sang HOLD lần nữa → `409 NO_CHANGE`, `"Lot is already in status HOLD"`, trace `b33d2ee49a2a47f7`.
- Lô đã quá hạn sang AVAILABLE → `409 LOT_NOT_ELIGIBLE`, `"An expired lot cannot be made AVAILABLE"`, trace `96f10240fbc542fb`.
  Sang HOLD/REJECTED thì vẫn được.
- `POST /api/v1/inventory/receive` với lô **mới** + `"expiresAt": "2031-12-31T16:59:59Z"` → 200 (trace `7e3f3c8c24864c38`).
  `expiresAt` quá khứ → `400`, `errors[0].field = "expiresAt"`, `"must be a future date"` (trace `bc809107698c4822`).
  Gửi `expiresAt` cho lô **đã có** (`lotId`) → `422 OPERATION_NOT_ALLOWED` (trace `a9824b9b2041421a`).
- Aggregate sau cùng của lượt đo (trace `122dc180934b40cf`):

```json
{
  "itemId": "34b11c9c-0494-4a95-87bd-b65b0525361d",
  "itemCode": "L-170309",
  "itemName": "Son lo",
  "uom": "G4170309",
  "warehouseId": "86d973e3-49c0-482d-bad0-11eb273892c2",
  "onHandQuantity": 12.000000,
  "reservedQuantity": 0.000000,
  "availableQuantity": 5.000000,
  "qualityHoldQuantity": 0.000000,
  "rejectedQuantity": 1.000000,
  "expiredQuantity": 4.000000,
  "heldLotQuantity": 2.000000,
  "lotCount": 4,
  "updatedAt": "2026-10-15T10:03:13.196298Z"
}
```

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

- `code` là **tên hằng**, FE rẽ nhánh theo nó — nhiều mã dùng chung HTTP 409.
- `errors` chỉ xuất hiện khi có lỗi theo field; khi lỗi, `result` **vắng mặt**.
- Phân trang: `result = { content, page, size, totalElements, totalPages, first, last }`.

| Tình huống | HTTP | `code` | `errors[]`? |
|---|---|---|---|
| Thiếu/sai field, số lượng ≤ 0, đếm âm | 400 | `VALIDATION_ERROR` | ✅ |
| Quá 6 số lẻ (FE đề xuất `DECIMAL_PRECISION_EXCEEDED`) | 400 | `VALIDATION_ERROR` | ✅ `quantity` / `lines[i].quantity` |
| Hai kho giống nhau (FE: `SAME_WAREHOUSE`) | 400 | `VALIDATION_ERROR` | ✅ `toWarehouseId` |
| Hai dòng cùng vật tư + lô | 400 | `VALIDATION_ERROR` | ✅ `lines[i]` |
| Vật tư theo lô thiếu `lotId` khi chuyển | 400 | `LOT_REQUIRED` | ✅ `lines[i].lotId` |
| Thiếu `Idempotency-Key` | 400 | `MISSING_REQUIRED_FIELD` | — |
| Không đủ tồn tự do | 409 | `INSUFFICIENT_AVAILABLE_STOCK` | ✅ khi chuyển kho (`lines[i].quantity`); — khi xuất |
| Lô HOLD/REJECTED/quá hạn (FE: `LOT_EXPIRED`) | 409 | `LOT_NOT_ELIGIBLE` | — |
| Còn giữ chỗ cho Lệnh sản xuất 🆕 | 409 | `RESERVED_STOCK` | — |
| Không có gì thay đổi 🆕 | 409 | `NO_CHANGE` | — |
| `expectedVersion` cũ (FE: `STALE_VERSION`) | 409 | `CONCURRENT_MODIFICATION` | — |
| Cùng key, nội dung khác | 409 | `IDEMPOTENCY_CONFLICT` | — |
| `X-Plant-Id` khác nhà máy của hai kho | 409 | `STATE_CONFLICT` | — |
| Kho/vật tư/nhà máy ngừng hoạt động (FE: `*_INACTIVE`) | 422 | `RESOURCE_INACTIVE` | — |
| Hai kho khác nhà máy, lô khác vật tư (FE: `PLANT_SCOPE_MISMATCH`, `LOT_ITEM_MISMATCH`) | 422 | `RESOURCE_SCOPE_MISMATCH` | — |
| `expiresAt` cho lô đã có; vật tư theo serial | 422 | `OPERATION_NOT_ALLOWED` | — |
| Thiếu quyền, kể cả ID ngoài phạm vi | 403 | `PERMISSION_DENIED` | — |
| ID không tồn tại (phiếu, movement) | 403 | `PERMISSION_DENIED` | — (xem §6) |
| Vị thế chưa có khi kiểm đếm | 404 | `ENTITY_NOT_FOUND` | — |
| Hết phiên | 401 | mã Auth hiện có | — |

---

## §6 — Quyền, phạm vi và giới hạn đã biết

| Thao tác | Quyền | Phạm vi được kiểm |
|---|---|---|
| Tạo phiếu chuyển | `PERM_INVENTORY_MOVE` | **cả** kho nguồn **và** kho đích |
| Danh sách phiếu | `PERM_INVENTORY_READ` | nhà máy `plantId`; hoặc kho `warehouseId` nếu gửi (người được gán theo kho dùng cách này) |
| Chi tiết phiếu | `PERM_INVENTORY_READ` | **một trong hai** kho của phiếu |
| Kiểm đếm | `PERM_INVENTORY_MOVE` | kho |
| Đổi trạng thái lô | `PERM_INVENTORY_MOVE` (**không** phải `PERM_INVENTORY_MANAGE` như FE ghi — `MANAGE` là quyền thiết lập kho) | kho trong body **và mọi kho còn tồn của lô** |
| Chi tiết movement, reservations | `PERM_INVENTORY_READ` | kho của movement / kho trong query |

Không có permission mới; ADMIN/MANAGER/OPERATOR giữ nguyên các quyền inventory đang có.

**Giới hạn đã biết:**

- **ID không tồn tại trả 403, không phải 404** trên chi tiết phiếu/movement — hệ thống tra bản ghi để biết thuộc kho nào,
  không có bản ghi thì từ chối trước. Giống mọi endpoint chi tiết khác hiện có. Thiếu quyền ở một kho của lô khi đổi trạng
  thái cũng trả **403** (không phải 404 như FE đề xuất — FE vẫn đang xem lô đó ở kho của mình, 404 sẽ sai nghĩa); response
  **không** nói kho nào.
- **Idempotency-Key duy nhất theo loại thao tác, chưa theo người dùng:** hai người khác nhau vô tình dùng cùng một key cho
  cùng loại thao tác sẽ bị coi là một yêu cầu. FE luôn sinh key ngẫu nhiên (UUID) là đủ.
- **`createdAt`/`updatedAt` của movement và dòng tồn** trong response **vừa ghi** có thể có 9 chữ số lẻ, đọc lại thì 6 (vd
  `…837905700Z` → `…837905Z`). Có sẵn trên **mọi** lệnh ghi tồn kho, không riêng giai đoạn này. So sánh theo `movementId`,
  không theo chuỗi thời gian. `postedAt` của phiếu chuyển **không** bị (đã sửa).
- **Mã lô phân biệt hoa thường** (`L1` khác `l1`) — A05 của FE kỳ vọng ngược lại; chưa đổi (§8).
- Kiểm đếm và chuyển kho **chưa** hỗ trợ vật tư theo **serial** (`422`).

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Test tự động

`mvn -o clean verify` (unit + integration trên PostgreSQL thật qua Testcontainers):
**1461 case unit + 202 case integration, failures = 0, errors = 0**. Trước giai đoạn này: 1394 + 192 (đo lại trên một bản
sạch, khớp số đã ghi). Không xoá test cũ nào.

Integration đáng chú ý (DB thật):
- Chuyển kho hai dòng commit đủ cả hai phía, lô giữ nguyên hạn dùng; `postedAt` của response khớp DB.
- **Dòng cuối lỗi sau khi dòng đầu đã ghi ⇒ không còn gì** (tồn, phiếu, movement) — kiểm transaction thật, không chỉ kiểm
  trước khi ghi.
- Hai phiếu đồng thời tranh nhau phần tồn tự do cuối (10, mỗi phiếu xin 6), 5 vòng: luôn **một** phiếu thành công, phiếu kia
  `INSUFFICIENT_AVAILABLE_STOCK`; tồn nguồn cuối cùng 4, không âm.
- Aggregate: lô AVAILABLE quá hạn vào `expiredQuantity`; lô HOLD vào `heldLotQuantity`; `search` theo mã/tên.
- Danh sách giữ chỗ giữ cả dòng của vật tư **không** theo lô.

**Kiểm chứng "test có bắt được lỗi thật không":** cố ý phá 7 chỗ trong code (bỏ khoá tồn nguồn, bỏ transaction chung của
phiếu, kiểm quyền lô chỉ ở một kho, đổi `NO_CHANGE` sang một mã 409 khác, bỏ chặn lô quá hạn khi xuất, bỏ chặn đếm dưới lượng
giữ chỗ, bỏ điều kiện hạn dùng khỏi nhóm available) — **7/7 lần test đỏ đúng chỗ**; đã khôi phục.

### 7.2 Kiểm chứng qua HTTP thật

- **Môi trường:** backend local (profile dev), PostgreSQL + Redis thật qua Docker, tài khoản quản trị + một tài khoản không có
  quyền inventory tạo riêng. Mỗi lượt tự dựng công ty/2 nhà máy/4 kho/2 vật tư mới.
- **Lượt `165104`** (16:51): 70 lượt gọi, mọi HTTP status đúng kỳ vọng — **nhưng** đọc nội dung lộ ra lệch aggregate ở §0 R01.
- **Lượt `170021`** (17:00), sau khi sửa aggregate: 72/72 đúng; bốn nhóm cộng đủ `onHand`.
- **Lượt `170309`** (17:03): 72/72, ghi nguyên văn mọi response — mẫu ở §4. Lộ ra `postedAt` lệch chữ số lẻ.
- **Lượt `170856`** (17:09), bản cuối: 72/72; `postedAt` giống hệt ở 4 lần đọc (§4.1). Chứng minh đúng bản build: cùng phép đo
  "`postedAt` lúc tạo so với lúc đọc lại" **khác** ở lượt `170309` và **khớp** ở lượt `170856`.
- **Dữ liệu dọn:** kho, nhà máy, công ty thử đã ngừng hoạt động cuối mỗi lượt (công ty `GD4-165104`, `GD4-170021`, `GD4-170125`,
  `GD4-170309`, `GD4-170856`). Lô "quá hạn" được dựng bằng cách sửa trực tiếp hạn dùng trong DB — API cố ý không cho ghi hạn
  quá khứ (A06).

| Ca FE | Kết quả (lượt `170309`) | `X-Trace-Id` |
|---|---|---|
| A01 aggregate nhiều lô | `12 = 5 + 2 HOLD + 1 REJECTED + 4 quá hạn` | `122dc180934b40cf` |
| A02 lô ở hai kho | lịch sử kho A chỉ có dòng kho A; chi tiết tại kho B chỉ có balance kho B | `46c894f013bb4816`, `f1ebd694cafc445a` |
| A05 nhập lô mới có hạn dùng | 200, lô mới tạo | `7e3f3c8c24864c38` |
| A06 hạn dùng quá khứ | 400 trên `expiresAt` | `bc809107698c4822` |
| A07 xuất vượt / đúng tồn tự do | 409 `INSUFFICIENT_AVAILABLE_STOCK` / 200 | §4 log, cùng lượt |
| A08 xuất lô HOLD / REJECTED / quá hạn | 409 `LOT_NOT_ELIGIBLE` ×3 | `f80f7b77ae064fa9` (quá hạn) |
| A09 đếm 9 → 7 | 200, delta −2, `version` 1 → 2 | `b21c102eb1e14401` |
| A10 đếm bằng số hiện tại | 409 `NO_CHANGE` | `a5b3117805f74b6c` |
| A12 `expectedVersion` cũ | 409 `CONCURRENT_MODIFICATION` | `0576522f04e242c2` |
| A14 chuyển 2 dòng | 201, 2 cặp movement | `068f975680204e9d` |
| A15 một dòng thiếu | 409, tồn nguồn **không đổi** | `b71b9e4aaf9c49d9`, `75c4115623bb4ac7` |
| A16 cùng kho / khác nhà máy / kho ngừng | 400 / 422 `RESOURCE_SCOPE_MISMATCH` / 422 `RESOURCE_INACTIVE` | `6eba1cb15b204e82`, `7ec44857688a45a7`, `6a7dbcac94134bf1` |
| A17 lô giữ mã/ngày/hạn khi chuyển | đúng, nguồn = phiếu chuyển | `f1ebd694cafc445a` |
| A18 hai dòng cùng vị thế | 400 trên `lines[1]` | `fe78564e1dff4ece` |
| A20 gửi lại cùng key | cùng `transferId`; kiểm đếm trả cùng `movementId` | §4 log, cùng lượt |
| A21 cùng key, nội dung khác | 409 `IDEMPOTENCY_CONFLICT` | `6cc1ca32a1464714` |
| A22 tài khoản không có quyền | 403 khi tạo phiếu, xem phiếu, kiểm đếm | cùng lượt |
| A23 đổi trạng thái: không đổi / quá hạn→AVAILABLE | 409 `NO_CHANGE` / 409 `LOT_NOT_ELIGIBLE` | `b33d2ee49a2a47f7`, `96f10240fbc542fb` |
| A26 quá 6 số lẻ | 400 trên `quantity` và `lines[0].quantity` | `84a23492c658442f`, `32aed8f3d62e4f79` |
| `X-Plant-Id` sai nhà máy | 409 `STATE_CONFLICT` | `454192ddb0f84e71` |

### 7.3 Chưa nghiệm thu

| Ca | Vì sao | Cần gì |
|---|---|---|
| A03, A04, A13, A14 (phần giá), A25 | Định giá chưa làm (§8) | Giai đoạn định giá |
| A11, A24 (đếm dưới giữ chỗ, đổi trạng thái lô đang giữ chỗ, WO dùng phần giữ của mình) | Lượt live không dựng Lệnh sản xuất có giữ chỗ — chỉ có test tự động | Fixture có BOM + Lệnh sản xuất đã giữ vật tư |
| A19 tranh chấp | Chỉ đo bằng integration test (5 vòng), chưa qua HTTP song song | Bắn hai request cùng lúc qua HTTP |
| A20 hai request **đồng thời** cùng key | Chưa đo. Thiết kế: một bản ghi nhờ ràng buộc duy nhất của key, request kia nhận lỗi 409 — **chưa kiểm** mã lỗi cụ thể | Bắn song song cùng key |
| A22 thu hồi quyền rồi gửi lại cùng key | Chưa đo | Tài khoản bị thu hồi giữa hai lần gọi |
| A23 lô có hàng ở kho ngoài phạm vi | Chỉ test tự động (kiểm quyền), chưa có tài khoản gán theo kho trong lượt live | Tài khoản gán một kho + lô nằm ở hai kho |
| A05 trùng mã lô khác hoa thường | Chưa làm (§8) | — |
| `GET /reservations` có dữ liệu | Live chỉ ra trang rỗng | Như A11 |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm:**

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Định giá tồn kho: `unitCost` bắt buộc khi nhập, `averageCost`, `stockValue`, `currency`, snapshot giá trên movement (`recordedUnitCost`, `movementValue`, `valueBefore/After`), giá khi đếm/chuyển | Đụng **mọi** đường ghi tồn kho (nhập mua, xuất cho sản xuất, nhập thành phẩm, QC…), cần chính sách cho dữ liệu cũ không có giá và quy tắc làm tròn — quá lớn cho một giai đoạn bàn giao màn hình | Phase định giá riêng, gộp với giá trị WIP |
| `beforeOnHandQuantity` / `afterOnHandQuantity` / `signedQuantityDelta` trên movement | Cần lưu snapshot lúc ghi; dòng cũ không có | Cùng phase định giá |
| Hết hạn chặn cả giữ chỗ / xuất cho Lệnh sản xuất / hoạch định | Đổi kết quả hoạch định đang chạy | Quyết định riêng |
| Mã lô không phân biệt hoa thường | Đổi quy tắc lô đang dùng ở nhập mua + nhập thành phẩm | Quyết định riêng |
| `affected positions` trong response đổi trạng thái | FE đọc lại `GET /lots/{id}` là đủ | Nếu FE cần tránh một lần gọi |
| Lọc `expiryState=NONE/EXPIRING`, lọc movement theo `type`/`search` | Không nằm trong danh sách tối thiểu | FE xác nhận cần |
| Chuyển khác nhà máy, in-transit, sửa/huỷ phiếu, serial | Ngoài phạm vi FE §1.2 | — |

**Việc FE cần làm tiếp:**

Bắt buộc:
- [ ] Nối adapter chuyển kho (§4.1–4.3), luôn gửi `Idempotency-Key` (UUID) và giữ nguyên key khi retry.
- [ ] Kiểm đếm: đọc `version` từ `GET /balances` (theo đúng lô), gửi `countedQuantity` + `expectedVersion`; bắt `NO_CHANGE`,
      `CONCURRENT_MODIFICATION` (tải lại), `RESERVED_STOCK`.
- [ ] Đổi trạng thái lô: bắt `NO_CHANGE`, `RESERVED_STOCK`, `LOT_NOT_ELIGIBLE`, `403`.
- [ ] Giới hạn 6 số lẻ ở mọi ô số lượng.
- [ ] Aggregate: hiển thị nhóm "Đang kiểm tra" = `qualityHoldQuantity + heldLotQuantity`.
- [ ] Ẩn/khoá mọi ô giá trong chế độ API (chưa có định giá).
- [ ] Gộp dòng trùng vật tư + lô trước khi gửi phiếu chuyển.

Nên làm:
- [ ] Hiển thị `createdByUsername` + `traceId` ở lịch sử biến động.
- [ ] Dùng `GET /reservations` cho tab "Giữ cho sản xuất".
- [ ] Chạy A01–A26 trên môi trường của FE và báo kết quả theo mã ca.

**Thứ tự deploy:** không ràng buộc.

---

**Tin nhắn kèm (Zalo):**

```
[BE→FE] Giai đoạn 4 — Tồn kho, lô, chuyển kho
Mức: P1 · Breaking: CÓ
Cần FE làm: nối chuyển kho + kiểm đếm, bắt mã lỗi mới NO_CHANGE / RESERVED_STOCK, giới hạn 6 số lẻ; định giá tồn kho CHƯA có
File: BE_RESPONSE_GD_04_2026-10-15.md
Thứ tự deploy: không ràng buộc
```

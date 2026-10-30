# BE trả lời CF-8 — giá chuẩn · BOM scale 6 · contract & nhận diện bản (kèm smoke HTTP CF-9…CF-13)

**Phiên bản:** v1 · **Ngày đo:** 29/10/2026 (UTC 15:58–16:01) · **Trả lời cho 3 file FE:**
`FE_QUESTION_STANDARD_COST_BASELINE_2026-10-28`, `FE_QUESTION_BOM_SCALE6_SCOPE_2026-10-28`,
`FE_QUESTION_GD06_GD07_CONTRACT_2026-10-28_v2`. Kèm bằng chứng HTTP cho các thay đổi CF-9…CF-13 (đợt bổ sung 28/10).

> Mọi request/response trong file này **copy từ lần gọi thật** trên bản BE ghi ở §0.1. Số được giữ nguyên chữ như server trả
> (không định dạng lại). Chỗ nào cắt bớt phần dài ghi rõ `…(cắt)…`; phần còn lại nguyên văn. Token/mật khẩu đã che.

---

## §0 — Kết luận và bảng phân loại

**Một câu:** cả 3 file QUESTION trả lời **CÓ** theo đúng chiều FE nghiêng về; 7 phép đo HTTP trên bản đang chạy đều **PASS**;
có **một lỗi do lượt đo tìm ra và đã sửa** (phiếu nhập thành phẩm không theo lô lên `version` +2 khi duyệt, §8) và **hai phép đo chưa làm** (§7.3).

### 0.1 Nhận diện bản BE (trả lời D02 / Q2 file GD06_GD07)

| Mốc | Giá trị |
|---|---|
| File chạy | `manufacturing-0.0.1-SNAPSHOT.jar`, **SHA-256 `9f46693515802501b9d09e7147c6fb2c0ffa460768a6ae571cf413a51b338a00`** |
| Commit | **`7cf3703eb99eeed9cd8296f743e33b9e4593a1d9`** |
| `/actuator/info` (cổng quản trị) | nguyên văn bên dưới — **mới từ bản này**, trước đây trả `{}` |
| Migration DB | Flyway **V95** (`add case insensitive lot code unique index`, cài 2026-10-29 22:51:37 giờ VN, `success = t`) |
| Môi trường | dev, Postgres + Redis Docker, app `http://localhost:8090/api`, quản trị `:8091` |

```
GET http://localhost:8091/actuator/info  → 200
{"app":{"commit":"7cf3703eb99eeed9cd8296f743e33b9e4593a1d9"},"build":{"artifact":"manufacturing","name":"manufacturing-erp","time":"2026-10-29T15:50:55.463Z","version":"0.0.1-SNAPSHOT","group":"com.erp"}}
```

Cách FE tự xác định đúng bản từ nay: gọi `/actuator/info` và so `app.commit` + `build.time`. `build.time` là giờ đóng gói
jar (UTC). Số migration một mình **không** đủ (CF-1/2/3 không có migration).

**4 dấu hiệu hành vi** (đo trên chính bản này, trừ hai mục ghi rõ):

| # | Dấu hiệu | Kết quả trên bản này | Trace |
|---|---|---|---|
| 1 | `adjust` lô còn chờ QC mua ⇒ 409 | ✅ `409 STATE_CONFLICT` (§4.8) | `6086666aa86042d1` |
| 2 | giữ chỗ lô hết hạn ⇒ 409 | **chưa đo trên bản này** — tạo lô hết hạn cần ghi hạn quá khứ, API cố ý không cho; có IT (`CF-2`) | — |
| 3 | đề xuất mua có hàng chờ QC ⇒ `WARNING` + `PENDING_QC_SUPPLY` | ✅ (§4.9) | `35dca09d79be4892` |
| 4 | `capacityUnits = 2`: xem trước = release | **chưa đo trên bản này** (cần lịch làm việc + tổ 2 đơn vị); có unit/IT (`CF-5`) | — |

### 0.2 Bảng phân loại các mục FE hỏi

| # | File / mục | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| 1 | STANDARD_COST Q1 / D01 / D02 — baseline vật tư theo giá chuẩn chụp lúc RELEASE | ✅ **Có, đúng như FE nghiêng** | Đổi giá 1000→1200 sau release: baseline lệnh cũ vẫn 20000 (`807e093147604927`); lệnh release sau lấy 1200 → 24000 (`8ee97cdaa2434934`); tiêu thụ 21 ⇒ thực tế vật tư **21000**, không 25200 (`96a04b10bcb440f3`) |
| 2 | STANDARD_COST Q2 — contract `standard-cost` | ✅ **Cung cấp ở §2.1** | DTO + ví dụ thật |
| 3 | STANDARD_COST Q3 — CF-6 ghi là hoãn | ✅ **Có** | CF-6 (sổ giá trị tồn/WIP) không có dòng code nào; §8 |
| 4 | STANDARD_COST D03 — `/variance` khác `/production-costs` | ✅ **Xác nhận khác công thức** | §2.3 |
| 5 | BOM_SCALE6 Q1 / D01 / D02 — form và import cùng luật | ✅ **Có** | Form 3/1 ⇒ 400 `lines[0].componentQuantity` (`a96b61ba03024f48`); import 3/1 ⇒ batch `INVALID`, `INVALID_FORMAT` cột F (`92b3fb67a93f40cf`); commit ⇒ 409 `STATE_CONFLICT` (`3f5ac215c2544621`) |
| 6 | BOM_SCALE6 D03 — 10 → 25 đọc lại 25 / 2.5 | ✅ **Có** | `664c24519bff4516`; qua import `5a7470a4831d4132` |
| 7 | BOM_SCALE6 hao hụt 5.1234% / 5.12345% | ✅ **Có** | form `0.051234` lưu đúng; `0.0512345` ⇒ 400 `lines[0].scrapRate` (`9b68cba588094d68`); import `5.12345` ⇒ `INVALID_FORMAT` cột H (`dfc6210eeeaf4a49`) |
| 8 | BOM_SCALE6 Q2 — dữ liệu/snapshot cũ không đổi | ✅ **Có** | Không migration nào đổi `quantity_per`; giới hạn chỉ áp lần ghi mới (§1) |
| 9 | BOM_SCALE6 Q3 — file numeric/BOM-ratio | ✅ **Có, đã soạn** | `BE_RESPONSE_NUMERIC_CONTRACT_2026-10-28.md`, `BE_RESPONSE_BOM_RATIO_2026-10-28.md` (§8) |
| 10 | GD06_GD07 Q1 / D01 — 11 file | ✅ **Có đủ 11 file** + 1 bản sau | §8 (danh sách tên) |
| 11 | GD06_GD07 Q2 / D02 — mốc ngoài migration | ✅ **Có** | SHA-256 jar + commit + `/actuator/info` (§0.1) |
| 12 | GD06_GD07 Q3 / D03 — tách loại bằng chứng | ✅ **Có** | §7.2 bảng IT / unit / HTTP smoke / chưa đo |

---

## §1 — Nguyên nhân / phạm vi (ngắn)

- **Giá thành GĐ11 không dùng giá bình quân.** Giá vật tư = giá chuẩn của vật tư **chụp một lần lúc phát hành lệnh** (`RELEASE`),
  khoá theo lệnh. Đổi giá chuẩn sau đó không đụng lệnh đã phát hành, không sửa lịch sử, không sửa run đã chốt. Thực tế vật tư =
  lượng **tiêu thụ** × giá đã chụp. Nhân công / chi phí chung = phút thực tế × đơn giá giờ chụp lúc phát hành.
- **Kho vẫn không mang giá trị** (CF-6 hoãn): không có số dư giá trị tồn, không có giá mua thực tế, không có bình quân.
- **BOM theo lô:** định mức lưu dưới dạng "per-unit" tối đa 6 số lẻ. Thương `lượng thành phần / lượng đầu ra` không biểu diễn được
  trong 6 số lẻ (ví dụ 1/3) bị **từ chối**, không làm tròn. Áp cho mọi **lần ghi mới** (form và file nhập); BOM và lệnh đã có không
  bị đọc lại, không bị sửa.
- **CF-9 / CF-10 / CF-11** là thay đổi hành vi thật (§3); **CF-12 / CF-13** chỉ thêm test, không đổi code.

---

## §2 — Contract

### 2.1 `standard-cost` (Q2 file STANDARD_COST)

| | |
|---|---|
| Ghi | `PUT /api/v1/companies/{companyId}/items/{itemId}/standard-cost` — **upsert**, không có `expectedVersion`, không `Idempotency-Key` (gửi lại cùng body = ghi lại cùng giá trị, `version` +1) |
| Đọc | `GET /api/v1/companies/{companyId}/items/{itemId}/standard-cost` · danh sách `GET /api/v1/companies/{companyId}/items/standard-costs` |
| Quyền | Ghi: `PERM_COSTING_MANAGE`; đọc: `PERM_COSTING_READ`. Phạm vi **công ty** của vật tư. Role cấp sẵn: **ADMIN, MANAGER** (OPERATOR không có) |
| Tiền tệ | Không có field tiền tệ trên giá chuẩn; giá thành GĐ11 ghi `currency: "VND"` |

Request body:

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `materialCost` | number | có | ≥ 0, tối đa 13 chữ số nguyên + 6 số lẻ |
| `laborCost` | number | có | như trên |
| `overheadCost` | number | có | như trên |

Response `result`:

| Field | Kiểu | Nullable | Nguồn giá trị |
|---|---|---|---|
| `itemStandardCostId` | UUID | không | id dòng giá chuẩn |
| `companyId`, `itemId`, `itemCode` | UUID / UUID / string | không | vật tư |
| `materialCost`, `laborCost`, `overheadCost` | number | không | như đã lưu (response ghi lặp lại cách viết của request; GET trả 6 số lẻ) |
| `totalStandardCost` | number | không | tính **lúc đọc** = giá của chính vật tư + cộng dồn BOM con (nếu có BOM) |
| `version` | number | không | 0 sau lần ghi đầu, +1 mỗi lần ghi |
| `createdAt`, `updatedAt` | ISO UTC | không | xem lưu ý µs ở §6 |

Ví dụ thật — ghi (trace `07a744ab96a3420b`):

```
PUT /api/v1/companies/c5906fea-baca-4731-ba8d-291c818a37ae/items/b430b791-02aa-4a16-a8f9-6f71e6317eb4/standard-cost
{"materialCost":1000,"laborCost":0,"overheadCost":0}
→ 200
{"code":"SUCCESS","result":{"itemStandardCostId":"d9ae64ae-a2eb-4e56-9df4-c9d21352adcf","companyId":"c5906fea-baca-4731-ba8d-291c818a37ae","itemId":"b430b791-02aa-4a16-a8f9-6f71e6317eb4","itemCode":"RM-225804","materialCost":1000,"laborCost":0,"overheadCost":0,"totalStandardCost":1000,"version":0,"createdAt":"2026-10-29T15:58:06.867401100Z","updatedAt":"2026-10-29T15:58:06.867401100Z"},"message":"OK"}
```

Đọc lại (trace `22a55fe50c744b1d`):

```
GET /api/v1/companies/c5906fea-baca-4731-ba8d-291c818a37ae/items/b430b791-02aa-4a16-a8f9-6f71e6317eb4/standard-cost
→ 200
{"code":"SUCCESS","result":{"itemStandardCostId":"d9ae64ae-a2eb-4e56-9df4-c9d21352adcf","companyId":"c5906fea-baca-4731-ba8d-291c818a37ae","itemId":"b430b791-02aa-4a16-a8f9-6f71e6317eb4","itemCode":"RM-225804","materialCost":1000.000000,"laborCost":0.000000,"overheadCost":0.000000,"totalStandardCost":1000.000000,"version":0,"createdAt":"2026-10-29T15:58:06.867401Z","updatedAt":"2026-10-29T15:58:06.867401Z"},"message":"OK"}
```

Nhánh lỗi (CF-10, trace `2d48a6149dfa4f74`):

```
PUT …/items/939a9ddc-9327-4396-8a63-d3d9c9bc4826/standard-cost
{"materialCost":0,"laborCost":1.1234567,"overheadCost":0}
→ 400
{"code":"VALIDATION_ERROR","message":"laborCost: numeric value out of bounds (<13 digits>.<6 digits> expected)","errors":[{"field":"laborCost","message":"numeric value out of bounds (<13 digits>.<6 digits> expected)"}]}
```

Chưa có giá chuẩn ⇒ `GET` trả **404 `ENTITY_NOT_FOUND`** (trace `7bf7085b338b4702`). Vật tư chưa có giá lúc phát hành ⇒ baseline ghi
dòng đó là *unknown*, không tự dùng 0; chốt giá thành bị chặn `409 COST_DATA_INCOMPLETE` (không đo HTTP lần này, xem §7.3).

### 2.2 Baseline giá thành (D01/D02)

`GET /api/v1/work-orders/{id}/cost-baseline` — `result.baseline.materials[].unitCost` là giá chụp lúc release;
`planned = {material, labor, overhead, total}`. Ví dụ thật ở §4.2.

### 2.3 `/variance` khác `/production-costs` (D03)

| | `GET /api/v1/work-orders/{id}/variance` | `GET /api/v1/work-orders/{id}/production-costs` |
|---|---|---|
| Nguồn giá | giá chuẩn **hiện tại** (đọc sống) | giá chuẩn + đơn giá giờ **chụp lúc RELEASE** |
| Nhân công / chi phí chung | `laborCost`/`overheadCost` của giá chuẩn thành phẩm × số good | phút thực tế × đơn giá giờ của tổ sản xuất |
| Thực tế vật tư | giá trị **xuất** | lượng **tiêu thụ** (báo sản lượng) × giá đã chụp |
| Quyền | `PERM_WORK_ORDER_VARIANCE_READ` | quyền xem giá thành (field tiền vắng mặt khi thiếu quyền) |

Hai con số **không** được kỳ vọng bằng nhau; nhãn FE "Chi phí vật tư theo giá chuẩn" nên đi với `/production-costs`.

### 2.4 BOM nháp (form)

`PUT /api/v1/companies/{companyId}/bom-drafts`, field liên quan: `outputQuantity` (> 0, 13.6), `lines[].componentQuantity` (> 0, 13.6),
`lines[].scrapRate` — **tỉ lệ** `0 ≤ x < 1`, tối đa **6 số lẻ** (5.1234% gửi `0.051234`). File nhập (`kind=bom`, mẫu `omniplant-bom-v1`)
dùng cột **"Scrap (%)"** — **phần trăm**, tối đa 4 số lẻ.

---

## §3 — Breaking change

**Wire: CÓ** (hẹp, cả ba đã có ở bản đang chạy).

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| Mọi request có số thập phân chưa có giới hạn trước đây (CF-10, 20 field / 14 request: giá chuẩn, thiết lập kho, nhu cầu thủ công, NCC–vật tư, đề nghị mua + duyệt, đường tạo cũ PO/SO/lệnh SX, phút quy trình, cấp vật tư phẳng, WIP, xuất/hoàn thành cũ) | gửi > 6 số lẻ hoặc > 13 chữ số nguyên | `200` (DB làm tròn, response lặp lại số chưa làm tròn) / `422 BUSINESS_RULE_VIOLATION` không `errors[]` | **`400 VALIDATION_ERROR`** + `errors[{field}]` | Đổi mã | Giữ nguyên số người nhập, hiện lỗi theo field; số dạng chuỗi `"1.1234567"` cũng 400 (đo ở §4.5). `1.1234560` (số 0 thừa) cũng bị đếm là 7 số lẻ |
| `POST /api/v1/goods-receipts/{id}/post` và mọi đường tạo lô (CF-9) | hai lô cùng vật tư chỉ khác hoa thường, ghi đồng thời | cả hai thành công ⇒ hai lô `LOT-X`/`lot-x` | một `200`, một **`409 LOT_CODE_ALREADY_EXISTS`** | Đổi mã (nhánh đua) | Bắt theo `code`. Kẻ thua đua trả **không có `errors[]`** (message chung); nhánh kiểm tuần tự trả có `errors[{field:"lines[0].lotCode"}]` — đừng dựa vào `errors[]` |
| `POST /api/v1/plants/{plantId}/work-orders` (CF-11) | response tạo lệnh | `requiredQuantity` có thể in 12 số lẻ (`20.000000000000`) | đúng 6 số lẻ, bằng số đọc lại | Đổi hình dạng số | Không phải sửa nếu FE đã parse số |

Không đổi giá trị mặc định query param, không đổi thứ tự sắp xếp, không bỏ field nào. Thứ tự deploy: không ràng buộc.

---

## §4 — Bằng chứng HTTP (copy từ lần gọi thật)

Fixture mới: công ty **`CF8-225804`** (`c5906fea-baca-4731-ba8d-291c818a37ae`), nhà máy `PL-225804`
(`4ce5d6bc-23fe-4f53-aeab-07ce0a101baf`, `Asia/Ho_Chi_Minh`), kho `WH-225804` (`812b87bf-3cf2-45f7-afb5-cae07ff3eeae`), tổ sản xuất
`WC-225804` (1 đơn vị, không gắn lịch) với đơn giá giờ nhân công 6000 + chi phí chung 3000 (`rateVersion` 1). Quy trình FG: 1 công
đoạn, chuẩn bị 0 + 6 phút/đơn vị ⇒ 60 phút cho 10.

### 4.1 Nhận diện bản — xem §0.1

### 4.2 CF-8.1 giá chuẩn chụp lúc RELEASE

1. RM giá 1000 (§2.1). FG1 BOM 2.0 RM/đơn vị, hao hụt 0. Lệnh `WO-A-225804` kế hoạch 10 ⇒ `requiredQuantity` **20** (trace `e6f0ed65e64d45cd`).
2. Giữ chỗ ⇒ `workOrderVersion` 0 → **1**; release với `expectedWorkOrderVersion: 1` ⇒ `RELEASED`, `releasedAt` `2026-10-29T15:58:07.239597600Z` (trace `78685bdaacac4dcc`).
3. Baseline **sau khi đổi giá master lên 1200** (trace `807e093147604927`) — vẫn 1000 / 20000:

```
GET /api/v1/work-orders/7d1cad60-92e0-4dd3-b919-3b14d0bd1f93/cost-baseline → 200
{"code":"SUCCESS","result":{"workOrderId":"7d1cad60-92e0-4dd3-b919-3b14d0bd1f93","baseline":{"baselineId":"1c26862c-0b11-4e52-bdc3-597557ffada6","capturedAt":"2026-10-29T15:58:07.239597Z","capturedBy":{"id":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","displayName":"admin"},"outputWarehouseId":"812b87bf-3cf2-45f7-afb5-cae07ff3eeae","outputOpeningQuantity":0.000000,"plannedQuantity":10.000000,"currency":"VND","planned":{"material":20000.000000,"labor":6000.000000,"overhead":3000.000000,"total":29000.000000},"materials":[{"componentLineId":"6110927c-753b-40fd-b978-7266d3b5739c","lineNo":1,"itemId":"b430b791-02aa-4a16-a8f9-6f71e6317eb4","warehouseId":null,"uom":"KG","plannedQuantity":20.000000,"unitCost":1000.000000000000,"plannedAmount":20000.000000}],"operations":[{"operationId":"3a890369-914d-4d2b-a725-1153ba31181d","sequence":10,"name":"Assemble","workCenterId":"574c8c75-fc9a-4ba2-bb3d-2459c23be4ab","capacityUnits":1,"plannedMinutes":60.000000000000,"rateVersion":1,"laborRate":6000.000000,"overheadRate":3000.000000,"plannedLabor":6000.000000,"plannedOverhead":3000.000000}]},"unknownReasons":[]},"message":"OK"}
```

Lệnh đổi giá (trace `46a17ac1b26d4673`): `{"materialCost":1200,…}` ⇒ `version` 0 → **1**.

4. Lệnh `WO-B-225804` release **sau** khi đổi giá ⇒ `unitCost 1200.000000000000`, `planned.material 24000.000000`, `total 33000.000000` (trace `8ee97cdaa2434934`).
5. Tiêu thụ 21 trên WO-A: phiếu cấp 21 (vượt định mức, `reasonCode: PROCESS_LOSS` + `overrideReason`) ⇒ `PENDING_APPROVAL` (trace `068060c031aa40b9`) ⇒ duyệt ⇒ `POSTED` (trace `a44431d63a554f0d`); báo sản lượng good 10, 60 phút, `consumption` 21 (trace `ecc7713000d64efd`). Đọc giá thành (trace `96a04b10bcb440f3`):

```
GET /api/v1/work-orders/7d1cad60-92e0-4dd3-b919-3b14d0bd1f93/production-costs → 200
{"code":"SUCCESS","result":{…(cắt phần định danh)…"status":"COMPLETED","workOrderVersion":6,…,"readiness":"BLOCKED",…,"data":{…,"currency":"VND","planned":{"material":20000.000000,"labor":6000.000000,"overhead":3000.000000,"total":29000.000000},"actual":{"material":21000.000000,"labor":6000.000000,"overhead":3000.000000,"total":30000.000000},"variance":1000.000000,"variancePercent":3.448276,"unitCost":3000.000000,"loss":null,"remainingValue":0.000000,"issuedValue":0.000000,"unknownReasons":[]},"closed":null},"message":"OK"}
```

⇒ thực tế vật tư **21000 = 21 × 1000** (giá chụp), không phải 25200. `readiness: BLOCKED` vì lệnh này chưa nhập kho / QC — đúng.

### 4.3 CF-8.2 BOM scale 6 — form

Ghi 10 → 25, hao hụt 0.051234 (trace `6797147bfc594b36`) ⇒ 200; đọc lại (trace `664c24519bff4516`):

```
PUT /api/v1/companies/c5906fea-baca-4731-ba8d-291c818a37ae/bom-drafts
{"bomCode":"BOM-R1-225804","parentItemId":"218a74c3-1fc3-4cb8-8f86-2ade0f5ce6f3","revision":"A","outputQuantity":10,"lines":[{"lineNo":1,"componentItemId":"b430b791-02aa-4a16-a8f9-6f71e6317eb4","componentQuantity":25,"scrapRate":0.051234}]}
→ 200

GET /api/v1/boms/35b1fca4-0574-48c5-beb7-075479c2c528 → 200
{"code":"SUCCESS","result":{…,"lines":[{"lineId":"1aa4b439-d946-4d3c-a32d-84e6671b90da",…,"componentItemCode":"RM-225804","componentItemName":"Raw A","quantityPer":2.500000,"scrapRate":0.051234,"componentQuantity":25.000000,"componentUomCode":"KG"}],"bomCode":"BOM-R1-225804","versionNumber":1,"outputQuantity":10.000000,"outputUomCode":"PCS","effectiveFrom":null,"version":0},"message":"OK"}
```

Hao hụt 7 số lẻ (trace `9b68cba588094d68`) và tỉ lệ 1/3 (trace `a96b61ba03024f48`):

```
PUT …/bom-drafts  {…"outputQuantity":10,"lines":[{…"componentQuantity":25,"scrapRate":0.0512345}]}
→ 400 {"code":"VALIDATION_ERROR","message":"lines[0].scrapRate: numeric value out of bounds (<1 digits>.<6 digits> expected)","errors":[{"field":"lines[0].scrapRate","message":"numeric value out of bounds (<1 digits>.<6 digits> expected)"}]}

PUT …/bom-drafts  {…"outputQuantity":3,"lines":[{…"componentQuantity":1,"scrapRate":0}]}
→ 400 {"code":"VALIDATION_ERROR","message":"lines[0].componentQuantity: divided by outputQuantity needs more than 6 decimals; adjust either quantity","errors":[{"field":"lines[0].componentQuantity","message":"divided by outputQuantity needs more than 6 decimals; adjust either quantity"}]}
```

Danh sách BOM sau hai lần từ chối (trace `155d53b84cf84344`): không có `BOM-R2-225804` / `BOM-R3-225804` — không ghi gì.

### 4.4 CF-8.2 BOM scale 6 — file nhập (`kind=bom`)

Mẫu `omniplant-bom-v1`, `_meta` `version`/`locale=en`, sheet `BOM`, header tiếng Anh. Request: `POST /api/v1/data-import/batches?kind=bom&companyId=c5906fea-baca-4731-ba8d-291c818a37ae` (multipart `file`).

- 10 / 25 / 5.1234% ⇒ **201 `READY`** (trace `18eca2781a5e44ef`); commit `{"expectedVersion":0}` ⇒ `COMMITTED`, `createdIds` 1 BOM (trace `9af00b177d064972`); đọc BOM đó: `"quantityPer":2.500000,"scrapRate":0.051234,"componentQuantity":25.000000` (trace `5a7470a4831d4132`).
- 5.12345% (sản phẩm chưa có BOM), trace `dfc6210eeeaf4a49`:

```
→ 201 {"code":"SUCCESS","result":{"id":"bd43e128-7dd2-46a7-89da-a2816cd97bee",…,"status":"INVALID","version":0,…,"rowCount":1,"validRowCount":0,"errorRowCount":1,"createdCount":0,"rows":[{"line":2,"values":{"bomCode":"BOM-I2B-225804","productCode":"FGB2-225804","outputQuantity":"10","outputUom":"PCS","componentCode":"RM-225804","componentQuantity":"25","componentUom":"KG","scrapPercent":"5.12345","notes":""},"issues":[{"field":"scrapPercent","column":"H","code":"INVALID_FORMAT","message":"Scrap allows at most 4 decimals of a percent"}]}],"createdIds":[],"bomCount":1,"salesOrderCount":null},"message":"Created successfully"}
```

- 3 / 1 (trace `92b3fb67a93f40cf`): `"issues":[{"field":"componentQuantity","column":"F","code":"INVALID_FORMAT","message":"Component quantity divided by output quantity needs more than 6 decimals"}]`; commit batch đó:

```
POST /api/v1/data-import/batches/b7aee5e8-0607-4ce7-a9bf-88021a8552ee/commit  {"expectedVersion":0}
→ 409 {"code":"STATE_CONFLICT","message":"Import batch in status INVALID cannot be committed; fix the file and upload it again"}
```

Danh sách BOM sau đó (trace `836849493fe649b3`): không có BOM nào từ hai batch lỗi.

### 4.5 CF-10 số thập phân

| Request (`PUT …/items/939a9ddc-…/standard-cost`) | Kết quả | Trace |
|---|---|---|
| `"laborCost":1.1234567` | 400 `VALIDATION_ERROR` field `laborCost` | `2d48a6149dfa4f74` |
| `"laborCost":"1.1234567"` (chuỗi) | 400 `VALIDATION_ERROR` field `laborCost` (cùng message) | `c1ec48cb17fb4fcb` |
| GET sau hai lần trên | 404 `ENTITY_NOT_FOUND` — không ghi gì | `7bf7085b338b4702` |
| `"laborCost":1.123456` | 200, `"laborCost":1.123456` | `eb282af252104cbb` |
| GET đọc lại | `"laborCost":1.123456` | `c819b8353c5d4900` |

### 4.6 CF-9 mã lô khác hoa thường, ghi đồng thời

Vật tư theo lô `LRM-225804`, hai PO xác nhận (`PO-CF9-1-225804`, `PO-CF9-2-225804`), hai phiếu nhận nháp `GR-000021` (lô `LOT-X225804`) và
`GR-000022` (lô `lot-x225804`). Gửi hai lệnh post **cùng lúc** (`{"expectedVersion":0}`):

```
POST /api/v1/goods-receipts/e3820849-c994-4ba7-94f7-251fa8f3024f/post  → 200   (trace b7f34d22c96a4623) — "status":"POSTED","version":1, lô "lot-x225804" "lotStatus":"HOLD"
POST /api/v1/goods-receipts/f0a57788-64ca-4e47-9648-ec7928330ee9/post  → 409   (trace 475d0b08d8c84c67)
{"code":"LOT_CODE_ALREADY_EXISTS","message":"Lot code already exists for this item"}
```

- Phiếu thua đọc lại (trace `aebc43b595684b0b`): `"status":"DRAFT"`, `"version":0`, `lotId: null`, PO `PO-CF9-1` vẫn `CONFIRMED` v1. PO thắng `FULLY_RECEIVED` v2.
- Post lại phiếu thua tuần tự (trace `731a707731944143`):
  `409 {"code":"LOT_CODE_ALREADY_EXISTS","message":"lines[0].lotCode: already exists for this item","errors":[{"field":"lines[0].lotCode","message":"already exists for this item"}]}`
- Phiếu thứ ba mã `Lot-X225804` bị chặn ngay **khi lưu nháp** (trace `93424390aea740c7`, cùng mã + field).
- Lô của vật tư (trace `c88e5de5bd0646f1`): **đúng 1 lô** `lot-x225804`, `HOLD`, tồn 5.

### 4.7 CF-11 làm tròn `requiredQuantity`

FG2 BOM `quantityPer 1.000001`, hao hụt 0; tạo lệnh kế hoạch `1.2` (trace `92ce19191b5e454e`) — response tạo:

```
"componentLines":[{"componentLineId":"087f2b87-610e-4e4f-888b-ef5cf88e49f1",…,"uom":"KG","quantityPer":1.000001,"scrapRate":0.000000,"requiredQuantity":1.200001,"reservedQuantity":0,"issuedQuantity":0,"remainingQuantity":1.200001,"warehouseId":null,"warehouseCode":null}]
```

GET đọc lại (trace `10924833ae3f4b43`): `"requiredQuantity":1.200001`. (1.2 × 1.000001 = 1.2000012 ⇒ HALF_UP 6 số lẻ.)

### 4.8 CF-13 smoke chốt giá thành (fixture FE: 10 PCS, 20 KG × 1000, 60 phút, 6000 + 3000 /giờ)

| Bước | Kết quả | `workOrderVersion` | Trace |
|---|---|---|---|
| Giá chuẩn RM2 1000; tạo `WO-C-225804` 10 | 201 | 0 | `a517defa9048477e` |
| Giữ chỗ 20, release | RELEASED | 1 → 2 | `1d4672442b4c442a`, `f4e507eb2cd94f74` |
| Baseline | `planned.total 29000.000000` (vật tư 20000, nhân công 6000, chung 3000) | — | `b325ddbe682d4bc8` |
| Cấp 20 (theo giữ chỗ) | POSTED | 2 → 3 | `31c356d173f84965` |
| Đầu vào công đoạn 10 | | 3 → 4 | `c91cfe29a6ae4882` |
| Báo good 10, 60 phút, tiêu thụ 20 | `durationMinutes 60`, WO `COMPLETED` | 4 → 5 | `21a211cfb3954173` |
| Kết thúc công đoạn | | 5 → 6 | `5850bc5da1324e8a` |
| Phiếu nhập 10 → gửi → duyệt | `APPROVED` | 6 → 7 → 8 → 9 | `8ab1157417c140ed`, `1da8433b71da43d2`, `e9fb264630364bec` |
| QC đạt 10 | `qualityState ACCEPTED` | 9 → 10 | `ba5229bf3bb140fa` |
| Checklist | `BLOCKED`, chỉ `PREVIEW: NO_PREVIEW` | 10 | `c52d0056aecd4a04` |
| Tính thử (run) | `runVersion 1` — **không** đổi version lệnh | 10 | `7444e920aee347ad` |
| Giá thành sau tính thử | `READY`, thực tế **29000**, `unitCost` **2900** | 10 | `a4a1a59b815247fc` |
| Chốt với `expectedVersion` sai | **409 `CONCURRENT_MODIFICATION`** field `expectedVersion` | 10 | `c2a5b70835394927` |
| Chốt đúng | 200, `runVersion 2` | 10 → **11** | `b8a9cc4489364a8c` |
| Đọc lệnh / run | `CLOSED` / run `CLOSED` | 11 | `1a9a93b89b3b49de`, `71278ec4a3654ac0` |
| Bút toán | 1 dòng `COST_ADJUSTMENT`, `quantity 0.000000`, `stockValueDelta 29000.000000` | — | `2a274cd44cc94264` |
| Tồn FG3 | `quantity 10.000000`, `availableQuantity 10.000000` | — | `07958a1862364d96` |
| Gửi lại lệnh chốt cùng key | 200, cùng `runVersion 2` / `workOrderVersion 11` (replay) | 11 | `41af9f2c5e3845e2` |

```
POST /api/v1/work-orders/571284f3-c65c-452e-97d3-0c05d29c7f79/finalize-cost-and-close
Idempotency-Key: CF13-FIN-BAD-225804
{"expectedVersion":17,"expectedSourceVersion":"cs1-aa8e9fe495ab0dc96230f4555041836a","runId":"9cdc1b79-77cf-49c8-8d2c-610129047cd7","expectedRunVersion":1,"reason":"CF-13 smoke close"}
→ 409 {"code":"CONCURRENT_MODIFICATION","message":"expectedVersion: work order version is 10, not 17","errors":[{"field":"expectedVersion","message":"work order version is 10, not 17"}]}

(cùng body, "expectedVersion":10, Idempotency-Key: CF13-FIN-225804)
→ 200 {"code":"SUCCESS","result":{"workOrderId":"571284f3-c65c-452e-97d3-0c05d29c7f79","runId":"9cdc1b79-77cf-49c8-8d2c-610129047cd7","runVersion":2,"workOrderVersion":11},"message":"OK"}
```

### 4.9 Dấu hiệu CF-1 và CF-4 (cùng fixture)

```
POST /api/v1/inventory/adjust  Idempotency-Key: CF8-MARK-ADJ-225804
{"itemId":"25fbc5e7-479f-42f3-a545-e51a4dada870","warehouseId":"812b87bf-3cf2-45f7-afb5-cae07ff3eeae","lotId":"5ca493ca-9e5f-483b-92dd-aaf706975814","quantityDelta":-1,"reason":"CF-8 marker: adjust a lot purchase QC still owns"}
→ 409 {"code":"STATE_CONFLICT","message":"Lot still has quantity awaiting purchase QC; it cannot be adjusted outside the goods receipt"}
```

Nhu cầu thủ công 50 KG vật tư `LRM-225804` + run MRP (trace `7a06616a4cf347f1`): dòng nhu cầu `openSupplyQuantity 10` (hai PO còn mở),
`netRequiredQuantity 40`, `pendingQc` 5 / 1 nguồn (không trừ vào net); đề xuất (trace `35dca09d79be4892`):
`"supplyType":"BUY","suggestedQuantity":40.000000,…,"exceptionState":"WARNING","messages":["MATERIAL_SHORTAGE","SYSTEM_FALLBACK_USED","PURCHASING_DEFERRED","PENDING_QC_SUPPLY"]`.
(`SYSTEM_FALLBACK_USED` có vì fixture không khai thiết lập kho — không liên quan CF-4.)

---

## §5 — Envelope và hợp đồng lỗi

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "quantity", "message": "must be greater than 0" } ] }
```

- `code` là tên hằng, FE rẽ nhánh theo nó; `errors` chỉ có khi lỗi theo field; danh sách phân trang
  `{content, page, size, totalElements, totalPages, first, last}`.
- `409 IMPORT_REVALIDATION_FAILED` của commit batch là ngoại lệ duy nhất có `result` khi lỗi (batch vừa thành `INVALID`).

| Tình huống | HTTP | `code` | `errors[]`? |
|---|---|---|---|
| Số > 6 số lẻ / > 13 chữ số nguyên (mọi request) | 400 | `VALIDATION_ERROR` | có, field |
| BOM form: tỉ lệ không vừa 6 số lẻ | 400 | `VALIDATION_ERROR` | có, `lines[i].componentQuantity` |
| BOM file: dòng sai | 201 | `SUCCESS`, batch `INVALID`, issue `INVALID_FORMAT` cột F/H | (trong `rows[].issues`) |
| Commit batch `INVALID` | 409 | `STATE_CONFLICT` | không |
| Mã lô trùng (kể cả khác hoa thường) | 409 | `LOT_CODE_ALREADY_EXISTS` | có khi kiểm tuần tự, **không** khi thua đua |
| Chốt: version lệnh lệch | 409 | `CONCURRENT_MODIFICATION` | có, `expectedVersion` |
| Chốt: nguồn đổi sau tính thử | 409 | `COST_SOURCE_STALE` | — |
| Chốt: thiếu giá / dữ liệu | 409 | `COST_DATA_INCOMPLETE` | — |
| Đóng lệnh có baseline bằng `close` cũ | 409 | `CLOSE_BLOCKED` | — |
| Không quyền | 403 | `PERMISSION_DENIED` | — |
| Chưa có giá chuẩn khi đọc | 404 | `ENTITY_NOT_FOUND` | — |

---

## §6 — Quyền, phạm vi, giới hạn đã biết

- Giá chuẩn: `PERM_COSTING_READ` / `PERM_COSTING_MANAGE` (ADMIN, MANAGER), phạm vi công ty.
- Đơn giá giờ: `PERM_COST_RATE_MANAGE`; tính thử: `PERM_PRODUCTION_COST_CALCULATE`; chốt + đóng: `PERM_WORK_ORDER_CLOSE` (ADMIN, MANAGER), phạm vi nhà máy. Field tiền **vắng mặt** (không `null`) với người thiếu quyền xem giá.
- BOM: `PERM_BOM_READ` + `PERM_BOM_MANAGE` (form và file), phạm vi công ty.
- **Giới hạn đã biết (FE đã nói không mở rộng — BE ghi lại, không làm):**
  - Hàng **không theo lô** đã giao sau khi đóng lệnh: giá vốn (COGS) **chưa biết**.
  - Đóng lệnh **không có quy trình**: không hỗ trợ trên đường chốt giá thành.
  - `/variance` khác công thức `/production-costs` (§2.3).
  - **CF-6** (sổ giá trị tồn/WIP, bình quân, giá mua thực tế) **hoãn** — không coi kết quả GĐ11 là đạt CF-6.
  - MRP lập kế hoạch dư tối đa 1e-6 so với lệnh SX (MRP làm tròn lên, lệnh SX HALF_UP) — có chủ đích.
- **Thời điểm trên response vừa ghi:** `createdAt`/`updatedAt` (và `releasedAt`, `bomCapturedAt`, `postedAt` của phiếu cấp vật tư vừa duyệt) có thể in 7 chữ số lẻ giây (`…867401100Z`) trong response ghi, đọc lại in 6 (`…867401Z`). Cùng một thời điểm; FE so sánh thời gian thì cắt về micro giây. Đây là giới hạn có từ trước, không phải của bản này.

---

## §7 — Bằng chứng nghiệm thu

### 7.1 Môi trường và test tự động

- HTTP smoke: bản §0.1, DB dev Postgres thật (không phải container test), 29/10/2026 15:58–16:01 UTC, fixture mới `CF8-225804`.
- Test tự động của bản này (đo ở lượt build CF-12/13): `mvn -o clean verify` — **2317 unit + 345 IT, failures = 0, errors = 0**.

### 7.2 Loại bằng chứng theo hành vi (trả lời D03 / Q3 file GD06_GD07)

| Hành vi | unit | IT (Postgres thật) | HTTP smoke lượt này |
|---|---|---|---|
| CF-1 `adjust` lô QC còn sở hữu ⇒ 409 | ✅ | ✅ | ✅ §4.9 |
| CF-2 lô hết hạn khỏi MRP / giữ chỗ / cấp | ✅ | ✅ | ❌ chưa đo |
| CF-3 aggregate tách chờ / loại QC mua | ✅ | ✅ | ❌ chưa đo |
| CF-4 chờ QC cạnh nhu cầu, `WARNING` | ✅ | ✅ | ✅ §4.9 |
| CF-5 một công thức lịch | ✅ | ✅ | ❌ chưa đo |
| CF-8.1 baseline giá chụp lúc RELEASE, thực tế theo tiêu thụ | ✅ (calculator) | ✅ E2E (đổi đơn giá giờ) | ✅ §4.2 (đổi **giá chuẩn vật tư**) |
| CF-8.2 BOM scale 6 form + file | ✅ | ✅ | ✅ §4.3–4.4 |
| CF-9 mã lô khác hoa thường | — | ✅ (đua thật) | ✅ §4.6 |
| CF-10 `@Digits` mọi request | ✅ (guard quét classpath + bảng) | — | ✅ §4.5 (một endpoint đại diện) |
| CF-11 `requiredQuantity` HALF_UP 6 | ✅ | ❌ (IT chuỗi BOM→MRP→WO→baseline chưa viết) | ✅ §4.7 |
| CF-12 QC ↔ count/adjust (ma trận §7.4) | ✅ | ✅ (`PurchaseQcCountRaceIT` 5 ca) | ❌ chưa đo |
| CF-13 A39 thu hồi quyền khi chờ khoá | — | ✅ | ❌ (không tái hiện được bằng HTTP tuần tự) |
| CF-13 A40 lỗi giữa chừng rollback | — | ✅ | ❌ (cần tiêm lỗi, chỉ làm được trong test) |
| CF-13 chốt end-to-end, version sai, replay | ✅ | ✅ | ✅ §4.8 |

### 7.3 Chưa nghiệm thu

| Mục | Vì sao | Cần gì |
|---|---|---|
| Dấu hiệu CF-2 (giữ chỗ lô hết hạn) qua HTTP | API không cho ghi hạn quá khứ; phải sửa DB | Chạy kịch bản có SQL đặt hạn, ngoài phạm vi smoke |
| Dấu hiệu CF-5 (`capacityUnits=2`) qua HTTP | Cần lịch làm việc + tổ 2 đơn vị, chưa dựng | Một lượt smoke riêng |
| `COST_SOURCE_STALE`, `COST_DATA_INCOMPLETE`, thiếu quyền qua HTTP | Không dựng trong lượt này | Có IT/E2E (`ProductionFlowE2EIT`) |
| OpenAPI xuất lại sau CF-9/CF-10 | `BE_OPENAPI_2026-10-28.json` (SHA-256 `220ee57728813005b48496f04cd1f358c12cc416a4defeb90a1d37c6198e95aa`) **chưa** có ràng buộc `@Digits` mới | Xuất một lần sau CF-13 (dự kiến) |
| Nghiệm thu phía FE | BE nói "xong" ≠ FE nói "đạt" | FE chạy trên bản §0.1 |

### 7.4 Ma trận phủ bảo vệ QC mua (CF-12)

| Thao tác | Hàng theo lô | Hàng không theo lô |
|---|---|---|
| adjust | ✅ `PurchaseQualityIT.adjust_lotsPurchaseQcStillOwns_areRefused_acceptedChildAdjusts`, `LotQcOwnershipGuardTest` | ✅ `PurchaseQualityIT.nonTrackedQualityHold_countAndAdjustNeverEatIntoIt` |
| count | ✅ `PurchaseQualityIT.trackedCount_lotsPurchaseQcStillOwns_areLotNotEligible_acceptedChildCounts` | ✅ `nonTrackedQualityHold_*` + `PurchaseQcCountRaceIT` (5 ca đua) |
| transfer | 🟡 một phần: `StockTransferServiceTest.create_lotOnHold_isRejected` | ❌ thiếu |
| reserve | ❌ thiếu cho `HOLD`/`REJECTED` (chỉ có hết hạn) | ❌ thiếu |
| issue | ✅ `InventoryMovementServiceTest.issue_holdLot_failsBeforeStockMutation` | ❌ thiếu |
| đổi trạng thái lô | ✅ `InventoryLotServiceTest.changeStatus_holdEscapeBlockedWhilePurchaseQcPending` / `changeStatus_purchaseQcRejectedChild_cannotBeReleased` | — |
| huỷ phiếu nhận | ✅ `GoodsReceiptServiceTest.cancelPosted_withQualityDecisions_isStateConflict`, `cancelPostedOnHold_releasesTheHoldBeforeReversing` | (cùng) |

Ô "thiếu" là **thiếu test**, không phải đã thấy lỗ hổng: tồn khả dụng của hàng không lô đã trừ phần chờ QC, và lô `HOLD`/`REJECTED`
không xuất/giữ được theo luật trạng thái lô; chưa có test đặt tên cho từng ô đó.

---

## §8 — Tóm tắt CF-9…CF-13, chưa làm có chủ đích, việc FE

### 8.1 Thay đổi CF-9…CF-13 (theo loại bằng chứng)

| Phase | Thay đổi | Bằng chứng |
|---|---|---|
| CF-9 | Mã lô không trùng khác hoa thường trong cùng vật tư (index DB, V95); kẻ thua đua ⇒ `409 LOT_CODE_ALREADY_EXISTS`, phiếu vẫn `DRAFT` | IT + HTTP (§4.6). DB dev trước migration: 0 cặp trùng |
| CF-10 | `@Digits(13,6)` trên 20 field / 14 request còn thiếu; guard tự động chặn DTO mới quên | unit (guard + bảng) + HTTP một endpoint (§4.5). Nhập Excel không đổi (đã chặn từ trước) |
| CF-11 | `requiredQuantity` lệnh SX HALF_UP 6 tường minh; công thức hao hụt `× (1 + rate)` không đổi ở mọi đường | unit + HTTP (§4.7); IT chuỗi chưa viết |
| CF-12 | Chỉ test (QC ↔ kiểm đếm/điều chỉnh), không đổi code | IT (§7.4) |
| CF-13 | Chỉ test (A39, A40), không đổi code | IT + HTTP smoke đường chính (§4.8) |
| CF-8.5 | `/actuator/info` có commit + build | HTTP (§0.1) |

### 8.2 File đã soạn (trả lời D01 / Q1 file GD06_GD07)

1. `BE_RESPONSE_GD_06_2026-10-17.md` 2. `BE_RESPONSE_GD_06_2026-10-17_v2.md` 3. `BE_RESPONSE_VERSION_MAP_2026-10-28.md`
4. `BE_RESPONSE_NUMERIC_CONTRACT_2026-10-28.md` 5. `BE_RESPONSE_BOM_RATIO_2026-10-28.md` 6–10. `BE_RESPONSE_CF_1…CF_5_2026-10-28.md`
11. `BE_OPENAPI_2026-10-28.json` — cộng `BE_RESPONSE_GD_06_2026-10-17_v3.md` (bản sau) và `BE_REPORT_CF_COMPLETED_2026-10-28.md`.
Gửi cùng file này. 🔴 OpenAPI ngày 28 chưa có ràng buộc CF-10 — số liệu ràng buộc theo §3 file này.

### 8.3 Chưa làm (có chủ đích)

| Mục | Vì sao | Điều kiện để làm |
|---|---|---|
| CF-6 sổ giá trị tồn/WIP | PO hoãn 27–28/10 | PO mở lại |
| BOM tỉ lệ 1/3 | PO chọn giữ scale 6 (CF-7 phương án A) | PO đổi quyết định |
| COGS hàng không lô sau đóng lệnh; đóng lệnh không quy trình | FE nói không mở rộng | — |
| Ô "thiếu" ở §7.4 | Chưa có test đặt tên | Lượt test riêng nếu PO cần |

### 8.4 Việc FE cần làm tiếp

**Bắt buộc**
- [ ] Đổi nhãn/mapping giá thành sang "Chi phí vật tư theo giá chuẩn" (§2.3); bỏ giả định giá bình quân.
- [ ] Bắt `409 LOT_CODE_ALREADY_EXISTS` theo `code`, không phụ thuộc `errors[]` (§3).
- [ ] Hiển thị `400 VALIDATION_ERROR` theo field cho mọi số > 6 số lẻ, giữ nguyên số người nhập (§3).
- [ ] Đọc `/actuator/info` để xác nhận đúng bản (§0.1) trước khi nghiệm thu.

**Nên làm**
- [ ] So sánh mốc thời gian ở mức micro giây (§6).
- [ ] Luôn dùng `version` server trả về làm `expectedVersion` lần sau (đừng tự cộng).

**Lỗi do lượt đo tìm ra — ĐÃ SỬA sau lượt đo (2026-10-30):** duyệt phiếu nhập thành phẩm làm `version` **phiếu** tăng **+2**
(1 → 3; trace `e9fb264630364bec`, đọc lại 3 ở `92fba74f64d74b9e`), trong khi `workOrderVersion` tăng đúng +1. Gửi (0 → 1) và QC (3 → 4)
đều +1. Nguyên nhân: với hàng **không theo lô**, câu truy vấn lấy nguồn WIP chạy **giữa** hai lần đổi phiếu nên tự flush thêm một lần (hàng theo lô đã flush sớm khi tạo lô nên không dính). Sửa: đọc nguồn WIP **trước** mọi thay đổi. Bằng chứng: `ProductionFlowE2EIT` kiểm version duyệt = gửi + 1 ở cả luồng có lô lẫn không lô (bỏ bản sửa ⇒ đỏ `expected 2, but was 3`); `mvn -o verify` 2317 unit + 345 IT, 0 lỗi. **Lượt smoke ở §4 chạy trên bản trước khi sửa** — bản sửa nằm ở commit kế tiếp; FE dùng `version` server trả về thì không bị ảnh hưởng ở cả hai bản.

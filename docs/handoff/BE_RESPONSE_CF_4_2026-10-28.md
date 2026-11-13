# BE trả lời — Hàng mua đang chờ QC hiện cạnh nhu cầu MRP

**Trả lời cho:** `FE_REQUEST_MRP_PENDING_QC_VISIBILITY_2026-10-20.md` (v1) · **Ngày gửi:** 28/10/2026 · **Bản:** v1
**Mức:** P2 · **Breaking:** CÓ, hẹp (§3) · **Thứ tự deploy:** không ràng buộc

---

## §0 — Kết luận trong một câu, và bảng phân loại

Mỗi lượt chạy MRP chụp lại hàng mua đang chờ QC của từng dòng nhu cầu và hiện nó cạnh số net (net không đổi); đề xuất mua có hàng chờ QC được cảnh báo; endpoint mới liệt kê từng dòng phiếu nhận.

| # | FE nêu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | Dòng nhu cầu hiện lượng chờ QC đúng vật tư + kho, tách khỏi khả dụng / PO mở / đã loại | 🟡 **Thiếu thật, đã làm** | §4.1: `pendingQcQuantity 10` cạnh `availableQuantity 10`, `openSupplyQuantity 15` |
| R02 | Có nguồn phiếu nhận/dòng + lượng, không giá | 🟡 **Đã làm** | §4.2 endpoint mới |
| R03 | Net không đổi | 🟡 **Đã làm** | §4.1: nhu cầu 40 − khả dụng 10 − PO mở 15 = net 15; chờ QC 10 chỉ hiện bên cạnh |
| R04 | Cảnh báo khi chờ QC > 0 trước đề xuất mua | 🟡 **Đã làm** | Đề xuất mua `READY` → `WARNING` + thông điệp `PENDING_QC_SUPPLY`; đối chứng: đề xuất mua không có hàng chờ QC vẫn `READY` |
| R05 | Ảnh chụp của lượt chạy không bị QC sau đó viết lại | 🟡 **Đã làm** | Nguồn lưu theo lượt chạy; đọc lại không hỏi QC hiện tại |

## §1 — Nguyên nhân thật

Từ GĐ7b (18/10) hàng mua về nằm ở vùng chờ kiểm; nó không còn là lượng mở của đơn mua và chưa là tồn khả dụng, nên MRP không thấy nó và có thể đề xuất mua lại trong lúc QC. Bản này **không** đổi cách tính net (FE Q1) — chỉ thêm phần hiển thị. Lượt chạy cũ: hai field mới là `null` (chưa chụp), khác 0.

Ảnh chụp gồm dòng phiếu nhận mua đã ghi sổ, đã vào vùng chờ, còn phần chờ > 0 (cả hàng theo lô lẫn không lô), đúng vật tư của dòng nhu cầu, ở kho của dòng (dòng không có kho thì các kho trong phạm vi lượt chạy). Phần đã loại không tính. Chờ QC **sản xuất** không tính.

## §2 — Thay đổi contract: bảng trước / sau

| Field | Kiểu | Nullable | Nguồn giá trị | Ví dụ thật (§4.1) |
|---|---|---|---|---|
| `pendingQcQuantity` (dòng nhu cầu) | number | có — `null` = lượt chạy trước bản này | Σ phần chờ QC mua lúc chạy (§1) | `10.0` |
| `pendingQcSourceCount` (dòng nhu cầu) | integer | có — như trên | số dòng phiếu nhận góp vào | `1` |
| thông điệp `PENDING_QC_SUPPLY` trong `messages[]` | string | — | đề xuất **mua** có `pendingQcQuantity > 0` | có |

**Endpoint mới** `GET /api/v1/planning-runs/{runId}/pending-qc-supplies?requirementId=&warehouseId=&page=&size=` — trang, mặc định 20, tối đa 100, sắp theo mã phiếu nhận rồi id dòng (cố định, không có `sortBy`). Mỗi dòng:

| Field | Kiểu | Nguồn |
|---|---|---|
| `sourceId` | uuid | id ảnh chụp |
| `requirementLineId` | uuid | dòng nhu cầu |
| `goodsReceiptId`, `goodsReceiptLineId` | uuid | phiếu / dòng phiếu nhận |
| `receiptCode` | string | mã phiếu (`GR-…`) |
| `receiptVersion` | number | `version` của phiếu lúc chụp |
| `itemId`, `warehouseId` | uuid | |
| `uomCode` | string | đơn vị của vật tư |
| `pendingQuantity` | number | phần chờ lúc chụp |

Không có giá, không có thông tin nhà cung cấp.

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /api/v1/planning-runs`, `/planning-runs/commercial` | đề xuất mua có hàng chờ QC | `exceptionState` có thể `READY` | `WARNING` + `PENDING_QC_SUPPLY` | Đổi giá trị | Hiển thị cảnh báo; chuyển đổi vẫn chạy (không chặn) |

Còn lại thêm field / endpoint mới. Không đổi mặc định query param, thứ tự sắp xếp, không bỏ field nào.

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Fixture: đơn mua 40 KG, đã nhận 25 vào vùng chờ, QC đạt 10 / loại 5 ⇒ còn chờ 10, PO mở 15, khả dụng 10; nhu cầu 40.

### §4.1 Dòng nhu cầu sau lượt chạy

```http
GET /api/v1/planning-runs/20ab106b-5a5a-4b7d-95bc-10f57742889f/requirements?size=100
Content-Type: application/json
```

HTTP **200** · `X-Trace-Id: a84835a8733c4c71`

```json
{"code":"SUCCESS","result":{"content":[{"mrpRequirementLineId":"e91da58c-8457-4a8b-b31a-f9fe824e1578","mrpRunId":"20ab106b-5a5a-4b7d-95bc-10f57742889f","parentRequirementLineId":null,"sourceDemandId":"7a1f3537-d869-4374-a48e-9223e8944471","itemId":"e1961985-d280-4b8c-b8a9-d0e834c63d6f","itemSku":"RAW-170603","itemName":"RAW","uom":"KG","warehouseId":"05a7d7ab-8699-4f08-aca7-1e39b91e9589","warehouseCode":"RM-170603","requirementLevel":0,"grossRequiredQuantity":40.000000,"availableQuantity":10.000000,"reservedQuantity":0.000000,"openSupplyQuantity":15.000000,"safetyStockQuantity":0.000000,"projectedAvailableQuantity":25.000000,"netRequiredQuantity":15.000000,"dueDate":"2026-11-04","requirementStatus":"SHORTAGE","settingSource":"ITEM_WAREHOUSE","warehouseResolutionSource":"DEMAND_WAREHOUSE","excludedLotCount":0,"note":null,"createdAt":"2026-10-28T10:06:04.802698Z","sourceKind":"MANUAL","sourceSalesOrderLineIds":[],"parentRequirementLineIds":[],"calculationState":"CALCULATED","supplySuggestionId":"61fa83c7-64cf-47cd-9b1d-1c4e3de304ca","supplyType":"BUY","exceptionState":"WARNING","messages":["MATERIAL_SHORTAGE","PURCHASING_DEFERRED","PENDING_QC_SUPPLY"],"convertedQuantity":0,"remainingConvertibleQuantity":15.000000,"conversions":null,"pendingQcQuantity":10.000000,"pendingQcSourceCount":1}],"page":0,"size":100,"totalElements":1,"totalPages":1,"first":true,"last":true},"message":"OK"}
```

### §4.2 Nguồn chờ QC của dòng đó

```http
GET /api/v1/planning-runs/20ab106b-5a5a-4b7d-95bc-10f57742889f/pending-qc-supplies?requirementId=e91da58c-8457-4a8b-b31a-f9fe824e1578
Content-Type: application/json
```

HTTP **200** · `X-Trace-Id: 63346559a9f14018`

```json
{"code":"SUCCESS","result":{"content":[{"sourceId":"9332f30c-dacb-4315-a4ad-a0714efc36e9","requirementLineId":"e91da58c-8457-4a8b-b31a-f9fe824e1578","goodsReceiptId":"4b735b30-e3c9-4c85-afb6-7c184af8ff0f","goodsReceiptLineId":"5abca29a-a253-4757-975c-74d455a51cb8","receiptCode":"GR-000020","receiptVersion":3,"itemId":"e1961985-d280-4b8c-b8a9-d0e834c63d6f","warehouseId":"05a7d7ab-8699-4f08-aca7-1e39b91e9589","uomCode":"KG","pendingQuantity":10.000000}],"page":0,"size":20,"totalElements":1,"totalPages":1,"first":true,"last":true},"message":"OK"}
```

Nhánh lỗi:

```http
GET /api/v1/planning-runs/20ab106b-5a5a-4b7d-95bc-10f57742889f/pending-qc-supplies?requirementId=nope
Content-Type: application/json
```

HTTP **400** · `X-Trace-Id: abadb84203c44407`

```json
{"code":"VALIDATION_ERROR","message":"requirementId: must be a valid UUID","errors":[{"field":"requirementId","message":"must be a valid UUID"}]}
```

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — dùng cùng một envelope:

```json
{ "code": "SUCCESS", "result": { "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "first": true, "last": true }, "message": "OK" }
```

| Tình huống | HTTP | `code` | Có `errors[]`? |
|---|---|---|---|
| `requirementId` / `warehouseId` sai định dạng | 400 | `VALIDATION_ERROR` | có |
| Lượt chạy không thuộc phạm vi / không quyền | 403 | `PERMISSION_DENIED` | không |
| Lượt chạy không tồn tại | **403** (không phải 404 — quyền kiểm theo nhà máy của lượt chạy, không có lượt chạy thì không suy ra được nhà máy; đo thật, `X-Trace-Id fbcbc5ec35044eaa`) | `PERMISSION_DENIED` | không |

## §6 — Quyền, phạm vi và giới hạn đã biết

- **Trả lời Q2:** **có**, `PERM_MRP_READ` trên nhà máy của lượt chạy là đủ để đọc endpoint mới — không cần quyền phiếu nhận.
- **Trả lời Q3:** **có**, nguồn đọc từ ảnh chụp của lượt chạy; QC quyết sau đó không đổi số của lượt cũ, lượt mới phản ánh kết quả QC mới.
- **Giới hạn:** ảnh chụp không phải số dư sống — muốn biết hiện còn chờ bao nhiêu thì xem hàng đợi QC mua `GET /api/v1/purchase-qc`.

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — **2193 unit + 331 IT**, 0 failures, 0 errors (lúc phase này đóng); bản hiện tại 2219 + 332 xanh |
| Mutation | 11/11 bị test bắt (cộng chờ QC vào khả dụng · trừ khỏi net · đọc QC sống · bỏ lọc kho · dòng không kho bỏ phạm vi · cảnh báo cả đề xuất sản xuất · không nâng `READY` → `WARNING` · nhận dòng đã quyết xong · bỏ lọc vùng chờ · `null` thay 0 khi không có nguồn · tìm nguồn bỏ lọc dòng nhu cầu) |
| HTTP thật | §4 + đối chứng: đề xuất mua của vật tư khác (không có hàng chờ QC) trong cùng nhà máy vẫn `READY`, `pendingQcQuantity 0` |
| Môi trường | profile dev, jar cổng 8090, Postgres + Redis docker, DB dev có dữ liệu demo (migration mới chạy sạch trên đó); đo 28/10/2026 lúc 17:06 (giờ VN) |

**Chưa nghiệm thu qua HTTP:** "QC xong rồi chạy lượt mới ⇒ chờ 0, lượt cũ vẫn 10" (R05) — có test tự động trên DB thật (`PurchaseQualityIT`), chưa đo qua HTTP.

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Đường ghi mới cho chuyển đổi khi có chờ QC | FE R04 nói không xin trong task này | — |

**FE cần làm tiếp**
- [ ] (nên làm) Hiện `pendingQcQuantity` cạnh net; `null` = lượt chạy cũ, đừng hiện 0.
- [ ] (nên làm) Khi `messages[]` có `PENDING_QC_SUPPLY`, cảnh báo trước khi chuyển đề xuất mua; mở `pending-qc-supplies` để xem phiếu.

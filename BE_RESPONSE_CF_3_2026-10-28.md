# BE trả lời — Tổng hợp tồn tách phần chờ / phần loại QC mua của hàng không theo lô

**Trả lời cho:** `FE_REQUEST_NONTRACKED_QC_BREAKDOWN_2026-10-20.md` (v1) · **Ngày gửi:** 28/10/2026 · **Bản:** v1
**Mức:** P2 · **Breaking:** KHÔNG · **Thứ tự deploy:** không ràng buộc

---

## §0 — Kết luận trong một câu, và bảng phân loại

`GET /api/v1/inventory/balances/aggregate` thêm 4 field giải thích `qualityHoldQuantity` của hàng không theo lô thành phần chờ QC mua, phần QC mua đã loại và phần không phân loại được; `qualityHoldQuantity` giữ nguyên nghĩa.

| # | FE nêu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | Số chờ/loại không lô, không phụ thuộc phân trang hàng đợi QC | 🟡 **Thiếu thật, đã làm** | Một truy vấn tổng hợp cho cả trang tổng hợp tồn, không đọc từ hàng đợi QC |
| R02 | Field mới thêm vào, giữ nghĩa `qualityHoldQuantity` | 🟡 **Đã làm** | §2 |
| R03 | Nhận 25 / đạt 20 / loại 5 ⇒ chờ 0, loại 5, khả dụng 20, tồn 25 | 🟡 **Đã làm** | §4: đo ca 25 / đạt 10 / loại 5 ⇒ chờ 10, loại 5, khả dụng 10, tồn 25, giữ 15 |
| R04 | Phần giữ cũ không phân loại được ⇒ báo không rõ, không ép 0 | 🟡 **Đã làm** | Phần đó vào `unclassifiedQualityHoldQuantity`, `qualityBreakdownKnown = false` |
| R05 | Đọc bằng `PERM_INVENTORY_READ`, không có tiền | ✅ **Đúng như vậy** | Quyền không đổi; 4 field đều là số lượng |

## §1 — Nguyên nhân thật

Từ GĐ7c (19/10), hàng mua không theo lô bị QC loại vẫn nằm trong `qualityHoldQuantity` cùng phần đang chờ, nên màn hình tồn không tách được hai phần. Chỉ riêng endpoint tổng hợp tồn cần sửa; không có dữ liệu nào bị đổi. Hàng theo lô không bị ảnh hưởng (phần chờ/loại của nó nằm trên lô gốc / lô con).

## §2 — Thay đổi contract: bảng trước / sau

| Field | Kiểu | Nullable | Nguồn giá trị | Ví dụ thật (§4) |
|---|---|---|---|---|
| `pendingPurchaseQcQuantity` | number | không | Σ (nhận − đạt − loại) của dòng phiếu nhận mua **không lô**, phiếu đã ghi sổ và đã vào vùng chờ, đúng vật tư + kho | `10.0` |
| `rejectedPurchaseQcQuantity` | number | không | Σ phần QC đã loại của cùng các dòng đó | `5.0` |
| `unclassifiedQualityHoldQuantity` | number | không | `qualityHoldQuantity − pending − rejected`, **không** kẹp về 0 (âm = dữ liệu lệch, để lộ ra) | `0.0` |
| `qualityBreakdownKnown` | boolean | không | `true` khi `unclassified = 0` | `true` |

`qualityHoldQuantity` không đổi nghĩa: `= pending + rejected + unclassified`.

**Trả lời Q2:** phần giữ của phiếu nhận cũ (trước khi có vùng chờ kiểm) hay phần chờ QC **sản xuất** của hàng không lô không có nguồn mua ⇒ rơi vào `unclassifiedQualityHoldQuantity`, và `qualityBreakdownKnown = false`. Không ép thành chờ 0 / loại 0 âm thầm.

## §3 — Breaking change

**`Wire: KHÔNG có`** — chỉ thêm field. Không đổi mặc định query param, thứ tự sắp xếp, không bỏ field nào.

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Fixture: nhận mua 25 KG hàng không lô vào vùng chờ, QC đạt 10 / loại 5.

```http
GET /api/v1/inventory/balances/aggregate?warehouseId=05a7d7ab-8699-4f08-aca7-1e39b91e9589&itemId=e1961985-d280-4b8c-b8a9-d0e834c63d6f
Content-Type: application/json
```

HTTP **200** · `X-Trace-Id: e03a443ab367464e`

```json
{"code":"SUCCESS","result":{"content":[{"itemId":"e1961985-d280-4b8c-b8a9-d0e834c63d6f","itemCode":"RAW-170603","itemName":"RAW","uom":"KG","warehouseId":"05a7d7ab-8699-4f08-aca7-1e39b91e9589","onHandQuantity":25.000000,"reservedQuantity":0.000000,"availableQuantity":10.000000,"qualityHoldQuantity":15.000000,"rejectedQuantity":0,"expiredQuantity":0,"heldLotQuantity":0,"lotCount":0,"updatedAt":"2026-10-28T10:06:04.159368Z","pendingPurchaseQcQuantity":10.000000,"rejectedPurchaseQcQuantity":5.000000,"unclassifiedQualityHoldQuantity":0.000000,"qualityBreakdownKnown":true}],"page":0,"size":20,"totalElements":1,"totalPages":1,"first":true,"last":true},"message":"OK"}
```

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — dùng cùng một envelope:

```json
{ "code": "SUCCESS", "result": { "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "first": true, "last": true }, "message": "OK" }
```

Không có lỗi mới. Lỗi sẵn có: thiếu `warehouseId` ⇒ 400 `VALIDATION_ERROR` có `errors[{field:"warehouseId"}]`; không có quyền kho ⇒ 403 `PERMISSION_DENIED`.

## §6 — Quyền, phạm vi và giới hạn đã biết

- `PERM_INVENTORY_READ` trên kho của request (không đổi).
- **Giới hạn:** chưa có field riêng cho phần chờ QC **sản xuất** của hàng không lô (PO chốt 27/10: để ở `unclassified`).

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — **2180 unit + 327 IT**, 0 failures, 0 errors (lúc phase này đóng); bản hiện tại 2219 + 332 xanh |
| Mutation | 6/6 bị test bắt (cộng phần loại vào phần chờ · bỏ lọc phiếu đã ghi sổ · bỏ lọc vùng chờ · bỏ lọc dòng không lô · kẹp `unclassified` về 0 · gọi truy vấn cho từng dòng thay vì một lần) |
| HTTP thật | Trước QC: giữ 25 = chờ 25 + loại 0 + 0, `known = true`. Sau QC 10/5: §4 |
| Môi trường | profile dev, jar cổng 8090, Postgres + Redis docker, DB dev có dữ liệu demo; đo 28/10/2026 lúc 17:06 (giờ VN) |

**Chưa nghiệm thu qua HTTP:** ca có `unclassified > 0` (cần phiếu nhận cũ hoặc phiếu nhập SX không lô chờ QC) — chỉ có test tự động.

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Field riêng cho chờ QC sản xuất không lô | PO chốt để ở `unclassified` | PO cần tách |

**FE cần làm tiếp**
- [ ] (nên làm) Hiển thị 4 field mới; khi `qualityBreakdownKnown = false` thì ghi "không phân loại được" thay vì suy ra 0.

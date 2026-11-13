# BE trả lời — Chặn điều chỉnh tồn trên lô còn thuộc QC mua / QC sản xuất

**Trả lời cho:** `FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-20.md` (v1) · **Ngày gửi:** 28/10/2026 · **Bản:** v1
**Mức:** P1 · **Breaking:** CÓ, hẹp (một chặn mới, §3) · **Thứ tự deploy:** không ràng buộc

---

## §0 — Kết luận trong một câu, và bảng phân loại

`POST /api/v1/inventory/adjust` nay từ chối (409 `STATE_CONFLICT`) mọi điều chỉnh trên lô mà QC còn sở hữu; các lối đi vòng còn lại (đổi trạng thái lô, xuất, giữ chỗ, chuyển kho, kiểm đếm, đảo nhập) đã chặn từ trước.

| # | FE nêu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | adjust/count không đổi lượng của lô/vùng chờ còn thuộc phiếu nhận | 🟡 **Thiếu thật ở `adjust`, đã làm.** `count` đã chặn từ trước | §4.1: `adjust +1` trên lô chờ QC ⇒ 409, không sinh biến động |
| R02 | status/issue/reserve/transfer không biến hàng chờ/loại thành dùng được | ✅ **Đã có từ trước, không sửa** | Đổi trạng thái lô chờ QC / lô con bị loại ⇒ 409 `LOT_NOT_ELIGIBLE`; xuất/giữ chỗ/chuyển kho chỉ nhận lô `AVAILABLE` |
| R03 | Hàng không theo lô: tổng kho không xuống dưới phần đang giữ | ✅ **Đã có từ trước, không sửa** | Kiểm đếm hàng không lô xuống dưới (đã giữ chỗ + vùng chờ) ⇒ 409 `STATE_CONFLICT`; `adjust` âm vượt tồn khả dụng ⇒ 409 `INSUFFICIENT_AVAILABLE_STOCK` |
| R04 | QC là nơi duy nhất chuyển chờ → đạt/loại, một người thắng | ✅ **Đã có từ trước, không sửa** | Hai quyết định QC cùng `expectedVersion` ⇒ một 200, một 409 `CONCURRENT_MODIFICATION` |
| R05 | Phiếu đã có quyết định QC không đảo nhập | ✅ **Đã có từ trước, không sửa** | `POST /api/v1/goods-receipts/{id}/cancel` trên phiếu đã QC ⇒ 409 `STATE_CONFLICT` |

## §1 — Nguyên nhân thật

`adjust` cộng/trừ thẳng vào lô mà không hỏi QC còn giữ lô đó không, nên một lô đang chờ QC mua có thể bị chỉnh tay mà không qua quyết định QC nào. Lỗ hổng có từ khi có QC theo lượng (GĐ7c, 19/10) và QC sản xuất (GĐ9c, 23/10). Chỉ riêng `adjust` bị; các đường khác đã kiểm trạng thái lô. Dữ liệu cũ không bị đổi; bản sửa chỉ chặn lệnh mới.

Lô bị chặn khi **một trong ba**: (a) lô gốc còn phần chờ QC mua; (b) lô còn phần chờ QC sản xuất; (c) lô con bị QC loại (mua hoặc sản xuất). Lô `HOLD` do người dùng tự đặt (không gắn phiếu nào), lô con đạt, và hàng không theo lô **không** bị chặn bởi luật này.

## §2 — Thay đổi contract: bảng trước / sau

| Field / Endpoint | Trước | Sau | Nullable? | Phá client đang chạy? |
|---|---|---|---|---|
| `POST /api/v1/inventory/adjust` trên lô QC còn sở hữu | 200, đổi tồn | 409 `STATE_CONFLICT`, không ghi gì | — | Có, nếu client từng chỉnh tay lô đang chờ QC (đúng việc FE xin chặn) |

Không thêm field nào.

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /api/v1/inventory/adjust` | lô còn phần chờ QC mua/sản xuất, hoặc lô con bị QC loại | 200 | 409 `STATE_CONFLICT` | Chặn mới | Hiển thị lỗi; hướng người dùng sang màn hình QC |

Không đổi mặc định query param, không đổi thứ tự sắp xếp, không bỏ field nào.

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

### §4.1 Lô gốc đang chờ QC mua (4 nhận, chưa quyết) ⇒ bị chặn

```http
POST /api/v1/inventory/adjust
Content-Type: application/json
Idempotency-Key: 2359025e-4992-4258-875d-08304e18ec63

{"itemId": "7c1b4dfb-5ac3-4d9f-a917-87fd1050797a", "warehouseId": "05a7d7ab-8699-4f08-aca7-1e39b91e9589", "lotId": "5a8027ae-4cf5-47bd-9d27-97de7921f4eb", "quantityDelta": 1, "reason": "Smoke recount"}
```

HTTP **409** · `X-Trace-Id: a6285c6770ab4211`

```json
{"code":"STATE_CONFLICT","message":"Lot still has quantity awaiting purchase QC; it cannot be adjusted outside the goods receipt"}
```

Sau khi QC đạt 2.5 / loại 0.5, lô gốc còn chờ 1 ⇒ vẫn bị chặn; lô con bị loại cũng bị chặn:

```http
POST /api/v1/inventory/adjust
Content-Type: application/json
Idempotency-Key: d588ba34-6dde-4c94-9d7c-be66a2dc4e3f

{"itemId": "7c1b4dfb-5ac3-4d9f-a917-87fd1050797a", "warehouseId": "05a7d7ab-8699-4f08-aca7-1e39b91e9589", "lotId": "1d3f5bac-413f-4337-99eb-bb854948badc", "quantityDelta": -0.5, "reason": "Smoke recount"}
```

HTTP **409** · `X-Trace-Id: 88c417b8059b4002`

```json
{"code":"STATE_CONFLICT","message":"Lot was rejected by QC; its quantity belongs to the QC decision and cannot be adjusted"}
```

### §4.2 Lô con đạt ⇒ chỉnh được bình thường

```http
POST /api/v1/inventory/adjust
Content-Type: application/json
Idempotency-Key: d601f532-b6d4-419a-8478-3934a39eae0a

{"itemId": "7c1b4dfb-5ac3-4d9f-a917-87fd1050797a", "warehouseId": "05a7d7ab-8699-4f08-aca7-1e39b91e9589", "lotId": "1512aa96-14fc-4be8-8a26-63e052525561", "quantityDelta": -0.5, "reason": "Smoke recount"}
```

HTTP **200** · `X-Trace-Id: 0bfc8a2946634de5`

```json
{"code":"SUCCESS","result":{"movementId":"d5b13a71-00b3-4563-8804-9ac89f9a3170","itemId":"7c1b4dfb-5ac3-4d9f-a917-87fd1050797a","warehouseId":"05a7d7ab-8699-4f08-aca7-1e39b91e9589","lotId":"1512aa96-14fc-4be8-8a26-63e052525561","lotCode":"LOT-170603-A1","movementType":"ADJUST_OUT","direction":"OUT","quantity":0.5,"reason":"Smoke recount","referenceType":null,"referenceId":null,"idempotencyKey":"d601f532-b6d4-419a-8478-3934a39eae0a","createdAt":"2026-10-28T10:06:04.719209600Z","traceId":"0bfc8a2946634de5","createdBy":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","createdByUsername":"admin"},"message":"OK"}
```

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — dùng cùng một envelope:

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "quantity", "message": "must be greater than 0" } ] }
```

`code` là tên hằng, FE rẽ nhánh theo nó; `errors` chỉ có khi lỗi theo field; `result` vắng mặt khi lỗi.

| Tình huống | HTTP | `code` | Có `errors[]`? |
|---|---|---|---|
| Lô còn thuộc QC (3 trường hợp §1) | 409 | `STATE_CONFLICT` | không |
| Rút quá tồn khả dụng | 409 | `INSUFFICIENT_AVAILABLE_STOCK` | không |
| Gửi lại cùng `Idempotency-Key` + cùng body | 200 | `SUCCESS` (chứng từ cũ, không ghi lần hai) | không |
| Cùng key, body khác | 409 | `IDEMPOTENCY_CONFLICT` | không |
| `quantityDelta` 0 | 422 | `NEGATIVE_QUANTITY` | không |

Trả lời Q3: **có**, `STATE_CONFLICT` là mã dùng nhất quán cho luật "QC còn sở hữu" ở `adjust`. Đổi trạng thái lô dùng `LOT_NOT_ELIGIBLE` như trước (GĐ7c) — hai mã này không đổi.

## §6 — Quyền, phạm vi và giới hạn đã biết

- Quyền: `PERM_INVENTORY_MOVE` trên kho của request (không đổi). Kiểm quyền chạy **trước** khi xét replay key.
- **Giới hạn (trả lời Q2):** chưa có quy trình riêng để "sửa lượng gốc" của một phiếu nhận đang chờ QC. Hiện cả `adjust` lẫn `count` đều chặn; muốn sửa thì phải QC quyết hết phần chờ trước (đạt/loại), hoặc huỷ phiếu nhận khi chưa có quyết định QC nào. Một quy trình sửa nguồn có audit là việc riêng nếu PO cần.

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn -o clean verify` — **2151 unit + 317 IT**, 0 failures, 0 errors (lúc phase này đóng); bản hiện tại 2219 + 332 cũng xanh |
| Mutation | 5/5 bị test bắt: bỏ guard · đổi `STATE_CONFLICT` sang một mã 409 khác · mở rộng chặn sang mọi lô không `AVAILABLE` · gọi guard trên đường replay · bỏ trường hợp lô con QC sản xuất loại |
| HTTP thật | §4: 4 probe đạt — `+1` lô chờ ⇒ 409 (`a6285c6770ab4211`), `−1` lô gốc còn chờ 1 ⇒ 409, lô con loại ⇒ 409, lô con đạt ⇒ 200. Lệnh bị chặn **không** sinh biến động kho (đếm `stock_movements` của lô gốc trước/sau) |
| Môi trường | profile dev, app chạy từ jar ở cổng 8090, Postgres + Redis qua docker, DB dev có dữ liệu demo; fixture dùng-một-lần tạo qua API; đo 28/10/2026 lúc 17:06 (giờ VN) |

**Chưa nghiệm thu:** hai lệnh QC + `adjust` gửi đồng thời trên cùng lô chưa đo qua HTTP (chỉ có test tự động cho tranh chấp QC).

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Quy trình sửa lượng gốc của phiếu nhận | FE Q2 đồng ý tạm chặn; chưa có thiết kế audit | PO chốt cần quy trình này |

**FE cần làm tiếp**
- [ ] (bắt buộc) Bắt 409 `STATE_CONFLICT` ở màn hình điều chỉnh tồn và chỉ người dùng sang QC.
- [ ] (nên làm) Ẩn nút điều chỉnh trên lô đang chờ QC / lô con bị loại — server vẫn chặn nếu gọi thẳng.

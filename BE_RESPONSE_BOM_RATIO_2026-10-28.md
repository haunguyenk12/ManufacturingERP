# BE trả lời — BOM theo lô có tỉ lệ không biểu diễn được (1 KG ra 3 PCS)

**Trả lời cho:** `FE_QUESTION_BOM_BATCH_RATIO_2026-10-20.md` (v1) · **Ngày gửi:** 28/10/2026 · **Bản:** v1
**Mức:** P2 · **Breaking:** KHÔNG (chỉ trả lời, không đổi code) · **Thứ tự deploy:** không ràng buộc

---

## §0 — Kết luận trong một câu, và bảng phân loại

BOM có tỉ lệ thành phần/đầu ra cần quá 6 số lẻ (như 1/3) bị từ chối rõ ràng ở cả form lẫn file nhập, không lưu số đã làm tròn; PO đã duyệt (27/10) **giữ giới hạn này**, chưa mở rộng sang lưu lượng theo lô làm nguồn.

| # | FE hỏi | Trả lời | Bằng chứng |
|---|---|---|---|
| D01 | 1 / 3 bị 400 ở `lines[i].componentQuantity`, không lưu số làm tròn? | **Đúng** | §4.1: 400, field `lines[0].componentQuantity`, không tạo BOM |
| Q1 | Lưu `componentQuantity` + `outputQuantity` làm nguồn chính xác? | **Chưa.** PO chọn giữ giới hạn 6 số lẻ (27/10) | quyết định PO |
| Q2 | Giới hạn 1/3 được ghi là PO đã chốt phạm vi? | **Có** — PO đã duyệt ngày 27/10/2026 chọn **giữ** giới hạn; không còn là "BE tự chọn" | quyết định PO |
| Q3 | Form và file nhập `kind=bom` cùng luật? | **Có** (đo thật) | §4.1 form ⇒ 400; §4.2 file ⇒ batch `INVALID`, lỗi cùng nội dung ở cột F |

## §1 — Nguyên nhân thật

Không phải lỗi. Hệ thống lưu định mức theo **một đơn vị thành phẩm** (`quantityPer`, tối đa 6 số lẻ); lượng theo lô trên form chỉ để nhập và đọc lại. 1 KG / 3 PCS = 0.333…, không có biểu diễn 6 số lẻ chính xác, nên BE từ chối thay vì làm tròn — làm tròn thì mở lại form sẽ thấy số khác số đã gõ. Ví dụ biểu diễn được (đầu ra 10, thành phần 25 ⇒ 2.5) lưu và đọc lại đúng 25.

Hệ quả cho người dùng: tỉ lệ thành phần / đầu ra phải là số thập phân hữu hạn tối đa 6 số lẻ. 1 KG → 4 PCS (0.25) hay 1 KG → 8 PCS (0.125) được; 1 KG → 3 PCS hay 1 KG → 6 PCS thì không, dù đổi lô sang 3 KG → 9 PCS (vẫn là 1/3). Cách duy nhất hiện nay là chọn đơn vị khiến tỉ lệ chia hết (ví dụ thành phần tính theo gram và chấp nhận 333.333 g / PCS là xấp xỉ do người dùng tự chọn).

## §2 — Thay đổi contract

Không đổi. Ràng buộc hiện hành: `componentQuantity / outputQuantity` phải biểu diễn đúng với tối đa 6 số lẻ; `scrapRate` tối đa 6 số lẻ (`5.1234%` = `0.051234` được nhận; `5.12345%` = `0.0512345` bị từ chối 400, không cắt âm thầm — theo ràng buộc trong code, chưa đo riêng qua HTTP).

## §3 — Breaking change

**`Wire: KHÔNG có`**

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

### §4.1 Form: `PUT bom-drafts` với 1 thành phần cho 3 đầu ra

```http
PUT /api/v1/companies/f3b31f95-697b-4c19-95a5-cd8915a85454/bom-drafts
Content-Type: application/json

{"bomCode": "B13-170603", "parentItemId": "424a7bd4-05d2-4d56-9741-c33568eea17c", "outputQuantity": 3, "lines": [{"lineNo": 1, "componentItemId": "e1961985-d280-4b8c-b8a9-d0e834c63d6f", "componentQuantity": 1, "scrapRate": 0}]}
```

HTTP **400** · `X-Trace-Id: 9d7ce6670e844c38`

```json
{"code":"VALIDATION_ERROR","message":"lines[0].componentQuantity: divided by outputQuantity needs more than 6 decimals; adjust either quantity","errors":[{"field":"lines[0].componentQuantity","message":"divided by outputQuantity needs more than 6 decimals; adjust either quantity"}]}
```

### §4.2 File nhập `kind=bom` cùng dữ liệu

File mẫu `omniplant-bom-v1` (sheet `_meta`: `version` / `omniplant-bom-v1`, `locale` / `en`; sheet `BOM` một dòng: đầu ra 3 KG, thành phần 1 KG). Upload trả **201** với batch `INVALID` — đúng luật nhập (lỗi theo dòng không phải lỗi HTTP), commit sẽ không tạo gì:

```http
POST /api/v1/data-import/batches?kind=bom&companyId=f3b31f95-697b-4c19-95a5-cd8915a85454
Content-Type: application/json

multipart: bom-1-per-3-170728.xlsx
```

HTTP **201** · `X-Trace-Id: 471af2031a6e4203`

```json
{"code":"SUCCESS","result":{"id":"d57262ff-d12a-4ae5-9b1b-52b5ada8e3da","actorId":"23fce5b6-dd3b-4d30-a8b8-c33962a38a82","companyId":"f3b31f95-697b-4c19-95a5-cd8915a85454","plantId":null,"kind":"bom","locale":"en","templateVersion":"omniplant-bom-v1","fileName":"bom-1-per-3.xlsx","fileSize":5496,"status":"INVALID","version":0,"createdAt":"2026-10-28T10:07:28.948115Z","committedAt":null,"rowCount":1,"validRowCount":0,"errorRowCount":1,"createdCount":0,"rows":[{"line":2,"values":{"bomCode":"B13X-170728","productCode":"OUT-170603","outputQuantity":"3","outputUom":"KG","componentCode":"RAW-170603","componentQuantity":"1","componentUom":"KG","scrapPercent":"0","notes":""},"issues":[{"field":"componentQuantity","column":"F","code":"INVALID_FORMAT","message":"Component quantity divided by output quantity needs more than 6 decimals"}]}],"createdIds":[],"bomCount":1,"salesOrderCount":null},"message":"Created successfully"}
```

Mã lỗi theo dòng: `INVALID_FORMAT`, field `componentQuantity`, cột `F`.

## §5 — Envelope và hợp đồng lỗi

Mọi response — thành công và lỗi — dùng cùng một envelope:

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "quantity", "message": "must be greater than 0" } ] }
```

| Tình huống | HTTP | `code` | Có `errors[]`? |
|---|---|---|---|
| Form: tỉ lệ cần quá 6 số lẻ | 400 | `VALIDATION_ERROR` | có, `lines[i].componentQuantity` |
| File: tỉ lệ cần quá 6 số lẻ | 201 | `SUCCESS`, batch `status: INVALID`; lỗi ở `rows[].issues[]` mã `INVALID_FORMAT` | — (lỗi nằm trong batch) |
| Commit batch `INVALID` | 409 | `STATE_CONFLICT` | không |
| Version cũ khi lưu nháp | 409 | `CONCURRENT_MODIFICATION` | có |

## §6 — Quyền, phạm vi và giới hạn đã biết

- Quyền: `PERM_BOM_READ` + `PERM_BOM_MANAGE` trên công ty (không đổi).
- Giới hạn: như §1 — không hỗ trợ tỉ lệ cần quá 6 số lẻ (PO duyệt).

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| HTTP thật | §4.1 (`9d7ce6670e844c38`) và §4.2 (`471af2031a6e4203`) |
| Môi trường | profile dev, jar cổng 8090, Postgres + Redis docker; đo 28/10/2026 lúc 17:06–17:07 (giờ VN) |

**Chưa nghiệm thu qua HTTP:** ca hao hụt `5.12345%` (theo ràng buộc trong code là 400).

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Lưu lượng theo lô làm nguồn chính xác (Q1, D02/D03) | PO chọn giữ giới hạn 6 số lẻ (27/10). Mở rộng đụng nổ BOM của MRP, ảnh chụp lệnh SX, giá thành, nhập Excel | PO đổi quyết định; BE sẽ đo tác động trước khi lập kế hoạch |

**FE cần làm tiếp**
- [ ] (nên làm) Ở form BOM, báo trước khi lưu nếu `componentQuantity / outputQuantity` cần quá 6 số lẻ (cùng luật với BE).

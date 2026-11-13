# BE báo cáo — Hoàn thành đợt bổ sung Capstone 3 (CF-8 → CF-13)

**Gửi cho:** team FE · **Ngày gửi:** 30/10/2026 · **Bản:** v1
**Mức:** P1 · **Breaking:** CÓ, hẹp (CF-9, CF-10, CF-11 — bảng ở §3) · **Thứ tự deploy:** không ràng buộc
**Nối tiếp:** `BE_REPORT_CF_COMPLETED_2026-10-28.md` (CF-0 → CF-5). File này thay hai ý của file đó: §2 mục 1 (nhóm field thập phân chưa chặn số lẻ thứ 7) và §4 (`/actuator/info` trả `{}`) — cả hai nay đã đổi, xem dưới.

---

## §0 — Kết luận

BE đã xong **6/6 phase** của đợt bổ sung 28/10 (gói `CAPSTONE3_BE_COMPLETION`), theo ba quyết định PO ngày 28/10 và bốn câu trả lời PO ngày 29/10. Đợt này chủ yếu **khép lỗ hổng và chứng minh bằng đo thật**, ít tính năng mới.

| Phase | Nội dung | Mức | Breaking | Loại thay đổi |
|---|---|---|---|---|
| CF-8 | Trả lời 3 câu hỏi (giá chuẩn, BOM scale 6, nhận diện bản) + `/actuator/info` nay có commit và thời điểm đóng gói | P2 | KHÔNG | Tài liệu + cấu hình |
| CF-9 | Mã lô không trùng khi chỉ khác hoa thường, kể cả khi nhận đồng thời | P1 | CÓ, hẹp (nhánh đua) | Sửa lỗi toàn vẹn dữ liệu |
| CF-10 | Chặn số lẻ thứ 7 / quá 13 chữ số nguyên cho 20 field còn thiếu | P1 | CÓ, hẹp | Chặn mới |
| CF-11 | `requiredQuantity` của lệnh SX làm tròn đúng 6 số lẻ ngay trong response tạo | P1 | KHÔNG (đổi hình dạng số) | Chuẩn hoá |
| CF-12 | Khép phần bảo vệ QC sau CF-1 (đếm / điều chỉnh / đua với QC) | P1 | KHÔNG | Chỉ thêm test, không đổi hành vi |
| CF-13 | Kiểm chứng chốt giá thành: thu hồi quyền khi chờ khoá, lỗi giữa chừng rollback sạch | P1 | KHÔNG | Chỉ thêm test, không đổi hành vi |

**Một lỗi do lượt đo thật tìm ra và đã sửa:** duyệt phiếu nhập thành phẩm **không theo lô** làm `version` của phiếu tăng **+2** thay vì +1 (lượt đo trên bản chưa sửa: 1 → 3). Test tự động trước đó không thấy vì chỉ duyệt hàng theo lô. Đã sửa; FE luôn dùng `version` server trả về thì không bị ảnh hưởng ở cả hai bản.

**Không làm (có chủ đích):** sổ giá trị tồn kho (CF-6) vẫn hoãn; BOM tỉ lệ cần quá 6 số lẻ như 1/3 (CF-7) vẫn giữ giới hạn. Giá thành tiếp tục lấy **giá chuẩn vật tư chụp lúc phát hành**, không phải bình quân kho.

Kèm theo: `BE_RESPONSE_CF_8_2026-10-29.md` (trả lời chi tiết 3 câu hỏi, có DTO `standard-cost` và ví dụ gọi thật). `BE_OPENAPI_2026-10-28.json` **vẫn là bản đúng** — đã xuất lại sau CF-13, trùng từng byte; springdoc không thể hiện ràng buộc số lẻ nên đọc ràng buộc ở §2 file này.

## §1 — Từng phase đã làm gì

### CF-8 — Trả lời và nhận diện bản
- **Giá chuẩn:** baseline giá thành đọc `requiredQuantity` × giá chuẩn **chụp lúc phát hành**. Đo thật: giá RM 1.000 → phát hành lệnh 20 KG (kế hoạch 20.000) → đổi giá master 1.200 → baseline của lệnh đó **vẫn 20.000**; lệnh phát hành sau đó lấy 1.200; tiêu thụ 21 ⇒ thực tế 21.000 (không phải 25.200).
- **BOM scale 6:** form 10 → 25 lưu `quantityPer 2.5`, đọc lại `componentQuantity 25`; hao hụt `5.1234%` nhận (`0.051234`), `5.12345%` bị từ chối — cả form lẫn file nhập `kind=bom`. Dữ liệu cũ không bị đổi; giới hạn chỉ áp lần ghi mới.
- **Nhận diện bản:** `GET /actuator/info` (cổng quản trị) nay trả `app.commit` + `build.time`. Trước đây trả `{}`.

### CF-9 — Mã lô không phân biệt hoa thường
Hai request đồng thời nhận cùng một vật tư với mã lô `LOT-X` và `lot-x`: trước đây **cả hai thành công** (hai lô chỉ khác hoa thường). Nay một thắng, một nhận **409 `LOT_CODE_ALREADY_EXISTS`** và không ghi gì (phiếu nhận vẫn `DRAFT`, PO không tăng số đã nhận, không có biến động tồn). Cùng mã ở vật tư khác vẫn dùng được. Áp cho mọi đường tạo lô (nhận mua, nhập thủ công, duyệt phiếu nhập thành phẩm, tách lô QC).

### CF-10 — Chặn số lẻ thứ 7
20 field thập phân trước đây nhận 7 số lẻ rồi để DB làm tròn nay bị từ chối **trước khi ghi**. Quy tắc chung: tối đa **6 số lẻ** và **13 chữ số nguyên**; số `0` thừa cũng được đếm (`1.1234560` = 7 số lẻ ⇒ lỗi). Số dạng chuỗi JSON cho kết quả giống số dạng number.

| Endpoint | Field |
|---|---|
| `PUT /api/v1/companies/{C}/items/{I}/standard-cost` | `materialCost`, `laborCost`, `overheadCost` |
| `PUT /api/v1/inventory/item-warehouse-settings` | `safetyStock`, `reorderPoint` |
| `POST /api/v1/planning/demands` | `requiredQuantity` |
| `POST /api/v1/planning/production-estimates` | `targetQuantity` |
| `POST /api/v1/items/{itemId}/suppliers` | `minimumOrderQuantity`, `unitPrice` |
| `POST /api/v1/purchase-orders` (đường tạo cũ) | `lines[].orderedQuantity`, `lines[].unitPrice` |
| `POST /api/v1/purchase-requisitions` | `lines[].requestedQuantity` |
| `POST /api/v1/purchase-requisitions/{id}/approve` | `approvedLines[].approvedQuantity` |
| `POST /api/v1/companies/{C}/routings` (đường tạo cũ) | `operations[].setupMinutes`, `operations[].runMinutesPerUnit` |
| `POST /api/sales-orders/v1`, `PATCH /api/sales-orders/v1/{id}` (đường cũ) | `lines[].orderedQuantity` |
| `POST /api/v1/material-issues` (phẳng) | `quantity` |
| `POST /api/v1/work-orders/{id}/wip-transactions` | `quantity` |
| `POST /api/v1/work-orders/{id}/component-issues` | `quantity` |
| `POST /api/v1/work-orders/{id}/output-completions` | `quantity` |

Các đường mới của GĐ6–GĐ11 (đơn bán nháp, đơn mua nháp, phiếu nhận, phiếu giao, hoá đơn, thu tiền, BOM/routing nháp, báo sản lượng, phiếu nhập) **đã chặn từ trước**, không đổi. Nhập Excel cũng đã chặn 13/6 từ trước.

### CF-11 — Làm tròn `requiredQuantity`
Công thức không đổi: `kế hoạch × quantityPer × (1 + tỉ lệ hao hụt)`, không có chỗ nào chia `(1 − tỉ lệ)`. Điều đổi: response **tạo/sửa** lệnh SX nay in đúng 6 số lẻ (HALF_UP) và bằng số lưu / số đọc lại; trước đây có thể in 12 số lẻ. MRP vẫn làm tròn **lên** (CEILING) 6 số lẻ, nên dòng lệnh SX có thể thấp hơn run đã đề xuất nó **tối đa 0,000001** — MRP lập kế hoạch dư, không bao giờ thiếu. Baseline giá thành **kế thừa** số đã lưu, không nhân hao hụt lần hai.

### CF-12 — Bảo vệ QC (chỉ test)
Đã chứng minh bằng test: hàng **không theo lô** có 25 nhận, 20 đạt / 5 loại thì kiểm đếm tổng 4 bị từ chối `STATE_CONFLICT`, kiểm đếm 24 và điều chỉnh −1 hợp lệ, điều chỉnh −21 bị từ chối `INSUFFICIENT_AVAILABLE_STOCK`, vùng chờ giữ nguyên 5. Kiểm đếm lô cha đang chờ QC hoặc lô con bị loại ⇒ `LOT_NOT_ELIGIBLE`. Khi QC và kiểm đếm chạy đồng thời trên cùng dòng tồn, bên thua luôn nhận **409**, không bao giờ ghi số sai âm thầm. Không cần sửa code nên không có thay đổi cho FE.

### CF-13 — Chốt giá thành (chỉ test + một phép đo thật)
- Thu hồi quyền chốt trong lúc đang chờ khoá ⇒ **403 `PERMISSION_DENIED`**, không có run / bút toán / đóng lệnh nào được ghi.
- Lỗi giữa chừng khi chốt ⇒ **500** và DB không đổi; gửi lại cùng `Idempotency-Key` chốt đúng một lần.
- Đo thật trên app (10 PCS, 20 KG × 1.000, 60 phút, nhân công 6.000 + chi phí chung 3.000 mỗi giờ): kế hoạch **29.000**, thực tế **29.000**, đơn vị **2.900**; `expectedVersion` sai ⇒ 409 `CONCURRENT_MODIFICATION`; chốt ⇒ `CLOSED`, một bút toán chỉ-giá-trị `quantity 0` / giá trị 29.000, tồn vật lý thành phẩm vẫn 10; gửi lại cùng key trả cùng kết quả.

## §2 — Thay đổi contract: trước / sau

| Chỗ | Trước | Sau |
|---|---|---|
| 20 field thập phân ở §1 CF-10 | gửi 7 số lẻ ⇒ `200`, response lặp lại số chưa làm tròn, DB lưu số đã làm tròn; quá 13 chữ số nguyên ⇒ `422 BUSINESS_RULE_VIOLATION` không có `errors[]` | `400 VALIDATION_ERROR` + `errors[{field, message}]` |
| Đua tạo lô `LOT-X` / `lot-x` | cả hai thành công | một thành công, một `409 LOT_CODE_ALREADY_EXISTS` |
| `componentLines[].requiredQuantity` trong response tạo lệnh SX | có thể in 12 số lẻ (`20.000000000000`) | đúng 6 số lẻ |
| `GET /actuator/info` | `{}` | có `app.commit`, `build.time`, `build.version` |

Field mới: **không có**. Query param mặc định, thứ tự sắp xếp, field bị bỏ: **không đổi**.

## §3 — Breaking change

**Wire: CÓ** (hẹp).

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| 14 request ở bảng §1 CF-10 | > 6 số lẻ hoặc > 13 chữ số nguyên | `200` / `422` | **`400 VALIDATION_ERROR`** + `errors[{field}]` | Đổi mã | Giữ số người nhập, hiện lỗi theo field. Field lồng dạng `lines[1].orderedQuantity` |
| Mọi đường tạo lô | hai lô cùng vật tư, chỉ khác hoa thường, ghi đồng thời | cả hai `200` | một `200`, một **`409 LOT_CODE_ALREADY_EXISTS`** | Đổi mã (nhánh đua) | Bắt theo `code`. Kẻ thua đua **không có `errors[]`**; nhánh kiểm tuần tự có `errors[{field:"lines[0].lotCode"}]` — đừng dựa vào `errors[]` |
| Response tạo lệnh SX | `requiredQuantity` | tối đa 12 số lẻ | 6 số lẻ | Đổi hình dạng số | Không phải sửa nếu FE đã parse số |

Ví dụ nhánh lỗi thật của CF-10 (`PUT /api/v1/companies/{C}/items/{I}/standard-cost`, body `{"materialCost":0,"laborCost":1.1234567,"overheadCost":0}`, trace `2d48a6149dfa4f74`):

```
HTTP 400
{"code":"VALIDATION_ERROR","message":"laborCost: numeric value out of bounds (<13 digits>.<6 digits> expected)","errors":[{"field":"laborCost","message":"numeric value out of bounds (<13 digits>.<6 digits> expected)"}]}
```

Cùng body với `"laborCost":"1.1234567"` (chuỗi) cho kết quả y hệt (trace `c1ec48cb17fb4fcb`); sau cả hai lần, đọc lại giá chuẩn trả 404 — không ghi gì. `"laborCost":1.123456` ⇒ `200` và đọc lại đúng `1.123456`.

Ví dụ nhánh lỗi thật của CF-9 (hai post đồng thời `GR-000021` lô `LOT-X…` và `GR-000022` lô `lot-x…`):

```
POST /api/v1/goods-receipts/{id thắng}/post  → 200  (trace b7f34d22c96a4623)
POST /api/v1/goods-receipts/{id thua}/post   → 409  (trace 475d0b08d8c84c67)
{"code":"LOT_CODE_ALREADY_EXISTS","message":"Lot code already exists for this item"}
```

Phiếu thua đọc lại: `status DRAFT`, `version 0`; PO của nó vẫn `CONFIRMED`; vật tư chỉ có đúng **1 lô**.

## §4 — Nhận diện bản BE đang tích hợp

```
GET http://<host>:<cổng quản trị>/actuator/info
{"app":{"commit":"448000a60bc6d1b7e56b121da681943dfda456d1"},"build":{"artifact":"manufacturing","name":"manufacturing-erp","time":"2026-10-30T02:09:25.395Z","version":"0.0.1-SNAPSHOT","group":"com.erp"}}
```

| Mốc | Giá trị (bản BE đang chạy sau đợt này) |
|---|---|
| Commit | `448000a60bc6d1b7e56b121da681943dfda456d1` |
| SHA-256 của file chạy | `0e259a896dcaaa87e37d307d98231aad14febedd5a9dd3b7aed8b3faf2fd6c91` |
| Migration DB | `95` (chỉ đợt này thêm đúng một migration: chỉ mục duy nhất cho mã lô) |

FE so `app.commit` và `build.time`. Số migration một mình **không** đủ để phân biệt bản (nhiều phase không có migration). Cổng quản trị là cổng riêng, không nằm dưới `/api`.

## §5 — Envelope và hợp đồng lỗi

Không đổi: `{ "code", "result", "message", "errors" }`; FE rẽ nhánh theo `code`, không theo HTTP status. Mã dùng trong đợt này đều đã có: `VALIDATION_ERROR` (400), `LOT_CODE_ALREADY_EXISTS` (409), `STATE_CONFLICT` (409), `LOT_NOT_ELIGIBLE` (409), `INSUFFICIENT_AVAILABLE_STOCK` (409), `CONCURRENT_MODIFICATION` (409), `PERMISSION_DENIED` (403). Không có mã mới.

## §6 — Quyền, phạm vi, giới hạn đã biết

- **Quyền:** không thêm quyền mới, không đổi quyền nào.
- **Dấu thời gian lúc ghi:** response của lệnh vừa ghi có thể in độ chính xác 100 ns (`…867401100Z`) còn đọc lại in µs (`…867401Z`). Đã có từ trước; so sánh theo giây, đừng so chuỗi giữa lúc ghi và lúc đọc lại.
- **Hai dạng 409 của `LOT_CODE_ALREADY_EXISTS`:** xem §3.
- **Chưa có quy trình "sửa lượng nguồn"** của phiếu nhận đang chờ QC: điều chỉnh / kiểm đếm trên lô còn thuộc QC bị chặn, chưa có đường thay thế.
- **Giá thành:** không phải bình quân kho. Hàng không theo lô sau khi đóng lệnh không truy được giá vốn; đóng lệnh không có quy trình chưa hỗ trợ; `/variance` (theo vật tư) và `/production-costs` (theo trung tâm + thời gian) là hai hệ khác nhau, không so trực tiếp.

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | `mvn verify` xanh: **2.317 test đơn vị + 345 test tích hợp**, 0 lỗi. Mỗi thay đổi có kiểm đột biến: cố ý làm hỏng logic (bỏ chỉ mục lô, bỏ giới hạn số lẻ, đổi cách làm tròn, bỏ kiểm quyền dưới khoá, nuốt lỗi giữa chừng…) đều bị test bắt |
| HTTP thật | **7/7 kịch bản đạt** trên app chạy thật: nhận diện bản, giá chuẩn chụp lúc phát hành, BOM scale 6 (form + file nhập), đua mã lô, số lẻ thứ 7, `requiredQuantity 1.200001` ngay trong response tạo, chốt giá thành 29.000 |
| Môi trường | profile dev, Postgres + Redis qua docker; đo ngày 29/10/2026 (giờ UTC 15:58–16:01) |

**Chưa nghiệm thu:**
- Lỗi `version` +2 khi duyệt phiếu nhập hàng không lô: lượt đo HTTP chạy **trước** bản sửa (thấy 1 → 3). Bản sửa chỉ được kiểm bằng test tích hợp (duyệt = gửi + 1 ở cả luồng có lô lẫn không lô), **chưa đo lại qua HTTP**.
- Dấu hiệu hành vi "giữ chỗ lô hết hạn ⇒ 409" (CF-2) và "xem trước lịch = phát hành khi `capacityUnits = 2`" (CF-5) chưa đo qua HTTP trên bản này: cần đặt hạn quá khứ cho lô (API cố ý không cho) và cần lịch làm việc. Cả hai đã có test tự động.
- Chuỗi BOM → MRP → lệnh SX → baseline giá thành với hao hụt > 0 chưa có test chuỗi (từng khâu đã có test riêng).
- Chuyển kho, giữ chỗ và cấp vật tư trên hàng **không theo lô** đang chờ QC chưa có test riêng; giữ chỗ trên lô `HOLD` / `REJECTED` chỉ có test cho nhánh hết hạn.

## §8 — Chưa làm + việc FE cần làm tiếp

| Mục | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Sổ giá trị tồn kho / WIP (CF-6) | PO hoãn | PO mở lại |
| BOM tỉ lệ cần quá 6 số lẻ, ví dụ 1/3 (CF-7) | PO chọn giữ giới hạn | PO đổi quyết định; BE đo tác động trước |
| Test chuỗi hao hụt BOM → MRP → lệnh SX → baseline | Từng khâu đã có test; chuỗi chưa viết | Khi cần bằng chứng toàn tuyến |
| Test bảo vệ QC cho chuyển kho / giữ chỗ / cấp vật tư trên hàng không lô chờ QC | Ngoài phạm vi CF-12 | Khi FE hoặc PO yêu cầu |

**FE cần làm tiếp**
- [ ] (bắt buộc) 14 request ở §1 CF-10: bắt `400 VALIDATION_ERROR`, gắn lỗi vào đúng field theo `errors[].field` (kể cả field lồng `lines[i].…`).
- [ ] (bắt buộc) Tạo lô / nhận hàng: bắt `409 LOT_CODE_ALREADY_EXISTS` theo `code`, không dựa vào việc có `errors[]`.
- [ ] (bắt buộc) Luôn dùng `version` trong response vừa nhận làm `expectedVersion` lần sau; không tự cộng.
- [ ] (nên làm) Có thể bỏ lớp tự giới hạn 6 số lẻ / 13 chữ số nguyên ở form mà FE đã thêm tạm sau báo cáo 28/10 — BE nay chặn thật; giữ lại nếu muốn báo lỗi sớm ở trình duyệt.
- [ ] (nên làm) Nếu có quyền truy cập cổng quản trị, ghi `app.commit` + `build.time` vào báo cáo lỗi để BE biết đúng bản.
- [ ] (nên làm) Script / dữ liệu mẫu phía FE: kiểm tra không gửi quá 6 số lẻ vào các field ở §1 CF-10.

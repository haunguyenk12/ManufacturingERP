# BE trả lời — Giai đoạn 9: Nhập thành phẩm và kiểm tra chất lượng

**Trả lời cho:** `FE_HANDOFF_GD_09_2026-10-15.md` (v1, P1) · **Ngày gửi:** 21/10/2026 · **Bản:** v1 — **phần 1/3: vòng đời phiếu nhập + đọc (`G9a`)**
**Trạng thái:** BE đã triển khai và tự nghiệm thu trên môi trường dev. Phía BE chia Giai đoạn 9 thành **3 phần**, gửi lần lượt:
- v1 (bản này): vòng đời phiếu nhập, version, hạn mức nhập, ngày, quyền đọc.
- v2: phân bổ theo từng lượt báo sản lượng và truy xuất nguồn.
- v3: QC từng phần nhiều lượt.

Task chưa đóng: FE cần chạy A01–A40 và báo đạt.

---

## §0 — Kết luận trong một câu, và bảng phân loại

**Phiếu nhập thành phẩm nay có đủ vòng đời:**
- tạo, sửa nháp, gửi, rút về nháp, huỷ, duyệt, từ chối;
- mọi lệnh sau khi tạo phải gửi version của phiếu **và** của lệnh sản xuất, mỗi lệnh làm version lệnh sản xuất tăng đúng 1;
- hạn mức `G/C/A/D/W/U` được trả ở mọi response, cùng `allowedActions`;
- `receivedOn`/`expiresOn` do server kiểm theo ngày của nhà máy;
- có hai quyền đọc mới.

| # | FE yêu cầu | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| R01 | List/ứng viên/chi tiết đúng phạm vi, bộ lọc, phân trang, không cần quyền đọc lệnh SX | 🟡 **Đã làm** | Bộ lọc mới ở §2.1; chi tiết `GET .../production-receipts/{R}`; quyền đọc riêng (§6) |
| R02 | Một phiếu một WO/vật tư/kho đầu ra/lô; nguồn khoá theo WO | 🟡 **Một phần đã có, phần khoá kho đã làm** | Một phiếu một dòng vốn đã đúng. **Mới:** kho nhập phải là kho đầu ra của lệnh, khác ⇒ `422 RESOURCE_SCOPE_MISMATCH` (§4.3) |
| R03 | Trần từ good công đoạn cuối, kẹp theo kế hoạch | ✅ **Đã đúng + trả riêng `G`/`C`** | `actualGoodQuantity` = good công đoạn cuối từ GĐ8 (B219). `C = min(P, G)` trả riêng (`receiptableGoodCap`) — xem Q4 |
| R04 | Nháp/chờ giữ phần đã nhận; sửa loại trừ phần của chính nó; rút giữ; từ chối/huỷ trả một lần | 🟡 **Đã làm** | Lượt `004655`: tạo 5 ⇒ `U 4`; tạo thêm 5 ⇒ `409` field `quantity`; rút ⇒ vẫn `U 4`; từ chối/huỷ ⇒ `U` về `4` (§4.4–§4.7) |
| R05 | Phân bổ theo từng lượt báo | ⏳ **Phần v2** | — |
| R06 | Chỉ người tạo sửa/gửi/rút/huỷ; từ chối/rút/huỷ cần lý do | 🟡 **Đã làm** | Người khác có `MANAGE` huỷ ⇒ `403` (§4.8); lý do 4 ký tự ⇒ `400` field `reason` |
| R07 | Duyệt nguyên tử | ✅ **Đã đúng từ trước** | Một transaction: phiếu + lô `HOLD` + biến động + `completedQuantity` + sổ WIP + audit. Gửi lại cùng key ⇒ không biến động thứ hai (§4.6) |
| R08 | Duyệt kiểm lại hiện trạng, không tự đổi kho/lô | 🟡 **Đã làm** | Duyệt kiểm lại version, kho còn hoạt động và đúng kho đầu ra, `A + q ≤ C`, mã lô còn trống, ngày. Không tự chọn kho/lô khác |
| R09–R11 | QC từng phần | ⏳ **Phần v3** | — |
| R12 | Lô `HOLD`/hết hạn không dùng được; lệnh kho chung không đi vòng QC | ✅ **Phần lớn đã đúng** · ⚠️ một lệch | Xem §6, lệch 2 |
| R13 | Truy xuất nguồn | ⏳ **Phần v2** | — |
| R15 | Nguồn cung WO cho MRP giảm theo phần đã duyệt | ⏳ **Hoãn có chủ đích** | Xem Q32 và §8 |
| R16 | QC không làm đơn bán `FULFILLED` | ⏳ **Giữ hành vi cũ có chủ đích** | Xem Q31 và §8 |
| R17 | `NUMERIC(19,6)`, từ chối chữ số lẻ thứ 7 | 🟡 **Đã làm** | Số lẻ thứ 7 ⇒ `400` field `quantity` |
| R18 | Quyền + phạm vi trên cả ID trực tiếp lẫn replay | 🟡 **Đã làm** · ⚠️ 403 thay 404 | Xem §6, lệch 1 |
| R19 | Version trên mọi lệnh | 🟡 **Đã làm** | Thiếu ⇒ `400`, lệch ⇒ `409 CONCURRENT_MODIFICATION` (§4.2, §4.6) |
| R20 | Idempotency: cùng key + body ⇒ một lần; khác body ⇒ xung đột | ✅ **Đã đúng + mở rộng** | Thêm cho sửa/gửi/rút/huỷ/duyệt/từ chối (§4.6) |
| R21 | Ngày do server kiểm, `receivedOn ≤ PlantToday` | 🟡 **Đã làm** | §4.3; hạn lô xem Q23 |
| R22 | Phiếu cũ giữ nguyên, không bịa nguồn | 🟡 **Đã làm phần ngày** | `receivedOn` của phiếu cũ = ngày nhận kho thật (§2.3). Phần nguồn nằm ở v2 |
| R23 | Giá trị unknown ≠ 0 | ⏳ **Hoãn** | Xem Q26 |
| R24 | API cũ không bị đổi nghĩa ngầm | 🟡 **Một phần** | Endpoint QC cũ chưa đổi trong v1. Submit/approve/reject đổi body (§3) |

### Trả lời Q (những câu thuộc phần này)

| Q | Trả lời |
|---|---|
| Q1 | **Có.** Một phiếu = một lệnh = một vật tư = một kho đầu ra = một lô (nếu theo lô). Kho phải là kho đầu ra của lệnh |
| Q2 | **Có.** Nhận từng phần khi lệnh `RELEASED`/`IN_PROGRESS`, và cả khi `COMPLETED` (vốn đã thế từ trước) |
| Q3 | **Có.** Trần lấy good công đoạn cuối, gồm good của lượt xử lý lại; không cộng các công đoạn khác, không tính làm lại đang chờ |
| Q4 | **Có, trả riêng `actualFinalGoodQuantity` (G) và `receiptableGoodCap` (C = min(P, G)).** Lưu ý: CSDL **đã ép** `good ≤ kế hoạch` và `đã nhập ≤ good`. Vì vậy `G > P` hay `A > C` không xảy ra được, và thực tế `C = G`. BE không gửi cảnh báo cho hai tình huống không thể xảy ra |
| Q5 | **Có.** `U = max(0, C − A − D)`, với `D` = tổng phiếu `DRAFT` + `PENDING_APPROVAL`. Sửa nháp được trả lại phần của chính nó trước khi so |
| Q7 | **Có.** Phần nháp đã giữ không tự hết hạn; chỉ huỷ/từ chối mới trả lại |
| Q8 | **Có, theo vật tư** (vật tư thuộc một công ty). Mã lô đã trim, so **không phân biệt hoa thường** khi tạo/sửa/gửi/duyệt ⇒ `409 LOT_CODE_ALREADY_EXISTS` field `lotNumber` |
| Q9 | **Không.** Nháp **không** giữ chỗ mã lô. Kiểm sớm chỉ để báo lỗi cho người dùng; bảo đảm thật nằm ở lúc duyệt (kiểm lại + ràng buộc duy nhất trong CSDL) |
| Q10 | **Giữ chính sách FE:** người tạo được tự duyệt nếu có `PERM_PRODUCTION_RECEIPT_APPROVE`; không áp luật tự duyệt của cấp vật tư |
| Q11 | **Có.** `PERM_PRODUCTION_RECEIPT_READ`, `PERM_PRODUCTION_QC_READ`, chỉ cho ADMIN + MANAGER (§6) |
| Q12 | **Có, một version chung của lệnh sản xuất.** Mọi lệnh trên phiếu nhập (tạo, sửa, gửi, rút, huỷ, duyệt, từ chối, QC) làm version lệnh tăng **đúng 1**. Response trả cả `version` của phiếu lẫn `workOrderVersion`. ⚠️ Khác ví dụ J2: phiếu mới có `version: 0`, không phải 1 |
| Q13 | **Khác đề xuất, bảo đảm vẫn đủ.** Tạo phiếu: key nằm trên chính phiếu, duy nhất toàn bảng, so dấu vân tay body. Lệnh khác: key duy nhất theo `(key, lệnh)`, dấu vân tay gồm phiếu + body. Key dùng lại cho phiếu khác hoặc body khác ⇒ `409 IDEMPOTENCY_CONFLICT`. Quyền được kiểm **trước** khi trả replay |
| Q14 | **Có, mạnh hơn yêu cầu:** key không hết hạn |
| Q23 | **Có.** Lô hết hạn tại **00:00** của ngày `expiresOn`, giờ nhà máy. Ví dụ thật: `expiresOn 2027-01-19` ⇒ `expiresAt 2027-01-18T17:00:00Z` (giờ VN) |
| Q25 | **Có.** Lý do trim, dài 5–2000 ký tự. Thiếu ⇒ `400 APPROVAL_REASON_REQUIRED`; ngoài khoảng ⇒ `400 VALIDATION_ERROR` field `reason`. `note` tối đa 2000 |
| Q26 | **Có — giá trị chưa tính.** BE chưa có lớp giá thành phẩm (hoãn lần thứ tư, cùng lý do GĐ7/GĐ8). Phần v3 sẽ trả `valuationStatus = PENDING_COSTING` và **không** trả field giá trị, nên FE không bao giờ nhận số `0` |
| Q28 | **Dùng mã có sẵn, không thêm mã mới ở v1** (bảng ánh xạ ở §5) |
| Q31 | **Khác mong muốn của FE — giữ nguyên hành vi cũ có chủ đích.** QC `AVAILABLE` vẫn tăng `fulfilledQuantity` và có thể đưa đơn bán sang `FULFILLED`. FE cần đọc số QC đạt ở `qcAcceptedQuantity` (có từ GĐ6); `deliveredQuantity` luôn `null`. Đổi nghĩa `fulfilled` sẽ làm cùng chứng từ giao hàng ở GĐ10 — nếu đổi bây giờ, đơn bán sẽ không bao giờ hoàn tất |
| Q32 | **Khác fixture A38 — giữ nguyên có chủ đích.** Cung tương lai của lệnh vẫn là `kế hoạch − đã nhập` cho lệnh `RELEASED`/`IN_PROGRESS`. Với fixture A38 (kế hoạch 10, duyệt 9), cung tương lai là **1**, không phải 0; con số về 0 khi lệnh sang `COMPLETED`. Phần "QC đạt vào tồn đúng một lần" thì đã đúng: hàng `HOLD` không tính, chỉ phần đạt tính |
| Q6, Q15–Q22, Q24, Q27, Q29, Q30 | Trả lời ở v2 (nguồn) và v3 (QC) |

---

## §1 — Nguyên nhân thật

FE không báo lỗi nào; đây là phần nâng cấp. Bốn điều ảnh hưởng tới FE:

1. **Gửi, duyệt, từ chối nay bắt buộc có body version.** Adapter đang gọi `submit`/`approve` không body sẽ nhận `400` ngay khi BE deploy. Đây là breaking change lớn nhất (§3).
2. **Tạo phiếu bắt buộc `expectedWorkOrderVersion` + `receivedOn`, và kho nhập phải là kho đầu ra của lệnh.**
3. **Smoke qua HTTP thật tìm ra hai lỗi, cả hai đã sửa trước khi gửi:**
   - **Sửa nháp chỉ đổi số lượng thì `version` phiếu không tăng.** Số lượng nằm ở dòng con, nên phiếu không bị coi là đã đổi. Hai lần sửa đồng thời trên cùng version vì thế cùng qua được. Nay mỗi lần sửa đều tăng version.
   - **Mốc thời gian trong response của lệnh khác lần đọc lại.** Ví dụ `postedAt …270618600Z` lúc tạo nhưng `…270619Z` khi đọc lại. Nay mọi mốc của phiếu nhập được cắt về micro giây ngay khi ghi.
4. **Nghiệm thu mutation 15/15 bị giết** (§7).

---

## §2 — Thay đổi contract

### 2.1 Endpoint

| Method | Path | Mới / đổi | Quyền |
|---|---|---|---|
| GET | `/api/v1/production-receipts/candidates?plantId&workOrderId?&search?` | **Đổi (additive):** thêm `workOrderId`, `search` (mã lệnh, SKU/tên vật tư, không phân biệt hoa thường) + field hạn mức (§2.2) | `PERM_PRODUCTION_RECEIPT_READ` / `_MANAGE` / `_APPROVE` |
| GET | `/api/v1/production-receipts?plantId&status?&workOrderId?&destinationWarehouseId?&receivedFrom?&receivedTo?&search?` | **Đổi (additive):** 5 bộ lọc mới, kết hợp AND; ngày `YYYY-MM-DD` theo ngày nhà máy, gồm cả hai đầu; `search` trên mã phiếu, mã lệnh, SKU/tên vật tư, số lô | 3 quyền phiếu nhập **hoặc** `PERM_PRODUCTION_QC_READ` / `PERM_QUALITY_DISPOSITION` (chỉ thấy `APPROVED`) |
| GET | `/api/v1/work-orders/{W}/production-receipts/{R}` | **Mới** | như dòng trên |
| GET | `/api/v1/work-orders/{W}/production-receipts/{R}/events?page&size` | **Mới** | như dòng trên |
| POST | `/api/v1/work-orders/{W}/production-receipts` | **Đổi:** `expectedWorkOrderVersion` + `receivedOn` **bắt buộc**; `expiresOn?`; kho = kho đầu ra của lệnh | `PERM_PRODUCTION_RECEIPT_MANAGE` |
| PATCH | `/api/v1/work-orders/{W}/production-receipts/{R}` | **Mới** — body `{expectedVersion, expectedWorkOrderVersion, quantity, lotNumber?, receivedOn, expiresOn?, note?}`, **thay toàn bộ** các field này; nguồn (lệnh/vật tư/kho) không đổi; chỉ người tạo | `PERM_PRODUCTION_RECEIPT_MANAGE` |
| POST | `.../{R}/submit` | **Đổi:** body `{expectedVersion, expectedWorkOrderVersion}` **bắt buộc**; chỉ người tạo | `PERM_PRODUCTION_RECEIPT_MANAGE` |
| POST | `.../{R}/withdraw` | **Mới** — `PENDING_APPROVAL → DRAFT`, body `{expectedVersion, expectedWorkOrderVersion, reason}`; chỉ người tạo | `PERM_PRODUCTION_RECEIPT_MANAGE` |
| POST | `.../{R}/cancel` | **Mới** — `DRAFT`/`PENDING_APPROVAL → CANCELLED`, cùng body; chỉ người tạo | `PERM_PRODUCTION_RECEIPT_MANAGE` |
| POST | `.../{R}/approve` | **Đổi:** body `{expectedVersion, expectedWorkOrderVersion}` **bắt buộc**; kiểm lại hiện trạng | `PERM_PRODUCTION_RECEIPT_APPROVE` |
| POST | `.../{R}/reject` | **Đổi:** body `{expectedVersion, expectedWorkOrderVersion, reason}` | `PERM_PRODUCTION_RECEIPT_APPROVE` |
| GET | `/api/v1/work-orders/{W}/production-receipts` | **Đổi quyền:** đọc được bằng 5 quyền như list theo nhà máy (trước chỉ `_MANAGE`) | như trên |

Mọi lệnh có thay đổi nhận header `Idempotency-Key`. Tạo phiếu vẫn **bắt buộc** key như trước; các lệnh khác thì tuỳ chọn. `POST .../qc-disposition` (QC nguyên phiếu) **chưa đổi** trong v1; phần v3 sẽ nối nó vào sổ QC mới.

### 2.2 Field mới trên response

`ProductionReceiptResponse` giữ nguyên mọi field cũ và thêm:

| Field | Kiểu | Nullable? | Nguồn | Ví dụ thật (lượt `004655`) |
|---|---|---|---|---|
| `version` | integer | không | version phiếu — gửi lại làm `expectedVersion` | `0` lúc tạo, `6` sau khi duyệt |
| `workOrderVersion` | integer | không | version lệnh **sau** lệnh này | `5` sau khi tạo |
| `plantId` / `plantTimeZone` | UUID / string | không | nhà máy của lệnh | `"Asia/Ho_Chi_Minh"` |
| `receivedOn` | date | không | ngày nhận nghiệp vụ | `"2026-10-21"` |
| `expiresOn` | date | **có** | hạn lô; chỉ hàng theo lô | `"2027-01-19"` |
| `destinationWarehouseName` | string | không | | `"FG"` |
| `submittedByUsername` / `rejectedByUsername` | string | có | người gửi (lần gần nhất) / người từ chối | `"admin"` |
| `withdrawnAt` / `withdrawReason` / `withdrawnByUsername` | instant / string / string | có | lần rút **gần nhất** | `"Wrong expiry date"` |
| `cancelledAt` / `cancelReason` / `cancelledByUsername` | instant / string / string | có | | `"Entered twice"` |
| `actualFinalGoodQuantity` | decimal | không | G | `9.000000` |
| `receiptableGoodCap` | decimal | không | C = min(P, G) | `9.000000` |
| `approvedReceiptQuantity` | decimal | không | A | `5.000000` sau khi duyệt |
| `claimedQuantity` | decimal | không | D | `5.000000`; `0` khi không còn phiếu mở |
| `awaitingReceiptQuantity` | decimal | không | W = max(0, C − A) | `4.000000` |
| `unclaimedReceiptQuantity` | decimal | không | U = max(0, C − A − D) | `4.000000` |
| `allowedActions` | string[] | không | `EDIT`/`SUBMIT`/`WITHDRAW`/`CANCEL`/`APPROVE`/`REJECT`/`QC` theo trạng thái + người tạo + quyền của **người gọi**; chỉ để bật/tắt nút — server vẫn kiểm từng lệnh | `["EDIT","SUBMIT","CANCEL"]` |

Lưu ý:
- Hạn mức (G…U) là của **lệnh sản xuất**, giống nhau trên mọi phiếu của cùng lệnh.
- Số lượng là JSON number. Số chữ số lẻ không cố định: `claimedQuantity` bằng 0 in là `0`, không phải `0.000000`. Giá trị không đổi theo cách viết.

`ProductionReceiptCandidateResponse` giữ field cũ (`availableToReceipt` = U) và thêm: `workOrderVersion`, `plannedQuantity`, `receiptableGoodCap`, `approvedReceiptQuantity`, `claimedQuantity`, `awaitingReceiptQuantity`, `unclaimedReceiptQuantity`, `outputWarehouseId`/`Code`/`Name`.

Mỗi dòng của `events[]` có dạng `{action, actorId, actorName, reason, at}`, xếp cũ nhất trước:
- `action` ∈ `CREATED` / `SUBMITTED` / `WITHDRAWN` / `APPROVED` / `REJECTED` / `CANCELLED` / `QC_DECIDED`.
- ⚠️ Mỗi loại hành động hiện **một dòng — lần gần nhất**. Phiếu rút rồi gửi lại hai lần chỉ thấy lần gửi cuối. Vết đầy đủ nằm ở `GET /api/v1/audit-logs?entityId={R}` (cần quyền audit).

### 2.3 Dữ liệu cũ

Phiếu có từ trước bản này được gán `receivedOn` = ngày duyệt; phiếu chưa duyệt lấy ngày tạo nháp. Ngày tính theo giờ nhà máy.

Ví dụ kiểm trong test migration: duyệt lúc `2026-10-19T18:30:00Z` ⇒ `receivedOn 2026-10-20`.

`expiresOn` của phiếu cũ để `null`.

---

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST .../production-receipts` | thiếu `expectedWorkOrderVersion` / `receivedOn` | `201` | `400 VALIDATION_ERROR` theo field | **Bắt buộc mới** | gửi version lệnh vừa đọc + ngày nhận |
| `POST .../production-receipts` | kho khác kho đầu ra của lệnh | `201` | `422 RESOURCE_SCOPE_MISMATCH` field `destinationWarehouseId` | **Chặn mới** | khoá ô kho = kho đầu ra của lệnh (có sẵn trên ứng viên) |
| `POST .../production-receipts` | `quantity` có chữ số lẻ thứ 7 | lưu | `400` field `quantity` | **Chặn mới** | giới hạn 6 chữ số lẻ |
| `POST .../production-receipts` | mã lô trùng mã đã có, **khác hoa thường** | `201` (đụng CSDL lúc duyệt hoặc tạo lô mới) | `409 LOT_CODE_ALREADY_EXISTS` field `lotNumber` | **Chặn mới** | không |
| `POST .../submit`, `.../approve` | không body | `200` | `400 VALIDATION_ERROR` | **Bắt buộc mới** | gửi `{expectedVersion, expectedWorkOrderVersion}` |
| `POST .../submit` | người gọi không phải người tạo | `200` | `403 PERMISSION_DENIED` | **Chặn mới** | ẩn nút theo `allowedActions` |
| `POST .../reject` | body chỉ có `reason` | `200` | `400` field `expectedVersion` | **Bắt buộc mới** | thêm hai version |
| `POST .../reject` | lý do 1–4 ký tự | `200` | `400` field `reason` | **Chặn mới** | ≥ 5 ký tự |
| `POST .../approve` | kho đích đã ngừng / quá hạn từ lúc lưu / vượt `C` | tạo tồn | `422 RESOURCE_INACTIVE` / `409 LOT_NOT_ELIGIBLE` field `expiresOn` / `409 PLANNED_QUANTITY_EXCEEDED` | **Chặn mới** | đọc lại và xử lý nháp |
| mọi lệnh trên | version cũ | `200` | `409 CONCURRENT_MODIFICATION` | **Bắt buộc mới** | đọc lại rồi gửi với key mới |
| mọi lệnh trên phiếu nhập | — | version lệnh không đổi | version lệnh **+1** | **Đổi hành vi** | dùng `workOrderVersion` của response cho lệnh kế tiếp |

Additive (không phá): 5 endpoint mới, 5 bộ lọc, các field ở §2.2.

Nới quyền đọc: đọc phiếu không còn chỉ cần `_MANAGE` — xem §6.

**Thứ tự deploy: FE trước hoặc cùng lúc.**
- BE không có giai đoạn chấp nhận cả dạng cũ lẫn dạng mới.
- FE có thể gửi sẵn các field version và ngày ngay bây giờ: bản BE cũ bỏ qua field lạ, không báo lỗi.

---

## §4 — Cách dùng: ví dụ copy từ lần gọi thật

Môi trường: dev, cổng 8090, ngày 21/10/2026 khoảng 00:47 giờ VN (lượt `004655`).
- Token đã che.
- UUID là bản ghi thật.
- Response dài được lược bằng `...`; các phần còn lại là nguyên văn.
- Header chung: `Authorization: Bearer <REDACTED>`, `Content-Type: application/json`.

Fixture:

| Thành phần | Giá trị |
|---|---|
| Lệnh | `e78b4046-1377-4e8f-acb6-0a88db82b7e7` |
| Kế hoạch / good cuối | 10 / 9 |
| Vật tư | theo lô |
| Kho đầu ra | `807ee03e-d72b-4cde-9dcb-d36bf3dbb34e` (`FG-004655`) |

### 4.1 Tạo nháp 5

```http
POST /api/v1/work-orders/e78b4046-1377-4e8f-acb6-0a88db82b7e7/production-receipts
Idempotency-Key: bc06fc5c-dfe4-4356-a7c9-a8fbf4722b3b
```
```json
{"destinationWarehouseId": "807ee03e-d72b-4cde-9dcb-d36bf3dbb34e", "quantity": 5, "receivedOn": "2026-10-21", "lotNumber": "GD9-004655-01", "expiresOn": "2027-01-19", "expectedWorkOrderVersion": 4}
```
`201`, `X-Trace-Id: 6a19d7ad3b1a411e`
```json
{"code":"SUCCESS","result":{"receiptId":"c725f114-b91b-4920-81d9-7b54d1e1a5b9","code":"PR-979B1922",...,"status":"DRAFT",...,"version":0,"workOrderVersion":5,"plantId":"6a441ad5-ef55-4859-b04a-bc69b431beaf","plantTimeZone":"Asia/Ho_Chi_Minh","receivedOn":"2026-10-21","expiresOn":"2027-01-19","destinationWarehouseName":"FG",...,"actualFinalGoodQuantity":9.000000,"receiptableGoodCap":9.000000,"approvedReceiptQuantity":0.000000,"claimedQuantity":5.000000,"awaitingReceiptQuantity":9.000000,"unclaimedReceiptQuantity":4.000000,"allowedActions":["EDIT","SUBMIT","CANCEL"]},"message":"Created successfully"}
```
Version lệnh 4 → 5: tăng đúng 1. Tồn kho không đổi.

### 4.2 Version thiếu

Cùng path, thiếu `expectedWorkOrderVersion` ⇒ `400`, `X-Trace-Id: bedac4c94f024e6a`:
```json
{"code":"VALIDATION_ERROR","message":"expectedWorkOrderVersion: must not be null","errors":[{"field":"expectedWorkOrderVersion","message":"must not be null"}]}
```

### 4.3 Ngày và kho do server kiểm

`receivedOn: "2026-10-22"` (ngày mai theo giờ nhà máy) ⇒ `400`, `X-Trace-Id: d9ef3ab2e3cf4ec6`:
```json
{"code":"VALIDATION_ERROR","message":"receivedOn: must not be after the plant's today 2026-10-21","errors":[{"field":"receivedOn","message":"must not be after the plant's today 2026-10-21"}]}
```
Kho khác cùng nhà máy ⇒ `422`, `X-Trace-Id: f586d823bee34e3e`:
```json
{"code":"RESOURCE_SCOPE_MISMATCH","message":"destinationWarehouseId: must be the work order's output warehouse FG-004655","errors":[{"field":"destinationWarehouseId","message":"must be the work order's output warehouse FG-004655"}]}
```
`expiresOn` bằng `receivedOn` ⇒ `400` field `expiresOn`. Gửi `expiresOn` cho hàng không theo lô ⇒ `400` field `expiresOn`.

### 4.4 Vượt phần chưa ai giữ

Còn 4 chưa giữ, tạo thêm 5 ⇒ `409`, `X-Trace-Id: 76108cc2dfbc468d`:
```json
{"code":"PLANNED_QUANTITY_EXCEEDED","message":"quantity: exceeds the 4.000000 of final good, up to the plan, that no receipt has claimed yet","errors":[{"field":"quantity","message":"exceeds the 4.000000 of final good, up to the plan, that no receipt has claimed yet"}]}
```
Tên mã là `PLANNED_QUANTITY_EXCEEDED`, tương ứng mã FE đề xuất `PRODUCTION_OUTPUT_EXCEEDED` (§5).

### 4.5 Sửa, gửi, rút, gửi lại

Sửa nháp thành 4 rồi thành 5:
```http
PATCH /api/v1/work-orders/e78b4046-…/production-receipts/c725f114-…
```
```json
{"expectedVersion": 0, "expectedWorkOrderVersion": 5, "quantity": 4, "lotNumber": "GD9-004655-01", "receivedOn": "2026-10-21", "expiresOn": "2027-01-19", "note": "edited"}
```
- Lần sửa đầu: `200`, phiếu `version` 0 → 1, lệnh 5 → 6.
- Lần sửa sau (lên 5): `200`, phiếu `version` 1 → 2, lệnh 6 → 7.

Gửi:
```http
POST /api/v1/work-orders/e78b4046-…/production-receipts/c725f114-…/submit
```
```json
{"expectedVersion": 2, "expectedWorkOrderVersion": 7}
```
`200`, `X-Trace-Id: 9ab4d4896dee4c37`. Response có `"status":"PENDING_APPROVAL"`, `"version":3`, `"workOrderVersion":8`, `"allowedActions":["WITHDRAW","CANCEL","APPROVE","REJECT"]`.

Rút với lý do 4 ký tự (`"abcd"`) ⇒ `400`, `X-Trace-Id: c6dca57cc33f4b6c`:
```json
{"code":"VALIDATION_ERROR","message":"reason: must be between 5 and 2000 characters","errors":[{"field":"reason","message":"must be between 5 and 2000 characters"}]}
```
Rút với lý do hợp lệ:
```json
{"expectedVersion": 3, "expectedWorkOrderVersion": 8, "reason": "Wrong expiry date"}
```
`200`, `X-Trace-Id: 4d21923c8d32491e`. Response có `"status":"DRAFT"`, `"withdrawReason":"Wrong expiry date"`, `"withdrawnByUsername":"admin"`, `"claimedQuantity":5.000000`, `"unclaimedReceiptQuantity":4.000000` — phần đã giữ **vẫn giữ**.

### 4.6 Duyệt: version cũ, duyệt thật, gửi lại

Duyệt với version phiếu cũ ⇒ `409`, `X-Trace-Id: da98a732deef4637`:
```json
{"code":"CONCURRENT_MODIFICATION","message":"Production receipt version is 5, not 4; reload it"}
```
Duyệt đúng version:
```http
POST /api/v1/work-orders/e78b4046-…/production-receipts/c725f114-…/approve
Idempotency-Key: 05fedca1-c082-4565-8b65-1212858199a2
```
```json
{"expectedVersion": 5, "expectedWorkOrderVersion": 10}
```
`200`, `X-Trace-Id: c3ab9ac994e148be`. Response có:
- `"status":"APPROVED"`, `"outputLotStatus":"HOLD"`, `"version":6`, `"workOrderVersion":11`;
- `"approvedReceiptQuantity":5.000000`, `"claimedQuantity":0`, `"awaitingReceiptQuantity":4.000000`, `"unclaimedReceiptQuantity":4.000000`, `"allowedActions":["QC"]`.

Kết quả đọc lại:
- Lệnh tăng đúng 1 (10 → 11).
- Tồn thực tế +5; lô `HOLD`.
- Lô có `expiresAt: "2027-01-18T17:00:00Z"`, tức 00:00 ngày 19/01 giờ VN.

Gửi lại **cùng key + cùng body** ⇒ `200`, cùng phiếu, không có biến động kho thứ hai.

Cùng key nhưng **khác body** ⇒ `409`, `X-Trace-Id: c891e7d30b1d49ad`:
```json
{"code":"IDEMPOTENCY_CONFLICT","message":"Idempotency-Key was already used with a different payload"}
```

### 4.7 Mã lô trùng khác hoa thường; từ chối; huỷ

Tạo phiếu với `lotNumber: "gd9-004655-01"` (lô `GD9-004655-01` đã có) ⇒ `409`, `X-Trace-Id: de0c6eb44cb74ebb`:
```json
{"code":"LOT_CODE_ALREADY_EXISTS","message":"lotNumber: lot gd9-004655-01 already exists for this item; send its lotId only for an eligible partial receipt","errors":[{"field":"lotNumber","message":"lot gd9-004655-01 already exists for this item; send its lotId only for an eligible partial receipt"}]}
```
Từ chối một phiếu chờ:
```json
{"expectedVersion": 1, "expectedWorkOrderVersion": 13, "reason": "Count does not match"}
```
`200`, `X-Trace-Id: 5a235da054e1492a`. Response có `"status":"REJECTED"`, `"rejectedByUsername":"admin"`, `"claimedQuantity":0`, `"unclaimedReceiptQuantity":4.000000` — phần giữ đã được trả.

Huỷ một nháp:
```json
{"expectedVersion": 0, "expectedWorkOrderVersion": 15, "reason": "Entered twice"}
```
`200`, `X-Trace-Id: 4addd23635734ebc`. Response có `"status":"CANCELLED"`, `"cancelledAt":"2026-10-20T17:46:59.355510Z"`, `"cancelReason":"Entered twice"`. Huỷ lần hai ⇒ `409 STATE_CONFLICT`.

### 4.8 Người khác có quyền vẫn không đụng được nháp của người tạo

User `g9a.clerk.004655` có `PERM_PRODUCTION_RECEIPT_MANAGE` + `_READ` trên nhà máy. User này huỷ nháp do `admin` tạo ⇒ `403`, `X-Trace-Id: 74108af284ad4efc`:
```json
{"code":"PERMISSION_DENIED","message":"Only the person who created the receipt can cancel it"}
```
Khi user này đọc nháp đó, `allowedActions` là `[]`.

### 4.9 Danh sách và quyền chỉ đọc QC

Các truy vấn danh sách (kết quả đúng như mô tả):
- `GET /api/v1/production-receipts?plantId=…&search=gd9-004655-01` ⇒ đúng phiếu có lô đó (không phân biệt hoa thường).
- `receivedFrom=receivedTo=2026-10-20` ⇒ đúng phiếu nhận ngày hôm qua.
- `status=CANCELLED` ⇒ hai phiếu đã huỷ.
- Header `X-Plant-Id` lệch ⇒ `409 STATE_CONFLICT`.

User chỉ có `PERM_PRODUCTION_QC_READ`:
- Đọc phiếu `APPROVED` ⇒ `200`.
- Đọc phiếu đã huỷ ⇒ `403`, `X-Trace-Id: 72c8a6e37b5846c5`:
  ```json
  {"code":"PERMISSION_DENIED","message":"Quality permissions read approved production receipts only"}
  ```
- List `status=DRAFT` ⇒ `200` và trang rỗng, `X-Trace-Id: 83cb94d466ab403c`:
  ```json
  {"code":"SUCCESS","result":{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"first":true,"last":true},"message":"OK"}
  ```
- List không lọc ⇒ chỉ hai phiếu `APPROVED`.
- Ứng viên ⇒ `403`.

---

## §5 — Envelope và hợp đồng lỗi

Envelope: `{code, result?, message, errors?}`.
- `result` vắng mặt khi lỗi; `errors` vắng mặt khi không có lỗi theo field.
- FE rẽ nhánh theo `code`, không theo HTTP status.
- Mọi response có header `X-Trace-Id`.

| Tình huống | HTTP | `code` thật | Field | Mã FE đề xuất (§4) |
|---|---|---|---|---|
| Thiếu version / ngày; số lẻ thứ 7; ngày sai; lý do ngoài 5–2000 | 400 | `VALIDATION_ERROR` | đúng tên field | `VALIDATION_ERROR` |
| Thiếu lý do khi rút/huỷ/từ chối | 400 | `APPROVAL_REASON_REQUIRED` | — | — |
| Hàng theo lô thiếu mã lô | 400 | `LOT_REQUIRED` | — | `LOT_REQUIRED` |
| Gửi `expiresOn` cho hàng không theo lô | 400 | `VALIDATION_ERROR` | `expiresOn` | `LOT_NOT_ALLOWED` |
| Mã lô đã có | 409 | `LOT_CODE_ALREADY_EXISTS` | `lotNumber` | như FE |
| Sai trạng thái (gửi khi không nháp, rút khi không chờ…) | 409 | `STATE_CONFLICT` | — | `RECEIPT_STATE_CONFLICT` |
| Vượt `U` (tạo/sửa) hoặc vượt `C` (duyệt) | 409 | `PLANNED_QUANTITY_EXCEEDED` | `quantity` | `PRODUCTION_OUTPUT_EXCEEDED` |
| Version lệch | 409 | `CONCURRENT_MODIFICATION` | — | `VERSION_CONFLICT` |
| Cùng key, khác body | 409 | `IDEMPOTENCY_CONFLICT` | — | như FE |
| Hạn lô đã qua từ lúc lưu (gửi/duyệt) | 409 | `LOT_NOT_ELIGIBLE` | `expiresOn` | `LOT_EXPIRED` |
| Kho khác kho đầu ra | 422 | `RESOURCE_SCOPE_MISMATCH` | `destinationWarehouseId` | `PRODUCTION_RECEIPT_SOURCE_CHANGED` |
| Kho/vật tư đã ngừng | 422 | `RESOURCE_INACTIVE` | — | `REFERENCE_INACTIVE` |
| Không phải người tạo; thiếu quyền; QC đọc phiếu chưa duyệt | 403 | `PERMISSION_DENIED` | — | như FE |
| ID không có / ngoài phạm vi | **403** | `PERMISSION_DENIED` | — | 404 `NOT_FOUND` — xem §6 |
| `X-Plant-Id` lệch | 409 | `STATE_CONFLICT` | — | 403 `PLANT_SCOPE_MISMATCH` |

---

## §6 — Quyền, phạm vi và giới hạn đã biết

| Quyền | Ai có (seed) | Mở gì |
|---|---|---|
| `PERM_PRODUCTION_RECEIPT_READ` *(mới)* | ADMIN, MANAGER | đọc mọi phiếu, ứng viên, lịch sử |
| `PERM_PRODUCTION_QC_READ` *(mới)* | ADMIN, MANAGER | đọc phiếu `APPROVED` |
| `PERM_PRODUCTION_RECEIPT_MANAGE` | như cũ | tạo/sửa/gửi/rút/huỷ (chỉ phiếu của mình) + đọc mọi phiếu |
| `PERM_PRODUCTION_RECEIPT_APPROVE` | như cũ | duyệt/từ chối + đọc mọi phiếu |
| `PERM_QUALITY_DISPOSITION` | như cũ | QC + đọc phiếu `APPROVED` |

OPERATOR **không** được cấp ngầm hai quyền mới.

**Giới hạn đã biết:**
1. **ID không tồn tại hoặc ngoài phạm vi trả `403`, không phải `404`.** Quyền được kiểm trước, khi chưa tìm thấy bản ghi để suy ra phạm vi. Không lộ dữ liệu. Đây là hành vi chung của toàn hệ thống, không đổi trong giai đoạn này.
2. **`POST /api/v1/inventory/adjust` vẫn chạy trên lô `HOLD`.** Vì vậy câu "lệnh kho chung không đi vòng QC" chỉ đúng với **đổi trạng thái lô**: lô `HOLD` của phiếu nhập không đổi trạng thái tay được. Đây là giới hạn có từ trước GĐ7.
3. **`postedAt` được ghi ngay lúc tạo nháp, không phải lúc ghi sổ.** Thời điểm hàng vào kho là `approvedAt`; ngày nghiệp vụ là `receivedOn`.
4. **Lịch sử chỉ giữ lần gửi/rút gần nhất** (§2.2).

---

## §7 — Bằng chứng nghiệm thu

| Hạng mục | Kết quả |
|---|---|
| `mvn -o clean verify` | **1968 unit + 274 IT, 0 lỗi** (trước phần này: 1913 + 269). Số cuối xem `CLAUDE.md §0.64` |
| Migration `V83` | Chạy sạch trên DB dev có dữ liệu demo. Test migration seed ở V82 để backfill chạy trên dòng thật, gồm một thời điểm có ngày UTC khác ngày giờ VN |
| Smoke HTTP thật | Ba lượt: `002951`, `004159`, `004655` — lượt cuối trên code giao, **45/45** |
| Lỗi do smoke tìm ra | Hai lỗi, đều sửa trước khi gửi (§1 điểm 3) |
| Nghiệm thu mutation | **15/15 bị giết**: trả lại phần giữ khi sửa; kiểm lại trần lúc duyệt; tăng `completedQuantity` trước biến động; chỉ người tạo; 409 vs 400 cho hạn quá; mã lô không phân biệt hoa thường; quyền QC chỉ thấy `APPROVED`; replay trước luật trạng thái; `allowedActions` theo người tạo; hạn lô theo giờ nhà máy; tạo phiếu tăng version lệnh (E2E); lọc ứng viên theo lệnh (IT); khoảng ngày gồm cả hai đầu (IT); tìm theo số lô của nháp (IT); sửa chỉ lượng vẫn tăng version phiếu |
| E2E | `ProductionFlowE2EIT` chạy luồng 11 bước qua HTTP với body mới; kiểm version lệnh **+1** sau tạo phiếu và sau duyệt |

---

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Phần v2, v3 (gửi tiếp):**
- **v2:** `contributions[]`, `outputEvidenceKnown`, `GET .../source` (R05, R13, R22).
- **v3:** `POST`/`GET .../qc-decisions`, `qualityState`, tổng đạt/loại/chờ, lô con, bộ lọc `qualityState`/`pendingQc`/`hasQcDecision`, `valuationStatus`. Endpoint QC cũ được nối vào sổ mới (R09–R11, R24).

**Hoãn hoặc giữ có chủ đích:**
- Giá trị thành phẩm (Q26).
- Cung tương lai cho MRP theo phần đã duyệt (Q32).
- Tách `fulfilled` khỏi QC (Q31).
- Đổi 403 → 404 (§6).
- Lô con / QC từng phần cho hàng theo số sê-ri.

**FE cần làm:**
1. Gửi `{expectedVersion, expectedWorkOrderVersion}` cho submit/approve, thêm vào reject. Thêm `expectedWorkOrderVersion` + `receivedOn` khi tạo.
2. Khoá ô kho nhập = `outputWarehouseId` của ứng viên.
3. Lấy `workOrderVersion` của response làm version cho lệnh kế tiếp — mỗi lệnh trên phiếu làm version lệnh tăng 1.
4. Bật/tắt nút theo `allowedActions`.
5. Chạy A01–A08, A15, A16 (phần mã lô), A20, A23, A28–A31, A34 bằng bản này. Các ca còn lại chờ v2/v3.

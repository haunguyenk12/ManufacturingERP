# BE báo cáo — Hoàn thành đợt follow-up Capstone 3 (CF-0 → CF-5)

**Gửi cho:** team FE · **Ngày gửi:** 28/10/2026 · **Bản:** v1
**Mức:** P1 · **Breaking:** CÓ (CF-1, CF-2, CF-4, CF-5 — bảng ở §3) · **Thứ tự deploy:** không ràng buộc

---

## §0 — Kết luận

BE đã xong **6/6 phase** của đợt này theo quyết định PO ngày 27/10/2026. Mỗi phase có một file trả lời chi tiết riêng (ví dụ gọi thật, mã lỗi, việc FE cần làm); file này chỉ là bản tổng hợp để FE nắm toàn cảnh và thứ tự ưu tiên.

| Phase | Nội dung | Mức | Breaking | File chi tiết |
|---|---|---|---|---|
| CF-0 | Tài liệu: bản đồ phiên bản, hợp đồng số thập phân, tỉ lệ BOM 1/3 | P2 | KHÔNG | `BE_RESPONSE_VERSION_MAP_2026-10-28.md`, `BE_RESPONSE_NUMERIC_CONTRACT_2026-10-28.md`, `BE_RESPONSE_BOM_RATIO_2026-10-28.md` |
| CF-1 | Chặn điều chỉnh tồn trên lô còn thuộc QC | P1 | CÓ, hẹp | `BE_RESPONSE_CF_1_2026-10-28.md` |
| CF-2 | Lô hết hạn bị loại khỏi MRP, giữ chỗ, cấp vật tư, readiness | P1 | CÓ | `BE_RESPONSE_CF_2_2026-10-28.md` |
| CF-3 | Tổng hợp tồn tách phần chờ / phần loại QC mua | P2 | KHÔNG | `BE_RESPONSE_CF_3_2026-10-28.md` |
| CF-4 | Hàng mua chờ QC hiện cạnh nhu cầu MRP | P2 | CÓ, hẹp | `BE_RESPONSE_CF_4_2026-10-28.md` |
| CF-5 | Một công thức lịch cho xem trước và phát hành lệnh SX | P1 | CÓ | `BE_RESPONSE_CF_5_2026-10-28.md` |

**Không làm (có chủ đích, PO chốt 27/10):** sổ giá trị tồn kho (CF-6) hoãn; BOM tỉ lệ cần quá 6 số lẻ như 1/3 (CF-7) — giữ giới hạn hiện tại.

Kèm theo: `BE_OPENAPI_2026-10-28.json` (256 path, xuất từ bản BE đang chạy, đã gồm CF-1 → CF-5).

## §1 — Từng phase đã làm gì

### CF-1 — Chặn điều chỉnh lô QC còn sở hữu
`POST /api/v1/inventory/adjust` trên lô còn phần chờ QC mua, còn phần chờ QC sản xuất, hoặc lô con bị QC loại ⇒ **409 `STATE_CONFLICT`**, không ghi biến động nào. Lô `HOLD` do người dùng tự đặt, lô con đã đạt và hàng không theo lô **không** bị chặn.

### CF-2 — Lô hết hạn rời khỏi hoạch định và luồng lệnh SX
Lô `AVAILABLE` đã quá hạn:
- bị loại khỏi tồn khả dụng của MRP và khỏi tự động giữ chỗ cho lệnh SX; được đếm vào `excludedLotCount`;
- giữ chỗ tay vào lô đó ⇒ **409 `LOT_NOT_ELIGIBLE`**; cấp/duyệt cấp vật tư từ lô đó cũng ⇒ 409;
- readiness và cổng phát hành trừ phần đang giữ trên lô không còn xuất được: thêm `ineligibleReservedQuantity` + `blocker`.

Giữ chỗ cũ **không** tự huỷ — FE gợi ý người dùng gọi `release-reservations`. Đo A/B cùng dữ liệu: bản cũ báo tồn 10, net 0, `COVERED`; bản mới tồn 0, net 10, `SHORTAGE`, `excludedLotCount` 1, 2 đề xuất.

### CF-3 — Tách vùng chờ QC mua
`GET /api/v1/inventory/balances/aggregate` thêm 4 field: `pendingPurchaseQcQuantity`, `rejectedPurchaseQcQuantity`, `unclassifiedQualityHoldQuantity`, `qualityBreakdownKnown`. Chỉ áp cho hàng **không theo lô**; phần chờ QC sản xuất không lô nằm ở `unclassifiedQualityHoldQuantity`. Thuần additive.

### CF-4 — Hàng mua chờ QC cạnh nhu cầu MRP
- Dòng nhu cầu của run thêm `pendingQcQuantity` + `pendingQcSourceCount` (0 khi không có; `null` = run chạy trước bản này).
- Endpoint mới `GET /api/v1/planning-runs/{runId}/pending-qc-supplies?requirementId=&warehouseId=` liệt kê các dòng phiếu nhận đang chờ.
- **Net không đổi** — hàng chờ QC chỉ để hiển thị. Đề xuất mua có hàng chờ QC chuyển `READY` → `WARNING` với thông điệp `PENDING_QC_SUPPLY`.
- Ảnh chụp lúc chạy run, không cập nhật khi QC quyết sau.

### CF-5 — Một công thức lịch
Phát hành lệnh SX, xem trước lịch của lệnh và điều chỉnh lịch nay dùng **cùng** cách xếp lịch với bản xem trước công suất nhà máy: chia thời gian theo `capacityUnits` của tổ, đi theo cửa sổ làm việc của lịch.
- Lệnh phát hành **mới** có thể ngắn hơn trước khi tổ có `capacityUnits > 1`; lệnh đã phát hành không đổi.
- Công đoạn thêm 3 field chụp lúc phát hành: `capturedCapacityUnits`, `requiredResourceMinutes`, `activeElapsedMinutes` (`null` với công đoạn phát hành trước đó).
- Đổi `capacityUnits` của tổ **sau** khi phát hành không làm đổi lịch đã chụp. Bảng tải công suất giữ nguyên.

### CF-0 — Tài liệu và hợp đồng số thập phân
- Số thập phân trên response luôn là **JSON number đủ chữ số** (ví dụ `9999999999999.999999`), không dạng mũ; FE cần parser giữ nguyên token số trước khi đưa vào Decimal.
- Request nhận cả số lẫn chuỗi số cho field thập phân; field số nguyên nhận `"2"`, từ chối `"1.5"` và `5.0` (400).
- BOM 1 KG → 3 PCS: form ⇒ 400 tại `lines[0].componentQuantity`; file nhập ⇒ batch `INVALID` (`INVALID_FORMAT`, cột F). PO giữ giới hạn 6 số lẻ.

## §2 — Điểm FE cần chú ý nhất

1. **Một nhóm field thập phân chưa chặn số lẻ thứ 7** (giá thành chuẩn, thiết lập kho, nhu cầu thủ công, nhà cung cấp–vật tư, đề nghị mua, các đường tạo cũ của PO/SO/lệnh SX, phút quy trình): gửi 7 số lẻ ⇒ 200, response lặp lại số chưa làm tròn nhưng DB lưu số đã làm tròn; quá 13 chữ số nguyên ⇒ 422 `BUSINESS_RULE_VIOLATION` không có `errors[]`. Danh sách đầy đủ ở `BE_RESPONSE_NUMERIC_CONTRACT_2026-10-28.md` §2.3. Tạm thời FE tự giới hạn 6 số lẻ / 13 chữ số nguyên ở form.
2. **Số MRP có thể đổi** khi kho có lô hết hạn (CF-2) — không phải lỗi.
3. **BE không có số phiên bản ứng dụng**; nhận diện bản BE bằng migration cao nhất (`92` = GĐ2–GĐ11, `93` = + CF-4, `94` = + CF-5). Chi tiết ở file bản đồ phiên bản.

## §3 — Breaking change

**`Wire: CÓ`**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|
| `POST /api/v1/inventory/adjust` | lô còn thuộc QC | 200 | 409 `STATE_CONFLICT` | Chặn mới | Hiển thị lỗi, hướng sang màn QC |
| Giữ chỗ tay, cấp / duyệt cấp vật tư | lô hết hạn | 200 | 409 `LOT_NOT_ELIGIBLE` | Chặn mới | Hiển thị lỗi |
| Phát hành lệnh SX | giữ chỗ nằm trên lô hết hạn | 200 | có thể 409 `BLOCKED` | Chặn mới | Đọc `blocker`, gợi ý `release-reservations` |
| Số MRP | kho có lô hết hạn | tồn tính cả lô hết hạn | không tính | Đổi số | Không cần sửa |
| Đề xuất mua | có hàng mua chờ QC | `READY` | `WARNING` + `PENDING_QC_SUPPLY` | Đổi giá trị | Hiển thị cảnh báo |
| Lịch lệnh SX phát hành mới | tổ có `capacityUnits > 1` | dài hơn | ngắn hơn | Đổi số | Không cần sửa |

Additive (không phá client): 4 field ở aggregate tồn (CF-3); 2 field dòng nhu cầu + 1 endpoint (CF-4); 3 field công đoạn (CF-5); 2 field readiness (CF-2).

## §4 — Cách nhận diện bản BE đang tích hợp

```sql
select max(version::int) from flyway_schema_history where success;
```

Kết quả `92` / `93` / `94` tương ứng bảng ở `BE_RESPONSE_VERSION_MAP_2026-10-28.md` §1. `GET /actuator/info` hiện trả `{}`.

## §5 — Envelope và hợp đồng lỗi

Không đổi: `{ "code", "result", "message", "errors" }`; FE rẽ nhánh theo `code`, không theo HTTP status. Mã mới/mở rộng dùng trong đợt này: `STATE_CONFLICT`, `LOT_NOT_ELIGIBLE`, `PENDING_QC_SUPPLY` (thông điệp cảnh báo, không phải lỗi HTTP).

## §6 — Quyền

Không thêm quyền mới. Endpoint `pending-qc-supplies` dùng quyền đọc run hoạch định hiện có.

## §7 — Bằng chứng nghiệm thu

| Loại | Kết quả |
|---|---|
| Test tự động | Toàn bộ test BE xanh, 0 lỗi (khoảng 2200 test đơn vị và 330 test tích hợp). Mỗi phase có kiểm đột biến: các thay đổi cố ý làm hỏng logic đều bị test bắt |
| HTTP thật | 20/20 kịch bản smoke đạt trên app chạy thật: CF-1 (4 probe), CF-2 (A/B cũ–mới + giữ chỗ/phát hành/readiness), CF-3, CF-4 (kể cả đối chứng đề xuất không có hàng chờ QC vẫn `READY`), CF-5 (xem trước = phát hành, đổi `capacityUnits` sau phát hành không đổi lịch) |
| Môi trường | profile dev, Postgres + Redis qua docker, đo 28/10/2026 (giờ VN) |

**Chưa nghiệm thu:** tranh chấp đồng thời giữa lệnh QC và `adjust` trên cùng lô chưa đo qua HTTP (chỉ có test tự động); ca hao hụt BOM `5.12345%` chưa đo qua HTTP; từng field trong bảng phạm vi số thập phân chưa đo riêng qua HTTP (dựng từ ràng buộc trong code và kiểu cột DB).

## §8 — Chưa làm + việc FE cần làm tiếp

| Mục | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Sổ giá trị tồn kho (CF-6) | PO hoãn | PO mở lại |
| BOM tỉ lệ cần quá 6 số lẻ, ví dụ 1/3 (CF-7) | PO chọn giữ giới hạn | PO đổi quyết định; BE đo tác động trước |
| Chặn số lẻ thứ 7 cho nhóm field ở §2 mục 1 | Là chặn mới | FE/PO đồng ý |
| Quy trình sửa lượng gốc của phiếu nhận đang chờ QC | Chưa có thiết kế audit | PO cần |

**FE cần làm tiếp**
- [ ] (bắt buộc) Bắt 409 `STATE_CONFLICT` ở màn điều chỉnh tồn, và 409 `LOT_NOT_ELIGIBLE` ở giữ chỗ tay / cấp vật tư.
- [ ] (bắt buộc) Readiness: đọc `ineligibleReservedQuantity` + `blocker`.
- [ ] (bắt buộc) Đọc response thập phân bằng parser giữ nguyên token số.
- [ ] (nên làm) Hiển thị 4 field mới ở tổng hợp tồn (CF-3), `pendingQcQuantity` + danh sách nguồn chờ QC ở run MRP (CF-4), 3 field chụp trên công đoạn (CF-5).
- [ ] (nên làm) Ẩn nút điều chỉnh trên lô đang chờ QC; giới hạn 6 số lẻ / 13 chữ số nguyên ở form cho nhóm field chưa chặn.

# BE báo cáo — Hoàn thành gói bổ sung CF ngày 30/10 (4 task)

02/11/2026 · Báo cáo các việc BE đã làm cho 4 task: `FE_QUESTION_GD06_GD07_CONTRACT_2026-10-30_v3`,
`FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT_2026-10-30_v2`, `FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-30_v3`,
`FE_REQUEST_COST_CLOSE_VERIFICATION_2026-10-30_v2`. Trả lời từng câu hỏi Q của FE nằm ở file
`BE_RESPONSE_CF_SUPPLEMENT_2026-11-02.md`; file này chỉ tóm tắt việc đã làm và bằng chứng.

## §0 — Kết luận

| Mục | Kết quả |
|---|---|
| Số task xong | 4/4 |
| Đổi hành vi, đổi contract, migration mới | **Không** |
| Lỗi mới tìm ra trong code chạy thật | **Không** |
| Breaking change | **KHÔNG** |
| Thứ tự deploy | Không ràng buộc |
| Mức | P1 |

Bốn task đều là bổ sung bằng chứng: gửi tài liệu, thêm test, đo lại HTTP. Mọi test và lượt đo mới đều khẳng định code
hiện tại đúng với quyết định của PO. Không có dòng code chạy thật nào bị sửa.

## §1 — Từng task đã làm gì

### Task 1 — Gửi contract và OpenAPI
- Đã nén đủ 11 file thành một ZIP (SHA-256 `05d6f4607c199b1945751924f333210230711cce858b7ca5eb1514c60ab5e1e2`).
  Đã kiểm không có mật khẩu hay token.
- Đã xuất OpenAPI trực tiếp từ binary mới và so với file 28/10: **giống từng byte** (SHA-256
  `220ee57728813005b48496f04cd1f358c12cc416a4defeb90a1d37c6198e95aa`). File 28/10 vẫn đúng cho bản hiện tại.
- OpenAPI không thể hiện giới hạn số thập phân. Dùng bảng 14 request / 20 field ở báo cáo CF 30/10 làm phụ lục.

### Task 2 — Test chuỗi hao hụt
3 ca mới đi qua các lệnh API thật: đặt giá chuẩn → tạo và xác nhận đơn bán → chạy kế hoạch MRP → duyệt đề xuất và chuyển
thành lệnh sản xuất → giữ chỗ → phát hành → đọc cơ sở chi phí. Không ca nào ghi trực tiếp lượng kỳ vọng vào dữ liệu.

| Ca | MRP (gộp, làm tròn lên 6 số lẻ) | Lệnh SX (HALF_UP 6) | Vật tư trong cơ sở chi phí |
|---|---|---|---|
| C01: 2 KG/PCS, hao hụt 0.1, đặt 10 | 22 (net 0) | 22 | 22000 |
| C02: 1.000001 KG/PCS, hao hụt 0.000001, đặt 10 | 10.000021 | 10.000020 | 10000.020000 |
| C03: 1.000001 KG/PCS, hao hụt 0.1, đặt 1.2 | 1.320002 | 1.320001 | 1320.001000 |

- Gross và net được kiểm riêng ở C01 (gross 22, net 0).
- Cơ sở chi phí lấy lượng của lệnh SX, **không nhân hao hụt lần hai**. Đổi giá chuẩn từ 1000 lên 2500 sau khi phát hành:
  cơ sở chi phí giữ nguyên.
- Chênh MRP và lệnh SX ở C02/C03 là 0.000001, đúng ngưỡng PO chốt, kiểm trên cùng dòng nguồn.
- Cố ý làm sai bốn chỗ (chia `1 − rate`, MRP làm tròn HALF_UP, nhân hao hụt lần hai ở cơ sở chi phí, lệnh SX làm tròn lên):
  cả bốn lần test đều đỏ.

### Task 3 — Hàng không theo lô đang chờ QC
12 ca mới. Mỗi ca dựng dữ liệu riêng: nhận 25, QC đạt 20 / loại 5 (tồn 25, chờ QC 5, khả dụng 20), có thêm biến thể "5 còn chờ,
chưa quyết".

| Lệnh | Vượt hàng tự do (21) | Trong hàng tự do (5) |
|---|---|---|
| Chuyển kho | `409 INSUFFICIENT_AVAILABLE_STOCK`, lỗi gắn `lines[0].quantity`, không ghi gì | WH1 còn tồn 20 / chờ 5 / khả dụng 15; WH2 nhận 5 |
| Giữ chỗ tay | `409`, không có giữ chỗ nào | Tồn 25 / giữ 5 / chờ 5 / khả dụng 15 |
| Giữ chỗ tự động | — | Cần 30, giữ đúng 20 (một phần), không đụng phần chờ |
| Xuất kho | `409`, không đổi gì | Tồn 20 / chờ 5 / khả dụng 15, đúng 1 biến động |

- Phiếu chuyển 2 dòng mà dòng 2 vi phạm: từ chối cả phiếu, không ghi dòng nào.
- Gửi lại cùng `Idempotency-Key`: chuyển kho, giữ chỗ và xuất kho đều không ghi lần hai.
- Giữ nguyên hành vi hiện hành: giữ chỗ tay "đủ hoặc không", giữ chỗ tự động được phép giữ một phần.
- Cố ý làm sai năm chỗ (bỏ phần chờ QC khỏi phép tính khả dụng ở từng lệnh): cả năm lần test đều đỏ.

### Task 4 — Smoke HTTP sau bản sửa version
Đo trên app chạy thật, binary `e46fae39a8fe36f0c76741405c2c82b35bb438ff` (chứa bản sửa version duyệt phiếu nhập). **21/21 đạt.**
- Duyệt phiếu nhập hàng **có lô** và **không lô**: version phiếu +1, version lệnh SX +1; đọc lại khớp; gửi lại cùng key không
  duyệt lần hai.
- Đường giá thành 29k trên dữ liệu mới: cơ sở chi phí 20000 + 6000 + 3000; tính thử `READY` với kế hoạch = thực tế = 29000,
  giá đơn vị 2900; chốt sai `expectedVersion` ⇒ `409`, không ghi gì; chốt đúng ⇒ lệnh `CLOSED`, đúng 1 bút toán lượng 0 / giá trị
  29000; tồn thành phẩm vẫn 10; gửi lại lệnh chốt trả đúng kết quả lần đầu, không thêm bút toán.

## §2 — Thay đổi contract: trước / sau

Không có. Có **một đính chính tài liệu** cho smoke 29/10:

| Chỗ đọc | Field | Giá trị sau khi chốt | Nghĩa |
|---|---|---|---|
| `GET /v1/work-orders/{id}/cost-runs` | `state` (từng run) | `"FINALIZED"` | Trạng thái của **run** giá thành. Chỉ có `PREVIEW` và `FINALIZED` |
| `GET /v1/work-orders/{id}/cost-runs/{runId}` | `status` | `"CLOSED"` | Trạng thái của **lệnh SX** |
| cùng endpoint | `readiness` | `"CLOSED"` | `INCOMPLETE` / `BLOCKED` / `READY` / `STALE` / `CLOSED` |

Bảng smoke 29/10 ghi "run status = CLOSED" là nhầm: đó là trạng thái lệnh SX. OpenAPI khai `state` là `string` thường (không
có danh sách giá trị); FE tự khai kiểu với hai giá trị trên.

## §3 — Breaking change

Không có.

## §4 — Nhận diện bản BE đang tích hợp

| Mốc | Commit | Build time | Jar SHA-256 | Flyway | Có bản sửa version? |
|---|---|---|---|---|---|
| Smoke 29/10 | `7cf3703eb99eeed9cd8296f743e33b9e4593a1d9` | 2026-10-29T15:50:55.463Z | `9f466935…338a00` | 95 | Không |
| Báo cáo 30/10 | `448000a60bc6d1b7e56b121da681943dfda456d1` | 2026-10-30T02:09:25.395Z | `0e259a89…fd6c91` | 95 | Có (chỉ kiểm bằng test tích hợp) |
| **Smoke 02/11** | `e46fae39a8fe36f0c76741405c2c82b35bb438ff` | 2026-11-02T07:52:02.830Z | `a46ad3deead57b9b8c31913c41fafa2546445cafa9dc2f6705c3cb92666ed3e3` | 95 | **Có** (đo HTTP) |

Bản `e46fae3` = bản `448000a` cộng chỉ test và tài liệu. Binary chạy smoke còn một thay đổi cấu hình cục bộ chưa commit: mức
log của profile dev; không đổi hành vi API. Nhận diện bản đang chạy bằng `app.commit` ở `/actuator/info` (cổng quản trị).

## §5 — Envelope và hợp đồng lỗi

Không đổi. Mã lỗi gặp trong các ca mới:
- `409 INSUFFICIENT_AVAILABLE_STOCK`: chuyển kho có `errors[]` theo dòng; xuất kho và giữ chỗ tay **không** có `errors[]`,
  FE rẽ nhánh theo `code`.
- `409 CONCURRENT_MODIFICATION`: version cũ.
- Gửi lại cùng `Idempotency-Key` trả `200`: duyệt phiếu nhập trả trạng thái **hiện tại** của phiếu; chốt giá thành trả
  **đúng** kết quả lần đầu.

## §6 — Quyền, phạm vi, giới hạn đã biết

- Test của task 2 và 3 không kiểm quyền theo tài khoản; quyền đã có test riêng từ các giai đoạn trước.
- Phiếu chuyển 2 dòng: dịch vụ kiểm hết các dòng trước khi ghi, nên ca này chứng minh "từ chối cả phiếu, không ghi gì".
  Trường hợp lỗi sau khi đã ghi một phần có test riêng từ giai đoạn 4.
- Giới hạn PO đã nêu vẫn còn, gói này không mở rộng: giá vốn hàng không lô sau khi đóng, đóng lệnh không có quy trình, giá
  thực tế của lệnh tương lai.

## §7 — Bằng chứng nghiệm thu

| Phần | Loại | Kết quả |
|---|---|---|
| Task 1 | Băm file | OpenAPI live của bản mới = file 28/10 |
| Task 2 | Test tích hợp đầu-cuối, Postgres thật | Lớp test có 11 ca (8 cũ + 3 mới), 0 lỗi |
| Task 3 | Test tích hợp, Postgres thật | 12 ca mới; cùng lớp QC cũ: 28 ca, 0 lỗi |
| Task 4 | HTTP trên app chạy thật | 21/21 đạt, 118 lượt gọi, có `X-Trace-Id` từng lượt |

**Chưa chạy:** toàn bộ test suite sau commit này (chỉ chạy các lớp test liên quan). Các lớp khác không bị đụng tới.

## §8 — Chưa làm + việc FE cần làm tiếp

**Chưa làm, có chủ đích:**
- A39/A40 qua HTTP (hai ca này chỉ có test tích hợp; nằm ngoài phạm vi task).
- Lô hết hạn (CF-2) và công suất 2 tổ (CF-5) qua HTTP.
- Ca riêng cho phiếu xuất vật tư của lệnh SX trên hàng không lô đang chờ QC. Phần xuất từ hàng tự do dùng chung bộ kiểm với
  xuất kho thường; phần lấy từ giữ chỗ đã được bảo vệ ngay lúc giữ chỗ.
- Tổng giá thành 31000 (có nhân công và chi phí chung) trong chuỗi hao hụt: ca test không đặt đơn giá giờ nên chỉ kiểm vật tư.

**FE cần làm:**
1. Đọc trạng thái run giá thành từ `state` của `GET …/cost-runs` (`PREVIEW` / `FINALIZED`), không đọc từ `status` của
   `GET …/cost-runs/{runId}`.
2. Dùng ZIP 11 file và OpenAPI 28/10 làm contract cho binary `e46fae3`.
3. Ghi smoke mới gắn với binary `e46fae3`, tách khỏi smoke 29/10.
4. Bắt `409 INSUFFICIENT_AVAILABLE_STOCK` theo `code` ở xuất kho và giữ chỗ tay.

**File đi kèm:** `BE_RESPONSE_CF_SUPPLEMENT_2026-11-02.md`, `BE_CONTRACT_ARTIFACTS_2026-10-28.zip`,
`evidence-R0211-145453.jsonl`, `results-R0211-145453.json`.

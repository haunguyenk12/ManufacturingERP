# BE trả lời — Gói bổ sung CF ngày 30/10 (4 task)

02/11/2026 · Trả lời 4 file trong gói bổ sung 30/10:
`FE_QUESTION_GD06_GD07_CONTRACT_2026-10-30_v3`, `FE_REQUEST_SCRAP_ALLOWANCE_ALIGNMENT_2026-10-30_v2`,
`FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-30_v3`, `FE_REQUEST_COST_CLOSE_VERIFICATION_2026-10-30_v2`.

## §0 — Kết luận

Cả 4 task đã xong. Không có thay đổi hành vi, không có thay đổi contract, không có migration mới. Các test và lượt
smoke mới không tìm ra lỗi nào trong code chạy thật.

| Task | Loại | Kết quả |
|---|---|---|
| 1. Contract + OpenAPI | Gửi tài liệu | Gửi đủ 11 file trong một ZIP; OpenAPI áp dụng nguyên vẹn cho binary mới |
| 2. Chuỗi hao hụt BOM → MRP → lệnh SX → giá thành | Test bổ sung | 3 ca mới (C01/C02/C03), số khớp quyết định PO 30/10 |
| 3. Hàng không theo lô đang chờ QC | Test bổ sung | 12 ca mới: chuyển kho, giữ chỗ tay, giữ chỗ tự động, xuất kho đều không lấy vào phần chờ QC |
| 4. Smoke sau bản sửa version | Đo HTTP thật | 21/21 PASS trên binary `e46fae3`, chứa bản sửa |

**Breaking change: KHÔNG.** Thứ tự deploy: không ràng buộc.

## §1 — Từng task

### Task 1 — Contract và OpenAPI (trả lời Q1–Q3)

- **Q1 — Có.** File `BE_CONTRACT_ARTIFACTS_2026-10-28.zip` chứa đúng 11 file (SHA-256 của ZIP:
  `05d6f4607c199b1945751924f333210230711cce858b7ca5eb1514c60ab5e1e2`). Đã kiểm: không có mật khẩu hay token; chữ
  `password` trong OpenAPI chỉ là tên field của schema đăng nhập.

| File | Kích thước (byte) | SHA-256 (16 ký tự đầu) |
|---|---|---|
| BE_RESPONSE_GD_06_2026-10-17.md | 28443 | 4f520fbfc6fd4a2b |
| BE_RESPONSE_GD_06_2026-10-17_v2.md | 31268 | 6ecc0a46520f78ad |
| BE_RESPONSE_VERSION_MAP_2026-10-28.md | 7535 | dd9bd5f030a88a00 |
| BE_RESPONSE_NUMERIC_CONTRACT_2026-10-28.md | 11985 | 2ee6200925b57aeb |
| BE_RESPONSE_BOM_RATIO_2026-10-28.md | 7388 | 3a11787eb38536e7 |
| BE_RESPONSE_CF_1_2026-10-28.md | 9544 | a4232e67dd62568e |
| BE_RESPONSE_CF_2_2026-10-28.md | 12711 | 1cc0fe352bf167be |
| BE_RESPONSE_CF_3_2026-10-28.md | 6570 | ac0d49d00c567497 |
| BE_RESPONSE_CF_4_2026-10-28.md | 10612 | 3e75584a37fca7d1 |
| BE_RESPONSE_CF_5_2026-10-28.md | 14558 | 520edcf1b9e29b39 |
| BE_OPENAPI_2026-10-28.json | 477732 | 220ee57728813005 |

- **Q2 — Có.** OpenAPI xuất trực tiếp từ binary mới (`e46fae3`, đã có bản sửa version) có SHA-256
  `220ee57728813005b48496f04cd1f358c12cc416a4defeb90a1d37c6198e95aa`, giống từng byte file 28/10. Lý do: từ 28/10 tới nay
  không có thay đổi nào làm đổi schema. OpenAPI **không** thể hiện `@Digits`; giới hạn số thập phân lấy từ bảng 14 request /
  20 field ở `BE_REPORT_CF_COMPLETED_2026-10-30.md §2`, cộng giới hạn đã có ở `BE_RESPONSE_NUMERIC_CONTRACT_2026-10-28.md`.
- **Q3 — Có.** Bảng mốc binary:

| Mốc | Commit | Build time | Jar SHA-256 | Flyway | Có bản sửa version? |
|---|---|---|---|---|---|
| Smoke 29/10 | `7cf3703eb99eeed9cd8296f743e33b9e4593a1d9` | 2026-10-29T15:50:55.463Z | `9f466935…338a00` | 95 | **Không** |
| Bản cuối báo cáo 30/10 | `448000a60bc6d1b7e56b121da681943dfda456d1` | 2026-10-30T02:09:25.395Z | `0e259a89…fd6c91` | 95 | Có — chỉ kiểm bằng test tích hợp |
| **Smoke 02/11 (bản này)** | `e46fae39a8fe36f0c76741405c2c82b35bb438ff` | 2026-11-02T07:52:02.830Z | `a46ad3deead57b9b8c31913c41fafa2546445cafa9dc2f6705c3cb92666ed3e3` | 95 | **Có** — đo HTTP |

`e46fae3` là `448000a` cộng **chỉ** commit test và tài liệu, không đổi code chạy. Binary chạy smoke có thêm một
thay đổi cấu hình cục bộ chưa commit: mức log của profile dev (tắt in SQL). Thay đổi này không đổi hành vi API.

### Task 2 — Chuỗi hao hụt (trả lời Q1–Q3)

Test nằm trong bộ test tích hợp đầu-cuối của BE. Đi qua đúng các lệnh thật qua HTTP: đặt giá chuẩn → tạo và xác nhận đơn bán
→ `POST /v1/planning-runs` → duyệt và `convert-to-work-order` → giữ chỗ → release → `GET /v1/work-orders/{id}/cost-baseline`.
Không ghi trực tiếp lượng kỳ vọng nào vào dữ liệu; vật tư, BOM, quy trình và tồn đầu được dựng sẵn làm nền.

| Ca | Đầu vào | MRP `grossRequiredQuantity` | Lệnh SX `componentLines[].requiredQuantity` | Baseline `materials[].plannedAmount` |
|---|---|---|---|---|
| C01 | 2 KG/PCS, hao hụt 0.1, đặt 10 | **22** (net 0 — kiểm riêng gross và net) | **22** | **22000** (22 × 1000) |
| C02 | 1.000001 KG/PCS, hao hụt 0.000001, đặt 10 | **10.000021** (làm tròn lên) | **10.000020** (HALF_UP) | **10000.020000** — lấy từ lệnh SX, không lấy từ MRP |
| C03 | 1.000001 KG/PCS, hao hụt 0.1, đặt 1.2 | **1.320002** | **1.320001** | **1320.001000** |

- **Q1 — Có.** Mỗi ca kiểm liên kết nguồn xuyên các bước:
  - Dòng nhu cầu RM trỏ dòng nhu cầu thành phẩm. Đề xuất MAKE trỏ đúng dòng thành phẩm đó.
  - Lệnh SX mang `planningRunId`, `planningProposalId` và `bomId` của BOM nguồn.
  - Dòng vật tư của lệnh mang `bomLineId` của BOM, cùng `quantityPer`/`scrapRate`.
  - Dòng vật tư của baseline trỏ đúng `componentLineId` của lệnh.
  - Đơn vị là KG ở cả ba nơi.
- **Q2 — Có.** Test giữ quyết định PO 30/10: MRP làm tròn lên 6 số lẻ, lệnh SX HALF_UP 6. Test kiểm |MRP − lệnh SX| ≤ 0.000001
  trên cùng dòng nguồn, **không** kiểm bằng nhau.
- **Q3 — Có.** Đã thử bốn lỗi cố ý; cả bốn đều làm test đỏ:

| Lỗi cố ý | Test đỏ |
|---|---|
| Lệnh SX chia `(1 − rate)` thay vì nhân `(1 + rate)` | C01, C03 |
| MRP làm tròn HALF_UP thay vì lên | C02, C03 |
| Baseline nhân hao hụt **lần hai** | C01, C02, C03 |
| Lệnh SX làm tròn lên thay vì HALF_UP | C02, C03 |

  C02 không bắt được lỗi thứ nhất, vì với hao hụt 0.000001 hai công thức cho cùng kết quả ở 6 số lẻ. C01 và C03 bắt được.
- **R04:** sau release, đổi giá chuẩn RM từ 1000 thành 2500. Baseline vẫn giữ nguyên `baselineId`, đơn giá 1000 và 22000.
- MRP chỉ có một field lượng gộp, `grossRequiredQuantity`. Field này đã mang giá trị làm tròn lên 6 số lẻ; không có field trung
  gian độ chính xác cao hơn.
- Cấu hình test:
  - Không có quy tắc gom lô ở MRP hay lúc chuyển đổi.
  - Thành phẩm không có tồn và tồn an toàn bằng 0, nên lượng kế hoạch = lượng đặt.
  - Không đặt đơn giá giờ, nên chỉ kiểm phần vật tư. Tổng 31000 trong ví dụ của FE không được kiểm ở task này.

### Task 3 — Hàng không theo lô đang chờ QC (trả lời Q1–Q3)

Mỗi ca dựng lại dữ liệu từ đầu: vật tư không theo lô nhận 25 vào vùng chờ, QC đạt 20 / loại 5. Kết quả trước khi chạy lệnh:
tồn 25, chờ QC 5, khả dụng 20. Có thêm biến thể **5 còn chờ, chưa quyết** (đạt 20 / loại 0) cho cả ba loại lệnh.

| Ca | Lệnh | Kết quả |
|---|---|---|
| T01 (+ biến thể còn chờ) | Chuyển 21 từ WH1 sang WH2 | `409 INSUFFICIENT_AVAILABLE_STOCK`, lỗi gắn field `lines[0].quantity`. Không tạo phiếu chuyển, không biến động, WH2 không có dòng tồn |
| T02 | Chuyển 5 | WH1: tồn 20 / chờ 5 / khả dụng 15. WH2 nhận 5 tự do. Đúng 2 biến động. Gửi lại cùng key ⇒ cùng `transferId`, không ghi thêm |
| R05 | Phiếu chuyển 2 dòng, dòng 2 lấy vào phần chờ | Từ chối cả phiếu, lỗi chỉ gắn `lines[1].quantity`. Dòng 1 (vật tư khác) không đổi, không có biến động nào |
| H01 (+ biến thể còn chờ) | Giữ chỗ tay 21 cho lệnh SX hợp lệ (`RELEASED`, cần 30) | `409 INSUFFICIENT_AVAILABLE_STOCK`. Không có giữ chỗ nào, version lệnh không đổi |
| H02 | Giữ chỗ tay 5 | Tồn 25 / giữ 5 / chờ 5 / khả dụng 15. Gửi lại cùng key ⇒ cùng `reservationId` |
| Giữ chỗ tự động (+ biến thể còn chờ) | Lệnh cần 30 | Giữ đúng 20 (một phần, đúng contract). Phần chờ không bị đụng |
| I01 (+ biến thể còn chờ) | Xuất kho 21 | `409 INSUFFICIENT_AVAILABLE_STOCK`. Không đổi gì |
| I02 | Xuất kho 5 | Tồn 20 / chờ 5 / khả dụng 15. Đúng 1 biến động `ISSUE`. Gửi lại cùng key ⇒ cùng `movementId` |

Mọi ca bị từ chối còn kiểm thêm: version dòng tồn không đổi, số biến động không đổi, và nguồn QC không đổi (nhận 25, đạt 20,
cùng số quyết định QC).

- **Q1:** Bộ test kiểm đếm / điều chỉnh của CF-12 được giữ nguyên, không viết lại. 12 ca trên là phần còn thiếu.
- **Q2 — Có.**
  - Mỗi ca dùng dữ liệu riêng.
  - Đường xuất vật tư cho lệnh SX dùng **chung** bộ kiểm tồn với đường xuất kho thường cho phần lấy từ hàng tự do (cả lúc
    ghi phiếu lẫn lúc duyệt).
  - Phần lấy từ giữ chỗ chỉ kiểm theo lượng đã giữ. Phần chờ QC vẫn được bảo vệ ở đó, vì chính lượt giữ chỗ đã bị chặn như
    ca H01.
  - Lớp test này **không** có ca riêng cho phiếu xuất vật tư của lệnh SX.
- **Q3 — Có.** Giữ chỗ tay vẫn "đủ hoặc không gì"; giữ chỗ tự động vẫn được phép giữ một phần. Không đổi hành vi nào.
- Năm lỗi cố ý (bỏ phần chờ QC khỏi phép tính khả dụng ở từng lệnh) đều làm đúng các ca liên quan đỏ. Khi lớp kiểm của dịch
  vụ bị gỡ, ràng buộc của DB vẫn chặn, nhưng trả lỗi chung thay vì `409` rõ ràng. Lớp dịch vụ là thứ cho ra mã lỗi đúng.
- Giới hạn của R05: dịch vụ chuyển kho kiểm hết mọi dòng **trước** khi ghi. Vì vậy ca này chứng minh "cả phiếu bị từ chối,
  không ghi gì". Trường hợp lỗi xảy ra **sau** khi đã ghi một phần có test riêng từ GĐ4.

### Task 4 — Smoke HTTP sau bản sửa (trả lời Q1–Q3)

Chạy ngày 02/11/2026 trên dữ liệu mới, hậu tố `R0211-145453`. Cổng ứng dụng 8090, cổng quản trị 8091, profile dev.

- **Q1 — Có.** `/actuator/info` của đúng tiến trình chạy smoke trả `app.commit` = `e46fae3…`. Commit này chứa bản sửa `448000a`.
- **Q2 — Có.** Transcript mới nằm ở `evidence-R0211-145453.jsonl` (118 lượt gọi). Transcript 29/10 giữ nguyên, không ghi đè.
- **Q3 — Trả lời ở §2 bên dưới** (đọc từ GET thật và OpenAPI thật, không suy từ văn bản).

| Yêu cầu | Ca | Kỳ vọng | Thực tế | HTTP / code | X-Trace-Id |
|---|---|---|---|---|---|
| R02 có lô | Duyệt phiếu nhập | Version phiếu +1, version lệnh +1 | Phiếu 1→2 `APPROVED`, lệnh 8→9 | 200 SUCCESS | `b2011daef76f45f9` |
| R02 có lô | Đọc lại | Khớp | Phiếu 2, lệnh 9, `completedQuantity` 5 | 200 | `4706b860caa14a34` |
| R02 có lô | Gửi lại cùng key và body | Không duyệt lần hai | 200, trả trạng thái hiện tại (2 / 9). Vẫn 1 biến động `RECEIVE` | 200 SUCCESS | `c6c8eed6f43b4e1c` |
| R02 không lô | Duyệt | +1 / +1 | Phiếu 1→2, lệnh 8→9 | 200 SUCCESS | `7a6e6ffb32d14a74` |
| R02 không lô | Đọc lại | Khớp | 2 / 9 | 200 | `177b023a89cb4ce4` |
| R02 không lô | Gửi lại | Không duyệt lần hai | 200, 2 / 9. Vẫn 1 biến động | 200 SUCCESS | `37578db3a54244f0` |
| R03 | Tồn thành phẩm trước release | 0 | 0 | 200 | `5aef254a6d4f446a` |
| R03 | Baseline lúc release | 20000 + 6000 + 3000 | 20000 / 6000 / 3000 / 29000, đơn giá 1000 | 200 | `31d295c2fd824894` |
| R03 | Tính thử | `READY`, kế hoạch = thực tế = 29000, giá đơn vị 2900; version lệnh không đổi | Đúng như kỳ vọng, version lệnh vẫn 10 | 200 | `2df1031d7efb40f8` |
| R04 | Chốt với `expectedVersion` sai | 409, không ghi gì | 409; lệnh vẫn `COMPLETED` v10, run vẫn `PREVIEW` v1, 0 bút toán | 409 CONCURRENT_MODIFICATION | `34a9c9e4280e49ed` |
| R03 | Chốt giá thành và đóng lệnh | Version lệnh +1 | `runVersion` 2, `workOrderVersion` 11 | 200 | `bd2ce176c8a1446b` |
| R03 | Bút toán sau chốt | 1 dòng, lượng 0, giá trị 29000 | `COST_ADJUSTMENT`, lượng 0, `stockValueDelta` 29000 | 200 | `6d78c66ed96b406d` |
| R03 | Tồn thành phẩm sau chốt | 10, không đổi | 10 | 200 | `efdf5623949742b6` |
| R04 | Gửi lại lệnh chốt, cùng key và body | Cùng kết quả, không thêm bút toán | 200, kết quả giống lần đầu; vẫn 1 bút toán; lệnh v11 `CLOSED` | 200 SUCCESS | `fd9631b364654ed3` |

Phiếu nhập thứ ba (không lô) của bộ dữ liệu 29k cũng tăng version đúng +1 / +1 (`4650c701bba14f01`).

Mã bản ghi:

| Bản ghi | ID |
|---|---|
| Lệnh SX 29k | `d01f7952-0d9b-4eee-a08e-f21d2d8769cf` |
| Run giá thành | `bca5ddd2-cc4e-4999-9caf-02bd499b46d4` (CST-8EBBDA5C) |
| Bút toán | `255cafcd-c901-48f1-ba8b-087a6ab78a9d` |
| Phiếu nhập có lô | `cf7b4c27-870d-41a4-a342-2c22d28affef` |
| Phiếu nhập không lô | `67cce382-e0ef-4aa9-941d-5f8244793015` |

**R05 — phân loại bằng chứng:**
- A39/A40 vẫn **chỉ có test tích hợp**: thu hồi quyền giữa lúc chốt, và lỗi giữa chừng rồi gửi lại. Hai ca này không đo qua HTTP.
- Smoke 7/7 ngày 29/10 vẫn là của bản `7cf3703`, trước bản sửa; không đổi nhãn thành bản mới.
- Hai ca lô hết hạn (CF-2) và công suất 2 tổ (CF-5) vẫn chưa đo qua HTTP; chỉ có test tự động.

## §2 — Thay đổi contract: trước / sau

Không có thay đổi contract. Riêng **R06**, mô tả cũ cần đính chính:

| Chỗ đọc | Field | Giá trị sau khi chốt | Nghĩa |
|---|---|---|---|
| `GET /v1/work-orders/{id}/cost-runs` | `state` của từng run | **`"FINALIZED"`** | Trạng thái của **run** giá thành |
| `GET /v1/work-orders/{id}/cost-runs/{runId}` | `status` | `"CLOSED"` | Trạng thái của **lệnh SX** |
| cùng endpoint trên | `readiness` | `"CLOSED"` | Độ sẵn sàng chốt (`INCOMPLETE`/`BLOCKED`/`READY`/`STALE`/`CLOSED`) |
| cùng endpoint trên | `closed.finalRunId` | id của run đã chốt | Run cuối cùng |

- Bảng smoke 29/10 ghi "run status = CLOSED". Thực ra đó là trạng thái của **lệnh SX** lấy từ field `status`, không phải trạng
  thái của run. Trạng thái của run nằm ở field `state` của endpoint danh sách.
- Run chỉ có hai giá trị: `PREVIEW` và `FINALIZED`.
- OpenAPI khai `state` là `string` thường, không có danh sách giá trị, vì phía server lưu nó là hằng chuỗi chứ không phải enum.
  FE nên tự khai kiểu với đúng hai giá trị trên.

## §3 — Breaking change

Không có.

## §4 — Ví dụ copy từ lần gọi thật

Toàn bộ request và response của task 4 nằm ở `evidence-R0211-145453.jsonl`, mỗi dòng một lượt gọi. Header `Authorization` đã
che thành `Bearer ***`; body đăng nhập và token đã bị xoá. Bảng tóm tắt các ca nằm ở `results-R0211-145453.json`.

## §5 — Envelope và hợp đồng lỗi

Không đổi. Mã lỗi gặp trong các ca trên:
- **`409 INSUFFICIENT_AVAILABLE_STOCK`** khi vượt hàng tự do. Chuyển kho có `errors[]` theo từng dòng (`lines[i].quantity`).
  Xuất kho và giữ chỗ tay **không** có `errors[]` — FE rẽ nhánh theo `code`.
- **`409 CONCURRENT_MODIFICATION`** khi version cũ.
- Gửi lại cùng `Idempotency-Key` trả `200`:
  - Duyệt phiếu nhập trả trạng thái **hiện tại** của phiếu.
  - Chốt giá thành trả **đúng** kết quả lần đầu.

## §6 — Quyền, phạm vi, giới hạn đã biết

- Test tích hợp của task 2 và 3 chạy ở tầng dịch vụ hoặc HTTP nội bộ, **không** kiểm quyền theo từng tài khoản. Quyền đã có
  test riêng từ các GĐ trước.
- Giới hạn đã nêu trong gói 30/10 vẫn còn, gói này không mở rộng: giá vốn hàng không lô sau khi đóng, đóng lệnh không có quy
  trình, giá thực tế của lệnh tương lai.

## §7 — Bằng chứng nghiệm thu

| Phần | Loại | Kết quả |
|---|---|---|
| Task 2 | Test tích hợp đầu-cuối (Postgres thật) | Lớp test đầu-cuối 11 ca (8 cũ + 3 mới), 0 lỗi; 4/4 lỗi cố ý làm test đỏ |
| Task 3 | Test tích hợp (Postgres thật) | 12 ca mới + 16 ca QC cũ = 28, 0 lỗi; 5/5 lỗi cố ý làm test đỏ |
| Task 4 | HTTP trên app chạy thật | 21/21 PASS, binary `e46fae3` |
| Task 1 | Băm file | OpenAPI live của binary mới = file 28/10 (cùng SHA-256) |

Commit BE chứa hai bộ test mới: `e46fae3`. Không có thay đổi code chạy. Chưa chạy lại toàn bộ test suite sau commit này: các
lớp đã chạy đều xanh, còn các lớp khác không bị đụng tới.

## §8 — Chưa làm + việc FE cần làm tiếp

**Chưa làm, có chủ đích:**
- A39/A40 qua HTTP (ngoài phạm vi).
- Lô hết hạn và công suất 2 tổ qua HTTP.
- Ca riêng cho phiếu xuất vật tư của lệnh SX trên hàng không lô đang chờ QC.
- Tổng giá thành 31000 có nhân công / chi phí chung trong chuỗi hao hụt.

**FE cần làm:**
1. Đọc trạng thái run giá thành từ field `state` của `GET …/cost-runs` (giá trị `PREVIEW` / `FINALIZED`). Đừng đọc từ
   `status` của `GET …/cost-runs/{runId}` — field đó là trạng thái lệnh SX.
2. Dùng ZIP 11 file và OpenAPI 28/10 làm contract cho binary `e46fae3`.
3. Ghi nhận smoke mới gắn với binary `e46fae3`, tách khỏi smoke 29/10.
4. Bắt `409 INSUFFICIENT_AVAILABLE_STOCK` theo `code` ở xuất kho và giữ chỗ tay (không có `errors[]`).

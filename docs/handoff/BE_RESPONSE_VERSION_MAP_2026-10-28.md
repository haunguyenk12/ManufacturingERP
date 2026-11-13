# BE trả lời — Bản đồ phiên bản các file trả lời GĐ2 → GĐ11

**Trả lời cho:** `CAPSTONE3_BE_FOLLOWUP_2026-10-20/` — câu hỏi "GĐ6/GĐ7 dùng bản nào" · **Ngày gửi:** 28/10/2026 · **Bản:** v1
**Mức:** P2 (tài liệu, không đổi code) · **Breaking:** KHÔNG · **Thứ tự deploy:** không ràng buộc

---

## §0 — Kết luận

Mỗi giai đoạn có **một** bộ file trả lời đang có hiệu lực, liệt kê ở §2. Quy tắc đọc:

- **Chỉ GĐ2 có bản thay thế:** `BE_RESPONSE_GD_02_2026-10-14_v2.md` **thay hoàn toàn** v1 (khác đúng một điểm: số có dấu chấm vào field số nguyên nay trả 400). Bỏ v1.
- **Mọi `_v2`, `_v3`, `_v4` khác là phần bổ sung**, không thay bản trước. Ví dụ GĐ8 cần đọc đủ v1 + v2 + v3 + v4 — mỗi bản là một phần của giai đoạn đó (nháp lệnh, cấp vật tư, công đoạn, dở dang).
- **Ngày trong tên file là ngày bắt đầu giai đoạn đó, không phải ngày gửi** — ba chỗ lệch ở §3.
- `BE_RESPONSE_G7-11.zip` chứa **đúng 14 file** của GĐ7 → GĐ11, trùng tên và nội dung với các file rời cùng tên. Không thiếu, không thừa. GĐ2 → GĐ6 không nằm trong zip.

## §0b — Trả lời ba câu hỏi

| # | FE hỏi | Trả lời |
|---|---|---|
| Q1 | Gửi lại bốn phản hồi còn thiếu (GĐ6 v1, GĐ6 v2, GĐ7 v1, GĐ7 v2) hoặc bản hợp nhất? | **Có — gửi lại nguyên bốn file** kèm file này: `BE_RESPONSE_GD_06_2026-10-17.md`, `BE_RESPONSE_GD_06_2026-10-17_v2.md`, `BE_RESPONSE_GD_07_2026-10-18.md`, `BE_RESPONSE_GD_07_2026-10-18_v2.md`. Tên đúng như FE nêu, nội dung không sửa so với bản đã gửi lần đầu |
| Q2 | Hai bản v3 (GĐ6, GĐ7) là phụ lục, không thay v1/v2? | **Có.** Mọi `_v2`/`_v3`/`_v4` trừ GĐ2 v2 là phần bổ sung (§2) |
| Q3 | OpenAPI + một phép nhận diện bản? | **Có.** File `BE_OPENAPI_2026-10-28.json` (256 path) xuất từ bản đang chạy; phép nhận diện ở §1. Bản OpenAPI này **mới hơn** GĐ11: có thêm `CF-1`→`CF-5` (gửi riêng trong các file `BE_RESPONSE_CF_*_2026-10-28.md`) |

## §1 — Nhận diện bản BE đang chạy

BE không có số phiên bản ứng dụng: `/actuator/info` (cổng quản trị, mặc định 8081, **không** có tiền tố `/api`) mở công khai nhưng trả rỗng — đo thật 28/10/2026 17:09: `GET http://localhost:8081/actuator/info` ⇒ `200` body `{}`. Dấu nhận diện đáng tin là migration cao nhất đã chạy:

```sql
select max(version::int) from flyway_schema_history where success;
```

| Kết quả | Bản BE có đủ |
|---|---|
| `92` | GĐ2 → GĐ11 (bản FE đang nghiệm thu) |
| `93` | + `CF-4` (hàng mua chờ QC cạnh nhu cầu MRP) |
| `94` | + `CF-5` (một công thức lịch cho xem trước và release) |

`CF-1`, `CF-2`, `CF-3` không có migration — chúng nằm giữa `92` và `93`, nên `92` không phân biệt được có hay chưa có ba phase đó; hỏi BE commit đang chạy nếu cần.

## §2 — Bảng phiên bản

| GĐ | File (theo thứ tự đọc) | Phần | Ngày gửi | Migration | Quan hệ |
|---|---|---|---|---|---|
| 2 | `BE_RESPONSE_GD_02_2026-10-14_v2.md` | Tổ chức + danh mục nền | 14/10 | `V69`, `V70` | **thay** v1 |
| 3 | `BE_RESPONSE_GD_03_2026-10-14.md` | Nhập Excel dữ liệu nền | 14/10 | `V71` | — |
| 4 | `BE_RESPONSE_GD_04_2026-10-15.md` | Tồn kho, lô, chuyển kho | 15/10 | `V72` | — |
| 5 | `BE_RESPONSE_GD_05_2026-10-16.md` | BOM, quy trình, công suất | 16/10 | `V73` | — |
| 6 | `BE_RESPONSE_GD_06_2026-10-17.md` | 1/3 đơn bán + giữ tồn | 17/10 | `V74` | — |
| 6 | `BE_RESPONSE_GD_06_2026-10-17_v2.md` | 2/3 hoạch định + chuyển đổi | **18/10** | `V75` | bổ sung |
| 6 | `BE_RESPONSE_GD_06_2026-10-17_v3.md` | 3/3 nhập đơn bán Excel | **18/10** | — | bổ sung |
| 7 | `BE_RESPONSE_GD_07_2026-10-18.md` | 1/3 vòng đời đơn mua | 18/10 | `V76` | — |
| 7 | `BE_RESPONSE_GD_07_2026-10-18_v2.md` | 2/3 phiếu nhận, vùng chờ kiểm | 18/10 | `V77` | bổ sung |
| 7 | `BE_RESPONSE_GD_07_2026-10-18_v3.md` | 3/3 QC mua theo lượng | **19/10** | `V78` | bổ sung |
| 8 | `BE_RESPONSE_GD_08_2026-10-19.md` | 1/4 nháp lệnh SX | 19/10 | `V79` | — |
| 8 | `BE_RESPONSE_GD_08_2026-10-19_v2.md` | 2/4 cấp vật tư | 19/10 | `V80` | bổ sung |
| 8 | `BE_RESPONSE_GD_08_2026-10-20_v3.md` | 3/4 công đoạn | 20/10 | `V81` | bổ sung |
| 8 | `BE_RESPONSE_GD_08_2026-10-20_v4.md` | 4/4 dở dang + hoàn vật tư | 20/10 | `V82` | bổ sung |
| 9 | `BE_RESPONSE_GD_09_2026-10-21.md` | 1/3 vòng đời phiếu nhập | 21/10 | `V83` | — |
| 9 | `BE_RESPONSE_GD_09_2026-10-23_v2.md` | 2/3 phân bổ theo lượt báo | 23/10 | `V84` | bổ sung |
| 9 | `BE_RESPONSE_GD_09_2026-10-23_v3.md` | 3/3 QC theo lượng | 23/10 | `V85` | bổ sung |
| 10 | `BE_RESPONSE_GD_10_2026-10-25.md` | 1–2/4 giá trên đơn, giao hàng | 25/10 | `V86`, `V87` | — |
| 10 | `BE_RESPONSE_GD_10_2026-10-26_v2.md` | 3/4 hoá đơn | 26/10 | `V88` | bổ sung |
| 10 | `BE_RESPONSE_GD_10_2026-10-27_v3.md` | 4/4 thu tiền, tiến độ đơn | 27/10 | `V89` | bổ sung |
| 11 | `BE_RESPONSE_GD_11_2026-10-27.md` | Giá thành, đóng lệnh | 27/10 | `V90`–`V92` | — |

## §3 — Tên file lệch ngày gửi

| File | Ngày trong tên | Ngày gửi (dòng đầu file) | Vì sao |
|---|---|---|---|
| `BE_RESPONSE_GD_06_2026-10-17_v2.md` | 17/10 | 18/10 | Giữ tên v1 của giai đoạn để ba phần đứng cạnh nhau |
| `BE_RESPONSE_GD_06_2026-10-17_v3.md` | 17/10 | 18/10 | như trên |
| `BE_RESPONSE_GD_07_2026-10-18_v3.md` | 18/10 | 19/10 | như trên |

Ngoài ra `BE_RESPONSE_GD_10_2026-10-25.md` ghi ngày 25/10 nhưng bản trong zip được sửa lần cuối 26/10 — nội dung phần 1–2 không đổi, chỉ cập nhật trước khi đóng gói.

## §4 — Đã kiểm thế nào

- Danh sách và kích thước 14 file trong `BE_RESPONSE_G7-11.zip`, so với file rời cùng tên.
- Dòng "Ngày gửi"/"Ngày" ở đầu mỗi file — §3 lấy từ đó, không lấy từ tên file.
- Migration của từng phần lấy từ nhật ký phase nội bộ của BE.
- `/actuator/info` và file OpenAPI lấy từ bản BE đang chạy trên môi trường dev (profile dev, cổng 8090) lúc 17:09 ngày 28/10/2026.

## §5 — Envelope và hợp đồng lỗi

Không đổi. Mọi response dùng `{ "code", "result", "message", "errors" }`; bảng lỗi của từng giai đoạn nằm trong file trả lời của giai đoạn đó.

## §6 — Quyền, phạm vi và giới hạn đã biết

Không liên quan quyền. Giới hạn: BE không có số phiên bản ứng dụng — nhận diện bằng migration cao nhất (§1).

## §7 — Bằng chứng nghiệm thu

Xem §4. **Chưa nghiệm thu:** không có.

## §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|
| Endpoint trả số phiên bản BE | Không có trong yêu cầu; `/actuator/info` hiện rỗng | FE/PO cần |

**FE cần làm tiếp**
- [ ] (bắt buộc) Đọc đủ bộ file ở §2 theo thứ tự; bỏ `BE_RESPONSE_GD_02_2026-10-14.md` (đã có v2 thay).
- [ ] (nên làm) Dùng câu truy vấn ở §1 để biết bản BE trên môi trường đang tích hợp.

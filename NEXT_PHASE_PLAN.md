# Next Phase Plan — **chưa chốt**

> Phase trước: **`D8a` – Refresh Token Reuse Detection (RTR)** ✅ **HOÀN THÀNH 2026-08-03.**
> Bản ghi đầy đủ: `CLAUDE.md §0.22` · bất biến `B80`: `module/auth/CLAUDE.md` · cơ chế + 2 giới hạn
> đã biết: `common/security/CLAUDE.md` §4.12 · sổ track `D*`: `FRONTEND_ALIGNMENT_ROADMAP.md §6`.
>
> **577 case unit** (từ 571) + 66 case IT / 10 class IT · failures = 0 · không migration ·
> wire additive (thêm đúng 1 mã lỗi `TOKEN_REUSE_DETECTED`) · 4/4 mutation đụng `src/main` đều bị bắt.

---

## Trạng thái bàn giao *(đo 2026-08-03 sau `D8a` — ĐO LẠI trước khi bắt đầu phase mới, đừng chép)*

```text
Baseline:            577 case unit  +  66 case IT / 10 class IT   ·  failures = 0
Coverage (unit):     line 74.2%  ·  branch 59.4%     (đo lại 2026-08-03)
Coverage (unit+IT):  line 80.1%  ·  branch 63.9%     (số của F10, 2026-08-01 — CHƯA đo lại)
Migration mới nhất:  V40
```

🔴 **`D8a` không chạy được `*IT`** — Docker Desktop không bật lúc nghiệm thu. Hợp lệ với phase đó vì
nó thuần Redis/service: không JPQL, không schema, không entity, và grep xác nhận **không** `*IT.java`
nào chạm `refresh`/`TokenStore`. **Phase kế tiếp phải bật Docker và chạy `mvn -o verify`** để xác nhận
lại 66 case IT + đo lại coverage unit+IT.

⚠️ Ba bẫy đo số liệu (không đổi): SIGPIPE khi pipe `Tests run:` qua `head`/`Select-Object -First`;
`jacoco:report` phải chạy **lần hai** sau `mvn -o verify` để có số unit+IT; `*IT` cần Docker.

---

## Ứng viên cho phase kế tiếp

| Ứng viên | Nội dung | Effort | Ghi chú |
|---|---|---|---|
| **`D8b`** | Absolute session timeout (30 ngày) — nợ #6, phần 2/3 | Cao | Đổi cấu trúc lưu refresh token trong Redis: `String` → payload có `sessionCreatedAt`. **Đụng mọi đường đọc/ghi refresh token**, kể cả RTR vừa làm ⇒ rủi ro cao hơn `D8a` rõ rệt. Thiết kế: `common/security/CLAUDE.md` §4.15 |
| **`D8c`** | Forgot-password — nợ #6, phần 3/3 | Cao (khối lượng file) | Toàn bộ file mới (service/controller/DTO). **Cần xác nhận hạ tầng gửi email trước khi bắt đầu** — không có nó thì flow không nghiệm thu được đầu-cuối. Thiết kế: §4.14 |
| **`P3`** | Costing | Cao | Spec FE nói tường minh *"detailed costing/OEE… chưa thuộc contract này"*. Làm trước `P4` sẽ phải làm lại phần labor cost (thiếu `standardRunTimePerUnit`) |
| **`P4`** | Work Center entity / CRP | Cao | `workCenterCode` hiện chỉ là string hiển thị, **không** logic nghiệp vụ nào phụ thuộc |
| Nợ nhỏ `E`/`B`/`C`/`D`/`I` | Field-level còn lại của spec FE | Thấp–Trung bình | Không phải bug; lý do hoãn ghi ở `FRONTEND_ALIGNMENT_ROADMAP.md §7.1`. Nợ `E` cần chú ý: `outputQuantity` đổi **công thức nổ BOM** ⇒ đổi số MRP lẫn component line của mọi WO |

> **Chưa chọn cái nào** — chờ user chốt. Khi chốt xong, viết prompt đầy đủ vào file này theo đúng
> cấu trúc `D8a` đã dùng (vì sao phase tồn tại → trạng thái bàn giao → thiết kế đã chốt → việc cần làm
> → test bắt buộc + nghiệm thu mutation → breaking changes → tài liệu phải cập nhật → KHÔNG làm gì).

---

## Việc `D8a` cố ý KHÔNG làm (vẫn còn mở)

| Việc | Vì sao |
|---|---|
| Grace-period / lock (CAS, Lua) cho race 2-request-đồng-thời | Thiết kế §4.12 không có cơ chế đó. Hệ quả **đã chấp nhận**: client double-submit có thể bị force-logout oan — giới hạn cố hữu của RTR cơ bản |
| Mở rộng RTR sang nhánh `stored != null` nhưng mismatch | Có test `never()` canh đúng ranh giới này (bất biến `B80`) |
| Device fingerprinting (§4.19) | Tài liệu tự ghi "Nice-to-have, không bắt buộc Phase 1" |
| Sửa mâu thuẫn "multi-device" (`architecture-decisions.md`) vs single-session (code thật: `login()` gọi `deleteAllUserTokens` mỗi lần login) | 🔴 **Mâu thuẫn tài liệu ↔ code CÓ THẬT, có sẵn từ trước `D8a`.** Ngoài phạm vi phase đó nên chỉ ghi nhận — vẫn **chưa ai sửa**. Ứng viên tốt cho một phase dọn nhỏ |

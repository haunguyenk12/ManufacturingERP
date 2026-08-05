# Next Phase Plan — Chưa chốt

> Phase trước: **`C2-7` – Shift + Work Calendar Entity + CRUD + Work Center FK** ✅ **HOÀN THÀNH
> 2026-08-05.** Bản ghi đầy đủ: `CLAUDE.md §0.29` · sổ track `C2-*`: `FRONTEND_ALIGNMENT_ROADMAP.md
> §8.7` (bảng phase §8.1, checklist §8.0 — 8/13).
>
> **759 case unit + 80 case IT / 12 class IT · failures = 0, errors = 0** (đo bằng `mvn -o clean
> verify` thật với Docker) · migration mới nhất `V47`.

---

## Trạng thái

Chưa chốt phase kế tiếp. Ứng viên không bị chặn:

- **`C2-8` – Capacity Board + schedule adjustment.** Phụ thuộc `C2-6` (xong) + `C2-7` (xong). **Nặng
  nhất** trong cluster `P4` — trước khi viết kế hoạch, đọc:
  - `FRONTEND_ALIGNMENT_ROADMAP.md §8.9` bảng bẫy hàng `C2-8`: `work_order_operations` **chưa có**
    `plannedStartAt`/`plannedEndAt` ⇒ cần chốt **quyết định nghiệp vụ** (sinh lịch lúc tạo WO, lúc
    `plan`, hay lúc `release`?) trước khi code, không chỉ "thêm 2 cột".
  - `FRONTEND_ALIGNMENT_ROADMAP.md §7.1` nợ **B** (`predecessorOperationIds`) — `C2-8` là consumer
    làm lý do hoãn của nợ đó hết hiệu lực, cần xác nhận có làm cùng lúc hay tách riêng.
  - `module/shift/CLAUDE.md` mục 1 (quy ước qui-thuộc-ngày ca qua đêm) — `C2-8` phải cộng dồn giờ
    làm đúng theo quy ước đó, đảo ngược sẽ tính sai capacity của mọi ca qua đêm.
  - `WorkCalendarLookupService.computeWorkingWindows(calendarId, from, to)` (`module/shift/service/`)
    là entry point sẵn có cho net working window — `C2-8` là consumer đầu tiên, gọi thẳng, không cần
    lookup service mới.
- **`D8c` – Forgot-password.** 🔴 Vẫn bị chặn: thiếu `spring-boot-starter-mail`, cần chốt hạ tầng gửi
  email trước khi code.
- **`P3` – Costing.** Chưa bắt đầu, chưa có kế hoạch chi tiết.

**Vẫn bị chặn** (chờ FE trả lời):
- `C2-1` (audit read API) — chờ câu 1 ở `docs/capstone2-api-gap-response.md §5`.
- `C2-2` (inventory lot) — chờ câu 2 ở `docs/capstone2-api-gap-response.md §5`.

---

## Trước khi viết kế hoạch chi tiết cho phase kế tiếp

1. Xác nhận với user chọn ứng viên nào ở trên.
2. Đo lại baseline thật (`mvn -o clean verify`) — **đừng chép số ở đầu file này**, số liệu đổi theo
   từng phase.
3. Theo đúng `.claude/rules/dev-workflow.md §6.6`: viết kế hoạch mới đè lên file này, giữ đúng một
   phase "đang chạy" tại một thời điểm.

# Next Phase Plan — Chưa chốt

> Phase trước: **`C2-6` – Work Center Entity + CRUD + Routing FK** ✅ **HOÀN THÀNH 2026-08-05.**
> Bản ghi đầy đủ: `CLAUDE.md §0.28` · sổ track `C2-*`: `FRONTEND_ALIGNMENT_ROADMAP.md §8.6`
> (bảng phase §8.1, checklist §8.0 — 7/13).
>
> **690 case unit + 79 case IT / 12 class IT · failures = 0, errors = 0** · migration mới nhất `V45`.

---

## Trạng thái track `C2-*` sau `C2-6`

| Phase | Trạng thái |
|---|---|
| `C2-0`, `C2-3`, `C2-4`, `C2-5`, `C2-6` | ✅ Xong |
| `C2-1` (Audit read API), `C2-2` (Inventory lot) | 🔴 **Bị chặn** — chờ FE trả lời câu 1/2 ở `docs/capstone2-api-gap-response.md §5` |
| `C2-7` (Shift + Work Calendar) | Không bị chặn — phụ thuộc `C2-6` (đã xong). Ứng viên cho phase kế tiếp |
| `C2-8` (Capacity Board + schedule adjustment) | Không bị chặn về mặt block, nhưng phụ thuộc `C2-6`+`C2-7` — nặng nhất, nên làm sau `C2-7` |
| `D8c` (forgot-password) | 🔴 Bị chặn — thiếu `spring-boot-starter-mail`, cần chốt hạ tầng gửi email trước |
| `P3` (costing) | Chưa bắt đầu, không phụ thuộc gì đang mở |

## Phase kế tiếp: chưa chốt

Ứng viên hợp lý nhất là `C2-7` (nối tiếp `P4`, không bị chặn), nhưng cần user xác nhận trước khi viết
kế hoạch chi tiết — theo đúng cách `C2-6` đã chốt 3 quyết định thiết kế với user trước khi viết
`NEXT_PHASE_PLAN.md` của chính nó (ảnh hưởng schema Work Calendar/Shift, không tự suy ra được).

Khi chốt phase kế tiếp, viết lại file này theo đúng khuôn các phase trước: mục tiêu, quyết định thiết
kế cần chốt trước, việc cần làm, test bắt buộc, "KHÔNG làm gì", breaking changes, tài liệu phải cập
nhật.

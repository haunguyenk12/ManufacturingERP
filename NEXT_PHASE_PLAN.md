# Next Phase Plan — Chưa chốt

> Phase trước: **`C2-4` – SO PATCH · Role/Scope Lifecycle · Over-BOM Contract · Time Variance**
> ✅ **HOÀN THÀNH 2026-08-05.**
> Bản ghi đầy đủ + nghiệm thu mutation: `CLAUDE.md §0.27` · bất biến `B86` (`module/workorder/CLAUDE.md`),
> `B87` (`module/sales/CLAUDE.md`), `B88` (`module/organization/CLAUDE.md`) · sổ track `C2-*`:
> `FRONTEND_ALIGNMENT_ROADMAP.md §8` (bản ghi phase này: §8.5).
>
> **661 case unit + 78 case IT / 12 class IT · failures = 0, errors = 0** · migration mới nhất `V43`
> (`C2-4` không migration).

---

## Vì sao phase tiếp theo chưa chốt

Trạng thái checklist Capstone 2 (`FRONTEND_ALIGNMENT_ROADMAP.md §8.0`): **6/13**. Các phase còn lại:

| Phase | Trạng thái | Vì sao chưa làm ngay |
|---|---|---|
| `C2-1` (Audit read API) | 🔴 **BỊ CHẶN** | Chờ FE trả lời câu 1 ở `docs/capstone2-api-gap-response.md §5` — màn hình Audit dùng được khi `changes[]` rỗng hay bắt buộc chờ field-level diff (`AuditableAspect`, phạm vi khác hẳn) |
| `C2-2` (Inventory Lot list/detail/status) | 🔴 **BỊ CHẶN** | Chờ FE trả lời câu 2 — màn hình Lot có dẫn user sang QC disposition cho lot `HOLD` không (nếu không, cho `HOLD → AVAILABLE` tự do sẽ dựng lại đúng nợ #17 mà `D5` vừa trả, bất biến `B62`) |
| `C2-6` (Work Center entity + CRUD) | ⚪ **Không bị chặn**, nhưng **chưa thiết kế** | Mở đầu cluster `P4` (Work Center/Shift/Calendar/Capacity) — **nặng nhất** trong toàn bộ track `C2-*`. Cần quyết định trước khi viết plan chi tiết: `WorkOrderOperation.workCenterCode` **giữ nguyên** là snapshot phẳng (bất biến `B56`/`B49`, đã cảnh báo ở `FRONTEND_ALIGNMENT_ROADMAP.md §8.7`) hay `RoutingOperation.workCenterCode` đổi sang FK trỏ Work Center — ảnh hưởng schema + migration, không phải chi tiết triển khai nên không tự quyết được |
| `D8c` (forgot-password) | 🔴 **BỊ CHẶN** | Thiếu `spring-boot-starter-mail` — phải chốt hạ tầng gửi email trước |
| `P3` (Costing) | ⚪ Chưa thiết kế | Chưa có invariant/entity nào tồn tại — cần phiên riêng để phác thảo trước khi lên kế hoạch triển khai |

Không phase nào trong nhóm "không bị chặn" đủ dữ kiện để viết kế hoạch chi tiết ngay — `C2-6` cần
quyết định thiết kế Work Center trước (FK hay snapshot, lịch capacity sinh lúc nào — câu hỏi này đã
ghi ở `FRONTEND_ALIGNMENT_ROADMAP.md §8.7` cho `C2-8` nhưng phải trả lời từ `C2-6`), `P3` chưa có gì
để bắt đầu từ.

---

## Việc cần làm trước khi viết plan chi tiết cho `C2-6`

1. Đọc lại `MANUFACTURING_GAP_ROADMAP.md` phần Work Center/CRP (`P4`) — `C2-6..8` chính là `P4`.
2. Quyết định: `WorkOrderOperation.workCenterCode` có tiếp tục là cột phẳng snapshot hay không khi
   Work Center trở thành entity thật (khuyến nghị hiện tại: **giữ snapshot**, chỉ `RoutingOperation`
   trỏ FK — xem bẫy đã ghi ở `FRONTEND_ALIGNMENT_ROADMAP.md §8.7` hàng `C2-6`/`C2-8`).
3. Xác nhận với user: Work Center có cần gắn `plant_id` (per-plant) hay là master data company-level?
   Chưa có dữ kiện rõ trong `BACKEND_CAPSTONE2_API_GAPS.md` — cần hỏi hoặc suy ra từ cách FE dùng.
4. Sau khi có câu trả lời, viết `NEXT_PHASE_PLAN.md` mới theo đúng khuôn các phase `C2-*` trước
   (thiết kế đã chốt → việc cần làm → test bắt buộc → KHÔNG làm gì → breaking changes → tài liệu phải
   cập nhật → quyết định còn mở).

---

## Nếu FE trả lời trước khi `C2-6` được thiết kế xong

`C2-1`/`C2-2` có độ ưu tiên cao hơn (đã có hạ tầng — bảng `V6`, `StockBalanceRepository.aggregate*` —
chỉ chờ quyết định thiết kế từ câu trả lời FE) và nên làm trước `C2-6` nếu câu trả lời tới trước.

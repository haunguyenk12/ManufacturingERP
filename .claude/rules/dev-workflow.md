# Development Workflow

> Tách từ `CLAUDE.md` §6 (2026-07-25).

## 6.1 Git Branch Strategy
```
main          → production-ready
develop       → integration branch
feature/*     → tính năng mới (merge vào develop qua PR)
hotfix/*      → fix khẩn cấp (merge vào main + develop)
```

## 6.2 Commit Convention (Conventional Commits)
```
feat(auth): implement JWT refresh token rotation
fix(inventory): correct WIP balance calculation
chore(db): add V3__idx_workorder_status migration
```

## 6.3 Environment Profiles
```yaml
spring.profiles.active: ${APP_ENV:dev}

# application-dev.yml   → dev local, bảo mật lỏng, PG local
# application-test.yml  → Testcontainers, Redis in-memory
# application-prod.yml  → cấu hình strict, secrets qua env variables
```

## 6.4 Database Migration Naming
```
V1__create_users_roles.sql
V2__create_inventory_tables.sql
V3__create_bom_workorder.sql
V4__add_idx_performance.sql
V5__create_audit_logs.sql
```

## 6.5 Sau Mỗi Thay Đổi (bắt buộc, mọi task — không chỉ breaking change)

Trước khi báo cáo task là xong, cập nhật tài liệu cho khớp với thay đổi vừa làm:

1. **`CLAUDE.md`** (root) — nếu thay đổi ảnh hưởng phase/trạng thái/baseline test/coverage → sửa §0
   (`0.1` Phase Hiện Tại, `0.2` Module Đã Hoàn Thành, `0.4` Nợ Kỹ Thuật). Nếu thêm/sửa module,
   permission, business rule mới → cập nhật đúng file split tương ứng (xem "BẢN ĐỒ TÀI LIỆU" ở cuối
   `CLAUDE.md`) — **không** viết đè nội dung đó thẳng vào root.
2. **File tiến độ liên quan** — cập nhật đúng file đang track phase của thay đổi:
   - `NEXT_PHASE_PLAN.md` — phase nghiệp vụ/test đang chạy (checklist, Definition of Done, breaking changes).
   - `TEST_IMPROVEMENT_PLAN.md` — nếu thay đổi thêm/sửa test, coverage, hoặc trả nợ kỹ thuật `T*`.
   - `MANUFACTURING_GAP_ROADMAP.md` — nếu thay đổi thuộc phase nghiệp vụ `P*`.
   - `FRONTEND_ALIGNMENT_ROADMAP.md` — nếu thay đổi thuộc phase căn chỉnh FE `F*` (tổng quan + lịch sử).
   - `docs/roles-and-permissions.md` — nếu thêm/sửa permission (bắt buộc, xem `coding-rules.md` C10).
3. Nếu thay đổi thêm bất biến nghiệp vụ mới hoặc sửa bất biến hiện có (bảng `B<n>`) → cập nhật đúng
   `CLAUDE.md` split file của module đó, **không** để bất biến chỉ tồn tại trong code/test mà thiếu
   trong tài liệu.
4. Không hoãn việc này sang task sau — tài liệu lệch code dù chỉ 1 task cũng đủ để agent kế tiếp đọc
   sai trạng thái dự án.

## 6.6 Commit Sau Khi Hoàn Thành Một Phase Của `NEXT_PHASE_PLAN.md` (bắt buộc)

> **Mục tiêu:** lịch sử một phase phải suy ra được từ `git log`, không chỉ từ tài liệu — hai nguồn kể
> hai câu chuyện khác nhau là đúng loại lỗi `CLAUDE.md §0.20` đã cảnh báo (repo tuyên bố "đã đóng" một
> nợ ba lần mà thực tế phạm vi hẹp hơn). Commit theo phase là bằng chứng độc lập, đối chiếu được.

1. **Thứ tự bắt buộc: kiểm chứng → cập nhật tài liệu (§6.5) → commit.** Không commit code rồi để tài
   liệu theo sau ở commit khác — lúc đó `git log` tại thời điểm code commit sẽ nói dối về trạng thái
   dự án.
2. **`git status` trước khi `add`.** Working tree có thể đang chứa thay đổi **không thuộc phase vừa
   làm** (việc dở của phiên trước, file người dùng đang sửa tay). Chỉ `git add` đúng những file nằm
   trong phạm vi phase — liệt kê theo tên, không dùng `git add -A`/`git add .`. Thấy thay đổi lạ thì
   hỏi trước khi gộp vào cùng commit.
3. **Message theo Conventional Commits (§6.2), ghi rõ mã phase** để `git log --grep "F10"` (hoặc
   `D8`, `P3`...) tìm lại đúng commit:
   ```
   feat(planning): F10 — persist projectedAvailable, freeze routing snapshot, add document codes

   Closes debts G/F/H (FRONTEND_ALIGNMENT_ROADMAP.md §7.1). Migration V40. No wire breaking change.
   ```
   Phase sửa nhiều module (vd `F5`, `D7`) thì chọn scope là module chiếm phần lớn thay đổi, hoặc
   `chore(docs)` riêng cho commit chỉ sửa tài liệu.
4. **Sau commit, `NEXT_PHASE_PLAN.md` phải phản ánh đúng trạng thái mới** — phase vừa xong không còn
   được mô tả như "đang chạy" trong file đó (chuyển nội dung chi tiết vào roadmap tương ứng theo quy
   ước đã có, giữ `NEXT_PHASE_PLAN.md` là **đúng một** phase đang chạy hoặc "chưa chốt").
5. **Không `push`** trừ khi user yêu cầu tường minh trong chính lượt đó (xem "Executing actions with
   care" — commit là hành động cục bộ, push là hành động chia sẻ, hai mức rủi ro khác nhau).
6. Phase không migration/không breaking change vẫn phải commit theo đúng quy trình này — "nhỏ" không
   phải lý do để bỏ qua bước tài liệu hoá.
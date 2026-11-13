# Development Workflow

## 6.1 Nhánh git

```
main                          → nhánh ổn định
feature/<mã-phase>-<mô-tả>    → một nhánh cho một phase hoặc track; merge vào main khi phase xong
hotfix/*                      → fix khẩn cấp, merge vào main
```

Không có nhánh `develop`.

> ⚠️ Hiện trạng 2026-11-12: mọi việc từ 08/2026 (`G2`–`G11`, `CF-*`, `AP-1`) nằm trên
> `feature/D8b-absolute-session-timeout`, chưa merge vào `main`. Merge nhánh này trước khi mở nhánh
> cho phase kế tiếp; việc merge và push do user quyết định.

## 6.2 Commit (Conventional Commits, có mã phase)

```
feat(planning): F10 — persist projectedAvailable, freeze routing snapshot
fix(inventory): CF-1 — refuse manual adjust on lots QC still owns
chore(docs): CF-8 — record the OpenAPI re-export
```

Mã phase trong tiêu đề để `git log --grep "F10"` tìm lại được. Không dùng tiêu đề kiểu "cap2 done".

## 6.3 Profile

```
application.yml        → mặc định, mọi giá trị môi trường qua ${ENV_VAR}
application-dev.yml    → nạp .env; KHÔNG đặt log level hay JWT expiry ở đây (file profile thắng
                         application.yml nên sẽ vô hiệu hoá biến tương ứng trong .env)
application-test.yml   → giá trị giả an toàn (DB/Redis trỏ cổng 1); *IT lấy Postgres thật từ
                         Testcontainers qua @DynamicPropertySource
application-prod.yml   → tắt springdoc, actuator chỉ health/info
```

## 6.4 Tên migration

`V<số kế tiếp>__<mô_tả_snake_case>.sql`, ví dụ `V96__add_password_setup_challenges.sql`.
Số mới nhất ghi ở `CLAUDE.md §0.0`. Trước khi thêm cột, `grep` tên cột trong `db/migration/` — cột
có thể đã tồn tại mà entity chưa ánh xạ (vd `roles.version` có từ `V6`).

## 6.5 Sau mỗi thay đổi (bắt buộc)

Trước khi báo xong, cập nhật tài liệu cho khớp:

1. **Phase mới** ⇒ tạo `docs/phases/<mã phase>.md` (nghiệm thu, smoke, breaking change, hệ quả) + một
   dòng trong `docs/phases/INDEX.md`.
2. **`CLAUDE.md` gốc**: chỉ sửa bảng **§0.0** (vài ô). Không dán bản ghi dài vào đó — file được nạp vào
   mọi phiên và từng phình tới ~534 KB. Không sửa `CLAUDE.md` giữa phiên (mất prompt cache); gom về cuối phiên.
3. **Bất biến `B<n>`** mới hoặc đổi ⇒ cập nhật `CLAUDE.md` của đúng module đó.
4. **File kế hoạch của track đang chạy** theo ô "Phase đang chạy" ở `CLAUDE.md §0.0`
   (`NEXT_PHASE_PLAN.md` chỉ chứa đúng một phase đang chạy; `MANUFACTURING_GAP_ROADMAP.md` cho phase `P*`).
5. **Permission** thêm hoặc đổi ⇒ `docs/roles-and-permissions.md` (C10).
6. **Endpoint / DTO / mã lỗi** đổi ⇒ `docs/api-guide-for-frontend.md` trong cùng commit.
7. Không hoãn sang task sau.

## 6.6 Commit sau khi xong một phase (bắt buộc)

1. Thứ tự: **kiểm chứng → cập nhật tài liệu (§6.5) → commit**. Không commit code rồi để tài liệu ở commit sau.
2. `git status` trước khi `add`. Chỉ `git add` đúng các file thuộc phase, liệt kê theo tên; không
   `git add -A`. Thấy thay đổi lạ thì hỏi trước.
3. Message theo §6.2. Phase sửa nhiều module thì chọn scope là module chiếm phần lớn thay đổi.
4. Sau commit, `NEXT_PHASE_PLAN.md` không còn mô tả phase vừa xong là "đang chạy".
5. **Không `push`** trừ khi user yêu cầu tường minh trong chính lượt đó.
6. Phase nhỏ, không migration vẫn theo đúng quy trình này.

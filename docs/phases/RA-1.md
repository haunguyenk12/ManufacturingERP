# RA-1 — Rà soát toàn dự án và dọn dẹp (2026-11-12 → 2026-11-13)

Nguồn: báo cáo `docs/audit-report-2026-11-12.html` (31 đề xuất; user duyệt 25: `A1`–`A7`, `B1`–`B5`,
`B7`, `B8`, `C1`–`C9`, `D1`, `D2`). Không duyệt: `B6` (MapStruct), `B9` (biên dịch hai lần), `D3`–`D6`.

## Thay đổi hành vi (đọc trước khi deploy)

| Mục | Trước | Sau |
|---|---|---|
| `A1` `GET /api/users/v1/{id}` | Nhánh "chính chủ" so sánh `UUID` với `String` nên luôn sai ⇒ tài khoản không phải ADMIN nhận `403` trên hồ sơ của mình | Chính chủ đọc được hồ sơ mình |
| `A1` `PATCH /api/users/v1/{id}` | Có nhánh chính chủ trong `@PreAuthorize` nhưng nhánh đó chết | **Bỏ** nhánh chính chủ. Đổi mật khẩu đi qua `POST /api/auth/v1/password/change` |
| `B3` `/api/users/v1/**` | `hasRole('ADMIN')` (7 chỗ trong `UserService`) | `PERM_ACCESS_MANAGE`. ADMIN vẫn qua; role tự tạo có quyền này cũng quản lý được tài khoản. `adminUnlockAccount` **giữ** `hasRole('ADMIN')` |
| `A2` | `app.rate-limit.enabled` ghi cứng `true` | `${RATE_LIMIT_ENABLED:true}` |
| `A3` profile dev | `application-dev.yml` ghi cứng log level và access token 1 giờ, vô hiệu hoá `LOG_LEVEL_*` / `JWT_ACCESS_TOKEN_EXPIRY_MS` trong `.env` | `.env` quyết định. `.env.example` đặt về giá trị dev vẫn dùng (SQL log `WARN`, token 1 giờ). 🔴 `.env` trên máy đang ghi SQL `DEBUG` và token 15 phút — hai giá trị đó nay **có hiệu lực** |
| `A6` | `spring.flyway.baseline-on-migrate: true` cho mọi môi trường | `false`. DB có dữ liệu mà chưa có bảng lịch sử sẽ bị từ chối lúc khởi động thay vì bị baseline |
| `A7` | Email mã xác thực chạy `@Async` không tên executor | Executor riêng `mailExecutor` (1–2 thread, hàng đợi 100, giữ MDC) |
| `B5` | `Role` không có `@Version` | Ánh xạ cột `roles.version` (có từ `V6`). Hai lần sửa role chồng nhau ⇒ `409 CONCURRENT_MODIFICATION` |

Không có migration mới, không đổi DTO, không đổi mã lỗi.

## `A5` — Spring Boot 3.2.5 ⇒ 3.5.16

`pom.xml`: parent `3.5.16` (bản 3.x mới nhất trên Maven Central ngày 2026-11-13), `springdoc` `2.5.0` ⇒ `2.8.17`,
thêm `flyway-database-postgresql` (bắt buộc từ Flyway 10), bỏ ghi đè `lombok.version` và khối import
`testcontainers-bom` (Boot quản lý cả hai). Không phải sửa dòng code nào; `jjwt` giữ `0.12.5`.

Việc để lại cho lần lên 4.x: 55 file test dùng `@MockBean` (deprecated từ Boot 3.4, vẫn chạy ở 3.5) phải đổi sang
`@MockitoBean`. Dòng 3.5 cũng là dòng 3.x cuối; cần kiểm tra lịch hỗ trợ trên spring.io trước khi coi đây là đích dài hạn.

## Việc đã làm theo mục

- **`A4`**: bỏ `prometheus` khỏi `MANAGEMENT_ENDPOINTS_EXPOSURE_INCLUDE` trong `.env.example` (repo không có registry Prometheus).
- **`B1`**: ba field `@Autowired(required = false)` (`MrpCalculationService`, `MaterialIssueService` ×2) thành constructor
  injection; bỏ các nhánh `!= null`. `MrpCalculationServiceTest` trước đó dựng service **không có** resolver kho —
  trạng thái production không thể có — nay stub resolver trả `ITEM_WAREHOUSE_DEFAULT`.
- **`B2`**: chọn phương án ghi ngoại lệ vào `coding-rules.md` C7 (18 import, 5 nhóm), không refactor. Lý do: nhóm
  `workorder` → `StockBalanceRepository` vừa đọc vừa **ghi** trong transaction giữ chỗ; lookup service không thay được.
- **`B4`**: thêm `ImportProfileControllerTest` (7 ca), `ImportTargetControllerTest` (4), `InventoryReservationControllerTest` (4).
- **`B7`**: 6 chuỗi mã lỗi cứng trong test ⇒ `BusinessErrorCode.X.code()`. `"SUCCESS"` là ngoại lệ ghi ở `testing.md` R8.
- **`B8`**: `dev-workflow.md §6.1` mô tả đúng mô hình nhánh (không có `develop`) và ghi hiện trạng nhánh `D8b` chưa merge.
- **`C1`/`C5`/`C8`**: viết lại `.claude/rules`. `error-handling.md` và `testing.md` (mới, tách từ `best-practices §8.6`)
  có frontmatter `paths:` nên chỉ nạp khi đọc `src/**` / `src/test/**`. Xoá `structure.md`. Bản trước khi rút gọn:
  `docs/archive/rules-2026-11-12/`. Phần luôn nạp (CLAUDE.md + rules): ~85 KB ⇒ ~39 KB.
- **`C2`**: `CLAUDE.md §1.2–§1.4, §2` chuyển sang `README.md` (kèm hướng dẫn chạy).
- **`C3`**: `module/workorder/CLAUDE.md` (92 KB) tách **nguyên văn** thành file cha (42 KB) +
  `service/execution/CLAUDE.md` (45 KB) + `service/cost/CLAUDE.md` (7 KB). `inventory` và `common/security` (46 KB mỗi
  file) **không** tách: toàn bảng bất biến / thiết kế, không có thư mục con tương ứng; `common/security` chỉ được gắn
  nhãn "thiết kế, chưa triển khai" cho §4.17 và §4.19.
- **`C4`**: `NEXT_PHASE_PLAN.md` cũ ⇒ `docs/phases/NEXT_PHASE_PLAN-G2-G11.md`; file ở gốc còn bản ngắn "chưa chốt".
- **`C6`**: `.claude/settings.json` thêm `Bash(bash scripts/mvn-summary.sh *)`, bỏ 6 mục dùng một lần.
- **`C7`**: khôi phục 6 tài liệu đã xoá ở commit `19da202` vào `docs/archive/`.
- **`C9`**: `docs/architecture.md` viết lại §3.3 và §12, đính chính §5.3, §9.5 (các mục khác chưa đối chiếu);
  `RoleDocs.md` gộp vào `fe-session-bootstrap.md §8`.
- **`D1`**: 63 file trao đổi FE–BE ⇒ `docs/handoff/`; 5 kế hoạch refactor ⇒ `docs/plans/`. Chỉ xoá
  `BE_RESPONSE_GD_02_2026-10-14.md` (v2 ghi "thay hoàn toàn v1"). 🔴 Các bản `_v2`/`_v3`/`_v4` khác là **phần bổ
  sung**, không phải bản thay thế — đề xuất "chỉ giữ bản mới nhất" trong báo cáo là sai và không được thực hiện.
- **`D2`**: xoá bản backup docx; `git rm --cached` hai dump có ngày (file còn trên đĩa); `.gitignore` chỉ giữ
  `d26-seeded.dump`.

## Sửa trong lượt rà đầu (trước khi duyệt)

Redis healthcheck trong `docker-compose.yml`; `SPRINGDOC_*` khai trùng trong `.env.example`; URL dạng cũ
`/api/v1/auth/…` trong `api-guide-for-frontend.md`, `fe-session-bootstrap.md`, `architecture.md`; ba hàng sai của
`architecture-decisions.md` (rate limit, Lua, soft delete).

## Sai sót trong chính phase này

Thêm `V97__add_role_version.sql` mà không kiểm tra: `roles.version` đã có từ `V6`. 360/367 IT đỏ ngay lượt
`clean verify` đầu; đã xoá `V97` trước khi commit. Bài học ghi vào `dev-workflow.md §6.4`.

## Kiểm chứng

- Baseline trước khi sửa: 2379 unit, xanh.
- Sau khi sửa, trên Spring Boot 3.2.5: `clean verify` **2401 unit + 367 IT**, 0 failure, 0 error.
- Sau `A5`, trên Spring Boot 3.5.16: `clean verify` **2401 unit + 367 IT**, 0 failure, 0 error
  (`ProductionFlowE2EIT` là `@SpringBootTest` nên phủ luôn việc dựng full context trên bản mới).
- Chưa chạy ứng dụng thật và chưa smoke HTTP: thay đổi `A3`, `A6`, `A7` chỉ được kiểm bằng test, không phải bằng một lần khởi động profile dev.

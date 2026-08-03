# Next Phase Plan — **chưa chốt**

> Phase trước: **`D8b` – Absolute Session Timeout** ✅ **HOÀN THÀNH 2026-08-03.**
> Bản ghi đầy đủ: `CLAUDE.md §0.23` · bất biến `B81`: `module/auth/CLAUDE.md` · cơ chế + 3 chỗ lệch
> thiết kế gốc + giới hạn đã biết: `common/security/CLAUDE.md` §4.15 · sổ track `D*`:
> `FRONTEND_ALIGNMENT_ROADMAP.md §6`.
>
> **586 case unit** (từ 577) + 66 case IT / 10 class IT · failures = 0 · không migration ·
> wire additive (thêm đúng 1 mã lỗi `SESSION_ABSOLUTE_TIMEOUT`) · 4/4 mutation đụng `src/main` đều bị bắt.

---

## Trạng thái bàn giao *(đo 2026-08-03 sau `D8b` — ĐO LẠI trước khi bắt đầu phase mới, đừng chép)*

```text
Baseline:            586 case unit  +  66 case IT / 10 class IT   ·  failures = 0
Coverage (unit):     line 74.3%  ·  branch 59.5%     (đo lại 2026-08-03)
Coverage (unit+IT):  line 80.5%  ·  branch 64.2%     (đo lại 2026-08-03)
Migration mới nhất:  V40
```

✅ **Nợ đo `*IT` của `D8a` đã trả.** `D8b` bật Docker và chạy `mvn -o verify` thật: 66 case IT xanh,
coverage unit+IT đo lại được. **0 case IT bị `D8b` ảnh hưởng** — `ProductionFlowE2EIT` dựng auth bằng
`SecurityMockMvcRequestPostProcessors.authentication(...)` nên **không** đi qua `AuthService.login`.

⚠️ Ba bẫy đo số liệu (không đổi): SIGPIPE khi pipe `Tests run:` qua `head`/`Select-Object -First`;
`jacoco:report` phải chạy **lần hai** sau `mvn -o verify` để có số unit+IT (chạy `mvn -o clean verify
-DskipITs` mới ra số unit một mình); `*IT` cần Docker Desktop bật sẵn.

---

## Ứng viên cho phase kế tiếp

| Ứng viên | Nội dung | Effort | Ghi chú |
|---|---|---|---|
| **`D8c`** | Forgot-password — nợ #6, phần **3/3** (phần cuối) | Cao (khối lượng file) | 🔴 **Đang bị chặn**: `pom.xml` **không** có `spring-boot-starter-mail`. Phải chốt hạ tầng gửi email (thật / stub log / để FE tự gửi) **trước khi bắt đầu** — không có nó thì flow không nghiệm thu đầu-cuối được. Toàn bộ file mới (service/controller/DTO) + 2 mã lỗi `RESET_TOKEN_INVALID`/`RESET_TOKEN_EXPIRED` và 2 `AuditAction` (`PASSWORD_RESET`, `ACCOUNT_UNLOCKED`) hiện **chưa tồn tại trong code**. Thiết kế: `common/security/CLAUDE.md` §4.14 |
| **`P3`** | Costing | Cao | Spec FE nói tường minh *"detailed costing/OEE… chưa thuộc contract này"*. Làm trước `P4` sẽ phải làm lại phần labor cost (thiếu `standardRunTimePerUnit`) |
| **`P4`** | Work Center entity / CRP | Cao | `workCenterCode` hiện chỉ là string hiển thị, **không** logic nghiệp vụ nào phụ thuộc |
| Nợ nhỏ `E`/`B`/`C`/`D`/`I` | Field-level còn lại của spec FE | Thấp–Trung bình | Không phải bug; lý do hoãn ghi ở `FRONTEND_ALIGNMENT_ROADMAP.md §7.1`. Nợ `E` cần chú ý: `outputQuantity` đổi **công thức nổ BOM** ⇒ đổi số MRP lẫn component line của mọi WO |

> **Chưa chọn cái nào** — chờ user chốt. Khi chốt xong, viết prompt đầy đủ vào file này theo đúng
> cấu trúc `D8a`/`D8b` đã dùng (vì sao phase tồn tại → trạng thái bàn giao → thiết kế đã chốt → việc
> cần làm → test bắt buộc + nghiệm thu mutation → breaking changes → tài liệu phải cập nhật →
> KHÔNG làm gì).

---

## Việc `D8b` cố ý KHÔNG làm (vẫn còn mở)

| Việc | Vì sao |
|---|---|
| Kiểm absolute timeout ở `JwtAuthenticationFilter` | Timeout hiện chỉ đánh giá **khi refresh** ⇒ access token đang cầm còn dùng được tới hết TTL (tối đa 15 phút) dù phiên vừa vượt mốc. Đóng cửa sổ đó tốn **một lượt đọc Redis mỗi request**, không tương xứng rủi ro. Giới hạn **đã chấp nhận**, ghi ở §4.15 |
| Đổi value refresh token sang JSON payload (§4.15 bản gốc) | User chốt companion key (2026-08-03); JSON đụng **mọi** đường đọc/ghi refresh token kể cả nhánh RTR của `D8a` |
| Backfill `sessionCreatedAt` cho phiên cũ | Không backfill được — Redis không biết phiên cũ bắt đầu khi nào. Fail-open có chủ đích, xem `B81` |
| Thêm `SESSION_TERMINATED` | Có trong tài liệu (`error-handling.md §5.3`, `common/security/CLAUDE.md §4.7`) nhưng **chưa từng tồn tại trong code**. Việc riêng |
| Grace-period / lock (CAS, Lua) cho race 2-request-đồng-thời của `D8a` | Thiết kế §4.12 không có cơ chế đó. Hệ quả **đã chấp nhận**: client double-submit có thể bị force-logout oan |
| Device fingerprinting (§4.19) | Tài liệu tự ghi "Nice-to-have, không bắt buộc Phase 1" |
| **Đổi** hành vi single-session của `login()` | `D8b` sửa **tài liệu** cho khớp code (`architecture-decisions.md`, §4.10). Bỏ 2 dòng `deleteAll*` để thật sự cho multi-device là **quyết định bảo mật riêng**, không phải dọn tài liệu — vẫn mở nếu user muốn |

---

## Hai chỗ lệch tài liệu đã ghi nhận, **chưa sửa** (ngoài phạm vi `D8b`)

| Chỗ | Nội dung lệch |
|---|---|
| `docs/fe-session-bootstrap.md:284` | Ghi *"Toàn bộ endpoint dưới `/api/v1/auth`: **chỉ có 4**"* trong khi chính dòng 23 của file đó nói về `/auth/me` — endpoint **thứ 5**, thêm 2026-08-01 |
| `common/audit/CLAUDE.md §9.7` | Liệt kê `ACCOUNT_UNLOCKED` và `PASSWORD_RESET` như thể đã có, nhưng `AuditAction.java` **không** có hai hằng đó — chúng thuộc thiết kế `D8c` |

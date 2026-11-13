# Key Architectural Decisions

> Bản đầy đủ kèm lịch sử từng lần sửa: `docs/archive/rules-2026-11-12/architecture-decisions.md`.

| Quyết định | Lựa chọn | Lý do / điều cần nhớ |
|---|---|---|
| Token strategy | Stateless JWT + Redis blacklist | Scalable; logout vẫn vô hiệu hoá được token |
| Refresh token | Lưu Redis, xoay vòng mỗi lần refresh bằng Lua (`TokenStoreService.ROTATE_REFRESH_SCRIPT`) | TTL tự dọn; rotate nguyên tử; phát hiện reuse (`B80`) |
| Device session — hạ tầng | Key Redis theo thiết bị `auth:session:device:{userId}:{deviceId}`, sliding TTL | Single-IP enforcement đã bỏ |
| Device session — chính sách | **Single-session per user** | `AuthService.login` xoá mọi token + device session cũ mỗi lần login. Muốn multi-device là quyết định bảo mật riêng — `common/security/CLAUDE.md §4.10` |
| Absolute session timeout | 30 ngày tính từ login, companion key `auth:refresh:{userId}:{tokenId}:meta` | Refresh TTL sliding 7 ngày không tự ép login lại (`B81`) |
| Concurrent refresh lock | `SET NX PX` qua `setIfAbsent`, TTL 2s, không unlock tường minh | `common/security/CLAUDE.md §4.12a` |
| Rate limiting | Redis **fixed window** theo IP và theo user: `INCR` key `rate:counter:{rule}:{id}:{epoch/window}` + `EXPIRE` | Không phải sliding window: cho phép burst tới 2× limit quanh ranh giới cửa sổ |
| Brute-force counter | Redis `INCR` + `EXPIRE` riêng khi count = 1 (không atomic, không Lua) | Chấp nhận có chủ đích; đừng mô tả là "Lua atomic" |
| Session | `STATELESS` | Scale ngang |
| Response contract | `{code, result, message, errors?}` cho mọi response | Chi tiết: `.claude/rules/error-handling.md` |
| ORM | JPA + Hibernate; native SQL chỉ cho report | Type safety |
| Soft delete | Cột `status` (`INACTIVE` / `CANCELLED` …), không hard-delete | Repo **không** có `deleted_at` hay `@Where` — đừng thêm |
| Optimistic locking | `@Version` trên entity sửa theo kiểu đọc–sửa–ghi | Bảng chỉ insert/delete hoặc đổi trạng thái bằng `UPDATE` có điều kiện (vd `password_challenges`, `B277`) thì không cần |
| Audit — ghi | **Transactional outbox** (`audit_outbox`) + dispatcher `@Scheduled` | Outbox row nằm trong transaction nghiệp vụ: cùng commit, cùng rollback. Listener `AFTER_COMMIT` cũ còn nhưng mặc định tắt |
| Audit — event `FAILURE` | `REQUIRES_NEW` qua **bean riêng** | Phải sống qua transaction vừa rollback; gọi trên chính mình sẽ bypass proxy |
| Audit — idempotency | `eventId` do producer sinh + unique index `uk_audit_logs_event_id` | Không sinh lại `eventId` khi retry |
| Audit — bất biến | Trigger DB (`V68`) + `AppendOnlyRepository` | Trigger chặn mọi role, kể cả superuser |
| Audit — failure policy | **`FAIL_OPEN`** toàn hệ thống (user chốt, `docs/plans/AuditRefactorPlan.md §11.1`) | Audit hỏng không đổi kết quả nghiệp vụ |

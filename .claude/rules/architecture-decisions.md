# Key Architectural Decisions

> Tách từ `CLAUDE.md` §7 (2026-07-25).

| Quyết Định | Lựa Chọn | Lý Do |
|---|---|---|
| Token strategy | Stateless JWT + Redis blacklist | Scalable; logout vẫn được vô hiệu hóa |
| Refresh token storage | Redis (không phải DB) | Latency thấp; tự hết hạn qua TTL |
| Token rotation khi refresh | Có | Ngăn refresh token reuse attacks |
| Device session — **hạ tầng** | Per-device Redis key (`auth:session:device:{userId}:{deviceId}`), sliding TTL | Chịu được nhiều phiên song song; single-IP enforcement (kick session cũ) **đã bị loại bỏ** |
| Device session — **chính sách** | **Single-session per user** | 🔴 **Sửa 2026-08-03 (`D8b`)**: hàng này trước đây ghi "cho phép nhiều thiết bị đăng nhập đồng thời", **không khớp code**. `AuthService.login` gọi `deleteAllUserTokens` + `deleteAllDeviceSessions` **mỗi lần login** ⇒ máy mới thu hồi mọi phiên cũ. `D8b` sửa **tài liệu** theo code, **không** đổi hành vi. Muốn multi-device thật thì đó là quyết định bảo mật riêng (bỏ 2 dòng đó) — xem `common/security/CLAUDE.md` §4.10 |
| Absolute session timeout | 30 ngày, đếm từ login, companion key `auth:refresh:{userId}:{tokenId}:meta` | Refresh TTL sliding 7 ngày ⇒ user active liên tục không bao giờ bị buộc login lại (`D8b`, bất biến `B81`) |
| Rate limiting | Redis sliding window per-IP + per-user | Không phụ thuộc ngoài; overhead dưới 1ms |
| Response contract | `{code, result, message}` thống nhất | Frontend 1 contract duy nhất; OpenAPI dễ spec |
| Brute-force counter | Redis `INCR` + `EXPIRE` khi count=1 | 🔴 **Sửa (concurrent refresh-token race phase)**: hàng này trước đây ghi "Lua script atomic" — **không khớp code**. `TokenStoreService.incrementFailCount` là `opsForValue().increment` (tự atomic) rồi một `expire()` **riêng, không atomic** khi count vừa chạm 1. Không có `RedisScript`/`DefaultRedisScript` nào trong repo (đã grep xác nhận, 0 kết quả) — Lua **chưa bao giờ** được dùng ở đây. Không sửa hành vi (ngoài phạm vi), chỉ sửa mô tả cho khớp code |
| Concurrent refresh-token lock | `SET NX PX` qua `opsForValue().setIfAbsent` (không phải Lua) | Atomic đủ dùng cho advisory lock 1 lệnh; không cần compare-and-delete vì không unlock tường minh (TTL 2s tự dọn). Đóng nợ #6 phần "concurrent refresh race", xem `common/security/CLAUDE.md §4.12a` |
| Session | Hoàn toàn stateless (STATELESS policy) | Horizontal scalability, microservice-ready |
| ORM | JPA + Hibernate (không dùng native SQL mặc định) | Portability + type safety; raw SQL chỉ cho reports |
| Soft delete | `deleted_at TIMESTAMPTZ NULL` + `@Where` filter | Audit trail + khôi phục dữ liệu |
| Optimistic locking | `@Version` trên mọi entity có thể bị cập nhật đồng thời | Ngăn xung đột cập nhật concurrent |
| Audit log | Async Spring Events + `@TransactionalEventListener` | Không block response; không ghi khi transaction rollback |
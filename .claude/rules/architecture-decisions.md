# Key Architectural Decisions

> Tách từ `CLAUDE.md` §7 (2026-07-25).

| Quyết Định | Lựa Chọn | Lý Do |
|---|---|---|
| Token strategy | Stateless JWT + Redis blacklist | Scalable; logout vẫn được vô hiệu hóa |
| Refresh token storage | Redis (không phải DB) | Latency thấp; tự hết hạn qua TTL |
| Token rotation khi refresh | Có | Ngăn refresh token reuse attacks |
| Multi-device session | Per-device Redis key (`auth:session:device:{userId}:{deviceId}`), sliding TTL | Cho phép nhiều thiết bị đăng nhập đồng thời — phù hợp ERP nội bộ (xem `common/security/CLAUDE.md` §4.10). Single-IP enforcement (kick session cũ) **đã bị loại bỏ** |
| Rate limiting | Redis sliding window per-IP + per-user | Không phụ thuộc ngoài; overhead dưới 1ms |
| Response contract | `{code, result, message}` thống nhất | Frontend 1 contract duy nhất; OpenAPI dễ spec |
| Brute-force counter | Redis Lua script (atomic) | Tránh race condition khi concurrent attack |
| Session | Hoàn toàn stateless (STATELESS policy) | Horizontal scalability, microservice-ready |
| ORM | JPA + Hibernate (không dùng native SQL mặc định) | Portability + type safety; raw SQL chỉ cho reports |
| Soft delete | `deleted_at TIMESTAMPTZ NULL` + `@Where` filter | Audit trail + khôi phục dữ liệu |
| Optimistic locking | `@Version` trên mọi entity có thể bị cập nhật đồng thời | Ngăn xung đột cập nhật concurrent |
| Audit log | Async Spring Events + `@TransactionalEventListener` | Không block response; không ghi khi transaction rollback |
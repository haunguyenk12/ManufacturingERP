# Backend Deep-Dive — OmniPlant Manufacturing ERP

> Tài liệu backup cho `Outline.md`. **Không dùng để đọc trên sân khấu** — dùng để tra cứu nhanh khi
> hội đồng hỏi xoáy vào một cơ chế cụ thể ("cái này xử lý thế nào?", "nếu hai request cùng lúc thì
> sao?", "sao anh biết cái này đúng?"). Mỗi khối đi theo cấu trúc: **mục đích → code thật (có
> `file:line`) → giải thích luồng → lý do thiết kế → (nếu có) lưu ý/gap đã tự phát hiện**.
>
> Toàn bộ code trích dưới đây đã được đọc trực tiếp từ file thật tại thời điểm soạn tài liệu
> (2026-08-22), không phải paraphrase. Nếu code đổi sau này, số dòng có thể lệch — nhưng tên
> method/class thì ổn định.

## Mục lục

1. [Optimistic Locking (Concurrency Control cơ bản)](#1-optimistic-locking)
2. [JWT Refresh Rotation + RTR + Absolute Timeout + Concurrent-Refresh Race](#2-jwt-refresh-rotation)
3. [Dynamic Scoped RBAC](#3-dynamic-scoped-rbac)
4. [Idempotency (DB column + UNIQUE, không phải Redis TTL)](#4-idempotency)
5. [Production Receipt Lifecycle + QC Disposition](#5-production-receipt--qc)
6. [Work Order Release Gate + Sales Order Fulfillment Loop](#6-release-gate--fulfillment)
7. [MRP Engine](#7-mrp-engine)

---

## 1. Optimistic Locking

**Mục đích:** hai request PATCH cùng một chứng từ (Work Order, Sales Order, …) cùng lúc — request
nào thắng, request nào phải biết là mình vừa ghi đè lên dữ liệu đã cũ.

### 1.1. `@Version` trên mọi entity nghiệp vụ

`src/main/java/com/erp/manufacturing/common/audit/BaseEntity.java`

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate  @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @CreatedBy    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
    @LastModifiedBy @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version")
    private Long version;
}
```

Mọi entity nghiệp vụ (`WorkOrder`, `SalesOrder`, `ProductionReceipt`, …) kế thừa `BaseEntity`. Hibernate
tự thêm `WHERE version = ?` vào câu `UPDATE` và tự tăng `version` sau mỗi lần ghi thành công. Nếu một
request đọc `version = 3` rồi ghi, nhưng lúc đó DB đã là `version = 4` (request khác vừa ghi trước),
câu `UPDATE` khớp **0 dòng** → Hibernate ném `ObjectOptimisticLockingFailureException`.

### 1.2. Đường "ngầm" — Hibernate tự phát hiện lúc flush

`src/main/java/com/erp/manufacturing/common/exception/GlobalExceptionHandler.java:217-225`

```java
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
public ResponseEntity<ApiResponse<Void>> handleOptimisticLocking(
        ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    log.warn("[{}] Optimistic lock conflict at {}: {}",
            BusinessErrorCode.CONCURRENT_MODIFICATION.code(), request.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(BusinessErrorCode.CONCURRENT_MODIFICATION));
}
```

Đây là con đường **tự động, ngầm** — bất kỳ entity nào bị đụng độ đều rơi vào đây mà không cần code
riêng ở từng service.

### 1.3. Đường "tường minh" — vì sao `SalesOrderService.update` phải tự kiểm tra

`src/main/java/com/erp/manufacturing/module/sales/service/SalesOrderService.java:106-137`

```java
if (!order.isDraft()) {
    throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT, "...");
}
if (!request.expectedVersion().equals(order.getVersion())) {
    throw ExceptionFactory.businessRule(BusinessErrorCode.CONCURRENT_MODIFICATION,
            "Sales order was modified by another request");
}
...
if (request.lines() != null) {
    replaceLines(order, request.lines());
}
// saveAndFlush (không phải save): response phải mang đúng version cho lần PATCH kế tiếp.
return mapper.toResponse(salesOrderRepository.saveAndFlush(order), true);
```

**Vì sao cần check tường minh trong khi đã có `@Version` tự động?** Đây là full-replace PATCH: entity
được load **tươi** từ DB trong chính transaction này, nên với Hibernate, `version` trong bộ nhớ và
`version` trong DB **luôn khớp nhau** — không có gì "cũ" để nó tự phát hiện. `expectedVersion` gửi từ
client (con số của lần GET/PATCH trước) phải được so **bằng tay** với `order.getVersion()` **trước
khi** mutate bất cứ field nào (rule `C9`: fail fast trước khi ghi).

**Bug thật đã sửa — `saveAndFlush` thay vì `save`:** `save()` chỉ *queue* câu `UPDATE`; Hibernate không
bump `@Version` trong bộ nhớ cho tới khi câu đó thật sự flush — mặc định là lúc **commit**, tức là
**sau khi** response DTO đã build xong. Hệ quả: response trả về `version` **cũ hơn** giá trị vừa thật
sự ghi vào DB, client dùng con số đó làm `expectedVersion` cho lần sau sẽ luôn bị `409` giả (lệch đúng
1). `confirm`/`cancel` trong cùng file cũng dùng `saveAndFlush` vì cùng lý do.

---

## 2. JWT Refresh Rotation

**Mục đích:** access token 15 phút, refresh token 7 ngày; mỗi lần refresh phải xoay vòng cả cặp (token
rotation), phát hiện được nếu refresh token bị đánh cắp và dùng lại, và ép logout sau 30 ngày dù user
vẫn hoạt động liên tục.

### 2.1. Toàn bộ `AuthService.refresh(...)` — đọc đúng thứ tự code

`src/main/java/com/erp/manufacturing/module/auth/service/AuthService.java:166-273`

```java
public AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest) {
    UUID userId = tokenStore.getTokenOwner(request.tokenId());          // (a)
    if (userId == null) throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
    User user = userRepository.findById(userId).orElseThrow(...);
    UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(user.getUsername());
    if (!principal.isEnabled()) throw ExceptionFactory.unauthorized(AuthErrorCode.ACCOUNT_INACTIVE);

    if (!tokenStore.acquireRefreshLock(request.tokenId())) {            // (b)
        sleepBriefly();
    }

    String stored = tokenStore.getRefreshToken(principal.getUserId(), request.tokenId());
    if (stored == null) {                                               // (c)
        if (tokenStore.wasRefreshTokenUsed(request.tokenId(), request.refreshToken(),
                principal.getAuthVersion())) {
            revokeAllSessions(user);
            auditLogService.logAuthFailure(..., AuditAction.SUSPICIOUS_TOKEN_REUSE, ...);
            throw ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_REUSE_DETECTED);
        }
        throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
    }
    if (!tokenStore.matchesRefreshToken(principal.getUserId(), request.tokenId(),
            request.refreshToken(), principal.getAuthVersion())) {       // (d)
        throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    Instant sessionStart = tokenStore.getSessionStart(principal.getUserId(), request.tokenId());
    if (sessionStart == null) {
        sessionStart = Instant.now();                                   // (e) fail-open cho session cũ
    } else if (Duration.between(sessionStart, Instant.now()).toMillis()
            >= jwtProperties.absoluteSessionTimeoutMs()) {
        revokeAllSessions(user);
        throw ExceptionFactory.unauthorized(AuthErrorCode.SESSION_ABSOLUTE_TIMEOUT);
    }

    String newAccessToken  = jwtTokenProvider.generateAccessToken(principal);
    String newRefreshToken = jwtTokenProvider.generateRefreshToken();
    String newTokenId      = UUID.randomUUID().toString();

    TokenStoreService.RotationResult rotation = tokenStore.rotateRefreshToken(   // (f)
            principal.getUserId(), request.tokenId(), request.refreshToken(),
            newTokenId, newRefreshToken, sessionStart, principal.getAuthVersion());
    if (rotation != TokenStoreService.RotationResult.ROTATED) {
        if (tokenStore.wasRefreshTokenUsed(request.tokenId(), request.refreshToken(),
                principal.getAuthVersion())) {
            revokeAllSessions(user);
            throw ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_REUSE_DETECTED);
        }
        throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    String deviceId = resolveDeviceId(request.deviceId(), httpRequest, ip);
    tokenStore.extendDeviceSession(principal.getUserId(), deviceId);
    auditLogService.logAuth(..., AuditAction.TOKEN_REFRESHED, "device=" + deviceId);

    return new AuthResponse(newAccessToken, newRefreshToken, newTokenId,
            jwtProperties.accessTokenExpiryMs() / 1000, deviceId, false);
}
```

**Đọc theo đúng thứ tự (a)→(f):**

- **(a)** Resolve `userId` chỉ từ `tokenId` (Redis `auth:refresh:owner:{tokenId}`) — **không** đọc
  header `Authorization`. Đây là fix P0 thật: trước đây `refresh()` phụ thuộc ngầm vào access token
  hợp lệ, nên đúng lúc access token hỏng (lý do chính đáng nhất để gọi refresh) lại là lúc endpoint
  chặn cứng request.
- **(b)** Khoá tư vấn (advisory lock) — xem mục 2.3.
- **(c)** Nhánh `stored == null`: đây là nơi RTR sống — xem mục 2.2.
- **(d)** Token tồn tại nhưng giá trị sai (không phải mất) → **không** phải RTR, chỉ là
  `REFRESH_TOKEN_EXPIRED` bình thường (token còn `tokenId` nghĩa là chưa từng bị rotate away).
- **(e)** Absolute session timeout — đặt **sau** khi đã validate token hợp lệ (người không cầm token
  hợp lệ không được biết gì về tuổi phiên) và **trước** rotate (phiên hết hạn tuyệt đối không được
  nhận cặp token mới).
- **(f)** Rotation atomic — xem mục 2.4.

### 2.2. RTR (Refresh Token Reuse Detection)

`TokenStoreService.wasRefreshTokenUsed` — kiểm tra token cũ có phải **vừa** bị rotate away hay không:

```java
public boolean wasRefreshTokenUsed(String tokenId, String presentedToken, long authVersion) {
    String retiredDigest = redisTemplate.opsForValue().get(REFRESH_USED_KEY_PREFIX + tokenId);
    if (retiredDigest == null) return false;
    return MessageDigest.isEqual(retiredDigest.getBytes(StandardCharsets.UTF_8),
            storedRefreshValue(presentedToken, authVersion).getBytes(StandardCharsets.UTF_8));
}
```

`REFRESH_USED_KEY_PREFIX = "auth:refresh:used:"`, TTL 60 giây (`REUSE_DETECTION_TTL_SEC`). Nếu key
này tồn tại **và** giá trị digest khớp đúng token đang được trình ra → chắc chắn đây là token đã bị
rotate away, ai đó đang dùng lại một token đã "chết" → dấu hiệu bị đánh cắp → `revokeAllSessions` +
`TOKEN_REUSE_DETECTED`.

### 2.3. Khoá tư vấn + Lua rotation atomic (phần "Concurrency Control" thật sự)

`TokenStoreService.acquireRefreshLock` — `SET NX PX`, một lệnh Redis atomic:

```java
public boolean acquireRefreshLock(String tokenId) {
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
            REFRESH_LOCK_KEY_PREFIX + tokenId, "1", REFRESH_LOCK_TTL_SEC, TimeUnit.SECONDS); // TTL 2s
    return Boolean.TRUE.equals(acquired);
}
```

Nếu không giành được khoá (một request khác đang xử lý cùng `tokenId`), request hiện tại `sleepBriefly()`
150ms rồi mới đọc tiếp — cho request kia đủ thời gian hoàn tất rotation trước khi mình đọc Redis.

**Rotation là một round-trip Redis duy nhất, atomic, qua Lua script** —
`TokenStoreService.rotateRefreshToken`, script `ROTATE_REFRESH_SCRIPT`:

```java
private static final DefaultRedisScript<Long> ROTATE_REFRESH_SCRIPT = new DefaultRedisScript<>("""
        local current = redis.call('GET', KEYS[1])
        if not current then return 0 end
        if current ~= ARGV[1] then return -1 end
        redis.call('SET', KEYS[3], ARGV[2], 'EX', ARGV[5])   -- refresh token mới
        redis.call('SET', KEYS[4], ARGV[4], 'EX', ARGV[5])   -- session-start mới (carry-forward)
        redis.call('SET', KEYS[5], ARGV[3], 'EX', ARGV[5])   -- owner reverse-lookup mới
        redis.call('SET', KEYS[6], ARGV[1], 'EX', ARGV[6])   -- đánh dấu token CŨ "used" (RTR marker)
        redis.call('DEL', KEYS[1], KEYS[2])                  -- xoá token cũ + meta cũ
        return 1
        """, Long.class);
```

Thứ tự bên trong script (đều xảy ra trong **một** lần round-trip, không thể bị chen ngang bởi request
khác): lưu cặp mới → đánh dấu cặp cũ "used" → xoá cặp cũ. Marker "used" **phải tồn tại trước khi** key
cũ biến mất — nếu làm ngược (xoá trước, đánh dấu sau), một request race đọc đúng vào khoảng hở đó sẽ
thấy `stored == null` **và** `wasRefreshTokenUsed == false` cùng lúc → bị chẩn đoán sai thành "hết hạn
bình thường" thay vì phát hiện được khả năng reuse.

> 🔴 **Gap đã tự phát hiện, đọc trước khi bị hỏi trực tiếp về cơ chế race:**
>
> `TokenStoreService` **còn có** hai method `saveRotationResult`/`getRotationResult` (breadcrumb) —
> ý tưởng thiết kế là: nếu request B (client retry, mất kết nối không nhận được response của request
> A) đọc `stored == null` vì A đã rotate xong, B sẽ tra breadcrumb này và được **trả lại đúng cặp token
> mà A vừa tạo**, thay vì bị coi là kẻ trộm dùng lại token cũ. Cơ chế này **có tồn tại trong code**
> (`TokenStoreService.java`), **có unit test riêng** (`TokenStoreServiceTest`), và **được tài liệu nội
> bộ** (`common/security/CLAUDE.md`, `module/auth/CLAUDE.md`) mô tả là đã hoàn thiện (bất biến B95).
>
> **Nhưng:** grep toàn bộ `AuthService.java` cho `saveRotationResult`/`getRotationResult` cho ra
> **0 lời gọi**. Đọc lại `AuthServiceTest` xác nhận: test tên
> `refresh_concurrentDuplicate_absorbsRotationResultInsteadOfThrowingReuseDetected` — nhưng nội dung
> thật của nó lại **assert `TOKEN_REUSE_DETECTED` vẫn bị ném** cho đúng kịch bản mà tên test nói là
> "được absorb". Nói cách khác: **primitive đã viết, đã test cô lập, nhưng không được wire vào luồng
> gọi thật** — tài liệu nội bộ đi trước code.
>
> **Kết luận đúng theo code (không phải theo tài liệu):** cơ chế giảm race hiện có là **lock tư vấn +
> sleep 150ms + Lua rotation atomic** (đủ để thu hẹp cửa sổ race xuống rất nhỏ, vì thời gian giữa
> "acquire lock thất bại" và "winner hoàn tất round-trip Redis" chỉ vài mili-giây), **không phải**
> "loser luôn được trả lại cặp của winner". Nếu loser thua khoá, ngủ 150ms, rồi vẫn đọc đúng lúc
> `stored == null` và `wasRefreshTokenUsed == true` (winner đã xong trước cả 150ms đó) — loser **vẫn**
> nhận `TOKEN_REUSE_DETECTED` và bị force-logout, đúng false-positive mà breadcrumb được thiết kế để
> tránh. **Nếu hội đồng hỏi trực tiếp câu này, đây là câu trả lời trung thực theo code, không phải
> theo `CLAUDE.md`.**

### 2.4. Absolute Session Timeout — carry-forward, không phải stamp mới

`sessionStart` được lưu ở **companion key** `auth:refresh:{userId}:{tokenId}:meta` (không nhồi vào
value của refresh token, để không đụng gì tới RTR). Điểm quan trọng nhất: khi rotate, `sessionStart`
được **mang nguyên** sang `tokenId` mới (tham số `ARGV[4]` trong Lua script ở trên) — **không** stamp
`Instant.now()`. Nếu stamp `now`, đồng hồ tuyệt đối sẽ tự reset mỗi 15 phút (mỗi lần access token hết
hạn và FE tự động refresh) và tính năng "hết hạn sau 30 ngày" sẽ thành **no-op hoàn toàn** — trong khi
response, mã lỗi, HTTP status đều không đổi gì, chỉ có `sessionStart` sai. Đây là loại bug mà chỉ
assertion **đối số** (verify đúng giá trị được truyền vào rotate) mới bắt được, không phải mock
`verify()` thông thường.

---

## 3. Dynamic Scoped RBAC

**Mục đích:** một permission (`PERM_WORK_ORDER_MANAGE`) không phải chỉ "có/không" — nó còn bị giới hạn
theo scope tổ chức (Company → Plant → Warehouse). Một Manager có quyền quản lý Work Order chỉ trong
đúng Plant được gán, request ngoài phạm vi bị từ chối dù permission tổng quát vẫn tồn tại.

### 3.1. `PermissionGuard.hasResourceAccess` — entry point mà `@PreAuthorize` gọi

`src/main/java/com/erp/manufacturing/module/organization/security/PermissionGuard.java`

```java
@Transactional(readOnly = true)
public boolean hasResourceAccess(Authentication authentication, String permissionCode,
                                 String resourceType, UUID resourceId) {
    if (resourceId == null) return false;
    String normalizedPermission = normalizePermissionCode(permissionCode);
    ScopeResourceType type = normalizeResourceType(resourceType);
    if (normalizedPermission == null || type == null) return false;
    if (isAdmin(authentication)) return true;
    Optional<UUID> userId = principalUserId(authentication);
    if (userId.isEmpty()) return false;
    if (hasPermission(authentication, normalizedPermission)) return true;   // scope GLOBAL

    return switch (type) {
        case COMPANY   -> hasDirectResourcePermission(userId.get(), normalizedPermission, ScopeResourceType.COMPANY, resourceId);
        case PLANT     -> hasPlantAccess(userId.get(), normalizedPermission, resourceId);
        case WAREHOUSE -> hasWarehouseAccess(userId.get(), normalizedPermission, resourceId);
    };
}
```

**Cơ chế kế thừa scope** — `hasPlantAccess` thử grant trực tiếp trên PLANT, nếu không có thì thử grant
trên COMPANY cha của plant đó:

```java
private boolean hasPlantAccess(UUID userId, String permissionCode, UUID plantId) {
    if (hasDirectResourcePermission(userId, permissionCode, ScopeResourceType.PLANT, plantId)) {
        return true;
    }
    return plantRepository.findById(plantId)
            .map(plant -> hasDirectResourcePermission(
                    userId, permissionCode, ScopeResourceType.COMPANY, plant.getCompany().getCompanyId()))
            .orElse(false);
}
```

`hasWarehouseAccess` đi thêm một tầng: thử WAREHOUSE → PLANT cha → COMPANY ông. Một grant ở
`COMPANY` tự động bao trùm mọi `PLANT`/`WAREHOUSE` con của nó; một grant ở `WAREHOUSE` **không** lan
sang warehouse khác.

### 3.2. Query gốc — 5 điều kiện fail-closed

`UserRoleAssignmentRepository.existsActiveResourcePermission` (gọi từ `hasDirectResourcePermission`):

```sql
select count(a) > 0
from UserRoleAssignment a
join Role r on a.roleId = r.roleId
join RolePermission rp on rp.roleId = r.roleId
join Permission p on p.permissionId = rp.permissionId
join AccessScope s on a.scopeId = s.scopeId
join AccessScopeResource sr on sr.scopeId = s.scopeId
where a.userId = :userId
  and p.code = :permissionCode
  and sr.resourceType = :resourceType
  and sr.resourceId = :resourceId
  and a.status = :assignmentStatus
  and r.status = :roleStatus
  and p.status = :permissionStatus
  and s.status = :scopeStatus
  and (a.expiresAt is null or a.expiresAt > :now)
```

5 điều kiện phải **đều đúng**: assignment `ACTIVE`, role `ACTIVE`, permission `ACTIVE`, scope
`ACTIVE`, và chưa hết hạn (`expiresAt`). Đây là bất biến **B32** trong `module/organization/CLAUDE.md`
— thiếu một điều kiện nào cũng là lỗ hổng bảo mật (VD: quên check `role.status` thì disable một role
không thu hồi được quyền của user đang giữ role đó ngay lập tức).

### 3.3. Resource-guard theo module — ví dụ `WorkOrderPermissionGuard`

`src/main/java/com/erp/manufacturing/module/workorder/service/WorkOrderPermissionGuard.java`

```java
@Component("workOrderPermissionGuard")
public class WorkOrderPermissionGuard {
    public boolean hasWorkOrderAccess(Authentication authentication, String permissionCode, UUID workOrderId) {
        if (workOrderId == null) return false;
        return workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .map(workOrder -> permissionGuard.hasResourceAccess(
                        authentication, permissionCode, "PLANT", workOrder.getPlant().getPlantId()))
                .orElse(false);
    }
}
```

Work Order tự nó không phải là "scope" — nó **resolve ra** plant của nó rồi hỏi `PermissionGuard`.
Mỗi module có một guard tương tự (`SalesPermissionGuard`, `BomPermissionGuard`,
`InventoryPermissionGuard`, …), tất cả đều mỏng, chỉ resolve resource → scope rồi delegate.

```java
// module/workorder/service/WorkOrderService.java
@PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_MANAGE', 'PLANT', #plantId)")
public WorkOrderResponse create(UUID plantId, WorkOrderCreateRequest request) { ... }

@PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_MANAGE', #workOrderId)")
public WorkOrderResponse release(UUID workOrderId) { ... }
```

`create` biết `plantId` ngay từ request (endpoint `/plants/{plantId}/work-orders`); `release` chỉ có
`workOrderId`, nên phải qua guard resolve ngược ra plant trước khi hỏi quyền.

---

## 4. Idempotency

**Mục đích:** network timeout, client retry, double-submit — cùng một hành động logic gửi 2 lần không
được tạo ra 2 chứng từ / cộng tồn kho 2 lần.

### 4.1. `IdempotencySupport` — 3 trách nhiệm dùng chung

`src/main/java/com/erp/manufacturing/common/idempotency/IdempotencySupport.java`

```java
public String normalizeKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) {
        throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                "Idempotency-Key header is required");
    }
    // trim + giới hạn 120 ký tự
}

/** SHA-256 (hex) của payload JSON — record serialize theo đúng thứ tự khai báo nên ổn định. */
public String payloadHash(Object payload) {
    byte[] json = objectMapper.writeValueAsBytes(payload);
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
}

public void ensureSamePayload(String storedHash, Object payload) {
    if (storedHash == null) return;   // dòng cũ trước khi có fingerprinting: cho replay qua
    if (!storedHash.equals(payloadHash(payload))) {
        throw ExceptionFactory.custom(BusinessErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency-Key was already used with a different payload");
    }
}
```

**Nguyên tắc cốt lõi (khác biệt lớn nhất so với "idempotency key" kiểu thông thường của nhiều hệ
thống):** key được lưu **trên chính cột của dòng chứng từ**, có `UNIQUE constraint` ở tầng database
— **không phải** Redis với TTL. Vì một chứng từ nghiệp vụ (Work Order, Goods Receipt, MRP Run…) phải
chống trùng lặp **vĩnh viễn**, không phải chỉ trong vài giờ đầu.

### 4.2. Ví dụ 1 — `MrpRunService.run`: claim key trước khi tính toán

`src/main/java/com/erp/manufacturing/module/planning/service/MrpRunService.java:78-154`

```java
@Transactional
public MrpRunResponse run(MrpRunCreateRequest request, String idempotencyKey) {
    String normalizedKey = StringUtils.hasText(idempotencyKey) ? idempotency.normalizeKey(idempotencyKey) : null;
    if (normalizedKey != null) {
        Optional<MrpRun> replayed = mrpRunRepository.findByIdempotencyKey(normalizedKey);
        if (replayed.isPresent()) {
            idempotency.ensureSamePayload(replayed.get().getPayloadHash(), request);
            return mapper.toResponse(replayed.get());          // replay: trả lại run cũ
        }
    }
    ...
    MrpRun run = MrpRun.builder()
            .idempotencyKey(normalizedKey)
            .payloadHash(normalizedKey == null ? null : idempotency.payloadHash(request))
            .build();
    run.start(Instant.now());
    // saveAndFlush: chiếm key ở DB NGAY, trước khi bắt đầu tính (có thể mất vài giây với BOM sâu).
    // Nếu chỉ save() (deferred flush), 2 request trùng key chạy song song sẽ CẢ HAI tính xong hết
    // rồi mới đụng UNIQUE constraint lúc commit — lãng phí đúng công việc mà cơ chế này sinh ra để
    // tránh.
    run = mrpRunRepository.saveAndFlush(run);
    ...
    try {
        // ... calculate(), persistRequirements(), persistSuggestions()
        persistedRun.complete(...);
    } catch (RuntimeException e) {
        persistedRun.fail(Instant.now(), e.getMessage());   // nuốt exception, KHÔNG rethrow
    }
    return mapper.toResponse(mrpRunRepository.save(persistedRun));
}
```

Migration `V58`:

```sql
ALTER TABLE mrp_runs ADD COLUMN idempotency_key VARCHAR(120), ADD COLUMN payload_hash VARCHAR(64);
ALTER TABLE mrp_runs ADD CONSTRAINT uk_mrp_runs_idempotency_key UNIQUE (idempotency_key);
```

**Lưu ý quan trọng, cũng là một quyết định thiết kế thấy được khi đọc kỹ code:** vì `run()` **nuốt**
exception rồi đánh dấu `FAILED` thay vì rethrow, một run thất bại **vẫn giữ vĩnh viễn** key của nó
(dòng đã insert trước khi calculate chạy). Muốn thử lại sau khi fail phải dùng **key mới** — đây là hệ
quả tất yếu của thiết kế "claim key trước khi tính", không phải thiếu sót.

### 4.3. Ví dụ 2 — `InventoryMovementService.receive`: scope theo cặp `(key, movement_type)`

`src/main/java/com/erp/manufacturing/module/inventory/service/InventoryMovementService.java:61-105`

```java
public InventoryMovementResult receive(InventoryReceiveCommand command, String idempotencyKey,
                                       LotStatus initialStatusForNewLot, boolean allowImplicitLotReuse) {
    String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
    Optional<StockMovement> existing = findReplay(normalizedKey, MovementType.RECEIVE);
    if (existing.isPresent()) {
        idempotency.ensureSamePayload(existing.get().getPayloadHash(), command);
        return new InventoryMovementResult(existing.get(), false);
    }
    ... // resolve item/warehouse/lot, tăng balance
    StockMovement movement = StockMovement.builder()
            .movementType(MovementType.RECEIVE)
            .idempotencyKey(normalizedKey)
            .payloadHash(idempotency.payloadHash(command))
            .build();
    return new InventoryMovementResult(movementRepository.save(movement), true);
}
```

Migration `V37`:

```sql
ALTER TABLE stock_movements DROP CONSTRAINT uk_stock_movements_idempotency_key;
ALTER TABLE stock_movements ADD CONSTRAINT uk_stock_movements_idempotency_key
    UNIQUE (idempotency_key, movement_type);
```

**Bug lịch sử đã sửa (bất biến B69):** trước `V37`, constraint là `UNIQUE (idempotency_key)` — toàn
bảng, không phân biệt loại nghiệp vụ. Cùng một key gửi tới `/receive` rồi (do lỗi client) gửi lại đúng
key đó tới `/issue` sẽ khiến `/issue` trả nhầm lại movement của lần `/receive`. Sau `V37`, mỗi cặp
`(key, movement_type)` là một chứng từ độc lập. **Nguyên tắc phải nhớ:** query replay
(`findByIdempotencyKeyAndMovementType`) và DB constraint **phải luôn cùng một bộ cột** — đổi một bên
mà quên bên kia là nguồn bug thật (query hẹp hơn constraint → replay trả về dòng không xác định trước
khi nhiều dòng trùng key; query rộng hơn → DB từ chối một chứng từ hợp lệ).

`InventoryMovementService` có 5 entry point cùng theo một khuôn này: `receive`, `issue`/`issueReserved`
(dùng chung scope `ISSUE` — có chủ đích, cả hai đều là "xuất kho"), `changeLotStatus`, `adjust`,
`reverseReceive`.

---

## 5. Production Receipt + QC

**Mục đích:** sản lượng làm ra không được vào thẳng tồn kho khả dụng — phải qua 2 lớp kiểm soát:
approve (Manager xác nhận nhận hàng, hàng vào kho ở trạng thái `HOLD`) và QC disposition (quyết định
riêng, tách biệt, ai đó phán quyết `AVAILABLE`/`REJECTED`).

### 5.1. State machine

`ProductionReceiptStatus`: `DRAFT → PENDING_APPROVAL → APPROVED`, nhánh cụt `REJECTED`, cộng
`CANCELLED` (chưa dùng). `POSTED` (tên cũ) đã đổi thành `APPROVED` ở migration `V26`.

### 5.2. `approve` — nơi duy nhất sinh movement + mở lot `HOLD`

`src/main/java/com/erp/manufacturing/module/workorder/service/execution/ProductionReceiptService.java`

```java
public ProductionReceiptResponse approve(UUID workOrderId, UUID receiptId) {
    ProductionReceipt receipt = findPendingReceipt(workOrderId, receiptId);
    WorkOrder workOrder = receipt.getWorkOrder();
    ...
    for (ProductionReceiptLine line : receipt.getLines()) {
        index++;
        InventoryMovementResult movementResult = movementService.receive(new InventoryReceiveCommand(
                workOrder.getProductItem().getItemId(),
                line.getWarehouse().getWarehouseId(),
                line.getLot() != null ? line.getLot().getLotId() : null,
                line.getRequestedLotCode(),
                line.getQuantity(),
                line.getReason(),
                WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE,
                workOrder.getWorkOrderId().toString(),
                null,
                line.getRequestedSerialCode()),
                idempotency.childKey(receipt.getIdempotencyKey() + ":approve", index),
                LotStatus.HOLD,          // <-- lot MỚI mở ở HOLD
                false);
        line.setStockMovement(movementResult.movement());
        line.setLot(movementResult.movement().getLot());
        totalReceived = totalReceived.add(line.getQuantity());
    }
    workOrder.setCompletedQuantity(alreadyReceipted.add(totalReceived));
    receipt.approve(now, auditorAware.getCurrentAuditor().orElse(null));
    ProductionReceipt saved = receiptRepository.save(receipt);
    wipTransactionService.recordOutputReceipted(workOrder, totalReceived, saved.getReceiptId());
    return toResponse(saved);
}
```

**Điểm quan trọng:** `approve` **không** tự chuyển status của Work Order (không `markInProgress`,
không `complete`) — đó là trách nhiệm **duy nhất** của `ProductionExecutionService.report`. Đây là kết
quả của một quyết định thiết kế lớn của hệ thống (F5, "đảo ngược ngữ nghĩa"): trước đây receipt approve
là thứ làm WO tiến triển, giờ tách hẳn ra hai luồng độc lập — `actualGoodQuantity` (xưởng làm ra, do
`ProductionExecutionService` cập nhật) và `completedQuantity` (đã nhập kho, do `approve` cập nhật) là
hai cột khác nhau, luôn có `completedQuantity ≤ actualGoodQuantity`.

### 5.3. `qcDisposition` — rẽ 3 nhánh theo shape của output

```java
public ProductionReceiptResponse qcDisposition(UUID workOrderId, UUID receiptId,
                                               ProductionReceiptQcDispositionRequest request) {
    ProductionReceipt receipt = findReceiptOfWorkOrder(workOrderId, receiptId);
    if (!receipt.isApproved()) throw ...STATE_CONFLICT...;
    if (receipt.isQcDecided()) throw ...STATE_CONFLICT...;   // chỉ được phán quyết MỘT lần

    List<ProductionReceiptLine> lotLines = receipt.getLines().stream()
            .filter(line -> line.getLot() != null).toList();
    List<ProductionReceiptLine> serialLines = receipt.getLines().stream()
            .filter(line -> line.getSerial() != null).toList();

    BigDecimal dispositionedQuantity;
    if (!lotLines.isEmpty())         dispositionedQuantity = dispositionLots(receipt, lotLines, ...);
    else if (!serialLines.isEmpty()) dispositionedQuantity = dispositionSerials(receipt, serialLines, ...);
    else                              dispositionedQuantity = dispositionWithoutLots(receipt, request.result());

    if (request.result() == QualityDispositionResult.AVAILABLE) {
        allocationService.fulfill(receipt.getWorkOrder(), dispositionedQuantity);
    }
    receipt.recordQcDecision(request.result(), reason, now, actor);
    return toResponse(receiptRepository.save(receipt));
}
```

**Nhánh có lot** (`dispositionLots`) — validate mọi lot đang `HOLD` trước (fail-fast, không đổi nửa
chừng), rồi chỉ đổi status:

```java
movementService.changeLotStatus(new LotStatusChangeCommand(
        line.getItem().getItemId(), line.getWarehouse().getWarehouseId(),
        line.getLot().getLotId(), result.lotStatus(), line.getQuantity(), reason,
        WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE, ...),
        idempotency.childKey(receipt.getIdempotencyKey() + ":qc", index));
```

`InventoryMovementService.changeLotStatus` chỉ flip `lot.status` + ghi một dòng `LOT_STATUS_CHANGE`
(direction `NONE`) — **không đụng `StockBalance`**. `REJECTED` ở nhánh này **không** sinh `ADJUST_OUT`:
hàng vẫn nằm on-hand vật lý, chỉ là bị lọc khỏi mọi truy vấn "available" vì `lot.status != AVAILABLE`
(đúng khối 6.3 trong `Outline.md`).

**Nhánh serial** (`dispositionSerials`) — khác nhánh lot: serial-tracked output **không** có giai đoạn
`HOLD` chờ QC (ra `AVAILABLE` ngay lúc approve, quyết định chốt với user để tránh phải thêm cột
`serial_id` vào `stock_balances`), nên khi `REJECTED`, hàng **đã** ở tồn khả dụng và phải bị **rút chủ
động**:

```java
if (result == QualityDispositionResult.REJECTED) {
    movementService.adjust(new InventoryAdjustCommand(
            line.getItem().getItemId(), line.getWarehouse().getWarehouseId(), null, null,
            line.getQuantity().negate(), reason, ...,
            line.getSerial().getSerialId(), null), ...);
}
line.getSerial().setStatus(result == QualityDispositionResult.AVAILABLE
        ? SerialStatus.AVAILABLE : SerialStatus.REJECTED);
```

**Nhánh không lot/không serial** (`dispositionWithoutLots`) — verdict chỉ sống trên chính
`ProductionReceipt` (cột `qc_result`/`qc_reason`/`qc_at`/`qc_by`), **không** ghi vào bảng
`quality_dispositions` (bảng đó định nghĩa là "một dòng cho mỗi lot"):

```java
private BigDecimal dispositionWithoutLots(ProductionReceipt receipt, QualityDispositionResult result) {
    BigDecimal dispositionedQuantity = BigDecimal.ZERO;
    for (ProductionReceiptLine line : receipt.getLines()) {
        if (result == QualityDispositionResult.AVAILABLE) {
            movementService.releaseQualityHold(
                    line.getItem().getItemId(), line.getWarehouse().getWarehouseId(), line.getQuantity());
        }
        dispositionedQuantity = dispositionedQuantity.add(line.getQuantity());
    }
    return dispositionedQuantity;
}
```

`AVAILABLE` gọi `releaseQualityHold` — giảm `StockBalance.qualityHoldQuantity`, **không** sinh movement
nào (hàng đã ở on-hand từ lúc approve; đây thuần là "mở khoá"). `REJECTED` **không làm gì** — hàng vẫn
nằm đó (traceable) nhưng vĩnh viễn không được reserve/issue/MRP dùng vì vẫn tính vào
`qualityHoldQuantity`.

**Tóm tắt 3 nhánh cùng một câu hỏi "REJECTED thì hàng đi đâu":**

| Nhánh | Cơ chế giữ hàng | `REJECTED` làm gì |
|---|---|---|
| Có lot | `lot.status` | Chỉ đổi status, hàng vẫn on-hand nhưng bị lọc |
| Serial | `serial.status` (đã `AVAILABLE` từ approve) | `ADJUST_OUT` rút hàng chủ động |
| Không tracking | `StockBalance.qualityHoldQuantity` | Không làm gì, giữ nguyên hold vĩnh viễn |

---

## 6. Release Gate + Fulfillment

### 6.1. Work Order chỉ release được khi 100% component đã reserve

`src/main/java/com/erp/manufacturing/module/workorder/service/WorkOrderReleaseGate.java`

```java
@Transactional(readOnly = true)
public void ensureMaterialReady(UUID workOrderId) {
    WorkOrder workOrder = workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)...;
    List<WorkOrderMaterialReadinessLineResponse> lines = evaluate(workOrder);
    long shortLines = lines.stream()
            .filter(line -> line.shortageQuantity().compareTo(BigDecimal.ZERO) > 0)
            .count();
    if (shortLines == 0) return;

    blockRecorder.recordBlocked(workOrderId, shortLines + "/" + lines.size() + " components short on reservation");
    throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
            "Cannot release: " + shortLines + " component(s) not fully reserved");
}
```

### 6.2. War story có sẵn trong chính javadoc của code — `WorkOrderBlockRecorder`

`src/main/java/com/erp/manufacturing/module/workorder/service/WorkOrderBlockRecorder.java`

```java
/**
 * Persists the BLOCKED state of gate 1a in a transaction of its own (debt #23, fixed in D9).
 *
 * This exists as a separate bean for one reason: it must return normally. The refusal previously
 * lived inside WorkOrderReleaseGate.ensureMaterialReady, which was annotated REQUIRES_NEW and then
 * threw — and an exception leaving a transactional method marks that very transaction
 * rollback-only, so the BLOCKED it had just saved was discarded with it. The work order stayed
 * DRAFT and B14's promise that planners can query which work orders are waiting for material never
 * held. Writing here and throwing in the caller is what makes the state survive the caller's
 * rollback.
 *
 * Found by ProductionFlowE2EIT; no unit test could have caught it, because a mocked repository
 * reports a save() that a rollback later throws away.
 */
@Component
public class WorkOrderBlockRecorder {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlocked(UUID workOrderId, String reason) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)...;
        workOrder.block(Instant.now(), reason);
        workOrderRepository.save(workOrder);
    }
}
```

**Đây là war story tốt nhất để kể nếu hội đồng hỏi "bug khó nhất bạn từng gặp":** ban đầu, code ghi
`BLOCKED` **rồi throw ngay trong cùng một transaction** `REQUIRES_NEW`. Nghe hợp lý (transaction riêng
để tách khỏi transaction cha), nhưng sai một điểm cơ bản: **exception thoát ra khỏi một method
`@Transactional` sẽ đánh dấu transaction đó rollback-only**, bất kể nó là `REQUIRES_NEW` hay không —
tức là chính `save(BLOCKED)` vừa ghi cũng bị cuốn theo rollback. Kết quả: Work Order thực tế vẫn nằm ở
`DRAFT`, không ai biết nó đang chờ vật tư gì. **Cách sửa đúng:** tách hẳn thành 2 bean — một bean chỉ
ghi rồi **return bình thường** (transaction của nó commit độc lập), bean gọi nó (gate) mới là nơi
throw. Bug này **không** một unit test nào (dùng mock repository) có thể bắt được, vì mock không mô
phỏng hành vi rollback thật của transaction — chỉ có integration test chạy Postgres thật
(`ProductionFlowE2EIT`) mới lộ ra.

### 6.3. Sales Order Fulfillment — khép vòng demand → supply

`src/main/java/com/erp/manufacturing/module/workorder/service/WorkOrderDemandAllocationService.java`

```java
public void fulfill(WorkOrder workOrder, BigDecimal quantity) {
    ...
    for (WorkOrderDemandAllocation allocation : sortByDueDate(allocations, targets)) {
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
        SalesOrderAllocationTarget target = targets.get(allocation.getSalesOrderLineId());
        BigDecimal room = allocation.remainingQuantity().min(target.openQuantity());
        if (room.compareTo(BigDecimal.ZERO) <= 0) continue;
        BigDecimal applied = room.min(remaining);
        allocation.addFulfilled(applied);
        remaining = remaining.subtract(applied);
        appliedByLine.put(allocation.getSalesOrderLineId(), applied);
    }
    allocationRepository.saveAll(touched);
    salesOrderFulfillmentService.applyFulfillment(appliedByLine);
}
```

Duyệt allocation theo thứ tự **xác định**: `dueDate` → `lineNo` → `salesOrderLineId` — không phụ
thuộc thứ tự DB trả về. `room = min(phần allocation còn thiếu, phần SO line còn mở)` — chặn ở **cả
hai** trần cùng lúc, vì một Sales Order line có thể được phủ một phần bởi Work Order khác rồi.

`SalesOrderFulfillmentService.applyFulfillment` cộng `fulfilledQuantity` lên từng SO line rồi roll-up
status:

```java
private void rollUpStatus(SalesOrder order) {
    if (order.getStatus() == SalesOrderStatus.CANCELLED || order.getLines().isEmpty()) return;
    if (order.getLines().stream().allMatch(SalesOrderLine::isFullyFulfilled)) {
        order.markFulfilled();
    } else if (order.getLines().stream()
            .anyMatch(line -> line.getFulfilledQuantity().compareTo(BigDecimal.ZERO) > 0)) {
        order.markPartiallyFulfilled();
    }
}
```

`WorkOrderDemandAllocation.salesOrderLineId` là **cột UUID phẳng, không phải `@ManyToOne`** — lý do:
hướng phụ thuộc `sales → planning → workorder` đã tồn tại, nếu thêm association kiểu (`@ManyToOne` vào
`SalesOrderLine`) sẽ đóng thành vòng phụ thuộc compile-time `workorder → sales → ... → workorder`. Mọi
thứ `workorder` cần biết về SO line (due date, line no, open quantity) đọc qua
`SalesOrderFulfillmentService` — một lookup/application service, đúng rule `C7` (cross-module chỉ gọi
qua service của module đó, không gọi thẳng repository).

---

## 7. MRP Engine

Đây là phần đã trình bày trong `Outline.md` mục 6.1. Khối này ghép lại đầy đủ nhất, đọc trực tiếp từ
`MrpCalculationService.java` và `MrpRunService.java`.

### 7.1. Immutable snapshot — `MrpRunService.run`

`src/main/java/com/erp/manufacturing/module/planning/service/MrpRunService.java:78-154`

```java
List<PlanningDemand> demands = resolveDemands(request, company, plant, warehouse);  // resolve TRƯỚC
MrpRun run = MrpRun.builder()...build();
run.start(Instant.now());
run = mrpRunRepository.saveAndFlush(run);        // claim run NGAY

mrpRunDemandRepository.saveAll(demands.stream()
        .map(demand -> snapshotDemand(persistedRun, demand))
        .toList());

try {
    MrpCalculationService.MrpCalculationResult result = calculationService.calculate(persistedRun, demands, scopeWarehouseIds);
    persistRequirements(...); persistSuggestions(...);
    persistedRun.complete(Instant.now(), demands.size(), ...);
} catch (RuntimeException e) {
    persistedRun.fail(Instant.now(), e.getMessage());
}
```

```java
private MrpRunDemand snapshotDemand(MrpRun run, PlanningDemand demand) {
    return MrpRunDemand.builder()
            .mrpRun(run).planningDemand(demand)
            .item(demand.getItem()).warehouse(demand.getWarehouse())
            .requiredQuantity(demand.getRequiredQuantity())
            .dueDate(demand.getDueDate()).priority(demand.getPriority())
            .build();
}
```

`snapshotDemand` copy dữ liệu **sống** của demand (item, warehouse, quantity, due date, priority)
thành một dòng bất biến `MrpRunDemand` — sửa Sales Order sau đó **không** viết lại lịch sử run đã
chạy. `MrpRunStatus`: `PENDING, RUNNING, COMPLETED, FAILED, CANCELLED`.

### 7.2. Nổ BOM đa cấp — BFS theo level, không phải đệ quy hàm-gọi-hàm

`src/main/java/com/erp/manufacturing/module/planning/service/MrpCalculationService.java:40-135`

```java
List<RequirementSeed> currentLevel = demands.stream().map(demand -> topLevelSeed(run, demand)).toList();
Map<ItemScopeKey, BigDecimal> consumedCoverageByItem = new HashMap<>();

while (!currentLevel.isEmpty()) {
    Set<UUID> manufacturableItemIds = currentLevel.stream()
            .map(RequirementSeed::item).filter(this::canHaveBom)
            .map(Item::getItemId).collect(Collectors.toCollection(LinkedHashSet::new));
    Map<UUID, BomHeader> activeBoms = bomLookupService.findActiveBoms(companyId, manufacturableItemIds);
    Map<UUID, RoutingSummary> activeRoutings = routingLookupService.findActiveRoutingSummaries(companyId, manufacturableItemIds);
    LevelSupplySnapshot supplySnapshot = loadLevelSupplySnapshot(run, currentLevel, scopeWarehouseIds);

    List<RequirementSeed> nextLevel = new ArrayList<>();
    for (RequirementSeed seed : currentLevel) {
        // ... tính netRequiredQuantity (xem 7.3)
        if (netRequiredQuantity.compareTo(BigDecimal.ZERO) > 0 && activeBom != null) {
            nextLevel.addAll(expandChildren(requirement, seed, activeBom, netRequiredQuantity, suggestedOrderDate));
        }
    }
    currentLevel = nextLevel;
}
```

Mỗi vòng lặp `while` xử lý **một cấp BOM**: gom mọi item manufacturable ở cấp hiện tại, **bulk-fetch**
BOM/routing của chúng bằng đúng **một** query mỗi loại (rule `C14` — không query theo từng item), tính
netting, rồi nổ ra `nextLevel` cho vòng kế tiếp. Đây là lý do MRP không bị N+1 dù cây BOM có thể sâu
nhiều cấp.

**Cycle guard** — mỗi `RequirementSeed` mang theo `path`, tập hợp item id đã đi qua trên đúng nhánh
gốc-tới-nút-hiện-tại:

```java
private record RequirementSeed(..., LinkedHashSet<UUID> path, ...) {}

private List<RequirementSeed> expandChildren(...) {
    for (BomLine line : activeBom.getLines()) {
        Item component = line.getComponentItem();
        if (seed.path().contains(component.getItemId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE);
        }
        LinkedHashSet<UUID> childPath = new LinkedHashSet<>(seed.path());
        childPath.add(component.getItemId());
        BigDecimal grossRequiredQuantity = parentNetRequiredQuantity
                .multiply(line.getQuantityPer())
                .multiply(BigDecimal.ONE.add(line.getScrapRate()));
        children.add(new RequirementSeed(..., childWarehouse, grossRequiredQuantity, ..., childPath, ...));
    }
}
```

Nếu một component sắp được thêm vào `nextLevel` đã có mặt trong `path` của chính nó (BOM tự tham
chiếu vòng) → ném `BOM_CIRCULAR_REFERENCE` ngay, không bao giờ vào vòng lặp vô hạn. `path` là bản sao
riêng cho mỗi nhánh (`LinkedHashSet<>(seed.path())`) nên hai nhánh anh em không ảnh hưởng lẫn nhau.

### 7.3. Công thức netting

```java
BigDecimal stockTargetQuantity = max(inventory.safetyStockQuantity(), inventory.reorderPointQuantity());
BigDecimal remainingCoverage = positiveDifference(
        inventory.availableQuantity().add(openSupplyQuantity),
        consumedCoverageByItem.getOrDefault(itemScopeKey, BigDecimal.ZERO));
BigDecimal netRequiredQuantity = positiveDifference(
        seed.grossRequiredQuantity().add(stockTargetQuantity),
        remainingCoverage);
BigDecimal consumedCoverage = seed.grossRequiredQuantity().add(stockTargetQuantity).min(remainingCoverage);
consumedCoverageByItem.merge(itemScopeKey, consumedCoverage, BigDecimal::add);
```

```java
private BigDecimal positiveDifference(BigDecimal left, BigDecimal right) {
    BigDecimal difference = left.subtract(right);
    return difference.compareTo(BigDecimal.ZERO) > 0 ? difference : BigDecimal.ZERO;
}
```

Diễn dịch ra công thức toán:

```
stockTarget       = max(safetyStock, reorderPoint)
remainingCoverage = max(0, (available + openSupply) - đã bị các line TRƯỚC dùng)
netRequired       = max(0, (gross + stockTarget) - remainingCoverage)
```

Đúng khớp `netRequirement = max(0, grossRequirement + stockTarget − projectedAvailable)` đã nêu trong
`Outline.md`, với `projectedAvailable` chính là `remainingCoverage`.

**Cơ chế chống đếm trùng cung ứng** — `consumedCoverageByItem` (một `Map<ItemScopeKey, BigDecimal>`,
key = `(itemId, warehouseId)`) sống trong **suốt một lần gọi `calculate()`**: mỗi requirement line xử
lý xong sẽ "ghi sổ" phần `available + openSupply` mà nó vừa dùng
(`consumedCoverage = min(gross+stockTarget, remainingCoverage)`), dòng kế tiếp cùng item (VD: cùng một
ốc vít xuất hiện dưới 2 sản phẩm cha khác nhau trong cùng run) sẽ trừ tiếp từ phần **còn lại**, không
được tính lại từ đầu. Không có cơ chế này, một nguồn cung 100 đơn vị sẽ bị "cấp" cho cả hai nhu cầu 80
đơn vị mỗi cái — báo sai là đủ hàng trong khi thực ra thiếu 60.

`openSupplyQuantity` (cung đã lên kế hoạch: Work Order + Purchase Order đang mở) đọc **một lần cho mỗi
cấp BOM** (không phải mỗi seed), qua `loadLevelSupplySnapshot` — cộng dồn từ `WorkOrderSupplyService`
và `PurchaseOrderSupplyService` (hai module khác, gọi qua lookup service theo rule `C7`).

### 7.4. Supply Suggestion — không bao giờ im lặng bỏ qua

```java
private MrpRequirementStatus determineStatus(Item item, BomHeader activeBom, BigDecimal netRequiredQuantity) {
    if (netRequiredQuantity.compareTo(BigDecimal.ZERO) == 0) return MrpRequirementStatus.COVERED;
    if (canHaveBom(item) && activeBom == null) return MrpRequirementStatus.BOM_MISSING;
    return MrpRequirementStatus.SHORTAGE;
}
```

```java
/**
 * A manufacturable item is always a MAKE proposal, even when its BOM or routing is missing: the
 * planner has to see the blocked line to know what master data to fix. BLOCKED is what stops it
 * from becoming a work order, not the absence of a proposal.
 */
private SuggestionDraft suggestionFor(...) {
    SupplySuggestionType suggestionType = canHaveBom(requirement.item())
            ? SupplySuggestionType.WORK_ORDER
            : SupplySuggestionType.PURCHASE_REQUISITION;
    ...
    if (suggestionType == SupplySuggestionType.WORK_ORDER) {
        if (activeBom == null) messages.add(PlanningMessageCode.MISSING_BOM);
        if (routing == null)   messages.add(PlanningMessageCode.MISSING_ROUTING);
    }
    ...
}

private SupplySuggestionExceptionState exceptionStateOf(List<PlanningMessageCode> messages) {
    if (messages.contains(MISSING_BOM) || messages.contains(MISSING_ROUTING)
            || messages.contains(MISSING_WAREHOUSE_POLICY) || messages.contains(AMBIGUOUS_WAREHOUSE_POLICY)) {
        return SupplySuggestionExceptionState.BLOCKED;
    }
    if (messages.contains(SYSTEM_FALLBACK_USED)) return SupplySuggestionExceptionState.WARNING;
    return SupplySuggestionExceptionState.READY;
}
```

Item manufacturable (WIP/FINISHED_GOOD) **luôn** nhận suggestion `MAKE`, kể cả khi thiếu BOM/Routing
`ACTIVE` — chỉ là `exceptionState = BLOCKED` kèm message code giải thích thiếu gì. **Quyết định thiết
kế:** nếu im lặng bỏ dòng đó ra khỏi kết quả, planner sẽ không bao giờ biết cần đi sửa BOM/Routing nào
— shortage thật sự biến mất khỏi tầm nhìn thay vì được cảnh báo.

---

## Tóm tắt file:line để mở nhanh lúc luyện tập

| Khối | File | Method chính |
|---|---|---|
| 1 | `common/audit/BaseEntity.java` | field `@Version` |
| 1 | `common/exception/GlobalExceptionHandler.java:217` | `handleOptimisticLocking` |
| 1 | `module/sales/service/SalesOrderService.java:106` | `update` (`expectedVersion` + `saveAndFlush`) |
| 2 | `module/auth/service/AuthService.java:166` | `refresh` |
| 2 | `common/security/TokenStoreService.java` | `acquireRefreshLock`, `rotateRefreshToken` (Lua), `wasRefreshTokenUsed`, `getSessionStart` |
| 3 | `module/organization/security/PermissionGuard.java` | `hasResourceAccess`, `hasPlantAccess` |
| 3 | `module/organization/repository/UserRoleAssignmentRepository.java` | `existsActiveResourcePermission` |
| 3 | `module/workorder/service/WorkOrderPermissionGuard.java` | `hasWorkOrderAccess` |
| 4 | `common/idempotency/IdempotencySupport.java` | `normalizeKey`, `payloadHash`, `ensureSamePayload` |
| 4 | `module/planning/service/MrpRunService.java:78` | `run` |
| 4 | `module/inventory/service/InventoryMovementService.java:61` | `receive` |
| 5 | `module/workorder/service/execution/ProductionReceiptService.java` | `approve`, `qcDisposition` |
| 6 | `module/workorder/service/WorkOrderReleaseGate.java` | `ensureMaterialReady` |
| 6 | `module/workorder/service/WorkOrderBlockRecorder.java` | `recordBlocked` |
| 6 | `module/workorder/service/WorkOrderDemandAllocationService.java` | `fulfill` |
| 6 | `module/sales/service/SalesOrderFulfillmentService.java` | `applyFulfillment`, `rollUpStatus` |
| 7 | `module/planning/service/MrpRunService.java:78` | `run`, `snapshotDemand` |
| 7 | `module/planning/service/MrpCalculationService.java:40` | `calculate`, `expandChildren`, `suggestionFor` |

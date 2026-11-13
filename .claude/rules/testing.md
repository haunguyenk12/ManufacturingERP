---
paths:
  - "src/test/**"
---

# Quy tắc viết test

> Tách từ `best-practices.md §8.6` (2026-11-12). Bản đầy đủ kèm lịch sử:
> `docs/archive/rules-2026-11-12/best-practices.md`, ví dụ sự cố: `docs/testing-notes.md`.

## Khung test của repo

| # | Quy ước |
|---|---|
| T1 | Unit > Integration > E2E. **Cấm `@SpringBootTest`**, trừ đúng một class: `ProductionFlowE2EIT`. Dùng `@WebMvcTest`, `@DataJpaTest`, `@SpringJUnitConfig` |
| T2 | `*IT.java` kế thừa `AbstractPostgresIntegrationTest` (Testcontainers, `postgres:16-alpine`), chạy qua `mvn verify`. **Bắt buộc viết IT** khi logic nằm trong JPQL, `@EntityGraph`, constraint, `@Version` hoặc migration — mock repository ở đó chỉ là tautology |
| T3 | Mỗi controller thật có một `*ControllerTest` (`@WebMvcTest`, `addFilters = false`, service là `@MockBean`). Thêm controller ⇒ thêm test cùng phase |
| T4 | Mỗi test tự dựng dữ liệu bằng builder trong helper riêng; không chia sẻ state |
| T5 | Tầng HTTP assert `$.code` bằng `ErrorCode.code()`. Endpoint phân trang assert đủ `page`/`size`/`totalElements`/`totalPages`/`first`/`last`. Giá trị mặc định và trần phân trang (20 / 100) assert bằng **literal**, không bằng hằng của `PageableFactory` |
| T6 | `*MethodSecurityTest` cho mỗi service có `@PreAuthorize` (xem R2). `PermissionCatalogTest` chỉ chứng minh permission **tồn tại**; việc role nào **được cấp** nằm ở `FlywayMigrationIT.migrate_v41_*`. Thêm permission ⇒ nghĩ cả tới role |

Ba bẫy IT đã trả giá: (1) tham số `String` nullable đi vào `concat`/`like` phải `cast(... as string)`;
(2) `@EntityGraph` join-fetch hai `List` ⇒ `MultipleBagFetchException`; (3) fixture phải điền cả
collection lồng nhau. Test về commit/rollback (outbox, audit) phải tắt transaction bọc của
`@DataJpaTest`: `@Transactional(propagation = NOT_SUPPORTED)` + `TransactionTemplate` tường minh.

## Quy tắc bắt buộc

**R1 — Assert `ErrorCode`, không chỉ kiểu exception.**
```java
assertThatThrownBy(...)
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                .isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK));
```

**R2 — Test method-security có đủ DENY + VERIFY + ALLOW.** Chỉ có nhánh deny thì sai tên permission
vẫn xanh (mock trả `false` mặc định).
```java
verify(permissionGuard).hasResourceAccess(any(), eq("PERM_BOM_MANAGE"), eq("COMPANY"), eq(companyId));
```
Nghiệm thu: đổi chuỗi `PERM_*` trong `@PreAuthorize` thành rác ⇒ test phải đỏ.

**R3 — Không mock class chứa logic nghiệp vụ.** Mapper và helper thuần dùng instance thật. Chỉ mock
repository, service của module khác, hạ tầng (Redis, JWT, clock). Không dựng service ở trạng thái
production không thể có (vd bỏ trống một dependency bắt buộc).

**R4 — Verify side-effect**, không chỉ giá trị trả về (ghi Redis, audit, gọi movement service).

**R5 — Nhánh bị chặn phải chứng minh không rò xuống tầng dưới**: `verifyNoInteractions(...)` hoặc
`verify(x, never())`.

**R6 — Assert bằng con số nghiệp vụ thật**, khoá cả công thức lẫn scale:
`isEqualByComparingTo("22.00")`, không `isNotNull()`.

**R7 — Tên test mô tả đúng nội dung.** Logic nằm trong JPQL thì viết `@DataJpaTest`, không giả vờ
test bằng mock.

**R8 — Mã lỗi assert bằng enum**: `jsonPath("$.code").value(BusinessErrorCode.X.code())`, không
chuỗi cứng. Ngoại lệ duy nhất: `"SUCCESS"` — đó là literal của envelope, không thuộc `ErrorCode`.

**R9 — Idempotency phải có test** cho mọi POST nhạy cảm: gửi trùng key ⇒ trả document cũ +
`verifyNoInteractions(<service ghi tồn kho>)`.

**R10 — Không xoá test để build xanh.** Nghiệp vụ đổi thì sửa hoặc thay test; số test sau mỗi phase
≥ baseline.

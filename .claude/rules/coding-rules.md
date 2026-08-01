# Coding Rules – Tóm Tắt Áp Dụng Hàng Ngày

> Tách từ `CLAUDE.md` §11 (2026-07-25), hợp nhất với `AGENTS.md` (đã xoá 2026-07-25 sau khi nội dung
> được phân bổ hết vào các file split — xem "BẢN ĐỒ TÀI LIỆU" ở cuối `CLAUDE.md`). Mục này liệt kê
> những lỗi **thực tế hay mắc** trong repo này.

## 11.1 Bắt buộc

| # | Rule |
|---|---|
| C1 | Controller **chỉ** nhận/validate request → gọi service → wrap `ApiResponse`. Không business logic, không `@Transactional` |
| C2 | Không trả entity ra API. DTO là **`record`**, tách hoàn toàn Request/Response |
| C3 | Constructor injection (`@RequiredArgsConstructor`). **Không** `@Autowired` field |
| C4 | `@Transactional` đặt ở **service**; read-only method dùng `@Transactional(readOnly = true)` |
| C5 | **Không sửa migration cũ.** Mọi thay đổi schema = migration mới, số hiệu tiếp theo (`V25`, `V26`...) |
| C6 | Không hard-delete document nghiệp vụ. Dùng status / cancel / reversal |
| C7 | Cross-module: gọi qua lookup/application service của module đó, **không** gọi repository của module khác. Ví dụ đã có: BOM cần item → `ItemLookupService`; Work Order cần BOM → `BomLookupService`; Planning cần BOM + inventory availability → lookup/service tương ứng của module đó |
| C8 | `orElseThrow()` với message rõ ràng — **không** `Optional.get()` |
| C9 | Fail fast: validate ở đầu method, throw ngay khi invalid, **trước khi** đụng tồn kho / ghi DB |
| C10 | Thêm permission mới ⇒ **bắt buộc** seed trong migration cùng phase + ghi vào `docs/roles-and-permissions.md` |

## 11.2 Chống N+1 (đã có tiền lệ trong repo)

| # | Rule |
|---|---|
| C11 | Association mặc định `FetchType.LAZY`. Không `EAGER` collection cho nghiệp vụ mới |
| C12 | Detail endpoint: `@EntityGraph` / `JOIN FETCH` (pattern có sẵn: `findWithDetailsByXxxId`, `findWithLinesByXxxId`) |
| C13 | **Không** join-fetch collection cùng pagination |
| C14 | Report / availability / variance / readiness: **1 aggregate query**, không loop repository theo từng line.<br>Tiền lệ đúng: `MaterialReservationRepository.sumActiveRemainingByWorkOrderId`, `StockBalanceRepository.aggregate*` |
| C15 | Khi thêm aggregate query mới ⇒ thêm test verify repository được gọi **đúng 1 lần** |

## 11.3 Khi thêm status mới vào enum (bài học từ P1)

Thêm 1 giá trị enum status là **breaking change ngầm**. Checklist bắt buộc:

1. `grep -rn "<EnumName>\." src/main` — rà **mọi** `switch` / so sánh, xử lý nhánh mới
2. Sửa `CHECK constraint` tương ứng bằng migration mới (`DROP CONSTRAINT` → `ADD CONSTRAINT`)
3. Kiểm tra các method `canXxx()` / `isXxx()` trên entity có cần cập nhật không
4. Dữ liệu cũ phải vẫn hợp lệ — **không** yêu cầu backfill (tái dùng giá trị cũ nếu được)
5. Cập nhật `docs/system-flow.md` (sơ đồ trạng thái) và các file bất biến nghiệp vụ liên quan (xem mục "BẢN ĐỒ TÀI LIỆU" ở cuối `CLAUDE.md`)
6. Thêm test cho **cả nhánh vào** và **nhánh ra** của status mới

## 11.4 Khi làm breaking change

1. Ghi rõ vào mục "Breaking Changes" của `NEXT_PHASE_PLAN.md` phase hiện tại
2. Chạy `mvn -q test` lấy **baseline** trước khi bắt đầu
3. **Sửa** test cũ cho khớp — **không xoá**, không nới assertion (xem rule `R10` trong `.claude/rules/best-practices.md` §8.6.1)
4. Kết thúc: số test case **≥ baseline**, `failures = 0`
5. Cập nhật `docs/architecture.md`, `docs/system-flow.md`, `docs/roles-and-permissions.md`

## 11.5 Cấm

- ❌ Tạo abstraction/interface cho code chỉ có 1 implementation
- ❌ Thêm config/flexibility không ai yêu cầu
- ❌ "Tiện tay" refactor code ngoài phạm vi task
- ❌ Xoá test để build xanh
- ❌ Sửa migration đã tồn tại
- ❌ Log password / raw token / refresh token (xem `common/security/CLAUDE.md` §4.18)
- ❌ Expose stack trace ra API response
- ❌ Thay đổi API hiện có nếu không được yêu cầu; cần breaking change thì phải nêu rõ (xem §11.4)

## 11.6 Database Schema Conventions (từ `AGENTS.md` §4)

- PostgreSQL là source of truth; Flyway migration là cách **duy nhất** thay đổi schema.
- Primary key: **UUID**.
- Quantity: **`NUMERIC(19,6)`**.
- Bảng mutable business entity cần đủ audit field: `created_at`, `updated_at`, `created_by`, `updated_by`, `version`.
- Ledger/document nghiệp vụ nên có: `status`, `reference_type`, `reference_id`, và `idempotency_key` với POST nhạy cảm.
- Index bắt buộc cho FK, `status`, `created_at`, và các cột filter chính của query hay dùng.
- Check constraint cho quantity/status/type quan trọng.

## 11.7 Definition of Done (từ `AGENTS.md` §13)

Một task chỉ được xem là xong khi:

- Code compile.
- Test liên quan pass.
- Không sửa migration cũ.
- Không phá API hiện có nếu không được yêu cầu.
- Logic transaction đúng.
- Không tạo N+1 query rõ ràng.
- Có audit cho thao tác quan trọng (xem `common/audit/CLAUDE.md` "Việc bắt buộc audit").
- Có validation và error response rõ ràng.
- Có thể giải thích luồng nghiệp vụ bằng ngôn ngữ domain, không chỉ bằng tên class.
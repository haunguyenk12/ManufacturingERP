Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.



---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

do not delete 47 lines above

# Manufacturing ERP – Capstone Project
> **Role**: Backend Developer | **Stack**: Spring Boot 3.x · PostgreSQL · Redis · Java 17

---

## 0. TRẠNG THÁI DỰ ÁN  *(cập nhật: 2026-11-06)*

> **Đọc mục này TRƯỚC KHI sửa bất kỳ dòng code nào.**
> Thứ tự tài liệu bắt buộc: `CLAUDE.md` (file này) →
> `NEXT_PHASE_PLAN.md` (phase nghiệp vụ đang chạy) → `TEST_IMPROVEMENT_PLAN.md` (phase test).
> *(`AGENTS.md` đã xoá 2026-07-25 — nội dung được phân bổ vào `CLAUDE.md` và các file split, xem "BẢN ĐỒ TÀI LIỆU" ở cuối file này.)*

> 🔴 **Lịch sử phase đã chuyển khỏi file này (2026-11-06).** Trước đó `CLAUDE.md` nặng ~534 KB (~170k token) và bị nạp vào *mọi* phiên.
> Toàn bộ `§0.1`–`§0.72` (trừ `§0.5`) nằm **nguyên văn** ở `docs/phases/` — mở `docs/phases/INDEX.md` để tra.
> Mọi tham chiếu dạng "xem `CLAUDE.md §0.45`" / "nợ #26" trong tài liệu khác vẫn đúng, chỉ khác chỗ chứa:
> `§0.1` = bảng phase + baseline test + coverage · `§0.2` = module đã xong · `§0.4` = nợ kỹ thuật · `§0.6`–`§0.72` = bản ghi từng phase.

### 0.0 Trạng thái hiện tại *(đọc nhanh — chi tiết ở `docs/phases/`)*

| | |
|---|---|
| **Phase đang chạy** | Track **`CF-*`** (Capstone 3 follow-up, `BE_GAP_NEXT_PLAN.md`): `CF-0`→`CF-5` ✅, `CF-8` ✅, `CF-9`–`CF-13` ✅, gói bổ sung 30/10 ✅ (2026-11-02). `CF-6` (sổ giá trị tồn kho) **hoãn**; `CF-7` chọn phương án A (giữ giới hạn scale 6 cho BOM). Bản ghi: `docs/phases/0.72.md`. Việc lẻ `AP-1` (luồng mật khẩu/tài khoản cho FE, 2026-11-06): `docs/phases/AP-1.md` |
| **GĐ nghiệp vụ FE** | `G2`–`G11` đều **BE xong** (giá thành `G11` xong 2026-10-27), chờ FE nghiệm thu. Bản ghi: `0.50`–`0.71` |
| **Migration mới nhất** | **`V96`** (`AP-1`, bảng `password_challenges` cho mã email đặt/đổi mật khẩu). `MANUFACTURING_PHASE_PLAN_P7_P11.md` (WIP valuation, shop floor, traceability, CRP, MPS) đánh số lại từ **`V97`** |
| **Bất biến mới nhất** | **`B282`** (`module/auth`: endpoint công khai khai ở cả `SecurityConfig` lẫn `JwtAuthenticationFilter.BYPASS_PATHS`; `B275`–`B282` là luồng mã email). Bất biến theo module nằm ở `CLAUDE.md` của từng module |
| **Baseline test** | **2378 unit + 366 IT** (`AP-1`, 2026-11-06, `clean verify` xanh). Coverage JaCoCo chưa đo lại từ `D8b` (xem `0.1`) |
| **Phase kế tiếp** | Chưa chốt. Roadmap: `NEXT_PHASE_PLAN.md`, `MANUFACTURING_PHASE_PLAN_P7_P11.md` |

### 0.0.1 Quy tắc ghi chép (thay cho `dev-workflow.md §6.5` cũ)

- Phase mới ⇒ tạo `docs/phases/<mã phase>.md` (nghiệm thu mutation, smoke, breaking change, hệ quả) + thêm **một dòng** vào `docs/phases/INDEX.md`.
- Chỉ sửa `CLAUDE.md` ở **§0.0** (vài ô trong bảng trên) và ở `CLAUDE.md` của module nếu có bất biến `B<n>` mới. **Không** dán lại bản ghi dài vào file này.
- Không sửa `CLAUDE.md` giữa phiên làm việc — mỗi lần sửa làm mất prompt cache của cả file; gom về cuối phiên.

### 0.0.2 Làm việc tiết kiệm token

- Chạy test qua `bash scripts/mvn-summary.sh <mvn args>` (log đầy đủ ra file, chỉ in tóm tắt). Thang chi phí: `-Dtest=OneClass` → cả module → `*IT` (chỉ khi đụng JPQL/migration/`@EntityGraph`) → `clean verify` một lần cuối phase.
- Không `Read` nguyên file lớn (vd `ProductionReceiptServiceTest` 148 KB, `FlywayMigrationIT` 142 KB, `ProductionFlowE2EIT` 109 KB): `Grep -n` rồi `Read` với `offset`/`limit`.
- Tìm kiếm rộng ⇒ giao subagent `Explore`; nghiệm thu mutation / smoke HTTP ⇒ giao subagent, chỉ nhận bảng kết quả.
- Một phiên một việc; `/clear` khi đổi việc.
- Đừng sửa file nguồn trong lúc `mvn` đang chạy (Java LS ghi đè `target/classes` ⇒ lỗi giả).

### 0.5 ✅ Đảo Ngược Ngữ Nghĩa — ĐÃ XỬ LÝ Ở `F5-A` (2026-07-27)

Đây từng là rủi ro lớn nhất của track `F*`. **Đã xong**, giữ lại bảng để agent sau hiểu vì sao code
trông như hiện nay:

| | Trước `F5` | Sau `F5-A` (hiện tại) |
|---|---|---|
| Ai làm WO tiến triển | **Receipt approve** | **`ProductionExecutionService.report`** — operator post good/scrap/rework |
| Trần của receipt (**B16**) | `plannedQuantity − completedQuantity − Σ(DRAFT + PENDING_APPROVAL)` | `actualGoodQuantity − completedQuantity − Σ(DRAFT + PENDING_APPROVAL)` |
| WO → `COMPLETED` khi | receipt approve đủ số | cumulative good = planned, **độc lập receipt** |

**Ba cột số lượng trên `work_orders`, đừng nhầm:** `plannedQuantity` (kế hoạch) ·
`actualGoodQuantity`/`actualScrapQuantity`/`actualReworkQuantity` (xưởng làm ra) ·
`completedQuantity` (**đã nhập kho** — giữ nguyên tên và ngữ nghĩa cũ, có chủ đích, để dữ liệu lịch
sử không bị diễn giải lại). Chi tiết + bất biến `B53`-`B57`: `module/workorder/CLAUDE.md`.

## 1. PROJECT OVERVIEW

### 1.1 Description
Manufacturing ERP là hệ thống hoạch định nguồn lực doanh nghiệp dành riêng cho **sản xuất** (nhà máy lắp ráp, sản xuất theo quy trình). Khác với Commercial ERP (mua-bán-phân phối), hệ thống quản lý **vòng đời sản xuất đầu cuối** — từ nguyên liệu thô đến thành phẩm.

**Mục tiêu nghiệp vụ lõi** (từ `AGENTS.md` §1, đã xoá):
- Inventory accuracy
- Multi-level BOM
- Shortage detection
- Work Order execution
- Material issue / production receipt / WIP tracking / variance

### 1.2 So Sánh với Commercial ERP

| Chiều | Manufacturing ERP | Commercial ERP |
|---|---|---|
| Sản xuất | BOM đa cấp, Work Order, Routing, MRP I/II | Không có / cơ bản |
| Tồn kho | WIP, theo dõi Batch/Lot/Serial | Chỉ kho tiêu chuẩn |
| Chi phí | Job/Process/Standard Costing + Phân tích variance | Chỉ COGS |
| Hoạch định | MRP + CRP (tự động yêu cầu vật tư/năng lực) | Dự báo cầu cơ bản |
| Truy xuất nguồn gốc | Toàn chuỗi (bắt buộc) | Tùy chọn |
| Tích hợp MES | Giao diện ERP ↔ MES 2 chiều | Không cần |

### 1.3 Lộ Trình Module

```
Phase 1 – Foundation
├── Auth & Security (JWT, RBAC, Session management)
├── User & Organization Management
└── Inventory / Warehouse (cờ IsManufacturing)

Phase 2 – Manufacturing Core
├── Bill of Materials (BOM đa cấp)
├── Work Order Management
├── Material Requirements Planning (MRP)
└── Shop Floor Control

Phase 3 – Advanced & Integration
├── MES Integration (chuẩn ISA-95)
├── Quality Control (truy xuất Batch/Lot)
├── Costing Engine (Standard/Job/Process)
└── Reporting & Analytics (OEE, Variance)
```

### 1.4 Tham Chiếu Thực Tế
- **VinFast / Foxconn**: BOM 8–10 cấp + MES thời gian thực trên dây chuyền lắp ráp
- **Masan / TH True Milk**: Sản xuất theo quy trình + kiểm soát Batch/phế liệu
- Mô hình kiến trúc: **ISA-95** (chuẩn tích hợp ERP ↔ MES)

---

## 2. TECHNOLOGY STACK

```yaml
language:       Java 17 (records, sealed classes, pattern matching)
framework:      Spring Boot 3.x
security:       Spring Security 6 + JWT (jjwt)
database:
  primary:      PostgreSQL (quan hệ, ACID)
  cache:        Redis (token store, session blacklist, rate-limit)
orm:            Spring Data JPA + Hibernate
migration:      Flyway
build:          Maven
documentation:  Springdoc OpenAPI 3 (Swagger UI)
testing:        JUnit 5 · Mockito · MockMvc · Testcontainers
monitoring:     Spring Boot Actuator + Micrometer
logging:        SLF4J + Logback (JSON có cấu trúc trong prod)
containerize:   Docker + Docker Compose
```

---

## 3. PROJECT CONVENTIONS

### 3.1 Quy Tắc Đặt Tên
- **PascalCase** → tên class (`WorkOrderService`, `BomController`)
- **camelCase** → phương thức & biến (`findByWorkOrderId`, `isExpired`)
- **ALL_CAPS** → hằng số (`DEFAULT_PAGE_SIZE`, `TOKEN_PREFIX`)
- **snake_case** → cột database, file migration Flyway

### 3.2 Cấu Trúc Package
```
com.erp.manufacturing
├── common/
│   ├── exception/          # Phân cấp exception toàn cục
│   ├── response/           # ApiResponse, PageResult wrappers
│   ├── security/           # JWT utils, filters, IpExtractor, TokenStoreService — CLAUDE.md riêng
│   ├── audit/              # BaseEntity, AuditLog entity, AuditLogService, Aspect — CLAUDE.md riêng
│   └── context/            # RequestContext (userId, ip, traceId) – ThreadLocal holder
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── AsyncConfig.java            # @EnableAsync + Executor truyền MDC
│   ├── JpaAuditingConfig.java      # @EnableJpaAuditing
│   ├── RateLimitProperties.java    # @ConfigurationProperties
│   └── OpenApiConfig.java
├── module/
│   ├── auth/               # AuthController, AuthService, PasswordResetTokenService — CLAUDE.md riêng
│   ├── user/
│   ├── organization/       # roles/permissions/access_scopes, AccessControlService — CLAUDE.md riêng
│   ├── inventory/          # CLAUDE.md riêng
│   ├── bom/                # CLAUDE.md riêng
│   ├── workorder/          # CLAUDE.md riêng
│   ├── routing/            # Routing master data + snapshot lên Work Order — CLAUDE.md riêng
│   ├── sales/              # Sales Order + independent demand cho MRP — CLAUDE.md riêng
│   ├── planning/           # MRP run, requirement explosion, supply suggestion — CLAUDE.md riêng
│   ├── purchasing/         # Supplier, PR → PO → Goods Receipt — CLAUDE.md riêng
│   └── reporting/          # Report controller theo domain (inventory/workorder/purchasing)
└── ManufacturingErpApplication.java
```

> Các thư mục ghi "CLAUDE.md riêng" có file `CLAUDE.md` lồng bên trong — Claude Code chỉ tự nạp file đó
> khi có agent thực sự đọc/sửa code trong đúng thư mục ấy. Xem bảng đầy đủ ở "Bản Đồ Tài Liệu" dưới đây.

### 3.3 Quy Ước API
- Base path: `/api/v1/`
- Auth endpoints: `/api/v1/auth/**` (permit all)
- Protected: `/api/v1/**` (yêu cầu JWT hợp lệ)
- HTTP status codes: tuân thủ chặt chẽ ngữ nghĩa REST
- Phân trang: query params `page`, `size`, `sortBy`, `sortDir`

---

## BẢN ĐỒ TÀI LIỆU

> File này đã được chia nhỏ (2026-07-25) để agent chỉ tự nạp phần liên quan đến task đang làm,
> thay vì luôn nạp toàn bộ ~1500 dòng. Cơ chế:
> - `.claude/rules/*.md` → Claude Code **luôn** tự nạp cùng `CLAUDE.md`, dùng cho rule cross-cutting (áp dụng mọi module).
> - `<package>/CLAUDE.md` (lồng trong `src/main/java/...`) → chỉ tự nạp khi agent đọc/sửa file trong đúng thư mục đó.

| Chủ đề | File | Cơ chế nạp |
|---|---|---|
| Error handling / response contract | `.claude/rules/error-handling.md` | Luôn nạp |
| Git workflow / commit convention / migration naming | `.claude/rules/dev-workflow.md` | Luôn nạp |
| Key architectural decisions | `.claude/rules/architecture-decisions.md` | Luôn nạp |
| Best practices checklist (security/API/DB/code quality/perf/test/observability) | `.claude/rules/best-practices.md` | Luôn nạp |
| Coding rules hàng ngày (N+1, breaking change, cấm) | `.claude/rules/coding-rules.md` | Luôn nạp |
| Auth/Session design (JWT, Redis key, rate limit, RTR, fingerprint) + bất biến B35-B36 | `src/main/java/com/erp/manufacturing/common/security/CLAUDE.md` | Chỉ khi chạm `common/security/**` |
| AuthService/AuthController + bất biến B33-B34 | `src/main/java/com/erp/manufacturing/module/auth/CLAUDE.md` | Chỉ khi chạm `module/auth/**` |
| Audit Log Architecture | `src/main/java/com/erp/manufacturing/common/audit/CLAUDE.md` | Chỉ khi chạm `common/audit/**` |
| Bất biến RBAC B30-B32, B37 | `src/main/java/com/erp/manufacturing/module/organization/CLAUDE.md` | Chỉ khi chạm `module/organization/**` |
| Bất biến Inventory B1-B6 | `src/main/java/com/erp/manufacturing/module/inventory/CLAUDE.md` | Chỉ khi chạm `module/inventory/**` |
| Bất biến BOM B7-B11 | `src/main/java/com/erp/manufacturing/module/bom/CLAUDE.md` | Chỉ khi chạm `module/bom/**` |
| Bất biến Work Order & Manufacturing Execution B12-B20 | `src/main/java/com/erp/manufacturing/module/workorder/CLAUDE.md` | Chỉ khi chạm `module/workorder/**` |
| Bất biến Planning/MRP B21-B26 | `src/main/java/com/erp/manufacturing/module/planning/CLAUDE.md` | Chỉ khi chạm `module/planning/**` |
| Bất biến Routing B48-B52 + quyết định `MISSING_ROUTING` / Work Center FK (`C2-6`) | `src/main/java/com/erp/manufacturing/module/routing/CLAUDE.md` | Chỉ khi chạm `module/routing/**` |
| Bất biến Sales Order B43-B47 + quyết định `sales`→`planning` | `src/main/java/com/erp/manufacturing/module/sales/CLAUDE.md` | Chỉ khi chạm `module/sales/**` |
| Bất biến Purchasing B27-B29 | `src/main/java/com/erp/manufacturing/module/purchasing/CLAUDE.md` | Chỉ khi chạm `module/purchasing/**` |
| Bất biến UOM B82-B85 + giới hạn `hasPermission`/`GLOBAL` scope (`C2-3`) | `src/main/java/com/erp/manufacturing/module/uom/CLAUDE.md` | Chỉ khi chạm `module/uom/**` |
| Bất biến Work Center B_wc1-B_wc4 (`C2-6`, `B_wc4` ở `C2-7`) | `src/main/java/com/erp/manufacturing/module/workcenter/CLAUDE.md` | Chỉ khi chạm `module/workcenter/**` |
| Bất biến Shift/Work Calendar B_sh1-B_sh2, B_cal1-B_cal2 + quy ước qui-thuộc-ngày ca qua đêm (`C2-7`) | `src/main/java/com/erp/manufacturing/module/shift/CLAUDE.md` | Chỉ khi chạm `module/shift/**` |
| Bất biến Billing B246-B255 (hoá đơn bán hàng `G10c`, thanh toán + tiến độ đơn `G10d`) | `src/main/java/com/erp/manufacturing/module/billing/CLAUDE.md` | Chỉ khi chạm `module/billing/**` |
| Bất biến Delivery B241-B245 (phiếu giao hàng, `G10b`) | `src/main/java/com/erp/manufacturing/module/delivery/CLAUDE.md` | Chỉ khi chạm `module/delivery/**` |
| Bất biến Data Import B142-B148 + hai đường nhập chung bảng (`G3`) | `src/main/java/com/erp/manufacturing/module/dataimport/CLAUDE.md` | Chỉ khi chạm `module/dataimport/**` |
| Bất biến Costing B91-B92 + entry points `ItemStandardCostLookupService` (`P3`) | `src/main/java/com/erp/manufacturing/module/costing/CLAUDE.md` | Chỉ khi chạm `module/costing/**` |
| **Hướng dẫn API cho FE** (envelope, auth, luồng 10 bước, mã lỗi, chỗ lệch spec) | `docs/api-guide-for-frontend.md` | Đọc thủ công — **tài liệu đối ngoại**, viết cho team FE |
| **Session bootstrap cho FE** (decode JWT lấy permissions, workaround profile/plant/scope) | `docs/fe-session-bootstrap.md` | Đọc thủ công — **tài liệu đối ngoại**. Ghi rõ 2 khoảng trống: không có `GET /auth/me`, không có "default plant" |
| **Bộ dữ liệu demo Capstone 2** (namespace `D26-`: 3 tài khoản, kịch bản trình diễn 9 bước, cách dựng lại, các chỗ script cố ý lệch tài liệu nguồn) | `docs/demo-capstone2-guide.md` | Đọc thủ công — cặp với `scripts/demo-capstone2/`. 🔴 Đây là bộ dữ liệu **cho khách hàng xem** (sạch, không bản ghi lỗi); bộ `VIETBIKE` ở hàng dưới là bộ **kiểm thử** (cố ý phủ cả trạng thái xấu) — **đừng trộn hai bộ trên cùng một môi trường**. Sửa dữ liệu thì sửa `scripts/demo-capstone2/catalogue.sh` |
| **Bộ dữ liệu demo** (chủ đề nhà máy xe đạp `VIETBIKE`: tài khoản, cây BOM, ma trận trạng thái, kịch bản thuyết trình, các trạng thái **không** seed được) | `docs/demo-dataset-guide.md` | Đọc thủ công — cặp với `scripts/demo/seed-demo.sh`. 🔴 Sửa dữ liệu demo thì sửa **`scripts/demo/lib/catalogue.sh`**, không sửa các file trong `stages/` |
| **Phản hồi gap Capstone 2** (đối chiếu `BACKEND_CAPSTONE2_API_GAPS.md` của FE với code thật) | `docs/capstone2-api-gap-response.md` | Đọc thủ công — **tài liệu đối ngoại**, thêm ở `C2-0` (2026-08-04). Chứa 3 mục FE báo thiếu mà **đã có**, 2 chỗ FE mô tả nhẹ hơn thực tế (audit diff rỗng, lot-status vs `B62`), và **5 câu hỏi đang chờ FE trả lời** — `C2-1`/`C2-2` bị chặn cho tới khi có câu 1 và 2 |

> 🔴 **`docs/api-guide-for-frontend.md` là tài liệu FE đang dùng để wire API.** Phase nào đổi
> endpoint / DTO / mã lỗi trên luồng sản xuất **phải** cập nhật file đó trong cùng commit — để nó lệch
> code chính là làm FE gắn sai. Nguồn của nó là **code thật**, không phải spec; spec gốc ở
> `docs/fe-spec-omniplant.md`, phần lệch giữa hai bên nằm ở §9 của guide.

> Nội dung không mất — chỉ đổi vị trí. Khi cần tra một bất biến `B<n>` hay một mục `4.x`/`9.x` cụ thể mà
> không chắc nằm ở file nào, grep theo mã bất biến hoặc theo tên class liên quan trong bảng trên.

# Kế hoạch: Khung Import Dữ Liệu Từ Excel (chuẩn bị trước khi có file mẫu)

## Trạng thái triển khai — 2026-08-15

**Slice 1 đã hoàn thành phần backend và nghiệm thu tự động.** Phạm vi đã có: migration V59/V60,
Apache POI, registry/descriptor, profile mapping + catalog transform, staging hai pha
upload → validate → apply, target `ITEM`, template `.xlsx`, batch lookup mã, phân quyền theo scope,
audit, idempotency, lỗi theo từng dòng và toàn bộ API trong mục 5.

- `mvn -o clean verify`: **BUILD SUCCESS**.
- Unit test: **1.056**, 0 failure/error (baseline: 1.025).
- Integration test: **127**, 0 failure/error (baseline: 124), chạy PostgreSQL 16 bằng Testcontainers.
- Flyway xác nhận schema sạch lên version **60**; ma trận quyền V60 và JSONB/paging đã được test.
- Mutation nghiệm thu: bỏ `DECIMAL_COMMA` làm đỏ đúng test transform; cho apply trước `VALIDATED`
  làm đỏ đúng test lifecycle. Cả hai mutation đã được hoàn nguyên và test gốc xanh lại.
- Fixture Excel bẩn được tạo trực tiếp bằng POI trong test (merge cell, hai dòng header, sheet phụ,
  Unicode NFD/NBSP, cột trống xen giữa, dòng trống, mã số định dạng giữ số 0 đầu) thay vì lưu một
  file nhị phân cố định trong `src/test/resources`.

Phần còn chờ dữ liệu ngoài repo không chặn Slice 1: tạo profile chính thức từ header của doanh
nghiệp và chạy E2E thủ công với file thật. Các target `SUPPLIER`, `UOM`,
`ITEM_WAREHOUSE_SETTING`, `BOM`, `OPENING_STOCK` vẫn thuộc Slice 2–5 như kế hoạch ban đầu.

## Context

Sắp phát triển tính năng import master data từ file Excel của doanh nghiệp. Hai ràng buộc đã biết:
tên cột trong file khách **sẽ không khớp** field hệ thống, và **chưa có file mẫu nào**.

Câu hỏi thật của user: *có nên xây gì trước không, hay phải đợi file mẫu?*

**Trả lời: ~85% tính năng xây được ngay.** Lý do nằm ở chính quyết định đã chốt — *mapping là dữ liệu,
không phải code*. Khi mapping là dữ liệu, một file lạ không còn là "thay đổi code" mà là "một dòng
profile mới". File mẫu vì thế chỉ quyết định **nội dung** của một dòng dữ liệu, không quyết định
**kiến trúc**.

Khảo sát xác nhận: repo **chưa có gì** liên quan (không POI/CSV trong `pom.xml`, không một chỗ nào
dùng `MultipartFile`, không cấu hình multipart, không staging table, không Spring Batch, executor
`@Async` duy nhất là `auditExecutor` chỉ phục vụ audit). Đây là surface hoàn toàn mới.

**Ba quyết định đã chốt với user:** mapping lưu thành profile (dữ liệu) · luồng 2 bước validate
(dry-run) → apply · phạm vi cuối cùng gồm Item+UOM, Nhà cung cấp, BOM, Tồn kho đầu kỳ (được **chia
slice**, không làm cùng lúc).

---

## 1. Xây ngay được vs. phải chờ

| Xây ngay | Vì sao file mẫu không thể làm nó sai |
|---|---|
| Schema `import_runs` / `import_rows` / `import_profiles` | Ô dữ liệu thô lưu JSONB theo đúng header khách gõ. Cột lạ được **lưu**, không bị từ chối |
| Tầng đọc file (POI) | Đọc được mọi `.xlsx` bất kể header là gì |
| Registry mô tả field đích (`ITEM` có `code/name/type/unit/lotTracked/serialTracked`) | Đây là field **của mình**, suy ra từ `ItemCreateRequest`. Khách không đổi được |
| Catalog transform (trim, uppercase, dấu phẩy thập phân, ngày dạng số Excel, giữ số 0 đầu mã, "Có"/"Không") | Đây là tập bệnh **của Excel tiếng Việt**, không phải của riêng một khách hàng |
| Vòng đời run 2 pha, staging, model lỗi theo dòng, endpoint đọc lỗi | Thuần cấu trúc |
| **Tầng resolve mã → UUID** | Thuần cấu trúc, và là lỗ hổng lớn nhất hiện nay (mục 2) |
| Apply orchestrator, idempotency theo dòng, permission, audit, migration | Thuần cấu trúc |
| **Tải template chuẩn** (`GET .../template`) | Đây là đòn bẩy **biến ẩn số thành đã biết** — xem mục 5 |
| Bộ fixture `.xlsx` "bẩn" tự dựng | Mình tự viết, và cố tình bẩn hơn file thật |

**Thật sự phải chờ — chỉ 3 thứ:**
1. **Nội dung profile** của khách. Đúng 1 dòng `INSERT`, không phải sửa code.
2. Một transform ngoài catalog (nếu có). Thêm 1 hằng vào enum + 1 hàm, không đụng kiến trúc.
3. Quy tắc nghiệp vụ riêng của khách mà một cột không diễn đạt được (ví dụ cột "Đơn vị" ghi
   `hộp (12 cái)` — vừa là UOM vừa là hệ số quy đổi). Loại này **phải** thấy file thật mới biết.

---

## 2. Điểm gãy lớn nhất — resolve mã → UUID (làm trước, độc lập file mẫu)

Mọi create request hiện nhận **UUID**: `companyId`, `plantId`, `itemId`, `componentItemId`,
`warehouseId`, `workCenterId`. Excel chỉ có mã (`VT-001`, `KHO-A`). Repo hôm nay chỉ có
`boolean existsBy...Code(...)` — **chống trùng, không phải tra cứu**. Không một `findBy...Code`
nào cho master data (tiền lệ duy nhất: `RoleRepository.findByCodeAndCompanyId`,
`InventoryLotRepository.findByItemItemIdAndLotCode`).

Việc cần làm (rule `C14` — 1 query gộp, **không** gọi repository theo từng dòng):

| Repository | Method batch cần thêm |
|---|---|
| `module/inventory/repository/ItemRepository` | `List<Item> findByCompanyCompanyIdAndCodeIn(UUID, Collection<String>)` |
| `module/purchasing/repository/SupplierRepository` | `findByCompanyCompanyIdAndCodeIn(...)` |
| `module/uom/repository/UomRepository` | `findByCodeIn(...)` *(UOM là global)* |
| `module/organization/repository/WarehouseRepository` | `findByPlantPlantIdAndCodeIn(...)` |
| `module/organization/repository/PlantRepository` | `findByCompanyCompanyIdAndCodeIn(...)` |
| `module/workcenter/repository/WorkCenterRepository` | `findByPlantPlantIdAndCodeIn(...)` |

Expose qua **lookup service sẵn có của chính module đó** (rule `C7` — module import không được
chạm repository module khác): thêm method batch vào `ItemLookupService`,
`OrganizationLookupService`, và tạo `SupplierLookupService`/`UomLookupService` nếu chưa có. Khuôn
mẫu có sẵn: `BomLookupService.findActiveBoms(UUID, Collection<UUID>)` và
`RoutingLookupService.findActiveRoutingSummaries(...)` — cả hai đã trả `Map` batch.

> Đây là phần **chắc chắn cần**, dù file khách trông ra sao. Nên làm ngay cả khi hoãn phần còn lại.

---

## 3. Schema — `V59` (schema) + `V60` (seed permission)

Tách 2 migration theo đúng tiền lệ `V54`/`V55`, `V50`/`V51`.

**`V59__create_data_import_tables.sql`** — 3 bảng:

```
import_profiles
  profile_id UUID PK · code VARCHAR(100) · name VARCHAR(255)
  target_type VARCHAR(40) NOT NULL          -- ITEM | SUPPLIER | UOM | ITEM_WAREHOUSE_SETTING | BOM | OPENING_STOCK
  company_id UUID NULL FK companies         -- NULL = profile dùng chung
  header_row_index INT NOT NULL DEFAULT 0
  first_data_row_index INT NOT NULL DEFAULT 1
  sheet_name VARCHAR(255) NULL              -- NULL = sheet đầu tiên
  mappings JSONB NOT NULL                   -- xem mục 4
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
  + audit columns của BaseEntity (created_at/updated_at/created_by/updated_by/version)
  UNIQUE (code)

import_runs                                  -- sao khuôn mrp_runs (V36 + V58)
  import_run_id UUID PK
  code VARCHAR(40) NOT NULL UNIQUE           -- 'IMP-' + 8 hex, gán ở @PrePersist
  target_type VARCHAR(40) NOT NULL
  profile_id UUID NULL FK import_profiles
  company_id UUID NOT NULL FK · plant_id UUID NULL FK · warehouse_id UUID NULL FK
  original_filename VARCHAR(255) · file_size_bytes BIGINT · file_sha256 VARCHAR(64)
  detected_headers JSONB                     -- header đọc được, kể cả cột không map
  status VARCHAR(20) NOT NULL DEFAULT 'PARSING'
  total_rows / valid_rows / error_rows / applied_rows / failed_rows  INT NOT NULL DEFAULT 0
  parsed_at · validated_at · applied_at  TIMESTAMPTZ
  error_message TEXT
  idempotency_key VARCHAR(120) NULL UNIQUE · payload_hash VARCHAR(64) NULL
  + audit columns
  CHECK (total_rows >= 0 AND valid_rows >= 0 AND ...)   -- theo chk_mrp_runs_totals

import_rows                                  -- staging, dữ liệu THÔ
  import_row_id UUID PK
  import_run_id UUID NOT NULL FK import_runs ON DELETE CASCADE
  row_number INT NOT NULL                    -- số dòng trong file, 1-based như user thấy
  raw_cells JSONB NOT NULL                   -- { "Mã vật tư": "VT-001", "ĐVT": "cái", ... }
  mapped_values JSONB NULL                   -- sau khi áp profile + transform
  status VARCHAR(20) NOT NULL                -- PENDING|VALID|ERROR|APPLIED|FAILED|SKIPPED
  errors JSONB NULL                          -- [{ column, targetField, code, message }]
  created_entity_id UUID NULL                -- id thực thể đã tạo, để truy vết
  UNIQUE (import_run_id, row_number)
  INDEX (import_run_id, status)
```

`ImportRunStatus`: `PARSING → PARSED → VALIDATING → VALIDATED → APPLYING → APPLIED |
PARTIALLY_APPLIED | FAILED | CANCELLED`.

> `raw_cells` là JSONB nên **cột lạ không phá gì** — đó chính là thứ làm cho "chưa có file mẫu" không
> còn là blocker.

🔴 `FlywayMigrationIT.migrate_onEmptyDatabase_appliesAllMigrationsCleanly` đang assert version `"58"`
— phải bump.

---

## 4. Mapping profile — hình dạng dữ liệu

`mappings` JSONB là một mảng:

```json
[
  { "sourceHeader": "Mã vật tư", "targetField": "code",
    "transforms": ["TRIM", "NFC", "UPPER"], "required": true },
  { "sourceHeader": "Tên hàng",  "targetField": "name",  "transforms": ["TRIM"], "required": true },
  { "sourceHeader": "ĐVT",       "targetField": "unit",  "transforms": ["TRIM", "UPPER"],
    "defaultValue": "CAI" },
  { "sourceHeader": "Theo dõi lô", "targetField": "lotTracked",
    "transforms": ["BOOLEAN_VN"], "defaultValue": "false" }
]
```

**Field đích hợp lệ đến từ đâu?** Một registry **trong code**, không phải DSL, không phải bảng:
`ImportTargetDescriptor` cho mỗi `targetType`, liệt kê `ImportFieldDescriptor(name, type, required,
maxLength, referenceType)`. Nguồn sự thật là chính request DTO đang có (`ItemCreateRequest`,
`SupplierCreateRequest`, …) — viết tay, đối chiếu bằng test để không trôi. `referenceType` là thứ
báo cho resolver biết cột này chứa **mã** cần đổi ra UUID (`ITEM_CODE`, `WAREHOUSE_CODE`).

**Catalog transform** — enum đóng, mỗi hằng là một `Function<String,String>` thuần (rất dễ unit test):

`TRIM` · `STRIP_NBSP` · `COLLAPSE_SPACES` · `NFC` (chuẩn hoá dấu tiếng Việt) · `UPPER` · `LOWER` ·
`PAD_LEFT_ZERO(n)` (giữ mã kiểu `007`) · `DECIMAL_COMMA` (`1.234,56` → `1234.56`) ·
`DECIMAL_DOT` · `EXCEL_DATE` (số serial → `LocalDate`) · `DATE_FORMAT(pattern)` ·
`BOOLEAN_VN` (`Có/Không/x/X/1/0/true/false`) · `VALUE_DICT` (từ điển giá trị, ví dụ
`"Nguyên vật liệu" → RAW_MATERIAL`).

Profile được **validate khi lưu** (không phải khi chạy): mọi `targetField` phải tồn tại trong
descriptor, mọi field `required` phải được map hoặc có `defaultValue`, transform phải hợp kiểu.

---

## 5. API surface

Module mới `module/dataimport/` (controller/service/domain/dto/repository), theo đúng cấu trúc các
module khác.

| Method | Endpoint | Mục đích |
|---|---|---|
| `GET` | `/api/v1/import-targets` | Liệt kê loại dữ liệu import được |
| `GET` | `/api/v1/import-targets/{type}/fields` | Field descriptor — FE dùng dựng UI kéo-thả map cột |
| `GET` | `/api/v1/import-targets/{type}/template` | **Tải file `.xlsx` template chuẩn** |
| `POST` `GET` `PATCH` | `/api/v1/import-profiles` | CRUD profile |
| `POST` | `/api/v1/import-runs` (multipart) | Upload + parse vào staging. Body: `file`, `targetType`, `profileId?`, `companyId`, `plantId?`. Không map, không ghi gì → `PARSED` + trả `detectedHeaders` |
| `POST` | `/api/v1/import-runs/{id}/validate` | Áp profile + transform + resolve mã → `VALIDATED`, điền counters, ghi lỗi vào `import_rows` |
| `GET` | `/api/v1/import-runs/{id}` | Summary + counters |
| `GET` | `/api/v1/import-runs/{id}/rows?status=ERROR` | Phân trang, chi tiết lỗi từng dòng |
| `POST` | `/api/v1/import-runs/{id}/apply` | Ghi thật. Nhận `Idempotency-Key` |
| `POST` | `/api/v1/import-runs/{id}/cancel` | Huỷ run chưa apply |

**Tách `upload` và `validate` làm 2 lời gọi** (thay vì gộp) để đổi profile rồi validate lại **không
phải upload lại file** — đúng vòng lặp thực tế của người dùng khi header chưa khớp.

**DTO lỗi theo dòng — thứ repo chưa có.** `ApiResponse.errors` hiện là `List<FieldErrorResponse(field,
message)>`, không mang được số dòng. Không throw; trả **200** với payload trong `result`:

```java
record ImportRowResponse(int rowNumber, ImportRowStatus status,
                         Map<String,String> rawCells, Map<String,Object> mappedValues,
                         List<ImportCellError> errors, UUID createdEntityId) {}
record ImportCellError(String column, String targetField, String code, String message) {}
```

`code` là mã máy đọc được (`REQUIRED_MISSING`, `REFERENCE_NOT_FOUND`, `DUPLICATE_IN_FILE`,
`ALREADY_EXISTS`, `INVALID_FORMAT`, `PATTERN_MISMATCH`) — FE lọc/nhóm theo nó, không parse message.

### 🔴 Template download nên làm SỚM

Đây là món rẻ nhất và có đòn bẩy cao nhất trong toàn kế hoạch: sinh một `.xlsx` từ chính
`ImportTargetDescriptor` (header + dòng ví dụ + sheet "Hướng dẫn" liệt kê giá trị hợp lệ của enum).
Gửi template cho doanh nghiệp **ngay bây giờ** thì bài toán "không biết file khách trông thế nào"
chuyển thành "đối chiếu file khách với template đã biết" — và rất có thể thu được file mẫu sớm hơn
là ngồi đợi.

---

## 6. Ngữ nghĩa apply

- **Giao dịch theo từng dòng** (`REQUIRES_NEW`) + tiếp tục khi lỗi, kết quả ghi vào `import_rows`
  (`APPLIED`/`FAILED`). File vài nghìn dòng mà all-or-nothing thì người dùng sẽ sửa-thử vô hạn lần.
  Run kết thúc `APPLIED` hoặc `PARTIALLY_APPLIED`.
  Ngoại lệ: **BOM** và **tồn kho đầu kỳ** apply theo **nhóm** (một BOM = header + n dòng + activate,
  phải nguyên khối) — đơn vị giao dịch là nhóm, không phải dòng.
- **Chỉ tạo mới ở slice 1** (`ALREADY_EXISTS` là lỗi dòng, không phải update ngầm). Upsert là quyết
  định nghiệp vụ riêng, chốt sau — ghi rõ để không ai "tiện tay" thêm.
- **Không bao giờ ghi thẳng repository** — luôn đi qua `ItemService.createItem` / `SupplierService.create`
  / `BomService.createBom`+`addLine`+`activateBom` / `InventoryMovementService`. Mã chứng từ sinh ở
  `@PrePersist`, `@Version`, audit, và mọi bất biến nghiệp vụ chỉ chạy ở tầng service.
- **Idempotency theo dòng** dùng `IdempotencySupport.childKey(runKey, rowNumber)` — có sẵn, đúng
  hình dạng cần.
- **Đồng bộ, giới hạn 5.000 dòng/file** ở slice 1. Không mượn `auditExecutor`; cần async thì thêm
  executor riêng ở phase sau.
- Thứ tự trong `apply` sao khuôn `MrpRunService.run`: resolve/validate hết trước → `saveAndFlush`
  để chiếm key ngay → `try{...} catch{ run.fail(...) }` (lỗi **được ghi lại**, không rethrow).

---

## 7. Permission + audit

`V60__seed_data_import_permissions.sql` theo khuôn `V51`:

| Permission | Role |
|---|---|
| `PERM_DATA_IMPORT_READ` | ADMIN + MANAGER |
| `PERM_DATA_IMPORT_EXECUTE` | ADMIN + MANAGER |

Gác bằng `@permissionGuard.hasResourceAccess(authentication, 'PERM_DATA_IMPORT_EXECUTE', 'COMPANY',
#companyId)` — **không** dùng `hasPermission` (chỉ khớp assignment scope `GLOBAL`, tài khoản
scope-plant sẽ bị 403 oan; đúng cái bẫy đã ghi ở `module/uom/CLAUDE.md`).

`AuditAction` thêm: `IMPORT_RUN_CREATED`, `IMPORT_RUN_VALIDATED`, `IMPORT_RUN_APPLIED`,
`IMPORT_RUN_FAILED`, `IMPORT_PROFILE_CREATED`, `IMPORT_PROFILE_UPDATED`.

---

## 8. Chia slice

| Slice | Nội dung | Cần dữ liệu khách? |
|---|---|---|
| **1** | Toàn bộ khung (schema, POI, profile, 2 pha, lỗi theo dòng, resolver batch, permission, audit) + **đúng một** target `ITEM` + template + fixture bẩn tự dựng | **Không** |
| **2** | `SUPPLIER` + `UOM` — rẻ, và là bài kiểm chứng khung có thật sự generic không | Không |
| **3** | `ITEM_WAREHOUSE_SETTING` (safetyStock/reorderPoint/leadTime) — target đầu tiên có **2 cột tham chiếu** (item + kho) | Không |
| **4** | `BOM` — cha-con, apply theo nhóm, chống vòng lặp, `activate` | Nên có file thật |
| **5** | `OPENING_STOCK` — khó nhất, phải đi qua ledger `stock_movements`, dính lot/serial | Nên có file thật |

Slice 1 **ship được và nghiệm thu được** mà không cần một byte dữ liệu nào của doanh nghiệp.

### Fixture "bẩn" tự dựng (thay cho file mẫu)

Tự viết bộ `.xlsx` trong `src/test/resources/import/`, cố ý mắc đủ bệnh Excel Việt Nam:
header có dấu · **2 dòng header** · ô merge · số lưu dạng text · dấu phẩy thập phân (`1.234,56`) ·
ngày dạng số serial · mã mất số 0 đầu (`007` → `7`) · khoảng trắng thừa và ký tự nbsp ·
`Có`/`Không`/`x` cho boolean · dòng trống giữa file · **dòng "Tổng cộng" ở cuối** · sheet phụ ·
mã trùng nhau **trong cùng file** · tham chiếu tới item chưa tồn tại · dấu tiếng Việt tổ hợp
(NFD) lẫn dựng sẵn (NFC).

---

## 9. Bẫy riêng của repo này

1. **`items.unit` là `String` tự do, `uoms` chưa có FK nào trỏ tới.** Import Item **không** resolve
   được đơn vị ra `uom_id`. Đừng "tiện tay" nối — đó là phase riêng.
2. **Tồn kho đầu kỳ phải đi qua `InventoryMovementService`**; `stock_balances` là projection của
   ledger append-only. INSERT thẳng là phá bất biến gốc của module inventory.
3. **BOM là 3 lời gọi** (`createBom` → `addLine` ×n → `activateBom`), không phải một.
4. **Không có entity `Customer`** — `SalesOrder.customerName` là text tự do. "Import khách hàng"
   hôm nay không có đích để ghi vào; phải tạo entity trước, là việc riêng.
5. **`hasPermission` chỉ khớp scope `GLOBAL`** — dùng `hasResourceAccess` (mục 7).
6. **`PermissionCatalogTest` làm đỏ build** nếu có `@PreAuthorize` mà thiếu `INSERT INTO permissions`
   trong migration.
7. **Bump `FlywayMigrationIT`** từ `"58"` lên version mới.
8. **`@WebMvcTest` cần đủ 6 `@MockBean`** hạ tầng bảo mật (`IpExtractor`, `JwtTokenProvider`,
   `TokenStoreService`, `UserDetailsService`, `RedisTemplate<String,String>`, `RateLimitProperties`);
   test upload dùng `MockMultipartFile`. Cần thêm `spring.servlet.multipart.*` (repo chưa có dòng nào).
9. **Mojibake UTF-8** — repo đã dính một lần (`CLAUDE.md §0.44`). Chuẩn hoá NFC ngay ở tầng đọc ô và
   có test round-trip tên tiếng Việt.
10. **Nhiều bag trong một `@EntityGraph`** — nếu `ImportRun` fetch cả `rows` và một collection khác
    sẽ dính `MultipleBagFetchException`; repo đã mắc **3 lần** (`§0.27`, `§0.29`, `§0.45`).
11. **Rule `C14`** — tuyệt đối không gọi repository theo từng dòng khi resolve mã; batch theo file.

---

## 10. Thư viện — vì sao Apache POI

### Trước hết: 3 thứ hay bị gộp làm một

| Tầng | Việc của nó | Ở đây dùng gì |
|---|---|---|
| **Đọc/ghi file `.xlsx`** | Giải nén OOXML, đọc ô, hiểu kiểu số/ngày/text | **Apache POI** ← chỗ đang bàn |
| **Điều phối job** (chunk, restart, retry) | Chạy khối lượng lớn, khôi phục khi lỗi giữa chừng | `import_runs`/`import_rows` tự làm (xem dưới) |
| **Truy cập DB** | Lưu/đọc bản ghi | **Spring Data JPA** — đã dùng sẵn, không thay thế được gì ở tầng trên |

**Spring Data không phải lựa chọn thay thế.** Nó là abstraction trên *cơ sở dữ liệu* (JPA/Redis) —
không có API nào đọc được file Excel. Trong kế hoạch này Spring Data **vẫn được dùng**, nhưng ở đúng
vai trò của nó: `ImportRunRepository`/`ImportRowRepository` để lưu staging. Nó nằm *sau* POI trong
luồng, không cạnh tranh với POI.

### Ứng viên thật sự

| Lựa chọn | Đánh giá |
|---|---|
| **Apache POI** ✅ | De-facto standard Java, Apache-2.0. **Đọc và ghi** — mục 5 cần ghi để sinh template `.xlsx`, đây là lý do loại phần lớn thư viện chỉ-đọc. Có `DataFormatter` (xem dưới). Có sẵn đường nâng cấp streaming (`XSSFReader`/SAX) khi file lớn mà không đổi API tầng trên |
| **Spring Batch (+ `spring-batch-excel`)** ❌ slice 1 | Không phải thư viện đọc Excel — `spring-batch-excel` **bọc chính Apache POI** bên trong, nên POI vẫn nằm dưới dù chọn đường nào. Đổi lại nó kéo theo JobRepository + ~9 bảng `BATCH_*` (migration riêng), khái niệm Job/Step/Chunk, và cơ chế restart **trùng lặp** với `import_rows` mà thiết kế này đã có: staging table đã là điểm khôi phục, đã có status/counters/lỗi theo dòng, và còn hiển thị được cho người dùng — thứ `BATCH_STEP_EXECUTION` không làm được. Repo hiện **không có Spring Batch, không có scheduler, không có job nào**; thêm nguyên một framework cho slice đầu là đúng thứ `coding-rules.md §11.5` cấm. Cân nhắc lại nếu sau này file vượt ~100k dòng hoặc cần chạy nền có retry tự động |
| **Alibaba EasyExcel** ❌ | SAX, tốn ít RAM — nhưng mô hình của nó là **bind vào POJO qua annotation** (`@ExcelProperty("Mã vật tư")`), tức tên cột nằm trong **code**. Điều đó **mâu thuẫn trực tiếp** với quyết định #1 của user (mapping là dữ liệu): mỗi khách hàng có header khác sẽ thành một class mới. Đây là lý do quyết định loại nó |
| **FastExcel / jxls** ❌ | Hẹp hơn (jxls thiên về sinh báo cáo từ template), cộng đồng nhỏ, không đủ bù rủi ro |

### Vì sao `DataFormatter` là chi tiết quan trọng, không phải tiểu tiết

Đọc ô bằng `cell.getNumericCellValue()` sẽ cho `45123.0` với một ô ngày và `7.0` với mã `007`.
`DataFormatter` trả **đúng chuỗi người dùng nhìn thấy trong Excel** — nhờ đó `raw_cells` lưu được
"sự thật như người nhập liệu thấy", và mọi chuyển đổi kiểu dồn về **một chỗ duy nhất** là catalog
transform ở mục 4 (nơi có unit test). Đây chính là thứ làm cho "chưa biết file trông thế nào" vẫn
code được.

### Ranh giới để đổi ý sau này không tốn kém

Tầng đọc file được tách sau một interface hẹp:

```java
interface SpreadsheetReader {
    List<String> readHeaders(InputStream in, String sheetName, int headerRowIndex);
    Stream<RawRow> readRows(InputStream in, String sheetName, int firstDataRowIndex);
}
record RawRow(int rowNumber, Map<String, String> cells) {}
```

`PoiSpreadsheetReader` là implementation duy nhất ở slice 1. Đổi sang streaming POI, hay thêm CSV
sau này, chỉ là thêm implementation — không đụng profile/validate/apply.

> ⚠️ Đây là ngoại lệ **có ý thức** với `coding-rules.md §11.5` (cấm interface cho 1 implementation):
> lý do là ranh giới bộ nhớ/định dạng file đã được nhận diện trước, không phải "linh hoạt cho vui".
> Nếu thấy không thuyết phục thì bỏ interface, dùng class thẳng — phần còn lại của kế hoạch không đổi.

CSV: **chưa làm ở slice 1** (không ai yêu cầu, và CSV làm sống lại vấn đề encoding + delimiter tiếng
Việt).

---

## 11. Nghiệm thu

- **Unit**: từng transform trong catalog (thuần, dễ pin bằng con số thật — rule `R6`); mapper
  profile→giá trị; validate profile khi lưu.
- **`*IT`** (`AbstractPostgresIntegrationTest`): `ImportRunRepositoryIT` (query JSONB + phân trang
  rows theo status), `FlywayMigrationIT` bump version + test ma trận grant `V60` (khuôn
  `migrate_v51_*` / `migrate_v55_*`).
- **`@WebMvcTest`** + `MockMultipartFile`: envelope, 400 khi thiếu file/sai content-type, 200 + payload
  lỗi theo dòng.
- **`*MethodSecurityTest`**: deny + allow + `verify(guard)` khoá đúng chuỗi `PERM_*` (rule `R2`).
- **E2E thủ công**: nạp từng fixture bẩn ở mục 8, đối chiếu `import_rows.errors` với kỳ vọng, apply,
  rồi `GET /companies/{id}/items` xác nhận dữ liệu vào đúng.
- **Nghiệm thu mutation** (quy ước repo): tối thiểu 2 mutation đụng `src/main` — ví dụ bỏ transform
  `DECIMAL_COMMA`, và cho `apply` chạy khi run chưa `VALIDATED` — mỗi cái phải làm đỏ đúng case dự kiến.
- `mvn -o clean verify` xanh, số case ≥ baseline (1025 unit / 124 IT).

---

## Đề xuất hành động ngay

1. Làm **mục 2** (batch `findBy...CodeIn` + lookup service) — cần bất kể file mẫu ra sao.
2. Làm **template download** và gửi cho doanh nghiệp — vừa là tính năng, vừa là cách lấy được file
   mẫu sớm nhất.
3. Dựng **fixture bẩn** trước khi viết parser, để parser được viết theo dữ liệu xấu chứ không phải
   theo dữ liệu đẹp tưởng tượng.

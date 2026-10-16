# module/dataimport — Nhập dữ liệu nền từ Excel

> Tạo ở `G3` (2026-10-14). Trước đó module **chưa có** file này dù đã tồn tại từ `V59`.
> Kế hoạch + 8 quyết định D1–D8: `NEXT_PHASE_PLAN.md §10`. Bản ghi phase: `CLAUDE.md §0.51`.

## 1. Hai đường nhập, chung bảng, KHÔNG thấy nhau

| | Mapped profile (cũ) | Fixed template (`G3`) |
|---|---|---|
| Path | `/v1/import-runs*` | `/v1/data-import/batches*` |
| `import_runs.kind` | `MAPPED_PROFILE` | `FIXED_TEMPLATE` |
| Nguồn cột | profile ánh xạ header tự do | mẫu `omniplant-import-v1` cố định (metadata `_OmniPlant`) |
| Ghi master | **từng dòng**, `REQUIRES_NEW`, có `PARTIALLY_APPLIED` | **cả lô một transaction** hoặc không gì cả |
| Quyền | `PERM_DATA_IMPORT_READ/EXECUTE` | READ + MANAGE **của chính loại** (`PERM_ITEM_*` / `SUPPLIER_*` / `CUSTOMER_*`) |
| Lịch sử | theo công ty | theo **owner** (`owner_user_id`) |
| Giới hạn | 5000 dòng | **500 dòng / 5 MiB** (`fixed-template-max-*`) |
| Status | `PARSED…APPLIED` | `READY` / `INVALID` / `COMMITTED` |

🔴 Mọi query của **mỗi** đường lọc theo `kind` (`ImportRunRepository`). Bỏ lọc đó ở đường cũ thì
`/v1/import-runs/{id}/cancel` hủy được một batch `READY`, và list của đường cũ lộ batch của người khác.

## 2. Bất biến

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B142 | Upload (`POST /v1/data-import/batches`) đọc + validate cả file và lưu batch `READY`/`INVALID` kèm mọi dòng/lỗi — **không tạo một master nào**. Lỗi theo dòng **không** phải lỗi HTTP: file có dòng lỗi vẫn là **201** với batch `INVALID`. Chỉ file không phải mẫu mới là lỗi file-level (`FILE_FORMAT`/`FILE_SIZE`/`TEMPLATE`/`EMPTY_FILE`/`ROW_LIMIT`) và không sinh batch | `DataImportBatchServiceTest.upload_validFile_*` (`never().apply`), `DataImportBatchIT.upload_validFile_*` (đếm DB = 0) |
| B143 | Commit **all-or-nothing**: master tạo qua chính service của module sở hữu (join transaction của `DataImportBatchCommitter.commit`); lỗi ở **bất kỳ** dòng nào ⇒ rollback mọi dòng. 🔴 `entityManager.flush()` **bên trong** `try` là load-bearing: thiếu nó, va chạm unique ở **dòng cuối** nổ lúc commit transaction, ngoài `try`, và không còn phân biệt được với lỗi DB khác | `DataImportBatchIT.commit_failureOnTheLastRow_*`, `.commit_twoBatchesSharingACode_concurrently_*` (mã chung đặt **cuối** có chủ đích — nghiệm thu mutation) |
| B144 | Commit thất bại ⇒ transaction **thứ hai** (`markInvalidIfStale`) validate lại theo DB hiện tại: có lỗi dữ liệu ⇒ batch `INVALID` + lưu lỗi mới + trả **409 `IMPORT_REVALIDATION_FAILED` kèm `result` = batch**; dữ liệu vẫn hợp lệ (lỗi DB thuần) ⇒ batch giữ `READY`, ném lại lỗi gốc. Hai transaction **phải** ở bean riêng (`DataImportBatchCommitter`) — gọi qua `this` là bypass proxy | `DataImportBatchServiceTest.commit_*`, `DataImportBatchCommitterTest.markInvalidIfStale_*`, `DataImportBatchIT.commit_codeTakenAfterValidation_*` |
| B145 | Thứ tự commit theo FE §3.6: **quyền** (guard, mỗi lần gọi) → **key** (`Idempotency-Key` của batch khác, hoặc cùng batch khác `expectedVersion` ⇒ 409 `IDEMPOTENCY_CONFLICT`) → batch đã `COMMITTED` ⇒ trả **receipt cũ trước** kiểm version, không ghi audit lần hai → `READY`? (khác ⇒ `STATE_CONFLICT`) → `expectedVersion` (lệch ⇒ `CONCURRENT_MODIFICATION`). Hai commit đồng thời cùng batch serialize qua `lockFixedTemplateBatch` (`PESSIMISTIC_WRITE`) | `DataImportBatchCommitterTest.commit_alreadyCommitted_*`, `.commit_keyAlreadyUsedForAnotherBatch_*`, `DataImportBatchIT.commit_sameBatchTwiceConcurrently_*` |
| B146 | Batch của **người khác** (hoặc không tồn tại) ⇒ **404**, không bao giờ 403 — guard `canAccessBatch` cố ý trả `true` khi không phải owner để service trả 404. Owner mất cặp quyền ⇒ **403**, kể cả khi chỉ đọc lại receipt | `DataImportBatchMethodSecurityTest.get_anotherOwnersBatch_*`, `.commit_ownBatchAfterThePairWasRevoked_*` |
| B147 | Ô Excel strict (D8): công thức ⇒ `FORMULA` (không evaluate); ô **số** ở cột mã/điện thoại ⇒ `TEXT_REQUIRED` (số 0 đầu đã mất, không đoán); ngày/boolean/lỗi ⇒ `CELL_TYPE`. Đó là lỗi **dòng**, không phải lỗi file. Archive kiểm **trước** POI: ≤ 64 part, ≤ 25 MiB giải nén **thật**, từ chối `vbaProject`/`externalLinks`/`activeX` | `FixedTemplateReaderTest` (11) |
| B148 | Trùng mã trong file ⇒ **mọi** lần xuất hiện bị gắn `DUPLICATE_IN_FILE` (FE A09), không chỉ lần thứ hai. Luật DB (mã đã có — mọi status; UOM thiếu/ngừng) lấy từ **chính** `ImportTargetHandler` mà đường cũ dùng: một định nghĩa cho cả hai đường | `FixedTemplateValidatorTest` |

## 3. Bẫy đã gặp

1. **`left join r.plant`** trong `searchBatches` là load-bearing: batch nhà cung cấp/khách hàng không có
   plant, đường dẫn `r.plant.plantId` sinh inner join và xoá chúng khỏi trang (mutation đã chứng minh).
2. **`cast(:search as string)`** — bẫy `lower(bytea)` (`CLAUDE.md §0.24`).
3. **Revalidation trước khi ghi là phòng thủ chồng lớp**: bỏ nó thì IT vẫn xanh vì `CustomerService`/
   `ItemService` tự chặn mã trùng/UOM ngừng; chỉ unit test bắt được. Giữ nó để lỗi trả về **đúng
   ô**, không phải một exception chung từ service.
4. `version` trên wire là `@Version` của JPA: **0** ngay sau upload (không phải 1 như ví dụ FE).
5. Supplier `phone` ≤ **60** (cột `V20`), customer ≤ 255 — template FE ghi 255 cho cả hai.

## 4. G5 — Nhập BOM (`kind=bom`, `omniplant-bom-v1`) (2026-10-16)

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B168 | BOM **không** đăng ký `ImportTargetHandler` (đường mapped-profile không gom nhóm được ⇒ trả "import target not found"). `BomImportHandler` riêng: validator gọi nó **thay** `flagRepeatedCodes` + luật handler; committer gọi `apply` theo nhóm, mỗi nhóm một `BomService.saveDraft` **trong cùng transaction** của batch. `createdIds` **distinct** (một id mỗi BOM); `bomCount` = số mã BOM | `BomImportHandlerTest.apply_*`, `DataImportBatchIT.bomBatch_*` |
| B169 | Luật nhóm: B/C/D/I lặp y hệt dòng đầu (`GROUP_MISMATCH`); trùng thành phần, một sản phẩm hai mã ⇒ `DUPLICATE_IN_FILE`; mã BOM đã có, sản phẩm đã có BOM **bất kỳ trạng thái** ⇒ `ALREADY_EXISTS` (create-only); đơn vị phải là đơn vị của vật tư; thương C÷F và % ÷ 100 phải vừa 6 số lẻ. Một dòng lỗi ⇒ mọi dòng còn lại của nhóm nhận `RULE_VIOLATION` | `BomImportHandlerTest.validate_*` |
| B170 | Đọc mẫu: sheet `BOM`, metadata `_meta` dạng key/value (A1 `version`, A2 `locale`). Ô **số** ở cột lượng/% được đọc theo **giá trị** (`BigDecimal.valueOf(double)`), không theo định dạng hiển thị; % trống = 0 | `BomImportHandlerTest.reader_*` |

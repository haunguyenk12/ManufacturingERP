# Lịch sử mã lỗi theo phase

> Chuyển **nguyên văn** từ `.claude/rules/error-handling.md §5.3` ngày 2026-11-06 để file luật (nạp mọi phiên) gọn lại.
> Dùng khi cần biết *vì sao* một mã tồn tại hoặc FE từng đề xuất mã nào và nó ánh xạ vào đâu.
> Nguồn sự thật cho *hiện có mã nào*: ba enum `AuthErrorCode`, `BusinessErrorCode`, `ValidationErrorCode` (`common/exception/`).
> Phase mới thêm mã lỗi ⇒ ghi vào **file này**, không ghi lại vào `error-handling.md`.

**Constant thêm ở `F1`** (theo `OmniPlant_MVP_Production_Backend_Handoff.docx` §8.2):
`STATE_CONFLICT` (409), `IDEMPOTENCY_CONFLICT` (409), `CONCURRENT_MODIFICATION` (409),
`RESERVATION_EXCEEDED` (409), `PLANNED_QUANTITY_EXCEEDED` (409), `LOT_NOT_ELIGIBLE` (409),
`LOT_REQUIRED` (400), `APPROVAL_REASON_REQUIRED` (400).

**Constant thêm ở `D8a`:** `TOKEN_REUSE_DETECTED` (401) — refresh token đã bị rotate away mà quay
lại. Cùng **401** với `REFRESH_TOKEN_EXPIRED` nên `code` là thứ **duy nhất** phân biệt "đăng nhập
lại đi" với "phiên của bạn vừa bị thu hồi vì phát hiện replay". Bất biến `B80`.

**Constant thêm ở `D8b`:** `SESSION_ABSOLUTE_TIMEOUT` (401) — phiên sống quá 30 ngày kể từ **login**,
bị thu hồi dù vẫn đang hoạt động. Đây là mã **thứ ba** cùng **401** trên `POST /auth/refresh`:

| `code` | Nghĩa | FE nên nói gì với user |
|---|---|---|
| `REFRESH_TOKEN_EXPIRED` | Token hỏng/hết hạn tự nhiên | "Đăng nhập lại" |
| `TOKEN_REUSE_DETECTED` | Replay ⇒ **mọi phiên đã bị thu hồi** | Cảnh báo bảo mật rõ ràng |
| `SESSION_ABSOLUTE_TIMEOUT` | Hết hạn theo chính sách 30 ngày | "Phiên đã hết hạn, đăng nhập lại" — **không** phải cảnh báo bảo mật |

⇒ HTTP status **không** phân biệt được ba trường hợp này. Client **phải** rẽ theo `code` (`A7`).
Bất biến `B81`.

**Constant thêm ở `D8c`:** `AuthErrorCode.RESET_TOKEN_INVALID` (401) trên `POST /auth/reset-password`
— **chỉ một** mã cho cả "token chưa từng tồn tại" lẫn "token đã hết hạn/đã dùng" (Redis TTL không
phân biệt được hai trường hợp, đúng cách `REFRESH_TOKEN_EXPIRED` đã xử lý cho refresh token). Danh
sách thiết kế gốc (bỏ ở `EH-6`) từng liệt kê cả `RESET_TOKEN_INVALID` **và** `RESET_TOKEN_EXPIRED` — chỉ vế đầu
được implement; `RESET_TOKEN_EXPIRED` **cố ý không thêm** vì không có nhánh nào thật sự ném nó
(`coding-rules.md §11.5`). Bất biến `B101`.

**Constant thêm ở P0 (2026-08-06, bugfix):** `AuthErrorCode.AUTHENTICATION_REQUIRED` (401) — trả bởi
`JwtAuthEntryPoint` khi request tới một endpoint cần auth mà **không** có `Authorization` header nào
(hoặc không đúng prefix `Bearer`) — nói cách khác, "chưa gửi credential gì cả". Trước đây chỗ này
hardcode `TOKEN_MALFORMED`, sai vì mã đó nên dành cho "đã gửi token nhưng token hỏng" (nhánh đó được
xử lý riêng, sớm hơn, ngay trong `JwtAuthenticationFilter`, không bao giờ chạm tới
`JwtAuthEntryPoint`). Hai mã giờ tách biệt đúng nghĩa:

| `code` | Nghĩa | Ai ném |
|---|---|---|
| `AUTHENTICATION_REQUIRED` | Không gửi credential gì | `JwtAuthEntryPoint` |
| `TOKEN_MALFORMED` | Có gửi, nhưng token hỏng/rác/sai chữ ký | `JwtAuthenticationFilter` (qua `JwtTokenProvider`) |

Chi tiết + bối cảnh phát hiện (hai lỗi P0 FE báo): `common/security/CLAUDE.md §4.20`.

**Constant thêm ở `G2` (2026-10-14):**

| `code` | HTTP | Ném ở đâu | Vì sao không tái dùng mã có sẵn |
|---|---|---|---|
| `DEFAULT_CONFLICT` | 409 | `PUT /inventory/item-warehouse-settings` với `replaceDefaults=false` khi kho khác của cùng item/plant đang giữ vai trò default được yêu cầu. **Không ghi gì** | FE cần phân biệt "hỏi người dùng rồi gửi lại với `replaceDefaults=true`" với một xung đột trạng thái chung — `STATE_CONFLICT` không mang được ý đó |
| `ACTIVE_CHILDREN_EXIST` | **422** | `DELETE /plants/{id}` khi plant còn Warehouse `ACTIVE` (từ chối, không cascade) | Là luật master data, không phải status chứng từ ⇒ 422 cùng nhóm `RESOURCE_INACTIVE`; khác hướng (cha → con) nên không gộp vào mã đó |

**Constant thêm ở `G3` (2026-10-14)** — nhập Excel mẫu cố định (`/v1/data-import/batches`):

| `code` | HTTP | Khi nào |
|---|---|---|
| `FILE_FORMAT` | 400 | không phải `.xlsx` hợp lệ; có macro / ActiveX / external link |
| `FILE_SIZE` | **413** | > 5 MiB (đường mẫu cố định), > giới hạn multipart chung, hoặc giải nén > 25 MiB |
| `TEMPLATE` | 400 | sai mẫu `omniplant-import-v1` (metadata, phiên bản, loại, ngôn ngữ, header, thừa cột) |
| `EMPTY_FILE` / `ROW_LIMIT` | 400 | không có dòng dữ liệu / quá 500 dòng |
| `IMPORT_REVALIDATION_FAILED` | 409 | commit batch mà dữ liệu đã đổi — **kèm `result`** (xem §5.1) |

Lỗi **theo dòng** của file **không** phải lỗi HTTP: chúng nằm trong batch `INVALID` của một response **201**
(`ImportErrorCode`, enum riêng). Batch không `READY` khi commit ⇒ `STATE_CONFLICT`; `expectedVersion` lệch ⇒
`CONCURRENT_MODIFICATION` — dùng mã sẵn có theo §5.3, **không** tạo `IMPORT_BATCH_NOT_READY` /
`IMPORT_BATCH_VERSION_CONFLICT` như FE đề xuất.

**Constant thêm ở `G4` (2026-10-15)** — tồn kho / lô / chuyển kho:

| `code` | HTTP | Khi nào | Vì sao không tái dùng `STATE_CONFLICT` |
|---|---|---|---|
| `NO_CHANGE` | 409 | kiểm đếm bằng đúng số hiện tại; đổi lô sang trạng thái nó đang có. **Không ghi gì** | FE nói "không có gì để ghi", không phải "tải lại rồi thử lại" |
| `RESERVED_STOCK` | 409 | đổi trạng thái lô còn reservation `ACTIVE`; đếm xuống dưới lượng đã giữ cho WO | cách xử lý là giải phóng reservation ở nguồn, khác hẳn thiếu tồn (`INSUFFICIENT_AVAILABLE_STOCK`) |

Còn lại của bảng mã FE đề xuất ánh xạ vào mã **sẵn có**: `STALE_VERSION` → `CONCURRENT_MODIFICATION`;
`SAME_WAREHOUSE` → `VALIDATION_ERROR` + `errors[{field:"toWarehouseId"}]`; `PLANT_SCOPE_MISMATCH` /
`LOT_ITEM_MISMATCH` → `RESOURCE_SCOPE_MISMATCH` (422); `WAREHOUSE/ITEM/PLANT_INACTIVE` → `RESOURCE_INACTIVE`
(422); `LOT_EXPIRED` → `LOT_NOT_ELIGIBLE`; `DECIMAL_PRECISION_EXCEEDED` → `VALIDATION_ERROR` + `errors[{field}]`.

Timezone sai ở Plant **không** có mã riêng: dùng `VALIDATION_ERROR` + `errors[{field:"timezone"}]` (tương đương `INVALID_TIMEZONE` FE đề xuất, xem bất biến `B140`).

**Constant thêm ở `G5` (2026-10-16)** — BOM / quy trình / công suất:

| `code` | HTTP | Khi nào | Vì sao không tái dùng mã có sẵn |
|---|---|---|---|
| `EFFECTIVE_DATE_FUTURE` | 409 | activate BOM/routing có `effectiveFrom` > giờ server | cách xử lý (chờ / đổi ngày) khác `STATE_CONFLICT`; không bao giờ tự activate khi tới ngày |
| `OPEN_DRAFT_EXISTS` | 409 | lưu nháp mới / sao chép routing khi sản phẩm đã có `DRAFT` | FE mở nháp có sẵn thay vì tải lại |
| `REFERENCE_IN_USE` | **422** | ngừng ca (trên lịch `ACTIVE`), lịch (trung tâm `ACTIVE`), trung tâm (routing `ACTIVE` / WO chưa xong) | luật master data như `ACTIVE_CHILDREN_EXIST` (422), FE đề xuất 409; khác hướng (được tham chiếu, không phải có con) |
| `OPERATION_SEQUENCE_CONFLICT` | 409 | điều chỉnh lịch chồng công đoạn trước/sau (D10, thay `B90`) | FE giữ form và báo xung đột chuỗi, không tải lại |
| `SCHEDULING_UNAVAILABLE` | 409 | xem trước: trung tâm không có lịch `ACTIVE` / không có giờ làm trong 180 ngày sau kỳ | phân biệt "không xếp được" với dữ liệu sai |

Còn lại của bảng mã FE ánh xạ vào mã **sẵn có**: `CIRCULAR_BOM` → `BOM_CIRCULAR_REFERENCE` (422); `INVALID_STATE` →
`STATE_CONFLICT`; `DUPLICATE_CODE`/`BOM_OUTPUT_EXISTS` → `RESOURCE_ALREADY_EXISTS` (API) hoặc `ALREADY_EXISTS` (lỗi dòng nhập);
`VERSION_CONFLICT` → `CONCURRENT_MODIFICATION`; `DUPLICATE_COMPONENT` → `VALIDATION_ERROR` + `errors[{field}]`;
`REFERENCE_UNAVAILABLE` → `RESOURCE_INACTIVE`/`ENTITY_NOT_FOUND`; `IMPORT_*` → mã của `G3`.

**`G6a` (2026-10-17) — không thêm mã mới.** Đơn bán/giữ tồn dùng mã sẵn có: vượt phần chưa phủ ⇒ `PLANNED_QUANTITY_EXCEEDED`
(FE đề xuất `DEMAND_QUANTITY_EXCEEDED`); có lệnh SX ⇒ `STATE_CONFLICT` (FE đề xuất `DOWNSTREAM_DOCUMENT_EXISTS`); khách/vật tư ngừng
⇒ `RESOURCE_INACTIVE`; `RESERVED_STOCK` nay gồm cả tồn đơn bán đang giữ.

**Constant thêm ở `G6b` (2026-10-18):** `PLANNING_SOURCE_STALE` (409) — chuyển đổi một run thương mại mà `expectedSourceVersion` không
phải của run, hoặc nguồn (dòng đơn / tồn đơn giữ / BOM, routing `ACTIVE`) đã đổi từ lúc chạy; kèm `errors[{field:"expectedSourceVersion"}]`.
Tách khỏi `STATE_CONFLICT` vì cách xử lý là **chạy run mới**, không phải tải lại rồi thử lại. Còn lại dùng mã sẵn có: vượt phần còn lại ⇒
`PLANNED_QUANTITY_EXCEEDED`; version chuyển đổi lệch ⇒ `CONCURRENT_MODIFICATION`; đề xuất bị chặn ⇒ đúng mã đã chặn (`MISSING_BOM`…, B58).

**Constant thêm ở `G7a` (2026-10-18):** `SOURCE_LOCKED` (409) — `PATCH` một PO nháp còn giữ quota hoạch định mà đổi kho / vật tư / lượng /
tập dòng; kèm `errors[{field}]`. Tách khỏi `STATE_CONFLICT` vì tải lại rồi gửi lại vẫn hỏng như cũ — cách xử lý là huỷ PO (trả quota) hoặc chỉ
sửa field cho phép. Còn lại dùng mã sẵn có: thiếu giá khi xác nhận ⇒ `VALIDATION_ERROR` field `lines[i].unitPrice`; vật tư sai loại ⇒
`OPERATION_NOT_ALLOWED` field `lines[i].itemId`; huỷ PO đã nhận / đóng PO hết lượng mở / đảo phiếu nhận của PO `CLOSED` ⇒ `STATE_CONFLICT`.

**`G10d` (2026-10-27) — không thêm mã mới.** Thanh toán dùng mã sẵn có: header khác tổng phân bổ, hoá đơn lạ/khách khác/nhà máy khác/trùng,
ngày sai ⇒ `VALIDATION_ERROR` field (`amount`, `allocations[i].invoiceId`, `documentDate`); vượt `amount − P − Cp` ⇒ `PLANNED_QUANTITY_EXCEEDED` field
`allocations[i].amount` (FE đề xuất `PAYMENT_EXCEEDED`); hoá đơn chưa `POSTED` hoặc thanh toán không `DRAFT` ⇒ `STATE_CONFLICT`; version hoá đơn lệch ⇒
`CONCURRENT_MODIFICATION` field `allocations[i].expectedInvoiceVersion`; khách ngừng ⇒ `RESOURCE_INACTIVE`; không phải người tạo sửa/huỷ ⇒ `403`.

**Constant thêm ở `G10c` (2026-10-26):** `PRICE_UNKNOWN` (409) — dòng đơn chưa có giá nên phiếu giao của nó không lập hoá đơn được (không bao giờ tính 0); field `lines[i].deliveryLineId`. `PRICE_STALE` (409) — nền tính tiền (hoá đơn `POSTED` của các dòng đơn chạm tới, hoặc phiên bản giá) đã đổi: lưu với `billingSnapshotVersion` cũ (field đó), hoặc ghi sổ khi nền đổi từ lúc lưu / số tiền đã lưu không khớp. Tách khỏi `CONCURRENT_MODIFICATION`: chính số tiền sẽ đổi, nên người dùng phải xem trước và xác nhận lại. Còn lại dùng mã sẵn có: vượt phần còn lập ⇒ `PLANNED_QUANTITY_EXCEEDED` field `lines[i].quantity` (FE đề xuất `INVOICE_EXCEEDED`); nguồn chưa `POSTED` ⇒ `STATE_CONFLICT`; nguồn lạ/đơn khác, ngày sai ⇒ `VALIDATION_ERROR` field.

**Constant thêm ở `G10b` (2026-10-25):** `SOURCE_UNKNOWN` (409) — đơn bán không có baseline giao hàng (xác nhận trước `V86`,
D3) hoặc không có khách master (D4): không giao / lập hoá đơn được. Tách khỏi `STATE_CONFLICT` vì tải lại không giúp gì — chỉ đối soát ngoài
hệ thống mới lấp được. Còn lại dùng mã sẵn có: vượt hạn mức giao (`ordered − D − Cd`) ⇒ `PLANNED_QUANTITY_EXCEEDED` field
`lines[i].quantity` (FE đề xuất `DELIVERY_EXCEEDED`); không đủ tồn tự do + phần giữ của chính đơn ⇒ `INSUFFICIENT_AVAILABLE_STOCK` field
`lines[i].quantity`; lô `HOLD`/`REJECTED`/hết hạn ⇒ `LOT_NOT_ELIGIBLE` field `lines[i].sourceKey`; `sourceKey` không do server phát ⇒
`VALIDATION_ERROR` field; phiếu không `DRAFT` hoặc huỷ đơn còn phiếu giao ⇒ `STATE_CONFLICT`; không phải người tạo sửa/huỷ ⇒ `403`.

**Constant thêm ở `G10a` (2026-10-25):** `PRICE_LOCKED` (409) — bổ sung giá cho một dòng đơn đã xác nhận mà giá đã biết (kể cả
0); kèm `errors[{field:"prices[i].unitPrice"}]`. Tách khỏi `STATE_CONFLICT` vì tải lại không giúp gì — giá đã chốt không sửa được. Còn
lại dùng mã sẵn có: xác nhận đơn không có khách master ⇒ `OPERATION_NOT_ALLOWED` (422) field `customerId`; giá âm/quá trần/quá 6 số lẻ
⇒ `VALIDATION_ERROR` field; thiếu `Idempotency-Key` ⇒ `MISSING_REQUIRED_FIELD`.

**Constant thêm ở `G7b` (2026-10-18):** `PENDING_GOODS_RECEIPT` (409) — huỷ hoặc đóng một PO còn phiếu nhận `DRAFT`. Tách khỏi
`STATE_CONFLICT` vì cách xử lý là post hoặc huỷ phiếu nhận nháp trước, không phải tải lại PO. Còn lại dùng mã sẵn có: nhận vượt lượng mở (kể cả
kẻ thua của hai lần post đồng thời) ⇒ `PLANNED_QUANTITY_EXCEEDED` field `lines[i].receivedQuantity`; mã lô đã có (không phân biệt hoa thường) ⇒
`LOT_CODE_ALREADY_EXISTS`; đường cũ nhận vào lô không `HOLD` ⇒ `LOT_NOT_ELIGIBLE`; post phiếu không `DRAFT` ⇒ `STATE_CONFLICT`.

**`G7c` (2026-10-18) — không thêm mã mới.** QC mua dùng mã sẵn có: cả hai lượng bằng 0 / loại thiếu lý do ⇒ `VALIDATION_ERROR` field
`acceptedQuantity` / `reason`; vượt phần chờ ⇒ `PLANNED_QUANTITY_EXCEEDED` field `acceptedQuantity`; chấp nhận lô hết hạn ⇒ `LOT_NOT_ELIGIBLE`;
version phiếu lệch ⇒ `CONCURRENT_MODIFICATION`; phiếu không `POSTED`/không nhận vào vùng chờ, hoặc đảo nhập phiếu đã có quyết định ⇒
`STATE_CONFLICT`; đổi trạng thái lô đang chờ QC mua / lô con bị QC loại ⇒ `LOT_NOT_ELIGIBLE`. Mã FE đề xuất ánh xạ: `QC_QUANTITY_EXCEEDED` → `PLANNED_QUANTITY_EXCEEDED`; `QC_STATE_INVALID` → `STATE_CONFLICT`;
`PURCHASE_QC_REQUIRED` / `LOT_EXPIRED` → `LOT_NOT_ELIGIBLE`; `REASON_REQUIRED` → `VALIDATION_ERROR` field `reason`.

**`G6c` (2026-10-18) — không thêm mã mới.** Nhập đơn bán Excel (`kind=so`) dùng nguyên mã file-level của `G3` (`FILE_FORMAT`,
`FILE_SIZE` 413, `TEMPLATE`, `EMPTY_FILE`, `ROW_LIMIT`, `IMPORT_REVALIDATION_FAILED`) và lỗi dòng `ImportErrorCode` sẵn có. Mã FE đề xuất ánh
xạ: `INVALID_FILE` → `FILE_FORMAT`; `FILE_TOO_LARGE` → `FILE_SIZE`; `INVALID_TEMPLATE` → `TEMPLATE`; `IMPORT_ROW_LIMIT_EXCEEDED` → `ROW_LIMIT`;
`IMPORT_BATCH_INVALID` → `STATE_CONFLICT`; `IMPORT_SOURCE_STALE` → `IMPORT_REVALIDATION_FAILED`; `IMPORT_GROUP_INVALID` (lỗi dòng) →
`RULE_VIOLATION` ở field `groupCode`. `IMPORT_BATCH_EXPIRED` không có — batch không hết hạn.

**Constant thêm ở `F5`:** `MISSING_BOM` (409) — anh em của `MISSING_ROUTING`. Trước `F5`,
`BomLookupService.getActiveBom` ném `RESOURCE_NOT_FOUND` (404) trong khi routing tương ứng trả 409,
dù spec §8.1 coi hai lỗi cùng loại (nợ #14).

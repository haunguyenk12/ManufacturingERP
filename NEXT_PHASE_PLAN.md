# Next Phase Plan — **chưa chốt, cần user quyết định**

> **`F10` đã hoàn thành 2026-08-01.** Trước đó `F9`. Bản ghi: `CLAUDE.md §0.21` +
> `FRONTEND_ALIGNMENT_ROADMAP.md §3.10`. Bảng đối chiếu spec: roadmap **§7**.
>
> 🔴 **Đọc `CLAUDE.md §0.20` trước khi tuyên bố bất kỳ nợ nào "đã đóng".** Repo đã **ba lần** tuyên bố
> đóng rộng hơn phạm vi thực sự rà (`D7` → nợ #26; `F7` → `F8`; `F8` → `F9`). Quy tắc: ghi **đã rà tới
> đâu**, không ghi "xong".
>
> File này **cố ý chưa chứa prompt phase mới** (quy ước `MANUFACTURING_GAP_ROADMAP.md §2.1` là ghi đè
> toàn bộ file khi phase sau đã được chọn). Sau `F10`, track `F*` **không còn nợ nào thuộc loại "field
> FE hiển thị mà backend đã có sẵn dữ liệu"** ⇒ **không còn phase kế tiếp hiển nhiên**; chọn cái nào là
> quyết định về **hướng sản phẩm**, không phải về nợ kỹ thuật.

---

## 1. Trạng thái bàn giao *(đo 2026-08-01 sau `F10`)*

```text
Baseline:            556 case unit  +  59 case IT / 10 class IT   ·  failures = 0
Coverage (unit+IT):  line 80.1%  ·  branch 63.9%
Coverage (unit):     line 73.7%  ·  branch 59.2%
Migration mới nhất:  V40  (F10 — projected_available + routing snapshot trên proposal + MI-/PE- code)
Controller có test:  20/20   ·   Nợ #2, #9, #25, #26 và nợ A đã đóng hết, đừng mở lại
Spec FE:             docs/fe-spec-omniplant.md · roadmap §7 — đã rà tới TỪNG Ô của các bảng
                     "Trường cần hiển thị"; ⚠️ còn lại đều là lệch tên có chủ đích / cố ý cắt
```

⚠️ **Ba cái bẫy khi đo lại số liệu:**
1. Đọc dòng tổng `Tests run:` của `mvn -o clean verify`. **Đừng** pipe qua `| head -n` — SIGPIPE cắt
   dòng tổng mà vẫn trả exit 0, nhìn như build sạch (bài học `D11`).
2. `jacoco:report` bind vào phase `test` ⇒ `mvn -o clean verify` cho số **unit một mình**. Muốn
   **unit + IT** phải chạy `mvn -o clean verify` rồi **`mvn -o jacoco:report`** lần nữa.
3. 🔴 **`*IT` cần Docker Desktop đang chạy** (Testcontainers). Nếu daemon tắt, cả 10 class IT
   **error** với `NoClassDefFoundError: AbstractPostgresIntegrationTest` — nhìn giống lỗi code chứ
   không giống "thiếu Docker". Kiểm bằng `docker info` trước khi đi tìm nguyên nhân trong source.

---

## 2. Ứng viên phase sau — **cần user chốt**

| Ứng viên | Nội dung | Ghi chú |
|---|---|---|
| **`D8`** | Nợ **#6**: RTR (reuse detection), absolute session timeout, forgot-password | Thiết kế xong từ lâu, chưa implement. `S11`/`S13` ở `best-practices.md §8.1` đang là 🔜 Phase 2. **Không** phụ thuộc gì của `F*` |
| **`P3`** | Costing engine (Standard/Job/Process + variance) | Module `costing` chưa bắt đầu. Lớn nhất trong danh sách |
| **`P4`** *(phần còn lại)* | Work Center entity + CRP | 🔴 **spec §11 TỰ xếp ngoài MVP** ⇒ cần user xác nhận tường minh trước khi khởi động. `F4` cố ý để `workCenterCode` là string |
| **Nợ `E`** | BOM `code` + `outputQuantity` (§2.4, §3.3) | 🔴 **Không phải field thiếu mà là khái niệm chưa tồn tại**: `BomHeader` chỉ có `revision`. Và `outputQuantity` **đổi công thức nổ BOM** (`requiredQuantity` phải chia cho nó) ⇒ đổi số MRP **và** component line của mọi WO đã tạo. User đã loại nó khỏi `F10` (2026-08-01) |
| **Nợ `B`, `C`, `D`, `I`** | `predecessorOperationIds` · `created_by` trong scope idempotency · 17 DTO ngoài luồng spec · `childBom` trên WO snapshot | Đều đã ghi lý do hoãn ở roadmap §7.1. Không cái nào là bug |

---

## 3. Ràng buộc chung (áp dụng cho bất kỳ phase nào được chọn)

1. **Baseline trước, luôn luôn.** Kết thúc **≥ 556 case unit / 59 IT**, `failures = 0` (`R10`).
2. **Không xoá test.** Nghiệp vụ đổi ⇒ **sửa**; test hết ý nghĩa ⇒ **thay**. Không nới assertion.
3. **Nghiệm thu mutation bắt buộc** — tối thiểu 3, mỗi cái làm **đỏ** ít nhất 1 case xác định trước,
   rồi revert. Mutation đổi mã lỗi **phải giữ nguyên HTTP status**. Ưu tiên mutation đụng `src/main`.
   🔴 **Bài học `F10`:** một mutation **xanh** không có nghĩa là code đúng — nó có nghĩa là **test đang
   xanh vì lý do sai**. Mutation #3 của `F10` xanh ở lần chạy đầu và lộ ra một assertion `isNull()`
   pass vì fixture không có dữ liệu để mà sai. Gặp mutation xanh thì **sửa test**, đừng bỏ mutation.
4. 🔴 **Logic nằm trong JPQL ⇒ bắt buộc `*IT` với Testcontainers** (`R7`). Mock repository ở đó chỉ
   tạo tautology. Tiền lệ: `B75` (`F7`), `B76` (`F8`), `status = ACTIVE` của query reservation (`F9`).
   Tương tự, **`@PrePersist` chỉ kiểm được bằng insert thật** (`B68` của `D4`, `B79` của `F10`).
5. **Không sửa migration cũ** (`C5`). Schema đổi = migration mới (`V41`+).
6. **Permission mới ⇒ bắt buộc** seed migration cùng phase + `docs/roles-and-permissions.md` (`C10`).
7. **Thêm giá trị enum status ⇒ chạy đủ 6 bước** `coding-rules.md §11.3`.
8. 🔴 **Đụng spec FE ⇒ cập nhật hàng tương ứng ở `FRONTEND_ALIGNMENT_ROADMAP.md §7`** trong **cùng
   commit**, và **ghi rõ đã rà tới đâu — TUYỆT ĐỐI không ghi "xong"/"hết"/"toàn diện"**. Repo đã mắc
   lỗi này **ba lần**; bảng đối chiếu ở `CLAUDE.md §0.20`. **Và:** khi kế hoạch phase liệt kê một danh
   sách field, danh sách đó là **gợi ý** — bảng spec mới là **nguồn**.
9. **Cập nhật tài liệu trong cùng commit** (`dev-workflow.md §6.5`).
10. **Đo lại coverage mỗi phase**, đừng chép số cũ (nợ #20 từng đúng là lỗi này).

---

## 4. KHÔNG làm, dù có vẻ hợp lý

| Việc | Vì sao |
|---|---|
| Backfill `mrp_requirement_lines.projected_available_quantity` cho run cũ | `available + openSupply` **chính là con số sai** mà cột đó sinh ra để thay thế (`B77`). `NULL` = "run trước `V40`" |
| Derive `projectedAvailable` trong mapper "cho gọn" | Số bị trừ (`consumedCoverageByItem`) chỉ sống trong một lần chạy calculate — derive ra số **lớn hơn thực tế** khi item nằm trên nhiều line |
| Thêm lại `findItemIdsWithActiveRouting` bên cạnh `findActiveRoutingSummaries` | 2 query cho cùng dữ liệu mỗi cấp BOM; `keySet()` đã trả lời đúng câu hỏi đó (`C14`, `B78`) |
| Gán document code sau `save()` thay vì `@PrePersist` | Hibernate chụp snapshot lúc queue INSERT ⇒ cột `NOT NULL` nổ (`B68`, `B79`) |
| Đổi prefix `PE-` sang `WIP-` "cho khớp spec" | `wip_transactions` là bảng ledger khác, không có `code` — lệch tên có chủ đích (`B79`) |
| Thêm cột `status` cho `ProductionExecution` | Một giá trị duy nhất, chưa có đường chuyển — `coding-rules.md §11.5` |
| Rename field sang tên spec (`workOrderNo`→`code`, `productItemCode`→`outputItemSku`…) | **User chốt additive** (2026-07-31, `F8`). Field **thiếu** chặn màn hình FE; tên khác thì FE map được |
| Wire `X-Plant-Id` "cho đồng bộ cả controller" | Ranh giới theo **endpoint**, không theo controller (`error-handling.md §5.6.1`). Đã chạm bẫy này **3** phase liên tiếp |
| Thêm endpoint alias uỷ quyền từ **service** sang service cùng bean | Self-invocation bypass Spring proxy ⇒ `@PreAuthorize` không chạy (`§0.19` hệ quả #3) |
| Mint `PERM_*_READ` mới cho endpoint đọc | `V17` seed một permission/loại chứng từ, mô tả *"Post **and read**"* (`§0.19` hệ quả #5) |
| Test concurrent 2 thread trong `ProductionFlowE2EIT` | Bên thua có **3** kết cục tuỳ timing ⇒ flaky. Nợ A đã đóng bằng 2 test deterministic ở 2 tầng |
| Làm `childBom` nested vào WO snapshot | Nợ `I` — WO snapshot direct-only theo `B12`, user chốt 2026-07-31 |
| Mở lại nợ **#2** / **#9** / **#25** / **#26** / **A** | Đã đóng ở `D7b` / `D7`+`D11`+`F5-A` / `D11` / `D11` / `F8` |
| "Đồng bộ" nốt các chỗ 422 còn lại | Master data / input sai ⇒ đúng 422. Danh sách: `B72`/`B73`/`B74` |
| Thêm `@SpringBootTest` thứ hai | Rule `T1`: **đúng một** (`ProductionFlowE2EIT`) |
| Nới thêm gate `canReceipt()`/`canReserve()`/`canExecute()` | Ba gate tách nhau **có chủ đích** (`B13`, đã tách 2 lần) |

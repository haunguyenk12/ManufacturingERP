# Bộ dữ liệu demo Capstone 2 — namespace `D26-`

> Nguồn yêu cầu: `BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx`
> Script: `scripts/demo-capstone2/` · Manifest: `demo-capstone2-ids.json` (không commit)
> Đối tượng xem dữ liệu: **khách hàng / hội đồng**. Bộ dữ liệu này **không** chứa bản ghi lỗi,
> bản ghi `INACTIVE` trưng bày, hay trạng thái `BLOCKED`/`REJECTED` nào.

---

## 1. Tài khoản đăng nhập

| Vai trò | Tài khoản | Mật khẩu | Phạm vi | Làm được gì |
|---|---|---|---|---|
| Quản trị viên | `admin` | `OmniPlant@Demo2026` | Toàn hệ thống | Mọi thứ, kể cả danh mục toàn cục |
| Quản lý sản xuất | `quanly.demo` | `OmniPlant@2026` | `D26-PLANT` | Chạy MRP, duyệt đề xuất, tạo/release lệnh sản xuất, giữ chỗ vật tư, **duyệt phiếu nhập kho**, **phán quyết QC** |
| Công nhân vận hành | `congnhan.demo` | `OmniPlant@2026` | `D26-PLANT` | Xuất vật tư, ghi sản lượng, **tạo + gửi duyệt** phiếu nhập kho |

`defaultPlantId` của cả hai tài khoản vận hành đã trỏ đúng `D26-PLANT`, nên đăng nhập xong là
vào thẳng nhà máy demo.

**Số quyền thực tế đo được:** `quanly.demo` 53 quyền, `congnhan.demo` 24 quyền — đúng tách bạch
mà §3.2 của tài liệu yêu cầu (operator **không** có `PERM_PRODUCTION_RECEIPT_APPROVE`,
`PERM_QUALITY_DISPOSITION`, `PERM_MRP_RUN`, `PERM_WORK_ORDER_MANAGE`).

> 🔴 **Một màn hình KHÔNG mở được bằng tài khoản vận hành: danh sách công ty toàn cục**
> (`GET /v1/companies` trả **403** cho `quanly.demo` và `congnhan.demo`).
> Đây **không phải** lỗi của bộ dữ liệu mà là đặc điểm có sẵn của repo:
> `PermissionGuard.hasPermission` chỉ đọc assignment có `scopeType = GLOBAL`, trong khi hai tài
> khoản này gán phạm vi `PLANT` (xem `module/uom/CLAUDE.md`). Mọi màn hình trong luồng demo đều đi
> qua `hasResourceAccess` và **chạy bình thường**. Khi trình diễn, dùng `admin` cho màn hình
> master data toàn cục.

---

## 2. Bộ dữ liệu có gì

| Bảng | Số dòng | Ghi chú |
|---|---|---|
| Công ty | 1 | `D26-OMNIPLANT` — Công ty Cổ phần Xe đạp OmniPlant |
| Nhà máy | 1 | `D26-PLANT` — Nhà máy Demo 2026, múi giờ `Asia/Ho_Chi_Minh` |
| Kho | 5 | `D26-RM` (NVL) · `D26-WIP` (BTP) · `D26-FG` (thành phẩm) · `D26-QC` · `D26-SCRAP` |
| Vật tư | 10 | 1 thành phẩm **lot-tracked**, 2 bán thành phẩm, 7 nguyên vật liệu — đều `NON_TRACKED`; **không có serial** |
| Cấu hình item-warehouse | 10 | Đúng **một** dòng cho mỗi vật tư (nhiều hơn một sẽ thành `AMBIGUOUS_WAREHOUSE_POLICY`) |
| Định mức (BOM) | 3 `ACTIVE` / 9 dòng | BOM 2 cấp |
| Quy trình công nghệ | 3 `ACTIVE` / 5 công đoạn | Mỗi công đoạn trỏ tới một tổ sản xuất `ACTIVE` cùng nhà máy |
| Tổ sản xuất | 4 | Đều gắn lịch `D26-CAL-DAY` |
| Ca / lịch làm việc | 1 / 1 | Thứ Hai–Thứ Bảy, 08:00–17:00 nghỉ trưa 12:00–13:00 (8 giờ công/ngày) |
| Tồn kho đầu kỳ | 9 phiếu nhập | Tất cả `AVAILABLE`, `reserved = 0`; thành phẩm cố ý **bằng 0** |
| Đơn bán hàng | 0 | KHÔNG seed — bạn tự tạo (xem §3) |
| Lượt chạy MRP | **0** | Chờ đơn hàng của bạn |
| Lệnh sản xuất | **0** | Sinh ra ngay trên sân khấu từ đề xuất MRP |

### Cây sản phẩm

```
D26-FG-BIKE16  Xe đạp trẻ em 16 inch          (thành phẩm, LOT_TRACKED, kho D26-FG)
├── D26-WIP-FRAME16  Bộ khung hoàn thiện  x1   (tồn sẵn 12 tại D26-WIP)
│   ├── D26-RM-FRAME     Khung thô        x1
│   └── D26-RM-PAINT     Sơn phủ          x0.15 KG
├── D26-WIP-DRIVE7   Bộ truyền động 7 tốc x1   (tồn sẵn 12 tại D26-WIP)
│   ├── D26-RM-CHAINRING Bộ giò đĩa       x1
│   ├── D26-RM-CHAIN     Xích xe          x1
│   └── D26-RM-CASSETTE7 Líp 7 tầng       x1
├── D26-RM-WHEEL16   Bánh xe 16 inch      x2   (tồn sẵn 24 tại D26-RM)
└── D26-RM-BRAKE     Bộ phanh             x1   (tồn sẵn 12 tại D26-RM)
```

> Bán thành phẩm được nạp sẵn để buổi demo **không phải** chạy thêm lệnh sản xuất cấp dưới —
> mọi dòng nhu cầu cấp 1 trả về `COVERED`, nên lượt chạy MRP cho ra **đúng một** đề xuất MAKE.

---

## 3. Kịch bản trình diễn (khớp §12 của tài liệu)

| # | Ai | Làm gì | Kết quả phải thấy |
|---|---|---|---|
| 1 | bạn | Tự tạo Sales Order (vd: 10 cái `D26-FG-BIKE16`) rồi CONFIRM | Đơn hàng → `CONFIRMED`, sinh 1 dòng nhu cầu hoạch định |
| 2 | `quanly.demo` | Mở đơn hàng đó, chạy Planning với kho nhu cầu `D26-FG` | Dòng nhu cầu cấp 0 `SHORTAGE`, 4 dòng cấp 1 `COVERED` · 1 đề xuất · **0 đề xuất bị chặn** |
| 3 | `quanly.demo` | Duyệt đề xuất MAKE rồi chuyển thành lệnh sản xuất | Đúng 1 lệnh sản xuất, đúng số lượng đã đặt |
| 4 | `quanly.demo` | Giữ chỗ vật tư rồi release | Sẵn sàng vật tư **100%**, thiếu hụt **0 dòng** |
| 5 | `congnhan.demo` | Xuất vật tư đúng định mức | Khung 1x · Truyền động 1x · Bánh 2x · Phanh 1x (theo số lượng lệnh sản xuất) |
| 6 | `congnhan.demo` | Ghi sản lượng: tốt = kế hoạch, phế 0, làm lại 0 | Lệnh sản xuất → `COMPLETED` |
| 7 | `congnhan.demo` | Tạo phiếu nhập kho, đặt lô tuỳ ý, gửi duyệt | `DRAFT` → `PENDING_APPROVAL` |
| 8 | `quanly.demo` | Duyệt phiếu nhập kho | Lô ở **`HOLD`** — tồn thực tế tăng nhưng **khả dụng vẫn 0** |
| 9 | `quanly.demo` | Phán quyết QC: cho phép xuất | Lô → `AVAILABLE`, tồn khả dụng `D26-FG` tăng đúng số lượng |
| 10 | — | Truy vết ngược đơn hàng → lệnh sản xuất → ảnh chụp BOM/quy trình → xuất → sản lượng → nhập → lô | Đơn hàng → **`FULFILLED`**, đã giao đủ số lượng |

> Bước 8 là điểm nhấn đáng chỉ cho hội đồng: hàng **đã có trong kho** (tồn thực tế) nhưng
> **chưa dùng được** (khả dụng 0) cho tới khi QC phán quyết. Đó là điều tách hệ thống này khỏi
> một phần mềm quản lý kho đơn thuần.

**Số liệu đã nghiệm thu thật trên chính bộ dữ liệu này** (chạy `verify.sh` trên một cơ sở dữ liệu
dùng-một-lần, xem §5): nhu cầu cấp 0 `gross 10 / net 10`, 4 dòng cấp 1 đều `COVERED`, đề xuất
`MAKE` / `READY` với thông điệp duy nhất `MATERIAL_SHORTAGE`, tổng lượng xuất 50, đơn hàng kết
thúc `FULFILLED`.

---

## 4. Dựng lại bộ dữ liệu từ đầu

Cần **Git Bash** (không dùng PowerShell/CMD — tên tiếng Việt sẽ hỏng mã hoá) và Docker Desktop
đang chạy.

```bash
# 1. Dừng backend đang chạy (giải phóng cổng 8080)

# 2. Xoá sạch Postgres + Redis, dựng lại lược đồ
./scripts/demo-capstone2/reset-db.sh

# 3. Khởi động backend MỘT LẦN để cấp tài khoản quản trị trên cơ sở dữ liệu trắng
mvn -o spring-boot:run -Dspring-boot.run.profiles=dev \
    "-Dspring-boot.run.arguments=--app.bootstrap.admin.enabled=true"
#    ...đợi log "Administrator 'admin' provisioned", rồi DỪNG tiến trình này.

# 4. Khởi động backend ở chế độ thường
mvn -o spring-boot:run -Dspring-boot.run.profiles=dev

# 5. Seed
ADMIN_PASSWORD='OmniPlant@Demo2026' ./scripts/demo-capstone2/seed.sh
```

Script dừng lại sau khi nạp tồn kho đầu kỳ — **không tạo Sales Order**. Tự tạo đơn hàng của
bạn (qua UI hoặc `POST /sales-orders/v1` + `POST /sales-orders/v1/{id}/confirm`), rồi mới
chạy Planning. Muốn seed lại phần còn lại nhiều lần: lặp lại bước 1→5, mỗi lần một namespace
`D26-` sạch, rồi tự tạo đơn hàng mới cho lượt đó.

🔴 **Bước 3 và 4 không gộp được.** `AdminBootstrapService` ném lỗi lúc khởi động nếu bật cờ
bootstrap trong khi tài khoản quản trị **đã** `ACTIVE` — để cờ bật rồi khởi động lại sẽ làm backend
không lên được. `.env` giữ `APP_BOOTSTRAP_ADMIN_ENABLED=false`; bước 3 chỉ ghi đè tạm bằng tham số
dòng lệnh.

Script dùng **mã cố định** (đó chính là điều làm dữ liệu dễ đọc dễ nhớ) nên **không chạy lại được**
trên cơ sở dữ liệu đã có namespace `D26-`. Nó phát hiện và dừng ngay ở bước preflight kèm hướng dẫn.

---

## 5. Nghiệm thu (`verify.sh`)

`seed.sh` tự chạy **preflight chỉ đọc** PF-01…PF-08, PF-15 và **từ chối bàn giao** nếu có mục
nào trượt. PF-09/PF-11 (Sales Order + nhu cầu hoạch định) không còn chạy trong `seed.sh` vì
script không tạo đơn hàng nữa.

Phần còn lại (PF-10, PF-12, PF-13, PF-14 và smoke test 9 bước) **bắt buộc phải ghi dữ liệu**: chạy
Planning, tạo lệnh sản xuất, xuất vật tư, QC — trên **chính đơn hàng bạn chỉ định**. Vì vậy
`verify.sh` giờ đòi thêm biến `SALES_ORDER_NO` trỏ tới một đơn hàng **CONFIRMED** bạn đã tự tạo,
và đòi `ALLOW_DESTRUCTIVE=1` để không ai chạy nhầm lên đơn hàng định dùng cho buổi demo thật.
Quy trình đúng là **hai lượt**:

```bash
# lượt 1 — nghiệm thu (trên cơ sở dữ liệu dùng-một-lần)
./scripts/demo-capstone2/reset-db.sh   # + bước 3, 4 ở mục 4
ADMIN_PASSWORD='...' ./scripts/demo-capstone2/seed.sh
#   ...tự tạo một Sales Order THỬ (vd SO-TEST-001), CONFIRM nó, rồi:
ALLOW_DESTRUCTIVE=1 ADMIN_PASSWORD='...' SALES_ORDER_NO=SO-TEST-001 \
    ./scripts/demo-capstone2/verify.sh

# lượt 2 — bàn giao (xoá sạch rồi seed lại, dừng ở tồn kho đầu kỳ)
./scripts/demo-capstone2/reset-db.sh   # + bước 3, 4 ở mục 4
ADMIN_PASSWORD='...' ./scripts/demo-capstone2/seed.sh
#   ...tự tạo đơn hàng THẬT cho buổi demo — KHÔNG chạy verify.sh lên nó
```

Kết quả lượt nghiệm thu gần nhất: **PF-01 → PF-08, PF-15 và toàn bộ smoke test §12 đều PASS**
(chạy trên một đơn hàng thử do người vận hành tự tạo).
Kết quả lượt nghiệm thu gần nhất: **PF-01 → PF-15 và toàn bộ smoke test §12 đều PASS.**

---

## 6. Những chỗ script lệch tài liệu (có chủ đích, đã kiểm chứng)

| Tài liệu | Thực tế trong repo | Vì sao |
|---|---|---|
| Loại vật tư `SEMI_FINISHED` | `ItemType.WIP` | Enum của backend không có `SEMI_FINISHED`; `WIP` là đúng ô đó |
| BOM có mã `D26-BOM-*` | `BomHeader` **không có** cột `code` | Mã của tài liệu được ghi vào `description` để vẫn tra ngược được |
| Ca "08:00-12:00 **và** 13:00-17:00" | Một ca 08:00–17:00 + giờ nghỉ 12:00–13:00 | Mô hình `Shift` là **một** khoảng làm việc kèm danh sách giờ nghỉ; tổng vẫn đúng 8 giờ công/ngày |
| "Dùng Company `ACTIVE` hiện hành" | Script **tạo** công ty `D26-OMNIPLANT` | Yêu cầu là xoá sạch dữ liệu trước, nên sau khi xoá không còn công ty nào để dùng lại |
| Phạm vi truy cập gồm cả kho | Phạm vi `PLANT` chỉ chứa tài nguyên `PLANT` | `AccessControlService.isResourceTypeAllowed` từ chối `WAREHOUSE` trong phạm vi `PLANT` (bất biến RBAC siết ở `V63`). Không cần thêm: `hasWarehouseAccess` lùi về nhà máy cha nên một tài nguyên `PLANT` phủ trọn 5 kho |
| Đường dẫn kiểu `/api/v1/...` | **Hỗn hợp** | Phần lớn ở `/api/v1/...`, nhưng `auth`/`users`/`access`/`sales-orders` đặt `/v1` **sau** tiền tố tài nguyên (`/api/auth/v1/login`, `/api/sales-orders/v1`). `seed.sh` đối chiếu 25 đường dẫn bắt buộc với OpenAPI runtime và **dừng ngay** nếu lệch — đoán sai đường dẫn ở API này trả `401 AUTHENTICATION_REQUIRED` chứ không phải 404, tức trông hệt như sai mật khẩu |

---

## 7. Những gì bộ dữ liệu này CỐ Ý không có

Đây là bộ dữ liệu trình diễn cho khách hàng, không phải bộ dữ liệu kiểm thử:

- Không có lô `HOLD` / `REJECTED` / hết hạn trong tồn kho đầu kỳ.
- Không có bản ghi `INACTIVE` nào (kho, vật tư, tổ sản xuất, đơn vị tính).
- Không có đề xuất `BLOCKED`, không có `MISSING_BOM` / `MISSING_ROUTING`.
- Không có Purchase Requisition / Purchase Order / Goods Receipt (Purchasing ngoài phạm vi Capstone 2).
- Không dùng Serial Tracking.
- Không có lượt chạy MRP hay lệnh sản xuất seed sẵn.

Cần bộ dữ liệu **phủ mọi trạng thái** (kể cả trạng thái lỗi) để kiểm thử thì dùng seeder cũ
`scripts/demo/seed-demo.sh` (chủ đề `VIETBIKE`) — **trên môi trường khác**, đừng trộn hai bộ.

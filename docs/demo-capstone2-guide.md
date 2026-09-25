# Bộ dữ liệu demo Capstone 2 — hai công ty `D26-` và `DEMO-`

> Nguồn yêu cầu gốc: `BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx`
> Script: `scripts/demo-capstone2/` · Manifest: `demo-capstone2-ids.<profile>.json` (không commit)
> Đối tượng xem dữ liệu: **khách hàng / hội đồng**. Không bản ghi lỗi, không bản ghi `INACTIVE`
> trưng bày, không trạng thái `BLOCKED`/`REJECTED` nào.

> 🔴 **Bản 2026-08-25: MỘT hệ thống, HAI công ty.** Chạy `seed.sh` hai lượt với `PROFILE` khác nhau:
>
> | Công ty | Namespace | Tài khoản | Dùng để | Chạy tới đâu |
> |---|---|---|---|---|
> | `D26-OMNIPLANT` *(Sample Data)* | `D26-` | `*.sample` | **Chụp hình giao diện bỏ vào slide** | Đi **hết luồng**: mua hàng, bán hàng, MRP, lệnh sản xuất, xuất/nhập, QC |
> | `DEMO-OMNIPLANT` *(Live Demo)* | `DEMO-` | `*.demo` | **Trình diễn trực tiếp trước hội đồng** | Dừng ở **tồn kho đầu kỳ** — không đơn hàng, không MRP, không lệnh sản xuất |
>
> Hai công ty dùng **chung một danh mục** (40 vật tư, 9 BOM, 9 quy trình…) nhưng mọi mã đều mang
> namespace riêng và mọi bất biến đều theo từng nhà máy, nên chúng không đụng nhau. Công ty
> `DEMO-` giữ đúng tinh thần §9 của tài liệu gốc ("Không seed Planning Run trước: để người trình
> bày chạy Planning trực tiếp"), còn `D26-` là bộ đã chạy xong để chụp hình.

> ⚠️ **Vì sao không nạp thẳng file backup cũ để có công ty thứ hai.** `backups/*.dump` là bản
> `pg_dump` của **toàn bộ cơ sở dữ liệu**, không phải bản xuất riêng một công ty, và bộ dữ liệu
> trong đó dùng **đúng cùng dãy mã** `D26-` với bộ hiện tại — nạp đè lên nhau sẽ đụng ràng buộc
> duy nhất ở `companies.code`, `items.code` và hàng chục bảng khác. Nên công ty thứ hai được dựng
> **bằng chính script**, với namespace riêng. Các file dump cũ vẫn nằm nguyên trong `backups/`
> để nạp lại nguyên trạng khi cần (`restore-db.sh`), chỉ là không gộp chồng lên nhau được.

---

## 1. Tài khoản đăng nhập

Mật khẩu của mọi tài khoản vận hành: **`OmniPlant@2026`**. Quản trị viên: `admin` /
**`OmniPlant@Demo2026`** (toàn hệ thống, thấy cả hai công ty).

| Vai trò | Công ty mẫu (`D26-`) | Công ty demo (`DEMO-`) | Làm được gì |
|---|---|---|---|
| Quản lý sản xuất | `quanly.sample` | `quanly.demo` | Chạy MRP, duyệt đề xuất, tạo/release lệnh sản xuất, giữ chỗ vật tư, **duyệt phiếu nhập kho**, **phán quyết QC** |
| Nhân viên kế hoạch | `kehoach.sample` | `kehoach.demo` | Như trên (cùng vai trò `MANAGER`) — có tài khoản thứ hai để màn hình phân quyền và nhật ký kiểm toán có nhiều người |
| Công nhân vận hành | `congnhan.sample` | `congnhan.demo` | Xuất vật tư, ghi sản lượng, **tạo + gửi duyệt** phiếu nhập kho |
| Thủ kho | `kho.sample` | `kho.demo` | Như trên (cùng vai trò `OPERATOR`) |

`defaultPlantId` của mỗi tài khoản trỏ đúng nhà máy của công ty tương ứng, nên đăng nhập xong là
vào thẳng đó. **Phân quyền có hạn hiệu lực 365 ngày** (cột "Expiry" trên màn hình phân quyền không
còn trống) — riêng assignment của `admin` do migration `V2` tạo thì **không** có hạn, đúng như nên
thế: một tài khoản quản trị hết hạn giữa chừng sẽ khoá cả hệ thống.

**Tách bạch quyền:** vai trò `OPERATOR` **không** có `PERM_PRODUCTION_RECEIPT_APPROVE`,
`PERM_QUALITY_DISPOSITION`, `PERM_MRP_RUN`, `PERM_WORK_ORDER_MANAGE` — đúng như §3.2 của tài liệu
yêu cầu.

> 🔴 **Một màn hình KHÔNG mở được bằng tài khoản vận hành: danh sách công ty toàn cục**
> (`GET /v1/companies` trả **403** cho mọi tài khoản demo/sample).
> Đây **không phải** lỗi của bộ dữ liệu mà là đặc điểm có sẵn của repo:
> `PermissionGuard.hasPermission` chỉ đọc assignment có `scopeType = GLOBAL`, trong khi các tài
> khoản này gán phạm vi `PLANT` (xem `module/uom/CLAUDE.md`). Mọi màn hình trong luồng demo đều đi
> qua `hasResourceAccess` và **chạy bình thường**. Khi trình diễn hoặc chụp màn hình master data
> toàn cục — và khi cần **chuyển qua lại giữa hai công ty** — dùng `admin`.

---

## 2. Bộ dữ liệu có gì

Số liệu dưới đây là **đếm thật trong cơ sở dữ liệu** sau lượt seed gần nhất, cho công ty **mẫu
`D26-`** (97 giây, 42/42 mục preflight PASS).

Công ty **demo `DEMO-`** có **y hệt phần master data** (40 vật tư, 9 định mức, 9 quy trình, 8 tổ
sản xuất, 2 ca, 2 lịch, 3 nhà cung cấp, giá thành chuẩn, tồn kho đầu kỳ, 4 tài khoản) nhưng
**0 đơn mua, 0 đơn bán, 0 lượt MRP, 0 lệnh sản xuất** — 67 giây, 20/20 mục preflight PASS.

| Bảng | Số dòng | Ghi chú |
|---|---|---|
| Công ty / nhà máy / kho | 1 / 1 / 5 | `D26-RM` · `D26-WIP` · `D26-FG` · `D26-QC` · `D26-SCRAP`, múi giờ `Asia/Ho_Chi_Minh` |
| Vật tư | **40** | 3 thành phẩm **lot-tracked**, 6 bán thành phẩm, 31 nguyên vật liệu — đều `NON_TRACKED`; **không có serial** |
| Cấu hình item-warehouse | 40 | Đúng **một** dòng cho mỗi vật tư (nhiều hơn một sẽ thành `AMBIGUOUS_WAREHOUSE_POLICY`) |
| Định mức (BOM) | **9 `ACTIVE`** / 59 dòng | BOM 2 cấp cho cả ba dòng sản phẩm |
| Quy trình công nghệ | **9 `ACTIVE`** / 21 công đoạn | Mỗi công đoạn trỏ tới một tổ sản xuất `ACTIVE` cùng nhà máy |
| Tổ sản xuất | **8** | Gắn hai lịch khác nhau (một ca / hai ca) |
| Ca / lịch làm việc | 2 / 2 | Ca ngày 08:00–17:00 nghỉ trưa · ca chiều 17:30–21:30 |
| Giá thành chuẩn | 40 | Nguyên vật liệu mang giá mua; BTP/TP mang nhân công + sản xuất chung, phần vật tư do BOM roll-up |
| Nhà cung cấp | 3 | 31 liên kết vật tư ↔ nhà cung cấp |
| Yêu cầu mua hàng | 3 | `DRAFT` · `APPROVED` · `CONVERTED` |
| Đơn mua hàng | 5 | `DRAFT` · `SENT` ×2 · `PARTIALLY_RECEIVED` · `RECEIVED` (có phiếu nhập kho thật) |
| Đơn bán hàng | 6 | `DRAFT` · `CONFIRMED` ×2 · `CANCELLED` · `PARTIALLY_FULFILLED` · `FULFILLED` |
| Lượt chạy MRP | **3** | Hai lượt đã xử lý xong; lượt thứ ba để lại **32 đề xuất `DRAFT`** (26 BUY + 6 MAKE, đều `READY`) |
| Lệnh sản xuất | **8** | `DRAFT` · `PLANNED` · `RELEASED` · `IN_PROGRESS` ×2 · `CLOSED` ×2 · `CANCELLED` |
| Lô thành phẩm | 6 | 5 `AVAILABLE` + **1 `HOLD` chờ QC** |
| Dòng bảng năng lực | 14 | Có tải, có sức chứa, có % sử dụng |
| Nhật ký kiểm toán | ~370 | Sinh tự nhiên từ chính các thao tác trên |

### Cảnh báo tồn kho trên Dashboard

Ngưỡng được chọn theo tồn khả dụng **thật ở cuối lượt seed** (sau khi mua hàng nhập kho và các
lệnh sản xuất đã xuất vật tư), nên Dashboard có đủ ba nhóm để chụp:

| Nhóm | Số dòng | Ví dụ |
|---|---|---|
| `OK` | 30 | phần lớn danh mục |
| `LOW_STOCK` | 6 | `D26-RM-PAINT` (24.5), `D26-RM-CASSETTE9` (18), `D26-RM-BRAKE` (57) |
| `REORDER_NEEDED` | 4 | `D26-RM-PRIMER` (12), `D26-RM-FRAMERD` (18), `D26-RM-BRAKEDISC` (23), `D26-RM-TIRE700` (26) |

### Ba dòng sản phẩm

```
D26-FG-BIKE16   Kids Bike 16-inch            (lot-tracked, kho D26-FG)
├── D26-WIP-FRAME16   Frame Assembly 16"  x1   -> khung thô + lót + sơn + tem
├── D26-WIP-DRIVE7    7-Speed Drivetrain  x1   -> giò đĩa + xích + líp + tay quay + bàn đạp
└── bánh 16" x2, phanh V, ghi đông, yên, cọc yên, bọc tay, bánh phụ, thùng carton

D26-FG-BIKE20   Youth Bike 20-inch           (lot-tracked)
├── D26-WIP-FRAME20   Frame Assembly 20"  x1
├── D26-WIP-DRIVE7    7-Speed Drivetrain  x1
└── bánh 20" x2, phanh V, ghi đông, yên, cọc yên, bọc tay, thùng carton

D26-FG-ROAD700  Road Racer 700C              (lot-tracked)
├── D26-WIP-FRAMERD   Frame Assembly Road x1
├── D26-WIP-DRIVE18   18-Speed Drivetrain x1  -> thêm líp 9, củ đề, tay đề
├── D26-WIP-WHEELSET  Wheelset 700C       x1  -> vành x2, đùm x2, nan hoa x64, lốp x2, săm x2, bi x2
└── phanh đĩa, ghi đông, yên, cọc yên, bọc tay, carton, bộ dụng cụ
```

> Bán thành phẩm được nạp sẵn tồn kho để lượt MRP chính cho ra **đúng một** đề xuất MAKE ở cấp
> thành phẩm — mọi dòng nhu cầu cấp dưới trả về `COVERED`.

---

## 3. Chụp màn hình ở đâu có gì

| Màn hình | Dữ liệu có sẵn |
|---|---|
| Dashboard tồn kho | 40 vật tư, 10 dòng cảnh báo đủ ba nhóm, 20 giao dịch gần nhất kèm nhãn vật tư/kho/người thao tác |
| Danh mục vật tư | 40 dòng — đủ để bảng phân trang trông thật |
| Định mức / Quy trình | 9 BOM `ACTIVE` 2 cấp, 9 routing với 1–4 công đoạn |
| Tổ sản xuất / Ca / Lịch | 8 tổ, 2 ca, 2 lịch (một ca và hai ca) |
| Bảng năng lực (Capacity Board) | 14 dòng có tải/sức chứa/% sử dụng theo từng tổ và từng ngày |
| Hoạch định (MRP) | 3 lượt chạy; lượt `backlog` còn **32 đề xuất `DRAFT`** để demo thao tác duyệt |
| Lệnh sản xuất | 8 lệnh phủ **6 trạng thái**, có lệnh nối thẳng về đơn bán hàng |
| Phiếu xuất vật tư / ghi sản lượng / nhập kho | Sinh từ 4 lệnh sản xuất đã chạy thật |
| Lô hàng | 6 lô, trong đó **`D26-BIKE20-B002` đang `HOLD`**: tồn thực tế 2 nhưng khả dụng 0 |
| Mua hàng | 3 yêu cầu + 5 đơn mua phủ 4 trạng thái, có phiếu nhập kho thật làm tăng tồn |
| Bán hàng | 6 đơn phủ 5 trạng thái, gồm `FULFILLED` và `PARTIALLY_FULFILLED` |
| Chênh lệch (Variance) | `D26-WO-1004`: chênh lệch thời gian 235 → 300 phút; chi phí chuẩn ~35.2 triệu, có số vật tư/nhân công/sản xuất chung |
| Nhật ký kiểm toán | ~370 bản ghi sinh tự nhiên |

---

## 4. Kịch bản trình diễn trực tiếp

**Diễn trên công ty `DEMO-OMNIPLANT`** (`quanly.demo` / `congnhan.demo`): nó chưa có đơn hàng nào,
nên bạn tự tạo đơn rồi chạy hết luồng — đúng thứ tự dưới đây, không có gì seed sẵn để "lộ bài".

Nếu muốn diễn ngay trên công ty mẫu `D26-` mà không phải tạo đơn mới: nó đã có sẵn một câu chuyện
**đã hoàn tất** để dẫn chứng (`D26-SO-1004` → `D26-WO-1004` → lô `D26-BIKE16-B001` → đơn hàng
`FULFILLED`), và hai đơn `CONFIRMED` chưa hoạch định (`D26-SO-1002`, `D26-SO-1003`) để chạy tiếp
từ bước 1.

| # | Ai | Làm gì | Kết quả phải thấy |
|---|---|---|---|
| 0 | `quanly.demo` | *(chỉ với công ty `DEMO-`)* Tạo đơn bán hàng, ví dụ 10 cái `DEMO-FG-BIKE16`, rồi CONFIRM | Đơn → `CONFIRMED`, sinh dòng nhu cầu hoạch định |

| 1 | `quanly.demo` | Chạy Planning cho đơn hàng đó, kho nhu cầu là kho thành phẩm | Nhu cầu cấp 0 thiếu hàng, các cấp dưới `COVERED` · **0 đề xuất bị chặn** |
| 2 | `quanly.demo` | Duyệt đề xuất MAKE rồi chuyển thành lệnh sản xuất | Lệnh sản xuất mang **số lượng ròng** (đã trừ tồn kho sẵn có) |
| 3 | `quanly.demo` | Giữ chỗ vật tư rồi release | Sẵn sàng vật tư **100%**, thiếu hụt **0 dòng** |
| 4 | `congnhan.demo` | Xuất vật tư đúng định mức | Số dòng đúng bằng số thành phần của BOM |
| 5 | `congnhan.demo` | Ghi sản lượng: tốt = kế hoạch | Lệnh sản xuất → `COMPLETED` |
| 6 | `congnhan.demo` | Tạo phiếu nhập kho, đặt lô mới, gửi duyệt | `DRAFT` → `PENDING_APPROVAL` |
| 7 | `quanly.demo` | Duyệt phiếu nhập kho | Lô ở **`HOLD`** — tồn thực tế tăng nhưng **khả dụng vẫn 0** |
| 8 | `quanly.demo` | Phán quyết QC: cho phép xuất | Lô → `AVAILABLE`, tồn khả dụng `D26-FG` tăng đúng số lượng |
| 9 | — | Truy vết ngược đơn hàng → lệnh sản xuất → ảnh chụp BOM/quy trình → xuất → sản lượng → nhập → lô | Đơn hàng → `FULFILLED` |

> 🔴 **Bước 2 là chỗ dễ nói nhầm nhất.** Số lượng của lệnh sản xuất là **số ròng của MRP**, không
> phải số trên đơn hàng: netting đã trừ tồn kho thành phẩm sẵn có. `D26-FG-BIKE20` đang có tồn nên
> một đơn 8 cái chỉ sinh lệnh sản xuất 2 cái — đó là MRP chạy đúng, không phải lỗi.

> Bước 7 là điểm nhấn đáng chỉ cho hội đồng: hàng **đã có trong kho** (tồn thực tế) nhưng
> **chưa dùng được** (khả dụng 0) cho tới khi QC phán quyết. Đó là điều tách hệ thống này khỏi
> một phần mềm quản lý kho đơn thuần. Lô `D26-BIKE20-B002` đang nằm sẵn ở trạng thái này để chụp
> hình mà không phải chạy lại luồng.

---

## 5. Dựng lại bộ dữ liệu từ đầu

Cần **Git Bash** (không dùng PowerShell/CMD) và Docker Desktop đang chạy.

```bash
# 1. Dừng backend đang chạy (giải phóng cổng 8080)

# 2. Xoá sạch Postgres + Redis, dựng lại lược đồ
./scripts/demo-capstone2/reset-db.sh

# 3. Biên dịch lại SẠCH rồi khởi động backend MỘT LẦN để cấp tài khoản quản trị,
#    đồng thời TẮT giới hạn tần suất vì seed bắn hơn 600 request liên tiếp
mvn -o -q -DskipTests clean compile
mvn -o -q spring-boot:run -Dspring-boot.run.profiles=dev     "-Dspring-boot.run.jvmArguments=-DAPP_BOOTSTRAP_ADMIN_ENABLED=true -Dapp.rate-limit.enabled=false"
#    ...đợi log "Administrator 'admin' provisioned"

# 4. Seed HAI lượt trên cùng cơ sở dữ liệu đó (backend ở bước 3 vẫn đang chạy)
ADMIN_PASSWORD='OmniPlant@Demo2026' PROFILE=sample ./scripts/demo-capstone2/seed.sh   # công ty mẫu
ADMIN_PASSWORD='OmniPlant@Demo2026' PROFILE=demo   ./scripts/demo-capstone2/seed.sh   # công ty demo

# 5. Điền hạn dùng cho lô (REST API không có đường đặt hạn lô — xem ghi chú trong script)
./scripts/demo-capstone2/set-lot-expiry.sh

# 6. Dừng backend rồi khởi động lại ở chế độ thường (bật lại giới hạn tần suất)
mvn -o -q spring-boot:run -Dspring-boot.run.profiles=dev

# 7. (tuỳ chọn) Chụp lại trạng thái để lần sau nạp thẳng, khỏi seed lại
./scripts/demo-capstone2/backup-db.sh d26-two-companies
```

Thứ tự hai lượt seed **không quan trọng** — chúng độc lập hoàn toàn. Muốn chỉ một công ty thì chạy
đúng một lượt.

🔴 **Bắt buộc tắt giới hạn tần suất khi seed.** `app.rate-limit.enabled` được ghi cứng `true`
trong `application.yml` và ngưỡng mặc định là 500 request/phút cho mỗi người dùng; bộ dữ liệu này
bắn nhiều hơn thế, nên seed sẽ chết giữa chừng với `RATE_LIMIT_EXCEEDED` nếu để bật. Cờ chỉ được
ghi đè tạm bằng tham số dòng lệnh — **đừng** sửa `application.yml`.

🔴 **Bước 3 không gộp được với lần khởi động sau.** `AdminBootstrapService` ném lỗi lúc khởi động
nếu bật cờ bootstrap trong khi tài khoản quản trị **đã** `ACTIVE` — để cờ bật rồi khởi động lại sẽ
làm backend không lên được. `.env` giữ `APP_BOOTSTRAP_ADMIN_ENABLED=false`.

🔴 **Chỉ chạy MỘT lượt seed tại một thời điểm.** Backend là **single-session**: `AuthService.login`
thu hồi mọi phiên cũ của cùng tài khoản, nên hai tiến trình seed cùng đăng nhập `admin` sẽ làm
tiến trình chạy trước chết giữa chừng với `TOKEN_REVOKED`. Nếu thấy mã lỗi đó, gần như chắc chắn
còn một lượt seed cũ đang chạy nền.

⚠️ **Nếu backend chết lúc khởi động với `ClassNotFoundException` hoặc `No qualifying bean of type
'...Mapper'`:** `target/classes` bị cắt dở do tiến trình Maven trước bị giết giữa lúc biên dịch, và
biên dịch **tăng dần** không sửa được (nó tưởng không có gì thay đổi). Chạy
`mvn -o -q -DskipTests clean compile` rồi khởi động lại — đó là lý do bước 3 có `clean`.

Script dùng **mã cố định** (đó chính là điều làm dữ liệu dễ đọc dễ nhớ) nên **không chạy lại được**
trên cơ sở dữ liệu đã có công ty của cùng profile. Nó phát hiện và dừng ngay ở bước preflight kèm
hướng dẫn.

**Sửa dữ liệu thì sửa `scripts/demo-capstone2/catalogue.sh`** — mọi mã, tên, số lượng, ngưỡng,
trạng thái mục tiêu, hạn hiệu lực đều nằm ở đó và chỉ ở đó. `seed.sh` không chứa một con số nghiệp
vụ nào; nó đổi tiền tố `D26-` sang namespace của profile ngay sau khi nạp catalogue.

---

## 6. Nghiệm thu

`seed.sh` tự chạy preflight ở cuối và **từ chối bàn giao** nếu có mục nào trượt:
**42 mục** với `PROFILE=sample`, **20 mục** với `PROFILE=demo` (profile demo không có chứng từ
nghiệp vụ nên bỏ các mục PF-10…PF-14).

- PF-01…PF-07 — tổ chức, vật tư, cấu hình kho, BOM, routing, tổ sản xuất đều `ACTIVE` và đủ số
  lượng **suy ra từ chính `catalogue.sh`** (đổi bảng dữ liệu thì preflight tự đổi theo).
- PF-08 — không dòng tồn kho nào âm sau toàn bộ chứng từ.
- PF-10/PF-11 — cả 3 lượt chạy MRP `COMPLETED`, **0 đề xuất bị chặn**, mọi dòng nhu cầu phân giải
  được kho.
- PF-13 — lệnh sản xuất phủ đủ 6 trạng thái và **không có `BLOCKED`** nào.
- PF-14 — đơn bán hàng phủ đủ 5 trạng thái.
- PF-15 — cả 4 tài khoản vận hành của công ty đó đăng nhập được và thấy đúng nhà máy của mình.

> 🔴 **PF-03 từng đúng vì may.** `GET /v1/inventory/item-warehouse-settings` **không có** tham số
> `plantId` — truyền vào thì nó bị bỏ qua im lặng và endpoint trả cấu hình của **mọi** công ty. Với
> một công ty trong cơ sở dữ liệu, con số vẫn khớp; đúng lúc thêm công ty thứ hai thì nó báo gấp
> đôi. Nay preflight cộng theo **từng kho** của nhà máy. Bài học: một phép kiểm chỉ chạy trên một
> tập dữ liệu duy nhất thì chưa chứng minh được nó lọc đúng.

`verify.sh` (bản cũ, nghiệm thu §11/§12 của tài liệu gốc) vẫn dùng được nhưng **không còn cần
thiết**: `seed.sh` nay tự đi hết luồng và tự kiểm. Nó **ghi dữ liệu không đảo ngược được**, nên chỉ
chạy trên cơ sở dữ liệu dùng-một-lần và phải đặt `ALLOW_DESTRUCTIVE=1` cùng `SALES_ORDER_NO`.
⚠️ Nó đọc manifest ở đường dẫn cũ `demo-capstone2-ids.json`; manifest nay tách theo profile
(`demo-capstone2-ids.sample.json` / `.demo.json`) nên phải truyền `MANIFEST=...` khi chạy.

---

## 7. Những chỗ script lệch tài liệu gốc (có chủ đích, đã kiểm chứng)

| Tài liệu | Thực tế trong repo | Vì sao |
|---|---|---|
| Loại vật tư `SEMI_FINISHED` | `ItemType.WIP` | Enum của backend không có `SEMI_FINISHED`; `WIP` là đúng ô đó |
| BOM có mã `D26-BOM-*` | `BomHeader` **không có** cột `code` | Mã của tài liệu được ghi vào `description` để vẫn tra ngược được |
| Ca "08:00-12:00 **và** 13:00-17:00" | Một ca 08:00–17:00 + giờ nghỉ 12:00–13:00 | Mô hình `Shift` là **một** khoảng làm việc kèm danh sách giờ nghỉ; tổng vẫn đúng 8 giờ công/ngày |
| "Dùng Company `ACTIVE` hiện hành" | Script **tạo** công ty `D26-OMNIPLANT` | Yêu cầu là xoá sạch dữ liệu trước, nên sau khi xoá không còn công ty nào để dùng lại |
| Phạm vi truy cập gồm cả kho | Phạm vi `PLANT` chỉ chứa tài nguyên `PLANT` | `AccessControlService.isResourceTypeAllowed` từ chối `WAREHOUSE` trong phạm vi `PLANT` (bất biến RBAC siết ở `V63`). Không cần thêm: `hasWarehouseAccess` lùi về nhà máy cha nên một tài nguyên `PLANT` phủ trọn 5 kho |
| "Không seed Planning Run trước" | Seed **3 lượt chạy MRP** | Yêu cầu 2026-08-23 của người dùng: cần dữ liệu để chụp hình giao diện làm slide. Vẫn để lại 32 đề xuất `DRAFT` và 2 đơn hàng `CONFIRMED` chưa hoạch định để diễn trực tiếp |
| Không có Purchasing | Có 3 yêu cầu mua + 5 đơn mua | Cùng lý do trên — màn hình mua hàng trống thì không chụp được |
| Một công ty demo duy nhất | **Hai** công ty: `D26-` (mẫu) và `DEMO-` (trình diễn) | Yêu cầu 2026-08-25 của người dùng: cần vừa có bộ đã chạy hết luồng để chụp hình, vừa có bộ sạch chưa đụng tới để diễn trực tiếp. Không gộp được từ file backup vì dump là toàn-cơ-sở-dữ-liệu và trùng dãy mã — xem ghi chú đầu tài liệu |
| Hạn dùng của lô | Đặt bằng `set-lot-expiry.sh` (SQL), không qua API | REST API **không có** đường đặt `InventoryLot.expiresAt` — không request DTO nào mang field đó. Để trống thì cột "Expiry" trên màn hình Lô hàng toàn dấu "—". Script tách riêng để `seed.sh` giữ nguyên bất biến "không một câu SQL nào" |
| Đường dẫn kiểu `/api/v1/...` | **Hỗn hợp** | Phần lớn ở `/api/v1/...`, nhưng `auth`/`users`/`access`/`sales-orders` đặt `/v1` **sau** tiền tố tài nguyên (`/api/auth/v1/login`, `/api/sales-orders/v1`). `seed.sh` đối chiếu ~40 đường dẫn bắt buộc với OpenAPI runtime và **dừng ngay** nếu lệch — đoán sai đường dẫn ở API này trả `401 AUTHENTICATION_REQUIRED` chứ không phải 404, tức trông hệt như sai mật khẩu |

---

## 8. Những gì bộ dữ liệu này CỐ Ý không có

Đây là bộ dữ liệu trình diễn cho khách hàng, không phải bộ dữ liệu kiểm thử:

- Không có lô `REJECTED` hay hết hạn. (Có **một** lô `HOLD` — đó là trạng thái nghiệp vụ bình
  thường của hàng chờ QC, không phải bản ghi lỗi.)
- Không có bản ghi `INACTIVE` nào (kho, vật tư, tổ sản xuất, đơn vị tính, nhà cung cấp).
- Không có lệnh sản xuất `BLOCKED`, không có đề xuất bị chặn, không có `MISSING_BOM` /
  `MISSING_ROUTING`.
- Không có phiếu nhập kho bị từ chối, không có yêu cầu mua bị từ chối.
- Không dùng Serial Tracking.

Cần bộ dữ liệu **phủ mọi trạng thái, kể cả trạng thái lỗi** để kiểm thử thì dùng seeder cũ
`scripts/demo/seed-demo.sh` (chủ đề `VIETBIKE`) — **trên môi trường khác**, đừng trộn hai bộ.

---

## 9. Trường thời gian: cái nào có giá trị, cái nào cố ý trống

Bản 2026-08-25 lấp **mọi** cột ngày/giờ mà API cho ghi, để màn hình không đầy dấu "—":

| Trường | Giá trị | Đặt ở đâu |
|---|---|---|
| Phân quyền `expiresAt` | hôm nay + **365 ngày** | `seed.sh`, tham số `ASSIGNMENT_EXPIRY_DAYS` |
| Lệnh sản xuất `plannedStartAt` / `plannedEndAt` | thứ Hai kế tiếp 08:00 giờ VN → +**5 ngày** | `seed.sh`, tham số `WO_PLANNED_DURATION_DAYS` |
| Phiếu ghi sản lượng `actualStartedAt` / `actualEndedAt` | 6 giờ trước → 1 giờ trước | `seed.sh` |
| Phiếu ghi sản lượng: công đoạn / tổ sản xuất | công đoạn **cuối** của ảnh chụp quy trình | `seed.sh`, `last_operation_id` |
| Lô `expiresAt` | ngày nhập + **730 ngày** | `set-lot-expiry.sh`, tham số `LOT_SHELF_LIFE_DAYS` |
| Đơn bán/mua `dueDate` / `orderDate` / `expectedDate` | ngày seed + 14…60 ngày tuỳ chứng từ | `catalogue.sh` |

Những cột **vẫn trống, và đúng là phải trống** — đừng "sửa" chúng:

| Trường | Vì sao trống |
|---|---|
| `cancelledAt` / `cancelReason` | chỉ lệnh sản xuất đã huỷ mới có; bộ dữ liệu có đúng một lệnh như vậy và nó **có** giá trị |
| `blockedAt` / `blockReason` | không lệnh sản xuất nào `BLOCKED` — đó là chủ đích của bộ dữ liệu sạch |
| `rejectedAt` / `rejectReason` (phiếu nhập kho) | không phiếu nào bị từ chối |
| `decidedAt` / `decidedBy` (phiếu xuất vật tư) | chỉ phiếu xuất **vượt định mức** mới cần duyệt; mọi phiếu ở đây đều đúng định mức |
| `serialId` / `serialNumber` | Capstone 2 không dùng serial tracking |
| `planningRunId` / `planningProposalId` | chỉ lệnh sản xuất sinh **từ MRP** mới có (`D26-WO-1004`, `D26-WO-1005`); lệnh tạo tay thì không, và đó là dữ liệu đúng chứ không phải lỗ hổng |
| `sourceRequisitionId` (đơn mua) | chỉ `D26-PO-0005` chuyển ra từ yêu cầu mua mới có; bốn đơn còn lại tạo thẳng |
| Assignment `expiresAt` của `admin` | do migration `V2` tạo, không do script; tài khoản quản trị hết hạn giữa chừng sẽ khoá cả hệ thống |

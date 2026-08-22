# Bộ dữ liệu demo — Công ty Cổ phần Xe đạp Việt (`VIETBIKE`)

> Tài liệu tra cứu khi thuyết trình. Sinh bởi `scripts/demo/seed-demo.sh`; mọi UUID nằm ở
> `demo-ids.json` (sinh ra lúc chạy, không commit).
>
> Toàn bộ dữ liệu được tạo **qua REST API thật**, không có câu `INSERT` nào. Mã chứng từ sinh ở
> `@PrePersist`, ảnh chụp BOM/quy trình trên lệnh sản xuất, projection `stock_balances`, `@Version`
> và nhật ký kiểm toán vì thế đều là thật — không phải dữ liệu dựng tay trông giống thật.

## 1. Dựng lại từ đầu

```bash
# 1. Dừng backend đang chạy (Ctrl-C)
# 2. Xoá sạch dữ liệu
docker compose down -v && docker compose up -d
# 3. Khởi động lại backend, đợi Flyway chạy xong V1..V58
mvn -o spring-boot:run
# 4. Seed — BẮT BUỘC chạy bằng Git Bash, KHÔNG dùng PowerShell
./scripts/demo/seed-demo.sh
```

⚠️ **PowerShell 5.1 giải mã UTF-8 sai** và sẽ ghi vào cơ sở dữ liệu những chuỗi hỏng kiểu `BÃ ...`
— đúng lỗi bộ fixture cũ của FE đã mắc. Script tự kiểm tra điều này ngay sau khi tạo công ty đầu
tiên và dừng lại nếu phát hiện, nhưng cách chắc chắn nhất vẫn là chạy đúng shell.

Script dùng **mã cố định** nên **không chạy lại được** trên cơ sở dữ liệu đã có dữ liệu demo; nó
kiểm tra và báo lỗi kèm hướng dẫn thay vì chết giữa chừng. Hỏng ở bước nào thì xoá sạch rồi chạy
lại — các ID đã kịp tạo được ghi ở `demo-ids.partial.json` để còn tra cứu.

## 2. Tài khoản

| Tài khoản | Mật khẩu | Vai trò | Phạm vi |
|---|---|---|---|
| admin bootstrap | Giá trị `APP_BOOTSTRAP_ADMIN_PASSWORD` dùng một lần | ADMIN | toàn hệ thống |
| `quanly.hanoi` | `Demo@1234` | MANAGER | chỉ nhà máy `HANOI` |
| `congnhan.hanoi` | `Demo@1234` | OPERATOR | chỉ nhà máy `HANOI` |
| `kehoach.vietbike` | `Demo@1234` | `KEHOACH` *(vai trò tuỳ chỉnh)* | toàn công ty |
| `quanly.saigon` | `Demo@1234` | MANAGER | chỉ nhà máy `SAIGON` |
| `kiemtoan.tamthoi` | `Demo@1234` | OPERATOR | kho thành phẩm HN, **hết hạn sau 7 ngày** |

🔴 **Đọc trước khi demo, tránh vấp trên sân khấu:** tài khoản gán phạm vi `PLANT`/`COMPANY` vẫn nhận
**403** ở `GET /uoms` và `GET /companies`. Không phải lỗi bộ dữ liệu — `PermissionGuard.hasPermission`
chỉ đọc phân công có phạm vi `GLOBAL`, đây là đặc điểm sẵn có của toàn repo (xem
`module/uom/CLAUDE.md`). **Các màn hình master data toàn cục hãy đăng nhập `admin`.**

## 3. Cây sản phẩm

```
XE-PHO-26  Xe đạp phố 26 inch            (thành phẩm, quản lý theo LÔ)
├── KHUNG-PHO  Khung nhôm xe phố          ×1
│   ├── ONG-NHOM   Ống nhôm 6061          ×3.2   hao hụt 8%
│   ├── SON-XANH   Sơn tĩnh điện xanh     ×0.25
│   └── KHI-HAN    Khí hàn Argon          ×0.4
├── BANH-26    Bánh xe 26 inch            ×2
│   ├── VANH-26    Vành hợp kim           ×2
│   ├── NAN-HOA    Nan hoa thép           ×64    hao hụt 5%
│   ├── MOAYO      Moay-ơ                 ×2
│   └── LOP-26     Lốp xe                 ×2
├── BO-TRUYEN-7  Bộ truyền động 7 tốc độ  ×1   ← CỐ Ý KHÔNG CÓ BOM
├── YEN-XE / TAY-LAI                      ×1
├── PHANH-V                               ×2
└── OC-VIT-M6                             ×24  hao hụt 2%
```

Ba sản phẩm còn lại, mỗi cái minh hoạ một nhánh khác nhau:

| Mã | Vai trò trong demo |
|---|---|
| `XE-DIA-275` | **Không** quản lý theo lô — QC ghi phán quyết trên chính phiếu nhập |
| `XE-TRE-EM` | Có BOM nhưng **không có quy trình công nghệ** ⇒ đề xuất MRP luôn `BLOCKED` |
| `XE-DIEN` | Quản lý theo **số sê-ri** — mỗi phiếu nhập đúng 1 đơn vị |

**Hai "khoảng trống" là cố ý, đừng lấp:**
- `BO-TRUYEN-7` không có BOM ⇒ đề xuất `BLOCKED` vì `MISSING_BOM`, ở **cấp 1** của cây nổ.
- `BANH-20` không được nhập kho ở **bất kỳ kho nào** ⇒ là thứ duy nhất khiến `WO-2603` vào `BLOCKED`.

## 4. Ma trận trạng thái — mở màn hình nào để thấy gì

### Lệnh sản xuất (nhà máy `HANOI`)

| Mã | Trạng thái | Điểm đáng nói khi demo |
|---|---|---|
| `WO-2601` | `DRAFT` | vừa tạo, chưa làm gì |
| `WO-2602` | `PLANNED` | đã lên lịch, chưa giữ vật tư |
| `WO-2603` | **`BLOCKED`** | thiếu `BANH-20`; bấm Ban hành sẽ ra 409 và WO bị khoá |
| `WO-2604` `WO-2609` | `RELEASED` | **cùng ngày trên tổ `TO-HAN`** ⇒ bảng năng lực báo quá tải |
| `WO-2605` | `IN_PROGRESS` | báo 10/20; mang **cả 6 phiếu nhập** của ma trận bên dưới |
| `WO-2606` | `COMPLETED` | báo đủ sản lượng ⇒ tự hoàn thành |
| `WO-2607` | `CLOSED` | đã đóng, trả lại phần vật tư còn giữ |
| `WO-2608` | `CANCELLED` | huỷ kèm lý do, chưa xuất/chưa báo gì |
| `WO-2610` | `IN_PROGRESS` | sinh từ MRP cho `SO-1004` ⇒ giao **một phần** |
| `WO-2611` | `COMPLETED` | sinh từ MRP cho `SO-1005` ⇒ giao **đủ** |
| `WO-2612` | `RELEASED` | sinh từ MRP cho `SO-1006`, dừng tại đây |
| `WO-2620` | `IN_PROGRESS` | sản phẩm theo số sê-ri |
| `WO-2650` | `PLANNED` | ở **`SAIGON`** — dùng để chứng minh cô lập theo nhà máy |

### Phiếu nhập thành phẩm — tất cả trên `WO-2605`

| Lô | Trạng thái phiếu | Trạng thái lô |
|---|---|---|
| `LO-BANH275-NHAP` | `DRAFT` | *(chưa động vào tồn kho)* |
| `LO-BANH275-CHO` | `PENDING_APPROVAL` | *(chưa động vào tồn kho)* |
| `LO-BANH275-TUCHOI` | `REJECTED` | *(chưa động vào tồn kho)* |
| `LO-BANH275-GIU` | `APPROVED`, **chưa QC** | **`HOLD`** |
| `LO-BANH275-DAT` | `APPROVED` + QC đạt | `AVAILABLE` |
| `LO-BANH275-LOI` | `APPROVED` + QC loại | `REJECTED` |

Số sê-ri trên `WO-2620`: `SN-XEDIEN-0001` (QC đạt) và `SN-XEDIEN-0002` (QC loại — bị rút khỏi tồn
kho bằng bút toán `ADJUST_OUT`).

Lô nguyên vật liệu đổi trạng thái thủ công: `LO-SON-GIU` → `HOLD`, `LO-THEP-LOI` → `REJECTED`.

> 💡 **Điểm nhấn nên nói:** mở màn hình lô, chỉ vào dòng `HOLD`/`REJECTED` — `onHand` vẫn còn hàng
> nhưng `available` bằng **0**. Hàng tồn tại vật lý nhưng không dùng được. Trước bản sửa 2026-08-14
> hai con số này mâu thuẫn nhau giữa các màn hình.

### Đơn bán hàng

| Mã | Khách hàng | Trạng thái |
|---|---|---|
| `SO-1001` | Cửa hàng Xe đạp Thống Nhất | `DRAFT` |
| `SO-1002` | Siêu thị Thể thao Động Lực | `CONFIRMED` — cố ý **chưa** đưa vào lượt MRP nào |
| `SO-1003` | Đại lý Xe đạp Hoàng Anh | `CANCELLED` (nhu cầu đi kèm cũng bị huỷ) |
| `SO-1004` | Cửa hàng Xe đạp Thống Nhất | **`PARTIALLY_FULFILLED`** — đã giao 6/10 |
| `SO-1005` | Xe đạp Sài Gòn | **`FULFILLED`** — 4/4 |
| `SO-1006` | Siêu thị Thể thao Động Lực | `IN_PRODUCTION` |
| `SO-1007` | Trường Tiểu học Kim Liên | `CONFIRMED`, đề xuất bị `BLOCKED` |

### Mua hàng

`PR-2001` `DRAFT` · `PR-2002` `APPROVED` (duyệt 240/300) · `PR-2003` `CONVERTED` (sinh từ MRP) ·
`PR-2004` `REJECTED` · `PR-2005` `CANCELLED`
`PO-3001` `DRAFT` · `PO-3002` `SENT` · `PO-3003` `PARTIALLY_RECEIVED` · `PO-3004` `RECEIVED` ·
`PO-3005` `CANCELLED`
`GR-4001` `POSTED` · `GR-4002` `CANCELLED` · `GR-4003` `POSTED`

### Hoạch định (MRP)

Hai lượt chạy, mã cụ thể xem `demo-ids.json` → `planningRuns`:

| Lượt | Phạm vi | Dùng để nói gì |
|---|---|---|
| **A** | kho thành phẩm, chọn đúng 4 dòng nhu cầu | Đề xuất phủ đủ `READY` / `WARNING` / `BLOCKED` và cả `MAKE` / `BUY` |
| **B** | toàn nhà máy, quét theo horizon | Cùng nhu cầu nhưng netting rộng hơn ⇒ số liệu tóm tắt khác hẳn |

Trạng thái đề xuất có đủ `DRAFT` / `APPROVED` / `REJECTED` / `CONVERTED`.

> 💡 `READY` hay `WARNING` **do phạm vi kho của lượt chạy quyết định**, không phải do vật tư:
> `XE-PHO-26` có tham số tồn kho tại `KHO-TP` nên ra `READY`; `XE-DIA-275` không có nên ra `WARNING`
> (`SYSTEM_FALLBACK_USED`).

### Bảng năng lực sản xuất

Mở `GET /plants/{HANOI}/capacity-board?from=…&to=…` với khoảng bao trùm **thứ Hai kế tiếp**:

- **`TO-HAN` quá tải ~140%** vào thứ Hai — `WO-2604` và `WO-2609` cùng đòi tổ hàn một ngày
  (1240 phút tải / 885 phút năng lực). Đây là điểm nhấn chính.
- **`TO-KCS` báo năng lực `null`** — tổ này cố ý không gắn lịch làm việc; "không biết" khác với
  "không có năng lực", nên không bao giờ bị đánh dấu quá tải.

### Cảnh báo tồn kho

Bảng điều khiển tại `HANOI` cho **7 `OK` / 4 `LOW_STOCK` / 5 `REORDER_NEEDED`**. Ngưỡng được tính
**từ tồn khả dụng thật tại thời điểm cuối lượt seed**, không phải số cứng — nên các nhóm luôn đúng
kể cả khi lượng tiêu thụ thay đổi.

### Giá thành và chênh lệch

`GET /work-orders/{WO-2610}/variance` cho số tiền thật: chuẩn ~116,4 triệu, thực tế ~17,3 triệu,
chênh lệch ~-99,1 triệu (mới xuất một phần vật tư), kèm chênh lệch thời gian 475 phút kế hoạch so
với 240 phút thực tế.

## 5. Kịch bản gợi ý (khoảng 8 phút)

1. **Danh mục** — mở vật tư, chỉ cây BOM 3 cấp của `XE-PHO-26`, nói về hao hụt 8% ở khâu cắt ống.
2. **Bảng điều khiển tồn kho** — 3 nhóm cảnh báo, di chuyển gần đây có đủ nhãn (một request là đủ).
3. **Đơn hàng → MRP** — mở `SO-1004`, sang lượt chạy A, chỉ một đề xuất `BLOCKED` và giải thích
   vì sao (`XE-TRE-EM` chưa có quy trình công nghệ).
4. **Lệnh sản xuất** — lọc theo trạng thái, dừng ở `WO-2603` `BLOCKED` để nói về cơ chế chặn ban hành.
5. **Bảng năng lực** — chỉ ngày thứ Hai `TO-HAN` đỏ 140%.
6. **QC** — mở `WO-2605`, chỉ 6 phiếu nhập ở 4 trạng thái; mở màn hình lô, chỉ `HOLD` có tồn nhưng
   khả dụng bằng 0.
7. **Kết** — `SO-1004` giao 6/10, `SO-1005` giao đủ; mở nhật ký kiểm toán cho thấy toàn bộ thao tác
   vừa nói đều có vết.

## 6. Những trạng thái KHÔNG seed được (đã kiểm, không phải thiếu sót)

| Trạng thái | Vì sao |
|---|---|
| `PlanningDemand.CONSUMED` | Không dòng code nào trong `src/main` set giá trị này |
| `MrpRun.FAILED` | Chỉ đạt được bằng cách làm `calculate` ném lỗi (vd BOM vòng lặp), sẽ làm bẩn mọi lượt chạy sau |
| `ProductionReceipt.CANCELLED` | Enum có khai báo nhưng không endpoint nào dẫn tới |
| `LotStatus.EXPIRED` | `InventoryLotService.changeStatus` chỉ nhận `AVAILABLE`/`HOLD`/`REJECTED` |
| Nhập mua hàng theo số sê-ri | `GoodsReceiptService` chưa hỗ trợ serial (nợ đã ghi ở `module/purchasing/CLAUDE.md`) |

## 7. Sửa dữ liệu demo

Mọi mã, tên, số lượng nằm ở **một chỗ duy nhất**: `scripts/demo/lib/catalogue.sh`. Các file trong
`scripts/demo/stages/` chỉ chứa trình tự gọi API, không chứa dữ liệu.

Vài ràng buộc đã được ghi ngay trong code, đọc trước khi sửa:

- Bỏ giờ nghỉ khỏi ca làm việc, hoặc nâng `TO-HAN` lên nhiều tổ ⇒ **mất điểm nhấn quá tải**.
- Nhập kho `BANH-20` ⇒ **`WO-2603` không còn `BLOCKED`**.
- Thêm BOM cho `BO-TRUYEN-7` hoặc quy trình cho `XE-TRE-EM` ⇒ **mất đề xuất `BLOCKED`**.
- Hạ sản lượng báo của `WO-2605` xuống dưới 10 ⇒ phiếu nhập cuối sẽ vượt trần và trả 409.
- Ghi đè tham số tồn kho của `XE-PHO-26` tại `KHO-TP` ⇒ đề xuất đổi từ `READY` sang `WARNING`.

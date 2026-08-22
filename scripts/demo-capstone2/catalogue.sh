#!/usr/bin/env bash
# catalogue.sh — mọi mã, tên và số lượng của bộ dữ liệu demo Capstone 2 (namespace D26-).
#
# Nguồn: BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx.
# Đổi tên hoặc số lượng thì sửa ở đây và chỉ ở đây. Không có lời gọi HTTP nào trong file này.
#
# Mã là chữ HOA ASCII (DTO tạo mới ép ^[A-Z0-9._-]+$).
# Tên là tiếng Việt có dấu — file này là UTF-8 KHÔNG BOM và phải giữ nguyên như vậy.

# ── R1: namespace ────────────────────────────────────────────────────────────
NS="D26"

# ── tổ chức (§3.1) ───────────────────────────────────────────────────────────
CO_CODE="D26-OMNIPLANT"
CO_NAME="Công ty Cổ phần Xe đạp OmniPlant"

PLANT_CODE="D26-PLANT"
PLANT_NAME="Nhà máy Demo 2026"
# R6: mọi ngày/giờ của bộ dữ liệu quy về múi giờ này.
PLANT_TZ="Asia/Ho_Chi_Minh"

# code|name|type
WAREHOUSE_ROWS='
D26-RM|Kho nguyên vật liệu|RAW_MATERIAL
D26-WIP|Kho bán thành phẩm|WIP
D26-FG|Kho thành phẩm|FINISHED_GOODS
D26-QC|Kho kiểm soát chất lượng|QUALITY
D26-SCRAP|Kho phế phẩm|SCRAP
'
WH_RM="D26-RM"; WH_WIP="D26-WIP"; WH_FG="D26-FG"; WH_QC="D26-QC"; WH_SCRAP="D26-SCRAP"

# ── đơn vị tính (toàn cục, không thuộc công ty nào) ──────────────────────────
# code|name|description
UOM_ROWS='
EA|Cái|Đơn vị đếm từng chiếc
KG|Kilôgam|Khối lượng
'

# ── danh mục vật tư (§4) ─────────────────────────────────────────────────────
# code|name|type|unit|lotTracked
#
# 🔴 Chỉ THÀNH PHẨM là LOT_TRACKED. Toàn bộ bán thành phẩm và nguyên vật liệu để NON_TRACKED
# để luồng reserve/issue không phụ thuộc vào lô đầu vào (§3). Không dùng serial trong Capstone 2.
# Loại "SEMI_FINISHED" của tài liệu ánh xạ sang ItemType.WIP của backend.
ITEM_ROWS='
D26-FG-BIKE16|Xe đạp trẻ em 16 inch|FINISHED_GOOD|EA|true
D26-WIP-FRAME16|Bộ khung hoàn thiện 16 inch|WIP|EA|false
D26-WIP-DRIVE7|Bộ truyền động 7 tốc độ|WIP|EA|false
D26-RM-WHEEL16|Bánh xe 16 inch|RAW_MATERIAL|EA|false
D26-RM-BRAKE|Bộ phanh|RAW_MATERIAL|EA|false
D26-RM-FRAME|Khung thô 16 inch|RAW_MATERIAL|EA|false
D26-RM-PAINT|Sơn phủ|RAW_MATERIAL|KG|false
D26-RM-CHAINRING|Bộ giò đĩa|RAW_MATERIAL|EA|false
D26-RM-CHAIN|Xích xe|RAW_MATERIAL|EA|false
D26-RM-CASSETTE7|Líp 7 tầng|RAW_MATERIAL|EA|false
'

# ── chính sách kho theo vật tư (§5) ──────────────────────────────────────────
# itemCode|warehouseCode|defaultSupply|defaultOutput|safetyStock|reorderPoint|leadTimeDays
#
# 🔴 Mỗi vật tư có ĐÚNG MỘT dòng cho mỗi nhà máy. MrpWarehouseResolutionService trả
# AMBIGUOUS_WAREHOUSE_POLICY ngay khi một vật tư có hơn một cấu hình ACTIVE trong cùng nhà máy,
# kể cả khi chỉ một trong số đó là default — đó là điều kiện PF-03 canh.
#
# Ngưỡng để 0 có chủ đích: chúng vẫn là giá trị Item-Warehouse thật (settingSource =
# ITEM_WAREHOUSE), nên không dòng nào rơi vào SYSTEM_FALLBACK_USED, mà số học netting vẫn sạch
# (net = gross - available) để giải thích trên màn hình.
IWS_ROWS='
D26-FG-BIKE16|D26-FG|false|true|0|0|5
D26-WIP-FRAME16|D26-WIP|true|true|0|0|2
D26-WIP-DRIVE7|D26-WIP|true|true|0|0|2
D26-RM-WHEEL16|D26-RM|true|false|0|0|3
D26-RM-BRAKE|D26-RM|true|false|0|0|3
D26-RM-FRAME|D26-RM|true|false|0|0|3
D26-RM-PAINT|D26-RM|true|false|0|0|3
D26-RM-CHAINRING|D26-RM|true|false|0|0|3
D26-RM-CHAIN|D26-RM|true|false|0|0|3
D26-RM-CASSETTE7|D26-RM|true|false|0|0|3
'

# ── ca làm việc và lịch (§6) ─────────────────────────────────────────────────
# Tài liệu ghi "08:00-12:00 và 13:00-17:00". Mô hình Shift của backend là MỘT khoảng làm việc
# kèm danh sách giờ nghỉ, nên hai khoảng đó được biểu diễn thành một ca 08:00-17:00 nghỉ trưa
# 12:00-13:00 — tổng 8 giờ công/ngày, đúng bằng tài liệu.
SHIFT_CODE="D26-SHIFT-DAY"
SHIFT_NAME="Ca ngày (08:00-17:00, nghỉ trưa 12:00-13:00)"
SHIFT_START="08:00"
SHIFT_END="17:00"
SHIFT_BREAK_START="12:00"
SHIFT_BREAK_END="13:00"

CAL_CODE="D26-CAL-DAY"
CAL_NAME="Lịch làm việc Thứ Hai - Thứ Bảy"
CAL_WEEKDAYS="MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY"
# §6: hiệu lực trước ngày demo tối thiểu 30 ngày. Dùng 60 cho dư biên.
CAL_EFFECTIVE_DAYS_AGO=60

# ── tổ sản xuất (§6) ─────────────────────────────────────────────────────────
# code|name|capacityUnitType|capacityUnits
WC_ROWS='
D26-WC-FRAME|Hoàn thiện khung|LINE|1
D26-WC-DRIVE|Lắp bộ truyền động|LINE|1
D26-WC-ASSEMBLY|Lắp ráp tổng|LINE|1
D26-WC-FINAL|Kiểm tra cuối|LABOR_TEAM|1
'

# ── quy trình công nghệ (§6) ─────────────────────────────────────────────────
# code|version|outputItem|op1;op2;...   với op = sequence:tên:workCenter:setupMinutes:runMinutesPerUnit
ROUTING_ROWS='
D26-RT-FRAME16|A|D26-WIP-FRAME16|10:Chuẩn bị khung:D26-WC-FRAME:15:6;20:Sơn hoàn thiện:D26-WC-FRAME:20:8
D26-RT-DRIVE7|A|D26-WIP-DRIVE7|10:Lắp bộ truyền động:D26-WC-DRIVE:10:7
D26-RT-BIKE16|A|D26-FG-BIKE16|10:Lắp ráp tổng:D26-WC-ASSEMBLY:20:12;20:Kiểm tra cuối:D26-WC-FINAL:10:5
'

# ── định mức vật tư (§7) ─────────────────────────────────────────────────────
# parentItem|revision|docCode|component:qtyPer:scrapRate;...
#
# 🔴 Thứ tự trong danh sách này là thứ tự kích hoạt: BOM con trước, BOM cha sau (§7, §10 bước 8).
# BomHeader không có cột "code" — mã của tài liệu (D26-BOM-*) đi vào description để vẫn tra được.
# Mọi outputQuantity = 1 và scrapRate = 0 để dữ liệu trình diễn dễ giải thích.
BOM_ROWS='
D26-WIP-FRAME16|A|D26-BOM-FRAME16|D26-RM-FRAME:1:0;D26-RM-PAINT:0.15:0
D26-WIP-DRIVE7|A|D26-BOM-DRIVE7|D26-RM-CHAINRING:1:0;D26-RM-CHAIN:1:0;D26-RM-CASSETTE7:1:0
D26-FG-BIKE16|A|D26-BOM-BIKE16|D26-WIP-FRAME16:1:0;D26-WIP-DRIVE7:1:0;D26-RM-WHEEL16:2:0;D26-RM-BRAKE:1:0
'

# ── tồn kho đầu kỳ (§8) ──────────────────────────────────────────────────────
# itemCode|warehouseCode|quantity
#
# 🔴 D26-FG-BIKE16 CỐ Ý không có tồn: nó là thứ làm MRP sinh đúng một đề xuất MAKE.
# Bán thành phẩm được nạp sẵn để demo không phải chạy thêm lệnh sản xuất cấp dưới, và nhờ đó
# mọi dòng nhu cầu cấp 1 trả về COVERED (§9.1) — không có đề xuất mua nào.
# Không seed lô HOLD/REJECTED/hết hạn vào namespace này (§8, §13).
STOCK_ROWS='
D26-WIP-FRAME16|D26-WIP|12
D26-WIP-DRIVE7|D26-WIP|12
D26-RM-WHEEL16|D26-RM|24
D26-RM-BRAKE|D26-RM|12
D26-RM-FRAME|D26-RM|20
D26-RM-PAINT|D26-RM|10.000000
D26-RM-CHAINRING|D26-RM|20
D26-RM-CHAIN|D26-RM|20
D26-RM-CASSETTE7|D26-RM|20
'

# ── đơn bán hàng ─────────────────────────────────────────────────────────────
# 🔴 KHÔNG seed ở đây nữa (quyết định của user, 2026-08-22). Người vận hành tự tạo Sales Order
# của riêng mình mỗi lần demo — nhờ vậy phần còn lại của bộ dữ liệu (tổ chức, vật tư, BOM/routing,
# tồn kho) seed lại được NHIỀU LẦN mà không dính một đơn hàng cố định. Nếu cần một mẫu tham khảo
# khi tạo tay: thành phẩm D26-FG-BIKE16, số lượng 10, due date = ngày demo + 30.


# ── tài khoản và phạm vi (§3.2) ──────────────────────────────────────────────
SCOPE_CODE="D26-SCOPE-PLANT"
SCOPE_NAME="Toàn bộ Nhà máy Demo 2026"

# username|email|roleCode|mô tả
USER_MANAGER="quanly.demo"
USER_MANAGER_EMAIL="quanly.demo@omniplant.vn"
USER_OPERATOR="congnhan.demo"
USER_OPERATOR_EMAIL="congnhan.demo@omniplant.vn"

# Mật khẩu chung của hai tài khoản vận hành. KHÔNG phải mật khẩu quản trị viên —
# tài khoản admin do quy trình bootstrap của backend cấp, nằm ngoài script này (§14).
DEMO_PASSWORD="${DEMO_PASSWORD:-OmniPlant@2026}"

# ── lô thành phẩm dùng khi trình diễn (§12 bước 6) ───────────────────────────
# Không seed trước — người trình bày nhập tay khi tạo phiếu nhập kho thành phẩm.
DEMO_OUTPUT_LOT="D26-BIKE16-B001"

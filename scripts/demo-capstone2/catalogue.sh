#!/usr/bin/env bash
# catalogue.sh — mọi mã, tên và số lượng của bộ dữ liệu demo Capstone 2 (namespace D26-).
#
# Nguồn gốc: BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx.
# Bản 2026-08-23 mở rộng bộ dữ liệu để MỌI màn hình đều có dữ liệu chụp hình làm slide:
# 3 dòng sản phẩm, 40 vật tư, 9 định mức, 9 quy trình, 8 tổ sản xuất, 2 ca, nhà cung cấp,
# giá thành chuẩn, chứng từ mua hàng, đơn bán hàng và lệnh sản xuất ở nhiều trạng thái.
#
# Đổi tên hoặc số lượng thì sửa ở đây và chỉ ở đây. Không có lời gọi HTTP nào trong file này.
#
# Mã là chữ HOA ASCII (DTO tạo mới ép ^[A-Z0-9._-]+$). Tên hiển thị là tiếng Anh.

# ── R1: namespace ────────────────────────────────────────────────────────────
NS="D26"

# ── tổ chức (§3.1) ───────────────────────────────────────────────────────────
CO_CODE="D26-OMNIPLANT"
CO_NAME="OmniPlant Bicycle Joint Stock Company"

PLANT_CODE="D26-PLANT"
PLANT_NAME="Demo Plant 2026"
# R6: mọi ngày/giờ của bộ dữ liệu quy về múi giờ này.
PLANT_TZ="Asia/Ho_Chi_Minh"

# code|name|type
WAREHOUSE_ROWS='
D26-RM|Raw Material Warehouse|RAW_MATERIAL
D26-WIP|WIP Warehouse|WIP
D26-FG|Finished Goods Warehouse|FINISHED_GOODS
D26-QC|Quality Control Warehouse|QUALITY
D26-SCRAP|Scrap Warehouse|SCRAP
'
WH_RM="D26-RM"; WH_WIP="D26-WIP"; WH_FG="D26-FG"; WH_QC="D26-QC"; WH_SCRAP="D26-SCRAP"

# ── đơn vị tính (toàn cục, không thuộc công ty nào) ──────────────────────────
# code|name|description
UOM_ROWS='
EA|Each|Unit for counting individual pieces
KG|Kilogram|Weight
SET|Set|A group of parts issued together
PR|Pair|Two matching pieces
M|Meter|Length
'

# ── danh mục vật tư (§4) ─────────────────────────────────────────────────────
# code|name|type|unit|lotTracked
#
# 🔴 Chỉ THÀNH PHẨM là LOT_TRACKED. Toàn bộ bán thành phẩm và nguyên vật liệu để NON_TRACKED
# để luồng reserve/issue không phụ thuộc vào lô đầu vào (§3). Không dùng serial trong Capstone 2.
# Loại "SEMI_FINISHED" của tài liệu ánh xạ sang ItemType.WIP của backend.
ITEM_ROWS='
D26-FG-BIKE16|Kids Bike 16-inch|FINISHED_GOOD|EA|true
D26-FG-BIKE20|Youth Bike 20-inch|FINISHED_GOOD|EA|true
D26-FG-ROAD700|Road Racer 700C|FINISHED_GOOD|EA|true
D26-WIP-FRAME16|Frame Assembly 16-inch|WIP|EA|false
D26-WIP-FRAME20|Frame Assembly 20-inch|WIP|EA|false
D26-WIP-FRAMERD|Frame Assembly Road 700C|WIP|EA|false
D26-WIP-DRIVE7|7-Speed Drivetrain Assembly|WIP|EA|false
D26-WIP-DRIVE18|18-Speed Drivetrain Assembly|WIP|EA|false
D26-WIP-WHEELSET|Wheelset Assembly 700C|WIP|SET|false
D26-RM-FRAME16|Raw Frame 16-inch|RAW_MATERIAL|EA|false
D26-RM-FRAME20|Raw Frame 20-inch|RAW_MATERIAL|EA|false
D26-RM-FRAMERD|Raw Frame Road 700C Alloy|RAW_MATERIAL|EA|false
D26-RM-PAINT|Paint Coating|RAW_MATERIAL|KG|false
D26-RM-PRIMER|Primer Coating|RAW_MATERIAL|KG|false
D26-RM-DECAL|Decal Set|RAW_MATERIAL|SET|false
D26-RM-WHEEL16|Wheel 16-inch|RAW_MATERIAL|EA|false
D26-RM-WHEEL20|Wheel 20-inch|RAW_MATERIAL|EA|false
D26-RM-RIM700|Rim 700C|RAW_MATERIAL|EA|false
D26-RM-HUB700|Hub 700C|RAW_MATERIAL|EA|false
D26-RM-SPOKE|Stainless Spoke|RAW_MATERIAL|EA|false
D26-RM-TIRE700|Tire 700x25C|RAW_MATERIAL|EA|false
D26-RM-TUBE700|Inner Tube 700C|RAW_MATERIAL|EA|false
D26-RM-CHAINRING|Chainring Set|RAW_MATERIAL|SET|false
D26-RM-CHAIN|Bike Chain|RAW_MATERIAL|EA|false
D26-RM-CASSETTE7|7-Speed Cassette|RAW_MATERIAL|EA|false
D26-RM-CASSETTE9|9-Speed Cassette|RAW_MATERIAL|EA|false
D26-RM-DERAILLEUR|Rear Derailleur|RAW_MATERIAL|EA|false
D26-RM-SHIFTER|Shifter Set|RAW_MATERIAL|SET|false
D26-RM-CRANKARM|Crank Arm Set|RAW_MATERIAL|SET|false
D26-RM-PEDAL|Pedal Pair|RAW_MATERIAL|PR|false
D26-RM-BRAKE|V-Brake Set|RAW_MATERIAL|SET|false
D26-RM-BRAKEDISC|Disc Brake Set|RAW_MATERIAL|SET|false
D26-RM-HANDLEBAR|Handlebar|RAW_MATERIAL|EA|false
D26-RM-SADDLE|Saddle|RAW_MATERIAL|EA|false
D26-RM-SEATPOST|Seat Post|RAW_MATERIAL|EA|false
D26-RM-GRIP|Handlebar Grip Pair|RAW_MATERIAL|PR|false
D26-RM-BEARING|Bearing Set|RAW_MATERIAL|SET|false
D26-RM-TRAINWHEEL|Training Wheel Set|RAW_MATERIAL|SET|false
D26-RM-CARTON|Packaging Carton|RAW_MATERIAL|EA|false
D26-RM-TOOLKIT|Assembly Tool Kit|RAW_MATERIAL|SET|false
'

# ── chính sách kho theo vật tư (§5) ──────────────────────────────────────────
# itemCode|warehouseCode|defaultSupply|defaultOutput|safetyStock|reorderPoint|leadTimeDays
#
# 🔴 Mỗi vật tư có ĐÚNG MỘT dòng cho mỗi nhà máy. MrpWarehouseResolutionService trả
# AMBIGUOUS_WAREHOUSE_POLICY ngay khi một vật tư có hơn một cấu hình ACTIVE trong cùng nhà máy,
# kể cả khi chỉ một trong số đó là default — đó là điều kiện PF-03 canh.
#
# Ngưỡng KHÁC 0 ở nguyên vật liệu là có chủ đích: chúng làm Dashboard có đủ ba nhóm cảnh báo
# OK / LOW_STOCK / REORDER_NEEDED để chụp hình. Thành phẩm và bán thành phẩm giữ ngưỡng 0 để số
# học netting của MRP vẫn sạch (net = gross - available) và dễ giải thích trên slide.
#
# Ngưỡng của nguyên vật liệu được chọn theo tồn khả dụng THẬT ở CUỐI lượt seed (sau khi nhập
# mua hàng và xuất cho các lệnh sản xuất bên dưới đã dịch chuyển số liệu), chứ không theo tồn
# đầu kỳ — chọn theo tồn đầu kỳ thì cảnh báo sẽ rơi vào nhóm khác với lúc trình diễn.
IWS_ROWS='
D26-FG-BIKE16|D26-FG|false|true|0|0|5
D26-FG-BIKE20|D26-FG|false|true|0|0|5
D26-FG-ROAD700|D26-FG|false|true|0|0|7
D26-WIP-FRAME16|D26-WIP|true|true|0|0|2
D26-WIP-FRAME20|D26-WIP|true|true|0|0|2
D26-WIP-FRAMERD|D26-WIP|true|true|0|0|2
D26-WIP-DRIVE7|D26-WIP|true|true|0|0|2
D26-WIP-DRIVE18|D26-WIP|true|true|0|0|2
D26-WIP-WHEELSET|D26-WIP|true|true|0|0|2
D26-RM-FRAME16|D26-RM|true|false|20|15|10
D26-RM-FRAME20|D26-RM|true|false|20|15|10
D26-RM-FRAMERD|D26-RM|true|false|25|20|14
D26-RM-PAINT|D26-RM|true|false|30|20|7
D26-RM-PRIMER|D26-RM|true|false|20|15|7
D26-RM-DECAL|D26-RM|true|false|40|30|5
D26-RM-WHEEL16|D26-RM|true|false|60|40|7
D26-RM-WHEEL20|D26-RM|true|false|60|40|7
D26-RM-RIM700|D26-RM|true|false|30|20|10
D26-RM-HUB700|D26-RM|true|false|30|20|10
D26-RM-SPOKE|D26-RM|true|false|600|400|12
D26-RM-TIRE700|D26-RM|true|false|40|30|9
D26-RM-TUBE700|D26-RM|true|false|40|30|9
D26-RM-CHAINRING|D26-RM|true|false|40|25|8
D26-RM-CHAIN|D26-RM|true|false|40|25|8
D26-RM-CASSETTE7|D26-RM|true|false|30|20|8
D26-RM-CASSETTE9|D26-RM|true|false|25|15|8
D26-RM-DERAILLEUR|D26-RM|true|false|50|35|8
D26-RM-SHIFTER|D26-RM|true|false|50|35|8
D26-RM-CRANKARM|D26-RM|true|false|40|25|8
D26-RM-PEDAL|D26-RM|true|false|40|25|6
D26-RM-BRAKE|D26-RM|true|false|60|45|6
D26-RM-BRAKEDISC|D26-RM|true|false|30|25|9
D26-RM-HANDLEBAR|D26-RM|true|false|40|25|6
D26-RM-SADDLE|D26-RM|true|false|40|25|6
D26-RM-SEATPOST|D26-RM|true|false|40|25|6
D26-RM-GRIP|D26-RM|true|false|40|25|4
D26-RM-BEARING|D26-RM|true|false|40|25|6
D26-RM-TRAINWHEEL|D26-RM|true|false|40|30|5
D26-RM-CARTON|D26-RM|true|false|120|80|4
D26-RM-TOOLKIT|D26-RM|true|false|30|20|5
'

# ── ca làm việc và lịch (§6) ─────────────────────────────────────────────────
# Tài liệu ghi "08:00-12:00 và 13:00-17:00". Mô hình Shift của backend là MỘT khoảng làm việc
# kèm danh sách giờ nghỉ, nên hai khoảng đó được biểu diễn thành một ca 08:00-17:00 nghỉ trưa
# 12:00-13:00 — tổng 8 giờ công/ngày, đúng bằng tài liệu.
#
# code|name|start|end|breakStart|breakEnd   (hai cột cuối rỗng = ca không có giờ nghỉ)
SHIFT_ROWS='
D26-SHIFT-DAY|Day Shift 08:00-17:00 (lunch break 12:00-13:00)|08:00|17:00|12:00|13:00
D26-SHIFT-EVE|Evening Shift 17:30-21:30|17:30|21:30||
'
SHIFT_DAY="D26-SHIFT-DAY"; SHIFT_EVE="D26-SHIFT-EVE"

# code|name|shiftCodes (phân tách bằng dấu phẩy)|weekdays
CALENDAR_ROWS='
D26-CAL-DAY|Monday - Saturday Single Shift|D26-SHIFT-DAY|MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY
D26-CAL-2SHIFT|Monday - Friday Two Shifts|D26-SHIFT-DAY,D26-SHIFT-EVE|MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY
'
# §6: hiệu lực trước ngày demo tối thiểu 30 ngày. Dùng 60 cho dư biên.
CAL_EFFECTIVE_DAYS_AGO=60

# ── tổ sản xuất (§6) ─────────────────────────────────────────────────────────
# code|name|capacityUnitType|capacityUnits|calendarCode
WC_ROWS='
D26-WC-FRAME|Frame Preparation|LINE|1|D26-CAL-DAY
D26-WC-PAINT|Paint Line|LINE|2|D26-CAL-2SHIFT
D26-WC-DRIVE|Drivetrain Assembly|LINE|1|D26-CAL-DAY
D26-WC-WHEEL|Wheel Building|LINE|2|D26-CAL-DAY
D26-WC-ASSEMBLY|Final Assembly|LINE|2|D26-CAL-2SHIFT
D26-WC-FINAL|Final Inspection|LABOR_TEAM|2|D26-CAL-DAY
D26-WC-PACK|Packaging|LINE|1|D26-CAL-DAY
D26-WC-QCLAB|Quality Laboratory|LABOR_TEAM|1|D26-CAL-DAY
'

# ── quy trình công nghệ (§6) ─────────────────────────────────────────────────
# code|version|outputItem|op1;op2;...   với op = sequence:tên:workCenter:setupMinutes:runMinutesPerUnit
ROUTING_ROWS='
D26-RT-FRAME16|A|D26-WIP-FRAME16|10:Frame Preparation:D26-WC-FRAME:15:6;20:Primer and Paint:D26-WC-PAINT:20:8
D26-RT-FRAME20|A|D26-WIP-FRAME20|10:Frame Preparation:D26-WC-FRAME:15:7;20:Primer and Paint:D26-WC-PAINT:20:9
D26-RT-FRAMERD|A|D26-WIP-FRAMERD|10:Alloy Frame Preparation:D26-WC-FRAME:20:9;20:Primer and Paint:D26-WC-PAINT:25:11
D26-RT-DRIVE7|A|D26-WIP-DRIVE7|10:Drivetrain Assembly:D26-WC-DRIVE:10:7
D26-RT-DRIVE18|A|D26-WIP-DRIVE18|10:Drivetrain Assembly:D26-WC-DRIVE:15:11;20:Shifting Adjustment:D26-WC-FINAL:5:4
D26-RT-WHEELSET|A|D26-WIP-WHEELSET|10:Spoke Lacing:D26-WC-WHEEL:10:14;20:Truing and Tensioning:D26-WC-WHEEL:5:9
D26-RT-BIKE16|A|D26-FG-BIKE16|10:Final Assembly:D26-WC-ASSEMBLY:20:12;20:Final Inspection:D26-WC-FINAL:10:5;30:Packaging:D26-WC-PACK:5:3
D26-RT-BIKE20|A|D26-FG-BIKE20|10:Final Assembly:D26-WC-ASSEMBLY:20:13;20:Final Inspection:D26-WC-FINAL:10:5;30:Packaging:D26-WC-PACK:5:3
D26-RT-ROAD700|A|D26-FG-ROAD700|10:Final Assembly:D26-WC-ASSEMBLY:30:22;20:Laboratory Check:D26-WC-QCLAB:15:8;30:Final Inspection:D26-WC-FINAL:10:6;40:Packaging:D26-WC-PACK:5:4
'

# ── định mức vật tư (§7) ─────────────────────────────────────────────────────
# parentItem|revision|docCode|component:qtyPer:scrapRate;...
#
# 🔴 Thứ tự trong danh sách này là thứ tự kích hoạt: BOM con trước, BOM cha sau (§7, §10 bước 8).
# BomHeader không có cột "code" — mã của tài liệu (D26-BOM-*) đi vào description để vẫn tra được.
BOM_ROWS='
D26-WIP-FRAME16|A|D26-BOM-FRAME16|D26-RM-FRAME16:1:0;D26-RM-PRIMER:0.08:0;D26-RM-PAINT:0.15:0;D26-RM-DECAL:1:0
D26-WIP-FRAME20|A|D26-BOM-FRAME20|D26-RM-FRAME20:1:0;D26-RM-PRIMER:0.1:0;D26-RM-PAINT:0.2:0;D26-RM-DECAL:1:0
D26-WIP-FRAMERD|A|D26-BOM-FRAMERD|D26-RM-FRAMERD:1:0;D26-RM-PRIMER:0.06:0;D26-RM-PAINT:0.12:0;D26-RM-DECAL:1:0
D26-WIP-DRIVE7|A|D26-BOM-DRIVE7|D26-RM-CHAINRING:1:0;D26-RM-CHAIN:1:0;D26-RM-CASSETTE7:1:0;D26-RM-CRANKARM:1:0;D26-RM-PEDAL:1:0
D26-WIP-DRIVE18|A|D26-BOM-DRIVE18|D26-RM-CHAINRING:1:0;D26-RM-CHAIN:1:0;D26-RM-CASSETTE9:1:0;D26-RM-DERAILLEUR:1:0;D26-RM-SHIFTER:1:0;D26-RM-CRANKARM:1:0;D26-RM-PEDAL:1:0
D26-WIP-WHEELSET|A|D26-BOM-WHEELSET|D26-RM-RIM700:2:0;D26-RM-HUB700:2:0;D26-RM-SPOKE:64:0;D26-RM-TIRE700:2:0;D26-RM-TUBE700:2:0;D26-RM-BEARING:2:0
D26-FG-BIKE16|A|D26-BOM-BIKE16|D26-WIP-FRAME16:1:0;D26-WIP-DRIVE7:1:0;D26-RM-WHEEL16:2:0;D26-RM-BRAKE:1:0;D26-RM-HANDLEBAR:1:0;D26-RM-SADDLE:1:0;D26-RM-SEATPOST:1:0;D26-RM-GRIP:1:0;D26-RM-TRAINWHEEL:1:0;D26-RM-CARTON:1:0
D26-FG-BIKE20|A|D26-BOM-BIKE20|D26-WIP-FRAME20:1:0;D26-WIP-DRIVE7:1:0;D26-RM-WHEEL20:2:0;D26-RM-BRAKE:1:0;D26-RM-HANDLEBAR:1:0;D26-RM-SADDLE:1:0;D26-RM-SEATPOST:1:0;D26-RM-GRIP:1:0;D26-RM-CARTON:1:0
D26-FG-ROAD700|A|D26-BOM-ROAD700|D26-WIP-FRAMERD:1:0;D26-WIP-DRIVE18:1:0;D26-WIP-WHEELSET:1:0;D26-RM-BRAKEDISC:1:0;D26-RM-HANDLEBAR:1:0;D26-RM-SADDLE:1:0;D26-RM-SEATPOST:1:0;D26-RM-GRIP:1:0;D26-RM-CARTON:1:0;D26-RM-TOOLKIT:1:0
'

# ── giá thành chuẩn (P3) ─────────────────────────────────────────────────────
# itemCode|materialCost|laborCost|overheadCost   (đơn vị: nghìn đồng, số tròn cho dễ đọc)
#
# Nguyên vật liệu mang giá mua; bán thành phẩm và thành phẩm chỉ mang nhân công + sản xuất
# chung, phần vật tư do CostingService nổ BOM cộng lên (roll-up nhiều cấp).
COST_ROWS='
D26-RM-FRAME16|420|0|0
D26-RM-FRAME20|480|0|0
D26-RM-FRAMERD|1650|0|0
D26-RM-PAINT|180|0|0
D26-RM-PRIMER|140|0|0
D26-RM-DECAL|25|0|0
D26-RM-WHEEL16|260|0|0
D26-RM-WHEEL20|290|0|0
D26-RM-RIM700|340|0|0
D26-RM-HUB700|420|0|0
D26-RM-SPOKE|6|0|0
D26-RM-TIRE700|310|0|0
D26-RM-TUBE700|75|0|0
D26-RM-CHAINRING|350|0|0
D26-RM-CHAIN|180|0|0
D26-RM-CASSETTE7|240|0|0
D26-RM-CASSETTE9|520|0|0
D26-RM-DERAILLEUR|610|0|0
D26-RM-SHIFTER|540|0|0
D26-RM-CRANKARM|380|0|0
D26-RM-PEDAL|120|0|0
D26-RM-BRAKE|210|0|0
D26-RM-BRAKEDISC|760|0|0
D26-RM-HANDLEBAR|150|0|0
D26-RM-SADDLE|180|0|0
D26-RM-SEATPOST|110|0|0
D26-RM-GRIP|45|0|0
D26-RM-BEARING|90|0|0
D26-RM-TRAINWHEEL|130|0|0
D26-RM-CARTON|35|0|0
D26-RM-TOOLKIT|95|0|0
D26-WIP-FRAME16|0|60|40
D26-WIP-FRAME20|0|65|45
D26-WIP-FRAMERD|0|95|65
D26-WIP-DRIVE7|0|55|35
D26-WIP-DRIVE18|0|85|55
D26-WIP-WHEELSET|0|140|90
D26-FG-BIKE16|0|120|80
D26-FG-BIKE20|0|130|85
D26-FG-ROAD700|0|260|170
'

# ── tồn kho đầu kỳ (§8) ──────────────────────────────────────────────────────
# itemCode|warehouseCode|quantity|lotCode   (lotCode rỗng = vật tư không theo lô)
#
# 🔴 D26-FG-BIKE16 CỐ Ý không có tồn: nó là thứ làm MRP sinh ra đề xuất MAKE cho câu chuyện
# trình diễn chính. Hai thành phẩm còn lại có ít tồn để màn hình kho thành phẩm không trống.
# Nguyên vật liệu để dư rộng rãi cho toàn bộ lệnh sản xuất bên dưới giữ chỗ và xuất được 100%.
STOCK_ROWS='
D26-WIP-FRAME16|D26-WIP|20|
D26-WIP-FRAME20|D26-WIP|20|
D26-WIP-FRAMERD|D26-WIP|10|
D26-WIP-DRIVE7|D26-WIP|30|
D26-WIP-DRIVE18|D26-WIP|12|
D26-WIP-WHEELSET|D26-WIP|10|
D26-FG-BIKE20|D26-FG|6|D26-BIKE20-B001
D26-FG-ROAD700|D26-FG|2|D26-ROAD700-B001
D26-RM-FRAME16|D26-RM|60|
D26-RM-FRAME20|D26-RM|50|
D26-RM-FRAMERD|D26-RM|18|
D26-RM-PAINT|D26-RM|24.500000|
D26-RM-PRIMER|D26-RM|12.000000|
D26-RM-DECAL|D26-RM|150|
D26-RM-WHEEL16|D26-RM|140|
D26-RM-WHEEL20|D26-RM|120|
D26-RM-RIM700|D26-RM|60|
D26-RM-HUB700|D26-RM|60|
D26-RM-SPOKE|D26-RM|2400|
D26-RM-TIRE700|D26-RM|26|
D26-RM-TUBE700|D26-RM|70|
D26-RM-CHAINRING|D26-RM|90|
D26-RM-CHAIN|D26-RM|90|
D26-RM-CASSETTE7|D26-RM|70|
D26-RM-CASSETTE9|D26-RM|18|
D26-RM-DERAILLEUR|D26-RM|45|
D26-RM-SHIFTER|D26-RM|45|
D26-RM-CRANKARM|D26-RM|85|
D26-RM-PEDAL|D26-RM|85|
D26-RM-BRAKE|D26-RM|75|
D26-RM-BRAKEDISC|D26-RM|26|
D26-RM-HANDLEBAR|D26-RM|85|
D26-RM-SADDLE|D26-RM|85|
D26-RM-SEATPOST|D26-RM|85|
D26-RM-GRIP|D26-RM|85|
D26-RM-BEARING|D26-RM|90|
D26-RM-TRAINWHEEL|D26-RM|45|
D26-RM-CARTON|D26-RM|150|
D26-RM-TOOLKIT|D26-RM|24|
'

# ── nhà cung cấp ─────────────────────────────────────────────────────────────
# code|name|email|phone|address|taxCode
SUPPLIER_ROWS='
D26-SUP-FRAME|Vietnam Steel Frame Co.|sales@vnsteelframe.example|+84 24 3555 0101|12 Nguyen Van Cu, Long Bien, Ha Noi|0101234567
D26-SUP-COMP|Asia Bicycle Components JSC|order@asiabikeparts.example|+84 28 3822 0202|55 Truong Chinh, Tan Binh, Ho Chi Minh City|0302345678
D26-SUP-TIRE|GreenRoad Tire and Tube Ltd.|contact@greenroadtire.example|+84 25 3777 0303|Road 3, Song Than Industrial Park, Binh Duong|0403456789
'

# supplierCode|itemCode,itemCode,...   (mỗi vật tư chỉ gắn một nhà cung cấp, đặt preferred)
SUPPLIER_ITEM_ROWS='
D26-SUP-FRAME|D26-RM-FRAME16,D26-RM-FRAME20,D26-RM-FRAMERD,D26-RM-PAINT,D26-RM-PRIMER,D26-RM-DECAL
D26-SUP-COMP|D26-RM-CHAINRING,D26-RM-CHAIN,D26-RM-CASSETTE7,D26-RM-CASSETTE9,D26-RM-DERAILLEUR,D26-RM-SHIFTER,D26-RM-CRANKARM,D26-RM-PEDAL,D26-RM-BRAKE,D26-RM-BRAKEDISC,D26-RM-HANDLEBAR,D26-RM-SADDLE,D26-RM-SEATPOST,D26-RM-GRIP,D26-RM-BEARING,D26-RM-TRAINWHEEL,D26-RM-TOOLKIT,D26-RM-CARTON
D26-SUP-TIRE|D26-RM-WHEEL16,D26-RM-WHEEL20,D26-RM-RIM700,D26-RM-HUB700,D26-RM-SPOKE,D26-RM-TIRE700,D26-RM-TUBE700
'

# ── chứng từ mua hàng ────────────────────────────────────────────────────────
# Yêu cầu mua hàng: reqNo|supplierCode|finalState|itemCode:qty;...
#   finalState ∈ DRAFT | APPROVED | CONVERTED   (CONVERTED = duyệt rồi chuyển thành đơn mua)
PR_ROWS='
D26-PR-0001|D26-SUP-TIRE|DRAFT|D26-RM-TIRE700:40;D26-RM-TUBE700:40
D26-PR-0002|D26-SUP-FRAME|APPROVED|D26-RM-PRIMER:30;D26-RM-PAINT:40
D26-PR-0003|D26-SUP-COMP|CONVERTED|D26-RM-CASSETTE9:30;D26-RM-DERAILLEUR:20
'
# Đơn mua hàng chuyển ra từ D26-PR-0003
PR3_PO_NO="D26-PO-0005"

# Đơn mua hàng tạo thẳng: poNo|supplierCode|finalState|itemCode:qty:unitPrice;...
#   finalState ∈ DRAFT | SENT | PARTIALLY_RECEIVED | RECEIVED
PO_ROWS='
D26-PO-0001|D26-SUP-COMP|DRAFT|D26-RM-BRAKE:40:210;D26-RM-GRIP:40:45
D26-PO-0002|D26-SUP-TIRE|SENT|D26-RM-RIM700:40:340;D26-RM-HUB700:40:420
D26-PO-0003|D26-SUP-FRAME|PARTIALLY_RECEIVED|D26-RM-FRAME16:40:420;D26-RM-FRAME20:40:480
D26-PO-0004|D26-SUP-COMP|RECEIVED|D26-RM-CARTON:100:35;D26-RM-TOOLKIT:30:95
'

# ── đơn bán hàng ─────────────────────────────────────────────────────────────
# orderNo|customer|finalState|dueInDays|itemCode:qty;...
#   finalState ∈ DRAFT | CONFIRMED | CANCELLED | PLANNED_FULL | PLANNED_PARTIAL
#   PLANNED_FULL / PLANNED_PARTIAL: xác nhận rồi chạy tiếp toàn bộ luồng sản xuất (§10 stage 13)
SO_ROWS='
D26-SO-1001|Hanoi Bicycle Retail JSC|DRAFT|45|D26-FG-BIKE20:12;D26-FG-ROAD700:4
D26-SO-1002|Saigon Sports Distribution Co.|CONFIRMED|40|D26-FG-BIKE20:15
D26-SO-1003|Da Nang Cycling Store|CONFIRMED|50|D26-FG-ROAD700:6;D26-FG-BIKE16:8
D26-SO-1004|Green City Bike Rental|PLANNED_FULL|30|D26-FG-BIKE16:10
D26-SO-1005|Mekong School Supplies Ltd.|PLANNED_PARTIAL|35|D26-FG-BIKE20:8
D26-SO-1006|Northern Highland Trading|CANCELLED|60|D26-FG-BIKE16:5
'

# Lô thành phẩm của hai luồng đi qua MRP
LOT_SO1004="D26-BIKE16-B001"
LOT_SO1005="D26-BIKE20-B003"

# ── lệnh sản xuất tạo thủ công (ngoài luồng MRP) ─────────────────────────────
# woNo|itemCode|qty|finalState
#   finalState ∈ DRAFT | PLANNED | RELEASED | IN_PROGRESS | CLOSED | CANCELLED
WO_ROWS='
D26-WO-2001|D26-FG-BIKE20|5|DRAFT
D26-WO-2002|D26-WIP-FRAME20|10|PLANNED
D26-WO-2003|D26-WIP-DRIVE7|10|RELEASED
D26-WO-2004|D26-FG-BIKE20|6|IN_PROGRESS
D26-WO-2005|D26-FG-ROAD700|3|CLOSED
D26-WO-2006|D26-FG-BIKE16|4|CANCELLED
'
# Lô thành phẩm sinh ra từ hai lệnh sản xuất thủ công có nhập kho
LOT_WO2004="D26-BIKE20-B002"
LOT_WO2005="D26-ROAD700-B002"

# ── tham số thời gian ────────────────────────────────────────────────────────
# Không để trường ngày/giờ nào trống trên màn hình: mỗi cột thời gian mà API cho phép ghi thì
# script đều ghi một giá trị có nghĩa.
#
# Hạn hiệu lực của phân quyền. 🔴 Quá hạn là MẤT quyền (UserRoleAssignmentRepository lọc
# expires_at) — để 365 ngày cho an toàn, đừng hạ xuống vài ngày rồi ngạc nhiên vì tài khoản demo
# hết đăng nhập được vào giữa buổi trình diễn.
ASSIGNMENT_EXPIRY_DAYS=365
# plannedEndAt = plannedStartAt + N ngày, cho mọi lệnh sản xuất (kể cả lệnh sinh từ MRP).
WO_PLANNED_DURATION_DAYS=5
# Hạn dùng của lô, tính từ ngày nhập. Chỉ dùng bởi set-lot-expiry.sh — REST API KHÔNG có đường
# đặt hạn lô (không request DTO nào mang field đó), xem ghi chú trong script ấy.
LOT_SHELF_LIFE_DAYS=730

# ── tài khoản và phạm vi (§3.2) ──────────────────────────────────────────────
SCOPE_CODE="D26-SCOPE-PLANT"
SCOPE_NAME="Entire Demo Plant 2026"

# username|email|roleCode
USER_ROWS='
quanly.demo|quanly.demo@omniplant.vn|MANAGER
kehoach.demo|kehoach.demo@omniplant.vn|MANAGER
congnhan.demo|congnhan.demo@omniplant.vn|OPERATOR
kho.demo|kho.demo@omniplant.vn|OPERATOR
'
USER_MANAGER="quanly.demo"
USER_OPERATOR="congnhan.demo"

# Mật khẩu chung của các tài khoản vận hành. KHÔNG phải mật khẩu quản trị viên —
# tài khoản admin do quy trình bootstrap của backend cấp, nằm ngoài script này (§14).
DEMO_PASSWORD="${DEMO_PASSWORD:-OmniPlant@2026}"

# Tương thích ngược với verify.sh (nó tham chiếu DEMO_OUTPUT_LOT).
DEMO_OUTPUT_LOT="$LOT_SO1004"

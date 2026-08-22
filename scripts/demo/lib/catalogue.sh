#!/usr/bin/env bash
# lib/catalogue.sh — every code, name and quantity of the demo dataset. No HTTP calls here.
#
# Change a name or a quantity in this file and nowhere else.
#
# Codes are ASCII uppercase (the create DTOs enforce ^[A-Z0-9._-]+$).
# Names are Vietnamese with diacritics — this file is UTF-8 without BOM and MUST stay that way.
# Stage 00 verifies a round-trip through the API so a broken shell encoding is caught immediately
# instead of showing up as "BÃ ..." rows halfway through a presentation.

# ── organisation ─────────────────────────────────────────────────────────────
CO_CODE="VIETBIKE"
CO_NAME="Công ty Cổ phần Xe đạp Việt"

P1_CODE="HANOI";  P1_NAME="Nhà máy Hà Nội"
P2_CODE="SAIGON"; P2_NAME="Nhà máy Sài Gòn"

# Warehouse codes repeat per plant — uniqueness is (plant_id, code).
WH_NVL="KHO-NVL"; WH_NVL_NAME="Kho nguyên vật liệu"
WH_BTP="KHO-BTP"; WH_BTP_NAME="Kho bán thành phẩm"
WH_TP="KHO-TP";   WH_TP_NAME="Kho thành phẩm"
WH_KIEMDINH="KHO-KD"; WH_KIEMDINH_NAME="Kho cách ly kiểm định"   # deactivated on purpose

# ── units of measure (global) ────────────────────────────────────────────────
# code|name|description
UOM_ROWS='
CAI|Cái|Đơn vị đếm từng chiếc
BO|Bộ|Nhóm chi tiết đi liền nhau
KG|Kilôgam|Khối lượng
MET|Mét|Chiều dài
LIT|Lít|Thể tích
GIO|Giờ công|Thời gian lao động
TA|Tạ|Đơn vị cũ, không còn dùng
'
UOM_RETIRED="TA"   # deactivated so the UOM list shows both statuses

# ── items ────────────────────────────────────────────────────────────────────
# code|name|type|unit|lotTracked|serialTracked
#
# Four load-bearing choices, none of them cosmetic:
#   XE-PHO-26   lot-tracked  -> QC releases a LOT (the B39 branch)
#   XE-DIA-275  not tracked  -> QC releases the RECEIPT itself (the B40 branch, debt #17)
#   XE-DIEN     serial       -> the only serial rows in the database (P5); receipts must be qty 1
#   BANH-20     never stocked anywhere -> the only reliable way into WorkOrder BLOCKED
ITEM_ROWS='
XE-PHO-26|Xe đạp phố 26 inch|FINISHED_GOOD|CAI|true|false
XE-DIA-275|Xe đạp địa hình 27.5 inch|FINISHED_GOOD|CAI|false|false
XE-TRE-EM|Xe đạp trẻ em 16 inch|FINISHED_GOOD|CAI|false|false
XE-DIEN|Xe đạp điện trợ lực 36V|FINISHED_GOOD|CAI|false|true
KHUNG-PHO|Khung nhôm xe phố (đã sơn)|WIP|CAI|true|false
KHUNG-DIA|Khung nhôm xe địa hình (đã sơn)|WIP|CAI|true|false
BANH-26|Bánh xe 26 inch hoàn chỉnh|WIP|BO|true|false
BANH-275|Bánh xe 27.5 inch hoàn chỉnh|WIP|BO|true|false
BO-TRUYEN-7|Bộ truyền động 7 tốc độ|WIP|BO|false|false
ONG-NHOM|Ống nhôm hợp kim 6061|RAW_MATERIAL|MET|true|false
ONG-THEP|Ống thép Crôm-Môlipđen 4130|RAW_MATERIAL|MET|true|false
VANH-26|Vành hợp kim 26 inch|RAW_MATERIAL|CAI|false|false
VANH-275|Vành hợp kim 27.5 inch|RAW_MATERIAL|CAI|false|false
NAN-HOA|Nan hoa thép không gỉ|RAW_MATERIAL|CAI|false|false
MOAYO|Moay-ơ vòng bi kín|RAW_MATERIAL|CAI|false|false
LOP-26|Lốp xe 26 inch|RAW_MATERIAL|CAI|false|false
LOP-275|Lốp xe 27.5 inch|RAW_MATERIAL|CAI|false|false
XICH|Xích xe đạp 7 tốc độ|RAW_MATERIAL|CAI|false|false
LIP-7|Líp 7 tầng|RAW_MATERIAL|CAI|false|false
BAN-DAP|Bàn đạp hợp kim|RAW_MATERIAL|BO|false|false
YEN-XE|Yên xe da tổng hợp|RAW_MATERIAL|CAI|false|false
TAY-LAI|Tay lái nhôm|RAW_MATERIAL|CAI|false|false
PHANH-V|Bộ phanh V-brake|RAW_MATERIAL|BO|false|false
BANH-20|Bánh xe 20 inch mua ngoài|RAW_MATERIAL|CAI|false|false
PIN-36V|Pin Li-ion 36V|RAW_MATERIAL|CAI|true|false
DONG-CO|Động cơ trợ lực 250W|RAW_MATERIAL|CAI|false|false
SON-XANH|Sơn tĩnh điện màu xanh|CONSUMABLE|KG|true|false
KHI-HAN|Khí hàn Argon|CONSUMABLE|LIT|false|false
OC-VIT-M6|Ốc vít M6 mạ kẽm|CONSUMABLE|CAI|false|false
DV-ANOT|Dịch vụ anốt hoá bề mặt|SERVICE|GIO|false|false
VANH-24|Vành hợp kim 24 inch (ngừng dùng)|RAW_MATERIAL|CAI|false|false
'
ITEM_RETIRED="VANH-24"   # deactivated so the item list shows both statuses

# ── bills of material ────────────────────────────────────────────────────────
# parentCode|revision|ACTIVATE|componentCode:qtyPer:scrapRate;...
#
# BO-TRUYEN-7 deliberately has NO BOM: it is WIP, so MRP treats it as MAKE, and a missing BOM makes
# its proposal BLOCKED (MISSING_BOM) at level 1 of the explosion.
# XE-TRE-EM has a BOM but NO routing, which blocks its proposal at level 0 (MISSING_ROUTING).
BOM_ROWS='
XE-PHO-26|A|YES|KHUNG-PHO:1:0;BANH-26:2:0;BO-TRUYEN-7:1:0;YEN-XE:1:0;TAY-LAI:1:0;PHANH-V:2:0;OC-VIT-M6:24:0.02
XE-PHO-26|B|NO|KHUNG-PHO:1:0;BANH-26:2:0;BO-TRUYEN-7:1:0;YEN-XE:1:0;TAY-LAI:1:0;PHANH-V:2:0;OC-VIT-M6:26:0.02
XE-DIA-275|A|YES|KHUNG-DIA:1:0;BANH-275:2:0;BO-TRUYEN-7:1:0;YEN-XE:1:0;TAY-LAI:1:0;PHANH-V:2:0
XE-TRE-EM|A|YES|BANH-20:2:0;XICH:1:0;YEN-XE:1:0;TAY-LAI:1:0
XE-DIEN|A|YES|KHUNG-DIA:1:0;BANH-275:2:0;PIN-36V:1:0;DONG-CO:1:0;YEN-XE:1:0
KHUNG-PHO|A|YES|ONG-NHOM:3.2:0.08;SON-XANH:0.25:0;KHI-HAN:0.4:0
KHUNG-DIA|A|YES|ONG-NHOM:3.8:0.08;SON-XANH:0.3:0;KHI-HAN:0.5:0
BANH-26|A|YES|VANH-26:2:0;NAN-HOA:64:0.05;MOAYO:2:0;LOP-26:2:0
BANH-275|A|YES|VANH-275:2:0;NAN-HOA:64:0.05;MOAYO:2:0;LOP-275:2:0
'

# ── work centers (plant HANOI unless the row says otherwise) ─────────────────
# code|name|capacityUnitType|capacityUnits|calendarCode  (calendarCode "-" = none, on purpose)
#
# TO-KCS keeps no calendar so the capacity board has a work center reporting
# dayCapacityMinutes=null / utilizationPercent=null (C2-8 decision #3). WorkCenterUpdateRequest
# treats null as "unchanged", so a calendar can never be removed later — it has to start without one.
WC_ROWS='
TO-CAT|Tổ cắt ống|MACHINE|2|LICH-3CA
TO-HAN|Tổ hàn khung|LABOR_TEAM|1|LICH-2CA
TO-SON|Tổ sơn tĩnh điện|LINE|1|LICH-2CA
TO-BANH|Tổ vào bánh|LABOR_TEAM|3|LICH-2CA
TO-LAPRAP|Tổ lắp ráp hoàn thiện|LINE|2|LICH-2CA
TO-KCS|Tổ KCS và đóng gói|LABOR_TEAM|1|-
TO-HANCU|Tổ hàn hơi (đã ngừng)|MACHINE|1|-
'
WC_RETIRED="TO-HANCU"

# ── shifts (plant HANOI) ─────────────────────────────────────────────────────
# code|name|start|end|break1s-break1e,break2s-break2e   ("-" = no break)
# CA-DEM ends before it starts: a legitimate overnight shift (ShiftTimeWindow measures circularly).
SHIFT_ROWS='
CA-SANG|Ca sáng|06:00|14:00|09:30-09:45,11:30-12:00
CA-CHIEU|Ca chiều|14:00|22:00|18:00-18:30
CA-DEM|Ca đêm|22:00|06:00|02:00-02:30
'

# ── work calendars (plant HANOI) ─────────────────────────────────────────────
# code|name|shiftCodes(comma)|weekdays(comma)
CAL_2CA="LICH-2CA";  CAL_2CA_NAME="Lịch 2 ca, Thứ Hai đến Thứ Bảy"
CAL_3CA="LICH-3CA";  CAL_3CA_NAME="Lịch 3 ca liên tục, Thứ Hai đến Thứ Sáu"
CAL_SG="LICH-SG";    CAL_SG_NAME="Lịch hành chính Sài Gòn"

# ── routings ─────────────────────────────────────────────────────────────────
# code|version|itemCode|ACTIVATE|seq:name:workCenterCode:setupMinutes:runMinutesPerUnit;...
#
# TO-HAN's runMinutesPerUnit is high on purpose, and TO-HAN is deliberately the only tổ with
# capacityUnits = 1: two 10-unit frame work orders land on it the same day for ~1240 minutes against
# LICH-2CA's ~885 working minutes, so the capacity board reports overload=true with a real
# utilization figure (~140%) instead of an all-green screen nobody can demo. Capacity is per-day
# working minutes MULTIPLIED by capacityUnits — raise TO-HAN back to 4 and the overload disappears.
ROUTING_ROWS='
QT-XE-PHO|1|XE-PHO-26|YES|10:Chuẩn bị khung:TO-LAPRAP:20:4;20:Vào bánh:TO-BANH:10:6;30:Lắp ráp hoàn thiện:TO-LAPRAP:15:25;40:Chạy thử và KCS:TO-KCS:0:8
QT-XE-PHO|2|XE-PHO-26|NO|10:Chuẩn bị khung:TO-LAPRAP:15:3;20:Vào bánh:TO-BANH:10:5;30:Lắp ráp hoàn thiện:TO-LAPRAP:10:20;40:Chạy thử và KCS:TO-KCS:0:6
QT-XE-DIA|1|XE-DIA-275|YES|10:Chuẩn bị khung:TO-LAPRAP:20:4;20:Vào bánh:TO-BANH:10:6;30:Lắp ráp hoàn thiện:TO-LAPRAP:15:28
QT-XE-DIEN|1|XE-DIEN|YES|10:Lắp động cơ và pin:TO-LAPRAP:30:40;20:Kiểm tra điện:TO-KCS:0:20
QT-KHUNG-PHO|1|KHUNG-PHO|YES|10:Cắt và vát ống:TO-CAT:30:12;20:Hàn TIG:TO-HAN:45:55;30:Sơn tĩnh điện:TO-SON:60:9
QT-KHUNG-DIA|1|KHUNG-DIA|YES|10:Cắt và vát ống:TO-CAT:30:14;20:Hàn TIG:TO-HAN:45:60;30:Sơn tĩnh điện:TO-SON:60:10
QT-BANH-26|1|BANH-26|YES|10:Đan nan hoa:TO-BANH:15:22;20:Cân vành và căng nan:TO-BANH:5:14
QT-BANH-275|1|BANH-275|YES|10:Đan nan hoa:TO-BANH:15:24;20:Cân vành và căng nan:TO-BANH:5:15
'

# ── suppliers ────────────────────────────────────────────────────────────────
# code|name|email|phone|address|taxCode
SUPPLIER_ROWS='
NCC-NHOM|Công ty Nhôm Đông Á|banhang@nhomdonga.vn|02438765432|Khu công nghiệp Sài Đồng, Hà Nội|0101234567
NCC-VANH|Công ty Vành xe Tiến Đạt|sales@vanhtiendat.vn|02839001122|Khu công nghiệp Tân Bình, TP.HCM|0302233445
NCC-TRUYENDONG|Công ty Phụ tùng Thống Nhất|kinhdoanh@thongnhat.vn|02437778899|Số 10 Nguyễn Trãi, Hà Nội|0100998877
NCC-CAOSU|Công ty Cao su Sao Vàng|export@caosusaovang.vn|02438584949|231 Nguyễn Trãi, Thanh Xuân, Hà Nội|0100100999
NCC-SONCU|Công ty Sơn Hải Phòng (ngừng hợp tác)|lienhe@sonhaiphong.vn|02253846789|12 Lạch Tray, Hải Phòng|0200456789
'
SUPPLIER_RETIRED="NCC-SONCU"

# itemCode|supplierCode|preferred|leadTimeDays|minimumOrderQuantity|unitPrice
# LOP-26 intentionally has two sources so the item-supplier screen has a multi-row case, and only
# one of them is preferred (a partial unique index allows exactly one preferred ACTIVE row per item).
ITEM_SUPPLIER_ROWS='
ONG-NHOM|NCC-NHOM|true|14|100|185000
ONG-THEP|NCC-NHOM|true|21|100|240000
VANH-26|NCC-VANH|true|10|50|480000
VANH-275|NCC-VANH|true|10|50|520000
NAN-HOA|NCC-VANH|true|7|1000|9500
MOAYO|NCC-VANH|true|12|40|390000
LOP-26|NCC-CAOSU|true|9|60|565000
LOP-26|NCC-VANH|false|15|100|610000
LOP-275|NCC-CAOSU|true|9|60|672000
XICH|NCC-TRUYENDONG|true|18|30|198000
LIP-7|NCC-TRUYENDONG|true|18|30|265000
BAN-DAP|NCC-TRUYENDONG|true|18|30|145000
YEN-XE|NCC-TRUYENDONG|true|12|50|175000
TAY-LAI|NCC-TRUYENDONG|true|12|50|210000
PHANH-V|NCC-TRUYENDONG|true|15|40|320000
BANH-20|NCC-VANH|true|20|40|455000
'

# ── standard costs (VND) ─────────────────────────────────────────────────────
# itemCode|materialCost|laborCost|overheadCost
# Raw materials carry a material cost; WIP/finished goods carry labour + overhead and let the BOM
# roll-up compute their material component.
COST_ROWS='
ONG-NHOM|185000|0|0
ONG-THEP|240000|0|0
VANH-26|480000|0|0
VANH-275|520000|0|0
NAN-HOA|9500|0|0
MOAYO|390000|0|0
LOP-26|565000|0|0
LOP-275|672000|0|0
XICH|198000|0|0
LIP-7|265000|0|0
BAN-DAP|145000|0|0
YEN-XE|175000|0|0
TAY-LAI|210000|0|0
PHANH-V|320000|0|0
BANH-20|455000|0|0
PIN-36V|4200000|0|0
DONG-CO|3100000|0|0
SON-XANH|142000|0|0
KHI-HAN|38000|0|0
OC-VIT-M6|1200|0|0
DV-ANOT|0|95000|0
VANH-24|410000|0|0
KHUNG-PHO|0|360000|120000
KHUNG-DIA|0|410000|140000
BANH-26|0|180000|60000
BANH-275|0|195000|65000
BO-TRUYEN-7|980000|0|0
XE-PHO-26|0|700000|240000
XE-DIA-275|0|760000|260000
XE-TRE-EM|0|320000|110000
XE-DIEN|0|1200000|440000
'

# ── opening stock, plant HANOI ───────────────────────────────────────────────
# itemCode|warehouse(NVL|BTP|TP)|quantity|lotCode ("-" = item is not lot-tracked)
#
# BANH-20 appears nowhere. reserveAutomatically resolves the PLANT scope, not the output warehouse,
# so a component has to be absent from EVERY warehouse of the plant to keep a work order short.
STOCK_ROWS='
ONG-NHOM|NVL|900|LO-NHOM-01
ONG-NHOM|NVL|400|LO-NHOM-02
ONG-THEP|NVL|200|LO-THEP-01
ONG-THEP|BTP|40|LO-THEP-LOI
VANH-26|NVL|300|-
VANH-275|NVL|300|-
NAN-HOA|NVL|9000|-
MOAYO|NVL|400|-
LOP-26|NVL|260|-
LOP-275|NVL|240|-
XICH|NVL|150|-
LIP-7|NVL|150|-
BAN-DAP|NVL|150|-
YEN-XE|NVL|200|-
TAY-LAI|NVL|200|-
PHANH-V|NVL|200|-
PIN-36V|NVL|20|LO-PIN-01
DONG-CO|NVL|20|-
SON-XANH|NVL|120|LO-SON-01
SON-XANH|NVL|60|LO-SON-GIU
KHI-HAN|NVL|400|-
OC-VIT-M6|NVL|5000|-
KHUNG-PHO|BTP|40|LO-KHUNGPHO-01
KHUNG-DIA|BTP|40|LO-KHUNGDIA-01
BANH-26|BTP|70|LO-BANH26-01
BANH-275|BTP|40|LO-BANH275-01
BO-TRUYEN-7|BTP|60|-
'

# Lots that get moved out of AVAILABLE by hand after the receive.
# These are receive-born lots, so the manual endpoint is legal on them; a lot born from a production
# receipt is gated by LotQcOriginLookupService and must go through qc-disposition instead.
LOT_HOLD_CODE="LO-SON-GIU"
LOT_HOLD_ITEM="SON-XANH"
LOT_HOLD_REASON="Độ nhớt lô sơn vượt ngưỡng, giữ chờ kết luận của nhà cung cấp"
LOT_REJECT_CODE="LO-THEP-LOI"
LOT_REJECT_ITEM="ONG-THEP"
LOT_REJECT_REASON="Phát hiện rỗ khí trên bề mặt ống, loại bỏ toàn bộ lô"

# opening stock, plant SAIGON — lighter, only enough to prove plant isolation is real
STOCK_ROWS_SG='
KHUNG-DIA|NVL|10|LO-SG-KHUNGDIA-01
BANH-275|NVL|20|LO-SG-BANH275-01
BO-TRUYEN-7|NVL|10|-
YEN-XE|NVL|20|-
TAY-LAI|NVL|20|-
PHANH-V|NVL|20|-
'

# ── access control ───────────────────────────────────────────────────────────
DEMO_PASSWORD="Demo@1234"

SCOPE_ALL="PHAMVI-TOANCONGTY"; SCOPE_ALL_NAME="Toàn công ty Xe đạp Việt"
SCOPE_HN="PHAMVI-HANOI";       SCOPE_HN_NAME="Chỉ nhà máy Hà Nội"
SCOPE_SG="PHAMVI-SAIGON";      SCOPE_SG_NAME="Chỉ nhà máy Sài Gòn"
SCOPE_TP="PHAMVI-KHOTP";       SCOPE_TP_NAME="Chỉ kho thành phẩm Hà Nội"

ROLE_PLANNER="KEHOACH"; ROLE_PLANNER_NAME="Nhân viên kế hoạch sản xuất"

# username|email|roleCode|scopeCode|expiresInDays ("-" = never)
USER_ROWS='
quanly.hanoi|quanly.hanoi@vietbike.vn|MANAGER|PHAMVI-HANOI|-
congnhan.hanoi|congnhan.hanoi@vietbike.vn|OPERATOR|PHAMVI-HANOI|-
kehoach.vietbike|kehoach.vietbike@vietbike.vn|KEHOACH|PHAMVI-TOANCONGTY|-
quanly.saigon|quanly.saigon@vietbike.vn|MANAGER|PHAMVI-SAIGON|-
kiemtoan.tamthoi|kiemtoan.tamthoi@vietbike.vn|OPERATOR|PHAMVI-KHOTP|7
'

# Permission code prefixes granted to the custom KEHOACH role.
PLANNER_PERMISSIONS='PERM_MRP_RUN PERM_MRP_READ PERM_SUPPLY_SUGGESTION_MANAGE PERM_PLANNING_READ PERM_PLANNING_DEMAND_READ PERM_PLANNING_DEMAND_MANAGE PERM_WORK_ORDER_READ PERM_INVENTORY_READ PERM_ITEM_READ PERM_SALES_ORDER_READ PERM_BOM_READ'

# ── customers used on sales orders ───────────────────────────────────────────
KH_THONGNHAT="Cửa hàng Xe đạp Thống Nhất"
KH_DONGLUC="Siêu thị Thể thao Động Lực"
KH_SAIGON="Xe đạp Sài Gòn"
KH_KIMLIEN="Trường Tiểu học Kim Liên"
KH_HOANGANH="Đại lý Xe đạp Hoàng Anh"

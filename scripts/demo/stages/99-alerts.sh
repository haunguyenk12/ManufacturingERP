#!/usr/bin/env bash
# 99-alerts — item/warehouse thresholds, so the inventory dashboard shows all three bands.
#
# 🔴 Runs LAST, and derives every threshold from the LIVE available quantity rather than hard-coding
# numbers. By this point reservations, issues, QC rejections and goods receipts have all moved the
# figures; a threshold picked up front would have drifted into the wrong band. InventoryAlertService
# decides the band like this:
#
#     available <= reorderPoint  -> REORDER_NEEDED
#     available <  safetyStock   -> LOW_STOCK
#     otherwise                  -> OK
#
# so choosing the thresholds relative to `available` makes the band exact by construction.

stage_banner "14. Ngưỡng cảnh báo tồn kho"

# set_alert ITEM_CODE WAREHOUSE_ID BAND LEAD_DAYS
set_alert() {
    local item="$1" wh="$2" band="$3" lead="$4" avail safety reorder
    avail=$(available_qty "${ID[items.$item]}" "$wh")
    case "$band" in
        OK)             safety=$(num "max(1, int(float('$avail')*0.5))")
                        reorder=$(num "max(0, int(float('$avail')*0.3))") ;;
        LOW_STOCK)      safety=$(num "int(float('$avail'))+50")
                        reorder=$(num "max(0, int(float('$avail'))-10)") ;;
        REORDER_NEEDED) safety=$(num "int(float('$avail'))+100")
                        reorder=$(num "int(float('$avail'))+25") ;;
        *) die "nhóm cảnh báo không hợp lệ: $band" ;;
    esac
    call PUT /inventory/item-warehouse-settings \
        "{\"itemId\":\"${ID[items.$item]}\",\"warehouseId\":\"$wh\",\"safetyStock\":$safety,\"reorderPoint\":$reorder,\"leadTimeDays\":$lead}" \
        >/dev/null
    info "$item: khả dụng $avail, an toàn $safety, đặt lại $reorder -> $band"
}

WH_RM_HN="${ID[plants.$P1_CODE.warehouses.$WH_NVL]}"
WH_BTP_HN="${ID[plants.$P1_CODE.warehouses.$WH_BTP]}"
WH_RM_SG="${ID[plants.$P2_CODE.warehouses.$WH_NVL]}"

log "Kho nguyên vật liệu $P1_CODE"
for item in NAN-HOA OC-VIT-M6 XICH ONG-THEP KHI-HAN; do set_alert "$item" "$WH_RM_HN" OK 10; done
for item in ONG-NHOM VANH-26 MOAYO;                    do set_alert "$item" "$WH_RM_HN" LOW_STOCK 14; done
for item in LOP-26 LOP-275 VANH-275 SON-XANH;          do set_alert "$item" "$WH_RM_HN" REORDER_NEEDED 9; done

log "Kho bán thành phẩm $P1_CODE"
set_alert KHUNG-PHO   "$WH_BTP_HN" OK 5
set_alert BANH-275    "$WH_BTP_HN" LOW_STOCK 5
set_alert BO-TRUYEN-7 "$WH_BTP_HN" REORDER_NEEDED 7

log "Kho nguyên vật liệu $P2_CODE"
set_alert BANH-275 "$WH_RM_SG" OK 5
set_alert YEN-XE   "$WH_RM_SG" REORDER_NEEDED 12

# 🔴 (XE-PHO-26, KHO-TP) is deliberately NOT touched here. Stage 20 created it with both thresholds
# at zero, and that row is the single thing making the XE-PHO-26 proposal come back READY instead of
# WARNING. Overwriting it would silently rewrite the MRP story on the next run.

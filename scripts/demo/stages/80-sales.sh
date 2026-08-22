#!/usr/bin/env bash
# 80-sales — sales orders. Three finish here; four are handed to MRP and finished in stages 85/90.
#
# Confirming an order is what creates its PlanningDemand rows (one per line, referenceId = the
# sales order line id). That is the hook stage 85 uses to pick exactly the demands it wants.

stage_banner "10. Đơn bán hàng"

CO="${ID[company.id]}"
P1="${ID[plants.$P1_CODE.id]}"

# so_create NO CUSTOMER ITEM QTY DUE_DAYS
#
# Sets the global NEW_ID instead of echoing. Calling it inside $(...) would run `remember` in a
# subshell, so the order would exist in the database and be missing from demo-ids.json.
so_create() {
    local no="$1" customer="$2" item="$3" qty="$4" due="$5" resp
    resp=$(call POST /sales-orders \
        "{\"companyId\":\"$CO\",\"plantId\":\"$P1\",\"orderNo\":\"$no\",\"customerName\":\"$customer\",\"orderDate\":\"$(d_today)\",\"note\":\"Đơn hàng thuộc bộ dữ liệu demo\",\"lines\":[{\"itemId\":\"${ID[items.$item]}\",\"orderedQuantity\":$qty,\"dueDate\":\"$(d_plus "$due")\"}]}")
    NEW_ID=$(echo "$resp" | rid salesOrderId)
    remember "salesOrders.$no.id" "$NEW_ID"
    remember "salesOrders.$no.lineId" "$(echo "$resp" | extract "d['result']['lines'][0]['salesOrderLineId']")"
}

log "SO-1001 — DRAFT"
so_create "SO-1001" "$KH_THONGNHAT" "XE-PHO-26" 5 30; SO1001="$NEW_ID"
assert_status "/sales-orders/$SO1001" "DRAFT"

# Confirmed but deliberately left out of every MRP run, so the planning-demand screen has a row
# still waiting to be planned.
log "SO-1002 — CONFIRMED (cố ý không đưa vào lượt chạy MRP nào)"
so_create "SO-1002" "$KH_DONGLUC" "XE-DIA-275" 8 45; SO1002="$NEW_ID"
call POST "/sales-orders/$SO1002/confirm" >/dev/null
assert_status "/sales-orders/$SO1002" "CONFIRMED"

# Cancel is only legal from DRAFT or CONFIRMED, so this has to happen before any run could move it
# to IN_PRODUCTION. Cancelling also cancels the order's still-OPEN demands, which is what gives the
# demand screen a CANCELLED row.
log "SO-1003 — CANCELLED"
so_create "SO-1003" "$KH_HOANGANH" "XE-PHO-26" 3 20; SO1003="$NEW_ID"
call POST "/sales-orders/$SO1003/confirm" >/dev/null
call POST "/sales-orders/$SO1003/cancel" >/dev/null
assert_status "/sales-orders/$SO1003" "CANCELLED"

log "SO-1004 — CONFIRMED, sẽ thành PARTIALLY_FULFILLED sau khi QC"
so_create "SO-1004" "$KH_THONGNHAT" "XE-PHO-26" 10 35; SO1004="$NEW_ID"
call POST "/sales-orders/$SO1004/confirm" >/dev/null

log "SO-1005 — CONFIRMED, sẽ thành FULFILLED sau khi QC"
so_create "SO-1005" "$KH_SAIGON" "XE-DIA-275" 4 40; SO1005="$NEW_ID"
call POST "/sales-orders/$SO1005/confirm" >/dev/null

log "SO-1006 — CONFIRMED, sẽ dừng ở IN_PRODUCTION"
so_create "SO-1006" "$KH_DONGLUC" "XE-PHO-26" 6 50; SO1006="$NEW_ID"
call POST "/sales-orders/$SO1006/confirm" >/dev/null

# XE-TRE-EM has no ACTIVE routing, so this order's proposal comes back BLOCKED and can never be
# converted. The order therefore stays CONFIRMED for good — a realistic "planning is stuck" row.
log "SO-1007 — CONFIRMED, đề xuất sẽ bị BLOCKED (XE-TRE-EM chưa có quy trình công nghệ)"
so_create "SO-1007" "$KH_KIMLIEN" "XE-TRE-EM" 12 60; SO1007="$NEW_ID"
call POST "/sales-orders/$SO1007/confirm" >/dev/null

# ── a demand that did not come from a sales order ────────────────────────────
# Gives the demand screen a FORECAST row, and gives the suggestion list a proposal with no sales
# order behind it (so no fulfilment allocation is created when it converts — B64).
log "Tạo nhu cầu dự báo thủ công (không gắn đơn bán hàng)"
remember "planningDemands.FORECAST" "$(call POST /planning/demands \
    "{\"companyId\":\"$CO\",\"plantId\":\"$P1\",\"itemId\":\"${ID[items.XE-DIA-275]}\",\"warehouseId\":\"${ID[plants.$P1_CODE.warehouses.$WH_TP]}\",\"demandType\":\"FORECAST\",\"requiredQuantity\":15,\"dueDate\":\"$(d_plus 75)\",\"priority\":50,\"referenceType\":\"DEMO_FORECAST\",\"referenceId\":\"DUBAO-Q4-XEDIA\"}" \
    | rid planningDemandId)"

#!/usr/bin/env bash
# 60-stock — opening inventory, and the two lots that start life outside AVAILABLE.
#
# There is no POST /inventory/lots. A lot exists because some movement carried a lotCode — here a
# receive; later, a goods receipt or a production receipt. That is why the lot ids below have to be
# looked up after the fact rather than captured from the create response.

stage_banner "7. Tồn kho đầu kỳ"

wh_id() {  # wh_id PLANT_CODE NVL|BTP|TP
    case "$2" in
        NVL) echo "${ID[plants.$1.warehouses.$WH_NVL]}" ;;
        BTP) echo "${ID[plants.$1.warehouses.$WH_BTP]}" ;;
        TP)  echo "${ID[plants.$1.warehouses.$WH_TP]}"  ;;
        *)   die "kho không hợp lệ: $2" ;;
    esac
}

receive_rows() {  # receive_rows PLANT_CODE <<< rows
    local plant="$1" n=0
    while IFS='|' read -r item wh qty lot; do
        [ -z "$item" ] && continue
        n=$((n + 1))
        local wid body key
        wid=$(wh_id "$plant" "$wh")
        key="DEMO-NHAP-$plant-$item-$n"
        if [ "$lot" = "-" ]; then
            body="{\"itemId\":\"${ID[items.$item]}\",\"warehouseId\":\"$wid\",\"quantity\":$qty,\"reason\":\"Tồn kho đầu kỳ của bộ dữ liệu demo\",\"referenceType\":\"DEMO_SEED\",\"referenceId\":\"$key\"}"
        else
            body="{\"itemId\":\"${ID[items.$item]}\",\"warehouseId\":\"$wid\",\"lotCode\":\"$lot\",\"quantity\":$qty,\"reason\":\"Tồn kho đầu kỳ của bộ dữ liệu demo\",\"referenceType\":\"DEMO_SEED\",\"referenceId\":\"$key\"}"
        fi
        call POST /inventory/receive "$body" -H "Idempotency-Key: $key" >/dev/null
        if [ "$lot" = "-" ]; then
            info "$item -> $wh: $qty"
        else
            info "$item -> $wh: $qty (lô $lot)"
        fi
    done
}

log "Nhập tồn kho đầu kỳ tại $P1_CODE"
receive_rows "$P1_CODE" <<EOF
$(echo "$STOCK_ROWS" | sed '/^[[:space:]]*$/d')
EOF

log "Nhập tồn kho đầu kỳ tại $P2_CODE"
receive_rows "$P2_CODE" <<EOF
$(echo "$STOCK_ROWS_SG" | sed '/^[[:space:]]*$/d')
EOF

# 🔴 BANH-20 is deliberately absent from every warehouse of both plants. reserveAutomatically
# resolves the PLANT scope rather than the work order's output warehouse, so a component has to be
# missing plant-wide for a work order to stay short — that is the only reliable route into BLOCKED
# (stage 70). Do not "helpfully" stock it.

# ── lots that do not start AVAILABLE ─────────────────────────────────────────
# Legal here because these lots were born from a receive. A lot born from a production receipt is
# gated by LotQcOriginLookupService (B102/B103) and must be released through qc-disposition instead
# — stage 70 does it that way.
log "Chuyển trạng thái lô thủ công"

# Unlike /inventory/receive, where Idempotency-Key is optional, the lot-status endpoint REQUIRES it
# (MISSING_REQUIRED_FIELD without one) — a lot status change writes a LOT_STATUS_CHANGE movement.
HOLD_WH=$(wh_id "$P1_CODE" NVL)
HOLD_LOT=$(lot_id "${ID[items.$LOT_HOLD_ITEM]}" "$HOLD_WH" "$LOT_HOLD_CODE")
call POST "/inventory/lots/$HOLD_LOT/status" \
    "{\"warehouseId\":\"$HOLD_WH\",\"newStatus\":\"HOLD\",\"reason\":\"$LOT_HOLD_REASON\",\"referenceType\":\"DEMO_SEED\",\"referenceId\":\"$LOT_HOLD_CODE\"}" \
    -H "Idempotency-Key: DEMO-TRANGTHAILO-$LOT_HOLD_CODE" >/dev/null
remember "lots.$LOT_HOLD_CODE" "$HOLD_LOT"
info "$LOT_HOLD_CODE -> HOLD"

REJ_WH=$(wh_id "$P1_CODE" BTP)
REJ_LOT=$(lot_id "${ID[items.$LOT_REJECT_ITEM]}" "$REJ_WH" "$LOT_REJECT_CODE")
call POST "/inventory/lots/$REJ_LOT/status" \
    "{\"warehouseId\":\"$REJ_WH\",\"newStatus\":\"REJECTED\",\"reason\":\"$LOT_REJECT_REASON\",\"referenceType\":\"DEMO_SEED\",\"referenceId\":\"$LOT_REJECT_CODE\"}" \
    -H "Idempotency-Key: DEMO-TRANGTHAILO-$LOT_REJECT_CODE" >/dev/null
remember "lots.$LOT_REJECT_CODE" "$REJ_LOT"
info "$LOT_REJECT_CODE -> REJECTED"

# Both lots keep their on-hand quantity and report availableQuantity 0 — the behaviour corrected on
# 2026-08-14 after FE's live-data audit. Worth showing side by side on the lot screen.

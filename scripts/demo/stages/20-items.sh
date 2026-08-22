#!/usr/bin/env bash
# 20-items — item master, standard costs, and the one planning setting the MRP story depends on.

stage_banner "3. Danh mục vật tư và giá thành chuẩn"

log "Tạo vật tư"
while IFS='|' read -r code name type unit lot serial; do
    [ -z "$code" ] && continue
    iid=$(call POST "/companies/${ID[company.id]}/items" \
        "{\"code\":\"$code\",\"name\":\"$name\",\"type\":\"$type\",\"unit\":\"$unit\",\"lotTracked\":$lot,\"serialTracked\":$serial}" \
        | rid itemId)
    remember "items.$code" "$iid"
    info "$code — $name"
done <<EOF
$(echo "$ITEM_ROWS" | sed '/^[[:space:]]*$/d')
EOF

# ── standard costs ───────────────────────────────────────────────────────────
# PUT is an upsert keyed by item (uk_item_standard_costs_item). totalStandardCost on the response is
# computed at read time by rolling the BOM up, so these numbers only become interesting after
# stage 40 activates the BOMs.
log "Đặt giá thành chuẩn"
while IFS='|' read -r code mat lab ovh; do
    [ -z "$code" ] && continue
    call PUT "/companies/${ID[company.id]}/items/${ID[items.$code]}/standard-cost" \
        "{\"materialCost\":$mat,\"laborCost\":$lab,\"overheadCost\":$ovh}" >/dev/null
done <<EOF
$(echo "$COST_ROWS" | sed '/^[[:space:]]*$/d')
EOF
info "đã đặt giá cho $(echo "$COST_ROWS" | sed '/^[[:space:]]*$/d' | wc -l | tr -d ' ') vật tư"

# ── the one planning setting that decides READY vs WARNING ───────────────────
# MrpCalculationService flags a requirement SYSTEM_FALLBACK_USED (=> exceptionState WARNING) when
# there is no ACTIVE ItemWarehouseSetting for THE RUN'S warehouse. Stage 85 runs MRP against
# KHO-TP, so this single row is what makes the XE-PHO-26 proposal come back READY while
# XE-DIA-275 (no setting there) comes back WARNING — two different exception states from one run.
#
# Both thresholds are zero so the row changes the exception state without distorting the net
# requirement arithmetic. Stage 99 must NOT overwrite it.
log "Đặt tham số hoạch định cho XE-PHO-26 tại $WH_TP (quyết định READY vs WARNING của MRP)"
call PUT /inventory/item-warehouse-settings \
    "{\"itemId\":\"${ID[items.XE-PHO-26]}\",\"warehouseId\":\"${ID[plants.$P1_CODE.warehouses.$WH_TP]}\",\"safetyStock\":0,\"reorderPoint\":0,\"leadTimeDays\":3}" \
    >/dev/null

# ── retire one item, LAST ────────────────────────────────────────────────────
# Deliberately after the cost loop: an INACTIVE item is refused by every master-data write
# (OPERATION_NOT_ALLOWED, "Inactive item cannot be used"), including PUT .../standard-cost. Retiring
# it earlier would abort the run — which is exactly what happened on the first attempt.
log "Ngừng dùng vật tư $ITEM_RETIRED (để danh sách vật tư có trạng thái INACTIVE)"
call DELETE "/items/${ID[items.$ITEM_RETIRED]}" >/dev/null
assert_status "/items/${ID[items.$ITEM_RETIRED]}" "INACTIVE"

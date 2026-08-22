#!/usr/bin/env bash
# 50-suppliers — suppliers and item-supplier links.

stage_banner "6. Nhà cung cấp"

log "Tạo nhà cung cấp"
while IFS='|' read -r code name email phone address tax; do
    [ -z "$code" ] && continue
    sid=$(call POST /suppliers \
        "{\"companyId\":\"${ID[company.id]}\",\"code\":\"$code\",\"name\":\"$name\",\"email\":\"$email\",\"phone\":\"$phone\",\"address\":\"$address\",\"taxCode\":\"$tax\"}" \
        | rid supplierId)
    remember "suppliers.$code" "$sid"
    info "$code — $name"
done <<EOF
$(echo "$SUPPLIER_ROWS" | sed '/^[[:space:]]*$/d')
EOF

# ── item ↔ supplier ──────────────────────────────────────────────────────────
# At most one preferred ACTIVE row per item (partial unique index uk_item_suppliers_preferred_active),
# so LOP-26's two sources have exactly one preferred between them.
log "Gán nhà cung cấp cho vật tư"
while IFS='|' read -r item supplier preferred lead moq price; do
    [ -z "$item" ] && continue
    call POST "/items/${ID[items.$item]}/suppliers" \
        "{\"supplierId\":\"${ID[suppliers.$supplier]}\",\"supplierItemCode\":\"$supplier-$item\",\"leadTimeDays\":$lead,\"minimumOrderQuantity\":$moq,\"unitPrice\":$price,\"currencyCode\":\"VND\",\"preferred\":$preferred}" \
        >/dev/null
done <<EOF
$(echo "$ITEM_SUPPLIER_ROWS" | sed '/^[[:space:]]*$/d')
EOF
info "đã gán $(echo "$ITEM_SUPPLIER_ROWS" | sed '/^[[:space:]]*$/d' | wc -l | tr -d ' ') liên kết vật tư ↔ nhà cung cấp"

log "Ngừng hợp tác với $SUPPLIER_RETIRED"
call PATCH "/suppliers/${ID[suppliers.$SUPPLIER_RETIRED]}/deactivate" >/dev/null
assert_status "/suppliers/${ID[suppliers.$SUPPLIER_RETIRED]}" "INACTIVE"

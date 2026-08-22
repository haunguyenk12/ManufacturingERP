#!/usr/bin/env bash
# 10-org — company, plants, warehouses, units of measure.

stage_banner "1. Công ty, nhà máy, kho, đơn vị tính"

# ── company ──────────────────────────────────────────────────────────────────
log "Tạo công ty $CO_CODE"
remember "company.id" "$(call POST /companies \
    "{\"code\":\"$CO_CODE\",\"name\":\"$CO_NAME\"}" | rid companyId)"
remember "company.code" "$CO_CODE"

# ── UTF-8 round trip ─────────────────────────────────────────────────────────
# The earliest possible moment to prove Vietnamese text survives shell -> curl -> Spring -> Postgres
# and back. FE's PowerShell seeder failed exactly here and left "BÃ ..." rows nobody noticed until
# the data was on a slide. Abort now, with 1 row written, rather than with 300.
ECHOED=$(call GET "/companies/${ID[company.id]}" | rid name)
if [ "$ECHOED" != "$CO_NAME" ]; then
    cat >&2 <<EOF

DỪNG: tên tiếng Việt bị hỏng mã hoá trên đường truyền.

    đã gửi : $CO_NAME
    nhận về: $ECHOED

Shell đang mã hoá sai. Chạy script bằng Git Bash (không dùng PowerShell/CMD),
và bảo đảm các file trong scripts/demo/ vẫn là UTF-8 không BOM.

EOF
    exit 1
fi
info "UTF-8 round-trip OK: $ECHOED"

# ── plants ───────────────────────────────────────────────────────────────────
# timezone is pinned to UTC on purpose: operation scheduling converts through Plant.timezone
# (WorkCalendarLookupService.computeEndInstant) and the capacity board groups load by LOCAL day.
# Keeping the plant on UTC makes those days line up with the dates computed here with `date -u`.
for row in "$P1_CODE|$P1_NAME" "$P2_CODE|$P2_NAME"; do
    code="${row%%|*}"; name="${row#*|}"
    log "Tạo nhà máy $code"
    pid=$(call POST "/companies/${ID[company.id]}/plants" \
        "{\"code\":\"$code\",\"name\":\"$name\",\"timezone\":\"UTC\"}" | rid plantId)
    remember "plants.$code.id" "$pid"

    # ── warehouses ───────────────────────────────────────────────────────────
    while IFS='|' read -r wcode wname wtype; do
        [ -z "$wcode" ] && continue
        wid=$(call POST "/plants/$pid/warehouses" \
            "{\"code\":\"$wcode\",\"name\":\"$wname\",\"type\":\"$wtype\"}" | rid warehouseId)
        remember "plants.$code.warehouses.$wcode" "$wid"
        info "kho $wcode ($wtype)"
    done <<EOF
$WH_NVL|$WH_NVL_NAME|RAW_MATERIAL
$WH_BTP|$WH_BTP_NAME|WIP
$WH_TP|$WH_TP_NAME|FINISHED_GOODS
EOF
done

# One extra warehouse in HANOI only, deactivated straight away, so the warehouse list has an
# INACTIVE row to look at.
log "Tạo kho cách ly $WH_KIEMDINH rồi ngừng hoạt động (để danh sách kho có trạng thái INACTIVE)"
KD_ID=$(call POST "/plants/${ID[plants.$P1_CODE.id]}/warehouses" \
    "{\"code\":\"$WH_KIEMDINH\",\"name\":\"$WH_KIEMDINH_NAME\",\"type\":\"QUALITY\"}" | rid warehouseId)
call DELETE "/warehouses/$KD_ID" >/dev/null
assert_status "/warehouses/$KD_ID" "INACTIVE"
remember "plants.$P1_CODE.warehouses.$WH_KIEMDINH" "$KD_ID"

# ── units of measure (global, not company-scoped) ────────────────────────────
log "Tạo đơn vị tính"
while IFS='|' read -r ucode uname udesc; do
    [ -z "$ucode" ] && continue
    uid=$(call POST /uoms \
        "{\"code\":\"$ucode\",\"name\":\"$uname\",\"description\":\"$udesc\"}" | rid uomId)
    remember "uoms.$ucode" "$uid"
    info "đơn vị $ucode — $uname"
done <<EOF
$(echo "$UOM_ROWS" | sed '/^[[:space:]]*$/d')
EOF

log "Ngừng dùng đơn vị $UOM_RETIRED"
call POST "/uoms/${ID[uoms.$UOM_RETIRED]}/deactivate" >/dev/null
assert_status "/uoms/${ID[uoms.$UOM_RETIRED]}" "INACTIVE"

# Item.unit is a free String(30), NOT a foreign key to uoms. The two are kept consistent here by
# convention only — do not read this as a link the schema enforces.

#!/usr/bin/env bash
# 30-shopfloor — work centers, shifts, work calendars.
#
# 🔴 Order is forced by a circular dependency: WorkCenter can point at a WorkCalendar, and a
# WorkCalendar's weekly rows point at Shifts of the same plant. So: work centers WITHOUT a calendar
# -> shifts -> calendars -> PATCH the calendar onto the work centers.
#
# And it only works in that direction. WorkCenterUpdateRequest reads null as "leave unchanged", so
# a calendar can never be detached once attached — TO-KCS has to be created without one and left
# alone, which is what gives the capacity board a work center reporting null capacity.

stage_banner "4. Tổ sản xuất, ca làm việc, lịch làm việc"

P1="${ID[plants.$P1_CODE.id]}"
P2="${ID[plants.$P2_CODE.id]}"

# ── work centers (no calendar yet) ───────────────────────────────────────────
log "Tạo tổ sản xuất tại $P1_CODE"
while IFS='|' read -r code name captype capunits calcode; do
    [ -z "$code" ] && continue
    wcid=$(call POST "/plants/$P1/work-centers" \
        "{\"code\":\"$code\",\"name\":\"$name\",\"description\":\"Tổ sản xuất demo\",\"capacityUnitType\":\"$captype\",\"capacityUnits\":$capunits}" \
        | rid workCenterId)
    remember "workCenters.$P1_CODE.$code" "$wcid"
    info "$code — $name ($captype x$capunits)"
done <<EOF
$(echo "$WC_ROWS" | sed '/^[[:space:]]*$/d')
EOF

log "Tạo tổ sản xuất tại $P2_CODE"
for row in "TO-LAPRAP|Tổ lắp ráp Sài Gòn|LINE|1" "TO-KCS|Tổ KCS Sài Gòn|LABOR_TEAM|1"; do
    IFS='|' read -r code name captype capunits <<<"$row"
    wcid=$(call POST "/plants/$P2/work-centers" \
        "{\"code\":\"$code\",\"name\":\"$name\",\"description\":\"Tổ sản xuất demo\",\"capacityUnitType\":\"$captype\",\"capacityUnits\":$capunits}" \
        | rid workCenterId)
    remember "workCenters.$P2_CODE.$code" "$wcid"
done

# ── shifts ───────────────────────────────────────────────────────────────────
log "Tạo ca làm việc tại $P1_CODE"
while IFS='|' read -r code name start end breaks; do
    [ -z "$code" ] && continue
    bjson="[]"
    if [ "$breaks" != "-" ]; then
        bjson="["
        first=1
        IFS=',' read -ra blist <<<"$breaks"
        for b in "${blist[@]}"; do
            [ $first -eq 0 ] && bjson="$bjson,"
            bjson="$bjson{\"startTime\":\"${b%%-*}\",\"endTime\":\"${b#*-}\"}"
            first=0
        done
        bjson="$bjson]"
    fi
    sid=$(call POST "/plants/$P1/shifts" \
        "{\"code\":\"$code\",\"name\":\"$name\",\"startTime\":\"$start\",\"endTime\":\"$end\",\"breaks\":$bjson}" \
        | rid shiftId)
    remember "shifts.$P1_CODE.$code" "$sid"
    if [[ "$end" < "$start" ]]; then
        info "$code — $name ($start → $end, ca qua đêm)"
    else
        info "$code — $name ($start → $end)"
    fi
done <<EOF
$(echo "$SHIFT_ROWS" | sed '/^[[:space:]]*$/d')
EOF

log "Tạo ca hành chính tại $P2_CODE"
remember "shifts.$P2_CODE.CA-HANHCHINH" "$(call POST "/plants/$P2/shifts" \
    "{\"code\":\"CA-HANHCHINH\",\"name\":\"Ca hành chính\",\"startTime\":\"08:00\",\"endTime\":\"17:00\",\"breaks\":[{\"startTime\":\"12:00\",\"endTime\":\"13:00\"}]}" \
    | rid shiftId)"

# ── work calendars ───────────────────────────────────────────────────────────
# weekly_json PLANT_CODE "WEEKDAY,WEEKDAY,..." "SHIFTCODE,SHIFTCODE,..."
# A weekday may legitimately carry more than one shift — the unique key is
# (work_calendar_id, weekday, shift_id), not (work_calendar_id, weekday).
weekly_json() {
    local plant="$1" out="[" first=1 wd sc
    IFS=',' read -ra days <<<"$2"
    IFS=',' read -ra shifts <<<"$3"
    for wd in "${days[@]}"; do
        for sc in "${shifts[@]}"; do
            [ $first -eq 0 ] && out="$out,"
            out="$out{\"weekday\":\"$wd\",\"shiftId\":\"${ID[shifts.$plant.$sc]}\"}"
            first=0
        done
    done
    echo "$out]"
}

EXCEPTIONS="[{\"exceptionDate\":\"$(d_plus 7)\",\"reason\":\"Bảo dưỡng máy định kỳ\"},{\"exceptionDate\":\"$(d_plus 21)\",\"reason\":\"Nghỉ lễ toàn nhà máy\"}]"

log "Tạo lịch làm việc"
remember "workCalendars.$P1_CODE.$CAL_2CA" "$(call POST "/plants/$P1/work-calendars" \
    "{\"code\":\"$CAL_2CA\",\"name\":\"$CAL_2CA_NAME\",\"effectiveFrom\":\"$(d_minus 90)\",\"weeklyShifts\":$(weekly_json "$P1_CODE" "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY" "CA-SANG,CA-CHIEU"),\"exceptions\":$EXCEPTIONS}" \
    | rid workCalendarId)"
info "$CAL_2CA — $CAL_2CA_NAME (2 ca x 6 ngày, 2 ngày nghỉ đặc biệt)"

remember "workCalendars.$P1_CODE.$CAL_3CA" "$(call POST "/plants/$P1/work-calendars" \
    "{\"code\":\"$CAL_3CA\",\"name\":\"$CAL_3CA_NAME\",\"effectiveFrom\":\"$(d_minus 90)\",\"weeklyShifts\":$(weekly_json "$P1_CODE" "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY" "CA-SANG,CA-CHIEU,CA-DEM"),\"exceptions\":[]}" \
    | rid workCalendarId)"
info "$CAL_3CA — $CAL_3CA_NAME (gồm cả ca đêm)"

remember "workCalendars.$P2_CODE.$CAL_SG" "$(call POST "/plants/$P2/work-calendars" \
    "{\"code\":\"$CAL_SG\",\"name\":\"$CAL_SG_NAME\",\"effectiveFrom\":\"$(d_minus 90)\",\"weeklyShifts\":$(weekly_json "$P2_CODE" "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY" "CA-HANHCHINH"),\"exceptions\":[]}" \
    | rid workCalendarId)"

# ── attach calendars (the second half of the circular dependency) ────────────
log "Gắn lịch làm việc vào tổ sản xuất"
while IFS='|' read -r code name captype capunits calcode; do
    [ -z "$code" ] && continue
    if [ "$calcode" = "-" ]; then
        info "$code — cố ý KHÔNG gắn lịch (bảng năng lực sẽ báo capacity null)"
        continue
    fi
    call PATCH "/work-centers/${ID[workCenters.$P1_CODE.$code]}" \
        "{\"workCalendarId\":\"${ID[workCalendars.$P1_CODE.$calcode]}\"}" >/dev/null
    info "$code -> $calcode"
done <<EOF
$(echo "$WC_ROWS" | sed '/^[[:space:]]*$/d')
EOF

call PATCH "/work-centers/${ID[workCenters.$P2_CODE.TO-LAPRAP]}" \
    "{\"workCalendarId\":\"${ID[workCalendars.$P2_CODE.$CAL_SG]}\"}" >/dev/null

log "Ngừng dùng tổ $WC_RETIRED"
call POST "/work-centers/${ID[workCenters.$P1_CODE.$WC_RETIRED]}/deactivate" >/dev/null
assert_status "/work-centers/${ID[workCenters.$P1_CODE.$WC_RETIRED]}" "INACTIVE"

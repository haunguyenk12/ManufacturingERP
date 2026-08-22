#!/usr/bin/env bash
# 40-bom-routing — multi-level BOMs and routings.
#
# Two absences in here are load-bearing and must survive any future edit:
#   BO-TRUYEN-7 has NO BOM      -> it is WIP, so MRP calls it MAKE, and MISSING_BOM makes its
#                                  proposal BLOCKED at level 1 of the explosion.
#   XE-TRE-EM   has NO routing  -> MISSING_ROUTING blocks its proposal at level 0.
# Between them the suggestion list shows both ways a proposal can be blocked.

stage_banner "5. Định mức vật tư (BOM) và quy trình công nghệ"

CO="${ID[company.id]}"

# ── BOMs ─────────────────────────────────────────────────────────────────────
# Headers are created DRAFT; lines are added one call each; activation is a separate call.
# Only one revision per parent item may be ACTIVE, so XE-PHO-26 keeps rev A active and rev B draft.
log "Tạo định mức vật tư"
while IFS='|' read -r parent revision activate lines; do
    [ -z "$parent" ] && continue
    bid=$(call POST "/companies/$CO/boms" \
        "{\"parentItemId\":\"${ID[items.$parent]}\",\"revision\":\"$revision\",\"description\":\"Định mức sản xuất $parent bản $revision\"}" \
        | rid bomId)
    remember "boms.$parent.$revision" "$bid"

    lineno=1
    IFS=';' read -ra lrows <<<"$lines"
    for l in "${lrows[@]}"; do
        IFS=':' read -r comp qty scrap <<<"$l"
        call POST "/boms/$bid/lines" \
            "{\"componentItemId\":\"${ID[items.$comp]}\",\"lineNo\":$lineno,\"quantityPer\":$qty,\"scrapRate\":$scrap}" \
            >/dev/null
        lineno=$((lineno + 1))
    done

    if [ "$activate" = "YES" ]; then
        call POST "/boms/$bid/activate" >/dev/null
        assert_status "/boms/$bid" "ACTIVE"
        info "$parent bản $revision — ACTIVE ($((lineno - 1)) dòng)"
    else
        info "$parent bản $revision — giữ DRAFT ($((lineno - 1)) dòng)"
    fi
done <<EOF
$(echo "$BOM_ROWS" | sed '/^[[:space:]]*$/d')
EOF

# ── routings ─────────────────────────────────────────────────────────────────
# Operations are supplied inline on create (there are no routing-line endpoints), and every
# operation of one routing must sit in the same plant — B_wc2. All of these use HANOI work centers.
log "Tạo quy trình công nghệ"
while IFS='|' read -r code version item activate ops; do
    [ -z "$code" ] && continue
    opjson="["
    first=1
    IFS=';' read -ra oplist <<<"$ops"
    for o in "${oplist[@]}"; do
        IFS=':' read -r seq name wc setup runmin <<<"$o"
        [ $first -eq 0 ] && opjson="$opjson,"
        opjson="$opjson{\"sequence\":$seq,\"name\":\"$name\",\"workCenterId\":\"${ID[workCenters.$P1_CODE.$wc]}\",\"setupMinutes\":$setup,\"runMinutesPerUnit\":$runmin}"
        first=0
    done
    opjson="$opjson]"

    rid_=$(call POST "/companies/$CO/routings" \
        "{\"itemId\":\"${ID[items.$item]}\",\"code\":\"$code\",\"version\":\"$version\",\"note\":\"Quy trình demo cho $item\",\"operations\":$opjson}" \
        | rid routingId)
    remember "routings.$code.$version" "$rid_"

    if [ "$activate" = "YES" ]; then
        call POST "/routings/$rid_/activate" >/dev/null
        assert_status "/routings/$rid_" "ACTIVE"
        info "$code bản $version ($item) — ACTIVE"
    else
        info "$code bản $version ($item) — giữ DRAFT"
    fi
done <<EOF
$(echo "$ROUTING_ROWS" | sed '/^[[:space:]]*$/d')
EOF

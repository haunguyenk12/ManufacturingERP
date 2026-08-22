#!/usr/bin/env bash
# 85-planning — two MRP runs, and supply suggestions across every status and exception state.

stage_banner "11. Hoạch định nhu cầu vật tư (MRP)"

CO="${ID[company.id]}"
P1="${ID[plants.$P1_CODE.id]}"
WH_TP_HN="${ID[plants.$P1_CODE.warehouses.$WH_TP]}"

# ── find the demand rows the confirmed orders generated ──────────────────────
# GET /planning/demands returns planningDemandId directly, so there is no need to go near the
# database for it (the gap FE hit on GET /sales-orders/planning-demands, debt #21).
DEMANDS=$(call GET "/planning/demands?companyId=$CO&plantId=$P1&status=OPEN&page=0&size=100")

demand_for() {  # demand_for SALES_ORDER_NO -> planningDemandId
    echo "$DEMANDS" | extract "next(x['planningDemandId'] for x in d['result']['content'] if x['referenceId']=='${ID[salesOrders.$1.lineId]}')"
}

D1004=$(demand_for SO-1004)
D1005=$(demand_for SO-1005)
D1006=$(demand_for SO-1006)
D1007=$(demand_for SO-1007)
info "đã lấy 4 dòng nhu cầu từ SO-1004/1005/1006/1007"

# SO-1002's demand is intentionally excluded — it stays OPEN and unplanned.
# SO-1003's demand is CANCELLED; feeding a non-OPEN demand id to a run is rejected with 409 before
# the run row is even written (B60), which would abort this stage.

# ── run A: scoped to the finished-goods warehouse ────────────────────────────
# The warehouse choice is what produces two different exception states from one run. A requirement
# is flagged SYSTEM_FALLBACK_USED (=> WARNING) when the item has no ACTIVE ItemWarehouseSetting for
# THE RUN'S warehouse. Stage 20 created exactly one such row, for XE-PHO-26 at KHO-TP, so:
#     XE-PHO-26  -> READY      (setting exists)
#     XE-DIA-275 -> WARNING    (no setting at KHO-TP)
#     XE-TRE-EM  -> BLOCKED    (MISSING_ROUTING)
#     BO-TRUYEN-7-> BLOCKED    (MISSING_BOM, reached at level 1 of the explosion)
log "Chạy MRP lượt A (phạm vi kho thành phẩm)"
RUN_A=$(call POST /planning-runs \
    "{\"companyId\":\"$CO\",\"plantId\":\"$P1\",\"warehouseId\":\"$WH_TP_HN\",\"horizonStartDate\":\"$(d_today)\",\"horizonEndDate\":\"$(d_plus 90)\",\"demandLineIds\":[\"$D1004\",\"$D1005\",\"$D1006\",\"$D1007\"]}" \
    -H "Idempotency-Key: DEMO-MRP-LUOT-A")
RUN_A_ID=$(echo "$RUN_A" | rid mrpRunId)
RUN_A_CODE=$(echo "$RUN_A" | rid code)
remember "planningRuns.A.id" "$RUN_A_ID"
remember "planningRuns.A.code" "$RUN_A_CODE"
assert_status "/planning-runs/$RUN_A_ID" "COMPLETED"
info "$RUN_A_CODE — $(echo "$RUN_A" | rid totalRequirementLines) dòng nhu cầu, $(echo "$RUN_A" | rid totalSuggestionLines) đề xuất, $(echo "$RUN_A" | rid blockedProposals) bị chặn"

SUGG=$(call GET "/planning-runs/$RUN_A_ID/suggestions?page=0&size=100")

# find_sugg ITEM_SKU [SUPPLY_TYPE] -> supplySuggestionId ("" when absent)
find_sugg() {
    local sku="$1" stype="${2:-}"
    if [ -n "$stype" ]; then
        echo "$SUGG" | extract "next((s['supplySuggestionId'] for s in d['result']['content'] if s['itemSku']=='$sku' and s['supplyType']=='$stype'), '')"
    else
        echo "$SUGG" | extract "next((s['supplySuggestionId'] for s in d['result']['content'] if s['itemSku']=='$sku'), '')"
    fi
}
sugg_state() {
    echo "$SUGG" | extract "next(s['exceptionState'] for s in d['result']['content'] if s['supplySuggestionId']=='$1')"
}

info "phân bố trạng thái ngoại lệ: $(echo "$SUGG" | extract "', '.join('%s=%d' % (k, sum(1 for s in d['result']['content'] if s['exceptionState']==k)) for k in ('READY','WARNING','BLOCKED'))")"
info "phân bố loại cung ứng:      $(echo "$SUGG" | extract "', '.join('%s=%d' % (k, sum(1 for s in d['result']['content'] if s['supplyType']==k)) for k in ('MAKE','BUY'))")"

# ── a BLOCKED proposal must refuse to convert ────────────────────────────────
S_TREEM=$(find_sugg "XE-TRE-EM" "MAKE")
if [ -n "$S_TREEM" ]; then
    STATE=$(sugg_state "$S_TREEM")
    [ "$STATE" = "BLOCKED" ] || die "đề xuất XE-TRE-EM phải ở trạng thái BLOCKED, đang là $STATE"
    remember "supplySuggestions.BLOCKED" "$S_TREEM"
    log "Chứng minh đề xuất BLOCKED không convert được"
    call POST "/supply-suggestions/$S_TREEM/approve" \
        '{"decisionNote":"Duyệt về mặt nhu cầu, nhưng còn thiếu quy trình công nghệ"}' >/dev/null
    call_expect MISSING_ROUTING POST "/supply-suggestions/$S_TREEM/convert-to-work-order" \
        "{\"outputWarehouseId\":\"$WH_TP_HN\"}" >/dev/null
else
    warn "không tìm thấy đề xuất cho XE-TRE-EM — bỏ qua phần chứng minh BLOCKED"
fi

# ── one approved-but-not-converted, one rejected, the rest left DRAFT ────────
S_NANHOA=$(find_sugg "NAN-HOA" "BUY")
if [ -n "$S_NANHOA" ]; then
    call POST "/supply-suggestions/$S_NANHOA/approve" \
        '{"decisionNote":"Duyệt bổ sung nan hoa cho kế hoạch quý 4"}' >/dev/null
    remember "supplySuggestions.APPROVED" "$S_NANHOA"
    info "NAN-HOA -> APPROVED (dừng lại, chưa convert)"
fi

S_OCVIT=$(find_sugg "OC-VIT-M6" "BUY")
if [ -n "$S_OCVIT" ]; then
    call POST "/supply-suggestions/$S_OCVIT/reject" \
        '{"decisionNote":"Tồn kho ốc vít hiện tại đã đủ cho đợt này"}' >/dev/null
    remember "supplySuggestions.REJECTED" "$S_OCVIT"
    info "OC-VIT-M6 -> REJECTED"
fi

# ── convert the MAKE proposals into work orders ──────────────────────────────
# Converting flips the parent sales order to IN_PRODUCTION and creates the fulfilment allocation
# that stage 90 later settles. workOrderNo is passed explicitly so the demo has readable numbers
# instead of server-generated ones.
# convert_make ITEM_SKU WO_NO SO_NO
#
# Must run in the current shell, not inside $(...): it both calls `remember` and rewrites SUGG, and
# neither would survive a subshell — the work order would exist in the database while the manifest
# and the local "already converted" bookkeeping stayed empty.
convert_make() {
    local sku="$1" wono="$2" sono="$3" sid
    sid=$(echo "$SUGG" | extract "next((s['supplySuggestionId'] for s in d['result']['content'] if s['itemSku']=='$sku' and s['supplyType']=='MAKE' and s['exceptionState']!='BLOCKED' and s['status']=='DRAFT'), '')")
    if [ -z "$sid" ]; then
        warn "không còn đề xuất MAKE khả dụng cho $sku — bỏ qua $wono"
        NEW_ID=""
        return
    fi
    call POST "/supply-suggestions/$sid/approve" \
        "{\"decisionNote\":\"Duyệt chuyển thành lệnh sản xuất $wono\"}" >/dev/null
    NEW_ID=$(call POST "/supply-suggestions/$sid/convert-to-work-order" \
        "{\"workOrderNo\":\"$wono\",\"outputWarehouseId\":\"$WH_TP_HN\",\"plannedStartAt\":\"$(ts_at 3 06)\",\"plannedEndAt\":\"$(ts_at 8 22)\",\"notes\":\"Sinh từ lượt MRP $RUN_A_CODE cho $sono\"}" \
        | rid convertedWorkOrderId)
    remember "supplySuggestions.CONVERTED_$wono" "$sid"
    remember "workOrders.$wono" "$NEW_ID"
    # Two of these convert the same SKU (XE-PHO-26, once for SO-1004 and once for SO-1006), so the
    # one just consumed has to be struck off the local copy or the second call would pick it again.
    SUGG=$(echo "$SUGG" | "$PY" -c "
import json,sys
d=json.load(sys.stdin)
for s in d['result']['content']:
    if s['supplySuggestionId']=='$sid':
        s['status']='CONVERTED'
print(json.dumps(d))
")
    info "$sku -> $wono"
}

log "Chuyển đề xuất MAKE thành lệnh sản xuất"
convert_make "XE-PHO-26"  "WO-2610" "SO-1004"
convert_make "XE-DIA-275" "WO-2611" "SO-1005"
convert_make "XE-PHO-26"  "WO-2612" "SO-1006"

# ── convert one BUY proposal into a purchase requisition ────────────────────
S_ONGNHOM=$(find_sugg "ONG-NHOM" "BUY")
if [ -n "$S_ONGNHOM" ]; then
    call POST "/supply-suggestions/$S_ONGNHOM/approve" \
        '{"decisionNote":"Duyệt mua ống nhôm bổ sung"}' >/dev/null
    remember "purchaseRequisitions.PR-2003" "$(call POST "/supply-suggestions/$S_ONGNHOM/convert-to-purchase-requisition" \
        "{\"requisitionNo\":\"PR-2003\",\"warehouseId\":\"${ID[plants.$P1_CODE.warehouses.$WH_NVL]}\",\"supplierId\":\"${ID[suppliers.NCC-NHOM]}\",\"note\":\"Phát sinh từ thiếu hụt của lượt $RUN_A_CODE\"}" \
        | rid purchaseRequisitionId)"
    remember "supplySuggestions.CONVERTED_PR" "$S_ONGNHOM"
    info "ONG-NHOM -> phiếu đề nghị mua hàng PR-2003"
fi

# ── run B: plant-wide, horizon sweep ─────────────────────────────────────────
# No warehouseId and no demandLineIds. Every raw material lives in KHO-NVL, so netting plant-wide
# covers most component lines — the deliberate contrast with run A, and it gives the run list two
# rows whose summary counters look nothing alike.
log "Chạy MRP lượt B (toàn nhà máy, quét theo horizon)"
RUN_B=$(call POST /planning-runs \
    "{\"companyId\":\"$CO\",\"plantId\":\"$P1\",\"horizonStartDate\":\"$(d_today)\",\"horizonEndDate\":\"$(d_plus 90)\"}" \
    -H "Idempotency-Key: DEMO-MRP-LUOT-B-TOANNHAMAY")
remember "planningRuns.B.id" "$(echo "$RUN_B" | rid mrpRunId)"
remember "planningRuns.B.code" "$(echo "$RUN_B" | rid code)"
info "$(echo "$RUN_B" | rid code) — $(echo "$RUN_B" | rid totalRequirementLines) dòng nhu cầu, $(echo "$RUN_B" | rid shortageLines) dòng thiếu hụt"

# Idempotency keys here are intent-based (DEMO-MRP-LUOT-A / -B), never timestamped: replaying the
# same key with the same payload returns the same run instead of creating a duplicate. FE created
# three identical runs by not having this, and the duplicates then looked like a transaction bug.

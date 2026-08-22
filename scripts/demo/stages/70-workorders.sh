#!/usr/bin/env bash
# 70-workorders — every WorkOrderStatus, plus every ProductionReceiptStatus.
#
# Each step reads the state back with assert_status. That is not belt-and-braces: WorkOrderService
# .release() runs three guards and TWO of them throw the same STATE_CONFLICT code, but only the
# material-readiness gate persists BLOCKED. A 409 on its own would let this script claim success
# while leaving WO-2603 sitting in DRAFT.

stage_banner "8. Lệnh sản xuất — đủ 8 trạng thái"

P1="${ID[plants.$P1_CODE.id]}"
P2="${ID[plants.$P2_CODE.id]}"
WH_BTP_HN="${ID[plants.$P1_CODE.warehouses.$WH_BTP]}"
WH_TP_HN="${ID[plants.$P1_CODE.warehouses.$WH_TP]}"

# wo_create PLANT_ID NO ITEM_CODE QTY OUT_WAREHOUSE_ID [PLANNED_START] [PLANNED_END]
#
# Sets the global NEW_ID rather than echoing it. Calling this in $(...) would run `remember` inside
# a subshell, so the id would land in the manifest of a shell that exits one line later — the
# entity would exist in the database and be missing from demo-ids.json.
wo_create() {
    local plant="$1" no="$2" item="$3" qty="$4" wh="$5" pstart="${6:-}" pend="${7:-}" body
    body="{\"workOrderNo\":\"$no\",\"productItemId\":\"${ID[items.$item]}\",\"outputWarehouseId\":\"$wh\",\"plannedQuantity\":$qty"
    [ -n "$pstart" ] && body="$body,\"plannedStartAt\":\"$pstart\""
    [ -n "$pend" ]   && body="$body,\"plannedEndAt\":\"$pend\""
    body="$body,\"notes\":\"Bộ dữ liệu demo\"}"
    NEW_ID=$(call POST "/plants/$plant/work-orders" "$body" | rid workOrderId)
    remember "workOrders.$no" "$NEW_ID"
}

wo_execute() {  # wo_execute WO_ID KEY GOOD SCRAP
    call POST "/work-orders/$1/production-executions" \
        "{\"goodQuantity\":$3,\"scrapQuantity\":$4,\"reworkQuantity\":0,\"actualStartedAt\":\"$(ts_ago 6)\",\"actualEndedAt\":\"$(ts_ago 1)\",\"notes\":\"Báo cáo sản lượng của bộ dữ liệu demo\"}" \
        -H "Idempotency-Key: $2" >/dev/null
}

# ── DRAFT ────────────────────────────────────────────────────────────────────
log "WO-2601 — DRAFT"
wo_create "$P1" "WO-2601" "KHUNG-PHO" 12 "$WH_BTP_HN"; WO2601="$NEW_ID"
assert_status "/work-orders/$WO2601" "DRAFT"

# ── PLANNED ──────────────────────────────────────────────────────────────────
log "WO-2602 — PLANNED"
wo_create "$P1" "WO-2602" "BANH-26" 10 "$WH_BTP_HN"; WO2602="$NEW_ID"
call POST "/work-orders/$WO2602/plan" >/dev/null
assert_status "/work-orders/$WO2602" "PLANNED"

# ── BLOCKED ──────────────────────────────────────────────────────────────────
# XE-TRE-EM's BOM needs BANH-20, which stage 60 left out of every warehouse of the plant.
# Never call /reserve on this one — a reservation is exactly what would unblock it.
log "WO-2603 — BLOCKED (thiếu BANH-20, cố ý không nhập kho ở bất kỳ đâu)"
wo_create "$P1" "WO-2603" "XE-TRE-EM" 8 "$WH_TP_HN"; WO2603="$NEW_ID"
call POST "/work-orders/$WO2603/plan" >/dev/null
call_expect STATE_CONFLICT POST "/work-orders/$WO2603/release" >/dev/null
assert_status "/work-orders/$WO2603" "BLOCKED"

# ── RELEASED (x2, colliding on TO-HAN so the capacity board has an overloaded day) ──
# plannedStartAt is explicit on purpose, and anchored on a Monday rather than "today + N": without a
# start the first operation anchors at now(), and with a fixed offset the anchor drifts onto a
# Sunday whenever the seed runs late in the week — which flags an overload for the boring reason
# (nobody works that day) instead of the interesting one (two work orders want the same tổ).
COLLIDE_START="$(ts_next_monday 06)"
log "WO-2604 — RELEASED (bắt đầu $COLLIDE_START)"
wo_create "$P1" "WO-2604" "KHUNG-DIA" 10 "$WH_BTP_HN" "$COLLIDE_START" "$(ts_at 5 22)"; WO2604="$NEW_ID"
call POST "/work-orders/$WO2604/reserve" >/dev/null
call POST "/work-orders/$WO2604/release" >/dev/null
assert_status "/work-orders/$WO2604" "RELEASED"

log "WO-2609 — RELEASED (cùng ngày với WO-2604 trên tổ TO-HAN ⇒ bảng năng lực báo quá tải)"
wo_create "$P1" "WO-2609" "KHUNG-PHO" 10 "$WH_BTP_HN" "$COLLIDE_START" "$(ts_at 5 22)"; WO2609="$NEW_ID"
call POST "/work-orders/$WO2609/reserve" >/dev/null
call POST "/work-orders/$WO2609/release" >/dev/null
assert_status "/work-orders/$WO2609" "RELEASED"

# ── IN_PROGRESS ──────────────────────────────────────────────────────────────
log "WO-2605 — IN_PROGRESS (báo 10/20, còn dư trần để tạo đủ 4 trạng thái phiếu nhập)"
wo_create "$P1" "WO-2605" "BANH-275" 20 "$WH_BTP_HN" "$(ts_at 1 06)"; WO2605="$NEW_ID"
RES2605=$(call POST "/work-orders/$WO2605/reserve")
call POST "/work-orders/$WO2605/release" >/dev/null
wo_execute "$WO2605" "DEMO-SANLUONG-WO-2605" 10 1
assert_status "/work-orders/$WO2605" "IN_PROGRESS"

# One material issue so the material-issue screens are not empty before stage 90 runs.
RES_ID=$(echo "$RES2605" | extract "d['result'][0]['reservationId']")
RES_QTY=$(echo "$RES2605" | extract "d['result'][0]['quantity']")
ISSUE_QTY=$(num "round(float('$RES_QTY')/2, 3)")
log "Xuất vật tư một phần cho WO-2605 ($ISSUE_QTY / $RES_QTY)"
call POST /material-issues \
    "{\"workOrderId\":\"$WO2605\",\"reservationId\":\"$RES_ID\",\"quantity\":$ISSUE_QTY,\"reason\":\"Xuất đợt một cho bộ dữ liệu demo\"}" \
    -H "Idempotency-Key: DEMO-XUAT-WO-2605-1" >/dev/null

# ── COMPLETED ────────────────────────────────────────────────────────────────
# COMPLETED is reached by reporting output (B53), never by a receipt.
log "WO-2606 — COMPLETED"
wo_create "$P1" "WO-2606" "XE-DIA-275" 5 "$WH_TP_HN" "$(ts_at 1 06)"; WO2606="$NEW_ID"
call POST "/work-orders/$WO2606/reserve" >/dev/null
call POST "/work-orders/$WO2606/release" >/dev/null
wo_execute "$WO2606" "DEMO-SANLUONG-WO-2606" 5 0
assert_status "/work-orders/$WO2606" "COMPLETED"

# ── CLOSED ───────────────────────────────────────────────────────────────────
# /close only accepts COMPLETED, and it releases whatever is left of the reservations.
log "WO-2607 — CLOSED"
wo_create "$P1" "WO-2607" "BANH-26" 6 "$WH_BTP_HN" "$(ts_at 1 06)"; WO2607="$NEW_ID"
call POST "/work-orders/$WO2607/reserve" >/dev/null
call POST "/work-orders/$WO2607/release" >/dev/null
wo_execute "$WO2607" "DEMO-SANLUONG-WO-2607" 6 0
assert_status "/work-orders/$WO2607" "COMPLETED"
call POST "/work-orders/$WO2607/close" >/dev/null
assert_status "/work-orders/$WO2607" "CLOSED"

# ── CANCELLED ────────────────────────────────────────────────────────────────
# Must stay untouched: cancel refuses once the work order has any issued or produced quantity.
log "WO-2608 — CANCELLED (giữ sạch, không xuất/không báo sản lượng)"
wo_create "$P1" "WO-2608" "KHUNG-PHO" 4 "$WH_BTP_HN"; WO2608="$NEW_ID"
call POST "/work-orders/$WO2608/plan" >/dev/null
call POST "/work-orders/$WO2608/cancel" \
    '{"reason":"Khách hàng dời kế hoạch sang quý sau"}' >/dev/null
assert_status "/work-orders/$WO2608" "CANCELLED"

# ── serial-tracked output ────────────────────────────────────────────────────
log "WO-2620 — IN_PROGRESS, sản phẩm quản lý theo số sê-ri"
wo_create "$P1" "WO-2620" "XE-DIEN" 4 "$WH_TP_HN" "$(ts_at 1 06)"; WO2620="$NEW_ID"
call POST "/work-orders/$WO2620/reserve" >/dev/null
call POST "/work-orders/$WO2620/release" >/dev/null
wo_execute "$WO2620" "DEMO-SANLUONG-WO-2620" 2 0
assert_status "/work-orders/$WO2620" "IN_PROGRESS"

# ── plant isolation ──────────────────────────────────────────────────────────
# Left at PLANNED on purpose: releasing it would schedule operations from a routing whose work
# centers live in HANOI, which would put Sai Gon's load on another plant's capacity board.
log "WO-2650 — PLANNED tại $P2_CODE (dùng để chứng minh cô lập theo nhà máy)"
wo_create "$P2" "WO-2650" "XE-DIA-275" 3 "${ID[plants.$P2_CODE.warehouses.$WH_TP]}"; WO2650="$NEW_ID"
call POST "/work-orders/$WO2650/plan" >/dev/null
call POST "/work-orders/$WO2650/reserve" >/dev/null
assert_status "/work-orders/$WO2650" "PLANNED"

################################################################################
stage_banner "9. Phiếu nhập thành phẩm — đủ 4 trạng thái, lô đủ 3 trạng thái"
################################################################################

# All six sit on WO-2605, which reported good=10 and has claimed nothing yet. The ceiling for any
# one receipt is actualGood − completedQuantity − Σ(DRAFT + PENDING_APPROVAL) (B16); six receipts of
# 1 unit each stay comfortably inside it. Lower that good=10 and the last ones start returning 409.

# assert_receipt WO_ID RECEIPT_ID FIELD EXPECTED
#
# There is no GET for a single production receipt — only the per-work-order list — so the read-back
# goes through the list and picks the row out. Worth the extra call: without reading the state back,
# a submit/approve/reject chain that silently stopped working would still look like a clean run.
assert_receipt() {
    local wo="$1" rcp="$2" field="$3" expected="$4" actual
    actual=$(call GET "/work-orders/$wo/production-receipts?page=0&size=100" \
        | extract "next(r['$field'] for r in d['result']['content'] if r['receiptId']=='$rcp')")
    if [ "$actual" != "$expected" ]; then
        echo "STATE ASSERTION FAILED: receipt $rcp -> $field = '$actual', expected '$expected'" >&2
        exit 1
    fi
    info "ok: $field=$expected"
}

receipt_post() {  # receipt_post WO_ID KEY LOT QTY -> receiptId
    call POST "/work-orders/$1/production-receipts" \
        "{\"destinationWarehouseId\":\"$WH_BTP_HN\",\"lotNumber\":\"$3\",\"quantity\":$4,\"note\":\"Bộ dữ liệu demo\"}" \
        -H "Idempotency-Key: $2" | rid receiptId
}

log "Phiếu nhập DRAFT"
R1=$(receipt_post "$WO2605" "DEMO-PHIEU-BANH275-NHAP" "LO-BANH275-NHAP" 1)
assert_receipt "$WO2605" "$R1" status "DRAFT"
remember "productionReceipts.DRAFT" "$R1"

log "Phiếu nhập PENDING_APPROVAL"
R2=$(receipt_post "$WO2605" "DEMO-PHIEU-BANH275-CHO" "LO-BANH275-CHO" 1)
call POST "/work-orders/$WO2605/production-receipts/$R2/submit" >/dev/null
assert_receipt "$WO2605" "$R2" status "PENDING_APPROVAL"
remember "productionReceipts.PENDING_APPROVAL" "$R2"

log "Phiếu nhập REJECTED"
R3=$(receipt_post "$WO2605" "DEMO-PHIEU-BANH275-TUCHOI" "LO-BANH275-TUCHOI" 1)
call POST "/work-orders/$WO2605/production-receipts/$R3/submit" >/dev/null
call POST "/work-orders/$WO2605/production-receipts/$R3/reject" \
    '{"reason":"Độ đảo vành vượt dung sai trên mẫu kiểm"}' >/dev/null
assert_receipt "$WO2605" "$R3" status "REJECTED"
remember "productionReceipts.REJECTED" "$R3"

# Approving is what moves stock and opens the lot at HOLD. Leaving one receipt here, with no QC
# verdict, is what puts a production lot in HOLD on the lot screen.
log "Phiếu nhập APPROVED, chưa QC ⇒ lô ở trạng thái HOLD"
R4=$(receipt_post "$WO2605" "DEMO-PHIEU-BANH275-GIU" "LO-BANH275-GIU" 1)
call POST "/work-orders/$WO2605/production-receipts/$R4/submit" >/dev/null
call POST "/work-orders/$WO2605/production-receipts/$R4/approve" >/dev/null
assert_receipt "$WO2605" "$R4" status "APPROVED"
assert_receipt "$WO2605" "$R4" outputLotStatus "HOLD"
remember "productionReceipts.APPROVED_CHUA_QC" "$R4"

log "Phiếu nhập APPROVED + QC đạt ⇒ lô AVAILABLE"
R5=$(receipt_post "$WO2605" "DEMO-PHIEU-BANH275-DAT" "LO-BANH275-DAT" 1)
call POST "/work-orders/$WO2605/production-receipts/$R5/submit" >/dev/null
call POST "/work-orders/$WO2605/production-receipts/$R5/approve" >/dev/null
call POST "/work-orders/$WO2605/production-receipts/$R5/qc-disposition" \
    '{"result":"AVAILABLE","reason":"Đạt kiểm tra kích thước và lực căng nan hoa"}' >/dev/null
assert_receipt "$WO2605" "$R5" outputLotStatus "AVAILABLE"
remember "productionReceipts.APPROVED_QC_DAT" "$R5"

log "Phiếu nhập APPROVED + QC loại ⇒ lô REJECTED"
R6=$(receipt_post "$WO2605" "DEMO-PHIEU-BANH275-LOI" "LO-BANH275-LOI" 1)
call POST "/work-orders/$WO2605/production-receipts/$R6/submit" >/dev/null
call POST "/work-orders/$WO2605/production-receipts/$R6/approve" >/dev/null
call POST "/work-orders/$WO2605/production-receipts/$R6/qc-disposition" \
    '{"result":"REJECTED","reason":"Lực căng nan hoa lệch chuẩn ở 4 nan"}' >/dev/null
assert_receipt "$WO2605" "$R6" outputLotStatus "REJECTED"
remember "productionReceipts.APPROVED_QC_LOAI" "$R6"

# ── serial output ────────────────────────────────────────────────────────────
# A serial is exactly one physical unit, so quantity must be 1 (B99). Serial output never sits in
# HOLD (B98) — a QC rejection withdraws it with an ADJUST_OUT instead.
log "Phiếu nhập theo số sê-ri"
for pair in "SN-XEDIEN-0001|AVAILABLE|Đạt kiểm tra điện và quãng đường thử" \
            "SN-XEDIEN-0002|REJECTED|Lỗi cảm biến trợ lực, loại khỏi tồn kho"; do
    IFS='|' read -r sn verdict reason <<<"$pair"
    rcp=$(call POST "/work-orders/$WO2620/production-receipts" \
        "{\"destinationWarehouseId\":\"$WH_TP_HN\",\"serialNumber\":\"$sn\",\"quantity\":1,\"note\":\"Bộ dữ liệu demo\"}" \
        -H "Idempotency-Key: DEMO-PHIEU-$sn" | rid receiptId)
    call POST "/work-orders/$WO2620/production-receipts/$rcp/submit" >/dev/null
    call POST "/work-orders/$WO2620/production-receipts/$rcp/approve" >/dev/null
    call POST "/work-orders/$WO2620/production-receipts/$rcp/qc-disposition" \
        "{\"result\":\"$verdict\",\"reason\":\"$reason\"}" >/dev/null
    remember "productionReceipts.SERIAL_$verdict" "$rcp"
    info "$sn -> QC $verdict"
done

#!/usr/bin/env bash
# 90-fulfilment — take the MRP-converted work orders through production and let QC settle the
# sales orders behind them.
#
# The chain that matters: converting a proposal created a WorkOrderDemandAllocation linking the work
# order back to its sales order line. Approving a production receipt moves stock but fulfils
# nothing — the lot lands in HOLD. Only the QC verdict AVAILABLE releases it, and only then does
# fulfilledQuantity move and the sales order status roll up. Partial QC gives PARTIALLY_FULFILLED,
# full QC gives FULFILLED.

stage_banner "12. Sản xuất và giao hàng cho các đơn đã hoạch định"

WH_TP_HN="${ID[plants.$P1_CODE.warehouses.$WH_TP]}"

drive() {  # drive WO_ID TAG GOOD LOT_NUMBER_OR_DASH RECEIPT_QTY
    local wo="$1" tag="$2" good="$3" lot="$4" qty="$5" body rcp reserved
    # POST /reserve answers with a bare array of the reservations it just created; the GET
    # counterpart answers with a PageResult. Reading the POST response avoids the second call and
    # the shape difference.
    reserved=$(call POST "/work-orders/$wo/reserve")
    call POST "/work-orders/$wo/release" >/dev/null

    # One material issue per work order so the material-issue list reflects real consumption.
    local res rq
    res=$(echo "$reserved" | extract "(d['result'][0]['reservationId'] if d['result'] else '')")
    if [ -n "$res" ]; then
        rq=$(echo "$reserved" | extract "d['result'][0]['quantity']")
        call POST /material-issues \
            "{\"workOrderId\":\"$wo\",\"reservationId\":\"$res\",\"quantity\":$rq,\"reason\":\"Xuất vật tư cho $tag\"}" \
            -H "Idempotency-Key: DEMO-XUAT-$tag" >/dev/null
    fi

    call POST "/work-orders/$wo/production-executions" \
        "{\"goodQuantity\":$good,\"scrapQuantity\":0,\"reworkQuantity\":0,\"actualStartedAt\":\"$(ts_ago 5)\",\"actualEndedAt\":\"$(ts_ago 1)\",\"notes\":\"Báo cáo sản lượng cho $tag\"}" \
        -H "Idempotency-Key: DEMO-SANLUONG-$tag" >/dev/null

    if [ "$lot" = "-" ]; then
        body="{\"destinationWarehouseId\":\"$WH_TP_HN\",\"quantity\":$qty,\"note\":\"Nhập kho thành phẩm cho $tag\"}"
    else
        body="{\"destinationWarehouseId\":\"$WH_TP_HN\",\"lotNumber\":\"$lot\",\"quantity\":$qty,\"note\":\"Nhập kho thành phẩm cho $tag\"}"
    fi
    rcp=$(call POST "/work-orders/$wo/production-receipts" "$body" \
        -H "Idempotency-Key: DEMO-PHIEU-$tag" | rid receiptId)
    call POST "/work-orders/$wo/production-receipts/$rcp/submit"  >/dev/null
    call POST "/work-orders/$wo/production-receipts/$rcp/approve" >/dev/null
    echo "$rcp"
}

# ── SO-1004 -> PARTIALLY_FULFILLED ───────────────────────────────────────────
# Ten ordered, six produced, six QC-released. XE-PHO-26 is lot-tracked, so this is the branch where
# the verdict is written against a LOT.
log "WO-2610 (SO-1004): sản xuất 6/10 rồi QC đạt ⇒ đơn hàng giao một phần"
RCP_2610=$(drive "${ID[workOrders.WO-2610]}" "WO-2610" 6 "LO-XEPHO-2610" 6)
assert_field "/sales-orders/${ID[salesOrders.SO-1004.id]}" "status" "IN_PRODUCTION"
call POST "/work-orders/${ID[workOrders.WO-2610]}/production-receipts/$RCP_2610/qc-disposition" \
    '{"result":"AVAILABLE","reason":"Đạt kiểm tra tổng thể và chạy thử trên đường"}' >/dev/null
assert_status "/sales-orders/${ID[salesOrders.SO-1004.id]}" "PARTIALLY_FULFILLED"
remember "productionReceipts.SO1004_QC_DAT" "$RCP_2610"

# ── SO-1005 -> FULFILLED ─────────────────────────────────────────────────────
# XE-DIA-275 is NOT lot-tracked, so the verdict is written on the receipt itself (the branch debt
# #17 opened: before it, a non-lot finished good could never fulfil anything).
# Reporting 4 of 4 also drives the work order to COMPLETED — and a receipt on a COMPLETED work order
# is legal (B13 as widened by D11), which is exactly what happens next.
log "WO-2611 (SO-1005): sản xuất đủ 4/4 rồi QC đạt ⇒ đơn hàng giao đủ"
RCP_2611=$(drive "${ID[workOrders.WO-2611]}" "WO-2611" 4 "-" 4)
assert_status "/work-orders/${ID[workOrders.WO-2611]}" "COMPLETED"
call POST "/work-orders/${ID[workOrders.WO-2611]}/production-receipts/$RCP_2611/qc-disposition" \
    '{"result":"AVAILABLE","reason":"Đạt toàn bộ hạng mục kiểm tra xuất xưởng"}' >/dev/null
assert_status "/sales-orders/${ID[salesOrders.SO-1005.id]}" "FULFILLED"
remember "productionReceipts.SO1005_QC_DAT" "$RCP_2611"

# ── SO-1006 -> stays IN_PRODUCTION ───────────────────────────────────────────
log "WO-2612 (SO-1006): chỉ giữ vật tư và ban hành, dừng tại đây ⇒ đơn hàng ở IN_PRODUCTION"
call POST "/work-orders/${ID[workOrders.WO-2612]}/reserve" >/dev/null
call POST "/work-orders/${ID[workOrders.WO-2612]}/release" >/dev/null
assert_status "/work-orders/${ID[workOrders.WO-2612]}" "RELEASED"
assert_status "/sales-orders/${ID[salesOrders.SO-1006.id]}" "IN_PRODUCTION"

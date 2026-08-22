#!/usr/bin/env bash
# 95-purchasing — purchase requisitions, purchase orders, goods receipts.
#
# PR-2003 already exists: stage 85 created it by converting a BUY suggestion. Everything else is
# raised here so the three lists cover every status they can reach.

stage_banner "13. Mua hàng — đề nghị, đơn đặt, phiếu nhập"

CO="${ID[company.id]}"
P1="${ID[plants.$P1_CODE.id]}"
WH_RM="${ID[plants.$P1_CODE.warehouses.$WH_NVL]}"

# Both creators set the global NEW_ID rather than echoing it. Calling them inside $(...) would run
# `remember` in a subshell, so the document would exist in the database and be missing from
# demo-ids.json.
pr_create() {  # pr_create NO ITEM QTY SUPPLIER NEEDED_DAYS
    local no="$1" item="$2" qty="$3" sup="$4" days="$5"
    NEW_ID=$(call POST /purchase-requisitions \
        "{\"companyId\":\"$CO\",\"plantId\":\"$P1\",\"warehouseId\":\"$WH_RM\",\"requisitionNo\":\"$no\",\"neededByDate\":\"$(d_plus "$days")\",\"sourceType\":\"DEMO_SEED\",\"lines\":[{\"itemId\":\"${ID[items.$item]}\",\"supplierId\":\"${ID[suppliers.$sup]}\",\"requestedQuantity\":$qty,\"neededByDate\":\"$(d_plus "$days")\",\"note\":\"Bổ sung tồn kho theo kế hoạch quý\"}]}" \
        | rid purchaseRequisitionId)
    remember "purchaseRequisitions.$no" "$NEW_ID"
}

log "PR-2001 — DRAFT"
pr_create "PR-2001" "VANH-26" 200 "NCC-VANH" 30; PR2001="$NEW_ID"
assert_status "/purchase-requisitions/$PR2001" "DRAFT"

# Approving with an explicit approvedLines entry demonstrates partial approval: 300 requested,
# 240 approved. Approving MORE than requested is refused with PLANNED_QUANTITY_EXCEEDED.
log "PR-2002 — APPROVED (duyệt 240 trên 300 yêu cầu)"
pr_create "PR-2002" "LOP-26" 300 "NCC-CAOSU" 30; PR2002="$NEW_ID"
PR2002_LINE=$(call GET "/purchase-requisitions/$PR2002" \
    | extract "d['result']['lines'][0]['purchaseRequisitionLineId']")
call POST "/purchase-requisitions/$PR2002/approve" \
    "{\"decisionNote\":\"Duyệt 240 cho khớp ngân sách quý 4\",\"approvedLines\":[{\"purchaseRequisitionLineId\":\"$PR2002_LINE\",\"approvedQuantity\":240}]}" \
    >/dev/null
assert_status "/purchase-requisitions/$PR2002" "APPROVED"

log "PR-2004 — REJECTED"
pr_create "PR-2004" "LIP-7" 500 "NCC-TRUYENDONG" 45; PR2004="$NEW_ID"
call POST "/purchase-requisitions/$PR2004/reject" \
    '{"decisionNote":"Nhu cầu chưa cấp thiết, chuyển sang kỳ sau"}' >/dev/null
assert_status "/purchase-requisitions/$PR2004" "REJECTED"

log "PR-2005 — CANCELLED"
pr_create "PR-2005" "BAN-DAP" 400 "NCC-TRUYENDONG" 45; PR2005="$NEW_ID"
call POST "/purchase-requisitions/$PR2005/cancel" \
    '{"decisionNote":"Trùng với đề nghị PR-2002, huỷ bỏ"}' >/dev/null
assert_status "/purchase-requisitions/$PR2005" "CANCELLED"

# ── PR-2003 (from the MRP suggestion) -> approved -> converted to a PO ───────
if [ -n "${ID[purchaseRequisitions.PR-2003]:-}" ]; then
    log "PR-2003 (sinh từ MRP) — duyệt rồi chuyển thành đơn đặt hàng PO-3004"
    call POST "/purchase-requisitions/${ID[purchaseRequisitions.PR-2003]}/approve" \
        '{"decisionNote":"Duyệt theo thiếu hụt MRP đã tính"}' >/dev/null
    PO3004=$(call POST "/purchase-requisitions/${ID[purchaseRequisitions.PR-2003]}/convert-to-purchase-order" \
        "{\"purchaseOrderNo\":\"PO-3004\",\"supplierId\":\"${ID[suppliers.NCC-NHOM]}\",\"orderDate\":\"$(d_today)\",\"expectedDate\":\"$(d_plus 14)\",\"note\":\"Chuyển từ đề nghị PR-2003\"}" \
        | rid purchaseOrderId)
    remember "purchaseOrders.PO-3004" "$PO3004"
    assert_status "/purchase-requisitions/${ID[purchaseRequisitions.PR-2003]}" "CONVERTED"
fi

# ── purchase orders ──────────────────────────────────────────────────────────
po_create() {  # po_create NO SUPPLIER ITEM QTY PRICE EXPECTED_DAYS
    local no="$1" sup="$2" item="$3" qty="$4" price="$5" days="$6"
    NEW_ID=$(call POST /purchase-orders \
        "{\"companyId\":\"$CO\",\"plantId\":\"$P1\",\"warehouseId\":\"$WH_RM\",\"supplierId\":\"${ID[suppliers.$sup]}\",\"purchaseOrderNo\":\"$no\",\"orderDate\":\"$(d_today)\",\"expectedDate\":\"$(d_plus "$days")\",\"note\":\"Đơn đặt hàng thuộc bộ dữ liệu demo\",\"lines\":[{\"itemId\":\"${ID[items.$item]}\",\"orderedQuantity\":$qty,\"unitPrice\":$price,\"currencyCode\":\"VND\",\"expectedDate\":\"$(d_plus "$days")\"}]}" \
        | rid purchaseOrderId)
    remember "purchaseOrders.$no" "$NEW_ID"
}

log "PO-3001 — DRAFT"
po_create "PO-3001" "NCC-VANH" "VANH-275" 150 520000 12; PO3001="$NEW_ID"
assert_status "/purchase-orders/$PO3001" "DRAFT"

# A SENT order is open supply: MRP nets it against demand, so it makes future runs propose less.
log "PO-3002 — SENT (nguồn cung đang mở, MRP sẽ trừ vào nhu cầu)"
po_create "PO-3002" "NCC-TRUYENDONG" "XICH" 120 198000 18; PO3002="$NEW_ID"
call POST "/purchase-orders/$PO3002/send" >/dev/null
assert_status "/purchase-orders/$PO3002" "SENT"

log "PO-3005 — CANCELLED"
po_create "PO-3005" "NCC-VANH" "MOAYO" 90 390000 15; PO3005="$NEW_ID"
call POST "/purchase-orders/$PO3005/cancel" >/dev/null
assert_status "/purchase-orders/$PO3005" "CANCELLED"

# ── goods receipts ───────────────────────────────────────────────────────────
# A receipt line may not exceed the order line's remaining quantity (B27): PO-3003 orders 200 and
# takes 80 + 30, well inside it.
log "PO-3003 — SENT rồi nhận hàng từng phần ⇒ PARTIALLY_RECEIVED"
po_create "PO-3003" "NCC-CAOSU" "LOP-275" 200 672000 9; PO3003="$NEW_ID"
call POST "/purchase-orders/$PO3003/send" >/dev/null
PO3003_LINE=$(call GET "/purchase-orders/$PO3003" \
    | extract "d['result']['lines'][0]['purchaseOrderLineId']")

GR4001=$(call POST "/purchase-orders/$PO3003/goods-receipts" \
    "{\"receiptNo\":\"GR-4001\",\"note\":\"Giao đợt một, 80 lốp\",\"lines\":[{\"purchaseOrderLineId\":\"$PO3003_LINE\",\"receivedQuantity\":80}]}" \
    -H "Idempotency-Key: DEMO-NHAPMUA-3003-1" | rid goodsReceiptId)
remember "goodsReceipts.GR-4001" "$GR4001"
assert_status "/purchase-orders/$PO3003" "PARTIALLY_RECEIVED"

# Cancelling a posted receipt reverses its stock movements and rolls the order status back — the
# CANCELLED row on the goods-receipt list.
log "GR-4002 — POSTED rồi CANCELLED (đảo bút toán, trả trạng thái đơn về SENT/PARTIALLY_RECEIVED)"
GR4002=$(call POST "/purchase-orders/$PO3003/goods-receipts" \
    "{\"receiptNo\":\"GR-4002\",\"note\":\"Giao đợt hai, 30 lốp\",\"lines\":[{\"purchaseOrderLineId\":\"$PO3003_LINE\",\"receivedQuantity\":30}]}" \
    -H "Idempotency-Key: DEMO-NHAPMUA-3003-2" | rid goodsReceiptId)
call POST "/goods-receipts/$GR4002/cancel" \
    '{"cancelNote":"Giao sai quy cách gai lốp, trả lại nhà cung cấp"}' >/dev/null
remember "goodsReceipts.GR-4002" "$GR4002"
assert_status "/goods-receipts/$GR4002" "CANCELLED"

# ── PO-3004 received in full ────────────────────────────────────────────────
# ONG-NHOM is lot-tracked, so lotCode is mandatory on the receipt line. Never put a serial-tracked
# item on a purchase order: GoodsReceiptService has no serial support and answers SERIAL_REQUIRED.
if [ -n "${ID[purchaseOrders.PO-3004]:-}" ]; then
    log "PO-3004 — SENT rồi nhận đủ ⇒ RECEIVED"
    call POST "/purchase-orders/${ID[purchaseOrders.PO-3004]}/send" >/dev/null
    PO3004_JSON=$(call GET "/purchase-orders/${ID[purchaseOrders.PO-3004]}")
    PO3004_LINE=$(echo "$PO3004_JSON" | extract "d['result']['lines'][0]['purchaseOrderLineId']")
    PO3004_QTY=$(echo "$PO3004_JSON"  | extract "d['result']['lines'][0]['orderedQuantity']")
    GR4003=$(call POST "/purchase-orders/${ID[purchaseOrders.PO-3004]}/goods-receipts" \
        "{\"receiptNo\":\"GR-4003\",\"note\":\"Giao đủ theo đơn, 2 pallet\",\"lines\":[{\"purchaseOrderLineId\":\"$PO3004_LINE\",\"lotCode\":\"LO-NHOM-03\",\"receivedQuantity\":$PO3004_QTY}]}" \
        -H "Idempotency-Key: DEMO-NHAPMUA-3004-1" | rid goodsReceiptId)
    remember "goodsReceipts.GR-4003" "$GR4003"
    assert_status "/purchase-orders/${ID[purchaseOrders.PO-3004]}" "RECEIVED"
fi

#!/usr/bin/env bash
# verify.sh — chạy TRỌN luồng nghiệm thu của tài liệu (§11 PF-10..PF-14 + §12 smoke test
# end-to-end 9 bước) trên bộ dữ liệu D26- vừa seed.
#
# 🔴 seed.sh KHÔNG còn tạo Sales Order (quyết định của user, 2026-08-22) — bạn phải tự tạo một
#    đơn hàng CONFIRMED trước, rồi truyền số đơn hàng qua SALES_ORDER_NO. Script tự tra
#    salesOrderId/lineId từ đó, không đọc từ manifest.
#
# 🔴 SCRIPT NÀY GHI DỮ LIỆU VÀ KHÔNG ĐẢO NGƯỢC ĐƯỢC.
#    Nó tạo Planning Run, Work Order, phiếu xuất, phiếu ghi sản lượng, phiếu nhập kho và phán
#    quyết QC cho chính đơn hàng bạn chỉ định — tiêu mất đúng câu chuyện mà người trình bày định
#    diễn trực tiếp (§9: "Không seed Planning Run trước"). Đừng chạy lên đơn hàng bạn định dùng
#    để trình diễn thật.
#
#    Vì vậy quy trình đúng là HAI LƯỢT:
#      lượt 1 (nghiệm thu):  seed.sh -> tự tạo 1 đơn hàng thử -> verify.sh -> đọc PF-10..PF-14
#      xoá sạch:             docker compose down -v && docker compose up -d, khởi động lại backend
#      lượt 2 (bàn giao):    seed.sh -> tự tạo đơn hàng THẬT (không đụng bằng verify.sh)
#
#    Script bắt buộc phải có ALLOW_DESTRUCTIVE=1 để không ai chạy nhầm lên môi trường demo.
#
# Cách dùng:
#   ALLOW_DESTRUCTIVE=1 ADMIN_PASSWORD=... SALES_ORDER_NO=SO-TEST-001 \
#       ./scripts/demo-capstone2/verify.sh

set -euo pipefail

[ "${ALLOW_DESTRUCTIVE:-0}" = "1" ] || {
    echo "TU CHOI CHAY: script nay ghi du lieu khong dao nguoc duoc." >&2
    echo "Chi chay tren co so du lieu dung-mot-lan, va dat ALLOW_DESTRUCTIVE=1." >&2
    exit 1
}

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Dat bien ADMIN_PASSWORD}"
SALES_ORDER_NO="${SALES_ORDER_NO:?Dat bien SALES_ORDER_NO = so don hang CONFIRMED ban da tu tao}"

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEMO_DIR/../.." && pwd)"
MANIFEST="${MANIFEST:-$REPO_ROOT/demo-capstone2-ids.json}"
EVIDENCE="${EVIDENCE:-$REPO_ROOT/demo-capstone2-preflight.json}"

BODY_TMP="$(mktemp)"
trap 'rm -f "$BODY_TMP"' EXIT

. "$REPO_ROOT/scripts/demo/lib/common.sh"
. "$DEMO_DIR/catalogue.sh"

PY=python
command -v "$PY" >/dev/null 2>&1 || PY=python3

[ -f "$MANIFEST" ] || die "không thấy $MANIFEST — chạy seed.sh trước"

# mid KEY.PATH — đọc một id từ manifest do seed.sh ghi ra.
mid() { "$PY" -c "
import json,sys,io,functools
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
d = json.load(open(sys.argv[1], encoding='utf-8'))
print(functools.reduce(lambda n,k: n[k], sys.argv[2].split('.'), d))
" "$MANIFEST" "$1"; }

CO=$(mid company.id)
PLANT=$(mid plant.id)
WH_FG_ID=$(mid "warehouses.$WH_FG")
WH_WIP_ID=$(mid "warehouses.$WH_WIP")
WH_RM_ID=$(mid "warehouses.$WH_RM")

log "Đăng nhập $ADMIN_USERNAME"
TOKEN=$(curl -sS -X POST "$BASE_URL/auth/v1/login" -H "Content-Type: application/json; charset=utf-8" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" | rid accessToken)
[ -n "$TOKEN" ] || die "đăng nhập thất bại"

# Sales Order không còn do seed.sh tạo — tra theo SALES_ORDER_NO mà bạn đã tự tạo tay.
# search khớp orderNo; lọc thêm đúng plant để không vô tình bắt nhầm đơn hàng trùng số ở nhà máy khác.
SO_LIST=$(call GET "/sales-orders/v1?companyId=$CO&plantId=$PLANT&search=$SALES_ORDER_NO&page=0&size=20")
SO=$(echo "$SO_LIST" | extract "next((o['salesOrderId'] for o in d['result']['content'] if o['orderNo']=='$SALES_ORDER_NO'), '')")
[ -n "$SO" ] || die "không tìm thấy đơn hàng '$SALES_ORDER_NO' ở nhà máy $PLANT_CODE — tạo nó (và CONFIRM) trước khi chạy verify.sh"
SO_DETAIL=$(call GET "/sales-orders/v1/$SO")
[ "$(echo "$SO_DETAIL" | rid status)" = "CONFIRMED" ] \
    || die "đơn hàng '$SALES_ORDER_NO' chưa CONFIRMED — verify.sh cần đơn đã xác nhận để có PlanningDemand"
SO_LINE=$(echo "$SO_DETAIL" | extract "d['result']['lines'][0]['salesOrderLineId']")
info "dùng đơn hàng $SALES_ORDER_NO ($SO), dòng $SO_LINE"

PF_FAILED=0
pf() {
    if [ "$3" = "$4" ]; then info "$1 PASS — $2"
    else warn "$1 FAIL — $2 (nhận '$3', cần '$4')"; PF_FAILED=$((PF_FAILED + 1)); fi
}

################################################################################
# §15 + PF-10: chạy Planning cho $SALES_ORDER_NO
################################################################################
stage_banner "A. Chạy Planning (PF-10, PF-11, PF-12)"

DEMAND=$(call GET "/v1/planning/demands?companyId=$CO&plantId=$PLANT&status=OPEN&page=0&size=100" \
    | extract "next(x['planningDemandId'] for x in d['result']['content'] if x['referenceId']=='$SO_LINE')")
info "dòng nhu cầu: $DEMAND"

# demandWarehouseId = kho thành phẩm (§15). Nhu cầu cấp 0 lấy kho này; các cấp dưới do chính
# sách DEC-03 (defaultSupply của từng vật tư) quyết định — đó là lý do bảng §5 phải đủ 10 dòng.
RUN=$(call POST /v1/planning-runs \
    "{\"companyId\":\"$CO\",\"plantId\":\"$PLANT\",\"demandWarehouseId\":\"$WH_FG_ID\",\"horizonStartDate\":\"$(d_today)\",\"horizonEndDate\":\"$(d_plus 90)\",\"demandLineIds\":[\"$DEMAND\"]}" \
    -H "Idempotency-Key: D26-VERIFY-RUN")
RUN_ID=$(echo "$RUN" | rid mrpRunId)
RUN_CODE=$(echo "$RUN" | rid code)
info "$RUN_CODE"

pf PF-10 "lượt chạy hoàn tất" "$(echo "$RUN" | rid status)" "COMPLETED"
pf PF-10 "blockedProposals = 0" "$(echo "$RUN" | rid blockedProposals)" "0"
pf PF-10 "plannedWorkOrders = 1" "$(echo "$RUN" | rid plannedWorkOrders)" "1"
pf PF-10 "plannedPurchaseRecommendations = 0" "$(echo "$RUN" | rid plannedPurchaseRecommendations)" "0"

REQS=$(call GET "/v1/planning-runs/$RUN_ID/requirements?page=0&size=200")
pf PF-11 "không dòng nhu cầu nào BOM_MISSING/INVALID" \
    "$(echo "$REQS" | extract "sum(1 for r in d['result']['content'] if r['requirementStatus'] in ('BOM_MISSING','INVALID'))")" "0"
pf PF-11 "mọi dòng nhu cầu đều phân giải được kho" \
    "$(echo "$REQS" | extract "sum(1 for r in d['result']['content'] if not r.get('warehouseId'))")" "0"
pf PF-04 "mọi dòng nhu cầu dùng settingSource = ITEM_WAREHOUSE" \
    "$(echo "$REQS" | extract "sum(1 for r in d['result']['content'] if r.get('settingSource')!='ITEM_WAREHOUSE')")" "0"
pf PF-11 "nhu cầu cấp 0: gross 10 / net 10" \
    "$(echo "$REQS" | extract "next('%g/%g' % (float(r['grossRequiredQuantity']), float(r['netRequiredQuantity'])) for r in d['result']['content'] if r['requirementLevel']==0)")" "10/10"
pf PF-11 "mọi dòng cấp dưới đều COVERED" \
    "$(echo "$REQS" | extract "sum(1 for r in d['result']['content'] if r['requirementLevel']>0 and r['requirementStatus']!='COVERED')")" "0"

SUGG=$(call GET "/v1/planning-runs/$RUN_ID/suggestions?page=0&size=100")
pf PF-12 "đúng 1 đề xuất" \
    "$(echo "$SUGG" | extract "len(d['result']['content'])")" "1"
pf PF-12 "đề xuất là MAKE, trạng thái ngoại lệ READY" \
    "$(echo "$SUGG" | extract "'%s/%s' % (d['result']['content'][0]['supplyType'], d['result']['content'][0]['exceptionState'])")" "MAKE/READY"
pf PF-12 "chỉ có thông điệp MATERIAL_SHORTAGE" \
    "$(echo "$SUGG" | extract "','.join(d['result']['content'][0]['messages'])")" "MATERIAL_SHORTAGE"
pf PF-12 "đề xuất có kho đầu ra" \
    "$(echo "$SUGG" | extract "'yes' if d['result']['content'][0].get('outputWarehouseId') else 'no'")" "yes"

################################################################################
# PF-13 + PF-14: duyệt đề xuất, chuyển thành lệnh sản xuất, giữ chỗ vật tư
################################################################################
stage_banner "B. Đề xuất -> Lệnh sản xuất -> Giữ chỗ (PF-13, PF-14)"

SUGG_ID=$(echo "$SUGG" | extract "d['result']['content'][0]['supplySuggestionId']")

call POST "/v1/supply-suggestions/$SUGG_ID/approve" \
    '{"decisionNote":"Duyệt đề xuất sản xuất cho '"$SALES_ORDER_NO"'"}' >/dev/null
info "đề xuất -> APPROVED"

WO_NO="WO-VERIFY-$(date -u +%H%M%S)"
WO=$(call POST "/v1/supply-suggestions/$SUGG_ID/convert-to-work-order" \
    "{\"workOrderNo\":\"$WO_NO\",\"outputWarehouseId\":\"$WH_FG_ID\",\"notes\":\"Sinh từ $RUN_CODE cho $SALES_ORDER_NO\"}" \
    | rid convertedWorkOrderId)
info "lệnh sản xuất: $WO_NO ($WO)"

WO_JSON=$(call GET "/v1/work-orders/$WO")
pf PF-13 "đúng một lệnh sản xuất được tạo" \
    "$(call GET "/v1/plants/$PLANT/work-orders?page=0&size=50" | extract "len(d['result']['content'])")" "1"
pf PF-13 "số lượng kế hoạch = 10" \
    "$(echo "$WO_JSON" | extract "'%g' % float(d['result']['plannedQuantity'])")" "10"
pf PF-13 "ảnh chụp quy trình công nghệ không rỗng" \
    "$(echo "$WO_JSON" | extract "d['result'].get('sourceRoutingCode') or 'NULL'")" "D26-RT-BIKE16"

# reserveAutomatically phân giải theo PHẠM VI NHÀ MÁY chứ không theo kho đầu ra của lệnh sản xuất
# — nên vật tư nằm ở D26-WIP/D26-RM vẫn được giữ chỗ dù lệnh sản xuất xuất hàng ra D26-FG.
call POST "/v1/work-orders/$WO/reserve" >/dev/null
READY=$(call GET "/v1/work-orders/$WO/material-readiness")
pf PF-14 "reservedPercent = 100" \
    "$(echo "$READY" | extract "'%g' % float(d['result']['reservedPercent'])")" "100"
pf PF-14 "shortageLineCount = 0" \
    "$(echo "$READY" | extract "d['result']['shortageLineCount']")" "0"
pf PF-14 "ready = true và canRelease = true" \
    "$(echo "$READY" | extract "'%s/%s' % (d['result']['ready'], d['result']['canRelease'])")" "True/True"

################################################################################
# §12: smoke test end-to-end — release, xuất vật tư, ghi sản lượng
################################################################################
stage_banner "C. Sản xuất: release -> xuất vật tư -> ghi sản lượng"

call POST "/v1/work-orders/$WO/release" >/dev/null
pf "SMOKE-3" "lệnh sản xuất -> RELEASED" \
    "$(call GET "/v1/work-orders/$WO" | rid status)" "RELEASED"

# Xuất đúng định mức: khung 10, bộ truyền động 10, bánh xe 20, phanh 10 (§12 bước 4).
# componentLineId và reservationId lấy từ chính lệnh sản xuất — không đoán.
LINES=$(call GET "/v1/work-orders/$WO/material-reservations?page=0&size=50")
ISSUE_LINES=$(echo "$LINES" | "$PY" -c "
import json,sys,io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
c = json.loads(sys.stdin.buffer.read().decode('utf-8'))['result']['content']
out = [{'componentLineId': r['componentLineId'],
        'reservationId': r['reservationId'],
        'warehouseId': r['warehouseId'],
        'lotId': r.get('lotId'),
        'quantity': float(r['remainingQuantity']),
        'reason': 'Xuat vat tu theo dinh muc cho WO-DEMO26-001'} for r in c]
print(json.dumps(out))
")
ISSUE=$(call POST "/v1/work-orders/$WO/material-issues" \
    "{\"note\":\"Xuất vật tư theo định mức\",\"lines\":$ISSUE_LINES}" \
    -H "Idempotency-Key: D26-VERIFY-ISSUE")
info "phiếu xuất: $(echo "$ISSUE" | rid code)"
pf "SMOKE-4" "xuất đủ 4 dòng thành phần" \
    "$(echo "$ISSUE" | extract "len(d['result']['lines'])")" "4"
pf "SMOKE-4" "tổng lượng xuất = 50 (10+10+20+10)" \
    "$(echo "$ISSUE" | extract "'%g' % sum(float(l['quantity']) for l in d['result']['lines'])")" "50"

# Ghi sản lượng: tốt 10, phế 0, làm lại 0 (§12 bước 5).
EXEC=$(call POST "/v1/work-orders/$WO/production-executions" \
    "{\"goodQuantity\":10,\"scrapQuantity\":0,\"reworkQuantity\":0,\"actualStartedAt\":\"$(ts_ago 4)\",\"actualEndedAt\":\"$(ts_ago 1)\",\"notes\":\"Ghi sản lượng ca ngày\"}" \
    -H "Idempotency-Key: D26-VERIFY-EXEC")
info "phiếu ghi sản lượng: $(echo "$EXEC" | rid code)"
pf "SMOKE-5" "lệnh sản xuất -> COMPLETED (sản lượng tốt chạm kế hoạch)" \
    "$(call GET "/v1/work-orders/$WO" | rid status)" "COMPLETED"

################################################################################
# §12: nhập kho thành phẩm -> HOLD -> QC -> AVAILABLE
################################################################################
stage_banner "D. Nhập kho thành phẩm, duyệt, phán quyết QC"

RCP=$(call POST "/v1/work-orders/$WO/production-receipts" \
    "{\"destinationWarehouseId\":\"$WH_FG_ID\",\"lotNumber\":\"$DEMO_OUTPUT_LOT\",\"quantity\":10,\"reason\":\"Nhập kho thành phẩm\",\"note\":\"Phiếu nhập kho trình diễn\"}" \
    -H "Idempotency-Key: D26-VERIFY-RECEIPT")
RCP_ID=$(echo "$RCP" | rid receiptId)
pf "SMOKE-6" "phiếu nhập kho khởi tạo ở DRAFT" "$(echo "$RCP" | rid status)" "DRAFT"

pf "SMOKE-6" "sau submit -> PENDING_APPROVAL" \
    "$(call POST "/v1/work-orders/$WO/production-receipts/$RCP_ID/submit" | rid status)" "PENDING_APPROVAL"

# Phản hồi của approve mang luôn lotId và outputLotStatus, nên không phải tra lô theo mã.
APPROVED=$(call POST "/v1/work-orders/$WO/production-receipts/$RCP_ID/approve" \
    '{"reason":"Đã kiểm đếm đủ số lượng"}')
LOT_ID=$(echo "$APPROVED" | rid lotId)
info "phiếu nhập kho -> APPROVED, lô $DEMO_OUTPUT_LOT ($LOT_ID)"

# Bước 7 của §12: lô thành phẩm phải ở HOLD và CHƯA làm tăng tồn khả dụng.
LOT=$(call GET "/v1/inventory/lots/$LOT_ID")
pf "SMOKE-7" "lô $DEMO_OUTPUT_LOT ở trạng thái HOLD" "$(echo "$LOT" | rid status)" "HOLD"
pf "SMOKE-7" "lô đang HOLD báo khả dụng = 0" \
    "$(echo "$LOT" | extract "'%g' % sum(float(b['availableQuantity']) for b in d['result']['balances'])")" "0"
pf "SMOKE-7" "nhưng tồn thực tế vẫn là 10" \
    "$(echo "$LOT" | extract "'%g' % sum(float(b['onHandQuantity']) for b in d['result']['balances'])")" "10"

# Bước 8: QC cho phép xuất -> lô AVAILABLE, tồn D26-FG tăng đúng 10.
call POST "/v1/work-orders/$WO/production-receipts/$RCP_ID/qc-disposition" \
    '{"result":"AVAILABLE","reason":"Kiểm tra ngoại quan và vận hành đạt yêu cầu"}' >/dev/null
pf "SMOKE-8" "lô -> AVAILABLE" "$(call GET "/v1/inventory/lots/$LOT_ID" | rid status)" "AVAILABLE"
pf "SMOKE-8" "tồn khả dụng D26-FG tăng đúng 10" \
    "$(call GET "/v1/inventory/balances?warehouseId=$WH_FG_ID&size=100" \
        | extract "'%g' % sum(float(b['availableQuantity']) for b in d['result']['content'])")" "10"

# Bước 9: truy vết ngược SO -> WO -> ảnh chụp BOM/quy trình -> xuất -> sản lượng -> nhập -> lô.
SO_FINAL=$(call GET "/sales-orders/v1/$SO")
pf "SMOKE-9" "đơn bán hàng -> FULFILLED sau khi QC giải phóng lô" \
    "$(echo "$SO_FINAL" | rid status)" "FULFILLED"
pf "SMOKE-9" "số lượng đã đáp ứng = 10" \
    "$(echo "$SO_FINAL" | extract "'%g' % float(d['result']['lines'][0]['fulfilledQuantity'])")" "10"

################################################################################
# Kết luận
################################################################################
stage_banner "Kết quả nghiệm thu"

"$PY" - "$EVIDENCE" "$RUN_ID" "$RUN_CODE" "$WO" "$PF_FAILED" <<'PY'
import json, sys
target, run_id, run_code, wo, failed = sys.argv[1:6]
json.dump({"planningRunId": run_id, "planningRunCode": run_code,
           "workOrderId": wo, "preflightFailures": int(failed)},
          open(target, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
PY
info "đã ghi $EVIDENCE"

if [ "$PF_FAILED" -ne 0 ]; then
    die "$PF_FAILED mục KHÔNG đạt."
fi
log "TOÀN BỘ PF-10..PF-14 và smoke test §12 ĐẠT"
warn "Cơ sở dữ liệu này đã bị tiêu hao — xoá sạch rồi seed lại trước khi bàn giao."

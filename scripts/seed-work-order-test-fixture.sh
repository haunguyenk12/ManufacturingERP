#!/usr/bin/env bash
# seed-work-order-test-fixture.sh — disposable Work Order test fixture for FE.
#
# Provisions, through the real REST API (not raw SQL — a Work Order's snapshot fields, reservation
# math and status gates all live in service code, so faking them with INSERT would risk violating
# invariants the service layer enforces), a fresh, throwaway data set:
#
#   - Company -> Plant -> Warehouse + Work Center
#   - One RAW_MATERIAL item + one FINISHED_GOOD item, linked by an ACTIVE BOM (qty-per = 2, no scrap)
#   - One ACTIVE Routing for the finished good, with an Assembly operation at the Work Center
#   - AVAILABLE stock of the raw material in the warehouse (500 units — 300 more than the work
#     order's requirement, so there is comfortable headroom left over for an over-BOM issue)
#   - A Work Order (planned qty = 100 => required component qty = 200), planned, auto-reserved
#     (FEFO) and released -> status RELEASED, with one MaterialReservation left ACTIVE holding its
#     full 200 units (nothing has been issued yet)
#
# That state is deliberately the STOPPING point, not a place where partial issue / over-issue /
# completion / close have already been exercised — the whole point of the fixture is to hand FE a
# Work Order where all four of those actions are still there to try. The script prints ready-to-run
# curl commands for exactly those four actions at the end, with the real IDs already filled in.
#
# Every run creates brand-new Company/Plant/Warehouse/Work Center/Items/BOM/Routing/Work Order codes (timestamp +
# random suffix) — safe to re-run any number of times, nothing to clean up first. The data is
# ordinary rows through ordinary endpoints: dispose of it however you like (there is no special
# teardown path — deactivate/cancel/close it like any other fixture, or just leave it, it costs
# nothing to leave behind in a dev database).
#
# Requirements: curl, python (or python3) on PATH for JSON parsing (no jq dependency).
#
# Usage:
#   BASE_URL=http://localhost:8080/api/v1 ADMIN_USERNAME=<bootstrap-user> ADMIN_PASSWORD='<secret>' \
#     ./scripts/seed-work-order-test-fixture.sh
#
# All three env vars are optional and default to the values above.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api/v1}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Set ADMIN_PASSWORD explicitly; no default credential is allowed}"

PY=python
command -v "$PY" >/dev/null 2>&1 || PY=python3
command -v "$PY" >/dev/null 2>&1 || { echo "ERROR: need python or python3 on PATH" >&2; exit 1; }

# Uppercase: entity codes are constrained to ^[A-Z0-9._-]+$ (organization/inventory create DTOs).
SUFFIX="$(date +%s)-$(head -c4 /dev/urandom | od -An -tx1 | tr -d ' \n')"
SUFFIX="$(echo "$SUFFIX" | tr '[:lower:]' '[:upper:]')"

log()   { echo "==> $*" >&2; }
extract() {
    # extract "d['result']['workOrderId']"  — reads a JSON response from stdin into `d`, evals the
    # given expression against it.
    "$PY" -c "import json,sys; d=json.load(sys.stdin); print(eval(\"$1\"))"
}

# call METHOD PATH [BODY_JSON] [EXTRA_CURL_ARGS...]
# Prints the raw response body to stdout, aborts the script if `code` != SUCCESS.
call() {
    local method="$1" path="$2" body="${3:-}"
    shift 3 2>/dev/null || shift $#
    local resp
    if [ -n "$body" ]; then
        resp=$(curl -sS -X "$method" "$BASE_URL$path" \
            -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
            -d "$body" "$@")
    else
        resp=$(curl -sS -X "$method" "$BASE_URL$path" \
            -H "Authorization: Bearer $TOKEN" "$@")
    fi
    local code
    code=$(echo "$resp" | extract "d['code']" 2>/dev/null || echo "UNPARSEABLE")
    if [ "$code" != "SUCCESS" ]; then
        echo "FAILED: $method $path" >&2
        echo "$resp" >&2
        exit 1
    fi
    echo "$resp"
}

log "Logging in as $ADMIN_USERNAME against $BASE_URL"
LOGIN_RESP=$(curl -sS -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")
TOKEN=$(echo "$LOGIN_RESP" | extract "d['result']['accessToken']" 2>/dev/null || true)
if [ -z "${TOKEN:-}" ]; then
    echo "FAILED to log in:" >&2
    echo "$LOGIN_RESP" >&2
    exit 1
fi

log "Creating Company"
COMPANY_RESP=$(call POST /companies "{\"code\":\"WOTEST-$SUFFIX\",\"name\":\"WO Fixture Co $SUFFIX\"}")
COMPANY_ID=$(echo "$COMPANY_RESP" | extract "d['result']['companyId']")

log "Creating Plant"
PLANT_RESP=$(call POST "/companies/$COMPANY_ID/plants" \
    "{\"code\":\"WOTEST-P-$SUFFIX\",\"name\":\"WO Fixture Plant $SUFFIX\"}")
PLANT_ID=$(echo "$PLANT_RESP" | extract "d['result']['plantId']")

log "Creating Warehouse"
WAREHOUSE_RESP=$(call POST "/plants/$PLANT_ID/warehouses" \
    "{\"code\":\"WOTEST-WH-$SUFFIX\",\"name\":\"WO Fixture Warehouse $SUFFIX\",\"type\":\"GENERAL\"}")
WAREHOUSE_ID=$(echo "$WAREHOUSE_RESP" | extract "d['result']['warehouseId']")

log "Creating raw material item"
RM_RESP=$(call POST "/companies/$COMPANY_ID/items" \
    "{\"code\":\"WOTEST-RM-$SUFFIX\",\"name\":\"Fixture Raw Material\",\"type\":\"RAW_MATERIAL\",\"unit\":\"KG\",\"lotTracked\":false,\"serialTracked\":false}")
RM_ITEM_ID=$(echo "$RM_RESP" | extract "d['result']['itemId']")

log "Creating finished good item"
FG_RESP=$(call POST "/companies/$COMPANY_ID/items" \
    "{\"code\":\"WOTEST-FG-$SUFFIX\",\"name\":\"Fixture Finished Good\",\"type\":\"FINISHED_GOOD\",\"unit\":\"EA\",\"lotTracked\":false,\"serialTracked\":false}")
FG_ITEM_ID=$(echo "$FG_RESP" | extract "d['result']['itemId']")

log "Creating BOM header (finished good <- raw material, qty-per 2, no scrap)"
BOM_RESP=$(call POST "/companies/$COMPANY_ID/boms" \
    "{\"parentItemId\":\"$FG_ITEM_ID\",\"revision\":\"A\",\"description\":\"WO test fixture BOM\"}")
BOM_ID=$(echo "$BOM_RESP" | extract "d['result']['bomId']")

log "Adding BOM line"
call POST "/boms/$BOM_ID/lines" \
    "{\"componentItemId\":\"$RM_ITEM_ID\",\"lineNo\":1,\"quantityPer\":2,\"scrapRate\":0}" >/dev/null

log "Activating BOM"
call POST "/boms/$BOM_ID/activate" >/dev/null

log "Creating Work Center for the production routing"
WORK_CENTER_RESP=$(call POST "/plants/$PLANT_ID/work-centers" \
    "{\"code\":\"WOTEST-WC-$SUFFIX\",\"name\":\"WO Fixture Work Center $SUFFIX\",\"description\":\"Disposable FE test fixture\",\"capacityUnitType\":\"LINE\",\"capacityUnits\":1}")
WORK_CENTER_ID=$(echo "$WORK_CENTER_RESP" | extract "d['result']['workCenterId']")

log "Creating Routing with one Assembly operation"
ROUTING_RESP=$(call POST "/companies/$COMPANY_ID/routings" \
    "{\"itemId\":\"$FG_ITEM_ID\",\"code\":\"WOTEST-RT-$SUFFIX\",\"version\":\"A\",\"note\":\"WO test fixture routing\",\"operations\":[{\"sequence\":10,\"name\":\"Assembly\",\"workCenterId\":\"$WORK_CENTER_ID\",\"setupMinutes\":0,\"runMinutesPerUnit\":1}]}")
ROUTING_ID=$(echo "$ROUTING_RESP" | extract "d['result']['routingId']")

log "Activating Routing"
call POST "/routings/$ROUTING_ID/activate" >/dev/null

log "Receiving 500 units of raw material into the warehouse (AVAILABLE stock)"
call POST /inventory/receive \
    "{\"itemId\":\"$RM_ITEM_ID\",\"warehouseId\":\"$WAREHOUSE_ID\",\"quantity\":500,\"reason\":\"WO test fixture initial stock\",\"referenceType\":\"TEST_FIXTURE\",\"referenceId\":\"$SUFFIX\"}" \
    -H "Idempotency-Key: WOTEST-RECEIVE-$SUFFIX" >/dev/null

log "Creating Work Order (planned qty 100 => required component qty 200)"
WO_RESP=$(call POST "/plants/$PLANT_ID/work-orders" \
    "{\"workOrderNo\":\"WOTEST-$SUFFIX\",\"productItemId\":\"$FG_ITEM_ID\",\"outputWarehouseId\":\"$WAREHOUSE_ID\",\"plannedQuantity\":100,\"notes\":\"Disposable FE test fixture\"}")
WORK_ORDER_ID=$(echo "$WO_RESP" | extract "d['result']['workOrderId']")
WORK_ORDER_NO=$(echo "$WO_RESP" | extract "d['result']['workOrderNo']")

log "Scheduling (plan) the Work Order"
call POST "/work-orders/$WORK_ORDER_ID/plan" >/dev/null

log "Auto-reserving components (FEFO)"
RESERVE_RESP=$(call POST "/work-orders/$WORK_ORDER_ID/reserve")
RESERVATION_ID=$(echo "$RESERVE_RESP" | extract "d['result'][0]['reservationId']")
COMPONENT_LINE_ID=$(echo "$RESERVE_RESP" | extract "d['result'][0]['componentLineId']")
RESERVED_QTY=$(echo "$RESERVE_RESP" | extract "d['result'][0]['quantity']")

log "Checking material readiness before release"
READINESS_RESP=$(call GET "/work-orders/$WORK_ORDER_ID/material-readiness")
CAN_RELEASE=$(echo "$READINESS_RESP" | extract "d['result']['canRelease']")
if [ "$CAN_RELEASE" != "True" ]; then
    echo "Reservation did not fully cover the requirement — readiness response:" >&2
    echo "$READINESS_RESP" >&2
    exit 1
fi

log "Releasing the Work Order"
RELEASE_RESP=$(call POST "/work-orders/$WORK_ORDER_ID/release")
WO_STATUS=$(echo "$RELEASE_RESP" | extract "d['result']['status']")

cat >&2 <<EOF

================================================================================
Fixture ready. Work Order status: $WO_STATUS
================================================================================

  Company        : $COMPANY_ID   (code WOTEST-$SUFFIX)
  Plant           : $PLANT_ID   (code WOTEST-P-$SUFFIX)
  Warehouse       : $WAREHOUSE_ID   (code WOTEST-WH-$SUFFIX, 500 KG raw material AVAILABLE)
  Raw material    : $RM_ITEM_ID   (code WOTEST-RM-$SUFFIX)
  Finished good   : $FG_ITEM_ID   (code WOTEST-FG-$SUFFIX)
  BOM             : $BOM_ID   (ACTIVE, qty-per 2, scrap 0)
  Work Center     : $WORK_CENTER_ID   (code WOTEST-WC-$SUFFIX, ACTIVE)
  Routing         : $ROUTING_ID   (code WOTEST-RT-$SUFFIX, version A, ACTIVE)
  Work Order      : $WORK_ORDER_ID   (code $WORK_ORDER_NO, plannedQuantity 100)
  Component line  : $COMPONENT_LINE_ID   (requiredQuantity 200)
  Reservation     : $RESERVATION_ID   (ACTIVE, quantity $RESERVED_QTY, nothing consumed yet)

--------------------------------------------------------------------------------
Ready-to-run examples for the four actions this fixture is meant to exercise
--------------------------------------------------------------------------------

1) Partial issue (issues 50 of the 200 reserved; reservation stays ACTIVE with 150 remaining):

curl -X POST "$BASE_URL/material-issues" \\
  -H "Authorization: Bearer \$TOKEN" -H "Content-Type: application/json" \\
  -H "Idempotency-Key: WOTEST-ISSUE-PARTIAL-$SUFFIX" \\
  -d '{"workOrderId":"$WORK_ORDER_ID","reservationId":"$RESERVATION_ID","quantity":50,"reason":"Partial issue test"}'

2) Over-BOM issue (250 in one line exceeds the 200 required — needs PERM_MATERIAL_ISSUE_OVERRIDE +
   overrideReason; admin has the permission. Drawn straight from available stock, not tied to a
   reservation, so it works whether or not you ran step 1 first):

curl -X POST "$BASE_URL/work-orders/$WORK_ORDER_ID/material-issues" \\
  -H "Authorization: Bearer \$TOKEN" -H "Content-Type: application/json" \\
  -H "Idempotency-Key: WOTEST-ISSUE-OVER-$SUFFIX" \\
  -d '{"lines":[{"componentLineId":"$COMPONENT_LINE_ID","warehouseId":"$WAREHOUSE_ID","quantity":250,"reason":"Over-BOM issue test","overrideReason":"Testing over-issue override"}]}'

3) Complete (reports the full planned quantity as good output; work order auto-completes when
   cumulative good reaches 100 — independent of how much material has been issued):

curl -X POST "$BASE_URL/work-orders/$WORK_ORDER_ID/production-executions" \\
  -H "Authorization: Bearer \$TOKEN" -H "Content-Type: application/json" \\
  -H "Idempotency-Key: WOTEST-EXEC-$SUFFIX" \\
  -d '{"goodQuantity":100,"scrapQuantity":0,"reworkQuantity":0,"actualStartedAt":"$(date -u +%Y-%m-%dT%H:%M:%SZ)","actualEndedAt":"$(date -u +%Y-%m-%dT%H:%M:%SZ)"}'

4) Close (only works once the Work Order is COMPLETED — run after step 3; also releases whatever is
   left of the reservation back to available stock):

curl -X POST "$BASE_URL/work-orders/$WORK_ORDER_ID/close" \\
  -H "Authorization: Bearer \$TOKEN"

--------------------------------------------------------------------------------
\$TOKEN in the examples above is the access token from this run's login — export it if you copy
these commands into a new shell:
  export TOKEN="$TOKEN"
================================================================================
EOF

# Machine-readable summary on stdout (everything else above went to stderr), so this script can
# also be piped into other tooling: seed-work-order-test-fixture.sh | python -m json.tool
"$PY" -c "
import json
print(json.dumps({
    'companyId': '$COMPANY_ID',
    'plantId': '$PLANT_ID',
    'warehouseId': '$WAREHOUSE_ID',
    'rawMaterialItemId': '$RM_ITEM_ID',
    'finishedGoodItemId': '$FG_ITEM_ID',
    'bomId': '$BOM_ID',
    'workCenterId': '$WORK_CENTER_ID',
    'routingId': '$ROUTING_ID',
    'workOrderId': '$WORK_ORDER_ID',
    'workOrderNo': '$WORK_ORDER_NO',
    'componentLineId': '$COMPONENT_LINE_ID',
    'reservationId': '$RESERVATION_ID',
    'accessToken': '$TOKEN',
}, indent=2))
"

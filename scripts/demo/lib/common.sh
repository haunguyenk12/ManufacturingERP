#!/usr/bin/env bash
# lib/common.sh — shared helpers for the Velo demo seeder.
#
# Sourced by seed-demo.sh. Never executed directly.
#
# Everything here talks to the REAL REST API. Nothing writes SQL: a work order's BOM/routing
# snapshot, the stock_balances projection, @PrePersist document codes, @Version and the audit trail
# only exist if the service layer runs. See scripts/demo/README-vi.md.

# ── output ───────────────────────────────────────────────────────────────────
# Convention inherited from scripts/seed-work-order-test-fixture.sh:
#   stderr = human narration, stdout = machine-readable JSON only.
log()   { echo "==> $*" >&2; }
info()  { echo "    $*" >&2; }
warn()  { echo "!!  $*" >&2; }
die()   { echo "ERROR: $*" >&2; exit 1; }

stage_banner() {
    echo "" >&2
    echo "################################################################################" >&2
    echo "# $*" >&2
    echo "################################################################################" >&2
}

# ── JSON ─────────────────────────────────────────────────────────────────────
# extract "d['result']['companyId']"  — reads a JSON response on stdin into `d`, evals the expr.
#
# 🔴 Decodes stdin from bytes explicitly instead of using json.load(sys.stdin). On Windows, Python's
# stdin defaults to the ANSI code page (cp1252), which cannot decode a UTF-8 Vietnamese response and
# dies with "'charmap' codec can't decode byte 0x8d". Whether it blows up depends on which bytes
# happen to land in the payload, so a plain json.load() run can pass a hundred times and then fail
# on a name nobody changed. stdout is forced to UTF-8 for the same reason on the way back out.
extract() {
    "$PY" -c "import json,sys,io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
d = json.loads(sys.stdin.buffer.read().decode('utf-8'))
print(eval(\"$1\"))"
}

# rid FIELD  — sugar for the overwhelmingly common case.
rid() { extract "d['result']['$1']"; }

# ── HTTP ─────────────────────────────────────────────────────────────────────
# The body is written to a temp file and sent with --data-binary rather than -d, so multi-byte
# UTF-8 (every Vietnamese name in this dataset) reaches the server byte-for-byte. FE's PowerShell
# seeder mangled exactly this and left "BÃ ..." rows in the database.
_curl() {
    local method="$1" path="$2" body="$3"; shift 3
    if [ -n "$body" ]; then
        printf '%s' "$body" > "$BODY_TMP"
        curl -sS -X "$method" "$BASE_URL$path" \
            -H "Authorization: Bearer $TOKEN" \
            -H "Content-Type: application/json; charset=utf-8" \
            --data-binary "@$BODY_TMP" "$@"
    else
        curl -sS -X "$method" "$BASE_URL$path" -H "Authorization: Bearer $TOKEN" "$@"
    fi
}

# call METHOD PATH [BODY] [EXTRA_CURL_ARGS...]
# Prints the raw response on stdout; aborts the run on any envelope code other than SUCCESS.
call() {
    local method="$1" path="$2" body="${3:-}"
    shift 3 2>/dev/null || shift $#
    local resp code
    resp=$(_curl "$method" "$path" "$body" "$@")
    code=$(echo "$resp" | extract "d['code']" 2>/dev/null || echo "UNPARSEABLE")
    if [ "$code" != "SUCCESS" ]; then
        echo "FAILED: $method $path" >&2
        [ -n "$body" ] && echo "  body: $body" >&2
        echo "$resp" >&2
        exit 1
    fi
    echo "$resp"
}

# call_expect EXPECTED_CODE METHOD PATH [BODY] [EXTRA_CURL_ARGS...]
#
# For the steps that MUST fail. Three of them exist and none is optional:
#   - driving WO-2603 into BLOCKED (POST /release returns 409 and persists BLOCKED)
#   - proving a BLOCKED supply suggestion refuses to convert
#   - proving an over-BOM issue is refused without the override permission
#
# Aborts both when the call unexpectedly succeeds (the failure we relied on stopped happening —
# that is a regression, not a win) and when it fails with a different code.
call_expect() {
    local expected="$1" method="$2" path="$3" body="${4:-}"
    shift 4 2>/dev/null || shift $#
    local resp code
    resp=$(_curl "$method" "$path" "$body" "$@")
    code=$(echo "$resp" | extract "d['code']" 2>/dev/null || echo "UNPARSEABLE")
    if [ "$code" != "$expected" ]; then
        echo "EXPECTED $expected FROM $method $path BUT GOT $code" >&2
        echo "$resp" >&2
        exit 1
    fi
    info "expected failure ok: $method $path -> $expected"
    echo "$resp"
}

# ── state assertions ─────────────────────────────────────────────────────────
# assert_field PATH FIELD EXPECTED
#
# Load-bearing, not decoration. WorkOrderService.release() runs three guards and TWO of them throw
# the same STATE_CONFLICT code — but only the material-readiness gate writes BLOCKED. A 409 alone
# therefore does not prove the work order reached the state we wanted; reading it back does.
assert_field() {
    local path="$1" field="$2" expected="$3" actual
    actual=$(call GET "$path" | extract "d['result']['$field']")
    if [ "$actual" != "$expected" ]; then
        echo "STATE ASSERTION FAILED: $path -> $field = '$actual', expected '$expected'" >&2
        exit 1
    fi
    info "ok: $field=$expected"
}

assert_status() { assert_field "$1" "status" "$2"; }

# ── id registry ──────────────────────────────────────────────────────────────
declare -A ID
RECORD_KEYS=(); RECORD_VALS=()

# remember KEY VALUE — store in $ID and queue for demo-ids.json (dots become nesting).
remember() {
    ID["$1"]="$2"
    RECORD_KEYS+=("$1"); RECORD_VALS+=("$2")
}

# note KEY VALUE — manifest only, not an id lookup (counts, codes, passwords).
note() { RECORD_KEYS+=("$1"); RECORD_VALS+=("$2"); }

dump_manifest() {
    local target="$1"
    "$PY" - "$target" <<'PY' "$(printf '%s\n' ${RECORD_KEYS+"${RECORD_KEYS[@]}"})" "$(printf '%s\n' ${RECORD_VALS+"${RECORD_VALS[@]}"})"
import json, sys
target = sys.argv[1]
keys = [k for k in sys.argv[2].split("\n") if k]
vals = sys.argv[3].split("\n")
out = {}
for k, v in zip(keys, vals):
    node = out
    parts = k.split(".")
    for p in parts[:-1]:
        node = node.setdefault(p, {})
        if not isinstance(node, dict):
            raise SystemExit("manifest key collision at %r" % k)
    node[parts[-1]] = v
with open(target, "w", encoding="utf-8") as fh:
    json.dump(out, fh, indent=2, ensure_ascii=False)
    fh.write("\n")
PY
}

# ── dates ────────────────────────────────────────────────────────────────────
# GNU date only (-d). Checked in stage 00.
d_today() { date -u +%F; }
d_plus()  { date -u -d "+$1 days" +%F; }
d_minus() { date -u -d "-$1 days" +%F; }
ts_now()  { date -u +%Y-%m-%dT%H:%M:%SZ; }
ts_ago()  { date -u -d "-$1 hours" +%Y-%m-%dT%H:%M:%SZ; }
# ts_at DAYS HH  -> instant at HH:00Z, DAYS days from today
ts_at()   { date -u -d "$(d_plus "$1") $2:00:00 UTC" +%Y-%m-%dT%H:%M:%SZ; }

# ts_next_monday HH -> instant at HH:00Z on the next Monday.
#
# Work orders that must land on a working day anchor here rather than on "today + N". Every calendar
# in the dataset works Monday, none works Sunday, so a fixed day offset would put the schedule on a
# non-working day whenever the seed happens to run late in the week — and the capacity board would
# then report capacity 0 / utilization null and flag an overload that says nothing about capacity.
ts_next_monday() { date -u -d "next monday $1:00:00 UTC" +%Y-%m-%dT%H:%M:%SZ; }

# ── numeric helpers ──────────────────────────────────────────────────────────
# Bash has no float arithmetic; python is already a hard dependency.
num() { "$PY" -c "print($1)"; }

# ── lookups used by more than one stage ──────────────────────────────────────

# lot_id ITEM_ID WAREHOUSE_ID LOT_CODE
# There is no POST /inventory/lots — lots are born from a receive/goods-receipt/production-receipt
# that carries a lotCode, so the id has to be looked up afterwards.
lot_id() {
    call GET "/inventory/lots?warehouseId=$2&itemId=$1&size=200" \
        | extract "next(l['lotId'] for l in d['result']['content'] if l['lotCode']=='$3')"
}

# available_qty ITEM_ID WAREHOUSE_ID — summed across every lot row of that item/warehouse.
available_qty() {
    call GET "/inventory/balances?warehouseId=$2&itemId=$1&size=200" \
        | extract "sum(float(b['availableQuantity']) for b in d['result']['content'])"
}

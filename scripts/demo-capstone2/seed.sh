#!/usr/bin/env bash
# seed.sh — dựng bộ dữ liệu demo SẠCH cho Capstone 2 (namespace D26-) từ cơ sở dữ liệu trắng.
#
# Nguồn yêu cầu: BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx
#
# CHẠY BẰNG GIT BASH (không dùng PowerShell/CMD). Tên hiển thị là tiếng Việt có dấu; PowerShell
# 5.1 giải mã UTF-8 sai và sẽ ghi vào cơ sở dữ liệu những chuỗi hỏng kiểu "BÃ ...".
#
# QUY TRÌNH ĐẦY ĐỦ
#   1. Dừng backend đang chạy.
#   2. docker compose down -v && docker compose up -d
#   3. Khởi động backend một lần với APP_BOOTSTRAP_ADMIN_ENABLED=true để cấp tài khoản quản trị,
#      rồi khởi động lại với giá trị false.
#   4. ADMIN_PASSWORD=... ./scripts/demo-capstone2/seed.sh
#
# Script dùng MÃ CỐ ĐỊNH nên KHÔNG chạy lại được trên cơ sở dữ liệu đã có namespace D26-.
# Hỏng giữa chừng thì xoá sạch rồi chạy lại — đừng vá tay.
#
# Mọi thứ đi qua REST API thật, không có câu SQL nào (R3/R7 của tài liệu): mã chứng từ sinh ở
# @PrePersist, projection stock_balances, @Version và nhật ký kiểm toán chỉ tồn tại nếu tầng
# service thực sự chạy. Chèn thẳng vào cơ sở dữ liệu sẽ tạo ra những dòng mà chính hệ thống
# không bao giờ sinh ra được.
#
# Script DỪNG LẠI ở đơn bán hàng đã CONFIRMED. Nó CỐ Ý không chạy MRP (§9 của tài liệu:
# "Không seed Planning Run trước: Để người trình bày chạy Planning trực tiếp").

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Dat bien ADMIN_PASSWORD; script khong co mat khau mac dinh}"

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEMO_DIR/../.." && pwd)"
MANIFEST="${MANIFEST:-$REPO_ROOT/demo-capstone2-ids.json}"

BODY_TMP="$(mktemp)"

# Dùng lại nguyên bộ helper của seeder VIETBIKE: call/call_expect/extract/remember/assert_status
# đã được kiểm chứng trên chính API này (xử lý UTF-8, envelope, ghi manifest).
# shellcheck source=../demo/lib/common.sh
. "$REPO_ROOT/scripts/demo/lib/common.sh"
# shellcheck source=catalogue.sh
. "$DEMO_DIR/catalogue.sh"

on_exit() {
    local rc=$?
    rm -f "$BODY_TMP"
    if [ $rc -ne 0 ] && [ "${#RECORD_KEYS[@]}" -gt 0 ]; then
        dump_manifest "$REPO_ROOT/demo-capstone2-ids.partial.json" 2>/dev/null || true
        echo "" >&2
        echo "Seed THAT BAI. Nhung gi da kip tao duoc ghi o demo-capstone2-ids.partial.json" >&2
        echo "Xoa sach roi chay lai: docker compose down -v && docker compose up -d" >&2
    fi
}
trap on_exit EXIT

START_TS=$(date +%s)

################################################################################
# 0. Kiểm tra môi trường (§10 bước 1)
################################################################################
stage_banner "0. Kiểm tra môi trường"

[ "${BASH_VERSINFO:-0}" -ge 4 ] || die "cần bash >= 4 (đang chạy ${BASH_VERSION:-?}). Trên Windows hãy dùng Git Bash."
command -v curl >/dev/null 2>&1 || die "không tìm thấy curl trên PATH"

PY=python
command -v "$PY" >/dev/null 2>&1 || PY=python3
command -v "$PY" >/dev/null 2>&1 || die "cần python hoặc python3 trên PATH (script không dùng jq)"
"$PY" -c 'import json,sys' >/dev/null 2>&1 || die "$PY không chạy được"
info "python: $("$PY" -V 2>&1)"

date -u -d '+1 day' +%F >/dev/null 2>&1 \
    || die "cần GNU date (tham số -d). Trên macOS: brew install coreutils rồi dùng gdate."
info "hôm nay (UTC): $(d_today)"

for f in "$DEMO_DIR"/*.sh; do
    grep -q $'\r' "$f" 2>/dev/null && die "file $f có ký tự xuống dòng CRLF."
done
info "line endings: LF"

# seedRunId (§10 bước 1, §14 gói bàn giao)
SEED_RUN_ID="D26-$(date -u +%Y%m%dT%H%M%SZ)"
note "meta.seedRunId" "$SEED_RUN_ID"
note "meta.generatedAt" "$(ts_now)"
note "meta.sourceDocument" "BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx"
note "meta.gitCommit" "$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
remember "meta.baseUrl" "$BASE_URL"
info "seedRunId: $SEED_RUN_ID"

log "Đăng nhập $ADMIN_USERNAME tại $BASE_URL"
LOGIN_RESP=$(curl -sS -X POST "$BASE_URL/auth/v1/login" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" 2>&1) \
    || die "không gọi được $BASE_URL/auth/v1/login — backend đã chạy chưa? ($LOGIN_RESP)"
TOKEN=$(echo "$LOGIN_RESP" | rid accessToken 2>/dev/null || true)
[ -n "${TOKEN:-}" ] || { echo "$LOGIN_RESP" >&2; die "đăng nhập thất bại"; }
info "đăng nhập thành công"

# §15 dòng cuối: "Runtime OpenAPI của môi trường triển khai là nguồn chính xác cuối cùng về path
# và DTO. Script seed phải fail-fast nếu schema/version không khớp; không tự suy đoán field."
#
# 🔴 Kiểm tra này không phải trang trí. Bố cục URL của API là hỗn hợp — phần lớn controller nằm ở
# /api/v1/..., nhưng auth/users/access/sales-orders lại đặt "/v1" SAU tiền tố tài nguyên
# (/api/auth/v1/login, /api/sales-orders/v1). Đoán sai một đường dẫn sẽ trả 401
# AUTHENTICATION_REQUIRED thay vì 404, tức là trông hệt như sai mật khẩu.
log "Đối chiếu đường dẫn với OpenAPI runtime"
OPENAPI_PATHS=$(curl -sS "$BASE_URL/v3/api-docs" -H "Authorization: Bearer $TOKEN" \
    | "$PY" -c "import json,sys; print(' '.join(sorted(json.loads(sys.stdin.buffer.read().decode('utf-8'))['paths'])))") \
    || die "không đọc được $BASE_URL/v3/api-docs"
for required in \
    /v1/companies /v1/companies/{companyId}/plants /v1/plants/{plantId}/warehouses \
    /v1/uoms /v1/companies/{companyId}/items /v1/inventory/item-warehouse-settings \
    /v1/plants/{plantId}/shifts /v1/plants/{plantId}/work-calendars /v1/plants/{plantId}/work-centers \
    /v1/companies/{companyId}/boms /v1/boms/{bomId}/lines /v1/boms/{bomId}/activate \
    /v1/companies/{companyId}/routings /v1/routings/{routingId}/activate \
    /v1/inventory/receive /v1/inventory/balances \
    /sales-orders/v1 /sales-orders/v1/{salesOrderId}/confirm \
    /v1/planning/demands /v1/planning-runs \
    /users/v1 /access/v1/roles /access/v1/scopes /access/v1/assignments /auth/v1/me
do
    case " $OPENAPI_PATHS " in
        *" $required "*) ;;
        *) die "OpenAPI runtime KHÔNG có đường dẫn '$required' — hợp đồng API đã đổi, cập nhật script trước khi seed." ;;
    esac
done
info "$(echo "$OPENAPI_PATHS" | wc -w) đường dẫn trong OpenAPI; 25 đường dẫn bắt buộc đều có mặt"

# R2 nói bộ seed phải idempotent. Script này đạt điều đó bằng cách TỪ CHỐI chạy lần hai thay vì
# cố vá dữ liệu cũ: không endpoint tạo mới nào của API này replay-safe, và ma trận trạng thái
# không đảo ngược được. Chạy lại = xoá sạch rồi seed lại, cho ra đúng bộ dữ liệu ấy.
EXISTING=$(call GET "/v1/companies?page=0&size=200" \
    | extract "','.join(c['code'] for c in d['result']['content'])")
case ",$EXISTING," in
    *",$CO_CODE,"*)
        warn "DỪNG: công ty $CO_CODE đã tồn tại — namespace D26- đã được seed rồi."
        warn "Chạy: docker compose down -v && docker compose up -d, khởi động lại backend, rồi seed lại."
        exit 1 ;;
esac
info "cơ sở dữ liệu chưa có namespace $CO_CODE — tiếp tục"

################################################################################
# 1. Công ty, nhà máy, kho (§3.1, §10 bước 2+4)
################################################################################
stage_banner "1. Công ty, nhà máy, kho"

log "Tạo công ty $CO_CODE"
CO=$(call POST /v1/companies "{\"code\":\"$CO_CODE\",\"name\":\"$CO_NAME\"}" | rid companyId)
remember "company.id" "$CO"
note     "company.code" "$CO_CODE"

# Thời điểm sớm nhất chứng minh tiếng Việt sống sót qua shell -> curl -> Spring -> Postgres và
# quay lại. Dừng ngay với 1 dòng đã ghi, thay vì với 200 dòng hỏng nằm trên slide.
ECHOED=$(call GET "/v1/companies/$CO" | rid name)
if [ "$ECHOED" != "$CO_NAME" ]; then
    warn "DỪNG: tên tiếng Việt bị hỏng mã hoá trên đường truyền."
    warn "    đã gửi : $CO_NAME"
    warn "    nhận về: $ECHOED"
    die  "Chạy script bằng Git Bash và giữ file ở UTF-8 không BOM."
fi
info "UTF-8 round-trip OK: $ECHOED"

log "Tạo nhà máy $PLANT_CODE (múi giờ $PLANT_TZ)"
PLANT=$(call POST "/v1/companies/$CO/plants" \
    "{\"code\":\"$PLANT_CODE\",\"name\":\"$PLANT_NAME\",\"timezone\":\"$PLANT_TZ\"}" | rid plantId)
remember "plant.id" "$PLANT"
note     "plant.code" "$PLANT_CODE"
note     "plant.timezone" "$PLANT_TZ"

log "Tạo kho"
while IFS='|' read -r wcode wname wtype; do
    [ -z "$wcode" ] && continue
    wid=$(call POST "/v1/plants/$PLANT/warehouses" \
        "{\"code\":\"$wcode\",\"name\":\"$wname\",\"type\":\"$wtype\"}" | rid warehouseId)
    remember "warehouses.$wcode" "$wid"
    info "$wcode — $wname ($wtype)"
done <<WH
$(echo "$WAREHOUSE_ROWS" | sed '/^[[:space:]]*$/d')
WH

################################################################################
# 2. Phạm vi truy cập và tài khoản demo (§3.2, §10 bước 2)
################################################################################
stage_banner "2. Phân quyền và tài khoản demo"

ROLES_JSON=$(call GET "/access/v1/roles?page=0&size=100")
for rc in MANAGER OPERATOR; do
    remember "roles.$rc" "$(echo "$ROLES_JSON" \
        | extract "next(r['roleId'] for r in d['result']['content'] if r['code']=='$rc')")"
done
info "vai trò hệ thống: MANAGER, OPERATOR"

log "Tạo phạm vi $SCOPE_CODE"
SCOPE=$(call POST /access/v1/scopes \
    "{\"code\":\"$SCOPE_CODE\",\"name\":\"$SCOPE_NAME\",\"scopeType\":\"PLANT\",\"description\":\"Phạm vi dữ liệu demo Capstone 2\"}" \
    | rid scopeId)
remember "scope.id" "$SCOPE"
note     "scope.code" "$SCOPE_CODE"

# 🔴 Phạm vi kiểu PLANT chỉ nhận tài nguyên kiểu PLANT — AccessControlService.isResourceTypeAllowed
# từ chối WAREHOUSE với OPERATION_NOT_ALLOWED (bất biến RBAC siết lại ở V63). Và không cần liệt kê
# thêm: PermissionGuard.hasWarehouseAccess kiểm quyền trực tiếp trên kho TRƯỚC, rồi mới lùi về nhà
# máy và công ty của kho đó — nên một tài nguyên PLANT phủ trọn cả 5 kho của nhà máy.
call POST "/access/v1/scopes/$SCOPE/resources" \
    "{\"resourceType\":\"PLANT\",\"resourceId\":\"$PLANT\"}" >/dev/null
info "phạm vi gồm nhà máy $PLANT_CODE (phủ cả 5 kho qua quan hệ cha-con)"

log "Tạo tài khoản demo"
create_demo_user() {  # create_demo_user USERNAME EMAIL ROLE_CODE
    local uname="$1" email="$2" rolecode="$3" uid slug
    uid=$(call POST /users/v1 \
        "{\"username\":\"$uname\",\"email\":\"$email\",\"password\":\"$DEMO_PASSWORD\"}" | rid userId)
    # Khoá manifest lồng theo dấu chấm, mà username có dấu chấm — slug hoá khoá, giữ username
    # thật làm giá trị.
    slug="${uname//./_}"
    remember "users.$slug.id" "$uid"
    note     "users.$slug.username" "$uname"
    note     "users.$slug.role" "$rolecode"
    note     "users.$slug.scope" "$SCOPE_CODE"
    call POST /access/v1/assignments \
        "{\"userId\":\"$uid\",\"roleId\":\"${ID[roles.$rolecode]}\",\"scopeId\":\"$SCOPE\"}" >/dev/null
    info "$uname -> $rolecode @ $SCOPE_CODE"
}
create_demo_user "$USER_MANAGER"  "$USER_MANAGER_EMAIL"  MANAGER
create_demo_user "$USER_OPERATOR" "$USER_OPERATOR_EMAIL" OPERATOR

################################################################################
# 3. Đơn vị tính (§10 bước 3)
################################################################################
stage_banner "3. Đơn vị tính"

# UOM là master data TOÀN CỤC (không thuộc công ty nào), nên mã không mang tiền tố D26-.
UOMS_JSON=$(call GET "/v1/uoms?page=0&size=200")
while IFS='|' read -r ucode uname udesc; do
    [ -z "$ucode" ] && continue
    found=$(echo "$UOMS_JSON" | extract "next((u['uomId'] for u in d['result']['content'] if u['code']=='$ucode'), '')")
    if [ -n "$found" ]; then
        remember "uoms.$ucode" "$found"
        info "$ucode đã có — dùng lại"
    else
        remember "uoms.$ucode" "$(call POST /v1/uoms \
            "{\"code\":\"$ucode\",\"name\":\"$uname\",\"description\":\"$udesc\"}" | rid uomId)"
        info "$ucode — $uname"
    fi
done <<UOMS
$(echo "$UOM_ROWS" | sed '/^[[:space:]]*$/d')
UOMS

################################################################################
# 4. Danh mục vật tư (§4, §10 bước 5)
################################################################################
stage_banner "4. Danh mục vật tư"

log "Tạo vật tư"
while IFS='|' read -r code name type unit lot; do
    [ -z "$code" ] && continue
    # serialTracked luôn false — Capstone 2 không dùng serial tracking (§1).
    iid=$(call POST "/v1/companies/$CO/items" \
        "{\"code\":\"$code\",\"name\":\"$name\",\"type\":\"$type\",\"unit\":\"$unit\",\"lotTracked\":$lot,\"serialTracked\":false}" \
        | rid itemId)
    remember "items.$code" "$iid"
    info "$code — $name ($type, $unit, lotTracked=$lot)"
done <<ITEMS
$(echo "$ITEM_ROWS" | sed '/^[[:space:]]*$/d')
ITEMS

################################################################################
# 5. Chính sách kho theo vật tư (§5, §10 bước 6)
################################################################################
stage_banner "5. Chính sách kho và tham số hoạch định"

# PUT là upsert nguyên tử mang cả ngưỡng lẫn cờ default (§5 dòng cuối), nên không có khoảnh khắc
# nào một vật tư tồn tại với cấu hình nửa vời.
log "Đặt cấu hình item-warehouse"
while IFS='|' read -r icode wcode dsupply doutput safety rop lead; do
    [ -z "$icode" ] && continue
    call PUT /v1/inventory/item-warehouse-settings \
        "{\"itemId\":\"${ID[items.$icode]}\",\"warehouseId\":\"${ID[warehouses.$wcode]}\",\"safetyStock\":$safety,\"reorderPoint\":$rop,\"leadTimeDays\":$lead,\"defaultSupply\":$dsupply,\"defaultOutput\":$doutput}" \
        >/dev/null
    info "$icode @ $wcode (supply=$dsupply, output=$doutput, lead=${lead}d)"
done <<IWS
$(echo "$IWS_ROWS" | sed '/^[[:space:]]*$/d')
IWS

################################################################################
# 6. Ca làm việc, lịch, tổ sản xuất (§6, §10 bước 7)
################################################################################
stage_banner "6. Ca làm việc, lịch làm việc, tổ sản xuất"

log "Tạo ca làm việc $SHIFT_CODE"
SHIFT=$(call POST "/v1/plants/$PLANT/shifts" \
    "{\"code\":\"$SHIFT_CODE\",\"name\":\"$SHIFT_NAME\",\"startTime\":\"$SHIFT_START\",\"endTime\":\"$SHIFT_END\",\"breaks\":[{\"startTime\":\"$SHIFT_BREAK_START\",\"endTime\":\"$SHIFT_BREAK_END\"}]}" \
    | rid shiftId)
remember "shift.id" "$SHIFT"
note     "shift.code" "$SHIFT_CODE"

log "Tạo lịch làm việc $CAL_CODE"
WEEKLY="["
first=1
IFS=',' read -ra days <<<"$CAL_WEEKDAYS"
for wd in "${days[@]}"; do
    [ $first -eq 0 ] && WEEKLY="$WEEKLY,"
    WEEKLY="$WEEKLY{\"weekday\":\"$wd\",\"shiftId\":\"$SHIFT\"}"
    first=0
done
WEEKLY="$WEEKLY]"
# exceptions rỗng có chủ đích: một ngày NON_WORKING rơi trúng ngày trình diễn sẽ làm lịch sản
# xuất trượt đi vì một lý do chẳng liên quan gì tới câu chuyện đang kể.
CAL=$(call POST "/v1/plants/$PLANT/work-calendars" \
    "{\"code\":\"$CAL_CODE\",\"name\":\"$CAL_NAME\",\"effectiveFrom\":\"$(d_minus $CAL_EFFECTIVE_DAYS_AGO)\",\"weeklyShifts\":$WEEKLY,\"exceptions\":[]}" \
    | rid workCalendarId)
remember "workCalendar.id" "$CAL"
note     "workCalendar.code" "$CAL_CODE"
info "$CAL_CODE — 6 ngày/tuần, hiệu lực từ $(d_minus $CAL_EFFECTIVE_DAYS_AGO)"

# Tổ sản xuất tạo TRƯỚC rồi mới PATCH lịch vào: WorkCenter trỏ tới WorkCalendar, còn
# WorkCalendar trỏ tới Shift của cùng nhà máy — phụ thuộc vòng, chỉ đi được theo chiều này.
log "Tạo tổ sản xuất và gắn lịch $CAL_CODE"
while IFS='|' read -r code name captype capunits; do
    [ -z "$code" ] && continue
    wcid=$(call POST "/v1/plants/$PLANT/work-centers" \
        "{\"code\":\"$code\",\"name\":\"$name\",\"description\":\"Tổ sản xuất bộ dữ liệu demo\",\"capacityUnitType\":\"$captype\",\"capacityUnits\":$capunits}" \
        | rid workCenterId)
    call PATCH "/v1/work-centers/$wcid" "{\"workCalendarId\":\"$CAL\"}" >/dev/null
    remember "workCenters.$code" "$wcid"
    info "$code — $name ($captype x$capunits) -> $CAL_CODE"
done <<WCS
$(echo "$WC_ROWS" | sed '/^[[:space:]]*$/d')
WCS

################################################################################
# 7. Định mức vật tư (§7, §10 bước 8)
################################################################################
stage_banner "7. Định mức vật tư (BOM)"

# Thứ tự trong BOM_ROWS đã là thứ tự kích hoạt: con trước, cha sau.
while IFS='|' read -r parent revision doccode lines; do
    [ -z "$parent" ] && continue
    log "Tạo $doccode cho $parent"
    bid=$(call POST "/v1/companies/$CO/boms" \
        "{\"parentItemId\":\"${ID[items.$parent]}\",\"revision\":\"$revision\",\"description\":\"$doccode — định mức sản xuất $parent bản $revision\"}" \
        | rid bomId)
    remember "boms.$doccode" "$bid"

    lineno=1
    IFS=';' read -ra lrows <<<"$lines"
    for l in "${lrows[@]}"; do
        IFS=':' read -r comp qty scrap <<<"$l"
        call POST "/v1/boms/$bid/lines" \
            "{\"componentItemId\":\"${ID[items.$comp]}\",\"lineNo\":$lineno,\"quantityPer\":$qty,\"scrapRate\":$scrap}" \
            >/dev/null
        info "  dòng $lineno: $comp x $qty"
        lineno=$((lineno + 1))
    done

    call POST "/v1/boms/$bid/activate" >/dev/null
    assert_status "/v1/boms/$bid" "ACTIVE"
done <<BOMS
$(echo "$BOM_ROWS" | sed '/^[[:space:]]*$/d')
BOMS

################################################################################
# 8. Quy trình công nghệ (§6, §10 bước 9)
################################################################################
stage_banner "8. Quy trình công nghệ (Routing)"

while IFS='|' read -r code version item ops; do
    [ -z "$code" ] && continue
    opjson="["
    first=1
    IFS=';' read -ra oplist <<<"$ops"
    for o in "${oplist[@]}"; do
        IFS=':' read -r seq opname wc setup runmin <<<"$o"
        [ $first -eq 0 ] && opjson="$opjson,"
        opjson="$opjson{\"sequence\":$seq,\"name\":\"$opname\",\"workCenterId\":\"${ID[workCenters.$wc]}\",\"setupMinutes\":$setup,\"runMinutesPerUnit\":$runmin}"
        first=0
    done
    opjson="$opjson]"

    log "Tạo $code bản $version cho $item"
    rtid=$(call POST "/v1/companies/$CO/routings" \
        "{\"itemId\":\"${ID[items.$item]}\",\"code\":\"$code\",\"version\":\"$version\",\"note\":\"Quy trình công nghệ bộ dữ liệu demo cho $item\",\"operations\":$opjson}" \
        | rid routingId)
    call POST "/v1/routings/$rtid/activate" >/dev/null
    assert_status "/v1/routings/$rtid" "ACTIVE"
    remember "routings.$code" "$rtid"
done <<RTS
$(echo "$ROUTING_ROWS" | sed '/^[[:space:]]*$/d')
RTS

################################################################################
# 9. Tồn kho đầu kỳ (§8, §10 bước 10)
################################################################################
stage_banner "9. Tồn kho đầu kỳ"

n=0
while IFS='|' read -r icode wcode qty; do
    [ -z "$icode" ] && continue
    n=$((n + 1))
    # R2: khoá idempotency ổn định, gắn với vật tư chứ không gắn với thời điểm chạy — gửi lại
    # cùng khoá KHÔNG cộng tồn hai lần.
    key="D26-OPENING-$icode"
    call POST /v1/inventory/receive \
        "{\"itemId\":\"${ID[items.$icode]}\",\"warehouseId\":\"${ID[warehouses.$wcode]}\",\"quantity\":$qty,\"reason\":\"Tồn kho đầu kỳ bộ dữ liệu demo Capstone 2\",\"referenceType\":\"D26_OPENING_STOCK\",\"referenceId\":\"$SEED_RUN_ID\"}" \
        -H "Idempotency-Key: $key" >/dev/null
    note "openingStock.$icode" "$qty @ $wcode"
    info "$icode -> $wcode: $qty"
done <<STK
$(echo "$STOCK_ROWS" | sed '/^[[:space:]]*$/d')
STK
info "$n dòng tồn kho đầu kỳ"

# D26-FG-BIKE16 cố ý KHÔNG có tồn kho và KHÔNG có lô nào (§8). Đó là thứ làm MRP sinh ra đúng
# một đề xuất MAKE khi người trình bày chạy Planning.

################################################################################
# 10. (Đã bỏ) — KHÔNG seed Sales Order ở đây, quyết định của user 2026-08-22
################################################################################

# 🔴 KHÔNG seed Sales Order ở đây — người vận hành tự tạo đơn hàng của riêng mình mỗi lần demo,
# để có thể seed lại phần còn lại của bộ dữ liệu (tổ chức, vật tư, BOM/routing, tồn kho) NHIỀU
# LẦN mà không dính một đơn hàng cố định lặp đi lặp lại. Script dừng lại đúng ở cuối tồn kho đầu
# kỳ — mọi thứ từ Sales Order trở đi (Planning, Work Order, Reservation, Issue, Execution,
# Receipt, QC) là việc của người trình bày, làm thủ công qua UI/API.

################################################################################
# 11. Preflight chỉ đọc (§11) — PF-01..PF-08, PF-15 (PF-09/PF-11 cần Sales Order, đã bỏ)
################################################################################
stage_banner "11. Preflight (chỉ đọc)"

PF_FAILED=0
pf() {  # pf ID MÔ_TẢ ACTUAL EXPECTED
    if [ "$3" = "$4" ]; then
        info "$1 PASS — $2"
    else
        warn "$1 FAIL — $2 (nhận '$3', cần '$4')"
        PF_FAILED=$((PF_FAILED + 1))
    fi
}

# PF-01 Plant/Warehouse ACTIVE, cùng scope
pf PF-01 "nhà máy $PLANT_CODE ACTIVE" \
    "$(call GET "/v1/plants/$PLANT" | rid status)" "ACTIVE"
pf PF-01 "5 kho ACTIVE của nhà máy" \
    "$(call GET "/v1/plants/$PLANT/warehouses?page=0&size=100" \
        | extract "sum(1 for w in d['result']['content'] if w['status']=='ACTIVE')")" "5"

# PF-02 10 Item ACTIVE, tracking đúng, không SERIAL
ITEMS_JSON=$(call GET "/v1/companies/$CO/items?page=0&size=200")
pf PF-02 "10 vật tư ACTIVE" \
    "$(echo "$ITEMS_JSON" | extract "sum(1 for i in d['result']['content'] if i['status']=='ACTIVE')")" "10"
pf PF-02 "đúng 1 vật tư lot-tracked (thành phẩm)" \
    "$(echo "$ITEMS_JSON" | extract "sum(1 for i in d['result']['content'] if i['lotTracked'])")" "1"
pf PF-02 "không vật tư nào serial-tracked" \
    "$(echo "$ITEMS_JSON" | extract "sum(1 for i in d['result']['content'] if i.get('serialTracked'))")" "0"

# PF-03/PF-04 mỗi vật tư đúng một cấu hình kho ACTIVE trong nhà máy (nhiều hơn một =
# AMBIGUOUS_WAREHOUSE_POLICY), và cấu hình đó tồn tại nên settingSource = ITEM_WAREHOUSE.
IWS_JSON=$(call GET "/v1/inventory/item-warehouse-settings?plantId=$PLANT&page=0&size=200")
pf PF-03 "10 cấu hình item-warehouse ACTIVE" \
    "$(echo "$IWS_JSON" | extract "sum(1 for s in d['result']['content'] if s['status']=='ACTIVE')")" "10"
pf PF-03 "không vật tư nào có quá một cấu hình" \
    "$(echo "$IWS_JSON" | extract "max([sum(1 for x in d['result']['content'] if x['itemId']==s['itemId']) for s in d['result']['content']] or [0])")" "1"
pf PF-04 "9 dòng defaultSupply (mọi vật tư trừ thành phẩm)" \
    "$(echo "$IWS_JSON" | extract "sum(1 for s in d['result']['content'] if s['defaultSupply'])")" "9"
pf PF-04 "3 dòng defaultOutput (thành phẩm + 2 bán thành phẩm)" \
    "$(echo "$IWS_JSON" | extract "sum(1 for s in d['result']['content'] if s['defaultOutput'])")" "3"

# PF-05 BOM
pf PF-05 "3 định mức ACTIVE" \
    "$(call GET "/v1/companies/$CO/boms?page=0&size=100" \
        | extract "sum(1 for b in d['result']['content'] if b['status']=='ACTIVE')")" "3"

# PF-06/PF-07 Routing + Work Center
pf PF-06 "3 quy trình ACTIVE" \
    "$(call GET "/v1/companies/$CO/routings?page=0&size=100" \
        | extract "sum(1 for r in d['result']['content'] if r['status']=='ACTIVE')")" "3"
pf PF-07 "4 tổ sản xuất ACTIVE" \
    "$(call GET "/v1/plants/$PLANT/work-centers?page=0&size=100" \
        | extract "sum(1 for w in d['result']['content'] if w['status']=='ACTIVE')")" "4"

# PF-08 tồn kho: available đủ cho lệnh sản xuất 10 xe, reserved = 0
check_avail() {  # check_avail ITEM_CODE WAREHOUSE_CODE EXPECTED
    local got
    got=$(call GET "/v1/inventory/balances?warehouseId=${ID[warehouses.$2]}&itemId=${ID[items.$1]}&size=100" \
        | extract "'%g' % sum(float(b['availableQuantity']) for b in d['result']['content'])")
    pf PF-08 "$1 khả dụng tại $2" "$got" "$3"
}
check_avail D26-WIP-FRAME16 D26-WIP 12
check_avail D26-WIP-DRIVE7  D26-WIP 12
check_avail D26-RM-WHEEL16  D26-RM  24
check_avail D26-RM-BRAKE    D26-RM  12
check_avail D26-FG-BIKE16   D26-FG  0
pf PF-08 "chưa có lượng nào bị giữ chỗ ở kho bán thành phẩm" \
    "$(call GET "/v1/inventory/balances?warehouseId=${ID[warehouses.$WH_WIP]}&size=200" \
        | extract "'%g' % sum(float(b['reservedQuantity']) for b in d['result']['content'])")" "0"

# PF-09/PF-11 (Sales Order + nhu cầu hoạch định) KHÔNG chạy ở đây nữa — không còn Sales Order
# nào do script tạo ra để kiểm. Người vận hành tự xác nhận hai mục này sau khi tạo đơn hàng riêng.

# PF-15 RBAC: hai tài khoản vận hành đăng nhập được và thấy đúng nhà máy demo
check_login() {  # check_login USERNAME
    local resp tok
    resp=$(curl -sS -X POST "$BASE_URL/auth/v1/login" -H "Content-Type: application/json; charset=utf-8" \
        -d "{\"username\":\"$1\",\"password\":\"$DEMO_PASSWORD\"}")
    tok=$(echo "$resp" | rid accessToken 2>/dev/null || echo "")
    if [ -z "$tok" ]; then
        pf PF-15 "$1 đăng nhập" "FAIL" "OK"
        return
    fi
    pf PF-15 "$1 đăng nhập" "OK" "OK"
    pf PF-15 "$1 thấy nhà máy $PLANT_CODE trong phạm vi" \
        "$(curl -sS "$BASE_URL/auth/v1/me" -H "Authorization: Bearer $tok" \
            | extract "sum(1 for s in d['result']['scopes'] if s.get('plantCode')=='$PLANT_CODE')")" "1"
}
check_login "$USER_MANAGER"
check_login "$USER_OPERATOR"

################################################################################
# 12. Manifest bàn giao (§14)
################################################################################
stage_banner "12. Manifest bàn giao"

note "demo.outputLotCode" "$DEMO_OUTPUT_LOT"
note "preflight.readOnlyFailures" "$PF_FAILED"
dump_manifest "$MANIFEST"
info "đã ghi $MANIFEST"

if [ "$PF_FAILED" -ne 0 ]; then
    die "$PF_FAILED mục preflight KHÔNG đạt — không bàn giao môi trường này."
fi

log "Preflight chỉ đọc: PASS toàn bộ"
log "Hoàn tất trong $(( $(date +%s) - START_TS )) giây"

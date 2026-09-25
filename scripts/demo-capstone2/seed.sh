#!/usr/bin/env bash
# seed.sh — dựng bộ dữ liệu demo cho Capstone 2 (namespace D26-) từ cơ sở dữ liệu trắng.
#
# Nguồn yêu cầu: BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx
#
# 🔴 BẢN 2026-08-23 ĐI HẾT LUỒNG NGHIỆP VỤ, khác hẳn bản trước (chỉ dừng ở tồn kho đầu kỳ).
#    Mục đích: mọi màn hình đều có dữ liệu để chụp hình làm slide — Dashboard, danh mục, định
#    mức, quy trình, bảng năng lực, nhà cung cấp, mua hàng, bán hàng, hoạch định (MRP), lệnh
#    sản xuất ở đủ trạng thái, phiếu xuất/nhập, lô hàng, chênh lệch chi phí, nhật ký kiểm toán.
#    Dữ liệu vẫn SẠCH theo tinh thần tài liệu gốc: không bản ghi lỗi, không BLOCKED, không lô
#    REJECTED, không phiếu bị từ chối.
#
# CHẠY BẰNG GIT BASH (không dùng PowerShell/CMD). Tên hiển thị có ký tự ngoài ASCII; PowerShell
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

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Dat bien ADMIN_PASSWORD; script khong co mat khau mac dinh}"

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEMO_DIR/../.." && pwd)"
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
        dump_manifest "$REPO_ROOT/demo-capstone2-ids.${PROFILE:-sample}.partial.json" 2>/dev/null || true
        echo "" >&2
        echo "Seed THAT BAI. Nhung gi da kip tao duoc ghi o demo-capstone2-ids.<profile>.partial.json" >&2
        echo "Xoa sach roi chay lai: docker compose down -v && docker compose up -d" >&2
    fi
}
trap on_exit EXIT

START_TS=$(date +%s)

# rows LIST — bỏ dòng trống của một bảng dữ liệu trong catalogue.sh.
rows() { echo "$1" | sed '/^[[:space:]]*$/d'; }

################################################################################
# Hồ sơ chạy (PROFILE) — một hệ thống chứa HAI công ty
################################################################################
#
#   PROFILE=sample (mặc định) — công ty DỮ LIỆU MẪU, namespace D26-.
#       Đi hết luồng: mua hàng, bán hàng, MRP, lệnh sản xuất, xuất/nhập, QC.
#       Dùng để chụp hình giao diện bỏ vào slide.
#
#   PROFILE=demo — công ty TRÌNH DIỄN TRỰC TIẾP, namespace DEMO-.
#       Dừng lại ở tồn kho đầu kỳ, KHÔNG có đơn hàng / MRP / lệnh sản xuất nào —
#       người trình bày tự tạo trên sân khấu (đúng §9 của tài liệu gốc:
#       "Không seed Planning Run trước").
#
# Hai công ty sống chung một cơ sở dữ liệu, không đụng nhau: mọi mã đều mang namespace riêng,
# tài khoản mang đuôi riêng, và mọi bất biến (RBAC, cấu hình kho, netting) đều theo từng nhà máy.
# Chạy lần lượt hai lượt trên cùng một cơ sở dữ liệu:
#   ADMIN_PASSWORD=... PROFILE=sample ./scripts/demo-capstone2/seed.sh
#   ADMIN_PASSWORD=... PROFILE=demo   ./scripts/demo-capstone2/seed.sh
PROFILE="${PROFILE:-sample}"
case "$PROFILE" in
    sample) NS="D26";  USER_TAG="sample"; CO_SUFFIX=" (Sample Data)";   SEED_TRANSACTIONS=yes ;;
    demo)   NS="DEMO"; USER_TAG="demo";   CO_SUFFIX=" (Live Demo)";     SEED_TRANSACTIONS=no  ;;
    *) die "PROFILE phải là 'sample' hoặc 'demo' (đang nhận '$PROFILE')" ;;
esac

# catalogue.sh viết cứng namespace D26-; đổi tiền tố tại chỗ thay vì nhân đôi cả bảng dữ liệu.
# Mọi chuỗi chứa "D26-" trong catalogue đều LÀ mã namespace, không có ngoại lệ nào.
if [ "$NS" != "D26" ]; then
    for var in WAREHOUSE_ROWS ITEM_ROWS IWS_ROWS SHIFT_ROWS CALENDAR_ROWS WC_ROWS ROUTING_ROWS                BOM_ROWS COST_ROWS STOCK_ROWS SUPPLIER_ROWS SUPPLIER_ITEM_ROWS PR_ROWS PO_ROWS                SO_ROWS WO_ROWS CO_CODE PLANT_CODE SCOPE_CODE WH_RM WH_WIP WH_FG WH_QC WH_SCRAP                SHIFT_DAY SHIFT_EVE PR3_PO_NO LOT_SO1004 LOT_SO1005 LOT_WO2004 LOT_WO2005                DEMO_OUTPUT_LOT
    do
        eval "$var=\"\${$var//D26-/$NS-}\""
    done
fi
CO_NAME="$CO_NAME$CO_SUFFIX"
SCOPE_NAME="$SCOPE_NAME ($PROFILE)"
# Tên đăng nhập là duy nhất TOÀN HỆ THỐNG, nên mỗi công ty một đuôi riêng.
USER_ROWS="${USER_ROWS//.demo/.$USER_TAG}"
MANIFEST="${MANIFEST:-$REPO_ROOT/demo-capstone2-ids.$PROFILE.json}"

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

SEED_RUN_ID="$NS-$(date -u +%Y%m%dT%H%M%SZ)"
note "meta.seedRunId" "$SEED_RUN_ID"
note "meta.generatedAt" "$(ts_now)"
note "meta.sourceDocument" "BE_CLEAN_DEMO_DATA_GUIDE_2026-08-22.docx"
note "meta.gitCommit" "$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
remember "meta.baseUrl" "$BASE_URL"
note "meta.profile" "$PROFILE"
info "seedRunId: $SEED_RUN_ID (profile $PROFILE, namespace $NS-)"

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
    /v1/companies/{companyId}/items/{itemId}/standard-cost \
    /v1/suppliers /v1/items/{itemId}/suppliers \
    /v1/purchase-requisitions /v1/purchase-requisitions/{requisitionId}/approve \
    /v1/purchase-requisitions/{requisitionId}/convert-to-purchase-order \
    /v1/purchase-orders /v1/purchase-orders/{purchaseOrderId}/send \
    /v1/purchase-orders/{purchaseOrderId}/goods-receipts \
    /v1/inventory/receive /v1/inventory/balances /v1/inventory/lots \
    /sales-orders/v1 /sales-orders/v1/{salesOrderId}/confirm \
    /v1/planning/demands /v1/planning-runs \
    /v1/supply-suggestions/{suggestionId}/approve \
    /v1/supply-suggestions/{suggestionId}/convert-to-work-order \
    /v1/plants/{plantId}/work-orders /v1/work-orders/{workOrderId}/reserve \
    /v1/work-orders/{workOrderId}/release /v1/work-orders/{workOrderId}/close \
    /v1/work-orders/{workOrderId}/material-issues \
    /v1/work-orders/{workOrderId}/production-executions \
    /v1/work-orders/{workOrderId}/production-receipts \
    /v1/plants/{plantId}/capacity-board \
    /users/v1 /access/v1/roles /access/v1/scopes /access/v1/assignments /auth/v1/me
do
    case " $OPENAPI_PATHS " in
        *" $required "*) ;;
        *) die "OpenAPI runtime KHÔNG có đường dẫn '$required' — hợp đồng API đã đổi, cập nhật script trước khi seed." ;;
    esac
done
info "$(echo "$OPENAPI_PATHS" | wc -w) đường dẫn trong OpenAPI; mọi đường dẫn bắt buộc đều có mặt"

# R2 nói bộ seed phải idempotent. Script này đạt điều đó bằng cách TỪ CHỐI chạy lần hai thay vì
# cố vá dữ liệu cũ: không endpoint tạo mới nào của API này replay-safe, và ma trận trạng thái
# không đảo ngược được. Chạy lại = xoá sạch rồi seed lại, cho ra đúng bộ dữ liệu ấy.
EXISTING=$(call GET "/v1/companies?page=0&size=200" \
    | extract "','.join(c['code'] for c in d['result']['content'])")
case ",$EXISTING," in
    *",$CO_CODE,"*)
        warn "DỪNG: công ty $CO_CODE đã tồn tại — namespace $NS- đã được seed rồi."
        warn "Chạy: docker compose down -v && docker compose up -d, khởi động lại backend, rồi seed lại."
        exit 1 ;;
esac
info "cơ sở dữ liệu chưa có công ty $CO_CODE — tiếp tục"

################################################################################
# 1. Công ty, nhà máy, kho (§3.1, §10 bước 2+4)
################################################################################
stage_banner "1. Công ty, nhà máy, kho"

log "Tạo công ty $CO_CODE"
CO=$(call POST /v1/companies "{\"code\":\"$CO_CODE\",\"name\":\"$CO_NAME\"}" | rid companyId)
remember "company.id" "$CO"
note     "company.code" "$CO_CODE"

# Thời điểm sớm nhất chứng minh chuỗi nhiều byte sống sót qua shell -> curl -> Spring -> Postgres
# và quay lại. Dừng ngay với 1 dòng đã ghi, thay vì với 400 dòng hỏng nằm trên slide.
ECHOED=$(call GET "/v1/companies/$CO" | rid name)
if [ "$ECHOED" != "$CO_NAME" ]; then
    warn "DỪNG: tên bị hỏng mã hoá trên đường truyền."
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
done < <(rows "$WAREHOUSE_ROWS")

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
    "{\"code\":\"$SCOPE_CODE\",\"name\":\"$SCOPE_NAME\",\"scopeType\":\"PLANT\",\"description\":\"Data scope for the Capstone 2 demo\"}" \
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

ASSIGNMENT_EXPIRES_AT=$(date -u -d "+$ASSIGNMENT_EXPIRY_DAYS days" +%Y-%m-%dT%H:%M:%SZ)

log "Tạo tài khoản demo"
while IFS='|' read -r uname email rolecode; do
    [ -z "$uname" ] && continue
    uid=$(call POST /users/v1 \
        "{\"username\":\"$uname\",\"email\":\"$email\",\"password\":\"$DEMO_PASSWORD\"}" | rid userId)
    # Khoá manifest lồng theo dấu chấm, mà username có dấu chấm — slug hoá khoá, giữ username
    # thật làm giá trị.
    slug="${uname//./_}"
    remember "users.$slug.id" "$uid"
    note     "users.$slug.username" "$uname"
    note     "users.$slug.role" "$rolecode"
    note     "users.$slug.scope" "$SCOPE_CODE"
    # expiresAt có giá trị thật thay vì để trống: màn hình phân quyền có cột "Expiry".
    # 🔴 Quá hạn là MẤT quyền — xem ghi chú ASSIGNMENT_EXPIRY_DAYS trong catalogue.sh.
    call POST /access/v1/assignments \
        "{\"userId\":\"$uid\",\"roleId\":\"${ID[roles.$rolecode]}\",\"scopeId\":\"$SCOPE\",\"expiresAt\":\"$ASSIGNMENT_EXPIRES_AT\"}" >/dev/null
    note "users.$slug.expiresAt" "$ASSIGNMENT_EXPIRES_AT"
    info "$uname -> $rolecode @ $SCOPE_CODE (hết hạn $ASSIGNMENT_EXPIRES_AT)"
done < <(rows "$USER_ROWS")

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
done < <(rows "$UOM_ROWS")

################################################################################
# 4. Danh mục vật tư (§4, §10 bước 5)
################################################################################
stage_banner "4. Danh mục vật tư"

ITEM_COUNT=0
while IFS='|' read -r code name type unit lot; do
    [ -z "$code" ] && continue
    # serialTracked luôn false — Capstone 2 không dùng serial tracking (§1).
    iid=$(call POST "/v1/companies/$CO/items" \
        "{\"code\":\"$code\",\"name\":\"$name\",\"type\":\"$type\",\"unit\":\"$unit\",\"lotTracked\":$lot,\"serialTracked\":false}" \
        | rid itemId)
    remember "items.$code" "$iid"
    ITEM_COUNT=$((ITEM_COUNT + 1))
    info "$code — $name ($type, $unit, lotTracked=$lot)"
done < <(rows "$ITEM_ROWS")
info "$ITEM_COUNT vật tư"

################################################################################
# 5. Chính sách kho theo vật tư (§5, §10 bước 6)
################################################################################
stage_banner "5. Chính sách kho và tham số hoạch định"

# PUT là upsert nguyên tử mang cả ngưỡng lẫn cờ default (§5 dòng cuối), nên không có khoảnh khắc
# nào một vật tư tồn tại với cấu hình nửa vời.
IWS_COUNT=0
while IFS='|' read -r icode wcode dsupply doutput safety rop lead; do
    [ -z "$icode" ] && continue
    call PUT /v1/inventory/item-warehouse-settings \
        "{\"itemId\":\"${ID[items.$icode]}\",\"warehouseId\":\"${ID[warehouses.$wcode]}\",\"safetyStock\":$safety,\"reorderPoint\":$rop,\"leadTimeDays\":$lead,\"defaultSupply\":$dsupply,\"defaultOutput\":$doutput}" \
        >/dev/null
    IWS_COUNT=$((IWS_COUNT + 1))
done < <(rows "$IWS_ROWS")
info "$IWS_COUNT cấu hình item-warehouse"

################################################################################
# 6. Ca làm việc, lịch, tổ sản xuất (§6, §10 bước 7)
################################################################################
stage_banner "6. Ca làm việc, lịch làm việc, tổ sản xuất"

log "Tạo ca làm việc"
while IFS='|' read -r scode sname sstart send bstart bend; do
    [ -z "$scode" ] && continue
    if [ -n "$bstart" ]; then
        breaks="[{\"startTime\":\"$bstart\",\"endTime\":\"$bend\"}]"
    else
        breaks="[]"
    fi
    sid=$(call POST "/v1/plants/$PLANT/shifts" \
        "{\"code\":\"$scode\",\"name\":\"$sname\",\"startTime\":\"$sstart\",\"endTime\":\"$send\",\"breaks\":$breaks}" \
        | rid shiftId)
    remember "shifts.$scode" "$sid"
    info "$scode — $sname"
done < <(rows "$SHIFT_ROWS")

log "Tạo lịch làm việc"
# exceptions rỗng có chủ đích: một ngày NON_WORKING rơi trúng ngày trình diễn sẽ làm lịch sản
# xuất trượt đi vì một lý do chẳng liên quan gì tới câu chuyện đang kể.
while IFS='|' read -r ccode cname cshifts cdays; do
    [ -z "$ccode" ] && continue
    weekly="["; first=1
    IFS=',' read -ra days <<<"$cdays"
    IFS=',' read -ra shiftcodes <<<"$cshifts"
    for wd in "${days[@]}"; do
        for sc in "${shiftcodes[@]}"; do
            [ $first -eq 0 ] && weekly="$weekly,"
            weekly="$weekly{\"weekday\":\"$wd\",\"shiftId\":\"${ID[shifts.$sc]}\"}"
            first=0
        done
    done
    weekly="$weekly]"
    cid=$(call POST "/v1/plants/$PLANT/work-calendars" \
        "{\"code\":\"$ccode\",\"name\":\"$cname\",\"effectiveFrom\":\"$(d_minus $CAL_EFFECTIVE_DAYS_AGO)\",\"weeklyShifts\":$weekly,\"exceptions\":[]}" \
        | rid workCalendarId)
    remember "workCalendars.$ccode" "$cid"
    info "$ccode — $cname (${#days[@]} ngày/tuần x ${#shiftcodes[@]} ca)"
done < <(rows "$CALENDAR_ROWS")

# Tổ sản xuất tạo TRƯỚC rồi mới PATCH lịch vào: WorkCenter trỏ tới WorkCalendar, còn
# WorkCalendar trỏ tới Shift của cùng nhà máy — phụ thuộc vòng, chỉ đi được theo chiều này.
log "Tạo tổ sản xuất và gắn lịch"
while IFS='|' read -r code name captype capunits calcode; do
    [ -z "$code" ] && continue
    wcid=$(call POST "/v1/plants/$PLANT/work-centers" \
        "{\"code\":\"$code\",\"name\":\"$name\",\"description\":\"Work center for the demo dataset\",\"capacityUnitType\":\"$captype\",\"capacityUnits\":$capunits}" \
        | rid workCenterId)
    call PATCH "/v1/work-centers/$wcid" "{\"workCalendarId\":\"${ID[workCalendars.$calcode]}\"}" >/dev/null
    remember "workCenters.$code" "$wcid"
    info "$code — $name ($captype x$capunits) -> $calcode"
done < <(rows "$WC_ROWS")

################################################################################
# 7. Định mức vật tư (§7, §10 bước 8)
################################################################################
stage_banner "7. Định mức vật tư (BOM)"

# Thứ tự trong BOM_ROWS đã là thứ tự kích hoạt: con trước, cha sau.
BOM_COUNT=0
while IFS='|' read -r parent revision doccode lines; do
    [ -z "$parent" ] && continue
    log "Tạo $doccode cho $parent"
    bid=$(call POST "/v1/companies/$CO/boms" \
        "{\"parentItemId\":\"${ID[items.$parent]}\",\"revision\":\"$revision\",\"description\":\"$doccode — bill of materials for $parent revision $revision\"}" \
        | rid bomId)
    remember "boms.$doccode" "$bid"

    lineno=1
    IFS=';' read -ra lrows <<<"$lines"
    for l in "${lrows[@]}"; do
        IFS=':' read -r comp qty scrap <<<"$l"
        call POST "/v1/boms/$bid/lines" \
            "{\"componentItemId\":\"${ID[items.$comp]}\",\"lineNo\":$lineno,\"quantityPer\":$qty,\"scrapRate\":$scrap}" \
            >/dev/null
        lineno=$((lineno + 1))
    done

    call POST "/v1/boms/$bid/activate" >/dev/null
    assert_status "/v1/boms/$bid" "ACTIVE"
    BOM_COUNT=$((BOM_COUNT + 1))
    info "  $((lineno - 1)) dòng, ACTIVE"
done < <(rows "$BOM_ROWS")
info "$BOM_COUNT định mức"

################################################################################
# 8. Quy trình công nghệ (§6, §10 bước 9)
################################################################################
stage_banner "8. Quy trình công nghệ (Routing)"

RT_COUNT=0
while IFS='|' read -r code version item ops; do
    [ -z "$code" ] && continue
    opjson="["; first=1
    IFS=';' read -ra oplist <<<"$ops"
    for o in "${oplist[@]}"; do
        IFS=':' read -r seq opname wc setup runmin <<<"$o"
        [ $first -eq 0 ] && opjson="$opjson,"
        opjson="$opjson{\"sequence\":$seq,\"name\":\"$opname\",\"workCenterId\":\"${ID[workCenters.$wc]}\",\"setupMinutes\":$setup,\"runMinutesPerUnit\":$runmin}"
        first=0
    done
    opjson="$opjson]"

    rtid=$(call POST "/v1/companies/$CO/routings" \
        "{\"itemId\":\"${ID[items.$item]}\",\"code\":\"$code\",\"version\":\"$version\",\"note\":\"Routing of the demo dataset for $item\",\"operations\":$opjson}" \
        | rid routingId)
    call POST "/v1/routings/$rtid/activate" >/dev/null
    assert_status "/v1/routings/$rtid" "ACTIVE"
    remember "routings.$code" "$rtid"
    RT_COUNT=$((RT_COUNT + 1))
    info "$code bản $version cho $item (${#oplist[@]} công đoạn)"
done < <(rows "$ROUTING_ROWS")
info "$RT_COUNT quy trình"

################################################################################
# 9. Giá thành chuẩn (P3)
################################################################################
stage_banner "9. Giá thành chuẩn"

# Nguyên vật liệu mang giá mua thẳng; bán thành phẩm/thành phẩm chỉ mang nhân công + sản xuất
# chung, phần vật tư do CostingService nổ BOM cộng lên khi đọc. Nhờ đó màn hình chênh lệch chi
# phí của lệnh sản xuất có số thật thay vì toàn số 0.
COST_COUNT=0
while IFS='|' read -r icode mat lab ovh; do
    [ -z "$icode" ] && continue
    call PUT "/v1/companies/$CO/items/${ID[items.$icode]}/standard-cost" \
        "{\"materialCost\":$mat,\"laborCost\":$lab,\"overheadCost\":$ovh}" >/dev/null
    COST_COUNT=$((COST_COUNT + 1))
done < <(rows "$COST_ROWS")
info "$COST_COUNT dòng giá thành chuẩn"

################################################################################
# 10. Nhà cung cấp
################################################################################
stage_banner "10. Nhà cung cấp"

while IFS='|' read -r code name email phone address tax; do
    [ -z "$code" ] && continue
    sid=$(call POST /v1/suppliers \
        "{\"companyId\":\"$CO\",\"code\":\"$code\",\"name\":\"$name\",\"email\":\"$email\",\"phone\":\"$phone\",\"address\":\"$address\",\"taxCode\":\"$tax\"}" \
        | rid supplierId)
    remember "suppliers.$code" "$sid"
    info "$code — $name"
done < <(rows "$SUPPLIER_ROWS")

log "Gắn nhà cung cấp cho vật tư"
LINK_COUNT=0
while IFS='|' read -r scode itemlist; do
    [ -z "$scode" ] && continue
    IFS=',' read -ra items <<<"$itemlist"
    for ic in "${items[@]}"; do
        call POST "/v1/items/${ID[items.$ic]}/suppliers" \
            "{\"supplierId\":\"${ID[suppliers.$scode]}\",\"preferred\":true}" >/dev/null
        LINK_COUNT=$((LINK_COUNT + 1))
    done
    info "$scode: ${#items[@]} vật tư"
done < <(rows "$SUPPLIER_ITEM_ROWS")
info "$LINK_COUNT liên kết vật tư - nhà cung cấp"

################################################################################
# 11. Tồn kho đầu kỳ (§8, §10 bước 10)
################################################################################
stage_banner "11. Tồn kho đầu kỳ"

n=0
while IFS='|' read -r icode wcode qty lotcode; do
    [ -z "$icode" ] && continue
    n=$((n + 1))
    if [ -n "$lotcode" ]; then
        lotpart="\"lotCode\":\"$lotcode\","
    else
        lotpart=""
    fi
    # R2: khoá idempotency ổn định, gắn với vật tư chứ không gắn với thời điểm chạy — gửi lại
    # cùng khoá KHÔNG cộng tồn hai lần.
    call POST /v1/inventory/receive \
        "{\"itemId\":\"${ID[items.$icode]}\",\"warehouseId\":\"${ID[warehouses.$wcode]}\",$lotpart\"quantity\":$qty,\"reason\":\"Opening stock of the Capstone 2 demo dataset\",\"referenceType\":\"${NS}_OPENING_STOCK\",\"referenceId\":\"$SEED_RUN_ID\"}" \
        -H "Idempotency-Key: $NS-OPENING-$icode" >/dev/null
    note "openingStock.$icode" "$qty @ $wcode"
done < <(rows "$STOCK_ROWS")
info "$n dòng tồn kho đầu kỳ"

# D26-FG-BIKE16 cố ý KHÔNG có tồn kho (§8): đó là thứ làm MRP sinh ra đề xuất MAKE.

################################################################################
# 12..15. Chứng từ nghiệp vụ — CHỈ chạy ở profile "sample"
################################################################################
#
# Profile "demo" dừng lại đúng ở đây: không đơn mua, không đơn bán, không lượt chạy MRP, không
# lệnh sản xuất nào. Đó là yêu cầu §9 của tài liệu gốc ("Không seed Planning Run trước: để người
# trình bày chạy Planning trực tiếp") — công ty DEMO- tồn tại để diễn trên sân khấu, còn công ty
# D26- (sample) là bộ dữ liệu đã chạy hết luồng để chụp hình.
if [ "$SEED_TRANSACTIONS" = "yes" ]; then

stage_banner "12. Yêu cầu mua hàng và đơn mua hàng"

# ── yêu cầu mua hàng ─────────────────────────────────────────────────────────
while IFS='|' read -r reqno supcode state lines; do
    [ -z "$reqno" ] && continue
    linejson="["; first=1
    IFS=';' read -ra lrows <<<"$lines"
    for l in "${lrows[@]}"; do
        IFS=':' read -r icode qty <<<"$l"
        [ $first -eq 0 ] && linejson="$linejson,"
        linejson="$linejson{\"itemId\":\"${ID[items.$icode]}\",\"supplierId\":\"${ID[suppliers.$supcode]}\",\"requestedQuantity\":$qty,\"neededByDate\":\"$(d_plus 21)\"}"
        first=0
    done
    linejson="$linejson]"

    PR_JSON=$(call POST /v1/purchase-requisitions \
        "{\"companyId\":\"$CO\",\"plantId\":\"$PLANT\",\"warehouseId\":\"${ID[warehouses.$WH_RM]}\",\"requisitionNo\":\"$reqno\",\"neededByDate\":\"$(d_plus 21)\",\"sourceType\":\"MANUAL\",\"lines\":$linejson}")
    prid=$(echo "$PR_JSON" | rid purchaseRequisitionId)
    remember "purchaseRequisitions.$reqno" "$prid"

    if [ "$state" != "DRAFT" ]; then
        # Duyệt đúng số lượng đã xin — duyệt vượt sẽ trả 409 PLANNED_QUANTITY_EXCEEDED (B72).
        approvals=$(echo "$PR_JSON" | "$PY" -c "
import json,sys
c = json.loads(sys.stdin.buffer.read().decode('utf-8'))['result']['lines']
print(json.dumps([{'purchaseRequisitionLineId': l['purchaseRequisitionLineId'],
                   'approvedQuantity': float(l['requestedQuantity'])} for l in c]))
")
        call POST "/v1/purchase-requisitions/$prid/approve" \
            "{\"decisionNote\":\"Approved for the demo dataset\",\"approvedLines\":$approvals}" >/dev/null
    fi

    if [ "$state" = "CONVERTED" ]; then
        poid=$(call POST "/v1/purchase-requisitions/$prid/convert-to-purchase-order" \
            "{\"purchaseOrderNo\":\"$PR3_PO_NO\",\"supplierId\":\"${ID[suppliers.$supcode]}\",\"orderDate\":\"$(d_today)\",\"expectedDate\":\"$(d_plus 14)\",\"note\":\"Converted from $reqno\"}" \
            | rid purchaseOrderId)
        remember "purchaseOrders.$PR3_PO_NO" "$poid"
        call POST "/v1/purchase-orders/$poid/send" >/dev/null
        info "$reqno -> $state -> $PR3_PO_NO (SENT)"
    else
        info "$reqno -> $state"
    fi
done < <(rows "$PR_ROWS")

# ── đơn mua hàng tạo thẳng ───────────────────────────────────────────────────
while IFS='|' read -r pono supcode state lines; do
    [ -z "$pono" ] && continue
    linejson="["; first=1
    IFS=';' read -ra lrows <<<"$lines"
    for l in "${lrows[@]}"; do
        IFS=':' read -r icode qty price <<<"$l"
        [ $first -eq 0 ] && linejson="$linejson,"
        linejson="$linejson{\"itemId\":\"${ID[items.$icode]}\",\"orderedQuantity\":$qty,\"unitPrice\":$price,\"currencyCode\":\"VND\",\"expectedDate\":\"$(d_plus 14)\"}"
        first=0
    done
    linejson="$linejson]"

    PO_JSON=$(call POST /v1/purchase-orders \
        "{\"companyId\":\"$CO\",\"plantId\":\"$PLANT\",\"warehouseId\":\"${ID[warehouses.$WH_RM]}\",\"supplierId\":\"${ID[suppliers.$supcode]}\",\"purchaseOrderNo\":\"$pono\",\"orderDate\":\"$(d_today)\",\"expectedDate\":\"$(d_plus 14)\",\"note\":\"Purchase order of the demo dataset\",\"lines\":$linejson}")
    poid=$(echo "$PO_JSON" | rid purchaseOrderId)
    remember "purchaseOrders.$pono" "$poid"

    [ "$state" = "DRAFT" ] && { info "$pono -> DRAFT"; continue; }

    call POST "/v1/purchase-orders/$poid/send" >/dev/null
    [ "$state" = "SENT" ] && { info "$pono -> SENT"; continue; }

    # PARTIALLY_RECEIVED nhận nửa số đặt; RECEIVED nhận đủ. Cả hai đều làm tăng tồn kho thật
    # qua ledger, nên số trên màn hình kho là hệ quả của chứng từ chứ không phải số bịa.
    if [ "$state" = "PARTIALLY_RECEIVED" ]; then ratio=0.5; else ratio=1; fi
    gr_lines=$(echo "$PO_JSON" | "$PY" -c "
import json,sys
c = json.loads(sys.stdin.buffer.read().decode('utf-8'))['result']['lines']
print(json.dumps([{'purchaseOrderLineId': l['purchaseOrderLineId'],
                   'receivedQuantity': float(l['orderedQuantity']) * $ratio} for l in c]))
")
    call POST "/v1/purchase-orders/$poid/goods-receipts" \
        "{\"receiptNo\":\"$NS-GR-${pono#$NS-PO-}\",\"note\":\"Goods receipt against $pono\",\"lines\":$gr_lines}" \
        -H "Idempotency-Key: $NS-GR-$pono" >/dev/null
    assert_status "/v1/purchase-orders/$poid" "$state"
    info "$pono -> $state (đã nhập kho)"
done < <(rows "$PO_ROWS")

################################################################################
# 13. Đơn bán hàng
################################################################################
stage_banner "13. Đơn bán hàng"

SO_PLANNED_FULL=""; SO_PLANNED_FULL_NO=""
SO_PLANNED_PARTIAL=""; SO_PLANNED_PARTIAL_NO=""

while IFS='|' read -r orderno customer state duedays lines; do
    [ -z "$orderno" ] && continue
    linejson="["; first=1
    IFS=';' read -ra lrows <<<"$lines"
    for l in "${lrows[@]}"; do
        IFS=':' read -r icode qty <<<"$l"
        [ $first -eq 0 ] && linejson="$linejson,"
        linejson="$linejson{\"itemId\":\"${ID[items.$icode]}\",\"orderedQuantity\":$qty,\"dueDate\":\"$(d_plus "$duedays")\"}"
        first=0
    done
    linejson="$linejson]"

    soid=$(call POST /sales-orders/v1 \
        "{\"companyId\":\"$CO\",\"plantId\":\"$PLANT\",\"orderNo\":\"$orderno\",\"customerName\":\"$customer\",\"orderDate\":\"$(d_today)\",\"note\":\"Sales order of the demo dataset\",\"lines\":$linejson}" \
        | rid salesOrderId)
    remember "salesOrders.$orderno" "$soid"

    case "$state" in
        DRAFT) ;;
        CANCELLED)
            call POST "/sales-orders/v1/$soid/confirm" >/dev/null
            call POST "/sales-orders/v1/$soid/cancel" >/dev/null ;;
        *)
            call POST "/sales-orders/v1/$soid/confirm" >/dev/null ;;
    esac
    [ "$state" = "PLANNED_FULL" ]    && { SO_PLANNED_FULL="$soid";    SO_PLANNED_FULL_NO="$orderno"; }
    [ "$state" = "PLANNED_PARTIAL" ] && { SO_PLANNED_PARTIAL="$soid"; SO_PLANNED_PARTIAL_NO="$orderno"; }
    info "$orderno — $customer -> $state"
done < <(rows "$SO_ROWS")

################################################################################
# 14. Hoạch định (MRP) và lệnh sản xuất sinh từ đề xuất
################################################################################
stage_banner "14. Hoạch định nhu cầu vật tư"

# demandWarehouseId = kho thành phẩm (§15). Nhu cầu cấp 0 lấy kho này; các cấp dưới do chính
# sách DEC-03 (defaultSupply của từng vật tư) quyết định — đó là lý do bảng §5 phải đủ mọi vật tư.
DEMANDS=$(call GET "/v1/planning/demands?companyId=$CO&plantId=$PLANT&status=OPEN&page=0&size=200")

# demand_of SALES_ORDER_ID — id dòng nhu cầu hoạch định sinh từ dòng đầu tiên của đơn hàng.
demand_of() {
    local so_line
    so_line=$(call GET "/sales-orders/v1/$1" | extract "d['result']['lines'][0]['salesOrderLineId']")
    echo "$DEMANDS" | extract "next(x['planningDemandId'] for x in d['result']['content'] if x['referenceId']=='$so_line')"
}

# run_mrp TÊN_KHOÁ DEMAND_ID... — chạy một lượt MRP cho các dòng nhu cầu được nêu.
run_mrp() {
    local key="$1"; shift
    local ids="" first=1 id
    for id in "$@"; do
        [ $first -eq 0 ] && ids="$ids,"
        ids="$ids\"$id\""
        first=0
    done
    call POST /v1/planning-runs \
        "{\"companyId\":\"$CO\",\"plantId\":\"$PLANT\",\"demandWarehouseId\":\"${ID[warehouses.$WH_FG]}\",\"horizonStartDate\":\"$(d_today)\",\"horizonEndDate\":\"$(d_plus 90)\",\"demandLineIds\":[$ids]}" \
        -H "Idempotency-Key: $NS-RUN-$key"
}

log "Lượt chạy 1 — $SO_PLANNED_FULL_NO (luồng trình diễn chính)"
RUN1=$(run_mrp FULL "$(demand_of "$SO_PLANNED_FULL")")
RUN1_ID=$(echo "$RUN1" | rid mrpRunId)
remember "planningRuns.full" "$RUN1_ID"
note     "planningRuns.fullCode" "$(echo "$RUN1" | rid code)"
info "$(echo "$RUN1" | rid code): $(echo "$RUN1" | rid plannedWorkOrders) lệnh sản xuất đề xuất, $(echo "$RUN1" | rid blockedProposals) bị chặn"

log "Lượt chạy 2 — $SO_PLANNED_PARTIAL_NO"
RUN2=$(run_mrp PARTIAL "$(demand_of "$SO_PLANNED_PARTIAL")")
RUN2_ID=$(echo "$RUN2" | rid mrpRunId)
remember "planningRuns.partial" "$RUN2_ID"
note     "planningRuns.partialCode" "$(echo "$RUN2" | rid code)"

# Lượt chạy 3 gộp mọi nhu cầu còn mở và ĐỂ NGUYÊN đề xuất ở trạng thái DRAFT — màn hình hoạch
# định vì thế luôn có việc đang chờ duyệt để chụp hình, cạnh hai lượt đã xử lý xong.
log "Lượt chạy 3 — toàn bộ nhu cầu đang mở (để nguyên đề xuất DRAFT)"
ALL_OPEN=$(call GET "/v1/planning/demands?companyId=$CO&plantId=$PLANT&status=OPEN&page=0&size=200" \
    | extract "','.join(x['planningDemandId'] for x in d['result']['content'])")
IFS=',' read -ra open_ids <<<"$ALL_OPEN"
RUN3=$(run_mrp ALL "${open_ids[@]}")
remember "planningRuns.backlog" "$(echo "$RUN3" | rid mrpRunId)"
note     "planningRuns.backlogCode" "$(echo "$RUN3" | rid code)"
info "$(echo "$RUN3" | rid code): $(echo "$RUN3" | rid totalSuggestionLines) đề xuất đang chờ duyệt"

################################################################################
# 15. Lệnh sản xuất: từ đề xuất MRP và tạo thủ công
################################################################################
stage_banner "15. Lệnh sản xuất"

# Cửa sổ kế hoạch của mọi lệnh sản xuất: 08:00 giờ Việt Nam thứ Hai kế tiếp, kéo dài
# WO_PLANNED_DURATION_DAYS ngày. Neo vào thứ Hai chứ không phải "hôm nay + N" vì mọi lịch
# làm việc của bộ dữ liệu đều nghỉ Chủ nhật — neo lệch sẽ đẩy lịch vào ngày không làm việc
# và bảng năng lực báo sức chứa 0 vì một lý do chẳng liên quan tới câu chuyện đang kể.
MONDAY=$(ts_next_monday 01)
MONDAY_END=$(date -u -d "next monday +$WO_PLANNED_DURATION_DAYS days 01:00:00 UTC" +%Y-%m-%dT%H:%M:%SZ)

# issue_all WO_ID KHOÁ — xuất toàn bộ vật tư đang giữ chỗ của một lệnh sản xuất.
issue_all() {
    local wo="$1" key="$2" lines
    lines=$(call GET "/v1/work-orders/$wo/material-reservations?page=0&size=100" | "$PY" -c "
import json,sys
c = json.loads(sys.stdin.buffer.read().decode('utf-8'))['result']['content']
print(json.dumps([{'componentLineId': r['componentLineId'],
                   'reservationId': r['reservationId'],
                   'warehouseId': r['warehouseId'],
                   'lotId': r.get('lotId'),
                   'quantity': float(r['remainingQuantity']),
                   'reason': 'Issue to production'} for r in c]))
")
    call POST "/v1/work-orders/$wo/material-issues" \
        "{\"note\":\"Material issued against the bill of materials\",\"lines\":$lines}" \
        -H "Idempotency-Key: $key" >/dev/null
}

# planned_qty WO_ID — số lượng kế hoạch (số nguyên) của một lệnh sản xuất.
planned_qty() { call GET "/v1/work-orders/$1" | extract "int(float(d['result']['plannedQuantity']))"; }

# last_operation_id WO_ID — công đoạn CUỐI trong ảnh chụp quy trình của lệnh sản xuất.
#
# Phiếu ghi sản lượng gắn vào một công đoạn cụ thể, nếu không thì màn hình ghi sản lượng bỏ
# trống cả ba cột công đoạn / thứ tự / tổ sản xuất. Chọn công đoạn cuối vì sản lượng tốt chỉ
# thành hình sau bước cuối cùng (đóng gói / kiểm tra cuối) — gắn vào công đoạn đầu sẽ đọc như
# thể hàng ra lò ngay tại khâu chuẩn bị.
last_operation_id() {
    call GET "/v1/work-orders/$1" \
        | extract "(sorted(d['result']['operations'], key=lambda o: o['sequence'])[-1]['workOrderOperationId'] if d['result'].get('operations') else '')"
}

# report_output WO_ID SỐ_LƯỢNG KHOÁ — ghi sản lượng tốt (phế 0, làm lại 0).
report_output() {
    local op
    op=$(last_operation_id "$1")
    local oppart=""
    [ -n "$op" ] && oppart="\"workOrderOperationId\":\"$op\","
    call POST "/v1/work-orders/$1/production-executions" \
        "{$oppart\"goodQuantity\":$2,\"scrapQuantity\":0,\"reworkQuantity\":0,\"actualStartedAt\":\"$(ts_ago 6)\",\"actualEndedAt\":\"$(ts_ago 1)\",\"notes\":\"Day shift output report\"}" \
        -H "Idempotency-Key: $3" >/dev/null
}

# receipt_and_qc WO_ID SỐ_LƯỢNG LÔ KHOÁ [QC]
#   QC=yes  -> nhập kho, duyệt, QC cho phép xuất (lô AVAILABLE)
#   QC=hold -> nhập kho, duyệt, DỪNG ở lô HOLD chờ QC (trạng thái nghiệp vụ bình thường,
#              và là thứ làm màn hình Lô hàng có cả hai trạng thái để chụp)
receipt_and_qc() {
    local wo="$1" qty="$2" lot="$3" key="$4" mode="${5:-yes}" rid_
    rid_=$(call POST "/v1/work-orders/$wo/production-receipts" \
        "{\"destinationWarehouseId\":\"${ID[warehouses.$WH_FG]}\",\"lotNumber\":\"$lot\",\"quantity\":$qty,\"reason\":\"Finished goods receipt\",\"note\":\"Production receipt of the demo dataset\"}" \
        -H "Idempotency-Key: $key" | rid receiptId)
    call POST "/v1/work-orders/$wo/production-receipts/$rid_/submit" >/dev/null
    call POST "/v1/work-orders/$wo/production-receipts/$rid_/approve" \
        '{"reason":"Quantity verified against the output report"}' >/dev/null
    if [ "$mode" = "yes" ]; then
        call POST "/v1/work-orders/$wo/production-receipts/$rid_/qc-disposition" \
            '{"result":"AVAILABLE","reason":"Visual and functional inspection passed"}' >/dev/null
    fi
    echo "$rid_"
}

# convert_suggestion RUN_ID WO_NO — duyệt đề xuất MAKE đầu tiên của lượt chạy rồi chuyển thành
# lệnh sản xuất. Chỉ đề xuất đi ra từ dòng nhu cầu cấp 0 mới nối được về đơn bán hàng (B64),
# và đó chính là thứ làm đơn hàng chuyển sang FULFILLED sau khi QC giải phóng lô.
convert_suggestion() {
    local run="$1" wono="$2" sid
    sid=$(call GET "/v1/planning-runs/$run/suggestions?page=0&size=100" \
        | extract "next(s['supplySuggestionId'] for s in d['result']['content'] if s['supplyType']=='MAKE')")
    call POST "/v1/supply-suggestions/$sid/approve" \
        '{"decisionNote":"Approved by the production planner"}' >/dev/null
    call POST "/v1/supply-suggestions/$sid/convert-to-work-order" \
        "{\"workOrderNo\":\"$wono\",\"outputWarehouseId\":\"${ID[warehouses.$WH_FG]}\",\"plannedStartAt\":\"$MONDAY\",\"plannedEndAt\":\"$MONDAY_END\",\"notes\":\"Generated from a planning run\"}" \
        | rid convertedWorkOrderId
}

# ── A. Luồng đầy đủ: đơn hàng -> MRP -> lệnh sản xuất -> giao đủ ─────────────
log "A. $SO_PLANNED_FULL_NO: chạy hết luồng tới FULFILLED"
WO_FULL=$(convert_suggestion "$RUN1_ID" "$NS-WO-1004")
remember "workOrders.$NS-WO-1004" "$WO_FULL"
call POST "/v1/work-orders/$WO_FULL/reserve" >/dev/null
call POST "/v1/work-orders/$WO_FULL/release" >/dev/null
issue_all "$WO_FULL" "$NS-ISSUE-1004"
# 🔴 Số kế hoạch của lệnh sản xuất là số RÒNG của MRP, KHÔNG phải số trên đơn hàng: netting đã
# trừ tồn kho sẵn có. Báo sản lượng theo số đặt hàng sẽ trả 409 PLANNED_QUANTITY_EXCEEDED (B54).
QTY_FULL=$(planned_qty "$WO_FULL")
report_output "$WO_FULL" "$QTY_FULL" "$NS-EXEC-1004"
receipt_and_qc "$WO_FULL" "$QTY_FULL" "$LOT_SO1004" "$NS-RECEIPT-1004" yes >/dev/null
call POST "/v1/work-orders/$WO_FULL/close" >/dev/null
assert_status "/sales-orders/v1/$SO_PLANNED_FULL" "FULFILLED"
info "$NS-WO-1004 -> CLOSED, $SO_PLANNED_FULL_NO -> FULFILLED, lô $LOT_SO1004 AVAILABLE"

# ── B. Luồng giao một phần ───────────────────────────────────────────────────
log "B. $SO_PLANNED_PARTIAL_NO: sản xuất một phần -> PARTIALLY_FULFILLED"
WO_PART=$(convert_suggestion "$RUN2_ID" "$NS-WO-1005")
remember "workOrders.$NS-WO-1005" "$WO_PART"
call POST "/v1/work-orders/$WO_PART/reserve" >/dev/null
call POST "/v1/work-orders/$WO_PART/release" >/dev/null
issue_all "$WO_PART" "$NS-ISSUE-1005"
# Làm ra ÍT hơn số kế hoạch ⇒ lệnh sản xuất dừng ở IN_PROGRESS và đơn hàng chỉ giao được một
# phần. Trần là số kế hoạch ròng của MRP (xem ghi chú ở luồng A), nên lấy nửa số đó.
QTY_PART=$(( $(planned_qty "$WO_PART") / 2 ))
[ "$QTY_PART" -lt 1 ] && QTY_PART=1
report_output "$WO_PART" "$QTY_PART" "$NS-EXEC-1005"
receipt_and_qc "$WO_PART" "$QTY_PART" "$LOT_SO1005" "$NS-RECEIPT-1005" yes >/dev/null
assert_status "/sales-orders/v1/$SO_PLANNED_PARTIAL" "PARTIALLY_FULFILLED"
info "$NS-WO-1005 -> IN_PROGRESS, $SO_PLANNED_PARTIAL_NO -> PARTIALLY_FULFILLED"

# ── C. Lệnh sản xuất thủ công phủ nốt các trạng thái còn lại ─────────────────
log "C. Lệnh sản xuất thủ công"
while IFS='|' read -r wono icode qty state; do
    [ -z "$wono" ] && continue
    wo=$(call POST "/v1/plants/$PLANT/work-orders" \
        "{\"workOrderNo\":\"$wono\",\"productItemId\":\"${ID[items.$icode]}\",\"outputWarehouseId\":\"${ID[warehouses.$WH_FG]}\",\"plannedQuantity\":$qty,\"plannedStartAt\":\"$MONDAY\",\"plannedEndAt\":\"$MONDAY_END\",\"notes\":\"Work order of the demo dataset\"}" \
        | rid workOrderId)
    remember "workOrders.$wono" "$wo"
    suffix="${wono#D26-WO-}"

    case "$state" in
        DRAFT) ;;
        PLANNED)
            call POST "/v1/work-orders/$wo/plan" >/dev/null ;;
        CANCELLED)
            call POST "/v1/work-orders/$wo/cancel" \
                '{"reason":"Customer postponed the order"}' >/dev/null ;;
        RELEASED)
            call POST "/v1/work-orders/$wo/reserve" >/dev/null
            call POST "/v1/work-orders/$wo/release" >/dev/null ;;
        IN_PROGRESS)
            call POST "/v1/work-orders/$wo/reserve" >/dev/null
            call POST "/v1/work-orders/$wo/release" >/dev/null
            issue_all "$wo" "$NS-ISSUE-$suffix"
            # Sản lượng nhỏ hơn kế hoạch ⇒ lệnh sản xuất dừng ở IN_PROGRESS, và lô nhập kho
            # dừng ở HOLD chờ QC — hai màn hình cần dữ liệu ở đúng trạng thái đó.
            report_output "$wo" 2 "$NS-EXEC-$suffix"
            receipt_and_qc "$wo" 2 "$LOT_WO2004" "$NS-RECEIPT-$suffix" hold >/dev/null ;;
        CLOSED)
            call POST "/v1/work-orders/$wo/reserve" >/dev/null
            call POST "/v1/work-orders/$wo/release" >/dev/null
            issue_all "$wo" "$NS-ISSUE-$suffix"
            report_output "$wo" "$qty" "$NS-EXEC-$suffix"
            receipt_and_qc "$wo" "$qty" "$LOT_WO2005" "$NS-RECEIPT-$suffix" yes >/dev/null
            call POST "/v1/work-orders/$wo/close" >/dev/null ;;
        *) die "trạng thái lệnh sản xuất không hỗ trợ: $state" ;;
    esac
    assert_status "/v1/work-orders/$wo" "$state"
    info "$wono ($icode x$qty) -> $state"
done < <(rows "$WO_ROWS")

fi   # kết thúc khối chứng từ nghiệp vụ (profile "sample")

if [ "$SEED_TRANSACTIONS" != "yes" ]; then
    stage_banner "12..15. Bỏ qua chứng từ nghiệp vụ (profile $PROFILE)"
    info "không tạo đơn mua / đơn bán / lượt MRP / lệnh sản xuất nào — người trình bày tự tạo"
fi

################################################################################
# 16. Preflight (§11)
################################################################################
stage_banner "16. Preflight"

PF_FAILED=0
pf() {  # pf ID MÔ_TẢ ACTUAL EXPECTED
    if [ "$3" = "$4" ]; then
        info "$1 PASS — $2"
    else
        warn "$1 FAIL — $2 (nhận '$3', cần '$4')"
        PF_FAILED=$((PF_FAILED + 1))
    fi
}
# Số kỳ vọng suy ra TỪ CHÍNH catalogue, không viết cứng — đổi bảng dữ liệu thì preflight tự theo.
count_rows() { rows "$1" | wc -l | tr -d ' '; }

# PF-01 Plant/Warehouse ACTIVE, cùng scope
pf PF-01 "nhà máy $PLANT_CODE ACTIVE" \
    "$(call GET "/v1/plants/$PLANT" | rid status)" "ACTIVE"
pf PF-01 "kho ACTIVE của nhà máy" \
    "$(call GET "/v1/plants/$PLANT/warehouses?page=0&size=100" \
        | extract "sum(1 for w in d['result']['content'] if w['status']=='ACTIVE')")" "$(count_rows "$WAREHOUSE_ROWS")"

# PF-02 Item ACTIVE, tracking đúng, không SERIAL
ITEMS_JSON=$(call GET "/v1/companies/$CO/items?page=0&size=200")
pf PF-02 "vật tư ACTIVE" \
    "$(echo "$ITEMS_JSON" | extract "sum(1 for i in d['result']['content'] if i['status']=='ACTIVE')")" "$(count_rows "$ITEM_ROWS")"
pf PF-02 "chỉ thành phẩm mới lot-tracked" \
    "$(echo "$ITEMS_JSON" | extract "sum(1 for i in d['result']['content'] if i['lotTracked'])")" \
    "$(rows "$ITEM_ROWS" | grep -c 'true$')"
pf PF-02 "không vật tư nào serial-tracked" \
    "$(echo "$ITEMS_JSON" | extract "sum(1 for i in d['result']['content'] if i.get('serialTracked'))")" "0"

# PF-03/PF-04 mỗi vật tư đúng một cấu hình kho ACTIVE trong nhà máy (nhiều hơn một =
# AMBIGUOUS_WAREHOUSE_POLICY), và cấu hình đó tồn tại nên settingSource = ITEM_WAREHOUSE.
# 🔴 GET /v1/inventory/item-warehouse-settings KHÔNG có tham số plantId — chỉ có warehouseId
# và itemId. Truyền plantId thì nó bị bỏ qua IM LẶNG và endpoint trả cấu hình của MỌI công ty.
# Với một công ty trong cơ sở dữ liệu thì con số vẫn tình cờ đúng; thêm công ty thứ hai là kiểm
# tra này báo gấp đôi. Vì vậy phải cộng theo TỪNG KHO của nhà máy.
IWS_ITEM_IDS=""
for wcode in $(rows "$WAREHOUSE_ROWS" | cut -d'|' -f1); do
    IWS_ITEM_IDS="$IWS_ITEM_IDS $(call GET "/v1/inventory/item-warehouse-settings?warehouseId=${ID[warehouses.$wcode]}&page=0&size=200" \
        | extract "' '.join(s['itemId'] for s in d['result']['content'] if s['status']=='ACTIVE')")"
done
pf PF-03 "cấu hình item-warehouse ACTIVE trong nhà máy" \
    "$(echo $IWS_ITEM_IDS | wc -w | tr -d ' ')" "$(count_rows "$IWS_ROWS")"
pf PF-03 "không vật tư nào có quá một cấu hình" \
    "$(echo $IWS_ITEM_IDS | tr ' ' '\n' | sed '/^$/d' | sort | uniq -c | sort -rn | head -1 | awk '{print $1}')" "1"

# PF-05..PF-07 định mức, quy trình, tổ sản xuất
pf PF-05 "định mức ACTIVE" \
    "$(call GET "/v1/companies/$CO/boms?page=0&size=100" \
        | extract "sum(1 for b in d['result']['content'] if b['status']=='ACTIVE')")" "$(count_rows "$BOM_ROWS")"
pf PF-06 "quy trình ACTIVE" \
    "$(call GET "/v1/companies/$CO/routings?page=0&size=100" \
        | extract "sum(1 for r in d['result']['content'] if r['status']=='ACTIVE')")" "$(count_rows "$ROUTING_ROWS")"
pf PF-07 "tổ sản xuất ACTIVE" \
    "$(call GET "/v1/plants/$PLANT/work-centers?page=0&size=100" \
        | extract "sum(1 for w in d['result']['content'] if w['status']=='ACTIVE')")" "$(count_rows "$WC_ROWS")"

# PF-08 tồn kho vẫn dương ở kho nguyên vật liệu sau toàn bộ chứng từ bên trên
pf PF-08 "không dòng tồn kho nào âm" \
    "$(call GET "/v1/inventory/balances?warehouseId=${ID[warehouses.$WH_RM]}&size=200" \
        | extract "sum(1 for b in d['result']['content'] if float(b['quantity']) < 0)")" "0"

if [ "$SEED_TRANSACTIONS" = "yes" ]; then

# PF-10..PF-12 hoạch định: không đề xuất nào bị chặn, mọi dòng nhu cầu phân giải được kho
for runkey in full partial backlog; do
    runid="${ID[planningRuns.$runkey]}"
    pf PF-10 "lượt chạy $runkey hoàn tất" \
        "$(call GET "/v1/planning-runs/$runid" | rid status)" "COMPLETED"
    pf PF-10 "lượt chạy $runkey không có đề xuất bị chặn" \
        "$(call GET "/v1/planning-runs/$runid" | rid blockedProposals)" "0"
    pf PF-11 "lượt chạy $runkey: mọi dòng nhu cầu phân giải được kho" \
        "$(call GET "/v1/planning-runs/$runid/requirements?page=0&size=200" \
            | extract "sum(1 for r in d['result']['content'] if not r.get('warehouseId'))")" "0"
done

# PF-13 lệnh sản xuất phủ đủ ma trận trạng thái
WO_JSON=$(call GET "/v1/plants/$PLANT/work-orders?page=0&size=100")
pf PF-13 "số lệnh sản xuất" \
    "$(echo "$WO_JSON" | extract "d['result']['totalElements']")" "$(( $(count_rows "$WO_ROWS") + 2 ))"
for st in DRAFT PLANNED RELEASED IN_PROGRESS CLOSED CANCELLED; do
    pf PF-13 "có lệnh sản xuất ở trạng thái $st" \
        "$(echo "$WO_JSON" | extract "'yes' if any(w['status']=='$st' for w in d['result']['content']) else 'no'")" "yes"
done
pf PF-13 "không lệnh sản xuất nào BLOCKED (bộ dữ liệu sạch)" \
    "$(echo "$WO_JSON" | extract "sum(1 for w in d['result']['content'] if w['status']=='BLOCKED')")" "0"

# PF-14 đơn bán hàng phủ đủ ma trận trạng thái
SO_JSON=$(call GET "/sales-orders/v1?companyId=$CO&plantId=$PLANT&page=0&size=100")
for st in DRAFT CONFIRMED CANCELLED FULFILLED PARTIALLY_FULFILLED; do
    pf PF-14 "có đơn bán hàng ở trạng thái $st" \
        "$(echo "$SO_JSON" | extract "'yes' if any(o['status']=='$st' for o in d['result']['content']) else 'no'")" "yes"
done

fi   # kết thúc preflight của phần chứng từ nghiệp vụ

# PF-15 RBAC: tài khoản vận hành đăng nhập được và thấy đúng nhà máy demo
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
while IFS='|' read -r uname _ _; do
    [ -z "$uname" ] && continue
    check_login "$uname"
done < <(rows "$USER_ROWS")

################################################################################
# 17. Manifest bàn giao (§14)
################################################################################
stage_banner "17. Manifest bàn giao"

note "demo.outputLot.full" "$LOT_SO1004"
note "demo.outputLot.partial" "$LOT_SO1005"
note "demo.outputLot.onHold" "$LOT_WO2004"
note "demo.password" "$DEMO_PASSWORD"
note "preflight.failures" "$PF_FAILED"
dump_manifest "$MANIFEST"
info "đã ghi $MANIFEST"

################################################################################
# 18. Bảng tổng kết những gì có trên màn hình
################################################################################
stage_banner "18. Bộ dữ liệu đã dựng xong — công ty $CO_CODE (profile $PROFILE)"

DASH=$(call GET "/v1/reports/inventory-dashboard?scopeType=PLANT&scopeId=$PLANT&lowStockLimit=20&movementLimit=20" 2>/dev/null || echo "")
if [ -n "$DASH" ]; then
    info "Dashboard: $(echo "$DASH" | extract "d['result']['totalItemCount']") vật tư, \
$(echo "$DASH" | extract "len(d['result']['topLowStockLines'])") dòng cảnh báo, \
$(echo "$DASH" | extract "len(d['result']['recentMovements'])") giao dịch gần đây"
fi
info "Công ty/nhà máy/kho : 1 / 1 / $(count_rows "$WAREHOUSE_ROWS")"
info "Vật tư / định mức / quy trình : $(count_rows "$ITEM_ROWS") / $(count_rows "$BOM_ROWS") / $(count_rows "$ROUTING_ROWS")"
info "Ca / lịch / tổ sản xuất : $(count_rows "$SHIFT_ROWS") / $(count_rows "$CALENDAR_ROWS") / $(count_rows "$WC_ROWS")"
if [ "$SEED_TRANSACTIONS" = "yes" ]; then
    info "Nhà cung cấp / yêu cầu mua / đơn mua : $(count_rows "$SUPPLIER_ROWS") / $(count_rows "$PR_ROWS") / $(( $(count_rows "$PO_ROWS") + 1 ))"
    info "Đơn bán hàng / lượt MRP / lệnh sản xuất : $(count_rows "$SO_ROWS") / 3 / $(( $(count_rows "$WO_ROWS") + 2 ))"
else
    info "Nhà cung cấp : $(count_rows "$SUPPLIER_ROWS") (chưa có chứng từ mua hàng nào)"
    info "Đơn bán hàng / lượt MRP / lệnh sản xuất : 0 / 0 / 0 — người trình bày tự tạo"
fi
info "Tài khoản : admin + $(count_rows "$USER_ROWS") tài khoản demo (mật khẩu: $DEMO_PASSWORD)"

if [ "$PF_FAILED" -ne 0 ]; then
    die "$PF_FAILED mục preflight KHÔNG đạt — không bàn giao môi trường này."
fi

log "Preflight: PASS toàn bộ"
log "Hoàn tất trong $(( $(date +%s) - START_TS )) giây"

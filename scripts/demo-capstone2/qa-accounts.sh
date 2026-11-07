#!/usr/bin/env bash
# qa-accounts.sh — tài khoản phân quyền cho lượt nghiệm thu live của FE (nối tiếp qa-bootstrap.sh).
#
# Cần có sẵn công ty QA-GD12 + nhà máy QA-A / QA-B (chạy qa-bootstrap.sh trước). Đăng nhập bằng qa_admin.
#
# Tạo 4 nhóm tài khoản, tất cả mật khẩu = QA_USER_PASSWORD:
#   1. Giới hạn nhà máy  : qa_only_a (QA-A), qa_only_b (QA-B)           — vai trò MANAGER, chỉ một nhà máy
#   2. Ma trận của FE    : qa_manager_a, qa_operator_a, qa_operator_b, qa_commercial_a, qa_readonly_a
#   3. Thiếu quyền       : qa_no_price (không thấy giá bán), qa_no_finance (không có quyền tài chính)
#   4. Theo từng bước    : qa_step_* — mỗi tài khoản đúng quyền của MỘT bước trong chuỗi mua → sản xuất → bán
#
# Mọi vai trò tuỳ chỉnh mang mã QA-ROLE-*; phạm vi mang mã QA-SCOPE-*. Chạy lại được: mục nào đã có theo
# mã/tên thì dùng lại; quyền của vai trò tuỳ chỉnh luôn được đối chiếu lại cho khớp bảng bên dưới.
# Mật khẩu của tài khoản đã tồn tại KHÔNG bị đổi.
#
#   ADMIN_PASSWORD=<mật khẩu qa_admin> QA_USER_PASSWORD='QaUser@2026' ./scripts/demo-capstone2/qa-accounts.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
ADMIN_USERNAME="${ADMIN_USERNAME:-qa_admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Dat bien ADMIN_PASSWORD (mat khau qa_admin)}"
QA_USER_PASSWORD="${QA_USER_PASSWORD:?Dat bien QA_USER_PASSWORD (>= 8 ky tu)}"
EMAIL_DOMAIN="${EMAIL_DOMAIN:-omniplant.vn}"

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEMO_DIR/../.." && pwd)"
BODY_TMP="$(mktemp)"
trap 'rm -f "$BODY_TMP"' EXIT
PY=python; command -v "$PY" >/dev/null 2>&1 || PY=python3
# shellcheck source=../demo/lib/common.sh
. "$REPO_ROOT/scripts/demo/lib/common.sh"

TOKEN=$(curl -sS -X POST "$BASE_URL/auth/v1/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" | rid accessToken)
[ -n "$TOKEN" ] || die "dang nhap $ADMIN_USERNAME that bai"

find_id() { call GET "$1" | extract "next(($2), '')"; }

# ── Bảng quyền ───────────────────────────────────────────────────────────────
# Mỗi vai trò = danh sách PERM_* cách nhau bằng dấu cách. Chỉ liệt kê quyền của chính bước đó cộng
# vài quyền ĐỌC cần để màn hình mở được; tài khoản bước KHÔNG có quyền của bước khác.
READ_BASE="PERM_ITEM_READ PERM_ORG_READ PERM_INVENTORY_READ"

declare -A ROLE_PERMS
# 4. Theo từng bước
ROLE_PERMS[QA-ROLE-STEP-PLANNING]="$READ_BASE PERM_BOM_READ PERM_ROUTING_READ PERM_SALES_ORDER_READ PERM_PLANNING_READ PERM_PLANNING_DEMAND_READ PERM_PLANNING_DEMAND_MANAGE PERM_MRP_READ PERM_MRP_RUN PERM_SUPPLY_SUGGESTION_MANAGE"
ROLE_PERMS[QA-ROLE-STEP-PURCHASING]="$READ_BASE PERM_SUPPLIER_READ PERM_PURCHASE_REQUISITION_READ PERM_PURCHASE_REQUISITION_MANAGE PERM_PURCHASE_ORDER_READ PERM_PURCHASE_ORDER_MANAGE"
ROLE_PERMS[QA-ROLE-STEP-RECEIVING]="$READ_BASE PERM_PURCHASE_ORDER_READ PERM_GOODS_RECEIPT_READ PERM_GOODS_RECEIPT_MANAGE PERM_GOODS_RECEIPT_POST"
ROLE_PERMS[QA-ROLE-STEP-PURCHASE-QC]="$READ_BASE PERM_PURCHASE_ORDER_READ PERM_GOODS_RECEIPT_READ PERM_PURCHASE_QC_READ PERM_PURCHASE_QC_MANAGE"
ROLE_PERMS[QA-ROLE-STEP-WORKORDER]="$READ_BASE PERM_BOM_READ PERM_ROUTING_READ PERM_WORK_CENTER_READ PERM_WORK_ORDER_READ PERM_WORK_ORDER_MANAGE PERM_MATERIAL_RESERVATION_MANAGE"
ROLE_PERMS[QA-ROLE-STEP-ISSUE]="$READ_BASE PERM_INVENTORY_MOVE PERM_WORK_ORDER_READ PERM_MATERIAL_ISSUE_MANAGE PERM_MATERIAL_RETURN_READ PERM_MATERIAL_RETURN_MANAGE"
ROLE_PERMS[QA-ROLE-STEP-EXECUTION]="$READ_BASE PERM_WORK_ORDER_READ PERM_WORK_ORDER_EXECUTE PERM_PRODUCTION_EXECUTION_READ PERM_PRODUCTION_EXECUTION_MANAGE PERM_WIP_READ PERM_WIP_MANAGE"
ROLE_PERMS[QA-ROLE-STEP-RECEIPT]="$READ_BASE PERM_WORK_ORDER_READ PERM_PRODUCTION_RECEIPT_READ PERM_PRODUCTION_RECEIPT_MANAGE"
ROLE_PERMS[QA-ROLE-STEP-APPROVAL]="$READ_BASE PERM_WORK_ORDER_READ PERM_PRODUCTION_RECEIPT_READ PERM_PRODUCTION_RECEIPT_APPROVE PERM_MATERIAL_ISSUE_APPROVE PERM_MATERIAL_ISSUE_OVERRIDE"
ROLE_PERMS[QA-ROLE-STEP-QC]="$READ_BASE PERM_WORK_ORDER_READ PERM_PRODUCTION_RECEIPT_READ PERM_PRODUCTION_QC_READ PERM_QUALITY_DISPOSITION"
ROLE_PERMS[QA-ROLE-STEP-DELIVERY]="$READ_BASE PERM_CUSTOMER_READ PERM_SALES_ORDER_READ PERM_DELIVERY_READ PERM_DELIVERY_MANAGE PERM_DELIVERY_POST"
ROLE_PERMS[QA-ROLE-STEP-INVOICE]="$READ_BASE PERM_CUSTOMER_READ PERM_SALES_ORDER_READ PERM_DELIVERY_READ PERM_SALES_PRICE_MANAGE PERM_SALES_INVOICE_READ PERM_SALES_INVOICE_MANAGE PERM_SALES_INVOICE_POST"
ROLE_PERMS[QA-ROLE-STEP-PAYMENT]="$READ_BASE PERM_CUSTOMER_READ PERM_SALES_INVOICE_READ PERM_CUSTOMER_PAYMENT_READ PERM_CUSTOMER_PAYMENT_MANAGE PERM_CUSTOMER_PAYMENT_POST"
ROLE_PERMS[QA-ROLE-STEP-COSTING]="$READ_BASE PERM_WORK_ORDER_READ PERM_WORK_ORDER_VARIANCE_READ PERM_PRODUCTION_COST_CALCULATE PERM_COST_RATE_MANAGE PERM_WORK_ORDER_CLOSE PERM_COSTING_READ"
# 2. Ma trận của FE (không có sẵn vai trò hệ thống tương ứng)
ROLE_PERMS[QA-ROLE-COMMERCIAL]="$READ_BASE PERM_CUSTOMER_READ PERM_CUSTOMER_MANAGE PERM_SALES_ORDER_READ PERM_SALES_ORDER_MANAGE PERM_SALES_PRICE_MANAGE PERM_DELIVERY_READ PERM_DELIVERY_MANAGE PERM_DELIVERY_POST PERM_SALES_INVOICE_READ PERM_SALES_INVOICE_MANAGE PERM_SALES_INVOICE_POST PERM_CUSTOMER_PAYMENT_READ PERM_CUSTOMER_PAYMENT_MANAGE PERM_CUSTOMER_PAYMENT_POST"
ROLE_PERMS[QA-ROLE-READONLY]="$READ_BASE PERM_BOM_READ PERM_ROUTING_READ PERM_WORK_CENTER_READ PERM_SHIFT_READ PERM_WORK_CALENDAR_READ PERM_WORK_ORDER_READ PERM_SALES_ORDER_READ PERM_PLANNING_READ PERM_MRP_READ PERM_SUPPLIER_READ PERM_PURCHASE_ORDER_READ PERM_PURCHASE_REQUISITION_READ PERM_GOODS_RECEIPT_READ PERM_PRODUCTION_EXECUTION_READ PERM_PRODUCTION_RECEIPT_READ PERM_DELIVERY_READ"

# 3. Hai vai trò "MANAGER trừ đi": quyền lấy từ chính vai trò MANAGER lúc chạy, nên không lệch khi seed đổi.
# Thấy giá bán = PERM_SALES_PRICE_MANAGE hoặc bất kỳ quyền hoá đơn nào (docs/roles-and-permissions.md, D6).
NO_PRICE_DROP=" PERM_SALES_PRICE_MANAGE PERM_SALES_INVOICE_READ PERM_SALES_INVOICE_MANAGE PERM_SALES_INVOICE_POST "
# Tài chính = giá bán + hoá đơn + thanh toán + mọi thứ chạm giá thành/chi phí.
NO_FINANCE_DROP="$NO_PRICE_DROP PERM_CUSTOMER_PAYMENT_READ PERM_CUSTOMER_PAYMENT_MANAGE PERM_CUSTOMER_PAYMENT_POST PERM_COSTING_READ PERM_COSTING_MANAGE PERM_COST_RATE_MANAGE PERM_PRODUCTION_COST_CALCULATE PERM_WORK_ORDER_VARIANCE_READ PERM_WORK_ORDER_CLOSE "

# ── Tra cứu chung ────────────────────────────────────────────────────────────
log "Công ty / nhà máy (từ qa-bootstrap.sh)"
CO=$(find_id "/v1/companies?page=0&size=100" "c['companyId'] for c in d['result']['content'] if c['code']=='QA-GD12'")
[ -n "$CO" ] || die "chua co cong ty QA-GD12 — chay qa-bootstrap.sh truoc"
declare -A PLANT
for pc in QA-A QA-B; do
    PLANT[$pc]=$(find_id "/v1/companies/$CO/plants?page=0&size=100" "p['plantId'] for p in d['result']['content'] if p['code']=='$pc'")
    [ -n "${PLANT[$pc]}" ] || die "chua co nha may $pc — chay qa-bootstrap.sh truoc"
    info "plant $pc = ${PLANT[$pc]}"
done

log "Danh mục quyền"
declare -A PERM_ID
for ((pg = 0; ; pg++)); do
    page=$(call GET "/access/v1/permissions?page=$pg&size=100")
    while IFS='|' read -r code id; do [ -n "$code" ] && PERM_ID[$code]=$id; done < <(
        echo "$page" | extract "chr(10).join(p['code']+'|'+p['permissionId'] for p in d['result']['content'])" | tr -d '\r')
    [ "$(echo "$page" | extract "d['result']['last']")" = "True" ] && break
done
info "${#PERM_ID[@]} quyền trong catalog"

# role_id CODE — id vai trò theo mã (hệ thống hoặc tuỳ chỉnh), rỗng nếu chưa có.
role_id() { find_id "/access/v1/roles?page=0&size=100" "r['roleId'] for r in d['result']['content'] if r['code']=='$1'"; }

# role_perm_codes ROLE_ID — mã quyền hiện có của vai trò, mỗi dòng một mã.
role_perm_codes() {
    local pg page
    for ((pg = 0; ; pg++)); do
        page=$(call GET "/access/v1/roles/$1/permissions?page=$pg&size=100")
        echo "$page" | extract "chr(10).join(p['code'] for p in d['result']['content'])" | tr -d '\r'
        [ "$(echo "$page" | extract "d['result']['last']")" = "True" ] && break
    done
}

# ensure_role CODE "tên" "PERM_A PERM_B ..." — tạo vai trò nếu chưa có, rồi cấp thêm/thu hồi bớt cho khớp danh sách.
ensure_role() {
    local code="$1" name="$2" want=" $3 " rid_ have p
    rid_=$(role_id "$code")
    [ -n "$rid_" ] || rid_=$(call POST /access/v1/roles \
        "{\"companyId\":\"$CO\",\"code\":\"$code\",\"name\":\"$name\",\"description\":\"QA acceptance role\"}" | rid roleId)
    have=" $(role_perm_codes "$rid_" | tr '\n' ' ') "
    for p in $3; do
        [ -n "${PERM_ID[$p]:-}" ] || die "quyen $p khong co trong catalog (ten da doi?)"
        case "$have" in *" $p "*) ;; *) call POST "/access/v1/roles/$rid_/permissions/${PERM_ID[$p]}" >/dev/null ;; esac
    done
    for p in $have; do
        case "$want" in *" $p "*) ;; *) call DELETE "/access/v1/roles/$rid_/permissions/${PERM_ID[$p]}" >/dev/null; info "thu hoi $p khoi $code" ;; esac
    done
    ROLE[$code]=$rid_
    info "vai tro $code: $(echo $3 | wc -w) quyen"
}

declare -A ROLE
log "Vai trò tuỳ chỉnh"
for code in "${!ROLE_PERMS[@]}"; do ensure_role "$code" "${code#QA-ROLE-}" "${ROLE_PERMS[$code]}"; done

# MANAGER trừ quyền
MGR=$(role_id MANAGER); [ -n "$MGR" ] || die "khong thay vai tro MANAGER"
MGR_PERMS=$(role_perm_codes "$MGR" | tr '\n' ' ')
subtract() { local drop="$1" out="" p; for p in $MGR_PERMS; do case "$drop" in *" $p "*) ;; *) out="$out $p" ;; esac; done; echo "$out"; }
ensure_role QA-ROLE-NO-PRICE   "MANAGER without sales-price visibility" "$(subtract " $NO_PRICE_DROP ")"
ensure_role QA-ROLE-NO-FINANCE "MANAGER without finance permissions"    "$(subtract " $NO_FINANCE_DROP ")"
ROLE[MANAGER]=$MGR; ROLE[OPERATOR]=$(role_id OPERATOR)

# ── Phạm vi ──────────────────────────────────────────────────────────────────
log "Phạm vi"
declare -A SCOPE
ensure_scope() { # code name plantCodes...
    local code="$1" name="$2" id pc; shift 2
    id=$(find_id "/access/v1/scopes?page=0&size=100" "s['scopeId'] for s in d['result']['content'] if s['code']=='$code'")
    [ -n "$id" ] || id=$(call POST /access/v1/scopes "{\"code\":\"$code\",\"name\":\"$name\",\"scopeType\":\"PLANT\"}" | rid scopeId)
    for pc in "$@"; do
        has=$(call GET "/access/v1/scopes/$id/resources?page=0&size=100" \
            | extract "len([r for r in d['result']['content'] if r['resourceId']=='${PLANT[$pc]}'])")
        [ "$has" != "0" ] || call POST "/access/v1/scopes/$id/resources" \
            "{\"resourceType\":\"PLANT\",\"resourceId\":\"${PLANT[$pc]}\"}" >/dev/null
    done
    SCOPE[$code]=$id; info "scope $code = $id ($*)"
}
ensure_scope QA-SCOPE-A  "QA plant A only" QA-A
ensure_scope QA-SCOPE-B  "QA plant B only" QA-B

# ── Tài khoản ────────────────────────────────────────────────────────────────
# make_user USERNAME ROLE_CODE SCOPE_CODE
# Người dùng mới tự có vai trò hệ thống OPERATOR (UserService.create) nhưng quyền nghiệp vụ chỉ đọc từ
# phân công theo phạm vi (PermissionGuard), nên vai trò mặc định đó không cộng thêm quyền ở đây.
declare -A USER_ID
make_user() {
    local u="$1" role="$2" scope="$3" id has
    id=$(find_id "/users/v1?page=0&size=100&search=$u" "x['userId'] for x in d['result']['content'] if x['username']=='$u'")
    if [ -z "$id" ]; then
        id=$(call POST /users/v1 "{\"username\":\"$u\",\"email\":\"$u@$EMAIL_DOMAIN\",\"password\":\"$QA_USER_PASSWORD\"}" | rid userId)
    fi
    has=$(call GET "/access/v1/assignments?userId=$id&page=0&size=50" \
        | extract "len([a for a in d['result']['content'] if a['roleId']=='${ROLE[$role]}' and a['scopeId']=='${SCOPE[$scope]}' and a['status']=='ACTIVE'])")
    [ "$has" != "0" ] || call POST /access/v1/assignments \
        "{\"userId\":\"$id\",\"roleId\":\"${ROLE[$role]}\",\"scopeId\":\"${SCOPE[$scope]}\"}" >/dev/null
    USER_ID[$u]=$id
    printf '    %-22s %-26s %s\n' "$u" "$role" "$scope" >&2
}

log "Tài khoản"
# 1. Giới hạn nhà máy
make_user qa_only_a        MANAGER             QA-SCOPE-A
make_user qa_only_b        MANAGER             QA-SCOPE-B
# 2. Ma trận của FE
make_user qa_manager_a     MANAGER             QA-SCOPE-A
make_user qa_operator_a    OPERATOR            QA-SCOPE-A
make_user qa_operator_b    OPERATOR            QA-SCOPE-B
make_user qa_commercial_a  QA-ROLE-COMMERCIAL  QA-SCOPE-A
make_user qa_readonly_a    QA-ROLE-READONLY    QA-SCOPE-A
# 3. Thiếu quyền (đặt ở QA-A)
make_user qa_no_price      QA-ROLE-NO-PRICE    QA-SCOPE-A
make_user qa_no_finance    QA-ROLE-NO-FINANCE  QA-SCOPE-A
# 4. Theo từng bước (đặt ở QA-A)
for step in planning purchasing receiving purchase-qc workorder issue execution receipt approval qc delivery invoice payment costing; do
    make_user "qa_step_${step//-/_}" "QA-ROLE-STEP-${step^^}" QA-SCOPE-A
done

echo ""
echo "mat khau (moi tai khoan tren) = QA_USER_PASSWORD"
for u in "${!USER_ID[@]}"; do echo "userId  $u = ${USER_ID[$u]}"; done | sort

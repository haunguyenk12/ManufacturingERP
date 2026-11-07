#!/usr/bin/env bash
# qa-bootstrap.sh — nền cho lượt nghiệm thu live của FE (FE_REQUEST_LIVE_QA_BOOTSTRAP_2026-11-04).
#
# Tạo ĐÚNG gói B01–B04, không hơn:
#   B01  tài khoản qa_admin (vai trò ADMIN, phạm vi GLOBAL_ALL)
#   B03  công ty QA-GD12, hai nhà máy QA-A / QA-B (Asia/Ho_Chi_Minh), kho từng nhà máy, đơn vị KG/PCS
# FE tự dựng phần còn lại (tài khoản theo vai trò, phạm vi, vật tư, BOM, chứng từ) — script này
# CỐ Ý không tạo qa_manager_a... để FE kiểm được chính luồng tạo người dùng/phân quyền.
#
# Chạy lại được: mục nào đã có (theo mã) thì dùng lại, không tạo trùng.
#   ADMIN_PASSWORD=... QA_ADMIN_PASSWORD=... ./scripts/demo-capstone2/qa-bootstrap.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Dat bien ADMIN_PASSWORD}"
QA_ADMIN_PASSWORD="${QA_ADMIN_PASSWORD:?Dat bien QA_ADMIN_PASSWORD (>= 8 ky tu, co hoa/thuong/so/dac biet)}"
QA_ADMIN_EMAIL="${QA_ADMIN_EMAIL:-qa_admin@omniplant.vn}"
TZ_NAME="Asia/Ho_Chi_Minh"

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

# find_id PATH EXPR — id của phần tử đầu tiên khớp, rỗng nếu không có.
find_id() { call GET "$1" | extract "next(($2), '')"; }

log "Công ty QA-GD12"
CO=$(find_id "/v1/companies?page=0&size=200" "c['companyId'] for c in d['result']['content'] if c['code']=='QA-GD12'")
[ -n "$CO" ] || CO=$(call POST /v1/companies '{"code":"QA-GD12","name":"QA GD12 Acceptance"}' | rid companyId)
info "companyId=$CO"

declare -A PLANT WH
for pc in QA-A QA-B; do
    pid=$(find_id "/v1/companies/$CO/plants?page=0&size=100" "p['plantId'] for p in d['result']['content'] if p['code']=='$pc'")
    [ -n "$pid" ] || pid=$(call POST "/v1/companies/$CO/plants" \
        "{\"code\":\"$pc\",\"name\":\"Plant $pc\",\"timezone\":\"$TZ_NAME\"}" | rid plantId)
    PLANT[$pc]=$pid; info "plant $pc = $pid"
done

mkwh() { # plantCode code name type
    local pid="${PLANT[$1]}" id
    id=$(find_id "/v1/plants/$pid/warehouses?page=0&size=100" "w['warehouseId'] for w in d['result']['content'] if w['code']=='$2'")
    [ -n "$id" ] || id=$(call POST "/v1/plants/$pid/warehouses" "{\"code\":\"$2\",\"name\":\"$3\",\"type\":\"$4\"}" | rid warehouseId)
    WH[$2]=$id; info "warehouse $2 = $id"
}
mkwh QA-A QA-A-RM  "QA-A Raw Material"      RAW_MATERIAL
mkwh QA-A QA-A-FG  "QA-A Finished Goods"    FINISHED_GOODS
mkwh QA-A QA-A-FG2 "QA-A Finished Goods 2"  FINISHED_GOODS
mkwh QA-B QA-B-RM  "QA-B Raw Material"      RAW_MATERIAL

log "Đơn vị tính (toàn cục)"
for u in "KG|Kilogram" "PCS|Piece"; do
    code="${u%%|*}"; name="${u##*|}"
    id=$(find_id "/v1/uoms?page=0&size=200" "x['uomId'] for x in d['result']['content'] if x['code']=='$code'")
    if [ -z "$id" ]; then call POST /v1/uoms "{\"code\":\"$code\",\"name\":\"$name\",\"description\":\"QA\"}" >/dev/null; info "$code tao moi"; else info "$code da co"; fi
done

log "Tài khoản qa_admin"
UID_=$(find_id "/users/v1?page=0&size=200&search=qa_admin" "u['userId'] for u in d['result']['content'] if u['username']=='qa_admin'")
if [ -z "$UID_" ]; then
    UID_=$(call POST /users/v1 \
        "{\"username\":\"qa_admin\",\"email\":\"$QA_ADMIN_EMAIL\",\"password\":\"$QA_ADMIN_PASSWORD\"}" | rid userId)
    info "tao moi userId=$UID_"
else
    info "da co userId=$UID_ (mat khau KHONG bi doi)"
fi
ROLE=$(find_id "/access/v1/roles?page=0&size=100" "r['roleId'] for r in d['result']['content'] if r['code']=='ADMIN'")
SCOPE=$(find_id "/access/v1/scopes?page=0&size=100" "s['scopeId'] for s in d['result']['content'] if s['code']=='GLOBAL_ALL'")
HAS=$(call GET "/access/v1/assignments?userId=$UID_&page=0&size=20" \
    | extract "len([a for a in d['result']['content'] if a['roleId']=='$ROLE' and a['scopeId']=='$SCOPE' and a['status']=='ACTIVE'])")
if [ "$HAS" = "0" ]; then
    call POST /access/v1/assignments "{\"userId\":\"$UID_\",\"roleId\":\"$ROLE\",\"scopeId\":\"$SCOPE\"}" >/dev/null
    info "gan ADMIN @ GLOBAL_ALL (khong het han)"
else
    info "da co phan cong ADMIN @ GLOBAL_ALL"
fi

log "Kiểm đăng nhập qa_admin"
QT=$(curl -sS -X POST "$BASE_URL/auth/v1/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"qa_admin\",\"password\":\"$QA_ADMIN_PASSWORD\"}" | rid accessToken 2>/dev/null || true)
[ -n "$QT" ] && info "dang nhap qa_admin OK" || warn "dang nhap qa_admin KHONG thanh cong (mat khau da co khac QA_ADMIN_PASSWORD?)"

echo ""
echo "companyId  QA-GD12 = $CO"
for pc in QA-A QA-B; do echo "plantId    $pc = ${PLANT[$pc]}"; done
for w in QA-A-RM QA-A-FG QA-A-FG2 QA-B-RM; do echo "warehouse  $w = ${WH[$w]}"; done
echo "userId     qa_admin = $UID_"

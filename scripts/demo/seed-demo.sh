#!/usr/bin/env bash
# seed-demo.sh — dựng lại toàn bộ dữ liệu demo của Manufacturing ERP từ cơ sở dữ liệu trắng,
# theo chủ đề nhà máy xe đạp "Công ty Cổ phần Xe đạp Việt".
#
# ⚠️ CHẠY BẰNG GIT BASH (không dùng PowerShell/CMD). Tên hiển thị là tiếng Việt có dấu; PowerShell
#    5.1 giải mã UTF-8 sai và sẽ ghi vào cơ sở dữ liệu những chuỗi hỏng kiểu "BÃ ...".
#
# QUY TRÌNH ĐẦY ĐỦ
#   1. Dừng backend đang chạy.
#   2. docker compose down -v && docker compose up -d
#   3. Khởi động lại backend, đợi Flyway chạy xong V1..V58.
#   4. ./scripts/demo/seed-demo.sh
#
# Script dùng MÃ CỐ ĐỊNH (đó chính là điều làm dữ liệu dễ đọc dễ nhớ) nên KHÔNG chạy lại được trên
# cơ sở dữ liệu đã có dữ liệu demo. Hỏng giữa chừng thì xoá sạch rồi chạy lại — đừng vá tay. Khi
# thất bại, script ghi demo-ids.partial.json để còn tìm lại được những gì đã kịp tạo.
#
# Mọi thứ đi qua REST API thật, không có câu SQL nào: mã chứng từ sinh ở @PrePersist, ảnh chụp
# BOM/quy trình trên lệnh sản xuất, projection stock_balances, @Version và nhật ký kiểm toán chỉ tồn
# tại nếu tầng service thực sự chạy. Chèn thẳng vào cơ sở dữ liệu sẽ tạo ra những dòng mà chính hệ
# thống không bao giờ sinh ra được.
#
# Yêu cầu: bash >= 4, curl, python (không dùng jq), GNU date.
#
# Biến môi trường (đều có giá trị mặc định):
#   BASE_URL=http://localhost:8080/api/v1 ADMIN_USERNAME=<bootstrap-user> ADMIN_PASSWORD='<secret>'

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api/v1}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Set ADMIN_PASSWORD explicitly; no default credential is allowed}"

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEMO_DIR/../.." && pwd)"
MANIFEST="${MANIFEST:-$REPO_ROOT/demo-ids.json}"

BODY_TMP="$(mktemp)"
trap 'rm -f "$BODY_TMP"' EXIT

# shellcheck source=lib/common.sh
. "$DEMO_DIR/lib/common.sh"
# shellcheck source=lib/catalogue.sh
. "$DEMO_DIR/lib/catalogue.sh"

# On failure, dump whatever ids exist so a half-built dataset is still findable. Without this the
# only way to locate ~200 orphaned rows is by hand in psql.
on_exit() {
    local rc=$?
    rm -f "$BODY_TMP"
    if [ $rc -ne 0 ] && [ "${#RECORD_KEYS[@]}" -gt 0 ]; then
        dump_manifest "$REPO_ROOT/demo-ids.partial.json" 2>/dev/null || true
        cat >&2 <<EOF

--------------------------------------------------------------------------------
Seed THẤT BẠI. Những gì đã kịp tạo được ghi ở demo-ids.partial.json

Script không chạy lại được trên dữ liệu dở dang. Xoá sạch rồi chạy lại:

    docker compose down -v && docker compose up -d
    # khởi động lại backend, đợi Flyway xong
    ./scripts/demo/seed-demo.sh
--------------------------------------------------------------------------------
EOF
    fi
}
trap on_exit EXIT

STAGES=(
    00-preflight
    10-org
    15-access
    20-items
    30-shopfloor
    40-bom-routing
    50-suppliers
    60-stock
    70-workorders
    80-sales
    85-planning
    90-fulfilment
    95-purchasing
    99-alerts
    999-manifest
)

# --only / --from exist for iterating on THIS SCRIPT against a database you are about to wipe
# anyway. They are not a repair mechanism: no create endpoint in this API is replay-safe, and the
# state matrix is irreversible (there is no un-release, un-approve, un-QC, un-close).
ONLY=""; FROM=""
while [ $# -gt 0 ]; do
    case "$1" in
        --only) ONLY="$2"; shift 2 ;;
        --from) FROM="$2"; shift 2 ;;
        -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}" >&2; exit 0 ;;
        *) die "tham số không hiểu: $1" ;;
    esac
done
if [ -n "$ONLY" ] || [ -n "$FROM" ]; then
    warn "Đang chạy một phần (--only/--from). Chỉ dùng khi đang sửa chính script này;"
    warn "bộ dữ liệu sinh ra sẽ KHÔNG đầy đủ."
fi

START_TS=$(date +%s)
SKIPPING=0
[ -n "$FROM" ] && SKIPPING=1

for stage in "${STAGES[@]}"; do
    if [ -n "$ONLY" ] && [ "$stage" != "$ONLY" ]; then continue; fi
    if [ $SKIPPING -eq 1 ]; then
        if [ "$stage" = "$FROM" ]; then SKIPPING=0; else continue; fi
    fi
    # Sourced, not executed: every stage shares one shell, so the ~200 ids collected along the way
    # stay in scope without a save/load round trip between stages.
    # shellcheck source=/dev/null
    . "$DEMO_DIR/stages/$stage.sh"
done

log "Hoàn tất trong $(( $(date +%s) - START_TS )) giây"

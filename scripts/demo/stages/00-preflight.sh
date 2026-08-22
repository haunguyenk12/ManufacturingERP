#!/usr/bin/env bash
# 00-preflight — refuse to start unless the environment and the database are both in the one state
# this script supports. Everything here is cheap; every check here has a matching failure that would
# otherwise surface a few hundred HTTP calls later, with half a dataset already written.

stage_banner "0. Kiểm tra môi trường"

# ── bash ─────────────────────────────────────────────────────────────────────
# The id registry is an associative array (declare -A), which is bash 4+.
if [ "${BASH_VERSINFO:-0}" -lt 4 ]; then
    die "cần bash >= 4 (đang chạy ${BASH_VERSION:-?}). Trên Windows hãy dùng Git Bash."
fi

# ── external tools ───────────────────────────────────────────────────────────
command -v curl >/dev/null 2>&1 || die "không tìm thấy curl trên PATH"

# Order matters and is not a typo: on Windows, `python3` is usually a Microsoft Store stub that
# prints an advert and exits non-zero, while `python` is the real interpreter.
PY=python
command -v "$PY" >/dev/null 2>&1 || PY=python3
command -v "$PY" >/dev/null 2>&1 || die "cần python hoặc python3 trên PATH (script không dùng jq)"
"$PY" -c 'import json,sys' >/dev/null 2>&1 || die "$PY không chạy được — kiểm tra lại cài đặt Python"
info "python: $("$PY" -V 2>&1)"

# GNU date only. BSD date (macOS) silently means something else by -d, which would put every
# relative date in the dataset somewhere unintended instead of failing.
date -u -d '+1 day' +%F >/dev/null 2>&1 \
    || die "cần GNU date (tham số -d). Trên macOS: brew install coreutils rồi dùng gdate."
info "hôm nay (UTC): $(d_today)"

# ── line endings ─────────────────────────────────────────────────────────────
# A single CR on the `set -euo pipefail` line turns this script into failures that look like API
# bugs. .gitattributes pins eol=lf, but a zip download or a stray editor can still undo that.
for f in "$DEMO_DIR"/seed-demo.sh "$DEMO_DIR"/lib/*.sh "$DEMO_DIR"/stages/*.sh; do
    if grep -q $'\r' "$f" 2>/dev/null; then
        die "file $f có ký tự xuống dòng CRLF. Chạy: git config core.autocrlf input rồi checkout lại."
    fi
done
info "line endings: LF"

# ── API reachable + credentials ──────────────────────────────────────────────
log "Đăng nhập $ADMIN_USERNAME tại $BASE_URL"
LOGIN_RESP=$(curl -sS -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" 2>&1) \
    || die "không gọi được $BASE_URL/auth/login — backend đã chạy chưa? ($LOGIN_RESP)"

TOKEN=$(echo "$LOGIN_RESP" | rid accessToken 2>/dev/null || true)
[ -n "${TOKEN:-}" ] || { echo "$LOGIN_RESP" >&2; die "đăng nhập thất bại"; }
info "đăng nhập thành công"

# ── the database must be empty of this dataset ───────────────────────────────
# The whole point of this seeder is memorable, FIXED codes, so it is not re-runnable: the first
# duplicate would abort somewhere in the middle with a bare RESOURCE_ALREADY_EXISTS. Catching it
# here turns that into an instruction.
EXISTING=$(call GET "/companies?page=0&size=200" \
    | extract "','.join(c['code'] for c in d['result']['content'])")
case ",$EXISTING," in
    *",$CO_CODE,"*)
        cat >&2 <<EOF

DỪNG: công ty $CO_CODE đã tồn tại trong cơ sở dữ liệu.

Script này dùng mã cố định và KHÔNG chạy lại được trên dữ liệu cũ.
Hãy xoá sạch rồi chạy lại:

    docker compose down -v
    docker compose up -d
    # đợi Postgres healthy, khởi động lại backend để Flyway chạy lại V1..V58
    ./scripts/demo/seed-demo.sh

EOF
        exit 1
        ;;
esac
info "cơ sở dữ liệu chưa có $CO_CODE — tiếp tục"

remember "meta.baseUrl" "$BASE_URL"
note    "meta.generatedAt" "$(ts_now)"
note    "meta.demoPassword" "$DEMO_PASSWORD"

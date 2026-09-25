#!/usr/bin/env bash
# restore-db.sh — nạp lại file đã tạo bằng backup-db.sh. Thay thế hoàn toàn cho việc chạy lại
# reset-db.sh + bootstrap admin + seed.sh — nhanh hơn nhiều vì không gọi API, không cần Flyway
# chạy lại (dump đã có sẵn schema đầy đủ).
#
# 🔴 BACKEND PHẢI ĐANG TẮT trước khi chạy script này — kết nối đang mở sẽ chặn DROP DATABASE.
#
# Cách dùng:
#   ./scripts/demo-capstone2/restore-db.sh                # nạp backups/d26-seeded.dump
#   ./scripts/demo-capstone2/restore-db.sh my-checkpoint   # nạp backups/my-checkpoint.dump
#
# Sau khi chạy xong: khởi động lại backend BÌNH THƯỜNG (không cần cờ bootstrap admin — tài
# khoản admin đã có sẵn trong file backup).
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi
DB="${POSTGRES_DB:-manufacturing_erp}"
USER="${POSTGRES_USER:-postgres}"
REDIS_PW="${REDIS_PASSWORD:-}"

NAME="${1:-d26-seeded}"
FILE="scripts/demo-capstone2/backups/$NAME.dump"

if [ ! -f "$FILE" ]; then
    echo "khong tim thay file backup: $FILE" >&2
    echo "cac file backup hien co:" >&2
    ls -1 scripts/demo-capstone2/backups/*.dump 2>/dev/null || echo "  (chua co file nao — chay backup-db.sh truoc)" >&2
    exit 1
fi

if ! docker exec erp-postgres pg_isready -U "$USER" >/dev/null 2>&1; then
    echo "postgres khong chay (docker ps de kiem tra)" >&2
    exit 1
fi

echo "==> ngat moi ket noi dang mo toi '$DB' (backend con chay se lam DROP DATABASE that bai)" >&2
docker exec erp-postgres psql -U "$USER" -d postgres -v ON_ERROR_STOP=1 -c \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB' AND pid <> pg_backend_pid();" >/dev/null

echo "==> drop + tao lai database '$DB'" >&2
docker exec erp-postgres psql -U "$USER" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$DB\";" >/dev/null
docker exec erp-postgres psql -U "$USER" -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$DB\";" >/dev/null

echo "==> pg_restore tu $FILE" >&2
docker exec -i erp-postgres pg_restore -U "$USER" -d "$DB" --no-owner --role="$USER" < "$FILE"

if [ -n "$REDIS_PW" ]; then
    echo "==> xoa Redis (token/session cu tro toi user_id khong con y nghia sau restore)" >&2
    docker exec erp-redis redis-cli -a "$REDIS_PW" --no-auth-warning FLUSHALL >/dev/null
fi

echo "==> xong. Khoi dong lai backend BINH THUONG (KHONG can cờ bootstrap-admin):" >&2
echo "    mvn -o spring-boot:run -Dspring-boot.run.profiles=dev" >&2

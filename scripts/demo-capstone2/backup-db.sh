#!/usr/bin/env bash
# backup-db.sh — dump toàn bộ Postgres hiện tại ra một file trên máy bạn, để restore-db.sh
# nạp lại sau này mà KHÔNG cần chạy lại seed.sh (không cần bootstrap admin, không cần gọi API).
#
# Dùng lúc nào: SAU KHI seed.sh đã chạy xong và preflight PASS toàn bộ — chụp lại đúng trạng
# thái "sạch" đó. Backend có đang chạy hay không đều được (pg_dump tự chụp một snapshot nhất
# quán qua MVCC, không cần dừng backend để backup).
#
# Cách dùng:
#   ./scripts/demo-capstone2/backup-db.sh                # ghi vào backups/d26-seeded.dump
#   ./scripts/demo-capstone2/backup-db.sh my-checkpoint   # ghi vào backups/my-checkpoint.dump
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Nạp POSTGRES_USER / POSTGRES_DB từ .env (không hardcode — .env là nguồn thật).
if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi
DB="${POSTGRES_DB:-manufacturing_erp}"
USER="${POSTGRES_USER:-postgres}"

NAME="${1:-d26-seeded}"
BACKUP_DIR="scripts/demo-capstone2/backups"
FILE="$BACKUP_DIR/$NAME.dump"
mkdir -p "$BACKUP_DIR"

if ! docker exec erp-postgres pg_isready -U "$USER" >/dev/null 2>&1; then
    echo "postgres khong chay (docker ps de kiem tra)" >&2
    exit 1
fi

echo "==> pg_dump (custom format, nen san) database '$DB' -> $FILE" >&2
docker exec erp-postgres pg_dump -U "$USER" -d "$DB" -Fc > "$FILE"

SIZE=$(du -h "$FILE" 2>/dev/null | cut -f1)
echo "==> xong: $FILE ($SIZE)" >&2
echo "    Nạp lại sau này bằng: ./scripts/demo-capstone2/restore-db.sh $NAME" >&2

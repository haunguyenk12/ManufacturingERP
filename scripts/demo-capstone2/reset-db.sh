#!/usr/bin/env bash
# reset-db.sh — xoá sạch Postgres + Redis rồi để Flyway dựng lại lược đồ từ đầu.
#
# Backend PHẢI đang tắt khi chạy script này (Flyway chạy lúc khởi động, và các kết nối còn mở sẽ
# giữ volume lại). Sau khi chạy xong: khởi động lại backend, đợi Flyway xong, rồi seed.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "==> docker compose down -v (xoá cả volume: TOÀN BỘ dữ liệu sẽ mất)" >&2
docker compose down -v
echo "==> docker compose up -d" >&2
docker compose up -d

for i in $(seq 1 60); do
    if docker exec erp-postgres pg_isready -U "${POSTGRES_USER:-postgres}" >/dev/null 2>&1; then
        echo "==> postgres sẵn sàng sau ${i}s" >&2
        exit 0
    fi
    sleep 1
done
echo "postgres khong san sang sau 60s" >&2
exit 1

#!/usr/bin/env bash
# set-lot-expiry.sh — điền hạn dùng cho mọi lô của bộ dữ liệu demo.
#
# 🔴 ĐÂY LÀ SCRIPT DUY NHẤT CỦA BỘ NÀY GHI THẲNG SQL, và có lý do hẹp:
#    REST API **không có đường nào** đặt hạn dùng cho lô. `InventoryLot.expiresAt` là cột đọc-ghi
#    ở tầng entity, nhưng không request DTO nào mang field đó — không phải ở
#    StockReceiveRequest, không phải ở GoodsReceiptLineRequest, không phải ở
#    ProductionReceiptPostRequest. Nên không thể "seed qua API thật" như mọi thứ khác
#    (nguyên tắc R3/R7 của bộ seeder), và để trống thì cột "Expiry" trên màn hình Lô hàng
#    hiện dấu "—" ở mọi dòng.
#
#    Vì vậy nó nằm ở FILE RIÊNG, không nhét vào seed.sh: seed.sh giữ nguyên bất biến "không một
#    câu SQL nào". Chạy nó là một quyết định có ý thức, không phải tác dụng phụ.
#
#    An toàn ở chỗ hạn dùng là **thuộc tính hiển thị thuần**: không cơ chế nào của backend đọc
#    `expires_at` để quyết định gì (không có luồng tự chuyển lô sang EXPIRED — xem
#    `docs/demo-capstone2-guide.md` §8). Ghi cột này không tạo ra trạng thái mà tầng service
#    không bao giờ sinh được, khác hẳn việc chèn tay một phiếu nhập kho hay một dòng tồn kho.
#
# Cách dùng (sau khi seed xong, backend có thể đang chạy):
#   ./scripts/demo-capstone2/set-lot-expiry.sh              # mọi lô chưa có hạn
#   LOT_SHELF_LIFE_DAYS=365 ./scripts/demo-capstone2/set-lot-expiry.sh
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# shellcheck source=catalogue.sh
. scripts/demo-capstone2/catalogue.sh

if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi
DB="${POSTGRES_DB:-manufacturing_erp}"
USER="${POSTGRES_USER:-postgres}"
DAYS="${LOT_SHELF_LIFE_DAYS:-730}"

docker exec erp-postgres pg_isready -U "$USER" >/dev/null 2>&1 \
    || { echo "postgres khong chay (docker ps de kiem tra)" >&2; exit 1; }

echo "==> đặt hạn dùng = ngày nhập + $DAYS ngày cho mọi lô chưa có hạn" >&2

# Hạn tính TỪ received_at của chính lô, không phải từ lúc chạy script — nếu không, hai lô nhập
# cách nhau vài tháng sẽ có cùng ngày hết hạn và trông vô lý trên slide.
docker exec -i erp-postgres psql -U "$USER" -d "$DB" -v ON_ERROR_STOP=1 <<SQL
UPDATE inventory_lots
   SET expires_at = received_at + INTERVAL '$DAYS days'
 WHERE expires_at IS NULL;
SELECT count(*) AS lo_co_han_dung FROM inventory_lots WHERE expires_at IS NOT NULL;
SQL

echo "==> xong" >&2

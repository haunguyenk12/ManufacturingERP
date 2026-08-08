# Hướng dẫn: Bộ dữ liệu test disposable cho Work Order

> Ngày viết: 2026-08-08 · Đối tượng: FE team
> Script: `scripts/seed-work-order-test-fixture.sh`

## FE đã yêu cầu gì

Một bộ dữ liệu test dùng ngay, cho phép mutate/xoá tự do (disposable), gồm:

- Plant có Warehouse và tồn kho `AVAILABLE`.
- Work Order trạng thái `RELEASED` hoặc `IN_PROGRESS`.
- Ít nhất một `MaterialReservation` `ACTIVE` còn số lượng (`remainingQuantity > 0`).
- Ở trạng thái mà cả 4 hành động sau đều **còn thực hiện được**: xuất một phần, xuất vượt BOM,
  hoàn thành (report đủ sản lượng kế hoạch), và Close.

## Script tạo gì, và dừng ở đâu

`scripts/seed-work-order-test-fixture.sh` gọi thẳng vào REST API thật (không insert SQL trực tiếp —
Work Order có nhiều field snapshot + state machine sống trong tầng service, insert tay rất dễ vi
phạm bất biến) để dựng:

1. Company → Plant → Warehouse (mã tự sinh, duy nhất mỗi lần chạy).
2. 1 item nguyên liệu (`RAW_MATERIAL`, không lot-tracked) + 1 item thành phẩm (`FINISHED_GOOD`,
   không lot-tracked).
3. BOM: 1 thành phẩm cần 2 nguyên liệu/đơn vị, không hao hụt (`scrapRate = 0`). Đã `activate`.
4. Nhận **500** đơn vị nguyên liệu vào kho (`AVAILABLE`).
5. Work Order `plannedQuantity = 100` ⇒ `requiredQuantity` của dòng component = **200**.
6. `plan` → `PLANNED`.
7. `reserve` (auto FEFO) → 1 reservation `ACTIVE`, `quantity = 200`, **chưa tiêu thụ gì**.
8. `release` → **`RELEASED`**.

Script **dừng đúng ở đó** — cố ý **không** tự thực hiện xuất/hoàn thành/close, để 4 hành động đó vẫn
còn nguyên cho FE tự bấm/tự gọi API. 300 đơn vị nguyên liệu dư ra ngoài 200 đã reserve chính là phần
headroom để test xuất vượt BOM.

## Chạy thế nào

```bash
# Mặc định: BASE_URL=http://localhost:8080/api/v1, ADMIN_USERNAME=admin, ADMIN_PASSWORD=Admin@123
./scripts/seed-work-order-test-fixture.sh

# Hoặc override:
BASE_URL=http://localhost:8081/api/v1 ADMIN_PASSWORD='...' ./scripts/seed-work-order-test-fixture.sh
```

Yêu cầu: `curl` + `python`/`python3` trên PATH (không cần `jq`). Chạy được nhiều lần liên tiếp không
lỗi — mỗi lần sinh Company/Plant/Warehouse/Item/BOM/WO **hoàn toàn mới** (hậu tố timestamp + random),
không đụng tới dữ liệu của lần chạy trước.

Toàn bộ log tiến trình + cheat-sheet 4 lệnh `curl` mẫu (đã điền sẵn ID thật) in ra **stderr**; một
khối JSON chứa mọi ID (company/plant/warehouse/item/BOM/work order/component line/reservation/access
token) in ra **stdout** — script có thể pipe được: `./scripts/seed-work-order-test-fixture.sh | python -m json.tool`.

## 4 hành động — đã xác nhận chạy được qua HTTP thật trên đúng state script tạo ra

| # | Hành động | Endpoint | Kết quả xác nhận |
|---|---|---|---|
| 1 | Xuất một phần | `POST /material-issues` (flat, kèm `reservationId`, `quantity` < remaining) | `201`; WO tự chuyển `IN_PROGRESS`; reservation vẫn `ACTIVE`, `remainingQuantity` giảm đúng phần đã xuất |
| 2 | Xuất vượt BOM | `POST /work-orders/{id}/material-issues` (`lines[]`, không `reservationId`, `quantity` > `remainingQuantity` của component line, kèm `overrideReason`) | `201`, `lines[0].overIssue = true` |
| 3 | Hoàn thành | `POST /work-orders/{id}/production-executions` (`goodQuantity` = đủ `plannedQuantity`) | `201`; `workOrderStatus` trong response = `COMPLETED` |
| 4 | Close | `POST /work-orders/{id}/close` (không body, chỉ gọi được từ `COMPLETED`) | `200`, `status = CLOSED`, `closedAt` được set; reservation còn sót tự động chuyển `CANCELLED` (giải phóng lại tồn khả dụng — đây là phần "Reconcile" của `close`, xem `module/workorder/CLAUDE.md` B100) |

Ghi chú quan trọng nếu FE tự viết lại kịch bản này:

- **"Xuất vượt BOM" so với `componentLine.remainingQuantity()`** (= `requiredQuantity − issuedQuantity`
  cộng dồn), **không** so với số lượng còn lại của reservation. Vì vậy dòng xuất vượt trong ví dụ trên
  **không** truyền `reservationId` — nó rút thẳng từ tồn kho `AVAILABLE` của kho, độc lập với
  reservation.
- **"Hoàn thành" độc lập hoàn toàn với việc đã xuất bao nhiêu vật tư** — `ProductionExecutionService`
  không kiểm tra `issuedQuantity` trước khi cho `report`. Vì vậy hành động #3 gọi được ngay cả khi FE
  chưa từng gọi #1/#2 — thứ tự giữa 4 hành động không bị ép buộc, chỉ có #4 phải sau #3 (đòi
  `COMPLETED`).
- Response `403 PERMISSION_DENIED` ở hành động #2 nghĩa là tài khoản đang dùng thiếu
  `PERM_MATERIAL_ISSUE_OVERRIDE` — tài khoản `admin` mặc định có quyền này.

## Dọn dẹp

Không có bước dọn dẹp riêng — đây là dữ liệu bình thường qua endpoint bình thường, disposable đúng
nghĩa: để nguyên trong DB dev không tốn gì, hoặc tự `cancel`/`close`/deactivate như mọi Work Order
khác nếu muốn dọn tay.

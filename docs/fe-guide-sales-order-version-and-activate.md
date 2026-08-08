# Hướng dẫn tích hợp: Sales Order `version` & Activate API (Plant/Warehouse/Item)

> Ngày viết: 2026-08-06 · Đối tượng: FE team
> Phạm vi: 2 điểm FE báo thiếu khi tích hợp `PATCH /sales-orders/{id}` — **không** phải bản tổng hợp
> toàn bộ API. Envelope/pagination/auth chung: `docs/api-guide-for-frontend.md §2`.

---

## 0. Việc chung trước khi đọc tiếp

Cả 2 thay đổi dưới đây dùng đúng contract chung của toàn hệ thống:

- **Envelope**: mọi response đều `{code, result, message}` (lỗi có thêm `errors[]`).
- **Header**: `Authorization: Bearer <accessToken>` như mọi endpoint có bảo vệ khác.
- **Lỗi validate**: `400 VALIDATION_ERROR` kèm `errors[{field, message}]`.
- Cả hai thay đổi đều **additive trên wire** — không endpoint nào cũ bị đổi request/response theo
  hướng phá vỡ tương thích ngược.

---

## 1. `SalesOrderResponse` giờ có field `version`

### 1.1 Vấn đề đã đóng

`PATCH /sales-orders/{id}` đòi body có `expectedVersion` (optimistic locking) từ trước, nhưng
**không** endpoint nào trả `version` lại cho FE — không có cách hợp lệ để biết giá trị phải gửi.

Từ nay **mọi** response Sales Order — `POST /sales-orders` (create), `GET /sales-orders/{id}`,
`GET /sales-orders` (mỗi phần tử trong `content[]`), `PATCH /sales-orders/{id}` (update),
`POST /sales-orders/{id}/confirm`, `POST /sales-orders/{id}/cancel` — đều có field `version`
(số nguyên, bắt đầu từ `0`, tăng dần mỗi lần đơn bị sửa qua bất kỳ action nào ở trên).

```jsonc
{
  "code": "SUCCESS",
  "result": {
    "salesOrderId": "b3382f23-...",
    "orderNo": "SO-001",
    "status": "DRAFT",
    "...": "...",
    "version": 0
  },
  "message": "OK"
}
```

### 1.2 Luồng tích hợp đúng

1. Lấy `version` từ response gần nhất (bất kỳ action nào ở trên, kể cả `GET`).
2. Gửi **nguyên giá trị đó** làm `expectedVersion` trên `PATCH /sales-orders/{id}` tiếp theo.
3. Response của `PATCH` trả `version` **mới** (đã tăng 1 so với `expectedVersion` vừa gửi) — dùng nó
   cho lần `PATCH` kế tiếp.
4. Nếu `expectedVersion` gửi lên **không khớp** `version` hiện tại của đơn (đơn đã bị sửa bởi request
   khác ở giữa) → `409 CONCURRENT_MODIFICATION`. Đây là tín hiệu để FE báo "đơn vừa bị người khác cập
   nhật, tải lại trước khi sửa tiếp", không phải lỗi cần retry im lặng.

```
PATCH /sales-orders/{id}  {"expectedVersion": 0, "customerName": "A"}
  → 200, result.version = 1

PATCH /sales-orders/{id}  {"expectedVersion": 1, "customerName": "B"}
  → 200, result.version = 2

PATCH /sales-orders/{id}  {"expectedVersion": 1, "customerName": "C"}  ← version cũ, đơn đã ở version 2
  → 409 CONCURRENT_MODIFICATION
```

🔴 **Đừng tự tăng `version` ở phía client để đoán giá trị tiếp theo.** Luôn dùng con số backend vừa
trả — action nào cũng có thể đã chạy song song từ tab/thiết bị khác.

---

## 2. Activate API cho Plant, Warehouse, Item

### 2.1 Vấn đề đã đóng

Trước đây Plant/Warehouse/Item chỉ có `DELETE` (deactivate) — không có đường quay lại `ACTIVE` qua
API. Ba endpoint mới:

| Resource | Endpoint |
|---|---|
| Plant | `POST /api/v1/plants/{plantId}/activate` |
| Warehouse | `POST /api/v1/warehouses/{warehouseId}/activate` |
| Item | `POST /api/v1/items/{itemId}/activate` |

🔴 **`Company` KHÔNG có endpoint này** — FE chỉ hỏi về Plant/Warehouse/Item, phạm vi chỉ đúng ba
resource đó. `Company` vẫn chỉ có `DELETE /companies/{companyId}` (deactivate), không có đường
quay lại `ACTIVE` qua API.

Không body, không query param. Response là **toàn bộ resource** (không phải `{"result": null}` như
`DELETE`):

```jsonc
// POST /api/v1/warehouses/{warehouseId}/activate → 200
{
  "code": "SUCCESS",
  "result": {
    "warehouseId": "c55d6a5b-...",
    "plantId": "a734ab37-...",
    "code": "WH1",
    "name": "Warehouse 1",
    "type": "GENERAL",
    "status": "ACTIVE",
    "createdAt": "...",
    "updatedAt": "..."
  },
  "message": "OK"
}
```

### 2.2 Idempotent

Gọi `activate` trên một record **đã** `ACTIVE` không lỗi — trả `200` với `status: "ACTIVE"` y hệt,
không đổi gì. An toàn để gọi lại khi không chắc trạng thái hiện tại.

### 2.3 🔴 Bị chặn nếu "cha" đang `INACTIVE`

Đây là điểm quan trọng nhất cần UI xử lý đúng: **activate một record con khi cha của nó đang
`INACTIVE` sẽ luôn thất bại**, dù bản thân record con hợp lệ.

| Activate | Bị chặn khi | Mã lỗi |
|---|---|---|
| Warehouse | Plant của nó đang `INACTIVE` | `422 OPERATION_NOT_ALLOWED` |
| Plant | Company của nó đang `INACTIVE` | `422 OPERATION_NOT_ALLOWED` |
| Item | Company của nó đang `INACTIVE` | `422 OPERATION_NOT_ALLOWED` |

```jsonc
// POST /api/v1/warehouses/{id}/activate khi Plant cha đang INACTIVE → 422
{
  "code": "OPERATION_NOT_ALLOWED",
  "result": null,
  "message": "Cannot activate a warehouse while its plant is inactive: c55d6a5b-..."
}
```

Đây **không phải** state machine của chính resource (không phải `STATE_CONFLICT` 409) — nó là kiểm
tra tính hợp lệ dữ liệu (master data đứng dưới một cha chưa sẵn sàng), cùng logic với chiều tạo mới
(`POST /companies/{id}/plants` cũng bị chặn tương tự nếu company `INACTIVE`).

**Khuyến nghị UI:** vô hiệu hoá (disable, không chỉ ẩn) nút "Activate" của Warehouse khi Plant cha
đang hiển thị `INACTIVE` trên màn hình, và tương tự cho Plant/Item khi Company cha `INACTIVE` — thay
vì chỉ phát hiện qua lỗi `422` sau khi người dùng đã bấm. Muốn activate Warehouse thì phải activate
Plant cha trước (rồi mới activate được Warehouse); muốn activate Plant/Item thì activate Company
trước.

### 2.4 Verb activate/deactivate KHÔNG đối xứng — có chủ đích

Ba resource này vẫn giữ nguyên `DELETE /plants/{id}`, `DELETE /warehouses/{id}`,
`DELETE /items/{id}` cho chiều deactivate (không đổi, không có `POST .../deactivate` mới). Chỉ chiều
**activate** là endpoint mới (`POST .../activate`). Đây là asymmetry thật của API — nhiều nhóm
resource khác trong hệ thống (BOM, Routing, UOM, Work Center...) có verb pattern khác nhau, xem
`docs/api-guide-for-frontend.md` mục "Sales Order · Inventory · Master data" để đối chiếu đầy đủ.

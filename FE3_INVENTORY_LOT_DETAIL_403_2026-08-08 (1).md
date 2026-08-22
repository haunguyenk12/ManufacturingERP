# FE-3 Inventory Lot Detail — Permission 403 Reproduction

## Kết luận

Lỗi quyền Inventory Lot detail vẫn tái hiện trên backend ngày `2026-08-08 14:50:52 +07:00`.
Manager và Operator đều có `PERM_INVENTORY_READ`, đọc được Lot list nhưng không đọc được detail của
chính record xuất hiện trong list.

Không có mutation nào được thực hiện. File này không chứa password, access token hoặc refresh token.

## Target

```text
Backend:     http://10.60.243.54:8080
Lot ID:      86ccfeb2-e464-41fd-b27b-8170578d9ad2
Warehouse:   dc5d66e5-d179-43eb-9580-8e006bfbf79a
```

Admin đã đọc thành công cùng Lot detail trước khi chạy hai tài khoản bị ảnh hưởng.

## Manager reproduction

Profile:

```text
Username:       manager.a
/auth/me:       HTTP 200
Roles:          1
Permissions:    48
Scopes:         1
Inventory permissions:
- PERM_INVENTORY_MANAGE
- PERM_INVENTORY_MOVE
- PERM_INVENTORY_READ
Auth/me trace:  bb2ffd38eacb4539
```

Requests:

```text
GET /api/v1/inventory/lots?warehouseId=dc5d66e5-d179-43eb-9580-8e006bfbf79a&page=0&size=20
HTTP 200 SUCCESS
totalElements: 1
trace: 38ff6757aa8945d9

GET /api/v1/inventory/lots/86ccfeb2-e464-41fd-b27b-8170578d9ad2
HTTP 403 PERMISSION_DENIED
message: Insufficient permissions
trace: eff2804f1b2b4129
```

## Operator reproduction

Profile:

```text
Username:       operator.a
/auth/me:       HTTP 200
Roles:          1
Permissions:    23
Scopes:         1
Inventory permissions:
- PERM_INVENTORY_MOVE
- PERM_INVENTORY_READ
Auth/me trace:  6b5850b83bb646c3
```

Requests:

```text
GET /api/v1/inventory/lots?warehouseId=dc5d66e5-d179-43eb-9580-8e006bfbf79a&page=0&size=20
HTTP 200 SUCCESS
totalElements: 1
trace: d0ad44283ad24f4c

GET /api/v1/inventory/lots/86ccfeb2-e464-41fd-b27b-8170578d9ad2
HTTP 403 PERMISSION_DENIED
message: Insufficient permissions
trace: 67602c8f45e54784
```

## Expected behavior

Theo contract hiện tại, list và detail đều là read operations bảo vệ bằng `PERM_INVENTORY_READ`.
Người dùng nhìn thấy record trong list và có `PERM_INVENTORY_READ` phải đọc được detail của cùng
record trong cùng scope.

Backend cần kiểm tra method-security/permission expression của
`GET /inventory/lots/{lotId}` và đối chiếu scope resolution với list endpoint. Trace ưu tiên để tra log
là Manager detail `eff2804f1b2b4129`.

## Recheck sau phản hồi backend về `warehouseId` query

Backend hướng dẫn FE đổi detail thành:

```http
GET /api/v1/inventory/lots/{lotId}?warehouseId={selectedWarehouseId}
```

FE đã tái hiện đúng flow list → lấy trực tiếp `row.lotId` và `row.warehouseId` → detail trong cùng
authenticated session. Kết quả live vẫn chưa pass:

| Account | List | List trace | Detail có `warehouseId` | Detail trace |
|---|---:|---|---:|---|
| `manager.a` | 200, 1 row | `2f9ca86059f34f3a` | 403 `PERMISSION_DENIED` | `db57b7a67cfa4cda` |
| `operator.a` | 200, 1 row | `39c16651b085495a` | 403 `PERMISSION_DENIED` | `9279ad814f774572` |

IDs lấy trực tiếp từ row list:

```text
lotId:       86ccfeb2-e464-41fd-b27b-8170578d9ad2
warehouseId: dc5d66e5-d179-43eb-9580-8e006bfbf79a
```

Live OpenAPI tại thời điểm recheck vẫn chỉ công bố path parameter `lotId` cho operation `get_11` và
chưa có query parameter `warehouseId`.

Frontend đã cập nhật theo contract backend hướng dẫn và focused tests pass, nhưng defect chỉ có thể
đóng sau khi backend deploy/method-security effective và live OpenAPI được cập nhật.

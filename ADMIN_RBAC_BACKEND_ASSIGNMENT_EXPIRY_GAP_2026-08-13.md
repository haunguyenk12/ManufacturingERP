# Backend follow-up — Assignment `expiresAt` bị mất — 2026-08-13

## Kết luận

Trong RBAC-07 live acceptance, tạo Assignment thành công nhưng backend trả và lưu `expiresAt: null` dù frontend đã gửi một UTC Instant hợp lệ trong tương lai.

Lỗi này không chặn create/revoke hay hiệu lực phân quyền, nhưng làm chức năng Assignment có thời hạn chưa đúng contract đã xác nhận.

## Request đã gửi

```http
POST /api/v1/access/assignments
Content-Type: application/json
Authorization: Bearer <admin-token>

{
  "userId": "05c6e50f-4c7b-4fc5-a919-8c417b9d201c",
  "roleId": "44b346bc-90c0-47be-b97e-e8ba8e0d70dd",
  "scopeId": "757b8e31-2c46-4a18-b8de-3fcbb46a34b7",
  "expiresAt": "2026-08-20T16:59:59.000Z"
}
```

Frontend lấy ngày `2026-08-20` từ input và chuẩn hóa cuối ngày theo timezone máy người dùng thành UTC ISO-8601 như trên. Thời điểm này lớn hơn thời điểm tạo Assignment.

## Kết quả thực tế

Create trả success và tạo Assignment:

```text
assignmentId = 3de25cac-48a1-4771-8bd5-95b2e09919bc
status       = ACTIVE
```

Filtered read-back:

```http
GET /api/v1/access/assignments
  ?userId=05c6e50f-4c7b-4fc5-a919-8c417b9d201c
  &roleId=44b346bc-90c0-47be-b97e-e8ba8e0d70dd
  &scopeId=757b8e31-2c46-4a18-b8de-3fcbb46a34b7
  &page=0&size=20
```

```json
{
  "code": "SUCCESS",
  "result": {
    "totalElements": 1,
    "content": [
      {
        "assignmentId": "3de25cac-48a1-4771-8bd5-95b2e09919bc",
        "status": "ACTIVE",
        "expiresAt": null
      }
    ]
  }
}
```

UI vì vậy hiển thị `—` tại cột Expiry.

## Kết quả mong đợi

- Backend deserialize và persist `expiresAt` đã gửi.
- POST response và GET filtered read-back cùng trả canonical UTC Instant:

```json
"expiresAt": "2026-08-20T16:59:59Z"
```

- Validation giữ nguyên contract: giá trị phải lớn hơn hiện tại; thời điểm hết hạn là exclusive.

## Backend vui lòng kiểm tra

1. Request DTO/controller có bind đúng field `expiresAt` không.
2. Service/entity có gán và persist field này khi tạo Assignment không.
3. Response mapper có trả field đã lưu không.
4. Bổ sung integration test với Postgres thật: POST future Instant → GET filtered giữ nguyên Instant.

## Cleanup

- Assignment trên đã được revoke và read-back thành `INACTIVE`.
- User và custom Role hỗ trợ đã được deactivate.
- Không cần Backend chỉnh dữ liệu test.


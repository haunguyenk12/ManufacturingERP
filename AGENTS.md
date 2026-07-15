# Manufacturing ERP - Coding Agent Rules

File này là hướng dẫn bắt buộc cho mọi AI/coding agent khi làm việc trong repo này.
Trước khi sửa code, hãy đọc file này cùng `docs/architecture.md` và migration hiện có.

## 1. Project Context

- Dự án: Manufacturing ERP modular monolith.
- Stack: Java 17, Spring Boot 3.x, PostgreSQL, Redis, Flyway, JPA/Hibernate, Spring Security JWT.
- Kiến trúc hiện tại có các module chính:
  - `auth`, `user`
  - `organization` và dynamic RBAC
  - `inventory`
  - `bom`
  - `planning`
  - `workorder`
  - `reporting`
  - `common/audit`, `common/security`, `common/response`, `common/exception`
- Mục tiêu nghiệp vụ lõi:
  - Inventory accuracy
  - Multi-level BOM
  - Shortage detection
  - Work Order execution
  - Material issue / production receipt / WIP tracking / variance

## 2. Non-Negotiable Engineering Rules

- Tuân thủ SOLID, Clean Code, KISS, DRY ở mức hợp lý.
- Không nhồi toàn bộ logic vào một service lớn.
- Controller chỉ nhận/validate request, gọi service, trả `ApiResponse`.
- Business logic đặt trong service layer và có `@Transactional`.
- Không trả entity trực tiếp ra API. Luôn dùng DTO `record` và mapper.
- Không gọi repository của module khác trực tiếp nếu module đó đã có lookup/application service.
- Không sửa migration cũ. Mọi thay đổi database phải tạo Flyway migration mới.
- Không hard delete document nghiệp vụ. Dùng status/cancel/reversal khi cần.
- Không tạo abstraction nếu chưa có lợi ích rõ ràng.
- Không thay đổi API hiện có nếu không cần; nếu cần breaking change phải nêu rõ.

## 3. Module Boundary Rules

- Mỗi module sở hữu repository/entity của chính nó.
- Cross-module access ưu tiên qua service:
  - Inventory cần item/stock movement: dùng inventory service/lookup.
  - BOM cần item: dùng `ItemLookupService`.
  - Work Order cần BOM: dùng `BomLookupService`.
  - Planning cần BOM + inventory availability: dùng lookup/service tương ứng.
- `common/*` chỉ chứa cross-cutting concern thật sự: security, audit, exception, response, context.

## 4. Database & Flyway Rules

- PostgreSQL là source of truth.
- Flyway migration là cách duy nhất thay đổi schema.
- Existing migrations immutable: không sửa `V1..Vn` đã có.
- Dùng UUID primary key.
- Dùng `NUMERIC(19,6)` cho quantity.
- Các bảng mutable business entity cần audit fields:
  - `created_at`, `updated_at`, `created_by`, `updated_by`, `version`
- Ledger/document nghiệp vụ nên có:
  - `status`
  - `reference_type`
  - `reference_id`
  - `idempotency_key` với POST nhạy cảm
- Index bắt buộc cho FK, status, created_at và các query filter chính.
- Check constraint cho quantity/status/type quan trọng.

## 5. Inventory Rules

- `stock_movements` là append-only ledger.
- `stock_balances` là projection đọc nhanh.
- Không sửa lịch sử movement để sửa sai; dùng adjustment/reversal movement.
- Available stock:
  - `available = quantity - reserved_quantity` nếu có reservation.
  - Chỉ lot `AVAILABLE` được reserve/issue.
  - Lot `HOLD`, `REJECTED`, `EXPIRED` không được dùng cho production.
- POST tạo movement phải dùng `Idempotency-Key`.
- Stock update và document nghiệp vụ phải nằm trong cùng transaction.

## 6. BOM Rules

- BOM gồm `bom_headers` và `bom_lines`.
- Chỉ BOM `ACTIVE` được dùng cho planning/work order.
- Mỗi product trong cùng company/plant chỉ có một active BOM tại một thời điểm.
- BOM parent item chỉ nên là `WIP` hoặc `FINISHED_GOOD`.
- Component không được là `SERVICE`.
- `quantity_per > 0`.
- `0 <= scrap_rate < 1`.
- Phải kiểm tra circular reference trước khi activate.
- Tránh N+1 khi load BOM tree hoặc BOM explosion.

## 7. Work Order / Manufacturing Execution Rules

Luồng cần bám:

```text
Work Order
-> BOM Explosion / requirement snapshot
-> Material Reservation
-> Material Issue
-> WIP Tracking
-> Production Completion / Receipt
-> Inventory Update
-> Variance
```

- Work Order là trung tâm gom mọi phát sinh sản xuất.
- Work Order status tối thiểu:
  - `DRAFT`
  - `RELEASED`
  - `IN_PROGRESS`
  - `COMPLETED`
  - `CANCELLED`
- Chỉ `RELEASED` hoặc `IN_PROGRESS` mới được reserve/issue/receipt/WIP update.
- `COMPLETED` và `CANCELLED` không được phát sinh movement mới.
- Khi tạo Work Order, snapshot component requirements từ active BOM.
- Không issue vượt remaining requirement nếu chưa có rule approve override.
- Không receipt vượt remaining planned quantity.
- Production receipt phải tạo stock movement `RECEIVE`.
- Material issue phải tạo stock movement `ISSUE`.
- Issue/receipt line nên link tới `stock_movement_id` để trace ledger.
- WIP transaction nên append-only.

## 8. RBAC & Security Rules

- Hệ thống dùng dynamic RBAC:
  - roles
  - permissions
  - role_permissions
  - access_scopes
  - access_scope_resources
  - user_role_assignments
- Không hardcode chỉ `ADMIN/MANAGER/OPERATOR` cho nghiệp vụ mới.
- API nghiệp vụ phải check permission và scope.
- Scope có thể là company, plant, warehouse.
- Không expose stack trace hoặc lỗi nội bộ ra API response.
- Response luôn dùng `ApiResponse`.

## 9. Audit Rules

- Thao tác quan trọng phải audit:
  - create/update/deactivate master data
  - BOM create/update/activate/deactivate
  - inventory receive/issue/adjust
  - work order create/release/cancel/issue/receipt/complete
  - permission/role/scope changes
- `audit_logs` ghi hành động tổng quát.
- `audit_log_changes` ghi field-level changes khi phù hợp.
- Bulk/report/read-only operation không cần ghi field changes.

## 10. Performance & N+1 Rules

- Entity association mặc định `FetchType.LAZY`.
- Không dùng `EAGER` collection cho nghiệp vụ mới.
- List endpoint dùng DTO projection hoặc query có chủ đích.
- Detail endpoint dùng `JOIN FETCH` hoặc `@EntityGraph` có kiểm soát.
- Không join-fetch collection cùng pagination.
- Với report/availability/variance, ưu tiên aggregate query thay vì loop repository.
- BOM explosion có thể dùng batch load hoặc recursive CTE; không query từng node trong vòng lặp lớn.

## 11. API Design Rules

- Endpoint version: `/api/v1`.
- POST nhạy cảm phải có `Idempotency-Key`.
- Pagination bắt buộc cho list endpoint.
- Request DTO dùng Bean Validation.
- Response DTO không chứa entity.
- Error code dùng enum hiện có hoặc thêm enum domain rõ ràng.
- Không đưa business rule vào controller.

## 12. Testing Rules

Mọi thay đổi nghiệp vụ phải có test tương ứng:

- Unit test service rule chính.
- Integration test cho repository/query phức tạp nếu có.
- Security test cho permission/scope quan trọng.
- Test idempotency với movement/document POST.
- Test không reserve/issue vượt available.
- Test không receipt vượt planned quantity.
- Test lot HOLD/REJECTED/EXPIRED bị reject khi reserve/issue.
- Test BOM circular reference.
- Test variance planned vs actual.
- Test query count hoặc ít nhất đảm bảo không có N+1 rõ ràng ở endpoint list/detail quan trọng.

## 13. Definition of Done

Một task chỉ được xem là xong khi:

- Code compile.
- Test liên quan pass.
- Không sửa migration cũ.
- Không phá API hiện có nếu không được yêu cầu.
- Logic transaction đúng.
- Không tạo N+1 query rõ ràng.
- Có audit cho thao tác quan trọng.
- Có validation và error response rõ ràng.
- Có thể giải thích luồng nghiệp vụ bằng ngôn ngữ domain, không chỉ bằng tên class.

## 14. Current Phase Priority

Ưu tiên phase tiếp theo: `Purchasing v1`.

1. Supplier Master.
2. Item Supplier.
3. Purchase Requisition.
4. Purchase Order.
5. Goods Receipt tích hợp Inventory RECEIVE.

Không ưu tiên ngay:

- Full Sales Order.
- MPS calendar đầy đủ.
- MES integration.
- OEE.
- Quality inspection.
- Costing kế toán chi tiết.
- Routing/công đoạn sản xuất phức tạp.
- Supplier contract/pricing nâng cao.
- Invoice/payment/accounting posting.

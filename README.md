# ManufacturingERP (OmniPlant)

Backend của hệ thống ERP cho **sản xuất**: quản lý vòng đời từ nguyên liệu đến thành phẩm — tồn kho
theo lô/serial, BOM đa cấp, hoạch định vật tư (MRP), lệnh sản xuất, cấp vật tư, nhập thành phẩm, QC,
giá thành, mua hàng, bán hàng, giao hàng và hoá đơn. Đồ án Capstone.

Hướng dẫn làm việc với code (quy ước, trạng thái phase, bất biến nghiệp vụ) nằm ở [`CLAUDE.md`](CLAUDE.md).

## Công nghệ

| | |
|---|---|
| Ngôn ngữ / framework | Java 17 · Spring Boot 3 · Spring Security 6 + JWT (jjwt) |
| Dữ liệu | PostgreSQL 16 (Flyway quản lý schema) · Redis 7 (token, session, rate limit) |
| ORM | Spring Data JPA + Hibernate (`ddl-auto: validate`) |
| Tài liệu API | Springdoc OpenAPI 3 (Swagger UI) |
| Test | JUnit 5 · Mockito · MockMvc · Testcontainers |
| Vận hành | Actuator + Micrometer · Docker Compose |

## Chạy trên máy

Cần JDK 17 trở lên, Maven và Docker.

```bash
# 1. Cấu hình: sao chép rồi thay MỌI giá trị giữ chỗ.
#    Profile dev từ chối khởi động nếu JWT_SECRET, DB_PASSWORD hoặc REDIS_PASSWORD
#    ngắn hơn 16 byte hoặc còn chứa "replace-with".
cp .env.example .env

# 2. Postgres + Redis
docker compose up -d

# 3. Backend (Flyway tự dựng schema lần đầu)
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

| | |
|---|---|
| API | `http://localhost:8080/api` |
| Swagger UI | `http://localhost:8080/api/swagger-ui.html` (khi `SPRINGDOC_SWAGGER_UI_ENABLED=true`) |
| Health | `http://localhost:8081/actuator/health` (cổng quản trị riêng) |
| Hộp thư thử (tuỳ chọn) | `docker compose --profile mail up -d mailpit`, chạy backend với `SPRING_MAIL_HOST=localhost`, đọc thư ở `http://localhost:8025` |

Tài khoản quản trị đầu tiên: đặt `APP_BOOTSTRAP_ADMIN_ENABLED=true` cùng username, email, mật khẩu cho
**một** lần khởi động rồi tắt lại. Dữ liệu demo: [`docs/demo-capstone2-guide.md`](docs/demo-capstone2-guide.md).

## Test

```bash
bash scripts/mvn-summary.sh test            # unit test, chỉ in tóm tắt
bash scripts/mvn-summary.sh clean verify    # thêm integration test (*IT), cần Docker đang chạy
```

## Bố cục

```
src/main/java/com/erp/manufacturing/
├── common/     exception, response, security, audit, idempotency, web
├── config/     SecurityConfig, RedisConfig, AsyncConfig, …
└── module/     auth · user · organization · inventory · uom · bom · routing · workcenter · shift
                planning · purchasing · workorder · costing · sales · delivery · billing
                dataimport · reporting
src/main/resources/db/migration/   migration Flyway (V1 … )
scripts/        mvn-summary.sh, bộ seed demo, công cụ kiểm tra dữ liệu
docs/           tài liệu cho FE, kiến trúc, lịch sử phase (docs/phases), trao đổi FE–BE (docs/handoff)
```

## Tài liệu chính

- [`docs/api-guide-for-frontend.md`](docs/api-guide-for-frontend.md) — hợp đồng API cho FE
- [`docs/architecture.md`](docs/architecture.md), [`docs/system-flow.md`](docs/system-flow.md) — kiến trúc và luồng nghiệp vụ
- [`docs/roles-and-permissions.md`](docs/roles-and-permissions.md) — vai trò và quyền
- [`docs/phases/INDEX.md`](docs/phases/INDEX.md) — bản ghi từng phase đã làm

## Định vị: ERP sản xuất khác ERP thương mại ở đâu

| Chiều | Manufacturing ERP | Commercial ERP |
|---|---|---|
| Sản xuất | BOM đa cấp, lệnh sản xuất, routing, MRP | Không có hoặc cơ bản |
| Tồn kho | WIP, theo dõi lô / serial | Kho tiêu chuẩn |
| Chi phí | Giá thành chuẩn + phân tích chênh lệch | Chỉ giá vốn |
| Hoạch định | MRP + CRP | Dự báo cầu cơ bản |
| Truy xuất nguồn gốc | Toàn chuỗi | Tuỳ chọn |

Mô hình tham chiếu: ISA-95 (tích hợp ERP ↔ MES). Bối cảnh thực tế: lắp ráp BOM nhiều cấp
(VinFast, Foxconn) và sản xuất theo quy trình có kiểm soát lô (Masan, TH True Milk).

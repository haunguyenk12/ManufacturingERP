# Configuration Refactor Plan

> Ngày lập: 2026-08-22  
> Phạm vi khảo sát: `src/main/resources/application*.yml`, `src/test/resources/application-test.yml`,
> `com.erp.manufacturing.config/**`, Security/Rate-limit filters, `pom.xml`, `.env.example`,
> `docker-compose.yml` và các config test hiện có.  
> Đây là **kế hoạch**, chưa triển khai thay đổi trong `src/main`. Mục tiêu là làm config đủ rõ và ổn
> định cho Capstone, đồng thời giữ đường nâng cấp production ngắn; không biến Capstone thành một dự
> án platform/DevOps thu nhỏ.

> **Không phải phase nghiệp vụ `P*`/`F*`/`D*`/`C2-*`.** Khi bắt đầu thực hiện, đưa đúng một hạng mục
> `CFG-n` vào trạng thái đang chạy trong `NEXT_PHASE_PLAN.md`, đo baseline thật, hoàn thành và ghi nhận
> trước khi chuyển sang hạng mục kế tiếp theo `.claude/rules/dev-workflow.md §6.5-§6.6`.

## 0. Kết luận và chiến lược

Config hiện tại có nền tảng tốt: tách profile, secret đi qua environment, custom properties dùng
record, production có fail-fast validator, Swagger mặc định tắt và đã có test CORS/JSON/config bảo
mật. Vì vậy **không viết lại toàn bộ config** và không đưa thêm framework quản lý configuration.

Khoảng trống chính nằm ở bốn nhóm:

1. **Config contract không khớp runtime:** `RATE_LIMIT_ENABLED` không có hiệu lực, Springdoc bị khai
   hai lần, Redis pool được cấu hình nhưng thiếu dependency nên không hoạt động, Redis healthcheck
   trong Compose bị thụt lề sai.
2. **Một số default chưa fail-safe:** Flyway `baseline-on-migrate=true`; runtime rate-limit override
   nhận `0`/số âm; production validator chấp nhận `enabled=true` với rules rỗng.
3. **Khả năng vận hành còn cứng:** audit executor hardcode và chưa chờ drain khi shutdown; chưa có
   một test khởi động bằng profile production; chưa có CI gate cho config drift.
4. **Nền dependency đã cũ:** Spring Boot `3.2.5` không còn là dòng OSS được duy trì. Việc nâng cấp cần
   một đợt riêng, thực hiện sau khi config contract đã có test bảo vệ.

**Chiến lược:** sửa những gì đang nói sai/chạy sai trước; bổ sung validation và test kế tiếp; chỉ sau
đó mới nâng Spring Boot. Các hạ tầng tốn chi phí như Vault, Redis HA, Kubernetes, distributed config,
outbox audit và secret rotation tự động chỉ được ghi thành production runway, **không** là điều kiện
hoàn thành Capstone.

## 1. Mục tiêu

1. Một key được ghi trong `.env.example` phải có đúng một nghĩa và có bằng chứng rằng runtime bind
   đúng; không còn “config trang trí”.
2. Cấu hình nguy hiểm hoặc vô nghĩa phải fail lúc startup/ghi override, không chờ tới request đầu
   tiên mới nổ.
3. `application.yml` chứa default an toàn và dùng được chung; `dev`, `test`, `prod` chỉ ghi phần khác
   biệt thật sự của môi trường.
4. Capstone vẫn khởi động bằng quy trình hiện tại, demo scripts không bị phá và không cần thêm dịch
   vụ ngoài PostgreSQL + Redis.
5. Production profile có smoke test và fail-fast guard cho các giá trị quan trọng.
6. Scale dọc/ngang không đòi sửa business code: các tuning knob hợp lý được typed, validated và có
   default; các tối ưu chưa có bằng chứng được để ngoài phạm vi.
7. Nâng Spring Boot thành một thay đổi cô lập, review/rollback được, không trộn với refactor rate
   limiter hoặc thay đổi dependency hàng loạt.
8. Không thay đổi HTTP API, permission, schema nghiệp vụ hoặc dữ liệu demo trong track này.

## 2. Ngoài phạm vi

- Không đưa Spring Cloud Config, Consul, Vault, Kubernetes ConfigMap/Secret hoặc một config server
  mới vào Capstone.
- Không xây giao diện quản trị rate-limit/runtime config.
- Không đổi fixed-window rate limit thành sliding-window trong phần Capstone. Hành vi thật sẽ được
  tài liệu hoá đúng; thuật toán mới chỉ làm khi có yêu cầu tải/bảo mật rõ ràng.
- Không thêm Redis connection pool chỉ vì YAML đang có block pool. Lettuce shared connection là lựa
  chọn mặc định cho đến khi load test chứng minh cần pool.
- Không đổi toàn bộ giá trị `...Ms` sang `Duration` trong đợt đầu vì sẽ đổi environment contract và
  nhiều test constructor mà không giải quyết defect đang chạy. Property mới được phép dùng
  `Duration` ngay từ đầu; việc rename JWT keys được ghi ở production runway.
- Không xử lý durability của audit event bằng outbox trong plan này. Việc đó thuộc
  `AuditRefactorPlan.md`; track config chỉ xử lý executor tuning và graceful shutdown.
- Không xoá `container_name` của Compose trong Capstone: `scripts/generate-dbml-erd.js` và
  `scripts/demo-capstone2/reset-db.sh` đang gọi trực tiếp `erp-postgres`. Muốn bỏ tên cố định phải là
  một task riêng sửa scripts + docs cùng lúc.
- Không ép Redis/DB TLS bằng một boolean chung khi chưa biết topology triển khai. Production runway
  sẽ bắt buộc ghi rõ trust boundary và cơ chế TLS của môi trường đích.

## 3. Bất biến thiết kế

1. Không profile nào tự nhúng secret thật trong Git; `.env` tiếp tục bị ignore.
2. `dev` là profile duy nhất được import `optional:file:.env[.properties]`; `prod` không bao giờ đọc
   `.env` từ working directory.
3. Không đặt profile mặc định là `dev`. Môi trường phải kích hoạt profile tường minh; tránh deploy
   nhầm với dev logging/secret behavior.
4. `ddl-auto=validate`; Flyway là đường duy nhất thay đổi schema; không sửa migration cũ.
5. Production không được bật Swagger/OpenAPI, SQL/bind DEBUG/TRACE, broad actuator exposure,
   localhost CORS hoặc broad trusted-proxy range.
6. Rate-limit `enabled=true` phải có ít nhất một BLOCK rule hợp lệ; rule ID là duy nhất toàn cục.
7. Runtime override sai không được làm request trả 500. Policy mặc định: bỏ override sai, dùng static
   YAML value đã validate, log WARN có `ruleId`/field nhưng không log dữ liệu nhạy cảm.
8. Mọi matching rule được áp dụng: global và endpoint-specific cùng đếm; thứ tự chỉ quyết định rule
   BLOCK nào được trả về trước khi nhiều rule cùng vượt ngưỡng.
9. Graceful shutdown không nhận request mới nhưng cho request đang chạy và audit queue một khoảng
   hữu hạn để kết thúc; hết timeout thì shutdown vẫn phải hoàn tất.
10. Không thêm config key nếu không có consumer, test binding và entry trong `.env.example` hoặc tài
    liệu production tương ứng.

## 4. Kiến trúc config đích

### 4.1 Phân lớp nguồn cấu hình

```text
application.yml
  ├─ default chung, an toàn, không secret
  ├─ placeholder bắt buộc cho DB/Redis/JWT/CORS/proxy
  └─ app.* typed properties với default Capstone hợp lý

application-dev.yml
  ├─ chỉ active khi profile=dev
  ├─ import local .env
  └─ verbose logging / dev token TTL có chủ đích

application-test.yml
  ├─ dữ liệu test-only cố định, không dùng .env của developer
  └─ override external systems bằng test fixture/Testcontainers

application-prod.yml
  ├─ chỉ strict override, không secret
  ├─ Swagger/verbose SQL tắt cứng
  └─ production validator kiểm effective Environment sau mọi override

Deployment environment / secret store
  └─ nguồn cuối cho secret, endpoint, capacity và topology-specific settings
```

### 4.2 Quy ước key

- Custom application behavior nằm dưới `app.*` và bind bằng `@ConfigurationProperties`.
- Infrastructure giữ namespace chuẩn của Spring Boot (`spring.datasource`, `spring.data.redis`,
  `management`, `server`) trong YAML; alias environment ngắn như `DB_URL` chỉ tồn tại khi đã được ghi
  rõ trong `.env.example`.
- Một key chỉ khai một lần trong `.env.example`; thứ tự file không được mang ý nghĩa override.
- Secret không có default. Tuning có default phải kèm unit trong tên hoặc dùng type có unit.
- Key deprecated phải có một release window được tài liệu hoá; không duy trì hai alias vô thời hạn.

### 4.3 Validation layers

```text
Binder/type conversion
        ↓
Jakarta Validation trên @ConfigurationProperties
        ↓
Cross-field invariant trong property record/validator
        ↓
Profile-specific security validator
        ↓
Production startup smoke test
```

Mỗi layer chỉ giữ trách nhiệm của nó: không dùng `Environment` cho property đã có typed class; chỉ
dùng `Environment` trong production validator cho các Spring Boot property bên thứ ba chưa có wrapper
riêng hoặc để kiểm tra effective value sau override.

## 5. Danh sách hạng mục

| ID | Hạng mục | Ưu tiên | Capstone gate | Production runway | Breaking HTTP |
|---|---|---:|---|---|---|
| CFG-0 | Baseline + characterization tests | P0 | **Có** | Có | Không |
| CFG-1 | Sửa config drift trong YAML/`.env.example`/Compose | P0 | **Có** | Có | Không |
| CFG-2 | Flyway fail-safe + production profile contract | P1 | **Có** | Có | Không |
| CFG-3 | Typed validation cho app properties | P1 | **Có** | Có | Không |
| CFG-4 | Làm đúng contract rate-limit và gom evaluator | P1 | **Có** | Có | Không |
| CFG-5 | Audit executor tuning + graceful shutdown | P2 | Nên có | Có | Không |
| CFG-6 | Config metadata + automated config gates | P2 | Nên có | **Có** | Không |
| CFG-7 | Nâng Spring Boot lên dòng đang được hỗ trợ | P1 | Sau demo rehearsal ổn định | **Có** | Không dự kiến |
| CFG-8 | Production deployment runway | Deferred | Không | Sau Capstone | Tuỳ deployment |

## 6. Kế hoạch chi tiết

### CFG-0 — Baseline và characterization (P0, không đổi hành vi)

**Mục đích:** khóa hành vi thật trước khi sửa để các phase sau không dựa vào comment hoặc giả định.

**Việc cần làm:**

1. Đo baseline `mvn -o clean verify` khi Docker hoạt động; ghi số unit/IT, failures/errors và thời gian.
2. Lưu kết quả `docker compose --env-file .env.example config --quiet`; không in expanded config vì có
   thể chứa password mẫu/secret.
3. Thêm `ConfigurationContractTest` đọc `.env.example` và các `application*.yml` để khóa:
   - không duplicate key;
   - mọi `${UPPER_CASE_KEY}` đều có entry trong `.env.example`;
   - entry đặc biệt do Compose tự dùng (`COMPOSE_PROJECT_NAME`) nằm trong allow-list tường minh;
   - không secret bắt buộc nào có default trong `application.yml`;
   - `.env` và `.env.*` tiếp tục bị ignore, trừ `.env.example`.
4. Thêm characterization test cho rate-limit xác nhận implementation hiện tại là fixed-window và
   global + specific rules đều được đếm. Test này có thể được cập nhật tên/comment ở CFG-4 nhưng
   không được xoá.
5. Ghi lại effective Redis connection factory trong một context test: pooling hiện tắt khi không có
   `commons-pool2`. Đây là bằng chứng để CFG-1 xóa config chết, không phải lý do tự động thêm pool.

**Nghiệm thu:** không đổi file production ngoài test/docs; baseline cuối không thấp hơn đầu; test mới
đỏ nếu tái tạo duplicate Springdoc hoặc thêm một env placeholder không được tài liệu hoá.

---

### CFG-1 — Sửa config drift và Compose (P0, quick wins)

**Việc cần làm:**

1. `application.yml`: đổi `app.rate-limit.enabled: true` thành
   `${RATE_LIMIT_ENABLED:true}` để key đã công bố thực sự có tác dụng. Không đổi default Capstone.
2. `.env.example`: giữ đúng một cặp `SPRINGDOC_*`; đặt chúng trong nhóm dev và ghi rõ `true` chỉ nên
   dùng với profile `dev`. Không để một giá trị `false` ở đầu rồi `true` ở cuối.
3. `.env.example`: nhóm key theo consumer (`app`, Compose/Postgres, Redis, server/management,
   security, import); không đổi tên hàng loạt trong phase này.
4. `application.yml`: xóa block `spring.data.redis.lettuce.pool.*` đang không có hiệu lực. Thêm một
   ghi chú ngắn vào tài liệu vận hành: chỉ bật pooling khi load test chứng minh cần và dependency
   `commons-pool2` được thêm có chủ đích.
5. `docker-compose.yml`:
   - chuyển `interval`, `timeout`, `retries` về đúng dưới `redis.healthcheck`;
   - bỏ top-level `version: "3.9"` đã obsolete;
   - giữ `container_name` trong Capstone vì demo/ERD scripts đang phụ thuộc;
   - không thêm app service, restart policy hay production network vào local Compose.
6. Chạy lại normalized Compose và chỉ assert shape an toàn: Redis healthcheck có đủ `test`,
   `interval`, `timeout`, `retries`; các tên đó không còn trong `environment`.

**Test:** `ConfigurationContractTest`, một script/test parse `docker compose config --format json`
chỉ đọc tên key/healthcheck shape, không snapshot secret value.

**Nghiệm thu demo:** `docker compose down -v && docker compose up -d`, Postgres và Redis healthy,
backend `dev` khởi động, smoke login + `/actuator/health` pass, demo reset/seed scripts vẫn tìm được
`erp-postgres`.

---

### CFG-2 — Flyway fail-safe và production contract (P1)

**Việc cần làm:**

1. Đổi `spring.flyway.baseline-on-migrate` về `false` trong common config (hoặc xóa dòng để dùng
   default false). Không bật lại trong `dev`: database trắng chạy V1→latest bình thường; database đã
   có `flyway_schema_history` cũng không bị ảnh hưởng.
2. Viết runbook ngắn cho trường hợp duy nhất cần baseline: DB không rỗng được đưa vào quản lý Flyway.
   Baseline phải là thao tác operator tường minh sau khi kiểm tra target DB/schema/version; không biến
   nó lại thành startup default.
3. Giữ `ddl-auto=validate`, `flyway.enabled=true`, migration location duy nhất và không sửa V1..V66.
4. Mở rộng `ProductionSecurityValidator` theo effective config:
   - DB username không blank và không phải superuser phổ biến;
   - Redis password không blank;
   - `app.bootstrap.admin.enabled=true` phải tạo cảnh báo/fail theo quyết định dưới đây;
   - rate limit không chỉ `enabled` mà phải có BLOCK rule;
   - giữ các guard Swagger/Actuator/log/CORS/trusted proxy hiện có.
5. **Quyết định cần chốt khi thực hiện:** production có cho phép admin bootstrap một lần hay cấm
   hoàn toàn? Đề xuất cho Capstone: cho phép trong `dev`; `prod` mặc định fail nếu bật, provisioning
   production dùng một runbook/job riêng sau này. Không tự đổi trước khi user chốt vì ảnh hưởng quy
   trình cấp tài khoản ban đầu.

**Test bắt buộc:**

- database trắng chạy toàn bộ migration;
- database đã có history vẫn validate/migrate bình thường;
- database không rỗng nhưng không có history phải fail, chứng minh safety net hoạt động;
- production validator đỏ với rules rỗng và các setting nguy hiểm đã liệt kê.

**Operational breaking change:** một DB legacy không có Flyway history sẽ không còn tự baseline. Đây
là thay đổi có chủ đích, cần ghi trong runbook; không phải HTTP breaking change.

---

### CFG-3 — Typed properties và validation khép kín (P1)

**Phạm vi class:** `JwtProperties`, `CorsProperties`, `SecurityProperties`,
`RateLimitProperties`, `DataImportProperties`, `AdminBootstrapProperties`; không wrapper toàn bộ
`spring.datasource`/`spring.data.redis` trong phase này.

**Việc cần làm:**

1. `JwtProperties`:
   - enforce secret tối thiểu 32 UTF-8 bytes ở mọi profile vì signing library yêu cầu vậy;
   - `access > 0`, `refresh > access`, `absolute >= refresh`;
   - placeholder/production lifetime policy tiếp tục nằm ở profile validator;
   - giữ `toString()` che secret và thêm regression test không lộ secret.
2. `CorsProperties`:
   - list không rỗng và không chứa blank;
   - origin là URI `http/https` hợp lệ, không path/query/fragment;
   - wildcard vẫn bị từ chối khi credentials bật;
   - `maxAgeSeconds >= 0`;
   - production tiếp tục từ chối loopback origin.
3. `SecurityProperties`: list không rỗng, không blank; CIDR/IP syntax vẫn được parse một lần lúc
   startup bởi `IpExtractor`; thêm test binding invalid CIDR thay vì chỉ unit-test parser.
4. `RateLimitProperties`:
   - `rules` không null; khi enabled phải `@NotEmpty`;
   - ID không blank, match pattern ổn định (đề xuất `[a-z0-9][a-z0-9-]*`) và duy nhất toàn cục;
   - pattern bắt đầu bằng `/`; limit/window positive; action/scope non-null;
   - production có ít nhất một `BLOCK` rule; chỉ `LOG_ONLY` không được xem là protection.
5. `AdminBootstrapProperties`: validation có điều kiện khi enabled thay cho việc để lỗi tới
   `ApplicationRunner`; password tiếp tục bị che trong `toString()`.
6. `DataImportProperties`: giữ `[1, 20_000]` và default 5.000; thêm binding test cho missing/0/over
   max để làm rõ khác biệt giữa “missing dùng default” và “explicit 0 là invalid”. Không silently sửa
   explicit `0` thành 5.000 nếu binder có thể phân biệt bằng nullable/default ở YAML.
7. Giảm dùng `Environment` cho `app.*` sau khi typed properties đã đủ; không refactor Spring Boot
   infrastructure property chỉ để đạt “100% typed”.

**Test:** dùng `ApplicationContextRunner`/binder test cho valid, missing, boundary và cross-field
cases. Mỗi property class ít nhất có một happy-path và một startup-failure test; secret không xuất
hiện trong assertion failure/toString.

---

### CFG-4 — Rate-limit contract, correctness và nền scale (P1)

#### CFG-4A — Phần bắt buộc cho Capstone

1. Đổi mô tả `sliding-window` thành `fixed-window` trong YAML/docs/Javadoc vì đó là thuật toán đang
   chạy. Ghi rõ boundary burst là giới hạn được chấp nhận cho Capstone; không quảng bá sai.
2. Tách phần trùng giữa `RateLimitFilter` và `UserRateLimitFilter` vào một evaluator/service dùng
   chung. Hai filter chỉ còn trách nhiệm lấy identifier, gọi evaluator và ghi HTTP response.
3. Runtime override:
   - parse `limit`/`windowSeconds`;
   - chỉ nhận số dương trong một upper bound hợp lý;
   - invalid/missing dùng static rule value và log WARN rate-limited;
   - không bao giờ chia zero hoặc tạo TTL âm;
   - không thay đổi static `RateLimitProperties` object.
4. Giữ semantics “mọi matching rule cùng áp dụng”; đặt specific rule trước global chỉ để response
   ưu tiên rule cụ thể khi cùng block, không dùng order để bỏ qua global counter.
5. Loại bỏ duplicated result record/helper nếu evaluator chung đã sở hữu chúng; không tạo interface
   khi chỉ có một implementation.
6. Xử lý Redis exception theo policy của `ExceptionHandlerRefactorPlan.md` để HTTP envelope không
   vỡ. **Không tự quyết fail-open/fail-closed trong config plan** nếu EH plan chưa chốt; security
   endpoint và business endpoint có thể cần policy khác nhau.

**Test bắt buộc:** global + specific cùng tăng; USER rule chỉ chạy sau auth; LOG_ONLY không block;
override valid có hiệu lực; `0`, âm, overflow, non-number không 500; boundary fixed-window; headers và
error code không đổi.

#### CFG-4B — Tối ưu production, chỉ làm sau profiling

Hiện mỗi matching rule tốn nhiều Redis round-trip (`HGET` override ×2 + `INCR` + có thể `EXPIRE`).
Sau khi CFG-4A gom evaluator, đo bằng load test trước khi chọn một trong hai:

- **Phương án A:** Lua script một round-trip/rule, atomically lấy override hợp lệ + increment + đặt
  expiry lần đầu. Repo đã có `DefaultRedisScript` trong `TokenStoreService`, không đưa công nghệ mới.
- **Phương án B:** cache override ngắn hạn trong app + Lua/atomic counter, chấp nhận propagation delay
  đã tài liệu hoá.

Không pipeline/Lua toàn bộ rules trong Capstone nếu chưa có test concurrency và số đo latency. DoD
production cho CFG-4B: load test ghi p50/p95/p99, Redis ops/request và error rate trước/sau; không chỉ
khẳng định “ít round-trip hơn” bằng đọc code.

---

### CFG-5 — Audit executor và graceful shutdown (P2)

**Việc cần làm:**

1. Thêm `AuditExecutorProperties` dưới `app.async.audit` với default giữ hành vi hiện tại:
   `corePoolSize=2`, `maxPoolSize=5`, `queueCapacity=500`; validation `core <= max`, mọi size dương.
2. Property mới dùng type phù hợp (`Duration` cho await termination), không dùng số millisecond trần.
3. Cấu hình `ThreadPoolTaskExecutor`:
   - `waitForTasksToCompleteOnShutdown=true`;
   - await termination có timeout hữu hạn;
   - thread prefix và `MdcTaskDecorator` giữ nguyên;
   - `CallerRunsPolicy` giữ cho Capstone, nhưng tài liệu hoá nó có thể tăng request latency khi queue
     đầy chứ không phải durability guarantee.
4. Bật graceful server shutdown và lifecycle timeout tương thích với executor drain time.
5. Thêm test property binding + executor configuration; test shutdown ở mức integration nếu ổn định,
   không dùng sleep dài/flaky.
6. Không tuyên bố “không mất audit” sau phase này. Queue vẫn in-memory; durability/retry/outbox thuộc
   `AuditRefactorPlan.md`.

**Nghiệm thu:** request demo không đổi; app shutdown trong timeout; task đã nhận trước shutdown được
drain trong test; invalid pool sizes fail startup.

---

### CFG-6 — Metadata, test matrix và CI gates (P2)

**Việc cần làm:**

1. Thêm `spring-boot-configuration-processor` vào `maven-compiler-plugin.annotationProcessorPaths`
   đang liệt kê tường minh Lombok/MapStruct; chỉ thêm dependency thường là chưa đủ khi processor path
   đã bị giới hạn.
2. Verify `target/classes/META-INF/spring-configuration-metadata.json` chứa toàn bộ `app.*` properties
   và mô tả/default hợp lý; không chứa secret value.
3. Tạo test matrix:

| Test | Mục tiêu | Chạy ở đâu |
|---|---|---|
| `ConfigurationContractTest` | `.env.example`/placeholder không drift | unit |
| `*PropertiesBindingTest` | type/boundary/cross-field validation | unit |
| `ProductionSecurityValidatorTest` | unsafe prod effective value fail | unit |
| `ProdProfileStartupIT` | context prod + PostgreSQL + Redis thật khởi động | Failsafe/Testcontainers |
| `FlywayMigrationIT` bổ sung | empty/history/no-history safety | Failsafe/Testcontainers |
| Compose config check | YAML normalize đúng, healthcheck shape đúng | CI/script |

4. Thêm GitHub Actions workflow tối thiểu khi user chốt dùng CI:
   - Java 17 đúng với `pom.xml`;
   - `mvn -B -ntp test` trên PR;
   - `docker compose --env-file .env.example config --quiet`;
   - full `mvn verify` với Testcontainers là job riêng, có timeout/cache rõ ràng;
   - không ghi `.env` hoặc expanded secret ra artifact/log.
5. Thêm một tài liệu config ngắn (`docs/configuration.md`) làm source of truth cho profile, required
   key, default, secret flag, dev command, production guard và troubleshooting. `.env.example` vẫn là
   executable example, không phải nơi chứa toàn bộ giải thích kiến trúc.
6. Cập nhật `.claude/rules/dev-workflow.md §6.3`: mô tả đúng trạng thái hiện tại — profile được kích
   hoạt tường minh, test dùng PostgreSQL/Redis/Testcontainers theo code thật; không giữ ví dụ
   `APP_ENV:dev` nếu ứng dụng không implement nó.

**Nghiệm thu:** một typo/duplicate/missing required key làm CI đỏ trước deployment; prod startup IT
chứng minh validator và profile layering cùng chạy trong full context, không chỉ new validator bằng
tay trong unit test.

---

### CFG-7 — Nâng Spring Boot có kiểm soát (P1 production, làm sau guardrails)

**Target:** latest patch của dòng Spring Boot OSS còn được hỗ trợ tại thời điểm thực hiện (đề xuất
hiện tại: `3.5.x`), giữ Java 17 trong phase này. Không hardcode patch trong plan vì patch security có
thể thay đổi trước ngày triển khai.

**Trình tự:**

1. Đọc release notes/migration guide 3.2→3.3→3.4→3.5; lập bảng breaking/deprecation liên quan trực
   tiếp tới Spring Security, Jackson, JPA/Hibernate, Redis, Actuator, Flyway, test slices.
2. Chụp `mvn dependency:tree` trước; nâng **Spring Boot parent trước**, không đồng thời “latest all”
   cho JJWT/Springdoc/MapStruct/POI/Testcontainers nếu không bị compatibility bắt buộc.
3. Chạy compile + unit + full verify. So sánh generated OpenAPI/JSON wire format, Flyway migration,
   SecurityFilterChain order, CORS, Redis token flows và production validator.
4. Chỉ nâng explicit dependency không tương thích trong commit kế tiếp, có lý do và test riêng.
5. Không mass-migrate deprecated test annotation (`@MockBean`, v.v.) trong cùng commit nếu build vẫn
   đúng; tạo debt nhỏ riêng để diff nâng framework review được.
6. Chạy smoke demo sạch từ `docker compose down -v`: migrate → bootstrap theo quy trình đã chốt →
   login → seed → các bước demo chính.
7. Cập nhật version/runbook/baseline test trong docs cùng commit; không push nếu user chưa yêu cầu.

**Rollback:** revert commit nâng parent/dependency; không có migration mới trong CFG-7 nên rollback
application không phụ thuộc rollback DB.

---

### CFG-8 — Production deployment runway (Deferred, không phải Capstone gate)

Đây là checklist để khi có môi trường production thật không phải thiết kế lại config layer. Chỉ đưa
từng mục vào phase khi topology/nhà cung cấp đã được chốt.

1. **Secret delivery:** secret manager/workload identity hoặc CI/CD secret injection; rotation runbook;
   không mount `.env` lâu dài trên server.
2. **Network/TLS:** DB `sslmode`/certificate policy, Redis TLS/ACL, trusted proxy chính xác, management
   port không public; quyết định dựa trên topology thật.
3. **Least privilege:** app DB role không superuser/owner; migration role tách app runtime role nếu
   pipeline cho phép; Redis ACL chỉ cho command/keyspace cần thiết.
4. **Isolation:** mỗi environment có DB/Redis namespace riêng; nếu buộc dùng chung Redis thì thêm
   environment/application key prefix trước khi go-live.
5. **Container/deployment:** Dockerfile/image pin digest, non-root user, readiness/liveness, resource
   requests/limits, graceful termination budget và rollback strategy.
6. **Observability:** Hikari/Redis/executor/rate-limit metrics, alert cho config startup failure,
   rate-limit override invalid, audit rejection và dependency health.
7. **Change management:** config schema/version, owner/approval cho production override, audit trail
   cho runtime rate-limit changes, staging rehearsal và rollback manifest.
8. **Audit durability:** thực hiện các phase phù hợp trong `AuditRefactorPlan.md`; executor drain của
   CFG-5 không thay thế transactional outbox.

## 7. Thứ tự thực hiện đề xuất

```text
CFG-0  baseline/characterization
  ↓
CFG-1  config drift + Compose quick wins
  ↓
CFG-2  Flyway/prod fail-safe
  ↓
CFG-3  typed validation
  ↓
CFG-4A rate-limit contract/correctness
  ↓
CFG-5  executor/graceful shutdown ─┐
CFG-6  metadata/test/CI            ├─ có thể làm độc lập sau CFG-3
                                  ┘
  ↓
Capstone rehearsal + freeze
  ↓
CFG-7  Spring Boot upgrade (riêng một đợt)
  ↓
CFG-4B + CFG-8 chỉ theo số đo/topology production thật
```

Không gộp CFG-1..CFG-7 vào một PR. Đề xuất commit boundaries:

- `test(config): CFG-0 — lock configuration contracts`
- `fix(config): CFG-1 — remove dead and conflicting local configuration`
- `fix(config): CFG-2 — restore Flyway and production startup safety`
- `refactor(config): CFG-3 — validate typed application properties`
- `fix(security): CFG-4A — align and harden fixed-window rate limiting`
- `chore(config): CFG-5 — make audit executor shutdown-aware`
- `ci(config): CFG-6 — validate configuration metadata and profiles`
- `chore(deps): CFG-7 — upgrade Spring Boot to supported 3.5.x`

## 8. Test và nghiệm thu tổng

### 8.1 Gate cho từng CFG phase

- Đo baseline trước phase; compile/test liên quan xanh; không xoá/nới assertion để làm build xanh.
- `mvn -o clean verify` xanh khi phase không cần dependency mới; phase thêm/nâng dependency chạy online
  một lần để resolve rồi phải verify lại đầy đủ.
- Không sửa migration cũ; CFG-2 chỉ thay Flyway runtime config/test.
- `git diff` chỉ chứa file thuộc hạng mục đang chạy; không gom `AuditRefactorPlan` hoặc
  `ExceptionHandlerRefactorPlan` vào commit config.
- Docs/config example cập nhật cùng code; không để key mới chỉ tồn tại trong Java.
- Không log/in expanded secret trong test output, CI artifact hoặc plan completion report.

### 8.2 Gate Capstone

1. Local setup từ clone sạch + `.env.example` đã thay secret chạy được theo một command sequence được
   tài liệu hoá.
2. `docker compose` báo Postgres/Redis healthy; reset/seed/verify scripts hiện có không bị phá.
3. Backend dev khởi động; Swagger chỉ bật khi dev explicitly chọn; prod validator không chạy nhầm.
4. Login/refresh/logout, CORS preflight, rate-limit headers, import upload và actuator health smoke
   pass.
5. Demo dataset Capstone 2 seed + verify pass như baseline hiện tại.
6. Không có HTTP wire breaking change; nếu một message/config startup error đổi thì ghi rõ trong
   completion note.

### 8.3 Gate “production-ready base”

1. `ProdProfileStartupIT` pass với PostgreSQL/Redis thật và migration latest.
2. Unsafe production matrix fail startup đúng lý do, không fail bằng NPE/connection error mơ hồ.
3. Config metadata sinh đầy đủ; `.env.example` contract test và Compose normalization pass CI.
4. Spring Boot nằm trên dòng được hỗ trợ và full verify/demo rehearsal pass sau upgrade.
5. Runbook có: required config, baseline DB procedure, admin provisioning decision, secret/TLS trust
   boundary, health/readiness và rollback.
6. Các mục deferred được ghi đúng là deferred; không quảng bá Capstone executor/rate limiter/audit là
   HA/durable khi chưa có bằng chứng.

## 9. Rủi ro và rollback

| Hạng mục | Rủi ro | Giảm thiểu / rollback |
|---|---|---|
| CFG-1 rate-limit flag | Môi trường từng đặt `RATE_LIMIT_ENABLED=false` nhưng trước đây bị bỏ qua; sau fix hành vi thật sự tắt | Default vẫn true; prod validator cấm false; ghi rõ thay đổi effective config |
| CFG-1 Redis pool | Người đọc kỳ vọng pool 8 connection dù thực tế chưa từng có | Characterization chứng minh trạng thái; xóa config chết, không đổi runtime; load test trước khi thêm pool |
| CFG-1 Compose | Sửa healthcheck làm startup script chờ khác trước | Giữ container/service names; chạy toàn bộ demo reset/seed smoke |
| CFG-2 Flyway | Legacy DB không history không tự startup | Fail có chủ đích; runbook manual baseline; revert config chỉ khi đã xác minh target DB |
| CFG-3 validation | Local `.env` cũ bị startup failure | Error nêu đúng key/rule; cập nhật `.env.example`; không auto-coerce giá trị nguy hiểm |
| CFG-4 evaluator | Đổi thứ tự/counter semantics ngoài ý muốn | Characterization test global+specific trước refactor; giữ response headers/code |
| CFG-5 shutdown | Await quá dài làm deploy treo hoặc CallerRuns tăng latency | Timeout hữu hạn; defaults hiện tại; executor metrics/load test ở production runway |
| CFG-6 prod IT | Testcontainers làm CI chậm/flaky | Tách unit và verify job, timeout rõ, không bỏ test mà điều tra infra failure |
| CFG-7 Boot upgrade | Behavior drift xuyên framework | Làm sau guardrails, một commit parent trước, full verify + demo rehearsal, không migration |

## 10. Quyết định cần chốt khi bắt đầu thực hiện

Chỉ có ba quyết định cần user xác nhận; các phần còn lại có default đề xuất rõ để không chặn tiến độ:

1. **Admin bootstrap trên prod:** cấm hoàn toàn (đề xuất) hay cho phép một lần bằng runbook?
2. **CI:** thêm GitHub Actions ngay trong CFG-6 hay chỉ cung cấp local Maven/Compose gate vì repository
   chưa dùng CI?
3. **Thời điểm CFG-7:** nâng Spring Boot trước buổi demo cuối hay freeze Capstone trước, nâng ngay sau
   demo rehearsal? Đề xuất: hoàn thành CFG-0..CFG-6, rehearsal/freeze, rồi CFG-7 trong đợt riêng.

Không cần chốt trước về Vault/Kubernetes/managed Redis/TLS provider; đó là quyết định deployment của
CFG-8 khi production topology tồn tại.

## 11. Definition of Done của toàn track

- CFG-0..CFG-6 hoàn thành, mỗi hạng mục có baseline/test/docs/commit riêng và trạng thái được cập nhật
  theo workflow dự án.
- Không còn duplicate/unconsumed key trong `.env.example`; Redis healthcheck normalize đúng; không có
  config pool chết.
- Flyway không auto-baseline; production unsafe settings fail startup; property validation phủ
  boundary/cross-field quan trọng.
- Rate-limit được gọi đúng là fixed-window, invalid override không gây 500, behavior global + specific
  có test.
- Audit executor typed/tunable và shutdown-aware, nhưng tài liệu không tuyên bố durability vượt quá
  implementation.
- Config metadata và production startup test tồn tại; CI decision được ghi rõ dù chọn triển khai hay
  hoãn.
- CFG-7 hoàn thành trên một Spring Boot line được hỗ trợ trước khi go-live production; nếu Capstone
  freeze trước CFG-7 thì trạng thái phải ghi rõ “Capstone-ready, chưa production-ready”.
- `mvn clean verify`, Compose health, dev smoke, prod startup IT và demo seed/verify đều xanh; số test
  cuối không thấp hơn baseline, failures/errors bằng 0.
- Không có HTTP API breaking change, không migration mới, không secret bị commit hoặc xuất hiện trong
  log/artifact.

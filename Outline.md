# Outline Thuyết Trình — Backend (OmniPlant Manufacturing ERP, Capstone 2/3)

> Phạm vi: outline này **chỉ dành cho phần của Backend Developer** (Nguyễn Phúc Hậu) trong buổi
> pitching trước hội đồng. Phần Frontend, demo UI, hay phần mở đầu chung của cả nhóm — nếu có —
> không lặp lại ở đây. Nội dung dựa trên `Report.pdf` (Capstone 2) đối chiếu với trạng thái thật
> của code trong `CLAUDE.md` tại thời điểm 2026-08-21, nên vài con số/chi tiết kỹ thuật ở đây **mới
> hơn** report (report chốt số liệu ở mốc Capstone 2, code đã đi tiếp một số phase sau đó).
>
> Nguyên tắc chọn nội dung: ưu tiên những gì hội đồng **hỏi xoáy** một sinh viên Backend — kiến trúc,
> lý do quyết định kỹ thuật, xử lý logic phức tạp/concurrency, và bằng chứng đã kiểm chứng (test,
> mutation testing) — hơn là liệt kê tính năng dàn trải.

---

## 0. Khung thời gian gợi ý

Giả định phần nói của bạn trong buổi pitching rơi vào khoảng **6–10 phút** (phần còn lại của buổi là
FE, demo, Q&A chung). Cấu trúc dưới đây chia theo khối, đánh dấu khối nào **bắt buộc nói**, khối nào
**dự phòng/rút gọn nếu thiếu thời gian**, và khối nào để **backup slide cho Q&A** (không nói trừ khi
được hỏi).

| Khối | Nội dung | Mức ưu tiên |
|---|---|---|
| 1 | Vai trò & framing | Bắt buộc, 20–30s |
| 2 | Kiến trúc hệ thống | Bắt buộc, 1 phút |
| 3 | Tech stack | Bắt buộc nhưng lướt nhanh, 30–45s |
| 4 | Data model tổng quan | Rút gọn nếu thiếu giờ — 1 câu + hình ERD |
| 5 | Luồng nghiệp vụ xương sống | Bắt buộc, đây là "câu chuyện" chính, ~1.5 phút |
| 6 | Logic cốt lõi (trọng tâm) | Bắt buộc, chọn 2-3 mục nói kỹ thay vì lướt cả 9 mục, ~3-4 phút |
| 7 | API/Error contract | Rút gọn — 1 câu nếu thiếu giờ |
| 8 | Testing & chất lượng | Bắt buộc, 30–45s, đây là điểm cộng lớn |
| 9 | War stories kỹ thuật | **Không nói chủ động** — để dành trả lời khi hội đồng hỏi "khó khăn gì?" |
| 10 | Giới hạn & roadmap | Bắt buộc, 20–30s |
| 11 | Q&A prep | Học thuộc, không lên slide |

---

## 1. Mở đầu — vai trò & framing (20–30s)

**Nói gì:**
- Giới thiệu ngắn: bạn là Backend Developer, chịu trách nhiệm toàn bộ tầng API, business logic,
  database schema, security, và tính đúng đắn nghiệp vụ (business correctness) của hệ thống.
- Câu framing mở đầu (dựa theo Abstract của report, diễn đạt lại bằng lời của bạn):
  > "Backend không chỉ là nơi lưu dữ liệu — nó là nơi **thực thi và bảo vệ mọi luật nghiệp vụ**:
  > không cho phép xuất kho vượt tồn, không cho phép Work Order chạy khi thiếu vật tư, không cho
  > phép hàng đang chờ QC lẫn vào hàng có thể bán. Phần trình bày của tôi tập trung vào cách backend
  > OmniPlant đảm bảo những ràng buộc đó ở mọi lớp: API, transaction, database constraint."

---

## 2. Kiến trúc hệ thống (System Architecture) — ~1 phút

**Diagram cần chuẩn bị:** sơ đồ kiến trúc tổng (tương đương Figure 1 trong report) — Next.js FE ↔
REST API `/api/v1` ↔ Spring Boot modular-monolith ↔ PostgreSQL + Redis.

**Nói gì:**
- **Modular-monolith**, không phải microservices: một backend deployable duy nhất, nhưng chia theo
  package `module/*` với ranh giới rõ (auth, organization, inventory, bom, workorder, routing,
  sales, planning, purchasing, costing, workcenter, shift, uom, common/audit, common/security).
  Lý do chọn: transaction đơn giản hơn (một `@Transactional` xuyên module không cần saga/2PC),
  deploy đơn giản hơn cho quy mô SME, vẫn giữ được ranh giới domain rõ ràng qua rule nội bộ
  "cross-module chỉ gọi qua lookup/application service của module đó, không gọi thẳng repository của
  module khác" (rule `C7`).
- **Backend là nguồn thẩm quyền cuối cùng** (authoritative source), FE chỉ là lớp trải nghiệm: mọi
  rule quan trọng (quyền theo Plant/Warehouse, điều kiện release Work Order, tồn kho khả dụng, phê
  duyệt, QC) đều được **revalidate ở backend** dù FE đã chặn trước đó — tránh trường hợp bypass FE.
- PostgreSQL là nguồn dữ liệu chính (ACID, ràng buộc toàn vẹn ở tầng DB — CHECK constraint, FK,
  UNIQUE). Redis phục vụ session/token/idempotency-lock — **không** phải nguồn dữ liệu nghiệp vụ.

---

## 3. Tech Stack — ~30–45s

**Bảng gợi ý trình chiếu** (rút gọn từ Table 3 của report):

| Lớp | Công nghệ |
|---|---|
| Ngôn ngữ / Framework | Java 17, Spring Boot 3.x, Spring Security 6 |
| Persistence | PostgreSQL + Spring Data JPA/Hibernate, Flyway migration |
| Cache/Session | Redis (JWT refresh token store, rate-limit, idempotency lock) |
| Auth | JWT (access + rotating refresh token), dynamic RBAC theo scope |
| API | REST `/api/v1`, JSON, envelope response chuẩn hoá |
| Test | JUnit 5, Mockito, MockMvc, Testcontainers (Postgres thật) |
| Build | Maven |

**Câu nói chốt (1 câu, nếu bị hỏi "vì sao chọn X"):**
- Spring Boot + JPA: hệ sinh thái enterprise trưởng thành, transaction/security/validation tích hợp
  sẵn — phù hợp một hệ ERP cần tính đúng đắn giao dịch cao hơn tốc độ phát triển thuần tuý.
- Flyway thay vì tự tay chỉnh schema: **mọi thay đổi schema đều là migration có version**, không ai
  được sửa migration cũ — đảm bảo mọi môi trường (dev/test/prod) luôn tái tạo được cùng một schema.
- Redis cho token/idempotency chứ không cho dữ liệu nghiệp vụ: tận dụng TTL tự dọn dẹp, latency thấp,
  nhưng **không** dùng Redis làm nguồn sự thật cho bất cứ thứ gì cần bền vững/audit được.

---

## 4. Data Model — tổng quan (rút gọn nếu thiếu giờ)

**Diagram cần chuẩn bị:** 1 slide tổng hợp 6 ERD (hoặc chỉ nêu tên 6 module, không cần vẽ chi tiết
nếu thời gian gấp).

**Nói gì (1-2 câu):**
- Database chia thành 6 module liên kết nhau: *System Administration & Integration*, *Product &
  Production Master Data*, *Sales & Planning*, *Purchasing*, *Inventory & Warehouse*, *Manufacturing
  Execution & Quality*.
- Nguyên tắc thiết kế: UUID cho mọi primary key, FK tường minh cho quan hệ vật lý, còn quan hệ đa
  hình (polymorphic, ví dụ `reference_id` trên `stock_movements`) được ghi nhận là **quan hệ logic**,
  không ép thành FK cứng — vì một dòng ledger có thể trỏ tới nhiều loại chứng từ nguồn khác nhau
  (Work Order, Goods Receipt, điều chỉnh thủ công...).
- Mọi entity nghiệp vụ có đủ audit trail: `created_at/by`, `updated_at/by`, và `@Version` cho
  optimistic locking.

---

## 5. Luồng nghiệp vụ xương sống (production spine) — ~1.5 phút

Đây là phần **kể chuyện** — dùng một luồng end-to-end duy nhất để người nghe hình dung toàn hệ thống,
trước khi đi sâu vào từng khối logic ở mục 6.

**Diagram cần chuẩn bị:** sequence/swimlane rút gọn (dựa theo Figure 3 của report) — 8 bước:

```
Sales Order (CONFIRMED)
   → Planning Demand (mở)
   → Planning Run (MRP: nổ BOM đa cấp, trừ tồn khả dụng, tính net requirement)
   → Supply Suggestion (MAKE / BUY)
   → Work Order (snapshot BOM+Routing, reserve vật tư, release)
   → Material Issue (xuất kho theo reservation)
   → Production Execution (ghi nhận good/scrap/rework thủ công — không phải MES)
   → Production Receipt (submit → approve → nhập kho ở trạng thái HOLD)
   → QC Disposition (HOLD → AVAILABLE hoặc REJECTED)
   → Sales Order Fulfillment (chỉ hàng AVAILABLE mới được tính vào fulfilled quantity)
```

**Câu nói chốt:**
> "Đây không phải 10 màn hình rời rạc — đó là **một chuỗi state transition có kiểm soát**: mỗi bước
> chuyển trạng thái có chủ sở hữu (role nào được làm), có permission, có validation, có tác động
> tồn kho rõ ràng, và có thể truy vết ngược lại. Một reviewer có thể đi từ một dòng Sales Order,
> lần theo Planning Run, Work Order, lô vật tư đã xuất, đến lô thành phẩm cụ thể — đó là điểm mạnh
> traceability của hệ thống."

---

## 6. PHẦN TRỌNG TÂM — Logic cốt lõi & phức tạp (~3–4 phút, chọn lọc)

> Đừng cố nói hết cả 9 mục dưới đây trong 3-4 phút. **Chọn 2–3 mục** làm nói kỹ (khuyến nghị: 6.1 MRP,
> 6.3 Inventory Availability, 6.7 Auth/RBAC — ba mục này thể hiện chiều sâu kỹ thuật rõ nhất và dễ vẽ
> hình minh hoạ), các mục còn lại nói 1 câu tóm tắt hoặc để dành cho Q&A.

### 6.1. MRP Engine — nổ BOM đa cấp + netting công thức

**Đây là logic phức tạp nhất của hệ thống — nên là điểm nhấn số 1.**

- Khi Manager chọn demand và chạy Planning Run, hệ thống:
  1. Chụp snapshot **bất biến** của demand được chọn (không đổi dù sau này Sales Order bị sửa).
  2. Nổ **BOM đa cấp** theo BOM `ACTIVE` — đệ quy xuống từng sub-assembly, có cơ chế chống vòng lặp
     (cycle guard) để BOM tự tham chiếu không làm treo hệ thống.
  3. Với mỗi requirement line, tính:
     ```
     netRequirement = max(0, grossRequirement + stockTarget − projectedAvailable)
     ```
     trong đó `stockTarget = max(safetyStock, reorderPoint)`, và `projectedAvailable` gồm tồn khả
     dụng hiện tại **cộng** cung đã lên kế hoạch (Work Order đang mở + Purchase Order đang mở) —
     **trừ** phần đã bị "ăn" bởi các requirement line khác cùng item trong cùng lượt chạy (tránh
     đếm trùng một nguồn cung cho nhiều nhu cầu).
  4. Nếu thiếu hụt → sinh **Supply Suggestion** (MAKE hoặc BUY) kèm `exceptionState`
     (`READY`/`WARNING`/`BLOCKED`) — ví dụ một item có thể sản xuất được nhưng thiếu BOM/Routing
     `ACTIVE` vẫn sinh ra đề xuất, chỉ là ở trạng thái `BLOCKED`, để không "giấu" vấn đề khỏi người
     lập kế hoạch.
- **Vì sao Planning Run phải "immutable" (bất biến)?** Vì đây là bằng chứng ra quyết định tại một
  thời điểm — nếu cho phép chạy lại/sửa đè, không ai audit lại được "vì sao lúc đó hệ thống đề xuất
  mua X". Mỗi lần chạy lại = một run mới, độc lập.

### 6.2. Work Order Lifecycle & Reservation

- State machine: `DRAFT → PLANNED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED`, với hai nhánh phụ
  `BLOCKED` (thiếu vật tư khi release) và `CANCELLED`.
- **Gate quan trọng nhất:** Work Order chỉ được `RELEASED` khi **100% component đã được reserve**.
  Reservation dùng chiến lược **FEFO** (First-Expired-First-Out) khi chọn lô — ưu tiên lô sắp hết hạn
  trước để giảm hao hụt.
- Nếu reserve không đủ → hệ thống **không throw lỗi rồi biến mất** — nó **persist** Work Order ở
  trạng thái `BLOCKED` (đây từng là một bug thật: transaction ném exception rồi rollback luôn cả việc
  ghi trạng thái `BLOCKED`, khiến planner không có cách nào biết WO đang kẹt ở đâu — đã sửa bằng cách
  tách việc ghi nhận `BLOCKED` ra một transaction `REQUIRES_NEW` riêng).

### 6.3. Inventory Ledger & Availability Semantics

**Điểm nhấn kỹ thuật quan trọng thứ hai.**

- Thiết kế 2 tầng: `stock_movements` là **ledger append-only** (không bao giờ UPDATE/DELETE một dòng
  đã ghi — chỉ ghi thêm dòng đối ứng khi cần đảo ngược), `stock_balances` là **projection** tối ưu
  cho truy vấn nhanh (on-hand / reserved / quality-hold / available).
- Công thức tồn khả dụng **không đơn giản là on-hand**:
  ```
  available = onHand − reserved − qualityHold   (và loại trừ lot HOLD/REJECTED/EXPIRED)
  ```
  Nguyên tắc này áp dụng **nhất quán** ở mọi nơi đọc tồn kho: Dashboard, MRP netting, màn hình
  reservation, và điều kiện fulfillment của Sales Order — vì một điểm không nhất quán duy nhất
  (ví dụ MRP loại trừ lot HOLD nhưng dashboard thì không) sẽ tạo ra **hai con số khác nhau cho cùng
  một câu hỏi "còn bao nhiêu hàng dùng được"**, điều tệ nhất có thể xảy ra trong một hệ thống ERP.
- Lot/Serial tracking: `lotTracked` và `serialTracked` loại trừ lẫn nhau trên một Item; serial luôn
  mang `quantity = 1` (một serial = một đơn vị vật lý), lot mang số lượng tuỳ ý.

### 6.4. Immutable Snapshot Pattern (BOM/Routing → Work Order)

- Khi một Work Order được tạo, nó **chụp ảnh** (snapshot) nội dung BOM và Routing đang `ACTIVE` tại
  thời điểm đó — số lượng component, thứ tự operation, Work Center — thành dữ liệu cố định trên
  chính Work Order.
- **Vì sao cần?** Vì kỹ thuật (engineering master data) có thể thay đổi sau đó (sửa BOM, đổi Routing)
  — nếu Work Order đọc trực tiếp từ master data sống, một Work Order đã chạy được nửa chừng có thể
  đột nhiên yêu cầu vật tư khác. Snapshot giữ cho **chứng từ lịch sử luôn giải thích được**, dù master
  data có đổi bao nhiêu lần sau đó.

### 6.5. Quality Gate & Sales Order Fulfillment Loop

- Production Receipt đi qua **3 bước tách bạch**: `DRAFT → PENDING_APPROVAL → APPROVED` — chỉ bước
  `APPROVED` mới thật sự ghi nhận movement vào kho, và hàng vào kho ở trạng thái **HOLD**, chưa dùng
  được ngay.
- **QC disposition** là quyết định tách biệt hoàn toàn khỏi việc approve receipt — một Manager khác
  (hoặc cùng Manager nhưng một hành động khác, có phân quyền riêng) phán quyết `AVAILABLE` hay
  `REJECTED`. Đây là **separation of duties**: người approve nhận hàng không tự động là người xác
  nhận chất lượng.
- Chỉ khi lot chuyển `AVAILABLE`, số lượng đó mới được cộng vào `fulfilledQuantity` của Sales Order
  line tương ứng (qua bảng allocation nối Work Order ↔ Sales Order line) — khép kín vòng
  demand → supply → fulfillment.

### 6.6. Idempotency & Retry-Safety

- Mọi API ghi tồn kho nhạy cảm (nhận hàng, xuất hàng, chạy MRP, tạo Production Receipt...) nhận
  header `Idempotency-Key` tuỳ chọn.
- **Thiết kế then chốt:** key được lưu **ngay trên chính dòng chứng từ** (cột + `UNIQUE constraint`
  ở tầng database), **không phải** ở Redis với TTL — vì một chứng từ nghiệp vụ phải chống trùng lặp
  **vĩnh viễn**, không phải chỉ trong vài giờ.
- Gửi lại đúng key + đúng payload → trả lại chính chứng từ cũ (an toàn khi client retry do mất mạng).
  Gửi lại đúng key nhưng **khác** payload → từ chối rõ ràng bằng `409 IDEMPOTENCY_CONFLICT`, thay vì
  âm thầm ghi đè hoặc tạo nhầm bản ghi thứ hai.

### 6.7. AuthN/AuthZ — JWT Rotation, Reuse Detection, Absolute Timeout, Scoped RBAC

- **JWT stateless** cho access token, **refresh token rotation** lưu ở Redis: mỗi lần refresh sinh
  cặp token mới, thu hồi cặp cũ.
- **Refresh Token Reuse Detection (RTR):** nếu một refresh token *đã bị rotate away* mà vẫn có ai đó
  dùng lại — đó là dấu hiệu token bị đánh cắp — hệ thống **buộc đăng xuất toàn bộ thiết bị** của user
  đó ngay lập tức, không chỉ báo lỗi hết hạn thông thường.
- **Absolute session timeout:** dù user hoạt động liên tục (refresh token là sliding TTL), phiên vẫn
  bị buộc kết thúc sau 30 ngày kể từ lúc **đăng nhập** — chống trường hợp một phiên sống vĩnh viễn chỉ
  vì user không bao giờ ngừng dùng.
- **Dynamic RBAC có phạm vi (scope):** một permission không chỉ là "có" hay "không" — nó còn bị giới
  hạn theo **Company/Plant/Warehouse**. Một Manager có quyền quản lý Work Order nhưng chỉ trong đúng
  Plant được gán — request ngoài phạm vi đó bị từ chối dù permission tổng quát vẫn có.

### 6.8. Concurrency Control

- Optimistic locking (`@Version`) trên mọi entity có thể bị sửa đồng thời — hai request PATCH cùng
  một chứng từ với `expectedVersion` cũ sẽ có đúng một request thắng, request kia nhận
  `409 CONCURRENT_MODIFICATION`.
- Xử lý race điều kiện thực tế: hai request refresh-token đến gần như đồng thời (double-submit do
  client retry mạng chậm) được **giảm** bằng một khoá tư vấn ngắn hạn trên Redis (`SET NX PX`, TTL
  2s) + sleep ngắn cho request thua + rotation atomic qua **một** Lua script (lưu cặp mới → đánh dấu
  cặp cũ "used" → xoá cặp cũ, không thể bị chen ngang). Đây là cơ chế **thu hẹp cửa sổ race**, không
  phải loại bỏ hoàn toàn — chi tiết + một gap tự phát hiện giữa tài liệu nội bộ và code thật (primitive
  "trả lại cặp token cho request thua" đã viết nhưng chưa được gọi trong luồng chính): xem
  `Backend-DeepDive.md` mục 2.3.

### 6.9. (Dự phòng) Capacity Board & Costing Engine

- **Capacity Board (CRP tĩnh):** tổng hợp tải sản xuất theo Work Center/ngày dựa trên lịch làm việc
  (Shift/Work Calendar), so với năng lực khả dụng, cảnh báo quá tải — nhưng **không tự động dời lịch**
  của Work Order khác, chỉ cảnh báo cho Manager tự quyết định (infinite-capacity scheduling có kiểm
  soát, không phải finite-capacity optimization).
- **Costing Engine:** tính giá thành chuẩn (standard cost) bằng roll-up đệ quy qua BOM đa cấp
  (material + labor + overhead), và biến động thực tế (variance) so giữa số liệu kế hoạch và số liệu
  thực tế issue/report — dùng chung dữ liệu số lượng/thời gian đã có sẵn từ luồng thực thi.

---

## 7. API Contract & Error Handling (rút gọn nếu thiếu giờ — 1 câu)

- Mọi response, thành công lẫn lỗi, dùng **chung một envelope**: `{ code, result, message, errors[] }`
  — frontend chỉ cần xử lý một hình dạng response duy nhất.
- `ErrorCode` là enum tường minh (không phải mã lỗi tuỳ tiện) — client code theo `code`, không parse
  chuỗi message. Phân biệt rõ hai loại lỗi hay bị nhầm: lỗi **validate dữ liệu đầu vào** (422) và lỗi
  **sai trạng thái chứng từ** (409 `STATE_CONFLICT`).
- `GlobalExceptionHandler` tập trung xử lý toàn bộ exception — kể cả những lỗi tưởng như "vô hại"
  (thiếu query param bắt buộc, gọi nhầm URL không tồn tại) đều được ánh xạ về đúng mã lỗi 4xx thay vì
  rơi xuống catch-all 500 — tránh alert giả "server sập" khi thực ra chỉ là client gọi sai.

---

## 8. Chất lượng & kiểm thử (Testing Strategy) — ~30–45s

**Đây là điểm cộng dễ ghi điểm với hội đồng — nói ngắn nhưng chắc, có số liệu cụ thể.**

- Test pyramid: Unit test (mock repository) → `@WebMvcTest`/`@DataJpaTest` có mục tiêu → Integration
  test (`Testcontainers`, PostgreSQL thật) → đúng **một** test end-to-end xuyên toàn bộ luồng nghiệp
  vụ (Sales Order → QC → fulfillment) chạy qua HTTP thật.
- Quy mô hiện tại (tính đến thời điểm chuẩn bị outline này): hơn **1.000 unit test case** và **hơn
  120 integration test case** trên **18 class integration test** chạy Postgres thật qua
  Testcontainers, `failures = 0`.
- Không chỉ "viết test cho xanh": áp dụng **mutation testing thủ công** có kỷ luật ở nhiều phase —
  cố tình sửa một dòng logic production code, chạy lại test, xác nhận **đúng** test nào phải đỏ. Nếu
  test vẫn xanh sau khi logic bị phá — test đó là vô nghĩa (tautology) và bị viết lại.
- Lý do quan trọng nhất để nhấn mạnh: **một số bug nghiêm trọng chỉ có thể phát hiện qua
  integration test chạy JPQL/Hibernate thật**, không thể bằng unit test dùng mock repository (xem
  mục 9 — có ví dụ cụ thể để trả lời khi hội đồng hỏi).

---

## 9. "War stories" kỹ thuật — KHÔNG chủ động nói, để dành cho Q&A

> Chỉ dùng khi hội đồng hỏi kiểu: *"khó khăn lớn nhất khi làm backend là gì?"*, *"bug nào khó nhất
> em từng gặp?"*, *"sao em tin hệ thống đúng?"*. Mỗi story nên kể theo công thức: **triệu chứng → tại
> sao khó thấy → cách phát hiện → cách sửa → bài học rút ra**. Chọn 1-2 story phù hợp nhất với câu hỏi,
> đừng kể hết.

**Story A — Lỗi Hibernate `MultipleBagFetchException` chỉ lộ ra khi seed dữ liệu demo thật:**
Một truy vấn join-fetch cùng lúc hai quan hệ dạng danh sách (`weeklyShifts` và
`Shift.breaks`, cách nhau một association) khiến Hibernate ném lỗi ngay khi build câu query — nhưng
**toàn bộ hơn 1.000 unit test vẫn xanh**, vì unit test dùng mock repository, không bao giờ thực sự
build câu query đó. Lỗi chỉ lộ ra khi build bộ dữ liệu demo đủ phong phú (có ca làm việc kèm giờ
nghỉ, gắn vào lịch, rồi release một Work Order qua đúng lịch đó) — đúng lúc chuẩn bị demo trước hội
đồng. Bài học: **integration test phải có dữ liệu đủ "lồng nhau" (nested), không chỉ có mặt entity
gốc** — dữ liệu test "sạch quá" cũng là một dạng thiếu coverage.

**Story B — Một hệ thống, hai con số "tồn khả dụng":**
Hai đường đọc tồn kho (một dùng cho màn hình danh sách lot, một dùng cho MRP/dashboard) tính công
thức available khác nhau — một đường có lọc theo trạng thái lot (`HOLD`/`REJECTED` loại trừ), đường
kia thì không. Hậu quả: cùng một lô hàng đang `HOLD` chờ QC, màn hình lot báo "còn 2 cái dùng được"
trong khi dashboard/MRP đúng đắn báo "0". Sửa bằng cách gom logic tính available về **một nơi duy
nhất** (ở tầng mapper), đảm bảo mọi điểm đọc tồn kho đi qua cùng một công thức. Bài học: trong ERP,
**một khái niệm nghiệp vụ phải có đúng một cách tính, không được để mỗi màn hình tự suy diễn lại.**

**Story C — Refresh-token race khi client retry mạng chậm:**
Client gửi request refresh, mạng chậm không nhận được response dù server đã xử lý xong (đã rotate
token), client tự động retry với token cũ — server thấy token cũ "đã bị dùng" và hiểu nhầm thành
tấn công replay, ép đăng xuất toàn bộ thiết bị của một user hoàn toàn hợp lệ. Giải quyết bằng khoá
tư vấn ngắn hạn (Redis `SET NX`) + lưu lại "kết quả rotate gần nhất" trong vài giây để request đến
sau có thể **nhận lại đúng kết quả của request đến trước**, thay vì tự làm lại và bị chẩn đoán nhầm.
Bài học: bảo mật chặt (reuse detection) và trải nghiệm người dùng thật (mạng không hoàn hảo) đôi khi
mâu thuẫn — giải pháp đúng phải xử lý được cả hai, không chọn một bên.

---

## 10. Giới hạn hiện tại & hướng Capstone 3 (BE) — ~20–30s

- Purchasing (Supplier → PR → PO → Goods Receipt) đã có nền tảng nhưng luồng **BUY** trong MRP hiện
  dừng ở mức đề xuất, chưa tạo scheduled receipt commit được — ưu tiên số 1 của Capstone 3 để khép
  kín vòng MRP.
- Costing hiện có standard cost + material usage variance; **chưa** làm labor/overhead thực tế chi
  tiết, WIP valuation, hay Material Price Variance (giá thực trả) — vì ledger tồn kho hiện chưa lưu
  giá trên từng dòng movement.
- Capacity Board là advisory (cảnh báo), chưa phải finite-capacity scheduling tự động dời lịch.
- MES/PLC integration hoàn toàn ngoài phạm vi hiện tại — mọi số liệu thực thi (good/scrap/rework) là
  nhập tay có kiểm soát, không giả vờ là dữ liệu máy.

---

## 11. Dự kiến câu hỏi hội đồng + gợi ý trả lời

**Q: Vì sao chọn modular-monolith thay vì microservices?**
> Quy mô SME, một team nhỏ, transaction xuyên nhiều domain (VD: release Work Order phải đồng thời
> đúng cả reservation lẫn inventory) — với monolith, đó là một `@Transactional` đơn giản và ACID
> thật. Với microservices, đó là distributed transaction/saga — độ phức tạp tăng vọt mà lợi ích scale
> độc lập chưa cần thiết ở quy mô này. Ranh giới domain vẫn được giữ nghiêm ở tầng code (module +
> quy tắc cross-module chỉ gọi qua lookup service), nên nếu sau này cần tách, ranh giới đã có sẵn.

**Q: Làm sao đảm bảo tồn kho không bao giờ âm hay sai lệch?**
> Ba lớp bảo vệ: (1) CHECK constraint ở tầng DB cho các bất biến số lượng (reserved+hold ≤ quantity),
> (2) ledger append-only nên không bao giờ "sửa đè" một giao dịch đã ghi — chỉ ghi thêm dòng đối ứng,
> (3) mọi API ghi tồn kho có validate fail-fast trước khi chạm DB và bọc trong transaction.

**Q: Nếu hai người cùng thao tác một Work Order cùng lúc thì sao?**
> Optimistic locking qua `@Version` — request đến sau với version cũ bị từ chối rõ ràng bằng
> `409 CONCURRENT_MODIFICATION`, không có chuyện ghi đè âm thầm.

**Q: Sao biết hệ thống đúng, không chỉ "chạy được"?**
> Không chỉ code compile và test xanh — team áp dụng mutation testing thủ công: cố tình phá một dòng
> logic, xác nhận đúng test nào bắt được. Một số bug nghiêm trọng (xem war story B) chỉ lộ ra qua
> cách làm này, không phải chỉ chạy `mvn test` là xong.

**Q: Backend có phụ thuộc gì vào frontend để đảm bảo đúng đắn không?**
> Không. Frontend chỉ cải thiện trải nghiệm (chặn sớm, gợi ý) — mọi rule quan trọng được backend
> revalidate độc lập. Kể cả khi FE có bug hoặc bị bypass hoàn toàn (gọi thẳng API), backend vẫn giữ
> đúng mọi ràng buộc.

**Q: Phần nào của hệ thống bạn tự hào nhất / phức tạp nhất mà bạn tự viết?**
> Chọn theo thế mạnh thật của bạn — gợi ý: MRP netting engine (đệ quy BOM đa cấp + công thức netting
> + chống đếm trùng cung ứng), hoặc thiết kế availability/idempotency đảm bảo tính nhất quán toàn hệ
> thống. Trả lời kèm **một** ví dụ cụ thể, không nói chung chung.

---

## 12. Checklist chuẩn bị trước buổi pitching

- [ ] 1 slide kiến trúc tổng (FE ↔ API ↔ DB/Redis) — vẽ đơn giản, không copy nguyên Figure 1 của báo cáo.
- [ ] 1 slide/sequence rút gọn 8 bước luồng nghiệp vụ xương sống (mục 5).
- [ ] 1 slide state machine Work Order (DRAFT→...→CLOSED, nhánh BLOCKED/CANCELLED).
- [ ] 1 slide công thức netting MRP + 1 ví dụ số cụ thể (dễ hiểu hơn công thức trừu tượng).
- [ ] 1 slide/bảng số liệu test (unit/integration/mutation) để chốt phần chất lượng.
- [ ] Học thuộc 2-3 "war story" ở mục 9 — kể được trong 30-40 giây mỗi câu, không lan man.
- [ ] Canh giờ nói thử ít nhất 1 lần với đồng hồ — 6-10 phút là rất dễ vượt nếu không luyện.
- [ ] Thống nhất với 2 thành viên còn lại ai nói phần nào để không lặp/chồng nội dung (đặc biệt mục 4,
      5, 7 có thể trùng với phần FE hoặc phần mở đầu chung).

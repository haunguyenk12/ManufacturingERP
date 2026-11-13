# DiagramPlan — Rút gọn sơ đồ KIẾN TRÚC (Architecture Overview)

> **Trạng thái:** BẢN NHÁP CHỜ DUYỆT — **chưa sửa một file nào**.
> **Nguồn:** phản hồi của thầy phản biện (2026-08-27): *"architecture cũng quá chi tiết, chữ nhỏ,
> không thể đọc hết"*.
> **Phạm vi lượt này:** **chỉ trang 1 (`1 - Architecture Overview`) của `Diagram.drawio`.**

---

## 0. Việc đã xong ở lượt trước (business flow) — để không làm lại

| | |
|---|---|
| **Đã làm** | Thêm **trang 3** `3 - Business Flow (condensed)` vào `Diagram.drawio`: **3 lane · 9 hộp · 1 decision · 12 mũi tên · canvas 1560 × 940 · chữ 17px**, tiếng Anh. Bỏ lane ADMIN, bỏ ghi chú sổ kho (ý 10 theo yêu cầu). |
| **Còn nợ** | (a) Xoá trang 2 cũ sau khi chốt trang 3. (b) Đồng bộ `Outline.md`, `Outline_For_Slide.md`, `ReportExplaination.md §3.4` (đang ghi *"4 làn bơi"*). (c) Bản chi tiết `docs/manufacturing-erp-activity-swimlane.drawio` **giữ nguyên**, đóng băng làm bằng chứng. |

---

## 1. Đo hiện trạng trang 1 — số liệu thật, không cảm tính

| Chỉ số | Hiện tại | Mục tiêu |
|---|---|---|
| Tổng vertex | **34** | **≤ 14** |
| Hộp nội dung (không kể khung dải) | **26** | **≤ 11** |
| Khung dải / container | **7** (6 dải + 1 cột cross-cutting) | **2** |
| Mũi tên | **5** | 5–6 |
| Cỡ chữ hộp nội dung | **13 px** | **16–17 px** |
| Cỡ chữ nhãn dải | 15 px | 18 px |
| Canvas | **1900 × 1090** | **≈ 1380 × 880** |

**Tỉ lệ thông tin/mực:** 26 nhãn nhưng chỉ **5 quan hệ**. Hình dành gần hết diện tích để **liệt kê
tên**, gần như không dành chỗ nào để **cho thấy các thứ đó liên hệ với nhau ra sao** — trong khi
"liên hệ ra sao" mới là thứ một sơ đồ kiến trúc tồn tại để trả lời.

---

## 2. Chẩn đoán — 4 bệnh, mỗi bệnh có thuốc riêng

### 2.1 🔴 Chữ nhỏ là **hệ quả**, không phải nguyên nhân

13 px trên canvas rộng 1900 px: khi chiếu vừa màn hình, chữ hiện ra tương đương ~9 px. Nhưng **phóng
chữ to lên mà giữ 26 hộp thì hình sẽ tràn** — vậy nên phải **giảm số hộp trước**, chữ to là kết quả
đi kèm chứ không phải một việc riêng.

### 2.2 🔴 Dải 3 và dải 4 **chia hệ thống hai lần theo cùng một trục** — đây là chỗ dư thừa lớn nhất

| Dải 3 (API layer) | Dải 4 (Business layer) |
|---|---|
| Auth & Users | Access control |
| Master data | Master data (BOM/Routing/Items) |
| Operations | Sales · Planning · Production · Inventory · Purchasing |
| Reports & Audit | *(không có)* |

Người xem đọc **8 hộp** để nhận đúng **một** thông tin: *"hệ thống chia theo nghiệp vụ"*. Dải 3 không
thêm gì ngoài việc lặp lại cách chia của dải 4 bằng từ ngữ khác. ⇒ **Gộp dải 3 thành một hộp duy nhất**
("REST API `/api/v1`"), giữ toàn bộ việc chia theo nghiệp vụ ở dải 4.

### 2.3 🔴 Bốn dải chỉ chứa nội dung ở mức "một ý" nhưng vẫn tốn một khung dải riêng

Dải 1 (2 hộp), dải 2 (4 hộp cùng loại), dải 3 (4 hộp, xem 2.2), dải 5 (2 hộp). Mỗi khung dải tốn
~110 px chiều cao + một nhãn — đúng loại chi phí mà lane ADMIN của business flow đã bị cắt vì lý do
này. ⇒ **Bỏ khung dải cho dải 1, 2, 3, 5**; tên dải viết **ngay trên hộp**. Chỉ **dải 4** (nhóm 4 khối
ngang hàng) và **cột cross-cutting** thật sự cần một khung.

### 2.4 🔴 Hình **không nói ra** thông điệp quan trọng nhất — đây là thiếu sót, không phải chi tiết thừa

`ReportExplaination.md §3.2` và `Outline_For_Slide.md` "Ý 1" đều lấy **modular monolith** làm câu ăn
điểm, `Outline.md:359` liệt kê nó thành câu hỏi phản biện dự kiến. Nhưng trên hình hiện tại **không có
chữ "monolith" nào**, cũng không có chữ nào nói *"một bản triển khai duy nhất"* hay *"module gọi nhau
qua service, không chạm repository của nhau"*. Người xem không thể tự suy ra điều đó từ 7 ô xếp hàng.

⇒ Rút gọn lần này **thêm chữ** ở đúng hai chỗ (nhãn dải 4 + nhãn mũi tên bảo mật) trong khi bỏ 15 hộp.
Ít mực hơn, nhiều nghĩa hơn.

### 2.5 Hai điểm lệch nhỏ, tiện sửa luôn

1. **"Web Frontend (SPA)"** — report và slide đều gọi tên cụ thể **Next.js + TypeScript**. Tên cụ thể
   là điểm cộng kỹ thuật, tên chung thì không.
2. **"API Documentation (Swagger/OpenAPI)"** đang nằm trong dải **CLIENT**, ngang hàng với frontend —
   sai về mô hình: Swagger **không phải một client**, nó là tài liệu do chính backend sinh ra. Đặt nó
   ở đó khiến người đọc kỹ đặt câu hỏi không đáng có.

---

## 3. Bốn thông điệp hình phải truyền *(đây là câu trả lời cho "nhìn vào là nắm được ý chính")*

Trước khi vẽ, chốt xem hình **phải** làm người xem nhớ được gì. Mọi hộp không phục vụ một trong bốn ý
này đều là ứng viên bị cắt.

| # | Thông điệp | Thể hiện bằng gì trên hình |
|---|---|---|
| **M1** | **Modular monolith** — một bản triển khai duy nhất, bên trong chia module có ranh giới cứng | Nhãn dải nghiệp vụ: *"Modular monolith — one deployable, hard module boundaries"* |
| **M2** | **Backend là nguồn thẩm quyền cuối cùng** — mọi request đi qua một cổng bảo mật trước khi chạm nghiệp vụ | Một hộp "Security filter chain" nằm **chắn ngang** giữa client và API + nhãn mũi tên |
| **M3** | **Hai kho dữ liệu, hai vai khác nhau** | PostgreSQL = *source of truth* · Redis = *tokens · sessions · rate limits* (giữ 2 hộp riêng) |
| **M4** | **Bảo đảm xuyên suốt** — audit, truy vết, idempotency, hợp đồng lỗi thống nhất, RBAC | **Một** cột bên phải, 5 gạch đầu dòng trong **một** hộp |

---

## 4. Thiết kế bản rút gọn — 11 hộp, 13 vertex

```
                   MANUFACTURING ERP - SYSTEM ARCHITECTURE

   ┌──────────────────────────────────────────────────┐   ┌──────────────┐
   │ CLIENT — Next.js Frontend (SPA)                  │   │ CROSS-CUTTING│
   │ permission-aware UI · EN / VI                    │   │              │
   └────────────────────────┬─────────────────────────┘   │ · Audit log  │
                    HTTPS + JWT                           │ · Trace id   │
   ┌────────────────────────▼─────────────────────────┐   │ · Idempotency│
   │ SECURITY — Filter chain                          │   │ · Unified    │
   │ CORS · rate limit · JWT auth · trace id          │   │   error      │
   └────────────────────────┬─────────────────────────┘   │   contract   │
              every rule re-checked here                  │ · RBAC       │
   ┌────────────────────────▼─────────────────────────┐   │   per plant  │
   │ API — REST /api/v1                               │   │              │
   │ 20+ controllers · one response envelope          │   │              │
   └────────────────────────┬─────────────────────────┘   │              │
                            │                             │              │
   ┌────────────────────────▼─────────────────────────┐   │              │
   │ BUSINESS — Modular monolith                      │   │              │
   │ one deployable · hard module boundaries          │   │              │
   │ ┌───────────┐┌───────────┐┌──────────┐┌────────┐ │   │              │
   │ │ Sales &   ││ Production││ Inventory││ Master │ │   │              │
   │ │ Planning  ││ & Quality ││ &        ││ data & │ │   │              │
   │ │           ││           ││ Purchas. ││ Access │ │   │              │
   │ └───────────┘└───────────┘└──────────┘└────────┘ │   │              │
   └────────────────────────┬─────────────────────────┘   │              │
                            │                             │              │
   ┌────────────────────────▼─────────────────────────┐   │              │
   │ PERSISTENCE — Domain entities & JPA repositories │   │              │
   └───────────┬──────────────────────────┬───────────┘   └──────────────┘
               ▼                          ▼
   ┌───────────────────────┐  ┌───────────────────────┐
   │ PostgreSQL            │  │ Redis                 │
   │ source of truth       │  │ tokens · sessions ·   │
   │                       │  │ rate limits           │
   └───────────────────────┘  └───────────────────────┘
```

### 4.1 Bảng 11 hộp — nội dung chính + dòng phụ

| # | Hộp | Dòng chính (17 px) | Dòng phụ (13 px) |
|---|---|---|---|
| 1 | Client | **Next.js Frontend (SPA)** | permission-aware UI · EN / VI |
| 2 | Security | **Security filter chain** | CORS · rate limit · JWT auth · trace id |
| 3 | API | **REST API `/api/v1`** | 20+ controllers · one response envelope |
| 4 | Nghiệp vụ | **Sales & Planning** | orders · MRP |
| 5 | Nghiệp vụ | **Production & Quality** | work order · routing · QC · costing |
| 6 | Nghiệp vụ | **Inventory & Purchasing** | stock ledger · PO · goods receipt |
| 7 | Nghiệp vụ | **Master data & Access control** | items · BOM · org · roles & scopes |
| 8 | Persistence | **Domain entities & JPA repositories** | business invariants live here |
| 9 | Store | **PostgreSQL** | source of truth |
| 10 | Store | **Redis** | tokens · sessions · rate limits |
| 11 | Cross-cutting | **Cross-cutting guarantees** | audit log · trace id · idempotency · unified errors · RBAC per plant |

> Cộng thêm **2 vertex khung** (khung dải nghiệp vụ, khung cột cross-cutting) và **1 tiêu đề** ⇒ tổng
> **14 vertex**, so với 34 hiện tại.

### 4.2 Mũi tên (5) — mỗi mũi tên mang một nhãn có nghĩa

| Từ → đến | Nhãn |
|---|---|
| Client → Security | `HTTPS + JWT` |
| Security → API | `every rule re-checked server-side` ← **chở thông điệp M2** |
| API → Business | `business rules · ACID transaction` |
| Business → Persistence | *(không nhãn)* |
| Persistence → PostgreSQL / Redis | `SQL` / `key-value` |

---

## 5. Bảng gộp: 26 hộp → 11, cắt gì đi đâu

| Hộp hiện tại | Xử lý | Đi đâu |
|---|---|---|
| `c11` Web Frontend (SPA) | **Đổi tên** → *Next.js Frontend (SPA)* | Hộp 1 |
| `c12` API Documentation (Swagger/OpenAPI) | **Bỏ khỏi hình** | `ReportExplaination.md §3.2` + nói miệng khi demo Swagger UI. Xem lý do ở §2.5 |
| `c21`–`c24` CORS · tracing · rate limit · JWT (4 hộp) | **Gộp 1 hộp** | Hộp 2 — giữ **đủ 4 từ** ở dòng phụ |
| `c31`–`c34` Auth&Users · Master data · Operations · Reports&Audit (4 hộp) | **Gộp 1 hộp** | Hộp 3 — cách chia theo nghiệp vụ đã có ở dải 4, xem §2.2 |
| `c41` Sales + `c42` Planning (MRP) | **Gộp** | Hộp 4 |
| `c43` Production (Work Order) | Mở rộng tên | Hộp 5 — thêm *routing · QC · costing* (những module hiện **không** có mặt trên hình) |
| `c44` Inventory + `c45` Purchasing | **Gộp** | Hộp 6 |
| `c46` Master data + `c47` Access control | **Gộp** | Hộp 7 |
| `c51` Domain entities + `c52` JPA repositories | **Gộp** | Hộp 8 |
| `c61` PostgreSQL | **Giữ riêng** | Hộp 9 — chở thông điệp M3 |
| `c62` Redis | **Giữ riêng** | Hộp 10 — chở thông điệp M3 |
| `c71`–`c75` (5 hộp cross-cutting) | **Gộp 1 hộp, 5 gạch đầu dòng** | Hộp 11 — giữ **đủ 5 khái niệm** |
| Khung dải `b1`, `b2`, `b3`, `b5` | **Bỏ khung** | Tên dải viết ngay trên hộp (`CLIENT —`, `SECURITY —`, …) |
| Khung dải `b4`, cột `b7` | **Giữ** | Hai chỗ duy nhất thật sự nhóm nhiều khối ngang hàng |

> **Nghiệm thu bảng này:** không khái niệm nào biến mất — 4 từ bảo mật, 5 khái niệm cross-cutting, 7
> module nghiệp vụ đều còn nguyên trên hình, chỉ đổi từ *"mỗi thứ một hộp"* sang *"một hộp, nhiều
> từ"*. Thứ **duy nhất** rời khỏi hình là Swagger — và nó có địa chỉ mới ở §5 hàng 2.

---

## 6. Bố cục pixel (để dựng, không phải để bàn thêm)

- Canvas **1380 × 880** (tỉ lệ 1.57 — vừa slide 16:9 và A4 ngang).
- Cột chính: `x = 40`, rộng **980**. Cột cross-cutting: `x = 1060`, rộng **280**, cao suốt hình.
- Chiều cao hàng: hộp đơn **h = 78**; khung dải nghiệp vụ **h = 190** (4 hộp con `h = 100`).
- Toạ độ `y`: tiêu đề 20 · hộp 1 tại 80 · hộp 2 tại 196 · hộp 3 tại 312 · khung dải 4 tại 428 ·
  hộp 8 tại 656 · hàng store tại 772 (h = 88) → kết thúc 860.
- Chữ: dòng chính **17 px đậm**, dòng phụ **13 px**, tên dải **18 px**, tiêu đề **26 px**.
- Màu: **giữ nguyên bảng màu hiện tại** (client xanh dương, security đỏ, API vàng, business xanh lá,
  persistence tím, hạ tầng xám, cross-cutting cam) — đổi màu là đổi luôn liên hệ thị giác với trang 3.
- Ngôn ngữ: **tiếng Anh**, đồng bộ trang 3 vừa chốt.

---

## 7. Phương án kỹ thuật

### ✅ Phương án A — Thêm **trang 4** `4 - Architecture (condensed)`, giữ trang 1 *(đề xuất)*
Đúng cách đã làm với business flow ở lượt trước: dựng bản mới cạnh bản cũ, so sánh trực tiếp, chốt
xong mới xoá bản cũ. Rủi ro thấp nhất.

### Phương án B — Sửa đè trang 1
Gọn file ngay, nhưng **mất bản đối chiếu** khi bạn muốn cân nhắc lại một hộp nào đó. Trang 1 **không**
có bản sao ở `docs/` (khác business flow — cái đó có `manufacturing-erp-activity-swimlane.drawio` làm
bản chi tiết dự phòng) ⇒ sửa đè là **mất vĩnh viễn**. **Không khuyến nghị.**

### Phương án C — Giữ 6 dải, chỉ phóng chữ to
Không giải quyết được gì: phóng 26 hộp lên 17 px thì canvas phải rộng ~2400 px, chiếu ra còn nhỏ hơn
hiện tại.

---

## 8. Ảnh hưởng sang tài liệu khác

| File | Có phải sửa? | Việc cụ thể |
|---|---|---|
| `Outline.md:53-54` | ⚠️ Kiểm lại | Đang mô tả *"FE ↔ REST `/api/v1` ↔ modular-monolith ↔ PostgreSQL + Redis"* — **đã khớp** hình mới. Nhiều khả năng không phải sửa |
| `Outline.md:394` | ✅ Có | Đang ghi *"vẽ đơn giản, **không copy nguyên Figure 1** của báo cáo"* — sau lượt này thì hình chính **đã** đơn giản, bỏ được câu cảnh báo đó |
| `Outline_For_Slide.md:228-236` | ✅ Có | Khối ASCII "Diagram 1 — Kiến trúc 3 lớp" liệt kê **9 module** dạng chữ chạy; hình mới nhóm thành **4 khối**. Đồng bộ để slide và hình không nói hai kiểu |
| `ReportExplaination.md §3.2` | ⚠️ Kiểm lại | Văn xuôi **đã** đúng tinh thần hình mới (monolith · backend là nguồn thẩm quyền · PostgreSQL vs Redis). Chỉ cần bảo đảm caption Figure 1 trỏ đúng trang mới |
| `docs/architecture.md` | ❌ **Không đụng** | 574 dòng, 12 chương — tài liệu **kỹ thuật nội bộ** (Redis key pattern, YAML rate limit). Không phải Figure, thầy phản biện không đọc |
| `CLAUDE.md §0.1` | ✅ Có | Ghi nhận theo `dev-workflow.md §6.5` |

---

## 9. Các bước thực hiện

| # | Bước | Đầu ra kiểm chứng được |
|---|---|---|
| 1 | Bạn duyệt file này (§11 có 5 câu cần chốt) | — |
| 2 | Dựng trang 4 theo §4 + §6 | Đếm được **14 vertex · 11 hộp nội dung · 5 mũi tên · canvas 1380 × 880** |
| 3 | Mở draw.io kiểm mắt: không hộp nào tràn chữ, không mũi tên chồng | Export PNG đối chiếu |
| 4 | Đối chiếu bảng §5 — mọi khái niệm cũ còn nguyên trên hình | Chỉ Swagger rời hình, và có địa chỉ mới |
| 5 | Sửa `Outline.md:394`, `Outline_For_Slide.md:228` | Slide và hình nói cùng một cách chia |
| 6 | Xoá trang 1 và trang 2 cũ sau khi bạn chốt cả hai bản mới | File còn **2 trang**: Architecture + Business Flow |
| 7 | Cập nhật `CLAUDE.md §0.1`, commit `docs(diagram): condense architecture to 11 blocks` | 1 commit |

**Không** làm ở bất kỳ bước nào: đụng `src/main`, đụng migration, sửa `docs/architecture.md`, sửa
`docs/manufacturing-erp-activity-swimlane.drawio`.

---

## 10. Rủi ro và cách chặn

| Rủi ro | Cách chặn |
|---|---|
| Hội đồng hỏi *"backend có bao nhiêu module?"* mà hình chỉ có 4 khối | Dòng phụ trong mỗi khối **liệt kê tên module thật** (orders · MRP · work order · routing · QC · costing · stock ledger · PO · goods receipt · items · BOM · org · roles) — đếm được **13 tên** trên hình, nhiều hơn bản cũ (7) |
| Mất hộp Swagger ⇒ mất một điểm cộng | Demo Swagger UI trực tiếp khi trình bày; nhắc trong `ReportExplaination.md §3.2` |
| Gộp "Master data & Access control" bị coi là gộp hai thứ không liên quan | Đây là câu hỏi #3 ở §11 — có phương án tách thành 5 khối nếu bạn thấy gợn |
| Bỏ khung dải làm mất cảm giác "phân lớp" | Mũi tên dọc một chiều + tên dải viết in hoa đầu mỗi hộp (`CLIENT —`, `SECURITY —`, `API —`) vẫn giữ nguyên trục phân lớp |

---

## 11. Câu cần bạn chốt trước khi tôi bắt tay

1. **Phương án A / B / C** ở §7? *(tôi đề xuất **A** — thêm trang 4, giữ trang 1 để đối chiếu)*
2. **Bỏ hộp Swagger/OpenAPI** khỏi hình — đồng ý chứ? *(lý do ở §2.5; nếu muốn giữ, tôi để nó thành
   một nhãn nhỏ cạnh hộp API thay vì một hộp ngang hàng với frontend)*
3. **Gộp 7 module nghiệp vụ thành 4 khối** như §4.1, hay tách thành **5** để "Access control" đứng
   riêng khỏi "Master data"? *(4 khối gọn hơn; 5 khối trung thực hơn về ngữ nghĩa)*
4. **Bỏ khung dải cho CLIENT / SECURITY / API / PERSISTENCE** (tên dải viết trên chính hộp) — đồng ý
   chứ? Đây là thay đổi cắt nhiều "mực" nhất.
5. **Thêm chữ "Modular monolith — one deployable, hard module boundaries"** lên nhãn dải nghiệp vụ —
   đây là **thêm** chứ không phải bớt (§2.4). Bạn có muốn giữ nguyên không có câu này không?

---

## 12. Nghiệm thu (Definition of Done)

- [ ] Hình mới có **≤ 11 hộp nội dung**, **≤ 14 vertex**, **2 khung** (dải nghiệp vụ + cột cross-cutting)
- [ ] Cỡ chữ hộp **≥ 16 px**; canvas **≤ 1400 × 900**, đọc được **không cần zoom**
- [ ] Người chưa biết hệ thống nhìn hình **≤ 30 giây** nói được **4 thông điệp M1–M4** ở §3
- [ ] Bảng §5 điền đủ — mọi khái niệm cũ còn trên hình, trừ Swagger (có địa chỉ mới)
- [ ] Trên hình đọc được chữ **"Modular monolith"** và **"one deployable"** (M1) — thứ bản cũ không có
- [ ] Trang 1 cũ vẫn mở được cho tới khi bạn tự tay chốt xoá

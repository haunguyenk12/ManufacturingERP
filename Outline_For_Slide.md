# Outline Slide Pitching — OmniPlant Manufacturing ERP (Capstone 2)

> **Bản rút gọn: 10 slide + clip demo 5 phút chiếu sau khi trình bày xong.**
>
> **Cấu trúc buổi:**
> ```
> Slide 1–5  : Bối cảnh, giá trị, thiết kế hệ thống   (~8 phút)
> Slide 6–9  : Các chức năng chính — thao tác thế nào  (~5 phút)
> Slide 10   : Future Work + mời xem demo              (~1.5 phút)
> ▶ CLIP DEMO 5 PHÚT
> Q&A
> ```
> **Tổng: ~14–15 phút nói + 5 phút clip ≈ 20 phút.**
>
> **Nguyên tắc phân bổ nội dung:**
> - **Slide 1–5**: nói *vì sao*, *cho ai*, *thiết kế thế nào* — đây là chỗ duy nhất đi sâu kỹ thuật.
> - **Slide 6–9**: chỉ nói **người dùng thao tác thế nào trên màn hình** — bấm gì, điền gì, thấy gì.
>   **Không giải thích cơ chế bên trong** (clip demo sẽ chứng minh, và Q&A sẽ đào sâu nếu cần).
> - Mỗi slide 6–9 kết bằng một câu gợi mở *"lát nữa trong clip thầy cô sẽ thấy…"* để nối sang demo.
>
> **Cách đọc file:** mỗi slide có **📊 Trên slide** (hiện gì) · **🗣 Nói** (lời thoại) ·
> **🎬 Clip sẽ thấy** (nối sang demo) · **❓ Phòng khi bị hỏi** (không nói, chỉ để thủ sẵn).
>
> Tài liệu tra sâu khi bị hỏi xoáy: `Backend-DeepDive.md` · `Outline.md` · `ReportExplaination.md`.

## ⚠️ Đọc trước — 2 điều phải thống nhất cả nhóm

| Vấn đề | Trạng thái |
|---|---|
| 🔴 **Slide 10 (Future Work)** | Routing / Capacity / Excel-Import **đã có ở backend rồi**, chỉ thiếu UI. **Đừng nói "chưa làm"** — hội đồng mở Swagger là thấy endpoint. Cách nói đúng ở Slide 10 |
| **Slide 2** | Cần điền vai trò thật của An và Cương |

---

## Bản đồ slide & phân vai

| # | Nội dung | Người nói | Phút | Tính chất |
|---|---|---|---|---|
| 1 | Trang mở đầu | Người A | 0.5 | — |
| 2 | Giới thiệu thành viên | Cả 3 (mỗi người 1 câu) | 0.5 | — |
| 3 | Bối cảnh dự án | Người A | 2.5 | Bán ý tưởng |
| 4 | Capstone 2 làm được gì | Người A | 1.5 | Chứng minh kết quả |
| 5 | **Thiết kế hệ thống & luồng nghiệp vụ** *(diagram tự chèn)* | **BE (Hậu)** | 3.0 | ⚙️ **Kỹ thuật** |
| 6 | Đăng nhập & phân quyền — thao tác | FE | 1.0 | 🖥 UI |
| 7 | Hoạch định vật tư (MRP) — thao tác | BE | 1.5 | 🖥 UI |
| 8 | Lệnh sản xuất — thao tác | FE | 1.0 | 🖥 UI |
| 9 | Nhập kho & kiểm tra chất lượng — thao tác | BE | 1.5 | 🖥 UI |
| 10 | Future Work + mời xem demo | Người A | 1.5 | — |
| | | **Tổng nói** | **~14.5** | |
| ▶ | **Clip demo 5 phút** | *(kịch bản ở Phụ lục B)* | 5.0 | |

**Phân vai slide 6–9:** FE dẫn slide 6 và 8 (quản trị + lệnh sản xuất), BE dẫn slide 7 và 9
(hoạch định + chất lượng — hai nghiệp vụ lõi). Linh hoạt đổi nếu ai nắm màn hình nào chắc hơn.

---

# SLIDE 1 — Trang mở đầu

**📊 Trên slide**

```
                    OmniPlant
              Manufacturing ERP System

        Hệ thống hoạch định nguồn lực sản xuất
             cho doanh nghiệp vừa và nhỏ

                    Capstone 2

     Trần Quốc An · Nguyễn Phúc Hậu · Đặng Cao Cương
              GVHD: ThS. Hà Minh Ngọc
       Trường Đại học Quốc tế Miền Đông — 08/2026
```

**🗣 Nói (20s)**
> "Kính chào thầy cô. Nhóm em xin trình bày đề tài **OmniPlant — hệ thống ERP sản xuất cho doanh
> nghiệp vừa và nhỏ**. Ở Capstone 1, nhóm em dựng phân tích và bản mẫu giao diện. **Capstone 2, nhóm
> em biến nó thành một vòng sản xuất chạy thật** — từ đơn hàng của khách cho tới lô thành phẩm đã qua
> kiểm tra chất lượng."

> 💡 **Nói thêm 1 câu về cấu trúc buổi** (giúp hội đồng biết trước, đỡ sốt ruột):
> *"Phần trình bày khoảng 15 phút, sau đó nhóm em xin chiếu một clip demo 5 phút để thầy cô thấy hệ
> thống chạy thật ạ."*

---

# SLIDE 2 — Giới thiệu thành viên

**📊 Trên slide** — 3 ô ngang: ảnh + tên + MSSV + vai trò + 2–3 từ khoá đóng góp.

| Thành viên | Vai trò | Đóng góp chính |
|---|---|---|
| **Trần Quốc An** — 2231200001 | *(điền)* | *(điền)* |
| **Nguyễn Phúc Hậu** — 2231200065 | **Backend Developer** | API, luật nghiệp vụ, cơ sở dữ liệu, bảo mật & phân quyền |
| **Đặng Cao Cương** — 2231200087 | *(điền)* | *(điền)* |

**🗣 Nói (25s)** — mỗi người **tự** giới thiệu một câu, không ai nói hộ ai:
> BE: *"Em là Hậu, phụ trách backend — em làm API, luật nghiệp vụ, thiết kế cơ sở dữ liệu và phần
> phân quyền bảo mật."*

**⚙️ Lưu ý** — hội đồng **rất hay hỏi** "phần này ai làm?". Nói rõ vai trò ngay từ đầu để về sau
không lúng túng. Tính năng nào 2 người cùng làm thì nói thẳng là cùng làm.

---

# SLIDE 3 — Bối cảnh dự án

> Slide "bán ý tưởng" — trả lời 4 câu: **vì sao cần**, **làm gì**, **cho ai**, **khác gì cái đã có**.
> Nói bằng ngôn ngữ nghiệp vụ, chưa nói kỹ thuật.

**📊 Trên slide** — 4 khối, mỗi khối 1 dòng + icon. **Đừng bê nguyên các bảng dưới đây lên slide** —
đó là tư liệu cho người nói.

### Khối 1 — Vì sao SME cần (nỗi đau)

| Nỗi đau thực tế | Hậu quả |
|---|---|
| Kho báo **còn 100**, nhưng 60 đã giữ chỗ, 20 đang chờ kiểm tra chất lượng | Nhận đơn rồi mới biết **không đủ hàng** |
| Sửa định mức hôm nay, lệnh sản xuất tuần trước **đổi theo** | Sản xuất sai, không truy được vì sao |
| Bấm nhập kho 2 lần do mạng chậm | **Tồn kho cộng đôi**, sổ sách sai |
| Không lần ngược được từ thành phẩm về lô nguyên liệu | Có lỗi chất lượng, **không thu hồi đúng lô** |

### Khối 2 — Dự án này để làm gì

> **Một câu:** OmniPlant nối liền **đơn hàng của khách → hoạch định vật tư → sản xuất → kiểm tra chất
> lượng → tồn kho** thành **một chuỗi chứng từ có kiểm soát và truy vết được**.

### Khối 3 — Đối tượng sử dụng

| Ai | Dùng để làm gì |
|---|---|
| **Quản trị viên** | Cấu hình tổ chức, tài khoản, phân quyền, xem nhật ký hệ thống |
| **Quản lý sản xuất** | Chốt đơn hàng, chạy hoạch định, duyệt lệnh sản xuất, phê duyệt & quyết định chất lượng |
| **Nhân viên kho / công nhân xưởng** | Giữ chỗ vật tư, xuất kho, báo sản lượng, nộp phiếu nhập kho |

Quy mô mục tiêu: **nhà máy lắp ráp vừa và nhỏ** — nhiều kho, cần kiểm soát chặt, nhưng **không đủ
ngân sách và nhân lực** triển khai SAP.

### Khối 4 — So sánh với phần mềm hiện có

| Hệ thống | Mạnh | Vấn đề với SME |
|---|---|---|
| **SAP S/4HANA** | MRP quy mô tập đoàn, lập lịch chi tiết, tính giá thành đầy đủ | Chi phí và độ phức tạp triển khai **quá lớn** |
| **Odoo Manufacturing** | Cấu hình linh hoạt, nhiều module | Bề mặt cấu hình **rộng hơn nhu cầu**, dễ lạc |
| **ERPNext** | Mã nguồn mở, dễ tiếp cận | Vẫn là ERP **tổng quát**, phải tuỳ biến nhiều |
| **OmniPlant** | **Ít khái niệm hơn nhưng luật của từng khái niệm rõ ràng**, truy vết xuyên suốt, phân quyền theo nhà máy | Phạm vi hẹp: mua hàng / tài chính / MES để Capstone 3 |

**🗣 Nói (2.5 phút) — kịch bản 4 đoạn:**

*(Mở bằng nỗi đau, đừng mở bằng định nghĩa ERP)*
> "Một xưởng sản xuất nhỏ hỏi một câu rất đơn giản: ***'còn đủ hàng để nhận đơn không?'*** — và
> thường không trả lời được. Hệ thống báo tồn 100, nhưng 60 đã giữ cho lệnh khác, 20 đang chờ kiểm
> tra chất lượng. Thực dùng chỉ có 20. Khi những con số này nằm rải rác ở Excel, quyết định sản xuất
> dựa trên số liệu cũ, định mức lỗi thời, và truy vết không đầy đủ."

*(Giải pháp — một câu)*
> "OmniPlant nối liền đơn hàng khách → hoạch định vật tư → sản xuất → kiểm tra chất lượng → tồn kho
> thành **một chuỗi chứng từ có kiểm soát**. Mỗi bước có người chịu trách nhiệm, có quyền hạn, có luật
> kiểm tra, và **truy ngược lại được**."

*(Đối tượng — nhanh)*
> "Ba nhóm người dùng: quản trị viên cấu hình hệ thống, quản lý sản xuất ra quyết định, và nhân viên
> kho cùng công nhân xưởng thực thi. Quy mô mục tiêu là nhà máy lắp ráp vừa và nhỏ."

*(So sánh — điểm chốt của slide)*
> "SAP, Odoo, ERPNext đều đã làm rất tốt. **Nhóm em không cạnh tranh về độ rộng.** Khoảng trống nhóm
> em nhắm tới không phải là 'thiếu phần mềm ERP', mà là **khó áp dụng một nền tảng khổng lồ cho một
> đội nhỏ cần sự minh bạch**. Vì vậy OmniPlant chọn **ít khái niệm hơn, nhưng làm rõ luật của từng
> khái niệm**: hàng nào được phép dùng, khi nào lệnh sản xuất được chạy, ai duyệt cái gì, và chất
> lượng ảnh hưởng thế nào tới hàng khả dụng."

**❓ Phòng khi bị hỏi**
- *"Sao không dùng luôn ERPNext cho nhanh?"* → "ERPNext là ERP tổng quát, dùng cho một xưởng cụ thể
  vẫn phải tuỳ biến đáng kể — mà khi tuỳ biến thì đội nhỏ mất kiểm soát luật nghiệp vụ. Nhóm em **cố
  ý đi theo đúng các khái niệm chuẩn của ngành** — BOM, lệnh sản xuất, phiếu kho — để người dùng
  không phải học mô hình lạ, nhưng tự viết để luật nào cũng giải thích được."

---

# SLIDE 4 — Capstone 2 làm được gì

> Slide "chứng minh đã giao hàng". Nói **kết quả**, chưa nói cách làm (cách làm ở slide 5).

**📊 Trên slide** — 2 cột.

| ✅ Đã hoàn thành ở Capstone 2 | ⏸ Có chủ đích để lại |
|---|---|
| **Xác thực & phân quyền động** theo Công ty → Nhà máy → Kho | Giao diện mua hàng |
| **Dữ liệu nền**: đơn vị tính, vật tư, **BOM đa cấp**, tồn kho, lô hàng | Giao hàng, hoá đơn, công nợ |
| **Đơn hàng bán** → sinh nhu cầu hoạch định | Tích hợp máy móc (MES/PLC/IoT), OEE |
| ⭐ **Hoạch định vật tư (MRP)**: nổ BOM đa cấp, tính thiếu hụt, đề xuất *tự làm / đi mua* | Tính giá thành chi tiết |
| **Lệnh sản xuất**: chụp ảnh định mức, giữ chỗ vật tư, đủ 100% mới cho chạy | Tối ưu lịch sản xuất tự động |
| **Thực thi**: xuất vật tư, báo sản lượng tốt / hỏng / làm lại | |
| ⭐ **Nhập kho & cổng chất lượng**: HOLD → AVAILABLE / REJECTED | |
| **Khép vòng**: hàng đạt chất lượng mới tính vào số đã giao của đơn hàng | |
| Dashboard, cảnh báo tồn thấp, **nhật ký kiểm toán**, giao diện **song ngữ EN/VI** | |

**🗣 Nói (1.5 phút)**
> "Capstone 2 nhóm em hoàn thành **trọn vẹn một vòng sản xuất**, chứ không phải làm dở nhiều thứ. Từ
> đơn hàng đã chốt, hệ thống tự tính ra cần sản xuất bao nhiêu, thiếu vật tư gì, đề xuất tự làm hay
> đi mua. Chuyển đề xuất thành lệnh sản xuất, giữ chỗ vật tư, xuất kho, báo sản lượng, nhập kho — và
> **hàng phải qua cổng kiểm tra chất lượng mới được tính là dùng được**, đồng thời mới được ghi nhận
> vào đơn hàng của khách."

*(Nhấn cột phải — đây là điểm tạo thiện cảm)*
> "Cột bên phải là những phần nhóm em **cố ý** để lại. Nhóm em chọn **làm sâu một vòng trọn vẹn** thay
> vì làm nông nhiều mảng. Đặc biệt, nhóm em **không giả lập dữ liệu máy móc** — mọi số liệu sản xuất
> đều do người nhập, có người chịu trách nhiệm và có dấu thời gian. Tuyên bố có tích hợp MES trong khi
> không có sẽ là mô tả sai hệ thống."

**❓ Phòng khi bị hỏi** — số liệu chứng minh, chỉ nói khi được hỏi: bộ dữ liệu demo phủ **8 trạng thái
lệnh sản xuất**, 4 trạng thái phiếu nhập kho, 6 trạng thái đơn hàng. Một lượt hoạch định thật:
**2 nhu cầu → 8 dòng yêu cầu → 6 thiếu hụt → 3 đề xuất tự làm + 3 đề xuất mua**.

> 💡 **Chuyển sang slide 5:** *"Để chuỗi đó không hở ở bất kỳ mắt xích nào, kiến trúc phải đặt đúng
> chỗ thẩm quyền. Em xin mời [tên BE] nói về thiết kế hệ thống."*

---

# SLIDE 5 — Thiết kế hệ thống & luồng nghiệp vụ ⭐

> **Slide kỹ thuật DUY NHẤT của cả bài — và là slide quan trọng nhất của BE.**
> Vì slide 6–9 đã chuyển sang mô tả thao tác, toàn bộ chiều sâu kỹ thuật dồn vào đây.
> **Bạn tự chèn 2 diagram.** Nếu chật thì tách thành 5a (kiến trúc) và 5b (luồng).

**📊 Trên slide**

**Diagram 1 — Kiến trúc 3 lớp** *(nội dung tối thiểu cần có)*

```
   Next.js + TypeScript  ──REST /api/v1──►  Spring Boot (Modular Monolith)
   (giao diện, điều hướng                   auth · organization · inventory · bom
    theo quyền, EN/VI)                      workorder · planning · sales · purchasing
                                                    │              │
                                            PostgreSQL         Redis
                                          (nguồn sự thật)   (phiên & token)
```

**Diagram 2 — Luồng nghiệp vụ xương sống**

```
Đơn hàng bán ──► Hoạch định (MRP) ──► Đề xuất cung ứng ──► Lệnh sản xuất
 (đã chốt)        nổ BOM đa cấp        tự làm / đi mua      chụp ảnh định mức
                  tính thiếu hụt                            giữ chỗ đủ 100%
                                                                  │
   Đơn hàng  ◄── Cổng chất lượng ◄── Nhập kho ◄── Báo sản lượng ◄──┘
  ghi đã giao      HOLD→AVAILABLE      (vào HOLD)    ◄── Xuất vật tư
```

**🗣 Nói (3 phút) — 3 ý, mỗi ý 1 phút:**

### Ý 1 — Kiến trúc (60s)
> "Backend dùng kiến trúc **modular monolith**: một ứng dụng triển khai duy nhất, nhưng bên trong
> chia thành các module theo nghiệp vụ với ranh giới cứng — module này muốn dùng dữ liệu module kia
> phải đi qua một lớp dịch vụ định nghĩa sẵn, **không được chạm thẳng vào cơ sở dữ liệu của nhau**."
>
> "Vì sao không dùng microservices? Vì một thao tác như *cho chạy lệnh sản xuất* phải đồng thời đúng
> cả phần giữ chỗ vật tư lẫn phần tồn kho. Với monolith, đó là **một giao dịch ACID thật** — sai một
> chỗ là quay lui toàn bộ. Với microservices, đó là giao dịch phân tán, phức tạp hơn nhiều mà lợi ích
> chia tải độc lập thì quy mô doanh nghiệp vừa và nhỏ chưa cần tới."

### Ý 2 — Ai có thẩm quyền (60s) *(câu ăn điểm)*
> "Nguyên tắc quan trọng nhất: **backend là nguồn thẩm quyền cuối cùng**. Giao diện có chặn sớm để
> người dùng đỡ bấm nhầm, nhưng **mọi luật quan trọng đều được backend kiểm lại**: quyền theo nhà máy,
> điều kiện cho phép chạy lệnh sản xuất, tồn kho khả dụng, quyền phê duyệt, quyết định chất lượng."
>
> "Nghĩa là nếu ai đó bỏ qua giao diện, gọi thẳng API, **cũng không phá được luật nào**. Giao diện lo
> trải nghiệm, backend lo tính đúng đắn."

### Ý 3 — Luồng nghiệp vụ (60s) — chỉ theo diagram 2, kể như một câu chuyện
> "Khách đặt hàng. Đơn **phải được chốt** thì nhu cầu mới vào hoạch định — đơn nháp không tham gia."
>
> "Chạy hoạch định: hệ thống nổ cây định mức nhiều cấp, trừ đi tồn khả dụng và hàng đang trên đường
> về, ra con số thiếu hụt, rồi đề xuất **tự sản xuất** hay **đi mua**."
>
> "Đề xuất tự sản xuất chuyển thành lệnh sản xuất. Lúc này định mức và quy trình công nghệ được **chụp
> ảnh đóng băng** vào lệnh — sau này kỹ thuật có sửa định mức thì lệnh cũ vẫn giải thích được."
>
> "Phải giữ chỗ **đủ 100%** vật tư mới được cho chạy. Thiếu thì hệ thống đưa lệnh về trạng thái
> **BLOCKED** và báo rõ thiếu bao nhiêu — chứ không im lặng."
>
> "Xuất vật tư, xưởng báo sản lượng, nộp phiếu nhập kho. Quản lý duyệt thì hàng vào kho **nhưng ở
> trạng thái HOLD, chưa dùng được**. Chỉ khi bộ phận chất lượng phán quyết `AVAILABLE`, hàng mới thành
> tồn khả dụng — **và cùng lúc đó** mới được ghi nhận vào số đã giao của đơn hàng ban đầu. Vòng khép
> kín."

**❓ Phòng khi bị hỏi** *(nắm chắc 5 điểm này là đủ tự tin cho cả buổi)*

| Câu hỏi | Trả lời |
|---|---|
| Modular monolith có phải monolith nói cho sang? | Khác ở ranh giới **được ép buộc**: module không gọi cơ sở dữ liệu của module khác, chỉ gọi qua dịch vụ công khai. Nếu sau này cần tách microservice thì đường cắt đã có sẵn |
| Hai người sửa cùng lúc? | Mỗi chứng từ có **số phiên bản**; người đến sau mang số cũ bị từ chối `409 CONCURRENT_MODIFICATION`, không ghi đè âm thầm |
| Mạng lỗi bấm 2 lần? | **Idempotency key lưu trên cột chứng từ + ràng buộc duy nhất ở database** (không phải cache tạm) — gửi lại trả về đúng chứng từ cũ |
| Tồn kho có bao giờ âm được không? | 3 lớp: ràng buộc `CHECK` ở database, **sổ cái chỉ-ghi-thêm** (sửa sai bằng dòng đối ứng, không xoá), và validate trước khi ghi |
| Sao biết hệ thống đúng chứ không chỉ chạy được? | Ngoài test thường, nhóm **cố tình phá một dòng code rồi chạy lại test** để xác nhận đúng test nào phải đỏ; và test tích hợp chạy PostgreSQL thật trong Docker |

> 💡 **Chuyển sang slide 6:** *"Đó là bên trong. Bây giờ em xin cho thầy cô xem người dùng thật sự
> thao tác thế nào trên hệ thống."*

---

> # 🖥 TỪ ĐÂY: SLIDE 6–9 — CHỈ NÓI THAO TÁC TRÊN GIAO DIỆN
>
> **Quy tắc cho cả 4 slide:**
> - Mô tả **người dùng bấm gì, điền gì, thấy gì** — như đang hướng dẫn sử dụng.
> - **Không** giải thích cơ chế bên trong, không nêu công thức, không kể bug.
> - Mỗi slide kết bằng câu **"trong clip lát nữa thầy cô sẽ thấy…"**.
> - Ảnh **to, ít** — 1 ảnh chính + tối đa 1 ảnh phụ. Nhồi 5 ảnh nhỏ là hội đồng không nhìn được.
> - Trên slide nên có **các bước 1-2-3** đánh số, để hội đồng theo dõi bằng mắt trong lúc nghe.

---

# SLIDE 6 — Đăng nhập & phân quyền: thao tác thế nào

**📊 Trên slide**
- Ảnh chính: màn hình **Đăng nhập** hoặc **Gán quyền cho người dùng**
- Ảnh phụ: menu bên trái của 2 tài khoản khác nhau đặt cạnh nhau *(quản lý vs công nhân)* — **đây là
  ảnh thuyết phục nhất** vì thấy ngay menu khác nhau
- Tiêu đề: *"Mỗi người chỉ thấy đúng phần việc của mình"*

**Các bước hiện trên slide:**
```
1  Đăng nhập → hệ thống nhận diện quyền và nhà máy được phép
2  Menu, nút bấm, dữ liệu tự thay đổi theo quyền
3  Quản trị viên: tạo tài khoản → tạo vai trò → gán quyền → gán nhà máy
```

**🗣 Nói — FE (50s)**
> "Bắt đầu từ đăng nhập. Ngay sau khi vào, hệ thống nhận diện người này có những quyền gì và **được
> làm việc ở nhà máy nào**."
>
> *(chỉ vào ảnh 2 menu cạnh nhau)*
> "Thầy cô nhìn hai ảnh này: cùng một hệ thống, nhưng **menu hoàn toàn khác nhau**. Bên trái là tài
> khoản quản lý — thấy đầy đủ hoạch định, lệnh sản xuất, phê duyệt. Bên phải là tài khoản công nhân —
> chỉ thấy những màn hình mình cần thao tác. Người dùng **không nhìn thấy nút bấm mà bấm vào sẽ báo
> lỗi từ chối**."
>
> "Còn về phía quản trị viên, thao tác gồm 4 bước: tạo tài khoản, tạo vai trò, tick chọn các quyền
> cho vai trò đó, rồi gán vai trò kèm **phạm vi nhà máy** cho từng người. Toàn bộ làm trên giao diện,
> không cần lập trình viên can thiệp."

**🎬 Clip sẽ thấy**
> "Trong clip lát nữa, thầy cô sẽ thấy nhóm em đăng nhập bằng tài khoản quản lý và chọn nhà máy —
> mọi thao tác sau đó đều nằm trong phạm vi nhà máy đó."

**❓ Phòng khi bị hỏi** *(không nói ra)*
- Quyền **không hardcode** — lưu trong cơ sở dữ liệu, thêm vai trò mới không cần deploy lại.
- Quyền cấp **Công ty tự động bao trùm** mọi Nhà máy/Kho con; quyền ở một Kho **không lan** sang kho khác.
- **Tách bạch nhiệm vụ**: người nộp phiếu nhập kho **không** tự duyệt phiếu của mình được.

---

# SLIDE 7 — Hoạch định vật tư (MRP): thao tác thế nào ⭐

**📊 Trên slide**
- Ảnh chính (to): **Chi tiết lượt hoạch định** — thấy rõ danh sách yêu cầu, cột thiếu hụt, và nhãn
  MAKE / BUY
- Ảnh phụ: màn hình **Chọn nhu cầu để chạy hoạch định**
- Tiêu đề: *"Từ đơn hàng → biết ngay cần làm gì, mua gì"*

**Các bước hiện trên slide:**
```
1  Chọn các đơn hàng đã chốt cần hoạch định
2  Bấm "Chạy hoạch định"
3  Xem kết quả: cần gì · còn bao nhiêu · thiếu bao nhiêu
4  Với mỗi dòng thiếu: hệ thống đề xuất TỰ LÀM hoặc ĐI MUA
5  Quản lý duyệt đề xuất → chuyển thành lệnh sản xuất
```

**🗣 Nói — BE (1.5 phút)**
> "Đây là màn hình quan trọng nhất với người quản lý sản xuất."
>
> "**Bước 1 và 2** rất đơn giản: quản lý mở màn hình hoạch định, tick chọn những đơn hàng đã chốt cần
> lên kế hoạch, rồi bấm **Chạy hoạch định**. Chỉ một nút."
>
> *(chỉ vào ảnh chính — đây là phần đáng nói nhất)*
> "**Bước 3 — đây là thứ hệ thống trả về.** Mỗi dòng là một loại vật tư: cần bao nhiêu, đang có bao
> nhiêu, hàng đang trên đường về bao nhiêu, và **còn thiếu bao nhiêu**. Quan trọng là hệ thống không
> chỉ tính cho thành phẩm — nó **đi xuống hết cây định mức**. Cần 100 xe đạp thì nó tự biết cần bao
> nhiêu khung, bao nhiêu bánh, và mỗi bánh cần bao nhiêu nan hoa."
>
> "**Bước 4** — với mỗi dòng thiếu, hệ thống gắn nhãn: món này **tự sản xuất được** hay **phải đi
> mua**. Quản lý nhìn màn hình là biết ngay việc gì phải làm."
>
> "**Bước 5** — quản lý duyệt đề xuất tự sản xuất, bấm chuyển đổi, hệ thống tự tạo ra lệnh sản xuất
> tương ứng. **Không phải gõ lại gì cả** — sản phẩm, số lượng, ngày cần đều được mang sang."
>
> *(một câu về giá trị, không đi vào kỹ thuật)*
> "Điểm nhóm em muốn nhấn: **quản lý không phải tự tính bằng Excel nữa**. Và mỗi lượt chạy được lưu
> lại như một bản ghi độc lập, nên sau này vẫn xem lại được lúc đó hệ thống đã đề xuất gì và vì sao."

**🎬 Clip sẽ thấy**
> "Trong clip, thầy cô sẽ thấy nhóm em chạy hoạch định thật và xem kết quả thiếu hụt hiện ra."

**❓ Phòng khi bị hỏi** *(không nói ra)*
- Công thức: `cần thêm = max(0, nhu cầu + tồn an toàn − (tồn khả dụng + hàng đang về))`
- Nổ định mức **theo từng cấp**, mỗi cấp truy vấn một lần → cây sâu bao nhiêu cũng không chậm.
- Cùng một vật tư xuất hiện dưới 2 sản phẩm cha thì **không** được cấp cùng một nguồn tồn cho cả hai
  — hệ thống ghi sổ phần đã dùng, dòng sau chỉ trừ từ phần còn lại.
- Món tự làm được nhưng **thiếu định mức** vẫn hiện ra, gắn nhãn **BLOCKED** kèm lý do, không im lặng
  bỏ qua.
- Lượt hoạch định là **bất biến** — sửa đơn hàng sau đó không viết lại lịch sử lượt chạy cũ.

---

# SLIDE 8 — Lệnh sản xuất: thao tác thế nào

**📊 Trên slide**
- Ảnh chính: **Chi tiết lệnh sản xuất** — nhìn rõ **bảng sẵn sàng vật tư** (cần / đã giữ / còn thiếu)
- Ảnh phụ: **Danh sách lệnh sản xuất** với các nhãn trạng thái nhiều màu
- Tiêu đề: *"Không đủ vật tư thì không cho chạy"*

**Các bước hiện trên slide:**
```
1  Mở lệnh sản xuất → xem bảng vật tư: cần gì, còn thiếu gì
2  Bấm "Giữ chỗ vật tư" → hệ thống tự tìm và giữ hàng trong kho
3  Đủ 100% → nút "Cho chạy" mới bật lên
4  Thiếu → lệnh chuyển sang BLOCKED, ghi rõ thiếu dòng nào
```

**🗣 Nói — FE (1 phút)**
> "Sau khi duyệt đề xuất, hệ thống tạo ra lệnh sản xuất. Người dùng mở lên sẽ thấy đầy đủ: sản phẩm
> gì, số lượng bao nhiêu, nhập vào kho nào, và **danh sách vật tư cần dùng**."
>
> *(chỉ vào bảng sẵn sàng vật tư)*
> "Phần quan trọng nhất là bảng này. Từng loại vật tư: **cần bao nhiêu, đã giữ chỗ được bao nhiêu,
> còn thiếu bao nhiêu**. Người dùng bấm một nút **Giữ chỗ vật tư**, hệ thống tự đi tìm hàng trong kho
> và giữ lại cho lệnh này — hàng đã giữ thì lệnh khác không lấy được nữa."
>
> "**Và đây là luật nghiêm nhất của màn hình này:** phải giữ đủ **100%** thì nút *Cho chạy* mới bật
> lên. Thiếu dù chỉ một loại, hệ thống chuyển lệnh sang trạng thái **BLOCKED** và ghi rõ thiếu dòng
> nào. Người lập kế hoạch nhìn danh sách là biết ngay lệnh nào đang kẹt vì vật tư — **không phải đi
> hỏi từng người**."
>
> *(chỉ vào ảnh danh sách)*
> "Ở màn hình danh sách, mỗi lệnh có nhãn màu theo trạng thái: nháp, đã lên kế hoạch, đang chạy, bị
> chặn, hoàn thành. Quản lý xưởng nhìn một lần là nắm được toàn bộ tình hình."

**🎬 Clip sẽ thấy**
> "Trong clip, thầy cô sẽ thấy nhóm em giữ chỗ vật tư và cho lệnh chạy — nút *Cho chạy* chuyển từ mờ
> sang bật khi đủ hàng."

**❓ Phòng khi bị hỏi** *(không nói ra)*
- Định mức và quy trình công nghệ được **copy thành dữ liệu riêng của lệnh**, không phải liên kết tới
  dữ liệu gốc → sửa định mức sau đó không đụng được vào lệnh cũ.
- Vòng đời: `DRAFT → PLANNED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED`, cộng nhánh `BLOCKED` và
  `CANCELLED`.
- Chọn lô để giữ chỗ theo **FEFO** — hàng hết hạn trước thì giữ trước, để giảm hao hụt.

---

# SLIDE 9 — Nhập kho & kiểm tra chất lượng: thao tác thế nào ⭐

> **Slide chốt của cả bài.** Nếu chỉ được giữ 1 slide trong nhóm 6–9 thì giữ slide này.

**📊 Trên slide**
- Ảnh chính: **Danh sách phiếu nhập kho** với các trạng thái duyệt
- Ảnh phụ (rất quan trọng): **Danh sách lô hàng** — hiện rõ dòng có **Tồn = 50 nhưng Khả dụng = 0**
- Tiêu đề: *"Hàng đã vào kho ≠ hàng dùng được"*

**Các bước hiện trên slide:**
```
1  Công nhân xuất vật tư và báo sản lượng: tốt / hỏng / làm lại
2  Công nhân tạo phiếu nhập kho → bấm Nộp        (kho CHƯA thay đổi)
3  Quản lý bấm Duyệt   → hàng vào kho ở trạng thái HOLD, khả dụng = 0
4  Bộ phận chất lượng phán quyết:
       ✅ ĐẠT     → hàng thành khả dụng + đơn hàng ghi "đã giao một phần"
       ❌ KHÔNG ĐẠT → hàng vẫn truy vết được nhưng không ai lấy ra dùng được
```

**🗣 Nói — BE (1.5 phút)**
> "Đây là đoạn cuối của vòng, và là chỗ nhóm em đầu tư kiểm soát chặt nhất."
>
> "**Bước 1** — công nhân xuất vật tư theo phần đã giữ chỗ, rồi báo kết quả sản xuất: bao nhiêu sản
> phẩm tốt, bao nhiêu phế phẩm, bao nhiêu phải làm lại, kèm thời gian thực tế."
>
> "**Bước 2** — công nhân tạo phiếu nhập kho và bấm Nộp. Xin lưu ý: **lúc này kho chưa thay đổi gì
> cả**. Phiếu chỉ đang chờ duyệt."
>
> "**Bước 3** — quản lý bấm Duyệt. Bây giờ hàng mới thực sự vào kho."
>
> *(chỉ vào ảnh lô hàng — đây là câu quan trọng nhất cả bài)*
> "**Và đây là điểm nhóm em muốn thầy cô chú ý nhất.** Nhìn dòng này: **tồn kho là 50, nhưng khả dụng
> bằng 0**. Hàng **đã nằm trong kho thật**, cân đo đong đếm được, nhưng **chưa ai được phép lấy ra
> dùng** — vì nó đang chờ kiểm tra chất lượng."
>
> "**Bước 4** — bộ phận chất lượng vào phán quyết. Đây là **một thao tác hoàn toàn riêng biệt, quyền
> riêng, người khác làm** — người duyệt nhận hàng không được tự xác nhận chất lượng hàng mình nhận."
>
> "Nếu **đạt**: con số khả dụng từ 0 nhảy lên 50, hàng dùng được ngay — **và cùng lúc đó**, đơn hàng
> của khách tự chuyển sang *đã giao một phần*. Nếu **không đạt**: hàng vẫn nằm đó, vẫn truy vết được,
> nhưng vĩnh viễn không ai lấy ra dùng được."
>
> *(câu kết)*
> "Đó là vòng khép kín: **từ nhu cầu của khách, qua sản xuất, qua chất lượng, rồi quay về chính đơn
> hàng đó** — không cần ai gõ tay cập nhật."

**🎬 Clip sẽ thấy**
> "Đây cũng chính là phần cao trào của clip: thầy cô sẽ thấy con số khả dụng nhảy từ 0 lên, và đơn
> hàng tự đổi trạng thái ngay sau đó."

**❓ Phòng khi bị hỏi** *(không nói ra)*
- Vòng đời phiếu: `DRAFT → PENDING_APPROVAL → APPROVED`, nhánh cụt `REJECTED`. **Chỉ bước duyệt mới
  sinh giao dịch tồn kho.**
- Duyệt phiếu và quyết định chất lượng là **hai quyền khác nhau**, gán được cho hai người khác nhau.
- *"Sao không gộp duyệt phiếu với kiểm tra chất lượng cho nhanh?"* → "Vì đó là hai câu hỏi khác nhau:
  *hàng có về đủ không* và *hàng có đạt chất lượng không*. Gộp lại là để một người vừa nhận vừa tự xác
  nhận chất lượng hàng mình nhận — mất kiểm soát nội bộ."

---

# SLIDE 10 — Future Work & mời xem demo

> 🔴 **ĐỌC KỸ TRƯỚC KHI LÀM SLIDE NÀY.**
> Bạn dự định ghi *"Routing, schedule, excel chưa hoàn thiện"*. Nhưng rà code thật thì **cả ba đều đã
> có ở backend**:
>
> | Mục | Trạng thái thật |
> |---|---|
> | **Routing (quy trình công nghệ)** | ✅ Backend đầy đủ — vòng đời nháp/hoạt động/ngừng, chụp ảnh lên lệnh sản xuất |
> | **Capacity / điều độ** | ✅ Backend có bảng năng lực theo tổ/ngày + cảnh báo quá tải *(chỉ cảnh báo, không tự dời lịch)* |
> | **Excel / nhập liệu** | ✅ Backend có module riêng: **42 file, 13 API** (tải mẫu → upload → kiểm tra → áp dụng → huỷ), có kiểm thử |
>
> ⇒ **Nói "chưa làm" là sai và rủi ro** — hội đồng mở tài liệu API là thấy ngay. Cách nói đúng bên dưới.

**📊 Trên slide** — 2 nhóm, **đừng gộp làm một**.

### Nhóm A — Đã có backend, đang hoàn thiện giao diện

| Module | Backend | Còn thiếu |
|---|---|---|
| Quy trình công nghệ (Routing) | ✅ API đầy đủ | Màn hình quản lý công đoạn |
| Bảng năng lực & điều độ | ✅ API + cảnh báo quá tải | Màn hình biểu đồ tải theo tổ/ngày |
| Nhập liệu từ Excel | ✅ Tải mẫu → upload → kiểm tra → áp dụng | Màn hình nhập liệu từng bước |
| Mua hàng | ✅ Nhà cung cấp → đề nghị mua → đơn mua → nhập hàng | Màn hình quy trình mua |

### Nhóm B — Chưa làm, để Capstone 3

| Ưu tiên | Hạng mục | Vì sao ưu tiên vậy |
|---|---|---|
| **1** | Hoàn thiện mua hàng + hàng đang trên đường về | Khép vòng đề xuất "đi mua", làm số hoạch định chính xác hơn |
| **2** | Giao hàng, hoá đơn, công nợ | Khép vòng bán hàng sau khi sản xuất xong |
| **3** | Tính giá thành & phân tích chênh lệch | Tận dụng dữ liệu số lượng/thời gian **đã thu thập sẵn** |
| **4** | Quản lý chất lượng chuyên sâu (vai trò QC riêng, phiếu kiểm) | Mở rộng cổng chất lượng hiện tại thành kiểm tra đo lường được |
| **5** | Tích hợp máy móc (MES/IoT), OEE, tối ưu lịch tự động | Xây trên nền chứng từ ERP đã ổn định |

**🗣 Nói (1.5 phút) — cách nói an toàn và trung thực:**
> "Nhóm em chia phần còn lại thành hai nhóm."
>
> "**Nhóm thứ nhất — backend đã xong, đang hoàn thiện giao diện.** Quy trình công nghệ, bảng năng lực
> sản xuất, nhập liệu từ Excel, và mua hàng — **API đã chạy được và có kiểm thử**, nhưng nhóm em chưa
> dựng xong màn hình nên **không đưa vào phạm vi trình bày hôm nay**. Nhóm em chọn trình bày đúng
> những gì chạy được **trọn vẹn cả hai đầu**, thay vì khoe API mà không có màn hình."
>
> "**Nhóm thứ hai — thực sự chưa làm**, để Capstone 3. Ưu tiên số một là hoàn thiện mua hàng, vì khi
> hệ thống biết có hàng đang trên đường về thì con số hoạch định mới sát thực tế. Sau đó là giao hàng
> và hoá đơn để khép vòng bán hàng, rồi tính giá thành — phần này thuận lợi vì **dữ liệu số lượng và
> thời gian nhóm em đã thu thập sẵn từ Capstone 2 rồi**, chỉ cần thêm đơn giá."
>
> *(câu tạo thiện cảm)*
> "Và một điều nhóm em muốn nói rõ: nhóm em **cố ý không giả lập dữ liệu máy móc**. Tuyên bố có tích
> hợp MES trong khi không có sẽ là mô tả sai hệ thống. Nhóm em chọn ranh giới trung thực, và để sẵn
> chỗ mở rộng."

**🎬 Câu chuyển sang clip demo — CHUẨN BỊ KỸ CÂU NÀY:**
> "Trên đây là phần trình bày của nhóm em. Để thầy cô thấy hệ thống chạy thật, nhóm em xin phép chiếu
> một **clip demo khoảng 5 phút**, đi trọn vẹn từ đơn hàng của khách cho tới lô thành phẩm đã qua kiểm
> tra chất lượng ạ."
>
> *(bấm chiếu clip — sau clip mới quay lại nhận câu hỏi)*

**❓ Phòng khi bị hỏi**
- *"Backend làm rồi sao không demo luôn?"* → "Vì demo một API bằng công cụ kỹ thuật thì không chứng
  minh được người dùng thật dùng được. Nhóm em ưu tiên trình bày phần **trọn vẹn cả backend lẫn giao
  diện**. Nếu thầy cô muốn kiểm chứng, em có thể mở tài liệu API ngay ạ."
  *(thủ sẵn Swagger ở một tab: `http://localhost:8080/swagger-ui.html`)*
- *"Vì sao ưu tiên mua hàng trước?"* → "Vì hiện tại đề xuất *đi mua* chưa tạo được hàng-đang-về cam
  kết, nên lượt hoạch định sau vẫn tính là còn thiếu. Đóng mắt xích đó làm con số chính xác hơn hẳn."

> 💡 **Nếu muốn có slide kết thúc** *(tuỳ chọn — bạn không liệt kê nhưng nhiều hội đồng thích có)*:
> thêm 1 slide sau clip với đúng 3 dòng — *(1)* một vòng sản xuất chạy thật · *(2)* an toàn cấp doanh
> nghiệp ở quy mô nhỏ · *(3)* liên tục của bằng chứng từ đơn hàng tới thành phẩm — cộng lời cảm ơn.
> Không có cũng được, miễn là sau clip có người nói *"Nhóm em xin cảm ơn và sẵn sàng nhận câu hỏi ạ."*

---

# Phụ lục A — Q&A: ai trả lời câu gì

> **Quy tắc:** người phụ trách mảng đó trả lời, hai người kia **không chen ngang**. Không chắc thì nói
> *"Dạ chỗ này em chưa kiểm chứng kỹ, em xin phép trả lời sau"* — tốt hơn đoán bừa.
>
> 🔴 **Lưu ý riêng cho buổi này:** vì slide 6–9 đã bỏ phần kỹ thuật, **hội đồng nhiều khả năng sẽ hỏi
> bù ở Q&A**. BE nên đọc lại bảng "Phòng khi bị hỏi" của slide 5, 7, 9 trước khi vào phòng.

| Câu hỏi | Ai | Trả lời ngắn |
|---|---|---|
| Vì sao monolith mà không microservices? | BE | Thao tác chạm nhiều module phải là **một giao dịch ACID**; quy mô SME chưa cần chia tải; ranh giới module vẫn nghiêm nên sau tách được |
| Tồn kho có bao giờ sai/âm không? | BE | 3 lớp: ràng buộc ở database, sổ cái chỉ-ghi-thêm, validate trước khi ghi |
| Hai người sửa cùng lúc? | BE | Số phiên bản trên chứng từ — người đến sau bị từ chối `409`, không ghi đè âm thầm |
| Mạng lỗi bấm 2 lần? | BE | Khoá chống trùng lưu **trên chứng từ + ràng buộc duy nhất ở database**, gửi lại trả về chứng từ cũ |
| MRP tính thế nào? | BE | `cần thêm = max(0, nhu cầu + tồn an toàn − (khả dụng + hàng đang về))`, nổ định mức theo từng cấp |
| Sao biết hệ thống đúng chứ không chỉ chạy được? | BE | **Cố tình phá một dòng code rồi chạy lại test** để xác nhận đúng test nào phải đỏ; test tích hợp chạy PostgreSQL thật |
| **Routing / Excel / Capacity làm chưa?** | BE | ✅ **Backend đã có**, chưa dựng UI — xem Slide 10 |
| Frontend có tự bảo mật được không? | FE | Không — FE lo trải nghiệm; backend kiểm lại 100%, bỏ qua giao diện cũng không phá được luật |
| Người dùng thật dùng được không? | FE | Thiết kế cho desktop/tablet, song ngữ EN/VI, giữ dữ liệu khi lỗi, bắt xác nhận cho thao tác nguy hiểm |
| Khác gì Odoo/ERPNext? | Người A | Không cạnh tranh độ rộng — ít khái niệm hơn nhưng **luật của từng khái niệm rõ ràng** |
| Triển khai thật còn thiếu gì? | Người A | Theo thứ tự Slide 10 nhóm B, ưu tiên mua hàng |
| Đóng góp từng người? | Mỗi người | Chuẩn bị **1 câu** cho mình, không ai nói hộ ai |

---

# Phụ lục B — Kịch bản clip demo 5 phút

> Clip nên **có phụ đề hoặc thuyết minh**, và **hiện đồng hồ mốc thời gian** để hội đồng theo dõi.
> Quay ở độ phân giải đủ đọc được chữ trên bảng (tối thiểu 1080p), **phóng to vùng số liệu quan trọng**.

| Mốc | Nội dung | Phải thấy rõ trên màn hình |
|---|---|---|
| **0:00–0:25** | Đăng nhập bằng tài khoản quản lý, chọn nhà máy | Menu hiện theo quyền, tên nhà máy đang chọn |
| **0:25–0:50** | Lướt nhanh dữ liệu nền: vật tư + **cây định mức nhiều cấp** | Cây BOM mở rộng ra nhiều tầng — ảnh ấn tượng nhất |
| **0:50–1:20** | Mở đơn hàng bán → bấm **Chốt đơn** | Trạng thái đổi từ *Nháp* → *Đã chốt* |
| **1:20–2:20** | Vào hoạch định → chọn nhu cầu → **Chạy hoạch định** → xem kết quả | Danh sách yêu cầu, **cột thiếu hụt**, nhãn *TỰ LÀM* / *ĐI MUA* |
| **2:20–3:00** | Duyệt đề xuất tự làm → chuyển thành **lệnh sản xuất** | Lệnh mới sinh ra với đúng sản phẩm/số lượng, không gõ tay |
| **3:00–3:40** | Mở lệnh → **Giữ chỗ vật tư** → bảng sẵn sàng đủ 100% → **Cho chạy** | Nút *Cho chạy* chuyển từ mờ sang bật |
| **3:40–4:15** | **Xuất vật tư** → **Báo sản lượng** (tốt / hỏng / làm lại) | Tồn kho vật tư giảm; số lượng thực tế của lệnh tăng |
| **4:15–4:45** | Tạo & **Nộp** phiếu nhập kho → quản lý **Duyệt** → mở danh sách lô | ⭐ **Tồn = 50 nhưng Khả dụng = 0** — zoom vào đúng chỗ này |
| **4:45–5:00** | **Phán quyết chất lượng: ĐẠT** → xem lại lô và đơn hàng | ⭐ Khả dụng **nhảy từ 0 lên 50**; đơn hàng đổi sang *Đã giao một phần* |

**🔴 Hai khoảnh khắc quyết định của clip** — nếu quay hỏng phần nào thì quay lại phần đó:
1. **4:15–4:45** — dòng *Tồn 50 / Khả dụng 0*. Nên **dừng khung hình 2 giây** và khoanh đỏ.
2. **4:45–5:00** — con số khả dụng nhảy lên **cùng lúc** đơn hàng đổi trạng thái. Nếu quay được **hai
   màn hình cạnh nhau** hoặc cắt qua lại nhanh giữa hai màn thì càng thuyết phục.

**Chuẩn bị trước khi quay:**
- [ ] Dùng bộ dữ liệu demo có tên tiếng Việt (nhà máy xe đạp) — dễ hiểu hơn mã hàng khô khan
- [ ] Đăng nhập sẵn, tắt thông báo hệ thống, ẩn thanh bookmark trình duyệt
- [ ] Chạy thử **trọn kịch bản một lần trước khi bấm quay** — tránh gặp lỗi giữa chừng phải cắt ghép
- [ ] Xuất clip ra **file MP4 để sẵn trong máy**, đừng phụ thuộc mạng/YouTube

---

# Phụ lục C — Co giãn thời lượng

| Có | Làm gì |
|---|---|
| **10 phút nói + clip** | Slide 3 rút còn 2 khối (bỏ đối tượng + so sánh, để dành Q&A); slide 5 còn 2 ý (bỏ ý kiến trúc); gộp slide 6 vào slide 8 |
| **12 phút nói + clip** | Slide 3 bỏ khối so sánh; slide 8 rút còn 40 giây |
| **15 phút nói + clip** | Dùng nguyên bản này |
| **Nếu bị cắt giờ đột ngột** | Giữ bằng mọi giá: **slide 3, 5, 9** + clip. Ba slide đó là bài toán, thiết kế, và điểm chốt |

**Slide dự phòng — chuẩn bị nhưng KHÔNG trình bày, chỉ mở khi bị hỏi:**
- Sơ đồ ERD 6 module cơ sở dữ liệu
- Sơ đồ trạng thái đầy đủ của lệnh sản xuất và phiếu nhập kho
- Bảng phân quyền chi tiết theo vai trò
- Số liệu kiểm thử: FE **59 file / 265 test** · BE **1.025 test đơn vị + 124 test tích hợp / 18 lớp**

---

# Phụ lục D — Checklist trước buổi

**Nội dung**
- [ ] Cả 3 người đọc hết file, biết mình nói slide nào và **câu chuyển** của mình
- [ ] 🔴 Thống nhất cách nói **Slide 10** — cả 3 phải giống nhau, tuyệt đối tránh người này nói "chưa
      làm" người kia nói "làm rồi"
- [ ] Điền vai trò của An và Cương vào **Slide 2**
- [ ] BE đọc lại bảng *"Phòng khi bị hỏi"* của slide 5, 7, 9 — vì slide 6–9 đã bỏ phần kỹ thuật nên
      hội đồng sẽ hỏi bù ở Q&A
- [ ] Chèn 2 diagram vào **Slide 5**; chèn ảnh màn hình vào **Slide 6–9**

**Kỹ thuật**
- [ ] **Clip demo đã xuất ra file MP4 và thử phát trên máy sẽ dùng** *(quan trọng nhất)*
- [ ] Slide xuất **PDF dự phòng**
- [ ] Font tiếng Việt hiển thị đúng trên máy sẽ dùng — thử trước, đừng tin "chắc là được"
- [ ] Ảnh màn hình đủ nét khi chiếu — **1 ảnh chính + tối đa 1 ảnh phụ mỗi slide**
- [ ] Thủ sẵn tab Swagger phòng khi bị hỏi về API chưa có giao diện (Slide 10)

**Diễn tập**
- [ ] Chạy thử **có bấm giờ ít nhất 2 lần** — lần 1 biết vượt bao nhiêu, lần 2 sửa
- [ ] Tập **câu chuyển sang clip** ở cuối slide 10 — đây là chỗ dễ vấp và dễ mất nhịp nhất
- [ ] BE tập riêng **slide 5** (kỹ thuật) và **slide 9** (điểm chốt) — hai slide nặng ký nhất

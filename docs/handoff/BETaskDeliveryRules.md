# Nguyên Tắc BE Giao Task Cho FE

**Phiên bản:** v1 · **Ngày:** 2026-10-12 · **Đối tượng:** dev Backend của dự án Manufacturing ERP.

> **Bối cảnh áp dụng:** FE và BE **không dùng chung repo**. Mỗi lần giao việc, BE gửi **một file `.md`
> qua Zalo**. Bên nhận không mở được code, không mở được tài liệu nội bộ, không thấy commit của BE.
>
> ⇒ **Nguyên tắc gốc, suy ra mọi nguyên tắc còn lại: file gửi đi phải TỰ CHỨA.** Mọi thứ FE cần để
> code và nghiệm thu phải nằm **trong** file đó. Không trỏ đường dẫn repo, không "xem code sẽ rõ",
> không "như đã nói hôm trước".

---

## 1. Năm nguyên tắc không được vi phạm

| # | Nguyên tắc | Vì sao |
|---|---|---|
| **B1** | **Tự chứa.** Không link `docs/...`, `src/main/...`, `.claude/rules/...`, không link commit/PR. Cần quy ước nào thì **copy nguyên văn phần liên quan** vào file | FE không có repo. Một link chết là FE phải nhắn hỏi lại, mất nửa ngày |
| **B2** | **Mọi mẫu request/response phải copy từ một lần gọi THẬT**, không viết tay | Mẫu viết tay sai field, sai kiểu, sai cấu trúc lồng — FE code theo rồi mới phát hiện |
| **B3** | **Breaking change ghi TỪNG CÁI, hoặc ghi tường minh "wire: KHÔNG có"** | Im lặng buộc FE tự đi kiểm lại toàn bộ màn hình |
| **B4** | **Nói rõ cái gì CHƯA nghiệm thu** và vì sao | Báo xong trong khi chưa đo là tạo nợ ẩn; FE tin rồi lên demo mới phát hiện |
| **B5** | **Không tự quyết hộ FE.** FE hỏi một câu thì trả lời đúng câu đó; muốn làm khác thì nêu phương án + lý do rồi **chờ** | Tự đổi hướng rồi mới báo là ép FE sửa lại việc đã làm |

---

## 2. Quy ước file và gửi qua Zalo

### 2.1 Loại file và cách đặt tên

| Loại | Tên file | Khi nào |
|---|---|---|
| Trả lời một task của FE | `BE_RESPONSE_<CHỦ_ĐỀ>_<YYYY-MM-DD>.md` | FE báo lỗi / xin field, BE làm xong |
| Bàn giao contract mới | `BE_HANDOFF_<CHỦ_ĐỀ>_<YYYY-MM-DD>.md` | Thêm endpoint / module mới |
| Thông báo breaking change | `BE_BREAKING_<CHỦ_ĐỀ>_<YYYY-MM-DD>.md` | Đổi field/mã lỗi/bắt buộc mới, **gửi trước khi deploy** |
| Hướng dẫn dùng lâu dài | `BE_GUIDE_<CHỦ_ĐỀ>_<YYYY-MM-DD>.md` | Luồng nhiều bước FE sẽ tra lại nhiều lần |

- Ngày là **ngày gửi**. Sửa nội dung sau khi đã gửi ⇒ tên file thêm `_v2`, `_v3`; **không** gửi lại
  file cùng tên với nội dung khác (Zalo không cho FE biết file nào mới hơn).
- **Một task = một file.** Gộp ba chủ đề vào một file thì FE không có cách nào đóng từng phần.
- Tên file **không dấu, không khoảng trắng** — Zalo trên một số máy đổi tên file có dấu.

### 2.2 Tin nhắn đi kèm file *(bắt buộc, 4 dòng)*

File đính kèm một mình dễ bị trôi trong hội thoại. Luôn gửi kèm:

```
[BE→FE] <Chủ đề>
Mức: P0/P1/P2/P3 · Breaking: CÓ / KHÔNG
Cần FE làm: <một dòng>
File: <tên file .md>
```

Có breaking change ⇒ thêm một dòng: `Thứ tự deploy: BE trước / FE trước / không ràng buộc`.

### 2.3 Ảnh và dữ liệu

- JSON/log **phải** để trong code block trong file `.md`, **không** gửi ảnh chụp màn hình JSON — FE
  không copy được, và ảnh bị Zalo nén mờ số/ký tự.
- Che secret trước khi gửi: `Authorization`, password, refresh token, chuỗi kết nối DB. Giữ lại
  `X-Trace-Id` (không phải secret, và FE cần nó để đối chiếu).
- File lớn (dump dữ liệu, export OpenAPI) ⇒ gửi file riêng, trong `.md` chỉ ghi tên file đó.

---

## 3. Dàn ý bắt buộc của file BE gửi FE

Tám mục dưới đây là **tối thiểu**. Mục nào không có nội dung thì ghi "không có", **không xoá mục** —
FE đọc theo thứ tự và việc thiếu mục khiến họ không biết là "không có" hay "bị quên".

### §0 — Kết luận trong một câu, và bảng phân loại

Nếu đang trả lời task của FE, phân loại **thẳng từng mục họ báo**, không viết văn mở đầu:

| # | FE báo | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| 1 | ... | 🔴 **Bug thật, đã sửa** | ... |
| 2 | ... | 🟡 **Thiếu thật, đã làm** | ... |
| 3 | ... | ✅ **Báo nhầm, không sửa dòng nào** | ... |

🔴 **"Báo nhầm" phải kèm DỮ LIỆU, không kèm lập luận.** Nói FE sai mà không có bằng chứng là mở thêm
một vòng tranh luận. Dạng bằng chứng đạt: "bản ghi các bạn trích dẫn (`id=...`) thuộc lượt chạy khác;
bản ghi đúng là `id=...`, trạng thái của nó là ...".

Nếu FE chẩn đoán đúng triệu chứng nhưng sai nguyên nhân, nói rõ chỗ lệch — FE còn có lớp phòng vệ phía
họ phụ thuộc vào chẩn đoán đó.

### §1 — Nguyên nhân thật *(ngắn, 5–10 dòng)*

Viết để FE hiểu **phạm vi ảnh hưởng**, không phải để trình bày kỹ thuật nội bộ. Trả lời đúng ba câu:
nó sai từ bao giờ · còn endpoint/màn hình nào khác bị cùng lỗi không · dữ liệu cũ có bị ảnh hưởng không.

Không cần tên class, tên bảng, tên migration — FE không đọc được và không dùng được.

### §2 — Thay đổi contract: bảng trước / sau

Mỗi field hoặc endpoint bị đụng **một dòng**, kể cả khi chỉ thêm:

| Field / Endpoint | Trước | Sau | Nullable? | Phá client đang chạy? |
|---|---|---|---|---|

Field mới ⇒ bắt buộc đủ 5 cột: **tên · kiểu · nullable? · nguồn giá trị · ví dụ giá trị thật**.
"Nguồn giá trị" là mục FE hay phải hỏi lại nhất — ví dụ `shortageQuantity` tính theo công thức nào,
`uomCode` lấy từ đâu khi item chưa khai đơn vị.

Đổi **số liệu / công thức** ⇒ nêu công thức mới **và một ví dụ số**, không chỉ nói "đã sửa cho đúng".

### §3 — Breaking change

Mở đầu bằng đúng một dòng: **`Wire: CÓ`** hoặc **`Wire: KHÔNG có`**.

Có thì ghi từng cái, không gộp:

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|

Ba loại phải tách bạch vì rủi ro khác nhau hoàn toàn:

| Loại | Nghĩa | FE phải làm |
|---|---|---|
| **Nới lỏng** | request trước đây lỗi nay thành công | thường không phải sửa gì |
| **Đổi mã / đổi kiểu** | cùng request, `code` hoặc kiểu dữ liệu đổi | **phải** sửa, chỉ rõ chỗ nào |
| **Bắt buộc mới** | thêm field/header bắt buộc | **phải** sửa **trước** khi BE deploy |

🔴 **Ba thứ hay bị quên nhưng vẫn là breaking change:** đổi **giá trị mặc định** của query param · đổi
**thứ tự sắp xếp** mặc định · **bỏ** một field dù FE "chắc chưa dùng". Cả ba phải khai ở mục này.

Có "bắt buộc mới" ⇒ nêu **thứ tự deploy** và khoảng thời gian BE chấp nhận cả hai dạng (nếu có).

### §4 — Cách dùng: ví dụ copy từ lần gọi thật

Mỗi endpoint mới/đã sửa cần **ba** khối, lấy từ một lần gọi thật:

1. **Request** — method + path **đầy đủ**, header liên quan, body.
2. **Response thành công** — nguyên văn, kèm HTTP status.
3. **Một nhánh lỗi** — tình huống xấu hay gặp nhất.

Kèm **đường dẫn đầy đủ**, không viết tắt. 🔴 **Bố cục URL của hệ thống này là HỖN HỢP** — phần lớn là
`/api/v1/<tài-nguyên>`, nhưng auth/users/access/sales-orders đặt `v1` **sau** tên tài nguyên
(`/api/auth/v1/login`, `/api/sales-orders/v1`, `/api/access/v1/roles`). Đoán sai đường dẫn **không**
trả 404 mà trả **401**, tức trông hệt như sai mật khẩu. Vì vậy path trong file luôn viết đủ, copy từ
lần gọi thật.

### §5 — Envelope và hợp đồng lỗi *(copy vào file, đừng trỏ link)*

Mọi response — **thành công và lỗi** — dùng cùng một envelope:

```json
{
  "code":    "SUCCESS",
  "result":  { },
  "message": "OK",
  "errors":  [ { "field": "quantity", "message": "must be greater than 0" } ]
}
```

- `code` là **tên hằng**, FE rẽ nhánh theo nó.
- `errors` chỉ xuất hiện khi có lỗi theo field; một field có thể xuất hiện nhiều lần.
- Danh sách phân trang: `result` là `{ content, page, size, totalElements, totalPages, first, last }`.

🔴 **Luôn liệt kê `code` cho từng tình huống xấu, không chỉ HTTP status** — hệ thống này có nhiều mã
khác nhau dùng **chung một status**. Ví dụ `POST /auth/.../refresh` trả **401** ở cả ba trường hợp,
và FE phải nói ba câu khác nhau với người dùng:

| `code` | Nghĩa | FE nên hiển thị |
|---|---|---|
| `REFRESH_TOKEN_EXPIRED` | hết hạn tự nhiên | "Đăng nhập lại" |
| `SESSION_ABSOLUTE_TIMEOUT` | hết hạn theo chính sách | "Phiên đã hết hạn" — **không** phải cảnh báo bảo mật |
| `TOKEN_REUSE_DETECTED` | phát hiện dùng lại ⇒ **mọi phiên đã bị thu hồi** | cảnh báo bảo mật rõ ràng |

Bảng lỗi cho mỗi endpoint mới:

| Tình huống | HTTP | `code` | Có `errors[]`? |
|---|---|---|---|

### §6 — Quyền, phạm vi và giới hạn đã biết

- Permission nào gọi được từng endpoint.
- Role nào đã được cấp sẵn (ADMIN / MANAGER / OPERATOR).
- Phạm vi ảnh hưởng ra sao (company / plant / warehouse).

🔴 **Giới hạn đã biết phải ghi ở đây, không để FE gặp lần đầu trên sân khấu demo.** Ví dụ thật: tài
khoản được gán phạm vi `PLANT` vẫn bị **403** ở một số endpoint cấp toàn hệ thống — đó là đặc điểm có
sẵn, không phải bug của task; FE không biết sẽ báo lỗi oan và mất một lượt trao đổi.

### §7 — Bằng chứng nghiệm thu

| Loại | Phải ghi gì |
|---|---|
| Test tự động | lệnh đã chạy + số case + `failures` / `errors` |
| Kiểm chứng qua HTTP thật | từng probe: request → kết quả thật, kèm `X-Trace-Id` |
| Môi trường | dev/demo, DB thật hay container, dữ liệu nào đã dùng, **giờ đo** |

🔴 **Phần "chưa nghiệm thu" là mục bắt buộc**, không phải mục tuỳ chọn. Ghi thẳng: cái gì chưa đo, vì
sao, và cần gì để đo được.

🔴 **Khi đo A/B (trước sửa / sau sửa), phải chứng minh đúng bản build đang chạy** bằng cách đo thêm một
**field mốc** đã biết chắc khác nhau giữa hai bản. Dự án này đã hai lần suýt kết luận sai vì tiến
trình cũ vẫn giữ cổng và cả hai lượt đo bắn vào **cùng một** build.

### §8 — Chưa làm (có chủ đích) + việc FE cần làm tiếp

**Chưa làm:**

| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|

Không có mục nào thì ghi "không có". Đây là chỗ ngăn FE tưởng bị quên rồi mở lại task y hệt lần sau.

**Việc FE cần làm tiếp** — checklist đánh dấu được, chia **bắt buộc** / **nên làm**, kèm thứ tự deploy.

---

## 4. Checklist tự kiểm trước khi bấm gửi

- [ ] File **không** chứa link repo / đường dẫn code / tham chiếu commit
- [ ] Mọi mẫu request + response **copy từ lần gọi thật**, kèm path đầy đủ
- [ ] Mỗi endpoint có **một ví dụ nhánh lỗi**
- [ ] Field mới đủ 5 cột: tên · kiểu · nullable · nguồn giá trị · ví dụ
- [ ] Mục §3 mở đầu bằng `Wire: CÓ` hoặc `Wire: KHÔNG có`
- [ ] Đã rà ba thứ hay quên: mặc định query param · thứ tự sắp xếp · field bị bỏ
- [ ] Bảng lỗi nêu theo `code`, không chỉ HTTP status
- [ ] Đã nêu permission + role + **giới hạn đã biết**
- [ ] §7 có số case, môi trường, giờ đo, và mục **chưa nghiệm thu**
- [ ] §8 có "chưa làm có chủ đích" (hoặc ghi "không có") + checklist cho FE
- [ ] Tên file không dấu, có ngày, `_v2` nếu là bản sửa
- [ ] Đã viết tin nhắn 4 dòng đi kèm (§2.2)
- [ ] Đã che secret; `X-Trace-Id` **giữ lại**
- [ ] Tám mục §0–§8 đều có mặt (mục trống ghi "không có")

---

## 5. Bảy lỗi BE đã mắc thật — đừng lặp lại

| # | Lỗi | Hệ quả đã xảy ra | Cách tránh |
|---|---|---|---|
| 1 | **Đọc bảng câu hỏi của FE như danh sách việc** | FE gửi file phần lớn là xin xác nhận contract; BE tưởng mọi ô là một thay đổi code | Phân loại trước khi code: mục nào cần code, mục nào chỉ cần trả lời |
| 2 | **Tuyên bố "đã xong hết"** rộng hơn phạm vi thật đã rà | Lặp **ba lần** trong dự án; mỗi lần sinh ra một task vá lại | Ghi **đã rà tới đâu**. Không dùng "hết", "toàn diện", "xong" |
| 3 | **Đổi kiểu dữ liệu của một field cùng lượt bật tính năng mới** | Một field có **năm** kiểu runtime khác nhau; màn hình FE sập | Đổi kiểu là breaking change độc lập, phải khai riêng ở §3 |
| 4 | **Sửa một loại lỗi, bỏ các loại anh em của nó** | Path không tồn tại trả **500** suốt nhiều tháng sau khi đã vá một lỗi cùng họ | Vá xong thì rà cả nhóm, và ghi vào §2 những gì cùng nhóm đã rà |
| 5 | **Báo xong khi phần integration test chưa chạy được** | Nợ ẩn, phát hiện ở lượt sau | §7 bắt buộc có mục "chưa nghiệm thu" |
| 6 | **Để tài liệu lệch code** | Tài liệu từng mô tả **hệ quả của một lỗi** như thể đó là tính năng ⇒ không ai đọc ra là contract đã bị phá | Contract đổi thì cập nhật tài liệu trong **cùng lượt**, không hoãn |
| 7 | **Tự quyết thay FE một thay đổi toàn hệ thống** | Suýt xoá mọi field `null` khỏi mọi response trong khi FE chỉ xin đổi định dạng ngày | Thay đổi vượt phạm vi câu hỏi ⇒ nêu phương án, **chờ** FE chốt |

---

## 6. Việc BE phải làm sau khi gửi

1. **Lưu bản vừa gửi vào repo BE** (cùng tên file) — Zalo không phải nơi lưu trữ, hội thoại sẽ trôi.
2. **Cập nhật tài liệu contract nội bộ** nếu wire đổi — đừng để lượt sau phải đọc lại file Zalo mới
   biết hiện trạng.
3. **Chờ FE xác nhận đã nhận.** Task chỉ đóng khi **FE** nghiệm thu trên môi trường của họ và nói đạt
   — BE nói "xong", FE nói "đạt", hai việc khác nhau.
4. FE trả lời là chưa đạt ⇒ **không gửi file mới**, trả lời trong cùng mạch hội thoại và gửi bản `_v2`
   của đúng file đó.

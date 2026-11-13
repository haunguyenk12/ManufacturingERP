# Dàn Ý Bàn Giao Task Giữa FE Và BE

> **File này là khuôn mẫu, không phải task.** Mỗi lần một bên giao việc cho bên kia, copy đúng
> template ở §7 hoặc §8, điền vào, lưu thành file mới. **Không** sửa file này để ghi nội dung task.
>
> **Mục tiêu duy nhất:** người nhận đọc một lượt là biết **(a)** phải làm gì, **(b)** đầu ra được coi
> là đạt khi nào, **(c)** cái gì *không* thuộc phạm vi — mà không phải nhắn hỏi lại hay đi đọc code
> của bên kia.

---

## 1. Cách đọc và cách dùng

| Bạn là | Gửi task đi thì dùng | Nhận task thì đọc |
|---|---|---|
| **FE** | §3 (FE → BE) + template §7 | §4 để biết BE sẽ trả về cái gì |
| **BE** | §4 (BE → FE) + template §8 | §3 để biết FE đang hỏi gì |

Trước khi viết, đọc §6 — danh sách những chỗ **đã từng gây hiểu nhầm thật** trong dự án này. Phần lớn
task bị trả lại không phải vì thiếu chi tiết kỹ thuật mà vì thiếu đúng một trong các mục ở đó.

---

## 2. Quy ước chung cho mọi task

### 2.1 Đặt tên và vị trí file

| Hướng | Tên file | Ở đâu |
|---|---|---|
| FE → BE, báo lỗi | `FE_DEFECT_<CHỦ_ĐỀ>_<YYYY-MM-DD>.md` | gốc repo |
| FE → BE, yêu cầu tính năng / contract | `FE_REQUEST_<CHỦ_ĐỀ>_<YYYY-MM-DD>.md` | gốc repo |
| FE → BE, bàn giao cả giai đoạn | `FE_HANDOFF_<GIAI_ĐOẠN>_<YYYY-MM-DD>.md` | gốc repo |
| BE → FE, trả lời một task | ghi vào `FE_SingleTask_Response.md` *(ghi đè nội dung cũ, **giữ** dòng đầu tiên)* | gốc repo |
| BE → FE, hướng dẫn dùng lâu dài | `docs/fe-guide-<chủ-đề>.md` | `docs/` |
| BE → FE, bàn giao cả giai đoạn | `BE_HANDOFF_<GIAI_ĐOẠN>_<YYYY-MM-DD>.md` | gốc repo |

Ngày trong tên file là **ngày gửi**, không phải ngày bắt đầu làm. Một chủ đề mở lại lần hai thì tạo
file mới với ngày mới, **không** sửa đè file cũ — lịch sử hai bên nói gì phải tra ngược được.

### 2.2 Vòng đời một task

```
Bên gửi: viết file → tự chạy checklist §5 → thông báo link file
   ↓
Bên nhận: đọc → trả lời TỪNG mục "Cần xác nhận" → còn mục chưa rõ thì CHƯA code
   ↓
Bên nhận: làm → tự nghiệm thu theo "Đầu ra mong đợi" của chính file task
   ↓
Bên nhận: trả lời bằng file theo §2.1 → ghi rõ mục nào ĐÃ làm / KHÔNG làm / BÁO NHẦM
   ↓
Bên gửi: nghiệm thu lại trên môi trường của mình → đóng task
```

🔴 **Không bên nào được đóng task thay bên kia.** Bên làm nói "xong", bên gửi mới nói "đạt".

### 2.3 Mức ưu tiên — dùng đúng 4 mức này, không tự thêm

| Mức | Nghĩa | Ví dụ thật trong dự án |
|---|---|---|
| **P0** | Chặn hoàn toàn, không có đường đi vòng | `/auth/refresh` trả 401 ⇒ không ai giữ được phiên |
| **P1** | Một màn hình/luồng không dùng được, phần còn lại vẫn chạy | `PATCH /sales-orders/{id}` kèm `lines[]` luôn lỗi |
| **P2** | Dùng được nhưng sai số/sai nhãn, hoặc phải gọi thêm API để bù | thiếu nhãn `itemName` ⇒ FE phải gọi N+1 |
| **P3** | Cải thiện, không chặn ai | thêm filter tiện lợi, đổi mặc định phân trang |

### 2.4 Ba quy tắc về nội dung, áp cho cả hai chiều

1. **Một sự thật, một chỗ.** Đã có `docs/api-guide-for-frontend.md` thì task chỉ **trích dẫn**, không
   copy lại định nghĩa — hai bản copy chắc chắn sẽ lệch nhau.
2. **Phân biệt rõ ba loại câu:** *đã kiểm chứng* · *đang giả định* · *đang đề xuất*. Viết lẫn ba loại
   này là nguyên nhân số một của việc bên kia làm sai hướng.
3. **Mọi con số phải nói rõ đo ở đâu.** "Trả về 0" khác hẳn "trả về 0 trên bản ghi `lotId=abc`, môi
   trường dev, 14:02 ngày 2026-10-12".

---

## 3. Dàn ý task FE → BE

> Dùng khi FE cần backend sửa lỗi, thêm field, thêm endpoint, hoặc xác nhận một contract.

### 3.1 Kết luận trong 3 dòng *(bắt buộc, viết đầu file)*

Người đọc chỉ có 30 giây đầu. Ba dòng phải trả lời: **cái gì sai/thiếu** · **ai bị ảnh hưởng** ·
**mức ưu tiên**. Không mở đầu bằng bối cảnh dự án.

### 3.2 Phạm vi

- **Trong phạm vi:** liệt kê màn hình / endpoint / field cụ thể.
- **Ngoài phạm vi:** liệt kê thứ *gần giống* mà BE có thể tiện tay làm luôn — đây là mục ngăn trượt
  phạm vi; bỏ nó là mời BE đoán.
- **Ai dùng đầu ra:** màn hình nào, có đang chạy production/demo hay không.

### 3.3 Bằng chứng tái hiện *(mục quan trọng nhất của một báo lỗi)*

Mỗi lỗi phải có đủ **5 mảnh**; thiếu một mảnh là BE phải đoán:

| # | Mảnh | Ví dụ |
|---|---|---|
| 1 | Request thật | method + path đầy đủ + header liên quan + body (đã che secret) |
| 2 | Response thật | **nguyên văn**, kèm HTTP status và `code` trong envelope |
| 3 | `X-Trace-Id` | copy từ response header — BE tra log bằng chính giá trị này |
| 4 | Môi trường + thời điểm | dev/demo/prod, ngày giờ, id bản ghi cụ thể đã dùng |
| 5 | Mong đợi vs thực tế | hai dòng cạnh nhau, không phải một đoạn văn |

🔴 **Nêu id bản ghi thật đã dùng, không chỉ nêu triệu chứng.** Đã có lần FE báo "convert suggestion
không atomic" nhưng bản ghi họ trích dẫn thuộc một MRP run **khác** — hai câu truy vấn trên đúng id
đó là đủ để thấy không có lỗi nào. Thiếu id thì cả hai bên tranh luận trên suy đoán.

### 3.4 Đầu ra mong đợi *(viết dưới dạng kiểm được, không phải mô tả)*

Mỗi dòng phải là một câu **đúng/sai kiểm được**, không phải tính từ:

```
❌ "API trả dữ liệu đầy đủ hơn"
✅ "GET /v1/inventory/lots?warehouseId=X trả thêm uomCode (string, không null khi item có unit)"

❌ "Xử lý lỗi tốt hơn"
✅ "Thiếu query param bắt buộc ⇒ HTTP 400, code = VALIDATION_ERROR, errors[0].field = tên param"
```

- Yêu cầu là **field mới** ⇒ lập bảng từng field: tên · kiểu · nullable? · nguồn giá trị · ví dụ.
- Yêu cầu là **endpoint mới** ⇒ method · path đề xuất · query/body · hình dạng response · có phân
  trang hay không · ai được gọi (role/permission dự kiến).
- Yêu cầu là **sửa số liệu** ⇒ nêu **công thức** mong đợi và một ví dụ số, không chỉ nói "số sai".

### 3.5 Hợp đồng lỗi mong đợi

Liệt kê từng tình huống xấu và `code` mong đợi. **Rẽ nhánh theo `code`, không theo HTTP status** —
dự án này có nhiều mã khác nhau dùng chung một status (ví dụ ba mã đều trả **401** trên
`POST /auth/refresh`, status không phân biệt nổi). Xem `.claude/rules/error-handling.md §5.3`.

### 3.6 Cần BE xác nhận *(đánh số, mỗi mục một câu hỏi đóng)*

Mục này tồn tại để BE **không tự quyết hộ**. Quy tắc viết:

- Một câu hỏi = một quyết định. Không gộp "và/hoặc".
- Kèm **phương án FE nghiêng về** + lý do một dòng. Câu hỏi mở hoàn toàn thường bị bỏ qua.
- Ghi rõ câu nào **chặn** việc FE code tiếp, câu nào không.

### 3.7 Những gì FE đã tự xử lý

Ghi rõ FE đã thêm lớp phòng vệ nào (normalize, fallback, retry). BE cần biết để **không** sửa theo
cách làm lớp đó thành lỗi — và để FE biết khi nào lớp đó thành no-op có thể gỡ.

### 3.8 Tham chiếu

Link tới file spec / plan / task cũ liên quan, kèm **một dòng** nói link đó dùng để làm gì.

---

## 4. Dàn ý task BE → FE

> Dùng khi BE bàn giao kết quả, thông báo contract mới, hoặc trả lời một task của FE.

### 4.1 Kết luận trong một câu *(bắt buộc)*

Nếu đang trả lời một task của FE, **phân loại thẳng từng mục họ báo**, không viết văn:

| # | FE báo | Kết luận | Bằng chứng |
|---|---|---|---|
| 1 | ... | 🔴 **Bug thật, đã sửa** | ... |
| 2 | ... | 🟡 **Gap thật, đã làm** | ... |
| 3 | ... | ✅ **Báo nhầm, không sửa dòng nào** | ... |

🔴 **"Báo nhầm" phải kèm bằng chứng, không kèm lập luận.** Nói FE sai mà không có dữ liệu là mở ra
thêm một vòng tranh luận.

### 4.2 Thay đổi contract — bảng trước/sau

Mỗi field/endpoint bị đụng một dòng: **tên** · **trước** · **sau** · **nullable?** · **có phá client
đang chạy không**. Chỉ thêm field thì vẫn ghi, vì FE cần biết để dùng.

### 4.3 Breaking change *(mục FE đọc đầu tiên — không gộp, ghi từng cái)*

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|

Ba loại phải tách bạch, vì rủi ro khác nhau hoàn toàn:

| Loại | Nghĩa | FE phải làm |
|---|---|---|
| **Nới lỏng** | request trước đây lỗi nay thành công | thường không phải sửa gì |
| **Đổi mã / đổi kiểu** | cùng request, `code` hoặc kiểu dữ liệu đổi | **phải** sửa, nêu rõ chỗ nào |
| **Bắt buộc mới** | thêm field/header bắt buộc | **phải** sửa trước khi deploy |

Không có breaking change thì **ghi tường minh "wire: KHÔNG có"** — im lặng khiến FE phải tự đi kiểm.

### 4.4 Cách dùng — ví dụ chạy được

Mỗi endpoint mới/đã sửa: **một** request mẫu + **một** response mẫu, copy từ lần gọi thật (đã che
secret), không viết tay. Kèm **một ví dụ nhánh lỗi**.

### 4.5 Quyền và phạm vi

Permission nào gọi được, role nào được cấp, phạm vi (company/plant/warehouse) ảnh hưởng ra sao. Có
giới hạn đã biết (ví dụ tài khoản phạm vi `PLANT` bị 403 ở một endpoint global) thì **ghi ra đây**,
đừng để FE gặp lần đầu trên sân khấu demo.

### 4.6 Bằng chứng nghiệm thu

| Loại | Phải ghi gì |
|---|---|
| Test tự động | lệnh đã chạy + số case + `failures` / `errors` |
| Kiểm chứng thủ công | từng probe: request → kết quả thật, kèm `X-Trace-Id` |
| Môi trường | dev/demo, DB thật hay container, dữ liệu nào đã dùng |

🔴 **Nói rõ cái gì CHƯA nghiệm thu.** Đã có lần một task báo xong trong khi phần integration test
chưa chạy được (Docker tắt giữa chừng) — ghi ra là trung thực, bỏ qua là nợ ẩn.

### 4.7 Chưa làm, có chủ đích

Liệt kê mục FE xin mà BE **cố ý không** làm, **kèm lý do** và điều kiện để làm. Không có mục nào thì
ghi "không có". Đây là chỗ ngăn FE tưởng bị quên rồi mở lại task y hệt ở lượt sau.

### 4.8 Việc FE cần làm tiếp

Checklist đánh dấu được, chia **bắt buộc** / **nên làm**. Có thứ tự triển khai bắt buộc (BE deploy
trước hay FE deploy trước) thì nói rõ ở đây.

---

## 5. Checklist trước khi gửi

### 5.1 FE → BE

- [ ] 3 dòng kết luận ở đầu file, có mức ưu tiên P0–P3
- [ ] Có mục **Ngoài phạm vi**
- [ ] Mỗi lỗi có đủ 5 mảnh bằng chứng (§3.3), kèm `X-Trace-Id` và **id bản ghi thật**
- [ ] Mỗi đầu ra mong đợi là một câu **kiểm được**, không phải tính từ
- [ ] Field mới có bảng: tên · kiểu · nullable · nguồn · ví dụ
- [ ] Hợp đồng lỗi nêu theo `code`, không chỉ theo HTTP status
- [ ] Mục "Cần xác nhận" đánh số, mỗi mục kèm phương án FE nghiêng về
- [ ] Đã ghi rõ câu hỏi nào **chặn** FE code tiếp
- [ ] Đã phân biệt *đã kiểm chứng* / *đang giả định* / *đang đề xuất*

### 5.2 BE → FE

- [ ] Kết luận một câu; nếu trả lời task thì có bảng phân loại từng mục FE báo
- [ ] Bảng contract trước/sau cho mọi field bị đụng
- [ ] Mục breaking change ghi **từng cái**, hoặc ghi tường minh "KHÔNG có"
- [ ] Mỗi endpoint có request + response mẫu **copy từ lần gọi thật**, cộng một ví dụ lỗi
- [ ] Đã nêu permission/phạm vi và giới hạn đã biết
- [ ] Bằng chứng nghiệm thu có **số case + môi trường**, và nêu rõ cái gì **chưa** nghiệm thu
- [ ] Có mục "Chưa làm, có chủ đích" kèm lý do
- [ ] Checklist việc FE cần làm, chia bắt buộc / nên làm
- [ ] Đã cập nhật `docs/api-guide-for-frontend.md` nếu contract đổi *(xem `.claude/rules/dev-workflow.md §6.5`)*

---

## 6. Chín chỗ đã từng gây hiểu nhầm thật — đọc trước khi viết

| # | Cái bẫy | Hệ quả đã xảy ra | Cách tránh |
|---|---|---|---|
| 1 | **Task là bảng câu hỏi nhưng bị đọc như danh sách việc** | BE tưởng mọi ô `☐` là một thay đổi code; thực tế phần lớn chỉ cần trả lời bằng tài liệu | Tách hẳn hai mục: "Cần code" và "Chỉ cần xác nhận" |
| 2 | **Tuyên bố "đã xong hết" rộng hơn phạm vi thật** | Lặp **ba lần** trong dự án: nợ #9 (`D7`), spec FE (`F7`), bảng field (`F8`) | Luôn ghi **đã rà tới đâu**. Không dùng từ "hết", "toàn diện", "xong" |
| 3 | **Báo lỗi không kèm id bản ghi** | Tranh luận trên suy đoán; một mục hoá ra là báo nhầm | Bắt buộc nêu id + môi trường + giờ |
| 4 | **Rẽ nhánh theo HTTP status** | Ba mã khác nhau cùng **401** ⇒ FE hiển thị sai thông điệp cho người dùng | Luôn rẽ theo `code` |
| 5 | **Kiểm chứng trên môi trường chưa chạy bản sửa** | FE báo một lỗi đã được sửa; mất một lượt đo A/B mới phát hiện | Đo **một field mốc** để biết chắc build nào đang chạy |
| 6 | **Thiếu mục "ngoài phạm vi"** | Bên làm tiện tay sửa thêm, phát sinh breaking change không ai yêu cầu | Luôn có mục đó, ghi cả thứ *gần giống* |
| 7 | **Đổi giá trị mặc định mà không nói** | Thu hẹp payload của endpoint đang chạy một cách âm thầm | Đổi mặc định là breaking change, phải nêu ở §4.3 |
| 8 | **Mô tả "dữ liệu đầy đủ hơn" / "xử lý tốt hơn"** | Hai bên hiểu khác nhau, nghiệm thu không quyết được | Viết thành câu đúng/sai kiểm được |
| 9 | **Không nói cái gì chưa nghiệm thu** | Nợ ẩn, phát hiện ở lượt sau khi cả hai bên đã tin là xong | Ghi thẳng phần chưa đo và lý do |

---

## 7. Template copy-paste — FE → BE

<details>
<summary>Bấm để mở / copy</summary>

```markdown
# [P0/P1/P2/P3] <Chủ đề> — FE → BE (<YYYY-MM-DD>)

## 0. Kết luận trong 3 dòng
- Vấn đề:
- Ảnh hưởng (màn hình / người dùng / môi trường):
- Mức ưu tiên và lý do:

## 1. Phạm vi
- Trong phạm vi:
- Ngoài phạm vi (đừng tiện tay sửa):
- Môi trường FE đang chạy / snapshot code:

## 2. Bằng chứng tái hiện
### 2.1 <Tên lỗi/gap thứ nhất>
- Request: <METHOD> <path> + header liên quan
- Body gửi: (đã che secret)
- Response thật: HTTP <status>, nguyên văn body
- X-Trace-Id:
- Môi trường / thời điểm / id bản ghi đã dùng:
- Mong đợi: ...
- Thực tế: ...

## 3. Đầu ra mong đợi
| # | Yêu cầu (câu kiểm được) | Endpoint | Chặn FE? |
|---|---|---|---|

### Field mới cần có
| Field | Kiểu | Nullable | Nguồn giá trị | Ví dụ |
|---|---|---|---|---|

## 4. Hợp đồng lỗi mong đợi
| Tình huống | HTTP | code mong đợi | FE hiển thị gì |
|---|---|---|---|

## 5. Cần BE xác nhận
1. (câu hỏi đóng) — FE nghiêng về: ... vì ... — **chặn / không chặn** FE code tiếp
2. ...

## 6. FE đã tự xử lý
-

## 7. Tham chiếu
- <file> — dùng để ...
```

</details>

---

## 8. Template copy-paste — BE → FE

<details>
<summary>Bấm để mở / copy</summary>

```markdown
# Trả lời <tên task của FE> — <Chủ đề> (<YYYY-MM-DD>)

## 0. Kết luận trong một câu

| # | FE báo | Kết luận | Bằng chứng quyết định |
|---|---|---|---|
| 1 | | Bug thật, đã sửa / Gap thật, đã làm / Báo nhầm | |

## 1. Nguyên nhân thật
(ngắn; nếu FE chẩn đoán lệch thì nói rõ lệch ở đâu, kèm dữ liệu)

## 2. Thay đổi contract
| Field / Endpoint | Trước | Sau | Nullable | Phá client đang chạy? |
|---|---|---|---|---|

## 3. Breaking change
**Wire: CÓ / KHÔNG có.**

| Endpoint | Điều kiện | Cũ | Mới | Loại | FE phải sửa gì |
|---|---|---|---|---|---|

## 4. Cách dùng
### 4.1 <Endpoint>
- Request mẫu (copy từ lần gọi thật):
- Response mẫu:
- Nhánh lỗi mẫu:

## 5. Quyền và phạm vi
- Permission:
- Role được cấp:
- Giới hạn đã biết:

## 6. Bằng chứng nghiệm thu
- Test tự động: <lệnh> — <N> case, failures = 0, errors = 0
- Kiểm chứng thủ công: môi trường, từng probe → kết quả, X-Trace-Id
- Chưa nghiệm thu: ... vì ...

## 7. Chưa làm, có chủ đích
| Mục FE xin | Vì sao chưa làm | Điều kiện để làm |
|---|---|---|

## 8. Việc FE cần làm tiếp
**Bắt buộc:**
- [ ]

**Nên làm:**
- [ ]

**Thứ tự deploy:** (BE trước / FE trước / không ràng buộc)
```

</details>

# Nguyên Tắc FE Giao Task Cho BE

**Phiên bản:** v1 · **Ngày:** 2026-10-12 · **Đối tượng:** dev Frontend của dự án Manufacturing ERP.

> **Bối cảnh áp dụng:** FE và BE **không dùng chung repo**. Mỗi lần giao việc, FE gửi **một file `.md`
> qua Zalo**. BE không mở được code FE, không thấy màn hình FE, không chạy được app FE.
>
> ⇒ **Nguyên tắc gốc, suy ra mọi nguyên tắc còn lại: file gửi đi phải TỰ CHỨA và TÁI HIỆN ĐƯỢC.** BE
> phải dựng lại được đúng tình huống chỉ bằng nội dung file. Không "xem màn hình là rõ", không "như
> hôm qua đã nói", không link tới repo FE.

---

## 1. Năm nguyên tắc không được vi phạm

| # | Nguyên tắc | Vì sao |
|---|---|---|
| **F1** | **Mỗi lỗi phải có đủ 5 mảnh bằng chứng** (§3.1): request thật · response thật · `X-Trace-Id` · môi trường + giờ · id bản ghi đã dùng | Thiếu một mảnh là BE phải đoán. BE tra log của họ **bằng `X-Trace-Id`** — không có nó, họ không tìm được request của bạn |
| **F2** | **Đầu ra mong đợi viết thành câu đúng/sai kiểm được**, không phải tính từ | "trả dữ liệu đầy đủ hơn" ⇒ hai bên hiểu khác nhau, nghiệm thu không quyết được |
| **F3** | **Luôn có mục "Ngoài phạm vi"** | Thiếu nó, BE tiện tay sửa thêm và sinh breaking change không ai yêu cầu |
| **F4** | **Tách rõ "cần BE code" và "chỉ cần BE xác nhận"** | Đã xảy ra: FE gửi một bảng phần lớn là xin xác nhận, BE tưởng mọi dòng là một thay đổi code |
| **F5** | **Phân biệt ba loại câu: đã kiểm chứng · đang giả định · đang đề xuất** | Viết lẫn ba loại là nguyên nhân số một của việc BE làm sai hướng |

---

## 2. Quy ước file và gửi qua Zalo

### 2.1 Loại file và cách đặt tên

| Loại | Tên file | Khi nào |
|---|---|---|
| Báo lỗi | `FE_DEFECT_<CHU_DE>_<YYYY-MM-DD>.md` | API trả sai, lỗi, hoặc không như tài liệu |
| Xin field / endpoint | `FE_REQUEST_<CHU_DE>_<YYYY-MM-DD>.md` | Màn hình thiếu dữ liệu để hiển thị |
| Xin xác nhận contract | `FE_QUESTION_<CHU_DE>_<YYYY-MM-DD>.md` | **Không** cần BE code, chỉ cần chốt ngữ nghĩa |
| Bàn giao cả giai đoạn | `FE_HANDOFF_<GIAI_DOAN>_<YYYY-MM-DD>.md` | FE xong một giai đoạn, cần BE triển khai theo |

- Ngày là **ngày gửi**. Sửa nội dung sau khi đã gửi ⇒ thêm `_v2`, `_v3`; **không** gửi lại file cùng
  tên với nội dung khác (Zalo không cho BE biết file nào mới hơn).
- **Một task = một file.** Gộp ba chủ đề vào một file thì không bên nào đóng được từng phần, và một
  mục P0 sẽ bị chôn giữa các mục P3.
- Tên file **không dấu, không khoảng trắng**.
- 🔴 **Loại `FE_QUESTION_` phải dùng đúng.** Đặt tên là `DEFECT` cho một việc chỉ cần xác nhận sẽ làm
  BE mở ra một lượt sửa code không cần thiết.




### 2.3 Ảnh và dữ liệu

- JSON, log, lỗi console **phải** nằm trong code block trong file `.md`. **Không** gửi ảnh chụp màn
  hình JSON — BE không copy được và ảnh bị Zalo nén mờ ký tự.
- Ảnh chỉ dùng cho thứ **không diễn đạt được bằng chữ**: bố cục màn hình, chỗ số liệu hiển thị sai.
  Mỗi ảnh kèm một dòng chú thích nói đang chỉ vào cái gì.
- Che secret trước khi gửi: `Authorization`, mật khẩu, refresh token. **Giữ lại `X-Trace-Id`** — nó
  không phải secret, và là thứ BE cần nhất.

---

## 3. Dàn ý bắt buộc của file FE gửi BE

Bảy mục dưới đây là **tối thiểu**. Mục nào không có nội dung thì ghi "không có", **không xoá mục**.

### §0 — Kết luận trong 3 dòng

BE chỉ có 30 giây đầu. Ba dòng phải trả lời:

```
- Vấn đề:            <cái gì sai/thiếu, một câu>
- Ảnh hưởng:         <màn hình nào, ai dùng, đang demo/production hay chưa>
- Mức ưu tiên:       P0/P1/P2/P3 + lý do một dòng
```

**Không** mở đầu bằng bối cảnh dự án hay lịch sử công việc FE.

Mức ưu tiên dùng đúng bốn mức, không tự thêm:

| Mức | Nghĩa |
|---|---|
| **P0** | Chặn hoàn toàn, không có đường đi vòng |
| **P1** | Một màn hình/luồng không dùng được, phần còn lại vẫn chạy |
| **P2** | Dùng được nhưng sai số/sai nhãn, hoặc phải gọi thêm API để bù |
| **P3** | Cải thiện, không chặn ai |

🔴 **Không gắn P0 cho mọi thứ.** Lạm dụng P0 làm mức ưu tiên mất nghĩa, và lần có P0 thật sẽ không ai
phân biệt được.

### §1 — Phạm vi

- **Trong phạm vi:** liệt kê màn hình / endpoint / field **cụ thể**.
- **Ngoài phạm vi:** liệt kê thứ *gần giống* mà BE có thể tiện tay sửa luôn. Đây là mục ngăn trượt
  phạm vi — bỏ nó là mời BE đoán.
- **Môi trường FE đang chạy:** dev/demo, bản FE nào (commit/tag hoặc mốc phiên làm việc).

### §2 — Bằng chứng tái hiện *(mục quan trọng nhất)*

Mỗi lỗi một mục con, đủ **5 mảnh**:

| # | Mảnh | Chi tiết |
|---|---|---|
| 1 | **Request thật** | method + path **đầy đủ** + header liên quan + body đã gửi (che secret) |
| 2 | **Response thật** | **nguyên văn**, kèm HTTP status và `code` trong envelope |
| 3 | **`X-Trace-Id`** | copy từ response header |
| 4 | **Môi trường + thời điểm** | dev/demo, ngày giờ cụ thể |
| 5 | **Id bản ghi đã dùng** | id thật của record đã thử (đơn hàng, lô, lệnh sản xuất...) |

Rồi hai dòng cạnh nhau, **không** viết thành đoạn văn:

```
Mong đợi:  ...
Thực tế:   ...
```

🔴 **Mảnh số 5 là mảnh hay bị bỏ nhất và tốn nhất khi thiếu.** Đã có lần FE báo một nghiệp vụ "không
atomic", nhưng bản ghi được trích dẫn thuộc **một lượt chạy khác** — hai câu truy vấn trên đúng id đó
là đủ để thấy không có lỗi nào. Thiếu id thì cả hai bên tranh luận trên suy đoán, mất nhiều lượt.

🔴 **Trước khi báo lỗi, kiểm xem môi trường bạn đang gọi có chạy bản sửa mới nhất không.** Đã có lần
FE báo một lỗi **đã được sửa** vì môi trường test của họ còn bản cũ; mất một lượt đo đối chứng mới
phát hiện. Cách kiểm nhanh: gọi một endpoint đã biết chắc khác nhau giữa hai bản và xem nó trả dạng
nào.

### §3 — Đầu ra mong đợi *(viết dạng kiểm được)*

| # | Yêu cầu (một câu đúng/sai kiểm được) | Endpoint | Chặn FE? |
|---|---|---|---|

```
❌ "API trả dữ liệu đầy đủ hơn"
✅ "GET /v1/inventory/lots?warehouseId=X trả thêm uomCode (string, không null khi item có đơn vị)"

❌ "Xử lý lỗi tốt hơn"
✅ "Thiếu query param bắt buộc ⇒ HTTP 400, code = VALIDATION_ERROR, errors[0].field = tên param"
```

**Xin field mới** ⇒ bảng đủ 5 cột:

| Field | Kiểu | Nullable | Nguồn giá trị / công thức FE hình dung | Dùng ở đâu trên UI |
|---|---|---|---|---|

**Xin endpoint mới** ⇒ method · path đề xuất · query/body · hình dạng response · có cần phân trang
không · ai được gọi (role dự kiến).

🔴 **Xin "sửa số liệu" thì phải nêu công thức bạn mong đợi, kèm một ví dụ số.** Đã có lần FE đề xuất
một công thức mà với cách hệ thống cấu hình ngưỡng, nó trả **0 cho mọi dòng** cần con số đó nhất. Nêu
công thức + ví dụ số giúp BE phát hiện lệch giả định **trước** khi code.

### §4 — Hợp đồng lỗi mong đợi

| Tình huống | HTTP | `code` mong đợi | FE sẽ hiển thị gì |
|---|---|---|---|

🔴 **Luôn nêu theo `code`, không chỉ theo HTTP status.** Hệ thống này có nhiều mã khác nhau dùng
**chung một status**; FE **phải** rẽ nhánh theo `code`. Ví dụ `refresh` trả **401** ở cả ba trường
hợp mà FE phải nói ba câu khác nhau với người dùng:

| `code` | Nghĩa | FE hiển thị |
|---|---|---|
| `REFRESH_TOKEN_EXPIRED` | hết hạn tự nhiên | "Đăng nhập lại" |
| `SESSION_ABSOLUTE_TIMEOUT` | hết hạn theo chính sách | "Phiên đã hết hạn" — **không** phải cảnh báo bảo mật |
| `TOKEN_REUSE_DETECTED` | phát hiện dùng lại ⇒ mọi phiên đã bị thu hồi | cảnh báo bảo mật rõ ràng |

Nhắc lại envelope để cùng ngôn ngữ — mọi response, **thành công và lỗi**, đều có dạng:

```json
{ "code": "SUCCESS", "result": { }, "message": "OK", "errors": [ { "field": "...", "message": "..." } ] }
```

### §5 — Cần BE xác nhận *(tách khỏi phần cần code)*

Đánh số, **mỗi mục một câu hỏi đóng**:

```
1. <câu hỏi đóng> — FE nghiêng về: <phương án> vì <lý do một dòng> — CHẶN / KHÔNG CHẶN FE code tiếp
```

Ba quy tắc:

- **Một câu hỏi = một quyết định.** Không gộp "và/hoặc" — BE sẽ trả lời một nửa.
- **Luôn kèm phương án FE nghiêng về.** Câu hỏi mở hoàn toàn thường bị bỏ qua hoặc bị BE tự chọn hộ.
- **Ghi rõ câu nào chặn FE code tiếp** — đó là thứ quyết định BE trả lời câu nào trước.

🔴 **Mục này phải nằm riêng, và đầu mục ghi rõ: "các mục dưới đây KHÔNG cần code, chỉ cần trả lời".**

### §6 — FE đã tự xử lý

Ghi rõ lớp phòng vệ FE đã thêm: chuẩn hoá dữ liệu, giá trị mặc định, retry, ẩn cột khi thiếu field.

Hai lý do mục này quan trọng: BE cần biết để **không** sửa theo cách làm lớp đó thành lỗi; và khi BE
sửa xong, bạn biết lớp nào đã thành vô tác dụng và có thể gỡ.

### §7 — Tham chiếu

Liệt kê file đã gửi/nhận trước đó liên quan tới chủ đề này, **kèm một dòng** nói dùng để làm gì. Vì
không chung repo, chỉ ghi **tên file** — không ghi đường dẫn máy bạn.

---

## 4. Checklist tự kiểm trước khi bấm gửi

- [ ] §0 có 3 dòng kết luận + mức ưu tiên P0–P3 **dùng đúng mức**
- [ ] §1 có mục **Ngoài phạm vi**
- [ ] Mỗi lỗi đủ **5 mảnh** bằng chứng, trong đó có `X-Trace-Id` và **id bản ghi thật**
- [ ] Đã kiểm môi trường test đang chạy **bản BE mới nhất**
- [ ] JSON/log để trong code block, **không** phải ảnh chụp
- [ ] Mỗi đầu ra mong đợi là một câu **kiểm được**, không phải tính từ
- [ ] Field mới đủ 5 cột; xin sửa số liệu thì có **công thức + ví dụ số**
- [ ] Bảng lỗi nêu theo `code`, không chỉ HTTP status
- [ ] §5 nằm riêng, đầu mục ghi rõ "không cần code", mỗi câu hỏi đóng + phương án FE nghiêng về
- [ ] Đã ghi rõ câu hỏi nào **chặn** FE code tiếp
- [ ] §6 có liệt kê lớp phòng vệ FE đã thêm
- [ ] Đã phân biệt *đã kiểm chứng* / *đang giả định* / *đang đề xuất*
- [ ] Tên file không dấu, có ngày, `_v2` nếu là bản sửa; tên loại (`DEFECT`/`REQUEST`/`QUESTION`) đúng
- [ ] Đã viết tin nhắn 4 dòng đi kèm (§2.2)
- [ ] Đã che secret; `X-Trace-Id` **giữ lại**

---

## 5. Bảy lỗi FE đã mắc thật — đừng lặp lại

| # | Lỗi | Hệ quả đã xảy ra | Cách tránh |
|---|---|---|---|
| 1 | **Báo lỗi không kèm id bản ghi** | Bản ghi được trích dẫn thuộc lượt chạy khác ⇒ hoá ra báo nhầm, mất nhiều lượt trao đổi | §2 mảnh 5, bắt buộc |
| 2 | **Test trên môi trường chưa chạy bản sửa** | Báo lại một lỗi đã được sửa | Kiểm một endpoint mốc trước khi báo |
| 3 | **Gửi bảng xin xác nhận nhưng đặt tên như báo lỗi** | BE mở một lượt sửa code không cần thiết | Dùng `FE_QUESTION_`, và tách §5 ra riêng |
| 4 | **Viết yêu cầu dạng tính từ** ("đầy đủ hơn", "tốt hơn") | Nghiệm thu không quyết được, phải làm lại | §3, mỗi dòng là câu đúng/sai kiểm được |
| 5 | **Đề xuất công thức mà không kiểm với cấu hình thật** | Công thức đề xuất trả 0 cho đúng những dòng cần con số đó | Nêu công thức **kèm ví dụ số** để BE đối chiếu |
| 6 | **Dữ liệu seed từ phía FE bị lỗi mã hoá rồi báo là "dữ liệu cũ của BE"** | BE mất thời gian tìm chỗ ghép chuỗi không tồn tại | Trước khi báo, kiểm xem chuỗi hỏng do script/máy FE gửi lên hay do BE sinh ra |
| 7 | **Ghi hai số liệu trái nhau trong cùng bộ tài liệu bàn giao** | Hai màn hình/hai slide nói khác nhau về cùng một bản ghi | Trước khi gửi, đối chiếu lại các id và con số xuất hiện nhiều lần trong file |

---

## 6. Việc FE phải làm sau khi gửi

1. **Lưu bản vừa gửi vào repo FE** (cùng tên file) — Zalo không phải nơi lưu trữ, hội thoại sẽ trôi.
2. **Trả lời từng mục BE hỏi lại**, đánh số theo đúng số thứ tự họ dùng — trả lời gộp thành đoạn văn
   làm BE không biết câu nào đã được chốt.
3. **Nghiệm thu trên môi trường của mình rồi mới đóng task.** BE nói "xong", FE nói "đạt" — hai việc
   khác nhau. Khi nghiệm thu, dùng lại đúng id bản ghi và đúng request đã ghi ở §2.
4. Chưa đạt ⇒ **không mở file mới**, trả lời trong cùng mạch hội thoại và gửi bản `_v2` của đúng file
   đó, nêu rõ mục nào đã đạt và mục nào chưa.

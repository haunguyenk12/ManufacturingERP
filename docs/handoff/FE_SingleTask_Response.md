Đây là file trả lời cho FE, mục đích để FE biết các sử dụng các chức năng hoặc lỗi đã sửa phần mới làm xong. Nội dung sẽ ghi xuống bên dưới không được xóa dòng này. Nội dung mới sẽ ghi đè lên nội dung cũ.

# Trả lời `BACKEND_AUDIT_LOG_VALUE_CONTRACT_2026-08-25` — Audit Log value contract (2026-08-25)

## 0. Kết luận trong một câu

Các bạn **báo đúng**, và nguyên nhân tệ hơn báo cáo mô tả một chút: đây không phải "runtime lệch tài
liệu" mà là một **hồi quy backend tự gây ra** hôm 2026-08-17 và không ai ghi nhận. Đã sửa theo đúng
phương án các bạn khuyến nghị — **giữ contract đã công bố**, chuẩn hoá mọi giá trị về `string`.

| | Trước | Sau |
|---|---|---|
| `changes[].oldValue` / `newValue` | JSON đa kiểu: object · array · number · boolean · string | **`string` hoặc `null`, luôn luôn** |
| Schema OpenAPI runtime | `$ref: JsonNode` ⇒ `{"type":"object"}` | **`{"type":"string"}`** |
| Màn hình Audit detail | sập khi gặp snapshot có cấu trúc | render được, không cần lớp phòng vệ |

Lớp normalize phòng vệ các bạn vừa thêm nay thành **no-op** — cứ giữ, nó vẫn đúng và bảo vệ các bạn
nếu chạy với backend cũ hơn bản này.

---

## 1. Nguyên nhân thật — không phải "runtime chưa khớp tài liệu"

Bản OpenAPI các bạn lưu (`oldValue: type: string`) **là contract gốc `C2-1`, và nó đúng**. Chuyện đã
xảy ra là:

- **2026-08-06 (`C2-1`)** — API audit ra đời, `oldValue`/`newValue` là `String`. Lúc đó `changes[]`
  luôn rỗng vì chưa ai ghi vào `audit_log_changes`, nên kiểu này chưa bao giờ bị thử với dữ liệu thật.
- **2026-08-17** — backend bật field-level diff thật, và **trong cùng lượt đó** đổi kiểu hai field sang
  `JsonNode`. Từ giây phút ấy, kiểu runtime của **cùng một field** đổi theo từng dòng dữ liệu.
- **2026-08-25** — các bạn là người đầu tiên chạm phải, vì phải có một audit **mang snapshot dạng
  container** mới lộ ra. Trong dữ liệu demo hiện tại, đúng loại đó là `WORK_ORDER_CREATED`
  (`componentLines`, `operations`, `allocations`).

Cột lưu là `jsonb`, nên "kiểu gì cũng có" không phải giả thuyết. Đếm thật trên DB demo — **2758** dòng
`audit_log_changes`:

```text
string   2266
boolean   222
number    162
array     108
```

⇒ Một field, **bốn** kiểu runtime, chưa kể `null`. Không client sinh mã nào type nổi thứ đó, và đó
đúng là lý do các bạn nhận một object vào cây render React.

🔴 **Một chỗ tài liệu backend đã nói sai, nay đã sửa:** `docs/api-guide-for-frontend.md` từng ghi
*"`oldValue`/`newValue` giữ đúng kiểu JSON"* như thể đó là tính năng. Câu đó thực chất đang mô tả
**hệ quả của lỗi**, nên không ai đọc ra rằng contract vừa bị phá.

---

## 2. Đã sửa gì — đúng theo 5 quy tắc các bạn đề xuất

Sửa nằm ở **tầng đọc**. Đường ghi và cột `jsonb` **không đụng** — chúng vẫn lưu JSON có cấu trúc để
backend còn query được bằng toán tử `jsonb`; chỉ khi map ra wire mới chuẩn hoá.

| Nguồn trong `jsonb` | Ví dụ đã lưu | Trả về cho các bạn |
|---|---|---|
| SQL `NULL` | — | `null` |
| JSON `null` | `null` | `null` |
| Chuỗi | `"DRAFT"` | **`DRAFT`** (bỏ dấu nháy JSON) |
| Số | `10` | `"10"` |
| Boolean | `true` | `"true"` |
| Object | `{"uom":"EA","lineNo":1}` | chuỗi JSON compact của chính object đó |
| Array | `[{"name":"Final Assembly"}]` | chuỗi JSON compact của chính array đó |
| Text không phải JSON *(chỉ có ở dữ liệu ghi tay)* | `not json at all` | giữ nguyên |

### 2.1 Hai chi tiết khác với ví dụ trong tài liệu của các bạn

1. **Compact, không pretty-print.** Ví dụ ở mục "Recommended Backend Resolution" của các bạn có xuống
   dòng và thụt lề. Backend trả **compact** — đúng byte-for-byte thứ đang nằm trong cột. Lý do: định
   dạng cho người đọc là việc của tầng hiển thị; trả compact thì `JSON.parse` một phát là ra cấu trúc,
   còn muốn đẹp thì `JSON.stringify(JSON.parse(v), null, 2)` ngay chỗ render. Nếu các bạn **bắt buộc**
   cần pretty từ server thì báo lại — đổi một dòng, nhưng backend không tự chọn hộ.
2. **Chuỗi trả ra không kèm dấu nháy.** Đây là chỗ dễ làm sai nhất: trong `jsonb`, một giá trị chữ
   được lưu **kèm** dấu nháy. Trả thô nguyên cột sẽ khiến **2266/2758** dòng hiện chuỗi có nháy trên
   UI. Backend parse rồi mới rẽ nhánh, nên các bạn nhận `DRAFT` chứ không phải `"DRAFT"`.

---

## 3. Đối chiếu từng Acceptance Criteria của các bạn

| Tiêu chí | Kết quả | Bằng chứng |
|---|---|---|
| Runtime khớp OpenAPI đã công bố | ✅ | `/v3/api-docs` nay trả `"oldValue": {"type": "string"}` — §4.3 |
| `WORK_ORDER_CREATED` mở được khi có component snapshot | ✅ | §4.1, trên đúng bản ghi `D26-WO-1004` |
| `oldValue`/`newValue` cùng một kiểu runtime ở **mọi** action | ✅ | §4.1 + §4.2, đo trên 5 action khác nhau |
| Test phủ string / `null` / number / boolean / object / array | ✅ | §5 — một case đi hết 7 hình dạng, một case riêng cho SQL `NULL` |
| Dữ liệu lịch sử vẫn đọc được, không lỗi migration | ✅ | **Không migration nào** trong lượt này; 2758 dòng cũ đọc bình thường |

---

## 4. Đo thật qua HTTP — chạy **A/B**, cùng một bản ghi, hai build

Backend thật trên Postgres + Redis (`docker compose`, không phải Testcontainer), dữ liệu demo hiện tại.

### 4.1 Audit đúng loại đã làm sập màn hình của các bạn

`GET /api/v1/audit-logs/9db449ce-fee8-4e79-9f2e-be8aee1409a4` — `WORK_ORDER_CREATED`, `D26-WO-1004`:

```text
TRƯỚC   tập kiểu của mọi newValue:  ['int', 'list', 'str']
        componentLines   list      <-- đây là thứ lọt vào cây render React
        plannedQuantity  int
        bomRevision      str

SAU     tập kiểu của mọi newValue:  ['str']     (oldValue: toàn bộ null, vì đây là CREATE)
        componentLines   str   [{"uom":"EA","lineNo":1,"bomLineId":"e463b29c-...
        operations       str   [{"name":"Final Assembly","version":0,"sequence":10,...
        allocations      str   [{"uom":"EA","dueDate":"2026-09-24","allocationId":...
        plannedQuantity  str   10
        bomRevision      str   A
```

### 4.2 Bốn action khác, phủ nốt các hình dạng còn lại

```text
ITEM_CREATED     lotTracked "true" · serialTracked "false"     <-- boolean
                 type "FINISHED_GOOD" · code "D26-FG-BIKE16"   <-- chuỗi, KHÔNG có dấu nháy
BOM_ACTIVATED    status  old = DRAFT   new = ACTIVE            <-- UPDATE, oldValue khác null
USER_CREATED     roles   ["OPERATOR"] dưới dạng chuỗi          <-- array
```

Trace id của một lượt gọi thành công: `7a5f114883a84bbb`.

### 4.3 OpenAPI runtime

```yaml
# TRƯỚC
oldValue: { $ref: '#/components/schemas/JsonNode' }   # JsonNode: { "type": "object" }
newValue: { $ref: '#/components/schemas/JsonNode' }

# SAU
oldValue: { type: string }
newValue: { type: string }
```

🔴 Schema **trước** khai `object` cho **cả** dòng chuỗi lẫn dòng số — nghĩa là bản OpenAPI runtime lúc
đó sai với hầu hết dữ liệu, không riêng dòng object. Bản các bạn lưu (`type: string`) mới là bản đúng,
và nay nó lại đúng.

---

## 5. Test

`mvn -o clean verify` (Postgres thật qua Testcontainers) — **1184 case unit + 142 case IT / 21 class
IT, failures = 0, errors = 0**, `BUILD SUCCESS`. Baseline trước: 1181 unit + 142 IT.

`+3` case, tất cả nhắm đúng contract này:

| Tầng | Case |
|---|---|
| Service | Một case đi hết **7** hình dạng lưu trữ (chuỗi · số · boolean · object · array · JSON `null` · text không phải JSON) trong **một** audit và assert từng giá trị ra |
| Service | Một case riêng: SQL `NULL` phải ra `null`, **không** ra chuỗi `"null"` |
| HTTP | Một case assert `isString()` **trước** rồi mới assert nội dung |

3 assertion cũ **sửa tại chỗ** (bỏ `.asText()`), không xoá case nào.

🔴 **`isString()` mới là thứ khoá thật.** Assert kiểu `jsonPath(...).value("...")` **vẫn xanh** khi giá
trị là một object node — đúng loại assertion đã để lọt lỗi này ngay từ đầu. Nếu bên các bạn có contract
test cho audit detail, khuyến nghị assert **kiểu** trước, đừng chỉ assert nội dung.

### 5.1 Cố tình phá code (3 mutation, đều đã revert)

| Phá gì | Kết quả |
|---|---|
| Không bỏ dấu nháy cho scalar | **3 case đỏ** — bắt đúng bẫy "trả thô nguyên cột" ở §2.1 |
| Làm phẳng container bằng `asText()` | **1 case đỏ** — object/array không được âm thầm thành chuỗi rỗng |
| Bỏ nhánh JSON `null` | **1 case đỏ** — `null` không được thành chuỗi `"null"` |

Ngoài ra, kiểu `String` trong record là bảo đảm **mạnh hơn mọi test**: sau thay đổi này một `JsonNode`
không còn compile được vào DTO đó, nên hồi quy cũ không thể tái diễn một cách âm thầm.

---

## 6. Các bạn cần làm gì

1. **Không phải làm gì để hết sập.** Lớp normalize vừa thêm thành no-op; giữ lại là hợp lý (nó bảo vệ
   các bạn khi chạy với backend cũ hơn bản này).
2. **Nếu muốn hiện cấu trúc đẹp** cho `componentLines`/`operations`/`allocations`/`roles`/`breaks`:
   `JSON.parse(value)` rồi render như cũ — nhớ `try/catch` vì phần lớn dòng là chữ thường, không phải
   JSON. Dấu hiệu nhận biết rẻ tiền: giá trị bắt đầu bằng `{` hoặc `[`.
3. **Số và boolean nay là chuỗi** (`"10"`, `"true"`). Nếu màn hình đang so sánh kiểu số ở đâu đó thì
   đổi sang so chuỗi, hoặc `Number(value)` khi biết field đó là số.
4. **Type DTO phía các bạn nên là `string | null`** — regenerate từ OpenAPI runtime là ra đúng.

---

## 7. Việc còn lại / chưa làm

- 🔴 **Deploy nằm ngoài phạm vi lượt này.** Bản sửa đã xong và đã chạy thật trên máy backend; môi
  trường của các bạn vẫn chạy bản cũ cho tới khi deploy.
- **Không migration, không permission mới, không endpoint mới.** Dữ liệu lịch sử không phải đụng gì.
- **Chỉ `GET /v1/audit-logs/{auditLogId}` đổi.** `GET /v1/audit-logs` (danh sách) **không** trả
  `changes[]` và **không** đổi field nào — đó là chủ ý: batch-query một mảng cho mọi dòng của mọi
  trang là lãng phí.
- Tài liệu đã cập nhật: `docs/api-guide-for-frontend.md` (đầu file + mục Audit Logs) và
  `docs/fe-guide-audit-logs-and-inventory-lots.md`.
- **Một câu hỏi cho các bạn:** có cần backend pretty-print JSON của object/array không (§2.1)? Backend
  đang trả compact và sẽ không tự đổi. Ngoài câu đó, phía backend không còn gì chờ phản hồi.

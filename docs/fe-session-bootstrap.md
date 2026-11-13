# Session Bootstrap cho FE — profile, permissions, plant & access scope

> **Trả lời trực tiếp cho blocker Scope 0.** Tài liệu này nói rõ **cái gì lấy được hôm nay**.
>
> Bổ sung cho [`api-guide-for-frontend.md`](./api-guide-for-frontend.md). Nguồn: đọc trực tiếp
> `JwtTokenProvider`, `UserPrincipal`, `UserDetailsServiceImpl`, `AuthService`, `AccessControlService`.
> Viết ban đầu 2026-08-01 (khi `GET /auth/me` **chưa** tồn tại) — **cập nhật cùng ngày** sau khi
> `GET /api/auth/v1/me` được triển khai. §7 giữ nguyên làm bằng chứng lịch sử của lượt kiểm chứng đầu.

---

## 1. TL;DR — bảng trạng thái 4 thông tin

| # | Thông tin | Có endpoint? | Lấy thế nào hôm nay |
|---|---|---|---|
| 1 | **Username** | ✅ Có | Decode JWT (`sub`) **hoặc** `GET /api/auth/v1/me` (`username`) |
| 1b | **userId, email** | ✅ **Có** | `GET /api/auth/v1/me` → `userId`, `email`, `status` |
| 2 | **Permissions + roles** | ✅ **Có** | Decode JWT (union phẳng, nhanh) **hoặc** `GET /api/auth/v1/me` (cùng union, tách sẵn `roles[]`/`permissions[]`) |
| 3 | **Default Plant** | ✅ **Có (gợi ý)** | `GET /api/auth/v1/me` → `defaultPlantId` (plant đầu tiên theo `(companyCode, plantCode)`; `null` nếu user không có scope `PLANT` nào) |
| 4 | **Access scope** (company/plant được phép, permission theo từng scope) | ✅ **Có** | `GET /api/auth/v1/me` → `scopes[]` |

✅ **`GET /api/auth/v1/me` đã tồn tại** kể từ 2026-08-01 — endpoint mới, yêu cầu `Authorization: Bearer`
hợp lệ (đây là ngoại lệ duy nhất trong `AuthController`: 4 endpoint còn lại — `login`/`refresh`/
`logout`/`logout-all` — vẫn permit-all). Không cần permission `PERM_*` cụ thể, chỉ cần đã đăng nhập.

**Kết luận cho Scope 0: đã đóng hoàn toàn.** Một lệnh gọi duy nhất sau login trả đủ cả 4 thông tin.

---

## 2. Decode JWT — nguồn dữ liệu chính hiện nay

Access token là JWT ký HS256. FE **decode** (không verify — verify là việc của backend) để đọc payload.

### Payload thực tế

```jsonc
{
  "sub":   "admin",                    // ← USERNAME (không phải userId)
  "jti":   "9c2e77a1-…",               // id của access token, dùng cho blacklist
  "roles": [                           // ← union ROLE_* + PERM_*
    "ROLE_ADMIN",
    "ROLE_MANAGER",
    "PERM_WORK_ORDER_MANAGE",
    "PERM_WORK_ORDER_READ",
    "PERM_MRP_RUN",
    "PERM_PRODUCTION_RECEIPT_APPROVE"
    // …
  ],
  "iat": 1785000000,
  "exp": 1785003600
}
```

🔴 **Tên claim là `roles` nhưng nội dung gồm CẢ hai loại.** Đây là chỗ dễ hiểu nhầm nhất:

| Tiền tố | Nguồn | Dùng để |
|---|---|---|
| `ROLE_*` | Role tĩnh (`user_roles`) **+** role động từ scoped assignment | Phân nhánh giao diện thô (`ADMIN` thấy menu cấu hình) |
| `PERM_*` | Permission từ **scoped assignment đang `ACTIVE`** | Ẩn/hiện từng nút cụ thể |

### Code mẫu (TypeScript, không cần thư viện)

```ts
type JwtPayload = {
  sub: string;          // username
  jti: string;
  roles: string[];      // ROLE_* + PERM_*
  iat: number;
  exp: number;
};

function decodeJwt(token: string): JwtPayload {
  const payload = token.split('.')[1];
  // base64url → base64, rồi decode UTF-8 (tên tiếng Việt sẽ hỏng nếu dùng atob trực tiếp)
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  const json = decodeURIComponent(
    atob(base64).split('').map(c => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join('')
  );
  return JSON.parse(json);
}

export function buildSession(accessToken: string) {
  const p = decodeJwt(accessToken);
  const authorities = new Set(p.roles);
  return {
    username:    p.sub,
    roles:       p.roles.filter(r => r.startsWith('ROLE_')).map(r => r.slice(5)),
    permissions: p.roles.filter(r => r.startsWith('PERM_')),
    expiresAt:   new Date(p.exp * 1000),
    has:  (perm: string) => authorities.has(perm),
    isAdmin: () => authorities.has('ROLE_ADMIN'),
  };
}
```

### Dùng để ẩn/hiện nút

```ts
const session = buildSession(accessToken);

session.has('PERM_PRODUCTION_RECEIPT_APPROVE')   // → hiện nút "Duyệt phiếu nhập"
session.has('PERM_QUALITY_DISPOSITION')          // → hiện nút "QC"
session.has('PERM_MRP_RUN')                      // → hiện menu Planning
```

### 🔴 Ba giới hạn phải biết

**(a) Danh sách permission là hợp nhất TOÀN BỘ scope, không theo từng plant.**
User có `PERM_WORK_ORDER_MANAGE` chỉ ở Plant A vẫn thấy `PERM_WORK_ORDER_MANAGE` trong JWT khi đang
làm việc ở Plant B. ⇒ Dùng JWT cho **ẩn/hiện thô**; server vẫn kiểm theo từng resource và **vẫn có thể
trả `403`**. FE **bắt buộc** xử lý `403 PERMISSION_DENIED` tử tế, đừng coi JWT là chân lý.

**(b) Permission chỉ cập nhật khi lấy token mới.**
Admin gán thêm quyền → user **không** thấy hiệu lực cho tới khi access token hết hạn và refresh
(15 phút ở prod, 1 tiếng ở dev), hoặc đăng nhập lại. Nếu cần hiệu lực ngay, FE cho user nút "Làm mới
phiên" gọi `POST /auth/refresh`.

**(c) Decode ≠ verify.** Đừng bao giờ tin JWT ở FE cho quyết định bảo mật — nó chỉ để dựng UI.

---

## 3. Lấy profile đầy đủ (userId, email)

### `GET /api/auth/v1/me`

```
GET /api/auth/v1/me
Authorization: Bearer <accessToken>
```

```jsonc
{
  "code": "SUCCESS",
  "result": {
    "userId": "d22df979-049b-4113-9063-6c0150a0fd5b",
    "username": "admin",
    "email": "admin@erp.local",
    "status": "ACTIVE",
    "roles": ["ADMIN"],
    "permissions": ["PERM_WORK_ORDER_MANAGE", "PERM_MRP_RUN", "…"],
    "scopes": [ /* xem §4 */ ],
    "defaultPlantId": null
  },
  "message": "OK"
}
```

Không token → `401`. Đây là ví dụ **thật**, lấy trực tiếp từ server đang chạy (xem §7.5 cho response
đầy đủ của `admin`, có 38 permission và 1 scope `GLOBAL`).

**Không phải admin/không có scope `PLANT` nào** vẫn gọi được endpoint này — `roles`/`permissions` khi
đó chỉ phản ánh những gì user thật sự có; `scopes` có thể rỗng nếu user chưa được gán bất kỳ
`UserRoleAssignment` nào (hợp lệ, không phải lỗi).

```ts
const res = await api.get('/api/auth/v1/me');
const me  = res.data.result;
// me: { userId, username, email, status, roles[], permissions[], scopes[], defaultPlantId }
```

**Khuyến nghị Scope 0:** gọi `/auth/me` ngay sau `login` thành công — thay hẳn cho việc decode JWT chỉ
để lấy `roles`/`permissions` (dù decode JWT vẫn đúng và nhanh hơn nếu chỉ cần ẩn/hiện nút ngay lập tức
trước khi round-trip network kịp về, xem §2).

---

## 4. Chọn Plant & access scope

### `scopes[]` của `/auth/me` — nguồn chính, thay cho workaround probe-403

`GET /api/auth/v1/me` trả `scopes[]`: mỗi phần tử là một scope `GLOBAL`/`COMPANY`/`PLANT` mà user có
assignment `ACTIVE`, kèm permission áp dụng riêng cho scope đó (khác với `permissions[]` top-level —
union phẳng toàn hệ thống, không phân biệt theo company/plant nào).

```jsonc
"scopes": [
  {
    "scopeType": "PLANT",
    "companyId": "…", "companyCode": "CO-01",
    "plantId": "…",   "plantCode": "PL-HN",
    "permissions": ["PERM_WORK_ORDER_MANAGE", "PERM_MATERIAL_ISSUE_MANAGE"]
  },
  {
    "scopeType": "GLOBAL",
    "companyId": null, "companyCode": null, "plantId": null, "plantCode": null,
    "permissions": ["PERM_ACCESS_MANAGE"]
  }
]
```

- `scopeType: "GLOBAL"` → `companyId`/`plantId` luôn `null` — quyền áp dụng mọi nơi, không suy ra được
  company/plant cụ thể từ đó. FE vẫn cần cho user chọn company/plant nếu thao tác cần resource cụ thể.
- `scopeType: "COMPANY"` → `plantId`/`plantCode` là `null`, quyền áp dụng mọi plant trong company đó.
- `scopeType: "PLANT"` → đủ cả 4 field, dùng trực tiếp để dựng dropdown chọn plant.
- **`WAREHOUSE`-scope không xuất hiện trong `scopes[]`** (có chủ đích — FE chỉ điều hướng theo
  company/plant); quyền đó vẫn nằm trong `permissions[]` union phẳng, không mất thông tin.

### `defaultPlantId` — gợi ý, không phải chân lý

Là `plantId` của phần tử `scopeType: "PLANT"` đầu tiên sau khi sort theo `(companyCode, plantCode)`,
hoặc `null` nếu user không có scope `PLANT` nào (ví dụ: chỉ có `GLOBAL`, như tài khoản `admin` ở §7.5).
FE dùng nó để **auto-chọn** plant khi mở app, nhưng vẫn phải cho user đổi qua danh sách `scopes[]` nếu
`defaultPlantId` là `null` hoặc user muốn plant khác.

```ts
const me = (await api.get('/api/auth/v1/me')).data.result;
const plantScopes = me.scopes.filter(s => s.scopeType === 'PLANT');
const currentPlant = plantScopes.find(s => s.plantId === me.defaultPlantId) ?? plantScopes[0] ?? null;
// currentPlant: null ⇒ user không có plant nào — chỉ có GLOBAL/COMPANY scope, cho thao tác không cần X-Plant-Id
```

Lưu `{ companyId, plantId, plantCode }` vào localStorage để nhớ lựa chọn giữa các lần mở app; gửi kèm
`X-Plant-Id: {plantId}` ở mọi request có `plantId` tường minh.

### Nhắc lại về `X-Plant-Id`

Header này là **cross-check**, không phải nguồn dữ liệu. Nếu gửi mà **lệch** với `plantId` trong
path/query/body → `409 STATE_CONFLICT`. ⇒ Khi user đổi plant ở thanh trên cùng, phải đổi **đồng thời**
cả header lẫn tham số `plantId` trong request. Xem `api-guide-for-frontend.md` §2.5.

---

## 5. Giới hạn còn lại (không phải gap, có chủ đích)

| # | Đặc điểm | Hệ quả với FE |
|---|---|---|
| 1 | `defaultPlantId` chỉ là **gợi ý** đầu tiên theo sort tên, không phải "plant user dùng lần cuối" | FE vẫn nên tự lưu lựa chọn thật của user vào localStorage, chỉ dùng `defaultPlantId` cho lần đầu mở app |
| 2 | `permissions[]` top-level vẫn là **union phẳng toàn hệ thống** (giống JWT) | Muốn permission **theo từng plant cụ thể** phải đọc `scopes[]`, không đọc `permissions[]` |
| 3 | `/auth/me` không cache — mỗi lần gọi là 1 round-trip + vài query DB | Đừng gọi lặp lại trong vòng lặp UI; gọi 1 lần sau login/refresh rồi lưu vào state client |

Không có khoảng trống nào chặn Scope 0 nữa — mục này chỉ ghi nhận đặc điểm thiết kế để FE không hiểu
nhầm thành bug.

---

## 6. Luồng bootstrap khuyến nghị cho FE

```
1. POST /api/auth/v1/login
      ↓ lưu accessToken + refreshToken + tokenId
2. decodeJwt(accessToken)   [tuỳ chọn, cho UI phản hồi ngay trước khi round-trip #3 kịp về]
      ↓ username, roles[], permissions[]  → dựng menu, ẩn/hiện nút
3. GET /api/auth/v1/me
      ↓ userId, email, status, roles[], permissions[], scopes[], defaultPlantId
4. Chọn plant hiện tại từ scopes[]:
      defaultPlantId khác null → auto-chọn plant đó
      defaultPlantId là null, scopes có PLANT → cho user chọn trong scopes[]
      scopes chỉ có GLOBAL/COMPANY → không cần chọn plant cho các thao tác không cần X-Plant-Id
5. Lưu { companyId, plantId, plantCode } vào localStorage
      ↓
6. Mọi request sau đó: Authorization + X-Plant-Id (khi endpoint có plantId)
      ↓
7. Bắt 401 → refresh → gọi lại /auth/me (permission có thể đã đổi) → retry
   Bắt 403 → nút đó ngoài quyền: ẩn đi, KHÔNG retry
```

### Checklist Scope 0

- [ ] Sau login: gọi `GET /api/auth/v1/me`, lưu `{userId, username, email, roles, permissions, scopes, defaultPlantId}` vào state client
- [ ] Dùng `permissions[]` để ẩn/hiện nút — nhưng **vẫn** xử lý `403` tử tế (đây là union mọi scope, không phải per-plant)
- [ ] Dùng `scopes[]` khi cần permission **theo đúng plant/company** đang thao tác
- [ ] Plant: `defaultPlantId` auto-chọn lần đầu; sau đó ưu tiên lựa chọn đã lưu trong localStorage
- [ ] Gọi lại `/auth/me` **sau mỗi lần refresh** (permission có thể đã thay đổi)
- [ ] Đổi plant ⇒ đổi **đồng thời** `X-Plant-Id` và tham số `plantId` (lệch → `409`)

---

## 7. Bằng chứng kiểm chứng thực tế

Chạy thật ngày 2026-08-01 trên `localhost:8080` (profile `dev`), **không** phải suy luận từ code.

> **Mốc 2026-08-01 (sau khi implement):** §7.1–§7.4 dưới đây ghi lại lượt kiểm chứng **đầu tiên**, khi
> `/auth/me` **chưa tồn tại** — giữ nguyên làm lịch sử/bằng chứng cho bug 500 ở §7.4 (vẫn chưa sửa).
> Response `/auth/me` thật **sau khi** implement: xem ví dụ đầy đủ ở §3 và bảng permission/scope ở §4.

### 7.1 Server & OpenAPI

```
GET /actuator/health   → {"status":"UP"}
GET /v3/api-docs       → HTTP 200, 168.904 bytes
                         openapi 3.0.1 · "Manufacturing ERP API" v1.0.0
                         102 paths · 223 schemas
```
Toàn bộ endpoint dưới `/api/v1/auth`: **chỉ có 4** — `login`, `refresh`, `logout`, `logout-all`.

### 7.2 Login thật và nội dung JWT

```
POST /api/v1/auth/login  {"username":"<bootstrap-user>","password":"<secret>"}  → HTTP 200
```
```
envelope keys : ['code', 'result', 'message']        ← KHÔNG có 'errors' (xác nhận NON_NULL)
result keys   : ['accessToken','refreshToken','tokenId','expiresIn','deviceId','sessionKicked']

JWT claims    : ['sub', 'jti', 'roles', 'iat', 'exp']
  sub         = "admin"                              ← username, KHÔNG phải userId
  exp − iat   = 3600 giây                            ← dev profile 1 giờ (prod 15 phút)
  ROLE_*      = 1   → ["ROLE_ADMIN"]
  PERM_*      = 38  → PERM_ACCESS_MANAGE, PERM_BOM_MANAGE, PERM_BOM_READ,
                      PERM_GOODS_RECEIPT_POST, PERM_INVENTORY_MANAGE, … (còn 30)
```
⇒ **Khẳng định ở §2 được chứng minh:** claim `roles` chứa **cả** `ROLE_*` **lẫn** 38 `PERM_*`.
FE decode JWT là có ngay danh sách quyền, không cần gọi thêm API nào.

### 7.3 Workaround cho ADMIN trước khi có `/auth/me` (lịch sử, không còn cần thiết)

```
GET /api/v1/users?size=100  (ADMIN)  → lọc theo username từ JWT:
{ userId: "d22df979-049b-4113-9063-6c0150a0fd5b",
  username: "admin", email: "admin@erp.local", status: "ACTIVE", roles: ["ADMIN"] }
```

### 7.4 🔴 Cạm bẫy phát hiện khi chạy thật: URL sai trả `500`, không phải `404` (vẫn CHƯA sửa)

```
GET /api/v1/auth/me        → HTTP 500  {"code":"INTERNAL_SERVER_ERROR", …}   ← đúng lúc đó, trước khi implement
GET /api/v1/khong-ton-tai  → HTTP 500  {"code":"INTERNAL_SERVER_ERROR", …}
GET /api/v1/users/me       → HTTP 400  {"code":"VALIDATION_ERROR",
                                        "errors":[{"field":"userId","message":"must be a valid UUID"}]}
```

**Đây là lỗi thật của backend** (`NoHandlerFoundException` chưa được xử lý, rơi vào catch-all), đã báo
cho team backend nhưng **chưa sửa** — ngoài phạm vi của phase thêm `/auth/me`. Ảnh hưởng tới FE:

- Gõ sai đường dẫn → nhận `500`, **không** phải `404` ⇒ dễ tưởng server hỏng trong khi chỉ sai URL.
- ⇒ Khi debug, **kiểm tra lại đường dẫn trước** khi kết luận backend lỗi. Đối chiếu với
  `/v3/api-docs` hoặc Swagger UI.
- `GET /users/me` trả `400` với message rõ ràng (`"me"` không parse được thành UUID) — đây là hành vi
  **đúng**, không phải lỗi.

### 7.5 `/auth/me` sau khi implement — kiểm chứng lại trên server thật (2026-08-01)

```
GET /api/v1/auth/me   (không token)                      → HTTP 401
POST /api/v1/auth/login {"username":"<bootstrap-user>","password":"<secret>"}  → HTTP 200
GET /api/v1/auth/me   (Authorization: Bearer <token>)     → HTTP 200
{
  "code": "SUCCESS",
  "result": {
    "userId": "d22df979-049b-4113-9063-6c0150a0fd5b",
    "username": "admin", "email": "admin@erp.local", "status": "ACTIVE",
    "roles": ["ADMIN"],
    "permissions": [ /* 38 PERM_* — khớp claim JWT ở §7.2 */ ],
    "scopes": [
      { "scopeType": "GLOBAL", "companyId": null, "companyCode": null,
        "plantId": null, "plantCode": null,
        "permissions": [ /* cùng 38 permission — admin seed có 1 assignment GLOBAL */ ] }
    ],
    "defaultPlantId": null
  },
  "message": "OK"
}
```

`defaultPlantId: null` là **đúng**, không phải bug — `admin` seed chỉ có scope `GLOBAL`, không có scope
`PLANT` nào (xem quy tắc chọn `defaultPlantId` ở §4). Route `/api/v1/khong-ton-tai` **vẫn** trả `500`
(chưa sửa, xem §7.4) — thêm route mới không ảnh hưởng tới bug đó.

---

## 8. Phân quyền giao diện *(gộp từ `RoleDocs.md`, 2026-11-12)*

> `RoleDocs.md` đã gộp vào đây. Phần trùng với §2–§6 ở trên (decode JWT, `GET /api/auth/v1/me`, `scopes[]`,
> checklist) đã bỏ. Danh mục permission theo role **không** chép lại: nguồn duy nhất là
> [`roles-and-permissions.md`](./roles-and-permissions.md).

### Mô hình phân quyền — 3 khái niệm cần nắm trước

| Khái niệm | Là gì |
|---|---|
| **Role** (`ADMIN`/`MANAGER`/`OPERATOR`) | Nhãn thô, dùng để phân nhánh giao diện lớn (menu cấu hình chỉ ADMIN thấy). **Không** dùng để ẩn/hiện từng nút — quá thô |
| **Permission** (`PERM_<MODULE>_<ACTION>`, vd `PERM_WORK_ORDER_MANAGE`) | Đơn vị cấp quyền thật sự. Mỗi nút/hành động trên UI nên map tới đúng 1 permission code |
| **Scope** (`GLOBAL` / `COMPANY` / `PLANT`) | Permission **không** áp dụng toàn hệ thống mặc định — nó gắn với một phạm vi. Có `PERM_WORK_ORDER_MANAGE` ở Plant A **không** có nghĩa dùng được ở Plant B |

Hệ thống là **Dynamic RBAC**: một user có thể có nhiều `UserRoleAssignment`, mỗi assignment gắn
`(role, scope)`. Permission cuối cùng của user là **hợp của mọi assignment `ACTIVE`, chưa hết hạn**.

### Cấu trúc tên permission

```
PERM_<MODULE>_<ACTION>
```

| Action suffix | Ý nghĩa |
|---|---|
| `_READ` | Xem/list |
| `_MANAGE` | Tạo/sửa/activate/deactivate (CRUD ghi trên master data hoặc chứng từ) |
| `_EXECUTE` | Thực thi thao tác vận hành (không phải CRUD, không phải duyệt) |
| `_APPROVE` | Duyệt/từ chối một đề nghị đang chờ |
| `_OVERRIDE` | Ghi đè một giới hạn nghiệp vụ (kèm lý do bắt buộc) |
| `_MOVE` | Nhập/xuất/điều chỉnh tồn kho thủ công |

> ⚠️ Vài tài liệu nội bộ backend còn dùng tên thiết kế cũ (`WO_WRITE`, `BOM_READ`, `STOCK_RECEIVE`…) ở
> một số bảng lịch sử — **tên thật trả về trên wire (JWT/`/auth/me`) luôn có tiền tố `PERM_`**. Nếu
> thấy một permission không có tiền tố này trong tài liệu, đó là tên cũ chưa migrate, đừng dùng để so
> khớp trong code FE.

### Hai cơ chế kiểm tra quyền ở backend — vì sao đôi khi 403 dù có permission

Đây là chỗ dễ gây bug khó hiểu nhất nếu FE không biết. Backend có **hai** cách gác quyền khác nhau
cho hai nhóm endpoint khác nhau:

| Cơ chế | Áp dụng cho | Cách hoạt động |
|---|---|---|
| `hasResourceAccess` | Phần lớn permission (`PERM_WORK_ORDER_*`, `PERM_BOM_*`, `PERM_INVENTORY_*`, `PERM_ITEM_*`…) | Đi theo cây `Company → Plant → Warehouse`. Có permission qua **bất kỳ** scope nào phủ tới resource đang thao tác là qua (`GLOBAL`, hoặc `COMPANY` chứa nó, hoặc đúng `PLANT`/`WAREHOUSE` đó) |
| `hasPermission` | **Chỉ** những quyền "không gắn resource cụ thể": `PERM_ORG_READ`/`PERM_ORG_MANAGE`, `PERM_ACCESS_MANAGE`, `PERM_UOM_*`, `PERM_AUDIT_READ` | **Chỉ** đọc assignment có `scopeType = GLOBAL`. Có permission đó nhưng gắn scope `PLANT`/`COMPANY` vẫn bị **403** |

**Hệ quả cho FE:** một user có `PERM_UOM_MANAGE` ở scope `PLANT` (không phải `GLOBAL`) sẽ thấy
permission đó trong `permissions[]` (vì đó là union phẳng, không lọc theo scope), nhưng gọi API thật
sự vẫn nhận `403 PERMISSION_DENIED`. ⇒ Với nhóm quyền ở cột phải (org/access/uom/audit), **đừng** chỉ
dựa vào `permissions[]`/`scopes[]` để quyết định hiện nút — coi đây là nhóm "chỉ admin cấu hình toàn
cục mới chắc chắn dùng được", và **luôn** xử lý `403` tử tế thay vì coi UI hiện = chắc chắn gọi được.

### Áp dụng trên FE — pattern khuyến nghị

#### 7.1 Ẩn/hiện nút (permission check thô, dùng `permissions[]`)

```ts
function Can({ perm, children }: { perm: string; children: React.ReactNode }) {
  const { permissions } = useSession();
  return permissions.includes(perm) ? <>{children}</> : null;
}

<Can perm="PERM_PRODUCTION_RECEIPT_APPROVE">
  <Button onClick={approve}>Duyệt phiếu nhập</Button>
</Can>
```

#### 7.2 Ẩn/hiện nút theo đúng plant đang thao tác (dùng `scopes[]`)

```ts
<Can perm="PERM_WORK_ORDER_MANAGE" plantId={currentPlant.plantId}>
  <Button onClick={createWorkOrder}>Tạo Work Order</Button>
</Can>
```

#### 7.3 Guard route

```ts
function RequirePermission({ perm, children }: { perm: string; children: React.ReactNode }) {
  const { permissions } = useSession();
  if (!permissions.includes(perm)) return <Navigate to="/403" replace />;
  return <>{children}</>;
}
```

#### 7.4 Luôn xử lý `403 PERMISSION_DENIED` từ server, đừng chỉ tin UI

UI chỉ **ẩn/hiện**, không phải nguồn chân lý cuối cùng. Ba lý do request vẫn có thể 403 dù nút đã
hiện:
1. Permission trong JWT/`/auth/me` **cũ** (đã bị revoke sau khi token phát hành — permission chỉ cập
   nhật khi có access token mới, xem §8).
2. Đang thao tác trên resource **ngoài scope** thật của user (`scopes[]` không được dùng để check,
   chỉ dùng `permissions[]` thô).
3. Permission thuộc nhóm `hasPermission`/`GLOBAL` (§5) — có trong `permissions[]` nhưng scope thật
   không phải `GLOBAL`.

```ts
try {
  await api.post('/work-orders', payload);
} catch (e) {
  if (e.response?.data?.code === 'PERMISSION_DENIED') {
    toast.error('Bạn không có quyền thực hiện thao tác này.');
    return; // KHÔNG retry
  }
  throw e;
}
```

### Khi nào permission "cập nhật"

Permission trong JWT/`/auth/me` là **snapshot tại thời điểm phát hành token**. Admin gán/thu hồi
quyền cho user khác **không** có hiệu lực ngay:

| Sự kiện | Khi nào permission mới có hiệu lực |
|---|---|
| Admin gán thêm/thu hồi permission | Lần `refresh` kế tiếp (access token hết hạn: 15 phút prod / 1 giờ dev), hoặc login lại |
| User muốn thấy hiệu lực ngay | Cho nút "Làm mới phiên" gọi `POST /auth/refresh` rồi gọi lại `/auth/me` |
| Sau mỗi lần `refresh` thành công | **Luôn** gọi lại `/auth/me` — đừng chỉ decode access token mới rồi coi state cũ vẫn đúng |

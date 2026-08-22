# RoleDocs — Hướng dẫn FE lấy quyền user & phân quyền giao diện

> Tài liệu dành cho đội FE. Trả lời đúng một câu hỏi: **"làm sao biết user hiện tại được làm gì, và
> dùng thông tin đó để ẩn/hiện UI như thế nào cho đúng?"**
>
> Không lặp lại toàn bộ nội dung hai tài liệu nguồn — chỉ trích phần FE cần và diễn giải lại cho dễ
> tích hợp. Khi cần chi tiết sâu hơn (bằng chứng kiểm chứng thật, từng permission một):
> - [`fe-session-bootstrap.md`](./fe-session-bootstrap.md) — cách lấy profile/permission/scope, có ví
>   dụ response thật và log kiểm chứng.
> - [`roles-and-permissions.md`](./roles-and-permissions.md) — danh mục đầy đủ theo role, RACI, các
>   ghi chú lịch sử về seed permission.
> - [`api-guide-for-frontend.md`](./api-guide-for-frontend.md) — hợp đồng API tổng quát (envelope,
>   mã lỗi, luồng auth).

---

## 1. Mô hình phân quyền — 3 khái niệm cần nắm trước

| Khái niệm | Là gì |
|---|---|
| **Role** (`ADMIN`/`MANAGER`/`OPERATOR`) | Nhãn thô, dùng để phân nhánh giao diện lớn (menu cấu hình chỉ ADMIN thấy). **Không** dùng để ẩn/hiện từng nút — quá thô |
| **Permission** (`PERM_<MODULE>_<ACTION>`, vd `PERM_WORK_ORDER_MANAGE`) | Đơn vị cấp quyền thật sự. Mỗi nút/hành động trên UI nên map tới đúng 1 permission code |
| **Scope** (`GLOBAL` / `COMPANY` / `PLANT`) | Permission **không** áp dụng toàn hệ thống mặc định — nó gắn với một phạm vi. Có `PERM_WORK_ORDER_MANAGE` ở Plant A **không** có nghĩa dùng được ở Plant B |

Hệ thống là **Dynamic RBAC**: một user có thể có nhiều `UserRoleAssignment`, mỗi assignment gắn
`(role, scope)`. Permission cuối cùng của user là **hợp của mọi assignment `ACTIVE`, chưa hết hạn**.

---

## 2. Lấy quyền của user — 2 nguồn, dùng cho 2 mục đích khác nhau

### 2.1 Decode JWT (access token) — nhanh, dùng ngay sau login

Access token là JWT HS256. FE **decode** (không cần verify chữ ký — đó là việc của backend), đọc claim
`roles`, thực chất chứa **cả hai** loại token: tiền tố `ROLE_*` và `PERM_*` trộn chung một mảng.

```jsonc
// Payload JWT
{
  "sub":   "manager.hanoi",
  "jti":   "9c2e77a1-…",
  "roles": [
    "ROLE_MANAGER",
    "PERM_WORK_ORDER_MANAGE",
    "PERM_WORK_ORDER_READ",
    "PERM_BOM_READ",
    "PERM_INVENTORY_READ"
    // … union phẳng của MỌI scope user có
  ],
  "iat": 1785000000,
  "exp": 1785003600
}
```

```ts
type JwtPayload = { sub: string; jti: string; roles: string[]; iat: number; exp: number };

function decodeJwt(token: string): JwtPayload {
  const payload = token.split('.')[1];
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  // decode UTF-8 tường minh — atob() trực tiếp sẽ hỏng tên tiếng Việt có dấu
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

Dùng cho ẩn/hiện UI **ngay lập tức**, trước khi có round-trip network thứ hai:

```ts
const session = buildSession(accessToken);
session.has('PERM_PRODUCTION_RECEIPT_APPROVE')   // → hiện nút "Duyệt phiếu nhập"
session.has('PERM_QUALITY_DISPOSITION')          // → hiện nút "QC"
session.has('PERM_MRP_RUN')                      // → hiện menu Planning
```

### 2.2 `GET /api/v1/auth/me` — nguồn đầy đủ, dùng để dựng session thật

```
GET /api/v1/auth/me
Authorization: Bearer <accessToken>
```

```jsonc
{
  "code": "SUCCESS",
  "result": {
    "userId": "d22df979-049b-4113-9063-6c0150a0fd5b",
    "username": "manager.hanoi",
    "email": "manager.hanoi@erp.local",
    "status": "ACTIVE",
    "roles": ["MANAGER"],
    "permissions": ["PERM_WORK_ORDER_MANAGE", "PERM_BOM_READ", "…"],  // union phẳng, giống JWT
    "scopes": [
      {
        "scopeType": "PLANT",
        "companyId": "…", "companyCode": "CO-01",
        "plantId": "…",   "plantCode": "PL-HN",
        "permissions": ["PERM_WORK_ORDER_MANAGE", "PERM_MATERIAL_ISSUE_MANAGE"]
      }
    ],
    "defaultPlantId": "…"
  },
  "message": "OK"
}
```

Khác JWT ở **hai điểm** quan trọng cho FE:
1. Có `userId`/`email`/`status` — JWT không mang.
2. Có `scopes[]` — permission **tách theo từng company/plant**, không chỉ union phẳng.

**Khuyến nghị:** decode JWT ngay sau login để UI phản hồi tức thời (§2.1), rồi gọi `/auth/me` ngay sau
đó để lấy state đầy đủ dùng cho phần còn lại của phiên làm việc.

```ts
const me = (await api.get('/api/v1/auth/me')).data.result;
// lưu { userId, username, email, roles, permissions, scopes, defaultPlantId } vào state client
```

---

## 3. `scopes[]` — permission theo đúng company/plant đang thao tác

`permissions[]` top-level là **union toàn hệ thống**, không phân biệt plant. Nếu chỉ dùng nó, một
user có `PERM_WORK_ORDER_MANAGE` ở Plant A vẫn thấy nút "Tạo Work Order" khi đang đứng ở Plant B — bấm
vào sẽ ăn `403` từ server. Muốn đúng theo plant đang chọn, đọc `scopes[]`:

```ts
function hasPermissionInPlant(me: MeResponse, perm: string, plantId: string): boolean {
  return me.scopes.some(s =>
    (s.scopeType === 'PLANT'   && s.plantId === plantId && s.permissions.includes(perm)) ||
    (s.scopeType === 'COMPANY' && s.companyId === currentCompanyIdOfPlant(plantId) && s.permissions.includes(perm)) ||
    (s.scopeType === 'GLOBAL'  && s.permissions.includes(perm))
  );
}
```

| `scopeType` | `companyId`/`plantId` | Ý nghĩa |
|---|---|---|
| `GLOBAL` | cả hai `null` | Quyền áp dụng **mọi nơi**. Không suy ra được company/plant cụ thể — vẫn phải cho user chọn plant nếu thao tác cần resource cụ thể |
| `COMPANY` | `plantId` `null` | Quyền áp dụng mọi plant **trong** company đó |
| `PLANT` | đủ cả 4 field | Quyền chỉ áp dụng đúng plant đó — dùng trực tiếp để build dropdown chọn plant |

`WAREHOUSE`-scope **không** xuất hiện trong `scopes[]` (chủ đích — FE chỉ điều hướng theo
company/plant). Quyền warehouse-scope vẫn có mặt trong `permissions[]` union phẳng.

### Chọn plant hiện tại

```ts
const plantScopes  = me.scopes.filter(s => s.scopeType === 'PLANT');
const currentPlant = plantScopes.find(s => s.plantId === me.defaultPlantId) ?? plantScopes[0] ?? null;
```

`defaultPlantId` chỉ là **gợi ý** (plant đầu tiên theo sort `companyCode, plantCode`), có thể `null`
nếu user chỉ có scope `GLOBAL`/`COMPANY`. Lưu lựa chọn thật của user vào `localStorage`, chỉ dùng
`defaultPlantId` cho lần đầu mở app.

### `X-Plant-Id` — gửi kèm, không phải tự suy

Khi gọi endpoint có `plantId` tường minh trong path/query/body (vd
`/plants/{plantId}/work-orders`), gửi kèm header `X-Plant-Id: {plantId}`. Đây là **cross-check**,
không phải nguồn dữ liệu — lệch giữa header và tham số `plantId` ⇒ **`409 STATE_CONFLICT`**. Khi user
đổi plant ở thanh trên cùng, đổi **đồng thời** cả header lẫn tham số `plantId` trong mọi request đang
gọi tiếp theo. (Chi tiết ranh giới endpoint nào cần header này: `api-guide-for-frontend.md §2.5`.)

---

## 4. Cấu trúc tên permission

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

---

## 5. Hai cơ chế kiểm tra quyền ở backend — vì sao đôi khi 403 dù có permission

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

---

## 6. Danh mục permission theo role (bản wire — tên `PERM_*` thật)

> Bảng dưới trích từ `roles-and-permissions.md`, chỉ giữ tên `PERM_*` đúng với JWT/`/auth/me`. Vài
> quyền cũ (`WO_WRITE`, `BOM_READ` không tiền tố…) trong tài liệu gốc là tên thiết kế lịch sử — đã bỏ
> khỏi bảng này, chỉ giữ những gì thật sự xuất hiện trên wire.

### ADMIN — cấu hình hệ thống, không tham gia luồng sản xuất/mua hàng

| Permission | Dùng cho |
|---|---|
| `PERM_ITEM_MANAGE` | Tạo/sửa/activate/deactivate Item |
| `PERM_UOM_MANAGE` | Tạo/sửa/activate/deactivate đơn vị tính (global, `hasPermission`) |
| `PERM_ORG_MANAGE` | Tạo/sửa Company/Plant/Warehouse (`hasPermission`, **chỉ ADMIN** kể cả MANAGER cũng không có) |
| `PERM_ACCESS_MANAGE` | Quản lý Role/Scope/User-role-assignment (`hasPermission`, **chỉ ADMIN**) |
| `PERM_AUDIT_READ` | Xem audit trail (`hasPermission`, **chỉ ADMIN**) |

> `PERM_ORG_MANAGE` và `PERM_ACCESS_MANAGE` là **duy nhất hai** permission chỉ ADMIN có — mọi
> permission "lõi" khác đều được MANAGER và/hoặc OPERATOR chia sẻ.

### MANAGER — quản lý & phê duyệt toàn bộ luồng nghiệp vụ, xem nhưng không cấu hình hệ thống

| Nhóm | Permission |
|---|---|
| Item/Org (đọc) | `PERM_ITEM_READ`, `PERM_ITEM_MANAGE`, `PERM_ORG_READ`, `PERM_UOM_READ`, `PERM_UOM_MANAGE` |
| Sales | `PERM_SALES_ORDER_READ`, `PERM_SALES_ORDER_MANAGE` |
| BOM | `PERM_BOM_READ`, `PERM_BOM_MANAGE` |
| Routing | `PERM_ROUTING_READ`, `PERM_ROUTING_MANAGE` |
| Work Center | `PERM_WORK_CENTER_READ`, `PERM_WORK_CENTER_MANAGE` |
| Shift/Calendar | `PERM_SHIFT_READ`, `PERM_SHIFT_MANAGE`, `PERM_WORK_CALENDAR_READ`, `PERM_WORK_CALENDAR_MANAGE` |
| Capacity | `PERM_CAPACITY_READ`, `PERM_CAPACITY_MANAGE` |
| Costing | `PERM_COSTING_READ`, `PERM_COSTING_MANAGE` (OPERATOR **không** có, kể cả `_READ`) |
| Planning/MRP | `PERM_PLANNING_READ`, `PERM_MRP_RUN`, `PERM_PLANNING_DEMAND_READ`, `PERM_PLANNING_DEMAND_MANAGE` |
| Work Order | `PERM_WORK_ORDER_READ`, `PERM_WORK_ORDER_MANAGE`, `PERM_WORK_ORDER_EXECUTE`, `PERM_WORK_ORDER_VARIANCE_READ` |
| Material Issue | `PERM_MATERIAL_ISSUE_MANAGE`, `PERM_MATERIAL_ISSUE_OVERRIDE`, `PERM_MATERIAL_ISSUE_APPROVE` |
| Material Reservation | `PERM_MATERIAL_RESERVATION_MANAGE` |
| Production Receipt | `PERM_PRODUCTION_RECEIPT_MANAGE`, `PERM_PRODUCTION_RECEIPT_APPROVE` |
| Production Execution | `PERM_PRODUCTION_EXECUTION_MANAGE`, `PERM_PRODUCTION_EXECUTION_READ` |
| Quality | `PERM_QUALITY_DISPOSITION` |
| Inventory | `PERM_INVENTORY_READ`, `PERM_INVENTORY_MOVE`, `PERM_INVENTORY_MANAGE` |
| Reports | `PERM_REPORT_INVENTORY_READ` (tên hiển thị; xác nhận đúng tiền tố trong Swagger nếu cần khớp chính xác) |

### OPERATOR — thực thi trực tiếp, không phê duyệt, không cấu hình

| Nhóm | Permission |
|---|---|
| Item/Org (chỉ đọc) | `PERM_ITEM_READ`, `PERM_ORG_READ`, `PERM_UOM_READ` |
| Sales (chỉ đọc) | `PERM_SALES_ORDER_READ` |
| Routing/Work Center/Shift/Calendar (chỉ đọc) | `PERM_ROUTING_READ`, `PERM_WORK_CENTER_READ`, `PERM_SHIFT_READ`, `PERM_WORK_CALENDAR_READ` |
| Capacity (chỉ đọc) | `PERM_CAPACITY_READ` |
| Work Order | `PERM_WORK_ORDER_READ`, `PERM_WORK_ORDER_EXECUTE`, `PERM_WORK_ORDER_VARIANCE_READ` |
| Production Execution | `PERM_PRODUCTION_EXECUTION_MANAGE`, `PERM_PRODUCTION_EXECUTION_READ` |
| Inventory | `PERM_INVENTORY_READ`, `PERM_INVENTORY_MOVE` |
| Reports | `PERM_REPORT_INVENTORY_READ` |

**Separation of duties — OPERATOR không bao giờ có 4 quyền này** (người thao tác không được tự duyệt
việc của chính mình): `PERM_MATERIAL_ISSUE_MANAGE`, `PERM_MATERIAL_ISSUE_APPROVE`,
`PERM_PRODUCTION_RECEIPT_MANAGE`, `PERM_PRODUCTION_RECEIPT_APPROVE`, `PERM_QUALITY_DISPOSITION`.
Nếu FE thấy một trong các quyền này xuất hiện với user role OPERATOR, đó là dấu hiệu seed permission
sai, không phải hành vi mong đợi. (Lưu ý: `PERM_MATERIAL_ISSUE_OVERRIDE` cũng chỉ dành cho MANAGER
trở lên, là phần của nhóm "phê duyệt", không dùng để tạo Material Issue thường lệ.)

> 🔴 **Danh sách trên có thể lệch nhẹ theo thời gian** (permission mới thêm ở các phase sau ngày viết
> tài liệu này). Nguồn đáng tin nhất luôn là: gọi `/auth/me` bằng chính tài khoản test, hoặc đọc
> `permissions[]` thật trong response — **không** hardcode danh sách này vào logic FE, chỉ dùng để
> tham khảo khi thiết kế màn hình.

---

## 7. Áp dụng trên FE — pattern khuyến nghị

### 7.1 Ẩn/hiện nút (permission check thô, dùng `permissions[]`)

```ts
function Can({ perm, children }: { perm: string; children: React.ReactNode }) {
  const { permissions } = useSession();
  return permissions.includes(perm) ? <>{children}</> : null;
}

<Can perm="PERM_PRODUCTION_RECEIPT_APPROVE">
  <Button onClick={approve}>Duyệt phiếu nhập</Button>
</Can>
```

### 7.2 Ẩn/hiện nút theo đúng plant đang thao tác (dùng `scopes[]`)

```ts
<Can perm="PERM_WORK_ORDER_MANAGE" plantId={currentPlant.plantId}>
  <Button onClick={createWorkOrder}>Tạo Work Order</Button>
</Can>
```

### 7.3 Guard route

```ts
function RequirePermission({ perm, children }: { perm: string; children: React.ReactNode }) {
  const { permissions } = useSession();
  if (!permissions.includes(perm)) return <Navigate to="/403" replace />;
  return <>{children}</>;
}
```

### 7.4 Luôn xử lý `403 PERMISSION_DENIED` từ server, đừng chỉ tin UI

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

---

## 8. Khi nào permission "cập nhật"

Permission trong JWT/`/auth/me` là **snapshot tại thời điểm phát hành token**. Admin gán/thu hồi
quyền cho user khác **không** có hiệu lực ngay:

| Sự kiện | Khi nào permission mới có hiệu lực |
|---|---|
| Admin gán thêm/thu hồi permission | Lần `refresh` kế tiếp (access token hết hạn: 15 phút prod / 1 giờ dev), hoặc login lại |
| User muốn thấy hiệu lực ngay | Cho nút "Làm mới phiên" gọi `POST /auth/refresh` rồi gọi lại `/auth/me` |
| Sau mỗi lần `refresh` thành công | **Luôn** gọi lại `/auth/me` — đừng chỉ decode access token mới rồi coi state cũ vẫn đúng |

---

## 9. Checklist tích hợp cho FE

- [ ] Sau `POST /auth/login`: decode JWT ngay để UI phản hồi tức thời (§2.1)
- [ ] Gọi `GET /api/v1/auth/me`, lưu `{userId, username, email, roles, permissions, scopes, defaultPlantId}` vào state client
- [ ] Ẩn/hiện nút bằng `permissions[]` (thô) hoặc `scopes[]` (theo đúng plant) — chọn theo mức độ chính xác cần
- [ ] Với nhóm quyền `PERM_ORG_*`/`PERM_ACCESS_MANAGE`/`PERM_UOM_*`/`PERM_AUDIT_READ`: coi là "chỉ chắc chắn khi scope `GLOBAL`" (§5)
- [ ] Luôn bắt và xử lý `403 PERMISSION_DENIED` tử tế — không dùng UI làm bằng chứng quyền hợp lệ
- [ ] Đổi plant ⇒ đổi **đồng thời** header `X-Plant-Id` và tham số `plantId` trong request (lệch → `409 STATE_CONFLICT`)
- [ ] Sau mỗi `POST /auth/refresh` thành công: gọi lại `/auth/me` để đồng bộ permission mới nhất
- [ ] Không hardcode danh mục permission theo role vào logic nghiệp vụ FE — luôn đọc từ response thật của user đang đăng nhập

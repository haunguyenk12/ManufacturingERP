# Giai đoạn 1 — Hướng dẫn bàn giao Backend: Tài khoản, đăng nhập và phân quyền

**Phiên bản:** v1 · **Ngày:** 09/10/2026 · **Trạng thái:** tài liệu bàn giao từ FE; chờ BE xác nhận contract và triển khai.

PO yêu cầu tạo tài liệu sau khi hoàn tất FE và các chỉnh sửa UI. Phạm vi FE/mock đã triển khai;
đây chưa phải nghiệm thu API, email thật hoặc bảo mật trên hệ thống tích hợp. Các chính sách chưa
thống nhất được liệt kê tại mục 9, không tự coi là quyết định PO.

Tài liệu này là đầu mối bàn giao mới, thay bản nháp kỹ thuật 08/10. Luồng tài khoản tại mục 2
là yêu cầu mới nhất, thay đoạn cũ “không dùng mật khẩu ban đầu” trong kế hoạch trước đây.

## 1. Phạm vi, phiên bản và cách đọc

- Trong phạm vi: tài khoản nội bộ; đăng nhập lần đầu; quên/đổi mật khẩu; phiên đăng nhập;
  quản lý vai trò, danh mục quyền, phạm vi và gán/thu hồi quyền; quyền hiệu lực và lịch sử.
- Ngoài phạm vi: đăng ký công khai, đăng nhập mạng xã hội, import tài khoản, mở rộng nghiệp vụ
  kho/sản xuất và tự phê duyệt các chứng từ sản xuất.
- FE owner: nhóm FE; BE owner: nhóm BE, chưa ghi nhận cá nhân phụ trách.
- Snapshot FE: mã nguồn làm việc sau Session 285; HEAD nền `55fbcfe` có thay đổi chưa commit.
  HEAD này không đại diện toàn bộ bản FE đã bàn giao; cần lưu commit/tag khi tích hợp chính thức.
- Evidence đã chạy: full regression Session 283 — 85 file/378 test đạt; Session 285 — build/TypeScript,
  lint và 11 test navigation/roles đạt. Không chạy lại toàn bộ test chỉ để viết tài liệu này.
- Live acceptance của contract Auth/Admin mới: **chưa chạy**. Tablet/touch/dark chưa được kiểm tra lại
  trong các phiên UI gần nhất.

Tham chiếu: kế hoạch (`CAPSTONE3_FE_DELIVERY_PLAN_2026-10-07.md`, không có trong repo),
báo cáo FE (`CAPSTONE3_GD1_FE_REVIEW_2026-10-08.md`, không có trong repo),
bản nháp lịch sử và từ điển 43 nhãn quyền (`CAPSTONE3_GD1_BE_CONTRACT_DRAFT_2026-10-08.md`, không có trong repo).

## 2. Luồng nghiệp vụ cần BE thực hiện

### 2.1 Admin tạo tài khoản và người dùng đăng nhập lần đầu

1. Admin nhập **email + mật khẩu ban đầu**, xác nhận tạo tài khoản. Không cho người dùng tự đăng ký
   hoặc tự chọn vai trò. Account mới dùng email làm username trong adapter hiện tại.
2. BE kiểm tra email trùng, lưu hash mật khẩu và trạng thái `PENDING_SETUP`. Admin gán vai trò/phạm vi
   tại chi tiết tài khoản. Việc gán quyền trước setup không cho phép account vào ERP.
3. Người dùng mở `/login`, nhập email và mật khẩu ban đầu. BE phải xác minh đúng mật khẩu trước
   khi trả nhánh `PASSWORD_SETUP` và tạo/gửi mã email.
4. FE vẫn ở `/login`, hiển thị mã email, mật khẩu mới, xác nhận mật khẩu và gửi lại mã.
   Không chuyển vào dashboard; không gọi `/me` ở nhánh này.
5. Nút **Tạo mật khẩu mới** gọi verify để nhận token hạn chế, rồi complete để lưu mật khẩu mới.
   Mật khẩu mới phải khác mật khẩu ban đầu. Complete chuyển account sang `ACTIVE`, tiêu thụ token
   và kết thúc phiên cũ; không tự đăng nhập.
6. Người dùng trở lại form login và đăng nhập bằng mật khẩu mới. Chỉ lần này BE trả bộ token ERP,
   FE gọi `/auth/v1/me`, nạp quyền/phạm vi và mở chức năng được phép.

BE không trả token ERP ở bước 3–5 và phải chặn gọi trực tiếp ERP API trước khi setup hoàn tất.
Lưu cờ bắt buộc đổi mật khẩu độc lập với trạng thái khóa: lock/unlock hoặc deactivate/activate
không được bỏ qua setup. Account chưa setup khi mở lại phải về `PENDING_SETUP`.

Admin chuyển mật khẩu ban đầu theo quy trình nội bộ. Không đưa mật khẩu/hash/mã email/token vào
account response, audit hoặc email thông báo tài khoản. Mã email thật chỉ gửi tới email đã đăng ký.

### 2.2 Quên mật khẩu, đổi mật khẩu và phiên

| Luồng | Thao tác FE | Yêu cầu BE |
|---|---|---|
| Quên mật khẩu `/forgot-password` | Nhập identifier → yêu cầu mã RESET → xác minh → mật khẩu mới | Response chung cho tài khoản không tồn tại/không đủ điều kiện; không tiết lộ account; token chỉ dùng reset |
| Đổi mật khẩu `/account/password` | Nhập mật khẩu hiện tại, mới, xác nhận | Cần phiên hợp lệ và mật khẩu hiện tại đúng; kiểm tra password policy |
| Sau setup/reset/change | Xóa phiên local, yêu cầu đăng nhập lại | Thu hồi access/refresh cũ tại server; không chỉ dựa vào FE xóa local |
| Refresh/logout | Dùng contract token hiện có | Giữ token triplet; refresh đồng thời chỉ một request ở FE; logout thu hồi phiên |
| Khóa/vô hiệu hóa/thu hồi quyền | Hiển thị lỗi hoặc trạng thái mới | Server thực thi trên API trực tiếp và phiên đang mở; quyền không được tồn tại chỉ vì JWT còn hạn |

Gửi lại mã vô hiệu hóa mã cũ; giới hạn thử/gửi và deadline do BE cung cấp. Khi verify thành công
nhưng complete lỗi, FE giữ token hạn chế để retry complete, không verify lại mã đã tiêu thụ.

### 2.3 Các màn hình chính và dữ liệu BE cung cấp

| Màn hình | Thao tác | Dữ liệu cần có |
|---|---|---|
| `/login` | Login bình thường hoặc setup ngay trên cùng màn hình | Token result hoặc `PasswordSetupLogin`, lỗi credential, deadline challenge |
| `/admin/users` | Tìm username/email, lọc status, phân trang, tạo account bằng dialog | Account list, tổng số/trang, kết quả CREATE |
| `/admin/users/{id}` | Sửa email; khóa/mở/ngừng hoạt động; gửi lại; gán/thu hồi vai trò | Account, assignments, effectiveAccess, history; kết quả command |
| `/admin/roles` | Tạo/sửa vai trò, chọn quyền theo nhóm, tìm tên/mã quyền | Roles và catalog đầy đủ; kết quả ROLE |
| `/admin/roles?tab=permissions` | Xem/tìm danh mục quyền | Catalog authority codes; tên nghiệp vụ là presentation của FE |
| `/admin/access-scopes` | Xem/quản lý phạm vi theo giao diện API hiện có | Existing scope API và resources; không có command tạo scope trong AccountCommand mới |
| `/admin/audit-logs` | Xem lịch sử theo giao diện hiện có | Existing audit integration; không suy diễn log từ danh sách hiện tại |
| Topbar/điều hướng | Chọn Plant, ngôn ngữ, tài khoản | `/me` trả scopes/permissions/defaultPlant; danh sách Plant theo nguồn hiện có |

Gán vai trò gồm role + scope + thời điểm hết hạn tùy chọn. FE hiện dùng drawer cho phần này,
dialog gọn cho tạo/sửa email, confirmation trước mutation. `/admin/permissions` chuyển về tab danh mục;
assignment thực hiện tại chi tiết account. Không cần tạo màn hình BE riêng theo layout UI.

## 3. Quyền, phạm vi và trạng thái

**Policy quản trị hiện FE đang dùng, cần PO/BE xác nhận:** có `PERM_ACCESS_MANAGE` ở scope `GLOBAL`;
không bắt buộc tên role là `ADMIN`. Quyền chỉ ở COMPANY/PLANT không cho quản trị toàn hệ thống.
Nếu policy BE khác, cần thống nhất trước bật API; không dùng tên role để đoán quyền.

| Command | Trạng thái/tác động | Kiểm tra server |
|---|---|---|
| CREATE | absent → PENDING_SETUP; lưu account/hash | Global manage, email unique, password policy |
| UPDATE | Sửa email, giữ lifecycle | Global manage, unique; xử lý challenge/email cũ theo policy mục 9 |
| STATUS LOCKED/INACTIVE | Chặn login/ERP, thu hồi phiên; giữ lịch sử | Global manage, last-admin guard |
| STATUS ACTIVE | Account đã setup → ACTIVE; chưa setup → PENDING_SETUP | Không bypass first-change; last-admin khi liên quan |
| RESEND | Thử lại gửi cho account đã có | Global manage, rate limit; không tạo account mới hoặc cấp phiên ERP |
| GRANT/REVOKE | Cấp hoặc thu hồi assignment | Global manage, role/scope hợp lệ, expiry, chống duplicate, last-admin guard |
| ROLE | Tạo/sửa membership vai trò | Global manage, code unique/bất biến khi update, quyền thuộc catalog; last-admin guard |

- Quyền hiệu lực từ account active + role/catalog hợp lệ + assignment chưa expired/revoked + scope.
- Quyền Plant B không lan sang Plant A. `/me.permissions` dạng union không được dùng để bỏ qua
  kiểm tra scope ở endpoint nghiệp vụ; sử dụng scopes tương ứng với resource được truy cập.
- GET account/detail trực tiếp theo UUID cũng phải kiểm tra quyền; ẩn nút/route FE không thay server auth.
- Bảo vệ quản trị viên cuối cùng trong transaction, gồm sửa membership của role và các request đồng thời.
  Cách xử lý grant quản trị tự hết hạn cần chốt tại mục 9.
- Không tự bổ sung granular permission từ nhãn giao diện. `PERM_ACCESS_MANAGE` hiện là quyền rộng;
  wire codes vẫn là `PERM_*`, dù FE chỉ hiển thị nhãn nghiệp vụ.

## 4. API cần cung cấp

### 4.1 Đường dẫn và existing/proposed

BE có prefix `/api`; browser gọi proxy `/api/backend`. Các path dưới đây tính **sau `/api`**.
API dưới là contract **đề xuất đang có trong adapter FE**, chưa được xác nhận triển khai live.

| Method / path | Request | Success `result` | Retry |
|---|---|---|---|
| GET `/admin/users/v1/accounts` | `search`, `status?`, `page=0`, `size=20` | PaginatedResult<Account> | Read retry |
| GET `/admin/users/v1/accounts/{id}` | UUID | AccountDetail | Read retry |
| GET `/admin/users/v1/accounts/references` | — | `{roles, scopes, permissions}` | Read retry |
| POST `/admin/users/v1/accounts/commands` | AccountCommand | Account hoặc AccessRole hoặc explicit null | Idempotency-Key |
| POST `/auth/v1/login` | `{username, password}` | AuthTokenResult hoặc PasswordSetupLogin | Cần rate limit; adapter login chưa gửi idempotency key |
| POST `/auth/v1/password-challenges` | `{identifier, purpose: SETUP/RESET, previousId?}` | Challenge | Idempotency-Key |
| POST `/auth/v1/password-challenges/verify` | `{challengeId, code}` | `{setupToken}` | Idempotency-Key |
| POST `/auth/v1/password-challenges/complete` | `{setupToken, password}` | null hoặc result omitted | Idempotency-Key |
| POST `/auth/v1/password/change` | `{currentPassword, password}` | null hoặc result omitted | Bearer + Idempotency-Key |

Account commands hiện được FE unwrap bằng hàm yêu cầu có field `result`: thành công không có payload
cần trả **`result: null`**. Nếu BE dùng NON_NULL và bỏ field này, cần sửa adapter sau khi thống nhất;
không coi response thiếu `result` là đã tương thích. Complete/change đã cho phép result omitted.

**Existing integration giữ lại:** login/me/refresh/logout; users `/users/v1`; admin user actions
`/admin/users/v1`; role/permission/scope/assignment `/access/v1`. Scope CRUD/resources và audit
không được thay bằng references snapshot. Existing wire contract nằm trong
`src/features/admin/api/adminApi.ts` và `types.ts`; phiên bàn giao này không kiểm tra lại live.
BE có thể giữ REST endpoints riêng thay dispatcher nếu cung cấp đủ hành vi và thống nhất mapping
trước sửa adapter. Không đổi tên path rồi coi FE đã tự tương thích.

### 4.2 Envelope, phân trang và lỗi

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "content": [], "page": 0, "size": 20,
    "totalElements": 0, "totalPages": 0
  }
}
```

List page bắt đầu từ 0, metadata đúng với search/filter. `result` bắt buộc cho GET có dữ liệu.
Envelope lỗi: `code`, `message`, `errors?: [{field, message}]`; không trả stacktrace/credential.
UUID strings cho entity ID; thời gian ISO-8601 UTC; `expiresIn` tính bằng giây.
Email unique không phân biệt hoa thường; BE thống nhất normalization, không đổi âm thầm username cũ.

References hiện là **complete snapshot**: không trả một trang 100 quyền rồi giả là toàn bộ catalog.
FE tìm kiếm/phân trang picker trên snapshot. Nếu catalog lớn, BE/FE chốt endpoint search/page riêng.

### 4.3 DTO sát với adapter FE

```ts
type Account = {
  id: string; username: string; email: string;
  status: 'PENDING_SETUP' | 'ACTIVE' | 'LOCKED' | 'INACTIVE';
  emailDelivery: 'SENT' | 'FAILED';
};
type AccessRole = {
  id: string; code: string; name: string; description: string; permissions: string[];
};
type AccessScope = {
  id: string; name: string; type: 'GLOBAL' | 'COMPANY' | 'PLANT'; plantId: string | null;
};
type Grant = {
  id: string; userId: string; roleId: string; scopeId: string;
  expiresAt: string | null; revoked: boolean;
};
type AccountDetail = {
  account: Account; assignments: Grant[];
  effectiveAccess: {
    permission: string; assignmentId: string; role: string; scope: string; plantId: string | null;
  }[];
  history: {id: string; userId: string; action: string; actor: string; at: string}[];
};
type Challenge = {id: string; expiresAt: string; resendAt: string};
type PasswordSetupLogin = {kind: 'PASSWORD_SETUP'; email: string; challenge: Challenge};
type AuthTokenResult = {
  accessToken: string; refreshToken: string; tokenId: string; expiresIn: number;
  deviceId: string | null; sessionKicked: boolean;
};
type AuthMeResult = {
  userId: string; username: string; email: string; status: string;
  roles: string[]; permissions: string[];
  scopes: {
    scopeType: 'GLOBAL' | 'COMPANY' | 'PLANT'; companyId: string | null;
    companyCode: string | null; plantId: string | null; plantCode: string | null;
    permissions: string[];
  }[];
  defaultPlantId: string | null;
};
type AccountCommand =
  | {kind: 'CREATE'; username: string; email: string; password: string}
  | {kind: 'UPDATE'; userId: string; email: string}
  | {kind: 'STATUS'; userId: string; status: Account['status']}
  | {kind: 'RESEND'; userId: string}
  | {kind: 'GRANT'; userId: string; roleId: string; scopeId: string; expiresAt: string | null}
  | {kind: 'REVOKE'; userId: string; assignmentId: string}
  | {kind: 'ROLE'; id?: string; code: string; name: string; description: string; permissions: string[]};
```

`permissions` là authority codes, không phải nhãn dịch hoặc permission UUID. Existing membership API
có thể dùng permission IDs riêng; BE phải map đúng khi tiếp nhận command mới. Scope snapshot không có
companyId/resource list: muốn hiển thị chi tiết COMPANY cần thống nhất nguồn existing scopes/resources.
Không suy ra Warehouse từ PLANT hoặc từ tên scope.

Mẫu response login lần đầu (toàn bộ giá trị minh họa, không phải token thật):

```json
{
  "code": "SUCCESS", "message": "Success",
  "result": {
    "kind": "PASSWORD_SETUP", "email": "user@example.com",
    "challenge": {
      "id": "<opaque-challenge-id>",
      "expiresAt": "2026-10-09T08:05:00Z", "resendAt": "2026-10-09T08:00:30Z"
    }
  }
}
```

Các mốc trong mẫu không quyết định TTL production. Nhánh này tuyệt đối không chứa access/refresh/tokenId.
Confirm password chỉ kiểm tra ở FE, không gửi BE. History cần tên actor, thời gian và nguồn audit thật;
không trả UUID thay tên thông thường, không tái dựng lịch sử từ quyền hiện tại.

## 5. Transaction, email, phiên và retry

| Event | Account/challenge/email/session | Kho, WIP, tiền |
|---|---|---|
| CREATE | Commit account độc lập kết quả gửi email; lỗi gửi vẫn trả account để retry | N/A |
| RESEND | Retry account/challenge đúng mục đích, không tạo user trùng; rate limit | N/A |
| VERIFY | Tiêu thụ code một lần, cấp token hạn chế có hạn, bind user/purpose/challenge | N/A |
| COMPLETE | Kiểm tra lại token/state/policy, consume atomic, lưu hash, thu hồi phiên | N/A |
| LOCK/INACTIVE | Chặn login/ERP, thu hồi phiên; giữ chứng từ và lịch sử | N/A |
| GRANT/REVOKE/ROLE | Đổi effective access, cập nhật audit và profile của user bị ảnh hưởng | N/A |

SETUP request chỉ hợp lệ sau xác minh mật khẩu ban đầu: `previousId` phải là challenge hợp lệ của
login đó. Admin RESEND không được cấp capability setup công khai hoặc giúp bypass credential check.
RESET là recovery công khai với response chung, kể cả identifier không tồn tại.

BE cần thống nhất `SENT` là đã enqueue hay đã giao email và mục đích email khi CREATE/RESEND.
Adapter hiện chỉ có SENT/FAILED; không bịa “đã đến hộp thư” từ response thành công. Khi gửi thất bại,
giữ account để Admin retry; email/code không được làm rollback account thành công.

Idempotency-Key phải bind caller/challenge + payload fingerprint; retry cùng payload dùng cùng key,
đổi payload dùng key mới; cùng key khác payload trả conflict. Replay sau timeout verify/complete
phải phục hồi kết quả cũ an toàn, vẫn bảo đảm code/token chỉ consume một lần. Không lưu secrets thô
trong audit hoặc log replay; BE thiết kế lưu trữ/retention an toàn. Login cũng cần chống gửi email dồn
khi người dùng submit lặp, dù adapter hiện chưa gửi idempotency key cho login.

Sau command, FE invalidate account queries và refetch `/auth/v1/me` của actor; không rotate token
chỉ để refresh quyền. BE phải enforce quyền mới với các phiên khác. Global account API không cần
Plant header; endpoint nghiệp vụ theo Plant vẫn kiểm tra resource scope tại server.

## 6. Lỗi và hành vi giao diện

HTTP mapping dưới đây là đề xuất để BE xác nhận; code/field errors cần khớp adapter trước tích hợp.

| Tình huống | HTTP / code đề xuất | FE và retry |
|---|---|---|
| Sai credential | 401 `INVALID_CREDENTIALS` | Giữ dữ liệu, không tiết lộ pending setup |
| Cần setup | `PASSWORD_SETUP_REQUIRED` khi gọi luồng không hợp lệ | Login bằng mật khẩu ban đầu; không mở setup công khai |
| Account/phiên bị khóa/thu hồi | 401/403 `ACCOUNT_LOCKED`, `SESSION_REVOKED` | Kết thúc phiên/chặn login |
| Mã sai/hết hạn/đã dùng/vượt thử | 400/422/429 `CODE_INVALID`, `CODE_EXPIRED`, `CODE_USED`, `CODE_ATTEMPTS` | Lỗi cụ thể, retry/gửi lại theo deadline; không reset mật khẩu nhập |
| Gửi lại quá sớm | 429 `RESEND_TOO_SOON` | Chờ resendAt; BE vẫn enforce rate limit |
| Password không hợp lệ | 422 `PASSWORD_POLICY` + field errors | Giữ input, sửa trường lỗi |
| Trùng tài khoản/role/grant | 409 `DUPLICATE_ACCOUNT`, `DUPLICATE_ROLE`, `DUPLICATE_ASSIGNMENT` | Không tạo bản thứ hai, giữ form |
| Tham chiếu/hạn/dữ liệu sai | 400/422 `INVALID_REFERENCE`, `INVALID_EXPIRY`, `INVALID_INPUT` | Giữ lựa chọn, báo lỗi trường |
| Last admin/không quyền/không thấy | 409/403/404 `LAST_ADMIN`, `PERMISSION_DENIED`, `NOT_FOUND` | Không báo thành công giả; không retry permission denied |
| Timeout/replay conflict | Network / 409, code conflict cần BE chốt | Giữ attempt key/input, đối soát và retry an toàn |

Lỗi email cần trả trạng thái gửi + lỗi có thể hành động; không trả raw SMTP secrets hoặc biến failure
thành SUCCESS giả. Message BE phải cụ thể và phù hợp để hiển thị; known codes có nhãn EN/VI ở FE.

## 7. Thứ tự BE triển khai và tích hợp

1. Xác nhận các điểm mục 9 và mapping endpoint/DTO. Công bố OpenAPI, examples success/error và policy.
2. Chuẩn bị migration: email unique, hash, setup-required flag, challenge/token state, grants/expiry,
   session revocation, audit, idempotency. Seed catalog/role/scope theo policy đã thống nhất.
3. Ưu tiên vertical slice: CREATE account → login ban đầu → gửi mã → verify → complete → login → `/me`.
4. Bổ sung resend/email failure, forgot/change password, refresh/logout, lock/unlock/inactive.
5. Bổ sung list/detail/reference, role/grant/revoke, effective access và last-admin concurrency guard.
   Duy trì existing scopes/resources/audit contract; không cắt mất catalog hoặc lifecycle hiện có.
6. Triển khai test environment với hai user khác quyền, hai Plant và email test. Account pending cũ
   thiếu initial password phải được migrate/cấp lại theo quy trình nội bộ, không tự bịa credential.
7. Sau BE deploy và thống nhất contract, FE bật cấu hình rồi chạy mục 8. Chỉ bật production sau acceptance.

```dotenv
BACKEND_API_ORIGIN=http://localhost:8080
NEXT_PUBLIC_API_BASE_URL=/api/backend
NEXT_PUBLIC_DATA_SOURCE=api
NEXT_PUBLIC_ACCOUNT_API_ENABLED=true
```

Origin không chứa `/api` hoặc dấu `/` cuối. Đây là cấu hình minh họa sau BE sẵn sàng, **chưa bật**
trong phiên bàn giao. Các biến NEXT_PUBLIC cần rebuild FE. `api` + flag false trả `API_NOT_READY` cho
service mới; không tự fallback sang mock khi API lỗi. Dùng `mock` cho demo chưa tích hợp.

## 8. Checklist nghiệm thu BE + FE

Tất cả case dưới đây đang **chờ API/email thật**, không đánh dấu pass từ fixture.

| ID | Dữ liệu/thao tác | Kết quả cần chứng minh |
|---|---|---|
| G1-01 | Admin tạo account, login ban đầu, nhập mã/new password, login lại | Đúng flow mục 2; ERP API trực tiếp bị chặn trước complete |
| G1-02 | Sai mật khẩu ban đầu; setup request không có previousId hợp lệ | Không lộ pending status hoặc cấp setup capability |
| G1-03 | Mã sai/hết hạn/đã dùng/vượt thử; resend rồi dùng mã cũ | Lỗi đúng; deadline/rate limit server; mã cũ vô hiệu |
| G1-04 | Timeout verify/complete, retry cùng key; hai request consume đồng thời | Replay an toàn; không consume hai lần hoặc mất khả năng retry complete |
| G1-05 | Email gửi thất bại, RESEND; create/retry/concurrent create cùng email | Account không mất/trùng; delivery state thật, email không có credential |
| G1-06 | Forgot cho identifier tồn tại/không tồn tại/locked/inactive | Response recovery chung; eligibility do BE enforce; không lộ account |
| G1-07 | Reset/change xong thử access và refresh cũ | Cả hai bị từ chối; FE yêu cầu login lại |
| G1-08 | Lock/inactive rồi activate account chưa setup | Không bypass setup; lịch sử/chứng từ còn nguyên |
| G1-09 | Custom role GLOBAL manage; role chỉ Plant; GET detail bằng UUID trực tiếp | Theo policy đã chốt; Plant-only không quản trị global |
| G1-10 | Operator Plant A + Manager Plant B | Quyền/source assignment đúng; quyền duyệt B không lan sang A |
| G1-11 | Revoke/expire grant, đổi role membership khi user đang mở phiên | Server API và `/me` phản ánh thay đổi; FE refresh đúng |
| G1-12 | Hai admin cùng lock/revoke/sửa role; grant admin có expiry | Không làm mất toàn bộ quản trị hợp lệ theo policy |
| G1-13 | Catalog/reference >100 record; tìm/lọc/phân trang; reload | Đủ dữ liệu, metadata đúng, không UUID thay tên; dữ liệu lưu thật |
| G1-14 | Login/logout/refresh đồng thời, TOKEN_EXPIRED và permission denied | Một refresh ở FE; không retry 403 hoặc phục hồi phiên đã logout |
| G1-15 | Sửa email khi challenge đang tồn tại | Mã/token cũ và email mới được xử lý theo quyết định mục 9 |
| G1-16 | Unauthorized/stale command, timeout, field validation | Error envelope đúng, FE giữ input; không mutation ngoài quyền |

BE/FE bổ sung evidence ngày/version môi trường, response đã loại token/credential và kết quả từng case.

## 9. Điểm cần BE/PO xác nhận trước rollout

| Điểm còn mở | Owner | Đề xuất/cần trả lời | Chặn gì |
|---|---|---|---|
| Admin GLOBAL permission hay bắt buộc role ADMIN | PO + BE | FE hiện dùng GLOBAL `PERM_ACCESS_MANAGE`, cho custom role hợp lệ | Security acceptance/Admin access |
| TTL mã/token, số lần thử, resend/rate limit, password policy | BE + PO khi ảnh hưởng UX | Công bố policy và errors/deadlines; số demo không là production | Challenge/password acceptance |
| Email khi CREATE/RESEND; SENT là queued hay delivered | BE + FE/PO | Xác định mục đích notification; setup code sau login credential đúng | Email/UI semantics |
| Thay email, account pending cũ | BE + PO | Invalidate challenge/token cũ; migration/cấp initial password theo quy trình nội bộ | Lifecycle/migration |
| Last-admin và grant quản trị có hạn | BE + PO | Kiểm tra atomic; chốt cách bảo vệ khi grant tự hết hạn | Tránh mất toàn bộ quản trị |
| Endpoint riêng hay dispatcher, result null/omitted | BE + FE | Giữ DTO/hành vi hoặc cung cấp mapping; commands hiện cần result field | Adapter compatibility |
| Thu hồi access/refresh và quyền phiên đang mở | BE | Chọn cơ chế enforce server, công bố độ trễ nếu có | Session/security acceptance |
| Role templates/catalog/company scope, audit thay đổi role | BE + PO | Xác nhận seed/membership, nguồn resource detail và user bị ảnh hưởng | RBAC/audit acceptance |

Không có quyết định mới nào ở bảng này được tự coi là đã duyệt. BE phản hồi từng dòng và ghi người
chốt/ngày; FE chỉ sửa source sau PO duyệt theo quy ước hiện tại.

## 10. Điểm tra cứu source và trạng thái bàn giao

- DTO mới: `src/features/admin/phase1/types.ts`.
- Endpoint/headers mới: `src/features/admin/phase1/services/accountApi.ts`.
- Chọn mock/API, invalidate/profile refresh: `src/features/admin/phase1/services/accounts.ts`.
- Existing Admin wire contract: `src/features/admin/api/adminApi.ts`, `types.ts`, `adapters.ts`.
- Existing auth/session: `src/types/api-auth.ts`, `src/features/auth/services/authApi.ts`.
- Luồng setup/reset/change: `src/features/auth/components/PasswordFlow.tsx`.
- Envelope: `src/types/api.ts`, `src/lib/api/envelope.ts`; route policy: `src/lib/authorization/routePolicies.ts`.
- Nhãn quyền: `src/lib/permissionPresentation.ts`; UI không thay đổi authority codes BE.

**Trạng thái hiện tại:** tài liệu đã tạo để PO gửi BE → chờ BE xác nhận contract/owner → BE triển khai
→ tích hợp/acceptance. Chưa xác nhận BE đã nhận tài liệu, chưa deploy hoặc gửi thông điệp bên ngoài.

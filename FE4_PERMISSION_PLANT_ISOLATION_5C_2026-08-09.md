# FE-4 Section 5C — Permission and Plant-Isolation Acceptance

## Result

- Run date: `2026-08-09`
- Backend origin: `http://26.154.206.236:8080` from `.env.local` (Radmin VPN)
- Accounts: Admin, `manager.a`, `operator.a`
- Mutations: none
- Plant isolation: **PASS**
- Admin/RBAC denial: **PASS**
- Organization permission mapping: **FE FIXED**
- Item Master permission contract: **BLOCKED — backend clarification/fix required**

No credential or token is stored in this report.

## Account scopes

- Admin has a GLOBAL scope and can read the disposable WOTEST Plant.
- Manager and Operator are scoped only to `DEMO-CO / PLANT-A`
  (`e5dd4166-46b1-4dd0-942b-ca8fee642b47`).
- Neither Manager nor Operator has `PERM_ACCESS_MANAGE`; both were correctly denied Admin Users.
- Manager has approval/QC/variance/override permissions; Operator has execution, receipt-create and
  material-issue permissions but not approval/QC/override permissions.

## Live authorization matrix

| Account | Check | HTTP | Trace |
|---|---|---:|---|
| Manager | Admin Users denied | 403 | `d2d99f7214434a10` |
| Operator | Admin Users denied | 403 | `20f9486b50a04792` |
| Manager | Plant-A Warehouses | 200 | `452477054ce84931` |
| Manager | WOTEST Warehouses denied | 403 | `72e1e2de72364371` |
| Operator | Plant-A Warehouses | 200 | `39e35d1348de4520` |
| Operator | WOTEST Warehouses denied | 403 | `c1b99c1a90a049ca` |
| Manager | Plant-A Work Orders | 200 | `38ad378910104422` |
| Manager | WOTEST Work Orders denied | 403 | `7592b10e4f8448fd` |
| Manager | WOTEST aggregate Work Order detail denied | 403 | `c2f15f6ad3224c57` |
| Operator | Plant-A Work Orders | 200 | `a80b6d6601734701` |
| Operator | WOTEST Work Orders denied | 403 | `cfbcbbcb497a4ef3` |
| Operator | WOTEST aggregate Work Order detail denied | 403 | `313fce8359d84684` |
| Manager | Plant-A Production Receipts | 200 | `04f689d1d69f45ba` |
| Manager | WOTEST Production Receipts denied | 403 | `12af50d9e40c4c51` |
| Operator | Plant-A Production Receipts | 200 | `75aae0177e314dbe` |
| Operator | WOTEST Production Receipts denied | 403 | `cdc2800d1e844bd2` |
| Admin | Admin Users | 200 | `1bc9806cf24f40a3` |
| Admin | WOTEST Work Orders | 200 | `5da0cdab9b594e4c` |

The matching `X-Plant-Id` header was supplied only for endpoints with an explicit Plant path/query.
Aggregate Work Order detail was tested without a global Plant header and was still correctly scoped by
the backend.

## Frontend Organization permission fix

The live permission catalog publishes:

```text
PERM_ORG_READ
PERM_ORG_MANAGE
```

FE incorrectly used nonexistent `PERM_ORGANIZATION_READ/MANAGE`, hiding Organization Structure from
otherwise authorized users. The semantic Plant/Warehouse constants now map to the exact live
`PERM_ORG_*` codes, with a regression test locking the wire values.

## Backend support required — Item Master permission contract

The live catalog contains 51 permissions but no `PERM_ITEM_READ` or `PERM_ITEM_MANAGE`. None of the
three `/auth/me` responses contains an Item permission either. Results are inconsistent with the
permission-driven FE contract:

| Account | Request | HTTP | Trace |
|---|---|---:|---|
| Admin | WOTEST company Items | 200 | `c873cf22523d42e0` |
| Manager | own DEMO-CO Items | 403 | `c6ab5ff69e1d44ff` |
| Operator | own DEMO-CO Items | 403 | `7a5020a81a6544c9` |

FE currently gates Item Master with semantic `ITEM_VIEW/CREATE/EDIT/DELETE`, but their provisional
wire values `PERM_ITEM_READ/MANAGE` can never match `/auth/me`; therefore even Admin receives the FE
No Permission state although the backend allows Admin to read Items.

Backend should either:

1. publish `PERM_ITEM_READ` and `PERM_ITEM_MANAGE` in the permission catalog and return the assigned
   codes from `/auth/me`; or
2. document the exact existing permission codes that authorize Item read/manage.

FE must not map Item to Inventory permission: Manager and Operator both hold Inventory read/manage
permissions, but their own-company Item requests are denied. FE also must not add an undocumented
role-name bypass.

## Quality gate

- Navigation/Auth focused tests: `10/10` passed across 2 files.
- Strict TypeScript: passed.
- Focused ESLint: zero errors; existing CRLF/Prettier warnings only.
- No translation or API route boundary changed.


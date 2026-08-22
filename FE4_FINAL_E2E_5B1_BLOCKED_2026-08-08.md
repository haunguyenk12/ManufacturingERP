# FE-4 Final E2E — Substep 5B-1 Backend Blocker

## Result

- Run date: `2026-08-08`
- Scope attempted: Sales Order → Planning demand → MRP → MAKE suggestion → Work Order conversion
- Result: **BLOCKED before Work Order conversion**
- Cleanup: the disposable Sales Order was cancelled; no Work Order or downstream production data was created

## Disposable documents

```text
Sales Order ID:       68c27f6a-12a4-4aac-a354-af4e2709c25a
Sales Order number:   FE4-E2E-0808155545
Final SO status:      CANCELLED
SO line ID:           26343373-55fc-4bb9-93fb-34e84f01cab1
Planning demand ID:   ad29953e-1fd3-473b-b2e5-fa40f455b63c
Demand quantity:      2 EA
MRP run ID:           ab1fe3eb-f377-4e62-9c29-fcaad4664e32
MRP run code:         RUN-AB1FE3EB
MRP run status:       COMPLETED
Suggestion ID:        1fc170d9-c78a-42e2-b061-ce7ae39c8561
Suggestion type:      MAKE
Suggestion status:    DRAFT
Exception state:      BLOCKED
```

The MRP run remains as immutable diagnostic history. Cancelling the Sales Order removed the open
demand. No Work Order was created.

## Backend response

The MAKE suggestion returned these messages:

```text
MATERIAL_SHORTAGE
MISSING_ROUTING
SYSTEM_FALLBACK_USED
```

The suggestion cannot be approved/converted while `exceptionState=BLOCKED`.

## Requirement evidence

MRP requirement detail for the same run proves that the component is covered:

| Item | Level | Gross required | Available | Projected available | Net required | Status | Setting source |
|---|---:|---:|---:|---:|---:|---|---|
| `WOTEST-FG-1786161607-15871A9F` | 0 | 2 | 0 | 0 | 2 | `SHORTAGE` | `SYSTEM_DEFAULT` |
| `WOTEST-RM-1786161607-15871A9F` | 1 | 4 | 200 | 200 | 0 | `COVERED` | `SYSTEM_DEFAULT` |

For both rows, reserved quantity, open supply, safety stock and excluded-lot count are zero.

Therefore:

- the raw material needed by the BOM is **not short**;
- the level-0 FG shortage is the independent demand that the MAKE proposal is supposed to cover;
- `MISSING_ROUTING` is the concrete conversion blocker;
- backend should also verify why `MATERIAL_SHORTAGE` blocks the MAKE suggestion when every component
  requirement is `COVERED`.

## Trace IDs

```text
SO create:             16ff4bb7cfdd43e1
SO confirm:            1b5f573e01b4425d
Planning demands read: c7eeabd8604e4bee
MRP create:            f5eb5e0d278442db
Suggestions read:      64d0ac686a6d4242
SO cleanup/cancel:     975a694599d14fd7
```

## Backend support requested

Please provide one of the following for the disposable WOTEST fixture:

1. an ACTIVE Routing resolvable by Planning for Finished Good
   `399a1494-333c-4b9e-ac01-5a0e2138ed47` in Plant
   `87f93598-f7ce-4d56-89e9-933f3cd74a2c`; or
2. a backend-supported aggregate-production contract that allows a new MAKE suggestion to convert
   without a Routing, consistent with the existing aggregate Work Order behavior.

Please also verify the `MATERIAL_SHORTAGE` exception calculation for this run. The FE will retry with
a new unique Sales Order only after backend confirms the supported contract/deployment. FE will not
create a standalone Routing integration, manually bypass MRP, or add an atomicity workaround.

# FE-4 Final E2E — Substep 5B-3 HOLD Gate Blocker

## Result

- Run date: `2026-08-09`
- Backend origin: `http://26.154.206.236:8080` from `.env.local` (Radmin VPN)
- Scope reached: Receipt create → submit → approve → pre-QC inventory verification
- Result: **BLOCKED before QC**
- Cause: approved `NON_TRACKED` output became inventory-available before QC disposition

No credential, token or idempotency key is stored in this report.

## Account scope note

`operator.a` and `manager.a` have the relevant coarse Receipt permissions but are scoped only to
`DEMO-CO / PLANT-A`, not the disposable WOTEST Plant. Operator candidate access to WOTEST correctly
returned 403 (trace `1eef98f8dd0447b2`). FE did not alter assignments or expand access. Admin was
used for the functional WOTEST lifecycle; role/isolation acceptance remains in 5C.

## Preflight

```text
Work Order:             FE4-WO-0809-130437
Work Order ID:          458b0bd2-f8a0-4d49-b50e-1663c36f7a52
WO state:               COMPLETED
Available to Receipt:   2 EA
Existing Receipts:      0
Finished Good balance:  none
Sales Order state:      IN_PRODUCTION
SO fulfilled/open:      0 / 2 EA
```

## Receipt lifecycle

```text
Receipt:                PR-690DC47B
Receipt ID:             01e51346-6bdb-4ea1-b402-52d9af0c1081
Quantity:               2 EA
Tracking:               NON_TRACKED
Create result:          DRAFT
Submit result:          PENDING_APPROVAL
Approve result:         APPROVED
Create trace:           9cf816703d474502
Submit trace:           a2952f6bcb5346a6
Approve HTTP trace:     14033da2003d45d5
```

After create and submit, `stockMovementId` and `lotId` were null and Finished Good balance remained
absent, which is correct.

## Defect after approve

Approved Receipt state:

```text
status:                 APPROVED
qcResult/qcAt:          null / null
outputLotStatus:        null
lotId/lotNumber:        null / null
stockMovementId:        433f04e4-725e-4be8-8504-0d0d745ef5f4
receipt read trace:     0fbce84b380b4348
```

Inventory balance immediately before any QC command:

```text
balanceId:              8e544667-02bd-45b1-9160-6d0f930ba27e
onHand quantity:        2 EA
available quantity:     2 EA       <-- expected 0 before QC
reserved quantity:      0 EA
lotId/lotCode:          null / null
balance read trace:     bf083eb18f284b1a
```

The RECEIVE movement exists and references the Work Order:

```text
movementId:             433f04e4-725e-4be8-8504-0d0d745ef5f4
movementType/direction: RECEIVE / IN
quantity:               2 EA
referenceType:          WORK_ORDER
referenceId:            458b0bd2-f8a0-4d49-b50e-1663c36f7a52
movement read trace:    27f40cd4abb34a80
```

Sales Order fulfillment was still 0/2, but the stock balance already exposed the uninspected output
as available to reservation, MRP and general inventory consumers.

## Expected contract

- Receipt approval increases on-hand inventory but keeps available quantity at zero.
- Approved output is logically `HOLD` until QC, including `NON_TRACKED` output.
- QC `AVAILABLE` alone releases available quantity and fulfills Sales Order demand.
- QC `REJECTED` must remain unavailable and must not fulfill demand.

The current response has no lot/status representation for NON_TRACKED output, so FE cannot repair or
infer the missing quality hold. A frontend subtraction/workaround would be non-atomic and could
diverge from MRP, reservation and inventory APIs.

## Backend support requested

1. Make approval of `NON_TRACKED` Production Receipts post quantity as on-hand but unavailable while
   `qcResult` is null.
2. Make QC AVAILABLE atomically release that held quantity; QC REJECTED must leave it unavailable and
   preserve traceability.
3. If `outputLotStatus` remains null for NON_TRACKED output, document the authoritative state that FE
   should display as pending quality.
4. Remediate existing Receipt `01e51346-6bdb-4ea1-b402-52d9af0c1081` and balance
   `8e544667-02bd-45b1-9160-6d0f930ba27e` without deleting audit/trace history, then confirm whether FE
   should call QC AVAILABLE on this Receipt or create a fresh replacement flow.

FE intentionally has not called QC, variance or Close after detecting the leak.

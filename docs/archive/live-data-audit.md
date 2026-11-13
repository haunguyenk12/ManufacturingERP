# OmniPlant presentation refresh — Phase 2 live-data audit

Date: 2026-08-14  
Environment: API mode, `DEMO-CO · PLANT-A`  
Fixture prefix: `SLIDE-260814-*`

## Result

The API-backed production story now has stable records for `Sales Order -> MRP -> Work Order -> Material Issue -> Production Execution -> Production Receipt/QC -> Inventory`.

| Capture area | Live result | Decision |
|---|---|---|
| Dashboard | 6 configured items, 3 warehouses, 1 LOW_STOCK, 2 REORDER_NEEDED, recent production movements | Ready |
| Item Master | 7 rows across RAW_MATERIAL, WIP and FINISHED_GOOD | Ready |
| Stock / stock detail | RAW stock has On Hand/Reserved/Available diversity after reservations; FG has multiple output lots | Data ready; Backend availability defect blocks final capture |
| Inventory Lots | RAW has AVAILABLE/HOLD/REJECTED; FG has AVAILABLE/HOLD/REJECTED from production/QC | Data ready; Backend availability defect blocks final capture |
| Stock Movements | RECEIVE, ISSUE, ADJUST, LOT_STATUS_CHANGE and production-generated receipt/QC movements | Ready |
| BOM list/detail/tree | ACTIVE + DRAFT; finished good and WIP BOM; two-level tree with scrap rates | Ready; correct garbled legacy BOM description before capture |
| Production Estimation | Live Backend endpoint returns OmniDesk shortage data, but the current route still uses the legacy numeric mock store | Requires frontend API migration before capture |
| Sales Order list | DRAFT, CONFIRMED, PARTIALLY_FULFILLED and FULFILLED are visible | Ready |
| Sales Order detail | `SLIDE-260814-SO-CONF` has two lines; FE0 line shows 6 ordered, 3 allocated and 3 fulfilled while OmniDesk remains 120/0/0 | Ready |
| Planning | Confirmed demand is visible; completed run has 8 requirements, 6 shortages, 3 MAKE and 3 BUY suggestions | Ready |
| Planning Run detail | Source route exists, but direct/live navigation returns Next.js 404 | Frontend fix required |
| Work Order list | PLANNED, BLOCKED, RELEASED, IN_PROGRESS and COMPLETED all visible in one list | Ready |
| Work Order detail | Snapshot, component requirement, reservation/issue and receipt quantities are populated | Ready; evaluate adding variance section in Phase 3 |
| Material Issues | `SLIDE-260814-WO-RELEASED` exposes an ACTIVE reservation with 4 KG remaining and issue history | Ready |
| Production Execution | `SLIDE-260814-WO-PARTIAL` shows 3/6 good, 0.2 scrap, 0.4 rework, timing and history | Ready |
| Production Receipts/QC | DRAFT, PENDING_APPROVAL, APPROVED/HOLD, QC AVAILABLE and QC REJECTED visible together | Ready |
| Inventory after QC | FG warehouse contains AVAILABLE, HOLD and REJECTED production lots | Data ready; Backend availability defect blocks final capture |

## Backend support required

1. **HOLD/REJECTED availability is incorrect.**
   - Warehouse: `dc5d66e5-d179-43eb-9580-8e006bfbf79a` (`WH-FG`).
   - HOLD lot `C2-DEMO-LOT-0810154309` returns on-hand `2`, available `2`, quality-hold `0`.
   - REJECTED lot `SLIDE-260814-FG-REJECTED` returns on-hand `1`, available `1`, quality-hold `0`.
   - Expected: HOLD/REJECTED must be excluded from available; HOLD should contribute to the quality-hold quantity.
   - Affected reads: `GET /inventory/lots?warehouseId=...` and `GET /inventory/balances?warehouseId=...`.

2. **Planning Run idempotency did not replay.**
   - Repeated `POST /planning-runs` used the same `Idempotency-Key` and identical payload.
   - Backend created `RUN-63033D7C`, `RUN-EA182AFB` and `RUN-4C77B225` instead of returning the first run.
   - Please either enforce idempotent replay or explicitly document that this mutation ignores the header so the client can avoid transport replay.

3. **Suggestion conversion was not atomic.**
   - Suggestion `492763fd-8561-401e-b307-6fbcef551618` created Work Order `7e5deaaf-d62d-445e-87e9-9772ee708c51`.
   - The suggestion remained `APPROVED` with null converted-reference fields.
   - Retrying conversion returned `RESOURCE_ALREADY_EXISTS` for `SLIDE-260814-WO-PARTIAL`.
   - Expected: successful Work Order creation and suggestion conversion state commit together, or retry should reconcile to the already-created Work Order.

## Frontend Phase 3 work identified

- Migrate Production Estimation from `useProductionEstimation.ts` mock data to live `POST /planning/production-estimates`, UUIDs and decimal-safe adapters. The deployed endpoint was verified successfully for OmniDesk target `120`: max buildable `59.489815`, 3 component lines, 2 shortages.
- Fix `/manufacturing/planning/runs/[id]` returning 404 in the local runtime despite the source route being present.
- Correct the legacy BOM description encoding (`BÃ...`) before screenshots.
- After Backend fixes availability, verify Stock and Inventory Lots render HOLD/REJECTED as unavailable without client-side fabrication.

## Reproducibility

- Stable IDs are stored in `fixture-ids.json`.
- The repeat-safe seeding workflow is stored in `seed-slide-fixtures.ps1`.
- No password, access token, refresh token or token ID is persisted in either artifact.

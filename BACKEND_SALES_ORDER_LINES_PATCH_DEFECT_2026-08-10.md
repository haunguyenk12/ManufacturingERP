# Backend handoff — Sales Order full-replacement PATCH fails with `lines[]`

> Date prepared: 2026-08-10  
> First reproduced live: 2026-08-08  
> Owner requested: Backend team  
> FE status: integrated; waiting only for backend correction and focused live recheck

## 1. Summary

`PATCH /api/v1/sales-orders/{salesOrderId}` succeeds for a DRAFT Sales Order when updating only
header fields, but returns `Data constraint violation` when the same OpenAPI-compliant request also
contains the documented full-replacement `lines[]` array.

Frontend sends no Sales Order line ID and no `lineNo`. The backend owns line identity/numbering and
must replace the line collection atomically.

## 2. Impact

- User can edit DRAFT header fields such as customer, order date and note.
- User cannot safely edit demand-line Item, quantity or due date.
- FE keeps the edit form and displays the backend error.
- FE intentionally does not implement delete-then-add calls because multiple non-atomic requests
  could leave a partially updated Sales Order.
- This is the only remaining known backend defect from the focused Capstone 2 MVP acceptance.

## 3. Endpoint and prerequisites

```http
PATCH /api/v1/sales-orders/{salesOrderId}
Authorization: Bearer <accessToken>
Content-Type: application/json
Idempotency-Key: <stable-key-for-this-submit>
```

Prerequisites:

1. Sales Order exists and is still `DRAFT`.
2. Caller has the Sales Order manage permission at the applicable scope.
3. `expectedVersion` is copied exactly from the latest backend detail response.
4. Every line references a valid active Finished Good Item.
5. `orderedQuantity > 0` and `dueDate >= orderDate`.

Observed disposable record: `FE2-SO-0808-01`.

That record later completed the accepted lifecycle and is now `CANCELLED`; backend should use a new
disposable DRAFT record when reproducing the write.

## 4. Control request — header-only PATCH succeeds

The following request shape succeeded live and incremented the optimistic version:

```json
{
  "expectedVersion": 0,
  "customerName": "FE2 Customer Updated",
  "orderDate": "2026-08-08",
  "note": "Header update acceptance"
}
```

Observed lifecycle evidence for `FE2-SO-0808-01`:

```text
create DRAFT version 0
header PATCH version 0 -> 1
confirm version 1 -> 2
cancel version 2 -> 3
```

This proves DRAFT state validation, authorization and `expectedVersion` handling work for the header
path.

## 5. Failing request — adding full-replacement `lines[]`

Use the latest version returned by detail and real UUIDs from the disposable DRAFT record:

```json
{
  "expectedVersion": 0,
  "customerName": "FE2 Customer Updated",
  "orderDate": "2026-08-08",
  "note": "Replace demand lines atomically",
  "lines": [
    {
      "itemId": "<finishedGoodItemUuid>",
      "orderedQuantity": "12.000000",
      "dueDate": "2026-08-12"
    }
  ]
}
```

Actual live result:

```text
Data constraint violation
```

Key isolation result:

- Same DRAFT and valid `expectedVersion`, without `lines`: succeeds.
- Adding contract-compliant `lines[]`: fails.
- FE does not send request `lineNo`, Sales Order line ID or any persisted child ID.
- Server-generated line numbers are read only from the response.

The original session checkpoint preserved the backend message but not the HTTP trace ID. Please
capture a new trace ID while reproducing with a fresh disposable DRAFT.

## 6. Exact FE wire behavior

Before the request, FE validates and serializes each line to:

```ts
{
  itemId: UUID,
  orderedQuantity: NUMERIC_19_6_STRING,
  dueDate: LocalDate
}
```

The PATCH body is:

```ts
{
  expectedVersion,
  customerName,
  orderDate,
  note,
  lines
}
```

Relevant frontend implementation and regression test:

- `src/features/manufacturing/sales-orders/api/salesOrderApi.ts`
- `src/features/manufacturing/sales-orders/api/salesOrderApi.test.ts`
- `src/features/manufacturing/sales-orders/api/types.ts`

The FE regression test explicitly locks the absence of line IDs and `lineNo` in the PATCH request.

## 7. Expected backend behavior

For a valid DRAFT request:

1. Lock/check the Sales Order using `expectedVersion`.
2. Validate the complete replacement line collection.
3. Replace existing children and assign server-owned line identity/`lineNo` values.
4. Persist header and lines in one transaction.
5. Increment Sales Order `version` exactly once.
6. Return the updated Sales Order detail, including persisted lines and their generated `lineNo`.

If any line is invalid, the entire transaction must roll back. No header-only or partial-line update
should remain.

## 8. Suggested backend investigation

The following are investigation hints, not confirmed root cause:

- Hibernate/JPA child reconciliation order may insert replacement lines before deleting existing
  children, colliding with a unique `(sales_order_id, line_no)` constraint.
- Replacement children may reach persistence without a backend-assigned `lineNo`.
- Orphan removal/cascade configuration may leave existing rows while replacement rows are inserted.
- DTO-to-entity mapping may accidentally treat omitted client line identity as a constraint error
  instead of rebuilding the owned collection.

Please inspect the nested database exception and SQL constraint name from a fresh trace before
choosing the fix.

## 9. Backend acceptance checklist

Use disposable data and verify all cases:

- [ ] Header-only PATCH still succeeds.
- [ ] Change quantity/due date of one existing line through full replacement.
- [ ] Replace one line with another valid Finished Good Item.
- [ ] Add a second line.
- [ ] Remove a line while keeping at least one line.
- [ ] Response contains backend-generated stable IDs/`lineNo` values.
- [ ] Version increments exactly once per successful PATCH.
- [ ] Same idempotency key + same payload replays the same result.
- [ ] Same idempotency key + different payload returns idempotency conflict.
- [ ] Stale `expectedVersion` returns the documented concurrency conflict.
- [ ] Invalid replacement line rolls back header and every child change atomically.
- [ ] OpenAPI request/response schemas remain aligned with the implemented contract.
- [ ] Error response includes stable code, useful message and `X-Trace-Id`.

## 10. FE recheck after backend deploy

After backend confirms deployment, FE will run only this focused acceptance:

1. Create a disposable DRAFT Sales Order.
2. Read detail/version.
3. PATCH header plus full replacement `lines[]`.
4. Re-read detail and confirm persisted Item, quantity, due date, generated `lineNo` and version.
5. Exercise add/remove reconciliation while the order remains DRAFT.
6. Verify a validation failure preserves both server state and the frontend form.
7. Run focused Sales Order tests, TypeScript, i18n parity, API coverage, lint, production build and
   `git diff --check`.

No FE contract change is requested unless backend intentionally changes and republishes OpenAPI.

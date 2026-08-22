# Manufacturing ERP ERD — DBML by Module

These DBML files reflect the live PostgreSQL `public` schema after Flyway V65 (`add entity name to audit logs`). The 62 business tables are assigned exactly once across six modules.

## Modules

| File | Scope | Owned tables |
|---|---|---:|
| `01-system-administration-integration.dbml` | Users, organization, RBAC, audit, and data import | 16 |
| `02-product-production-master-data.dbml` | UOM, items, BOM, routing, work centers, shifts, calendars, and standard cost | 13 |
| `03-sales-planning.dbml` | Sales orders, planning demand, MRP, requirements, and supply suggestions | 7 |
| `04-purchasing.dbml` | Suppliers, purchase requisitions, purchase orders, and goods receipts | 8 |
| `05-inventory-warehouse.dbml` | Lots, serial numbers, warehouse settings, balances, and movements | 5 |
| `06-manufacturing-execution-quality.dbml` | Work orders, material execution, WIP, production receipts, quality, and accumulated cost | 13 |

## Using the files on dbdiagram.io

1. Open [dbdiagram.io](https://dbdiagram.io/).
2. Create a new diagram.
3. Copy the content of one `.dbml` file into the DBML editor.
4. Repeat in a separate diagram for each module.

Every file is independently parseable. A table marked `External reference table` is a relationship-only stub containing the referenced key columns. Its complete definition belongs to another module.

`Ref` statements represent physical PostgreSQL foreign keys. Polymorphic identifiers such as `reference_id`, `resource_id`, or `converted_reference_id` are documented with notes and are not presented as physical foreign keys.

The files include current columns, PostgreSQL data types, nullability, defaults, primary keys, unique constraints, check constraints, delete actions, and foreign-key cardinality.

## Regeneration

The generator is located at `scripts/generate-dbml-erd.js`. It reads schema metadata from the running `erp-postgres` container and fails if any current business table is missing from, duplicated across, or unknown to the six-module map.

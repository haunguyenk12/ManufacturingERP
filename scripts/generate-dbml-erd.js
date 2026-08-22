const { execFileSync } = require('node:child_process');

const DB = 'manufacturing_erp';
const USER = 'postgres';
const CONTAINER = 'erp-postgres';

const modules = {
  '01-system-administration-integration': [
    'users', 'companies', 'plants', 'warehouses', 'permissions', 'roles',
    'role_permissions', 'access_scopes', 'access_scope_resources',
    'user_role_assignments', 'user_roles', 'audit_logs', 'audit_log_changes',
    'import_profiles', 'import_runs', 'import_rows'
  ],
  '02-product-production-master-data': [
    'uoms', 'items', 'bom_headers', 'bom_lines', 'routings',
    'routing_operations', 'work_centers', 'shifts', 'shift_breaks',
    'work_calendars', 'work_calendar_weekly_shifts',
    'work_calendar_exceptions', 'item_standard_costs'
  ],
  '03-sales-planning': [
    'sales_orders', 'sales_order_lines', 'planning_demands', 'mrp_runs',
    'mrp_run_demands', 'mrp_requirement_lines', 'supply_suggestions'
  ],
  '04-purchasing': [
    'suppliers', 'item_suppliers', 'purchase_requisitions',
    'purchase_requisition_lines', 'purchase_orders', 'purchase_order_lines',
    'goods_receipts', 'goods_receipt_lines'
  ],
  '05-inventory-warehouse': [
    'inventory_lots', 'serial_numbers', 'item_warehouse_settings',
    'stock_balances', 'stock_movements'
  ],
  '06-manufacturing-execution-quality': [
    'work_orders', 'work_order_component_lines', 'work_order_operations',
    'material_reservations', 'material_issues', 'material_issue_lines',
    'wip_transactions', 'production_executions', 'production_receipts',
    'production_receipt_lines', 'quality_dispositions',
    'work_order_demand_allocations', 'work_order_cost_accumulators'
  ]
};

const logicalNotes = {
  'access_scope_resources.resource_id': 'Polymorphic logical target: companies, plants, or warehouses; no physical FK by design.',
  'audit_logs.entity_id': 'Polymorphic audited entity identifier; no physical FK by design.',
  'audit_logs.user_id': 'Logical users.user_id; nullable and intentionally not constrained by a physical FK.',
  'import_rows.created_entity_id': 'Polymorphic identifier of the entity created by the import row.',
  'planning_demands.reference_id': 'Polymorphic source identifier selected by reference_type; SALES_ORDER uses a Sales Order line identifier.',
  'stock_movements.reference_id': 'Polymorphic business-document identifier selected by reference_type.',
  'supply_suggestions.converted_reference_id': 'Polymorphic identifier of the generated Work Order or Purchase Requisition.',
  'wip_transactions.reference_id': 'Polymorphic business-document identifier selected by reference_type.',
  'production_executions.operator_user_id': 'Logical users.user_id; no physical FK in the current schema.',
  'production_receipts.approved_by': 'Logical users.user_id; no physical FK in the current schema.',
  'production_receipts.rejected_by': 'Logical users.user_id; no physical FK in the current schema.',
  'quality_dispositions.decided_by': 'Logical users.user_id; no physical FK in the current schema.'
};

function psql(sql) {
  const out = execFileSync('docker', [
    'exec', CONTAINER, 'psql', '-X', '-A', '-t', '-U', USER, '-d', DB,
    '-c', sql
  ], { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
  return out.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
}

const columns = psql(`
SELECT json_build_object(
  'table', c.relname,
  'ordinal', a.attnum,
  'name', a.attname,
  'type', pg_catalog.format_type(a.atttypid, a.atttypmod),
  'notNull', a.attnotnull,
  'default', COALESCE(pg_get_expr(d.adbin, d.adrelid), '')
)::text
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
ORDER BY c.relname, a.attnum`);

const constraints = psql(`
SELECT json_build_object(
  'table', conrelid::regclass::text,
  'name', conname,
  'type', contype,
  'columns', ARRAY(
    SELECT att.attname
    FROM unnest(conkey) WITH ORDINALITY AS k(attnum, ord)
    JOIN pg_attribute att ON att.attrelid = conrelid AND att.attnum = k.attnum
    ORDER BY k.ord
  ),
  'refTable', CASE WHEN confrelid = 0 THEN '' ELSE confrelid::regclass::text END,
  'refColumns', CASE WHEN confrelid = 0 THEN ARRAY[]::text[] ELSE ARRAY(
    SELECT att.attname
    FROM unnest(confkey) WITH ORDINALITY AS k(attnum, ord)
    JOIN pg_attribute att ON att.attrelid = confrelid AND att.attnum = k.attnum
    ORDER BY k.ord
  ) END,
  'deleteAction', confdeltype,
  'definition', pg_get_constraintdef(oid, true)
)::text
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
  AND conrelid::regclass::text <> 'flyway_schema_history'
ORDER BY conrelid::regclass::text, contype, conname`);

const migration = psql(`
SELECT json_build_object('version', version, 'description', description)::text
FROM flyway_schema_history
WHERE success = true
ORDER BY installed_rank DESC
LIMIT 1`)[0];

const colsByTable = new Map();
for (const col of columns) {
  if (!colsByTable.has(col.table)) colsByTable.set(col.table, []);
  colsByTable.get(col.table).push(col);
}

const consByTable = new Map();
for (const con of constraints) {
  if (!consByTable.has(con.table)) consByTable.set(con.table, []);
  consByTable.get(con.table).push(con);
}

const allAssigned = new Set(Object.values(modules).flat());
const allActual = new Set(columns.map((c) => c.table));
const missingAssignments = [...allActual].filter((t) => !allAssigned.has(t));
const unknownAssignments = [...allAssigned].filter((t) => !allActual.has(t));
if (missingAssignments.length || unknownAssignments.length) {
  throw new Error(`Module coverage mismatch. Missing: ${missingAssignments.join(', ')}; unknown: ${unknownAssignments.join(', ')}`);
}

function esc(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\r?\n/g, ' ');
}

function dbmlType(type) {
  if (type.startsWith('character varying')) return type.replace('character varying', 'varchar');
  if (type === 'timestamp with time zone') return 'timestamptz';
  if (type === 'timestamp without time zone') return 'timestamp';
  if (type === 'time without time zone') return 'time';
  if (type === 'double precision') return '"double precision"';
  return type;
}

function dbmlDefault(value) {
  if (!value) return null;
  const castString = value.match(/^'((?:''|[^'])*)'::.+$/);
  if (castString) return `'${castString[1].replace(/''/g, "\\'")}'`;
  if (/^(true|false|-?\d+(?:\.\d+)?)$/i.test(value)) return value.toLowerCase();
  return `\`${value.replace(/`/g, '')}\``;
}

function tuple(table, names) {
  return names.length === 1 ? `${table}.${names[0]}` : `${table}.(${names.join(', ')})`;
}

function sameColumns(left, right) {
  return left.length === right.length && left.every((v, i) => v === right[i]);
}

function renderTable(table, external = false, requiredColumns = null) {
  const actualColumns = colsByTable.get(table) || [];
  const selected = requiredColumns
    ? actualColumns.filter((c) => requiredColumns.has(c.name))
    : actualColumns;
  const cons = consByTable.get(table) || [];
  const primary = cons.find((c) => c.type === 'p');
  const uniques = cons.filter((c) => c.type === 'u');
  const checks = external ? [] : cons.filter((c) => c.type === 'c');
  const lines = [];
  const tableNote = external
    ? " [note: 'External reference table. Only relationship columns are shown; see its owning module for the full definition.']"
    : '';
  lines.push(`Table ${table}${tableNote} {`);
  for (const col of selected) {
    const settings = [];
    if (primary && primary.columns.length === 1 && primary.columns[0] === col.name) settings.push('pk');
    if (col.notNull) settings.push('not null');
    if (uniques.some((u) => u.columns.length === 1 && u.columns[0] === col.name)) settings.push('unique');
    const def = dbmlDefault(col.default);
    if (def !== null) settings.push(`default: ${def}`);
    const note = logicalNotes[`${table}.${col.name}`];
    if (note) settings.push(`note: '${esc(note)}'`);
    lines.push(`  ${col.name} ${dbmlType(col.type)}${settings.length ? ` [${settings.join(', ')}]` : ''}`);
  }
  const compositeIndexes = [];
  if (primary && primary.columns.length > 1 && (!requiredColumns || primary.columns.every((c) => requiredColumns.has(c)))) {
    compositeIndexes.push(`    (${primary.columns.join(', ')}) [pk, name: '${esc(primary.name)}']`);
  }
  for (const unique of uniques) {
    if (unique.columns.length > 1 && (!requiredColumns || unique.columns.every((c) => requiredColumns.has(c)))) {
      compositeIndexes.push(`    (${unique.columns.join(', ')}) [unique, name: '${esc(unique.name)}']`);
    }
  }
  if (compositeIndexes.length) {
    lines.push('');
    lines.push('  indexes {');
    lines.push(...compositeIndexes);
    lines.push('  }');
  }
  if (checks.length) {
    lines.push('');
    lines.push('  checks {');
    for (const check of checks) {
      const expression = check.definition.replace(/^CHECK\s*\(/i, '').replace(/\)\s*(?:NOT VALID)?\s*$/i, '');
      lines.push(`    \`${expression.replace(/`/g, '')}\` [name: '${esc(check.name)}']`);
    }
    lines.push('  }');
  }
  lines.push('}');
  return lines.join('\n');
}

function renderModule(moduleName, ownedTables) {
  const owned = new Set(ownedTables);
  const outboundFks = constraints.filter((c) => c.type === 'f' && owned.has(c.table));
  const externalColumns = new Map();
  for (const fk of outboundFks) {
    if (owned.has(fk.refTable)) continue;
    if (!externalColumns.has(fk.refTable)) externalColumns.set(fk.refTable, new Set());
    for (const col of fk.refColumns) externalColumns.get(fk.refTable).add(col);
  }

  const lines = [];
  lines.push(`Project manufacturing_erp_${moduleName.replace(/-/g, '_')} {`);
  lines.push("  database_type: 'PostgreSQL'");
  lines.push(`  Note: 'Manufacturing ERP ${moduleName} module. Source: live PostgreSQL schema after Flyway V${esc(migration.version)} (${esc(migration.description)}). External tables are relationship-only stubs.'`);
  lines.push('}');
  lines.push('');
  for (const table of ownedTables) {
    lines.push(renderTable(table));
    lines.push('');
  }
  for (const [table, required] of [...externalColumns.entries()].sort(([a], [b]) => a.localeCompare(b))) {
    lines.push(renderTable(table, true, required));
    lines.push('');
  }
  for (const fk of outboundFks) {
    const childCons = consByTable.get(fk.table) || [];
    const isOneToOne = childCons.some((c) => (c.type === 'p' || c.type === 'u') && sameColumns(c.columns, fk.columns));
    const relation = isOneToOne ? '-' : '>';
    const settings = [];
    const deleteMap = { c: 'cascade', n: 'set null', d: 'set default', r: 'restrict' };
    if (deleteMap[fk.deleteAction]) settings.push(`delete: ${deleteMap[fk.deleteAction]}`);
    lines.push(`Ref ${fk.name}: ${tuple(fk.table, fk.columns)} ${relation} ${tuple(fk.refTable, fk.refColumns)}${settings.length ? ` [${settings.join(', ')}]` : ''}`);
  }
  lines.push('');
  lines.push(`TableGroup module_${moduleName.replace(/-/g, '_')} {`);
  for (const table of ownedTables) lines.push(`  ${table}`);
  lines.push('}');
  lines.push('');
  return lines.join('\n');
}

const files = {};
for (const [moduleName, tables] of Object.entries(modules)) {
  files[`docs/erd/dbml/${moduleName}.dbml`] = renderModule(moduleName, tables);
}

process.stdout.write(JSON.stringify({
  migration,
  tableCount: allActual.size,
  moduleCount: Object.keys(modules).length,
  files
}));

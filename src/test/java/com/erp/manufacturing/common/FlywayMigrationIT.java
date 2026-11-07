package com.erp.manufacturing.common;

import com.erp.manufacturing.module.planning.domain.MrpRun;
import com.erp.manufacturing.module.workorder.domain.MaterialIssue;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migration chain (V1..V60) against an empty Postgres
 * Testcontainer. Uses the Flyway API directly (no ApplicationContext) so this
 * doesn't need to boot Redis/JWT/filter-chain beans unrelated to migration
 * correctness (NEXT_PHASE_PLAN.md D4).
 */
class FlywayMigrationIT extends AbstractPostgresIntegrationTest {

    private static final String BACKFILL_SCHEMA = "v36_backfill_check";
    private static final String IDEMPOTENCY_SCHEMA = "v37_idempotency_check";
    private static final String DOCUMENT_CODE_SCHEMA = "v40_document_code_check";
    private static final String SERIAL_TRACKING_SCHEMA = "v52_serial_tracking_check";
    private static final String RUN_IDEMPOTENCY_SCHEMA = "v58_run_idempotency_check";
    private static final String AUDIT_PIPELINE_SCHEMA = "v67_audit_pipeline_check";
    private static final String VERSION_BACKFILL_SCHEMA = "v73_version_backfill_check";
    private static final String PO_LIFECYCLE_SCHEMA = "v76_po_lifecycle_check";
    private static final String GR_DRAFT_SCHEMA = "v77_gr_draft_check";

    @Test
    void migrate_onEmptyDatabase_appliesAllMigrationsCleanly() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        // V69 (customers + supplier contact columns) and V70 (customer permissions) are G2-1;
        // V71 (fixed-template import batches on import_runs) is G3; V72 (stock transfers) is G4-2.
        // V93 (MRP pending purchase-QC snapshot) is CF-4; V94 (captured capacity on operations) is CF-5;
        // V95 (case-insensitive lot code index) is CF-9; V96 (password_challenges) is the account
        // password flows (FE_REQUEST_ACCOUNT_PASSWORD_FLOWS_2026-11-06).
        assertThat(current.getVersion().getVersion()).isEqualTo("96");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(Arrays.stream(flyway.info().all()))
                .noneMatch(info -> info.getState() == MigrationState.FAILED);
    }

    /**
     * C2-5: the RBAC grant matrix, asserted against a really-migrated database.
     *
     * <p>{@code PermissionCatalogTest} already proves every {@code @PreAuthorize} code is <em>seeded as
     * a permission row</em> — and that is precisely the blind spot that let this defect live: 12 core
     * permissions existed and were granted to ADMIN alone, so a MANAGER account received 403 on the
     * work order list of its own plant while every test stayed green. Granting is a different fact from
     * existing, it lives only in {@code role_permissions}, and only a real database can answer it.
     *
     * <p>The assertion is deliberately shaped as "every permission must be usable by someone other
     * than the superuser, unless it is declared system-configuration". A per-permission checklist would
     * pass forever once written; this shape fails the moment a <em>new</em> migration seeds a permission
     * and forgets MANAGER/OPERATOR — the exact way V7..V15 drifted from the documented model.
     */
    @Test
    void migrate_v41_leavesNoCorePermissionGrantedToAdminAlone() throws Exception {
        // The complete admin-only set: the two "configure the system" permissions
        // docs/roles-and-permissions.md reserves for ADMIN ("MANAGER xem master data nhưng không
        // cấu hình hệ thống"), plus PERM_AUDIT_READ added at C2-1/V55 (audit trail exposes every
        // user's IP/user-agent/action history — same sensitivity class, confirmed with user).
        // Adding to this list is a security decision, not a formality.
        Set<String> adminOnlyByDesign = Set.of("PERM_ORG_MANAGE", "PERM_ACCESS_MANAGE", "PERM_AUDIT_READ");

        migratePublicSchema();

        Set<String> adminOnlyInDatabase = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM permissions p
                     WHERE NOT EXISTS (
                         SELECT 1 FROM role_permissions rp
                         JOIN roles r ON r.role_id = rp.role_id
                         WHERE rp.permission_id = p.permission_id
                           AND r.code IN ('MANAGER', 'OPERATOR')
                     )
                     ORDER BY p.code
                     """)) {
            while (rows.next()) {
                adminOnlyInDatabase.add(rows.getString("code"));
            }
        }

        assertThat(adminOnlyInDatabase)
                .as("These permissions are granted to ADMIN only. If that is intended, add them to "
                        + "adminOnlyByDesign here AND to docs/roles-and-permissions.md; otherwise grant "
                        + "them in a migration. Silently admin-only means the feature is unreachable "
                        + "for every real user.")
                .isEqualTo(new TreeSet<>(adminOnlyByDesign));
    }

    /**
     * C2-5: the positive half. The admin-only assertion above accepts "MANAGER <b>or</b> OPERATOR", so
     * it cannot notice a permission landing on the wrong one of the two — dropping MANAGER's
     * {@code PERM_WORK_ORDER_MANAGE} would leave it granted to nobody who may create a work order while
     * that test stayed green. A security policy is worth an explicit list; updating this list is the
     * point at which someone has to say out loud that the policy changed.
     */
    @Test
    void migrate_v41_grantsManagerTheCorePermissionsTheRolesDocPromises() throws Exception {
        migratePublicSchema();

        Set<String> expected = new TreeSet<>(Set.of(
                "PERM_WORK_ORDER_READ", "PERM_WORK_ORDER_MANAGE", "PERM_WORK_ORDER_EXECUTE",
                "PERM_BOM_READ", "PERM_BOM_MANAGE",
                "PERM_INVENTORY_READ", "PERM_INVENTORY_MOVE", "PERM_INVENTORY_MANAGE",
                "PERM_ORG_READ", "PERM_PLANNING_READ"));

        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = 'MANAGER' AND p.code IN (
                         'PERM_WORK_ORDER_READ', 'PERM_WORK_ORDER_MANAGE', 'PERM_WORK_ORDER_EXECUTE',
                         'PERM_BOM_READ', 'PERM_BOM_MANAGE',
                         'PERM_INVENTORY_READ', 'PERM_INVENTORY_MOVE', 'PERM_INVENTORY_MANAGE',
                         'PERM_ORG_READ', 'PERM_PLANNING_READ')
                     """)) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }

        assertThat(granted)
                .as("MANAGER is accountable for these in docs/roles-and-permissions.md; a missing one "
                        + "means the role cannot do its documented job")
                .isEqualTo(expected);
    }

    /**
     * C2-5: separation of duties, stated as prohibitions because that is the half a positive grant list
     * can never express. docs/roles-and-permissions.md "Separation of duties (P1 + F2)": whoever issues
     * or produces must not approve their own work.
     */
    @Test
    void migrate_v41_keepsOperatorOutOfApprovalAndConfigurationPermissions() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            for (String forbidden : new String[]{
                    "PERM_MATERIAL_ISSUE_OVERRIDE",      // cannot approve its own over-issue
                    "PERM_PRODUCTION_RECEIPT_APPROVE",   // cannot approve its own output
                    "PERM_QUALITY_DISPOSITION",          // cannot pass its own goods through QC
                    "PERM_WORK_ORDER_MANAGE",            // create/release stays with MANAGER
                    "PERM_ORG_MANAGE",
                    "PERM_ACCESS_MANAGE"}) {
                try (ResultSet rows = statement.executeQuery("""
                        SELECT count(*) AS granted
                        FROM role_permissions rp
                        JOIN roles r ON r.role_id = rp.role_id
                        JOIN permissions p ON p.permission_id = rp.permission_id
                        WHERE r.code = 'OPERATOR' AND p.code = '%s'
                        """.formatted(forbidden))) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt("granted"))
                            .as("OPERATOR must not hold %s — separation of duties", forbidden)
                            .isZero();
                }
            }
        }
    }

    /**
     * C2-3: same shape as the {@code migrate_v41_*} pair above, applied to the new UOM permissions —
     * {@code PERM_UOM_READ} for all three roles, {@code PERM_UOM_MANAGE} for ADMIN/MANAGER only
     * (docs/roles-and-permissions.md, V43). Written as a single grant-set assertion per role rather
     * than as separate positive/negative tests: with only two permissions the full set is the
     * simplest correct statement, and it still fails if either permission lands on the wrong role.
     */
    @Test
    void migrate_v43_grantsUomPermissionsToTheDocumentedRoles() throws Exception {
        migratePublicSchema();

        assertThat(grantedUomPermissions("ADMIN")).containsExactlyInAnyOrder("PERM_UOM_READ", "PERM_UOM_MANAGE");
        assertThat(grantedUomPermissions("MANAGER")).containsExactlyInAnyOrder("PERM_UOM_READ", "PERM_UOM_MANAGE");
        assertThat(grantedUomPermissions("OPERATOR")).containsExactly("PERM_UOM_READ");
    }

    /**
     * C2-6: same shape as {@code migrate_v43_*} — {@code PERM_WORK_CENTER_READ} for all three roles,
     * {@code PERM_WORK_CENTER_MANAGE} for ADMIN/MANAGER only (docs/roles-and-permissions.md, V45).
     */
    @Test
    void migrate_v45_grantsWorkCenterPermissionsToTheDocumentedRoles() throws Exception {
        migratePublicSchema();

        assertThat(grantedWorkCenterPermissions("ADMIN"))
                .containsExactlyInAnyOrder("PERM_WORK_CENTER_READ", "PERM_WORK_CENTER_MANAGE");
        assertThat(grantedWorkCenterPermissions("MANAGER"))
                .containsExactlyInAnyOrder("PERM_WORK_CENTER_READ", "PERM_WORK_CENTER_MANAGE");
        assertThat(grantedWorkCenterPermissions("OPERATOR")).containsExactly("PERM_WORK_CENTER_READ");
    }

    private Set<String> grantedWorkCenterPermissions(String roleCode) throws Exception {
        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s' AND p.code IN ('PERM_WORK_CENTER_READ', 'PERM_WORK_CENTER_MANAGE')
                     """.formatted(roleCode))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    /**
     * C2-7: same shape as {@code migrate_v45_*} — {@code PERM_SHIFT_READ}/{@code
     * PERM_WORK_CALENDAR_READ} for all three roles, the two {@code _MANAGE} permissions for
     * ADMIN/MANAGER only (docs/roles-and-permissions.md, V47).
     */
    @Test
    void migrate_v47_grantsShiftAndWorkCalendarPermissionsToTheDocumentedRoles() throws Exception {
        migratePublicSchema();

        assertThat(grantedShiftAndWorkCalendarPermissions("ADMIN")).containsExactlyInAnyOrder(
                "PERM_SHIFT_READ", "PERM_SHIFT_MANAGE", "PERM_WORK_CALENDAR_READ", "PERM_WORK_CALENDAR_MANAGE");
        assertThat(grantedShiftAndWorkCalendarPermissions("MANAGER")).containsExactlyInAnyOrder(
                "PERM_SHIFT_READ", "PERM_SHIFT_MANAGE", "PERM_WORK_CALENDAR_READ", "PERM_WORK_CALENDAR_MANAGE");
        assertThat(grantedShiftAndWorkCalendarPermissions("OPERATOR")).containsExactlyInAnyOrder(
                "PERM_SHIFT_READ", "PERM_WORK_CALENDAR_READ");
    }

    private Set<String> grantedShiftAndWorkCalendarPermissions(String roleCode) throws Exception {
        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s' AND p.code IN
                         ('PERM_SHIFT_READ', 'PERM_SHIFT_MANAGE', 'PERM_WORK_CALENDAR_READ', 'PERM_WORK_CALENDAR_MANAGE')
                     """.formatted(roleCode))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    /**
     * C2-8: same shape as {@code migrate_v45_*}/{@code migrate_v47_*} — {@code PERM_CAPACITY_READ}
     * for all three roles, {@code PERM_CAPACITY_MANAGE} for ADMIN/MANAGER only (gap doc §3.6:
     * "Manager điều chỉnh một operation" — OPERATOR does not get schedule-adjustments,
     * docs/roles-and-permissions.md, V49).
     */
    @Test
    void migrate_v49_grantsCapacityPermissionsToTheDocumentedRoles() throws Exception {
        migratePublicSchema();

        assertThat(grantedCapacityPermissions("ADMIN"))
                .containsExactlyInAnyOrder("PERM_CAPACITY_READ", "PERM_CAPACITY_MANAGE");
        assertThat(grantedCapacityPermissions("MANAGER"))
                .containsExactlyInAnyOrder("PERM_CAPACITY_READ", "PERM_CAPACITY_MANAGE");
        assertThat(grantedCapacityPermissions("OPERATOR")).containsExactly("PERM_CAPACITY_READ");
    }

    private Set<String> grantedCapacityPermissions(String roleCode) throws Exception {
        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s' AND p.code IN ('PERM_CAPACITY_READ', 'PERM_CAPACITY_MANAGE')
                     """.formatted(roleCode))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    /**
     * P3: {@code PERM_COSTING_READ}/{@code PERM_COSTING_MANAGE} — ADMIN+MANAGER only, NO grant to
     * OPERATOR at all (unlike {@code migrate_v49_*}/{@code migrate_v47_*}, which give OPERATOR
     * read-only). This is a pure "cấm" case: {@code PermissionCatalogTest} only proves the two
     * permissions exist, never that OPERATOR is excluded from them — only a real database answers
     * that (same lesson as {@code migrate_v41_*}).
     */
    @Test
    void migrate_v51_grantsCostingPermissionsToAdminAndManagerOnly() throws Exception {
        migratePublicSchema();

        assertThat(grantedCostingPermissions("ADMIN"))
                .containsExactlyInAnyOrder("PERM_COSTING_READ", "PERM_COSTING_MANAGE");
        assertThat(grantedCostingPermissions("MANAGER"))
                .containsExactlyInAnyOrder("PERM_COSTING_READ", "PERM_COSTING_MANAGE");
        assertThat(grantedCostingPermissions("OPERATOR")).isEmpty();
    }

    /**
     * G7c / {@code V78}: purchase QC permissions go to ADMIN and MANAGER only (FE Q24), the receipt line
     * cannot have more decided than received, a rejected decision needs a reason, and the ledger accepts
     * the lot-split movement types.
     */
    @Test
    void migrate_v78_grantsPurchaseQcToAdminAndManagerOnly_andGuardsTheQcQuantities() throws Exception {
        migratePublicSchema();

        for (String role : new String[] {"ADMIN", "MANAGER", "OPERATOR"}) {
            Set<String> granted = new TreeSet<>();
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("""
                         SELECT p.code FROM role_permissions rp
                         JOIN roles r ON r.role_id = rp.role_id
                         JOIN permissions p ON p.permission_id = rp.permission_id
                         WHERE r.code = '%s' AND p.code IN ('PERM_PURCHASE_QC_READ', 'PERM_PURCHASE_QC_MANAGE')
                         """.formatted(role))) {
                while (rows.next()) {
                    granted.add(rows.getString("code"));
                }
            }
            if (role.equals("OPERATOR")) {
                assertThat(granted).isEmpty();
            } else {
                assertThat(granted).containsExactlyInAnyOrder("PERM_PURCHASE_QC_READ", "PERM_PURCHASE_QC_MANAGE");
            }
        }

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT conname, pg_get_constraintdef(oid) AS def FROM pg_constraint
                     WHERE connamespace = 'public'::regnamespace
                       AND conname IN ('chk_gr_lines_qc_within_received', 'chk_purchase_qc_reject_reason',
                                       'chk_purchase_qc_quantities', 'chk_stock_movements_type')
                     """)) {
            java.util.Map<String, String> definitions = new java.util.HashMap<>();
            while (rows.next()) {
                definitions.put(rows.getString("conname"), rows.getString("def"));
            }
            assertThat(definitions.get("chk_gr_lines_qc_within_received"))
                    .contains("accepted_quantity + rejected_quantity").contains("<= received_quantity");
            assertThat(definitions.get("chk_purchase_qc_reject_reason")).contains("rejected_quantity = (0)");
            assertThat(definitions.get("chk_purchase_qc_quantities")).contains("> (0)");
            assertThat(definitions.get("chk_stock_movements_type"))
                    .contains("LOT_SPLIT_OUT").contains("LOT_SPLIT_IN").contains("TRANSFER_IN").contains("REVERSAL");
        }
    }

    /**
     * G8a / {@code V79}: a component line may name its warehouse (nullable, so every existing line keeps
     * reserving across the plant), a work order starts with schedule revision 0 and refuses a negative one,
     * and an Idempotency-Key is unique per work order command.
     */
    @Test
    void migrate_v79_addsComponentWarehouseScheduleContextAndWorkOrderIdempotency() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT table_name, column_name, is_nullable, column_default FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND ((table_name = 'work_order_component_lines' AND column_name = 'warehouse_id')
                        OR (table_name = 'work_orders' AND column_name IN ('schedule_revision',
                            'schedule_calendar_revision', 'schedule_time_zone', 'schedule_captured_at')))
                    """)) {
                java.util.Map<String, String> nullable = new java.util.HashMap<>();
                java.util.Map<String, String> defaults = new java.util.HashMap<>();
                while (rows.next()) {
                    nullable.put(rows.getString("column_name"), rows.getString("is_nullable"));
                    defaults.put(rows.getString("column_name"), rows.getString("column_default"));
                }
                assertThat(nullable).containsEntry("warehouse_id", "YES")
                        .containsEntry("schedule_revision", "NO")
                        .containsEntry("schedule_calendar_revision", "YES")
                        .containsEntry("schedule_time_zone", "YES")
                        .containsEntry("schedule_captured_at", "YES");
                assertThat(defaults.get("schedule_revision")).isEqualTo("0");
            }
            try (ResultSet rows = statement.executeQuery("""
                    SELECT conname, pg_get_constraintdef(oid) AS def FROM pg_constraint
                    WHERE connamespace = 'public'::regnamespace
                      AND conname IN ('chk_work_orders_schedule_revision', 'uk_work_order_idempotency_key_command')
                    """)) {
                java.util.Map<String, String> definitions = new java.util.HashMap<>();
                while (rows.next()) {
                    definitions.put(rows.getString("conname"), rows.getString("def"));
                }
                assertThat(definitions.get("chk_work_orders_schedule_revision")).contains("schedule_revision >= 0");
                assertThat(definitions.get("uk_work_order_idempotency_key_command"))
                        .contains("UNIQUE (idempotency_key, command)");
            }
        }
    }

    /**
     * G8b / {@code V80}: every existing issue is a normal (not supplemental) request, and a requester's
     * withdrawal reason has its own nullable column.
     */
    @Test
    void migrate_v80_addsSupplementalFlagAndCancellationReasonToMaterialIssues() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT column_name, is_nullable, column_default FROM information_schema.columns
                     WHERE table_schema = 'public' AND table_name = 'material_issues'
                       AND column_name IN ('supplemental', 'cancellation_reason')
                     """)) {
            java.util.Map<String, String> nullable = new java.util.HashMap<>();
            java.util.Map<String, String> defaults = new java.util.HashMap<>();
            while (rows.next()) {
                nullable.put(rows.getString("column_name"), rows.getString("is_nullable"));
                defaults.put(rows.getString("column_name"), rows.getString("column_default"));
            }
            assertThat(nullable).containsEntry("supplemental", "NO").containsEntry("cancellation_reason", "YES");
            assertThat(defaults.get("supplemental")).isEqualTo("false");
        }
    }

    /**
     * G9c / {@code V85}: receipts judged through the old whole-receipt endpoint get their line totals and one
     * decision row from the recorded verdict; an approved receipt not judged yet gets neither. Seeded at V84 so the
     * backfill runs on real rows.
     */
    @Test
    void migrate_v85_backfillsTheRecordedVerdictsIntoTheDecisionLedger() throws Exception {
        String schema = "flyway_v85_quality";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .target("84")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111118501";
        String plant = "22222222-2222-4222-8222-222222228501";
        String warehouse = "33333333-3333-4333-8333-333333338501";
        String item = "55555555-5555-4555-8555-555555558501";
        String bom = "66666666-6666-4666-8666-666666668501";
        String workOrder = "77777777-7777-4777-8777-777777778501";
        String passed = "88888888-8888-4888-8888-888888888501";
        String failed = "88888888-8888-4888-8888-888888888502";
        String waiting = "88888888-8888-4888-8888-888888888503";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("insert into companies (company_id, code, name) values ('%s', 'V85', 'V85')".formatted(company));
            statement.execute("insert into plants (plant_id, company_id, code, name, timezone) values"
                    + " ('%s', '%s', 'P', 'P', 'Asia/Ho_Chi_Minh')".formatted(plant, company));
            statement.execute("insert into warehouses (warehouse_id, plant_id, code, name) values ('%s', '%s', 'W', 'W')"
                    .formatted(warehouse, plant));
            statement.execute("insert into items (item_id, company_id, code, name, type, unit) values"
                    + " ('%s', '%s', 'FG', 'FG', 'FINISHED_GOOD', 'EA')".formatted(item, company));
            statement.execute("insert into bom_headers (bom_id, company_id, parent_item_id, revision, bom_code, version_number)"
                    + " values ('%s', '%s', '%s', 'A', 'FG-A', 1)".formatted(bom, company, item));
            statement.execute(("insert into work_orders (work_order_id, company_id, plant_id, work_order_no, product_item_id,"
                    + " bom_id, bom_revision, output_warehouse_id, planned_quantity, status) values ('%s', '%s', '%s',"
                    + " 'WO-V85', '%s', '%s', 'A', '%s', 10, 'IN_PROGRESS')")
                    .formatted(workOrder, company, plant, item, bom, warehouse));
            String[][] receipts = {{passed, "AVAILABLE", "'Passed'"}, {failed, "REJECTED", "'Scratched'"}, {waiting, null, "null"}};
            int index = 0;
            for (String[] receipt : receipts) {
                index++;
                statement.execute(("insert into production_receipts (receipt_id, work_order_id, status, idempotency_key,"
                        + " posted_at, approved_at, received_on, qc_result, qc_reason, qc_at, created_at, updated_at)"
                        + " values ('%s', '%s', 'APPROVED', 'V85-%d', now(), now(), '2026-10-20', %s, %s, %s, now(), now())")
                        .formatted(receipt[0], workOrder, index, receipt[1] == null ? "null" : "'" + receipt[1] + "'",
                                receipt[2], receipt[1] == null ? "null" : "'2026-10-21T03:00:00Z'"));
                statement.execute(("insert into production_receipt_lines (receipt_line_id, receipt_id, item_id, warehouse_id,"
                        + " quantity, created_at, updated_at) values (gen_random_uuid(), '%s', '%s', '%s', 4, now(), now())")
                        .formatted(receipt[0], item, warehouse));
            }
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            java.util.Map<String, String> totals = new java.util.HashMap<>();
            try (ResultSet rows = statement.executeQuery(
                    "select receipt_id, qc_accepted_quantity, qc_rejected_quantity from production_receipt_lines")) {
                while (rows.next()) {
                    totals.put(rows.getString("receipt_id"),
                            rows.getBigDecimal("qc_accepted_quantity").stripTrailingZeros().toPlainString() + "/"
                                    + rows.getBigDecimal("qc_rejected_quantity").stripTrailingZeros().toPlainString());
                }
            }
            assertThat(totals).containsEntry(passed, "4/0").containsEntry(failed, "0/4").containsEntry(waiting, "0/0");
            java.util.Map<String, String> decisions = new java.util.HashMap<>();
            try (ResultSet rows = statement.executeQuery(
                    "select receipt_id, accepted_quantity, rejected_quantity, reason, decided_at from production_quality_decisions")) {
                while (rows.next()) {
                    decisions.put(rows.getString("receipt_id"),
                            rows.getBigDecimal("accepted_quantity").stripTrailingZeros().toPlainString() + "/"
                                    + rows.getBigDecimal("rejected_quantity").stripTrailingZeros().toPlainString() + "/"
                                    + rows.getString("reason"));
                }
            }
            assertThat(decisions).hasSize(2).containsEntry(passed, "4/0/Passed").containsEntry(failed, "0/4/Scratched");
        }
    }

    /**
     * G10a / {@code V86} (D3): an order already past DRAFT when V86 runs has no delivery baseline — nothing recorded what
     * left the warehouse for it — while a DRAFT has shipped nothing and keeps a known baseline. A price is 0..999 999 999,
     * {@code NULL} allowed; the ten commercial permissions go to ADMIN and MANAGER only.
     */
    @Test
    void migrate_v86_marksOrdersPastDraftAsBaselineUnknownAndBoundsThePrice() throws Exception {
        String schema = "flyway_v86_sales";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .target("85")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111118601";
        String plant = "22222222-2222-4222-8222-222222228601";
        String item = "55555555-5555-4555-8555-555555558601";
        String draft = "99999999-9999-4999-8999-999999998601";
        String confirmed = "99999999-9999-4999-8999-999999998602";
        String cancelled = "99999999-9999-4999-8999-999999998603";
        String line = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa8601";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("insert into companies (company_id, code, name) values ('%s', 'V86', 'V86')".formatted(company));
            statement.execute("insert into plants (plant_id, company_id, code, name, timezone) values"
                    + " ('%s', '%s', 'P', 'P', 'Asia/Ho_Chi_Minh')".formatted(plant, company));
            statement.execute("insert into items (item_id, company_id, code, name, type, unit) values"
                    + " ('%s', '%s', 'FG', 'FG', 'FINISHED_GOOD', 'EA')".formatted(item, company));
            String[][] orders = {{draft, "DRAFT"}, {confirmed, "CONFIRMED"}, {cancelled, "CANCELLED"}};
            for (String[] order : orders) {
                statement.execute(("insert into sales_orders (sales_order_id, company_id, plant_id, order_no, customer_name,"
                        + " order_date, status) values ('%s', '%s', '%s', 'SO-%s', 'A', '2026-10-01', '%s')")
                        .formatted(order[0], company, plant, order[1], order[1]));
            }
            statement.execute(("insert into sales_order_lines (sales_order_line_id, sales_order_id, item_id, line_no,"
                    + " ordered_quantity, due_date) values ('%s', '%s', '%s', 1, 10, '2026-10-09')")
                    .formatted(line, confirmed, item));
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            java.util.Map<String, Boolean> baseline = new java.util.HashMap<>();
            try (ResultSet rows = statement.executeQuery(
                    "select sales_order_id, delivery_baseline_known, pricing_revision from sales_orders")) {
                while (rows.next()) {
                    baseline.put(rows.getString("sales_order_id"), rows.getBoolean("delivery_baseline_known"));
                    assertThat(rows.getInt("pricing_revision")).isZero();
                }
            }
            assertThat(baseline).containsEntry(draft, true).containsEntry(confirmed, false).containsEntry(cancelled, false);
            try (ResultSet rows = statement.executeQuery("select unit_price from sales_order_lines")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal("unit_price")).isNull();
            }
            statement.execute("update sales_order_lines set unit_price = 0");
            statement.execute("update sales_order_lines set unit_price = 999999999");
            assertThatThrownBy(() -> statement.execute("update sales_order_lines set unit_price = -1"))
                    .hasMessageContaining("chk_sales_order_lines_unit_price");
            assertThatThrownBy(() -> statement.execute("update sales_order_lines set unit_price = 1000000000"))
                    .hasMessageContaining("chk_sales_order_lines_unit_price");
            java.util.Map<String, java.util.Set<String>> grants = new java.util.HashMap<>();
            try (ResultSet rows = statement.executeQuery("select p.code, r.code as role from permissions p"
                    + " join role_permissions rp on rp.permission_id = p.permission_id"
                    + " join roles r on r.role_id = rp.role_id"
                    + " where p.code like 'PERM_DELIVERY_%' or p.code like 'PERM_SALES_INVOICE_%'"
                    + " or p.code like 'PERM_CUSTOMER_PAYMENT_%' or p.code = 'PERM_SALES_PRICE_MANAGE'")) {
                while (rows.next()) {
                    grants.computeIfAbsent(rows.getString("code"), k -> new java.util.HashSet<>()).add(rows.getString("role"));
                }
            }
            assertThat(grants).hasSize(10);
            assertThat(grants.values()).allSatisfy(roles -> assertThat(roles).containsExactlyInAnyOrder("ADMIN", "MANAGER"));
        }
    }

    /**
     * G9b / {@code V84}: a contribution claims a positive quantity, and a receipt claims each execution at most once.
     */
    @Test
    void migrate_v84_contributionsArePositiveAndUniquePerReceiptAndExecution() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT conname, pg_get_constraintdef(oid) AS def FROM pg_constraint
                     WHERE connamespace = 'public'::regnamespace
                       AND conname IN ('chk_production_receipt_contributions_quantity',
                                       'uk_production_receipt_contributions_receipt_execution')
                     """)) {
            java.util.Map<String, String> definitions = new java.util.HashMap<>();
            while (rows.next()) {
                definitions.put(rows.getString("conname"), rows.getString("def"));
            }
            assertThat(definitions.get("chk_production_receipt_contributions_quantity")).contains("> (0)");
            assertThat(definitions.get("uk_production_receipt_contributions_receipt_execution"))
                    .contains("UNIQUE (receipt_id, production_execution_id)");
        }
    }

    /**
     * G9a / {@code V83}: {@code received_on} is backfilled from when the stock was received — approval, else the
     * draft's posted_at — in the plant's time zone, then NOT NULL; the two read permissions go to ADMIN and MANAGER
     * only. Seeded at V82 so the backfill runs on real rows, and on an instant whose UTC and plant dates differ.
     */
    @Test
    void migrate_v83_backfillsReceivedOnInThePlantZone_andGrantsReadPermissions() throws Exception {
        String schema = "flyway_v83_receipts";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .target("82")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111118301";
        String plant = "22222222-2222-4222-8222-222222228301";
        String warehouse = "33333333-3333-4333-8333-333333338301";
        String item = "55555555-5555-4555-8555-555555558301";
        String bom = "66666666-6666-4666-8666-666666668301";
        String workOrder = "77777777-7777-4777-8777-777777778301";
        String approved = "88888888-8888-4888-8888-888888888301";
        String draft = "88888888-8888-4888-8888-888888888302";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("insert into companies (company_id, code, name) values ('%s', 'V83', 'V83')".formatted(company));
            statement.execute("insert into plants (plant_id, company_id, code, name, timezone) values"
                    + " ('%s', '%s', 'P', 'P', 'Asia/Ho_Chi_Minh')".formatted(plant, company));
            statement.execute("insert into warehouses (warehouse_id, plant_id, code, name) values ('%s', '%s', 'W', 'W')"
                    .formatted(warehouse, plant));
            statement.execute("insert into items (item_id, company_id, code, name, type, unit) values"
                    + " ('%s', '%s', 'FG', 'FG', 'FINISHED_GOOD', 'EA')".formatted(item, company));
            statement.execute("insert into bom_headers (bom_id, company_id, parent_item_id, revision, bom_code, version_number)"
                    + " values ('%s', '%s', '%s', 'A', 'FG-A', 1)".formatted(bom, company, item));
            statement.execute(("insert into work_orders (work_order_id, company_id, plant_id, work_order_no, product_item_id,"
                    + " bom_id, bom_revision, output_warehouse_id, planned_quantity, status) values ('%s', '%s', '%s',"
                    + " 'WO-V83', '%s', '%s', 'A', '%s', 10, 'IN_PROGRESS')")
                    .formatted(workOrder, company, plant, item, bom, warehouse));
            // 18:30 UTC is already the next day in Asia/Ho_Chi_Minh (UTC+7).
            statement.execute(("insert into production_receipts (receipt_id, work_order_id, status, idempotency_key,"
                    + " posted_at, approved_at) values ('%s', '%s', 'APPROVED', 'V83-A', '2026-10-18T03:00:00Z',"
                    + " '2026-10-19T18:30:00Z')").formatted(approved, workOrder));
            statement.execute(("insert into production_receipts (receipt_id, work_order_id, status, idempotency_key,"
                    + " posted_at) values ('%s', '%s', 'DRAFT', 'V83-D', '2026-10-18T03:00:00Z')")
                    .formatted(draft, workOrder));
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            java.util.Map<String, String> receivedOn = new java.util.HashMap<>();
            try (ResultSet rows = statement.executeQuery("select receipt_id, received_on from production_receipts")) {
                while (rows.next()) {
                    receivedOn.put(rows.getString("receipt_id"), rows.getString("received_on"));
                }
            }
            assertThat(receivedOn).containsEntry(approved, "2026-10-20").containsEntry(draft, "2026-10-18");
            try (ResultSet rows = statement.executeQuery("""
                    select is_nullable from information_schema.columns
                    where table_schema = '%s' and table_name = 'production_receipts' and column_name = 'received_on'
                    """.formatted(schema))) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("is_nullable")).isEqualTo("NO");
            }
            for (String role : new String[] {"ADMIN", "MANAGER", "OPERATOR"}) {
                Set<String> granted = new TreeSet<>();
                try (ResultSet rows = statement.executeQuery("""
                        select p.code from role_permissions rp
                        join roles r on r.role_id = rp.role_id
                        join permissions p on p.permission_id = rp.permission_id
                        where r.code = '%s' and p.code in ('PERM_PRODUCTION_RECEIPT_READ', 'PERM_PRODUCTION_QC_READ')
                        """.formatted(role))) {
                    while (rows.next()) {
                        granted.add(rows.getString("code"));
                    }
                }
                if (role.equals("OPERATOR")) {
                    assertThat(granted).isEmpty();
                } else {
                    assertThat(granted).containsExactly("PERM_PRODUCTION_QC_READ", "PERM_PRODUCTION_RECEIPT_READ");
                }
            }
        }
    }

    /**
     * G8d / {@code V82}: the three new permissions go to ADMIN and MANAGER only (FE Q34, never OPERATOR implicitly); the WIP
     * ledger accepts MATERIAL_RETURNED; a return line cannot carry a zero quantity and a return is POSTED only.
     */
    @Test
    void migrate_v82_grantsWipAndReturnPermissionsToAdminAndManagerOnly_andGuardsReturns() throws Exception {
        migratePublicSchema();

        for (String role : new String[] {"ADMIN", "MANAGER", "OPERATOR"}) {
            Set<String> granted = new TreeSet<>();
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("""
                         SELECT p.code FROM role_permissions rp
                         JOIN roles r ON r.role_id = rp.role_id
                         JOIN permissions p ON p.permission_id = rp.permission_id
                         WHERE r.code = '%s'
                           AND p.code IN ('PERM_WIP_READ', 'PERM_MATERIAL_RETURN_READ', 'PERM_MATERIAL_RETURN_MANAGE')
                         """.formatted(role))) {
                while (rows.next()) {
                    granted.add(rows.getString("code"));
                }
            }
            if (role.equals("OPERATOR")) {
                assertThat(granted).isEmpty();
            } else {
                assertThat(granted).containsExactlyInAnyOrder(
                        "PERM_WIP_READ", "PERM_MATERIAL_RETURN_READ", "PERM_MATERIAL_RETURN_MANAGE");
            }
        }

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT conname, pg_get_constraintdef(oid) AS def FROM pg_constraint
                     WHERE connamespace = 'public'::regnamespace
                       AND conname IN ('chk_wip_transactions_type', 'chk_material_return_lines_quantity',
                                       'chk_material_returns_status')
                     """)) {
            java.util.Map<String, String> definitions = new java.util.HashMap<>();
            while (rows.next()) {
                definitions.put(rows.getString("conname"), rows.getString("def"));
            }
            assertThat(definitions.get("chk_wip_transactions_type")).contains("MATERIAL_RETURNED").contains("MATERIAL_ISSUED");
            assertThat(definitions.get("chk_material_return_lines_quantity")).contains("> (0)");
            assertThat(definitions.get("chk_material_returns_status")).contains("POSTED");
        }
    }

    /**
     * G8c / {@code V81}: a work order that already had production reported keeps {@code operation_tracking = false}
     * — its operation inputs and consumption were never recorded, so they stay unknown (FE Q22) — while one with no
     * execution yet is tracked from now on. Seeded at V80 so the backfill runs on real rows.
     */
    @Test
    void migrate_v81_marksWorkOrdersWithEarlierExecutionsAsUntracked() throws Exception {
        String schema = "flyway_v81_tracking";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .target("80")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111118101";
        String plant = "22222222-2222-4222-8222-222222228101";
        String warehouse = "33333333-3333-4333-8333-333333338101";
        String item = "55555555-5555-4555-8555-555555558101";
        String bom = "66666666-6666-4666-8666-666666668101";
        String reported = "77777777-7777-4777-8777-777777778101";
        String fresh = "77777777-7777-4777-8777-777777778102";
        String workOrder = "insert into work_orders (work_order_id, company_id, plant_id, work_order_no, product_item_id,"
                + " bom_id, bom_revision, output_warehouse_id, planned_quantity, status) values ('%s', '" + company
                + "', '" + plant + "', '%s', '" + item + "', '" + bom + "', 'A', '" + warehouse + "', 10, 'IN_PROGRESS')";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("insert into companies (company_id, code, name) values ('%s', 'V81', 'V81')".formatted(company));
            statement.execute("insert into plants (plant_id, company_id, code, name) values ('%s', '%s', 'P', 'P')"
                    .formatted(plant, company));
            statement.execute("insert into warehouses (warehouse_id, plant_id, code, name) values ('%s', '%s', 'W', 'W')"
                    .formatted(warehouse, plant));
            statement.execute("insert into items (item_id, company_id, code, name, type, unit) values"
                    + " ('%s', '%s', 'FG', 'FG', 'FINISHED_GOOD', 'EA')".formatted(item, company));
            statement.execute("insert into bom_headers (bom_id, company_id, parent_item_id, revision, bom_code, version_number)"
                    + " values ('%s', '%s', '%s', 'A', 'FG-A', 1)".formatted(bom, company, item));
            statement.execute(workOrder.formatted(reported, "WO-REPORTED"));
            statement.execute(workOrder.formatted(fresh, "WO-FRESH"));
            statement.execute("insert into production_executions (work_order_id, good_quantity, idempotency_key, code)"
                    + " values ('%s', 4, 'V81-EXEC', 'PE-V81')".formatted(reported));
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .target("81")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            java.util.Map<String, Boolean> tracking = new java.util.HashMap<>();
            try (ResultSet rs = statement.executeQuery(
                    "select work_order_no, operation_tracking, terminal_shortfall_quantity from work_orders")) {
                while (rs.next()) {
                    tracking.put(rs.getString(1), rs.getBoolean(2));
                    // Not backfilled: no shortfall was ever captured for a work order before V81.
                    assertThat(rs.getBigDecimal(3)).isNull();
                }
            }
            assertThat(tracking).containsEntry("WO-REPORTED", false).containsEntry("WO-FRESH", true);
            assertThatThrownBy(() -> statement.execute(
                    "update work_orders set terminal_shortfall_quantity = -1 where work_order_no = 'WO-FRESH'"))
                    .hasMessageContaining("chk_work_orders_terminal_shortfall");
        }
    }

    /**
     * G11 / {@code V90}: the three production cost permissions go to ADMIN and MANAGER only (D6) — OPERATOR may read the
     * checklist through PERM_WORK_ORDER_READ but never calculate, set rates or close. Only a real database answers which
     * role holds what (the {@code migrate_v41_*} lesson).
     */
    @Test
    void migrate_v90_grantsProductionCostPermissionsToAdminAndManagerOnly() throws Exception {
        migratePublicSchema();
        List<String> codes = List.of("PERM_PRODUCTION_COST_CALCULATE", "PERM_COST_RATE_MANAGE", "PERM_WORK_ORDER_CLOSE");

        assertThat(granted("ADMIN", codes)).containsExactlyInAnyOrderElementsOf(codes);
        assertThat(granted("MANAGER", codes)).containsExactlyInAnyOrderElementsOf(codes);
        assertThat(granted("OPERATOR", codes)).isEmpty();
    }

    /**
     * G11c / {@code V92}: a cost entry never moves quantity, and a delivery entry never changes the stock value — the
     * two rules of a value-only entry (FE §3.6) are CHECKs, not only service code.
     */
    @Test
    void migrate_v92_cost_adjustments_areValueOnly() throws Exception {
        migratePublicSchema();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT conname, pg_get_constraintdef(oid) AS definition FROM pg_constraint
                     WHERE conrelid = 'public.production_cost_adjustments'::regclass AND contype = 'c'
                     """)) {
            java.util.Map<String, String> checks = new java.util.HashMap<>();
            while (rows.next()) {
                checks.put(rows.getString("conname"), rows.getString("definition"));
            }
            assertThat(checks.get("chk_production_cost_adjustments_quantity")).contains("quantity = (0)");
            assertThat(checks.get("chk_production_cost_adjustments_kind"))
                    .contains("stock_value_delta = allocated_amount").contains("stock_value_delta = (0)");
        }
    }

    /**
     * CF-4 / {@code V93}: a requirement line of a run made before V93 keeps {@code NULL} pending-QC columns — "not
     * captured", never backfilled to zero. The two columns are both NULL or both {@code >= 0}; a source row holds a
     * strictly positive pending quantity and one row per (requirement line, receipt line).
     */
    @Test
    void migrate_v93_pendingQcColumnsStayNullOnOldLines_checkPairsThem_sourceTableHasItsChecks() throws Exception {
        String schema = "flyway_v93_pending_qc";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .target("92")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111119301";
        String plant = "22222222-2222-4222-8222-222222229301";
        String item = "55555555-5555-4555-8555-555555559301";
        String run = "77777777-7777-4777-8777-777777779301";
        String line = "88888888-8888-4888-8888-888888889301";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("insert into companies (company_id, code, name) values ('%s', 'V93', 'V93')".formatted(company));
            statement.execute("insert into plants (plant_id, company_id, code, name, timezone) values"
                    + " ('%s', '%s', 'P', 'P', 'Asia/Ho_Chi_Minh')".formatted(plant, company));
            statement.execute("insert into items (item_id, company_id, code, name, type, unit) values"
                    + " ('%s', '%s', 'RM', 'RM', 'RAW_MATERIAL', 'KG')".formatted(item, company));
            statement.execute(("insert into mrp_runs (mrp_run_id, code, company_id, plant_id, horizon_start_date,"
                    + " horizon_end_date, status) values ('%s', 'RUN-V93', '%s', '%s', DATE '2026-10-01',"
                    + " DATE '2026-10-31', 'COMPLETED')").formatted(run, company, plant));
            statement.execute(("insert into mrp_requirement_lines (mrp_requirement_line_id, mrp_run_id, item_id,"
                    + " requirement_level, gross_required_quantity, net_required_quantity, due_date, requirement_status)"
                    + " values ('%s', '%s', '%s', 0, 40, 25, DATE '2026-10-20', 'SHORTAGE')").formatted(line, run, item));
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            try (ResultSet rows = statement.executeQuery(
                    "select pending_qc_quantity, pending_qc_source_count from mrp_requirement_lines")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal("pending_qc_quantity")).isNull();
                assertThat(rows.getObject("pending_qc_source_count")).isNull();
            }
            assertThatThrownBy(() -> statement.execute(
                    "update mrp_requirement_lines set pending_qc_quantity = -1, pending_qc_source_count = 1"))
                    .hasMessageContaining("chk_mrp_req_pending_qc");
            assertThatThrownBy(() -> statement.execute(
                    "update mrp_requirement_lines set pending_qc_quantity = 1, pending_qc_source_count = -1"))
                    .hasMessageContaining("chk_mrp_req_pending_qc");
            statement.execute("update mrp_requirement_lines set pending_qc_quantity = 0, pending_qc_source_count = 0");
            statement.execute("update mrp_requirement_lines set pending_qc_quantity = 25, pending_qc_source_count = 1");

            java.util.Map<String, String> constraints = new java.util.HashMap<>();
            try (ResultSet rows = statement.executeQuery(("""
                    SELECT c.conname, pg_get_constraintdef(c.oid) AS definition FROM pg_constraint c
                    JOIN pg_class t ON t.oid = c.conrelid
                    WHERE t.relname = 'mrp_pending_qc_sources' AND c.connamespace = '%s'::regnamespace
                    """).formatted(schema))) {
                while (rows.next()) {
                    constraints.put(rows.getString("conname"), rows.getString("definition"));
                }
            }
            assertThat(constraints.get("chk_mrp_pending_qc_sources_quantity")).contains("pending_quantity > (0)");
            assertThat(constraints.get("uk_mrp_pending_qc_sources_line_receipt_line"))
                    .contains("UNIQUE (requirement_line_id, goods_receipt_line_id)");
        }
    }

    /**
     * CF-4 / {@code V93}: "both NULL or both {@code >= 0}" must reject a line with only one of the two pending-QC
     * columns set — a quantity without its source count (or the reverse) is neither "not captured" nor a capture.
     * SQL three-valued logic makes {@code 5 >= 0 AND NULL >= 0} NULL, and a CHECK lets NULL through, so the pairing has
     * to be stated explicitly.
     */
    @Test
    void migrate_v93_checkRejectsALineWithOnlyOneOfThePendingQcColumns() throws Exception {
        migratePublicSchema();
        String company = "11111111-1111-4111-8111-111111119311";
        String plant = "22222222-2222-4222-8222-222222229311";
        String item = "55555555-5555-4555-8555-555555559311";
        String run = "77777777-7777-4777-8777-777777779311";
        String line = "88888888-8888-4888-8888-888888889311";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("insert into companies (company_id, code, name) values ('%s', 'V93B', 'V93B')".formatted(company));
            statement.execute("insert into plants (plant_id, company_id, code, name, timezone) values"
                    + " ('%s', '%s', 'P', 'P', 'Asia/Ho_Chi_Minh')".formatted(plant, company));
            statement.execute("insert into items (item_id, company_id, code, name, type, unit) values"
                    + " ('%s', '%s', 'RM', 'RM', 'RAW_MATERIAL', 'KG')".formatted(item, company));
            statement.execute(("insert into mrp_runs (mrp_run_id, code, company_id, plant_id, horizon_start_date,"
                    + " horizon_end_date, status) values ('%s', 'RUN-V93B', '%s', '%s', DATE '2026-10-01',"
                    + " DATE '2026-10-31', 'COMPLETED')").formatted(run, company, plant));
            statement.execute(("insert into mrp_requirement_lines (mrp_requirement_line_id, mrp_run_id, item_id,"
                    + " requirement_level, gross_required_quantity, net_required_quantity, due_date, requirement_status)"
                    + " values ('%s', '%s', '%s', 0, 40, 25, DATE '2026-10-20', 'SHORTAGE')").formatted(line, run, item));

            String where = " where mrp_requirement_line_id = '" + line + "'";
            assertThatThrownBy(() -> statement.execute(
                    "update mrp_requirement_lines set pending_qc_quantity = 5, pending_qc_source_count = null" + where))
                    .hasMessageContaining("chk_mrp_req_pending_qc");
            assertThatThrownBy(() -> statement.execute(
                    "update mrp_requirement_lines set pending_qc_quantity = null, pending_qc_source_count = 1" + where))
                    .hasMessageContaining("chk_mrp_req_pending_qc");
        }
    }

    /**
     * CF-5 / {@code V94}: an operation scheduled before V94 keeps the three captured-capacity columns {@code NULL} —
     * its schedule did not divide, so a value would describe a formula that never ran. The CHECK is all three NULL or
     * all three set with units {@code > 0}. SQL three-valued logic: {@code 2 > 0 AND NULL >= 0} is NULL and a CHECK
     * lets NULL through, so "one of three set" is rejected only because the {@code IS NOT NULL} terms are spelled out
     * (the exact bug of V93's first draft).
     */
    @Test
    void migrate_v94_capturedCapacityStaysNullOnOldOperations_checkRequiresAllThreeOrNone() throws Exception {
        String schema = "flyway_v94_captured_capacity";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .target("93")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111119401";
        String plant = "22222222-2222-4222-8222-222222229401";
        String warehouse = "33333333-3333-4333-8333-333333339401";
        String item = "55555555-5555-4555-8555-555555559401";
        String bom = "66666666-6666-4666-8666-666666669401";
        String workOrder = "77777777-7777-4777-8777-777777779401";
        String operation = "88888888-8888-4888-8888-888888889401";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("insert into companies (company_id, code, name) values ('%s', 'V94', 'V94')".formatted(company));
            statement.execute("insert into plants (plant_id, company_id, code, name, timezone) values"
                    + " ('%s', '%s', 'P', 'P', 'Asia/Ho_Chi_Minh')".formatted(plant, company));
            statement.execute("insert into warehouses (warehouse_id, plant_id, code, name) values ('%s', '%s', 'W', 'W')"
                    .formatted(warehouse, plant));
            statement.execute("insert into items (item_id, company_id, code, name, type, unit) values"
                    + " ('%s', '%s', 'FG', 'FG', 'FINISHED_GOOD', 'EA')".formatted(item, company));
            statement.execute("insert into bom_headers (bom_id, company_id, parent_item_id, revision, bom_code, version_number)"
                    + " values ('%s', '%s', '%s', 'A', 'FG-A', 1)".formatted(bom, company, item));
            statement.execute(("insert into work_orders (work_order_id, company_id, plant_id, work_order_no, product_item_id,"
                    + " bom_id, bom_revision, output_warehouse_id, planned_quantity, status) values ('%s', '%s', '%s',"
                    + " 'WO-V94', '%s', '%s', 'A', '%s', 10, 'RELEASED')")
                    .formatted(workOrder, company, plant, item, bom, warehouse));
            statement.execute(("insert into work_order_operations (work_order_operation_id, work_order_id, sequence, name,"
                    + " work_center_code, setup_minutes, run_minutes_per_unit, planned_start_at, planned_end_at)"
                    + " values ('%s', '%s', 1, 'Assembly', 'WC-1', 0, 12, TIMESTAMPTZ '2026-10-13T08:00:00Z',"
                    + " TIMESTAMPTZ '2026-10-13T10:00:00Z')").formatted(operation, workOrder));
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            try (ResultSet rows = statement.executeQuery("select captured_capacity_units, required_resource_minutes,"
                    + " active_elapsed_minutes from work_order_operations")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("captured_capacity_units")).isNull();
                assertThat(rows.getBigDecimal("required_resource_minutes")).isNull();
                assertThat(rows.getBigDecimal("active_elapsed_minutes")).isNull();
            }
            String update = "update work_order_operations set captured_capacity_units = %s,"
                    + " required_resource_minutes = %s, active_elapsed_minutes = %s";
            assertThatThrownBy(() -> statement.execute(update.formatted("2", "null", "null")))
                    .hasMessageContaining("chk_wo_operations_captured_capacity");
            assertThatThrownBy(() -> statement.execute(update.formatted("2", "120", "null")))
                    .hasMessageContaining("chk_wo_operations_captured_capacity");
            assertThatThrownBy(() -> statement.execute(update.formatted("null", "120", "60")))
                    .hasMessageContaining("chk_wo_operations_captured_capacity");
            assertThatThrownBy(() -> statement.execute(update.formatted("0", "120", "60")))
                    .hasMessageContaining("chk_wo_operations_captured_capacity");
            assertThatThrownBy(() -> statement.execute(update.formatted("2", "-1", "60")))
                    .hasMessageContaining("chk_wo_operations_captured_capacity");
            assertThatThrownBy(() -> statement.execute(update.formatted("2", "120", "-1")))
                    .hasMessageContaining("chk_wo_operations_captured_capacity");
            statement.execute(update.formatted("2", "120", "60"));
            statement.execute(update.formatted("1", "0", "0"));
            statement.execute(update.formatted("null", "null", "null"));
        }
    }

    /**
     * CF-9: after V95 a lot code is unique per item regardless of letter case. {@code LOT-X} and
     * {@code lot-x} of one item collide on {@code uk_inventory_lots_item_code_ci} (V8's index alone
     * compares case-sensitively and lets both in); the same code on another item is still allowed.
     */
    @Test
    void migrate_v95_rejectsACaseOnlyDuplicateLotCodeOfOneItem_allowsItOnAnotherItem() throws Exception {
        String schema = "flyway_v95_lot_code_ci";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111119501";
        String itemA = "55555555-5555-4555-8555-555555559501";
        String itemB = "55555555-5555-4555-8555-555555559502";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            seedV95CompanyAndItems(statement, company, itemA, itemB);
            statement.execute("insert into inventory_lots (item_id, lot_code) values ('%s', 'LOT-X')".formatted(itemA));

            assertThatThrownBy(() -> statement.execute(
                    "insert into inventory_lots (item_id, lot_code) values ('%s', 'lot-x')".formatted(itemA)))
                    .hasMessageContaining("uk_inventory_lots_item_code_ci");

            statement.execute("insert into inventory_lots (item_id, lot_code) values ('%s', 'lot-x')".formatted(itemB));
            try (ResultSet rows = statement.executeQuery("select count(*) from inventory_lots")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            try (ResultSet rows = statement.executeQuery("""
                    select count(*) from pg_indexes
                    where schemaname = '%s' and indexname = 'uk_inventory_lots_item_code_ci'
                    """.formatted(schema))) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }

    /**
     * CF-9: V95 refuses to run on a database that already holds two lots of one item whose codes
     * differ only by case — merging or renaming a lot rewrites stock history and needs a person. The
     * chain must stop at V94, not silently pick one of the two.
     */
    @Test
    void migrate_v95_failsOnADatabaseThatAlreadyHoldsACaseOnlyDuplicate() throws Exception {
        String schema = "flyway_v95_existing_duplicate";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .target("94")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111119511";
        String itemA = "55555555-5555-4555-8555-555555559511";
        String itemB = "55555555-5555-4555-8555-555555559512";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            seedV95CompanyAndItems(statement, company, itemA, itemB);
            statement.execute("insert into inventory_lots (item_id, lot_code) values ('%s', 'LOT-X')".formatted(itemA));
            statement.execute("insert into inventory_lots (item_id, lot_code) values ('%s', 'lot-x')".formatted(itemA));
        }

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .load();

        assertThatThrownBy(flyway::migrate).hasMessageContaining("uk_inventory_lots_item_code_ci");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("94");
    }

    private void seedV95CompanyAndItems(Statement statement, String company, String itemA, String itemB)
            throws SQLException {
        statement.execute("insert into companies (company_id, code, name) values ('%s', 'V95', 'V95')".formatted(company));
        statement.execute(("insert into items (item_id, company_id, code, name, type, unit, lot_tracked) values"
                + " ('%s', '%s', 'RAW-A', 'RAW-A', 'RAW_MATERIAL', 'EA', true),"
                + " ('%s', '%s', 'RAW-B', 'RAW-B', 'RAW_MATERIAL', 'EA', true)").formatted(itemA, company, itemB, company));
    }

    private Set<String> granted(String roleCode, List<String> codes) throws Exception {
        Set<String> granted = new TreeSet<>();
        String in = codes.stream().map(code -> "'" + code + "'").collect(java.util.stream.Collectors.joining(", "));
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s' AND p.code IN (%s)
                     """.formatted(roleCode, in))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    private Set<String> grantedCostingPermissions(String roleCode) throws Exception {
        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s' AND p.code IN ('PERM_COSTING_READ', 'PERM_COSTING_MANAGE')
                     """.formatted(roleCode))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    /**
     * C2-1: {@code PERM_AUDIT_READ} — ADMIN only, NO grant to MANAGER or OPERATOR at all (same
     * "cấm" shape as {@code migrate_v51_*}, not the OPERATOR-gets-read-only shape of
     * {@code migrate_v45_*}/{@code migrate_v47_*}/{@code migrate_v49_*}). {@code PermissionCatalogTest}
     * only proves the permission exists, never that MANAGER/OPERATOR are excluded from it — only a
     * real database answers that (same lesson as {@code migrate_v41_*}).
     */
    @Test
    void migrate_v55_grantsAuditReadToAdminOnly() throws Exception {
        migratePublicSchema();

        assertThat(grantedAuditPermissions("ADMIN")).containsExactly("PERM_AUDIT_READ");
        assertThat(grantedAuditPermissions("MANAGER")).isEmpty();
        assertThat(grantedAuditPermissions("OPERATOR")).isEmpty();
    }

    /**
     * DEC-09 separation of duties. The whole point of the Over-BOM gate is that the person asking
     * for extra material is not the person who releases it, so an OPERATOR holding the approval
     * permission would make the two-step flow decorative. {@code PermissionCatalogTest} cannot see
     * this: it proves the permission <em>row exists</em>, never who it is granted to (CLAUDE.md
     * §0.25) — only a really-migrated database answers that.
     */
    @Test
    void migrate_v66_grantsMaterialIssueApprovalToApproversOnly() throws Exception {
        migratePublicSchema();

        assertThat(grantedPermission("ADMIN", "PERM_MATERIAL_ISSUE_APPROVE"))
                .containsExactly("PERM_MATERIAL_ISSUE_APPROVE");
        assertThat(grantedPermission("MANAGER", "PERM_MATERIAL_ISSUE_APPROVE"))
                .containsExactly("PERM_MATERIAL_ISSUE_APPROVE");
        assertThat(grantedPermission("OPERATOR", "PERM_MATERIAL_ISSUE_APPROVE")).isEmpty();
    }

    /**
     * DEC-05: the trim-only normalization is enforced by the database, not just by the service.
     * A pre-check alone loses the race between two concurrent approvals of the same lot code.
     */
    @Test
    void migrate_v66_rejectsUntrimmedLotCodesAtTheDatabase() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT pg_get_constraintdef(oid) AS definition
                     FROM pg_constraint
                     WHERE conname = 'chk_inventory_lots_code_trimmed'
                     """)) {
            assertThat(rows.next())
                    .as("V66 must add chk_inventory_lots_code_trimmed")
                    .isTrue();
            assertThat(rows.getString("definition")).contains("btrim");
        }
    }

    private Set<String> grantedPermission(String roleCode, String permissionCode) throws Exception {
        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s' AND p.code = '%s'
                     """.formatted(roleCode, permissionCode))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    private Set<String> grantedAuditPermissions(String roleCode) throws Exception {
        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s' AND p.code = 'PERM_AUDIT_READ'
                     """.formatted(roleCode))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    /**
     * FE-4 5C: Item Master has dedicated wire permissions. READ is needed by every operational
     * role; MANAGE stays with ADMIN/MANAGER, matching the prior PERM_INVENTORY_* grants that V57
     * copies for backward-compatible role configuration.
     */
    @Test
    void migrate_v57_grantsItemPermissionsToTheDocumentedRoles() throws Exception {
        migratePublicSchema();

        assertThat(grantedItemPermissions("ADMIN"))
                .containsExactlyInAnyOrder("PERM_ITEM_READ", "PERM_ITEM_MANAGE");
        assertThat(grantedItemPermissions("MANAGER"))
                .containsExactlyInAnyOrder("PERM_ITEM_READ", "PERM_ITEM_MANAGE");
        assertThat(grantedItemPermissions("OPERATOR")).containsExactly("PERM_ITEM_READ");
    }


    /**
     * V58 makes {@code POST /planning-runs} replay-safe. Both halves matter and only the database
     * can answer either: the constraint must reject a second run claiming the same key, and it must
     * still let any number of key-less runs coexist — the header is optional and every run created
     * before V58 has {@code NULL}, so a constraint that treated NULLs as equal would break them all.
     */
    @Test
    void migrate_v58_rejectsADuplicateRunKeyButStillAllowsManyRunsWithoutOne() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(RUN_IDEMPOTENCY_SCHEMA)
                .load();
        flyway.migrate();

        String insertRun = """
                INSERT INTO mrp_runs (mrp_run_id, code, company_id, plant_id,
                                      horizon_start_date, horizon_end_date, status,
                                      created_at, updated_at, idempotency_key)
                VALUES ('%s', '%s', '55555555-5555-4555-8555-555555555555',
                        '66666666-6666-4666-8666-666666666666',
                        DATE '2026-08-01', DATE '2026-09-01', 'COMPLETED', now(), now(), %s)
                """;

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + RUN_IDEMPOTENCY_SCHEMA);
            statement.execute("""
                    INSERT INTO companies (company_id, code, name)
                    VALUES ('55555555-5555-4555-8555-555555555555', 'RUNIDEMP_CO', 'Run Idempotency Co')
                    """);
            statement.execute("""
                    INSERT INTO plants (plant_id, company_id, code, name)
                    VALUES ('66666666-6666-4666-8666-666666666666',
                            '55555555-5555-4555-8555-555555555555', 'RUNIDEMP_PL', 'Run Idempotency Plant')
                    """);

            statement.execute(insertRun.formatted(
                    "77777777-7777-4777-8777-777777777777", "RUN-77777777", "'SLIDE-SEED-1'"));

            assertThatThrownBy(() -> statement.execute(insertRun.formatted(
                    "88888888-8888-4888-8888-888888888888", "RUN-88888888", "'SLIDE-SEED-1'")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_mrp_runs_idempotency_key");

            // Runs submitted without the header must keep working, any number of them.
            statement.execute(insertRun.formatted(
                    "99999999-9999-4999-8999-999999999999", "RUN-99999999", "NULL"));
            statement.execute(insertRun.formatted(
                    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "RUN-AAAAAAAA", "NULL"));

            try (ResultSet rows = statement.executeQuery(
                    "SELECT count(*) AS total FROM mrp_runs WHERE idempotency_key IS NULL")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("total")).isEqualTo(2);
            }
        }
    }

    @Test
    void migrate_v60_grantsDataImportPermissionsToAdminAndManagerOnly() throws Exception {
        migratePublicSchema();

        assertThat(grantedDataImportPermissions("ADMIN"))
                .containsExactlyInAnyOrder("PERM_DATA_IMPORT_READ", "PERM_DATA_IMPORT_EXECUTE");
        assertThat(grantedDataImportPermissions("MANAGER"))
                .containsExactlyInAnyOrder("PERM_DATA_IMPORT_READ", "PERM_DATA_IMPORT_EXECUTE");
        assertThat(grantedDataImportPermissions("OPERATOR")).isEmpty();
    }

    /**
     * G2-1 / {@code V69}: the customer catalogue owns its code per company regardless of status, and
     * suppliers gain the two contact columns G2-2 maps. Asserted on the real catalog because the
     * entity mapping would happily compile against columns that do not exist.
     */
    @Test
    void migrate_v69_createsCustomersAndAddsSupplierContactColumns() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(columnLengths(statement, "customers"))
                    .containsEntry("code", 100)
                    .containsEntry("name", 255)
                    .containsEntry("contact_name", 255)
                    .containsEntry("email", 255)
                    .containsEntry("phone", 255)
                    .containsEntry("address", 500)
                    .containsEntry("notes", 500);
            assertThat(columnLengths(statement, "suppliers"))
                    .containsEntry("contact_name", 255)
                    .containsEntry("notes", 500);

            try (ResultSet rs = statement.executeQuery("""
                    SELECT pg_get_constraintdef(c.oid) AS def
                    FROM pg_constraint c
                    JOIN pg_class t ON t.oid = c.conrelid
                    WHERE t.relname = 'customers' AND c.conname = 'uk_customers_company_code'
                    """)) {
                assertThat(rs.next()).isTrue();
                // No status column in the key: a deactivated customer keeps its code.
                assertThat(rs.getString("def")).isEqualTo("UNIQUE (company_id, code)");
            }
        }
    }

    /**
     * G2-1 / {@code V70}: Customer copies Supplier's grant matrix from the database rather than
     * restating roles. Asserted both ways — the two sets must be equal, and they must be the documented
     * ADMIN + MANAGER with OPERATOR holding neither — so a copy that silently matched nothing (empty =
     * empty) cannot pass.
     */
    @Test
    void migrate_v70_grantsCustomerPermissionsToExactlyTheRolesHoldingSupplierPermissions() throws Exception {
        migratePublicSchema();

        assertThat(rolesHolding("PERM_CUSTOMER_READ")).isEqualTo(rolesHolding("PERM_SUPPLIER_READ"));
        assertThat(rolesHolding("PERM_CUSTOMER_MANAGE")).isEqualTo(rolesHolding("PERM_SUPPLIER_MANAGE"));
        assertThat(rolesHolding("PERM_CUSTOMER_READ")).containsExactlyInAnyOrder("ADMIN", "MANAGER");
        assertThat(rolesHolding("PERM_CUSTOMER_MANAGE")).containsExactlyInAnyOrder("ADMIN", "MANAGER");
    }

    /**
     * G3 / {@code V71}: the fixed-template batch columns on {@code import_runs}. Asserted against the
     * database because each guarantee lives there and nowhere else: an existing run reads back as
     * {@code MAPPED_PROFILE} without a backfill, a batch cannot exist without an owner, the three new
     * statuses are accepted, and an upload key is unique per owner — but only where one was sent.
     */
    @Test
    void migrate_v71_addsFixedTemplateBatchColumnsAndConstraints() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            UUID companyId = UUID.randomUUID();
            String tag = companyId.toString().substring(0, 8).toUpperCase();
            statement.execute("INSERT INTO companies (company_id, code, name) VALUES ('%s', 'V71_%s', 'V71')"
                    .formatted(companyId, companyId));
            String owner;
            try (ResultSet rs = statement.executeQuery("SELECT user_id FROM users ORDER BY created_at LIMIT 1")) {
                assertThat(rs.next()).isTrue();
                owner = rs.getString(1);
            }
            String insert = "INSERT INTO import_runs (import_run_id, code, target_type, company_id,"
                    + " original_filename, file_size_bytes, file_sha256, detected_headers, status";

            UUID legacy = UUID.randomUUID();
            statement.execute(insert + ") VALUES ('%s', 'L-%s', 'ITEM', '%s', 'a.xlsx', 1, 'x', '[]', 'PARSED')"
                    .formatted(legacy, tag, companyId));
            try (ResultSet rs = statement.executeQuery(
                    "SELECT kind FROM import_runs WHERE import_run_id = '%s'".formatted(legacy))) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("kind")).isEqualTo("MAPPED_PROFILE");
            }

            assertThatThrownBy(() -> statement.execute((insert + ", kind) VALUES (gen_random_uuid(),"
                    + " 'N-%s', 'CUSTOMER', '%s', 'a.xlsx', 1, 'x', '[]', 'READY', 'FIXED_TEMPLATE')")
                    .formatted(tag, companyId)))
                    .hasMessageContaining("chk_import_runs_fixed_template_owner");

            String batch = insert + ", kind, owner_user_id, upload_idempotency_key) VALUES (gen_random_uuid(),"
                    + " '%s', 'CUSTOMER', '%s', 'a.xlsx', 1, 'x', '[]', '%s', 'FIXED_TEMPLATE', '%s', %s)";
            statement.execute(batch.formatted("B1-" + tag, companyId, "READY", owner, "'K-" + tag + "'"));
            statement.execute(batch.formatted("B2-" + tag, companyId, "COMMITTED", owner, "NULL"));
            statement.execute(batch.formatted("B3-" + tag, companyId, "INVALID", owner, "NULL"));
            assertThatThrownBy(() -> statement.execute(
                    batch.formatted("B4-" + tag, companyId, "READY", owner, "'K-" + tag + "'")))
                    .hasMessageContaining("uk_import_runs_owner_upload_key");
        }
    }

    /**
     * G4-2 / {@code V72}: a new {@code MovementType} is a breaking change for
     * {@code chk_stock_movements_type} (coding-rules.md §11.3). The constraint must accept both transfer
     * types and still carry every older value — V26 once shipped a version that forgot REVERSAL.
     */
    @Test
    void migrate_v72_movementTypeCheckAcceptsTransfersAndKeepsEveryOlderType() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT pg_get_constraintdef(oid) FROM pg_constraint
                     WHERE connamespace = 'public'::regnamespace
                       AND conname = 'chk_stock_movements_type'
                     """)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).contains("'RECEIVE'", "'ISSUE'", "'ADJUST_IN'", "'ADJUST_OUT'",
                    "'REVERSAL'", "'LOT_STATUS_CHANGE'", "'TRANSFER_OUT'", "'TRANSFER_IN'");
        }
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT conname FROM pg_constraint
                     WHERE conname IN ('uk_stock_transfers_idempotency_key', 'uk_stock_transfers_code',
                                       'chk_stock_transfers_distinct_warehouses',
                                       'uk_stock_transfer_lines_transfer_line_no')
                     """)) {
            Set<String> names = new TreeSet<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            assertThat(names).hasSize(4);
        }
    }

    /**
     * G5 / {@code V73}: BOM and routing versions that existed before the migration get version numbers
     * 1..n per product in creation order, every BOM gets a code ("product-revision", case-insensitively
     * unique per company — a collision gets its version appended) and an output quantity of 1, which
     * reproduces exactly what its per-unit lines meant. The revision label is never parsed.
     */
    @Test
    void migrate_v73_backfillsVersionNumbersBomCodesAndOutputQuantity() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(VERSION_BACKFILL_SCHEMA)
                .target("72")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111111111";
        String itemAb = "33333333-3333-4333-8333-333333333331";
        String itemA = "33333333-3333-4333-8333-333333333332";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + VERSION_BACKFILL_SCHEMA);
            statement.execute("INSERT INTO companies (company_id, code, name) VALUES ('%s', 'V73_CO', 'V73')"
                    .formatted(company));
            statement.execute(("INSERT INTO items (item_id, company_id, code, name, type, unit) VALUES "
                    + "('%s', '%s', 'A-B', 'A-B', 'FINISHED_GOOD', 'EA'), ('%s', '%s', 'A', 'A', 'FINISHED_GOOD', 'EA')")
                    .formatted(itemAb, company, itemA, company));
            // Item A-B: revision "R2" (older) and "r1" (newer). Item A, revision "B-R2", spells A-B-R2 too.
            String bom = "INSERT INTO bom_headers (bom_id, company_id, parent_item_id, revision, status, created_at)"
                    + " VALUES ('%s', '%s', '%s', '%s', '%s', '%s')";
            statement.execute(bom.formatted("55555555-5555-4555-8555-555555555551", company, itemAb, "R2", "INACTIVE", "2026-01-01T00:00:00Z"));
            statement.execute(bom.formatted("55555555-5555-4555-8555-555555555552", company, itemAb, "r1", "ACTIVE", "2026-02-01T00:00:00Z"));
            statement.execute(bom.formatted("55555555-5555-4555-8555-555555555553", company, itemA, "B-R2", "DRAFT", "2026-03-01T00:00:00Z"));
            String routing = "INSERT INTO routings (routing_id, company_id, item_id, code, routing_version, status, created_at)"
                    + " VALUES ('%s', '%s', '%s', 'RT', '%s', '%s', '%s')";
            statement.execute(routing.formatted("77777777-7777-4777-8777-777777777771", company, itemAb, "V9", "INACTIVE", "2026-01-01T00:00:00Z"));
            statement.execute(routing.formatted("77777777-7777-4777-8777-777777777772", company, itemAb, "V10", "ACTIVE", "2026-02-01T00:00:00Z"));
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(VERSION_BACKFILL_SCHEMA)
                .target("73")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + VERSION_BACKFILL_SCHEMA);
            java.util.Map<String, String> codes = new java.util.HashMap<>();
            java.util.Map<String, Integer> versions = new java.util.HashMap<>();
            try (ResultSet rs = statement.executeQuery(
                    "SELECT bom_id, bom_code, version_number, output_quantity FROM bom_headers")) {
                while (rs.next()) {
                    codes.put(rs.getString("bom_id"), rs.getString("bom_code"));
                    versions.put(rs.getString("bom_id"), rs.getInt("version_number"));
                    assertThat(rs.getBigDecimal("output_quantity")).isEqualByComparingTo("1");
                }
            }
            assertThat(versions.get("55555555-5555-4555-8555-555555555551")).isEqualTo(1);
            assertThat(versions.get("55555555-5555-4555-8555-555555555552")).isEqualTo(2);
            assertThat(versions.get("55555555-5555-4555-8555-555555555553")).isEqualTo(1);
            assertThat(codes.get("55555555-5555-4555-8555-555555555551")).isEqualTo("A-B-R2");
            assertThat(codes.get("55555555-5555-4555-8555-555555555552")).isEqualTo("A-B-R1");
            // the later row of the "A-B-R2" collision gets its version number appended
            assertThat(codes.get("55555555-5555-4555-8555-555555555553")).isEqualTo("A-B-R2-V1");
            try (ResultSet rs = statement.executeQuery(
                    "SELECT routing_version, version_number FROM routings ORDER BY created_at")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("V9");
                assertThat(rs.getInt(2)).isEqualTo(1);
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("V10");
                assertThat(rs.getInt(2)).isEqualTo(2);
            }
            assertThatThrownBy(() -> statement.execute(("INSERT INTO bom_headers (company_id, parent_item_id, revision,"
                    + " bom_code, version_number) VALUES ('%s', '%s', 'X', 'a-b-r1', 9)").formatted(company, itemA)))
                    .hasMessageContaining("uk_bom_headers_company_bom_code");
            assertThatThrownBy(() -> statement.execute(("INSERT INTO bom_headers (company_id, parent_item_id, revision,"
                    + " bom_code, version_number, output_quantity) VALUES ('%s', '%s', 'Y', 'NEW', 5, 0)")
                    .formatted(company, itemA)))
                    .hasMessageContaining("chk_bom_headers_output_quantity");
        }
    }

    /** G5 / {@code V73}: a predecessor link can never point an operation at itself. */
    @Test
    void migrate_v73_predecessorTableRefusesASelfLink() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT pg_get_constraintdef(oid) FROM pg_constraint
                     WHERE conname = 'chk_routing_operation_predecessors_not_self'
                     """)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).contains("routing_operation_id <> predecessor_operation_id");
        }
    }

    /**
     * G6a: a reservation now has exactly one owner — a work order component line or a sales order
     * line — and the order-number sequence exists. Adding the CHECK validates every historical row,
     * so a clean migration is itself the proof that existing work-order reservations still satisfy it.
     */
    @Test
    void migrate_v74_reservationsHaveExactlyOneOwner_andOrderNumbersComeFromASequence() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("""
                    SELECT pg_get_constraintdef(oid) FROM pg_constraint
                    WHERE conname = 'chk_material_reservations_single_owner'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).contains("work_order_id IS NOT NULL").contains("sales_order_line_id IS NOT NULL");
            }
            try (ResultSet rs = statement.executeQuery("""
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'material_reservations'
                      AND column_name IN ('work_order_id', 'component_line_id')
                    """)) {
                int columns = 0;
                while (rs.next()) {
                    assertThat(rs.getString(1)).isEqualTo("YES");
                    columns++;
                }
                assertThat(columns).isEqualTo(2);
            }
            try (ResultSet rs = statement.executeQuery("SELECT nextval('sales_order_number_seq')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isPositive();
            }
        }
    }

    /**
     * G6b: commercial runs and the conversion ledger. Existing runs default to STANDARD with no
     * source version, and the requirement-line CHECK accepts the new RUN_ITEM_MAPPING source.
     */
    @Test
    void migrate_v75_addsRunKindAndTheConversionLedger() throws Exception {
        migratePublicSchema();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("""
                    SELECT column_default FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'mrp_runs' AND column_name = 'run_kind'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).contains("STANDARD");
            }
            try (ResultSet rs = statement.executeQuery("""
                    SELECT pg_get_constraintdef(oid) FROM pg_constraint
                    WHERE conname = 'chk_mrp_req_warehouse_resolution_source'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).contains("RUN_ITEM_MAPPING").contains("ITEM_WAREHOUSE_DEFAULT");
            }
            try (ResultSet rs = statement.executeQuery("""
                    SELECT count(*) FROM pg_constraint
                    WHERE connamespace = 'public'::regnamespace
                      AND conname IN ('uk_supply_suggestion_conversions_run_key_entry',
                                      'chk_supply_suggestion_conversions_quantity',
                                      'chk_supply_suggestion_conversions_target_type')
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
        }
    }

    /**
     * G7a / {@code V76}: purchase orders already SENT/RECEIVED are renamed in place to
     * CONFIRMED/FULLY_RECEIVED (D1, precedent V26), the CHECK refuses the old names and accepts CLOSED,
     * and received + closed may never exceed ordered. Seeded at V75 in its own schema, then migrated.
     */
    @Test
    void migrate_v76_renamesPurchaseOrderStatusesAndGuardsClosedQuantity() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(PO_LIFECYCLE_SCHEMA)
                .target("75")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111117601";
        String plant = "22222222-2222-4222-8222-222222227601";
        String warehouse = "33333333-3333-4333-8333-333333337601";
        String supplier = "44444444-4444-4444-8444-444444447601";
        String item = "55555555-5555-4555-8555-555555557601";
        String order = "insert into purchase_orders (purchase_order_id, company_id, plant_id, warehouse_id, supplier_id,"
                + " purchase_order_no, status, order_date, expected_date)"
                + " values ('%s', '" + company + "', '" + plant + "', '" + warehouse + "', '" + supplier
                + "', '%s', '%s', date '2026-10-01', date '2026-10-10')";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + PO_LIFECYCLE_SCHEMA);
            statement.execute("insert into companies (company_id, code, name) values ('%s', 'V76', 'V76')".formatted(company));
            statement.execute("insert into plants (plant_id, company_id, code, name) values ('%s', '%s', 'P', 'P')"
                    .formatted(plant, company));
            statement.execute("insert into warehouses (warehouse_id, plant_id, code, name) values ('%s', '%s', 'W', 'W')"
                    .formatted(warehouse, plant));
            statement.execute("insert into suppliers (supplier_id, company_id, code, name) values ('%s', '%s', 'S', 'S')"
                    .formatted(supplier, company));
            statement.execute("insert into items (item_id, company_id, code, name, type, unit) values"
                    + " ('%s', '%s', 'RM', 'RM', 'RAW_MATERIAL', 'KG')".formatted(item, company));
            statement.execute(order.formatted("66666666-6666-4666-8666-666666667601", "PO-SENT", "SENT"));
            statement.execute(order.formatted("66666666-6666-4666-8666-666666667602", "PO-RECEIVED", "RECEIVED"));
            statement.execute(order.formatted("66666666-6666-4666-8666-666666667603", "PO-DRAFT", "DRAFT"));
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(PO_LIFECYCLE_SCHEMA)
                .target("76")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + PO_LIFECYCLE_SCHEMA);
            java.util.Map<String, String> statuses = new java.util.HashMap<>();
            try (ResultSet rs = statement.executeQuery("select purchase_order_no, status, confirmed_at from purchase_orders")) {
                while (rs.next()) {
                    statuses.put(rs.getString(1), rs.getString(2));
                    // Not backfilled: nobody recorded when a pre-G7a order was sent.
                    assertThat(rs.getTimestamp(3)).isNull();
                }
            }
            assertThat(statuses).containsEntry("PO-SENT", "CONFIRMED")
                    .containsEntry("PO-RECEIVED", "FULLY_RECEIVED")
                    .containsEntry("PO-DRAFT", "DRAFT");
            assertThatThrownBy(() -> statement.execute(
                    order.formatted("66666666-6666-4666-8666-666666667604", "PO-OLD", "SENT")))
                    .hasMessageContaining("chk_purchase_orders_status");
            statement.execute(order.formatted("66666666-6666-4666-8666-666666667605", "PO-CLOSED", "CLOSED"));
            String line = "insert into purchase_order_lines (purchase_order_id, item_id, ordered_quantity,"
                    + " received_quantity, closed_quantity, expected_date) values"
                    + " ('66666666-6666-4666-8666-666666667605', '" + item + "', 10, %s, %s, date '2026-10-10')";
            statement.execute(line.formatted("6", "4"));
            assertThatThrownBy(() -> statement.execute(line.formatted("6", "4.000001")))
                    .hasMessageContaining("chk_po_lines_received_closed_within_ordered");
            try (ResultSet rs = statement.executeQuery("select nextval('purchase_order_number_seq')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(1L);
            }
        }
    }

    /**
     * G7b / {@code V77}: a receipt posted before G7b gets its received date from posted_at in the
     * plant's zone (20:00Z = 03:00 next day in Asia/Ho_Chi_Minh), is not marked quality-held, a DRAFT
     * may have no posted_at but a POSTED one may not, and the two new goods receipt permissions go to
     * ADMIN and MANAGER only (FE Q24).
     */
    @Test
    void migrate_v77_backfillsReceivedDateInThePlantZone_andGuardsPostedAt() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(GR_DRAFT_SCHEMA)
                .target("76")
                .load()
                .migrate();

        String company = "11111111-1111-4111-8111-111111117701";
        String plant = "22222222-2222-4222-8222-222222227701";
        String warehouse = "33333333-3333-4333-8333-333333337701";
        String supplier = "44444444-4444-4444-8444-444444447701";
        String order = "66666666-6666-4666-8666-666666667701";
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + GR_DRAFT_SCHEMA);
            statement.execute("insert into companies (company_id, code, name) values ('%s', 'V77', 'V77')".formatted(company));
            statement.execute("insert into plants (plant_id, company_id, code, name, timezone) values ('%s', '%s', 'P', 'P', 'Asia/Ho_Chi_Minh')"
                    .formatted(plant, company));
            statement.execute("insert into warehouses (warehouse_id, plant_id, code, name) values ('%s', '%s', 'W', 'W')"
                    .formatted(warehouse, plant));
            statement.execute("insert into suppliers (supplier_id, company_id, code, name) values ('%s', '%s', 'S', 'S')"
                    .formatted(supplier, company));
            statement.execute(("insert into purchase_orders (purchase_order_id, company_id, plant_id, warehouse_id, supplier_id,"
                    + " purchase_order_no, status, order_date, expected_date) values ('%s', '%s', '%s', '%s', '%s', 'PO-1',"
                    + " 'PARTIALLY_RECEIVED', date '2026-10-01', date '2026-10-10')").formatted(order, company, plant, warehouse, supplier));
            statement.execute(("insert into goods_receipts (purchase_order_id, warehouse_id, receipt_no, status, posted_at,"
                    + " idempotency_key) values ('%s', '%s', 'GR-OLD', 'POSTED', timestamptz '2026-10-18T20:00:00Z', 'K-OLD')")
                    .formatted(order, warehouse));
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(GR_DRAFT_SCHEMA)
                .target("77")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + GR_DRAFT_SCHEMA);
            try (ResultSet rs = statement.executeQuery("select received_date, quality_held from goods_receipts where receipt_no = 'GR-OLD'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDate(1).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 19));
                assertThat(rs.getBoolean(2)).isFalse();
            }
            String insert = "insert into goods_receipts (purchase_order_id, warehouse_id, receipt_no, status, received_date)"
                    + " values ('" + order + "', '" + warehouse + "', '%s', '%s', date '2026-10-19')";
            statement.execute(insert.formatted("GR-DRAFT", "DRAFT"));
            assertThatThrownBy(() -> statement.execute(insert.formatted("GR-BAD", "POSTED")))
                    .hasMessageContaining("chk_goods_receipts_posted_at");
            try (ResultSet rs = statement.executeQuery("""
                    select r.code, string_agg(p.code, ',' order by p.code)
                    from role_permissions rp join roles r on r.role_id = rp.role_id
                    join permissions p on p.permission_id = rp.permission_id
                    where p.code in ('PERM_GOODS_RECEIPT_READ', 'PERM_GOODS_RECEIPT_MANAGE')
                    group by r.code order by r.code
                    """)) {
                java.util.Map<String, String> grants = new java.util.HashMap<>();
                while (rs.next()) {
                    grants.put(rs.getString(1), rs.getString(2));
                }
                assertThat(grants).containsOnlyKeys("ADMIN", "MANAGER");
                assertThat(grants.get("MANAGER")).isEqualTo("PERM_GOODS_RECEIPT_MANAGE,PERM_GOODS_RECEIPT_READ");
            }
        }
    }

    private java.util.Map<String, Integer> columnLengths(Statement statement, String table) throws SQLException {
        java.util.Map<String, Integer> lengths = new java.util.HashMap<>();
        try (ResultSet rs = statement.executeQuery("""
                SELECT column_name, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = '%s'
                """.formatted(table))) {
            while (rs.next()) {
                lengths.put(rs.getString("column_name"), rs.getInt("character_maximum_length"));
            }
        }
        return lengths;
    }

    private Set<String> rolesHolding(String permissionCode) throws Exception {
        Set<String> roles = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT r.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE p.code = '%s'
                     """.formatted(permissionCode))) {
            while (rows.next()) {
                roles.add(rows.getString("code"));
            }
        }
        return roles;
    }

    private Set<String> grantedDataImportPermissions(String roleCode) throws Exception {
        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s'
                       AND p.code IN ('PERM_DATA_IMPORT_READ', 'PERM_DATA_IMPORT_EXECUTE')
                     """.formatted(roleCode))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    private Set<String> grantedItemPermissions(String roleCode) throws Exception {
        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s' AND p.code IN ('PERM_ITEM_READ', 'PERM_ITEM_MANAGE')
                     """.formatted(roleCode))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    private Set<String> grantedUomPermissions(String roleCode) throws Exception {
        Set<String> granted = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.role_id = rp.role_id
                     JOIN permissions p ON p.permission_id = rp.permission_id
                     WHERE r.code = '%s' AND p.code IN ('PERM_UOM_READ', 'PERM_UOM_MANAGE')
                     """.formatted(roleCode))) {
            while (rows.next()) {
                granted.add(rows.getString("code"));
            }
        }
        return granted;
    }

    /**
     * P5: {@code chk_items_tracking_exclusive} (V52) is the DB-level half of the mutual-exclusivity
     * decision — {@code ItemServiceTest} already proves the service rejects both flags before saving,
     * this proves the constraint backs it up even for a row written outside the service layer.
     */
    @Test
    void migrate_v52_rejectsAnItemThatIsBothLotAndSerialTracked() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(SERIAL_TRACKING_SCHEMA)
                .load();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + SERIAL_TRACKING_SCHEMA);
            statement.execute("""
                    INSERT INTO companies (company_id, code, name)
                    VALUES ('66666666-6666-4666-8666-666666666666', 'SN_CO', 'Serial Co')
                    """);

            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO items (item_id, company_id, code, name, type, unit, lot_tracked, serial_tracked)
                    VALUES ('77777777-7777-4777-8777-777777777777',
                            '66666666-6666-4666-8666-666666666666', 'SN-ITEM', 'Serial Item',
                            'FINISHED_GOOD', 'EA', true, true)
                    """))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_items_tracking_exclusive");

            // A serial-tracked-only item is unaffected by the constraint.
            statement.execute("""
                    INSERT INTO items (item_id, company_id, code, name, type, unit, lot_tracked, serial_tracked)
                    VALUES ('88888888-8888-4888-8888-888888888888',
                            '66666666-6666-4666-8666-666666666666', 'SN-ITEM-2', 'Serial Item 2',
                            'FINISHED_GOOD', 'EA', false, true)
                    """);
        }
    }

    /** Migrating the shared public schema is idempotent, so each test can ask for it independently. */
    private void migratePublicSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /**
     * D4.3: {@code V36} makes {@code mrp_runs.code} NOT NULL, so it has to backfill rows that already
     * exist — and with the <em>same</em> formula {@link MrpRun#assignCode()} uses, or a historical run
     * would carry a code that cannot be traced back to its id.
     *
     * <p>Migrating to V35 first, seeding a run, then finishing the chain is the only way to exercise
     * that: the test above only ever sees an empty database. Runs in its own schema so it does not
     * disturb the shared container's public schema.
     */
    @Test
    void migrate_v36_backfillsExistingRunsWithTheSameCodeFormulaAsTheEntity() throws Exception {
        UUID runId = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000001");

        Flyway toV35 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(BACKFILL_SCHEMA)
                .target("35")
                .load();
        toV35.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + BACKFILL_SCHEMA);
            statement.execute("""
                    INSERT INTO companies (company_id, code, name)
                    VALUES ('11111111-1111-4111-8111-111111111111', 'BACKFILL_CO', 'Backfill Co')
                    """);
            statement.execute("""
                    INSERT INTO plants (plant_id, company_id, code, name)
                    VALUES ('22222222-2222-4222-8222-222222222222',
                            '11111111-1111-4111-8111-111111111111', 'BACKFILL_PL', 'Backfill Plant')
                    """);
            statement.execute("""
                    INSERT INTO mrp_runs (mrp_run_id, company_id, plant_id,
                                          horizon_start_date, horizon_end_date, status)
                    VALUES ('%s', '11111111-1111-4111-8111-111111111111',
                            '22222222-2222-4222-8222-222222222222',
                            DATE '2026-07-01', DATE '2026-07-31', 'COMPLETED')
                    """.formatted(runId));
        }

        Flyway toV36 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(BACKFILL_SCHEMA)
                .load();
        toV36.migrate();

        MrpRun sameIdInJava = MrpRun.builder().mrpRunId(runId).build();
        sameIdInJava.assignCode();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + BACKFILL_SCHEMA);
            try (ResultSet rows = statement.executeQuery(
                    "SELECT code, shortage_lines, blocked_proposals FROM mrp_runs WHERE mrp_run_id = '"
                            + runId + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("code")).isEqualTo("RUN-A1B2C3D4");
                assertThat(rows.getString("code")).isEqualTo(sameIdInJava.getCode());
                // The new counters default to 0 rather than being recomputed for historical runs.
                assertThat(rows.getInt("shortage_lines")).isZero();
                assertThat(rows.getInt("blocked_proposals")).isZero();
            }
        }
    }

    /**
     * D6.5: proof at the <b>database</b> layer that {@code V37} rescoped the ledger's idempotency key.
     * A mocked repository can only show that the <em>query</em> now passes a movement type; only the
     * real constraint can show the DB accepts two rows sharing a key across operations — the half that
     * debts #8 and #23 slipped through for want of.
     *
     * <p>Migrates to V35 first (the last version before the {@code V36} run-header change, which is
     * irrelevant here) so the same key can be proven <em>rejected</em> under V8's table-wide constraint,
     * then finishes the chain and re-inserts to show it now passes. Uses its own schema so the shared
     * container's public schema is untouched.
     */
    @Test
    void migrate_v37_allowsOneIdempotencyKeyPerMovementTypeInsteadOfPerTable() throws Exception {
        Flyway toV35 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(IDEMPOTENCY_SCHEMA)
                .target("35")
                .load();
        toV35.migrate();

        String insertIssueWithSharedKey = """
                INSERT INTO stock_movements (item_id, warehouse_id, movement_type, direction,
                                             quantity, idempotency_key)
                VALUES ('33333333-3333-4333-8333-333333333333',
                        '44444444-4444-4444-8444-444444444444',
                        'ISSUE', 'OUT', 2, 'SHARED-KEY')
                """;

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + IDEMPOTENCY_SCHEMA);
            statement.execute("""
                    INSERT INTO companies (company_id, code, name)
                    VALUES ('11111111-1111-4111-8111-111111111111', 'IDEMP_CO', 'Idempotency Co')
                    """);
            statement.execute("""
                    INSERT INTO plants (plant_id, company_id, code, name)
                    VALUES ('22222222-2222-4222-8222-222222222222',
                            '11111111-1111-4111-8111-111111111111', 'IDEMP_PL', 'Idempotency Plant')
                    """);
            statement.execute("""
                    INSERT INTO items (item_id, company_id, code, name, type, unit)
                    VALUES ('33333333-3333-4333-8333-333333333333',
                            '11111111-1111-4111-8111-111111111111', 'IDEMP-ITEM', 'Idempotency Item',
                            'RAW_MATERIAL', 'KG')
                    """);
            statement.execute("""
                    INSERT INTO warehouses (warehouse_id, plant_id, code, name, type)
                    VALUES ('44444444-4444-4444-8444-444444444444',
                            '22222222-2222-4222-8222-222222222222', 'IDEMP-WH', 'Idempotency WH',
                            'RAW_MATERIAL')
                    """);
            statement.execute("""
                    INSERT INTO stock_movements (item_id, warehouse_id, movement_type, direction,
                                                 quantity, idempotency_key)
                    VALUES ('33333333-3333-4333-8333-333333333333',
                            '44444444-4444-4444-8444-444444444444',
                            'RECEIVE', 'IN', 10, 'SHARED-KEY')
                    """);

            // Debt #10 at the storage layer: under V8's UNIQUE (idempotency_key) the ledger physically
            // could not hold a second operation under the same key, which is why the service had to
            // hand the RECEIVE document back to an /issue caller.
            assertThatThrownBy(() -> statement.execute(insertIssueWithSharedKey))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_stock_movements_idempotency_key");
        }

        Flyway toV37 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(IDEMPOTENCY_SCHEMA)
                .load();
        // Migrating over a table that already holds data: widening the key can only reduce collisions,
        // so V37 needs no backfill and cannot fail here.
        toV37.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + IDEMPOTENCY_SCHEMA);

            statement.execute(insertIssueWithSharedKey);

            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) AS total FROM stock_movements WHERE idempotency_key = 'SHARED-KEY'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("total")).isEqualTo(2);
            }

            // Still exactly one row per (key, type): replay protection inside one operation is intact.
            assertThatThrownBy(() -> statement.execute(insertIssueWithSharedKey))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_stock_movements_idempotency_key");
        }
    }

    /**
     * F10 / debt H: {@code V40} makes {@code code} NOT NULL on two tables that never had one, so it
     * has to backfill the rows already there — with the <em>same</em> formula the entities'
     * {@code @PrePersist} uses, or a historical document ends up with a number that cannot be traced
     * back to its id, and UNIQUE would not notice.
     *
     * <p>Migrating to V39 first, seeding one document of each kind, then finishing the chain is the
     * only way to exercise the backfill: the empty-database test above never sees a row to fix. Same
     * shape as the {@code V36} case, in its own schema.
     */
    @Test
    void migrate_v40_backfillsExistingDocumentsWithTheSameCodeFormulaAsTheEntities() throws Exception {
        UUID issueId = UUID.fromString("b1b2c3d4-0000-4000-8000-000000000002");
        UUID executionId = UUID.fromString("c1b2c3d4-0000-4000-8000-000000000003");
        UUID workOrderId = UUID.fromString("d1b2c3d4-0000-4000-8000-000000000004");

        Flyway toV39 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(DOCUMENT_CODE_SCHEMA)
                .target("39")
                .load();
        toV39.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + DOCUMENT_CODE_SCHEMA);
            statement.execute("""
                    INSERT INTO companies (company_id, code, name)
                    VALUES ('11111111-1111-4111-8111-111111111111', 'CODE_CO', 'Code Co')
                    """);
            statement.execute("""
                    INSERT INTO plants (plant_id, company_id, code, name)
                    VALUES ('22222222-2222-4222-8222-222222222222',
                            '11111111-1111-4111-8111-111111111111', 'CODE_PL', 'Code Plant')
                    """);
            statement.execute("""
                    INSERT INTO items (item_id, company_id, code, name, type, unit)
                    VALUES ('33333333-3333-4333-8333-333333333333',
                            '11111111-1111-4111-8111-111111111111', 'CODE-ITEM', 'Code Item',
                            'FINISHED_GOOD', 'EA')
                    """);
            statement.execute("""
                    INSERT INTO warehouses (warehouse_id, plant_id, code, name, type)
                    VALUES ('44444444-4444-4444-8444-444444444444',
                            '22222222-2222-4222-8222-222222222222', 'CODE-WH', 'Code WH',
                            'FINISHED_GOODS')
                    """);
            statement.execute("""
                    INSERT INTO bom_headers (bom_id, company_id, parent_item_id, revision, status)
                    VALUES ('55555555-5555-4555-8555-555555555555',
                            '11111111-1111-4111-8111-111111111111',
                            '33333333-3333-4333-8333-333333333333', 'R1', 'ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO work_orders (work_order_id, company_id, plant_id, work_order_no,
                                             product_item_id, bom_id, bom_revision,
                                             output_warehouse_id, planned_quantity, status)
                    VALUES ('%s', '11111111-1111-4111-8111-111111111111',
                            '22222222-2222-4222-8222-222222222222', 'WO-CODE-1',
                            '33333333-3333-4333-8333-333333333333',
                            '55555555-5555-4555-8555-555555555555', 'R1',
                            '44444444-4444-4444-8444-444444444444', 10, 'IN_PROGRESS')
                    """.formatted(workOrderId));
            statement.execute("""
                    INSERT INTO material_issues (issue_id, work_order_id, idempotency_key)
                    VALUES ('%s', '%s', 'CODE-ISSUE-KEY')
                    """.formatted(issueId, workOrderId));
            statement.execute("""
                    INSERT INTO production_executions (production_execution_id, work_order_id,
                                                       good_quantity, idempotency_key)
                    VALUES ('%s', '%s', 5, 'CODE-EXEC-KEY')
                    """.formatted(executionId, workOrderId));
        }

        Flyway toV40 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(DOCUMENT_CODE_SCHEMA)
                .load();
        toV40.migrate();

        MaterialIssue sameIssueIdInJava = MaterialIssue.builder().issueId(issueId).build();
        sameIssueIdInJava.assignCode();
        ProductionExecution sameExecutionIdInJava =
                ProductionExecution.builder().productionExecutionId(executionId).build();
        sameExecutionIdInJava.assignCode();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + DOCUMENT_CODE_SCHEMA);
            try (ResultSet rows = statement.executeQuery(
                    "SELECT code FROM material_issues WHERE issue_id = '" + issueId + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("code")).isEqualTo("MI-B1B2C3D4");
                assertThat(rows.getString("code")).isEqualTo(sameIssueIdInJava.getCode());
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT code FROM production_executions WHERE production_execution_id = '"
                            + executionId + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("code")).isEqualTo("PE-C1B2C3D4");
                assertThat(rows.getString("code")).isEqualTo(sameExecutionIdInJava.getCode());
            }
            // Debt G: historical requirement lines are deliberately left NULL rather than backfilled
            // with available + openSupply, which is the very number the column exists to replace.
            try (ResultSet rows = statement.executeQuery("""
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_schema = '%s' AND table_name = 'mrp_requirement_lines'
                      AND column_name = 'projected_available_quantity'
                    """.formatted(DOCUMENT_CODE_SCHEMA))) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("is_nullable")).isEqualTo("YES");
            }
        }
    }

    /**
     * {@code V67}: the widened {@code trace_id} column and the {@code event_id} unique index.
     *
     * <p>Both are load-bearing and neither is visible to a mocked repository. The column was 32 while
     * {@code TraceIdFilter} accepts 64, so a legitimate upstream trace id made the audit INSERT fail
     * <em>after</em> the business transaction had committed. The unique index is what turns a
     * dispatcher retry after a crash into a no-op instead of a duplicate row.
     */
    @Test
    void migrate_v67_widensTraceIdAndMakesEventIdUnique() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(AUDIT_PIPELINE_SCHEMA)
                .load();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + AUDIT_PIPELINE_SCHEMA);

            try (ResultSet rs = statement.executeQuery("""
                    SELECT character_maximum_length FROM information_schema.columns
                    WHERE table_schema = '""" + AUDIT_PIPELINE_SCHEMA + """
                    ' AND table_name = 'audit_logs' AND column_name = 'trace_id'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(64);
            }

            UUID eventId = UUID.randomUUID();
            String insert = """
                    INSERT INTO audit_logs (audit_id, action, status, event_id, created_at)
                    VALUES ('%s', 'LOGIN', 'SUCCESS', '%s', now())
                    """;
            statement.execute(String.format(insert, UUID.randomUUID(), eventId));

            assertThatThrownBy(() -> statement.execute(
                    String.format(insert, UUID.randomUUID(), eventId)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_audit_logs_event_id");

            // NULL event_id is the historical case and must stay unconstrained: every row written
            // before V67 has one, and the partial index exists precisely so they do not collide.
            String insertWithoutEvent = """
                    INSERT INTO audit_logs (audit_id, action, status, created_at)
                    VALUES ('%s', 'LOGIN', 'SUCCESS', now())
                    """;
            statement.execute(String.format(insertWithoutEvent, UUID.randomUUID()));
            statement.execute(String.format(insertWithoutEvent, UUID.randomUUID()));
        }
    }

    /**
     * {@code V68}: the audit tables reject UPDATE and DELETE for every role, including the superuser
     * Testcontainers connects as.
     *
     * <p>That role choice is the point. A grant-based control would be bypassed by a superuser and the
     * test would pass without proving anything; a trigger applies to everyone, which is why it is the
     * primary control and the REVOKE is documented as defence in depth.
     */
    @Test
    void migrate_v68_makesAuditRowsUnwritableExceptOnTheMaintenancePath() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(AUDIT_PIPELINE_SCHEMA)
                .load();
        flyway.migrate();

        UUID auditId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + AUDIT_PIPELINE_SCHEMA);
            statement.execute("""
                    INSERT INTO audit_logs (audit_id, action, status, created_at)
                    VALUES ('%s', 'LOGIN', 'SUCCESS', now())
                    """.formatted(auditId));

            assertThatThrownBy(() -> statement.execute(
                    "UPDATE audit_logs SET username = 'rewritten' WHERE audit_id = '" + auditId + "'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");

            assertThatThrownBy(() -> statement.execute(
                    "DELETE FROM audit_logs WHERE audit_id = '" + auditId + "'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");

            // The single documented escape hatch, used only by AuditRetentionService.
            connection.setAutoCommit(false);
            statement.execute("SET LOCAL audit.maintenance = 'on'");
            statement.execute("DELETE FROM audit_logs WHERE audit_id = '" + auditId + "'");
            connection.commit();
            connection.setAutoCommit(true);

            try (ResultSet rs = statement.executeQuery(
                    "SELECT count(*) FROM audit_logs WHERE audit_id = '" + auditId + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }
}

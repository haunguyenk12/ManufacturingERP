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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migration chain (V1..V53) against an empty Postgres
 * Testcontainer. Uses the Flyway API directly (no ApplicationContext) so this
 * doesn't need to boot Redis/JWT/filter-chain beans unrelated to migration
 * correctness (NEXT_PHASE_PLAN.md D4).
 */
class FlywayMigrationIT extends AbstractPostgresIntegrationTest {

    private static final String BACKFILL_SCHEMA = "v36_backfill_check";
    private static final String IDEMPOTENCY_SCHEMA = "v37_idempotency_check";
    private static final String DOCUMENT_CODE_SCHEMA = "v40_document_code_check";
    private static final String SERIAL_TRACKING_SCHEMA = "v52_serial_tracking_check";

    @Test
    void migrate_onEmptyDatabase_appliesAllMigrationsCleanly() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("53");
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
        // The complete admin-only set after V41: the two "configure the system" permissions
        // docs/roles-and-permissions.md reserves for ADMIN ("MANAGER xem master data nhưng không
        // cấu hình hệ thống"). Adding to this list is a security decision, not a formality.
        Set<String> adminOnlyByDesign = Set.of("PERM_ORG_MANAGE", "PERM_ACCESS_MANAGE");

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
}

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migration chain (V1..V40) against an empty Postgres
 * Testcontainer. Uses the Flyway API directly (no ApplicationContext) so this
 * doesn't need to boot Redis/JWT/filter-chain beans unrelated to migration
 * correctness (NEXT_PHASE_PLAN.md D4).
 */
class FlywayMigrationIT extends AbstractPostgresIntegrationTest {

    private static final String BACKFILL_SCHEMA = "v36_backfill_check";
    private static final String IDEMPOTENCY_SCHEMA = "v37_idempotency_check";
    private static final String DOCUMENT_CODE_SCHEMA = "v40_document_code_check";

    @Test
    void migrate_onEmptyDatabase_appliesAllMigrationsCleanly() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("40");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(Arrays.stream(flyway.info().all()))
                .noneMatch(info -> info.getState() == MigrationState.FAILED);
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

package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.planning.domain.MrpRun;
import com.erp.manufacturing.module.planning.domain.MrpRunStatus;
import com.erp.manufacturing.module.planning.dto.MrpRunResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.MrpRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EH-4: proof that the run row and its {@code FAILED} verdict survive the caller's transaction dying.
 *
 * <p>Rule {@code R7}: a mocked repository cannot show this. It happily reports a {@code save()} that a
 * rollback later throws away — which is exactly what used to happen when an MRP calculation failed on
 * a database error: the {@code FAILED} row was written into the very transaction that was going down,
 * and the operator got a 500 with no record that the run had ever existed. The same lesson
 * {@code WorkOrderBlockRecorder} learned in debt #23.
 *
 * <p>The test drives the boundary explicitly with {@link TestTransaction}: the surrounding
 * transaction is rolled back <em>after</em> the recorder has been called, and the row is then read
 * back in a fresh one.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MrpRunStateRecorder.class, MrpPlanningMapper.class, MrpRunStateRecorderIT.AuditingConfig.class})
class MrpRunStateRecorderIT extends AbstractPostgresIntegrationTest {

    /** {@code @DataJpaTest} does not pick up {@code JpaAuditingConfig}; the NOT NULL audit columns do. */
    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }
    }

    @Autowired
    MrpRunStateRecorder recorder;

    @Autowired
    MrpRunRepository mrpRunRepository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void failedVerdict_survivesTheCallersRollback() {
        Plant plant = persistPlant();
        // The arrangement has to be committed too: everything the recorder does happens in a
        // transaction of its own, which cannot see rows this one has not committed yet.
        commitAndContinue();

        UUID runId = recorder.start(newRun(plant));

        assertThat(mrpRunRepository.findById(runId))
                .get()
                .satisfies(run -> {
                    assertThat(run.getStatus()).isEqualTo(MrpRunStatus.RUNNING);
                    assertThat(run.getStartedAt()).isNotNull();
                    // Assigned by @PrePersist, so a committed row proves the INSERT really ran.
                    assertThat(run.getCode()).startsWith("RUN-");
                });

        MrpRunResponse response = recorder.recordFailure(runId, "MRP calculation failed - see server logs");
        assertThat(response.status()).isEqualTo(MrpRunStatus.FAILED.name());
        assertThat(response.errorMessage()).isEqualTo("MRP calculation failed - see server logs");

        rollbackAndContinue();

        assertThat(mrpRunRepository.findById(runId))
                .as("the FAILED verdict must outlive the transaction that was failing")
                .get()
                .satisfies(run -> {
                    assertThat(run.getStatus()).isEqualTo(MrpRunStatus.FAILED);
                    assertThat(run.getErrorMessage()).isEqualTo("MRP calculation failed - see server logs");
                    assertThat(run.getCompletedAt()).isNotNull();
                });
    }

    /**
     * The other half of the same guarantee: {@code start} has to <em>commit</em>, not merely flush.
     * A flushed-but-uncommitted row is invisible to any other transaction, so the failure recorder
     * would find nothing to mark.
     */
    @Test
    void startedRun_isVisibleToASeparateTransactionImmediately() {
        Plant plant = persistPlant();
        commitAndContinue();

        UUID runId = recorder.start(newRun(plant));

        rollbackAndContinue();

        assertThat(mrpRunRepository.findById(runId)).isPresent();
    }

    private MrpRun newRun(Plant plant) {
        return MrpRun.builder()
                .company(plant.getCompany())
                .plant(plant)
                .horizonStartDate(LocalDate.of(2026, 8, 1))
                .horizonEndDate(LocalDate.of(2026, 8, 31))
                .build();
    }

    private Plant persistPlant() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Company company = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        Company persistedCompany = entityManager.persistFlushFind(company);

        Plant newPlant = Plant.builder().company(persistedCompany).code("PL_" + suffix)
                .name("Plant " + suffix).status(OrganizationStatus.ACTIVE).build();
        newPlant.setCreatedAt(now);
        newPlant.setUpdatedAt(now);
        return entityManager.persistFlushFind(newPlant);
    }

    private void commitAndContinue() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    private void rollbackAndContinue() {
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
    }
}

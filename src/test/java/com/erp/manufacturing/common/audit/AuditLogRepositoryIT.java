package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link AuditLogRepository#search} against a real Postgres Testcontainer (C2-1).
 *
 * <p>Every filter here is a plain equality/range check, not {@code concat}/{@code like}, so this
 * class does not exist to re-prove the {@code lower(bytea)} class of bug (rule R7, `CLAUDE.md §0.24`)
 * — it exists because a 6-filter dynamic JPQL query is exactly the shape that class of bug hides in,
 * and a mock repository cannot tell a correctly-scoped query from one that silently ignores a filter.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditLogRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    AuditLogRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void search_withoutFilters_returnsEveryEntry() {
        persist(UUID.randomUUID(), "LOGIN", null, null, null, null);
        persist(UUID.randomUUID(), "LOGOUT", null, null, null, null);

        List<AuditLog> found = repository.search(
                null, null, null, null, null, null, null, null, PageRequest.of(0, 20)).getContent();

        assertThat(found).hasSize(2);
    }

    @Test
    void search_filtersByActorUserId() {
        UUID wantedUser = UUID.randomUUID();
        AuditLog wanted = persist(wantedUser, "LOGIN", null, null, null, null);
        persist(UUID.randomUUID(), "LOGIN", null, null, null, null);

        List<AuditLog> found = repository.search(
                wantedUser, null, null, null, null, null, null, null, PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(AuditLog::getAuditId).containsExactly(wanted.getAuditId());
    }

    @Test
    void search_filtersByEntityTypeAndEntityId() {
        AuditLog wanted = persistWithEntity("WorkOrder", "wo-1");
        persistWithEntity("WorkOrder", "wo-2");
        persistWithEntity("BomHeader", "wo-1");

        List<AuditLog> found = repository.search(
                null, "WorkOrder", "wo-1", null, null, null, null, null, PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(AuditLog::getAuditId).containsExactly(wanted.getAuditId());
    }

    @Test
    void search_filtersByAction() {
        AuditLog wanted = persist(UUID.randomUUID(), "WORK_ORDER_CREATED", null, null, null, null);
        persist(UUID.randomUUID(), "WORK_ORDER_CANCELLED", null, null, null, null);

        List<AuditLog> found = repository.search(
                null, null, null, "WORK_ORDER_CREATED", null, null, null, null, PageRequest.of(0, 20))
                .getContent();

        assertThat(found).extracting(AuditLog::getAuditId).containsExactly(wanted.getAuditId());
    }

    @Test
    void search_filtersByPlantId() {
        UUID wantedPlant = persistPlant().getPlantId();
        UUID otherPlant = persistPlant().getPlantId();
        AuditLog wanted = persist(UUID.randomUUID(), "LOGIN", null, null, wantedPlant, null);
        persist(UUID.randomUUID(), "LOGIN", null, null, otherPlant, null);
        persist(UUID.randomUUID(), "LOGIN", null, null, null, null);

        List<AuditLog> found = repository.search(
                null, null, null, null, wantedPlant, null, null, null, PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(AuditLog::getAuditId).containsExactly(wanted.getAuditId());
    }

    @Test
    void search_filtersByTraceId() {
        AuditLog wanted = persist(UUID.randomUUID(), "LOGIN", null, null, null, "trace-abc");
        persist(UUID.randomUUID(), "LOGIN", null, null, null, "trace-xyz");

        List<AuditLog> found = repository.search(
                null, null, null, null, null, "trace-abc", null, null, PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(AuditLog::getAuditId).containsExactly(wanted.getAuditId());
    }

    @Test
    void search_filtersByCreatedAtRange() {
        Instant now = Instant.now();
        AuditLog tooOld = persistAt(now.minus(10, ChronoUnit.DAYS));
        AuditLog inRange = persistAt(now.minus(1, ChronoUnit.DAYS));
        AuditLog tooNew = persistAt(now.plus(10, ChronoUnit.DAYS));

        List<AuditLog> found = repository.search(
                null, null, null, null, null, null,
                now.minus(2, ChronoUnit.DAYS), now, PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(AuditLog::getAuditId).containsExactly(inRange.getAuditId());
        assertThat(found).extracting(AuditLog::getAuditId)
                .doesNotContain(tooOld.getAuditId(), tooNew.getAuditId());
    }

    @Test
    void search_combinesMultipleFiltersWithAnd() {
        UUID wantedUser = UUID.randomUUID();
        AuditLog wanted = persist(wantedUser, "WORK_ORDER_CREATED", "WorkOrder", "wo-1", null, null);
        // Same user, different action — must be excluded once action is added to the filter set.
        persist(wantedUser, "WORK_ORDER_CANCELLED", "WorkOrder", "wo-1", null, null);

        List<AuditLog> found = repository.search(
                wantedUser, "WorkOrder", "wo-1", "WORK_ORDER_CREATED", null, null, null, null,
                PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(AuditLog::getAuditId).containsExactly(wanted.getAuditId());
    }

    private Plant persistPlant() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Company company = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        company = entityManager.persistFlushFind(company);

        Plant plant = Plant.builder().company(company).code("PL_" + suffix).name("Plant " + suffix)
                .timezone("UTC").status(OrganizationStatus.ACTIVE).build();
        plant.setCreatedAt(now);
        plant.setUpdatedAt(now);
        return entityManager.persistFlushFind(plant);
    }

    private AuditLog persist(UUID userId, String action, String entityType, String entityId,
                             UUID plantId, String traceId) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .plantId(plantId)
                .traceId(traceId)
                .status("SUCCESS")
                .createdAt(Instant.now())
                .build();
        return entityManager.persistFlushFind(auditLog);
    }

    private AuditLog persistWithEntity(String entityType, String entityId) {
        return persist(UUID.randomUUID(), "WORK_ORDER_UPDATED", entityType, entityId, null, null);
    }

    private AuditLog persistAt(Instant createdAt) {
        AuditLog auditLog = AuditLog.builder()
                .action("LOGIN")
                .status("SUCCESS")
                .createdAt(createdAt)
                .build();
        return entityManager.persistFlushFind(auditLog);
    }
}

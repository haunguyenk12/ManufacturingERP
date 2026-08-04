package com.erp.manufacturing.module.uom.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.domain.UomStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link UomRepository#search} against a real Postgres Testcontainer.
 *
 * <p>Exists for the exact reason {@code SupplierRepositoryIT} and the {@code WorkOrderRepository}
 * bugfix of 2026-08-04 exist (rule {@code R7}, {@code CLAUDE.md §0.24}): a mocked repository stays
 * green when {@code cast(:keyword as string)} is removed from the query, because Mockito never talks
 * to Postgres. Only a real database can prove {@code GET /uoms} (no search term — the common call)
 * does not 500 with {@code function lower(bytea) does not exist}.
 *
 * <p>{@code search_matchesTheCodeCaseInsensitively} seeds a UOM that must <em>not</em> match alongside
 * the one that must, so a query that returned everything could not pass as correct filtering.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UomRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    UomRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void search_withoutAKeywordReturnsEveryUom() {
        persistUom("KG", "Kilogram", UomStatus.ACTIVE);
        persistUom("PCS", "Pieces", UomStatus.ACTIVE);

        List<Uom> found = repository.search(null, null, PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(Uom::getCode).containsExactlyInAnyOrder("KG", "PCS");
    }

    @Test
    void search_matchesTheCodeCaseInsensitively() {
        Uom wanted = persistUom("KG", "Nothing in the name", UomStatus.ACTIVE);
        persistUom("PCS", "Pieces", UomStatus.ACTIVE);

        List<Uom> found = repository.search(null, "kg", PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(Uom::getUomId).containsExactly(wanted.getUomId());
    }

    /** The keyword box covers both columns; matching only the code would half-answer it. */
    @Test
    void search_alsoMatchesTheName() {
        Uom wanted = persistUom("KG", "Kilogram", UomStatus.ACTIVE);
        persistUom("PCS", "Pieces", UomStatus.ACTIVE);

        List<Uom> found = repository.search(null, "kilo", PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(Uom::getUomId).containsExactly(wanted.getUomId());
    }

    @Test
    void search_withoutAKeywordStillHonoursTheStatusFilter() {
        Uom active = persistUom("KG", "Kilogram", UomStatus.ACTIVE);
        persistUom("PCS", "Pieces", UomStatus.INACTIVE);

        List<Uom> found = repository.search(UomStatus.ACTIVE, null, PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(Uom::getUomId).containsExactly(active.getUomId());
    }

    private Uom persistUom(String code, String name, UomStatus status) {
        Instant now = Instant.now();
        Uom uom = Uom.builder().code(code).name(name).status(status).build();
        uom.setCreatedAt(now);
        uom.setUpdatedAt(now);
        return entityManager.persistFlushFind(uom);
    }
}

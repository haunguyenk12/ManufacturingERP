package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link ItemRepository#search} on a real Postgres (G2-4).
 *
 * <p>The query mixes a nullable {@code String} in {@code concat}/{@code like}, two nullable enums and a
 * nullable {@code Boolean} under {@code is null}. Each of those is proven with {@code null} and with a
 * value, because a nullable parameter Postgres cannot type fails the whole statement (CLAUDE.md §0.24,
 * §0.36) — invisible to a mocked repository.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ItemRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired ItemRepository repository;
    @Autowired TestEntityManager entityManager;

    private Company company;
    private Item rawLot;
    private Item rawPlain;
    private Item finishedInactive;

    @BeforeEach
    void setUp() {
        company = persistCompany();
        rawLot = persistItem(company, "RM-COIL", "Steel Coil", ItemType.RAW_MATERIAL, true, ItemStatus.ACTIVE);
        rawPlain = persistItem(company, "RM-BOLT", "Bolt", ItemType.RAW_MATERIAL, false, ItemStatus.ACTIVE);
        finishedInactive = persistItem(company, "FG-BIKE", "Bike Coil Edition", ItemType.FINISHED_GOOD, false,
                ItemStatus.INACTIVE);
        persistItem(persistCompany(), "RM-COIL", "Other company coil", ItemType.RAW_MATERIAL, true, ItemStatus.ACTIVE);
    }

    @Test
    void search_withEveryFilterNull_returnsTheWholeCompanyOnly() {
        assertThat(ids(repository.search(company.getCompanyId(), null, null, null, null, page()).getContent()))
                .containsExactlyInAnyOrder(rawLot.getItemId(), rawPlain.getItemId(), finishedInactive.getItemId());
    }

    @Test
    void search_freeTextMatchesCodeOrNameCaseInsensitively() {
        assertThat(ids(repository.search(company.getCompanyId(), "COIL", null, null, null, page()).getContent()))
                .containsExactlyInAnyOrder(rawLot.getItemId(), finishedInactive.getItemId());
    }

    @Test
    void search_byStatus() {
        assertThat(ids(repository.search(company.getCompanyId(), null, ItemStatus.INACTIVE, null, null, page())
                .getContent())).containsExactly(finishedInactive.getItemId());
    }

    @Test
    void search_byType() {
        assertThat(ids(repository.search(company.getCompanyId(), null, null, ItemType.RAW_MATERIAL, null, page())
                .getContent())).containsExactlyInAnyOrder(rawLot.getItemId(), rawPlain.getItemId());
    }

    /** Boolean filter both ways — false must not be treated as "no filter". */
    @Test
    void search_byLotTrackedTrueAndFalse() {
        assertThat(ids(repository.search(company.getCompanyId(), null, null, null, true, page()).getContent()))
                .containsExactly(rawLot.getItemId());
        assertThat(ids(repository.search(company.getCompanyId(), null, null, null, false, page()).getContent()))
                .containsExactlyInAnyOrder(rawPlain.getItemId(), finishedInactive.getItemId());
    }

    @Test
    void search_combinesEveryFilter() {
        assertThat(ids(repository.search(company.getCompanyId(), "coil", ItemStatus.ACTIVE, ItemType.RAW_MATERIAL,
                true, page()).getContent())).containsExactly(rawLot.getItemId());
    }

    /** Filter applies before pagination: totals reflect the filter, not the company. */
    @Test
    void search_totalElementsReflectsTheFilter() {
        assertThat(repository.search(company.getCompanyId(), null, null, ItemType.RAW_MATERIAL, null,
                PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
    }

    private static PageRequest page() {
        return PageRequest.of(0, 50);
    }

    private static List<UUID> ids(List<Item> items) {
        return items.stream().map(Item::getItemId).toList();
    }

    private Company persistCompany() {
        String suffix = UUID.randomUUID().toString();
        Company created = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        stamp(created);
        return entityManager.persistFlushFind(created);
    }

    private Item persistItem(Company owner, String code, String name, ItemType type, boolean lotTracked,
                             ItemStatus status) {
        Item item = Item.builder().company(owner).code(code).name(name).type(type).unit("EA")
                .lotTracked(lotTracked).status(status).build();
        stamp(item);
        return entityManager.persistFlushFind(item);
    }

    private void stamp(BaseEntity entity) {
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
    }
}

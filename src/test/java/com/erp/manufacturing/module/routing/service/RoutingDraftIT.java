package com.erp.manufacturing.module.routing.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.dto.RoutingDraftOperationRequest;
import com.erp.manufacturing.module.routing.dto.RoutingDraftSaveRequest;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import com.erp.manufacturing.module.routing.mapper.RoutingMapper;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.repository.WorkCenterRepository;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G5 routing graph against real Postgres (rule R7): predecessor rows in their join table, a re-save
 * that swaps sequences under {@code uk_routing_operations_sequence} and drops an operation that was
 * someone's predecessor, and the JPQL filters the plant/work-center views rely on.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RoutingService.class, RoutingMapper.class, ItemLookupService.class, WorkCenterLookupService.class,
        RoutingDraftIT.AuditingConfig.class})
class RoutingDraftIT extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }
    }

    @Autowired RoutingService service;
    @Autowired RoutingHeaderRepository routingHeaderRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WorkCenterRepository workCenterRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void saveDraft_reSaveSwappingSequencesAndDroppingAPredecessor_keepsIdsAndRewritesTheLinks() {
        Fixture f = fixture();
        RoutingResponse created = service.saveDraft(f.company.getCompanyId(), new RoutingDraftSaveRequest(null, null,
                "RT-" + f.tag, "Demo", f.product.getItemId(), null, null, null, List.of(
                op(null, "cut", 10, f.cut, List.of()),
                op(null, "weld", 20, f.weld, List.of("cut")),
                op(null, "pack", 30, f.cut, List.of("cut", "weld")))));
        UUID cutId = created.operations().get(0).routingOperationId();
        UUID weldId = created.operations().get(1).routingOperationId();
        assertThat(created.operations().get(2).predecessorOperationIds()).containsExactly(cutId, weldId);

        // weld moves to 10, cut to 20 and now waits for weld; pack — cut's dependant — is dropped.
        RoutingResponse replaced = service.saveDraft(f.company.getCompanyId(), new RoutingDraftSaveRequest(
                created.routingId(), created.lockVersion(), "RT-" + f.tag, "Demo", f.product.getItemId(), null, null,
                null, List.of(
                op(weldId, "weld", 10, f.weld, List.of()),
                op(cutId, "cut", 20, f.cut, List.of("weld")))));

        assertThat(replaced.operations()).extracting(operation -> operation.routingOperationId())
                .containsExactly(weldId, cutId);
        assertThat(replaced.operations().get(1).predecessorOperationIds()).containsExactly(weldId);
        assertThat(replaced.lockVersion()).isEqualTo(created.lockVersion() + 1);

        Map<UUID, List<UUID>> stored = new TransactionTemplate(transactionManager).execute(status ->
                routingHeaderRepository.findWithOperationsByRoutingId(created.routingId()).orElseThrow()
                        .getOperations().stream().collect(Collectors.toMap(RoutingOperation::getRoutingOperationId,
                                operation -> operation.getPredecessors().stream()
                                        .map(RoutingOperation::getRoutingOperationId).toList())));
        assertThat(stored).containsOnlyKeys(weldId, cutId);
        assertThat(stored.get(cutId)).containsExactly(weldId);
        assertThat(stored.get(weldId)).isEmpty();
    }

    @Test
    void search_byPlantAndWorkCenter_withANullSearch() {
        Fixture f = fixture();
        UUID routingId = service.saveDraft(f.company.getCompanyId(), new RoutingDraftSaveRequest(null, null,
                "RT-" + f.tag, null, f.product.getItemId(), null, null, null,
                List.of(op(null, "cut", 10, f.cut, List.of())))).routingId();

        assertThat(ids(f, f.plant.getPlantId(), null, null)).containsExactly(routingId);
        assertThat(ids(f, UUID.randomUUID(), null, null)).isEmpty();
        assertThat(ids(f, null, f.weld.getWorkCenterId(), null)).isEmpty();
        assertThat(ids(f, null, f.cut.getWorkCenterId(), null)).containsExactly(routingId);
        assertThat(ids(f, null, null, "rt-" + f.tag.toLowerCase())).containsExactly(routingId);
        assertThat(ids(f, null, null, null)).containsExactly(routingId);
    }

    private List<UUID> ids(Fixture f, UUID plantId, UUID workCenterId, String search) {
        List<UUID> found = new TransactionTemplate(transactionManager).execute(status -> routingHeaderRepository
                .search(f.company.getCompanyId(), null, null, plantId, workCenterId, search, PageRequest.of(0, 10))
                .map(RoutingHeader::getRoutingId).getContent());
        return found;
    }

    /** FE Q9: only an ACTIVE routing pins a work center; a draft does not. */
    @Test
    void existsActiveRoutingUsingWorkCenter_countsOnlyActiveRoutings() {
        Fixture f = fixture();
        UUID routingId = service.saveDraft(f.company.getCompanyId(), new RoutingDraftSaveRequest(null, null,
                "RT-" + f.tag, null, f.product.getItemId(), null, null, null,
                List.of(op(null, "cut", 10, f.cut, List.of())))).routingId();

        assertThat(routingHeaderRepository.existsActiveRoutingUsingWorkCenter(f.cut.getWorkCenterId())).isFalse();
        service.activate(routingId, null);
        assertThat(routingHeaderRepository.existsActiveRoutingUsingWorkCenter(f.cut.getWorkCenterId())).isTrue();
        assertThat(routingHeaderRepository.existsActiveRoutingUsingWorkCenter(f.weld.getWorkCenterId())).isFalse();
    }

    private RoutingDraftOperationRequest op(UUID id, String key, int sequence, WorkCenter workCenter,
                                            List<String> predecessors) {
        return new RoutingDraftOperationRequest(id, key, sequence, "Op " + key, workCenter.getWorkCenterId(),
                new BigDecimal("10"), new BigDecimal("1.5"), predecessors);
    }

    private record Fixture(String tag, Company company, Plant plant, Item product, WorkCenter cut, WorkCenter weld) {}

    private Fixture fixture() {
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Company company = companyRepository.save(Company.builder().code("G5R-" + tag).name("G5 " + tag)
                .status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("PL-" + tag).name("Plant " + tag)
                .timezone("Asia/Ho_Chi_Minh").status(OrganizationStatus.ACTIVE).build());
        Item product = itemRepository.save(Item.builder().company(company).code("FG-" + tag).name("FG")
                .type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).lotTracked(false).build());
        return new Fixture(tag, company, plant, product, workCenter(plant, "CUT-" + tag), workCenter(plant, "WELD-" + tag));
    }

    private WorkCenter workCenter(Plant plant, String code) {
        return workCenterRepository.save(WorkCenter.builder().plant(plant).code(code).name(code)
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1).status(OrganizationStatus.ACTIVE).build());
    }
}

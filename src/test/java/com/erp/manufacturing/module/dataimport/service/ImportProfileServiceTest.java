package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.module.dataimport.domain.ImportProfile;
import com.erp.manufacturing.module.dataimport.domain.ImportProfileStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.ColumnMappingRequest;
import com.erp.manufacturing.module.dataimport.dto.ImportProfileCreateRequest;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportProfileRepository;
import com.erp.manufacturing.module.dataimport.target.ItemImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.ItemService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportProfileServiceTest {

    @Mock ImportProfileRepository profileRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemService itemService;
    @Mock ItemLookupService itemLookupService;

    private ImportProfileService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        var registry = new ImportTargetRegistry(List.<ImportTargetHandler>of(
                new ItemImportTargetHandler(itemService, itemLookupService)));
        service = new ImportProfileService(
                profileRepository, organizationLookupService, registry, new DataImportMapper());
        companyId = UUID.randomUUID();
    }

    @Test
    void create_validatesAndNormalisesBeforeSaving() {
        when(organizationLookupService.getActiveCompany(companyId)).thenReturn(company(companyId));
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(request(" customer item ", validMappings()));

        assertThat(response.code()).isEqualTo("CUSTOMER ITEM");
        ArgumentCaptor<ImportProfile> saved = ArgumentCaptor.forClass(ImportProfile.class);
        verify(profileRepository).save(saved.capture());
        assertThat(saved.getValue().getHeaderRowIndex()).isZero();
        assertThat(saved.getValue().getFirstDataRowIndex()).isEqualTo(1);
        assertThat(saved.getValue().getStatus()).isEqualTo(ImportProfileStatus.ACTIVE);
    }

    @Test
    void create_rejectsUnknownTargetFieldBeforeTouchingCompany() {
        List<ColumnMappingRequest> mappings = new ArrayList<>(validMappings());
        mappings.set(0, mapping("Mã", "notAField", "TRIM"));

        assertThatThrownBy(() -> service.create(request("ITEM", mappings)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Unknown target field");
        verify(organizationLookupService, never()).getActiveCompany(any());
    }

    @Test
    void create_rejectsMissingRequiredTargetField() {
        List<ColumnMappingRequest> mappings = validMappings().stream()
                .filter(mapping -> !mapping.targetField().equals("unit"))
                .toList();

        assertThatThrownBy(() -> service.create(request("ITEM", mappings)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Required target fields are not mapped")
                .hasMessageContaining("unit");
    }

    @Test
    void create_rejectsTransformThatDoesNotMatchFieldType() {
        List<ColumnMappingRequest> mappings = new ArrayList<>(validMappings());
        mappings.set(0, mapping("Mã", "code", "BOOLEAN_VN"));

        assertThatThrownBy(() -> service.create(request("ITEM", mappings)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("BOOLEAN_VN is not compatible with STRING field 'code'");
    }

    @Test
    void create_rejectsDataRowAtOrBeforeHeader() {
        var base = request("ITEM", validMappings());
        var invalid = new ImportProfileCreateRequest(base.code(), base.name(), base.targetType(),
                base.companyId(), base.sheetName(), 2, 2, base.mappings());

        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("firstDataRowIndex must be greater");
    }

    private ImportProfileCreateRequest request(String code, List<ColumnMappingRequest> mappings) {
        return new ImportProfileCreateRequest(code, "Customer Item", ImportTargetType.ITEM,
                companyId, null, null, null, mappings);
    }

    private List<ColumnMappingRequest> validMappings() {
        return List.of(
                mapping("Mã vật tư", "code", "TRIM", "UPPER"),
                mapping("Tên hàng", "name", "TRIM"),
                mapping("Loại", "type", "TRIM", "UPPER"),
                mapping("ĐVT", "unit", "TRIM", "UPPER"),
                mapping("Theo dõi lô", "lotTracked", "TRIM", "BOOLEAN_VN"),
                mapping("Theo dõi serial", "serialTracked", "TRIM", "BOOLEAN_VN"));
    }

    private ColumnMappingRequest mapping(String source, String target, String... transforms) {
        return new ColumnMappingRequest(source, target, List.of(transforms), null);
    }

    private Company company(UUID id) {
        return Company.builder().companyId(id).code("CO").name("Company")
                .status(OrganizationStatus.ACTIVE).build();
    }
}

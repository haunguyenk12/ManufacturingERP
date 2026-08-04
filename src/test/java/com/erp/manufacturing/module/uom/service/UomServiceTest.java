package com.erp.manufacturing.module.uom.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.domain.UomStatus;
import com.erp.manufacturing.module.uom.dto.UomCreateRequest;
import com.erp.manufacturing.module.uom.dto.UomUpdateRequest;
import com.erp.manufacturing.module.uom.mapper.UomMapper;
import com.erp.manufacturing.module.uom.repository.UomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UomService")
class UomServiceTest {

    private final UomRepository repository = mock(UomRepository.class);
    private final UomService service = new UomService(repository, new UomMapper());

    private static final UUID UOM_ID = UUID.randomUUID();

    @BeforeEach
    void stubSave() {
        when(repository.save(any(Uom.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("create: normalizes the code to upper case and persists it")
    void create_normalizesCodeToUpperCase() {
        when(repository.existsByCode("KG")).thenReturn(false);

        var response = service.create(new UomCreateRequest("kg", "Kilogram", "Base mass unit"));

        assertThat(response.code()).isEqualTo("KG");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("create: duplicate code throws RESOURCE_ALREADY_EXISTS before any save")
    void create_duplicateCode_throwsBeforeSaving() {
        when(repository.existsByCode("KG")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new UomCreateRequest("kg", "Kilogram", null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update: code is not settable — UomUpdateRequest has no code component")
    void update_hasNoCodeField() {
        // Compile-time guarantee, not a runtime check: documents the intent so a future edit that
        // adds a code() component to UomUpdateRequest is a visible, deliberate change.
        assertThat(UomUpdateRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("name", "description")
                .doesNotContain("code");
    }

    @Test
    @DisplayName("update: only touches fields that are non-null in the request")
    void update_leavesUnspecifiedFieldsUnchanged() {
        Uom existing = Uom.builder().uomId(UOM_ID).code("KG").name("Kilogram").description("old").build();
        when(repository.findById(UOM_ID)).thenReturn(Optional.of(existing));

        var response = service.update(UOM_ID, new UomUpdateRequest(null, "new description"));

        assertThat(response.name()).isEqualTo("Kilogram");
        assertThat(response.description()).isEqualTo("new description");
    }

    @Test
    @DisplayName("activate: is a no-op 200 when the UOM is already ACTIVE")
    void activate_alreadyActive_isNoOp() {
        Uom existing = Uom.builder().uomId(UOM_ID).code("KG").name("Kilogram").status(UomStatus.ACTIVE).build();
        when(repository.findById(UOM_ID)).thenReturn(Optional.of(existing));

        var response = service.activate(UOM_ID);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("deactivate then activate: round-trips status without error")
    void deactivateThenActivate_roundTrips() {
        Uom existing = Uom.builder().uomId(UOM_ID).code("KG").name("Kilogram").status(UomStatus.ACTIVE).build();
        when(repository.findById(UOM_ID)).thenReturn(Optional.of(existing));

        assertThat(service.deactivate(UOM_ID).status()).isEqualTo("INACTIVE");
        assertThat(service.activate(UOM_ID).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("get: unknown id throws RESOURCE_NOT_FOUND")
    void get_unknownId_throwsResourceNotFound() {
        when(repository.findById(UOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(UOM_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("list: passes the status and trimmed keyword straight through to the repository")
    void list_passesFiltersThrough() {
        when(repository.search(eq(UomStatus.ACTIVE), eq("kg"), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(UomStatus.ACTIVE, "  kg  ", org.springframework.data.domain.PageRequest.of(0, 20));

        verify(repository).search(eq(UomStatus.ACTIVE), eq("kg"), any());
    }
}

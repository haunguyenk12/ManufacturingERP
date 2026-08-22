package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.dto.ItemCreateRequest;
import com.erp.manufacturing.module.inventory.dto.ItemResponse;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.ItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemImportTargetHandlerTest {

    @Mock ItemService itemService;
    @Mock ItemLookupService itemLookupService;

    private ItemImportTargetHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ItemImportTargetHandler(itemService, itemLookupService);
    }

    @Test
    void descriptor_staysAlignedWithItemCreateRequest() {
        assertThat(handler.descriptor().fields()).extracting(ImportFieldDescriptor::name)
                .containsExactly("code", "name", "type", "unit", "lotTracked", "serialTracked");
    }

    @Test
    void validateBatch_usesOneLookupAndReportsDuplicateExistingPatternAndTrackingRule() {
        UUID companyId = UUID.randomUUID();
        Item existing = Item.builder().code("EXISTING").build();
        when(itemLookupService.findItemsByCode(eq(companyId), any()))
                .thenReturn(Map.of("EXISTING", existing));
        List<Map<String, String>> rows = List.of(
                row("new-1", "false", "false"),
                row("NEW-1", "false", "false"),
                row("EXISTING", "false", "false"),
                row("bad code", "false", "false"),
                row("TRACK", "true", "true"));

        var errors = handler.validateBatch(companyId, rows);

        assertThat(errors.get(1)).extracting(error -> error.code())
                .containsExactly(ImportErrorCode.DUPLICATE_IN_FILE);
        assertThat(errors.get(2)).extracting(error -> error.code())
                .containsExactly(ImportErrorCode.ALREADY_EXISTS);
        assertThat(errors.get(3)).extracting(error -> error.code())
                .containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(errors.get(4)).extracting(error -> error.code())
                .containsExactly(ImportErrorCode.RULE_VIOLATION);
        verify(itemLookupService).findItemsByCode(eq(companyId), any());
    }

    @Test
    void apply_goesThroughItemServiceAndConvertsTypes() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(itemService.createItem(eq(companyId), any())).thenReturn(new ItemResponse(
                itemId, companyId, "VT-001", "Khung", "RAW_MATERIAL", "CAI",
                true, false, "ACTIVE", null, null));

        UUID result = handler.apply(companyId, Map.of(
                "code", " vt-001 ", "name", "Khung", "type", "RAW_MATERIAL", "unit", "CAI",
                "lotTracked", "true", "serialTracked", "false"));

        assertThat(result).isEqualTo(itemId);
        ArgumentCaptor<ItemCreateRequest> request = ArgumentCaptor.forClass(ItemCreateRequest.class);
        verify(itemService).createItem(eq(companyId), request.capture());
        assertThat(request.getValue().code()).isEqualTo("VT-001");
        assertThat(request.getValue().lotTracked()).isTrue();
    }

    private Map<String, String> row(String code, String lot, String serial) {
        return Map.of("code", code, "name", "Name", "type", "RAW_MATERIAL", "unit", "CAI",
                "lotTracked", lot, "serialTracked", serial);
    }
}

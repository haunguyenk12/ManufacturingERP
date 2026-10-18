package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.dto.BomDraftSaveRequest;
import com.erp.manufacturing.module.bom.dto.BomResponse;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.bom.service.BomService;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.service.FixedTemplateValidator;
import com.erp.manufacturing.module.dataimport.service.FixedTemplateValidator.RowInput;
import com.erp.manufacturing.module.dataimport.service.FixedTemplateValidator.RowResult;
import com.erp.manufacturing.module.dataimport.spreadsheet.FixedTemplateReader;
import com.erp.manufacturing.module.dataimport.spreadsheet.FixedTemplateWorkbook;
import com.erp.manufacturing.module.dataimport.spreadsheet.TemplateWorkbooks;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G5 BOM import: the {@code omniplant-bom-v1} reader, the group rules and the commit through
 * {@code BomService.saveDraft}. Validation runs through the real {@link FixedTemplateValidator} so the
 * field rules (decimal shape, blank percent) are part of what is tested.
 */
@DisplayName("BOM import (omniplant-bom-v1)")
class BomImportHandlerTest {

    private final UUID companyId = UUID.randomUUID();
    private final ItemLookupService itemLookupService = mock(ItemLookupService.class);
    private final BomLookupService bomLookupService = mock(BomLookupService.class);
    private final BomService bomService = mock(BomService.class);
    private final BomImportHandler handler = new BomImportHandler(itemLookupService, bomLookupService, bomService);
    private final FixedTemplateValidator validator =
            new FixedTemplateValidator(new ImportTargetRegistry(List.of()), handler,
                    mock(SalesOrderImportHandler.class));
    private final Map<String, Item> items = new HashMap<>();

    @BeforeEach
    void setUp() {
        item("FG-DEMO", ItemType.FINISHED_GOOD, "PCS");
        item("FG-TWO", ItemType.FINISHED_GOOD, "PCS");
        item("RM-DEMO", ItemType.RAW_MATERIAL, "KG");
        item("RM-TWO", ItemType.RAW_MATERIAL, "KG");
        when(itemLookupService.findItemsByCode(eq(companyId), anyCollection())).thenAnswer(invocation -> {
            Map<String, Item> found = new HashMap<>();
            for (Object code : (java.util.Collection<?>) invocation.getArgument(1)) {
                if (items.containsKey(code)) {
                    found.put((String) code, items.get(code));
                }
            }
            return found;
        });
        when(bomLookupService.findTakenBomCodes(eq(companyId), anyCollection())).thenReturn(Set.of());
        when(bomLookupService.findItemIdsWithAnyBom(eq(companyId), anyCollection())).thenReturn(Set.of());
    }

    @Test
    void reader_bomTemplateVi_isReadFromTheBomSheetWithNumbersByValue() {
        byte[] file = TemplateWorkbooks.boms("vi")
                .row(2, "bom-demo-01", "fg-demo", null, "pcs", "rm-demo", null, "kg", null, "BOM minh họa")
                .numberCell(2, 2, 10).numberCell(2, 5, 25).numberCell(2, 7, 5)
                .build();

        FixedTemplateWorkbook workbook = new FixedTemplateReader().read(file, DataImportKind.BOMS, 500);

        assertThat(workbook.templateVersion()).isEqualTo("omniplant-bom-v1");
        assertThat(workbook.rows().get(0).values())
                .containsEntry("bomCode", "BOM-DEMO-01")
                .containsEntry("productCode", "FG-DEMO")
                .containsEntry("outputQuantity", "10")
                .containsEntry("outputUom", "PCS")
                .containsEntry("componentQuantity", "25")
                .containsEntry("scrapPercent", "5");
        assertThat(workbook.rows().get(0).issues()).isEmpty();
    }

    @Test
    void reader_itemsTemplateUploadedAsBom_isATemplateError() {
        byte[] file = TemplateWorkbooks.items("vi").row(2, "X", "X", "Thành phẩm", "PCS", "Không").build();

        assertThatThrownBy(() -> new FixedTemplateReader().read(file, DataImportKind.BOMS, 500))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.TEMPLATE));
    }

    /** FE A11: one group of two components is valid; blank scrap reads as 0. */
    @Test
    void validate_oneGroupTwoComponents_isValid() {
        List<RowResult> results = validate(
                row(2, "BOM-DEMO-01", "FG-DEMO", "10", "PCS", "RM-DEMO", "25", "KG", "5", "Notes"),
                row(3, "BOM-DEMO-01", "FG-DEMO", "10", "PCS", "RM-TWO", "5", "KG", "", "Notes"));

        assertThat(results).allMatch(RowResult::valid);
        assertThat(results.get(1).values()).containsEntry("scrapPercent", "0");
    }

    /** FE A13: a row that disagrees on the output quantity fails, and the whole BOM with it. */
    @Test
    void validate_groupMismatch_rejectsEveryRowOfTheGroup() {
        List<RowResult> results = validate(
                row(2, "BOM-DEMO-01", "FG-DEMO", "10", "PCS", "RM-DEMO", "25", "KG", "", ""),
                row(3, "BOM-DEMO-01", "FG-DEMO", "20", "PCS", "RM-TWO", "5", "KG", "", ""));

        assertThat(codes(results.get(1), "outputQuantity")).containsExactly(ImportErrorCode.GROUP_MISMATCH);
        assertThat(results.get(0).valid()).isFalse();
        assertThat(codes(results.get(0), "bomCode")).containsExactly(ImportErrorCode.RULE_VIOLATION);
    }

    @Test
    void validate_duplicateComponentInTheGroup_flagsBothRows() {
        List<RowResult> results = validate(
                row(2, "BOM-1", "FG-DEMO", "1", "PCS", "RM-DEMO", "1", "KG", "", ""),
                row(3, "BOM-1", "FG-DEMO", "1", "PCS", "RM-DEMO", "2", "KG", "", ""));

        assertThat(codes(results.get(0), "componentCode")).containsExactly(ImportErrorCode.DUPLICATE_IN_FILE);
        assertThat(codes(results.get(1), "componentCode")).containsExactly(ImportErrorCode.DUPLICATE_IN_FILE);
    }

    /** FE A14: one product, one BOM per file. */
    @Test
    void validate_sameProductUnderTwoCodes_flagsBothGroups() {
        List<RowResult> results = validate(
                row(2, "BOM-A", "FG-DEMO", "1", "PCS", "RM-DEMO", "1", "KG", "", ""),
                row(3, "BOM-B", "FG-DEMO", "1", "PCS", "RM-DEMO", "1", "KG", "", ""));

        assertThat(codes(results.get(0), "productCode")).containsExactly(ImportErrorCode.DUPLICATE_IN_FILE);
        assertThat(codes(results.get(1), "productCode")).containsExactly(ImportErrorCode.DUPLICATE_IN_FILE);
    }

    /** FE A14: create-only — a product with any BOM already, or a taken code, is refused. */
    @Test
    void validate_productWithABomAndTakenCode_areRefused() {
        when(bomLookupService.findItemIdsWithAnyBom(eq(companyId), anyCollection()))
                .thenReturn(Set.of(items.get("FG-DEMO").getItemId()));
        when(bomLookupService.findTakenBomCodes(eq(companyId), anyCollection())).thenReturn(Set.of("BOM-TWO"));

        List<RowResult> results = validate(
                row(2, "BOM-ONE", "FG-DEMO", "1", "PCS", "RM-DEMO", "1", "KG", "", ""),
                row(3, "BOM-TWO", "FG-TWO", "1", "PCS", "RM-DEMO", "1", "KG", "", ""));

        assertThat(codes(results.get(0), "productCode")).containsExactly(ImportErrorCode.ALREADY_EXISTS);
        assertThat(codes(results.get(1), "bomCode")).containsExactly(ImportErrorCode.ALREADY_EXISTS);
    }

    /** FE A03/§3.4: units are not converted — the item's own unit only. */
    @Test
    void validate_unitThatIsNotTheItemsOwn_isRefused() {
        List<RowResult> results = validate(
                row(2, "BOM-1", "FG-DEMO", "1", "EA", "RM-DEMO", "1", "G", "", ""));

        assertThat(codes(results.get(0), "outputUom")).containsExactly(ImportErrorCode.RULE_VIOLATION);
        assertThat(codes(results.get(0), "componentUom")).containsExactly(ImportErrorCode.RULE_VIOLATION);
    }

    @Test
    void validate_decimalShapeAndRanges() {
        List<RowResult> results = validate(
                row(2, "BOM-1", "FG-DEMO", "1e3", "PCS", "RM-DEMO", "0", "KG", "100", ""),
                row(3, "BOM-2", "FG-TWO", "3", "PCS", "RM-DEMO", "1", "KG", "0.00001", ""));

        assertThat(codes(results.get(0), "outputQuantity")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(codes(results.get(0), "componentQuantity")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(codes(results.get(0), "scrapPercent")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        // 1 / 3 does not fit 6 decimals; 0.00001 % is a rate of 7 decimals
        assertThat(codes(results.get(1), "componentQuantity")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(codes(results.get(1), "scrapPercent")).containsExactly(ImportErrorCode.INVALID_FORMAT);
    }

    /** One saveDraft per group, in file order, with line numbers 1..n and the percent as a rate. */
    @Test
    void apply_createsOneDraftPerGroupThroughTheBomService() {
        UUID firstBom = UUID.randomUUID();
        UUID secondBom = UUID.randomUUID();
        when(bomService.saveDraft(eq(companyId), any())).thenReturn(response(firstBom), response(secondBom));

        List<Map<String, String>> rows = List.of(
                values("BOM-1", "FG-DEMO", "10", "PCS", "RM-DEMO", "25", "KG", "5", "Notes"),
                values("BOM-2", "FG-TWO", "1", "PCS", "RM-DEMO", "2", "KG", "0", ""),
                values("BOM-1", "FG-DEMO", "10", "PCS", "RM-TWO", "5", "KG", "0", "Notes"));

        List<UUID> created = handler.apply(companyId, rows);

        assertThat(created).containsExactly(firstBom, secondBom, firstBom);
        ArgumentCaptor<BomDraftSaveRequest> requests = ArgumentCaptor.forClass(BomDraftSaveRequest.class);
        verify(bomService, times(2)).saveDraft(eq(companyId), requests.capture());
        BomDraftSaveRequest first = requests.getAllValues().get(0);
        assertThat(first.bomCode()).isEqualTo("BOM-1");
        assertThat(first.outputQuantity()).isEqualByComparingTo("10");
        assertThat(first.description()).isEqualTo("Notes");
        assertThat(first.lines()).extracting(line -> line.lineNo()).containsExactly(1, 2);
        assertThat(first.lines().get(0).scrapRate()).isEqualByComparingTo("0.05");
        assertThat(first.lines().get(0).componentQuantity()).isEqualByComparingTo("25");
        assertThat(requests.getAllValues().get(1).description()).isNull();
    }

    private List<RowResult> validate(RowInput... rows) {
        return validator.validate(DataImportKind.BOMS, companyId, "vi", List.of(rows));
    }

    private RowInput row(int line, String... values) {
        return new RowInput(line, values(values), new ArrayList<>());
    }

    private Map<String, String> values(String... values) {
        String[] fields = {"bomCode", "productCode", "outputQuantity", "outputUom", "componentCode",
                "componentQuantity", "componentUom", "scrapPercent", "notes"};
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index++) {
            map.put(fields[index], values[index]);
        }
        return map;
    }

    private List<ImportErrorCode> codes(RowResult row, String field) {
        return row.issues().stream().filter(issue -> field.equals(issue.targetField()))
                .map(ImportCellError::code).toList();
    }

    private void item(String code, ItemType type, String unit) {
        items.put(code, Item.builder().itemId(UUID.randomUUID()).code(code).name(code).type(type).unit(unit)
                .status(ItemStatus.ACTIVE).build());
    }

    private BomResponse response(UUID bomId) {
        return new BomResponse(bomId, companyId, null, null, null, "1", "DRAFT", null, null, null, List.of(),
                "BOM", 1, BigDecimal.ONE, "PCS", null, 0L);
    }
}

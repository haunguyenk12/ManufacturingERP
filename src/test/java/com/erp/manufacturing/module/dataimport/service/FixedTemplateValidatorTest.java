package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.target.CustomerImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.dataimport.target.ItemImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.SupplierImportTargetHandler;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.ItemService;
import com.erp.manufacturing.module.purchasing.service.SupplierLookupService;
import com.erp.manufacturing.module.purchasing.service.SupplierService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.service.CustomerLookupService;
import com.erp.manufacturing.module.sales.service.CustomerService;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.domain.UomStatus;
import com.erp.manufacturing.module.uom.service.UomLookupService;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The real handlers are used (R3): the database rules they own are half of what a row must pass, and
 * mocking them would delete the thing under test. Only the lookups that would hit the database are
 * mocked.
 */
class FixedTemplateValidatorTest {

    private static final UUID COMPANY = UUID.randomUUID();

    private final ItemLookupService itemLookup = mock(ItemLookupService.class);
    private final UomLookupService uomLookup = mock(UomLookupService.class);
    private final SupplierLookupService supplierLookup = mock(SupplierLookupService.class);
    private final CustomerLookupService customerLookup = mock(CustomerLookupService.class);
    private FixedTemplateValidator validator;

    @BeforeEach
    void setUp() {
        ImportTargetRegistry registry = new ImportTargetRegistry(List.of(
                new ItemImportTargetHandler(mock(ItemService.class), itemLookup, uomLookup),
                new SupplierImportTargetHandler(mock(SupplierService.class), supplierLookup),
                new CustomerImportTargetHandler(mock(CustomerService.class), customerLookup)));
        validator = new FixedTemplateValidator(registry, org.mockito.Mockito.mock(com.erp.manufacturing.module.dataimport.target.BomImportHandler.class), org.mockito.Mockito.mock(com.erp.manufacturing.module.dataimport.target.SalesOrderImportHandler.class));
        when(itemLookup.findItemsByCode(any(), anyCollection())).thenReturn(Map.of());
        when(supplierLookup.findSuppliersByCode(any(), anyCollection())).thenReturn(Map.of());
        when(customerLookup.findCustomersByCode(any(), anyCollection())).thenReturn(Map.of());
        when(uomLookup.findByCode(anyCollection())).thenReturn(Map.of(
                "KG", Uom.builder().code("KG").status(UomStatus.ACTIVE).build(),
                "OLD", Uom.builder().code("OLD").status(UomStatus.INACTIVE).build()));
    }

    @Test
    void validate_viLabels_becomeCanonicalValues() {
        var result = validator.validate(DataImportKind.ITEMS, COMPANY, "vi", List.of(
                item(2, "GD3-RM-0001", "Nguyên liệu", "Nguyên vật liệu", "KG", "Có"),
                item(3, "GD3-RM-0002", "Nguyên liệu", "vật tư tiêu hao", "KG", "Không"),
                item(4, "GD3-RM-0003", "Nguyên liệu", "FINISHED_GOOD", "KG", "LOT")));

        assertThat(result).allMatch(FixedTemplateValidator.RowResult::valid);
        assertThat(result).extracting(row -> row.values().get("type"), row -> row.values().get("tracking"))
                .containsExactly(tuple("RAW_MATERIAL", "LOT"), tuple("CONSUMABLE", "NONE"),
                        tuple("FINISHED_GOOD", "LOT"));
    }

    @Test
    void validate_englishLabelUnderVietnameseLocale_isNotAccepted() {
        var result = validator.validate(DataImportKind.ITEMS, COMPANY, "vi", List.of(
                item(2, "A-1", "Name", "Raw material", "KG", "Yes")));

        assertThat(issues(result.get(0))).containsExactly(
                tuple("type", "C", ImportErrorCode.NOT_ALLOWED_VALUE),
                tuple("tracking", "E", ImportErrorCode.NOT_ALLOWED_VALUE));
    }

    @Test
    void validate_serviceItemType_isOutOfTemplateScope() {
        var result = validator.validate(DataImportKind.ITEMS, COMPANY, "en", List.of(
                item(2, "A-1", "Name", "SERVICE", "KG", "No")));

        assertThat(issues(result.get(0))).containsExactly(tuple("type", "C", ImportErrorCode.NOT_ALLOWED_VALUE));
    }

    @Test
    void validate_fieldRules_reportEveryBrokenFieldWithItsColumn() {
        var result = validator.validate(DataImportKind.CUSTOMERS, COMPANY, "en", List.of(
                partner(5, "BAD CODE!", "", "x".repeat(256), "not-an-email", "", "", "")));

        assertThat(result.get(0).line()).isEqualTo(5);
        assertThat(issues(result.get(0))).containsExactly(
                tuple("code", "A", ImportErrorCode.INVALID_FORMAT),
                tuple("name", "B", ImportErrorCode.REQUIRED_MISSING),
                tuple("contact", "C", ImportErrorCode.TOO_LONG),
                tuple("email", "D", ImportErrorCode.INVALID_FORMAT));
    }

    @Test
    void validate_supplierPhone_isBoundByTheSupplierColumnWidth_customerPhoneIsNot() {
        String phone = "0".repeat(61);

        var supplier = validator.validate(DataImportKind.SUPPLIERS, COMPANY, "en", List.of(
                partner(2, "P-1", "Name", "", "", phone, "", "")));
        var customer = validator.validate(DataImportKind.CUSTOMERS, COMPANY, "en", List.of(
                partner(2, "P-1", "Name", "", "", phone, "", "")));

        assertThat(issues(supplier.get(0))).containsExactly(tuple("phone", "E", ImportErrorCode.TOO_LONG));
        assertThat(customer.get(0).valid()).isTrue();
    }

    /** FE A09: abc/ABC normalise to one code — both rows are flagged, not just the second. */
    @Test
    void validate_codeRepeatedAfterNormalisation_flagsEveryOccurrence() {
        var result = validator.validate(DataImportKind.SUPPLIERS, COMPANY, "en", List.of(
                partner(2, "ABC", "One", "", "", "", "", ""),
                partner(3, "OTHER", "Two", "", "", "", "", ""),
                partner(4, "ABC", "Three", "", "", "", "", "")));

        assertThat(issues(result.get(0))).containsExactly(tuple("code", "A", ImportErrorCode.DUPLICATE_IN_FILE));
        assertThat(result.get(1).valid()).isTrue();
        assertThat(issues(result.get(2))).containsExactly(tuple("code", "A", ImportErrorCode.DUPLICATE_IN_FILE));
    }

    /** FE A10/A11: a code taken in this catalogue — any status — is refused; one query for the file. */
    @Test
    void validate_codeAlreadyInTheCatalogue_isAlreadyExists_fromOneLookup() {
        when(customerLookup.findCustomersByCode(eq(COMPANY), anyCollection()))
                .thenReturn(Map.of("TAKEN", Customer.builder().code("TAKEN").build()));

        var result = validator.validate(DataImportKind.CUSTOMERS, COMPANY, "en", List.of(
                partner(2, "TAKEN", "One", "", "", "", "", ""),
                partner(3, "FREE", "Two", "", "", "", "", "")));

        assertThat(issues(result.get(0))).containsExactly(tuple("code", "A", ImportErrorCode.ALREADY_EXISTS));
        assertThat(result.get(1).valid()).isTrue();
        verify(customerLookup, times(1)).findCustomersByCode(eq(COMPANY), eq(Set.of("TAKEN", "FREE")));
        verifyNoInteractions(supplierLookup);
    }

    @Test
    void validate_missingOrInactiveUom_isReportedOnTheUnitColumn() {
        var result = validator.validate(DataImportKind.ITEMS, COMPANY, "en", List.of(
                item(2, "A-1", "Name", "Raw material", "NOPE", "No"),
                item(3, "A-2", "Name", "Raw material", "OLD", "No")));

        assertThat(issues(result.get(0))).containsExactly(tuple("unit", "D", ImportErrorCode.REFERENCE_NOT_FOUND));
        assertThat(issues(result.get(1))).containsExactly(tuple("unit", "D", ImportErrorCode.RULE_VIOLATION));
    }

    @Test
    void validate_itemCodeTakenInTheCompany_isAlreadyExists() {
        when(itemLookup.findItemsByCode(eq(COMPANY), anyCollection()))
                .thenReturn(Map.of("A-1", Item.builder().code("A-1").build()));

        var result = validator.validate(DataImportKind.ITEMS, COMPANY, "en", List.of(
                item(2, "A-1", "Name", "Raw material", "KG", "No")));

        assertThat(issues(result.get(0))).containsExactly(tuple("code", "A", ImportErrorCode.ALREADY_EXISTS));
    }

    /** A cell refused while reading keeps its one issue; the field rules do not pile on a second. */
    @Test
    void validate_cellTypeIssueFromTheReader_isNotFollowedByARequiredIssue() {
        var input = new FixedTemplateValidator.RowInput(2, partnerValues("", "Name", "", "", "", "", ""),
                List.of(ImportCellError.of("A", "code", ImportErrorCode.TEXT_REQUIRED, "number")));

        var result = validator.validate(DataImportKind.SUPPLIERS, COMPANY, "en", List.of(input));

        assertThat(issues(result.get(0))).containsExactly(tuple("code", "A", ImportErrorCode.TEXT_REQUIRED));
        verifyNoInteractions(supplierLookup);
    }

    private static List<Tuple> issues(FixedTemplateValidator.RowResult row) {
        return row.issues().stream().map(issue -> tuple(issue.targetField(), issue.column(), issue.code())).toList();
    }

    private static FixedTemplateValidator.RowInput item(int line, String code, String name, String type,
                                                        String unit, String tracking) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("code", code);
        values.put("name", name);
        values.put("type", type);
        values.put("unit", unit);
        values.put("tracking", tracking);
        return new FixedTemplateValidator.RowInput(line, values, List.of());
    }

    private static FixedTemplateValidator.RowInput partner(int line, String code, String name, String contact,
                                                           String email, String phone, String address,
                                                           String notes) {
        return new FixedTemplateValidator.RowInput(line,
                partnerValues(code, name, contact, email, phone, address, notes), List.of());
    }

    private static Map<String, String> partnerValues(String code, String name, String contact, String email,
                                                     String phone, String address, String notes) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("code", code);
        values.put("name", name);
        values.put("contact", contact);
        values.put("email", email);
        values.put("phone", phone);
        values.put("address", address);
        values.put("notes", notes);
        return values;
    }
}

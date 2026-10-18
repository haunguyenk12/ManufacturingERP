package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
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
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.service.CustomerLookupService;
import com.erp.manufacturing.module.sales.service.SalesOrderService;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.domain.UomStatus;
import com.erp.manufacturing.module.uom.service.UomLookupService;
import org.apache.poi.ss.usermodel.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G6c sales order import: the {@code omniplant-so-v1} reader (dates above all, FE Q19), the group rules
 * and the commit through {@code SalesOrderService.createDraft}. Validation runs through the real
 * {@link FixedTemplateValidator} so the field rules (decimal shape, ISO date) are part of what is tested.
 */
@DisplayName("Sales order import (omniplant-so-v1)")
class SalesOrderImportHandlerTest {

    private final UUID companyId = UUID.randomUUID();
    private final UUID plantId = UUID.randomUUID();
    private final CustomerLookupService customerLookupService = mock(CustomerLookupService.class);
    private final ItemLookupService itemLookupService = mock(ItemLookupService.class);
    private final UomLookupService uomLookupService = mock(UomLookupService.class);
    private final SalesOrderService salesOrderService = mock(SalesOrderService.class);
    private final SalesOrderImportHandler handler = new SalesOrderImportHandler(customerLookupService,
            itemLookupService, uomLookupService, salesOrderService);
    private final FixedTemplateValidator validator = new FixedTemplateValidator(new ImportTargetRegistry(List.of()),
            mock(BomImportHandler.class), handler);
    private final Map<String, Customer> customers = new HashMap<>();
    private final Map<String, Item> items = new HashMap<>();
    private final Map<String, Uom> uoms = new HashMap<>();

    @BeforeEach
    void setUp() {
        customer("CUS-A", CustomerStatus.ACTIVE);
        customer("CUS-B", CustomerStatus.ACTIVE);
        customer("CUS-OFF", CustomerStatus.INACTIVE);
        item("FG-1", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE, "PCS");
        item("WIP-1", ItemType.WIP, ItemStatus.ACTIVE, "PCS");
        item("RM-1", ItemType.RAW_MATERIAL, ItemStatus.ACTIVE, "KG");
        item("FG-OFF", ItemType.FINISHED_GOOD, ItemStatus.INACTIVE, "PCS");
        item("FG-BOX", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE, "BOX");
        uoms.put("PCS", Uom.builder().code("PCS").name("Pieces").status(UomStatus.ACTIVE).build());
        uoms.put("BOX", Uom.builder().code("BOX").name("Box").status(UomStatus.INACTIVE).build());
        when(customerLookupService.findCustomersByCode(eq(companyId), anyCollection()))
                .thenAnswer(invocation -> pick(customers, invocation.getArgument(1)));
        when(itemLookupService.findItemsByCode(eq(companyId), anyCollection()))
                .thenAnswer(invocation -> pick(items, invocation.getArgument(1)));
        when(uomLookupService.findByCode(anyCollection())).thenAnswer(invocation -> pick(uoms, invocation.getArgument(0)));
    }

    // ── Reader ─────────────────────────────────────────────────────────────

    /** FE §3.5: sheet "Sales orders", _meta key/value; ISO text and a real date cell both read as yyyy-MM-dd. */
    @Test
    void reader_soTemplate_readsIsoTextAndRealDateCellsAsTheSameIsoDate() {
        byte[] file = TemplateWorkbooks.salesOrders("en")
                .row(2, "a-1", "cus-a", "2026-10-20", " REF 1 ", "fg-1", "2.5", "2026-10-30")
                .row(3, "A-1", "CUS-A", null, "REF 1", "FG-1", null, null)
                .dateCell(3, 2, LocalDate.of(2026, 10, 20)).numberCell(3, 5, 1.000001)
                .dateCell(3, 6, LocalDate.of(2026, 11, 2))
                .build();

        FixedTemplateWorkbook workbook = new FixedTemplateReader().read(file, DataImportKind.SALES_ORDERS, 500);

        assertThat(workbook.templateVersion()).isEqualTo("omniplant-so-v1");
        assertThat(workbook.rows().get(0).values())
                .containsEntry("groupCode", "A-1").containsEntry("customerCode", "CUS-A")
                .containsEntry("orderDate", "2026-10-20").containsEntry("customerReference", "REF 1")
                .containsEntry("itemCode", "FG-1").containsEntry("quantity", "2.5")
                .containsEntry("dueDate", "2026-10-30");
        assertThat(workbook.rows().get(1).values())
                .containsEntry("orderDate", "2026-10-20").containsEntry("quantity", "1.000001")
                .containsEntry("dueDate", "2026-11-02");
        assertThat(workbook.rows()).allMatch(row -> row.issues().isEmpty());
    }

    /**
     * FE Q19: the workbook's own date system decides the day. The serial below is 2028-10-20 in a 1904
     * workbook; read with the 1900 system the same number lands 1,462 days earlier, in 2024 — the bug
     * this test exists for.
     */
    @Test
    void reader_1904Workbook_convertsTheSerialWithTheWorkbooksDateSystem() {
        double serial = DateUtil.getExcelDate(LocalDateTime.of(2028, 10, 20, 0, 0), true);
        byte[] file = TemplateWorkbooks.salesOrders("en").date1904()
                .row(2, "A", "CUS-A", null, "", "FG-1", "1", "2028-10-25")
                .dateSerialCell(2, 2, serial, "dd/mm/yyyy")
                .build();

        FixedTemplateWorkbook workbook = new FixedTemplateReader().read(file, DataImportKind.SALES_ORDERS, 500);

        assertThat(workbook.rows().get(0).values()).containsEntry("orderDate", "2028-10-20");
        assertThat(workbook.rows().get(0).issues()).isEmpty();
    }

    /** A plain number (no date format) and a date with a time of day are refused, never guessed. */
    @Test
    void reader_plainNumberOrDateTimeInADateColumn_isACellTypeIssue() {
        byte[] file = TemplateWorkbooks.salesOrders("vi")
                .row(2, "A", "CUS-A", null, "", "FG-1", "1", null)
                .numberCell(2, 2, 46315)
                .dateSerialCell(2, 6, DateUtil.getExcelDate(LocalDateTime.of(2026, 10, 30, 13, 30), false),
                        "yyyy-mm-dd hh:mm")
                .build();

        FixedTemplateWorkbook workbook = new FixedTemplateReader().read(file, DataImportKind.SALES_ORDERS, 500);

        assertThat(workbook.rows().get(0).issues()).extracting(ImportCellError::targetField, ImportCellError::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("orderDate", ImportErrorCode.CELL_TYPE),
                        org.assertj.core.groups.Tuple.tuple("dueDate", ImportErrorCode.CELL_TYPE));
    }

    /** FE A35: a BOM workbook (or any other version) uploaded as kind=so is a TEMPLATE error, no batch. */
    @Test
    void reader_otherTemplateUploadedAsSo_isATemplateError() {
        byte[] bom = TemplateWorkbooks.boms("en").row(2, "B", "FG-1", "1", "PCS", "RM-1", "1", "KG", "", "").build();
        byte[] oldVersion = TemplateWorkbooks.salesOrders("en").version("omniplant-so-v0")
                .row(2, "A", "CUS-A", "2026-10-20", "", "FG-1", "1", "2026-10-30").build();

        for (byte[] file : List.of(bom, oldVersion)) {
            assertThatThrownBy(() -> new FixedTemplateReader().read(file, DataImportKind.SALES_ORDERS, 500))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.TEMPLATE));
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────

    /** FE A33: two groups (2 + 1 rows), FG and WIP, blank reference — all valid. */
    @Test
    void validate_twoValidGroups_haveNoIssue() {
        List<RowResult> results = validate(
                row(2, "A", "CUS-A", "2026-10-20", "PO-77", "FG-1", "2", "2026-10-30"),
                row(3, "A", "CUS-A", "2026-10-20", "PO-77", "WIP-1", "0.000001", "2026-11-05"),
                row(4, "B", "CUS-B", "2026-10-21", "", "FG-1", "5", "2026-10-21"));

        assertThat(results).allMatch(RowResult::valid);
    }

    /** FE A34: one bad row rejects its whole group, keeps its own original error, leaves the other group alone. */
    @Test
    void validate_badRow_rejectsTheWholeGroupButKeepsTheRowsOwnError() {
        List<RowResult> results = validate(
                row(2, "A", "CUS-A", "2026-10-20", "", "FG-1", "0", "2026-10-30"),
                row(3, "A", "CUS-A", "2026-10-20", "", "FG-1", "3", "2026-10-30"),
                row(4, "B", "CUS-B", "2026-10-20", "", "FG-1", "3", "2026-10-30"));

        assertThat(codes(results.get(0), "quantity")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(codes(results.get(0), "groupCode")).isEmpty();
        assertThat(codes(results.get(1), "groupCode")).containsExactly(ImportErrorCode.RULE_VIOLATION);
        assertThat(results.get(1).issues()).singleElement()
                .satisfies(issue -> assertThat(issue.message()).contains("[2]"));
        assertThat(results.get(2).valid()).isTrue();
    }

    /** Columns B–D must repeat within a group; a blank reference is not the same as a filled one. */
    @Test
    void validate_groupMismatch_onCustomerDateOrReference() {
        List<RowResult> results = validate(
                row(2, "A", "CUS-A", "2026-10-20", "R1", "FG-1", "1", "2026-10-30"),
                row(3, "A", "CUS-B", "2026-10-21", "", "FG-1", "1", "2026-10-30"));

        assertThat(results.get(1).issues()).extracting(ImportCellError::targetField)
                .containsExactlyInAnyOrder("customerCode", "orderDate", "customerReference");
        assertThat(results.get(1).issues()).extracting(ImportCellError::code).containsOnly(ImportErrorCode.GROUP_MISMATCH);
        assertThat(codes(results.get(0), "groupCode")).containsExactly(ImportErrorCode.RULE_VIOLATION);
    }

    /** FE A36 (validation side): every master rule the G6a form enforces, as a row issue on the right column. */
    @Test
    void validate_masterRules_customerItemTypeUnitAndDueDate() {
        List<RowResult> results = validate(
                row(2, "A", "CUS-NONE", "2026-10-20", "", "FG-NONE", "1", "2026-10-30"),
                row(3, "B", "CUS-OFF", "2026-10-20", "", "FG-OFF", "1", "2026-10-30"),
                row(4, "C", "CUS-A", "2026-10-20", "", "RM-1", "1", "2026-10-19"),
                row(5, "D", "CUS-A", "2026-10-20", "", "FG-BOX", "1", "2026-10-20"));

        assertThat(codes(results.get(0), "customerCode")).containsExactly(ImportErrorCode.REFERENCE_NOT_FOUND);
        assertThat(codes(results.get(0), "itemCode")).containsExactly(ImportErrorCode.REFERENCE_NOT_FOUND);
        assertThat(codes(results.get(1), "customerCode")).containsExactly(ImportErrorCode.RULE_VIOLATION);
        assertThat(codes(results.get(1), "itemCode")).containsExactly(ImportErrorCode.RULE_VIOLATION);
        assertThat(codes(results.get(2), "itemCode")).containsExactly(ImportErrorCode.NOT_ALLOWED_VALUE);
        assertThat(codes(results.get(2), "dueDate")).containsExactly(ImportErrorCode.RULE_VIOLATION);
        assertThat(codes(results.get(3), "itemCode")).containsExactly(ImportErrorCode.RULE_VIOLATION);
        assertThat(results.get(3).issues()).singleElement()
                .satisfies(issue -> assertThat(issue.message()).contains("BOX"));
    }

    /** FE Q19: only yyyy-MM-dd text, strictly — no local format, no impossible day, no 7th decimal. */
    @Test
    void validate_dateAndQuantityShape() {
        List<RowResult> results = validate(
                row(2, "A", "CUS-A", "20/10/2026", "", "FG-1", "1.0000001", "2026-02-30"),
                row(3, "B", "CUS-A", "", "", "FG-1", "1e3", "2026-10-30"),
                // LocalDate.parse alone accepts a signed five-digit year; the template does not.
                row(4, "C", "CUS-A", "+12026-10-20", "", "FG-1", "1", "+12026-10-30"));

        assertThat(codes(results.get(0), "orderDate")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(codes(results.get(0), "quantity")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(codes(results.get(0), "dueDate")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(codes(results.get(1), "orderDate")).containsExactly(ImportErrorCode.REQUIRED_MISSING);
        assertThat(codes(results.get(1), "quantity")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(codes(results.get(2), "orderDate")).containsExactly(ImportErrorCode.INVALID_FORMAT);
        assertThat(codes(results.get(2), "dueDate")).containsExactly(ImportErrorCode.INVALID_FORMAT);
    }

    /** The group code is a gathering key, not the order number: no "duplicate code" rule ever applies. */
    @Test
    void validate_sameItemTwiceInAGroup_isAllowed_andNoMasterLookupWhenEveryGroupCodeIsBroken() {
        List<RowResult> results = validate(
                row(2, "A", "CUS-A", "2026-10-20", "", "FG-1", "1", "2026-10-30"),
                row(3, "A", "CUS-A", "2026-10-20", "", "FG-1", "2", "2026-11-30"));
        assertThat(results).allMatch(RowResult::valid);

        validate(row(2, "", "CUS-A", "2026-10-20", "", "FG-1", "1", "2026-10-30"));
        verify(customerLookupService, times(1)).findCustomersByCode(any(), anyCollection());
    }

    // ── Apply ──────────────────────────────────────────────────────────────

    /** One createDraft per group, in file order, in the batch's plant; every row gets its order's id. */
    @Test
    void apply_createsOneDraftPerGroupThroughTheSalesOrderService() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SalesOrderResponse firstResponse = mock(SalesOrderResponse.class);
        SalesOrderResponse secondResponse = mock(SalesOrderResponse.class);
        when(firstResponse.salesOrderId()).thenReturn(first);
        when(secondResponse.salesOrderId()).thenReturn(second);
        when(salesOrderService.createDraft(any())).thenReturn(firstResponse, secondResponse);

        List<UUID> created = handler.apply(companyId, plantId, List.of(
                values("A", "CUS-A", "2026-10-20", "PO-77", "FG-1", "2", "2026-10-30"),
                values("B", "CUS-B", "2026-10-21", "", "FG-1", "5", "2026-10-21"),
                values("A", "CUS-A", "2026-10-20", "PO-77", "WIP-1", "0.000001", "2026-11-05")));

        assertThat(created).containsExactly(first, second, first);
        ArgumentCaptor<SalesOrderDraftCreateRequest> requests = ArgumentCaptor.forClass(SalesOrderDraftCreateRequest.class);
        verify(salesOrderService, times(2)).createDraft(requests.capture());
        SalesOrderDraftCreateRequest a = requests.getAllValues().get(0);
        assertThat(a.companyId()).isEqualTo(companyId);
        assertThat(a.plantId()).isEqualTo(plantId);
        assertThat(a.customerId()).isEqualTo(customers.get("CUS-A").getCustomerId());
        assertThat(a.customerReference()).isEqualTo("PO-77");
        assertThat(a.orderDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(a.lines()).extracting(line -> line.itemId())
                .containsExactly(items.get("FG-1").getItemId(), items.get("WIP-1").getItemId());
        assertThat(a.lines().get(1).orderedQuantity()).isEqualByComparingTo("0.000001");
        assertThat(a.lines().get(1).dueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(requests.getAllValues().get(1).customerReference()).isNull();
    }

    /** A customer gone between validation and commit is an exception (the committer revalidates), not a silent skip. */
    @Test
    void apply_customerMissingAtCommit_throwsBeforeCreatingAnything() {
        assertThatThrownBy(() -> handler.apply(companyId, plantId,
                List.of(values("A", "CUS-NONE", "2026-10-20", "", "FG-1", "1", "2026-10-30"))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
        verify(salesOrderService, never()).createDraft(any());
    }

    private List<RowResult> validate(RowInput... rows) {
        return validator.validate(DataImportKind.SALES_ORDERS, companyId, "en", List.of(rows));
    }

    private RowInput row(int line, String... values) {
        return new RowInput(line, values(values), new ArrayList<>());
    }

    private Map<String, String> values(String... values) {
        String[] fields = {"groupCode", "customerCode", "orderDate", "customerReference", "itemCode", "quantity", "dueDate"};
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

    private <T> Map<String, T> pick(Map<String, T> source, Collection<?> codes) {
        Map<String, T> found = new HashMap<>();
        for (Object code : codes) {
            if (source.containsKey(code)) {
                found.put((String) code, source.get(code));
            }
        }
        return found;
    }

    private void customer(String code, CustomerStatus status) {
        customers.put(code, Customer.builder().customerId(UUID.randomUUID()).code(code).name(code).status(status).build());
    }

    private void item(String code, ItemType type, ItemStatus status, String unit) {
        items.put(code, Item.builder().itemId(UUID.randomUUID()).code(code).name(code).type(type).unit(unit)
                .status(status).build());
    }
}

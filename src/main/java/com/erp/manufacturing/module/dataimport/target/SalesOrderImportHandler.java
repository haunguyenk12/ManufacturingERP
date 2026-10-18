package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.service.FixedTemplateValidator.RowResult;
import com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderDraftLineRequest;
import com.erp.manufacturing.module.sales.service.CustomerLookupService;
import com.erp.manufacturing.module.sales.service.SalesOrderService;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.service.UomLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema.*;

/**
 * The {@code omniplant-so-v1} rules that need more than one cell (G6c, FE_HANDOFF_GD_06 §3.5): rows
 * grouped by group code, each group one sales order DRAFT. Not an {@link ImportTargetHandler} for the
 * same reason as {@link BomImportHandler} — the mapped-profile path is one row, one record.
 *
 * <p>The group code only gathers rows; it is never the order number (the server allocates
 * {@code SO-000001}…, FE Q1) and never an upsert key, so the same file uploaded twice creates two sets
 * of drafts — replay protection is the batch's own commit key. A group with any error rejects every
 * row of it, while each row keeps its own original issue (FE §3.5).
 */
@Component
@RequiredArgsConstructor
public class SalesOrderImportHandler {

    /** Columns that must repeat identically on every row of one order. */
    private static final List<String> GROUP_FIELDS = List.of(CUSTOMER_CODE, ORDER_DATE, CUSTOMER_REFERENCE);
    /** What can be sold — the same set {@code SalesOrderService.createDraft} enforces (G6a). */
    private static final Set<ItemType> SELLABLE_TYPES = Set.of(ItemType.FINISHED_GOOD, ItemType.WIP);

    private final CustomerLookupService customerLookupService;
    private final ItemLookupService itemLookupService;
    private final UomLookupService uomLookupService;
    private final SalesOrderService salesOrderService;

    /** Adds every group and database problem to the rows; rows whose group code is unreadable are skipped. */
    public void validate(UUID companyId, List<RowResult> rows) {
        Map<String, List<RowResult>> groups = groupRows(rows);
        if (groups.isEmpty()) {
            return;
        }
        groups.values().forEach(this::checkGroupConsistency);

        Set<String> customerCodes = new HashSet<>();
        Set<String> itemCodes = new HashSet<>();
        groups.values().forEach(group -> group.forEach(row -> {
            customerCodes.add(row.values().get(CUSTOMER_CODE));
            itemCodes.add(row.values().get(ITEM_CODE));
        }));
        customerCodes.removeIf(code -> code == null || code.isBlank());
        itemCodes.removeIf(code -> code == null || code.isBlank());
        Map<String, Customer> customers = customerLookupService.findCustomersByCode(companyId, customerCodes);
        Map<String, Item> items = itemLookupService.findItemsByCode(companyId, itemCodes);
        Map<String, Uom> uoms = uomLookupService.findByCode(items.values().stream()
                .map(Item::getUnit).filter(Objects::nonNull).distinct().toList());

        groups.values().forEach(group -> group.forEach(row -> checkRowAgainstMasters(row, customers, items, uoms)));
        groups.forEach(this::rejectWholeGroupIfAnyRowFailed);
    }

    /**
     * Creates one sales order DRAFT per group, in file order, through
     * {@link SalesOrderService#createDraft} — the G6a path with its own rules, server order number and
     * audit (no INSERT behind the service's back, §0.45 #4). Runs inside the committer's transaction, so
     * a failure on the last group takes the earlier orders with it.
     *
     * @return for each input row, the id of the sales order it became
     */
    public List<UUID> apply(UUID companyId, UUID plantId, List<Map<String, String>> rows) {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        Set<String> customerCodes = new HashSet<>();
        Set<String> itemCodes = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            groups.computeIfAbsent(row.get(GROUP_CODE), code -> new ArrayList<>()).add(index);
            customerCodes.add(row.get(CUSTOMER_CODE));
            itemCodes.add(row.get(ITEM_CODE));
        }
        Map<String, Customer> customers = customerLookupService.findCustomersByCode(companyId, customerCodes);
        Map<String, Item> items = itemLookupService.findItemsByCode(companyId, itemCodes);
        List<UUID> created = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            created.add(null);
        }
        groups.forEach((groupCode, indexes) -> {
            Map<String, String> first = rows.get(indexes.get(0));
            List<SalesOrderDraftLineRequest> lines = new ArrayList<>(indexes.size());
            for (Integer index : indexes) {
                Map<String, String> row = rows.get(index);
                lines.add(new SalesOrderDraftLineRequest(
                        resolve(items, row.get(ITEM_CODE), "Item").getItemId(),
                        new BigDecimal(row.get(QUANTITY)), LocalDate.parse(row.get(DUE_DATE))));
            }
            String reference = first.get(CUSTOMER_REFERENCE);
            UUID salesOrderId = salesOrderService.createDraft(new SalesOrderDraftCreateRequest(
                    companyId, plantId, resolve(customers, first.get(CUSTOMER_CODE), "Customer").getCustomerId(),
                    reference == null || reference.isBlank() ? null : reference,
                    LocalDate.parse(first.get(ORDER_DATE)), null, lines)).salesOrderId();
            indexes.forEach(index -> created.set(index, salesOrderId));
        });
        return created;
    }

    private Map<String, List<RowResult>> groupRows(List<RowResult> rows) {
        Map<String, List<RowResult>> groups = new LinkedHashMap<>();
        for (RowResult row : rows) {
            if (!hasIssue(row, GROUP_CODE)) {
                groups.computeIfAbsent(row.values().get(GROUP_CODE), code -> new ArrayList<>()).add(row);
            }
        }
        return groups;
    }

    private void checkGroupConsistency(List<RowResult> group) {
        RowResult first = group.get(0);
        for (RowResult row : group.subList(1, group.size())) {
            for (String field : GROUP_FIELDS) {
                if (!hasIssue(row, field) && !hasIssue(first, field)
                        && !first.values().getOrDefault(field, "").equals(row.values().getOrDefault(field, ""))) {
                    add(row, field, ImportErrorCode.GROUP_MISMATCH, "Must repeat the value of row " + first.line()
                            + " for order group " + first.values().get(GROUP_CODE));
                }
            }
        }
    }

    private void checkRowAgainstMasters(RowResult row, Map<String, Customer> customers, Map<String, Item> items,
                                        Map<String, Uom> uoms) {
        if (!hasIssue(row, CUSTOMER_CODE)) {
            Customer customer = customers.get(row.values().get(CUSTOMER_CODE));
            if (customer == null) {
                add(row, CUSTOMER_CODE, ImportErrorCode.REFERENCE_NOT_FOUND, "No customer with this code");
            } else if (!customer.isActive()) {
                add(row, CUSTOMER_CODE, ImportErrorCode.RULE_VIOLATION, "Customer is inactive");
            }
        }
        if (!hasIssue(row, ITEM_CODE)) {
            Item item = items.get(row.values().get(ITEM_CODE));
            Uom uom = item == null ? null : uoms.get(item.getUnit());
            if (item == null) {
                add(row, ITEM_CODE, ImportErrorCode.REFERENCE_NOT_FOUND, "No item with this code");
            } else if (!item.isActive()) {
                add(row, ITEM_CODE, ImportErrorCode.RULE_VIOLATION, "Item is inactive");
            } else if (!SELLABLE_TYPES.contains(item.getType())) {
                add(row, ITEM_CODE, ImportErrorCode.NOT_ALLOWED_VALUE,
                        "Only finished goods and WIP items can be sold");
            } else if (uom != null && !uom.isActive()) {
                add(row, ITEM_CODE, ImportErrorCode.RULE_VIOLATION,
                        "The item's unit of measure " + item.getUnit() + " is inactive");
            }
        }
        if (!hasIssue(row, ORDER_DATE) && !hasIssue(row, DUE_DATE)
                && LocalDate.parse(row.values().get(DUE_DATE)).isBefore(LocalDate.parse(row.values().get(ORDER_DATE)))) {
            add(row, DUE_DATE, ImportErrorCode.RULE_VIOLATION, "Must not be before the order date");
        }
    }

    private void rejectWholeGroupIfAnyRowFailed(String groupCode, List<RowResult> group) {
        List<Integer> failing = group.stream().filter(row -> !row.valid()).map(RowResult::line).toList();
        if (failing.isEmpty()) {
            return;
        }
        group.stream().filter(RowResult::valid).forEach(row -> add(row, GROUP_CODE, ImportErrorCode.RULE_VIOLATION,
                "Order group " + groupCode + " has errors on rows " + failing + "; the whole order is rejected"));
    }

    private <T> T resolve(Map<String, T> byCode, String code, String what) {
        T found = byCode.get(code);
        if (found == null) {
            // Only reachable when the record disappeared between validation and commit: the committer
            // rolls back and revalidates, which turns this into a row issue.
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, what, code);
        }
        return found;
    }

    private void add(RowResult row, String field, ImportErrorCode code, String message) {
        if (!hasIssue(row, field)) {
            row.issues().add(ImportCellError.of(
                    FixedTemplateSchema.columnLetter(DataImportKind.SALES_ORDERS, field).orElse(null),
                    field, code, message));
        }
    }

    private boolean hasIssue(RowResult row, String field) {
        return row.issues().stream().anyMatch(issue -> field.equals(issue.targetField()));
    }
}

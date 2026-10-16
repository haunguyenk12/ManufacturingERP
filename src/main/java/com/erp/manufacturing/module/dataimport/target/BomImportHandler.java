package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.dto.BomDraftLineRequest;
import com.erp.manufacturing.module.bom.dto.BomDraftSaveRequest;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.bom.service.BomService;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.service.FixedTemplateValidator.RowResult;
import com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema.*;

/**
 * The {@code omniplant-bom-v1} rules that need more than one cell (G5, FE §3.4): rows grouped by BOM
 * code, each group one BOM. Deliberately not an {@link ImportTargetHandler} — that contract is one row,
 * one record, and registering BOM there would offer it to the mapped-profile path, which cannot group.
 *
 * <p>Create-only: a product that already has a BOM in any status, or a BOM code already taken, refuses
 * the group — the file never versions, upserts or activates (FE §1.2). A group with any error rejects
 * every row of it, so one bad component never leaves the rest of its BOM looking importable.
 */
@Component
@RequiredArgsConstructor
public class BomImportHandler {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int QUANTITY_SCALE = 6;
    /** Columns that must repeat identically on every row of one BOM. */
    private static final List<String> GROUP_FIELDS = List.of(PRODUCT_CODE, OUTPUT_QUANTITY, OUTPUT_UOM, NOTES);

    private final ItemLookupService itemLookupService;
    private final BomLookupService bomLookupService;
    private final BomService bomService;

    /** Adds every group and database problem to the rows; rows whose BOM code is unreadable are skipped. */
    public void validate(UUID companyId, List<RowResult> rows) {
        Map<String, List<RowResult>> groups = new LinkedHashMap<>();
        for (RowResult row : rows) {
            if (!hasIssue(row, BOM_CODE)) {
                groups.computeIfAbsent(row.values().get(BOM_CODE), code -> new ArrayList<>()).add(row);
            }
        }
        if (groups.isEmpty()) {
            return;
        }
        groups.values().forEach(this::checkGroupConsistency);
        flagProductsInSeveralGroups(groups);

        Set<String> codes = new HashSet<>();
        groups.values().forEach(group -> group.forEach(row -> {
            codes.add(row.values().get(PRODUCT_CODE));
            codes.add(row.values().get(COMPONENT_CODE));
        }));
        codes.removeIf(code -> code == null || code.isBlank());
        Map<String, Item> items = itemLookupService.findItemsByCode(companyId, codes);
        Set<String> takenCodes = bomLookupService.findTakenBomCodes(companyId, groups.keySet());
        Set<UUID> productsWithBom = bomLookupService.findItemIdsWithAnyBom(companyId, items.values().stream()
                .map(Item::getItemId).toList());

        groups.forEach((bomCode, group) -> {
            if (takenCodes.contains(bomCode)) {
                group.forEach(row -> add(row, BOM_CODE, ImportErrorCode.ALREADY_EXISTS,
                        "BOM code '" + bomCode + "' already exists"));
            }
            group.forEach(row -> checkRowAgainstMasters(row, items, productsWithBom));
        });
        groups.forEach(this::rejectWholeGroupIfAnyRowFailed);
    }

    /**
     * Creates one BOM DRAFT per group, in file order, through {@link BomService#saveDraft} — the same
     * path and the same rules as the form (no INSERT behind the service's back, §0.45 #4). Runs inside
     * the committer's transaction, so a failure on the last group takes the earlier ones with it.
     *
     * @return for each input row, the id of the BOM it became
     */
    public List<UUID> apply(UUID companyId, List<Map<String, String>> rows) {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            groups.computeIfAbsent(row.get(BOM_CODE), code -> new ArrayList<>()).add(index);
            codes.add(row.get(PRODUCT_CODE));
            codes.add(row.get(COMPONENT_CODE));
        }
        Map<String, Item> items = itemLookupService.findItemsByCode(companyId, codes);
        List<UUID> created = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            created.add(null);
        }
        groups.forEach((bomCode, indexes) -> {
            Map<String, String> first = rows.get(indexes.get(0));
            List<BomDraftLineRequest> lines = new ArrayList<>();
            int lineNo = 1;
            for (Integer index : indexes) {
                Map<String, String> row = rows.get(index);
                lines.add(new BomDraftLineRequest(lineNo++, item(items, row.get(COMPONENT_CODE)).getItemId(),
                        new BigDecimal(row.get(COMPONENT_QUANTITY)), scrapRate(row.get(SCRAP_PERCENT))));
            }
            String notes = first.get(NOTES);
            UUID bomId = bomService.saveDraft(companyId, new BomDraftSaveRequest(
                    null, null, bomCode, item(items, first.get(PRODUCT_CODE)).getItemId(), null,
                    new BigDecimal(first.get(OUTPUT_QUANTITY)), null,
                    notes == null || notes.isBlank() ? null : notes, lines)).bomId();
            indexes.forEach(index -> created.set(index, bomId));
        });
        return created;
    }

    private void checkGroupConsistency(List<RowResult> group) {
        RowResult first = group.get(0);
        for (RowResult row : group.subList(1, group.size())) {
            for (String field : GROUP_FIELDS) {
                if (!hasIssue(row, field) && !hasIssue(first, field) && !sameValue(first, row, field)) {
                    add(row, field, ImportErrorCode.GROUP_MISMATCH,
                            "Must repeat the value of row " + first.line() + " for BOM " + first.values().get(BOM_CODE));
                }
            }
        }
        Map<String, List<RowResult>> byComponent = new HashMap<>();
        group.stream().filter(row -> !hasIssue(row, COMPONENT_CODE))
                .forEach(row -> byComponent.computeIfAbsent(row.values().get(COMPONENT_CODE), code -> new ArrayList<>()).add(row));
        byComponent.forEach((component, repeated) -> {
            if (repeated.size() > 1) {
                repeated.forEach(row -> add(row, COMPONENT_CODE, ImportErrorCode.DUPLICATE_IN_FILE,
                        "Component '" + component + "' appears twice in BOM " + row.values().get(BOM_CODE)));
            }
        });
    }

    /** One product, one BOM per file — two codes for the same product would be two versions at once. */
    private void flagProductsInSeveralGroups(Map<String, List<RowResult>> groups) {
        Map<String, Set<String>> codesByProduct = new HashMap<>();
        groups.forEach((bomCode, group) -> {
            RowResult first = group.get(0);
            if (!hasIssue(first, PRODUCT_CODE)) {
                codesByProduct.computeIfAbsent(first.values().get(PRODUCT_CODE), product -> new HashSet<>()).add(bomCode);
            }
        });
        codesByProduct.forEach((product, bomCodes) -> {
            if (bomCodes.size() > 1) {
                bomCodes.forEach(bomCode -> groups.get(bomCode).forEach(row -> add(row, PRODUCT_CODE,
                        ImportErrorCode.DUPLICATE_IN_FILE,
                        "Product '" + product + "' has more than one BOM in this file: " + bomCodes)));
            }
        });
    }

    private void checkRowAgainstMasters(RowResult row, Map<String, Item> items, Set<UUID> productsWithBom) {
        Item product = null;
        if (!hasIssue(row, PRODUCT_CODE)) {
            product = items.get(row.values().get(PRODUCT_CODE));
            if (product == null) {
                add(row, PRODUCT_CODE, ImportErrorCode.REFERENCE_NOT_FOUND, "No item with this code");
            } else if (!product.isActive()) {
                add(row, PRODUCT_CODE, ImportErrorCode.RULE_VIOLATION, "Item is inactive");
            } else if (product.getType() != ItemType.FINISHED_GOOD && product.getType() != ItemType.WIP) {
                add(row, PRODUCT_CODE, ImportErrorCode.NOT_ALLOWED_VALUE, "A BOM product must be FINISHED_GOOD or WIP");
            } else if (productsWithBom.contains(product.getItemId())) {
                add(row, PRODUCT_CODE, ImportErrorCode.ALREADY_EXISTS,
                        "This product already has a BOM; the import only creates new ones");
            } else if (!hasIssue(row, OUTPUT_UOM) && !product.getUnit().equalsIgnoreCase(row.values().get(OUTPUT_UOM))) {
                add(row, OUTPUT_UOM, ImportErrorCode.RULE_VIOLATION,
                        "Must be the product's unit " + product.getUnit() + "; units are not converted");
            }
        }
        if (!hasIssue(row, COMPONENT_CODE)) {
            Item component = items.get(row.values().get(COMPONENT_CODE));
            if (component == null) {
                add(row, COMPONENT_CODE, ImportErrorCode.REFERENCE_NOT_FOUND, "No item with this code");
            } else if (!component.isActive()) {
                add(row, COMPONENT_CODE, ImportErrorCode.RULE_VIOLATION, "Item is inactive");
            } else if (component.getType() == ItemType.FINISHED_GOOD || component.getType() == ItemType.SERVICE) {
                add(row, COMPONENT_CODE, ImportErrorCode.NOT_ALLOWED_VALUE,
                        "A component must be a raw material, WIP or consumable item");
            } else if (product != null && component.getItemId().equals(product.getItemId())) {
                add(row, COMPONENT_CODE, ImportErrorCode.RULE_VIOLATION, "A product cannot be its own component");
            } else if (!hasIssue(row, COMPONENT_UOM)
                    && !component.getUnit().equalsIgnoreCase(row.values().get(COMPONENT_UOM))) {
                add(row, COMPONENT_UOM, ImportErrorCode.RULE_VIOLATION,
                        "Must be the component's unit " + component.getUnit() + "; units are not converted");
            }
        }
        if (!hasIssue(row, COMPONENT_QUANTITY) && !hasIssue(row, OUTPUT_QUANTITY)
                && !fitsScale(new BigDecimal(row.values().get(COMPONENT_QUANTITY)),
                new BigDecimal(row.values().get(OUTPUT_QUANTITY)))) {
            add(row, COMPONENT_QUANTITY, ImportErrorCode.INVALID_FORMAT,
                    "Component quantity divided by output quantity needs more than 6 decimals");
        }
        if (!hasIssue(row, SCRAP_PERCENT) && !fitsScale(new BigDecimal(row.values().get(SCRAP_PERCENT)), HUNDRED)) {
            add(row, SCRAP_PERCENT, ImportErrorCode.INVALID_FORMAT, "Scrap allows at most 4 decimals of a percent");
        }
    }

    private void rejectWholeGroupIfAnyRowFailed(String bomCode, List<RowResult> group) {
        List<Integer> failing = group.stream().filter(row -> !row.valid()).map(RowResult::line).toList();
        if (failing.isEmpty()) {
            return;
        }
        group.stream().filter(RowResult::valid).forEach(row -> add(row, BOM_CODE, ImportErrorCode.RULE_VIOLATION,
                "BOM " + bomCode + " has errors on rows " + failing + "; the whole BOM is rejected"));
    }

    private boolean sameValue(RowResult a, RowResult b, String field) {
        String left = a.values().getOrDefault(field, "");
        String right = b.values().getOrDefault(field, "");
        if (OUTPUT_QUANTITY.equals(field)) {
            return new BigDecimal(left).compareTo(new BigDecimal(right)) == 0;
        }
        return left.equals(right);
    }

    /** {@code dividend / divisor} is exact within 6 decimals — the stored scale of quantity and rate. */
    private boolean fitsScale(BigDecimal dividend, BigDecimal divisor) {
        try {
            return dividend.divide(divisor).stripTrailingZeros().scale() <= QUANTITY_SCALE;
        } catch (ArithmeticException nonTerminating) {
            return false;
        }
    }

    private BigDecimal scrapRate(String percent) {
        return new BigDecimal(percent).divide(HUNDRED).stripTrailingZeros().setScale(QUANTITY_SCALE);
    }

    private Item item(Map<String, Item> items, String code) {
        Item item = items.get(code);
        if (item == null) {
            // Only reachable when the item disappeared between validation and commit: the committer
            // rolls back and revalidates, which turns this into a row issue.
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Item", code);
        }
        return item;
    }

    private void add(RowResult row, String field, ImportErrorCode code, String message) {
        if (!hasIssue(row, field)) {
            row.issues().add(ImportCellError.of(
                    FixedTemplateSchema.columnLetter(DataImportKind.BOMS, field).orElse(null), field, code, message));
        }
    }

    private boolean hasIssue(RowResult row, String field) {
        return row.issues().stream().anyMatch(issue -> field.equals(issue.targetField()));
    }
}

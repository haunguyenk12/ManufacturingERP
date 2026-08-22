package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.dto.ItemCreateRequest;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Imports item master rows.
 *
 * <p>The field list mirrors {@code ItemCreateRequest} and the write goes through
 * {@code ItemService.createItem}, so an imported item is indistinguishable from one created through
 * the API: same code normalisation, same duplicate rule, same audit entry, same
 * {@code PERM_ITEM_MANAGE} check.
 *
 * <p>Create-only. A code that already exists is an error on that row, never a silent update — an
 * import that quietly rewrote existing master data would be far harder to undo than one that refused
 * a thousand rows. Upsert is a separate business decision, not a flag to add here.
 */
@Component
@RequiredArgsConstructor
public class ItemImportTargetHandler implements ImportTargetHandler {

    static final String FIELD_CODE = "code";
    static final String FIELD_NAME = "name";
    static final String FIELD_TYPE = "type";
    static final String FIELD_UNIT = "unit";
    static final String FIELD_LOT_TRACKED = "lotTracked";
    static final String FIELD_SERIAL_TRACKED = "serialTracked";

    /** Same expression {@code ItemCreateRequest} enforces, checked here so the row names the cell. */
    private static final String CODE_PATTERN = "^[A-Z0-9._-]+$";

    private final ItemService itemService;
    private final ItemLookupService itemLookupService;

    @Override
    public ImportTargetType targetType() {
        return ImportTargetType.ITEM;
    }

    @Override
    public ImportTargetDescriptor descriptor() {
        return new ImportTargetDescriptor(ImportTargetType.ITEM, "Item master", List.of(
                ImportFieldDescriptor.string(FIELD_CODE, "Item code", true, 100, "VT-001"),
                ImportFieldDescriptor.string(FIELD_NAME, "Item name", true, 255, "Khung xe 26 inch"),
                ImportFieldDescriptor.enumeration(FIELD_TYPE, "Item type", true,
                        Arrays.stream(ItemType.values()).map(Enum::name).toList(), "RAW_MATERIAL"),
                ImportFieldDescriptor.string(FIELD_UNIT, "Unit of measure", true, 30, "CAI"),
                ImportFieldDescriptor.bool(FIELD_LOT_TRACKED, "Lot tracked", "false"),
                ImportFieldDescriptor.bool(FIELD_SERIAL_TRACKED, "Serial tracked", "false")));
    }

    /**
     * Three checks that cannot be made one row at a time: the code shape, whether the file repeats a
     * code, and whether the database already has it. The last one is a single query for the whole
     * file (rule C14) — asking per row would be one round trip per line.
     */
    @Override
    public Map<Integer, List<ImportCellError>> validateBatch(UUID companyId, List<Map<String, String>> rows) {
        Map<Integer, List<ImportCellError>> problems = new HashMap<>();

        List<String> normalisedCodes = rows.stream()
                .map(row -> normaliseCode(row.get(FIELD_CODE)))
                .toList();

        Set<String> existingCodes = itemLookupService
                .findItemsByCode(companyId, new LinkedHashSet<>(normalisedCodes.stream()
                        .filter(code -> code != null && code.matches(CODE_PATTERN))
                        .toList()))
                .keySet();

        Set<String> seenInFile = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            String code = normalisedCodes.get(index);
            List<ImportCellError> rowProblems = new ArrayList<>();

            if (code != null && !code.matches(CODE_PATTERN)) {
                rowProblems.add(ImportCellError.of(null, FIELD_CODE, ImportErrorCode.INVALID_FORMAT,
                        "Item code may only contain A-Z, 0-9, dot, underscore or hyphen, got '" + code + "'"));
            } else if (code != null) {
                if (!seenInFile.add(code)) {
                    rowProblems.add(ImportCellError.of(null, FIELD_CODE, ImportErrorCode.DUPLICATE_IN_FILE,
                            "Item code '" + code + "' appears more than once in this file"));
                } else if (existingCodes.contains(code)) {
                    rowProblems.add(ImportCellError.of(null, FIELD_CODE, ImportErrorCode.ALREADY_EXISTS,
                            "Item code '" + code + "' already exists in this company"));
                }
            }

            // B96: an item is lot-tracked or serial-tracked, never both. Caught here so the row says
            // so, instead of ItemService throwing halfway through the apply loop.
            if (Boolean.parseBoolean(row.get(FIELD_LOT_TRACKED))
                    && Boolean.parseBoolean(row.get(FIELD_SERIAL_TRACKED))) {
                rowProblems.add(ImportCellError.of(null, FIELD_SERIAL_TRACKED, ImportErrorCode.RULE_VIOLATION,
                        "An item cannot be both lot-tracked and serial-tracked"));
            }

            if (!rowProblems.isEmpty()) {
                problems.put(index, rowProblems);
            }
        }
        return problems;
    }

    @Override
    public UUID apply(UUID companyId, Map<String, String> values) {
        ItemCreateRequest request = new ItemCreateRequest(
                normaliseCode(values.get(FIELD_CODE)),
                values.get(FIELD_NAME),
                ItemType.valueOf(values.get(FIELD_TYPE)),
                values.get(FIELD_UNIT),
                Boolean.parseBoolean(values.get(FIELD_LOT_TRACKED)),
                Boolean.parseBoolean(values.get(FIELD_SERIAL_TRACKED)));
        return itemService.createItem(companyId, request).itemId();
    }

    /** Same normalisation {@code ItemService} applies, so duplicate detection sees the stored form. */
    private String normaliseCode(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toUpperCase(Locale.ROOT);
    }
}

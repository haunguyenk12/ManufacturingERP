package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything that differs between "importing items" and "importing suppliers".
 *
 * <p>An interface with one implementation today, which {@code coding-rules.md} §11.5 normally forbids.
 * The exception is deliberate: this is the extension point the remaining slices plug into (supplier,
 * UOM, item-warehouse settings, BOM, opening stock are already scoped), and the alternative — a
 * {@code switch} over {@link ImportTargetType} in the run service — would put five unrelated
 * validation and write paths in one class.
 */
public interface ImportTargetHandler {

    ImportTargetType targetType();

    /** The field contract shown to the mapping screen and baked into the downloadable template. */
    ImportTargetDescriptor descriptor();

    /**
     * Checks that need the whole file, or the database, at once.
     *
     * <p>Called once per validation with every row that already passed field-level checks, so a
     * handler can answer "which of these codes already exist?" in a single query rather than one per
     * row (rule C14). Rows are identified by their position in {@code rows}, not by row number, so a
     * handler never has to know how the file was laid out.
     *
     * @param rows mapped values, in file order
     * @return index in {@code rows} to the problems found there; absent index means no problem
     */
    Map<Integer, List<ImportCellError>> validateBatch(UUID companyId, List<Map<String, String>> rows);

    /**
     * Writes one row.
     *
     * <p>Must go through the owning module's service — never a repository — so document codes,
     * optimistic locking, audit and every business invariant still run. A consequence worth stating:
     * the caller therefore needs that service's permission as well as the import permission, which is
     * the intended behaviour. Importing items is creating items.
     *
     * @return identifier of the record that was created
     */
    UUID apply(UUID companyId, Map<String, String> values);
}

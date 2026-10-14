package com.erp.manufacturing.module.dataimport.spreadsheet;

import com.erp.manufacturing.module.dataimport.domain.ImportCellError;

import java.util.List;
import java.util.Map;

/**
 * An {@code omniplant-import-v1} workbook after reading, before any business validation.
 *
 * @param locale          {@code vi} or {@code en}, from the metadata sheet
 * @param templateVersion from the metadata sheet
 * @param headers         the template's header texts for that locale, in column order
 * @param rows            non-blank data rows, in file order
 */
public record FixedTemplateWorkbook(String locale, String templateVersion, List<String> headers,
                                    List<ParsedRow> rows) {

    /**
     * @param line   Excel row number (1-based), never renumbered after blank rows are skipped
     * @param values template field → trimmed text ({@code ""} for an empty or refused cell)
     * @param issues cell-type problems found while reading ({@code FORMULA}, {@code TEXT_REQUIRED},
     *               {@code CELL_TYPE})
     */
    public record ParsedRow(int line, Map<String, String> values, List<ImportCellError> issues) {
    }
}

package com.erp.manufacturing.module.dataimport.spreadsheet;

import java.util.List;
import java.util.Map;

/**
 * A spreadsheet after reading, before any mapping.
 *
 * @param headers    header texts in column order, including ones no mapping refers to
 * @param rows       data rows in file order
 */
public record RawSheet(List<String> headers, List<RawRow> rows) {

    /**
     * One data row.
     *
     * @param rowNumber 1-based row number as Excel displays it, so an error points somewhere the user
     *                  can actually click
     * @param cells     header text to cell text, exactly as Excel rendered it
     */
    public record RawRow(int rowNumber, Map<String, String> cells) {
    }
}

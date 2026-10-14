package com.erp.manufacturing.module.dataimport.dto;

import java.util.List;
import java.util.Map;

/**
 * @param line   Excel row number, as the user sees it
 * @param values template field → normalised text (enum labels replaced by canonical names); empty
 *               cells are {@code ""}
 * @param issues empty when the row is valid
 */
public record DataImportBatchRowResponse(int line, Map<String, String> values,
                                         List<DataImportIssueResponse> issues) {
}

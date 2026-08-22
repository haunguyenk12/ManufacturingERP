package com.erp.manufacturing.module.dataimport.dto;

import java.util.List;

/**
 * One target field, as the mapping screen sees it.
 *
 * <p>This is what makes a mapping UI buildable without a sample file: the frontend reads the headers
 * out of the uploaded workbook, reads this list, and lets a user connect the two.
 */
public record ImportFieldResponse(String name,
                                  String label,
                                  String type,
                                  boolean required,
                                  Integer maxLength,
                                  List<String> allowedValues,
                                  String example) {
}

package com.erp.manufacturing.module.dataimport.dto;

import java.util.List;

/** A kind of data that can be imported, with the fields it accepts. */
public record ImportTargetResponse(String type, String label, List<ImportFieldResponse> fields) {
}

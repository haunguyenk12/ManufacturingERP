package com.erp.manufacturing.module.dataimport.dto;

/**
 * One problem in one row of a fixed-template batch (G3).
 *
 * @param field   template field ({@code code}, {@code unit}, {@code contact}...)
 * @param column  Excel column letter of that field in the template
 * @param code    {@code ImportErrorCode} name — the client localises on this, never on {@code message}
 * @param message English explanation from the server
 */
public record DataImportIssueResponse(String field, String column, String code, String message) {
}

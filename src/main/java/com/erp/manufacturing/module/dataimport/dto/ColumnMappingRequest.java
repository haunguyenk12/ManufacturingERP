package com.erp.manufacturing.module.dataimport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One column wired to one field.
 *
 * <p>Bean validation covers only shape. Whether {@code targetField} exists, and whether each
 * transform token parses, is checked against the target descriptor when the profile is saved —
 * neither question can be answered by an annotation.
 *
 * @param transforms   pipeline tokens applied left to right, e.g. {@code ["TRIM","UPPER"]} or
 *                     {@code ["PAD_LEFT_ZERO:6"]}
 * @param defaultValue substituted when the cell is empty; {@code null} leaves the value missing
 */
public record ColumnMappingRequest(

        @NotBlank
        @Size(max = 255)
        String sourceHeader,

        @NotBlank
        @Size(max = 100)
        String targetField,

        List<String> transforms,

        @Size(max = 255)
        String defaultValue) {
}

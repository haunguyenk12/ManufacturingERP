package com.erp.manufacturing.module.dataimport.domain;

import java.util.List;

/**
 * One column of a customer's sheet wired to one field of a target, stored as JSON inside
 * {@code import_profiles.mappings}.
 *
 * <p>This record is why a file whose headers do not match the system is a data problem rather than a
 * code problem: a new customer means a new profile row, not a new class.
 *
 * <p>There is deliberately no {@code required} flag here. Whether a field is mandatory belongs to the
 * target descriptor — it is a property of our schema — and letting a profile relax it would allow a
 * mapping to talk the importer out of a rule the system actually enforces.
 *
 * @param sourceHeader header text exactly as it appears in the sheet
 * @param targetField  field name from the target descriptor
 * @param transforms   pipeline tokens, applied left to right (see {@link TransformStep#parse})
 * @param defaultValue used when the cell is absent or blank; {@code null} means "leave it missing"
 */
public record ColumnMapping(String sourceHeader,
                            String targetField,
                            List<String> transforms,
                            String defaultValue) {

    public ColumnMapping {
        transforms = transforms == null ? List.of() : List.copyOf(transforms);
    }

    /** Runs the pipeline, falling back to {@code defaultValue} when the sheet had nothing to offer. */
    public String extract(String rawValue) {
        String value = rawValue;
        for (String token : transforms) {
            value = TransformStep.parse(token).apply(value);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}

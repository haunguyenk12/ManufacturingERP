package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;

import java.util.List;

/**
 * What the system will accept in one target field.
 *
 * <p>These descriptors are the half of the contract the customer cannot change: they are derived from
 * our own request DTOs ({@code ItemCreateRequest} and friends). A profile may decide which column
 * feeds a field and how the text is cleaned up, but not whether a required field can be skipped or
 * what an enum is allowed to contain.
 *
 * <p>Hand-written rather than reflected off the DTO on purpose. Reflection would give the field names
 * for free but not {@code description}, {@code example} or a Vietnamese-facing label, and it would
 * silently change the public API of the importer whenever someone reordered a record component.
 * {@code ImportTargetRegistryTest} is what keeps the two in step.
 *
 * @param name         field name, as used in a mapping's {@code targetField}
 * @param label        human label for the template and the mapping screen
 * @param type         how the value is checked
 * @param required     whether a row without this value is an error
 * @param maxLength    for {@link ImportFieldType#STRING}; {@code null} when unbounded
 * @param allowedValues for {@link ImportFieldType#ENUM}; empty otherwise
 * @param example      sample value written into the downloadable template
 */
public record ImportFieldDescriptor(String name,
                                    String label,
                                    ImportFieldType type,
                                    boolean required,
                                    Integer maxLength,
                                    List<String> allowedValues,
                                    String example) {

    public ImportFieldDescriptor {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }

    public static ImportFieldDescriptor string(String name, String label, boolean required,
                                               Integer maxLength, String example) {
        return new ImportFieldDescriptor(name, label, ImportFieldType.STRING, required,
                maxLength, List.of(), example);
    }

    public static ImportFieldDescriptor bool(String name, String label, String example) {
        return new ImportFieldDescriptor(name, label, ImportFieldType.BOOLEAN, false,
                null, List.of(), example);
    }

    public static ImportFieldDescriptor enumeration(String name, String label, boolean required,
                                                    List<String> allowedValues, String example) {
        return new ImportFieldDescriptor(name, label, ImportFieldType.ENUM, required,
                null, allowedValues, example);
    }

    public static ImportFieldDescriptor decimal(String name, String label, boolean required, String example) {
        return new ImportFieldDescriptor(name, label, ImportFieldType.DECIMAL, required,
                null, List.of(), example);
    }

    public static ImportFieldDescriptor date(String name, String label, boolean required, String example) {
        return new ImportFieldDescriptor(name, label, ImportFieldType.DATE, required,
                null, List.of(), example);
    }

    /**
     * Checks one already-transformed value against this field.
     *
     * <p>Generic rules only — required, length, enum membership, boolean shape. Anything that needs
     * to look at another field, another row or the database belongs to the target handler.
     *
     * @param sourceHeader column the value came from, so the error can point at the user's own header
     * @return the problem, or {@code null} when the value is acceptable
     */
    public ImportCellError check(String sourceHeader, String value) {
        boolean missing = value == null || value.isBlank();
        if (missing) {
            return required
                    ? ImportCellError.of(sourceHeader, name, ImportErrorCode.REQUIRED_MISSING,
                            "Column '" + label + "' is required but the cell is empty")
                    : null;
        }

        if (maxLength != null && value.length() > maxLength) {
            return ImportCellError.of(sourceHeader, name, ImportErrorCode.TOO_LONG,
                    "Column '" + label + "' allows at most " + maxLength
                            + " characters, got " + value.length());
        }

        return switch (type) {
            case ENUM -> allowedValues.contains(value)
                    ? null
                    : ImportCellError.of(sourceHeader, name, ImportErrorCode.NOT_ALLOWED_VALUE,
                            "Column '" + label + "' must be one of " + allowedValues + ", got '" + value + "'");
            case BOOLEAN -> "true".equals(value) || "false".equals(value)
                    ? null
                    : ImportCellError.of(sourceHeader, name, ImportErrorCode.INVALID_FORMAT,
                            "Column '" + label + "' must be a yes/no value, got '" + value
                                    + "' (add the BOOLEAN_VN transform to the mapping)");
            case DECIMAL -> isDecimal(value)
                    ? null
                    : ImportCellError.of(sourceHeader, name, ImportErrorCode.INVALID_FORMAT,
                            "Column '" + label + "' must be a decimal number, got '" + value + "'");
            case DATE -> isIsoDate(value)
                    ? null
                    : ImportCellError.of(sourceHeader, name, ImportErrorCode.INVALID_FORMAT,
                            "Column '" + label + "' must be an ISO date, got '" + value + "'");
            case STRING -> null;
        };
    }

    private boolean isDecimal(String value) {
        try {
            new java.math.BigDecimal(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isIsoDate(String value) {
        try {
            java.time.LocalDate.parse(value);
            return true;
        } catch (java.time.format.DateTimeParseException exception) {
            return false;
        }
    }
}

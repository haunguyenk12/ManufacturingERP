package com.erp.manufacturing.module.dataimport.domain;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;

/**
 * One entry of a mapping's transform pipeline, written in a profile as a compact token
 * ({@code "TRIM"}, {@code "PAD_LEFT_ZERO:6"}) so a profile stays readable to whoever writes it.
 */
public record TransformStep(CellTransform type, String argument) {

    private static final char SEPARATOR = ':';

    /**
     * Parses one token, rejecting anything a transform cannot run with — an unknown name, a missing
     * argument, or an argument that is not a positive width.
     *
     * <p>Called when a profile is saved, not when a file is validated: a broken pipeline is a
     * configuration mistake to surface immediately, not a per-row error to repeat a thousand times.
     */
    public static TransformStep parse(String token) {
        String trimmed = token == null ? "" : token.trim();
        if (trimmed.isEmpty()) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Transform token must not be blank");
        }

        int separator = trimmed.indexOf(SEPARATOR);
        int openParenthesis = trimmed.indexOf('(');
        boolean parenthesised = separator < 0 && openParenthesis > 0 && trimmed.endsWith(")");
        String name = parenthesised
                ? trimmed.substring(0, openParenthesis).trim()
                : (separator < 0 ? trimmed : trimmed.substring(0, separator)).trim();
        String argument = parenthesised
                ? trimmed.substring(openParenthesis + 1, trimmed.length() - 1).trim()
                : (separator < 0 ? null : trimmed.substring(separator + 1).trim());

        CellTransform type;
        try {
            type = CellTransform.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Unknown transform: " + name);
        }

        if (type.requiresArgument()) {
            if (argument == null || argument.isEmpty()) {
                throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                        "Transform " + type + " requires an argument, e.g. " + type + ":6");
            }
            validateArgument(type, argument);
        } else if (argument != null && !argument.isEmpty()) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Transform " + type + " takes no argument, got: " + argument);
        }

        return new TransformStep(type, argument);
    }

    private static void validateArgument(CellTransform type, String argument) {
        switch (type) {
            case PAD_LEFT_ZERO -> {
                int width;
                try {
                    width = Integer.parseInt(argument);
                } catch (NumberFormatException e) {
                    throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                            "Transform " + type + " expects a whole number, got: " + argument);
                }
                if (width <= 0) {
                    throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                            "Transform " + type + " expects a positive number, got: " + argument);
                }
            }
            case DATE_FORMAT -> {
                try {
                    java.time.format.DateTimeFormatter.ofPattern(argument);
                } catch (IllegalArgumentException exception) {
                    throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                            "Transform DATE_FORMAT has an invalid pattern: " + argument);
                }
            }
            case VALUE_DICT -> {
                boolean invalid = java.util.Arrays.stream(argument.split(";"))
                        .anyMatch(entry -> entry.split("=", 2).length != 2);
                if (invalid) {
                    throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                            "Transform VALUE_DICT expects source=target pairs separated by semicolons");
                }
            }
            default -> {
                // No other current transform accepts an argument.
            }
        }
    }

    public String apply(String value) {
        return type.apply(value, argument);
    }

    /** Round-trips {@link #parse(String)} so a profile can be stored and shown in the same form. */
    public String token() {
        return argument == null ? type.name() : type.name() + SEPARATOR + argument;
    }
}

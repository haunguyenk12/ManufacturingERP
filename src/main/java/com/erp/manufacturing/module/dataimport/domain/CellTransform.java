package com.erp.manufacturing.module.dataimport.domain;

import java.text.Normalizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * The closed set of cleanups a mapping may apply to one cell before the value reaches a target field.
 *
 * <p>These are pathologies of Vietnamese business spreadsheets, not of any one customer's file, which
 * is why the catalogue can be written before a sample file exists. Every constant is a pure
 * {@code String -> String} function, so each one is pinned by a unit test with a real value rather
 * than by an integration test.
 *
 * <p>This file carries literal Vietnamese words, and it is the one place where a mojibake round-trip
 * would be invisible: the constants would still compile and still look plausible in a diff, they
 * would simply never match anything. This repository has been bitten by a UTF-8 round-trip before
 * ({@code CLAUDE.md} §0.44), so the guard is not a comment but {@code CellTransformTest}, which feeds
 * the real characters in and asserts the real result out. The two non-breaking spaces are written as
 * code points for the same reason — as literals they are indistinguishable from ordinary spaces.
 *
 * <p>The numeric/date transforms are present even though the first target is ITEM: profiles are data
 * and the catalog is part of the framework contract. Keeping the pure conversions here lets later
 * targets reuse them without changing the profile format.
 */
public enum CellTransform {

    /** Removes leading/trailing whitespace. */
    TRIM(false) {
        @Override
        String applyTo(String value, String argument) {
            return value.trim();
        }
    },

    /**
     * Turns non-breaking spaces (U+00A0, U+202F) into ordinary ones. Excel picks these up from pasted
     * web content and they survive {@link #TRIM}, so a code looks identical on screen yet never
     * matches anything.
     */
    STRIP_NBSP(false) {
        @Override
        String applyTo(String value, String argument) {
            return value.replace(NO_BREAK_SPACE, ' ').replace(NARROW_NO_BREAK_SPACE, ' ');
        }
    },

    /** Collapses runs of whitespace (including the tabs and newlines a wrapped cell carries). */
    COLLAPSE_SPACES(false) {
        @Override
        String applyTo(String value, String argument) {
            return value.replaceAll("\\s+", " ");
        }
    },

    /**
     * Normalises Vietnamese diacritics to composed form. A word typed on macOS is decomposed (NFD)
     * and compares unequal to the same word typed on Windows (NFC) despite looking identical.
     */
    NFC(false) {
        @Override
        String applyTo(String value, String argument) {
            return Normalizer.normalize(value, Normalizer.Form.NFC);
        }
    },

    UPPER(false) {
        @Override
        String applyTo(String value, String argument) {
            return value.toUpperCase(Locale.ROOT);
        }
    },

    LOWER(false) {
        @Override
        String applyTo(String value, String argument) {
            return value.toLowerCase(Locale.ROOT);
        }
    },

    /**
     * Restores leading zeros Excel dropped by storing a code as a number: {@code PAD_LEFT_ZERO:6}
     * turns {@code 7} back into {@code 000007}. Longer values are left alone.
     */
    PAD_LEFT_ZERO(true) {
        @Override
        String applyTo(String value, String argument) {
            int width = Integer.parseInt(argument);
            if (value.length() >= width) {
                return value;
            }
            return "0".repeat(width - value.length()) + value;
        }
    },

    /**
     * Maps the words a Vietnamese sheet uses for yes/no onto {@code "true"}/{@code "false"}.
     *
     * <p>An unrecognised word is returned unchanged on purpose: the boolean field validator then
     * reports {@code INVALID_FORMAT} naming the actual cell content, which is far more useful than
     * silently defaulting to {@code false}. A blank cell is likewise left blank, so the mapping's
     * {@code defaultValue} still gets its turn.
     */
    BOOLEAN_VN(false) {
        @Override
        String applyTo(String value, String argument) {
            String key = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
            if (TRUE_WORDS.contains(key)) {
                return "true";
            }
            if (FALSE_WORDS.contains(key)) {
                return "false";
            }
            return value;
        }
    },

    /** Vietnamese/continental number: {@code 1.234,56 -> 1234.56}. */
    DECIMAL_COMMA(false) {
        @Override
        String applyTo(String value, String argument) {
            String canonical = value.trim().replace(".", "").replace(',', '.');
            return new BigDecimal(canonical).toPlainString();
        }
    },

    /** English number with comma grouping: {@code 1,234.56 -> 1234.56}. */
    DECIMAL_DOT(false) {
        @Override
        String applyTo(String value, String argument) {
            return new BigDecimal(value.trim().replace(",", "")).toPlainString();
        }
    },

    /** Excel's 1900 date system, including its historical leap-year compatibility offset. */
    EXCEL_DATE(false) {
        @Override
        String applyTo(String value, String argument) {
            long days = new BigDecimal(value.trim()).setScale(0, RoundingMode.FLOOR).longValueExact();
            return LocalDate.of(1899, 12, 30).plusDays(days).toString();
        }
    },

    /** Parses a date with the supplied Java pattern and emits the stable ISO {@code yyyy-MM-dd}. */
    DATE_FORMAT(true) {
        @Override
        String applyTo(String value, String argument) {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern(argument)).toString();
        }
    },

    /**
     * Exact value dictionary. Argument format is {@code source=target;source 2=target2}; values not
     * listed pass through so the target-field validator can report the original text.
     */
    VALUE_DICT(true) {
        @Override
        String applyTo(String value, String argument) {
            for (String entry : argument.split(";")) {
                String[] pair = entry.split("=", 2);
                if (pair.length == 2 && pair[0].trim().equals(value.trim())) {
                    return pair[1].trim();
                }
            }
            return value;
        }
    };

    /** "co"/"có", "dung"/"đúng", plus the ASCII markers people type in a checkbox column. */
    private static final Set<String> TRUE_WORDS = Set.of(
            "co", "có", "dung", "đúng", "x", "1", "true", "yes", "y");

    /** "khong"/"không", "sai", and the usual ASCII negatives. */
    private static final Set<String> FALSE_WORDS = Set.of(
            "khong", "không", "sai", "0", "false", "no", "n");

    private static final char NO_BREAK_SPACE = 0x00A0;
    private static final char NARROW_NO_BREAK_SPACE = 0x202F;

    private final boolean requiresArgument;

    CellTransform(boolean requiresArgument) {
        this.requiresArgument = requiresArgument;
    }

    public boolean requiresArgument() {
        return requiresArgument;
    }

    /**
     * Applies this transform. {@code null} passes straight through so a blank cell keeps meaning
     * "absent" rather than becoming the empty string somewhere in the middle of a pipeline.
     */
    public String apply(String value, String argument) {
        return value == null ? null : applyTo(value, argument);
    }

    abstract String applyTo(String value, String argument);
}

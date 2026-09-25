package com.erp.manufacturing.common.exception;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a database constraint violation into the {@link ErrorCode} that describes it (EH-3).
 *
 * <p>Before this class, {@code GlobalExceptionHandler} inspected the driver message with a hardcoded
 * {@code detail.contains(...)} for one lot constraint and answered every other violation with
 * {@code RESOURCE_ALREADY_EXISTS} (409). That is the wrong answer for the majority of them: a foreign
 * key, a {@code NOT NULL} or a {@code CHECK} failure is not "this already exists", and a client told
 * 409 has no way to tell a genuine duplicate from a malformed request. Worse, the {@code contains}
 * chain had to be edited by hand every time a migration added a constraint worth naming.
 *
 * <h3>How a violation is classified</h3>
 * <ol>
 *   <li>The constraint name is read out of the driver message ({@code ... constraint "the_name"}).</li>
 *   <li>{@link #KNOWN} is consulted first — constraints this system has a specific answer for.</li>
 *   <li>Otherwise the name's prefix decides: this schema names unique constraints and unique indexes
 *       {@code uk_*} throughout (see {@code src/main/resources/db/migration}), so those stay
 *       {@code RESOURCE_ALREADY_EXISTS}. Everything else — {@code chk_*}, {@code fk_*}, an unnamed
 *       {@code NOT NULL} — is a rule the payload broke, which is {@code BUSINESS_RULE_VIOLATION}
 *       (422).</li>
 * </ol>
 *
 * <p>The constraint name never leaves the server: it is logged, not returned. Client messages stay
 * generic for the same reason the catch-all does not echo exception text.
 */
final class DataIntegrityErrorMapper {

    /** Matches the constraint name PostgreSQL puts in unique / check / foreign-key violations. */
    private static final Pattern CONSTRAINT_NAME = Pattern.compile("constraint \"([^\"]+)\"");

    /** Prefix this schema gives every unique constraint and unique index. */
    private static final String UNIQUE_PREFIX = "uk_";

    /**
     * Constraints with an answer better than the prefix rule can give.
     *
     * <p>Deliberately short. A constraint earns an entry here by having actually confused someone,
     * not by existing — inventing a distinct answer for each of the ~170 named constraints in the
     * schema would give clients more codes than anyone can branch on
     * ({@code .claude/rules/coding-rules.md} §11.5).
     */
    private static final Map<String, Mapping> KNOWN = Map.of(
            // Two constraints, one meaning: the lot code is taken, or it is blank/untrimmed and
            // would collide with one that is. Both were already special-cased before EH-3.
            "uk_inventory_lots_item_code", new Mapping(
                    BusinessErrorCode.LOT_CODE_ALREADY_EXISTS,
                    BusinessErrorCode.LOT_CODE_ALREADY_EXISTS.message()),
            "chk_inventory_lots_code_trimmed", new Mapping(
                    BusinessErrorCode.LOT_CODE_ALREADY_EXISTS,
                    BusinessErrorCode.LOT_CODE_ALREADY_EXISTS.message()),
            // CLAUDE.md §0.40: a full-replacement PATCH that reuses line numbers 1..N used to hit this
            // and the client saw only "Data constraint violation", which cost a round of diagnosis.
            // The ordering bug behind it is fixed; this message is here so a relapse names itself.
            "uk_sales_order_lines_order_line_no", new Mapping(
                    ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Sales order line numbers must be unique within the order"));

    private DataIntegrityErrorMapper() {
    }

    /**
     * @param detail the driver's message, i.e. {@code ex.getMostSpecificCause().getMessage()};
     *               may be {@code null}
     */
    static Mapping resolve(String detail) {
        String constraintName = constraintNameIn(detail);
        if (constraintName != null) {
            Mapping known = KNOWN.get(constraintName);
            if (known != null) {
                return known;
            }
            if (constraintName.startsWith(UNIQUE_PREFIX)) {
                return duplicate();
            }
            return violatedRule();
        }
        // No name to go on. PostgreSQL still says "duplicate key value violates unique constraint"
        // when an index has no application-chosen name, so the wording is a second, weaker signal.
        if (detail != null && detail.toLowerCase().contains("duplicate key")) {
            return duplicate();
        }
        return violatedRule();
    }

    /** The constraint name in the driver message, or {@code null} when it carries none. */
    static String constraintNameIn(String detail) {
        if (detail == null) {
            return null;
        }
        Matcher matcher = CONSTRAINT_NAME.matcher(detail);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Mapping duplicate() {
        return new Mapping(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Data constraint violation");
    }

    private static Mapping violatedRule() {
        return new Mapping(BusinessErrorCode.BUSINESS_RULE_VIOLATION,
                "Request violates a data constraint");
    }

    record Mapping(ErrorCode code, String message) {
    }
}

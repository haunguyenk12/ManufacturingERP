package com.erp.manufacturing.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EH-3. The classification rule lives here rather than in the handler so it can be exercised against
 * the exact driver strings PostgreSQL produces, without a servlet in the way.
 *
 * <p>The 409-vs-422 split it implements is the one stated in
 * {@code .claude/rules/error-handling.md} §5.3: 409 is "right data, wrong state", 422 is "wrong data".
 * A foreign key or check failure is the second, and answering 409 for it — which is what happened
 * before — tells the client to retry something that will never succeed.
 */
@DisplayName("DataIntegrityErrorMapper - constraint violation classification")
class DataIntegrityErrorMapperTest {

    @Test
    @DisplayName("a unique violation is a duplicate (409)")
    void uniqueViolation_isADuplicate() {
        DataIntegrityErrorMapper.Mapping mapping = DataIntegrityErrorMapper.resolve(
                "ERROR: duplicate key value violates unique constraint \"uk_companies_code\"");

        assertThat(mapping.code()).isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS);
        assertThat(mapping.code().status().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("a check violation is a broken business rule (422), not a duplicate")
    void checkViolation_isABrokenRule() {
        DataIntegrityErrorMapper.Mapping mapping = DataIntegrityErrorMapper.resolve(
                "ERROR: new row for relation \"items\" violates check constraint "
                        + "\"chk_items_tracking_exclusive\"");

        assertThat(mapping.code()).isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION);
        assertThat(mapping.code().status().value()).isEqualTo(422);
    }

    @Test
    @DisplayName("a foreign-key violation is a broken business rule (422), not a duplicate")
    void foreignKeyViolation_isABrokenRule() {
        DataIntegrityErrorMapper.Mapping mapping = DataIntegrityErrorMapper.resolve(
                "ERROR: insert or update on table \"bom_lines\" violates foreign key constraint "
                        + "\"fk_bom_lines_component_item\"");

        assertThat(mapping.code()).isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION);
    }

    /** A {@code NOT NULL} failure carries no constraint name at all, so the name regex finds nothing. */
    @Test
    @DisplayName("a not-null violation carries no constraint name and still lands on 422")
    void notNullViolation_hasNoConstraintNameAndLandsOn422() {
        String detail = "ERROR: null value in column \"work_center_code\" of relation "
                + "\"routing_operations\" violates not-null constraint";

        assertThat(DataIntegrityErrorMapper.constraintNameIn(detail)).isNull();
        assertThat(DataIntegrityErrorMapper.resolve(detail).code())
                .isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    @DisplayName("KNOWN wins over the prefix rule")
    void knownConstraint_winsOverThePrefixRule() {
        DataIntegrityErrorMapper.Mapping lotCode = DataIntegrityErrorMapper.resolve(
                "ERROR: duplicate key value violates unique constraint \"uk_inventory_lots_item_code\"");
        DataIntegrityErrorMapper.Mapping trimmed = DataIntegrityErrorMapper.resolve(
                "ERROR: new row violates check constraint \"chk_inventory_lots_code_trimmed\"");

        // Both would fall on opposite sides of the prefix rule; the map overrides both to one answer.
        assertThat(lotCode.code()).isEqualTo(BusinessErrorCode.LOT_CODE_ALREADY_EXISTS);
        assertThat(trimmed.code()).isEqualTo(BusinessErrorCode.LOT_CODE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("a mapped constraint can carry its own message")
    void mappedConstraint_carriesItsOwnMessage() {
        DataIntegrityErrorMapper.Mapping mapping = DataIntegrityErrorMapper.resolve(
                "ERROR: duplicate key value violates unique constraint "
                        + "\"uk_sales_order_lines_order_line_no\"");

        assertThat(mapping.code()).isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS);
        assertThat(mapping.message()).contains("line numbers must be unique");
    }

    /**
     * A driver that names no constraint but does say "duplicate key" is still a duplicate. Without
     * this second signal an unnamed unique index would be answered 422 and the client told to fix a
     * payload that is not the problem.
     */
    @Test
    @DisplayName("an unnamed duplicate is still recognised from the wording")
    void unnamedDuplicate_isRecognisedFromTheWording() {
        assertThat(DataIntegrityErrorMapper.resolve("duplicate key value violates unique constraint").code())
                .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("a null or unrecognisable detail falls back to 422, not to a phantom duplicate")
    void unrecognisableDetail_fallsBackTo422() {
        assertThat(DataIntegrityErrorMapper.resolve(null).code())
                .isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION);
        assertThat(DataIntegrityErrorMapper.resolve("something the driver phrased differently").code())
                .isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION);
    }
}

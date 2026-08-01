package com.erp.manufacturing.common.web;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PlantContextResolver tests")
class PlantContextResolverTest {

    private final PlantContextResolver resolver = new PlantContextResolver();

    @Test
    @DisplayName("Header matching the request's plant passes")
    void matchingHeader_passes() {
        UUID plantId = UUID.randomUUID();
        assertThatCode(() -> resolver.ensureMatches(plantId.toString(), plantId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Header naming a different plant is rejected with STATE_CONFLICT")
    void mismatchedHeader_throwsStateConflict() {
        assertThatThrownBy(() -> resolver.ensureMatches(UUID.randomUUID().toString(), UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    @Test
    @DisplayName("Malformed header is a 400 FIELD_FORMAT_INVALID, not a 500")
    void malformedHeader_throwsFieldFormatInvalid() {
        assertThatThrownBy(() -> resolver.ensureMatches("not-a-uuid", UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.FIELD_FORMAT_INVALID));
    }

    @Test
    @DisplayName("Absent or blank header is allowed so existing callers keep working")
    void absentHeader_isAllowed() {
        UUID plantId = UUID.randomUUID();
        assertThatCode(() -> resolver.ensureMatches(null, plantId)).doesNotThrowAnyException();
        assertThatCode(() -> resolver.ensureMatches("   ", plantId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Surrounding whitespace in the header is tolerated")
    void headerWithWhitespace_isTrimmed() {
        UUID plantId = UUID.randomUUID();
        assertThatCode(() -> resolver.ensureMatches("  " + plantId + "  ", plantId))
                .doesNotThrowAnyException();
    }
}

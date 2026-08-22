package com.erp.manufacturing.module.dataimport.domain;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CellTransformTest {

    @Test
    void textTransforms_handleVietnameseSpreadsheetPathologies() {
        String decomposed = Normalizer.normalize("Nguyên vật liệu", Normalizer.Form.NFD);

        assertThat(CellTransform.TRIM.apply("  VT-001  ", null)).isEqualTo("VT-001");
        assertThat(CellTransform.STRIP_NBSP.apply("Mã\u00a0vật\u202f tư", null))
                .isEqualTo("Mã vật  tư");
        assertThat(CellTransform.COLLAPSE_SPACES.apply("Khung\t xe\n  đạp", null))
                .isEqualTo("Khung xe đạp");
        assertThat(CellTransform.NFC.apply(decomposed, null))
                .isEqualTo("Nguyên vật liệu")
                .isNotEqualTo(decomposed);
        assertThat(CellTransform.UPPER.apply("vt-đỏ", null)).isEqualTo("VT-ĐỎ");
        assertThat(CellTransform.LOWER.apply("VT-ĐỎ", null)).isEqualTo("vt-đỏ");
    }

    @Test
    void padLeftZero_preservesLongValuesAndPadsShortOnes() {
        assertThat(CellTransform.PAD_LEFT_ZERO.apply("7", "3")).isEqualTo("007");
        assertThat(CellTransform.PAD_LEFT_ZERO.apply("1234", "3")).isEqualTo("1234");
    }

    @Test
    void booleanVn_recognisesComposedVietnameseAndCheckboxMarkers() {
        assertThat(CellTransform.BOOLEAN_VN.apply(" Có ", null)).isEqualTo("true");
        assertThat(CellTransform.BOOLEAN_VN.apply("KHÔNG", null)).isEqualTo("false");
        assertThat(CellTransform.BOOLEAN_VN.apply("x", null)).isEqualTo("true");
        assertThat(CellTransform.BOOLEAN_VN.apply("khác", null)).isEqualTo("khác");
    }

    @Test
    void transformStep_validatesArgumentsAndRoundTripsToken() {
        TransformStep step = TransformStep.parse(" pad_left_zero(6) ");
        assertThat(step.token()).isEqualTo("PAD_LEFT_ZERO:6");
        assertThat(step.apply("7")).isEqualTo("000007");

        assertThatThrownBy(() -> TransformStep.parse("PAD_LEFT_ZERO"))
                .hasMessageContaining("requires an argument");
        assertThatThrownBy(() -> TransformStep.parse("TRIM:2"))
                .hasMessageContaining("takes no argument");
        assertThatThrownBy(() -> TransformStep.parse("NOT_REAL"))
                .hasMessageContaining("Unknown transform");
    }

    @Test
    void numericDateAndDictionaryTransformsProduceCanonicalValues() {
        assertThat(TransformStep.parse("DECIMAL_COMMA").apply("1.234,56")).isEqualTo("1234.56");
        assertThat(TransformStep.parse("DECIMAL_DOT").apply("1,234.56")).isEqualTo("1234.56");
        assertThat(TransformStep.parse("EXCEL_DATE").apply("45292")).isEqualTo("2024-01-01");
        assertThat(TransformStep.parse("DATE_FORMAT(dd/MM/yyyy)").apply("15/08/2026"))
                .isEqualTo("2026-08-15");
        assertThat(TransformStep.parse(
                "VALUE_DICT:Nguyên vật liệu=RAW_MATERIAL;Thành phẩm=FINISHED_GOOD")
                .apply("Nguyên vật liệu")).isEqualTo("RAW_MATERIAL");
    }

    @Test
    void numericAndDictionaryTransformsRejectMalformedInputOrConfiguration() {
        assertThatThrownBy(() -> TransformStep.parse("DECIMAL_COMMA").apply("1,2,3"))
                .isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> TransformStep.parse("DATE_FORMAT(yyyy-MM-dd])"))
                .hasMessageContaining("invalid pattern");
        assertThatThrownBy(() -> TransformStep.parse("VALUE_DICT:missing-equals"))
                .hasMessageContaining("source=target pairs");
    }
}

package com.erp.manufacturing.module.dataimport.spreadsheet;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedTemplateReaderTest {

    private final FixedTemplateReader reader = new FixedTemplateReader();

    @Test
    void read_validItemsVi_keepsExcelLineNumbersAcrossBlankRows_andNormalisesCodeAndUnit() {
        byte[] file = TemplateWorkbooks.items("vi")
                .styledEmptyRow(2)
                .row(3, "  gd3-rm-0001 ", "Nguyên liệu minh họa", "Nguyên vật liệu", "kg", "Có")
                .row(8, "GD3-RM-0002", "Tên", "Thành phẩm", "KG", "Không")
                .styledEmptyRow(9)
                .build();

        FixedTemplateWorkbook workbook = reader.read(file, DataImportKind.ITEMS, 500);

        assertThat(workbook.locale()).isEqualTo("vi");
        assertThat(workbook.templateVersion()).isEqualTo("omniplant-import-v1");
        assertThat(workbook.rows()).extracting(FixedTemplateWorkbook.ParsedRow::line).containsExactly(3, 8);
        FixedTemplateWorkbook.ParsedRow first = workbook.rows().get(0);
        assertThat(first.values())
                .containsEntry("code", "GD3-RM-0001")
                .containsEntry("unit", "KG")
                .containsEntry("type", "Nguyên vật liệu")
                .containsEntry("tracking", "Có");
        assertThat(first.issues()).isEmpty();
    }

    @Test
    void read_textCodeAndPhoneWithLeadingZeros_areKeptVerbatim() {
        byte[] file = TemplateWorkbooks.partners("suppliers", "en")
                .row(2, "0007", "Partner", "", "", "0000123456", "", "")
                .build();

        FixedTemplateWorkbook.ParsedRow row = reader.read(file, DataImportKind.SUPPLIERS, 500).rows().get(0);

        assertThat(row.values()).containsEntry("code", "0007").containsEntry("phone", "0000123456");
        assertThat(row.issues()).isEmpty();
    }

    @Test
    void read_numericCodeAndPhone_areTextRequired_notGuessed() {
        byte[] file = TemplateWorkbooks.partners("customers", "en")
                .row(2, null, "Partner")
                .numberCell(2, 0, 7)
                .numberCell(2, 4, 123456)
                .build();

        FixedTemplateWorkbook.ParsedRow row = reader.read(file, DataImportKind.CUSTOMERS, 500).rows().get(0);

        assertThat(row.issues()).extracting(ImportCellError::targetField, ImportCellError::column, ImportCellError::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("code", "A", ImportErrorCode.TEXT_REQUIRED),
                        org.assertj.core.groups.Tuple.tuple("phone", "E", ImportErrorCode.TEXT_REQUIRED));
        assertThat(row.values()).containsEntry("code", "").containsEntry("phone", "");
    }

    @Test
    void read_formulaDateAndBooleanCells_areRowIssues_andNeverEvaluated() {
        byte[] file = TemplateWorkbooks.partners("customers", "en")
                .row(2, "C-1")
                .formulaCell(2, 1, "\"Na\"&\"me\"")
                .dateCell(2, 2, LocalDate.of(2026, 10, 14))
                .booleanCell(2, 3, true)
                .build();

        FixedTemplateWorkbook.ParsedRow row = reader.read(file, DataImportKind.CUSTOMERS, 500).rows().get(0);

        assertThat(row.issues()).extracting(ImportCellError::targetField, ImportCellError::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("name", ImportErrorCode.FORMULA),
                        org.assertj.core.groups.Tuple.tuple("contact", ImportErrorCode.CELL_TYPE),
                        org.assertj.core.groups.Tuple.tuple("email", ImportErrorCode.CELL_TYPE));
        assertThat(row.values()).containsEntry("name", "");
    }

    @Test
    void read_exactlyMaxRows_isAccepted_oneMoreIsRowLimit() {
        TemplateWorkbooks fiveHundred = TemplateWorkbooks.partners("customers", "en");
        for (int line = 2; line <= 501; line++) {
            fiveHundred.row(line, "C-" + line, "Name");
        }
        assertThat(reader.read(fiveHundred.build(), DataImportKind.CUSTOMERS, 500).rows()).hasSize(500);

        byte[] tooMany = fiveHundred.row(502, "C-502", "Name").build();
        assertCode(() -> reader.read(tooMany, DataImportKind.CUSTOMERS, 500), ValidationErrorCode.ROW_LIMIT);
    }

    @Test
    void read_onlyStyledEmptyRows_isEmptyFile() {
        byte[] file = TemplateWorkbooks.items("en").styledEmptyRow(2).styledEmptyRow(501).build();

        assertCode(() -> reader.read(file, DataImportKind.ITEMS, 500), ValidationErrorCode.EMPTY_FILE);
    }

    @Test
    void read_templateMismatches_areTemplateErrors() {
        assertCode(() -> reader.read(TemplateWorkbooks.items("vi").withoutMetadata().row(2, "A").build(),
                DataImportKind.ITEMS, 500), ValidationErrorCode.TEMPLATE);
        assertCode(() -> reader.read(TemplateWorkbooks.items("vi").version("omniplant-import-v0").row(2, "A").build(),
                DataImportKind.ITEMS, 500), ValidationErrorCode.TEMPLATE);
        // A customers file uploaded as items.
        assertCode(() -> reader.read(TemplateWorkbooks.partners("customers", "vi").row(2, "A").build(),
                DataImportKind.ITEMS, 500), ValidationErrorCode.TEMPLATE);
        // English headers under Vietnamese metadata.
        assertCode(() -> reader.read(TemplateWorkbooks.items("vi").headers(TemplateWorkbooks.ITEM_HEADERS_EN)
                .row(2, "A").build(), DataImportKind.ITEMS, 500), ValidationErrorCode.TEMPLATE);
        // An extra data column.
        assertCode(() -> reader.read(TemplateWorkbooks.items("en").headers(List.of(
                "Code", "Name", "Item type", "Unit of measure", "Lot tracking", "Price")).row(2, "A").build(),
                DataImportKind.ITEMS, 500), ValidationErrorCode.TEMPLATE);
    }

    @Test
    void read_notAnArchive_isFileFormat() {
        byte[] csv = "Code,Name\nA,B\n".getBytes(StandardCharsets.UTF_8);

        assertCode(() -> reader.read(csv, DataImportKind.ITEMS, 500), ValidationErrorCode.FILE_FORMAT);
    }

    @Test
    void read_workbookCarryingAMacroPart_isRefusedBeforePoiOpensIt() throws Exception {
        byte[] withMacro = addEntry(TemplateWorkbooks.items("en").row(2, "A").build(), "xl/vbaProject.bin");

        assertCode(() -> reader.read(withMacro, DataImportKind.ITEMS, 500), ValidationErrorCode.FILE_FORMAT);
    }

    @Test
    void read_workbookWithExternalLinks_isRefused() throws Exception {
        byte[] withLink = addEntry(TemplateWorkbooks.items("en").row(2, "A").build(),
                "xl/externalLinks/externalLink1.xml");

        assertCode(() -> reader.read(withLink, DataImportKind.ITEMS, 500), ValidationErrorCode.FILE_FORMAT);
    }

    @Test
    void read_archiveExpandingPastTheLimit_isFileSize() throws Exception {
        byte[] bomb = addEntry(TemplateWorkbooks.items("en").row(2, "A").build(), "docProps/padding.bin",
                new byte[(int) FixedTemplateReader.MAX_EXPANDED_BYTES + 1]);

        assertCode(() -> reader.read(bomb, DataImportKind.ITEMS, 500), ValidationErrorCode.FILE_SIZE);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }

    private static byte[] addEntry(byte[] xlsx, String name) throws Exception {
        return addEntry(xlsx, name, new byte[]{1, 2, 3});
    }

    /** Copies every part of {@code xlsx} and appends one more, as a renamed .xlsm would carry. */
    private static byte[] addEntry(byte[] xlsx, String name, byte[] content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(xlsx));
             ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(in.readAllBytes());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}

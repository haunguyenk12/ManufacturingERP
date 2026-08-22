package com.erp.manufacturing.module.dataimport.spreadsheet;

import com.erp.manufacturing.common.exception.AppException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpreadsheetReaderTest {

    private final SpreadsheetReader reader = new SpreadsheetReader();

    @Test
    void read_normalisesVietnameseHeaders_preservesCellTextAndExcelRowNumbers() throws Exception {
        String nfdHeader = Normalizer.normalize("Mã vật tư", Normalizer.Form.NFD);
        byte[] workbook = workbook(book -> {
            var sheet = book.createSheet("Dữ liệu");
            sheet.createRow(0).createCell(0).setCellValue("Tiêu đề báo cáo");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
            book.createSheet("Danh mục phụ").createRow(0).createCell(0).setCellValue("Không đọc sheet này");
            var header = sheet.createRow(1);
            header.createCell(0).setCellValue("  " + nfdHeader + "\u00a0 ");
            header.createCell(2).setCellValue("Tên hàng");
            var data = sheet.createRow(2);
            data.createCell(0).setCellValue(7);
            var codeStyle = book.createCellStyle();
            codeStyle.setDataFormat(book.createDataFormat().getFormat("000"));
            data.getCell(0).setCellStyle(codeStyle);
            data.createCell(1).setCellValue("PHẢI BỊ BỎ QUA");
            data.createCell(2).setCellValue("  Khung xe  ");
            sheet.createRow(3);
            sheet.createRow(4).createCell(0).setCellValue("VT-002");
        });

        RawSheet result = reader.read(new ByteArrayInputStream(workbook), "Dữ liệu", 1, 2, 10);

        assertThat(result.headers()).containsExactly("Mã vật tư", "Tên hàng");
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).rowNumber()).isEqualTo(3);
        assertThat(result.rows().get(0).cells())
                .containsEntry("Mã vật tư", "007")
                .containsEntry("Tên hàng", "  Khung xe  ")
                .doesNotContainValue("PHẢI BỊ BỎ QUA");
        assertThat(result.rows().get(1).rowNumber()).isEqualTo(5);
    }

    @Test
    void read_rejectsDuplicateHeadersAfterNormalisation() throws Exception {
        byte[] workbook = workbook(book -> {
            var sheet = book.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã vật tư");
            header.createCell(1).setCellValue(" Mã  vật tư ");
        });

        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(workbook), null, 0, 1, 10))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("appears in more than one column");
    }

    @Test
    void read_enforcesConfiguredRowLimit() throws Exception {
        byte[] workbook = workbook(book -> {
            var sheet = book.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Code");
            sheet.createRow(1).createCell(0).setCellValue("A");
            sheet.createRow(2).createCell(0).setCellValue("B");
        });

        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(workbook), null, 0, 1, 1))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("more than 1 data rows");
    }

    private byte[] workbook(WorkbookWriter writer) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            writer.write(workbook);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    @FunctionalInterface
    private interface WorkbookWriter {
        void write(XSSFWorkbook workbook);
    }
}

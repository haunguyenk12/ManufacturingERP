package com.erp.manufacturing.module.dataimport.spreadsheet;

import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.target.ImportFieldDescriptor;
import com.erp.manufacturing.module.dataimport.target.ImportTargetDescriptor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadsheetTemplateWriterTest {

    @Test
    void write_generatesDataAndGuideSheetsFromTheSameDescriptor() throws Exception {
        var descriptor = new ImportTargetDescriptor(ImportTargetType.ITEM, "Item master", List.of(
                ImportFieldDescriptor.string("code", "Mã vật tư", true, 100, "VT-001"),
                ImportFieldDescriptor.enumeration("type", "Loại", true,
                        List.of("RAW_MATERIAL", "FINISHED_GOOD"), "RAW_MATERIAL")));

        byte[] bytes = new SpreadsheetTemplateWriter().write(descriptor);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Data");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Mã vật tư *");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("VT-001");
            assertThat(workbook.getSheetAt(1).getRow(2).getCell(3).getStringCellValue())
                    .isEqualTo("RAW_MATERIAL, FINISHED_GOOD");
        }
    }
}

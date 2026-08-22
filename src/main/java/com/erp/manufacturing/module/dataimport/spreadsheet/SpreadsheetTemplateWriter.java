package com.erp.manufacturing.module.dataimport.spreadsheet;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.dataimport.target.ImportFieldDescriptor;
import com.erp.manufacturing.module.dataimport.target.ImportTargetDescriptor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Generates the blank workbook a company fills in.
 *
 * <p>Worth building early, and worth building before any customer file has been seen: handing over a
 * template turns "we do not know what their columns look like" into "compare their file with a shape
 * we defined". Even when a customer insists on their own export, the template is the reference the
 * mapping screen is built against.
 *
 * <p>Two sheets. The first carries the exact headers a matching profile expects plus one example row,
 * so the file is usable without reading anything. The second explains each column — required or not,
 * maximum length, allowed values — because the alternative is a support conversation per column.
 */
@Component
public class SpreadsheetTemplateWriter {

    static final String DATA_SHEET = "Data";
    static final String GUIDE_SHEET = "Guide";

    public byte[] write(ImportTargetDescriptor descriptor) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = boldStyle(workbook);
            writeDataSheet(workbook, descriptor, headerStyle);
            writeGuideSheet(workbook, descriptor, headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw ExceptionFactory.custom(BusinessErrorCode.BUSINESS_RULE_VIOLATION,
                    "Could not generate the import template", e);
        }
    }

    private void writeDataSheet(Workbook workbook, ImportTargetDescriptor descriptor, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(DATA_SHEET);
        List<ImportFieldDescriptor> fields = descriptor.fields();

        Row header = sheet.createRow(0);
        Row example = sheet.createRow(1);
        for (int column = 0; column < fields.size(); column++) {
            ImportFieldDescriptor field = fields.get(column);
            // The header is the field's label, and the shipped profile maps that label back to the
            // field name. Using the raw field name would make the file read like a database dump.
            Cell headerCell = header.createCell(column);
            headerCell.setCellValue(field.label() + (field.required() ? " *" : ""));
            headerCell.setCellStyle(headerStyle);

            example.createCell(column).setCellValue(field.example() == null ? "" : field.example());
            sheet.setColumnWidth(column, 22 * 256);
        }
    }

    private void writeGuideSheet(Workbook workbook, ImportTargetDescriptor descriptor, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(GUIDE_SHEET);
        String[] columns = {"Column", "Required", "Max length", "Allowed values", "Example"};

        Row header = sheet.createRow(0);
        for (int column = 0; column < columns.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(columns[column]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(column, 28 * 256);
        }

        int rowIndex = 1;
        for (ImportFieldDescriptor field : descriptor.fields()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(field.label());
            row.createCell(1).setCellValue(field.required() ? "Yes" : "No");
            row.createCell(2).setCellValue(field.maxLength() == null ? "" : String.valueOf(field.maxLength()));
            row.createCell(3).setCellValue(String.join(", ", field.allowedValues()));
            row.createCell(4).setCellValue(field.example() == null ? "" : field.example());
        }
    }

    private CellStyle boldStyle(Workbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        return style;
    }
}

package com.erp.manufacturing.module.dataimport.spreadsheet;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds {@code omniplant-import-v1} workbooks in memory, shaped like the FE-generated template
 * (FE_HANDOFF_GD_03 §3.2): a data sheet, a {@code veryHidden} {@code _OmniPlant} metadata sheet, the
 * locale's headers on row 1. Data cells are strings unless a test says otherwise.
 */
public final class TemplateWorkbooks {

    public static final List<String> ITEM_HEADERS_VI = List.of("Mã", "Tên", "Loại vật tư", "Đơn vị tính", "Theo dõi lô");
    public static final List<String> ITEM_HEADERS_EN = List.of("Code", "Name", "Item type", "Unit of measure", "Lot tracking");
    public static final List<String> PARTNER_HEADERS_VI =
            List.of("Mã", "Tên", "Người liên hệ", "Email", "Điện thoại", "Địa chỉ", "Ghi chú");
    public static final List<String> PARTNER_HEADERS_EN =
            List.of("Code", "Name", "Contact person", "Email", "Phone", "Address", "Notes");

    private String version = "omniplant-import-v1";
    private String kind;
    private String locale;
    private List<String> headers;
    private boolean metadata = true;
    private final List<Consumer<Sheet>> edits = new ArrayList<>();

    private TemplateWorkbooks(String kind, String locale, List<String> headers) {
        this.kind = kind;
        this.locale = locale;
        this.headers = new ArrayList<>(headers);
    }

    public static TemplateWorkbooks items(String locale) {
        return new TemplateWorkbooks("items", locale, "vi".equals(locale) ? ITEM_HEADERS_VI : ITEM_HEADERS_EN);
    }

    public static TemplateWorkbooks partners(String kind, String locale) {
        return new TemplateWorkbooks(kind, locale, "vi".equals(locale) ? PARTNER_HEADERS_VI : PARTNER_HEADERS_EN);
    }

    public TemplateWorkbooks version(String value) {
        version = value;
        return this;
    }

    public TemplateWorkbooks kind(String value) {
        kind = value;
        return this;
    }

    public TemplateWorkbooks withoutMetadata() {
        metadata = false;
        return this;
    }

    public TemplateWorkbooks headers(List<String> value) {
        headers = new ArrayList<>(value);
        return this;
    }

    /** Puts {@code values} as text cells on Excel row {@code line} (2 = first data row). */
    public TemplateWorkbooks row(int line, String... values) {
        edits.add(sheet -> {
            Row row = rowAt(sheet, line);
            for (int index = 0; index < values.length; index++) {
                if (values[index] != null) {
                    row.createCell(index).setCellValue(values[index]);
                }
            }
        });
        return this;
    }

    public TemplateWorkbooks numberCell(int line, int column, double value) {
        edits.add(sheet -> rowAt(sheet, line).createCell(column).setCellValue(value));
        return this;
    }

    public TemplateWorkbooks formulaCell(int line, int column, String formula) {
        edits.add(sheet -> rowAt(sheet, line).createCell(column).setCellFormula(formula));
        return this;
    }

    public TemplateWorkbooks booleanCell(int line, int column, boolean value) {
        edits.add(sheet -> rowAt(sheet, line).createCell(column).setCellValue(value));
        return this;
    }

    public TemplateWorkbooks dateCell(int line, int column, LocalDate value) {
        edits.add(sheet -> {
            CellStyle style = sheet.getWorkbook().createCellStyle();
            style.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat("yyyy-mm-dd"));
            Cell cell = rowAt(sheet, line).createCell(column);
            cell.setCellValue(value);
            cell.setCellStyle(style);
        });
        return this;
    }

    /** An empty but styled row — what the FE blank template has down to row 501. */
    public TemplateWorkbooks styledEmptyRow(int line) {
        edits.add(sheet -> {
            Row row = rowAt(sheet, line);
            for (int index = 0; index < headers.size(); index++) {
                row.createCell(index).setCellStyle(sheet.getWorkbook().createCellStyle());
            }
        });
        return this;
    }

    public byte[] build() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet("vi".equals(locale) ? "Dữ liệu" : "Data");
            Row header = data.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }
            if (metadata) {
                Sheet meta = workbook.createSheet("_OmniPlant");
                Row row = meta.createRow(0);
                row.createCell(0).setCellValue(version);
                row.createCell(1).setCellValue(kind);
                row.createCell(2).setCellValue(locale);
                workbook.setSheetVisibility(workbook.getSheetIndex(meta), SheetVisibility.VERY_HIDDEN);
            }
            edits.forEach(edit -> edit.accept(data));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Row rowAt(Sheet sheet, int line) {
        Row row = sheet.getRow(line - 1);
        return row == null ? sheet.createRow(line - 1) : row;
    }
}

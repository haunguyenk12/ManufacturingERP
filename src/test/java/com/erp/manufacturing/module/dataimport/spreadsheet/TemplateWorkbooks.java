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

    /** G5: {@code omniplant-bom-v1} headers (FE_HANDOFF_GD_05 §3.4). */
    public static final List<String> BOM_HEADERS_VI = List.of("Mã định mức", "Mã sản phẩm", "Lượng đầu ra",
            "Đơn vị đầu ra", "Mã thành phần", "Lượng thành phần", "Đơn vị thành phần", "Hao hụt (%)", "Ghi chú");
    public static final List<String> BOM_HEADERS_EN = List.of("BOM code", "Product code", "Output quantity",
            "Output UOM", "Component code", "Component quantity", "Component UOM", "Scrap (%)", "Notes");

    /** G6c: {@code omniplant-so-v1} headers (FE_HANDOFF_GD_06 §3.5). */
    public static final List<String> SO_HEADERS_VI = List.of("Mã nhóm đơn", "Mã khách hàng", "Ngày đặt",
            "Tham chiếu khách hàng", "Mã sản phẩm", "Số lượng", "Ngày cần");
    public static final List<String> SO_HEADERS_EN = List.of("Group code", "Customer code", "Order date",
            "Customer reference", "Item code", "Quantity", "Due date");

    private String version = "omniplant-import-v1";
    /**
     * G5: sheet {@code BOM} and a two-row {@code _meta} key/value sheet instead of {@code _OmniPlant}.
     * G6c reuses the layout with sheet {@code Sales orders}.
     */
    private boolean bomLayout;
    private String keyValueSheet = "BOM";
    private boolean date1904;
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

    public static TemplateWorkbooks boms(String locale) {
        TemplateWorkbooks workbook = new TemplateWorkbooks("bom", locale, "vi".equals(locale) ? BOM_HEADERS_VI : BOM_HEADERS_EN);
        workbook.bomLayout = true;
        workbook.version = "omniplant-bom-v1";
        return workbook;
    }

    public static TemplateWorkbooks salesOrders(String locale) {
        TemplateWorkbooks workbook = new TemplateWorkbooks("so", locale, "vi".equals(locale) ? SO_HEADERS_VI : SO_HEADERS_EN);
        workbook.bomLayout = true;
        workbook.keyValueSheet = "Sales orders";
        workbook.version = "omniplant-so-v1";
        return workbook;
    }

    /** The workbook uses the 1904 date system (Excel for Mac legacy) — serials are 1462 days smaller. */
    public TemplateWorkbooks date1904() {
        date1904 = true;
        return this;
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

    /** A raw serial with a date format — what Excel stores; read with the workbook's date system. */
    public TemplateWorkbooks dateSerialCell(int line, int column, double serial, String format) {
        edits.add(sheet -> {
            CellStyle style = sheet.getWorkbook().createCellStyle();
            style.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat(format));
            Cell cell = rowAt(sheet, line).createCell(column);
            cell.setCellValue(serial);
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
            if (date1904) {
                // A new workbook already has a <workbookPr>; adding a second one would be ignored.
                var properties = workbook.getCTWorkbook().getWorkbookPr();
                (properties == null ? workbook.getCTWorkbook().addNewWorkbookPr() : properties).setDate1904(true);
            }
            Sheet data = workbook.createSheet(bomLayout ? keyValueSheet : "vi".equals(locale) ? "Dữ liệu" : "Data");
            Row header = data.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }
            if (metadata && bomLayout) {
                Sheet meta = workbook.createSheet("_meta");
                Row versionRow = meta.createRow(0);
                versionRow.createCell(0).setCellValue("version");
                versionRow.createCell(1).setCellValue(version);
                Row localeRow = meta.createRow(1);
                localeRow.createCell(0).setCellValue("locale");
                localeRow.createCell(1).setCellValue(locale);
                workbook.setSheetVisibility(workbook.getSheetIndex(meta), SheetVisibility.VERY_HIDDEN);
            } else if (metadata) {
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

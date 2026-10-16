package com.erp.manufacturing.module.dataimport.spreadsheet;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema;
import com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema.Column;
import com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema.ValueKind;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads an {@code omniplant-import-v1} workbook (G3) into text, refusing anything that is not one.
 *
 * <p>Unlike {@link SpreadsheetReader}, which renders every cell as Excel <em>displays</em> it and lets
 * a profile decide, this reader is strict about cell types (NEXT_PHASE_PLAN.md §10.2 D8): a formula is
 * never evaluated, a code or phone stored as a number is refused rather than guessed (its leading
 * zeros are already gone), and date/boolean/error cells are refused. Those are <em>row</em> issues —
 * the batch is still created, {@code INVALID} — while a file that is not the template at all is a
 * file-level error and creates nothing.
 *
 * <p>The archive is inspected before POI sees it: entry count, the real decompressed size (not the
 * size the archive advertises) and the parts that would make it a macro workbook or a workbook with
 * external links. POI is then only ever handed something small and inert.
 */
@Component
@Slf4j
public class FixedTemplateReader {

    /** FE uses the same bound; a template saved by Excel has well under 30 parts. */
    static final int MAX_ZIP_ENTRIES = 64;

    /** Real bytes after inflation, summed over every part. */
    static final long MAX_EXPANDED_BYTES = 25L * 1024 * 1024;

    private final DataFormatter formatter = new DataFormatter();

    /**
     * @param maxRows non-blank data rows accepted; one more is {@code ROW_LIMIT}
     */
    public FixedTemplateWorkbook read(byte[] bytes, DataImportKind kind, int maxRows) {
        inspectArchive(bytes);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Metadata metadata = kind == DataImportKind.BOMS ? readBomMetadata(workbook) : readMetadata(workbook, kind);
            String sheetName = FixedTemplateSchema.dataSheetName(kind, metadata.locale());
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw template("Workbook has no sheet named '" + sheetName + "'");
            }
            List<Column> columns = FixedTemplateSchema.columns(kind);
            checkHeaders(sheet, columns, metadata.locale());
            if (sheet.getLastRowNum() >= FixedTemplateSchema.MAX_PHYSICAL_ROWS) {
                throw template("Data sheet has more than " + FixedTemplateSchema.MAX_PHYSICAL_ROWS
                        + " formatted rows; use the template as downloaded");
            }
            List<FixedTemplateWorkbook.ParsedRow> rows = readRows(sheet, columns, maxRows);
            if (rows.isEmpty()) {
                throw ExceptionFactory.custom(ValidationErrorCode.EMPTY_FILE, "File contains no data rows");
            }
            return new FixedTemplateWorkbook(metadata.locale(), metadata.version(),
                    columns.stream().map(column -> column.header(metadata.locale())).toList(), rows);
        } catch (AppException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("[Import] Unreadable fixed-template workbook: {}", e.toString());
            throw ExceptionFactory.custom(ValidationErrorCode.FILE_FORMAT,
                    "File could not be read as an .xlsx workbook");
        }
    }

    // ── Archive guard ──────────────────────────────────────────────────────

    private void inspectArchive(byte[] bytes) {
        int entries = 0;
        long expanded = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw ExceptionFactory.custom(ValidationErrorCode.FILE_FORMAT,
                            "Workbook archive has more than " + MAX_ZIP_ENTRIES + " parts");
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("vbaproject") || name.startsWith("xl/externallinks/")
                        || name.startsWith("xl/activex/")) {
                    throw ExceptionFactory.custom(ValidationErrorCode.FILE_FORMAT,
                            "Workbooks with macros, ActiveX or external links are not accepted");
                }
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES) {
                        throw ExceptionFactory.custom(ValidationErrorCode.FILE_SIZE,
                                "Workbook expands to more than " + MAX_EXPANDED_BYTES + " bytes");
                    }
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ExceptionFactory.custom(ValidationErrorCode.FILE_FORMAT, "File is not an .xlsx archive");
        }
        if (entries == 0) {
            throw ExceptionFactory.custom(ValidationErrorCode.FILE_FORMAT, "File is not an .xlsx archive");
        }
    }

    // ── Template structure ─────────────────────────────────────────────────

    private Metadata readMetadata(Workbook workbook, DataImportKind kind) {
        Sheet sheet = workbook.getSheet(FixedTemplateSchema.METADATA_SHEET);
        Row row = sheet == null ? null : sheet.getRow(0);
        if (row == null) {
            throw template("Workbook has no template metadata; download a new template");
        }
        String version = plainText(row.getCell(0));
        String fileKind = plainText(row.getCell(1));
        String locale = plainText(row.getCell(2)).toLowerCase(Locale.ROOT);
        if (!FixedTemplateSchema.TEMPLATE_VERSION.equals(version)) {
            throw template("Template version '" + version + "' is not supported; expected "
                    + FixedTemplateSchema.TEMPLATE_VERSION);
        }
        if (!kind.wireValue().equals(fileKind)) {
            throw template("Template is for '" + fileKind + "', not '" + kind.wireValue() + "'");
        }
        if (!FixedTemplateSchema.isSupportedLocale(locale)) {
            throw template("Template locale '" + locale + "' is not supported");
        }
        return new Metadata(version, locale);
    }

    /**
     * G5: the {@code omniplant-bom-v1} metadata is a two-row key/value sheet ({@code _meta}: A1
     * {@code version} / B1 version, A2 {@code locale} / B2 locale), unlike the one-row sheet of
     * {@code omniplant-import-v1}. The kind is implied by the version.
     */
    private Metadata readBomMetadata(Workbook workbook) {
        Sheet sheet = workbook.getSheet(FixedTemplateSchema.BOM_METADATA_SHEET);
        Row versionRow = sheet == null ? null : sheet.getRow(0);
        Row localeRow = sheet == null ? null : sheet.getRow(1);
        if (versionRow == null || localeRow == null
                || !"version".equalsIgnoreCase(plainText(versionRow.getCell(0)))
                || !"locale".equalsIgnoreCase(plainText(localeRow.getCell(0)))) {
            throw template("Workbook has no BOM template metadata; download a new template");
        }
        String version = plainText(versionRow.getCell(1));
        String locale = plainText(localeRow.getCell(1)).toLowerCase(Locale.ROOT);
        if (!FixedTemplateSchema.BOM_TEMPLATE_VERSION.equals(version)) {
            throw template("Template version '" + version + "' is not supported; expected "
                    + FixedTemplateSchema.BOM_TEMPLATE_VERSION);
        }
        if (!FixedTemplateSchema.isSupportedLocale(locale)) {
            throw template("Template locale '" + locale + "' is not supported");
        }
        return new Metadata(version, locale);
    }

    /** Header text and order must match the locale exactly; no extra column may carry a header. */
    private void checkHeaders(Sheet sheet, List<Column> columns, String locale) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw template("Header row is missing");
        }
        for (int index = 0; index < columns.size(); index++) {
            String actual = FixedTemplateSchema.comparable(plainText(header.getCell(index)));
            String expected = FixedTemplateSchema.comparable(columns.get(index).header(locale));
            if (!expected.equals(actual)) {
                throw template("Column " + FixedTemplateSchema.columnLetter(index) + " must be '"
                        + columns.get(index).header(locale) + "'");
            }
        }
        for (int index = columns.size(); index < header.getLastCellNum(); index++) {
            if (!plainText(header.getCell(index)).isBlank()) {
                throw template("Column " + FixedTemplateSchema.columnLetter(index)
                        + " is not part of the template");
            }
        }
    }

    // ── Rows ───────────────────────────────────────────────────────────────

    private List<FixedTemplateWorkbook.ParsedRow> readRows(Sheet sheet, List<Column> columns, int maxRows) {
        List<FixedTemplateWorkbook.ParsedRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlank(row, columns.size())) {
                continue;
            }
            if (rows.size() >= maxRows) {
                throw ExceptionFactory.custom(ValidationErrorCode.ROW_LIMIT,
                        "File has more than " + maxRows + " data rows");
            }
            Map<String, String> values = new LinkedHashMap<>();
            List<ImportCellError> issues = new ArrayList<>();
            for (int index = 0; index < columns.size(); index++) {
                Column column = columns.get(index);
                values.put(column.field(), readCell(row.getCell(index), column, index, issues));
            }
            // +1: Excel numbers rows from 1, POI from 0. Never renumbered after skipping blank rows.
            rows.add(new FixedTemplateWorkbook.ParsedRow(rowIndex + 1, values, issues));
        }
        return rows;
    }

    /** A row is data when any template column holds anything other than whitespace. */
    private boolean isBlank(Row row, int width) {
        for (int index = 0; index < width; index++) {
            Cell cell = row.getCell(index);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                continue;
            }
            if (cell.getCellType() != CellType.STRING || !cell.getStringCellValue().isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The trimmed text of one cell, or {@code ""} plus an issue when the cell type is not accepted.
     * The refused value is deliberately not echoed back: a formula or an error cell is not something
     * a report should reproduce.
     */
    private String readCell(Cell cell, Column column, int index, List<ImportCellError> issues) {
        if (cell == null) {
            return "";
        }
        String letter = FixedTemplateSchema.columnLetter(index);
        String text;
        switch (cell.getCellType()) {
            case BLANK -> text = "";
            case STRING -> text = cell.getStringCellValue();
            case FORMULA -> {
                issues.add(ImportCellError.of(letter, column.field(), ImportErrorCode.FORMULA,
                        "Cell contains a formula; enter the value itself"));
                return "";
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    issues.add(ImportCellError.of(letter, column.field(), ImportErrorCode.CELL_TYPE,
                            "Cell is a date; this column expects text"));
                    return "";
                }
                if (column.valueKind() == ValueKind.CODE || column.valueKind() == ValueKind.PHONE) {
                    issues.add(ImportCellError.of(letter, column.field(), ImportErrorCode.TEXT_REQUIRED,
                            "Cell is a number; format the column as Text so leading zeros are kept"));
                    return "";
                }
                if (column.valueKind() == ValueKind.QUANTITY || column.valueKind() == ValueKind.PERCENT) {
                    // The stored value, not the displayed one: a cell formatted "0" would otherwise turn
                    // 2.5 into "3" without anyone seeing it.
                    text = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                } else {
                    text = formatter.formatCellValue(cell);
                }
            }
            default -> {
                issues.add(ImportCellError.of(letter, column.field(), ImportErrorCode.CELL_TYPE,
                        "Cell type " + cell.getCellType() + " is not accepted; enter plain text"));
                return "";
            }
        }
        String normalised = Normalizer.normalize(text, Normalizer.Form.NFC).strip();
        return column.valueKind() == ValueKind.CODE || column.valueKind() == ValueKind.UPPER_TEXT
                ? normalised.toUpperCase(Locale.ROOT)
                : normalised;
    }

    /** Metadata and header cells: plain text only, anything else reads as empty. */
    private String plainText(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.STRING) {
            return "";
        }
        return Normalizer.normalize(cell.getStringCellValue(), Normalizer.Form.NFC).strip();
    }

    private AppException template(String message) {
        return ExceptionFactory.custom(ValidationErrorCode.TEMPLATE, message);
    }

    private record Metadata(String version, String locale) {
    }
}

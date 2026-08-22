package com.erp.manufacturing.module.dataimport.spreadsheet;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.CellType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads an {@code .xlsx} file into plain text.
 *
 * <p>Everything here uses {@link DataFormatter}, which returns the string Excel <em>displays</em>
 * rather than the value it stores. That is the single most important decision in this class:
 * {@code getNumericCellValue()} would turn a date into {@code 45123.0} and the code {@code 007} into
 * {@code 7.0}, and the importer would then be guessing what the user meant. With the displayed text,
 * every type question is deferred to exactly one place — the transform catalogue and the field
 * descriptors — where it is unit-tested.
 *
 * <p>Header text is normalised (NFC, trimmed, internal whitespace collapsed) because a header is a
 * lookup key: a trailing space or a decomposed Vietnamese vowel would make a mapping silently miss.
 * Cell values are <em>not</em> normalised — that is what the profile's transforms are for, and doing
 * it here would hide from the user what their file actually contains.
 */
@Component
@Slf4j
public class SpreadsheetReader {

    private static final int MAX_COLUMNS = 256;
    private static final int MAX_CELL_CHARACTERS = 32_767;

    static {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(10L * 1024 * 1024);
    }

    /**
     * Written as code points, not as literals: a non-breaking space in source is indistinguishable
     * from an ordinary one in a diff, so a well-meaning reformat could turn this into a no-op that
     * nothing would notice until a customer's header stopped matching.
     */
    private static final char NO_BREAK_SPACE = 0x00A0;
    private static final char NARROW_NO_BREAK_SPACE = 0x202F;

    private final DataFormatter formatter = new DataFormatter();

    /**
     * @param sheetName          {@code null} for the first sheet
     * @param headerRowIndex     0-based
     * @param firstDataRowIndex  0-based, must be after the header row
     * @param maxRows            refuse anything larger, rather than tie up a request thread
     */
    public RawSheet read(InputStream in, String sheetName, int headerRowIndex,
                         int firstDataRowIndex, int maxRows) {
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = resolveSheet(workbook, sheetName);
            List<HeaderColumn> columns = readHeaders(sheet, headerRowIndex);
            List<RawSheet.RawRow> rows = readRows(sheet, columns, firstDataRowIndex, maxRows);
            return new RawSheet(columns.stream().map(HeaderColumn::name).toList(), rows);
        } catch (IOException | RuntimeException e) {
            if (e instanceof com.erp.manufacturing.common.exception.AppException appException) {
                throw appException;
            }
            log.warn("[Import] Unreadable spreadsheet: {}", e.toString());
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "File could not be read as an .xlsx spreadsheet: " + e.getMessage());
        }
    }

    private Sheet resolveSheet(Workbook workbook, String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            if (workbook.getNumberOfSheets() == 0) {
                throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                        "Workbook contains no sheets");
            }
            return workbook.getSheetAt(0);
        }
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Workbook has no sheet named '" + sheetName + "'");
        }
        return sheet;
    }

    /**
     * Reads the header row, skipping blank columns.
     *
     * <p>Duplicate headers are rejected rather than silently deduplicated: if two columns are both
     * called "Mã", a mapping naming "Mã" would pick one of them by accident of iteration order, and
     * the user would have no way to tell which.
     */
    private List<HeaderColumn> readHeaders(Sheet sheet, int headerRowIndex) {
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Header row " + (headerRowIndex + 1) + " is empty");
        }

        List<HeaderColumn> headers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (headerRow.getLastCellNum() > MAX_COLUMNS) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Spreadsheet has more than " + MAX_COLUMNS + " columns");
        }
        for (int column = 0; column < headerRow.getLastCellNum(); column++) {
            String header = normaliseHeader(cellText(headerRow.getCell(column)));
            if (header.isEmpty()) {
                continue;
            }
            if (!seen.add(header)) {
                throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                        "Header '" + header + "' appears in more than one column; "
                                + "a mapping could not tell them apart");
            }
            // Preserve the physical column index. Compressing blank header cells would pair the
            // next non-blank header with the wrong data cell (A="Code", B blank, C="Name" would
            // otherwise read B's value as Name).
            headers.add(new HeaderColumn(column, header));
        }

        if (headers.isEmpty()) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Header row " + (headerRowIndex + 1) + " has no usable column names");
        }
        return headers;
    }

    /**
     * Reads data rows, dropping the fully blank ones.
     *
     * <p>Blank rows are ordinary in a hand-maintained sheet — a spacer before a totals line, a gap
     * between groups — and reporting them as a thousand "required field missing" errors would bury
     * the real problems. A row where every mapped column is empty carries no instruction, so there is
     * nothing to import and nothing to complain about.
     */
    private List<RawSheet.RawRow> readRows(Sheet sheet, List<HeaderColumn> headers,
                                           int firstDataRowIndex, int maxRows) {
        List<RawSheet.RawRow> rows = new ArrayList<>();
        for (int rowIndex = firstDataRowIndex; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Map<String, String> cells = new LinkedHashMap<>();
            boolean anyValue = false;
            for (HeaderColumn header : headers) {
                String text = cellText(row.getCell(header.index()));
                cells.put(header.name(), text);
                anyValue |= !text.isBlank();
            }
            if (!anyValue) {
                continue;
            }

            if (rows.size() >= maxRows) {
                throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                        "File has more than " + maxRows + " data rows; split it and import in batches");
            }
            // +1 because Excel numbers rows from 1 while POI indexes from 0.
            rows.add(new RawSheet.RawRow(rowIndex + 1, cells));
        }
        return rows;
    }

    /** Empty string for a missing cell, so every row has the same key set as the header. */
    private String cellText(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Formula cells are not allowed in imported spreadsheets");
        }
        String text = formatter.formatCellValue(cell);
        if (text.length() > MAX_CELL_CHARACTERS) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Spreadsheet cell exceeds " + MAX_CELL_CHARACTERS + " characters");
        }
        return text;
    }

    private String normaliseHeader(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replace(NO_BREAK_SPACE, ' ')
                .replace(NARROW_NO_BREAK_SPACE, ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record HeaderColumn(int index, String name) {
    }
}

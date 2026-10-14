package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.target.ImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.dataimport.target.PartnerImportTargetHandler;
import com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema;
import com.erp.manufacturing.module.dataimport.template.FixedTemplateSchema.Column;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Every rule a fixed-template row must pass, run identically at upload and again at commit (FE §3.6:
 * commit never trusts the earlier preview).
 *
 * <p>Three layers, in order: field rules (required, length, code shape, enum label, e-mail), repeats
 * inside the file — <em>every</em> occurrence is flagged (FE A09), not just the second — and then the
 * target handler's database rules (code already taken in any status, UOM missing or inactive). The
 * handler is the same one the mapped-profile path uses, so both paths share a single definition of
 * "this code is taken" and "this unit is usable".
 *
 * <p>The handler only sees rows whose code is well-formed and unique in the file, and an issue it
 * reports for a field that already has one is dropped — a row lists each broken field once.
 */
@Component
@RequiredArgsConstructor
public class FixedTemplateValidator {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9._-]+$");

    private final ImportTargetRegistry registry;

    /** One row going in: the values read or stored, plus issues already found while reading cells. */
    public record RowInput(int line, Map<String, String> values, List<ImportCellError> issues) {
    }

    /** One row coming out: values with enum labels replaced by canonical names, and every issue. */
    public record RowResult(int line, Map<String, String> values, List<ImportCellError> issues) {

        public boolean valid() {
            return issues.isEmpty();
        }
    }

    public List<RowResult> validate(DataImportKind kind, UUID companyId, String locale, List<RowInput> rows) {
        List<Column> columns = FixedTemplateSchema.columns(kind);
        List<RowResult> results = new ArrayList<>(rows.size());
        for (RowInput row : rows) {
            Map<String, String> values = new LinkedHashMap<>(row.values());
            List<ImportCellError> issues = new ArrayList<>(row.issues());
            for (int index = 0; index < columns.size(); index++) {
                checkField(columns.get(index), FixedTemplateSchema.columnLetter(index), locale, values, issues);
            }
            results.add(new RowResult(row.line(), values, issues));
        }
        flagRepeatedCodes(kind, results);
        applyHandlerRules(kind, companyId, results);
        return results;
    }

    private void checkField(Column column, String letter, String locale, Map<String, String> values,
                            List<ImportCellError> issues) {
        String field = column.field();
        if (hasIssue(issues, field)) {
            return;
        }
        String value = values.getOrDefault(field, "");
        values.put(field, value);
        if (value.isBlank()) {
            if (column.required()) {
                issues.add(ImportCellError.of(letter, field, ImportErrorCode.REQUIRED_MISSING,
                        "Column " + letter + " is required"));
            }
            return;
        }
        if (column.maxLength() != null && value.length() > column.maxLength()) {
            issues.add(ImportCellError.of(letter, field, ImportErrorCode.TOO_LONG,
                    "Column " + letter + " allows at most " + column.maxLength() + " characters, got "
                            + value.length()));
            return;
        }
        switch (column.valueKind()) {
            case CODE -> {
                if (!CODE_PATTERN.matcher(value).matches()) {
                    issues.add(ImportCellError.of(letter, field, ImportErrorCode.INVALID_FORMAT,
                            "Code may only contain A-Z, 0-9, dot, underscore or hyphen"));
                }
            }
            case ITEM_TYPE -> canonical(FixedTemplateSchema.canonicalItemType(locale, value),
                    letter, field, values, issues);
            case TRACKING -> canonical(FixedTemplateSchema.canonicalTracking(locale, value),
                    letter, field, values, issues);
            case TEXT -> {
                if (FixedTemplateSchema.EMAIL.equals(field)
                        && !PartnerImportTargetHandler.EMAIL_PATTERN.matcher(value).matches()) {
                    issues.add(ImportCellError.of(letter, field, ImportErrorCode.INVALID_FORMAT,
                            "Email is not a valid address"));
                }
            }
            case PHONE, UPPER_TEXT -> {
                // Length is the only rule; the cell type was checked while reading.
            }
        }
    }

    private void canonical(Optional<String> canonical, String letter, String field,
                           Map<String, String> values, List<ImportCellError> issues) {
        if (canonical.isPresent()) {
            values.put(field, canonical.get());
        } else {
            issues.add(ImportCellError.of(letter, field, ImportErrorCode.NOT_ALLOWED_VALUE,
                    "Column " + letter + " is not one of the template's values"));
        }
    }

    private void flagRepeatedCodes(DataImportKind kind, List<RowResult> results) {
        Map<String, List<RowResult>> byCode = new HashMap<>();
        for (RowResult result : results) {
            if (!hasIssue(result.issues(), FixedTemplateSchema.CODE)) {
                byCode.computeIfAbsent(result.values().get(FixedTemplateSchema.CODE), code -> new ArrayList<>())
                        .add(result);
            }
        }
        String letter = FixedTemplateSchema.columnLetter(kind, FixedTemplateSchema.CODE).orElseThrow();
        byCode.forEach((code, group) -> {
            if (group.size() > 1) {
                String lines = group.stream().map(row -> String.valueOf(row.line())).toList().toString();
                group.forEach(row -> row.issues().add(ImportCellError.of(letter, FixedTemplateSchema.CODE,
                        ImportErrorCode.DUPLICATE_IN_FILE,
                        "Code '" + code + "' appears on several rows of this file: " + lines)));
            }
        });
    }

    private void applyHandlerRules(DataImportKind kind, UUID companyId, List<RowResult> results) {
        List<RowResult> candidates = results.stream()
                .filter(result -> !hasIssue(result.issues(), FixedTemplateSchema.CODE))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        ImportTargetHandler handler = registry.handlerFor(kind.targetType());
        Map<Integer, List<ImportCellError>> problems = handler.validateBatch(companyId, candidates.stream()
                .map(result -> FixedTemplateSchema.toHandlerValues(kind, result.values()))
                .toList());
        problems.forEach((index, found) -> {
            RowResult row = candidates.get(index);
            for (ImportCellError problem : found) {
                String field = FixedTemplateSchema.fromHandlerField(kind, problem.targetField());
                if (field == null || hasIssue(row.issues(), field)) {
                    continue;
                }
                row.issues().add(ImportCellError.of(
                        FixedTemplateSchema.columnLetter(kind, field).orElse(null),
                        field, problem.code(), problem.message()));
            }
        });
    }

    private boolean hasIssue(List<ImportCellError> issues, String field) {
        return issues.stream().anyMatch(issue -> field.equals(issue.targetField()));
    }
}

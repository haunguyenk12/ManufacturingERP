package com.erp.manufacturing.module.dataimport.target;

import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Shared rules for the two business-partner catalogues (G3): suppliers and customers have the same
 * seven fields, the same code rule and the same "unique per company, any status" constraint — only
 * the owning service, the lookup and the phone column width differ. Subclasses supply exactly those.
 *
 * <p>Create-only, for the same reason {@link ItemImportTargetHandler} is: an existing code is a row
 * error, never a silent update.
 */
public abstract class PartnerImportTargetHandler implements ImportTargetHandler {

    public static final String FIELD_CODE = "code";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_CONTACT_NAME = "contactName";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_NOTES = "notes";

    /** Same shape the partner services enforce after trim + uppercase. */
    static final String CODE_PATTERN = "^[A-Z0-9._-]+$";

    /** FE §3.3. Deliberately loose: it rejects typos, it does not try to be RFC 5322. */
    public static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Human name of the catalogue, for messages ("Supplier", "Customer"). */
    protected abstract String label();

    /** Column width of {@code phone} in this catalogue's table. */
    public abstract int phoneMaxLength();

    /** Codes among {@code codes} that already exist in the company, in any status, in one query. */
    protected abstract Set<String> existingCodes(UUID companyId, Set<String> codes);

    @Override
    public ImportTargetDescriptor descriptor() {
        return new ImportTargetDescriptor(targetType(), label() + " master", List.of(
                ImportFieldDescriptor.string(FIELD_CODE, label() + " code", true, 100, "PARTNER-001"),
                ImportFieldDescriptor.string(FIELD_NAME, label() + " name", true, 255, "Minh Hoa Co., Ltd"),
                ImportFieldDescriptor.string(FIELD_CONTACT_NAME, "Contact person", false, 255, "Nguyen Van A"),
                ImportFieldDescriptor.string(FIELD_EMAIL, "Email", false, 255, "contact@example.com"),
                ImportFieldDescriptor.string(FIELD_PHONE, "Phone", false, phoneMaxLength(), "0000123456"),
                ImportFieldDescriptor.string(FIELD_ADDRESS, "Address", false, 500, "1 Street A, Ha Noi"),
                ImportFieldDescriptor.string(FIELD_NOTES, "Notes", false, 500, "")));
    }

    /**
     * Code shape, repeats inside the file, existing codes (one query for the whole file, rule C14) and
     * the e-mail shape — the partner services do not run Bean Validation when called directly, so an
     * import that skipped this would store an address the API itself would refuse.
     */
    @Override
    public Map<Integer, List<ImportCellError>> validateBatch(UUID companyId, List<Map<String, String>> rows) {
        Map<Integer, List<ImportCellError>> problems = new HashMap<>();
        List<String> codes = rows.stream().map(row -> normaliseCode(row.get(FIELD_CODE))).toList();
        Set<String> existing = existingCodes(companyId, new LinkedHashSet<>(codes.stream()
                .filter(code -> code != null && code.matches(CODE_PATTERN))
                .toList()));

        Set<String> seenInFile = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            String code = codes.get(index);
            List<ImportCellError> rowProblems = new ArrayList<>();
            if (code != null && !code.matches(CODE_PATTERN)) {
                rowProblems.add(ImportCellError.of(null, FIELD_CODE, ImportErrorCode.INVALID_FORMAT,
                        label() + " code may only contain A-Z, 0-9, dot, underscore or hyphen, got '" + code + "'"));
            } else if (code != null) {
                if (!seenInFile.add(code)) {
                    rowProblems.add(ImportCellError.of(null, FIELD_CODE, ImportErrorCode.DUPLICATE_IN_FILE,
                            label() + " code '" + code + "' appears more than once in this file"));
                } else if (existing.contains(code)) {
                    rowProblems.add(ImportCellError.of(null, FIELD_CODE, ImportErrorCode.ALREADY_EXISTS,
                            label() + " code '" + code + "' already exists in this company"));
                }
            }
            String email = rows.get(index).get(FIELD_EMAIL);
            if (email != null && !email.isBlank() && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
                rowProblems.add(ImportCellError.of(null, FIELD_EMAIL, ImportErrorCode.INVALID_FORMAT,
                        "Email is not a valid address"));
            }
            if (!rowProblems.isEmpty()) {
                problems.put(index, rowProblems);
            }
        }
        return problems;
    }

    /** Same normalisation the partner services apply, so duplicate detection sees the stored form. */
    protected String normaliseCode(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toUpperCase(Locale.ROOT);
    }
}

package com.erp.manufacturing.module.dataimport.template;

import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.target.CustomerImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ItemImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.PartnerImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.SupplierImportTargetHandler;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code omniplant-import-v1} workbook contract (FE_HANDOFF_GD_03 §3.2–§3.3), written down once.
 *
 * <p>FE generates the template; this class is the server's independent reading of the same contract —
 * the sheet names, the header text and order per locale, the label ↔ canonical value tables and the
 * per-column limits. It deliberately does not trust anything else in the file: the reference sheet
 * listing UOMs is ignored, and Company/Plant/actor never come from the workbook.
 *
 * <p>Row {@code values} use FE's field names ({@code tracking}, {@code contact}); the target handlers
 * use the master DTO names ({@code lotTracked}, {@code contactName}). {@link #toHandlerValues} and
 * {@link #fromHandlerField} are the only two places that translate between them.
 */
public final class FixedTemplateSchema {

    public static final String TEMPLATE_VERSION = "omniplant-import-v1";
    /** G5: the BOM workbook has its own version, sheet and metadata layout (FE_HANDOFF_GD_05 §3.4). */
    public static final String BOM_TEMPLATE_VERSION = "omniplant-bom-v1";
    public static final String BOM_DATA_SHEET = "BOM";
    public static final String BOM_METADATA_SHEET = "_meta";
    public static final String METADATA_SHEET = "_OmniPlant";
    public static final String LOCALE_VI = "vi";
    public static final String LOCALE_EN = "en";

    /**
     * FE pre-formats the blank template down to row 501, so physical rows say nothing about data. This
     * bound only stops a sheet styled down to row 1,048,576 from costing a full scan (FE §3.3).
     */
    public static final int MAX_PHYSICAL_ROWS = 10_000;

    public static final String CODE = "code";
    public static final String NAME = "name";
    public static final String TYPE = "type";
    public static final String UNIT = "unit";
    public static final String TRACKING = "tracking";
    public static final String CONTACT = "contact";
    public static final String EMAIL = "email";
    public static final String PHONE = "phone";
    public static final String ADDRESS = "address";
    public static final String NOTES = "notes";

    public static final String BOM_CODE = "bomCode";
    public static final String PRODUCT_CODE = "productCode";
    public static final String OUTPUT_QUANTITY = "outputQuantity";
    public static final String OUTPUT_UOM = "outputUom";
    public static final String COMPONENT_CODE = "componentCode";
    public static final String COMPONENT_QUANTITY = "componentQuantity";
    public static final String COMPONENT_UOM = "componentUom";
    public static final String SCRAP_PERCENT = "scrapPercent";

    public static final String TRACKING_NONE = "NONE";
    public static final String TRACKING_LOT = "LOT";

    /** How a column's cell is read and normalised. */
    public enum ValueKind {
        /** Trimmed, upper-cased; a numeric cell is {@code TEXT_REQUIRED} (leading zeros already lost). */
        CODE,
        /** Trimmed; a numeric cell is {@code TEXT_REQUIRED}. */
        PHONE,
        /** Trimmed, upper-cased. */
        UPPER_TEXT,
        /** Trimmed. */
        TEXT,
        /** Locale label or canonical {@code ItemType} name. */
        ITEM_TYPE,
        /** Locale label or {@code NONE}/{@code LOT}. */
        TRACKING,
        /**
         * G5: a plain decimal greater than zero, at most 6 decimals, no sign or exponent. A number cell
         * is read by its value, never by its display format — "2.5" shown as "3" is still 2.5.
         */
        QUANTITY,
        /** G5: like {@link #QUANTITY} but 0 ≤ x &lt; 100, blank means 0. 5 means 5 %. */
        PERCENT
    }

    /**
     * @param field     FE field name used in {@code rows[].values} and in issues
     * @param headerVi  exact header text of the Vietnamese template
     * @param headerEn  exact header text of the English template
     * @param maxLength characters after trim; {@code null} for enum columns
     */
    public record Column(String field, String headerVi, String headerEn, ValueKind valueKind,
                         boolean required, Integer maxLength) {

        public String header(String locale) {
            return LOCALE_VI.equals(locale) ? headerVi : headerEn;
        }
    }

    private static final List<Column> ITEM_COLUMNS = List.of(
            new Column(CODE, "Mã", "Code", ValueKind.CODE, true, 100),
            new Column(NAME, "Tên", "Name", ValueKind.TEXT, true, 255),
            new Column(TYPE, "Loại vật tư", "Item type", ValueKind.ITEM_TYPE, true, null),
            new Column(UNIT, "Đơn vị tính", "Unit of measure", ValueKind.UPPER_TEXT, true, 30),
            new Column(TRACKING, "Theo dõi lô", "Lot tracking", ValueKind.TRACKING, true, null));

    private static List<Column> partnerColumns(int phoneMaxLength) {
        return List.of(
                new Column(CODE, "Mã", "Code", ValueKind.CODE, true, 100),
                new Column(NAME, "Tên", "Name", ValueKind.TEXT, true, 255),
                new Column(CONTACT, "Người liên hệ", "Contact person", ValueKind.TEXT, false, 255),
                new Column(EMAIL, "Email", "Email", ValueKind.TEXT, false, 255),
                new Column(PHONE, "Điện thoại", "Phone", ValueKind.PHONE, false, phoneMaxLength),
                new Column(ADDRESS, "Địa chỉ", "Address", ValueKind.TEXT, false, 500),
                new Column(NOTES, "Ghi chú", "Notes", ValueKind.TEXT, false, 500));
    }

    /** G5 (FE §3.4): one row per component; columns A–D and I repeat identically within a BOM group. */
    private static final List<Column> BOM_COLUMNS = List.of(
            new Column(BOM_CODE, "Mã định mức", "BOM code", ValueKind.CODE, true, 100),
            new Column(PRODUCT_CODE, "Mã sản phẩm", "Product code", ValueKind.CODE, true, 100),
            new Column(OUTPUT_QUANTITY, "Lượng đầu ra", "Output quantity", ValueKind.QUANTITY, true, null),
            new Column(OUTPUT_UOM, "Đơn vị đầu ra", "Output UOM", ValueKind.UPPER_TEXT, true, 30),
            new Column(COMPONENT_CODE, "Mã thành phần", "Component code", ValueKind.CODE, true, 100),
            new Column(COMPONENT_QUANTITY, "Lượng thành phần", "Component quantity", ValueKind.QUANTITY, true, null),
            new Column(COMPONENT_UOM, "Đơn vị thành phần", "Component UOM", ValueKind.UPPER_TEXT, true, 30),
            new Column(SCRAP_PERCENT, "Hao hụt (%)", "Scrap (%)", ValueKind.PERCENT, false, null),
            new Column(NOTES, "Ghi chú", "Notes", ValueKind.TEXT, false, 1000));

    private static final List<Column> SUPPLIER_COLUMNS =
            partnerColumns(SupplierImportTargetHandler.PHONE_MAX_LENGTH);
    private static final List<Column> CUSTOMER_COLUMNS =
            partnerColumns(CustomerImportTargetHandler.PHONE_MAX_LENGTH);

    /** Label (lower-cased) → canonical, per locale. SERVICE is deliberately absent (FE §1.2). */
    private static final Map<String, Map<String, String>> ITEM_TYPE_LABELS = Map.of(
            LOCALE_VI, Map.of(
                    "nguyên vật liệu", "RAW_MATERIAL",
                    "bán thành phẩm", "WIP",
                    "thành phẩm", "FINISHED_GOOD",
                    "vật tư tiêu hao", "CONSUMABLE"),
            LOCALE_EN, Map.of(
                    "raw material", "RAW_MATERIAL",
                    "work in progress", "WIP",
                    "finished good", "FINISHED_GOOD",
                    "consumable", "CONSUMABLE"));

    private static final List<String> ITEM_TYPES = List.of("RAW_MATERIAL", "WIP", "FINISHED_GOOD", "CONSUMABLE");

    private static final Map<String, Map<String, String>> TRACKING_LABELS = Map.of(
            LOCALE_VI, Map.of("không", TRACKING_NONE, "có", TRACKING_LOT),
            LOCALE_EN, Map.of("no", TRACKING_NONE, "yes", TRACKING_LOT));

    private FixedTemplateSchema() {
    }

    public static List<Column> columns(DataImportKind kind) {
        return switch (kind) {
            case ITEMS -> ITEM_COLUMNS;
            case SUPPLIERS -> SUPPLIER_COLUMNS;
            case CUSTOMERS -> CUSTOMER_COLUMNS;
            case BOMS -> BOM_COLUMNS;
        };
    }

    public static String templateVersion(DataImportKind kind) {
        return kind == DataImportKind.BOMS ? BOM_TEMPLATE_VERSION : TEMPLATE_VERSION;
    }

    public static String dataSheetName(DataImportKind kind, String locale) {
        return kind == DataImportKind.BOMS ? BOM_DATA_SHEET : dataSheetName(locale);
    }

    public static String dataSheetName(String locale) {
        return LOCALE_VI.equals(locale) ? "Dữ liệu" : "Data";
    }

    public static boolean isSupportedLocale(String locale) {
        return LOCALE_VI.equals(locale) || LOCALE_EN.equals(locale);
    }

    /** Excel column letter for a 0-based index (A..Z is enough: the widest template, BOM, has 9 columns). */
    public static String columnLetter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    public static Optional<String> columnLetter(DataImportKind kind, String field) {
        List<Column> columns = columns(kind);
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).field().equals(field)) {
                return Optional.of(columnLetter(index));
            }
        }
        return Optional.empty();
    }

    /** The locale's label or the canonical name, compared case-insensitively; empty when neither. */
    public static Optional<String> canonicalItemType(String locale, String value) {
        String key = comparable(value);
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (ITEM_TYPES.contains(upper)) {
            return Optional.of(upper);
        }
        return Optional.ofNullable(ITEM_TYPE_LABELS.getOrDefault(locale, Map.of()).get(key));
    }

    public static Optional<String> canonicalTracking(String locale, String value) {
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (TRACKING_NONE.equals(upper) || TRACKING_LOT.equals(upper)) {
            return Optional.of(upper);
        }
        return Optional.ofNullable(TRACKING_LABELS.getOrDefault(locale, Map.of()).get(comparable(value)));
    }

    /** Canonical row values → the field names and value shapes the kind's target handler expects. */
    public static Map<String, String> toHandlerValues(DataImportKind kind, Map<String, String> values) {
        Map<String, String> handlerValues = new LinkedHashMap<>();
        if (kind == DataImportKind.ITEMS) {
            handlerValues.put(ItemImportTargetHandler.FIELD_CODE, values.get(CODE));
            handlerValues.put(ItemImportTargetHandler.FIELD_NAME, values.get(NAME));
            handlerValues.put(ItemImportTargetHandler.FIELD_TYPE, values.get(TYPE));
            handlerValues.put(ItemImportTargetHandler.FIELD_UNIT, values.get(UNIT));
            handlerValues.put(ItemImportTargetHandler.FIELD_LOT_TRACKED,
                    String.valueOf(TRACKING_LOT.equals(values.get(TRACKING))));
            // FE §1.2: serial tracking is out of scope for the template, never imported as true.
            handlerValues.put(ItemImportTargetHandler.FIELD_SERIAL_TRACKED, "false");
            return handlerValues;
        }
        // FE Q9: an empty optional cell becomes null in the master, not an empty string.
        handlerValues.put(PartnerImportTargetHandler.FIELD_CODE, values.get(CODE));
        handlerValues.put(PartnerImportTargetHandler.FIELD_NAME, values.get(NAME));
        handlerValues.put(PartnerImportTargetHandler.FIELD_CONTACT_NAME, emptyToNull(values.get(CONTACT)));
        handlerValues.put(PartnerImportTargetHandler.FIELD_EMAIL, emptyToNull(values.get(EMAIL)));
        handlerValues.put(PartnerImportTargetHandler.FIELD_PHONE, emptyToNull(values.get(PHONE)));
        handlerValues.put(PartnerImportTargetHandler.FIELD_ADDRESS, emptyToNull(values.get(ADDRESS)));
        handlerValues.put(PartnerImportTargetHandler.FIELD_NOTES, emptyToNull(values.get(NOTES)));
        return handlerValues;
    }

    /** A target handler's field name back to the template field it came from. */
    public static String fromHandlerField(DataImportKind kind, String handlerField) {
        if (handlerField == null) {
            return null;
        }
        if (kind == DataImportKind.ITEMS
                && (ItemImportTargetHandler.FIELD_LOT_TRACKED.equals(handlerField)
                || ItemImportTargetHandler.FIELD_SERIAL_TRACKED.equals(handlerField))) {
            return TRACKING;
        }
        if (kind != DataImportKind.ITEMS && PartnerImportTargetHandler.FIELD_CONTACT_NAME.equals(handlerField)) {
            return CONTACT;
        }
        return handlerField;
    }

    /** NFC + trim + lower case, for comparing labels and headers typed by hand in Excel. */
    public static String comparable(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

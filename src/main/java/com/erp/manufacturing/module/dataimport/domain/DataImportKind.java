package com.erp.manufacturing.module.dataimport.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The three catalogues the {@code omniplant-import-v1} template can fill (G3), with everything that
 * differs between them: the wire value FE uses, the import target, the permission pair and whether
 * the batch is authorised against a plant.
 *
 * <p>Permissions are the catalogue's own READ + MANAGE pair, never {@code PERM_DATA_IMPORT_*}
 * (NEXT_PHASE_PLAN.md §10.2 D3): importing customers is creating customers, and a READ of one
 * catalogue never combines with the MANAGE of another.
 */
public enum DataImportKind {

    /**
     * Items are company master data, but FE authorises the import against the selected plant (FE Q2):
     * a PLANT assignment, a COMPANY assignment covering that plant, or GLOBAL.
     */
    ITEMS("items", ImportTargetType.ITEM, "PERM_ITEM_READ", "PERM_ITEM_MANAGE", true),

    SUPPLIERS("suppliers", ImportTargetType.SUPPLIER, "PERM_SUPPLIER_READ", "PERM_SUPPLIER_MANAGE", false),

    CUSTOMERS("customers", ImportTargetType.CUSTOMER, "PERM_CUSTOMER_READ", "PERM_CUSTOMER_MANAGE", false);

    private final String wireValue;
    private final ImportTargetType targetType;
    private final String readPermission;
    private final String managePermission;
    private final boolean plantScoped;

    DataImportKind(String wireValue, ImportTargetType targetType, String readPermission,
                   String managePermission, boolean plantScoped) {
        this.wireValue = wireValue;
        this.targetType = targetType;
        this.readPermission = readPermission;
        this.managePermission = managePermission;
        this.plantScoped = plantScoped;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    public ImportTargetType targetType() {
        return targetType;
    }

    public String readPermission() {
        return readPermission;
    }

    public String managePermission() {
        return managePermission;
    }

    public boolean plantScoped() {
        return plantScoped;
    }

    /** Lower-case wire value only ({@code items}, {@code suppliers}, {@code customers}). */
    public static Optional<DataImportKind> fromWire(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(kind -> kind.wireValue.equals(normalized)).findFirst();
    }

    public static Optional<DataImportKind> fromTargetType(ImportTargetType targetType) {
        return Arrays.stream(values()).filter(kind -> kind.targetType == targetType).findFirst();
    }

    public static List<String> wireValues() {
        return Arrays.stream(values()).map(DataImportKind::wireValue).toList();
    }
}

package com.erp.manufacturing.module.dataimport.target;

/**
 * The kinds of value a target field can hold, as far as the importer is concerned.
 *
 * <p>DECIMAL and DATE are included in the framework contract even though ITEM does not use them;
 * the transform catalog can therefore validate profiles before later targets are introduced.
 */
public enum ImportFieldType {

    /** Free text, bounded by {@code maxLength}. */
    STRING,

    /** {@code "true"}/{@code "false"} after transforms — see {@code CellTransform.BOOLEAN_VN}. */
    BOOLEAN,

    /** One of {@code allowedValues}, compared case-sensitively after transforms. */
    ENUM,

    /** Canonical decimal text accepted by {@link java.math.BigDecimal}. */
    DECIMAL,

    /** Canonical ISO local date ({@code yyyy-MM-dd}). */
    DATE
}

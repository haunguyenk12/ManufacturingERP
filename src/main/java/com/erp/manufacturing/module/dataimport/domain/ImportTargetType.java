package com.erp.manufacturing.module.dataimport.domain;

/**
 * What a spreadsheet is being imported into.
 *
 * <p>Only the targets that actually have a handler are listed. Adding a constant here without an
 * {@code ImportTargetHandler} would put a choice in the API that fails at runtime, so the enum grows
 * one entry per slice: UOM, item-warehouse settings, BOM and opening stock are queued but
 * deliberately absent until their handler exists.
 */
public enum ImportTargetType {

    /** Item master ({@code items}), created through {@code ItemService.createItem}. */
    ITEM,

    /** Supplier master ({@code suppliers}), created through {@code SupplierService.createForCompany} (G3). */
    SUPPLIER,

    /** Customer master ({@code customers}), created through {@code CustomerService.create} (G3). */
    CUSTOMER,

    /**
     * BOM drafts (G5), created through {@code BomService.saveDraft} — one BOM per group of rows. Only
     * the fixed-template path ({@code omniplant-bom-v1}) imports it: a BOM spans several rows, which the
     * mapped-profile path's one-row-one-record handlers cannot express, so no {@code ImportTargetHandler}
     * is registered and that path answers "import target not found" for it.
     */
    BOM,

    /**
     * Sales order drafts (G6c), created through {@code SalesOrderService.createDraft} — one order per
     * group of rows ({@code omniplant-so-v1}). Fixed-template path only, for the same reason as {@link #BOM}.
     */
    SALES_ORDER
}

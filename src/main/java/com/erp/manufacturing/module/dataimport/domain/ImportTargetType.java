package com.erp.manufacturing.module.dataimport.domain;

/**
 * What a spreadsheet is being imported into.
 *
 * <p>Only the targets that actually have a handler are listed. Adding a constant here without an
 * {@code ImportTargetHandler} would put a choice in the API that fails at runtime, so the enum grows
 * one entry per slice: supplier/UOM, item-warehouse settings, BOM and opening stock are queued but
 * deliberately absent until their handler exists.
 */
public enum ImportTargetType {

    /** Item master ({@code items}), created through {@code ItemService.createItem}. */
    ITEM
}

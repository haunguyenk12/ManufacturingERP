package com.erp.manufacturing.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits for the spreadsheet importer, bound from {@code app.data-import.*}.
 *
 * <p>{@code maxRows} is not a tuning knob. Apply writes each row in its own transaction on the
 * request thread, so the ceiling is really a statement about how long an HTTP request may run.
 * Raising it past a few thousand is a decision to move apply off the request thread — a different
 * design, not a bigger number — which is why the upper bound is enforced here rather than left to
 * whoever edits the YAML.
 *
 * <p>The two {@code fixedTemplate*} limits belong to {@code /v1/data-import/batches} only (G3, D5)
 * and deliberately do not lower the mapped-profile path's {@code maxRows}. That path commits the whole
 * batch in <em>one</em> transaction, so its ceiling is lower and its upper bound tighter.
 */
@ConfigurationProperties(prefix = "app.data-import")
@Validated
public record DataImportProperties(

        @Min(1)
        @Max(20_000)
        int maxRows,

        @Min(1)
        @Max(2_000)
        int fixedTemplateMaxRows,

        @Min(1)
        @Max(10L * 1024 * 1024)
        long fixedTemplateMaxFileBytes) {

    public static final int DEFAULT_FIXED_TEMPLATE_MAX_ROWS = 500;
    public static final long DEFAULT_FIXED_TEMPLATE_MAX_FILE_BYTES = 5L * 1024 * 1024;

    public DataImportProperties {
        if (maxRows == 0) {
            maxRows = 5_000;
        }
        if (fixedTemplateMaxRows == 0) {
            fixedTemplateMaxRows = DEFAULT_FIXED_TEMPLATE_MAX_ROWS;
        }
        if (fixedTemplateMaxFileBytes == 0) {
            fixedTemplateMaxFileBytes = DEFAULT_FIXED_TEMPLATE_MAX_FILE_BYTES;
        }
    }
}

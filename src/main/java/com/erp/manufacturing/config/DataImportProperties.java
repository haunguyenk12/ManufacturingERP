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
 */
@ConfigurationProperties(prefix = "app.data-import")
@Validated
public record DataImportProperties(

        @Min(1)
        @Max(20_000)
        int maxRows) {

    public DataImportProperties {
        if (maxRows == 0) {
            maxRows = 5_000;
        }
    }
}

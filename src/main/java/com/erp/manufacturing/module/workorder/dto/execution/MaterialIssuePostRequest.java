package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MaterialIssuePostRequest(
        @Size(max = 2000) String note,
        @NotEmpty @Size(max = 500) List<@Valid MaterialIssueLineRequest> lines,
        /**
         * G8b (D5): the work order version last read. Required on {@code POST /work-orders/{id}/material-issues};
         * the older single-line paths ({@code component-issues}, flat {@code POST /material-issues}) build this
         * request internally without it.
         */
        Long expectedWorkOrderVersion,
        /**
         * G8b (FE §1.4 step 5, Q9): {@code true} — a supplemental issue from free stock, every line carries a
         * reason code and a reason, any active warehouse of the plant; {@code false} — a normal issue, which may
         * not go over the BOM ({@code OVER_BOM_APPROVAL_REQUIRED}); absent — the behaviour before G8b, where an
         * over-BOM line with a reason turns the whole request into a pending approval.
         */
        Boolean supplemental
) {

    /** Shape before G8b. */
    public MaterialIssuePostRequest(String note, List<MaterialIssueLineRequest> lines) {
        this(note, lines, null, null);
    }
}

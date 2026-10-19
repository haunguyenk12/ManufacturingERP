package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * G8b: body of approve, reject and the requester's cancel of a pending material issue. Both versions are
 * required (D5) — the issue's and its work order's, read together from the decision screen. {@code reason}
 * is required on reject and cancel (5–2000 characters, FE Q33) and optional on approve.
 */
public record MaterialIssueVersionedDecisionRequest(
        @NotNull Long expectedWorkOrderVersion,
        @NotNull Long expectedIssueVersion,
        @Size(max = 2000) String reason
) {}

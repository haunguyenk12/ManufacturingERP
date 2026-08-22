package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.Size;

public record MaterialIssueDecisionRequest(
        @Size(max = 2000) String reason
) {}

package com.erp.manufacturing.module.planning.dto;

import jakarta.validation.constraints.Size;

public record SupplySuggestionDecisionRequest(
        @Size(max = 2000) String decisionNote
) {}

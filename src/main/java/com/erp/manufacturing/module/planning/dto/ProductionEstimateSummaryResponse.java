package com.erp.manufacturing.module.planning.dto;

public record ProductionEstimateSummaryResponse(
        int totalLineCount,
        int shortageLineCount,
        boolean feasible
) {}

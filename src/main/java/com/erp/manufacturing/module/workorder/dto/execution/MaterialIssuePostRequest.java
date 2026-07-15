package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MaterialIssuePostRequest(
        @Size(max = 2000) String note,
        @NotEmpty List<@Valid MaterialIssueLineRequest> lines
) {}

package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesOrderCreateRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        @NotBlank @Size(max = 100) String orderNo,
        @NotBlank @Size(max = 255) String customerName,
        @NotNull LocalDate orderDate,
        @Size(max = 2000) String note,
        @NotEmpty List<@Valid SalesOrderLineRequest> lines
) {}

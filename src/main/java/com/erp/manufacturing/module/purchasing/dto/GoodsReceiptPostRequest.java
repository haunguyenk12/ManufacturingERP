package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GoodsReceiptPostRequest(
        @NotBlank @Size(max = 100) String receiptNo,
        @Size(max = 2000) String note,
        @NotEmpty List<@Valid GoodsReceiptLineRequest> lines
) {}

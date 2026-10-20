package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * G9a (FE §3.3): full replacement of the editable fields of a DRAFT receipt. The source — work order, item and
 * destination warehouse — is fixed at creation and cannot be changed here.
 */
public record ProductionReceiptUpdateRequest(
        @NotNull Long expectedVersion,
        @NotNull Long expectedWorkOrderVersion,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity,
        @Size(max = 120) String lotNumber,
        @NotNull LocalDate receivedOn,
        LocalDate expiresOn,
        @Size(max = 2000) String note
) {}

package com.erp.manufacturing.module.dataimport.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Points an already-uploaded run at a mapping profile and re-checks every staged row.
 *
 * <p>Separate from upload so that a user whose headers did not match can try another profile without
 * sending the file again — that retry loop is the normal case when a customer's columns are unknown,
 * not an edge case.
 */
public record ImportValidateRequest(@NotNull UUID profileId) {
}

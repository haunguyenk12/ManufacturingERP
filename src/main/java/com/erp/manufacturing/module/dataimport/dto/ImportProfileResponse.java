package com.erp.manufacturing.module.dataimport.dto;

import com.erp.manufacturing.module.dataimport.domain.ColumnMapping;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A stored mapping profile.
 *
 * <p>{@code mappings} returns the domain value object unchanged: it is a JSON document with no
 * identity, no lazy association and no field the caller may not see, so wrapping it in a parallel
 * response record would produce two definitions of the same shape that could drift apart.
 */
public record ImportProfileResponse(UUID profileId,
                                    String code,
                                    String name,
                                    String targetType,
                                    UUID companyId,
                                    String sheetName,
                                    Integer headerRowIndex,
                                    Integer firstDataRowIndex,
                                    List<ColumnMapping> mappings,
                                    String status,
                                    Instant createdAt,
                                    Instant updatedAt) {
}

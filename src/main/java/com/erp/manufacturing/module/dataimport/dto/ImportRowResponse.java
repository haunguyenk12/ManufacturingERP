package com.erp.manufacturing.module.dataimport.dto;

import com.erp.manufacturing.module.dataimport.domain.ImportCellError;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One staged row and what happened to it.
 *
 * <p>The piece the standard error envelope cannot carry. {@code ApiResponse.errors} is a flat list of
 * {@code (field, message)} that only travels on a failed call; a validated import is a <em>successful</em>
 * call whose payload happens to describe a thousand bad rows. So this travels inside {@code result},
 * paged, and the HTTP status stays 200.
 *
 * <p>{@code rawCells} is returned alongside {@code mappedValues} so the user can see their own
 * spelling next to what the system made of it — without that, "invalid value" is unactionable.
 */
public record ImportRowResponse(UUID importRowId,
                                Integer rowNumber,
                                String status,
                                Map<String, String> rawCells,
                                Map<String, String> mappedValues,
                                List<ImportCellError> errors,
                                UUID createdEntityId) {
}

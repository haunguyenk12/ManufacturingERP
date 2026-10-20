package com.erp.manufacturing.module.workorder.dto.materialreturn;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** G8d: a material return — 1 to 500 lines, a reason of 5–2000 characters (FE Q33), the work order version read. */
public record MaterialReturnPostRequest(
        @NotNull Long expectedWorkOrderVersion,
        String reason,
        @NotEmpty @Size(max = 500) List<@Valid @NotNull MaterialReturnLineRequest> lines
) {}

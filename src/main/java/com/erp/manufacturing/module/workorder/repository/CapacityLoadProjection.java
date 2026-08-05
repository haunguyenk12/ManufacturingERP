package com.erp.manufacturing.module.workorder.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One (work center, calendar day) bucket of already-scheduled load minutes — see C2-8. */
public interface CapacityLoadProjection {
    UUID getWorkCenterId();
    LocalDate getDay();
    BigDecimal getLoadMinutes();
}

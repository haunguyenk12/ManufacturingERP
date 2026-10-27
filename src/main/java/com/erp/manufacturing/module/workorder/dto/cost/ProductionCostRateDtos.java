package com.erp.manufacturing.module.workorder.dto.cost;

import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** G11a: the wire shapes of the hourly cost rates of work centers (FE C1, C2, C13). */
public final class ProductionCostRateDtos {

    private ProductionCostRateDtos() {
    }

    /**
     * C2 body. Rates are VND per hour of operating time, {@code 0..999 999 999} with at most 6 decimals (FE Q5); 0 is a
     * valid, known rate (FE A05). {@code expectedVersion} is the current rate version of the work center, 0 before the
     * first save. The reason (5–2000 after trim, FE Q33) is checked by the service.
     */
    public record RateRequest(
            @NotNull @Min(0) Integer expectedVersion,
            String currency,
            @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer = 9, fraction = 6) BigDecimal laborRate,
            @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer = 9, fraction = 6) BigDecimal overheadRate,
            String reason) {
    }

    /** C2 success: identity and new version only — the client reads the rate back (FE §3.4). */
    public record RateCommandResult(UUID workCenterId, Integer rateVersion) {
    }

    /** Who did something: id and username (FE §3.3 {@code actor}). */
    public record ActorRef(UUID id, String displayName) {
    }

    /** One saved rate version — the current one in C1, every one in C13. */
    public record RateResponse(UUID rateId, Integer rateVersion, BigDecimal laborRate, BigDecimal overheadRate,
                               String currency, String reason, ActorRef actor, Instant at) {
    }

    /**
     * C1 row: the minimal work center reference a rate-only user needs (FE R28) and its current rate, {@code null} when
     * the center was never priced. {@code rateVersion} is what C2 expects next as {@code expectedVersion}.
     */
    public record RateRowResponse(UUID workCenterId, UUID plantId, String code, String name, OrganizationStatus status,
                                  Integer capacityUnits, Integer rateVersion, RateResponse rate) {
    }
}

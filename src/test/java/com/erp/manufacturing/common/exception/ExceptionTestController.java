package com.erp.manufacturing.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Test-only controller that deliberately throws each exception type
 * {@link GlobalExceptionHandler} maps, so the handler can be exercised via {@code @WebMvcTest}
 * without borrowing a real business controller. Mirrors the {@code SecurityTestController} pattern.
 */
@RestController
@RequestMapping("/v1/test/exceptions")
class ExceptionTestController {

    static final String DEFAULT_DUPLICATE_DETAIL =
            "ERROR: duplicate key value violates unique constraint \"uk_companies_code\"";

    @GetMapping("/app")
    String app() {
        throw new AppException(BusinessErrorCode.INSUFFICIENT_STOCK);
    }

    @GetMapping("/multi")
    String multi() {
        throw new MultiErrorException(ValidationErrorCode.INVALID_INPUT,
                Map.of("quantity", "must be positive"));
    }

    @PostMapping("/validation")
    String validation(@Valid @RequestBody ValidationTestRequest body) {
        return "ok";
    }

    @GetMapping("/access-denied")
    String accessDenied() {
        throw new AccessDeniedException("no");
    }

    /**
     * Shaped like the real query endpoints ({@code GET /inventory/movements?warehouseId=}): a required
     * {@code @RequestParam} with no default. Calling it without the parameter is what Spring turns into
     * {@code MissingServletRequestParameterException}.
     */
    @GetMapping("/required-param")
    String requiredParam(@RequestParam UUID warehouseId) {
        return warehouseId.toString();
    }

    /**
     * EH-3: the answer now depends on <em>which</em> constraint failed, so the test drives the driver
     * message. The default is a real PostgreSQL unique-violation string rather than the placeholder
     * {@code "dup"} that used to be here, because "409 duplicate" is only a meaningful assertion when
     * the input actually describes a duplicate.
     */
    @GetMapping("/data-integrity")
    String dataIntegrity(@RequestParam(defaultValue = DEFAULT_DUPLICATE_DETAIL) String detail) {
        throw new DataIntegrityViolationException(detail);
    }

    /**
     * What JPA throws when a {@code @Version} check loses a race (spec §10.3). Constructed directly
     * rather than provoked with a real entity: the handler's job is to map the exception, and the
     * proof that {@code @Version} actually raises it on {@code work_orders} belongs at the database
     * layer, in {@code WorkOrderRepositoryIT}.
     */
    @GetMapping("/optimistic-lock")
    String optimisticLock() {
        throw new ObjectOptimisticLockingFailureException("WorkOrder", "some-id");
    }

    @GetMapping("/generic")
    String generic() {
        throw new RuntimeException("boom - internal detail");
    }

    record ValidationTestRequest(@NotBlank String name) {
    }
}

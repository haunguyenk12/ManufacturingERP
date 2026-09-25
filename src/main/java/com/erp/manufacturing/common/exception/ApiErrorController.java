package com.erp.manufacturing.common.exception;

import com.erp.manufacturing.common.response.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Last-resort envelope for anything that never reaches {@link GlobalExceptionHandler} (EH-1).
 *
 * <p>{@code @RestControllerAdvice} only covers exceptions raised inside the {@code DispatcherServlet}.
 * Anything that fails earlier — in the servlet container itself, or in a filter that did not write a
 * response of its own — is forwarded by the container to {@code /error}, where Spring Boot's
 * {@code BasicErrorController} answers with {@code {timestamp,status,error,path}}: a JSON shape the
 * frontend has never been taught to read, breaking the single-envelope promise of
 * {@code .claude/rules/error-handling.md} §5.1. Declaring an {@link ErrorController} bean here
 * replaces that controller, so <em>every</em> HTTP error leaves this application as
 * {@code {code, result, message}}.
 *
 * <p>This is a net, not a routing table: the security filters
 * ({@code JwtAuthenticationFilter}, {@code RateLimitFilter}, {@code UserRateLimitFilter}) each write
 * their own envelope and never fall through to here, and ordinary controller failures are mapped
 * with far more precision by {@link GlobalExceptionHandler}. Reaching this class therefore means
 * something unforeseen happened, which is why it says as little as possible: the status is
 * translated to an {@link ErrorCode} and nothing about the underlying failure is echoed back.
 */
@RestController
@Slf4j
public class ApiErrorController implements ErrorController {

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        ErrorCode  code   = resolveErrorCode(status);

        log.warn("[{}] Container-level error dispatch: status={} forwardedFrom={}",
                code.code(), status.value(),
                request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

        return ResponseEntity.status(status).body(ApiResponse.error(code));
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (raw instanceof Integer statusCode) {
            HttpStatus resolved = HttpStatus.resolve(statusCode);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Deliberately narrow. Only the statuses the container can realistically produce on its own are
     * named; everything else answers {@code INTERNAL_SERVER_ERROR} rather than inventing a code for a
     * situation nobody has observed ({@code .claude/rules/coding-rules.md} §11.5).
     */
    private ErrorCode resolveErrorCode(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND            -> ValidationErrorCode.RESOURCE_NOT_FOUND;
            case UNAUTHORIZED         -> AuthErrorCode.AUTHENTICATION_REQUIRED;
            case FORBIDDEN            -> AuthErrorCode.ACCESS_DENIED;
            case BAD_REQUEST,
                 METHOD_NOT_ALLOWED   -> ValidationErrorCode.INVALID_INPUT;
            case TOO_MANY_REQUESTS    -> BusinessErrorCode.RATE_LIMIT_EXCEEDED;
            default                   -> BusinessErrorCode.INTERNAL_SERVER_ERROR;
        };
    }
}

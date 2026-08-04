package com.erp.manufacturing.common.exception;

import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.security.TraceIdFilter;
import com.erp.manufacturing.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link GlobalExceptionHandler} – locks the {@code {code,result,message}}
 * envelope (`.claude/rules/error-handling.md` §5.1) for every exception branch it maps.
 *
 * <p>Exercises the handler against {@link ExceptionTestController} (test-only), the only
 * controller in the slice, so each branch of {@code GlobalExceptionHandler} can be triggered
 * deterministically.
 *
 * <p>{@code @WebMvcTest} auto-detects every {@code Filter}-typed bean on the classpath
 * (not just the ones explicitly {@code @Import}-ed), so the real security filters
 * ({@code JwtAuthenticationFilter}, {@code RateLimitFilter}, {@code UserRateLimitFilter}) are
 * still constructed here even though only {@link TraceIdFilter} is imported on purpose (D5).
 * Their infrastructure dependencies are mocked purely so the context can start; with no
 * {@code Authorization} header and {@code rateLimitProperties.enabled() == false} (Mockito
 * default), they no-op and never intercept these requests.
 */
@WebMvcTest(controllers = ExceptionTestController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class})
@WithMockUser
@DisplayName("GlobalExceptionHandler – response envelope contract")
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    IpExtractor ipExtractor;

    // Unused directly by these tests – required only so the auto-detected security filters
    // (JwtAuthenticationFilter, RateLimitFilter, UserRateLimitFilter) can be constructed.
    @MockBean
    JwtTokenProvider jwtTokenProvider;
    @MockBean
    TokenStoreService tokenStoreService;
    @MockBean
    UserDetailsService userDetailsService;
    @MockBean
    RedisTemplate<String, String> redisTemplate;
    @MockBean
    RateLimitProperties rateLimitProperties;

    @BeforeEach
    void setUp() {
        when(ipExtractor.extract(any(HttpServletRequest.class))).thenReturn("127.0.0.1");
    }

    @Test
    @DisplayName("AppException maps to the ErrorCode's declared HTTP status and code")
    void appException_mapsToDeclaredHttpStatusAndErrorCode() throws Exception {
        mockMvc.perform(get("/api/v1/test/exceptions/app"))
                .andExpect(status().is(BusinessErrorCode.INSUFFICIENT_STOCK.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.INSUFFICIENT_STOCK.code()))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("MultiErrorException with field errors returns the errors array of {field, message}")
    void multiErrorException_withFieldErrors_returnsFieldErrorsArray() throws Exception {
        mockMvc.perform(get("/api/v1/test/exceptions/multi"))
                .andExpect(status().is(ValidationErrorCode.INVALID_INPUT.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].message").value("must be positive"));
    }

    @Test
    @DisplayName("@Valid failure on missing required field returns 400 INVALID_INPUT with a structured errors array")
    void validationFailure_missingRequiredField_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/test/exceptions/validation")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    @DisplayName("Malformed JSON body returns 400 INVALID_INPUT instead of falling through to the 500 catch-all")
    void malformedJsonBody_returns400InvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/test/exceptions/validation")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
    }

    /**
     * A missing required query param used to fall through to the catch-all and answer 500 — the shape
     * of the {@code GET /inventory/movements} report of 2026-08-04. Asserting the {@code errors[]} entry
     * as well as the code is what makes this bite: a handler that returns a bare 400 without naming the
     * parameter leaves the caller guessing which one it forgot.
     */
    @Test
    @DisplayName("Missing required query param returns 400 INVALID_INPUT naming the param, not a 500")
    void missingRequiredQueryParam_returns400NamingTheParameter() throws Exception {
        mockMvc.perform(get("/api/v1/test/exceptions/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("warehouseId"))
                .andExpect(jsonPath("$.errors[0].message").exists())
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("Spring Security AccessDeniedException returns 403 ACCESS_DENIED")
    void accessDeniedException_returns403AccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/test/exceptions/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.ACCESS_DENIED.code()));
    }

    @Test
    @DisplayName("DataIntegrityViolationException returns 409 RESOURCE_ALREADY_EXISTS")
    void dataIntegrityViolation_returns409ResourceAlreadyExists() throws Exception {
        mockMvc.perform(get("/api/v1/test/exceptions/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code()));
    }

    /**
     * Spec §10.3 / §11 "Tests": a lost {@code @Version} race must surface as
     * {@code CONCURRENT_MODIFICATION}, not as some other 409 and certainly not as a 500. Asserting
     * {@code $.code} rather than only the status is what makes this meaningful — three different
     * error codes in this codebase map to 409, so a status-only assertion would pass under any of
     * them (rule R8).
     */
    @Test
    @DisplayName("ObjectOptimisticLockingFailureException returns 409 CONCURRENT_MODIFICATION")
    void optimisticLockConflict_returns409ConcurrentModification() throws Exception {
        mockMvc.perform(get("/api/v1/test/exceptions/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.CONCURRENT_MODIFICATION.code()))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("Unhandled generic exception returns 500 without leaking the original message or stack trace")
    void genericException_returns500WithoutLeakingStackTrace() throws Exception {
        String body = mockMvc.perform(get("/api/v1/test/exceptions/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.INTERNAL_SERVER_ERROR.code()))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("boom - internal detail")
                .doesNotContain("at com.erp")
                .doesNotContain("RuntimeException");
    }
}

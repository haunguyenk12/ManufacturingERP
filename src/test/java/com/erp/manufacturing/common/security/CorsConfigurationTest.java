package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.config.CorsProperties;
import com.erp.manufacturing.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks the CORS contract added in {@code C2-5}.
 *
 * <p>Before it, the application had no {@code CorsConfigurationSource} at all: Spring Boot's default
 * chain still contained a {@code CorsFilter}, so the wiring <em>looked</em> present while allowing
 * nothing — the frontend could not call a single endpoint cross-origin no matter how correct the API
 * was. A configuration this easy to believe in without proof needs tests that go through the real
 * filter chain, which is why {@code SecurityConfig} is imported rather than mocked.
 *
 * <p>{@link CorsProperties} is supplied as a <strong>real</strong> instance with fixed origins
 * (rule {@code R3} — it carries the wildcard validation, so mocking it would delete the thing under
 * test) instead of binding {@code application.yml}, so these assertions do not silently change
 * meaning when someone edits the deployed origin list.
 */
@WebMvcTest(controllers = SecurityTestController.class)
@Import({
        com.erp.manufacturing.config.SecurityConfig.class,
        TraceIdFilter.class,
        RateLimitFilter.class,
        JwtAuthenticationFilter.class,
        UserRateLimitFilter.class,
        JwtAuthEntryPoint.class,
        JwtAccessDeniedHandler.class,
        CorsConfigurationTest.FixedCorsProperties.class,
})
@DisplayName("CORS – preflight and origin allow-list")
class CorsConfigurationTest {

    private static final String ALLOWED = "http://localhost:5173";
    private static final String FOREIGN = "http://evil.example.com";

    @TestConfiguration
    static class FixedCorsProperties {
        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties(
                    List.of(ALLOWED),
                    List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"),
                    List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Plant-Id"),
                    List.of("X-Trace-Id"),
                    true,
                    3600);
        }
    }

    @Autowired MockMvc mockMvc;

    @MockBean RedisTemplate<String, String>                   redisTemplate;
    @MockBean JwtTokenProvider                                jwtTokenProvider;
    @MockBean TokenStoreService                               tokenStoreService;
    @MockBean UserDetailsService                              userDetailsService;
    @MockBean AuditLogService                                 auditLogService;
    @MockBean RateLimitProperties                             rateLimitProperties;
    @MockBean IpExtractor                                     ipExtractor;
    @MockBean com.erp.manufacturing.config.SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        when(ipExtractor.extract(any(HttpServletRequest.class))).thenReturn("127.0.0.1");
    }

    /**
     * The preflight carries no {@code Authorization} header, so it can never satisfy
     * {@code .anyRequest().authenticated()}. If CORS were wired as a plain {@code WebMvcConfigurer}
     * instead of through {@code http.cors(...)}, this would answer 401 and the browser would block
     * every real request that follows — while a direct (non-browser) curl kept working, which is how
     * this class of bug survives manual testing.
     */
    @Test
    @DisplayName("Preflight from an allowed origin passes without a token")
    void preflight_fromAllowedOrigin_isAnsweredWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/v1/test/ping")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    /** {@code Idempotency-Key} and {@code X-Plant-Id} are ours; a preflight that rejects them
     *  blocks every sensitive POST and every plant-scoped call. */
    @Test
    @DisplayName("Preflight allows the custom headers the API actually requires")
    void preflight_allowsIdempotencyKeyAndPlantIdHeaders() throws Exception {
        mockMvc.perform(options("/v1/inventory/receive")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Idempotency-Key, X-Plant-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
    }

    @Test
    @DisplayName("Preflight from an origin outside the allow-list is refused")
    void preflight_fromForeignOrigin_isRefused() throws Exception {
        mockMvc.perform(options("/v1/test/ping")
                        .header("Origin", FOREIGN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    /**
     * A cross-origin GET from a foreign origin still reaches the chain (CORS is enforced by the
     * browser reading the response headers), so the assertion that matters is the <em>absence</em> of
     * {@code Access-Control-Allow-Origin} — with it present, any site could read authenticated data.
     */
    @Test
    @DisplayName("Actual request from a foreign origin gets no Allow-Origin header")
    void actualRequest_fromForeignOrigin_isNotGrantedAllowOrigin() throws Exception {
        mockMvc.perform(get("/v1/test/ping").header("Origin", FOREIGN))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    /** {@code X-Trace-Id} is what the frontend quotes in a bug report; unexposed, JS cannot read it. */
    @Test
    @DisplayName("Trace id is exposed to the browser on an allowed cross-origin request")
    void allowedOrigin_exposesTraceIdHeaderToTheBrowser() throws Exception {
        mockMvc.perform(get("/v1/test/ping").header("Origin", ALLOWED))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.containsString("X-Trace-Id")));
    }

    /**
     * Wildcard plus credentials is refused at startup rather than at the first cross-origin request:
     * Spring's own failure surfaces inside the filter and reads as a runtime bug, while this one
     * names the property and the reason.
     */
    @Test
    @DisplayName("Wildcard origin is refused at startup because the API is credentialed")
    void wildcardOrigin_isRejectedWithAnExplanation() {
        CorsProperties wildcard = new CorsProperties(
                List.of("*"), List.of("GET"), List.of("Authorization"), List.of(), true, 3600);

        assertThatThrownBy(wildcard::validate)
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains("app.cors.allowed-origins")
                        .contains("Authorization"));
    }
}

package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EH-1 safety net for the two Redis-backed rate-limit filters.
 *
 * <p>Both run ahead of the {@code DispatcherServlet}, so an exception escaping either one never
 * reaches {@code GlobalExceptionHandler}: the container forwards to {@code /error} and the caller
 * receives a JSON shape ({@code {timestamp,status,error,path}}) the frontend has never been taught to
 * read. Redis being unreachable is the realistic way to trigger that, and it is exactly the moment a
 * client most needs a parseable answer.
 *
 * <p>Each filter is covered twice on purpose: once for the failure branch, once to prove the ordinary
 * pass-through still works. Without the second case the first would also pass if the filter simply
 * refused every request.
 */
@DisplayName("Rate-limit filters - error envelope on infrastructure failure")
class RateLimitFilterErrorEnvelopeTest {

    private static final String REDIS_DETAIL = "redis down at 10.0.0.7:6379";

    private final RateLimitProperties noRules = new RateLimitProperties(true, List.of());

    @Nested
    @DisplayName("RateLimitFilter (IP scope)")
    class IpScope {

        @SuppressWarnings("unchecked")
        private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        private final IpExtractor ipExtractor = mock(IpExtractor.class);
        // R3: ObjectMapper carries real serialization logic, so it is a real instance, not a mock.
        private final RateLimitFilter filter = new RateLimitFilter(
                noRules, redisTemplate, ipExtractor, new ObjectMapper());

        @Test
        @DisplayName("Redis failure answers the standard envelope without leaking the cause")
        void redisFailure_answersTheStandardEnvelopeWithoutLeakingTheCause() throws Exception {
            when(ipExtractor.extract(any(HttpServletRequest.class))).thenReturn("10.0.0.1");
            when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException(REDIS_DETAIL));
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request(), response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentAsString())
                    .contains(BusinessErrorCode.INTERNAL_SERVER_ERROR.code())
                    .doesNotContain(REDIS_DETAIL)
                    .doesNotContain("RedisConnectionFailureException");
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("a request under the limit still passes straight through")
        void requestUnderTheLimit_passesThrough() throws Exception {
            when(ipExtractor.extract(any(HttpServletRequest.class))).thenReturn("10.0.0.1");
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn(null);   // not blacklisted
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            MockHttpServletRequest request = request();
            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("UserRateLimitFilter (USER scope)")
    class UserScope {

        @SuppressWarnings("unchecked")
        private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

        @Test
        @DisplayName("Redis failure answers the standard envelope without leaking the cause")
        void redisFailure_answersTheStandardEnvelopeWithoutLeakingTheCause() throws Exception {
            // A rule must match before Redis is touched at all, so this filter gets a real rule list.
            UserRateLimitFilter filter = new UserRateLimitFilter(
                    new RateLimitProperties(true, List.of(new RateLimitProperties.RateLimitRule(
                            "user-writes", RateLimitProperties.Scope.USER, "/**", 100, 60,
                            RateLimitProperties.Action.BLOCK))),
                    redisTemplate, new ObjectMapper());
            when(redisTemplate.opsForHash()).thenThrow(new RedisConnectionFailureException(REDIS_DETAIL));
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(authenticatedRequest(), response, chain);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentAsString())
                    .contains(BusinessErrorCode.INTERNAL_SERVER_ERROR.code())
                    .doesNotContain(REDIS_DETAIL)
                    .doesNotContain("RedisConnectionFailureException");
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("an authenticated request matching no rule still passes straight through")
        void noMatchingRule_passesThrough() throws Exception {
            UserRateLimitFilter filter = new UserRateLimitFilter(noRules, redisTemplate, new ObjectMapper());
            MockHttpServletRequest request = authenticatedRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        private MockHttpServletRequest authenticatedRequest() {
            MockHttpServletRequest request = request();
            request.setAttribute("authenticatedUserId", "alice");
            return request;
        }
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/uoms");
        request.setServletPath("/v1/uoms");
        return request;
    }
}

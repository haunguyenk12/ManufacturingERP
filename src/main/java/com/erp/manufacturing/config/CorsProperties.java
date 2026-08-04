package com.erp.manufacturing.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * CORS configuration bound from {@code app.cors.*} (best practice {@code S5}).
 *
 * <p>Before this existed the application had <em>no</em> CORS configuration at all: Spring Boot's
 * default {@code HttpSecurity} puts a {@code CorsFilter} in the chain, but with no
 * {@code CorsConfigurationSource} bean it allows nothing, so every cross-origin call from the
 * frontend was rejected by the browser regardless of whether the API itself was correct.
 *
 * <p>{@code allowedOrigins} is an exact-match list on purpose. {@code "*"} is refused at startup
 * ({@link #validate()}) rather than merely discouraged, because this application sends the
 * {@code Authorization} header on every request: pairing a wildcard origin with credentialed
 * requests is exactly the combination the CORS spec forbids, and Spring would fail later with a
 * message that points at the filter instead of at this file.
 */
@ConfigurationProperties(prefix = "app.cors")
@Validated
public record CorsProperties(
        @NotEmpty List<String> allowedOrigins,
        @NotEmpty List<String> allowedMethods,
        @NotEmpty List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        long maxAgeSeconds
) {

    public CorsProperties {
        exposedHeaders = exposedHeaders == null ? List.of() : exposedHeaders;
    }

    /**
     * Fails fast on the one misconfiguration that looks harmless in a diff and cannot work at
     * runtime: a wildcard origin on a credentialed API.
     */
    public void validate() {
        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins must list explicit origins, not \"*\" — this API is "
                            + "called with the Authorization header, and the CORS spec forbids a "
                            + "wildcard origin on credentialed requests (best practice S5)");
        }
    }
}

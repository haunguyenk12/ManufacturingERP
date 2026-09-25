package com.erp.manufacturing.config;

import com.erp.manufacturing.common.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration.
 *
 * <h3>Filter chain order</h3>
 * <pre>
 *   1. TraceIdFilter          – set traceId + clientIp in MDC and request attributes
 *   2. RateLimitFilter        – IP blacklist, IP whitelist, IP-scope rate limiting
 *   3. JwtAuthenticationFilter – validate JWT, set SecurityContext + authenticatedUserId
 *   4. UserRateLimitFilter    – USER-scope rate limiting (needs authenticatedUserId)
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
// Binds CorsProperties for anyone importing this class. The application's
// @ConfigurationPropertiesScan covers the full context, but a @WebMvcTest slice that imports only
// SecurityConfig does not run it — without this, every such slice fails to start on a missing bean.
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter         rateLimitFilter;
    private final UserRateLimitFilter     userRateLimitFilter;
    private final TraceIdFilter           traceIdFilter;
    private final JwtAuthEntryPoint       authEntryPoint;
    private final JwtAccessDeniedHandler  accessDeniedHandler;
    private final UserDetailsService      userDetailsService;
    private final CorsProperties          corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // More specific matcher must come first — authorizeHttpRequests evaluates
                        // in declaration order and the first match wins. /me is the one auth
                        // endpoint that is NOT permit-all: it needs a valid, unexpired token.
                        .requestMatchers("/auth/v1/login", "/auth/v1/refresh",
                                "/auth/v1/forgot-password", "/auth/v1/reset-password").permitAll()
                        .requestMatchers("/auth/v1/me", "/auth/v1/logout",
                                "/auth/v1/logout-all").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Container-level error dispatches land on /error (EH-1). Spring Boot
                        // registers the security chain for the ERROR dispatcher type as well, so
                        // without this entry an unauthenticated request whose real failure was a 500
                        // would be re-answered as 401 and the actual status lost. ApiErrorController
                        // echoes nothing about the failure, so exposing it costs nothing.
                        .requestMatchers("/error").permitAll()
                        // Springdoc is disabled by default and forced off in prod. When explicitly
                        // enabled in dev/acceptance, its bootstrap HTML and JSON must be reachable
                        // before Swagger UI has any opportunity to attach a Bearer token.
                        .requestMatchers("/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // Filter order: TraceId (1) → IP RateLimit (2) → JWT (3) → User RateLimit (4)
                .addFilterBefore(traceIdFilter,           UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter,         UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(userRateLimitFilter,      jwtAuthenticationFilter.getClass())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .build();
    }

    /**
     * Applies {@link CorsProperties} to every path. Registered explicitly because Spring Boot's
     * default chain already contains a {@code CorsFilter} — without this bean that filter allows
     * nothing, which is why the frontend could not call the API cross-origin at all before C2-5.
     *
     * <p>Preflight requests must stay reachable without a token: the browser sends {@code OPTIONS}
     * with no {@code Authorization} header, so it cannot pass {@code .anyRequest().authenticated()}.
     * Spring Security's CORS support runs the preflight short-circuit ahead of authorization, which
     * is why no {@code permitAll} entry for {@code OPTIONS} is needed here — and why this must be
     * wired through {@code http.cors(...)} rather than as a standalone {@code WebMvcConfigurer}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        corsProperties.validate();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(corsProperties.allowedMethods());
        config.setAllowedHeaders(corsProperties.allowedHeaders());
        config.setExposedHeaders(corsProperties.exposedHeaders());
        config.setAllowCredentials(corsProperties.allowCredentials());
        config.setMaxAge(corsProperties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}

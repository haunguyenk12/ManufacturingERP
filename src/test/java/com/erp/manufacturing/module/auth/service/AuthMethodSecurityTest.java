package com.erp.manufacturing.module.auth.service;

import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.JwtProperties;
import com.erp.manufacturing.module.organization.service.AccessControlService;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.repository.UserRepository;
import com.erp.manufacturing.module.user.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@code adminUnlockAccount} is the only {@code @PreAuthorize}-gated method on {@link AuthService} (D8c). */
@SpringJUnitConfig(AuthMethodSecurityTest.Config.class)
@DisplayName("AuthService method security")
class AuthMethodSecurityTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired TokenStoreService tokenStore;

    @BeforeEach
    void setUp() {
        reset(userRepository, tokenStore);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminUnlockAccount_deniedWithoutAdminRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> authService.adminUnlockAccount(userId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userRepository, tokenStore);
    }

    @Test
    void adminUnlockAccount_allowedWithAdminRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        User user = User.builder()
                .userId(UUID.randomUUID())
                .username("locked-user")
                .email("locked@erp.local")
                .password("$2a$12$encoded")
                .status(UserStatus.LOCKED)
                .build();
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));

        assertThatCode(() -> authService.adminUnlockAccount(user.getUserId())).doesNotThrowAnyException();

        verify(userRepository).save(user);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AuthService authService(UserDetailsServiceImpl userDetailsService,
                                 JwtTokenProvider jwtTokenProvider,
                                 TokenStoreService tokenStore,
                                 PasswordEncoder passwordEncoder,
                                 AuditLogService auditLogService,
                                 JwtProperties jwtProperties,
                                 AccessControlService accessControlService,
                                 UserRepository userRepository,
                                 PasswordResetTokenService passwordResetTokenService,
                                 EmailNotificationService emailNotificationService) {
            return new AuthService(userDetailsService, jwtTokenProvider, tokenStore, passwordEncoder,
                    auditLogService, jwtProperties, accessControlService, userRepository,
                    passwordResetTokenService, emailNotificationService);
        }

        @Bean UserDetailsServiceImpl userDetailsService() { return mock(UserDetailsServiceImpl.class); }

        @Bean JwtTokenProvider jwtTokenProvider() { return mock(JwtTokenProvider.class); }

        @Bean TokenStoreService tokenStore() { return mock(TokenStoreService.class); }

        @Bean PasswordEncoder passwordEncoder() { return mock(PasswordEncoder.class); }

        @Bean AuditLogService auditLogService() { return mock(AuditLogService.class); }

        /** Plain value record — a real instance is used rather than a mock (rule R3). */
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties("test-secret-key-that-is-at-least-256-bits-long!!",
                    900_000L, 604_800_000L, 2_592_000_000L);
        }

        @Bean AccessControlService accessControlService() { return mock(AccessControlService.class); }

        @Bean UserRepository userRepository() { return mock(UserRepository.class); }

        @Bean PasswordResetTokenService passwordResetTokenService() { return mock(PasswordResetTokenService.class); }

        @Bean EmailNotificationService emailNotificationService() { return mock(EmailNotificationService.class); }
    }
}

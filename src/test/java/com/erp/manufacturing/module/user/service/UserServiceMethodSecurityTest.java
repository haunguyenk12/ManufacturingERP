package com.erp.manufacturing.module.user.service;

import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.module.organization.repository.RoleRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.dto.UpdateUserRequest;
import com.erp.manufacturing.module.user.dto.UserResponse;
import com.erp.manufacturing.module.user.mapper.UserMapper;
import com.erp.manufacturing.module.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Rule R2 for {@link UserService}: deny + verify of the exact permission string + allow.
 *
 * <p>The self-access cases use a real {@link UserPrincipal}: the guard compares the path id with
 * {@code authentication.principal.userId}, and the earlier form of that expression compared a
 * {@code UUID} with a {@code String}, which SpEL evaluates to {@code false} for every caller.
 */
@SpringJUnitConfig(UserServiceMethodSecurityTest.Config.class)
@DisplayName("UserService method security")
class UserServiceMethodSecurityTest {

    private static final String ACCESS_MANAGE = "PERM_ACCESS_MANAGE";

    @Autowired UserService userService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired UserRepository userRepository;
    @Autowired UserMapper userMapper;

    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, userRepository, userMapper);
        UserPrincipal caller = new UserPrincipal(user(callerId));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(caller, null, caller.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static User user(UUID id) {
        return User.builder().userId(id).username("u-" + id).email(id + "@erp.local")
                .password("$2a$12$encoded").status(UserStatus.ACTIVE).build();
    }

    @Test
    void findAll_deniedWithoutAccessManage() {
        assertThatThrownBy(() -> userService.findAll(PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasPermission(any(), eq(ACCESS_MANAGE));
        verifyNoInteractions(userRepository);
    }

    @Test
    void findAll_allowedWithAccessManage() {
        when(permissionGuard.hasPermission(any(), eq(ACCESS_MANAGE))).thenReturn(true);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        assertThatCode(() -> userService.findAll(PageRequest.of(0, 20))).doesNotThrowAnyException();
    }

    @Test
    void findById_allowsTheCallerToReadTheirOwnAccount() {
        when(userRepository.findById(callerId)).thenReturn(Optional.of(user(callerId)));
        when(userMapper.toResponse(any())).thenReturn(new UserResponse(
                callerId, "u", "u@erp.local", "ACTIVE", Set.of(), Instant.now(), Instant.now()));

        assertThatCode(() -> userService.findById(callerId)).doesNotThrowAnyException();

        verify(userRepository).findById(callerId);
    }

    @Test
    void findById_deniesAnotherUsersAccountWithoutAccessManage() {
        UUID otherId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.findById(otherId))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasPermission(any(), eq(ACCESS_MANAGE));
        verifyNoInteractions(userRepository);
    }

    @Test
    void update_deniesTheCallerEditingTheirOwnAccountWithoutAccessManage() {
        assertThatThrownBy(() -> userService.update(callerId, new UpdateUserRequest(null, "N3w!Password")))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasPermission(any(), eq(ACCESS_MANAGE));
        verifyNoInteractions(userRepository);
    }

    @Test
    void assignRole_deniedWithoutAccessManage() {
        assertThatThrownBy(() -> userService.assignRole(UUID.randomUUID(), "MANAGER"))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasPermission(any(), eq(ACCESS_MANAGE));
        verifyNoInteractions(userRepository);
    }

    @Test
    void delete_deniedWithoutAccessManage() {
        assertThatThrownBy(() -> userService.delete(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasPermission(any(), eq(ACCESS_MANAGE));
        verifyNoInteractions(userRepository);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        UserService userService(UserRepository userRepository, UserMapper userMapper) {
            return new UserService(userRepository, mock(RoleRepository.class), userMapper,
                    mock(PasswordEncoder.class), mock(TokenStoreService.class));
        }

        @Bean
        UserMapper userMapper() {
            return mock(UserMapper.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }
    }
}

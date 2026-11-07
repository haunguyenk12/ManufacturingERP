package com.erp.manufacturing.module.user.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.repository.RoleRepository;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.dto.CreateAccountCommand;
import com.erp.manufacturing.module.user.dto.UserResponse;
import com.erp.manufacturing.module.user.mapper.UserMapper;
import com.erp.manufacturing.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code UserService.createPendingSetup}: the admin command that creates an account which cannot
 * enter the ERP until its owner replaces the initial password. {@code POST /users/v1} behaviour is
 * pinned alongside because the seeds and demo scripts depend on it staying {@code ACTIVE}.
 */
@DisplayName("UserService.createPendingSetup")
class UserServiceCreatePendingSetupTest {

    private static final String INITIAL = "Init1al!Pass";

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserMapper mapper = mock(UserMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, roles, mapper, encoder, mock(TokenStoreService.class));
        when(roles.findByName("OPERATOR")).thenReturn(Optional.of(Role.builder().name("OPERATOR").code("OPERATOR").build()));
        when(encoder.encode(any())).thenAnswer(inv -> "{hash}" + inv.getArgument(0));
        when(encoder.matches(any(), any())).thenAnswer(inv -> ("{hash}" + inv.getArgument(0)).equals(inv.getArgument(1)));
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(UUID.randomUUID());
            return u;
        });
        when(mapper.toResponse(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new UserResponse(u.getUserId(), u.getUsername(), u.getEmail(), u.getStatus().name(), Set.of(),
                    Instant.now(), Instant.now());
        });
    }

    @Test
    @DisplayName("creates a PENDING_SETUP account with the hashed initial password and a normalised email")
    void create_savesPendingAccount() {
        UserResponse response = service.createPendingSetup(
                new CreateAccountCommand("CREATE", " new.hire ", " New.Hire@OmniPlant.vn ", INITIAL));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.PENDING_SETUP);
        assertThat(saved.getValue().getUsername()).isEqualTo("new.hire");
        assertThat(saved.getValue().getEmail()).isEqualTo("new.hire@omniplant.vn");
        assertThat(saved.getValue().getPassword()).isEqualTo("{hash}" + INITIAL).isNotEqualTo(INITIAL);
        assertThat(response.status()).isEqualTo("PENDING_SETUP");
    }

    @Test
    @DisplayName("a weak initial password is refused with PASSWORD_POLICY before anything is saved")
    void create_weakPassword_isRefused() {
        assertThat(codeOf(new CreateAccountCommand("CREATE", "a.b", "a.b@omniplant.vn", "weakpass")))
                .isEqualTo(AuthErrorCode.PASSWORD_POLICY);

        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("an unsupported command kind is a validation error naming the field")
    void create_unknownKind_isRefused() {
        assertThat(codeOf(new CreateAccountCommand("DELETE", "a.b", "a.b@omniplant.vn", INITIAL)))
                .isEqualTo(ValidationErrorCode.INVALID_INPUT);

        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("a taken email is 409 EMAIL_ALREADY_EXISTS")
    void create_duplicateEmail_isConflict() {
        when(users.existsByEmail("a.b@omniplant.vn")).thenReturn(true);

        assertThat(codeOf(new CreateAccountCommand("CREATE", "a.b", "a.b@omniplant.vn", INITIAL)))
                .isEqualTo(ValidationErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("repeating the identical command while still PENDING_SETUP returns the same account instead of 409")
    void create_identicalReplay_returnsExistingAccount() {
        User existing = User.builder().userId(UUID.randomUUID()).username("new.hire").email("new.hire@omniplant.vn")
                .password("{hash}" + INITIAL).status(UserStatus.PENDING_SETUP).build();
        when(users.findByUsername("new.hire")).thenReturn(Optional.of(existing));

        UserResponse response = service.createPendingSetup(
                new CreateAccountCommand("CREATE", "new.hire", "new.hire@omniplant.vn", INITIAL));

        assertThat(response.userId()).isEqualTo(existing.getUserId());
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("replay protection does not apply once set up or when the initial password differs: still 409")
    void create_notAReplay_isStillConflict() {
        User active = User.builder().userId(UUID.randomUUID()).username("new.hire").email("new.hire@omniplant.vn")
                .password("{hash}" + INITIAL).status(UserStatus.ACTIVE).build();
        when(users.findByUsername("new.hire")).thenReturn(Optional.of(active));
        when(users.existsByUsername("new.hire")).thenReturn(true);

        assertThat(codeOf(new CreateAccountCommand("CREATE", "new.hire", "new.hire@omniplant.vn", INITIAL)))
                .isEqualTo(ValidationErrorCode.USERNAME_ALREADY_EXISTS);

        User pending = User.builder().userId(UUID.randomUUID()).username("other").email("other@omniplant.vn")
                .password("{hash}Different1!Pass").status(UserStatus.PENDING_SETUP).build();
        when(users.findByUsername("other")).thenReturn(Optional.of(pending));
        when(users.existsByUsername("other")).thenReturn(true);

        assertThat(codeOf(new CreateAccountCommand("CREATE", "other", "other@omniplant.vn", INITIAL)))
                .isEqualTo(ValidationErrorCode.USERNAME_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("POST /users/v1 (create) still produces an ACTIVE account")
    void legacyCreate_staysActive() {
        service.create(new com.erp.manufacturing.module.user.dto.CreateUserRequest("seed.user", "seed@omniplant.vn", "Passw0rd!x"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    private com.erp.manufacturing.common.exception.ErrorCode codeOf(CreateAccountCommand command) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(() -> service.createPendingSetup(command), AppException.class)
                .getErrorCode();
    }
}

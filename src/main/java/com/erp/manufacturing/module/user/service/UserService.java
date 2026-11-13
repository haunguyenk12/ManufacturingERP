package com.erp.manufacturing.module.user.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.PasswordPolicy;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.dto.CreateAccountCommand;
import com.erp.manufacturing.module.user.dto.CreateUserRequest;
import com.erp.manufacturing.module.user.dto.UpdateUserRequest;
import com.erp.manufacturing.module.user.dto.UserResponse;
import com.erp.manufacturing.module.user.mapper.UserMapper;
import com.erp.manufacturing.module.organization.repository.RoleRepository;
import com.erp.manufacturing.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * User management service.
 * All write operations are auto-audited via {@link Auditable} + AOP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository  userRepository;
    private final RoleRepository  roleRepository;
    private final UserMapper      userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStoreService tokenStore;

    // ── Read ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    public PageResult<UserResponse> findAll(Pageable pageable) {
        return PageResult.from(userRepository.findAll(pageable).map(userMapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE') "
            + "or #userId == authentication.principal.userId")
    public UserResponse findById(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "User", userId));
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.USER_CREATED, entityType = "User",
               entityIdExpression = "userId.toString()")
    public UserResponse create(CreateUserRequest request) {
        return persistNew(request.username(), request.email(), request.password(), UserStatus.ACTIVE);
    }

    /**
     * Admin-created account that cannot enter the ERP until its owner replaces the initial password
     * through the emailed-code flow (see {@code PasswordFlowService}). {@code POST /users/v1} is
     * untouched and still creates {@code ACTIVE} accounts — seeds and demo scripts depend on that.
     *
     * <p>Replay-safe without an {@code Idempotency-Key} table: repeating the exact command (same
     * username, email and initial password) while the account is still {@code PENDING_SETUP}
     * returns that account instead of {@code 409}; any other duplicate is still refused.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.USER_CREATED, entityType = "User",
               entityIdExpression = "userId.toString()")
    public UserResponse createPendingSetup(CreateAccountCommand command) {
        if (!CreateAccountCommand.CREATE.equals(command.kind())) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("kind", "unsupported account command, expected " + CreateAccountCommand.CREATE));
        }
        String username = command.username().trim();
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        PasswordPolicy.require(command.password());

        Optional<User> sameCommand = userRepository.findByUsername(username)
                .filter(u -> u.getStatus() == UserStatus.PENDING_SETUP
                        && u.getEmail().equalsIgnoreCase(email)
                        && passwordEncoder.matches(command.password(), u.getPassword()));
        if (sameCommand.isPresent()) {
            return userMapper.toResponse(sameCommand.get());
        }
        return persistNew(username, email, command.password(), UserStatus.PENDING_SETUP);
    }

    private UserResponse persistNew(String username, String email, String password, UserStatus status) {
        if (userRepository.existsByUsername(username)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.USERNAME_ALREADY_EXISTS,
                    "Username already taken: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.EMAIL_ALREADY_EXISTS,
                    "Email already in use: " + email);
        }

        Role defaultRole = roleRepository.findByName("OPERATOR")
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Role", "OPERATOR"));

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .status(status)
                .build();
        user.getRoles().add(defaultRole);

        User saved = userRepository.save(user);
        log.info("[USER] Created user={} id={} status={}", saved.getUsername(), saved.getUserId(), status);
        return userMapper.toResponse(saved);
    }

    // ── Update ────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.USER_UPDATED, entityType = "User",
               entityIdExpression = "userId.toString()")
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "User", userId));

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw ExceptionFactory.alreadyExists(ValidationErrorCode.EMAIL_ALREADY_EXISTS,
                        "Email already in use: " + request.email());
            }
            user.setEmail(request.email());
        }
        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
            revokeSessions(user);
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    // entityId (not entityIdExpression): the method returns void, so "toString()" was evaluated
    // against nothing and the deleted user was never named in its own deletion record.
    @Auditable(action = AuditAction.USER_DELETED, entityType = "User",
               entityId = "#userId",
               operation = com.erp.manufacturing.common.audit.model.AuditOperation.DELETE)
    public void delete(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "User", userId));
        user.setStatus(UserStatus.INACTIVE);
        revokeSessions(user);
        userRepository.save(user);
        log.info("[USER] Deactivated user={}", userId);
    }

    // ── Role management ───────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.ROLE_ASSIGNED, entityType = "User",
               entityIdExpression = "userId.toString()")
    public UserResponse assignRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "User", userId));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Role", roleName));
        user.getRoles().add(role);
        revokeSessions(user);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ACCESS_MANAGE')")
    @Auditable(action = AuditAction.ROLE_REVOKED, entityType = "User",
               entityIdExpression = "userId.toString()")
    public UserResponse revokeRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "User", userId));
        user.getRoles().removeIf(r -> r.getName().equals(roleName));
        revokeSessions(user);
        return userMapper.toResponse(userRepository.save(user));
    }

    private void revokeSessions(User user) {
        user.revokeAllSessions();
        tokenStore.deleteAllUserTokens(user.getUserId());
        tokenStore.deleteAllDeviceSessions(user.getUserId());
    }
}

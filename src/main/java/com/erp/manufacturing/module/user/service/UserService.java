package com.erp.manufacturing.module.user.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.ResourceAlreadyExistsException;
import com.erp.manufacturing.common.exception.ResourceNotFoundException;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.user.domain.Role;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.dto.CreateUserRequest;
import com.erp.manufacturing.module.user.dto.UpdateUserRequest;
import com.erp.manufacturing.module.user.dto.UserResponse;
import com.erp.manufacturing.module.user.mapper.UserMapper;
import com.erp.manufacturing.module.user.repository.RoleRepository;
import com.erp.manufacturing.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // ── Read ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<UserResponse> findAll(Pageable pageable) {
        return PageResult.from(userRepository.findAll(pageable).map(userMapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId.toString()")
    public UserResponse findById(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.USER_CREATED, entityType = "User",
               entityIdExpression = "userId.toString()")
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResourceAlreadyExistsException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceAlreadyExistsException("Email already in use: " + request.email());
        }

        Role defaultRole = roleRepository.findByName("OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "OPERATOR"));

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .build();
        user.getRoles().add(defaultRole);

        User saved = userRepository.save(user);
        log.info("[USER] Created user={} id={}", saved.getUsername(), saved.getUserId());
        return userMapper.toResponse(saved);
    }

    // ── Update ────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId.toString()")
    @Auditable(action = AuditAction.USER_UPDATED, entityType = "User",
               entityIdExpression = "userId.toString()")
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new ResourceAlreadyExistsException("Email already in use: " + request.email());
            }
            user.setEmail(request.email());
        }
        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.USER_DELETED, entityType = "User",
               entityIdExpression = "toString()")
    public void delete(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("[USER] Deactivated user={}", userId);
    }

    // ── Role management ───────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.ROLE_ASSIGNED, entityType = "User",
               entityIdExpression = "userId.toString()")
    public UserResponse assignRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleName));
        user.getRoles().add(role);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.ROLE_REVOKED, entityType = "User",
               entityIdExpression = "userId.toString()")
    public UserResponse revokeRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.getRoles().removeIf(r -> r.getName().equals(roleName));
        return userMapper.toResponse(userRepository.save(user));
    }
}

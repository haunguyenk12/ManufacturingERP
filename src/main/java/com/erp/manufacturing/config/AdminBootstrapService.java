package com.erp.manufacturing.config;

import com.erp.manufacturing.module.organization.repository.RoleRepository;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, one-time replacement for the legacy public administrator credential. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class AdminBootstrapService implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validate();
        User admin = users.findByUsername(properties.username()).orElseGet(() -> User.builder()
                .username(properties.username())
                .email(properties.email())
                .status(UserStatus.INACTIVE)
                .build());
        if (admin.isActive()) {
            throw new IllegalStateException("Admin bootstrap is still enabled after provisioning; disable it");
        }
        var adminRole = roles.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing"));
        admin.setEmail(properties.email());
        admin.setPassword(passwordEncoder.encode(properties.password()));
        admin.activate();
        admin.revokeAllSessions();
        admin.getRoles().add(adminRole);
        users.save(admin);
        log.warn("Administrator '{}' provisioned. Disable BOOTSTRAP_ADMIN_ENABLED before restart.",
                properties.username());
    }

    private void validate() {
        if (properties.username() == null || properties.username().isBlank()
                || properties.email() == null || properties.email().isBlank()
                || properties.password() == null || properties.password().length() < 15
                || "Admin@123".equals(properties.password())) {
            throw new IllegalStateException("Admin bootstrap requires username, email and a unique 15+ character password");
        }
    }
}

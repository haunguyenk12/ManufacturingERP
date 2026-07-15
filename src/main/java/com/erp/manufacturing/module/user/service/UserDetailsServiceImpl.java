package com.erp.manufacturing.module.user.service;

import com.erp.manufacturing.module.organization.domain.AssignmentStatus;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.repository.UserRoleAssignmentRepository;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.erp.manufacturing.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Security UserDetailsService – loads user with roles in a single query.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameWithRoles(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Instant now = Instant.now();
        Set<String> dynamicRoles = assignmentRepository.findActiveRoleCodesForUser(
                user.getUserId(),
                now,
                AssignmentStatus.ACTIVE,
                RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE);
        Set<String> permissions = assignmentRepository.findActivePermissionCodesForUser(
                user.getUserId(),
                now,
                AssignmentStatus.ACTIVE,
                RoleStatus.ACTIVE,
                OrganizationStatus.ACTIVE,
                OrganizationStatus.ACTIVE);

        return new UserPrincipal(user, dynamicRoles, permissions);
    }
}

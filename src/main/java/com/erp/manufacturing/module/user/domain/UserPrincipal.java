package com.erp.manufacturing.module.user.domain;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetails wrapper for {@link User}.
 * Exposes userId UUID for use in AuditorAware and RequestContext.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID   userId;
    private final String username;
    private final String password;
    private final boolean active;
    private final long authVersion;
    private final boolean globalAdmin;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this(user, Set.of(), Set.of());
    }

    public UserPrincipal(User user, Collection<String> dynamicRoleCodes, Collection<String> permissionCodes) {
        this(user, dynamicRoleCodes, permissionCodes, false);
    }

    public UserPrincipal(User user,
                         Collection<String> dynamicRoleCodes,
                         Collection<String> permissionCodes,
                         boolean verifiedDynamicGlobalAdmin) {
        this.userId    = user.getUserId();
        this.username  = user.getUsername();
        this.password  = user.getPassword();
        this.active    = user.isActive();
        this.authVersion = user.getAuthVersion();
        this.globalAdmin = verifiedDynamicGlobalAdmin || safe(user.getRoles()).stream()
                .anyMatch(UserPrincipal::isAuthoritativeGlobalAdminRole);

        Set<String> authorityNames = new HashSet<>();
        safe(user.getRoles()).stream()
                .filter(role -> !isReservedAdminCode(role.getCode()))
                .map(com.erp.manufacturing.module.organization.domain.Role::getCode)
                .map(UserPrincipal::toRoleAuthority)
                .filter(name -> name != null)
                .forEach(authorityNames::add);
        safe(dynamicRoleCodes).stream()
                .filter(code -> !isReservedAdminCode(code))
                .map(UserPrincipal::toRoleAuthority)
                .filter(name -> name != null)
                .forEach(authorityNames::add);
        safe(permissionCodes).stream()
                .map(UserPrincipal::toPermissionAuthority)
                .filter(name -> name != null)
                .forEach(authorityNames::add);
        if (globalAdmin) {
            authorityNames.add("ROLE_ADMIN");
        }

        this.authorities = authorityNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    private static String toRoleAuthority(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private static String toPermissionAuthority(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return null;
        }
        String normalized = permissionCode.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("PERM_") ? normalized : "PERM_" + normalized;
    }

    private static boolean isAuthoritativeGlobalAdminRole(
            com.erp.manufacturing.module.organization.domain.Role role) {
        return role != null
                && role.isSystem()
                && role.isActive()
                && role.getCompanyId() == null
                && isReservedAdminCode(role.getCode());
    }

    private static boolean isReservedAdminCode(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("ADMIN") || normalized.equals("ROLE_ADMIN");
    }

    private static <T> Collection<T> safe(Collection<T> values) {
        return values == null ? Collections.emptySet() : values;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()                                     { return password; }
    @Override public String getUsername()                                     { return username; }
    @Override public boolean isAccountNonExpired()                            { return true; }
    @Override public boolean isAccountNonLocked()                             { return active; }
    @Override public boolean isCredentialsNonExpired()                        { return true; }
    @Override public boolean isEnabled()                                      { return active; }
}

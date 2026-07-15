package com.erp.manufacturing.module.user.domain;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
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
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this(user, Set.of(), Set.of());
    }

    public UserPrincipal(User user, Collection<String> dynamicRoleCodes, Collection<String> permissionCodes) {
        this.userId    = user.getUserId();
        this.username  = user.getUsername();
        this.password  = user.getPassword();
        this.active    = user.isActive();

        Set<String> authorityNames = new HashSet<>();
        user.getRoles().stream()
                .map(role -> role.getCode() != null ? role.getCode() : role.getName())
                .map(UserPrincipal::toRoleAuthority)
                .forEach(authorityNames::add);
        dynamicRoleCodes.stream()
                .map(UserPrincipal::toRoleAuthority)
                .forEach(authorityNames::add);
        permissionCodes.stream()
                .map(UserPrincipal::toPermissionAuthority)
                .forEach(authorityNames::add);

        this.authorities = authorityNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    private static String toRoleAuthority(String roleCode) {
        String normalized = roleCode.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private static String toPermissionAuthority(String permissionCode) {
        String normalized = permissionCode.trim().toUpperCase();
        return normalized.startsWith("PERM_") ? normalized : "PERM_" + normalized;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()                                     { return password; }
    @Override public String getUsername()                                     { return username; }
    @Override public boolean isAccountNonExpired()                            { return true; }
    @Override public boolean isAccountNonLocked()                             { return active; }
    @Override public boolean isCredentialsNonExpired()                        { return true; }
    @Override public boolean isEnabled()                                      { return active; }
}

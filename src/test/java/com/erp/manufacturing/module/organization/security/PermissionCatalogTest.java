package com.erp.manufacturing.module.organization.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against code ↔ Flyway drift: every permission referenced from a {@code @PreAuthorize}
 * expression must be seeded by a migration. Without this, adding a {@code @PreAuthorize} with a
 * typo (or an un-seeded permission) silently disables the feature on production while the build
 * stays green.
 */
@DisplayName("Permission catalog: @PreAuthorize codes must all be seeded")
class PermissionCatalogTest {

    private static final String BASE_PACKAGE = "com.erp.manufacturing";
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("PERM_[A-Z_]+");

    @Test
    void everyPreAuthorizePermissionIsSeededInFlyway() throws IOException {
        Set<String> codePermissions = scanPreAuthorizePermissions();
        Set<String> seededPermissions = scanSeededPermissions();

        // Guard: if either scan silently returns nothing, isSubsetOf on an empty set would
        // pass vacuously and the test would disable itself.
        assertThat(codePermissions)
                .as("No PERM_* found in any @PreAuthorize — the code scan is broken")
                .isNotEmpty();
        assertThat(seededPermissions)
                .as("No PERM_* found in db/migration seeds — the migration scan is broken")
                .isNotEmpty();

        Set<String> missing = new TreeSet<>(codePermissions);
        missing.removeAll(seededPermissions);
        assertThat(missing)
                .as("These permissions are used in @PreAuthorize but never seeded by a Flyway "
                        + "migration (feature would be dead on production): %s", missing)
                .isEmpty();
    }

    /** Reads permission codes ONLY from {@code @PreAuthorize} — never from raw source, to avoid
     *  catching the {@code "PERM_"} prefix constants in PermissionGuard / UserPrincipal. */
    private Set<String> scanPreAuthorizePermissions() {
        Set<String> permissions = new TreeSet<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        scanner.findCandidateComponents(BASE_PACKAGE).forEach(candidate -> {
            Class<?> beanClass;
            try {
                beanClass = Class.forName(candidate.getBeanClassName());
            } catch (ClassNotFoundException e) {
                return;
            }
            for (Method method : beanClass.getDeclaredMethods()) {
                PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
                if (annotation != null) {
                    addMatches(annotation.value(), permissions);
                }
            }
        });
        return permissions;
    }

    /** Reads permission codes from the {@code INSERT INTO permissions} blocks in Flyway seeds. */
    private Set<String> scanSeededPermissions() throws IOException {
        Set<String> permissions = new TreeSet<>();
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/*.sql");
        for (Resource resource : resources) {
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            addMatches(extractPermissionInserts(sql), permissions);
        }
        return permissions;
    }

    /** Returns only the text of {@code INSERT INTO permissions ... ;} statements. */
    private String extractPermissionInserts(String sql) {
        StringBuilder inserts = new StringBuilder();
        String lower = sql.toLowerCase();
        int from = 0;
        while (true) {
            int start = lower.indexOf("insert into permissions", from);
            if (start < 0) {
                break;
            }
            int end = sql.indexOf(';', start);
            if (end < 0) {
                end = sql.length();
            }
            inserts.append(sql, start, end).append('\n');
            from = end + 1;
        }
        return inserts.toString();
    }

    private void addMatches(String text, Set<String> target) {
        Matcher matcher = PERMISSION_PATTERN.matcher(text);
        while (matcher.find()) {
            target.add(matcher.group());
        }
    }
}

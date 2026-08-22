package com.erp.manufacturing.module.organization.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Structural information-security gate: HTTP adapters must not bypass application services. */
@DisplayName("Authorization architecture security")
class AuthorizationArchitectureSecurityTest {

    @Test
    void restControllersNeverDependDirectlyOnRepositories() throws Exception {
        List<String> violations = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(
                "classpath*:com/erp/manufacturing/**/*Controller.class");

        for (Resource resource : resources) {
            String className = toClassName(resource);
            if (className == null || className.contains("$")) {
                continue;
            }
            Class<?> type = Class.forName(className, false, getClass().getClassLoader());
            if (!type.isAnnotationPresent(RestController.class)) {
                continue;
            }
            for (Field field : type.getDeclaredFields()) {
                if (Repository.class.isAssignableFrom(field.getType())) {
                    violations.add(type.getName() + " -> " + field.getType().getName());
                }
            }
        }

        assertThat(violations)
                .as("Controllers must authorize through application services, never repositories")
                .isEmpty();
    }

    private String toClassName(Resource resource) throws Exception {
        String url = resource.getURL().toString().replace('\\', '/');
        String marker = "/com/erp/manufacturing/";
        int markerIndex = url.lastIndexOf(marker);
        if (markerIndex < 0 || !url.endsWith(".class")) {
            return null;
        }
        return url.substring(markerIndex + 1, url.length() - ".class".length())
                .replace('/', '.');
    }
}

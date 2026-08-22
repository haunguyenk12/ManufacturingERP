package com.erp.manufacturing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record AdminBootstrapProperties(boolean enabled, String username, String email, String password) {
    @Override
    public String toString() {
        return "AdminBootstrapProperties[enabled=" + enabled + ", username=" + username
                + ", email=" + email + ", password=***]";
    }
}

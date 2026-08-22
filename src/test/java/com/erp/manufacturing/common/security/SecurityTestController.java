package com.erp.manufacturing.common.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecurityTestController {

    @GetMapping("/v1/test/ping")
    String ping() {
        return "pong";
    }

    @GetMapping("/swagger-ui/index.html")
    String swaggerUi() {
        return "swagger";
    }

    @GetMapping("/v3/api-docs")
    String apiDocs() {
        return "openapi";
    }
}

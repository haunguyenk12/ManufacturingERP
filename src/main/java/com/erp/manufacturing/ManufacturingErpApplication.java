package com.erp.manufacturing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.erp.manufacturing.config")
public class ManufacturingErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManufacturingErpApplication.class, args);
    }
}

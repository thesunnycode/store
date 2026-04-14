package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the E-Commerce REST API application.
 *
 * @SpringBootApplication is a shortcut that combines:
 *   - @Configuration      → marks this as a config source
 *   - @EnableAutoConfiguration → tells Spring Boot to auto-configure beans
 *   - @ComponentScan      → scans this package and sub-packages for @Component, @Service, etc.
 */
@SpringBootApplication
public class EcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
    }
}

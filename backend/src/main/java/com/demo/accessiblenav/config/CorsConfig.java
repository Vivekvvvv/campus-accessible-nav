package com.demo.accessiblenav.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        boolean hasWildcard = Arrays.stream(origins).anyMatch(origin -> "*".equals(origin) || origin.contains("*"));

        CorsRegistration registration = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // Expose custom response headers to browser JS.
                .exposedHeaders(
                        "X-Trace-Id",
                        "X-New-Access-Token",
                        "X-Token-Expires-In",
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining",
                        "X-RateLimit-Reset"
                )
                .allowCredentials(true)
                .maxAge(3600);

        if (origins.length == 0 || hasWildcard) {
            registration.allowedOriginPatterns(origins.length == 0 ? new String[]{"*"} : origins);
        } else {
            registration.allowedOrigins(origins);
        }
    }
}

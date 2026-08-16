package com.adam_stegienko.stacc_api_gateway.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration {

    @Value("${app.cors.mapping:/gw/**}")
    private String corsMapping;

    @Value("${app.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${app.cors.allowed-methods:*}")
    private String corsAllowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String corsAllowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean corsAllowCredentials;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                String[] allowedOrigins = parseCsv(corsAllowedOrigins);
                if (allowedOrigins.length == 0) {
                    return;
                }

                registry.addMapping(corsMapping)
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods(parseCsv(corsAllowedMethods, "*"))
                        .allowedHeaders(parseCsv(corsAllowedHeaders, "*"))
                        .allowCredentials(corsAllowCredentials);
            }
        };
    }

    private String[] parseCsv(String value, String... defaults) {
        if (value == null || value.isBlank()) {
            return defaults;
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toArray(String[]::new);
    }
}

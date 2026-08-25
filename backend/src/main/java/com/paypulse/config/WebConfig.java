package com.paypulse.config;

import com.paypulse.filter.JwtAuthFilter;
import com.paypulse.filter.MaintenanceModeFilter;
import com.paypulse.filter.SecurityHeadersFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig {

    @Bean
    WebMvcConfigurer corsConfigurer(AppProperties properties) {
        List<String> origins = Arrays.stream(properties.getCorsOrigins().split(",")) // get all the requests
            .map(String::trim) // Remove spaces
            .filter(s -> !s.isEmpty())   // Remove Empty Objects
            .toList();                   // Collect into Lists

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] allowed = origins.contains("*") ? new String[]{"*"} : origins.toArray(String[]::new);
                registry.addMapping("/**")
                    .allowedOrigins(allowed)
                    .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()
                    )
                    .allowedHeaders("Content-Type", "Authorization", "Idempotency-Key", "x-payment-signature");
            }
        };
    }

    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration(SecurityHeadersFilter filter) {
        FilterRegistrationBean<SecurityHeadersFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(1);       // Runs first
        // Add Security Headers to every response
        return bean;
    }

    @Bean
    FilterRegistrationBean<MaintenanceModeFilter> maintenanceModeFilterRegistration(MaintenanceModeFilter filter) {
        FilterRegistrationBean<MaintenanceModeFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(2);     // Runs after Security Headers

        //Likely checks:
        //app.maintenance-mode=true
        //If enabled, it may return:503 Service Unavailable without reaching controllers.

        return bean;
    }

    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(3);       //Runs after maintenance check.
//        Likely:
//        Reads Authorization: Bearer xxx
//        Validates JWT , Extracts user ID/roles
//        Stores authentication in request/security context
        return bean;
    }

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}

package com.mobilebanking.gateway.config;

import com.mobilebanking.gateway.filter.AuthenticationFilter;
import com.mobilebanking.gateway.filter.LoggingFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Value("${services.auth-service.url}")
    private String authServiceUrl;

    @Value("${services.user-service.url}")
    private String userServiceUrl;

    private final AuthenticationFilter authenticationFilter;
    private final LoggingFilter loggingFilter;

    public RouteConfig(AuthenticationFilter authenticationFilter, LoggingFilter loggingFilter) {
        this.authenticationFilter = authenticationFilter;
        this.loggingFilter = loggingFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service-public", r -> r
                        .path("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/validate")
                        .filters(f -> f
                                .filter(loggingFilter.apply(new LoggingFilter.Config()))
                                .circuitBreaker(c -> c.setName("authCircuitBreaker").setFallbackUri("forward:/fallback/auth")))
                        .uri(authServiceUrl))
                .route("auth-service-protected", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f
                                .filter(loggingFilter.apply(new LoggingFilter.Config()))
                                .filter(authenticationFilter.apply(new AuthenticationFilter.Config()))
                                .circuitBreaker(c -> c.setName("authCircuitBreaker").setFallbackUri("forward:/fallback/auth")))
                        .uri(authServiceUrl))
                .route("user-service", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f
                                .filter(loggingFilter.apply(new LoggingFilter.Config()))
                                .filter(authenticationFilter.apply(new AuthenticationFilter.Config()))
                                .circuitBreaker(c -> c.setName("userCircuitBreaker").setFallbackUri("forward:/fallback/user")))
                        .uri(userServiceUrl))
                .route("auth-swagger", r -> r
                        .path("/auth/v3/api-docs/**", "/auth/swagger-ui/**")
                        .filters(f -> f.rewritePath("/auth(?<segment>/?.*)", "${segment}"))
                        .uri(authServiceUrl))
                .route("user-swagger", r -> r
                        .path("/user/v3/api-docs/**", "/user/swagger-ui/**")
                        .filters(f -> f.rewritePath("/user(?<segment>/?.*)", "${segment}"))
                        .uri(userServiceUrl))
                .build();
    }
}

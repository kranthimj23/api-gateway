package com.mobilebanking.gateway.filter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitingFilter extends AbstractGatewayFilterFactory<RateLimitingFilter.Config> {

    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final RateLimiterRegistry rateLimiterRegistry;

    public RateLimitingFilter() {
        super(Config.class);
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(100)
                .timeoutDuration(Duration.ofMillis(100))
                .build();
        this.rateLimiterRegistry = RateLimiterRegistry.of(config);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String key = getKey(request, config);

            RateLimiter rateLimiter = rateLimiters.computeIfAbsent(key, k -> 
                    rateLimiterRegistry.rateLimiter(k, RateLimiterConfig.custom()
                            .limitRefreshPeriod(Duration.ofMinutes(1))
                            .limitForPeriod(config.getRequestsPerMinute())
                            .timeoutDuration(Duration.ofMillis(100))
                            .build()));

            if (rateLimiter.acquirePermission()) {
                return chain.filter(exchange);
            } else {
                log.warn("Rate limit exceeded for key: {}", key);
                return onRateLimitExceeded(exchange);
            }
        };
    }

    private String getKey(ServerHttpRequest request, Config config) {
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }

        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return "ip:" + xForwardedFor.split(",")[0].trim();
        }

        return "ip:" + (request.getRemoteAddress() != null 
                ? request.getRemoteAddress().getAddress().getHostAddress() 
                : "anonymous");
    }

    private Mono<Void> onRateLimitExceeded(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        exchange.getResponse().getHeaders().add("Retry-After", "60");
        String body = String.format(
                "{\"success\":false,\"message\":\"Rate limit exceeded. Please try again later.\",\"timestamp\":\"%s\"}",
                java.time.Instant.now().toString());
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes()))
        );
    }

    @Getter
    @Setter
    public static class Config {
        private int requestsPerMinute = 100;
        private boolean enabled = true;
    }
}

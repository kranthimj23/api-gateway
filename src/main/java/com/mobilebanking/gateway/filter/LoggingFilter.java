package com.mobilebanking.gateway.filter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Slf4j
public class LoggingFilter extends AbstractGatewayFilterFactory<LoggingFilter.Config> {

    public LoggingFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestId = UUID.randomUUID().toString();
            long startTime = System.currentTimeMillis();

            String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
            String path = request.getPath().value();
            String clientIp = getClientIp(request);

            log.info("Request: {} {} from {} [requestId={}]", method, path, clientIp, requestId);

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-Request-Id", requestId)
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build())
                    .then(Mono.fromRunnable(() -> {
                        ServerHttpResponse response = exchange.getResponse();
                        long duration = System.currentTimeMillis() - startTime;
                        int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

                        log.info("Response: {} {} - {} in {}ms [requestId={}]",
                                method, path, statusCode, duration, requestId);
                    }));
        };
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddress() != null 
                ? request.getRemoteAddress().getAddress().getHostAddress() 
                : "unknown";
    }

    @Getter
    @Setter
    public static class Config {
        private boolean enabled = true;
    }
}

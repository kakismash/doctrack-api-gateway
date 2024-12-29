package com.kaki.doctrack.apigateway.filter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ContextPropagationFilter extends AbstractGatewayFilterFactory<ContextPropagationFilter.Config> {

    private static final Logger logger = LoggerFactory.getLogger(ContextPropagationFilter.class);

    public ContextPropagationFilter() {
        super(ContextPropagationFilter.Config.class);
    }

    private final String USER_ID = "X-User-Id";
    private final String USER_NAME = "X-User-Name";
    private final String USER_ROLE = "X-User-Role";

    @Override
    public GatewayFilter apply(Config config) {
        return this::filter;
    }

    private Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Extract headers safely
        String userId = exchange.getRequest().getHeaders().getFirst(USER_ID);
        String username = exchange.getRequest().getHeaders().getFirst(USER_NAME);
        String userRole = exchange.getRequest().getHeaders().getFirst(USER_ROLE);

        // Log the headers being passed for debugging
        logger.info("UserID: {}", userId);
        logger.info("Username: {}", username);
        logger.info("UserRole: {}", userRole);

        // Propagate context
        return chain.filter(exchange)
            .contextWrite(ctx -> {
                if (userId != null) ctx = ctx.put("X-User-Id", userId);
                if (username != null) ctx = ctx.put("X-User-Name", username);
                if (userRole != null) ctx = ctx.put("X-User-Role", userRole);
                return ctx;
            });
    }
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Config {
        private String authValidationUrl;
    }
}

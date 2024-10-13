package com.kaki.doctrack.apigateway.filter;

import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final WebClient.Builder webClientBuilder;

    public JwtAuthFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.error("Missing or invalid Authorization header");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = authHeader.substring(7);

            return webClientBuilder.build()
                    .get()
                    .uri(config.getAuthValidationUrl() + "?token=" + token)
                    .exchangeToMono(clientResponse -> {
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            logger.info("Token is valid");

                            return clientResponse.bodyToMono(UserInfo.class)
                                    .flatMap(userInfo -> {
                                        // Remove Authorization header
                                        // Build a new ServerHttpRequest with the updated headers
                                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                                .headers(httpHeaders -> httpHeaders.remove(HttpHeaders.AUTHORIZATION))
                                                .header("X-User-Id", userInfo.userId().toString())
                                                .header("X-User-Role", userInfo.role())
                                                .header("X-User-Name", userInfo.username())
                                                .build();

                                        // Update the ServerWebExchange with the mutated request
                                        ServerWebExchange mutatedExchange = exchange.mutate()
                                                .request(mutatedRequest)
                                                .build();

                                        return chain.filter(mutatedExchange);
                                    });
                        } else {
                            logger.error("Token validation failed with status: {}", clientResponse.statusCode());
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap("Unauthorized: Invalid or expired token".getBytes())));
                        }
                    })
                    .onErrorResume(e -> {
                        logger.error("Error during token validation: {}", e.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap("Unauthorized: Token validation error".getBytes())));
                    });

        };
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Config {
        private String authValidationUrl;
    }
}
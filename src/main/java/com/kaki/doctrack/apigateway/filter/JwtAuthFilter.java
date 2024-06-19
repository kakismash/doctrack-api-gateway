package com.kaki.doctrack.apigateway.filter;

import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Objects;

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
            String authHeader = Objects.requireNonNull(exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION)).get(0);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Authorization Header"));
            }

            String token = authHeader.substring(7);

            return webClientBuilder.build()
                    .get()
                    .uri(config.getAuthValidationUrl() + "?token=" + token)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .then(Mono.defer(() -> chain.filter(exchange)))
                    .doOnError(e -> {
                        logger.error("Error validating token: {}", e.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
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
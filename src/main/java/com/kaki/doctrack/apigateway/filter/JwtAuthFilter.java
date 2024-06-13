package com.kaki.doctrack.apigateway.filter;

import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);

    private WebClient.Builder webClientBuilder;

    public JwtAuthFilter() {
        super(Config.class);
        webClientBuilder = WebClient.builder();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization Header"));
            }

            String authHeader = Objects.requireNonNull(exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION)).get(0);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Authorization Header"));
            }

            String token = authHeader.substring(7);

            return webClientBuilder.build()
                    .get()
                    .uri(config.getAuthValidationUrl() + "?token=" + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> Mono.error(new ResponseStatusException(response.statusCode(), "Invalid Token")))
                    .bodyToMono(Void.class)
                    .then(chain.filter(exchange))
                    .doOnError(e -> {
                        // Log error here
                        System.out.println("Authorization error: " + e.getMessage());
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
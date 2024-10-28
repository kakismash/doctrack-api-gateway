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
public class InternalServiceAuthFilter extends AbstractGatewayFilterFactory<InternalServiceAuthFilter.Config> {

    private final Logger logger = LoggerFactory.getLogger(InternalServiceAuthFilter.class);
    private final WebClient.Builder webClientBuilder;

    public InternalServiceAuthFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Retrieve the X-Internal-Service header
            String internalServiceHeader = exchange.getRequest().getHeaders().getFirst("X-Internal-Service");
            String internalApiKeyHeader = exchange.getRequest().getHeaders().getFirst("X-Internal-Api-Key");
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // Check if the X-Internal-Service header is present
            if (internalServiceHeader == null) {
                logger.error("Missing X-Internal-Service header");
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap("Forbidden: Missing X-Internal-Service header".getBytes())));
            }

            if (internalApiKeyHeader == null) {
                logger.error("Missing X-Internal-Api-Key header");
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap("Forbidden: Missing X-Internal-Api-Key header".getBytes())));
            }

            // Check for the Authorization header
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.error("Missing or invalid Authorization header");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Validate and decode the JWT token
            return validateAndDecodeToken(config.getAuthValidationUrl(), internalApiKeyHeader, internalServiceHeader)
                    .flatMap(microserviceName -> {
                        if (!isValidInternalService(microserviceName, internalServiceHeader)) {
                            logger.error("Invalid internal service: {}", internalServiceHeader);
                            return setForbiddenResponse(exchange, "Forbidden: Invalid internal service");
                        }

                        // Forward the headers and proceed with the request
                        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .header("X-Internal-Api-Key", internalApiKeyHeader)
                                .header("X-Internal-Service", internalServiceHeader)
                                .build();

                        ServerWebExchange modifiedExchange = exchange.mutate().request(modifiedRequest).build();
                        return chain.filter(modifiedExchange);
                    })
                    .onErrorResume(e -> {
                        logger.error("Error during token validation: {}", e.getMessage());
                        return setUnauthorizedResponse(exchange, "Unauthorized: Token validation error");
                    });
        };
    }

    private Mono<Void> setForbiddenResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(message.getBytes())));
    }

    private Mono<Void> setUnauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(message.getBytes())));
    }

    private Mono<String> validateAndDecodeToken(String authValidationUrl, String internalApiKey, String internalServiceHeader) {
        return webClientBuilder.build()
                .get()
                .uri(authValidationUrl + "/" + internalServiceHeader)
                .header("X-Internal-Api-Key", internalApiKey)
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().is2xxSuccessful()) {
                        return clientResponse.bodyToMono(String.class); // Assuming the response body contains the microservice name
                    } else {
                        return Mono.error(new RuntimeException("Token validation failed"));
                    }
                });
    }

    private boolean isValidInternalService(String decodedServiceName, String internalServiceHeader) {
        // Compare decoded service name from token with the header
        return decodedServiceName.equals(internalServiceHeader);
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Config {
        private String authValidationUrl;
    }
}
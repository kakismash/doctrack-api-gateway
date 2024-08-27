package com.kaki.doctrack.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public class LoggingFilter extends AbstractGatewayFilterFactory<LoggingFilter.Config> {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    public LoggingFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Log the request details
            logger.info("Incoming request: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI());

            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> {
                        // Log the response details
                        logger.info("Outgoing response: {}", exchange.getResponse().getStatusCode());
                    }))
                    .doOnSuccess(aVoid -> {
                        logger.info("Successful response for: {}", exchange.getRequest().getURI());
                        if (Objects.requireNonNull(exchange.getResponse().getStatusCode()).is2xxSuccessful()) {
                            logger.info("Response status: {}", exchange.getResponse().getStatusCode());
                        } else {
                            logger.warn("Response status error: {}", exchange.getResponse().getStatusCode());
                        }
                    })
                    .onErrorResume(throwable -> {
                        ServerHttpResponse response = exchange.getResponse();

                        if (throwable instanceof ResponseStatusException) {
                            ResponseStatusException ex = (ResponseStatusException) throwable;
                            logger.error("Authorization error for request {}: {}", exchange.getRequest().getURI(), ex.getMessage());
                            response.setStatusCode(ex.getStatusCode());
                            return response.writeWith(Mono.just(response.bufferFactory().wrap(ex.getMessage().getBytes())));
                        } else {
                            logger.error("Error for request {}: {}", exchange.getRequest().getURI(), throwable.getMessage());
                            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                            return response.writeWith(Mono.just(response.bufferFactory().wrap("Internal Server Error".getBytes())));
                        }
                    })
                    .then();  // Ensures the Mono is properly terminated
        };
    }

    public static class Config {
        // Configuration properties can be added here
    }
}

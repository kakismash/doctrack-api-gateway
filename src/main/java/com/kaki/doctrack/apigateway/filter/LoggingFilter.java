package com.kaki.doctrack.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

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
                    .doOnSuccess(aVoid -> logger.info("Successful response for: {}", exchange.getRequest().getURI()))
                    .doOnError(throwable -> {
                        if (throwable instanceof ResponseStatusException) {
                            ResponseStatusException ex = (ResponseStatusException) throwable;
                            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                                logger.error("Authorization error for request {}: {}", exchange.getRequest().getURI(), ex.getMessage());
                            }
                        }
                    });
        };
    }

    public static class Config {
        // Configuration properties can be added here
    }
}

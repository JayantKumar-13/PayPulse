package com.paypulse.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class PaymentCircuitBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentCircuitBreakerConfig.class);

    @Bean(name = "paymentCircuitBreaker")
    CircuitBreaker paymentCircuitBreaker(AppProperties properties) {
        AppProperties.CircuitBreaker breaker = properties.getPayment().getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(breaker.getFailureRateThreshold())
            .slowCallRateThreshold(breaker.getSlowCallRateThreshold())
            .slowCallDurationThreshold(Duration.ofMillis(breaker.getSlowCallDurationThresholdMs()))
            .slidingWindowSize(breaker.getSlidingWindowSize())
            .minimumNumberOfCalls(breaker.getMinimumNumberOfCalls())
            .permittedNumberOfCallsInHalfOpenState(breaker.getPermittedNumberOfCallsInHalfOpenState())
            .waitDurationInOpenState(Duration.ofSeconds(breaker.getWaitDurationOpenSeconds()))
            .build();

        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("paymentGateway");
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> log.warn("[Circuit Breaker] paymentGateway {}", event.getStateTransition()))
            .onFailureRateExceeded(event -> log.error("[Circuit Breaker] paymentGateway failure rate exceeded: {}%", event.getFailureRate()))
            .onCallNotPermitted(event -> log.error("[Circuit Breaker] paymentGateway call rejected because circuit is OPEN"))
            .onError(event -> log.error("[Circuit Breaker] paymentGateway failure: {}", event.getThrowable().getMessage()))
            .onSuccess(event -> log.info("[Circuit Breaker] paymentGateway call successful"));
        return circuitBreaker;
    }
}

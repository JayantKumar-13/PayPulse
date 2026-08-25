package com.paypulse.service;

import com.paypulse.config.AppProperties;
import com.paypulse.dto.PaymentDtos;
import com.paypulse.exception.ApiException;
import com.paypulse.model.PaymentEntity;
import com.paypulse.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final TaskScheduler taskScheduler;
    private final AppProperties properties;
    private final WebhookService webhookService;
    private final RestClient restClient;
    private final CircuitBreaker paymentCircuitBreaker;

    public PaymentService(PaymentRepository paymentRepository,
                          TaskScheduler taskScheduler,
                          AppProperties properties,
                          WebhookService webhookService,
                          RestClient restClient,
                          @Qualifier("paymentCircuitBreaker") CircuitBreaker paymentCircuitBreaker) {
        this.paymentRepository = paymentRepository;
        this.taskScheduler = taskScheduler;
        this.properties = properties;
        this.webhookService = webhookService;
        this.restClient = restClient;
        this.paymentCircuitBreaker = paymentCircuitBreaker;
    }

    @Transactional
    public PaymentDtos.TopupResponse initiateTopup(String userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be greater than 0");
        }
        PaymentEntity payment = new PaymentEntity();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setUserId(userId);
        payment.setAmount(amount);
        paymentRepository.save(payment);
        scheduleProcessing(payment.getPaymentId());
        return new PaymentDtos.TopupResponse("Top-up initiated", payment.getPaymentId(), payment.getStatus());
    }

    public PaymentDtos.PaymentStatusResponse getTopupStatus(String userId, String paymentId) {
        PaymentEntity payment = paymentRepository.findByPaymentIdAndUserId(paymentId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        return new PaymentDtos.PaymentStatusResponse(
            payment.getPaymentId(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getCreatedAt(),
            payment.getUpdatedAt()
        );
    }

    private void scheduleProcessing(String paymentId) {
        int min = properties.getPayment().getMinDelayMs();
        int max = Math.max(min, properties.getPayment().getMaxDelayMs());
        long delay = ThreadLocalRandom.current().nextLong(min, max + 1L);
        taskScheduler.schedule(() -> processPayment(paymentId), Instant.now().plusMillis(delay));
    }

    private void processPayment(String paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || "SUCCESS".equals(payment.getStatus()) || "FAILED".equals(payment.getStatus())) {
            return;
        }

        payment.setStatus("PROCESSING");
        paymentRepository.save(payment);

        try {
            PaymentDtos.PaymentWebhookRequest payload = paymentCircuitBreaker.executeSupplier(() -> gatewayRoundTrip(payment));
            log.info("Gateway processed payment {} with status {}", payment.getPaymentId(), payload.status());
        } catch (CallNotPermittedException ex) {
            payment.setStatus("FAILED");
            payment.setFailureReason("Payment gateway is temporarily unavailable");
            paymentRepository.save(payment);
            log.warn("Payment processing rejected for {} because the circuit breaker is OPEN", paymentId);
        } catch (Exception ex) {
            payment.setStatus("FAILED");
            payment.setFailureReason(ex.getMessage());
            paymentRepository.save(payment);
            log.warn("Payment processing failed for {}: {}", paymentId, ex.getMessage());
        }
    }

    private PaymentDtos.PaymentWebhookRequest gatewayRoundTrip(PaymentEntity payment) {
        if (ThreadLocalRandom.current().nextDouble() < properties.getPayment().getGatewayFailureRate()) {
            throw new IllegalStateException("Payment gateway unavailable");
        }

        String status = ThreadLocalRandom.current().nextDouble() < properties.getPayment().getSuccessRate()
            ? "SUCCESS"
            : "FAILED";
        PaymentDtos.PaymentWebhookRequest payload = new PaymentDtos.PaymentWebhookRequest(
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            status,
            payment.getGateway(),
            "mock_" + UUID.randomUUID(),
            "FAILED".equals(status) ? "Mock gateway declined payment" : null
        );

        dispatchWebhook(payload);
        return payload;
    }

    private void dispatchWebhook(PaymentDtos.PaymentWebhookRequest payload) {
        if (properties.getPayment().getWebhookUrl() != null && !properties.getPayment().getWebhookUrl().isBlank()) {
            String rawBody = """
                {"paymentId":"%s","userId":"%s","amount":%s,"status":"%s","gateway":"%s","gatewayTxnId":"%s"%s}
                """.formatted(
                payload.paymentId(),
                payload.userId(),
                payload.amount().stripTrailingZeros().toPlainString(),
                payload.status(),
                payload.gateway(),
                payload.gatewayTxnId(),
                payload.failureReason() == null ? "" : ",\"failureReason\":\"" + payload.failureReason() + "\""
            ).replace("\n", "").trim();

            restClient.post()
                .uri(properties.getPayment().getWebhookUrl())
                .header("Content-Type", "application/json")
                .header("x-payment-signature", hmacSha256(rawBody, properties.getPayment().getWebhookSecret()))
                .body(rawBody)
                .retrieve()
                .toBodilessEntity();
            return;
        }
        webhookService.processPaymentWebhook(payload);
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign payload", ex);
        }
    }
}

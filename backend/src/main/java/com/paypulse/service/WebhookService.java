package com.paypulse.service;

import com.paypulse.config.AppProperties;
import com.paypulse.dto.PaymentDtos;
import com.paypulse.exception.ApiException;
import com.paypulse.model.PaymentEntity;
import com.paypulse.model.TransactionEntity;
import com.paypulse.model.WalletEntity;
import com.paypulse.repository.PaymentRepository;
import com.paypulse.repository.TransactionRepository;
import com.paypulse.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebhookService {

    private final AppProperties properties;
    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final Map<String, Long> processedEvents = new ConcurrentHashMap<>();

    public WebhookService(AppProperties properties,
                          PaymentRepository paymentRepository,
                          WalletRepository walletRepository,
                          TransactionRepository transactionRepository,
                          WalletService walletService) {
        this.properties = properties;
        this.paymentRepository = paymentRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
    }

    public boolean verifySignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = hmacSha256(rawBody, properties.getPayment().getWebhookSecret());
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public PaymentDtos.WebhookResponse processPaymentWebhook(PaymentDtos.PaymentWebhookRequest payload) {
        validatePayload(payload);
        String eventKey = payload.paymentId() + ":" + payload.gatewayTxnId();
        long expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L;
        Long existing = processedEvents.putIfAbsent(eventKey, expiresAt);
        if (existing != null && existing > System.currentTimeMillis()) {
            return new PaymentDtos.WebhookResponse(true, "Webhook already processed");
        }

        try {
            PaymentEntity payment = paymentRepository.findById(payload.paymentId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
            if (!payment.getUserId().equals(payload.userId()) || payment.getAmount().compareTo(payload.amount()) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Payment webhook does not match the original payment");
            }
            if ("SUCCESS".equals(payment.getStatus()) || "FAILED".equals(payment.getStatus())) {
                return new PaymentDtos.WebhookResponse(false, "Payment webhook processed");
            }

            payment.setStatus(payload.status());
            payment.setGatewayTxnId(payload.gatewayTxnId());
            payment.setFailureReason(payload.failureReason());
            paymentRepository.save(payment);

            if ("SUCCESS".equals(payload.status())) {
                WalletEntity wallet = walletRepository.findByUserId(payload.userId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found"));
                if (!"Active".equals(wallet.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Wallet is not active");
                }
                wallet.setBalance(wallet.getBalance().add(payload.amount()));
                walletRepository.save(wallet);

                TransactionEntity tx = new TransactionEntity();
                tx.setTransactionId(UUID.randomUUID().toString());
                tx.setType("topup");
                tx.setToWalletId(wallet.getId());
                tx.setAmount(payload.amount());
                tx.setStatus("success");
                tx.setNote("Wallet top-up via " + payment.getGateway());
                tx.setReceiverUsername("wallet-topup");
                tx.setPaymentId(payment.getPaymentId());
                tx.setGatewayTxnId(payload.gatewayTxnId());
                transactionRepository.save(tx);
            }

            walletService.evictWalletCaches(payload.userId());
            return new PaymentDtos.WebhookResponse(false, "Payment webhook processed");
        } catch (RuntimeException ex) {
            processedEvents.remove(eventKey);
            throw ex;
        }
    }

    private void validatePayload(PaymentDtos.PaymentWebhookRequest payload) {
        if (payload.paymentId() == null || payload.userId() == null || payload.gatewayTxnId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment webhook payload");
        }
        if (!"SUCCESS".equals(payload.status()) && !"FAILED".equals(payload.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment webhook payload");
        }
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

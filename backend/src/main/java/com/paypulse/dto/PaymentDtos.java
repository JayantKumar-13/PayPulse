package com.paypulse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {                //final -> cannot be extended

    private PaymentDtos() {
    }

    public record TopupRequest(
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000") BigDecimal amount
        // Because of precision errors we use BigDecimal instead of double
    ) {
    }

    public record TopupResponse(String message, String paymentId, String status) {

        // Why only these fields, because the payment is not completed yet.
    }

    public record PaymentStatusResponse(
        String paymentId,
        BigDecimal amount,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    //Why no validation annotations here , Because this payload comes from an external gateway.
    // Although we can add validations
    public record PaymentWebhookRequest(
        String paymentId,
        String userId,
        BigDecimal amount,
        String status,
        String gateway,
        String gatewayTxnId,
        String failureReason
    ) {
    }

    public record WebhookResponse(boolean duplicate, String message) {
        //Why track duplicates ,B/C Payment gateways often retry webhooks.

    }
}

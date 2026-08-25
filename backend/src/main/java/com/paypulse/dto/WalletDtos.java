package com.paypulse.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class WalletDtos {

    private WalletDtos() {
    }

    public record WalletResponse(BigDecimal balance, String currency, String status, String qrCode) {
    }

    public record TransactionResponse(
        String transactionId,
        BigDecimal amount,
        String status,
        String type,            // topup , transfer, refund etc
        String note,
        String senderUsername,
        String receiverUsername,
        String peerUsername,            //Backend can directly provide the name
        String paymentId,           //Used for top-ups.May be null for transfers.


        String gatewayTxnId,
        Instant createdAt
    ) {
    }
}

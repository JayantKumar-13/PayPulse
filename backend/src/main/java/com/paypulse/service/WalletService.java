package com.paypulse.service;

import com.paypulse.dto.WalletDtos;
import com.paypulse.exception.ApiException;
import com.paypulse.model.TransactionEntity;
import com.paypulse.model.WalletEntity;
import com.paypulse.repository.TransactionRepository;
import com.paypulse.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WalletService {

    private static final long BALANCE_TTL = 60;
    private static final long TXNS_TTL = 30;

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final CacheService cacheService;

    public WalletService(WalletRepository walletRepository,
                         TransactionRepository transactionRepository,
                         CacheService cacheService) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.cacheService = cacheService;
    }

    @SuppressWarnings("unchecked")
    public WalletDtos.WalletResponse getMyWallet(String userId) {
        String cacheKey = "cache:balance:" + userId;
        Object cached = cacheService.get(cacheKey);
        if (cached instanceof WalletDtos.WalletResponse response) {
            return response;
        }
        WalletEntity wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found"));
        WalletDtos.WalletResponse payload = new WalletDtos.WalletResponse(
            wallet.getBalance(),
            wallet.getCurrency(),
            wallet.getStatus(),
            wallet.getQrCode()
        );
        cacheService.put(cacheKey, payload, BALANCE_TTL);
        return payload;
    }

    public List<WalletDtos.TransactionResponse> getMyTransactions(String userId) {
        String cacheKey = "cache:txns:" + userId;
        Object cached = cacheService.get(cacheKey);
        if (cached instanceof List<?> list) {
            return (List<WalletDtos.TransactionResponse>) list;
        }

        WalletEntity wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found"));
        List<WalletDtos.TransactionResponse> payload = transactionRepository
            .findTop50ByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(wallet.getId(), wallet.getId())
            .stream()
            .map(tx -> mapTransaction(tx, wallet.getId()))
            .toList();
        cacheService.put(cacheKey, payload, TXNS_TTL);
        return payload;
    }

    public void evictWalletCaches(String... userIds) {
        String[] keys = new String[userIds.length * 2];
        for (int i = 0; i < userIds.length; i++) {
            keys[i * 2] = "cache:balance:" + userIds[i];
            keys[i * 2 + 1] = "cache:txns:" + userIds[i];
        }
        cacheService.evict(keys);
    }

    private WalletDtos.TransactionResponse mapTransaction(TransactionEntity tx, String walletId) {
        boolean isTopup = "topup".equals(tx.getType());
        boolean isDebit = !isTopup && walletId.equals(tx.getFromWalletId());
        return new WalletDtos.TransactionResponse(
            tx.getTransactionId(),
            tx.getAmount(),
            tx.getStatus(),
            isTopup ? "topup" : (isDebit ? "debit" : "credit"),
            tx.getNote(),
            tx.getSenderUsername(),
            tx.getReceiverUsername(),
            isTopup ? "MOCK_GATEWAY" : (isDebit ? tx.getReceiverUsername() : tx.getSenderUsername()),
            tx.getPaymentId(),
            tx.getGatewayTxnId(),
            tx.getCreatedAt()
        );
    }
}

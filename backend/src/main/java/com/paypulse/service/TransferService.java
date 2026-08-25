package com.paypulse.service;

import com.paypulse.dto.CommonDtos;
import com.paypulse.dto.TransactionDtos;
import com.paypulse.exception.ApiException;
import com.paypulse.model.AuditLogEntity;
import com.paypulse.model.TransactionEntity;
import com.paypulse.model.UserEntity;
import com.paypulse.model.WalletEntity;
import com.paypulse.repository.AuditLogRepository;
import com.paypulse.repository.TransactionRepository;
import com.paypulse.repository.UserRepository;
import com.paypulse.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final WalletService walletService;
    private final MailService mailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public TransferService(UserRepository userRepository,
                           WalletRepository walletRepository,
                           TransactionRepository transactionRepository,
                           AuditLogRepository auditLogRepository,
                           WalletService walletService,
                           MailService mailService) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.walletService = walletService;
        this.mailService = mailService;
    }

    @Transactional
    public CommonDtos.MessageResponse transferMoney(String senderId,
                                                    TransactionDtos.TransferRequest request,
                                                    String idempotencyKey) {
        UserEntity sender = userRepository.findById(senderId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid sender"));
        if (!sender.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can not send money because you are not active");
        }

        WalletEntity senderWallet = walletRepository.findByUserId(senderId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Sender wallet does not exist"));
        if (!"Active".equals(senderWallet.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your wallet is either frozen or closed");
        }

        UserEntity receiver = userRepository.findByUsername(request.receiverUsername().trim().toLowerCase())
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid receiver"));
        if (senderId.equals(receiver.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You are sending money to yourself");
        }
        if (!receiver.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can not send money because receiver is inactive");
        }

        WalletEntity receiverWallet = walletRepository.findByUserId(receiver.getId())
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Receiver does not have a wallet"));
        if (!"Active".equals(receiverWallet.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Receiver's wallet is either closed or frozen");
        }

        if (!passwordEncoder.matches(request.pin(), sender.getHashedPin())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You entered wrong pin");
        }

        BigDecimal amount = request.amount();
        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You don't have sufficient money");
        }

        BigDecimal totalSent = transactionRepository.sumSuccessfulSentSince(
            senderWallet.getId(),
            Instant.now().minusSeconds(24 * 60 * 60)
        );
        if (totalSent.add(amount).compareTo(new BigDecimal("100000")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot send more than ₹1,00,000 in 24 hours");
        }

        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }

        TransactionEntity tx = new TransactionEntity();
        tx.setTransactionId(UUID.randomUUID().toString());
        tx.setIdempotencyKey(idempotencyKey);
        tx.setType("transfer");
        tx.setFromWalletId(senderWallet.getId());
        tx.setToWalletId(receiverWallet.getId());
        tx.setAmount(amount);
        tx.setStatus("pending");
        tx.setNote(blankToNull(request.note()));
        tx.setSenderUsername(sender.getUsername());
        tx.setReceiverUsername(receiver.getUsername());
        transactionRepository.save(tx);

        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
        receiverWallet.setBalance(receiverWallet.getBalance().add(amount));
        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        tx.setStatus("success");
        transactionRepository.save(tx);

        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setEvent("transfer");
        auditLog.setTransactionId(tx.getTransactionId());
        auditLog.setFromUserId(senderId);
        auditLog.setToUserId(receiver.getId());
        auditLog.setAmount(amount);
        auditLog.setStatus("success");
        auditLogRepository.save(auditLog);

        walletService.evictWalletCaches(senderId, receiver.getId());
        mailService.sendTransactionEmail(senderId, amount, receiver.getUsername(), "success", tx.getTransactionId());
        return new CommonDtos.MessageResponse("Money sent successfully");
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

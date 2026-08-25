package com.paypulse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    public void sendOtpEmail(String email, String otp) {
        log.info("OTP for {} is {}", email, otp);
    }

    public void sendTransactionEmail(String userId, BigDecimal amount, String peerUsername, String status, String transactionId) {
        log.info("Transaction email queued for user={} status={} amount={} peer={} txn={}",
            userId, status, amount, peerUsername, transactionId);
    }
}

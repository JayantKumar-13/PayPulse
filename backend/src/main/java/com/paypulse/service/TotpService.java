package com.paypulse.service;

import com.paypulse.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class TotpService {

    private static final int STEP_SECONDS = 60;
    private static final int WINDOW = 1;
    private final AppProperties properties;

    public TotpService(AppProperties properties) {
        this.properties = properties;
    }

    public String generateOtp(String email) {
        return generateForCounter(normalize(email), currentCounter());
    }

    public boolean verifyOtp(String email, String token) {
        String normalizedEmail = normalize(email);
        long counter = currentCounter();
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (generateForCounter(normalizedEmail, counter + offset).equals(token)) {
                return true;
            }
        }
        return false;
    }

    private long currentCounter() {
        return Instant.now().getEpochSecond() / STEP_SECONDS;
    }

    private String generateForCounter(String email, long counter) {
        try {
            byte[] secret = (properties.getOtpSecret() + email).getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate OTP", ex);
        }
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}

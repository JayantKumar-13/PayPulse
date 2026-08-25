package com.paypulse.service;

import com.paypulse.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final long ACCESS_TOKEN_MINUTES = 15;
    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(AppProperties properties) {
        try {
            byte[] raw = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            this.secretKey = Keys.hmacShaKeyFor(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize JWT key", ex);
        }
    }

    public String generateAccessToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ACCESS_TOKEN_MINUTES * 60)))
            .signWith(secretKey)
            .compact();
    }

    public String parseAccessToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.getSubject();
    }

    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}

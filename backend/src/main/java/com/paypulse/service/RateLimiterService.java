package com.paypulse.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public synchronized Decision check(String keyPrefix, String identity, long windowMs, int max) {
        String key = keyPrefix + ":" + identity;
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;
        Deque<Long> timestamps = requestLog.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
            timestamps.removeFirst();
        }
        timestamps.addLast(now);
        int count = timestamps.size();
        boolean allowed = count <= max;
        long resetEpochSeconds = timestamps.isEmpty()
            ? Instant.ofEpochMilli(now + windowMs).getEpochSecond()
            : Instant.ofEpochMilli(timestamps.peekFirst() + windowMs).getEpochSecond();
        long retryAfter = Math.max(1, (windowMs + 999) / 1000);
        return new Decision(allowed, Math.max(0, max - count), resetEpochSeconds, retryAfter);
    }

    public record Decision(boolean allowed, int remaining, long resetEpochSeconds, long retryAfterSeconds) {
    }
}

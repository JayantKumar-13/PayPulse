package com.paypulse.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {

    private static final long LOCK_TTL_SECONDS = 60;
    private static final long RESPONSE_TTL_SECONDS = 24 * 60 * 60;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public synchronized ClaimResult claim(String key) {
        Entry entry = entries.get(key);
        if (entry != null && entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(key);
            entry = null;
        }

        if (entry == null) {
            entries.put(key, Entry.pending(Instant.now().plusSeconds(LOCK_TTL_SECONDS)));
            return new ClaimResult(ClaimState.CLAIMED, null);
        }

        if (entry.pending()) {
            return new ClaimResult(ClaimState.PENDING, null);
        }

        return new ClaimResult(ClaimState.COMPLETED, entry.response());
    }

    public synchronized void complete(String key, ResponseEntity<?> response) {
        entries.put(key, Entry.completed(
            new StoredResponse(response.getStatusCode().value(), response.getBody()),
            Instant.now().plusSeconds(RESPONSE_TTL_SECONDS)
        ));
    }

    public synchronized void release(String key) {
        entries.remove(key);
    }

    public enum ClaimState {
        CLAIMED,
        PENDING,
        COMPLETED
    }

    public record StoredResponse(int statusCode, Object body) {
    }

    public record ClaimResult(ClaimState state, StoredResponse storedResponse) {
    }

    private record Entry(boolean pending, StoredResponse response, Instant expiresAt) {
        static Entry pending(Instant expiresAt) {
            return new Entry(true, null, expiresAt);
        }

        static Entry completed(StoredResponse response, Instant expiresAt) {
            return new Entry(false, response, expiresAt);
        }
    }
}

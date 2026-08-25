package com.paypulse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Entry> fallbackStore = new ConcurrentHashMap<>();
    private final AtomicBoolean usingFallback = new AtomicBoolean(false);

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Object get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            fallbackStore.remove(key);
            markRedisHealthy();
            return value;
        } catch (DataAccessResourceFailureException ex) {
            markRedisUnavailable(ex);
            return getFromFallback(key);
        }
    }

    public void put(String key, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
            fallbackStore.remove(key);
            markRedisHealthy();
        } catch (DataAccessResourceFailureException ex) {
            markRedisUnavailable(ex);
            fallbackStore.put(key, new Entry(value, Instant.now().plusSeconds(ttlSeconds)));
        }
    }

    public void evict(String... keys) {
        try {
            redisTemplate.delete(Arrays.asList(keys));
            markRedisHealthy();
        } catch (DataAccessResourceFailureException ex) {
            markRedisUnavailable(ex);
        } finally {
            for (String key : keys) {
                fallbackStore.remove(key);
            }
        }
    }

    private Object getFromFallback(String key) {
        Entry entry = fallbackStore.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            fallbackStore.remove(key);
            return null;
        }
        return entry.value();
    }

    private void markRedisUnavailable(Exception ex) {
        if (usingFallback.compareAndSet(false, true)) {
            logger.warn("Redis cache unavailable, falling back to in-memory cache: {}", ex.getMessage());
        }
    }

    private void markRedisHealthy() {
        if (usingFallback.compareAndSet(true, false)) {
            logger.info("Redis cache connection restored");
        }
    }

    private record Entry(Object value, Instant expiresAt) {
    }
}

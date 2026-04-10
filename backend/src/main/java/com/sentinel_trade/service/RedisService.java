package com.sentinel_trade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for reading hot data from Redis.
 * Requirements: 5.1
 */
@Service
public class RedisService {

    private static final Logger LOG = LoggerFactory.getLogger(RedisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Get the latest KLine JSON for a symbol and interval.
     *
     * @param symbol   trading pair, e.g. "BTCUSDT"
     * @param interval time interval, e.g. "1m"
     * @return JSON string if present, empty otherwise
     */
    public Optional<String> getLatestKLine(String symbol, String interval) {
        String key = String.format("kline:%s:%s:latest", symbol, interval);
        try {
            String value = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            LOG.error("Redis GET failed for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get the latest alert JSON.
     */
    public Optional<String> getLatestAlert(String symbol) {
        String key = String.format("alert:%s:latest", symbol);
        try {
            String value = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            LOG.error("Redis GET failed for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
}

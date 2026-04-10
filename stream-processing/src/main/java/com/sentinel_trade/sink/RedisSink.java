package com.sentinel_trade.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel_trade.model.KLine;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Flink sink that writes the latest KLine to Redis with TTL=60s.
 *
 * Key format: kline:{symbol}:{interval}:latest
 *
 * Requirements: 4.1
 */
public class RedisSink extends RichSinkFunction<KLine> {

    private static final Logger LOG = LoggerFactory.getLogger(RedisSink.class);
    private static final int TTL_SECONDS = 60;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String host;
    private final int port;

    private transient JedisPool jedisPool;

    public RedisSink(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void open(Configuration parameters) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(10);
        jedisPool = new JedisPool(config, host, port);
        LOG.info("RedisSink connected to {}:{}", host, port);
    }

    @Override
    public void invoke(KLine kline, Context context) {
        String key = String.format("kline:%s:%s:latest", kline.getSymbol(), kline.getInterval());
        try (Jedis jedis = jedisPool.getResource()) {
            String json = MAPPER.writeValueAsString(kline);
            jedis.setex(key, TTL_SECONDS, json);
            LOG.debug("Redis SET {} TTL={}", key, TTL_SECONDS);
        } catch (Exception e) {
            LOG.error("RedisSink failed to write key={}: {}", key, e.getMessage());
            // Non-fatal: hot data can be lost without blocking the pipeline
        }
    }

    @Override
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}

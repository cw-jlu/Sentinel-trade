package com.sentinel_trade.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic JSON serialization schema using Jackson.
 *
 * Requirements: 2.4, 3.3
 */
public class JsonSerializationSchema<T> implements SerializationSchema<T> {

    private static final Logger LOG = LoggerFactory.getLogger(JsonSerializationSchema.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte[] serialize(T element) {
        try {
            return MAPPER.writeValueAsBytes(element);
        } catch (Exception e) {
            LOG.error("Failed to serialize element to JSON: {}", e.getMessage());
            return new byte[0];
        }
    }
}

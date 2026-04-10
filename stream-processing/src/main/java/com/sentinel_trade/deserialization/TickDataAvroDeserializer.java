package com.sentinel_trade.deserialization;

import com.sentinel_trade.model.TickData;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Deserializes Avro-encoded TickData bytes (Object Container File format)
 * produced by the Python ingestion module.
 *
 * Requirements: 2.1
 */
public class TickDataAvroDeserializer implements DeserializationSchema<TickData> {

    private static final Logger LOG = LoggerFactory.getLogger(TickDataAvroDeserializer.class);

    @Override
    public TickData deserialize(byte[] message) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(message)) {
            DatumReader<GenericRecord> reader = new GenericDatumReader<>();
            try (DataFileStream<GenericRecord> stream = new DataFileStream<>(bais, reader)) {
                if (!stream.hasNext()) {
                    LOG.warn("Avro message contains no records");
                    return null;
                }
                GenericRecord record = stream.next();
                return new TickData(
                        record.get("symbol").toString(),
                        new BigDecimal(record.get("price").toString()),
                        new BigDecimal(record.get("quantity").toString()),
                        (Long) record.get("timestamp"),
                        record.get("trade_id").toString(),
                        (Boolean) record.get("is_buyer_maker")
                );
            }
        } catch (Exception e) {
            LOG.error("Failed to deserialize Avro message: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(TickData nextElement) {
        return false;
    }

    @Override
    public TypeInformation<TickData> getProducedType() {
        return TypeInformation.of(TickData.class);
    }
}

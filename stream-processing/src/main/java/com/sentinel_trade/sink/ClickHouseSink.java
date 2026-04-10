package com.sentinel_trade.sink;

import com.sentinel_trade.model.TickData;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Flink sink that batch-writes TickData to ClickHouse.
 * Flushes every BATCH_SIZE records or on close.
 *
 * Requirements: 4.2
 */
public class ClickHouseSink extends RichSinkFunction<TickData> {

    private static final Logger LOG = LoggerFactory.getLogger(ClickHouseSink.class);
    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_SQL =
            "INSERT INTO sentinel_trade.tick_data " +
            "(symbol, price, quantity, timestamp, trade_id, is_buyer_maker) VALUES (?,?,?,?,?,?)";

    private final String jdbcUrl;

    private transient Connection connection;
    private transient PreparedStatement statement;
    private transient List<TickData> buffer;

    public ClickHouseSink(String clickhouseUrl) {
        // e.g. "jdbc:clickhouse://localhost:8123/sentinel_trade"
        this.jdbcUrl = clickhouseUrl;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        connection = DriverManager.getConnection(jdbcUrl);
        statement = connection.prepareStatement(INSERT_SQL);
        buffer = new ArrayList<>(BATCH_SIZE);
        LOG.info("ClickHouseSink connected to {}", jdbcUrl);
    }

    @Override
    public void invoke(TickData tick, Context context) throws Exception {
        buffer.add(tick);
        if (buffer.size() >= BATCH_SIZE) {
            flush();
        }
    }

    private void flush() throws Exception {
        if (buffer.isEmpty()) return;
        for (TickData tick : buffer) {
            statement.setString(1, tick.getSymbol());
            statement.setBigDecimal(2, tick.getPrice());
            statement.setBigDecimal(3, tick.getQuantity());
            statement.setTimestamp(4, new Timestamp(tick.getTimestamp()));
            statement.setString(5, tick.getTradeId());
            statement.setInt(6, tick.isBuyerMaker() ? 1 : 0);
            statement.addBatch();
        }
        statement.executeBatch();
        LOG.debug("ClickHouseSink flushed {} records", buffer.size());
        buffer.clear();
    }

    @Override
    public void close() throws Exception {
        try {
            flush();
        } finally {
            if (statement != null) statement.close();
            if (connection != null) connection.close();
        }
    }
}

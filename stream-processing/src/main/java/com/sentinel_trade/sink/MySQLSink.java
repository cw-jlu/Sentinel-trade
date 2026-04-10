package com.sentinel_trade.sink;

import com.sentinel_trade.model.KLine;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Flink sink that writes aggregated KLine data to MySQL permanently.
 *
 * Requirements: 4.3
 */
public class MySQLSink extends RichSinkFunction<KLine> {

    private static final Logger LOG = LoggerFactory.getLogger(MySQLSink.class);

    private static final String INSERT_SQL =
            "INSERT INTO kline_aggregated " +
            "(symbol, `interval`, open_time, open_price, high_price, low_price, close_price, volume, trade_count) " +
            "VALUES (?,?,?,?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE " +
            "open_price=VALUES(open_price), high_price=VALUES(high_price), " +
            "low_price=VALUES(low_price), close_price=VALUES(close_price), " +
            "volume=VALUES(volume), trade_count=VALUES(trade_count)";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private transient Connection connection;
    private transient PreparedStatement statement;

    public MySQLSink(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        connection = DriverManager.getConnection(jdbcUrl, username, password);
        connection.setAutoCommit(true);
        statement = connection.prepareStatement(INSERT_SQL);
        LOG.info("MySQLSink connected to {}", jdbcUrl);
    }

    @Override
    public void invoke(KLine kline, Context context) throws Exception {
        try {
            statement.setString(1, kline.getSymbol());
            statement.setString(2, kline.getInterval());
            statement.setTimestamp(3, new Timestamp(kline.getOpenTime()));
            statement.setBigDecimal(4, kline.getOpen());
            statement.setBigDecimal(5, kline.getHigh());
            statement.setBigDecimal(6, kline.getLow());
            statement.setBigDecimal(7, kline.getClose());
            statement.setBigDecimal(8, kline.getVolume());
            statement.setInt(9, kline.getTradeCount());
            statement.executeUpdate();
            LOG.debug("MySQLSink wrote KLine symbol={} interval={} openTime={}",
                    kline.getSymbol(), kline.getInterval(), kline.getOpenTime());
        } catch (SQLException e) {
            LOG.error("MySQLSink failed to write KLine: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void close() throws Exception {
        if (statement != null) statement.close();
        if (connection != null) connection.close();
    }
}

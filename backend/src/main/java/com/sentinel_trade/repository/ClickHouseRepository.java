package com.sentinel_trade.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Repository for querying raw tick data from ClickHouse.
 * Requirements: 5.3
 */
@Repository
public class ClickHouseRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ClickHouseRepository.class);
    private static final int MAX_RESULTS = 10_000;

    private final JdbcTemplate clickHouseJdbc;

    public ClickHouseRepository(JdbcTemplate clickHouseJdbc) {
        this.clickHouseJdbc = clickHouseJdbc;
    }

    /**
     * Query tick data for a symbol within a time range.
     *
     * @param symbol    trading pair, e.g. "BTCUSDT"
     * @param startTime start timestamp in milliseconds
     * @param endTime   end timestamp in milliseconds
     * @return list of tick records as maps (max 10,000)
     */
    public List<Map<String, Object>> queryTicks(String symbol, long startTime, long endTime) {
        String sql = """
                SELECT symbol, price, quantity, timestamp, trade_id, is_buyer_maker
                FROM sentinel_trade.tick_data
                WHERE symbol = ?
                  AND timestamp BETWEEN toDateTime64(?, 3) AND toDateTime64(?, 3)
                ORDER BY timestamp ASC
                LIMIT ?
                """;
        try {
            return clickHouseJdbc.query(sql,
                    (rs, rowNum) -> mapRow(rs),
                    symbol,
                    startTime / 1000.0,
                    endTime / 1000.0,
                    MAX_RESULTS);
        } catch (Exception e) {
            LOG.error("ClickHouse query failed for symbol={}: {}", symbol, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> mapRow(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("symbol", rs.getString("symbol"));
        row.put("price", rs.getBigDecimal("price"));
        row.put("quantity", rs.getBigDecimal("quantity"));
        row.put("timestamp", rs.getTimestamp("timestamp").getTime());
        row.put("trade_id", rs.getString("trade_id"));
        row.put("is_buyer_maker", rs.getInt("is_buyer_maker") == 1);
        return row;
    }
}

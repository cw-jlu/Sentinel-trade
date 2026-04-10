package com.sentinel_trade.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ClickHouse JDBC datasource configuration (separate from MySQL datasource).
 * Requirements: 5.3
 */
@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.jdbc-url:jdbc:clickhouse://localhost:8123/sentinel_trade}")
    private String jdbcUrl;

    public DataSource clickHouseDataSource() throws SQLException {
        Properties props = new Properties();
        return new ClickHouseDataSource(jdbcUrl, props);
    }

    @Bean(name = "clickHouseJdbc")
    public JdbcTemplate clickHouseJdbcTemplate() throws SQLException {
        return new JdbcTemplate(clickHouseDataSource());
    }
}

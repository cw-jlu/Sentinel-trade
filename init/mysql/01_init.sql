-- MySQL initialization script for Sentinel-Trade
-- Requirement 4.3: Store aggregated K-line data permanently

CREATE DATABASE IF NOT EXISTS sentinel_trade
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE sentinel_trade;

CREATE TABLE IF NOT EXISTS kline_aggregated (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol      VARCHAR(20)                         NOT NULL,
    `interval`  ENUM('1m', '5m', '1h', '1d')       NOT NULL,
    open_time   DATETIME(3)                         NOT NULL,
    open_price  DECIMAL(18, 8)                      NOT NULL,
    high_price  DECIMAL(18, 8)                      NOT NULL,
    low_price   DECIMAL(18, 8)                      NOT NULL,
    close_price DECIMAL(18, 8)                      NOT NULL,
    volume      DECIMAL(18, 8)                      NOT NULL,
    trade_count INT UNSIGNED                        NOT NULL DEFAULT 0,
    INDEX idx_symbol_interval_time (symbol, `interval`, open_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

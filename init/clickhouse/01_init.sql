-- ClickHouse initialization script for Sentinel-Trade
-- Requirement 4.2: Store Tick_Data with TTL of 7 days

CREATE DATABASE IF NOT EXISTS sentinel_trade;

CREATE TABLE IF NOT EXISTS sentinel_trade.tick_data
(
    symbol          String,
    price           Decimal(18, 8),
    quantity        Decimal(18, 8),
    timestamp       DateTime64(3),
    trade_id        String,
    is_buyer_maker  UInt8
)
ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (symbol, timestamp)
TTL toDateTime(timestamp) + INTERVAL 7 DAY
SETTINGS index_granularity = 8192;

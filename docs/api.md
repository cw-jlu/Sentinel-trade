# Sentinel-Trade API 文档

## REST API

Base URL: `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

### GET /api/history/ticks

查询历史逐笔成交数据（来自 ClickHouse，最多返回 10,000 条）。

**参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| symbol | string | 是 | 交易对，如 `BTCUSDT` |
| startTime | long | 是 | 开始时间戳（毫秒） |
| endTime | long | 是 | 结束时间戳（毫秒） |

**响应示例**

```json
[
  {
    "symbol": "BTCUSDT",
    "price": 45123.56,
    "quantity": 0.125,
    "timestamp": 1704067200000,
    "trade_id": "12345678",
    "is_buyer_maker": false
  }
]
```

---

### GET /api/history/klines

查询历史 K 线数据（来自 MySQL）。

**参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| symbol | string | 是 | 交易对 |
| interval | string | 是 | 时间间隔：`1m`, `5m`, `1h` |
| startTime | long | 是 | 开始时间戳（毫秒） |
| endTime | long | 是 | 结束时间戳（毫秒） |
| page | int | 否 | 页码，默认 0 |
| size | int | 否 | 每页数量，默认 500，最大 5000 |

**响应示例**

```json
[
  {
    "symbol": "BTCUSDT",
    "interval": "1m",
    "openTime": "2024-01-01T00:00:00",
    "openPrice": 45000.00,
    "highPrice": 45200.00,
    "lowPrice": 44900.00,
    "closePrice": 45100.00,
    "volume": 12.345,
    "tradeCount": 156
  }
]
```

---

## WebSocket API

连接地址: `ws://localhost:8080/ws/market-data`

服务端每秒推送最新 K 线和告警数据。

### K 线消息

```json
{
  "type": "kline",
  "symbol": "BTCUSDT",
  "interval": "1m",
  "data": {
    "symbol": "BTCUSDT",
    "interval": "1m",
    "openTime": 1704067200000,
    "open": "45000.00",
    "high": "45200.00",
    "low": "44900.00",
    "close": "45100.00",
    "volume": "12.345",
    "tradeCount": 156
  }
}
```

### 告警消息

```json
{
  "type": "alert",
  "symbol": "BTCUSDT",
  "data": {
    "alertId": "uuid-string",
    "alertType": "LARGE_ORDER",
    "symbol": "BTCUSDT",
    "timestamp": 1704067200000,
    "severity": "HIGH",
    "price": "45123.56",
    "quantity": "1.5",
    "amount": "67685.34"
  }
}
```

### 告警类型

| alertType | severity | 触发条件 |
|-----------|----------|----------|
| `LARGE_ORDER` | `HIGH` | 单笔成交金额 > 50,000 USDT |
| `FLASH_CRASH` | `CRITICAL` | 10 秒内价格变化 > 2% |

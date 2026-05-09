# Sentinel-Trade 项目报告

**项目成员**：40240223 陈为、16230823 季楷昊、20240708 李涛君、40240208 张泽壑、40240216 梁贺禹

代码仓库：[https://github.com/cw-jlu/Sentinel-trade/tree/main](https://github.com/cw-jlu/Sentinel-trade/tree/main)

---

## 一、项目是干什么的、处理的是什么数据

### 1.1 项目介绍

我们做的是一套**实时看币成交、算 K 线、顺便做点异常提醒**的小系统。大致过程是：用程序和 **Binance（币安）** 的 **WebSocket**（就是和网站保持一个长连接，对方有成交就推一条消息过来）连着；收到 JSON 之后，在 **Python 采集端**里整理成统一格式，用 **Avro**（一种带 schema 的二进制格式，省空间、类型清楚）打包，丢进 **Kafka**（消息队列，先存一条是一条）。然后 **Flink**（专门做流式计算的框架）从 Kafka 里读这些成交，**去掉重复的 trade_id**，按分钟 / 5 分钟 / 小时算 **K 线**，同时跑一个简单的**异常检测**；算出来的东西再分别写到 **Kafka（给别人继续用）**、**Redis（给页面读最新的 K 线）**、**ClickHouse（存很多笔成交明细）**、**MySQL（把 K 线长期存起来）**。

### 1.2 数据格式


| 项目       | 说明                                                                            |
| -------- | ----------------------------------------------------------------------------- |
| **从哪来**  | 币安 `aggTrade` 流，默认 `BTCUSDT`，也可以改环境变量换别的交易对                                   |
| **刚收到时** | 一长串 JSON，字段是缩写，比如 `s` 是交易对、`p` 价格、`q` 数量、`T` 时间戳、`a` 成交编号、`m` 是不是买方挂单成交等      |
| **整理之后** | 我们叫它 **Tick**：交易对、价格、数量、时间戳、唯一的 `trade_id`、以及 `is_buyer_maker` 这类后面分析可能用得上的字段 |


**成交额（名义）**：**amount = price × quantity**，后面写「大单」规则会用到；价格和数量在代码里用高精度小数算，单位可以理解为 **USDT** 这种报价币。

---

## 二、数据从头到尾怎么走

下面这张图是按我们仓库里 `**ingestion/`**（采集）和 `**stream-processing/**`（Flink）两个模块的真实顺序画的，方便后面几节对着看。

```text
币安 WebSocket
  → Python 采集：解析 JSON、清洗、变成 Tick、再打成 Avro
      → Kafka 里名叫 raw-tick-data 的队列（消息 key 用交易对 symbol）
          → Flink：把 Avro 解回来；按真实成交时间 + 一点延迟容忍；按 trade_id 去重
              ├→ 按时间窗口算 K 线（1 分钟、5 分钟、1 小时）
              ├→ 同一条「已经去重」的流再做异常检测 → 告警
              └→ 往外写：
                    ├→ Kafka：几根 K 线各自一个 topic，告警一个 topic（内容是 JSON）
                    ├→ Redis：每个周期只保留「最新一根」K 线，带过期时间
                    ├→ MySQL：K 线写进关系库，方便以后按条件查
                    └→ ClickHouse：每一笔成交攒一批再插入，适合存很多明细
```

---

## 三、采集端怎么写进 Kafka

采集服务里，每条校验通过的 Tick 会先 `serialize_avro` 成字节数组，再发到 Kafka。Topic 名字默认叫 `**raw-tick-data**`，和 Flink 里读的那个字符串要对上。消息的 **key** 用 `**symbol`（交易对）**，这样同一个币的消息会落到同一个分区里，后面 Flink 里 `keyBy(交易对)` 也对得上。

```python
# ingestion/main.py
KAFKA_TOPIC: str = os.getenv("KAFKA_TOPIC", "raw-tick-data")
# ...
            avro_bytes = serialize_avro(tick)
            await self._producer.send(KAFKA_TOPIC, key=tick.symbol, value=avro_bytes)
```

```python
# ingestion/kafka_producer.py
            await self._producer.send(
                topic,
                key=key.encode("utf-8"),
                value=value,
            )
```

---

## 四、Flink 里在算什么、代码怎么串的

### 4.1 我们在代码里的实际顺序

可以对着 `SentinelTradeJob.java` 从上往下看，大概是这几步：

1. **从 Kafka 读**：用 `KafkaSource` 订 `raw-tick-data`，用自带的 `TickDataAvroDeserializer` 把二进制变回 Java 对象。
2. **时间怎么处理**：用 Flink 的 `WatermarkStrategy`，允许数据稍微乱序几分钟内到齐（默认多等 5 秒那种思路），时间戳用每条成交里的 `getTimestamp()`。
3. **去重**：先按 `symbol` 分组，再进 `DeduplicationFilter`，同一个 `trade_id` 不要算两遍。
4. **K 线**：在「已经去重」的那条流上，分别开 1 分钟、5 分钟、1 小时的滚动窗口，每个窗口调 `KLineAggregator` 聚成一根 K 线。
5. **异常检测**：还是那条去重后的流，按 `symbol` 分组进 `AnomalyDetector`，得到 `alerts`。
6. **写出去**：三根 K 线各自先 `sinkTo` 自己的 Kafka topic（值用 JSON），再 `addSink` 到 Redis、MySQL；**逐笔**那条是 `dedupedStream` 单独接一个 `ClickHouseSink`。

### 4.2 主要代码（`SentinelTradeJob` 里搭管道的那一段）

```java
// stream-processing/src/main/java/com/sentinel_trade/SentinelTradeJob.java
        env.enableCheckpointing(30_000);
        env.setParallelism(1);

        KafkaSource<TickData> kafkaSource = KafkaSource.<TickData>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setTopics(SOURCE_TOPIC)
                .setGroupId("sentinel-flink-consumer")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TickDataAvroDeserializer())
                .build();

        WatermarkStrategy<TickData> watermarkStrategy = WatermarkStrategy
                .<TickData>forBoundedOutOfOrderness(WATERMARK_DELAY)
                .withTimestampAssigner((tick, recordTimestamp) -> tick.getTimestamp());

        DataStream<TickData> rawStream = env
                .fromSource(kafkaSource, watermarkStrategy, "Kafka Source: raw-tick-data");

        SingleOutputStreamOperator<TickData> dedupedStream = rawStream
                .keyBy(TickData::getSymbol)
                .process(new DeduplicationFilter())
                .name("Deduplication Filter");

        DataStream<KLine> kline1m = dedupedStream
                .keyBy(TickData::getSymbol)
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .process(new KLineAggregator("1m"))
                .name("KLine Aggregator 1m");
        DataStream<KLine> kline5m = dedupedStream
                .keyBy(TickData::getSymbol)
                .window(TumblingEventTimeWindows.of(Time.minutes(5)))
                .process(new KLineAggregator("5m"))
                .name("KLine Aggregator 5m");
        DataStream<KLine> kline1h = dedupedStream
                .keyBy(TickData::getSymbol)
                .window(TumblingEventTimeWindows.of(Time.hours(1)))
                .process(new KLineAggregator("1h"))
                .name("KLine Aggregator 1h");

        DataStream<Alert> alerts = dedupedStream
                .keyBy(TickData::getSymbol)
                .process(new AnomalyDetector())
                .name("Anomaly Detector");

        String mysqlUrl = System.getenv().getOrDefault("MYSQL_URL", "jdbc:mysql://mysql:3306/sentinel_trade?useSSL=false");
        String mysqlUser = System.getenv().getOrDefault("MYSQL_USER", "root");
        String mysqlPass = System.getenv().getOrDefault("MYSQL_PASSWORD", "sentinel123");
        String clickhouseUrl = System.getenv().getOrDefault("CLICKHOUSE_JDBC_URL", "jdbc:clickhouse://clickhouse:8123/sentinel_trade");
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "redis");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        kline1m.sinkTo(buildKafkaSink(TOPIC_KLINE_1M)).name("Sink: kline-1m (Kafka)");
        kline5m.sinkTo(buildKafkaSink(TOPIC_KLINE_5M)).name("Sink: kline-5m (Kafka)");
        kline1h.sinkTo(buildKafkaSink(TOPIC_KLINE_1H)).name("Sink: kline-1h (Kafka)");
        alerts.sinkTo(buildKafkaSink(TOPIC_ALERTS)).name("Sink: alerts (Kafka)");

        kline1m.addSink(new com.sentinel_trade.sink.RedisSink(redisHost, redisPort)).name("Sink: kline-1m (Redis)");
        kline5m.addSink(new com.sentinel_trade.sink.RedisSink(redisHost, redisPort)).name("Sink: kline-5m (Redis)");
        kline1h.addSink(new com.sentinel_trade.sink.RedisSink(redisHost, redisPort)).name("Sink: kline-1h (Redis)");

        kline1m.addSink(new com.sentinel_trade.sink.MySQLSink(mysqlUrl, mysqlUser, mysqlPass)).name("Sink: kline-1m (MySQL)");
        kline5m.addSink(new com.sentinel_trade.sink.MySQLSink(mysqlUrl, mysqlUser, mysqlPass)).name("Sink: kline-5m (MySQL)");
        kline1h.addSink(new com.sentinel_trade.sink.MySQLSink(mysqlUrl, mysqlUser, mysqlPass)).name("Sink: kline-1h (MySQL)");

        dedupedStream.addSink(new com.sentinel_trade.sink.ClickHouseSink(clickhouseUrl)).name("Sink: tick-data (ClickHouse)");

        LOG.info("Sentinel-Trade pipeline built – brokers={}", KAFKA_BROKERS);
        return env;
    }
```

再往 Kafka 里写 K 线、告警的时候，用的是下面这个辅助方法：**Topic 名字传进去**，**value 用 JSON 序列化**。

```java
    private static <T> KafkaSink<T> buildKafkaSink(String topic) {
        return KafkaSink.<T>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<T>builder()
                                .setTopic(topic)
                                .setValueSerializationSchema(new JsonSerializationSchema<T>())
                                .build()
                )
                .build();
    }
```

---

## 五、写进 Redis、ClickHouse、MySQL 的具体写法

这一节只讲「**写库**」三件事，**不再重复**上一节 Flink 怎么搭的，避免说两遍。下面出现的 **JDBC** 可以理解成「Java 里用标准方式连数据库、执行 SQL」。

### 5.1 Redis：每个周期最新一根 K 线，60 秒过期

```java
// stream-processing/.../sink/RedisSink.java
    private static final int TTL_SECONDS = 60;

    @Override
    public void invoke(KLine kline, Context context) {
        String key = String.format("kline:%s:%s:latest", kline.getSymbol(), kline.getInterval());
        try (Jedis jedis = jedisPool.getResource()) {
            String json = MAPPER.writeValueAsString(kline);
            jedis.setex(key, TTL_SECONDS, json);
        } catch (Exception e) {
            LOG.error("RedisSink failed to write key={}: {}", key, e.getMessage());
        }
    }
```

意思就是：键名里带上交易对和周期，值是一整根 K 线的 JSON；`setex` 自带 **60 秒过期**，页面来读的时候一般只关心「最近一根」就够了。写挂了只打日志，不把整个 Flink 任务拖死。

### 5.2 ClickHouse：一千条一批往明细表里插

```java
// stream-processing/.../sink/ClickHouseSink.java
    private static final int BATCH_SIZE = 1000;
    private static final String INSERT_SQL =
            "INSERT INTO sentinel_trade.tick_data " +
            "(symbol, price, quantity, timestamp, trade_id, is_buyer_maker) VALUES (?,?,?,?,?,?)";

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
        buffer.clear();
    }
```

先攒在内存 `buffer` 里，满 **1000 条** 就 `executeBatch` 一次，比来一条写一条省很多网络往返。`is_buyer_maker` 在表里用 **0/1** 存。

### 5.3 MySQL：K 线插入，如果「主键/唯一键冲突」就改成更新

下面 SQL 里带了 `**ON DUPLICATE KEY UPDATE`**：意思是，如果 MySQL 觉得「这条和表里已有某条唯一记录撞了」，就把后面的 OHLCV 等字段更新掉，而不是再插一行。  
**注意**：我们仓库里的 `01_init.sql` 如果只建了普通索引、没有建「能唯一确定一根 K 线」的那种唯一键，那这句话在数据库里**不一定会按你想的那样变成更新**，上线前要和表结构对一下；没有唯一约束的话，就当成普通 `INSERT` 理解就行。

```java
// stream-processing/.../sink/MySQLSink.java
    private static final String INSERT_SQL =
            "INSERT INTO kline_aggregated " +
            "(symbol, `interval`, open_time, open_price, high_price, low_price, close_price, volume, trade_count) " +
            "VALUES (?,?,?,?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE " +
            "open_price=VALUES(open_price), high_price=VALUES(high_price), " +
            "low_price=VALUES(low_price), close_price=VALUES(close_price), " +
            "volume=VALUES(volume), trade_count=VALUES(trade_count)";

    @Override
    public void invoke(KLine kline, Context context) throws Exception {
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
    }
```

### 5.4 数据库里表是怎么建的（和上面 Java 里写的列要对上）

下面两段就是项目里初始化脚本 `**init/clickhouse/01_init.sql**` 和 `**init/mysql/01_init.sql**` 的内容：字段名、顺序最好和 `INSERT` 里一一对应，不然运行时会报错或插错列。

```sql
-- init/clickhouse/01_init.sql
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
```

```sql
-- init/mysql/01_init.sql
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
```

---

## 六、小结

1. **整体**：第二节画了从币安到 Kafka、再到 Flink、再到几种数据库的**一条线**；第三节是**怎么进 Kafka**；第四节是 **Flink 怎么算、怎么再往 Kafka 写**；第五节是 **Redis / ClickHouse / MySQL 里具体怎么写、表长什么样**。
2. **谁干什么**：Redis 适合给前端「瞄一眼最新 K 线」；ClickHouse 适合堆很多笔成交做分析；MySQL 里那张 K 线表适合按交易对、周期、时间去查历史。
3. **Kafka 出现两次**：一次是喂给 Flink 的原始成交，一次是 Flink 算完再吐出去给别的程序用，结构上就是「中间都过一遍 Kafka，好拆开、也好排查」。

---

## 七、参考文献

- 币安 WebSocket 文档：[https://binance-docs.github.io/apidocs/spot/cn/](https://binance-docs.github.io/apidocs/spot/cn/)  
- 本仓库里的设计说明：`docs/architecture.md`、`docs/design.md`  
- Kafka、Flink、Redis、ClickHouse、MySQL 的官方文档（查语法、配置时用的）


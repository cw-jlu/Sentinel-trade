package com.sentinel_trade;

import com.sentinel_trade.aggregation.KLineAggregator;
import com.sentinel_trade.deduplication.DeduplicationFilter;
import com.sentinel_trade.deserialization.TickDataAvroDeserializer;
import com.sentinel_trade.detection.AnomalyDetector;
import com.sentinel_trade.model.Alert;
import com.sentinel_trade.model.KLine;
import com.sentinel_trade.model.TickData;
import com.sentinel_trade.serialization.JsonSerializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Main Flink streaming job for Sentinel-Trade.
 *
 * Pipeline:
 *   KafkaSource (raw-tick-data)
 *     → Watermark assignment
 *     → Deduplication (by trade_id)
 *     → K-Line aggregation (1m, 5m, 1h tumbling windows)
 *     → Anomaly detection (large order, flash crash)
 *     → KafkaSink (kline-1m, kline-5m, kline-1h, alerts)
 *
 * Requirements: 2.1, 2.2, 3.1, 3.2
 */
public class SentinelTradeJob {

    private static final Logger LOG = LoggerFactory.getLogger(SentinelTradeJob.class);

    // -----------------------------------------------------------------------
    // Configuration (from environment variables or defaults)
    // -----------------------------------------------------------------------

    private static final String KAFKA_BROKERS =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

    private static final String SOURCE_TOPIC = "raw-tick-data";
    private static final String TOPIC_KLINE_1M  = "kline-1m";
    private static final String TOPIC_KLINE_5M  = "kline-5m";
    private static final String TOPIC_KLINE_1H  = "kline-1h";
    private static final String TOPIC_ALERTS    = "alerts";

    /** Watermark out-of-orderness tolerance: 5 seconds (Requirement 2.3) */
    private static final Duration WATERMARK_DELAY = Duration.ofSeconds(5);

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = buildPipeline(
                StreamExecutionEnvironment.getExecutionEnvironment());
        env.execute("Sentinel-Trade Streaming Job");
    }

    /**
     * Builds the full Flink pipeline. Extracted for testability.
     *
     * @param env the execution environment (can be a local test env)
     * @return the configured environment (not yet executed)
     */
    public static StreamExecutionEnvironment buildPipeline(
            StreamExecutionEnvironment env) {

        // Checkpointing every 30 seconds
        env.enableCheckpointing(30_000);
        env.setParallelism(1);

        // ── Source ──────────────────────────────────────────────────────────
        KafkaSource<TickData> kafkaSource = KafkaSource.<TickData>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setTopics(SOURCE_TOPIC)
                .setGroupId("sentinel-flink-consumer")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TickDataAvroDeserializer())
                .build();

        // ── Watermark strategy (event time, 5s out-of-orderness) ────────────
        WatermarkStrategy<TickData> watermarkStrategy = WatermarkStrategy
                .<TickData>forBoundedOutOfOrderness(WATERMARK_DELAY)
                .withTimestampAssigner((tick, recordTimestamp) -> tick.getTimestamp());

        DataStream<TickData> rawStream = env
                .fromSource(kafkaSource, watermarkStrategy, "Kafka Source: raw-tick-data");

        // ── Deduplication ───────────────────────────────────────────────────
        SingleOutputStreamOperator<TickData> dedupedStream = rawStream
                .keyBy(TickData::getSymbol)
                .process(new DeduplicationFilter())
                .name("Deduplication Filter");

        // ── K-Line aggregation (1m, 5m, 1h) ────────────────────────────────
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

        // ── Anomaly detection ───────────────────────────────────────────────
        DataStream<Alert> alerts = dedupedStream
                .keyBy(TickData::getSymbol)
                .process(new AnomalyDetector())
                .name("Anomaly Detector");

        // ── Database Sinks Variables ─────────────────────────────────────────
        String mysqlUrl = System.getenv().getOrDefault("MYSQL_URL", "jdbc:mysql://mysql:3306/sentinel_trade?useSSL=false");
        String mysqlUser = System.getenv().getOrDefault("MYSQL_USER", "root");
        String mysqlPass = System.getenv().getOrDefault("MYSQL_PASSWORD", "sentinel123");
        String clickhouseUrl = System.getenv().getOrDefault("CLICKHOUSE_JDBC_URL", "jdbc:clickhouse://clickhouse:8123/sentinel_trade");
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "redis");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        // ── Kafka Sinks ─────────────────────────────────────────────────────
        kline1m.sinkTo(buildKafkaSink(TOPIC_KLINE_1M)).name("Sink: kline-1m (Kafka)");
        kline5m.sinkTo(buildKafkaSink(TOPIC_KLINE_5M)).name("Sink: kline-5m (Kafka)");
        kline1h.sinkTo(buildKafkaSink(TOPIC_KLINE_1H)).name("Sink: kline-1h (Kafka)");
        alerts.sinkTo(buildKafkaSink(TOPIC_ALERTS)).name("Sink: alerts (Kafka)");

        // ── Custom Rich Sinks (Redis, MySQL, ClickHouse) ────────────────────
        // Redis sinks for real-time dashboard plotting
        kline1m.addSink(new com.sentinel_trade.sink.RedisSink(redisHost, redisPort)).name("Sink: kline-1m (Redis)");
        kline5m.addSink(new com.sentinel_trade.sink.RedisSink(redisHost, redisPort)).name("Sink: kline-5m (Redis)");
        kline1h.addSink(new com.sentinel_trade.sink.RedisSink(redisHost, redisPort)).name("Sink: kline-1h (Redis)");
        
        // MySQL sinks for permanent K-line storage
        kline1m.addSink(new com.sentinel_trade.sink.MySQLSink(mysqlUrl, mysqlUser, mysqlPass)).name("Sink: kline-1m (MySQL)");
        kline5m.addSink(new com.sentinel_trade.sink.MySQLSink(mysqlUrl, mysqlUser, mysqlPass)).name("Sink: kline-5m (MySQL)");
        kline1h.addSink(new com.sentinel_trade.sink.MySQLSink(mysqlUrl, mysqlUser, mysqlPass)).name("Sink: kline-1h (MySQL)");

        // ClickHouse sink for massive raw tick storage
        dedupedStream.addSink(new com.sentinel_trade.sink.ClickHouseSink(clickhouseUrl)).name("Sink: tick-data (ClickHouse)");

        LOG.info("Sentinel-Trade pipeline built – brokers={}", KAFKA_BROKERS);
        return env;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
}

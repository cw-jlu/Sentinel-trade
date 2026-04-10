package com.sentinel_trade.aggregation;

import com.sentinel_trade.model.KLine;
import com.sentinel_trade.model.TickData;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregates a tumbling window of TickData into a KLine (OHLCV).
 *
 * - Open  = price of the first tick (by timestamp)
 * - High  = maximum price in the window
 * - Low   = minimum price in the window
 * - Close = price of the last tick (by timestamp)
 * - Volume = sum of all quantities
 *
 * Requirements: 2.2, 10.1
 */
public class KLineAggregator
        extends ProcessWindowFunction<TickData, KLine, String, TimeWindow> {

    private static final Logger LOG = LoggerFactory.getLogger(KLineAggregator.class);

    private final String interval;

    public KLineAggregator(String interval) {
        this.interval = interval;
    }

    @Override
    public void process(String symbol,
                        Context context,
                        Iterable<TickData> elements,
                        Collector<KLine> out) {

        List<TickData> ticks = new ArrayList<>();
        for (TickData t : elements) {
            ticks.add(t);
        }

        if (ticks.isEmpty()) {
            LOG.debug("Empty window for symbol={} interval={} – skipping", symbol, interval);
            return;
        }

        TimeWindow window = context.window();
        KLine kline = computeKLine(symbol, interval, window.getStart(), window.getEnd() - 1, ticks);

        LOG.info("KLine aggregated: symbol={} interval={} windowStart={} records={}",
                symbol, interval, window.getStart(), ticks.size());

        out.collect(kline);
    }

    /**
     * Pure OHLCV computation — extracted for unit testability without Flink context.
     */
    public static KLine computeKLine(String symbol, String interval,
                                     long openTime, long closeTime,
                                     List<TickData> ticks) {
        if (ticks.isEmpty()) return null;

        // Sort by timestamp to determine open/close correctly
        List<TickData> sorted = new ArrayList<>(ticks);
        sorted.sort(Comparator.comparingLong(TickData::getTimestamp));

        BigDecimal open   = sorted.get(0).getPrice();
        BigDecimal close  = sorted.get(sorted.size() - 1).getPrice();
        BigDecimal high   = sorted.stream().map(TickData::getPrice).max(BigDecimal::compareTo).orElse(open);
        BigDecimal low    = sorted.stream().map(TickData::getPrice).min(BigDecimal::compareTo).orElse(open);
        BigDecimal volume = sorted.stream().map(TickData::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new KLine(symbol, interval, openTime, closeTime,
                open, high, low, close, volume, ticks.size());
    }
}

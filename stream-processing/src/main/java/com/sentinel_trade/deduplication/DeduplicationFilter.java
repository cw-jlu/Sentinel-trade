package com.sentinel_trade.deduplication;

import com.sentinel_trade.model.TickData;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters duplicate TickData records based on trade_id.
 * Uses Flink KeyedState to track seen trade IDs per symbol.
 *
 * Requirements: 10.2
 */
public class DeduplicationFilter extends KeyedProcessFunction<String, TickData, TickData> {

    private static final Logger LOG = LoggerFactory.getLogger(DeduplicationFilter.class);

    /** Stores the last seen trade_id for each symbol key. */
    private transient ValueState<String> lastSeenTradeId;

    // We use a simple set-like approach: store a comma-separated list of recent IDs.
    // For production, a MapState<String, Boolean> would be more efficient.
    private transient ValueState<String> seenTradeIds;

    @Override
    public void open(Configuration parameters) throws Exception {
        ValueStateDescriptor<String> descriptor =
                new ValueStateDescriptor<>("seen-trade-ids", Types.STRING);
        seenTradeIds = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void processElement(TickData tick,
                               Context ctx,
                               Collector<TickData> out) throws Exception {
        String tradeId = tick.getTradeId();
        String seen = seenTradeIds.value();

        if (seen != null && containsId(seen, tradeId)) {
            LOG.debug("Duplicate trade_id={} for symbol={} – skipping",
                    tradeId, tick.getSymbol());
            return;
        }

        // Record this trade_id
        String updated = (seen == null) ? tradeId : seen + "," + tradeId;
        // Keep state bounded: only retain last 10,000 IDs
        if (updated.length() > 500_000) {
            int cutoff = updated.indexOf(',', updated.length() / 2);
            if (cutoff > 0) updated = updated.substring(cutoff + 1);
        }
        seenTradeIds.update(updated);

        out.collect(tick);
    }

    private boolean containsId(String seen, String tradeId) {
        // Fast check: look for exact match in comma-separated list
        int idx = seen.indexOf(tradeId);
        if (idx < 0) return false;
        // Verify it's a whole token (not a substring of another ID)
        boolean startOk = (idx == 0 || seen.charAt(idx - 1) == ',');
        boolean endOk   = (idx + tradeId.length() == seen.length()
                || seen.charAt(idx + tradeId.length()) == ',');
        return startOk && endOk;
    }
}

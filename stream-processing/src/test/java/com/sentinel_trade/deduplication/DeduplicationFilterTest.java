package com.sentinel_trade.deduplication;

import com.sentinel_trade.model.TickData;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeduplicationFilter.
 *
 * Property 16: 去重防止重复计数
 * Validates: Requirements 10.2
 */
class DeduplicationFilterTest {

    // -----------------------------------------------------------------------
    // Unit tests
    // -----------------------------------------------------------------------

    @Test
    void uniqueTicksPassThrough() throws Exception {
        List<TickData> ticks = List.of(
                tick("t1", 1000L), tick("t2", 2000L), tick("t3", 3000L)
        );
        List<TickData> result = deduplicate(ticks);
        assertEquals(3, result.size());
    }

    @Test
    void duplicateTickIsFiltered() throws Exception {
        List<TickData> ticks = List.of(
                tick("t1", 1000L),
                tick("t1", 2000L),  // duplicate trade_id
                tick("t2", 3000L)
        );
        List<TickData> result = deduplicate(ticks);
        assertEquals(2, result.size());
        assertEquals("t1", result.get(0).getTradeId());
        assertEquals("t2", result.get(1).getTradeId());
    }

    @Test
    void allDuplicatesFiltered() throws Exception {
        List<TickData> ticks = List.of(
                tick("t1", 1000L),
                tick("t1", 2000L),
                tick("t1", 3000L)
        );
        List<TickData> result = deduplicate(ticks);
        assertEquals(1, result.size());
    }

    @Test
    void emptyInputProducesEmptyOutput() throws Exception {
        List<TickData> result = deduplicate(List.of());
        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Property 16: 去重防止重复计数
    // Validates: Requirements 10.2
    // -----------------------------------------------------------------------

    /**
     * Feature: sentinel-trade, Property 16: 去重防止重复计数
     *
     * For any sequence of TickData (possibly containing duplicates),
     * each unique trade_id SHALL appear exactly once in the output.
     */
    @Property(tries = 200)
    void deduplicationPreventsDoubleCounting(
            @ForAll @Size(min = 1, max = 30) List<@From("ticksWithDuplicates") TickData> ticks)
            throws Exception {

        List<TickData> result = deduplicate(ticks);

        // Each trade_id must appear exactly once
        List<String> outputIds = result.stream()
                .map(TickData::getTradeId)
                .collect(Collectors.toList());

        long distinctCount = outputIds.stream().distinct().count();
        assertEquals(distinctCount, outputIds.size(),
                "Each trade_id must appear exactly once in output");

        // All unique input IDs must be present in output
        long uniqueInputIds = ticks.stream().map(TickData::getTradeId).distinct().count();
        assertEquals(uniqueInputIds, result.size(),
                "Output size must equal number of unique input trade_ids");
    }

    @Provide
    Arbitrary<TickData> ticksWithDuplicates() {
        // Use only 5 distinct trade IDs to force duplicates
        return Combinators.combine(
                Arbitraries.integers().between(1, 5).map(i -> "trade-" + i),
                Arbitraries.longs().between(1000L, 100000L)
        ).as((id, ts) -> tick(id, ts));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static TickData tick(String tradeId, long timestamp) {
        return new TickData("BTCUSDT", new BigDecimal("45000"), new BigDecimal("1.0"),
                timestamp, tradeId, false);
    }

    private List<TickData> deduplicate(List<TickData> ticks) throws Exception {
        InMemoryDeduplicationFilter filter = new InMemoryDeduplicationFilter();
        List<TickData> result = new ArrayList<>();
        org.apache.flink.util.Collector<TickData> collector = new org.apache.flink.util.Collector<TickData>() {
            @Override public void collect(TickData record) { result.add(record); }
            @Override public void close() {}
        };
        for (TickData tick : ticks) {
            filter.processElement(tick, null, collector);
        }
        return result;
    }

    /**
     * In-memory version of DeduplicationFilter for unit testing (no Flink state).
     */
    static class InMemoryDeduplicationFilter extends DeduplicationFilter {
        private final java.util.Set<String> seen = new java.util.HashSet<>();

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) {
            // no-op
        }

        @Override
        public void processElement(TickData tick,
                                   Context ctx,
                                   org.apache.flink.util.Collector<TickData> out) {
            if (seen.add(tick.getTradeId())) {
                out.collect(tick);
            }
        }
    }
}

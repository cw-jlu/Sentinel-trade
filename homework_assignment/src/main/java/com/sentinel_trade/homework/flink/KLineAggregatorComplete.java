package com.sentinel_trade.homework.flink;

import org.apache.flink.api.common.functions.AggregateFunction;

// --- DTO 实体定义 ---
class TickData {
    private String symbol;
    private double price;
    private double volume;
    private long timestamp;

    public TickData(String symbol, double price, double volume, long timestamp) {
        this.symbol = symbol; this.price = price; this.volume = volume; this.timestamp = timestamp;
    }
    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public double getVolume() { return volume; }
    public long getTimestamp() { return timestamp; }
}

class KLine {
    public String symbol;
    public double open;
    public double high;
    public double low;
    public double close;
    public double volume;
    
    public KLine(String symbol, double open, double high, double low, double close, double volume) {
        this.symbol = symbol; this.open = open; this.high = high; this.low = low; this.close = close; this.volume = volume;
    }
    
    @Override
    public String toString() {
        return String.format("KLine{symbol='%s', open=%.2f, high=%.2f, low=%.2f, close=%.2f, volume=%.2f}", 
                             symbol, open, high, low, close, volume);
    }
}

class KLineAccumulator {
    public String symbol;
    public double open = 0.0;
    public double high = Double.MIN_VALUE;
    public double low = Double.MAX_VALUE;
    public double close = 0.0;
    public double volume = 0.0;
    
    // 用于辅助决定 open 和 close 的时间戳
    public long minTimestamp = Long.MAX_VALUE;
    public long maxTimestamp = Long.MIN_VALUE;
}

// --- Flink 聚合核心逻辑 ---

/**
 * 完整的 K线聚合器（补充了 merge 方法及实体类）
 */
public class KLineAggregatorComplete implements AggregateFunction<TickData, KLineAccumulator, KLine> {

    @Override
    public KLineAccumulator createAccumulator() {
        return new KLineAccumulator();
    }

    @Override
    public KLineAccumulator add(TickData tick, KLineAccumulator acc) {
        acc.symbol = tick.getSymbol();
        
        // 更新开盘价（根据时间戳决定）
        if (tick.getTimestamp() < acc.minTimestamp) {
            acc.minTimestamp = tick.getTimestamp();
            acc.open = tick.getPrice();
        }
        
        // 更新收盘价
        if (tick.getTimestamp() > acc.maxTimestamp) {
            acc.maxTimestamp = tick.getTimestamp();
            acc.close = tick.getPrice();
        }

        // 更新最高/最低价和总成交量
        acc.high = Math.max(acc.high, tick.getPrice());
        acc.low = Math.min(acc.low, tick.getPrice());
        acc.volume += tick.getVolume();
        
        return acc;
    }

    @Override
    public KLine getResult(KLineAccumulator acc) {
        return new KLine(acc.symbol, acc.open, acc.high, acc.low, acc.close, acc.volume);
    }

    @Override
    public KLineAccumulator merge(KLineAccumulator a, KLineAccumulator b) {
        KLineAccumulator merged = new KLineAccumulator();
        merged.symbol = a.symbol != null ? a.symbol : b.symbol;
        
        // 合并最高/最低价
        merged.high = Math.max(a.high, b.high);
        merged.low = Math.min(a.low, b.low);
        // 累加总成交量
        merged.volume = a.volume + b.volume;
        
        // 处理开盘价：取时间戳最小的那个 Accumulator 的开盘价
        if (a.minTimestamp < b.minTimestamp) {
            merged.minTimestamp = a.minTimestamp;
            merged.open = a.open;
        } else {
            merged.minTimestamp = b.minTimestamp;
            merged.open = b.open;
        }
        
        // 处理收盘价：取时间戳最大的那个 Accumulator 的收盘价
        if (a.maxTimestamp > b.maxTimestamp) {
            merged.maxTimestamp = a.maxTimestamp;
            merged.close = a.close;
        } else {
            merged.maxTimestamp = b.maxTimestamp;
            merged.close = b.close;
        }
        
        return merged;
    }
}

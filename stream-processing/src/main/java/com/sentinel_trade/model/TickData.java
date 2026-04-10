package com.sentinel_trade.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Standardised tick (trade) event from the exchange.
 * Corresponds to the Avro schema defined in the ingestion module.
 *
 * Requirements: 2.1, 2.2
 */
public class TickData {

    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private long timestamp;   // milliseconds since epoch
    private String tradeId;
    private boolean isBuyerMaker;

    /** Required no-arg constructor for Flink POJO serialization. */
    public TickData() {}

    public TickData(String symbol, BigDecimal price, BigDecimal quantity,
                    long timestamp, String tradeId, boolean isBuyerMaker) {
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
        this.tradeId = tradeId;
        this.isBuyerMaker = isBuyerMaker;
    }

    /** Notional value: price × quantity */
    public BigDecimal getAmount() {
        return price.multiply(quantity);
    }

    // -----------------------------------------------------------------------
    // Getters / Setters (required for Flink POJO)
    // -----------------------------------------------------------------------

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public boolean isBuyerMaker() { return isBuyerMaker; }
    public void setBuyerMaker(boolean buyerMaker) { isBuyerMaker = buyerMaker; }

    // -----------------------------------------------------------------------
    // equals / hashCode / toString
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TickData)) return false;
        TickData that = (TickData) o;
        return timestamp == that.timestamp
                && isBuyerMaker == that.isBuyerMaker
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(price, that.price)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(tradeId, that.tradeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, price, quantity, timestamp, tradeId, isBuyerMaker);
    }

    @Override
    public String toString() {
        return "TickData{symbol='" + symbol + "', price=" + price
                + ", quantity=" + quantity + ", timestamp=" + timestamp
                + ", tradeId='" + tradeId + "', isBuyerMaker=" + isBuyerMaker + '}';
    }
}

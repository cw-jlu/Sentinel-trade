package com.sentinel_trade.model;

import java.math.BigDecimal;

/**
 * Alert DTO for WebSocket push and API responses.
 * Requirements: 3.4, 5.2
 */
public class AlertDto {

    private String alertId;
    private String alertType;
    private String symbol;
    private long timestamp;
    private String severity;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal amount;

    public AlertDto() {}

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

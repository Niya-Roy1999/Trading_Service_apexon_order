package com.example.trading.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for receiving execution events from Exchange Service
 * Matches the OrderExecutedEvent structure published to execution.v1 topic
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderExecutedEvent {
    private String orderId;
    private String counterOrderId;
    private String userId;
    private String symbol;
    private String side;           // BUY or SELL
    private String type;           // LIMIT, MARKET, etc.
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal notionalValue;
    private String status;         // PENDING, PARTIALLY_FILLED, FILLED
    private Instant executedAt;
}

package com.example.trading.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for receiving cancellation events from Exchange Service
 * Matches the OrderCancelledEvent structure published to failed.v1 topic
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCancelledEvent {
    private String orderId;
    private String userId;
    private String symbol;
    private String side;           // BUY or SELL
    private String type;           // LIMIT, MARKET, etc.
    private BigDecimal quantity;
    private String status;         // CANCELLED
    private String reason;
    private Instant cancelledAt;
}

package com.example.trading.order_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderEventPayload {
    private String orderId;
    private String userId;
    private String symbol;
    private String side;        // BUY / SELL
    private String type;        // MARKET / LIMIT / STOP_LIMIT / etc.
    private BigDecimal quantity;
    private BigDecimal price;        // optional for LIMIT or MARKET
    private BigDecimal stopPrice;    // optional for STOP orders
    private BigDecimal trailingOffset; // optional for TRAILING_STOP
    private String trailingType;      // optional for TRAILING_STOP
    private Integer displayQuantity;  // optional for ICEBERG
    private String timeInForce;       // DAY / IOC / GTC / etc.
    private String status;            // DRAFT / PENDING / etc.
}
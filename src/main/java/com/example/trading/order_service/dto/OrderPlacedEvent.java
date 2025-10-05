package com.example.trading.order_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderPlacedEvent {
    private String orderId;
    private String userId;
    private String symbol;
    private String side;
    private String type;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal stopPrice;
    private BigDecimal trailingOffset;
    private String trailingType;
    private Integer displayQuantity;
    private String timeInForce;
    private String status;
}

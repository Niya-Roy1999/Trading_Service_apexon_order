package com.example.trading.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderValidationEvent {
    private String orderId;
    private Long userId;
    private String instrumentId;
    private String instrumentSymbol;
    private String orderSide;
    private String orderType;
    private String timeInForce;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal stopPrice;
    private BigDecimal trailingOffset;
    private String trailingType;
    private Integer displayQuantity;
    private String clientOrderId;
}


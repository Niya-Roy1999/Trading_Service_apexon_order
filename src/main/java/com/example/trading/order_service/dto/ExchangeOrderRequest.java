package com.example.trading.order_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExchangeOrderRequest {
    private String orderId;
    private String userId;
    private String symbol;
    private String side;           // BUY or SELL
    private String orderType;      // LIMIT, MARKET, STOP, etc.
    private BigDecimal quantity;

    // For LIMIT and STOP_LIMIT orders
    @JsonProperty("limitPrice")
    private BigDecimal limitPrice;

    private String timeInForce;    // DAY, GTC, IOC, FOK
    private String status;         // NEW, PENDING, APPROVED, etc.

    // Optional fields for advanced orders
    private BigDecimal stopPrice;       // For STOP_MARKET and STOP_LIMIT orders
    private BigDecimal trailingOffset;  // For TRAILING_STOP orders
    private String trailingType;        // For TRAILING_STOP orders (PERCENTAGE or FIXED)
    private Integer displayQuantity;    // For ICEBERG orders

    // Metadata
    private Long timestamp;
    private String clientOrderId;

    // OCO (One-Cancels-Other) specific fields
    private String ocoGroupId;
    private String primaryOrderType;
    private BigDecimal primaryPrice;
    private BigDecimal primaryStopPrice;
    private String secondaryOrderType;
    private BigDecimal secondaryPrice;
    private BigDecimal secondaryStopPrice;
    private BigDecimal secondaryTrailAmount;
}

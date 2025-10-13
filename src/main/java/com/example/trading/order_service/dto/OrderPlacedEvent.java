package com.example.trading.order_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderPlacedEvent{
    private String orderId;
    private String userId;
    private String symbol;
    private String side;
    private String type;
    private BigDecimal quantity;

    @JsonProperty("limitPrice")
    private BigDecimal price;

    private BigDecimal stopPrice;
    private BigDecimal trailingOffset;
    private String trailingType;
    private Integer displayQuantity;
    private String timeInForce;
    private String status;

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

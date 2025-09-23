package com.example.trading.order_service.dto;

import com.example.trading.order_service.Enums.OrderSide;
import com.example.trading.order_service.Enums.OrderType;
import com.example.trading.order_service.Enums.TimeInForce;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NonNull;

import java.math.BigDecimal;

@Data
public class CreateMarketOrderRequest {

    @NotNull
    private Long userId;

    @NotBlank
    private String instrumentId;

    @NonNull
    OrderSide orderSide;

    @NotBlank
    String instrumentSymbol;

    @NonNull
    OrderType orderType;

    @NotNull
    @DecimalMin(value = "0.00000001")
    private BigDecimal quantity;

    private TimeInForce timeInForce = TimeInForce.IMMEDIATE_OR_CANCEL;

    @Size(max = 64)
    private String clientOrderId; // idempotency

    @PositiveOrZero
    BigDecimal price;

    private BigDecimal stopPrice;
    private BigDecimal trailingOffset;
    private String trailingType;
    private Integer displayQuantity;
}

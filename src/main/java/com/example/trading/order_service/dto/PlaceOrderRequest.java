package com.example.trading.order_service.dto;

import com.example.trading.order_service.Enums.OrderType;
import com.example.trading.order_service.Enums.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NonNull;

import java.math.BigDecimal;


@Data
public class PlaceOrderRequest {

    @NonNull
    Side side;
    @NotBlank
    String symbol;
    @NonNull
    OrderType type;
    @Positive
    Long quantity;
    @PositiveOrZero
    BigDecimal price;
}

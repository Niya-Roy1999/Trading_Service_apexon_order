package com.example.trading.order_service.dto.Order;

import com.example.trading.order_service.Enums.OrderSide;
import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.Enums.OrderType;
import com.example.trading.order_service.Enums.TimeInForce;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public abstract class BaseOrder {

    private String orderId;
    private String userId;
    private String symbol;
    private OrderSide side;
    private OrderType type;
    private Integer quantity;
    private TimeInForce timeInForce;
    private OrderStatus status;
}

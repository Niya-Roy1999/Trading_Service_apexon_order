package com.example.trading.order_service.dto;

import com.example.trading.order_service.Enums.OrderSide;
import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.Enums.OrderType;
import com.example.trading.order_service.Enums.TimeInForce;
import com.example.trading.order_service.entity.Executions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateMarketOrderResponse {

    private String orderId;
    private Long userId;
    private String instrumentId;
    private String instrumentSymbol;
    private OrderSide orderSide;
    private OrderType orderType;
    private OrderStatus orderStatus;
    private BigDecimal totalQuantity;
    private BigDecimal filledQuantity;
    private BigDecimal averageFillPrice;
    private BigDecimal notionalValue;
    private TimeInForce timeInForce;
    private String clientOrderId;
    private OffsetDateTime placedAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime executedAt;
    private List<Executions> items;
    private boolean isConfirmed;
}

package com.example.trading.order_service.dto;

import com.example.trading.order_service.Enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO for real-time order status updates sent to frontend via WebSocket
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusUpdate {
    private Long orderId;
    private Long userId;
    private String instrumentSymbol;
    private OrderStatus status;
    private BigDecimal totalQuantity;
    private BigDecimal filledQuantity;
    private BigDecimal avgFillPrice;
    private BigDecimal notionalValue;
    private String message;
    private OffsetDateTime updatedAt;

    // Execution details (if applicable)
    private BigDecimal lastExecutionPrice;
    private BigDecimal lastExecutionQuantity;
    private String lastExecutionId;
}

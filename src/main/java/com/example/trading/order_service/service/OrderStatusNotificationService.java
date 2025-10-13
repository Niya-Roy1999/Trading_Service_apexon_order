package com.example.trading.order_service.service;

import com.example.trading.order_service.dto.OrderStatusUpdate;
import com.example.trading.order_service.entity.Executions;
import com.example.trading.order_service.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for sending real-time order status updates to frontend via WebSocket
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderStatusNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Sends order status update to specific user
     * Frontend should subscribe to /topic/orders/{userId}
     */
    public void sendOrderUpdate(Long userId, Order order) {
        sendOrderUpdate(userId, order, null, null);
    }

    /**
     * Sends order status update with execution details
     */
    public void sendOrderUpdate(Long userId, Order order, String message, Executions lastExecution) {
        try {
            OrderStatusUpdate update = buildOrderStatusUpdate(order, message, lastExecution);

            // Send to user-specific topic: /topic/orders/{userId}
            String destination = "/topic/orders/" + userId;
            messagingTemplate.convertAndSend(destination, update);

            log.info("Sent order status update to user {}: orderId={}, status={}",
                    userId, order.getId(), order.getStatus());

        } catch (Exception e) {
            log.error("Failed to send order status update to user {}: orderId={}",
                    userId, order.getId(), e);
            // Don't throw - notification failure shouldn't break the order processing
        }
    }

    /**
     * Broadcasts order update to all subscribed clients
     * Use sparingly - prefer user-specific updates
     */
    public void broadcastOrderUpdate(Order order, String message) {
        try {
            OrderStatusUpdate update = buildOrderStatusUpdate(order, message, null);

            // Broadcast to all: /topic/orders/all
            messagingTemplate.convertAndSend("/topic/orders/all", update);

            log.debug("Broadcast order status update: orderId={}, status={}",
                    order.getId(), order.getStatus());

        } catch (Exception e) {
            log.error("Failed to broadcast order status update: orderId={}",
                    order.getId(), e);
        }
    }

    /**
     * Builds OrderStatusUpdate DTO from Order entity
     */
    private OrderStatusUpdate buildOrderStatusUpdate(Order order, String message, Executions lastExecution) {
        OrderStatusUpdate.OrderStatusUpdateBuilder builder = OrderStatusUpdate.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .instrumentSymbol(order.getInstrumentSymbol())
                .status(order.getStatus())
                .totalQuantity(order.getTotalQuantity())
                .filledQuantity(order.getFilledQuantity())
                .avgFillPrice(order.getAvgFillPrice())
                .notionalValue(order.getNotionalValue())
                .updatedAt(order.getUpdatedAt())
                .message(message);

        // Add last execution details if available
        if (lastExecution != null) {
            builder.lastExecutionPrice(lastExecution.getExecutedPrice())
                    .lastExecutionQuantity(lastExecution.getQuantity())
                    .lastExecutionId(lastExecution.getExecutionId());
        }

        return builder.build();
    }
}

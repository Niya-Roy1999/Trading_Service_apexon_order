package com.example.trading.order_service.kafka;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.OrderCancelledEvent;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.exception.OrderNotFoundException;
import com.example.trading.order_service.repository.OrderRepository;
import com.example.trading.order_service.service.OrderStatusNotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCancellationConsumer {

    private final OrderRepository orderRepo;
    private final OrderStatusNotificationService notificationService;

    /**
     * Listens to failed.v1 topic for order cancellation events from Exchange Service
     * Handles: CANCELLED status with reason
     */
    @KafkaListener(
            topics = "failed.v1",
            groupId = "cancellation-event-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeCancellationEvent(
            @Payload EventEnvelope<Map<String, Object>> envelope,
            @Header(KafkaHeaders.RECEIVED_KEY) String messageKey,
            Acknowledgment ack
    ) {
        try {
            log.info("Processing cancellation event with eventType: {}", envelope.getEventType());

            // Extract cancellation details from payload
            Map<String, Object> payload = envelope.getPayload();
            String orderId = String.valueOf(payload.get("orderId"));
            String reason = String.valueOf(payload.get("reason"));

            log.info("Processing cancellation for order: {}, reason: {}", orderId, reason);

            Order order = orderRepo.findById(Long.parseLong(orderId))
                    .orElseThrow(() -> new OrderNotFoundException(Long.parseLong(orderId)));

            // Idempotency check - if already cancelled, skip
            if (order.getStatus() == OrderStatus.CANCELLED) {
                log.warn("Order {} already cancelled, skipping", orderId);
                ack.acknowledge();
                return;
            }

            // Don't cancel if already filled
            if (order.getStatus() == OrderStatus.FILLED) {
                log.warn("Order {} is already FILLED, cannot cancel", orderId);
                ack.acknowledge();
                return;
            }

            // Update order status to CANCELLED
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(OffsetDateTime.now());

            // Parse cancelledAt timestamp if available
            if (payload.get("cancelledAt") != null) {
                try {
                    OffsetDateTime cancelledAt = OffsetDateTime.parse(String.valueOf(payload.get("cancelledAt")));
                    order.setExecutedAt(cancelledAt); // Reusing executedAt for cancellation time
                } catch (Exception e) {
                    log.warn("Could not parse cancelledAt timestamp", e);
                }
            }

            orderRepo.save(order);

            log.info("Order {} marked as CANCELLED. Reason: {}", orderId, reason);

            // TODO: Release reserved funds in wallet service
            // walletService.releaseFunds(order.getUserId(), order.getId());

            // Send real-time notification to frontend via WebSocket
            String message = String.format("Order cancelled: %s", reason);
            notificationService.sendOrderUpdate(order.getUserId(), order, message, null);

            ack.acknowledge();

        } catch (OrderNotFoundException e) {
            log.error("Order not found for cancellation event", e);
            ack.acknowledge(); // Don't retry - order doesn't exist
        } catch (Exception e) {
            log.error("Error processing cancellation event", e);
            throw e; // Retry
        }
    }
}

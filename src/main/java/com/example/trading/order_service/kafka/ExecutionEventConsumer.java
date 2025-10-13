package com.example.trading.order_service.kafka;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.entity.Executions;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExecutionEventConsumer {

    private final OrderRepository orderRepo;
    private final OrderStatusNotificationService notificationService;

    /**
     * Listens to execution.v1 topic for order execution updates from Exchange Service
     * Handles: PENDING, PARTIALLY_FILLED, FILLED statuses
     */
    @KafkaListener(
            topics = "execution.v1",
            groupId = "execution-event-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeExecutionEvent(
            @Payload EventEnvelope<Map<String, Object>> envelope,
            @Header(KafkaHeaders.RECEIVED_KEY) String messageKey,
            Acknowledgment ack
    ) {
        try {
            log.info("🔔 [KAFKA-EXECUTION] Received execution event - EventType: {}, Key: {}",
                    envelope.getEventType(), messageKey);

            // Extract execution details from payload
            Map<String, Object> payload = envelope.getPayload();
            String orderId = String.valueOf(payload.get("orderId"));
            String status = String.valueOf(payload.get("status"));
            log.info("📦 [KAFKA-EXECUTION] Processing execution - OrderID: {}, Status: {}", orderId, status);

            log.debug("🔍 [KAFKA-EXECUTION] Fetching order from database - OrderID: {}", orderId);
            Order order = orderRepo.findById(Long.parseLong(orderId))
                    .orElseThrow(() -> {
                        log.error("❌ [KAFKA-EXECUTION] Order not found - OrderID: {}", orderId);
                        return new OrderNotFoundException(Long.parseLong(orderId));
                    });

            // Idempotency check - if order is already in terminal state, skip
            if (order.getStatus() == OrderStatus.FILLED ||
                order.getStatus() == OrderStatus.CANCELLED) {
                log.warn("⚠️ [KAFKA-EXECUTION] Order already in terminal state - OrderID: {}, Status: {}, Skipping",
                        orderId, order.getStatus());
                ack.acknowledge();
                return;
            }
            log.debug("✅ [KAFKA-EXECUTION] Idempotency check passed - OrderID: {}, CurrentStatus: {}",
                    orderId, order.getStatus());

            // Extract execution data
            BigDecimal executionQuantity = parseBigDecimal(payload.get("quantity"));
            BigDecimal executionPrice = parseBigDecimal(payload.get("price"));
            BigDecimal notionalValue = parseBigDecimal(payload.get("notionalValue"));
            String counterOrderId = String.valueOf(payload.get("counterOrderId"));
            log.debug("📊 [KAFKA-EXECUTION] Execution details - OrderID: {}, Qty: {}, Price: {}, Notional: {}, CounterOrderID: {}",
                    orderId, executionQuantity, executionPrice, notionalValue, counterOrderId);

            // Parse executedAt timestamp
            OffsetDateTime executedAt = OffsetDateTime.now();
            if (payload.get("executedAt") != null) {
                try {
                    executedAt = OffsetDateTime.parse(String.valueOf(payload.get("executedAt")));
                } catch (Exception e) {
                    log.warn("⚠️ [KAFKA-EXECUTION] Could not parse executedAt timestamp, using current time - OrderID: {}", orderId);
                }
            }

            // Create execution record
            log.debug("📝 [KAFKA-EXECUTION] Creating execution record - OrderID: {}", orderId);
            Executions execution = Executions.builder()
                    .order(order)
                    .instrumentId(order.getInstrumentId())
                    .quantity(executionQuantity)
                    .executedPrice(executionPrice)
                    .notional(notionalValue)
                    .executionId(counterOrderId)
                    .executedAt(executedAt)
                    .fees(BigDecimal.ZERO) // TODO: Calculate fees
                    .build();

            order.getItems().add(execution);
            log.debug("✅ [KAFKA-EXECUTION] Execution record added - OrderID: {}, Total executions: {}",
                    orderId, order.getItems().size());

            // Update order filled quantity and average fill price
            BigDecimal previousFilled = order.getFilledQuantity() != null ?
                    order.getFilledQuantity() : BigDecimal.ZERO;
            BigDecimal newFilledQuantity = previousFilled.add(executionQuantity);
            order.setFilledQuantity(newFilledQuantity);
            log.debug("💰 [KAFKA-EXECUTION] Updated filled quantity - OrderID: {}, Previous: {}, New: {}, Total: {}",
                    orderId, previousFilled, executionQuantity, newFilledQuantity);

            // Calculate weighted average fill price
            if (order.getAvgFillPrice() == null) {
                order.setAvgFillPrice(executionPrice);
                log.debug("💵 [KAFKA-EXECUTION] Set initial average fill price - OrderID: {}, AvgPrice: {}",
                        orderId, executionPrice);
            } else {
                BigDecimal totalNotional = order.getNotionalValue() != null ?
                        order.getNotionalValue() : BigDecimal.ZERO;
                totalNotional = totalNotional.add(notionalValue);
                order.setNotionalValue(totalNotional);

                BigDecimal avgPrice = totalNotional.divide(newFilledQuantity, 8, RoundingMode.HALF_UP);
                order.setAvgFillPrice(avgPrice);
                log.debug("💵 [KAFKA-EXECUTION] Updated average fill price - OrderID: {}, AvgPrice: {}, TotalNotional: {}",
                        orderId, avgPrice, totalNotional);
            }

            // Update order status based on execution status from exchange
            OrderStatus newStatus = mapExecutionStatus(status);
            order.setStatus(newStatus);
            order.setUpdatedAt(OffsetDateTime.now());

            if (newStatus == OrderStatus.FILLED) {
                order.setExecutedAt(executedAt);
                log.info("✅ [KAFKA-EXECUTION] Order fully filled - OrderID: {}, ExecutedAt: {}", orderId, executedAt);
            }

            log.debug("💾 [KAFKA-EXECUTION] Saving order updates - OrderID: {}", orderId);
            orderRepo.save(order);

            log.info("✅ [KAFKA-EXECUTION] Order updated successfully - OrderID: {}, Status: {}, Filled: {}/{}, AvgPrice: {}",
                    orderId, newStatus, newFilledQuantity, order.getTotalQuantity(), order.getAvgFillPrice());

            // Send real-time notification to frontend via WebSocket
            String message = String.format("Order execution: %s at price %s", executionQuantity, executionPrice);
            log.debug("🔔 [KAFKA-EXECUTION] Sending WebSocket notification - OrderID: {}, UserID: {}",
                    orderId, order.getUserId());
            notificationService.sendOrderUpdate(order.getUserId(), order, message, execution);

            ack.acknowledge();

        } catch (OrderNotFoundException e) {
            log.error("❌ [KAFKA-EXECUTION] Order not found for execution event - Error: {}", e.getMessage());
            ack.acknowledge(); // Don't retry - order doesn't exist
        } catch (Exception e) {
            log.error("❌ [KAFKA-EXECUTION] Error processing execution event - Error: {}", e.getMessage(), e);
            throw e; // Retry
        }
    }

    /**
     * Maps exchange execution status to order status
     */
    private OrderStatus mapExecutionStatus(String exchangeStatus) {
        return switch (exchangeStatus) {
            case "PENDING" -> OrderStatus.PENDING;
            case "PARTIALLY_FILLED" -> OrderStatus.PARTIALLY_FILLED;
            case "FILLED" -> OrderStatus.FILLED;
            default -> {
                log.warn("Unknown execution status: {}, defaulting to PENDING", exchangeStatus);
                yield OrderStatus.PENDING;
            }
        };
    }

    /**
     * Safely parse BigDecimal from object
     */
    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("Could not parse BigDecimal from: {}", value);
            return BigDecimal.ZERO;
        }
    }
}

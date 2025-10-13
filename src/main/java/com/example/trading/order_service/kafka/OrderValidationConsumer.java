package com.example.trading.order_service.kafka;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.exception.OrderNotFoundException;
import com.example.trading.order_service.exception.ValidationException;
import com.example.trading.order_service.repository.OrderRepository;
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
import java.time.OffsetDateTime;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderValidationConsumer {

    private final OrderRepository orderRepo;
    private final OrderEventsProducer producer;
    private final com.example.trading.order_service.service.PositionService positionService;

    @KafkaListener(
            topics = "orders.validation.v1",
            groupId = "order-validation-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeOrderForValidation(
            @Payload EventEnvelope<Map<String, Object>> envelope,
            @Header(KafkaHeaders.RECEIVED_KEY) String orderId,
            Acknowledgment ack
    ) {
        try {
            log.info("🔔 [KAFKA-VALIDATION] Received validation request - OrderID: {}, EventType: {}",
                    orderId, envelope.getEventType());

            // Extract orderId from payload Map
            Map<String, Object> payload = envelope.getPayload();
            String extractedOrderId = String.valueOf(payload.get("orderId"));
            log.debug("📦 [KAFKA-VALIDATION] Extracted OrderID from payload: {}", extractedOrderId);

            // 1. Idempotency check - has this message been processed?
            log.debug("🔍 [KAFKA-VALIDATION] Fetching order from database - OrderID: {}", extractedOrderId);
            Order order = orderRepo.findById(Long.parseLong(extractedOrderId))
                    .orElseThrow(() -> {
                        log.error("❌ [KAFKA-VALIDATION] Order not found - OrderID: {}", extractedOrderId);
                        return new OrderNotFoundException(Long.parseLong(extractedOrderId));
                    });

            if (order.getStatus() != OrderStatus.PENDING_VALIDATION) {
                log.warn("⚠️ [KAFKA-VALIDATION] Order not in PENDING_VALIDATION state - OrderID: {}, CurrentStatus: {}, Skipping",
                        extractedOrderId, order.getStatus());
                ack.acknowledge();
                return;
            }
            log.debug("✅ [KAFKA-VALIDATION] Idempotency check passed - OrderID: {}, Status: {}",
                    extractedOrderId, order.getStatus());

            // 2. Validate payload (null checks, business rules, position check for SELL orders)
            log.info("🔬 [KAFKA-VALIDATION] Starting payload validation - OrderID: {}", extractedOrderId);
            validateOrderPayload(payload, order);
            log.info("✅ [KAFKA-VALIDATION] Payload validation passed - OrderID: {}", extractedOrderId);

            // 3. Update status
            log.debug("💾 [KAFKA-VALIDATION] Updating order status to PENDING_WALLET_CHECK - OrderID: {}", extractedOrderId);
            order.setStatus(OrderStatus.PENDING_WALLET_CHECK);
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepo.save(order);
            log.info("✅ [KAFKA-VALIDATION] Order status updated - OrderID: {}, NewStatus: PENDING_WALLET_CHECK", extractedOrderId);

            // 4. Publish to next topic
            log.info("📤 [KAFKA-VALIDATION] Publishing to wallet-check topic - OrderID: {}", extractedOrderId);
            producer.publish("orders.wallet-check.v1", extractedOrderId, envelope);
            log.info("✅ [KAFKA-VALIDATION] Order validated and forwarded successfully - OrderID: {}", extractedOrderId);

            ack.acknowledge();

        } catch (ValidationException e) {
            log.error("❌ [KAFKA-VALIDATION] Validation failed - OrderID: {}, Error: {}", orderId, e.getMessage());
            handleValidationFailure(orderId, e);
            ack.acknowledge(); // Don't retry validation errors
        } catch (Exception e) {
            log.error("❌ [KAFKA-VALIDATION] Unexpected error processing order - OrderID: {}, Error: {}",
                    orderId, e.getMessage(), e);
            // Don't acknowledge - message will be retried
            throw e;
        }
    }

    private void validateOrderPayload(Map<String, Object> payload, Order order) {
        if (payload == null) {
            throw new ValidationException("Order event is null");
        }

        String orderId = String.valueOf(payload.get("orderId"));
        if (orderId == null || orderId.isEmpty() || "null".equals(orderId)) {
            throw new ValidationException("Order ID is required");
        }

        String userId = String.valueOf(payload.get("userId"));
        if (userId == null || userId.isEmpty() || "null".equals(userId)) {
            throw new ValidationException("User ID is required");
        }

        String symbol = String.valueOf(payload.get("symbol"));
        if (symbol == null || symbol.isEmpty() || "null".equals(symbol)) {
            throw new ValidationException("Instrument symbol is required");
        }

        Object quantityObj = payload.get("quantity");
        if (quantityObj == null) {
            throw new ValidationException("Quantity must be greater than zero");
        }
        BigDecimal quantity = parseBigDecimal(quantityObj);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Quantity must be greater than zero");
        }

        Object priceObj = payload.get("price");
        if (priceObj != null) {
            BigDecimal price = parseBigDecimal(priceObj);
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Price cannot be negative");
            }
        }

        String side = String.valueOf(payload.get("side"));
        if (side == null || side.isEmpty() || "null".equals(side)) {
            throw new ValidationException("Order side is required");
        }

        String type = String.valueOf(payload.get("type"));
        if (type == null || type.isEmpty() || "null".equals(type)) {
            throw new ValidationException("Order type is required");
        }

        // Position validation for SELL orders
        if ("SELL".equalsIgnoreCase(side)) {
            log.info("🔍 [KAFKA-VALIDATION] Validating position for SELL order - OrderID: {}, Symbol: {}, Quantity: {}",
                    orderId, symbol, quantity);

            Long userIdLong = Long.parseLong(userId);
            boolean hasSufficientPosition = positionService.hasSufficientPosition(userIdLong, symbol, quantity);

            if (!hasSufficientPosition) {
                BigDecimal currentPosition = positionService.getPosition(userIdLong, symbol);
                String errorMsg = String.format(
                        "Insufficient position to sell. Symbol: %s, Requested: %s, Available: %s",
                        symbol, quantity, currentPosition
                );
                log.error("❌ [KAFKA-VALIDATION] Position check failed - OrderID: {}, {}", orderId, errorMsg);
                throw new ValidationException(errorMsg);
            }

            log.info("✅ [KAFKA-VALIDATION] Position check passed - OrderID: {}, Symbol: {}", orderId, symbol);
        }

        log.debug("Order {} passed all payload validations", orderId);
    }

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
            return BigDecimal.ZERO;
        }
    }

    private void handleValidationFailure(String orderId, ValidationException e) {
        try {
            log.info("🚫 [KAFKA-VALIDATION] Handling validation failure - OrderID: {}, Reason: {}", orderId, e.getMessage());
            Order order = orderRepo.findById(Long.parseLong(orderId)).orElse(null);
            if (order != null) {
                order.setStatus(OrderStatus.REJECTED);
                order.setUpdatedAt(OffsetDateTime.now());
                orderRepo.save(order);
                log.info("✅ [KAFKA-VALIDATION] Order marked as REJECTED - OrderID: {}, Reason: {}", orderId, e.getMessage());
            } else {
                log.warn("⚠️ [KAFKA-VALIDATION] Could not find order to mark as REJECTED - OrderID: {}", orderId);
            }
        } catch (Exception ex) {
            log.error("❌ [KAFKA-VALIDATION] Failed to update order status to REJECTED - OrderID: {}, Error: {}",
                    orderId, ex.getMessage(), ex);
        }
    }
}

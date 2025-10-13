package com.example.trading.order_service.kafka;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.ExchangeOrderRequest;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.exception.InsufficientFundsException;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class WalletCheckConsumer {

    private final OrderRepository orderRepo;
    private final OrderEventsProducer producer;
    private final OrderStatusNotificationService notificationService;

    @KafkaListener(
            topics = "orders.wallet-check.v1",
            groupId = "wallet-check-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeOrderForWalletCheck(
            @Payload EventEnvelope<Map<String, Object>> envelope,
            @Header(KafkaHeaders.RECEIVED_KEY) String orderId,
            Acknowledgment ack
    ) {
        try {
            log.info("🔔 [KAFKA-WALLET] Received wallet check request - OrderID: {}, EventType: {}",
                    orderId, envelope.getEventType());

            // Extract orderId from payload Map
            Map<String, Object> payload = envelope.getPayload();
            String extractedOrderId = String.valueOf(payload.get("orderId"));
            log.debug("📦 [KAFKA-WALLET] Extracted OrderID from payload: {}", extractedOrderId);

            log.debug("🔍 [KAFKA-WALLET] Fetching order from database - OrderID: {}", extractedOrderId);
            Order order = orderRepo.findById(Long.parseLong(extractedOrderId))
                    .orElseThrow(() -> {
                        log.error("❌ [KAFKA-WALLET] Order not found - OrderID: {}", extractedOrderId);
                        return new OrderNotFoundException(Long.parseLong(extractedOrderId));
                    });

            // Idempotency check
            if (order.getStatus() != OrderStatus.PENDING_WALLET_CHECK) {
                log.warn("⚠️ [KAFKA-WALLET] Order not in PENDING_WALLET_CHECK state - OrderID: {}, CurrentStatus: {}, Skipping",
                        orderId, order.getStatus());
                ack.acknowledge();
                return;
            }
            log.debug("✅ [KAFKA-WALLET] Idempotency check passed - OrderID: {}, Status: {}", extractedOrderId, order.getStatus());

            // Calculate required amount
            log.debug("💰 [KAFKA-WALLET] Calculating required amount - OrderID: {}", extractedOrderId);
            BigDecimal requiredAmount = calculateRequiredAmount(order);
            log.info("💰 [KAFKA-WALLET] Required amount calculated - OrderID: {}, Amount: {}, User: {}",
                    extractedOrderId, requiredAmount, order.getUserId());

            // Check wallet balance
            // TODO: Replace with actual wallet service call
            log.info("🔍 [KAFKA-WALLET] Checking wallet balance - OrderID: {}, UserID: {}, Required: {}",
                    extractedOrderId, order.getUserId(), requiredAmount);
            boolean hasSufficientFunds = checkWalletBalance(order.getUserId(), requiredAmount);

            if (!hasSufficientFunds) {
                log.warn("❌ [KAFKA-WALLET] Insufficient funds - OrderID: {}, UserID: {}, Required: {}",
                        orderId, order.getUserId(), requiredAmount);
                handleInsufficientFunds(order, requiredAmount);
                ack.acknowledge();
                return;
            }
            log.info("✅ [KAFKA-WALLET] Sufficient funds available - OrderID: {}, Amount: {}", extractedOrderId, requiredAmount);

            // TODO: Reserve funds in wallet service
            log.debug("🔒 [KAFKA-WALLET] Reserving funds (TODO: implement) - OrderID: {}, Amount: {}", orderId, requiredAmount);
            // reserveFunds(order.getUserId(), requiredAmount, order.getId());

            // Update order status
            log.debug("💾 [KAFKA-WALLET] Updating order status to PENDING_COMPLIANCE - OrderID: {}", extractedOrderId);
            order.setStatus(OrderStatus.PENDING_COMPLIANCE);
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepo.save(order);
            log.info("✅ [KAFKA-WALLET] Order status updated - OrderID: {}, NewStatus: PENDING_COMPLIANCE", extractedOrderId);

            // Send WebSocket notification to frontend
            log.debug("📡 [KAFKA-WALLET] Sending WebSocket notification - OrderID: {}", extractedOrderId);
            notificationService.sendOrderUpdate(order.getUserId(), order, "Wallet check passed", null);
            log.debug("✅ [KAFKA-WALLET] WebSocket notification sent - OrderID: {}", extractedOrderId);

            // Transform to exchange-compatible format
            log.debug("🔄 [KAFKA-WALLET] Transforming to exchange format - OrderID: {}", orderId);
            ExchangeOrderRequest exchangeOrder = buildExchangeOrderRequest(order);
            log.debug("✅ [KAFKA-WALLET] Exchange format created - OrderID: {}, Symbol: {}, Side: {}, Type: {}",
                    orderId, exchangeOrder.getSymbol(), exchangeOrder.getSide(), exchangeOrder.getOrderType());

            // Wrap in EventEnvelope for compliance service
            EventEnvelope<ExchangeOrderRequest> exchangeEnvelope = new EventEnvelope<>(
                    "OrderReadyForCompliance",
                    "v1",
                    UUID.randomUUID().toString(),
                    "order-service",
                    Instant.now().toString(),
                    exchangeOrder
            );

            // Publish to compliance topic with exchange format
            log.info("📤 [KAFKA-WALLET] Publishing to compliance topic - OrderID: {}", orderId);
            producer.publish("orders.compliance.v1", orderId, exchangeEnvelope);
            log.info("✅ [KAFKA-WALLET] Order wallet check passed and forwarded - OrderID: {}", orderId);

            ack.acknowledge();

        } catch (InsufficientFundsException e) {
            log.error("❌ [KAFKA-WALLET] Insufficient funds exception - OrderID: {}, Error: {}", orderId, e.getMessage());
            ack.acknowledge(); // Don't retry - permanent failure
        } catch (Exception e) {
            log.error("❌ [KAFKA-WALLET] Unexpected error in wallet check - OrderID: {}, Error: {}",
                    orderId, e.getMessage(), e);
            // Don't acknowledge - message will be retried
            throw e;
        }
    }

    private BigDecimal calculateRequiredAmount(Order order) {
        // For buy orders, need to check if user has enough funds
        if (order.getNotionalValue() != null && order.getNotionalValue().compareTo(BigDecimal.ZERO) > 0) {
            return order.getNotionalValue();
        }

        // Calculate from price and quantity
        if (order.getLimitPrice() != null && order.getTotalQuantity() != null) {
            return order.getLimitPrice().multiply(order.getTotalQuantity());
        }

        log.warn("Unable to calculate required amount for order {}. Using total quantity as fallback.", order.getId());
        return order.getTotalQuantity();
    }

    private boolean checkWalletBalance(Long userId, BigDecimal requiredAmount) {
        // TODO: Implement actual wallet service call
        // This is a placeholder - replace with actual wallet check logic
        // Example:
        // WalletBalance balance = walletService.getBalance(userId);
        // return balance.getAvailableBalance().compareTo(requiredAmount) >= 0;

        log.debug("Checking wallet balance for user {}. Required: {}", userId, requiredAmount);

        // For now, returning true to allow flow to continue
        // In production, implement actual wallet check
        return true;
    }

    private void handleInsufficientFunds(Order order, BigDecimal requiredAmount) {
        try {
            log.info("🚫 [KAFKA-WALLET] Handling insufficient funds - OrderID: {}, Required: {}", order.getId(), requiredAmount);
            order.setStatus(OrderStatus.REJECTED);
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepo.save(order);
            log.info("✅ [KAFKA-WALLET] Order marked as REJECTED due to insufficient funds - OrderID: {}", order.getId());

            // Send WebSocket notification to frontend
            String message = String.format("Insufficient funds: Required %.2f", requiredAmount);
            notificationService.sendOrderUpdate(order.getUserId(), order, message, null);
            log.debug("✅ [KAFKA-WALLET] WebSocket notification sent for rejected order - OrderID: {}", order.getId());
        } catch (Exception ex) {
            log.error("❌ [KAFKA-WALLET] Failed to update order status to REJECTED - OrderID: {}, Error: {}",
                    order.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * Transforms the Order entity into ExchangeOrderRequest format
     * This is the format expected by the Exchange Service
     */
    private ExchangeOrderRequest buildExchangeOrderRequest(Order order) {
        return ExchangeOrderRequest.builder()
                .orderId(order.getId().toString())
                .userId(order.getUserId().toString())
                .symbol(order.getInstrumentSymbol())
                .side(order.getOrderSide().name())
                .orderType(order.getType().name())
                .quantity(order.getTotalQuantity())
                .limitPrice(order.getLimitPrice())
                .timeInForce(order.getTimeInForce().name())
                .status(order.getStatus().name())
                .stopPrice(order.getStopPrice())
                .trailingOffset(order.getTrailingOffset())
                .trailingType(order.getTrailingType())
                .displayQuantity(order.getDisplayQuantity())
                .timestamp(Instant.now().toEpochMilli())
                .clientOrderId(order.getClientOrderId())
                // OCO-specific fields
                .ocoGroupId(order.getOcoGroupId())
                .primaryOrderType(order.getPrimaryOrderType())
                .primaryPrice(order.getPrimaryPrice())
                .primaryStopPrice(order.getPrimaryStopPrice())
                .secondaryOrderType(order.getSecondaryOrderType())
                .secondaryPrice(order.getSecondaryPrice())
                .secondaryStopPrice(order.getSecondaryStopPrice())
                .secondaryTrailAmount(order.getSecondaryTrailAmount())
                .build();
    }
}

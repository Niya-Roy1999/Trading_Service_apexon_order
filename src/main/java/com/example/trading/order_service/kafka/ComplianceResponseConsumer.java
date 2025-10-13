package com.example.trading.order_service.kafka;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.ExchangeOrderRequest;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ComplianceResponseConsumer {

    private final OrderRepository orderRepo;
    private final OrderEventsProducer producer;
    private final OrderStatusNotificationService notificationService;

    /**
     * Listens to orders.approved.v1 topic for compliance-approved orders
     */
    @KafkaListener(
            topics = "orders.approved.v1",
            groupId = "compliance-response-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeApprovedOrder(
            @Payload EventEnvelope<Map<String, Object>> envelope,
            @Header(KafkaHeaders.RECEIVED_KEY) String orderId,
            Acknowledgment ack
    ) {
        try {
            log.info("🔔 [KAFKA-COMPLIANCE] Received APPROVED order - OrderID: {}, EventType: {}",
                    orderId, envelope.getEventType());

            log.debug("🔍 [KAFKA-COMPLIANCE] Fetching order from database - OrderID: {}", orderId);
            Order order = orderRepo.findById(Long.parseLong(orderId))
                    .orElseThrow(() -> {
                        log.error("❌ [KAFKA-COMPLIANCE] Order not found - OrderID: {}", orderId);
                        return new OrderNotFoundException(Long.parseLong(orderId));
                    });

            // Idempotency check
            if (order.getStatus() == OrderStatus.APPROVED || order.getStatus() == OrderStatus.EXECUTED) {
                log.warn("⚠️ [KAFKA-COMPLIANCE] Order already processed - OrderID: {}, CurrentStatus: {}, Skipping",
                        orderId, order.getStatus());
                ack.acknowledge();
                return;
            }
            log.debug("✅ [KAFKA-COMPLIANCE] Idempotency check passed - OrderID: {}, CurrentStatus: {}",
                    orderId, order.getStatus());

            // Update order to APPROVED
            log.debug("💾 [KAFKA-COMPLIANCE] Updating order status to APPROVED - OrderID: {}", orderId);
            order.setStatus(OrderStatus.APPROVED);
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepo.save(order);
            log.info("✅ [KAFKA-COMPLIANCE] Order marked as APPROVED - OrderID: {}", orderId);

            // Build exchange-compatible order request
            log.debug("🔄 [KAFKA-COMPLIANCE] Building exchange order request - OrderID: {}", orderId);
            ExchangeOrderRequest exchangeOrder = buildExchangeOrderRequest(order);
            log.debug("✅ [KAFKA-COMPLIANCE] Exchange order request built - OrderID: {}, Symbol: {}, Side: {}, Type: {}",
                    orderId, exchangeOrder.getSymbol(), exchangeOrder.getSide(), exchangeOrder.getOrderType());

            // Wrap in EventEnvelope for exchange service
            EventEnvelope<ExchangeOrderRequest> exchangeEnvelope = new EventEnvelope<>(
                    "OrderApprovedForExchange",
                    "v1",
                    UUID.randomUUID().toString(),
                    "order-service",
                    Instant.now().toString(),
                    exchangeOrder
            );

            // Publish to exchange service topic (this is what exchange service will consume)
            log.info("📤 [KAFKA-COMPLIANCE] Publishing to exchange service - OrderID: {}", orderId);
            producer.publish("orders.exchange.v1", orderId, exchangeEnvelope);
            log.info("✅ [KAFKA-COMPLIANCE] Order published to exchange service - OrderID: {}", orderId);

            // Send real-time notification to frontend via WebSocket
            log.debug("🔔 [KAFKA-COMPLIANCE] Sending WebSocket notification - OrderID: {}, UserID: {}",
                    orderId, order.getUserId());
            notificationService.sendOrderUpdate(order.getUserId(), order, "Order approved by compliance", null);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("❌ [KAFKA-COMPLIANCE] Error processing approved order - OrderID: {}, Error: {}",
                    orderId, e.getMessage(), e);
            throw e; // Retry
        }
    }

    /**
     * Listens to orders.rejected.v1 topic for compliance-rejected orders
     */
    @KafkaListener(
            topics = "orders.rejected.v1",
            groupId = "compliance-response-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeRejectedOrder(
            @Payload EventEnvelope<Map<String, Object>> envelope,
            @Header(KafkaHeaders.RECEIVED_KEY) String orderId,
            Acknowledgment ack
    ) {
        try {
            log.info("🔔 [KAFKA-COMPLIANCE] Received REJECTED order - OrderID: {}, EventType: {}",
                    orderId, envelope.getEventType());

            log.debug("🔍 [KAFKA-COMPLIANCE] Fetching order from database - OrderID: {}", orderId);
            Order order = orderRepo.findById(Long.parseLong(orderId))
                    .orElseThrow(() -> {
                        log.error("❌ [KAFKA-COMPLIANCE] Order not found - OrderID: {}", orderId);
                        return new OrderNotFoundException(Long.parseLong(orderId));
                    });

            // Idempotency check
            if (order.getStatus() == OrderStatus.REJECTED) {
                log.warn("⚠️ [KAFKA-COMPLIANCE] Order already marked as REJECTED - OrderID: {}, Skipping", orderId);
                ack.acknowledge();
                return;
            }
            log.debug("✅ [KAFKA-COMPLIANCE] Idempotency check passed - OrderID: {}, CurrentStatus: {}",
                    orderId, order.getStatus());

            // Extract rejection reason if available
            String rejectionReason = "Compliance check failed";
            if (envelope.getPayload() != null && envelope.getPayload().containsKey("reason")) {
                rejectionReason = String.valueOf(envelope.getPayload().get("reason"));
            }
            log.info("📋 [KAFKA-COMPLIANCE] Rejection reason - OrderID: {}, Reason: {}", orderId, rejectionReason);

            // Update order to REJECTED
            log.debug("💾 [KAFKA-COMPLIANCE] Updating order status to REJECTED - OrderID: {}", orderId);
            order.setStatus(OrderStatus.REJECTED);
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepo.save(order);
            log.info("✅ [KAFKA-COMPLIANCE] Order marked as REJECTED - OrderID: {}, Reason: {}", orderId, rejectionReason);

            // TODO: Release reserved funds in wallet service
            log.debug("🔓 [KAFKA-COMPLIANCE] Releasing funds (TODO: implement) - OrderID: {}, UserID: {}",
                    orderId, order.getUserId());
            // walletService.releaseFunds(order.getUserId(), order.getId());

            // Send real-time notification to frontend via WebSocket
            String message = String.format("Order rejected by compliance: %s", rejectionReason);
            log.debug("🔔 [KAFKA-COMPLIANCE] Sending WebSocket notification - OrderID: {}, UserID: {}, Message: {}",
                    orderId, order.getUserId(), message);
            notificationService.sendOrderUpdate(order.getUserId(), order, message, null);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("❌ [KAFKA-COMPLIANCE] Error processing rejected order - OrderID: {}, Error: {}",
                    orderId, e.getMessage(), e);
            throw e; // Retry
        }
    }

    /**
     * This consumer listens to responses from the compliance service
     * The compliance service should publish to either orders.approved.v1 or orders.rejected.v1
     * based on their compliance rules check
     */

    /**
     * Transforms the Order entity into ExchangeOrderRequest format
     * This is the format expected by the Exchange Service for order matching
     *
     * Only sets fields relevant to each order type (null fields are excluded via @JsonInclude)
     *
     * Field mapping by order type:
     * - MARKET: quantity only
     * - LIMIT: limitPrice
     * - STOP_MARKET: stopPrice
     * - STOP_LIMIT: stopPrice + limitPrice
     * - TRAILING_STOP: trailingOffset + trailingType
     * - ICEBERG: limitPrice + displayQuantity
     */
    private ExchangeOrderRequest buildExchangeOrderRequest(Order order) {
        ExchangeOrderRequest.ExchangeOrderRequestBuilder builder = ExchangeOrderRequest.builder()
                .orderId(order.getId().toString())
                .userId(order.getUserId().toString())
                .symbol(order.getInstrumentSymbol())
                .side(order.getOrderSide().name())
                .orderType(order.getType().name())
                .quantity(order.getTotalQuantity())
                .timeInForce(order.getTimeInForce().name())
                .status(order.getStatus().name())
                .timestamp(Instant.now().toEpochMilli())
                .clientOrderId(order.getClientOrderId());

        // Set order-type-specific fields
        switch (order.getType()) {
            case LIMIT:
                builder.limitPrice(order.getLimitPrice());
                break;

            case STOP_MARKET:
                builder.stopPrice(order.getStopPrice());
                break;

            case STOP_LIMIT:
                builder.stopPrice(order.getStopPrice())
                       .limitPrice(order.getLimitPrice());
                break;

            case TRAILING_STOP:
                builder.trailingOffset(order.getTrailingOffset())
                       .trailingType(order.getTrailingType());
                break;

            case ICEBERG:
                builder.limitPrice(order.getLimitPrice())
                       .displayQuantity(order.getDisplayQuantity());
                break;

            case MARKET:
                // No additional fields needed for market orders
                break;

            case ONE_CANCELS_OTHER:
                builder.ocoGroupId(order.getOcoGroupId())
                       .primaryOrderType(order.getPrimaryOrderType())
                       .primaryPrice(order.getPrimaryPrice())
                       .primaryStopPrice(order.getPrimaryStopPrice())
                       .secondaryOrderType(order.getSecondaryOrderType())
                       .secondaryPrice(order.getSecondaryPrice())
                       .secondaryStopPrice(order.getSecondaryStopPrice())
                       .secondaryTrailAmount(order.getSecondaryTrailAmount());
                break;

            default:
                log.warn("⚠️ Unknown order type: {}, setting all available fields", order.getType());
                builder.limitPrice(order.getLimitPrice())
                       .stopPrice(order.getStopPrice())
                       .trailingOffset(order.getTrailingOffset())
                       .trailingType(order.getTrailingType())
                       .displayQuantity(order.getDisplayQuantity());
        }

        return builder.build();
    }
}

package com.example.trading.order_service.service;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.Enums.TimeInForce;
import com.example.trading.order_service.dto.CreateMarketOrderRequest;
import com.example.trading.order_service.dto.CreateMarketOrderResponse;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.OrderPlacedEvent;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.exception.DuplicateOrderException;
import com.example.trading.order_service.exception.OrderNotFoundException;
import com.example.trading.order_service.kafka.OrderEventsProducer;
import com.example.trading.order_service.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepo;
    private final OrderEventsProducer producer;

    @Transactional
    public CreateMarketOrderResponse createMarketOrder(CreateMarketOrderRequest req) {
        log.info("🏭 [SERVICE] Creating new order - User: {}, Symbol: {}, Side: {}, Type: {}, Qty: {}, Price: {}",
                req.getUserId(), req.getInstrumentSymbol(), req.getOrderSide(), req.getOrderType(),
                req.getQuantity(), req.getPrice());

        // 1. Idempotency check: reject duplicate client_order_id for the same user
        if (req.getClientOrderId() != null &&
                orderRepo.findByUserIdAndClientOrderId(req.getUserId(), req.getClientOrderId()).isPresent()) {
            log.error("❌ [SERVICE] Duplicate order detected - ClientOrderId: {}, User: {}",
                    req.getClientOrderId(), req.getUserId());
            throw new DuplicateOrderException(req.getClientOrderId());
        }
        log.debug("✅ [SERVICE] Idempotency check passed - ClientOrderId: {}", req.getClientOrderId());
        // 2. Build the Order entity
        Order order = Order.builder()
                .userId(req.getUserId())
                .instrumentId(req.getInstrumentId())
                .instrumentSymbol(req.getInstrumentSymbol())
                .orderSide(req.getOrderSide())
                .type(req.getOrderType())
                .status(OrderStatus.NEW)
                .limitPrice(req.getPrice())
                .stopPrice(req.getStopPrice())
                .trailingOffset(req.getTrailingOffset())
                .trailingType(req.getTrailingType())
                .displayQuantity(req.getDisplayQuantity())
                .totalQuantity(req.getQuantity())
                .filledQuantity(BigDecimal.ZERO)
                .timeInForce(req.getTimeInForce() == null ? TimeInForce.IMMEDIATE_OR_CANCEL : req.getTimeInForce())
                .clientOrderId(req.getClientOrderId())
                .placedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .notionalValue(req.getPrice() != null ? req.getPrice().multiply(req.getQuantity()) : BigDecimal.ZERO)
                // OCO-specific fields
                .ocoGroupId(req.getOcoGroupId())
                .primaryOrderType(req.getPrimaryOrderType())
                .primaryPrice(req.getPrimaryPrice())
                .primaryStopPrice(req.getPrimaryStopPrice())
                .secondaryOrderType(req.getSecondaryOrderType())
                .secondaryPrice(req.getSecondaryPrice())
                .secondaryStopPrice(req.getSecondaryStopPrice())
                .secondaryTrailAmount(req.getSecondaryTrailAmount())
                .build();

        // 3. Save the order to the database (ID will be generated here)
        Order saved = orderRepo.save(order);
        log.info("💾 [SERVICE] Order saved to database - OrderID: {}, Status: {}, NotionalValue: {}",
                saved.getId(), saved.getStatus(), saved.getNotionalValue());

        // 4. Map entity -> DTO
        log.debug("🔄 [SERVICE] Mapping order entity to response DTO - OrderID: {}", saved.getId());
        return  CreateMarketOrderResponse.builder()
                .orderId(saved.getId().toString())
                .userId(saved.getUserId())
                .instrumentId(saved.getInstrumentId())
                .instrumentSymbol(saved.getInstrumentSymbol())
                .orderSide(saved.getOrderSide())
                .orderType(saved.getType())
                .orderStatus(saved.getStatus())
                .totalQuantity(saved.getTotalQuantity())
                .filledQuantity(saved.getFilledQuantity())
                .averageFillPrice(saved.getAvgFillPrice())
                .notionalValue(saved.getNotionalValue())
                .timeInForce(saved.getTimeInForce())
                .clientOrderId(saved.getClientOrderId())
                .placedAt(saved.getPlacedAt())
                .updatedAt(saved.getUpdatedAt())
                .executedAt(saved.getExecutedAt()) // null initially
                .items(Collections.emptyList())    // empty list until executions happen
                .build();
    }

    @Transactional
    public CreateMarketOrderResponse reviewAndConfirmOrder(Long id) {
        log.info("🔍 [SERVICE] Reviewing and confirming order - OrderID: {}", id);

        Order order = orderRepo.findById(id)
                .orElseThrow(() -> {
                    log.error("❌ [SERVICE] Order not found for review - OrderID: {}", id);
                    return new OrderNotFoundException(id);
                });

        log.info("📋 [SERVICE] Order found - OrderID: {}, CurrentStatus: {}, Symbol: {}, Qty: {}",
                id, order.getStatus(), order.getInstrumentSymbol(), order.getTotalQuantity());

        // Update status if order is NEW - start the wallet check pipeline
        if (order.getStatus() == OrderStatus.NEW) {
            log.info("🚀 [SERVICE] Order is NEW - Starting wallet check pipeline - OrderID: {}", id);
            order.setStatus(OrderStatus.PENDING_WALLET_CHECK);
            order.setUpdatedAt(OffsetDateTime.now());
            order.setConfirmed(true);
            order = orderRepo.save(order); // persist changes
            log.info("💾 [SERVICE] Order status updated - OrderID: {}, NewStatus: {}, Confirmed: true", id, order.getStatus());
        } else {
            log.warn("⚠️ [SERVICE] Order not in NEW status - OrderID: {}, CurrentStatus: {}, Skipping status update",
                    id, order.getStatus());
        }

        // Publish to wallet-check topic to start the pipeline
        log.debug("📦 [SERVICE] Building event payload - OrderID: {}", order.getId());
        OrderPlacedEvent payload = buildEventPayload(order);
        EventEnvelope<OrderPlacedEvent> envelope = new EventEnvelope<>(
                "OrderStatusChanged",
                "v1",
                UUID.randomUUID().toString(),
                "order-service",
                Instant.now().toString(),
                payload
        );

        // Start the wallet check pipeline (first step)
        log.info("📤 [SERVICE] Publishing to Kafka - Topic: orders.wallet-check.v1, OrderID: {}", order.getId());
        producer.publish("orders.wallet-check.v1", order.getId().toString(), envelope);
        log.info("✅ [SERVICE] Order {} submitted for wallet check pipeline - Status: {}",
                order.getId(), order.getStatus());

        // Map entity -> DTO
        return CreateMarketOrderResponse.builder()
                .orderId(order.getId().toString())
                .userId(order.getUserId())
                .instrumentId(order.getInstrumentId())
                .instrumentSymbol(order.getInstrumentSymbol())
                .orderSide(order.getOrderSide())
                .orderType(order.getType())
                .orderStatus(order.getStatus())
                .totalQuantity(order.getTotalQuantity())
                .filledQuantity(order.getFilledQuantity())
                .averageFillPrice(order.getAvgFillPrice())
                .notionalValue(order.getNotionalValue())
                .timeInForce(order.getTimeInForce())
                .clientOrderId(order.getClientOrderId())
                .placedAt(order.getPlacedAt())
                .updatedAt(order.getUpdatedAt())
                .executedAt(order.getExecutedAt())
                .items(Collections.emptyList()) // until executions happen
                .isConfirmed(order.isConfirmed())
                .build();
    }


    private OrderPlacedEvent buildEventPayload(Order order) {
        OrderPlacedEvent payload = new OrderPlacedEvent();
        payload.setOrderId(order.getId().toString());
        payload.setUserId(order.getUserId().toString());
        payload.setSymbol(order.getInstrumentSymbol());
        payload.setSide(order.getOrderSide().name());
        payload.setType(order.getType().name());
        payload.setPrice(order.getLimitPrice());
        payload.setStopPrice(order.getStopPrice());
        payload.setTrailingOffset(order.getTrailingOffset());
        payload.setTrailingType(order.getTrailingType());
        payload.setDisplayQuantity(order.getDisplayQuantity());
        payload.setQuantity(order.getTotalQuantity());
        payload.setTimeInForce(order.getTimeInForce().name());
        payload.setStatus(order.getStatus().name());

        // Include OCO-specific fields if present
        payload.setOcoGroupId(order.getOcoGroupId());
        payload.setPrimaryOrderType(order.getPrimaryOrderType());
        payload.setPrimaryPrice(order.getPrimaryPrice());
        payload.setPrimaryStopPrice(order.getPrimaryStopPrice());
        payload.setSecondaryOrderType(order.getSecondaryOrderType());
        payload.setSecondaryPrice(order.getSecondaryPrice());
        payload.setSecondaryStopPrice(order.getSecondaryStopPrice());
        payload.setSecondaryTrailAmount(order.getSecondaryTrailAmount());

        return payload;
    }
}

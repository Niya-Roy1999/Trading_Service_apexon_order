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
        // 1. Idempotency check: reject duplicate client_order_id for the same user
        if (req.getClientOrderId() != null &&
                orderRepo.findByUserIdAndClientOrderId(req.getUserId(), req.getClientOrderId()).isPresent()) {
            log.error("There is a conflict and this can't be done");
            throw new DuplicateOrderException(req.getClientOrderId());
        }
        // 2. Build the Order entity
        Order order = Order.builder()
                .userId(req.getUserId())
                .instrumentId(req.getInstrumentId())
                .instrumentSymbol(req.getInstrumentSymbol())
                .orderSide(req.getOrderSide())
                .type(req.getOrderType())
                .status(OrderStatus.NEW)
                .limitPrice(req.getPrice())
                .totalQuantity(req.getQuantity())
                .filledQuantity(BigDecimal.ZERO)
                .timeInForce(req.getTimeInForce() == null ? TimeInForce.IMMEDIATE_OR_CANCEL : req.getTimeInForce())
                .clientOrderId(req.getClientOrderId())
                .placedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .notionalValue(req.getPrice() != null ? req.getPrice().multiply(req.getQuantity()) : BigDecimal.ZERO)
                .build();

        // 3. Save the order to the database (ID will be generated here)
        Order saved = orderRepo.save(order);

        // 4. Map entity -> DTO
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
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        // Update status if order is NEW
        if (order.getStatus() == OrderStatus.NEW) {
            order.setStatus(OrderStatus.PENDING);
            order.setUpdatedAt(OffsetDateTime.now());
            order.setIsConfirmed(true);
            order = orderRepo.save(order); // persist changes
        }

        // Publish single Kafka event
        OrderPlacedEvent payload = buildEventPayload(order);
        EventEnvelope<OrderPlacedEvent> envelope = new EventEnvelope<>(
                "OrderStatusChanged",
                "v1",
                UUID.randomUUID().toString(),
                "order-service",
                Instant.now().toString(),
                payload
        );
        producer.publish("orders.v1", order.getId().toString(), envelope);

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
                .isConfirmed(order.getIsConfirmed())
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
        payload.setQuantity(order.getTotalQuantity());
        payload.setTimeInForce(order.getTimeInForce().name());
        payload.setStatus(order.getStatus().name());
        return payload;
    }
}

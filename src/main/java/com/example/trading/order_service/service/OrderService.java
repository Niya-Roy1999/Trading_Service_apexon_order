package com.example.trading.order_service.service;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.OrderPlacedEvent;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.kafka.OrderEventsProducer;
import com.example.trading.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepo;
    private final OrderEventsProducer producer;

    public Optional<Order> reviewAndConfirmOrder(Long id) {
        return orderRepo.findById(id).map(order -> {

            // Update status if order is NEW
            if (order.getStatus() == OrderStatus.NEW) {
                order.setStatus(OrderStatus.PENDING);
                order.setUpdatedAt(OffsetDateTime.now());
                order = orderRepo.save(order);
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

            return order;
        });
    }

    private OrderPlacedEvent buildEventPayload(Order order) {
        OrderPlacedEvent payload = new OrderPlacedEvent();
        payload.setOrderId(order.getId().toString());
        payload.setUserId(order.getUserId().toString());
        payload.setSymbol(order.getInstrumentSymbol());
        payload.setSide(order.getOrderSide().name());
        payload.setType(order.getType().name());
        payload.setQuantity(order.getTotalQuantity());
        payload.setTimeInForce(order.getTimeInForce().name());
        payload.setStatus(order.getStatus().name());
        return payload;
    }
}

package com.example.trading.order_service.controller;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.Enums.OrderType;
import com.example.trading.order_service.Enums.TimeInForce;
import com.example.trading.order_service.dto.CreateMarketOrderRequest;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.OrderPlacedEvent;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.kafka.OrderEventsProducer;
import com.example.trading.order_service.repository.OrderRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class OrdersController {

    private  final OrderRepository orderRepo;
    private  final OrderEventsProducer producer;

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<Order> createMarketOrder(@Valid @RequestBody CreateMarketOrderRequest req) {
        // 1. Idempotency check: reject duplicate client_order_id for the same user
        if (req.getClientOrderId() != null &&
                orderRepo.findByUserIdAndClientOrderId(req.getUserId(), req.getClientOrderId()).isPresent()) {
            return ResponseEntity.status(409).build();
        }

        // 2. Build the Order entity
        Order order = Order.builder()
                .userId(req.getUserId())
                .instrumentId(req.getInstrumentId())
                .instrumentSymbol(req.getInstrumentSymbol())
                .orderSide(req.getOrderSide())
                .type(OrderType.MARKET)
                .status(OrderStatus.PENDING)
                .totalQuantity(req.getQuantity())
                .filledQuantity(BigDecimal.ZERO)
                .timeInForce(req.getTimeInForce() == null ? TimeInForce.IMMEDIATE_OR_CANCEL : req.getTimeInForce())
                .clientOrderId(req.getClientOrderId())
                .placedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .notionalValue(req.getPrice().multiply(req.getQuantity()))
                .build();

        // 3. Save the order to the database (ID will be generated here)
        Order saved = orderRepo.save(order);

        var payload = new OrderPlacedEvent(
                saved.getId().toString(),
                saved.getUserId().toString(),
                saved.getInstrumentSymbol(),
                saved.getOrderSide().name(),
                saved.getType().name(),
                saved.getTotalQuantity(),
                saved.getNotionalValue().toPlainString(),
                saved.getTimeInForce() == null ? TimeInForce.IMMEDIATE_OR_CANCEL.name() : saved.getTimeInForce().name()
        );

        var envelope =  new EventEnvelope<>(
                "OrderPlaced",
                "v1",
                UUID.randomUUID().toString(),
                "order-service",
                Instant.now().toString(),
                payload
        );

        // 5. Publish the event to Kafka
        producer.publish("orders.v1", saved.getId().toString(), envelope);
        // 6. Return the saved order as response
        return ResponseEntity.ok(saved);
    }


    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId) {
        return orderRepo.findById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{userId}/orders")
    public List<Order> listOrdersForUser(@PathVariable Long userId,
                                         @RequestParam(value = "instrumentId", required = false) String instrumentId) {
        if (instrumentId != null) {
            return orderRepo.findByUserIdAndInstrumentIdOrderByPlacedAtDesc(userId, instrumentId);
        }
        return orderRepo.findByUserIdOrderByPlacedAtDesc(userId);
    }




}

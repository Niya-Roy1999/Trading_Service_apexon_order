package com.example.trading.order_service.controller;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.dto.PlaceOrderRequest;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.kafka.OrderEventsProducer;
import com.example.trading.order_service.repository.OrderRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrdersController {

    private  final OrderRepository repo;
    private  final OrderEventsProducer producer;

    @PostMapping
    public ResponseEntity<?> place(@Valid @RequestBody PlaceOrderRequest req)
    {
        var order=repo.save(Order.builder()
                .side(req.getSide())
                .symbol(req.getSymbol())
                .type(req.getType())
                .quantity(req.getQuantity())
                .price(req.getPrice())
                .status(OrderStatus.NEW).createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        var evt= Map.of("eventType","OrderPlaced",
                "orderId",order.getId().toString(),
                "clientId","T-123",
                "symbol",order.getSymbol(),
                "side",order.getSide().name(),
                "type",order.getType().name(),
                "quantity",order.getQuantity(),
                "price",order.getPrice(),
                "timestamp", Instant.now().toString()
                );

        producer.publish("orders.v1",order.getId().toString(),evt,
                Map.of("correlationId", UUID.randomUUID().toString(),
                        "schemaVersion","v1","producer","order-service"));

        return ResponseEntity.accepted().body(Map.of("orderId",order.getId()));
    }
}

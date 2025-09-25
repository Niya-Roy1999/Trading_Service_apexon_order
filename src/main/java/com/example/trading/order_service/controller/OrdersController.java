package com.example.trading.order_service.controller;

import com.example.trading.order_service.dto.*;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.kafka.OrderEventsProducer;
import com.example.trading.order_service.repository.OrderRepository;
import com.example.trading.order_service.service.OrderService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class OrdersController {

    private  final OrderRepository orderRepo;
    private  final OrderEventsProducer producer;
    private final OrderService orderService;

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<CreateMarketOrderResponse> createMarketOrder(@Valid @RequestBody CreateMarketOrderRequest req) {
        CreateMarketOrderResponse response = orderService.createMarketOrder(req);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/review-confirm")
    public ResponseEntity<CreateMarketOrderResponse> reviewAndConfirmOrder(@PathVariable Long id) {
        return orderService.reviewAndConfirmOrder(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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

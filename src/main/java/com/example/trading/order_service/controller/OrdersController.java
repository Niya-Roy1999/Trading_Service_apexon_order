package com.example.trading.order_service.controller;

import com.example.trading.order_service.dto.CreateMarketOrderRequest;
import com.example.trading.order_service.dto.CreateMarketOrderResponse;
import com.example.trading.order_service.dto.pnl.PnlResult;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.repository.OrderRepository;
import com.example.trading.order_service.service.OrderService;
import com.example.trading.order_service.service.ProfitAndLossService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class OrdersController {

    private final OrderRepository orderRepo;
    private final OrderService orderService;
    private final ProfitAndLossService pnlService;

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<CreateMarketOrderResponse> createMarketOrder(@Valid @RequestBody CreateMarketOrderRequest req) {
        log.info("📥 [API] Received CREATE ORDER request - User: {}, Symbol: {}, Side: {}, Type: {}, Quantity: {}, Price: {}",
                req.getUserId(), req.getInstrumentSymbol(), req.getOrderSide(), req.getOrderType(),
                req.getQuantity(), req.getPrice());

        try {
            CreateMarketOrderResponse response = orderService.createMarketOrder(req);
            log.info("✅ [API] Order created successfully - OrderID: {}, Status: {}, ClientOrderId: {}",
                    response.getOrderId(), response.getOrderStatus(), req.getClientOrderId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ [API] Failed to create order - User: {}, Symbol: {}, Error: {}",
                    req.getUserId(), req.getInstrumentSymbol(), e.getMessage(), e);
            throw e;
        }
    }

    @PutMapping("/{id}/review-confirm")
    public ResponseEntity<CreateMarketOrderResponse> reviewAndConfirmOrder(@PathVariable Long id) {
        log.info("🔍 [API] Received REVIEW & CONFIRM request - OrderID: {}", id);

        try {
            CreateMarketOrderResponse response = orderService.reviewAndConfirmOrder(id);
            log.info("✅ [API] Order confirmed and pipeline started - OrderID: {}, Status: {}",
                    response.getOrderId(), response.getOrderStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ [API] Failed to confirm order - OrderID: {}, Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId) {
        log.debug("🔎 [API] GET order request - OrderID: {}", orderId);

        return orderRepo.findById(orderId)
                .map(order -> {
                    log.debug("✅ [API] Order found - OrderID: {}, Status: {}", orderId, order.getStatus());
                    return ResponseEntity.ok(order);
                })
                .orElseGet(() -> {
                    log.warn("⚠️ [API] Order not found - OrderID: {}", orderId);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping("/users/{userId}/orders")
    public List<Order> listOrdersForUser(@PathVariable Long userId,
                                         @RequestParam(value = "instrumentId", required = false) String instrumentId) {
        if (instrumentId != null) {
            log.debug("🔎 [API] GET user orders - UserID: {}, Instrument: {}", userId, instrumentId);
            List<Order> orders = orderRepo.findByUserIdAndInstrumentIdOrderByPlacedAtDesc(userId, instrumentId);
            log.debug("✅ [API] Found {} orders for user {} and instrument {}", orders.size(), userId, instrumentId);
            return orders;
        }
        log.debug("🔎 [API] GET all user orders - UserID: {}", userId);
        List<Order> orders = orderRepo.findByUserIdOrderByPlacedAtDesc(userId);
        log.debug("✅ [API] Found {} total orders for user {}", orders.size(), userId);
        return orders;
    }

    @PostMapping("/pnl/calculate/{userId}")
    public ResponseEntity<PnlResult> calculatePnlForUser(
            @PathVariable Long userId,
            @RequestBody Map<String, BigDecimal> marketPrices) {

        log.info("📊 [API] Calculate P&L request - UserID: {}, Market prices for {} symbols",
                userId, marketPrices.size());

        try {
            PnlResult pnlResult = pnlService.calculatePnlForUser(userId, marketPrices);
            log.info("✅ [API] P&L calculated - UserID: {}, Total P&L: {}, Realized: {}, Unrealized: {}",
                    userId, pnlResult.getTotalNet(), pnlResult.getTotalRealized(), pnlResult.getTotalUnrealized());
            return ResponseEntity.ok(pnlResult);
        } catch (Exception e) {
            log.error("❌ [API] P&L calculation failed - UserID: {}, Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}

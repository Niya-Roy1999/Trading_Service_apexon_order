package com.example.trading.order_service.service;

import com.example.trading.order_service.entity.Executions;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to calculate and track user positions (holdings) for each instrument.
 * A position represents the net quantity of shares owned after accounting for all buys and sells.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PositionService {

    private final OrderRepository orderRepository;

    /**
     * Calculate the current position (net quantity) for a specific instrument for a user.
     *
     * @param userId The user ID
     * @param instrumentSymbol The instrument symbol (e.g., "AAPL")
     * @return The net quantity owned (positive = long position, zero = no position, negative = short position)
     */
    @Transactional(readOnly = true)
    public BigDecimal getPosition(Long userId, String instrumentSymbol) {
        log.debug("Calculating position for user {} and instrument {}", userId, instrumentSymbol);

        // Fetch all orders for this user and instrument
        List<Order> orders = orderRepository.findByUserIdAndInstrumentSymbol(userId, instrumentSymbol);

        BigDecimal netPosition = BigDecimal.ZERO;

        for (Order order : orders) {
            // Only count executed quantities from filled/partially filled orders
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                for (Executions execution : order.getItems()) {
                    if (execution.getQuantity() != null) {
                        BigDecimal quantity = execution.getQuantity();

                        // Add for BUY, subtract for SELL
                        if ("BUY".equalsIgnoreCase(order.getOrderSide().name())) {
                            netPosition = netPosition.add(quantity);
                        } else if ("SELL".equalsIgnoreCase(order.getOrderSide().name())) {
                            netPosition = netPosition.subtract(quantity);
                        }
                    }
                }
            }
        }

        log.debug("User {} has position {} for instrument {}", userId, netPosition, instrumentSymbol);
        return netPosition;
    }

    /**
     * Calculate all positions for a user across all instruments.
     *
     * @param userId The user ID
     * @return Map of instrument symbol to net quantity owned
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getAllPositions(Long userId) {
        log.debug("Calculating all positions for user {}", userId);

        List<Order> allOrders = orderRepository.findByUserIdOrderByPlacedAtDesc(userId);
        Map<String, BigDecimal> positions = new HashMap<>();

        for (Order order : allOrders) {
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                for (Executions execution : order.getItems()) {
                    if (execution.getQuantity() != null && execution.getInstrumentId() != null) {
                        String symbol = execution.getInstrumentId();
                        BigDecimal quantity = execution.getQuantity();

                        BigDecimal currentPosition = positions.getOrDefault(symbol, BigDecimal.ZERO);

                        if ("BUY".equalsIgnoreCase(order.getOrderSide().name())) {
                            positions.put(symbol, currentPosition.add(quantity));
                        } else if ("SELL".equalsIgnoreCase(order.getOrderSide().name())) {
                            positions.put(symbol, currentPosition.subtract(quantity));
                        }
                    }
                }
            }
        }

        log.debug("User {} has {} positions", userId, positions.size());
        return positions;
    }

    /**
     * Check if a user has sufficient position to sell a given quantity of an instrument.
     *
     * @param userId The user ID
     * @param instrumentSymbol The instrument symbol
     * @param quantityToSell The quantity the user wants to sell
     * @return true if user has sufficient position, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasSufficientPosition(Long userId, String instrumentSymbol, BigDecimal quantityToSell) {
        BigDecimal currentPosition = getPosition(userId, instrumentSymbol);
        boolean sufficient = currentPosition.compareTo(quantityToSell) >= 0;

        log.debug("Position check for user {}, instrument {}: current={}, requested={}, sufficient={}",
                userId, instrumentSymbol, currentPosition, quantityToSell, sufficient);

        return sufficient;
    }
}

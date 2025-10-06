package com.example.trading.order_service.service;

import com.example.trading.order_service.dto.pnl.Lot;
import com.example.trading.order_service.dto.pnl.PnlResult;
import com.example.trading.order_service.dto.pnl.SymbolPnl;
import com.example.trading.order_service.entity.Executions;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.repository.OrderRepository;
import com.example.trading.order_service.utility.SellMatcher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Service class responsible for calculating Profit and Loss (PnL) for users.
 * Processes orders and executions to compute realized and unrealized PnL per instrument symbol
 * using FIFO lot matching logic implemented by SellMatcher.
 */
@Service
@RequiredArgsConstructor
public class ProfitAndLossService {
    private static final Logger logger = LoggerFactory.getLogger(ProfitAndLossService.class);
    private final OrderRepository orderRepository;
    private final SellMatcher sellMatcher = new SellMatcher();

    private static final int SCALE = 8;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    //Calculates the realized and unrealized PnL for the given user based on their orders and current market prices.
    @Transactional
    public PnlResult calculatePnlForUser(Long userId, Map<String, BigDecimal> marketPrices) {
        List<Order> orders = orderRepository.findByUserIdOrderByPlacedAtDesc(userId);

        Map<String, Deque<Lot>> buyQueues = new HashMap<>();
        Map<String, Deque<Lot>> sellQueues = new HashMap<>(); // kept for interface compatibility, not used
        Map<String, BigDecimal> realizedPnlMap = new HashMap<>();

        processOrders(orders, buyQueues, sellQueues, realizedPnlMap);

        return buildPnlResult(buyQueues, realizedPnlMap, marketPrices);
    }

    private void processOrders(List<Order> orders,
                               Map<String, Deque<Lot>> buyQueues,
                               Map<String, Deque<Lot>> sellQueues,
                               Map<String, BigDecimal> realized) {

        for (Order order : orders) {
            if (order.getItems() == null || order.getOrderSide() == null) continue;

            for (Executions ex : order.getItems()) {
                if (ex == null || ex.getInstrumentId() == null) continue;

                if ("BUY".equalsIgnoreCase(order.getOrderSide().name())) {
                    processBuy(order, ex, buyQueues);
                } else if ("SELL".equalsIgnoreCase(order.getOrderSide().name())) {
                    try {
                        BigDecimal realizedForThisExec = sellMatcher.processSell(order, ex, buyQueues, sellQueues);
                        realized.merge(ex.getInstrumentId(), realizedForThisExec, BigDecimal::add);
                    } catch (IllegalStateException e) {
                        logger.warn("Skipping invalid sell for instrument {}: {}", ex.getInstrumentId(), e.getMessage());
                    }
                }
            }
        }
    }

    private void processBuy(Order order, Executions ex, Map<String, Deque<Lot>> buyQueues) {
        BigDecimal qty = safe(ex.getQuantity());
        if (qty.compareTo(BigDecimal.ZERO) == 0) return;

        Lot lot = new Lot(buildLotId(order, ex), qty, safe(ex.getExecutedPrice()), safe(ex.getFees()));
        buyQueues.computeIfAbsent(ex.getInstrumentId(), s -> new ArrayDeque<>()).addLast(lot);
    }

    /**
     * Builds a comprehensive PnlResult with detailed per-symbol PnL including positions, avg cost,
     * realized, unrealized, and market prices along with overall totals.
     */
    private PnlResult buildPnlResult(Map<String, Deque<Lot>> buyQueues,
                                     Map<String, BigDecimal> realizedPnlMap,
                                     Map<String, BigDecimal> marketPrices) {

        Map<String, SymbolPnl> bySymbol = new HashMap<>();
        BigDecimal totalRealized = BigDecimal.ZERO;
        BigDecimal totalUnrealized = BigDecimal.ZERO;

        // Set of all symbols encountered
        Set<String> allSymbols = new HashSet<>();
        allSymbols.addAll(buyQueues.keySet());
        allSymbols.addAll(realizedPnlMap.keySet());

        for (String symbol : allSymbols) {
            Deque<Lot> lots = buyQueues.getOrDefault(symbol, new ArrayDeque<>());
            BigDecimal positionQty = BigDecimal.ZERO;
            BigDecimal costSum = BigDecimal.ZERO;

            // Calculate position quantity and cost based on open lots
            for (Lot lot : lots) {
                positionQty = positionQty.add(lot.getQty());
                costSum = costSum.add(lot.getPrice().multiply(lot.getQty()));
            }

            BigDecimal avgCost = positionQty.compareTo(BigDecimal.ZERO) > 0
                    ? costSum.divide(positionQty, SCALE, ROUND)
                    : BigDecimal.ZERO;

            BigDecimal marketPrice = marketPrices.getOrDefault(symbol, BigDecimal.ZERO);

            BigDecimal unrealizedPnl = BigDecimal.ZERO;
            for (Lot lot : lots) {
                BigDecimal pnl = marketPrice.subtract(lot.getPrice())
                        .multiply(lot.getQty())
                        .setScale(SCALE, ROUND);
                unrealizedPnl = unrealizedPnl.add(pnl);
            }
            unrealizedPnl = unrealizedPnl.setScale(SCALE, ROUND);

            BigDecimal realizedPnl = realizedPnlMap.getOrDefault(symbol, BigDecimal.ZERO);

            SymbolPnl symbolPnl = SymbolPnl.builder()
                    .symbol(symbol)
                    .positionQty(positionQty)
                    .avgCost(avgCost)
                    .marketPrice(marketPrice)
                    .realizedPnl(realizedPnl)
                    .unrealizedPnl(unrealizedPnl)
                    .openLots(new ArrayList<>(lots))
                    .build();

            bySymbol.put(symbol, symbolPnl);

            totalRealized = totalRealized.add(realizedPnl);
            totalUnrealized = totalUnrealized.add(unrealizedPnl);
        }

        BigDecimal totalNet = totalRealized.add(totalUnrealized);

        return new PnlResult(bySymbol, totalRealized, totalUnrealized, totalNet);
    }

    private String buildLotId(Order order, Executions ex) {
        String exId = (ex.getExecutionId() != null ? ex.getExecutionId()
                : (ex.getId() != null ? ex.getId().toString() : "x"));
        return (order.getId() != null ? order.getId().toString() : "o") + "-" + exId;
    }

    private BigDecimal safe(BigDecimal val) {
        return val == null ? BigDecimal.ZERO : val;
    }
}

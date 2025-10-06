package com.example.trading.order_service.utility;

import com.example.trading.order_service.dto.pnl.Lot;
import com.example.trading.order_service.entity.Executions;
import com.example.trading.order_service.entity.Order;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Deque;
import java.util.Map;

/**
 * Utility class to handle matching of sell executions against existing buy lots in FIFO order.
 * Calculates realized Profit and Loss (PnL) by matching sell quantities with previously bought lots,
 * adjusting lot quantities accordingly, and disallowing selling without prior buys.
 */
@Component
public class SellMatcher {
    private static final int SCALE = 8;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    public BigDecimal processSell(Order order,
                                  Executions ex,
                                  Map<String, Deque<Lot>> buyQueues,
                                  Map<String, Deque<Lot>> sellQueues) {
        BigDecimal qty = safe(ex.getQuantity());
        if (qty.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        String symbol = ex.getInstrumentId();
        Deque<Lot> buyQ = buyQueues.get(symbol);

        // Reject sell if no buy lots exist for this instrument
        if (buyQ == null || buyQ.isEmpty()) {
            throw new IllegalStateException("Cannot sell instrument " + symbol + " without prior buys");
        }

        BigDecimal remainingToSell = qty;
        BigDecimal realizedForThisExec = BigDecimal.ZERO;

        // Match sell quantity against existing buy lots until fulfilled or no buy lots left
        while (remainingToSell.compareTo(BigDecimal.ZERO) > 0 && !buyQ.isEmpty()) {
            realizedForThisExec = realizedForThisExec.add(matchBuyLot(buyQ, ex, qty, remainingToSell));
            BigDecimal lotQty = buyQ.peekFirst().getQty();
            remainingToSell = remainingToSell.subtract(lotQty.min(remainingToSell));
        }
        return realizedForThisExec;
    }

    private BigDecimal matchBuyLot(Deque<Lot> buyQ, Executions ex,
                                   BigDecimal sellQty, BigDecimal remainingToSell) {
        Lot buyLot = buyQ.peekFirst();
        BigDecimal matched = buyLot.getQty().min(remainingToSell);

        // Allocate fees proportionally for matched quantity
        BigDecimal buyFeeAlloc = buyLot.getFees().multiply(matched).divide(buyLot.getQty(), SCALE, ROUND);
        BigDecimal sellFeeAlloc = safe(ex.getFees()).multiply(matched).divide(sellQty, SCALE, ROUND);

        // Realized PnL = (Sell price - Buy price) * matched qty - fees
        BigDecimal pnl = safe(ex.getExecutedPrice()).subtract(buyLot.getPrice())
                .multiply(matched).subtract(buyFeeAlloc).subtract(sellFeeAlloc);

        // Adjust or remove buy lot based on matched quantity
        if (buyLot.getQty().compareTo(matched) == 0) {
            buyQ.removeFirst();
        } else {
            Lot newLot = new Lot(buyLot.getId(),
                    buyLot.getQty().subtract(matched),
                    buyLot.getPrice(),
                    buyLot.getFees().subtract(buyFeeAlloc));
            buyQ.removeFirst();
            buyQ.addFirst(newLot);
        }

        return pnl;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

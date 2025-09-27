package com.example.trading.order_service.dto.pnl;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SymbolPnl {
    private String symbol;
    private BigDecimal positionQty;    // positive = net long, negative = net short
    private BigDecimal avgCost;        // average cost of open position
    private BigDecimal marketPrice;    // latest price for unrealized PnL
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private List<Lot> openLots;        // FIFO lots still open
}

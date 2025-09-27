package com.example.trading.order_service.dto.pnl;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
public class PnlResult {
    private Map<String, SymbolPnl> bySymbol;
    private BigDecimal totalRealized;
    private BigDecimal totalUnrealized;
    private BigDecimal totalNet; // realized + unrealized
}
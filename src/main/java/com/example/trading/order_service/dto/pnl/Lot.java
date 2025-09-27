package com.example.trading.order_service.dto.pnl;

import lombok.Value;

import java.math.BigDecimal;

/**
 * Represents a unit of holding (a block of bought or sold quantity of a symbol at a specific cost basis).
 * Whenever you buy, a new Lot is created.
 * when you sell, quantity is matched against one or more FIFO lots.
 */
@Value
public class Lot {
    String id;              // e.g. orderId-executionId
    BigDecimal qty;         // remaining quantity in this lot
    BigDecimal price;
    BigDecimal fees;
}
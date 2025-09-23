package com.example.trading.order_service.dto;

import java.math.BigDecimal;

public record OrderPlacedEvent(
        String orderId,
        String userId,
        String symbol,
        String slide,
        String type,
        BigDecimal quantity,
        String price,
        String advancedFeatures
) { }

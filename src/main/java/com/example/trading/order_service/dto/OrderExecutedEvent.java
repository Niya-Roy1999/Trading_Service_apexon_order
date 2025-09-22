package com.example.trading.order_service.dto;

import org.springframework.kafka.annotation.KafkaListener;

import java.math.BigDecimal;

public record OrderExecutedEvent (
        String eventType, String orderId, String clientId, String symbol,
        Long filledQuantity, BigDecimal fillPrice, String status, String timestamp
){}


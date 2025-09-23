package com.example.trading.order_service.dto;

public record EventEnvelope<T>(
        String eventType,
        String schemaVersion,
        String correlationId,
        String producer,
        String timeStamp,
        T payload
) { }

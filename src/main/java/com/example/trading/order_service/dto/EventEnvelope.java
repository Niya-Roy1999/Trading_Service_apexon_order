package com.example.trading.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {
    private String eventType;
    private String schemaVersion;
    private String correlationId;
    private String producer;
    private String timeStamp;
    private T payload;
}


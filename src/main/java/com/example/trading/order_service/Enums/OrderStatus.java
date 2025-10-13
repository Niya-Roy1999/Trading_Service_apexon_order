package com.example.trading.order_service.Enums;

public enum OrderStatus {
    NEW,
    PENDING_VALIDATION,
    PENDING_WALLET_CHECK,
    PENDING_COMPLIANCE,
    APPROVED,
    REJECTED,
    PENDING,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    EXECUTED
}

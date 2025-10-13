package com.example.trading.order_service.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(Long userId, String amount) {
        super(String.format("User %d has insufficient funds. Required: %s", userId, amount));
    }
}

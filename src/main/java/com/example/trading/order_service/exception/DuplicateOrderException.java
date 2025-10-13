package com.example.trading.order_service.exception;

public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String clientOrderId) {
        super("Order with client-order id " + clientOrderId + " is duplicate");
    }
}

package com.example.trading.order_service.entity;

import lombok.Data;

@Data
public class Wallet {
    private Long walletId;
    private Long accountId;
    private Double balance;
}

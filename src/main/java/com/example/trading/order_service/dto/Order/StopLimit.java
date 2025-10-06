package com.example.trading.order_service.dto.Order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class StopLimit extends BaseOrder {
    private Double stopPrice;
    private Double limitPrice;
}

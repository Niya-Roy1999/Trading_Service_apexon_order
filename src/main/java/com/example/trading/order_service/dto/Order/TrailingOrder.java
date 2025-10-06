package com.example.trading.order_service.dto.Order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrailingOrder extends BaseOrder{
    private Double stopPrice;
    private Double trailingOffset;
    private String trailingType;
}

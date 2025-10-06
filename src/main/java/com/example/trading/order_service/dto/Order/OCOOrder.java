package com.example.trading.order_service.dto.Order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OCOOrder extends BaseOrder{
    private String ocoGroupId;
    private String linkedOrderId;
    private Double price;
    private Double stopPrice;
}

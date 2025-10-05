package com.example.trading.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderApprovalEvent {
    private String orderId;
    private boolean approved;
}

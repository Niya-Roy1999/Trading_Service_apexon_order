package com.example.trading.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
public class ComplianceApproved {

    public String orderId;
    private boolean passed;
    private List<String> violatedRules;
}

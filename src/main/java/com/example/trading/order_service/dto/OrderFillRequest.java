package com.example.trading.order_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class OrderFillRequest {
    @NotNull
    @DecimalMin("0.00000001")
    private BigDecimal quantity;

    @NotNull
    @DecimalMin("0")
    private BigDecimal executedPrice;

    @NotNull
    @DecimalMin("0")
    private BigDecimal fees;

    private String executionId;

    private OffsetDateTime executedAt;
}

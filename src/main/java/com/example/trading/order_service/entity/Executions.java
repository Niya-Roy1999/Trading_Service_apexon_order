package com.example.trading.order_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "order_items",
        indexes = {
                @Index(name = "ix_items_order", columnList = "order_id"),
                @Index(name = "ix_items_instr_time", columnList = "instrument_id, executed_at DESC")
        }
)
public class Executions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_items_order"))
    private Order order;

    @Column(name = "instrument_id", nullable = false, length = 64)
    private String instrumentId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(name = "executed_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal executedPrice;

    @Column(name = "fees", nullable = false, precision = 18, scale = 8)
    private BigDecimal fees = BigDecimal.ZERO;

    @Column(name = "notional", nullable = false, precision = 20, scale = 8)
    private BigDecimal notional;

    @Column(name = "execution_id", length = 64)
    private String executionId;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.notional == null && quantity != null && executedPrice != null) {
            this.notional = quantity.multiply(executedPrice);
        }
    }
}

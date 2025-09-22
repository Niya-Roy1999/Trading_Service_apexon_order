package com.example.trading.order_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "assets",
        uniqueConstraints = @UniqueConstraint(name = "uq_assets_user_instr", columnNames = {"user_id","instrument_id"}),
        indexes = {
                @Index(name = "ix_assets_user", columnList = "user_id"),
                @Index(name = "ix_assets_instr", columnList = "instrument_id")
        }
)
public class Assets {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instrument_id", nullable = false, length = 64)
    private String instrumentId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "avg_buy_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal avgBuyPrice = BigDecimal.ZERO;

    @Column(name = "last_updated", nullable = false)
    private OffsetDateTime lastUpdated = OffsetDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.lastUpdated = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        this.lastUpdated = OffsetDateTime.now();
    }
}


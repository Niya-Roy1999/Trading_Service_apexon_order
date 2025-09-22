package com.example.trading.order_service.kafka;

import com.example.trading.order_service.dto.OrderExecutedEvent;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
@Component
@RequiredArgsConstructor
public class ExecutionEventsConsumer {
    private final OrderRepository repo;
    @KafkaListener(topics = "executions.v1", groupId = "order-service")
    public void onExecuted(OrderExecutedEvent e) {
        repo.findById(UUID.fromString(e.orderId())).ifPresent(o -> {
            o.setStatus(OrderStatus.FILLED); // or map from e.status
            o.setUpdatedAt(Instant.now());
            repo.save(o);
        });
    }
}
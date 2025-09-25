package com.example.trading.order_service.kafka;

/*
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
  */
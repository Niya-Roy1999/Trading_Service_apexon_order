package com.example.trading.order_service.kafka;

import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.OrderApprovalEvent;
import com.example.trading.order_service.dto.OrderRejectedEvent;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class OrderApprovalConsumer {

    @Autowired
    private OrderEventsProducer producer;

    @Autowired
    private OrderRepository orderRepository;

    @KafkaListener(topics = "order-waiting-approval-topic")
    public void handleApproval(EventEnvelope<OrderApprovalEvent> envelope) {
        OrderApprovalEvent event = envelope.getPayload();
        String orderId = event.getOrderId();

        Optional<Order> orderOpt = orderRepository.findById(Long.valueOf(orderId));
        boolean approved = false;
        String rejectionReason = "Unknown error";
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            // EXAMPLE LOGIC: block certain users/symbols
            Set<Long> blockedUsers = Set.of(911L, 1313L);
            Set<String> blockedSymbols = Set.of("XYZHALTED", "SUSPENDEDSTOCK");

            if (blockedUsers.contains(order.getUserId())) {
                approved = false;
                rejectionReason = "User is blocked.";
            } else if (blockedSymbols.contains(order.getInstrumentSymbol())) {
                approved = false;
                rejectionReason = "Instrument is halted.";
            } else {
                approved = true;
            }
        } else {
            approved = false;
            rejectionReason = "Order not found in DB";
        }

        if (approved) {
            EventEnvelope<OrderApprovalEvent> approveEnvelope = new EventEnvelope<>(
                    "OrderApproved", "v1", UUID.randomUUID().toString(),
                    "approval-service", Instant.now().toString(),
                    new OrderApprovalEvent(orderId, true)
            );
            producer.publish("order-approved-topic", orderId, approveEnvelope);
        } else {
            EventEnvelope<OrderRejectedEvent> rejectEnvelope = new EventEnvelope<>(
                    "OrderRejected", "v1", UUID.randomUUID().toString(),
                    "approval-service", Instant.now().toString(),
                    new OrderRejectedEvent(orderId, rejectionReason)
            );
            producer.publish("order-rejected-topic", orderId, rejectEnvelope);
        }
    }
}

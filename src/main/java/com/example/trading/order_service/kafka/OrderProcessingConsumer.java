package com.example.trading.order_service.kafka;

import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.OrderApprovalEvent;
import com.example.trading.order_service.dto.OrderPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class OrderProcessingConsumer {
    @Autowired
    private OrderEventsProducer producer;

    //Thresholds for requiring approval
    private static final BigDecimal LARGE_QUANTITY_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal MAX_PRICE_THRESHOLD = new BigDecimal("1000000");

    //High-risk order types that require approval
    private static final Set<String> HIGH_RISK_TYPES = new HashSet<>(Arrays.asList(
            "STOP_MARKET", "STOP_LIMIT", "TRAILING_STOP", "ICEBERG", "PEGGED"));

    @KafkaListener(topics = "order-topic")
    public void handleOrder(EventEnvelope<OrderPlacedEvent> envelope) {
        OrderPlacedEvent event = envelope.getPayload();
        boolean needsApproval = isApprovalRequired(event);

        if (needsApproval) {
            OrderApprovalEvent approvalEvent = new OrderApprovalEvent(event.getOrderId(), false);
            EventEnvelope<OrderApprovalEvent> outEnvelope = new EventEnvelope<>(
                    "OrderWaitingApproval", "v1",
                    UUID.randomUUID().toString(),
                    "processing-service", Instant.now().toString(),
                    approvalEvent
            );
            producer.publish("order-waiting-approval-topic",
                    event.getOrderId(), outEnvelope);
        } else {
            EventEnvelope<OrderApprovalEvent> approveEnvelope = new EventEnvelope<>(
                    "OrderApproved", "v1", UUID.randomUUID().toString(),
                    "processing-service", Instant.now().toString(),
                    new OrderApprovalEvent(event.getOrderId(), true)
            );
            producer.publish("order-approved-topic",
                    event.getOrderId(), approveEnvelope);
        }
    }

    private boolean isApprovalRequired(OrderPlacedEvent event) {
        try {
            //Large order size check
            if (event.getQuantity() != null &&
                    event.getQuantity().compareTo(LARGE_QUANTITY_THRESHOLD) > 0) {
                log.info("Order " + event.getOrderId() + "requires approval due to large quantity: " + event.getQuantity());
                return true;
            }

            //High-risk types check
            if (event.getType() != null &&
                    HIGH_RISK_TYPES.contains(event.getType().toUpperCase())) {
                log.info("Order " + event.getOrderId() + "requires approval due to type: " + event.getType());
                return true;
            }

            //High price limit check
            if (event.getPrice() != null &&
                    event.getPrice().compareTo(MAX_PRICE_THRESHOLD) > 0) {
                log.info("Order " + event.getOrderId() + "requires approval due to high price: " + event.getPrice());
                return true;
            }
        } catch (Exception e) {
            log.warn("Error while evaluating approval for order " + event.getOrderId(),
                    e.getMessage());
            return true;
        }
        return false;
    }
}

package com.example.trading.order_service.kafka;

import com.example.trading.order_service.Enums.OrderSide;
import com.example.trading.order_service.Enums.OrderType;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.OrderPlacedEvent;
import com.example.trading.order_service.dto.OrderRejectedEvent;
import com.example.trading.order_service.dto.OrderValidationEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
public class OrderValidationConsumer {
    @Autowired
    private OrderEventsProducer producer;

    @KafkaListener(topics = "order-validation-topic")
    public void handleValidation(EventEnvelope<OrderValidationEvent> envelope) {
        OrderValidationEvent event = envelope.getPayload();

        boolean isValid = isValidOrder(event);

        if (isValid) {
            // Send to order topic if valid
            EventEnvelope<OrderPlacedEvent> nextEnvelope = new EventEnvelope<>(
                    "orderPlaced", "v1", UUID.randomUUID().toString(),
                    "validation-service", Instant.now().toString(),
                    convertToPlacedEvent(event)
            );
            producer.publish("order-topic", event.getOrderId(), nextEnvelope);
        } else {
            // Send to order-rejected topic if invalid
            OrderRejectedEvent rejection = new OrderRejectedEvent(event.getOrderId(), makeRejectionReason(event));
            EventEnvelope<OrderRejectedEvent> rejectEnvelope = new EventEnvelope<>(
                    "orderRejected", "v1", UUID.randomUUID().toString(),
                    "Validation-service", Instant.now().toString(), rejection
            );
            producer.publish("order-rejected-topic", event.getOrderId(), rejectEnvelope);
        }
    }

    private boolean isValidOrder(OrderValidationEvent event) {
        if (event.getOrderId() == null || event.getOrderId().isEmpty()) return false;
        if (event.getUserId() == null) return false;
        if (event.getInstrumentId() == null || event.getInstrumentSymbol() == null) return false;

        if (event.getOrderSide() == null ||
                (!event.getOrderSide().equalsIgnoreCase(OrderSide.BUY.name()) &&
                        !event.getOrderSide().equalsIgnoreCase(OrderSide.SELL.name())))
            return false;

        if (event.getOrderType() == null) return false;

        if (event.getQuantity() == null || event.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            return false;

        if (event.getOrderType().equalsIgnoreCase(OrderType.LIMIT.name()) && (event.getPrice() == null || event.getPrice().compareTo(BigDecimal.ZERO) <= 0))
            return false;

        if ((event.getOrderType().equalsIgnoreCase(OrderType.STOP_LIMIT.name()) ||
                event.getOrderType().equalsIgnoreCase(OrderType.STOP_MARKET.name())) &&
                (event.getStopPrice() == null || event.getStopPrice().compareTo(BigDecimal.ZERO) <= 0))
            return false;

        // Example iceberg validation
        if (event.getOrderType().equalsIgnoreCase(OrderType.ICEBERG.name()) &&
                (event.getDisplayQuantity() == null || event.getDisplayQuantity() < 1 ||
                        event.getDisplayQuantity() > event.getQuantity().intValue()))
            return false;

        // TimeInForce validation
        if (event.getTimeInForce() == null) return false;

        return true;
    }

    private String makeRejectionReason(OrderValidationEvent event) {
        if (event.getQuantity() == null || event.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            return "Invalid quantity";
        if (event.getOrderType() != null && event.getOrderType().equalsIgnoreCase(OrderType.LIMIT.name())
                && (event.getPrice() == null || event.getPrice().compareTo(BigDecimal.ZERO) <= 0))
            return "Invalid limit price";
        if ((event.getOrderType() != null && (
                event.getOrderType().equalsIgnoreCase(OrderType.STOP_LIMIT.name()) ||
                        event.getOrderType().equalsIgnoreCase(OrderType.STOP_MARKET.name()))
        ) && (event.getStopPrice() == null || event.getStopPrice().compareTo(BigDecimal.ZERO) <= 0))
            return "Invalid stop price";
        if (event.getOrderId() == null || event.getOrderId().isEmpty())
            return "Order ID missing";
        return "Failed validation";
    }

    private OrderPlacedEvent convertToPlacedEvent(OrderValidationEvent event) {
        OrderPlacedEvent placed = new OrderPlacedEvent();
        placed.setOrderId(event.getOrderId());
        placed.setUserId(event.getUserId().toString());
        placed.setSymbol(event.getInstrumentSymbol());
        placed.setSide(event.getOrderSide());
        placed.setType(event.getOrderType());
        placed.setQuantity(event.getQuantity());
        placed.setPrice(event.getPrice());
        placed.setStopPrice(event.getStopPrice());
        placed.setTrailingOffset(event.getTrailingOffset());
        placed.setTrailingType(event.getTrailingType());
        placed.setDisplayQuantity(event.getDisplayQuantity());
        placed.setTimeInForce(event.getTimeInForce());
        placed.setStatus("VALIDATED");
        return placed;
    }
}


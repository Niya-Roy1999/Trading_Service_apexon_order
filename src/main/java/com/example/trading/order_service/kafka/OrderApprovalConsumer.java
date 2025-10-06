package com.example.trading.order_service.kafka;

import com.example.trading.order_service.Enums.OrderType;
import com.example.trading.order_service.dto.ComplianceApproved;
import com.example.trading.order_service.dto.EventEnvelope;
import com.example.trading.order_service.dto.Order.*;
import com.example.trading.order_service.entity.Order;
import com.example.trading.order_service.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderApprovalConsumer {

    private final OrderEventsProducer kafkaProducer;
    private final OrderRepository orderRepository;

    @Value("${spring.kafka.topics.exchange}")
    private String exchangeTopic;

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${spring.kafka.topics.order-approved}",
            groupId = "order-service-group",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void consumeApproval(String message) {
        try {
            EventEnvelope<ComplianceApproved> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<EventEnvelope<ComplianceApproved>>() {}
            );

            ComplianceApproved payload = envelope.getPayload();
            log.info("Deserialized payload from Compliance service: {}", payload);
            Long orderId = Long.parseLong(payload.getOrderId());

            Order order = orderRepository.findById(orderId).orElseThrow(
                    () -> new IllegalArgumentException("Order not found for id: " + orderId));
            log.info("Fetched order from DB: id={}, userId={}, type={}, quantity={}",
                    order.getId(),
                    order.getUserId(),
                    order.getType(),
                    order.getTotalQuantity());

            BaseOrder transformedOrder = transformOrder(order);
            log.info("Transformed order ready to process/send: {}", transformedOrder);
            kafkaProducer.publish(exchangeTopic, String.valueOf(order.getId()), transformedOrder);
        } catch (JsonProcessingException e) {
            log.error("Error parsing compliance approved message", e);
        } catch (Exception e) {
            log.error("Error processing orderId from compliance approved", e);
        }
    }

    private BaseOrder transformOrder(Order order) {
        if (order.getType() == OrderType.LIMIT) {
            return LimitOrder.builder()
                    .orderId(String.valueOf(order.getId()))
                    .userId(String.valueOf(order.getUserId()))
                    .symbol(order.getInstrumentSymbol())
                    .side(order.getOrderSide())
                    .type(order.getType())
                    .quantity(order.getTotalQuantity().intValue())
                    .timeInForce(order.getTimeInForce())
                    .status(order.getStatus())
                    .price(order.getLimitPrice() != null ? order.getLimitPrice().doubleValue() : null)
                    .build();
        } else if (order.getType() == OrderType.MARKET) {
            return MarketOrder.builder()
                    .orderId(String.valueOf(order.getId()))
                    .userId(String.valueOf(order.getUserId()))
                    .symbol(order.getInstrumentSymbol())
                    .side(order.getOrderSide())
                    .type(order.getType())
                    .quantity(order.getTotalQuantity().intValue())
                    .timeInForce(order.getTimeInForce())
                    .status(order.getStatus())
                    .build();
        } else if (order.getType() == OrderType.STOP_LIMIT) {
            return StopLimit.builder()
                    .orderId(String.valueOf(order.getId()))
                    .userId(String.valueOf(order.getUserId()))
                    .symbol(order.getInstrumentSymbol())
                    .side(order.getOrderSide())
                    .type(order.getType())
                    .quantity(order.getTotalQuantity().intValue())
                    .timeInForce(order.getTimeInForce())
                    .status(order.getStatus())
                    .limitPrice(order.getLimitPrice() != null ? order.getLimitPrice().doubleValue() : null)
                    .stopPrice(order.getStopPrice() != null ? order.getStopPrice().doubleValue() : null)
                    .build();
        } else if (order.getType() == OrderType.STOP_MARKET) {
            return StopMarket.builder()
                    .orderId(String.valueOf(order.getId()))
                    .userId(String.valueOf(order.getUserId()))
                    .symbol(order.getInstrumentSymbol())
                    .side(order.getOrderSide())
                    .type(order.getType())
                    .quantity(order.getTotalQuantity().intValue())
                    .timeInForce(order.getTimeInForce())
                    .status(order.getStatus())
                    .build();
        }
        throw new IllegalArgumentException("Unsupported order type: " + order.getType());
    }
}


     /*
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
    } */

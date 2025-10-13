package com.example.trading.order_service.kafka;

import com.example.trading.order_service.exception.KafkaPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventsProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String orderId, Object payload, Map<String, String> headers) {
        log.debug("📤 [KAFKA-PRODUCER] Preparing to publish - Topic: {}, OrderID: {}, PayloadType: {}",
                topic, orderId, payload.getClass().getSimpleName());

        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, orderId, payload);

        if (headers != null && !headers.isEmpty()) {
            log.debug("📋 [KAFKA-PRODUCER] Adding {} custom headers - OrderID: {}", headers.size(), orderId);
            headers.forEach((k, v) ->
                    record.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));
        }

        try {
            log.debug("🚀 [KAFKA-PRODUCER] Sending message to Kafka - Topic: {}, OrderID: {}", topic, orderId);
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(record);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("❌ [KAFKA-PRODUCER] Failed to publish message - Topic: {}, OrderID: {}, Error: {}",
                            topic, orderId, ex.getMessage(), ex);
                    // TODO: Consider persisting failed messages to DB for retry
                    throw new KafkaPublishException("Failed to publish order event to topic: " + topic, ex);
                } else {
                    log.info("✅ [KAFKA-PRODUCER] Message published successfully - Topic: {}, OrderID: {}, Partition: {}, Offset: {}",
                            topic, orderId,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

        } catch (Exception e) {
            log.error("❌ [KAFKA-PRODUCER] Unexpected error publishing to Kafka - Topic: {}, OrderID: {}, Error: {}",
                    topic, orderId, e.getMessage(), e);
            throw new KafkaPublishException("Failed to publish order event to topic: " + topic, e);
        }
    }

    public void publish(String topic, String orderId, Object payload) {
        log.debug("📤 [KAFKA-PRODUCER] Publishing without custom headers - Topic: {}, OrderID: {}", topic, orderId);
        publish(topic, orderId, payload, Collections.emptyMap());
    }
}

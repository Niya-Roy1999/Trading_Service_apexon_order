package com.example.trading.order_service.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor

public class OrderEventsProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String orderId, Object payload, Map<String, String> headers) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, orderId, payload);

        if (headers != null && !headers.isEmpty()) {
            headers.forEach((k, v) ->
                    record.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));
        }
        kafkaTemplate.send(record);
    }

    public void publish(String topic, String orderId, Object payload) {
        publish(topic, orderId, payload, Collections.emptyMap());
    }
}

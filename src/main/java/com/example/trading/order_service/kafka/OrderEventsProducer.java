package com.example.trading.order_service.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor

public class OrderEventsProducer {
    private  final KafkaTemplate<String,Object> kafka;

    public void publish(String topic,String orderId,Object payload, Map<String,String> headers)
    {
        var record=new ProducerRecord<String,Object>(topic,orderId,payload);
        headers.forEach((key,value)->record.headers().
                add(key,value.getBytes(StandardCharsets.UTF_8)));
        kafka.send(record);
    }

}

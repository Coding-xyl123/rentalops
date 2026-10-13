
package com.olivia.rental.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;

@Service
@EnableScheduling
public class OutboxPublisher {

    private static final String TOPIC = "maintenance-events";

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() throws Exception {
        List<OutboxEvent> pendingEvents =
                outboxRepository.findByStatusOrderByCreatedAtAsc(
                        OutboxEvent.Status.PENDING);

        for (OutboxEvent event : pendingEvents) {
            // Wait for Kafka broker acknowledgment before updating DB.
            kafkaTemplate.send(
                    TOPIC,
                    event.getEventId(),
                    event.getPayload()
            ).get(10, TimeUnit.SECONDS);

            event.markPublished();
            outboxRepository.save(event);
        }
    }
}

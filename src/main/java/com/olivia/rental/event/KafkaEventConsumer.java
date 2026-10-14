
package com.olivia.rental.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class KafkaEventConsumer {

    private final ObjectMapper objectMapper;
    private final KafkaEventProcessingService processingService;

    public KafkaEventConsumer(
            ObjectMapper objectMapper,
            KafkaEventProcessingService processingService) {
        this.objectMapper = objectMapper;
        this.processingService = processingService;
    }

   @KafkaListener(
    topics = "maintenance-events",
    groupId = "${spring.kafka.consumer.group-id}"
)
    public void consume(String message) {

        Event event = objectMapper.readValue(message, Event.class);

        if (event.getEventId() == null
                || event.getEventId().isBlank()
                || event.getEventType() == null
                || event.getEventType().isBlank()) {

            // Reject incomplete event envelopes.
            // This also catches older payload-only messages.
            throw new IllegalArgumentException(
                    "Invalid Kafka event envelope"
            );
        }

        processingService.process(event);
    }
}

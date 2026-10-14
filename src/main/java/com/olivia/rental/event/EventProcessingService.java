
package com.olivia.rental.event;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Service
public class EventProcessingService {

    private final EventService eventService;
    private final EntityManager entityManager;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public EventProcessingService(
            EventService eventService,
            EntityManager entityManager,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {

        this.eventService = eventService;
        this.entityManager = entityManager;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProcessingResult process(Event event) {

        if (event.getEventId() == null
                || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }

        // Serialize concurrent requests with the same event ID.
        entityManager.createNativeQuery(
                "SELECT pg_advisory_xact_lock("
                        + "hashtextextended(:eventId, 0))"
        )
        .setParameter("eventId", event.getEventId())
        .getSingleResult();

        Optional<Event> existing =
                eventService.findById(event.getEventId());

        if (existing.isPresent()) {

            // Preserve idempotency and conflict handling.
            Event savedEvent = eventService.save(event);

            // Recommendation may or may not be ready yet.
            Event recommendation = eventService
                    .findRecommendationForTrigger(
                            savedEvent.getEventId()
                    )
                    .orElse(null);

            return new ProcessingResult(
                    new EventProcessingResponse(
                            savedEvent,
                            recommendation
                    ),
                    false
            );
        }

        // Persist the new event without generating a recommendation.
        Event savedEvent = eventService.save(event);

        // Serialize the complete event envelope.
        String eventJson =
                objectMapper.writeValueAsString(savedEvent);

        // Persist outbox record in the same transaction.
        OutboxEvent outboxEvent = new OutboxEvent(
                savedEvent.getEventId(),
                savedEvent.getEventType(),
                eventJson
        );

        outboxEventRepository.save(outboxEvent);

        // Kafka Consumer will generate the recommendation asynchronously.
        return new ProcessingResult(
                new EventProcessingResponse(savedEvent, null),
                true
        );
    }

    public record ProcessingResult(
            EventProcessingResponse response,
            boolean created
    ) {
    }
}


package com.olivia.rental.event;

import com.olivia.rental.event.recommendation.RecommendationService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EventProcessingService {

    private final EventService eventService;
    private final RecommendationService recommendationService;
    private final EntityManager entityManager;
    private final OutboxEventRepository outboxEventRepository;

    public EventProcessingService(
            EventService eventService,
            RecommendationService recommendationService,
            EntityManager entityManager,
            OutboxEventRepository outboxEventRepository) {

        this.eventService = eventService;
        this.recommendationService = recommendationService;
        this.entityManager = entityManager;
        this.outboxEventRepository = outboxEventRepository;
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

            // Preserve existing idempotency and conflict handling.
            Event savedEvent = eventService.save(event);

            Event recommendation = eventService
                    .findRecommendationForTrigger(
                            savedEvent.getEventId()
                    )
                    .orElse(null);

            // Do not create another outbox message for a retry.
            return new ProcessingResult(
                    new EventProcessingResponse(
                            savedEvent,
                            recommendation
                    ),
                    false
            );
        }

        // New event: persist it and generate the recommendation.
        Event savedEvent = eventService.save(event);

        Event recommendation =
                recommendationService.generateRecommendation(
                        savedEvent
                );

        // V2: Create an outbox record in the same transaction.
        OutboxEvent outboxEvent = new OutboxEvent(
                savedEvent.getEventId(),
                savedEvent.getEventType(),
                savedEvent.getPayload()
        );

        outboxEventRepository.save(outboxEvent);

        return new ProcessingResult(
                new EventProcessingResponse(
                        savedEvent,
                        recommendation
                ),
                true
        );
    }

    public record ProcessingResult(
            EventProcessingResponse response,
            boolean created
    ) {
    }
}


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

    public EventProcessingService(
            EventService eventService,
            RecommendationService recommendationService,
            EntityManager entityManager) {

        this.eventService = eventService;
        this.recommendationService = recommendationService;
        this.entityManager = entityManager;
    }

    @Transactional
    public ProcessingResult process(Event event) {

        if (event.getEventId() == null
                || event.getEventId().isBlank()) {

            throw new IllegalArgumentException(
                    "eventId is required"
            );
        }

        // Serialize concurrent requests with the same event ID.
        // The lock is released when this transaction ends.
        entityManager.createNativeQuery(
                "SELECT pg_advisory_xact_lock("
                        + "hashtextextended(:eventId, 0))"
        )
        .setParameter("eventId", event.getEventId())
        .getSingleResult();

        Optional<Event> existing =
                eventService.findById(event.getEventId());

        if (existing.isPresent()) {

            // Validates that the incoming event matches
            // the existing event. Throws EventConflictException
            // if the content differs.
            Event savedEvent = eventService.save(event);

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

        // First request: save the issue and its recommendation
        // in the same transaction.
        Event savedEvent = eventService.save(event);

        Event recommendation =
                recommendationService.generateRecommendation(
                        savedEvent
                );

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

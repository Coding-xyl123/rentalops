
package com.olivia.rental.event;

import com.olivia.rental.event.recommendation.RecommendationService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KafkaEventProcessingService {

    private final ProcessedKafkaEventRepository processedRepository;
    private final EntityManager entityManager;
    private final RecommendationService recommendationService;

    public KafkaEventProcessingService(
            ProcessedKafkaEventRepository processedRepository,
            EntityManager entityManager,
            RecommendationService recommendationService) {

        this.processedRepository = processedRepository;
        this.entityManager = entityManager;
        this.recommendationService = recommendationService;
    }

    @Transactional
    public void process(Event event) {

        String eventId = event.getEventId();

        // Serialize concurrent processing of the same event ID.
        entityManager.createNativeQuery(
                "SELECT pg_advisory_xact_lock("
                        + "hashtextextended(:eventId, 0))"
        )
        .setParameter("eventId", eventId)
        .getSingleResult();

        // Skip previously processed events.
        if (processedRepository.existsById(eventId)) {
            return;
        }

        // Generate a recommendation only for maintenance events.
        if ("MaintenanceIssueDetected".equals(event.getEventType())) {
            recommendationService.generateRecommendation(event);
        }

        // Record successful processing in the same transaction.
        processedRepository.save(
                new ProcessedKafkaEvent(eventId)
        );
    }
}

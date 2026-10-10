
package com.olivia.rental.event.recommendation;

import com.olivia.rental.event.Event;
import com.olivia.rental.event.EventService;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RecommendationService {

    private final EventService eventService;

    public RecommendationService(EventService eventService) {
        this.eventService = eventService;
    }

    public Event generateRecommendation(Event triggerEvent) {

        if (!"MaintenanceIssueDetected".equals(
                triggerEvent.getEventType())) {
            return null;
        }

        // Deterministic ID: same issue -> same recommendation ID.
        String recommendationId =
                "rec_" + triggerEvent.getEventId();

        // Return an existing recommendation if already generated.
        var existing =
                eventService.findById(recommendationId);

        if (existing.isPresent()) {
            return existing.get();
        }

        String recommendationPayload =
                "{\"triggerEventId\":\""
                        + triggerEvent.getEventId()
                        + "\",\"recommendedAction\":"
                        + "\"Resolve maintenance issue before next guest check-in\"}";

        Event recommendation = new Event(
                recommendationId,
                "RecommendationGenerated",
                Instant.now(),
                triggerEvent.getPropertyId(),
                "recommendation_service",
                recommendationPayload
        );

        return eventService.save(recommendation);
    }
}

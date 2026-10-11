
package com.olivia.rental.event.recommendation;

import com.olivia.rental.event.Event;
import com.olivia.rental.event.EventService;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Locale;

@Service
public class RecommendationService {

    private final EventService eventService;
    private final ObjectMapper objectMapper;

    public RecommendationService(
            EventService eventService,
            ObjectMapper objectMapper) {

        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    public Event generateRecommendation(Event triggerEvent) {

        if (!"MaintenanceIssueDetected".equals(
                triggerEvent.getEventType())) {
            return null;
        }

        // Preserve deterministic recommendation IDs.
        String recommendationId =
                "rec_" + triggerEvent.getEventId();

        // Return existing recommendation if already generated.
        var existing = eventService.findById(recommendationId);

        if (existing.isPresent()) {
            return existing.get();
        }

        // Extract issue description from event payload.
        String description = extractDescription(
                triggerEvent.getPayload()
        );

        // Apply rule-based priority classification.
        RecommendationRule rule = classify(description);

        // Build valid JSON without manual string concatenation.
        ObjectNode payload = objectMapper.createObjectNode();

        payload.put(
                "triggerEventId",
                triggerEvent.getEventId()
        );

        payload.put("priority", rule.priority());

        payload.put(
                "recommendedAction",
                rule.action()
        );

        payload.put(
                "explanation",
                rule.explanation()
        );

        Event recommendation = new Event(
                recommendationId,
                "RecommendationGenerated",
                Instant.now(),
                triggerEvent.getPropertyId(),
                "recommendation_service",
                payload.toString()
        );

        return eventService.save(recommendation);
    }

    private String extractDescription(String payload) {

        if (payload == null || payload.isBlank()) {
            return "";
        }

        try {
            JsonNode json = objectMapper.readTree(payload);

            String description = json.path("description").asText("");

            if (!description.isBlank()) {
                return description;
            }

            return json.path("issue").asText("");

        } catch (Exception e) {
            return "";
        }
    }

    private RecommendationRule classify(String description) {

        String issue = description.toLowerCase(Locale.ROOT);

        // Critical: immediate property or guest safety risks.
        if (containsAny(
                issue,
                "water leak",
                "flood",
                "gas leak",
                "fire",
                "smoke",
                "sparking",
                "electrical hazard")) {

            return new RecommendationRule(
                    "CRITICAL",
                    "Contact emergency maintenance immediately",
                    "The reported issue may pose an immediate "
                            + "safety or property damage risk."
            );
        }

        // High: essential guest amenities are unavailable.
        if (containsAny(
                issue,
                "heater",
                "heating",
                "no hot water",
                "air conditioning",
                "ac broken",
                "toilet not working",
                "broken lock")) {

            return new RecommendationRule(
                    "HIGH",
                    "Arrange urgent repair before guest arrival",
                    "An essential guest amenity or security "
                            + "function may be unavailable."
            );
        }

        // Medium: non-emergency maintenance.
        return new RecommendationRule(
                "MEDIUM",
                "Schedule maintenance before next check-in",
                "The issue requires maintenance but does not "
                        + "match a defined urgent-risk rule."
        );
    }

    private boolean containsAny(
            String text,
            String... keywords) {

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private record RecommendationRule(
            String priority,
            String action,
            String explanation
    ) {
    }
}

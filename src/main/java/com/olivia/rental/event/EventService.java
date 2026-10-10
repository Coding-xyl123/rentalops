
package com.olivia.rental.event;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public EventService(
            EventRepository eventRepository,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    public Event save(Event event) {
        Optional<Event> existing =
                eventRepository.findById(event.getEventId());

        if (existing.isPresent()) {
            if (sameEvent(existing.get(), event)) {
                return existing.get();
            }

            throw new EventConflictException(
                    "Event ID already exists with different content: "
                            + event.getEventId()
            );
        }

        return eventRepository.save(event);
    }

    private boolean sameEvent(Event existing, Event incoming) {
        return java.util.Objects.equals(
                    existing.getEventType(),
                    incoming.getEventType())
                && java.util.Objects.equals(
                    existing.getTimestamp(),
                    incoming.getTimestamp())
                && java.util.Objects.equals(
                    existing.getPropertyId(),
                    incoming.getPropertyId())
                && java.util.Objects.equals(
                    existing.getSource(),
                    incoming.getSource())
                && samePayload(
                    existing.getPayload(),
                    incoming.getPayload());
    }

    private boolean samePayload(String first, String second) {
        try {
            return objectMapper.readTree(first)
                    .equals(objectMapper.readTree(second));
        } catch (Exception e) {
            return java.util.Objects.equals(first, second);
        }
    }

    public Optional<Event> findById(String eventId) {
        return eventRepository.findById(eventId);
    }

    public List<Event> getRecentEvents() {
        return eventRepository.findTop50ByOrderByTimestampDesc();
    }

    public List<Event> findDecisionsForRecommendation(
            String recommendationId) {

        return eventRepository
                .findByEventTypeOrderByTimestampDesc("OperatorActionTaken")
                .stream()
                .filter(event ->
                        matchesRecommendation(event, recommendationId))
                .toList();
    }

    public Optional<Event> findTriggerEventForRecommendation(
            String recommendationId) {

        Optional<Event> recommendation = findById(recommendationId);

        if (recommendation.isEmpty()
                || !"RecommendationGenerated".equals(
                        recommendation.get().getEventType())) {
            return Optional.empty();
        }

        try {
            JsonNode payload = objectMapper.readTree(
                    recommendation.get().getPayload()
            );

            String triggerEventId = payload
                    .path("triggerEventId")
                    .asText();

            if (triggerEventId.isBlank()) {
                return Optional.empty();
            }

            return findById(triggerEventId);

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean matchesRecommendation(
            Event event,
            String recommendationId) {

        try {
            JsonNode payload =
                    objectMapper.readTree(event.getPayload());

            return recommendationId.equals(
                    payload.path("recommendationEventId").asText()
            );

        } catch (Exception e) {
            return false;
        }
    }

public Optional<Event> findRecommendationForTrigger(
        String triggerEventId) {

    return eventRepository
            .findByEventTypeOrderByTimestampDesc(
                    "RecommendationGenerated"
            )
            .stream()
            .filter(event -> {
                try {
                    JsonNode payload =
                            objectMapper.readTree(
                                    event.getPayload()
                            );

                    return triggerEventId.equals(
                            payload.path("triggerEventId").asText()
                    );

                } catch (Exception e) {
                    return false;
                }
            })
            .findFirst();
}

}

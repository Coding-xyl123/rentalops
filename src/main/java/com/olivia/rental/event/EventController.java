
package com.olivia.rental.event;

import com.olivia.rental.event.recommendation.RecommendationService;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final RecommendationService recommendationService;

    public EventController(
            EventService eventService,
            RecommendationService recommendationService) {
        this.eventService = eventService;
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public ResponseEntity<EventProcessingResponse> createEvent(
            @RequestBody Event event) {

        // Check whether this event was already processed.
        Optional<Event> existing =
                eventService.findById(event.getEventId());

        if (existing.isPresent()) {

            // EventService.save() validates that the
            // existing event has the same content.
            Event savedEvent = eventService.save(event);

            // Return the original recommendation, if one exists.
            Event recommendation =
                    eventService.findRecommendationForTrigger(
                            savedEvent.getEventId()
                    ).orElse(null);

            return ResponseEntity.ok(
                    new EventProcessingResponse(
                            savedEvent,
                            recommendation
                    )
            );
        }

        // First submission.
        Event savedEvent = eventService.save(event);

        Event recommendation =
                recommendationService.generateRecommendation(savedEvent);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new EventProcessingResponse(
                        savedEvent,
                        recommendation
                ));
    }

    @ExceptionHandler(EventConflictException.class)
    public ResponseEntity<String> handleEventConflict(
            EventConflictException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<Event> getEvent(
            @PathVariable String eventId) {

        return eventService.findById(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{recommendationId}/replay")
    public ResponseEntity<DecisionReplayResponse> getDecisionReplay(
            @PathVariable String recommendationId) {

        Optional<Event> recommendation =
                eventService.findById(recommendationId);

        if (recommendation.isEmpty() ||
                !"RecommendationGenerated".equals(
                        recommendation.get().getEventType())) {
            return ResponseEntity.notFound().build();
        }

        Optional<Event> triggerEvent =
                eventService.findTriggerEventForRecommendation(
                        recommendationId
                );

        if (triggerEvent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Event> decisions =
                eventService.findDecisionsForRecommendation(
                        recommendationId
                );

        DecisionReplayResponse replay =
                new DecisionReplayResponse(
                        triggerEvent.get(),
                        recommendation.get(),
                        decisions
                );

        return ResponseEntity.ok(replay);
    }

    @GetMapping("/{recommendationId}/decisions")
    public List<Event> getDecisionsForRecommendation(
            @PathVariable String recommendationId) {

        return eventService.findDecisionsForRecommendation(
                recommendationId
        );
    }

    @GetMapping
    public List<Event> getRecentEvents() {
        return eventService.getRecentEvents();
    }
}


package com.olivia.rental.event;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final EventProcessingService eventProcessingService;

    public EventController(
            EventService eventService,
            EventProcessingService eventProcessingService) {

        this.eventService = eventService;
        this.eventProcessingService = eventProcessingService;
    }

    // Create a new event or safely handle an identical retry.
    @PostMapping
    public ResponseEntity<EventProcessingResponse> createEvent(
            @RequestBody Event event) {

        EventProcessingService.ProcessingResult result =
                eventProcessingService.process(event);

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity
                .status(status)
                .body(result.response());
    }

    // Same event ID but different content -> HTTP 409.
    @ExceptionHandler(EventConflictException.class)
    public ResponseEntity<String> handleEventConflict(
            EventConflictException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    // Missing event ID -> HTTP 400.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidEvent(
            IllegalArgumentException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(exception.getMessage());
    }

    // Retrieve an event by ID.
    @GetMapping("/{eventId}")
    public ResponseEntity<Event> getEvent(
            @PathVariable String eventId) {

        return eventService.findById(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Retrieve the full decision replay.
    @GetMapping("/{recommendationId}/replay")
    public ResponseEntity<DecisionReplayResponse> getDecisionReplay(
            @PathVariable String recommendationId) {

        Optional<Event> recommendation =
                eventService.findById(recommendationId);

        if (recommendation.isEmpty()
                || !"RecommendationGenerated".equals(
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

    // Retrieve operator decisions for a recommendation.
    @GetMapping("/{recommendationId}/decisions")
    public List<Event> getDecisionsForRecommendation(
            @PathVariable String recommendationId) {

        return eventService.findDecisionsForRecommendation(
                recommendationId
        );
    }

    // Retrieve the 50 most recent events.
    @GetMapping
    public List<Event> getRecentEvents() {

        return eventService.getRecentEvents();
    }
}

package com.olivia.rental.event;

import com.olivia.rental.event.recommendation.RecommendationService;
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
    public ResponseEntity<Event> createEvent(@RequestBody Event event) {

        Event savedEvent = eventService.save(event);

        recommendationService.generateRecommendation(savedEvent);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedEvent);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<Event> getEvent(@PathVariable String eventId) {
        return eventService.findById(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
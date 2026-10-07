package com.olivia.rental.event;

public class EventProcessingResponse {

    private Event event;
    private Event recommendation;

    public EventProcessingResponse(
            Event event,
            Event recommendation) {
        this.event = event;
        this.recommendation = recommendation;
    }

    public Event getEvent() {
        return event;
    }

    public Event getRecommendation() {
        return recommendation;
    }
}
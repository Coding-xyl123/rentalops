package com.olivia.rental.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {

    @Id
    private String eventId;

    private String eventType;

    private Instant timestamp;

    private String propertyId;

    private String source;

    @Column(columnDefinition = "TEXT")
    private String payload;

    public Event() {
    }

    public Event(
            String eventId,
            String eventType,
            Instant timestamp,
            String propertyId,
            String source,
            String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.propertyId = propertyId;
        this.source = source;
        this.payload = payload;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(String propertyId) {
        this.propertyId = propertyId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
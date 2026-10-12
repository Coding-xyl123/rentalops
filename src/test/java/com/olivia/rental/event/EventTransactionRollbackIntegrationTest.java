
package com.olivia.rental.event;

import com.olivia.rental.event.recommendation.RecommendationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class EventTransactionRollbackIntegrationTest {

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private EventRepository eventRepository;

    @MockitoBean
    private RecommendationService recommendationService;

    @Autowired
private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldRollbackEventWhenRecommendationGenerationFails() {

        String eventId = "evt_" + UUID.randomUUID();

        Event event = new Event(
                eventId,
                "MaintenanceIssueDetected",
                Instant.now(),
                "property_001",
                "rollback_test",
                "{\"description\":\"Broken bathroom light\"}"
        );

        // Simulate a failure during recommendation generation.
        when(recommendationService.generateRecommendation(any(Event.class)))
                .thenThrow(new RuntimeException(
                        "Simulated recommendation failure"
                ));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> eventProcessingService.process(event)
        );

        assertEquals(
                "Simulated recommendation failure",
                exception.getMessage()
        );

        // The transaction must roll back the original event.
        assertFalse(
                eventRepository.existsById(eventId),
                "Event should not remain in the database after rollback"
        );

        long outboxCount = outboxEventRepository.findAll()
        .stream()
        .filter(outbox -> eventId.equals(outbox.getEventId()))
        .count();

assertEquals(
        0,
        outboxCount,
        "Outbox record should not exist after transaction rollback"
);
    }
    
}


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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;

@SpringBootTest
class EventTransactionRollbackIntegrationTest {

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private KafkaEventProcessingService kafkaEventProcessingService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedKafkaEventRepository processedRepository;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void shouldRollbackKafkaProcessingWhenRecommendationFails() {

        String eventId = "evt_" + UUID.randomUUID();

        Event event = new Event(
                eventId,
                "MaintenanceIssueDetected",
                Instant.now(),
                "property_001",
                "rollback_test",
                "{\"description\":\"Broken bathroom light\"}"
        );

        // Step 1: HTTP transaction succeeds.
        var result = eventProcessingService.process(event);

        assertTrue(result.created());
        assertTrue(eventRepository.existsById(eventId));

        assertEquals(
                1,
                countOutboxRecords(eventId)
        );

        // Step 2: Simulate recommendation generation failure.
        doThrow(new RuntimeException("Simulated recommendation failure"))
                .when(recommendationService)
                .generateRecommendation(any(Event.class));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> kafkaEventProcessingService.process(event)
        );

        assertEquals(
                "Simulated recommendation failure",
                exception.getMessage()
        );

        // Step 3: Original event and outbox remain committed.
        assertTrue(
                eventRepository.existsById(eventId),
                "Original event should remain after consumer failure"
        );

        assertEquals(
                1,
                countOutboxRecords(eventId),
                "Outbox record should remain after consumer failure"
        );

        // Step 4: Consumer transaction must roll back.
        assertFalse(
                processedRepository.existsById(eventId),
                "Failed processing must not be marked complete"
        );

        assertFalse(
                eventRepository.existsById("rec_" + eventId),
                "Failed recommendation must not be persisted"
        );
    }

    private long countOutboxRecords(String eventId) {
        return outboxEventRepository.findAll()
                .stream()
                .filter(outbox ->
                        eventId.equals(outbox.getEventId()))
                .count();
    }
}

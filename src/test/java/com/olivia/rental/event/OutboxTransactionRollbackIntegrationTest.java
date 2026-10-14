
package com.olivia.rental.event;

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
class OutboxTransactionRollbackIntegrationTest {

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private EventRepository eventRepository;

    @MockitoBean
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldRollbackEventWhenOutboxSaveFails() {

        String eventId = "evt_" + UUID.randomUUID();

        Event event = new Event(
                eventId,
                "MaintenanceIssueDetected",
                Instant.now(),
                "property_001",
                "outbox_rollback_test",
                "{\"description\":\"Broken bathroom light\"}"
        );

        // Simulate an outbox repository failure.
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenThrow(new RuntimeException(
                        "Simulated outbox failure"
                ));

        // The transaction should fail.
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> eventProcessingService.process(event)
        );

        assertEquals(
                "Simulated outbox failure",
                exception.getMessage()
        );

        // The original event must also roll back.
        assertFalse(
                eventRepository.existsById(eventId),
                "Event must not persist when outbox save fails"
        );
    }
}

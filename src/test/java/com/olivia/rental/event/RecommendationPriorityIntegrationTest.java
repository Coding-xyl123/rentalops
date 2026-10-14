
package com.olivia.rental.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RecommendationPriorityIntegrationTest {

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private KafkaEventProcessingService kafkaEventProcessingService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ProcessedKafkaEventRepository processedRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldGenerateCriticalPriorityForWaterLeak()
            throws Exception {

        assertPriority(
                "Water leak under kitchen sink",
                "CRITICAL",
                "Contact emergency maintenance immediately"
        );
    }

    @Test
    void shouldGenerateHighPriorityForBrokenHeater()
            throws Exception {

        assertPriority(
                "Broken heater in living room",
                "HIGH",
                "Arrange urgent repair before guest arrival"
        );
    }

    @Test
    void shouldGenerateMediumPriorityForBrokenLight()
            throws Exception {

        assertPriority(
                "Broken bathroom light",
                "MEDIUM",
                "Schedule maintenance before next check-in"
        );
    }

    @Test
    void identicalRetryShouldReturnSameRecommendation()
            throws Exception {

        Event event = createEvent(
                "Water leak under kitchen sink"
        );

        // First HTTP processing
        var first = eventProcessingService.process(event);

        assertTrue(first.created());
        assertNull(first.response().getRecommendation());

        // Identical HTTP retry before Kafka processing
        var second = eventProcessingService.process(event);

        assertFalse(second.created());
        assertNull(second.response().getRecommendation());

        // First Kafka processing
        kafkaEventProcessingService.process(event);

        String recommendationId = "rec_" + event.getEventId();

        Event recommendation = eventRepository
                .findById(recommendationId)
                .orElseThrow();

        assertEquals(
                "RecommendationGenerated",
                recommendation.getEventType()
        );

        assertTrue(
                processedRepository.existsById(event.getEventId())
        );

        // Duplicate Kafka processing
        kafkaEventProcessingService.process(event);

        // Still the same recommendation
        Event recommendationAfterRetry = eventRepository
                .findById(recommendationId)
                .orElseThrow();

        assertEquals(
                recommendation.getEventId(),
                recommendationAfterRetry.getEventId()
        );

        // HTTP retry after recommendation generation
        var third = eventProcessingService.process(event);

        assertFalse(third.created());
        assertNotNull(third.response().getRecommendation());

        assertEquals(
                recommendationId,
                third.response().getRecommendation().getEventId()
        );
    }

    private void assertPriority(
            String description,
            String expectedPriority,
            String expectedAction) throws Exception {

        Event event = createEvent(description);

        // Step 1: Save event and outbox
        var result = eventProcessingService.process(event);

        assertTrue(result.created());

        // Recommendation is not generated synchronously
        assertNull(result.response().getRecommendation());

        // Step 2: Simulate Kafka consumer processing
        kafkaEventProcessingService.process(event);

        // Step 3: Retrieve persisted recommendation
        Event recommendation = eventRepository
                .findById("rec_" + event.getEventId())
                .orElseThrow();

        assertEquals(
                "RecommendationGenerated",
                recommendation.getEventType()
        );

        JsonNode payload =
                objectMapper.readTree(recommendation.getPayload());

        assertEquals(
                expectedPriority,
                payload.path("priority").asText()
        );

        assertEquals(
                expectedAction,
                payload.path("recommendedAction").asText()
        );

        assertFalse(
                payload.path("explanation").asText().isBlank()
        );

        assertEquals(
                event.getEventId(),
                payload.path("triggerEventId").asText()
        );

        // Step 4: Verify Kafka processing marker
        assertTrue(
                processedRepository.existsById(event.getEventId())
        );
    }

    private Event createEvent(String description)
            throws Exception {

        String payload = objectMapper
                .createObjectNode()
                .put("description", description)
                .toString();

        return new Event(
                "evt_" + UUID.randomUUID(),
                "MaintenanceIssueDetected",
                Instant.now(),
                "property_001",
                "priority_test",
                payload
        );
    }
}

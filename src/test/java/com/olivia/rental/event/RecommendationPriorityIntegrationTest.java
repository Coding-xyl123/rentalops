
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

        var first = eventProcessingService.process(event);
        var second = eventProcessingService.process(event);

        assertTrue(first.created());
        assertFalse(second.created());

        assertNotNull(first.response().getRecommendation());
        assertNotNull(second.response().getRecommendation());

        assertEquals(
                first.response().getRecommendation().getEventId(),
                second.response().getRecommendation().getEventId()
        );
    }

    private void assertPriority(
            String description,
            String expectedPriority,
            String expectedAction) throws Exception {

        Event event = createEvent(description);

        var result = eventProcessingService.process(event);

        assertTrue(result.created());

        Event recommendation =
                result.response().getRecommendation();

        assertNotNull(recommendation);

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

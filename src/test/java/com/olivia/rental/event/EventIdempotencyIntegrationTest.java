
package com.olivia.rental.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class EventIdempotencyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void repeatedRequestReturnsSameRecommendation() throws Exception {

        String eventId = "evt_" + UUID.randomUUID();

        Event event = new Event(
                eventId,
                "MaintenanceIssueDetected",
                Instant.now(),
                "property_001",
                "integration_test",
                "{\"description\":\"Bathroom light is broken\"}"
        );

        String requestBody = objectMapper.writeValueAsString(event);

        // First submission
        HttpResponse<String> first = postEvent(requestBody);

        assertEquals(201, first.statusCode());

        JsonNode firstResponse =
                objectMapper.readTree(first.body());

        String firstRecommendationId = firstResponse
                .path("recommendation")
                .path("eventId")
                .asText();

        assertFalse(firstRecommendationId.isBlank());

        // Identical retry
        HttpResponse<String> second = postEvent(requestBody);

        assertEquals(200, second.statusCode());

        JsonNode secondResponse =
                objectMapper.readTree(second.body());

        String secondRecommendationId = secondResponse
                .path("recommendation")
                .path("eventId")
                .asText();

        assertEquals(
                firstRecommendationId,
                secondRecommendationId,
                "Retry should return the original recommendation"
        );

        // Verify only one recommendation references this issue.
        long recommendationCount = eventRepository
                .findByEventTypeOrderByTimestampDesc(
                        "RecommendationGenerated"
                )
                .stream()
                .filter(recommendation -> {
                    try {
                        JsonNode payload = objectMapper.readTree(
                                recommendation.getPayload()
                        );

                        return eventId.equals(
                                payload.path("triggerEventId").asText()
                        );
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();

        assertEquals(
                1,
                recommendationCount,
                "Only one recommendation should be generated"
        );

        // Same event ID, different content
        Event conflictingEvent = new Event(
                eventId,
                "MaintenanceIssueDetected",
                event.getTimestamp(),
                "property_001",
                "integration_test",
                "{\"description\":\"Kitchen sink is leaking\"}"
        );

        HttpResponse<String> conflict = postEvent(
                objectMapper.writeValueAsString(conflictingEvent)
        );

        assertEquals(
                409,
                conflict.statusCode(),
                "Conflicting retry should return HTTP 409"
        );
    }

    private HttpResponse<String> postEvent(String requestBody)
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/events"
                ))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }
}

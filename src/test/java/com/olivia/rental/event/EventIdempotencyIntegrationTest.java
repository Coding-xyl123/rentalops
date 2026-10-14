
package com.olivia.rental.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

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

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedKafkaEventRepository processedRepository;

    @Autowired
    private KafkaEventProcessingService kafkaEventProcessingService;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void repeatedRequestAndKafkaProcessingShouldBeIdempotent()
            throws Exception {

        String eventId = "evt_" + UUID.randomUUID();
        String recommendationId = "rec_" + eventId;

        Event event = new Event(
                eventId,
                "MaintenanceIssueDetected",
                Instant.now(),
                "property_001",
                "integration_test",
                "{\"description\":\"Bathroom light is broken\"}"
        );

        String requestBody =
                objectMapper.writeValueAsString(event);

        // 1. First HTTP request
        HttpResponse<String> first = postEvent(requestBody);

        assertEquals(201, first.statusCode());

        JsonNode firstResponse =
                objectMapper.readTree(first.body());

        assertEquals(
                eventId,
                firstResponse.path("event").path("eventId").asText()
        );

        assertTrue(
                firstResponse.path("recommendation").isNull()
        );

        // 2. Confirm exactly one outbox record exists
        assertEquals(1, countOutboxRecords(eventId));

        // 3. Identical HTTP retry
        HttpResponse<String> second = postEvent(requestBody);

        assertEquals(200, second.statusCode());

        JsonNode secondResponse =
                objectMapper.readTree(second.body());

        assertEquals(
                eventId,
                secondResponse.path("event").path("eventId").asText()
        );

        // Retry must not create another outbox record
        assertEquals(1, countOutboxRecords(eventId));

        // 4. Simulate first Kafka delivery
        Event savedEvent = eventRepository.findById(eventId)
                .orElseThrow();

        kafkaEventProcessingService.process(savedEvent);

        assertTrue(
                eventRepository.findById(recommendationId).isPresent()
        );

        assertTrue(
                processedRepository.existsById(eventId)
        );

        // 5. Simulate duplicate Kafka delivery
        kafkaEventProcessingService.process(savedEvent);

        // Still exactly one recommendation
        assertEquals(
                1,
                countRecommendations(eventId),
                "Duplicate Kafka delivery must not create duplicates"
        );

        // Still exactly one processed marker
        assertTrue(processedRepository.existsById(eventId));

        // 6. Identical HTTP retry after processing
        HttpResponse<String> third = postEvent(requestBody);

        assertEquals(200, third.statusCode());

        JsonNode thirdResponse =
                objectMapper.readTree(third.body());

        assertEquals(
                recommendationId,
                thirdResponse
                        .path("recommendation")
                        .path("eventId")
                        .asText()
        );

        assertEquals(1, countOutboxRecords(eventId));
        assertEquals(1, countRecommendations(eventId));

        // 7. Conflicting retry
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

        // Conflict must not change existing data
        assertEquals(1, countOutboxRecords(eventId));
        assertEquals(1, countRecommendations(eventId));
    }

    private long countOutboxRecords(String eventId) {
        return outboxEventRepository.findAll()
                .stream()
                .filter(outbox ->
                        eventId.equals(outbox.getEventId()))
                .count();
    }

    private long countRecommendations(String eventId) {
        return eventRepository
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

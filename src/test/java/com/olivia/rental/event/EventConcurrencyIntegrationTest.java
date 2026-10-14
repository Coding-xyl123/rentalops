
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class EventConcurrencyIntegrationTest {

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
    void concurrentRequestsShouldCreateOnlyOneEventAndOutbox()
            throws Exception {

        Event event = createEvent();
        String eventId = event.getEventId();
        String requestBody = objectMapper.writeValueAsString(event);

        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(2)) {

            CompletableFuture<HttpResponse<String>> first =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            start.await();
                            return postEvent(requestBody);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, executor);

            CompletableFuture<HttpResponse<String>> second =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            start.await();
                            return postEvent(requestBody);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, executor);

            start.countDown();

            HttpResponse<String> response1 =
                    first.get(15, TimeUnit.SECONDS);

            HttpResponse<String> response2 =
                    second.get(15, TimeUnit.SECONDS);

            // Exactly one request creates the event.
            assertTrue(
                    (response1.statusCode() == 201
                            && response2.statusCode() == 200)
                    ||
                    (response1.statusCode() == 200
                            && response2.statusCode() == 201),
                    "Expected one 201 and one 200, but got "
                            + response1.statusCode()
                            + " and " + response2.statusCode()
            );

            JsonNode body1 = objectMapper.readTree(response1.body());
            JsonNode body2 = objectMapper.readTree(response2.body());

            assertEquals(
                    eventId,
                    body1.path("event").path("eventId").asText()
            );

            assertEquals(
                    eventId,
                    body2.path("event").path("eventId").asText()
            );

            // No synchronous recommendation.
            assertTrue(body1.path("recommendation").isNull());
            assertTrue(body2.path("recommendation").isNull());

            assertTrue(eventRepository.existsById(eventId));

            assertEquals(
                    1,
                    countOutbox(eventId),
                    "Concurrent HTTP requests must create one outbox record"
            );

            OutboxEvent outbox = outboxEventRepository.findAll()
                    .stream()
                    .filter(item -> eventId.equals(item.getEventId()))
                    .findFirst()
                    .orElseThrow();

            JsonNode envelope = objectMapper.readTree(outbox.getPayload());

            assertEquals(eventId, envelope.path("eventId").asText());
            assertEquals(
                    "MaintenanceIssueDetected",
                    envelope.path("eventType").asText()
            );
            assertEquals(
                    "property_001",
                    envelope.path("propertyId").asText()
            );
        }
    }

    @Test
    void concurrentKafkaProcessingShouldCreateOnlyOneRecommendation()
            throws Exception {

        Event event = createEvent();
        String eventId = event.getEventId();

        // Persist event and outbox before Kafka processing.
        HttpResponse<String> response =
                postEvent(objectMapper.writeValueAsString(event));

        assertEquals(201, response.statusCode());

        Event savedEvent = eventRepository.findById(eventId)
                .orElseThrow();

        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(2)) {

            CompletableFuture<Void> first =
                    CompletableFuture.runAsync(() -> {
                        try {
                            start.await();
                            kafkaEventProcessingService.process(savedEvent);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, executor);

            CompletableFuture<Void> second =
                    CompletableFuture.runAsync(() -> {
                        try {
                            start.await();
                            kafkaEventProcessingService.process(savedEvent);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, executor);

            start.countDown();

            CompletableFuture.allOf(first, second)
                    .get(15, TimeUnit.SECONDS);
        }

        String recommendationId = "rec_" + eventId;

        Event recommendation = eventRepository
                .findById(recommendationId)
                .orElseThrow();

        assertEquals(
                "RecommendationGenerated",
                recommendation.getEventType()
        );

        assertEquals(
                1,
                eventRepository
                        .findByEventTypeOrderByTimestampDesc(
                                "RecommendationGenerated"
                        )
                        .stream()
                        .filter(item -> {
                            try {
                                JsonNode payload =
                                        objectMapper.readTree(
                                                item.getPayload()
                                        );

                                return eventId.equals(
                                        payload.path("triggerEventId").asText()
                                );
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .count(),
                "Concurrent Kafka processing must create one recommendation"
        );

        assertTrue(
                processedRepository.existsById(eventId),
                "Kafka processing marker must be persisted"
        );

        assertEquals(
                1,
                countOutbox(eventId),
                "Kafka processing must not duplicate the original outbox"
        );
    }

    private Event createEvent() {
        return new Event(
                "evt_" + UUID.randomUUID(),
                "MaintenanceIssueDetected",
                Instant.now(),
                "property_001",
                "concurrency_test",
                "{\"description\":\"Bathroom light is broken\"}"
        );
    }

    private long countOutbox(String eventId) {
        return outboxEventRepository.findAll()
                .stream()
                .filter(item -> eventId.equals(item.getEventId()))
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

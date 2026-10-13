
package com.olivia.rental.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

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
private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EventRepository eventRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void concurrentRequestsShouldCreateOnlyOneRecommendation()
            throws Exception {

        String eventId = "evt_" + UUID.randomUUID();

        Event event = new Event(
                eventId,
                "MaintenanceIssueDetected",
                Instant.now(),
                "property_001",
                "concurrency_test",
                "{\"description\":\"Bathroom light is broken\"}"
        );

        String requestBody =
                objectMapper.writeValueAsString(event);

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

            // Release both requests at nearly the same time.
            start.countDown();

            HttpResponse<String> response1 =
                    first.get(15, TimeUnit.SECONDS);

            HttpResponse<String> response2 =
                    second.get(15, TimeUnit.SECONDS);

            assertTrue(
                    isSuccess(response1.statusCode()),
                    "First request failed with HTTP "
                            + response1.statusCode()
                            + ": " + response1.body()
            );

            assertTrue(
                    isSuccess(response2.statusCode()),
                    "Second request failed with HTTP "
                            + response2.statusCode()
                            + ": " + response2.body()
            );

            JsonNode body1 =
                    objectMapper.readTree(response1.body());

            JsonNode body2 =
                    objectMapper.readTree(response2.body());

            String recommendationId1 = body1
                    .path("recommendation")
                    .path("eventId")
                    .asText();

            String recommendationId2 = body2
                    .path("recommendation")
                    .path("eventId")
                    .asText();

            assertFalse(recommendationId1.isBlank());
            assertEquals(
                    recommendationId1,
                    recommendationId2,
                    "Both requests should return the same recommendation"
            );

            long recommendationCount = eventRepository
                    .findByEventTypeOrderByTimestampDesc(
                            "RecommendationGenerated"
                    )
                    .stream()
                    .filter(recommendation -> {
                        try {
                            JsonNode payload =
                                    objectMapper.readTree(
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
                    "Only one recommendation should be stored"
            );
            long outboxCount = outboxEventRepository
        .findAll()
        .stream()
        .filter(outbox -> eventId.equals(outbox.getEventId()))
        .count();

assertEquals(
        1,
        outboxCount,
        "Only one outbox record should be stored for concurrent requests"
);
OutboxEvent storedOutbox = outboxEventRepository
        .findAll()
        .stream()
        .filter(outbox -> eventId.equals(outbox.getEventId()))
        .findFirst()
        .orElseThrow();

assertTrue(
        storedOutbox.getPayload().contains("\"eventId\""),
        "Outbox payload should contain eventId"
);

assertTrue(
        storedOutbox.getPayload().contains("\"eventType\""),
        "Outbox payload should contain eventType"
);

assertTrue(
        storedOutbox.getPayload().contains("\"propertyId\""),
        "Outbox payload should contain propertyId"
);

        }
    }

    private boolean isSuccess(int statusCode) {
        return statusCode == 200 || statusCode == 201;
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

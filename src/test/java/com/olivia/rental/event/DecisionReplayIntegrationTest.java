
package com.olivia.rental.event;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class DecisionReplayIntegrationTest {

   @LocalServerPort
private int port;

    @Autowired
    private EventService eventService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void replayReturnsLinkedIssueRecommendationAndDecision()
            throws Exception {

        String suffix = UUID.randomUUID().toString();
        String propertyId = "property_test_" + suffix;

        Event issue = new Event(
                "evt_" + UUID.randomUUID(),
                "MaintenanceIssueDetected",
                Instant.now(),
                propertyId,
                "integration_test",
                "{\"description\":\"Bathroom light broken\"}"
        );

        eventService.save(issue);

        Event recommendation = new Event(
                "rec_" + UUID.randomUUID(),
                "RecommendationGenerated",
                Instant.now(),
                propertyId,
                "integration_test",
                "{\"triggerEventId\":\"" + issue.getEventId()
                        + "\",\"recommendedAction\":\"Replace bulb\"}"
        );

        eventService.save(recommendation);

        Event decision = new Event(
                "evt_" + UUID.randomUUID(),
                "OperatorActionTaken",
                Instant.now(),
                propertyId,
                "integration_test",
                "{\"recommendationEventId\":\""
                        + recommendation.getEventId()
                        + "\",\"decision\":\"ACCEPTED\","
                        + "\"action\":\"Replace bulb\"}"
        );

        eventService.save(decision);

        HttpClient client = HttpClient.newHttpClient();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(
                "http://localhost:" + port
                + "/events/" + recommendation.getEventId()
                + "/replay"
        ))
        .GET()
        .build();

HttpResponse<String> response = client.send(
        request,
        HttpResponse.BodyHandlers.ofString()
);

assertEquals(200, response.statusCode());

JsonNode replay = objectMapper.readTree(response.body());

        assertEquals(
                issue.getEventId(),
                replay.path("triggerEvent").path("eventId").asText()
        );

        assertEquals(
                recommendation.getEventId(),
                replay.path("recommendation").path("eventId").asText()
        );

        assertEquals(
                1,
                replay.path("decisions").size()
        );

        assertEquals(
                decision.getEventId(),
                replay.path("decisions").get(0).path("eventId").asText()
        );
    }
}

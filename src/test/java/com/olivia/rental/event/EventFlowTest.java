package com.olivia.rental.event;

import com.olivia.rental.event.recommendation.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
@SpringBootTest
@AutoConfigureMockMvc
class EventFlowIntegrationTest {
   
   
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RecommendationService recommendationService;

    @BeforeEach
    void cleanDatabase() {
        eventRepository.deleteAll();
    }
    @Test
void guestQuestionShouldNotGenerateRecommendation() {

    Event guestQuestion = new Event(
            "test_evt_002",
            "GuestQuestionReceived",
            Instant.now(),
            "property_001",
            "test",
            "{\"question\":\"Where do I park?\"}"
    );

    Event savedEvent = eventService.save(guestQuestion);

    Event recommendation =
            recommendationService.generateRecommendation(savedEvent);

    assertNull(recommendation);

    List<Event> events = eventRepository.findAll();

    assertEquals(1, events.size());
    assertTrue(eventRepository.findById("test_evt_002").isPresent());
}
    
    @Test
    void maintenanceIssueShouldGenerateRecommendation() {

        // 1. Create a maintenance issue
        Event maintenanceIssue = new Event(
                "test_evt_001",
                "MaintenanceIssueDetected",
                Instant.now(),
                "property_001",
                "test",
                "{\"issue\":\"Bathroom light is not working\"}"
        );

        // 2. Persist the incoming event
        Event savedIssue = eventService.save(maintenanceIssue);

        // 3. Generate recommendation
        Event recommendation =
                recommendationService.generateRecommendation(savedIssue);

        // 4. Verify recommendation was generated
        assertNotNull(recommendation);

        assertEquals(
                "RecommendationGenerated",
                recommendation.getEventType()
        );

        assertEquals(
                "property_001",
                recommendation.getPropertyId()
        );

        // 5. Verify causal relationship
        assertTrue(
                recommendation.getPayload()
                        .contains("\"triggerEventId\":\"test_evt_001\"")
        );

        // 6. Verify both events actually exist in PostgreSQL
        List<Event> events = eventRepository.findAll();

        assertEquals(2, events.size());

        assertTrue(
                eventRepository.findById("test_evt_001").isPresent()
        );

        assertTrue(
                eventRepository.findById(recommendation.getEventId()).isPresent()
        );
    }
    @Test
void postMaintenanceIssueShouldPersistIssueAndGenerateRecommendation()
        throws Exception {

    String requestBody = """
            {
              "eventId": "http_evt_001",
              "eventType": "MaintenanceIssueDetected",
              "timestamp": "2026-10-06T19:00:00Z",
              "propertyId": "property_001",
              "source": "test",
              "payload": "{\\"issue\\":\\"Kitchen sink is leaking\\"}"
            }
            """;

    mockMvc.perform(
            post("/events")
                    .contentType("application/json")
                    .content(requestBody)
    )
    .andExpect(status().isCreated());

    List<Event> events = eventRepository.findAll();

    assertEquals(2, events.size());

    assertTrue(
            eventRepository.findById("http_evt_001").isPresent()
    );

    Event recommendation = events.stream()
            .filter(event ->
                    "RecommendationGenerated".equals(event.getEventType()))
            .findFirst()
            .orElseThrow();

    assertEquals(
            "property_001",
            recommendation.getPropertyId()
    );

    assertTrue(
            recommendation.getPayload()
                    .contains("\"triggerEventId\":\"http_evt_001\"")
    );
}
@Test
void getExistingEventShouldReturnEvent() throws Exception {

    Event event = new Event(
            "get_evt_001",
            "GuestQuestionReceived",
            Instant.now(),
            "property_001",
            "test",
            "{\"question\":\"Where is the parking?\"}"
    );

    eventService.save(event);

    mockMvc.perform(get("/events/get_evt_001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("get_evt_001"))
            .andExpect(jsonPath("$.eventType").value("GuestQuestionReceived"))
            .andExpect(jsonPath("$.propertyId").value("property_001"));
}
   @Test
void getMissingEventShouldReturn404() throws Exception {

    mockMvc.perform(get("/events/does_not_exist"))
            .andExpect(status().isNotFound());
}
}
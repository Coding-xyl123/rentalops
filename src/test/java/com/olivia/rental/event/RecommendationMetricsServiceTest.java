
package com.olivia.rental.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RecommendationMetricsServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private RecommendationMetricsService metricsService;

    @Test
    void acceptedDecisionIsCounted() {
        String suffix = UUID.randomUUID().toString();
        Instant recommendationTime = Instant.now();

        Event recommendation = new Event(
                "rec_metrics_" + suffix,
                "RecommendationGenerated",
                recommendationTime,
                "property_metrics_" + suffix,
                "integration_test",
                "{\"recommendedAction\":\"Fix bathroom light\"}"
        );

        eventService.save(recommendation);

        Event action = new Event(
                "evt_metrics_" + suffix,
                "OperatorActionTaken",
                recommendationTime.plusSeconds(10),
                "property_metrics_" + suffix,
                "integration_test",
                "{\"recommendationEventId\":\""
                        + recommendation.getEventId()
                        + "\",\"decision\":\"ACCEPTED\"}"
        );

        eventService.save(action);

        RecommendationMetricsResponse metrics =
                metricsService.calculateMetrics();

        assertTrue(metrics.totalRecommendations() >= 1);
        assertTrue(metrics.decidedRecommendations() >= 1);
        assertTrue(metrics.acceptedRecommendations() >= 1);

        assertEquals(
                metrics.decidedRecommendations(),
                metrics.acceptedRecommendations()
                        + metrics.modifiedRecommendations()
                        + metrics.rejectedRecommendations()
        );

        assertEquals(
                (double) metrics.decidedRecommendations()
                        / metrics.totalRecommendations(),
                metrics.decisionRate(),
                0.000001
        );
    }

@Test
void multipleActionsForSameRecommendationCountOnlyOnce() {
    String suffix = UUID.randomUUID().toString();
    Instant recommendationTime = Instant.now();

    Event recommendation = new Event(
            "rec_duplicate_" + suffix,
            "RecommendationGenerated",
            recommendationTime,
            "property_duplicate_" + suffix,
            "integration_test",
            "{\"recommendedAction\":\"Repair heater\"}"
    );

    eventService.save(recommendation);

    RecommendationMetricsResponse before =
            metricsService.calculateMetrics();

    Event firstAction = new Event(
            "evt_first_" + suffix,
            "OperatorActionTaken",
            recommendationTime.plusSeconds(10),
            "property_duplicate_" + suffix,
            "integration_test",
            "{\"recommendationEventId\":\""
                    + recommendation.getEventId()
                    + "\",\"decision\":\"ACCEPTED\"}"
    );

    Event secondAction = new Event(
            "evt_second_" + suffix,
            "OperatorActionTaken",
            recommendationTime.plusSeconds(20),
            "property_duplicate_" + suffix,
            "integration_test",
            "{\"recommendationEventId\":\""
                    + recommendation.getEventId()
                    + "\",\"decision\":\"REJECTED\"}"
    );

    eventService.save(firstAction);
    eventService.save(secondAction);

    RecommendationMetricsResponse after =
            metricsService.calculateMetrics();

    assertEquals(
            before.decidedRecommendations() + 1,
            after.decidedRecommendations()
    );

    assertEquals(
            before.acceptedRecommendations() + 1,
            after.acceptedRecommendations()
    );

    assertEquals(
            before.rejectedRecommendations(),
            after.rejectedRecommendations()
    );
}

@Test
void missingDecisionIsNotCounted() {
    String suffix = UUID.randomUUID().toString();
    Instant recommendationTime = Instant.now();

    Event recommendation = new Event(
            "rec_missing_" + suffix,
            "RecommendationGenerated",
            recommendationTime,
            "property_missing_" + suffix,
            "integration_test",
            "{\"recommendedAction\":\"Inspect plumbing\"}"
    );

    eventService.save(recommendation);

    RecommendationMetricsResponse before =
            metricsService.calculateMetrics();

    Event action = new Event(
            "evt_missing_" + suffix,
            "OperatorActionTaken",
            recommendationTime.plusSeconds(10),
            "property_missing_" + suffix,
            "integration_test",
            "{\"recommendationEventId\":\""
                    + recommendation.getEventId()
                    + "\",\"actionTaken\":\"Inspected plumbing\"}"
    );

    eventService.save(action);

    RecommendationMetricsResponse after =
            metricsService.calculateMetrics();

    assertEquals(
            before.totalRecommendations(),
            after.totalRecommendations()
    );

    assertEquals(
            before.decidedRecommendations(),
            after.decidedRecommendations()
    );

    assertEquals(
            before.acceptedRecommendations(),
            after.acceptedRecommendations()
    );
}

@Test
void invalidDecisionIsNotCounted() {
    String suffix = UUID.randomUUID().toString();
    Instant recommendationTime = Instant.now();

    Event recommendation = new Event(
            "rec_invalid_" + suffix,
            "RecommendationGenerated",
            recommendationTime,
            "property_invalid_" + suffix,
            "integration_test",
            "{\"recommendedAction\":\"Inspect heater\"}"
    );

    eventService.save(recommendation);

    RecommendationMetricsResponse before =
            metricsService.calculateMetrics();

    Event action = new Event(
            "evt_invalid_" + suffix,
            "OperatorActionTaken",
            recommendationTime.plusSeconds(10),
            "property_invalid_" + suffix,
            "integration_test",
            "{\"recommendationEventId\":\""
                    + recommendation.getEventId()
                    + "\",\"decision\":\"PENDING\"}"
    );

    eventService.save(action);

    RecommendationMetricsResponse after =
            metricsService.calculateMetrics();

    assertEquals(
            before.decidedRecommendations(),
            after.decidedRecommendations()
    );

    assertEquals(
            before.acceptedRecommendations(),
            after.acceptedRecommendations()
    );

    assertEquals(
            before.modifiedRecommendations(),
            after.modifiedRecommendations()
    );

    assertEquals(
            before.rejectedRecommendations(),
            after.rejectedRecommendations()
    );
}

@Test
void averageTimeToDecisionIsNonNegative() {
    RecommendationMetricsResponse metrics =
            metricsService.calculateMetrics();

    if (metrics.decidedRecommendations() > 0) {
        assertNotNull(metrics.averageTimeToDecisionSeconds());

        assertTrue(
                metrics.averageTimeToDecisionSeconds() >= 0.0
        );
    } else {
        assertNull(metrics.averageTimeToDecisionSeconds());
    }
}



}

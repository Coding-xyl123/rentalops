
package com.olivia.rental.event;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationMetricsUnitTest {

    private final EventRepository repository =
            mock(EventRepository.class);

    private final RecommendationMetricsService service =
            new RecommendationMetricsService(
                    repository,
                    new ObjectMapper()
            );

    @Test
    void calculatesExactDecisionTimeAndRates() {
        Instant start = Instant.parse(
                "2026-10-14T12:00:00Z"
        );

        Event recommendation = new Event(
                "rec_test_1",
                "RecommendationGenerated",
                start,
                "property_1",
                "unit_test",
                "{}"
        );

        Event action = new Event(
                "evt_test_1",
                "OperatorActionTaken",
                start.plusSeconds(10),
                "property_1",
                "unit_test",
                "{\"recommendationEventId\":\"rec_test_1\","
                        + "\"decision\":\"ACCEPTED\"}"
        );

        when(repository.findByEventTypeOrderByTimestampDesc(
                "RecommendationGenerated"
        )).thenReturn(List.of(recommendation));

        when(repository.findByEventTypeOrderByTimestampDesc(
                "OperatorActionTaken"
        )).thenReturn(List.of(action));

        RecommendationMetricsResponse metrics =
                service.calculateMetrics();

        assertEquals(1, metrics.totalRecommendations());
        assertEquals(1, metrics.decidedRecommendations());
        assertEquals(1, metrics.acceptedRecommendations());
        assertEquals(0, metrics.modifiedRecommendations());
        assertEquals(0, metrics.rejectedRecommendations());

        assertEquals(1.0, metrics.decisionRate(), 0.000001);
        assertEquals(1.0, metrics.acceptanceRate(), 0.000001);
        assertEquals(0.0, metrics.modificationRate(), 0.000001);
        assertEquals(0.0, metrics.rejectionRate(), 0.000001);

        assertNotNull(metrics.averageTimeToDecisionSeconds());
        assertEquals(
                10.0,
                metrics.averageTimeToDecisionSeconds(),
                0.000001
        );
    }

    @Test
    void zeroRecommendationsProduceZeroRates() {
        when(repository.findByEventTypeOrderByTimestampDesc(
                "RecommendationGenerated"
        )).thenReturn(List.of());

        when(repository.findByEventTypeOrderByTimestampDesc(
                "OperatorActionTaken"
        )).thenReturn(List.of());

        RecommendationMetricsResponse metrics =
                service.calculateMetrics();

        assertEquals(0, metrics.totalRecommendations());
        assertEquals(0, metrics.decidedRecommendations());
        assertEquals(0.0, metrics.decisionRate());
        assertEquals(0.0, metrics.acceptanceRate());
        assertNull(metrics.averageTimeToDecisionSeconds());
    }

    @Test
    void recommendationWithoutDecisionHasNullAverage() {
        Event recommendation = new Event(
                "rec_no_decision",
                "RecommendationGenerated",
                Instant.parse("2026-10-14T12:00:00Z"),
                "property_1",
                "unit_test",
                "{}"
        );

        when(repository.findByEventTypeOrderByTimestampDesc(
                "RecommendationGenerated"
        )).thenReturn(List.of(recommendation));

        when(repository.findByEventTypeOrderByTimestampDesc(
                "OperatorActionTaken"
        )).thenReturn(List.of());

        RecommendationMetricsResponse metrics =
                service.calculateMetrics();

        assertEquals(1, metrics.totalRecommendations());
        assertEquals(0, metrics.decidedRecommendations());
        assertEquals(0.0, metrics.decisionRate());
        assertNull(metrics.averageTimeToDecisionSeconds());
    }
}

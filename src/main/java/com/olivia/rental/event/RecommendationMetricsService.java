package com.olivia.rental.event;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationMetricsService {

    private static final Set<String> VALID_DECISIONS =
            Set.of("ACCEPTED", "MODIFIED", "REJECTED");

    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public RecommendationMetricsService(
            EventRepository eventRepository,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    public RecommendationMetricsResponse calculateMetrics() {

        List<Event> recommendations = eventRepository
                .findByEventTypeOrderByTimestampDesc(
                        "RecommendationGenerated");

        List<Event> actions = eventRepository
                .findByEventTypeOrderByTimestampDesc(
                        "OperatorActionTaken");

        Map<String, Event> recommendationsById =
                recommendations.stream()
                        .collect(Collectors.toMap(
                                Event::getEventId,
                                event -> event
                        ));

        Map<String, DecisionRecord> firstDecisions =
                new HashMap<>();

        for (Event action : actions) {

            if (action.getTimestamp() == null) {
                continue;
            }

            try {
                JsonNode payload =
                        objectMapper.readTree(action.getPayload());

                String recommendationId = payload
                        .path("recommendationEventId")
                        .asText();

                String decision = payload
                        .path("decision")
                        .asText();

                if (!VALID_DECISIONS.contains(decision)) {
                    continue;
                }

                Event recommendation =
                        recommendationsById.get(recommendationId);

                if (recommendation == null
                        || recommendation.getTimestamp() == null) {
                    continue;
                }

                Instant actionTime = action.getTimestamp();
                Instant recommendationTime =
                        recommendation.getTimestamp();

                if (actionTime.isBefore(recommendationTime)) {
                    continue;
                }

                DecisionRecord candidate =
                        new DecisionRecord(decision, actionTime);

                firstDecisions.merge(
                        recommendationId,
                        candidate,
                        (existing, incoming) ->
                                incoming.timestamp()
                                        .isBefore(existing.timestamp())
                                        ? incoming : existing
                );

            } catch (Exception ignored) {
                // Malformed historical payloads are excluded.
            }
        }

        long total = recommendationsById.size();
        long decided = firstDecisions.size();

        long accepted = firstDecisions.values().stream()
                .filter(d -> d.decision().equals("ACCEPTED"))
                .count();

        long modified = firstDecisions.values().stream()
                .filter(d -> d.decision().equals("MODIFIED"))
                .count();

        long rejected = firstDecisions.values().stream()
                .filter(d -> d.decision().equals("REJECTED"))
                .count();

        double totalSeconds = 0;

        for (Map.Entry<String, DecisionRecord> entry :
                firstDecisions.entrySet()) {

            Instant recommendationTime =
                    recommendationsById.get(entry.getKey())
                            .getTimestamp();

            totalSeconds += Duration.between(
                    recommendationTime,
                    entry.getValue().timestamp()
            ).toMillis() / 1000.0;
        }

        Double averageTimeToDecision =
                decided == 0 ? null : totalSeconds / decided;

        return new RecommendationMetricsResponse(
                total,
                decided,
                accepted,
                modified,
                rejected,
                rate(decided, total),
                rate(accepted, decided),
                rate(modified, decided),
                rate(rejected, decided),
                averageTimeToDecision
        );
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0
                ? 0.0
                : (double) numerator / denominator;
    }

    private record DecisionRecord(
            String decision,
            Instant timestamp
    ) {
    }
}
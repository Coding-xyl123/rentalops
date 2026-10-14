package com.olivia.rental.event;

public record RecommendationMetricsResponse(
        long totalRecommendations,
        long decidedRecommendations,
        long acceptedRecommendations,
        long modifiedRecommendations,
        long rejectedRecommendations,
        double decisionRate,
        double acceptanceRate,
        double modificationRate,
        double rejectionRate,
        Double averageTimeToDecisionSeconds
) {
}
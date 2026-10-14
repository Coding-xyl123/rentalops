package com.olivia.rental.event;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
public class RecommendationMetricsController {

    private final RecommendationMetricsService metricsService;

    public RecommendationMetricsController(
            RecommendationMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/recommendations")
    public RecommendationMetricsResponse getRecommendationMetrics() {
        return metricsService.calculateMetrics();
    }
}
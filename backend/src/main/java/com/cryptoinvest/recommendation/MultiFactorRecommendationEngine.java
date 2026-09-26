package com.cryptoinvest.recommendation;

import static com.cryptoinvest.recommendation.RecommendationModels.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic weighted score; unavailable categories are omitted and the remaining weights rescaled. */
public final class MultiFactorRecommendationEngine {
    public static final String RULE_VERSION = "rules-1.0";
    private static final Map<String, Integer> WEIGHTS = Map.of("TECHNICAL", 30, "MARKET_REGIME", 20, "FUNDAMENTAL_ONCHAIN", 20, "FLOW_DERIVATIVES", 15, "RELATIVE_STRENGTH", 10, "RISK", 5);
    public int score(Map<String, Integer> categoryScores) {
        BigDecimal weighted = BigDecimal.ZERO;
        int available = 0;
        for (var entry : WEIGHTS.entrySet()) {
            Integer value = categoryScores.get(entry.getKey());
            if (value != null) {
                if (value < -100 || value > 100) throw new IllegalArgumentException("Factor score out of range");
                weighted = weighted.add(BigDecimal.valueOf((long) value * entry.getValue()));
                available += entry.getValue();
            }
        }
        if (available == 0) throw new IllegalArgumentException("At least one factor score is required");
        return weighted.divide(BigDecimal.valueOf(available), 0, RoundingMode.HALF_UP).intValueExact();
    }
    public ConfidenceBreakdown confidence(Map<String, MetricObservation> metrics, List<Integer> directions, Instant now) {
        int expected = Math.max(1, metrics.size());
        long available = metrics.values().stream().filter(m -> m.status() == MetricStatus.AVAILABLE).count();
        int coverage = (int) (available * 100 / expected);
        int freshness = metrics.values().stream().filter(m -> m.status() == MetricStatus.AVAILABLE).mapToInt(m -> freshness(m, now)).sum() / (int) Math.max(1, available);
        int agreement = directions.isEmpty() ? 0 : (int) (Math.max(java.util.Collections.frequency(directions, 1), java.util.Collections.frequency(directions, -1)) * 100L / directions.size());
        int quality = (int) metrics.values().stream().filter(m -> m.status() == MetricStatus.OUTLIER || m.status() == MetricStatus.INVALID).count() * 100 / expected;
        int score = coverage * (freshness * 40 + agreement * 40 + (100 - quality) * 20) / 10000;
        return new ConfidenceBreakdown(coverage, freshness, agreement, 100 - quality, score);
    }
    private int freshness(MetricObservation m, Instant now) {
        long hours = Duration.between(m.capturedAt(), now).toHours();
        return hours <= 24 ? 100 : hours <= 72 ? 70 : hours <= 168 ? 40 : 0;
    }
    public MarketRegimeEvaluation regime(Map<String, MetricObservation> metrics, Map<String, Integer> observations, Instant now) {
        int score = score(observations);
        MarketRegime regime = score >= 25 ? MarketRegime.RISK_ON : score <= -25 ? MarketRegime.RISK_OFF : MarketRegime.NEUTRAL;
        List<FactorContribution> factors = new ArrayList<>();
        observations.forEach((code, points) -> factors.add(new FactorContribution("MARKET_REGIME", code, points > 0 ? FactorDirection.POSITIVE : points < 0 ? FactorDirection.NEGATIVE : FactorDirection.NEUTRAL, points, code.replace('_', ' ') + " trend", MetricStatus.AVAILABLE)));
        return new MarketRegimeEvaluation(regime, score, confidence(metrics, observations.values().stream().toList(), now), factors, metrics);
    }
    public static Map<String, Integer> weights() { return WEIGHTS; }
}

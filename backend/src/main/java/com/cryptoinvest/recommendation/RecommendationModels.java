package com.cryptoinvest.recommendation;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared point-in-time recommendation data; it contains no order command or user portfolio data. */
public final class RecommendationModels {
    private RecommendationModels() {}

    public enum MarketRegime { RISK_ON, NEUTRAL, RISK_OFF }
    public enum MetricStatus { AVAILABLE, UNAVAILABLE, STALE, INVALID, OUTLIER }
    public enum FactorDirection { POSITIVE, NEUTRAL, NEGATIVE }
    public enum Rating { STRONG_BUY, BUY, HOLD, REDUCE, STRONG_REDUCE }
    public enum PortfolioAction { HOLD, BUY_SMALL, BUY, BUY_STRONG, REDUCE_10, REDUCE_25, REDUCE_50, EXIT }
    public enum ReductionReason { TAKE_PROFIT, RISK_REDUCTION, REBALANCE, TREND_BREAK, FUNDAMENTAL_DETERIORATION }

    public record MetricObservation(String code, BigDecimal value, String unit, MetricStatus status,
            String source, Instant capturedAt, String note) {
        public MetricObservation {
            Objects.requireNonNull(code);
            Objects.requireNonNull(status);
            if (status == MetricStatus.AVAILABLE && (value == null || source == null || capturedAt == null)) {
                throw new IllegalArgumentException("Available metrics require a value, source, and capture time");
            }
        }
    }

    public record FactorContribution(String category, String code, FactorDirection direction, int points,
            String explanation, MetricStatus dataStatus) {
        public FactorContribution {
            Objects.requireNonNull(category);
            Objects.requireNonNull(code);
            Objects.requireNonNull(direction);
            Objects.requireNonNull(dataStatus);
            if (points < -100 || points > 100) throw new IllegalArgumentException("Factor points must be between -100 and 100");
        }
    }

    public record ConfidenceBreakdown(int dataCoverage, int freshness, int agreement, int outlierQuality, int score) {
        public ConfidenceBreakdown {
            if (dataCoverage < 0 || dataCoverage > 100 || freshness < 0 || freshness > 100
                    || agreement < 0 || agreement > 100 || outlierQuality < 0 || outlierQuality > 100
                    || score < 0 || score > 100) throw new IllegalArgumentException("Confidence values must be between 0 and 100");
        }
    }

    public record MarketRegimeEvaluation(MarketRegime regime, int score, ConfidenceBreakdown confidence,
            List<FactorContribution> factors, Map<String, MetricObservation> metrics) {
        public MarketRegimeEvaluation {
            Objects.requireNonNull(regime);
            Objects.requireNonNull(confidence);
            if (score < -100 || score > 100) throw new IllegalArgumentException("Market regime score must be between -100 and 100");
            factors = List.copyOf(factors);
            metrics = Map.copyOf(metrics);
        }
    }

    public record AssetEvaluation(Exchange exchange, String market, String ruleVersion, Instant evaluatedAt,
            MarketRegimeEvaluation marketRegime, int score, int confidence, Rating rating,
            ConfidenceBreakdown confidenceBreakdown, List<FactorContribution> factors,
            Map<String, MetricObservation> metrics) {
        public AssetEvaluation {
            Objects.requireNonNull(exchange);
            Objects.requireNonNull(market);
            Objects.requireNonNull(ruleVersion);
            Objects.requireNonNull(evaluatedAt);
            Objects.requireNonNull(marketRegime);
            Objects.requireNonNull(rating);
            Objects.requireNonNull(confidenceBreakdown);
            if (score < -100 || score > 100) throw new IllegalArgumentException("Asset score must be between -100 and 100");
            if (confidence < 0 || confidence > 100) throw new IllegalArgumentException("Confidence must be between 0 and 100");
            if (confidence != confidenceBreakdown.score()) throw new IllegalArgumentException("Confidence must match its breakdown");
            if (rating != ratingForScore(score)) throw new IllegalArgumentException("Rating must match the signed score");
            factors = List.copyOf(factors);
            metrics = Map.copyOf(metrics);
        }
    }

    public static Rating ratingForScore(int score) {
        if (score < -100 || score > 100) throw new IllegalArgumentException("Asset score must be between -100 and 100");
        if (score >= 60) return Rating.STRONG_BUY;
        if (score >= 25) return Rating.BUY;
        if (score >= -24) return Rating.HOLD;
        if (score >= -59) return Rating.REDUCE;
        return Rating.STRONG_REDUCE;
    }
}
package com.cryptoinvest.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptoinvest.exchange.Exchange;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationModelsTest {
    @Test
    void capturesSignedAssetScoreConfidenceAndImmutableExplainability() {
        var confidence = new RecommendationModels.ConfidenceBreakdown(75, 80, 60, 100, 74);
        var regime = new RecommendationModels.MarketRegimeEvaluation(RecommendationModels.MarketRegime.NEUTRAL,
                0, confidence, List.of(), Map.of());
        var factor = new RecommendationModels.FactorContribution("TECHNICAL", "RSI", RecommendationModels.FactorDirection.NEGATIVE,
                -8, "RSI 과열", RecommendationModels.MetricStatus.AVAILABLE);
        var result = new RecommendationModels.AssetEvaluation(Exchange.UPBIT, "KRW-BTC", "rules-v1", Instant.EPOCH,
                regime, -42, 74, RecommendationModels.Rating.REDUCE, confidence, List.of(factor), Map.of());

        assertThat(result.score()).isEqualTo(-42);
        assertThat(result.confidence()).isEqualTo(74);
        assertThat(result.factors()).containsExactly(factor);
        assertThatThrownBy(() -> result.factors().add(factor)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapsSignedScoreBandsExactly() {
        assertThat(RecommendationModels.ratingForScore(60)).isEqualTo(RecommendationModels.Rating.STRONG_BUY);
        assertThat(RecommendationModels.ratingForScore(25)).isEqualTo(RecommendationModels.Rating.BUY);
        assertThat(RecommendationModels.ratingForScore(-24)).isEqualTo(RecommendationModels.Rating.HOLD);
        assertThat(RecommendationModels.ratingForScore(-25)).isEqualTo(RecommendationModels.Rating.REDUCE);
        assertThat(RecommendationModels.ratingForScore(-60)).isEqualTo(RecommendationModels.Rating.STRONG_REDUCE);
    }

    @Test
    void rejectsInvalidScoreConfidenceAndUnverifiableAvailableMetric() {
        assertThatThrownBy(() -> new RecommendationModels.ConfidenceBreakdown(101, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecommendationModels.MetricObservation("funding", null, "ratio",
                RecommendationModels.MetricStatus.AVAILABLE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
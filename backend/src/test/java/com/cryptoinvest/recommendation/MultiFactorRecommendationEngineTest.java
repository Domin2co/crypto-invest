package com.cryptoinvest.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiFactorRecommendationEngineTest {
    private final MultiFactorRecommendationEngine engine = new MultiFactorRecommendationEngine();
    @Test void rescalesOnlyAvailableFactorsAndKeepsSignedBands() {
        assertThat(engine.score(Map.of("TECHNICAL", 80, "MARKET_REGIME", -20))).isEqualTo(40);
        assertThat(RecommendationModels.ratingForScore(-60)).isEqualTo(RecommendationModels.Rating.STRONG_REDUCE);
        assertThat(CoinEvaluationStrategy.forMarket("KRW-XRP").metrics()).contains("RLUSD_METRICS");
        assertThatThrownBy(() -> engine.score(Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}

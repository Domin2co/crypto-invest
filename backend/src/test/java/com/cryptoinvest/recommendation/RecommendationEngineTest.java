package com.cryptoinvest.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RecommendationEngineTest {
    @Test void isDeterministicAndDoesNotExceedTargetWeight() {
        Recommendation result = RecommendationEngine.recommend("BTC", BigDecimal.valueOf(25), BigDecimal.ONE, BigDecimal.ONE);
        assertThat(result.score()).isEqualTo(90);
        assertThat(result.signal()).isEqualTo("ACCUMULATE");
        assertThat(result.targetWeight()).isEqualByComparingTo("0.35");
        assertThat(RecommendationEngine.recommend("BTC", BigDecimal.valueOf(25), BigDecimal.ONE, BigDecimal.ONE)).isEqualTo(result);
    }
}

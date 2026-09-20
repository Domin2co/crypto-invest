package com.cryptoinvest.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndicatorEngineTest {
    private final List<BigDecimal> closes = List.of(BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(3), BigDecimal.valueOf(4));
    @Test void calculatesIndicatorsWithBigDecimal() {
        assertThat(IndicatorEngine.movingAverage(closes, 2)).isEqualByComparingTo("3.5");
        assertThat(IndicatorEngine.momentum(closes, 2)).isEqualByComparingTo("2");
        assertThat(IndicatorEngine.rsi(closes, 3)).isEqualByComparingTo("100");
        assertThat(IndicatorEngine.volatility(closes, 2)).isGreaterThan(BigDecimal.ZERO);
    }
    @Test void rejectsInsufficientData() { assertThatThrownBy(() -> IndicatorEngine.rsi(closes, 4)).isInstanceOf(IllegalArgumentException.class); }
}

package com.cryptoinvest.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.util.List;
import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.market.MarketCandle;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IndicatorEngineTest {
    private final List<BigDecimal> closes = List.of(BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(3), BigDecimal.valueOf(4));
    @Test void calculatesIndicatorsWithBigDecimal() {
        assertThat(IndicatorEngine.movingAverage(closes, 2)).isEqualByComparingTo("3.5");
        assertThat(IndicatorEngine.momentum(closes, 2)).isEqualByComparingTo("2");
        assertThat(IndicatorEngine.rsi(closes, 3)).isEqualByComparingTo("100");
        assertThat(IndicatorEngine.volatility(closes, 2)).isGreaterThan(BigDecimal.ZERO);
    }
    @Test void calculatesEmaMacdAndTrueRangeAtr() {
        List<BigDecimal> rising = java.util.stream.IntStream.rangeClosed(1, 30).mapToObj(BigDecimal::valueOf).toList();
        assertThat(IndicatorEngine.ema(rising, 12)).isGreaterThan(BigDecimal.valueOf(24));
        assertThat(IndicatorEngine.macd(rising)).isPositive();
        List<MarketCandle> candles = List.of(
            new MarketCandle(Exchange.UPBIT, "KRW-BTC", Instant.EPOCH, BigDecimal.TEN, BigDecimal.valueOf(12), BigDecimal.valueOf(9), BigDecimal.TEN, BigDecimal.ONE),
            new MarketCandle(Exchange.UPBIT, "KRW-BTC", Instant.EPOCH.plusSeconds(86400), BigDecimal.TEN, BigDecimal.valueOf(15), BigDecimal.valueOf(10), BigDecimal.valueOf(14), BigDecimal.ONE));
        assertThat(IndicatorEngine.atr(candles, 2)).isEqualByComparingTo("4");
    }    @Test void rejectsInsufficientData() { assertThatThrownBy(() -> IndicatorEngine.rsi(closes, 4)).isInstanceOf(IllegalArgumentException.class); }
}

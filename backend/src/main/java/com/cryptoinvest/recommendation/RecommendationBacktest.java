package com.cryptoinvest.recommendation;

import com.cryptoinvest.market.MarketCandle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Uses the first quote at or after each horizon; candles at or before evaluation are never outcomes. */
public final class RecommendationBacktest {
    private RecommendationBacktest() {}
    public static BigDecimal forwardReturnPercent(Instant evaluatedAt, BigDecimal evaluationPrice, List<MarketCandle> candles, int days) {
        if (evaluatedAt == null || evaluationPrice == null || evaluationPrice.signum() <= 0 || days < 1 || candles == null) throw new IllegalArgumentException("Invalid backtest input");
        Instant horizon = evaluatedAt.plus(Duration.ofDays(days));
        return candles.stream().filter(c -> c.openedAt() != null && !c.openedAt().isBefore(horizon) && c.close() != null && c.close().signum() > 0)
                .min(Comparator.comparing(MarketCandle::openedAt)).map(c -> c.close().subtract(evaluationPrice).multiply(BigDecimal.valueOf(100)).divide(evaluationPrice, MathContext.DECIMAL64)).orElse(null);
    }
}

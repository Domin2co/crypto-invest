package com.cryptoinvest.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.market.MarketCandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioRecommendationEngineTest {
    @Test void holdsCashFloorAndBacktestOnlyUsesPostHorizonQuotes() {
        var planner = new PortfolioRecommendationEngine();
        var suggestion = planner.suggest(80, RecommendationModels.MarketRegime.RISK_ON, new BigDecimal("0.10"), new BigDecimal("0.30"), new BigDecimal("0.30"), new BigDecimal("0.15"), new BigDecimal("0.20"), PortfolioRecommendationEngine.Limits.defaults());
        assertThat(suggestion.action()).isEqualTo(RecommendationModels.PortfolioAction.BUY_STRONG);
        assertThat(suggestion.weightDelta()).isEqualByComparingTo("0.01500000");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        var candles = List.of(candle(start.plusSeconds(86400), "110"), candle(start.plusSeconds(8L * 86400), "120"));
        assertThat(RecommendationBacktest.forwardReturnPercent(start, BigDecimal.valueOf(100), candles, 7)).isEqualByComparingTo("20");
    }
    @Test void zeroTargetNeverFallsBackToAssetMaximumAndEmptyHoldingsAreNotSold() {
        var planner = new PortfolioRecommendationEngine();
        var noTarget = planner.suggest(80, RecommendationModels.MarketRegime.RISK_ON, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.15"), new BigDecimal("0.50"), BigDecimal.ZERO, PortfolioRecommendationEngine.Limits.defaults());
        assertThat(noTarget.action()).isEqualTo(RecommendationModels.PortfolioAction.HOLD);
        var empty = planner.suggest(-80, RecommendationModels.MarketRegime.RISK_OFF, BigDecimal.ZERO, new BigDecimal("0.10"),
                new BigDecimal("0.15"), new BigDecimal("0.50"), BigDecimal.ZERO, PortfolioRecommendationEngine.Limits.defaults());
        assertThat(empty.action()).isEqualTo(RecommendationModels.PortfolioAction.HOLD);
    }

    private MarketCandle candle(Instant at, String price) { BigDecimal p = new BigDecimal(price); return new MarketCandle(Exchange.UPBIT,"KRW-BTC",at,p,p,p,p,BigDecimal.ONE); }
}

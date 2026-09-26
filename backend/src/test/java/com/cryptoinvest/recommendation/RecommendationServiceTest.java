package com.cryptoinvest.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import com.cryptoinvest.market.MarketCandle;
import com.cryptoinvest.market.MarketPrice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {
    @Test void returnsSignedTransparentEvaluationAndMarksMissingMetrics() {
        List<MarketCandle> candles = java.util.stream.IntStream.range(0, 121).mapToObj(day -> {
            BigDecimal close = BigDecimal.valueOf(100 + day);
            return new MarketCandle(Exchange.UPBIT, "KRW-BTC", Instant.EPOCH.plusSeconds(day * 86400L), close, close.add(BigDecimal.ONE), close.subtract(BigDecimal.ONE), close, BigDecimal.valueOf(day + 1));
        }).toList();
        RecommendationService service = new RecommendationService(List.of(new StubClient(candles)));
        RecommendationService.RecommendationView result = service.recommend(Exchange.UPBIT, "KRW-BTC");
        assertThat(result.candleCount()).isEqualTo(121);
        assertThat(result.dataSource()).contains("UPBIT");
        assertThat(result.evaluation().confidence()).isBetween(0, 100);
        assertThat(result.evaluation().confidence()).isLessThanOrEqualTo(result.evaluation().confidenceBreakdown().dataCoverage());
        assertThat(result.evaluation().metrics().get("MVRV").status()).isEqualTo(RecommendationModels.MetricStatus.UNAVAILABLE);
        assertThat(result.evaluation().metrics()).containsKeys("SMA_20", "SMA_60", "SMA_120", "MACD", "ATR_14", "VOLUME_TREND_7D");
    }
    @Test void rejectsUnsafeMarketBeforeCallingExchange() {
        RecommendationService service = new RecommendationService(List.of(new StubClient(List.of())));
        assertThatThrownBy(() -> service.recommend(Exchange.UPBIT, "btc")).isInstanceOf(IllegalArgumentException.class);
    }
    private record StubClient(List<MarketCandle> candles) implements ExchangePublicClient {
        @Override public Exchange exchange() { return Exchange.UPBIT; }
        @Override public MarketPrice getPrice(String market) { throw new UnsupportedOperationException(); }
        @Override public List<MarketCandle> getDailyCandles(String market, int count) { return candles; }
    }
}

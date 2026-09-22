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
    @Test
    void sortsExchangeCandlesAndExposesTransparentRecommendationMetadata() {
        List<MarketCandle> newestFirst = java.util.stream.IntStream.range(0, 15).mapToObj(index -> {
            int day = 14 - index;
            BigDecimal close = BigDecimal.valueOf(100 + day);
            return new MarketCandle(Exchange.UPBIT, "KRW-BTC", Instant.parse("2026-01-" + String.format("%02d", day + 1) + "T00:00:00Z"), close, close, close, close, BigDecimal.ONE);
        }).toList();
        RecommendationService service = new RecommendationService(List.of(new StubClient(newestFirst)));

        RecommendationService.RecommendationView result = service.recommend(Exchange.UPBIT, "KRW-BTC");

        assertThat(result.recommendation().symbol()).isEqualTo("KRW-BTC");
        assertThat(result.candleCount()).isEqualTo(15);
        assertThat(result.dataSource()).contains("UPBIT");
        assertThat(result.dataCapturedAt()).isEqualTo(Instant.parse("2026-01-15T00:00:00Z"));
        assertThat(result.indicators().volatilityPercent()).isPositive();
        assertThat(result.limitations()).isNotEmpty();
    }

    @Test
    void rejectsUnsafeMarketBeforeCallingExchange() {
        RecommendationService service = new RecommendationService(List.of(new StubClient(List.of())));
        assertThatThrownBy(() -> service.recommend(Exchange.UPBIT, "btc")).isInstanceOf(IllegalArgumentException.class);
    }

    private record StubClient(List<MarketCandle> candles) implements ExchangePublicClient {
        @Override public Exchange exchange() { return Exchange.UPBIT; }
        @Override public MarketPrice getPrice(String market) { throw new UnsupportedOperationException(); }
        @Override public List<MarketCandle> getDailyCandles(String market, int count) { return candles; }
    }
}

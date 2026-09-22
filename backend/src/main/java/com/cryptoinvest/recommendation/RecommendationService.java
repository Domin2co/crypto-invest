package com.cryptoinvest.recommendation;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import com.cryptoinvest.indicator.IndicatorEngine;
import com.cryptoinvest.market.MarketCandle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** 공개 일봉만으로 재현 가능한 추천 정보를 만들며, 사용자 자격증명이나 주문 경로는 사용하지 않는다. */
@Service
public class RecommendationService {
    private static final int INDICATOR_PERIOD = 14;
    private final Map<Exchange, ExchangePublicClient> clients;

    public RecommendationService(List<ExchangePublicClient> clients) {
        this.clients = clients.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ExchangePublicClient::exchange, Function.identity()));
    }

    public RecommendationView recommend(Exchange exchange, String market) {
        if (exchange == null || market == null || !market.matches("[A-Z]{2,10}-[A-Z0-9]{2,20}")) throw new IllegalArgumentException("Invalid recommendation request");
        ExchangePublicClient client = clients.get(exchange);
        if (client == null) throw new IllegalArgumentException("Unsupported exchange");
        List<MarketCandle> candles = client.getDailyCandles(market, INDICATOR_PERIOD + 1).stream()
                .sorted(Comparator.comparing(MarketCandle::openedAt)).toList();
        if (candles.size() < INDICATOR_PERIOD + 1 || candles.stream().anyMatch(candle -> candle.close() == null || candle.close().signum() <= 0)) {
            throw new IllegalStateException("Insufficient candle data");
        }
        List<BigDecimal> closes = candles.stream().map(MarketCandle::close).toList();
        BigDecimal latestPrice = closes.getLast();
        BigDecimal rsi = IndicatorEngine.rsi(closes, INDICATOR_PERIOD);
        BigDecimal momentum = IndicatorEngine.momentum(closes, 1);
        BigDecimal volatilityPercent = IndicatorEngine.volatility(closes, INDICATOR_PERIOD)
                .divide(latestPrice, MathContext.DECIMAL64).multiply(BigDecimal.valueOf(100));
        Recommendation recommendation = RecommendationEngine.recommend(market, rsi, momentum, volatilityPercent);
        return new RecommendationView(recommendation, Instant.now(), candles.getLast().openedAt(), exchange.name() + " public daily candles",
                candles.size(), new IndicatorValues(rsi, momentum, volatilityPercent),
                List.of("추천은 주문 지시나 수익 보장이 아닙니다.", "일봉 " + candles.size() + "개 기반으로 장중 변동·수수료·슬리피지는 반영하지 않습니다."));
    }

    public record RecommendationView(Recommendation recommendation, Instant generatedAt, Instant dataCapturedAt, String dataSource,
            int candleCount, IndicatorValues indicators, List<String> limitations) {}
    public record IndicatorValues(BigDecimal rsi, BigDecimal momentum, BigDecimal volatilityPercent) {}
}

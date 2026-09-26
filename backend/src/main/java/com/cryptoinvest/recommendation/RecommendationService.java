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
import org.springframework.beans.factory.annotation.Autowired;

/** Public candles only; evaluation never reads credentials or creates orders. */
@Service
public class RecommendationService {
    private static final int LOOKBACK = 121;
    private static final int INDICATOR_PERIOD = 14;
    private final Map<Exchange, ExchangePublicClient> clients;
    private final RecommendationEvaluationService evaluations = new RecommendationEvaluationService();
    private final PublicMarketDataService publicMarketData;
    private final RecommendationSnapshotRepository snapshots;

    @Autowired
    public RecommendationService(List<ExchangePublicClient> clients, PublicMarketDataService publicMarketData, RecommendationSnapshotRepository snapshots) {
        this.clients = clients.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ExchangePublicClient::exchange, Function.identity()));
        this.publicMarketData = publicMarketData;
        this.snapshots = snapshots;
    }

    public RecommendationService(List<ExchangePublicClient> clients) {
        this.clients = clients.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ExchangePublicClient::exchange, Function.identity()));
        this.publicMarketData = null;
        this.snapshots = null;
    }

    public RecommendationView recommend(Exchange exchange, String market) {
        if (exchange == null || market == null || !market.matches("[A-Z]{2,10}-[A-Z0-9]{2,20}")) throw new IllegalArgumentException("Invalid recommendation request");
        ExchangePublicClient client = clients.get(exchange);
        if (client == null) throw new IllegalArgumentException("Unsupported exchange");
        List<MarketCandle> candles = candles(client, market);
        List<BigDecimal> closes = candles.stream().map(MarketCandle::close).toList();
        BigDecimal latestPrice = closes.getLast();
        List<MarketCandle> benchmark = market.endsWith("-BTC") ? candles : candles(client, "KRW-BTC");
        Recommendation recommendation = RecommendationEngine.recommend(market,
                IndicatorEngine.rsi(closes.subList(closes.size() - 15, closes.size()), INDICATOR_PERIOD),
                IndicatorEngine.momentum(closes, 1),
                IndicatorEngine.volatility(closes, INDICATOR_PERIOD).divide(latestPrice, MathContext.DECIMAL64).multiply(BigDecimal.valueOf(100)));
        var evaluation = evaluations.evaluate(exchange, market, candles, benchmark, publicMarketData == null ? Map.of() : mergeMetrics(market));
        if (snapshots != null) snapshots.save(evaluation, latestPrice);
        return new RecommendationView(recommendation, Instant.now(), candles.getLast().openedAt(), exchange.name() + " public daily candles",
                candles.size(), new IndicatorValues(IndicatorEngine.rsi(closes, INDICATOR_PERIOD), IndicatorEngine.momentum(closes, 1),
                IndicatorEngine.volatility(closes, INDICATOR_PERIOD).divide(latestPrice, MathContext.DECIMAL64).multiply(BigDecimal.valueOf(100))),
                List.of("추천은 주문 지시나 수익 보장이 아닙니다.", "현재 기술 지표는 거래소 일봉만 사용하며 외부 온체인/수급/ETF 출처가 연결되지 않은 항목은 미확보로 표시합니다."), evaluation);
    }
    private Map<String, RecommendationModels.MetricObservation> mergeMetrics(String market) {
        Map<String, RecommendationModels.MetricObservation> values = new java.util.LinkedHashMap<>(publicMarketData.metrics());
        String symbol = market.substring(market.indexOf('-') + 1);
        values.putAll(publicMarketData.assetMetrics(symbol));
        return Map.copyOf(values);
    }
    private List<MarketCandle> candles(ExchangePublicClient client, String market) {
        List<MarketCandle> values = client.getDailyCandles(market, LOOKBACK).stream().sorted(Comparator.comparing(MarketCandle::openedAt)).toList();
        if (values.size() < LOOKBACK || values.stream().anyMatch(c -> c.close() == null || c.close().signum() <= 0 || c.high() == null || c.low() == null || c.volume() == null || c.high().compareTo(c.low()) < 0)) throw new IllegalStateException("Insufficient or invalid candle data");
        return values;
    }
    public Map<String, BigDecimal> correlations(Exchange exchange, String market, List<String> currencies) {
        ExchangePublicClient client = clients.get(exchange);
        if (client == null || currencies == null || currencies.isEmpty()) return Map.of();
        List<MarketCandle> candidateCandles = client.getDailyCandles(market, 40);
        Map<java.time.LocalDate, BigDecimal> candidate = dailyReturns(candidateCandles);
        Map<String, BigDecimal> values = new java.util.LinkedHashMap<>();
        for (String currency : currencies.stream().distinct().filter(c -> c != null && c.matches("[A-Z0-9]{2,20}") && !market.endsWith("-" + c)).toList()) {
            try {
                Map<java.time.LocalDate, BigDecimal> peer = dailyReturns(client.getDailyCandles("KRW-" + currency, 40));
                List<java.time.LocalDate> common = candidate.keySet().stream().filter(peer::containsKey).sorted().toList();
                if (common.size() >= 20) values.put(currency, pearson(common.stream().map(candidate::get).toList(), common.stream().map(peer::get).toList()));
            } catch (RuntimeException ignored) {
                // Missing peer candles remain absent and are exposed as unavailable by the caller.
            }
        }
        return Map.copyOf(values);
    }

    private Map<java.time.LocalDate, BigDecimal> dailyReturns(List<MarketCandle> candles) {
        List<MarketCandle> sorted = candles.stream().filter(c -> c.openedAt() != null && c.close() != null && c.close().signum() > 0)
                .sorted(Comparator.comparing(MarketCandle::openedAt)).toList();
        Map<java.time.LocalDate, BigDecimal> returns = new java.util.TreeMap<>();
        for (int i = 1; i < sorted.size(); i++) {
            var previous = sorted.get(i - 1);
            var current = sorted.get(i);
            BigDecimal change = current.close().subtract(previous.close()).divide(previous.close(), MathContext.DECIMAL64);
            returns.put(current.openedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(), change);
        }
        return returns;
    }

    private BigDecimal pearson(List<BigDecimal> left, List<BigDecimal> right) {
        double meanLeft = left.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double meanRight = right.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double covariance = 0, varianceLeft = 0, varianceRight = 0;
        for (int i = 0; i < left.size(); i++) {
            double x = left.get(i).doubleValue() - meanLeft, y = right.get(i).doubleValue() - meanRight;
            covariance += x * y; varianceLeft += x * x; varianceRight += y * y;
        }
        if (varianceLeft == 0 || varianceRight == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(covariance / Math.sqrt(varianceLeft * varianceRight));
    }

    public record RecommendationView(Recommendation recommendation, Instant generatedAt, Instant dataCapturedAt, String dataSource,
            int candleCount, IndicatorValues indicators, List<String> limitations, RecommendationModels.AssetEvaluation evaluation) {}
    public record IndicatorValues(BigDecimal rsi, BigDecimal momentum, BigDecimal volatilityPercent) {}
}

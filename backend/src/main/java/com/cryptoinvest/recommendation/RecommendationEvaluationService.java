package com.cryptoinvest.recommendation;

import static com.cryptoinvest.recommendation.RecommendationModels.*;
import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.indicator.IndicatorEngine;
import com.cryptoinvest.market.MarketCandle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RecommendationEvaluationService {
    private final MultiFactorRecommendationEngine engine = new MultiFactorRecommendationEngine();
    AssetEvaluation evaluate(Exchange exchange, String market, List<MarketCandle> candles, List<MarketCandle> btcCandles) {
        return evaluate(exchange, market, candles, btcCandles, Map.of());
    }
    AssetEvaluation evaluate(Exchange exchange, String market, List<MarketCandle> candles, List<MarketCandle> btcCandles, Map<String, MetricObservation> external) {
        Instant now = Instant.now();
        Map<String, MetricObservation> metrics = new LinkedHashMap<>();
        List<BigDecimal> closes = candles.stream().map(MarketCandle::close).toList();
        BigDecimal price = closes.getLast();
        put(metrics, "SMA_20", IndicatorEngine.movingAverage(closes, 20), candles, "exchange daily candles");
        put(metrics, "SMA_60", IndicatorEngine.movingAverage(closes, 60), candles, "exchange daily candles");
        put(metrics, "SMA_120", IndicatorEngine.movingAverage(closes, 120), candles, "exchange daily candles");
        put(metrics, "EMA_12", IndicatorEngine.ema(closes, 12), candles, "exchange daily candles");
        put(metrics, "EMA_26", IndicatorEngine.ema(closes, 26), candles, "exchange daily candles");
        put(metrics, "MACD", IndicatorEngine.macd(closes), candles, "exchange daily candles");
        put(metrics, "RSI_14", IndicatorEngine.rsi(closes, 14), candles, "exchange daily candles");
        BigDecimal atr = IndicatorEngine.atr(candles, 14);
        put(metrics, "ATR_14", atr, candles, "exchange OHLC daily candles");
        List<BigDecimal> volumes = candles.stream().map(MarketCandle::volume).toList();
        BigDecimal recentVolume = average(volumes.subList(volumes.size() - 7, volumes.size()));
        BigDecimal priorVolume = average(volumes.subList(volumes.size() - 14, volumes.size() - 7));
        put(metrics, "VOLUME_TREND_7D", recentVolume.subtract(priorVolume).multiply(BigDecimal.valueOf(100)).divide(priorVolume.max(BigDecimal.ONE.scaleByPowerOfTen(-8)), 8, RoundingMode.HALF_UP), candles, "exchange daily candles");
        BigDecimal dailyReturn = price.subtract(closes.get(closes.size() - 2)).multiply(BigDecimal.valueOf(100)).divide(closes.get(closes.size() - 2), 8, RoundingMode.HALF_UP);
        List<BigDecimal> returns = new java.util.ArrayList<>();
        for (int i = Math.max(1, closes.size() - 31); i < closes.size(); i++) returns.add(closes.get(i).subtract(closes.get(i - 1)).multiply(BigDecimal.valueOf(100)).divide(closes.get(i - 1), 8, RoundingMode.HALF_UP));
        BigDecimal medianReturn = median(returns);
        BigDecimal mad = median(returns.stream().map(value -> value.subtract(medianReturn).abs()).toList());
        MetricStatus outlierStatus = dailyReturn.subtract(medianReturn).abs().compareTo(mad.multiply(BigDecimal.valueOf(6)).max(new BigDecimal("5"))) > 0 ? MetricStatus.OUTLIER : MetricStatus.AVAILABLE;
        metrics.put("DAILY_RETURN_OUTLIER", new MetricObservation("DAILY_RETURN_OUTLIER", dailyReturn, "percent", outlierStatus, "exchange daily candles; 30-day median/MAD check", candles.getLast().openedAt(), null));
        metrics.putAll(external);
        unavailable(metrics, List.of("BTC_DOMINANCE_CHANGE", "BTC_ETH_ETF_FLOW").stream().filter(key -> !metrics.containsKey(key)).toList());
        List<MarketCandle> benchmark = btcCandles.stream().sorted(Comparator.comparing(MarketCandle::openedAt)).toList();
        List<BigDecimal> btc = benchmark.stream().map(MarketCandle::close).toList();
        put(metrics, "BTC_SMA_20", IndicatorEngine.movingAverage(btc, 20), benchmark, "exchange KRW-BTC daily candles");
        put(metrics, "BTC_SMA_60", IndicatorEngine.movingAverage(btc, 60), benchmark, "exchange KRW-BTC daily candles");
        put(metrics, "BTC_SMA_120", IndicatorEngine.movingAverage(btc, 120), benchmark, "exchange KRW-BTC daily candles");
        String symbol = market.substring(market.indexOf('-') + 1);
        for (String key : List.of("TVL_CHANGE_24H", "DEX_VOLUME_24H", "STABLECOIN_CHANGE_24H")) {
            MetricObservation observation = external.get(key + "_" + symbol);
            if (observation != null) {
                String profileKey = key.equals("TVL_CHANGE_24H") ? "TVL" : key.equals("DEX_VOLUME_24H") ? "DEX_VOLUME" : "STABLECOIN_SUPPLY";
                metrics.put(profileKey, new MetricObservation(profileKey, observation.value(), observation.unit(), observation.status(), observation.source(), observation.capturedAt(), observation.note()));
                metrics.put(key, observation);
            }
        }
        unavailable(metrics, CoinEvaluationStrategy.forMarket(market).metrics().stream().filter(key -> !metrics.containsKey(key)).toList());
        int tech = averageScore(
                signScore(price.compareTo(IndicatorEngine.movingAverage(closes, 20))),
                signScore(IndicatorEngine.macd(closes).signum()),
                momentumScore(IndicatorEngine.momentum(closes, 20), price),
                riskScore(atr, price),
                volumeScore(recentVolume.compareTo(priorVolume)));
        Map<String, Integer> regimeSignals = new LinkedHashMap<>();
        regimeSignals.put("BTC_SMA_TREND", averageScore(signScore(btc.getLast().compareTo(IndicatorEngine.movingAverage(btc, 20))), signScore(IndicatorEngine.movingAverage(btc, 20).compareTo(IndicatorEngine.movingAverage(btc, 60))), signScore(IndicatorEngine.movingAverage(btc, 60).compareTo(IndicatorEngine.movingAverage(btc, 120)))));
        MetricObservation fear = metrics.get("FEAR_GREED");
        if (fear != null && fear.status() == MetricStatus.AVAILABLE) regimeSignals.put("FEAR_GREED", fear.value().compareTo(BigDecimal.valueOf(60)) >= 0 ? 30 : fear.value().compareTo(BigDecimal.valueOf(40)) <= 0 ? -30 : 0);
        MetricObservation marketCapChange = metrics.get("MARKET_CAP_CHANGE_24H");
        if (marketCapChange != null && marketCapChange.status() == MetricStatus.AVAILABLE) regimeSignals.put("MARKET_CAP_CHANGE_24H", marketCapChange.value().compareTo(BigDecimal.ONE) >= 0 ? 30 : marketCapChange.value().compareTo(BigDecimal.ONE.negate()) <= 0 ? -30 : 0);
        MetricObservation fundingSignal = metrics.get("FUNDING_RATE");
        if (fundingSignal != null && fundingSignal.status() == MetricStatus.AVAILABLE) regimeSignals.put("FUNDING_RATE", fundingSignal.value().abs().compareTo(new BigDecimal("0.0003")) >= 0 ? -30 : 0);
        int regimeScore = averageScore(regimeSignals.values().stream().mapToInt(Integer::intValue).toArray());
        Map<String, Integer> categoryScores = new LinkedHashMap<>();
        categoryScores.put("TECHNICAL", tech);
        categoryScores.put("MARKET_REGIME", regimeScore);
        categoryScores.put("RELATIVE_STRENGTH", relativeStrength(closes, btc));
        categoryScores.put("RISK", outlierStatus == MetricStatus.OUTLIER ? -60 : riskScore(atr, price));
        Integer fundamental = availableMetricAverage(CoinEvaluationStrategy.forMarket(market).metrics().stream().map(metrics::get).filter(java.util.Objects::nonNull).toList());
        if (fundamental != null) categoryScores.put("FUNDAMENTAL_ONCHAIN", fundamental);
        MetricObservation funding = metrics.get("FUNDING_RATE");
        if (funding != null && funding.status() == MetricStatus.AVAILABLE) categoryScores.put("FLOW_DERIVATIVES", funding.value().compareTo(new BigDecimal("0.0003")) > 0 ? -40 : funding.value().compareTo(new BigDecimal("-0.0003")) < 0 ? 20 : 10);
        MarketRegime regime = regimeScore >= 25 ? MarketRegime.RISK_ON : regimeScore <= -25 ? MarketRegime.RISK_OFF : MarketRegime.NEUTRAL;
        List<FactorContribution> regimeFactors = regimeSignals.entrySet().stream().map(e -> factor(e.getKey(), "MARKET_REGIME", e.getValue(), e.getKey().replace('_', ' '), MetricStatus.AVAILABLE)).toList();
        var quality = engine.confidence(metrics, List.of(Integer.signum(tech), Integer.signum(regimeScore)), now);
        var regimeEvaluation = new MarketRegimeEvaluation(regime, regimeScore, quality, regimeFactors, metrics);
        List<FactorContribution> factors = new ArrayList<>(regimeFactors);
        factors.add(factor("TECHNICAL_TREND", "TECHNICAL", tech, "이평·MACD·거래량·ATR 기반 기술 점수", MetricStatus.AVAILABLE));
        for (String key : CoinEvaluationStrategy.forMarket(market).metrics()) {
            MetricObservation observation = metrics.get(key);
            int points = observation != null && observation.status() == MetricStatus.AVAILABLE && observation.value() != null && (key.equals("TVL") || key.equals("STABLECOIN_SUPPLY"))
                    ? observation.value().signum() > 0 ? 40 : observation.value().signum() < 0 ? -40 : 0 : 0;
            factors.add(factor(key, "FUNDAMENTAL_ONCHAIN", points, observation == null ? key + " unavailable" : key + " observed; " + observation.source(), observation == null ? MetricStatus.UNAVAILABLE : observation.status()));
        }
        for (String key : List.of("BTC_DOMINANCE_CHANGE", "TOTAL_MARKET_CAP", "TOTAL_MARKET_VOLUME", "FEAR_GREED", "FUNDING_RATE", "OPEN_INTEREST", "BTC_ETH_ETF_FLOW")) {
            MetricObservation observation = metrics.get(key);
            factors.add(factor(key, "MARKET_REGIME", 0, observation == null ? key + " unavailable" : key + " observed; " + observation.source(), observation == null ? MetricStatus.UNAVAILABLE : observation.status()));
        }
        int score = engine.score(categoryScores);
        return new AssetEvaluation(exchange, market, MultiFactorRecommendationEngine.RULE_VERSION, now, regimeEvaluation, score, quality.score(), RecommendationModels.ratingForScore(score), quality, factors, metrics);
    }
    private void put(Map<String, MetricObservation> out, String key, BigDecimal value, List<MarketCandle> candles, String source) { out.put(key, new MetricObservation(key, value, null, MetricStatus.AVAILABLE, source, candles.getLast().openedAt(), null)); }
    private void unavailable(Map<String, MetricObservation> out, java.util.Collection<String> keys) { keys.forEach(key -> out.put(key, new MetricObservation(key, null, null, MetricStatus.UNAVAILABLE, null, null, "무료 공개 출처 연결 전"))); }
    private FactorContribution factor(String code, String category, int points, String reason, MetricStatus status) { return new FactorContribution(category, code, points > 0 ? FactorDirection.POSITIVE : points < 0 ? FactorDirection.NEGATIVE : FactorDirection.NEUTRAL, points, reason, status); }
    private int averageScore(int... values) { return java.util.Arrays.stream(values).sum() / values.length; }
    private int signScore(int sign) { return sign > 0 ? 60 : sign < 0 ? -60 : 0; }
    private int momentumScore(BigDecimal momentum, BigDecimal price) { return signScore(momentum.signum()) * 1; }
    private int riskScore(BigDecimal atr, BigDecimal price) { BigDecimal pct = atr.multiply(BigDecimal.valueOf(100)).divide(price, 8, RoundingMode.HALF_UP); return pct.compareTo(BigDecimal.TEN) > 0 ? -60 : pct.compareTo(BigDecimal.valueOf(5)) > 0 ? -30 : pct.compareTo(BigDecimal.valueOf(2)) < 0 ? 20 : 0; }
    private int volumeScore(int sign) { return sign > 0 ? 30 : sign < 0 ? -20 : 0; }
    private BigDecimal average(List<BigDecimal> values) { return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP); }
    private BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP) : sorted.get(middle);
    }

    private Integer relativeStrength(List<BigDecimal> asset, List<BigDecimal> btc) {
        BigDecimal a = asset.getLast().subtract(asset.get(asset.size() - 21)).divide(asset.get(asset.size() - 21), 8, RoundingMode.HALF_UP);
        BigDecimal b = btc.getLast().subtract(btc.get(btc.size() - 21)).divide(btc.get(btc.size() - 21), 8, RoundingMode.HALF_UP);
        return signScore(a.compareTo(b));
    }
    private Integer availableMetricAverage(java.util.Collection<MetricObservation> values) {
        List<Integer> scores = values.stream().filter(m -> m.status() == MetricStatus.AVAILABLE && m.value() != null
                        && (m.code().equals("TVL") || m.code().equals("STABLECOIN_SUPPLY")))
                .map(m -> m.value().signum() > 0 ? 40 : m.value().signum() < 0 ? -40 : 0).toList();
        return scores.isEmpty() ? null : averageScore(scores.stream().mapToInt(Integer::intValue).toArray());
    }
}

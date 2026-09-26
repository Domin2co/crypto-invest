package com.cryptoinvest.indicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import com.cryptoinvest.market.MarketCandle;

/** 시장 종가 목록만 받아 지표를 계산한다. 모든 금액 계산은 BigDecimal로 유지한다. */
public final class IndicatorEngine {
    private static final MathContext MC = MathContext.DECIMAL64;
    private IndicatorEngine() {}

    public static BigDecimal movingAverage(List<BigDecimal> closes, int period) {
        require(closes, period); BigDecimal sum = closes.subList(closes.size() - period, closes.size()).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(period), MC);
    }
    public static BigDecimal momentum(List<BigDecimal> closes, int period) {
        require(closes, period + 1); return closes.get(closes.size() - 1).subtract(closes.get(closes.size() - 1 - period));
    }
    public static BigDecimal rsi(List<BigDecimal> closes, int period) {
        require(closes, period + 1); BigDecimal gain = BigDecimal.ZERO, loss = BigDecimal.ZERO;
        for (int i = closes.size() - period; i < closes.size(); i++) { BigDecimal change = closes.get(i).subtract(closes.get(i - 1)); if (change.signum() > 0) gain = gain.add(change); else loss = loss.add(change.negate()); }
        if (loss.signum() == 0) return BigDecimal.valueOf(100);
        BigDecimal rs = gain.divide(loss, MC); return BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), MC));
    }
    public static BigDecimal volatility(List<BigDecimal> closes, int period) {
        require(closes, period); BigDecimal mean = movingAverage(closes, period), variance = BigDecimal.ZERO;
        for (BigDecimal close : closes.subList(closes.size() - period, closes.size())) variance = variance.add(close.subtract(mean).pow(2));
        return variance.divide(BigDecimal.valueOf(period), MC).sqrt(MC);
    }
    public static BigDecimal ema(List<BigDecimal> closes, int period) {
        require(closes, period);
        BigDecimal alpha = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1L), MC);
        BigDecimal value = movingAverage(closes.subList(0, period), period);
        for (int i = period; i < closes.size(); i++) value = closes.get(i).multiply(alpha).add(value.multiply(BigDecimal.ONE.subtract(alpha)));
        return value;
    }
    public static BigDecimal macd(List<BigDecimal> closes) {
        require(closes, 26);
        return ema(closes, 12).subtract(ema(closes, 26));
    }
    public static BigDecimal atr(List<MarketCandle> candles, int period) {
        if (period < 1 || candles == null || candles.size() < period || candles.stream().anyMatch(c -> c == null || c.high() == null || c.low() == null || c.close() == null || c.high().compareTo(c.low()) < 0)) throw new IllegalArgumentException("Invalid candle input");
        BigDecimal total = BigDecimal.ZERO;
        int start = candles.size() - period;
        for (int i = start; i < candles.size(); i++) {
            MarketCandle candle = candles.get(i);
            BigDecimal range = candle.high().subtract(candle.low());
            if (i > 0) range = range.max(candle.high().subtract(candles.get(i - 1).close()).abs()).max(candle.low().subtract(candles.get(i - 1).close()).abs());
            total = total.add(range);
        }
        return total.divide(BigDecimal.valueOf(period), MC);
    }
    private static void require(List<BigDecimal> closes, int period) {
        if (period < 1 || closes == null || closes.size() < period || closes.stream().anyMatch(v -> v == null || v.signum() < 0)) throw new IllegalArgumentException("Invalid indicator input");
    }
}

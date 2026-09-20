package com.cryptoinvest.indicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

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
    private static void require(List<BigDecimal> closes, int period) {
        if (period < 1 || closes == null || closes.size() < period || closes.stream().anyMatch(v -> v == null || v.signum() < 0)) throw new IllegalArgumentException("Invalid indicator input");
    }
}

package com.cryptoinvest.recommendation;

import static com.cryptoinvest.recommendation.RecommendationModels.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Calculates suggested allocation only; it has no order or account integration. */
public final class PortfolioRecommendationEngine {
    public record Limits(BigDecimal btcMax, BigDecimal ethMax, BigDecimal majorAltMax, BigDecimal highRiskAltMax, BigDecimal totalAltMax, BigDecimal cashMin) {
        public Limits {
            if (java.util.stream.Stream.of(btcMax, ethMax, majorAltMax, highRiskAltMax, totalAltMax, cashMin)
                    .anyMatch(value -> value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0))
                throw new IllegalArgumentException("Portfolio limits must be between 0 and 1");
        }
        public static Limits defaults() { return new Limits(new BigDecimal("0.55"), new BigDecimal("0.30"), new BigDecimal("0.15"), new BigDecimal("0.05"), new BigDecimal("0.35"), new BigDecimal("0.10")); }
    }
    public record Suggestion(PortfolioAction action, BigDecimal weightDelta, ReductionReason reason, List<String> explanations) {}
    public Suggestion suggest(int score, MarketRegime regime, BigDecimal currentWeight, BigDecimal targetWeight, BigDecimal maxWeight, BigDecimal cashWeight, BigDecimal altWeight, Limits limits) {
        if (score < -100 || score > 100 || currentWeight.signum() < 0 || targetWeight.signum() < 0 || maxWeight.signum() < 0 || cashWeight.signum() < 0 || altWeight.signum() < 0) throw new IllegalArgumentException("Invalid portfolio input");
        BigDecimal gap = targetWeight.subtract(currentWeight);
        if (currentWeight.signum() == 0 && score <= -25) return new Suggestion(PortfolioAction.HOLD, BigDecimal.ZERO, null, List.of("No position to reduce."));
        if (score > -25 && gap.signum() < 0) return reduce(PortfolioAction.REDUCE_10, currentWeight.multiply(new BigDecimal("-0.10")), ReductionReason.REBALANCE, "Current weight exceeds target allocation.");
        if (score <= -60) return reduce(PortfolioAction.REDUCE_50, currentWeight.multiply(new BigDecimal("-0.50")), ReductionReason.TREND_BREAK, "강한 하락 점수로 보유 비중 절반 축소 검토");
        if (score <= -25) return reduce(score <= -45 ? PortfolioAction.REDUCE_50 : PortfolioAction.REDUCE_25, currentWeight.multiply(new BigDecimal(score <= -45 ? "-0.50" : "-0.25")), score <= -45 ? ReductionReason.RISK_REDUCTION : ReductionReason.FUNDAMENTAL_DETERIORATION, "자산 점수 하락에 따른 위험 축소 제안");
        if (score < 25 || regime == MarketRegime.RISK_OFF || gap.signum() <= 0) return new Suggestion(PortfolioAction.HOLD, BigDecimal.ZERO, null, List.of("목표 비중 또는 시장 조건상 추가 매수 제안 없음"));
        BigDecimal assetCap = maxWeight.min(targetWeight);
        BigDecimal headroom = assetCap.subtract(currentWeight).max(BigDecimal.ZERO);
        if (altWeight.compareTo(limits.totalAltMax()) >= 0) headroom = BigDecimal.ZERO; else headroom = headroom.min(limits.totalAltMax().subtract(altWeight));
        BigDecimal investableCash = cashWeight.subtract(limits.cashMin()).max(BigDecimal.ZERO);
        BigDecimal amount = gap.min(headroom).min(investableCash);
        if (amount.signum() <= 0) return new Suggestion(PortfolioAction.HOLD, BigDecimal.ZERO, ReductionReason.REBALANCE, List.of("현금 하한 또는 최대 비중 제한"));
        BigDecimal fraction = score >= 60 && regime == MarketRegime.RISK_ON ? new BigDecimal("0.30") : new BigDecimal("0.15");
        PortfolioAction action = score >= 60 && regime == MarketRegime.RISK_ON ? PortfolioAction.BUY_STRONG : PortfolioAction.BUY_SMALL;
        return new Suggestion(action, amount.multiply(fraction).setScale(8, RoundingMode.DOWN), null, List.of("목표 추가 투자금의 일부만 제안", "현금 하한과 자산 최대 비중 적용"));
    }

    private Suggestion reduce(PortfolioAction action, BigDecimal delta, ReductionReason reason, String explanation) { return new Suggestion(action, delta, reason, List.of(explanation)); }
}

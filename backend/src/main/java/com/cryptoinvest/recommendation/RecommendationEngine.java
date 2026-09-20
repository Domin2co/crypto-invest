package com.cryptoinvest.recommendation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 고정 규칙으로 재현 가능한 추천을 산출한다. 설정 가능한 정책은 향후 phase에서 분리한다. */
public final class RecommendationEngine {
    private RecommendationEngine() {}
    public static Recommendation recommend(String symbol, BigDecimal rsi, BigDecimal momentum, BigDecimal volatility) {
        int score = 0; List<String> reasons = new ArrayList<>();
        if (rsi.compareTo(BigDecimal.valueOf(30)) <= 0) { score += 40; reasons.add("RSI 과매도"); }
        else if (rsi.compareTo(BigDecimal.valueOf(70)) >= 0) { score -= 30; reasons.add("RSI 과매수"); }
        if (momentum.signum() > 0) { score += 30; reasons.add("상승 모멘텀"); } else { score -= 10; reasons.add("하락 모멘텀"); }
        if (volatility.compareTo(BigDecimal.ONE) <= 0) { score += 20; reasons.add("낮은 변동성"); } else { score -= 10; reasons.add("변동성 위험"); }
        score = Math.max(0, Math.min(100, score));
        String signal = score >= 60 ? "ACCUMULATE" : score <= 30 ? "REDUCE" : "HOLD";
        BigDecimal weight = "ACCUMULATE".equals(signal) ? new BigDecimal("0.35") : "HOLD".equals(signal) ? new BigDecimal("0.10") : BigDecimal.ZERO;
        return new Recommendation(symbol, score, signal, weight, List.copyOf(reasons));
    }
}

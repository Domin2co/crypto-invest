package com.cryptoinvest.trading;

import com.cryptoinvest.risk.RiskEngine;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 기본 PAPER 설정과 RiskEngine을 모두 통과한 경우에만 실주문 경로를 열어 준다. */
@Component
public class LiveTradingGuard {
    private final String tradingMode;
    private final boolean liveTradingEnabled;
    private final BigDecimal dailyLimit;

    public LiveTradingGuard(@Value("${app.trading-mode:PAPER}") String tradingMode,
            @Value("${app.live-trading-enabled:false}") boolean liveTradingEnabled,
            @Value("${app.live-daily-limit:0}") BigDecimal dailyLimit) {
        this.tradingMode = tradingMode;
        this.liveTradingEnabled = liveTradingEnabled;
        this.dailyLimit = dailyLimit;
    }

    /** 거절 사유는 감사 가능하지만 호출자는 이 메서드를 우회해 주문할 수 없다. */
    public void requireAllowed(OrderPlan plan, RiskPolicy policy, BigDecimal dailySubmittedAmount) {
        String riskReason = RiskEngine.rejectReason(plan, policy);
        if (riskReason != null) throw rejected(riskReason);
        if (!"LIVE".equals(tradingMode)) throw rejected("TRADING_MODE_NOT_LIVE");
        if (!liveTradingEnabled) throw rejected("LIVE_TRADING_DISABLED");
        if (dailyLimit == null || dailyLimit.signum() <= 0) throw rejected("LIVE_DAILY_LIMIT_NOT_CONFIGURED");
        if (dailySubmittedAmount.add(plan.amount()).compareTo(dailyLimit) > 0) throw rejected("LIVE_DAILY_LIMIT");
    }

    private static IllegalStateException rejected(String reason) { return new IllegalStateException("Live order rejected: " + reason); }
}

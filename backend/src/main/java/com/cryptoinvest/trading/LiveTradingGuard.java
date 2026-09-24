package com.cryptoinvest.trading;

import com.cryptoinvest.risk.RiskEngine;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 신규 실주문은 명시적 enable, 해제된 kill switch, 양수 한도와 RiskEngine을 모두 통과해야 한다. */
@Component
public class LiveTradingGuard {
    private final boolean liveTradingEnabled;
    private final boolean liveTradingKillSwitch;
    private final BigDecimal dailyLimit;

    public LiveTradingGuard(@Value("${app.live-trading-enabled:false}") boolean liveTradingEnabled,
            @Value("${app.live-trading-kill-switch:true}") boolean liveTradingKillSwitch,
            @Value("${app.live-daily-limit:0}") BigDecimal dailyLimit) {
        this.liveTradingEnabled = liveTradingEnabled;
        this.liveTradingKillSwitch = liveTradingKillSwitch;
        this.dailyLimit = dailyLimit;
    }

    public void requireSwitchesOpen() {
        if (liveTradingKillSwitch) throw rejected("LIVE_KILL_SWITCH");
        if (!liveTradingEnabled) throw rejected("LIVE_TRADING_DISABLED");
        if (dailyLimit == null || dailyLimit.signum() <= 0) throw rejected("LIVE_DAILY_LIMIT_NOT_CONFIGURED");
    }

    /** 거절 사유는 감사 가능하지만 호출자는 이 메서드를 우회해 주문할 수 없다. */
    public void requireAllowed(OrderPlan plan, RiskPolicy policy, BigDecimal dailySubmittedAmount) {
        requireSwitchesOpen();
        String riskReason = RiskEngine.rejectReason(plan, policy);
        if (riskReason != null) throw rejected(riskReason);
        if (dailySubmittedAmount.add(plan.amount()).compareTo(dailyLimit) > 0) throw rejected("LIVE_DAILY_LIMIT");
    }

    private static IllegalStateException rejected(String reason) { return new IllegalStateException("Live order rejected: " + reason); }
}
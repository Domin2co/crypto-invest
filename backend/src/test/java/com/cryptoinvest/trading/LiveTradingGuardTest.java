package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveTradingGuardTest {
    private final OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "live-guard");
    private final RiskPolicy policy = new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE);

    @Test void rejectsUnlessEveryExplicitLiveSwitchAndLimitIsSet() {
        assertThatThrownBy(() -> new LiveTradingGuard("PAPER", true, new BigDecimal("20000")).requireAllowed(plan, policy, BigDecimal.ZERO))
                .hasMessageContaining("TRADING_MODE_NOT_LIVE");
        assertThatThrownBy(() -> new LiveTradingGuard("LIVE", false, new BigDecimal("20000")).requireAllowed(plan, policy, BigDecimal.ZERO))
                .hasMessageContaining("LIVE_TRADING_DISABLED");
        assertThatThrownBy(() -> new LiveTradingGuard("LIVE", true, BigDecimal.ZERO).requireAllowed(plan, policy, BigDecimal.ZERO))
                .hasMessageContaining("LIVE_DAILY_LIMIT_NOT_CONFIGURED");
        assertThatThrownBy(() -> new LiveTradingGuard("LIVE", true, new BigDecimal("15000")).requireAllowed(plan, policy, new BigDecimal("6000")))
                .hasMessageContaining("LIVE_DAILY_LIMIT");
    }

    @Test void permitsOnlyRiskApprovedPlanBelowDailyLimit() {
        assertThatCode(() -> new LiveTradingGuard("LIVE", true, new BigDecimal("20000")).requireAllowed(plan, policy, BigDecimal.ZERO))
                .doesNotThrowAnyException();
    }
}

package com.cryptoinvest.risk;

import static org.assertj.core.api.Assertions.assertThat;
import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.trading.OrderPlan;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskEngineTest {
    private final OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.2"), "test-key");
    @Test void rejectsKillSwitchAndLimitViolations() {
        assertThat(RiskEngine.rejectReason(plan, new RiskPolicy(true, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE))).isEqualTo("KILL_SWITCH");
        assertThat(RiskEngine.rejectReason(plan, new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), new BigDecimal("0.1")))).isEqualTo("MAX_ASSET_WEIGHT");
    }
}

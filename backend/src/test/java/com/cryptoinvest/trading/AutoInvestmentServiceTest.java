package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutoInvestmentServiceTest {
    @Test void cashOnlyDoesNotSellAndNeverBypassesRisk() {
        OrderPlan buy = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "buy");
        OrderPlan sell = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "SELL", new BigDecimal("10000"), new BigDecimal("0.1"), "sell");
        RiskPolicy policy = new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE);
        assertThat(AutoInvestmentService.eligible(List.of(buy, sell), AutoInvestmentMode.CASH_ONLY, policy)).containsExactly(buy);
    }

    @Test void keepExistingAssetsAlsoPreventsSellButRebalanceMayIncludeIt() {
        OrderPlan buy = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "buy");
        OrderPlan sell = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "SELL", new BigDecimal("10000"), new BigDecimal("0.1"), "sell");
        RiskPolicy policy = new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE);

        assertThat(AutoInvestmentService.eligible(List.of(buy, sell), AutoInvestmentMode.KEEP_EXISTING_ASSETS, policy)).containsExactly(buy);
        assertThat(AutoInvestmentService.eligible(List.of(buy, sell), AutoInvestmentMode.REBALANCE_ALL, policy)).containsExactly(buy, sell);
    }
}

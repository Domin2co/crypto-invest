package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutoInvestmentExecutorTest {
    @Test void cashOnlyUsesExistingPaperPathForBuyAndSkipsSell() {
        PersistentPaperTradingService paper = mock(PersistentPaperTradingService.class);
        OrderPlan buy = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "auto-buy");
        OrderPlan sell = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "SELL", new BigDecimal("10000"), new BigDecimal("0.1"), "auto-sell");
        AutoInvestmentRequest buyRequest = new AutoInvestmentRequest(UUID.randomUUID(), buy, new BigDecimal("1000"), null, BigDecimal.ZERO);
        AutoInvestmentRequest sellRequest = new AutoInvestmentRequest(UUID.randomUUID(), sell, new BigDecimal("1000"), BigDecimal.ONE, BigDecimal.ZERO);
        var fill = new PaperTradingService.PaperFill("BTC", "BUY", new BigDecimal("10"), new BigDecimal("10000"), BigDecimal.ZERO, "FILLED", "auto-buy");
        when(paper.execute(any(), any(), any(), any(), any(), any())).thenReturn(fill);
        RiskPolicy policy = new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE);

        assertThat(new AutoInvestmentExecutor(paper).execute(List.of(buyRequest, sellRequest), AutoInvestmentMode.CASH_ONLY, policy)).containsExactly(fill);
        verify(paper).execute(buyRequest.orderPlanId(), buy, buyRequest.price(), null, BigDecimal.ZERO, policy);
        verifyNoMoreInteractions(paper);
    }
}

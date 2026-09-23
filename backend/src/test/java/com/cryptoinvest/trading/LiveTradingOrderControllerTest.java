package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.privateapi.ExchangeOrderChance;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import com.cryptoinvest.market.MarketPrice;
import com.cryptoinvest.portfolio.AccountReadService;
import com.cryptoinvest.portfolio.PortfolioReadService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

class LiveTradingOrderControllerTest {
    private final LiveTradingService trading = mock(LiveTradingService.class);
    private final AccountReadService accounts = mock(AccountReadService.class);
    private final PortfolioReadService portfolios = mock(PortfolioReadService.class);
    private final PaperOrderPlanRepository plans = mock(PaperOrderPlanRepository.class);
    private final ExchangePublicClient market = mock(ExchangePublicClient.class);
    private final UUID userId = UUID.randomUUID();
    private final Authentication authentication = authentication(userId);
    private LiveTradingOrderController controller() {
        when(market.exchange()).thenReturn(Exchange.UPBIT);
        return new LiveTradingOrderController(trading, accounts, portfolios, plans, List.of(market), new BigDecimal("0.35"));
    }
    private final String idem = "e2e-live-order-key-0001";

    @Test void constructsPlanFromOwnedAccountMarketAndBalanceBeforeCallingLiveService() {
        LiveTradingOrderController controller = controller();
        when(trading.recoverExisting(userId, idem)).thenReturn(Optional.empty());
        when(market.exchange()).thenReturn(Exchange.UPBIT);
        when(market.getPrice("KRW-BTC")).thenReturn(new MarketPrice(Exchange.UPBIT, "KRW-BTC", new BigDecimal("20000000"), BigDecimal.ONE, Instant.now()));
        when(accounts.getOrderChance(userId, Exchange.UPBIT, "KRW-BTC")).thenReturn(chance("25000", "0.2"));
        when(portfolios.read(userId, Exchange.UPBIT)).thenReturn(new PortfolioReadService.PortfolioView(Exchange.UPBIT,
                new BigDecimal("100000"), new BigDecimal("0.8"), List.of(
                        new PortfolioReadService.Position("KRW", new BigDecimal("80000"), BigDecimal.ZERO, BigDecimal.ONE,
                                new BigDecimal("80000"), new BigDecimal("0.8"), null, null),
                        new PortfolioReadService.Position("BTC", new BigDecimal("0.001"), new BigDecimal("10000000"),
                                new BigDecimal("20000000"), new BigDecimal("20000"), new BigDecimal("0.2"), null, null)), Instant.now()));
        UUID planId = UUID.randomUUID();
        when(plans.createOrFind(any(), any())).thenReturn(Optional.of(planId));
        LiveOrder order = new LiveOrder(UUID.randomUUID(), Exchange.UPBIT, "client-order", "exchange-order", "FILLED",
                new BigDecimal("0.0005"), new BigDecimal("10000"), BigDecimal.ONE, true);
        when(trading.execute(eq(planId), any(), any(), any())).thenReturn(order);

        var response = controller.execute(authentication, new LiveTradingOrderController.LiveOrderRequest(
                Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), null, idem));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().order()).isEqualTo(order);
        ArgumentCaptor<OrderPlan> plan = ArgumentCaptor.forClass(OrderPlan.class);
        verify(plans).createOrFind(plan.capture(), eq(null));
        assertThat(plan.getValue().userId()).isEqualTo(userId);
        assertThat(plan.getValue().amount()).isEqualByComparingTo("10000");
        assertThat(plan.getValue().projectedWeight()).isEqualByComparingTo(new BigDecimal("30000").divide(new BigDecimal("110000"), 18, java.math.RoundingMode.HALF_UP));
        verify(trading).requireNewOrderReady(userId);
    }

    @Test void confirmationGateRunsBeforeMarketOrPrivateAccountCalls() {
        LiveTradingOrderController controller = controller();
        when(trading.recoverExisting(userId, idem)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("confirmation required")).when(trading).requireNewOrderReady(userId);
        assertThatThrownBy(() -> controller.execute(authentication, new LiveTradingOrderController.LiveOrderRequest(
                Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), null, idem)))
                .hasMessageContaining("confirmation required");
        verifyNoInteractions(accounts, portfolios, plans);
        verify(market, never()).getPrice(any());
    }

    @Test void insufficientExchangeBalancePreventsPlanAndOrderCreation() {
        LiveTradingOrderController controller = controller();
        when(trading.recoverExisting(userId, idem)).thenReturn(Optional.empty());
        when(market.exchange()).thenReturn(Exchange.UPBIT);
        when(market.getPrice("KRW-BTC")).thenReturn(new MarketPrice(Exchange.UPBIT, "KRW-BTC", new BigDecimal("20000000"), BigDecimal.ONE, Instant.now()));
        when(accounts.getOrderChance(userId, Exchange.UPBIT, "KRW-BTC")).thenReturn(chance("10000", "0.2"));
        assertThatThrownBy(() -> controller.execute(authentication, new LiveTradingOrderController.LiveOrderRequest(
                Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), null, idem)))
                .hasMessageContaining("Insufficient available quote balance");
        verify(plans, never()).createOrFind(any(), any());
        verify(trading, never()).execute(any(), any(), any(), any());
    }

    private static ExchangeOrderChance chance(String quote, String base) {
        return new ExchangeOrderChance("KRW-BTC", "active", "KRW", "BTC", new BigDecimal(quote), new BigDecimal(base),
                new BigDecimal("5000"), new BigDecimal("5000"), new BigDecimal("1000000"), new BigDecimal("0.0005"), new BigDecimal("0.0005"));
    }
    private static Authentication authentication(UUID userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userId);
        return authentication;
    }
}

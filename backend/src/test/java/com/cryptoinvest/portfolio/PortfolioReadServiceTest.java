package com.cryptoinvest.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import com.cryptoinvest.market.MarketPrice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioReadServiceTest {
    @Test
    void calculatesUserPortfolioWithCashAndAssetWeights() {
        UUID userId = UUID.randomUUID();
        AccountReadService accounts = mock(AccountReadService.class);
        ExchangePublicClient market = mock(ExchangePublicClient.class);
        PortfolioTargetRepository targets = mock(PortfolioTargetRepository.class);
        when(market.exchange()).thenReturn(Exchange.UPBIT);
        when(accounts.getBalances(userId, Exchange.UPBIT)).thenReturn(List.of(
                new ExchangeBalance(Exchange.UPBIT, "KRW", new BigDecimal("800"), BigDecimal.ZERO),
                new ExchangeBalance(Exchange.UPBIT, "BTC", new BigDecimal("2"), new BigDecimal("50"))));
        when(market.getPrice("KRW-BTC")).thenReturn(new MarketPrice(Exchange.UPBIT, "KRW-BTC", new BigDecimal("100"), BigDecimal.ZERO, Instant.EPOCH));
        when(targets.findByUserAndExchange(userId, Exchange.UPBIT)).thenReturn(java.util.Map.of("BTC", new BigDecimal("0.35")));

        PortfolioReadService.PortfolioView result = new PortfolioReadService(accounts, List.of(market), targets).read(userId, Exchange.UPBIT);

        assertThat(result.totalEvaluatedAmount()).isEqualByComparingTo("1000");
        assertThat(result.cashWeight()).isEqualByComparingTo("0.8");
        assertThat(result.positions()).extracting(PortfolioReadService.Position::currency).containsExactly("KRW", "BTC");
        assertThat(result.positions().get(1).weight()).isEqualByComparingTo("0.2");
        assertThat(result.positions().get(1).rebalancingGap()).isEqualByComparingTo("0.15");
    }
}

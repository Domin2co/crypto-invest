package com.cryptoinvest.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioEngineTest {
    @Test void calculatesValueWeightAndGapWithoutFloatingPoint() {
        ExchangeBalance btc = new ExchangeBalance(Exchange.UPBIT, "BTC", new BigDecimal("2"), BigDecimal.ZERO);
        assertThat(PortfolioEngine.evaluatedAmount(btc, new BigDecimal("100"))).isEqualByComparingTo("200");
        assertThat(PortfolioEngine.weight(new BigDecimal("200"), List.of(new BigDecimal("200"), new BigDecimal("800")))).isEqualByComparingTo("0.2");
        assertThat(PortfolioEngine.rebalancingGap(new BigDecimal("0.2"), new BigDecimal("0.35"))).isEqualByComparingTo("0.15");
    }
}

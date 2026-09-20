package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaperTradingServiceTest {
    @Test void fillsPaperOrderOnceAndIncludesFee() {
        PaperTradingService service = new PaperTradingService(new BigDecimal("20000"));
        var fill = service.buy("BTC", new BigDecimal("10000"), new BigDecimal("1000"), new BigDecimal("0.001"), "idempotent-key");
        assertThat(fill.fee()).isEqualByComparingTo("10");
        assertThat(service.buy("BTC", new BigDecimal("10000"), new BigDecimal("1000"), new BigDecimal("0.001"), "idempotent-key")).isEqualTo(fill);
        assertThat(service.balance("KRW")).isEqualByComparingTo("9990");
        assertThat(service.sell("BTC", new BigDecimal("1"), new BigDecimal("1000"), new BigDecimal("0.001"), "sell-key").fee()).isEqualByComparingTo("1");
        assertThatThrownBy(() -> service.sell("BTC", new BigDecimal("100"), new BigDecimal("1000"), BigDecimal.ZERO, "too-much"))
                .isInstanceOf(IllegalStateException.class);
    }
}

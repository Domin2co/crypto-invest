package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderPlannerTest {
    @Test void appliesFeeBeforeQuantityRounding() {
        ExchangeRules rules = new ExchangeRules(new BigDecimal("5000"), 8, new BigDecimal("0.001"));
        assertThat(OrderPlanner.fee(new BigDecimal("10000"), rules)).isEqualByComparingTo("10");
        assertThat(OrderPlanner.quantity(new BigDecimal("10000"), new BigDecimal("1000"), rules)).isEqualByComparingTo("9.99000999");
    }
}

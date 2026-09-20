package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class AutoInvestmentPolicyTest {
    @Test void limitsAutomaticSellsToRebalanceAll() {
        assertThat(AutoInvestmentPolicy.allowsSell(AutoInvestmentMode.REBALANCE_ALL)).isTrue();
        assertThat(AutoInvestmentPolicy.allowsSell(AutoInvestmentMode.KEEP_EXISTING_ASSETS)).isFalse();
        assertThat(AutoInvestmentPolicy.allowsSell(AutoInvestmentMode.CASH_ONLY)).isFalse();
    }
}

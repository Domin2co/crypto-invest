package com.cryptoinvest.exchange.privateapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ExchangeOrderChanceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void parsesExchangeOrderLimitsAndAvailableBalances() throws Exception {
        ExchangeOrderChance chance = ExchangeOrderChance.from(mapper.readTree("""
                {"market":{"id":"KRW-BTC","state":"active","bid":{"min_total":"5000"},
                  "ask":{"min_total":"5000"},"max_total":"1000000000"},
                 "bid_account":{"currency":"KRW","balance":"25000"},
                 "ask_account":{"currency":"BTC","balance":"0.2"},"bid_fee":"0.0005","ask_fee":"0.0005"}
                """));

        assertThat(chance.market()).isEqualTo("KRW-BTC");
        assertThat(chance.availableQuote()).isEqualByComparingTo("25000");
        assertThat(chance.availableBase()).isEqualByComparingTo("0.2");
        assertThat(chance.minimumBidAmount()).isEqualByComparingTo("5000");
        assertThat(chance.maximumOrderAmount()).isEqualByComparingTo("1000000000");
        assertThat(chance.bidFee()).isEqualByComparingTo("0.0005");
    }

    @Test void rejectsMissingOrNegativeOrderRules() throws Exception {
        assertThatThrownBy(() -> ExchangeOrderChance.from(mapper.readTree("{}")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Exchange order rules are unavailable");
        assertThatThrownBy(() -> ExchangeOrderChance.from(mapper.readTree("""
                {"market":{"id":"KRW-BTC","state":"active","bid":{"min_total":"-1"},
                  "ask":{"min_total":"5000"},"max_total":"1000000000"},
                 "bid_account":{"currency":"KRW","balance":"25000"},
                 "ask_account":{"currency":"BTC","balance":"0.2"},"bid_fee":"0.0005","ask_fee":"0.0005"}
                """)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Exchange order rules are unavailable");
    }
}

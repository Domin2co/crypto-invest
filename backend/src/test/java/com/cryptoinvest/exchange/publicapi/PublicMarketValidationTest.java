package com.cryptoinvest.exchange.publicapi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PublicMarketValidationTest {
    @Test
    void rejectsMalformedMarketAndUnsafeCandleCount() {
        assertThatThrownBy(() -> PublicClientValidation.check("btc", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicClientValidation.check("KRW-BTC", 201)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class PublicClientValidation extends AbstractPublicClient {
        private PublicClientValidation() { super(new com.fasterxml.jackson.databind.ObjectMapper()); }
        static void check(String market, int count) { AbstractPublicClient.validate(market, count); }
    }
}

package com.cryptoinvest.exchange.credential;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtSignerTest {
    @Test void createsDifferentThreePartBearerTokens() {
        JwtSigner signer = new JwtSigner();
        String token = signer.bearerToken(new ExchangeCredentials("access", "secret"));
        assertThat(token).startsWith("Bearer ").doesNotContain("secret");
        assertThat(token.substring(7).split("\\.")).hasSize(3);
    }
}

package com.cryptoinvest.exchange.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtSignerTest {
    @Test void createsDifferentThreePartBearerTokens() {
        JwtSigner signer = new JwtSigner();
        String token = signer.bearerToken(new ExchangeCredentials("access", "secret"));
        assertThat(token).startsWith("Bearer ").doesNotContain("secret");
        assertThat(token.substring(7).split("\\.")).hasSize(3);
    }

    @Test void createsBithumbCompatibleQueryTokenAndHmacSignature() throws Exception {
        String credentialsSecret = "secret";
        String token = new JwtSigner().bearerTokenForQuery(
                new ExchangeCredentials("access", credentialsSecret), "market=KRW-BTC", "HS256", true);
        String[] parts = token.substring("Bearer ".length()).split("\\.");
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.readTree(Base64.getUrlDecoder().decode(parts[0])).path("alg").asText()).isEqualTo("HS256");
        var payload = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(payload.path("timestamp").asLong()).isPositive();
        assertThat(payload.path("query_hash").asText()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-512").digest("market=KRW-BTC".getBytes(StandardCharsets.UTF_8))));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(credentialsSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertThat(Base64.getUrlDecoder().decode(parts[2]))
                .containsExactly(mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)));
    }
}

package com.cryptoinvest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AppTokenServiceTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    @Test void signsAndVerifiesTheAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        AppTokenService tokens = new AppTokenService(SECRET, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertThat(tokens.verify(tokens.issue(userId))).isEqualTo(userId);
    }
    @Test void rejectsTamperedTokens() {
        AppTokenService tokens = new AppTokenService(SECRET, Clock.systemUTC());
        assertThatThrownBy(() -> tokens.verify(tokens.issue(UUID.randomUUID()) + "x")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expiresTokensAfterThirtyMinutes() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String token = new AppTokenService(SECRET, Clock.fixed(now, ZoneOffset.UTC)).issue(UUID.randomUUID());
        AppTokenService afterExpiry = new AppTokenService(SECRET, Clock.offset(Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(30)));
        assertThatThrownBy(() -> afterExpiry.verify(token)).isInstanceOf(IllegalArgumentException.class).hasMessage("Expired access token");
    }
    @Test void rejectsLegacyEightHourTokens() throws Exception {
        String payload = UUID.randomUUID() + "." + Instant.parse("2026-01-01T08:00:00Z").getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String legacyToken = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + signature;
        AppTokenService tokens = new AppTokenService(SECRET, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertThatThrownBy(() -> tokens.verify(legacyToken)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid access token");
    }
    @Test void missingSigningKeyFailsAtTokenIssue() {
        assertThatThrownBy(() -> new AppTokenService("", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_TOKEN_SECRET");
    }
}

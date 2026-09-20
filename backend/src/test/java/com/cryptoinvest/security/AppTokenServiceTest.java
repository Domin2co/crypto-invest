package com.cryptoinvest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
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
}

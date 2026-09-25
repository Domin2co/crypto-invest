package com.cryptoinvest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    @Test void storesOnlyBcryptHashAndIssuesSignedToken() {
        UserAuthRepository users = mock(UserAuthRepository.class);
        UserConsentRepository consents = mock(UserConsentRepository.class);
        UUID id = UUID.randomUUID();
        when(users.existsByEmail("user@example.com")).thenReturn(false);
        when(users.create(anyString(), anyString())).thenReturn(id);
        AppTokenService tokens = new AppTokenService(Base64.getEncoder().encodeToString(new byte[32]), java.time.Clock.systemUTC());

        String token = new AuthService(users, consents, tokens, mock(EmailVerificationService.class), mock(TotpMfaService.class)).register("user@example.com", "long-enough-password", true, true, "2026-09-21");

        assertThat(tokens.verify(token)).isEqualTo(id);
        verify(consents).grant(id, "PRIVACY", "2026-09-21");
        verify(consents).grant(id, "MARKETING", "2026-09-21");
    }
    @Test void rejectsWrongPassword() {
        UserAuthRepository users = mock(UserAuthRepository.class);
        UserConsentRepository consents = mock(UserConsentRepository.class);
        AppTokenService tokens = new AppTokenService(Base64.getEncoder().encodeToString(new byte[32]), java.time.Clock.systemUTC());
        when(users.findEnabledByEmail("user@example.com")).thenReturn(Optional.of(new UserAuthRepository.UserCredentials(UUID.randomUUID(), new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("correct-password"))));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AuthService(users, consents, tokens, mock(EmailVerificationService.class), mock(TotpMfaService.class)).login("user@example.com", "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void matchesRfc6238Sha1Vector() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(TotpMfaService.totp(secret, 1)).isEqualTo("287082");
        assertThat(TotpMfaService.matchingStep(secret, "287082", 59)).isEqualTo(1L);
        assertThat(TotpMfaService.matchingStep(secret, "000000", 59)).isNull();
    }
}

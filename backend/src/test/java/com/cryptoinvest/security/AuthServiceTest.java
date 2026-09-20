package com.cryptoinvest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    @Test void storesOnlyBcryptHashAndIssuesSignedToken() {
        UserAuthRepository users = mock(UserAuthRepository.class);
        UUID id = UUID.randomUUID();
        when(users.existsByEmail("user@example.com")).thenReturn(false);
        when(users.create(anyString(), anyString())).thenReturn(id);
        AppTokenService tokens = new AppTokenService(Base64.getEncoder().encodeToString(new byte[32]), java.time.Clock.systemUTC());

        String token = new AuthService(users, tokens).register("user@example.com", "long-enough-password");

        assertThat(tokens.verify(token)).isEqualTo(id);
    }
    @Test void rejectsWrongPassword() {
        UserAuthRepository users = mock(UserAuthRepository.class);
        AppTokenService tokens = new AppTokenService(Base64.getEncoder().encodeToString(new byte[32]), java.time.Clock.systemUTC());
        when(users.findEnabledByEmail("user@example.com")).thenReturn(Optional.of(new UserAuthRepository.UserCredentials(UUID.randomUUID(), new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("correct-password"))));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AuthService(users, tokens).login("user@example.com", "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

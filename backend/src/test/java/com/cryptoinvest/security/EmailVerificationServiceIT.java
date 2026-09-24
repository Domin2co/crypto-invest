package com.cryptoinvest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;

@SpringBootTest
class EmailVerificationServiceIT {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.auth-token-secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.credential-encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @Autowired EmailVerificationService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mailSender;

    @Test
    void sendsHashedCodePersistsFailedAttemptsAndConsumesSingleUseToken() {
        String email = "verify-" + UUID.randomUUID() + "@example.com";
        UUID challengeId = service.start(email, null, "SIGNUP");
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        String code = sent.getValue().getText().substring(sent.getValue().getText().indexOf(": ") + 2, sent.getValue().getText().indexOf("\n"));

        String wrongCode = code.equals("000000") ? "000001" : "000000";
        assertThatThrownBy(() -> service.confirm(challengeId, email, wrongCode, "SIGNUP", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT attempts FROM account_email_verification WHERE challenge_id = ?", Integer.class, challengeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT code_hash FROM account_email_verification WHERE challenge_id = ?", String.class, challengeId)).isNotEqualTo(code);

        String token = service.confirm(challengeId, email, code, "SIGNUP", null);
        service.consumeSignup(email, token);
        assertThatThrownBy(() -> service.consumeSignup(email, token)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT verified_token_hash FROM account_email_verification WHERE challenge_id = ?", String.class, challengeId)).isNotEqualTo(token);
        jdbc.update("DELETE FROM account_email_verification WHERE challenge_id = ?", challengeId);
    }
}
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
    @Autowired UserAuthRepository users;
    @Autowired AuthService authService;
    @Autowired AppTokenService tokenService;
    @Autowired TotpMfaService mfa;
    @MockitoBean JavaMailSender mailSender;

    @Test
    void passwordResetUsesEmailCodeAndSingleUseToken() {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        UUID userId = UUID.randomUUID();
        String oldPassword = "OldPass!123";
        String newPassword = "NewPass!456";
        jdbc.update("INSERT INTO app_user (id, email, password_hash, email_verified) VALUES (?, ?, ?, TRUE)", userId, email,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(oldPassword));
        service.start(email, userId, "PASSWORD_RESET");
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        String code = sent.getValue().getText().substring(sent.getValue().getText().indexOf(": ") + 2, sent.getValue().getText().indexOf("\n"));

        String priorSession = authService.login(email, oldPassword);
        String token = service.confirmPasswordReset(email, code, userId);
        authService.resetPassword(userId, email, token, newPassword);
        assertThatThrownBy(() -> tokenService.verify(priorSession)).isInstanceOf(IllegalArgumentException.class);
        assertThat(authService.login(email, newPassword)).isNotBlank();
        assertThatThrownBy(() -> authService.resetPassword(userId, email, token, oldPassword))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("DELETE FROM account_email_verification WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

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

    @Test
    void totpLoginUsesEncryptedSecretReplayProtectionAndOneUseRecoveryCodes() {
        String email = "mfa-" + UUID.randomUUID() + "@example.com";
        UUID userId = UUID.randomUUID();
        String password = "CurrentPass!123";
        jdbc.update("INSERT INTO app_user (id, email, password_hash, email_verified) VALUES (?, ?, ?, TRUE)", userId, email,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));

        TotpMfaService.Enrollment enrollment = mfa.begin(userId, email);
        assertThat(jdbc.queryForObject("SELECT encrypted_secret FROM user_totp_mfa WHERE user_id = ?", String.class, userId))
                .isNotEqualTo(enrollment.secret());
        long currentStep = java.time.Instant.now().getEpochSecond() / 30;
        java.util.List<String> recoveryCodes = mfa.confirm(userId, TotpMfaService.totp(enrollment.secret(), currentStep));
        assertThat(authService.login(email, password, null).mfaRequired()).isTrue();

        String loginCode = TotpMfaService.totp(enrollment.secret(), currentStep + 1);
        assertThat(authService.login(email, password, loginCode).accessToken()).isNotBlank();
        assertThatThrownBy(() -> authService.login(email, password, loginCode)).isInstanceOf(IllegalArgumentException.class);

        String recoveryCode = recoveryCodes.get(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_totp_recovery_code WHERE user_id = ? AND code_hash = ?", Integer.class, userId, recoveryCode)).isZero();
        assertThat(authService.login(email, password, recoveryCode).accessToken()).isNotBlank();
        assertThatThrownBy(() -> authService.login(email, password, recoveryCode)).isInstanceOf(IllegalArgumentException.class);
        mfa.disable(userId, recoveryCodes.get(1));
        assertThat(mfa.isEnabled(userId)).isFalse();
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }
}
package com.cryptoinvest.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입·로그인에서 비밀번호 원문을 저장하지 않고 서명 토큰만 반환한다. */
@Service
public class AuthService {
    private final UserAuthRepository users;
    private final UserConsentRepository consents;
    private final AppTokenService tokens;
    private final EmailVerificationService emailVerification;
    private final TotpMfaService mfa;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    public AuthService(UserAuthRepository users, UserConsentRepository consents, AppTokenService tokens, EmailVerificationService emailVerification, TotpMfaService mfa) {
        this.users = users; this.consents = consents; this.tokens = tokens; this.emailVerification = emailVerification; this.mfa = mfa;
    }
    @Transactional
    public String register(String email, String password, boolean privacyAccepted, boolean marketingAccepted, String policyVersion) {
        if (!privacyAccepted) throw new IllegalArgumentException("Privacy consent is required");
        if (users.existsByEmail(email)) throw new IllegalArgumentException("Email is already registered");
        java.util.UUID userId = users.create(email, passwordEncoder.encode(password));
        consents.grant(userId, "PRIVACY", policyVersion);
        if (marketingAccepted) consents.grant(userId, "MARKETING", policyVersion);
        return tokens.issue(userId);
    }
    public void changePassword(java.util.UUID userId, String currentPassword, String newPassword) {
        var user = users.findEnabledById(userId).filter(value -> passwordEncoder.matches(currentPassword, value.passwordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        users.updatePassword(user.id(), passwordEncoder.encode(newPassword));
    }
    @Transactional
    public void resetPassword(java.util.UUID userId, String email, String verificationToken, String newPassword) {
        emailVerification.consumePasswordReset(userId, email, verificationToken);
        users.updatePassword(userId, passwordEncoder.encode(newPassword));
        users.revokeAuthTokens(userId);
    }
    public String login(String email, String password) {
        LoginResult result = login(email, password, null);
        if (result.mfaRequired()) throw new IllegalArgumentException("Authenticator code required");
        return result.accessToken();
    }

    public LoginResult login(String email, String password, String secondFactorCode) {
        var user = users.findEnabledByEmail(email).filter(value -> passwordEncoder.matches(password, value.passwordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (mfa.isEnabled(user.id())) {
            if (secondFactorCode == null || secondFactorCode.isBlank()) return new LoginResult(null, true);
            if (!mfa.verify(user.id(), secondFactorCode)) throw new IllegalArgumentException("Invalid credentials");
        }
        return new LoginResult(tokens.issue(user.id()), false);
    }

    public boolean verifyCurrentPassword(java.util.UUID userId, String password) {
        return users.findEnabledById(userId).filter(value -> passwordEncoder.matches(password, value.passwordHash())).isPresent();
    }

    public record LoginResult(String accessToken, boolean mfaRequired) {}
}

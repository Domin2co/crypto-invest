package com.cryptoinvest.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 토큰만 응답하며 비밀번호·거래소 API Key는 절대 응답하지 않는다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final String privacyPolicyVersion;
    private final EmailVerificationService emailVerification;
    public AuthController(AuthService authService, EmailVerificationService emailVerification, @Value("${app.privacy-policy-version}") String privacyPolicyVersion) {
        this.authService = authService; this.emailVerification = emailVerification; this.privacyPolicyVersion = privacyPolicyVersion;
    }
    @PostMapping("/email-verification") @ResponseStatus(HttpStatus.ACCEPTED)
    public ChallengeResponse startSignupVerification(@Valid @RequestBody EmailRequest request) {
        return new ChallengeResponse(emailVerification.start(request.email(), null, "SIGNUP"));
    }
    @PostMapping("/email-verification/confirm") public VerificationResponse confirmSignupVerification(@Valid @RequestBody EmailCodeRequest request) {
        return new VerificationResponse(emailVerification.confirm(request.challengeId(), request.email(), request.code(), "SIGNUP", null));
    }
    @PostMapping("/register") @Transactional public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        emailVerification.consumeSignup(request.email(), request.verificationToken());
        return new TokenResponse(authService.register(request.email().trim().toLowerCase(java.util.Locale.ROOT), request.password(), request.privacyAccepted(), request.marketingAccepted(), privacyPolicyVersion));
    }
    @PatchMapping("/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest request) { authService.changePassword((UUID) authentication.getPrincipal(), request.currentPassword(), request.newPassword()); }
    @PostMapping("/login") public TokenResponse login(@Valid @RequestBody LoginRequest request) { return new TokenResponse(authService.login(request.email(), request.password())); }
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[!-~]{10,20}$";
    public record EmailRequest(@Email @NotBlank @Size(max = 254) String email) {}
    public record EmailCodeRequest(@jakarta.validation.constraints.NotNull UUID challengeId, @Email @NotBlank @Size(max = 254) String email, @Pattern(regexp = "^[0-9]{6}$") String code) {}
    public record RegisterRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(min = 10, max = 20) @Pattern(regexp = PASSWORD_PATTERN) String password, String verificationToken,
            boolean privacyAccepted, boolean marketingAccepted) {}
    public record LoginRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}
    public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 20) @Pattern(regexp = PASSWORD_PATTERN) String newPassword) {}
    public record ChallengeResponse(UUID challengeId) {}
    public record VerificationResponse(String verificationToken) {}
    public record TokenResponse(String accessToken) {}
}

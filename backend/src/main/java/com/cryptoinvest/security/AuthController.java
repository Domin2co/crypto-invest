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
    private final UserAuthRepository users;
    private final AuthRateLimitService rateLimits;
    private final ClientIpResolver clientIpResolver;
    public AuthController(AuthService authService, EmailVerificationService emailVerification, UserAuthRepository users, AuthRateLimitService rateLimits, ClientIpResolver clientIpResolver, @Value("${app.privacy-policy-version}") String privacyPolicyVersion) {
        this.authService = authService; this.emailVerification = emailVerification; this.users = users; this.rateLimits = rateLimits; this.clientIpResolver = clientIpResolver; this.privacyPolicyVersion = privacyPolicyVersion;
    }
    @PostMapping("/email-verification") @ResponseStatus(HttpStatus.ACCEPTED)
    public ChallengeResponse startSignupVerification(@Valid @RequestBody EmailRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        rateLimits.check("signup-code", request.email(), clientIpResolver.resolve(httpRequest), 3, 20, java.time.Duration.ofHours(1));
        return new ChallengeResponse(emailVerification.start(request.email(), null, "SIGNUP"));
    }
    @PostMapping("/email-verification/confirm") public VerificationResponse confirmSignupVerification(@Valid @RequestBody EmailCodeRequest request) {
        return new VerificationResponse(emailVerification.confirm(request.challengeId(), request.email(), request.code(), "SIGNUP", null));
    }
    @PostMapping("/password-reset") @ResponseStatus(HttpStatus.ACCEPTED)
    public GenericMessage requestPasswordReset(@Valid @RequestBody EmailRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        rateLimits.check("password-reset", request.email(), clientIpResolver.resolve(httpRequest), 3, 20, java.time.Duration.ofHours(1));
        users.findEnabledIdByEmail(request.email()).ifPresent(userId -> {
            try { emailVerification.start(request.email(), userId, "PASSWORD_RESET"); }
            catch (org.springframework.web.server.ResponseStatusException ignored) { /* Keep account existence private. */ }
        });
        return new GenericMessage("If this email is registered, a verification code has been sent.");
    }
    @PostMapping("/password-reset/confirm")
    public VerificationResponse confirmPasswordReset(@Valid @RequestBody PasswordResetCodeRequest request) {
        UUID userId = users.findEnabledIdByEmail(request.email()).orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));
        return new VerificationResponse(emailVerification.confirmPasswordReset(request.email(), request.code(), userId));
    }
    @PostMapping("/password-reset/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completePasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        UUID userId = users.findEnabledIdByEmail(request.email()).orElseThrow(() -> new IllegalArgumentException("Email verification is required"));
        authService.resetPassword(userId, request.email(), request.verificationToken(), request.newPassword());
    }
    @PostMapping("/register") @Transactional public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        emailVerification.consumeSignup(request.email(), request.verificationToken());
        return new TokenResponse(authService.register(request.email().trim().toLowerCase(java.util.Locale.ROOT), request.password(), request.privacyAccepted(), request.marketingAccepted(), privacyPolicyVersion));
    }
    @PatchMapping("/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest request) { authService.changePassword((UUID) authentication.getPrincipal(), request.currentPassword(), request.newPassword()); }
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        rateLimits.check("login", request.email(), clientIpResolver.resolve(httpRequest), 10, 60, java.time.Duration.ofMinutes(15));
        AuthService.LoginResult result = authService.login(request.email(), request.password(), request.authenticatorCode());
        return new LoginResponse(result.accessToken(), result.mfaRequired());
    }
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[!-~]{10,20}$";
    public record EmailRequest(@Email @NotBlank @Size(max = 254) String email) {}
    public record EmailCodeRequest(@jakarta.validation.constraints.NotNull UUID challengeId, @Email @NotBlank @Size(max = 254) String email, @Pattern(regexp = "^[0-9]{6}$") String code) {}
    public record RegisterRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(min = 10, max = 20) @Pattern(regexp = PASSWORD_PATTERN) String password, String verificationToken,
            boolean privacyAccepted, boolean marketingAccepted) {}
    public record LoginRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password, @Size(max = 32) String authenticatorCode) {}
    public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 20) @Pattern(regexp = PASSWORD_PATTERN) String newPassword) {}
    public record PasswordResetCodeRequest(@Email @NotBlank @Size(max = 254) String email, @Pattern(regexp = "^[0-9]{6}$") String code) {}
    public record PasswordResetRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String verificationToken, @NotBlank @Size(min = 10, max = 20) @Pattern(regexp = PASSWORD_PATTERN) String newPassword) {}
    public record GenericMessage(String message) {}
    public record ChallengeResponse(UUID challengeId) {}
    public record VerificationResponse(String verificationToken) {}
    public record TokenResponse(String accessToken) {}
    public record LoginResponse(String accessToken, boolean mfaRequired) {}
}

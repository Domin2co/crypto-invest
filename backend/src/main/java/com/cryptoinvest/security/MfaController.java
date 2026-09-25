package com.cryptoinvest.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/mfa")
public class MfaController {
    private final TotpMfaService mfa;
    private final AuthService auth;
    private final UserAuthRepository users;
    private final AuthRateLimitService limits;
    private final ClientIpResolver clientIp;

    public MfaController(TotpMfaService mfa, AuthService auth, UserAuthRepository users, AuthRateLimitService limits, ClientIpResolver clientIp) {
        this.mfa = mfa; this.auth = auth; this.users = users; this.limits = limits; this.clientIp = clientIp;
    }

    @GetMapping
    public Status status(Authentication authentication) { return new Status(mfa.isEnabled((UUID) authentication.getPrincipal())); }

    @PostMapping("/setup")
    public TotpMfaService.Enrollment setup(Authentication authentication, @Valid @RequestBody PasswordRequest request, jakarta.servlet.http.HttpServletRequest http) {
        UUID userId = (UUID) authentication.getPrincipal();
        limits.check("mfa-setup", email(userId), clientIp.resolve(http), 5, 20, Duration.ofMinutes(15));
        requirePassword(userId, request.currentPassword());
        return mfa.begin(userId, email(userId));
    }

    @PostMapping("/confirm") @Transactional
    public RecoveryCodes confirm(Authentication authentication, @Valid @RequestBody CodeRequest request, jakarta.servlet.http.HttpServletRequest http) {
        UUID userId = (UUID) authentication.getPrincipal();
        limits.check("mfa-confirm", email(userId), clientIp.resolve(http), 5, 20, Duration.ofMinutes(15));
        return new RecoveryCodes(mfa.confirm(userId, request.code()));
    }

    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(Authentication authentication, @Valid @RequestBody DisableRequest request, jakarta.servlet.http.HttpServletRequest http) {
        UUID userId = (UUID) authentication.getPrincipal();
        limits.check("mfa-disable", email(userId), clientIp.resolve(http), 5, 20, Duration.ofMinutes(15));
        requirePassword(userId, request.currentPassword());
        mfa.disable(userId, request.code());
    }

    private void requirePassword(UUID userId, String password) {
        if (!auth.verifyCurrentPassword(userId, password)) throw new IllegalArgumentException("Invalid credentials");
    }

    private String email(UUID userId) { return users.findEnabledEmail(userId).orElseThrow(() -> new IllegalArgumentException("Account is unavailable")); }

    public record Status(boolean enabled) {}
    public record PasswordRequest(@NotBlank @Size(max = 128) String currentPassword) {}
    public record CodeRequest(@NotBlank @Size(max = 32) String code) {}
    public record DisableRequest(@NotBlank @Size(max = 128) String currentPassword, @NotBlank @Size(max = 32) String code) {}
    public record RecoveryCodes(List<String> recoveryCodes) {}
}

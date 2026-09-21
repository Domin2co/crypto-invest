package com.cryptoinvest.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    public AuthController(AuthService authService, @Value("${app.privacy-policy-version}") String privacyPolicyVersion) {
        this.authService = authService; this.privacyPolicyVersion = privacyPolicyVersion;
    }
    @PostMapping("/register") public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return new TokenResponse(authService.register(request.email(), request.password(), request.privacyAccepted(), request.marketingAccepted(), privacyPolicyVersion));
    }
    @PostMapping("/login") public TokenResponse login(@Valid @RequestBody LoginRequest request) { return new TokenResponse(authService.login(request.email(), request.password())); }
    public record LoginRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(min = 12, max = 128) String password) {}
    public record RegisterRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(min = 12, max = 128) String password,
            boolean privacyAccepted, boolean marketingAccepted) {}
    public record TokenResponse(String accessToken) {}
}

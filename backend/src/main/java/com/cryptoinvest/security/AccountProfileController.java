package com.cryptoinvest.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 닉네임은 사용자 ID에서만 저장하며, 등록이 끝날 때까지 보호 API 접근은 보안 필터에서 차단한다. */
@RestController
@RequestMapping("/api/account")
public class AccountProfileController {
    private static final String NICKNAME_PATTERN = "[A-Za-z0-9가-힣]{2,8}";
    private final UserAuthRepository users;
    private final EmailVerificationService emailVerification;
    private final AppTokenService tokens;

    public AccountProfileController(UserAuthRepository users, EmailVerificationService emailVerification, AppTokenService tokens) { this.users = users; this.emailVerification = emailVerification; this.tokens = tokens; }

    @PostMapping("/session/extend")
    public SessionExtensionResponse extendSession(Authentication authentication) {
        return new SessionExtensionResponse(tokens.issue((UUID) authentication.getPrincipal()));
    }

    @GetMapping("/profile")
    public Profile profile(Authentication authentication) {
        UUID userId=(UUID)authentication.getPrincipal();
        String nickname=users.nickname(userId).orElse(null);
        return new Profile(nickname,nickname==null,users.role(userId));
    }

    @GetMapping("/email-status")
    public UserAuthRepository.EmailStatus emailStatus(Authentication authentication) { return users.emailStatus((UUID) authentication.getPrincipal()); }

    @PostMapping("/email-verification") @ResponseStatus(HttpStatus.ACCEPTED)
    public ChallengeResponse startEmailChange(Authentication authentication, @Valid @RequestBody EmailChangeRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        if (!users.emailAvailable(request.email())) throw new EmailTakenException();
        Instant availableAt = users.emailChangeAvailableAt(userId);
        if (availableAt != null && Instant.now().isBefore(availableAt)) throw new EmailChangeCooldownException(availableAt);
        return new ChallengeResponse(emailVerification.start(request.email(), userId, "EMAIL_CHANGE"));
    }

    @PostMapping("/email-verification/confirm")
    public EmailVerificationResponse confirmEmailChange(Authentication authentication, @Valid @RequestBody EmailChangeCodeRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        return new EmailVerificationResponse(emailVerification.confirm(request.challengeId(), request.email(), request.code(), "EMAIL_CHANGE", userId));
    }

    @PatchMapping("/email") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void updateEmail(Authentication authentication, @Valid @RequestBody EmailUpdateRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        Instant availableAt = users.emailChangeAvailableAt(userId);
        if (availableAt != null && Instant.now().isBefore(availableAt)) throw new EmailChangeCooldownException(availableAt);
        if (!users.emailAvailable(request.email())) throw new EmailTakenException();
        emailVerification.consumeEmailChange(userId, request.email(), request.verificationToken());
        try { users.updateEmail(userId, request.email().trim().toLowerCase(java.util.Locale.ROOT)); } catch (DuplicateKeyException exception) { throw new EmailTakenException(); }
    }

    @GetMapping("/nickname/availability")
    public NicknameAvailability availability(Authentication authentication, @RequestParam String nickname) {
        boolean valid = nickname.matches("^" + NICKNAME_PATTERN + "$");
        boolean available = valid && users.nicknameAvailable(nickname, (UUID) authentication.getPrincipal());
        return new NicknameAvailability(valid, available);
    }

    @PostMapping("/nickname")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateNickname(Authentication authentication, @Valid @RequestBody NicknameRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        if (!users.nicknameAvailable(request.nickname(), userId)) throw new NicknameTakenException();
        try {
            users.setNickname(userId, request.nickname());
        } catch (DuplicateKeyException exception) {
            throw new NicknameTakenException();
        }
    }

    public record NicknameRequest(@NotBlank @Size(min = 2, max = 8) @Pattern(regexp = "^[A-Za-z0-9가-힣]{2,8}$") String nickname) {}
    public record EmailChangeRequest(@Email @NotBlank @Size(max = 254) String email) {}
    public record EmailChangeCodeRequest(@NotNull UUID challengeId, @Email @NotBlank @Size(max = 254) String email, @Pattern(regexp = "^[0-9]{6}$") String code) {}
    public record EmailUpdateRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank String verificationToken) {}
    public record ChallengeResponse(UUID challengeId) {}
    public record EmailVerificationResponse(String verificationToken) {}
    public static final class EmailChangeCooldownException extends RuntimeException {
        private final Instant availableAt;
        public EmailChangeCooldownException(Instant availableAt) { this.availableAt = availableAt; }
        public Instant availableAt() { return availableAt; }
    }
    public record Profile(String nickname, boolean nicknameRequired, String role) {}
    public record SessionExtensionResponse(String accessToken) {}
    public record NicknameAvailability(boolean valid, boolean available) {}

    public static final class NicknameTakenException extends RuntimeException {}
    public static final class EmailTakenException extends RuntimeException {}
}
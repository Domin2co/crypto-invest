package com.cryptoinvest.security;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/** 정보주체가 본인 정보·동의를 확인하고 선택 동의를 철회하거나 계정 삭제를 요청하는 API다. */
@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {
    private final UserAuthRepository users;
    private final UserConsentRepository consents;
    private final String policyVersion;
    public PrivacyController(UserAuthRepository users, UserConsentRepository consents,
            @Value("${app.privacy-policy-version}") String policyVersion) {
        this.users = users; this.consents = consents; this.policyVersion = policyVersion;
    }

    @GetMapping("/me")
    public PrivacyExport export(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        String email = users.findEnabledEmail(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new PrivacyExport(email, consents.findByUserId(userId));
    }

    @PatchMapping("/marketing-consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marketing(Authentication authentication, @RequestBody MarketingConsent request) {
        UUID userId = (UUID) authentication.getPrincipal();
        if (request.accepted()) consents.grant(userId, "MARKETING", policyVersion);
        else consents.withdraw(userId, "MARKETING");
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(Authentication authentication) {
        users.anonymizeAndDisable((UUID) authentication.getPrincipal());
    }

    public record MarketingConsent(boolean accepted) {}
    public record PrivacyExport(String email, List<UserConsentRepository.Consent> consents) {}
}

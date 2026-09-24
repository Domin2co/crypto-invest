package com.cryptoinvest.security;

import com.cryptoinvest.trading.PaperLeagueRepository;
import com.cryptoinvest.market.MarketDiscussionRepository;

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
    private final String paperLeagueConsentVersion;
    private final PaperLeagueRepository paperLeague;
    private final MarketDiscussionRepository discussions;
    public PrivacyController(UserAuthRepository users, UserConsentRepository consents, PaperLeagueRepository paperLeague, MarketDiscussionRepository discussions,
            @Value("${app.privacy-policy-version}") String policyVersion,
            @Value("${app.paper-league-consent-version:2026-09-24}") String paperLeagueConsentVersion) {
        this.users = users; this.consents = consents; this.paperLeague = paperLeague; this.discussions = discussions;
        this.policyVersion = policyVersion; this.paperLeagueConsentVersion = paperLeagueConsentVersion;
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


    @PatchMapping("/paper-leaderboard-consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void paperLeaderboard(Authentication authentication, @RequestBody PaperLeaderboardConsent request) {
        UUID userId = (UUID) authentication.getPrincipal();
        if (request.accepted()) consents.grant(userId, "PAPER_LEADERBOARD", paperLeagueConsentVersion);
        else {
            consents.withdraw(userId, "PAPER_LEADERBOARD");
            paperLeague.deleteByUserId(userId);
        }
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        paperLeague.deleteByUserId(userId);
        discussions.deleteByUserId(userId);
        users.anonymizeAndDisable(userId);
    }

    public record MarketingConsent(boolean accepted) {}
    public record PaperLeaderboardConsent(boolean accepted) {}
    public record PrivacyExport(String email, List<UserConsentRepository.Consent> consents) {}
}

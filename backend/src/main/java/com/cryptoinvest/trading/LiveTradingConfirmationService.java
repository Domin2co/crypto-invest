package com.cryptoinvest.trading;

import com.cryptoinvest.security.UserConsentRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자의 최근 실거래 재확인과 감사 이력을 한 경계에서 관리한다. */
@Service
public class LiveTradingConfirmationService {
    private final UserConsentRepository consents;
    private final String policyVersion;
    private final long confirmationMinutes;

    public LiveTradingConfirmationService(UserConsentRepository consents,
            @Value("${app.live-trading-confirmation-version}") String policyVersion,
            @Value("${app.live-trading-confirmation-minutes:15}") long confirmationMinutes) {
        this.consents = consents;
        this.policyVersion = policyVersion;
        this.confirmationMinutes = confirmationMinutes;
    }

    @Transactional
    public void confirm(UUID userId) { consents.recordLiveTradingConfirmation(userId, policyVersion); }

    public void requireActive(UUID userId) {
        if (confirmationMinutes <= 0 || !consents.hasActive(userId, "LIVE_TRADING", OffsetDateTime.now().minusMinutes(confirmationMinutes))) {
            throw new IllegalStateException("Live order rejected: LIVE_TRADING_CONFIRMATION_REQUIRED");
        }
    }
}

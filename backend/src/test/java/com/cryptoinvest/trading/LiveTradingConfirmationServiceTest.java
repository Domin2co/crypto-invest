package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptoinvest.security.UserConsentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveTradingConfirmationServiceTest {
    @Test void recordsConfirmationAndRequiresAnUnwithdrawnRecentConfirmation() {
        UserConsentRepository consents = mock(UserConsentRepository.class);
        UUID userId = UUID.randomUUID();
        LiveTradingConfirmationService service = new LiveTradingConfirmationService(consents, "2026-09-22", 15);
        when(consents.hasActive(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq("LIVE_TRADING"), any())).thenReturn(true);

        service.confirm(userId);
        assertThatCode(() -> service.requireActive(userId)).doesNotThrowAnyException();
        verify(consents).recordLiveTradingConfirmation(userId, "2026-09-22");
    }

    @Test void rejectsAnExpiredOrWithdrawnConfirmation() {
        UserConsentRepository consents = mock(UserConsentRepository.class);
        when(consents.hasActive(any(), org.mockito.ArgumentMatchers.eq("LIVE_TRADING"), any())).thenReturn(false);

        assertThatThrownBy(() -> new LiveTradingConfirmationService(consents, "2026-09-22", 15).requireActive(UUID.randomUUID()))
                .hasMessageContaining("LIVE_TRADING_CONFIRMATION_REQUIRED");
    }
}

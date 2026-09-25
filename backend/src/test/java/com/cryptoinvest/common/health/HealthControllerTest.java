package com.cryptoinvest.common.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptoinvest.security.AppTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {"app.live-trading-enabled=false", "app.live-trading-kill-switch=true", "app.live-daily-limit=0"})
class HealthControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AppTokenService tokenService;
    @MockitoBean com.cryptoinvest.security.UserAuthRepository users;

    @Test
    void reportsSystemHealthAndLiveOrderLockWithoutGlobalTradingMode() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.liveOrderSubmissionEnabled").value(false))
                .andExpect(jsonPath("$.tradingMode").doesNotExist());
    }
}


class PaperLeagueHealthIndicatorTest {
    @Test
    void reportsOverdueValuationsForOperationsMonitoring() {
        var repository = org.mockito.Mockito.mock(com.cryptoinvest.trading.PaperLeagueRepository.class);
        org.mockito.Mockito.when(repository.overdueStartingSnapshots(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(1);
        org.mockito.Mockito.when(repository.overdueFinalSnapshots(org.mockito.ArgumentMatchers.any())).thenReturn(0);

        var health = new PaperLeagueHealthIndicator(repository).health();

        org.assertj.core.api.Assertions.assertThat(health.getStatus())
                .isEqualTo(org.springframework.boot.actuate.health.Status.DOWN);
        org.assertj.core.api.Assertions.assertThat(health.getDetails())
                .containsEntry("pendingOpeningSnapshots", 1);
    }

    @Test
    void reportsHealthyWhenNoValuationsAreOverdue() {
        var repository = org.mockito.Mockito.mock(com.cryptoinvest.trading.PaperLeagueRepository.class);
        org.mockito.Mockito.when(repository.overdueStartingSnapshots(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(0);
        org.mockito.Mockito.when(repository.overdueFinalSnapshots(org.mockito.ArgumentMatchers.any())).thenReturn(0);

        org.assertj.core.api.Assertions.assertThat(new PaperLeagueHealthIndicator(repository).health().getStatus())
                .isEqualTo(org.springframework.boot.actuate.health.Status.UP);
    }
}

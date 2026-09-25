package com.cryptoinvest.common.health;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 상태와 신규 실주문 전역 스위치 상태만 노출하며 secret이나 계정 정보는 반환하지 않는다. */
@RestController
@RequestMapping("/api")
public class HealthController {
    private final boolean liveOrderSubmissionEnabled;

    public HealthController(@Value("${app.live-trading-enabled:false}") boolean liveTradingEnabled,
            @Value("${app.live-trading-kill-switch:true}") boolean liveTradingKillSwitch,
            @Value("${app.live-daily-limit:0}") java.math.BigDecimal dailyLimit) {
        this.liveOrderSubmissionEnabled = liveTradingEnabled && !liveTradingKillSwitch
                && dailyLimit != null && dailyLimit.signum() > 0;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "liveOrderSubmissionEnabled", liveOrderSubmissionEnabled);
    }
}


@org.springframework.stereotype.Component("paperLeague")
class PaperLeagueHealthIndicator implements org.springframework.boot.actuate.health.HealthIndicator {
    private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Asia/Seoul");
    private final com.cryptoinvest.trading.PaperLeagueRepository league;

    PaperLeagueHealthIndicator(com.cryptoinvest.trading.PaperLeagueRepository league) {
        this.league = league;
    }

    @Override
    public org.springframework.boot.actuate.health.Health health() {
        java.time.LocalDate today = java.time.LocalDate.now(ZONE);
        java.time.YearMonth month = java.time.YearMonth.from(today);
        int pendingStarts = league.overdueStartingSnapshots(month, today.getDayOfMonth() > 1);
        int pendingFinals = league.overdueFinalSnapshots(month);
        var health = org.springframework.boot.actuate.health.Health.status(
                pendingStarts == 0 && pendingFinals == 0
                        ? org.springframework.boot.actuate.health.Status.UP
                        : org.springframework.boot.actuate.health.Status.DOWN);
        return health.withDetail("pendingOpeningSnapshots", pendingStarts)
                .withDetail("pendingClosingSnapshots", pendingFinals)
                .build();
    }
}

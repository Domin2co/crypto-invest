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

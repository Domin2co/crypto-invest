package com.cryptoinvest.common.health;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 확인용 endpoint. 거래 모드만 노출하며 secret이나 계정 정보는 반환하지 않는다. */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final String tradingMode;

    public HealthController(@Value("${app.trading-mode:PAPER}") String tradingMode) {
        this.tradingMode = tradingMode;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "tradingMode", tradingMode);
    }
}

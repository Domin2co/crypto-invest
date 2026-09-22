package com.cryptoinvest.trading;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 실거래 주문을 만들지 않고, 사용자의 명시적 재확인만 기록하는 인증 API다. */
@RestController
@RequestMapping("/api/live-trading")
public class LiveTradingConfirmationController {
    private final LiveTradingConfirmationService confirmations;
    public LiveTradingConfirmationController(LiveTradingConfirmationService confirmations) { this.confirmations = confirmations; }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(Authentication authentication, @RequestBody ConfirmationRequest request) {
        if (!request.accepted()) throw new IllegalArgumentException("Live trading confirmation is required");
        confirmations.confirm((UUID) authentication.getPrincipal());
    }

    public record ConfirmationRequest(boolean accepted) {}
}

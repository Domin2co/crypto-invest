package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.risk.RiskEngine;
import com.cryptoinvest.risk.RiskPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/** 외부 거래소를 호출하지 않는 사용자별 PAPER 주문 API다. */
@RestController
@RequestMapping("/api/paper/orders")
public class PaperTradingController {
    private final PersistentPaperTradingService paperTrading;
    private final PaperOrderPlanRepository plans;
    private final PaperWalletRepository wallets;
    private final PaperOrderAuditRepository orders;
    private final RiskPolicy policy;
    private final BigDecimal feeRate;

    public PaperTradingController(PersistentPaperTradingService paperTrading, PaperOrderPlanRepository plans,
            PaperWalletRepository wallets, PaperOrderAuditRepository orders,
            @Value("${app.paper-trading-kill-switch:false}") boolean killSwitch,
            @Value("${app.paper-min-order-amount:5000}") BigDecimal minOrderAmount,
            @Value("${app.paper-max-order-amount:1000000}") BigDecimal maxOrderAmount,
            @Value("${app.paper-fee-rate:0.0005}") BigDecimal feeRate) {
        this.paperTrading = paperTrading; this.plans = plans; this.wallets = wallets; this.orders = orders;
        this.policy = new RiskPolicy(killSwitch, minOrderAmount, maxOrderAmount, BigDecimal.ONE);
        this.feeRate = feeRate;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PaperTradingService.PaperFill execute(Authentication authentication, @Valid @RequestBody PaperOrderRequest request) {
        OrderPlan plan = new OrderPlan((UUID) authentication.getPrincipal(), request.exchange(), request.symbol(), request.side(),
                request.amount(), BigDecimal.ZERO, request.idempotencyKey());
        if (RiskEngine.rejectReason(plan, policy) != null) throw new IllegalArgumentException("Paper order rejected");
        UUID planId = plans.createOrFind(plan, request.quantity())
                .orElseThrow(() -> new IllegalStateException("Paper order idempotency conflict"));
        return paperTrading.execute(planId, plan, request.price(), request.quantity(), feeRate, policy);
    }

    @GetMapping("/summary")
    public PaperSummary summary(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return new PaperSummary(wallets.findByUserId(userId), orders.findByUserId(userId));
    }

    public record PaperOrderRequest(
            @NotNull Exchange exchange,
            @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,32}") String symbol,
            @NotBlank @Pattern(regexp = "BUY|SELL") String side,
            @NotNull @DecimalMin("1") BigDecimal amount,
            @NotNull @DecimalMin("0.00000001") BigDecimal price,
            @DecimalMin("0.000000000000000001") BigDecimal quantity,
            @NotBlank @Size(max = 128) String idempotencyKey) {}
    public record PaperSummary(java.util.List<PaperWalletRepository.WalletBalance> wallets,
            java.util.List<PaperOrderAuditRepository.PaperOrder> orders) {}
}

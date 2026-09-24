package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import com.cryptoinvest.market.MarketPrice;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final BigDecimal initialKrw;
    private final Map<Exchange, ExchangePublicClient> marketClients;

    public PaperTradingController(PersistentPaperTradingService paperTrading, PaperOrderPlanRepository plans,
            PaperWalletRepository wallets, PaperOrderAuditRepository orders, List<ExchangePublicClient> marketClients,
            @Value("${app.paper-initial-krw:1000000}") BigDecimal initialKrw,
            @Value("${app.paper-trading-kill-switch:false}") boolean killSwitch,
            @Value("${app.paper-min-order-amount:5000}") BigDecimal minOrderAmount,
            @Value("${app.paper-max-order-amount:1000000}") BigDecimal maxOrderAmount,
            @Value("${app.paper-fee-rate:0.0005}") BigDecimal feeRate) {
        this.paperTrading = paperTrading; this.plans = plans; this.wallets = wallets; this.orders = orders;
        this.marketClients = marketClients.stream().collect(Collectors.toUnmodifiableMap(ExchangePublicClient::exchange, Function.identity()));
        this.initialKrw = initialKrw;
        this.policy = new RiskPolicy(killSwitch, minOrderAmount, maxOrderAmount, BigDecimal.ONE);
        this.feeRate = feeRate;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PaperOrderResult execute(Authentication authentication, @Valid @RequestBody PaperOrderRequest request) {
        ExchangePublicClient marketClient = marketClients.get(request.exchange());
        if (marketClient == null) throw new IllegalArgumentException("Unsupported exchange");
        if ("SELL".equals(request.side()) && (request.quantity() == null || request.quantity().signum() <= 0)) {
            throw new IllegalArgumentException("Sell quantity is required");
        }
        MarketPrice quote = marketClient.getPrice("KRW-" + request.symbol());
        if (!("KRW-" + request.symbol()).equals(quote.market()) || quote.price() == null || quote.price().signum() <= 0 || quote.capturedAt() == null || quote.capturedAt().isBefore(Instant.now().minusSeconds(30))) {
            throw new IllegalStateException("Exchange quote is unavailable or stale");
        }
        String orderType = request.orderType() == null ? "MARKET" : request.orderType();
        if (!orderType.equals("MARKET") && !orderType.equals("LIMIT")) throw new IllegalArgumentException("Unsupported order type");
        if (orderType.equals("LIMIT")) {
            if (request.limitPrice() == null || request.limitPrice().signum() <= 0) throw new IllegalArgumentException("Limit price is required");
            boolean marketable = "BUY".equals(request.side()) ? request.limitPrice().compareTo(quote.price()) >= 0 : request.limitPrice().compareTo(quote.price()) <= 0;
            if (!marketable) throw new IllegalStateException("Limit order is not immediately marketable at the latest quote");
        }
        BigDecimal orderAmount = "BUY".equals(request.side()) ? request.amount() : request.quantity().multiply(quote.price());
        OrderPlan plan = new OrderPlan((UUID) authentication.getPrincipal(), request.exchange(), request.symbol(), request.side(),
                orderAmount, BigDecimal.ZERO, request.idempotencyKey());
        if (RiskEngine.rejectReason(plan, policy) != null) throw new IllegalArgumentException("Paper order rejected");
        UUID planId = plans.createOrFind(plan, request.quantity(), orderType, request.limitPrice())
                .orElseThrow(() -> new IllegalStateException("Paper order idempotency conflict"));
        PaperTradingService.PaperFill fill = paperTrading.execute(planId, plan, quote.price(), request.quantity(), feeRate, policy, orderType, request.limitPrice());
        return new PaperOrderResult(fill.symbol(), fill.side(), fill.quantity(), fill.amount(), quote.price(), fill.fee(), fill.status(), orderType, request.exchange(), quote.market(), quote.capturedAt());
    }

    @GetMapping("/summary")
    public PaperSummary summary(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        for (Exchange exchange : Exchange.values()) wallets.initializeKrw(userId, exchange, initialKrw);
        return new PaperSummary(wallets.findByUserId(userId), orders.findByUserId(userId));
    }

    public record PaperOrderRequest(
            @NotNull Exchange exchange,
            @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,32}") String symbol,
            @NotBlank @Pattern(regexp = "BUY|SELL") String side,
            @NotNull @DecimalMin("1") BigDecimal amount,
            @DecimalMin("0.000000000000000001") BigDecimal quantity,
            @NotBlank @Size(max = 128) String idempotencyKey,
            @Pattern(regexp = "MARKET|LIMIT") String orderType,
            @DecimalMin("0.00000001") BigDecimal limitPrice) {}
    public record PaperOrderResult(String symbol, String side, BigDecimal quantity, BigDecimal amount, BigDecimal price, BigDecimal fee, String status, String orderType, Exchange exchange, String market, Instant capturedAt) {}
    public record PaperSummary(List<PaperWalletRepository.WalletBalance> wallets,
            java.util.List<PaperOrderAuditRepository.PaperOrder> orders) {}
}

package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.privateapi.ExchangeOrderChance;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import com.cryptoinvest.market.MarketPrice;
import com.cryptoinvest.portfolio.AccountReadService;
import com.cryptoinvest.portfolio.PortfolioReadService;
import com.cryptoinvest.risk.RiskPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** authenticated LIVE execution only; the guarded order service remains PAPER-blocked by default. */
@RestController
@RequestMapping("/api/live-trading")
public class LiveTradingOrderController {
    private final LiveTradingService trading;
    private final AccountReadService accounts;
    private final PortfolioReadService portfolios;
    private final PaperOrderPlanRepository plans;
    private final Map<Exchange, ExchangePublicClient> markets;
    private final BigDecimal maxAssetWeight;

    public LiveTradingOrderController(LiveTradingService trading, AccountReadService accounts, PortfolioReadService portfolios,
            PaperOrderPlanRepository plans, List<ExchangePublicClient> markets,
            @Value("${app.live-max-asset-weight:0.35}") BigDecimal maxAssetWeight) {
        this.trading = trading; this.accounts = accounts; this.portfolios = portfolios; this.plans = plans;
        this.markets = markets.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ExchangePublicClient::exchange, Function.identity()));
        this.maxAssetWeight = maxAssetWeight;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> execute(Authentication authentication, @Valid @RequestBody LiveOrderRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        var existing = trading.recoverExisting(userId, request.idempotencyKey());
        if (existing.isPresent()) return ResponseEntity.ok(new OrderResponse(null, existing.get()));

        trading.requireNewOrderReady(userId);
        boolean buy = "BUY".equals(request.side());
        if (buy ? request.amount() == null || request.quantity() != null : request.quantity() == null || request.amount() != null)
            throw new IllegalArgumentException("Invalid LIVE order request");
        ExchangePublicClient marketClient = markets.get(request.exchange());
        if (marketClient == null) throw new IllegalArgumentException("Unsupported exchange");
        MarketPrice quote = marketClient.getPrice(request.market());
        if (quote.price().signum() <= 0 || quote.capturedAt() == null || Duration.between(quote.capturedAt(), Instant.now()).compareTo(Duration.ofSeconds(60)) > 0
                || quote.capturedAt().isAfter(Instant.now().plusSeconds(5))) throw new IllegalStateException("Fresh market price is unavailable");

        ExchangeOrderChance chance = accounts.getOrderChance(userId, request.exchange(), request.market());
        if (!"active".equalsIgnoreCase(chance.state())) throw new IllegalStateException("Exchange market is unavailable");
        String currency = request.market().substring(4);
        if (!"KRW".equals(chance.quoteCurrency()) || !currency.equals(chance.baseCurrency())) throw new IllegalStateException("Exchange market account data is unavailable");

        BigDecimal amount = buy ? request.amount() : request.quantity().multiply(quote.price());
        BigDecimal feeRate = buy ? chance.bidFee() : chance.askFee();
        BigDecimal minimum = buy ? chance.minimumBidAmount() : chance.minimumAskAmount();
        if (amount.compareTo(minimum) < 0 || amount.compareTo(chance.maximumOrderAmount()) > 0) throw new IllegalArgumentException("Exchange order amount is outside allowed limits");
        if (buy && amount.multiply(BigDecimal.ONE.add(feeRate)).compareTo(chance.availableQuote()) > 0) throw new IllegalArgumentException("Insufficient available quote balance");
        if (!buy && request.quantity().compareTo(chance.availableBase()) > 0) throw new IllegalArgumentException("Insufficient available asset balance");

        PortfolioReadService.PortfolioView portfolio = portfolios.read(userId, request.exchange());
        BigDecimal currentAssetAmount = portfolio.positions().stream().filter(position -> currency.equals(position.currency()))
                .map(PortfolioReadService.Position::evaluatedAmount).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal projectedTotal = buy ? portfolio.totalEvaluatedAmount().add(amount) : portfolio.totalEvaluatedAmount().subtract(amount);
        if (!buy && amount.compareTo(currentAssetAmount) > 0) throw new IllegalArgumentException("Sell amount exceeds the current position");
        BigDecimal projectedAsset = buy ? currentAssetAmount.add(amount) : currentAssetAmount.subtract(amount);
        BigDecimal projectedWeight = projectedTotal.signum() <= 0 ? BigDecimal.ZERO : projectedAsset.divide(projectedTotal, 18, java.math.RoundingMode.HALF_UP);
        OrderPlan plan = new OrderPlan(userId, request.exchange(), request.market(), request.side(), amount, projectedWeight, request.idempotencyKey());
        BigDecimal minimumRiskAmount = minimum.max(new BigDecimal("1"));
        RiskPolicy policy = new RiskPolicy(false, minimumRiskAmount, chance.maximumOrderAmount(), maxAssetWeight);
        UUID planId = plans.createOrFind(plan, request.quantity()).orElseThrow(() -> new IllegalStateException("LIVE order key is already in use"));
        LiveOrder order = trading.execute(planId, plan, request.quantity(), policy);
        return ResponseEntity.status(HttpStatus.CREATED).body(new OrderResponse(planId, order));
    }

    public record LiveOrderRequest(@NotNull Exchange exchange,
            @NotBlank @Pattern(regexp = "KRW-[A-Z0-9]{2,20}") String market,
            @NotBlank @Pattern(regexp = "BUY|SELL") String side,
            @jakarta.validation.constraints.DecimalMin("1") BigDecimal amount,
            @jakarta.validation.constraints.DecimalMin("0.00000001") BigDecimal quantity,
            @NotBlank @Size(min = 16, max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey) {}
    public record OrderResponse(UUID orderPlanId, LiveOrder order) {}
}
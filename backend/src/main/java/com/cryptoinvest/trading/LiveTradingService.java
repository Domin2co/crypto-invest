package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeAccountCredentialService;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.exchange.publicapi.ExchangeApiException;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** LIVE 제출 의도를 먼저 커밋하고, 결과가 불명확하거나 재호출되면 재전송 대신 거래소 상태를 조회한다. */
@Service
public class LiveTradingService {
    private final LiveTradingGuard guard;
    private final LiveOrderRepository orders;
    private final ExchangeAccountCredentialService credentials;
    private final LiveOrderClient client;
    private final LiveTradingConfirmationService confirmations;

    public LiveTradingService(LiveTradingGuard guard, LiveOrderRepository orders,
            ExchangeAccountCredentialService credentials, LiveOrderClient client, LiveTradingConfirmationService confirmations) {
        this.guard = guard; this.orders = orders; this.credentials = credentials; this.client = client; this.confirmations = confirmations;
    }

    /** 신규 주문을 위해 동의와 전역 스위치를 private API 호출보다 먼저 검사한다. */
    public void requireNewOrderReady(UUID userId) {
        confirmations.requireActive(userId);
        guard.requireSwitchesOpen();
    }

    /** 재호출은 같은 사용자의 저장 주문을 상태조회로만 복구한다. */
    public Optional<LiveOrder> recoverExisting(UUID userId, String idempotencyKey) {
        return orders.findByUserAndIdempotencyKey(userId, idempotencyKey)
                .map(order -> needsRecovery(order) ? recover(order, enabledCredentials(userId, order.exchange())) : order);
    }

    public LiveOrder execute(UUID orderPlanId, OrderPlan plan, BigDecimal sellQuantity, RiskPolicy policy) {
        Optional<LiveOrder> existing = orders.findByUserAndIdempotencyKey(plan.userId(), plan.idempotencyKey());
        if (existing.isPresent()) {
            LiveOrder order = existing.get();
            return needsRecovery(order) ? recover(order, enabledCredentials(plan.userId(), order.exchange())) : order;
        }

        confirmations.requireActive(plan.userId());
        guard.requireAllowed(plan, policy, orders.submittedAmountToday(plan.userId()));
        ExchangeCredentials account = enabledCredentials(plan.userId(), plan.exchange());
        String clientOrderId = clientOrderId(plan.idempotencyKey());
        LiveOrder submitted = orders.createSubmitted(orderPlanId, plan, sellQuantity, clientOrderId);
        try {
            return orders.update(submitted, client.submit(plan, sellQuantity, clientOrderId, account));
        } catch (LiveOrderUnknownResultException exception) {
            return recover(submitted, account);
        } catch (ExchangeApiException exception) {
            return orders.update(submitted, failed(submitted));
        }
    }

    /** 신규 주문을 만들지 않으므로 LIVE 스위치가 꺼진 뒤에도 안전하게 복구할 수 있다. */
    public LiveOrder recover(LiveOrder order, ExchangeCredentials account) {
        try {
            return orders.update(order, client.findByClientOrderId(order.exchange(), order.clientOrderId(), account));
        } catch (RuntimeException exception) {
            return orders.update(order, unknown(order));
        }
    }

    private ExchangeCredentials enabledCredentials(UUID userId, Exchange exchange) {
        ExchangeCredentials account = credentials.getEnabled(userId, exchange);
        if (account == null) throw new IllegalStateException("Live order rejected: EXCHANGE_CREDENTIAL_NOT_AVAILABLE");
        return account;
    }
    private static boolean needsRecovery(LiveOrder order) {
        return !order.terminal() && ("SUBMITTED".equals(order.status()) || "PARTIALLY_FILLED".equals(order.status()) || "UNKNOWN".equals(order.status()));
    }
    private static LiveOrder failed(LiveOrder order) { return new LiveOrder(null, order.exchange(), order.clientOrderId(), null, "FAILED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true); }
    private static LiveOrder unknown(LiveOrder order) { return new LiveOrder(null, order.exchange(), order.clientOrderId(), order.exchangeOrderId(), "UNKNOWN", order.executedQuantity(), order.executedAmount(), order.fee(), false); }
    static String clientOrderId(String idempotencyKey) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(idempotencyKey.getBytes(StandardCharsets.UTF_8)), 0, 16); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}
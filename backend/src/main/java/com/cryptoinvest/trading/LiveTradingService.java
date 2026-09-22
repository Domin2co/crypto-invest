package com.cryptoinvest.trading;

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
import org.springframework.transaction.annotation.Transactional;

/** 실주문은 저장된 멱등성 상태를 먼저 확인하고 timeout에는 재전송 대신 상태조회만 수행한다. */
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

    @Transactional
    public LiveOrder execute(UUID orderPlanId, OrderPlan plan, BigDecimal sellQuantity, RiskPolicy policy) {
        Optional<LiveOrder> existing = orders.findByIdempotencyKey(plan.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get().status().equals("UNKNOWN") ? recover(existing.get(), enabledCredentials(plan)) : existing.get();
        }

        confirmations.requireActive(plan.userId());
        guard.requireAllowed(plan, policy, orders.submittedAmountToday(plan.userId()));
        ExchangeCredentials account = enabledCredentials(plan);
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

    /** 이 경로는 신규 주문을 만들지 않으므로 Live 스위치가 꺼진 후에도 안전하게 상태를 복구할 수 있다. */
    @Transactional
    public LiveOrder recover(LiveOrder order, ExchangeCredentials account) {
        try {
            return orders.update(order, client.findByClientOrderId(order.exchange(), order.clientOrderId(), account));
        } catch (RuntimeException exception) {
            return orders.update(order, unknown(order));
        }
    }

    private ExchangeCredentials enabledCredentials(OrderPlan plan) {
        ExchangeCredentials account = credentials.getEnabled(plan.userId(), plan.exchange());
        if (account == null) throw new IllegalStateException("Live order rejected: EXCHANGE_CREDENTIAL_NOT_AVAILABLE");
        return account;
    }
    private static LiveOrder failed(LiveOrder order) { return new LiveOrder(null, order.exchange(), order.clientOrderId(), null, "FAILED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO); }
    private static LiveOrder unknown(LiveOrder order) { return new LiveOrder(null, order.exchange(), order.clientOrderId(), order.exchangeOrderId(), "UNKNOWN", order.executedQuantity(), order.executedAmount(), order.fee()); }
    static String clientOrderId(String idempotencyKey) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(idempotencyKey.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}

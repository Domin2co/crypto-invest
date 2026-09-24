package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistentPaperTradingServiceTest {
    @Test void persistsWalletAndAuditOnlyAfterRiskAcceptsBuy() {
        PaperWalletRepository wallet = mock(PaperWalletRepository.class);
        PaperOrderAuditRepository orders = mock(PaperOrderAuditRepository.class);
        OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "paper-buy");
        when(orders.findByUserAndIdempotencyKey(plan.userId(), "paper-buy")).thenReturn(Optional.empty());
        when(orders.save(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        RiskPolicy policy = new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE);

        var fill = new PersistentPaperTradingService(wallet, orders, new BigDecimal("50000"))
                .execute(UUID.randomUUID(), plan, new BigDecimal("1000"), null, new BigDecimal("0.001"), policy);

        assertThat(fill.quantity()).isEqualByComparingTo("10");
        assertThat(fill.fee()).isEqualByComparingTo("10");
        verify(wallet).initializeKrw(plan.userId(), Exchange.UPBIT, new BigDecimal("50000"));
        verify(wallet).subtract(eq(plan.userId()), eq(Exchange.UPBIT), eq("KRW"), argThat(value -> value.compareTo(new BigDecimal("10010")) == 0));
        verify(wallet).add(eq(plan.userId()), eq(Exchange.UPBIT), eq("BTC"), argThat(value -> value.compareTo(new BigDecimal("10")) == 0));
        verify(orders).save(any(), any(), any(), any(), any(), any(), any());
    }

    @Test void rejectsBeforeTouchingWalletWhenRiskFails() {
        PaperWalletRepository wallet = mock(PaperWalletRepository.class);
        PaperOrderAuditRepository orders = mock(PaperOrderAuditRepository.class);
        OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "rejected");
        RiskPolicy stopped = new RiskPolicy(true, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE);

        assertThatThrownBy(() -> new PersistentPaperTradingService(wallet, orders, BigDecimal.ONE)
                .execute(UUID.randomUUID(), plan, BigDecimal.ONE, null, BigDecimal.ZERO, stopped))
                .isInstanceOf(IllegalStateException.class);
        verify(wallet, never()).initializeKrw(any(), any(), any());
    }

    @Test void rollsBackPaperExecutionWhenIdempotencyInsertLosesRace() {
        PaperWalletRepository wallet = mock(PaperWalletRepository.class);
        PaperOrderAuditRepository orders = mock(PaperOrderAuditRepository.class);
        OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "raced-key");
        when(orders.findByUserAndIdempotencyKey(plan.userId(), plan.idempotencyKey())).thenReturn(Optional.empty());
        when(orders.save(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);
        RiskPolicy policy = new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE);

        assertThatThrownBy(() -> new PersistentPaperTradingService(wallet, orders, new BigDecimal("50000"))
                .execute(UUID.randomUUID(), plan, new BigDecimal("1000"), null, new BigDecimal("0.001"), policy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Paper order idempotency conflict");
    }
}

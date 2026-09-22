package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeAccountCredentialService;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveTradingServiceTest {
    @Test void rejectionDoesNotDecryptCredentialsOrCallAnExchange() {
        LiveTradingGuard guard = mock(LiveTradingGuard.class);
        LiveOrderRepository orders = mock(LiveOrderRepository.class);
        ExchangeAccountCredentialService credentials = mock(ExchangeAccountCredentialService.class);
        LiveOrderClient client = mock(LiveOrderClient.class);
        LiveTradingConfirmationService confirmations = mock(LiveTradingConfirmationService.class);
        OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "rejected-key");
        when(orders.findByIdempotencyKey(plan.idempotencyKey())).thenReturn(Optional.empty());
        when(orders.submittedAmountToday(plan.userId())).thenReturn(BigDecimal.ZERO);
        doThrow(new IllegalStateException("Live order rejected: TRADING_MODE_NOT_LIVE"))
                .when(guard).requireAllowed(any(), any(), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new LiveTradingService(guard, orders, credentials, client, confirmations)
                .execute(UUID.randomUUID(), plan, null, new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE)))
                .hasMessageContaining("TRADING_MODE_NOT_LIVE");

        verifyNoInteractions(credentials, client);
    }

    @Test void timeoutQueriesExistingOrderInsteadOfSendingItAgain() {
        LiveTradingGuard guard = mock(LiveTradingGuard.class);
        LiveOrderRepository orders = mock(LiveOrderRepository.class);
        ExchangeAccountCredentialService credentials = mock(ExchangeAccountCredentialService.class);
        LiveOrderClient client = mock(LiveOrderClient.class);
        LiveTradingConfirmationService confirmations = mock(LiveTradingConfirmationService.class);
        OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "timeout-key");
        String clientOrderId = LiveTradingService.clientOrderId(plan.idempotencyKey());
        ExchangeCredentials account = new ExchangeCredentials("access", "secret");
        LiveOrder submitted = new LiveOrder(UUID.randomUUID(), Exchange.UPBIT, clientOrderId, null, "SUBMITTED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        LiveOrder filled = new LiveOrder(null, Exchange.UPBIT, clientOrderId, "exchange-id", "FILLED", BigDecimal.ONE, new BigDecimal("10000"), BigDecimal.ZERO);
        when(orders.findByIdempotencyKey(plan.idempotencyKey())).thenReturn(Optional.empty());
        when(orders.submittedAmountToday(plan.userId())).thenReturn(BigDecimal.ZERO);
        when(credentials.getEnabled(plan.userId(), plan.exchange())).thenReturn(account);
        when(orders.createSubmitted(any(), any(), any(), any())).thenReturn(submitted);
        when(client.submit(any(), any(), any(), any())).thenThrow(new LiveOrderUnknownResultException("timeout", new HttpTimeoutException("timeout")));
        when(client.findByClientOrderId(Exchange.UPBIT, clientOrderId, account)).thenReturn(filled);
        when(orders.update(submitted, filled)).thenReturn(new LiveOrder(submitted.id(), Exchange.UPBIT, clientOrderId, "exchange-id", "FILLED", BigDecimal.ONE, new BigDecimal("10000"), BigDecimal.ZERO));

        LiveOrder result = new LiveTradingService(guard, orders, credentials, client, confirmations)
                .execute(UUID.randomUUID(), plan, null, new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE));

        assertThat(result.status()).isEqualTo("FILLED");
        verify(client).submit(plan, null, clientOrderId, account);
        verify(client).findByClientOrderId(Exchange.UPBIT, clientOrderId, account);
    }

    @Test void missingConfirmationDoesNotEvaluateRiskDecryptCredentialsOrCallAnExchange() {
        LiveTradingGuard guard = mock(LiveTradingGuard.class);
        LiveOrderRepository orders = mock(LiveOrderRepository.class);
        ExchangeAccountCredentialService credentials = mock(ExchangeAccountCredentialService.class);
        LiveOrderClient client = mock(LiveOrderClient.class);
        LiveTradingConfirmationService confirmations = mock(LiveTradingConfirmationService.class);
        OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), "confirmation-key");
        when(orders.findByIdempotencyKey(plan.idempotencyKey())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Live order rejected: LIVE_TRADING_CONFIRMATION_REQUIRED"))
                .when(confirmations).requireActive(plan.userId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new LiveTradingService(guard, orders, credentials, client, confirmations)
                .execute(UUID.randomUUID(), plan, null, new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE)))
                .hasMessageContaining("LIVE_TRADING_CONFIRMATION_REQUIRED");

        verifyNoInteractions(guard, credentials, client);
    }
}

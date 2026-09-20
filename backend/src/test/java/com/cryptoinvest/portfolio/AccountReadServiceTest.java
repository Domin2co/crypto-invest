package com.cryptoinvest.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeAccountCredentialService;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.exchange.privateapi.PrivateAccountClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountReadServiceTest {
    @Test void readsOnlyTheAuthenticatedUsersEnabledAccount() {
        ExchangeAccountCredentialService store = mock(ExchangeAccountCredentialService.class);
        PrivateAccountClient client = mock(PrivateAccountClient.class);
        UUID userId = UUID.randomUUID();
        when(client.exchange()).thenReturn(Exchange.UPBIT);
        when(store.getEnabled(userId, Exchange.UPBIT)).thenReturn(new ExchangeCredentials("access", "secret"));
        when(client.getBalances(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(new ExchangeBalance(Exchange.UPBIT, "KRW", java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO)));
        assertThat(new AccountReadService(store, List.of(client)).getBalances(userId, Exchange.UPBIT)).hasSize(1);
        assertThatThrownBy(() -> new AccountReadService(store, List.of(client)).getBalances(UUID.randomUUID(), Exchange.UPBIT)).isInstanceOf(IllegalStateException.class);
    }
}

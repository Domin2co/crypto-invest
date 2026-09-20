package com.cryptoinvest.portfolio;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeAccountCredentialService;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.exchange.privateapi.PrivateAccountClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** 인증 계층이 확인한 userId만 받아 해당 사용자의 거래소 잔고를 읽는다. */
@Service
public class AccountReadService {
    private final ExchangeAccountCredentialService credentials;
    private final Map<Exchange, PrivateAccountClient> clients;

    public AccountReadService(ExchangeAccountCredentialService credentials, List<PrivateAccountClient> clients) {
        this.credentials = credentials;
        this.clients = clients.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(PrivateAccountClient::exchange, Function.identity()));
    }

    public List<ExchangeBalance> getBalances(UUID authenticatedUserId, Exchange exchange) {
        ExchangeCredentials account = credentials.getEnabled(authenticatedUserId, exchange);
        if (account == null) throw new IllegalStateException("No enabled exchange account");
        return clients.get(exchange).getBalances(account);
    }
}

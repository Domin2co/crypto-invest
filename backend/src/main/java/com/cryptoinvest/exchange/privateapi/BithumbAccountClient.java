package com.cryptoinvest.exchange.privateapi;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.exchange.credential.JwtSigner;
import com.cryptoinvest.exchange.publicapi.AbstractPublicClient;
import com.cryptoinvest.portfolio.ExchangeBalance;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/** Bithumb `/v1/accounts`의 읽기 전용 응답만 변환한다. */
@Component
public class BithumbAccountClient extends AbstractPublicClient implements PrivateAccountClient {
    private final JwtSigner signer;
    public BithumbAccountClient(ObjectMapper objectMapper, JwtSigner signer) { super(objectMapper); this.signer = signer; }
    public Exchange exchange() { return Exchange.BITHUMB; }
    public List<ExchangeBalance> getBalances(ExchangeCredentials credentials) {
        return java.util.stream.StreamSupport.stream(get("https://api.bithumb.com/v1/accounts", signer.bearerToken(credentials, "HS256", true)).spliterator(), false)
                .map(n -> new ExchangeBalance(exchange(), n.path("currency").asText(), n.path("balance").decimalValue(), n.path("avg_buy_price").decimalValue())).toList();
    }
}

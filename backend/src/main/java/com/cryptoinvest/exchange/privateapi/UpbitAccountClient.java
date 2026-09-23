package com.cryptoinvest.exchange.privateapi;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.exchange.credential.JwtSigner;
import com.cryptoinvest.exchange.publicapi.AbstractPublicClient;
import com.cryptoinvest.portfolio.ExchangeBalance;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** Upbit `/v1/accounts`의 읽기 전용 응답만 변환한다. */
@Component
public class UpbitAccountClient extends AbstractPublicClient implements PrivateAccountClient {
    private final JwtSigner signer;
    public UpbitAccountClient(ObjectMapper objectMapper, JwtSigner signer) { super(objectMapper); this.signer = signer; }
    public Exchange exchange() { return Exchange.UPBIT; }
    public List<ExchangeBalance> getBalances(ExchangeCredentials credentials) {
        return java.util.stream.StreamSupport.stream(get("https://api.upbit.com/v1/accounts", signer.bearerToken(credentials)).spliterator(), false)
                .map(n -> new ExchangeBalance(exchange(), n.path("currency").asText(), new BigDecimal(n.path("balance").asText()).add(new BigDecimal(n.path("locked").asText())), new BigDecimal(n.path("avg_buy_price").asText()))).toList();
    }
    public ExchangeOrderChance getOrderChance(ExchangeCredentials credentials, String market) {
        validate(market, 1);
        String query = "market=" + market;
        return ExchangeOrderChance.from(get("https://api.upbit.com/v1/orders/chance?" + query,
                signer.bearerTokenForQuery(credentials, query, "HS512", false)));
    }
}

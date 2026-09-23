package com.cryptoinvest.exchange.privateapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.exchange.credential.JwtSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PrivateAccountClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void balancesIncludeFundsLockedByOpenOrders() throws Exception {
        JsonNode response = mapper.readTree("""
                [{"currency":"BTC","balance":"1.2","locked":"0.3","avg_buy_price":"100"}]
                """);
        StubUpbit upbit = new StubUpbit(response);
        StubBithumb bithumb = new StubBithumb(response);

        assertThat(upbit.getBalances(new ExchangeCredentials("a", "s")).getFirst().quantity()).isEqualByComparingTo("1.5");
        assertThat(bithumb.getBalances(new ExchangeCredentials("a", "s")).getFirst().quantity()).isEqualByComparingTo("1.5");
    }

    @Test void orderChanceRequestsUseMarketAndParseRules() throws Exception {
        JsonNode response = mapper.readTree("""
                {"market":{"id":"KRW-BTC","state":"active","bid":{"min_total":"5000"},"ask":{"min_total":"5000"},"max_total":"1000000000"},
                 "bid_account":{"currency":"KRW","balance":"25000"},"ask_account":{"currency":"BTC","balance":"0.2"},"bid_fee":"0.0005","ask_fee":"0.0005"}
                """);
        StubUpbit upbit = new StubUpbit(response);
        StubBithumb bithumb = new StubBithumb(response);

        assertThat(upbit.getOrderChance(new ExchangeCredentials("a", "s"), "KRW-BTC").minimumBidAmount()).isEqualByComparingTo("5000");
        assertThat(upbit.url).isEqualTo("https://api.upbit.com/v1/orders/chance?market=KRW-BTC");
        assertThat(upbit.authorization).isNotBlank();
        assertThat(bithumb.getOrderChance(new ExchangeCredentials("a", "s"), "KRW-BTC").availableBase()).isEqualByComparingTo("0.2");
        assertThat(bithumb.url).isEqualTo("https://api.bithumb.com/v1/orders/chance?market=KRW-BTC");
        assertThat(bithumb.authorization).isNotBlank();
    }

    private class StubUpbit extends UpbitAccountClient {
        private final JsonNode response;
        private String url;
        private String authorization;
        StubUpbit(JsonNode response) { super(mapper, new JwtSigner()); this.response = response; }
        @Override protected JsonNode get(String url, String authorization) { this.url = url; this.authorization = authorization; return response; }
    }

    private class StubBithumb extends BithumbAccountClient {
        private final JsonNode response;
        private String url;
        private String authorization;
        StubBithumb(JsonNode response) { super(mapper, new JwtSigner()); this.response = response; }
        @Override protected JsonNode get(String url, String authorization) { this.url = url; this.authorization = authorization; return response; }
    }
}

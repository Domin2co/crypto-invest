package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.exchange.credential.JwtSigner;
import com.cryptoinvest.exchange.publicapi.ExchangeApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LiveOrderClientTest {
    @Test void submitsUpbitAndBithumbLimitOrdersWithImmediateOrCancel() throws Exception {
        AtomicReference<String> upbitBody = new AtomicReference<>();
        AtomicReference<String> bithumbBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/orders", exchange -> { upbitBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); respond(exchange, 201, "{\"uuid\":\"upbit-order\",\"state\":\"cancel\"}"); });
        server.createContext("/v2/orders", exchange -> { bithumbBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); respond(exchange, 201, "{\"order_id\":\"bithumb-order\",\"state\":\"cancel\"}"); });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            LiveOrderClient client = new LiveOrderClient(new ObjectMapper(), new JwtSigner(), HttpClient.newHttpClient(), baseUrl, baseUrl);
            ExchangeCredentials credentials = new ExchangeCredentials("access", "secret");
            BigDecimal quantity = new BigDecimal("0.0005");
            BigDecimal price = new BigDecimal("20000000");
            client.submit(new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), BigDecimal.ONE, "upbit-limit-key"), quantity, "upbit-client", credentials, "LIMIT", price);
            client.submit(new OrderPlan(UUID.randomUUID(), Exchange.BITHUMB, "KRW-BTC", "SELL", new BigDecimal("10000"), BigDecimal.ONE, "bithumb-limit-key"), quantity, "bithumb-client", credentials, "LIMIT", price);

            assertThat(upbitBody.get()).isEqualTo("{\"market\":\"KRW-BTC\",\"side\":\"bid\",\"ord_type\":\"limit\",\"price\":\"20000000\",\"volume\":\"0.0005\",\"time_in_force\":\"ioc\",\"identifier\":\"upbit-client\"}");
            assertThat(bithumbBody.get()).isEqualTo("{\"market\":\"KRW-BTC\",\"side\":\"ask\",\"order_type\":\"limit\",\"price\":\"20000000\",\"volume\":\"0.0005\",\"time_in_force\":\"ioc\",\"client_order_id\":\"bithumb-client\"}");
        } finally { server.stop(0); }
    }
    @Test void signsTheSameOrderedFieldsThatItSendsAndUsesClientIdForRecovery() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> queryHash = new AtomicReference<>();
        AtomicReference<String> jwtAlgorithm = new AtomicReference<>();
        AtomicReference<Boolean> hasTimestamp = new AtomicReference<>(false);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/orders", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            jwtAlgorithm.set(header(exchange).path("alg").asText());
            hasTimestamp.set(payload(exchange).has("timestamp"));
            queryHash.set(payload(exchange).path("query_hash").asText());
            respond(exchange, 201, "{\"uuid\":\"exchange-order\",\"state\":\"wait\"}");
        });
        server.createContext("/v1/order", exchange -> respond(exchange, 200, "{\"uuid\":\"exchange-order\",\"state\":\"done\",\"executed_volume\":\"0.01\",\"executed_funds\":\"10000\",\"paid_fee\":\"5\"}"));
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            LiveOrderClient client = new LiveOrderClient(new ObjectMapper(), new JwtSigner(), HttpClient.newHttpClient(), baseUrl, baseUrl);
            OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), BigDecimal.ONE, "idempotency-key");
            LiveOrder submitted = client.submit(plan, null, "client-id", new ExchangeCredentials("access", "secret"));
            LiveOrder recovered = client.findByClientOrderId(Exchange.UPBIT, "client-id", new ExchangeCredentials("access", "secret"));

            assertThat(body.get()).isEqualTo("{\"market\":\"KRW-BTC\",\"side\":\"bid\",\"ord_type\":\"price\",\"price\":\"10000\",\"identifier\":\"client-id\"}");
            assertThat(jwtAlgorithm.get()).isEqualTo("HS512");
            assertThat(hasTimestamp.get()).isFalse();
            assertThat(queryHash.get()).isEqualTo(sha512("market=KRW-BTC&side=bid&ord_type=price&price=10000&identifier=client-id"));
            assertThat(submitted.status()).isEqualTo("SUBMITTED");
            assertThat(recovered.status()).isEqualTo("FILLED");
            assertThat(recovered.executedAmount()).isEqualByComparingTo("10000");
        } finally { server.stop(0); }
    }

    @Test void mapsBithumbOrderTypeAndClientOrderId() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> jwtAlgorithm = new AtomicReference<>();
        AtomicReference<Boolean> hasTimestamp = new AtomicReference<>(false);
        AtomicReference<String> queryHash = new AtomicReference<>();
        AtomicReference<String> recoveryQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/orders", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            jwtAlgorithm.set(header(exchange).path("alg").asText());
            hasTimestamp.set(payload(exchange).hasNonNull("timestamp"));
            queryHash.set(payload(exchange).path("query_hash").asText());
            respond(exchange, 201, "{\"order_id\":\"bithumb-order\",\"state\":\"wait\"}");
        });
        server.createContext("/v1/order", exchange -> {
            recoveryQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"order_id\":\"bithumb-order\",\"state\":\"done\"}");
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            LiveOrderClient client = new LiveOrderClient(new ObjectMapper(), new JwtSigner(), HttpClient.newHttpClient(), baseUrl, baseUrl);
            OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.BITHUMB, "KRW-BTC", "BUY", new BigDecimal("10000"), BigDecimal.ONE, "bithumb-key");

            LiveOrder submitted = client.submit(plan, null, "client-id", new ExchangeCredentials("access", "secret"));
            LiveOrder recovered = client.findByClientOrderId(Exchange.BITHUMB, "client-id", new ExchangeCredentials("access", "secret"));

            assertThat(body.get()).contains("\"order_type\":\"price\"").contains("\"client_order_id\":\"client-id\"");
            assertThat(jwtAlgorithm.get()).isEqualTo("HS256");
            assertThat(hasTimestamp.get()).isTrue();
            assertThat(queryHash.get()).isEqualTo(sha512("market=KRW-BTC&side=bid&order_type=price&price=10000&client_order_id=client-id"));
            assertThat(recoveryQuery.get()).isEqualTo("client_order_id=client-id");
            assertThat(submitted.exchangeOrderId()).isEqualTo("bithumb-order");
            assertThat(recovered.status()).isEqualTo("FILLED");
        } finally { server.stop(0); }
    }

    @Test void generatedClientOrderIdFitsBithumbLimitAndAllowedCharacters() {
        String id = LiveTradingService.clientOrderId("idempotency-key");

        assertThat(id).hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test void keepsBithumbDonePartialFillAsTerminalPartialInsteadOfFullFill() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/orders", exchange -> respond(exchange, 201,
                "{\"order_id\":\"bithumb-order\",\"state\":\"done\",\"volume\":\"0.01\",\"remaining_volume\":\"0\",\"executed_volume\":\"0.006\",\"executed_funds\":\"6000\",\"paid_fee\":\"3\"}"));
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            LiveOrderClient client = new LiveOrderClient(new ObjectMapper(), new JwtSigner(), HttpClient.newHttpClient(), baseUrl, baseUrl);
            OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.BITHUMB, "KRW-BTC", "BUY", new BigDecimal("10000"), BigDecimal.ONE, "partial-done-key");

            LiveOrder result = client.submit(plan, null, "client-id", new ExchangeCredentials("access", "secret"));

            assertThat(result.status()).isEqualTo("PARTIALLY_FILLED");
            assertThat(result.terminal()).isTrue();
            assertThat(result.executedQuantity()).isEqualByComparingTo("0.006");
            assertThat(result.executedAmount()).isEqualByComparingTo("6000");
        } finally { server.stop(0); }
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 409, 500})
    void treatsAmbiguousOrderResponsesAsUnknownAndNeverRetries(int statusCode) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/orders", exchange -> {
            calls.incrementAndGet();
            respond(exchange, statusCode, "{\"error\":\"ambiguous result\"}");
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            LiveOrderClient client = new LiveOrderClient(new ObjectMapper(), new JwtSigner(), HttpClient.newHttpClient(), baseUrl, baseUrl);
            OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), BigDecimal.ONE, "gateway-unknown-key");

            assertThatThrownBy(() -> client.submit(plan, null, "client-id", new ExchangeCredentials("access", "secret")))
                    .isInstanceOf(LiveOrderUnknownResultException.class);
            assertThat(calls).hasValue(1);
        } finally { server.stop(0); }
    }

    @Test void keepsRateLimitResponseAsDefinitiveRejection() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/orders", exchange -> respond(exchange, 429, "{\"error\":\"rate limited\"}"));
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            LiveOrderClient client = new LiveOrderClient(new ObjectMapper(), new JwtSigner(), HttpClient.newHttpClient(), baseUrl, baseUrl);
            OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"), BigDecimal.ONE, "rate-limit-key");

            assertThatThrownBy(() -> client.submit(plan, null, "client-id", new ExchangeCredentials("access", "secret")))
                    .isInstanceOf(ExchangeApiException.class);
        } finally { server.stop(0); }
    }

    private static com.fasterxml.jackson.databind.JsonNode header(HttpExchange exchange) throws IOException {
        String token = exchange.getRequestHeaders().getFirst("Authorization").substring("Bearer ".length());
        return new ObjectMapper().readTree(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
    }
    private static com.fasterxml.jackson.databind.JsonNode payload(HttpExchange exchange) throws IOException {
        String token = exchange.getRequestHeaders().getFirst("Authorization").substring("Bearer ".length());
        String encoded = token.split("\\.")[1];
        return new ObjectMapper().readTree(Base64.getUrlDecoder().decode(encoded));
    }
    private static String sha512(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-512").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(); for (byte valueByte : hash) result.append(String.format("%02x", valueByte)); return result.toString();
    }
    private static void respond(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }
}

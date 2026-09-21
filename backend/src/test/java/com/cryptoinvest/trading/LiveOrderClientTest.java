package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.exchange.credential.JwtSigner;
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
import org.junit.jupiter.api.Test;

class LiveOrderClientTest {
    @Test void signsTheSameOrderedFieldsThatItSendsAndUsesClientIdForRecovery() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> queryHash = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/orders", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
            assertThat(queryHash.get()).isEqualTo(sha512("market=KRW-BTC&side=bid&ord_type=price&price=10000&identifier=client-id"));
            assertThat(submitted.status()).isEqualTo("SUBMITTED");
            assertThat(recovered.status()).isEqualTo("FILLED");
            assertThat(recovered.executedAmount()).isEqualByComparingTo("10000");
        } finally { server.stop(0); }
    }

    @Test void mapsBithumbOrderTypeAndClientOrderId() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/orders", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 201, "{\"order_id\":\"bithumb-order\",\"state\":\"wait\"}");
        });
        server.createContext("/v1/order", exchange -> respond(exchange, 200, "{\"order_id\":\"bithumb-order\",\"state\":\"done\"}"));
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            LiveOrderClient client = new LiveOrderClient(new ObjectMapper(), new JwtSigner(), HttpClient.newHttpClient(), baseUrl, baseUrl);
            OrderPlan plan = new OrderPlan(UUID.randomUUID(), Exchange.BITHUMB, "KRW-BTC", "BUY", new BigDecimal("10000"), BigDecimal.ONE, "bithumb-key");

            LiveOrder submitted = client.submit(plan, null, "client-id", new ExchangeCredentials("access", "secret"));
            LiveOrder recovered = client.findByClientOrderId(Exchange.BITHUMB, "client-id", new ExchangeCredentials("access", "secret"));

            assertThat(body.get()).contains("\"order_type\":\"price\"").contains("\"client_order_id\":\"client-id\"");
            assertThat(submitted.exchangeOrderId()).isEqualTo("bithumb-order");
            assertThat(recovered.status()).isEqualTo("FILLED");
        } finally { server.stop(0); }
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

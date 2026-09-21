package com.cryptoinvest.exchange.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExchangeHttpFailureTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }
    @AfterEach void stopServer() { server.stop(0); }

    @Test void rejects429And500WithoutRetrying() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/limited", exchange -> respond(exchange, 429, "{}", calls));
        server.createContext("/failed", exchange -> respond(exchange, 500, "{}", calls));
        Client client = new Client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.fetch(baseUrl + "/limited")).hasMessageContaining("429");
        assertThatThrownBy(() -> client.fetch(baseUrl + "/failed")).hasMessageContaining("500");
        assertThat(calls).hasValue(2);
    }

    @Test void rejectsMalformedJsonAndTimesOut() {
        server.createContext("/invalid", exchange -> respond(exchange, 200, "not-json", null));
        server.createContext("/slow", exchange -> { try { Thread.sleep(200); respond(exchange, 200, "{}", null); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } });
        Client client = new Client(Duration.ofMillis(50));

        assertThatThrownBy(() -> client.fetch(baseUrl + "/invalid")).hasMessageContaining("request failed");
        assertThatThrownBy(() -> client.fetch(baseUrl + "/slow")).hasMessageContaining("request failed");
    }

    private static void respond(HttpExchange exchange, int status, String body, AtomicInteger calls) throws IOException {
        if (calls != null) calls.incrementAndGet();
        byte[] bytes = body.getBytes(); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
    private static final class Client extends AbstractPublicClient {
        private Client(Duration timeout) { super(new ObjectMapper(), HttpClient.newHttpClient(), timeout); }
        private void fetch(String url) { get(url); }
    }
}
